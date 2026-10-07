# Tuần 9 — Delivery semantics và consistency

> Mục tiêu: phân biệt chính xác at-most-once, at-least-once và exactly-once; biết guarantee nằm ở đâu và thiết kế database–Kafka flow không tự quảng cáo “exactly once end-to-end”.

## 1. Kết quả cần đạt

Sau bài học, bạn có thể:

- Mô tả at-most-once bằng commit trước process.
- Mô tả at-least-once bằng process rồi commit.
- Giải thích duplicate và mất record trong từng failure window.
- Hiểu exactly-once semantics trong Kafka-to-Kafka pipeline.
- Cấu hình idempotent producer và transactional.id.
- Hiểu transaction timeout và transaction coordinator.
- Dùng isolation.level=read_committed.
- Phân biệt Kafka transaction với database transaction.
- Nhận diện database–Kafka dual write.
- Thiết kế transactional outbox, inbox và CDC ở mức kiến trúc.
- Dùng business idempotency key và unique constraint.
- Viết consistency guarantee table cho từng pipeline.
- Kiểm thử crash, duplicate, retry, transaction abort và replay.

## 2. Vì sao delivery semantics quan trọng?

Một pipeline Kafka thường có các bước:

    Producer
       |
       v
    Kafka topic
       |
       v
    Consumer
       |
       v
    Database/API/Email

Mỗi bước có thể thất bại ở những thời điểm khác nhau:

    request gửi đi
    broker append
    response quay về
    consumer process
    side effect hoàn tất
    offset commit

Không có một câu trả lời chung kiểu “message chỉ xử lý một lần”. Cần hỏi:

- một record có thể bị mất không?
- một record có thể được xử lý nhiều lần không?
- duplicate ở Kafka hay ở side effect?
- guarantee áp dụng trong một topic, một pipeline hay toàn hệ thống?
- database có cùng transaction với Kafka không?
- replay có tạo tác dụng phụ thứ hai không?

Delivery semantics là mô tả hành vi khi có failure, không phải khẩu hiệu marketing.

## 3. Failure window cơ bản

### 3.1 Producer

    application -> send -> broker append -> response

Failure có thể xảy ra:

- trước broker append;
- sau broker append nhưng trước response;
- sau response nhưng application crash;
- retry tạo duplicate nếu không có idempotence.

### 3.2 Consumer

    poll -> process -> side effect -> commit offset

Failure có thể xảy ra:

- trước process;
- giữa process;
- sau side effect nhưng trước commit;
- sau commit nhưng side effect chưa hoàn tất;
- commit thành công nhưng response về chậm.

Hai vị trí quyết định semantics:

- commit trước process;
- commit sau process.

### 3.3 Database và Kafka

    DB transaction -> commit
    Kafka publish -> ack

Nếu hai bước không cùng transaction coordinator, process có thể chết giữa chúng. Đây là database–Kafka dual write.

## 4. At-most-once

### 4.1 Luồng

    poll record
       |
       v
    commit offset
       |
       v
    process record

Record được kỳ vọng xử lý không quá một lần. Nếu process chết sau commit nhưng trước side effect, record có thể bị bỏ qua.

### 4.2 Failure example

    offset 10 được poll
    commit offset 11 thành công
    process crash
    restart đọc từ offset 11

Record offset 10 không được process lại.

### 4.3 Ưu điểm

- giảm duplicate;
- đơn giản với side effect không quan trọng;
- phù hợp một số metric hoặc telemetry có thể mất;
- giảm retry side effect.

### 4.4 Nhược điểm

- có thể mất event;
- không phù hợp payment, inventory hoặc audit;
- lỗi khó phục hồi nếu retention đã trôi qua;
- application dễ “trông như khỏe” vì offset vẫn tiến.

### 4.5 Khi dùng?

Chỉ chọn at-most-once khi mất record đã được chấp nhận bằng business decision. Không chọn chỉ vì implementation dễ hơn.

## 5. At-least-once

### 5.1 Luồng

    poll record
       |
       v
    process record
       |
       v
    side effect thành công
       |
       v
    commit offset

Record chưa commit có thể được xử lý lại sau crash.

### 5.2 Failure example

    offset 10 được poll
    database ghi payment result
    process crash trước commit offset 11
    restart đọc lại offset 10
    database/payment được gọi lần hai

At-least-once không tự động có nghĩa business duplicate. Nếu side effect idempotent, kết quả quan sát được có thể effectively-once.

### 5.3 Ưu điểm

- giảm nguy cơ mất record;
- phù hợp event quan trọng hơn;
- dễ kết hợp với retry và replay;
- failure có thể recovery bằng xử lý lại.

### 5.4 Nhược điểm

- duplicate là hành vi bình thường;
- cần idempotency;
- database unique constraint hoặc inbox;
- downstream API cần idempotency key;
- email/payment không nên gọi mù nhiều lần.

### 5.5 At-least-once là lựa chọn phổ biến

Nhiều hệ thống chọn at-least-once vì duplicate có thể kiểm soát bằng business key, trong khi mất event có thể khó phục hồi hơn. Nhưng phải ghi rõ consumer idempotency trong design.

## 6. Idempotency

Một operation idempotent có thể được thực hiện nhiều lần mà trạng thái cuối quan sát được tương đương thực hiện một lần.

### 6.1 Event ID deduplication

    eventId = evt-123

Consumer lưu:

    processed_events
      event_id unique
      processed_at
      result_reference

Khi nhận lại:

1. kiểm tra eventId;
2. nếu đã xử lý, bỏ qua hoặc trả lại kết quả cũ;
3. nếu chưa xử lý, thực hiện trong transaction;
4. ghi marker processed.

### 6.2 Unique constraint

Ví dụ:

    CREATE TABLE processed_events (
        event_id VARCHAR(100) PRIMARY KEY,
        processed_at TIMESTAMP NOT NULL
    );

Insert duplicate sẽ fail hoặc dùng insert-if-not-exists. Constraint phải nằm trong database bền vững, không chỉ memory.

### 6.3 Business idempotency key

Payment API:

    idempotency-key: payment-ORD-1001

Nếu request timeout nhưng provider đã xử lý, retry cùng key trả kết quả cũ thay vì tạo charge mới.

Key cần:

- ổn định qua retry;
- gắn với business operation;
- không tái sử dụng cho operation khác;
- có retention đủ dài;
- được bảo vệ và audit.

### 6.4 Idempotent không phải ignore mọi duplicate

Có duplicate cần được kiểm tra version/state:

    event v1 -> apply
    event v1 retry -> ignore
    event v0 cũ -> reject/ignore
    event v2 -> apply

Dedup chỉ theo eventId có thể chưa đủ nếu producer phát ra hai event khác nhau nhưng cùng business operation.

## 7. Exactly-once semantics trong Kafka

### 7.1 Phạm vi

Exactly-once semantics của Kafka thường nói về pipeline Kafka-to-Kafka:

    read input topic
       |
       v
    process
       |
       v
    write output topic
       |
       v
    commit input offset và output transaction cùng nhau

Nếu transaction abort, output record không được coi là committed và input offset không được commit trong transaction đó. Khi retry, pipeline có thể tạo kết quả quan sát như một lần.

### 7.2 Không phải exactly-once end-to-end

Kafka transaction không tự động bao trùm:

- PostgreSQL/MySQL;
- REST API;
- payment gateway;
- email provider;
- filesystem;
- cache ngoài Kafka.

Flow này chưa exactly-once end-to-end:

    consume Kafka
       |
       +--> INSERT database
       +--> produce Kafka
       +--> commit Kafka transaction

Nếu database commit thành công nhưng Kafka transaction abort, DB side effect vẫn tồn tại.

Luôn viết rõ:

    Kafka-to-Kafka: exactly-once có thể đạt trong phạm vi cấu hình
    Kafka-to-DB: cần outbox/inbox/idempotency hoặc transaction integration
    Kafka-to-email: thường effectively-once nhờ idempotency/dedup

## 8. Idempotent producer

Idempotent producer giúp Kafka xử lý một số duplicate do retry trong cùng producer session.

Cấu hình khái niệm:

    enable.idempotence=true
    acks=all
    retries > 0

Producer dùng identity và sequence để broker nhận diện request phù hợp.

### 8.1 Giới hạn

Idempotent producer không giải quyết:

- application gửi cùng event hai lần có eventId khác;
- producer restart và business retry;
- replay DLT;
- nhiều producer cùng phát một operation;
- side effect ở consumer;
- duplicate do outbox publisher chưa dedup.

Vẫn cần eventId và business idempotency.

## 9. Kafka transaction

### 9.1 Transactional producer

Một producer transaction có thể:

1. begin transaction;
2. gửi nhiều record;
3. commit hoặc abort.

Khái niệm Java:

    producer.initTransactions();

    producer.beginTransaction();

    producer.send(inputOutputRecord);

    producer.commitTransaction();

Khi có lỗi:

    producer.abortTransaction();

Không dùng cùng transactional.id cho nhiều producer instance độc lập nếu không hiểu fencing và lifecycle.

### 9.2 transactional.id

transactional.id định danh producer transaction identity ổn định. Nó hỗ trợ fencing producer cũ khi một instance mới dùng cùng identity.

Ví dụ:

    transactional.id=orders-transformer-instance-1

Quy tắc:

- mỗi logical producer instance cần identity phù hợp;
- không hardcode một transactional.id cho mọi replica;
- identity phải ổn định theo instance nhưng không bị trùng;
- deployment/scale cần chiến lược đặt ID.

### 9.3 Transaction timeout

Transaction phải hoàn thành trong giới hạn timeout. Nếu processing hoặc downstream chậm quá lâu:

- transaction có thể bị timeout;
- producer bị abort/fail;
- output không commit;
- consumer cần retry/recover.

Không tăng transaction timeout vô hạn. Transaction dài giữ resource và làm recovery phức tạp.

### 9.4 Transaction coordinator

Kafka transaction cần coordinator quản lý state transaction. Đây là coordination trong Kafka, không phải database transaction coordinator.

## 10. Read committed và read uncommitted

### 10.1 read_uncommitted

Consumer có thể đọc cả record từ transaction chưa commit hoặc đã abort tùy context. Phù hợp khi cần raw log hoặc không dùng transaction, nhưng không phù hợp downstream cần chỉ thấy dữ liệu committed.

### 10.2 read_committed

    isolation.level=read_committed

Consumer chỉ đọc record từ transaction đã commit. Dữ liệu transaction aborted bị bỏ qua về mặt application visibility, dù offset/log internals có thể vẫn tồn tại.

Dùng read_committed khi:

- output topic được ghi bằng Kafka transaction;
- consumer không được thấy kết quả transaction abort;
- pipeline Kafka-to-Kafka cần exactly-once semantics.

### 10.3 Các bên phải đồng thuận

Exactly-once pipeline cần:

- producer transaction;
- consumer offset gửi vào transaction;
- output consumer dùng read_committed;
- downstream không đọc nhầm read_uncommitted;
- transaction timeout hợp lý;
- idempotency cho boundary ngoài Kafka.

Một consumer read_uncommitted có thể thấy record mà pipeline intended to abort.

## 11. Kafka Streams read-process-write

Kafka Streams thường cung cấp abstraction cho read-process-write transactional. Ở mức khái niệm:

    input topic
        |
        v
    topology processing
        |
        +--> output topic
        +--> state store/changelog
        +--> input offset transaction

Nếu bật processing guarantee phù hợp, Kafka Streams có thể dùng transaction cho pipeline Kafka-to-Kafka. Nhưng side effect gọi database/API bên trong processor vẫn cần thiết kế riêng.

## 12. Database–Kafka dual write

### 12.1 Vấn đề

Use case:

    HTTP request
       |
       +--> DB: INSERT order
       +--> Kafka: publish OrderCreated

Nếu DB commit trước Kafka publish:

    DB success
    process crash
    Kafka event missing

Nếu Kafka publish trước DB commit:

    Kafka success
    DB rollback
    event mô tả order không tồn tại

Đây là dual write consistency gap.

### 12.2 Không giải quyết bằng retry đơn giản

Retry publish có thể tạo duplicate. Retry DB có thể tạo conflict. Nếu hai hệ thống không cùng transaction, cần pattern kiến trúc.

## 13. Transactional outbox

### 13.1 Mô hình

    application transaction
       |
       +--> orders table
       +--> outbox table
                    |
                    v
             outbox publisher
                    |
                    v
                 Kafka

Order và outbox record được ghi trong cùng database transaction. Nếu transaction commit, publisher cuối cùng sẽ thấy cả hai.

### 13.2 Outbox record

    outbox_events
      id
      aggregate_type
      aggregate_id
      event_type
      payload
      occurred_at
      published_at
      attempts
      status

Publisher:

1. đọc outbox chưa publish;
2. publish Kafka;
3. nhận ack;
4. đánh dấu published;
5. retry nếu fail.

Outbox publisher có thể crash sau Kafka ack nhưng trước khi mark published. Vì vậy publisher cần idempotent event key hoặc consumer dedup.

### 13.3 Ưu điểm

- DB order và intent publish atomic;
- không mất event giữa DB commit và publish;
- retry được;
- dễ audit;
- phù hợp service sở hữu database.

### 13.4 Nhược điểm

- outbox table tăng;
- cần cleanup/archive;
- publisher vận hành thêm;
- duplicate publish vẫn có thể xảy ra;
- latency không realtime tuyệt đối;
- schema payload cần quản lý.

### 13.5 Outbox không tự exactly-once

Outbox đảm bảo intent được lưu cùng business transaction. Nó không đảm bảo Kafka publish và mark published atomically nếu DB và Kafka không cùng transaction. Vì vậy thường là at-least-once publish + idempotency.

## 14. Inbox pattern

Inbox xử lý event đến ở consumer:

    Kafka event
       |
       v
    inbox table unique(event_id)
       |
       +--> business side effect
       +--> processed marker

Consumer ghi eventId vào inbox trong cùng DB transaction với business update. Nếu event duplicate:

- unique constraint phát hiện;
- business logic không chạy lần hai;
- offset commit sau transaction phù hợp.

Inbox hữu ích khi consumer có database side effect quan trọng.

### 14.1 Quy trình

1. nhận event;
2. mở DB transaction;
3. insert inbox eventId;
4. nếu duplicate, không chạy side effect;
5. nếu mới, update business tables;
6. commit DB;
7. commit Kafka offset.

Crash trước Kafka commit có thể đọc lại, nhưng inbox ngăn side effect lặp.

## 15. CDC

Change Data Capture đọc thay đổi database transaction log và phát thành event:

    DB transaction
       |
       v
    database log
       |
       v
    CDC connector
       |
       v
    Kafka topic

CDC giảm nhu cầu application tự dual write. Nó phù hợp khi event cần phản ánh row/database change, nhưng event CDC không luôn là business event đẹp.

Cần phân biệt:

- row change: customer.email changed;
- business event: CustomerEmailChanged vì actor/intent/reason.

CDC có thể cần transform/envelope để tạo contract phù hợp.

## 16. So sánh outbox, inbox và CDC

| Pattern | Giải quyết | Vị trí |
|---|---|---|
| Outbox | DB change và event intent không tách rời | Producer service |
| Inbox | Consumer side effect dedup/transaction | Consumer service |
| CDC | Đọc thay đổi từ DB log | Data integration |
| Idempotency key | Retry operation ngoài hệ thống | API/business boundary |
| Kafka transaction | Atomic Kafka read/process/write | Kafka pipeline |

Nhiều hệ thống kết hợp:

    Order Service: DB + outbox
    Kafka
    Payment Service: inbox + DB
    Payment API: idempotency key

## 17. Consistency guarantee table

Mỗi pipeline nên ghi rõ:

| Bước | Guarantee | Failure behavior | Bảo vệ duplicate |
|---|---|---|---|
| HTTP -> orders DB | local transaction | rollback | unique orderId |
| orders DB -> outbox | atomic cùng DB | retry publisher | outbox eventId |
| outbox -> Kafka | at-least-once | publish lại | idempotent key |
| Kafka -> payment DB | at-least-once | offset chưa commit | inbox/eventId |
| payment DB -> provider | effectively-once | retry timeout | provider key |
| Kafka -> Kafka | exactly-once có thể | transaction abort | read_committed |

Không viết “end-to-end exactly-once” nếu bảng còn bước ngoài Kafka chưa có transaction/idempotency.

## 18. Bài lab 1 — Tạo duplicate bằng crash trước commit

1. Tạo consumer manual commit.
2. Poll record.
3. Ghi side effect vào bảng kết quả.
4. Dừng process trước commit offset.
5. Restart cùng group.
6. Quan sát side effect duplicate.
7. Thêm eventId unique constraint.
8. Chạy lại và quan sát duplicate bị chặn.

Ghi timeline:

    poll offset 10
    DB side effect committed
    process crash
    restart offset 10
    dedup/duplicate

## 19. Bài lab 2 — processed_events với unique event_id

Tạo bảng:

    CREATE TABLE processed_events (
        event_id VARCHAR(100) PRIMARY KEY,
        processed_at TIMESTAMP NOT NULL,
        result_reference VARCHAR(200)
    );

Consumer transaction:

1. INSERT eventId;
2. nếu duplicate, return;
3. ghi business result;
4. commit;
5. commit Kafka offset.

Test:

- gửi cùng eventId hai lần;
- gửi payload khác nhưng cùng eventId;
- crash giữa các bước;
- kiểm tra business state chỉ thay đổi theo policy.

## 20. Bài lab 3 — Outbox publisher có retry

1. Tạo orders và outbox_events.
2. Trong một DB transaction, insert cả order và outbox.
3. Tạo publisher đọc event chưa published.
4. Gửi Kafka.
5. Cố tình làm Kafka unavailable.
6. Khởi động lại Kafka.
7. Publisher retry.
8. Cố tình crash sau Kafka ack trước mark published.
9. Kiểm tra duplicate publish và consumer dedup.

Báo cáo:

- order có bị mất không;
- outbox event có bị mất không;
- duplicate được xử lý ở đâu;
- cleanup outbox thế nào;
- metric attempts/oldest unpublished.

## 21. Bài lab 4 — Kafka-to-Kafka transactional pipeline

Pipeline:

    input topic -> transformer -> output topic

Yêu cầu:

1. producer bật transaction;
2. read input;
3. transform record;
4. gửi output trong transaction;
5. gửi offset input vào transaction;
6. commit hoặc abort;
7. consumer output dùng read_committed;
8. chạy consumer read_uncommitted để so sánh.

Tạo crash giữa send output và commit transaction. Quan sát:

- read_committed không thấy output aborted;
- read_uncommitted có thể thấy record transaction chưa commit/aborted tùy timing;
- input offset được xử lý lại sau abort;
- output committed không bị duplicate ngoài guarantee pipeline.

## 22. Bài lab 5 — read_uncommitted và read_committed

1. Tạo producer transaction.
2. Gửi record transaction rồi delay trước commit.
3. Chạy consumer isolation.level=read_uncommitted.
4. Chạy consumer isolation.level=read_committed.
5. Abort một transaction.
6. So sánh record thấy được.
7. Ghi rõ consumer nào phù hợp downstream production.

Đừng chỉ kiểm tra console output; ghi lại transaction state, offset và timing.

## 23. Transaction timeout và failure

Tạo transaction processing lâu hơn timeout trong lab. Quan sát:

- commitTransaction fail;
- transaction bị abort;
- consumer cần xử lý lại input;
- output visibility với read_committed;
- log coordinator/producer.

Không chọn timeout dựa trên một record average. Dùng processing p95/p99 và downstream latency.

## 24. Chọn pattern theo use case

### Order service phát event

Dùng:

    orders DB + outbox publisher + Kafka

Lý do: order và intent publish được commit cùng nhau.

### Payment consumer cập nhật DB

Dùng:

    Kafka + inbox/processed_events + DB transaction

Lý do: duplicate event không charge/update hai lần.

### Payment gọi external provider

Dùng:

    eventId/paymentId làm idempotency key

Lý do: Kafka transaction không bao trùm provider.

### Kafka-to-Kafka transform

Dùng:

    Kafka transaction + read_committed

Lý do: read-process-write nằm trong Kafka boundary.

### Đồng bộ search index

Tùy business:

    at-least-once + upsert theo entity version

Lý do: index update thường có thể idempotent.

## 25. Sai lầm phổ biến

### Gọi mọi pipeline là exactly-once

Kafka transaction không bao trùm DB/API/email.

### Bật idempotent producer và dừng ở đó

Producer idempotence không bảo vệ consumer side effect.

### Commit offset cùng lúc với DB bằng niềm tin

Hai hệ thống không atomic nếu không có transaction integration.

### Outbox nhưng không dedup publisher

Crash sau Kafka ack có thể publish lại.

### Inbox chỉ lưu sau business update

Nếu marker không cùng transaction, crash vẫn tạo duplicate.

### Dùng read_uncommitted cho output transactional

Consumer có thể thấy dữ liệu transaction abort.

### Transaction quá dài

Tăng lock/resource pressure và timeout risk.

### Reuse transactional.id sai

Các producer instance fencing lẫn nhau hoặc mất semantics.

### Replay không kiểm tra side effect

Có thể chạy payment/email lần hai.

### Unique eventId nhưng eventId không ổn định

Retry tạo ID mới thì dedup không có tác dụng.

## 26. Câu hỏi tự kiểm tra

1. At-most-once commit ở thời điểm nào?
2. At-least-once vì sao duplicate?
3. Exactly-once Kafka áp dụng trong phạm vi nào?
4. Idempotent producer giải quyết gì?
5. transactional.id dùng để làm gì?
6. Transaction timeout ảnh hưởng thế nào?
7. read_committed khác read_uncommitted ra sao?
8. Vì sao Kafka transaction không bao trùm DB?
9. Dual write là gì?
10. Outbox giải quyết khoảng hở nào?
11. Vì sao outbox vẫn có thể duplicate publish?
12. Inbox giải quyết phần nào?
13. CDC khác business event ở đâu?
14. Business idempotency key là gì?
15. Unique constraint giúp gì?
16. Vì sao commit DB rồi mới commit Kafka không atomic?
17. Khi nào Kafka-to-Kafka exactly-once phù hợp?
18. Vì sao replay cần audit và rate limit?
19. Nếu timeout sau payment provider đã charge, cần làm gì?
20. Consistency guarantee table cần ghi những gì?

### Đáp án ngắn

1. Trước business process.
2. Process xong nhưng crash trước commit.
3. Chủ yếu Kafka read-process-write.
4. Một số duplicate do retry trong cùng producer session.
5. Định danh producer transaction và fencing.
6. Transaction quá lâu có thể abort/fail.
7. Chỉ đọc transaction committed so với cả record chưa committed/aborted.
8. DB và Kafka có coordinator/state khác nhau.
9. Ghi DB và publish Kafka trong hai hệ thống không atomic.
10. Ghi business change và event intent cùng DB transaction.
11. Crash sau Kafka ack trước mark published.
12. Dedup event trước side effect DB.
13. CDC là row change; business event mang semantics nghiệp vụ.
14. Key ổn định để retry operation không tạo side effect mới.
15. Chặn cùng event/operation được apply nhiều lần.
16. Hai commit độc lập.
17. Khi toàn bộ pipeline nằm trong Kafka boundary.
18. Để không replay mù và gây duplicate.
19. Retry cùng idempotency key/check status.
20. Guarantee, failure behavior và duplicate protection.

## 27. Checklist hoàn thành Tuần 9

- [ ] Tôi giải thích được at-most-once.
- [ ] Tôi giải thích được at-least-once.
- [ ] Tôi hiểu duplicate window trước commit.
- [ ] Tôi phân biệt Kafka exactly-once với end-to-end exactly-once.
- [ ] Tôi cấu hình được idempotent producer.
- [ ] Tôi hiểu transactional.id và fencing.
- [ ] Tôi biết transaction timeout ảnh hưởng ra sao.
- [ ] Tôi dùng được read_committed trong Kafka pipeline.
- [ ] Tôi nhận diện được database–Kafka dual write.
- [ ] Tôi mô tả được transactional outbox.
- [ ] Tôi mô tả được inbox/processed_events.
- [ ] Tôi hiểu CDC khác business event.
- [ ] Tôi dùng eventId/business idempotency key ổn định.
- [ ] Tôi dùng unique constraint để chống duplicate.
- [ ] Tôi hoàn thành lab crash trước commit.
- [ ] Tôi dựng được outbox publisher retry.
- [ ] Tôi test được Kafka-to-Kafka transaction.
- [ ] Tôi có consistency guarantee table cho capstone.
- [ ] Tôi không quảng cáo exactly-once end-to-end thiếu căn cứ.

## 28. Câu chốt cần nhớ

Delivery semantics chỉ có ý nghĩa khi đặt trong một failure window cụ thể. Kafka có thể cung cấp exactly-once trong phạm vi Kafka transaction, nhưng database và external API vẫn cần outbox, inbox, idempotency hoặc transaction integration riêng.

Năm ý cần nhớ:

1. At-most-once có thể mất record; at-least-once có thể duplicate.
2. Idempotency là cách biến duplicate kỹ thuật thành kết quả nghiệp vụ an toàn.
3. Kafka transaction không tự động bao trùm database transaction.
4. Outbox bảo vệ DB change và event intent; inbox bảo vệ consumer side effect.
5. Luôn ghi rõ guarantee từng bước thay vì nói chung chung “exactly once”.


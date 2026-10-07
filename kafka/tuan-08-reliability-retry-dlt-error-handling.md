# Tuần 8 — Reliability: retry, DLT và error handling

> Mục tiêu: thiết kế được error flow có retry, backoff, DLT và replay có kiểm soát; không biến DLT thành nơi vứt mọi record lỗi.

## 1. Kết quả cần đạt

Sau bài học, bạn có thể:

- Phân biệt transient error, permanent error và poison record.
- Chọn blocking retry hoặc non-blocking retry.
- Thiết kế fixed, exponential và exponential backoff có jitter.
- Cấu hình DefaultErrorHandler ở mức cơ bản.
- Dùng DeadLetterPublishingRecoverer để đưa record không xử lý được vào DLT.
- Hiểu retry topic và tác động tới ordering.
- Ghi metadata đầy đủ vào DLT.
- Quyết định exception nào retry, exception nào recover ngay.
- Replay DLT sau khi sửa bug mà không tạo side effect trùng lặp.
- Viết runbook audit/replay và metric cho error pipeline.
- Kiểm thử retry, DLT, poison record và downstream outage.

## 2. Vì sao cần error handling?

Một consumer thực tế có thể thất bại vì:

- database tạm thời unavailable;
- HTTP downstream timeout;
- rate limit;
- payload sai schema;
- dữ liệu nghiệp vụ không hợp lệ;
- service bug;
- record quá lớn;
- permission hoặc authentication;
- dependency trả về response không mong đợi.

Nếu không có policy, consumer thường rơi vào một trong hai trạng thái xấu:

### Bỏ qua lỗi

    poll -> listener lỗi -> nuốt exception -> commit offset

Hậu quả:

- mất event ở góc nhìn business;
- không có audit;
- không thể biết record nào lỗi;
- downstream state không đầy đủ.

### Retry vô hạn tại cùng một partition

    record lỗi -> retry
    record lỗi -> retry
    record lỗi -> retry
    record sau không được xử lý

Hậu quả:

- poison record chặn cả partition;
- lag tăng mãi;
- consumer có thể rebalance;
- downstream bị bão request;
- operator không biết phải can thiệp ở đâu.

Reliability cần một flow có giới hạn:

    consume
       |
       v
    process
       |
       +--> success -> commit
       |
       +--> transient failure -> retry/backoff
       |
       +--> permanent failure -> recover/DLT
       |
       +--> poison record -> DLT + alert

## 3. Phân loại lỗi

### 3.1 Transient error

Lỗi có khả năng tự hết:

- database connection pool tạm hết;
- network timeout;
- broker leader election;
- downstream 503;
- rate limit có retry-after;
- dependency restart ngắn hạn.

Xử lý thường:

- retry có giới hạn;
- backoff;
- jitter;
- circuit breaker;
- metric và alert;
- không giữ partition kẹt quá lâu.

### 3.2 Permanent error

Retry không làm dữ liệu đúng hơn:

- thiếu orderId bắt buộc;
- currency không hợp lệ;
- event version không được hỗ trợ;
- permission bị từ chối;
- business rule bị vi phạm;
- schema incompatible;
- topic/route sai.

Xử lý thường:

- không retry hoặc retry rất ít;
- đưa DLT/error topic;
- alert owner;
- sửa dữ liệu hoặc producer;
- replay sau khi có quyết định.

### 3.3 Poison record

Poison record là record cứ làm consumer fail lặp lại:

- JSON malformed;
- payload quá lớn;
- field gây exception;
- dữ liệu làm bug code;
- side effect không thể hoàn tất.

Poison record nguy hiểm vì nó có thể chặn cả partition. Cần giới hạn retry và đưa record ra DLT thay vì để consumer mắc kẹt vô hạn.

### 3.4 Lỗi không chắc chắn

Timeout là ví dụ quan trọng:

1. consumer gọi downstream;
2. downstream đã xử lý;
3. response bị mất;
4. consumer thấy timeout;
5. retry có thể tạo duplicate.

Retry policy phải đi với idempotency key hoặc deduplication. Không coi timeout đơn giản là “chưa xử lý”.

## 4. Retry là gì?

Retry là cố gắng xử lý lại record sau lỗi. Retry không phải luôn đúng; nó chỉ phù hợp khi:

- lỗi có khả năng tạm thời;
- request retry không tạo side effect không kiểm soát;
- có giới hạn attempts;
- có backoff;
- có deadline;
- có nơi recover khi hết attempts.

Một retry policy nên xác định:

- exception nào retry;
- số lần tối đa;
- delay ban đầu;
- multiplier;
- delay tối đa;
- jitter;
- timeout tổng;
- DLT/error topic;
- metric và alert;
- replay procedure.

## 5. Blocking retry

### 5.1 Mô hình

Consumer giữ record tại listener và chờ giữa các lần retry:

    record R
      |
      +--> process fail
      |    wait 1s
      +--> process fail
           wait 2s
      +--> process fail
           -> DLT

### 5.2 Ưu điểm

- dễ hiểu;
- giữ record trong flow hiện tại;
- có thể giữ ordering trong partition tốt hơn;
- không cần retry topic riêng;
- phù hợp retry ngắn.

### 5.3 Nhược điểm

- giữ partition bị block;
- consumer thread chờ, ảnh hưởng poll interval;
- downstream outage lâu làm lag tăng;
- có thể gây rebalance nếu tổng thời gian quá dài;
- không phù hợp retry nhiều phút/giờ.

Blocking retry cần tính:

    tổng thời gian retry + processing
    < giới hạn poll/ownership phù hợp

Đừng dùng blocking retry cho outage dài nếu có thể chuyển sang retry topic hoặc DLT.

## 6. Non-blocking retry và retry topic

### 6.1 Mô hình

Thay vì giữ record trong partition, publish sang topic retry:

    main topic
        |
        +--> process fail
                |
                v
        orders.retry.1m
                |
                v
        orders.retry.10m
                |
                v
        main processing hoặc DLT

Consumer retry topic đọc record sau delay/logical schedule.

### 6.2 Ưu điểm

- không block partition chính lâu;
- phù hợp delay dài;
- downstream chính vẫn có thể xử lý record khác;
- retry delay có thể quan sát như topic;
- dễ tách retry policy theo loại lỗi.

### 6.3 Nhược điểm

- tăng số topic và vận hành;
- ordering giữa main và retry có thể thay đổi;
- duplicate dễ xảy ra;
- cần metadata original topic/partition/offset;
- partition mapping retry cần thiết kế;
- replay phức tạp hơn.

### 6.4 Retry topic làm thay đổi ordering

Giả sử partition có:

    offset 10 -> lỗi, gửi retry
    offset 11 -> thành công

Nếu offset 11 được xử lý trước khi offset 10 quay lại từ retry topic, ordering ban đầu không còn được giữ.

Nếu ordering theo aggregate bắt buộc:

- blocking retry có thể phù hợp hơn;
- pause partition/aggregate;
- dùng retry cùng partition key;
- tách workflow;
- chấp nhận xử lý theo version/sequence ở downstream.

Không dùng non-blocking retry mà vẫn quảng cáo ordering tuyệt đối.

## 7. Backoff

### 7.1 Fixed backoff

    retry 1: chờ 1s
    retry 2: chờ 1s
    retry 3: chờ 1s

Đơn giản, phù hợp lỗi ngắn và tải nhỏ.

### 7.2 Exponential backoff

    retry 1: 1s
    retry 2: 2s
    retry 3: 4s
    retry 4: 8s

Giảm áp lực lên dependency khi outage kéo dài.

### 7.3 Exponential với max delay

    delay = min(initial * multiplier^attempt, maxDelay)

Cần có max delay để tránh một record bị giữ quá lâu ngoài dự kiến.

### 7.4 Jitter

Nếu hàng nghìn consumer retry cùng lúc, backoff giống nhau có thể tạo thundering herd. Jitter thêm ngẫu nhiên nhỏ:

    delay thực tế = backoff + random(0, jitter)

Jitter giúp phân tán request retry theo thời gian.

### 7.5 Retry budget

Đừng chỉ giới hạn attempts. Cần giới hạn cả:

- tổng thời gian;
- tổng request;
- số record retry đồng thời;
- tải lên downstream;
- số lần retry trên mỗi partition.

## 8. Spring Kafka DefaultErrorHandler

API chính xác phụ thuộc version Spring Kafka. Ý tưởng cấu hình:

    FixedBackOff backOff =
        new FixedBackOff(1000L, 2L);

    DefaultErrorHandler errorHandler =
        new DefaultErrorHandler(backOff);

    factory.setCommonErrorHandler(errorHandler);

Ví dụ này retry với delay cố định theo số lần đã cấu hình. Trong project thật, cần kiểm tra:

- version Spring Kafka;
- exception classification;
- seek behavior;
- ack mode;
- batch/record listener;
- recoverer;
- transaction setting.

### 8.1 Retry exception chọn lọc

    DefaultErrorHandler handler =
        new DefaultErrorHandler(recoverer, backOff);

    handler.addRetryableExceptions(
        TransientDownstreamException.class,
        SocketTimeoutException.class
    );

    handler.addNotRetryableExceptions(
        InvalidOrderException.class,
        UnsupportedEventVersionException.class
    );

Tên exception chỉ minh họa. Không bắt Exception chung rồi retry mọi thứ.

### 8.2 Error handler cần quan sát được

Ghi metric/log:

- topic;
- partition;
- offset;
- key;
- eventId;
- exception class;
- attempt;
- first failure time;
- next retry time;
- recover destination;
- original timestamp.

Không log toàn payload nếu có PII hoặc secret.

## 9. DeadLetterPublishingRecoverer

DLPR là cơ chế publish record không xử lý được sang destination khác sau khi hết retry hoặc được phân loại recover ngay.

Khái niệm:

    main topic
        |
        +--> retry exhausted
                |
                v
        main topic.DLT

Ví dụ skeleton:

    DeadLetterPublishingRecoverer recoverer =
        new DeadLetterPublishingRecoverer(
            kafkaTemplate,
            (record, exception) ->
                new TopicPartition(
                    record.topic() + ".DLT",
                    record.partition()
                )
        );

    DefaultErrorHandler errorHandler =
        new DefaultErrorHandler(
            recoverer,
            new FixedBackOff(1000L, 2L)
        );

    factory.setCommonErrorHandler(errorHandler);

Destination resolver cần được thiết kế rõ:

- giữ partition hay phân bố lại;
- DLT theo topic hay theo domain;
- tên DLT version/environment;
- topic có đủ partition không;
- producer publish DLT có serializer phù hợp không.

### 9.1 DLT metadata

DLT nên giữ hoặc thêm:

- original topic;
- original partition;
- original offset;
- original key;
- eventId;
- exception class;
- exception message an toàn;
- stack trace hoặc error code tùy policy;
- failed timestamp;
- retry attempts;
- service/consumer group;
- correlationId;
- schema/event version.

Spring Kafka có thể thêm metadata headers tùy recoverer/configuration. Hãy kiểm tra actual headers bằng consumer CLI/test.

### 9.2 DLT không phải thùng rác

Mỗi DLT cần:

- owner;
- retention;
- alert;
- dashboard;
- classification;
- replay policy;
- access control;
- runbook;
- cleanup policy;
- audit.

Nếu không ai xem DLT, hệ thống chỉ đang che lỗi.

## 10. DLT naming và ownership

Ví dụ:

    prod.orders.order-created.v1.DLT

Hoặc tách rõ:

    prod.orders.order-created.v1.dlt

Một số team dùng retry topic:

    prod.orders.order-created.v1.retry.1m
    prod.orders.order-created.v1.retry.10m
    prod.orders.order-created.v1.dlt

Naming phải thống nhất. Ghi:

| Topic | Owner | Retry | DLT | Retention | Replay |
|---|---|---|---|---|---|
| prod.orders.order-created.v1 | order-team | yes | yes | 14d | approved |
| prod.orders.order-created.v1.dlt | order-team | no | yes | 30d | runbook |

Không dùng một DLT chung cho mọi domain nếu làm mất context và ownership.

## 11. Retry policy theo exception

Ví dụ:

| Exception | Loại | Policy |
|---|---|---|
| SocketTimeoutException | transient | exponential retry 3 lần |
| HTTP 503 | transient | retry + jitter |
| HTTP 400 | permanent | DLT ngay |
| InvalidOrderException | permanent | DLT ngay |
| JsonParseException | poison/schema | recover ngay + alert |
| DuplicateKeyException | có thể expected | idempotency path |
| AuthenticationException | configuration | dừng/alert, không retry mù |
| UnsupportedEventVersionException | permanent | DLT + schema owner |

Policy cần dựa trên nguyên nhân, không chỉ tên exception. Một SocketTimeout có thể do dependency chết lâu; retry 100 lần là sai.

## 12. Retry, offset và commit

Với record listener:

    poll
      |
      +--> listener fail
              |
              +--> retry succeeds -> commit theo container
              |
              +--> recover to DLT -> commit original offset
              |
              +--> error handler misconfigured -> loop

Nếu recover thành công vào DLT nhưng original offset không tiến, consumer có thể đọc lại record vô hạn. Error handler phải phối hợp với acknowledgment/seek policy để record được xem là recovered.

Nếu publish DLT cũng fail:

- không được coi record đã recover;
- cần retry DLT publish;
- cần alert;
- cần tránh mất original record;
- có thể giữ partition blocked tới khi recover thành công hoặc có fallback.

## 13. Retry và idempotency

Retry có thể gọi side effect nhiều lần:

    consumer -> payment API timeout
    payment API đã charge
    consumer retry -> charge lần hai

Bảo vệ:

- idempotency key = eventId/paymentId;
- provider hỗ trợ dedup;
- processed_events unique constraint;
- upsert;
- transactional inbox;
- kiểm tra current business state;
- version/sequence validation.

DLT không thay thế idempotency. DLT chỉ giúp tách record lỗi khỏi luồng chính.

## 14. Poison record và partition blocking

Nếu record offset 10 luôn lỗi:

    offset 10 fail
    offset 11 không được xử lý
    offset 12 không được xử lý

Đây là behavior có thể đúng nếu ordering partition quan trọng, nhưng phải có giới hạn:

- retry count;
- DLT;
- alert;
- operator decision;
- pause hoặc quarantine;
- replay sau sửa.

Nếu chuyển offset 10 sang DLT rồi commit, offset 11 có thể tiếp tục. Điều này giải phóng partition nhưng có thể làm business order bị thiếu. Cần quyết định theo domain.

## 15. Batch listener và error handling

Batch listener làm error semantics phức tạp:

- một record lỗi có thể fail toàn batch;
- retry toàn batch gây duplicate record đã thành công;
- commit offset batch có thể bỏ qua record;
- partial batch recovery cần API/config phù hợp version.

Nếu cần xử lý lỗi theo record, record listener thường dễ reasoning hơn. Dùng batch khi bạn có chiến lược partial failure rõ ràng.

## 16. Non-blocking retry và ordering theo aggregate

Nếu retry topic làm record quay lại sau, event sau của cùng order có thể được xử lý trước event lỗi.

Các lựa chọn:

1. chấp nhận eventual ordering và dùng event version;
2. xử lý tuần tự theo aggregate;
3. pause aggregate/partition;
4. retry blocking ngắn;
5. tách workflow thành state machine;
6. dùng topic retry có partition key ổn định;
7. giữ failed event trong DLT và không xử lý bước sau cho tới khi operator xử lý.

Không có retry architecture nào vừa delay dài, throughput tối đa và ordering tuyệt đối miễn phí.

## 17. Replay DLT có kiểm soát

### 17.1 Trước replay

Cần biết:

- vì sao record vào DLT;
- lỗi đã được sửa chưa;
- consumer version nào sẽ đọc;
- side effect đã xảy ra một phần chưa;
- eventId có dedup không;
- replay toàn DLT hay một subset;
- downstream có capacity không;
- replay vào topic gốc hay topic staging;
- ai approve.

### 17.2 Quy trình replay

1. lọc theo thời gian, topic, partition, offset, eventId hoặc error code;
2. copy sang replay topic hoặc tạo replay group;
3. chạy dry-run nếu có;
4. giới hạn tốc độ;
5. theo dõi success/error/duplicate;
6. ghi audit batch replay;
7. xác minh business result;
8. chỉ cleanup DLT sau retention/runbook.

### 17.3 Không replay mù

Nếu payment đã thành công nhưng consumer timeout rồi vào DLT, replay mù có thể charge lần hai. Cần provider idempotency hoặc kiểm tra trạng thái trước side effect.

## 18. Error handling configuration mẫu

### 18.1 Recover DLT sau fixed retry

    @Bean
    public DefaultErrorHandler errorHandler(
        KafkaTemplate<Object, Object> kafkaTemplate
    ) {
        DeadLetterPublishingRecoverer recoverer =
            new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, exception) ->
                    new TopicPartition(
                        record.topic() + ".DLT",
                        record.partition()
                    )
            );

        FixedBackOff backOff =
            new FixedBackOff(1000L, 2L);

        DefaultErrorHandler handler =
            new DefaultErrorHandler(recoverer, backOff);

        handler.addNotRetryableExceptions(
            InvalidOrderException.class,
            UnsupportedEventVersionException.class
        );

        return handler;
    }

Tên bean và generic type có thể cần điều chỉnh theo app. Đừng copy nguyên mẫu nếu KafkaTemplate serializer không thể xử lý DLT value/headers.

### 18.2 Exponential backoff

    ExponentialBackOff backOff =
        new ExponentialBackOff(1000L, 2.0);

    backOff.setMaxInterval(30000L);

    DefaultErrorHandler handler =
        new DefaultErrorHandler(recoverer, backOff);

Kiểm tra constructor/API của version đang dùng. Một số behavior retry không blocking cần dùng cơ chế khác.

## 19. Bài lab 1 — Retry 3 lần với exponential backoff

1. Tạo listener xử lý OrderCreated.
2. Với một eventId đặc biệt, throw TransientDownstreamException.
3. Cấu hình retry 3 lần.
4. Dùng exponential backoff 1s, 2s, 4s.
5. Log attempt, timestamp và offset.
6. Cho lần thứ tư thành công.
7. Kiểm tra original offset được commit.
8. Đếm side effect không bị chạy sai số lần.

Báo cáo:

- tổng thời gian;
- số attempts;
- partition bị block bao lâu;
- lag;
- behavior khi process crash giữa retry.

## 20. Bài lab 2 — Đưa record lỗi sang DLT

1. Cấu hình recoverer.
2. Tạo InvalidOrderException.
3. Cấu hình exception không retry.
4. Gửi record invalid.
5. Kiểm tra topic DLT.
6. Consume DLT và in headers.
7. Kiểm tra original topic/partition/offset/eventId.
8. Kiểm tra consumer group chính tiếp tục record sau.

Nếu DLT topic chưa tồn tại, xác định app có auto-create hay cần provision trước. Không phụ thuộc auto-create trong production.

## 21. Bài lab 3 — Phân loại retry/recover

Tạo ba event:

- transient event: retry rồi thành công;
- permanent event: DLT ngay;
- poison event: fail mọi attempt rồi DLT.

Kiểm tra:

- số lần listener được gọi;
- destination cuối;
- offset;
- lag;
- metric theo exception;
- alert cho poison record.

## 22. Bài lab 4 — Replay DLT sau khi sửa bug

1. Đưa record lỗi vào DLT.
2. Sửa logic consumer.
3. Chọn một subset DLT.
4. Copy sang replay topic/group.
5. Chạy replay với tốc độ giới hạn.
6. Assert business state đúng.
7. Kiểm tra eventId không tạo side effect lặp.
8. Ghi audit replay.

Không replay toàn DLT nếu chưa biết mỗi record có side effect trước đó hay chưa.

## 23. Bài lab 5 — Retry topic và ordering

Tạo event cùng orderId:

    OrderCreated
    PaymentAuthorized
    OrderCompleted

Làm cho PaymentAuthorized fail và đi retry topic. Cho OrderCompleted xử lý thành công từ main topic.

Quan sát:

- OrderCompleted có thể đến trước PaymentAuthorized retry;
- ordering ban đầu bị thay đổi;
- consumer cần sequence/state check hay không;
- policy nào phù hợp nếu order workflow cần thứ tự tuyệt đối.

## 24. Metrics và dashboard

Tối thiểu:

- listener success count;
- listener failure count;
- retry count;
- retry delay;
- DLT publish count;
- DLT publish failure;
- DLT depth/lag;
- poison record count;
- processing latency;
- time since oldest DLT record;
- replay count;
- duplicate detection count.

Dashboard nên chia:

- main topic consumer health;
- retry topic health;
- DLT health;
- downstream dependency health;
- replay operations.

Alert nên có:

- DLT mới xuất hiện;
- DLT tăng nhanh;
- một partition retry quá lâu;
- DLT publish thất bại;
- oldest DLT record vượt SLA;
- retry rate vượt baseline.

## 25. Runbook xử lý DLT

### Khi DLT có record

1. xác định topic/partition/offset/eventId;
2. đọc exception class và error code;
3. kiểm tra payload/schema version;
4. kiểm tra side effect đã xảy ra chưa;
5. xác định transient hay permanent;
6. tìm owner;
7. quyết định fix data, fix code hay bỏ qua có audit;
8. chạy replay subset;
9. xác minh kết quả;
10. ghi incident/replay audit.

### Không làm

- xóa DLT để dashboard về 0;
- replay toàn bộ không có approval;
- retry vô hạn;
- sửa payload trực tiếp mà không audit;
- bỏ qua eventId;
- dùng DLT làm storage vĩnh viễn.

## 26. Sai lầm phổ biến

### Retry mọi exception

Lỗi schema hoặc validation không tự hết.

### Backoff không có giới hạn

Một record có thể giữ partition quá lâu.

### DLT không có owner

Record nằm đó nhưng không ai xử lý.

### DLT publish failure bị nuốt

Có thể mất record ở bước recover.

### Retry topic nhưng vẫn quảng cáo ordering tuyệt đối

Non-blocking retry có thể làm event sau xử lý trước event lỗi.

### Replay DLT mù

Payment/email có thể bị gọi lần hai.

### Không lưu original metadata

Không biết record đến từ đâu và commit/replay thế nào.

### Dùng stack trace đầy đủ chứa dữ liệu nhạy cảm

DLT headers/log có thể làm lộ PII hoặc secret.

### Không giới hạn DLT retention

DLT cũng tiêu tốn disk và cần cleanup policy.

### Chỉ test success path

Reliability phải test crash, timeout, retry exhausted, DLT publish failure và replay.

## 27. Câu hỏi tự kiểm tra

1. Transient, permanent và poison record khác nhau thế nào?
2. Khi nào blocking retry phù hợp?
3. Khi nào nên dùng retry topic?
4. Vì sao retry topic có thể phá ordering?
5. Fixed và exponential backoff khác nhau thế nào?
6. Jitter giải quyết vấn đề gì?
7. DefaultErrorHandler có vai trò gì?
8. DeadLetterPublishingRecoverer làm gì?
9. DLT cần metadata nào?
10. Vì sao DLT không phải thùng rác?
11. Commit offset diễn ra thế nào sau khi recover vào DLT?
12. Nếu DLT publish thất bại thì xử lý sao?
13. Vì sao timeout cần idempotency?
14. Batch listener làm retry phức tạp hơn thế nào?
15. Replay DLT cần kiểm tra gì trước?
16. Làm sao tránh duplicate khi replay?
17. Vì sao không retry AuthenticationException vô hạn?
18. DLT cần metric/alert nào?

### Đáp án ngắn

1. Tạm thời, không tự hết, và record làm fail lặp lại.
2. Lỗi ngắn, cần giữ ordering và delay nhỏ.
3. Delay dài hoặc không muốn block partition chính.
4. Record sau có thể xử lý trước record retry.
5. Fixed giữ delay; exponential tăng delay theo attempt.
6. Tránh nhiều consumer retry cùng lúc.
7. Định nghĩa retry/recover behavior quanh listener.
8. Publish record thất bại sang destination DLT.
9. Original topic/partition/offset, eventId, exception, attempt, timestamp.
10. Vì DLT cần owner, retention, alert và replay policy.
11. Chỉ commit khi recover thành công và error handler hoàn tất.
12. Không coi recovered; retry publish/alert/fallback.
13. Side effect có thể đã xảy ra dù caller timeout.
14. Retry nguyên batch có thể duplicate record đã thành công.
15. Root cause, fix, side effect, subset, capacity, approval.
16. eventId, dedup, idempotency key, state check.
17. Auth/config lỗi không tự khỏi bằng retry.
18. Rate, depth, oldest age, publish failure, replay và duplicate.

## 28. Checklist hoàn thành Tuần 8

- [ ] Tôi phân loại được transient, permanent và poison error.
- [ ] Tôi có retry policy theo exception.
- [ ] Tôi hiểu blocking và non-blocking retry.
- [ ] Tôi cấu hình được fixed backoff.
- [ ] Tôi cấu hình được exponential backoff có giới hạn.
- [ ] Tôi hiểu vai trò của jitter.
- [ ] Tôi dùng được DefaultErrorHandler ở mức cơ bản.
- [ ] Tôi đưa được record exhausted vào DLT.
- [ ] Tôi kiểm tra được DLT metadata.
- [ ] Tôi không retry validation/schema vô hạn.
- [ ] Tôi hiểu retry topic có thể làm mất ordering.
- [ ] Tôi có idempotency cho side effect retry.
- [ ] Tôi có runbook replay DLT.
- [ ] Tôi replay subset có audit và rate limit.
- [ ] Tôi có metric/alert cho retry và DLT.
- [ ] Tôi kiểm tra được DLT publish failure.
- [ ] Tôi không dùng DLT như thùng rác.

## 29. Câu chốt cần nhớ

Reliability không có nghĩa là retry mọi thứ. Reliability là biết lỗi nào đáng retry, retry trong bao lâu, khi nào dừng, lưu record lỗi ở đâu và replay thế nào mà không tạo side effect lần hai.

Năm ý cần nhớ:

1. Retry phải có phân loại lỗi, số lần tối đa, backoff và deadline.
2. Poison record cần được tách ra để không chặn partition vô hạn.
3. DLT là một workflow cần owner, metric, retention và runbook.
4. Retry topic có thể hy sinh ordering, nên phải ghi rõ guarantee.
5. Replay chỉ an toàn khi đã kiểm tra nguyên nhân lỗi, side effect và idempotency.


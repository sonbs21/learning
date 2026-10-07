# Tuần 1 — Nền tảng Event Streaming

> Mục tiêu: hiểu Kafka đang giải quyết bài toán gì trước khi học CLI, partition, producer và consumer bằng code.

## 1. Kết quả cần đạt

Sau bài học, bạn có thể:

- Phân biệt event, command, fact, notification và audit log.
- Mô tả event-driven architecture ở mức hệ thống.
- Giải thích Kafka như một distributed append-only log.
- Hiểu record, offset, cursor, retention, replay và consumer group.
- Phân biệt queue, pub/sub, database polling và event streaming.
- Giải thích at-most-once, at-least-once và effectively-once.
- Nhận diện nguyên nhân gây mất event, duplicate và xử lý chậm.
- Chọn Kafka hoặc công cụ khác cho một use case cụ thể.

## 2. Bức tranh lớn

Giả sử có luồng đặt hàng:

    Client
      |
      v
    Order Service ----> Database
      |
      +----> Payment Service
      +----> Inventory Service
      +----> Notification Service

Nếu Order Service gọi trực tiếp cả ba service, nó phụ thuộc vào trạng thái online, thời gian phản hồi, retry và lỗi của từng service.

Event streaming đưa thêm một lớp lưu giữ sự kiện:

    Client
      |
      v
    Order Service ---> OrderCreated stream
                             |
                             +--> Payment Service
                             +--> Inventory Service
                             +--> Notification Service

Order Service chỉ cần phát ra sự kiện. Mỗi consumer tự đọc và xử lý theo tốc độ của mình. Dữ liệu không bị xóa ngay khi một consumer đọc xong; nó được lưu theo retention và có thể đọc lại.

> Message queue thường nhấn mạnh việc giao một message cho worker. Event streaming nhấn mạnh việc lưu chuỗi sự kiện để nhiều consumer độc lập đọc, xử lý và replay.

## 3. Event, command và các loại message

### 3.1 Event

Event là sự thật đã xảy ra trong quá khứ. Tên event thường dùng thì quá khứ:

    {
      "eventType": "OrderCreated",
      "orderId": "ORD-1001",
      "customerId": "CUS-42",
      "totalAmount": 1250000,
      "occurredAt": "2026-10-04T09:30:00Z"
    }

Đặc điểm:

- đã xảy ra, không phải yêu cầu;
- có thể được nhiều consumer sử dụng;
- nên có định danh và thời điểm xảy ra;
- cần được thiết kế để có thể xử lý lại an toàn.

Ví dụ: OrderCreated, PaymentAuthorized, InventoryReserved, UserEmailChanged.

### 3.2 Command

Command là yêu cầu một thành phần thực hiện hành động:

    {
      "commandType": "ReserveInventory",
      "orderId": "ORD-1001",
      "items": [
        {"sku": "BOOK-01", "quantity": 2}
      ]
    }

| Tiêu chí | Command | Event |
|---|---|---|
| Ý nghĩa | Hãy làm việc này | Việc này đã xảy ra |
| Người phát | Caller hoặc orchestrator | Service tạo ra thay đổi |
| Người nhận | Thường có một owner chính | Có thể có nhiều consumer |
| Ví dụ | ReserveInventory | InventoryReserved |

Kafka có thể truyền cả command, nhưng cần gọi đúng tên để tránh thiết kế sai kỳ vọng.

### 3.3 Fact, notification và audit log

- Fact: sự thật nghiệp vụ đã xảy ra, ví dụ PaymentCaptured.
- Notification: thông báo có thay đổi, đôi khi payload chỉ có ID.
- Audit log: lịch sử phục vụ truy vết, compliance hoặc điều tra; thường cần actor, thời gian, lý do và correlation ID.

Một event có thể đồng thời là fact và audit log, nhưng không nên mặc định mọi event nghiệp vụ đã đáp ứng yêu cầu audit.

## 4. Event-driven architecture

### 4.1 Request/response truyền thống

    Order API --HTTP--> Payment API
              <--HTTP--

Mô hình này phù hợp khi caller cần câu trả lời ngay, như đăng nhập, lấy giá hoặc kiểm tra tồn kho đồng bộ.

Khi dùng cho mọi thứ, hệ thống dễ gặp:

- coupling thời gian: caller phải chờ;
- failure propagation: một lỗi lan qua nhiều service;
- khó fan-out: thêm consumer thường phải sửa caller;
- khó replay: request cũ không nhất thiết còn đủ dữ liệu.

### 4.2 Mô hình event-driven

    Order Service --publish--> OrderCreated
                                  |
                  +---------------+---------------+
                  |               |               |
                  v               v               v
            Payment Service  Inventory Service  Analytics Service

Ưu điểm:

- producer không cần biết tất cả consumer;
- một event phục vụ nhiều nhóm consumer;
- consumer xử lý bất đồng bộ;
- thêm consumer mới không nhất thiết sửa producer;
- event được giữ lại nên có thể replay.

Đổi lại, cần chấp nhận:

- dữ liệu giữa service có thể nhất quán theo thời gian;
- event có thể được xử lý nhiều lần;
- thứ tự chỉ được bảo đảm trong phạm vi phù hợp;
- cần theo dõi lag, retry, dead-letter và schema;
- debug phải lần theo correlation ID và offset.

### 4.3 Event sourcing ở mức khái niệm

Event sourcing lưu event nghiệp vụ làm nguồn sự thật chính, thay vì chỉ lưu trạng thái cuối cùng:

    AccountOpened(ACC-1, balance=0)
    MoneyDeposited(ACC-1, amount=100)
    MoneyWithdrawn(ACC-1, amount=30)

Trạng thái hiện tại được dựng lại bằng cách áp dụng event theo thứ tự: 0 + 100 - 30 = 70.

Kafka phù hợp cho log và replay, nhưng dùng Kafka không tự động biến hệ thống thành event-sourced. Event sourcing còn cần aggregate, snapshot, versioning và invariant.

## 5. Kafka là distributed append-only log

### 5.1 Append-only

Producer ghi record nối tiếp vào cuối log:

    offset: 0        1        2        3        4
            |        |        |        |        |
    log:    [A]      [B]      [C]      [D]      [E]

Consumer giữ vị trí đọc riêng:

    Consumer A: đã đọc đến offset 4
    Consumer B: đã đọc đến offset 2

A đọc xong không làm B mất cơ hội đọc. Đây là nền tảng của fan-out và replay.

### 5.2 Record và offset

Một record có thể có:

- key: định danh hoặc định tuyến;
- value: payload;
- headers: metadata phụ;
- timestamp: thời điểm tạo hoặc ghi;
- offset: vị trí trong partition.

Offset không phải ID toàn cục của event trong cluster. Offset có ý nghĩa trong một partition; phần partition sẽ học sâu hơn ở tuần 3.

### 5.3 Cursor

Cursor là dấu mốc cho biết consumer đã đọc tới đâu:

    Đã xử lý an toàn: 0  1  2  3
    Consumer position:          ^ 4
    Record mới:                       5  6  7

Nếu lưu cursor sau khi xử lý thành công, process restart có thể tiếp tục từ vị trí gần nhất. Nếu lưu trước khi xử lý xong, record có thể bị bỏ qua.

### 5.4 Retention khác delete-after-ack

Queue truyền thống:

    Producer --> Queue --> Consumer --ack--> message bị xóa

Kafka:

    Producer --> Log --> Consumer A đọc
                        Consumer B đọc
                        Log vẫn tồn tại tới khi retention xóa

Retention có thể dựa trên thời gian hoặc kích thước. Consumer mới có thể đọc dữ liệu cũ trong khoảng retention nếu offset phù hợp.

### 5.5 Replay

Replay là đọc lại một phần hoặc toàn bộ dữ liệu đã lưu. Use case:

- rebuild materialized view;
- chạy analytics consumer mới;
- khôi phục state sau sự cố;
- điều tra một khoảng thời gian;
- reprocess sau khi sửa logic.

Replay không tự động an toàn. Consumer phải idempotent hoặc có cơ chế deduplicate, nếu không có thể gửi email trùng hoặc trừ tiền hai lần.

## 6. Queue, pub/sub và consumer group

### 6.1 Queue competing consumers

    Queue
      +--> Worker A nhận message 1
      +--> Worker B nhận message 2

Các worker cạnh tranh, một message thường chỉ được một worker xử lý thành công.

### 6.2 Consumer group của Kafka

Kafka kết hợp hai ý tưởng:

- consumer trong cùng group chia nhau tải;
- group khác nhau đọc độc lập từ cùng topic.

    Topic: OrderCreated
      |
      +--> group=payment:   payment-1, payment-2
      +--> group=inventory: inventory-1
      +--> group=analytics: analytics-1

Trong cùng group, một partition thường chỉ thuộc về một consumer tại một thời điểm. Group payment đọc xong không làm group inventory mất event. Thêm instance vào cùng group giúp tăng khả năng xử lý trong giới hạn số partition.

## 7. Throughput, latency, retention và backpressure

- Throughput: lượng dữ liệu xử lý trong một đơn vị thời gian.
- Latency: thời gian từ lúc event phát sinh đến lúc consumer xử lý hoặc nhìn thấy nó.
- Retention: thời gian hoặc dung lượng record được giữ lại.
- Backpressure: producer nhanh hơn consumer, làm backlog tăng.

Ví dụ:

    Producer: 10.000 records/s
    Consumer:  2.000 records/s
    Kết quả: backlog tăng dần

Cách xử lý có thể là:

- tăng số consumer nếu còn khả năng song song;
- giảm chi phí xử lý;
- batch nhiều record hơn;
- giới hạn tốc độ producer;
- tách workload nặng;
- không để record lỗi làm kẹt cả luồng.

Tăng consumer không luôn giải quyết được vấn đề. Nếu chỉ có một partition hoặc downstream là bottleneck, thêm consumer có thể không giúp gì.

Khi đặt retention, hãy hỏi:

- consumer chậm nhất cần bao lâu để bắt kịp?
- có cần replay trong 7 ngày, 30 ngày hay lâu hơn không?
- dữ liệu có nhạy cảm không?
- chi phí disk và backup là bao nhiêu?

Retention không thay thế backup và không phải archive vô hạn.

## 8. Delivery semantics và duplicate

### 8.1 At-most-once

Mỗi record được xử lý không quá một lần:

    Đọc record -> commit offset -> xử lý

Nếu process chết sau commit nhưng trước xử lý, record có thể bị mất. Phù hợp khi mất một phần dữ liệu có thể chấp nhận, ví dụ một số metric không quan trọng.

### 8.2 At-least-once

Mỗi record được cố gắng xử lý ít nhất một lần:

    Đọc record -> xử lý thành công -> commit offset

Nếu process xử lý xong nhưng chết trước commit, record được đọc lại và có thể duplicate.

Consumer cần idempotency:

- dùng event ID làm unique key;
- dùng upsert;
- lưu bảng deduplication;
- kết hợp ghi kết quả và đánh dấu đã xử lý trong transaction phù hợp.

### 8.3 Exactly-once và effectively-once

Exactly-once phải được hiểu trong phạm vi cụ thể:

- giữa Kafka producer và topic;
- trong pipeline Kafka Streams;
- hay cả side effect bên ngoài như email, HTTP, payment gateway?

Nhiều hệ thống hướng tới effectively-once: dù retry hoặc replay, kết quả nghiệp vụ quan sát được tương đương xử lý một lần.

Ví dụ payment provider hỗ trợ idempotency key:

    Payment Service nhận PaymentRequested
      |
      +--> gọi provider với idempotency key = paymentId
      +--> retry nếu timeout

## 9. Các điểm có thể mất event hoặc tạo duplicate

Với flow:

    HTTP request -> Order Service -> Database -> Kafka -> Consumer -> Database/HTTP

### Có thể mất event

- ghi database nhưng chết trước khi publish;
- producer gửi async nhưng không kiểm tra callback;
- consumer commit offset trước khi xử lý;
- retention xóa event trước khi consumer chậm bắt kịp;
- seek hoặc cấu hình offset ban đầu sai;
- serialization error.

### Có thể duplicate

- producer retry sau timeout dù broker đã ghi;
- consumer xử lý xong nhưng chết trước commit;
- retry HTTP không có idempotency key;
- replay sau khi sửa logic;
- gửi lại event thủ công mà không có event ID.

### Có thể out-of-order

- event liên quan bị phân tán vào các partition;
- nhiều producer ghi cùng aggregate;
- consumer xử lý bất đồng bộ;
- retry làm event cũ đến sau event mới.

## 10. So sánh Kafka với lựa chọn khác

| Công cụ/mô hình | Phù hợp khi | Điểm cần lưu ý |
|---|---|---|
| Kafka | Cần throughput, retention, replay, nhiều consumer, stream processing | Vận hành, partition, lag và schema phức tạp hơn |
| RabbitMQ | Task delivery, routing linh hoạt, ack từng message | Replay và lịch sử dài không phải điểm mạnh chính |
| SQS | Queue managed, muốn giảm vận hành hạ tầng | Ordering và replay phụ thuộc loại queue/dịch vụ |
| Database polling | Traffic thấp, đã có database, cần đơn giản | Tốn query, latency và khó scale |
| HTTP request/response | Cần kết quả ngay, topology đơn giản | Coupling thời gian và khó fan-out |

### Khi nên chọn Kafka?

Kafka là ứng viên tốt khi có nhiều điều kiện:

- nhiều consumer độc lập;
- cần giữ lịch sử và replay;
- throughput lớn hoặc tăng dần;
- cần stream processing hoặc CDC;
- muốn tách producer và consumer theo thời gian;
- dữ liệu có giá trị như log nghiệp vụ;
- muốn scale theo partition.

### Khi không nên chọn Kafka?

Cân nhắc lựa chọn đơn giản hơn khi:

- request cần response ngay;
- chỉ có một worker và traffic thấp;
- task phải ack ngay, không cần replay;
- topology chỉ có một producer và consumer;
- team chưa vận hành được broker và monitoring;
- outbox hoặc queue managed đã đủ.

## 11. Case study: OrderCreated

### 11.1 Luồng nghiệp vụ

1. Client gửi yêu cầu tạo order.
2. Order Service xác thực request.
3. Order Service tạo order ở trạng thái PENDING.
4. Hệ thống phát event OrderCreated.
5. Payment Service tạo yêu cầu thanh toán.
6. Inventory Service giữ hàng.
7. Notification Service gửi email/push.
8. Analytics Service cập nhật báo cáo.

### 11.2 Event mẫu

    {
      "eventId": "evt-01J8-abc",
      "eventType": "OrderCreated",
      "eventVersion": 1,
      "aggregateType": "Order",
      "aggregateId": "ORD-1001",
      "occurredAt": "2026-10-04T09:30:00Z",
      "correlationId": "req-01J8-xyz",
      "producer": "order-service",
      "payload": {
        "customerId": "CUS-42",
        "currency": "VND",
        "totalAmount": 1250000,
        "items": [
          {"sku": "BOOK-01", "quantity": 2, "unitPrice": 625000}
        ]
      }
    }

Ý nghĩa metadata:

- eventId: deduplicate và truy vết;
- eventType: định tuyến/deserialization;
- eventVersion: hỗ trợ schema evolution;
- aggregateId: liên kết với order;
- occurredAt: lúc event xảy ra, không phải lúc consumer đọc;
- correlationId: nối log của một request/workflow;
- producer: xác định nguồn phát.

### 11.3 Dual write

Nếu Order Service làm:

    BEGIN database transaction
      INSERT order
    COMMIT
    publish OrderCreated

process có thể chết giữa COMMIT và publish, tạo order nhưng không có event. Đây là vấn đề dual write. Outbox pattern sẽ học ở phần nâng cao/capstone; tuần này chỉ cần nhận diện đúng rủi ro.

## 12. Bài thực hành 1 — Vẽ flow OrderCreated

Vẽ hai sơ đồ:

1. Flow dùng HTTP gọi trực tiếp Payment, Inventory và Notification.
2. Flow dùng OrderCreated stream.

Trên sơ đồ ghi:

- thành phần phát event;
- thành phần đọc event;
- consumer nào cùng group;
- consumer nào đọc độc lập;
- nơi có thể retry;
- nơi cần idempotency.

Định hướng:

- Payment, Inventory và Notification không nên dùng chung một group nếu cả ba đều phải nhận event.
- Mỗi domain nên có group riêng: payment, inventory, notification.
- Hai instance Payment có thể cùng thuộc group payment.
- Analytics nên là group riêng để replay độc lập.

## 13. Bài thực hành 2 — Queue và Kafka retention

### Kịch bản A: queue

1. Producer gửi M1.
2. Worker A nhận M1.
3. Worker A ack.
4. Worker B khởi động sau đó.

Câu hỏi: Worker B có thể đọc lại M1 không?

### Kịch bản B: Kafka

1. Producer ghi R1 ở offset 10.
2. Group payment đọc offset 10.
3. Group analytics chưa chạy.
4. Group analytics khởi động với chính sách đọc dữ liệu cũ.

Câu hỏi: analytics có thể đọc R1 không?

Kết luận:

- Queue thường xóa message sau ack nên worker sau không còn message.
- Kafka giữ record theo retention; group khác có thể đọc từ offset phù hợp.
- Replay còn phụ thuộc retention, offset và cấu hình consumer.

## 14. Bài thực hành 3 — Mô phỏng log và cursor bằng file

### Bước 1: tạo log

Tạo file events.log:

    0|OrderCreated|ORD-1001
    1|OrderCreated|ORD-1002
    2|PaymentAuthorized|ORD-1001
    3|InventoryReserved|ORD-1001
    4|OrderCreated|ORD-1003

### Bước 2: tạo cursor

Tạo file payment.cursor chứa số 1. Giả sử cursor biểu thị record tiếp theo cần đọc. Consumer payment đọc từ offset 1.

### Bước 3: replay

Đổi cursor về 0 rồi chạy lại consumer. Dữ liệu cũ xuất hiện lại mà log không thay đổi.

### Bước 4: mô phỏng duplicate

1. Đọc offset 1.
2. Ghi kết quả ra processed.log.
3. Không cập nhật cursor.
4. Chạy consumer lần nữa.

Cùng record được xử lý lần hai: đây là mô hình at-least-once.

### Bước 5: mô phỏng mất event

1. Cập nhật cursor trước khi ghi kết quả.
2. Dừng chương trình giữa hai bước.
3. Chạy lại consumer.

Record bị bỏ qua: đây là rủi ro at-most-once.

## 15. Bài thực hành 4 — Bảng failure point

Với flow HTTP -> Order Service -> Order DB -> Kafka -> Payment Consumer -> Payment DB, hãy hoàn thành:

| Điểm | Process chết ở đâu? | Hậu quả | Biện pháp |
|---|---|---|---|
| DB -> Kafka | Sau commit DB, trước publish | Mất event | Outbox, CDC |
| Producer send | Sau broker ghi, trước response | Duplicate khi retry | Idempotent producer, event ID |
| Consumer xử lý | Sau side effect, trước commit offset | Duplicate side effect | Idempotent consumer, dedup |
| Retention | Consumer chậm quá lâu | Không còn dữ liệu replay | Retention phù hợp, alert lag |
| Downstream HTTP | Timeout nhưng request đã thành công | Side effect lặp | Idempotency key |

## 16. Mini project cuối tuần

Thiết kế trên giấy một hệ thống event-driven cho đơn hàng.

Đầu vào:

- Người dùng tạo order.
- Một order có nhiều item.
- Payment, Inventory, Notification và Analytics là consumer khác nhau.

Tài liệu cần nộp:

1. Sơ đồ component.
2. Danh sách event và command.
3. Event schema mẫu cho OrderCreated.
4. Bảng consumer group.
5. Bảng failure point.
6. Quyết định chỗ nào cần at-least-once và idempotency.
7. Hai lý do chọn Kafka và hai lý do không chọn RabbitMQ.

Thiết kế tham khảo:

| Thành phần | Vai trò | Consumer group |
|---|---|---|
| Order Service | Phát OrderCreated | Không áp dụng |
| Payment Service | Xử lý thanh toán | order-payment |
| Inventory Service | Giữ hàng | order-inventory |
| Notification Service | Gửi thông báo | order-notification |
| Analytics Service | Cập nhật báo cáo | order-analytics |

## 17. Câu hỏi tự kiểm tra

1. Event khác command ở điểm nào?
2. Vì sao Kafka không giống queue xóa message sau ack?
3. Offset dùng để làm gì?
4. Vì sao hai consumer group đọc được cùng một event?
5. Hai consumer trong cùng group xử lý message thế nào?
6. Replay có thể gây vấn đề gì?
7. At-most-once đánh đổi điều gì?
8. Vì sao at-least-once cần idempotency?
9. Throughput và latency khác nhau thế nào?
10. Backpressure là gì và nhận biết bằng tín hiệu nào?
11. Khi nào database polling phù hợp hơn Kafka?
12. Vì sao publish sau commit database có thể làm mất event?
13. Exactly-once cần xác định rõ phạm vi nào?
14. Vì sao thêm consumer không luôn tăng throughput?
15. Vì sao Payment, Inventory và Analytics thường cần group riêng?

### Đáp án ngắn

1. Command là yêu cầu; event là sự thật đã xảy ra.
2. Kafka giữ record theo retention, không xóa ngay khi một consumer đọc.
3. Offset là vị trí đọc trong partition.
4. Mỗi group có cursor/offset độc lập.
5. Họ chia nhau các partition được assign.
6. Duplicate side effect nếu consumer không idempotent.
7. Giảm duplicate nhưng có thể mất record sau commit trước xử lý.
8. Process có thể chết sau side effect nhưng trước commit offset.
9. Throughput là lượng xử lý; latency là thời gian chờ/xử lý.
10. Producer nhanh hơn consumer; backlog/lag tăng.
11. Traffic thấp, topology đơn giản và không cần replay.
12. Process có thể chết giữa database commit và publish.
13. Kafka, pipeline nội bộ hay cả hệ thống bên ngoài.
14. Có thể bị giới hạn bởi số partition hoặc downstream.
15. Mỗi domain cần nhận event độc lập; dùng chung group sẽ chia event cho nhau.

## 18. Checklist hoàn thành Tuần 1

- [ ] Tôi phân biệt được event và command.
- [ ] Tôi giải thích được append-only log bằng ví dụ file.
- [ ] Tôi hiểu record không biến mất ngay sau khi consumer đọc.
- [ ] Tôi giải thích được offset và replay.
- [ ] Tôi phân biệt được cùng group và khác group.
- [ ] Tôi mô tả được at-most-once và at-least-once.
- [ ] Tôi nêu được ít nhất ba nguyên nhân gây duplicate.
- [ ] Tôi nêu được ít nhất ba nguyên nhân gây mất event.
- [ ] Tôi giải thích được backpressure và lag.
- [ ] Tôi biết khi nào không nên dùng Kafka.
- [ ] Tôi hoàn thành mô phỏng log/cursor bằng file.
- [ ] Tôi có thiết kế sơ bộ cho flow OrderCreated.

## 19. Câu chốt cần nhớ

Kafka không chỉ là nơi gửi message. Kafka là một log phân tán được lưu giữ theo retention, nơi nhiều consumer group có thể đọc cùng một dòng sự kiện ở các vị trí khác nhau.

Nếu chỉ nhớ bốn ý sau, bạn đã có nền tảng đúng để sang tuần 2:

1. Event là sự thật đã xảy ra.
2. Record được giữ lại, không biến mất chỉ vì một consumer đã đọc.
3. Offset cho phép consumer tiếp tục, dừng, seek và replay.
4. At-least-once là thực tế phổ biến, nên consumer phải chịu được duplicate.


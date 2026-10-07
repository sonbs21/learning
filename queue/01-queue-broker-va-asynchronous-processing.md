# Tuần 1: Queue, broker và asynchronous processing

## 1. Mục tiêu bài học

Sau bài này, bạn có thể:

- Phân biệt xử lý synchronous và asynchronous trong backend.
- Giải thích vì sao queue không chỉ là một `List` nằm trong memory của một process.
- Nhận diện vai trò của producer, consumer, broker, message, payload và metadata.
- Phân biệt queue, topic và subscription.
- Chọn mô hình work queue, competing consumers hay pub/sub cho một bài toán cụ thể.
- Phân biệt command message, event message, request/reply và event notification.
- Hiểu queue giúp tách producer khỏi consumer như thế nào.
- Nhận biết các giới hạn của asynchronous processing: message có thể trễ, trùng, mất hoặc xử lý thất bại.
- Nhận biết backpressure và các cách phản ứng khi consumer không theo kịp producer.

> Bài này xây dựng mental model. RabbitMQ, Kafka và AWS SQS sẽ được học chi tiết ở các tuần sau.

---

## 2. Bài toán nền: một request không nên làm tất cả mọi việc

Giả sử API tạo đơn hàng phải làm các việc sau:

1. Kiểm tra dữ liệu đơn hàng.
2. Lưu đơn hàng vào database.
3. Gửi email xác nhận.
4. Gửi thông báo đến hệ thống analytics.
5. Gọi dịch vụ vận chuyển.

Cách đơn giản nhất là xử lý toàn bộ trong một HTTP request:

```text
Client
  │ POST /orders
  ▼
Order API
  ├── ghi database
  ├── gọi Email Service
  ├── gọi Analytics Service
  └── gọi Shipping Service
  │
  ▼
HTTP 201 Created
```

Cách này có thể phù hợp khi mọi bước đều nhanh, ít lỗi và bắt buộc hoàn thành trước khi trả response. Tuy nhiên, nó tạo ra nhiều coupling:

- API phải chờ tất cả downstream service.
- Một service chậm làm request chậm theo.
- Một service tạm thời không hoạt động có thể làm việc tạo đơn thất bại.
- Khi traffic tăng đột biến, số lượng request đồng thời và connection downstream cũng tăng.
- Việc retry HTTP có thể khiến một thao tác phụ bị thực hiện nhiều lần.

Queue cho phép tách thời điểm nhận yêu cầu khỏi thời điểm xử lý công việc.

```text
Client
  │ POST /orders
  ▼
Order API ── publish OrderCreated ──▶ Broker ──▶ Queue
  │                                      │
  └── HTTP 201                         Consumer
                                         ├── gửi email
                                         ├── ghi analytics
                                         └── gọi shipping
```

API chỉ cần hoàn thành phần việc thuộc trách nhiệm của nó và gửi một message hợp lệ. Consumer xử lý phần việc phía sau ở thời điểm phù hợp.

---

## 3. Synchronous và asynchronous processing

### 3.1. Synchronous processing

Synchronous nghĩa là bên gọi gửi request và chờ kết quả của thao tác trước khi tiếp tục.

```java
Order order = orderRepository.save(command.toOrder());
emailService.sendOrderConfirmation(order);
return order;
```

Trong ví dụ này, `OrderService` phụ thuộc trực tiếp vào thời gian phản hồi và trạng thái của `emailService`.

Đặc điểm:

- Kết quả thường có ngay trong cùng flow xử lý.
- Dễ suy luận khi thao tác ngắn và cần tính nhất quán tức thời.
- Caller phải chờ các dependency.
- Timeout của một dependency có thể kéo dài hoặc làm hỏng request.

### 3.2. Asynchronous processing

Asynchronous nghĩa là bên gọi gửi công việc đi rồi không cần chờ toàn bộ công việc hoàn thành trong cùng flow.

```java
Order order = orderRepository.save(command.toOrder());
messagePublisher.publish(new OrderCreated(order.id()));
return order;
```

Ở đây, `OrderService` không trực tiếp gửi email. Nó gửi message để một consumer làm việc đó sau.

Đặc điểm:

- Response có thể trả nhanh hơn.
- Producer và consumer có thể scale độc lập.
- Một consumer tạm thời chết không nhất thiết làm producer thất bại ngay.
- Kết quả phụ thường xuất hiện sau response: đây là eventual consistency.
- Cần thiết kế acknowledgement, retry, duplicate handling và quan sát message flow.

### 3.3. Asynchronous không đồng nghĩa với reliable

Đẩy message sang queue chỉ thay đổi cách hệ thống xử lý công việc; nó không tự động đảm bảo message sẽ được lưu hoặc xử lý thành công.

Các câu hỏi phải trả lời riêng:

- Producer đã chắc chắn broker nhận message chưa?
- Message có được lưu bền vững khi broker restart không?
- Consumer đã xử lý xong hay mới nhận message?
- Nếu consumer chết giữa chừng, message có được giao lại không?
- Nếu giao lại, handler có chịu được duplicate không?
- Message lỗi vĩnh viễn sẽ đi đâu?

Delivery semantics và failure model sẽ được học ở Tuần 2.

---

## 4. Queue-based load leveling

### 4.1. Ý tưởng

Queue hoạt động như một vùng đệm giữa tốc độ sản xuất công việc và tốc độ xử lý công việc.

```text
Producer rate: 1.000 jobs/giây
Consumer rate:   200 jobs/giây

Producer ──▶ [ job  job  job  job  job  ... ] ──▶ Consumer
                queue depth tăng dần
```

Khi traffic tăng trong thời gian ngắn, producer vẫn có thể tiếp nhận công việc, còn consumer xử lý dần. Đây là **load leveling**.

### 4.2. Queue không tạo ra năng lực xử lý vô hạn

Nếu producer liên tục nhanh hơn consumer, queue chỉ trì hoãn vấn đề:

- Queue depth tăng.
- Thời gian chờ của message tăng.
- Message có thể hết hạn.
- Bộ nhớ hoặc disk của broker có thể đầy.
- User nhận kết quả phụ quá muộn.

Vì vậy, queue cần đi cùng các giới hạn và tín hiệu vận hành:

- Queue depth hoặc backlog.
- Message age hoặc wait time.
- Consumer throughput.
- Processing latency.
- Error và redelivery rate.
- Giới hạn kích thước queue.

### 4.3. Ví dụ định lượng đơn giản

Giả sử:

- Producer tạo 100 message/giây.
- Consumer xử lý 80 message/giây.
- Backlog ban đầu là 0.

Mỗi giây backlog tăng khoảng 20 message. Sau 5 phút:

```text
20 message/giây × 300 giây = 6.000 message
```

Muốn giải quyết, cần một hoặc nhiều hướng:

- Tăng số consumer.
- Làm handler nhanh hơn.
- Giảm tốc độ producer.
- Batch nhiều message nếu phù hợp.
- Từ chối hoặc trì hoãn công việc khi đã quá tải.
- Dùng chính sách retention, TTL hoặc dead-letter phù hợp.

---

## 5. Các thành phần cốt lõi

### 5.1. Producer

Producer là thành phần tạo và gửi message.

Ví dụ:

- Order API phát `OrderCreated`.
- Payment Service gửi lệnh `CapturePayment`.
- Scheduler gửi `GenerateDailyReport`.

Producer không nhất thiết là HTTP API. Bất kỳ service, worker hoặc job nào tạo message đều có thể là producer.

### 5.2. Consumer

Consumer là thành phần nhận và xử lý message.

Ví dụ:

- Email Worker nhận `OrderCreated` rồi gửi email.
- Billing Worker nhận `CapturePayment` rồi gọi payment provider.
- Analytics Worker nhận event rồi ghi dữ liệu báo cáo.

Một consumer tốt cần có:

- Handler rõ ràng.
- Timeout cho dependency.
- Logging có message ID và correlation ID.
- Quyết định rõ khi nào thành công và khi nào thất bại.
- Khả năng xử lý duplicate.

### 5.3. Broker

Broker là hạ tầng trung gian nhận, lưu tạm hoặc lưu bền vững, định tuyến và phân phối message.

Broker thường đảm nhiệm một phần hoặc toàn bộ các việc:

- Nhận message từ producer.
- Chọn queue/topic đích.
- Lưu message trong thời gian chờ consumer.
- Phân phối message cho consumer.
- Theo dõi acknowledgement.
- Giao lại message khi consumer không hoàn tất.
- Giữ message lỗi trong dead-letter queue hoặc cơ chế tương đương.

Broker không phải business service. Nó không nên quyết định “đơn hàng có hợp lệ về nghiệp vụ hay không”; đó là trách nhiệm của domain service.

### 5.4. Message

Message là đơn vị dữ liệu được truyền giữa các thành phần. Một message nên phân biệt được:

```text
Message
├── payload: dữ liệu nghiệp vụ
├── metadata: dữ liệu mô tả message
└── headers: thông tin định tuyến hoặc xử lý
```

Ví dụ một envelope:

```json
{
  "messageId": "01JABC...",
  "messageType": "OrderCreated",
  "version": 1,
  "occurredAt": "2026-09-26T08:30:00Z",
  "correlationId": "req-7f1...",
  "producer": "order-service",
  "payload": {
    "orderId": "order-123",
    "customerId": "customer-456",
    "totalAmount": 250000,
    "currency": "VND"
  }
}
```

`payload` chứa dữ liệu nghiệp vụ. `messageId`, `version`, `occurredAt` và `correlationId` giúp trace, deduplicate, version contract và điều tra sự cố.

Không nên nhét dữ liệu nhạy cảm vào message nếu consumer không cần. Cũng không nên phụ thuộc vào một object Java serialized trực tiếp nếu message cần được đọc bởi nhiều ngôn ngữ hoặc tồn tại lâu.

---

## 6. Queue, topic và subscription

### 6.1. Queue

Queue là nơi message chờ một consumer xử lý. Mô hình phổ biến là mỗi message được một consumer trong cùng nhóm xử lý.

```text
Producer ──▶ Queue ──▶ Consumer A
                    └─▶ Consumer B
```

Trong nhiều hệ thống, A và B là **competing consumers**: mỗi message chỉ được một trong hai nhận để xử lý.

### 6.2. Topic

Topic là tên logic hoặc dòng phân loại message. Một topic thường chứa nhiều message cùng loại hoặc cùng domain stream.

```text
Producer ──▶ topic: order-events
                 ├── partition 0
                 ├── partition 1
                 └── partition 2
```

Trong event streaming như Kafka, topic thường là log có thứ tự trong từng partition và consumer theo dõi vị trí bằng offset. Đây là mô hình khác với queue truyền thống, dù cả hai đều giúp truyền message.

### 6.3. Subscription

Subscription mô tả một consumer hoặc nhóm consumer quan tâm đến topic nào và nhận message theo quy tắc nào.

```text
order-events
├── email-subscription     ──▶ Email Consumer
├── analytics-subscription ──▶ Analytics Consumer
└── audit-subscription     ──▶ Audit Consumer
```

Trong một số broker, subscription được biểu diễn bằng queue riêng được bind vào topic/exchange. Tên gọi và chi tiết triển khai thay đổi theo công nghệ, nhưng ý tưởng chính là: mỗi nhóm xử lý cần có “dòng nhận” riêng nếu không muốn tranh message với nhóm khác.

---

## 7. Ba mô hình phân phối quan trọng

### 7.1. Work queue

Một producer gửi các job giống nhau vào một queue. Một hoặc nhiều worker xử lý từng job.

```text
                 ┌──▶ Worker A
Producer ──▶ Queue├──▶ Worker B
                 └──▶ Worker C
```

Dùng khi:

- Mỗi job chỉ cần được xử lý một lần bởi một worker flow.
- Muốn scale ngang bằng cách thêm worker.
- Ví dụ: resize ảnh, gửi email, tạo PDF, xử lý import file.

### 7.2. Competing consumers

Đây là cách scale consumer của một work queue. Các consumer cạnh tranh để nhận job tiếp theo.

Điểm cần nhớ:

- Thêm consumer có thể tăng throughput.
- Thứ tự xử lý toàn cục thường không còn được đảm bảo.
- Hai job liên quan có thể chạy đồng thời.
- Handler phải an toàn khi chạy song song.

Nếu hai message cập nhật cùng một tài khoản hoặc cùng một order, cần cân nhắc partition key, lock, optimistic concurrency hoặc thiết kế idempotent.

### 7.3. Pub/sub

Một event được gửi cho nhiều nhóm subscriber độc lập.

```text
                         ┌──▶ Email Subscriber
Order Service ──▶ Topic ├──▶ Analytics Subscriber
                         └──▶ Audit Subscriber
```

Mỗi subscriber cần nhận bản sao logic của event để xử lý theo mục đích riêng. Email subscriber chậm không nên làm analytics subscriber dừng theo.

| Mô hình | Một message được xử lý bởi | Mục đích chính |
|---|---|---|
| Work queue | Một worker trong nhóm | Phân phối job |
| Competing consumers | Một consumer được chọn trong nhóm | Scale xử lý |
| Pub/sub | Nhiều subscriber độc lập | Fan-out event |

---

## 8. Command, event và event notification

### 8.1. Command message

Command là yêu cầu một consumer thực hiện hành động.

```json
{
  "messageType": "CapturePayment",
  "payload": {
    "paymentId": "pay-123",
    "amount": 250000,
    "currency": "VND"
  }
}
```

Command thường có tính hướng đích và tên mang tính mệnh lệnh: `SendInvoice`, `ReserveStock`, `GenerateReport`.

Producer ngầm kỳ vọng có một bên chịu trách nhiệm thực hiện command. Vì vậy, command cần contract rõ và cơ chế báo lỗi phù hợp.

### 8.2. Event message

Event mô tả một sự việc đã xảy ra.

```json
{
  "messageType": "OrderCreated",
  "payload": {
    "orderId": "order-123",
    "occurredAt": "2026-09-26T08:30:00Z"
  }
}
```

Event không nên được đặt tên như yêu cầu hành động. `OrderCreated` mô tả sự kiện; consumer có thể gửi email, cập nhật analytics hoặc tạo workflow riêng.

### 8.3. Event notification và event-carried state transfer

Event notification chỉ mang thông tin đủ để consumer biết có thay đổi:

```json
{
  "orderId": "order-123"
}
```

Consumer có thể phải gọi API hoặc đọc database để lấy chi tiết. Điều này làm consumer phụ thuộc vào nguồn dữ liệu khác.

Event-carried state transfer mang nhiều dữ liệu hơn:

```json
{
  "orderId": "order-123",
  "customerId": "customer-456",
  "totalAmount": 250000,
  "items": [
    { "sku": "book-1", "quantity": 2, "unitPrice": 125000 }
  ]
}
```

Consumer ít phải gọi ngược producer hơn nhưng message contract lớn hơn và cần quản lý version cẩn thận.

### 8.4. Request/reply qua message

Request/reply là một flow bất đồng bộ nhưng vẫn có kết quả trả về:

```text
Client ── request ──▶ Request Queue ──▶ Worker
Client ◀─ reply  ──── Reply Queue   ◀── Worker
```

Thông thường request có `correlationId` để ghép reply với request. Đây là một lựa chọn hữu ích khi hai bên không muốn gọi HTTP trực tiếp, nhưng không nên biến mọi thao tác thành request/reply qua queue. Nếu caller luôn phải chờ reply, phần lợi ích của asynchronous có thể giảm đáng kể.

---

## 9. Backpressure

### 9.1. Backpressure là gì?

Backpressure là cơ chế làm chậm hoặc giới hạn producer khi downstream không thể xử lý thêm.

Không có backpressure:

```text
Producer nhanh ──▶ queue tăng vô hạn ──▶ broker cạn tài nguyên
```

Có backpressure:

```text
Producer ──▶ [giới hạn / rate limit / bounded queue] ──▶ Consumer
```

### 9.2. Các chiến lược thường gặp

**Giới hạn concurrency**

- Giới hạn số message xử lý đồng thời.
- Tránh tạo quá nhiều thread hoặc connection đến downstream.

**Prefetch hoặc receive limit**

- Consumer chỉ lấy trước một số message giới hạn.
- Tránh một consumer giữ quá nhiều message chưa xử lý.

**Rate limit**

- Giới hạn tốc độ gửi message hoặc gọi API bên ngoài.
- Hữu ích khi downstream có quota.

**Bounded queue ở application**

- Queue nội bộ có kích thước tối đa.
- Khi đầy, application phải block, reject, shed load hoặc trả lỗi có chủ đích.

**Scale consumer**

- Tăng số instance hoặc worker khi backlog tăng.
- Chỉ hiệu quả khi bottleneck nằm ở số lượng consumer và downstream vẫn chịu được tải.

**Batching**

- Gom nhiều message thành một lần ghi hoặc gọi.
- Tăng throughput nhưng có thể tăng latency và làm retry phức tạp hơn.

### 9.3. Backpressure không phải là drop tùy tiện

Nếu bỏ message, cần biết message nào có thể bỏ và message nào bắt buộc phải xử lý. Ví dụ:

- Metric phụ có thể chấp nhận mất một phần.
- Lệnh trừ tiền không nên bị bỏ mà không có cơ chế bù.
- Notification có thể hết hạn sau một khoảng thời gian.

Quyết định này thuộc business requirement, không chỉ là một setting của broker.

---

## 10. So sánh ba kiến trúc trong bài tập tuần 1

### 10.1. API gọi email service đồng bộ

```text
Client ──▶ Order API ──HTTP──▶ Email Service
   ▲             │
   └── response ─┘
```

| Câu hỏi | Phân tích |
|---|---|
| Khi nào trả response? | Sau khi Order API nhận kết quả từ Email Service hoặc timeout. |
| Consumer chết thì sao? | Request có thể timeout hoặc fail ngay. |
| Message có mất không? | Không có message queue; request đang bay có thể thất bại khi network/service lỗi. |
| Ưu điểm | Đơn giản, biết kết quả email ngay trong flow. |
| Nhược điểm | Coupling cao, latency và failure của email ảnh hưởng API. |

### 10.2. API đẩy email job vào queue

```text
Client ──▶ Order API ──publish──▶ Email Queue ──▶ Email Worker
   ▲             │                                      │
   └── response ─┘                                      └── Email Provider
```

| Câu hỏi | Phân tích |
|---|---|
| Khi nào trả response? | Sau khi order được lưu và message publish thành công theo guarantee đã chọn. |
| Consumer chết thì sao? | Queue có thể giữ message chờ worker quay lại; điều này phụ thuộc cấu hình broker và delivery semantics. |
| Message có mất không? | Có thể mất nếu publish không được xác nhận hoặc broker/message không bền vững. Không được mặc định là an toàn. |
| Ưu điểm | API không chờ email; worker scale độc lập; có thể retry. |
| Nhược điểm | Email xảy ra sau response; cần xử lý duplicate, retry và quan sát backlog. |

### 10.3. Order service phát event cho nhiều consumer

```text
                         ┌──▶ Email Consumer
Order Service ──▶ Topic ├──▶ Analytics Consumer
                         └──▶ Audit Consumer
```

| Câu hỏi | Phân tích |
|---|---|
| Khi nào trả response? | Sau khi order service hoàn tất phần việc của nó và publish event theo guarantee đã chọn. |
| Một consumer chết thì sao? | Các consumer khác có thể tiếp tục; subscriber bị lỗi cần tự xử lý backlog/retry. |
| Message có mất không? | Phụ thuộc persistence, acknowledgement, retention và cấu hình của broker/stream. |
| Ưu điểm | Tách producer khỏi nhiều consumer; thêm subscriber mới ít ảnh hưởng producer. |
| Nhược điểm | Contract event cần ổn định; dữ liệu đến các hệ thống có thể không đồng thời. |

---

## 11. Ví dụ thiết kế message trong Java

Một record đơn giản có thể mô tả envelope:

```java
import java.time.Instant;

public record MessageEnvelope<T>(
        String messageId,
        String messageType,
        int version,
        Instant occurredAt,
        String correlationId,
        String producer,
        T payload
) {}
```

Payload của event:

```java
public record OrderCreated(
        String orderId,
        String customerId,
        long totalAmount,
        String currency
) {}
```

Tạo message:

```java
var event = new MessageEnvelope<>(
        UUID.randomUUID().toString(),
        "OrderCreated",
        1,
        Instant.now(),
        requestId,
        "order-service",
        new OrderCreated("order-123", "customer-456", 250_000, "VND")
);
```

Đây chỉ là mô hình dữ liệu minh họa. Việc serialize JSON, publish đến RabbitMQ/Kafka/SQS và xác nhận kết quả sẽ được học ở các bài sau.

### Nguyên tắc đặt message

- Tên message phải nói rõ đây là command hay event.
- Có version ngay từ đầu nếu message có thể tồn tại lâu.
- Có ID duy nhất để trace và deduplicate.
- Không đưa thông tin mà consumer không cần.
- Không dùng Java class name làm contract công khai.
- Không thay đổi ý nghĩa field cũ một cách âm thầm.
- Xác định field bắt buộc, nullable và format thời gian.

---

## 12. Các ngộ nhận thường gặp

### “Async là nhanh hơn”

Async thường làm response nhanh hơn vì request không chờ toàn bộ công việc phụ. Nhưng tổng thời gian đến khi công việc hoàn thành có thể dài hơn do message phải chờ trong queue.

### “Đã vào queue thì chắc chắn không mất”

Không đúng. Cần biết broker có lưu bền vững không, producer có nhận publish confirmation không, message có TTL không và khi broker lỗi thì hành vi ra sao.

### “Có queue là xử lý đúng một lần”

Không đúng. Consumer có thể crash sau khi side effect thành công nhưng trước khi acknowledgement được ghi nhận, dẫn đến redelivery. Handler cần idempotent.

### “Thêm consumer luôn làm hệ thống nhanh hơn”

Không đúng. Downstream database, API provider, CPU hoặc lock có thể là bottleneck. Thêm consumer quá mức có thể làm lỗi tăng và tạo retry storm.

### “Event và command giống nhau”

Không giống nhau. Command yêu cầu hành động và thường có bên nhận cụ thể. Event thông báo một việc đã xảy ra và cho phép nhiều consumer phản ứng độc lập.

### “Queue thay thế database”

Queue thường là cơ chế truyền và giữ message trong một khoảng thời gian, không tự động thay thế nguồn dữ liệu nghiệp vụ. Retention, replay và query capability tùy công nghệ.

---

## 13. Bài tập thực hành

### Bài 1: Vẽ ba kiến trúc

Vẽ sequence diagram hoặc sơ đồ khối cho ba flow:

1. API gọi email service đồng bộ.
2. API đẩy email job vào queue.
3. Order service phát event cho email, analytics và audit.

Với mỗi sơ đồ, ghi rõ:

- Ai là producer?
- Ai là consumer?
- Broker hoặc queue nằm ở đâu?
- Response trả ở thời điểm nào?
- Điều gì xảy ra nếu consumer chết?
- Có khả năng duplicate ở đâu?
- Metric nào cần quan sát?

### Bài 2: Chọn mô hình phân phối

Chọn work queue, competing consumers hoặc pub/sub cho từng tình huống và giải thích:

1. Tạo thumbnail cho mỗi ảnh upload.
2. Một đơn hàng cần gửi email và ghi audit độc lập.
3. Chạy một report nặng vào ban đêm, chỉ cần một worker thực hiện.
4. Xử lý 10 triệu bản ghi import với nhiều worker.
5. Gửi sự kiện `UserRegistered` cho marketing, analytics và fraud detection.

### Bài 3: Thiết kế message contract

Thiết kế JSON envelope cho event `OrderPaid`, gồm tối thiểu:

- `messageId`
- `messageType`
- `version`
- `occurredAt`
- `correlationId`
- `producer`
- `payload`

Trong payload, chọn field phù hợp cho consumer gửi receipt và consumer cập nhật analytics. Ghi rõ field nào là bắt buộc.

### Bài 4: Phân tích backlog

Một producer tạo 500 job/phút, một consumer xử lý 400 job/phút.

1. Backlog tăng bao nhiêu sau 10 phút nếu không có job ban đầu?
2. Tối thiểu cần bao nhiêu consumer nếu mỗi consumer xử lý 400 job/phút và muốn backlog không tăng?
3. Nếu mỗi job mất trung bình 200 ms, các yếu tố nào có thể làm throughput thực tế thấp hơn con số lý thuyết?
4. Bạn sẽ theo dõi metric nào để phát hiện user đang phải chờ quá lâu?

### Bài 5: Thử nghiệm local không cần broker thật

Viết một Java console program nhỏ có:

- Một producer tạo job theo tốc độ cố định.
- Một bounded in-memory queue.
- Hai consumer xử lý job.
- Log thời điểm tạo, bắt đầu xử lý và hoàn thành.
- Một chế độ làm consumer chậm hơn producer.

Quan sát queue depth và mô tả điều gì xảy ra khi queue đầy. Bài thử nghiệm này giúp hiểu khái niệm, nhưng không đại diện cho độ bền hoặc delivery guarantee của broker production.

---

## 14. Câu hỏi tự kiểm tra

1. Queue giải quyết vấn đề coupling nào giữa producer và consumer?
2. Vì sao response có thể trả thành công nhưng email vẫn chưa được gửi?
3. Work queue khác pub/sub ở điểm nào?
4. Vì sao `OrderCreated` thường là event còn `SendOrderEmail` thường là command?
5. Broker có đảm bảo business operation thành công không? Vì sao?
6. Nếu producer nhanh hơn consumer liên tục, queue sẽ ra sao?
7. Backpressure có thể được tạo bằng những cơ chế nào?
8. Vì sao message nên có `messageId` và `correlationId`?
9. Consumer chết sau khi gọi payment provider nhưng trước khi ack sẽ tạo rủi ro gì?
10. Khi nào request/reply qua message làm mất bớt lợi ích của asynchronous processing?

---

## 15. Checklist hoàn thành Tuần 1

- [ ] Mình giải thích được synchronous và asynchronous bằng một ví dụ backend.
- [ ] Mình phân biệt được producer, consumer, broker và message.
- [ ] Mình biết queue giúp load leveling nhưng không tạo năng lực xử lý vô hạn.
- [ ] Mình phân biệt được queue, topic và subscription ở mức khái niệm.
- [ ] Mình phân biệt được work queue, competing consumers và pub/sub.
- [ ] Mình phân biệt được command message và event message.
- [ ] Mình biết event notification có thể khiến consumer phải gọi ngược producer.
- [ ] Mình hiểu async không tự động reliable và không tự động exactly-once.
- [ ] Mình biết backlog, message age, throughput và processing latency là các tín hiệu cần quan sát.
- [ ] Mình hoàn thành ba sơ đồ kiến trúc và bài phân tích backlog.

## Kết luận

Queue là một cơ chế tách thời điểm tạo công việc khỏi thời điểm xử lý công việc. Broker đứng giữa producer và consumer để truyền, lưu và phân phối message theo mô hình của công nghệ. Lợi ích chính là giảm coupling, san bằng tải và cho phép scale độc lập; đổi lại, hệ thống phải chấp nhận eventual consistency và giải quyết các vấn đề về retry, duplicate, ordering, backpressure và observability.

Ở Tuần 2, từ mental model này, ta sẽ đi vào delivery semantics và failure model: message có thể mất như thế nào, vì sao duplicate xảy ra, và vì sao mục tiêu thực tế thường là **at-least-once delivery + idempotent processing**.

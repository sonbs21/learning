# Tuần 11: Kafka delivery, ordering và retry

## 1. Mục tiêu bài học

Sau bài này, bạn có thể:

- Phân biệt commit trước xử lý và commit sau xử lý.
- Hiểu at-least-once consumer và duplicate record.
- Phân biệt ordering trong một partition với global ordering.
- Giải thích vì sao một record lỗi có thể block các record sau.
- Thiết kế blocking retry bằng seek và backoff.
- Thiết kế retry topic với nhiều delay.
- Hiểu non-blocking retry và trade-off ordering.
- Thiết kế dead-letter topic cho poison record.
- Phân biệt lỗi transient, permanent và unknown outcome.
- Dùng pause/resume partition ở mức khái niệm.
- Cấu hình DefaultErrorHandler và DeadLetterPublishingRecoverer.
- Dùng RetryableTopic cho retry topic/DLT trong Spring Kafka.
- Đặt giới hạn retry và bảo vệ business effect bằng idempotency.
- Xây flow orders.v1 → retry.10s → retry.1m → orders.dlt.

> Mục tiêu thực tế thường là at-least-once delivery + idempotent processing. Retry không làm mất sự cần thiết của idempotency.

---

## 2. Ba tầng cần phân biệt

Khi nói Kafka delivery, đừng trộn ba khái niệm:

~~~text
Delivery
  record có thể được đọc lại

Processing
  listener có thể chạy nhiều lần

Business effect
  database/external API có thể thay đổi nhiều lần
~~~

Ví dụ:

~~~text
poll record
  ↓
gọi payment provider thành công
  ↓
process crash
  ↓
offset chưa commit
  ↓
record được đọc lại
  ↓
payment provider có thể bị gọi lần hai
~~~

Kafka có thể giúp consumer replay record, nhưng Kafka không tự biết external API đã tạo side effect hay chưa. Consumer cần operation ID, idempotency key hoặc state transition an toàn.

---

## 3. Commit trước khi xử lý

Flow:

~~~text
poll record
   ↓
commit offset
   ↓
business processing
~~~

Nếu process crash sau commit nhưng trước processing:

~~~text
offset đã lưu
business effect chưa xảy ra
record có thể bị bỏ qua
~~~

Đây gần với at-most-once processing.

Có thể dùng khi:

- Mất record chấp nhận được.
- Record chỉ là metric phụ.
- Business effect có thể tính lại từ nguồn khác.
- Muốn giảm duplicate hơn là đảm bảo xử lý đủ.

Không dùng cho payment, inventory, audit hoặc command bắt buộc phải hoàn tất nếu chưa có cơ chế bù.

---

## 4. Commit sau khi xử lý

Flow:

~~~text
poll record
   ↓
business processing
   ↓
commit offset
~~~

Nếu process crash sau business effect nhưng trước commit:

~~~text
business effect đã xảy ra
offset chưa commit
record được xử lý lại
~~~

Đây là cơ chế phổ biến để đạt at-least-once processing.

Đổi lại:

- Duplicate là bình thường.
- Handler phải idempotent.
- Database/external API cần operation ID.
- Retry và replay phải có giới hạn.

### 4.1. Vì sao không thể loại bỏ duplicate hoàn toàn?

Ack/commit request có thể thất bại sau khi business processing đã thành công:

~~~text
database commit thành công
        ↓
offset commit request thất bại
        ↓
consumer restart
        ↓
record chạy lại
~~~

Không nên cố “chữa” duplicate bằng cách commit sớm nếu mất record nguy hiểm hơn. Hãy làm business effect idempotent.

---

## 5. At-least-once consumer

Một consumer at-least-once thường có flow:

~~~text
poll
  ↓
deserialize
  ↓
validate
  ↓
process
  ↓
commit offset sau success
~~~

Các trường hợp record chạy lại:

- Consumer crash trước commit.
- Rebalance giữa processing và commit.
- Commit timeout.
- Listener exception.
- Error handler seek về offset cũ.
- Operator reset group offset.
- Replay sau khi sửa bug.

At-least-once không có nghĩa record chắc chắn chỉ chạy hai lần hoặc có giới hạn duplicate. Nếu retry không giới hạn, record có thể chạy rất nhiều lần.

---

## 6. Duplicate record và idempotency

### 6.1. Duplicate do retry

~~~text
record offset 100
  ├── lần 1: DB update thành công
  ├── offset commit thất bại
  └── lần 2: record được đọc lại
~~~

### 6.2. Business operation ID

Envelope nên có:

~~~json
{
  "eventId": "evt-001",
  "operationId": "capture:payment-123",
  "eventType": "PaymentCaptured",
  "eventVersion": 1,
  "correlationId": "checkout-789",
  "payload": {
    "paymentId": "payment-123"
  }
}
~~~

Chọn key:

| Mục tiêu | Key |
|---|---|
| Một lần publish logic | eventId |
| Một business operation | operationId |
| Aggregate version | aggregateId + version |
| External API retry | idempotencyKey |

### 6.3. Idempotent database update

Ví dụ chuyển trạng thái:

~~~sql
UPDATE orders
SET status = 'PAID',
    paid_at = :paidAt
WHERE order_id = :orderId
  AND status = 'PENDING';
~~~

Nếu record chạy lại sau khi order đã PAID, update thứ hai không tạo transition mới.

Với counter hoặc balance, không dùng delta mù quáng:

~~~sql
UPDATE wallet
SET balance = balance - :amount
WHERE user_id = :userId;
~~~

Cần operation table, unique constraint hoặc state machine để không trừ hai lần.

### 6.4. Idempotency với external API

~~~text
operationId = capture:payment-123
idempotencyKey = capture:payment-123
~~~

Mọi retry cùng operation giữ nguyên key. UUID mới cho từng retry có thể tạo operation mới.

---

## 7. Ordering trong một partition

Kafka đảm bảo record order trong từng partition:

~~~text
partition 0
offset 10 → OrderCreated(order-1)
offset 11 → OrderPaid(order-1)
offset 12 → OrderShipped(order-1)
~~~

Một consumer đọc partition theo offset order.

### 7.1. Ordering không phải global

~~~text
partition 0: A0 → A1 → A2
partition 1: B0 → B1 → B2
~~~

Không có thứ tự được đảm bảo giữa A và B.

Sai:

~~~text
A0 luôn xảy ra trước B0
~~~

### 7.2. Ordering theo aggregate

Dùng aggregate ID làm key:

~~~text
key = order-1
OrderCreated → partition 0
OrderPaid    → partition 0
OrderShipped → partition 0
~~~

Ordering chỉ giữ nếu producer tiếp tục dùng cùng key và mapping phù hợp.

### 7.3. Một partition có nhiều aggregate

~~~text
partition 0
order-1 created
order-2 created
order-1 paid
order-2 paid
~~~

Ordering của partition là sequence chung. Business chỉ nên yêu cầu ordering cần thiết theo từng aggregate, không giả định tất cả order có một global business order.

---

## 8. Global ordering giữa nhiều partition

Global ordering cần một partition duy nhất:

~~~text
topic
└── partition 0
    └── mọi record theo một sequence
~~~

Đánh đổi:

- Không có parallelism theo partition.
- Throughput thấp hơn.
- Một record chậm block toàn stream.
- Scale consumer không giúp nếu chỉ có một partition.

Nếu chỉ cần ordering theo orderId, dùng nhiều partition và key orderId. Đừng chọn global ordering khi domain chỉ cần per-entity ordering.

---

## 9. Poison record

Poison record là record liên tục fail vì:

- Schema không hợp lệ.
- Thiếu field bắt buộc.
- Event version không hỗ trợ.
- Giá trị nghiệp vụ sai.
- Reference không tồn tại.
- Code bug luôn tái hiện với payload đó.

Nếu retry blocking ngay trên partition:

~~~text
P0:
record 10 lỗi
record 11 chưa được xử lý
record 12 chưa được xử lý
~~~

Record 10 có thể block record 11 và 12 dù chúng hợp lệ.

### 9.1. Không retry validation vô hạn

Phân loại:

| Lỗi | Retry? |
|---|---|
| HTTP 503 | Có giới hạn |
| Timeout unknown outcome | Có với idempotency |
| Database deadlock | Có với backoff |
| JSON malformed | Không retry vô hạn |
| Missing required field | Không retry |
| Unsupported version | DLT hoặc migration |
| Business state invalid | DLT/manual handling |

---

## 10. Blocking retry

Blocking retry giữ record trong flow của consumer và chờ trước khi thử lại.

~~~text
main topic
   ↓ error
retry trong container/thread
   ↓ backoff
thử lại record
   ├── success → commit
   └── fail → retry tiếp/DLT
~~~

### 10.1. Ưu điểm

- Dễ hiểu.
- Có thể giữ ordering trong partition tốt hơn.
- Không cần nhiều retry topic.
- Phù hợp lỗi ngắn và transient.

### 10.2. Nhược điểm

- Giữ consumer capacity.
- Có thể block partition.
- Backoff dài ảnh hưởng poll interval.
- Một poison record chặn record sau.
- Retry đồng thời có thể làm downstream quá tải nếu không có jitter.

### 10.3. Khi dùng

Dùng blocking retry cho:

- Lỗi database ngắn.
- Connection reset.
- HTTP 503 ngắn.
- Deadlock.
- Lỗi có khả năng tự hết trong vài giây.

Không dùng blocking retry nhiều phút trên partition nóng nếu record sau có thể xử lý độc lập.

---

## 11. Seek và blocking retry

Error handler có thể seek partition về offset record lỗi:

~~~text
poll P0 offsets 10, 11, 12
offset 11 fail
  ↓
seek P0 về 11
  ↓
poll lại 11, 12
~~~

Offset 10 có thể đã được commit nếu xử lý thành công. Offset 11 và 12 sẽ được đọc lại.

### 11.1. Retry state

Retry handler cần theo dõi:

- Topic.
- Partition.
- Offset.
- Exception type.
- Delivery attempt.
- First failure time.
- Last failure time.
- Backoff state.

Nếu exception thay đổi, có thể cần reset retry state tùy policy.

### 11.2. Seek và consumer liveness

Trong lúc backoff, consumer vẫn cần poll đủ để không bị coi là dead. Với backoff dài, dùng container pausing hoặc retry topic thay vì block thread mù quáng.

---

## 12. Pause và resume partition

Kafka Consumer API hỗ trợ pause/resume partition:

~~~java
consumer.pause(Set.of(topicPartition));
...
consumer.resume(Set.of(topicPartition));
~~~

Ý tưởng:

~~~text
P0 có record lỗi
P1 vẫn khỏe

pause P0
resume P1
~~~

### 12.1. Khi pause hữu ích

- Một partition có dependency chậm.
- Backpressure theo partition.
- Chờ retry time.
- Tạm cô lập hot/poison partition.
- Giảm concurrency cho downstream.

### 12.2. Pause không phải commit

Pause chỉ ngừng fetch partition tạm thời. Record vẫn tồn tại và offset không tự thay đổi.

### 12.3. Rủi ro

- Pause quá lâu gây lag.
- Consumer có thể timeout nếu poll loop không tiếp tục.
- Partition khác có thể bị ảnh hưởng nếu container implementation không đúng.
- Cần metric và timeout resume.

---

## 13. Retry topic

Retry topic tách retry delay khỏi main topic.

Flow roadmap:

~~~text
orders.v1
   ↓ lỗi tạm thời
orders.retry.10s
   ↓
orders.retry.1m
   ↓ lỗi vĩnh viễn
orders.dlt
~~~

### 13.1. Producer flow

~~~text
main listener
   ├── success → commit main offset
   └── transient failure → publish retry record
                              + commit main offset
~~~

Retry record chứa metadata:

- Original topic.
- Original partition.
- Original offset.
- Attempt.
- Next retry time.
- Exception.
- Correlation ID.
- Operation ID.

### 13.2. Retry consumer

~~~text
orders.retry.10s
   ├── chưa đến thời gian → chờ/pause
   └── đến thời gian → xử lý hoặc gửi retry.1m
~~~

### 13.3. Ưu điểm

- Main topic không bị giữ bởi backoff dài.
- Các partition khác có thể tiếp tục.
- Retry policy quan sát được qua topic riêng.
- Có thể scale retry consumer riêng.
- Delay nhiều bước rõ ràng.

### 13.4. Nhược điểm

- Nhiều topic và consumer.
- Quản lý partition mapping.
- Header/retry metadata phức tạp.
- Ordering end-to-end có thể thay đổi.
- Record có thể đến main flow sau record mới hơn.
- Cần idempotency và version check.

---

## 14. Non-blocking retry

Non-blocking retry chuyển record sang retry topic thay vì giữ main consumer thread/partition trong thời gian delay.

~~~text
main topic
   ↓ fail
retry topic
   ↓ delay
retry listener
   ↓
main processing hoặc DLT
~~~

Điểm đánh đổi quan trọng:

> Non-blocking retry có thể làm mất ordering end-to-end.

Ví dụ:

~~~text
main partition 0
m1 lỗi → retry topic
m2 thành công → commit main

sau đó:
m2 effect xảy ra trước m1 retry
~~~

Nếu m1 và m2 cần strict order, non-blocking retry có thể không phù hợp nếu không có thiết kế bổ sung.

### 14.1. Khi phù hợp

- Event độc lập.
- Notification.
- Analytics.
- Lỗi downstream cần delay dài.
- Chấp nhận eventual order hoặc có sequence validation.
- Cần main topic throughput ổn định.

### 14.2. Khi không phù hợp

- Payment state transition bắt buộc tuần tự.
- Inventory command phụ thuộc record trước.
- Workflow mà bỏ qua thứ tự tạo sai state.
- Consumer không có cách kiểm tra version.

---

## 15. Dead-letter topic

DLT là topic nhận record sau khi retry exhausted hoặc gặp lỗi permanent.

~~~text
orders.v1
   ├── transient error → retry topics
   ├── schema error    → orders.dlt
   └── retry exhausted → orders.dlt
~~~

### 15.1. DLT record cần gì?

Giữ metadata:

~~~text
originalTopic
originalPartition
originalOffset
originalKey
exceptionClass
exceptionMessage
failedAt
attempt
correlationId
operationId
~~~

Payload original có thể giữ trực tiếp hoặc lưu reference nếu nhạy cảm/lớn.

### 15.2. DLT không phải nơi bỏ quên record

Runbook:

1. Alert khi DLT có record.
2. Đọc metadata và exception.
3. Phân loại lỗi.
4. Sửa code/schema/data.
5. Kiểm tra idempotency.
6. Replay chọn lọc.
7. Theo dõi lag và side effect.
8. Ghi nguyên nhân gốc.

### 15.3. Replay DLT

Replay có thể:

- Gửi lại main topic.
- Gửi vào topic sửa lỗi riêng.
- Đọc DLT bằng consumer group mới.
- Replay theo offset/time window.
- Replay từng operation ID.

Không replay toàn bộ DLT mà không có rate limit hoặc approval.

---

## 16. Spring Kafka DefaultErrorHandler

Spring Kafka hiện đại dùng CommonErrorHandler. DefaultErrorHandler có thể retry bằng BackOff và recover record sau khi retry hết.

~~~java
@Bean
DefaultErrorHandler errorHandler(
        KafkaOperations<Object, Object> operations) {

    var recoverer = new DeadLetterPublishingRecoverer(
            operations,
            (record, exception) -> new TopicPartition(
                    record.topic() + ".DLT",
                    record.partition()));

    var backOff = new FixedBackOff(1000L, 2L);

    return new DefaultErrorHandler(recoverer, backOff);
}
~~~

Ý nghĩa:

- Listener exception được đưa đến error handler.
- Handler chờ/retry theo backoff.
- Khi retry exhausted, recoverer publish sang DLT.
- Offset recovered có thể được commit theo AckMode/configuration.

Cần test chính xác số delivery attempts và behavior khi recoverer publish thất bại.

### 16.1. Phân loại exception

~~~java
errorHandler.addNotRetryableExceptions(
        InvalidSchemaException.class,
        ValidationException.class,
        UnsupportedVersionException.class);
~~~

Schema/validation error thường nên đi DLT sớm. Timeout hoặc temporary downstream error có thể retry giới hạn.

### 16.2. DLT partition mapping

Nếu DLT giữ cùng partition với record gốc, có thể dễ trace ordering partition. Nhưng DLT topic phải có đủ partition hoặc cần partition resolver rõ ràng.

---

## 17. Spring Kafka RetryableTopic

Spring Kafka hỗ trợ non-blocking retry bằng RetryableTopic và RetryTopicConfiguration.

Ví dụ minh họa:

~~~java
@RetryableTopic(
        attempts = "4",
        backoff = @Backoff(
                delay = 10000,
                multiplier = 6.0,
                maxDelay = 60000),
        include = TemporaryDownstreamException.class,
        exclude = {
                ValidationException.class,
                InvalidSchemaException.class
        })
@KafkaListener(
        topics = "orders.v1",
        groupId = "order-notification")
public void handle(OrderEvent event) {
    notificationService.send(event);
}

@DltHandler
public void handleDlt(
        OrderEvent event,
        @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
    log.error("Record sent to DLT topic={}", topic);
}
~~~

Semantics cần nhớ:

- Framework tạo hoặc dùng retry topic/DLT theo configuration.
- Retry topic thường giữ partition mapping theo record gốc nếu cấu hình mặc định phù hợp.
- Non-blocking retry không hỗ trợ batch listener.
- Non-blocking retry không kết hợp với container transactions trong các flow được framework mô tả.
- Ordering end-to-end có thể không còn được đảm bảo.

Luôn kiểm tra version Spring Kafka đang dùng vì annotation/options thay đổi theo major version.

---

## 18. Blocking và non-blocking kết hợp

Một chiến lược thực tế:

~~~text
lỗi database ngắn
  → blocking retry 3 lần

lỗi provider chậm
  → retry topic 10s, 1m

lỗi schema/validation
  → DLT ngay
~~~

Flow:

~~~text
orders.v1
   ├── DatabaseTemporaryException
   │       └── blocking retry
   ├── ProviderTimeout
   │       └── retry.10s → retry.1m
   └── ValidationException
           └── orders.dlt
~~~

Lợi ích:

- Giữ lỗi ngắn trong cùng partition flow.
- Đẩy delay dài sang topic riêng.
- Không retry lỗi permanent.
- Giảm block main topic.

Cần document rõ exception classification để team không thay đổi behavior vô tình.

---

## 19. Ordering khi retry

### 19.1. Blocking retry giữ order tốt hơn

~~~text
P0:
m1 fail
m2 chờ
m3 chờ
~~~

Nếu m1 thành công trước khi m2 được xử lý, thứ tự trong partition được bảo toàn.

### 19.2. Retry topic có thể phá order

~~~text
P0 main:
m1 fail → retry
m2 success → main commit

retry:
m1 xử lý sau
~~~

Nếu m1 và m2 cùng aggregate, m2 có thể chạy trước m1.

### 19.3. Cách giảm rủi ro

- Dùng blocking retry cho flow cần order chặt.
- Dùng key và partition theo aggregate.
- Ghi sequence/version vào event.
- Consumer reject event có version vượt quá expected.
- Lưu pending event cho đến khi event trước hoàn tất.
- Tách command state transition khỏi notification event.
- Không dùng non-blocking retry cho payment/inventory nếu không có state guard.

### 19.4. FIFO không có nghĩa retry luôn đúng thứ tự

Kafka ordering chỉ trong partition. Retry topic là topic khác, nên end-to-end order cần thiết kế riêng.

---

## 20. Pause/resume partition trong retry architecture

Một retry manager có thể:

~~~text
record m1 ở P0 fail
  ↓
pause P0
  ↓
P1, P2 vẫn poll
  ↓
đến thời điểm retry
  ↓
resume P0
~~~

Cần bảo đảm:

- Consumer tiếp tục poll để giữ membership.
- Pause có timeout.
- Metric paused duration.
- Không pause toàn container vì một partition lỗi nếu không cần.
- Đặt policy khi pause quá lâu.
- Dừng graceful không làm mất current state.

Spring RetryTopic có cơ chế pause partition để chờ backoff trong các trường hợp non-blocking retry. Tuy nhiên, đây vẫn là behavior cần hiểu qua container/topic architecture, không chỉ nhìn annotation.

---

## 21. Retry và consumer lag

Retry có thể làm lag theo nhiều cách:

### Blocking retry

~~~text
main partition bị giữ
→ lag main tăng
~~~

### Retry topic

~~~text
main lag giảm
retry topic lag tăng
~~~

Không nhìn một metric duy nhất. Theo dõi:

- Main topic lag.
- Retry topic lag.
- DLT count.
- Retry age.
- Processing latency.
- Attempt count.
- Partition paused duration.
- Rate retry → success.
- Rate retry → DLT.

Một main topic có lag thấp nhưng retry topic bị nghẽn vẫn là incident.

---

## 22. Producer, consumer và DLT schema

Retry/DLT record có thể giữ nguyên payload hoặc bọc envelope mới.

Envelope gợi ý:

~~~json
{
  "originalTopic": "orders.v1",
  "originalPartition": 2,
  "originalOffset": 8192,
  "attempt": 3,
  "failedAt": "2026-09-26T08:30:00Z",
  "exceptionType": "TemporaryDownstreamException",
  "correlationId": "checkout-789",
  "operationId": "order-created:order-123",
  "payload": {
    "orderId": "order-123"
  }
}
~~~

Không thay đổi business payload một cách không tương thích qua retry topic. Consumer retry cần biết metadata gốc để trace và recovery.

---

## 23. Thực hành theo roadmap

Topology:

~~~text
orders.v1
   ↓ lỗi tạm thời
orders.retry.10s
   ↓
orders.retry.1m
   ↓ lỗi vĩnh viễn
orders.dlt
~~~

### 23.1. Error classification

Cấu hình:

~~~text
TemporaryDownstreamException
  → retry.10s → retry.1m → DLT

ValidationException
  → DLT ngay

SchemaException
  → DLT ngay

UnknownException
  → blocking retry ngắn → DLT
~~~

### 23.2. Ordering test

Gửi:

~~~text
order-1: Event-1
order-1: Event-2
order-1: Event-3
~~~

Làm Event-1 fail tạm thời. So sánh:

- Blocking retry.
- Retry topic.
- Event-2 có chạy trước Event-1 không?
- Business state có sai không?
- Sequence guard có cần không?

### 23.3. Poison record test

Gửi một record JSON sai schema. Kiểm tra:

- Có retry không?
- Có block partition không?
- DLT giữ metadata gì?
- Các record sau có tiếp tục không?
- Alarm nào firing?

### 23.4. Replay DLT

1. Sửa consumer.
2. Chọn một record DLT.
3. Replay về main topic.
4. Theo dõi duplicate effect.
5. Kiểm tra offset và correlation ID.
6. Ghi runbook.

---

## 24. Bài tập thực hành

### Bài 1: Commit timing

Vẽ flow và phân tích crash ở các điểm:

~~~text
poll → process → database commit → offset commit
~~~

Phân tích:

1. Crash trước process.
2. Crash sau database commit.
3. Crash trước offset commit.
4. Crash sau offset commit.

Với mỗi trường hợp, ghi lost record, duplicate record và biện pháp giảm rủi ro.

### Bài 2: Partition ordering

Tạo topic 3 partition. Gửi event cho 3 order ID bằng orderId làm key.

1. Kiểm tra event cùng order có cùng partition không.
2. Làm event giữa fail.
3. So sánh blocking retry và retry topic.
4. Xác định global order có tồn tại không.

### Bài 3: Retry policy

Thiết kế policy:

| Exception | Retry |
|---|---|
| HTTP 503 | blocking 2 lần, sau đó retry topic |
| Timeout | retry topic 10s rồi 1m |
| Validation | DLT ngay |
| Schema | DLT ngay |
| Unknown | 3 lần với backoff, sau đó DLT |

Ghi rõ topic, attempt và recovery behavior.

### Bài 4: DLT

Tạo orders.dlt. Gửi record lỗi vĩnh viễn.

Kiểm tra:

- Original topic.
- Original partition.
- Original offset.
- Key.
- Exception.
- Attempt.
- Correlation ID.
- Replay procedure.

### Bài 5: Non-blocking ordering

Gửi m1, m2, m3 cùng partition. Làm m1 fail và m2/m3 success.

Quan sát thứ tự side effect với retry topic. Đề xuất hai cách bảo vệ business state.

### Bài 6: Pause/resume

Pause partition có m1 lỗi trong 20 giây, nhưng cho partition khác tiếp tục. Theo dõi:

- Poll liveness.
- Rebalance.
- Lag theo partition.
- Thời gian paused.
- Resume behavior.

### Bài 7: Spring RetryableTopic

Dùng RetryableTopic cho temporary exception và exclude validation exception.

Kiểm tra:

- Topic retry được tạo/dùng thế nào.
- DLT handler nhận metadata gì.
- Batch listener có dùng được không.
- Consumer group của retry topic.
- Ordering có còn như main topic không.

### Bài 8: Idempotent replay

Replay một record DLT đã từng tạo database effect. Chứng minh operation ID/unique constraint không tạo effect lần hai.

---

## 25. Câu hỏi tự kiểm tra

1. Commit trước processing có rủi ro gì?
2. Commit sau processing dẫn đến guarantee nào?
3. Vì sao duplicate record là bình thường?
4. Ordering được đảm bảo ở phạm vi nào?
5. Vì sao global ordering làm giảm parallelism?
6. Poison record có thể block partition như thế nào?
7. Blocking retry giữ ordering bằng cách nào?
8. Vì sao blocking retry làm tăng lag?
9. Retry topic giải phóng main partition ra sao?
10. Vì sao non-blocking retry có thể phá ordering end-to-end?
11. Khi nào dùng DLT ngay?
12. max retry có nên giống nhau cho mọi exception không?
13. Pause khác commit offset thế nào?
14. Vì sao pause lâu có thể gây rebalance?
15. DLT cần lưu metadata nào?
16. Vì sao DLT replay cần idempotency?
17. Retry topic có cần consumer group riêng không?
18. Vì sao schema error không nên retry vô hạn?
19. DefaultErrorHandler và RetryableTopic khác nhau thế nào?
20. Vì sao FIFO/key ordering không tự giải quyết retry ordering?

---

## 26. Checklist hoàn thành Tuần 11

- [ ] Mình phân biệt commit trước và sau processing.
- [ ] Mình hiểu at-least-once consumer.
- [ ] Mình thiết kế được idempotent handler.
- [ ] Mình biết ordering chỉ nằm trong partition.
- [ ] Mình hiểu global ordering và chi phí của nó.
- [ ] Mình nhận diện poison record.
- [ ] Mình biết blocking retry có thể block partition.
- [ ] Mình biết retry topic có thể làm main flow non-blocking.
- [ ] Mình hiểu non-blocking retry có trade-off ordering.
- [ ] Mình thiết kế được retry.10s và retry.1m.
- [ ] Mình cấu hình được DLT cho lỗi permanent/exhausted.
- [ ] Mình biết giữ original topic/partition/offset.
- [ ] Mình hiểu pause/resume partition.
- [ ] Mình phân loại retryable và non-retryable exception.
- [ ] Mình dùng được DefaultErrorHandler ở mức cơ bản.
- [ ] Mình hiểu DeadLetterPublishingRecoverer.
- [ ] Mình biết RetryableTopic không phù hợp với mọi batch/transaction flow.
- [ ] Mình có runbook replay DLT.
- [ ] Mình theo dõi lag main/retry/DLT.
- [ ] Mình hoàn thành ordering, poison record và replay lab.

## Kết luận

Kafka delivery thực tế thường chọn at-least-once bằng cách xử lý record trước rồi commit offset. Điều này giảm mất record nhưng tạo duplicate, nên business effect phải idempotent.

Ordering chỉ được đảm bảo trong một partition. Blocking retry bảo vệ thứ tự tốt hơn nhưng có thể giữ partition và làm lag tăng. Retry topic/non-blocking retry giúp main flow tiếp tục nhưng có thể làm record sau hoàn thành trước record lỗi. Vì vậy retry strategy phải dựa trên business ordering requirement, không chỉ dựa trên thời gian delay.

Flow cần nhớ:

~~~text
orders.v1
   ├── lỗi transient ngắn
   │      └── blocking retry + backoff
   ├── lỗi transient dài
   │      └── orders.retry.10s → orders.retry.1m
   ├── lỗi schema/validation
   │      └── orders.dlt
   └── success
          └── process idempotent → commit offset
~~~

Ở Tuần 12, ta sẽ chuyển sang Kafka performance và durability: batch size, linger, compression, throughput, storage, replication và các trade-off khi tối ưu producer/consumer.

## Nguồn chính thức

- [Spring Kafka handling exceptions](https://docs.spring.io/spring-kafka/reference/kafka/annotation-error-handling.html)
- [Spring Kafka non-blocking retries](https://docs.spring.io/spring-kafka/reference/retrytopic.html)
- [Spring Kafka retry configuration](https://docs.spring.io/spring-kafka/reference/retrytopic/retry-config.html)
- [Spring Kafka RetryableTopic](https://docs.spring.io/spring-kafka/docs/current/api/org/springframework/kafka/annotation/RetryableTopic.html)
- [Apache Kafka Consumer API](https://kafka.apache.org/40/javadoc/org/apache/kafka/clients/consumer/Consumer.html)
- [Apache Kafka consumer configuration](https://kafka.apache.org/40/generated/consumer_config.html)
- [Apache Kafka design and offset replay](https://kafka.apache.org/40/design/design/)

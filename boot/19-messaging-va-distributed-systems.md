# Tuần 19 — Messaging và distributed systems

> Stack thực hành: Java 21, Spring Boot, Spring Kafka, Kafka, PostgreSQL và Redis. Bài này nối tiếp transactional outbox ở Tuần 18; Kafka là lựa chọn triển khai chính, RabbitMQ được so sánh ở mức khái niệm.

## 1. Mục tiêu bài học

Sau tuần này, bạn có thể:

- Phân biệt command, event, queue, topic, partition và consumer group.
- Viết producer và consumer bằng Spring Kafka.
- Hiểu at-most-once, at-least-once và effectively-once processing.
- Thiết kế idempotent consumer chống xử lý trùng.
- Cấu hình retry, backoff và dead-letter topic.
- Hiểu ordering, partition key và giới hạn ordering toàn cục.
- Nối transactional outbox với Kafka publisher.
- Xử lý timeout, consumer restart, rebalance và poison message.
- Nhận biết eventual consistency trong workflow TaskAssigned.
- Hiểu Saga ở mức orchestration/choreography và compensation.
- Test message flow bằng embedded broker hoặc Testcontainers.
- Biết khi nào Kafka phù hợp hơn RabbitMQ và ngược lại.

Messaging không làm hệ thống hết lỗi. Nó đổi một lời gọi trực tiếp thành một workflow có delay, duplicate, retry, ordering và failure mode riêng. Mỗi message cần contract, owner, retry policy, idempotency và observability.

---

## 2. Vì sao dùng messaging?

### 2.1. Gọi trực tiếp

~~~text
Task service ──HTTP call──> Notification service ──email──> Provider
~~~

Ưu điểm là caller biết kết quả ngay. Nhược điểm là request bị phụ thuộc latency/availability của notification service và provider.

### 2.2. Gửi message

~~~text
Task service ──publish TaskAssigned──> broker
                                      ↓
                              Notification consumer
                                      ↓
                                   email
~~~

Task API có thể trả nhanh sau khi event đã được lưu/publish. Notification xử lý bất đồng bộ, retry độc lập và scale riêng. Đổi lại API phải document trạng thái `queued` hoặc eventual consistency.

### 2.3. Khi không nên dùng message

- Caller cần kết quả đồng bộ để quyết định response.
- Workflow quá nhỏ và thêm broker chỉ làm khó debug.
- Team chưa có khả năng vận hành broker, alert và replay.
- Dữ liệu cần transaction ACID giữa hai bước nhưng chưa có outbox/Saga.

Đừng dùng Kafka để thay một method call nội bộ đơn giản trong cùng modular monolith.

---

## 3. Thuật ngữ cốt lõi

| Khái niệm | Ý nghĩa |
|---|---|
| Producer | Ứng dụng gửi record/message |
| Consumer | Ứng dụng đọc và xử lý message |
| Topic | Luồng message có tên, thường trong Kafka |
| Queue | Nơi message chờ một worker xử lý, thường trong RabbitMQ |
| Partition | Nhánh có thứ tự riêng trong Kafka topic |
| Consumer group | Nhóm consumer chia nhau partition |
| Offset | Vị trí record trong partition |
| Ack/commit | Xác nhận message đã được xử lý tới broker |
| DLT/DLQ | Nơi giữ message không xử lý được sau retry |
| Replay | Đọc lại message từ offset cũ |

### 3.1. Command và event

- **Command**: yêu cầu làm việc, thường có một handler sở hữu. Ví dụ `SendTaskNotification`.
- **Event**: thông báo điều đã xảy ra, có thể có nhiều subscriber. Ví dụ `TaskAssigned`.

Tên command dùng động từ; tên event dùng quá khứ. Consumer không nên biến event thành command ngầm mà không document contract.

### 3.2. Message envelope

Một envelope nên có metadata ổn định:

~~~json
{
  "messageId": "0d4c...",
  "eventType": "TaskAssigned.v1",
  "occurredAt": "2026-09-26T09:30:00Z",
  "producer": "task-api",
  "correlationId": "request-123",
  "causationId": "command-456",
  "schemaVersion": 1,
  "payload": {
    "taskId": "task-1",
    "assigneeId": "user-1"
  }
}
~~~

`messageId` dùng cho idempotency. `correlationId` nối cả workflow; `causationId` chỉ ra message nào gây ra event này. Không đưa access token, password hoặc payload PII không cần thiết vào message.

---

## 4. Kafka và RabbitMQ: chọn một trước

### 4.1. Kafka

Kafka là distributed log:

- Record được append vào topic partition.
- Consumer giữ offset và có thể replay.
- Nhiều consumer group đọc cùng topic độc lập.
- Ordering được đảm bảo trong một partition.
- Phù hợp event stream, analytics, audit và throughput lớn.

### 4.2. RabbitMQ

RabbitMQ là message broker thiên về routing và work queue:

- Producer gửi vào exchange.
- Exchange route tới queue bằng binding/routing key.
- Consumer ack message; queue thường phân phối công việc cho worker.
- Phù hợp command queue, routing linh hoạt và workload task.

### 4.3. Bảng so sánh

| Tiêu chí | Kafka | RabbitMQ |
|---|---|---|
| Mô hình | Append log/topic | Exchange/queue |
| Replay | Tự nhiên qua offset | Cần giữ/requeue hoặc plugin |
| Ordering | Trong partition | Trong queue, tùy concurrency |
| Scale | Partition/consumer group | Queue/consumer, routing |
| Event stream | Rất phù hợp | Có thể nhưng không phải trọng tâm |
| Work queue | Có thể | Rất phù hợp |

Chọn Kafka cho bài lab này vì cần minh họa partition, offset, replay và consumer group. Sau khi nắm contract/idempotency, học RabbitMQ sẽ dễ hơn.

---

## 5. Topic và partition design

### 5.1. Topic theo business event

Ví dụ:

~~~text
task.events.v1
task.events.v1.DLT
notification.commands.v1
~~~

Không tạo topic cho từng `taskId`. Topic là contract stream; key phân phối record trong topic.

### 5.2. Partition key

Nếu cần thứ tự cho cùng một task, dùng `taskId` làm key:

~~~text
TaskAssigned(task-1) → partition 2
TaskRenamed(task-1)  → partition 2
TaskClosed(task-1)   → partition 2
~~~

Các record cùng key đi cùng partition và giữ thứ tự tương đối. Không có ordering toàn cục giữa nhiều partition.

Nếu dùng `assigneeId` làm key, các task của cùng user có thứ tự nhưng task có thể bị dồn vào một partition. Chọn key theo invariant cần bảo vệ, không theo field thuận tiện.

### 5.3. Số partition và concurrency

Consumer concurrency hữu ích tối đa gần số partition có dữ liệu. Tăng thread lên 20 khi topic chỉ có 3 partition không làm xử lý nhanh hơn và có thể tăng overhead.

Thay đổi số partition có thể ảnh hưởng phân phối key và ordering kỳ vọng. Quyết định partition count dựa trên throughput, retention và consumer group, không chỉ dựa trên số pod.

---

## 6. Spring Kafka producer

### 6.1. Dependency

~~~xml
<dependency>
    <groupId>org.springframework.kafka</groupId>
    <artifactId>spring-kafka</artifactId>
</dependency>
~~~

Để Spring Boot BOM quản lý version. Broker version, client version và serializer phải có compatibility matrix; không tự ghép version tùy tiện.

### 6.2. Producer configuration

~~~yaml
spring:
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
      acks: all
      retries: 5
      properties:
        enable.idempotence: true
        max.in.flight.requests.per.connection: 5
        compression.type: zstd
    consumer:
      group-id: task-notification
      auto-offset-reset: earliest
      enable-auto-commit: false
~~~

`acks=all` và idempotent producer giảm nguy cơ mất/duplicate do retry phía producer, nhưng không biến toàn bộ workflow thành exactly-once. Consumer side effect vẫn phải idempotent.

### 6.3. KafkaTemplate

~~~java
@Component
public class KafkaTaskEventPublisher implements TaskEventPort {

    private final KafkaTemplate<String, TaskAssignedEvent> kafka;

    public KafkaTaskEventPublisher(
            KafkaTemplate<String, TaskAssignedEvent> kafka
    ) {
        this.kafka = kafka;
    }

    @Override
    public CompletableFuture<SendResult<String, TaskAssignedEvent>> publish(
            TaskAssignedEvent event
    ) {
        return kafka.send(
                "task.events.v1",
                event.taskId().toString(),
                event
        );
    }
}
~~~

Producer phải quan sát future gửi. Không trả HTTP 201 cho business transaction rồi giả định `send()` luôn thành công nếu event chưa được lưu outbox hoặc có delivery guarantee khác.

### 6.4. JSON contract

Consumer cần biết schema event. Có thể dùng JSON với type mapping hoặc envelope tự quản lý:

~~~java
public record TaskAssignedEvent(
        UUID messageId,
        UUID taskId,
        UUID assigneeId,
        Instant occurredAt,
        int schemaVersion
) {}
~~~

Đừng serialize JPA entity. Khi đổi schema:

- Thêm field optional trước.
- Giữ consumer đọc được version cũ trong thời gian chuyển tiếp.
- Tăng `schemaVersion` cho breaking change.
- Xác định retention/replay compatibility.

---

## 7. Consumer và consumer group

### 7.1. Listener cơ bản

~~~java
@Component
public class TaskAssignedConsumer {

    @KafkaListener(
            topics = "task.events.v1",
            groupId = "notification-service"
    )
    public void consume(TaskAssignedEvent event) {
        notificationService.send(event);
    }
}
~~~

Mọi instance dùng cùng group ID sẽ chia partition; chỉ một consumer trong group xử lý một record tại một thời điểm bình thường. Group khác nhận toàn bộ stream độc lập.

### 7.2. Concurrency

~~~java
@KafkaListener(
        topics = "task.events.v1",
        groupId = "notification-service",
        concurrency = "3"
)
public void consume(TaskAssignedEvent event) {
    notificationService.send(event);
}
~~~

`concurrency=3` tạo consumer thread trong instance; tổng consumer của mọi instance cùng group phải phù hợp số partition. Processing order giữa các partition không được đảm bảo.

### 7.3. Commit offset

Với at-least-once, chỉ commit offset sau khi xử lý thành công:

~~~yaml
spring:
  kafka:
    listener:
      ack-mode: record
      missing-topics-fatal: true
~~~

Nếu process chết sau side effect nhưng trước commit offset, record sẽ được đọc lại. Đây là lý do cần idempotent consumer.

Không commit offset trước khi gửi email hoặc ghi database nếu side effect chưa durable. Commit sau xử lý cũng không phải atomic với external provider; cần deduplication/outbox ở consumer.

---

## 8. At-least-once và idempotent consumer

### 8.1. Các delivery semantic

- **At-most-once**: commit/ack trước xử lý; có thể mất message nhưng ít duplicate.
- **At-least-once**: xử lý rồi mới commit/ack; không mất dễ dàng nhưng có duplicate.
- **Exactly-once**: chỉ đúng trong phạm vi transaction/engine được hỗ trợ; không tự bao phủ email, HTTP hoặc database khác.

Trong phần lớn business integration, thiết kế at-least-once + idempotent handler thực tế và dễ vận hành hơn việc hứa exactly-once.

### 8.2. Dedupe table

~~~sql
CREATE TABLE processed_message (
    consumer_name VARCHAR(100) NOT NULL,
    message_id    UUID NOT NULL,
    processed_at  TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (consumer_name, message_id)
);
~~~

Handler lưu `processed_message` cùng transaction với side effect database:

~~~java
@Transactional
public void handle(TaskAssignedEvent event) {
    if (processedMessages.exists("notification-service", event.messageId())) {
        return;
    }

    notificationRepository.enqueue(
            NotificationCommand.forTask(event.taskId(), event.assigneeId())
    );
    processedMessages.insert(
            "notification-service",
            event.messageId()
    );
}
~~~

Unique primary key bảo vệ khi hai worker cùng xử lý. Nếu insert bị duplicate, handler coi message đã được xử lý và không gửi lại side effect.

### 8.3. Idempotency key ở provider

Nếu consumer gọi email/payment HTTP provider, gửi `Idempotency-Key: notification:{messageId}` nếu provider hỗ trợ. Dedupe local không đủ nếu process chết sau khi provider nhận request nhưng trước khi local commit.

Với provider không hỗ trợ idempotency, dùng bảng delivery với trạng thái `PENDING`, `SENT`, `FAILED`, timeout và reconciliation thủ công. Không thể chứng minh “chắc chắn không gửi trùng” khi network timeout khiến kết quả không xác định; hãy thiết kế để duplicate không gây thiệt hại.

---

## 9. Retry và dead-letter topic

### 9.1. Phân loại lỗi

Retry được:

- Timeout ngắn với external provider.
- HTTP 429/503 theo `Retry-After`.
- Connection reset hoặc broker tạm unavailable.

Không retry vô hạn:

- Payload sai schema.
- Validation/business rule không thể thành công.
- Authorization failure.
- Duplicate đã được xác nhận.

### 9.2. `DefaultErrorHandler` và DLT

~~~java
@Bean
ConcurrentKafkaListenerContainerFactory<String, TaskAssignedEvent>
taskKafkaListenerContainerFactory(
        ConsumerFactory<String, TaskAssignedEvent> consumerFactory,
        KafkaTemplate<String, TaskAssignedEvent> kafkaTemplate
) {
    var factory =
            new ConcurrentKafkaListenerContainerFactory<String, TaskAssignedEvent>();
    factory.setConsumerFactory(consumerFactory);

    var recoverer = new DeadLetterPublishingRecoverer(
            kafkaTemplate,
            (record, exception) -> new TopicPartition(
                    record.topic() + ".DLT",
                    record.partition()
            )
    );
    var handler = new DefaultErrorHandler(
            recoverer,
            new FixedBackOff(1_000L, 2L)
    );
    handler.addNotRetryableExceptions(
            InvalidMessageException.class,
            ValidationException.class
    );
    factory.setCommonErrorHandler(handler);
    return factory;
}
~~~

`FixedBackOff(1000, 2)` nghĩa là delay 1 giây và tối đa 2 lần retry sau lần xử lý đầu, tổng cộng 3 attempts. Production thường dùng backoff có jitter hoặc retry topic để không giữ partition quá lâu.

### 9.3. DLT/DLQ vận hành thế nào?

DLT không phải thùng rác. Mỗi record DLT cần:

- Lý do lỗi và stack trace đã redacted.
- Topic/partition/offset gốc.
- Số lần retry.
- Owner và SLA xử lý.
- Dashboard số lượng và tuổi message cũ nhất.
- Quy trình replay sau khi sửa lỗi.

Replay phải có idempotency. Nếu replay toàn bộ DLT vào topic chính mà không kiểm tra, có thể gửi email trùng hoặc tạo record duplicate.

### 9.4. Retry topic

Với lỗi cần delay dài, tách retry topic:

~~~text
task.events.v1
  → retry.1m
  → retry.10m
  → retry.1h
  → task.events.v1.DLT
~~~

Delay topic giải phóng partition chính nhưng làm topology và monitoring phức tạp hơn. Chọn một chiến lược nhất quán thay vì vừa retry blocking vừa retry topic không kiểm soát.

---

## 10. Ordering

### 10.1. Ordering trong Kafka

Kafka chỉ đảm bảo thứ tự trong một partition. Với cùng `taskId` làm key, các event của task đó giữ thứ tự nếu producer gửi đúng và consumer không xử lý song song cùng partition.

Không giả định:

~~~text
TaskAssigned(task-1) < TaskClosed(task-2)
~~~

giữa hai task khác nhau ở partition khác.

### 10.2. Khi event đến sai thứ tự

Có thể:

- Đưa version/sequence number vào aggregate event.
- Consumer kiểm tra `expectedVersion` và trì hoãn event sớm.
- Lưu event rồi reconcile khi thiếu predecessor.
- Thiết kế event commutative/idempotent.
- Dùng snapshot/read model cuối cùng thay vì áp dụng mọi event mù quáng.

Không dùng timestamp client để quyết định thứ tự nếu clock không đồng bộ. Dùng sequence do aggregate/database cấp.

### 10.3. Partition key và hot partition

Key không phù hợp có thể dồn mọi record nóng vào một partition. Theo dõi lag theo partition và phân bố key. Không đổi key giữa các version mà không đánh giá ordering và replay.

---

## 11. Transactional outbox với Kafka

### 11.1. Vì sao không dual-write trực tiếp?

Đoạn code sau có khoảng trống:

~~~text
commit database thành công
publish Kafka thất bại
→ business state có nhưng event mất
~~~

Hoặc ngược lại: event đã publish nhưng database rollback. Gọi DB và Kafka trong hai hệ thống không phải một transaction ACID mặc định.

### 11.2. Outbox flow

~~~text
TaskService transaction:
  save Task(ASSIGNED)
  save OutboxEvent(TaskAssigned.v1)
  commit

Outbox publisher:
  claim NEW rows
  KafkaTemplate.send(topic, key, envelope)
  mark PUBLISHED
~~~

Publisher crash sau Kafka send nhưng trước `PUBLISHED` sẽ gửi lại. Producer key/message ID và consumer idempotency xử lý duplicate.

### 11.3. Polling publisher

~~~java
@Scheduled(fixedDelayString = "${outbox.poll-ms:500}")
@Transactional
public void publishBatch() {
    List<OutboxEvent> events = outbox.claimBatch(100, clock.instant());
    for (OutboxEvent event : events) {
        kafkaTemplate.send(
                event.topic(),
                event.aggregateId(),
                event.payload()
        );
    }
}
~~~

Snippet trên là khung khái niệm: không mark `PUBLISHED` trước khi send future thành công, và không giữ DB transaction trong lúc chờ Kafka nếu transaction kéo dài. Production cần thiết kế claim lease, callback/transactional producer, retry và recovery rõ ràng.

### 11.4. CDC alternative

Debezium/CDC đọc transaction log của database và publish outbox table. CDC giảm code polling nhưng thêm connector, schema registry, operational burden và replay policy. Chọn polling trước để hiểu invariant; cân nhắc CDC khi throughput/latency yêu cầu.

---

## 12. Consumer resilience và vận hành

### 12.1. Consumer restart và rebalance

Khi consumer chết hoặc scale, Kafka rebalance partition giữa các member của group. Trong rebalance:

- Partition có thể tạm dừng.
- Consumer xử lý lâu có thể vượt `max.poll.interval.ms` và bị kick khỏi group.
- Commit offset không kịp có thể làm record được đọc lại.

Giới hạn thời gian xử lý mỗi message hoặc chuyển công việc dài sang job worker. Đừng tăng `max.poll.interval.ms` vô hạn để che handler chậm.

### 12.2. Backpressure

Theo dõi:

- Consumer lag theo topic/partition/group.
- Processing duration p95/p99.
- DLT rate và retry rate.
- Queue/outbox age.
- External provider saturation.

Khi lag tăng, không chỉ tăng concurrency. Kiểm tra partition count, DB pool, provider rate limit và idempotency trước khi scale.

### 12.3. Poison message

Một payload luôn gây exception có thể giữ partition mãi nếu retry vô hạn. DLT hoặc retry topic bounded là bắt buộc. Sau khi sửa consumer, replay có chọn lọc và theo dõi side effect.

### 12.4. Message size và retention

- Message chỉ nên chứa dữ liệu cần cho consumer.
- Dữ liệu lớn đưa vào object storage, message chỉ chứa reference.
- Retention dựa trên replay/audit requirement và storage budget.
- Compression giảm network nhưng tăng CPU.
- Không đặt PII vào topic có retention dài nếu không có policy mã hóa/xóa.

---

## 13. Eventual consistency

Sau khi Task được assign:

~~~text
T0: database task = ASSIGNED
T1: outbox event = NEW
T2: Kafka publish
T3: notification consumer xử lý
T4: email = SENT
~~~

Trong khoảng T0–T4, API đọc task có thể thấy `ASSIGNED` nhưng notification chưa `SENT`. Đây không phải bug nếu contract document rõ.

Thiết kế read model/status:

~~~text
NotificationDelivery:
  id
  taskId
  channel
  status: QUEUED | SENT | RETRYING | FAILED
  lastErrorCode
  attempts
~~~

UI có thể hiển thị “Notification queued” thay vì giả định đã gửi. Nếu cần wait synchronous, đó là trade-off latency/availability và nên dùng timeout rõ.

### 13.1. Read-your-writes

Nếu người dùng vừa assign task rồi ngay lập tức xem notification, read model có thể chưa cập nhật. Giải pháp:

- Trả trạng thái từ command response.
- Poll status bằng delivery ID.
- Dùng correlation/version để read model chờ tới sequence cần thiết.
- Không ép mọi read vào synchronous cross-service call.

---

## 14. Saga ở mức khái niệm

Saga chia một workflow dài thành các local transaction và compensation:

~~~text
Create order
  → reserve inventory
  → charge payment
  → arrange shipment
~~~

Nếu charge payment thất bại sau khi reserve inventory, compensation là release inventory. Compensation không phải rollback database; nó là một business action mới và có thể cũng thất bại.

### 14.1. Choreography

Mỗi service lắng nghe event và phát event tiếp:

~~~text
OrderCreated → InventoryReserved → PaymentCharged → ShipmentRequested
~~~

Ưu điểm ít coordinator; nhược điểm flow khó nhìn khi service nhiều, dễ tạo event chain ngầm.

### 14.2. Orchestration

Một coordinator gửi command và theo dõi state:

~~~text
OrderSaga
  ├─ ReserveInventory
  ├─ ChargePayment
  └─ RequestShipment
~~~

Ưu điểm dễ thấy workflow và timeout; nhược điểm coordinator cần sở hữu state và tránh biến thành god service.

### 14.3. Khi nào chưa cần Saga?

Trong modular monolith cùng database, local transaction + outbox có thể đủ. Đừng thêm Saga framework trước khi có nhiều bounded context, failure mode thực tế và requirement phân tán.

---

## 15. RabbitMQ mapping

Nếu chọn RabbitMQ thay Kafka, map khái niệm như sau:

| Kafka | RabbitMQ |
|---|---|
| Topic | Exchange + queue binding |
| Partition | Queue/concurrency, không hoàn toàn tương đương |
| Consumer group | Nhiều consumer cùng queue |
| Offset commit | Message ack |
| DLT | Dead-letter exchange/queue |
| Key | Routing key/message property |
| Replay offset | Requeue/republish hoặc lưu message |

RabbitMQ worker ack sau khi side effect durable. Nack/requeue vô hạn có thể tạo hot loop; dùng dead-letter exchange và retry queue có TTL/bounded attempts.

Không viết abstraction quá chung để che mọi khác biệt broker. Port nên mô tả business capability (`publishTaskAssigned`), còn delivery semantics và adapter config vẫn phải document theo broker thật.

---

## 16. Test messaging

### 16.1. Unit test

Test envelope, mapper, deduplication decision, retry classification và compensation bằng object/fake thuần Java.

### 16.2. Integration test

Dùng Kafka Testcontainers để kiểm tra:

- Producer serialize đúng event.
- Consumer group nhận message.
- Offset chỉ commit sau xử lý.
- Duplicate message không gửi notification hai lần.
- DLT nhận poison message sau số retry giới hạn.
- Restart consumer xử lý tiếp từ offset.
- Partition key giữ order của cùng aggregate.

Không chỉ mock `KafkaTemplate` rồi kết luận broker config đúng.

### 16.3. Contract test

Producer và consumer chia schema contract:

- Event type/version bắt buộc.
- Field required/optional.
- Backward/forward compatibility.
- Unknown field handling.
- Maximum message size.

Schema Registry hữu ích khi nhiều producer/consumer cần compatibility check; vẫn cần review semantics, không chỉ schema syntax.

### 16.4. Failure test

Mô phỏng:

- Kafka unavailable khi outbox publisher chạy.
- Consumer crash sau email provider thành công.
- Provider timeout nhưng request có thể đã tới.
- Duplicate event.
- DLT replay.
- Hai consumer tranh cùng outbox row.
- Rebalance trong lúc xử lý message lâu.

Mỗi test phải trả lời: message còn ở đâu, offset nào đã commit, side effect có duplicate không, operator nhìn metric nào.

---

## 17. Lỗi thường gặp

- Dùng event để gọi synchronous response bắt buộc.
- Hứa exactly-once cho email/HTTP/database khác.
- Commit offset trước side effect.
- Không có `messageId` hoặc idempotency key.
- Retry vô hạn poison message.
- DLT không có owner, alert hoặc replay runbook.
- Dùng timestamp để đảm bảo ordering giữa service.
- Tăng consumer concurrency vượt số partition và DB capacity.
- Gửi entity/PII/secret trong message payload.
- Dual-write database và Kafka không có outbox.
- Mark outbox published trước khi Kafka send thành công.
- Không có retention/cleanup cho outbox và dedupe table.
- Rebalance do handler chạy quá lâu nhưng chỉ tăng poll interval.
- Replay DLT không kiểm tra duplicate side effect.
- Dùng Kafka/RabbitMQ cùng lúc khi chưa có lý do.
- Tách microservice trước khi modular monolith có boundary.

---

## 18. Bài thực hành bắt buộc

1. Chạy Kafka bằng Docker Compose cùng Task API.
2. Tạo topic `task.events.v1` với partition key là `taskId`.
3. Khi Task được assign, ghi outbox event trong cùng database transaction.
4. Viết publisher đọc outbox và gửi `TaskAssigned.v1`.
5. Viết notification consumer group gửi email/in-app notification.
6. Cấu hình at-least-once và manual/record commit phù hợp.
7. Thêm bảng processed message hoặc unique idempotency key.
8. Gửi trùng event và chứng minh notification không duplicate.
9. Làm provider lỗi transient rồi kiểm tra retry/backoff.
10. Làm payload invalid rồi kiểm tra DLT.
11. Restart consumer giữa lúc xử lý và kiểm tra offset/side effect.
12. Gửi event cho cùng task để kiểm tra ordering trong partition.
13. Đo consumer lag, processing duration, retry và DLT metrics.
14. Viết runbook replay DLT an toàn.
15. Mô tả cùng workflow nếu dùng RabbitMQ thay Kafka.

---

## 19. Câu hỏi tự kiểm tra

1. Command khác event ở intent và ownership thế nào?
2. Topic, partition và consumer group liên hệ ra sao?
3. Vì sao at-least-once thường dẫn tới duplicate?
4. Consumer idempotent cần những key/storage nào?
5. Khi nào retry và khi nào đưa DLT ngay?
6. Vì sao ordering chỉ đảm bảo trong một partition?
7. Chọn partition key theo invariant nào?
8. Outbox giải quyết dual-write như thế nào?
9. Vì sao publisher có thể gửi trùng sau crash?
10. Eventual consistency nên hiển thị với người dùng ra sao?
11. Choreography khác orchestration thế nào?
12. Compensation khác rollback database ra sao?
13. Kafka và RabbitMQ khác nhau ở replay/routing nào?
14. Consumer lag tăng nhưng có nên tăng concurrency ngay không?
15. DLT replay cần kiểm tra điều gì trước?

---

## 20. Tiêu chí hoàn thành tuần 19

- [ ] Có producer và consumer Kafka chạy được.
- [ ] Có topic/version/key/partition policy rõ.
- [ ] Consumer group và concurrency phù hợp partition.
- [ ] Delivery semantic được document là at-least-once.
- [ ] Consumer idempotent bằng dedupe/idempotency key.
- [ ] Retry có giới hạn và phân loại lỗi.
- [ ] Có DLT/DLQ, metric và replay runbook.
- [ ] Ordering của cùng aggregate được kiểm tra.
- [ ] Outbox bảo vệ database + message publish.
- [ ] Outbox publisher có lease/retry/recovery.
- [ ] Message schema versioned và không chứa secret/PII thừa.
- [ ] Có test broker thật hoặc Testcontainers.
- [ ] Đã test restart, rebalance, timeout và duplicate.
- [ ] Hiểu Saga choreography/orchestration ở mức thiết kế.
- [ ] Có lý do rõ ràng khi chọn Kafka thay RabbitMQ.

---

## 21. Gợi ý lịch học 7 ngày

| Ngày | Nội dung | Kết quả |
|---|---|---|
| 1 | Broker, topic/queue, command/event | Chọn messaging model |
| 2 | Kafka producer, topic, partition, key | Publish event |
| 3 | Consumer group, offset, ack, ordering | Consume đúng semantics |
| 4 | Retry, backoff, DLT/DLQ | Xử lý poison message |
| 5 | Outbox, dedupe, idempotency | Không mất/duplicate side effect |
| 6 | Eventual consistency, Saga, RabbitMQ mapping | Thiết kế distributed workflow |
| 7 | Testcontainers, failure drill, metrics | Hoàn thiện runbook |

Tuần 20 theo roadmap là capstone project: chọn E-commerce backend hoặc Project Management System và áp dụng authentication, PostgreSQL, Redis, messaging, observability, Docker và CI/CD.

## 22. Tài liệu tham khảo

- Spring for Apache Kafka reference: https://docs.spring.io/spring-kafka/reference/
- Spring Kafka error handling: https://docs.spring.io/spring-kafka/reference/kafka/annotation-error-handling.html
- Apache Kafka documentation: https://kafka.apache.org/documentation/
- RabbitMQ tutorials: https://www.rabbitmq.com/tutorials
- Spring Modulith events: https://docs.spring.io/spring-modulith/reference/events.html
- Transactional outbox pattern: https://microservices.io/patterns/data/transactional-outbox.html
- CloudEvents specification: https://cloudevents.io/
- Debezium outbox event router: https://debezium.io/documentation/reference/stable/transformations/outbox-event-router.html

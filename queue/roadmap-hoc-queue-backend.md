# Roadmap học Queue và Message Queue cho Backend trong 18 tuần

Tài liệu này hiểu Queue theo hướng **backend message queue và event streaming**, phù hợp với Java Spring Boot. Trọng tâm là cách hệ thống gửi, nhận, retry, bảo đảm và quan sát message trong môi trường phân tán.

Thời lượng đề xuất: **10–14 giờ/tuần** trong 18 tuần.

Ba lựa chọn được dùng để so sánh:

- **RabbitMQ**: broker theo mô hình exchange, queue, binding và routing.
- **Apache Kafka**: event streaming theo topic, partition, offset và consumer group.
- **AWS SQS**: managed queue theo mô hình Standard/FIFO, visibility timeout và DLQ.

Học RabbitMQ trước để hiểu queue và delivery, sau đó học Kafka để hiểu event log, partition và scale.

## Điều kiện đầu vào

- Java/Spring Boot cơ bản.
- REST API và HTTP.
- SQL transaction cơ bản.
- Docker, Git, JSON và serialization.
- Exception handling và logging.

## Mục tiêu cuối lộ trình

- Phân biệt in-memory queue, message queue và event streaming.
- Hiểu producer, consumer, broker, queue, topic, partition và offset.
- Chọn RabbitMQ, Kafka hay SQS theo yêu cầu hệ thống.
- Thiết kế message contract có version.
- Xử lý acknowledgement, retry, timeout và dead-letter.
- Xây consumer idempotent, chịu được duplicate message.
- Hiểu at-most-once, at-least-once và giới hạn của exactly-once.
- Dùng Outbox Pattern và Inbox Pattern.
- Theo dõi lag, throughput, failure rate và queue depth.
- Test message flow bằng integration test và Testcontainers.

## Môi trường lab

~~~yaml
services:
  rabbitmq:
    image: rabbitmq:4-management
    ports:
      - "5672:5672"
      - "15672:15672"
  kafka:
    image: apache/kafka:latest
    ports:
      - "9092:9092"
~~~

Trong project thật nên pin version cụ thể, không dùng latest lâu dài.

---

# Giai đoạn 1 — Nền tảng messaging

## Tuần 1: Queue, broker và asynchronous processing

### Học

- Synchronous request/response và asynchronous processing.
- Queue-based load leveling.
- Producer, consumer và broker.
- Message, payload, metadata và header.
- Queue, topic và subscription.
- Work queue, competing consumers và pub/sub.
- Request/reply, command message và event message.
- Event notification.
- Backpressure.

### Bài tập

Vẽ và so sánh:

1. API gọi email service đồng bộ.
2. API đẩy email job vào queue.
3. Order service phát event cho nhiều consumer.

Với mỗi kiến trúc, ghi thời điểm trả response, điều gì xảy ra khi consumer chết và message có bị mất hay không.

### Tiêu chí hoàn thành

- Giải thích được queue không chỉ là List trong memory.
- Biết asynchronous không tự động đồng nghĩa với reliable.
- Biết queue giúp tách producer và consumer.

## Tuần 2: Delivery semantics và failure model

### Học

- At-most-once.
- At-least-once.
- Exactly-once processing và exactly-once effect.
- Duplicate delivery.
- Lost message.
- Poison message.
- Consumer crash.
- Broker crash.
- Network timeout.
- Partial failure.
- Retry storm.

### Bài tập

| Sự cố | Hậu quả | Cách giảm rủi ro |
|---|---|---|
| Consumer crash trước ack | Message có thể redeliver | Idempotent consumer |
| Broker mất kết nối | Producer không biết message đã đến chưa | Publisher confirm |
| Handler lỗi vĩnh viễn | Retry vô hạn | Dead-letter queue |
| Consumer chậm | Queue tăng | Scale hoặc backpressure |

### Nguyên tắc

Mục tiêu thực tế thường là **at-least-once delivery + idempotent processing**, không phải hứa hẹn exactly-once tuyệt đối.

---

# Giai đoạn 2 — RabbitMQ

## Tuần 3: AMQP model và RabbitMQ căn bản

### Học

- Connection và channel.
- Virtual host.
- Exchange, queue, binding và routing key.
- Direct, fanout và topic exchange.
- Durable queue.
- Persistent message.
- Exclusive và auto-delete queue.

Producer thường gửi message tới exchange; consumer nhận message từ queue thông qua binding.

### Thực hành

Tạo topology:

~~~text
order.events exchange
├── order.email queue
├── order.analytics queue
└── order.audit queue
~~~

Routing key:

~~~text
order.created
order.paid
order.cancelled
~~~

### Tiêu chí hoàn thành

- Dự đoán được queue nào nhận message theo exchange và routing key.
- Phân biệt durable queue với message đã persist.
- Phân biệt connection với channel.

## Tuần 4: RabbitMQ với Spring AMQP

### Học

- RabbitTemplate.
- RabbitListener.
- Queue, Exchange, Binding bean.
- Message converter.
- JSON serialization.
- Listener container.
- Manual và automatic acknowledgement.
- Prefetch.
- Consumer concurrency.
- Graceful shutdown.

### Thực hành

Xây Spring Boot app:

- POST /orders tạo order.
- Producer phát OrderCreated.
- Consumer gửi notification.
- Consumer khác ghi audit log.

Message envelope nên có eventId, eventType, eventVersion, occurredAt, aggregateId và payload.

### Tiêu chí hoàn thành

- Chạy được một producer và hai consumer.
- Biết mỗi consumer có thể thuộc consumer flow riêng.
- Không ack trước khi xử lý xong.

## Tuần 5: RabbitMQ reliability

### Học

- Consumer acknowledgement.
- Basic ack, nack và reject.
- Requeue.
- Publisher confirms.
- Mandatory publish.
- Durable exchange và queue.
- Persistent delivery.
- Dead Letter Exchange và Dead Letter Queue.
- Message TTL và queue TTL.
- Retry bằng delayed queue.
- Quorum queue ở mức khái niệm.

RabbitMQ phân biệt consumer acknowledgement và publisher confirm; hai cơ chế này bảo vệ hai chiều khác nhau của việc truyền message. [RabbitMQ confirms](https://www.rabbitmq.com/docs/confirms)

### Thực hành

Thiết kế flow:

~~~text
main queue
   ↓ lỗi tạm thời
retry queue 10s
   ↓
retry queue 60s
   ↓ lỗi vĩnh viễn
dead-letter queue
~~~

### Quy tắc

- Không requeue vô hạn.
- Phân biệt lỗi tạm thời và lỗi dữ liệu.
- Lưu retry count trong header hoặc metadata.
- Có correlation ID.
- Ack sau khi xử lý thành công.

## Tuần 6: RabbitMQ scale và vận hành

### Học

- Competing consumers.
- Prefetch tuning.
- Consumer concurrency.
- Fair dispatch.
- Ordering limitation.
- Queue depth.
- Unacked messages.
- Connection/channel limit.
- Quorum queue.
- Cluster và node failure ở mức khái niệm.

### Lab

- Chạy 1, 3 và 10 consumers.
- Đo throughput và latency.
- Thay đổi prefetch.
- Kill một consumer giữa lúc xử lý.
- Tạo message xử lý lâu.
- Quan sát ready và unacked messages.

### Deliverable

Báo cáo benchmark gồm message size, producer rate, consumer count, prefetch, throughput, p95 latency, redelivery và queue depth.

---

# Giai đoạn 3 — AWS SQS

## Tuần 7: SQS architecture và queue types

### Học

- AWS account, Region và ARN.
- SQS Standard Queue.
- SQS FIFO Queue.
- SendMessage, ReceiveMessage và DeleteMessage.
- Visibility timeout.
- Message retention.
- Delivery delay.
- Short polling và long polling.
- Batch send/receive/delete.
- Message size và giới hạn API.
- At-least-once delivery của Standard Queue.
- MessageGroupId và ordering của FIFO Queue.
- MessageDeduplicationId và content-based deduplication.

Standard Queue ưu tiên throughput và có thể giao message nhiều lần hoặc không đúng thứ tự. FIFO phù hợp khi ordering và duplicate control quan trọng. [SQS standard queues](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/standard-queues.html) · [SQS FIFO queues](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-fifo-queues.html)

### Thực hành

- Tạo một Standard Queue và một FIFO Queue trong AWS Console.
- Gửi/nhận message bằng AWS CLI hoặc AWS SDK for Java.
- Dùng batch API để gửi và xóa message.
- Đo khác biệt giữa short polling và long polling.
- Tạo nhiều MessageGroupId để xử lý FIFO song song theo group.

### Tiêu chí hoàn thành

- Giải thích được khi nào chọn Standard và khi nào chọn FIFO.
- Biết vì sao FIFO không có global parallelism nếu mọi message dùng cùng một group.
- Biết delete message là bước xác nhận xử lý thành công.

## Tuần 8: SQS reliability, security và Spring Cloud AWS

### Học

- Visibility timeout phù hợp với thời gian xử lý.
- ChangeMessageVisibility cho job dài.
- Duplicate delivery và idempotent consumer.
- Dead-Letter Queue.
- Redrive policy và maximum receives.
- DLQ phải cùng loại Standard hoặc FIFO với source queue.
- IAM identity policy và resource policy.
- Server-side encryption với SQS-managed key hoặc KMS.
- CloudWatch metrics.
- Queue depth và age of oldest message.
- Long polling tối đa 20 giây.
- Delay queue và message timer.
- Chi phí theo API request.

Visibility timeout mặc định là 30 giây và có thể điều chỉnh; nếu consumer không delete trước khi timeout kết thúc, message có thể xuất hiện lại. [Visibility timeout](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-visibility-timeout.html)

### Thực hành với Spring

- Dùng Spring Cloud AWS để gửi và nhận SQS message.
- Cấu hình listener container và concurrency.
- Serialize event envelope thành JSON.
- Retry lỗi tạm thời có giới hạn.
- Chuyển lỗi vĩnh viễn sang DLQ.
- Gắn correlation ID và idempotency key.
- Dùng IAM role thay vì access key hardcode.

### Lab production-like

~~~text
Order API
   ↓
orders-standard
   ↓ lỗi nhiều lần
orders-dlq

payment.fifo
   ↓ MessageGroupId = orderId
payment-dlq.fifo
~~~

### Tiêu chí hoàn thành

- Consumer không làm mất message khi process crash.
- Consumer không tạo duplicate side effect khi message redeliver.
- CloudWatch alarm khi queue depth hoặc message age tăng.
- Không có secret trong source code hoặc queue name.

---

# Giai đoạn 4 — Apache Kafka

## Tuần 9: Kafka architecture

### Học

- Broker và cluster.
- Topic và partition.
- Record và offset.
- Leader và follower.
- Replication factor.
- Producer và consumer.
- Consumer group.
- Log retention.
- Log compaction.
- Key và partition assignment.

### So sánh RabbitMQ và Kafka

| RabbitMQ | Kafka |
|---|---|
| Message thường biến mất sau ack | Record giữ theo retention |
| Queue phân phối work | Topic/partition là event log |
| Routing bằng exchange/binding | Routing theo topic, key và partition |
| Consumer nhận delivery | Consumer quản lý offset |
| Scale bằng competing consumers | Scale bằng partition và consumer group |

### Tiêu chí hoàn thành

- Biết partition là đơn vị parallelism của consumer group.
- Biết số consumer hoạt động song song không thể vượt số partition trong một group.
- Biết key ảnh hưởng partition và ordering.

## Tuần 10: Kafka producer, consumer và Spring Kafka

### Học

- Producer key/value serializer.
- Consumer deserializer.
- Consumer group.
- Offset commit.
- Auto commit và manual commit.
- Poll loop.
- KafkaTemplate.
- KafkaListener.
- Consumer concurrency.
- Error handler.
- Seek và replay.

Spring for Apache Kafka cung cấp template gửi message, listener container, transaction, monitoring và xử lý exception. [Spring Kafka Reference](https://docs.spring.io/spring-kafka/reference/)

### Thực hành

~~~text
order-events topic: 6 partitions
order-indexer group
order-notification group
order-analytics group
~~~

Kiểm tra hai consumer cùng group chia partition, hai group khác nhau đều nhận event và reset offset để replay.

## Tuần 11: Kafka delivery, ordering và retry

### Học

- Commit trước khi xử lý.
- Commit sau khi xử lý.
- At-least-once consumer.
- Duplicate record.
- Ordering trong một partition.
- Global ordering giữa nhiều partition.
- Retry topic.
- Dead-letter topic.
- Non-blocking retry.
- Poison record.
- Pause/resume partition.

### Thực hành

~~~text
orders.v1
   ↓ lỗi tạm thời
orders.retry.10s
   ↓
orders.retry.1m
   ↓ lỗi vĩnh viễn
orders.dlt
~~~

Không retry lỗi schema hoặc validation vô hạn.

## Tuần 12: Kafka performance và durability

### Học

- Batch size.
- Linger.
- Compression.
- Acks.
- Idempotent producer.
- Producer retries.
- In-flight requests.
- Fetch size.
- Max poll records.
- Max poll interval.
- Consumer lag.
- Retention theo time và size.
- Log compaction.
- Replication factor.
- Min in-sync replicas.

### Lab

- Đo throughput với compression khác nhau.
- Tắt/bật idempotent producer.
- Tạo consumer xử lý chậm.
- Theo dõi lag.
- Thử retention ngắn trong lab.
- So sánh event topic và compacted topic.

---

# Giai đoạn 5 — Message contract và consistency

## Tuần 13: Event schema và compatibility

### Học

- Event envelope.
- Command, event và notification.
- Schema evolution.
- Backward compatibility.
- Forward compatibility.
- Optional field và default value.
- Enum evolution.
- JSON Schema.
- Avro/Protobuf ở mức khái niệm.
- Schema Registry.
- Event version.
- Correlation ID và causation ID.

### Quy tắc contract

- Không đổi ý nghĩa field cũ.
- Thêm field mới theo hướng backward-compatible.
- Không tái sử dụng event name cho semantics khác.
- Có owner cho mỗi event.
- Có retention và privacy classification.
- Không đưa password, token hoặc dữ liệu nhạy cảm không cần thiết vào event.

### Bài tập

Thiết kế OrderCreated v1 và v2. Viết consumer cũ vẫn đọc được event mới, sau đó document migration plan.

## Tuần 14: Outbox, Inbox và distributed consistency

### Học

- Dual-write problem.
- Outbox Pattern.
- Polling publisher.
- CDC ở mức khái niệm.
- Inbox Pattern.
- Idempotency key.
- Deduplication table.
- Transaction boundary.
- Saga choreography.
- Saga orchestration.
- Compensating action.

### Outbox flow

~~~text
Database transaction:
  update orders
  insert outbox_events
commit

Publisher:
  đọc outbox_events chưa publish
  publish message
  đánh dấu published_at
~~~

### Lab

- Tạo order và outbox event trong cùng transaction.
- Giết service sau commit trước khi publish.
- Chạy publisher lại.
- Đảm bảo event không mất.
- Consumer dùng eventId để chống xử lý trùng.

---

# Giai đoạn 6 — Production readiness

## Tuần 15: Retry, timeout, idempotency và DLQ operations

### Học

- Retry budget.
- Exponential backoff.
- Jitter.
- Timeout theo dependency.
- Circuit breaker cho downstream.
- Rate limit producer.
- Backpressure.
- Dead-letter triage.
- Replay có kiểm soát.
- Poison message quarantine.

### Runbook DLQ

1. Đếm message theo nguyên nhân.
2. Lấy mẫu payload an toàn.
3. Phân loại lỗi code, dữ liệu hoặc dependency.
4. Sửa lỗi hoặc migrate message.
5. Replay một batch nhỏ.
6. Theo dõi duplicate và side effect.
7. Đóng incident bằng postmortem.

Không replay toàn bộ DLQ vào production mà không giới hạn tốc độ và kiểm tra idempotency.

## Tuần 16: Observability cho queue

### Học

- Queue depth.
- Consumer lag.
- Oldest message age.
- Throughput.
- Publish rate.
- Ack rate.
- Redelivery rate.
- Error rate.
- Retry count.
- DLQ count.
- Processing latency p50/p95/p99.
- Correlation ID.
- Trace propagation.
- Structured logging.

### Dashboard tối thiểu

RabbitMQ:

- Ready messages.
- Unacked messages.
- Consumer count.
- Publish/ack rate.
- Redelivery.
- Connection/channel count.

Kafka:

- Consumer lag theo group/partition.
- Bytes in/out.
- Records in/out.
- Under-replicated partitions.
- Offline partitions.
- Request latency.

## Tuần 17: Testing và chaos

### Học

- Unit test producer/consumer.
- Contract test.
- Integration test với Testcontainers.
- Serialization test.
- Retry test.
- DLQ test.
- Idempotency test.
- Ordering test.
- Consumer restart test.
- Broker unavailable test.
- Duplicate delivery test.

### Scenario bắt buộc

- Consumer chết trước ack.
- Consumer xử lý thành công nhưng crash trước commit offset.
- Broker mất kết nối.
- Message sai schema.
- Downstream timeout.
- Consumer chạy chậm hơn producer.
- Một partition bị hot.
- Duplicate event đến hai lần.

### Tiêu chí hoàn thành

- Test chứng minh duplicate không tạo duplicate side effect.
- Test chứng minh lỗi vĩnh viễn đi vào DLQ/DLT.
- Test chứng minh retry có giới hạn.
- Test có thể dựng broker bằng một lệnh.

## Tuần 18: Capstone và lựa chọn kiến trúc

### Capstone: Order Processing Platform

~~~text
Order API
  ↓
Order service
  ↓ outbox
RabbitMQ, Kafka hoặc SQS
  ├── Payment worker
  ├── Inventory worker
  ├── Notification worker
  └── Analytics consumer
~~~

### Chức năng

- Tạo order.
- Phát OrderCreated.
- Trừ inventory.
- Xử lý payment.
- Phát OrderPaid hoặc OrderFailed.
- Gửi notification.
- Retry lỗi tạm thời.
- Đưa lỗi vĩnh viễn vào DLQ/DLT.
- Replay event hoặc redrive message an toàn.
- Theo dõi lag và queue depth.

### Deliverables

- Architecture diagram.
- Event catalog.
- Message schema và versioning policy.
- Producer/consumer code.
- Outbox table và publisher.
- Idempotency strategy.
- Retry/DLQ policy.
- Integration test.
- Dashboard.
- Docker Compose.
- README.
- Incident runbook.

---

# Cách chọn RabbitMQ, Kafka hay SQS

Chọn RabbitMQ khi:

- Cần work queue cho task.
- Cần routing linh hoạt bằng exchange.
- Cần request/reply hoặc command delivery.
- Message thường được xử lý rồi xóa.
- Hệ thống vừa và cần triển khai nhanh.

Chọn Kafka khi:

- Cần event log có thể replay.
- Nhiều consumer độc lập cùng đọc event.
- Throughput lớn.
- Cần partition và scale theo key.
- Cần retention hoặc compaction.
- Xây data pipeline hoặc event-driven platform.

Chọn AWS SQS khi:

- Muốn queue managed, không tự vận hành broker.
- Hệ thống chạy chủ yếu trên AWS.
- Cần tích hợp tự nhiên với Lambda, ECS, EKS hoặc EventBridge.
- Cần Standard Queue throughput cao và vận hành đơn giản.
- Cần FIFO cho ordering theo MessageGroupId.
- Chấp nhận mô hình poll/delete và giới hạn retention của SQS.

SQS không thay thế Kafka trong bài toán cần replay dài hạn, nhiều consumer group độc lập hoặc event log lớn. SQS cũng không có exchange/routing model như RabbitMQ.

Không chọn công nghệ chỉ vì phổ biến. Hãy dựa trên ordering, throughput, retention, replay, delivery guarantee, vận hành và năng lực team.

---

# Checklist sau 18 tuần

## Kiến thức nền

- Phân biệt queue, topic, partition và consumer group.
- Giải thích at-most-once, at-least-once và exactly-once effect.
- Biết vì sao duplicate message là bình thường.

## Reliability

- Publisher có confirm hoặc cơ chế kiểm tra kết quả.
- Consumer ack/commit sau xử lý phù hợp.
- Có retry giới hạn và DLQ/DLT.
- Có idempotency.
- Có timeout và backpressure.
- Biết cấu hình visibility timeout và max receives cho SQS.

## Scale

- Biết tăng consumer mà không phá ordering.
- Biết chọn partition key.
- Biết chỉnh prefetch hoặc max poll records.
- Theo dõi được queue depth và consumer lag.
- Biết dùng long polling và batch API của SQS.

## Spring Boot

- Dùng được Spring AMQP, Spring Kafka hoặc Spring Cloud AWS SQS.
- Cấu hình listener container.
- Viết error handler.
- Test với Testcontainers.
- Propagate correlation ID.

## Production

- Có schema/version policy.
- Có dashboard.
- Có replay runbook.
- Có incident runbook.
- Có security policy cho broker.
- Với SQS, có IAM policy, KMS policy và CloudWatch alarm.
- Không log payload nhạy cảm.

---

# Nguồn học chính thức

- [RabbitMQ Tutorials](https://www.rabbitmq.com/tutorials)
- [RabbitMQ Consumer Acknowledgements and Publisher Confirms](https://www.rabbitmq.com/docs/confirms)
- [RabbitMQ Work Queues](https://www.rabbitmq.com/tutorials/tutorial-two-java)
- [Apache Kafka Documentation](https://kafka.apache.org/documentation/)
- [Spring AMQP Reference](https://docs.spring.io/spring-amqp/reference/)
- [Spring AMQP abstractions](https://docs.spring.io/spring-amqp/reference/amqp/abstractions.html)
- [Spring for Apache Kafka Reference](https://docs.spring.io/spring-kafka/reference/)
- [Spring Kafka error handling](https://docs.spring.io/spring-kafka/reference/kafka/annotation-error-handling.html)
- [Amazon SQS standard queues](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/standard-queues.html)
- [Amazon SQS FIFO queues](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-fifo-queues.html)
- [Amazon SQS visibility timeout](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-visibility-timeout.html)
- [Amazon SQS long polling](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/best-practices-setting-up-long-polling.html)
- [Amazon SQS dead-letter queues](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-configure-dead-letter-queue.html)
- [Spring Cloud AWS SQS integration](https://docs.awspring.io/spring-cloud-aws/docs/current/reference/html/index.html)

Queue production-ready không chỉ là gửi và nhận message. Bạn phải thiết kế được contract, xử lý duplicate, đo lag, kiểm soát retry, bảo vệ side effect và có khả năng replay hoặc khôi phục khi hệ thống phân tán gặp lỗi.

# Roadmap học RabbitMQ từ cơ bản đến production

> Lộ trình 16 tuần, khoảng 10–14 giờ mỗi tuần. Tập trung vào RabbitMQ 4.x, AMQP 0-9-1 và Spring AMQP trong Spring Boot. Mỗi tuần đều có kiến thức, bài lab và tiêu chí hoàn thành.

## 1. Mục tiêu đầu ra

Sau lộ trình, bạn có thể:

- Giải thích đúng mô hình AMQP: connection, channel, virtual host, exchange, queue, binding, routing key và consumer.
- Thiết kế topology RabbitMQ cho command, event, work queue, fan-out, routing, topic và request/reply.
- Viết producer/consumer bằng Java client và Spring Boot/Spring AMQP.
- Xử lý manual acknowledgement, negative acknowledgement, redelivery, prefetch và competing consumers.
- Đảm bảo publisher bằng publisher confirms, mandatory publish và return callback.
- Thiết kế retry, dead-letter exchange, dead-letter queue, TTL và poison-message handling.
- Xây dựng consumer idempotent, transactional outbox/inbox và xử lý bài toán database–broker dual write.
- Tối ưu throughput/latency qua prefetch, concurrency, batching, message size và connection/channel.
- Vận hành RabbitMQ: metrics, alerting, capacity planning, backup definitions, failure drill và runbook.
- Cấu hình security: vhost, permission, user tag, TLS, secret và network policy.
- Phân biệt classic queue và quorum queue, biết khi nào cần high availability.
- Viết unit test, integration test bằng Testcontainers, contract test và chaos/load test.
- Hoàn thiện một hệ thống xử lý đơn hàng có retry, DLQ, outbox, idempotency, metrics và tài liệu vận hành.

## 2. Phạm vi và điều kiện đầu vào

### Nên biết trước

- Java cơ bản đủ dùng: class, interface, exception, collection, thread và HTTP.
- Spring Boot căn bản: dependency injection, configuration, REST controller và JPA transaction.
- SQL/PostgreSQL: transaction, unique constraint và index.
- Docker, Docker Compose, Git và một công cụ gọi API.
- JSON, HTTP status code và khái niệm distributed system ở mức nhập môn.

### Không cần học trước

- Không cần biết Kafka hay một message broker khác.
- Không cần học Erlang để sử dụng RabbitMQ; chỉ cần hiểu một phần khi đọc log và troubleshooting.

### Nguyên tắc học

1. Mỗi khái niệm phải có một lab nhỏ chạy được.
2. Mỗi lab phải quan sát được qua Management UI, log hoặc metric.
3. Luôn kiểm tra cả trường hợp thành công, timeout, consumer crash và broker restart.
4. Viết README cho từng lab: topology, message flow, lệnh chạy và lỗi đã thử.
5. Không mặc định rằng message chỉ giao một lần; hãy thiết kế theo at-least-once và idempotency.

## 3. Môi trường thực hành

### Công cụ

- JDK 21 hoặc phiên bản LTS mà dự án đang dùng.
- Maven hoặc Gradle.
- Docker Engine và Docker Compose.
- RabbitMQ 4.x có Management UI.
- Spring Boot 3.x và Spring AMQP tương thích với version Spring Boot.
- IntelliJ IDEA/VS Code, curl hoặc Postman, Git.
- Testcontainers cho integration test.

### RabbitMQ local bằng Docker Compose

~~~yaml
services:
  rabbitmq:
    image: rabbitmq:4-management
    hostname: rabbitmq
    ports:
      - "5672:5672"
      - "15672:15672"
    environment:
      RABBITMQ_DEFAULT_USER: dev
      RABBITMQ_DEFAULT_PASS: dev-password
      RABBITMQ_DEFAULT_VHOST: /app
    volumes:
      - rabbitmq-data:/var/lib/rabbitmq

volumes:
  rabbitmq-data:
~~~

Đăng nhập Management UI tại localhost:15672. Trong môi trường thật phải pin image theo version cụ thể, dùng secret manager và không sử dụng tài khoản mặc định.

### Cấu trúc repository đề xuất

~~~text
rabbitmq-learning/
├── week-01-amqp/
├── week-02-topology/
├── week-03-java-client/
├── spring-demo/
├── reliability-lab/
├── outbox-demo/
├── observability/
├── chaos-lab/
└── capstone-order-platform/
~~~

## 4. Bản đồ kiến thức RabbitMQ

| Thành phần | Vai trò | Câu hỏi cần trả lời |
|---|---|---|
| Producer | Tạo và publish message | Publish vào exchange nào? Có confirm chưa? |
| Exchange | Nhận message và route | Direct, fanout, topic hay headers? |
| Binding | Luật nối exchange với queue | Routing key/pattern là gì? |
| Queue | Lưu message chờ consumer | Durable, exclusive, auto-delete hay quorum? |
| Consumer | Nhận và xử lý message | Ack lúc nào? Lỗi thì retry hay DLQ? |
| Connection | Kết nối TCP tới broker | Có recovery và heartbeat không? |
| Channel | Kênh logic trên connection | Có tạo một channel cho mỗi message không? |
| Virtual host | Namespace và boundary quyền | Service nào được đọc/ghi vhost nào? |
| Publisher confirm | Broker xác nhận publish | Message đã được broker accept chưa? |
| Consumer acknowledgement | Consumer xác nhận xử lý | Có ack trước khi ghi DB không? |
| DLX/DLQ | Đường xử lý message lỗi | Retry hữu hạn và quan sát được chưa? |
| Quorum queue | Queue replicated cho HA | Có cần durability/availability cao không? |

## 5. Lộ trình 16 tuần

## Tuần 1 — Messaging và tư duy bất đồng bộ

### Học

- Synchronous request/response và asynchronous messaging.
- Queue, broker, producer, consumer, worker, event và command.
- Khi nào dùng message queue: decouple, buffering, load leveling, retry và fan-out.
- Delivery semantics: at-most-once, at-least-once, effectively-once và vì sao exactly-once khó.
- Ordering, duplication, backpressure, timeout, eventual consistency.
- Phân biệt event “đã xảy ra” và command “hãy làm việc này”.

### Thực hành

1. Vẽ flow synchronous và asynchronous cho việc gửi email sau khi tạo đơn.
2. Viết một producer và consumer giả lập bằng Java BlockingQueue.
3. Mô phỏng consumer xử lý chậm, bị crash và chạy lại.
4. Ghi lại các điểm có thể tạo duplicate.

### Tiêu chí hoàn thành

- Giải thích được vì sao queue không tự làm cho hệ thống exactly-once.
- Chọn được message hay REST cho năm tình huống cụ thể.
- Viết được một tài liệu có sequence diagram cho flow đặt hàng.

## Tuần 2 — AMQP 0-9-1 và RabbitMQ topology

### Học

- Connection, channel, virtual host và lifecycle.
- Exchange, queue, binding, routing key.
- Direct exchange: route theo routing key chính xác.
- Fanout exchange: broadcast tới mọi queue đã bind.
- Topic exchange: wildcard theo từ và dấu chấm.
- Headers exchange: route theo header, chỉ học để nhận biết trade-off.
- Durable entity, auto-delete, exclusive queue và message persistence.
- Server-named queue cho subscription tạm thời.

### Bài lab

1. Tạo vhost riêng cho ứng dụng.
2. Tạo một direct exchange với ba queue cho payment, inventory và notification.
3. Tạo fanout exchange để broadcast OrderCreated.
4. Tạo topic exchange với các key order.created, order.paid và order.*.
5. Xóa/bind lại queue rồi quan sát message route thế nào.

### Deliverable

- Sơ đồ topology có tên exchange, queue, binding và routing key.
- Bảng quy ước tên, ví dụ: domain.event.version và domain.command.
- Một file README mô tả vì sao mỗi exchange type được chọn.

## Tuần 3 — RabbitMQ CLI, Management UI và Java client

### Học

- Quan sát overview, connections, channels, exchanges, queues, consumers và bindings.
- Các nhóm lệnh rabbitmqctl, rabbitmq-diagnostics và rabbitmq-plugins.
- Publish/consume message từ UI hoặc CLI để cô lập lỗi ứng dụng.
- Java RabbitMQ client: ConnectionFactory, Connection, Channel.
- Declare exchange/queue/binding, basic.publish và basic.consume.
- Heartbeat, connection timeout, automatic recovery và topology recovery.

### Bài lab

1. Viết Java producer publish 100 message có routing key khác nhau.
2. Viết consumer log delivery tag, redelivered flag và body.
3. Đóng consumer giữa chừng rồi kiểm tra unacked/ready.
4. Restart container và kiểm tra entity durable có còn không.
5. Dùng UI để tạo binding, sau đó export lại topology bằng code.

### Tiêu chí hoàn thành

- Có thể trả lời “message đang nằm ở queue nào và vì sao”.
- Biết phân biệt connection count, channel count và consumer count.
- Không mở connection mới cho từng message.

## Tuần 4 — Spring AMQP căn bản

### Học

- RabbitTemplate, AmqpAdmin và MessageListenerContainer.
- @RabbitListener, @RabbitHandler và listener container factory.
- Khai báo Queue, Exchange, Binding bằng Java configuration.
- Jackson JSON message converter, content type và header.
- application.yaml: host, port, username, password, virtual host.
- DTO message riêng với DTO REST; không serialize trực tiếp entity JPA.
- Correlation ID và custom message headers.

### Bài lab

1. Tạo Spring Boot producer publish OrderCreated.
2. Tạo consumer bằng @RabbitListener.
3. Cấu hình object mapper và kiểm tra JSON thực tế trên UI.
4. Tạo hai listener container với concurrency khác nhau.
5. Viết test kiểm tra topology được declare khi app khởi động.

### Tiêu chí hoàn thành

- Gửi được object JSON và consumer deserialize ổn định.
- Biết message converter lỗi sẽ đi qua error handler nào.
- Tách cấu hình RabbitMQ khỏi business service.

## Tuần 5 — Work queues và competing consumers

### Học

- Một queue với nhiều consumer.
- Fair dispatch và prefetch.
- Manual ack so với auto ack.
- Delivery tag, multiple ack, reject và nack.
- Consumer concurrency, consumer container và graceful shutdown.
- Ordering chỉ được đảm bảo trong giới hạn một queue/consumer sequence; concurrency có thể làm thứ tự hoàn tất khác nhau.

### Bài lab

1. Tạo worker xử lý thumbnail hoặc email.
2. Chạy 3 consumer có tốc độ khác nhau.
3. So sánh prefetch 1, 10 và 100.
4. Crash worker trước và sau khi xử lý.
5. Đo queue depth, processing time và redelivery.

### Tiêu chí hoàn thành

- Giải thích được vì sao prefetch quá lớn gây unfairness và memory pressure.
- Ack chỉ sau khi công việc thực sự hoàn tất.
- Có graceful shutdown để không nhận thêm message khi deploy.

## Tuần 6 — Publish/Subscribe, routing, topics và RPC

### Học

- Pub/sub với fanout.
- Routing theo direct exchange.
- Topic pattern với * và #.
- Request/reply, reply queue, correlation ID và timeout.
- Khi nào dùng event notification, khi nào dùng command.
- Vì sao RPC qua broker không nên thay thế mọi REST call.

### Bài lab

1. Dựng hệ thống audit, notification và analytics cùng nghe OrderCreated.
2. Dựng routing cho log.info, log.warn và log.error.
3. Dựng topic cho order.created.eu và order.created.vn.
4. Viết RPC tính phí vận chuyển có timeout và lỗi downstream.

### Tiêu chí hoàn thành

- Chọn đúng exchange type cho ít nhất 10 use case.
- RPC có timeout, correlation ID và xử lý reply thiếu.
- Không tạo một queue dùng chung cho mọi loại event không liên quan.

## Tuần 7 — Acknowledgement và publisher confirms

### Học

- Consumer ack: basic.ack, basic.nack, basic.reject.
- Requeue và nguy cơ vòng lặp lỗi vô hạn.
- Redelivered flag không phải là retry counter hoàn hảo.
- Publisher confirms: confirm từng message, batch và asynchronous confirm.
- Publisher return với mandatory publish khi exchange không route được message.
- Durable exchange/queue, persistent delivery mode và giới hạn của persistence.
- Connection recovery không đồng nghĩa business operation đã thành công.

### Ma trận quyết định

| Tình huống | Producer | Consumer |
|---|---|---|
| Publish không quan trọng | Có thể không confirm | Ack sau xử lý |
| Event quan trọng | Publisher confirm + return callback | Manual ack |
| Lỗi tạm thời | Confirm publish | Nack/retry có giới hạn |
| Lỗi dữ liệu | Confirm publish | Không requeue vô hạn, đưa DLQ |
| Không route được | Mandatory + return handler | Không có consumer để ack |

### Bài lab

1. Tắt broker ngay sau publish và quan sát confirm.
2. Publish routing key không có binding và bắt return callback.
3. Ném exception trước ack, sau đó kiểm tra redelivery.
4. Viết metric cho confirm latency và unconfirmed count.

### Tiêu chí hoàn thành

- Vẽ được lifecycle từ publish đến xử lý thành công.
- Biết lúc nào message có thể mất do producer không dùng confirm.
- Không ack trước khi side effect quan trọng hoàn tất.

## Tuần 8 — Retry, TTL, DLX và DLQ

### Học

- Phân loại lỗi transient, permanent và poison message.
- Dead-letter exchange, dead-letter routing key và dead-letter queue.
- TTL cho message và queue; hiểu rằng TTL không phải lúc nào cũng là delayed-message scheduler chính xác.
- Retry queue theo các mức 5s, 30s, 5m.
- Exponential backoff, jitter và giới hạn số lần retry.
- Nack/requeue trực tiếp so với republish vào retry exchange.
- Error payload, original routing key, exception class và retry count.
- DLQ phải được monitor và có quy trình replay có kiểm soát.

### Topology gợi ý

~~~text
orders.exchange
  └── orders.process.queue
        ├── lỗi tạm thời → orders.retry.5s.queue
        ├── lỗi tạm thời → orders.retry.30s.queue
        └── lỗi không thể xử lý → orders.dlq
~~~

### Bài lab

1. Tạo retry 3 tầng bằng TTL và DLX.
2. Chặn retry vô hạn bằng header x-retry-count.
3. Đưa JSON lỗi vào DLQ cùng metadata gốc.
4. Viết endpoint/admin command replay từng message hoặc theo batch.
5. Test poison message không làm queue bị nóng liên tục.

### Tiêu chí hoàn thành

- Có thể trace message từ queue chính qua retry tới DLQ.
- Phân biệt retry do broker và retry do application.
- DLQ replay không tạo duplicate nguy hiểm hoặc vòng lặp vô hạn.

## Tuần 9 — Message contract và schema evolution

### Học

- Envelope và payload.
- eventId, eventType, eventVersion, occurredAt.
- correlationId, causationId, traceparent.
- Producer/consumer ownership của contract.
- Backward compatibility, additive change, deprecated field.
- JSON naming, timezone, precision, enum và nullability.
- PII, secret, token và dữ liệu không nên đưa vào message.
- Schema registry là khái niệm cần biết khi hệ thống lớn; RabbitMQ không tự quản lý schema compatibility.

### Envelope khuyến nghị

~~~json
{
  "eventId": "uuid",
  "eventType": "order.created",
  "eventVersion": 1,
  "occurredAt": "2026-10-02T10:00:00Z",
  "correlationId": "uuid",
  "causationId": "uuid",
  "producer": "order-service",
  "payload": {
    "orderId": "ORD-1001",
    "customerId": "CUS-10",
    "total": 125000
  }
}
~~~

### Bài lab

1. Thêm field optional vào version 1 mà consumer cũ vẫn chạy.
2. Tạo version 2 cho event và viết adapter.
3. Kiểm tra malformed JSON, missing field và enum mới.
4. Gắn correlation ID vào log ở producer và consumer.

### Tiêu chí hoàn thành

- Có catalog các event/command và owner.
- Consumer không phụ thuộc vào field không cần thiết.
- Contract test bắt breaking change trước khi deploy.

## Tuần 10 — Idempotency, consistency và transactional outbox

### Học

- Vì sao at-least-once có thể tạo duplicate.
- Idempotent consumer bằng bảng inbox/deduplication.
- Unique constraint trên eventId hoặc business key.
- Ack order: ghi DB thành công rồi mới ack.
- Database transaction không bao trùm được publish broker một cách tự động.
- Dual-write problem.
- Transactional outbox: ghi business data và outbox record trong cùng DB transaction, worker publish outbox.
- Inbox pattern và xử lý event đã thấy.
- Saga/choreography ở mức khái niệm.

### Bài lab

1. Tạo bảng outbox với status, attempts, next_attempt_at và published_at.
2. Tạo scheduler/worker publish outbox bằng publisher confirms.
3. Tạo bảng processed_messages với unique event_id.
4. Cho consumer crash sau khi ghi DB nhưng trước ack; chứng minh lần chạy lại không tạo side effect lần hai.
5. Test transaction rollback không phát ra event giả.

### Tiêu chí hoàn thành

- Giải thích rõ at-least-once + idempotency tạo ra effectively-once ở business layer.
- Không publish event quan trọng trực tiếp sau save mà không có chiến lược consistency.
- Có cleanup/retention cho outbox và inbox.

## Tuần 11 — Performance, throughput và backpressure

### Học

- Throughput, latency, concurrency và saturation.
- Prefetch theo workload và processing time.
- Số consumer, channel và connection hợp lý.
- Batch publish, batch ack và asynchronous confirms.
- Message size, serialization, compression và network cost.
- Publisher rate vượt consumer rate: queue growth và backpressure.
- JVM heap, GC, thread pool và connection pool.
- Benchmark có warm-up, steady state, percentile p50/p95/p99.

### Bài lab

1. Benchmark 1, 4 và 16 consumer.
2. So sánh prefetch 1, 20 và 200.
3. Đo publish confirm latency theo batch.
4. Tạo producer nhanh hơn consumer và quan sát queue depth.
5. Đặt giới hạn rate ở producer hoặc tạm dừng consumer khi downstream quá tải.

### Bảng chẩn đoán

| Triệu chứng | Khả năng | Kiểm tra |
|---|---|---|
| Queue ready tăng | Consumer chậm/ít | Processing time, consumer count |
| Unacked tăng | Ack muộn hoặc consumer treo | Consumer logs, prefetch |
| Confirm latency tăng | Broker/disk/network bận | Disk, memory, publish rate |
| Redelivery tăng | Consumer crash/requeue | Error rate, container restart |
| Memory alarm | Message quá nhiều/quá lớn | Queue depth, message size |

### Tiêu chí hoàn thành

- Có benchmark report và lý do chọn prefetch/concurrency.
- Có giới hạn queue growth hoặc alert trước khi hết disk.
- Không tối ưu bằng cách chỉ tăng consumer vô hạn.

## Tuần 12 — Observability và vận hành hằng ngày

### Học

- Metrics cần theo dõi: ready, unacked, total, publish rate, deliver rate, ack rate, redeliver rate.
- Consumer count, connection/channel count, blocked connection.
- Disk free limit, memory watermark và alarm.
- Management UI so với Prometheus/Grafana.
- Structured logging và correlation ID.
- Distributed tracing qua message headers.
- SLI/SLO: message processing latency, success rate, DLQ rate, oldest message age.
- Alert severity và runbook.

### Dashboard đề xuất

1. Broker health: node, uptime, memory, disk, alarms.
2. Traffic: publish/deliver/ack rate và confirm latency.
3. Queues: ready/unacked, consumers, oldest message age.
4. Errors: redelivery, retry, DLQ, rejected message.
5. Application: processing latency, DB latency, downstream error.

### Bài lab

1. Export metrics RabbitMQ sang Prometheus.
2. Tạo Grafana dashboard cho queue depth và DLQ.
3. Tạo alert khi oldest message age vượt ngưỡng.
4. Trace một order qua HTTP → outbox → RabbitMQ → consumer.
5. Viết runbook cho broker unreachable, queue buildup và DLQ spike.

### Tiêu chí hoàn thành

- Một alert phải có owner, threshold, impact và cách xử lý.
- Từ một eventId có thể tìm được producer log và consumer log.
- Phân biệt broker issue với downstream issue.

## Tuần 13 — Security và governance

### Học

- User, password, tags và permission configure/write/read.
- Virtual host như boundary cho environment/team.
- Least privilege cho từng service.
- TLS cho AMQP và HTTPS cho Management UI.
- Certificate rotation và secret rotation.
- Không expose port quản trị công khai.
- Network policy, firewall và private subnet.
- Audit log, naming convention và ownership.
- Không đưa password, access token hoặc PII nhạy cảm vào message.

### Bài lab

1. Tạo vhost dev và service user chỉ có read/write đúng resource.
2. Thử publish vào exchange không được cấp quyền.
3. Cấu hình TLS local hoặc trên staging.
4. Rotate password mà không commit secret vào Git.
5. Viết permission matrix cho order, payment và notification service.

### Tiêu chí hoàn thành

- Mỗi service có user riêng, không dùng admin.
- Permission test chạy được trong CI.
- Có tài liệu rotate secret và revoke user.

## Tuần 14 — High availability, quorum queues và production topology

### Học

- Node, cluster, peer discovery, leader/follower và network partition.
- Classic queue và quorum queue; chọn theo durability/HA và workload.
- Quorum queue dùng replication và có trade-off về resource/latency.
- Queue type phải được quyết định từ đầu; không đổi tùy tiện trên queue đang có dữ liệu.
- Durable topology, rolling restart và upgrade compatibility.
- Policy, operator policy, queue limits và resource alarms.
- Backup/restore definition, users, permissions, policies và topology.
- Multi-AZ, failure domain và client reconnect.

### Failure drill

1. Restart một node.
2. Ngắt kết nối consumer.
3. Làm broker unavailable trong vài phút.
4. Tạo disk pressure ở môi trường lab.
5. Khôi phục topology và kiểm tra message flow.

### Tiêu chí hoàn thành

- Có sơ đồ failure domain và RTO/RPO giả định.
- Biết dữ liệu nào phải tồn tại sau restart.
- Có procedure rolling upgrade và rollback.
- Không nhầm queue replication với backup.

## Tuần 15 — Testing, contract test và chaos engineering

### Học

- Unit test cho routing key, envelope, retry policy và idempotency.
- Integration test với Testcontainers RabbitMQ.
- Test topology declaration và permission.
- Contract test producer–consumer.
- Test publish confirm, return callback và consumer ack.
- Failure test: broker down, network timeout, consumer crash, DB timeout.
- Load test và soak test.
- Chaos nhỏ, có giới hạn và có cleanup.

### Ma trận test tối thiểu

| Nhóm | Ca kiểm thử |
|---|---|
| Publish | Confirm success, unroutable, broker unavailable |
| Consume | Ack, nack, reject, redelivery, graceful shutdown |
| Retry | Backoff, max attempt, DLQ, replay |
| Contract | Version cũ, field thiếu, field thêm, malformed payload |
| Consistency | DB rollback, duplicate event, crash trước/sau ack |
| Ops | Disk alarm, memory alarm, node restart, reconnect |
| Performance | Prefetch, concurrency, burst, sustained load |

### Tiêu chí hoàn thành

- Integration test không phụ thuộc broker cài sẵn trên máy.
- Pipeline chạy được test quan trọng khi pull request.
- Chaos test có expected result và không làm hỏng môi trường dùng chung.

## Tuần 16 — Capstone: Order Processing Platform

### Bài toán

Xây hệ thống nhận đơn hàng và phát các công việc/event cho payment, inventory, notification và audit.

### Thành phần

- order-service: REST API, PostgreSQL và transactional outbox.
- payment-worker: xử lý PaymentRequested.
- inventory-worker: giữ/từ chối tồn kho.
- notification-worker: gửi email giả lập.
- audit-consumer: lưu audit event.
- RabbitMQ: exchange, queues, retry queues và DLQ.
- Redis hoặc database tùy chọn để idempotency cache.
- Prometheus/Grafana tùy chọn cho observability.

### Topology đề xuất

~~~text
orders.events
├── order.created → payment.queue
├── order.created → inventory.queue
├── order.created → notification.queue
└── order.*       → audit.queue

payment.retry.5s → payment.retry.30s → payment.dlq
inventory.retry.5s → inventory.retry.30s → inventory.dlq
notification.retry.5s → notification.dlq
~~~

### Chức năng bắt buộc

1. API tạo order lưu DB và outbox trong một transaction.
2. Outbox publisher dùng publisher confirms.
3. Message có envelope, eventId, version, correlationId và causationId.
4. Consumer manual ack sau khi business operation hoàn tất.
5. Lỗi transient retry tối đa ba lần với backoff.
6. Lỗi permanent đi DLQ với error metadata.
7. Idempotency chống duplicate side effect.
8. Admin replay DLQ có giới hạn và audit log.
9. Metrics cho queue depth, processing latency, retry và DLQ.
10. Docker Compose chạy được toàn bộ hệ thống.

### Definition of Done

- README có architecture diagram, topology và local setup.
- Có integration test cho happy path, duplicate, retry, DLQ và broker restart.
- Có load test report và lý do chọn prefetch/concurrency.
- Có security checklist và không commit secret.
- Có runbook cho queue buildup, DLQ replay, broker unavailable và database outage.
- Có ADR giải thích exchange type, queue type, retry strategy và outbox.

## 6. Lịch học mỗi tuần

| Buổi | Thời lượng | Việc cần làm |
|---|---:|---|
| Buổi 1 | 90 phút | Đọc concept và vẽ flow |
| Buổi 2 | 120 phút | Code lab nhỏ |
| Buổi 3 | 120 phút | Cố tình tạo lỗi/failure |
| Buổi 4 | 90 phút | Viết test và quan sát metric |
| Buổi 5 | 60 phút | Ghi chú, README và review |

Tỉ lệ đề xuất: 30% đọc, 50% code/lab, 20% test và viết tài liệu.

## 7. Checklist kiến thức cần nắm

### AMQP và topology

- [ ] Exchange khác queue ở điểm nào?
- [ ] Khi nào chọn direct, fanout, topic hoặc headers?
- [ ] Binding và routing key hoạt động thế nào?
- [ ] Durable, exclusive và auto-delete khác nhau ra sao?
- [ ] Virtual host giải quyết vấn đề gì?

### Reliability

- [ ] Ack xảy ra trước hay sau business side effect?
- [ ] Publisher confirm khác consumer ack thế nào?
- [ ] Unroutable message được phát hiện ra sao?
- [ ] Requeue có thể tạo vòng lặp gì?
- [ ] Retry và DLQ có max attempt, backoff và replay policy chưa?

### Consistency

- [ ] Duplicate event có gây side effect hai lần không?
- [ ] Có idempotency key/unique constraint chưa?
- [ ] Database và broker dual-write được xử lý thế nào?
- [ ] Outbox có cleanup và monitoring chưa?

### Performance

- [ ] Prefetch đang được chọn dựa trên workload nào?
- [ ] Queue depth và oldest message age có alert chưa?
- [ ] Consumer concurrency có vượt giới hạn DB/downstream không?
- [ ] Message có quá lớn hoặc chứa dữ liệu thừa không?

### Operations và security

- [ ] Có dashboard và runbook chưa?
- [ ] Có theo dõi ready, unacked, redelivery và DLQ không?
- [ ] Service user có least privilege không?
- [ ] Management UI và AMQP có TLS/network restriction không?
- [ ] Đã thử broker restart và reconnect chưa?

## 8. Anti-pattern cần tránh

- Dùng auto-ack cho công việc có side effect quan trọng.
- Requeue mọi exception vô hạn.
- Dùng một queue khổng lồ cho nhiều domain không liên quan.
- Tạo connection cho từng message.
- Tăng prefetch/consumer vô hạn để che giấu downstream chậm.
- Publish event trực tiếp sau khi commit business data mà không có consistency strategy.
- Đưa JPA entity hoặc secret vào message contract.
- Xóa DLQ để “hết cảnh báo” thay vì điều tra nguyên nhân.
- Dùng RPC qua RabbitMQ cho mọi request đồng bộ.
- Dùng user admin hoặc mật khẩu hard-code trong repository.
- Đổi topology production thủ công mà không có versioned definition.
- Không kiểm thử duplicate, redelivery và consumer crash.

## 9. Tài liệu và nguồn học chính thức

- [RabbitMQ Tutorials](https://www.rabbitmq.com/tutorials) — các pattern Hello World, Work Queues, Publish/Subscribe, Routing, Topics, RPC và Publisher Confirms.
- [Spring AMQP Hello World](https://www.rabbitmq.com/tutorials/tutorial-one-spring-amqp) — RabbitTemplate, listener và khái niệm AMQP trong Spring.
- [Spring AMQP Work Queues](https://www.rabbitmq.com/tutorials/tutorial-two-spring-amqp) — competing consumers và work distribution.
- [Spring AMQP Publish/Subscribe](https://www.rabbitmq.com/tutorials/tutorial-three-spring-amqp) — fanout/pub-sub.
- [Spring AMQP Routing](https://www.rabbitmq.com/tutorials/tutorial-four-spring-amqp) — direct routing.
- [Spring AMQP Topics](https://www.rabbitmq.com/tutorials/tutorial-five-spring-amqp) — topic binding.
- [RabbitMQ Consumer Acknowledgements and Publisher Confirms](https://www.rabbitmq.com/docs/confirms) — ack, nack, prefetch và confirms.
- [RabbitMQ Quorum Queues](https://www.rabbitmq.com/docs/next/quorum-queues) — queue replicated và trade-off HA.
- [Spring AMQP Reference](https://docs.spring.io/spring-amqp/reference/) — cấu hình, listener container, converter, retry và testing.
- [Spring AMQP Resilience and Recovery](https://docs.spring.io/spring-amqp/reference/amqp/resilience-recovering-from-errors-and-broker-failures.html) — recovery và xử lý lỗi broker.
- [Spring AMQP Abstractions](https://docs.spring.io/spring-amqp/reference/amqp/abstractions.html) — exchange, queue và binding abstractions.

## 10. Thứ tự ưu tiên nếu chỉ có 4 tuần

1. Tuần 1–2: AMQP model, exchange/queue/binding và topology.
2. Tuần 4–5: Spring AMQP, work queue, manual ack và prefetch.
3. Tuần 7–8: publisher confirms, retry, DLX/DLQ và poison message.
4. Tuần 10: idempotency và transactional outbox.
5. Làm phiên bản nhỏ của capstone, sau đó bổ sung monitoring, security và HA.

Mục tiêu không phải thuộc mọi API của RabbitMQ, mà là thiết kế được message flow có thể quan sát, chịu duplicate, chịu lỗi và vận hành an toàn trong production.

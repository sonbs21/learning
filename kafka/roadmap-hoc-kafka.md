# Roadmap học Apache Kafka từ cơ bản đến production

> Lộ trình 16 tuần, khoảng 10–14 giờ mỗi tuần. Tập trung vào Apache Kafka 4.x, Kafka clients, Kafka Streams, Kafka Connect, Schema Registry và Spring for Apache Kafka. Hãy luôn pin một version cụ thể trong lab thay vì dùng tag latest.

## 1. Mục tiêu đầu ra

Sau lộ trình, bạn có thể:

- Giải thích Kafka như một distributed append-only log, không chỉ như một message queue.
- Thiết kế topic, partition, key, retention và replication cho từng loại dữ liệu.
- Viết producer/consumer Java và Spring Boot với offset, consumer group, retry và error handling đúng.
- Hiểu leader, follower, ISR, replication factor, min.insync.replicas và controller trong KRaft.
- Xử lý rebalance, duplicate, out-of-order, slow consumer và poison record.
- Cấu hình producer idempotence, acks, retries, batching, compression và delivery timeout.
- Thiết kế at-most-once, at-least-once và exactly-once processing theo đúng giới hạn thực tế.
- Quản lý schema với Avro, Protobuf hoặc JSON Schema và kiểm soát schema evolution.
- Xây pipeline dữ liệu bằng Kafka Connect và xử lý stream bằng Kafka Streams.
- Dùng Spring Kafka: KafkaTemplate, @KafkaListener, container, error handler, transactions, retry và testing.
- Theo dõi lag, throughput, broker health, partition skew, disk usage và rebalance.
- Cấu hình security: TLS, SASL, ACL, principal và secret rotation.
- Vận hành cluster, backup metadata, rolling upgrade, capacity planning và failure drill.
- Hoàn thiện một hệ thống Order/Event Platform có outbox, schema, retry, DLT, Streams, Connect và observability.

## 2. Kafka khác RabbitMQ ở điểm nào?

| Góc nhìn | Kafka | RabbitMQ |
|---|---|---|
| Mô hình chính | Distributed log, consumer đọc theo offset | Broker route message vào queue |
| Lưu trữ | Retention theo thời gian/kích thước, có thể replay | Message thường biến mất sau ack |
| Scale consumer | Consumer group chia partition | Nhiều consumer cạnh tranh trên queue |
| Ordering | Theo từng partition | Theo queue/consumer trong giới hạn topology |
| Replay | Seek offset và đọc lại | Cần republish hoặc DLQ replay |
| Fan-out | Mỗi group đọc độc lập | Mỗi queue bind độc lập |
| Stream processing | Kafka Streams, Connect, ecosystem | Thường xử lý ở application |
| Use case mạnh | Event streaming, CDC, analytics, audit log | Work queue, routing, task delivery, RPC |

Không chọn Kafka chỉ vì “có message”. Hãy chọn dựa trên nhu cầu replay, throughput, retention, partitioning và stream processing.

## 3. Điều kiện đầu vào

### Nên biết trước

- Java, thread, exception, collection và HTTP.
- Spring Boot, REST, JPA transaction và configuration.
- SQL/PostgreSQL, unique constraint, transaction và index.
- Docker, Docker Compose, Git và Linux CLI.
- JSON, serialization và kiến thức cơ bản về distributed systems.

### Công cụ

- JDK 17 hoặc 21 LTS.
- Maven/Gradle, IntelliJ IDEA hoặc VS Code.
- Docker Engine và Docker Compose.
- Apache Kafka 4.x hoặc version được team thống nhất.
- Kafka CLI, kcat, curl/Postman.
- Spring Boot và Spring Kafka tương thích qua dependency management.
- Testcontainers, Prometheus và Grafana.
- Tùy chọn: Apicurio/Confluent Schema Registry và ksqlDB.

## 4. Môi trường lab

### Nguyên tắc version

1. Chọn một Kafka broker/client version cụ thể.
2. Pin image, Java, Spring Boot và Schema Registry trong file cấu hình.
3. Ghi rõ version và compatibility matrix trong README.
4. Không trộn tutorial cũ dùng ZooKeeper với cluster KRaft mới nếu chưa hiểu khác biệt.

### Compose tối giản cho lab

~~~yaml
services:
  kafka:
    image: apache/kafka:PINNED_VERSION
    ports:
      - "9092:9092"
    environment:
      KAFKA_NODE_ID: 1
      KAFKA_PROCESS_ROLES: broker,controller
      KAFKA_LISTENERS: PLAINTEXT://:9092,CONTROLLER://:9093
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092
      KAFKA_CONTROLLER_LISTENER_NAMES: CONTROLLER
      KAFKA_CONTROLLER_QUORUM_VOTERS: 1@kafka:9093
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 1
      KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: 1
~~~

Đây là cấu hình học local, không phải production. Production cần nhiều broker, listener riêng, TLS/SASL, disk riêng, replication và resource limit phù hợp.

### Repository đề xuất

~~~text
kafka-learning/
├── week-01-concepts/
├── week-02-kraft-cli/
├── week-03-topics-partitions/
├── week-04-java-producer/
├── week-05-consumer-groups/
├── week-06-spring-kafka/
├── reliability-lab/
├── schema-registry/
├── kafka-connect/
├── kafka-streams/
├── observability/
├── security/
└── capstone-event-platform/
~~~

## 5. Bản đồ kiến thức Kafka

| Thành phần | Cần hiểu |
|---|---|
| Broker | Lưu partition, nhận produce/fetch request |
| Topic | Tên logic của stream dữ liệu |
| Partition | Đơn vị ordering, parallelism và replication |
| Record | Key, value, headers, timestamp và offset |
| Producer | Serialize, partition, batch và publish |
| Consumer | Fetch record và commit offset |
| Consumer group | Phân chia partition cho các consumer instance |
| Offset | Vị trí đọc của group trong từng partition |
| Leader/follower | Leader nhận read/write; follower replicate |
| ISR | Replica đang bắt kịp leader |
| Controller | Quản lý metadata, election và lifecycle |
| KRaft | Kiến trúc metadata quorum không phụ thuộc ZooKeeper |
| Schema Registry | Quản lý schema và compatibility ngoài broker |
| Kafka Connect | Framework source/sink connector |
| Kafka Streams | Thư viện xử lý stream stateful/stateless |

## 6. Lộ trình 16 tuần

## Tuần 1 — Nền tảng event streaming

### Học

- Event, command, fact, notification và audit log.
- Event-driven architecture, pub/sub, event sourcing ở mức khái niệm.
- Kafka log, append-only record và cursor/offset.
- Throughput, latency, retention, replay và backpressure.
- At-most-once, at-least-once, effectively-once.
- So sánh Kafka với RabbitMQ, SQS và database polling.
- Khi nào không nên dùng Kafka: request/response ngắn, task nhỏ cần ack ngay, workload thấp và topology đơn giản.

### Thực hành

1. Vẽ flow OrderCreated từ HTTP request tới ba consumer độc lập.
2. Mô tả sự khác nhau giữa queue delete-after-ack và Kafka retention.
3. Viết một producer/consumer giả lập bằng file log và cursor.
4. Liệt kê các điểm có thể tạo duplicate hoặc mất event.

### Tiêu chí hoàn thành

- Giải thích được vì sao consumer group khác queue consumer.
- Giải thích được replay bằng offset.
- Chọn Kafka hay RabbitMQ cho ít nhất 10 use case.

## Tuần 2 — Kafka cluster, KRaft và CLI

### Học

- Broker, node, cluster, controller và metadata.
- KRaft: broker role, controller role, quorum và metadata log.
- Listener, advertised listener, bootstrap server.
- Kafka CLI: topic, producer, consumer, consumer groups và configs.
- Client connect trong Docker khác localhost thế nào.
- Topic naming, environment prefix và ownership.

### Bài lab

1. Khởi động Kafka local.
2. Tạo topic với CLI và mô tả cấu hình.
3. Publish record bằng console producer.
4. Consume từ earliest và latest.
5. Liệt kê consumer group, offset và lag.
6. Restart broker rồi kiểm tra topic metadata.

### Tiêu chí hoàn thành

- Không nhầm bootstrap server với broker duy nhất.
- Dùng được CLI để cô lập lỗi trước khi sửa code.
- Biết xem topic config, partition leader và consumer group offset.

## Tuần 3 — Topic, partition, key và replication

### Học

- Partition là đơn vị ordering và scale.
- Record key và partitioner.
- Hash key, sticky partitioning và custom partitioner.
- Partition count, replication factor, leader và follower.
- ISR, min.insync.replicas và unclean leader election.
- Partition skew/hot key.
- Retention time, retention bytes, segment và cleanup policy.
- Delete policy và compact policy; tombstone record.

### Bài lab

1. Tạo topic 3 partition với key là customerId.
2. Chứng minh cùng key đi vào cùng partition.
3. Tạo hot key và đo partition skew.
4. Thử retention ngắn rồi quan sát record hết hạn.
5. Tạo compacted topic cho trạng thái mới nhất của customer.

### Tiêu chí hoàn thành

- Chọn partition key dựa trên ordering và phân bố tải.
- Biết tăng partition có thể ảnh hưởng mapping key và ordering toàn cục.
- Có topic design document cho capstone.

## Tuần 4 — Java producer

### Học

- ProducerRecord, key/value serializer và headers.
- bootstrap.servers, client.id, request.timeout.ms.
- send async, callback và flush.
- acks, retries, delivery.timeout.ms.
- Batching: batch.size và linger.ms.
- Compression: none, gzip, snappy, lz4, zstd.
- max.in.flight.requests.per.connection và ordering.
- Idempotent producer và producer sequence.
- Error phân loại: retriable, fatal, timeout và serialization.

### Bài lab

1. Viết producer gửi OrderCreated có key orderId.
2. Ghi callback partition, offset và timestamp.
3. Thử serialization error và broker unavailable.
4. Benchmark batch/compression khác nhau.
5. Bật idempotence và tạo retry khi mạng chập chờn.

### Tiêu chí hoàn thành

- Không coi send async là “đã lưu thành công” trước khi callback/flush.
- Có cấu hình producer cho reliability và cấu hình cho throughput.
- Có metric send latency, error rate và records per second.

## Tuần 5 — Consumer, offset và consumer group

### Học

- Consumer poll loop, fetch và deserializer.
- group.id, subscription, assignment và partition ownership.
- Commit tự động và commit thủ công.
- Commit trước xử lý, sau xử lý và hậu quả.
- current offset, committed offset, log end offset và lag.
- earliest, latest và offset out of range.
- max.poll.interval.ms, max.poll.records, session.timeout.ms và heartbeat.
- Consumer pause/resume.

### Bài lab

1. Chạy một consumer group với 3 partition.
2. Thêm/bớt instance và quan sát assignment.
3. Commit trước và sau xử lý để tạo hai loại lỗi.
4. Kill consumer giữa lúc xử lý.
5. Seek về offset cũ và replay một khoảng record.

### Tiêu chí hoàn thành

- Giải thích được consumer lag bằng công thức và công cụ.
- Consumer không làm mất record do commit quá sớm.
- Có graceful shutdown và commit strategy rõ ràng.

## Tuần 6 — Rebalance và xử lý song song

### Học

- Rebalance khi member join, leave, crash hoặc partition thay đổi.
- Eager và cooperative assignment.
- Range, round-robin, sticky và cooperative sticky assignor.
- Rebalance listener để flush state hoặc commit.
- Static membership và incremental cooperative rebalance.
- Số consumer không vượt số partition nếu muốn tận dụng parallelism.
- Thread safety: consumer không được dùng đồng thời từ nhiều thread.
- Mô hình một consumer thread + worker pool và rủi ro commit ordering.

### Bài lab

1. Log partition revoked/assigned.
2. Tạo rolling deployment và đo thời gian rebalance.
3. So sánh assignor.
4. Tạo xử lý chậm vượt max.poll.interval.ms.
5. Thử pause partition khi downstream chậm.

### Tiêu chí hoàn thành

- Chẩn đoán được rebalance loop.
- Không xử lý cùng một partition bằng nhiều thread thiếu kiểm soát.
- Có chiến lược graceful shutdown không gây duplicate không cần thiết.

## Tuần 7 — Spring Kafka căn bản

### Học

- spring-kafka và Spring Boot auto-configuration.
- KafkaTemplate, @KafkaListener và ConcurrentMessageListenerContainer.
- ProducerFactory, ConsumerFactory và ConcurrentKafkaListenerContainerFactory.
- NewTopic, topic configuration và partition count.
- JSON serializer/deserializer và trusted packages.
- Record-based listener, batch listener và message headers.
- Error handler, acknowledgment mode và listener concurrency.
- application.yaml cho producer/consumer.

### Bài lab

1. Tạo Spring Boot producer gửi OrderCreated.
2. Tạo @KafkaListener cho payment và inventory.
3. Cấu hình concurrency và manual ack.
4. Log topic, partition, offset, key và headers.
5. Viết integration test với EmbeddedKafka hoặc Testcontainers.

### Tiêu chí hoàn thành

- Biết khi nào dùng record listener và batch listener.
- Không deserialize payload vào JPA entity.
- Cấu hình listener factory riêng cho workload khác nhau.

## Tuần 8 — Reliability: retry, DLT và error handling

### Học

- Retry blocking và non-blocking.
- Backoff fixed, exponential và jitter.
- DefaultErrorHandler, DeadLetterPublishingRecoverer và DLT.
- Phân biệt lỗi transient, permanent và poison record.
- Không retry lỗi validation hoặc schema vô hạn.
- Retry topic và ảnh hưởng ordering.
- DLT metadata: original topic, partition, offset, exception và timestamp.
- Replay DLT có kiểm soát, audit và idempotency.

### Bài lab

1. Tạo retry 3 lần với exponential backoff.
2. Đưa record lỗi sang DLT.
3. Phân loại exception để retry hoặc recover ngay.
4. Replay DLT sau khi sửa bug.
5. Kiểm tra partition ordering khi dùng retry topic.

### Tiêu chí hoàn thành

- Có retry policy cho từng loại exception.
- DLT không bị dùng như thùng rác.
- Replay không gây side effect trùng lặp.

## Tuần 9 — Delivery semantics và consistency

### Học

- At-most-once: commit trước process.
- At-least-once: process rồi commit, duplicate có thể xảy ra.
- Exactly-once semantics trong Kafka: read-process-write với transaction.
- Idempotent producer, transactional.id và transaction timeout.
- Consumer isolation.level read_committed.
- Kafka transaction không tự động bao trùm database transaction.
- Database–Kafka dual write.
- Transactional outbox, inbox và CDC.
- Business idempotency key và unique constraint.

### Bài lab

1. Tạo duplicate bằng crash trước commit.
2. Viết processed_events với unique event_id.
3. Dựng outbox publisher có retry.
4. Dựng Kafka-to-Kafka transactional pipeline.
5. Thử đọc read_uncommitted và read_committed.

### Tiêu chí hoàn thành

- Không quảng cáo “exactly once end-to-end” nếu DB không cùng transaction.
- Ack/commit chỉ xảy ra sau side effect phù hợp.
- Có tài liệu nêu rõ guarantee từng bước.

## Tuần 10 — Message contract và Schema Registry

### Học

- JSON, Avro, Protobuf và trade-off.
- Schema, subject, version, schema ID và serializer/deserializer.
- Backward, forward và full compatibility.
- TopicRecordNameStrategy, RecordNameStrategy và TopicNameStrategy.
- Optional field, default value, enum evolution và breaking change.
- Contract ownership và schema review.
- PII, encryption và data retention.
- Schema Registry là service riêng; broker không tự kiểm soát compatibility.

### Envelope gợi ý

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

1. Đăng ký Avro hoặc Protobuf schema.
2. Thêm optional field mà consumer cũ vẫn đọc được.
3. Thử breaking change để thấy registry từ chối.
4. Viết contract test chạy trong CI.
5. Kiểm tra schema ID và subject bằng API.

### Tiêu chí hoàn thành

- Có schema catalog và owner.
- Không đổi field bắt buộc tùy tiện.
- Consumer hỗ trợ ít nhất một giai đoạn migration.

## Tuần 11 — Kafka Streams

### Học

- Stream, table, KStream, KTable và GlobalKTable.
- Stateless: map, filter, branch, transform.
- Stateful: groupBy, aggregate, reduce, count.
- Window: tumbling, hopping, sliding, session.
- Join stream-stream, stream-table và table-table.
- Serde và state store.
- Changelog topic, repartition topic và restore state.
- Exactly-once processing trong Streams.
- Topology test driver và topology visualization.

### Bài lab

1. Đếm số order theo customer trong tumbling window.
2. Join OrderCreated với CustomerUpdated.
3. Dùng KTable giữ trạng thái order mới nhất.
4. Tạo hot key và quan sát repartition.
5. Xóa local state rồi restore từ changelog.

### Tiêu chí hoàn thành

- Phân biệt event stream và changelog table.
- Biết khi nào join cần repartition.
- Biết state store ảnh hưởng disk và recovery ra sao.

## Tuần 12 — Kafka Connect và data integration

### Học

- Source connector và sink connector.
- Worker distributed và standalone.
- Connector, task, config, offset và status.
- JDBC source/sink, object storage, Elasticsearch/OpenSearch ở mức khái niệm.
- SMT, transforms và data mapping.
- CDC bằng Debezium ở mức kiến trúc.
- Error tolerance, dead-letter queue và retry.
- Connector secret, plugin path và version compatibility.

### Bài lab

1. Đẩy dữ liệu Kafka vào PostgreSQL bằng sink connector.
2. Đọc bảng outbox bằng JDBC source hoặc CDC connector.
3. Thử lỗi schema và xem connector status.
4. Cấu hình retry/DLQ cho connector.
5. Theo dõi task restart và connector lag.

### Tiêu chí hoàn thành

- Biết khi nào dùng Connect thay vì tự viết consumer.
- Connector config không chứa plaintext secret.
- Có runbook khi task FAILED hoặc lag tăng.

## Tuần 13 — Performance và capacity planning

### Học

- Throughput theo broker, partition, producer và consumer.
- Batch size, linger, compression và buffer memory.
- Fetch min bytes, fetch max wait, max poll records.
- Partition count và giới hạn I/O.
- Page cache, disk throughput, network bandwidth và JVM heap.
- Consumer lag, processing time và arrival rate.
- Hot partition, skew và uneven assignment.
- Benchmark p50, p95, p99; warm-up và steady state.

### Bài lab

1. Benchmark producer với compression khác nhau.
2. Đo throughput khi tăng partition.
3. Đo consumer lag khi downstream chậm.
4. Tạo hot key và thử đổi partition key.
5. Tính retention storage dựa trên event rate và record size.

### Bảng chẩn đoán

| Triệu chứng | Khả năng | Kiểm tra |
|---|---|---|
| Lag tăng đều | Consumer không theo kịp | Processing time, partition count |
| Chỉ một partition lag | Hot key hoặc skew | Key distribution, leader load |
| Producer timeout | Broker/network/disk bận | Request latency, ISR, disk |
| Rebalance liên tục | Poll chậm hoặc session lỗi | max.poll.interval, heartbeat |
| Disk tăng nhanh | Retention/segment sai | Bytes in, retention config |

### Tiêu chí hoàn thành

- Có capacity model cho producer, consumer và disk.
- Có ngưỡng alert lag và oldest event age.
- Không giải quyết mọi vấn đề bằng cách tăng partition vô hạn.

## Tuần 14 — Observability và operations

### Học

- Broker metrics: bytes in/out, request latency, request errors.
- Under-replicated partitions, offline partitions, ISR shrink/expand.
- Controller health, active controller count và metadata propagation.
- Consumer lag theo group/topic/partition.
- JVM heap, GC, CPU, disk, network và page cache.
- JMX exporter, Prometheus và Grafana.
- Structured logging, trace ID và event ID.
- SLI/SLO: end-to-end latency, lag, availability, DLT rate.
- Runbook, alert routing và capacity review.

### Dashboard đề xuất

1. Cluster: broker up, controller, ISR, offline/under-replicated partitions.
2. Traffic: produce/fetch rate, bytes in/out, request latency.
3. Consumer: lag, records consumed, rebalance, commit failure.
4. Storage: disk usage, retention, segment count.
5. Application: processing latency, error, retry, DLT và DB/downstream.

### Bài lab

1. Export Kafka/JMX metrics.
2. Tạo dashboard lag và under-replicated partitions.
3. Tạo alert khi consumer lag vượt SLA.
4. Correlate một HTTP trace với Kafka record.
5. Viết runbook cho broker down, lag spike và ISR shrink.

### Tiêu chí hoàn thành

- Mỗi alert có threshold, impact, owner và bước xử lý.
- Có thể xác định lag do producer tăng hay consumer chậm.
- Có log đủ key, topic, partition, offset và correlation ID.

## Tuần 15 — Security, HA và disaster recovery

### Học

- Authentication: SSL/TLS, SASL/SCRAM, SASL/OAUTH ở mức phù hợp môi trường.
- Authorization bằng ACL: cluster, topic, group, transactional ID.
- Principal, least privilege và service account.
- Encryption in transit, secret rotation và certificate rotation.
- Encryption at rest ở hạ tầng/cloud.
- Multi-broker, replication factor, min.insync.replicas.
- Rack awareness, availability zone và failure domain.
- Rolling restart, rolling upgrade và compatibility.
- Mirror/replication giữa cluster ở mức khái niệm.
- Backup topic data, metadata/config và quy trình restore.

### Failure drill

1. Tắt một broker.
2. Làm consumer mất kết nối.
3. Làm disk gần đầy trong lab.
4. Chặn Schema Registry.
5. Rotate credential.
6. Restore một topic hoặc dựng cluster mới từ backup phù hợp.

### Tiêu chí hoàn thành

- Viết được ACL matrix cho producer, consumer và admin.
- Có RPO/RTO giả định.
- Phân biệt replication, retention và backup.
- Có procedure rollback sau upgrade.

## Tuần 16 — Testing và capstone

### Học

- Unit test serializer, partitioner, schema, retry và idempotency.
- Integration test bằng Testcontainers.
- Spring Kafka EmbeddedKafka/Testcontainers.
- Contract test với Schema Registry.
- Kafka Streams TopologyTestDriver.
- Failure test broker unavailable, consumer crash, duplicate và rebalance.
- Load/soak test.
- Chaos test có scope, timeout và cleanup.

## 7. Capstone: Order Event Platform

### Bài toán

Xây một nền tảng xử lý đơn hàng theo event. Khi order được tạo, nhiều service độc lập xử lý payment, inventory, notification, audit và analytics.

### Thành phần

- order-service: REST API, PostgreSQL và transactional outbox.
- outbox-publisher: publish event có schema và idempotent producer.
- payment-service: consumer group xử lý thanh toán.
- inventory-service: giữ/trừ tồn kho.
- notification-service: gửi email giả lập.
- analytics-stream: Kafka Streams aggregate theo customer/product.
- Kafka Connect: sink event hoặc CDC sang PostgreSQL/object storage.
- Schema Registry: quản lý event schema.
- Prometheus/Grafana: metrics và dashboard.

### Topic design gợi ý

~~~text
orders.v1.events
payments.v1.commands
inventory.v1.commands
notifications.v1.commands
orders.v1.dlt
payments.v1.dlt
analytics.v1.repartition
~~~

Key đề xuất:

- order event: orderId để giữ ordering của một order.
- customer event: customerId nếu cần ordering theo customer.
- Không dùng một key cố định cho mọi record.

### Chức năng bắt buộc

1. Tạo order và outbox trong cùng database transaction.
2. Outbox publisher dùng idempotent producer và kiểm tra send result.
3. Event có eventId, type, version, timestamp, correlationId và payload.
4. Consumer commit offset sau business operation.
5. Duplicate event không tạo duplicate payment/inventory.
6. Lỗi transient retry có backoff; lỗi permanent vào DLT.
7. Có DLT replay command an toàn.
8. Kafka Streams tạo dashboard doanh thu theo cửa sổ thời gian.
9. Kafka Connect đồng bộ dữ liệu tới sink.
10. Có metrics lag, DLT, processing latency, throughput và broker health.
11. Có ACL cho từng service và secret ngoài Git.
12. Có Docker Compose cho local và tài liệu deploy staging.

### Definition of Done

- README có architecture diagram, topic catalog, schema catalog và ownership.
- Có integration test cho happy path, duplicate, retry, DLT, rebalance và broker restart.
- Có benchmark report với partition count, producer config và consumer concurrency.
- Có dashboard và runbook.
- Có ADR giải thích partition key, retention, schema format, retry và outbox.
- Có threat model ngắn cho ACL, TLS, PII và secret.
- Không có message loss trong test failure đã định nghĩa.

## 8. Lịch học mỗi tuần

| Buổi | Thời lượng | Nội dung |
|---|---:|---|
| Buổi 1 | 90 phút | Đọc concept và vẽ topology |
| Buổi 2 | 120 phút | Code producer/consumer/stream |
| Buổi 3 | 120 phút | Tạo failure, rebalance hoặc replay |
| Buổi 4 | 90 phút | Viết test và xem metric |
| Buổi 5 | 60 phút | README, benchmark và review |

Tỉ lệ nên dùng: 30% đọc, 45% code, 15% test, 10% tài liệu.

## 9. Checklist kiến thức

### Core Kafka

- [ ] Topic, partition, record và offset.
- [ ] Key, partitioner và ordering.
- [ ] Broker, controller, leader, follower và ISR.
- [ ] KRaft và listener.
- [ ] Retention, segment và compaction.

### Producer

- [ ] Serializer và headers.
- [ ] acks, retries và delivery timeout.
- [ ] Idempotence.
- [ ] Batching và compression.
- [ ] Callback, flush và error handling.

### Consumer

- [ ] Poll loop và commit offset.
- [ ] Consumer group và assignment.
- [ ] Lag, rebalance và max poll.
- [ ] Manual commit và graceful shutdown.
- [ ] Seek, pause, resume và replay.

### Reliability

- [ ] Retry/DLT policy.
- [ ] Duplicate và idempotency.
- [ ] Transactional producer.
- [ ] Outbox/inbox.
- [ ] Schema compatibility.

### Ecosystem

- [ ] Spring Kafka.
- [ ] Schema Registry.
- [ ] Kafka Connect.
- [ ] Kafka Streams.
- [ ] Testcontainers và Streams test driver.

### Operations

- [ ] Lag và throughput dashboard.
- [ ] Under-replicated/offline partition alert.
- [ ] Disk/JVM/network monitoring.
- [ ] ACL/TLS/SASL.
- [ ] Backup, upgrade và failure drill.

## 10. Anti-pattern cần tránh

- Dùng một partition cho production workload lớn.
- Dùng null hoặc một key cố định khiến toàn bộ tải dồn vào một partition.
- Nghĩ rằng tăng partition vẫn giữ được ordering toàn topic.
- Commit offset trước khi side effect hoàn tất.
- Dùng consumer group mới cho mọi lần deploy khiến replay ngoài ý muốn.
- Retry vô hạn một poison record trong main topic.
- Gọi Kafka transaction là exactly-once cho cả database và Kafka.
- Dùng JSON không version cho event contract lâu dài.
- Để producer/consumer tự tạo topic production không kiểm soát.
- Dùng auto-commit cho consumer có business side effect quan trọng.
- Không theo dõi consumer lag và chỉ phát hiện khi người dùng báo lỗi.
- Chạy broker không có replication rồi gọi đó là HA.
- Mở Kafka listener hoặc Schema Registry ra Internet công khai.
- Hard-code password, SASL secret hoặc certificate trong Git.
- Tăng partition/retention mà không tính disk và recovery time.

## 11. Nguồn học chính thức

- [Apache Kafka Documentation](https://kafka.apache.org/documentation/) — key concepts, APIs, configuration, design, operations, security, Connect và Streams.
- [Apache Kafka Quickstart](https://kafka.apache.org/quickstart) — tạo topic, produce và consume record.
- [Apache Kafka Design](https://kafka.apache.org/documentation/#design) — log, replication, semantics và kiến trúc.
- [Apache Kafka Operations](https://kafka.apache.org/documentation/#operations) — vận hành cluster, monitoring và cấu hình.
- [Apache Kafka Security](https://kafka.apache.org/documentation/#security) — authentication, authorization và encryption.
- [Spring for Apache Kafka Reference](https://docs.spring.io/spring-kafka/reference/) — KafkaTemplate, listener, transactions, exactly-once, monitoring, error handling và testing.
- [Spring Kafka Quick Tour](https://docs.spring.io/spring-kafka/reference/quick-tour.html) — khởi tạo ứng dụng Spring Boot Kafka.
- [Confluent Schema Registry Fundamentals](https://docs.confluent.io/platform/current/schema-registry/fundamentals/index.html) — schema, subject, version và compatibility.
- [Confluent Serialization and Deserialization](https://docs.confluent.io/platform/current/schema-registry/fundamentals/serdes-develop/index.html) — Avro, Protobuf, JSON Schema và SerDes.

## 12. Nếu chỉ có 4 tuần

1. Tuần 1–3: topic, partition, key, replication, CLI và KRaft.
2. Tuần 4–6: Java producer, consumer group, offset, lag và rebalance.
3. Tuần 7–10: Spring Kafka, retry/DLT, idempotency, outbox và schema.
4. Tuần 13–16: performance, observability, security và capstone rút gọn.

Mục tiêu cuối cùng không phải thuộc mọi config, mà là thiết kế được một event pipeline có partition key đúng, offset/lag quan sát được, schema có compatibility, failure có replay và consumer có idempotency.

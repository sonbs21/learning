# Tuần 10: Kafka producer, consumer và Spring Kafka

## 1. Mục tiêu bài học

Sau bài này, bạn có thể:

- Phân biệt key/value serializer và deserializer.
- Mô tả producer flow từ record đến partition leader.
- Mô tả consumer poll loop và consumer group.
- Phân biệt consumer position với committed offset.
- So sánh auto commit, manual commit và container commit.
- Hiểu vì sao commit trước processing có thể làm mất record.
- Hiểu vì sao commit sau processing có thể tạo duplicate.
- Dùng KafkaTemplate để gửi record.
- Dùng KafkaListener để nhận record.
- Cấu hình consumer concurrency theo số partition.
- Cấu hình error handler, backoff và recovery.
- Dùng seek/replay để đọc lại record.
- Thiết kế business effect idempotent.
- Chạy lab với order-events có 6 partition và nhiều consumer group.

> Bài này dùng kiến trúc Kafka của Tuần 9 làm nền. Transaction, exactly-once và retry nâng cao sẽ được mở rộng ở các tuần sau.

---

## 2. Producer và consumer end-to-end

~~~text
Application producer
   │ tạo key/value
   │ serialize
   │ partitioner chọn partition
   ▼
Partition leader
   │ append record
   │ replicate
   │ trả metadata + offset
   ▼
Consumer group
   │ poll record
   │ deserialize
   │ xử lý business
   └ commit offset
~~~

Producer không gọi consumer trực tiếp. Hai phía giao tiếp qua topic và partition.

- Producer xác định topic và key.
- Partitioner chọn partition.
- Broker cấp offset.
- Consumer đọc theo assignment.
- Group lưu checkpoint offset.
- Record không bị xóa vì consumer đã commit.

---

## 3. Kafka producer

### 3.1. Config tối thiểu

~~~properties
bootstrap.servers=broker-1:9092,broker-2:9092
key.serializer=org.apache.kafka.common.serialization.StringSerializer
value.serializer=org.springframework.kafka.support.serializer.JsonSerializer
~~~

Production còn cần cân nhắc:

- Acknowledgement.
- Retry và timeout.
- Idempotent producer.
- Batch và compression.
- Max in-flight request.
- Security protocol.
- Schema compatibility.

### 3.2. Key serializer

Key serializer biến key Java thành bytes để partitioner sử dụng.

~~~text
String orderId
       ↓ StringSerializer
bytes
       ↓ partitioner
partition
~~~

Key serializer của producer phải tương thích với key deserializer của consumer.

### 3.3. Value serializer

Value serializer biến payload thành bytes:

~~~text
OrderCreated object
       ↓ JsonSerializer / Avro / Protobuf
bytes
       ↓ broker
record value
~~~

Serializer không tự đảm bảo business schema đúng. Payload vẫn cần validation, version policy và compatibility test.

### 3.4. Key và ordering

~~~text
key = order-123
OrderCreated  → partition 2
OrderPaid     → partition 2
OrderShipped  → partition 2
~~~

Dùng orderId làm key giúp event của cùng order có thể giữ ordering trong partition. Key null hoặc thay đổi format có thể phá ordering theo order.

### 3.5. Gửi record

~~~java
ProducerRecord<String, OrderCreated> record =
        new ProducerRecord<>(
                "order-events",
                order.id(),
                new OrderCreated(order.id(), order.customerId()));
~~~

Record có topic, key, value, headers, timestamp và có thể có partition chỉ định. Thông thường nên dùng key thay vì hardcode partition.

### 3.6. Gửi bất đồng bộ

~~~java
kafkaTemplate.send("order-events", order.id(), event)
        .whenComplete((result, error) -> {
            if (error != null) {
                log.error("Kafka publish failed", error);
                return;
            }

            var metadata = result.getRecordMetadata();
            log.info("Published topic={} partition={} offset={}",
                    metadata.topic(),
                    metadata.partition(),
                    metadata.offset());
        });
~~~

Gọi send chưa có nghĩa publish đã thành công. Cần xử lý future thất bại bằng retry, outbox, alert hoặc response error tùy business.

### 3.7. Gửi đồng bộ

~~~java
SendResult<String, OrderCreated> result =
        kafkaTemplate.send("order-events", order.id(), event)
                .get(10, TimeUnit.SECONDS);
~~~

Đơn giản nhưng block thread; không nên dùng cho mọi record nếu cần throughput cao.

### 3.8. Headers

~~~java
record.headers()
        .add("correlationId",
                correlationId.getBytes(StandardCharsets.UTF_8))
        .add("eventType",
                "OrderCreated".getBytes(StandardCharsets.UTF_8));
~~~

Headers phù hợp với metadata kỹ thuật; business payload chính nên nằm trong value.

---

## 4. Producer reliability cơ bản

### 4.1. Producer acknowledgement

| Acks | Ý nghĩa tổng quát | Đánh đổi |
|---|---|---|
| 0 | Không chờ broker | Latency thấp, rủi ro cao |
| 1 | Leader xác nhận append | Chưa chắc replica đủ |
| all | Chờ điều kiện ISR | An toàn hơn, latency cao hơn |

Behavior thực tế còn phụ thuộc replication, min in-sync replicas và cluster configuration.

### 4.2. Producer retry

~~~text
send
  ↓ temporary error
retry
  ↓
broker response
  ├── success
  └── failure sau retry budget
~~~

Nếu producer không biết request trước đã thành công hay chưa, duplicate record có thể xảy ra. Consumer vẫn cần idempotency.

### 4.3. Idempotent producer

Producer idempotence giúp hạn chế duplicate do retry trong Kafka. Nó không bảo vệ side effect ở database hoặc external API.

~~~text
producer idempotence
  = hạn chế duplicate trong Kafka

business idempotency
  = hạn chế duplicate effect trong hệ thống nghiệp vụ
~~~

### 4.4. Outbox

Nếu service commit database rồi mới send Kafka, process có thể crash giữa hai bước:

~~~text
save order + commit
       ↓ crash
send Kafka chưa chạy
~~~

Outbox lưu publish intent trong cùng database transaction; publisher riêng đọc outbox rồi gửi record. Đây là chủ đề cần học sâu hơn ở phần reliability.

---

## 5. Consumer deserializer

### 5.1. Config tối thiểu

~~~properties
bootstrap.servers=broker-1:9092,broker-2:9092
group.id=order-indexer
key.deserializer=org.apache.kafka.common.serialization.StringDeserializer
value.deserializer=org.springframework.kafka.support.serializer.JsonDeserializer
auto.offset.reset=earliest
enable.auto.commit=false
~~~

Consumer cần deserialize key/value, subscribe topic, poll, process và commit.

### 5.2. Serializer/deserializer phải tương thích

~~~text
JSON OrderCreated
  producer → JsonSerializer
  consumer → JsonDeserializer<OrderCreated>
~~~

Sai format có thể tạo deserialization error hoặc conversion error trước khi business listener chạy.

### 5.3. Deserialization security

Khi dùng JSON type metadata:

- Trust package cụ thể.
- Dùng type mapping rõ.
- Validate payload.
- Không deserialize arbitrary class từ input.
- Không log payload nhạy cảm.
- Gắn event type và version explicit.

### 5.4. Deserialization failure

Cần policy cho record không parse được:

- Ghi topic/partition/offset.
- Đưa sang dead-letter topic nếu phù hợp.
- Không retry vô hạn payload sai schema.
- Alert khi tỷ lệ conversion failure tăng.

---

## 6. Consumer group và poll loop

### 6.1. Subscribe

~~~java
consumer.subscribe(List.of("order-events"));
~~~

Group coordinator gán partition cho consumer.

### 6.2. Poll loop

~~~java
while (running) {
    ConsumerRecords<String, OrderCreated> records =
            consumer.poll(Duration.ofMillis(1000));

    for (ConsumerRecord<String, OrderCreated> record : records) {
        process(record);
    }
}
~~~

Một consumer phải poll đều đặn. Xử lý quá lâu không poll có thể làm group rebalance.

### 6.3. Poll không phải commit

~~~text
poll
  ≠
commit offset
~~~

Poll lấy record về application; commit lưu checkpoint để tiếp tục sau restart/rebalance.

### 6.4. Max poll interval

Nếu thời gian giữa các poll vượt giới hạn:

- Consumer có thể rời group.
- Partition rebalance.
- Record chưa commit có thể xử lý lại.
- Side effect có thể duplicate.

Giải pháp:

- Giảm batch size.
- Tách processing nặng phù hợp.
- Điều chỉnh max poll interval có lý do.
- Không block poll thread vô hạn.
- Giữ business effect idempotent.

### 6.5. Batch poll

Một poll có thể trả record từ nhiều partition:

~~~text
P0 offset 10
P0 offset 11
P1 offset 7
P2 offset 20
~~~

Nếu P0 offset 11 fail, offset sau nó trong P0 chưa nên commit. Error handler cần biết record nào đã xử lý.

---

## 7. Offset commit

### 7.1. Commit trước processing

~~~text
poll → commit → process
~~~

Crash sau commit nhưng trước business effect có thể làm record bị bỏ qua. Đây gần với at-most-once.

### 7.2. Commit sau processing

~~~text
poll → process → commit
~~~

Crash sau business effect nhưng trước commit làm record được xử lý lại. Đây gần với at-least-once; business effect phải idempotent.

### 7.3. Auto commit

Auto commit dễ cấu hình nhưng khó liên kết với business transaction. Nó có thể commit trước khi processing hoàn tất, đặc biệt khi code xử lý bất đồng bộ.

Không bật auto commit chỉ vì ít code hơn.

### 7.4. Manual/container commit

~~~text
poll
  ↓
process thành công
  ↓
commit offset
~~~

Kiểm soát rõ hơn nhưng cần hiểu partition, offset và xử lý song song.

### 7.5. Offset và database transaction

Không có transaction chung giữa offset store và database thì vẫn có khoảng failure:

~~~text
database update
       ↓ crash
offset commit
~~~

Hoặc:

~~~text
offset commit
       ↓ crash
database update
~~~

Dùng idempotency, outbox/inbox hoặc transaction integration phù hợp.

---

## 8. Spring Kafka

Spring Kafka cung cấp:

- KafkaTemplate.
- KafkaMessageListenerContainer.
- ConcurrentMessageListenerContainer.
- KafkaListenerContainerFactory.
- KafkaListener.
- Message conversion.
- Error handler.
- Offset acknowledgement.
- Retry/recovery.
- Transaction integration.
- Monitoring và observation.

Dependency minh họa:

~~~xml
<dependency>
    <groupId>org.springframework.kafka</groupId>
    <artifactId>spring-kafka</artifactId>
</dependency>
~~~

Dùng dependency management/BOM tương thích với Spring Boot và Spring Kafka version của project.

---

## 9. KafkaTemplate

### 9.1. Producer factory

~~~java
@Bean
ProducerFactory<String, OrderCreated> producerFactory(
        KafkaProperties properties) {
    Map<String, Object> config = new HashMap<>();
    config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
            properties.getBootstrapServers());
    config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
            StringSerializer.class);
    config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
            JsonSerializer.class);
    return new DefaultKafkaProducerFactory<>(config);
}
~~~

Project dùng Spring Boot auto-configuration có thể không cần tự tạo toàn bộ bean.

### 9.2. KafkaTemplate

~~~java
@Bean
KafkaTemplate<String, OrderCreated> kafkaTemplate(
        ProducerFactory<String, OrderCreated> producerFactory) {
    return new KafkaTemplate<>(producerFactory);
}
~~~

### 9.3. Publisher service

~~~java
@Service
public class OrderEventPublisher {

    private final KafkaTemplate<String, OrderCreated> kafkaTemplate;

    public OrderEventPublisher(
            KafkaTemplate<String, OrderCreated> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public CompletableFuture<SendResult<String, OrderCreated>> publish(
            OrderCreated event) {
        return kafkaTemplate.send(
                "order-events",
                event.orderId(),
                event);
    }
}
~~~

### 9.4. Kiểm tra metadata

~~~java
kafkaTemplate.send("order-events", event.orderId(), event)
        .whenComplete((result, error) -> {
            if (error != null) {
                log.error("publish failed", error);
                return;
            }

            var metadata = result.getRecordMetadata();
            log.info("topic={} partition={} offset={}",
                    metadata.topic(),
                    metadata.partition(),
                    metadata.offset());
        });
~~~

Metadata giúp debug partition distribution và ordering.

---

## 10. KafkaListener

### 10.1. Listener cơ bản

~~~java
@Component
public class OrderEventListener {

    @KafkaListener(
            topics = "order-events",
            groupId = "order-indexer")
    public void handle(OrderCreated event) {
        orderIndexService.index(event);
    }
}
~~~

### 10.2. Nhận ConsumerRecord

~~~java
@KafkaListener(
        topics = "order-events",
        groupId = "order-indexer")
public void handle(
        ConsumerRecord<String, OrderCreated> record) {

    log.info("topic={} partition={} offset={} key={}",
            record.topic(),
            record.partition(),
            record.offset(),
            record.key());

    orderIndexService.index(record.value());
}
~~~

Dùng ConsumerRecord khi cần metadata.

### 10.3. Nhận headers

~~~java
@KafkaListener(
        topics = "order-events",
        groupId = "order-notification")
public void handle(
        OrderCreated event,
        @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
        @Header(KafkaHeaders.OFFSET) long offset,
        @Header(KafkaHeaders.RECEIVED_KEY) String key) {

    notificationService.send(event);
}
~~~

Nếu key có thể null, header nên được xử lý optional.

### 10.4. Hai group độc lập

~~~java
@KafkaListener(
        topics = "order-events",
        groupId = "order-indexer")
public void index(OrderCreated event) {
    searchIndexService.index(event);
}

@KafkaListener(
        topics = "order-events",
        groupId = "order-notification")
public void notify(OrderCreated event) {
    notificationService.send(event);
}
~~~

Hai group đều nhận event nhưng assignment và offset riêng.

---

## 11. Listener container và concurrency

### 11.1. Cấu hình concurrency

~~~java
@Bean
ConcurrentKafkaListenerContainerFactory<String, OrderCreated>
        kafkaListenerContainerFactory(
                ConsumerFactory<String, OrderCreated> consumerFactory) {

    var factory =
            new ConcurrentKafkaListenerContainerFactory<
                    String, OrderCreated>();

    factory.setConsumerFactory(consumerFactory);
    factory.setConcurrency(3);
    return factory;
}
~~~

Có thể override ở listener tùy version/configuration.

### 11.2. Concurrency và partition

~~~text
partitions = 6
concurrency = 3
→ 3 consumer instance, mỗi instance khoảng 2 partition

partitions = 3
concurrency = 6
→ tối đa 3 consumer active, 3 consumer idle
~~~

Concurrency không tạo thêm partition.

### 11.3. Ordering và concurrency

- Ordering trong từng partition vẫn giữ.
- Không có ordering giữa partition.
- Record cùng key nên cùng partition.
- Async executor không kiểm soát có thể làm completion out-of-order.
- Downstream phải chịu được số request đồng thời.

### 11.4. Chọn concurrency

~~~text
consumer concurrency
≤ database capacity
≤ downstream quota
≤ CPU/memory
≤ partition count
~~~

Tăng concurrency khi database đã quá tải chỉ làm lỗi và retry tăng.

---

## 12. AckMode trong Spring Kafka

| Mode | Ý nghĩa tổng quát |
|---|---|
| RECORD | Commit sau listener xử lý record thành công |
| BATCH | Commit sau batch poll thành công |
| TIME | Commit theo thời gian |
| COUNT | Commit sau số record |
| MANUAL | Listener gọi acknowledge |
| MANUAL_IMMEDIATE | Cố gắng commit ngay khi acknowledge |

### 12.1. Record và batch

RECORD phù hợp record listener cần commit theo record. BATCH phù hợp batch listener nhưng record fail có thể làm các record chưa commit được xử lý lại.

### 12.2. Manual acknowledgement

~~~java
@KafkaListener(
        topics = "order-events",
        groupId = "manual-group",
        containerFactory = "manualKafkaListenerContainerFactory")
public void handle(
        OrderCreated event,
        Acknowledgment acknowledgment) {

    orderService.process(event);
    acknowledgment.acknowledge();
}
~~~

Chỉ acknowledge sau business processing thành công.

### 12.3. Không nuốt exception

Sai:

~~~java
@KafkaListener(topics = "order-events")
public void handle(OrderCreated event) {
    try {
        service.process(event);
    } catch (Exception ex) {
        log.error("failed", ex);
        // container có thể coi là success và commit
    }
}
~~~

Đúng hơn là throw lại exception cần retry hoặc dùng recovery strategy có chủ đích.

---

## 13. Error handler và retry

### 13.1. DefaultErrorHandler

Spring Kafka hiện đại dùng CommonErrorHandler. DefaultErrorHandler hỗ trợ retry, BackOff, seek/replay và recovery.

~~~java
@Bean
DefaultErrorHandler kafkaErrorHandler() {
    var backOff = new FixedBackOff(1000L, 2L);

    return new DefaultErrorHandler(
            (record, exception) -> {
                log.error(
                        "Recovering topic={} partition={} offset={}",
                        record.topic(),
                        record.partition(),
                        record.offset(),
                        exception);
            },
            backOff);
}
~~~

Cần test số lần delivery thực tế vì lần đầu và số lần retry là hai khái niệm khác nhau.

### 13.2. Gắn error handler vào factory

~~~java
@Bean
ConcurrentKafkaListenerContainerFactory<String, OrderCreated>
        kafkaListenerContainerFactory(
                ConsumerFactory<String, OrderCreated> consumerFactory,
                DefaultErrorHandler errorHandler) {

    var factory =
            new ConcurrentKafkaListenerContainerFactory<
                    String, OrderCreated>();

    factory.setConsumerFactory(consumerFactory);
    factory.setCommonErrorHandler(errorHandler);
    factory.getContainerProperties()
            .setAckMode(ContainerProperties.AckMode.RECORD);

    return factory;
}
~~~

### 13.3. Retryable và non-retryable

~~~java
errorHandler.addNotRetryableExceptions(
        InvalidOrderSchemaException.class,
        UnsupportedEventVersionException.class);
~~~

Schema lỗi không nên retry vô hạn. Timeout downstream có thể retry có giới hạn.

### 13.4. Dead-letter topic

Kafka thường dùng dead-letter topic:

~~~text
order-events
   ↓ retry exhausted
order-events.DLT
~~~

DLT nên giữ original topic, partition, offset, key, exception, timestamp, correlation ID và attempt. Không phát tán payload nhạy cảm ngoài policy.

### 13.5. Backoff dài

Delay dài cần cân nhắc max.poll.interval và việc giữ consumer thread. Có thể dùng container-pausing backoff hoặc retry topic architecture tùy use case.

---

## 14. Seek và replay

### 14.1. Seek

Seek thay đổi position đọc:

~~~text
current position = 100
seek partition 0 to offset 80
→ đọc lại từ offset 80
~~~

Dùng cho replay, backfill, recovery và test.

### 14.2. Replay không rollback side effect

Nếu record 80–99 đã tạo effect, seek về 80 sẽ chạy lại effect. Consumer phải idempotent hoặc replay vào sink riêng.

### 14.3. Seek sau error

Error handler có thể seek về offset record lỗi để redeliver. Không tự ý seek trong listener error handler nếu container không được cấu hình để biết việc đó.

### 14.4. Reset group offset

Có thể reset group về earliest, latest, timestamp hoặc offset cụ thể. Production cần:

- Approval.
- Backup offset hiện tại.
- Replay range.
- Idempotency review.
- Lag monitoring.
- Runbook rollback.

---

## 15. Serialization và event envelope

~~~java
public record EventEnvelope<T>(
        String eventId,
        String eventType,
        int eventVersion,
        Instant occurredAt,
        String correlationId,
        String operationId,
        T payload
) {}
~~~

Producer và consumer cần thống nhất:

- Event type.
- Event version.
- Required field.
- Date/time format.
- Compatibility.
- Nullability.
- Error policy.

Thay đổi an toàn thường là thêm field optional, giữ field cũ trong migration và không đổi ý nghĩa field cũ. Breaking change cần version hoặc topic contract mới.

---

## 16. Thực hành theo roadmap

Topology:

~~~text
order-events topic: 6 partitions

order-indexer group
order-notification group
order-analytics group
~~~

### 16.1. Dự đoán assignment

Hai consumer trong order-indexer:

~~~text
P0, P1, P2 → indexer-1
P3, P4, P5 → indexer-2
~~~

Ba consumer trong order-notification:

~~~text
P0, P1 → notification-1
P2, P3 → notification-2
P4, P5 → notification-3
~~~

Một consumer trong order-analytics:

~~~text
P0...P5 → analytics-1
~~~

### 16.2. So sánh group

Mỗi record trong order-events:

- Đến một consumer của order-indexer.
- Đến một consumer của order-notification.
- Đến một consumer của order-analytics.

Các group có offset và lag riêng.

### 16.3. Kiểm tra key

Gửi cùng orderId:

~~~text
OrderCreated
OrderPaid
OrderShipped
~~~

Kiểm tra ba record có cùng partition và ordering không.

### 16.4. Kiểm tra replay

1. Lưu committed offset.
2. Dừng consumer group.
3. Reset về offset cũ.
4. Chạy lại.
5. Đo thời gian replay.
6. Kiểm tra duplicate side effect.
7. Reset về latest sau lab.

---

## 17. Cấu hình Spring Boot minh họa

~~~yaml
spring:
  kafka:
    bootstrap-servers: broker-1:9092,broker-2:9092
    consumer:
      group-id: order-indexer
      auto-offset-reset: earliest
      enable-auto-commit: false
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      properties:
        spring.json.trusted.packages: com.example.orders.events
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
~~~

Config phải được kiểm tra theo Spring Boot/Spring Kafka version; không copy mù giữa các major version.

Topic lifecycle nên quản lý bằng Terraform, Helm, operator hoặc deployment pipeline thay vì để từng application instance tự thay đổi production topic.

---

## 18. Observability

Producer metrics:

- Send success/failure.
- Send latency.
- Record batch size.
- Retry count.
- Error theo topic/partition.

Consumer metrics:

- Records consumed.
- Processing latency.
- Commit latency.
- Error count.
- Rebalance count.
- Poll interval.
- Consumer lag.
- DLT publish count.
- Replay count.

Log fields:

~~~text
topic
partition
offset
key
groupId
eventId
correlationId
operationId
attempt
processingDuration
~~~

Lag tăng có thể do producer rate cao, consumer thiếu instance, hot partition, downstream chậm, rebalance liên tục hoặc poison record. Theo dõi theo group, topic, partition, oldest lag và rate of change.

---

## 19. Bài tập thực hành

### Bài 1: Producer partitioning

Tạo topic order-events có 6 partition. Gửi 30 record cho 3 order ID. Ghi partition và offset; kiểm tra event cùng key có cùng partition không.

### Bài 2: Consumer group assignment

Chạy cùng group với 1, 2, 3, 6 và 8 consumer. Ghi assignment, consumer idle, throughput, rebalance time và lag.

### Bài 3: Hai group độc lập

Chạy order-indexer và order-notification. Chứng minh cả hai nhận cùng record và có committed offset riêng.

### Bài 4: Auto commit và manual commit

Cố ý crash trước processing, sau processing, trước commit và sau commit. Ghi record bị mất hoặc duplicate.

### Bài 5: Error handler

Tạo temporary exception, invalid schema exception và unknown runtime exception. Cấu hình retry khác nhau; kiểm tra record nào đi DLT.

### Bài 6: Seek replay

Gửi 100 event, xử lý đến offset 60, reset về offset 40, replay và chứng minh database không tạo duplicate effect.

### Bài 7: Poll timeout

Tạo handler xử lý lâu hơn max.poll.interval. Quan sát rebalance; sau đó giảm batch size hoặc điều chỉnh config có chủ đích.

### Bài 8: Deserialization error

Gửi payload sai schema. Kiểm tra listener, error handler, retry count và metadata trong DLT.

---

## 20. Câu hỏi tự kiểm tra

1. Producer serializer có nhiệm vụ gì?
2. Consumer deserializer phải khớp với serializer ở đâu?
3. Key ảnh hưởng partitioning như thế nào?
4. Vì sao producer send thành công chưa có nghĩa consumer xử lý xong?
5. Poll khác commit offset ra sao?
6. Auto commit có rủi ro gì?
7. Vì sao commit sau processing thường dẫn đến at-least-once?
8. Consumer nhiều hơn partition thì điều gì xảy ra?
9. Hai group khác nhau đọc topic thế nào?
10. Spring Kafka concurrency có vượt số partition không?
11. AckMode RECORD và BATCH khác nhau ra sao?
12. Vì sao không nên nuốt exception trong listener?
13. DefaultErrorHandler dùng BackOff để làm gì?
14. Seek có thể tạo duplicate side effect không?
15. Khi nào gửi record sang DLT?
16. max.poll.interval liên quan gì đến processing lâu?
17. Vì sao key null có thể phá ordering theo aggregate?
18. Tăng partition có thể ảnh hưởng key mapping thế nào?
19. Consumer lag tăng có thể do nguyên nhân nào?
20. Vì sao producer idempotence không thay thế business idempotency?

---

## 21. Checklist hoàn thành Tuần 10

- [ ] Mình hiểu key/value serializer và deserializer.
- [ ] Mình mô tả được producer flow đến partition leader.
- [ ] Mình mô tả được consumer poll loop.
- [ ] Mình hiểu consumer group assignment.
- [ ] Mình phân biệt position và committed offset.
- [ ] Mình phân biệt auto commit và manual/container commit.
- [ ] Mình hiểu commit trước processing có thể làm mất record.
- [ ] Mình hiểu commit sau processing có thể tạo duplicate.
- [ ] Mình dùng được KafkaTemplate.
- [ ] Mình dùng được KafkaListener.
- [ ] Mình cấu hình được consumer concurrency.
- [ ] Mình hiểu concurrency không vượt parallelism của partition.
- [ ] Mình cấu hình được acknowledgement mode.
- [ ] Mình dùng được DefaultErrorHandler ở mức cơ bản.
- [ ] Mình phân loại retryable và non-retryable exception.
- [ ] Mình hiểu seek/replay và duplicate effect.
- [ ] Mình theo dõi được topic, partition, offset, group và lag.
- [ ] Mình chạy được topology order-events 6 partition.
- [ ] Mình chứng minh được hai group độc lập cùng nhận event.
- [ ] Mình hoàn thành crash, error handler và replay lab.

## Kết luận

Kafka producer serialize và gửi record đến partition; consumer deserialize, poll và xử lý theo assignment của consumer group. Offset là checkpoint, không phải business acknowledgement. Commit trước processing có thể mất record; commit sau processing giảm mất record nhưng cho phép duplicate. Business effect phải idempotent.

Trong Spring Kafka, KafkaTemplate bao bọc producer API, KafkaListener tạo listener container, còn error handler quyết định retry, seek và recovery. Concurrency chỉ có ý nghĩa khi topic có đủ partition và downstream chịu được tải.

~~~text
KafkaTemplate
   ↓ serialize + key
topic partition
   ↓
KafkaListener container
   ↓ poll + deserialize
business handler
   ↓ success
commit offset
~~~

Khi lỗi:

~~~text
handler exception
   ↓
CommonErrorHandler
   ├── retry + backoff
   ├── seek/replay
   └── recover/DLT
~~~

Ở Tuần 11, ta sẽ đi sâu vào Kafka delivery, ordering và retry: commit trước/sau xử lý, duplicate record, poison record, DLT và cách bảo vệ ordering khi retry.

## Nguồn chính thức

- [Spring for Apache Kafka Reference](https://docs.spring.io/spring-kafka/reference/)
- [Spring Kafka listener container properties](https://docs.spring.io/spring-kafka/reference/kafka/container-props.html)
- [Spring Kafka handling exceptions](https://docs.spring.io/spring-kafka/reference/kafka/annotation-error-handling.html)
- [Spring Kafka listener annotation](https://docs.spring.io/spring-kafka/reference/kafka/receiving-messages/listener-annotation.html)
- [Apache Kafka Consumer API](https://kafka.apache.org/40/javadoc/org/apache/kafka/clients/consumer/KafkaConsumer.html)
- [Apache Kafka consumer configuration](https://kafka.apache.org/40/generated/consumer_config.html)
- [Apache Kafka producer configuration](https://kafka.apache.org/35/configuration/producer-configs/)

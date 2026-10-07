# Tuần 7 — Spring Kafka căn bản

> Mục tiêu: xây được Spring Boot producer/consumer có cấu hình rõ ràng, listener factory phù hợp và integration test chạy được với Kafka thật.

## 1. Kết quả cần đạt

Sau bài học, bạn có thể:

- Hiểu Spring Boot auto-configuration cho Spring Kafka.
- Cấu hình producer và consumer bằng application.yaml.
- Dùng KafkaTemplate để gửi record.
- Dùng @KafkaListener để consume record.
- Phân biệt ProducerFactory, ConsumerFactory và listener container factory.
- Tạo topic bằng NewTopic và cấu hình partition count.
- Dùng JSON serializer/deserializer đúng cách.
- Hiểu trusted packages và rủi ro deserialize không an toàn.
- Phân biệt record listener và batch listener.
- Đọc topic, partition, offset, key và headers trong listener.
- Cấu hình manual acknowledgment và listener concurrency.
- Tách listener factory cho payment, inventory và analytics.
- Viết integration test bằng Testcontainers hoặc EmbeddedKafka.

## 2. Spring Kafka đứng ở đâu?

Spring Kafka là lớp tích hợp giữa Spring application và Kafka client:

    Spring Boot application
          |
          +--> KafkaTemplate
          |       |
          |       +--> KafkaProducer
          |
          +--> @KafkaListener
                  |
                  +--> Listener container
                          |
                          +--> KafkaConsumer

Spring Kafka không thay đổi bản chất Kafka:

- producer vẫn có serializer, partition, callback và retry;
- consumer vẫn có poll loop, offset, group và rebalance;
- listener container quản lý phần lớn poll loop;
- ack/commit vẫn phải gắn với business processing;
- duplicate và idempotency vẫn tồn tại.

Spring giúp giảm boilerplate, nhưng không tự động biến xử lý thành exactly-once.

## 3. Chuẩn bị project

### 3.1 Dependency

Dùng Spring Boot và Spring Kafka version tương thích, được pin trong project:

    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter</artifactId>
    </dependency>

    <dependency>
      <groupId>org.springframework.kafka</groupId>
      <artifactId>spring-kafka</artifactId>
    </dependency>

Test:

    <dependency>
      <groupId>org.springframework.kafka</groupId>
      <artifactId>spring-kafka-test</artifactId>
      <scope>test</scope>
    </dependency>

Nếu dùng Testcontainers:

    <dependency>
      <groupId>org.testcontainers</groupId>
      <artifactId>kafka</artifactId>
      <scope>test</scope>
    </dependency>

Không trộn version Spring Boot, spring-kafka và kafka-clients tùy ý. Ưu tiên dependency management của Spring Boot.

### 3.2 Package structure

Một cấu trúc nhỏ, dễ học:

    src/main/java/com/example/orders/
      OrderApplication.java
      config/
        KafkaProducerConfig.java
        KafkaConsumerConfig.java
        KafkaTopicConfig.java
      event/
        OrderCreated.java
      producer/
        OrderEventPublisher.java
      consumer/
        PaymentEventListener.java
        InventoryEventListener.java
      service/
        PaymentService.java
        InventoryService.java

    src/test/java/com/example/orders/
      OrderEventPublisherIT.java
      PaymentEventListenerIT.java

Không deserialize trực tiếp payload vào JPA entity. Event DTO và persistence entity có lifecycle, validation và schema khác nhau.

## 4. application.yaml

Cấu hình cơ bản:

    spring:
      kafka:
        bootstrap-servers: localhost:9092
        producer:
          key-serializer: org.apache.kafka.common.serialization.StringSerializer
          value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
          acks: all
          properties:
            enable.idempotence: true
        consumer:
          group-id: order-payment
          key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
          value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
          auto-offset-reset: earliest
          enable-auto-commit: false
          properties:
            spring.json.trusted.packages: com.example.orders.event
        listener:
          ack-mode: record
          concurrency: 1

Một số property có thể được đặt ở spring.kafka.properties hoặc producer/consumer properties tùy cấu trúc version. Khi property không có tác dụng, kiểm tra binding log và generated configuration.

### 4.1 Environment-specific config

Dùng profile:

    spring:
      config:
        activate:
          on-profile: local

    spring:
      kafka:
        bootstrap-servers: localhost:9092

Không hardcode production broker vào code. Dùng environment variable hoặc secret/config management:

    spring:
      kafka:
        bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}

Khi có credentials TLS/SASL, không ghi secret trực tiếp vào repository.

### 4.2 Cấu hình producer/consumer độc lập

Producer và consumer có lifecycle, timeout và error khác nhau. Đừng chỉ đặt một cục properties chung rồi dùng cho cả hai mà không kiểm tra.

Tách:

- producer bootstrap, serializer, acks, idempotence;
- consumer bootstrap, group-id, deserializer, offset reset;
- listener ack mode, concurrency, error handler;
- topic definitions.

## 5. Event DTO

Dùng DTO riêng:

    public record OrderCreated(
        String eventId,
        String orderId,
        String customerId,
        long totalAmount,
        String currency,
        Instant occurredAt
    ) {}

Event DTO nên:

- có schema rõ;
- immutable;
- không chứa lazy relation;
- không phụ thuộc entity persistence;
- có validation boundary;
- có version/evolution strategy;
- có eventId và correlationId nếu cần truy vết.

Nếu payload là record Java, kiểm tra JsonDeserializer và Jackson support tương thích với version Spring Boot đang dùng.

## 6. KafkaTemplate

### 6.1 Gửi event

    @Service
    public class OrderEventPublisher {
        private final KafkaTemplate<String, OrderCreated> kafkaTemplate;

        public OrderEventPublisher(
            KafkaTemplate<String, OrderCreated> kafkaTemplate
        ) {
            this.kafkaTemplate = kafkaTemplate;
        }

        public CompletableFuture<SendResult<String, OrderCreated>> publish(
            OrderCreated event
        ) {
            return kafkaTemplate.send(
                "dev.orders.order-created.v1",
                event.orderId(),
                event
            );
        }
    }

KafkaTemplate gửi bất đồng bộ và trả về future. Không coi việc gọi publish thành công là broker đã ack.

### 6.2 Gửi với headers

    public CompletableFuture<SendResult<String, OrderCreated>> publish(
        OrderCreated event,
        String correlationId
    ) {
        ProducerRecord<String, OrderCreated> record =
            new ProducerRecord<>(
                "dev.orders.order-created.v1",
                event.orderId(),
                event
            );

        record.headers().add(
            "correlation-id",
            correlationId.getBytes(StandardCharsets.UTF_8)
        );

        return kafkaTemplate.send(record);
    }

### 6.3 Callback/future

    kafkaTemplate.send(topic, event.orderId(), event)
        .whenComplete((result, exception) -> {
            if (exception != null) {
                log.error(
                    "Kafka publish failed eventId={}",
                    event.eventId(),
                    exception
                );
                return;
            }

            var metadata = result.getRecordMetadata();

            log.info(
                "Kafka publish success eventId={} topic={} partition={} offset={}",
                event.eventId(),
                metadata.topic(),
                metadata.partition(),
                metadata.offset()
            );
        });

Cần có metric publish success/error và không log payload nhạy cảm.

### 6.4 Gửi đồng bộ

Có thể chờ kết quả:

    SendResult<String, OrderCreated> result =
        kafkaTemplate.send(topic, event.orderId(), event).get();

Chỉ dùng khi flow cần kết quả trước khi tiếp tục. Chờ từng event có thể giảm throughput. Thông thường dùng async, rồi kiểm soát future ở boundary phù hợp.

## 7. ProducerFactory và KafkaTemplate bean

Spring Boot có thể auto-configure KafkaTemplate nếu properties phù hợp. Khi cần nhiều producer profile, tạo factory riêng:

    @Configuration
    public class KafkaProducerConfig {
        @Bean
        public ProducerFactory<String, OrderCreated>
        orderProducerFactory(KafkaProperties properties) {
            Map<String, Object> configs =
                new HashMap<>(properties.buildProducerProperties());

            configs.put(
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class
            );

            configs.put(
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                JsonSerializer.class
            );

            return new DefaultKafkaProducerFactory<>(configs);
        }

        @Bean
        public KafkaTemplate<String, OrderCreated>
        orderKafkaTemplate(
            ProducerFactory<String, OrderCreated> factory
        ) {
            return new KafkaTemplate<>(factory);
        }
    }

Tên API cụ thể có thể thay đổi theo Spring Boot/Spring Kafka version. Ý chính:

- ProducerFactory tạo KafkaProducer;
- KafkaTemplate là abstraction gửi message;
- serializer phải khớp generic type;
- producer config cần được pin/test.

## 8. NewTopic và topic configuration

### 8.1 Tạo topic bằng bean

    @Configuration
    public class KafkaTopicConfig {
        @Bean
        public NewTopic orderCreatedTopic() {
            return TopicBuilder
                .name("dev.orders.order-created.v1")
                .partitions(3)
                .replicas(1)
                .build();
        }
    }

Khi application khởi động, Spring có thể gửi request tạo topic nếu broker cho phép và infrastructure setup phù hợp.

### 8.2 Không coi application là nơi duy nhất quản lý topic production

NewTopic tiện cho local/test, nhưng production có thể cần:

- Terraform/Helm/Ansible;
- approval và ownership;
- replication factor chuẩn;
- retention/cleanup;
- ACL;
- migration policy;
- kiểm soát thay đổi partition.

Nếu application tự ý tạo topic trong production, startup có thể tạo side effect ngoài ý muốn.

### 8.3 Topic config

    @Bean
    public NewTopic customerStateTopic() {
        return TopicBuilder
            .name("dev.customers.state.v1")
            .partitions(3)
            .replicas(1)
            .config(TopicConfig.CLEANUP_POLICY_CONFIG, "compact")
            .config(TopicConfig.RETENTION_MS_CONFIG, "604800000")
            .build();
    }

Đừng dùng compact cho event history chỉ vì muốn giảm disk. Policy phải phù hợp semantics topic.

## 9. @KafkaListener

### 9.1 Record listener

    @KafkaListener(
        topics = "dev.orders.order-created.v1",
        groupId = "order-payment",
        containerFactory = "orderKafkaListenerContainerFactory"
    )
    public void onOrderCreated(
        OrderCreated event,
        @Header(KafkaHeaders.RECEIVED_KEY) String key
    ) {
        paymentService.authorize(event);
    }

Listener nhận từng record logic một lần. Container vẫn poll theo batch ở bên dưới, nhưng callback của application xử lý record.

### 9.2 Đọc metadata

    @KafkaListener(
        topics = "dev.orders.order-created.v1",
        groupId = "order-payment"
    )
    public void onOrderCreated(
        OrderCreated event,
        @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
        @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
        @Header(KafkaHeaders.OFFSET) long offset,
        @Header(KafkaHeaders.RECEIVED_KEY) String key,
        @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp
    ) {
        log.info(
            "received topic={} partition={} offset={} key={} eventId={}",
            topic,
            partition,
            offset,
            key,
            event.eventId()
        );

        paymentService.authorize(event);
    }

Metadata này hữu ích cho:

- trace;
- duplicate investigation;
- lag/offset debug;
- audit;
- replay.

Không dùng offset thay cho eventId trong idempotency nghiệp vụ.

### 9.3 Message<?> listener

Khi cần đọc headers linh hoạt:

    @KafkaListener(
        topics = "dev.orders.order-created.v1",
        groupId = "order-analytics"
    )
    public void onMessage(Message<OrderCreated> message) {
        OrderCreated event = message.getPayload();
        MessageHeaders headers = message.getHeaders();

        analyticsService.record(event, headers);
    }

Dùng kiểu rõ ràng và không cho phép listener generic tùy tiện nhận mọi payload trong production.

## 10. Batch listener

### 10.1 Khi nào dùng?

Batch listener phù hợp khi:

- xử lý nhiều record hiệu quả hơn;
- downstream hỗ trợ bulk insert;
- cần batch API;
- muốn giảm overhead per-record;
- processing có thể chấp nhận batch failure semantics.

Ví dụ:

    @KafkaListener(
        topics = "dev.orders.order-created.v1",
        groupId = "order-analytics-batch",
        containerFactory = "batchKafkaListenerContainerFactory"
    )
    public void onBatch(
        List<ConsumerRecord<String, OrderCreated>> records
    ) {
        analyticsService.processBatch(records);
    }

### 10.2 Rủi ro batch listener

- một record lỗi có thể ảnh hưởng cả batch;
- retry nguyên batch có thể duplicate record đã thành công;
- cần biết offset từng partition;
- batch size và poll interval liên quan;
- exception handling phức tạp hơn.

Không chọn batch listener chỉ vì “nhanh hơn”. Đo workload và thiết kế failure behavior.

### 10.3 Record listener hay batch listener?

| Tiêu chí | Record listener | Batch listener |
|---|---|---|
| Đơn giản | Tốt | Vừa |
| Xử lý từng record | Tốt | Cần tự loop |
| Bulk downstream | Kém hơn | Tốt |
| Partial failure | Dễ reasoning hơn | Phức tạp |
| Throughput | Có thể thấp hơn | Có thể cao hơn |
| Retry từng record | Thuận tiện hơn | Cần policy riêng |

## 11. ConcurrentMessageListenerContainer

Listener container quản lý:

- KafkaConsumer;
- poll loop;
- thread;
- assignment;
- commit/ack;
- error flow;
- lifecycle;
- concurrency.

Spring dùng container factory để tạo listener container cho @KafkaListener.

### 11.1 Container factory

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, OrderCreated>
    orderKafkaListenerContainerFactory(
        ConsumerFactory<String, OrderCreated> consumerFactory
    ) {
        var factory =
            new ConcurrentKafkaListenerContainerFactory<
                String, OrderCreated
            >();

        factory.setConsumerFactory(consumerFactory);
        factory.setConcurrency(3);
        factory.getContainerProperties()
            .setAckMode(ContainerProperties.AckMode.RECORD);

        return factory;
    }

Concurrency 3 thường tạo nhiều consumer instance/thread bên dưới, không phải ba thread dùng chung một KafkaConsumer.

### 11.2 Concurrency và partition

Nếu topic có 3 partition, concurrency 3 có thể sử dụng cả 3 partition trong cùng group. Nếu concurrency 5 nhưng topic chỉ có 3 partition, tối đa 3 consumer có assignment.

Concurrency không tạo thêm partition. Không tăng concurrency vô hạn.

### 11.3 Factory riêng cho workload

Ví dụ:

- payment: record listener, concurrency 3, manual ack;
- analytics: batch listener, concurrency 6;
- notification: concurrency 2, retry/error policy khác;
- state updater: ordering nghiêm ngặt, concurrency theo partition.

Không ép mọi listener dùng một factory nếu semantics khác nhau.

## 12. ConsumerFactory

ConsumerFactory chịu trách nhiệm tạo consumer với config:

    @Bean
    public ConsumerFactory<String, OrderCreated>
    orderConsumerFactory(KafkaProperties properties) {
        Map<String, Object> configs =
            new HashMap<>(properties.buildConsumerProperties());

        configs.put(
            ConsumerConfig.GROUP_ID_CONFIG,
            "order-payment"
        );

        configs.put(
            ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG,
            false
        );

        configs.put(
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
            "earliest"
        );

        return new DefaultKafkaConsumerFactory<>(
            configs,
            new StringDeserializer(),
            new JsonDeserializer<>(OrderCreated.class)
        );
    }

Đặt group-id trong factory hoặc annotation tùy design, nhưng cần tránh cấu hình mâu thuẫn giữa hai nơi.

## 13. JSON serializer/deserializer

### 13.1 JsonSerializer

JsonSerializer chuyển object thành JSON bytes. Producer cần event DTO có cấu trúc ổn định.

Lưu ý:

- type headers có thể được thêm vào;
- consumer cần hiểu type metadata;
- nhiều service khác ngôn ngữ có thể không muốn phụ thuộc Java class name;
- schema evolution cần kế hoạch.

### 13.2 JsonDeserializer

JsonDeserializer chuyển bytes thành object. Cần cấu hình target type và trusted packages phù hợp.

Ví dụ property:

    spring.json.trusted.packages: com.example.orders.event

Trusted packages không phải whitelist “cho đẹp”. Deserialize object từ nguồn không tin cậy với cấu hình quá rộng có thể tạo rủi ro bảo mật tùy serializer/configuration.

### 13.3 Trusted packages

Tránh:

    spring.json.trusted.packages: "*"

trong production nếu không có lý do và kiểm soát đầy đủ.

Nên:

- chỉ trust package DTO event;
- không deserialize trực tiếp vào class có side effect;
- không dùng payload Kafka như lệnh Java object tùy ý;
- validate payload sau deserialize;
- kiểm soát type headers;
- test payload lạ/malformed.

### 13.4 Type mapping

Nếu producer và consumer dùng package/class khác nhau, type header chứa Java class name có thể gây lỗi. Có thể dùng type mapping hoặc deserializer target type ổn định.

Định hướng tốt hơn cho hệ thống nhiều ngôn ngữ:

- envelope rõ eventType/eventVersion;
- schema độc lập ngôn ngữ;
- serializer/deserializer được contract test;
- không dùng Java class name làm business contract.

## 14. Acknowledgment mode

### 14.1 RECORD

Container ack sau khi listener xử lý record thành công:

    factory.getContainerProperties()
        .setAckMode(ContainerProperties.AckMode.RECORD);

Phù hợp khi muốn semantics gần process-then-commit theo record, nhưng duplicate vẫn có thể xảy ra khi crash trước commit.

### 14.2 BATCH

Ack sau khi toàn bộ batch poll xử lý thành công. Nếu một record lỗi, failure semantics cần được cấu hình cẩn thận.

### 14.3 MANUAL

Listener gọi ack:

    @KafkaListener(
        topics = "dev.orders.order-created.v1",
        containerFactory = "manualAckFactory"
    )
    public void onOrderCreated(
        OrderCreated event,
        Acknowledgment acknowledgment
    ) {
        paymentService.authorize(event);
        acknowledgment.acknowledge();
    }

Chỉ ack sau khi business processing thành công theo định nghĩa của application.

### 14.4 MANUAL_IMMEDIATE

Ack cố gắng commit ngay nếu được gọi trên consumer thread. Nếu gọi từ thread khác, behavior có thể khác và cần hiểu container threading.

Không chuyển acknowledgment tùy tiện sang worker thread mà không có thiết kế commit ordering.

### 14.5 Ack không phải transaction end-to-end

Ack/commit offset chỉ cập nhật Kafka progress. Nếu paymentService ghi database rồi ack thất bại, record có thể đọc lại. Payment service cần idempotency.

## 15. Consumer group trong Spring Kafka

Có thể đặt groupId trên annotation:

    @KafkaListener(
        topics = "dev.orders.order-created.v1",
        groupId = "order-payment"
    )

Hoặc cấu hình group-id trong consumer factory/application properties.

Mỗi domain nên có group riêng nếu đều cần nhận event:

- order-payment;
- order-inventory;
- order-notification;
- order-analytics.

Hai instance cùng listener/group sẽ chia partition. Hai listener khác group sẽ đọc độc lập.

## 16. Error handler ở mức căn bản

Tuần 8 sẽ học retry/DLT sâu hơn. Ở tuần này cần hiểu:

- exception trong listener không nên bị nuốt;
- container cần biết record xử lý thất bại;
- ack không được gọi trước business success;
- error handler phải có policy;
- log cần topic/partition/offset/eventId;
- permanent error không nên retry vô hạn.

Ví dụ error handler tối giản tùy version:

    factory.setCommonErrorHandler(
        new DefaultErrorHandler()
    );

API và constructor có thể khác theo Spring Kafka version. Hãy đọc Javadoc của version đang pin.

Đừng cấu hình retry/DLT phức tạp mà chưa hiểu offset và acknowledgment.

## 17. Không deserialize event vào JPA entity

Sai:

    @KafkaListener(...)
    public void onMessage(OrderEntity entity) {
        orderRepository.save(entity);
    }

Vấn đề:

- event schema bị khóa vào persistence schema;
- field lazy/relationship không phù hợp;
- producer thay đổi payload làm hỏng entity mapping;
- consumer có thể ghi đè dữ liệu không hợp lệ;
- khó versioning;
- domain logic bị ẩn trong deserialize.

Đúng hơn:

    @KafkaListener(...)
    public void onMessage(OrderCreated event) {
        PaymentCommand command =
            paymentMapper.toCommand(event);

        paymentService.handle(command);
    }

Tách:

- event DTO: contract nhận từ Kafka;
- command/domain model: logic nghiệp vụ;
- entity: mô hình database;
- response/read model: mô hình trả ra ngoài.

## 18. Spring Kafka configuration mẫu

### 18.1 Producer

    @Configuration
    public class ProducerConfig {
        @Bean
        public ProducerFactory<String, OrderCreated>
        producerFactory(KafkaProperties properties) {
            Map<String, Object> configs =
                new HashMap<>(properties.buildProducerProperties());

            configs.put(
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class
            );
            configs.put(
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                JsonSerializer.class
            );
            configs.put(
                ProducerConfig.ACKS_CONFIG,
                "all"
            );
            configs.put(
                ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG,
                true
            );

            return new DefaultKafkaProducerFactory<>(configs);
        }

        @Bean
        public KafkaTemplate<String, OrderCreated>
        kafkaTemplate(
            ProducerFactory<String, OrderCreated> factory
        ) {
            return new KafkaTemplate<>(factory);
        }
    }

### 18.2 Consumer

    @Configuration
    public class ConsumerConfig {
        @Bean
        public ConsumerFactory<String, OrderCreated>
        consumerFactory(KafkaProperties properties) {
            Map<String, Object> configs =
                new HashMap<>(properties.buildConsumerProperties());

            configs.put(
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG,
                false
            );
            configs.put(
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                "earliest"
            );

            JsonDeserializer<OrderCreated> valueDeserializer =
                new JsonDeserializer<>(OrderCreated.class);

            valueDeserializer.addTrustedPackages(
                "com.example.orders.event"
            );

            return new DefaultKafkaConsumerFactory<>(
                configs,
                new StringDeserializer(),
                valueDeserializer
            );
        }

        @Bean
        public ConcurrentKafkaListenerContainerFactory<
            String, OrderCreated
        > orderListenerFactory(
            ConsumerFactory<String, OrderCreated> consumerFactory
        ) {
            var factory =
                new ConcurrentKafkaListenerContainerFactory<
                    String, OrderCreated
                >();

            factory.setConsumerFactory(consumerFactory);
            factory.setConcurrency(3);
            factory.getContainerProperties().setAckMode(
                ContainerProperties.AckMode.RECORD
            );

            return factory;
        }
    }

Tên config class chỉ minh họa. Nếu auto-configuration đã đủ, không tạo bean trùng gây khó debug.

## 19. Bài lab 1 — Spring Boot producer

Yêu cầu:

1. Tạo Spring Boot app.
2. Cấu hình bootstrap server trong application.yaml.
3. Tạo OrderCreated DTO.
4. Tạo KafkaTemplate<String, OrderCreated>.
5. Viết endpoint hoặc command để publish event.
6. Dùng orderId làm key.
7. Log future callback với topic, partition và offset.
8. Consume bằng Kafka CLI để kiểm tra JSON.

Tiêu chí:

- publish failure được log;
- key đúng;
- payload có eventId;
- producer không block không cần thiết;
- shutdown không để mất future đang pending.

## 20. Bài lab 2 — Payment và Inventory listener

Tạo hai listener:

    @KafkaListener(
        topics = "dev.orders.order-created.v1",
        groupId = "order-payment"
    )
    public void payment(OrderCreated event) {
        paymentService.authorize(event);
    }

    @KafkaListener(
        topics = "dev.orders.order-created.v1",
        groupId = "order-inventory"
    )
    public void inventory(OrderCreated event) {
        inventoryService.reserve(event);
    }

Gửi 10 event và kiểm tra:

- cả hai group đều nhận đủ event;
- hai instance payment cùng group chia partition;
- payment và inventory khác group nên không chia event với nhau;
- log topic/partition/offset/key.

## 21. Bài lab 3 — Manual ack

1. Tạo listener factory ack mode manual.
2. Process event.
3. Chỉ gọi acknowledge sau khi service thành công.
4. Cố tình ném exception trước ack.
5. Restart consumer.
6. Kiểm tra record có được đọc lại không.
7. Gọi ack trước process và lặp lại để quan sát rủi ro.

Ghi rõ:

- offset commit ở thời điểm nào;
- side effect có xảy ra chưa;
- duplicate có thể xuất hiện không;
- consumer idempotency xử lý thế nào.

## 22. Bài lab 4 — Record listener và batch listener

Tạo hai listener factory:

- record factory;
- batch factory.

Gửi 100 event và đo:

- số lần callback;
- tổng thời gian;
- throughput;
- behavior khi một record lỗi;
- commit/ack semantics;
- log metadata.

Không kết luận batch luôn tốt hơn. Hãy ghi trade-off failure và retry.

## 23. Bài lab 5 — Listener concurrency

1. Tạo topic 3 partition.
2. Cấu hình concurrency 1.
3. Gửi record và đo throughput.
4. Tăng concurrency lên 3.
5. Chạy cùng group với hai application instance.
6. Kiểm tra assignment.
7. Tăng concurrency lên 5.
8. Quan sát số consumer active tối đa.

Kết luận:

- concurrency không vượt partition để tăng parallelism;
- số instance và concurrency cùng tham gia vào tổng member;
- ordering theo partition vẫn cần được bảo toàn;
- downstream có thể trở thành bottleneck.

## 24. Bài lab 6 — JSON trusted packages

1. Gửi payload hợp lệ vào topic.
2. Gửi payload thiếu field.
3. Gửi JSON malformed.
4. Gửi type header không mong đợi.
5. Quan sát deserialize exception.
6. Kiểm tra trusted package chỉ cho DTO event.
7. Xác nhận không deserialize vào entity.

Ghi rõ record lỗi được xử lý ở đâu. Tuần 8 sẽ đưa vào retry/DLT; tuần này chỉ cần không nuốt lỗi và hiểu nguyên nhân.

## 25. Bài lab 7 — Integration test

Test tối thiểu:

1. Start Kafka bằng Testcontainers hoặc EmbeddedKafka.
2. Tạo topic test.
3. Publish OrderCreated bằng KafkaTemplate.
4. Chờ listener nhận event.
5. Assert eventId/orderId đã xử lý.
6. Assert key, partition hoặc header nếu cần.
7. Dọn group/topic sau test.
8. Đảm bảo test không phụ thuộc topic/offset của lần chạy trước.

Test nên kiểm tra behavior, không chỉ kiểm tra bean tồn tại.

## 26. EmbeddedKafka và Testcontainers

### EmbeddedKafka

Ưu điểm:

- nhanh;
- phù hợp test Spring Kafka;
- setup đơn giản cho một số integration test.

Điểm cần lưu ý:

- behavior có thể khác cluster/container production;
- version và config phải tương thích;
- không thay thế test với broker thật cho network/replication.

### Testcontainers

Ưu điểm:

- chạy Kafka thật trong container;
- gần môi trường local hơn;
- kiểm tra bootstrap/network/config thực tế.

Chi phí:

- test chậm hơn;
- cần Docker;
- phải quản lý lifecycle;
- cần tránh port conflict và test flaky.

Chọn theo mục tiêu test. Dùng mock cho unit test nhỏ, broker thật cho integration behavior.

## 27. Test listener không flaky

- dùng groupId duy nhất cho mỗi test;
- chờ bằng Awaitility hoặc cơ chế tương đương, không sleep cố định dài;
- reset topic/offset có chủ đích;
- assert eventId cụ thể;
- dọn producer/consumer/container;
- tách test song song nếu broker isolation an toàn;
- log lỗi listener trong test;
- không dùng topic production;
- đặt timeout rõ ràng.

Không coi test pass chỉ vì message xuất hiện ở một consumer khác group.

## 28. Chẩn đoán lỗi Spring Kafka

### Producer gửi nhưng listener không nhận

Kiểm tra:

1. topic có đúng không;
2. group-id có offset cũ không;
3. auto-offset-reset;
4. listener container có running không;
5. deserializer có lỗi không;
6. bootstrap server đúng không;
7. producer và consumer có cùng cluster không;
8. listener method có bị disable bởi profile không.

### Listener không start

Kiểm tra:

- bean config tạo được không;
- deserializer class;
- trusted packages;
- topic tồn tại hoặc auto-create;
- authentication/TLS;
- factory type generic;
- exception trong application startup.

### Deserialize lỗi

Kiểm tra:

- JSON field/type;
- type headers;
- package mapping;
- DTO constructor/record support;
- producer serializer;
- schema version;
- trusted package.

Không giải quyết bằng cách trust tất cả package một cách mù quáng.

### Ack không commit như mong đợi

Kiểm tra:

- ack mode;
- enable auto commit;
- gọi acknowledge trên thread nào;
- exception trước ack;
- group offset bằng CLI;
- rebalance trong lúc xử lý;
- transaction setting nếu có.

### Concurrency tăng nhưng throughput không tăng

Khả năng:

- topic ít partition;
- downstream chậm;
- key skew;
- database lock;
- listener processing CPU-bound;
- container bị rebalance;
- consumer concurrency vượt assignment.

## 29. Sai lầm phổ biến

### Coi @KafkaListener là exactly-once

Annotation chỉ tạo listener; guarantee phụ thuộc offset, side effect, retry và idempotency.

### Dùng một listener factory cho mọi workload

Payment, analytics batch và state update có semantics khác nhau.

### Deserialize thẳng vào JPA entity

Làm coupling event contract với schema database.

### Trust tất cả package

Mở rộng boundary deserialize không cần thiết và khó kiểm soát.

### Cấu hình concurrency cao hơn partition

Không tạo thêm parallelism nếu topic không có partition tương ứng.

### Gọi acknowledge trước business processing

Có thể commit offset khi side effect chưa thành công.

### Không log metadata

Thiếu topic/partition/offset/eventId khiến điều tra duplicate và lag khó.

### Tạo topic production bằng startup app tùy tiện

Có thể tạo side effect hạ tầng ngoài quy trình.

### Dùng sleep để chờ integration test

Test chậm và flaky; dùng await có timeout.

### Gộp tất cả properties vào application.yaml duy nhất

Khó phân biệt producer, consumer, listener và profile.

## 30. Câu hỏi tự kiểm tra

1. Spring Kafka nằm trên Kafka client như thế nào?
2. KafkaTemplate làm gì?
3. ProducerFactory và ConsumerFactory khác nhau thế nào?
4. @KafkaListener thực sự chạy trên thành phần nào?
5. Vì sao listener container quan trọng?
6. Record listener và batch listener khác nhau thế nào?
7. Concurrency liên quan gì tới partition?
8. Trusted packages dùng để làm gì?
9. Vì sao không deserialize event vào JPA entity?
10. Ack mode RECORD và MANUAL khác nhau thế nào?
11. KafkaTemplate send return có phải broker success không?
12. Vì sao producer và consumer nên có config riêng?
13. NewTopic phù hợp ở đâu và cần cẩn trọng ở đâu?
14. Vì sao listener group phải tách theo domain?
15. Integration test cần assert những gì?
16. Vì sao test groupId nên unique?
17. Concurrency tăng mà throughput không tăng có thể do đâu?
18. Vì sao Spring Kafka không tự đảm bảo idempotency?

### Đáp án ngắn

1. Spring Kafka cung cấp abstraction/container trên Kafka client.
2. Gửi record qua Kafka producer.
3. Factory tạo producer hoặc consumer với config/serializer tương ứng.
4. ConcurrentMessageListenerContainer.
5. Quản lý poll, thread, assignment, commit và lifecycle.
6. Một record callback so với một danh sách record/batch.
7. Consumer active trong group bị giới hạn bởi partition.
8. Giới hạn class/package được phép deserialize.
9. Tránh coupling event contract với persistence model.
10. Container ack theo record so với listener tự ack.
11. Không, cần future/callback/metadata.
12. Semantics và property khác nhau.
13. Local/test tiện; production cần ownership/migration/governance.
14. Để mỗi domain nhận event độc lập.
15. Publish, consume, payload, key/header/side effect và timeout.
16. Tránh offset cũ và nhiễu giữa test.
17. Ít partition, downstream bottleneck, skew hoặc rebalance.
18. Spring không biết business side effect và duplicate policy.

## 31. Checklist hoàn thành Tuần 7

- [ ] Tôi tạo được Spring Boot app có spring-kafka.
- [ ] Tôi cấu hình bootstrap server trong application.yaml.
- [ ] Tôi gửi được event bằng KafkaTemplate.
- [ ] Tôi kiểm tra được future/callback gửi thành công hoặc lỗi.
- [ ] Tôi tạo được @KafkaListener record-based.
- [ ] Tôi tạo được batch listener.
- [ ] Tôi hiểu ProducerFactory, ConsumerFactory và listener factory.
- [ ] Tôi tạo được NewTopic cho local/test.
- [ ] Tôi cấu hình JSON serializer/deserializer.
- [ ] Tôi giới hạn trusted packages.
- [ ] Tôi log được topic, partition, offset, key và headers.
- [ ] Tôi cấu hình được manual ack.
- [ ] Tôi hiểu concurrency không vượt số partition.
- [ ] Tôi tách listener factory theo workload.
- [ ] Tôi không deserialize event vào JPA entity.
- [ ] Tôi viết được integration test với broker thật hoặc embedded broker.
- [ ] Tôi biết Spring Kafka không tự đảm bảo exactly-once/idempotency.

## 32. Câu chốt cần nhớ

Spring Kafka làm cho Kafka dễ dùng trong Spring Boot, nhưng không làm mất đi các quy luật nền tảng của Kafka.

Năm ý cần nhớ:

1. KafkaTemplate chỉ gửi event; phải kiểm tra future/callback để biết kết quả.
2. @KafkaListener chạy qua container, nhưng offset/ack vẫn cần commit strategy rõ.
3. Concurrency chỉ tạo parallelism trong giới hạn số partition.
4. Event DTO phải tách khỏi JPA entity và được deserialize trong boundary an toàn.
5. Mỗi workload nên có listener factory/config riêng thay vì một cấu hình dùng cho mọi consumer.


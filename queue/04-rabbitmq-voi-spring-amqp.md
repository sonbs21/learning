# Tuần 4: RabbitMQ với Spring AMQP

## 1. Mục tiêu bài học

Sau bài học này, bạn có thể:

- Cấu hình Spring Boot kết nối RabbitMQ.
- Dùng RabbitTemplate để publish message.
- Dùng RabbitListener để consume message.
- Khai báo exchange, queue và binding bằng Spring bean.
- Cấu hình JSON message converter.
- Thiết kế event envelope có eventId, eventType, eventVersion, occurredAt, aggregateId và payload.
- Phân biệt automatic acknowledgement với manual acknowledgement.
- Hiểu listener container.
- Điều chỉnh prefetch và consumer concurrency.
- Xây POST /orders phát OrderCreated.
- Chạy hai consumer flow độc lập: notification và audit.
- Thực hiện graceful shutdown.
- Viết integration test với broker thật.

Tuần 3 học AMQP model. Tuần 4 dùng Spring để hiện thực model đó. Spring không thay đổi bản chất routing của RabbitMQ.

---

## 2. Bài toán thực hành

Xây Spring Boot application:

    POST /orders
        |
        v
    Order service
        |
        | publish OrderCreated
        v
    order.events exchange
        ├── order.notification queue -> Notification consumer
        └── order.audit queue        -> Audit consumer

Yêu cầu:

- POST /orders nhận customer và danh sách item.
- Order service tạo orderId.
- Service phát OrderCreated.
- Notification consumer ghi log gửi notification.
- Audit consumer ghi audit record.
- Hai consumer là hai flow độc lập.
- Một consumer lỗi không ngăn queue của consumer còn lại.
- Không ack trước khi business handler xử lý xong.
- Có eventId và correlationId.

Nếu hai consumer dùng chung một queue, chúng sẽ competing consumers. Mỗi message chỉ được giao cho một consumer. Muốn broadcast, phải dùng queue riêng cho từng flow.

---

## 3. Thành phần chính của Spring AMQP

- RabbitTemplate: gửi message.
- RabbitListener: khai báo listener.
- RabbitAdmin: declare topology từ bean.
- RabbitListenerContainerFactory: tạo listener container.
- MessageConverter: chuyển Java object thành AMQP message và ngược lại.
- ConnectionFactory: quản lý connection.
- Listener container: nhận message, gọi listener, quản lý ack, concurrency và lifecycle.

Flow publish:

    OrderService
        -> RabbitTemplate
        -> MessageConverter
        -> exchange
        -> binding
        -> queue

Flow consume:

    queue
        -> listener container
        -> MessageConverter
        -> @RabbitListener method
        -> business handler
        -> acknowledgement

Spring Boot cung cấp starter amqp và cấu hình RabbitMQ qua nhóm thuộc tính spring.rabbitmq.

---

## 4. Khởi tạo project

### 4.1. Dependency Maven

    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-amqp</artifactId>
    </dependency>

    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>

    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>

    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>

### 4.2. Cấu trúc package

    src/main/java/com/example/orders
        OrderApplication.java
        api/
        config/
        domain/
        messaging/
        service/

Nên tổ chức theo feature/domain để topology, publisher, consumer và nghiệp vụ dễ tìm.

---

## 5. Chạy RabbitMQ bằng Docker Compose

File docker-compose.rabbitmq.yml:

    services:
      rabbitmq:
        image: rabbitmq:4-management
        container_name: learning-rabbitmq
        ports:
          - "5672:5672"
          - "15672:15672"
        environment:
          RABBITMQ_DEFAULT_USER: learning
          RABBITMQ_DEFAULT_PASS: learning-pass
        volumes:
          - rabbitmq-data:/var/lib/rabbitmq

    volumes:
      rabbitmq-data:

Chạy:

    docker compose -f docker-compose.rabbitmq.yml up -d

Management UI:

    http://localhost:15672

Cấu hình local:

    host: localhost
    port: 5672
    username: learning
    password: learning-pass
    virtual host: /

Password mẫu chỉ dành cho lab. Production phải dùng environment variable hoặc secret manager.

---

## 6. Cấu hình connection

Ví dụ application.yml:

    spring:
      rabbitmq:
        host: localhost
        port: 5672
        username: learning
        password: learning-pass
        virtual-host: /
        connection-timeout: 5s
        listener:
          simple:
            acknowledge-mode: auto
            prefetch: 10
            concurrency: 1
            max-concurrency: 3
            default-requeue-rejected: false

Ý nghĩa:

- connection-timeout: thời gian tối đa mở connection.
- acknowledge-mode: mode ack mặc định.
- prefetch: số message chưa ack có thể outstanding trên mỗi consumer.
- concurrency: số consumer ban đầu.
- max-concurrency: số consumer tối đa.
- default-requeue-rejected: behavior mặc định khi listener throw exception.

Tách config theo môi trường:

    application-local.yml
        host: localhost
        port: 5672
        vhost: /

    application-production.yml
        addresses: amqps://rabbitmq.internal
        username: lấy từ secret manager
        password: lấy từ secret manager
        vhost: /orders-prod
        tls: enabled

Không commit password production vào repository.

---

## 7. Khai báo topology

### 7.1. Constants

    public final class RabbitTopology {
        public static final String EXCHANGE = "order.events";
        public static final String NOTIFICATION_QUEUE =
            "order.notification";
        public static final String AUDIT_QUEUE = "order.audit";
        public static final String ORDER_CREATED = "order.created";

        private RabbitTopology() {
        }
    }

### 7.2. Exchange

    @Configuration
    public class RabbitTopologyConfig {

        @Bean
        public TopicExchange orderEventsExchange() {
            return ExchangeBuilder
                .topicExchange(RabbitTopology.EXCHANGE)
                .durable(true)
                .build();
        }
    }

Topic exchange phù hợp vì routing key có dạng order.created, order.paid và order.cancelled.

### 7.3. Queue

    @Bean
    public Queue notificationQueue() {
        return QueueBuilder
            .durable(RabbitTopology.NOTIFICATION_QUEUE)
            .build();
    }

    @Bean
    public Queue auditQueue() {
        return QueueBuilder
            .durable(RabbitTopology.AUDIT_QUEUE)
            .build();
    }

Business queue production thường:

    durable = true
    exclusive = false
    autoDelete = false

### 7.4. Binding

    @Bean
    public Binding notificationBinding(
            Queue notificationQueue,
            TopicExchange orderEventsExchange) {

        return BindingBuilder
            .bind(notificationQueue)
            .to(orderEventsExchange)
            .with(RabbitTopology.ORDER_CREATED);
    }

    @Bean
    public Binding auditBinding(
            Queue auditQueue,
            TopicExchange orderEventsExchange) {

        return BindingBuilder
            .bind(auditQueue)
            .to(orderEventsExchange)
            .with("order.#");
    }

Topology:

    order.events exchange
        ├── order.notification <- order.created
        └── order.audit        <- order.#

Notification chỉ nhận OrderCreated. Audit nhận các event thuộc order domain.

### 7.5. Topology mismatch

Khi startup:

- Entity chưa tồn tại thì tạo.
- Entity đã tồn tại cùng thuộc tính thì dùng lại.
- Entity đã tồn tại khác thuộc tính thì fail rõ ràng.

Không declare exchange order.events là topic ở lần đầu rồi đổi thành direct ở lần sau. Đây là breaking change. Tạo exchange mới và migration có kế hoạch.

---

## 8. JSON message converter

### 8.1. Vì sao cần converter?

Không nên để payload phụ thuộc Java serialization. JSON giúp:

- Consumer khác ngôn ngữ đọc được.
- Debug qua Management UI dễ hơn.
- Contract rõ hơn.
- Replay và lưu trữ thuận tiện hơn.

### 8.2. Cấu hình

    @Configuration
    public class RabbitMessagingConfig {

        @Bean
        public Jackson2JsonMessageConverter jsonMessageConverter(
                ObjectMapper objectMapper) {

            return new Jackson2JsonMessageConverter(objectMapper);
        }

        @Bean
        public RabbitTemplate rabbitTemplate(
                ConnectionFactory connectionFactory,
                Jackson2JsonMessageConverter converter) {

            RabbitTemplate template = new RabbitTemplate(connectionFactory);
            template.setMessageConverter(converter);
            return template;
        }

        @Bean
        public SimpleRabbitListenerContainerFactory
                rabbitListenerContainerFactory(
                    ConnectionFactory connectionFactory,
                    Jackson2JsonMessageConverter converter) {

            SimpleRabbitListenerContainerFactory factory =
                new SimpleRabbitListenerContainerFactory();

            factory.setConnectionFactory(connectionFactory);
            factory.setMessageConverter(converter);
            factory.setAcknowledgeMode(AcknowledgeMode.AUTO);
            factory.setPrefetchCount(10);
            factory.setConcurrentConsumers(1);
            factory.setMaxConcurrentConsumers(3);
            return factory;
        }
    }

Tên class converter tùy version Spring AMQP. Khi nâng version, kiểm tra API của version đang dùng.

### 8.3. Type metadata

Spring có thể đưa type metadata vào header. Điều này tiện trong một ứng dụng Java nhưng tạo coupling nếu:

- Consumer viết bằng ngôn ngữ khác.
- Java package bị đổi.
- Class cũ bị xóa.
- Message cần replay sau nhiều phiên bản.

Event contract nên dựa vào eventType và eventVersion trong envelope, không phụ thuộc duy nhất vào Java class name.

---

## 9. Event envelope

### 9.1. Envelope và payload

    public record OrderEventEnvelope<T>(
        String eventId,
        String eventType,
        int eventVersion,
        Instant occurredAt,
        String aggregateId,
        String correlationId,
        T payload
    ) {
    }

    public record OrderCreatedPayload(
        String orderId,
        String customerId,
        List<OrderItemPayload> items,
        BigDecimal totalAmount
    ) {
    }

    public record OrderItemPayload(
        String sku,
        int quantity,
        BigDecimal unitPrice
    ) {
    }

### 9.2. Ý nghĩa field

- eventId: unique event identifier.
- eventType: OrderCreated.
- eventVersion: version schema.
- occurredAt: thời điểm business event xảy ra.
- aggregateId: orderId.
- correlationId: nối HTTP request với workflow.
- payload: business data.

Message properties có thể thêm:

    contentType = application/json
    messageId = eventId
    correlationId = requestId
    type = OrderCreated

Business contract vẫn nên đủ trong body để message dễ kiểm tra và replay.

---

## 10. RabbitTemplate

### 10.1. Publish object

    @Service
    public class OrderEventPublisher {
        private final RabbitTemplate rabbitTemplate;

        public OrderEventPublisher(RabbitTemplate rabbitTemplate) {
            this.rabbitTemplate = rabbitTemplate;
        }

        public void publishOrderCreated(
                OrderEventEnvelope<OrderCreatedPayload> event) {

            rabbitTemplate.convertAndSend(
                RabbitTopology.EXCHANGE,
                RabbitTopology.ORDER_CREATED,
                event
            );
        }
    }

RabbitTemplate thực hiện:

1. Nhận Java object.
2. Gọi converter.
3. Tạo AMQP message.
4. Publish tới exchange.
5. Dùng routing key order.created.

### 10.2. Gắn properties và headers

    public void publishOrderCreated(
            OrderEventEnvelope<OrderCreatedPayload> event) {

        rabbitTemplate.convertAndSend(
            RabbitTopology.EXCHANGE,
            RabbitTopology.ORDER_CREATED,
            event,
            message -> {
                message.getMessageProperties()
                    .setMessageId(event.eventId());

                message.getMessageProperties()
                    .setCorrelationId(event.correlationId());

                message.getMessageProperties()
                    .setHeader("eventType", event.eventType());

                message.getMessageProperties()
                    .setHeader("eventVersion", event.eventVersion());

                return message;
            }
        );
    }

Đoạn này chưa bật publisher confirm. Confirm và mandatory publish thuộc reliability của Tuần 5.

### 10.3. RabbitTemplate không giải quyết dual-write

RabbitTemplate không tự đảm bảo:

- Consumer xử lý thành công.
- Không có duplicate.
- Database và broker commit atomic.
- Retry đúng policy.
- Message luôn được route.

Nếu database lưu order thành công nhưng publish fail, trạng thái database và broker lệch nhau. Outbox sẽ giải quyết bài toán này ở Tuần 14.

---

## 11. POST /orders

### 11.1. Request DTO

    public record CreateOrderRequest(
        @NotBlank String customerId,
        @NotEmpty List<CreateOrderItemRequest> items
    ) {
    }

    public record CreateOrderItemRequest(
        @NotBlank String sku,
        @Min(1) int quantity,
        @DecimalMin("0.01") BigDecimal unitPrice
    ) {
    }

### 11.2. Response DTO

    public record CreateOrderResponse(
        String orderId,
        String status
    ) {
    }

### 11.3. Controller

    @RestController
    @RequestMapping("/orders")
    public class OrderController {
        private final OrderService orderService;

        public OrderController(OrderService orderService) {
            this.orderService = orderService;
        }

        @PostMapping
        public ResponseEntity<CreateOrderResponse> create(
                @Valid @RequestBody CreateOrderRequest request,
                @RequestHeader(
                    value = "X-Correlation-Id",
                    required = false
                ) String correlationId) {

            CreateOrderResponse response =
                orderService.create(request, correlationId);

            return ResponseEntity.status(HttpStatus.CREATED)
                .body(response);
        }
    }

Validate ở boundary:

- customerId không rỗng.
- items không rỗng.
- quantity lớn hơn zero.
- unitPrice hợp lệ.
- Không tin dữ liệu từ client.
- Không log toàn bộ payload nhạy cảm.

### 11.4. Service

    @Service
    public class OrderService {
        private final OrderEventPublisher publisher;

        public OrderService(OrderEventPublisher publisher) {
            this.publisher = publisher;
        }

        public CreateOrderResponse create(
                CreateOrderRequest request,
                String correlationId) {

            String orderId = UUID.randomUUID().toString();

            OrderCreatedPayload payload =
                new OrderCreatedPayload(
                    orderId,
                    request.customerId(),
                    request.items().stream()
                        .map(item -> new OrderItemPayload(
                            item.sku(),
                            item.quantity(),
                            item.unitPrice()))
                        .toList(),
                    calculateTotal(request.items())
                );

            OrderEventEnvelope<OrderCreatedPayload> event =
                new OrderEventEnvelope<>(
                    UUID.randomUUID().toString(),
                    "OrderCreated",
                    1,
                    Instant.now(),
                    orderId,
                    correlationId,
                    payload
                );

            publisher.publishOrderCreated(event);

            return new CreateOrderResponse(orderId, "CREATED");
        }
    }

Đoạn code này phục vụ learning flow. Production nên persist order và outbox trong cùng database transaction trước khi publisher gửi message.

---

## 12. RabbitListener

### 12.1. Notification consumer

    @Component
    public class NotificationConsumer {
        private final NotificationService service;

        public NotificationConsumer(NotificationService service) {
            this.service = service;
        }

        @RabbitListener(
            queues = RabbitTopology.NOTIFICATION_QUEUE,
            containerFactory = "rabbitListenerContainerFactory"
        )
        public void handle(
                OrderEventEnvelope<OrderCreatedPayload> event) {

            service.sendOrderCreated(event.payload());
        }
    }

### 12.2. Audit consumer

    @Component
    public class AuditConsumer {
        private final AuditService service;

        public AuditConsumer(AuditService service) {
            this.service = service;
        }

        @RabbitListener(
            queues = RabbitTopology.AUDIT_QUEUE,
            containerFactory = "rabbitListenerContainerFactory"
        )
        public void handle(
                OrderEventEnvelope<OrderCreatedPayload> event) {

            service.record(
                event.eventId(),
                event.eventType(),
                event.aggregateId(),
                event.occurredAt()
            );
        }
    }

Hai listener dùng hai queue riêng. Vì vậy mỗi flow nhận một bản OrderCreated độc lập.

### 12.3. Nhận raw Message khi cần metadata

    @RabbitListener(queues = RabbitTopology.AUDIT_QUEUE)
    public void handle(
            Message message,
            OrderEventEnvelope<OrderCreatedPayload> event) {

        String messageId = message.getMessageProperties()
            .getMessageId();

        System.out.println("Message id: " + messageId);
        System.out.println("Order: " + event.payload().orderId());
    }

Dùng raw Message khi cần headers, properties hoặc redelivered flag. Dùng typed payload khi business handler không cần transport detail.

---

## 13. Acknowledgement

### 13.1. NONE hoặc auto ack

Broker coi message đã ack ngay khi deliver. Process chết giữa lúc nhận và xử lý có thể làm message mất khỏi queue.

Dùng cho telemetry không quan trọng hoặc use case chấp nhận at-most-once. Không dùng mặc định cho order/payment.

### 13.2. AUTO

Container ack khi listener return thành công. Nếu listener throw exception, error handler quyết định reject hoặc requeue.

Flow thành công:

    message delivered
        -> listener starts
        -> business processing succeeds
        -> listener returns
        -> container ack

Flow lỗi:

    message delivered
        -> listener throws
        -> error handler decides
        -> reject or requeue

### 13.3. MANUAL

Cấu hình factory:

    factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);

Listener:

    @RabbitListener(
        queues = RabbitTopology.NOTIFICATION_QUEUE,
        containerFactory = "manualRabbitListenerContainerFactory"
    )
    public void handle(
            OrderEventEnvelope<OrderCreatedPayload> event,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag)
            throws IOException {

        processNotification(event);

        channel.basicAck(deliveryTag, false);
    }

Manual ack phù hợp khi cần kiểm soát chính xác thời điểm ack.

Rủi ro:

- Quên ack làm unacked message tăng.
- Ack hai lần.
- Ack sai channel.
- Ack trước side effect.
- Async task chưa xong nhưng đã ack.

### 13.4. Quy tắc

Ack sau khi business side effect cần thiết hoàn tất.

Không ack chỉ vì:

- Đã deserialize JSON.
- Đã nhận message.
- Đã log message.
- Đã gọi downstream nhưng chưa biết kết quả.
- Đã submit task vào executor nhưng task còn chạy.

---

## 14. Error handling cơ bản

Cấu hình đơn giản cho lab:

    @Bean
    public SimpleRabbitListenerContainerFactory
            rabbitListenerContainerFactory(
                ConnectionFactory connectionFactory,
                Jackson2JsonMessageConverter converter) {

        SimpleRabbitListenerContainerFactory factory =
            new SimpleRabbitListenerContainerFactory();

        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(converter);
        factory.setAcknowledgeMode(AcknowledgeMode.AUTO);
        factory.setDefaultRequeueRejected(false);
        factory.setPrefetchCount(10);

        return factory;
    }

defaultRequeueRejected false có thể ngăn message lỗi quay lại vô hạn. Tuần 5 sẽ thiết kế retry queue và DLQ đầy đủ.

Không requeue mọi exception vô hạn. Poison message có thể tạo hot loop và làm queue không bao giờ giảm.

---

## 15. Listener container

RabbitListener là annotation. Listener container thực hiện:

- Mở consumer.
- Nhận message.
- Chuyển message qua converter.
- Gọi listener method.
- Quản lý acknowledgement.
- Xử lý exception.
- Tạo consumer theo concurrency.
- Phục hồi connection sau lỗi.
- Dừng khi application shutdown.

### 15.1. SimpleMessageListenerContainer

Phù hợp cho use case phổ biến:

- Một hoặc nhiều consumer.
- Concurrency fixed hoặc dynamic.
- Prefetch.
- Acknowledge mode.
- Error handling.
- Lifecycle control.

### 15.2. Tách factory theo flow

Notification có thể cần throughput cao. Audit có thể cần ordering nghiêm hơn.

    @RabbitListener(
        queues = RabbitTopology.NOTIFICATION_QUEUE,
        containerFactory = "notificationListenerFactory"
    )

    @RabbitListener(
        queues = RabbitTopology.AUDIT_QUEUE,
        containerFactory = "auditListenerFactory"
    )

Không dùng một factory cho mọi listener nếu SLA và failure policy khác nhau.

---

## 16. Prefetch

Prefetch là số message chưa ack mà broker có thể giao trước cho mỗi consumer.

Prefetch thấp:

    prefetch = 1

Ưu điểm:

- Phân phối công bằng hơn.
- Ít message nằm trong client.
- Dễ kiểm soát ordering tương đối.

Nhược điểm:

- Throughput có thể thấp.
- Round-trip và processing latency ảnh hưởng nhiều hơn.

Prefetch cao:

    prefetch = 50

Ưu điểm:

- Có thể tăng throughput.
- Consumer ít chờ broker hơn.

Nhược điểm:

- Memory client tăng.
- Một consumer giữ nhiều message.
- Fairness giảm.
- Redelivery batch có thể lớn.
- Ordering quan sát được phức tạp hơn.

Bắt đầu với:

    prefetch = 10

Sau đó benchmark với message size, processing time, queue depth, unacked messages và p95 latency.

Prefetch không làm business handler nhanh hơn và không thay thế scale consumer.

---

## 17. Consumer concurrency

### 17.1. Fixed concurrency

    factory.setConcurrentConsumers(2);

Có hai consumer cùng đọc queue.

### 17.2. Dynamic range

    factory.setConcurrentConsumers(1);
    factory.setMaxConcurrentConsumers(5);

Container có thể tăng consumer theo workload.

### 17.3. Tại listener

    @RabbitListener(
        queues = RabbitTopology.NOTIFICATION_QUEUE,
        concurrency = "2-5"
    )
    public void handle(OrderEventEnvelope<OrderCreatedPayload> event) {
        processNotification(event);
    }

### 17.4. Khi tăng concurrency

Chỉ tăng khi:

- Handler có thể chạy song song.
- Side effect idempotent.
- Downstream chịu được traffic.
- Ordering không cần strict toàn queue.
- Database pool đủ.
- Rate limit đủ.
- Metrics chứng minh cần scale.

Nếu database có 5 connection mà tạo 50 consumer, bottleneck chỉ chuyển sang database.

Concurrency cũng thay đổi behavior: thứ tự hoàn thành có thể khác thứ tự publish.

---

## 18. Graceful shutdown

Khi shutdown:

1. Ngừng nhận message mới.
2. Cho message đang xử lý hoàn tất trong timeout.
3. Ack message đã xử lý thành công.
4. Để message chưa xử lý được redeliver theo policy.
5. Dừng listener container.
6. Đóng connection và channel.

Ví dụ:

    spring:
      lifecycle:
        timeout-per-shutdown-phase: 30s
      rabbitmq:
        listener:
          simple:
            shutdown-timeout: 10000ms
            force-stop: false

Property cụ thể có thể thay đổi theo Spring Boot version. Kiểm tra reference documentation của version đang dùng.

### 18.1. Vì sao vẫn cần idempotency?

Process có thể chết sau side effect nhưng trước ack:

    side effect thành công
        -> process crash
        -> chưa ack
        -> message redelivered
        -> side effect chạy lại

Graceful shutdown giảm trường hợp dang dở nhưng không loại bỏ crash. Consumer vẫn cần idempotency.

### 18.2. Kubernetes

- Readiness fail trước khi terminate.
- termination grace period đủ dài.
- PreStop có thể hỗ trợ drain.
- Liveness không kill container chỉ vì queue đang có tải.
- Monitor active consumers và unacked messages.

---

## 19. Idempotency sơ bộ

Ack không ngăn duplicate tuyệt đối.

Bảng đơn giản:

    processed_message
        message_id primary key
        consumer_name
        processed_at

Flow:

    bắt đầu transaction
        insert eventId, consumerName
        nếu duplicate:
            bỏ qua side effect
        nếu mới:
            thực hiện side effect
            commit

Với audit, eventId thường là idempotency key tốt.

Với notification, có thể dùng eventId hoặc orderId cộng notification type.

Outbox, Inbox và distributed consistency sẽ học sâu ở Tuần 14.

---

## 20. Integration test

### 20.1. Cần test gì?

- Application khởi động khi broker chạy.
- Exchange, queue và binding được declare.
- POST /orders tạo event.
- Notification queue nhận OrderCreated.
- Audit queue nhận OrderCreated.
- Event có đúng eventType và eventVersion.
- aggregateId khớp orderId.
- correlationId được truyền.
- Hai queue nhận độc lập.
- Listener xử lý message.
- Exception behavior được kiểm tra.
- Shutdown không làm mất message ngoài policy.

### 20.2. Testcontainers

Ý tưởng:

    @Testcontainers
    @SpringBootTest
    class OrderMessagingIntegrationTest {

        @Container
        static RabbitMQContainer rabbit =
            new RabbitMQContainer("rabbitmq:4-management");

        @DynamicPropertySource
        static void rabbitProperties(
                DynamicPropertyRegistry registry) {

            registry.add(
                "spring.rabbitmq.host",
                rabbit::getHost
            );

            registry.add(
                "spring.rabbitmq.port",
                rabbit::getAmqpPort
            );

            registry.add(
                "spring.rabbitmq.username",
                rabbit::getAdminUsername
            );

            registry.add(
                "spring.rabbitmq.password",
                rabbit::getAdminPassword
            );
        }
    }

Tên class và API có thể thay đổi theo Testcontainers version. Kiểm tra module RabbitMQ tương ứng.

### 20.3. Test qua HTTP

Pseudo test:

    given()
        .contentType("application/json")
        .body(createOrderRequest())
    .when()
        .post("/orders")
    .then()
        .statusCode(201);

Sau đó consume từ notification queue và audit queue.

Assert:

- Mỗi queue có một OrderCreated.
- eventType = OrderCreated.
- aggregateId khớp orderId.
- correlationId đúng.
- payload chứa item hợp lệ.

### 20.4. Async test

Tránh sleep cố định dài. Dùng await có timeout:

    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> {
            assertThat(receivedEvents).hasSize(2);
        });

Test phải reset queue hoặc dùng topology unique để message từ test trước không ảnh hưởng test sau.

---

## 21. Bài thực hành

### Bài 1: Producer và hai consumer

Xây flow POST /orders -> RabbitTemplate -> order.events -> hai queue.

Tiêu chí:

- POST trả orderId.
- Hai queue nhận OrderCreated.
- Hai listener log eventId và orderId.
- Không dùng chung queue.

### Bài 2: JSON converter

1. Bật Jackson JSON converter.
2. Kiểm tra content type.
3. Consume vào typed envelope.
4. Thử thay đổi field không tương thích.
5. Ghi nhận lỗi deserialize và cách xử lý.

### Bài 3: Auto ack và manual ack

1. Chạy listener AUTO.
2. Throw exception sau business side effect.
3. Quan sát behavior.
4. Đổi MANUAL.
5. Chỉ ack sau side effect.
6. Kill process trước ack.
7. Kiểm tra redelivery.
8. Thêm idempotency guard.

### Bài 4: Prefetch

Chạy prefetch 1, 10 và 50. Ghi lại throughput, p95 latency, ready messages, unacked messages và redelivery.

### Bài 5: Concurrency

Chạy notification consumer với concurrency 1, 2 và 5. Giữ message rate cố định. So sánh throughput, database pool, ordering và queue depth.

### Bài 6: Graceful shutdown

1. Publish nhiều message.
2. Làm handler mất vài giây.
3. Gửi SIGTERM.
4. Kiểm tra listener drain.
5. Kiểm tra message ack.
6. Kiểm tra message chưa xong có redeliver không.
7. Kiểm tra duplicate side effect.

---

## 22. Common mistakes

### Mistake 1: Dùng một queue cho hai subscriber

Notification và audit cạnh tranh. Dùng queue riêng.

### Mistake 2: Ack trước khi xử lý

Message biến mất dù handler chưa hoàn tất.

### Mistake 3: Nghĩ RabbitTemplate là transaction

RabbitTemplate không làm database và broker commit atomic.

### Mistake 4: Dùng Java serialization

Tạo coupling vào Java class và package. Ưu tiên JSON contract.

### Mistake 5: Converter không đồng nhất

Producer gửi JSON nhưng consumer deserialize theo cấu hình khác.

### Mistake 6: Dựa vào default prefetch

Default thay đổi theo version và workload. Cấu hình explicit rồi benchmark.

### Mistake 7: Tăng concurrency không giới hạn

Database, HTTP downstream hoặc rate limit có thể quá tải.

### Mistake 8: Requeue exception vô hạn

Poison message tạo hot loop. Tuần 5 sẽ xây retry và DLQ.

### Mistake 9: Bỏ qua topology mismatch

Queue hoặc exchange đã tồn tại khác thuộc tính. Startup có thể fail.

### Mistake 10: Shutdown bằng kill -9

Process không có cơ hội drain. Dùng SIGTERM và graceful lifecycle.

### Mistake 11: Log toàn bộ payload

Payload có thể chứa dữ liệu nhạy cảm. Log eventId, eventType, aggregateId và correlationId.

### Mistake 12: Không có correlation ID

Khó nối HTTP request với consumer logs và traces.

---

## 23. Observability tối thiểu

Publisher nên có:

- exchange.
- routing key.
- eventId.
- eventType.
- aggregateId.
- correlationId.
- publish duration.
- publish error.

Consumer nên có:

- queue.
- consumer name.
- eventId.
- eventType.
- aggregateId.
- processing duration.
- success count.
- failure count.
- redelivered flag.
- ack hoặc reject result.

Dashboard ban đầu:

- Ready messages.
- Unacked messages.
- Consumer count.
- Publish rate.
- Consume rate.
- Processing latency.
- Failure rate.
- Connection state.

---

## 24. Security checklist

- [ ] Credentials lấy từ environment hoặc secret manager.
- [ ] Không commit password production.
- [ ] Dùng vhost riêng theo environment.
- [ ] User có permission tối thiểu.
- [ ] Producer chỉ write resource cần thiết.
- [ ] Consumer chỉ read queue cần thiết.
- [ ] Bật TLS khi chạy qua network không tin cậy.
- [ ] Management UI không expose công khai.
- [ ] Validate request ở HTTP boundary.
- [ ] Không deserialize Java object tùy ý từ message không tin cậy.
- [ ] Không log payload nhạy cảm.
- [ ] Có giới hạn message size.
- [ ] Có timeout cho downstream.

---

## 25. Definition of Done

- [ ] Spring Boot app chạy được.
- [ ] RabbitMQ chạy bằng Docker Compose.
- [ ] Có exchange order.events.
- [ ] Có queue order.notification.
- [ ] Có queue order.audit.
- [ ] Có binding đúng routing key.
- [ ] POST /orders validate request.
- [ ] POST /orders trả orderId.
- [ ] RabbitTemplate phát OrderCreated.
- [ ] Message dùng JSON.
- [ ] Envelope có eventId.
- [ ] Envelope có eventType.
- [ ] Envelope có eventVersion.
- [ ] Envelope có occurredAt.
- [ ] Envelope có aggregateId.
- [ ] Envelope có payload.
- [ ] Notification consumer chạy riêng.
- [ ] Audit consumer chạy riêng.
- [ ] Hai consumer không dùng chung queue.
- [ ] Hiểu automatic acknowledgement.
- [ ] Thực hành manual acknowledgement.
- [ ] Không ack trước khi xử lý xong.
- [ ] Cấu hình prefetch explicit.
- [ ] Thử concurrency 1, 2 và 5.
- [ ] Có graceful shutdown.
- [ ] Có integration test với broker thật.
- [ ] Có log correlationId và eventId.
- [ ] Không hardcode production secret.

---

## 26. Câu hỏi tự kiểm tra

1. RabbitTemplate dùng để làm gì?
2. RabbitListener có trực tiếp mở socket không?
3. Listener container làm những việc nào?
4. Vì sao notification và audit cần queue riêng?
5. MessageConverter có vai trò gì?
6. Vì sao ưu tiên JSON event contract?
7. AUTO ack xảy ra khi nào?
8. MANUAL ack cần channel và delivery tag để làm gì?
9. Vì sao ack trước side effect nguy hiểm?
10. Prefetch cao có lợi và hại gì?
11. Khi nào nên giảm prefetch xuống 1?
12. Concurrency làm thay đổi behavior nào ngoài throughput?
13. Vì sao concurrency cao có thể làm database quá tải?
14. RabbitTemplate có atomic transaction với database không?
15. Vì sao cần eventId nếu RabbitMQ có delivery tag?
16. Vì sao correlationId khác eventId?
17. Graceful shutdown xử lý message đang chạy thế nào?
18. Vì sao crash sau side effect trước ack tạo duplicate?
19. Listener exception có nên requeue vô hạn không?
20. Topology mismatch gây hậu quả gì?
21. Vì sao broadcast subscriber cần queue riêng?
22. Integration test cần kiểm tra queue nào?
23. Vì sao tránh sleep dài trong async test?
24. Khi nào tách listener container factory?
25. Spring AMQP có thay đổi bản chất routing RabbitMQ không?

---

## 27. Chuẩn bị cho Tuần 5

Tuần 5 mở rộng app bằng:

- Consumer acknowledgement sâu hơn.
- Basic ack, nack và reject.
- Requeue.
- Publisher confirms.
- Mandatory publish.
- Durable exchange và queue.
- Persistent delivery.
- Dead Letter Exchange và Dead Letter Queue.
- Message TTL.
- Retry queue.
- Delayed retry.
- Quorum queue ở mức khái niệm.

Flow:

    main queue
        ↓ lỗi tạm thời
    retry queue 10s
        ↓
    retry queue 60s
        ↓ lỗi vĩnh viễn
    dead-letter queue

Tuần 4 chỉ cần đặt nền tảng: listener không ack trước khi xử lý xong và không che giấu exception.

---

## 28. Tóm tắt

- Spring Boot dùng starter AMQP để tích hợp RabbitMQ.
- RabbitTemplate publish object qua MessageConverter.
- RabbitListener khai báo business handler.
- Listener container quản lý consumer, conversion, ack, concurrency và lifecycle.
- Exchange, queue và binding nên được khai báo bằng bean.
- JSON converter giúp event dễ đọc và ít coupling hơn Java serialization.
- Envelope nên có eventId, eventType, eventVersion, occurredAt, aggregateId, correlationId và payload.
- Notification và audit cần hai queue riêng.
- AUTO ack ack khi listener thành công theo container policy.
- MANUAL ack kiểm soát thời điểm ack.
- Ack sau business side effect cần thiết.
- Prefetch là trade-off giữa throughput, fairness, memory và ordering.
- Concurrency tăng throughput nhưng có thể làm downstream quá tải.
- Graceful shutdown giảm message đang dang dở nhưng không loại bỏ duplicate.
- RabbitTemplate không giải quyết dual-write giữa database và broker.
- Tuần 5 sẽ bổ sung retry, confirms và DLQ.

---

## Tài liệu tham khảo

- Spring Boot AMQP: https://docs.spring.io/spring-boot/reference/messaging/amqp.html
- Spring AMQP reference: https://docs.spring.io/spring-amqp/reference/
- RabbitTemplate: https://docs.spring.io/spring-amqp/reference/amqp/template.html
- Message converters: https://docs.spring.io/spring-amqp/reference/amqp/message-converters.html
- Listener container configuration: https://docs.spring.io/spring-amqp/reference/amqp/containerAttributes.html
- Listener concurrency: https://docs.spring.io/spring-amqp/reference/amqp/listener-concurrency.html
- RabbitMQ AMQP concepts: https://www.rabbitmq.com/tutorials/amqp-concepts


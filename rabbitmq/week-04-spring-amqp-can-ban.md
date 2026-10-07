# Tuần 4 — Spring AMQP căn bản

> Mục tiêu của bài này là xây một producer/consumer RabbitMQ bằng Spring Boot mà không che khuất các khái niệm AMQP. Sau tuần này, bạn có thể khai báo topology bằng Java configuration, publish object JSON bằng RabbitTemplate, consume bằng @RabbitListener, kiểm tra message converter, cấu hình concurrency và truyền correlation ID qua headers.

## 1. Kết quả cần đạt

Sau bài học, bạn có thể:

- Thêm và cấu hình Spring AMQP trong Spring Boot.
- Kết nối ứng dụng tới host, port, username, password và virtual host.
- Khai báo Queue, Exchange và Binding bằng Java configuration.
- Dùng RabbitAdmin để declare topology khi ứng dụng khởi động.
- Dùng RabbitTemplate để publish String hoặc object JSON.
- Dùng @RabbitListener để consume message.
- Cấu hình Jackson message converter và content type.
- Tách message DTO khỏi REST DTO và entity JPA.
- Truyền correlation ID, message ID và custom headers.
- Tạo listener container factory với concurrency phù hợp.
- Viết test kiểm tra application context và topology declaration.
- Chẩn đoán lỗi deserialize, sai vhost, sai exchange type và listener không nhận message.

## 2. Spring AMQP đang đơn giản hóa điều gì?

Java client thuần phải tự quản lý:

~~~text
ConnectionFactory
  -> Connection
  -> Channel
  -> exchangeDeclare
  -> queueDeclare
  -> queueBind
  -> basicPublish
  -> basicConsume
~~~

Spring AMQP cung cấp các abstraction:

~~~text
RabbitTemplate             = publish/request-reply
RabbitAdmin/AmqpAdmin      = quản lý topology
MessageListenerContainer   = vòng đời consumer
@RabbitListener            = đăng ký listener
MessageConverter           = chuyển payload/header
ConnectionFactory bean     = kết nối RabbitMQ
~~~

Spring không xóa các vấn đề của RabbitMQ. Message vẫn có thể duplicate, consumer vẫn có thể crash, queue vẫn có thể đầy, broker vẫn có thể restart và JSON schema vẫn có thể không tương thích.

## 3. Cấu trúc ứng dụng

~~~text
spring-amqp-week-04/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/com/example/orders/
    │   │   ├── OrderApplication.java
    │   │   ├── config/
    │   │   │   ├── RabbitNames.java
    │   │   │   ├── RabbitTopologyConfig.java
    │   │   │   ├── RabbitMessageConfig.java
    │   │   │   └── RabbitListenerConfig.java
    │   │   ├── message/
    │   │   │   ├── OrderCreatedMessage.java
    │   │   │   └── OrderCreatedPublisher.java
    │   │   └── listener/
    │   │       └── OrderCreatedListener.java
    │   └── resources/application.yaml
    └── test/java/com/example/orders/
        └── RabbitTopologyTest.java
~~~

## 4. Dependency và cấu hình

### 4.1 Maven

Version của starter nên được Spring Boot dependency management quản lý:

~~~xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>SPRING_BOOT_VERSION</version>
    <relativePath/>
</parent>

<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-amqp</artifactId>
    </dependency>

    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>

    <dependency>
        <groupId>org.springframework.amqp</groupId>
        <artifactId>spring-rabbit-test</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
~~~

Không override amqp-client tùy tiện nếu chưa kiểm tra compatibility giữa Spring Boot, Spring AMQP và RabbitMQ Java client.

### 4.2 application.yaml local

~~~yaml
spring:
  application:
    name: order-service

  rabbitmq:
    host: localhost
    port: 5672
    username: dev
    password: dev-password
    virtual-host: /app
    connection-timeout: 5s
    requested-heartbeat: 30s

    listener:
      simple:
        acknowledge-mode: auto
        concurrency: 1
        max-concurrency: 3
        prefetch: 1

    template:
      mandatory: true
~~~

Trong production, map các giá trị này từ environment/secret manager thay vì commit password:

~~~yaml
spring:
  rabbitmq:
    host: RABBITMQ_HOST
    port: RABBITMQ_PORT
    username: RABBITMQ_USERNAME
    password: RABBITMQ_PASSWORD
    virtual-host: RABBITMQ_VHOST
~~~

Các nhóm config:

| Nhóm | Mục đích |
|---|---|
| host/port/credentials | Kết nối broker |
| connection-timeout | Timeout khi mở connection |
| requested-heartbeat | Phát hiện connection chết |
| listener.simple | Consumer container |
| template | Producer/RabbitTemplate |
| acknowledge-mode | Cách listener ack |

Tuần này có thể dùng auto ack để hiểu flow. Tuần 5 sẽ đi sâu manual ack, nack, reject và prefetch.

## 5. Khởi động application

~~~java
package com.example.orders;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class OrderApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrderApplication.class, args);
    }
}
~~~

Chạy:

~~~bash
./mvnw spring-boot:run
./mvnw clean test
~~~

Khi app khởi động, kiểm tra Management UI:

- connection của app xuất hiện;
- channel được mở;
- exchange/queue/binding được declare;
- consumer xuất hiện trên queue.

Nếu app start nhưng không có consumer, kiểm tra listener bean, package scan, queue name và connection log.

## 6. Khai báo topology bằng Java

### 6.1 Naming

~~~text
exchange: orders.events
queue: orders.email
routing key: order.created
~~~

Đặt constant ở một class riêng:

~~~java
package com.example.orders.config;

public final class RabbitNames {
    public static final String ORDERS_EVENTS_EXCHANGE =
            "orders.events";
    public static final String ORDERS_EMAIL_QUEUE =
            "orders.email";
    public static final String ORDER_CREATED_ROUTING_KEY =
            "order.created";

    private RabbitNames() {
    }
}
~~~

Không rải String literal topology ở controller, service, publisher và listener.

### 6.2 Exchange, queue và binding

~~~java
package com.example.orders.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitTopologyConfig {

    @Bean
    public TopicExchange ordersEventsExchange() {
        return new TopicExchange(
                RabbitNames.ORDERS_EVENTS_EXCHANGE,
                true,
                false
        );
    }

    @Bean
    public Queue ordersEmailQueue() {
        return QueueBuilder
                .durable(RabbitNames.ORDERS_EMAIL_QUEUE)
                .build();
    }

    @Bean
    public Binding orderCreatedToEmailBinding(
            Queue ordersEmailQueue,
            TopicExchange ordersEventsExchange
    ) {
        return BindingBuilder
                .bind(ordersEmailQueue)
                .to(ordersEventsExchange)
                .with(RabbitNames.ORDER_CREATED_ROUTING_KEY);
    }
}
~~~

Ý nghĩa:

~~~text
TopicExchange(name, durable, autoDelete)
QueueBuilder.durable(name)
Binding(queue, exchange, routingKey)
~~~

Queue nghiệp vụ thường có tên ổn định, durable, không exclusive và không auto-delete.

### 6.3 RabbitAdmin

Spring Boot thường auto-configure RabbitAdmin khi có ConnectionFactory. Có thể khai báo rõ:

~~~java
@Bean
public RabbitAdmin rabbitAdmin(
        ConnectionFactory connectionFactory
) {
    RabbitAdmin admin = new RabbitAdmin(connectionFactory);
    admin.setAutoStartup(true);
    return admin;
}
~~~

Flow startup:

~~~text
Spring context start
  -> ConnectionFactory
  -> RabbitAdmin
  -> declare exchange
  -> declare queue
  -> declare binding
  -> listener container start
~~~

Nếu cùng tên entity nhưng khác exchange type, durable hoặc auto-delete, channel có thể lỗi. Không giải quyết bằng cách tự động xóa topology production.

## 7. Message DTO

### 7.1 DTO message riêng

~~~java
package com.example.orders.message;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderCreatedMessage(
        String eventId,
        String orderId,
        BigDecimal totalAmount,
        Instant occurredAt,
        int schemaVersion
) {
}
~~~

### 7.2 Vì sao không serialize entity JPA?

Không gửi trực tiếp entity JPA vì:

- entity có thể chứa lazy relationship;
- thay đổi database model sẽ đổi message contract;
- entity có thể chứa field nhạy cảm;
- JSON có thể có vòng lặp relationship;
- consumer bị phụ thuộc persistence model;
- khó versioning.

Tách model:

~~~text
REST DTO       = contract với HTTP client
Domain model   = business logic
Message DTO    = contract với consumer
JPA entity     = persistence model
~~~

## 8. JSON message converter

### 8.1 Jackson converter

~~~java
package com.example.orders.config;

import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMessageConfig {

    @Bean
    public MessageConverter rabbitMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
~~~

Converter thực hiện:

~~~text
Java object -> JSON bytes khi publish
JSON bytes  -> Java object khi consume
~~~

Nếu có ObjectMapper chung:

~~~java
@Bean
public Jackson2JsonMessageConverter rabbitMessageConverter(
        ObjectMapper objectMapper
) {
    return new Jackson2JsonMessageConverter(objectMapper);
}
~~~

Không bật deserialization tùy ý cho mọi class từ message header. Chỉ cho phép type/package hợp lệ.

### 8.2 RabbitTemplate

~~~java
@Bean
public RabbitTemplate rabbitTemplate(
        ConnectionFactory connectionFactory,
        MessageConverter messageConverter
) {
    RabbitTemplate template = new RabbitTemplate(connectionFactory);
    template.setMessageConverter(messageConverter);
    template.setMandatory(true);
    return template;
}
~~~

Một số Spring Boot version tự gắn converter bean, nhưng khai báo rõ giúp config dễ đọc và test dễ hơn.

## 9. RabbitTemplate

### 9.1 Publish object

~~~java
package com.example.orders.message;

import com.example.orders.config.RabbitNames;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class OrderCreatedPublisher {
    private final RabbitTemplate rabbitTemplate;

    public OrderCreatedPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publish(OrderCreatedMessage message) {
        rabbitTemplate.convertAndSend(
                RabbitNames.ORDERS_EVENTS_EXCHANGE,
                RabbitNames.ORDER_CREATED_ROUTING_KEY,
                message
        );
    }
}
~~~

Với Jackson converter, object được serialize thành JSON.

### 9.2 Publish với headers

~~~java
public void publish(
        OrderCreatedMessage message,
        String correlationId
) {
    rabbitTemplate.convertAndSend(
            RabbitNames.ORDERS_EVENTS_EXCHANGE,
            RabbitNames.ORDER_CREATED_ROUTING_KEY,
            message,
            outbound -> {
                outbound.getMessageProperties()
                        .setCorrelationId(correlationId);
                outbound.getMessageProperties()
                        .setMessageId(message.eventId());
                outbound.getMessageProperties()
                        .setHeader("x-source-service", "order-service");
                outbound.getMessageProperties()
                        .setHeader("x-schema-version", 1);
                return outbound;
            }
    );
}
~~~

Correlation ID nối log producer, broker và consumer. Không đưa password, token hoặc dữ liệu nhạy cảm vào headers.

### 9.3 Publish String

~~~java
rabbitTemplate.convertAndSend(
        RabbitNames.ORDERS_EVENTS_EXCHANGE,
        RabbitNames.ORDER_CREATED_ROUTING_KEY,
        "{\"orderId\":\"order-100\"}"
);
~~~

Trong domain code nên dùng DTO typed thay vì JSON string rải khắp nơi.

## 10. Consumer bằng @RabbitListener

### 10.1 Listener cơ bản

~~~java
package com.example.orders.listener;

import com.example.orders.config.RabbitNames;
import com.example.orders.message.OrderCreatedMessage;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class OrderCreatedListener {

    @RabbitListener(queues = RabbitNames.ORDERS_EMAIL_QUEUE)
    public void handle(OrderCreatedMessage message) {
        System.out.println(
                "Sending email for order " + message.orderId()
        );
    }
}
~~~

Listener container sẽ:

- mở connection/channel;
- subscribe queue;
- nhận message;
- gọi method;
- xử lý ack/error theo config.

### 10.2 Nhận payload và metadata

~~~java
@RabbitListener(queues = RabbitNames.ORDERS_EMAIL_QUEUE)
public void handle(
        OrderCreatedMessage payload,
        Message rawMessage
) {
    String correlationId = rawMessage.getMessageProperties()
            .getCorrelationId();

    System.out.println("correlationId=" + correlationId);
    System.out.println("orderId=" + payload.orderId());
}
~~~

Log correlation ID, event ID và order ID theo policy; không log toàn bộ payload nhạy cảm.

### 10.3 Dùng property

Nếu cần đổi queue theo environment, dùng property được quản lý rõ ràng:

~~~java
@RabbitListener(queues = "ORDERS_EMAIL_QUEUE")
public void handle(OrderCreatedMessage message) {
    // ...
}
~~~

Cách tốt hơn là dùng constant hoặc cấu hình bean topology thống nhất, tránh annotation và YAML lệch nhau.

## 11. Listener container factory

### 11.1 Factory cơ bản

~~~java
@Bean
public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
        SimpleRabbitListenerContainerFactoryConfigurer configurer,
        ConnectionFactory connectionFactory,
        MessageConverter messageConverter
) {
    SimpleRabbitListenerContainerFactory factory =
            new SimpleRabbitListenerContainerFactory();

    configurer.configure(factory, connectionFactory);
    factory.setMessageConverter(messageConverter);
    factory.setConcurrentConsumers(1);
    factory.setMaxConcurrentConsumers(3);
    factory.setPrefetchCount(1);
    return factory;
}
~~~

Dùng factory:

~~~java
@RabbitListener(
        queues = RabbitNames.ORDERS_EMAIL_QUEUE,
        containerFactory = "rabbitListenerContainerFactory"
)
public void handle(OrderCreatedMessage message) {
    // ...
}
~~~

### 11.2 Concurrency

Có thể cấu hình:

~~~java
@RabbitListener(
        queues = RabbitNames.ORDERS_EMAIL_QUEUE,
        concurrency = "2-4"
)
public void handle(OrderCreatedMessage message) {
    // ...
}
~~~

2 là số consumer khởi đầu, 4 là số tối đa. Concurrency cao có thể:

- làm downstream quá tải;
- tăng số channel/consumer;
- làm thứ tự hoàn tất thay đổi;
- tăng số message đang xử lý;
- tăng cạnh tranh database.

Không tăng concurrency chỉ để queue depth giảm. Đo throughput, processing time, error rate và downstream capacity.

### 11.3 Prefetch nhập môn

Prefetch là số message consumer có thể nhận trước khi ack. Prefetch thấp giới hạn in-flight message; prefetch cao có thể tăng throughput nhưng giữ nhiều message ở một consumer.

Tuần này chỉ cấu hình nhỏ để quan sát. Tuần 5 sẽ phân tích prefetch cùng manual ack và competing consumers.

## 12. Acknowledgement trong Spring AMQP

Cấu hình khởi đầu:

~~~yaml
spring:
  rabbitmq:
    listener:
      simple:
        acknowledge-mode: auto
~~~

Trong auto mode, container ack khi listener method hoàn tất bình thường; nếu method ném exception, container xử lý theo error/requeue config.

Giới hạn cần nhớ:

- method return không luôn có nghĩa external side effect an toàn;
- ack không rollback email/payment/HTTP;
- exception bị bắt và nuốt có thể khiến container nghĩ xử lý thành công;
- retry/requeue vô hạn tạo poison-message loop.

Không viết:

~~~java
@RabbitListener(queues = RabbitNames.ORDERS_EMAIL_QUEUE)
public void handle(OrderCreatedMessage message) {
    try {
        sendEmail(message);
    } catch (Exception exception) {
        log.error("send failed", exception);
    }
}
~~~

Đoạn này nuốt lỗi. Hãy để exception nổi lên hoặc ném exception domain:

~~~java
@RabbitListener(queues = RabbitNames.ORDERS_EMAIL_QUEUE)
public void handle(OrderCreatedMessage message) {
    sendEmail(message);
}
~~~

Retry và DLQ hữu hạn sẽ được thiết kế ở các tuần sau.

## 13. Correlation ID và headers

Correlation ID nối các log thuộc cùng request/workflow:

~~~text
HTTP request
  correlationId=trace-123
      |
      +-- publish OrderCreated
      |
      +-- email consumer
      |
      +-- SMTP call
~~~

Publish:

~~~java
rabbitTemplate.convertAndSend(
        RabbitNames.ORDERS_EVENTS_EXCHANGE,
        RabbitNames.ORDER_CREATED_ROUTING_KEY,
        message,
        outbound -> {
            outbound.getMessageProperties()
                    .setCorrelationId(correlationId);
            outbound.getMessageProperties()
                    .setMessageId(message.eventId());
            outbound.getMessageProperties()
                    .setHeader("x-service", "order-service");
            return outbound;
        }
);
~~~

Consume:

~~~java
@RabbitListener(queues = RabbitNames.ORDERS_EMAIL_QUEUE)
public void handle(
        OrderCreatedMessage payload,
        Message rawMessage
) {
    var properties = rawMessage.getMessageProperties();

    String correlationId = properties.getCorrelationId();
    String messageId = properties.getMessageId();

    System.out.printf(
            "correlationId=%s messageId=%s eventId=%s%n",
            correlationId,
            messageId,
            payload.eventId()
    );
}
~~~

Phân biệt:

~~~text
eventId       = định danh event/message
messageId     = định danh transport message
correlationId = định danh workflow/trace
~~~

## 14. Error handling cơ bản

Nếu business thất bại, listener nên ném exception:

~~~java
@RabbitListener(queues = RabbitNames.ORDERS_EMAIL_QUEUE)
public void handle(OrderCreatedMessage message) {
    if (message.orderId() == null || message.orderId().isBlank()) {
        throw new IllegalArgumentException("orderId is required");
    }

    emailService.sendConfirmation(message);
}
~~~

Payload sai schema có thể fail trước khi method được gọi. Hãy xem log converter và error handler. Không retry vô hạn message không thể deserialize.

Có thể gắn error handler để log context:

~~~java
@Bean
public ErrorHandler rabbitErrorHandler() {
    return throwable ->
            System.err.println(
                    "Rabbit listener error: "
                            + throwable.getMessage()
            );
}
~~~

Production cần structured logging, metric và policy retry/DLQ rõ ràng. Không tự giải quyết bằng requeue vô hạn.

## 15. Lab 1 — Publish OrderCreated

Topology:

~~~text
orders.events (topic)
  └── order.created -> orders.email
~~~

Tạo CommandLineRunner tạm thời:

~~~java
@Bean
CommandLineRunner publishSample(OrderCreatedPublisher publisher) {
    return args -> {
        OrderCreatedMessage message = new OrderCreatedMessage(
                "evt-100",
                "order-100",
                new BigDecimal("99000"),
                Instant.now(),
                1
        );

        publisher.publish(message);
    };
}
~~~

Thực hiện:

1. Khởi động RabbitMQ.
2. Khởi động Spring Boot.
3. Mở Management UI.
4. Xác nhận topology được declare.
5. Xác nhận JSON message vào queue nếu chưa có consumer.
6. Dùng Get messages để quan sát payload và content type.
7. Xóa CommandLineRunner sau khi thử để tránh publish lại mỗi lần restart.

Ghi lại exchange, routing key, queue, content type và payload.

## 16. Lab 2 — Consume bằng @RabbitListener

1. Thêm OrderCreatedListener.
2. Khởi động app.
3. Publish một message.
4. Quan sát consumer count.
5. Kiểm tra log listener.
6. Kiểm tra ready giảm sau khi method return.
7. Gửi payload thiếu field và quan sát converter/business error.

Trả lời:

- Consumer xuất hiện ở connection/channel nào?
- Message rời ready khi nào?
- Nếu listener ném exception, queue thay đổi thế nào?
- Log có eventId và correlation ID không?

## 17. Lab 3 — Hai listener container

Tạo hai queue:

~~~text
orders.fast
orders.slow
~~~

Factory nhanh:

~~~java
@Bean
public SimpleRabbitListenerContainerFactory fastListenerFactory(
        ConnectionFactory connectionFactory,
        MessageConverter converter
) {
    SimpleRabbitListenerContainerFactory factory =
            new SimpleRabbitListenerContainerFactory();
    factory.setConnectionFactory(connectionFactory);
    factory.setMessageConverter(converter);
    factory.setConcurrentConsumers(2);
    factory.setMaxConcurrentConsumers(4);
    factory.setPrefetchCount(1);
    return factory;
}
~~~

Factory chậm:

~~~java
@Bean
public SimpleRabbitListenerContainerFactory slowListenerFactory(
        ConnectionFactory connectionFactory,
        MessageConverter converter
) {
    SimpleRabbitListenerContainerFactory factory =
            new SimpleRabbitListenerContainerFactory();
    factory.setConnectionFactory(connectionFactory);
    factory.setMessageConverter(converter);
    factory.setConcurrentConsumers(1);
    factory.setMaxConcurrentConsumers(1);
    factory.setPrefetchCount(1);
    return factory;
}
~~~

Listener chậm:

~~~java
@RabbitListener(
        queues = "orders.slow",
        containerFactory = "slowListenerFactory"
)
public void handleSlow(OrderCreatedMessage message)
        throws InterruptedException {
    Thread.sleep(1_000);
}
~~~

Quan sát channel/consumer count, processing rate, queue depth và thứ tự hoàn tất. Concurrency cao không luôn tốt vì bottleneck có thể chuyển sang database hoặc downstream.

## 18. Lab 4 — Correlation ID và custom headers

1. Tạo correlation ID trace-week4-001.
2. Publish OrderCreated với correlation ID.
3. Thêm header x-source-service=order-service.
4. Consumer log correlation ID, message ID, event ID và source.
5. Publish message không có correlation ID.
6. Ghi policy xử lý header bị thiếu.

Policy mẫu:

~~~text
correlation ID có -> dùng để nối log
correlation ID thiếu -> warning, không tự động coi eventId là trace ID
~~~

## 19. Lab 5 — Test topology declaration

Context test tối thiểu:

~~~java
@SpringBootTest
class RabbitTopologyTest {

    @Autowired
    private TopicExchange ordersEventsExchange;

    @Autowired
    private Queue ordersEmailQueue;

    @Autowired
    private Binding orderCreatedToEmailBinding;

    @Test
    void topologyBeansAreCreated() {
        assertThat(ordersEventsExchange.getName())
                .isEqualTo(RabbitNames.ORDERS_EVENTS_EXCHANGE);

        assertThat(ordersEmailQueue.getName())
                .isEqualTo(RabbitNames.ORDERS_EMAIL_QUEUE);

        assertThat(orderCreatedToEmailBinding.getRoutingKey())
                .isEqualTo(RabbitNames.ORDER_CREATED_ROUTING_KEY);
    }
}
~~~

Test này kiểm tra Spring wiring, chưa chứng minh broker thật có topology. Integration test với Testcontainers sẽ kiểm tra kết nối, declaration, publish, consume và converter trong môi trường thật.

## 20. Lỗi thường gặp

### Sai host, port hoặc vhost

AMQP dùng port 5672, Management UI dùng 15672. Đổi nhầm hai port sẽ làm app kết nối sai.

### Sai credential hoặc permission

User có thể đăng nhập UI nhưng không có configure/write/read trong vhost. Kiểm tra permission bằng rabbitmqctl.

### Exchange type mismatch

Bean TopicExchange nhưng broker đã có cùng tên là DirectExchange. Không xóa bừa; xác định topology owner và migration.

### Listener không xuất hiện

Kiểm tra component scan, @Component, queue name, connection, listener annotation và container factory.

### Không deserialize được JSON

Kiểm tra converter, content type, field DTO, Instant/BigDecimal, type header và schema version.

### Publish được nhưng queue không có message

Kiểm tra RabbitTemplate exchange, routing key, binding, mandatory publish/return, consumer đã lấy message chưa và vhost.

### Listener ack dù business thất bại

Không catch rồi nuốt exception. Để lỗi nổi lên để container và policy retry/DLQ xử lý.

## 21. Checklist

- [ ] Credential production không hardcode.
- [ ] Host, port và vhost externalized.
- [ ] Exchange, queue và binding có tên ổn định.
- [ ] Topology khai báo bằng code hoặc definitions.
- [ ] Queue nghiệp vụ durable, không exclusive/auto-delete tùy tiện.
- [ ] Message DTO độc lập với entity JPA.
- [ ] JSON converter và content type thống nhất.
- [ ] Message có eventId/messageId.
- [ ] Correlation ID truyền qua header.
- [ ] Listener không nuốt exception.
- [ ] Concurrency phù hợp downstream.
- [ ] Có log/metric publish và consume.
- [ ] Không coi auto ack là database transaction.
- [ ] Có kế hoạch retry/DLQ.
- [ ] Có context test hoặc integration test.

## 22. Deliverable cuối tuần

Tạo:

~~~text
rabbitmq/week-04/
├── README.md
├── pom.xml
└── src/
    ├── main/java/com/example/orders/
    │   ├── OrderApplication.java
    │   ├── config/
    │   │   ├── RabbitNames.java
    │   │   ├── RabbitTopologyConfig.java
    │   │   ├── RabbitMessageConfig.java
    │   │   └── RabbitListenerConfig.java
    │   ├── message/
    │   │   ├── OrderCreatedMessage.java
    │   │   └── OrderCreatedPublisher.java
    │   └── listener/OrderCreatedListener.java
    └── test/java/com/example/orders/
        └── RabbitTopologyTest.java
~~~

README cần có:

1. Cách chạy RabbitMQ và Spring Boot.
2. application config local.
3. Sơ đồ exchange, queue và binding.
4. JSON payload thực tế.
5. Log publish/consume.
6. Correlation ID, message ID và custom header.
7. Kết quả thử concurrency.
8. Kết quả deserialize error.
9. Kết quả test topology.
10. Các vấn đề chưa giải quyết: retry, DLQ, manual ack và outbox.

## 23. Tiêu chí tự kiểm tra

1. RabbitTemplate, RabbitAdmin và listener container khác nhau thế nào?
2. Vì sao topology nên khai báo bằng Java config?
3. Queue durable khác message persistent thế nào?
4. Vì sao không serialize trực tiếp entity JPA?
5. Jackson message converter làm gì?
6. Khi content type sai, lỗi xảy ra ở đâu?
7. @RabbitListener tạo ra thành phần runtime nào?
8. Concurrency cao có rủi ro gì?
9. Correlation ID khác message ID và event ID thế nào?
10. Vì sao không catch rồi nuốt exception trong listener?
11. Auto acknowledgement có phải database transaction không?
12. Vì sao publish không exception chưa chứng minh message vào queue?
13. Management UI port khác AMQP port thế nào?
14. Vì sao topology mismatch có thể làm channel/context fail?
15. Khi listener không nhận message, kiểm tra những lớp nào?

### Đáp án cần nhớ

> Spring AMQP giảm boilerplate nhưng không thay đổi bản chất RabbitMQ. RabbitTemplate publish message, RabbitAdmin declare topology, listener container quản lý consumer và @RabbitListener nối business method vào queue. Hãy dùng DTO message riêng, JSON converter thống nhất, correlation ID để trace, concurrency có giới hạn, và để lỗi nổi lên để sau này áp dụng retry/DLQ đúng cách.

## 24. Chuẩn bị cho Tuần 5

Tuần sau đi sâu vào work queue và competing consumers:

- một queue với nhiều consumer;
- manual acknowledgement;
- delivery tag, ack, reject, nack;
- fair dispatch;
- prefetch;
- concurrency;
- graceful shutdown;
- consumer crash và redelivery.

Giữ lại Spring Boot app, topology config và listener factory của tuần này. Chúng sẽ là nền để chuyển từ auto acknowledgement sang manual acknowledgement và kiểm soát processing flow rõ ràng hơn.

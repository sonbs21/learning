# 10.6. Spring Integration

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#messaging.spring-integration>

Spring Boot cung cấp một số tiện ích để làm việc với [Spring Integration](https://spring.io/projects/spring-integration), bao gồm "Starter" `spring-boot-starter-integration`. Spring Integration cung cấp các abstraction trên messaging và cả các transport khác như HTTP, TCP, và các loại khác. Nếu Spring Integration có trên classpath, nó được khởi tạo thông qua annotation `@EnableIntegration`.

Logic polling của Spring Integration dựa vào [`TaskScheduler` được tự động cấu hình](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.task-execution-and-scheduling). `PollerMetadata` mặc định (poll số lượng message không giới hạn mỗi giây) có thể được tùy chỉnh bằng các configuration property `spring.integration.poller.*`.

Spring Boot cũng cấu hình một số tính năng được kích hoạt bởi sự hiện diện của các module Spring Integration bổ sung. Nếu `spring-integration-jmx` cũng có trên classpath, các thống kê xử lý message được publish qua JMX. Nếu có `spring-integration-jdbc`, schema database mặc định có thể được tạo khi khởi động, như trong dòng sau:

**Properties**

```properties
spring.integration.jdbc.initialize-schema=always
```

**Yaml**

```yaml
spring:
  integration:
    jdbc:
      initialize-schema: "always"
```

Nếu có `spring-integration-rsocket`, lập trình viên có thể cấu hình một RSocket server bằng các property `"spring.rsocket.server.*"` và để nó dùng các component `IntegrationRSocketEndpoint` hoặc `RSocketOutboundGateway` để xử lý các message RSocket đến. Hạ tầng này có thể xử lý các channel adapter RSocket của Spring Integration và các handler `@MessageMapping` (khi `"spring.integration.rsocket.server.message-mapping-enabled"` được cấu hình).

Spring Boot cũng có thể tự động cấu hình một `ClientRSocketConnector` bằng các configuration property:

**Properties**

```properties
# Connecting to a RSocket server over TCP
spring.integration.rsocket.client.host=example.org
spring.integration.rsocket.client.port=9898
```

**Yaml**

```yaml
# Connecting to a RSocket server over TCP
spring:
  integration:
    rsocket:
      client:
        host: "example.org"
        port: 9898
```

**Properties**

```properties
# Connecting to a RSocket Server over WebSocket
spring.integration.rsocket.client.uri=ws://example.org
```

**Yaml**

```yaml
# Connecting to a RSocket Server over WebSocket
spring:
  integration:
    rsocket:
      client:
        uri: "ws://example.org"
```

Xem các class [`IntegrationAutoConfiguration`](https://github.com/spring-projects/spring-boot/tree/v3.2.5/spring-boot-project/spring-boot-autoconfigure/src/main/java/org/springframework/boot/autoconfigure/integration/IntegrationAutoConfiguration.java) và [`IntegrationProperties`](https://github.com/spring-projects/spring-boot/tree/v3.2.5/spring-boot-project/spring-boot-autoconfigure/src/main/java/org/springframework/boot/autoconfigure/integration/IntegrationProperties.java) để biết thêm chi tiết.

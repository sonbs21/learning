# 10.3. Hỗ trợ Apache Kafka (Apache Kafka Support)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#messaging.kafka>

[Apache Kafka](https://kafka.apache.org/) được hỗ trợ bằng cách cung cấp auto-configuration cho project `spring-kafka`.

Cấu hình Kafka được kiểm soát bởi các configuration property bên ngoài trong `spring.kafka.*`. Ví dụ, bạn có thể khai báo phần sau trong `application.properties`:

**Properties**

```properties
spring.kafka.bootstrap-servers=localhost:9092
spring.kafka.consumer.group-id=myGroup
```

**Yaml**

```yaml
spring:
  kafka:
    bootstrap-servers: "localhost:9092"
    consumer:
      group-id: "myGroup"
```

> **MẸO (TIP):** Để tạo một topic khi khởi động, hãy thêm một bean kiểu `NewTopic`. Nếu topic đã tồn tại, bean sẽ bị bỏ qua.

Xem [`KafkaProperties`](https://github.com/spring-projects/spring-boot/tree/v3.2.5/spring-boot-project/spring-boot-autoconfigure/src/main/java/org/springframework/boot/autoconfigure/kafka/KafkaProperties.java) để biết thêm các tùy chọn được hỗ trợ.

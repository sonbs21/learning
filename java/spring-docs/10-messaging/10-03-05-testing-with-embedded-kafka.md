# 10.3.5. Kiểm thử với Kafka nhúng (Testing with Embedded Kafka)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#messaging.kafka.embedded>

Spring for Apache Kafka cung cấp một cách tiện lợi để kiểm thử project với một broker Apache Kafka nhúng. Để dùng tính năng này, hãy đánh annotation `@EmbeddedKafka` từ module `spring-kafka-test` lên một class test. Để biết thêm thông tin, vui lòng xem [tài liệu hướng dẫn tham khảo](https://docs.spring.io/spring-kafka/docs/3.1.4/reference/testing.html#ekb) của Spring for Apache Kafka.

Để auto-configuration của Spring Boot hoạt động với broker Apache Kafka nhúng nói trên, bạn cần ánh xạ lại một system property chứa địa chỉ broker nhúng (được `EmbeddedKafkaBroker` điền vào) sang configuration property của Spring Boot cho Apache Kafka. Có một số cách để làm điều đó:

- Cung cấp một system property để ánh xạ địa chỉ broker nhúng vào `spring.kafka.bootstrap-servers` trong class test:

**Java**

```java
static {
    System.setProperty(EmbeddedKafkaBroker.BROKER_LIST_PROPERTY, "spring.kafka.bootstrap-servers");
}
```

**Kotlin**

```kotlin
init {
    System.setProperty(EmbeddedKafkaBroker.BROKER_LIST_PROPERTY, "spring.kafka.bootstrap-servers")
}
```

- Cấu hình một tên property trên annotation `@EmbeddedKafka`:

**Java**

```java
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;

@SpringBootTest
@EmbeddedKafka(topics = "someTopic", bootstrapServersProperty = "spring.kafka.bootstrap-servers")
class MyTest {

    // ...

}
```

**Kotlin**

```kotlin
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.test.context.EmbeddedKafka

@SpringBootTest
@EmbeddedKafka(topics = ["someTopic"], bootstrapServersProperty = "spring.kafka.bootstrap-servers")
class MyTest {

    // ...

}
```

- Dùng placeholder trong configuration property:

**Properties**

```properties
spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}
```

**Yaml**

```yaml
spring:
  kafka:
    bootstrap-servers: "${spring.embedded.kafka.brokers}"
```

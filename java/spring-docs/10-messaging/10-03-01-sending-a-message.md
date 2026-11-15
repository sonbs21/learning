# 10.3.1. Gửi message (Sending a Message)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#messaging.kafka.sending>

`KafkaTemplate` của Spring được tự động cấu hình, và bạn có thể autowire nó trực tiếp vào các bean của riêng mình, như trong ví dụ sau:

**Java**

```java
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class MyBean {

    private final KafkaTemplate<String, String> kafkaTemplate;

    public MyBean(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    // ...

    public void someMethod() {
        this.kafkaTemplate.send("someTopic", "Hello");
    }

}
```

**Kotlin**

```kotlin
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

@Component
class MyBean(private val kafkaTemplate: KafkaTemplate<String, String>) {

    // ...

    fun someMethod() {
        kafkaTemplate.send("someTopic", "Hello")
    }

}
```

> **LƯU Ý (NOTE):** Nếu property `spring.kafka.producer.transaction-id-prefix` được định nghĩa, một `KafkaTransactionManager` sẽ được tự động cấu hình. Ngoài ra, nếu có bean `RecordMessageConverter` được định nghĩa, nó sẽ được tự động gắn với `KafkaTemplate` được tự động cấu hình.

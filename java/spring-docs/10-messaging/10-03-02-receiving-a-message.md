# 10.3.2. Nhận message (Receiving a Message)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#messaging.kafka.receiving>

Khi có hạ tầng Apache Kafka, bất kỳ bean nào cũng có thể được đánh annotation `@KafkaListener` để tạo một listener endpoint. Nếu chưa có `KafkaListenerContainerFactory` nào được định nghĩa, một factory mặc định sẽ được cấu hình tự động với các key được định nghĩa trong `spring.kafka.listener.*`.

Component sau tạo một listener endpoint trên topic `someTopic`:

**Java**

```java
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class MyBean {

    @KafkaListener(topics = "someTopic")
    public void processMessage(String content) {
        // ...
    }

}
```

**Kotlin**

```kotlin
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class MyBean {

    @KafkaListener(topics = ["someTopic"])
    fun processMessage(content: String?) {
        // ...
    }

}
```

Nếu có bean `KafkaTransactionManager` được định nghĩa, nó sẽ được tự động gắn với container factory. Tương tự, nếu có bean `RecordFilterStrategy`, `CommonErrorHandler`, `AfterRollbackProcessor` hoặc `ConsumerAwareRebalanceListener` được định nghĩa, nó sẽ được tự động gắn với factory mặc định.

Tùy thuộc vào loại listener, một bean `RecordMessageConverter` hoặc `BatchMessageConverter` sẽ được gắn với factory mặc định. Nếu chỉ có bean `RecordMessageConverter` cho một batch listener, nó sẽ được bọc trong một `BatchMessageConverter`.

> **MẸO (TIP):** Một `ChainedKafkaTransactionManager` tùy chỉnh phải được đánh dấu `@Primary` vì nó thường tham chiếu tới bean `KafkaTransactionManager` được tự động cấu hình.

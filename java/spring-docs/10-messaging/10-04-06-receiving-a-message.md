# 10.4.6. Nhận message (Receiving a Message)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#messaging.pulsar.receiving>

Khi có hạ tầng Apache Pulsar, bất kỳ bean nào cũng có thể được đánh annotation `@PulsarListener` để tạo một listener endpoint. Component sau tạo một listener endpoint trên topic `someTopic`:

**Java**

```java
import org.springframework.pulsar.annotation.PulsarListener;
import org.springframework.stereotype.Component;

@Component
public class MyBean {

    @PulsarListener(topics = "someTopic")
    public void processMessage(String content) {
        // ...
    }

}
```

**Kotlin**

```kotlin
import org.springframework.pulsar.annotation.PulsarListener
import org.springframework.stereotype.Component

@Component
class MyBean {

    @PulsarListener(topics = ["someTopic"])
    fun processMessage(content: String?) {
        // ...
    }

}
```

Auto-configuration của Spring Boot cung cấp tất cả các component cần thiết cho `PulsarListener`, chẳng hạn như `PulsarListenerContainerFactory` và consumer factory mà nó dùng để xây dựng các Pulsar consumer bên dưới. Bạn có thể cấu hình các component này bằng cách chỉ định bất kỳ application property nào có tiền tố `spring.pulsar.listener.*` và `spring.pulsar.consumer.*`.

Nếu bạn cần kiểm soát nhiều hơn đối với cấu hình consumer factory, hãy cân nhắc đăng ký một hoặc nhiều bean `ConsumerBuilderCustomizer`. Các customizer này được áp dụng cho tất cả các consumer được factory tạo ra, và do đó cho tất cả các instance `@PulsarListener`. Bạn cũng có thể tùy chỉnh một listener riêng lẻ bằng cách đặt thuộc tính `consumerCustomizer` của annotation `@PulsarListener`.

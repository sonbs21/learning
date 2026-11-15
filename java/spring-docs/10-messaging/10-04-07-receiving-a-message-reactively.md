# 10.4.7. Nhận message theo kiểu reactive (Receiving a Message Reactively)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#messaging.pulsar.receiving-reactive>

Khi có hạ tầng Apache Pulsar và auto-configuration Reactive được kích hoạt, bất kỳ bean nào cũng có thể được đánh annotation `@ReactivePulsarListener` để tạo một reactive listener endpoint. Component sau tạo một reactive listener endpoint trên topic `someTopic`:

**Java**

```java
import reactor.core.publisher.Mono;

import org.springframework.pulsar.reactive.config.annotation.ReactivePulsarListener;
import org.springframework.stereotype.Component;

@Component
public class MyBean {

    @ReactivePulsarListener(topics = "someTopic")
    public Mono<Void> processMessage(String content) {
        // ...
        return Mono.empty();
    }

}
```

**Kotlin**

```kotlin
import org.springframework.pulsar.reactive.config.annotation.ReactivePulsarListener
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

@Component
class MyBean {

    @ReactivePulsarListener(topics = ["someTopic"])
    fun processMessage(content: String?): Mono<Void> {
        // ...
        return Mono.empty()
    }

}
```

Auto-configuration của Spring Boot cung cấp tất cả các component cần thiết cho `ReactivePulsarListener`, chẳng hạn như `ReactivePulsarListenerContainerFactory` và consumer factory mà nó dùng để xây dựng các Pulsar consumer reactive bên dưới. Bạn có thể cấu hình các component này bằng cách chỉ định bất kỳ application property nào có tiền tố `spring.pulsar.listener.**` và `spring.pulsar.consumer.**`.

Nếu bạn cần kiểm soát nhiều hơn đối với cấu hình consumer factory, hãy cân nhắc đăng ký một hoặc nhiều bean `ReactiveMessageConsumerBuilderCustomizer`. Các customizer này được áp dụng cho tất cả các consumer được factory tạo ra, và do đó cho tất cả các instance `@ReactivePulsarListener`. Bạn cũng có thể tùy chỉnh một listener riêng lẻ bằng cách đặt thuộc tính `consumerCustomizer` của annotation `@ReactivePulsarListener`.

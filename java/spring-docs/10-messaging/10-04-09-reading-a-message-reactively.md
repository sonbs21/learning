# 10.4.9. Đọc message theo kiểu reactive (Reading a Message Reactively)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#messaging.pulsar.reading-reactive>

Khi có hạ tầng Apache Pulsar và auto-configuration Reactive được kích hoạt, `ReactivePulsarReaderFactory` của Spring được cung cấp, và bạn có thể dùng nó để tạo một reader nhằm đọc message theo kiểu reactive. Component sau tạo một reader bằng factory được cung cấp và đọc một message duy nhất từ 5 phút trước từ topic `someTopic`:

**Java**

```java
import java.time.Instant;
import java.util.List;

import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.Schema;
import org.apache.pulsar.reactive.client.api.StartAtSpec;
import reactor.core.publisher.Mono;

import org.springframework.pulsar.reactive.core.ReactiveMessageReaderBuilderCustomizer;
import org.springframework.pulsar.reactive.core.ReactivePulsarReaderFactory;
import org.springframework.stereotype.Component;

@Component
public class MyBean {

    private final ReactivePulsarReaderFactory<String> pulsarReaderFactory;

    public MyBean(ReactivePulsarReaderFactory<String> pulsarReaderFactory) {
        this.pulsarReaderFactory = pulsarReaderFactory;
    }

    public void someMethod() {
        ReactiveMessageReaderBuilderCustomizer<String> readerBuilderCustomizer = (readerBuilder) -> readerBuilder
            .topic("someTopic")
            .startAtSpec(StartAtSpec.ofInstant(Instant.now().minusSeconds(5)));
        Mono<Message<String>> message = this.pulsarReaderFactory
            .createReader(Schema.STRING, List.of(readerBuilderCustomizer))
            .readOne();
        // ...
    }

}
```

**Kotlin**

```kotlin
import org.apache.pulsar.client.api.Schema
import org.apache.pulsar.reactive.client.api.ReactiveMessageReaderBuilder
import org.apache.pulsar.reactive.client.api.StartAtSpec
import org.springframework.pulsar.reactive.core.ReactiveMessageReaderBuilderCustomizer
import org.springframework.pulsar.reactive.core.ReactivePulsarReaderFactory
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class MyBean(private val pulsarReaderFactory: ReactivePulsarReaderFactory<String>) {

    fun someMethod() {
        val readerBuilderCustomizer = ReactiveMessageReaderBuilderCustomizer {
            readerBuilder: ReactiveMessageReaderBuilder<String> ->
                readerBuilder
                    .topic("someTopic")
                    .startAtSpec(StartAtSpec.ofInstant(Instant.now().minusSeconds(5)))
        }
        val message = pulsarReaderFactory
                .createReader(Schema.STRING, listOf(readerBuilderCustomizer))
                .readOne()
        // ...
    }

}
```

Auto-configuration của Spring Boot cung cấp reader factory này, có thể được tùy chỉnh bằng cách đặt bất kỳ application property nào có tiền tố `spring.pulsar.reader.*`.

Nếu bạn cần kiểm soát nhiều hơn đối với cấu hình reader factory, hãy cân nhắc truyền vào một hoặc nhiều instance `ReactiveMessageReaderBuilderCustomizer` khi dùng factory để tạo một reader.

Nếu bạn cần kiểm soát nhiều hơn đối với cấu hình reader factory, hãy cân nhắc đăng ký một hoặc nhiều bean `ReactiveMessageReaderBuilderCustomizer`. Các customizer này được áp dụng cho tất cả các reader được tạo ra. Bạn cũng có thể truyền một hoặc nhiều `ReactiveMessageReaderBuilderCustomizer` khi tạo một reader để chỉ áp dụng các tùy chỉnh cho reader được tạo đó.

> **MẸO (TIP):** Để biết thêm chi tiết về bất kỳ component nào ở trên và khám phá các tính năng có sẵn khác, xem [tài liệu tham khảo](https://docs.spring.io/spring-pulsar/docs/1.0.5/reference/) của Spring for Apache Pulsar.

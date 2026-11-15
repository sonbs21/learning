# 10.4.8. Đọc message (Reading a Message)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#messaging.pulsar.reading>

Interface reader của Pulsar cho phép ứng dụng tự quản lý cursor. Khi bạn dùng một reader để kết nối tới một topic, bạn cần chỉ định reader bắt đầu đọc từ message nào khi nó kết nối tới topic.

Khi có hạ tầng Apache Pulsar, bất kỳ bean nào cũng có thể được đánh annotation `@PulsarReader` để tiêu thụ message bằng một reader. Component sau tạo một reader endpoint bắt đầu đọc message từ đầu topic `someTopic`:

**Java**

```java
import org.springframework.pulsar.annotation.PulsarReader;
import org.springframework.stereotype.Component;

@Component
public class MyBean {

    @PulsarReader(topics = "someTopic", startMessageId = "earliest")
    public void processMessage(String content) {
        // ...
    }

}
```

**Kotlin**

```kotlin
import org.springframework.pulsar.annotation.PulsarReader
import org.springframework.stereotype.Component

@Component
class MyBean {

    @PulsarReader(topics = ["someTopic"], startMessageId = "earliest")
    fun processMessage(content: String?) {
        // ...
    }

}
```

`@PulsarReader` dựa vào một `PulsarReaderFactory` để tạo Pulsar reader bên dưới. Auto-configuration của Spring Boot cung cấp reader factory này, có thể được tùy chỉnh bằng cách đặt bất kỳ application property nào có tiền tố `spring.pulsar.reader.*`.

Nếu bạn cần kiểm soát nhiều hơn đối với cấu hình reader factory, hãy cân nhắc đăng ký một hoặc nhiều bean `ReaderBuilderCustomizer`. Các customizer này được áp dụng cho tất cả các reader được factory tạo ra, và do đó cho tất cả các instance `@PulsarReader`. Bạn cũng có thể tùy chỉnh một listener riêng lẻ bằng cách đặt thuộc tính `readerCustomizer` của annotation `@PulsarReader`.

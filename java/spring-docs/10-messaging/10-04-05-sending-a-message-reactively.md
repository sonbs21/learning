# 10.4.5. Gửi message theo kiểu reactive (Sending a Message Reactively)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#messaging.pulsar.sending-reactive>

Khi auto-configuration Reactive được kích hoạt, `ReactivePulsarTemplate` của Spring được tự động cấu hình, và bạn có thể dùng nó để gửi message, như trong ví dụ sau:

**Java**

```java
import org.springframework.pulsar.reactive.core.ReactivePulsarTemplate;
import org.springframework.stereotype.Component;

@Component
public class MyBean {

    private final ReactivePulsarTemplate<String> pulsarTemplate;

    public MyBean(ReactivePulsarTemplate<String> pulsarTemplate) {
        this.pulsarTemplate = pulsarTemplate;
    }

    public void someMethod() {
        this.pulsarTemplate.send("someTopic", "Hello").subscribe();
    }

}
```

**Kotlin**

```kotlin
import org.springframework.pulsar.reactive.core.ReactivePulsarTemplate
import org.springframework.stereotype.Component

@Component
class MyBean(private val pulsarTemplate: ReactivePulsarTemplate<String>) {

    fun someMethod() {
        pulsarTemplate.send("someTopic", "Hello").subscribe()
    }

}
```

`ReactivePulsarTemplate` dựa vào một `ReactivePulsarSenderFactory` để thực sự tạo sender bên dưới. Auto-configuration của Spring Boot cũng cung cấp sender factory này, theo mặc định nó cache các producer mà nó tạo ra. Bạn có thể cấu hình sender factory và các thiết lập cache bằng cách chỉ định bất kỳ application property nào có tiền tố `spring.pulsar.producer.*` và `spring.pulsar.producer.cache.*`.

Nếu bạn cần kiểm soát nhiều hơn đối với cấu hình sender factory, hãy cân nhắc đăng ký một hoặc nhiều bean `ReactiveMessageSenderBuilderCustomizer`. Các customizer này được áp dụng cho tất cả các sender được tạo ra. Bạn cũng có thể truyền vào một `ReactiveMessageSenderBuilderCustomizer` khi gửi message để chỉ ảnh hưởng tới sender hiện tại.

Nếu bạn cần kiểm soát nhiều hơn đối với message được gửi, bạn có thể truyền vào một `MessageSpecBuilderCustomizer` khi gửi message.

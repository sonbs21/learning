# 10.4.4. Gửi message (Sending a Message)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#messaging.pulsar.sending>

`PulsarTemplate` của Spring được tự động cấu hình, và bạn có thể dùng nó để gửi message, như trong ví dụ sau:

**Java**

```java
import org.apache.pulsar.client.api.PulsarClientException;

import org.springframework.pulsar.core.PulsarTemplate;
import org.springframework.stereotype.Component;

@Component
public class MyBean {

    private final PulsarTemplate<String> pulsarTemplate;

    public MyBean(PulsarTemplate<String> pulsarTemplate) {
        this.pulsarTemplate = pulsarTemplate;
    }

    public void someMethod() throws PulsarClientException {
        this.pulsarTemplate.send("someTopic", "Hello");
    }

}
```

**Kotlin**

```kotlin
import org.apache.pulsar.client.api.PulsarClientException
import org.springframework.pulsar.core.PulsarTemplate
import org.springframework.stereotype.Component

@Component
class MyBean(private val pulsarTemplate: PulsarTemplate<String>) {

    @Throws(PulsarClientException::class)
    fun someMethod() {
        pulsarTemplate.send("someTopic", "Hello")
    }

}
```

`PulsarTemplate` dựa vào một `PulsarProducerFactory` để tạo Pulsar producer bên dưới. Auto-configuration của Spring Boot cũng cung cấp producer factory này, theo mặc định nó cache các producer mà nó tạo ra. Bạn có thể cấu hình producer factory và các thiết lập cache bằng cách chỉ định bất kỳ application property nào có tiền tố `spring.pulsar.producer.*` và `spring.pulsar.producer.cache.*`.

Nếu bạn cần kiểm soát nhiều hơn đối với cấu hình producer factory, hãy cân nhắc đăng ký một hoặc nhiều bean `ProducerBuilderCustomizer`. Các customizer này được áp dụng cho tất cả các producer được tạo ra. Bạn cũng có thể truyền vào một `ProducerBuilderCustomizer` khi gửi message để chỉ ảnh hưởng tới producer hiện tại.

Nếu bạn cần kiểm soát nhiều hơn đối với message được gửi, bạn có thể truyền vào một `TypedMessageBuilderCustomizer` khi gửi message.

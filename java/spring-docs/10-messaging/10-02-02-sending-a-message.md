# 10.2.2. Gửi message (Sending a Message)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#messaging.amqp.sending>

`AmqpTemplate` và `AmqpAdmin` của Spring được tự động cấu hình, và bạn có thể autowire chúng trực tiếp vào các bean của riêng mình, như trong ví dụ sau:

**Java**

```java
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.stereotype.Component;

@Component
public class MyBean {

    private final AmqpAdmin amqpAdmin;

    private final AmqpTemplate amqpTemplate;

    public MyBean(AmqpAdmin amqpAdmin, AmqpTemplate amqpTemplate) {
        this.amqpAdmin = amqpAdmin;
        this.amqpTemplate = amqpTemplate;
    }

    // ...

    public void someMethod() {
        this.amqpAdmin.getQueueInfo("someQueue");
    }

    public void someOtherMethod() {
        this.amqpTemplate.convertAndSend("hello");
    }

}
```

**Kotlin**

```kotlin
import org.springframework.amqp.core.AmqpAdmin
import org.springframework.amqp.core.AmqpTemplate
import org.springframework.stereotype.Component

@Component
class MyBean(private val amqpAdmin: AmqpAdmin, private val amqpTemplate: AmqpTemplate) {

    // ...

    fun someMethod() {
        amqpAdmin.getQueueInfo("someQueue")
    }

    fun someOtherMethod() {
        amqpTemplate.convertAndSend("hello")
    }

}
```

> **LƯU Ý (NOTE):** [`RabbitMessagingTemplate`](https://docs.spring.io/spring-amqp/docs/3.1.4/api/org/springframework/amqp/rabbit/core/RabbitMessagingTemplate.html) có thể được inject theo cách tương tự. Nếu có bean `MessageConverter` được định nghĩa, nó sẽ được tự động gắn với `AmqpTemplate` được tự động cấu hình.

Nếu cần, bất kỳ `org.springframework.amqp.core.Queue` nào được định nghĩa dưới dạng bean đều được tự động dùng để khai báo một queue tương ứng trên instance RabbitMQ.

Để thử lại (retry) các thao tác, bạn có thể bật retry trên `AmqpTemplate` (ví dụ, trong trường hợp mất kết nối tới broker):

**Properties**

```properties
spring.rabbitmq.template.retry.enabled=true
spring.rabbitmq.template.retry.initial-interval=2s
```

**Yaml**

```yaml
spring:
  rabbitmq:
    template:
      retry:
        enabled: true
        initial-interval: "2s"
```

Retry bị tắt theo mặc định. Bạn cũng có thể tùy chỉnh `RetryTemplate` bằng code bằng cách khai báo một bean `RabbitRetryTemplateCustomizer`.

Nếu bạn cần tạo thêm các instance `RabbitTemplate` hoặc nếu bạn muốn ghi đè cái mặc định, Spring Boot cung cấp một bean `RabbitTemplateConfigurer` mà bạn có thể dùng để khởi tạo một `RabbitTemplate` với các thiết lập giống như các factory được auto-configuration sử dụng.

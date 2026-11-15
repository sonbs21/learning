# 10.1.4. Gửi message (Sending a Message)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#messaging.jms.sending>

`JmsTemplate` của Spring được tự động cấu hình, và bạn có thể autowire nó trực tiếp vào các bean của riêng mình, như trong ví dụ sau:

**Java**

```java
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

@Component
public class MyBean {

    private final JmsTemplate jmsTemplate;

    public MyBean(JmsTemplate jmsTemplate) {
        this.jmsTemplate = jmsTemplate;
    }

    // ...

    public void someMethod() {
        this.jmsTemplate.convertAndSend("hello");
    }

}
```

**Kotlin**

```kotlin
import org.springframework.jms.core.JmsTemplate
import org.springframework.stereotype.Component

@Component
class MyBean(private val jmsTemplate: JmsTemplate) {

    // ...

    fun someMethod() {
        jmsTemplate.convertAndSend("hello")
    }

}
```

> **LƯU Ý (NOTE):** [`JmsMessagingTemplate`](https://docs.spring.io/spring-framework/docs/6.1.6/javadoc-api/org/springframework/jms/core/JmsMessagingTemplate.html) có thể được inject theo cách tương tự. Nếu có bean `DestinationResolver` hoặc `MessageConverter` được định nghĩa, nó sẽ được tự động gắn với `JmsTemplate` được tự động cấu hình.

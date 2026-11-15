# 10.2.4. Nhận message (Receiving a Message)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#messaging.amqp.receiving>

Khi có hạ tầng Rabbit, bất kỳ bean nào cũng có thể được đánh annotation `@RabbitListener` để tạo một listener endpoint. Nếu chưa có `RabbitListenerContainerFactory` nào được định nghĩa, một `SimpleRabbitListenerContainerFactory` mặc định sẽ được cấu hình tự động và bạn có thể chuyển sang direct container bằng property `spring.rabbitmq.listener.type`. Nếu có bean `MessageConverter` hoặc `MessageRecoverer` được định nghĩa, nó sẽ được tự động gắn với factory mặc định.

Component mẫu sau tạo một listener endpoint trên queue `someQueue`:

**Java**

```java
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class MyBean {

    @RabbitListener(queues = "someQueue")
    public void processMessage(String content) {
        // ...
    }

}
```

**Kotlin**

```kotlin
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Component

@Component
class MyBean {

    @RabbitListener(queues = ["someQueue"])
    fun processMessage(content: String?) {
        // ...
    }

}
```

> **MẸO (TIP):** Xem [Javadoc của `@EnableRabbit`](https://docs.spring.io/spring-amqp/docs/3.1.4/api/org/springframework/amqp/rabbit/annotation/EnableRabbit.html) để biết thêm chi tiết.

Nếu bạn cần tạo thêm các instance `RabbitListenerContainerFactory` hoặc nếu bạn muốn ghi đè cái mặc định, Spring Boot cung cấp một `SimpleRabbitListenerContainerFactoryConfigurer` và một `DirectRabbitListenerContainerFactoryConfigurer` mà bạn có thể dùng để khởi tạo một `SimpleRabbitListenerContainerFactory` và một `DirectRabbitListenerContainerFactory` với các thiết lập giống như các factory được auto-configuration sử dụng.

> **MẸO (TIP):** Bạn chọn loại container nào không quan trọng. Hai bean đó đều được auto-configuration expose.

Ví dụ, class cấu hình sau expose một factory khác dùng một `MessageConverter` cụ thể:

**Java**

```java
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class MyRabbitConfiguration {

    @Bean
    public SimpleRabbitListenerContainerFactory myFactory(SimpleRabbitListenerContainerFactoryConfigurer configurer) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        ConnectionFactory connectionFactory = getCustomConnectionFactory();
        configurer.configure(factory, connectionFactory);
        factory.setMessageConverter(new MyMessageConverter());
        return factory;
    }

    private ConnectionFactory getCustomConnectionFactory() {
        return ...
    }

}
```

**Kotlin**

```kotlin
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory
import org.springframework.amqp.rabbit.connection.ConnectionFactory
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class MyRabbitConfiguration {

    @Bean
    fun myFactory(configurer: SimpleRabbitListenerContainerFactoryConfigurer): SimpleRabbitListenerContainerFactory {
        val factory = SimpleRabbitListenerContainerFactory()
        val connectionFactory = getCustomConnectionFactory()
        configurer.configure(factory, connectionFactory)
        factory.setMessageConverter(MyMessageConverter())
        return factory
    }

    fun getCustomConnectionFactory() : ConnectionFactory? {
        return ...
    }

}
```

Sau đó, bạn có thể dùng factory đó trong bất kỳ phương thức nào được đánh annotation `@RabbitListener`, như sau:

**Java**

```java
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class MyBean {

    @RabbitListener(queues = "someQueue", containerFactory = "myFactory")
    public void processMessage(String content) {
        // ...
    }

}
```

**Kotlin**

```kotlin
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Component

@Component
class MyBean {

    @RabbitListener(queues = ["someQueue"], containerFactory = "myFactory")
    fun processMessage(content: String?) {
        // ...
    }

}
```

Bạn có thể bật retry để xử lý các tình huống listener ném exception. Theo mặc định, `RejectAndDontRequeueRecoverer` được sử dụng, nhưng bạn có thể định nghĩa một `MessageRecoverer` của riêng mình. Khi hết số lần retry, message sẽ bị từ chối và bị bỏ đi hoặc được định tuyến tới một dead-letter exchange nếu broker được cấu hình để làm vậy. Theo mặc định, retry bị tắt. Bạn cũng có thể tùy chỉnh `RetryTemplate` bằng code bằng cách khai báo một bean `RabbitRetryTemplateCustomizer`.

> **QUAN TRỌNG (IMPORTANT):** Theo mặc định, nếu retry bị tắt và listener ném exception, việc giao message sẽ được thử lại vô thời hạn. Bạn có thể thay đổi hành vi này theo hai cách: Đặt property `defaultRequeueRejected` thành `false` để không thử giao lại lần nào, hoặc ném một `AmqpRejectAndDontRequeueException` để báo hiệu message nên bị từ chối. Cách sau là cơ chế được dùng khi retry được bật và số lần thử giao tối đa đã đạt tới.

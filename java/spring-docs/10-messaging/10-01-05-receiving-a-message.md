# 10.1.5. Nhận message (Receiving a Message)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#messaging.jms.receiving>

Khi có hạ tầng JMS, bất kỳ bean nào cũng có thể được đánh annotation `@JmsListener` để tạo một listener endpoint. Nếu chưa có `JmsListenerContainerFactory` nào được định nghĩa, một factory mặc định sẽ được cấu hình tự động. Nếu có các bean `DestinationResolver`, `MessageConverter`, hoặc `jakarta.jms.ExceptionListener` được định nghĩa, chúng sẽ được tự động gắn với factory mặc định.

Theo mặc định, factory mặc định có tính transaction. Nếu bạn chạy trong một hạ tầng có `JtaTransactionManager`, nó sẽ được gắn với listener container theo mặc định. Nếu không, cờ `sessionTransacted` được bật. Trong trường hợp sau, bạn có thể gắn transaction của kho dữ liệu cục bộ với việc xử lý một message đến bằng cách thêm `@Transactional` trên phương thức listener (hoặc một delegate của nó). Điều này đảm bảo message đến được xác nhận (acknowledged) sau khi transaction cục bộ đã hoàn tất. Điều này cũng bao gồm việc gửi các message phản hồi đã được thực hiện trên cùng phiên JMS.

Component sau tạo một listener endpoint trên destination `someQueue`:

**Java**

```java
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

@Component
public class MyBean {

    @JmsListener(destination = "someQueue")
    public void processMessage(String content) {
        // ...
    }

}
```

**Kotlin**

```kotlin
import org.springframework.jms.annotation.JmsListener
import org.springframework.stereotype.Component

@Component
class MyBean {

    @JmsListener(destination = "someQueue")
    fun processMessage(content: String?) {
        // ...
    }

}
```

> **MẸO (TIP):** Xem [Javadoc của `@EnableJms`](https://docs.spring.io/spring-framework/docs/6.1.6/javadoc-api/org/springframework/jms/annotation/EnableJms.html) để biết thêm chi tiết.

Nếu bạn cần tạo thêm các instance `JmsListenerContainerFactory` hoặc nếu bạn muốn ghi đè factory mặc định, Spring Boot cung cấp một `DefaultJmsListenerContainerFactoryConfigurer` mà bạn có thể dùng để khởi tạo một `DefaultJmsListenerContainerFactory` với các thiết lập giống như factory được tự động cấu hình.

Ví dụ, ví dụ sau expose một factory khác dùng một `MessageConverter` cụ thể:

**Java**

```java
import jakarta.jms.ConnectionFactory;

import org.springframework.boot.autoconfigure.jms.DefaultJmsListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;

@Configuration(proxyBeanMethods = false)
public class MyJmsConfiguration {

    @Bean
    public DefaultJmsListenerContainerFactory myFactory(DefaultJmsListenerContainerFactoryConfigurer configurer) {
        DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory();
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
import jakarta.jms.ConnectionFactory
import org.springframework.boot.autoconfigure.jms.DefaultJmsListenerContainerFactoryConfigurer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jms.config.DefaultJmsListenerContainerFactory

@Configuration(proxyBeanMethods = false)
class MyJmsConfiguration {

    @Bean
    fun myFactory(configurer: DefaultJmsListenerContainerFactoryConfigurer): DefaultJmsListenerContainerFactory {
        val factory = DefaultJmsListenerContainerFactory()
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

Sau đó, bạn có thể dùng factory đó trong bất kỳ phương thức nào được đánh annotation `@JmsListener` như sau:

**Java**

```java
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

@Component
public class MyBean {

    @JmsListener(destination = "someQueue", containerFactory = "myFactory")
    public void processMessage(String content) {
        // ...
    }

}
```

**Kotlin**

```kotlin
import org.springframework.jms.annotation.JmsListener
import org.springframework.stereotype.Component

@Component
class MyBean {

    @JmsListener(destination = "someQueue", containerFactory = "myFactory")
    fun processMessage(content: String?) {
        // ...
    }

}
```

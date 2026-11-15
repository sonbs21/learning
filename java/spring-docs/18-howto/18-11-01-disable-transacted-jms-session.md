# 18.11.1. Vô hiệu hóa JMS session có transaction (Disable Transacted JMS Session)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.messaging.disable-transacted-jms-session>

Nếu JMS broker của bạn không hỗ trợ transacted session, bạn phải vô hiệu hóa hoàn toàn việc hỗ trợ transaction. Nếu bạn tự tạo `JmsListenerContainerFactory` của riêng mình, bạn không cần làm gì, vì theo mặc định nó không thể có transaction. Nếu bạn muốn dùng `DefaultJmsListenerContainerFactoryConfigurer` để tái sử dụng mặc định của Spring Boot, bạn có thể vô hiệu hóa transacted session như sau:

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
    public DefaultJmsListenerContainerFactory jmsListenerContainerFactory(ConnectionFactory connectionFactory,
            DefaultJmsListenerContainerFactoryConfigurer configurer) {
        DefaultJmsListenerContainerFactory listenerFactory = new DefaultJmsListenerContainerFactory();
        configurer.configure(listenerFactory, connectionFactory);
        listenerFactory.setTransactionManager(null);
        listenerFactory.setSessionTransacted(false);
        return listenerFactory;
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
    fun jmsListenerContainerFactory(connectionFactory: ConnectionFactory?,
            configurer: DefaultJmsListenerContainerFactoryConfigurer): DefaultJmsListenerContainerFactory {
        val listenerFactory = DefaultJmsListenerContainerFactory()
        configurer.configure(listenerFactory, connectionFactory)
        listenerFactory.setTransactionManager(null)
        listenerFactory.setSessionTransacted(false)
        return listenerFactory
    }

}
```

Ví dụ trên ghi đè factory mặc định, và nó nên được áp dụng cho bất kỳ factory nào khác mà ứng dụng của bạn định nghĩa, nếu có.

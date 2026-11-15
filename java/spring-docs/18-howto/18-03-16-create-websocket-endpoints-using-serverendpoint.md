# 18.3.16. Tạo WebSocket endpoint bằng @ServerEndpoint (Create WebSocket Endpoints Using @ServerEndpoint)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.webserver.create-websocket-endpoints-using-serverendpoint>

Nếu bạn muốn dùng `@ServerEndpoint` trong một ứng dụng Spring Boot dùng embedded container, bạn phải khai báo một `@Bean` `ServerEndpointExporter` duy nhất, như trong ví dụ sau:

**Java**

```java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

@Configuration(proxyBeanMethods = false)
public class MyWebSocketConfiguration {

    @Bean
    public ServerEndpointExporter serverEndpointExporter() {
        return new ServerEndpointExporter();
    }

}
```

**Kotlin**

```kotlin
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.socket.server.standard.ServerEndpointExporter

@Configuration(proxyBeanMethods = false)
class MyWebSocketConfiguration {

    @Bean
    fun serverEndpointExporter(): ServerEndpointExporter {
        return ServerEndpointExporter()
    }

}
```

Bean trong ví dụ trên đăng ký mọi bean được annotate với `@ServerEndpoint` với WebSocket container bên dưới. Khi được triển khai lên một servlet container độc lập, vai trò này được thực hiện bởi một servlet container initializer, và bean `ServerEndpointExporter` là không cần thiết.

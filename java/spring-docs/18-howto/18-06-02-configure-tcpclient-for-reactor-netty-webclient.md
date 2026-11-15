# 18.6.2. Cấu hình TcpClient được dùng bởi WebClient dựa trên Reactor Netty (Configure the TcpClient used by a Reactor Netty-based WebClient)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.http-clients.webclient-reactor-netty-customization>

Khi Reactor Netty có trên classpath, một `WebClient` dựa trên Reactor Netty sẽ được auto-configure. Để tùy chỉnh cách client xử lý các kết nối mạng, hãy cung cấp một bean `ClientHttpConnector`. Ví dụ sau cấu hình connect timeout 60 giây và thêm một `ReadTimeoutHandler`:

**Java**

```java
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import reactor.netty.http.client.HttpClient;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ReactorResourceFactory;
import org.springframework.http.client.reactive.ClientHttpConnector;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;

@Configuration(proxyBeanMethods = false)
public class MyReactorNettyClientConfiguration {

    @Bean
    ClientHttpConnector clientHttpConnector(ReactorResourceFactory resourceFactory) {
        HttpClient httpClient = HttpClient.create(resourceFactory.getConnectionProvider())
                .runOn(resourceFactory.getLoopResources())
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 60000)
                .doOnConnected((connection) -> connection.addHandlerLast(new ReadTimeoutHandler(60)));
        return new ReactorClientHttpConnector(httpClient);
    }

}
```

**Kotlin**

```kotlin
import io.netty.channel.ChannelOption
import io.netty.handler.timeout.ReadTimeoutHandler
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.reactive.ClientHttpConnector
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.http.client.ReactorResourceFactory
import reactor.netty.http.client.HttpClient

@Configuration(proxyBeanMethods = false)
class MyReactorNettyClientConfiguration {

    @Bean
    fun clientHttpConnector(resourceFactory: ReactorResourceFactory): ClientHttpConnector {
        val httpClient = HttpClient.create(resourceFactory.connectionProvider)
            .runOn(resourceFactory.loopResources)
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 60000)
            .doOnConnected { connection ->
                connection.addHandlerLast(ReadTimeoutHandler(60))
            }
        return ReactorClientHttpConnector(httpClient)
    }

}
```

> **MẸO (TIP):** Lưu ý việc dùng `ReactorResourceFactory` cho connection provider và các tài nguyên event loop. Điều này đảm bảo việc chia sẻ tài nguyên hiệu quả giữa server nhận request và client gửi request.

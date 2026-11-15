# 11.7.1. Gọi Web Service bằng WebServiceTemplate (Calling Web Services with WebServiceTemplate)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#io.webservices.template>

Nếu bạn cần gọi các Web service từ xa từ ứng dụng, bạn có thể dùng class [`WebServiceTemplate`](https://docs.spring.io/spring-ws/docs/4.0.10/reference/html/#client-web-service-template). Vì các instance `WebServiceTemplate` thường cần được tùy chỉnh trước khi được dùng, Spring Boot không cung cấp bất kỳ bean `WebServiceTemplate` duy nhất nào được tự động cấu hình. Tuy nhiên, nó tự động cấu hình một `WebServiceTemplateBuilder`, có thể dùng để tạo các instance `WebServiceTemplate` khi cần.

Đoạn code sau cho thấy một ví dụ điển hình:

**Java**

```java
import org.springframework.boot.webservices.client.WebServiceTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.soap.client.core.SoapActionCallback;

@Service
public class MyService {

    private final WebServiceTemplate webServiceTemplate;

    public MyService(WebServiceTemplateBuilder webServiceTemplateBuilder) {
        this.webServiceTemplate = webServiceTemplateBuilder.build();
    }

    public SomeResponse someWsCall(SomeRequest detailsReq) {
        return (SomeResponse) this.webServiceTemplate.marshalSendAndReceive(detailsReq,
                new SoapActionCallback("https://ws.example.com/action"));
    }

}
```

**Kotlin**

```kotlin
import org.springframework.boot.webservices.client.WebServiceTemplateBuilder
import org.springframework.stereotype.Service
import org.springframework.ws.client.core.WebServiceTemplate
import org.springframework.ws.soap.client.core.SoapActionCallback

@Service
class MyService(webServiceTemplateBuilder: WebServiceTemplateBuilder) {

    private val webServiceTemplate: WebServiceTemplate

    init {
        webServiceTemplate = webServiceTemplateBuilder.build()
    }

    fun someWsCall(detailsReq: SomeRequest?): SomeResponse {
        return webServiceTemplate.marshalSendAndReceive(
            detailsReq,
            SoapActionCallback("https://ws.example.com/action")
        ) as SomeResponse
    }

}
```

Theo mặc định, `WebServiceTemplateBuilder` phát hiện một `WebServiceMessageSender` dựa trên HTTP phù hợp bằng cách dùng các thư viện HTTP client có sẵn trên classpath. Bạn cũng có thể tùy chỉnh timeout đọc và kết nối như sau:

**Java**

```java
import java.time.Duration;

import org.springframework.boot.webservices.client.HttpWebServiceMessageSenderBuilder;
import org.springframework.boot.webservices.client.WebServiceTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.transport.WebServiceMessageSender;

@Configuration(proxyBeanMethods = false)
public class MyWebServiceTemplateConfiguration {

    @Bean
    public WebServiceTemplate webServiceTemplate(WebServiceTemplateBuilder builder) {
        WebServiceMessageSender sender = new HttpWebServiceMessageSenderBuilder()
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(2))
                .build();
        return builder.messageSenders(sender).build();
    }

}
```

**Kotlin**

```kotlin
import org.springframework.boot.webservices.client.HttpWebServiceMessageSenderBuilder
import org.springframework.boot.webservices.client.WebServiceTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.ws.client.core.WebServiceTemplate
import java.time.Duration

@Configuration(proxyBeanMethods = false)
class MyWebServiceTemplateConfiguration {

    @Bean
    fun webServiceTemplate(builder: WebServiceTemplateBuilder): WebServiceTemplate {
        val sender = HttpWebServiceMessageSenderBuilder()
            .setConnectTimeout(Duration.ofSeconds(5))
            .setReadTimeout(Duration.ofSeconds(2))
            .build()
        return builder.messageSenders(sender).build()
    }

}
```

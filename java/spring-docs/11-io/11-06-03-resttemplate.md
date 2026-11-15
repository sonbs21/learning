# 11.6.3. RestTemplate

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#io.rest-client.resttemplate>

Class [`RestTemplate`](https://docs.spring.io/spring-framework/docs/6.1.6/javadoc-api/org/springframework/web/client/RestTemplate.html) của Spring Framework ra đời trước `RestClient` và là cách cổ điển mà nhiều ứng dụng dùng để gọi các REST service từ xa. Bạn có thể chọn dùng `RestTemplate` khi bạn có code hiện có mà không muốn chuyển sang `RestClient`, hoặc vì bạn đã quen với API `RestTemplate`.

Vì các instance `RestTemplate` thường cần được tùy chỉnh trước khi được dùng, Spring Boot không cung cấp bất kỳ bean `RestTemplate` duy nhất nào được tự động cấu hình. Tuy nhiên, nó tự động cấu hình một `RestTemplateBuilder`, có thể dùng để tạo các instance `RestTemplate` khi cần. `RestTemplateBuilder` được tự động cấu hình đảm bảo các `HttpMessageConverters` hợp lý và một `ClientHttpRequestFactory` phù hợp được áp dụng cho các instance `RestTemplate`.

Đoạn code sau cho thấy một ví dụ điển hình:

**Java**

```java
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class MyService {

    private final RestTemplate restTemplate;

    public MyService(RestTemplateBuilder restTemplateBuilder) {
        this.restTemplate = restTemplateBuilder.build();
    }

    public Details someRestCall(String name) {
        return this.restTemplate.getForObject("/{name}/details", Details.class, name);
    }

}
```

**Kotlin**

```kotlin
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate

@Service
class MyService(restTemplateBuilder: RestTemplateBuilder) {

    private val restTemplate: RestTemplate

    init {
        restTemplate = restTemplateBuilder.build()
    }

    fun someRestCall(name: String): Details {
        return restTemplate.getForObject("/{name}/details", Details::class.java, name)!!
    }

}
```

`RestTemplateBuilder` bao gồm một số phương thức hữu ích có thể dùng để nhanh chóng cấu hình một `RestTemplate`. Ví dụ, để thêm hỗ trợ xác thực BASIC, bạn có thể dùng `builder.basicAuthentication("user", "password").build()`.

## Tùy chỉnh RestTemplate (RestTemplate Customization)

Có ba cách tiếp cận chính để tùy chỉnh `RestTemplate`, tùy thuộc vào phạm vi bạn muốn các tùy chỉnh được áp dụng rộng đến đâu.

Để phạm vi của mọi tùy chỉnh hẹp nhất có thể, hãy inject `RestTemplateBuilder` được tự động cấu hình rồi gọi các phương thức của nó khi cần. Mỗi lời gọi phương thức trả về một instance `RestTemplateBuilder` mới, vì vậy các tùy chỉnh chỉ ảnh hưởng tới lần dùng builder này.

Để thực hiện một tùy chỉnh bổ sung, áp dụng cho toàn ứng dụng, hãy dùng một bean `RestTemplateCustomizer`. Tất cả các bean như vậy đều được tự động đăng ký với `RestTemplateBuilder` được tự động cấu hình và được áp dụng cho mọi template được xây dựng bằng nó.

Ví dụ sau cho thấy một customizer cấu hình việc dùng proxy cho tất cả các host ngoại trừ `192.168.0.5`:

**Java**

```java
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.routing.DefaultProxyRoutePlanner;
import org.apache.hc.client5.http.routing.HttpRoutePlanner;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.protocol.HttpContext;

import org.springframework.boot.web.client.RestTemplateCustomizer;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

public class MyRestTemplateCustomizer implements RestTemplateCustomizer {

    @Override
    public void customize(RestTemplate restTemplate) {
        HttpRoutePlanner routePlanner = new CustomRoutePlanner(new HttpHost("proxy.example.com"));
        HttpClient httpClient = HttpClientBuilder.create().setRoutePlanner(routePlanner).build();
        restTemplate.setRequestFactory(new HttpComponentsClientHttpRequestFactory(httpClient));
    }

    static class CustomRoutePlanner extends DefaultProxyRoutePlanner {

        CustomRoutePlanner(HttpHost proxy) {
            super(proxy);
        }

        @Override
        protected HttpHost determineProxy(HttpHost target, HttpContext context) throws HttpException {
            if (target.getHostName().equals("192.168.0.5")) {
                return null;
            }
            return super.determineProxy(target, context);
        }

    }

}
```

**Kotlin**

```kotlin
import org.apache.hc.client5.http.classic.HttpClient
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder
import org.apache.hc.client5.http.impl.routing.DefaultProxyRoutePlanner
import org.apache.hc.client5.http.routing.HttpRoutePlanner
import org.apache.hc.core5.http.HttpException
import org.apache.hc.core5.http.HttpHost
import org.apache.hc.core5.http.protocol.HttpContext
import org.springframework.boot.web.client.RestTemplateCustomizer
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory
import org.springframework.web.client.RestTemplate

class MyRestTemplateCustomizer : RestTemplateCustomizer {

    override fun customize(restTemplate: RestTemplate) {
        val routePlanner: HttpRoutePlanner = CustomRoutePlanner(HttpHost("proxy.example.com"))
        val httpClient: HttpClient = HttpClientBuilder.create().setRoutePlanner(routePlanner).build()
        restTemplate.requestFactory = HttpComponentsClientHttpRequestFactory(httpClient)
    }

    internal class CustomRoutePlanner(proxy: HttpHost?) : DefaultProxyRoutePlanner(proxy) {

        @Throws(HttpException::class)
        public override fun determineProxy(target: HttpHost, context: HttpContext): HttpHost? {
            if (target.hostName == "192.168.0.5") {
                return null
            }
            return  super.determineProxy(target, context)
        }

    }

}
```

Cuối cùng, bạn có thể định nghĩa bean `RestTemplateBuilder` của riêng mình. Làm như vậy sẽ thay thế builder được tự động cấu hình. Nếu bạn muốn mọi bean `RestTemplateCustomizer` được áp dụng cho builder tùy chỉnh của bạn, như auto-configuration sẽ làm, hãy cấu hình nó bằng một `RestTemplateBuilderConfigurer`. Ví dụ sau expose một `RestTemplateBuilder` khớp với những gì auto-configuration của Spring Boot sẽ làm, ngoại trừ việc các timeout kết nối và đọc tùy chỉnh cũng được chỉ định:

**Java**

```java
import java.time.Duration;

import org.springframework.boot.autoconfigure.web.client.RestTemplateBuilderConfigurer;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class MyRestTemplateBuilderConfiguration {

    @Bean
    public RestTemplateBuilder restTemplateBuilder(RestTemplateBuilderConfigurer configurer) {
        return configurer.configure(new RestTemplateBuilder())
            .setConnectTimeout(Duration.ofSeconds(5))
            .setReadTimeout(Duration.ofSeconds(2));
    }

}
```

**Kotlin**

```kotlin
import org.springframework.boot.autoconfigure.web.client.RestTemplateBuilderConfigurer
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

@Configuration(proxyBeanMethods = false)
class MyRestTemplateBuilderConfiguration {

    @Bean
    fun restTemplateBuilder(configurer: RestTemplateBuilderConfigurer): RestTemplateBuilder {
        return configurer.configure(RestTemplateBuilder()).setConnectTimeout(Duration.ofSeconds(5))
            .setReadTimeout(Duration.ofSeconds(2))
    }

}
```

Tùy chọn cực đoan nhất (và hiếm khi được dùng) là tạo bean `RestTemplateBuilder` của riêng bạn mà không dùng configurer. Ngoài việc thay thế builder được tự động cấu hình, điều này cũng ngăn mọi bean `RestTemplateCustomizer` được sử dụng.

## Hỗ trợ SSL cho RestTemplate (RestTemplate SSL Support)

Nếu bạn cần cấu hình SSL tùy chỉnh trên `RestTemplate`, bạn có thể áp dụng một [SSL bundle](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.ssl.bundles) cho `RestTemplateBuilder` như trong ví dụ này:

**Java**

```java
import org.springframework.boot.docs.io.restclient.resttemplate.Details;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class MyService {

    private final RestTemplate restTemplate;

    public MyService(RestTemplateBuilder restTemplateBuilder, SslBundles sslBundles) {
        this.restTemplate = restTemplateBuilder.setSslBundle(sslBundles.getBundle("mybundle")).build();
    }

    public Details someRestCall(String name) {
        return this.restTemplate.getForObject("/{name}/details", Details.class, name);
    }

}
```

**Kotlin**

```kotlin
import org.springframework.boot.docs.io.restclient.resttemplate.Details
import org.springframework.boot.ssl.SslBundles
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate

@Service
class MyService(restTemplateBuilder: RestTemplateBuilder, sslBundles: SslBundles) {

    private val restTemplate: RestTemplate

    init {
        restTemplate = restTemplateBuilder.setSslBundle(sslBundles.getBundle("mybundle")).build()
    }

    fun someRestCall(name: String): Details {
        return restTemplate.getForObject("/{name}/details", Details::class.java, name)!!
    }

}
```

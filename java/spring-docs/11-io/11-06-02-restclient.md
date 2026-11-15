# 11.6.2. RestClient

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#io.rest-client.restclient>

Nếu bạn không dùng Spring WebFlux hay Project Reactor trong ứng dụng, chúng tôi khuyên bạn nên dùng `RestClient` để gọi các REST service từ xa.

Interface `RestClient` cung cấp một API blocking theo phong cách hàm.

Spring Boot tạo và cấu hình sẵn một bean prototype `RestClient.Builder` cho bạn. Chúng tôi đặc biệt khuyên bạn nên inject nó vào các component và dùng nó để tạo các instance `RestClient`. Spring Boot cấu hình builder đó với `HttpMessageConverters` và một `ClientHttpRequestFactory` phù hợp.

Đoạn code sau cho thấy một ví dụ điển hình:

**Java**

```java
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class MyService {

    private final RestClient restClient;

    public MyService(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.baseUrl("https://example.org").build();
    }

    public Details someRestCall(String name) {
        return this.restClient.get().uri("/{name}/details", name).retrieve().body(Details.class);
    }

}
```

**Kotlin**

```kotlin
import org.springframework.boot.docs.io.restclient.restclient.ssl.Details
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient

@Service
class MyService(restClientBuilder: RestClient.Builder) {

    private val restClient: RestClient

    init {
        restClient = restClientBuilder.baseUrl("https://example.org").build()
    }

    fun someRestCall(name: String?): Details {
        return restClient.get().uri("/{name}/details", name)
                .retrieve().body(Details::class.java)!!
    }

}
```

## Tùy chỉnh RestClient (RestClient Customization)

Có ba cách tiếp cận chính để tùy chỉnh `RestClient`, tùy thuộc vào phạm vi bạn muốn các tùy chỉnh được áp dụng rộng đến đâu.

Để phạm vi của mọi tùy chỉnh hẹp nhất có thể, hãy inject `RestClient.Builder` được tự động cấu hình rồi gọi các phương thức của nó khi cần. Các instance `RestClient.Builder` có trạng thái (stateful): Mọi thay đổi trên builder được phản ánh trong tất cả các client được tạo sau đó bằng nó. Nếu bạn muốn tạo nhiều client bằng cùng một builder, bạn cũng có thể cân nhắc clone builder bằng `RestClient.Builder other = builder.clone();`.

Để thực hiện một tùy chỉnh bổ sung, áp dụng cho toàn ứng dụng, lên tất cả các instance `RestClient.Builder`, bạn có thể khai báo các bean `RestClientCustomizer` và thay đổi `RestClient.Builder` cục bộ tại điểm inject.

Cuối cùng, bạn có thể quay về API gốc và dùng `RestClient.create()`. Trong trường hợp đó, không có auto-configuration hay `RestClientCustomizer` nào được áp dụng.

## Hỗ trợ SSL cho RestClient (RestClient SSL Support)

Nếu bạn cần cấu hình SSL tùy chỉnh trên `ClientHttpRequestFactory` được `RestClient` sử dụng, bạn có thể inject một instance `RestClientSsl` có thể dùng với phương thức `apply` của builder.

Interface `RestClientSsl` cung cấp quyền truy cập tới mọi [SSL bundle](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.ssl.bundles) mà bạn đã định nghĩa trong file `application.properties` hoặc `application.yaml`.

Đoạn code sau cho thấy một ví dụ điển hình:

**Java**

```java
import org.springframework.boot.autoconfigure.web.client.RestClientSsl;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class MyService {

    private final RestClient restClient;

    public MyService(RestClient.Builder restClientBuilder, RestClientSsl ssl) {
        this.restClient = restClientBuilder.baseUrl("https://example.org").apply(ssl.fromBundle("mybundle")).build();
    }

    public Details someRestCall(String name) {
        return this.restClient.get().uri("/{name}/details", name).retrieve().body(Details.class);
    }

}
```

**Kotlin**

```kotlin
import org.springframework.boot.autoconfigure.web.client.RestClientSsl
import org.springframework.boot.docs.io.restclient.restclient.ssl.settings.Details
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient

@Service
class MyService(restClientBuilder: RestClient.Builder, ssl: RestClientSsl) {

    private val restClient: RestClient

    init {
        restClient = restClientBuilder.baseUrl("https://example.org")
                .apply(ssl.fromBundle("mybundle")).build()
    }

    fun someRestCall(name: String?): Details {
        return restClient.get().uri("/{name}/details", name)
                .retrieve().body(Details::class.java)!!
    }

}
```

Nếu bạn cần áp dụng tùy chỉnh khác ngoài một SSL bundle, bạn có thể dùng class `ClientHttpRequestFactorySettings` với `ClientHttpRequestFactories`:

**Java**

```java
import java.time.Duration;

import org.springframework.boot.ssl.SslBundles;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class MyService {

    private final RestClient restClient;

    public MyService(RestClient.Builder restClientBuilder, SslBundles sslBundles) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
            .withReadTimeout(Duration.ofMinutes(2))
            .withSslBundle(sslBundles.getBundle("mybundle"));
        ClientHttpRequestFactory requestFactory = ClientHttpRequestFactories.get(settings);
        this.restClient = restClientBuilder.baseUrl("https://example.org").requestFactory(requestFactory).build();
    }

    public Details someRestCall(String name) {
        return this.restClient.get().uri("/{name}/details", name).retrieve().body(Details.class);
    }

}
```

**Kotlin**

```kotlin
import org.springframework.boot.ssl.SslBundles
import org.springframework.boot.web.client.ClientHttpRequestFactories
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import java.time.Duration

@Service
class MyService(restClientBuilder: RestClient.Builder, sslBundles: SslBundles) {

    private val restClient: RestClient

    init {
        val settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withReadTimeout(Duration.ofMinutes(2))
                .withSslBundle(sslBundles.getBundle("mybundle"))
        val requestFactory = ClientHttpRequestFactories.get(settings)
        restClient = restClientBuilder
                .baseUrl("https://example.org")
                .requestFactory(requestFactory).build()
    }

    fun someRestCall(name: String?): Details {
        return restClient.get().uri("/{name}/details", name).retrieve().body(Details::class.java)!!
    }

}
```

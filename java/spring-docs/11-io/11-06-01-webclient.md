# 11.6.1. WebClient

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#io.rest-client.webclient>

Nếu bạn có Spring WebFlux trên classpath, chúng tôi khuyên bạn nên dùng `WebClient` để gọi các REST service từ xa. Interface `WebClient` cung cấp một API theo phong cách hàm và hoàn toàn reactive. Bạn có thể tìm hiểu thêm về `WebClient` trong [phần chuyên biệt của tài liệu Spring Framework](https://docs.spring.io/spring-framework/reference/6.1/web/webflux-webclient.html).

> **MẸO (TIP):** Nếu bạn không viết một ứng dụng Spring WebFlux reactive, bạn có thể dùng [`RestClient`](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#io.rest-client.restclient) thay cho `WebClient`. Nó cung cấp một API theo phong cách hàm tương tự, nhưng là blocking thay vì reactive.

Spring Boot tạo và cấu hình sẵn một bean prototype `WebClient.Builder` cho bạn. Chúng tôi đặc biệt khuyên bạn nên inject nó vào các component và dùng nó để tạo các instance `WebClient`. Spring Boot cấu hình builder đó để chia sẻ tài nguyên HTTP và phản ánh thiết lập codec theo cùng cách như phía server (xem [auto-configuration cho HTTP codec của WebFlux](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#web.reactive.webflux.httpcodecs)), và nhiều thứ khác.

Đoạn code sau cho thấy một ví dụ điển hình:

**Java**

```java
import reactor.core.publisher.Mono;

import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Service
public class MyService {

    private final WebClient webClient;

    public MyService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.baseUrl("https://example.org").build();
    }

    public Mono<Details> someRestCall(String name) {
        return this.webClient.get().uri("/{name}/details", name).retrieve().bodyToMono(Details.class);
    }

}
```

**Kotlin**

```kotlin
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

@Service
class MyService(webClientBuilder: WebClient.Builder) {

    private val webClient: WebClient

    init {
        webClient = webClientBuilder.baseUrl("https://example.org").build()
    }

    fun someRestCall(name: String?): Mono<Details> {
        return webClient.get().uri("/{name}/details", name)
                .retrieve().bodyToMono(Details::class.java)
    }

}
```

## WebClient Runtime

Spring Boot sẽ tự động phát hiện `ClientHttpConnector` nào cần dùng để vận hành `WebClient`, tùy thuộc vào các thư viện có sẵn trên classpath của ứng dụng. Theo thứ tự ưu tiên, các client sau được hỗ trợ:

1. Reactor Netty
2. Jetty RS client
3. Apache HttpClient
4. JDK HttpClient

Nếu có nhiều client trên classpath, client được ưu tiên nhất sẽ được dùng.

Starter `spring-boot-starter-webflux` mặc định phụ thuộc vào `io.projectreactor.netty:reactor-netty`, mang theo cả implementation server và client. Nếu bạn chọn dùng Jetty làm reactive server thay thế, bạn nên thêm dependency tới thư viện Jetty Reactive HTTP client, `org.eclipse.jetty:jetty-reactive-httpclient`. Dùng cùng một công nghệ cho server và client có những lợi thế của nó, vì nó sẽ tự động chia sẻ tài nguyên HTTP giữa client và server.

Lập trình viên có thể ghi đè cấu hình tài nguyên cho Jetty và Reactor Netty bằng cách cung cấp một bean `ReactorResourceFactory` hoặc `JettyResourceFactory` tùy chỉnh - điều này sẽ được áp dụng cho cả client và server.

Nếu bạn muốn ghi đè lựa chọn đó cho client, bạn có thể định nghĩa bean `ClientHttpConnector` của riêng mình và kiểm soát hoàn toàn cấu hình client.

Bạn có thể tìm hiểu thêm về [các tùy chọn cấu hình `WebClient` trong tài liệu tham khảo Spring Framework](https://docs.spring.io/spring-framework/reference/6.1/web/webflux-webclient/client-builder.html).

## Tùy chỉnh WebClient (WebClient Customization)

Có ba cách tiếp cận chính để tùy chỉnh `WebClient`, tùy thuộc vào phạm vi bạn muốn các tùy chỉnh được áp dụng rộng đến đâu.

Để phạm vi của mọi tùy chỉnh hẹp nhất có thể, hãy inject `WebClient.Builder` được tự động cấu hình rồi gọi các phương thức của nó khi cần. Các instance `WebClient.Builder` có trạng thái (stateful): Mọi thay đổi trên builder được phản ánh trong tất cả các client được tạo sau đó bằng nó. Nếu bạn muốn tạo nhiều client bằng cùng một builder, bạn cũng có thể cân nhắc clone builder bằng `WebClient.Builder other = builder.clone();`.

Để thực hiện một tùy chỉnh bổ sung, áp dụng cho toàn ứng dụng, lên tất cả các instance `WebClient.Builder`, bạn có thể khai báo các bean `WebClientCustomizer` và thay đổi `WebClient.Builder` cục bộ tại điểm inject.

Cuối cùng, bạn có thể quay về API gốc và dùng `WebClient.create()`. Trong trường hợp đó, không có auto-configuration hay `WebClientCustomizer` nào được áp dụng.

## Hỗ trợ SSL cho WebClient (WebClient SSL Support)

Nếu bạn cần cấu hình SSL tùy chỉnh trên `ClientHttpConnector` được `WebClient` sử dụng, bạn có thể inject một instance `WebClientSsl` có thể dùng với phương thức `apply` của builder.

Interface `WebClientSsl` cung cấp quyền truy cập tới mọi [SSL bundle](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.ssl.bundles) mà bạn đã định nghĩa trong file `application.properties` hoặc `application.yaml`.

Đoạn code sau cho thấy một ví dụ điển hình:

**Java**

```java
import reactor.core.publisher.Mono;

import org.springframework.boot.autoconfigure.web.reactive.function.client.WebClientSsl;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Service
public class MyService {

    private final WebClient webClient;

    public MyService(WebClient.Builder webClientBuilder, WebClientSsl ssl) {
        this.webClient = webClientBuilder.baseUrl("https://example.org").apply(ssl.fromBundle("mybundle")).build();
    }

    public Mono<Details> someRestCall(String name) {
        return this.webClient.get().uri("/{name}/details", name).retrieve().bodyToMono(Details.class);
    }

}
```

**Kotlin**

```kotlin
import org.springframework.boot.autoconfigure.web.reactive.function.client.WebClientSsl
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

@Service
class MyService(webClientBuilder: WebClient.Builder, ssl: WebClientSsl) {

    private val webClient: WebClient

    init {
        webClient = webClientBuilder.baseUrl("https://example.org")
                .apply(ssl.fromBundle("mybundle")).build()
    }

    fun someRestCall(name: String?): Mono<Details> {
        return webClient.get().uri("/{name}/details", name)
                .retrieve().bodyToMono(Details::class.java)
    }

}
```

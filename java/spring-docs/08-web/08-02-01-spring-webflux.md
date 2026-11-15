# 8.2.1. "Spring WebFlux Framework" (The "Spring WebFlux Framework")

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#web.reactive.webflux>

Spring WebFlux là web framework reactive mới được giới thiệu trong Spring Framework 5.0. Không giống như Spring MVC, nó không yêu cầu servlet API, hoàn toàn bất đồng bộ và không chặn (non-blocking), và hiện thực đặc tả [Reactive Streams](https://www.reactive-streams.org/) thông qua [project Reactor](https://projectreactor.io/).

Spring WebFlux có hai dạng: theo phong cách hàm (functional) và dựa trên annotation. Dạng dựa trên annotation khá gần với mô hình Spring MVC, như trong ví dụ sau:

**Java**

```java
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
public class MyRestController {

    private final UserRepository userRepository;

    private final CustomerRepository customerRepository;

    public MyRestController(UserRepository userRepository, CustomerRepository customerRepository) {
        this.userRepository = userRepository;
        this.customerRepository = customerRepository;
    }

    @GetMapping("/{userId}")
    public Mono<User> getUser(@PathVariable Long userId) {
        return this.userRepository.findById(userId);
    }

    @GetMapping("/{userId}/customers")
    public Flux<Customer> getUserCustomers(@PathVariable Long userId) {
        return this.userRepository.findById(userId).flatMapMany(this.customerRepository::findByUser);
    }

    @DeleteMapping("/{userId}")
    public Mono<Void> deleteUser(@PathVariable Long userId) {
        return this.userRepository.deleteById(userId);
    }

}
```

**Kotlin**

```kotlin
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@RestController
@RequestMapping("/users")
class MyRestController(private val userRepository: UserRepository, private val customerRepository: CustomerRepository) {

    @GetMapping("/{userId}")
    fun getUser(@PathVariable userId: Long): Mono<User?> {
        return userRepository.findById(userId)
    }

    @GetMapping("/{userId}/customers")
    fun getUserCustomers(@PathVariable userId: Long): Flux<Customer> {
        return userRepository.findById(userId).flatMapMany { user: User? ->
            customerRepository.findByUser(user)
        }
    }

    @DeleteMapping("/{userId}")
    fun deleteUser(@PathVariable userId: Long): Mono<Void> {
        return userRepository.deleteById(userId)
    }

}
```

WebFlux là một phần của Spring Framework và thông tin chi tiết có trong [tài liệu tham khảo](https://docs.spring.io/spring-framework/reference/6.1/web/webflux.html) của nó.

"WebFlux.fn", biến thể theo phong cách hàm, tách cấu hình định tuyến khỏi việc xử lý request thực tế, như trong ví dụ sau:

**Java**

```java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RequestPredicate;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RequestPredicates.accept;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@Configuration(proxyBeanMethods = false)
public class MyRoutingConfiguration {

    private static final RequestPredicate ACCEPT_JSON = accept(MediaType.APPLICATION_JSON);

    @Bean
    public RouterFunction<ServerResponse> monoRouterFunction(MyUserHandler userHandler) {
        return route()
                .GET("/{user}", ACCEPT_JSON, userHandler::getUser)
                .GET("/{user}/customers", ACCEPT_JSON, userHandler::getUserCustomers)
                .DELETE("/{user}", ACCEPT_JSON, userHandler::deleteUser)
                .build();
    }

}
```

**Kotlin**

```kotlin
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.server.RequestPredicates.DELETE
import org.springframework.web.reactive.function.server.RequestPredicates.GET
import org.springframework.web.reactive.function.server.RequestPredicates.accept
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.RouterFunctions
import org.springframework.web.reactive.function.server.ServerResponse

@Configuration(proxyBeanMethods = false)
class MyRoutingConfiguration {

    @Bean
    fun monoRouterFunction(userHandler: MyUserHandler): RouterFunction<ServerResponse> {
        return RouterFunctions.route(
            GET("/{user}").and(ACCEPT_JSON), userHandler::getUser).andRoute(
            GET("/{user}/customers").and(ACCEPT_JSON), userHandler::getUserCustomers).andRoute(
            DELETE("/{user}").and(ACCEPT_JSON), userHandler::deleteUser)
    }

    companion object {
        private val ACCEPT_JSON = accept(MediaType.APPLICATION_JSON)
    }

}
```

**Java**

```java
import reactor.core.publisher.Mono;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

@Component
public class MyUserHandler {

    public Mono<ServerResponse> getUser(ServerRequest request) {
        ...
    }

    public Mono<ServerResponse> getUserCustomers(ServerRequest request) {
        ...
    }

    public Mono<ServerResponse> deleteUser(ServerRequest request) {
        ...
    }

}
```

**Kotlin**

```kotlin
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import reactor.core.publisher.Mono

@Component
class MyUserHandler {

    fun getUser(request: ServerRequest?): Mono<ServerResponse> {
        return ServerResponse.ok().build()
    }

    fun getUserCustomers(request: ServerRequest?): Mono<ServerResponse> {
        return ServerResponse.ok().build()
    }

    fun deleteUser(request: ServerRequest?): Mono<ServerResponse> {
        return ServerResponse.ok().build()
    }

}
```

"WebFlux.fn" là một phần của Spring Framework và thông tin chi tiết có trong [tài liệu tham khảo](https://docs.spring.io/spring-framework/reference/6.1/web/webflux-functional.html) của nó.

> **MẸO (TIP):** Bạn có thể định nghĩa bao nhiêu bean `RouterFunction` tùy thích để module hóa định nghĩa của router. Các bean có thể được sắp thứ tự nếu bạn cần áp dụng độ ưu tiên.

Để bắt đầu, hãy thêm module `spring-boot-starter-webflux` vào ứng dụng.

> **LƯU Ý (NOTE):** Thêm cả hai module `spring-boot-starter-web` và `spring-boot-starter-webflux` vào ứng dụng sẽ khiến Spring Boot tự động cấu hình Spring MVC, chứ không phải WebFlux. Hành vi này được chọn vì nhiều lập trình viên Spring thêm `spring-boot-starter-webflux` vào ứng dụng Spring MVC của họ để dùng `WebClient` reactive. Bạn vẫn có thể áp đặt lựa chọn của mình bằng cách đặt loại ứng dụng đã chọn thành `SpringApplication.setWebApplicationType(WebApplicationType.REACTIVE)`.

## Auto-configuration cho Spring WebFlux (Spring WebFlux Auto-configuration)

Spring Boot cung cấp auto-configuration cho Spring WebFlux, hoạt động tốt với hầu hết các ứng dụng.

Auto-configuration thêm các tính năng sau bên trên các giá trị mặc định của Spring:

- Cấu hình codec cho các instance `HttpMessageReader` và `HttpMessageWriter` (được mô tả [ở phần sau của tài liệu này](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#web.reactive.webflux.httpcodecs)).
- Hỗ trợ phục vụ tài nguyên tĩnh, bao gồm hỗ trợ WebJars (được mô tả [ở phần sau của tài liệu này](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#web.servlet.spring-mvc.static-content)).

Nếu bạn muốn giữ các tính năng Spring Boot WebFlux và muốn thêm [cấu hình WebFlux](https://docs.spring.io/spring-framework/reference/6.1/web/webflux/config.html) bổ sung, bạn có thể thêm class `@Configuration` của riêng mình thuộc kiểu `WebFluxConfigurer` nhưng **không có** `@EnableWebFlux`.

Nếu bạn muốn kiểm soát hoàn toàn Spring WebFlux, bạn có thể thêm `@Configuration` của riêng mình được đánh annotation `@EnableWebFlux`.

## Conversion Service của Spring WebFlux (Spring WebFlux Conversion Service)

Nếu bạn muốn tùy chỉnh `ConversionService` được Spring WebFlux sử dụng, bạn có thể cung cấp một bean `WebFluxConfigurer` với phương thức `addFormatters`.

Việc chuyển đổi cũng có thể được tùy chỉnh bằng các configuration property `spring.webflux.format.*`. Khi không được cấu hình, các giá trị mặc định sau được sử dụng:

| Property | `DateTimeFormatter` |
|---|---|
| `spring.webflux.format.date` | `ofLocalizedDate(FormatStyle.SHORT)` |
| `spring.webflux.format.time` | `ofLocalizedTime(FormatStyle.SHORT)` |
| `spring.webflux.format.date-time` | `ofLocalizedDateTime(FormatStyle.SHORT)` |

## HTTP Codec với HttpMessageReader và HttpMessageWriter (HTTP Codecs with HttpMessageReaders and HttpMessageWriters)

Spring WebFlux dùng các interface `HttpMessageReader` và `HttpMessageWriter` để chuyển đổi HTTP request và response. Chúng được cấu hình bằng `CodecConfigurer` để có các giá trị mặc định hợp lý dựa trên các thư viện có sẵn trên classpath.

Spring Boot cung cấp các configuration property chuyên biệt cho codec, `spring.codec.*`. Nó cũng áp dụng thêm các tùy chỉnh bằng các instance `CodecCustomizer`. Ví dụ, các key cấu hình `spring.jackson.*` được áp dụng cho codec Jackson.

Nếu bạn cần thêm hoặc tùy chỉnh codec, bạn có thể tạo một component `CodecCustomizer` tùy chỉnh, như trong ví dụ sau:

**Java**

```java
import org.springframework.boot.web.codec.CodecCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.codec.ServerSentEventHttpMessageReader;

@Configuration(proxyBeanMethods = false)
public class MyCodecsConfiguration {

    @Bean
    public CodecCustomizer myCodecCustomizer() {
        return (configurer) -> {
            configurer.registerDefaults(false);
            configurer.customCodecs().register(new ServerSentEventHttpMessageReader());
            // ...
        };
    }

}
```

**Kotlin**

```kotlin
import org.springframework.boot.web.codec.CodecCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.http.codec.CodecConfigurer
import org.springframework.http.codec.ServerSentEventHttpMessageReader

class MyCodecsConfiguration {

    @Bean
    fun myCodecCustomizer(): CodecCustomizer {
        return CodecCustomizer { configurer: CodecConfigurer ->
            configurer.registerDefaults(false)
            configurer.customCodecs().register(ServerSentEventHttpMessageReader())
        }
    }

}
```

Bạn cũng có thể tận dụng [các serializer và deserializer JSON tùy chỉnh của Boot](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.json.jackson.custom-serializers-and-deserializers).

## Nội dung tĩnh (Static Content)

Theo mặc định, Spring Boot phục vụ nội dung tĩnh từ một thư mục tên là `/static` (hoặc `/public` hoặc `/resources` hoặc `/META-INF/resources`) trong classpath. Nó dùng `ResourceWebHandler` từ Spring WebFlux, nên bạn có thể thay đổi hành vi đó bằng cách thêm `WebFluxConfigurer` của riêng mình và ghi đè phương thức `addResourceHandlers`.

Theo mặc định, tài nguyên được ánh xạ trên `/**`, nhưng bạn có thể điều chỉnh điều đó bằng cách đặt property `spring.webflux.static-path-pattern`. Ví dụ, việc chuyển tất cả tài nguyên sang `/resources/**` có thể được thực hiện như sau:

**Properties**

```properties
spring.webflux.static-path-pattern=/resources/**
```

**Yaml**

```yaml
spring:
  webflux:
    static-path-pattern: "/resources/**"
```

Bạn cũng có thể tùy chỉnh các vị trí tài nguyên tĩnh bằng `spring.web.resources.static-locations`. Làm như vậy sẽ thay thế các giá trị mặc định bằng một danh sách các vị trí thư mục. Nếu bạn làm vậy, việc phát hiện trang chào mừng mặc định sẽ chuyển sang các vị trí tùy chỉnh của bạn. Vì vậy, nếu có một `index.html` ở bất kỳ vị trí nào của bạn khi khởi động, nó sẽ là trang chủ của ứng dụng.

Ngoài các vị trí tài nguyên tĩnh "tiêu chuẩn" đã liệt kê ở trên, một trường hợp đặc biệt được dành cho [nội dung Webjars](https://www.webjars.org/). Theo mặc định, mọi tài nguyên có đường dẫn trong `/webjars/**` được phục vụ từ các file jar nếu chúng được đóng gói theo định dạng Webjars. Đường dẫn có thể được tùy chỉnh bằng property `spring.webflux.webjars-path-pattern`.

> **MẸO (TIP):** Các ứng dụng Spring WebFlux không phụ thuộc chặt chẽ vào servlet API, vì vậy chúng không thể được triển khai dưới dạng file war và không dùng thư mục `src/main/webapp`.

## Trang chào mừng (Welcome Page)

Spring Boot hỗ trợ cả trang chào mừng tĩnh và dựa trên template. Đầu tiên, nó tìm file `index.html` trong các vị trí nội dung tĩnh đã cấu hình. Nếu không tìm thấy, nó sẽ tìm template `index`. Nếu tìm thấy một trong hai, nó sẽ tự động được dùng làm trang chào mừng của ứng dụng.

Điều này chỉ đóng vai trò dự phòng cho các route index thực sự do ứng dụng định nghĩa. Thứ tự được xác định bởi thứ tự của các bean `HandlerMapping`, theo mặc định như sau:

| HandlerMapping | Mô tả |
|---|---|
| `RouterFunctionMapping` | Các endpoint được khai báo bằng bean `RouterFunction` |
| `RequestMappingHandlerMapping` | Các endpoint được khai báo trong bean `@Controller` |
| `RouterFunctionMapping` cho trang chào mừng | Hỗ trợ trang chào mừng |

## Template engine (Template Engines)

Ngoài các REST web service, bạn cũng có thể dùng Spring WebFlux để phục vụ nội dung HTML động. Spring WebFlux hỗ trợ nhiều công nghệ template khác nhau, bao gồm Thymeleaf, FreeMarker, và Mustache.

Spring Boot bao gồm hỗ trợ auto-configuration cho các template engine sau:

- [FreeMarker](https://freemarker.apache.org/docs/)
- [Thymeleaf](https://www.thymeleaf.org)
- [Mustache](https://mustache.github.io/)

Khi bạn dùng một trong các template engine này với cấu hình mặc định, các template của bạn được tự động lấy từ `src/main/resources/templates`.

## Xử lý lỗi (Error Handling)

Spring Boot cung cấp một `WebExceptionHandler` xử lý tất cả các lỗi một cách hợp lý. Vị trí của nó trong thứ tự xử lý là ngay trước các handler do WebFlux cung cấp, vốn được xem xét cuối cùng. Với các client là máy, nó tạo ra một response JSON với chi tiết về lỗi, HTTP status, và thông điệp exception. Với các client là trình duyệt, có một error handler "whitelabel" hiển thị cùng dữ liệu đó ở định dạng HTML. Bạn cũng có thể cung cấp các template HTML của riêng mình để hiển thị lỗi (xem [phần tiếp theo](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#web.reactive.webflux.error-handling.error-pages)).

Trước khi tùy chỉnh trực tiếp việc xử lý lỗi trong Spring Boot, bạn có thể tận dụng hỗ trợ [RFC 7807 Problem Details](https://docs.spring.io/spring-framework/reference/6.1/web/webflux/ann-rest-exceptions.html) trong Spring WebFlux. Spring WebFlux có thể tạo ra các thông báo lỗi tùy chỉnh với media type `application/problem+json`, như:

```json
{
  "type": "https://example.org/problems/unknown-project",
  "title": "Unknown project",
  "status": 404,
  "detail": "No project found for id 'spring-unknown'",
  "instance": "/projects/spring-unknown"
}
```

Hỗ trợ này có thể được bật bằng cách đặt `spring.webflux.problemdetails.enabled` thành `true`.

Bước đầu tiên để tùy chỉnh tính năng này thường là dùng cơ chế hiện có nhưng thay thế hoặc bổ sung nội dung lỗi. Để làm vậy, bạn có thể thêm một bean kiểu `ErrorAttributes`.

Để thay đổi hành vi xử lý lỗi, bạn có thể hiện thực `ErrorWebExceptionHandler` và đăng ký một bean definition thuộc kiểu đó. Vì `ErrorWebExceptionHandler` khá cấp thấp, Spring Boot cũng cung cấp một `AbstractErrorWebExceptionHandler` tiện lợi để cho phép bạn xử lý lỗi theo phong cách hàm của WebFlux, như trong ví dụ sau:

**Java**

```java
import reactor.core.publisher.Mono;

import org.springframework.boot.autoconfigure.web.WebProperties;
import org.springframework.boot.autoconfigure.web.reactive.error.AbstractErrorWebExceptionHandler;
import org.springframework.boot.web.reactive.error.ErrorAttributes;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.reactive.function.server.ServerResponse.BodyBuilder;

@Component
public class MyErrorWebExceptionHandler extends AbstractErrorWebExceptionHandler {

    public MyErrorWebExceptionHandler(ErrorAttributes errorAttributes, WebProperties webProperties,
            ApplicationContext applicationContext, ServerCodecConfigurer serverCodecConfigurer) {
        super(errorAttributes, webProperties.getResources(), applicationContext);
        setMessageReaders(serverCodecConfigurer.getReaders());
        setMessageWriters(serverCodecConfigurer.getWriters());
    }

    @Override
    protected RouterFunction<ServerResponse> getRoutingFunction(ErrorAttributes errorAttributes) {
        return RouterFunctions.route(this::acceptsXml, this::handleErrorAsXml);
    }

    private boolean acceptsXml(ServerRequest request) {
        return request.headers().accept().contains(MediaType.APPLICATION_XML);
    }

    public Mono<ServerResponse> handleErrorAsXml(ServerRequest request) {
        BodyBuilder builder = ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR);
        // ... additional builder calls
        return builder.build();
    }

}
```

**Kotlin**

```kotlin
import org.springframework.boot.autoconfigure.web.WebProperties
import org.springframework.boot.autoconfigure.web.reactive.error.AbstractErrorWebExceptionHandler
import org.springframework.boot.web.reactive.error.ErrorAttributes
import org.springframework.context.ApplicationContext
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.codec.ServerCodecConfigurer
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.RouterFunctions
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import reactor.core.publisher.Mono

@Component
class MyErrorWebExceptionHandler(
        errorAttributes: ErrorAttributes, webProperties: WebProperties,
        applicationContext: ApplicationContext, serverCodecConfigurer: ServerCodecConfigurer
) : AbstractErrorWebExceptionHandler(errorAttributes, webProperties.resources, applicationContext) {

    init {
        setMessageReaders(serverCodecConfigurer.readers)
        setMessageWriters(serverCodecConfigurer.writers)
    }

    override fun getRoutingFunction(errorAttributes: ErrorAttributes): RouterFunction<ServerResponse> {
        return RouterFunctions.route(this::acceptsXml, this::handleErrorAsXml)
    }

    private fun acceptsXml(request: ServerRequest): Boolean {
        return request.headers().accept().contains(MediaType.APPLICATION_XML)
    }

    fun handleErrorAsXml(request: ServerRequest): Mono<ServerResponse> {
        val builder = ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR)
        // ... additional builder calls
        return builder.build()
    }

}
```

Để có bức tranh hoàn chỉnh hơn, bạn cũng có thể kế thừa trực tiếp `DefaultErrorWebExceptionHandler` và ghi đè các phương thức cụ thể.

Trong một số trường hợp, các lỗi được xử lý ở cấp controller không được ghi nhận bởi web observation hoặc [hạ tầng metrics](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#actuator.metrics.supported.spring-webflux). Ứng dụng có thể đảm bảo những exception như vậy được ghi nhận với observation bằng cách [đặt exception đã xử lý lên observation context](https://docs.spring.io/spring-framework/reference/6.1/integration/observability.html#observability.http-server.reactive).

### Trang lỗi tùy chỉnh (Custom Error Pages)

Nếu bạn muốn hiển thị một trang lỗi HTML tùy chỉnh cho một status code nhất định, bạn có thể thêm các view phân giải từ `error/*`, ví dụ bằng cách thêm file vào thư mục `/error`. Trang lỗi có thể là HTML tĩnh (tức là được thêm vào bất kỳ thư mục tài nguyên tĩnh nào) hoặc được xây dựng bằng template. Tên file nên là status code chính xác, một mặt nạ chuỗi status code, hoặc `error` cho trang mặc định nếu không có gì khác khớp. Lưu ý rằng đường dẫn tới view lỗi mặc định là `error/error`, trong khi với Spring MVC view lỗi mặc định là `error`.

Ví dụ, để ánh xạ `404` tới một file HTML tĩnh, cấu trúc thư mục của bạn sẽ như sau:

```
src/
 +- main/
     +- java/
     |   + <source code>
     +- resources/
         +- public/
             +- error/
             |   +- 404.html
             +- <other public assets>
```

Để ánh xạ tất cả các lỗi `5xx` bằng một template Mustache, cấu trúc thư mục của bạn sẽ như sau:

```
src/
 +- main/
     +- java/
     |   + <source code>
     +- resources/
         +- templates/
             +- error/
             |   +- 5xx.mustache
             +- <other templates>
```

## Web Filter (Web Filters)

Spring WebFlux cung cấp interface `WebFilter` có thể được hiện thực để lọc các trao đổi HTTP request-response. Các bean `WebFilter` được tìm thấy trong application context sẽ được tự động dùng để lọc mỗi lần trao đổi.

Khi thứ tự của các filter là quan trọng, chúng có thể hiện thực `Ordered` hoặc được đánh annotation `@Order`. Auto-configuration của Spring Boot có thể cấu hình web filter cho bạn. Khi làm vậy, các thứ tự trong bảng sau sẽ được sử dụng:

| Web Filter | Thứ tự |
|---|---|
| `WebFilterChainProxy` (Spring Security) | `-100` |
| `HttpExchangesWebFilter` | `Ordered.LOWEST_PRECEDENCE - 10` |

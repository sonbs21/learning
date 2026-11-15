# 8.1.1. "Spring Web MVC Framework" (The "Spring Web MVC Framework")

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#web.servlet.spring-mvc>

[Spring Web MVC framework](https://docs.spring.io/spring-framework/reference/6.1/web/webmvc.html) (thường được gọi là "Spring MVC") là một web framework "model view controller" phong phú. Spring MVC cho phép bạn tạo các bean `@Controller` hoặc `@RestController` đặc biệt để xử lý các HTTP request đến. Các phương thức trong controller được ánh xạ tới HTTP bằng các annotation `@RequestMapping`.

Đoạn code sau cho thấy một `@RestController` điển hình phục vụ dữ liệu JSON:

**Java**

```java
import java.util.List;

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
    public User getUser(@PathVariable Long userId) {
        return this.userRepository.findById(userId).get();
    }

    @GetMapping("/{userId}/customers")
    public List<Customer> getUserCustomers(@PathVariable Long userId) {
        return this.userRepository.findById(userId).map(this.customerRepository::findByUser).get();
    }

    @DeleteMapping("/{userId}")
    public void deleteUser(@PathVariable Long userId) {
        this.userRepository.deleteById(userId);
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


@RestController
@RequestMapping("/users")
class MyRestController(private val userRepository: UserRepository, private val customerRepository: CustomerRepository) {

    @GetMapping("/{userId}")
    fun getUser(@PathVariable userId: Long): User {
        return userRepository.findById(userId).get()
    }

    @GetMapping("/{userId}/customers")
    fun getUserCustomers(@PathVariable userId: Long): List<Customer> {
        return userRepository.findById(userId).map(customerRepository::findByUser).get()
    }

    @DeleteMapping("/{userId}")
    fun deleteUser(@PathVariable userId: Long) {
        userRepository.deleteById(userId)
    }

}
```

"WebMvc.fn", biến thể theo phong cách hàm (functional), tách cấu hình định tuyến (routing) khỏi việc xử lý request thực tế, như trong ví dụ sau:

**Java**

```java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.function.RequestPredicate;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import static org.springframework.web.servlet.function.RequestPredicates.accept;
import static org.springframework.web.servlet.function.RouterFunctions.route;

@Configuration(proxyBeanMethods = false)
public class MyRoutingConfiguration {

    private static final RequestPredicate ACCEPT_JSON = accept(MediaType.APPLICATION_JSON);

    @Bean
    public RouterFunction<ServerResponse> routerFunction(MyUserHandler userHandler) {
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
import org.springframework.web.servlet.function.RequestPredicates.accept
import org.springframework.web.servlet.function.RouterFunction
import org.springframework.web.servlet.function.RouterFunctions
import org.springframework.web.servlet.function.ServerResponse

@Configuration(proxyBeanMethods = false)
class MyRoutingConfiguration {

    @Bean
    fun routerFunction(userHandler: MyUserHandler): RouterFunction<ServerResponse> {
        return RouterFunctions.route()
            .GET("/{user}", ACCEPT_JSON, userHandler::getUser)
            .GET("/{user}/customers", ACCEPT_JSON, userHandler::getUserCustomers)
            .DELETE("/{user}", ACCEPT_JSON, userHandler::deleteUser)
            .build()
    }

    companion object {
        private val ACCEPT_JSON = accept(MediaType.APPLICATION_JSON)
    }

}
```

**Java**

```java
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

@Component
public class MyUserHandler {

    public ServerResponse getUser(ServerRequest request) {
        ...
        return ServerResponse.ok().build();
    }

    public ServerResponse getUserCustomers(ServerRequest request) {
        ...
        return ServerResponse.ok().build();
    }

    public ServerResponse deleteUser(ServerRequest request) {
        ...
        return ServerResponse.ok().build();
    }

}
```

**Kotlin**

```kotlin
import org.springframework.stereotype.Component
import org.springframework.web.servlet.function.ServerRequest
import org.springframework.web.servlet.function.ServerResponse

@Component
class MyUserHandler {

    fun getUser(request: ServerRequest?): ServerResponse {
        return ServerResponse.ok().build()
    }

    fun getUserCustomers(request: ServerRequest?): ServerResponse {
        return ServerResponse.ok().build()
    }

    fun deleteUser(request: ServerRequest?): ServerResponse {
        return ServerResponse.ok().build()
    }

}
```

Spring MVC là một phần của Spring Framework cốt lõi, và thông tin chi tiết có trong [tài liệu tham khảo](https://docs.spring.io/spring-framework/reference/6.1/web/webmvc.html). Cũng có một số hướng dẫn về Spring MVC tại [spring.io/guides](https://spring.io/guides).

> **MẸO (TIP):** Bạn có thể định nghĩa bao nhiêu bean `RouterFunction` tùy thích để module hóa định nghĩa của router. Các bean có thể được sắp thứ tự nếu bạn cần áp dụng độ ưu tiên.

## Auto-configuration cho Spring MVC (Spring MVC Auto-configuration)

Spring Boot cung cấp auto-configuration cho Spring MVC, hoạt động tốt với hầu hết các ứng dụng. Nó thay thế nhu cầu dùng `@EnableWebMvc` và hai thứ này không thể dùng cùng nhau. Ngoài các giá trị mặc định của Spring MVC, auto-configuration cung cấp các tính năng sau:

- Bao gồm các bean `ContentNegotiatingViewResolver` và `BeanNameViewResolver`.
- Hỗ trợ phục vụ tài nguyên tĩnh, bao gồm hỗ trợ WebJars (được đề cập [ở phần sau của tài liệu này](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#web.servlet.spring-mvc.static-content)).
- Tự động đăng ký các bean `Converter`, `GenericConverter`, và `Formatter`.
- Hỗ trợ `HttpMessageConverters` (được đề cập [ở phần sau của tài liệu này](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#web.servlet.spring-mvc.message-converters)).
- Tự động đăng ký `MessageCodesResolver` (được đề cập [ở phần sau của tài liệu này](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#web.servlet.spring-mvc.message-codes)).
- Hỗ trợ `index.html` tĩnh.
- Tự động dùng bean `ConfigurableWebBindingInitializer` (được đề cập [ở phần sau của tài liệu này](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#web.servlet.spring-mvc.binding-initializer)).

Nếu bạn muốn giữ các tùy chỉnh Spring Boot MVC đó và thực hiện thêm [các tùy chỉnh MVC](https://docs.spring.io/spring-framework/reference/6.1/web/webmvc.html) (interceptor, formatter, view controller, và các tính năng khác), bạn có thể thêm class `@Configuration` của riêng mình thuộc kiểu `WebMvcConfigurer` nhưng **không có** `@EnableWebMvc`.

Nếu bạn muốn cung cấp các instance tùy chỉnh của `RequestMappingHandlerMapping`, `RequestMappingHandlerAdapter`, hoặc `ExceptionHandlerExceptionResolver`, mà vẫn giữ các tùy chỉnh Spring Boot MVC, bạn có thể khai báo một bean kiểu `WebMvcRegistrations` và dùng nó để cung cấp các instance tùy chỉnh của những component đó. Các instance tùy chỉnh sẽ tiếp tục được Spring MVC khởi tạo và cấu hình thêm. Để tham gia vào, và nếu muốn, ghi đè quá trình xử lý tiếp theo đó, nên dùng một `WebMvcConfigurer`.

Nếu bạn không muốn dùng auto-configuration và muốn kiểm soát hoàn toàn Spring MVC, hãy thêm `@Configuration` của riêng bạn được đánh annotation `@EnableWebMvc`. Ngoài ra, hãy thêm `DelegatingWebMvcConfiguration` được đánh annotation `@Configuration` của riêng bạn như mô tả trong Javadoc của `@EnableWebMvc`.

## Conversion Service của Spring MVC (Spring MVC Conversion Service)

Spring MVC dùng một `ConversionService` khác với cái được dùng để chuyển đổi các giá trị từ file `application.properties` hoặc `application.yaml`. Điều này có nghĩa là các converter `Period`, `Duration` và `DataSize` không có sẵn và các annotation `@DurationUnit` và `@DataSizeUnit` sẽ bị bỏ qua.

Nếu bạn muốn tùy chỉnh `ConversionService` được Spring MVC sử dụng, bạn có thể cung cấp một bean `WebMvcConfigurer` với phương thức `addFormatters`. Từ phương thức này, bạn có thể đăng ký bất kỳ converter nào bạn thích, hoặc bạn có thể ủy quyền cho các phương thức tĩnh có sẵn trên `ApplicationConversionService`.

Việc chuyển đổi cũng có thể được tùy chỉnh bằng các configuration property `spring.mvc.format.*`. Khi không được cấu hình, các giá trị mặc định sau được sử dụng:

| Property | `DateTimeFormatter` |
|---|---|
| `spring.mvc.format.date` | `ofLocalizedDate(FormatStyle.SHORT)` |
| `spring.mvc.format.time` | `ofLocalizedTime(FormatStyle.SHORT)` |
| `spring.mvc.format.date-time` | `ofLocalizedDateTime(FormatStyle.SHORT)` |

## HttpMessageConverters

Spring MVC dùng interface `HttpMessageConverter` để chuyển đổi các HTTP request và response. Các giá trị mặc định hợp lý có sẵn ngay. Ví dụ, object có thể được tự động chuyển đổi thành JSON (bằng thư viện Jackson) hoặc XML (bằng extension Jackson XML, nếu có, hoặc bằng JAXB nếu không có extension Jackson XML). Theo mặc định, chuỗi được mã hóa bằng `UTF-8`.

Nếu bạn cần thêm hoặc tùy chỉnh các converter, bạn có thể dùng class `HttpMessageConverters` của Spring Boot, như trong đoạn sau:

**Java**

```java
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;

@Configuration(proxyBeanMethods = false)
public class MyHttpMessageConvertersConfiguration {

    @Bean
    public HttpMessageConverters customConverters() {
        HttpMessageConverter<?> additional = new AdditionalHttpMessageConverter();
        HttpMessageConverter<?> another = new AnotherHttpMessageConverter();
        return new HttpMessageConverters(additional, another);
    }

}
```

**Kotlin**

```kotlin
import org.springframework.boot.autoconfigure.http.HttpMessageConverters
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.converter.HttpMessageConverter

@Configuration(proxyBeanMethods = false)
class MyHttpMessageConvertersConfiguration {

    @Bean
    fun customConverters(): HttpMessageConverters {
        val additional: HttpMessageConverter<*> = AdditionalHttpMessageConverter()
        val another: HttpMessageConverter<*> = AnotherHttpMessageConverter()
        return HttpMessageConverters(additional, another)
    }

}
```

Mọi bean `HttpMessageConverter` có trong context đều được thêm vào danh sách converter. Bạn cũng có thể ghi đè các converter mặc định theo cùng cách.

## MessageCodesResolver

Spring MVC có một chiến lược để sinh mã lỗi nhằm hiển thị thông báo lỗi từ các lỗi binding: `MessageCodesResolver`. Nếu bạn đặt property `spring.mvc.message-codes-resolver-format` là `PREFIX_ERROR_CODE` hoặc `POSTFIX_ERROR_CODE`, Spring Boot sẽ tạo một cái cho bạn (xem enum trong [`DefaultMessageCodesResolver.Format`](https://docs.spring.io/spring-framework/docs/6.1.6/javadoc-api/org/springframework/validation/DefaultMessageCodesResolver.Format.html)).

## Nội dung tĩnh (Static Content)

Theo mặc định, Spring Boot phục vụ nội dung tĩnh từ một thư mục tên là `/static` (hoặc `/public` hoặc `/resources` hoặc `/META-INF/resources`) trong classpath hoặc từ thư mục gốc của `ServletContext`. Nó dùng `ResourceHttpRequestHandler` từ Spring MVC, nên bạn có thể thay đổi hành vi đó bằng cách thêm `WebMvcConfigurer` của riêng mình và ghi đè phương thức `addResourceHandlers`.

Trong một ứng dụng web độc lập, servlet mặc định từ container không được bật. Nó có thể được bật bằng property `server.servlet.register-default-servlet`.

Servlet mặc định đóng vai trò dự phòng, phục vụ nội dung từ thư mục gốc của `ServletContext` nếu Spring quyết định không xử lý nó. Hầu hết thời gian, điều này không xảy ra (trừ khi bạn sửa đổi cấu hình MVC mặc định), vì Spring luôn có thể xử lý request thông qua `DispatcherServlet`.

Theo mặc định, tài nguyên được ánh xạ trên `/**`, nhưng bạn có thể điều chỉnh điều đó bằng property `spring.mvc.static-path-pattern`. Ví dụ, việc chuyển tất cả tài nguyên sang `/resources/**` có thể được thực hiện như sau:

**Properties**

```properties
spring.mvc.static-path-pattern=/resources/**
```

**Yaml**

```yaml
spring:
  mvc:
    static-path-pattern: "/resources/**"
```

Bạn cũng có thể tùy chỉnh các vị trí tài nguyên tĩnh bằng property `spring.web.resources.static-locations` (thay thế các giá trị mặc định bằng một danh sách các vị trí thư mục). Đường dẫn servlet context gốc, `"/"`, cũng được tự động thêm vào làm một vị trí.

Ngoài các vị trí tài nguyên tĩnh "tiêu chuẩn" đã đề cập ở trên, một trường hợp đặc biệt được dành cho [nội dung Webjars](https://www.webjars.org/). Theo mặc định, mọi tài nguyên có đường dẫn trong `/webjars/**` được phục vụ từ các file jar nếu chúng được đóng gói theo định dạng Webjars. Đường dẫn có thể được tùy chỉnh bằng property `spring.mvc.webjars-path-pattern`.

> **MẸO (TIP):** Đừng dùng thư mục `src/main/webapp` nếu ứng dụng của bạn được đóng gói thành jar. Mặc dù thư mục này là một tiêu chuẩn phổ biến, nó **chỉ** hoạt động với việc đóng gói war, và nó bị hầu hết các công cụ build âm thầm bỏ qua nếu bạn tạo jar.

Spring Boot cũng hỗ trợ các tính năng xử lý tài nguyên nâng cao do Spring MVC cung cấp, cho phép các trường hợp sử dụng như cache-busting cho tài nguyên tĩnh hoặc dùng URL không phụ thuộc phiên bản cho Webjars.

Để dùng URL không phụ thuộc phiên bản cho Webjars, hãy thêm dependency `webjars-locator-core`. Sau đó khai báo Webjar của bạn. Lấy jQuery làm ví dụ, thêm `"/webjars/jquery/jquery.min.js"` sẽ cho ra `"/webjars/jquery/x.y.z/jquery.min.js"`, trong đó `x.y.z` là phiên bản Webjar.

> **LƯU Ý (NOTE):** Nếu bạn dùng JBoss, bạn cần khai báo dependency `webjars-locator-jboss-vfs` thay vì `webjars-locator-core`. Nếu không, tất cả các Webjar sẽ phân giải thành `404`.

Để dùng cache busting, cấu hình sau thiết lập một giải pháp cache busting cho tất cả tài nguyên tĩnh, thực chất là thêm một mã băm nội dung (content hash), chẳng hạn như `<link href="/css/spring-2a2d595e6ed9a0b24f027f2b63b134d6.css"/>`, vào URL:

**Properties**

```properties
spring.web.resources.chain.strategy.content.enabled=true
spring.web.resources.chain.strategy.content.paths=/**
```

**Yaml**

```yaml
spring:
  web:
    resources:
      chain:
        strategy:
          content:
            enabled: true
            paths: "/**"
```

> **LƯU Ý (NOTE):** Các link tới tài nguyên được viết lại trong template tại runtime, nhờ một `ResourceUrlEncodingFilter` được tự động cấu hình cho Thymeleaf và FreeMarker. Bạn nên tự khai báo filter này khi dùng JSP. Các template engine khác hiện chưa được hỗ trợ tự động nhưng có thể được hỗ trợ với các macro/helper template tùy chỉnh và việc dùng [`ResourceUrlProvider`](https://docs.spring.io/spring-framework/docs/6.1.6/javadoc-api/org/springframework/web/servlet/resource/ResourceUrlProvider.html).

Khi nạp tài nguyên động, ví dụ bằng một JavaScript module loader, việc đổi tên file không phải là một lựa chọn. Đó là lý do các chiến lược khác cũng được hỗ trợ và có thể được kết hợp. Chiến lược "fixed" thêm một chuỗi phiên bản tĩnh vào URL mà không thay đổi tên file, như trong ví dụ sau:

**Properties**

```properties
spring.web.resources.chain.strategy.content.enabled=true
spring.web.resources.chain.strategy.content.paths=/**
spring.web.resources.chain.strategy.fixed.enabled=true
spring.web.resources.chain.strategy.fixed.paths=/js/lib/
spring.web.resources.chain.strategy.fixed.version=v12
```

**Yaml**

```yaml
spring:
  web:
    resources:
      chain:
        strategy:
          content:
            enabled: true
            paths: "/**"
          fixed:
            enabled: true
            paths: "/js/lib/"
            version: "v12"
```

Với cấu hình này, các JavaScript module nằm dưới `"/js/lib/"` dùng chiến lược đánh phiên bản cố định (`"/v12/js/lib/mymodule.js"`), trong khi các tài nguyên khác vẫn dùng chiến lược theo nội dung (`<link href="/css/spring-2a2d595e6ed9a0b24f027f2b63b134d6.css"/>`).

Xem [`WebProperties.Resources`](https://github.com/spring-projects/spring-boot/tree/v3.2.5/spring-boot-project/spring-boot-autoconfigure/src/main/java/org/springframework/boot/autoconfigure/web/WebProperties.java) để biết thêm các tùy chọn được hỗ trợ.

> **MẸO (TIP):** Tính năng này đã được mô tả kỹ lưỡng trong một [bài blog](https://spring.io/blog/2014/07/24/spring-framework-4-1-handling-static-web-resources) chuyên biệt và trong [tài liệu tham khảo](https://docs.spring.io/spring-framework/reference/6.1/web/webmvc/mvc-config/static-resources.html) của Spring Framework.

## Trang chào mừng (Welcome Page)

Spring Boot hỗ trợ cả trang chào mừng tĩnh và dựa trên template. Đầu tiên, nó tìm file `index.html` trong các vị trí nội dung tĩnh đã cấu hình. Nếu không tìm thấy, nó sẽ tìm template `index`. Nếu tìm thấy một trong hai, nó sẽ tự động được dùng làm trang chào mừng của ứng dụng.

Điều này chỉ đóng vai trò dự phòng cho các route index thực sự do ứng dụng định nghĩa. Thứ tự được xác định bởi thứ tự của các bean `HandlerMapping`, theo mặc định như sau:

| HandlerMapping | Mô tả |
|---|---|
| `RouterFunctionMapping` | Các endpoint được khai báo bằng bean `RouterFunction` |
| `RequestMappingHandlerMapping` | Các endpoint được khai báo trong bean `@Controller` |
| `WelcomePageHandlerMapping` | Hỗ trợ trang chào mừng |

## Favicon tùy chỉnh (Custom Favicon)

Cũng như với các tài nguyên tĩnh khác, Spring Boot kiểm tra `favicon.ico` trong các vị trí nội dung tĩnh đã cấu hình. Nếu có file như vậy, nó sẽ tự động được dùng làm favicon của ứng dụng.

## So khớp đường dẫn và đàm phán nội dung (Path Matching and Content Negotiation)

Spring MVC có thể ánh xạ các HTTP request đến tới các handler bằng cách xem đường dẫn request và so khớp nó với các ánh xạ được định nghĩa trong ứng dụng (ví dụ, các annotation `@GetMapping` trên các phương thức Controller).

Spring Boot chọn vô hiệu hóa việc so khớp mẫu hậu tố (suffix pattern matching) theo mặc định, nghĩa là các request như `"GET /projects/spring-boot.json"` sẽ không được khớp với các ánh xạ `@GetMapping("/projects/spring-boot")`. Đây được coi là một [best practice cho các ứng dụng Spring MVC](https://docs.spring.io/spring-framework/reference/6.1/web/webmvc/mvc-controller/ann-requestmapping.html#mvc-ann-requestmapping-suffix-pattern-match). Tính năng này chủ yếu hữu ích trong quá khứ cho các HTTP client không gửi header request "Accept" đúng; chúng ta cần đảm bảo gửi đúng Content Type cho client. Ngày nay, đàm phán nội dung (Content Negotiation) đáng tin cậy hơn nhiều.

Có những cách khác để xử lý các HTTP client không gửi nhất quán header request "Accept" đúng. Thay vì dùng so khớp hậu tố, chúng ta có thể dùng một query parameter để đảm bảo các request như `"GET /projects/spring-boot?format=json"` sẽ được ánh xạ tới `@GetMapping("/projects/spring-boot")`:

**Properties**

```properties
spring.mvc.contentnegotiation.favor-parameter=true
```

**Yaml**

```yaml
spring:
  mvc:
    contentnegotiation:
      favor-parameter: true
```

Hoặc nếu bạn muốn dùng một tên tham số khác:

**Properties**

```properties
spring.mvc.contentnegotiation.favor-parameter=true
spring.mvc.contentnegotiation.parameter-name=myparam
```

**Yaml**

```yaml
spring:
  mvc:
    contentnegotiation:
      favor-parameter: true
      parameter-name: "myparam"
```

Hầu hết các media type tiêu chuẩn đều được hỗ trợ sẵn, nhưng bạn cũng có thể định nghĩa các media type mới:

**Properties**

```properties
spring.mvc.contentnegotiation.media-types.markdown=text/markdown
```

**Yaml**

```yaml
spring:
  mvc:
    contentnegotiation:
      media-types:
        markdown: "text/markdown"
```

Kể từ Spring Framework 5.3, Spring MVC hỗ trợ hai chiến lược để so khớp đường dẫn request với controller. Theo mặc định, Spring Boot dùng chiến lược `PathPatternParser`. `PathPatternParser` là một [implementation được tối ưu hóa](https://spring.io/blog/2020/06/30/url-matching-with-pathpattern-in-spring-mvc) nhưng có một số hạn chế so với chiến lược `AntPathMatcher`. `PathPatternParser` hạn chế việc dùng [một số biến thể mẫu đường dẫn](https://docs.spring.io/spring-framework/reference/6.1/web/webmvc/mvc-controller/ann-requestmapping.html#mvc-ann-requestmapping-uri-templates). Nó cũng không tương thích với việc cấu hình `DispatcherServlet` với một tiền tố đường dẫn (`spring.mvc.servlet.path`).

Chiến lược có thể được cấu hình bằng configuration property `spring.mvc.pathmatch.matching-strategy`, như trong ví dụ sau:

**Properties**

```properties
spring.mvc.pathmatch.matching-strategy=ant-path-matcher
```

**Yaml**

```yaml
spring:
  mvc:
    pathmatch:
      matching-strategy: "ant-path-matcher"
```

Theo mặc định, Spring MVC sẽ gửi response lỗi 404 Not Found nếu không tìm thấy handler cho một request. Để `NoHandlerFoundException` được ném ra thay thế, hãy đặt configprop:spring.mvc.throw-exception-if-no-handler-found thành `true`. Lưu ý rằng, theo mặc định, [việc phục vụ nội dung tĩnh](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#web.servlet.spring-mvc.static-content) được ánh xạ tới `/**` và do đó sẽ cung cấp một handler cho mọi request. Để `NoHandlerFoundException` được ném ra, bạn cũng phải đặt `spring.mvc.static-path-pattern` thành một giá trị cụ thể hơn như `/resources/**` hoặc đặt `spring.web.resources.add-mappings` thành `false` để tắt hoàn toàn việc phục vụ nội dung tĩnh.

## ConfigurableWebBindingInitializer

Spring MVC dùng một `WebBindingInitializer` để khởi tạo một `WebDataBinder` cho một request cụ thể. Nếu bạn tạo `@Bean` `ConfigurableWebBindingInitializer` của riêng mình, Spring Boot sẽ tự động cấu hình Spring MVC để dùng nó.

## Template engine (Template Engines)

Ngoài các REST web service, bạn cũng có thể dùng Spring MVC để phục vụ nội dung HTML động. Spring MVC hỗ trợ nhiều công nghệ template khác nhau, bao gồm Thymeleaf, FreeMarker, và JSP. Ngoài ra, nhiều template engine khác cũng có tích hợp Spring MVC riêng.

Spring Boot bao gồm hỗ trợ auto-configuration cho các template engine sau:

- [FreeMarker](https://freemarker.apache.org/docs/)
- [Groovy](https://docs.groovy-lang.org/docs/next/html/documentation/template-engines.html#_the_markuptemplateengine)
- [Thymeleaf](https://www.thymeleaf.org)
- [Mustache](https://mustache.github.io/)

> **MẸO (TIP):** Nếu có thể, nên tránh dùng JSP. Có một số [hạn chế đã biết](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#web.servlet.embedded-container.jsp-limitations) khi dùng chúng với các servlet container nhúng.

Khi bạn dùng một trong các template engine này với cấu hình mặc định, các template của bạn được tự động lấy từ `src/main/resources/templates`.

> **MẸO (TIP):** Tùy thuộc vào cách bạn chạy ứng dụng, IDE của bạn có thể sắp xếp classpath khác nhau. Chạy ứng dụng trong IDE từ phương thức main của nó cho ra một thứ tự khác với khi bạn chạy ứng dụng bằng Maven hoặc Gradle hoặc từ jar đã đóng gói. Điều này có thể khiến Spring Boot không tìm thấy template mong đợi. Nếu bạn gặp vấn đề này, bạn có thể sắp xếp lại classpath trong IDE để đặt các class và resource của module lên đầu.

## Xử lý lỗi (Error Handling)

Theo mặc định, Spring Boot cung cấp một ánh xạ `/error` xử lý tất cả các lỗi một cách hợp lý, và nó được đăng ký như một trang lỗi "toàn cục" trong servlet container. Với các client là máy, nó tạo ra một response JSON với chi tiết về lỗi, HTTP status, và thông điệp exception. Với các client là trình duyệt, có một view lỗi "whitelabel" hiển thị cùng dữ liệu đó ở định dạng HTML (để tùy chỉnh nó, hãy thêm một `View` phân giải thành `error`).

Có một số property `server.error` có thể được đặt nếu bạn muốn tùy chỉnh hành vi xử lý lỗi mặc định. Xem phần ["Server Properties"](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#appendix.application-properties.server) trong Phụ lục.

Để thay thế hoàn toàn hành vi mặc định, bạn có thể hiện thực `ErrorController` và đăng ký một bean definition thuộc kiểu đó, hoặc thêm một bean kiểu `ErrorAttributes` để dùng cơ chế hiện có nhưng thay thế nội dung.

> **MẸO (TIP):** `BasicErrorController` có thể được dùng làm class cơ sở cho một `ErrorController` tùy chỉnh. Điều này đặc biệt hữu ích nếu bạn muốn thêm một handler cho một content type mới (mặc định là xử lý riêng `text/html` và cung cấp phương án dự phòng cho mọi thứ khác). Để làm vậy, hãy kế thừa `BasicErrorController`, thêm một phương thức public với `@RequestMapping` có thuộc tính `produces`, và tạo một bean thuộc kiểu mới của bạn.

Kể từ Spring Framework 6.0, [RFC 7807 Problem Details](https://docs.spring.io/spring-framework/reference/6.1/web/webmvc/mvc-ann-rest-exceptions.html) được hỗ trợ. Spring MVC có thể tạo ra các thông báo lỗi tùy chỉnh với media type `application/problem+json`, như:

```json
{
  "type": "https://example.org/problems/unknown-project",
  "title": "Unknown project",
  "status": 404,
  "detail": "No project found for id 'spring-unknown'",
  "instance": "/projects/spring-unknown"
}
```

Hỗ trợ này có thể được bật bằng cách đặt `spring.mvc.problemdetails.enabled` thành `true`.

Bạn cũng có thể định nghĩa một class được đánh annotation `@ControllerAdvice` để tùy chỉnh tài liệu JSON trả về cho một controller và/hoặc kiểu exception cụ thể, như trong ví dụ sau:

**Java**

```java
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@ControllerAdvice(basePackageClasses = SomeController.class)
public class MyControllerAdvice extends ResponseEntityExceptionHandler {

    @ResponseBody
    @ExceptionHandler(MyException.class)
    public ResponseEntity<?> handleControllerException(HttpServletRequest request, Throwable ex) {
        HttpStatus status = getStatus(request);
        return new ResponseEntity<>(new MyErrorBody(status.value(), ex.getMessage()), status);
    }

    private HttpStatus getStatus(HttpServletRequest request) {
        Integer code = (Integer) request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        HttpStatus status = HttpStatus.resolve(code);
        return (status != null) ? status : HttpStatus.INTERNAL_SERVER_ERROR;
    }

}
```

**Kotlin**

```kotlin
import jakarta.servlet.RequestDispatcher
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler

@ControllerAdvice(basePackageClasses = [SomeController::class])
class MyControllerAdvice : ResponseEntityExceptionHandler() {

    @ResponseBody
    @ExceptionHandler(MyException::class)
    fun handleControllerException(request: HttpServletRequest, ex: Throwable): ResponseEntity<*> {
        val status = getStatus(request)
        return ResponseEntity(MyErrorBody(status.value(), ex.message), status)
    }

    private fun getStatus(request: HttpServletRequest): HttpStatus {
        val code = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE) as Int
        val status = HttpStatus.resolve(code)
        return status ?: HttpStatus.INTERNAL_SERVER_ERROR
    }

}
```

Trong ví dụ trên, nếu `MyException` được ném ra bởi một controller được định nghĩa trong cùng package với `SomeController`, một biểu diễn JSON của POJO `MyErrorBody` sẽ được dùng thay cho biểu diễn `ErrorAttributes`.

Trong một số trường hợp, các lỗi được xử lý ở cấp controller không được ghi nhận bởi web observation hoặc [hạ tầng metrics](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#actuator.metrics.supported.spring-mvc). Ứng dụng có thể đảm bảo những exception như vậy được ghi nhận với observation bằng cách [đặt exception đã xử lý lên observation context](https://docs.spring.io/spring-framework/reference/6.1/integration/observability.html#observability.http-server.servlet).

### Trang lỗi tùy chỉnh (Custom Error Pages)

Nếu bạn muốn hiển thị một trang lỗi HTML tùy chỉnh cho một status code nhất định, bạn có thể thêm một file vào thư mục `/error`. Trang lỗi có thể là HTML tĩnh (tức là được thêm vào bất kỳ thư mục tài nguyên tĩnh nào) hoặc được xây dựng bằng template. Tên file nên là status code chính xác hoặc một mặt nạ chuỗi (series mask).

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

Để ánh xạ tất cả các lỗi `5xx` bằng một template FreeMarker, cấu trúc thư mục của bạn sẽ như sau:

```
src/
 +- main/
     +- java/
     |   + <source code>
     +- resources/
         +- templates/
             +- error/
             |   +- 5xx.ftlh
             +- <other templates>
```

Với các ánh xạ phức tạp hơn, bạn cũng có thể thêm các bean hiện thực interface `ErrorViewResolver`, như trong ví dụ sau:

**Java**

```java
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.boot.autoconfigure.web.servlet.error.ErrorViewResolver;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.ModelAndView;

public class MyErrorViewResolver implements ErrorViewResolver {

    @Override
    public ModelAndView resolveErrorView(HttpServletRequest request, HttpStatus status, Map<String, Object> model) {
        // Use the request or status to optionally return a ModelAndView
        if (status == HttpStatus.INSUFFICIENT_STORAGE) {
            // We could add custom model values here
            new ModelAndView("myview");
        }
        return null;
    }

}
```

**Kotlin**

```kotlin
import jakarta.servlet.http.HttpServletRequest
import org.springframework.boot.autoconfigure.web.servlet.error.ErrorViewResolver
import org.springframework.http.HttpStatus
import org.springframework.web.servlet.ModelAndView

class MyErrorViewResolver : ErrorViewResolver {

    override fun resolveErrorView(request: HttpServletRequest, status: HttpStatus,
            model: Map<String, Any>): ModelAndView? {
        // Use the request or status to optionally return a ModelAndView
        if (status == HttpStatus.INSUFFICIENT_STORAGE) {
            // We could add custom model values here
            return ModelAndView("myview")
        }
        return null
    }

}
```

Bạn cũng có thể dùng các tính năng Spring MVC thông thường như [các phương thức `@ExceptionHandler`](https://docs.spring.io/spring-framework/reference/6.1/web/webmvc/mvc-servlet/exceptionhandlers.html) và [`@ControllerAdvice`](https://docs.spring.io/spring-framework/reference/6.1/web/webmvc/mvc-controller/ann-advice.html). `ErrorController` sau đó sẽ nhận mọi exception chưa được xử lý.

### Ánh xạ trang lỗi bên ngoài Spring MVC (Mapping Error Pages Outside of Spring MVC)

Với các ứng dụng không dùng Spring MVC, bạn có thể dùng interface `ErrorPageRegistrar` để đăng ký trực tiếp các `ErrorPages`. Abstraction này làm việc trực tiếp với servlet container nhúng bên dưới và hoạt động ngay cả khi bạn không có `DispatcherServlet` của Spring MVC.

**Java**

```java
import org.springframework.boot.web.server.ErrorPage;
import org.springframework.boot.web.server.ErrorPageRegistrar;
import org.springframework.boot.web.server.ErrorPageRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;

@Configuration(proxyBeanMethods = false)
public class MyErrorPagesConfiguration {

    @Bean
    public ErrorPageRegistrar errorPageRegistrar() {
        return this::registerErrorPages;
    }

    private void registerErrorPages(ErrorPageRegistry registry) {
        registry.addErrorPages(new ErrorPage(HttpStatus.BAD_REQUEST, "/400"));
    }

}
```

**Kotlin**

```kotlin
import org.springframework.boot.web.server.ErrorPage
import org.springframework.boot.web.server.ErrorPageRegistrar
import org.springframework.boot.web.server.ErrorPageRegistry
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus

@Configuration(proxyBeanMethods = false)
class MyErrorPagesConfiguration {

    @Bean
    fun errorPageRegistrar(): ErrorPageRegistrar {
        return ErrorPageRegistrar { registry: ErrorPageRegistry -> registerErrorPages(registry) }
    }

    private fun registerErrorPages(registry: ErrorPageRegistry) {
        registry.addErrorPages(ErrorPage(HttpStatus.BAD_REQUEST, "/400"))
    }

}
```

> **LƯU Ý (NOTE):** Nếu bạn đăng ký một `ErrorPage` với một đường dẫn cuối cùng được xử lý bởi một `Filter` (như thường gặp với một số web framework không phải Spring, như Jersey và Wicket), thì `Filter` phải được đăng ký tường minh như một dispatcher `ERROR`, như trong ví dụ sau:

**Java**

```java
import java.util.EnumSet;

import jakarta.servlet.DispatcherType;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class MyFilterConfiguration {

    @Bean
    public FilterRegistrationBean<MyFilter> myFilter() {
        FilterRegistrationBean<MyFilter> registration = new FilterRegistrationBean<>(new MyFilter());
        // ...
        registration.setDispatcherTypes(EnumSet.allOf(DispatcherType.class));
        return registration;
    }

}
```

**Kotlin**

```kotlin
import jakarta.servlet.DispatcherType
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.EnumSet

@Configuration(proxyBeanMethods = false)
class MyFilterConfiguration {

    @Bean
    fun myFilter(): FilterRegistrationBean<MyFilter> {
        val registration = FilterRegistrationBean(MyFilter())
        // ...
        registration.setDispatcherTypes(EnumSet.allOf(DispatcherType::class.java))
        return registration
    }

}
```

> Lưu ý rằng `FilterRegistrationBean` mặc định không bao gồm kiểu dispatcher `ERROR`.

### Xử lý lỗi khi triển khai WAR (Error Handling in a WAR Deployment)

Khi được triển khai lên servlet container, Spring Boot dùng error page filter của nó để chuyển tiếp (forward) một request có status lỗi tới trang lỗi phù hợp. Điều này là cần thiết vì đặc tả servlet không cung cấp API để đăng ký trang lỗi. Tùy thuộc vào container mà bạn triển khai file war và các công nghệ ứng dụng sử dụng, có thể cần một số cấu hình bổ sung.

Error page filter chỉ có thể chuyển tiếp request tới đúng trang lỗi nếu response chưa được commit. Theo mặc định, WebSphere Application Server 8.0 trở lên commit response khi phương thức service của servlet hoàn tất thành công. Bạn nên tắt hành vi này bằng cách đặt `com.ibm.ws.webcontainer.invokeFlushAfterService` thành `false`.

## Hỗ trợ CORS (CORS Support)

[Cross-origin resource sharing](https://en.wikipedia.org/wiki/Cross-origin_resource_sharing) (CORS) là một [đặc tả W3C](https://www.w3.org/TR/cors/) được hiện thực bởi [hầu hết các trình duyệt](https://caniuse.com/#feat=cors), cho phép bạn chỉ định một cách linh hoạt loại request cross-domain nào được cho phép, thay vì dùng một số cách tiếp cận kém an toàn và kém mạnh mẽ hơn như IFRAME hoặc JSONP.

Kể từ phiên bản 4.2, Spring MVC [hỗ trợ CORS](https://docs.spring.io/spring-framework/reference/6.1/web/webmvc-cors.html). Việc dùng [cấu hình CORS trên phương thức controller](https://docs.spring.io/spring-framework/reference/6.1/web/webmvc-cors.html#mvc-cors-controller) với các annotation [`@CrossOrigin`](https://docs.spring.io/spring-framework/docs/6.1.6/javadoc-api/org/springframework/web/bind/annotation/CrossOrigin.html) trong ứng dụng Spring Boot không yêu cầu bất kỳ cấu hình cụ thể nào. [Cấu hình CORS toàn cục](https://docs.spring.io/spring-framework/reference/6.1/web/webmvc-cors.html#mvc-cors-global) có thể được định nghĩa bằng cách đăng ký một bean `WebMvcConfigurer` với phương thức `addCorsMappings(CorsRegistry)` được tùy chỉnh, như trong ví dụ sau:

**Java**

```java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
public class MyCorsConfiguration {

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {

            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**");
            }

        };
    }

}
```

**Kotlin**

```kotlin
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration(proxyBeanMethods = false)
class MyCorsConfiguration {

    @Bean
    fun corsConfigurer(): WebMvcConfigurer {
        return object : WebMvcConfigurer {
            override fun addCorsMappings(registry: CorsRegistry) {
                registry.addMapping("/api/**")
            }
        }
    }

}
```

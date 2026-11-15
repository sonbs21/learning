# 8.6.4. Transport (Transports)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#web.graphql.transports>

## HTTP và WebSocket (HTTP and WebSocket)

Endpoint HTTP của GraphQL mặc định nằm ở HTTP POST `/graphql`. Đường dẫn có thể được tùy chỉnh bằng `spring.graphql.path`.

> **MẸO (TIP):** Endpoint HTTP cho cả Spring MVC và Spring WebFlux được cung cấp bởi một bean `RouterFunction` với `@Order` là `0`. Nếu bạn định nghĩa các bean `RouterFunction` của riêng mình, bạn có thể muốn thêm các annotation `@Order` phù hợp để đảm bảo chúng được sắp xếp đúng.

Endpoint WebSocket của GraphQL mặc định bị tắt. Để bật nó:

- Với ứng dụng Servlet, hãy thêm starter WebSocket `spring-boot-starter-websocket`
- Với ứng dụng WebFlux, không cần dependency bổ sung nào
- Với cả hai, application property `spring.graphql.websocket.path` phải được đặt

Spring GraphQL cung cấp một mô hình [Web Interception](https://docs.spring.io/spring-graphql/reference/1.2/#web-interception). Điều này khá hữu ích để lấy thông tin từ header của HTTP request và đặt nó vào GraphQL context, hoặc lấy thông tin từ cùng context đó và ghi nó vào header của response. Với Spring Boot, bạn có thể khai báo một bean `WebInterceptor` để nó được đăng ký với web transport.

[Spring MVC](https://docs.spring.io/spring-framework/reference/6.1/web/webmvc-cors.html) và [Spring WebFlux](https://docs.spring.io/spring-framework/reference/6.1/web/webflux-cors.html) hỗ trợ các request CORS (Cross-Origin Resource Sharing). CORS là một phần quan trọng của cấu hình web cho các ứng dụng GraphQL được truy cập từ trình duyệt dùng các domain khác nhau.

Spring Boot hỗ trợ nhiều configuration property bên dưới namespace `spring.graphql.cors.*`; đây là một mẫu cấu hình ngắn:

**Properties**

```properties
spring.graphql.cors.allowed-origins=https://example.org
spring.graphql.cors.allowed-methods=GET,POST
spring.graphql.cors.max-age=1800s
```

**Yaml**

```yaml
spring:
  graphql:
    cors:
      allowed-origins: "https://example.org"
      allowed-methods: GET,POST
      max-age: 1800s
```

## RSocket

RSocket cũng được hỗ trợ như một transport, chạy trên WebSocket hoặc TCP. Sau khi [RSocket server được cấu hình](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#messaging.rsocket.server-auto-configuration), chúng ta có thể cấu hình GraphQL handler trên một route cụ thể bằng `spring.graphql.rsocket.mapping`. Ví dụ, cấu hình ánh xạ đó là `"graphql"` nghĩa là chúng ta có thể dùng nó làm route khi gửi request bằng `RSocketGraphQlClient`.

Spring Boot tự động cấu hình một bean `RSocketGraphQlClient.Builder<?>` mà bạn có thể inject vào các component của mình:

**Java**

```java
@Component
public class RSocketGraphQlClientExample {

    private final RSocketGraphQlClient graphQlClient;

    public RSocketGraphQlClientExample(RSocketGraphQlClient.Builder<?> builder) {
        this.graphQlClient = builder.tcp("example.spring.io", 8181).route("graphql").build();
    }
```

**Kotlin**

```kotlin
@Component
class RSocketGraphQlClientExample(private val builder: RSocketGraphQlClient.Builder<*>) {
```

Và sau đó gửi một request:

**Java**

```java
Mono<Book> book = this.graphQlClient.document("{ bookById(id: \"book-1\"){ id name pageCount author } }")
    .retrieve("bookById")
    .toEntity(Book.class);
```

**Kotlin**

```kotlin
val book = graphQlClient.document(
    """
    {
        bookById(id: "book-1"){
            id
            name
            pageCount
            author
        }
    }               
    """
)
    .retrieve("bookById").toEntity(Book::class.java)
```

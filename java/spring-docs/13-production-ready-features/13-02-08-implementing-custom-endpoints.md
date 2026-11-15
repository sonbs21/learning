# 13.2.8. Hiện thực endpoint tùy chỉnh (Implementing Custom Endpoints)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#actuator.endpoints.implementing-custom>

Nếu bạn thêm một `@Bean` được đánh annotation `@Endpoint`, mọi phương thức được đánh annotation `@ReadOperation`, `@WriteOperation`, hoặc `@DeleteOperation` đều được tự động expose qua JMX và, trong một ứng dụng web, cả qua HTTP. Các endpoint có thể được expose qua HTTP bằng Jersey, Spring MVC, hoặc Spring WebFlux. Nếu có cả Jersey và Spring MVC, Spring MVC sẽ được dùng.

Ví dụ sau expose một thao tác đọc trả về một object tùy chỉnh:

**Java**

```java
@ReadOperation
public CustomData getData() {
    return new CustomData("test", 5);
}
```

**Kotlin**

```kotlin
@ReadOperation
fun getData(): CustomData {
    return CustomData("test", 5)
}
```

Bạn cũng có thể viết các endpoint đặc thù cho từng công nghệ bằng `@JmxEndpoint` hoặc `@WebEndpoint`. Các endpoint này bị giới hạn ở công nghệ tương ứng của chúng. Ví dụ, `@WebEndpoint` chỉ được expose qua HTTP chứ không qua JMX.

Bạn có thể viết các extension đặc thù cho từng công nghệ bằng `@EndpointWebExtension` và `@EndpointJmxExtension`. Các annotation này cho phép bạn cung cấp các thao tác đặc thù cho từng công nghệ để bổ sung cho một endpoint hiện có.

Cuối cùng, nếu bạn cần truy cập chức năng đặc thù của web framework, bạn có thể hiện thực các endpoint servlet hoặc `@Controller` và `@RestController` của Spring, với cái giá là chúng không khả dụng qua JMX hoặc khi dùng một web framework khác.

## Nhận dữ liệu đầu vào (Receiving Input)

Các thao tác trên một endpoint nhận dữ liệu đầu vào thông qua các tham số của chúng. Khi được expose qua web, giá trị của các tham số này được lấy từ query parameter của URL và từ body JSON của request. Khi được expose qua JMX, các tham số được ánh xạ tới các tham số của các thao tác của MBean. Các tham số là bắt buộc theo mặc định. Chúng có thể được làm thành tùy chọn bằng cách đánh annotation `@javax.annotation.Nullable` hoặc `@org.springframework.lang.Nullable`.

Bạn có thể ánh xạ mỗi property gốc trong body JSON của request tới một tham số của endpoint. Xét body JSON của request sau:

```json
{
    "name": "test",
    "counter": 42
}
```

Bạn có thể dùng nó để gọi một thao tác ghi nhận các tham số `String name` và `int counter`, như ví dụ sau cho thấy:

**Java**

```java
@WriteOperation
public void updateData(String name, int counter) {
    // injects "test" and 42
}
```

**Kotlin**

```kotlin
@WriteOperation
fun updateData(name: String?, counter: Int) {
    // injects "test" and 42
}
```

> **MẸO (TIP):** Vì các endpoint không phụ thuộc công nghệ, chỉ các kiểu đơn giản có thể được chỉ định trong chữ ký phương thức. Cụ thể, việc khai báo một tham số duy nhất với kiểu `CustomData` định nghĩa các property `name` và `counter` là không được hỗ trợ.

> **LƯU Ý (NOTE):** Để dữ liệu đầu vào được ánh xạ tới các tham số của phương thức thao tác, code Java hiện thực một endpoint nên được biên dịch với `-parameters`, và code Kotlin hiện thực một endpoint nên được biên dịch với `-java-parameters`. Điều này sẽ tự động xảy ra nếu bạn dùng Gradle plugin của Spring Boot hoặc nếu bạn dùng Maven và `spring-boot-starter-parent`.

### Chuyển đổi kiểu dữ liệu đầu vào (Input Type Conversion)

Các tham số được truyền tới các phương thức thao tác của endpoint, nếu cần, sẽ được tự động chuyển đổi sang kiểu yêu cầu. Trước khi gọi một phương thức thao tác, dữ liệu đầu vào nhận được qua JMX hoặc HTTP được chuyển đổi sang các kiểu yêu cầu bằng một instance của `ApplicationConversionService` cũng như mọi bean `Converter` hoặc `GenericConverter` được đánh dấu bằng `@EndpointConverter`.

## Web endpoint tùy chỉnh (Custom Web Endpoints)

Các thao tác trên một `@Endpoint`, `@WebEndpoint`, hoặc `@EndpointWebExtension` được tự động expose qua HTTP bằng Jersey, Spring MVC, hoặc Spring WebFlux. Nếu có cả Jersey và Spring MVC, Spring MVC sẽ được dùng.

### Request predicate của web endpoint (Web Endpoint Request Predicates)

Một request predicate được tự động sinh ra cho mỗi thao tác trên một endpoint được expose qua web.

### Đường dẫn (Path)

Đường dẫn của predicate được xác định bởi ID của endpoint và đường dẫn cơ sở của các endpoint được expose qua web. Đường dẫn cơ sở mặc định là `/actuator`. Ví dụ, một endpoint có ID là `sessions` dùng `/actuator/sessions` làm đường dẫn trong predicate.

Bạn có thể tùy chỉnh thêm đường dẫn bằng cách đánh annotation `@Selector` lên một hoặc nhiều tham số của phương thức thao tác. Tham số như vậy được thêm vào path predicate dưới dạng một path variable. Giá trị của biến được truyền vào phương thức thao tác khi thao tác của endpoint được gọi. Nếu bạn muốn bắt tất cả các phần tử đường dẫn còn lại, bạn có thể thêm `@Selector(Match=ALL_REMAINING)` vào tham số cuối cùng và làm cho nó thuộc một kiểu tương thích chuyển đổi với `String[]`.

### HTTP method

HTTP method của predicate được xác định bởi loại thao tác, như trong bảng sau:

| Thao tác | HTTP method |
|---|---|
| `@ReadOperation` | `GET` |
| `@WriteOperation` | `POST` |
| `@DeleteOperation` | `DELETE` |

### Consumes

Với một `@WriteOperation` (HTTP `POST`) có dùng request body, mệnh đề `consumes` của predicate là `application/vnd.spring-boot.actuator.v2+json, application/json`. Với tất cả các thao tác khác, mệnh đề `consumes` là rỗng.

### Produces

Mệnh đề `produces` của predicate có thể được xác định bởi thuộc tính `produces` của các annotation `@DeleteOperation`, `@ReadOperation`, và `@WriteOperation`. Thuộc tính này là tùy chọn. Nếu nó không được dùng, mệnh đề `produces` được xác định tự động.

Nếu phương thức thao tác trả về `void` hoặc `Void`, mệnh đề `produces` là rỗng. Nếu phương thức thao tác trả về một `org.springframework.core.io.Resource`, mệnh đề `produces` là `application/octet-stream`. Với tất cả các thao tác khác, mệnh đề `produces` là `application/vnd.spring-boot.actuator.v2+json, application/json`.

### Response status của web endpoint (Web Endpoint Response Status)

Response status mặc định cho một thao tác của endpoint phụ thuộc vào loại thao tác (đọc, ghi, hoặc xóa) và thao tác trả về gì (nếu có).

Nếu một `@ReadOperation` trả về một giá trị, response status sẽ là 200 (OK). Nếu nó không trả về giá trị, response status sẽ là 404 (Not Found).

Nếu một `@WriteOperation` hoặc `@DeleteOperation` trả về một giá trị, response status sẽ là 200 (OK). Nếu nó không trả về giá trị, response status sẽ là 204 (No Content).

Nếu một thao tác được gọi mà thiếu một tham số bắt buộc hoặc với một tham số không thể chuyển đổi sang kiểu yêu cầu, phương thức thao tác sẽ không được gọi, và response status sẽ là 400 (Bad Request).

### Range request của web endpoint (Web Endpoint Range Requests)

Bạn có thể dùng một HTTP range request để yêu cầu một phần của một HTTP resource. Khi dùng Spring MVC hoặc Spring Web Flux, các thao tác trả về một `org.springframework.core.io.Resource` tự động hỗ trợ range request.

> **LƯU Ý (NOTE):** Range request không được hỗ trợ khi dùng Jersey.

### Bảo mật web endpoint (Web Endpoint Security)

Một thao tác trên một web endpoint hoặc một extension endpoint đặc thù cho web có thể nhận `java.security.Principal` hoặc `org.springframework.boot.actuate.endpoint.SecurityContext` hiện tại làm tham số phương thức. Cái đầu thường được dùng kết hợp với `@Nullable` để cung cấp hành vi khác nhau cho người dùng đã xác thực và chưa xác thực. Cái sau thường được dùng để thực hiện kiểm tra phân quyền bằng phương thức `isUserInRole(String)` của nó.

## Servlet endpoint (Servlet Endpoints)

Một servlet có thể được expose như một endpoint bằng cách hiện thực một class được đánh annotation `@ServletEndpoint` đồng thời hiện thực `Supplier<EndpointServlet>`. Servlet endpoint cung cấp tích hợp sâu hơn với servlet container nhưng với cái giá là tính di động (portability). Chúng nhằm để expose một servlet hiện có như một endpoint. Với các endpoint mới, các annotation `@Endpoint` và `@WebEndpoint` nên được ưu tiên bất cứ khi nào có thể.

## Controller endpoint (Controller Endpoints)

Bạn có thể dùng `@ControllerEndpoint` và `@RestControllerEndpoint` để hiện thực một endpoint chỉ được expose bởi Spring MVC hoặc Spring WebFlux. Các phương thức được ánh xạ bằng các annotation tiêu chuẩn của Spring MVC và Spring WebFlux, như `@RequestMapping` và `@GetMapping`, với ID của endpoint được dùng làm tiền tố cho đường dẫn. Controller endpoint cung cấp tích hợp sâu hơn với các web framework của Spring nhưng với cái giá là tính di động. Các annotation `@Endpoint` và `@WebEndpoint` nên được ưu tiên bất cứ khi nào có thể.

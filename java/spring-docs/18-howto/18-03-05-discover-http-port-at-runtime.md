# 18.3.5. Tìm HTTP port lúc runtime (Discover the HTTP Port at Runtime)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.webserver.discover-port>

Bạn có thể truy cập port mà server đang chạy từ log output hoặc từ `WebServerApplicationContext` thông qua `WebServer` của nó. Cách tốt nhất để lấy port và chắc chắn rằng nó đã được khởi tạo là thêm một `@Bean` kiểu `ApplicationListener<WebServerInitializedEvent>` và lấy container ra khỏi event khi event được publish.

Các test dùng `@SpringBootTest(webEnvironment=WebEnvironment.RANDOM_PORT)` cũng có thể inject port thực tế vào một field bằng cách dùng annotation `@LocalServerPort`, như trong ví dụ sau:

**Java**

```java
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class MyWebIntegrationTests {

    @LocalServerPort
    int port;

    // ...

}
```

**Kotlin**

```kotlin
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.boot.test.web.server.LocalServerPort

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class MyWebIntegrationTests {

    @LocalServerPort
    var port = 0

    // ...

}
```

> **LƯU Ý (NOTE):** `@LocalServerPort` là một meta-annotation cho `@Value("${local.server.port}")`. Đừng cố inject port trong một ứng dụng thông thường. Như chúng ta vừa thấy, giá trị chỉ được đặt sau khi container đã được khởi tạo. Ngược lại với test, các callback trong code ứng dụng được xử lý sớm (trước khi giá trị thực sự có sẵn).

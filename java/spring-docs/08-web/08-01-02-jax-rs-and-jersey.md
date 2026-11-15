# 8.1.2. JAX-RS và Jersey (JAX-RS and Jersey)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#web.servlet.jersey>

Nếu bạn thích mô hình lập trình JAX-RS cho các REST endpoint, bạn có thể dùng một trong các implementation có sẵn thay cho Spring MVC. [Jersey](https://jersey.github.io/) và [Apache CXF](https://cxf.apache.org/) hoạt động khá tốt ngay từ đầu. CXF yêu cầu bạn đăng ký `Servlet` hoặc `Filter` của nó dưới dạng `@Bean` trong application context. Jersey có một số hỗ trợ Spring gốc, nên chúng tôi cũng cung cấp hỗ trợ auto-configuration cho nó trong Spring Boot, cùng với một starter.

Để bắt đầu với Jersey, hãy thêm `spring-boot-starter-jersey` làm dependency, sau đó bạn cần một `@Bean` kiểu `ResourceConfig` trong đó bạn đăng ký tất cả các endpoint, như trong ví dụ sau:

```java
import org.glassfish.jersey.server.ResourceConfig;

import org.springframework.stereotype.Component;

@Component
public class MyJerseyConfig extends ResourceConfig {

    public MyJerseyConfig() {
        register(MyEndpoint.class);
    }

}
```

> **CẢNH BÁO (WARNING):** Hỗ trợ của Jersey cho việc quét các file nén thực thi (executable archive) khá hạn chế. Ví dụ, nó không thể quét endpoint trong một package nằm trong một [file jar thực thi đầy đủ (fully executable jar)](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#deployment.installing) hoặc trong `WEB-INF/classes` khi chạy một file war thực thi. Để tránh hạn chế này, không nên dùng phương thức `packages`, và các endpoint nên được đăng ký riêng lẻ bằng phương thức `register`, như trong ví dụ trên.

Để tùy chỉnh nâng cao hơn, bạn cũng có thể đăng ký một số lượng tùy ý các bean hiện thực `ResourceConfigCustomizer`.

Tất cả các endpoint được đăng ký nên là `@Components` với các annotation HTTP resource (`@GET` và các annotation khác), như trong ví dụ sau:

```java
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import org.springframework.stereotype.Component;

@Component
@Path("/hello")
public class MyEndpoint {

    @GET
    public String message() {
        return "Hello";
    }

}
```

Vì `Endpoint` là một `@Component` của Spring, vòng đời của nó được Spring quản lý và bạn có thể dùng annotation `@Autowired` để inject dependency và dùng annotation `@Value` để inject cấu hình bên ngoài. Theo mặc định, servlet Jersey được đăng ký và ánh xạ tới `/*`. Bạn có thể thay đổi ánh xạ bằng cách thêm `@ApplicationPath` vào `ResourceConfig`.

Theo mặc định, Jersey được thiết lập như một servlet trong một `@Bean` kiểu `ServletRegistrationBean` tên là `jerseyServletRegistration`. Theo mặc định, servlet được khởi tạo lười (lazily), nhưng bạn có thể tùy chỉnh hành vi đó bằng cách đặt `spring.jersey.servlet.load-on-startup`. Bạn có thể vô hiệu hóa hoặc ghi đè bean đó bằng cách tạo bean của riêng mình với cùng tên. Bạn cũng có thể dùng filter thay cho servlet bằng cách đặt `spring.jersey.type=filter` (trong trường hợp này, `@Bean` cần thay thế hoặc ghi đè là `jerseyFilterRegistration`). Filter có một `@Order`, bạn có thể đặt nó bằng `spring.jersey.filter.order`. Khi dùng Jersey như một filter, phải có một servlet xử lý mọi request không bị Jersey chặn lại. Nếu ứng dụng của bạn không chứa servlet như vậy, bạn có thể muốn bật servlet mặc định bằng cách đặt `server.servlet.register-default-servlet` thành `true`. Cả việc đăng ký servlet và filter đều có thể được cung cấp init parameter bằng cách dùng `spring.jersey.init.*` để chỉ định một map các property.

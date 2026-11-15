# 18.4.6. Tắt DispatcherServlet của Spring MVC (Switch Off the Spring MVC DispatcherServlet)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.spring-mvc.switch-off-dispatcherservlet>

Theo mặc định, mọi nội dung đều được phục vụ từ gốc của ứng dụng (`/`). Nếu bạn muốn ánh xạ tới một đường dẫn khác, bạn có thể cấu hình như sau:

**Properties**

```properties
spring.mvc.servlet.path=/mypath
```

**Yaml**

```yaml
spring:
  mvc:
    servlet:
      path: "/mypath"
```

Nếu bạn có thêm các servlet khác, bạn có thể khai báo một `@Bean` kiểu `Servlet` hoặc `ServletRegistrationBean` cho mỗi servlet và Spring Boot sẽ đăng ký chúng một cách trong suốt với container. Vì các servlet được đăng ký theo cách đó, chúng có thể được ánh xạ tới một sub-context của `DispatcherServlet` mà không gọi tới nó.

Việc tự cấu hình `DispatcherServlet` là điều không thường gặp nhưng nếu bạn thực sự cần làm vậy, một `@Bean` kiểu `DispatcherServletPath` cũng phải được cung cấp để cung cấp đường dẫn của `DispatcherServlet` tùy chỉnh của bạn.

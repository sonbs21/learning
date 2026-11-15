# 18.3.3. Thay đổi HTTP port (Change the HTTP Port)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.webserver.change-port>

Trong một ứng dụng độc lập, HTTP port chính mặc định là `8080` nhưng có thể được đặt bằng `server.port` (ví dụ, trong `application.properties` hoặc dưới dạng một System property). Nhờ relaxed binding của các giá trị `Environment`, bạn cũng có thể dùng `SERVER_PORT` (ví dụ, dưới dạng một biến môi trường của hệ điều hành).

Để tắt hoàn toàn các HTTP endpoint nhưng vẫn tạo một `WebApplicationContext`, hãy dùng `server.port=-1` (làm vậy đôi khi hữu ích cho việc kiểm thử).

Để biết thêm chi tiết, xem "[Tùy chỉnh embedded servlet container (Customizing Embedded Servlet Containers)](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#web.servlet.embedded-container.customizing)" trong phần 'Các tính năng của Spring Boot', hoặc mã nguồn [`ServerProperties`](https://github.com/spring-projects/spring-boot/tree/v3.2.5/spring-boot-project/spring-boot-autoconfigure/src/main/java/org/springframework/boot/autoconfigure/web/ServerProperties.java).

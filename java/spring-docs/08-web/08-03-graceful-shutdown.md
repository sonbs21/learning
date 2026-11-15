# 8.3. Tắt ứng dụng nhẹ nhàng (Graceful Shutdown)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#web.graceful-shutdown>

Graceful shutdown được hỗ trợ với cả bốn web server nhúng (Jetty, Reactor Netty, Tomcat, và Undertow) và với cả ứng dụng web reactive lẫn dựa trên servlet. Nó diễn ra như một phần của việc đóng application context và được thực hiện ở giai đoạn sớm nhất của việc dừng các bean `SmartLifecycle`. Quá trình dừng này dùng một timeout, cung cấp một khoảng thời gian ân hạn (grace period) trong đó các request hiện có được phép hoàn tất nhưng không có request mới nào được chấp nhận. Cách chính xác để không chấp nhận request mới khác nhau tùy thuộc vào web server đang được dùng. Jetty, Reactor Netty, và Tomcat sẽ ngừng chấp nhận request ở tầng mạng. Undertow sẽ chấp nhận request nhưng phản hồi ngay lập tức với response service unavailable (503).

> **LƯU Ý (NOTE):** Graceful shutdown với Tomcat yêu cầu Tomcat 9.0.33 trở lên.

Để bật graceful shutdown, hãy cấu hình property `server.shutdown`, như trong ví dụ sau:

**Properties**

```properties
server.shutdown=graceful
```

**Yaml**

```yaml
server:
  shutdown: "graceful"
```

Để cấu hình khoảng thời gian timeout, hãy cấu hình property `spring.lifecycle.timeout-per-shutdown-phase`, như trong ví dụ sau:

**Properties**

```properties
spring.lifecycle.timeout-per-shutdown-phase=20s
```

**Yaml**

```yaml
spring:
  lifecycle:
    timeout-per-shutdown-phase: "20s"
```

> **QUAN TRỌNG (IMPORTANT):** Việc dùng graceful shutdown với IDE có thể không hoạt động đúng nếu IDE không gửi tín hiệu `SIGTERM` đúng cách. Xem tài liệu của IDE để biết thêm chi tiết.

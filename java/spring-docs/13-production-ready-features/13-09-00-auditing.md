# 13.9. Auditing

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#actuator.auditing>

Khi Spring Security được dùng, Spring Boot Actuator có một framework audit linh hoạt, publish các event (theo mặc định là các exception "authentication success", "failure" và "access denied"). Tính năng này có thể rất hữu ích cho việc báo cáo và cho việc hiện thực một chính sách khóa tài khoản (lock-out) dựa trên các lần xác thực thất bại.

Bạn có thể bật auditing bằng cách cung cấp một bean kiểu `AuditEventRepository` trong cấu hình của ứng dụng. Để tiện lợi, Spring Boot cung cấp một `InMemoryAuditEventRepository`. `InMemoryAuditEventRepository` có khả năng hạn chế, và chúng tôi khuyên chỉ dùng nó cho môi trường phát triển. Với môi trường production, hãy cân nhắc tạo implementation `AuditEventRepository` thay thế của riêng bạn.

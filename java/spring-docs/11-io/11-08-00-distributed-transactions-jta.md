# 11.8. Giao dịch phân tán với JTA (Distributed Transactions With JTA)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#io.jta>

Spring Boot hỗ trợ các giao dịch JTA phân tán trên nhiều tài nguyên XA bằng cách dùng một transaction manager được lấy từ JNDI.

Khi phát hiện môi trường JTA, `JtaTransactionManager` của Spring được dùng để quản lý transaction. Các bean JMS, DataSource, và JPA được tự động cấu hình sẽ được nâng cấp để hỗ trợ transaction XA. Bạn có thể dùng các cách viết tiêu chuẩn của Spring, chẳng hạn như `@Transactional`, để tham gia vào một giao dịch phân tán. Nếu bạn đang ở trong môi trường JTA mà vẫn muốn dùng transaction cục bộ, bạn có thể đặt property `spring.jta.enabled` thành `false` để tắt auto-configuration JTA.

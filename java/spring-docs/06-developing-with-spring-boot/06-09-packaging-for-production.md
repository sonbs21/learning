# 6.9. Đóng gói ứng dụng cho production (Packaging Your Application for Production)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#using.packaging-for-production>

Các jar thực thi có thể được dùng để triển khai lên production. Vì chúng khép kín (self-contained), chúng cũng rất phù hợp cho việc triển khai trên cloud.

Để có thêm các tính năng "sẵn sàng cho production" ("production ready"), chẳng hạn như health, auditing và các endpoint REST hoặc JMX cho metric, hãy cân nhắc thêm `spring-boot-actuator`. Xem *[Các tính năng sẵn sàng cho production (Production-ready Features)](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#actuator)* để biết chi tiết.

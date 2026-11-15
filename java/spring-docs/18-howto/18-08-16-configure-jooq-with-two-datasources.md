# 18.8.16. Cấu hình jOOQ với hai DataSource (Configure jOOQ with Two DataSources)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.data-access.configure-jooq-with-multiple-datasources>

Nếu bạn cần dùng jOOQ với nhiều data source, bạn nên tạo `DSLContext` của riêng mình cho mỗi data source. Xem [JooqAutoConfiguration](https://github.com/spring-projects/spring-boot/tree/v3.2.5/spring-boot-project/spring-boot-autoconfigure/src/main/java/org/springframework/boot/autoconfigure/jooq/JooqAutoConfiguration.java) để biết thêm chi tiết.

> **MẸO (TIP):** Đặc biệt, `JooqExceptionTranslator` và `SpringTransactionProvider` có thể được tái sử dụng để cung cấp các tính năng tương tự như những gì auto-configuration làm với một `DataSource` duy nhất.

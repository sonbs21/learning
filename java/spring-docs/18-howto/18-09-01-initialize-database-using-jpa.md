# 18.9.1. Khởi tạo cơ sở dữ liệu bằng JPA (Initialize a Database Using JPA)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.data-initialization.using-jpa>

JPA có các tính năng sinh DDL, và chúng có thể được thiết lập để chạy lúc khởi động với cơ sở dữ liệu. Điều này được kiểm soát thông qua hai property bên ngoài:

- `spring.jpa.generate-ddl` (boolean) bật và tắt tính năng và độc lập với nhà cung cấp.
- `spring.jpa.hibernate.ddl-auto` (enum) là một tính năng của Hibernate kiểm soát hành vi theo cách chi tiết hơn. Tính năng này được mô tả chi tiết hơn ở phần sau của hướng dẫn này.

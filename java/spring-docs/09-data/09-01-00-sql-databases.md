# 9.1. Cơ sở dữ liệu SQL (SQL Databases)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#data.sql>

[Spring Framework](https://spring.io/projects/spring-framework) cung cấp hỗ trợ rộng rãi cho việc làm việc với cơ sở dữ liệu SQL, từ truy cập JDBC trực tiếp bằng `JdbcClient` hoặc `JdbcTemplate` đến các công nghệ "ánh xạ đối tượng - quan hệ" (object relational mapping) hoàn chỉnh như Hibernate. [Spring Data](https://spring.io/projects/spring-data) cung cấp thêm một cấp chức năng: tạo các implementation `Repository` trực tiếp từ interface và dùng quy ước để sinh query từ tên phương thức.

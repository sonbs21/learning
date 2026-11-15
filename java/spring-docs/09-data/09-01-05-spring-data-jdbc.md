# 9.1.5. Spring Data JDBC

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#data.sql.jdbc>

Spring Data bao gồm hỗ trợ repository cho JDBC và sẽ tự động sinh SQL cho các phương thức trên `CrudRepository`. Với các query nâng cao hơn, annotation `@Query` được cung cấp.

Spring Boot sẽ tự động cấu hình các repository JDBC của Spring Data khi các dependency cần thiết nằm trên classpath. Chúng có thể được thêm vào project chỉ với một dependency duy nhất tới `spring-boot-starter-data-jdbc`. Nếu cần, bạn có thể kiểm soát cấu hình của Spring Data JDBC bằng cách thêm annotation `@EnableJdbcRepositories` hoặc một subclass của `AbstractJdbcConfiguration` vào ứng dụng.

> **MẸO (TIP):** Để biết đầy đủ chi tiết về Spring Data JDBC, xem [tài liệu tham khảo](https://docs.spring.io/spring-data/jdbc/docs/3.2.5/reference/html/).

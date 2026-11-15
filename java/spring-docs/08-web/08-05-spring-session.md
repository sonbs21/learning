# 8.5. Spring Session

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#web.spring-session>

Spring Boot cung cấp auto-configuration [Spring Session](https://spring.io/projects/spring-session) cho nhiều loại kho dữ liệu. Khi xây dựng một ứng dụng web servlet, các kho sau có thể được tự động cấu hình:

- Redis
- JDBC
- Hazelcast
- MongoDB

Ngoài ra, [Spring Boot for Apache Geode](https://github.com/spring-projects/spring-boot-data-geode) cung cấp [auto-configuration để dùng Apache Geode làm kho lưu session](https://docs.spring.io/spring-boot-data-geode-build/2.0.x/reference/html5/#geode-session).

Auto-configuration cho servlet thay thế nhu cầu dùng `@Enable*HttpSession`.

Nếu chỉ có một module Spring Session duy nhất trên classpath, Spring Boot tự động dùng implementation kho lưu đó. Nếu bạn có nhiều hơn một implementation, Spring Boot dùng thứ tự sau để chọn một implementation cụ thể:

1. Redis
2. JDBC
3. Hazelcast
4. MongoDB
5. Nếu không có Redis, JDBC, Hazelcast hay MongoDB, chúng tôi không cấu hình `SessionRepository`.

Khi xây dựng một ứng dụng web reactive, các kho sau có thể được tự động cấu hình:

- Redis
- MongoDB

Auto-configuration cho reactive thay thế nhu cầu dùng `@Enable*WebSession`.

Tương tự như cấu hình servlet, nếu bạn có nhiều hơn một implementation, Spring Boot dùng thứ tự sau để chọn một implementation cụ thể:

1. Redis
2. MongoDB
3. Nếu không có cả Redis lẫn MongoDB, chúng tôi không cấu hình `ReactiveSessionRepository`.

Mỗi kho có các thiết lập bổ sung riêng. Ví dụ, có thể tùy chỉnh tên bảng cho kho JDBC, như trong ví dụ sau:

**Properties**

```properties
spring.session.jdbc.table-name=SESSIONS
```

**Yaml**

```yaml
spring:
  session:
    jdbc:
      table-name: "SESSIONS"
```

Để đặt timeout của session, bạn có thể dùng property `spring.session.timeout`. Nếu property đó không được đặt với một ứng dụng web servlet, auto-configuration sẽ dùng giá trị của `server.servlet.session.timeout`.

Bạn có thể kiểm soát cấu hình của Spring Session bằng `@Enable*HttpSession` (servlet) hoặc `@Enable*WebSession` (reactive). Điều này sẽ khiến auto-configuration lùi lại. Khi đó, Spring Session có thể được cấu hình bằng các thuộc tính của annotation thay vì các configuration property đã mô tả ở trên.

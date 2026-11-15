# 18.8.5. Cấu hình các property JPA (Configure JPA Properties)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.data-access.jpa-properties>

Spring Data JPA đã cung cấp sẵn một số tùy chọn cấu hình độc lập với nhà cung cấp (chẳng hạn như các tùy chọn cho SQL logging), và Spring Boot expose các tùy chọn đó cùng một vài tùy chọn khác cho Hibernate dưới dạng các property cấu hình bên ngoài. Một số trong số chúng được tự động phát hiện theo ngữ cảnh nên bạn không cần phải đặt chúng.

`spring.jpa.hibernate.ddl-auto` là một trường hợp đặc biệt, vì tùy thuộc vào điều kiện runtime, nó có các giá trị mặc định khác nhau. Nếu một embedded database được dùng và không có schema manager nào (chẳng hạn như Liquibase hoặc Flyway) đang xử lý `DataSource`, nó mặc định là `create-drop`. Trong tất cả các trường hợp khác, nó mặc định là `none`.

Dialect cần dùng được phát hiện bởi JPA provider. Nếu bạn muốn tự đặt dialect, hãy đặt property `spring.jpa.database-platform`.

Các tùy chọn thường được đặt nhất được trình bày trong ví dụ sau:

**Properties**

```properties
spring.jpa.hibernate.naming.physical-strategy=com.example.MyPhysicalNamingStrategy
spring.jpa.show-sql=true
```

**Yaml**

```yaml
spring:
  jpa:
    hibernate:
      naming:
        physical-strategy: "com.example.MyPhysicalNamingStrategy"
    show-sql: true
```

Ngoài ra, tất cả các property trong `spring.jpa.properties.*` được truyền qua như các property JPA thông thường (với tiền tố được bỏ đi) khi `EntityManagerFactory` cục bộ được tạo.

> **CẢNH BÁO (WARNING):** Bạn cần đảm bảo rằng các tên được định nghĩa trong `spring.jpa.properties.*` khớp chính xác với những tên mà JPA provider của bạn mong đợi. Spring Boot sẽ không thử bất kỳ kiểu relaxed binding nào cho các mục này.
>
> Ví dụ, nếu bạn muốn cấu hình batch size của Hibernate, bạn phải dùng `spring.jpa.properties.hibernate.jdbc.batch_size`. Nếu bạn dùng các dạng khác, chẳng hạn như `batchSize` hoặc `batch-size`, Hibernate sẽ không áp dụng thiết lập đó.

> **MẸO (TIP):** Nếu bạn cần áp dụng tùy chỉnh nâng cao cho các property Hibernate, hãy cân nhắc đăng ký một bean `HibernatePropertiesCustomizer` sẽ được gọi trước khi tạo `EntityManagerFactory`. Điều này được ưu tiên hơn bất cứ thứ gì được áp dụng bởi auto-configuration.

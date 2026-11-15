# 18.9.4. Khởi tạo cơ sở dữ liệu Spring Batch (Initialize a Spring Batch Database)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.data-initialization.batch>

Nếu bạn dùng Spring Batch, nó được đóng gói sẵn với các script khởi tạo SQL cho hầu hết các nền tảng cơ sở dữ liệu phổ biến. Spring Boot có thể phát hiện kiểu cơ sở dữ liệu của bạn và thực thi các script đó lúc khởi động. Nếu bạn dùng một embedded database, điều này xảy ra theo mặc định. Bạn cũng có thể bật nó cho bất kỳ kiểu cơ sở dữ liệu nào, như trong ví dụ sau:

**Properties**

```properties
spring.batch.jdbc.initialize-schema=always
```

**Yaml**

```yaml
spring:
  batch:
    jdbc:
      initialize-schema: "always"
```

Bạn cũng có thể tắt việc khởi tạo một cách tường minh bằng cách đặt `spring.batch.jdbc.initialize-schema` thành `never`.

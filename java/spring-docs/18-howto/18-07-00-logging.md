# 18.7. Logging

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.logging>

Spring Boot không có dependency logging bắt buộc nào, ngoại trừ Commons Logging API, thường được cung cấp bởi module `spring-jcl` của Spring Framework. Để dùng [Logback](https://logback.qos.ch), bạn cần đưa nó và `spring-jcl` vào classpath. Cách được khuyến nghị để làm điều đó là thông qua các starter, tất cả đều phụ thuộc vào `spring-boot-starter-logging`. Với một ứng dụng web, bạn chỉ cần `spring-boot-starter-web`, vì nó phụ thuộc bắc cầu vào logging starter. Nếu bạn dùng Maven, dependency sau sẽ thêm logging cho bạn:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
```

Spring Boot có một abstraction `LoggingSystem` cố gắng cấu hình logging dựa trên nội dung của classpath. Nếu Logback có sẵn, nó là lựa chọn đầu tiên.

Nếu thay đổi duy nhất bạn cần làm với logging là đặt mức (level) của các logger khác nhau, bạn có thể làm điều đó trong `application.properties` bằng cách dùng tiền tố "logging.level", như trong ví dụ sau:

**Properties**

```properties
logging.level.org.springframework.web=debug
logging.level.org.hibernate=error
```

**Yaml**

```yaml
logging:
  level:
    org.springframework.web: "debug"
    org.hibernate: "error"
```

Bạn cũng có thể đặt vị trí của một file mà log sẽ được ghi vào (ngoài console) bằng cách dùng `logging.file.name`.

Để cấu hình các thiết lập chi tiết hơn của một hệ thống logging, bạn cần dùng định dạng cấu hình native được `LoggingSystem` tương ứng hỗ trợ. Theo mặc định, Spring Boot lấy cấu hình native từ vị trí mặc định của hệ thống đó (chẳng hạn như `classpath:logback.xml` cho Logback), nhưng bạn có thể đặt vị trí của file cấu hình bằng cách dùng property `logging.config`.

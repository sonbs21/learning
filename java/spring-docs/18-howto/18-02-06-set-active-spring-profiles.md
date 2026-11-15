# 18.2.6. Đặt các Spring profile đang hoạt động (Set the Active Spring Profiles)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.properties-and-configuration.set-active-spring-profiles>

`Environment` của Spring có một API cho việc này, nhưng thông thường bạn sẽ đặt một System property (`spring.profiles.active`) hoặc một biến môi trường của hệ điều hành (`SPRING_PROFILES_ACTIVE`). Ngoài ra, bạn có thể khởi chạy ứng dụng với tham số `-D` (nhớ đặt nó trước main class hoặc file nén jar), như sau:

```shell
$ java -jar -Dspring.profiles.active=production demo-0.0.1-SNAPSHOT.jar
```

Trong Spring Boot, bạn cũng có thể đặt profile đang hoạt động trong `application.properties`, như trong ví dụ sau:

**Properties**

```properties
spring.profiles.active=production
```

**Yaml**

```yaml
spring:
  profiles:
    active: "production"
```

Một giá trị được đặt theo cách này sẽ bị thay thế bởi thiết lập System property hoặc biến môi trường nhưng không bị thay thế bởi phương thức `SpringApplicationBuilder.profiles()`. Vì vậy, Java API sau có thể được dùng để bổ sung thêm profile mà không làm thay đổi các giá trị mặc định.

Xem "[Profile](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.profiles)" trong phần "Các tính năng của Spring Boot" để biết thêm thông tin.

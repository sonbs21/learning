# 18.2.7. Đặt tên profile mặc định (Set the Default Profile Name)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.properties-and-configuration.set-default-spring-profile-name>

Profile mặc định là profile được bật nếu không có profile nào đang hoạt động. Theo mặc định, tên của profile mặc định là `default`, nhưng nó có thể được thay đổi bằng một System property (`spring.profiles.default`) hoặc một biến môi trường của hệ điều hành (`SPRING_PROFILES_DEFAULT`).

Trong Spring Boot, bạn cũng có thể đặt tên profile mặc định trong `application.properties`, như trong ví dụ sau:

**Properties**

```properties
spring.profiles.default=dev
```

**Yaml**

```yaml
spring:
  profiles:
    default: "dev"
```

Xem "[Profile](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.profiles)" trong phần "Các tính năng của Spring Boot" để biết thêm thông tin.

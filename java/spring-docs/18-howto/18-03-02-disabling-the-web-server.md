# 18.3.2. Vô hiệu hóa web server (Disabling the Web Server)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.webserver.disable>

Nếu classpath của bạn chứa các thành phần cần thiết để khởi động một web server, Spring Boot sẽ tự động khởi động nó. Để vô hiệu hóa hành vi này, hãy cấu hình `WebApplicationType` trong `application.properties`, như trong ví dụ sau:

**Properties**

```properties
spring.main.web-application-type=none
```

**Yaml**

```yaml
spring:
  main:
    web-application-type: "none"
```

# 11.7. Web Services

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#io.webservices>

Spring Boot cung cấp auto-configuration cho Web Services, vì vậy tất cả những gì bạn phải làm là định nghĩa các `Endpoints` của mình.

[Các tính năng Spring Web Services](https://docs.spring.io/spring-ws/docs/4.0.10/reference/html/) có thể được truy cập dễ dàng bằng module `spring-boot-starter-webservices`.

Các bean `SimpleWsdl11Definition` và `SimpleXsdSchema` có thể được tự động tạo lần lượt cho các WSDL và XSD của bạn. Để làm vậy, hãy cấu hình vị trí của chúng, như trong ví dụ sau:

**Properties**

```properties
spring.webservices.wsdl-locations=classpath:/wsdl
```

**Yaml**

```yaml
spring:
  webservices:
    wsdl-locations: "classpath:/wsdl"
```

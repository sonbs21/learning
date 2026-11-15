# 13.4.1. Tùy chỉnh tên MBean (Customizing MBean Names)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#actuator.jmx.custom-mbean-names>

Tên của MBean thường được sinh ra từ `id` của endpoint. Ví dụ, endpoint `health` được expose là `org.springframework.boot:type=Endpoint,name=Health`.

Nếu ứng dụng của bạn chứa nhiều hơn một `ApplicationContext` Spring, bạn có thể thấy các tên bị xung đột. Để giải quyết vấn đề này, bạn có thể đặt property `spring.jmx.unique-names` thành `true` để tên MBean luôn là duy nhất.

Bạn cũng có thể tùy chỉnh domain JMX mà các endpoint được expose bên dưới. Các thiết lập sau cho thấy một ví dụ về việc làm điều đó trong `application.properties`:

**Properties**

```properties
spring.jmx.unique-names=true
management.endpoints.jmx.domain=com.example.myapp
```

**Yaml**

```yaml
spring:
  jmx:
    unique-names: true
management:
  endpoints:
    jmx:
      domain: "com.example.myapp"
```

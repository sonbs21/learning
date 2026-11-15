# 13.2.7. Hỗ trợ CORS (CORS Support)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#actuator.endpoints.cors>

[Cross-origin resource sharing](https://en.wikipedia.org/wiki/Cross-origin_resource_sharing) (CORS) là một [đặc tả W3C](https://www.w3.org/TR/cors/) cho phép bạn chỉ định một cách linh hoạt loại request cross-domain nào được cho phép. Nếu bạn dùng Spring MVC hoặc Spring WebFlux, bạn có thể cấu hình các web endpoint của Actuator để hỗ trợ những tình huống như vậy.

Hỗ trợ CORS bị tắt theo mặc định và chỉ được bật khi bạn đã đặt property `management.endpoints.web.cors.allowed-origins`. Cấu hình sau cho phép các lời gọi `GET` và `POST` từ domain `example.com`:

**Properties**

```properties
management.endpoints.web.cors.allowed-origins=https://example.com
management.endpoints.web.cors.allowed-methods=GET,POST
```

**Yaml**

```yaml
management:
  endpoints:
    web:
      cors:
        allowed-origins: "https://example.com"
        allowed-methods: "GET,POST"
```

> **MẸO (TIP):** Xem [`CorsEndpointProperties`](https://github.com/spring-projects/spring-boot/tree/v3.2.5/spring-boot-project/spring-boot-actuator-autoconfigure/src/main/java/org/springframework/boot/actuate/autoconfigure/endpoint/web/CorsEndpointProperties.java) để có danh sách đầy đủ các tùy chọn.

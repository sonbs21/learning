# 13.2.6. Hypermedia cho các web endpoint của Actuator (Hypermedia for Actuator Web Endpoints)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#actuator.endpoints.hypermedia>

Một "trang khám phá" ("discovery page") được thêm vào với các link tới tất cả các endpoint. "Trang khám phá" có sẵn tại `/actuator` theo mặc định.

Để tắt "trang khám phá", hãy thêm property sau vào application properties:

**Properties**

```properties
management.endpoints.web.discovery.enabled=false
```

**Yaml**

```yaml
management:
  endpoints:
    web:
      discovery:
        enabled: false
```

Khi một đường dẫn management context tùy chỉnh được cấu hình, "trang khám phá" tự động chuyển từ `/actuator` tới thư mục gốc của management context. Ví dụ, nếu đường dẫn management context là `/management`, trang khám phá có sẵn từ `/management`. Khi đường dẫn management context được đặt thành `/`, trang khám phá bị tắt để ngăn khả năng xung đột với các ánh xạ khác.

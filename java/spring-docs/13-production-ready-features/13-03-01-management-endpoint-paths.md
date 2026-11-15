# 13.3.1. Tùy chỉnh đường dẫn của management endpoint (Customizing the Management Endpoint Paths)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#actuator.monitoring.customizing-management-server-context-path>

Đôi khi, sẽ hữu ích nếu tùy chỉnh tiền tố cho các management endpoint. Ví dụ, ứng dụng của bạn có thể đã dùng `/actuator` cho một mục đích khác. Bạn có thể dùng property `management.endpoints.web.base-path` để thay đổi tiền tố cho management endpoint, như ví dụ sau cho thấy:

**Properties**

```properties
management.endpoints.web.base-path=/manage
```

**Yaml**

```yaml
management:
  endpoints:
    web:
      base-path: "/manage"
```

Ví dụ `application.properties` trên thay đổi endpoint từ `/actuator/{id}` thành `/manage/{id}` (ví dụ, `/manage/info`).

> **LƯU Ý (NOTE):** Trừ khi cổng management đã được cấu hình để [expose endpoint bằng một cổng HTTP khác](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#actuator.monitoring.customizing-management-server-port), `management.endpoints.web.base-path` là tương đối với `server.servlet.context-path` (cho ứng dụng web servlet) hoặc `spring.webflux.base-path` (cho ứng dụng web reactive). Nếu `management.server.port` được cấu hình, `management.endpoints.web.base-path` là tương đối với `management.server.base-path`.

Nếu bạn muốn ánh xạ endpoint tới một đường dẫn khác, bạn có thể dùng property `management.endpoints.web.path-mapping`.

Ví dụ sau ánh xạ lại `/actuator/health` thành `/healthcheck`:

**Properties**

```properties
management.endpoints.web.base-path=/
management.endpoints.web.path-mapping.health=healthcheck
```

**Yaml**

```yaml
management:
  endpoints:
    web:
      base-path: "/"
      path-mapping:
        health: "healthcheck"
```

# 13.2.1. Bật endpoint (Enabling Endpoints)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#actuator.endpoints.enabling>

Theo mặc định, tất cả các endpoint ngoại trừ `shutdown` đều được bật. Để cấu hình việc bật một endpoint, hãy dùng property `management.endpoint.<id>.enabled` của nó. Ví dụ sau bật endpoint `shutdown`:

**Properties**

```properties
management.endpoint.shutdown.enabled=true
```

**Yaml**

```yaml
management:
  endpoint:
    shutdown:
      enabled: true
```

Nếu bạn muốn việc bật endpoint là chủ động bật (opt-in) thay vì chủ động tắt (opt-out), hãy đặt property `management.endpoints.enabled-by-default` thành `false` và dùng các property `enabled` của từng endpoint để bật lại. Ví dụ sau bật endpoint `info` và tắt tất cả các endpoint khác:

**Properties**

```properties
management.endpoints.enabled-by-default=false
management.endpoint.info.enabled=true
```

**Yaml**

```yaml
management:
  endpoints:
    enabled-by-default: false
  endpoint:
    info:
      enabled: true
```

> **LƯU Ý (NOTE):** Các endpoint bị tắt sẽ bị loại bỏ hoàn toàn khỏi application context. Nếu bạn chỉ muốn thay đổi các công nghệ mà qua đó một endpoint được expose, hãy dùng [các property `include` và `exclude`](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#actuator.endpoints.exposing) thay thế.

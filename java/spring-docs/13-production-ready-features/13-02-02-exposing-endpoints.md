# 13.2.2. Expose endpoint (Exposing Endpoints)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#actuator.endpoints.exposing>

Theo mặc định, chỉ endpoint health được expose qua HTTP và JMX. Vì các Endpoint có thể chứa thông tin nhạy cảm, bạn nên cân nhắc cẩn thận khi nào nên expose chúng.

Để thay đổi các endpoint nào được expose, hãy dùng các property `include` và `exclude` đặc thù cho từng công nghệ sau:

| Property | Mặc định |
|---|---|
| `management.endpoints.jmx.exposure.exclude` |  |
| `management.endpoints.jmx.exposure.include` | `health` |
| `management.endpoints.web.exposure.exclude` |  |
| `management.endpoints.web.exposure.include` | `health` |

Property `include` liệt kê ID của các endpoint được expose. Property `exclude` liệt kê ID của các endpoint không nên được expose. Property `exclude` được ưu tiên hơn property `include`. Bạn có thể cấu hình cả hai property `include` và `exclude` với một danh sách ID endpoint.

Ví dụ, để chỉ expose các endpoint `health` và `info` qua JMX, hãy dùng property sau:

**Properties**

```properties
management.endpoints.jmx.exposure.include=health,info
```

**Yaml**

```yaml
management:
  endpoints:
    jmx:
      exposure:
        include: "health,info"
```

`*` có thể được dùng để chọn tất cả các endpoint. Ví dụ, để expose mọi thứ qua HTTP ngoại trừ các endpoint `env` và `beans`, hãy dùng các property sau:

**Properties**

```properties
management.endpoints.web.exposure.include=*
management.endpoints.web.exposure.exclude=env,beans
```

**Yaml**

```yaml
management:
  endpoints:
    web:
      exposure:
        include: "*"
        exclude: "env,beans"
```

> **LƯU Ý (NOTE):** `*` có ý nghĩa đặc biệt trong YAML, vì vậy hãy đảm bảo thêm dấu nháy nếu bạn muốn bao gồm (hoặc loại trừ) tất cả các endpoint.

> **LƯU Ý (NOTE):** Nếu ứng dụng của bạn được expose công khai, chúng tôi đặc biệt khuyên bạn cũng nên [bảo mật các endpoint](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#actuator.endpoints.security).

> **MẸO (TIP):** Nếu bạn muốn hiện thực chiến lược riêng cho việc khi nào endpoint được expose, bạn có thể đăng ký một bean `EndpointFilter`.

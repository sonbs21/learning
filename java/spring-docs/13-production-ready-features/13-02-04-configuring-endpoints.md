# 13.2.4. Cấu hình endpoint (Configuring Endpoints)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#actuator.endpoints.caching>

Các endpoint tự động cache response cho các thao tác đọc không nhận tham số nào. Để cấu hình khoảng thời gian mà một endpoint cache response, hãy dùng property `cache.time-to-live` của nó. Ví dụ sau đặt thời gian sống (time-to-live) của cache của endpoint `beans` là 10 giây:

**Properties**

```properties
management.endpoint.beans.cache.time-to-live=10s
```

**Yaml**

```yaml
management:
  endpoint:
    beans:
      cache:
        time-to-live: "10s"
```

> **LƯU Ý (NOTE):** Tiền tố `management.endpoint.<name>` xác định duy nhất endpoint đang được cấu hình.

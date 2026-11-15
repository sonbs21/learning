# 13.3.5. Tắt các HTTP endpoint (Disabling HTTP Endpoints)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#actuator.monitoring.disabling-http-endpoints>

Nếu bạn không muốn expose endpoint qua HTTP, bạn có thể đặt cổng management thành `-1`, như ví dụ sau cho thấy:

**Properties**

```properties
management.server.port=-1
```

**Yaml**

```yaml
management:
  server:
    port: -1
```

Bạn cũng có thể đạt được điều này bằng property `management.endpoints.web.exposure.exclude`, như ví dụ sau cho thấy:

**Properties**

```properties
management.endpoints.web.exposure.exclude=*
```

**Yaml**

```yaml
management:
  endpoints:
    web:
      exposure:
        exclude: "*"
```

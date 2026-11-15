# 13.4.2. Tắt các JMX endpoint (Disabling JMX Endpoints)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#actuator.jmx.disable-jmx-endpoints>

Nếu bạn không muốn expose endpoint qua JMX, bạn có thể đặt property `management.endpoints.jmx.exposure.exclude` thành `*`, như ví dụ sau cho thấy:

**Properties**

```properties
management.endpoints.jmx.exposure.exclude=*
```

**Yaml**

```yaml
management:
  endpoints:
    jmx:
      exposure:
        exclude: "*"
```

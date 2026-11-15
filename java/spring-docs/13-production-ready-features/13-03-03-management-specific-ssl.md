# 13.3.3. Cấu hình SSL riêng cho management (Configuring Management-specific SSL)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#actuator.monitoring.management-specific-ssl>

Khi được cấu hình để dùng một cổng tùy chỉnh, bạn cũng có thể cấu hình management server với SSL riêng bằng các property `management.server.ssl.*` khác nhau. Ví dụ, làm như vậy cho phép một management server khả dụng qua HTTP trong khi ứng dụng chính dùng HTTPS, như các thiết lập property sau cho thấy:

**Properties**

```properties
server.port=8443
server.ssl.enabled=true
server.ssl.key-store=classpath:store.jks
server.ssl.key-password=secret
management.server.port=8080
management.server.ssl.enabled=false
```

**Yaml**

```yaml
server:
  port: 8443
  ssl:
    enabled: true
    key-store: "classpath:store.jks"
    key-password: "secret"
management:
  server:
    port: 8080
    ssl:
      enabled: false
```

Ngoài ra, cả server chính lẫn management server đều có thể dùng SSL nhưng với các key store khác nhau, như sau:

**Properties**

```properties
server.port=8443
server.ssl.enabled=true
server.ssl.key-store=classpath:main.jks
server.ssl.key-password=secret
management.server.port=8080
management.server.ssl.enabled=true
management.server.ssl.key-store=classpath:management.jks
management.server.ssl.key-password=secret
```

**Yaml**

```yaml
server:
  port: 8443
  ssl:
    enabled: true
    key-store: "classpath:main.jks"
    key-password: "secret"
management:
  server:
    port: 8080
    ssl:
      enabled: true
      key-store: "classpath:management.jks"
      key-password: "secret"
```

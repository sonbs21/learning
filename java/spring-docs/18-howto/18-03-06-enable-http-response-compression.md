# 18.3.6. Bật nén HTTP response (Enable HTTP Response Compression)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.webserver.enable-response-compression>

Nén HTTP response được hỗ trợ bởi Jetty, Tomcat, Reactor Netty, và Undertow. Nó có thể được bật trong `application.properties`, như sau:

**Properties**

```properties
server.compression.enabled=true
```

**Yaml**

```yaml
server:
  compression:
    enabled: true
```

Theo mặc định, response phải có độ dài ít nhất 2048 byte thì việc nén mới được thực hiện. Bạn có thể cấu hình hành vi này bằng cách đặt property `server.compression.min-response-size`.

Theo mặc định, response chỉ được nén nếu content type của chúng là một trong các loại sau:

- `text/html`
- `text/xml`
- `text/plain`
- `text/css`
- `text/javascript`
- `application/javascript`
- `application/json`
- `application/xml`

Bạn có thể cấu hình hành vi này bằng cách đặt property `server.compression.mime-types`.

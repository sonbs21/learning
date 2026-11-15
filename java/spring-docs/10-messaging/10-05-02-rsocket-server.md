# 10.5.2. Auto-configuration cho RSocket server (RSocket server Auto-configuration)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#messaging.rsocket.server-auto-configuration>

Spring Boot cung cấp auto-configuration cho RSocket server. Các dependency cần thiết được cung cấp bởi `spring-boot-starter-rsocket`.

Spring Boot cho phép expose RSocket qua WebSocket từ một server WebFlux, hoặc dựng một RSocket server độc lập. Điều này phụ thuộc vào loại ứng dụng và cấu hình của nó.

Với ứng dụng WebFlux (tức là loại `WebApplicationType.REACTIVE`), RSocket server sẽ được cắm vào Web Server chỉ khi các property sau khớp:

**Properties**

```properties
spring.rsocket.server.mapping-path=/rsocket
spring.rsocket.server.transport=websocket
```

**Yaml**

```yaml
spring:
  rsocket:
    server:
      mapping-path: "/rsocket"
      transport: "websocket"
```

> **CẢNH BÁO (WARNING):** Việc cắm RSocket vào một web server chỉ được hỗ trợ với Reactor Netty, vì bản thân RSocket được xây dựng bằng thư viện đó.

Ngoài ra, một RSocket server TCP hoặc websocket được khởi động như một server nhúng độc lập. Bên cạnh các yêu cầu về dependency, cấu hình duy nhất cần có là định nghĩa một cổng cho server đó:

**Properties**

```properties
spring.rsocket.server.port=9898
```

**Yaml**

```yaml
spring:
  rsocket:
    server:
      port: 9898
```

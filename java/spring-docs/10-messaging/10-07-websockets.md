# 10.7. WebSocket (WebSockets)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#messaging.websockets>

Spring Boot cung cấp auto-configuration WebSocket cho Tomcat, Jetty, và Undertow nhúng. Nếu bạn triển khai file war lên một container độc lập, Spring Boot giả định rằng container chịu trách nhiệm cấu hình hỗ trợ WebSocket của nó.

Spring Framework cung cấp [hỗ trợ WebSocket phong phú](https://docs.spring.io/spring-framework/reference/6.1/web/websocket.html) cho các ứng dụng web MVC, có thể dễ dàng truy cập thông qua module `spring-boot-starter-websocket`.

Hỗ trợ WebSocket cũng có sẵn cho [các ứng dụng web reactive](https://docs.spring.io/spring-framework/reference/6.1/web/webflux-websocket.html) và yêu cầu thêm WebSocket API cùng với `spring-boot-starter-webflux`:

```xml
<dependency>
    <groupId>jakarta.websocket</groupId>
    <artifactId>jakarta.websocket-api</artifactId>
</dependency>
```

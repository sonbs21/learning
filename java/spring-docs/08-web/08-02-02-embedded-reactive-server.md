# 8.2.2. Hỗ trợ reactive server nhúng (Embedded Reactive Server Support)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#web.reactive.reactive-server>

Spring Boot bao gồm hỗ trợ cho các reactive web server nhúng sau: Reactor Netty, Tomcat, Jetty, và Undertow. Hầu hết các lập trình viên dùng "Starter" phù hợp để có được một instance đã được cấu hình đầy đủ. Theo mặc định, server nhúng lắng nghe HTTP request trên cổng 8080.

## Tùy chỉnh reactive server (Customizing Reactive Servers)

Các thiết lập reactive web server phổ biến có thể được cấu hình bằng các property của `Environment` Spring. Thông thường, bạn sẽ định nghĩa các property trong file `application.properties` hoặc `application.yaml`.

Các thiết lập server phổ biến bao gồm:

- Thiết lập mạng: Cổng lắng nghe HTTP request đến (`server.port`), địa chỉ interface để bind (`server.address`), v.v.
- Quản lý lỗi: Vị trí của trang lỗi (`server.error.path`), v.v.
- [SSL](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.webserver.configure-ssl)
- [Nén HTTP (HTTP compression)](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.webserver.enable-response-compression)

Spring Boot cố gắng hết mức có thể để expose các thiết lập phổ biến, nhưng điều này không phải lúc nào cũng khả thi. Với những trường hợp đó, các namespace chuyên biệt như `server.netty.*` cung cấp các tùy chỉnh đặc thù cho từng server.

> **MẸO (TIP):** Xem class [`ServerProperties`](https://github.com/spring-projects/spring-boot/tree/v3.2.5/spring-boot-project/spring-boot-autoconfigure/src/main/java/org/springframework/boot/autoconfigure/web/ServerProperties.java) để có danh sách đầy đủ.

### Tùy chỉnh bằng code (Programmatic Customization)

Nếu bạn cần cấu hình reactive web server bằng code, bạn có thể đăng ký một Spring bean hiện thực interface `WebServerFactoryCustomizer`. `WebServerFactoryCustomizer` cung cấp quyền truy cập tới `ConfigurableReactiveWebServerFactory`, bao gồm rất nhiều phương thức setter để tùy chỉnh. Ví dụ sau cho thấy việc đặt cổng bằng code:

**Java**

```java
import org.springframework.boot.web.reactive.server.ConfigurableReactiveWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.stereotype.Component;

@Component
public class MyWebServerFactoryCustomizer implements WebServerFactoryCustomizer<ConfigurableReactiveWebServerFactory> {

    @Override
    public void customize(ConfigurableReactiveWebServerFactory server) {
        server.setPort(9000);
    }

}
```

**Kotlin**

```kotlin
import org.springframework.boot.web.server.WebServerFactoryCustomizer
import org.springframework.boot.web.reactive.server.ConfigurableReactiveWebServerFactory
import org.springframework.stereotype.Component

@Component
class MyWebServerFactoryCustomizer : WebServerFactoryCustomizer<ConfigurableReactiveWebServerFactory> {

    override fun customize(server: ConfigurableReactiveWebServerFactory) {
        server.setPort(9000)
    }

}
```

`JettyReactiveWebServerFactory`, `NettyReactiveWebServerFactory`, `TomcatReactiveWebServerFactory`, và `UndertowReactiveWebServerFactory` là các biến thể chuyên biệt của `ConfigurableReactiveWebServerFactory`, có thêm các phương thức setter tùy chỉnh lần lượt cho Jetty, Reactor Netty, Tomcat, và Undertow. Ví dụ sau cho thấy cách tùy chỉnh `NettyReactiveWebServerFactory`, cung cấp quyền truy cập tới các tùy chọn cấu hình đặc thù của Reactor Netty:

**Java**

```java
import java.time.Duration;

import org.springframework.boot.web.embedded.netty.NettyReactiveWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.stereotype.Component;

@Component
public class MyNettyWebServerFactoryCustomizer implements WebServerFactoryCustomizer<NettyReactiveWebServerFactory> {

    @Override
    public void customize(NettyReactiveWebServerFactory factory) {
        factory.addServerCustomizers((server) -> server.idleTimeout(Duration.ofSeconds(20)));
    }

}
```

**Kotlin**

```kotlin
import org.springframework.boot.web.embedded.netty.NettyReactiveWebServerFactory
import org.springframework.boot.web.server.WebServerFactoryCustomizer
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class MyNettyWebServerFactoryCustomizer : WebServerFactoryCustomizer<NettyReactiveWebServerFactory> {

    override fun customize(factory: NettyReactiveWebServerFactory) {
        factory.addServerCustomizers({ server -> server.idleTimeout(Duration.ofSeconds(20)) })
    }

}
```

### Tùy chỉnh trực tiếp ConfigurableReactiveWebServerFactory (Customizing ConfigurableReactiveWebServerFactory Directly)

Với các trường hợp sử dụng nâng cao hơn yêu cầu bạn kế thừa từ `ReactiveWebServerFactory`, bạn có thể tự expose một bean thuộc kiểu đó.

Các setter được cung cấp cho nhiều tùy chọn cấu hình. Một số phương thức protected đóng vai trò "hook" cũng được cung cấp nếu bạn cần làm điều gì đó đặc biệt hơn. Xem [tài liệu mã nguồn](https://docs.spring.io/spring-boot/docs/3.2.5/api/org/springframework/boot/web/reactive/server/ConfigurableReactiveWebServerFactory.html) để biết chi tiết.

> **LƯU Ý (NOTE):** Các customizer được tự động cấu hình vẫn được áp dụng lên factory tùy chỉnh của bạn, vì vậy hãy dùng tùy chọn đó một cách cẩn thận.

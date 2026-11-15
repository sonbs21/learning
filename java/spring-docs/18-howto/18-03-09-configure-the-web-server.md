# 18.3.9. Cấu hình web server (Configure the Web Server)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.webserver.configure>

Nói chung, trước tiên bạn nên cân nhắc dùng một trong nhiều configuration key có sẵn và tùy chỉnh web server bằng cách thêm các mục mới vào file `application.properties` hoặc `application.yaml`. Xem "[Khám phá các tùy chọn có sẵn cho property bên ngoài (Discover Built-in Options for External Properties)](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.properties-and-configuration.discover-build-in-options-for-external-properties)"). Namespace `server.*` khá hữu ích ở đây, và nó bao gồm các namespace như `server.tomcat.*`, `server.jetty.*` và các namespace khác, cho các tính năng đặc thù của từng server. Xem danh sách [Các property ứng dụng phổ biến (Common Application Properties)](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#appendix.application-properties).

Các phần trước đã đề cập nhiều trường hợp sử dụng phổ biến, chẳng hạn như nén, SSL hoặc HTTP/2. Tuy nhiên, nếu không có configuration key cho trường hợp sử dụng của bạn, thì bạn nên xem [`WebServerFactoryCustomizer`](https://docs.spring.io/spring-boot/docs/3.2.5/api/org/springframework/boot/web/server/WebServerFactoryCustomizer.html). Bạn có thể khai báo một component như vậy và có quyền truy cập vào server factory tương ứng với lựa chọn của mình: bạn nên chọn biến thể cho Server đã chọn (Tomcat, Jetty, Reactor Netty, Undertow) và web stack đã chọn (servlet hoặc reactive).

Ví dụ dưới đây dành cho Tomcat với `spring-boot-starter-web` (servlet stack):

**Java**

```java
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.stereotype.Component;

@Component
public class MyTomcatWebServerCustomizer implements WebServerFactoryCustomizer<TomcatServletWebServerFactory> {

    @Override
    public void customize(TomcatServletWebServerFactory factory) {
        // customize the factory here
    }

}
```

**Kotlin**

```kotlin
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory
import org.springframework.boot.web.server.WebServerFactoryCustomizer
import org.springframework.stereotype.Component

@Component
class MyTomcatWebServerCustomizer : WebServerFactoryCustomizer<TomcatServletWebServerFactory?> {

    override fun customize(factory: TomcatServletWebServerFactory?) {
        // customize the factory here
    }

}
```

> **LƯU Ý (NOTE):** Spring Boot dùng hạ tầng đó nội bộ để auto-configure server. Các bean `WebServerFactoryCustomizer` được auto-configure có order là `0` và sẽ được xử lý trước mọi customizer do người dùng định nghĩa, trừ khi nó có một order tường minh nói khác.

Một khi bạn đã có quyền truy cập vào một `WebServerFactory` thông qua customizer, bạn có thể dùng nó để cấu hình các phần cụ thể, như connector, tài nguyên server, hoặc chính server - tất cả đều dùng các API đặc thù của server.

Ngoài ra, Spring Boot cung cấp:

| Server | Servlet stack | Reactive stack |
|---|---|---|
| Tomcat | `TomcatServletWebServerFactory` | `TomcatReactiveWebServerFactory` |
| Jetty | `JettyServletWebServerFactory` | `JettyReactiveWebServerFactory` |
| Undertow | `UndertowServletWebServerFactory` | `UndertowReactiveWebServerFactory` |
| Reactor | N/A | `NettyReactiveWebServerFactory` |

Phương án cuối cùng, bạn cũng có thể khai báo bean `WebServerFactory` của riêng mình, bean này sẽ ghi đè bean do Spring Boot cung cấp. Khi làm vậy, các customizer được auto-configure vẫn được áp dụng lên factory tùy chỉnh của bạn, vì vậy hãy dùng tùy chọn đó một cách cẩn thận.

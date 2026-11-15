# 18.14.3. Bật HTTPS khi chạy sau một proxy server (Enable HTTPS When Running behind a Proxy Server)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.security.enable-https>

Đảm bảo rằng tất cả các endpoint chính của bạn chỉ có thể truy cập qua HTTPS là một công việc quan trọng đối với bất kỳ ứng dụng nào. Nếu bạn dùng Tomcat làm servlet container, thì Spring Boot sẽ tự động thêm `RemoteIpValve` của chính Tomcat nếu nó phát hiện một số thiết lập môi trường, cho phép bạn dựa vào `HttpServletRequest` để báo cáo liệu request có an toàn hay không (ngay cả khi ở phía sau một proxy server xử lý việc SSL termination thực sự). Hành vi chuẩn được xác định bởi sự có mặt hoặc vắng mặt của một số request header nhất định (`x-forwarded-for` và `x-forwarded-proto`), có tên theo quy ước, vì vậy nó sẽ hoạt động với hầu hết các front-end proxy. Bạn có thể bật valve bằng cách thêm một số mục vào `application.properties`, như trong ví dụ sau:

**Properties**

```properties
server.tomcat.remoteip.remote-ip-header=x-forwarded-for
server.tomcat.remoteip.protocol-header=x-forwarded-proto
```

**Yaml**

```yaml
server:
  tomcat:
    remoteip:
      remote-ip-header: "x-forwarded-for"
      protocol-header: "x-forwarded-proto"
```

(Sự có mặt của bất kỳ property nào trong số đó sẽ bật valve. Ngoài ra, bạn có thể thêm `RemoteIpValve` bằng cách tùy chỉnh `TomcatServletWebServerFactory` thông qua một bean `WebServerFactoryCustomizer`.)

Để cấu hình Spring Security yêu cầu kênh bảo mật cho tất cả (hoặc một số) request, hãy cân nhắc thêm bean `SecurityFilterChain` của riêng bạn có thêm cấu hình `HttpSecurity` sau:

**Java**

```java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class MySecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // Customize the application security ...
        http.requiresChannel((channel) -> channel.anyRequest().requiresSecure());
        return http.build();
    }

}
```

**Kotlin**

```kotlin
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain

@Configuration
class MySecurityConfig {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        // Customize the application security ...
        http.requiresChannel { requests -> requests.anyRequest().requiresSecure() }
        return http.build()
    }

}
```

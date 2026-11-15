# 8.4.2. Bảo mật WebFlux (WebFlux Security)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#web.security.spring-webflux>

Tương tự như các ứng dụng Spring MVC, bạn có thể bảo mật các ứng dụng WebFlux bằng cách thêm dependency `spring-boot-starter-security`. Cấu hình bảo mật mặc định được hiện thực trong `ReactiveSecurityAutoConfiguration` và `UserDetailsServiceAutoConfiguration`. `ReactiveSecurityAutoConfiguration` import `WebFluxSecurityConfiguration` cho bảo mật web và `UserDetailsServiceAutoConfiguration` cấu hình việc xác thực, điều này cũng liên quan trong các ứng dụng không phải web.

Để tắt hoàn toàn cấu hình bảo mật ứng dụng web mặc định, bạn có thể thêm một bean kiểu `WebFilterChainProxy` (làm vậy không vô hiệu hóa cấu hình `UserDetailsService` hay bảo mật của Actuator). Để tắt luôn cấu hình `UserDetailsService`, bạn có thể thêm một bean kiểu `ReactiveUserDetailsService` hoặc `ReactiveAuthenticationManager`.

Auto-configuration cũng sẽ lùi lại khi bất kỳ module Spring Security nào sau đây nằm trên classpath:

- `spring-security-oauth2-client`
- `spring-security-oauth2-resource-server`

Để dùng `ReactiveUserDetailsService` cùng với một hoặc nhiều dependency này, hãy định nghĩa bean `MapReactiveUserDetailsService` của riêng bạn.

Các quy tắc truy cập và việc dùng nhiều component Spring Security như OAuth 2 Client và Resource Server có thể được cấu hình bằng cách thêm một bean `SecurityWebFilterChain` tùy chỉnh. Spring Boot cung cấp các phương thức tiện lợi có thể dùng để ghi đè quy tắc truy cập cho các endpoint actuator và tài nguyên tĩnh. `EndpointRequest` có thể được dùng để tạo một `ServerWebExchangeMatcher` dựa trên property `management.endpoints.web.base-path`.

`PathRequest` có thể được dùng để tạo một `ServerWebExchangeMatcher` cho tài nguyên ở các vị trí thường dùng.

Ví dụ, bạn có thể tùy chỉnh cấu hình bảo mật bằng cách thêm nội dung như sau:

**Java**

```java
import org.springframework.boot.autoconfigure.security.reactive.PathRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration(proxyBeanMethods = false)
public class MyWebFluxSecurityConfiguration {

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        http.authorizeExchange((exchange) -> {
            exchange.matchers(PathRequest.toStaticResources().atCommonLocations()).permitAll();
            exchange.pathMatchers("/foo", "/bar").authenticated();
        });
        http.formLogin(withDefaults());
        return http.build();
    }

}
```

**Kotlin**

```kotlin
import org.springframework.boot.autoconfigure.security.reactive.PathRequest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.Customizer.withDefaults
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.web.server.SecurityWebFilterChain

@Configuration(proxyBeanMethods = false)
class MyWebFluxSecurityConfiguration {

    @Bean
    fun springSecurityFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain {
        http.authorizeExchange { spec ->
            spec.matchers(PathRequest.toStaticResources().atCommonLocations()).permitAll()
            spec.pathMatchers("/foo", "/bar").authenticated()
        }
        http.formLogin(withDefaults())
        return http.build()
    }

}
```

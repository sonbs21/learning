# 9.1.6. Sử dụng Web Console của H2 (Using H2's Web Console)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#data.sql.h2-web-console>

[Database H2](https://www.h2database.com) cung cấp một [console dựa trên trình duyệt](https://www.h2database.com/html/quickstart.html#h2_console) mà Spring Boot có thể tự động cấu hình cho bạn. Console được tự động cấu hình khi các điều kiện sau được đáp ứng:

- Bạn đang phát triển một ứng dụng web dựa trên servlet.
- `com.h2database:h2` nằm trên classpath.
- Bạn đang dùng [developer tools của Spring Boot](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#using.devtools).

> **MẸO (TIP):** Nếu bạn không dùng developer tools của Spring Boot nhưng vẫn muốn dùng console của H2, bạn có thể cấu hình property `spring.h2.console.enabled` với giá trị `true`.

> **LƯU Ý (NOTE):** Console H2 chỉ nhằm để dùng trong quá trình phát triển, vì vậy bạn nên cẩn thận đảm bảo `spring.h2.console.enabled` không được đặt thành `true` trên production.

## Thay đổi đường dẫn của H2 Console (Changing the H2 Console's Path)

Theo mặc định, console có sẵn tại `/h2-console`. Bạn có thể tùy chỉnh đường dẫn của console bằng property `spring.h2.console.path`.

## Truy cập H2 Console trong một ứng dụng được bảo mật (Accessing the H2 Console in a Secured Application)

H2 Console dùng frame và, vì nó chỉ nhằm cho mục đích phát triển, không hiện thực các biện pháp bảo vệ CSRF. Nếu ứng dụng của bạn dùng Spring Security, bạn cần cấu hình nó để

- tắt bảo vệ CSRF cho các request tới console,
- đặt header `X-Frame-Options` thành `SAMEORIGIN` trên các response từ console.

Thông tin thêm về [CSRF](https://docs.spring.io/spring-security/reference/6.2/features/exploits/csrf.html) và header [X-Frame-Options](https://docs.spring.io/spring-security/reference/6.2/features/exploits/headers.html#headers-frame-options) có thể được tìm thấy trong Tài liệu tham khảo Spring Security.

Trong các thiết lập đơn giản, có thể dùng một `SecurityFilterChain` như sau:

**Java**

```java
import org.springframework.boot.autoconfigure.security.servlet.PathRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Profile("dev")
@Configuration(proxyBeanMethods = false)
public class DevProfileSecurityConfiguration {

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    SecurityFilterChain h2ConsoleSecurityFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher(PathRequest.toH2Console());
        http.authorizeHttpRequests(yourCustomAuthorization());
        http.csrf((csrf) -> csrf.disable());
        http.headers((headers) -> headers.frameOptions((frame) -> frame.sameOrigin()));
        return http.build();
    }


}
```

**Kotlin**

```kotlin
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain

@Profile("dev")
@Configuration(proxyBeanMethods = false)
class DevProfileSecurityConfiguration {

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    fun h2ConsoleSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        return http.authorizeHttpRequests(yourCustomAuthorization())
            .csrf { csrf -> csrf.disable() }
            .headers { headers -> headers.frameOptions { frameOptions -> frameOptions.sameOrigin() } }
            .build()
    }


}
```

> **CẢNH BÁO (WARNING):** Console H2 chỉ nhằm để dùng trong quá trình phát triển. Trên production, việc tắt bảo vệ CSRF hoặc cho phép frame cho một website có thể tạo ra các rủi ro bảo mật nghiêm trọng.

> **MẸO (TIP):** `PathRequest.toH2Console()` cũng trả về đúng request matcher khi đường dẫn của console đã được tùy chỉnh.

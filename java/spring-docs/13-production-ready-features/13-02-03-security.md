# 13.2.3. Bảo mật (Security)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#actuator.endpoints.security>

Vì mục đích bảo mật, chỉ endpoint `/health` được expose qua HTTP theo mặc định. Bạn có thể dùng property `management.endpoints.web.exposure.include` để cấu hình các endpoint được expose.

> **LƯU Ý (NOTE):** Trước khi đặt `management.endpoints.web.exposure.include`, hãy đảm bảo các actuator được expose không chứa thông tin nhạy cảm, được bảo mật bằng cách đặt chúng phía sau firewall, hoặc được bảo mật bằng thứ gì đó như Spring Security.

Nếu Spring Security có trên classpath và không có bean `SecurityFilterChain` nào khác, tất cả các actuator ngoài `/health` đều được bảo mật bởi auto-configuration của Spring Boot. Nếu bạn định nghĩa một bean `SecurityFilterChain` tùy chỉnh, auto-configuration của Spring Boot sẽ lùi lại và cho phép bạn kiểm soát hoàn toàn các quy tắc truy cập actuator.

Nếu bạn muốn cấu hình bảo mật tùy chỉnh cho các HTTP endpoint (ví dụ, chỉ cho phép người dùng có một role nhất định truy cập chúng), Spring Boot cung cấp một số object `RequestMatcher` tiện lợi mà bạn có thể dùng kết hợp với Spring Security.

Một cấu hình Spring Security điển hình có thể trông giống như ví dụ sau:

**Java**

```java
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration(proxyBeanMethods = false)
public class MySecurityConfiguration {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher(EndpointRequest.toAnyEndpoint());
        http.authorizeHttpRequests((requests) -> requests.anyRequest().hasRole("ENDPOINT_ADMIN"));
        http.httpBasic(withDefaults());
        return http.build();
    }

}
```

**Kotlin**

```kotlin
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.Customizer.withDefaults
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain

@Configuration(proxyBeanMethods = false)
class MySecurityConfiguration {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http.securityMatcher(EndpointRequest.toAnyEndpoint()).authorizeHttpRequests { requests ->
            requests.anyRequest().hasRole("ENDPOINT_ADMIN")
        }
        http.httpBasic(withDefaults())
        return http.build()
    }

}
```

Ví dụ trên dùng `EndpointRequest.toAnyEndpoint()` để khớp một request với bất kỳ endpoint nào rồi đảm bảo tất cả đều có role `ENDPOINT_ADMIN`. Một số phương thức matcher khác cũng có sẵn trên `EndpointRequest`. Xem tài liệu API ([HTML](https://docs.spring.io/spring-boot/docs/3.2.5/actuator-api/htmlsingle) hoặc [PDF](https://docs.spring.io/spring-boot/docs/3.2.5/actuator-api/pdf/spring-boot-actuator-web-api.pdf)) để biết chi tiết.

Nếu bạn triển khai ứng dụng phía sau firewall, bạn có thể muốn tất cả các actuator endpoint có thể được truy cập mà không cần xác thực. Bạn có thể làm điều đó bằng cách thay đổi property `management.endpoints.web.exposure.include`, như sau:

**Properties**

```properties
management.endpoints.web.exposure.include=*
```

**Yaml**

```yaml
management:
  endpoints:
    web:
      exposure:
        include: "*"
```

Ngoài ra, nếu có Spring Security, bạn sẽ cần thêm cấu hình bảo mật tùy chỉnh cho phép truy cập không cần xác thực tới các endpoint, như ví dụ sau cho thấy:

**Java**

```java
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
public class MySecurityConfiguration {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher(EndpointRequest.toAnyEndpoint());
        http.authorizeHttpRequests((requests) -> requests.anyRequest().permitAll());
        return http.build();
    }

}
```

**Kotlin**

```kotlin
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain

@Configuration(proxyBeanMethods = false)
class MySecurityConfiguration {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http.securityMatcher(EndpointRequest.toAnyEndpoint()).authorizeHttpRequests { requests ->
            requests.anyRequest().permitAll()
        }
        return http.build()
    }

}
```

> **LƯU Ý (NOTE):** Trong cả hai ví dụ trên, cấu hình chỉ áp dụng cho các actuator endpoint. Vì cấu hình bảo mật của Spring Boot lùi lại hoàn toàn khi có bất kỳ bean `SecurityFilterChain` nào, bạn cần cấu hình thêm một bean `SecurityFilterChain` với các quy tắc áp dụng cho phần còn lại của ứng dụng.

## Bảo vệ chống giả mạo request liên trang (Cross Site Request Forgery Protection)

Vì Spring Boot dựa vào các giá trị mặc định của Spring Security, bảo vệ CSRF được bật theo mặc định. Điều này có nghĩa là các actuator endpoint yêu cầu `POST` (các endpoint shutdown và loggers), `PUT`, hoặc `DELETE` sẽ nhận lỗi 403 (forbidden) khi cấu hình bảo mật mặc định đang được dùng.

> **LƯU Ý (NOTE):** Chúng tôi khuyên chỉ tắt hoàn toàn bảo vệ CSRF nếu bạn đang tạo một service được dùng bởi các client không phải trình duyệt.

Bạn có thể tìm thêm thông tin về bảo vệ CSRF trong [Tài liệu tham khảo Spring Security](https://docs.spring.io/spring-security/reference/6.2/features/exploits/csrf.html).

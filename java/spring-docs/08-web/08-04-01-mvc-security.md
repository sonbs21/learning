# 8.4.1. Bảo mật MVC (MVC Security)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#web.security.spring-mvc>

Cấu hình bảo mật mặc định được hiện thực trong `SecurityAutoConfiguration` và `UserDetailsServiceAutoConfiguration`. `SecurityAutoConfiguration` import `SpringBootWebSecurityConfiguration` cho bảo mật web và `UserDetailsServiceAutoConfiguration` cấu hình việc xác thực, điều này cũng liên quan trong các ứng dụng không phải web.

Để tắt hoàn toàn cấu hình bảo mật ứng dụng web mặc định hoặc để kết hợp nhiều component Spring Security như OAuth2 Client và Resource Server, hãy thêm một bean kiểu `SecurityFilterChain` (làm vậy không vô hiệu hóa cấu hình `UserDetailsService` hay bảo mật của Actuator). Để tắt luôn cấu hình `UserDetailsService`, bạn có thể thêm một bean kiểu `UserDetailsService`, `AuthenticationProvider`, hoặc `AuthenticationManager`.

Auto-configuration của `UserDetailsService` cũng sẽ lùi lại nếu bất kỳ module Spring Security nào sau đây nằm trên classpath:

- `spring-security-oauth2-client`
- `spring-security-oauth2-resource-server`
- `spring-security-saml2-service-provider`

Để dùng `UserDetailsService` cùng với một hoặc nhiều dependency này, hãy định nghĩa bean `InMemoryUserDetailsManager` của riêng bạn.

Các quy tắc truy cập có thể được ghi đè bằng cách thêm một bean `SecurityFilterChain` tùy chỉnh. Spring Boot cung cấp các phương thức tiện lợi có thể dùng để ghi đè quy tắc truy cập cho các endpoint actuator và tài nguyên tĩnh. `EndpointRequest` có thể được dùng để tạo một `RequestMatcher` dựa trên property `management.endpoints.web.base-path`. `PathRequest` có thể được dùng để tạo một `RequestMatcher` cho tài nguyên ở các vị trí thường dùng.

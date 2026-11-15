# 8.4. Spring Security

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#web.security>

Nếu [Spring Security](https://spring.io/projects/spring-security) nằm trên classpath, thì các ứng dụng web được bảo mật theo mặc định. Spring Boot dựa vào chiến lược đàm phán nội dung (content-negotiation) của Spring Security để xác định nên dùng `httpBasic` hay `formLogin`. Để thêm bảo mật ở cấp phương thức (method-level security) cho một ứng dụng web, bạn cũng có thể thêm `@EnableGlobalMethodSecurity` với các thiết lập mong muốn. Thông tin bổ sung có thể được tìm thấy trong [Tài liệu tham khảo Spring Security](https://docs.spring.io/spring-security/reference/6.2/servlet/authorization/method-security.html).

`UserDetailsService` mặc định có một user duy nhất. Tên user là `user`, và mật khẩu là ngẫu nhiên và được in ra ở mức WARN khi ứng dụng khởi động, như trong ví dụ sau:

```
Using generated security password: 78fa095d-3f4c-48b1-ad50-e24c31d5cf35

This generated password is for development use only. Your security configuration must be updated before running your application in production.
```

> **LƯU Ý (NOTE):** Nếu bạn tinh chỉnh cấu hình logging, hãy đảm bảo category `org.springframework.boot.autoconfigure.security` được đặt để ghi log các thông điệp mức `WARN`. Nếu không, mật khẩu mặc định sẽ không được in ra.

Bạn có thể thay đổi username và password bằng cách cung cấp `spring.security.user.name` và `spring.security.user.password`.

Các tính năng cơ bản bạn có mặc định trong một ứng dụng web là:

- Một bean `UserDetailsService` (hoặc `ReactiveUserDetailsService` trong trường hợp ứng dụng WebFlux) với kho lưu trữ in-memory và một user duy nhất có mật khẩu được sinh ra (xem [`SecurityProperties.User`](https://docs.spring.io/spring-boot/docs/3.2.5/api/org/springframework/boot/autoconfigure/security/SecurityProperties.User.html) để biết các property của user).
- Đăng nhập dựa trên form hoặc bảo mật HTTP Basic (tùy thuộc vào header `Accept` trong request) cho toàn bộ ứng dụng (bao gồm cả các endpoint actuator nếu actuator nằm trên classpath).
- Một `DefaultAuthenticationEventPublisher` để publish các event xác thực.

Bạn có thể cung cấp một `AuthenticationEventPublisher` khác bằng cách thêm một bean cho nó.

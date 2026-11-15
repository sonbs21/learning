# 18.14.2. Thay đổi UserDetailsService và thêm tài khoản người dùng (Change the UserDetailsService and Add User Accounts)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.security.change-user-details-service-and-add-user-accounts>

Nếu bạn cung cấp một `@Bean` kiểu `AuthenticationManager`, `AuthenticationProvider`, hoặc `UserDetailsService`, `@Bean` mặc định cho `InMemoryUserDetailsManager` sẽ không được tạo. Điều này có nghĩa là bạn có sẵn toàn bộ bộ tính năng của Spring Security (chẳng hạn như [nhiều tùy chọn xác thực khác nhau](https://docs.spring.io/spring-security/reference/6.2/servlet/authentication/index.html)).

Cách dễ nhất để thêm tài khoản người dùng là cung cấp bean `UserDetailsService` của riêng bạn.

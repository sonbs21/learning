# 18.14.1. Tắt cấu hình bảo mật của Spring Boot (Switch off the Spring Boot Security Configuration)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.security.switch-off-spring-boot-configuration>

Nếu bạn định nghĩa một `@Configuration` có bean `SecurityFilterChain` trong ứng dụng của mình, hành động này sẽ tắt các thiết lập bảo mật webapp mặc định trong Spring Boot.

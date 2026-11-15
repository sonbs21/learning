# 18.8.9. Dùng EntityManagerFactory tùy chỉnh (Use a Custom EntityManagerFactory)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.data-access.use-custom-entity-manager>

Để kiểm soát hoàn toàn cấu hình của `EntityManagerFactory`, bạn cần thêm một `@Bean` tên là 'entityManagerFactory'. Auto-configuration của Spring Boot sẽ tắt entity manager của nó khi có một bean kiểu đó.

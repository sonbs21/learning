# 18.8.11. Dùng file persistence.xml truyền thống (Use a Traditional persistence.xml File)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.data-access.use-traditional-persistence-xml>

Theo mặc định, Spring Boot sẽ không tìm kiếm hoặc dùng `META-INF/persistence.xml`. Nếu bạn muốn dùng `persistence.xml` truyền thống, bạn cần định nghĩa `@Bean` của riêng mình kiểu `LocalEntityManagerFactoryBean` (với ID là 'entityManagerFactory') và đặt tên persistence unit ở đó.

Xem [`JpaBaseConfiguration`](https://github.com/spring-projects/spring-boot/tree/v3.2.5/spring-boot-project/spring-boot-autoconfigure/src/main/java/org/springframework/boot/autoconfigure/orm/jpa/JpaBaseConfiguration.java) để biết các thiết lập mặc định.

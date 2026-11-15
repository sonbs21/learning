# 18.8.8. Dùng dependency injection trong các component của Hibernate (Use Dependency Injection in Hibernate Components)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.data-access.dependency-injection-in-hibernate-components>

Theo mặc định, Spring Boot đăng ký một implementation `BeanContainer` dùng `BeanFactory` để các converter và entity listener có thể dùng dependency injection thông thường.

Bạn có thể vô hiệu hóa hoặc tinh chỉnh hành vi này bằng cách đăng ký một `HibernatePropertiesCustomizer` loại bỏ hoặc thay đổi property `hibernate.resource.beans.container`.

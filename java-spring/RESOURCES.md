# Spring Boot Resources

## Knowledge

- [Spring Boot Reference — Auto-configuration](https://docs.spring.io/spring-boot/reference/using/auto-configuration.html)
  Nguồn chính thức. Use for: auto-configuration "lùi lại" (back off), `--debug`, exclude.
- [Spring Boot Reference — Creating Your Own Auto-configuration](https://docs.spring.io/spring-boot/reference/features/developing-auto-configuration.html)
  Cơ chế thật bên dưới: `AutoConfiguration.imports`, `@Conditional*`, thứ tự nạp. Use for: mọi câu hỏi "Boot quyết định thế nào".
- [Spring Boot Reference — Using @SpringBootApplication](https://docs.spring.io/spring-boot/reference/using/using-the-springbootapplication-annotation.html)
  Annotation này gồm những gì. Use for: câu hỏi phỏng vấn về `@SpringBootApplication`.
- [Spring Framework Reference — The IoC Container](https://docs.spring.io/spring-framework/reference/core/beans.html)
  Bản chất ApplicationContext, bean definition, lifecycle. Use for: bean, scope, BeanPostProcessor.
- [Spring Framework Reference — Bean Scopes](https://docs.spring.io/spring-framework/reference/core/beans/factory-scopes.html)
  Singleton/prototype/web scopes; prototype không gọi destroy callback. Use for: bài 4.
- [Spring Framework Reference — @Primary](https://docs.spring.io/spring-framework/reference/core/beans/annotation-config/autowired-primary.html) / [@Qualifier](https://docs.spring.io/spring-framework/reference/core/beans/annotation-config/autowired-qualifiers.html)
  Use for: chọn bean khi có nhiều ứng viên.
- [Spring Framework Reference — @Bean and @Configuration](https://docs.spring.io/spring-framework/reference/core/beans/java/basic-concepts.html)
  Use for: tạo bean cho class thư viện.
- [Spring Boot Reference — Externalized Configuration](https://docs.spring.io/spring-boot/reference/features/external-config.html)
  Thứ tự nguồn cấu hình, profile, `@ConfigurationProperties`. Use for: bài 5.
- [Spring MVC — Annotated Controllers](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann.html) và [Spring Guide: Building a RESTful Web Service](https://spring.io/guides/gs/rest-service)
  Use for: REST API.
- [Spring Framework Reference — Proxying Mechanisms (AOP)](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html)
  JDK vs CGLIB proxy, self-invocation. Use for: vì sao `@Transactional` không chạy.
- [Spring Framework Reference — Transaction Management](https://docs.spring.io/spring-framework/reference/data-access/transaction.html)
  Use for: propagation, rollback rules, declarative transactions.
- [Source: spring-boot-autoconfigure trên GitHub](https://github.com/spring-projects/spring-boot/tree/main/spring-boot-project/spring-boot-autoconfigure)
  Đọc code auto-config thật. Use for: kiểm chứng điều kiện của một auto-config cụ thể.
- [Vlad Mihalcea — blog về JPA/Hibernate](https://vladmihalcea.com/blog/)
  Chuyên gia Hibernate. Use for: N+1, fetching, transaction, hiệu năng DB.
- [Spring Academy](https://spring.academy/courses)
  Khoá học chính thức của team Spring (miễn phí). Use for: lộ trình có cấu trúc, lab.

## Wisdom (Communities)

- [Stack Overflow — tag spring-boot](https://stackoverflow.com/questions/tagged/spring-boot)
  Use for: lỗi cụ thể, có thể tìm câu trả lời của chính các maintainer.
- [r/SpringBoot](https://www.reddit.com/r/SpringBoot/) và [r/java](https://www.reddit.com/r/java/)
  Use for: hỏi về thiết kế, review cách làm, kinh nghiệm thực tế.
- [GitHub Issues của spring-boot](https://github.com/spring-projects/spring-boot/issues)
  Use for: hành vi lạ nghi là bug, đọc lý do thiết kế từ maintainer.

## Gaps
- Chưa có cộng đồng Spring Boot tiếng Việt uy tín đã kiểm chứng.

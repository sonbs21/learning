# 10.4. Hỗ trợ Apache Pulsar (Apache Pulsar Support)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#messaging.pulsar>

[Apache Pulsar](https://pulsar.apache.org/) được hỗ trợ bằng cách cung cấp auto-configuration cho project [Spring for Apache Pulsar](https://docs.spring.io/spring-pulsar/docs/1.0.5/reference/).

Spring Boot sẽ tự động cấu hình và đăng ký các component Spring for Apache Pulsar cổ điển (imperative) khi `org.springframework.pulsar:spring-pulsar` có trên classpath. Nó sẽ làm tương tự cho các component reactive khi `org.springframework.pulsar:spring-pulsar-reactive` có trên classpath.

Có các "Starter" `spring-boot-starter-pulsar` và `spring-boot-starter-pulsar-reactive` để tiện lợi tập hợp các dependency lần lượt cho cách dùng imperative và reactive.

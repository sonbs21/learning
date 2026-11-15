# 7.9.1. Các dependency trong scope test (Test Scope Dependencies)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.testing.test-scope-dependencies>

"Starter" `spring-boot-starter-test` (trong `scope` `test`) chứa các thư viện được cung cấp sau:

- [JUnit 5](https://junit.org/junit5/): Tiêu chuẩn trên thực tế (de-facto) cho unit test ứng dụng Java.
- [Spring Test](https://docs.spring.io/spring-framework/reference/6.1/testing/integration.html) & Spring Boot Test: Các tiện ích và hỗ trợ integration test cho ứng dụng Spring Boot.
- [AssertJ](https://assertj.github.io/doc/): Một thư viện assertion kiểu fluent.
- [Hamcrest](https://github.com/hamcrest/JavaHamcrest): Một thư viện các object matcher (còn gọi là constraint hoặc predicate).
- [Mockito](https://site.mockito.org/): Một framework mock cho Java.
- [JSONassert](https://github.com/skyscreamer/JSONassert): Một thư viện assertion cho JSON.
- [JsonPath](https://github.com/jayway/JsonPath): XPath cho JSON.
- [Awaitility](https://github.com/awaitility/awaitility): Một thư viện để kiểm thử các hệ thống bất đồng bộ.

Chúng tôi thường thấy các thư viện phổ biến này hữu ích khi viết test. Nếu các thư viện này không phù hợp với nhu cầu của bạn, bạn có thể tự thêm các dependency test bổ sung.

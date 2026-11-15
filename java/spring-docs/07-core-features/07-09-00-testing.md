# 7.9. Kiểm thử (Testing)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.testing>

Spring Boot cung cấp một số tiện ích và annotation để hỗ trợ việc kiểm thử ứng dụng. Hỗ trợ kiểm thử được cung cấp bởi hai module: `spring-boot-test` chứa các thành phần cốt lõi, và `spring-boot-test-autoconfigure` hỗ trợ auto-configuration cho test.

Hầu hết các lập trình viên dùng "Starter" `spring-boot-starter-test`, starter này import cả hai module test của Spring Boot cũng như JUnit Jupiter, AssertJ, Hamcrest, và một số thư viện hữu ích khác.

> **MẸO (TIP):** Nếu bạn có các test dùng JUnit 4, vintage engine của JUnit 5 có thể được dùng để chạy chúng. Để dùng vintage engine, hãy thêm dependency `junit-vintage-engine`, như trong ví dụ sau:

```xml
<dependency>
    <groupId>org.junit.vintage</groupId>
    <artifactId>junit-vintage-engine</artifactId>
    <scope>test</scope>
    <exclusions>
        <exclusion>
            <groupId>org.hamcrest</groupId>
            <artifactId>hamcrest-core</artifactId>
        </exclusion>
    </exclusions>
</dependency>
```

> `hamcrest-core` bị loại trừ để ưu tiên `org.hamcrest:hamcrest`, vốn là một phần của `spring-boot-starter-test`.

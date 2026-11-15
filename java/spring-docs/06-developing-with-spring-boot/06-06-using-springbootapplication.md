# 6.6. Sử dụng annotation @SpringBootApplication (Using the @SpringBootApplication Annotation)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#using.using-the-springbootapplication-annotation>

Nhiều lập trình viên Spring Boot muốn ứng dụng của họ dùng auto-configuration, component scan và có thể định nghĩa cấu hình bổ sung trên "class ứng dụng" ("application class") của họ. Một annotation `@SpringBootApplication` duy nhất có thể được dùng để bật cả ba tính năng đó, cụ thể là:

- `@EnableAutoConfiguration`: bật [cơ chế auto-configuration của Spring Boot](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#using.auto-configuration)
- `@ComponentScan`: bật quét `@Component` trên package chứa ứng dụng (xem [các best practice](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#using.structuring-your-code))
- `@SpringBootConfiguration`: cho phép đăng ký thêm bean vào context hoặc import thêm các class cấu hình. Đây là một lựa chọn thay thế cho `@Configuration` tiêu chuẩn của Spring, giúp hỗ trợ việc [phát hiện cấu hình (configuration detection)](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.testing.spring-boot-applications.detecting-configuration) trong các integration test của bạn.

**Java**

```java
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// Same as @SpringBootConfiguration @EnableAutoConfiguration @ComponentScan
@SpringBootApplication
public class MyApplication {

    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }

}
```

**Kotlin**

```kotlin
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

// same as @SpringBootConfiguration @EnableAutoConfiguration @ComponentScan
@SpringBootApplication
class MyApplication

fun main(args: Array<String>) {
    runApplication<MyApplication>(*args)
}
```

> **LƯU Ý (NOTE):** `@SpringBootApplication` cũng cung cấp các alias để tùy chỉnh các thuộc tính của `@EnableAutoConfiguration` và `@ComponentScan`.

> **LƯU Ý (NOTE):** Không tính năng nào trong số này là bắt buộc, và bạn có thể chọn thay thế annotation duy nhất này bằng bất kỳ tính năng nào mà nó bật. Ví dụ, có thể bạn không muốn dùng component scan hoặc configuration properties scan trong ứng dụng của mình:

**Java**

```java
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Import;

@SpringBootConfiguration(proxyBeanMethods = false)
@EnableAutoConfiguration
@Import({ SomeConfiguration.class, AnotherConfiguration.class })
public class MyApplication {

    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }

}
```

**Kotlin**

```kotlin
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.docs.using.structuringyourcode.locatingthemainclass.MyApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Import

@SpringBootConfiguration(proxyBeanMethods = false)
@EnableAutoConfiguration
@Import(SomeConfiguration::class, AnotherConfiguration::class)
class MyApplication

fun main(args: Array<String>) {
    runApplication<MyApplication>(*args)
}
```

Trong ví dụ này, `MyApplication` cũng giống như bất kỳ ứng dụng Spring Boot nào khác, ngoại trừ việc các class được đánh annotation `@Component` và các class được đánh annotation `@ConfigurationProperties` sẽ không được phát hiện tự động, và các bean do người dùng định nghĩa được import một cách tường minh (xem `@Import`).

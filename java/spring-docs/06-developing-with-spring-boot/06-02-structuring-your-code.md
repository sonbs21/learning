# 6.2. Tổ chức code (Structuring Your Code)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#using.structuring-your-code>

Spring Boot không yêu cầu bất kỳ cách bố trí code cụ thể nào để hoạt động. Tuy nhiên, có một số best practice hữu ích.

> **MẸO (TIP):** Nếu bạn muốn áp đặt một cấu trúc dựa trên domain, hãy xem [Spring Modulith](https://spring.io/projects/spring-modulith#overview).

## 6.2.1. Sử dụng package "mặc định" (Using the "default" Package)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#using.structuring-your-code.using-the-default-package>

Khi một class không có khai báo `package`, nó được coi là nằm trong "package mặc định" ("default package"). Việc dùng "package mặc định" thường không được khuyến khích và nên tránh. Nó có thể gây ra các vấn đề đặc biệt cho ứng dụng Spring Boot sử dụng các annotation `@ComponentScan`, `@ConfigurationPropertiesScan`, `@EntityScan`, hoặc `@SpringBootApplication`, vì mọi class từ mọi jar đều bị đọc.

> **MẸO (TIP):** Chúng tôi khuyên bạn nên tuân theo quy ước đặt tên package được Java khuyến nghị và dùng tên miền đảo ngược (ví dụ: `com.example.project`).

## 6.2.2. Vị trí đặt class ứng dụng chính (Locating the Main Application Class)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#using.structuring-your-code.locating-the-main-class>

Chúng tôi thường khuyên bạn đặt class ứng dụng chính (main application class) trong một package gốc (root package), nằm trên các class khác. [Annotation `@SpringBootApplication`](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#using.using-the-springbootapplication-annotation) thường được đặt trên class chính, và nó ngầm định nghĩa một "package tìm kiếm" ("search package") cơ sở cho một số thành phần nhất định. Ví dụ, nếu bạn đang viết một ứng dụng JPA, package của class được đánh annotation `@SpringBootApplication` sẽ được dùng để tìm kiếm các `@Entity`. Việc dùng package gốc cũng cho phép component scan chỉ áp dụng trên project của bạn.

> **MẸO (TIP):** Nếu bạn không muốn dùng `@SpringBootApplication`, các annotation `@EnableAutoConfiguration` và `@ComponentScan` mà nó import sẽ định nghĩa hành vi đó, nên bạn cũng có thể dùng chúng thay thế.

Đoạn sau đây cho thấy một cách bố trí điển hình:

```
com
 +- example
     +- myapplication
         +- MyApplication.java
         |
         +- customer
         |   +- Customer.java
         |   +- CustomerController.java
         |   +- CustomerService.java
         |   +- CustomerRepository.java
         |
         +- order
             +- Order.java
             +- OrderController.java
             +- OrderService.java
             +- OrderRepository.java
```

File `MyApplication.java` sẽ khai báo phương thức `main`, cùng với `@SpringBootApplication` cơ bản, như sau:

**Java**

```java
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

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

@SpringBootApplication
class MyApplication

fun main(args: Array<String>) {
    runApplication<MyApplication>(*args)
}
```

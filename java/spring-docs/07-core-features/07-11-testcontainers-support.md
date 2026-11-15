# 7.11. Hỗ trợ Testcontainers (Testcontainers Support)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.testcontainers>

Ngoài việc [dùng Testcontainers cho integration test](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.testing.testcontainers), cũng có thể dùng chúng trong thời gian phát triển. Các phần tiếp theo sẽ cung cấp thêm chi tiết về điều đó.

## 7.11.1. Sử dụng Testcontainers trong thời gian phát triển (Using Testcontainers at Development Time)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.testcontainers.at-development-time>

Cách tiếp cận này cho phép lập trình viên nhanh chóng khởi động container cho các service mà ứng dụng phụ thuộc vào, loại bỏ nhu cầu phải tự cấp phát các thứ như database server. Dùng Testcontainers theo cách này cung cấp chức năng tương tự như Docker Compose, ngoại trừ việc cấu hình container của bạn được viết bằng Java thay vì YAML.

Để dùng Testcontainers trong thời gian phát triển, bạn cần khởi chạy ứng dụng bằng classpath "test" thay vì "main". Điều này sẽ cho phép bạn truy cập tất cả các test dependency đã khai báo và cho bạn một nơi tự nhiên để viết cấu hình test.

Để tạo một phiên bản có thể khởi chạy từ test của ứng dụng, bạn nên tạo một class "Application" trong thư mục `src/test`. Ví dụ, nếu ứng dụng chính của bạn nằm ở `src/main/java/com/example/MyApplication.java`, bạn nên tạo `src/test/java/com/example/TestMyApplication.java`.

Class `TestMyApplication` có thể dùng phương thức `SpringApplication.from(…)` để khởi chạy ứng dụng thực:

**Java**

```java
import org.springframework.boot.SpringApplication;

public class TestMyApplication {

    public static void main(String[] args) {
        SpringApplication.from(MyApplication::main).run(args);
    }

}
```

**Kotlin**

```kotlin
import org.springframework.boot.fromApplication

fun main(args: Array<String>) {
    fromApplication<MyApplication>().run(*args)
}
```

Bạn cũng sẽ cần định nghĩa các instance `Container` mà bạn muốn khởi động cùng với ứng dụng. Để làm vậy, bạn cần đảm bảo module `spring-boot-testcontainers` đã được thêm làm dependency `test`. Sau khi đã làm xong, bạn có thể tạo một class `@TestConfiguration` khai báo các phương thức `@Bean` cho các container bạn muốn khởi động.

Bạn cũng có thể đánh annotation `@ServiceConnection` lên các phương thức `@Bean` để tạo các bean `ConnectionDetails`. Xem phần [service connection](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.testing.testcontainers.service-connections) để biết chi tiết về các công nghệ được hỗ trợ.

Một cấu hình Testcontainers điển hình sẽ trông như thế này:

**Java**

```java
import org.testcontainers.containers.Neo4jContainer;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;

@TestConfiguration(proxyBeanMethods = false)
public class MyContainersConfiguration {

    @Bean
    @ServiceConnection
    public Neo4jContainer<?> neo4jContainer() {
        return new Neo4jContainer<>("neo4j:5");
    }

}
```

**Kotlin**

```kotlin
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.containers.Neo4jContainer

@TestConfiguration(proxyBeanMethods = false)
class MyContainersConfiguration {

    @Bean
    @ServiceConnection
    fun neo4jContainer(): Neo4jContainer<*> {
        return Neo4jContainer("neo4j:5")
    }

}
```

> **LƯU Ý (NOTE):** Vòng đời của các bean `Container` được Spring Boot tự động quản lý. Các container sẽ được khởi động và dừng tự động.

> **MẸO (TIP):** Bạn có thể dùng property `spring.testcontainers.beans.startup` để thay đổi cách các container được khởi động. Theo mặc định, cách khởi động `sequential` (tuần tự) được sử dụng, nhưng bạn cũng có thể chọn `parallel` nếu muốn khởi động nhiều container song song.

Sau khi đã định nghĩa cấu hình test, bạn có thể dùng phương thức `with(…)` để gắn nó vào launcher test:

**Java**

```java
import org.springframework.boot.SpringApplication;

public class TestMyApplication {

    public static void main(String[] args) {
        SpringApplication.from(MyApplication::main).with(MyContainersConfiguration.class).run(args);
    }

}
```

**Kotlin**

```kotlin
import org.springframework.boot.fromApplication
import org.springframework.boot.with

fun main(args: Array<String>) {
    fromApplication<MyApplication>().with(MyContainersConfiguration::class).run(*args)
}
```

Giờ đây, bạn có thể khởi chạy `TestMyApplication` giống như bất kỳ ứng dụng Java có phương thức `main` thông thường nào để khởi động ứng dụng và các container mà nó cần để chạy.

> **MẸO (TIP):** Bạn có thể dùng goal Maven `spring-boot:test-run` hoặc task Gradle `bootTestRun` để làm điều này từ dòng lệnh.

### Cung cấp property động trong thời gian phát triển (Contributing Dynamic Properties at Development Time)

Nếu bạn muốn cung cấp các property động trong thời gian phát triển từ các phương thức `@Bean` `Container`, bạn có thể làm vậy bằng cách inject một `DynamicPropertyRegistry`. Cách này hoạt động tương tự như [annotation `@DynamicPropertySource`](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.testing.testcontainers.dynamic-properties) mà bạn có thể dùng trong test. Nó cho phép bạn thêm các property sẽ trở nên khả dụng sau khi container của bạn đã khởi động.

Một cấu hình điển hình sẽ trông như thế này:

**Java**

```java
import org.testcontainers.containers.MongoDBContainer;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;

@TestConfiguration(proxyBeanMethods = false)
public class MyContainersConfiguration {

    @Bean
    public MongoDBContainer mongoDbContainer(DynamicPropertyRegistry properties) {
        MongoDBContainer container = new MongoDBContainer("mongo:5.0");
        properties.add("spring.data.mongodb.host", container::getHost);
        properties.add("spring.data.mongodb.port", container::getFirstMappedPort);
        return container;
    }

}
```

**Kotlin**

```kotlin
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.test.context.DynamicPropertyRegistry
import org.testcontainers.containers.MongoDBContainer

@TestConfiguration(proxyBeanMethods = false)
class MyContainersConfiguration {

    @Bean
    fun monogDbContainer(properties: DynamicPropertyRegistry): MongoDBContainer {
        var container = MongoDBContainer("mongo:5.0")
        properties.add("spring.data.mongodb.host", container::getHost)
        properties.add("spring.data.mongodb.port", container::getFirstMappedPort)
        return container
    }

}
```

> **LƯU Ý (NOTE):** Khuyến nghị dùng `@ServiceConnection` bất cứ khi nào có thể, tuy nhiên, property động có thể là một phương án dự phòng hữu ích cho các công nghệ chưa có hỗ trợ `@ServiceConnection`.

### Import các class khai báo Testcontainer (Importing Testcontainer Declaration Classes)

Một mẫu phổ biến khi dùng Testcontainers là khai báo các instance `Container` dưới dạng field static. Thường thì các field này được định nghĩa trực tiếp trên class test. Chúng cũng có thể được khai báo trên một class cha hoặc trên một interface mà test hiện thực.

Ví dụ, interface `MyContainers` sau khai báo các container `mongo` và `neo4j`:

```java
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.junit.jupiter.Container;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;

public interface MyContainers {

    @Container
    @ServiceConnection
    MongoDBContainer mongoContainer = new MongoDBContainer("mongo:5.0");

    @Container
    @ServiceConnection
    Neo4jContainer<?> neo4jContainer = new Neo4jContainer<>("neo4j:5");

}
```

Nếu bạn đã có các container được định nghĩa theo cách này, hoặc bạn chỉ đơn giản thích kiểu này hơn, bạn có thể import các class khai báo này thay vì định nghĩa container dưới dạng các phương thức `@Bean`. Để làm vậy, hãy thêm annotation `@ImportTestcontainers` vào class cấu hình test:

**Java**

```java
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;

@TestConfiguration(proxyBeanMethods = false)
@ImportTestcontainers(MyContainers.class)
public class MyContainersConfiguration {

}
```

**Kotlin**

```kotlin
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.context.ImportTestcontainers

@TestConfiguration(proxyBeanMethods = false)
@ImportTestcontainers(MyContainers::class)
class MyContainersConfiguration
```

> **MẸO (TIP):** Nếu bạn không định dùng [tính năng service connection](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.testing.testcontainers.service-connections) mà muốn dùng [`@DynamicPropertySource`](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.testing.testcontainers.dynamic-properties) thay thế, hãy xóa annotation `@ServiceConnection` khỏi các field `Container`. Bạn cũng có thể thêm các phương thức được đánh annotation `@DynamicPropertySource` vào class khai báo.

### Sử dụng DevTools với Testcontainers trong thời gian phát triển (Using DevTools with Testcontainers at Development Time)

Khi dùng devtools, bạn có thể đánh annotation `@RestartScope` lên các bean và phương thức bean. Những bean như vậy sẽ không được tạo lại khi devtools restart ứng dụng. Điều này đặc biệt hữu ích cho các bean `Container` của Testcontainer, vì chúng giữ nguyên trạng thái bất chấp việc ứng dụng restart.

**Java**

```java
import org.testcontainers.containers.MongoDBContainer;

import org.springframework.boot.devtools.restart.RestartScope;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;

@TestConfiguration(proxyBeanMethods = false)
public class MyContainersConfiguration {

    @Bean
    @RestartScope
    @ServiceConnection
    public MongoDBContainer mongoDbContainer() {
        return new MongoDBContainer("mongo:5.0");
    }

}
```

**Kotlin**

```kotlin
import org.springframework.boot.devtools.restart.RestartScope
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.containers.MongoDBContainer

@TestConfiguration(proxyBeanMethods = false)
class MyContainersConfiguration {

    @Bean
    @RestartScope
    @ServiceConnection
    fun monogDbContainer(): MongoDBContainer {
        return MongoDBContainer("mongo:5.0")
    }

}
```

> **CẢNH BÁO (WARNING):** Nếu bạn đang dùng Gradle và muốn dùng tính năng này, bạn cần thay đổi cấu hình của dependency `spring-boot-devtools` từ `developmentOnly` sang `testAndDevelopmentOnly`. Với scope mặc định `developmentOnly`, task `bootTestRun` sẽ không nhận các thay đổi trong code của bạn, vì devtools không hoạt động.

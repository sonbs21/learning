# 7.9.4. Testcontainers

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.testing.testcontainers>

Thư viện [Testcontainers](https://www.testcontainers.org/) cung cấp một cách để quản lý các service chạy bên trong Docker container. Nó tích hợp với JUnit, cho phép bạn viết một class test có thể khởi động một container trước khi bất kỳ test nào chạy. Testcontainers đặc biệt hữu ích để viết các integration test giao tiếp với một backend service thực như MySQL, MongoDB, Cassandra và các loại khác.

Testcontainers có thể được dùng trong một test Spring Boot như sau:

**Java**

```java
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.springframework.boot.test.context.SpringBootTest;

@Testcontainers
@SpringBootTest
class MyIntegrationTests {

    @Container
    static Neo4jContainer<?> neo4j = new Neo4jContainer<>("neo4j:5");

    @Test
    void myTest() {
        // ...
    }

}
```

**Kotlin**

```kotlin
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.testcontainers.containers.Neo4jContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
@SpringBootTest
class MyIntegrationTests {

    @Test
    fun myTest() {
        // ...
    }

    companion object {
        @Container
        val neo4j = Neo4jContainer("neo4j:5")
    }

}
```

Việc này sẽ khởi động một docker container chạy Neo4j (nếu Docker đang chạy ở máy cục bộ) trước khi bất kỳ test nào được chạy. Trong hầu hết các trường hợp, bạn sẽ cần cấu hình ứng dụng để kết nối tới service đang chạy trong container.

## Service Connection (Service Connections)

Một service connection là một kết nối tới bất kỳ remote service nào. Auto-configuration của Spring Boot có thể sử dụng các chi tiết của một service connection và dùng chúng để thiết lập kết nối tới một remote service. Khi làm vậy, các chi tiết kết nối được ưu tiên hơn mọi configuration property liên quan đến kết nối.

Khi dùng Testcontainers, các chi tiết kết nối có thể được tự động tạo cho một service chạy trong container bằng cách đánh annotation lên field container trong class test.

**Java**

```java
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;

@Testcontainers
@SpringBootTest
class MyIntegrationTests {

    @Container
    @ServiceConnection
    static Neo4jContainer<?> neo4j = new Neo4jContainer<>("neo4j:5");

    @Test
    void myTest() {
        // ...
    }

}
```

**Kotlin**

```kotlin
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.testcontainers.containers.Neo4jContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
@SpringBootTest
class MyIntegrationTests {

    @Test
    fun myTest() {
        // ...
    }

    companion object {

        @Container
        @ServiceConnection
        val neo4j = Neo4jContainer("neo4j:5")

    }

}
```

Nhờ `@ServiceConnection`, cấu hình trên cho phép các bean liên quan đến Neo4j trong ứng dụng giao tiếp với Neo4j đang chạy bên trong Docker container do Testcontainers quản lý. Điều này được thực hiện bằng cách tự động định nghĩa một bean `Neo4jConnectionDetails`, sau đó được auto-configuration của Neo4j sử dụng, ghi đè mọi configuration property liên quan đến kết nối.

> **LƯU Ý (NOTE):** Bạn sẽ cần thêm module `spring-boot-testcontainers` làm test dependency để dùng service connection với Testcontainers.

Các annotation service connection được xử lý bởi các class `ContainerConnectionDetailsFactory` được đăng ký với `spring.factories`. Một `ContainerConnectionDetailsFactory` có thể tạo một bean `ConnectionDetails` dựa trên một subclass `Container` cụ thể, hoặc tên Docker image.

Các service connection factory sau được cung cấp trong jar `spring-boot-testcontainers`:

| Connection Details | Khớp với |
|---|---|
| `ActiveMQConnectionDetails` | Container có tên "symptoma/activemq" |
| `CassandraConnectionDetails` | Container kiểu `CassandraContainer` |
| `CouchbaseConnectionDetails` | Container kiểu `CouchbaseContainer` |
| `ElasticsearchConnectionDetails` | Container kiểu `ElasticsearchContainer` |
| `FlywayConnectionDetails` | Container kiểu `JdbcDatabaseContainer` |
| `JdbcConnectionDetails` | Container kiểu `JdbcDatabaseContainer` |
| `KafkaConnectionDetails` | Container kiểu `KafkaContainer` hoặc `RedpandaContainer` |
| `LiquibaseConnectionDetails` | Container kiểu `JdbcDatabaseContainer` |
| `MongoConnectionDetails` | Container kiểu `MongoDBContainer` |
| `Neo4jConnectionDetails` | Container kiểu `Neo4jContainer` |
| `OtlpMetricsConnectionDetails` | Container có tên "otel/opentelemetry-collector-contrib" |
| `OtlpTracingConnectionDetails` | Container có tên "otel/opentelemetry-collector-contrib" |
| `PulsarConnectionDetails` | Container kiểu `PulsarContainer` |
| `R2dbcConnectionDetails` | Container kiểu `MariaDBContainer`, `MSSQLServerContainer`, `MySQLContainer`, `OracleContainer`, hoặc `PostgreSQLContainer` |
| `RabbitConnectionDetails` | Container kiểu `RabbitMQContainer` |
| `RedisConnectionDetails` | Container có tên "redis" |
| `ZipkinConnectionDetails` | Container có tên "openzipkin/zipkin" |

> **MẸO (TIP):** Theo mặc định, tất cả các bean connection details phù hợp sẽ được tạo cho một `Container` nhất định. Ví dụ, một `PostgreSQLContainer` sẽ tạo cả `JdbcConnectionDetails` và `R2dbcConnectionDetails`.
>
> Nếu bạn chỉ muốn tạo một tập con các kiểu phù hợp, bạn có thể dùng thuộc tính `type` của `@ServiceConnection`.

Theo mặc định, `Container.getDockerImageName()` được dùng để lấy tên dùng để tìm connection details. Điều này hoạt động miễn là Spring Boot có thể lấy được instance của `Container`, đó là trường hợp khi dùng một field `static` như trong ví dụ trên.

Nếu bạn đang dùng một phương thức `@Bean`, Spring Boot sẽ không gọi phương thức bean để lấy tên Docker image, vì điều này sẽ gây ra các vấn đề khởi tạo sớm (eager initialization). Thay vào đó, kiểu trả về của phương thức bean được dùng để tìm ra connection detail nào nên được sử dụng. Điều này hoạt động miễn là bạn đang dùng các container có kiểu cụ thể (typed container), ví dụ `Neo4jContainer` hoặc `RabbitMQContainer`. Điều này ngừng hoạt động nếu bạn đang dùng `GenericContainer`, ví dụ với Redis, như trong ví dụ sau:

**Java**

```java
import org.testcontainers.containers.GenericContainer;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;

@TestConfiguration(proxyBeanMethods = false)
public class MyRedisConfiguration {

    @Bean
    @ServiceConnection(name = "redis")
    public GenericContainer<?> redisContainer() {
        return new GenericContainer<>("redis:7");
    }

}
```

**Kotlin**

```kotlin
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.containers.GenericContainer

@TestConfiguration(proxyBeanMethods = false)
class MyRedisConfiguration {

    @Bean
    @ServiceConnection(name = "redis")
    fun redisContainer(): GenericContainer<*> {
        return GenericContainer("redis:7")
    }

}
```

Spring Boot không thể biết từ `GenericContainer` image container nào được sử dụng, vì vậy thuộc tính `name` từ `@ServiceConnection` phải được dùng để cung cấp gợi ý đó.

Bạn cũng có thể dùng thuộc tính `name` của `@ServiceConnection` để ghi đè connection detail nào sẽ được dùng, ví dụ khi dùng các image tùy chỉnh. Nếu bạn đang dùng Docker image `registry.mycompany.com/mirror/myredis`, bạn sẽ dùng `@ServiceConnection(name="redis")` để đảm bảo `RedisConnectionDetails` được tạo.

## Property động (Dynamic Properties)

Một lựa chọn thay thế dài dòng hơn một chút nhưng cũng linh hoạt hơn cho service connection là `@DynamicPropertySource`. Một phương thức static `@DynamicPropertySource` cho phép thêm các giá trị property động vào Spring Environment.

**Java**

```java
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@Testcontainers
@SpringBootTest
class MyIntegrationTests {

    @Container
    static Neo4jContainer<?> neo4j = new Neo4jContainer<>("neo4j:5");

    @Test
    void myTest() {
        // ...
    }

    @DynamicPropertySource
    static void neo4jProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.neo4j.uri", neo4j::getBoltUrl);
    }

}
```

**Kotlin**

```kotlin
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.Neo4jContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
@SpringBootTest
class MyIntegrationTests {

    @Test
    fun myTest() {
        // ...
    }

    companion object {

        @Container
        val neo4j = Neo4jContainer("neo4j:5")

        @DynamicPropertySource
        fun neo4jProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.neo4j.uri") { neo4j.boltUrl }
        }

    }

}
```

Cấu hình trên cho phép các bean liên quan đến Neo4j trong ứng dụng giao tiếp với Neo4j đang chạy bên trong Docker container do Testcontainers quản lý.

# 9.1.8. Sử dụng R2DBC (Using R2DBC)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#data.sql.r2dbc>

Project Reactive Relational Database Connectivity ([R2DBC](https://r2dbc.io)) mang các API lập trình reactive tới cơ sở dữ liệu quan hệ. `io.r2dbc.spi.Connection` của R2DBC cung cấp một phương thức tiêu chuẩn để làm việc với các kết nối database không chặn (non-blocking). Các kết nối được cung cấp bằng một `ConnectionFactory`, tương tự như một `DataSource` với jdbc.

Cấu hình `ConnectionFactory` được kiểm soát bởi các configuration property bên ngoài trong `spring.r2dbc.*`. Ví dụ, bạn có thể khai báo phần sau trong `application.properties`:

**Properties**

```properties
spring.r2dbc.url=r2dbc:postgresql://localhost/test
spring.r2dbc.username=dbuser
spring.r2dbc.password=dbpass
```

**Yaml**

```yaml
spring:
  r2dbc:
    url: "r2dbc:postgresql://localhost/test"
    username: "dbuser"
    password: "dbpass"
```

> **MẸO (TIP):** Bạn không cần chỉ định tên class driver, vì Spring Boot lấy driver từ cơ chế phát hiện Connection Factory của R2DBC.

> **LƯU Ý (NOTE):** Tối thiểu cần cung cấp url. Thông tin được chỉ định trong URL được ưu tiên hơn các property riêng lẻ, tức là `name`, `username`, `password` và các tùy chọn pooling.

> **MẸO (TIP):** Phần "How-to" có [một phần về cách khởi tạo database](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.data-initialization.using-basic-sql-scripts).

Để tùy chỉnh các kết nối được tạo bởi một `ConnectionFactory`, tức là đặt các tham số cụ thể mà bạn không muốn (hoặc không thể) cấu hình trong cấu hình database trung tâm, bạn có thể dùng một `@Bean` `ConnectionFactoryOptionsBuilderCustomizer`. Ví dụ sau cho thấy cách ghi đè thủ công cổng database trong khi các tùy chọn còn lại được lấy từ cấu hình ứng dụng:

**Java**

```java
import io.r2dbc.spi.ConnectionFactoryOptions;

import org.springframework.boot.autoconfigure.r2dbc.ConnectionFactoryOptionsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class MyR2dbcConfiguration {

    @Bean
    public ConnectionFactoryOptionsBuilderCustomizer connectionFactoryPortCustomizer() {
        return (builder) -> builder.option(ConnectionFactoryOptions.PORT, 5432);
    }

}
```

**Kotlin**

```kotlin
import io.r2dbc.spi.ConnectionFactoryOptions
import org.springframework.boot.autoconfigure.r2dbc.ConnectionFactoryOptionsBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class MyR2dbcConfiguration {

    @Bean
    fun connectionFactoryPortCustomizer(): ConnectionFactoryOptionsBuilderCustomizer {
        return ConnectionFactoryOptionsBuilderCustomizer { builder ->
            builder.option(ConnectionFactoryOptions.PORT, 5432)
        }
    }

}
```

Các ví dụ sau cho thấy cách đặt một số tùy chọn kết nối PostgreSQL:

**Java**

```java
import java.util.HashMap;
import java.util.Map;

import io.r2dbc.postgresql.PostgresqlConnectionFactoryProvider;

import org.springframework.boot.autoconfigure.r2dbc.ConnectionFactoryOptionsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class MyPostgresR2dbcConfiguration {

    @Bean
    public ConnectionFactoryOptionsBuilderCustomizer postgresCustomizer() {
        Map<String, String> options = new HashMap<>();
        options.put("lock_timeout", "30s");
        options.put("statement_timeout", "60s");
        return (builder) -> builder.option(PostgresqlConnectionFactoryProvider.OPTIONS, options);
    }

}
```

**Kotlin**

```kotlin
import io.r2dbc.postgresql.PostgresqlConnectionFactoryProvider
import org.springframework.boot.autoconfigure.r2dbc.ConnectionFactoryOptionsBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class MyPostgresR2dbcConfiguration {

    @Bean
    fun postgresCustomizer(): ConnectionFactoryOptionsBuilderCustomizer {
        val options: MutableMap<String, String> = HashMap()
        options["lock_timeout"] = "30s"
        options["statement_timeout"] = "60s"
        return ConnectionFactoryOptionsBuilderCustomizer { builder ->
            builder.option(PostgresqlConnectionFactoryProvider.OPTIONS, options)
        }
    }

}
```

Khi có bean `ConnectionFactory`, auto-configuration `DataSource` JDBC thông thường sẽ lùi lại. Nếu bạn muốn giữ auto-configuration `DataSource` JDBC, và chấp nhận rủi ro khi dùng JDBC API dạng blocking trong một ứng dụng reactive, hãy thêm `@Import(DataSourceAutoConfiguration.class)` trên một class `@Configuration` trong ứng dụng để bật lại nó.

## Hỗ trợ database nhúng (Embedded Database Support)

Tương tự như [hỗ trợ JDBC](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#data.sql.datasource.embedded), Spring Boot có thể tự động cấu hình một database nhúng để dùng theo kiểu reactive. Bạn không cần cung cấp URL kết nối nào. Bạn chỉ cần thêm dependency build tới database nhúng mà bạn muốn dùng, như trong ví dụ sau:

```xml
<dependency>
    <groupId>io.r2dbc</groupId>
    <artifactId>r2dbc-h2</artifactId>
    <scope>runtime</scope>
</dependency>
```

> **LƯU Ý (NOTE):** Nếu bạn đang dùng tính năng này trong test, bạn có thể nhận thấy cùng một database được tái sử dụng bởi toàn bộ test suite bất kể số lượng application context mà bạn dùng. Nếu bạn muốn đảm bảo mỗi context có một database nhúng riêng, bạn nên đặt `spring.r2dbc.generate-unique-name` thành `true`.

## Sử dụng DatabaseClient (Using DatabaseClient)

Một bean `DatabaseClient` được tự động cấu hình, và bạn có thể `@Autowire` nó trực tiếp vào các bean của riêng mình, như trong ví dụ sau:

**Java**

```java
import java.util.Map;

import reactor.core.publisher.Flux;

import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;

@Component
public class MyBean {

    private final DatabaseClient databaseClient;

    public MyBean(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    // ...

    public Flux<Map<String, Object>> someMethod() {
        return this.databaseClient.sql("select * from user").fetch().all();
    }

}
```

**Kotlin**

```kotlin
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux

@Component
class MyBean(private val databaseClient: DatabaseClient) {

    // ...

    fun someMethod(): Flux<Map<String, Any>> {
        return databaseClient.sql("select * from user").fetch().all()
    }

}
```

## Repository Spring Data R2DBC (Spring Data R2DBC Repositories)

Các repository [Spring Data R2DBC](https://spring.io/projects/spring-data-r2dbc) là các interface mà bạn có thể định nghĩa để truy cập dữ liệu. Các query được tạo tự động từ tên phương thức của bạn. Ví dụ, một interface `CityRepository` có thể khai báo phương thức `findAllByState(String state)` để tìm tất cả các thành phố trong một bang nhất định.

Với các query phức tạp hơn, bạn có thể đánh annotation [`Query`](https://docs.spring.io/spring-data/r2dbc/docs/3.2.5/api/org/springframework/data/r2dbc/repository/Query.html) của Spring Data lên phương thức.

Các repository Spring Data thường kế thừa từ interface [`Repository`](https://docs.spring.io/spring-data/commons/docs/3.2.5/api/org/springframework/data/repository/Repository.html) hoặc [`CrudRepository`](https://docs.spring.io/spring-data/commons/docs/3.2.5/api/org/springframework/data/repository/CrudRepository.html). Nếu bạn dùng auto-configuration, các [package auto-configuration](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#using.auto-configuration.packages) sẽ được tìm kiếm để tìm repository.

Ví dụ sau cho thấy một định nghĩa interface repository Spring Data điển hình:

**Java**

```java
import reactor.core.publisher.Mono;

import org.springframework.data.repository.Repository;

public interface CityRepository extends Repository<City, Long> {

    Mono<City> findByNameAndStateAllIgnoringCase(String name, String state);

}
```

**Kotlin**

```kotlin
import org.springframework.data.repository.Repository
import reactor.core.publisher.Mono

interface CityRepository : Repository<City?, Long?> {

    fun findByNameAndStateAllIgnoringCase(name: String?, state: String?): Mono<City?>?

}
```

> **MẸO (TIP):** Chúng tôi mới chỉ chạm vào bề mặt của Spring Data R2DBC. Để biết đầy đủ chi tiết, xem [tài liệu tham khảo Spring Data R2DBC](https://docs.spring.io/spring-data/r2dbc/docs/3.2.5/reference/html/).

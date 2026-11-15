# 9.2.5. Cassandra

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#data.nosql.cassandra>

[Cassandra](https://cassandra.apache.org/) là một hệ quản trị cơ sở dữ liệu phân tán, mã nguồn mở, được thiết kế để xử lý lượng dữ liệu lớn trên nhiều server phổ thông. Spring Boot cung cấp auto-configuration cho Cassandra và các abstraction bên trên nó do [Spring Data Cassandra](https://github.com/spring-projects/spring-data-cassandra) cung cấp. Có một "Starter" `spring-boot-starter-data-cassandra` để tập hợp các dependency một cách tiện lợi.

## Kết nối tới Cassandra (Connecting to Cassandra)

Bạn có thể inject một instance `CassandraTemplate` hoặc `CqlSession` của Cassandra đã được tự động cấu hình giống như với bất kỳ Spring Bean nào khác. Các property `spring.cassandra.*` có thể được dùng để tùy chỉnh kết nối. Thông thường, bạn cung cấp `keyspace-name` và `contact-points` cũng như tên datacenter cục bộ, như trong ví dụ sau:

**Properties**

```properties
spring.cassandra.keyspace-name=mykeyspace
spring.cassandra.contact-points=cassandrahost1:9042,cassandrahost2:9042
spring.cassandra.local-datacenter=datacenter1
```

**Yaml**

```yaml
spring:
  cassandra:
    keyspace-name: "mykeyspace"
    contact-points: "cassandrahost1:9042,cassandrahost2:9042"
    local-datacenter: "datacenter1"
```

Nếu cổng là giống nhau cho tất cả các contact point, bạn có thể dùng cách viết tắt và chỉ chỉ định tên host, như trong ví dụ sau:

**Properties**

```properties
spring.cassandra.keyspace-name=mykeyspace
spring.cassandra.contact-points=cassandrahost1,cassandrahost2
spring.cassandra.local-datacenter=datacenter1
```

**Yaml**

```yaml
spring:
  cassandra:
    keyspace-name: "mykeyspace"
    contact-points: "cassandrahost1,cassandrahost2"
    local-datacenter: "datacenter1"
```

> **MẸO (TIP):** Hai ví dụ đó giống hệt nhau vì cổng mặc định là `9042`. Nếu bạn cần cấu hình cổng, hãy dùng `spring.cassandra.port`.

`CqlSession` được tự động cấu hình có thể được cấu hình để dùng SSL khi giao tiếp với server bằng cách đặt các property như trong ví dụ này:

**Properties**

```properties
spring.cassandra.keyspace-name=mykeyspace
spring.cassandra.contact-points=cassandrahost1,cassandrahost2
spring.cassandra.local-datacenter=datacenter1
spring.cassandra.ssl.enabled=true
```

**Yaml**

```yaml
spring:
  cassandra:
    keyspace-name: "mykeyspace"
    contact-points: "cassandrahost1,cassandrahost2"
    local-datacenter: "datacenter1"
    ssl:
      enabled: true
```

Tài liệu tin cậy SSL tùy chỉnh có thể được cấu hình trong một [SSL bundle](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.ssl) và được áp dụng cho `CqlSession` như trong ví dụ này:

**Properties**

```properties
spring.cassandra.keyspace-name=mykeyspace
spring.cassandra.contact-points=cassandrahost1,cassandrahost2
spring.cassandra.local-datacenter=datacenter1
spring.cassandra.ssl.bundle=example
```

**Yaml**

```yaml
spring:
  cassandra:
    keyspace-name: "mykeyspace"
    contact-points: "cassandrahost1,cassandrahost2"
    local-datacenter: "datacenter1"
    ssl:
      bundle: "example"
```

> **LƯU Ý (NOTE):** Driver Cassandra có hạ tầng cấu hình riêng, nạp một file `application.conf` ở thư mục gốc của classpath.
>
> Spring Boot không tìm file như vậy theo mặc định nhưng có thể nạp một file bằng `spring.cassandra.config`. Nếu một property có mặt ở cả `spring.cassandra.*` và file cấu hình, giá trị trong `spring.cassandra.*` được ưu tiên.
>
> Để tùy chỉnh driver nâng cao hơn, bạn có thể đăng ký một số lượng tùy ý các bean hiện thực `DriverConfigLoaderBuilderCustomizer`. `CqlSession` có thể được tùy chỉnh bằng một bean kiểu `CqlSessionBuilderCustomizer`.

> **LƯU Ý (NOTE):** Nếu bạn dùng `CqlSessionBuilder` để tạo nhiều bean `CqlSession`, hãy nhớ rằng builder có thể thay đổi (mutable), vì vậy hãy đảm bảo inject một bản sao mới cho mỗi session.

Đoạn code sau cho thấy cách inject một bean Cassandra:

**Java**

```java
import org.springframework.data.cassandra.core.CassandraTemplate;
import org.springframework.stereotype.Component;

@Component
public class MyBean {

    private final CassandraTemplate template;

    public MyBean(CassandraTemplate template) {
        this.template = template;
    }

    // ...

    public long someMethod() {
        return this.template.count(User.class);
    }

}
```

**Kotlin**

```kotlin
import org.springframework.data.cassandra.core.CassandraTemplate
import org.springframework.stereotype.Component

@Component
class MyBean(private val template: CassandraTemplate) {

    // ...

    fun someMethod(): Long {
        return template.count(User::class.java)
    }

}
```

Nếu bạn thêm `@Bean` kiểu `CassandraTemplate` của riêng mình, nó sẽ thay thế bean mặc định.

## Repository Spring Data Cassandra (Spring Data Cassandra Repositories)

Spring Data bao gồm hỗ trợ repository cơ bản cho Cassandra. Hiện tại, hỗ trợ này hạn chế hơn so với các repository JPA đã thảo luận trước đó và cần các phương thức finder được đánh annotation `@Query`.

Repository và entity được tìm thấy thông qua việc quét. Theo mặc định, các [package auto-configuration](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#using.auto-configuration.packages) được quét. Bạn có thể tùy chỉnh các vị trí tìm kiếm repository và entity bằng cách dùng lần lượt `@EnableCassandraRepositories` và `@EntityScan`.

> **MẸO (TIP):** Để biết đầy đủ chi tiết về Spring Data Cassandra, xem [tài liệu tham khảo](https://docs.spring.io/spring-data/cassandra/docs/).

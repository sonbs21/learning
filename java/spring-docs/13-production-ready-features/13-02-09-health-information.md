# 13.2.9. Thông tin health (Health Information)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#actuator.endpoints.health>

Bạn có thể dùng thông tin health để kiểm tra trạng thái của ứng dụng đang chạy. Nó thường được phần mềm giám sát dùng để cảnh báo ai đó khi một hệ thống production bị sập. Thông tin được endpoint `health` expose phụ thuộc vào các property `management.endpoint.health.show-details` và `management.endpoint.health.show-components`, có thể được cấu hình với một trong các giá trị sau:

| Tên | Mô tả |
|---|---|
| `never` | Chi tiết không bao giờ được hiển thị. |
| `when-authorized` | Chi tiết chỉ được hiển thị cho người dùng được phân quyền. Các role được phân quyền có thể được cấu hình bằng `management.endpoint.health.roles`. |
| `always` | Chi tiết được hiển thị cho tất cả người dùng. |

Giá trị mặc định là `never`. Một người dùng được coi là được phân quyền khi họ thuộc một hoặc nhiều role của endpoint. Nếu endpoint không có role nào được cấu hình (mặc định), tất cả người dùng đã xác thực đều được coi là được phân quyền. Bạn có thể cấu hình các role bằng property `management.endpoint.health.roles`.

> **LƯU Ý (NOTE):** Nếu bạn đã bảo mật ứng dụng và muốn dùng `always`, cấu hình bảo mật của bạn phải cho phép truy cập endpoint health cho cả người dùng đã xác thực lẫn chưa xác thực.

Thông tin health được thu thập từ nội dung của một [`HealthContributorRegistry`](https://github.com/spring-projects/spring-boot/tree/v3.2.5/spring-boot-project/spring-boot-actuator/src/main/java/org/springframework/boot/actuate/health/HealthContributorRegistry.java) (theo mặc định, tất cả các instance [`HealthContributor`](https://github.com/spring-projects/spring-boot/tree/v3.2.5/spring-boot-project/spring-boot-actuator/src/main/java/org/springframework/boot/actuate/health/HealthContributor.java) được định nghĩa trong `ApplicationContext`). Spring Boot bao gồm một số `HealthContributors` được tự động cấu hình, và bạn cũng có thể viết của riêng mình.

Một `HealthContributor` có thể là một `HealthIndicator` hoặc một `CompositeHealthContributor`. Một `HealthIndicator` cung cấp thông tin health thực tế, bao gồm một `Status`. Một `CompositeHealthContributor` cung cấp một tổ hợp các `HealthContributors` khác. Gộp lại, các contributor tạo thành một cấu trúc cây để biểu diễn health tổng thể của hệ thống.

Theo mặc định, health cuối cùng của hệ thống được suy ra bởi một `StatusAggregator`, sắp xếp các status từ mỗi `HealthIndicator` dựa trên một danh sách status có thứ tự. Status đầu tiên trong danh sách đã sắp xếp được dùng làm health status tổng thể. Nếu không có `HealthIndicator` nào trả về một status mà `StatusAggregator` biết, status `UNKNOWN` sẽ được dùng.

> **MẸO (TIP):** Bạn có thể dùng `HealthContributorRegistry` để đăng ký và hủy đăng ký các health indicator tại runtime.

## Các HealthIndicator được tự động cấu hình (Auto-configured HealthIndicators)

Khi phù hợp, Spring Boot tự động cấu hình các `HealthIndicators` được liệt kê trong bảng sau. Bạn cũng có thể bật hoặc tắt các indicator được chọn bằng cách cấu hình `management.health.key.enabled`, với `key` được liệt kê trong bảng sau:

| Key | Tên | Mô tả |
|---|---|---|
| `cassandra` | [`CassandraDriverHealthIndicator`](https://github.com/spring-projects/spring-boot/tree/v3.2.5/spring-boot-project/spring-boot-actuator/src/main/java/org/springframework/boot/actuate/cassandra/CassandraDriverHealthIndicator.java) | Kiểm tra rằng database Cassandra đang hoạt động (up). |
| `couchbase` | [`CouchbaseHealthIndicator`](https://github.com/spring-projects/spring-boot/tree/v3.2.5/spring-boot-project/spring-boot-actuator/src/main/java/org/springframework/boot/actuate/couchbase/CouchbaseHealthIndicator.java) | Kiểm tra rằng cluster Couchbase đang hoạt động (up). |
| `db` | [`DataSourceHealthIndicator`](https://github.com/spring-projects/spring-boot/tree/v3.2.5/spring-boot-project/spring-boot-actuator/src/main/java/org/springframework/boot/actuate/jdbc/DataSourceHealthIndicator.java) | Kiểm tra rằng có thể lấy được một kết nối tới `DataSource`. |
| `diskspace` | [`DiskSpaceHealthIndicator`](https://github.com/spring-projects/spring-boot/tree/v3.2.5/spring-boot-project/spring-boot-actuator/src/main/java/org/springframework/boot/actuate/system/DiskSpaceHealthIndicator.java) | Kiểm tra dung lượng ổ đĩa thấp. |
| `elasticsearch` | [`ElasticsearchRestClientHealthIndicator`](https://github.com/spring-projects/spring-boot/tree/v3.2.5/spring-boot-project/spring-boot-actuator/src/main/java/org/springframework/boot/actuate/elasticsearch/ElasticsearchRestClientHealthIndicator.java) | Kiểm tra rằng cluster Elasticsearch đang hoạt động (up). |
| `hazelcast` | [`HazelcastHealthIndicator`](https://github.com/spring-projects/spring-boot/tree/v3.2.5/spring-boot-project/spring-boot-actuator/src/main/java/org/springframework/boot/actuate/hazelcast/HazelcastHealthIndicator.java) | Kiểm tra rằng server Hazelcast đang hoạt động (up). |
| `influxdb` | [`InfluxDbHealthIndicator`](https://github.com/spring-projects/spring-boot/tree/v3.2.5/spring-boot-project/spring-boot-actuator/src/main/java/org/springframework/boot/actuate/influx/InfluxDbHealthIndicator.java) | Kiểm tra rằng server InfluxDB đang hoạt động (up). |
| `jms` | [`JmsHealthIndicator`](https://github.com/spring-projects/spring-boot/tree/v3.2.5/spring-boot-project/spring-boot-actuator/src/main/java/org/springframework/boot/actuate/jms/JmsHealthIndicator.java) | Kiểm tra rằng broker JMS đang hoạt động (up). |
| `ldap` | [`LdapHealthIndicator`](https://github.com/spring-projects/spring-boot/tree/v3.2.5/spring-boot-project/spring-boot-actuator/src/main/java/org/springframework/boot/actuate/ldap/LdapHealthIndicator.java) | Kiểm tra rằng server LDAP đang hoạt động (up). |
| `mail` | [`MailHealthIndicator`](https://github.com/spring-projects/spring-boot/tree/v3.2.5/spring-boot-project/spring-boot-actuator/src/main/java/org/springframework/boot/actuate/mail/MailHealthIndicator.java) | Kiểm tra rằng server mail đang hoạt động (up). |
| `mongo` | [`MongoHealthIndicator`](https://github.com/spring-projects/spring-boot/tree/v3.2.5/spring-boot-project/spring-boot-actuator/src/main/java/org/springframework/boot/actuate/data/mongo/MongoHealthIndicator.java) | Kiểm tra rằng database Mongo đang hoạt động (up). |
| `neo4j` | [`Neo4jHealthIndicator`](https://github.com/spring-projects/spring-boot/tree/v3.2.5/spring-boot-project/spring-boot-actuator/src/main/java/org/springframework/boot/actuate/neo4j/Neo4jHealthIndicator.java) | Kiểm tra rằng database Neo4j đang hoạt động (up). |
| `ping` | [`PingHealthIndicator`](https://github.com/spring-projects/spring-boot/tree/v3.2.5/spring-boot-project/spring-boot-actuator/src/main/java/org/springframework/boot/actuate/health/PingHealthIndicator.java) | Luôn phản hồi với `UP`. |
| `rabbit` | [`RabbitHealthIndicator`](https://github.com/spring-projects/spring-boot/tree/v3.2.5/spring-boot-project/spring-boot-actuator/src/main/java/org/springframework/boot/actuate/amqp/RabbitHealthIndicator.java) | Kiểm tra rằng server Rabbit đang hoạt động (up). |
| `redis` | [`RedisHealthIndicator`](https://github.com/spring-projects/spring-boot/tree/v3.2.5/spring-boot-project/spring-boot-actuator/src/main/java/org/springframework/boot/actuate/data/redis/RedisHealthIndicator.java) | Kiểm tra rằng server Redis đang hoạt động (up). |

> **MẸO (TIP):** Bạn có thể tắt tất cả chúng bằng cách đặt property `management.health.defaults.enabled`.

Các `HealthIndicators` bổ sung có sẵn nhưng không được bật theo mặc định:

| Key | Tên | Mô tả |
|---|---|---|
| `livenessstate` | [`LivenessStateHealthIndicator`](https://github.com/spring-projects/spring-boot/tree/v3.2.5/spring-boot-project/spring-boot-actuator/src/main/java/org/springframework/boot/actuate/availability/LivenessStateHealthIndicator.java) | Expose trạng thái sẵn sàng "Liveness" của ứng dụng. |
| `readinessstate` | [`ReadinessStateHealthIndicator`](https://github.com/spring-projects/spring-boot/tree/v3.2.5/spring-boot-project/spring-boot-actuator/src/main/java/org/springframework/boot/actuate/availability/ReadinessStateHealthIndicator.java) | Expose trạng thái sẵn sàng "Readiness" của ứng dụng. |

## Viết HealthIndicator tùy chỉnh (Writing Custom HealthIndicators)

Để cung cấp thông tin health tùy chỉnh, bạn có thể đăng ký các Spring bean hiện thực interface [`HealthIndicator`](https://github.com/spring-projects/spring-boot/tree/v3.2.5/spring-boot-project/spring-boot-actuator/src/main/java/org/springframework/boot/actuate/health/HealthIndicator.java). Bạn cần cung cấp một implementation của phương thức `health()` và trả về một response `Health`. Response `Health` nên bao gồm một status và có thể tùy chọn bao gồm các chi tiết bổ sung để hiển thị. Đoạn code sau cho thấy một implementation `HealthIndicator` mẫu:

**Java**

```java
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class MyHealthIndicator implements HealthIndicator {

    @Override
    public Health health() {
        int errorCode = check();
        if (errorCode != 0) {
            return Health.down().withDetail("Error Code", errorCode).build();
        }
        return Health.up().build();
    }

    private int check() {
        // perform some specific health check
        return ...
    }

}
```

**Kotlin**

```kotlin
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.stereotype.Component

@Component
class MyHealthIndicator : HealthIndicator {

    override fun health(): Health {
        val errorCode = check()
        if (errorCode != 0) {
            return Health.down().withDetail("Error Code", errorCode).build()
        }
        return Health.up().build()
    }

    private fun check(): Int {
        // perform some specific health check
        return  ...
    }

}
```

> **LƯU Ý (NOTE):** Định danh cho một `HealthIndicator` nhất định là tên của bean không có hậu tố `HealthIndicator`, nếu có. Trong ví dụ trên, thông tin health có sẵn trong một entry tên là `my`.

> **MẸO (TIP):** Các health indicator thường được gọi qua HTTP và cần phản hồi trước bất kỳ timeout kết nối nào. Spring Boot sẽ ghi log một thông điệp cảnh báo cho bất kỳ health indicator nào mất hơn 10 giây để phản hồi. Nếu bạn muốn cấu hình ngưỡng này, bạn có thể dùng property `management.endpoint.health.logging.slow-indicator-threshold`.

Ngoài các kiểu [`Status`](https://github.com/spring-projects/spring-boot/tree/v3.2.5/spring-boot-project/spring-boot-actuator/src/main/java/org/springframework/boot/actuate/health/Status.java) được định nghĩa sẵn của Spring Boot, `Health` có thể trả về một `Status` tùy chỉnh đại diện cho một trạng thái hệ thống mới. Trong những trường hợp như vậy, bạn cũng cần cung cấp một implementation tùy chỉnh của interface [`StatusAggregator`](https://github.com/spring-projects/spring-boot/tree/v3.2.5/spring-boot-project/spring-boot-actuator/src/main/java/org/springframework/boot/actuate/health/StatusAggregator.java), hoặc bạn phải cấu hình implementation mặc định bằng configuration property `management.endpoint.health.status.order`.

Ví dụ, giả sử một `Status` mới với mã `FATAL` đang được dùng trong một implementation `HealthIndicator` của bạn. Để cấu hình thứ tự mức độ nghiêm trọng, hãy thêm property sau vào application properties:

**Properties**

```properties
management.endpoint.health.status.order=fatal,down,out-of-service,unknown,up
```

**Yaml**

```yaml
management:
  endpoint:
    health:
      status:
        order: "fatal,down,out-of-service,unknown,up"
```

HTTP status code trong response phản ánh health status tổng thể. Theo mặc định, `OUT_OF_SERVICE` và `DOWN` ánh xạ tới 503. Mọi health status chưa được ánh xạ, bao gồm `UP`, ánh xạ tới 200. Bạn cũng có thể muốn đăng ký các ánh xạ status tùy chỉnh nếu bạn truy cập endpoint health qua HTTP. Cấu hình một ánh xạ tùy chỉnh sẽ vô hiệu hóa các ánh xạ mặc định cho `DOWN` và `OUT_OF_SERVICE`. Nếu bạn muốn giữ các ánh xạ mặc định, bạn phải cấu hình chúng tường minh, cùng với mọi ánh xạ tùy chỉnh. Ví dụ, property sau ánh xạ `FATAL` tới 503 (service unavailable) và giữ các ánh xạ mặc định cho `DOWN` và `OUT_OF_SERVICE`:

**Properties**

```properties
management.endpoint.health.status.http-mapping.down=503
management.endpoint.health.status.http-mapping.fatal=503
management.endpoint.health.status.http-mapping.out-of-service=503
```

**Yaml**

```yaml
management:
  endpoint:
    health:
      status:
        http-mapping:
          down: 503
          fatal: 503
          out-of-service: 503
```

> **MẸO (TIP):** Nếu bạn cần kiểm soát nhiều hơn, bạn có thể định nghĩa bean `HttpCodeStatusMapper` của riêng mình.

Bảng sau cho thấy các ánh xạ status mặc định cho các status có sẵn:

| Status | Ánh xạ |
|---|---|
| `DOWN` | `SERVICE_UNAVAILABLE` (`503`) |
| `OUT_OF_SERVICE` | `SERVICE_UNAVAILABLE` (`503`) |
| `UP` | Không có ánh xạ theo mặc định, nên HTTP status là `200` |
| `UNKNOWN` | Không có ánh xạ theo mặc định, nên HTTP status là `200` |

## Health indicator reactive (Reactive Health Indicators)

Với các ứng dụng reactive, chẳng hạn như những ứng dụng dùng Spring WebFlux, `ReactiveHealthContributor` cung cấp một hợp đồng (contract) không chặn để lấy health của ứng dụng. Tương tự như một `HealthContributor` truyền thống, thông tin health được thu thập từ nội dung của một [`ReactiveHealthContributorRegistry`](https://github.com/spring-projects/spring-boot/tree/v3.2.5/spring-boot-project/spring-boot-actuator/src/main/java/org/springframework/boot/actuate/health/ReactiveHealthContributorRegistry.java) (theo mặc định, tất cả các instance [`HealthContributor`](https://github.com/spring-projects/spring-boot/tree/v3.2.5/spring-boot-project/spring-boot-actuator/src/main/java/org/springframework/boot/actuate/health/HealthContributor.java) và [`ReactiveHealthContributor`](https://github.com/spring-projects/spring-boot/tree/v3.2.5/spring-boot-project/spring-boot-actuator/src/main/java/org/springframework/boot/actuate/health/ReactiveHealthContributor.java) được định nghĩa trong `ApplicationContext`). Các `HealthContributors` thông thường không kiểm tra dựa trên API reactive sẽ được thực thi trên elastic scheduler.

> **MẸO (TIP):** Trong một ứng dụng reactive, bạn nên dùng `ReactiveHealthContributorRegistry` để đăng ký và hủy đăng ký các health indicator tại runtime. Nếu bạn cần đăng ký một `HealthContributor` thông thường, bạn nên bọc nó bằng `ReactiveHealthContributor#adapt`.

Để cung cấp thông tin health tùy chỉnh từ một API reactive, bạn có thể đăng ký các Spring bean hiện thực interface [`ReactiveHealthIndicator`](https://github.com/spring-projects/spring-boot/tree/v3.2.5/spring-boot-project/spring-boot-actuator/src/main/java/org/springframework/boot/actuate/health/ReactiveHealthIndicator.java). Đoạn code sau cho thấy một implementation `ReactiveHealthIndicator` mẫu:

**Java**

```java
import reactor.core.publisher.Mono;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.ReactiveHealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class MyReactiveHealthIndicator implements ReactiveHealthIndicator {

    @Override
    public Mono<Health> health() {
        return doHealthCheck().onErrorResume((exception) ->
            Mono.just(new Health.Builder().down(exception).build()));
    }

    private Mono<Health> doHealthCheck() {
        // perform some specific health check
        return ...
    }

}
```

**Kotlin**

```kotlin
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.ReactiveHealthIndicator
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

@Component
class MyReactiveHealthIndicator : ReactiveHealthIndicator {

    override fun health(): Mono<Health> {
        return doHealthCheck()!!.onErrorResume { exception: Throwable? ->
            Mono.just(Health.Builder().down(exception).build())
        }
    }

    private fun doHealthCheck(): Mono<Health>? {
        // perform some specific health check
        return  ...
    }

}
```

> **MẸO (TIP):** Để xử lý lỗi tự động, hãy cân nhắc kế thừa từ `AbstractReactiveHealthIndicator`.

## Các ReactiveHealthIndicator được tự động cấu hình (Auto-configured ReactiveHealthIndicators)

Khi phù hợp, Spring Boot tự động cấu hình các `ReactiveHealthIndicators` sau:

| Key | Tên | Mô tả |
|---|---|---|
| `cassandra` | [`CassandraDriverReactiveHealthIndicator`](https://github.com/spring-projects/spring-boot/tree/v3.2.5/spring-boot-project/spring-boot-actuator/src/main/java/org/springframework/boot/actuate/cassandra/CassandraDriverReactiveHealthIndicator.java) | Kiểm tra rằng database Cassandra đang hoạt động (up). |
| `couchbase` | [`CouchbaseReactiveHealthIndicator`](https://github.com/spring-projects/spring-boot/tree/v3.2.5/spring-boot-project/spring-boot-actuator/src/main/java/org/springframework/boot/actuate/couchbase/CouchbaseReactiveHealthIndicator.java) | Kiểm tra rằng cluster Couchbase đang hoạt động (up). |
| `elasticsearch` | [`ElasticsearchReactiveHealthIndicator`](https://github.com/spring-projects/spring-boot/tree/v3.2.5/spring-boot-project/spring-boot-actuator/src/main/java/org/springframework/boot/actuate/data/elasticsearch/ElasticsearchReactiveHealthIndicator.java) | Kiểm tra rằng cluster Elasticsearch đang hoạt động (up). |
| `mongo` | [`MongoReactiveHealthIndicator`](https://github.com/spring-projects/spring-boot/tree/v3.2.5/spring-boot-project/spring-boot-actuator/src/main/java/org/springframework/boot/actuate/data/mongo/MongoReactiveHealthIndicator.java) | Kiểm tra rằng database Mongo đang hoạt động (up). |
| `neo4j` | [`Neo4jReactiveHealthIndicator`](https://github.com/spring-projects/spring-boot/tree/v3.2.5/spring-boot-project/spring-boot-actuator/src/main/java/org/springframework/boot/actuate/neo4j/Neo4jReactiveHealthIndicator.java) | Kiểm tra rằng database Neo4j đang hoạt động (up). |
| `redis` | [`RedisReactiveHealthIndicator`](https://github.com/spring-projects/spring-boot/tree/v3.2.5/spring-boot-project/spring-boot-actuator/src/main/java/org/springframework/boot/actuate/data/redis/RedisReactiveHealthIndicator.java) | Kiểm tra rằng server Redis đang hoạt động (up). |

> **MẸO (TIP):** Nếu cần, các indicator reactive sẽ thay thế các indicator thông thường. Ngoài ra, mọi `HealthIndicator` không được xử lý tường minh sẽ được tự động bọc lại.

## Nhóm health (Health Groups)

Đôi khi sẽ hữu ích nếu tổ chức các health indicator thành các nhóm mà bạn có thể dùng cho các mục đích khác nhau.

Để tạo một nhóm health indicator, bạn có thể dùng property `management.endpoint.health.group.<name>` và chỉ định một danh sách ID health indicator để `include` hoặc `exclude`. Ví dụ, để tạo một nhóm chỉ bao gồm các indicator database, bạn có thể định nghĩa như sau:

**Properties**

```properties
management.endpoint.health.group.custom.include=db
```

**Yaml**

```yaml
management:
  endpoint:
    health:
      group:
        custom:
          include: "db"
```

Sau đó, bạn có thể kiểm tra kết quả bằng cách truy cập [localhost:8080/actuator/health/custom](http://localhost:8080/actuator/health/custom).

Tương tự, để tạo một nhóm loại trừ các indicator database khỏi nhóm và bao gồm tất cả các indicator khác, bạn có thể định nghĩa như sau:

**Properties**

```properties
management.endpoint.health.group.custom.exclude=db
```

**Yaml**

```yaml
management:
  endpoint:
    health:
      group:
        custom:
          exclude: "db"
```

Theo mặc định, việc khởi động sẽ thất bại nếu một nhóm health bao gồm hoặc loại trừ một health indicator không tồn tại. Để tắt hành vi này, hãy đặt `management.endpoint.health.validate-group-membership` thành `false`.

Theo mặc định, các nhóm kế thừa cùng các thiết lập `StatusAggregator` và `HttpCodeStatusMapper` như health của hệ thống. Tuy nhiên, bạn cũng có thể định nghĩa chúng cho từng nhóm. Bạn cũng có thể ghi đè các property `show-details` và `roles` nếu cần:

**Properties**

```properties
management.endpoint.health.group.custom.show-details=when-authorized
management.endpoint.health.group.custom.roles=admin
management.endpoint.health.group.custom.status.order=fatal,up
management.endpoint.health.group.custom.status.http-mapping.fatal=500
management.endpoint.health.group.custom.status.http-mapping.out-of-service=500
```

**Yaml**

```yaml
management:
  endpoint:
    health:
      group:
        custom:
          show-details: "when-authorized"
          roles: "admin"
          status:
            order: "fatal,up"
            http-mapping:
              fatal: 500
              out-of-service: 500
```

> **MẸO (TIP):** Bạn có thể dùng `@Qualifier("groupname")` nếu bạn cần đăng ký các bean `StatusAggregator` hoặc `HttpCodeStatusMapper` tùy chỉnh để dùng với nhóm.

Một nhóm health cũng có thể bao gồm/loại trừ một `CompositeHealthContributor`. Bạn cũng có thể chỉ bao gồm/loại trừ một component nhất định của một `CompositeHealthContributor`. Điều này có thể được thực hiện bằng tên đầy đủ (fully qualified name) của component như sau:

```properties
management.endpoint.health.group.custom.include="test/primary"
management.endpoint.health.group.custom.exclude="test/primary/b"
```

Trong ví dụ trên, nhóm `custom` sẽ bao gồm `HealthContributor` có tên `primary`, là một component của composite `test`. Ở đây, bản thân `primary` là một composite và `HealthContributor` có tên `b` sẽ bị loại trừ khỏi nhóm `custom`.

Các nhóm health có thể được cung cấp tại một đường dẫn bổ sung trên cổng chính hoặc cổng management. Điều này hữu ích trong các môi trường cloud như Kubernetes, nơi khá phổ biến việc dùng một cổng management riêng cho các actuator endpoint vì mục đích bảo mật. Việc có một cổng riêng có thể dẫn đến các health check không đáng tin cậy, vì ứng dụng chính có thể không hoạt động đúng ngay cả khi health check thành công. Nhóm health có thể được cấu hình với một đường dẫn bổ sung như sau:

```properties
management.endpoint.health.group.live.additional-path="server:/healthz"
```

Điều này sẽ làm cho nhóm health `live` khả dụng trên cổng server chính tại `/healthz`. Tiền tố là bắt buộc và phải là `server:` (đại diện cho cổng server chính) hoặc `management:` (đại diện cho cổng management, nếu được cấu hình). Đường dẫn phải là một đoạn đường dẫn duy nhất.

## Health của DataSource (DataSource Health)

Health indicator `DataSource` hiển thị health của cả các data source tiêu chuẩn lẫn các bean routing data source. Health của một routing data source bao gồm health của từng data source đích của nó. Trong response của endpoint health, mỗi đích của một routing data source được đặt tên bằng routing key của nó. Nếu bạn không muốn đưa các routing data source vào output của indicator, hãy đặt `management.health.db.ignore-routing-data-sources` thành `true`.

# 13.7.3. Các metric và meter được hỗ trợ (Supported Metrics and Meters)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#actuator.metrics.supported>

Spring Boot cung cấp việc đăng ký meter tự động cho nhiều loại công nghệ. Trong hầu hết các tình huống, các giá trị mặc định cung cấp các metric hợp lý có thể được publish tới bất kỳ hệ thống giám sát được hỗ trợ nào.

## Metric JVM (JVM Metrics)

Auto-configuration bật các metric JVM bằng các class Micrometer cốt lõi. Các metric JVM được publish bên dưới tên meter `jvm.`.

Các metric JVM sau được cung cấp:

- Các chi tiết khác nhau về bộ nhớ và buffer pool
- Thống kê liên quan đến garbage collection
- Mức sử dụng thread
- Số lượng class được nạp và gỡ bỏ
- Thông tin phiên bản JVM
- Thời gian biên dịch JIT

## Metric hệ thống (System Metrics)

Auto-configuration bật các metric hệ thống bằng các class Micrometer cốt lõi. Các metric hệ thống được publish bên dưới các tên meter `system.`, `process.`, và `disk.`.

Các metric hệ thống sau được cung cấp:

- Metric CPU
- Metric file descriptor
- Metric uptime (cả khoảng thời gian ứng dụng đã chạy lẫn một gauge cố định của thời điểm bắt đầu tuyệt đối)
- Dung lượng ổ đĩa còn trống

## Metric khởi động ứng dụng (Application Startup Metrics)

Auto-configuration expose các metric về thời gian khởi động ứng dụng:

- `application.started.time`: thời gian cần để khởi động ứng dụng.
- `application.ready.time`: thời gian cần để ứng dụng sẵn sàng phục vụ request.

Các metric được gắn tag bằng tên đầy đủ (fully qualified name) của class ứng dụng.

## Metric logger (Logger Metrics)

Auto-configuration bật các metric event cho cả Logback và Log4J2. Các chi tiết được publish bên dưới các tên meter `log4j2.events.` hoặc `logback.events.`.

## Metric thực thi và lập lịch tác vụ (Task Execution and Scheduling Metrics)

Auto-configuration bật instrumentation cho tất cả các bean `ThreadPoolTaskExecutor` và `ThreadPoolTaskScheduler` có sẵn, miễn là `ThreadPoolExecutor` bên dưới có sẵn. Các metric được gắn tag bằng tên của executor, được suy ra từ tên bean.

## Metric JMS (JMS Metrics)

Auto-configuration bật instrumentation cho tất cả các bean `JmsTemplate` có sẵn và các phương thức được đánh annotation `@JmsListener`. Điều này sẽ tạo ra lần lượt các metric `"jms.message.publish"` và `"jms.message.process"`. Xem [tài liệu tham khảo Spring Framework để biết thêm thông tin về các observation được tạo ra](https://docs.spring.io/spring-framework/reference/6.1/integration/observability.html#observability.jms).

## Metric Spring MVC (Spring MVC Metrics)

Auto-configuration bật instrumentation cho tất cả các request được xử lý bởi các controller Spring MVC và functional handler. Theo mặc định, metric được tạo ra với tên `http.server.requests`. Bạn có thể tùy chỉnh tên bằng cách đặt property `management.observations.http.server.requests.name`.

Xem [tài liệu tham khảo Spring Framework để biết thêm thông tin về các observation được tạo ra](https://docs.spring.io/spring-framework/reference/6.1/integration/observability.html#observability.http-server.servlet).

Để thêm vào các tag mặc định, hãy cung cấp một `@Bean` kế thừa `DefaultServerRequestObservationConvention` từ package `org.springframework.http.server.observation`. Để thay thế các tag mặc định, hãy cung cấp một `@Bean` hiện thực `ServerRequestObservationConvention`.

> **MẸO (TIP):** Trong một số trường hợp, các exception được xử lý trong web controller không được ghi nhận như tag của metric request. Ứng dụng có thể chủ động ghi nhận exception bằng cách [đặt các exception đã xử lý làm thuộc tính của request](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#web.servlet.spring-mvc.error-handling).

Theo mặc định, tất cả các request đều được xử lý. Để tùy chỉnh filter, hãy cung cấp một `@Bean` hiện thực `FilterRegistrationBean<ServerHttpObservationFilter>`.

## Metric Spring WebFlux (Spring WebFlux Metrics)

Auto-configuration bật instrumentation cho tất cả các request được xử lý bởi các controller Spring WebFlux và functional handler. Theo mặc định, metric được tạo ra với tên `http.server.requests`. Bạn có thể tùy chỉnh tên bằng cách đặt property `management.observations.http.server.requests.name`.

Xem [tài liệu tham khảo Spring Framework để biết thêm thông tin về các observation được tạo ra](https://docs.spring.io/spring-framework/reference/6.1/integration/observability.html#observability.http-server.reactive).

Để thêm vào các tag mặc định, hãy cung cấp một `@Bean` kế thừa `DefaultServerRequestObservationConvention` từ package `org.springframework.http.server.reactive.observation`. Để thay thế các tag mặc định, hãy cung cấp một `@Bean` hiện thực `ServerRequestObservationConvention`.

> **MẸO (TIP):** Trong một số trường hợp, các exception được xử lý trong controller và handler function không được ghi nhận như tag của metric request. Ứng dụng có thể chủ động ghi nhận exception bằng cách [đặt các exception đã xử lý làm thuộc tính của request](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#web.reactive.webflux.error-handling).

## Metric Jersey Server (Jersey Server Metrics)

Auto-configuration bật instrumentation cho tất cả các request được xử lý bởi implementation JAX-RS Jersey. Theo mặc định, metric được tạo ra với tên `http.server.requests`. Bạn có thể tùy chỉnh tên bằng cách đặt property `management.observations.http.server.requests.name`.

Theo mặc định, các metric Jersey server được gắn tag với thông tin sau:

| Tag | Mô tả |
|---|---|
| `exception` | Tên class đơn giản của bất kỳ exception nào được ném ra trong khi xử lý request. |
| `method` | Method của request (ví dụ, `GET` hoặc `POST`) |
| `outcome` | Kết quả của request, dựa trên status code của response. 1xx là `INFORMATIONAL`, 2xx là `SUCCESS`, 3xx là `REDIRECTION`, 4xx là `CLIENT_ERROR`, và 5xx là `SERVER_ERROR` |
| `status` | HTTP status code của response (ví dụ, `200` hoặc `500`) |
| `uri` | URI template của request trước khi thay thế biến, nếu có thể (ví dụ, `/api/person/{id}`) |

Để tùy chỉnh các tag, hãy cung cấp một `@Bean` hiện thực `JerseyTagsProvider`.

## Metric HTTP Client (HTTP Client Metrics)

Spring Boot Actuator quản lý instrumentation của `RestTemplate`, `WebClient` và `RestClient`. Để làm vậy, bạn phải inject builder được tự động cấu hình và dùng nó để tạo các instance:

- `RestTemplateBuilder` cho `RestTemplate`
- `WebClient.Builder` cho `WebClient`
- `RestClient.Builder` cho `RestClient`

Bạn cũng có thể áp dụng thủ công các customizer chịu trách nhiệm cho instrumentation này, cụ thể là `ObservationRestTemplateCustomizer`, `ObservationWebClientCustomizer` và `ObservationRestClientCustomizer`.

Theo mặc định, metric được tạo ra với tên `http.client.requests`. Bạn có thể tùy chỉnh tên bằng cách đặt property `management.observations.http.client.requests.name`.

Xem [tài liệu tham khảo Spring Framework để biết thêm thông tin về các observation được tạo ra](https://docs.spring.io/spring-framework/reference/6.1/integration/observability.html#observability.http-client).

Để tùy chỉnh các tag khi dùng `RestTemplate` hoặc `RestClient`, hãy cung cấp một `@Bean` hiện thực `ClientRequestObservationConvention` từ package `org.springframework.http.client.observation`. Để tùy chỉnh các tag khi dùng `WebClient`, hãy cung cấp một `@Bean` hiện thực `ClientRequestObservationConvention` từ package `org.springframework.web.reactive.function.client`.

## Metric Tomcat (Tomcat Metrics)

Auto-configuration chỉ bật instrumentation cho Tomcat khi một `MBeanRegistry` được bật. Theo mặc định, `MBeanRegistry` bị tắt, nhưng bạn có thể bật nó bằng cách đặt `server.tomcat.mbeanregistry.enabled` thành `true`.

Các metric Tomcat được publish bên dưới tên meter `tomcat.`.

## Metric cache (Cache Metrics)

Auto-configuration bật instrumentation cho tất cả các instance `Cache` có sẵn khi khởi động, với các metric có tiền tố `cache`. Instrumentation cache được chuẩn hóa cho một tập metric cơ bản. Các metric bổ sung, đặc thù cho từng cache, cũng có sẵn.

Các thư viện cache sau được hỗ trợ:

- Cache2k
- Caffeine
- Hazelcast
- Bất kỳ implementation JCache (JSR-107) tương thích nào
- Redis

Các metric được gắn tag bằng tên của cache và bằng tên của `CacheManager`, được suy ra từ tên bean.

> **LƯU Ý (NOTE):** Chỉ các cache được cấu hình khi khởi động mới được gắn với registry. Với các cache không được định nghĩa trong cấu hình của cache, chẳng hạn như các cache được tạo ngay lập tức hoặc bằng code sau giai đoạn khởi động, cần đăng ký tường minh. Một bean `CacheMetricsRegistrar` được cung cấp để giúp quá trình đó dễ dàng hơn.

## Metric Spring Batch (Spring Batch Metrics)

Xem [tài liệu tham khảo Spring Batch](https://docs.spring.io/spring-batch/reference/5.1/monitoring-and-metrics.html).

## Metric Spring GraphQL (Spring GraphQL Metrics)

Xem [tài liệu tham khảo Spring GraphQL](https://docs.spring.io/spring-graphql/reference/1.2/observability.html).

## Metric DataSource (DataSource Metrics)

Auto-configuration bật instrumentation cho tất cả các object `DataSource` có sẵn với các metric có tiền tố `jdbc.connections`. Instrumentation data source tạo ra các gauge đại diện cho số kết nối đang hoạt động, nhàn rỗi, tối đa cho phép, và tối thiểu cho phép trong pool.

Các metric cũng được gắn tag bằng tên của `DataSource` được tính dựa trên tên bean.

> **MẸO (TIP):** Theo mặc định, Spring Boot cung cấp metadata cho tất cả các data source được hỗ trợ. Bạn có thể thêm các bean `DataSourcePoolMetadataProvider` nếu data source yêu thích của bạn không được hỗ trợ. Xem `DataSourcePoolMetadataProvidersConfiguration` để có ví dụ.

Ngoài ra, các metric đặc thù của Hikari được expose với tiền tố `hikaricp`. Mỗi metric được gắn tag bằng tên của pool (bạn có thể kiểm soát nó bằng `spring.datasource.name`).

## Metric Hibernate (Hibernate Metrics)

Nếu `org.hibernate.orm:hibernate-micrometer` có trên classpath, tất cả các instance `EntityManagerFactory` của Hibernate có sẵn đã bật thống kê sẽ được instrument với một metric tên là `hibernate`.

Các metric cũng được gắn tag bằng tên của `EntityManagerFactory`, được suy ra từ tên bean.

Để bật thống kê, property JPA tiêu chuẩn `hibernate.generate_statistics` phải được đặt thành `true`. Bạn có thể bật nó trên `EntityManagerFactory` được tự động cấu hình:

**Properties**

```properties
spring.jpa.properties[hibernate.generate_statistics]=true
```

**Yaml**

```yaml
spring:
  jpa:
    properties:
      "[hibernate.generate_statistics]": true
```

## Metric repository Spring Data (Spring Data Repository Metrics)

Auto-configuration bật instrumentation cho tất cả các lời gọi phương thức `Repository` của Spring Data. Theo mặc định, metric được tạo ra với tên `spring.data.repository.invocations`. Bạn có thể tùy chỉnh tên bằng cách đặt property `management.metrics.data.repository.metric-name`.

Annotation `@Timed` từ package `io.micrometer.core.annotation` được hỗ trợ trên các interface và phương thức `Repository`. Nếu bạn không muốn ghi metric cho tất cả các lời gọi `Repository`, bạn có thể đặt `management.metrics.data.repository.autotime.enabled` thành `false` và chỉ dùng các annotation `@Timed` thay thế.

> **LƯU Ý (NOTE):** Một annotation `@Timed` với `longTask = true` bật một long task timer cho phương thức. Long task timer yêu cầu một tên metric riêng và có thể được xếp chồng với một short task timer.

Theo mặc định, các metric liên quan đến lời gọi repository được gắn tag với thông tin sau:

| Tag | Mô tả |
|---|---|
| `repository` | Tên class đơn giản của `Repository` nguồn. |
| `method` | Tên của phương thức `Repository` đã được gọi. |
| `state` | Trạng thái kết quả (`SUCCESS`, `ERROR`, `CANCELED`, hoặc `RUNNING`). |
| `exception` | Tên class đơn giản của bất kỳ exception nào được ném ra từ lời gọi. |

Để thay thế các tag mặc định, hãy cung cấp một `@Bean` hiện thực `RepositoryTagsProvider`.

## Metric RabbitMQ (RabbitMQ Metrics)

Auto-configuration bật instrumentation cho tất cả các connection factory RabbitMQ có sẵn với một metric tên là `rabbitmq`.

## Metric Spring Integration (Spring Integration Metrics)

Spring Integration tự động cung cấp [hỗ trợ Micrometer](https://docs.spring.io/spring-integration/reference/6.2/metrics.html#micrometer-integration) bất cứ khi nào có bean `MeterRegistry`. Các metric được publish bên dưới tên meter `spring.integration.`.

## Metric Kafka (Kafka Metrics)

Auto-configuration đăng ký một `MicrometerConsumerListener` và `MicrometerProducerListener` lần lượt cho consumer factory và producer factory được tự động cấu hình. Nó cũng đăng ký một `KafkaStreamsMicrometerListener` cho `StreamsBuilderFactoryBean`. Để biết thêm chi tiết, xem phần [Micrometer Native Metrics](https://docs.spring.io/spring-kafka/docs/3.1.4/reference/kafka/micrometer.html#micrometer-native) trong tài liệu Spring Kafka.

## Metric MongoDB (MongoDB Metrics)

Phần này mô tả ngắn gọn các metric có sẵn cho MongoDB.

### Metric lệnh MongoDB (MongoDB Command Metrics)

Auto-configuration đăng ký một `MongoMetricsCommandListener` với `MongoClient` được tự động cấu hình.

Một metric timer tên là `mongodb.driver.commands` được tạo cho mỗi lệnh được gửi tới driver MongoDB bên dưới. Mỗi metric được gắn tag với thông tin sau theo mặc định:

| Tag | Mô tả |
|---|---|
| `command` | Tên của lệnh được gửi. |
| `cluster.id` | Định danh của cluster mà lệnh được gửi tới. |
| `server.address` | Địa chỉ của server mà lệnh được gửi tới. |
| `status` | Kết quả của lệnh (`SUCCESS` hoặc `FAILED`). |

Để thay thế các tag metric mặc định, hãy định nghĩa một bean `MongoCommandTagsProvider`, như ví dụ sau cho thấy:

**Java**

```java
import io.micrometer.core.instrument.binder.mongodb.MongoCommandTagsProvider;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class MyCommandTagsProviderConfiguration {

    @Bean
    public MongoCommandTagsProvider customCommandTagsProvider() {
        return new CustomCommandTagsProvider();
    }

}
```

**Kotlin**

```kotlin
import io.micrometer.core.instrument.binder.mongodb.MongoCommandTagsProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class MyCommandTagsProviderConfiguration {

    @Bean
    fun customCommandTagsProvider(): MongoCommandTagsProvider? {
        return CustomCommandTagsProvider()
    }

}
```

Để tắt các metric lệnh được tự động cấu hình, hãy đặt property sau:

**Properties**

```properties
management.metrics.mongo.command.enabled=false
```

**Yaml**

```yaml
management:
  metrics:
    mongo:
      command:
        enabled: false
```

### Metric connection pool MongoDB (MongoDB Connection Pool Metrics)

Auto-configuration đăng ký một `MongoMetricsConnectionPoolListener` với `MongoClient` được tự động cấu hình.

Các metric gauge sau được tạo cho connection pool:

- `mongodb.driver.pool.size` báo cáo kích thước hiện tại của connection pool, bao gồm cả các thành viên nhàn rỗi và đang được dùng.
- `mongodb.driver.pool.checkedout` báo cáo số lượng kết nối hiện đang được dùng.
- `mongodb.driver.pool.waitqueuesize` báo cáo kích thước hiện tại của hàng đợi chờ lấy một kết nối từ pool.

Mỗi metric được gắn tag với thông tin sau theo mặc định:

| Tag | Mô tả |
|---|---|
| `cluster.id` | Định danh của cluster mà connection pool tương ứng. |
| `server.address` | Địa chỉ của server mà connection pool tương ứng. |

Để thay thế các tag metric mặc định, hãy định nghĩa một bean `MongoConnectionPoolTagsProvider`:

**Java**

```java
import io.micrometer.core.instrument.binder.mongodb.MongoConnectionPoolTagsProvider;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class MyConnectionPoolTagsProviderConfiguration {

    @Bean
    public MongoConnectionPoolTagsProvider customConnectionPoolTagsProvider() {
        return new CustomConnectionPoolTagsProvider();
    }

}
```

**Kotlin**

```kotlin
import io.micrometer.core.instrument.binder.mongodb.MongoConnectionPoolTagsProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class MyConnectionPoolTagsProviderConfiguration {

    @Bean
    fun customConnectionPoolTagsProvider(): MongoConnectionPoolTagsProvider {
        return CustomConnectionPoolTagsProvider()
    }

}
```

Để tắt các metric connection pool được tự động cấu hình, hãy đặt property sau:

**Properties**

```properties
management.metrics.mongo.connectionpool.enabled=false
```

**Yaml**

```yaml
management:
  metrics:
    mongo:
      connectionpool:
        enabled: false
```

## Metric Jetty (Jetty Metrics)

Auto-configuration gắn các metric cho `ThreadPool` của Jetty bằng `JettyServerThreadPoolMetrics` của Micrometer. Các metric cho các instance `Connector` của Jetty được gắn bằng `JettyConnectionMetrics` của Micrometer và, khi `server.ssl.enabled` được đặt thành `true`, bằng `JettySslHandshakeMetrics` của Micrometer.

## Hỗ trợ annotation @Timed (@Timed Annotation Support)

Để bật việc quét các annotation `@Timed`, bạn sẽ cần đặt property `management.observations.annotations.enabled` thành `true`. Vui lòng tham khảo [tài liệu Micrometer](https://docs.micrometer.io/micrometer/reference/concepts#_the_timed_annotation).

## Metric Redis (Redis Metrics)

Auto-configuration đăng ký một `MicrometerCommandLatencyRecorder` cho `LettuceConnectionFactory` được tự động cấu hình. Để biết thêm chi tiết, xem [phần Micrometer Metrics](https://lettuce.io/core/6.3.2.RELEASE/reference/index.html#command.latency.metrics.micrometer) trong tài liệu Lettuce.

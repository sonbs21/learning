# 9.2.6. Couchbase

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#data.nosql.couchbase>

[Couchbase](https://www.couchbase.com/) là một database NoSQL hướng tài liệu, đa mô hình (multi-model), phân tán, mã nguồn mở, được tối ưu cho các ứng dụng tương tác. Spring Boot cung cấp auto-configuration cho Couchbase và các abstraction bên trên nó do [Spring Data Couchbase](https://github.com/spring-projects/spring-data-couchbase) cung cấp. Có các "Starter" `spring-boot-starter-data-couchbase` và `spring-boot-starter-data-couchbase-reactive` để tập hợp các dependency một cách tiện lợi.

## Kết nối tới Couchbase (Connecting to Couchbase)

Bạn có thể có một `Cluster` bằng cách thêm Couchbase SDK và một số cấu hình. Các property `spring.couchbase.*` có thể được dùng để tùy chỉnh kết nối. Thông thường, bạn cung cấp [connection string](https://github.com/couchbaselabs/sdk-rfcs/blob/master/rfc/0011-connection-string.md), username, và password, như trong ví dụ sau:

**Properties**

```properties
spring.couchbase.connection-string=couchbase://192.168.1.123
spring.couchbase.username=user
spring.couchbase.password=secret
```

**Yaml**

```yaml
spring:
  couchbase:
    connection-string: "couchbase://192.168.1.123"
    username: "user"
    password: "secret"
```

Cũng có thể tùy chỉnh một số thiết lập của `ClusterEnvironment`. Ví dụ, cấu hình sau thay đổi timeout để mở một `Bucket` mới và bật hỗ trợ SSL với tham chiếu tới một [SSL bundle](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.ssl) đã cấu hình:

**Properties**

```properties
spring.couchbase.env.timeouts.connect=3s
spring.couchbase.env.ssl.bundle=example
```

**Yaml**

```yaml
spring:
  couchbase:
    env:
      timeouts:
        connect: "3s"
      ssl:
        bundle: "example"
```

> **MẸO (TIP):** Xem các property `spring.couchbase.env.*` để biết thêm chi tiết. Để kiểm soát nhiều hơn, có thể dùng một hoặc nhiều bean `ClusterEnvironmentBuilderCustomizer`.

## Repository Spring Data Couchbase (Spring Data Couchbase Repositories)

Spring Data bao gồm hỗ trợ repository cho Couchbase.

Repository và document được tìm thấy thông qua việc quét. Theo mặc định, các [package auto-configuration](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#using.auto-configuration.packages) được quét. Bạn có thể tùy chỉnh các vị trí tìm kiếm repository và document bằng cách dùng lần lượt `@EnableCouchbaseRepositories` và `@EntityScan`.

Để biết đầy đủ chi tiết về Spring Data Couchbase, xem [tài liệu tham khảo](https://docs.spring.io/spring-data/couchbase/docs/5.2.5/reference/html/).

Bạn có thể inject một instance `CouchbaseTemplate` đã được tự động cấu hình giống như với bất kỳ Spring Bean nào khác, miễn là có một bean `CouchbaseClientFactory`. Điều này xảy ra khi có một `Cluster`, như mô tả ở trên, và tên bucket đã được chỉ định:

**Properties**

```properties
spring.data.couchbase.bucket-name=my-bucket
```

**Yaml**

```yaml
spring:
  data:
    couchbase:
      bucket-name: "my-bucket"
```

Các ví dụ sau cho thấy cách inject một bean `CouchbaseTemplate`:

**Java**

```java
import org.springframework.data.couchbase.core.CouchbaseTemplate;
import org.springframework.stereotype.Component;

@Component
public class MyBean {

    private final CouchbaseTemplate template;

    public MyBean(CouchbaseTemplate template) {
        this.template = template;
    }

    // ...

    public String someMethod() {
        return this.template.getBucketName();
    }

}
```

**Kotlin**

```kotlin
import org.springframework.data.couchbase.core.CouchbaseTemplate
import org.springframework.stereotype.Component

@Component
class MyBean(private val template: CouchbaseTemplate) {

    // ...

    fun someMethod(): String {
        return template.bucketName
    }

}
```

Có một vài bean mà bạn có thể định nghĩa trong cấu hình của riêng mình để ghi đè những bean do auto-configuration cung cấp:

- Một `@Bean` `CouchbaseMappingContext` với tên `couchbaseMappingContext`.
- Một `@Bean` `CustomConversions` với tên `couchbaseCustomConversions`.
- Một `@Bean` `CouchbaseTemplate` với tên `couchbaseTemplate`.

Để tránh hard-code các tên đó trong cấu hình của riêng bạn, bạn có thể tái sử dụng `BeanNames` do Spring Data Couchbase cung cấp. Ví dụ, bạn có thể tùy chỉnh các converter cần dùng, như sau:

**Java**

```java
import org.assertj.core.util.Arrays;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.couchbase.config.BeanNames;
import org.springframework.data.couchbase.core.convert.CouchbaseCustomConversions;

@Configuration(proxyBeanMethods = false)
public class MyCouchbaseConfiguration {

    @Bean(BeanNames.COUCHBASE_CUSTOM_CONVERSIONS)
    public CouchbaseCustomConversions myCustomConversions() {
        return new CouchbaseCustomConversions(Arrays.asList(new MyConverter()));
    }

}
```

**Kotlin**

```kotlin
import org.assertj.core.util.Arrays
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.couchbase.config.BeanNames
import org.springframework.data.couchbase.core.convert.CouchbaseCustomConversions

@Configuration(proxyBeanMethods = false)
class MyCouchbaseConfiguration {

    @Bean(BeanNames.COUCHBASE_CUSTOM_CONVERSIONS)
    fun myCustomConversions(): CouchbaseCustomConversions {
        return CouchbaseCustomConversions(Arrays.asList(MyConverter()))
    }

}
```

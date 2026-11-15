# 18.8.1. Cấu hình DataSource tùy chỉnh (Configure a Custom DataSource)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.data-access.configure-custom-datasource>

Để cấu hình `DataSource` của riêng bạn, hãy định nghĩa một `@Bean` kiểu đó trong cấu hình. Spring Boot tái sử dụng `DataSource` của bạn ở bất cứ nơi nào cần, bao gồm cả việc khởi tạo cơ sở dữ liệu. Nếu bạn cần đưa một số thiết lập ra bên ngoài, bạn có thể bind `DataSource` của mình vào environment (xem "[Cấu hình bên thứ ba (Third-party Configuration)](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.external-config.typesafe-configuration-properties.third-party-configuration)").

Ví dụ sau cho thấy cách định nghĩa một data source trong một bean:

**Java**

```java
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class MyDataSourceConfiguration {

    @Bean
    @ConfigurationProperties(prefix = "app.datasource")
    public SomeDataSource dataSource() {
        return new SomeDataSource();
    }

}
```

**Kotlin**

```kotlin
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class MyDataSourceConfiguration {

    @Bean
    @ConfigurationProperties(prefix = "app.datasource")
    fun dataSource(): SomeDataSource {
        return SomeDataSource()
    }

}
```

Ví dụ sau cho thấy cách định nghĩa một data source bằng cách đặt các property:

**Properties**

```properties
app.datasource.url=jdbc:h2:mem:mydb
app.datasource.username=sa
app.datasource.pool-size=30
```

**Yaml**

```yaml
app:
  datasource:
    url: "jdbc:h2:mem:mydb"
    username: "sa"
    pool-size: 30
```

Giả sử `SomeDataSource` có các JavaBean property thông thường cho URL, username, và pool size, các thiết lập này sẽ được bind tự động trước khi `DataSource` được cung cấp cho các component khác.

Spring Boot cũng cung cấp một class builder tiện ích, tên là `DataSourceBuilder`, có thể được dùng để tạo một trong các data source chuẩn (nếu nó có trên classpath). Builder có thể phát hiện cái cần dùng dựa trên những gì có sẵn trên classpath. Nó cũng tự động phát hiện driver dựa trên JDBC URL.

Ví dụ sau cho thấy cách tạo một data source bằng `DataSourceBuilder`:

**Java**

```java
import javax.sql.DataSource;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class MyDataSourceConfiguration {

    @Bean
    @ConfigurationProperties("app.datasource")
    public DataSource dataSource() {
        return DataSourceBuilder.create().build();
    }

}
```

**Kotlin**

```kotlin
import javax.sql.DataSource

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.jdbc.DataSourceBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class MyDataSourceConfiguration {

    @Bean
    @ConfigurationProperties("app.datasource")
    fun dataSource(): DataSource {
        return DataSourceBuilder.create().build()
    }

}
```

Để chạy một ứng dụng với `DataSource` đó, tất cả những gì bạn cần là thông tin kết nối. Các thiết lập đặc thù của pool cũng có thể được cung cấp. Hãy kiểm tra implementation sẽ được dùng lúc runtime để biết thêm chi tiết.

Ví dụ sau cho thấy cách định nghĩa một JDBC data source bằng cách đặt các property:

**Properties**

```properties
app.datasource.url=jdbc:mysql://localhost/test
app.datasource.username=dbuser
app.datasource.password=dbpass
app.datasource.pool-size=30
```

**Yaml**

```yaml
app:
  datasource:
    url: "jdbc:mysql://localhost/test"
    username: "dbuser"
    password: "dbpass"
    pool-size: 30
```

Tuy nhiên, có một vấn đề. Vì kiểu thực tế của connection pool không được expose, không có key nào được sinh ra trong metadata cho `DataSource` tùy chỉnh của bạn và IDE không có gợi ý hoàn thành (completion) nào (vì interface `DataSource` không expose property nào). Ngoài ra, nếu bạn tình cờ có Hikari trên classpath, thiết lập cơ bản này không hoạt động, vì Hikari không có property `url` (nhưng có property `jdbcUrl`). Trong trường hợp đó, bạn phải viết lại cấu hình như sau:

**Properties**

```properties
app.datasource.jdbc-url=jdbc:mysql://localhost/test
app.datasource.username=dbuser
app.datasource.password=dbpass
app.datasource.pool-size=30
```

**Yaml**

```yaml
app:
  datasource:
    jdbc-url: "jdbc:mysql://localhost/test"
    username: "dbuser"
    password: "dbpass"
    pool-size: 30
```

Bạn có thể khắc phục điều đó bằng cách buộc connection pool dùng và trả về một implementation chuyên dụng thay vì `DataSource`. Bạn không thể thay đổi implementation lúc runtime, nhưng danh sách các tùy chọn sẽ được tường minh.

Ví dụ sau cho thấy cách tạo một `HikariDataSource` với `DataSourceBuilder`:

**Java**

```java
import com.zaxxer.hikari.HikariDataSource;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class MyDataSourceConfiguration {

    @Bean
    @ConfigurationProperties("app.datasource")
    public HikariDataSource dataSource() {
        return DataSourceBuilder.create().type(HikariDataSource.class).build();
    }

}
```

**Kotlin**

```kotlin
import com.zaxxer.hikari.HikariDataSource
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.jdbc.DataSourceBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class MyDataSourceConfiguration {

    @Bean
    @ConfigurationProperties("app.datasource")
    fun dataSource(): HikariDataSource {
        return DataSourceBuilder.create().type(HikariDataSource::class.java).build()
    }

}
```

Bạn thậm chí có thể đi xa hơn bằng cách tận dụng những gì `DataSourceProperties` làm cho bạn — tức là, cung cấp một embedded database mặc định với username và password hợp lý nếu không có URL nào được cung cấp. Bạn có thể dễ dàng khởi tạo một `DataSourceBuilder` từ trạng thái của bất kỳ object `DataSourceProperties` nào, vì vậy bạn cũng có thể inject DataSource mà Spring Boot tạo tự động. Tuy nhiên, điều đó sẽ chia cấu hình của bạn thành hai namespace: `url`, `username`, `password`, `type`, và `driver` trên `spring.datasource` và phần còn lại trên namespace tùy chỉnh của bạn (`app.datasource`). Để tránh điều đó, bạn có thể định nghĩa lại một `DataSourceProperties` tùy chỉnh trên namespace tùy chỉnh của mình, như trong ví dụ sau:

**Java**

```java
import com.zaxxer.hikari.HikariDataSource;

import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration(proxyBeanMethods = false)
public class MyDataSourceConfiguration {

    @Bean
    @Primary
    @ConfigurationProperties("app.datasource")
    public DataSourceProperties dataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @ConfigurationProperties("app.datasource.configuration")
    public HikariDataSource dataSource(DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }

}
```

**Kotlin**

```kotlin
import com.zaxxer.hikari.HikariDataSource
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary

@Configuration(proxyBeanMethods = false)
class MyDataSourceConfiguration {

    @Bean
    @Primary
    @ConfigurationProperties("app.datasource")
    fun dataSourceProperties(): DataSourceProperties {
        return DataSourceProperties()
    }

    @Bean
    @ConfigurationProperties("app.datasource.configuration")
    fun dataSource(properties: DataSourceProperties): HikariDataSource {
        return properties.initializeDataSourceBuilder().type(HikariDataSource::class.java).build()
    }

}
```

Thiết lập này giúp bạn *đồng bộ* với những gì Spring Boot làm cho bạn theo mặc định, ngoại trừ việc một connection pool chuyên dụng được chọn (trong code) và các thiết lập của nó được expose trong sub namespace `app.datasource.configuration`. Vì `DataSourceProperties` đảm nhận việc chuyển đổi `url`/`jdbcUrl` cho bạn, bạn có thể cấu hình nó như sau:

**Properties**

```properties
app.datasource.url=jdbc:mysql://localhost/test
app.datasource.username=dbuser
app.datasource.password=dbpass
app.datasource.configuration.maximum-pool-size=30
```

**Yaml**

```yaml
app:
  datasource:
    url: "jdbc:mysql://localhost/test"
    username: "dbuser"
    password: "dbpass"
    configuration:
      maximum-pool-size: 30
```

> **MẸO (TIP):** Spring Boot sẽ expose các thiết lập đặc thù của Hikari tới `spring.datasource.hikari`. Ví dụ này dùng một sub namespace `configuration` tổng quát hơn vì ví dụ không hỗ trợ nhiều implementation datasource.

> **LƯU Ý (NOTE):** Vì cấu hình tùy chỉnh của bạn chọn dùng Hikari, `app.datasource.type` không có tác dụng. Trên thực tế, builder được khởi tạo với bất kỳ giá trị nào bạn có thể đặt ở đó và sau đó bị ghi đè bởi lời gọi `.type()`.

Xem "[Cấu hình DataSource (Configure a DataSource)](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#data.sql.datasource)" trong phần "Các tính năng của Spring Boot" và class [`DataSourceAutoConfiguration`](https://github.com/spring-projects/spring-boot/tree/v3.2.5/spring-boot-project/spring-boot-autoconfigure/src/main/java/org/springframework/boot/autoconfigure/jdbc/DataSourceAutoConfiguration.java) để biết thêm chi tiết.

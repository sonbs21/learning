# 7.3. Profile (Profiles)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.profiles>

Spring Profile cung cấp một cách để tách biệt các phần cấu hình của ứng dụng và chỉ làm cho chúng khả dụng trong một số môi trường nhất định. Bất kỳ `@Component`, `@Configuration` hoặc `@ConfigurationProperties` nào cũng có thể được đánh dấu bằng `@Profile` để giới hạn thời điểm nó được nạp, như trong ví dụ sau:

**Java**

```java
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("production")
public class ProductionConfiguration {

    // ...

}
```

**Kotlin**

```kotlin
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration(proxyBeanMethods = false)
@Profile("production")
class ProductionConfiguration {

    // ...

}
```

> **LƯU Ý (NOTE):** Nếu các bean `@ConfigurationProperties` được đăng ký thông qua `@EnableConfigurationProperties` thay vì quét tự động, annotation `@Profile` cần được chỉ định trên class `@Configuration` có annotation `@EnableConfigurationProperties`. Trong trường hợp `@ConfigurationProperties` được quét, `@Profile` có thể được chỉ định trên chính class `@ConfigurationProperties`.

Bạn có thể dùng property `spring.profiles.active` của `Environment` để chỉ định những profile nào đang hoạt động. Bạn có thể chỉ định property này theo bất kỳ cách nào đã mô tả trước đó trong chương này. Ví dụ, bạn có thể đưa nó vào `application.properties`, như trong ví dụ sau:

**Properties**

```properties
spring.profiles.active=dev,hsqldb
```

**Yaml**

```yaml
spring:
  profiles:
    active: "dev,hsqldb"
```

Bạn cũng có thể chỉ định nó trên dòng lệnh bằng tham số sau: `--spring.profiles.active=dev,hsqldb`.

Nếu không có profile nào đang hoạt động, một profile mặc định sẽ được bật. Tên của profile mặc định là `default` và nó có thể được điều chỉnh bằng property `spring.profiles.default` của `Environment`, như trong ví dụ sau:

**Properties**

```properties
spring.profiles.default=none
```

**Yaml**

```yaml
spring:
  profiles:
    default: "none"
```

`spring.profiles.active` và `spring.profiles.default` chỉ có thể được dùng trong các document không theo profile. Điều này có nghĩa là chúng không thể được đưa vào [các file theo profile](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.external-config.files.profile-specific) hoặc [các document được kích hoạt](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.external-config.files.activation-properties) bởi `spring.config.activate.on-profile`.

Ví dụ, cấu hình của document thứ hai là không hợp lệ:

**Properties**

```properties
# this document is valid
spring.profiles.active=prod
#---
# this document is invalid
spring.config.activate.on-profile=prod
spring.profiles.active=metrics
```

**Yaml**

```yaml
# this document is valid
spring:
  profiles:
    active: "prod"
---
# this document is invalid
spring:
  config:
    activate:
      on-profile: "prod"
  profiles:
    active: "metrics"
```

## 7.3.1. Thêm profile đang hoạt động (Adding Active Profiles)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.profiles.adding-active-profiles>

Property `spring.profiles.active` tuân theo cùng các quy tắc thứ tự như các property khác: `PropertySource` cao nhất sẽ thắng. Điều này có nghĩa là bạn có thể chỉ định các profile đang hoạt động trong `application.properties` rồi **thay thế** chúng bằng tham số dòng lệnh.

Đôi khi, sẽ hữu ích nếu có các property **thêm vào** các profile đang hoạt động thay vì thay thế chúng. Property `spring.profiles.include` có thể được dùng để thêm các profile đang hoạt động bên trên những profile đã được kích hoạt bởi property `spring.profiles.active`. Entry point `SpringApplication` cũng có một Java API để đặt thêm profile. Xem phương thức `setAdditionalProfiles()` trong [SpringApplication](https://docs.spring.io/spring-boot/docs/3.2.5/api/org/springframework/boot/SpringApplication.html).

Ví dụ, khi một ứng dụng với các property sau được chạy, các profile common và local sẽ được kích hoạt ngay cả khi nó chạy với tham số `--spring.profiles.active`:

**Properties**

```properties
spring.profiles.include[0]=common
spring.profiles.include[1]=local
```

**Yaml**

```yaml
spring:
  profiles:
    include:
      - "common"
      - "local"
```

> **CẢNH BÁO (WARNING):** Tương tự như `spring.profiles.active`, `spring.profiles.include` chỉ có thể được dùng trong các document không theo profile. Điều này có nghĩa là nó không thể được đưa vào [các file theo profile](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.external-config.files.profile-specific) hoặc [các document được kích hoạt](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.external-config.files.activation-properties) bởi `spring.config.activate.on-profile`.

Nhóm profile (profile group), được mô tả trong [phần tiếp theo](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.profiles.groups), cũng có thể được dùng để thêm các profile đang hoạt động nếu một profile nhất định đang hoạt động.

## 7.3.2. Nhóm profile (Profile Groups)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.profiles.groups>

Đôi khi các profile mà bạn định nghĩa và dùng trong ứng dụng quá chi tiết và trở nên cồng kềnh khi sử dụng. Ví dụ, bạn có thể có các profile `proddb` và `prodmq` dùng để bật độc lập các tính năng database và messaging.

Để giúp giải quyết điều này, Spring Boot cho phép bạn định nghĩa các nhóm profile. Một nhóm profile cho phép bạn định nghĩa một tên logic cho một nhóm các profile liên quan.

Ví dụ, chúng ta có thể tạo một nhóm `production` bao gồm các profile `proddb` và `prodmq`.

**Properties**

```properties
spring.profiles.group.production[0]=proddb
spring.profiles.group.production[1]=prodmq
```

**Yaml**

```yaml
spring:
  profiles:
    group:
      production:
      - "proddb"
      - "prodmq"
```

Giờ đây, ứng dụng của chúng ta có thể được khởi động bằng `--spring.profiles.active=production` để kích hoạt các profile `production`, `proddb` và `prodmq` chỉ trong một lần.

## 7.3.3. Đặt profile bằng code (Programmatically Setting Profiles)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.profiles.programmatically-setting-profiles>

Bạn có thể đặt các profile đang hoạt động bằng code bằng cách gọi `SpringApplication.setAdditionalProfiles(…)` trước khi ứng dụng chạy. Cũng có thể kích hoạt profile bằng interface `ConfigurableEnvironment` của Spring.

## 7.3.4. File cấu hình theo profile (Profile-specific Configuration Files)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.profiles.profile-specific-configuration-files>

Các biến thể theo profile của cả `application.properties` (hoặc `application.yaml`) và các file được tham chiếu thông qua `@ConfigurationProperties` đều được coi là file và được nạp. Xem "[File theo profile (Profile Specific Files)](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.external-config.files.profile-specific)" để biết chi tiết.

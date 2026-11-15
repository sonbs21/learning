# 18.8.4. Tách các định nghĩa @Entity khỏi cấu hình Spring (Separate @Entity Definitions from Spring Configuration)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.data-access.separate-entity-definitions-from-spring-configuration>

Spring Boot xác định vị trí của các định nghĩa `@Entity` bằng cách quét các [auto-configuration package](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#using.auto-configuration.packages). Để kiểm soát nhiều hơn, hãy dùng annotation `@EntityScan`, như trong ví dụ sau:

**Java**

```java
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableAutoConfiguration
@EntityScan(basePackageClasses = City.class)
public class MyApplication {

    // ...

}
```

**Kotlin**

```kotlin
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
@EnableAutoConfiguration
@EntityScan(basePackageClasses = [City::class])
class MyApplication {

    // ...

}
```

# 18.2.2. Đưa cấu hình của SpringApplication ra bên ngoài (Externalize the Configuration of SpringApplication)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.properties-and-configuration.externalize-configuration>

Một `SpringApplication` có các setter cho bean property, vì vậy bạn có thể dùng Java API của nó khi tạo ứng dụng để thay đổi hành vi của nó. Ngoài ra, bạn có thể đưa cấu hình ra bên ngoài bằng cách đặt các property trong `spring.main.*`. Ví dụ, trong `application.properties`, bạn có thể có các thiết lập sau:

**Properties**

```properties
spring.main.web-application-type=none
spring.main.banner-mode=off
```

**Yaml**

```yaml
spring:
  main:
    web-application-type: "none"
    banner-mode: "off"
```

Khi đó banner của Spring Boot không được in ra lúc khởi động, và ứng dụng không khởi động một embedded web server.

Các property được định nghĩa trong cấu hình bên ngoài sẽ ghi đè và thay thế các giá trị được chỉ định bằng Java API, với ngoại lệ đáng chú ý là các primary source. Primary source là những source được cung cấp cho constructor của `SpringApplication`:

**Java**

```java
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MyApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(MyApplication.class);
        application.setBannerMode(Banner.Mode.OFF);
        application.run(args);
    }

}
```

**Kotlin**

```kotlin
import org.springframework.boot.Banner
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication

@SpringBootApplication
object MyApplication {

    @JvmStatic
    fun main(args: Array<String>) {
        val application = SpringApplication(MyApplication::class.java)
        application.setBannerMode(Banner.Mode.OFF)
        application.run(*args)
    }

}
```

Hoặc cho phương thức `sources(…​)` của một `SpringApplicationBuilder`:

**Java**

```java
import org.springframework.boot.Banner;
import org.springframework.boot.builder.SpringApplicationBuilder;

public class MyApplication {

    public static void main(String[] args) {
        new SpringApplicationBuilder()
            .bannerMode(Banner.Mode.OFF)
            .sources(MyApplication.class)
            .run(args);
    }

}
```

**Kotlin**

```kotlin
import org.springframework.boot.Banner
import org.springframework.boot.builder.SpringApplicationBuilder

object MyApplication {

    @JvmStatic
    fun main(args: Array<String>) {
        SpringApplicationBuilder()
            .bannerMode(Banner.Mode.OFF)
            .sources(MyApplication::class.java)
            .run(*args)
    }

}
```

Với các ví dụ trên, nếu chúng ta có cấu hình sau:

**Properties**

```properties
spring.main.sources=com.example.MyDatabaseConfig,com.example.MyJmsConfig
spring.main.banner-mode=console
```

**Yaml**

```yaml
spring:
  main:
    sources: "com.example.MyDatabaseConfig,com.example.MyJmsConfig"
    banner-mode: "console"
```

Ứng dụng thực tế sẽ hiển thị banner (do bị cấu hình ghi đè) và dùng ba source cho `ApplicationContext`. Các source của ứng dụng là:

1. `MyApplication` (từ code)
2. `MyDatabaseConfig` (từ cấu hình bên ngoài)
3. `MyJmsConfig` (từ cấu hình bên ngoài)

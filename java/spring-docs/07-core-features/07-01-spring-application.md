# 7.1. SpringApplication

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.spring-application>

Class `SpringApplication` cung cấp một cách tiện lợi để khởi động (bootstrap) một ứng dụng Spring được khởi chạy từ phương thức `main()`. Trong nhiều trường hợp, bạn có thể ủy quyền cho phương thức tĩnh `SpringApplication.run`, như trong ví dụ sau:

**Java**

```java
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MyApplication {

    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }

}
```

**Kotlin**

```kotlin
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication


@SpringBootApplication
class MyApplication

fun main(args: Array<String>) {
    runApplication<MyApplication>(*args)
}
```

Khi ứng dụng khởi động, bạn sẽ thấy output tương tự như sau:

```
.   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_\__, | / / / /
 =========|_|==============|___/=/_/_/_/
 :: Spring Boot ::                (v3.2.5)

2024-04-18T17:04:11.851Z  INFO 13524 --- [           main] o.s.b.d.f.logexample.MyApplication       : Starting MyApplication using Java 17.0.11 with PID 13524 (/opt/apps/myapp.jar started by myuser in /opt/apps/)
2024-04-18T17:04:11.905Z  INFO 13524 --- [           main] o.s.b.d.f.logexample.MyApplication       : No active profile set, falling back to 1 default profile: "default"
2024-04-18T17:04:16.256Z  INFO 13524 --- [           main] o.s.b.w.embedded.tomcat.TomcatWebServer  : Tomcat initialized with port 8080 (http)
2024-04-18T17:04:16.322Z  INFO 13524 --- [           main] o.apache.catalina.core.StandardService   : Starting service [Tomcat]
2024-04-18T17:04:16.323Z  INFO 13524 --- [           main] o.apache.catalina.core.StandardEngine    : Starting Servlet engine: [Apache Tomcat/10.1.20]
2024-04-18T17:04:16.503Z  INFO 13524 --- [           main] o.a.c.c.C.[Tomcat].[localhost].[/]       : Initializing Spring embedded WebApplicationContext
2024-04-18T17:04:16.513Z  INFO 13524 --- [           main] w.s.c.ServletWebServerApplicationContext : Root WebApplicationContext: initialization completed in 4332 ms
2024-04-18T17:04:18.401Z  INFO 13524 --- [           main] o.s.b.w.embedded.tomcat.TomcatWebServer  : Tomcat started on port 8080 (http) with context path ''
2024-04-18T17:04:18.429Z  INFO 13524 --- [           main] o.s.b.d.f.logexample.MyApplication       : Started MyApplication in 8.19 seconds (process running for 9.711)
```

Theo mặc định, các thông điệp log mức `INFO` được hiển thị, bao gồm một số chi tiết khởi động liên quan, chẳng hạn như người dùng đã khởi chạy ứng dụng. Nếu bạn cần mức log khác `INFO`, bạn có thể đặt nó như mô tả trong [Mức log (Log Levels)](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.logging.log-levels). Phiên bản ứng dụng được xác định bằng implementation version từ package của class ứng dụng chính. Việc ghi log thông tin khởi động có thể được tắt bằng cách đặt `spring.main.log-startup-info` thành `false`. Việc này cũng sẽ tắt việc ghi log các profile đang hoạt động của ứng dụng.

> **MẸO (TIP):** Để thêm log trong quá trình khởi động, bạn có thể ghi đè `logStartupInfo(boolean)` trong một subclass của `SpringApplication`.

## 7.1.1. Khởi động thất bại (Startup Failure)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.spring-application.startup-failure>

Nếu ứng dụng của bạn khởi động thất bại, các `FailureAnalyzers` đã đăng ký sẽ có cơ hội cung cấp một thông báo lỗi chuyên biệt và một hành động cụ thể để khắc phục vấn đề. Ví dụ, nếu bạn khởi động một ứng dụng web trên cổng `8080` và cổng đó đã được sử dụng, bạn sẽ thấy thông báo tương tự như sau:

```
***************************
APPLICATION FAILED TO START
***************************

Description:

Embedded servlet container failed to start. Port 8080 was already in use.

Action:

Identify and stop the process that is listening on port 8080 or configure this application to listen on another port.
```

> **LƯU Ý (NOTE):** Spring Boot cung cấp rất nhiều implementation của `FailureAnalyzer`, và bạn có thể [thêm của riêng mình](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.application.failure-analyzer).

Nếu không có failure analyzer nào xử lý được exception, bạn vẫn có thể hiển thị báo cáo điều kiện (conditions report) đầy đủ để hiểu rõ hơn điều gì đã sai. Để làm vậy, bạn cần [bật property `debug`](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.external-config) hoặc [bật logging mức `DEBUG`](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.logging.log-levels) cho `org.springframework.boot.autoconfigure.logging.ConditionEvaluationReportLoggingListener`.

Ví dụ, nếu bạn chạy ứng dụng bằng `java -jar`, bạn có thể bật property `debug` như sau:

```shell
$ java -jar myproject-0.0.1-SNAPSHOT.jar --debug
```

## 7.1.2. Khởi tạo lười (Lazy Initialization)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.spring-application.lazy-initialization>

`SpringApplication` cho phép một ứng dụng được khởi tạo lười (lazily). Khi lazy initialization được bật, các bean được tạo khi chúng cần đến thay vì trong quá trình khởi động ứng dụng. Kết quả là, bật lazy initialization có thể giảm thời gian khởi động ứng dụng. Trong một ứng dụng web, bật lazy initialization sẽ khiến nhiều bean liên quan đến web không được khởi tạo cho đến khi nhận được một HTTP request.

Một nhược điểm của lazy initialization là nó có thể làm chậm việc phát hiện vấn đề của ứng dụng. Nếu một bean bị cấu hình sai được khởi tạo lười, lỗi sẽ không còn xảy ra trong quá trình khởi động nữa và vấn đề chỉ lộ ra khi bean được khởi tạo. Cũng cần cẩn thận để đảm bảo JVM có đủ bộ nhớ để chứa tất cả các bean của ứng dụng chứ không chỉ những bean được khởi tạo trong quá trình khởi động. Vì những lý do này, lazy initialization không được bật theo mặc định, và khuyến nghị nên tinh chỉnh kích thước heap của JVM trước khi bật lazy initialization.

Lazy initialization có thể được bật bằng code thông qua phương thức `lazyInitialization` trên `SpringApplicationBuilder` hoặc phương thức `setLazyInitialization` trên `SpringApplication`. Ngoài ra, nó có thể được bật bằng property `spring.main.lazy-initialization` như trong ví dụ sau:

**Properties**

```properties
spring.main.lazy-initialization=true
```

**Yaml**

```yaml
spring:
  main:
    lazy-initialization: true
```

> **MẸO (TIP):** Nếu bạn muốn tắt lazy initialization cho một số bean nhất định trong khi vẫn dùng lazy initialization cho phần còn lại của ứng dụng, bạn có thể đặt tường minh thuộc tính lazy của chúng thành false bằng annotation `@Lazy(false)`.

## 7.1.3. Tùy chỉnh banner (Customizing the Banner)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.spring-application.banner>

Banner được in ra khi khởi động có thể được thay đổi bằng cách thêm một file `banner.txt` vào classpath hoặc đặt property `spring.banner.location` thành vị trí của file như vậy. Nếu file có encoding khác UTF-8, bạn có thể đặt `spring.banner.charset`.

Bên trong file `banner.txt`, bạn có thể dùng bất kỳ key nào có trong `Environment` cũng như bất kỳ placeholder nào sau đây:

**Bảng 4. Các biến của banner**

| Biến | Mô tả |
|---|---|
| `${application.version}` | Số phiên bản ứng dụng của bạn, như được khai báo trong `MANIFEST.MF`. Ví dụ, `Implementation-Version: 1.0` được in ra là `1.0`. |
| `${application.formatted-version}` | Số phiên bản ứng dụng của bạn, như được khai báo trong `MANIFEST.MF` và được định dạng để hiển thị (bao trong ngoặc đơn và có tiền tố `v`). Ví dụ `(v1.0)`. |
| `${spring-boot.version}` | Phiên bản Spring Boot bạn đang dùng. Ví dụ `3.2.5`. |
| `${spring-boot.formatted-version}` | Phiên bản Spring Boot bạn đang dùng, được định dạng để hiển thị (bao trong ngoặc đơn và có tiền tố `v`). Ví dụ `(v3.2.5)`. |
| `${Ansi.NAME}` (hoặc `${AnsiColor.NAME}`, `${AnsiBackground.NAME}`, `${AnsiStyle.NAME}`) | Trong đó `NAME` là tên của một mã ANSI escape. Xem [`AnsiPropertySource`](https://github.com/spring-projects/spring-boot/tree/v3.2.5/spring-boot-project/spring-boot/src/main/java/org/springframework/boot/ansi/AnsiPropertySource.java) để biết chi tiết. |
| `${application.title}` | Tiêu đề ứng dụng của bạn, như được khai báo trong `MANIFEST.MF`. Ví dụ `Implementation-Title: MyApp` được in ra là `MyApp`. |

> **MẸO (TIP):** Phương thức `SpringApplication.setBanner(…)` có thể được dùng nếu bạn muốn sinh banner bằng code. Hãy dùng interface `org.springframework.boot.Banner` và hiện thực phương thức `printBanner()` của riêng bạn.

Bạn cũng có thể dùng property `spring.main.banner-mode` để xác định banner sẽ được in ra `System.out` (`console`), gửi tới logger đã cấu hình (`log`), hay không được tạo ra (`off`).

Banner được in ra sẽ được đăng ký thành một singleton bean với tên: `springBootBanner`.

> **LƯU Ý (NOTE):** Các property `application.title`, `application.version`, và `application.formatted-version` chỉ có sẵn nếu bạn dùng `java -jar` hoặc `java -cp` với các launcher của Spring Boot. Các giá trị sẽ không được phân giải nếu bạn chạy một jar đã giải nén và khởi động nó bằng `java -cp <classpath> <mainclass>` hoặc chạy ứng dụng dưới dạng native image.
>
> Để dùng các property `application.*`, hãy khởi chạy ứng dụng dưới dạng jar đóng gói bằng `java -jar` hoặc dưới dạng jar đã giải nén bằng `java org.springframework.boot.loader.launch.JarLauncher`. Việc này sẽ khởi tạo các property banner `application.*` trước khi xây dựng classpath và khởi chạy ứng dụng.

## 7.1.4. Tùy chỉnh SpringApplication (Customizing SpringApplication)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.spring-application.customizing-spring-application>

Nếu các giá trị mặc định của `SpringApplication` không hợp với bạn, bạn có thể tạo một instance cục bộ và tùy chỉnh nó. Ví dụ, để tắt banner, bạn có thể viết:

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
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class MyApplication

fun main(args: Array<String>) {
    runApplication<MyApplication>(*args) {
        setBannerMode(Banner.Mode.OFF)
    }
}
```

> **LƯU Ý (NOTE):** Các tham số constructor được truyền vào `SpringApplication` là các nguồn cấu hình cho Spring bean. Trong hầu hết các trường hợp, đây là tham chiếu tới các class `@Configuration`, nhưng chúng cũng có thể là tham chiếu trực tiếp tới các class `@Component`.

Cũng có thể cấu hình `SpringApplication` bằng file `application.properties`. Xem *[Cấu hình bên ngoài (Externalized Configuration)](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.external-config)* để biết chi tiết.

Để có danh sách đầy đủ các tùy chọn cấu hình, hãy xem [Javadoc của `SpringApplication`](https://docs.spring.io/spring-boot/docs/3.2.5/api/org/springframework/boot/SpringApplication.html).

## 7.1.5. Fluent Builder API

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.spring-application.fluent-builder-api>

Nếu bạn cần xây dựng một cây phân cấp `ApplicationContext` (nhiều context có quan hệ cha/con) hoặc nếu bạn thích dùng builder API kiểu "fluent", bạn có thể dùng `SpringApplicationBuilder`.

`SpringApplicationBuilder` cho phép bạn nối chuỗi nhiều lời gọi phương thức và bao gồm các phương thức `parent` và `child` cho phép bạn tạo cây phân cấp, như trong ví dụ sau:

**Java**

```java
new SpringApplicationBuilder().sources(Parent.class)
    .child(Application.class)
    .bannerMode(Banner.Mode.OFF)
    .run(args);
```

**Kotlin**

```kotlin
SpringApplicationBuilder()
    .sources(Parent::class.java)
    .child(Application::class.java)
    .bannerMode(Banner.Mode.OFF)
    .run(*args)
```

> **LƯU Ý (NOTE):** Có một số hạn chế khi tạo cây phân cấp `ApplicationContext`. Ví dụ, các Web component **phải** nằm trong context con, và cùng một `Environment` được dùng cho cả context cha và context con. Xem [Javadoc của `SpringApplicationBuilder`](https://docs.spring.io/spring-boot/docs/3.2.5/api/org/springframework/boot/builder/SpringApplicationBuilder.html) để biết đầy đủ chi tiết.

## 7.1.6. Tính sẵn sàng của ứng dụng (Application Availability)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.spring-application.application-availability>

Khi được triển khai trên các nền tảng, ứng dụng có thể cung cấp thông tin về tính sẵn sàng (availability) của chúng cho nền tảng bằng cách dùng hạ tầng như [Kubernetes Probes](https://kubernetes.io/docs/tasks/configure-pod-container/configure-liveness-readiness-startup-probes/). Spring Boot có sẵn hỗ trợ cho các trạng thái sẵn sàng phổ biến là "liveness" và "readiness". Nếu bạn đang dùng hỗ trợ "actuator" của Spring Boot thì các trạng thái này được expose dưới dạng các nhóm health endpoint.

Ngoài ra, bạn cũng có thể lấy các trạng thái sẵn sàng bằng cách inject interface `ApplicationAvailability` vào các bean của riêng mình.

### Trạng thái Liveness (Liveness State)

Trạng thái "Liveness" của ứng dụng cho biết trạng thái nội bộ của nó có cho phép nó hoạt động đúng, hoặc tự phục hồi nếu hiện đang gặp lỗi hay không. Trạng thái "Liveness" bị hỏng nghĩa là ứng dụng đang ở trạng thái không thể phục hồi, và hạ tầng nên khởi động lại ứng dụng.

> **LƯU Ý (NOTE):** Nhìn chung, trạng thái "Liveness" không nên dựa trên các kiểm tra bên ngoài, chẳng hạn như [Health check](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#actuator.endpoints.health). Nếu làm vậy, một hệ thống bên ngoài bị lỗi (database, Web API, cache bên ngoài) sẽ kích hoạt hàng loạt lần khởi động lại và lỗi dây chuyền trên toàn nền tảng.

Trạng thái nội bộ của ứng dụng Spring Boot phần lớn được đại diện bởi `ApplicationContext` của Spring. Nếu application context đã khởi động thành công, Spring Boot giả định rằng ứng dụng đang ở trạng thái hợp lệ. Một ứng dụng được coi là live ngay khi context đã được refresh, xem [Vòng đời ứng dụng Spring Boot và các Application Event liên quan](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.spring-application.application-events-and-listeners).

### Trạng thái Readiness (Readiness State)

Trạng thái "Readiness" của ứng dụng cho biết ứng dụng đã sẵn sàng xử lý lưu lượng (traffic) hay chưa. Trạng thái "Readiness" thất bại cho nền tảng biết rằng tạm thời không nên định tuyến traffic tới ứng dụng. Điều này thường xảy ra trong quá trình khởi động, khi các component `CommandLineRunner` và `ApplicationRunner` đang được xử lý, hoặc vào bất kỳ lúc nào nếu ứng dụng quyết định rằng nó quá bận để nhận thêm traffic.

Một ứng dụng được coi là sẵn sàng ngay khi các application runner và command-line runner đã được gọi, xem [Vòng đời ứng dụng Spring Boot và các Application Event liên quan](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.spring-application.application-events-and-listeners).

> **MẸO (TIP):** Các tác vụ dự kiến chạy trong quá trình khởi động nên được thực thi bởi các component `CommandLineRunner` và `ApplicationRunner` thay vì dùng các callback vòng đời của Spring component như `@PostConstruct`.

### Quản lý trạng thái sẵn sàng của ứng dụng (Managing the Application Availability State)

Các component của ứng dụng có thể lấy trạng thái sẵn sàng hiện tại vào bất kỳ lúc nào, bằng cách inject interface `ApplicationAvailability` và gọi các phương thức trên nó. Thường gặp hơn là ứng dụng muốn lắng nghe các cập nhật trạng thái hoặc cập nhật trạng thái của ứng dụng.

Ví dụ, chúng ta có thể xuất trạng thái "Readiness" của ứng dụng ra một file để một "exec Probe" của Kubernetes có thể xem file này:

**Java**

```java
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class MyReadinessStateExporter {

    @EventListener
    public void onStateChange(AvailabilityChangeEvent<ReadinessState> event) {
        switch (event.getState()) {
            case ACCEPTING_TRAFFIC -> {
                // create file /tmp/healthy
            }
            case REFUSING_TRAFFIC -> {
                // remove file /tmp/healthy
            }
        }
    }

}
```

**Kotlin**

```kotlin
import org.springframework.boot.availability.AvailabilityChangeEvent
import org.springframework.boot.availability.ReadinessState
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class MyReadinessStateExporter {

    @EventListener
    fun onStateChange(event: AvailabilityChangeEvent<ReadinessState?>) {
        when (event.state) {
            ReadinessState.ACCEPTING_TRAFFIC -> {
                // create file /tmp/healthy
            }
            ReadinessState.REFUSING_TRAFFIC -> {
                // remove file /tmp/healthy
            }
            else -> {
                // ...
            }
        }
    }

}
```

Chúng ta cũng có thể cập nhật trạng thái của ứng dụng khi ứng dụng bị hỏng và không thể phục hồi:

**Java**

```java
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.LivenessState;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class MyLocalCacheVerifier {

    private final ApplicationEventPublisher eventPublisher;

    public MyLocalCacheVerifier(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    public void checkLocalCache() {
        try {
            // ...
        }
        catch (CacheCompletelyBrokenException ex) {
            AvailabilityChangeEvent.publish(this.eventPublisher, ex, LivenessState.BROKEN);
        }
    }

}
```

**Kotlin**

```kotlin
import org.springframework.boot.availability.AvailabilityChangeEvent
import org.springframework.boot.availability.LivenessState
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

@Component
class MyLocalCacheVerifier(private val eventPublisher: ApplicationEventPublisher) {

    fun checkLocalCache() {
        try {
            // ...
        } catch (ex: CacheCompletelyBrokenException) {
            AvailabilityChangeEvent.publish(eventPublisher, ex, LivenessState.BROKEN)
        }
    }

}
```

Spring Boot cung cấp [các HTTP probe Kubernetes cho "Liveness" và "Readiness" với Actuator Health Endpoint](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#actuator.endpoints.kubernetes-probes). Bạn có thể xem thêm hướng dẫn về [triển khai ứng dụng Spring Boot trên Kubernetes trong phần chuyên biệt](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#deployment.cloud.kubernetes).

## 7.1.7. Application Event và Listener (Application Events and Listeners)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.spring-application.application-events-and-listeners>

Ngoài các event thông thường của Spring Framework, chẳng hạn như [`ContextRefreshedEvent`](https://docs.spring.io/spring-framework/docs/6.1.6/javadoc-api/org/springframework/context/event/ContextRefreshedEvent.html), `SpringApplication` còn gửi thêm một số application event.

> **LƯU Ý (NOTE):** Một số event thực ra được kích hoạt trước khi `ApplicationContext` được tạo, nên bạn không thể đăng ký listener cho chúng dưới dạng `@Bean`. Bạn có thể đăng ký chúng bằng phương thức `SpringApplication.addListeners(…)` hoặc phương thức `SpringApplicationBuilder.listeners(…)`.
>
> Nếu bạn muốn các listener đó được đăng ký tự động, bất kể ứng dụng được tạo theo cách nào, bạn có thể thêm file `META-INF/spring.factories` vào project và tham chiếu tới (các) listener bằng key `org.springframework.context.ApplicationListener`, như trong ví dụ sau:

```
org.springframework.context.ApplicationListener=com.example.project.MyListener
```

Các application event được gửi theo thứ tự sau, khi ứng dụng của bạn chạy:

1. Một `ApplicationStartingEvent` được gửi khi bắt đầu chạy nhưng trước bất kỳ xử lý nào, ngoại trừ việc đăng ký các listener và initializer.
2. Một `ApplicationEnvironmentPreparedEvent` được gửi khi đã biết `Environment` sẽ được dùng trong context nhưng trước khi context được tạo.
3. Một `ApplicationContextInitializedEvent` được gửi khi `ApplicationContext` đã được chuẩn bị và các ApplicationContextInitializer đã được gọi nhưng trước khi bất kỳ bean definition nào được nạp.
4. Một `ApplicationPreparedEvent` được gửi ngay trước khi bắt đầu refresh nhưng sau khi các bean definition đã được nạp.
5. Một `ApplicationStartedEvent` được gửi sau khi context đã được refresh nhưng trước khi bất kỳ application runner và command-line runner nào được gọi.
6. Một `AvailabilityChangeEvent` được gửi ngay sau đó với `LivenessState.CORRECT` để cho biết ứng dụng được coi là live.
7. Một `ApplicationReadyEvent` được gửi sau khi tất cả [application runner và command-line runner](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.spring-application.command-line-runner) đã được gọi.
8. Một `AvailabilityChangeEvent` được gửi ngay sau đó với `ReadinessState.ACCEPTING_TRAFFIC` để cho biết ứng dụng đã sẵn sàng phục vụ request.
9. Một `ApplicationFailedEvent` được gửi nếu có exception khi khởi động.

Danh sách trên chỉ bao gồm các `SpringApplicationEvent` gắn với một `SpringApplication`. Ngoài những event này, các event sau cũng được publish sau `ApplicationPreparedEvent` và trước `ApplicationStartedEvent`:

- Một `WebServerInitializedEvent` được gửi sau khi `WebServer` đã sẵn sàng. `ServletWebServerInitializedEvent` và `ReactiveWebServerInitializedEvent` lần lượt là các biến thể cho servlet và reactive.
- Một `ContextRefreshedEvent` được gửi khi một `ApplicationContext` được refresh.

> **MẸO (TIP):** Bạn thường không cần dùng application event, nhưng biết rằng chúng tồn tại có thể sẽ hữu ích. Bên trong, Spring Boot dùng event để xử lý nhiều loại tác vụ khác nhau.

> **LƯU Ý (NOTE):** Các event listener không nên chạy các tác vụ có thể tốn nhiều thời gian, vì theo mặc định chúng thực thi trong cùng một thread. Hãy cân nhắc dùng [application runner và command-line runner](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.spring-application.command-line-runner) thay thế.

Các application event được gửi bằng cơ chế publish event của Spring Framework. Một phần của cơ chế này đảm bảo rằng một event được publish tới các listener trong context con cũng được publish tới các listener trong mọi context tổ tiên. Kết quả là, nếu ứng dụng của bạn dùng một cây phân cấp các instance `SpringApplication`, một listener có thể nhận được nhiều instance của cùng một loại application event.

Để cho phép listener phân biệt giữa event cho context của nó và event cho một context con cháu, nó nên yêu cầu application context của mình được inject vào rồi so sánh context được inject với context của event. Context có thể được inject bằng cách hiện thực `ApplicationContextAware` hoặc, nếu listener là một bean, bằng cách dùng `@Autowired`.

## 7.1.8. Môi trường web (Web Environment)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.spring-application.web-environment>

`SpringApplication` cố gắng tạo đúng loại `ApplicationContext` thay cho bạn. Thuật toán dùng để xác định `WebApplicationType` như sau:

- Nếu có Spring MVC, `AnnotationConfigServletWebServerApplicationContext` được sử dụng
- Nếu không có Spring MVC và có Spring WebFlux, `AnnotationConfigReactiveWebServerApplicationContext` được sử dụng
- Nếu không, `AnnotationConfigApplicationContext` được sử dụng

Điều này có nghĩa là nếu bạn dùng Spring MVC và `WebClient` mới từ Spring WebFlux trong cùng một ứng dụng, Spring MVC sẽ được dùng theo mặc định. Bạn có thể dễ dàng ghi đè điều đó bằng cách gọi `setWebApplicationType(WebApplicationType)`.

Cũng có thể kiểm soát hoàn toàn loại `ApplicationContext` được sử dụng bằng cách gọi `setApplicationContextFactory(…)`.

> **MẸO (TIP):** Thường nên gọi `setWebApplicationType(WebApplicationType.NONE)` khi dùng `SpringApplication` trong một JUnit test.

## 7.1.9. Truy cập tham số ứng dụng (Accessing Application Arguments)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.spring-application.application-arguments>

Nếu bạn cần truy cập các tham số ứng dụng đã được truyền vào `SpringApplication.run(…)`, bạn có thể inject một bean `org.springframework.boot.ApplicationArguments`. Interface `ApplicationArguments` cung cấp quyền truy cập cả các tham số `String[]` thô lẫn các tham số `option` và `non-option` đã được phân tích, như trong ví dụ sau:

**Java**

```java
import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.stereotype.Component;

@Component
public class MyBean {

    public MyBean(ApplicationArguments args) {
        boolean debug = args.containsOption("debug");
        List<String> files = args.getNonOptionArgs();
        if (debug) {
            System.out.println(files);
        }
        // if run with "--debug logfile.txt" prints ["logfile.txt"]
    }

}
```

**Kotlin**

```kotlin
import org.springframework.boot.ApplicationArguments
import org.springframework.stereotype.Component

@Component
class MyBean(args: ApplicationArguments) {

    init {
        val debug = args.containsOption("debug")
        val files = args.nonOptionArgs
        if (debug) {
            println(files)
        }
        // if run with "--debug logfile.txt" prints ["logfile.txt"]
    }

}
```

> **MẸO (TIP):** Spring Boot cũng đăng ký một `CommandLinePropertySource` với `Environment` của Spring. Điều này cho phép bạn inject từng tham số ứng dụng riêng lẻ bằng annotation `@Value`.

## 7.1.10. Sử dụng ApplicationRunner hoặc CommandLineRunner (Using the ApplicationRunner or CommandLineRunner)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.spring-application.command-line-runner>

Nếu bạn cần chạy một đoạn code cụ thể sau khi `SpringApplication` đã khởi động, bạn có thể hiện thực interface `ApplicationRunner` hoặc `CommandLineRunner`. Cả hai interface hoạt động theo cùng một cách và cung cấp một phương thức `run` duy nhất, được gọi ngay trước khi `SpringApplication.run(…)` hoàn tất.

> **LƯU Ý (NOTE):** Hợp đồng (contract) này rất phù hợp cho các tác vụ cần chạy sau khi ứng dụng khởi động nhưng trước khi nó bắt đầu nhận traffic.

Interface `CommandLineRunner` cung cấp quyền truy cập các tham số ứng dụng dưới dạng một mảng chuỗi, trong khi `ApplicationRunner` dùng interface `ApplicationArguments` đã đề cập ở trên. Ví dụ sau cho thấy một `CommandLineRunner` với phương thức `run`:

**Java**

```java
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class MyCommandLineRunner implements CommandLineRunner {

    @Override
    public void run(String... args) {
        // Do something...
    }

}
```

**Kotlin**

```kotlin
import org.springframework.boot.CommandLineRunner
import org.springframework.stereotype.Component

@Component
class MyCommandLineRunner : CommandLineRunner {

    override fun run(vararg args: String) {
        // Do something...
    }

}
```

Nếu có nhiều bean `CommandLineRunner` hoặc `ApplicationRunner` được định nghĩa và phải được gọi theo một thứ tự cụ thể, bạn có thể hiện thực thêm interface `org.springframework.core.Ordered` hoặc dùng annotation `org.springframework.core.annotation.Order`.

## 7.1.11. Thoát ứng dụng (Application Exit)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.spring-application.application-exit>

Mỗi `SpringApplication` đăng ký một shutdown hook với JVM để đảm bảo `ApplicationContext` đóng lại một cách nhẹ nhàng (gracefully) khi thoát. Tất cả các callback vòng đời tiêu chuẩn của Spring (chẳng hạn như interface `DisposableBean` hoặc annotation `@PreDestroy`) đều có thể được sử dụng.

Ngoài ra, các bean có thể hiện thực interface `org.springframework.boot.ExitCodeGenerator` nếu chúng muốn trả về một exit code cụ thể khi `SpringApplication.exit()` được gọi. Exit code này sau đó có thể được truyền vào `System.exit()` để trả về nó như một mã trạng thái (status code), như trong ví dụ sau:

**Java**

```java
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class MyApplication {

    @Bean
    public ExitCodeGenerator exitCodeGenerator() {
        return () -> 42;
    }

    public static void main(String[] args) {
        System.exit(SpringApplication.exit(SpringApplication.run(MyApplication.class, args)));
    }

}
```

**Kotlin**

```kotlin
import org.springframework.boot.ExitCodeGenerator
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean

import kotlin.system.exitProcess

@SpringBootApplication
class MyApplication {

    @Bean
    fun exitCodeGenerator() = ExitCodeGenerator { 42 }

}

fun main(args: Array<String>) {
    exitProcess(SpringApplication.exit(
        runApplication<MyApplication>(*args)))
}
```

Ngoài ra, interface `ExitCodeGenerator` cũng có thể được hiện thực bởi các exception. Khi gặp một exception như vậy, Spring Boot trả về exit code được cung cấp bởi phương thức `getExitCode()` đã hiện thực.

Nếu có nhiều hơn một `ExitCodeGenerator`, exit code khác 0 đầu tiên được sinh ra sẽ được sử dụng. Để kiểm soát thứ tự gọi các generator, hãy hiện thực thêm interface `org.springframework.core.Ordered` hoặc dùng annotation `org.springframework.core.annotation.Order`.

## 7.1.12. Các tính năng quản trị (Admin Features)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.spring-application.admin>

Có thể bật các tính năng liên quan đến quản trị (admin) cho ứng dụng bằng cách chỉ định property `spring.application.admin.enabled`. Việc này expose [`SpringApplicationAdminMXBean`](https://github.com/spring-projects/spring-boot/tree/v3.2.5/spring-boot-project/spring-boot/src/main/java/org/springframework/boot/admin/SpringApplicationAdminMXBean.java) trên `MBeanServer` của nền tảng. Bạn có thể dùng tính năng này để quản trị ứng dụng Spring Boot từ xa. Tính năng này cũng có thể hữu ích cho bất kỳ implementation service wrapper nào.

> **MẸO (TIP):** Nếu bạn muốn biết ứng dụng đang chạy trên cổng HTTP nào, hãy lấy property có key `local.server.port`.

## 7.1.13. Theo dõi quá trình khởi động ứng dụng (Application Startup tracking)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.spring-application.startup-tracking>

Trong quá trình khởi động ứng dụng, `SpringApplication` và `ApplicationContext` thực hiện nhiều tác vụ liên quan đến vòng đời ứng dụng, vòng đời của bean hoặc thậm chí xử lý các application event. Với [`ApplicationStartup`](https://docs.spring.io/spring-framework/docs/6.1.6/javadoc-api/org/springframework/core/metrics/ApplicationStartup.html), Spring Framework [cho phép bạn theo dõi trình tự khởi động ứng dụng bằng các object `StartupStep`](https://docs.spring.io/spring-framework/reference/6.1/core/beans/context-introduction.html#context-functionality-startup). Dữ liệu này có thể được thu thập cho mục đích profiling, hoặc chỉ để hiểu rõ hơn về quá trình khởi động của ứng dụng.

Bạn có thể chọn một implementation `ApplicationStartup` khi thiết lập instance `SpringApplication`. Ví dụ, để dùng `BufferingApplicationStartup`, bạn có thể viết:

**Java**

```java
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.metrics.buffering.BufferingApplicationStartup;

@SpringBootApplication
public class MyApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(MyApplication.class);
        application.setApplicationStartup(new BufferingApplicationStartup(2048));
        application.run(args);
    }

}
```

**Kotlin**

```kotlin
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.metrics.buffering.BufferingApplicationStartup
import org.springframework.boot.runApplication

@SpringBootApplication
class MyApplication

fun main(args: Array<String>) {
    runApplication<MyApplication>(*args) {
        applicationStartup = BufferingApplicationStartup(2048)
    }
}
```

Implementation đầu tiên có sẵn, `FlightRecorderApplicationStartup`, được cung cấp bởi Spring Framework. Nó thêm các event khởi động đặc thù của Spring vào một phiên Java Flight Recorder và dùng để profiling ứng dụng và đối chiếu vòng đời Spring context của chúng với các event của JVM (như cấp phát bộ nhớ, GC, nạp class…). Sau khi cấu hình, bạn có thể ghi dữ liệu bằng cách chạy ứng dụng với Flight Recorder được bật:

```shell
$ java -XX:StartFlightRecording:filename=recording.jfr,duration=10s -jar demo.jar
```

Spring Boot đi kèm biến thể `BufferingApplicationStartup`; implementation này dùng để đệm (buffer) các bước khởi động và đẩy chúng vào một hệ thống metrics bên ngoài. Ứng dụng có thể yêu cầu bean kiểu `BufferingApplicationStartup` trong bất kỳ component nào.

Spring Boot cũng có thể được cấu hình để expose một [endpoint `startup`](https://docs.spring.io/spring-boot/docs/3.2.5/actuator-api/htmlsingle/#startup) cung cấp thông tin này dưới dạng một tài liệu JSON.

## 7.1.14. Virtual thread (Virtual threads)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.spring-application.virtual-threads>

Nếu bạn đang chạy trên Java 21 trở lên, bạn có thể bật virtual thread bằng cách đặt property `spring.threads.virtual.enabled` thành `true`.

Trước khi bật tùy chọn này cho ứng dụng, bạn nên cân nhắc [đọc tài liệu chính thức về virtual thread của Java](https://docs.oracle.com/en/java/javase/21/core/virtual-threads.html). Trong một số trường hợp, ứng dụng có thể gặp throughput thấp hơn do "Pinned Virtual Threads"; trang này cũng giải thích cách phát hiện những trường hợp như vậy bằng JDK Flight Recorder hoặc CLI `jcmd`.

> **CẢNH BÁO (WARNING):** Một tác dụng phụ của virtual thread là chúng là daemon thread. JVM sẽ thoát nếu tất cả các thread của nó đều là daemon thread. Hành vi này có thể là vấn đề khi bạn dựa vào các bean `@Scheduled`, chẳng hạn, để giữ cho ứng dụng tiếp tục chạy. Nếu bạn dùng virtual thread, thread của scheduler là một virtual thread và do đó là một daemon thread, và sẽ không giữ cho JVM tiếp tục chạy. Điều này không chỉ ảnh hưởng đến việc lập lịch mà cũng có thể xảy ra với các công nghệ khác. Để giữ JVM chạy trong mọi trường hợp, khuyến nghị đặt property `spring.main.keep-alive` thành `true`. Việc này đảm bảo JVM được giữ chạy, ngay cả khi tất cả các thread đều là virtual thread.

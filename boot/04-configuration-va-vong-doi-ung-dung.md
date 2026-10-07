# Tuần 4 — Configuration và vòng đời ứng dụng Spring Boot

> Stack đề xuất: Java 21, Maven, Spring Boot 4.x. Bài này tiếp nối Task API in-memory ở Tuần 3.

## 1. Mục tiêu bài học

Sau tuần này, bạn có thể:

- Tổ chức cấu hình trong `application.yml` và các file theo profile.
- Hiểu cấu hình được nạp từ đâu và nguồn nào được ưu tiên khi trùng key.
- Override cấu hình bằng environment variable và command-line argument.
- Bind cấu hình có kiểu rõ ràng bằng `@ConfigurationProperties`.
- Validate cấu hình để ứng dụng fail fast khi khởi động.
- Tách cấu hình `local`, `test` và `prod` mà không hardcode môi trường.
- Dùng SLF4J để log đúng level, có context và không làm lộ dữ liệu nhạy cảm.
- Hiểu DevTools, embedded server và executable JAR.
- Mô tả lifecycle của toàn ứng dụng Spring Boot.
- Chọn đúng giữa constructor, `@PostConstruct`, runner và application event.
- Cấu hình graceful shutdown và dọn tài nguyên đúng cách.

---

## 2. Bức tranh tổng thể

Ứng dụng không nên hardcode các giá trị thay đổi theo môi trường:

```java
// Không nên làm
String baseUrl = "https://api.production.example";
int maxItems = 100;
```

Thay vào đó, code chỉ khai báo cấu hình nó cần:

```java
@ConfigurationProperties(prefix = "app.task")
public record TaskProperties(int maxItems) {
}
```

Giá trị thực tế đến từ bên ngoài:

```yaml
app:
  task:
    max-items: 100
```

Khi deploy, cùng một JAR có thể chạy ở nhiều môi trường:

```text
Một artifact
    ├── local: cấu hình dành cho máy developer
    ├── test: cấu hình cô lập, xác định được
    └── prod: cấu hình production + secrets từ môi trường
```

Nguyên tắc quan trọng:

```text
Build once → configure at runtime → run the same artifact everywhere
```

### Dependency dùng trong bài

Project tiếp nối tuần 3 cần Spring Web MVC, Validation và Actuator. Với Spring Boot 4.x, kiểm tra `pom.xml` có các dependency chính:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webmvc</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

Trong Spring Initializr, Web MVC starter được hiển thị với tên **Spring Web**. Actuator cung cấp các endpoint như `/actuator/health` được dùng trong bài.

---

## 3. Externalized Configuration là gì?

**Externalized configuration** là việc đưa các giá trị thay đổi theo môi trường ra khỏi business code và artifact nguồn.

Ví dụ các giá trị nên cấu hình:

- Port của server.
- URL hệ thống bên ngoài.
- Timeout.
- Connection pool size.
- Feature flag.
- Logging level.
- Giới hạn nghiệp vụ có tính vận hành.
- Database URL và username.
- Secret reference hoặc credential được cấp lúc runtime.

Không nên dùng configuration cho mọi thứ. Quy tắc nghiệp vụ cố định và tên domain rõ ràng nên nằm trong code thay vì trở thành một “túi cấu hình” không kiểm soát.

### 3.1. Spring Environment

Spring gom các nguồn cấu hình vào abstraction `Environment`:

```java
@Component
public class EnvironmentExample {
    private final Environment environment;

    public EnvironmentExample(Environment environment) {
        this.environment = environment;
    }

    public int serverPort() {
        return environment.getProperty("server.port", Integer.class, 8080);
    }
}
```

Đây là cách hợp lệ nhưng không nên dùng cho nhóm cấu hình nghiệp vụ lớn. `@ConfigurationProperties` cung cấp type safety, validation và cấu trúc tốt hơn.

---

## 4. `application.properties` hay `application.yml`?

Hai định dạng đều được hỗ trợ.

Properties:

```properties
spring.application.name=task-api
server.port=8080
app.task.max-items=100
```

YAML:

```yaml
spring:
  application:
    name: task-api

server:
  port: 8080

app:
  task:
    max-items: 100
```

YAML dễ đọc với cấu hình phân cấp. Properties đơn giản và ít nhạy với indentation. Chọn một style chính cho project để giảm nhầm lẫn.

### Quy tắc YAML cơ bản

- Dùng space, không dùng tab.
- Giữ indentation nhất quán, thường là hai space.
- Không lặp key trong cùng object.
- Quote giá trị có thể bị YAML diễn giải sai.
- Không trộn `application.properties` và `application.yml` cùng location nếu không có lý do rõ ràng.

Ví dụ:

```yaml
app:
  client:
    base-url: "https://api.example.com"
    connect-timeout: 2s
    read-timeout: 5s
    allowed-statuses:
      - OPEN
      - DONE
```

Spring Boot có thể bind duration như `500ms`, `2s`, `1m` và data size như `10MB` vào các kiểu tương ứng.

---

## 5. Thứ tự ưu tiên của các nguồn cấu hình

Khi cùng một property xuất hiện ở nhiều nguồn, nguồn có ưu tiên cao hơn sẽ override nguồn thấp hơn.

Thứ tự rút gọn từ thấp đến cao cho ứng dụng chạy bình thường:

```text
Default properties trong SpringApplication
    ↓
@PropertySource
    ↓
Config data: application.yml / application-{profile}.yml
    ↓
RandomValuePropertySource
    ↓
OS environment variables
    ↓
Java system properties (-D...)
    ↓
JNDI / servlet init parameters
    ↓
SPRING_APPLICATION_JSON
    ↓
Command-line arguments (--...)
```

Các annotation/property source dành cho test còn có mức ưu tiên riêng và có thể override cấu hình ứng dụng.

`@PropertySource` được thêm trong quá trình refresh context nên quá muộn đối với một số property được đọc rất sớm, chẳng hạn `logging.*` và `spring.main.*`. Với ứng dụng Boot, ưu tiên config data, environment variable hoặc command-line option cho những thiết lập này.

Trong nhóm config data, cấu hình bên ngoài JAR và cấu hình theo profile thường override cấu hình đóng gói trong JAR. Hãy đọc precedence như một hợp đồng vận hành: default hợp lý nằm trong artifact, còn môi trường deploy override phần cần thay đổi.

Ví dụ:

`application.yml`:

```yaml
server:
  port: 8080
```

Chạy:

```bash
java -jar task-api.jar --server.port=9090
```

Ứng dụng chạy ở port `9090` vì command-line argument có ưu tiên cao hơn file.

### Không lạm dụng precedence

Nếu một property bị định nghĩa ở năm nơi, việc debug sẽ rất khó. Nên có quy ước:

- Default không nhạy cảm: `application.yml`.
- Khác biệt môi trường không nhạy cảm: file profile hoặc deployment config.
- Secrets: secret manager/environment do nền tảng cấp.
- Override tạm thời: command-line argument, chỉ khi có chủ đích.

---

## 6. Environment variable

### 6.1. Dùng placeholder rõ ràng

`application.yml`:

```yaml
app:
  integration:
    base-url: ${TASK_API_BASE_URL:http://localhost:9090}
    api-key: ${TASK_API_KEY:}
```

Cú pháp:

```text
${ENVIRONMENT_VARIABLE:default-value}
```

Chạy:

```bash
TASK_API_BASE_URL=https://tasks.example.com \
TASK_API_KEY=replace-at-runtime \
java -jar target/task-api.jar
```

Không đặt default thật cho secret. Default rỗng chỉ nên đi kèm validation khiến production fail fast nếu thiếu.

### 6.2. Relaxed binding của environment variable

Spring Boot có thể ánh xạ property name sang environment variable bằng quy tắc tổng quát:

1. Thay dấu `.` bằng `_`.
2. Xóa dấu `-`.
3. Chuyển thành chữ hoa.

Ví dụ:

```text
server.port              → SERVER_PORT
spring.profiles.active   → SPRING_PROFILES_ACTIVE
app.task.max-items       → APP_TASK_MAXITEMS
```

Với property tùy chỉnh dài hoặc dễ nhầm, placeholder có tên biến rõ ràng thường dễ vận hành hơn.

### 6.3. `.env` không tự động là cấu hình Spring Boot

Spring Boot không mặc định đọc file `.env` theo convention như một số framework khác. IDE, Docker Compose hoặc shell có thể nạp `.env` thành environment variables, nhưng đó là hành vi của công cụ ngoài Spring Boot.

Không commit file chứa secret. Nếu cần template, dùng file như `.env.example` chỉ chứa tên biến và giá trị giả.

---

## 7. Type-safe configuration với `@ConfigurationProperties`

### 7.1. Tạo configuration record

```java
package com.example.taskapi.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.task")
public record TaskProperties(
        @Min(1)
        @Max(10_000)
        int maxItems,

        @NotNull
        Duration cleanupInterval,

        @Valid
        @NotNull
        Client client
) {
    public record Client(
            @NotNull URI baseUrl,
            @NotNull Duration connectTimeout,
            @NotNull Duration readTimeout
    ) {
    }
}
```

Đăng ký scanning:

```java
@SpringBootApplication
@ConfigurationPropertiesScan
public class TaskApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(TaskApiApplication.class, args);
    }
}
```

Cấu hình tương ứng:

```yaml
app:
  task:
    max-items: 100
    cleanup-interval: 10m
    client:
      base-url: "http://localhost:9090"
      connect-timeout: 2s
      read-timeout: 5s
```

### 7.2. Vì sao tốt hơn nhiều `@Value`?

`@Value` phù hợp với một giá trị nhỏ, độc lập:

```java
@Value("${spring.application.name}")
private String applicationName;
```

Nhưng với một nhóm cấu hình, `@ConfigurationProperties` tốt hơn vì:

- Có object cấu trúc rõ ràng.
- Có type conversion.
- Có validation.
- Hỗ trợ relaxed binding.
- Dễ inject và test.
- IDE có thể hỗ trợ metadata tốt hơn.

Không dùng field injection cho configuration properties. Inject object cấu hình qua constructor như dependency khác.

### 7.3. Fail fast bằng validation

Nếu `max-items` bằng `0` hoặc URL không bind được thành `URI`, context phải không khởi động. Fail fast tốt hơn chạy với cấu hình sai rồi lỗi ở request production.

Validation cần dependency:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
```

### 7.4. Default value đặt ở đâu?

Ưu tiên default vận hành dễ thấy trong `application.yml`:

```yaml
app:
  task:
    max-items: 100
```

Default trong code phù hợp khi nó thật sự là invariant hoặc giá trị kỹ thuật ổn định. Tránh vừa có default trong record vừa có default khác trong YAML.

### 7.5. Không log toàn bộ configuration object

Record tự sinh `toString()`. Nếu record chứa API key hoặc password, log object có thể làm lộ secret.

Không làm:

```java
log.info("Configuration: {}", properties);
```

Chỉ log những field không nhạy cảm và thực sự hữu ích:

```java
log.info(
        "Task client configured with baseUrl={} and timeout={}",
        properties.client().baseUrl(),
        properties.client().readTimeout()
);
```

---

## 8. Tổ chức profile local, test và prod

### 8.1. Cấu trúc file

```text
src/main/resources
├── application.yml
├── application-local.yml
└── application-prod.yml

src/test/resources
└── application-test.yml
```

`application.yml` chứa cấu hình chung:

```yaml
spring:
  application:
    name: task-api

app:
  task:
    max-items: 100
    cleanup-interval: 10m
    client:
      base-url: "http://localhost:9090"
      connect-timeout: 2s
      read-timeout: 5s

management:
  endpoints:
    web:
      exposure:
        include: health,info
```

### 8.2. Local profile

`application-local.yml`:

```yaml
server:
  port: 8080

app:
  task:
    max-items: 20

logging:
  level:
    com.example.taskapi: DEBUG
```

Chạy:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Hoặc:

```bash
SPRING_PROFILES_ACTIVE=local ./mvnw spring-boot:run
```

### 8.3. Test profile

`src/test/resources/application-test.yml`:

```yaml
server:
  port: 0

app:
  task:
    max-items: 5
    cleanup-interval: 1s
    client:
      base-url: "http://localhost:9999"
      connect-timeout: 100ms
      read-timeout: 200ms

logging:
  level:
    root: WARN
```

`server.port: 0` yêu cầu hệ điều hành chọn port trống, hữu ích cho integration test **có khởi động web server thật**. `@SpringBootTest` mặc định dùng mock web environment và không mở port; khi cần server thật, dùng:

```java
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles("test")
class TaskHttpIntegrationTest {
}
```

Kích hoạt trong test:

```java
@SpringBootTest
@ActiveProfiles("test")
class TaskApiApplicationTests {
}
```

Test không nên gọi service production thật. Dùng fake server, mock server hoặc test container phù hợp với loại integration.

### 8.4. Production profile

`application-prod.yml`:

```yaml
server:
  port: ${SERVER_PORT:8080}

app:
  task:
    max-items: ${TASK_MAX_ITEMS:1000}
    client:
      base-url: ${TASK_API_BASE_URL}
      connect-timeout: ${TASK_CONNECT_TIMEOUT:2s}
      read-timeout: ${TASK_READ_TIMEOUT:5s}

logging:
  level:
    root: INFO
    com.example.taskapi: INFO
```

Nếu `TASK_API_BASE_URL` thiếu, ứng dụng không thể resolve placeholder và phải fail startup. Đây là hành vi mong muốn với cấu hình production bắt buộc.

Không để password, access token, signing key hoặc credential thật trong `application-prod.yml`.

### 8.5. Cách kích hoạt profile

Environment variable:

```bash
SPRING_PROFILES_ACTIVE=prod java -jar target/task-api.jar
```

Command-line argument:

```bash
java -jar target/task-api.jar --spring.profiles.active=prod
```

Không hardcode production profile trong source code hoặc trong chính artifact. Môi trường deploy phải quyết định profile.

### 8.6. Default profile

Khi không có profile active, profile đặc biệt `default` được dùng. Có thể đổi tên default profiles từ cấu hình không phụ thuộc profile, nhưng thường một `application.yml` có default an toàn là đủ.

Không xem việc “không profile” là production một cách ngầm định. Production nên kích hoạt rõ ràng và được kiểm tra trong deployment.

### 8.7. Profile groups

Profile group gom nhiều profile kỹ thuật dưới một tên vận hành:

```yaml
spring:
  profiles:
    group:
      production:
        - proddb
        - observability
```

Kích hoạt `production` sẽ kích hoạt cả `proddb` và `observability`.

Các thuộc tính như `spring.profiles.active`, `include` và `default` phải nằm trong document không phải profile-specific. Không đặt chúng trong `application-prod.yml` để tự kích hoạt hoặc kích hoạt chéo profile.

### 8.8. `@Profile` cho bean

```java
@Configuration
@Profile("local")
public class LocalDebugConfiguration {

    @Bean
    TaskDataSeeder taskDataSeeder() {
        return new TaskDataSeeder();
    }
}
```

Dùng `@Profile` khi bean thực sự chỉ tồn tại trong môi trường đó, ví dụ local seeder. Nếu chỉ khác giá trị port, URL hoặc timeout, dùng configuration properties thay vì nhân bản bean.

---

## 9. Import cấu hình ngoài

Có thể import file cấu hình:

```yaml
spring:
  config:
    import: "optional:file:./config/local-overrides.yml"
```

`optional:` cho phép ứng dụng tiếp tục nếu file không tồn tại. Bỏ `optional:` nếu file là bắt buộc và thiếu file phải làm startup thất bại.

Có thể chỉ định location lúc chạy:

```bash
java -jar target/task-api.jar \
  --spring.config.additional-location=optional:file:./config/
```

Phân biệt:

- `spring.config.additional-location`: thêm location bên cạnh default locations.
- `spring.config.location`: thay thế default search locations; dùng sai có thể khiến `application.yml` mặc định không được đọc.

Không dùng config file ngoài như một cách tự chế secret manager nếu quyền file, rotation và audit không được kiểm soát.

---

## 10. Secret management

### 10.1. Tuyệt đối không hardcode

Không commit:

```yaml
app:
  integration:
    api-key: "real-production-key"
```

Không hardcode trong Java:

```java
private static final String API_KEY = "real-production-key";
```

### 10.2. Nguồn secret phù hợp

Tùy môi trường:

- Environment variable được nền tảng inject.
- Docker/Kubernetes secret với quyền truy cập phù hợp.
- Cloud secret manager.
- Vault hoặc hệ thống quản lý secret chuyên dụng.

Secret cần:

- Quyền truy cập tối thiểu.
- Rotation.
- Audit.
- Không xuất hiện trong Git history.
- Không xuất hiện trong log, exception, metrics hoặc health details.

Không truyền secret bằng command-line argument vì nó có thể xuất hiện trong shell history hoặc danh sách process. Environment variable cũng có giới hạn bảo mật tùy nền tảng; production nên ưu tiên cơ chế secret injection/secret manager được hệ thống vận hành hỗ trợ.

### 10.3. Nếu secret đã lộ

Xóa khỏi file chưa đủ vì secret có thể còn trong Git history hoặc log. Cần:

1. Thu hồi/rotate secret ngay.
2. Kiểm tra nơi secret đã được dùng.
3. Làm sạch history nếu chính sách yêu cầu.
4. Tìm secret tương tự trong repository và CI logs.
5. Thêm secret scanning/pre-commit policy phù hợp.

---

## 11. Logging với SLF4J

### 11.1. Tạo logger

```java
package com.example.taskapi.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class TaskService {
    private static final Logger log =
            LoggerFactory.getLogger(TaskService.class);

    public Task create(String title) {
        log.debug("Creating task with titleLength={}", title.length());
        // ...
    }
}
```

Dùng placeholder:

```java
log.info("Task created with id={}", task.id());
```

Không nối string không cần thiết:

```java
// Không nên
log.debug("Task payload: " + expensivePayload());
```

### 11.2. Chọn log level

| Level | Dùng khi |
|---|---|
| `TRACE` | Chi tiết rất sâu, thường chỉ bật ngắn hạn |
| `DEBUG` | Dữ liệu phục vụ debug kỹ thuật |
| `INFO` | Sự kiện vận hành/business đáng chú ý |
| `WARN` | Tình huống bất thường nhưng request/app còn xử lý được |
| `ERROR` | Thao tác thất bại cần điều tra |

Không log cùng một exception ở mọi layer. Thường layer chịu trách nhiệm biến lỗi thành outcome cuối cùng sẽ log với context phù hợp.

### 11.3. Cấu hình level

```yaml
logging:
  level:
    root: INFO
    com.example.taskapi: DEBUG
    org.springframework.web: INFO
```

Environment variable ví dụ:

```bash
LOGGING_LEVEL_COM_EXAMPLE_TASKAPI=DEBUG java -jar target/task-api.jar
```

### 11.4. Không log dữ liệu nhạy cảm

Không log:

- Password.
- Access/refresh token.
- API key.
- Cookie hoặc session ID.
- Authorization header.
- Thông tin cá nhân không cần thiết.
- Toàn bộ request/response body theo mặc định.

Error message trả cho client không chứa stack trace hoặc thông tin hạ tầng. Log server có thể chi tiết hơn nhưng vẫn phải được sanitize.

### 11.5. Khi nào dùng `logback-spring.xml`?

`application.yml` đủ cho level và pattern cơ bản. Dùng `logback-spring.xml` khi cần appender, rolling policy, structured logging hoặc behavior theo profile phức tạp.

Nếu cần extension của Spring trong Logback, dùng `logback-spring.xml` thay vì `logback.xml` để Spring có thể xử lý cấu hình mở rộng như `<springProfile>`.

---

## 12. Spring Boot DevTools

Thêm dependency cho môi trường phát triển:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-devtools</artifactId>
    <scope>runtime</scope>
    <optional>true</optional>
</dependency>
```

DevTools có thể cung cấp:

- Automatic restart khi classpath thay đổi.
- LiveReload support.
- Một số development-time defaults, ví dụ giảm caching.

### Restart không giống reload JVM hoàn toàn

DevTools dùng hai classloader để restart nhanh:

- Base classloader cho dependency ít thay đổi.
- Restart classloader cho application classes.

Điều này đôi khi gây vấn đề với library giữ static state, serialization hoặc classloader assumptions.

### Quy tắc sử dụng

- Chỉ dùng khi develop local.
- Không dựa vào DevTools cho production behavior.
- Không dùng restart thay cho test.
- Nếu có lỗi classloader khó hiểu, thử restart process hoàn toàn.
- Xác nhận artifact production không đóng gói behavior phát triển ngoài ý muốn.

---

## 13. Embedded server

Với Spring MVC starter, ứng dụng có embedded servlet server. Không cần deploy WAR vào application server ngoài cho use case thông thường.

### 13.1. Cấu hình cơ bản

```yaml
server:
  port: 8080
  shutdown: graceful
```

Environment variable:

```bash
SERVER_PORT=9090 java -jar target/task-api.jar
```

Port ngẫu nhiên cho test:

```yaml
server:
  port: 0
```

### 13.2. Không hardcode host public trong code

Ứng dụng có thể nằm sau reverse proxy, ingress hoặc load balancer. Scheme/host client nhìn thấy không nhất thiết giống địa chỉ server bind nội bộ.

Nếu xử lý forwarded headers, chỉ tin proxy được cấu hình và kiểm soát. Không tin tùy ý header do Internet client gửi vì có thể dẫn đến URL spoofing hoặc log sai nguồn.

### 13.3. Context path

Có thể cấu hình:

```yaml
server:
  servlet:
    context-path: /task-api
```

Nhưng đừng thêm context path nếu gateway/routing đã giải quyết prefix. Thay đổi này ảnh hưởng mọi endpoint, Actuator routing và client.

---

## 14. Bean lifecycle và application lifecycle khác nhau

### Bean lifecycle

Áp dụng cho từng bean:

```text
constructor
  → dependency injection
  → @PostConstruct
  → bean hoạt động
  → @PreDestroy
```

### Application lifecycle

Áp dụng cho toàn quá trình Spring Boot khởi động và dừng:

```text
SpringApplication.run()
  → chuẩn bị Environment
  → tạo ApplicationContext
  → load bean definitions
  → refresh context và khởi tạo singleton beans
  → publish started event
  → chạy runners
  → publish ready event
  → phục vụ request
  → nhận shutdown signal
  → graceful shutdown
  → đóng context và hủy beans
```

`@PostConstruct` của một bean không có nghĩa toàn ứng dụng đã ready nhận traffic.

---

## 15. Các event trong quá trình khởi động

Thứ tự quan trọng ở mức ứng dụng:

```text
ApplicationStartingEvent
    ↓
ApplicationEnvironmentPreparedEvent
    ↓
ApplicationContextInitializedEvent
    ↓
ApplicationPreparedEvent
    ↓
ApplicationStartedEvent
    ↓
AvailabilityChangeEvent<LivenessState.CORRECT>
    ↓
ApplicationRunner / CommandLineRunner
    ↓
ApplicationReadyEvent
    ↓
AvailabilityChangeEvent<ReadinessState.ACCEPTING_TRAFFIC>
```

Nếu startup thất bại, Spring Boot publish `ApplicationFailedEvent` khi có thể.

Liveness chuyển sang `CORRECT` sau `ApplicationStartedEvent`. Readiness chuyển sang `ACCEPTING_TRAFFIC` sau `ApplicationReadyEvent`. Trong shutdown hoặc khi trạng thái vận hành thay đổi, các availability event khác có thể cập nhật trạng thái tương ứng.

### Early events cần listener đăng ký sớm

Một listener được khai báo thành bean không thể bắt những event xảy ra trước khi context tạo bean. Listener cho early startup event phải được đăng ký bằng cơ chế khởi động phù hợp, ví dụ qua `SpringApplication` hoặc metadata framework.

Không viết listener chỉ để “thử cho biết” trong production. Event listener cần mục đích, error handling và thời gian chạy rõ ràng.

---

## 16. Chọn đúng hook lifecycle

### 16.1. Constructor

Dùng cho:

- Nhận dependency.
- Gán field `final`.
- Kiểm tra invariant rất nhẹ.

Không dùng cho:

- Network call.
- Query database lớn.
- Start background thread.
- Logic cần proxy của chính bean.

### 16.2. `@PostConstruct`

Dùng khi một bean cần hoàn tất initialization sau khi dependency đã được inject:

```java
@Component
public class TaskTemplateRegistry {
    private Map<String, String> templates = Map.of();

    @PostConstruct
    void initialize() {
        templates = Map.of("default", "New task");
    }
}
```

Callback nên nhanh và xác định. Exception làm bean/context không khởi tạo được, phù hợp nếu bean không thể hoạt động an toàn.

### 16.3. `ApplicationRunner`

Nhận `ApplicationArguments` đã parse:

```java
@Component
@Profile("local")
public class LocalTaskSeeder implements ApplicationRunner {
    private final TaskService taskService;

    public LocalTaskSeeder(TaskService taskService) {
        this.taskService = taskService;
    }

    @Override
    public void run(ApplicationArguments args) {
        taskService.create("Learn configuration");
    }
}
```

### 16.4. `CommandLineRunner`

Nhận raw `String... args`:

```java
@Component
public class StartupArgumentsLogger implements CommandLineRunner {
    private static final Logger log =
            LoggerFactory.getLogger(StartupArgumentsLogger.class);

    @Override
    public void run(String... args) {
        log.debug("Application received {} command-line arguments", args.length);
    }
}
```

Không log raw arguments vì chúng có thể chứa secret.

### 16.5. Thứ tự runner

Nếu có nhiều runner, dùng `@Order` hoặc implement `Ordered`:

```java
@Component
@Order(10)
public class FirstRunner implements ApplicationRunner {
    // ...
}
```

Chỉ dựa vào thứ tự khi dependency về thứ tự là có chủ đích. Nếu runner B bắt buộc dữ liệu từ runner A, cân nhắc gộp chúng vào một orchestration rõ ràng.

Runner chạy trước `ApplicationReadyEvent`. Nếu runner throw exception, startup thất bại.

### 16.6. `ApplicationReadyEvent`

```java
@Component
public class ReadyLogger {
    private static final Logger log =
            LoggerFactory.getLogger(ReadyLogger.class);

    @EventListener(ApplicationReadyEvent.class)
    public void onReady(ApplicationReadyEvent event) {
        log.info("Application is ready");
    }
}
```

Event này xảy ra sau khi runners hoàn tất. Không đặt công việc dài hoặc vô hạn vào listener đồng bộ vì nó làm chậm hoàn tất startup và readiness semantics.

### 16.7. Bảng lựa chọn

| Nhu cầu | Cơ chế phù hợp |
|---|---|
| Gán dependency | Constructor |
| Validate/khởi tạo nội bộ một bean | `@PostConstruct` |
| Chạy tác vụ sau khi context refresh, cần application args | `ApplicationRunner` |
| Cần raw command-line args | `CommandLineRunner` |
| Phản ứng khi toàn app đã hoàn tất runners | `ApplicationReadyEvent` |
| Dọn tài nguyên của bean | `@PreDestroy` hoặc destroy method |
| Quản lý component có start/stop phase | `Lifecycle`/`SmartLifecycle` khi thật sự cần |

---

## 17. Graceful shutdown

Spring Boot hỗ trợ graceful shutdown cho embedded web server. Khi context đóng:

1. Server ngừng nhận request mới theo cơ chế của server.
2. Request đang xử lý có cơ hội hoàn tất trong grace period.
3. Lifecycle components dừng theo phase.
4. Bean destruction callbacks chạy.
5. Process kết thúc.

Cấu hình:

```yaml
server:
  shutdown: graceful

spring:
  lifecycle:
    timeout-per-shutdown-phase: 20s
```

Trong Spring Boot hiện đại, graceful shutdown được bật mặc định cho embedded servers được hỗ trợ; khai báo rõ có thể giúp ý định vận hành dễ thấy.

### 17.1. Dọn tài nguyên với `@PreDestroy`

```java
@Component
public class TaskWorker {
    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }
}
```

Trong production, cần chờ có timeout và xử lý task dang dở phù hợp với ownership của worker.

### 17.2. Shutdown hook không phải bảo đảm tuyệt đối

Cleanup callback có thể không chạy khi:

- Process bị `SIGKILL`.
- JVM crash.
- Node mất điện.
- Container runtime cưỡng bức dừng sau timeout.

Business workflow quan trọng cần idempotency, retry, durable state và recovery strategy; không chỉ dựa vào `@PreDestroy`.

### 17.3. Shutdown trong container

Grace period của orchestrator phải lớn hơn hoặc phù hợp với timeout của ứng dụng. Nếu nền tảng kill process sau 10 giây nhưng ứng dụng chờ 30 giây, graceful shutdown không thể hoàn tất.

Readiness phải chuyển sang trạng thái từ chối traffic trước hoặc trong quá trình shutdown theo cơ chế nền tảng để hạn chế request mới.

---

## 18. Liveness và readiness

Hai khái niệm khác nhau:

- **Liveness**: process/app có còn sống hay bị kẹt đến mức cần restart?
- **Readiness**: app có sẵn sàng nhận traffic lúc này không?

Với Actuator, health probes có thể được dùng bởi nền tảng orchestration. Không đưa mọi dependency ngoài vào liveness; database tạm thời lỗi không nhất thiết đồng nghĩa process cần bị restart liên tục.

Không expose health details nhạy cảm công khai. Production cần chính sách endpoint và security phù hợp.

---

## 19. Đóng gói executable JAR

### 19.1. Maven plugin

Project tạo từ Spring Initializr thường có:

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-maven-plugin</artifactId>
        </plugin>
    </plugins>
</build>
```

Nếu không dùng Spring Boot parent/plugin management phù hợp, có thể cần cấu hình execution `repackage` rõ ràng.

### 19.2. Build

```bash
./mvnw clean verify
./mvnw package
```

`verify` nên chạy test/check trước khi đóng gói artifact dùng để deploy.

### 19.3. Chạy JAR

```bash
java -jar target/task-api-0.0.1-SNAPSHOT.jar
```

Chạy production profile và external configuration:

```bash
SPRING_PROFILES_ACTIVE=prod \
TASK_API_BASE_URL=https://tasks.example.com \
java -jar target/task-api-0.0.1-SNAPSHOT.jar
```

Cùng một JAR phải được promote qua các môi trường. Không rebuild một JAR khác chỉ để đổi URL hoặc logging level.

### 19.4. Bên trong executable JAR

Spring Boot executable JAR thường chứa:

- Application classes/resources.
- Dependency JARs theo layout của Boot loader.
- Metadata để xác định main class.
- Launcher cần thiết để chạy bằng `java -jar`.

Không giải nén và chỉnh file config bên trong artifact sau build. Dùng externalized configuration.

---

## 20. Thực hành hoàn chỉnh cho Task API

### Bước 1 — Tạo configuration properties

Tạo `TaskProperties` có:

```text
maxItems
cleanupInterval
client.baseUrl
client.connectTimeout
client.readTimeout
```

Yêu cầu:

- Dùng record.
- Có validation.
- Không dùng field injection.
- Không đặt secret trong `toString()` hoặc log.

### Bước 2 — Tạo ba profile

```text
application-local.yml
application-test.yml
application-prod.yml
```

- Local: DEBUG cho package ứng dụng, giới hạn nhỏ.
- Test: port ngẫu nhiên, timeout ngắn, không gọi production.
- Prod: INFO, URL bắt buộc từ environment.

### Bước 3 — Dùng cấu hình trong service

```java
@Service
public class TaskService {
    private final TaskRepository repository;
    private final TaskProperties properties;

    public TaskService(
            TaskRepository repository,
            TaskProperties properties
    ) {
        this.repository = repository;
        this.properties = properties;
    }
}
```

Service không gọi `System.getenv()` trực tiếp. Boundary cấu hình nằm trong Spring configuration/binding.

### Bước 4 — Local seeder

Tạo `ApplicationRunner` chỉ active ở profile local. Seeder tạo hai task mẫu và phải có hành vi idempotent nếu có khả năng chạy lại.

### Bước 5 — Logging

Log:

- Application ready.
- Task created với task ID.
- Validation/configuration failure có context an toàn.

Không log request body, API key hoặc raw command-line arguments.

### Bước 6 — Graceful shutdown

Tạo worker giả lập có lifecycle rõ ràng. Khi shutdown:

- Ngừng nhận job mới.
- Hoàn tất hoặc trả lại job dang dở theo policy.
- Chờ có timeout.
- Giải phóng executor.

### Bước 7 — Package và chạy

```bash
./mvnw clean verify
./mvnw package
SPRING_PROFILES_ACTIVE=local \
java -jar target/task-api-0.0.1-SNAPSHOT.jar
```

Kiểm tra:

```bash
curl -i http://localhost:8080/actuator/health
curl -i http://localhost:8080/api/tasks
```

Dừng bằng `Ctrl+C` và quan sát graceful shutdown log.

---

## 21. Kiểm thử configuration

### 21.1. Test binding thành công

```java
class TaskPropertiesTest {
    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(TestConfiguration.class)
                    .withPropertyValues(
                            "app.task.max-items=50",
                            "app.task.cleanup-interval=5m",
                            "app.task.client.base-url=http://localhost:9090",
                            "app.task.client.connect-timeout=1s",
                            "app.task.client.read-timeout=3s"
                    );

    @Test
    void bindsTaskProperties() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(TaskProperties.class);

            TaskProperties properties =
                    context.getBean(TaskProperties.class);

            assertThat(properties.maxItems()).isEqualTo(50);
            assertThat(properties.cleanupInterval())
                    .isEqualTo(Duration.ofMinutes(5));
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(TaskProperties.class)
    static class TestConfiguration {
    }
}
```

### 21.2. Test cấu hình sai làm context thất bại

```java
@Test
void rejectsInvalidMaxItems() {
    contextRunner
            .withPropertyValues("app.task.max-items=0")
            .run(context ->
                    assertThat(context).hasFailed()
            );
}
```

Trong test thực tế, cung cấp đầy đủ các property bắt buộc rồi assert root cause/message phù hợp để biết failure đến từ validation mong đợi.

### 21.3. Test profile

```java
@SpringBootTest
@ActiveProfiles("test")
class TestProfileIntegrationTest {
    @Test
    void usesTestLimits(@Autowired TaskProperties properties) {
        assertThat(properties.maxItems()).isEqualTo(5);
    }
}
```

Parameter injection giữ dependency của test method rõ ràng mà không cần field injection.

### 21.4. Test precedence có chủ đích

Không cần test Spring Boot hoạt động đúng. Chỉ test contract cấu hình của ứng dụng:

- Property bắt buộc.
- Default do project đặt.
- Binding nested object.
- Validation constraint.
- Conditional bean do project định nghĩa.
- Profile-specific behavior quan trọng.

---

## 22. Các lỗi thường gặp

### Placeholder không resolve được

```text
Could not resolve placeholder 'TASK_API_BASE_URL'
```

Kiểm tra:

- Biến môi trường đã được export cho đúng process chưa?
- Tên biến có đúng case không?
- IDE run configuration có nhận biến không?
- Container manifest có inject biến không?
- Property có thực sự bắt buộc hay cần default an toàn?

### Profile không được kích hoạt

Kiểm tra startup log và `SPRING_PROFILES_ACTIVE`. Không giả định IDE, shell và container dùng chung environment.

### File profile không được đọc

Kiểm tra tên chính xác:

```text
application-{profile}.yml
```

Ví dụ profile `prod` tương ứng `application-prod.yml`.

### `@ConfigurationProperties` không có bean

Kiểm tra:

- Có `@ConfigurationPropertiesScan` không?
- Hoặc đã dùng `@EnableConfigurationProperties` chưa?
- Class có nằm trong package scan không?
- Prefix có đúng không?

### Validation không chạy

Kiểm tra:

- Có `@Validated` không?
- Có validation starter không?
- Nested object có `@Valid` không?
- Constraint có đặt đúng component không?

### Log DEBUG không xuất hiện

Kiểm tra package logger và profile đang active. Logger name phải khớp package/class thực tế.

### App treo lúc startup

Tìm network call, blocking I/O hoặc task dài trong:

- Constructor.
- `@PostConstruct`.
- `ApplicationRunner`.
- `CommandLineRunner`.
- Synchronous event listener.
- `SmartLifecycle.start()`.

### App không dừng đúng hạn

Kiểm tra:

- Executor có shutdown không?
- Thread có non-daemon và không được quản lý không?
- Request/job dài hơn grace period không?
- Timeout ứng dụng có khớp timeout orchestrator không?
- Client call có timeout không?

---

## 23. Anti-pattern cần tránh

### Đọc environment variable rải rác

```java
String value = System.getenv("TASK_API_BASE_URL");
```

Làm vậy ở nhiều service khiến validation, naming và test khó kiểm soát. Bind tại boundary cấu hình rồi inject typed object.

### Hardcode profile trong code

```java
System.setProperty("spring.profiles.active", "prod");
```

Artifact không nên tự quyết định môi trường deploy.

### Dùng profile cho mọi biến thể

Không tạo hàng loạt profile chỉ để đổi một timeout. Dùng property override.

### Secret trong Git

Không commit secret dù repository private. Private repository không thay thế secret manager và rotation.

### Log toàn bộ configuration

Configuration object có thể chứa secret. Chỉ log allowlist field an toàn.

### Công việc dài trong startup hook

Startup hook đồng bộ làm chậm readiness hoặc khiến deployment timeout. Công việc dài cần design background job, retry và observability rõ ràng.

### Nuốt exception khi shutdown

Cleanup failure cần được log có context. Nhưng shutdown cũng cần timeout để process không treo vô hạn.

---

## 24. Bài tập thực hành

### Bài 1 — Quan sát precedence

Đặt `server.port` lần lượt ở:

- `application.yml`.
- `application-local.yml`.
- `SERVER_PORT`.
- Command-line argument.

Ghi lại giá trị cuối cùng và giải thích vì sao.

### Bài 2 — Validation configuration

Tạo `NotificationProperties`:

```text
enabled
baseUrl
connectTimeout
maxRetries
```

Yêu cầu:

- `baseUrl` bắt buộc khi `enabled=true`.
- Timeout phải dương.
- Retry từ 0 đến 10.
- Context fail với lỗi dễ hiểu khi sai.

### Bài 3 — Profile isolation

Tạo local seeder chỉ chạy ở local. Viết integration test chứng minh:

- Seeder tồn tại trong profile local.
- Seeder không tồn tại trong profile prod.

### Bài 4 — Logging an toàn

Review toàn bộ log của Task API:

- Thay string concatenation bằng placeholder.
- Không log request body mặc định.
- Redact token/header nhạy cảm.
- Chọn lại level.
- Bảo đảm một exception không bị log lặp ở nhiều layer.

### Bài 5 — Application lifecycle

Tạo component log các mốc:

```text
constructor
@PostConstruct
ApplicationRunner
ApplicationReadyEvent
@PreDestroy
```

Chạy và dừng app, ghi lại thứ tự rồi giải thích.

### Bài 6 — Graceful shutdown

Tạo endpoint giả lập xử lý 5 giây. Gửi request, sau đó gửi tín hiệu dừng. Quan sát:

- Request hiện tại có hoàn tất không?
- Request mới có được nhận không?
- Điều gì xảy ra khi timeout shutdown nhỏ hơn thời gian xử lý?

Không chạy thử nghiệm này trên môi trường có traffic thật.

### Bài 7 — Executable JAR

Build một JAR duy nhất rồi chạy lần lượt với profile local và test bằng cấu hình ngoài. Chứng minh không cần rebuild artifact.

---

## 25. Câu hỏi tự kiểm tra

1. Externalized configuration giải quyết vấn đề gì?
2. Khi cùng key có trong YAML, environment và command line, giá trị nào thắng?
3. `.env` có được Spring Boot tự động đọc không?
4. Vì sao `@ConfigurationProperties` tốt hơn nhiều `@Value` rời rạc?
5. Tại sao configuration cần validation?
6. Secrets nên đến từ đâu?
7. Nếu secret đã commit rồi xóa ở commit sau, đã an toàn chưa?
8. `application.yml` và `application-prod.yml` được merge như thế nào?
9. Vì sao không nên hardcode active profile?
10. Khi nào dùng `@Profile`, khi nào dùng property?
11. Vì sao không log toàn bộ configuration record?
12. `DEBUG`, `INFO`, `WARN`, `ERROR` khác nhau ra sao?
13. DevTools restart khác restart JVM hoàn toàn thế nào?
14. Embedded server mang lại lợi ích gì?
15. Bean lifecycle khác application lifecycle ra sao?
16. `ApplicationRunner` chạy trước hay sau `ApplicationReadyEvent`?
17. Công việc nào phù hợp với `@PostConstruct`?
18. Graceful shutdown làm gì?
19. Vì sao `@PreDestroy` không bảo đảm chạy trong mọi trường hợp?
20. Tại sao cùng một JAR nên được dùng cho mọi môi trường?

---

## 26. Tiêu chí hoàn thành tuần 4

- [ ] Tổ chức được `application.yml` và các file profile.
- [ ] Giải thích được configuration precedence cơ bản.
- [ ] Override cấu hình bằng environment variable và command line.
- [ ] Không dùng `System.getenv()` rải rác trong business code.
- [ ] Bind nested configuration bằng `@ConfigurationProperties`.
- [ ] Ứng dụng fail fast khi cấu hình bắt buộc thiếu hoặc sai.
- [ ] Chạy được profile local, test và prod.
- [ ] Không hardcode profile hoặc secret.
- [ ] Log bằng SLF4J placeholder và đúng level.
- [ ] Không log token, password, API key hoặc dữ liệu nhạy cảm.
- [ ] Giải thích được DevTools và embedded server.
- [ ] Phân biệt bean lifecycle với application lifecycle.
- [ ] Chọn đúng constructor, `@PostConstruct`, runner và event.
- [ ] Cấu hình và quan sát được graceful shutdown.
- [ ] Build, test và chạy được executable JAR.
- [ ] Các test configuration quan trọng đều pass.

---

## 27. Tóm tắt

Configuration tốt giúp cùng một artifact chạy an toàn ở nhiều môi trường:

```text
application.yml
    + profile-specific config
    + environment variables
    + runtime overrides
    ↓
Spring Environment
    ↓
validated @ConfigurationProperties
    ↓
application beans
```

Vòng đời ứng dụng cần ghi nhớ:

```text
prepare environment
  → create and refresh context
  → initialize beans
  → publish started event
  → publish liveness CORRECT
  → run ApplicationRunner/CommandLineRunner
  → publish ready event
  → publish readiness ACCEPTING_TRAFFIC
  → serve traffic
  → graceful shutdown
  → destroy beans
```

Các nguyên tắc cốt lõi:

- Default an toàn trong artifact, khác biệt môi trường ở bên ngoài.
- Typed configuration và validation tốt hơn string rải rác.
- Secrets không thuộc Git, log hoặc error response.
- Profile biểu diễn nhóm môi trường/capability, không thay thế mọi property.
- Startup hook phải ngắn và có failure semantics rõ ràng.
- Graceful shutdown cần phối hợp timeout ứng dụng với nền tảng deploy.
- Build một lần, promote cùng artifact qua mọi môi trường.

## Tài liệu chính thức

- [Externalized Configuration](https://docs.spring.io/spring-boot/reference/features/external-config.html)
- [Profiles](https://docs.spring.io/spring-boot/reference/features/profiles.html)
- [Logging](https://docs.spring.io/spring-boot/reference/features/logging.html)
- [Developer Tools](https://docs.spring.io/spring-boot/reference/using/devtools.html)
- [SpringApplication](https://docs.spring.io/spring-boot/reference/features/spring-application.html)
- [Graceful Shutdown](https://docs.spring.io/spring-boot/reference/web/graceful-shutdown.html)
- [Embedded Web Servers](https://docs.spring.io/spring-boot/how-to/webserver.html)
- [Build Systems and Executable JARs](https://docs.spring.io/spring-boot/reference/using/build-systems.html)

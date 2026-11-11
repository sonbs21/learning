# Tuần 2: Spring Boot Fundamentals

> Tài liệu tổng hợp kiến thức mục 2 trong lộ trình [spring-boot-roadmap.md](spring-boot-roadmap.md). Nội dung đã được đối chiếu với Spring Boot Reference (3.5) qua Context7 để đảm bảo chính xác về cơ chế auto-configuration và thứ tự ưu tiên configuration property.

---

## 1. Auto-configuration

### 1.1 `@SpringBootApplication` là gì
`@SpringBootApplication` là 1 **meta-annotation** gộp 3 annotation lại làm 1:

```java
@SpringBootApplication
// tương đương với:
@Configuration          // class này có thể khai báo bean bằng @Bean
@EnableAutoConfiguration // bật cơ chế tự động cấu hình
@ComponentScan          // tự động quét @Component/@Service/... từ package hiện tại trở xuống
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```
Đây là lý do class chứa `main()` **nên đặt ở package gốc** của project — `@ComponentScan` mặc định chỉ quét package chứa class được đánh dấu `@SpringBootApplication` và các package con của nó.

### 1.2 Cơ chế hoạt động bên trong
Khi ứng dụng khởi động, `@EnableAutoConfiguration` khiến Spring Boot đọc file:
```
META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
```
nằm bên trong các jar dependency (từ Spring Boot 3.x trở lên — các bản Boot 2.x cũ dùng `spring.factories`). Mỗi dòng trong file này là tên đầy đủ 1 class `@AutoConfiguration`, và **mỗi class chỉ được nạp qua file này** — không được để nó bị `@ComponentScan` quét trùng.

```text
# Ví dụ nội dung file .imports (rút gọn)
org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration
org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration
```

Mỗi class auto-configuration chỉ thực sự tạo bean khi **điều kiện** của nó thỏa mãn — đó là lý do có nhóm annotation `@Conditional...`:

| Annotation | Điều kiện để bean được tạo |
|---|---|
| `@ConditionalOnClass(SomeClass.class)` | Class `SomeClass` phải có mặt trên classpath (VD: chỉ auto-config Tomcat nếu có dependency Tomcat) |
| `@ConditionalOnMissingBean` | Chỉ tạo bean này nếu **chưa có bean cùng type** nào được khai báo — cho phép người dùng tự override bằng bean của riêng mình |
| `@ConditionalOnProperty(prefix = "app", name = "feature.enabled")` | Chỉ tạo bean khi property tương ứng tồn tại và **không** bằng `false` (có thể tùy chỉnh giá trị mong đợi qua `havingValue`, hoặc `matchIfMissing = true` để mặc định bật khi thiếu property) |

```java
@AutoConfiguration
public class MyAutoConfiguration {

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(SomeService.class) // tách riêng để JVM không load class khi điều kiện chưa thỏa
    static class SomeServiceConfiguration {

        @Bean
        @ConditionalOnMissingBean // nhường chỗ nếu người dùng đã tự khai báo bean SomeService
        public SomeService someService() {
            return new SomeService();
        }
    }
}
```
> Lưu ý: `@ConditionalOnClass` nên đặt trên 1 `@Configuration` lồng bên trong (tách riêng), không đặt trực tiếp lên method `@Bean` có tham số kiểu class đó — vì JVM có thể cố load class tham số **trước khi** điều kiện được đánh giá, gây lỗi `ClassNotFoundException` dù logic điều kiện đúng ra phải bỏ qua bean đó.

### 1.3 Debug Auto-configuration
Chạy ứng dụng kèm flag `--debug` để bật conditions report — in ra danh sách **Positive matches** (auto-config nào được áp dụng và lý do) và **Negative matches** (auto-config nào bị bỏ qua và lý do):

```bash
java -jar myapp.jar --debug
```

---

## 2. Starter Dependencies

### 2.1 Starter là gì
Starter là 1 dependency "gộp" — kéo theo sẵn tất cả thư viện cần thiết cho 1 nhóm chức năng, đã được Spring Boot team kiểm tra tương thích phiên bản với nhau. Ví dụ `spring-boot-starter-web` kéo theo Tomcat (embedded server), Jackson (JSON), Spring MVC — người dùng không cần tự khai báo version cho từng thư viện riêng lẻ, tránh xung đột version.

### 2.2 Quy ước đặt tên
| Loại starter | Quy ước tên |
|---|---|
| Starter chính thức của Spring Boot | `spring-boot-starter-*` (VD `spring-boot-starter-web`, `spring-boot-starter-data-jpa`) |
| Starter của bên thứ 3 / nội bộ công ty | **Không** được bắt đầu bằng `spring-boot` (tên này dành riêng cho project chính thức) — quy ước là `<tên-thư-viện>-spring-boot-starter` (VD `mylib-spring-boot-starter`) |

### 2.3 Tự viết Custom Starter
Khi muốn đóng gói 1 thư viện nội bộ để nhiều dự án khác cùng dùng lại, cấu trúc chuẩn tách thành **2 module**:

| Module | Trách nhiệm |
|---|---|
| `xxx-spring-boot-autoconfigure` | Chứa toàn bộ logic auto-configuration (`@AutoConfiguration` class + file `.imports`) — đây là phần "não" thực sự |
| `xxx-spring-boot-starter` | Chỉ chứa `pom.xml`/`build.gradle` khai báo dependency tới module autoconfigure ở trên + các thư viện cần thiết khác — không chứa code |

> Với project nhỏ, đơn giản, có thể **gộp chung 1 module** thay vì tách 2 — việc tách chỉ thực sự cần thiết khi có tính năng tùy chọn (optional feature) phức tạp cần quản lý dependency riêng biệt. Starter module **không nên** tự đưa ra giả định về project đích và không nên gồm dependency optional không cần thiết cho use-case thông thường; mọi starter đều phải tham chiếu (trực tiếp hoặc gián tiếp) tới `spring-boot-starter` gốc để đảm bảo tương thích với core framework.

---

## 3. Configuration Properties

### 3.1 `application.properties` vs `application.yml`
Cả 2 định dạng đều được Spring Boot đọc, khác nhau về cú pháp:

```properties
# application.properties
app.name=Order Service
app.timeout=30
```
```yaml
# application.yml — cùng ý nghĩa, dạng cây phân cấp, dễ đọc khi cấu trúc lồng nhau nhiều
app:
  name: Order Service
  timeout: 30
```
> Nếu cả 2 file cùng tồn tại ở cùng 1 vị trí, Spring Boot ưu tiên đọc `.properties` trước.

### 3.2 `@Value` — inject giá trị đơn lẻ

```java
@Component
class MailSender {
    @Value("${app.name}")
    private String appName;

    @Value("${app.timeout:60}") // có giá trị mặc định 60 nếu property không tồn tại
    private int timeout;
}
```

### 3.3 `@ConfigurationProperties` — bind cả nhóm property
Khi có nhiều property cùng nhóm, dùng `@ConfigurationProperties` để bind thẳng vào 1 class, gọn hơn nhiều so với khai báo từng `@Value` riêng lẻ, và hỗ trợ validate qua `@Validated`:

```java
@ConfigurationProperties(prefix = "app")
@Validated
public class AppProperties {
    @NotBlank
    private String name;

    @Min(1)
    private int timeout;

    // getter/setter (hoặc dùng record cho Java 17+/immutable style)
}
```
```java
@Configuration
@EnableConfigurationProperties(AppProperties.class) // hoặc đánh dấu AppProperties bằng @Component/@ConfigurationPropertiesScan
class AppConfig {}
```

### 3.4 Thứ tự ưu tiên override (từ thấp đến cao — cái sau ghi đè cái trước)
1. Default properties (`SpringApplication.setDefaultProperties`, `@PropertySource`)
2. `application.yml`/`application.properties` đóng gói **bên trong jar** (mặc định)
3. Profile-specific file bên trong jar (`application-{profile}.yml`) — ưu tiên hơn file mặc định cùng vị trí
4. File cấu hình **bên ngoài jar** (VD cùng thư mục chạy jar) — ưu tiên hơn file đóng gói bên trong
5. Environment variable của hệ điều hành
6. System property (`-Dkey=value`)
7. **Command-line argument** (`--key=value`) — ưu tiên cao nhất trong số các nguồn phổ biến kể trên

> Ghi nhớ nhanh: càng "gần lúc chạy ứng dụng" (command-line, env var) thì độ ưu tiên càng cao so với file cấu hình tĩnh đóng gói sẵn trong jar. Có thể debug toàn bộ quá trình load property bằng cách set log level `org.springframework.boot.context.config` = `TRACE`.

---

## 4. Profile

### 4.1 Tách cấu hình theo môi trường
```yaml
# application.yml (mặc định, áp dụng mọi môi trường)
spring:
  application:
    name: order-service

---
spring:
  config:
    activate:
      on-profile: dev
app:
  db-url: jdbc:h2:mem:testdb

---
spring:
  config:
    activate:
      on-profile: prod
app:
  db-url: jdbc:postgresql://prod-host:5432/orders
```
Hoặc tách file riêng: `application-dev.yml`, `application-prod.yml`. Kích hoạt profile bằng property `spring.profiles.active=dev` (qua command-line, env var, hoặc file cấu hình).

### 4.2 `@Profile` trên bean
```java
@Configuration
class DataSourceConfig {

    @Bean
    @Profile("dev")
    public DataSource devDataSource() {
        return new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2).build();
    }

    @Bean
    @Profile("prod")
    public DataSource prodDataSource() {
        return DataSourceBuilder.create().url("jdbc:postgresql://prod-host:5432/orders").build();
    }
}
```
`@Profile` thực chất cũng chỉ là 1 dạng `@Conditional` chuyên biệt (dựa trên `spring.profiles.active`) — không có annotation riêng tên `@ConditionalOnProfile`, đó chính là `@Profile`.

---

## 5. Embedded Server & DevTools

### 5.1 Embedded Server
Spring Boot mặc định dùng **Tomcat** làm embedded server khi có `spring-boot-starter-web`. Muốn đổi sang **Jetty** hoặc **Undertow**, cần exclude Tomcat khỏi starter rồi thêm starter tương ứng:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
    <exclusions>
        <exclusion>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-tomcat</artifactId>
        </exclusion>
    </exclusions>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-undertow</artifactId>
</dependency>
```

### 5.2 DevTools
`spring-boot-devtools` cung cấp:
- **Auto-restart**: tự động khởi động lại ứng dụng khi có thay đổi trên classpath (nhanh hơn restart thủ công vì dùng 2 classloader riêng — 1 cho code base ít đổi, 1 cho code hay sửa).
- **LiveReload**: tự động refresh trình duyệt khi có thay đổi (cần cài extension LiveReload tương ứng).

DevTools chỉ nên có ở scope development — khai báo dependency với `optional=true` (Maven) hoặc `developmentOnly` (Gradle) để **không** bị đóng gói vào build production, và nó cũng tự động bị vô hiệu hóa khi ứng dụng chạy từ jar đã đóng gói đầy đủ (fully packaged jar).

---

## Câu hỏi tự kiểm tra
1. `@SpringBootApplication` thực chất gộp những annotation nào? Vì sao class chứa `main()` nên đặt ở package gốc?
2. File `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` dùng để làm gì? Điều gì xảy ra nếu 1 class auto-configuration vừa được liệt kê trong file này, vừa bị `@ComponentScan` quét trùng?
3. Vì sao `@ConditionalOnClass` nên đặt trên 1 `@Configuration` lồng bên trong, thay vì đặt trực tiếp lên tham số của method `@Bean`?
4. `@ConditionalOnMissingBean` giải quyết vấn đề gì cho người dùng thư viện? Cho 1 ví dụ cụ thể khi nào bean auto-config nên "nhường chỗ" cho bean người dùng tự khai báo.
5. Liệt kê đúng thứ tự ưu tiên (từ thấp đến cao) giữa: command-line argument, environment variable, `application-{profile}.yml`, `application.yml` mặc định.
6. Tại sao custom starter thường tách thành 2 module (`autoconfigure` + `starter`) thay vì gộp chung 1 module? Khi nào thì việc gộp chung là hợp lý?

## Bài tập luyện tập
- Viết 1 `@AutoConfiguration` class tự tạo (không phải của Spring Boot có sẵn) cho 1 bean giả lập (VD `GreetingService`), dùng `@ConditionalOnProperty(prefix = "app.greeting", name = "enabled", havingValue = "true", matchIfMissing = true)` — đăng ký vào file `.imports`, chạy thử với property bật/tắt để kiểm chứng bean có/không được tạo.
- Tạo 1 class `AppProperties` dùng `@ConfigurationProperties(prefix = "app")` với ít nhất 3 field, thêm validation (`@NotBlank`, `@Min`...), cố tình truyền giá trị sai để xem lỗi validate lúc khởi động ứng dụng.
- Thiết lập 2 profile `dev`/`prod` với 2 giá trị `app.db-url` khác nhau, chạy ứng dụng với `spring.profiles.active=dev` rồi `prod`, in ra `app.db-url` để kiểm chứng đúng giá trị theo từng profile.
- Thử nghiệm thứ tự override: đặt `app.timeout=30` trong `application.yml`, sau đó chạy `java -jar app.jar --app.timeout=99` — kiểm chứng giá trị inject vào `@Value`/`@ConfigurationProperties` là `99` (command-line thắng file cấu hình).

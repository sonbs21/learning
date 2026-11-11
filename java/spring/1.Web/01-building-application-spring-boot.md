# Bài 1: Building an Application with Spring Boot

> Category **1. Web** trong [spring-boot-roadmap-complete.md](../spring-boot-roadmap-complete.md) — bài nền tảng nhất, dạy cách khởi tạo, chạy và đóng gói 1 ứng dụng Spring Boot với cấu hình tối thiểu. Nội dung đối chiếu với Spring Boot Reference 3.5 qua Context7 (tutorial `first-application`, `spring-application`, `testing/spring-boot-applications`).

---

## 0. Mục tiêu bài học

Sau bài này, bạn cần trả lời được:
- Spring Boot giải quyết vấn đề gì so với Spring thuần (Spring Framework cổ điển)?
- `SpringApplication.run()` thực sự làm những gì bên dưới?
- Vì sao 1 file jar Spring Boot chạy được bằng `java -jar` mà không cần cài server ngoài?
- Cấu trúc project tối thiểu để có 1 REST endpoint chạy được là gì?

---

## 1. Vấn đề Spring Boot giải quyết

Với Spring Framework thuần (trước Boot), để chạy 1 web app bạn phải:
1. Tự cấu hình `DispatcherServlet`, `web.xml` (hoặc `WebApplicationInitializer`).
2. Tự khai báo bean bằng XML hoặc `@Configuration` chi tiết cho từng thành phần (DataSource, ViewResolver...).
3. Tự cài đặt server (Tomcat/Jetty) rồi deploy file `.war` vào đó.
4. Tự quản lý version của hàng chục dependency sao cho tương thích với nhau.

Spring Boot xóa bỏ phần lớn việc này bằng 3 trụ cột:

| Trụ cột | Vai trò |
|---|---|
| **Auto-configuration** | Tự suy đoán cấu hình dựa trên dependency có trên classpath (xem chi tiết ở [02-spring-boot-fundamentals.md](../02-spring-boot-fundamentals.md)) |
| **Starter dependencies** | Gộp sẵn các thư viện tương thích version cho 1 nhóm chức năng (`spring-boot-starter-web`...) |
| **Embedded server** | Nhúng thẳng Tomcat/Jetty/Undertow vào trong jar — chạy `java -jar app.jar` là có server, không cần deploy ra ngoài |

Bài này tập trung vào việc **dựng và chạy** 1 ứng dụng cụ thể bằng 3 trụ cột trên; cơ chế bên trong auto-configuration sẽ học sâu ở bài 2 (Spring Boot Fundamentals).

---

## 2. Bước 1: Khởi tạo project

### 2.1 Dùng Spring Initializr
Cách chuẩn (và cách guide chính thức hướng dẫn) là vào `start.spring.io`, chọn:
- Build tool: Maven hoặc Gradle
- Dependencies: `Spring Web` (tương ứng `spring-boot-starter-web`)

Initializr sinh sẵn project với `pom.xml`/`build.gradle` đã cấu hình đúng.

### 2.2 `pom.xml` tối thiểu (Maven)
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.5.16</version>
    </parent>

    <groupId>com.example</groupId>
    <artifactId>myproject</artifactId>
    <version>0.0.1-SNAPSHOT</version>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

Giải thích 2 phần quan trọng:

| Thành phần | Vai trò |
|---|---|
| `spring-boot-starter-parent` (khai báo trong `<parent>`) | Cung cấp **dependency management** — bạn không cần ghi `<version>` cho từng dependency Spring Boot, parent đã chốt sẵn version tương thích. Cũng thiết lập sẵn encoding, Java version mặc định, cấu hình plugin mặc định. |
| `spring-boot-starter-web` | Starter kéo theo: Spring MVC, Jackson (JSON), **Tomcat nhúng sẵn** — chỉ cần dependency này là đủ để viết REST API và chạy server, không cần cài gì thêm. |
| `spring-boot-maven-plugin` | Plugin đóng gói project thành **executable jar** (fat/uber jar) — xem mục 5. |

> Nếu dùng Gradle, `id 'org.springframework.boot' version '3.5.16'` trong `plugins {}` đóng vai trò tương đương `spring-boot-starter-parent`.

---

## 3. Bước 2: Viết code

### 3.1 Class ứng dụng + REST endpoint gộp chung (ví dụ nhỏ nhất có thể chạy)
```java
package com.example.myproject;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@SpringBootApplication
public class MyApplication {

    @RequestMapping("/")
    String home() {
        return "Hello World!";
    }

    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}
```

Chạy được ngay — nhưng gộp `@RestController` và `@SpringBootApplication` vào cùng 1 class chỉ hợp lý cho demo cực nhỏ. Trong dự án thật, **tách class main ra khỏi controller**:

```java
// MyApplication.java — chỉ chịu trách nhiệm bootstrap
@SpringBootApplication
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}
```

```java
// HomeController.java — chịu trách nhiệm xử lý HTTP request
@RestController
public class HomeController {

    @RequestMapping("/")
    String home() {
        return "Hello World!";
    }
}
```

Vì `@ComponentScan` (ẩn bên trong `@SpringBootApplication`) tự quét mọi `@Component`/`@RestController` từ package chứa class main trở xuống, `HomeController` vẫn được phát hiện và đăng ký làm bean dù nằm ở file/class riêng — miễn là nó nằm cùng package hoặc package con.

### 3.2 `SpringApplication.run()` thực sự làm gì

```java
public static void main(String[] args) {
    SpringApplication.run(MyApplication.class, args);
}
```

Về bản chất, dòng này thực hiện tuần tự:
1. Tạo 1 `ApplicationContext` phù hợp (Servlet web context nếu có `spring-boot-starter-web` trên classpath, non-web context nếu không).
2. Đăng ký `MyApplication` như 1 configuration class gốc (vì có `@Configuration` ẩn bên trong `@SpringBootApplication`).
3. Chạy toàn bộ auto-configuration + component scanning để tạo bean.
4. Khởi động **embedded server** (nếu là web context) và bind vào port (mặc định `8080`).
5. Trả về `ConfigurableApplicationContext` — bạn có thể giữ lại reference này nếu cần truy cập bean thủ công sau khi start:
```java
ConfigurableApplicationContext ctx = SpringApplication.run(MyApplication.class, args);
MyService service = ctx.getBean(MyService.class);
```

### 3.3 Chạy logic khi ứng dụng khởi động — `CommandLineRunner`
Muốn chạy 1 đoạn code ngay sau khi context đã sẵn sàng (VD: seed dữ liệu, log thông tin cấu hình), implement `CommandLineRunner` thay vì nhồi logic vào `main()`:

```java
@Component
public class StartupRunner implements CommandLineRunner {

    @Override
    public void run(String... args) {
        System.out.println("Application started with args: " + Arrays.toString(args));
    }
}
```
Spring Boot tự gọi `run()` của mọi bean `CommandLineRunner` sau khi context khởi tạo xong — không cần đăng ký thủ công.

---

## 4. Bước 3: Chạy ứng dụng

3 cách tương đương nhau (đều dùng chung 1 embedded Tomcat, không có cách nào "nhanh hơn" bản chất):

| Cách | Lệnh |
|---|---|
| Qua Maven plugin | `mvn spring-boot:run` |
| Qua Gradle plugin | `./gradlew bootRun` |
| Chạy trực tiếp từ IDE | Click Run trên class có `main()` |

Log khởi động sẽ in ra banner ASCII của Spring Boot và dòng xác nhận thời gian start:
```
........ Started MyApplication in 0.484 seconds (process running for 0.642)
```
Sau đó mở `http://localhost:8080/` sẽ thấy `Hello World!`.

---

## 5. Bước 4: Đóng gói thành Executable Jar

### 5.1 Executable jar là gì
Còn gọi là **fat jar** / **uber jar** — 1 file `.jar` duy nhất chứa cả code biên dịch của bạn **lẫn** toàn bộ dependency (kể cả embedded Tomcat), có thể chạy trực tiếp bằng `java -jar` mà không cần classpath ngoài hay server cài sẵn.

Cấu trúc bên trong khác với jar Java thông thường:
```
example.jar
 |
 +-META-INF
 |  +-MANIFEST.MF
 +-org/springframework/boot/loader/   (class loader riêng của Spring Boot)
 +-BOOT-INF
    +-classes/                        (class của chính bạn)
    |  +-com/example/MyApplication.class
    +-lib/                            (toàn bộ dependency jar, giữ nguyên dạng jar)
       +-spring-web-x.x.x.jar
       +-tomcat-embed-core-x.x.x.jar
       ...
```
`org.springframework.boot.loader.*` là bootstrap classloader đặc biệt, có nhiệm vụ đọc các jar lồng bên trong `BOOT-INF/lib` — đây là lý do 1 file `.jar` chuẩn Java (vốn không hỗ trợ "jar trong jar") vẫn chạy được.

### 5.2 Build lệnh
```bash
# Maven — cần có spring-boot-maven-plugin trong pom.xml (đã khai báo ở mục 2.2)
mvn clean package

# Gradle — plugin org.springframework.boot tự thêm task bootJar
./gradlew clean build
```

### 5.3 Chạy jar đã đóng gói
```bash
java -jar target/myproject-0.0.1-SNAPSHOT.jar
```
Đây chính là artifact deploy lên production — không cần WAR, không cần cài Tomcat ngoài.

---

## 6. Bước 5: Viết Unit Test cho endpoint

Dùng `@SpringBootTest` + `@AutoConfigureMockMvc` để test endpoint mà **không cần** khởi động server thật (dùng mock request/response ở tầng Servlet):

```java
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MyApplicationTests {

    @Test
    void homeReturnsHelloWorld(@Autowired MockMvc mvc) throws Exception {
        mvc.perform(get("/"))
           .andExpect(status().isOk())
           .andExpect(content().string("Hello World!"));
    }
}
```

| Annotation | Ý nghĩa |
|---|---|
| `@SpringBootTest` | Khởi tạo **toàn bộ** `ApplicationContext` thật (mặc định dùng mock web environment — không bind port thật) — dùng cho integration test |
| `@AutoConfigureMockMvc` | Tự cấu hình bean `MockMvc` để giả lập HTTP request vào tầng `DispatcherServlet` mà không cần mở socket |

> Nếu chỉ muốn test tầng web (không load toàn bộ context, nhanh hơn), dùng `@WebMvcTest` thay vì `@SpringBootTest` — nội dung này học sâu hơn ở [07-testing-best-practices.md](../07-testing-best-practices.md).

Muốn test với server thật (VD cần gọi qua HTTP client thật để kiểm tra serialization end-to-end), dùng `webEnvironment = WebEnvironment.RANDOM_PORT` + `TestRestTemplate`:
```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MyApplicationRealServerTests {

    @Autowired
    private TestRestTemplate template;

    @Test
    void homeReturnsHelloWorld() {
        String body = template.getForObject("/", String.class);
        assertThat(body).isEqualTo("Hello World!");
    }
}
```

---

## 7. Tổng kết luồng end-to-end

```
1. pom.xml khai báo starter-parent + spring-boot-starter-web + spring-boot-maven-plugin
        │
2. Viết @SpringBootApplication (class main) + @RestController (endpoint)
        │
3. mvn spring-boot:run  →  SpringApplication.run() dựng ApplicationContext
        │                  + auto-configuration bật Tomcat nhúng
        │                  + component scan đăng ký HomeController
        ▼
4. Server chạy tại :8080, phục vụ request thật
        │
5. mvn clean package  →  spring-boot-maven-plugin đóng gói executable jar
        │
6. java -jar app.jar  →  deploy production, không cần cài server ngoài
```

---

## Câu hỏi tự kiểm tra
1. `spring-boot-starter-parent` giải quyết vấn đề gì mà nếu không có nó bạn sẽ phải tự làm thủ công trong `pom.xml`?
2. Liệt kê ít nhất 3 thứ mà `spring-boot-starter-web` tự động kéo theo.
3. `SpringApplication.run()` trả về kiểu dữ liệu gì, và tại sao đôi khi cần giữ lại giá trị trả về đó?
4. Vì sao 1 executable jar của Spring Boot chạy được bằng `java -jar` dù bên trong nó chứa "jar lồng trong jar" — điều mà format `.jar` chuẩn của Java không hỗ trợ trực tiếp?
5. Khác nhau giữa `@SpringBootTest` (mặc định) và `@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)` là gì? Khi nào chọn dùng cái nào?
6. Nếu tách `HomeController` ra file riêng khỏi class có `@SpringBootApplication`, điều gì đảm bảo nó vẫn được Spring quét và đăng ký làm bean?

## Bài tập luyện tập
- Khởi tạo project mới qua `start.spring.io` với dependency `Spring Web`, viết 1 `@RestController` có endpoint `GET /hello?name=...` trả về `"Hello, " + name`. Chạy bằng `mvn spring-boot:run` và kiểm tra bằng trình duyệt/`curl`.
- Đóng gói ứng dụng trên thành executable jar (`mvn clean package`), giải nén thử bằng `jar tf target/*.jar` để tận mắt thấy cấu trúc `BOOT-INF/classes` và `BOOT-INF/lib`.
- Viết 1 `CommandLineRunner` in ra `"App is ready!"` mỗi khi ứng dụng khởi động xong — xác nhận log này xuất hiện **sau** dòng `Started ... in ... seconds` trong console.
- Viết unit test cho endpoint `/hello` bằng `@SpringBootTest` + `@AutoConfigureMockMvc`, kiểm tra cả trường hợp có và không có param `name` (dùng `@RequestParam(defaultValue = "World")`).
- Thử đổi port mặc định bằng cách thêm `server.port=9090` vào `application.properties`, build lại jar và chạy — xác nhận ứng dụng lắng nghe ở port mới mà không cần sửa code Java.

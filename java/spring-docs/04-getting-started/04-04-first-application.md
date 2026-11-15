# 4.4. Phát triển ứng dụng Spring Boot đầu tiên (Developing Your First Spring Boot Application)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#getting-started.first-application>

Phần này mô tả cách phát triển một ứng dụng web "Hello World!" nhỏ, làm nổi bật một số tính năng chính của Spring Boot. Bạn có thể chọn giữa Maven hoặc Gradle làm hệ thống build.

> **MẸO (TIP):** Trang web [spring.io](https://spring.io) có nhiều [hướng dẫn](https://spring.io/guides) "Getting Started" sử dụng Spring Boot. Nếu bạn cần giải quyết một vấn đề cụ thể, hãy xem ở đó trước.
>
> Bạn có thể đi tắt qua các bước dưới đây bằng cách truy cập [start.spring.io](https://start.spring.io) và chọn starter "Web" từ ô tìm kiếm dependency. Làm như vậy sẽ sinh ra một cấu trúc project mới để bạn có thể [bắt đầu viết code ngay](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#getting-started.first-application.code). Xem [hướng dẫn sử dụng start.spring.io](https://github.com/spring-io/start.spring.io/blob/main/USING.adoc) để biết thêm chi tiết.

## 4.4.1. Điều kiện tiên quyết (Prerequisites)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#getting-started.first-application.prerequisites>

Trước khi bắt đầu, hãy mở terminal và chạy các lệnh sau để đảm bảo rằng bạn đã cài đặt một phiên bản Java hợp lệ:

```shell
$ java -version
openjdk version "17.0.4.1" 2022-08-12 LTS
OpenJDK Runtime Environment (build 17.0.4.1+1-LTS)
OpenJDK 64-Bit Server VM (build 17.0.4.1+1-LTS, mixed mode, sharing)
```

> **LƯU Ý (NOTE):** Ví dụ này cần được tạo trong một thư mục riêng. Các hướng dẫn tiếp theo giả định rằng bạn đã tạo một thư mục phù hợp và đó là thư mục hiện tại của bạn.

### Maven

Nếu bạn muốn dùng Maven, hãy đảm bảo rằng bạn đã cài đặt Maven:

```shell
$ mvn -v
Apache Maven 3.8.5 (3599d3414f046de2324203b78ddcf9b5e4388aa0)
Maven home: usr/Users/developer/tools/maven/3.8.5
Java version: 17.0.4.1, vendor: BellSoft, runtime: /Users/developer/sdkman/candidates/java/17.0.4.1-librca
```

### Gradle

Nếu bạn muốn dùng Gradle, hãy đảm bảo rằng bạn đã cài đặt Gradle:

```shell
$ gradle --version

------------------------------------------------------------
Gradle 8.1.1
------------------------------------------------------------

Build time:   2023-04-21 12:31:26 UTC
Revision:     1cf537a851c635c364a4214885f8b9798051175b

Kotlin:       1.8.10
Groovy:       3.0.15
Ant:          Apache Ant(TM) version 1.10.11 compiled on July 10 2021
JVM:          17.0.7 (BellSoft 17.0.7+7-LTS)
OS:           Linux 6.2.12-200.fc37.aarch64 aarch64
```

## 4.4.2. Thiết lập project với Maven (Setting up the project with Maven)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#getting-started.first-application.pom>

Chúng ta cần bắt đầu bằng việc tạo một file Maven `pom.xml`. File `pom.xml` là "công thức" được dùng để build project của bạn. Hãy mở trình soạn thảo văn bản yêu thích và thêm nội dung sau:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.example</groupId>
    <artifactId>myproject</artifactId>
    <version>0.0.1-SNAPSHOT</version>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.5</version>
    </parent>

    <!-- Additional lines to be added here... -->

</project>
```

Đoạn trên sẽ cho bạn một bản build hoạt động được. Bạn có thể kiểm tra bằng cách chạy `mvn package` (hiện tại, bạn có thể bỏ qua cảnh báo "jar will be empty - no content was marked for inclusion!").

> **LƯU Ý (NOTE):** Đến đây, bạn có thể import project vào IDE (hầu hết các IDE Java hiện đại đều có hỗ trợ sẵn cho Maven). Để đơn giản, chúng ta tiếp tục dùng trình soạn thảo văn bản thông thường cho ví dụ này.

## 4.4.3. Thiết lập project với Gradle (Setting up the project with Gradle)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#getting-started.first-application.gradle>

Chúng ta cần bắt đầu bằng việc tạo một file Gradle `build.gradle`. File `build.gradle` là build script được dùng để build project của bạn. Hãy mở trình soạn thảo văn bản yêu thích và thêm nội dung sau:

```gradle
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.2.5'
}

apply plugin: 'io.spring.dependency-management'

group = 'com.example'
version = '0.0.1-SNAPSHOT'
sourceCompatibility = '17'

repositories {
    mavenCentral()
}

dependencies {
}
```

Đoạn trên sẽ cho bạn một bản build hoạt động được. Bạn có thể kiểm tra bằng cách chạy `gradle classes`.

> **LƯU Ý (NOTE):** Đến đây, bạn có thể import project vào IDE (hầu hết các IDE Java hiện đại đều có hỗ trợ sẵn cho Gradle). Để đơn giản, chúng ta tiếp tục dùng trình soạn thảo văn bản thông thường cho ví dụ này.

## 4.4.4. Thêm dependency vào classpath (Adding Classpath Dependencies)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#getting-started.first-application.dependencies>

Spring Boot cung cấp một số "Starter" cho phép bạn thêm các jar vào classpath. Các "Starter" cung cấp những dependency mà bạn nhiều khả năng sẽ cần khi phát triển một loại ứng dụng cụ thể.

### Maven

Hầu hết các ứng dụng Spring Boot sử dụng `spring-boot-starter-parent` trong phần `parent` của POM. `spring-boot-starter-parent` là một starter đặc biệt cung cấp các giá trị mặc định hữu ích cho Maven. Nó cũng cung cấp một phần [`dependency-management`](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#using.build-systems.dependency-management) để bạn có thể bỏ qua thẻ `version` đối với các dependency "được chứng nhận" ("blessed").

Vì chúng ta đang phát triển một ứng dụng web, chúng ta sẽ thêm dependency `spring-boot-starter-web`. Trước đó, chúng ta có thể xem những gì đang có bằng cách chạy lệnh sau:

```shell
$ mvn dependency:tree

[INFO] com.example:myproject:jar:0.0.1-SNAPSHOT
```

Lệnh `mvn dependency:tree` in ra cây biểu diễn các dependency của project. Bạn có thể thấy rằng bản thân `spring-boot-starter-parent` không cung cấp dependency nào. Để thêm các dependency cần thiết, hãy sửa file `pom.xml` và thêm dependency `spring-boot-starter-web` ngay bên dưới phần `parent`:

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
</dependencies>
```

Nếu bạn chạy lại `mvn dependency:tree`, bạn sẽ thấy giờ đã có thêm một số dependency, bao gồm web server Tomcat và chính Spring Boot.

### Gradle

Hầu hết các ứng dụng Spring Boot sử dụng Gradle plugin `org.springframework.boot`. Plugin này cung cấp các giá trị mặc định và các Gradle task hữu ích. Gradle plugin `io.spring.dependency-management` cung cấp tính năng [quản lý dependency](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#using.build-systems.dependency-management) để bạn có thể bỏ qua thẻ `version` đối với các dependency "được chứng nhận" ("blessed").

Vì chúng ta đang phát triển một ứng dụng web, chúng ta sẽ thêm dependency `spring-boot-starter-web`. Trước đó, chúng ta có thể xem những gì đang có bằng cách chạy lệnh sau:

```shell
$ gradle dependencies

> Task :dependencies

------------------------------------------------------------
Root project 'myproject'
------------------------------------------------------------
```

Lệnh `gradle dependencies` in ra cây biểu diễn các dependency của project. Hiện tại, project chưa có dependency nào. Để thêm các dependency cần thiết, hãy sửa file `build.gradle` và thêm dependency `spring-boot-starter-web` vào phần `dependencies`:

```gradle
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
}
```

Nếu bạn chạy lại `gradle dependencies`, bạn sẽ thấy giờ đã có thêm một số dependency, bao gồm web server Tomcat và chính Spring Boot.

## 4.4.5. Viết code (Writing the Code)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#getting-started.first-application.code>

Để hoàn thành ứng dụng, chúng ta cần tạo một file Java duy nhất. Theo mặc định, Maven và Gradle biên dịch mã nguồn từ `src/main/java`, vì vậy bạn cần tạo cấu trúc thư mục đó rồi thêm một file tên là `src/main/java/MyApplication.java` chứa đoạn code sau:

**Java**

```java
package com.example;

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

**Kotlin**

```kotlin
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@SpringBootApplication
class MyApplication {

    @RequestMapping("/")
    fun home() = "Hello World!"

}

fun main(args: Array<String>) {
    runApplication<MyApplication>(*args)
}
```

Mặc dù ở đây không có nhiều code, nhưng có khá nhiều thứ đang diễn ra. Chúng ta sẽ lần lượt đi qua các phần quan trọng trong vài mục tiếp theo.

### Annotation @RestController và @RequestMapping (The @RestController and @RequestMapping Annotations)

Annotation đầu tiên trên class `MyApplication` là `@RestController`. Đây được gọi là một annotation *stereotype*. Nó cung cấp gợi ý cho người đọc code và cho Spring rằng class này đóng một vai trò cụ thể. Trong trường hợp này, class của chúng ta là một web `@Controller`, nên Spring sẽ xem xét nó khi xử lý các web request gửi đến.

Annotation `@RequestMapping` cung cấp thông tin "định tuyến" ("routing"). Nó cho Spring biết rằng bất kỳ HTTP request nào có đường dẫn `/` đều phải được ánh xạ tới phương thức `home`. Annotation `@RestController` cho Spring biết hãy trả chuỗi kết quả trực tiếp về cho bên gọi.

> **MẸO (TIP):** Các annotation `@RestController` và `@RequestMapping` là annotation của Spring MVC (chúng không dành riêng cho Spring Boot). Xem [phần MVC](https://docs.spring.io/spring-framework/reference/6.1/web/webmvc.html) trong Spring Reference Documentation để biết thêm chi tiết.

### Annotation @SpringBootApplication (The @SpringBootApplication Annotation)

Annotation cấp class thứ hai là `@SpringBootApplication`. Annotation này được gọi là một *meta-annotation*, nó kết hợp `@SpringBootConfiguration`, `@EnableAutoConfiguration` và `@ComponentScan`.

Trong số đó, annotation mà chúng ta quan tâm nhất ở đây là `@EnableAutoConfiguration`. `@EnableAutoConfiguration` bảo Spring Boot "đoán" cách bạn muốn cấu hình Spring, dựa trên các jar dependency mà bạn đã thêm. Vì `spring-boot-starter-web` đã thêm Tomcat và Spring MVC, auto-configuration giả định rằng bạn đang phát triển một ứng dụng web và thiết lập Spring cho phù hợp.

> **Starter và Auto-configuration**
>
> Auto-configuration được thiết kế để hoạt động tốt với các "Starter", nhưng hai khái niệm này không gắn trực tiếp với nhau. Bạn có thể tự do chọn các jar dependency bên ngoài các starter. Spring Boot vẫn sẽ cố gắng hết sức để tự động cấu hình ứng dụng của bạn.

### Phương thức "main" (The "main" Method)

Phần cuối cùng của ứng dụng là phương thức `main`. Đây là một phương thức tiêu chuẩn tuân theo quy ước của Java cho điểm khởi đầu (entry point) của ứng dụng. Phương thức main của chúng ta ủy quyền cho class `SpringApplication` của Spring Boot bằng cách gọi `run`. `SpringApplication` khởi động (bootstrap) ứng dụng, khởi chạy Spring, và đến lượt Spring sẽ khởi chạy web server Tomcat đã được tự động cấu hình. Chúng ta cần truyền `MyApplication.class` làm tham số cho phương thức `run` để cho `SpringApplication` biết đâu là component Spring chính. Mảng `args` cũng được truyền vào để cung cấp mọi tham số dòng lệnh.

## 4.4.6. Chạy ví dụ (Running the Example)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#getting-started.first-application.run>

### Maven

Đến đây, ứng dụng của bạn đã có thể hoạt động. Vì bạn đã dùng POM `spring-boot-starter-parent`, bạn có sẵn một goal `run` hữu ích để khởi chạy ứng dụng. Hãy gõ `mvn spring-boot:run` từ thư mục gốc của project để khởi chạy ứng dụng. Bạn sẽ thấy output tương tự như sau:

```shell
$ mvn spring-boot:run

  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_\__, | / / / /
 =========|_|==============|___/=/_/_/_/
 :: Spring Boot ::  (v3.2.5)
....... . . .
....... . . . (log output here)
....... . . .
........ Started MyApplication in 0.906 seconds (process running for 6.514)
```

Nếu bạn mở trình duyệt web tới [localhost:8080](http://localhost:8080), bạn sẽ thấy output sau:

```
Hello World!
```

Để thoát ứng dụng một cách nhẹ nhàng (gracefully), hãy nhấn `ctrl-c`.

### Gradle

Đến đây, ứng dụng của bạn đã có thể hoạt động. Vì bạn đã dùng Gradle plugin `org.springframework.boot`, bạn có sẵn một goal `bootRun` hữu ích để khởi chạy ứng dụng. Hãy gõ `gradle bootRun` từ thư mục gốc của project để khởi chạy ứng dụng. Bạn sẽ thấy output tương tự như sau:

```shell
$ gradle bootRun

  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_\__, | / / / /
 =========|_|==============|___/=/_/_/_/
 :: Spring Boot ::  (v3.2.5)
....... . . .
....... . . . (log output here)
....... . . .
........ Started MyApplication in 0.906 seconds (process running for 6.514)
```

Nếu bạn mở trình duyệt web tới [localhost:8080](http://localhost:8080), bạn sẽ thấy output sau:

```
Hello World!
```

Để thoát ứng dụng một cách nhẹ nhàng (gracefully), hãy nhấn `ctrl-c`.

## 4.4.7. Tạo một jar thực thi (Creating an Executable Jar)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#getting-started.first-application.executable-jar>

Chúng ta kết thúc ví dụ bằng cách tạo một file jar thực thi hoàn toàn khép kín (self-contained) mà ta có thể chạy trên production. Jar thực thi (đôi khi được gọi là "uber jar" hoặc "fat jar") là các file nén chứa các class đã biên dịch của bạn cùng với tất cả các jar dependency mà code của bạn cần để chạy.

> **Jar thực thi và Java**
>
> Java không cung cấp cách tiêu chuẩn để nạp các file jar lồng nhau (các file jar nằm bên trong một jar khác). Điều này có thể gây rắc rối nếu bạn muốn phân phối một ứng dụng khép kín.
>
> Để giải quyết vấn đề này, nhiều lập trình viên dùng "uber" jar. Một uber jar đóng gói tất cả các class từ tất cả các dependency của ứng dụng vào một file nén duy nhất. Vấn đề của cách tiếp cận này là rất khó để biết những thư viện nào có trong ứng dụng. Nó cũng có thể gây rắc rối nếu cùng một tên file được dùng (nhưng với nội dung khác nhau) trong nhiều jar.
>
> Spring Boot chọn một [cách tiếp cận khác](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#appendix.executable-jar) và cho phép bạn thực sự lồng các jar trực tiếp vào nhau.

### Maven

Để tạo một jar thực thi, chúng ta cần thêm `spring-boot-maven-plugin` vào `pom.xml`. Để làm vậy, hãy chèn các dòng sau ngay bên dưới phần `dependencies`:

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

> **LƯU Ý (NOTE):** POM `spring-boot-starter-parent` bao gồm cấu hình `<executions>` để gắn (bind) goal `repackage`. Nếu bạn không dùng parent POM, bạn cần tự khai báo cấu hình này. Xem [tài liệu của plugin](https://docs.spring.io/spring-boot/docs/3.2.5/maven-plugin/reference/htmlsingle/#getting-started) để biết chi tiết.

Lưu file `pom.xml` và chạy `mvn package` từ dòng lệnh như sau:

```shell
$ mvn package

[INFO] Scanning for projects...
[INFO]
[INFO] ------------------------------------------------------------------------
[INFO] Building myproject 0.0.1-SNAPSHOT
[INFO] ------------------------------------------------------------------------
[INFO] .... ..
[INFO] --- maven-jar-plugin:2.4:jar (default-jar) @ myproject ---
[INFO] Building jar: /Users/developer/example/spring-boot-example/target/myproject-0.0.1-SNAPSHOT.jar
[INFO]
[INFO] --- spring-boot-maven-plugin:3.2.5:repackage (default) @ myproject ---
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
```

Nếu nhìn vào thư mục `target`, bạn sẽ thấy `myproject-0.0.1-SNAPSHOT.jar`. File này có kích thước khoảng 18 MB. Nếu muốn xem bên trong, bạn có thể dùng `jar tvf` như sau:

```shell
$ jar tvf target/myproject-0.0.1-SNAPSHOT.jar
```

Bạn cũng sẽ thấy một file nhỏ hơn nhiều tên là `myproject-0.0.1-SNAPSHOT.jar.original` trong thư mục `target`. Đây là file jar gốc mà Maven đã tạo ra trước khi nó được Spring Boot đóng gói lại (repackage).

Để chạy ứng dụng đó, hãy dùng lệnh `java -jar` như sau:

```shell
$ java -jar target/myproject-0.0.1-SNAPSHOT.jar

  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_\__, | / / / /
 =========|_|==============|___/=/_/_/_/
 :: Spring Boot ::  (v3.2.5)
....... . . .
....... . . . (log output here)
....... . . .
........ Started MyApplication in 0.999 seconds (process running for 1.253)
```

Như trước, để thoát ứng dụng, hãy nhấn `ctrl-c`.

### Gradle

Để tạo một jar thực thi, chúng ta cần chạy `gradle bootJar` từ dòng lệnh như sau:

```shell
$ gradle bootJar

BUILD SUCCESSFUL in 639ms
3 actionable tasks: 3 executed
```

Nếu nhìn vào thư mục `build/libs`, bạn sẽ thấy `myproject-0.0.1-SNAPSHOT.jar`. File này có kích thước khoảng 18 MB. Nếu muốn xem bên trong, bạn có thể dùng `jar tvf` như sau:

```shell
$ jar tvf build/libs/myproject-0.0.1-SNAPSHOT.jar
```

Để chạy ứng dụng đó, hãy dùng lệnh `java -jar` như sau:

```shell
$ java -jar build/libs/myproject-0.0.1-SNAPSHOT.jar

  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_\__, | / / / /
 =========|_|==============|___/=/_/_/_/
 :: Spring Boot ::  (v3.2.5)
....... . . .
....... . . . (log output here)
....... . . .
........ Started MyApplication in 0.999 seconds (process running for 1.253)
```

Như trước, để thoát ứng dụng, hãy nhấn `ctrl-c`.

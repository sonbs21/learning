# 6.7. Chạy ứng dụng (Running Your Application)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#using.running-your-application>

Một trong những lợi thế lớn nhất của việc đóng gói ứng dụng thành jar và dùng HTTP server nhúng là bạn có thể chạy ứng dụng giống như bất kỳ ứng dụng nào khác. Điều này cũng áp dụng cho việc debug ứng dụng Spring Boot. Bạn không cần bất kỳ plugin hay extension IDE đặc biệt nào.

> **LƯU Ý (NOTE):** Phần này chỉ đề cập đến việc đóng gói dạng jar. Nếu bạn chọn đóng gói ứng dụng thành file war, hãy xem tài liệu của server và IDE của bạn.

## 6.7.1. Chạy từ IDE (Running From an IDE)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#using.running-your-application.from-an-ide>

Bạn có thể chạy một ứng dụng Spring Boot từ IDE như một ứng dụng Java. Tuy nhiên, trước tiên bạn cần import project. Các bước import khác nhau tùy thuộc vào IDE và hệ thống build của bạn. Hầu hết các IDE có thể import trực tiếp project Maven. Ví dụ, người dùng Eclipse có thể chọn `Import…` → `Existing Maven Projects` từ menu `File`.

Nếu bạn không thể import trực tiếp project vào IDE, bạn có thể sinh metadata cho IDE bằng một build plugin. Maven có các plugin cho [Eclipse](https://maven.apache.org/plugins/maven-eclipse-plugin/) và [IDEA](https://maven.apache.org/plugins/maven-idea-plugin/). Gradle cung cấp plugin cho [nhiều IDE khác nhau](https://docs.gradle.org/current/userguide/userguide.html).

> **MẸO (TIP):** Nếu bạn vô tình chạy một ứng dụng web hai lần, bạn sẽ thấy lỗi "Port already in use". Người dùng Spring Tools có thể dùng nút `Relaunch` thay vì nút `Run` để đảm bảo mọi instance đang chạy đều được đóng lại.

## 6.7.2. Chạy như một ứng dụng đã đóng gói (Running as a Packaged Application)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#using.running-your-application.as-a-packaged-application>

Nếu bạn dùng plugin Maven hoặc Gradle của Spring Boot để tạo jar thực thi, bạn có thể chạy ứng dụng bằng `java -jar`, như trong ví dụ sau:

```shell
$ java -jar target/myapplication-0.0.1-SNAPSHOT.jar
```

Cũng có thể chạy một ứng dụng đã đóng gói với hỗ trợ debug từ xa (remote debugging) được bật. Làm như vậy cho phép bạn gắn (attach) một debugger vào ứng dụng đã đóng gói, như trong ví dụ sau:

```shell
$ java -agentlib:jdwp=server=y,transport=dt_socket,address=8000,suspend=n \
       -jar target/myapplication-0.0.1-SNAPSHOT.jar
```

## 6.7.3. Sử dụng Maven Plugin (Using the Maven Plugin)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#using.running-your-application.with-the-maven-plugin>

Maven plugin của Spring Boot bao gồm goal `run` có thể dùng để nhanh chóng biên dịch và chạy ứng dụng. Ứng dụng chạy ở dạng giải nén (exploded form), giống như khi chạy trong IDE. Ví dụ sau cho thấy một lệnh Maven điển hình để chạy ứng dụng Spring Boot:

```shell
$ mvn spring-boot:run
```

Bạn cũng có thể muốn dùng biến môi trường hệ điều hành `MAVEN_OPTS`, như trong ví dụ sau:

```shell
$ export MAVEN_OPTS=-Xmx1024m
```

## 6.7.4. Sử dụng Gradle Plugin (Using the Gradle Plugin)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#using.running-your-application.with-the-gradle-plugin>

Gradle plugin của Spring Boot cũng bao gồm task `bootRun` có thể dùng để chạy ứng dụng ở dạng giải nén. Task `bootRun` được thêm vào mỗi khi bạn áp dụng các plugin `org.springframework.boot` và `java`, như trong ví dụ sau:

```
$ gradle bootRun
```

Bạn cũng có thể muốn dùng biến môi trường hệ điều hành `JAVA_OPTS`, như trong ví dụ sau:

```
$ export JAVA_OPTS=-Xmx1024m
```

## 6.7.5. Hot Swapping

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#using.running-your-application.hot-swapping>

Vì ứng dụng Spring Boot là ứng dụng Java thông thường, JVM hot-swapping sẽ hoạt động ngay mà không cần cấu hình thêm. JVM hot swapping có phần hạn chế về loại bytecode mà nó có thể thay thế. Để có giải pháp hoàn chỉnh hơn, có thể dùng [JRebel](https://www.jrebel.com/products/jrebel).

Module `spring-boot-devtools` cũng bao gồm hỗ trợ khởi động lại ứng dụng nhanh chóng. Xem [Hot swapping "How-to"](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.hotswapping) để biết chi tiết.

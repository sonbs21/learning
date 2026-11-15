# 7.4. Ghi log (Logging)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.logging>

Spring Boot dùng [Commons Logging](https://commons.apache.org/logging) cho toàn bộ việc ghi log nội bộ nhưng để ngỏ implementation log bên dưới. Các cấu hình mặc định được cung cấp cho [Java Util Logging](https://docs.oracle.com/en/java/javase/17/docs/api/java.logging/java/util/logging/package-summary.html), [Log4j2](https://logging.apache.org/log4j/2.x/), và [Logback](https://logback.qos.ch/). Trong mỗi trường hợp, các logger được cấu hình sẵn để xuất ra console, với tùy chọn xuất ra file cũng có sẵn.

Theo mặc định, nếu bạn dùng các "Starter", Logback được dùng để ghi log. Việc định tuyến Logback phù hợp cũng được bao gồm để đảm bảo các thư viện phụ thuộc dùng Java Util Logging, Commons Logging, Log4J, hoặc SLF4J đều hoạt động đúng.

> **MẸO (TIP):** Có rất nhiều logging framework dành cho Java. Đừng lo nếu danh sách trên có vẻ khó hiểu. Nhìn chung, bạn không cần thay đổi các dependency logging và các giá trị mặc định của Spring Boot hoạt động rất tốt.

> **MẸO (TIP):** Khi bạn triển khai ứng dụng lên servlet container hoặc application server, việc ghi log được thực hiện bằng Java Util Logging API không được định tuyến vào log của ứng dụng. Điều này ngăn log do container hoặc các ứng dụng khác được triển khai trên nó xuất hiện trong log của ứng dụng của bạn.

## 7.4.1. Định dạng log (Log Format)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.logging.log-format>

Output log mặc định của Spring Boot trông giống như ví dụ sau:

```
2024-04-18T17:02:16.177Z  INFO 6498 --- [myapp] [           main] o.s.b.d.f.logexample.MyApplication       : Starting MyApplication using Java 17.0.11 with PID 6498 (/opt/apps/myapp.jar started by myuser in /opt/apps/)
2024-04-18T17:02:16.180Z  INFO 6498 --- [myapp] [           main] o.s.b.d.f.logexample.MyApplication       : No active profile set, falling back to 1 default profile: "default"
2024-04-18T17:02:19.710Z  INFO 6498 --- [myapp] [           main] o.s.b.w.embedded.tomcat.TomcatWebServer  : Tomcat initialized with port 8080 (http)
2024-04-18T17:02:19.734Z  INFO 6498 --- [myapp] [           main] o.apache.catalina.core.StandardService   : Starting service [Tomcat]
2024-04-18T17:02:19.734Z  INFO 6498 --- [myapp] [           main] o.apache.catalina.core.StandardEngine    : Starting Servlet engine: [Apache Tomcat/10.1.20]
2024-04-18T17:02:19.946Z  INFO 6498 --- [myapp] [           main] o.a.c.c.C.[Tomcat].[localhost].[/]       : Initializing Spring embedded WebApplicationContext
2024-04-18T17:02:19.955Z  INFO 6498 --- [myapp] [           main] w.s.c.ServletWebServerApplicationContext : Root WebApplicationContext: initialization completed in 3534 ms
2024-04-18T17:02:21.570Z  INFO 6498 --- [myapp] [           main] o.s.b.w.embedded.tomcat.TomcatWebServer  : Tomcat started on port 8080 (http) with context path ''
2024-04-18T17:02:21.636Z  INFO 6498 --- [myapp] [           main] o.s.b.d.f.logexample.MyApplication       : Started MyApplication in 7.291 seconds (process running for 8.678)
```

Các mục sau được xuất ra:

- Ngày và giờ: Độ chính xác đến mili giây và dễ sắp xếp.
- Mức log: `ERROR`, `WARN`, `INFO`, `DEBUG`, hoặc `TRACE`.
- Process ID.
- Một dấu phân cách `---` để phân biệt điểm bắt đầu của thông điệp log thực sự.
- Tên ứng dụng: Được bao trong dấu ngoặc vuông (theo mặc định chỉ được ghi log nếu `spring.application.name` được đặt)
- Tên thread: Được bao trong dấu ngoặc vuông (có thể bị cắt ngắn khi xuất ra console).
- Correlation ID: Nếu tracing được bật (không hiển thị trong ví dụ trên)
- Tên logger: Đây thường là tên class nguồn (thường được viết tắt).
- Thông điệp log.

> **LƯU Ý (NOTE):** Logback không có mức `FATAL`. Nó được ánh xạ sang `ERROR`.

> **MẸO (TIP):** Nếu bạn có property `spring.application.name` nhưng không muốn nó được ghi log, bạn có thể đặt `logging.include-application-name` thành `false`.

## 7.4.2. Xuất ra console (Console Output)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.logging.console-output>

Cấu hình log mặc định in các thông điệp ra console ngay khi chúng được ghi. Theo mặc định, các thông điệp mức `ERROR`, `WARN` và `INFO` được ghi log. Bạn cũng có thể bật chế độ "debug" bằng cách khởi động ứng dụng với cờ `--debug`.

```shell
$ java -jar myapp.jar --debug
```

> **LƯU Ý (NOTE):** Bạn cũng có thể chỉ định `debug=true` trong `application.properties`.

Khi chế độ debug được bật, một số logger cốt lõi (container nhúng, Hibernate, và Spring Boot) được cấu hình để xuất nhiều thông tin hơn. Bật chế độ debug *không* cấu hình ứng dụng để ghi log tất cả thông điệp ở mức `DEBUG`.

Ngoài ra, bạn có thể bật chế độ "trace" bằng cách khởi động ứng dụng với cờ `--trace` (hoặc `trace=true` trong `application.properties`). Làm như vậy sẽ bật trace logging cho một số logger cốt lõi (container nhúng, việc sinh schema của Hibernate, và toàn bộ hệ sinh thái Spring).

### Output có màu (Color-coded Output)

Nếu terminal của bạn hỗ trợ ANSI, output có màu được dùng để giúp dễ đọc hơn. Bạn có thể đặt `spring.output.ansi.enabled` thành một [giá trị được hỗ trợ](https://docs.spring.io/spring-boot/docs/3.2.5/api/org/springframework/boot/ansi/AnsiOutput.Enabled.html) để ghi đè việc tự động phát hiện.

Việc tô màu được cấu hình bằng conversion word `%clr`. Ở dạng đơn giản nhất, converter tô màu output theo mức log, như trong ví dụ sau:

```
%clr(%5p)
```

Bảng sau mô tả ánh xạ từ mức log sang màu:

| Mức | Màu |
|---|---|
| `FATAL` | Đỏ |
| `ERROR` | Đỏ |
| `WARN` | Vàng |
| `INFO` | Xanh lá |
| `DEBUG` | Xanh lá |
| `TRACE` | Xanh lá |

Ngoài ra, bạn có thể chỉ định màu hoặc kiểu nên được dùng bằng cách cung cấp nó như một tùy chọn cho conversion. Ví dụ, để làm cho chữ có màu vàng, hãy dùng thiết lập sau:

```
%clr(%d{yyyy-MM-dd'T'HH:mm:ss.SSSXXX}){yellow}
```

Các màu và kiểu sau được hỗ trợ:

- `blue`
- `cyan`
- `faint`
- `green`
- `magenta`
- `red`
- `yellow`

## 7.4.3. Xuất ra file (File Output)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.logging.file-output>

Theo mặc định, Spring Boot chỉ ghi log ra console và không ghi file log. Nếu bạn muốn ghi file log ngoài output console, bạn cần đặt property `logging.file.name` hoặc `logging.file.path` (ví dụ, trong `application.properties`).

Bảng sau cho thấy cách các property `logging.*` có thể được dùng cùng nhau:

**Bảng 8. Các property logging**

| `logging.file.name` | `logging.file.path` | Ví dụ | Mô tả |
|---|---|---|---|
| *(không có)* | *(không có)* |  | Chỉ ghi log ra console. |
| File cụ thể | *(không có)* | `my.log` | Ghi vào file log được chỉ định. Tên có thể là một vị trí chính xác hoặc tương đối với thư mục hiện tại. |
| *(không có)* | Thư mục cụ thể | `/var/log` | Ghi `spring.log` vào thư mục được chỉ định. Tên có thể là một vị trí chính xác hoặc tương đối với thư mục hiện tại. |

File log được xoay vòng (rotate) khi đạt 10 MB và, giống như output console, các thông điệp mức `ERROR`, `WARN` và `INFO` được ghi log theo mặc định.

> **MẸO (TIP):** Các property logging độc lập với hạ tầng logging thực tế. Kết quả là, các key cấu hình cụ thể (chẳng hạn như `logback.configurationFile` cho Logback) không được Spring Boot quản lý.

## 7.4.4. Xoay vòng file (File Rotation)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.logging.file-rotation>

Nếu bạn đang dùng Logback, có thể tinh chỉnh các thiết lập xoay vòng log bằng file `application.properties` hoặc `application.yaml`. Với tất cả các hệ thống logging khác, bạn sẽ cần tự cấu hình trực tiếp các thiết lập xoay vòng (ví dụ, nếu bạn dùng Log4j2 thì bạn có thể thêm file `log4j2.xml` hoặc `log4j2-spring.xml`).

Các property chính sách xoay vòng sau được hỗ trợ:

| Tên | Mô tả |
|---|---|
| `logging.logback.rollingpolicy.file-name-pattern` | Mẫu tên file dùng để tạo các file log lưu trữ (archive). |
| `logging.logback.rollingpolicy.clean-history-on-start` | Có dọn dẹp các file log lưu trữ khi ứng dụng khởi động hay không. |
| `logging.logback.rollingpolicy.max-file-size` | Kích thước tối đa của file log trước khi nó được lưu trữ. |
| `logging.logback.rollingpolicy.total-size-cap` | Tổng kích thước tối đa mà các file log lưu trữ có thể chiếm trước khi bị xóa. |
| `logging.logback.rollingpolicy.max-history` | Số lượng file log lưu trữ tối đa được giữ lại (mặc định là 7). |

## 7.4.5. Mức log (Log Levels)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.logging.log-levels>

Tất cả các hệ thống logging được hỗ trợ đều có thể đặt mức logger trong `Environment` của Spring (ví dụ, trong `application.properties`) bằng cách dùng `logging.level.<logger-name>=<level>`, trong đó `level` là một trong TRACE, DEBUG, INFO, WARN, ERROR, FATAL, hoặc OFF. Logger `root` có thể được cấu hình bằng `logging.level.root`.

Ví dụ sau cho thấy các thiết lập logging có thể có trong `application.properties`:

**Properties**

```properties
logging.level.root=warn
logging.level.org.springframework.web=debug
logging.level.org.hibernate=error
```

**Yaml**

```properties
logging:
  level:
    root: "warn"
    org.springframework.web: "debug"
    org.hibernate: "error"
```

Cũng có thể đặt mức logging bằng biến môi trường. Ví dụ, `LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_WEB=DEBUG` sẽ đặt `org.springframework.web` thành `DEBUG`.

> **LƯU Ý (NOTE):** Cách tiếp cận trên chỉ hoạt động cho logging ở cấp package. Vì relaxed binding luôn chuyển biến môi trường thành chữ thường, không thể cấu hình logging cho một class riêng lẻ theo cách này. Nếu bạn cần cấu hình logging cho một class, bạn có thể dùng biến [`SPRING_APPLICATION_JSON`](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.external-config.application-json).

## 7.4.6. Nhóm log (Log Groups)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.logging.log-groups>

Thường sẽ hữu ích nếu có thể nhóm các logger liên quan lại với nhau để chúng có thể được cấu hình cùng một lúc. Ví dụ, bạn có thể thường xuyên thay đổi mức logging cho *tất cả* các logger liên quan đến Tomcat, nhưng bạn không dễ dàng nhớ được các package cấp cao nhất.

Để giúp giải quyết điều này, Spring Boot cho phép bạn định nghĩa các nhóm logging trong `Environment` của Spring. Ví dụ, đây là cách bạn có thể định nghĩa một nhóm "tomcat" bằng cách thêm nó vào `application.properties`:

**Properties**

```properties
logging.group.tomcat=org.apache.catalina,org.apache.coyote,org.apache.tomcat
```

**Yaml**

```yaml
logging:
  group:
    tomcat: "org.apache.catalina,org.apache.coyote,org.apache.tomcat"
```

Sau khi được định nghĩa, bạn có thể thay đổi mức cho tất cả các logger trong nhóm chỉ với một dòng:

**Properties**

```properties
logging.level.tomcat=trace
```

**Yaml**

```yaml
logging:
  level:
    tomcat: "trace"
```

Spring Boot bao gồm các nhóm logging được định nghĩa sẵn sau, có thể dùng ngay:

| Tên | Logger |
|---|---|
| web | `org.springframework.core.codec`, `org.springframework.http`, `org.springframework.web`, `org.springframework.boot.actuate.endpoint.web`, `org.springframework.boot.web.servlet.ServletContextInitializerBeans` |
| sql | `org.springframework.jdbc.core`, `org.hibernate.SQL`, `org.jooq.tools.LoggerListener` |

## 7.4.7. Sử dụng log shutdown hook (Using a Log Shutdown Hook)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.logging.shutdown-hook>

Để giải phóng tài nguyên logging khi ứng dụng kết thúc, một shutdown hook sẽ kích hoạt việc dọn dẹp hệ thống log khi JVM thoát được cung cấp sẵn. Shutdown hook này được đăng ký tự động trừ khi ứng dụng của bạn được triển khai dưới dạng file war. Nếu ứng dụng của bạn có cây phân cấp context phức tạp, shutdown hook có thể không đáp ứng được nhu cầu. Nếu vậy, hãy tắt shutdown hook và tìm hiểu các tùy chọn được cung cấp trực tiếp bởi hệ thống logging bên dưới. Ví dụ, Logback cung cấp [context selector](https://logback.qos.ch/manual/loggingSeparation.html) cho phép mỗi Logger được tạo trong context riêng của nó. Bạn có thể dùng property `logging.register-shutdown-hook` để tắt shutdown hook. Đặt nó thành `false` sẽ vô hiệu hóa việc đăng ký. Bạn có thể đặt property này trong file `application.properties` hoặc `application.yaml`:

**Properties**

```properties
logging.register-shutdown-hook=false
```

**Yaml**

```yaml
logging:
  register-shutdown-hook: false
```

## 7.4.8. Cấu hình log tùy chỉnh (Custom Log Configuration)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.logging.custom-log-configuration>

Các hệ thống logging khác nhau có thể được kích hoạt bằng cách đưa các thư viện phù hợp vào classpath và có thể được tùy chỉnh thêm bằng cách cung cấp một file cấu hình phù hợp ở thư mục gốc của classpath hoặc ở vị trí được chỉ định bởi property sau của `Environment` Spring: `logging.config`.

Bạn có thể buộc Spring Boot dùng một hệ thống logging cụ thể bằng system property `org.springframework.boot.logging.LoggingSystem`. Giá trị nên là tên class đầy đủ (fully qualified class name) của một implementation `LoggingSystem`. Bạn cũng có thể tắt hoàn toàn cấu hình logging của Spring Boot bằng giá trị `none`.

> **LƯU Ý (NOTE):** Vì logging được khởi tạo **trước khi** `ApplicationContext` được tạo, không thể kiểm soát logging từ `@PropertySources` trong các file `@Configuration` của Spring. Cách duy nhất để thay đổi hệ thống logging hoặc tắt nó hoàn toàn là thông qua System properties.

Tùy thuộc vào hệ thống logging của bạn, các file sau được nạp:

| Hệ thống logging | File tùy chỉnh |
|---|---|
| Logback | `logback-spring.xml`, `logback-spring.groovy`, `logback.xml`, hoặc `logback.groovy` |
| Log4j2 | `log4j2-spring.xml` hoặc `log4j2.xml` |
| JDK (Java Util Logging) | `logging.properties` |

> **LƯU Ý (NOTE):** Khi có thể, chúng tôi khuyên bạn nên dùng các biến thể `-spring` cho cấu hình logging (ví dụ, `logback-spring.xml` thay vì `logback.xml`). Nếu bạn dùng các vị trí cấu hình tiêu chuẩn, Spring không thể kiểm soát hoàn toàn việc khởi tạo log.

> **CẢNH BÁO (WARNING):** Có các vấn đề classloading đã biết với Java Util Logging gây ra sự cố khi chạy từ một 'jar thực thi'. Chúng tôi khuyên bạn nên tránh dùng nó khi chạy từ một 'jar thực thi' nếu có thể.

Để hỗ trợ việc tùy chỉnh, một số property khác được chuyển từ `Environment` của Spring sang System properties. Điều này cho phép các property được sử dụng bởi cấu hình của hệ thống logging. Ví dụ, đặt `logging.file.name` trong `application.properties` hoặc `LOGGING_FILE_NAME` dưới dạng biến môi trường sẽ khiến System property `LOG_FILE` được đặt. Các property được chuyển được mô tả trong bảng sau:

| Spring Environment | System Property | Ghi chú |
|---|---|---|
| `logging.exception-conversion-word` | `LOG_EXCEPTION_CONVERSION_WORD` | Conversion word được dùng khi ghi log exception. |
| `logging.file.name` | `LOG_FILE` | Nếu được định nghĩa, nó được dùng trong cấu hình log mặc định. |
| `logging.file.path` | `LOG_PATH` | Nếu được định nghĩa, nó được dùng trong cấu hình log mặc định. |
| `logging.pattern.console` | `CONSOLE_LOG_PATTERN` | Mẫu log dùng trên console (stdout). |
| `logging.pattern.dateformat` | `LOG_DATEFORMAT_PATTERN` | Mẫu appender cho định dạng ngày của log. |
| `logging.charset.console` | `CONSOLE_LOG_CHARSET` | Charset dùng cho việc ghi log ra console. |
| `logging.threshold.console` | `CONSOLE_LOG_THRESHOLD` | Ngưỡng mức log dùng cho việc ghi log ra console. |
| `logging.pattern.file` | `FILE_LOG_PATTERN` | Mẫu log dùng trong file (nếu `LOG_FILE` được bật). |
| `logging.charset.file` | `FILE_LOG_CHARSET` | Charset dùng cho việc ghi log ra file (nếu `LOG_FILE` được bật). |
| `logging.threshold.file` | `FILE_LOG_THRESHOLD` | Ngưỡng mức log dùng cho việc ghi log ra file. |
| `logging.pattern.level` | `LOG_LEVEL_PATTERN` | Định dạng dùng khi hiển thị mức log (mặc định `%5p`). |
| `PID` | `PID` | Process ID hiện tại (được phát hiện nếu có thể và khi chưa được định nghĩa dưới dạng biến môi trường của hệ điều hành). |

Nếu bạn dùng Logback, các property sau cũng được chuyển:

| Spring Environment | System Property | Ghi chú |
|---|---|---|
| `logging.logback.rollingpolicy.file-name-pattern` | `LOGBACK_ROLLINGPOLICY_FILE_NAME_PATTERN` | Mẫu cho tên các file log đã được xoay vòng (mặc định `${LOG_FILE}.%d{yyyy-MM-dd}.%i.gz`). |
| `logging.logback.rollingpolicy.clean-history-on-start` | `LOGBACK_ROLLINGPOLICY_CLEAN_HISTORY_ON_START` | Có dọn dẹp các file log lưu trữ khi khởi động hay không. |
| `logging.logback.rollingpolicy.max-file-size` | `LOGBACK_ROLLINGPOLICY_MAX_FILE_SIZE` | Kích thước file log tối đa. |
| `logging.logback.rollingpolicy.total-size-cap` | `LOGBACK_ROLLINGPOLICY_TOTAL_SIZE_CAP` | Tổng kích thước các bản sao lưu log được giữ lại. |
| `logging.logback.rollingpolicy.max-history` | `LOGBACK_ROLLINGPOLICY_MAX_HISTORY` | Số lượng file log lưu trữ tối đa được giữ lại. |

Tất cả các hệ thống logging được hỗ trợ đều có thể tham khảo System properties khi phân tích các file cấu hình của chúng. Xem các cấu hình mặc định trong `spring-boot.jar` để có ví dụ:

- [Logback](https://github.com/spring-projects/spring-boot/tree/v3.2.5/spring-boot-project/spring-boot/src/main/resources/org/springframework/boot/logging/logback/defaults.xml)
- [Log4j 2](https://github.com/spring-projects/spring-boot/tree/v3.2.5/spring-boot-project/spring-boot/src/main/resources/org/springframework/boot/logging/log4j2/log4j2.xml)
- [Java Util logging](https://github.com/spring-projects/spring-boot/tree/v3.2.5/spring-boot-project/spring-boot/src/main/resources/org/springframework/boot/logging/java/logging-file.properties)

> **MẸO (TIP):** Nếu bạn muốn dùng placeholder trong một property logging, bạn nên dùng [cú pháp của Spring Boot](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.external-config.files.property-placeholders) chứ không phải cú pháp của framework bên dưới. Đáng chú ý, nếu bạn dùng Logback, bạn nên dùng `:` làm dấu phân cách giữa tên property và giá trị mặc định của nó, và không dùng `:-`.

> **MẸO (TIP):** Bạn có thể thêm MDC và các nội dung tùy biến khác vào các dòng log bằng cách chỉ ghi đè `LOG_LEVEL_PATTERN` (hoặc `logging.pattern.level` với Logback). Ví dụ, nếu bạn dùng `logging.pattern.level=user:%X{user} %5p`, thì định dạng log mặc định sẽ chứa một entry MDC cho "user", nếu nó tồn tại, như trong ví dụ sau.

```
2019-08-30 12:30:04.031 user:someone INFO 22174 --- [  nio-8080-exec-0] demo.Controller
Handling authenticated request
```

## 7.4.9. Các extension của Logback (Logback Extensions)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.logging.logback-extensions>

Spring Boot bao gồm một số extension cho Logback có thể giúp ích cho cấu hình nâng cao. Bạn có thể dùng các extension này trong file cấu hình `logback-spring.xml`.

> **LƯU Ý (NOTE):** Vì file cấu hình `logback.xml` tiêu chuẩn được nạp quá sớm, bạn không thể dùng extension trong đó. Bạn cần dùng `logback-spring.xml` hoặc định nghĩa property `logging.config`.

> **CẢNH BÁO (WARNING):** Các extension không thể được dùng với tính năng [quét cấu hình (configuration scanning)](https://logback.qos.ch/manual/configuration.html#autoScan) của Logback. Nếu bạn cố làm vậy, việc thay đổi file cấu hình sẽ dẫn đến một lỗi tương tự như một trong các lỗi sau được ghi log:

```
ERROR in ch.qos.logback.core.joran.spi.Interpreter@4:71 - no applicable action for [springProperty], current ElementPath is [[configuration][springProperty]]
ERROR in ch.qos.logback.core.joran.spi.Interpreter@4:71 - no applicable action for [springProfile], current ElementPath is [[configuration][springProfile]]
```

### Cấu hình theo profile (Profile-specific Configuration)

Thẻ `<springProfile>` cho phép bạn tùy chọn bao gồm hoặc loại trừ các phần cấu hình dựa trên các Spring profile đang hoạt động. Các phần profile được hỗ trợ ở bất kỳ đâu bên trong phần tử `<configuration>`. Dùng thuộc tính `name` để chỉ định profile nào chấp nhận cấu hình. Thẻ `<springProfile>` có thể chứa tên profile (ví dụ `staging`) hoặc một biểu thức profile. Một biểu thức profile cho phép biểu diễn logic profile phức tạp hơn, ví dụ `production & (eu-central | eu-west)`. Xem [tài liệu tham khảo Spring Framework](https://docs.spring.io/spring-framework/reference/6.1/core/beans/environment.html#beans-definition-profiles-java) để biết thêm chi tiết. Đoạn sau cho thấy ba profile mẫu:

```xml
<springProfile name="staging">
    <!-- configuration to be enabled when the "staging" profile is active -->
</springProfile>

<springProfile name="dev | staging">
    <!-- configuration to be enabled when the "dev" or "staging" profiles are active -->
</springProfile>

<springProfile name="!production">
    <!-- configuration to be enabled when the "production" profile is not active -->
</springProfile>
```

### Environment property (Environment Properties)

Thẻ `<springProperty>` cho phép bạn expose các property từ `Environment` của Spring để dùng trong Logback. Làm như vậy có thể hữu ích nếu bạn muốn truy cập các giá trị từ file `application.properties` trong cấu hình Logback. Thẻ này hoạt động tương tự như thẻ `<property>` tiêu chuẩn của Logback. Tuy nhiên, thay vì chỉ định trực tiếp một `value`, bạn chỉ định `source` của property (từ `Environment`). Nếu bạn cần lưu property ở nơi khác ngoài phạm vi `local`, bạn có thể dùng thuộc tính `scope`. Nếu bạn cần một giá trị dự phòng (trong trường hợp property không được đặt trong `Environment`), bạn có thể dùng thuộc tính `defaultValue`. Ví dụ sau cho thấy cách expose property để dùng trong Logback:

```xml
<springProperty scope="context" name="fluentHost" source="myapp.fluentd.host"
        defaultValue="localhost"/>
<appender name="FLUENT" class="ch.qos.logback.more.appenders.DataFluentAppender">
    <remoteHost>${fluentHost}</remoteHost>
    ...
</appender>
```

> **LƯU Ý (NOTE):** `source` phải được chỉ định ở dạng kebab case (chẳng hạn như `my.property-name`). Tuy nhiên, các property có thể được thêm vào `Environment` bằng các quy tắc nới lỏng (relaxed).

## 7.4.10. Các extension của Log4j2 (Log4j2 Extensions)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.logging.log4j2-extensions>

Spring Boot bao gồm một số extension cho Log4j2 có thể giúp ích cho cấu hình nâng cao. Bạn có thể dùng các extension này trong bất kỳ file cấu hình `log4j2-spring.xml` nào.

> **LƯU Ý (NOTE):** Vì file cấu hình `log4j2.xml` tiêu chuẩn được nạp quá sớm, bạn không thể dùng extension trong đó. Bạn cần dùng `log4j2-spring.xml` hoặc định nghĩa property `logging.config`.

> **LƯU Ý (NOTE):** Các extension này thay thế [hỗ trợ Spring Boot](https://logging.apache.org/log4j/2.x/log4j-spring-boot/index.html) do Log4J cung cấp. Bạn nên đảm bảo không đưa module `org.apache.logging.log4j:log4j-spring-boot` vào bản build.

### Cấu hình theo profile (Profile-specific Configuration)

Thẻ `<SpringProfile>` cho phép bạn tùy chọn bao gồm hoặc loại trừ các phần cấu hình dựa trên các Spring profile đang hoạt động. Các phần profile được hỗ trợ ở bất kỳ đâu bên trong phần tử `<Configuration>`. Dùng thuộc tính `name` để chỉ định profile nào chấp nhận cấu hình. Thẻ `<SpringProfile>` có thể chứa tên profile (ví dụ `staging`) hoặc một biểu thức profile. Một biểu thức profile cho phép biểu diễn logic profile phức tạp hơn, ví dụ `production & (eu-central | eu-west)`. Xem [tài liệu tham khảo Spring Framework](https://docs.spring.io/spring-framework/reference/6.1/core/beans/environment.html#beans-definition-profiles-java) để biết thêm chi tiết. Đoạn sau cho thấy ba profile mẫu:

```xml
<SpringProfile name="staging">
    <!-- configuration to be enabled when the "staging" profile is active -->
</SpringProfile>

<SpringProfile name="dev | staging">
    <!-- configuration to be enabled when the "dev" or "staging" profiles are active -->
</SpringProfile>

<SpringProfile name="!production">
    <!-- configuration to be enabled when the "production" profile is not active -->
</SpringProfile>
```

### Tra cứu Environment property (Environment Properties Lookup)

Nếu bạn muốn tham chiếu tới các property từ `Environment` của Spring trong cấu hình Log4j2, bạn có thể dùng các [lookup](https://logging.apache.org/log4j/2.x/manual/lookups.html) có tiền tố `spring:`. Làm như vậy có thể hữu ích nếu bạn muốn truy cập các giá trị từ file `application.properties` trong cấu hình Log4j2.

Ví dụ sau cho thấy cách đặt một property Log4j2 tên là `applicationName` đọc `spring.application.name` từ `Environment` của Spring:

```xml
<Properties>
    <Property name="applicationName">${spring:spring.application.name}</Property>
</Properties>
```

> **LƯU Ý (NOTE):** Key lookup nên được chỉ định ở dạng kebab case (chẳng hạn như `my.property-name`).

### System property của Log4j2 (Log4j2 System Properties)

Log4j2 hỗ trợ một số [System Properties](https://logging.apache.org/log4j/2.x/manual/configuration.html#SystemProperties) có thể dùng để cấu hình nhiều thành phần khác nhau. Ví dụ, system property `log4j2.skipJansi` có thể được dùng để cấu hình việc `ConsoleAppender` có cố dùng output stream [Jansi](https://github.com/fusesource/jansi) trên Windows hay không.

Tất cả các system property được nạp sau khi Log4j2 khởi tạo đều có thể được lấy từ `Environment` của Spring. Ví dụ, bạn có thể thêm `log4j2.skipJansi=false` vào file `application.properties` để `ConsoleAppender` dùng Jansi trên Windows.

> **LƯU Ý (NOTE):** `Environment` của Spring chỉ được xem xét khi system properties và biến môi trường của hệ điều hành không chứa giá trị đang được nạp.

> **CẢNH BÁO (WARNING):** Các system property được nạp trong giai đoạn khởi tạo sớm của Log4j2 không thể tham chiếu tới `Environment` của Spring. Ví dụ, property mà Log4j2 dùng để cho phép chọn implementation Log4j2 mặc định được sử dụng trước khi Spring Environment khả dụng.

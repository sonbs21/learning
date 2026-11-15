# 18.7.1. Cấu hình Logback cho logging (Configure Logback for Logging)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.logging.logback>

Nếu bạn cần áp dụng các tùy chỉnh cho logback vượt ra ngoài những gì có thể đạt được với `application.properties`, bạn sẽ cần thêm một file cấu hình logback chuẩn. Bạn có thể thêm một file `logback.xml` vào gốc classpath để logback tìm thấy. Bạn cũng có thể dùng `logback-spring.xml` nếu muốn dùng [các phần mở rộng Logback của Spring Boot (Spring Boot Logback extensions)](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.logging.logback-extensions).

> **MẸO (TIP):** Tài liệu Logback có một [phần riêng đề cập đến cấu hình](https://logback.qos.ch/manual/configuration.html) khá chi tiết.

Spring Boot cung cấp một số cấu hình logback có thể được `included` (bao gồm) trong cấu hình của riêng bạn. Các phần include này được thiết kế để cho phép áp dụng lại một số quy ước chung nhất định của Spring Boot.

Các file sau được cung cấp trong `org/springframework/boot/logging/logback/`:

- `defaults.xml` - Cung cấp các quy tắc chuyển đổi (conversion rule), các pattern property và các cấu hình logger thông dụng.
- `console-appender.xml` - Thêm một `ConsoleAppender` dùng `CONSOLE_LOG_PATTERN`.
- `file-appender.xml` - Thêm một `RollingFileAppender` dùng `FILE_LOG_PATTERN` và `ROLLING_FILE_NAME_PATTERN` với các thiết lập phù hợp.

Ngoài ra, một file `base.xml` cũ (legacy) được cung cấp để tương thích với các phiên bản Spring Boot trước đây.

Một file `logback.xml` tùy chỉnh điển hình sẽ trông giống như sau:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <include resource="org/springframework/boot/logging/logback/defaults.xml"/>
    <include resource="org/springframework/boot/logging/logback/console-appender.xml" />
    <root level="INFO">
        <appender-ref ref="CONSOLE" />
    </root>
    <logger name="org.springframework.web" level="DEBUG"/>
</configuration>
```

File cấu hình logback của bạn cũng có thể sử dụng các System property mà `LoggingSystem` đảm nhận việc tạo cho bạn:

- `${PID}`: ID của process hiện tại.
- `${LOG_FILE}`: Liệu `logging.file.name` có được đặt trong cấu hình bên ngoài của Boot hay không.
- `${LOG_PATH}`: Liệu `logging.file.path` (đại diện cho một thư mục chứa các file log) có được đặt trong cấu hình bên ngoài của Boot hay không.
- `${LOG_EXCEPTION_CONVERSION_WORD}`: Liệu `logging.exception-conversion-word` có được đặt trong cấu hình bên ngoài của Boot hay không.
- `${ROLLING_FILE_NAME_PATTERN}`: Liệu `logging.pattern.rolling-file-name` có được đặt trong cấu hình bên ngoài của Boot hay không.

Spring Boot cũng cung cấp output terminal có màu ANSI đẹp mắt trên console (nhưng không có trong file log) bằng cách dùng một Logback converter tùy chỉnh. Xem `CONSOLE_LOG_PATTERN` trong cấu hình `defaults.xml` để có ví dụ.

Nếu Groovy có trên classpath, bạn cũng có thể cấu hình Logback bằng `logback.groovy`. Nếu có, thiết lập này được ưu tiên.

> **LƯU Ý (NOTE):** Các phần mở rộng của Spring không được hỗ trợ với cấu hình Groovy. Mọi file `logback-spring.groovy` sẽ không được phát hiện.

## Cấu hình Logback chỉ ghi ra file (Configure Logback for File-only Output)

Nếu bạn muốn vô hiệu hóa logging ra console và chỉ ghi output vào file, bạn cần một `logback-spring.xml` tùy chỉnh import `file-appender.xml` nhưng không import `console-appender.xml`, như trong ví dụ sau:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <include resource="org/springframework/boot/logging/logback/defaults.xml" />
    <property name="LOG_FILE" value="${LOG_FILE:-${LOG_PATH:-${LOG_TEMP:-${java.io.tmpdir:-/tmp}}/}spring.log}"/>
    <include resource="org/springframework/boot/logging/logback/file-appender.xml" />
    <root level="INFO">
        <appender-ref ref="FILE" />
    </root>
</configuration>
```

Bạn cũng cần thêm `logging.file.name` vào `application.properties` hoặc `application.yaml`, như trong ví dụ sau:

**Properties**

```properties
logging.file.name=myapplication.log
```

**Yaml**

```yaml
logging:
  file:
    name: "myapplication.log"
```

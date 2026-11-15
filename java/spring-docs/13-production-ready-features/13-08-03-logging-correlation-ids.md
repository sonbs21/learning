# 13.8.3. Ghi log Correlation ID (Logging Correlation IDs)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#actuator.micrometer-tracing.logging>

Correlation ID cung cấp một cách hữu ích để liên kết các dòng trong file log với span/trace. Nếu bạn đang dùng Micrometer Tracing, Spring Boot sẽ đưa correlation ID vào log theo mặc định.

Correlation ID mặc định được xây dựng từ các giá trị [MDC](https://logback.qos.ch/manual/mdc.html) `traceId` và `spanId`. Ví dụ, nếu Micrometer Tracing đã thêm MDC `traceId` là `803B448A0489F84084905D3093480352` và MDC `spanId` là `3425F23BB2432450`, output log sẽ bao gồm correlation ID `[803B448A0489F84084905D3093480352-3425F23BB2432450]`.

Nếu bạn muốn dùng một định dạng khác cho correlation ID, bạn có thể dùng property `logging.pattern.correlation` để định nghĩa. Ví dụ, đoạn sau sẽ cung cấp một correlation ID cho Logback theo định dạng trước đây được Spring Cloud Sleuth sử dụng:

**Properties**

```properties
logging.pattern.correlation=[${spring.application.name:},%X{traceId:-},%X{spanId:-}] 
logging.include-application-name=false
```

**Yaml**

```yaml
logging:
  pattern:
    correlation: "[${spring.application.name:},%X{traceId:-},%X{spanId:-}] "
  include-application-name: false
```

> **LƯU Ý (NOTE):** Trong ví dụ trên, `logging.include-application-name` được đặt thành `false` để tránh tên ứng dụng bị lặp lại trong các thông điệp log (`logging.pattern.correlation` đã chứa nó). Cũng cần đề cập rằng `logging.pattern.correlation` chứa một khoảng trắng ở cuối để nó được tách biệt với tên logger theo ngay sau theo mặc định.

# 13.5.3. Hỗ trợ OpenTelemetry (OpenTelemetry Support)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#actuator.observability.opentelemetry>

Module actuator của Spring Boot bao gồm hỗ trợ cơ bản cho [OpenTelemetry](https://opentelemetry.io/).

Nó cung cấp một bean kiểu `OpenTelemetry`, và nếu có các bean kiểu `SdkTracerProvider`, `ContextPropagators`, `SdkLoggerProvider` hoặc `SdkMeterProvider` trong application context, chúng sẽ được tự động đăng ký. Ngoài ra, nó cung cấp một bean `Resource`. Các thuộc tính của `Resource` được tự động cấu hình có thể được cấu hình thông qua configuration property `management.opentelemetry.resource-attributes`. Nếu bạn đã định nghĩa bean `Resource` của riêng mình, điều này sẽ không còn áp dụng.

> **LƯU Ý (NOTE):** Spring Boot không cung cấp auto-configuration cho metrics hay logging của OpenTelemetry. Tracing của OpenTelemetry chỉ được tự động cấu hình khi dùng cùng với [Micrometer Tracing](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#actuator.micrometer-tracing).

Các phần tiếp theo sẽ cung cấp thêm chi tiết về logging, metrics và trace.

# 13.8.5. Các implementation tracer (Tracer Implementations)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#actuator.micrometer-tracing.tracer-implementations>

Vì Micrometer Tracer hỗ trợ nhiều implementation tracer, có thể có nhiều tổ hợp dependency khác nhau với Spring Boot.

Tất cả các implementation tracer đều cần dependency `org.springframework.boot:spring-boot-starter-actuator`.

## OpenTelemetry với Zipkin (OpenTelemetry With Zipkin)

Tracing với OpenTelemetry và báo cáo tới Zipkin yêu cầu các dependency sau:

- `io.micrometer:micrometer-tracing-bridge-otel` - kết nối (bridge) Micrometer Observation API với OpenTelemetry.
- `io.opentelemetry:opentelemetry-exporter-zipkin` - báo cáo trace tới Zipkin.

Dùng các configuration property `management.zipkin.tracing.*` để cấu hình việc báo cáo tới Zipkin.

## OpenTelemetry với Wavefront (OpenTelemetry With Wavefront)

Tracing với OpenTelemetry và báo cáo tới Wavefront yêu cầu các dependency sau:

- `io.micrometer:micrometer-tracing-bridge-otel` - kết nối (bridge) Micrometer Observation API với OpenTelemetry.
- `io.micrometer:micrometer-tracing-reporter-wavefront` - báo cáo trace tới Wavefront.

Dùng các configuration property `management.wavefront.*` để cấu hình việc báo cáo tới Wavefront.

## OpenTelemetry với OTLP (OpenTelemetry With OTLP)

Tracing với OpenTelemetry và báo cáo bằng OTLP yêu cầu các dependency sau:

- `io.micrometer:micrometer-tracing-bridge-otel` - kết nối (bridge) Micrometer Observation API với OpenTelemetry.
- `io.opentelemetry:opentelemetry-exporter-otlp` - báo cáo trace tới một collector có thể chấp nhận OTLP.

Dùng các configuration property `management.otlp.tracing.*` để cấu hình việc báo cáo bằng OTLP.

## OpenZipkin Brave với Zipkin (OpenZipkin Brave With Zipkin)

Tracing với OpenZipkin Brave và báo cáo tới Zipkin yêu cầu các dependency sau:

- `io.micrometer:micrometer-tracing-bridge-brave` - kết nối (bridge) Micrometer Observation API với Brave.
- `io.zipkin.reporter2:zipkin-reporter-brave` - báo cáo trace tới Zipkin.

> **LƯU Ý (NOTE):** Nếu project của bạn không dùng Spring MVC hay Spring WebFlux, dependency `io.zipkin.reporter2:zipkin-sender-urlconnection` cũng cần thiết.

Dùng các configuration property `management.zipkin.tracing.*` để cấu hình việc báo cáo tới Zipkin.

## OpenZipkin Brave với Wavefront (OpenZipkin Brave With Wavefront)

Tracing với OpenZipkin Brave và báo cáo tới Wavefront yêu cầu các dependency sau:

- `io.micrometer:micrometer-tracing-bridge-brave` - kết nối (bridge) Micrometer Observation API với Brave.
- `io.micrometer:micrometer-tracing-reporter-wavefront` - báo cáo trace tới Wavefront.

Dùng các configuration property `management.wavefront.*` để cấu hình việc báo cáo tới Wavefront.

# 13.5.4. Hỗ trợ các annotation của Micrometer Observation (Micrometer Observation Annotations support)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#actuator.observability.annotations>

Để bật việc quét các annotation metrics và tracing như `@Timed`, `@Counted`, `@MeterTag` và `@NewSpan`, bạn sẽ cần đặt property `management.observations.annotations.enabled` thành `true`. Tính năng này được Micrometer hỗ trợ trực tiếp, vui lòng tham khảo tài liệu tham khảo của [Micrometer](https://docs.micrometer.io/micrometer/reference/concepts#_the_timed_annotation) và [Micrometer Tracing](https://docs.micrometer.io/tracing/reference/api.html#_aspect_oriented_programming).

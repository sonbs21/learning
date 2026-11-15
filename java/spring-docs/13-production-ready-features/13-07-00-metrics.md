# 13.7. Metrics

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#actuator.metrics>

Spring Boot Actuator cung cấp quản lý dependency và auto-configuration cho [Micrometer](https://micrometer.io), một facade metrics cho ứng dụng hỗ trợ [nhiều hệ thống giám sát](https://docs.micrometer.io/micrometer/reference), bao gồm:

- [AppOptics](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#actuator.metrics.export.appoptics)
- [Atlas](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#actuator.metrics.export.atlas)
- [Datadog](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#actuator.metrics.export.datadog)
- [Dynatrace](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#actuator.metrics.export.dynatrace)
- [Elastic](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#actuator.metrics.export.elastic)
- [Ganglia](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#actuator.metrics.export.ganglia)
- [Graphite](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#actuator.metrics.export.graphite)
- [Humio](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#actuator.metrics.export.humio)
- [Influx](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#actuator.metrics.export.influx)
- [JMX](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#actuator.metrics.export.jmx)
- [KairosDB](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#actuator.metrics.export.kairos)
- [New Relic](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#actuator.metrics.export.newrelic)
- [OpenTelemetry](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#actuator.metrics.export.otlp)
- [Prometheus](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#actuator.metrics.export.prometheus)
- [SignalFx](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#actuator.metrics.export.signalfx)
- [Simple (in-memory - trong bộ nhớ)](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#actuator.metrics.export.simple)
- [Stackdriver](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#actuator.metrics.export.stackdriver)
- [StatsD](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#actuator.metrics.export.statsd)
- [Wavefront](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#actuator.metrics.export.wavefront)

> **MẸO (TIP):** Để tìm hiểu thêm về các khả năng của Micrometer, hãy xem [tài liệu tham khảo](https://docs.micrometer.io/micrometer/reference) của nó, đặc biệt là [phần khái niệm (concepts)](https://docs.micrometer.io/micrometer/reference/concepts).

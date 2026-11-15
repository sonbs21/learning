# 13.8.6. Tích hợp với Micrometer Observation (Integration with Micrometer Observation)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#actuator.micrometer-tracing.micrometer-observation>

Một `TracingAwareMeterObservationHandler` được tự động đăng ký trên `ObservationRegistry`, tạo ra span cho mọi observation đã hoàn tất.

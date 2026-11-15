# 13.6. Logger (Loggers)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#actuator.loggers>

Spring Boot Actuator bao gồm khả năng xem và cấu hình mức log của ứng dụng tại runtime. Bạn có thể xem toàn bộ danh sách hoặc cấu hình của một logger riêng lẻ, bao gồm cả mức logging được cấu hình tường minh lẫn mức logging hiệu lực mà logging framework gán cho nó. Các mức này có thể là một trong:

- `TRACE`
- `DEBUG`
- `INFO`
- `WARN`
- `ERROR`
- `FATAL`
- `OFF`
- `null`

`null` cho biết không có cấu hình tường minh.

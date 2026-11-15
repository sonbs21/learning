# 13.11. Giám sát tiến trình (Process Monitoring)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#actuator.process-monitoring>

Trong module `spring-boot`, bạn có thể tìm thấy hai class để tạo các file thường hữu ích cho việc giám sát tiến trình:

- `ApplicationPidFileWriter` tạo một file chứa PID của ứng dụng (theo mặc định, trong thư mục ứng dụng với tên file `application.pid`).
- `WebServerPortFileWriter` tạo một file (hoặc nhiều file) chứa các cổng của web server đang chạy (theo mặc định, trong thư mục ứng dụng với tên file `application.port`).

Theo mặc định, các writer này không được kích hoạt, nhưng bạn có thể bật chúng:

- [Bằng cách mở rộng cấu hình](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#actuator.process-monitoring.configuration)
- [Bật giám sát tiến trình bằng code](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#actuator.process-monitoring.programmatically)

# 13.11.2. Bật giám sát tiến trình bằng code (Programmatically Enabling Process Monitoring)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#actuator.process-monitoring.programmatically>

Bạn cũng có thể kích hoạt một listener bằng cách gọi phương thức `SpringApplication.addListeners(…)` và truyền vào object `Writer` phù hợp. Phương thức này cũng cho phép bạn tùy chỉnh tên file và đường dẫn trong constructor của `Writer`.

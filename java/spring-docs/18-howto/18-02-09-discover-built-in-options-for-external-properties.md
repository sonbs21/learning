# 18.2.9. Khám phá các tùy chọn có sẵn cho property bên ngoài (Discover Built-in Options for External Properties)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.properties-and-configuration.discover-build-in-options-for-external-properties>

Spring Boot bind các property bên ngoài từ `application.properties` (hoặc các file YAML và những nơi khác) vào một ứng dụng lúc runtime. Không có (và về mặt kỹ thuật không thể có) một danh sách đầy đủ tất cả các property được hỗ trợ ở một nơi duy nhất, vì các đóng góp có thể đến từ các file jar bổ sung trên classpath.

Một ứng dụng đang chạy với các tính năng Actuator có endpoint `configprops` hiển thị tất cả các property đã được bind và có thể bind thông qua `@ConfigurationProperties`.

Phụ lục bao gồm một ví dụ [`application.properties`](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#appendix.application-properties) với danh sách các property phổ biến nhất được Spring Boot hỗ trợ. Danh sách chính xác nhất có được bằng cách tìm trong mã nguồn các annotation `@ConfigurationProperties` và `@Value` cũng như các chỗ thỉnh thoảng dùng `Binder`. Để biết thêm về thứ tự chính xác của việc nạp property, xem "[Cấu hình bên ngoài (Externalized Configuration)](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.external-config)".

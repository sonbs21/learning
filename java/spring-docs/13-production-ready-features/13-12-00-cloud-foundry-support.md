# 13.12. Hỗ trợ Cloud Foundry (Cloud Foundry Support)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#actuator.cloud-foundry>

Module actuator của Spring Boot bao gồm hỗ trợ bổ sung được kích hoạt khi bạn triển khai lên một instance Cloud Foundry tương thích. Đường dẫn `/cloudfoundryapplication` cung cấp một route được bảo mật thay thế tới tất cả các bean `@Endpoint`.

Hỗ trợ mở rộng cho phép các giao diện quản lý của Cloud Foundry (chẳng hạn như ứng dụng web mà bạn có thể dùng để xem các ứng dụng đã triển khai) được bổ sung thêm thông tin actuator của Spring Boot. Ví dụ, một trang trạng thái ứng dụng có thể bao gồm đầy đủ thông tin health thay vì trạng thái "running" hoặc "stopped" thông thường.

> **LƯU Ý (NOTE):** Đường dẫn `/cloudfoundryapplication` không thể được người dùng thông thường truy cập trực tiếp. Để dùng endpoint, bạn phải gửi kèm một UAA token hợp lệ với request.

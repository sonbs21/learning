# 15.3. Kiểm thử GraalVM Native Image (Testing GraalVM Native Images)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#native-image.testing>

Khi viết các ứng dụng native image, chúng tôi khuyên bạn tiếp tục dùng JVM bất cứ khi nào có thể để phát triển phần lớn các unit test và integration test. Điều này sẽ giúp giữ thời gian build của lập trình viên ở mức thấp và cho phép bạn dùng các tích hợp IDE hiện có. Với độ bao phủ test rộng trên JVM, bạn có thể tập trung kiểm thử native image vào những khu vực có khả năng khác biệt.

Với việc kiểm thử native image, bạn thường muốn đảm bảo các khía cạnh sau hoạt động:

- Engine Spring AOT có thể xử lý ứng dụng của bạn, và nó sẽ chạy ở chế độ đã được xử lý AOT.
- GraalVM có đủ gợi ý để đảm bảo có thể tạo ra một native image hợp lệ.

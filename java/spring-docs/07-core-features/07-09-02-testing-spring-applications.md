# 7.9.2. Kiểm thử ứng dụng Spring (Testing Spring Applications)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.testing.spring-applications>

Một trong những lợi thế lớn của dependency injection là nó giúp code của bạn dễ unit test hơn. Bạn có thể khởi tạo các object bằng toán tử `new` mà thậm chí không cần đến Spring. Bạn cũng có thể dùng *mock object* thay cho các dependency thực.

Thông thường, bạn cần vượt ra ngoài unit test và bắt đầu integration test (với một `ApplicationContext` của Spring). Sẽ rất hữu ích nếu có thể thực hiện integration test mà không cần triển khai ứng dụng hoặc kết nối tới hạ tầng khác.

Spring Framework bao gồm một module test chuyên biệt cho việc integration test như vậy. Bạn có thể khai báo dependency trực tiếp tới `org.springframework:spring-test` hoặc dùng "Starter" `spring-boot-starter-test` để kéo nó vào một cách bắc cầu.

Nếu bạn chưa từng dùng module `spring-test`, bạn nên bắt đầu bằng việc đọc [phần liên quan](https://docs.spring.io/spring-framework/reference/6.1/testing.html) trong tài liệu tham khảo Spring Framework.

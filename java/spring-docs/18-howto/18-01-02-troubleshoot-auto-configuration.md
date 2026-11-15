# 18.1.2. Khắc phục sự cố auto-configuration (Troubleshoot Auto-configuration)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.application.troubleshoot-auto-configuration>

Auto-configuration của Spring Boot cố gắng hết sức để "làm đúng", nhưng đôi khi mọi thứ thất bại, và có thể khó biết tại sao.

Có một `ConditionEvaluationReport` thực sự hữu ích có sẵn trong mọi `ApplicationContext` của Spring Boot. Bạn có thể thấy nó nếu bật log output ở mức `DEBUG`. Nếu bạn dùng `spring-boot-actuator` (xem [chương Actuator](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#actuator)), còn có một endpoint `conditions` hiển thị báo cáo dưới dạng JSON. Hãy dùng endpoint đó để debug ứng dụng và xem những tính năng nào đã được Spring Boot thêm vào (và những tính năng nào chưa được thêm) lúc runtime.

Còn nhiều câu hỏi khác có thể được giải đáp bằng cách xem mã nguồn và Javadoc. Khi đọc code, hãy nhớ các quy tắc kinh nghiệm sau:

- Tìm các class có tên `*AutoConfiguration` và đọc mã nguồn của chúng. Đặc biệt chú ý đến các annotation `@Conditional*` để tìm hiểu chúng bật những tính năng nào và khi nào. Thêm `--debug` vào dòng lệnh hoặc một System property `-Ddebug` để có log trên console về tất cả các quyết định auto-configuration đã được đưa ra trong ứng dụng của bạn. Trong một ứng dụng đang chạy có bật actuator, hãy xem endpoint `conditions` (`/actuator/conditions` hoặc tương đương JMX) để có cùng thông tin.
- Tìm các class là `@ConfigurationProperties` (chẳng hạn như [`ServerProperties`](https://github.com/spring-projects/spring-boot/tree/v3.2.5/spring-boot-project/spring-boot-autoconfigure/src/main/java/org/springframework/boot/autoconfigure/web/ServerProperties.java)) và đọc từ đó các tùy chọn cấu hình bên ngoài có sẵn. Annotation `@ConfigurationProperties` có một thuộc tính `name` đóng vai trò tiền tố cho các property bên ngoài. Vì vậy, `ServerProperties` có `prefix="server"` và các property cấu hình của nó là `server.port`, `server.address`, và các property khác. Trong một ứng dụng đang chạy có bật actuator, hãy xem endpoint `configprops`.
- Tìm các chỗ dùng phương thức `bind` trên `Binder` để lấy các giá trị cấu hình một cách tường minh ra khỏi `Environment` theo cách nới lỏng (relaxed). Nó thường được dùng với một tiền tố.
- Tìm các annotation `@Value` bind trực tiếp tới `Environment`.
- Tìm các annotation `@ConditionalOnExpression` bật và tắt các tính năng để phản hồi các biểu thức SpEL, thường được đánh giá với các placeholder được phân giải từ `Environment`.

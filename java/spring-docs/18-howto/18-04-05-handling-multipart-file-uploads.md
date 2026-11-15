# 18.4.5. Xử lý upload file multipart (Handling Multipart File Uploads)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.spring-mvc.multipart-file-uploads>

Spring Boot sử dụng API `jakarta.servlet.http.Part` của servlet 5 để hỗ trợ upload file. Theo mặc định, Spring Boot cấu hình Spring MVC với kích thước tối đa 1MB cho mỗi file và tối đa 10MB dữ liệu file trong một request. Bạn có thể ghi đè các giá trị này, vị trí lưu trữ dữ liệu trung gian (ví dụ, vào thư mục `/tmp`), và ngưỡng mà vượt qua đó dữ liệu được đẩy xuống đĩa bằng cách dùng các property được expose trong class `MultipartProperties`. Ví dụ, nếu bạn muốn chỉ định rằng file không giới hạn kích thước, hãy đặt property `spring.servlet.multipart.max-file-size` thành `-1`.

Hỗ trợ multipart rất hữu ích khi bạn muốn nhận dữ liệu file được mã hóa multipart dưới dạng một tham số được annotate `@RequestParam` kiểu `MultipartFile` trong một phương thức handler của Spring MVC controller.

Xem mã nguồn [`MultipartAutoConfiguration`](https://github.com/spring-projects/spring-boot/tree/v3.2.5/spring-boot-project/spring-boot-autoconfigure/src/main/java/org/springframework/boot/autoconfigure/web/servlet/MultipartAutoConfiguration.java) để biết thêm chi tiết.

> **LƯU Ý (NOTE):** Khuyến nghị dùng hỗ trợ có sẵn của container cho việc upload multipart thay vì đưa thêm một dependency bổ sung như Apache Commons File Upload.

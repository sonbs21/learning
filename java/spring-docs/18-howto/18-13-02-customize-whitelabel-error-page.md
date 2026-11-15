# 18.13.2. Tùy chỉnh trang lỗi 'whitelabel' (Customize the 'whitelabel' Error Page)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.actuator.customize-whitelabel-error-page>

Spring Boot cài đặt một trang lỗi 'whitelabel' mà bạn thấy trên client trình duyệt nếu gặp lỗi server (các client máy tiêu thụ JSON và các media type khác sẽ thấy một response hợp lý với mã lỗi đúng).

> **LƯU Ý (NOTE):** Đặt `server.error.whitelabel.enabled=false` để tắt trang lỗi mặc định. Làm vậy sẽ khôi phục mặc định của servlet container mà bạn đang dùng. Lưu ý rằng Spring Boot vẫn cố gắng phân giải error view, vì vậy có lẽ bạn nên thêm trang lỗi của riêng mình thay vì vô hiệu hóa hoàn toàn nó.

Việc ghi đè trang lỗi bằng trang của riêng bạn phụ thuộc vào công nghệ template mà bạn dùng. Ví dụ, nếu bạn dùng Thymeleaf, bạn có thể thêm một template `error.html`. Nếu bạn dùng FreeMarker, bạn có thể thêm một template `error.ftlh`. Nói chung, bạn cần một `View` được phân giải với tên `error` hoặc một `@Controller` xử lý đường dẫn `/error`. Trừ khi bạn đã thay thế một số cấu hình mặc định, bạn sẽ tìm thấy một `BeanNameViewResolver` trong `ApplicationContext` của mình, vì vậy một `@Bean` tên là `error` sẽ là một cách để làm điều đó. Xem [`ErrorMvcAutoConfiguration`](https://github.com/spring-projects/spring-boot/tree/v3.2.5/spring-boot-project/spring-boot-autoconfigure/src/main/java/org/springframework/boot/autoconfigure/web/servlet/error/ErrorMvcAutoConfiguration.java) để biết thêm các tùy chọn.

Xem thêm phần "[Xử lý lỗi (Error Handling)](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#web.servlet.spring-mvc.error-handling)" để biết chi tiết về cách đăng ký các handler trong servlet container.

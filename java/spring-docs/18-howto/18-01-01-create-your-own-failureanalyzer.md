# 18.1.1. Tạo FailureAnalyzer của riêng bạn (Create Your Own FailureAnalyzer)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.application.failure-analyzer>

[`FailureAnalyzer`](https://docs.spring.io/spring-boot/docs/3.2.5/api/org/springframework/boot/diagnostics/FailureAnalyzer.html) là một cách tuyệt vời để chặn một exception lúc khởi động và biến nó thành một thông điệp dễ đọc với con người, được bọc trong một [`FailureAnalysis`](https://docs.spring.io/spring-boot/docs/3.2.5/api/org/springframework/boot/diagnostics/FailureAnalysis.html). Spring Boot cung cấp một analyzer như vậy cho các exception liên quan đến application context, các validation JSR-303, và nhiều thứ khác. Bạn cũng có thể tạo analyzer của riêng mình.

`AbstractFailureAnalyzer` là một phần mở rộng tiện lợi của `FailureAnalyzer`, nó kiểm tra sự hiện diện của một kiểu exception được chỉ định trong exception cần xử lý. Bạn có thể kế thừa từ nó để implementation của bạn chỉ có cơ hội xử lý exception khi exception đó thực sự hiện diện. Nếu vì bất kỳ lý do gì mà bạn không thể xử lý exception, hãy trả về `null` để nhường cơ hội xử lý exception cho một implementation khác.

Các implementation `FailureAnalyzer` phải được đăng ký trong `META-INF/spring.factories`. Ví dụ sau đăng ký `ProjectConstraintViolationFailureAnalyzer`:

```properties
org.springframework.boot.diagnostics.FailureAnalyzer=\
com.example.ProjectConstraintViolationFailureAnalyzer
```

> **LƯU Ý (NOTE):** Nếu bạn cần truy cập `BeanFactory` hoặc `Environment`, hãy khai báo chúng làm tham số constructor trong implementation `FailureAnalyzer` của bạn.

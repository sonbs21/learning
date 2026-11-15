# 6.3. Các class cấu hình (Configuration Classes)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#using.configuration-classes>

Spring Boot ưu tiên cấu hình dựa trên Java (Java-based configuration). Mặc dù có thể dùng `SpringApplication` với các nguồn XML, chúng tôi thường khuyên rằng nguồn chính của bạn nên là một class `@Configuration` duy nhất. Thông thường, class định nghĩa phương thức `main` là một ứng viên tốt để làm `@Configuration` chính.

> **MẸO (TIP):** Nhiều ví dụ cấu hình Spring đã được đăng trên Internet sử dụng cấu hình XML. Nếu có thể, hãy luôn cố gắng dùng cấu hình tương đương dựa trên Java. Tìm kiếm các annotation `Enable*` có thể là một điểm khởi đầu tốt.

## 6.3.1. Import thêm các class cấu hình (Importing Additional Configuration Classes)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#using.configuration-classes.importing-additional-configuration>

Bạn không cần đặt tất cả `@Configuration` vào một class duy nhất. Annotation `@Import` có thể được dùng để import thêm các class cấu hình. Ngoài ra, bạn có thể dùng `@ComponentScan` để tự động nhận diện tất cả các Spring component, bao gồm cả các class `@Configuration`.

## 6.3.2. Import cấu hình XML (Importing XML Configuration)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#using.configuration-classes.importing-xml-configuration>

Nếu bạn nhất định phải dùng cấu hình dựa trên XML, chúng tôi khuyên bạn vẫn nên bắt đầu với một class `@Configuration`. Sau đó, bạn có thể dùng annotation `@ImportResource` để nạp các file cấu hình XML.

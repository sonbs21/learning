# 4.1. Giới thiệu Spring Boot (Introducing Spring Boot)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#getting-started.introducing-spring-boot>

Spring Boot giúp bạn tạo ra các ứng dụng dựa trên Spring, chạy độc lập (stand-alone), đạt chuẩn production (production-grade) mà bạn có thể chạy ngay. Chúng tôi có một quan điểm định sẵn (opinionated view) về nền tảng Spring và các thư viện bên thứ ba, để bạn có thể bắt đầu với ít phiền phức nhất. Hầu hết các ứng dụng Spring Boot cần rất ít cấu hình Spring.

Bạn có thể dùng Spring Boot để tạo các ứng dụng Java có thể khởi chạy bằng `java -jar` hoặc bằng cách triển khai war truyền thống hơn.

Các mục tiêu chính của chúng tôi là:

- Cung cấp trải nghiệm bắt đầu nhanh hơn một cách triệt để và dễ tiếp cận rộng rãi cho mọi hoạt động phát triển với Spring.
- Có quan điểm định sẵn ngay từ đầu (opinionated out of the box), nhưng nhanh chóng "tránh đường" khi yêu cầu bắt đầu khác với các giá trị mặc định.
- Cung cấp một loạt các tính năng phi chức năng (non-functional features) phổ biến cho nhiều loại dự án lớn (chẳng hạn như server nhúng, bảo mật, metrics, health check và cấu hình bên ngoài (externalized configuration)).
- Hoàn toàn không sinh code (khi không nhắm đến native image) và không yêu cầu cấu hình XML.

# Phụ lục B: Configuration metadata (Appendix B: Configuration Metadata)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#appendix.configuration-metadata>

Các jar của Spring Boot bao gồm các file metadata cung cấp chi tiết về tất cả các property cấu hình được hỗ trợ. Các file này được thiết kế để cho phép các nhà phát triển IDE đưa ra trợ giúp theo ngữ cảnh và "code completion" khi người dùng đang làm việc với các file `application.properties` hoặc `application.yaml`.

Phần lớn file metadata được sinh tự động lúc biên dịch bằng cách xử lý tất cả các mục được annotate với `@ConfigurationProperties`. Tuy nhiên, có thể [viết thủ công một phần metadata](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#appendix.configuration-metadata.annotation-processor.adding-additional-metadata) cho các trường hợp đặc biệt hoặc các trường hợp sử dụng nâng cao hơn.

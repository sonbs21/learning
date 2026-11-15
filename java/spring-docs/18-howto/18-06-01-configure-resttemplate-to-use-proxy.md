# 18.6.1. Cấu hình RestTemplate dùng proxy (Configure RestTemplate to Use a Proxy)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.http-clients.rest-template-proxy-configuration>

Như đã mô tả trong [Tùy chỉnh RestTemplate (RestTemplate Customization)](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#io.rest-client.resttemplate.customization), bạn có thể dùng một `RestTemplateCustomizer` với `RestTemplateBuilder` để xây dựng một `RestTemplate` tùy chỉnh. Đây là cách tiếp cận được khuyến nghị để tạo một `RestTemplate` được cấu hình để dùng proxy.

Các chi tiết chính xác của cấu hình proxy phụ thuộc vào client request factory bên dưới đang được dùng.

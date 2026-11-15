# 13.10. Ghi lại các trao đổi HTTP (Recording HTTP Exchanges)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#actuator.http-exchanges>

Bạn có thể bật việc ghi lại các trao đổi HTTP bằng cách cung cấp một bean kiểu `HttpExchangeRepository` trong cấu hình của ứng dụng. Để tiện lợi, Spring Boot cung cấp `InMemoryHttpExchangeRepository`, theo mặc định lưu 100 lần trao đổi request-response gần nhất. `InMemoryHttpExchangeRepository` bị hạn chế so với các giải pháp tracing, và chúng tôi khuyên chỉ dùng nó cho môi trường phát triển. Với môi trường production, chúng tôi khuyên dùng một giải pháp tracing hoặc observability sẵn sàng cho production, như Zipkin hoặc OpenTelemetry. Ngoài ra, bạn có thể tạo `HttpExchangeRepository` của riêng mình.

Bạn có thể dùng endpoint `httpexchanges` để lấy thông tin về các lần trao đổi request-response được lưu trong `HttpExchangeRepository`.

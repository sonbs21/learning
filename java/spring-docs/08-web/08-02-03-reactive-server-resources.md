# 8.2.3. Cấu hình tài nguyên cho reactive server (Reactive Server Resources Configuration)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#web.reactive.reactive-server-resources-configuration>

Khi tự động cấu hình một server Reactor Netty hoặc Jetty, Spring Boot sẽ tạo các bean cụ thể cung cấp tài nguyên HTTP cho instance server: `ReactorResourceFactory` hoặc `JettyResourceFactory`.

Theo mặc định, các tài nguyên đó cũng sẽ được chia sẻ với các client Reactor Netty và Jetty để có hiệu năng tối ưu, với điều kiện:

- cùng một công nghệ được dùng cho server và client
- instance client được xây dựng bằng bean `WebClient.Builder` được Spring Boot tự động cấu hình

Lập trình viên có thể ghi đè cấu hình tài nguyên cho Jetty và Reactor Netty bằng cách cung cấp một bean `ReactorResourceFactory` hoặc `JettyResourceFactory` tùy chỉnh - điều này sẽ được áp dụng cho cả client và server.

Bạn có thể tìm hiểu thêm về cấu hình tài nguyên ở phía client trong [phần WebClient Runtime](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#io.rest-client.webclient.runtime).

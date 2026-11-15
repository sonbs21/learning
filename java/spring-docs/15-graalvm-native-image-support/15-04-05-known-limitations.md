# 15.4.5. Các hạn chế đã biết (Known Limitations)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#native-image.advanced.known-limitations>

GraalVM native image là một công nghệ đang phát triển và không phải tất cả các thư viện đều cung cấp hỗ trợ. Cộng đồng GraalVM đang hỗ trợ bằng cách cung cấp [reachability metadata](https://github.com/oracle/graalvm-reachability-metadata) cho các project chưa tự cung cấp. Bản thân Spring không chứa gợi ý cho các thư viện bên thứ ba mà thay vào đó dựa vào project reachability metadata.

Nếu bạn gặp vấn đề khi sinh native image cho các ứng dụng Spring Boot, vui lòng xem trang [Spring Boot with GraalVM](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-with-GraalVM) trên wiki của Spring Boot. Bạn cũng có thể đóng góp issue cho project [spring-aot-smoke-tests](https://github.com/spring-projects/spring-aot-smoke-tests) trên GitHub, project được dùng để xác nhận các loại ứng dụng phổ biến hoạt động như mong đợi.

Nếu bạn tìm thấy một thư viện không hoạt động với GraalVM, vui lòng tạo một issue trên [project reachability metadata](https://github.com/oracle/graalvm-reachability-metadata).

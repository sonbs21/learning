# 10.5.3. Hỗ trợ RSocket trong Spring Messaging (Spring Messaging RSocket support)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#messaging.rsocket.messaging>

Spring Boot sẽ tự động cấu hình hạ tầng Spring Messaging cho RSocket.

Điều này có nghĩa là Spring Boot sẽ tạo một bean `RSocketMessageHandler` để xử lý các request RSocket tới ứng dụng của bạn.

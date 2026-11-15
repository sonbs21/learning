# 11.6. Gọi REST service (Calling REST Services)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#io.rest-client>

Spring Boot cung cấp nhiều cách tiện lợi để gọi các REST service từ xa. Nếu bạn đang phát triển một ứng dụng reactive không chặn (non-blocking) và đang dùng Spring WebFlux, bạn có thể dùng `WebClient`. Nếu bạn thích các API chặn (blocking), bạn có thể dùng `RestClient` hoặc `RestTemplate`.

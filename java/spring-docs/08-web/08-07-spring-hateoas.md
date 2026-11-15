# 8.7. Spring HATEOAS

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#web.spring-hateoas>

Nếu bạn phát triển một RESTful API có dùng hypermedia, Spring Boot cung cấp auto-configuration cho Spring HATEOAS, hoạt động tốt với hầu hết các ứng dụng. Auto-configuration thay thế nhu cầu dùng `@EnableHypermediaSupport` và đăng ký một số bean để giúp việc xây dựng các ứng dụng dựa trên hypermedia dễ dàng hơn, bao gồm một `LinkDiscoverers` (cho hỗ trợ phía client) và một `ObjectMapper` được cấu hình để chuyển đổi (marshal) response đúng cách sang biểu diễn mong muốn. `ObjectMapper` được tùy chỉnh bằng cách đặt các property `spring.jackson.*` khác nhau hoặc, nếu có, bằng một bean `Jackson2ObjectMapperBuilder`.

Bạn có thể kiểm soát cấu hình của Spring HATEOAS bằng cách dùng `@EnableHypermediaSupport`. Lưu ý rằng làm như vậy sẽ vô hiệu hóa việc tùy chỉnh `ObjectMapper` đã mô tả ở trên.

> **CẢNH BÁO (WARNING):** `spring-boot-starter-hateoas` dành riêng cho Spring MVC và không nên được kết hợp với Spring WebFlux. Để dùng Spring HATEOAS với Spring WebFlux, bạn có thể thêm dependency trực tiếp tới `org.springframework.hateoas:spring-hateoas` cùng với `spring-boot-starter-webflux`.

Theo mặc định, các request chấp nhận `application/json` sẽ nhận được response `application/hal+json`. Để tắt hành vi này, hãy đặt `spring.hateoas.use-hal-as-default-json-media-type` thành `false` và định nghĩa một `HypermediaMappingInformation` hoặc `HalConfiguration` để cấu hình Spring HATEOAS đáp ứng nhu cầu của ứng dụng và các client của nó.

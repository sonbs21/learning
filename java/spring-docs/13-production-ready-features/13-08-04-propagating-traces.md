# 13.8.4. Lan truyền trace (Propagating Traces)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#actuator.micrometer-tracing.propagating-traces>

Để tự động lan truyền trace qua mạng, hãy dùng [`RestTemplateBuilder`](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#io.rest-client.resttemplate) hoặc [`WebClient.Builder`](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#io.rest-client.webclient) được tự động cấu hình để xây dựng client.

> **CẢNH BÁO (WARNING):** Nếu bạn tạo `WebClient` hoặc `RestTemplate` mà không dùng các builder được tự động cấu hình, việc lan truyền trace tự động sẽ không hoạt động!

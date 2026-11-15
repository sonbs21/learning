# 18.8.14. Expose Spring Data repository dưới dạng REST endpoint (Expose Spring Data Repositories as REST Endpoint)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.data-access.exposing-spring-data-repositories-as-rest>

Spring Data REST có thể expose các implementation `Repository` dưới dạng REST endpoint cho bạn, với điều kiện Spring MVC đã được bật cho ứng dụng.

Spring Boot expose một tập các property hữu ích (từ namespace `spring.data.rest`) để tùy chỉnh [`RepositoryRestConfiguration`](https://docs.spring.io/spring-data/rest/docs/4.2.5/api/org/springframework/data/rest/core/config/RepositoryRestConfiguration.html). Nếu bạn cần cung cấp thêm tùy chỉnh, bạn nên dùng một bean [`RepositoryRestConfigurer`](https://docs.spring.io/spring-data/rest/docs/4.2.5/api/org/springframework/data/rest/webmvc/config/RepositoryRestConfigurer.html).

> **LƯU Ý (NOTE):** Nếu bạn không chỉ định order nào cho `RepositoryRestConfigurer` tùy chỉnh của mình, nó sẽ chạy sau cái mà Spring Boot dùng nội bộ. Nếu bạn cần chỉ định một order, hãy đảm bảo nó lớn hơn 0.

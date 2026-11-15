# 13.3. Giám sát và quản lý qua HTTP (Monitoring and Management Over HTTP)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#actuator.monitoring>

Nếu bạn đang phát triển một ứng dụng web, Spring Boot Actuator tự động cấu hình tất cả các endpoint đã bật để được expose qua HTTP. Quy ước mặc định là dùng `id` của endpoint với tiền tố `/actuator` làm đường dẫn URL. Ví dụ, `health` được expose là `/actuator/health`.

> **MẸO (TIP):** Actuator được hỗ trợ gốc với Spring MVC, Spring WebFlux, và Jersey. Nếu có cả Jersey và Spring MVC, Spring MVC sẽ được dùng.

> **LƯU Ý (NOTE):** Jackson là một dependency bắt buộc để có được các response JSON đúng như được tài liệu hóa trong tài liệu API ([HTML](https://docs.spring.io/spring-boot/docs/3.2.5/actuator-api/htmlsingle) hoặc [PDF](https://docs.spring.io/spring-boot/docs/3.2.5/actuator-api/pdf/spring-boot-actuator-web-api.pdf)).

# 18.8.3. Dùng Spring Data repository (Use Spring Data Repositories)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.data-access.spring-data-repositories>

Spring Data có thể tạo các implementation của các interface `@Repository` thuộc nhiều loại khác nhau. Spring Boot xử lý tất cả điều đó cho bạn, miễn là các annotation `@Repository` đó nằm trong một trong các [auto-configuration package](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#using.auto-configuration.packages), thường là package (hoặc một sub-package) của class ứng dụng chính được annotate với `@SpringBootApplication` hoặc `@EnableAutoConfiguration`.

Với nhiều ứng dụng, tất cả những gì bạn cần là đặt đúng các dependency Spring Data lên classpath. Có `spring-boot-starter-data-jpa` cho JPA, `spring-boot-starter-data-mongodb` cho Mongodb, và nhiều starter khác cho các công nghệ được hỗ trợ. Để bắt đầu, hãy tạo một số repository interface để xử lý các object `@Entity` của bạn.

Spring Boot xác định vị trí của các định nghĩa `@Repository` bằng cách quét các [auto-configuration package](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#using.auto-configuration.packages). Để kiểm soát nhiều hơn, hãy dùng các annotation `@Enable…Repositories` từ Spring Data.

Để biết thêm về Spring Data, xem [trang dự án Spring Data](https://spring.io/projects/spring-data).

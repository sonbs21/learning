# 18.8.12. Dùng Spring Data JPA và Mongo repository (Use Spring Data JPA and Mongo Repositories)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.data-access.use-spring-data-jpa-and-mongo-repositories>

Spring Data JPA và Spring Data Mongo đều có thể tự động tạo các implementation `Repository` cho bạn. Nếu cả hai cùng có mặt trên classpath, bạn có thể phải thực hiện thêm một số cấu hình để báo cho Spring Boot biết cần tạo những repository nào. Cách tường minh nhất để làm điều đó là dùng các annotation chuẩn của Spring Data `@EnableJpaRepositories` và `@EnableMongoRepositories` và cung cấp vị trí của các interface `Repository`.

Cũng có các cờ (`spring.data.*.repositories.enabled` và `spring.data.*.repositories.type`) mà bạn có thể dùng để bật và tắt các repository được auto-configure trong cấu hình bên ngoài. Làm vậy rất hữu ích, chẳng hạn, trong trường hợp bạn muốn tắt các Mongo repository nhưng vẫn dùng `MongoTemplate` được auto-configure.

Cùng trở ngại và cùng các tính năng đó cũng tồn tại với các loại Spring Data repository được auto-configure khác (Elasticsearch, Redis, và các loại khác). Để làm việc với chúng, hãy đổi tên các annotation và cờ cho phù hợp.

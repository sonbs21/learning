# 11.2. Hazelcast

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#io.hazelcast>

Nếu [Hazelcast](https://hazelcast.com/) có trên classpath và tìm thấy một cấu hình phù hợp, Spring Boot tự động cấu hình một `HazelcastInstance` mà bạn có thể inject vào ứng dụng.

Spring Boot trước tiên cố gắng tạo một client bằng cách kiểm tra các tùy chọn cấu hình sau:

- Sự hiện diện của một bean `com.hazelcast.client.config.ClientConfig`.
- Một file cấu hình được định nghĩa bởi property `spring.hazelcast.config`.
- Sự hiện diện của system property `hazelcast.client.config`.
- Một file `hazelcast-client.xml` trong thư mục làm việc hoặc ở thư mục gốc của classpath.
- Một file `hazelcast-client.yaml` (hoặc `hazelcast-client.yml`) trong thư mục làm việc hoặc ở thư mục gốc của classpath.

Nếu không thể tạo client, Spring Boot cố gắng cấu hình một server nhúng. Nếu bạn định nghĩa một bean `com.hazelcast.config.Config`, Spring Boot sẽ dùng nó. Nếu cấu hình của bạn định nghĩa một tên instance, Spring Boot cố gắng định vị một instance hiện có thay vì tạo một instance mới.

Bạn cũng có thể chỉ định file cấu hình Hazelcast cần dùng thông qua cấu hình, như trong ví dụ sau:

**Properties**

```properties
spring.hazelcast.config=classpath:config/my-hazelcast.xml
```

**Yaml**

```yaml
spring:
  hazelcast:
    config: "classpath:config/my-hazelcast.xml"
```

Nếu không, Spring Boot cố gắng tìm cấu hình Hazelcast từ các vị trí mặc định: `hazelcast.xml` trong thư mục làm việc hoặc ở thư mục gốc của classpath, hoặc một bản YAML tương ứng ở cùng các vị trí. Chúng tôi cũng kiểm tra xem system property `hazelcast.config` có được đặt hay không. Xem [tài liệu Hazelcast](https://docs.hazelcast.org/docs/latest/manual/html-single/) để biết thêm chi tiết.

> **MẸO (TIP):** Theo mặc định, `@SpringAware` trên các component Hazelcast được hỗ trợ. `ManagementContext` có thể được ghi đè bằng cách khai báo một bean `HazelcastConfigCustomizer` với `@Order` lớn hơn không.

> **LƯU Ý (NOTE):** Spring Boot cũng có [hỗ trợ caching tường minh cho Hazelcast](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#io.caching.provider.hazelcast). Nếu caching được bật, `HazelcastInstance` sẽ được tự động bọc trong một implementation `CacheManager`.

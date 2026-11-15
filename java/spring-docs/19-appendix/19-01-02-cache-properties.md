# A.2. Property cache (Cache Properties)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#appendix.application-properties.cache>

| Tên | Mô tả | Giá trị mặc định |
|---|---|---|
| [`spring.cache.cache-names`](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#application-properties.cache.spring.cache.cache-names) | Danh sách tên cache cần tạo phân tách bằng dấu phẩy, nếu được cache manager bên dưới hỗ trợ. Thông thường, điều này vô hiệu hóa khả năng tạo thêm cache ngay lập tức (on-the-fly). |  |
| [`spring.cache.caffeine.spec`](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#application-properties.cache.spring.cache.caffeine.spec) | Spec dùng để tạo cache. Xem CaffeineSpec để biết thêm chi tiết về định dạng spec. |  |
| [`spring.cache.couchbase.expiration`](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#application-properties.cache.spring.cache.couchbase.expiration) | Thời gian hết hạn của entry. Theo mặc định các entry không bao giờ hết hạn. Lưu ý rằng giá trị này cuối cùng được chuyển đổi sang giây. |  |
| [`spring.cache.infinispan.config`](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#application-properties.cache.spring.cache.infinispan.config) | Vị trí của file cấu hình dùng để khởi tạo Infinispan. |  |
| [`spring.cache.jcache.config`](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#application-properties.cache.spring.cache.jcache.config) | Vị trí của file cấu hình dùng để khởi tạo cache manager. File cấu hình phụ thuộc vào implementation cache bên dưới. |  |
| [`spring.cache.jcache.provider`](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#application-properties.cache.spring.cache.jcache.provider) | Tên đầy đủ của implementation CachingProvider dùng để lấy cache manager tuân thủ JSR-107. Chỉ cần thiết nếu có nhiều hơn một implementation JSR-107 trên classpath. |  |
| [`spring.cache.redis.cache-null-values`](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#application-properties.cache.spring.cache.redis.cache-null-values) | Cho phép cache các giá trị null. | `true` |
| [`spring.cache.redis.enable-statistics`](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#application-properties.cache.spring.cache.redis.enable-statistics) | Có bật thống kê cache hay không. | `false` |
| [`spring.cache.redis.key-prefix`](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#application-properties.cache.spring.cache.redis.key-prefix) | Tiền tố key. |  |
| [`spring.cache.redis.time-to-live`](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#application-properties.cache.spring.cache.redis.time-to-live) | Thời gian hết hạn của entry. Theo mặc định các entry không bao giờ hết hạn. |  |
| [`spring.cache.redis.use-key-prefix`](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#application-properties.cache.spring.cache.redis.use-key-prefix) | Có dùng tiền tố key khi ghi vào Redis hay không. | `true` |
| [`spring.cache.type`](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#application-properties.cache.spring.cache.type) | Kiểu cache. Theo mặc định, được tự động phát hiện theo environment. |  |

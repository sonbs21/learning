# 13.2. Endpoint (Endpoints)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#actuator.endpoints>

Các actuator endpoint cho phép bạn giám sát và tương tác với ứng dụng. Spring Boot bao gồm một số endpoint có sẵn và cho phép bạn thêm endpoint của riêng mình. Ví dụ, endpoint `health` cung cấp thông tin health (tình trạng sức khỏe) cơ bản của ứng dụng.

Bạn có thể [bật hoặc tắt](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#actuator.endpoints.enabling) từng endpoint riêng lẻ và [expose chúng (làm cho chúng có thể truy cập từ xa) qua HTTP hoặc JMX](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#actuator.endpoints.exposing). Một endpoint được coi là khả dụng khi nó vừa được bật vừa được expose. Các endpoint có sẵn chỉ được tự động cấu hình khi chúng khả dụng. Hầu hết các ứng dụng chọn expose qua HTTP, trong đó ID của endpoint và tiền tố `/actuator` được ánh xạ tới một URL. Ví dụ, theo mặc định, endpoint `health` được ánh xạ tới `/actuator/health`.

> **MẸO (TIP):** Để tìm hiểu thêm về các endpoint của Actuator và định dạng request/response của chúng, hãy xem tài liệu API riêng ([HTML](https://docs.spring.io/spring-boot/docs/3.2.5/actuator-api/htmlsingle) hoặc [PDF](https://docs.spring.io/spring-boot/docs/3.2.5/actuator-api/pdf/spring-boot-actuator-web-api.pdf)).

Các endpoint không phụ thuộc công nghệ sau có sẵn:

| ID | Mô tả |
|---|---|
| `auditevents` | Expose thông tin audit event cho ứng dụng hiện tại. Yêu cầu một bean `AuditEventRepository`. |
| `beans` | Hiển thị danh sách đầy đủ tất cả các Spring bean trong ứng dụng. |
| `caches` | Expose các cache có sẵn. |
| `conditions` | Hiển thị các điều kiện đã được đánh giá trên các class cấu hình và auto-configuration và lý do tại sao chúng khớp hoặc không khớp. |
| `configprops` | Hiển thị danh sách tổng hợp tất cả các `@ConfigurationProperties`. Chịu sự [sanitization](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#actuator.endpoints.sanitization). |
| `env` | Expose các property từ `ConfigurableEnvironment` của Spring. Chịu sự [sanitization](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#actuator.endpoints.sanitization). |
| `flyway` | Hiển thị mọi migration database Flyway đã được áp dụng. Yêu cầu một hoặc nhiều bean `Flyway`. |
| `health` | Hiển thị thông tin health của ứng dụng. |
| `httpexchanges` | Hiển thị thông tin trao đổi HTTP (theo mặc định, 100 lần trao đổi HTTP request-response gần nhất). Yêu cầu một bean `HttpExchangeRepository`. |
| `info` | Hiển thị thông tin tùy ý về ứng dụng. |
| `integrationgraph` | Hiển thị đồ thị Spring Integration. Yêu cầu dependency tới `spring-integration-core`. |
| `loggers` | Hiển thị và sửa đổi cấu hình của các logger trong ứng dụng. |
| `liquibase` | Hiển thị mọi migration database Liquibase đã được áp dụng. Yêu cầu một hoặc nhiều bean `Liquibase`. |
| `metrics` | Hiển thị thông tin "metrics" cho ứng dụng hiện tại. |
| `mappings` | Hiển thị danh sách tổng hợp tất cả các đường dẫn `@RequestMapping`. |
| `quartz` | Hiển thị thông tin về các job của Quartz Scheduler. Chịu sự [sanitization](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#actuator.endpoints.sanitization). |
| `scheduledtasks` | Hiển thị các tác vụ được lập lịch trong ứng dụng. |
| `sessions` | Cho phép truy xuất và xóa các session của người dùng từ một kho lưu session dựa trên Spring Session. Yêu cầu một ứng dụng web dựa trên servlet dùng Spring Session. |
| `shutdown` | Cho phép tắt ứng dụng một cách nhẹ nhàng (gracefully). Chỉ hoạt động khi dùng đóng gói jar. Bị tắt theo mặc định. |
| `startup` | Hiển thị [dữ liệu các bước khởi động](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.spring-application.startup-tracking) được thu thập bởi `ApplicationStartup`. Yêu cầu `SpringApplication` được cấu hình với một `BufferingApplicationStartup`. |
| `threaddump` | Thực hiện một thread dump. |

Nếu ứng dụng của bạn là một ứng dụng web (Spring MVC, Spring WebFlux, hoặc Jersey), bạn có thể dùng các endpoint bổ sung sau:

| ID | Mô tả |
|---|---|
| `heapdump` | Trả về một file heap dump. Trên JVM HotSpot, một file định dạng `HPROF` được trả về. Trên JVM OpenJ9, một file định dạng `PHD` được trả về. |
| `logfile` | Trả về nội dung của file log (nếu property `logging.file.name` hoặc `logging.file.path` đã được đặt). Hỗ trợ dùng header HTTP `Range` để lấy một phần nội dung của file log. |
| `prometheus` | Expose metrics ở định dạng mà một Prometheus server có thể thu thập (scrape). Yêu cầu dependency tới `micrometer-registry-prometheus`. |

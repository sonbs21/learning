# 13.8.2. Bắt đầu (Getting Started)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#actuator.micrometer-tracing.getting-started>

Chúng ta cần một ứng dụng ví dụ để bắt đầu với tracing. Cho mục đích của chúng ta, ứng dụng web "Hello World!" đơn giản được đề cập trong phần "[Phát triển ứng dụng Spring Boot đầu tiên (Developing Your First Spring Boot Application)](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#getting-started.first-application)" là đủ. Chúng ta sẽ dùng tracer OpenTelemetry với Zipkin làm trace backend.

Tóm tắt lại, code ứng dụng chính của chúng ta trông như thế này:

```java
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@SpringBootApplication
public class MyApplication {

    private static final Log logger = LogFactory.getLog(MyApplication.class);

    @RequestMapping("/")
    String home() {
        logger.info("home() has been called");
        return "Hello World!";
    }

    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }

}
```

> **LƯU Ý (NOTE):** Có một câu lệnh logger được thêm vào phương thức `home()`, điều này sẽ quan trọng về sau.

Giờ chúng ta phải thêm các dependency sau:

- `org.springframework.boot:spring-boot-starter-actuator`
- `io.micrometer:micrometer-tracing-bridge-otel` - kết nối (bridge) Micrometer Observation API với OpenTelemetry.
- `io.opentelemetry:opentelemetry-exporter-zipkin` - báo cáo [trace](https://micrometer.io/docs/tracing#_glossary) tới Zipkin.

Thêm các application property sau:

**Properties**

```properties
management.tracing.sampling.probability=1.0
```

**Yaml**

```yaml
management:
  tracing:
    sampling:
      probability: 1.0
```

Theo mặc định, Spring Boot chỉ lấy mẫu (sample) 10% request để tránh làm quá tải trace backend. Property này chuyển nó thành 100% để mọi request đều được gửi tới trace backend.

Để thu thập và trực quan hóa các trace, chúng ta cần một trace backend đang chạy. Ở đây chúng ta dùng Zipkin làm trace backend. [Hướng dẫn Zipkin Quickstart](https://zipkin.io/pages/quickstart) cung cấp hướng dẫn cách khởi động Zipkin ở máy cục bộ.

Sau khi Zipkin đang chạy, bạn có thể khởi động ứng dụng.

Nếu bạn mở trình duyệt web tới [localhost:8080](http://localhost:8080), bạn sẽ thấy output sau:

```
Hello World!
```

Ở phía sau, một observation đã được tạo cho HTTP request, observation này lại được kết nối tới OpenTelemetry, và OpenTelemetry báo cáo một trace mới tới Zipkin.

Giờ hãy mở giao diện Zipkin tại [localhost:9411](http://localhost:9411) và nhấn nút "Run Query" để liệt kê tất cả các trace đã thu thập. Bạn sẽ thấy một trace. Nhấn nút "Show" để xem chi tiết của trace đó.

# 13.5. Khả năng quan sát (Observability)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#actuator.observability>

Khả năng quan sát (observability) là khả năng quan sát trạng thái nội bộ của một hệ thống đang chạy từ bên ngoài. Nó gồm ba trụ cột: logging, metrics và trace.

Với metrics và trace, Spring Boot dùng [Micrometer Observation](https://micrometer.io/docs/observation). Để tạo các observation của riêng bạn (sẽ dẫn đến metrics và trace), bạn có thể inject một `ObservationRegistry`.

```java
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

import org.springframework.stereotype.Component;

@Component
public class MyCustomObservation {

    private final ObservationRegistry observationRegistry;

    public MyCustomObservation(ObservationRegistry observationRegistry) {
        this.observationRegistry = observationRegistry;
    }

    public void doSomething() {
        Observation.createNotStarted("doSomething", this.observationRegistry)
            .lowCardinalityKeyValue("locale", "en-US")
            .highCardinalityKeyValue("userId", "42")
            .observe(() -> {
                // Execute business logic here
            });
    }

}
```

> **LƯU Ý (NOTE):** Các tag có cardinality thấp sẽ được thêm vào metrics và trace, trong khi các tag có cardinality cao sẽ chỉ được thêm vào trace.

Các bean kiểu `ObservationPredicate`, `GlobalObservationConvention`, `ObservationFilter` và `ObservationHandler` sẽ được tự động đăng ký trên `ObservationRegistry`. Bạn cũng có thể đăng ký thêm bất kỳ số lượng bean `ObservationRegistryCustomizer` nào để cấu hình registry thêm.

Hỗ trợ observability dựa vào [thư viện Context Propagation](https://github.com/micrometer-metrics/context-propagation) để chuyển tiếp observation hiện tại qua các thread và các pipeline reactive. Theo mặc định, các giá trị `ThreadLocal` không được tự động khôi phục trong các toán tử reactive. Hành vi này được kiểm soát bằng property `spring.reactor.context-propagation`, có thể được đặt thành `auto` để bật việc lan truyền tự động.

Để biết thêm chi tiết về observation, vui lòng xem [tài liệu Micrometer Observation](https://micrometer.io/docs/observation).

> **MẸO (TIP):** Observability cho JDBC có thể được cấu hình bằng một project riêng. [Project Datasource Micrometer](https://github.com/jdbc-observations/datasource-micrometer) cung cấp một starter Spring Boot tự động tạo observation khi các thao tác JDBC được gọi. Đọc thêm về nó [trong tài liệu tham khảo](https://jdbc-observations.github.io/datasource-micrometer/docs/current/docs/html/).

> **MẸO (TIP):** Observability cho R2DBC được tích hợp sẵn trong Spring Boot. Để bật nó, hãy thêm dependency `io.r2dbc:r2dbc-proxy` vào project.

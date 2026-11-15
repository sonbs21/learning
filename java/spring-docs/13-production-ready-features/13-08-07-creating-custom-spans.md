# 13.8.7. Tạo span tùy chỉnh (Creating Custom Spans)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#actuator.micrometer-tracing.creating-spans>

Bạn có thể tạo span của riêng mình bằng cách bắt đầu một observation. Để làm vậy, hãy inject `ObservationRegistry` vào component của bạn:

```java
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

import org.springframework.stereotype.Component;

@Component
class CustomObservation {

    private final ObservationRegistry observationRegistry;

    CustomObservation(ObservationRegistry observationRegistry) {
        this.observationRegistry = observationRegistry;
    }

    void someOperation() {
        Observation observation = Observation.createNotStarted("some-operation", this.observationRegistry);
        observation.lowCardinalityKeyValue("some-tag", "some-value");
        observation.observe(() -> {
            // Business logic ...
        });
    }

}
```

Việc này sẽ tạo một observation tên là "some-operation" với tag "some-tag=some-value".

> **MẸO (TIP):** Nếu bạn muốn tạo một span mà không tạo metric, bạn cần dùng [API `Tracer` cấp thấp hơn](https://micrometer.io/docs/tracing#_using_micrometer_tracing_directly) từ Micrometer.

# 13.8.8. Baggage

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#actuator.micrometer-tracing.baggage>

Bạn có thể tạo baggage bằng API `Tracer`:

```java
import io.micrometer.tracing.BaggageInScope;
import io.micrometer.tracing.Tracer;

import org.springframework.stereotype.Component;

@Component
class CreatingBaggage {

    private final Tracer tracer;

    CreatingBaggage(Tracer tracer) {
        this.tracer = tracer;
    }

    void doSomething() {
        try (BaggageInScope scope = this.tracer.createBaggageInScope("baggage1", "value1")) {
            // Business logic
        }
    }

}
```

Ví dụ này tạo baggage tên là `baggage1` với giá trị `value1`. Baggage được tự động lan truyền qua mạng nếu bạn đang dùng lan truyền W3C. Nếu bạn đang dùng lan truyền B3, baggage không được tự động lan truyền. Để lan truyền baggage qua mạng một cách thủ công, hãy dùng configuration property `management.tracing.baggage.remote-fields` (cách này cũng hoạt động với W3C). Với ví dụ trên, đặt property này thành `baggage1` sẽ tạo ra một HTTP header `baggage1: value1`.

Nếu bạn muốn lan truyền baggage tới MDC, hãy dùng configuration property `management.tracing.baggage.correlation.fields`. Với ví dụ trên, đặt property này thành `baggage1` sẽ tạo ra một entry MDC tên là `baggage1`.

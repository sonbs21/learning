# 18.13.4. Ánh xạ health indicator sang metric của Micrometer (Map Health Indicators to Micrometer Metrics)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.actuator.map-health-indicators-to-metrics>

Các health indicator của Spring Boot trả về một kiểu `Status` để cho biết tình trạng sức khỏe tổng thể của hệ thống. Nếu bạn muốn giám sát hoặc cảnh báo về mức độ sức khỏe của một ứng dụng cụ thể, bạn có thể xuất các status này dưới dạng metric với Micrometer. Theo mặc định, các mã status "UP", "DOWN", "OUT_OF_SERVICE" và "UNKNOWN" được Spring Boot sử dụng. Để xuất chúng, bạn sẽ cần chuyển đổi các trạng thái này thành một tập số nào đó để chúng có thể được dùng với một `Gauge` của Micrometer.

Ví dụ sau cho thấy một cách để viết một exporter như vậy:

**Java**

```java
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.Status;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class MyHealthMetricsExportConfiguration {

    public MyHealthMetricsExportConfiguration(MeterRegistry registry, HealthEndpoint healthEndpoint) {
        // This example presumes common tags (such as the app) are applied elsewhere
        Gauge.builder("health", healthEndpoint, this::getStatusCode).strongReference(true).register(registry);
    }

    private int getStatusCode(HealthEndpoint health) {
        Status status = health.health().getStatus();
        if (Status.UP.equals(status)) {
            return 3;
        }
        if (Status.OUT_OF_SERVICE.equals(status)) {
            return 2;
        }
        if (Status.DOWN.equals(status)) {
            return 1;
        }
        return 0;
    }

}
```

**Kotlin**

```kotlin
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.boot.actuate.health.HealthEndpoint
import org.springframework.boot.actuate.health.Status
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class MyHealthMetricsExportConfiguration(registry: MeterRegistry, healthEndpoint: HealthEndpoint) {

    init {
        // This example presumes common tags (such as the app) are applied elsewhere
        Gauge.builder("health", healthEndpoint) { health ->
            getStatusCode(health).toDouble()
        }.strongReference(true).register(registry)
    }

    private fun getStatusCode(health: HealthEndpoint) = when (health.health().status) {
        Status.UP -> 3
        Status.OUT_OF_SERVICE -> 2
        Status.DOWN -> 1
        else -> 0
    }

}
```

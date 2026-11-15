# 13.7.1. Bắt đầu (Getting started)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#actuator.metrics.getting-started>

Spring Boot tự động cấu hình một `MeterRegistry` tổng hợp (composite) và thêm một registry vào tổ hợp cho mỗi implementation được hỗ trợ mà nó tìm thấy trên classpath. Có dependency tới `micrometer-registry-{system}` trong runtime classpath là đủ để Spring Boot cấu hình registry.

Hầu hết các registry đều có chung các tính năng. Ví dụ, bạn có thể tắt một registry cụ thể ngay cả khi implementation registry Micrometer có trên classpath. Ví dụ sau tắt Datadog:

**Properties**

```properties
management.datadog.metrics.export.enabled=false
```

**Yaml**

```yaml
management:
  datadog:
    metrics:
      export:
        enabled: false
```

Bạn cũng có thể tắt tất cả các registry trừ khi được nêu khác bởi property đặc thù của registry, như ví dụ sau cho thấy:

**Properties**

```properties
management.defaults.metrics.export.enabled=false
```

**Yaml**

```yaml
management:
  defaults:
    metrics:
      export:
        enabled: false
```

Spring Boot cũng thêm mọi registry được tự động cấu hình vào composite registry tĩnh toàn cục trên class `Metrics`, trừ khi bạn nói tường minh là không:

**Properties**

```properties
management.metrics.use-global-registry=false
```

**Yaml**

```yaml
management:
  metrics:
    use-global-registry: false
```

Bạn có thể đăng ký bất kỳ số lượng bean `MeterRegistryCustomizer` nào để cấu hình registry thêm, chẳng hạn như áp dụng các tag chung, trước khi bất kỳ meter nào được đăng ký với registry:

**Java**

```java
import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class MyMeterRegistryConfiguration {

    @Bean
    public MeterRegistryCustomizer<MeterRegistry> metricsCommonTags() {
        return (registry) -> registry.config().commonTags("region", "us-east-1");
    }

}
```

**Kotlin**

```kotlin
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class MyMeterRegistryConfiguration {

    @Bean
    fun metricsCommonTags(): MeterRegistryCustomizer<MeterRegistry> {
        return MeterRegistryCustomizer { registry ->
            registry.config().commonTags("region", "us-east-1")
        }
    }

}
```

Bạn có thể áp dụng các tùy chỉnh cho các implementation registry cụ thể bằng cách chỉ định cụ thể hơn kiểu generic:

**Java**

```java
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.graphite.GraphiteMeterRegistry;

import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class MyMeterRegistryConfiguration {

    @Bean
    public MeterRegistryCustomizer<GraphiteMeterRegistry> graphiteMetricsNamingConvention() {
        return (registry) -> registry.config().namingConvention(this::name);
    }

    private String name(String name, Meter.Type type, String baseUnit) {
        return ...
    }

}
```

**Kotlin**

```kotlin
import io.micrometer.core.instrument.Meter
import io.micrometer.core.instrument.config.NamingConvention
import io.micrometer.graphite.GraphiteMeterRegistry
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class MyMeterRegistryConfiguration {

    @Bean
    fun graphiteMetricsNamingConvention(): MeterRegistryCustomizer<GraphiteMeterRegistry> {
        return MeterRegistryCustomizer { registry: GraphiteMeterRegistry ->
            registry.config().namingConvention(this::name)
        }
    }

    private fun name(name: String, type: Meter.Type, baseUnit: String?): String {
        return  ...
    }

}
```

Spring Boot cũng [cấu hình instrumentation có sẵn](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#actuator.metrics.supported) mà bạn có thể kiểm soát thông qua cấu hình hoặc các annotation đánh dấu chuyên biệt.

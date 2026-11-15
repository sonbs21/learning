# 13.7.4. Đăng ký metric tùy chỉnh (Registering Custom Metrics)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#actuator.metrics.registering-custom>

Để đăng ký các metric tùy chỉnh, hãy inject `MeterRegistry` vào component của bạn:

**Java**

```java
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;

import org.springframework.stereotype.Component;

@Component
public class MyBean {

    private final Dictionary dictionary;

    public MyBean(MeterRegistry registry) {
        this.dictionary = Dictionary.load();
        registry.gauge("dictionary.size", Tags.empty(), this.dictionary.getWords().size());
    }

}
```

**Kotlin**

```kotlin
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tags
import org.springframework.stereotype.Component

@Component
class MyBean(registry: MeterRegistry) {

    private val dictionary: Dictionary

    init {
        dictionary = Dictionary.load()
        registry.gauge("dictionary.size", Tags.empty(), dictionary.words.size)
    }

}
```

Nếu các metric của bạn phụ thuộc vào các bean khác, chúng tôi khuyên bạn nên dùng một `MeterBinder` để đăng ký chúng:

**Java**

```java
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.MeterBinder;

import org.springframework.context.annotation.Bean;

public class MyMeterBinderConfiguration {

    @Bean
    public MeterBinder queueSize(Queue queue) {
        return (registry) -> Gauge.builder("queueSize", queue::size).register(registry);
    }

}
```

**Kotlin**

```kotlin
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.binder.MeterBinder
import org.springframework.context.annotation.Bean

class MyMeterBinderConfiguration {

    @Bean
    fun queueSize(queue: Queue): MeterBinder {
        return MeterBinder { registry ->
            Gauge.builder("queueSize", queue::size).register(registry)
        }
    }

}
```

Dùng một `MeterBinder` đảm bảo các quan hệ dependency đúng được thiết lập và bean khả dụng khi giá trị của metric được lấy. Một implementation `MeterBinder` cũng có thể hữu ích nếu bạn thấy mình lặp đi lặp lại việc instrument một bộ metric trên nhiều component hoặc ứng dụng.

> **LƯU Ý (NOTE):** Theo mặc định, các metric từ tất cả các bean `MeterBinder` được tự động gắn với `MeterRegistry` do Spring quản lý.

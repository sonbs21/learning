# 13.7.5. Tùy chỉnh từng metric riêng lẻ (Customizing Individual Metrics)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#actuator.metrics.customizing>

Nếu bạn cần áp dụng tùy chỉnh cho các instance `Meter` cụ thể, bạn có thể dùng interface `io.micrometer.core.instrument.config.MeterFilter`.

Ví dụ, nếu bạn muốn đổi tên tag `mytag.region` thành `mytag.area` cho tất cả các meter ID bắt đầu bằng `com.example`, bạn có thể làm như sau:

**Java**

```java
import io.micrometer.core.instrument.config.MeterFilter;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class MyMetricsFilterConfiguration {

    @Bean
    public MeterFilter renameRegionTagMeterFilter() {
        return MeterFilter.renameTag("com.example", "mytag.region", "mytag.area");
    }

}
```

**Kotlin**

```kotlin
import io.micrometer.core.instrument.config.MeterFilter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class MyMetricsFilterConfiguration {

    @Bean
    fun renameRegionTagMeterFilter(): MeterFilter {
        return MeterFilter.renameTag("com.example", "mytag.region", "mytag.area")
    }

}
```

> **LƯU Ý (NOTE):** Theo mặc định, tất cả các bean `MeterFilter` được tự động gắn với `MeterRegistry` do Spring quản lý. Hãy đảm bảo đăng ký các metric bằng `MeterRegistry` do Spring quản lý chứ không phải bằng bất kỳ phương thức tĩnh nào trên `Metrics`. Những phương thức đó dùng registry toàn cục không do Spring quản lý.

## Tag chung (Common Tags)

Tag chung thường được dùng để đi sâu theo chiều vào môi trường vận hành, như host, instance, region, stack, và các thông tin khác. Tag chung được áp dụng cho tất cả các meter và có thể được cấu hình, như ví dụ sau cho thấy:

**Properties**

```properties
management.metrics.tags.region=us-east-1
management.metrics.tags.stack=prod
```

**Yaml**

```yaml
management:
  metrics:
    tags:
      region: "us-east-1"
      stack: "prod"
```

Ví dụ trên thêm các tag `region` và `stack` vào tất cả các meter với giá trị lần lượt là `us-east-1` và `prod`.

> **LƯU Ý (NOTE):** Thứ tự của các tag chung là quan trọng nếu bạn dùng Graphite. Vì thứ tự của các tag chung không thể được đảm bảo khi dùng cách tiếp cận này, người dùng Graphite được khuyên nên định nghĩa một `MeterFilter` tùy chỉnh thay thế.

## Property cho từng meter (Per-meter Properties)

Ngoài các bean `MeterFilter`, bạn có thể áp dụng một tập hợp tùy chỉnh hạn chế cho từng meter bằng property. Các tùy chỉnh cho từng meter được áp dụng, bằng `PropertiesMeterFilter` của Spring Boot, cho bất kỳ meter ID nào bắt đầu bằng tên đã cho. Ví dụ sau lọc bỏ mọi meter có ID bắt đầu bằng `example.remote`.

**Properties**

```properties
management.metrics.enable.example.remote=false
```

**Yaml**

```yaml
management:
  metrics:
    enable:
      example:
        remote: false
```

Các property sau cho phép tùy chỉnh cho từng meter:

| Property | Mô tả |
|---|---|
| `management.metrics.enable` | Có chấp nhận các meter với một số ID nhất định hay không. Các meter không được chấp nhận sẽ bị lọc khỏi `MeterRegistry`. |
| `management.metrics.distribution.percentiles-histogram` | Có publish một histogram phù hợp để tính các xấp xỉ percentile có thể tổng hợp (trên các dimension) hay không. |
| `management.metrics.distribution.minimum-expected-value`, `management.metrics.distribution.maximum-expected-value` | Publish ít bucket histogram hơn bằng cách giới hạn khoảng giá trị mong đợi. |
| `management.metrics.distribution.percentiles` | Publish các giá trị percentile được tính trong ứng dụng của bạn |
| `management.metrics.distribution.expiry`, `management.metrics.distribution.buffer-length` | Đặt trọng số lớn hơn cho các mẫu gần đây bằng cách tích lũy chúng trong các ring buffer xoay vòng sau một thời gian hết hạn có thể cấu hình, với độ dài buffer có thể cấu hình. |
| `management.metrics.distribution.slo` | Publish một histogram tích lũy với các bucket được định nghĩa bởi các mục tiêu mức dịch vụ (service-level objective) của bạn. |

Để biết thêm chi tiết về các khái niệm đằng sau `percentiles-histogram`, `percentiles`, và `slo`, xem [phần "Histograms and percentiles"](https://docs.micrometer.io/micrometer/reference/concepts#_histograms_and_percentiles) trong tài liệu Micrometer.

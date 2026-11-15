# 13.5.2. Ngăn observation (Preventing Observations)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#actuator.observability.preventing-observations>

Nếu bạn muốn ngăn một số observation được báo cáo, bạn có thể dùng các property `management.observations.enable`:

**Properties**

```properties
management.observations.enable.denied.prefix=false
management.observations.enable.another.denied.prefix=false
```

**Yaml**

```yaml
management:
  observations:
    enable:
      denied:
        prefix: false
      another:
        denied:
          prefix: false
```

Ví dụ trên sẽ ngăn tất cả các observation có tên bắt đầu bằng `denied.prefix` hoặc `another.denied.prefix`.

> **MẸO (TIP):** Nếu bạn muốn ngăn Spring Security báo cáo observation, hãy đặt property `management.observations.enable.spring.security` thành `false`.

Nếu bạn cần kiểm soát nhiều hơn đối với việc ngăn observation, bạn có thể đăng ký các bean kiểu `ObservationPredicate`. Observation chỉ được báo cáo nếu tất cả các bean `ObservationPredicate` trả về `true` cho observation đó.

```java
import io.micrometer.observation.Observation.Context;
import io.micrometer.observation.ObservationPredicate;

import org.springframework.stereotype.Component;

@Component
class MyObservationPredicate implements ObservationPredicate {

    @Override
    public boolean test(String name, Context context) {
        return !name.contains("denied");
    }

}
```

Ví dụ trên sẽ ngăn tất cả các observation có tên chứa "denied".

# 11.1. Caching

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#io.caching>

Spring Framework cung cấp hỗ trợ để thêm caching vào ứng dụng một cách trong suốt. Về cốt lõi, abstraction này áp dụng caching cho các phương thức, nhờ đó giảm số lần thực thi dựa trên thông tin có sẵn trong cache. Logic caching được áp dụng một cách trong suốt, không gây can thiệp gì tới bên gọi. Spring Boot tự động cấu hình hạ tầng cache miễn là hỗ trợ caching được bật bằng annotation `@EnableCaching`.

> **LƯU Ý (NOTE):** Xem [phần liên quan](https://docs.spring.io/spring-framework/reference/6.1/integration/cache.html) trong tài liệu tham khảo Spring Framework để biết thêm chi tiết.

Tóm lại, để thêm caching vào một thao tác của service, hãy thêm annotation liên quan vào phương thức của nó, như trong ví dụ sau:

**Java**

```java
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

@Component
public class MyMathService {

    @Cacheable("piDecimals")
    public int computePiDecimal(int precision) {
        ...
    }

}
```

**Kotlin**

```kotlin
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Component

@Component
class MyMathService {

    @Cacheable("piDecimals")
    fun computePiDecimal(precision: Int): Int {
        ...
    }

}
```

Ví dụ này minh họa việc dùng caching cho một thao tác có thể tốn kém. Trước khi gọi `computePiDecimal`, abstraction tìm một entry trong cache `piDecimals` khớp với tham số `i`. Nếu tìm thấy entry, nội dung trong cache được trả về ngay cho bên gọi, và phương thức không được gọi. Nếu không, phương thức được gọi, và cache được cập nhật trước khi trả về giá trị.

> **THẬN TRỌNG (CAUTION):** Bạn cũng có thể dùng các annotation JSR-107 (JCache) tiêu chuẩn (chẳng hạn như `@CacheResult`) một cách trong suốt. Tuy nhiên, chúng tôi đặc biệt khuyên bạn không nên trộn lẫn các annotation Spring Cache và JCache.

Nếu bạn không thêm thư viện cache cụ thể nào, Spring Boot tự động cấu hình một [provider đơn giản](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#io.caching.provider.simple) dùng các concurrent map trong bộ nhớ. Khi cần một cache (chẳng hạn như `piDecimals` trong ví dụ trên), provider này tạo nó cho bạn. Provider đơn giản không thực sự được khuyến nghị cho production, nhưng nó rất tốt để bắt đầu và đảm bảo bạn hiểu các tính năng. Khi bạn đã quyết định dùng cache provider nào, hãy đảm bảo đọc tài liệu của nó để tìm ra cách cấu hình các cache mà ứng dụng của bạn dùng. Gần như tất cả các provider đều yêu cầu bạn cấu hình tường minh mọi cache mà bạn dùng trong ứng dụng. Một số cung cấp cách để tùy chỉnh các cache mặc định được định nghĩa bởi property `spring.cache.cache-names`.

> **MẸO (TIP):** Cũng có thể [cập nhật](https://docs.spring.io/spring-framework/reference/6.1/integration/cache/annotations.html#cache-annotations-put) hoặc [loại bỏ (evict)](https://docs.spring.io/spring-framework/reference/6.1/integration/cache/annotations.html#cache-annotations-evict) dữ liệu khỏi cache một cách trong suốt.

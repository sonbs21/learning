# 11.5. Validation

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#io.validation>

Tính năng validation phương thức được Bean Validation 1.1 hỗ trợ sẽ được tự động bật miễn là có một implementation JSR-303 (chẳng hạn như Hibernate validator) trên classpath. Điều này cho phép các phương thức của bean được đánh các ràng buộc `jakarta.validation` lên tham số và/hoặc giá trị trả về của chúng. Các class đích có các phương thức được đánh annotation như vậy cần được đánh annotation `@Validated` ở cấp kiểu (type level) để các phương thức của chúng được tìm kiếm các annotation ràng buộc inline.

Ví dụ, service sau kích hoạt validation cho tham số đầu tiên, đảm bảo kích thước của nó nằm trong khoảng từ 8 đến 10:

**Java**

```java
import jakarta.validation.constraints.Size;

import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
public class MyBean {

    public Archive findByCodeAndAuthor(@Size(min = 8, max = 10) String code, Author author) {
        return ...
    }

}
```

**Kotlin**

```kotlin
import jakarta.validation.constraints.Size
import org.springframework.stereotype.Service
import org.springframework.validation.annotation.Validated

@Service
@Validated
class MyBean {

    fun findByCodeAndAuthor(code: @Size(min = 8, max = 10) String?, author: Author?): Archive? {
        return null
    }

}
```

`MessageSource` của ứng dụng được dùng khi phân giải các `{parameters}` trong thông điệp ràng buộc. Điều này cho phép bạn dùng [các file `messages.properties` của ứng dụng](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.internationalization) cho các thông điệp Bean Validation. Sau khi các tham số đã được phân giải, việc nội suy thông điệp (message interpolation) được hoàn tất bằng interpolator mặc định của Bean Validation.

Để tùy chỉnh `Configuration` được dùng để xây dựng `ValidatorFactory`, hãy định nghĩa một bean `ValidationConfigurationCustomizer`. Khi có nhiều bean customizer được định nghĩa, chúng được gọi theo thứ tự dựa trên annotation `@Order` hoặc implementation `Ordered` của chúng.

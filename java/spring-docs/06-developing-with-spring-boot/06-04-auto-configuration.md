# 6.4. Auto-configuration

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#using.auto-configuration>

Auto-configuration của Spring Boot cố gắng tự động cấu hình ứng dụng Spring của bạn dựa trên các jar dependency mà bạn đã thêm. Ví dụ, nếu `HSQLDB` nằm trên classpath và bạn chưa tự cấu hình bất kỳ bean kết nối database nào, Spring Boot sẽ tự động cấu hình một database in-memory.

Bạn cần chủ động bật (opt-in) auto-configuration bằng cách thêm annotation `@EnableAutoConfiguration` hoặc `@SpringBootApplication` vào một trong các class `@Configuration` của bạn.

> **MẸO (TIP):** Bạn chỉ nên thêm duy nhất một annotation `@SpringBootApplication` hoặc `@EnableAutoConfiguration`. Chúng tôi thường khuyên bạn chỉ thêm một trong hai vào class `@Configuration` chính.

## 6.4.1. Thay thế dần auto-configuration (Gradually Replacing Auto-configuration)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#using.auto-configuration.replacing>

Auto-configuration không xâm lấn (non-invasive). Bất cứ lúc nào, bạn cũng có thể bắt đầu định nghĩa cấu hình của riêng mình để thay thế các phần cụ thể của auto-configuration. Ví dụ, nếu bạn thêm bean `DataSource` của riêng mình, hỗ trợ database nhúng mặc định sẽ lùi lại.

Nếu bạn cần biết auto-configuration nào đang được áp dụng và tại sao, hãy khởi chạy ứng dụng với tham số `--debug`. Làm như vậy sẽ bật log debug cho một số logger cốt lõi và ghi một báo cáo điều kiện (conditions report) ra console.

## 6.4.2. Vô hiệu hóa các class auto-configuration cụ thể (Disabling Specific Auto-configuration Classes)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#using.auto-configuration.disabling-specific>

Nếu bạn thấy các class auto-configuration cụ thể mà bạn không muốn đang được áp dụng, bạn có thể dùng thuộc tính exclude của `@SpringBootApplication` để vô hiệu hóa chúng, như trong ví dụ sau:

**Java**

```java
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

@SpringBootApplication(exclude = { DataSourceAutoConfiguration.class })
public class MyApplication {

}
```

**Kotlin**

```kotlin
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration

@SpringBootApplication(exclude = [DataSourceAutoConfiguration::class])
class MyApplication
```

Nếu class không nằm trên classpath, bạn có thể dùng thuộc tính `excludeName` của annotation và chỉ định tên đầy đủ (fully qualified name) thay thế. Nếu bạn thích dùng `@EnableAutoConfiguration` hơn là `@SpringBootApplication`, `exclude` và `excludeName` cũng có sẵn. Cuối cùng, bạn cũng có thể kiểm soát danh sách các class auto-configuration cần loại trừ bằng property `spring.autoconfigure.exclude`.

> **MẸO (TIP):** Bạn có thể định nghĩa việc loại trừ ở cả cấp annotation và bằng property.

> **LƯU Ý (NOTE):** Mặc dù các class auto-configuration là `public`, khía cạnh duy nhất của class được coi là public API là tên của class, thứ có thể dùng để vô hiệu hóa auto-configuration. Nội dung thực tế của các class đó, chẳng hạn như các class cấu hình lồng nhau hay các phương thức bean, chỉ dành cho sử dụng nội bộ và chúng tôi không khuyến khích dùng chúng trực tiếp.

## 6.4.3. Các package auto-configuration (Auto-configuration Packages)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#using.auto-configuration.packages>

Package auto-configuration là các package mà nhiều tính năng được tự động cấu hình sẽ tìm kiếm theo mặc định khi quét các thứ như entity và Spring Data repository. Annotation `@EnableAutoConfiguration` (trực tiếp hoặc thông qua sự hiện diện của nó trên `@SpringBootApplication`) xác định package auto-configuration mặc định. Các package bổ sung có thể được cấu hình bằng annotation `@AutoConfigurationPackage`.

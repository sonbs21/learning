# 18.4.3. Tùy chỉnh Jackson ObjectMapper (Customize the Jackson ObjectMapper)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.spring-mvc.customize-jackson-objectmapper>

Spring MVC (phía client và server) dùng `HttpMessageConverters` để thương lượng việc chuyển đổi nội dung trong một trao đổi HTTP. Nếu Jackson có trên classpath, bạn đã có sẵn (các) converter mặc định được cung cấp bởi `Jackson2ObjectMapperBuilder`, một instance của nó được auto-configure cho bạn.

Instance `ObjectMapper` (hoặc `XmlMapper` cho Jackson XML converter) (được tạo theo mặc định) có các property được tùy chỉnh sau:

- `MapperFeature.DEFAULT_VIEW_INCLUSION` bị vô hiệu hóa
- `DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES` bị vô hiệu hóa
- `SerializationFeature.WRITE_DATES_AS_TIMESTAMPS` bị vô hiệu hóa
- `SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS` bị vô hiệu hóa

Spring Boot cũng có một số tính năng giúp tùy chỉnh hành vi này dễ dàng hơn.

Bạn có thể cấu hình các instance `ObjectMapper` và `XmlMapper` bằng cách dùng environment. Jackson cung cấp một bộ lớn các tính năng bật/tắt có thể được dùng để cấu hình nhiều khía cạnh khác nhau trong quá trình xử lý của nó. Các tính năng này được mô tả trong một số enum (trong Jackson) ánh xạ lên các property trong environment:

| Enum | Property | Giá trị |
|---|---|---|
| `com.fasterxml.jackson.databind.cfg.EnumFeature` | `spring.jackson.datatype.enum.<feature_name>` | `true`, `false` |
| `com.fasterxml.jackson.databind.cfg.JsonNodeFeature` | `spring.jackson.datatype.json-node.<feature_name>` | `true`, `false` |
| `com.fasterxml.jackson.databind.DeserializationFeature` | `spring.jackson.deserialization.<feature_name>` | `true`, `false` |
| `com.fasterxml.jackson.core.JsonGenerator.Feature` | `spring.jackson.generator.<feature_name>` | `true`, `false` |
| `com.fasterxml.jackson.databind.MapperFeature` | `spring.jackson.mapper.<feature_name>` | `true`, `false` |
| `com.fasterxml.jackson.core.JsonParser.Feature` | `spring.jackson.parser.<feature_name>` | `true`, `false` |
| `com.fasterxml.jackson.databind.SerializationFeature` | `spring.jackson.serialization.<feature_name>` | `true`, `false` |
| `com.fasterxml.jackson.annotation.JsonInclude.Include` | `spring.jackson.default-property-inclusion` | `always`, `non_null`, `non_absent`, `non_default`, `non_empty` |

Ví dụ, để bật pretty print, hãy đặt `spring.jackson.serialization.indent_output=true`. Lưu ý rằng, nhờ việc dùng [relaxed binding](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.external-config.typesafe-configuration-properties.relaxed-binding), chữ hoa/thường của `indent_output` không cần khớp với hằng số enum tương ứng, vốn là `INDENT_OUTPUT`.

Cấu hình dựa trên environment này được áp dụng cho bean `Jackson2ObjectMapperBuilder` được auto-configure và áp dụng cho mọi mapper được tạo bằng builder đó, bao gồm cả bean `ObjectMapper` được auto-configure.

`Jackson2ObjectMapperBuilder` của context có thể được tùy chỉnh bởi một hoặc nhiều bean `Jackson2ObjectMapperBuilderCustomizer`. Các customizer bean như vậy có thể được sắp thứ tự (customizer của chính Boot có order là 0), cho phép áp dụng thêm tùy chỉnh cả trước và sau tùy chỉnh của Boot.

Mọi bean kiểu `com.fasterxml.jackson.databind.Module` đều được tự động đăng ký với `Jackson2ObjectMapperBuilder` được auto-configure và được áp dụng cho mọi instance `ObjectMapper` mà nó tạo ra. Điều này cung cấp một cơ chế toàn cục để đóng góp các module tùy chỉnh khi bạn thêm tính năng mới vào ứng dụng.

Nếu bạn muốn thay thế hoàn toàn `ObjectMapper` mặc định, hãy định nghĩa một `@Bean` kiểu đó và đánh dấu nó là `@Primary` hoặc, nếu bạn thích cách tiếp cận dựa trên builder, hãy định nghĩa một `@Bean` `Jackson2ObjectMapperBuilder`. Lưu ý rằng, trong cả hai trường hợp, làm vậy sẽ vô hiệu hóa toàn bộ auto-configuration của `ObjectMapper`.

Nếu bạn cung cấp bất kỳ `@Beans` nào kiểu `MappingJackson2HttpMessageConverter`, chúng sẽ thay thế giá trị mặc định trong cấu hình MVC. Ngoài ra, một bean tiện ích kiểu `HttpMessageConverters` cũng được cung cấp (và luôn có sẵn nếu bạn dùng cấu hình MVC mặc định). Nó có một số phương thức hữu ích để truy cập các message converter mặc định và các message converter được người dùng bổ sung.

Xem phần "[Tùy chỉnh việc render @ResponseBody (Customize the @ResponseBody Rendering)](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.spring-mvc.customize-responsebody-rendering)" và mã nguồn [`WebMvcAutoConfiguration`](https://github.com/spring-projects/spring-boot/tree/v3.2.5/spring-boot-project/spring-boot-autoconfigure/src/main/java/org/springframework/boot/autoconfigure/web/servlet/WebMvcAutoConfiguration.java) để biết thêm chi tiết.

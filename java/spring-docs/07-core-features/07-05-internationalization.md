# 7.5. Quốc tế hóa (Internationalization)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.internationalization>

Spring Boot hỗ trợ các thông điệp được bản địa hóa (localized message) để ứng dụng của bạn có thể phục vụ người dùng với các ngôn ngữ ưa thích khác nhau. Theo mặc định, Spring Boot tìm kiếm sự hiện diện của resource bundle `messages` ở thư mục gốc của classpath.

> **LƯU Ý (NOTE):** Auto-configuration được áp dụng khi file properties mặc định cho resource bundle đã cấu hình có sẵn (mặc định là `messages.properties`). Nếu resource bundle của bạn chỉ chứa các file properties theo ngôn ngữ cụ thể, bạn bắt buộc phải thêm file mặc định. Nếu không tìm thấy file properties nào khớp với bất kỳ tên cơ sở (base name) nào đã cấu hình, sẽ không có `MessageSource` nào được tự động cấu hình.

Tên cơ sở của resource bundle cũng như một số thuộc tính khác có thể được cấu hình bằng namespace `spring.messages`, như trong ví dụ sau:

**Properties**

```properties
spring.messages.basename=messages,config.i18n.messages
spring.messages.fallback-to-system-locale=false
```

**Yaml**

```yaml
spring:
  messages:
    basename: "messages,config.i18n.messages"
    fallback-to-system-locale: false
```

> **MẸO (TIP):** `spring.messages.basename` hỗ trợ danh sách các vị trí phân tách bằng dấu phẩy, có thể là một package qualifier hoặc một resource được phân giải từ thư mục gốc của classpath.

Xem [`MessageSourceProperties`](https://github.com/spring-projects/spring-boot/tree/v3.2.5/spring-boot-project/spring-boot-autoconfigure/src/main/java/org/springframework/boot/autoconfigure/context/MessageSourceProperties.java) để biết thêm các tùy chọn được hỗ trợ.

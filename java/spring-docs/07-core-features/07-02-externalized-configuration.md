# 7.2. Cấu hình bên ngoài (Externalized Configuration)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.external-config>

Spring Boot cho phép bạn đưa cấu hình ra bên ngoài (externalize) để có thể làm việc với cùng một code ứng dụng trong các môi trường khác nhau. Bạn có thể dùng nhiều nguồn cấu hình bên ngoài khác nhau, bao gồm file Java properties, file YAML, biến môi trường và tham số dòng lệnh.

Giá trị của property có thể được inject trực tiếp vào bean bằng annotation `@Value`, truy cập thông qua abstraction `Environment` của Spring, hoặc được [bind vào các object có cấu trúc](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.external-config.typesafe-configuration-properties) thông qua `@ConfigurationProperties`.

Spring Boot sử dụng một thứ tự `PropertySource` rất đặc thù, được thiết kế để cho phép ghi đè giá trị một cách hợp lý. Các property source sau có thể ghi đè giá trị được định nghĩa ở các property source trước. Các nguồn được xem xét theo thứ tự sau:

1. Các property mặc định (được chỉ định bằng cách đặt `SpringApplication.setDefaultProperties`).
2. Các annotation [`@PropertySource`](https://docs.spring.io/spring-framework/docs/6.1.6/javadoc-api/org/springframework/context/annotation/PropertySource.html) trên các class `@Configuration` của bạn. Lưu ý rằng các property source như vậy không được thêm vào `Environment` cho đến khi application context đang được refresh. Điều này là quá muộn để cấu hình một số property như `logging.*` và `spring.main.*`, vốn được đọc trước khi quá trình refresh bắt đầu.
3. Config data (chẳng hạn như các file `application.properties`).
4. Một `RandomValuePropertySource` chỉ có các property trong `random.*`.
5. Biến môi trường của hệ điều hành.
6. Java System properties (`System.getProperties()`).
7. Các thuộc tính JNDI từ `java:comp/env`.
8. Các init parameter của `ServletContext`.
9. Các init parameter của `ServletConfig`.
10. Các property từ `SPRING_APPLICATION_JSON` (JSON inline được nhúng trong một biến môi trường hoặc system property).
11. Tham số dòng lệnh.
12. Thuộc tính `properties` trên các test của bạn. Có sẵn trên [`@SpringBootTest`](https://docs.spring.io/spring-boot/docs/3.2.5/api/org/springframework/boot/test/context/SpringBootTest.html) và [các test annotation dùng để kiểm thử một lát cắt (slice) cụ thể của ứng dụng](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.testing.spring-boot-applications.autoconfigured-tests).
13. Các annotation [`@DynamicPropertySource`](https://docs.spring.io/spring-framework/docs/6.1.6/javadoc-api/org/springframework/test/context/DynamicPropertySource.html) trong các test của bạn.
14. Các annotation [`@TestPropertySource`](https://docs.spring.io/spring-framework/docs/6.1.6/javadoc-api/org/springframework/test/context/TestPropertySource.html) trên các test của bạn.
15. [Các property thiết lập toàn cục của Devtools](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#using.devtools.globalsettings) trong thư mục `$HOME/.config/spring-boot` khi devtools đang hoạt động.

Các file config data được xem xét theo thứ tự sau:

1. [Application properties](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.external-config.files) được đóng gói bên trong jar (`application.properties` và các biến thể YAML).
2. [Application properties theo profile](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.external-config.files.profile-specific) được đóng gói bên trong jar (`application-{profile}.properties` và các biến thể YAML).
3. [Application properties](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.external-config.files) bên ngoài jar đã đóng gói (`application.properties` và các biến thể YAML).
4. [Application properties theo profile](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.external-config.files.profile-specific) bên ngoài jar đã đóng gói (`application-{profile}.properties` và các biến thể YAML).

> **LƯU Ý (NOTE):** Khuyến nghị nên dùng thống nhất một định dạng cho toàn bộ ứng dụng. Nếu bạn có các file cấu hình ở cả định dạng `.properties` và YAML trong cùng một vị trí, `.properties` sẽ được ưu tiên.

> **LƯU Ý (NOTE):** Nếu bạn dùng biến môi trường thay vì system property, hầu hết các hệ điều hành không cho phép tên key phân tách bằng dấu chấm, nhưng bạn có thể dùng dấu gạch dưới thay thế (ví dụ, `SPRING_CONFIG_NAME` thay vì `spring.config.name`). Xem [Bind từ biến môi trường (Binding From Environment Variables)](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.external-config.typesafe-configuration-properties.relaxed-binding.environment-variables) để biết chi tiết.

> **LƯU Ý (NOTE):** Nếu ứng dụng của bạn chạy trong một servlet container hoặc application server, thì các property JNDI (trong `java:comp/env`) hoặc các tham số khởi tạo servlet context có thể được dùng thay cho, hoặc cùng với, biến môi trường hay system property.

Để đưa ra một ví dụ cụ thể, giả sử bạn phát triển một `@Component` dùng property `name`, như trong ví dụ sau:

**Java**

```java
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class MyBean {

    @Value("${name}")
    private String name;

    // ...

}
```

**Kotlin**

```kotlin
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class MyBean {

    @Value("\${name}")
    private val name: String? = null

    // ...

}
```

Trên classpath của ứng dụng (ví dụ, bên trong jar), bạn có thể có một file `application.properties` cung cấp giá trị mặc định hợp lý cho property `name`. Khi chạy trong một môi trường mới, một file `application.properties` có thể được cung cấp bên ngoài jar để ghi đè `name`. Để thử nghiệm một lần, bạn có thể khởi chạy với một tham số dòng lệnh cụ thể (ví dụ, `java -jar app.jar --name="Spring"`).

> **MẸO (TIP):** Các endpoint `env` và `configprops` có thể hữu ích trong việc xác định tại sao một property có một giá trị cụ thể. Bạn có thể dùng hai endpoint này để chẩn đoán các giá trị property không mong muốn. Xem phần "[Các tính năng sẵn sàng cho production (Production ready features)](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#actuator.endpoints)" để biết chi tiết.

## 7.2.1. Truy cập property từ dòng lệnh (Accessing Command Line Properties)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.external-config.command-line-args>

Theo mặc định, `SpringApplication` chuyển mọi tham số tùy chọn dòng lệnh (tức là các tham số bắt đầu bằng `--`, chẳng hạn như `--server.port=9000`) thành một `property` và thêm chúng vào `Environment` của Spring. Như đã đề cập trước đó, property từ dòng lệnh luôn được ưu tiên hơn các property source dựa trên file.

Nếu bạn không muốn các property từ dòng lệnh được thêm vào `Environment`, bạn có thể vô hiệu hóa chúng bằng `SpringApplication.setAddCommandLineProperties(false)`.

## 7.2.2. Application Properties dạng JSON (JSON Application Properties)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.external-config.application-json>

Biến môi trường và system property thường có những hạn chế khiến một số tên property không thể sử dụng được. Để giúp giải quyết điều này, Spring Boot cho phép bạn mã hóa một khối property thành một cấu trúc JSON duy nhất.

Khi ứng dụng khởi động, mọi property `spring.application.json` hoặc `SPRING_APPLICATION_JSON` sẽ được phân tích và thêm vào `Environment`.

Ví dụ, property `SPRING_APPLICATION_JSON` có thể được cung cấp trên dòng lệnh trong một UN*X shell dưới dạng biến môi trường:

```shell
$ SPRING_APPLICATION_JSON='{"my":{"name":"test"}}' java -jar myapp.jar
```

Trong ví dụ trên, bạn sẽ có `my.name=test` trong `Environment` của Spring.

JSON tương tự cũng có thể được cung cấp dưới dạng system property:

```shell
$ java -Dspring.application.json='{"my":{"name":"test"}}' -jar myapp.jar
```

Hoặc bạn có thể cung cấp JSON bằng tham số dòng lệnh:

```shell
$ java -jar myapp.jar --spring.application.json='{"my":{"name":"test"}}'
```

Nếu bạn triển khai lên một Application Server cổ điển, bạn cũng có thể dùng một biến JNDI tên là `java:comp/env/spring.application.json`.

> **LƯU Ý (NOTE):** Mặc dù các giá trị `null` từ JSON sẽ được thêm vào property source kết quả, `PropertySourcesPropertyResolver` coi các property `null` là giá trị bị thiếu. Điều này có nghĩa là JSON không thể ghi đè các property từ các property source có thứ tự thấp hơn bằng giá trị `null`.

## 7.2.3. Application Properties bên ngoài (External Application Properties)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.external-config.files>

Spring Boot sẽ tự động tìm và nạp các file `application.properties` và `application.yaml` từ các vị trí sau khi ứng dụng khởi động:

1. Từ classpath
   1. Thư mục gốc của classpath
   2. Package `/config` của classpath
2. Từ thư mục hiện tại
   1. Thư mục hiện tại
   2. Thư mục con `config/` trong thư mục hiện tại
   3. Các thư mục con trực tiếp của thư mục con `config/`

Danh sách được sắp xếp theo thứ tự ưu tiên (giá trị từ các mục bên dưới ghi đè các mục trước đó). Các document từ những file đã nạp được thêm dưới dạng `PropertySources` vào `Environment` của Spring.

Nếu bạn không thích `application` làm tên file cấu hình, bạn có thể chuyển sang tên file khác bằng cách chỉ định environment property `spring.config.name`. Ví dụ, để tìm các file `myproject.properties` và `myproject.yaml`, bạn có thể chạy ứng dụng như sau:

```shell
$ java -jar myproject.jar --spring.config.name=myproject
```

Bạn cũng có thể tham chiếu tới một vị trí tường minh bằng environment property `spring.config.location`. Property này chấp nhận một danh sách một hoặc nhiều vị trí cần kiểm tra, phân tách bằng dấu phẩy.

Ví dụ sau cho thấy cách chỉ định hai file riêng biệt:

```shell
$ java -jar myproject.jar --spring.config.location=\
    optional:classpath:/default.properties,\
    optional:classpath:/override.properties
```

> **MẸO (TIP):** Dùng tiền tố `optional:` nếu [các vị trí là tùy chọn](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.external-config.files.optional-prefix) và bạn không bận tâm nếu chúng không tồn tại.

> **CẢNH BÁO (WARNING):** `spring.config.name`, `spring.config.location`, và `spring.config.additional-location` được dùng từ rất sớm để xác định những file nào phải được nạp. Chúng phải được định nghĩa dưới dạng environment property (thường là biến môi trường của hệ điều hành, system property, hoặc tham số dòng lệnh).

Nếu `spring.config.location` chứa thư mục (thay vì file), chúng nên kết thúc bằng `/`. Tại runtime, chúng sẽ được nối thêm các tên được sinh ra từ `spring.config.name` trước khi được nạp. Các file được chỉ định trong `spring.config.location` được import trực tiếp.

> **LƯU Ý (NOTE):** Cả giá trị vị trí dạng thư mục và dạng file cũng được mở rộng để kiểm tra [các file theo profile](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.external-config.files.profile-specific). Ví dụ, nếu bạn có `spring.config.location` là `classpath:myconfig.properties`, bạn cũng sẽ thấy các file `classpath:myconfig-<profile>.properties` phù hợp được nạp.

Trong hầu hết các trường hợp, mỗi mục `spring.config.location` bạn thêm sẽ tham chiếu tới một file hoặc thư mục duy nhất. Các vị trí được xử lý theo thứ tự chúng được định nghĩa và các vị trí sau có thể ghi đè giá trị của các vị trí trước.

Nếu bạn có một thiết lập vị trí phức tạp, và bạn dùng các file cấu hình theo profile, bạn có thể cần cung cấp thêm gợi ý để Spring Boot biết chúng nên được nhóm như thế nào. Một nhóm vị trí (location group) là một tập hợp các vị trí được xem xét ở cùng một cấp. Ví dụ, bạn có thể muốn nhóm tất cả các vị trí classpath, rồi đến tất cả các vị trí bên ngoài. Các mục trong một nhóm vị trí nên được phân tách bằng `;`. Xem ví dụ trong phần "[File theo profile (Profile Specific Files)](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.external-config.files.profile-specific)" để biết thêm chi tiết.

Các vị trí được cấu hình bằng `spring.config.location` sẽ thay thế các vị trí mặc định. Ví dụ, nếu `spring.config.location` được cấu hình với giá trị `optional:classpath:/custom-config/,optional:file:./custom-config/`, tập hợp đầy đủ các vị trí được xem xét là:

1. `optional:classpath:custom-config/`
2. `optional:file:./custom-config/`

Nếu bạn muốn thêm các vị trí bổ sung thay vì thay thế chúng, bạn có thể dùng `spring.config.additional-location`. Các property được nạp từ các vị trí bổ sung có thể ghi đè những property ở các vị trí mặc định. Ví dụ, nếu `spring.config.additional-location` được cấu hình với giá trị `optional:classpath:/custom-config/,optional:file:./custom-config/`, tập hợp đầy đủ các vị trí được xem xét là:

1. `optional:classpath:/;optional:classpath:/config/`
2. `optional:file:./;optional:file:./config/;optional:file:./config/*/`
3. `optional:classpath:custom-config/`
4. `optional:file:./custom-config/`

Thứ tự tìm kiếm này cho phép bạn chỉ định các giá trị mặc định trong một file cấu hình rồi ghi đè có chọn lọc các giá trị đó trong một file khác. Bạn có thể cung cấp các giá trị mặc định cho ứng dụng trong `application.properties` (hoặc bất kỳ tên cơ sở nào khác bạn chọn bằng `spring.config.name`) tại một trong các vị trí mặc định. Các giá trị mặc định này sau đó có thể được ghi đè tại runtime bằng một file khác nằm ở một trong các vị trí tùy chỉnh.

### Vị trí tùy chọn (Optional Locations)

Theo mặc định, khi một vị trí config data được chỉ định không tồn tại, Spring Boot sẽ ném ra `ConfigDataLocationNotFoundException` và ứng dụng của bạn sẽ không khởi động.

Nếu bạn muốn chỉ định một vị trí nhưng không bận tâm nếu nó không phải lúc nào cũng tồn tại, bạn có thể dùng tiền tố `optional:`. Bạn có thể dùng tiền tố này với các property `spring.config.location` và `spring.config.additional-location`, cũng như với các khai báo [`spring.config.import`](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.external-config.files.importing).

Ví dụ, giá trị `spring.config.import` là `optional:file:./myconfig.properties` cho phép ứng dụng khởi động, ngay cả khi file `myconfig.properties` bị thiếu.

Nếu bạn muốn bỏ qua tất cả các `ConfigDataLocationNotFoundExceptions` và luôn tiếp tục khởi động ứng dụng, bạn có thể dùng property `spring.config.on-not-found`. Hãy đặt giá trị thành `ignore` bằng `SpringApplication.setDefaultProperties(…)` hoặc bằng biến system/environment.

### Vị trí dùng ký tự đại diện (Wildcard Locations)

Nếu một vị trí file cấu hình chứa ký tự `*` ở đoạn đường dẫn cuối cùng, nó được coi là một vị trí wildcard. Wildcard được mở rộng khi cấu hình được nạp để các thư mục con trực tiếp cũng được kiểm tra. Vị trí wildcard đặc biệt hữu ích trong môi trường như Kubernetes khi có nhiều nguồn config property.

Ví dụ, nếu bạn có một số cấu hình Redis và một số cấu hình MySQL, bạn có thể muốn giữ hai phần cấu hình đó tách biệt, đồng thời yêu cầu cả hai đều có mặt trong một file `application.properties`. Điều này có thể dẫn đến hai file `application.properties` riêng biệt được mount tại các vị trí khác nhau như `/config/redis/application.properties` và `/config/mysql/application.properties`. Trong trường hợp như vậy, có một vị trí wildcard `config/*/` sẽ khiến cả hai file được xử lý.

Theo mặc định, Spring Boot bao gồm `config/*/` trong các vị trí tìm kiếm mặc định. Điều đó có nghĩa là tất cả các thư mục con của thư mục `/config` bên ngoài jar sẽ được tìm kiếm.

Bạn có thể tự dùng vị trí wildcard với các property `spring.config.location` và `spring.config.additional-location`.

> **LƯU Ý (NOTE):** Một vị trí wildcard chỉ được chứa duy nhất một `*` và kết thúc bằng `*/` đối với các vị trí tìm kiếm là thư mục, hoặc `*/<filename>` đối với các vị trí tìm kiếm là file. Các vị trí có wildcard được sắp xếp theo thứ tự bảng chữ cái dựa trên đường dẫn tuyệt đối của tên file.

> **MẸO (TIP):** Vị trí wildcard chỉ hoạt động với thư mục bên ngoài. Bạn không thể dùng wildcard trong vị trí `classpath:`.

### File theo profile (Profile Specific Files)

Ngoài các file property `application`, Spring Boot cũng sẽ cố gắng nạp các file theo profile bằng quy ước đặt tên `application-{profile}`. Ví dụ, nếu ứng dụng của bạn kích hoạt một profile tên là `prod` và dùng file YAML, thì cả `application.yaml` và `application-prod.yaml` đều sẽ được xem xét.

Các property theo profile được nạp từ cùng các vị trí như `application.properties` tiêu chuẩn, với các file theo profile luôn ghi đè các file không theo profile. Nếu có nhiều profile được chỉ định, chiến lược "cái cuối cùng thắng" (last-wins) sẽ được áp dụng. Ví dụ, nếu các profile `prod,live` được chỉ định bằng property `spring.profiles.active`, các giá trị trong `application-prod.properties` có thể bị ghi đè bởi các giá trị trong `application-live.properties`.

> **LƯU Ý (NOTE):** Chiến lược last-wins áp dụng ở cấp [nhóm vị trí (location group)](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.external-config.files.location-groups). Một `spring.config.location` là `classpath:/cfg/,classpath:/ext/` sẽ không có cùng quy tắc ghi đè như `classpath:/cfg/;classpath:/ext/`.
>
> Ví dụ, tiếp tục ví dụ `prod,live` ở trên, chúng ta có thể có các file sau:

```
/cfg
  application-live.properties
/ext
  application-live.properties
  application-prod.properties
```

> Khi chúng ta có `spring.config.location` là `classpath:/cfg/,classpath:/ext/`, chúng ta xử lý tất cả các file `/cfg` trước tất cả các file `/ext`:
>
> 1. `/cfg/application-live.properties`
> 2. `/ext/application-prod.properties`
> 3. `/ext/application-live.properties`
>
> Khi thay vào đó chúng ta có `classpath:/cfg/;classpath:/ext/` (với dấu phân cách `;`), chúng ta xử lý `/cfg` và `/ext` ở cùng một cấp:
>
> 1. `/ext/application-prod.properties`
> 2. `/cfg/application-live.properties`
> 3. `/ext/application-live.properties`

`Environment` có một tập hợp các profile mặc định (theo mặc định là `[default]`) được dùng nếu không có profile nào đang hoạt động được đặt. Nói cách khác, nếu không có profile nào được kích hoạt tường minh, thì các property từ `application-default` sẽ được xem xét.

> **LƯU Ý (NOTE):** Các file properties chỉ được nạp một lần. Nếu bạn đã [import](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.external-config.files.importing) trực tiếp một file property theo profile thì nó sẽ không được import lần thứ hai.

### Import thêm dữ liệu (Importing Additional Data)

Application properties có thể import thêm config data từ các vị trí khác bằng property `spring.config.import`. Các import được xử lý khi chúng được phát hiện, và được coi là các document bổ sung được chèn ngay bên dưới document khai báo import.

Ví dụ, bạn có thể có nội dung sau trong file `application.properties` trên classpath:

**Properties**

```properties
spring.application.name=myapp
spring.config.import=optional:file:./dev.properties
```

**Yaml**

```yaml
spring:
  application:
    name: "myapp"
  config:
    import: "optional:file:./dev.properties"
```

Điều này sẽ kích hoạt việc import file `dev.properties` trong thư mục hiện tại (nếu file đó tồn tại). Các giá trị từ `dev.properties` được import sẽ được ưu tiên hơn file đã kích hoạt việc import. Trong ví dụ trên, `dev.properties` có thể định nghĩa lại `spring.application.name` thành một giá trị khác.

Một import sẽ chỉ được import một lần bất kể nó được khai báo bao nhiêu lần. Thứ tự định nghĩa một import bên trong một document duy nhất trong file properties/yaml không quan trọng. Ví dụ, hai ví dụ dưới đây cho ra cùng một kết quả:

**Properties**

```properties
spring.config.import=my.properties
my.property=value
```

**Yaml**

```yaml
spring:
  config:
    import: "my.properties"
my:
  property: "value"
```

**Properties**

```properties
my.property=value
spring.config.import=my.properties
```

**Yaml**

```yaml
my:
  property: "value"
spring:
  config:
    import: "my.properties"
```

Trong cả hai ví dụ trên, các giá trị từ file `my.properties` sẽ được ưu tiên hơn file đã kích hoạt việc import của nó.

Nhiều vị trí có thể được chỉ định dưới một key `spring.config.import` duy nhất. Các vị trí sẽ được xử lý theo thứ tự chúng được định nghĩa, với các import sau được ưu tiên hơn.

> **LƯU Ý (NOTE):** Khi phù hợp, [các biến thể theo profile](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.external-config.files.profile-specific) cũng được xem xét để import. Ví dụ trên sẽ import cả `my.properties` lẫn mọi biến thể `my-<profile>.properties`.

> **MẸO (TIP):** Spring Boot bao gồm một API có thể cắm thêm (pluggable) cho phép hỗ trợ nhiều loại địa chỉ vị trí khác nhau. Theo mặc định, bạn có thể import Java Properties, YAML và "[cây cấu hình (configuration trees)](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.external-config.files.configtree)".
>
> Các jar bên thứ ba có thể cung cấp hỗ trợ cho các công nghệ bổ sung (không có yêu cầu file phải nằm ở máy cục bộ). Ví dụ, bạn có thể hình dung config data đến từ các kho bên ngoài như Consul, Apache ZooKeeper hoặc Netflix Archaius.
>
> Nếu bạn muốn hỗ trợ các vị trí của riêng mình, hãy xem các class `ConfigDataLocationResolver` và `ConfigDataLoader` trong package `org.springframework.boot.context.config`.

### Import file không có phần mở rộng (Importing Extensionless Files)

Một số nền tảng cloud không thể thêm phần mở rộng file cho các file được mount qua volume. Để import các file không có phần mở rộng này, bạn cần cho Spring Boot một gợi ý để nó biết cách nạp chúng. Bạn có thể làm điều này bằng cách đặt gợi ý phần mở rộng trong dấu ngoặc vuông.

Ví dụ, giả sử bạn có file `/etc/config/myconfig` mà bạn muốn import dưới dạng yaml. Bạn có thể import nó từ `application.properties` bằng cách sau:

**Properties**

```properties
spring.config.import=file:/etc/config/myconfig[.yaml]
```

**Yaml**

```yaml
spring:
  config:
    import: "file:/etc/config/myconfig[.yaml]"
```

### Sử dụng cây cấu hình (Using Configuration Trees)

Khi chạy ứng dụng trên một nền tảng cloud (chẳng hạn như Kubernetes), bạn thường cần đọc các giá trị cấu hình do nền tảng cung cấp. Việc dùng biến môi trường cho mục đích như vậy không phải là hiếm, nhưng điều này có thể có nhược điểm, đặc biệt nếu giá trị cần được giữ bí mật.

Như một lựa chọn thay thế cho biến môi trường, nhiều nền tảng cloud hiện nay cho phép bạn ánh xạ cấu hình vào các data volume được mount. Ví dụ, Kubernetes có thể mount qua volume cả [`ConfigMaps`](https://kubernetes.io/docs/tasks/configure-pod-container/configure-pod-configmap/#populate-a-volume-with-data-stored-in-a-configmap) và [`Secrets`](https://kubernetes.io/docs/concepts/configuration/secret/#using-secrets-as-files-from-a-pod).

Có hai mẫu mount volume phổ biến có thể được sử dụng:

1. Một file duy nhất chứa một tập hợp property hoàn chỉnh (thường được viết dưới dạng YAML).
2. Nhiều file được ghi vào một cây thư mục, với tên file trở thành 'key' và nội dung trở thành 'value'.

Với trường hợp thứ nhất, bạn có thể import trực tiếp file YAML hoặc Properties bằng `spring.config.import` như đã mô tả [ở trên](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.external-config.files.importing). Với trường hợp thứ hai, bạn cần dùng tiền tố `configtree:` để Spring Boot biết rằng nó cần expose tất cả các file dưới dạng property.

Ví dụ, hãy tưởng tượng Kubernetes đã mount volume sau:

```
etc/
  config/
    myapp/
      username
      password
```

Nội dung của file `username` sẽ là một giá trị cấu hình, và nội dung của `password` sẽ là một secret.

Để import các property này, bạn có thể thêm nội dung sau vào file `application.properties` hoặc `application.yaml`:

**Properties**

```properties
spring.config.import=optional:configtree:/etc/config/
```

**Yaml**

```yaml
spring:
  config:
    import: "optional:configtree:/etc/config/"
```

Sau đó, bạn có thể truy cập hoặc inject các property `myapp.username` và `myapp.password` từ `Environment` theo cách thông thường.

> **MẸO (TIP):** Tên của các thư mục và file bên dưới cây cấu hình tạo thành tên property. Trong ví dụ trên, để truy cập các property dưới dạng `username` và `password`, bạn có thể đặt `spring.config.import` thành `optional:configtree:/etc/config/myapp`.

> **LƯU Ý (NOTE):** Tên file có ký hiệu dấu chấm cũng được ánh xạ chính xác. Ví dụ, trong ví dụ trên, một file tên là `myapp.username` trong `/etc/config` sẽ tạo ra property `myapp.username` trong `Environment`.

> **MẸO (TIP):** Các giá trị của cây cấu hình có thể được bind vào cả kiểu chuỗi `String` và kiểu `byte[]` tùy thuộc vào nội dung mong đợi.

Nếu bạn có nhiều cây cấu hình cần import từ cùng một thư mục cha, bạn có thể dùng cách viết tắt wildcard. Bất kỳ vị trí `configtree:` nào kết thúc bằng `/*/` sẽ import tất cả các thư mục con trực tiếp dưới dạng cây cấu hình. Cũng như với import không dùng wildcard, tên của các thư mục và file bên dưới mỗi cây cấu hình tạo thành tên property.

Ví dụ, với volume sau:

```
etc/
  config/
    dbconfig/
      db/
        username
        password
    mqconfig/
      mq/
        username
        password
```

Bạn có thể dùng `configtree:/etc/config/*/` làm vị trí import:

**Properties**

```properties
spring.config.import=optional:configtree:/etc/config/*/
```

**Yaml**

```yaml
spring:
  config:
    import: "optional:configtree:/etc/config/*/"
```

Việc này sẽ thêm các property `db.username`, `db.password`, `mq.username` và `mq.password`.

> **LƯU Ý (NOTE):** Các thư mục được nạp bằng wildcard được sắp xếp theo thứ tự bảng chữ cái. Nếu bạn cần một thứ tự khác, bạn nên liệt kê từng vị trí như một import riêng biệt.

Cây cấu hình cũng có thể được dùng cho Docker secret. Khi một service Docker swarm được cấp quyền truy cập một secret, secret đó sẽ được mount vào container. Ví dụ, nếu một secret tên là `db.password` được mount tại vị trí `/run/secrets/`, bạn có thể làm cho `db.password` khả dụng với Spring environment bằng cách sau:

**Properties**

```properties
spring.config.import=optional:configtree:/run/secrets/
```

**Yaml**

```yaml
spring:
  config:
    import: "optional:configtree:/run/secrets/"
```

### Placeholder trong property (Property Placeholders)

Các giá trị trong `application.properties` và `application.yaml` được lọc qua `Environment` hiện có khi chúng được sử dụng, nên bạn có thể tham chiếu ngược lại các giá trị đã được định nghĩa trước đó (ví dụ, từ System properties hoặc biến môi trường). Cú pháp property-placeholder tiêu chuẩn `${name}` có thể được dùng ở bất kỳ đâu trong một giá trị. Property placeholder cũng có thể chỉ định giá trị mặc định bằng cách dùng `:` để phân tách giá trị mặc định với tên property, ví dụ `${name:default}`.

Việc dùng placeholder có và không có giá trị mặc định được thể hiện trong ví dụ sau:

**Properties**

```properties
app.name=MyApp
app.description=${app.name} is a Spring Boot application written by ${username:Unknown}
```

**Yaml**

```yaml
app:
  name: "MyApp"
  description: "${app.name} is a Spring Boot application written by ${username:Unknown}"
```

Giả sử property `username` chưa được đặt ở nơi khác, `app.description` sẽ có giá trị `MyApp is a Spring Boot application written by Unknown`.

> **LƯU Ý (NOTE):** Bạn nên luôn tham chiếu tới tên property trong placeholder bằng dạng chuẩn (canonical form) của chúng (kebab-case chỉ dùng chữ thường). Điều này sẽ cho phép Spring Boot dùng cùng logic như khi thực hiện [relaxed binding](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.external-config.typesafe-configuration-properties.relaxed-binding) cho `@ConfigurationProperties`.
>
> Ví dụ, `${demo.item-price}` sẽ nhận các dạng `demo.item-price` và `demo.itemPrice` từ file `application.properties`, cũng như `DEMO_ITEMPRICE` từ môi trường hệ thống. Nếu bạn dùng `${demo.itemPrice}` thay thế, `demo.item-price` và `DEMO_ITEMPRICE` sẽ không được xem xét.

> **MẸO (TIP):** Bạn cũng có thể dùng kỹ thuật này để tạo các biến thể "ngắn" của các property Spring Boot hiện có. Xem hướng dẫn how-to *[Dùng tham số dòng lệnh 'ngắn' (Use 'Short' Command Line Arguments)](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.properties-and-configuration.short-command-line-arguments)* để biết chi tiết.

### Làm việc với file nhiều document (Working With Multi-Document Files)

Spring Boot cho phép bạn chia một file vật lý duy nhất thành nhiều document logic, mỗi document được thêm vào một cách độc lập. Các document được xử lý theo thứ tự, từ trên xuống dưới. Các document sau có thể ghi đè các property được định nghĩa ở các document trước.

Với file `application.yaml`, cú pháp nhiều document (multi-document) tiêu chuẩn của YAML được sử dụng. Ba dấu gạch ngang liên tiếp biểu thị sự kết thúc của một document và sự bắt đầu của document tiếp theo.

Ví dụ, file sau có hai document logic:

```yaml
spring:
  application:
    name: "MyApp"
---
spring:
  application:
    name: "MyCloudApp"
  config:
    activate:
      on-cloud-platform: "kubernetes"
```

Với file `application.properties`, một comment đặc biệt `#---` hoặc `!---` được dùng để đánh dấu các điểm phân chia document:

```properties
spring.application.name=MyApp
#---
spring.application.name=MyCloudApp
spring.config.activate.on-cloud-platform=kubernetes
```

> **LƯU Ý (NOTE):** Dấu phân cách trong file property không được có khoảng trắng phía trước và phải có đúng ba ký tự gạch ngang. Các dòng ngay trước và ngay sau dấu phân cách không được có cùng tiền tố comment.

> **MẸO (TIP):** File property nhiều document thường được dùng kết hợp với các activation property như `spring.config.activate.on-profile`. Xem [phần tiếp theo](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.external-config.files.activation-properties) để biết chi tiết.

> **CẢNH BÁO (WARNING):** File property nhiều document không thể được nạp bằng các annotation `@PropertySource` hoặc `@TestPropertySource`.

### Activation property (Activation Properties)

Đôi khi sẽ hữu ích nếu chỉ kích hoạt một tập hợp property nhất định khi một số điều kiện được đáp ứng. Ví dụ, bạn có thể có các property chỉ liên quan khi một profile cụ thể đang hoạt động.

Bạn có thể kích hoạt có điều kiện một document property bằng `spring.config.activate.*`.

Các activation property sau có sẵn:

**Bảng 5. Các activation property**

| Property | Ghi chú |
|---|---|
| `on-profile` | Một biểu thức profile phải khớp để document được kích hoạt. |
| `on-cloud-platform` | `CloudPlatform` phải được phát hiện để document được kích hoạt. |

Ví dụ, đoạn sau chỉ định rằng document thứ hai chỉ được kích hoạt khi chạy trên Kubernetes, và chỉ khi profile "prod" hoặc "staging" đang hoạt động:

**Properties**

```properties
myprop=always-set
#---
spring.config.activate.on-cloud-platform=kubernetes
spring.config.activate.on-profile=prod | staging
myotherprop=sometimes-set
```

**Yaml**

```yaml
myprop:
  "always-set"
---
spring:
  config:
    activate:
      on-cloud-platform: "kubernetes"
      on-profile: "prod | staging"
myotherprop: "sometimes-set"
```

## 7.2.4. Mã hóa property (Encrypting Properties)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.external-config.encrypting>

Spring Boot không cung cấp bất kỳ hỗ trợ có sẵn nào cho việc mã hóa giá trị property, tuy nhiên, nó cung cấp các điểm móc (hook point) cần thiết để sửa đổi các giá trị chứa trong `Environment` của Spring. Interface `EnvironmentPostProcessor` cho phép bạn thao tác `Environment` trước khi ứng dụng khởi động. Xem [Tùy chỉnh Environment hoặc ApplicationContext trước khi nó khởi động](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.application.customize-the-environment-or-application-context) để biết chi tiết.

Nếu bạn cần một cách an toàn để lưu trữ thông tin xác thực và mật khẩu, project [Spring Cloud Vault](https://cloud.spring.io/spring-cloud-vault/) cung cấp hỗ trợ lưu trữ cấu hình bên ngoài trong [HashiCorp Vault](https://www.vaultproject.io/).

## 7.2.5. Làm việc với YAML (Working With YAML)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.external-config.yaml>

[YAML](https://yaml.org) là một tập cha (superset) của JSON và, vì thế, là một định dạng tiện lợi để chỉ định dữ liệu cấu hình phân cấp. Class `SpringApplication` tự động hỗ trợ YAML như một lựa chọn thay thế cho properties bất cứ khi nào bạn có thư viện [SnakeYAML](https://github.com/snakeyaml/snakeyaml) trên classpath.

> **LƯU Ý (NOTE):** Nếu bạn dùng "Starter", SnakeYAML được tự động cung cấp bởi `spring-boot-starter`.

### Ánh xạ YAML sang Properties (Mapping YAML to Properties)

Các document YAML cần được chuyển đổi từ định dạng phân cấp sang một cấu trúc phẳng có thể dùng được với `Environment` của Spring. Ví dụ, xét document YAML sau:

```yaml
environments:
  dev:
    url: "https://dev.example.com"
    name: "Developer Setup"
  prod:
    url: "https://another.example.com"
    name: "My Cool App"
```

Để truy cập các property này từ `Environment`, chúng sẽ được làm phẳng như sau:

```properties
environments.dev.url=https://dev.example.com
environments.dev.name=Developer Setup
environments.prod.url=https://another.example.com
environments.prod.name=My Cool App
```

Tương tự, các danh sách YAML cũng cần được làm phẳng. Chúng được biểu diễn dưới dạng các key property với bộ truy xuất `[index]`. Ví dụ, xét YAML sau:

```yaml
my:
 servers:
 - "dev.example.com"
 - "another.example.com"
```

Ví dụ trên sẽ được chuyển thành các property sau:

```properties
my.servers[0]=dev.example.com
my.servers[1]=another.example.com
```

> **MẸO (TIP):** Các property dùng ký hiệu `[index]` có thể được bind vào các object `List` hoặc `Set` của Java bằng class `Binder` của Spring Boot. Để biết thêm chi tiết, xem phần "[Configuration Properties an toàn kiểu (Type-safe Configuration Properties)](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.external-config.typesafe-configuration-properties)" bên dưới.

> **CẢNH BÁO (WARNING):** File YAML không thể được nạp bằng các annotation `@PropertySource` hoặc `@TestPropertySource`. Vì vậy, trong trường hợp bạn cần nạp giá trị theo cách đó, bạn cần dùng file properties.

### Nạp YAML trực tiếp (Directly Loading YAML)

Spring Framework cung cấp hai class tiện lợi có thể dùng để nạp các document YAML. `YamlPropertiesFactoryBean` nạp YAML thành `Properties` và `YamlMapFactoryBean` nạp YAML thành `Map`.

Bạn cũng có thể dùng class `YamlPropertySourceLoader` nếu muốn nạp YAML thành một `PropertySource` của Spring.

## 7.2.6. Cấu hình giá trị ngẫu nhiên (Configuring Random Values)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.external-config.random-values>

`RandomValuePropertySource` hữu ích cho việc inject các giá trị ngẫu nhiên (ví dụ, vào secret hoặc test case). Nó có thể sinh ra số nguyên (integer), long, uuid, hoặc chuỗi, như trong ví dụ sau:

**Properties**

```properties
my.secret=${random.value}
my.number=${random.int}
my.bignumber=${random.long}
my.uuid=${random.uuid}
my.number-less-than-ten=${random.int(10)}
my.number-in-range=${random.int[1024,65536]}
```

**Yaml**

```yaml
my:
  secret: "${random.value}"
  number: "${random.int}"
  bignumber: "${random.long}"
  uuid: "${random.uuid}"
  number-less-than-ten: "${random.int(10)}"
  number-in-range: "${random.int[1024,65536]}"
```

Cú pháp `random.int*` là `OPEN value (,max) CLOSE`, trong đó `OPEN,CLOSE` là bất kỳ ký tự nào và `value,max` là số nguyên. Nếu `max` được cung cấp, thì `value` là giá trị nhỏ nhất và `max` là giá trị lớn nhất (không bao gồm).

## 7.2.7. Cấu hình property môi trường hệ thống (Configuring System Environment Properties)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.external-config.system-environment>

Spring Boot hỗ trợ đặt tiền tố cho các environment property. Điều này hữu ích nếu môi trường hệ thống được chia sẻ bởi nhiều ứng dụng Spring Boot có các yêu cầu cấu hình khác nhau. Tiền tố cho các property môi trường hệ thống có thể được đặt trực tiếp trên `SpringApplication`.

Ví dụ, nếu bạn đặt tiền tố là `input`, một property như `remote.timeout` cũng sẽ được phân giải thành `input.remote.timeout` trong môi trường hệ thống.

## 7.2.8. Configuration Properties an toàn kiểu (Type-safe Configuration Properties)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.external-config.typesafe-configuration-properties>

Việc dùng annotation `@Value("${property}")` để inject các configuration property đôi khi có thể cồng kềnh, đặc biệt nếu bạn làm việc với nhiều property hoặc dữ liệu của bạn có bản chất phân cấp. Spring Boot cung cấp một phương pháp thay thế để làm việc với property, cho phép các bean có kiểu mạnh (strongly typed) quản lý và kiểm tra (validate) cấu hình của ứng dụng.

> **MẸO (TIP):** Xem thêm [sự khác biệt giữa `@Value` và configuration property an toàn kiểu](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.external-config.typesafe-configuration-properties.vs-value-annotation).

### Bind property kiểu JavaBean (JavaBean Properties Binding)

Có thể bind một bean khai báo các property JavaBean tiêu chuẩn như trong ví dụ sau:

**Java**

```java
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("my.service")
public class MyProperties {

    private boolean enabled;

    private InetAddress remoteAddress;

    private final Security security = new Security();

    // getters / setters...

    public boolean isEnabled() {
        return this.enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public InetAddress getRemoteAddress() {
        return this.remoteAddress;
    }

    public void setRemoteAddress(InetAddress remoteAddress) {
        this.remoteAddress = remoteAddress;
    }

    public Security getSecurity() {
        return this.security;
    }

    public static class Security {

        private String username;

        private String password;

        private List<String> roles = new ArrayList<>(Collections.singleton("USER"));

        // getters / setters...

        public String getUsername() {
            return this.username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return this.password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public List<String> getRoles() {
            return this.roles;
        }

        public void setRoles(List<String> roles) {
            this.roles = roles;
        }

    }

}
```

**Kotlin**

```kotlin
import org.springframework.boot.context.properties.ConfigurationProperties
import java.net.InetAddress

@ConfigurationProperties("my.service")
class MyProperties {

    var isEnabled = false

    var remoteAddress: InetAddress? = null

    val security = Security()

    class Security {

        var username: String? = null

        var password: String? = null

        var roles: List<String> = ArrayList(setOf("USER"))

    }

}
```

POJO trên định nghĩa các property sau:

- `my.service.enabled`, với giá trị mặc định là `false`.
- `my.service.remote-address`, với kiểu có thể được ép kiểu (coerce) từ `String`.
- `my.service.security.username`, với một object "security" lồng nhau có tên được xác định bởi tên của property. Cụ thể, kiểu hoàn toàn không được dùng ở đây và có thể đã là `SecurityProperties`.
- `my.service.security.password`.
- `my.service.security.roles`, với một collection `String` có giá trị mặc định là `USER`.

> **LƯU Ý (NOTE):** Các property ánh xạ tới các class `@ConfigurationProperties` có sẵn trong Spring Boot, được cấu hình thông qua file properties, file YAML, biến môi trường và các cơ chế khác, là public API nhưng các phương thức truy cập (getter/setter) của chính class đó không nhằm để sử dụng trực tiếp.

> **LƯU Ý (NOTE):** Cách bố trí như vậy dựa vào một constructor rỗng mặc định, và getter/setter thường là bắt buộc, vì việc bind thông qua các property descriptor Java Beans tiêu chuẩn, giống như trong Spring MVC. Setter có thể được bỏ qua trong các trường hợp sau:
>
> - Map, miễn là chúng được khởi tạo, cần getter nhưng không nhất thiết cần setter, vì chúng có thể được binder thay đổi (mutate).
> - Collection và mảng có thể được truy cập thông qua index (thường với YAML) hoặc bằng một giá trị duy nhất phân tách bằng dấu phẩy (properties). Trong trường hợp sau, setter là bắt buộc. Chúng tôi khuyên luôn thêm setter cho các kiểu như vậy. Nếu bạn khởi tạo một collection, hãy đảm bảo nó không phải là bất biến (immutable) (như trong ví dụ trên).
> - Nếu các property POJO lồng nhau được khởi tạo (như field `Security` trong ví dụ trên), setter là không bắt buộc. Nếu bạn muốn binder tạo instance ngay lập tức bằng constructor mặc định của nó, bạn cần setter.
>
> Một số người dùng Project Lombok để tự động thêm getter và setter. Hãy đảm bảo Lombok không sinh ra bất kỳ constructor đặc biệt nào cho kiểu như vậy, vì nó được container tự động dùng để khởi tạo object.
>
> Cuối cùng, chỉ các property Java Bean tiêu chuẩn được xem xét và việc bind trên các property static không được hỗ trợ.

### Bind qua constructor (Constructor Binding)

Ví dụ ở phần trước có thể được viết lại theo cách bất biến (immutable) như trong ví dụ sau:

**Java**

```java
import java.net.InetAddress;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("my.service")
public class MyProperties {

    // fields...

    private final boolean enabled;

    private final InetAddress remoteAddress;

    private final Security security;

    public MyProperties(boolean enabled, InetAddress remoteAddress, Security security) {
        this.enabled = enabled;
        this.remoteAddress = remoteAddress;
        this.security = security;
    }

    // getters...

    public boolean isEnabled() {
        return this.enabled;
    }

    public InetAddress getRemoteAddress() {
        return this.remoteAddress;
    }

    public Security getSecurity() {
        return this.security;
    }

    public static class Security {

        // fields...

        private final String username;

        private final String password;

        private final List<String> roles;

        public Security(String username, String password, @DefaultValue("USER") List<String> roles) {
            this.username = username;
            this.password = password;
            this.roles = roles;
        }

        // getters...

        public String getUsername() {
            return this.username;
        }

        public String getPassword() {
            return this.password;
        }

        public List<String> getRoles() {
            return this.roles;
        }

    }

}
```

**Kotlin**

```kotlin
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.DefaultValue
import java.net.InetAddress

@ConfigurationProperties("my.service")
class MyProperties(val enabled: Boolean, val remoteAddress: InetAddress,
        val security: Security) {

    class Security(val username: String, val password: String,
            @param:DefaultValue("USER") val roles: List<String>)

}
```

Trong thiết lập này, sự hiện diện của một constructor có tham số duy nhất ngụ ý rằng constructor binding nên được sử dụng. Điều này có nghĩa là binder sẽ tìm một constructor có các tham số mà bạn muốn được bind. Nếu class của bạn có nhiều constructor, annotation `@ConstructorBinding` có thể được dùng để chỉ định constructor nào được dùng cho constructor binding. Để không dùng constructor binding cho một class có một constructor có tham số duy nhất, constructor đó phải được đánh annotation `@Autowired`. Constructor binding có thể được dùng với record. Trừ khi record của bạn có nhiều constructor, không cần dùng `@ConstructorBinding`.

Các thành viên lồng nhau của một class được bind qua constructor (chẳng hạn như `Security` trong ví dụ trên) cũng sẽ được bind thông qua constructor của chúng.

Giá trị mặc định có thể được chỉ định bằng `@DefaultValue` trên các tham số constructor và các thành phần của record. Conversion service sẽ được áp dụng để ép kiểu giá trị `String` của annotation sang kiểu đích của một property bị thiếu.

Quay lại ví dụ trước, nếu không có property nào được bind vào `Security`, instance `MyProperties` sẽ chứa giá trị `null` cho `security`. Để nó chứa một instance `Security` khác null ngay cả khi không có property nào được bind vào (khi dùng Kotlin, điều này sẽ yêu cầu các tham số `username` và `password` của `Security` được khai báo là nullable vì chúng không có giá trị mặc định), hãy dùng một annotation `@DefaultValue` rỗng:

**Java**

```java
public MyProperties(boolean enabled, InetAddress remoteAddress, @DefaultValue Security security) {
    this.enabled = enabled;
    this.remoteAddress = remoteAddress;
    this.security = security;
}
```

**Kotlin**

```kotlin
class MyProperties(val enabled: Boolean, val remoteAddress: InetAddress,
        @DefaultValue val security: Security) {

    class Security(val username: String?, val password: String?,
            @param:DefaultValue("USER") val roles: List<String>)

}
```

> **LƯU Ý (NOTE):** Để dùng constructor binding, class phải được bật bằng `@EnableConfigurationProperties` hoặc bằng configuration property scanning. Bạn không thể dùng constructor binding với các bean được tạo bởi các cơ chế Spring thông thường (ví dụ, các bean `@Component`, các bean được tạo bằng phương thức `@Bean` hoặc các bean được nạp bằng `@Import`).

> **LƯU Ý (NOTE):** Để dùng constructor binding, class phải được biên dịch với `-parameters`. Điều này sẽ tự động xảy ra nếu bạn dùng Gradle plugin của Spring Boot hoặc nếu bạn dùng Maven và `spring-boot-starter-parent`.

> **LƯU Ý (NOTE):** Không khuyến khích dùng `java.util.Optional` với `@ConfigurationProperties` vì nó chủ yếu nhằm để dùng làm kiểu trả về. Vì vậy, nó không phù hợp lắm cho việc inject configuration property. Để nhất quán với các property thuộc kiểu khác, nếu bạn khai báo một property `Optional` và nó không có giá trị, `null` sẽ được bind thay vì một `Optional` rỗng.

### Bật các kiểu được đánh annotation @ConfigurationProperties (Enabling @ConfigurationProperties-annotated Types)

Spring Boot cung cấp hạ tầng để bind các kiểu `@ConfigurationProperties` và đăng ký chúng thành bean. Bạn có thể bật configuration properties cho từng class một, hoặc bật configuration property scanning, hoạt động theo cách tương tự như component scanning.

Đôi khi, các class được đánh annotation `@ConfigurationProperties` có thể không phù hợp để quét, ví dụ, nếu bạn đang phát triển auto-configuration của riêng mình hoặc muốn bật chúng có điều kiện. Trong những trường hợp này, hãy chỉ định danh sách các kiểu cần xử lý bằng annotation `@EnableConfigurationProperties`. Điều này có thể được thực hiện trên bất kỳ class `@Configuration` nào, như trong ví dụ sau:

**Java**

```java
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SomeProperties.class)
public class MyConfiguration {

}
```

**Kotlin**

```kotlin
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SomeProperties::class)
class MyConfiguration
```

**Java**

```java
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("some.properties")
public class SomeProperties {

}
```

**Kotlin**

```kotlin
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("some.properties")
class SomeProperties
```

Để dùng configuration property scanning, hãy thêm annotation `@ConfigurationPropertiesScan` vào ứng dụng. Thông thường, nó được thêm vào class ứng dụng chính được đánh annotation `@SpringBootApplication`, nhưng nó có thể được thêm vào bất kỳ class `@Configuration` nào. Theo mặc định, việc quét sẽ diễn ra từ package của class khai báo annotation. Nếu bạn muốn định nghĩa các package cụ thể để quét, bạn có thể làm như trong ví dụ sau:

**Java**

```java
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan({ "com.example.app", "com.example.another" })
public class MyApplication {

}
```

**Kotlin**

```kotlin
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan

@SpringBootApplication
@ConfigurationPropertiesScan("com.example.app", "com.example.another")
class MyApplication
```

> **LƯU Ý (NOTE):** Khi bean `@ConfigurationProperties` được đăng ký bằng configuration property scanning hoặc thông qua `@EnableConfigurationProperties`, bean có một tên theo quy ước: `<prefix>-<fqn>`, trong đó `<prefix>` là tiền tố key môi trường được chỉ định trong annotation `@ConfigurationProperties` và `<fqn>` là tên đầy đủ (fully qualified name) của bean. Nếu annotation không cung cấp tiền tố nào, chỉ tên đầy đủ của bean được sử dụng.
>
> Giả sử nó nằm trong package `com.example.app`, tên bean của ví dụ `SomeProperties` ở trên là `some.properties-com.example.app.SomeProperties`.

Chúng tôi khuyến nghị `@ConfigurationProperties` chỉ làm việc với environment và, đặc biệt, không inject các bean khác từ context. Với các trường hợp đặc biệt (corner case), có thể dùng setter injection hoặc bất kỳ interface `*Aware` nào do framework cung cấp (chẳng hạn như `EnvironmentAware` nếu bạn cần truy cập `Environment`). Nếu bạn vẫn muốn inject các bean khác bằng constructor, bean configuration properties phải được đánh annotation `@Component` và dùng property binding dựa trên JavaBean.

### Sử dụng các kiểu được đánh annotation @ConfigurationProperties (Using @ConfigurationProperties-annotated Types)

Kiểu cấu hình này hoạt động đặc biệt tốt với cấu hình YAML bên ngoài của `SpringApplication`, như trong ví dụ sau:

```yaml
my:
  service:
    remote-address: 192.168.1.1
    security:
      username: "admin"
      roles:
      - "USER"
      - "ADMIN"
```

Để làm việc với các bean `@ConfigurationProperties`, bạn có thể inject chúng theo cùng cách như bất kỳ bean nào khác, như trong ví dụ sau:

**Java**

```java
import org.springframework.stereotype.Service;

@Service
public class MyService {

    private final MyProperties properties;

    public MyService(MyProperties properties) {
        this.properties = properties;
    }

    public void openConnection() {
        Server server = new Server(this.properties.getRemoteAddress());
        server.start();
        // ...
    }

    // ...

}
```

**Kotlin**

```kotlin
import org.springframework.stereotype.Service

@Service
class MyService(val properties: MyProperties) {

    fun openConnection() {
        val server = Server(properties.remoteAddress)
        server.start()
        // ...
    }

    // ...

}
```

> **MẸO (TIP):** Dùng `@ConfigurationProperties` cũng cho phép bạn sinh ra các file metadata mà IDE có thể dùng để cung cấp tính năng tự động hoàn thành (auto-completion) cho các key của riêng bạn. Xem [phụ lục](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#appendix.configuration-metadata) để biết chi tiết.

### Cấu hình cho thành phần bên thứ ba (Third-party Configuration)

Ngoài việc dùng `@ConfigurationProperties` để đánh annotation cho một class, bạn cũng có thể dùng nó trên các phương thức `@Bean` public. Làm như vậy có thể đặc biệt hữu ích khi bạn muốn bind property vào các component bên thứ ba nằm ngoài tầm kiểm soát của bạn.

Để cấu hình một bean từ các property của `Environment`, hãy thêm `@ConfigurationProperties` vào phần đăng ký bean của nó, như trong ví dụ sau:

**Java**

```java
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ThirdPartyConfiguration {

    @Bean
    @ConfigurationProperties(prefix = "another")
    public AnotherComponent anotherComponent() {
        return new AnotherComponent();
    }

}
```

**Kotlin**

```kotlin
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class ThirdPartyConfiguration {

    @Bean
    @ConfigurationProperties(prefix = "another")
    fun anotherComponent(): AnotherComponent = AnotherComponent()

}
```

Bất kỳ property JavaBean nào được định nghĩa với tiền tố `another` đều được ánh xạ lên bean `AnotherComponent` đó theo cách tương tự như ví dụ `SomeProperties` trước đó.

### Relaxed Binding

Spring Boot dùng một số quy tắc nới lỏng (relaxed) để bind các property của `Environment` vào các bean `@ConfigurationProperties`, vì vậy không cần có sự trùng khớp chính xác giữa tên property trong `Environment` và tên property của bean. Các ví dụ phổ biến mà điều này hữu ích bao gồm các environment property phân tách bằng dấu gạch ngang (ví dụ, `context-path` bind vào `contextPath`), và các environment property viết hoa (ví dụ, `PORT` bind vào `port`).

Ví dụ, xét class `@ConfigurationProperties` sau:

**Java**

```java
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "my.main-project.person")
public class MyPersonProperties {

    private String firstName;

    public String getFirstName() {
        return this.firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

}
```

**Kotlin**

```kotlin
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "my.main-project.person")
class MyPersonProperties {

    var firstName: String? = null

}
```

Với đoạn code trên, tất cả các tên property sau đều có thể được sử dụng:

**Bảng 6. relaxed binding**

| Property | Ghi chú |
|---|---|
| `my.main-project.person.first-name` | Kebab case, được khuyến nghị dùng trong file `.properties` và YAML. |
| `my.main-project.person.firstName` | Cú pháp camel case tiêu chuẩn. |
| `my.main-project.person.first_name` | Ký hiệu gạch dưới (underscore), là một định dạng thay thế để dùng trong file `.properties` và YAML. |
| `MY_MAINPROJECT_PERSON_FIRSTNAME` | Định dạng chữ hoa, được khuyến nghị khi dùng biến môi trường hệ thống. |

> **LƯU Ý (NOTE):** Giá trị `prefix` cho annotation *phải* ở dạng kebab case (chữ thường và phân tách bằng `-`, chẳng hạn như `my.main-project.person`).

**Bảng 7. quy tắc relaxed binding theo từng property source**

| Property Source | Đơn giản (Simple) | Danh sách (List) |
|---|---|---|
| File Properties | Camel case, kebab case, hoặc ký hiệu gạch dưới | Cú pháp danh sách tiêu chuẩn dùng `[ ]` hoặc các giá trị phân tách bằng dấu phẩy |
| File YAML | Camel case, kebab case, hoặc ký hiệu gạch dưới | Cú pháp danh sách YAML tiêu chuẩn hoặc các giá trị phân tách bằng dấu phẩy |
| Biến môi trường | Định dạng chữ hoa với dấu gạch dưới làm dấu phân cách (xem [Bind từ biến môi trường](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.external-config.typesafe-configuration-properties.relaxed-binding.environment-variables)). | Các giá trị số được bao quanh bởi dấu gạch dưới (xem [Bind từ biến môi trường](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.external-config.typesafe-configuration-properties.relaxed-binding.environment-variables)) |
| System properties | Camel case, kebab case, hoặc ký hiệu gạch dưới | Cú pháp danh sách tiêu chuẩn dùng `[ ]` hoặc các giá trị phân tách bằng dấu phẩy |

> **MẸO (TIP):** Chúng tôi khuyến nghị, khi có thể, các property nên được lưu ở định dạng kebab chữ thường, chẳng hạn như `my.person.first-name=Rod`.

#### Bind Map (Binding Maps)

Khi bind vào các property kiểu `Map`, bạn có thể cần dùng một ký hiệu ngoặc vuông đặc biệt để giá trị `key` gốc được giữ nguyên. Nếu key không được bao quanh bởi `[]`, mọi ký tự không phải chữ-số, `-` hoặc `.` sẽ bị loại bỏ.

Ví dụ, xét việc bind các property sau vào một `Map<String,String>`:

**Properties**

```properties
my.map.[/key1]=value1
my.map.[/key2]=value2
my.map./key3=value3
```

**Yaml**

```yaml
my:
  map:
    "[/key1]": "value1"
    "[/key2]": "value2"
    "/key3": "value3"
```

> **LƯU Ý (NOTE):** Với file YAML, dấu ngoặc vuông cần được bao trong dấu nháy để các key được phân tích đúng.

Các property trên sẽ bind vào một `Map` với `/key1`, `/key2` và `key3` là các key trong map. Dấu gạch chéo đã bị loại bỏ khỏi `key3` vì nó không được bao quanh bởi dấu ngoặc vuông.

Khi bind vào các giá trị vô hướng (scalar), các key có `.` bên trong không cần được bao quanh bởi `[]`. Giá trị vô hướng bao gồm enum và tất cả các kiểu trong package `java.lang` ngoại trừ `Object`. Bind `a.b=c` vào `Map<String, String>` sẽ giữ nguyên `.` trong key và trả về một Map với entry `{"a.b"="c"}`. Với bất kỳ kiểu nào khác, bạn cần dùng ký hiệu ngoặc vuông nếu `key` của bạn chứa `.`. Ví dụ, bind `a.b=c` vào `Map<String, Object>` sẽ trả về một Map với entry `{"a"={"b"="c"}}` trong khi `[a.b]=c` sẽ trả về một Map với entry `{"a.b"="c"}`.

#### Bind từ biến môi trường (Binding From Environment Variables)

Hầu hết các hệ điều hành áp đặt các quy tắc nghiêm ngặt về tên có thể dùng cho biến môi trường. Ví dụ, biến shell trên Linux chỉ có thể chứa chữ cái (`a` đến `z` hoặc `A` đến `Z`), chữ số (`0` đến `9`) hoặc ký tự gạch dưới (`_`). Theo quy ước, biến shell Unix cũng có tên viết HOA.

Các quy tắc relaxed binding của Spring Boot, trong phạm vi có thể, được thiết kế để tương thích với các hạn chế đặt tên này.

Để chuyển một tên property ở dạng chuẩn (canonical form) thành tên biến môi trường, bạn có thể làm theo các quy tắc sau:

- Thay dấu chấm (`.`) bằng dấu gạch dưới (`_`).
- Loại bỏ mọi dấu gạch ngang (`-`).
- Chuyển thành chữ hoa.

Ví dụ, configuration property `spring.main.log-startup-info` sẽ là một biến môi trường tên là `SPRING_MAIN_LOGSTARTUPINFO`.

Biến môi trường cũng có thể được dùng khi bind vào danh sách object. Để bind vào một `List`, số thứ tự phần tử nên được bao quanh bởi dấu gạch dưới trong tên biến.

Ví dụ, configuration property `my.service[0].other` sẽ dùng một biến môi trường tên là `MY_SERVICE_0_OTHER`.

#### Caching

Relaxed binding dùng cache để cải thiện hiệu năng. Theo mặc định, việc caching này chỉ được áp dụng cho các property source bất biến (immutable). Để tùy chỉnh hành vi này, ví dụ để bật caching cho các property source có thể thay đổi (mutable), hãy dùng `ConfigurationPropertyCaching`.

### Gộp các kiểu phức tạp (Merging Complex Types)

Khi danh sách được cấu hình ở nhiều nơi, việc ghi đè hoạt động bằng cách thay thế toàn bộ danh sách.

Ví dụ, giả sử có một object `MyPojo` với các thuộc tính `name` và `description` mặc định là `null`. Ví dụ sau expose một danh sách các object `MyPojo` từ `MyProperties`:

**Java**

```java
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("my")
public class MyProperties {

    private final List<MyPojo> list = new ArrayList<>();

    public List<MyPojo> getList() {
        return this.list;
    }

}
```

**Kotlin**

```kotlin
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("my")
class MyProperties {

    val list: List<MyPojo> = ArrayList()

}
```

Xét cấu hình sau:

**Properties**

```properties
my.list[0].name=my name
my.list[0].description=my description
#---
spring.config.activate.on-profile=dev
my.list[0].name=my another name
```

**Yaml**

```yaml
my:
  list:
  - name: "my name"
    description: "my description"
---
spring:
  config:
    activate:
      on-profile: "dev"
my:
  list:
  - name: "my another name"
```

Nếu profile `dev` không hoạt động, `MyProperties.list` chứa một entry `MyPojo`, như đã định nghĩa trước đó. Tuy nhiên, nếu profile `dev` được bật, `list` *vẫn* chỉ chứa một entry (với name là `my another name` và description là `null`). Cấu hình này *không* thêm instance `MyPojo` thứ hai vào danh sách, và nó cũng không gộp các mục lại.

Khi một `List` được chỉ định trong nhiều profile, cái có độ ưu tiên cao nhất (và chỉ cái đó) được sử dụng. Xét ví dụ sau:

**Properties**

```properties
my.list[0].name=my name
my.list[0].description=my description
my.list[1].name=another name
my.list[1].description=another description
#---
spring.config.activate.on-profile=dev
my.list[0].name=my another name
```

**Yaml**

```yaml
my:
  list:
  - name: "my name"
    description: "my description"
  - name: "another name"
    description: "another description"
---
spring:
  config:
    activate:
      on-profile: "dev"
my:
  list:
  - name: "my another name"
```

Trong ví dụ trên, nếu profile `dev` đang hoạt động, `MyProperties.list` chứa *một* entry `MyPojo` (với name là `my another name` và description là `null`). Với YAML, cả danh sách phân tách bằng dấu phẩy và danh sách YAML đều có thể được dùng để ghi đè hoàn toàn nội dung của danh sách.

Với các property kiểu `Map`, bạn có thể bind với các giá trị property lấy từ nhiều nguồn. Tuy nhiên, với cùng một property ở nhiều nguồn, cái có độ ưu tiên cao nhất được sử dụng. Ví dụ sau expose một `Map<String, MyPojo>` từ `MyProperties`:

**Java**

```java
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("my")
public class MyProperties {

    private final Map<String, MyPojo> map = new LinkedHashMap<>();

    public Map<String, MyPojo> getMap() {
        return this.map;
    }

}
```

**Kotlin**

```kotlin
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("my")
class MyProperties {

    val map: Map<String, MyPojo> = LinkedHashMap()

}
```

Xét cấu hình sau:

**Properties**

```properties
my.map.key1.name=my name 1
my.map.key1.description=my description 1
#---
spring.config.activate.on-profile=dev
my.map.key1.name=dev name 1
my.map.key2.name=dev name 2
my.map.key2.description=dev description 2
```

**Yaml**

```yaml
my:
  map:
    key1:
      name: "my name 1"
      description: "my description 1"
---
spring:
  config:
    activate:
      on-profile: "dev"
my:
  map:
    key1:
      name: "dev name 1"
    key2:
      name: "dev name 2"
      description: "dev description 2"
```

Nếu profile `dev` không hoạt động, `MyProperties.map` chứa một entry với key `key1` (với name là `my name 1` và description là `my description 1`). Tuy nhiên, nếu profile `dev` được bật, `map` chứa hai entry với key `key1` (với name là `dev name 1` và description là `my description 1`) và `key2` (với name là `dev name 2` và description là `dev description 2`).

> **LƯU Ý (NOTE):** Các quy tắc gộp ở trên áp dụng cho property từ tất cả các property source, chứ không chỉ riêng file.

### Chuyển đổi property (Properties Conversion)

Spring Boot cố gắng ép kiểu các application property bên ngoài sang đúng kiểu khi nó bind vào các bean `@ConfigurationProperties`. Nếu bạn cần chuyển đổi kiểu tùy chỉnh, bạn có thể cung cấp một bean `ConversionService` (với bean tên là `conversionService`) hoặc các property editor tùy chỉnh (thông qua một bean `CustomEditorConfigurer`) hoặc các `Converters` tùy chỉnh (với các bean definition được đánh annotation `@ConfigurationPropertiesBinding`).

> **LƯU Ý (NOTE):** Vì bean này được yêu cầu từ rất sớm trong vòng đời ứng dụng, hãy đảm bảo giới hạn các dependency mà `ConversionService` của bạn sử dụng. Thông thường, bất kỳ dependency nào bạn cần có thể chưa được khởi tạo đầy đủ tại thời điểm tạo. Bạn có thể muốn đổi tên `ConversionService` tùy chỉnh của mình nếu nó không cần thiết cho việc ép kiểu các key cấu hình và chỉ dựa vào các converter tùy chỉnh được đánh dấu bằng `@ConfigurationPropertiesBinding`.

#### Chuyển đổi Duration (Converting Durations)

Spring Boot có hỗ trợ chuyên biệt cho việc biểu diễn khoảng thời gian (duration). Nếu bạn expose một property `java.time.Duration`, các định dạng sau trong application properties có sẵn:

- Một biểu diễn `long` thông thường (dùng mili giây làm đơn vị mặc định trừ khi `@DurationUnit` được chỉ định)
- Định dạng ISO-8601 tiêu chuẩn [được dùng bởi `java.time.Duration`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/time/Duration.html#parse(java.lang.CharSequence))
- Một định dạng dễ đọc hơn, trong đó giá trị và đơn vị đi liền với nhau (`10s` nghĩa là 10 giây)

Xét ví dụ sau:

**Java**

```java
import java.time.Duration;
import java.time.temporal.ChronoUnit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DurationUnit;

@ConfigurationProperties("my")
public class MyProperties {

    @DurationUnit(ChronoUnit.SECONDS)
    private Duration sessionTimeout = Duration.ofSeconds(30);

    private Duration readTimeout = Duration.ofMillis(1000);

    // getters / setters...

    public Duration getSessionTimeout() {
        return this.sessionTimeout;
    }

    public void setSessionTimeout(Duration sessionTimeout) {
        this.sessionTimeout = sessionTimeout;
    }

    public Duration getReadTimeout() {
        return this.readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }

}
```

**Kotlin**

```kotlin
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.convert.DurationUnit
import java.time.Duration
import java.time.temporal.ChronoUnit

@ConfigurationProperties("my")
class MyProperties {

    @DurationUnit(ChronoUnit.SECONDS)
    var sessionTimeout = Duration.ofSeconds(30)

    var readTimeout = Duration.ofMillis(1000)

}
```

Để chỉ định session timeout là 30 giây, `30`, `PT30S` và `30s` đều tương đương nhau. Một read timeout 500ms có thể được chỉ định ở bất kỳ dạng nào sau đây: `500`, `PT0.5S` và `500ms`.

Bạn cũng có thể dùng bất kỳ đơn vị nào được hỗ trợ. Đó là:

- `ns` cho nano giây
- `us` cho micro giây
- `ms` cho mili giây
- `s` cho giây
- `m` cho phút
- `h` cho giờ
- `d` cho ngày

Đơn vị mặc định là mili giây và có thể được ghi đè bằng `@DurationUnit` như minh họa trong ví dụ trên.

Nếu bạn thích dùng constructor binding, các property tương tự có thể được expose, như trong ví dụ sau:

**Java**

```java
import java.time.Duration;
import java.time.temporal.ChronoUnit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.convert.DurationUnit;

@ConfigurationProperties("my")
public class MyProperties {

    // fields...

    private final Duration sessionTimeout;

    private final Duration readTimeout;

    public MyProperties(@DurationUnit(ChronoUnit.SECONDS) @DefaultValue("30s") Duration sessionTimeout,
            @DefaultValue("1000ms") Duration readTimeout) {
        this.sessionTimeout = sessionTimeout;
        this.readTimeout = readTimeout;
    }

    // getters...

    public Duration getSessionTimeout() {
        return this.sessionTimeout;
    }

    public Duration getReadTimeout() {
        return this.readTimeout;
    }

}
```

**Kotlin**

```kotlin
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.DefaultValue
import org.springframework.boot.convert.DurationUnit
import java.time.Duration
import java.time.temporal.ChronoUnit

@ConfigurationProperties("my")
class MyProperties(@param:DurationUnit(ChronoUnit.SECONDS) @param:DefaultValue("30s") val sessionTimeout: Duration,
        @param:DefaultValue("1000ms") val readTimeout: Duration)
```

> **MẸO (TIP):** Nếu bạn đang nâng cấp một property kiểu `Long`, hãy đảm bảo định nghĩa đơn vị (bằng `@DurationUnit`) nếu nó không phải mili giây. Làm như vậy sẽ cho một lộ trình nâng cấp minh bạch, đồng thời hỗ trợ một định dạng phong phú hơn nhiều.

#### Chuyển đổi Period (Converting Periods)

Ngoài duration, Spring Boot cũng có thể làm việc với kiểu `java.time.Period`. Các định dạng sau có thể được dùng trong application properties:

- Một biểu diễn `int` thông thường (dùng ngày làm đơn vị mặc định trừ khi `@PeriodUnit` được chỉ định)
- Định dạng ISO-8601 tiêu chuẩn [được dùng bởi `java.time.Period`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/time/Period.html#parse(java.lang.CharSequence))
- Một định dạng đơn giản hơn, trong đó các cặp giá trị và đơn vị đi liền với nhau (`1y3d` nghĩa là 1 năm và 3 ngày)

Các đơn vị sau được hỗ trợ với định dạng đơn giản:

- `y` cho năm
- `m` cho tháng
- `w` cho tuần
- `d` cho ngày

> **LƯU Ý (NOTE):** Kiểu `java.time.Period` thực ra không bao giờ lưu số tuần, đó là một cách viết tắt có nghĩa là "7 ngày".

#### Chuyển đổi kích thước dữ liệu (Converting Data Sizes)

Spring Framework có một value type `DataSize` biểu diễn kích thước theo byte. Nếu bạn expose một property `DataSize`, các định dạng sau trong application properties có sẵn:

- Một biểu diễn `long` thông thường (dùng byte làm đơn vị mặc định trừ khi `@DataSizeUnit` được chỉ định)
- Một định dạng dễ đọc hơn, trong đó giá trị và đơn vị đi liền với nhau (`10MB` nghĩa là 10 megabyte)

Xét ví dụ sau:

**Java**

```java
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DataSizeUnit;
import org.springframework.util.unit.DataSize;
import org.springframework.util.unit.DataUnit;

@ConfigurationProperties("my")
public class MyProperties {

    @DataSizeUnit(DataUnit.MEGABYTES)
    private DataSize bufferSize = DataSize.ofMegabytes(2);

    private DataSize sizeThreshold = DataSize.ofBytes(512);

    // getters/setters...

    public DataSize getBufferSize() {
        return this.bufferSize;
    }

    public void setBufferSize(DataSize bufferSize) {
        this.bufferSize = bufferSize;
    }

    public DataSize getSizeThreshold() {
        return this.sizeThreshold;
    }

    public void setSizeThreshold(DataSize sizeThreshold) {
        this.sizeThreshold = sizeThreshold;
    }

}
```

**Kotlin**

```kotlin
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.convert.DataSizeUnit
import org.springframework.util.unit.DataSize
import org.springframework.util.unit.DataUnit

@ConfigurationProperties("my")
class MyProperties {

    @DataSizeUnit(DataUnit.MEGABYTES)
    var bufferSize = DataSize.ofMegabytes(2)

    var sizeThreshold = DataSize.ofBytes(512)

}
```

Để chỉ định kích thước buffer là 10 megabyte, `10` và `10MB` là tương đương. Một ngưỡng kích thước 256 byte có thể được chỉ định là `256` hoặc `256B`.

Bạn cũng có thể dùng bất kỳ đơn vị nào được hỗ trợ. Đó là:

- `B` cho byte
- `KB` cho kilobyte
- `MB` cho megabyte
- `GB` cho gigabyte
- `TB` cho terabyte

Đơn vị mặc định là byte và có thể được ghi đè bằng `@DataSizeUnit` như minh họa trong ví dụ trên.

Nếu bạn thích dùng constructor binding, các property tương tự có thể được expose, như trong ví dụ sau:

**Java**

```java
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.convert.DataSizeUnit;
import org.springframework.util.unit.DataSize;
import org.springframework.util.unit.DataUnit;

@ConfigurationProperties("my")
public class MyProperties {

    // fields...

    private final DataSize bufferSize;

    private final DataSize sizeThreshold;

    public MyProperties(@DataSizeUnit(DataUnit.MEGABYTES) @DefaultValue("2MB") DataSize bufferSize,
            @DefaultValue("512B") DataSize sizeThreshold) {
        this.bufferSize = bufferSize;
        this.sizeThreshold = sizeThreshold;
    }

    // getters...

    public DataSize getBufferSize() {
        return this.bufferSize;
    }

    public DataSize getSizeThreshold() {
        return this.sizeThreshold;
    }

}
```

**Kotlin**

```kotlin
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.DefaultValue
import org.springframework.boot.convert.DataSizeUnit
import org.springframework.util.unit.DataSize
import org.springframework.util.unit.DataUnit

@ConfigurationProperties("my")
class MyProperties(@param:DataSizeUnit(DataUnit.MEGABYTES) @param:DefaultValue("2MB") val bufferSize: DataSize,
        @param:DefaultValue("512B") val sizeThreshold: DataSize)
```

> **MẸO (TIP):** Nếu bạn đang nâng cấp một property kiểu `Long`, hãy đảm bảo định nghĩa đơn vị (bằng `@DataSizeUnit`) nếu nó không phải byte. Làm như vậy sẽ cho một lộ trình nâng cấp minh bạch, đồng thời hỗ trợ một định dạng phong phú hơn nhiều.

### Validation cho @ConfigurationProperties (@ConfigurationProperties Validation)

Spring Boot cố gắng kiểm tra (validate) các class `@ConfigurationProperties` mỗi khi chúng được đánh annotation `@Validated` của Spring. Bạn có thể dùng trực tiếp các annotation ràng buộc (constraint) JSR-303 `jakarta.validation` trên class cấu hình. Để làm vậy, hãy đảm bảo có một implementation JSR-303 tương thích trên classpath rồi thêm các annotation ràng buộc vào các field, như trong ví dụ sau:

**Java**

```java
import java.net.InetAddress;

import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("my.service")
@Validated
public class MyProperties {

    @NotNull
    private InetAddress remoteAddress;

    // getters/setters...

    public InetAddress getRemoteAddress() {
        return this.remoteAddress;
    }

    public void setRemoteAddress(InetAddress remoteAddress) {
        this.remoteAddress = remoteAddress;
    }

}
```

**Kotlin**

```kotlin
import jakarta.validation.constraints.NotNull
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated
import java.net.InetAddress

@ConfigurationProperties("my.service")
@Validated
class MyProperties {

    var remoteAddress: @NotNull InetAddress? = null

}
```

> **MẸO (TIP):** Bạn cũng có thể kích hoạt validation bằng cách đánh annotation `@Validated` lên phương thức `@Bean` tạo ra configuration properties.

Để đảm bảo validation luôn được kích hoạt cho các property lồng nhau, ngay cả khi không tìm thấy property nào, field tương ứng phải được đánh annotation `@Valid`. Ví dụ sau được xây dựng dựa trên ví dụ `MyProperties` trước đó:

**Java**

```java
import java.net.InetAddress;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("my.service")
@Validated
public class MyProperties {

    @NotNull
    private InetAddress remoteAddress;

    @Valid
    private final Security security = new Security();

    // getters/setters...

    public InetAddress getRemoteAddress() {
        return this.remoteAddress;
    }

    public void setRemoteAddress(InetAddress remoteAddress) {
        this.remoteAddress = remoteAddress;
    }

    public Security getSecurity() {
        return this.security;
    }

    public static class Security {

        @NotEmpty
        private String username;

        // getters/setters...

        public String getUsername() {
            return this.username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

    }

}
```

**Kotlin**

```kotlin
import jakarta.validation.Valid
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated
import java.net.InetAddress

@ConfigurationProperties("my.service")
@Validated
class MyProperties {

    var remoteAddress: @NotNull InetAddress? = null

    @Valid
    val security = Security()

    class Security {

        @NotEmpty
        var username: String? = null

    }

}
```

Bạn cũng có thể thêm một `Validator` tùy chỉnh của Spring bằng cách tạo một bean definition tên là `configurationPropertiesValidator`. Phương thức `@Bean` nên được khai báo là `static`. Validator cho configuration properties được tạo ra từ rất sớm trong vòng đời ứng dụng, và khai báo phương thức `@Bean` là static cho phép bean được tạo mà không cần khởi tạo class `@Configuration`. Làm như vậy sẽ tránh mọi vấn đề có thể gây ra bởi việc khởi tạo sớm.

> **MẸO (TIP):** Module `spring-boot-actuator` bao gồm một endpoint expose tất cả các bean `@ConfigurationProperties`. Hãy trỏ trình duyệt tới `/actuator/configprops` hoặc dùng endpoint JMX tương đương. Xem phần "[Các tính năng sẵn sàng cho production (Production ready features)](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#actuator.endpoints)" để biết chi tiết.

### @ConfigurationProperties và @Value (@ConfigurationProperties vs. @Value)

Annotation `@Value` là một tính năng cốt lõi của container, và nó không cung cấp các tính năng giống như configuration property an toàn kiểu. Bảng sau tóm tắt các tính năng được hỗ trợ bởi `@ConfigurationProperties` và `@Value`:

| Tính năng | `@ConfigurationProperties` | `@Value` |
|---|---|---|
| [Relaxed binding](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.external-config.typesafe-configuration-properties.relaxed-binding) | Có | Hạn chế (xem [ghi chú bên dưới](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.external-config.typesafe-configuration-properties.vs-value-annotation.note)) |
| [Hỗ trợ metadata](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#appendix.configuration-metadata) | Có | Không |
| Đánh giá `SpEL` | Không | Có |

> **LƯU Ý (NOTE):** Nếu bạn muốn dùng `@Value`, chúng tôi khuyên bạn nên tham chiếu tới tên property bằng dạng chuẩn (canonical form) của chúng (kebab-case chỉ dùng chữ thường). Điều này sẽ cho phép Spring Boot dùng cùng logic như khi thực hiện [relaxed binding](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.external-config.typesafe-configuration-properties.relaxed-binding) cho `@ConfigurationProperties`.
>
> Ví dụ, `@Value("${demo.item-price}")` sẽ nhận các dạng `demo.item-price` và `demo.itemPrice` từ file `application.properties`, cũng như `DEMO_ITEMPRICE` từ môi trường hệ thống. Nếu bạn dùng `@Value("${demo.itemPrice}")` thay thế, `demo.item-price` và `DEMO_ITEMPRICE` sẽ không được xem xét.

Nếu bạn định nghĩa một tập hợp key cấu hình cho các component của riêng mình, chúng tôi khuyên bạn nên nhóm chúng trong một POJO được đánh annotation `@ConfigurationProperties`. Làm như vậy sẽ cung cấp cho bạn một object có cấu trúc, an toàn kiểu mà bạn có thể inject vào các bean của riêng mình.

Các biểu thức `SpEL` từ [các file application property](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.external-config.files) không được xử lý tại thời điểm phân tích các file này và nạp vào environment. Tuy nhiên, có thể viết một biểu thức `SpEL` trong `@Value`. Nếu giá trị của một property từ file application property là một biểu thức `SpEL`, nó sẽ được đánh giá khi được sử dụng thông qua `@Value`.

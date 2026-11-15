# 6.8. Công cụ dành cho lập trình viên (Developer Tools)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#using.devtools>

Spring Boot bao gồm một bộ công cụ bổ sung có thể giúp trải nghiệm phát triển ứng dụng dễ chịu hơn một chút. Module `spring-boot-devtools` có thể được thêm vào bất kỳ project nào để cung cấp các tính năng bổ sung trong thời gian phát triển. Để thêm hỗ trợ devtools, hãy thêm dependency của module vào bản build, như trong các đoạn sau cho Maven và Gradle:

**Maven**

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-devtools</artifactId>
        <optional>true</optional>
    </dependency>
</dependencies>
```

**Gradle**

```gradle
dependencies {
    developmentOnly("org.springframework.boot:spring-boot-devtools")
}
```

> **THẬN TRỌNG (CAUTION):** Devtools có thể gây ra các vấn đề về classloading, đặc biệt trong các project nhiều module (multi-module). Phần [Chẩn đoán các vấn đề classloading](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#using.devtools.diagnosing-classloading-issues) giải thích cách chẩn đoán và giải quyết chúng.

> **LƯU Ý (NOTE):** Developer tools tự động bị vô hiệu hóa khi chạy một ứng dụng đã được đóng gói hoàn chỉnh. Nếu ứng dụng của bạn được khởi chạy bằng `java -jar` hoặc được khởi động từ một classloader đặc biệt, nó sẽ được coi là một "ứng dụng production". Bạn có thể kiểm soát hành vi này bằng system property `spring.devtools.restart.enabled`. Để bật devtools bất kể classloader nào được dùng để khởi chạy ứng dụng, hãy đặt system property `-Dspring.devtools.restart.enabled=true`. Không được làm điều này trong môi trường production, nơi việc chạy devtools là một rủi ro bảo mật. Để vô hiệu hóa devtools, hãy loại bỏ dependency hoặc đặt system property `-Dspring.devtools.restart.enabled=false`.

> **MẸO (TIP):** Đánh dấu dependency là optional trong Maven hoặc dùng cấu hình `developmentOnly` trong Gradle (như trên) sẽ ngăn devtools bị áp dụng bắc cầu (transitively) sang các module khác sử dụng project của bạn.

> **MẸO (TIP):** Các file nén đã được đóng gói lại (repackaged archive) mặc định không chứa devtools. Nếu bạn muốn dùng [một số tính năng devtools từ xa](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#using.devtools.remote-applications), bạn cần đưa nó vào. Khi dùng Maven plugin, hãy đặt property `excludeDevtools` thành `false`. Khi dùng Gradle plugin, hãy [cấu hình classpath của task để bao gồm cấu hình `developmentOnly`](https://docs.spring.io/spring-boot/docs/3.2.5/gradle-plugin/reference/htmlsingle/#packaging-executable-configuring-including-development-only-dependencies).

## 6.8.1. Chẩn đoán các vấn đề classloading (Diagnosing Classloading Issues)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#using.devtools.diagnosing-classloading-issues>

Như đã mô tả trong phần [Restart và Reload](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#using.devtools.restart.restart-vs-reload), chức năng restart được hiện thực bằng cách dùng hai classloader. Với hầu hết các ứng dụng, cách tiếp cận này hoạt động tốt. Tuy nhiên, đôi khi nó có thể gây ra các vấn đề classloading, đặc biệt trong các project nhiều module.

Để chẩn đoán xem các vấn đề classloading có thực sự do devtools và hai classloader của nó gây ra hay không, hãy [thử vô hiệu hóa restart](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#using.devtools.restart.disable). Nếu cách này giải quyết được vấn đề, hãy [tùy chỉnh restart classloader](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#using.devtools.restart.customizing-the-classload) để bao gồm toàn bộ project của bạn.

## 6.8.2. Giá trị mặc định của property (Property Defaults)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#using.devtools.property-defaults>

Một số thư viện được Spring Boot hỗ trợ sử dụng cache để cải thiện hiệu năng. Ví dụ, các [template engine](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#web.servlet.spring-mvc.template-engines) cache các template đã biên dịch để tránh phải phân tích (parse) lặp đi lặp lại các file template. Ngoài ra, Spring MVC có thể thêm các header HTTP caching vào response khi phục vụ tài nguyên tĩnh.

Mặc dù caching rất có lợi trên production, nó có thể phản tác dụng trong quá trình phát triển, khiến bạn không thấy được những thay đổi vừa thực hiện trong ứng dụng. Vì lý do này, spring-boot-devtools mặc định vô hiệu hóa các tùy chọn caching.

Các tùy chọn cache thường được cấu hình bằng các thiết lập trong file `application.properties`. Ví dụ, Thymeleaf cung cấp property `spring.thymeleaf.cache`. Thay vì phải đặt các property này thủ công, module `spring-boot-devtools` tự động áp dụng cấu hình hợp lý cho thời gian phát triển.

Bảng sau liệt kê tất cả các property được áp dụng:

| Tên | Giá trị mặc định |
|---|---|
| `server.error.include-binding-errors` | `always` |
| `server.error.include-message` | `always` |
| `server.error.include-stacktrace` | `always` |
| `server.servlet.jsp.init-parameters.development` | `true` |
| `server.servlet.session.persistent` | `true` |
| `spring.docker.compose.readiness.wait` | `only-if-started` |
| `spring.freemarker.cache` | `false` |
| `spring.graphql.graphiql.enabled` | `true` |
| `spring.groovy.template.cache` | `false` |
| `spring.h2.console.enabled` | `true` |
| `spring.mustache.servlet.cache` | `false` |
| `spring.mvc.log-resolved-exception` | `true` |
| `spring.reactor.netty.shutdown-quiet-period` | `0s` |
| `spring.template.provider.cache` | `false` |
| `spring.thymeleaf.cache` | `false` |
| `spring.web.resources.cache.period` | `0` |
| `spring.web.resources.chain.cache` | `false` |

> **LƯU Ý (NOTE):** Nếu bạn không muốn các giá trị mặc định của property được áp dụng, bạn có thể đặt `spring.devtools.add-properties` thành `false` trong `application.properties`.

Vì bạn cần nhiều thông tin hơn về các web request khi phát triển ứng dụng Spring MVC và Spring WebFlux, developer tools gợi ý bạn bật logging mức `DEBUG` cho nhóm logging `web`. Điều này sẽ cho bạn thông tin về request đến, handler nào đang xử lý nó, kết quả response và các chi tiết khác. Nếu bạn muốn log toàn bộ chi tiết request (bao gồm cả thông tin có thể nhạy cảm), bạn có thể bật các property cấu hình `spring.mvc.log-request-details` hoặc `spring.codec.log-request-details`.

## 6.8.3. Tự động khởi động lại (Automatic Restart)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#using.devtools.restart>

Các ứng dụng dùng `spring-boot-devtools` tự động khởi động lại mỗi khi các file trên classpath thay đổi. Đây có thể là một tính năng hữu ích khi làm việc trong IDE, vì nó cho vòng phản hồi (feedback loop) rất nhanh đối với các thay đổi code. Theo mặc định, mọi mục trên classpath trỏ tới một thư mục đều được theo dõi thay đổi. Lưu ý rằng một số tài nguyên nhất định, chẳng hạn như tài nguyên tĩnh (static asset) và view template, [không cần khởi động lại ứng dụng](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#using.devtools.restart.excluding-resources).

> **Kích hoạt restart (Triggering a restart)**
>
> Vì DevTools theo dõi các tài nguyên trên classpath, cách duy nhất để kích hoạt restart là cập nhật classpath. Dù bạn đang dùng IDE hay một trong các build plugin, các file đã sửa đổi phải được biên dịch lại để kích hoạt restart. Cách bạn làm cho classpath được cập nhật phụ thuộc vào công cụ bạn đang dùng:
>
> - Trong Eclipse, lưu một file đã sửa đổi sẽ khiến classpath được cập nhật và kích hoạt restart.
> - Trong IntelliJ IDEA, build project (`Build → Build Project`) cũng có tác dụng tương tự.
> - Nếu dùng build plugin, chạy `mvn compile` với Maven hoặc `gradle build` với Gradle sẽ kích hoạt restart.

> **LƯU Ý (NOTE):** Nếu bạn restart bằng Maven hoặc Gradle thông qua build plugin, bạn phải để `forking` ở trạng thái `enabled`. Nếu bạn vô hiệu hóa forking, classloader ứng dụng biệt lập mà devtools sử dụng sẽ không được tạo ra và việc restart sẽ không hoạt động đúng.

> **MẸO (TIP):** Tự động restart hoạt động rất tốt khi dùng cùng LiveReload. [Xem phần LiveReload](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#using.devtools.livereload) để biết chi tiết. Nếu bạn dùng JRebel, tự động restart bị vô hiệu hóa để ưu tiên cho việc nạp lại class động (dynamic class reloading). Các tính năng devtools khác (như LiveReload và ghi đè property) vẫn có thể được sử dụng.

> **LƯU Ý (NOTE):** DevTools dựa vào shutdown hook của application context để đóng nó trong quá trình restart. Nó sẽ không hoạt động đúng nếu bạn đã vô hiệu hóa shutdown hook (`SpringApplication.setRegisterShutdownHook(false)`).

> **LƯU Ý (NOTE):** DevTools cần tùy chỉnh `ResourceLoader` được `ApplicationContext` sử dụng. Nếu ứng dụng của bạn đã cung cấp sẵn một cái, nó sẽ được bọc lại (wrap). Việc ghi đè trực tiếp phương thức `getResource` trên `ApplicationContext` không được hỗ trợ.

> **THẬN TRỌNG (CAUTION):** Tự động restart không được hỗ trợ khi dùng AspectJ weaving.

> **Restart và Reload (Restart vs Reload)**
>
> Công nghệ restart do Spring Boot cung cấp hoạt động bằng cách dùng hai classloader. Các class không thay đổi (ví dụ, các class từ jar của bên thứ ba) được nạp vào một classloader *base*. Các class mà bạn đang tích cực phát triển được nạp vào một classloader *restart*. Khi ứng dụng được restart, classloader *restart* bị loại bỏ và một classloader mới được tạo ra. Cách tiếp cận này có nghĩa là việc restart ứng dụng thường nhanh hơn nhiều so với "khởi động nguội" ("cold start"), vì classloader *base* đã có sẵn và đã được nạp đầy đủ.
>
> Nếu bạn thấy việc restart không đủ nhanh cho ứng dụng của mình hoặc gặp các vấn đề classloading, bạn có thể cân nhắc các công nghệ reload như [JRebel](https://jrebel.com/software/jrebel/) của ZeroTurnaround. Chúng hoạt động bằng cách viết lại các class khi chúng được nạp để giúp chúng dễ reload hơn.

### Ghi log các thay đổi trong đánh giá điều kiện (Logging Changes in Condition Evaluation)

Theo mặc định, mỗi khi ứng dụng restart, một báo cáo cho thấy phần chênh lệch (delta) trong đánh giá điều kiện (condition evaluation) sẽ được ghi log. Báo cáo này cho thấy những thay đổi trong auto-configuration của ứng dụng khi bạn thực hiện các thay đổi như thêm hoặc xóa bean và đặt các property cấu hình.

Để tắt việc ghi log báo cáo này, hãy đặt property sau:

**Properties**

```properties
spring.devtools.restart.log-condition-evaluation-delta=false
```

**Yaml**

```yaml
spring:
  devtools:
    restart:
      log-condition-evaluation-delta: false
```

### Loại trừ tài nguyên (Excluding Resources)

Một số tài nguyên không nhất thiết phải kích hoạt restart khi chúng thay đổi. Ví dụ, các template Thymeleaf có thể được chỉnh sửa tại chỗ. Theo mặc định, thay đổi tài nguyên trong `/META-INF/maven`, `/META-INF/resources`, `/resources`, `/static`, `/public`, hoặc `/templates` không kích hoạt restart nhưng có kích hoạt [live reload](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#using.devtools.livereload). Nếu bạn muốn tùy chỉnh các mục loại trừ này, bạn có thể dùng property `spring.devtools.restart.exclude`. Ví dụ, để chỉ loại trừ `/static` và `/public`, bạn sẽ đặt property sau:

**Properties**

```properties
spring.devtools.restart.exclude=static/**,public/**
```

**Yaml**

```yaml
spring:
  devtools:
    restart:
      exclude: "static/**,public/**"
```

> **MẸO (TIP):** Nếu bạn muốn giữ các giá trị mặc định đó và *thêm* các mục loại trừ bổ sung, hãy dùng property `spring.devtools.restart.additional-exclude` thay thế.

### Theo dõi thêm đường dẫn (Watching Additional Paths)

Có thể bạn muốn ứng dụng được restart hoặc reload khi bạn thay đổi các file không nằm trên classpath. Để làm vậy, hãy dùng property `spring.devtools.restart.additional-paths` để cấu hình thêm các đường dẫn cần theo dõi thay đổi. Bạn có thể dùng property `spring.devtools.restart.exclude` [đã mô tả ở trên](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#using.devtools.restart.excluding-resources) để kiểm soát việc các thay đổi bên dưới các đường dẫn bổ sung sẽ kích hoạt restart đầy đủ hay [live reload](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#using.devtools.livereload).

### Vô hiệu hóa restart (Disabling Restart)

Nếu bạn không muốn dùng tính năng restart, bạn có thể vô hiệu hóa nó bằng property `spring.devtools.restart.enabled`. Trong hầu hết các trường hợp, bạn có thể đặt property này trong `application.properties` (làm vậy vẫn khởi tạo restart classloader, nhưng nó không theo dõi thay đổi file).

Nếu bạn cần vô hiệu hóa *hoàn toàn* hỗ trợ restart (ví dụ, vì nó không hoạt động với một thư viện cụ thể), bạn cần đặt `System` property `spring.devtools.restart.enabled` thành `false` trước khi gọi `SpringApplication.run(…)`, như trong ví dụ sau:

**Java**

```java
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MyApplication {

    public static void main(String[] args) {
        System.setProperty("spring.devtools.restart.enabled", "false");
        SpringApplication.run(MyApplication.class, args);
    }

}
```

**Kotlin**

```kotlin
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication

@SpringBootApplication
object MyApplication {

    @JvmStatic
    fun main(args: Array<String>) {
        System.setProperty("spring.devtools.restart.enabled", "false")
        SpringApplication.run(MyApplication::class.java, *args)
    }

}
```

### Sử dụng file kích hoạt (Using a Trigger File)

Nếu bạn làm việc với một IDE liên tục biên dịch các file thay đổi, có thể bạn muốn chỉ kích hoạt restart vào những thời điểm cụ thể. Để làm vậy, bạn có thể dùng một "file kích hoạt" ("trigger file"), là một file đặc biệt phải được sửa đổi khi bạn thực sự muốn kích hoạt việc kiểm tra restart.

> **LƯU Ý (NOTE):** Bất kỳ cập nhật nào lên file cũng sẽ kích hoạt việc kiểm tra, nhưng restart chỉ thực sự xảy ra nếu Devtools phát hiện có việc cần làm.

Để dùng file kích hoạt, hãy đặt property `spring.devtools.restart.trigger-file` thành tên (không bao gồm đường dẫn) của file kích hoạt. File kích hoạt phải xuất hiện ở đâu đó trên classpath của bạn.

Ví dụ, nếu bạn có một project với cấu trúc sau:

```
src
+- main
   +- resources
      +- .reloadtrigger
```

Thì property `trigger-file` của bạn sẽ là:

**Properties**

```properties
spring.devtools.restart.trigger-file=.reloadtrigger
```

**Yaml**

```yaml
spring:
  devtools:
    restart:
      trigger-file: ".reloadtrigger"
```

Giờ đây, restart sẽ chỉ xảy ra khi `src/main/resources/.reloadtrigger` được cập nhật.

> **MẸO (TIP):** Bạn có thể muốn đặt `spring.devtools.restart.trigger-file` như một [thiết lập toàn cục (global setting)](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#using.devtools.globalsettings), để tất cả các project của bạn hoạt động theo cùng một cách.

Một số IDE có tính năng giúp bạn không cần cập nhật file kích hoạt thủ công. [Spring Tools for Eclipse](https://spring.io/tools) và [IntelliJ IDEA (Ultimate Edition)](https://www.jetbrains.com/idea/) đều có hỗ trợ như vậy. Với Spring Tools, bạn có thể dùng nút "reload" từ console view (miễn là `trigger-file` của bạn có tên `.reloadtrigger`). Với IntelliJ IDEA, bạn có thể làm theo [hướng dẫn trong tài liệu của họ](https://www.jetbrains.com/help/idea/spring-boot.html#application-update-policies).

### Tùy chỉnh restart classloader (Customizing the Restart Classloader)

Như đã mô tả trước đó trong phần [Restart và Reload](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#using.devtools.restart.restart-vs-reload), chức năng restart được hiện thực bằng cách dùng hai classloader. Nếu điều này gây ra vấn đề, bạn có thể cần tùy chỉnh thứ gì được nạp bởi classloader nào.

Theo mặc định, mọi project đang mở trong IDE được nạp bằng classloader "restart", và mọi file `.jar` thông thường được nạp bằng classloader "base". Điều tương tự cũng đúng nếu bạn dùng `mvn spring-boot:run` hoặc `gradle bootRun`: project chứa `@SpringBootApplication` của bạn được nạp bằng classloader "restart", và mọi thứ khác bằng classloader "base".

Bạn có thể chỉ thị cho Spring Boot nạp các phần của project bằng một classloader khác bằng cách tạo file `META-INF/spring-devtools.properties`. File `spring-devtools.properties` có thể chứa các property có tiền tố `restart.exclude` và `restart.include`. Các phần tử `include` là những mục nên được kéo lên classloader "restart", và các phần tử `exclude` là những mục nên được đẩy xuống classloader "base". Giá trị của property là một mẫu regex được áp dụng lên classpath, như trong ví dụ sau:

**Properties**

```properties
restart.exclude.companycommonlibs=/mycorp-common-[\\w\\d-\\.]+\\.jar
restart.include.projectcommon=/mycorp-myproj-[\\w\\d-\\.]+\\.jar
```

**Yaml**

```yaml
restart:
  exclude:
    companycommonlibs: "/mycorp-common-[\\w\\d-\\.]+\\.jar"
  include:
    projectcommon: "/mycorp-myproj-[\\w\\d-\\.]+\\.jar"
```

> **LƯU Ý (NOTE):** Tất cả các key của property phải là duy nhất. Miễn là một property bắt đầu bằng `restart.include.` hoặc `restart.exclude.` thì nó sẽ được xem xét.

> **MẸO (TIP):** Tất cả các file `META-INF/spring-devtools.properties` trên classpath đều được nạp. Bạn có thể đóng gói các file này bên trong project của mình, hoặc trong các thư viện mà project sử dụng.

### Các hạn chế đã biết (Known Limitations)

Chức năng restart không hoạt động tốt với các object được deserialize bằng `ObjectInputStream` tiêu chuẩn. Nếu bạn cần deserialize dữ liệu, bạn có thể cần dùng `ConfigurableObjectInputStream` của Spring kết hợp với `Thread.currentThread().getContextClassLoader()`.

Đáng tiếc là một số thư viện bên thứ ba deserialize mà không xem xét context classloader. Nếu bạn gặp vấn đề như vậy, bạn cần yêu cầu tác giả gốc sửa lỗi.

## 6.8.4. LiveReload

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#using.devtools.livereload>

Module `spring-boot-devtools` bao gồm một LiveReload server nhúng, có thể dùng để kích hoạt việc làm mới (refresh) trình duyệt khi một tài nguyên thay đổi. Các extension trình duyệt LiveReload có sẵn miễn phí cho Chrome, Firefox và Safari. Bạn có thể tìm các extension này bằng cách tìm 'LiveReload' trong marketplace hoặc store của trình duyệt bạn chọn.

Nếu bạn không muốn khởi động LiveReload server khi ứng dụng chạy, bạn có thể đặt property `spring.devtools.livereload.enabled` thành `false`.

> **LƯU Ý (NOTE):** Bạn chỉ có thể chạy một LiveReload server tại một thời điểm. Trước khi khởi động ứng dụng, hãy đảm bảo không có LiveReload server nào khác đang chạy. Nếu bạn khởi động nhiều ứng dụng từ IDE, chỉ ứng dụng đầu tiên có hỗ trợ LiveReload.

> **CẢNH BÁO (WARNING):** Để kích hoạt LiveReload khi một file thay đổi, [Tự động restart (Automatic Restart)](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#using.devtools.restart) phải được bật.

## 6.8.5. Thiết lập toàn cục (Global Settings)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#using.devtools.globalsettings>

Bạn có thể cấu hình các thiết lập devtools toàn cục bằng cách thêm bất kỳ file nào sau đây vào thư mục `$HOME/.config/spring-boot`:

1. `spring-boot-devtools.properties`
2. `spring-boot-devtools.yaml`
3. `spring-boot-devtools.yml`

Mọi property được thêm vào các file này sẽ áp dụng cho *tất cả* các ứng dụng Spring Boot trên máy của bạn có dùng devtools. Ví dụ, để cấu hình restart luôn dùng một [file kích hoạt](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#using.devtools.restart.triggerfile), bạn sẽ thêm property sau vào file `spring-boot-devtools` của mình:

**Properties**

```properties
spring.devtools.restart.trigger-file=.reloadtrigger
```

**Yaml**

```yaml
spring:
  devtools:
    restart:
      trigger-file: ".reloadtrigger"
```

Theo mặc định, `$HOME` là thư mục home của người dùng. Để tùy chỉnh vị trí này, hãy đặt biến môi trường `SPRING_DEVTOOLS_HOME` hoặc system property `spring.devtools.home`.

> **LƯU Ý (NOTE):** Nếu không tìm thấy các file cấu hình devtools trong `$HOME/.config/spring-boot`, thư mục gốc của `$HOME` sẽ được tìm kiếm xem có file `.spring-boot-devtools.properties` hay không. Điều này cho phép bạn chia sẻ cấu hình devtools toàn cục với các ứng dụng đang dùng phiên bản Spring Boot cũ hơn, vốn không hỗ trợ vị trí `$HOME/.config/spring-boot`.

> **LƯU Ý (NOTE):** Profile không được hỗ trợ trong các file properties/yaml của devtools.
>
> Bất kỳ profile nào được kích hoạt trong `.spring-boot-devtools.properties` sẽ không ảnh hưởng đến việc nạp các [file cấu hình theo profile (profile-specific configuration files)](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.external-config.files.profile-specific). Các tên file theo profile (có dạng `spring-boot-devtools-<profile>.properties`) và các document `spring.config.activate.on-profile` trong cả file YAML và Properties đều không được hỗ trợ.

### Cấu hình File System Watcher (Configuring File System Watcher)

[FileSystemWatcher](https://github.com/spring-projects/spring-boot/tree/v3.2.5/spring-boot-project/spring-boot-devtools/src/main/java/org/springframework/boot/devtools/filewatch/FileSystemWatcher.java) hoạt động bằng cách thăm dò (poll) các thay đổi của class theo một khoảng thời gian nhất định, rồi chờ một khoảng thời gian yên lặng (quiet period) định trước để đảm bảo không còn thay đổi nào nữa. Vì Spring Boot hoàn toàn dựa vào IDE để biên dịch và sao chép file vào vị trí mà Spring Boot có thể đọc được, bạn có thể thấy đôi khi một số thay đổi không được phản ánh khi devtools restart ứng dụng. Nếu bạn liên tục gặp những vấn đề như vậy, hãy thử tăng các tham số `spring.devtools.restart.poll-interval` và `spring.devtools.restart.quiet-period` lên giá trị phù hợp với môi trường phát triển của bạn:

**Properties**

```properties
spring.devtools.restart.poll-interval=2s
spring.devtools.restart.quiet-period=1s
```

**Yaml**

```yaml
spring:
  devtools:
    restart:
      poll-interval: "2s"
      quiet-period: "1s"
```

Giờ đây, các thư mục classpath được theo dõi sẽ được thăm dò thay đổi mỗi 2 giây, và một khoảng yên lặng 1 giây được duy trì để đảm bảo không có thêm thay đổi class nào.

## 6.8.6. Ứng dụng từ xa (Remote Applications)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#using.devtools.remote-applications>

Developer tools của Spring Boot không chỉ giới hạn ở việc phát triển cục bộ. Bạn cũng có thể dùng một số tính năng khi chạy ứng dụng từ xa. Hỗ trợ từ xa phải được chủ động bật (opt-in), vì việc bật nó có thể là một rủi ro bảo mật. Nó chỉ nên được bật khi chạy trên một mạng tin cậy hoặc khi được bảo mật bằng SSL. Nếu bạn không có lựa chọn nào trong hai lựa chọn này, bạn không nên dùng hỗ trợ từ xa của DevTools. Bạn tuyệt đối không bao giờ nên bật hỗ trợ này trên môi trường triển khai production.

Để bật nó, bạn cần đảm bảo rằng `devtools` được đưa vào file nén đã đóng gói lại, như trong đoạn sau:

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-maven-plugin</artifactId>
            <configuration>
                <excludeDevtools>false</excludeDevtools>
            </configuration>
        </plugin>
    </plugins>
</build>
```

Sau đó, bạn cần đặt property `spring.devtools.remote.secret`. Giống như bất kỳ mật khẩu hay secret quan trọng nào, giá trị này phải là duy nhất và đủ mạnh để không thể bị đoán ra hoặc bị tấn công brute-force.

Hỗ trợ devtools từ xa được cung cấp thành hai phần: một endpoint phía server chấp nhận kết nối, và một ứng dụng client mà bạn chạy trong IDE. Thành phần server được tự động bật khi property `spring.devtools.remote.secret` được đặt. Thành phần client phải được khởi chạy thủ công.

> **LƯU Ý (NOTE):** Devtools từ xa không được hỗ trợ cho các ứng dụng Spring WebFlux.

### Chạy ứng dụng client từ xa (Running the Remote Client Application)

Ứng dụng client từ xa được thiết kế để chạy từ bên trong IDE của bạn. Bạn cần chạy `org.springframework.boot.devtools.RemoteSpringApplication` với cùng classpath như project từ xa mà bạn kết nối tới. Tham số bắt buộc duy nhất của ứng dụng là URL từ xa mà nó kết nối tới.

Ví dụ, nếu bạn đang dùng Eclipse hoặc Spring Tools và có một project tên `my-app` đã triển khai lên Cloud Foundry, bạn sẽ làm như sau:

- Chọn `Run Configurations…` từ menu `Run`.
- Tạo một "launch configuration" `Java Application` mới.
- Duyệt (browse) tới project `my-app`.
- Dùng `org.springframework.boot.devtools.RemoteSpringApplication` làm main class.
- Thêm `https://myapp.cfapps.io` vào `Program arguments` (hoặc URL từ xa của bạn là gì thì điền vào đó).

Một remote client đang chạy có thể trông giống như đoạn sau:

```
.   ____          _                                              __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _          ___               _      \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` |        | _ \___ _ __  ___| |_ ___ \ \ \ \
 \\/  ___)| |_)| | | | | || (_| []::::::[]   / -_) '  \/ _ \  _/ -_) ) ) ) )
  '  |____| .__|_| |_|_| |_\__, |        |_|_\___|_|_|_\___/\__\___|/ / / /
 =========|_|==============|___/===================================/_/_/_/
 :: Spring Boot Remote ::  (v3.2.5)

2024-04-18T17:04:07.711Z  INFO 13353 --- [           main] o.s.b.devtools.RemoteSpringApplication   : Starting RemoteSpringApplication v3.2.5 using Java 17.0.11 with PID 13353 (/Users/myuser/.m2/repository/org/springframework/boot/spring-boot-devtools/3.2.5/spring-boot-devtools-3.2.5.jar started by myuser in /opt/apps/)
2024-04-18T17:04:07.721Z  INFO 13353 --- [           main] o.s.b.devtools.RemoteSpringApplication   : No active profile set, falling back to 1 default profile: "default"
2024-04-18T17:04:08.597Z  INFO 13353 --- [           main] o.s.b.d.a.OptionalLiveReloadServer       : LiveReload server is running on port 35729
2024-04-18T17:04:08.648Z  INFO 13353 --- [           main] o.s.b.devtools.RemoteSpringApplication   : Started RemoteSpringApplication in 2.936 seconds (process running for 4.812)
```

> **LƯU Ý (NOTE):** Vì remote client dùng cùng classpath với ứng dụng thực, nó có thể đọc trực tiếp các application property. Đây là cách property `spring.devtools.remote.secret` được đọc và gửi tới server để xác thực.

> **MẸO (TIP):** Luôn nên dùng `https://` làm giao thức kết nối, để lưu lượng được mã hóa và mật khẩu không thể bị chặn bắt.

> **MẸO (TIP):** Nếu bạn cần dùng proxy để truy cập ứng dụng từ xa, hãy cấu hình các property `spring.devtools.remote.proxy.host` và `spring.devtools.remote.proxy.port`.

### Cập nhật từ xa (Remote Update)

Remote client theo dõi các thay đổi trên classpath của ứng dụng theo cùng cách như [restart cục bộ](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#using.devtools.restart). Mọi tài nguyên được cập nhật sẽ được đẩy lên ứng dụng từ xa và (*nếu cần*) kích hoạt restart. Điều này có thể hữu ích nếu bạn đang phát triển lặp lại một tính năng dùng một dịch vụ cloud mà bạn không có ở máy cục bộ. Nhìn chung, việc cập nhật và restart từ xa nhanh hơn nhiều so với một chu trình build lại và triển khai đầy đủ.

Trên một môi trường phát triển chậm hơn, có thể xảy ra trường hợp khoảng yên lặng (quiet period) không đủ, và các thay đổi của class có thể bị chia thành nhiều đợt (batch). Server được restart sau khi đợt thay đổi class đầu tiên được tải lên. Đợt tiếp theo không thể được gửi tới ứng dụng, vì server đang restart.

Điều này thường biểu hiện bằng một cảnh báo trong log của `RemoteSpringApplication` về việc tải lên một số class thất bại, và sau đó là một lần thử lại. Nhưng nó cũng có thể dẫn đến sự không nhất quán của code ứng dụng và việc restart thất bại sau khi đợt thay đổi đầu tiên được tải lên. Nếu bạn liên tục gặp những vấn đề như vậy, hãy thử tăng các tham số `spring.devtools.restart.poll-interval` và `spring.devtools.restart.quiet-period` lên giá trị phù hợp với môi trường phát triển của bạn. Xem phần [Cấu hình File System Watcher](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#using.devtools.globalsettings.configuring-file-system-watcher) để cấu hình các property này.

> **LƯU Ý (NOTE):** File chỉ được theo dõi khi remote client đang chạy. Nếu bạn thay đổi một file trước khi khởi động remote client, nó sẽ không được đẩy lên server từ xa.

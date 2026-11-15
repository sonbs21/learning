# 8.1.3. Hỗ trợ servlet container nhúng (Embedded Servlet Container Support)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#web.servlet.embedded-container>

Với ứng dụng servlet, Spring Boot bao gồm hỗ trợ cho các server [Tomcat](https://tomcat.apache.org/), [Jetty](https://www.eclipse.org/jetty/), và [Undertow](https://github.com/undertow-io/undertow) nhúng. Hầu hết các lập trình viên dùng "Starter" phù hợp để có được một instance đã được cấu hình đầy đủ. Theo mặc định, server nhúng lắng nghe HTTP request trên cổng `8080`.

## Servlet, Filter và Listener (Servlets, Filters, and Listeners)

Khi dùng một servlet container nhúng, bạn có thể đăng ký servlet, filter, và tất cả các listener (chẳng hạn như `HttpSessionListener`) từ đặc tả servlet, bằng cách dùng Spring bean hoặc bằng cách quét các servlet component.

### Đăng ký Servlet, Filter và Listener dưới dạng Spring Bean (Registering Servlets, Filters, and Listeners as Spring Beans)

Bất kỳ instance `Servlet`, `Filter`, hoặc servlet `*Listener` nào là Spring bean đều được đăng ký với container nhúng. Điều này có thể đặc biệt tiện lợi nếu bạn muốn tham chiếu tới một giá trị từ `application.properties` trong quá trình cấu hình.

Theo mặc định, nếu context chỉ chứa một Servlet duy nhất, nó được ánh xạ tới `/`. Trong trường hợp có nhiều servlet bean, tên bean được dùng làm tiền tố đường dẫn. Filter được ánh xạ tới `/*`.

Nếu việc ánh xạ dựa trên quy ước không đủ linh hoạt, bạn có thể dùng các class `ServletRegistrationBean`, `FilterRegistrationBean`, và `ServletListenerRegistrationBean` để kiểm soát hoàn toàn.

Thường thì để các filter bean không có thứ tự là an toàn. Nếu cần một thứ tự cụ thể, bạn nên đánh annotation `@Order` lên `Filter` hoặc làm cho nó hiện thực `Ordered`. Bạn không thể cấu hình thứ tự của một `Filter` bằng cách đánh annotation `@Order` lên phương thức bean của nó. Nếu bạn không thể thay đổi class `Filter` để thêm `@Order` hoặc hiện thực `Ordered`, bạn phải định nghĩa một `FilterRegistrationBean` cho `Filter` và đặt thứ tự của registration bean bằng phương thức `setOrder(int)`. Tránh cấu hình một filter đọc request body ở `Ordered.HIGHEST_PRECEDENCE`, vì nó có thể đi ngược lại cấu hình mã hóa ký tự (character encoding) của ứng dụng. Nếu một servlet filter bọc (wrap) request, nó nên được cấu hình với một thứ tự nhỏ hơn hoặc bằng `OrderedFilter.REQUEST_WRAPPER_FILTER_MAX_ORDER`.

> **MẸO (TIP):** Để xem thứ tự của mọi `Filter` trong ứng dụng, hãy bật logging mức debug cho [nhóm logging](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.logging.log-groups) `web` (`logging.level.web=debug`). Chi tiết của các filter đã đăng ký, bao gồm thứ tự và mẫu URL của chúng, sau đó sẽ được ghi log khi khởi động.

> **CẢNH BÁO (WARNING):** Hãy cẩn thận khi đăng ký các bean `Filter` vì chúng được khởi tạo từ rất sớm trong vòng đời ứng dụng. Nếu bạn cần đăng ký một `Filter` tương tác với các bean khác, hãy cân nhắc dùng [`DelegatingFilterProxyRegistrationBean`](https://docs.spring.io/spring-boot/docs/3.2.5/api/org/springframework/boot/web/servlet/DelegatingFilterProxyRegistrationBean.html) thay thế.

## Khởi tạo servlet context (Servlet Context Initialization)

Các servlet container nhúng không trực tiếp thực thi interface `jakarta.servlet.ServletContainerInitializer` hay interface `org.springframework.web.WebApplicationInitializer` của Spring. Đây là một quyết định thiết kế có chủ đích, nhằm giảm rủi ro các thư viện bên thứ ba được thiết kế để chạy bên trong war có thể làm hỏng ứng dụng Spring Boot.

Nếu bạn cần thực hiện khởi tạo servlet context trong một ứng dụng Spring Boot, bạn nên đăng ký một bean hiện thực interface `org.springframework.boot.web.servlet.ServletContextInitializer`. Phương thức `onStartup` duy nhất cung cấp quyền truy cập tới `ServletContext` và, nếu cần, có thể dễ dàng được dùng như một adapter cho một `WebApplicationInitializer` hiện có.

### Quét Servlet, Filter và Listener (Scanning for Servlets, Filters, and listeners)

Khi dùng một container nhúng, việc tự động đăng ký các class được đánh annotation `@WebServlet`, `@WebFilter`, và `@WebListener` có thể được bật bằng `@ServletComponentScan`.

> **MẸO (TIP):** `@ServletComponentScan` không có tác dụng trong một container độc lập (standalone), nơi các cơ chế phát hiện có sẵn của container được dùng thay thế.

## ServletWebServerApplicationContext (The ServletWebServerApplicationContext)

Bên dưới, Spring Boot dùng một loại `ApplicationContext` khác để hỗ trợ servlet container nhúng. `ServletWebServerApplicationContext` là một loại `WebApplicationContext` đặc biệt, tự khởi động (bootstrap) bằng cách tìm kiếm một bean `ServletWebServerFactory` duy nhất. Thông thường, một `TomcatServletWebServerFactory`, `JettyServletWebServerFactory`, hoặc `UndertowServletWebServerFactory` đã được tự động cấu hình.

> **LƯU Ý (NOTE):** Bạn thường không cần biết về các class implementation này. Hầu hết các ứng dụng đều được tự động cấu hình, và `ApplicationContext` cùng `ServletWebServerFactory` phù hợp được tạo ra thay cho bạn.

Trong thiết lập container nhúng, `ServletContext` được đặt như một phần của quá trình khởi động server, diễn ra trong quá trình khởi tạo application context. Vì điều này, các bean trong `ApplicationContext` không thể được khởi tạo một cách đáng tin cậy với `ServletContext`. Một cách để giải quyết điều này là inject `ApplicationContext` như một dependency của bean và chỉ truy cập `ServletContext` khi cần. Một cách khác là dùng một callback sau khi server đã khởi động. Điều này có thể được thực hiện bằng một `ApplicationListener` lắng nghe `ApplicationStartedEvent` như sau:

```java
import jakarta.servlet.ServletContext;

import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.web.context.WebApplicationContext;

public class MyDemoBean implements ApplicationListener<ApplicationStartedEvent> {

    private ServletContext servletContext;

    @Override
    public void onApplicationEvent(ApplicationStartedEvent event) {
        ApplicationContext applicationContext = event.getApplicationContext();
        this.servletContext = ((WebApplicationContext) applicationContext).getServletContext();
    }

}
```

## Tùy chỉnh servlet container nhúng (Customizing Embedded Servlet Containers)

Các thiết lập servlet container phổ biến có thể được cấu hình bằng các property của `Environment` Spring. Thông thường, bạn sẽ định nghĩa các property trong file `application.properties` hoặc `application.yaml`.

Các thiết lập server phổ biến bao gồm:

- Thiết lập mạng: Cổng lắng nghe HTTP request đến (`server.port`), địa chỉ interface để bind (`server.address`), v.v.
- Thiết lập session: Session có được lưu trữ bền vững hay không (`server.servlet.session.persistent`), timeout của session (`server.servlet.session.timeout`), vị trí dữ liệu session (`server.servlet.session.store-dir`), và cấu hình session-cookie (`server.servlet.session.cookie.*`).
- Quản lý lỗi: Vị trí của trang lỗi (`server.error.path`), v.v.
- [SSL](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.webserver.configure-ssl)
- [Nén HTTP (HTTP compression)](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.webserver.enable-response-compression)

Spring Boot cố gắng hết mức có thể để expose các thiết lập phổ biến, nhưng điều này không phải lúc nào cũng khả thi. Với những trường hợp đó, các namespace chuyên biệt cung cấp các tùy chỉnh đặc thù cho từng server (xem `server.tomcat` và `server.undertow`). Ví dụ, [access log](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.webserver.configure-access-logs) có thể được cấu hình với các tính năng cụ thể của servlet container nhúng.

> **MẸO (TIP):** Xem class [`ServerProperties`](https://github.com/spring-projects/spring-boot/tree/v3.2.5/spring-boot-project/spring-boot-autoconfigure/src/main/java/org/springframework/boot/autoconfigure/web/ServerProperties.java) để có danh sách đầy đủ.

### Cookie SameSite (SameSite Cookies)

Thuộc tính cookie `SameSite` có thể được trình duyệt web dùng để kiểm soát việc cookie có được gửi trong các request cross-site hay không và gửi như thế nào. Thuộc tính này đặc biệt liên quan đến các trình duyệt web hiện đại, vốn đã bắt đầu thay đổi giá trị mặc định được dùng khi thuộc tính bị thiếu.

Nếu bạn muốn thay đổi thuộc tính `SameSite` của session cookie, bạn có thể dùng property `server.servlet.session.cookie.same-site`. Property này được hỗ trợ bởi các server Tomcat, Jetty và Undertow được tự động cấu hình. Nó cũng được dùng để cấu hình các bean `SessionRepository` dựa trên servlet của Spring Session.

Ví dụ, nếu bạn muốn session cookie có thuộc tính `SameSite` là `None`, bạn có thể thêm nội dung sau vào file `application.properties` hoặc `application.yaml`:

**Properties**

```properties
server.servlet.session.cookie.same-site=none
```

**Yaml**

```yaml
server:
  servlet:
    session:
      cookie:
        same-site: "none"
```

Nếu bạn muốn thay đổi thuộc tính `SameSite` trên các cookie khác được thêm vào `HttpServletResponse`, bạn có thể dùng một `CookieSameSiteSupplier`. `CookieSameSiteSupplier` nhận một `Cookie` và có thể trả về một giá trị `SameSite`, hoặc `null`.

Có một số phương thức factory và filter tiện lợi mà bạn có thể dùng để nhanh chóng khớp các cookie cụ thể. Ví dụ, thêm bean sau sẽ tự động áp dụng `SameSite` là `Lax` cho tất cả các cookie có tên khớp với biểu thức chính quy `myapp.*`.

**Java**

```java
import org.springframework.boot.web.servlet.server.CookieSameSiteSupplier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class MySameSiteConfiguration {

    @Bean
    public CookieSameSiteSupplier applicationCookieSameSiteSupplier() {
        return CookieSameSiteSupplier.ofLax().whenHasNameMatching("myapp.*");
    }

}
```

**Kotlin**

```kotlin
import org.springframework.boot.web.servlet.server.CookieSameSiteSupplier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class MySameSiteConfiguration {

    @Bean
    fun applicationCookieSameSiteSupplier(): CookieSameSiteSupplier {
        return CookieSameSiteSupplier.ofLax().whenHasNameMatching("myapp.*")
    }

}
```

### Mã hóa ký tự (Character Encoding)

Hành vi mã hóa ký tự (character encoding) của servlet container nhúng khi xử lý request và response có thể được cấu hình bằng các configuration property `server.servlet.encoding.*`.

Khi header `Accept-Language` của một request chỉ ra locale cho request đó, nó sẽ được servlet container tự động ánh xạ sang một charset. Mỗi container cung cấp các ánh xạ mặc định từ locale sang charset và bạn nên kiểm tra rằng chúng đáp ứng nhu cầu của ứng dụng. Nếu không, hãy dùng configuration property `server.servlet.encoding.mapping` để tùy chỉnh các ánh xạ, như trong ví dụ sau:

**Properties**

```properties
server.servlet.encoding.mapping.ko=UTF-8
```

**Yaml**

```yaml
server:
  servlet:
    encoding:
      mapping:
        ko: "UTF-8"
```

Trong ví dụ trên, locale `ko` (tiếng Hàn) đã được ánh xạ sang `UTF-8`. Điều này tương đương với một entry `<locale-encoding-mapping-list>` trong file `web.xml` của một triển khai war truyền thống.

### Tùy chỉnh bằng code (Programmatic Customization)

Nếu bạn cần cấu hình servlet container nhúng bằng code, bạn có thể đăng ký một Spring bean hiện thực interface `WebServerFactoryCustomizer`. `WebServerFactoryCustomizer` cung cấp quyền truy cập tới `ConfigurableServletWebServerFactory`, bao gồm rất nhiều phương thức setter để tùy chỉnh. Ví dụ sau cho thấy việc đặt cổng bằng code:

**Java**

```java
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory;
import org.springframework.stereotype.Component;

@Component
public class MyWebServerFactoryCustomizer implements WebServerFactoryCustomizer<ConfigurableServletWebServerFactory> {

    @Override
    public void customize(ConfigurableServletWebServerFactory server) {
        server.setPort(9000);
    }

}
```

**Kotlin**

```kotlin
import org.springframework.boot.web.server.WebServerFactoryCustomizer
import org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory
import org.springframework.stereotype.Component

@Component
class MyWebServerFactoryCustomizer : WebServerFactoryCustomizer<ConfigurableServletWebServerFactory> {

    override fun customize(server: ConfigurableServletWebServerFactory) {
        server.setPort(9000)
    }

}
```

`TomcatServletWebServerFactory`, `JettyServletWebServerFactory` và `UndertowServletWebServerFactory` là các biến thể chuyên biệt của `ConfigurableServletWebServerFactory`, có thêm các phương thức setter tùy chỉnh lần lượt cho Tomcat, Jetty và Undertow. Ví dụ sau cho thấy cách tùy chỉnh `TomcatServletWebServerFactory`, cung cấp quyền truy cập tới các tùy chọn cấu hình đặc thù của Tomcat:

**Java**

```java
import java.time.Duration;

import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.stereotype.Component;

@Component
public class MyTomcatWebServerFactoryCustomizer implements WebServerFactoryCustomizer<TomcatServletWebServerFactory> {

    @Override
    public void customize(TomcatServletWebServerFactory server) {
        server.addConnectorCustomizers((connector) -> connector.setAsyncTimeout(Duration.ofSeconds(20).toMillis()));
    }

}
```

**Kotlin**

```kotlin
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory
import org.springframework.boot.web.server.WebServerFactoryCustomizer
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class MyTomcatWebServerFactoryCustomizer : WebServerFactoryCustomizer<TomcatServletWebServerFactory> {

    override fun customize(server: TomcatServletWebServerFactory) {
        server.addConnectorCustomizers({ connector -> connector.asyncTimeout = Duration.ofSeconds(20).toMillis() })
    }

}
```

### Tùy chỉnh trực tiếp ConfigurableServletWebServerFactory (Customizing ConfigurableServletWebServerFactory Directly)

Với các trường hợp sử dụng nâng cao hơn yêu cầu bạn kế thừa từ `ServletWebServerFactory`, bạn có thể tự expose một bean thuộc kiểu đó.

Các setter được cung cấp cho nhiều tùy chọn cấu hình. Một số phương thức protected đóng vai trò "hook" cũng được cung cấp nếu bạn cần làm điều gì đó đặc biệt hơn. Xem [tài liệu mã nguồn](https://docs.spring.io/spring-boot/docs/3.2.5/api/org/springframework/boot/web/servlet/server/ConfigurableServletWebServerFactory.html) để biết chi tiết.

> **LƯU Ý (NOTE):** Các customizer được tự động cấu hình vẫn được áp dụng lên factory tùy chỉnh của bạn, vì vậy hãy dùng tùy chọn đó một cách cẩn thận.

## Các hạn chế của JSP (JSP Limitations)

Khi chạy một ứng dụng Spring Boot dùng servlet container nhúng (và được đóng gói dưới dạng file nén thực thi), có một số hạn chế trong hỗ trợ JSP.

- Với Jetty và Tomcat, nó sẽ hoạt động nếu bạn dùng đóng gói war. Một war thực thi sẽ hoạt động khi được khởi chạy bằng `java -jar`, và cũng có thể triển khai lên bất kỳ container tiêu chuẩn nào. JSP không được hỗ trợ khi dùng jar thực thi.
- Undertow không hỗ trợ JSP.
- Tạo một trang `error.jsp` tùy chỉnh không ghi đè view mặc định cho [xử lý lỗi](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#web.servlet.spring-mvc.error-handling). Thay vào đó nên dùng [các trang lỗi tùy chỉnh](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#web.servlet.spring-mvc.error-handling.error-pages).

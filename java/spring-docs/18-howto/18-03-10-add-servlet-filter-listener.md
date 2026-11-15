# 18.3.10. Thêm Servlet, Filter, hoặc Listener vào ứng dụng (Add a Servlet, Filter, or Listener to an Application)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.webserver.add-servlet-filter-listener>

Trong một ứng dụng servlet stack, tức là với `spring-boot-starter-web`, có hai cách để thêm `Servlet`, `Filter`, `ServletContextListener`, và các listener khác được Servlet API hỗ trợ vào ứng dụng:

- [Thêm Servlet, Filter, hoặc Listener bằng Spring Bean](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.webserver.add-servlet-filter-listener.spring-bean)
- [Thêm Servlet, Filter, và Listener bằng cách quét classpath](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.webserver.add-servlet-filter-listener.using-scanning)

## Thêm Servlet, Filter, hoặc Listener bằng Spring Bean (Add a Servlet, Filter, or Listener by Using a Spring Bean)

Để thêm một `Servlet`, `Filter`, hoặc servlet `*Listener` bằng một Spring bean, bạn phải cung cấp một định nghĩa `@Bean` cho nó. Làm vậy có thể rất hữu ích khi bạn muốn inject cấu hình hoặc dependency. Tuy nhiên, bạn phải hết sức cẩn thận để chúng không gây ra việc khởi tạo sớm (eager initialization) quá nhiều bean khác, vì chúng phải được cài đặt vào container từ rất sớm trong vòng đời ứng dụng. (Ví dụ, không nên để chúng phụ thuộc vào `DataSource` hoặc cấu hình JPA.) Bạn có thể khắc phục những hạn chế như vậy bằng cách khởi tạo các bean một cách lazy khi được dùng lần đầu thay vì lúc khởi tạo.

Trong trường hợp filter và servlet, bạn cũng có thể thêm các mapping và init parameter bằng cách thêm một `FilterRegistrationBean` hoặc một `ServletRegistrationBean` thay cho hoặc bổ sung cho component bên dưới.

> **LƯU Ý (NOTE):** Nếu không có `dispatcherType` nào được chỉ định trên một filter registration, `REQUEST` sẽ được dùng. Điều này phù hợp với dispatcher type mặc định của đặc tả servlet.

Giống như bất kỳ Spring bean nào khác, bạn có thể định nghĩa thứ tự của các bean servlet filter; hãy nhớ xem phần "[Đăng ký Servlet, Filter, và Listener dưới dạng Spring Bean (Registering Servlets, Filters, and Listeners as Spring Beans)](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#web.servlet.embedded-container.servlets-filters-listeners.beans)".

### Vô hiệu hóa việc đăng ký Servlet hoặc Filter (Disable Registration of a Servlet or Filter)

Như [đã mô tả ở trên](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.webserver.add-servlet-filter-listener.spring-bean), mọi bean `Servlet` hoặc `Filter` đều được đăng ký tự động với servlet container. Để vô hiệu hóa việc đăng ký một bean `Filter` hoặc `Servlet` cụ thể, hãy tạo một registration bean cho nó và đánh dấu nó là disabled, như trong ví dụ sau:

**Java**

```java
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class MyFilterConfiguration {

    @Bean
    public FilterRegistrationBean<MyFilter> registration(MyFilter filter) {
        FilterRegistrationBean<MyFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

}
```

**Kotlin**

```kotlin
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class MyFilterConfiguration {

    @Bean
    fun registration(filter: MyFilter): FilterRegistrationBean<MyFilter> {
        val registration = FilterRegistrationBean(filter)
        registration.isEnabled = false
        return registration
    }

}
```

## Thêm Servlet, Filter, và Listener bằng cách quét classpath (Add Servlets, Filters, and Listeners by Using Classpath Scanning)

Các class được annotate với `@WebServlet`, `@WebFilter`, và `@WebListener` có thể được đăng ký tự động với một embedded servlet container bằng cách annotate một class `@Configuration` với `@ServletComponentScan` và chỉ định (các) package chứa các component mà bạn muốn đăng ký. Theo mặc định, `@ServletComponentScan` quét từ package của class được annotate.

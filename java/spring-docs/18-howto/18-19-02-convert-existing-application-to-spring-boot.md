# 18.19.2. Chuyển đổi một ứng dụng hiện có sang Spring Boot (Convert an Existing Application to Spring Boot)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.traditional-deployment.convert-existing-application>

Để chuyển đổi một ứng dụng Spring không phải web hiện có sang một ứng dụng Spring Boot, hãy thay thế code tạo `ApplicationContext` bằng các lời gọi tới `SpringApplication` hoặc `SpringApplicationBuilder`. Các ứng dụng web Spring MVC nhìn chung phù hợp với việc trước tiên tạo một ứng dụng war có thể triển khai và sau đó chuyển đổi nó thành một war hoặc jar thực thi. Xem [Hướng dẫn Getting Started về chuyển đổi jar sang war](https://spring.io/guides/gs/convert-jar-to-war/).

Để tạo một war có thể triển khai bằng cách kế thừa `SpringBootServletInitializer` (ví dụ, trong một class tên là `Application`) và thêm annotation `@SpringBootApplication` của Spring Boot, hãy dùng code tương tự như trong ví dụ sau:

**Java**

```java
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

@SpringBootApplication
public class MyApplication extends SpringBootServletInitializer {

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
        // Customize the application or call application.sources(...) to add sources
        // Since our example is itself a @Configuration class (through
        // @SpringBootApplication)
        // we actually do not need to override this method.
        return application;
    }


}
```

**Kotlin**

```kotlin
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.runApplication
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer

@SpringBootApplication
class MyApplication : SpringBootServletInitializer() {

    override fun configure(application: SpringApplicationBuilder): SpringApplicationBuilder {
        // Customize the application or call application.sources(...) to add sources
        // Since our example is itself a @Configuration class (through @SpringBootApplication)
        // we actually do not need to override this method.
        return application
    }

}
```

Hãy nhớ rằng, bất cứ thứ gì bạn đặt vào `sources` chỉ đơn thuần là một `ApplicationContext` của Spring. Thông thường, bất cứ thứ gì đã hoạt động thì cũng sẽ hoạt động ở đây. Có thể có một số bean bạn có thể loại bỏ sau này và để Spring Boot cung cấp các mặc định của riêng nó cho chúng, nhưng bạn sẽ có thể làm cho mọi thứ hoạt động trước khi cần làm điều đó.

Các tài nguyên tĩnh có thể được chuyển tới `/public` (hoặc `/static` hoặc `/resources` hoặc `/META-INF/resources`) ở gốc classpath. Điều tương tự cũng áp dụng cho `messages.properties` (mà Spring Boot tự động phát hiện ở gốc classpath).

Việc dùng `DispatcherServlet` của Spring và Spring Security theo cách thông thường sẽ không cần thay đổi gì thêm. Nếu bạn có các tính năng khác trong ứng dụng (ví dụ, dùng các servlet hoặc filter khác), bạn có thể cần thêm một số cấu hình vào context `Application` của mình, bằng cách thay thế các phần tử đó từ `web.xml`, như sau:

- Một `@Bean` kiểu `Servlet` hoặc `ServletRegistrationBean` sẽ cài đặt bean đó vào container như thể nó là một `<servlet/>` và `<servlet-mapping/>` trong `web.xml`.
- Một `@Bean` kiểu `Filter` hoặc `FilterRegistrationBean` hoạt động tương tự (như một `<filter/>` và `<filter-mapping/>`).
- Một `ApplicationContext` trong một file XML có thể được thêm vào thông qua một `@ImportResource` trong `Application` của bạn. Ngoài ra, các trường hợp đã dùng nhiều cấu hình annotation có thể được tái tạo trong vài dòng dưới dạng các định nghĩa `@Bean`.

Một khi file war đã hoạt động, bạn có thể làm cho nó thực thi được bằng cách thêm một phương thức `main` vào `Application`, như trong ví dụ sau:

**Java**

```java
public static void main(String[] args) {
    SpringApplication.run(MyApplication.class, args);
}
```

**Kotlin**

```kotlin
fun main(args: Array<String>) {
    runApplication<MyApplication>(*args)
}
```

> **LƯU Ý (NOTE):** Nếu bạn định khởi động ứng dụng dưới dạng war hoặc dưới dạng một ứng dụng thực thi, bạn cần chia sẻ các tùy chỉnh của builder trong một phương thức vừa có sẵn cho callback của `SpringBootServletInitializer` vừa cho phương thức `main` trong một class tương tự như sau:

**Java**

```java
import org.springframework.boot.Banner;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

@SpringBootApplication
public class MyApplication extends SpringBootServletInitializer {

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
        return customizerBuilder(builder);
    }

    public static void main(String[] args) {
        customizerBuilder(new SpringApplicationBuilder()).run(args);
    }

    private static SpringApplicationBuilder customizerBuilder(SpringApplicationBuilder builder) {
        return builder.sources(MyApplication.class).bannerMode(Banner.Mode.OFF);
    }

}
```

**Kotlin**

```kotlin
import org.springframework.boot.Banner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer

@SpringBootApplication
class MyApplication : SpringBootServletInitializer() {

    override fun configure(builder: SpringApplicationBuilder): SpringApplicationBuilder {
        return customizerBuilder(builder)
    }

    companion object {

        @JvmStatic
        fun main(args: Array<String>) {
            customizerBuilder(SpringApplicationBuilder()).run(*args)
        }

        private fun customizerBuilder(builder: SpringApplicationBuilder): SpringApplicationBuilder {
            return builder.sources(MyApplication::class.java).bannerMode(Banner.Mode.OFF)
        }

    }

}
```

Các ứng dụng có thể thuộc nhiều hơn một loại:

- Các ứng dụng Servlet 3.0+ không có `web.xml`.
- Các ứng dụng có `web.xml`.
- Các ứng dụng có phân cấp context.
- Các ứng dụng không có phân cấp context.

Tất cả những loại này đều có thể được chuyển đổi, nhưng mỗi loại có thể đòi hỏi các kỹ thuật hơi khác nhau.

Các ứng dụng Servlet 3.0+ có thể được chuyển đổi khá dễ dàng nếu chúng đã dùng các class hỗ trợ initializer Spring Servlet 3.0+. Thông thường, tất cả code từ một `WebApplicationInitializer` hiện có có thể được chuyển vào một `SpringBootServletInitializer`. Nếu ứng dụng hiện có của bạn có nhiều hơn một `ApplicationContext` (ví dụ, nếu nó dùng `AbstractDispatcherServletInitializer`) thì bạn có thể kết hợp tất cả các context source vào một `SpringApplication` duy nhất. Biến chứng chính bạn có thể gặp là nếu việc kết hợp không hoạt động và bạn cần duy trì phân cấp context. Xem [mục về xây dựng phân cấp](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.application.context-hierarchy) để có ví dụ. Một context cha hiện có chứa các tính năng đặc thù cho web thường cần được tách ra để tất cả các component `ServletContextAware` nằm trong context con.

Các ứng dụng chưa phải là ứng dụng Spring có thể chuyển đổi được thành các ứng dụng Spring Boot, và hướng dẫn đã đề cập ở trên có thể giúp ích. Tuy nhiên, bạn vẫn có thể gặp vấn đề. Trong trường hợp đó, chúng tôi đề nghị [đặt câu hỏi trên Stack Overflow với tag `spring-boot`](https://stackoverflow.com/questions/tagged/spring-boot).

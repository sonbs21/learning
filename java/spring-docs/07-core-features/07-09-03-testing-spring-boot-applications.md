# 7.9.3. Kiểm thử ứng dụng Spring Boot (Testing Spring Boot Applications)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.testing.spring-boot-applications>

Một ứng dụng Spring Boot là một `ApplicationContext` của Spring, vì vậy không cần làm gì quá đặc biệt để kiểm thử nó ngoài những gì bạn thường làm với một Spring context thông thường.

> **LƯU Ý (NOTE):** Property bên ngoài, logging và các tính năng khác của Spring Boot chỉ được cài đặt vào context theo mặc định nếu bạn dùng `SpringApplication` để tạo nó.

Spring Boot cung cấp annotation `@SpringBootTest`, có thể dùng thay thế cho annotation `@ContextConfiguration` tiêu chuẩn của `spring-test` khi bạn cần các tính năng của Spring Boot. Annotation này hoạt động bằng cách [tạo `ApplicationContext` dùng trong test thông qua `SpringApplication`](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.testing.spring-boot-applications.detecting-configuration). Ngoài `@SpringBootTest`, một số annotation khác cũng được cung cấp để [kiểm thử các lát cắt (slice) cụ thể hơn](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.testing.spring-boot-applications.autoconfigured-tests) của một ứng dụng.

> **MẸO (TIP):** Nếu bạn đang dùng JUnit 4, đừng quên thêm `@RunWith(SpringRunner.class)` vào test, nếu không các annotation sẽ bị bỏ qua. Nếu bạn đang dùng JUnit 5, không cần thêm `@ExtendWith(SpringExtension.class)` tương đương, vì `@SpringBootTest` và các annotation `@…Test` khác đã được đánh annotation đó sẵn.

Theo mặc định, `@SpringBootTest` sẽ không khởi động server. Bạn có thể dùng thuộc tính `webEnvironment` của `@SpringBootTest` để tinh chỉnh thêm cách test của bạn chạy:

- `MOCK` (Mặc định): Nạp một web `ApplicationContext` và cung cấp một môi trường web giả lập (mock). Server nhúng không được khởi động khi dùng annotation này. Nếu không có môi trường web trên classpath, chế độ này sẽ tự động chuyển sang tạo một `ApplicationContext` thông thường không phải web. Nó có thể được dùng kết hợp với [`@AutoConfigureMockMvc` hoặc `@AutoConfigureWebTestClient`](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.testing.spring-boot-applications.with-mock-environment) để kiểm thử ứng dụng web dựa trên mock.
- `RANDOM_PORT`: Nạp một `WebServerApplicationContext` và cung cấp một môi trường web thực. Server nhúng được khởi động và lắng nghe trên một cổng ngẫu nhiên.
- `DEFINED_PORT`: Nạp một `WebServerApplicationContext` và cung cấp một môi trường web thực. Server nhúng được khởi động và lắng nghe trên một cổng đã định nghĩa (từ `application.properties`) hoặc trên cổng mặc định `8080`.
- `NONE`: Nạp một `ApplicationContext` bằng `SpringApplication` nhưng không cung cấp *bất kỳ* môi trường web nào (mock hay thực).

> **LƯU Ý (NOTE):** Nếu test của bạn là `@Transactional`, theo mặc định nó sẽ rollback transaction ở cuối mỗi phương thức test. Tuy nhiên, vì việc dùng cách bố trí này với `RANDOM_PORT` hoặc `DEFINED_PORT` ngầm cung cấp một môi trường servlet thực, HTTP client và server chạy trong các thread riêng biệt và do đó, trong các transaction riêng biệt. Bất kỳ transaction nào được khởi tạo trên server sẽ không bị rollback trong trường hợp này.

> **LƯU Ý (NOTE):** `@SpringBootTest` với `webEnvironment = WebEnvironment.RANDOM_PORT` cũng sẽ khởi động management server trên một cổng ngẫu nhiên riêng nếu ứng dụng của bạn dùng một cổng khác cho management server.

## Phát hiện loại ứng dụng web (Detecting Web Application Type)

Nếu có Spring MVC, một application context dựa trên MVC thông thường sẽ được cấu hình. Nếu bạn chỉ có Spring WebFlux, chúng tôi sẽ phát hiện điều đó và cấu hình một application context dựa trên WebFlux thay thế.

Nếu có cả hai, Spring MVC được ưu tiên. Nếu bạn muốn kiểm thử một ứng dụng web reactive trong trường hợp này, bạn phải đặt property `spring.main.web-application-type`:

**Java**

```java
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "spring.main.web-application-type=reactive")
class MyWebFluxTests {

    // ...

}
```

**Kotlin**

```kotlin
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest(properties = ["spring.main.web-application-type=reactive"])
class MyWebFluxTests {

    // ...

}
```

## Phát hiện cấu hình test (Detecting Test Configuration)

Nếu bạn quen thuộc với Spring Test Framework, bạn có thể đã quen dùng `@ContextConfiguration(classes=…)` để chỉ định `@Configuration` nào của Spring cần nạp. Ngoài ra, bạn có thể thường dùng các class `@Configuration` lồng nhau bên trong test.

Khi kiểm thử ứng dụng Spring Boot, điều này thường là không cần thiết. Các annotation `@*Test` của Spring Boot tự động tìm kiếm cấu hình chính của bạn bất cứ khi nào bạn không định nghĩa tường minh.

Thuật toán tìm kiếm đi ngược lên từ package chứa test cho đến khi tìm thấy một class được đánh annotation `@SpringBootApplication` hoặc `@SpringBootConfiguration`. Miễn là bạn [tổ chức code](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#using.structuring-your-code) một cách hợp lý, cấu hình chính của bạn thường sẽ được tìm thấy.

> **LƯU Ý (NOTE):** Nếu bạn dùng một [test annotation để kiểm thử một lát cắt cụ thể hơn của ứng dụng](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.testing.spring-boot-applications.autoconfigured-tests), bạn nên tránh thêm các thiết lập cấu hình đặc thù cho một khu vực cụ thể vào [class ứng dụng chứa phương thức main](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.testing.spring-boot-applications.user-configuration-and-slicing).
>
> Cấu hình component scan bên dưới của `@SpringBootApplication` định nghĩa các exclude filter được dùng để đảm bảo việc cắt lát (slicing) hoạt động như mong đợi. Nếu bạn đang dùng một chỉ thị `@ComponentScan` tường minh trên class được đánh annotation `@SpringBootApplication`, hãy lưu ý rằng các filter đó sẽ bị vô hiệu hóa. Nếu bạn đang dùng slicing, bạn nên định nghĩa lại chúng.

Nếu bạn muốn tùy chỉnh cấu hình chính, bạn có thể dùng một class `@TestConfiguration` lồng nhau. Không giống như một class `@Configuration` lồng nhau, vốn sẽ được dùng thay cho cấu hình chính của ứng dụng, một class `@TestConfiguration` lồng nhau được dùng bổ sung cho cấu hình chính của ứng dụng.

> **LƯU Ý (NOTE):** Test framework của Spring cache các application context giữa các test. Vì vậy, miễn là các test của bạn dùng chung cùng một cấu hình (bất kể nó được phát hiện như thế nào), quá trình nạp context có thể tốn thời gian chỉ diễn ra một lần.

## Sử dụng phương thức main của cấu hình test (Using the Test Configuration Main Method)

Thông thường, cấu hình test được `@SpringBootTest` phát hiện sẽ là `@SpringBootApplication` chính của bạn. Trong hầu hết các ứng dụng được tổ chức tốt, class cấu hình này cũng sẽ bao gồm phương thức `main` dùng để khởi chạy ứng dụng.

Ví dụ, sau đây là một mẫu code rất phổ biến cho một ứng dụng Spring Boot điển hình:

**Java**

```java
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MyApplication {

    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }

}
```

**Kotlin**

```kotlin
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.docs.using.structuringyourcode.locatingthemainclass.MyApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class MyApplication

fun main(args: Array<String>) {
    runApplication<MyApplication>(*args)
}
```

Trong ví dụ trên, phương thức `main` không làm gì khác ngoài việc ủy quyền cho `SpringApplication.run`. Tuy nhiên, có thể có một phương thức `main` phức tạp hơn, áp dụng các tùy chỉnh trước khi gọi `SpringApplication.run`.

Ví dụ, đây là một ứng dụng thay đổi chế độ banner và đặt thêm profile:

**Java**

```java
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MyApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(MyApplication.class);
        application.setBannerMode(Banner.Mode.OFF);
        application.setAdditionalProfiles("myprofile");
        application.run(args);
    }

}
```

**Kotlin**

```kotlin
import org.springframework.boot.Banner
import org.springframework.boot.runApplication
import org.springframework.boot.autoconfigure.SpringBootApplication

@SpringBootApplication
class MyApplication

fun main(args: Array<String>) {
    runApplication<MyApplication>(*args) {
        setBannerMode(Banner.Mode.OFF)
        setAdditionalProfiles("myprofile")
    }
}
```

Vì các tùy chỉnh trong phương thức `main` có thể ảnh hưởng đến `ApplicationContext` kết quả, có thể bạn cũng muốn dùng phương thức `main` để tạo `ApplicationContext` dùng trong test. Theo mặc định, `@SpringBootTest` sẽ không gọi phương thức `main` của bạn, mà thay vào đó dùng trực tiếp chính class đó để tạo `ApplicationContext`.

Nếu bạn muốn thay đổi hành vi này, bạn có thể đổi thuộc tính `useMainMethod` của `@SpringBootTest` thành `UseMainMethod.ALWAYS` hoặc `UseMainMethod.WHEN_AVAILABLE`. Khi đặt thành `ALWAYS`, test sẽ thất bại nếu không tìm thấy phương thức `main`. Khi đặt thành `WHEN_AVAILABLE`, phương thức `main` sẽ được dùng nếu có, nếu không cơ chế nạp tiêu chuẩn sẽ được dùng.

Ví dụ, test sau sẽ gọi phương thức `main` của `MyApplication` để tạo `ApplicationContext`. Nếu phương thức main đặt thêm profile thì những profile đó sẽ hoạt động khi `ApplicationContext` khởi động.

**Java**

```java
import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.UseMainMethod;

@SpringBootTest(useMainMethod = UseMainMethod.ALWAYS)
class MyApplicationTests {

    @Test
    void exampleTest() {
        // ...
    }

}
```

**Kotlin**

```kotlin
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.UseMainMethod

@SpringBootTest(useMainMethod = UseMainMethod.ALWAYS)
class MyApplicationTests {

    @Test
    fun exampleTest() {
        // ...
    }

}
```

## Loại trừ cấu hình test (Excluding Test Configuration)

Nếu ứng dụng của bạn dùng component scanning (ví dụ, nếu bạn dùng `@SpringBootApplication` hoặc `@ComponentScan`), bạn có thể thấy các class cấu hình cấp cao nhất mà bạn tạo ra chỉ cho các test cụ thể vô tình bị nhận diện ở khắp nơi.

Như chúng ta [đã thấy trước đó](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.testing.spring-boot-applications.detecting-configuration), `@TestConfiguration` có thể được dùng trên một inner class của test để tùy chỉnh cấu hình chính. `@TestConfiguration` cũng có thể được dùng trên một class cấp cao nhất. Làm như vậy cho biết class đó không nên bị nhận diện bởi việc quét. Sau đó, bạn có thể import class một cách tường minh ở nơi cần thiết, như trong ví dụ sau:

**Java**

```java
import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(MyTestsConfiguration.class)
class MyTests {

    @Test
    void exampleTest() {
        // ...
    }

}
```

**Kotlin**

```kotlin
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import

@SpringBootTest
@Import(MyTestsConfiguration::class)
class MyTests {

    @Test
    fun exampleTest() {
        // ...
    }

}
```

> **LƯU Ý (NOTE):** Nếu bạn dùng trực tiếp `@ComponentScan` (tức là không thông qua `@SpringBootApplication`), bạn cần đăng ký `TypeExcludeFilter` với nó. Xem [Javadoc](https://docs.spring.io/spring-boot/docs/3.2.5/api/org/springframework/boot/context/TypeExcludeFilter.html) để biết chi tiết.

> **LƯU Ý (NOTE):** Một `@TestConfiguration` được import sẽ được xử lý sớm hơn một `@TestConfiguration` dạng inner class, và một `@TestConfiguration` được import sẽ được xử lý trước bất kỳ cấu hình nào được tìm thấy thông qua component scanning. Nói chung, sự khác biệt về thứ tự này không có ảnh hưởng đáng kể, nhưng đó là điều cần lưu ý nếu bạn dựa vào việc ghi đè bean (bean overriding).

## Sử dụng tham số ứng dụng (Using Application Arguments)

Nếu ứng dụng của bạn cần [tham số](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.spring-application.application-arguments), bạn có thể để `@SpringBootTest` inject chúng bằng thuộc tính `args`.

**Java**

```java
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(args = "--app.test=one")
class MyApplicationArgumentTests {

    @Test
    void applicationArgumentsPopulated(@Autowired ApplicationArguments args) {
        assertThat(args.getOptionNames()).containsOnly("app.test");
        assertThat(args.getOptionValues("app.test")).containsOnly("one");
    }

}
```

**Kotlin**

```kotlin
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest(args = ["--app.test=one"])
class MyApplicationArgumentTests {

    @Test
    fun applicationArgumentsPopulated(@Autowired args: ApplicationArguments) {
        assertThat(args.optionNames).containsOnly("app.test")
        assertThat(args.getOptionValues("app.test")).containsOnly("one")
    }

}
```

## Kiểm thử với môi trường mock (Testing With a Mock Environment)

Theo mặc định, `@SpringBootTest` không khởi động server mà thay vào đó thiết lập một môi trường mock để kiểm thử các web endpoint.

Với Spring MVC, chúng ta có thể truy vấn các web endpoint bằng [`MockMvc`](https://docs.spring.io/spring-framework/reference/6.1/testing/spring-mvc-test-framework.html) hoặc `WebTestClient`, như trong ví dụ sau:

**Java**

```java
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MyMockMvcTests {

    @Test
    void testWithMockMvc(@Autowired MockMvc mvc) throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk()).andExpect(content().string("Hello World"));
    }

    // If Spring WebFlux is on the classpath, you can drive MVC tests with a WebTestClient
    @Test
    void testWithWebTestClient(@Autowired WebTestClient webClient) {
        webClient
                .get().uri("/")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("Hello World");
    }

}
```

**Kotlin**

```kotlin
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.expectBody
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers

@SpringBootTest
@AutoConfigureMockMvc
class MyMockMvcTests {

    @Test
    fun testWithMockMvc(@Autowired mvc: MockMvc) {
        mvc.perform(MockMvcRequestBuilders.get("/")).andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().string("Hello World"))
    }

    // If Spring WebFlux is on the classpath, you can drive MVC tests with a WebTestClient

    @Test
    fun testWithWebTestClient(@Autowired webClient: WebTestClient) {
        webClient
            .get().uri("/")
            .exchange()
            .expectStatus().isOk
            .expectBody<String>().isEqualTo("Hello World")
    }

}
```

> **MẸO (TIP):** Nếu bạn chỉ muốn tập trung vào tầng web và không khởi động một `ApplicationContext` hoàn chỉnh, hãy cân nhắc [dùng `@WebMvcTest` thay thế](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.testing.spring-boot-applications.spring-mvc-tests).

Với các endpoint Spring WebFlux, bạn có thể dùng [`WebTestClient`](https://docs.spring.io/spring-framework/reference/6.1/testing/webtestclient.html) như trong ví dụ sau:

**Java**

```java
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest
@AutoConfigureWebTestClient
class MyMockWebTestClientTests {

    @Test
    void exampleTest(@Autowired WebTestClient webClient) {
        webClient
            .get().uri("/")
            .exchange()
            .expectStatus().isOk()
            .expectBody(String.class).isEqualTo("Hello World");
    }

}
```

**Kotlin**

```kotlin
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.expectBody

@SpringBootTest
@AutoConfigureWebTestClient
class MyMockWebTestClientTests {

    @Test
    fun exampleTest(@Autowired webClient: WebTestClient) {
        webClient
            .get().uri("/")
            .exchange()
            .expectStatus().isOk
            .expectBody<String>().isEqualTo("Hello World")
    }

}
```

> **MẸO (TIP):** Kiểm thử trong một môi trường mock thường nhanh hơn chạy với một servlet container đầy đủ. Tuy nhiên, vì việc mock xảy ra ở tầng Spring MVC, code dựa vào hành vi cấp thấp hơn của servlet container không thể được kiểm thử trực tiếp bằng MockMvc.
>
> Ví dụ, việc xử lý lỗi của Spring Boot dựa trên hỗ trợ "error page" do servlet container cung cấp. Điều này có nghĩa là, mặc dù bạn có thể kiểm thử rằng tầng MVC ném và xử lý exception như mong đợi, bạn không thể kiểm thử trực tiếp rằng một [trang lỗi tùy chỉnh (custom error page)](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#web.servlet.spring-mvc.error-handling.error-pages) cụ thể được hiển thị. Nếu bạn cần kiểm thử những vấn đề cấp thấp này, bạn có thể khởi động một server chạy đầy đủ như mô tả trong phần tiếp theo.

## Kiểm thử với server đang chạy (Testing With a Running Server)

Nếu bạn cần khởi động một server chạy đầy đủ, chúng tôi khuyên bạn nên dùng cổng ngẫu nhiên. Nếu bạn dùng `@SpringBootTest(webEnvironment=WebEnvironment.RANDOM_PORT)`, một cổng khả dụng sẽ được chọn ngẫu nhiên mỗi khi test chạy.

Annotation `@LocalServerPort` có thể được dùng để [inject cổng thực tế đang được dùng](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.webserver.discover-port) vào test. Để tiện lợi, các test cần thực hiện các lời gọi REST tới server đã khởi động có thể `@Autowire` thêm một [`WebTestClient`](https://docs.spring.io/spring-framework/reference/6.1/testing/webtestclient.html), vốn phân giải các link tương đối tới server đang chạy và đi kèm một API chuyên biệt để kiểm tra response, như trong ví dụ sau:

**Java**

```java
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class MyRandomPortWebTestClientTests {

    @Test
    void exampleTest(@Autowired WebTestClient webClient) {
        webClient
            .get().uri("/")
            .exchange()
            .expectStatus().isOk()
            .expectBody(String.class).isEqualTo("Hello World");
    }

}
```

**Kotlin**

```kotlin
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.expectBody

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class MyRandomPortWebTestClientTests {

    @Test
    fun exampleTest(@Autowired webClient: WebTestClient) {
        webClient
            .get().uri("/")
            .exchange()
            .expectStatus().isOk
            .expectBody<String>().isEqualTo("Hello World")
    }

}
```

> **MẸO (TIP):** `WebTestClient` cũng có thể được dùng với một [môi trường mock](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.testing.spring-boot-applications.with-mock-environment), loại bỏ nhu cầu có một server đang chạy, bằng cách đánh annotation `@AutoConfigureWebTestClient` lên class test.

Thiết lập này yêu cầu có `spring-webflux` trên classpath. Nếu bạn không thể hoặc không muốn thêm webflux, Spring Boot cũng cung cấp tiện ích `TestRestTemplate`:

**Java**

```java
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class MyRandomPortTestRestTemplateTests {

    @Test
    void exampleTest(@Autowired TestRestTemplate restTemplate) {
        String body = restTemplate.getForObject("/", String.class);
        assertThat(body).isEqualTo("Hello World");
    }

}
```

**Kotlin**

```kotlin
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.boot.test.web.client.TestRestTemplate

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class MyRandomPortTestRestTemplateTests {

    @Test
    fun exampleTest(@Autowired restTemplate: TestRestTemplate) {
        val body = restTemplate.getForObject("/", String::class.java)
        assertThat(body).isEqualTo("Hello World")
    }

}
```

## Tùy chỉnh WebTestClient (Customizing WebTestClient)

Để tùy chỉnh bean `WebTestClient`, hãy cấu hình một bean `WebTestClientBuilderCustomizer`. Mọi bean như vậy đều được gọi với `WebTestClient.Builder` được dùng để tạo `WebTestClient`.

## Sử dụng JMX (Using JMX)

Vì test context framework cache context, JMX bị vô hiệu hóa theo mặc định để ngăn các component giống hệt nhau đăng ký trên cùng một domain. Nếu một test như vậy cần truy cập `MBeanServer`, hãy cân nhắc đánh dấu nó là "dirty" luôn:

**Java**

```java
import javax.management.MBeanServer;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.jmx.enabled=true")
@DirtiesContext
class MyJmxTests {

    @Autowired
    private MBeanServer mBeanServer;

    @Test
    void exampleTest() {
        assertThat(this.mBeanServer.getDomains()).contains("java.lang");
        // ...
    }

}
```

**Kotlin**

```kotlin
import javax.management.MBeanServer

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.DirtiesContext

@SpringBootTest(properties = ["spring.jmx.enabled=true"])
@DirtiesContext
class MyJmxTests(@Autowired val mBeanServer: MBeanServer) {

    @Test
    fun exampleTest() {
        assertThat(mBeanServer.domains).contains("java.lang")
        // ...
    }

}
```

## Sử dụng Observation (Using Observations)

Nếu bạn đánh annotation `@AutoConfigureObservability` lên [một sliced test](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.testing.spring-boot-applications.autoconfigured-tests), nó sẽ tự động cấu hình một `ObservationRegistry`.

## Sử dụng Metrics (Using Metrics)

Bất kể classpath của bạn như thế nào, các meter registry, ngoại trừ loại dựa trên bộ nhớ (in-memory), không được tự động cấu hình khi dùng `@SpringBootTest`.

Nếu bạn cần xuất metrics sang một backend khác như một phần của integration test, hãy đánh annotation `@AutoConfigureObservability` lên nó.

Nếu bạn đánh annotation `@AutoConfigureObservability` lên [một sliced test](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.testing.spring-boot-applications.autoconfigured-tests), nó sẽ tự động cấu hình một `MeterRegistry` in-memory. Việc xuất dữ liệu trong sliced test không được hỗ trợ với annotation `@AutoConfigureObservability`.

## Sử dụng Tracing (Using Tracing)

Bất kể classpath của bạn như thế nào, các component tracing có báo cáo dữ liệu không được tự động cấu hình khi dùng `@SpringBootTest`.

Nếu bạn cần các component đó như một phần của integration test, hãy đánh annotation `@AutoConfigureObservability` lên test.

Nếu bạn đã tạo các component báo cáo của riêng mình (ví dụ một `SpanExporter` hoặc `SpanHandler` tùy chỉnh) và bạn không muốn chúng hoạt động trong test, bạn có thể dùng annotation `@ConditionalOnEnabledTracing` để vô hiệu hóa chúng.

Nếu bạn đánh annotation `@AutoConfigureObservability` lên [một sliced test](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.testing.spring-boot-applications.autoconfigured-tests), nó sẽ tự động cấu hình một `Tracer` không làm gì (no-op). Việc xuất dữ liệu trong sliced test không được hỗ trợ với annotation `@AutoConfigureObservability`.

## Mock và Spy bean (Mocking and Spying Beans)

Khi chạy test, đôi khi cần mock một số component nhất định trong application context. Ví dụ, bạn có thể có một facade bọc một remote service không khả dụng trong quá trình phát triển. Mock cũng có thể hữu ích khi bạn muốn mô phỏng các lỗi khó kích hoạt trong môi trường thực.

Spring Boot bao gồm annotation `@MockBean` có thể dùng để định nghĩa một Mockito mock cho một bean bên trong `ApplicationContext`. Bạn có thể dùng annotation này để thêm bean mới hoặc thay thế một bean definition hiện có. Annotation có thể được dùng trực tiếp trên các class test, trên các field trong test, hoặc trên các class và field `@Configuration`. Khi được dùng trên một field, instance của mock được tạo ra cũng được inject vào. Các mock bean được tự động reset sau mỗi phương thức test.

> **LƯU Ý (NOTE):** Nếu test của bạn dùng một trong các test annotation của Spring Boot (chẳng hạn như `@SpringBootTest`), tính năng này được tự động bật. Để dùng tính năng này với một cách bố trí khác, các listener phải được thêm vào một cách tường minh, như trong ví dụ sau:

**Java**

```java
import org.springframework.boot.test.mock.mockito.MockitoTestExecutionListener;
import org.springframework.boot.test.mock.mockito.ResetMocksTestExecutionListener;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestExecutionListeners;

@ContextConfiguration(classes = MyConfig.class)
@TestExecutionListeners({ MockitoTestExecutionListener.class, ResetMocksTestExecutionListener.class })
class MyTests {

    // ...

}
```

**Kotlin**

```kotlin
import org.springframework.boot.test.mock.mockito.MockitoTestExecutionListener
import org.springframework.boot.test.mock.mockito.ResetMocksTestExecutionListener
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestExecutionListeners

@ContextConfiguration(classes = [MyConfig::class])
@TestExecutionListeners(
    MockitoTestExecutionListener::class,
    ResetMocksTestExecutionListener::class
)
class MyTests {

    // ...

}
```

Ví dụ sau thay thế một bean `RemoteService` hiện có bằng một implementation mock:

**Java**

```java
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@SpringBootTest
class MyTests {

    @Autowired
    private Reverser reverser;

    @MockBean
    private RemoteService remoteService;

    @Test
    void exampleTest() {
        given(this.remoteService.getValue()).willReturn("spring");
        String reverse = this.reverser.getReverseValue(); // Calls injected RemoteService
        assertThat(reverse).isEqualTo("gnirps");
    }

}
```

**Kotlin**

```kotlin
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean

@SpringBootTest
class MyTests(@Autowired val reverser: Reverser, @MockBean val remoteService: RemoteService) {

    @Test
    fun exampleTest() {
        given(remoteService.value).willReturn("spring")
        val reverse = reverser.reverseValue // Calls injected RemoteService
        assertThat(reverse).isEqualTo("gnirps")
    }

}
```

> **LƯU Ý (NOTE):** `@MockBean` không thể được dùng để mock hành vi của một bean được sử dụng trong quá trình refresh application context. Vào thời điểm test được thực thi, việc refresh application context đã hoàn tất và đã quá muộn để cấu hình hành vi được mock. Chúng tôi khuyên dùng một phương thức `@Bean` để tạo và cấu hình mock trong tình huống này.

Ngoài ra, bạn có thể dùng `@SpyBean` để bọc bất kỳ bean hiện có nào bằng một Mockito `spy`. Xem [Javadoc](https://docs.spring.io/spring-boot/docs/3.2.5/api/org/springframework/boot/test/mock/mockito/SpyBean.html) để biết đầy đủ chi tiết.

> **LƯU Ý (NOTE):** Mặc dù test framework của Spring cache các application context giữa các test và tái sử dụng một context cho các test dùng chung cùng một cấu hình, việc dùng `@MockBean` hoặc `@SpyBean` ảnh hưởng đến cache key, điều này rất có thể sẽ làm tăng số lượng context.

> **MẸO (TIP):** Nếu bạn đang dùng `@SpyBean` để spy một bean có các phương thức `@Cacheable` tham chiếu tới tham số theo tên, ứng dụng của bạn phải được biên dịch với `-parameters`. Điều này đảm bảo tên tham số khả dụng với hạ tầng caching sau khi bean đã bị spy.

> **MẸO (TIP):** Khi bạn đang dùng `@SpyBean` để spy một bean được Spring tạo proxy, bạn có thể cần loại bỏ proxy của Spring trong một số tình huống, ví dụ khi đặt kỳ vọng bằng `given` hoặc `when`. Hãy dùng `AopTestUtils.getTargetObject(yourProxiedSpy)` để làm điều đó.

## Test được tự động cấu hình (Auto-configured Tests)

Hệ thống auto-configuration của Spring Boot hoạt động tốt cho ứng dụng nhưng đôi khi có thể hơi quá mức cho test. Thường sẽ hữu ích nếu chỉ nạp những phần cấu hình cần thiết để kiểm thử một "lát cắt" (slice) của ứng dụng. Ví dụ, bạn có thể muốn kiểm thử rằng các controller Spring MVC đang ánh xạ URL đúng, và bạn không muốn liên quan đến các lời gọi database trong những test đó, hoặc bạn có thể muốn kiểm thử các entity JPA, và bạn không quan tâm đến tầng web khi những test đó chạy.

Module `spring-boot-test-autoconfigure` bao gồm một số annotation có thể dùng để tự động cấu hình những "lát cắt" như vậy. Mỗi annotation hoạt động theo cách tương tự, cung cấp một annotation `@…Test` nạp `ApplicationContext` và một hoặc nhiều annotation `@AutoConfigure…` có thể dùng để tùy chỉnh các thiết lập auto-configuration.

> **LƯU Ý (NOTE):** Mỗi lát cắt giới hạn component scan vào các component phù hợp và nạp một tập hợp rất hạn chế các class auto-configuration. Nếu bạn cần loại trừ một trong số chúng, hầu hết các annotation `@…Test` đều cung cấp thuộc tính `excludeAutoConfiguration`. Ngoài ra, bạn có thể dùng `@ImportAutoConfiguration#exclude`.

> **LƯU Ý (NOTE):** Việc bao gồm nhiều "lát cắt" bằng cách dùng nhiều annotation `@…Test` trong một test không được hỗ trợ. Nếu bạn cần nhiều "lát cắt", hãy chọn một trong các annotation `@…Test` và tự thêm các annotation `@AutoConfigure…` của các "lát cắt" khác.

> **MẸO (TIP):** Cũng có thể dùng các annotation `@AutoConfigure…` với annotation `@SpringBootTest` tiêu chuẩn. Bạn có thể dùng sự kết hợp này nếu bạn không quan tâm đến việc "cắt lát" ứng dụng nhưng muốn có một số test bean được tự động cấu hình.

## Test JSON được tự động cấu hình (Auto-configured JSON Tests)

Để kiểm thử rằng việc serialize và deserialize JSON của object hoạt động như mong đợi, bạn có thể dùng annotation `@JsonTest`. `@JsonTest` tự động cấu hình JSON mapper được hỗ trợ có sẵn, có thể là một trong các thư viện sau:

- Jackson `ObjectMapper`, mọi bean `@JsonComponent` và mọi `Module` của Jackson
- `Gson`
- `Jsonb`

> **MẸO (TIP):** Danh sách các auto-configuration được bật bởi `@JsonTest` có thể được [tìm thấy trong phụ lục](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#appendix.test-auto-configuration).

Nếu bạn cần cấu hình các thành phần của auto-configuration, bạn có thể dùng annotation `@AutoConfigureJsonTesters`.

Spring Boot bao gồm các helper dựa trên AssertJ, hoạt động với các thư viện JSONAssert và JsonPath để kiểm tra rằng JSON xuất hiện như mong đợi. Các class `JacksonTester`, `GsonTester`, `JsonbTester`, và `BasicJsonTester` có thể được dùng lần lượt cho Jackson, Gson, Jsonb, và String. Mọi field helper trên class test đều có thể được `@Autowired` khi dùng `@JsonTest`. Ví dụ sau cho thấy một class test cho Jackson:

**Java**

```java
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.boot.test.json.JacksonTester;

import static org.assertj.core.api.Assertions.assertThat;

@JsonTest
class MyJsonTests {

    @Autowired
    private JacksonTester<VehicleDetails> json;

    @Test
    void serialize() throws Exception {
        VehicleDetails details = new VehicleDetails("Honda", "Civic");
        // Assert against a `.json` file in the same package as the test
        assertThat(this.json.write(details)).isEqualToJson("expected.json");
        // Or use JSON path based assertions
        assertThat(this.json.write(details)).hasJsonPathStringValue("@.make");
        assertThat(this.json.write(details)).extractingJsonPathStringValue("@.make").isEqualTo("Honda");
    }

    @Test
    void deserialize() throws Exception {
        String content = "{\"make\":\"Ford\",\"model\":\"Focus\"}";
        assertThat(this.json.parse(content)).isEqualTo(new VehicleDetails("Ford", "Focus"));
        assertThat(this.json.parseObject(content).getMake()).isEqualTo("Ford");
    }

}
```

**Kotlin**

```kotlin
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.json.JsonTest
import org.springframework.boot.test.json.JacksonTester

@JsonTest
class MyJsonTests(@Autowired val json: JacksonTester<VehicleDetails>) {

    @Test
    fun serialize() {
        val details = VehicleDetails("Honda", "Civic")
        // Assert against a `.json` file in the same package as the test
        assertThat(json.write(details)).isEqualToJson("expected.json")
        // Or use JSON path based assertions
        assertThat(json.write(details)).hasJsonPathStringValue("@.make")
        assertThat(json.write(details)).extractingJsonPathStringValue("@.make").isEqualTo("Honda")
    }

    @Test
    fun deserialize() {
        val content = "{\"make\":\"Ford\",\"model\":\"Focus\"}"
        assertThat(json.parse(content)).isEqualTo(VehicleDetails("Ford", "Focus"))
        assertThat(json.parseObject(content).make).isEqualTo("Ford")
    }

}
```

> **LƯU Ý (NOTE):** Các class helper JSON cũng có thể được dùng trực tiếp trong các unit test tiêu chuẩn. Để làm vậy, hãy gọi phương thức `initFields` của helper trong phương thức `@Before` nếu bạn không dùng `@JsonTest`.

Nếu bạn dùng các helper dựa trên AssertJ của Spring Boot để assert một giá trị số tại một JSON path nhất định, bạn có thể không dùng được `isEqualTo` tùy thuộc vào kiểu. Thay vào đó, bạn có thể dùng `satisfies` của AssertJ để assert rằng giá trị khớp với điều kiện đã cho. Ví dụ, ví dụ sau assert rằng số thực tế là một giá trị float gần với `0.15` trong phạm vi sai lệch `0.01`.

**Java**

```java
@Test
void someTest() throws Exception {
    SomeObject value = new SomeObject(0.152f);
    assertThat(this.json.write(value)).extractingJsonPathNumberValue("@.test.numberValue")
        .satisfies((number) -> assertThat(number.floatValue()).isCloseTo(0.15f, within(0.01f)));
}
```

**Kotlin**

```kotlin
@Test
fun someTest() {
    val value = SomeObject(0.152f)
    assertThat(json.write(value)).extractingJsonPathNumberValue("@.test.numberValue")
        .satisfies(ThrowingConsumer { number ->
            assertThat(number.toFloat()).isCloseTo(0.15f, within(0.01f))
        })
}
```

## Test Spring MVC được tự động cấu hình (Auto-configured Spring MVC Tests)

Để kiểm thử xem các controller Spring MVC có hoạt động như mong đợi hay không, hãy dùng annotation `@WebMvcTest`. `@WebMvcTest` tự động cấu hình hạ tầng Spring MVC và giới hạn các bean được quét ở `@Controller`, `@ControllerAdvice`, `@JsonComponent`, `Converter`, `GenericConverter`, `Filter`, `HandlerInterceptor`, `WebMvcConfigurer`, `WebMvcRegistrations`, và `HandlerMethodArgumentResolver`. Các bean `@Component` và `@ConfigurationProperties` thông thường không được quét khi dùng annotation `@WebMvcTest`. `@EnableConfigurationProperties` có thể được dùng để đưa các bean `@ConfigurationProperties` vào.

> **MẸO (TIP):** Danh sách các thiết lập auto-configuration được bật bởi `@WebMvcTest` có thể được [tìm thấy trong phụ lục](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#appendix.test-auto-configuration).

> **MẸO (TIP):** Nếu bạn cần đăng ký thêm các component, chẳng hạn như `Module` của Jackson, bạn có thể import thêm các class cấu hình bằng `@Import` trên test.

Thông thường, `@WebMvcTest` được giới hạn ở một controller duy nhất và được dùng kết hợp với `@MockBean` để cung cấp các implementation mock cho các thành phần cộng tác cần thiết.

`@WebMvcTest` cũng tự động cấu hình `MockMvc`. Mock MVC cung cấp một cách mạnh mẽ để kiểm thử nhanh các controller MVC mà không cần khởi động một HTTP server đầy đủ.

> **MẸO (TIP):** Bạn cũng có thể tự động cấu hình `MockMvc` trong một test không phải `@WebMvcTest` (chẳng hạn như `@SpringBootTest`) bằng cách đánh annotation `@AutoConfigureMockMvc` lên nó. Ví dụ sau dùng `MockMvc`:

**Java**

```java
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserVehicleController.class)
class MyControllerTests {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private UserVehicleService userVehicleService;

    @Test
    void testExample() throws Exception {
        given(this.userVehicleService.getVehicleDetails("sboot"))
            .willReturn(new VehicleDetails("Honda", "Civic"));
        this.mvc.perform(get("/sboot/vehicle").accept(MediaType.TEXT_PLAIN))
            .andExpect(status().isOk())
            .andExpect(content().string("Honda Civic"));
    }

}
```

**Kotlin**

```kotlin
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers

@WebMvcTest(UserVehicleController::class)
class MyControllerTests(@Autowired val mvc: MockMvc) {

    @MockBean
    lateinit var userVehicleService: UserVehicleService

    @Test
    fun testExample() {
        given(userVehicleService.getVehicleDetails("sboot"))
            .willReturn(VehicleDetails("Honda", "Civic"))
        mvc.perform(MockMvcRequestBuilders.get("/sboot/vehicle").accept(MediaType.TEXT_PLAIN))
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().string("Honda Civic"))
    }

}
```

> **MẸO (TIP):** Nếu bạn cần cấu hình các thành phần của auto-configuration (ví dụ, khi nào servlet filter nên được áp dụng), bạn có thể dùng các thuộc tính trong annotation `@AutoConfigureMockMvc`.

Nếu bạn dùng HtmlUnit và Selenium, auto-configuration cũng cung cấp một bean `WebClient` của HtmlUnit và/hoặc một bean `WebDriver` của Selenium. Ví dụ sau dùng HtmlUnit:

**Java**

```java
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@WebMvcTest(UserVehicleController.class)
class MyHtmlUnitTests {

    @Autowired
    private WebClient webClient;

    @MockBean
    private UserVehicleService userVehicleService;

    @Test
    void testExample() throws Exception {
        given(this.userVehicleService.getVehicleDetails("sboot")).willReturn(new VehicleDetails("Honda", "Civic"));
        HtmlPage page = this.webClient.getPage("/sboot/vehicle.html");
        assertThat(page.getBody().getTextContent()).isEqualTo("Honda Civic");
    }

}
```

**Kotlin**

```kotlin
import com.gargoylesoftware.htmlunit.WebClient
import com.gargoylesoftware.htmlunit.html.HtmlPage
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean

@WebMvcTest(UserVehicleController::class)
class MyHtmlUnitTests(@Autowired val webClient: WebClient) {

    @MockBean
    lateinit var userVehicleService: UserVehicleService

    @Test
    fun testExample() {
        given(userVehicleService.getVehicleDetails("sboot")).willReturn(VehicleDetails("Honda", "Civic"))
        val page = webClient.getPage<HtmlPage>("/sboot/vehicle.html")
        assertThat(page.body.textContent).isEqualTo("Honda Civic")
    }

}
```

> **LƯU Ý (NOTE):** Theo mặc định, Spring Boot đặt các bean `WebDriver` vào một "scope" đặc biệt để đảm bảo driver thoát sau mỗi test và một instance mới được inject. Nếu bạn không muốn hành vi này, bạn có thể thêm `@Scope("singleton")` vào định nghĩa `@Bean` của `WebDriver`.

> **CẢNH BÁO (WARNING):** Scope `webDriver` do Spring Boot tạo ra sẽ thay thế mọi scope cùng tên do người dùng định nghĩa. Nếu bạn định nghĩa scope `webDriver` của riêng mình, bạn có thể thấy nó ngừng hoạt động khi dùng `@WebMvcTest`.

Nếu bạn có Spring Security trên classpath, `@WebMvcTest` cũng sẽ quét các bean `WebSecurityConfigurer`. Thay vì tắt hoàn toàn bảo mật cho những test như vậy, bạn có thể dùng hỗ trợ kiểm thử của Spring Security. Thông tin chi tiết hơn về cách dùng hỗ trợ `MockMvc` của Spring Security có thể được tìm thấy trong phần how-to *[Kiểm thử với Spring Security (Testing With Spring Security)](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.testing.with-spring-security)*.

> **MẸO (TIP):** Đôi khi viết test Spring MVC là không đủ; Spring Boot có thể giúp bạn chạy [các test end-to-end đầy đủ với một server thực](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.testing.spring-boot-applications.with-running-server).

## Test Spring WebFlux được tự động cấu hình (Auto-configured Spring WebFlux Tests)

Để kiểm thử rằng các controller [Spring WebFlux](https://docs.spring.io/spring-framework/reference/6.1/web-reactive.html) hoạt động như mong đợi, bạn có thể dùng annotation `@WebFluxTest`. `@WebFluxTest` tự động cấu hình hạ tầng Spring WebFlux và giới hạn các bean được quét ở `@Controller`, `@ControllerAdvice`, `@JsonComponent`, `Converter`, `GenericConverter`, `WebFilter`, và `WebFluxConfigurer`. Các bean `@Component` và `@ConfigurationProperties` thông thường không được quét khi dùng annotation `@WebFluxTest`. `@EnableConfigurationProperties` có thể được dùng để đưa các bean `@ConfigurationProperties` vào.

> **MẸO (TIP):** Danh sách các auto-configuration được bật bởi `@WebFluxTest` có thể được [tìm thấy trong phụ lục](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#appendix.test-auto-configuration).

> **MẸO (TIP):** Nếu bạn cần đăng ký thêm các component, chẳng hạn như `Module` của Jackson, bạn có thể import thêm các class cấu hình bằng `@Import` trên test.

Thông thường, `@WebFluxTest` được giới hạn ở một controller duy nhất và được dùng kết hợp với annotation `@MockBean` để cung cấp các implementation mock cho các thành phần cộng tác cần thiết.

`@WebFluxTest` cũng tự động cấu hình [`WebTestClient`](https://docs.spring.io/spring-framework/reference/6.1/testing/webtestclient.html), cung cấp một cách mạnh mẽ để kiểm thử nhanh các controller WebFlux mà không cần khởi động một HTTP server đầy đủ.

> **MẸO (TIP):** Bạn cũng có thể tự động cấu hình `WebTestClient` trong một test không phải `@WebFluxTest` (chẳng hạn như `@SpringBootTest`) bằng cách đánh annotation `@AutoConfigureWebTestClient` lên nó. Ví dụ sau cho thấy một class dùng cả `@WebFluxTest` và `WebTestClient`:

**Java**

```java
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.mockito.BDDMockito.given;

@WebFluxTest(UserVehicleController.class)
class MyControllerTests {

    @Autowired
    private WebTestClient webClient;

    @MockBean
    private UserVehicleService userVehicleService;

    @Test
    void testExample() {
        given(this.userVehicleService.getVehicleDetails("sboot"))
            .willReturn(new VehicleDetails("Honda", "Civic"));
        this.webClient.get().uri("/sboot/vehicle").accept(MediaType.TEXT_PLAIN).exchange()
            .expectStatus().isOk()
            .expectBody(String.class).isEqualTo("Honda Civic");
    }

}
```

**Kotlin**

```kotlin
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.expectBody

@WebFluxTest(UserVehicleController::class)
class MyControllerTests(@Autowired val webClient: WebTestClient) {

    @MockBean
    lateinit var userVehicleService: UserVehicleService

    @Test
    fun testExample() {
        given(userVehicleService.getVehicleDetails("sboot"))
            .willReturn(VehicleDetails("Honda", "Civic"))
        webClient.get().uri("/sboot/vehicle").accept(MediaType.TEXT_PLAIN).exchange()
            .expectStatus().isOk
            .expectBody<String>().isEqualTo("Honda Civic")
    }

}
```

> **MẸO (TIP):** Thiết lập này chỉ được hỗ trợ bởi các ứng dụng WebFlux, vì hiện tại việc dùng `WebTestClient` trong một ứng dụng web được mock chỉ hoạt động với WebFlux.

> **LƯU Ý (NOTE):** `@WebFluxTest` không thể phát hiện các route được đăng ký thông qua functional web framework. Để kiểm thử các bean `RouterFunction` trong context, hãy cân nhắc tự import `RouterFunction` bằng `@Import` hoặc dùng `@SpringBootTest`.

> **LƯU Ý (NOTE):** `@WebFluxTest` không thể phát hiện cấu hình bảo mật tùy chỉnh được đăng ký dưới dạng `@Bean` kiểu `SecurityWebFilterChain`. Để đưa nó vào test, bạn sẽ cần import cấu hình đăng ký bean đó bằng `@Import` hoặc dùng `@SpringBootTest`.

> **MẸO (TIP):** Đôi khi viết test Spring WebFlux là không đủ; Spring Boot có thể giúp bạn chạy [các test end-to-end đầy đủ với một server thực](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.testing.spring-boot-applications.with-running-server).

## Test Spring GraphQL được tự động cấu hình (Auto-configured Spring GraphQL Tests)

Spring GraphQL cung cấp một module hỗ trợ kiểm thử chuyên biệt; bạn sẽ cần thêm nó vào project:

**Maven**

```xml
<dependencies>
  <dependency>
    <groupId>org.springframework.graphql</groupId>
    <artifactId>spring-graphql-test</artifactId>
    <scope>test</scope>
  </dependency>
  <!-- Unless already present in the compile scope -->
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>
    <scope>test</scope>
  </dependency>
</dependencies>
```

**Gradle**

```gradle
dependencies {
  testImplementation("org.springframework.graphql:spring-graphql-test")
  // Unless already present in the implementation configuration
  testImplementation("org.springframework.boot:spring-boot-starter-webflux")
}
```

Module kiểm thử này đi kèm [GraphQlTester](https://docs.spring.io/spring-graphql/reference/1.2/#testing-graphqltester). Tester này được dùng rất nhiều trong test, vì vậy hãy làm quen với cách dùng nó. Có các biến thể `GraphQlTester` và Spring Boot sẽ tự động cấu hình chúng tùy thuộc vào loại test:

- `ExecutionGraphQlServiceTester` thực hiện test ở phía server, không có client hay transport
- `HttpGraphQlTester` thực hiện test với một client kết nối tới server, có hoặc không có server đang chạy

Spring Boot giúp bạn kiểm thử các [Controller Spring GraphQL](https://docs.spring.io/spring-graphql/reference/1.2/#controllers) bằng annotation `@GraphQlTest`. `@GraphQlTest` tự động cấu hình hạ tầng Spring GraphQL, không có transport hay server nào tham gia. Điều này giới hạn các bean được quét ở `@Controller`, `RuntimeWiringConfigurer`, `JsonComponent`, `Converter`, `GenericConverter`, `DataFetcherExceptionResolver`, `Instrumentation` và `GraphQlSourceBuilderCustomizer`. Các bean `@Component` và `@ConfigurationProperties` thông thường không được quét khi dùng annotation `@GraphQlTest`. `@EnableConfigurationProperties` có thể được dùng để đưa các bean `@ConfigurationProperties` vào.

> **MẸO (TIP):** Danh sách các auto-configuration được bật bởi `@GraphQlTest` có thể được [tìm thấy trong phụ lục](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#appendix.test-auto-configuration).

Thông thường, `@GraphQlTest` được giới hạn ở một tập hợp controller và được dùng kết hợp với annotation `@MockBean` để cung cấp các implementation mock cho các thành phần cộng tác cần thiết.

**Java**

```java
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.docs.web.graphql.runtimewiring.GreetingController;
import org.springframework.boot.test.autoconfigure.graphql.GraphQlTest;
import org.springframework.graphql.test.tester.GraphQlTester;

@GraphQlTest(GreetingController.class)
class GreetingControllerTests {

    @Autowired
    private GraphQlTester graphQlTester;

    @Test
    void shouldGreetWithSpecificName() {
        this.graphQlTester.document("{ greeting(name: \"Alice\") } ")
            .execute()
            .path("greeting")
            .entity(String.class)
            .isEqualTo("Hello, Alice!");
    }

    @Test
    void shouldGreetWithDefaultName() {
        this.graphQlTester.document("{ greeting } ")
            .execute()
            .path("greeting")
            .entity(String.class)
            .isEqualTo("Hello, Spring!");
    }

}
```

**Kotlin**

```kotlin
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.docs.web.graphql.runtimewiring.GreetingController
import org.springframework.boot.test.autoconfigure.graphql.GraphQlTest
import org.springframework.graphql.test.tester.GraphQlTester

@GraphQlTest(GreetingController::class)
internal class GreetingControllerTests {

    @Autowired
    lateinit var graphQlTester: GraphQlTester

    @Test
    fun shouldGreetWithSpecificName() {
        graphQlTester.document("{ greeting(name: \"Alice\") } ").execute().path("greeting").entity(String::class.java)
                .isEqualTo("Hello, Alice!")
    }

    @Test
    fun shouldGreetWithDefaultName() {
        graphQlTester.document("{ greeting } ").execute().path("greeting").entity(String::class.java)
                .isEqualTo("Hello, Spring!")
    }

}
```

Các test `@SpringBootTest` là các integration test đầy đủ và liên quan đến toàn bộ ứng dụng. Khi dùng cổng ngẫu nhiên hoặc cổng đã định nghĩa, một server thực được cấu hình và một bean `HttpGraphQlTester` được tự động cung cấp để bạn có thể dùng nó kiểm thử server. Khi một môi trường MOCK được cấu hình, bạn cũng có thể yêu cầu một bean `HttpGraphQlTester` bằng cách đánh annotation `@AutoConfigureHttpGraphQlTester` lên class test:

**Java**

```java
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.graphql.tester.AutoConfigureHttpGraphQlTester;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.graphql.test.tester.HttpGraphQlTester;

@AutoConfigureHttpGraphQlTester
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class GraphQlIntegrationTests {

    @Test
    void shouldGreetWithSpecificName(@Autowired HttpGraphQlTester graphQlTester) {
        HttpGraphQlTester authenticatedTester = graphQlTester.mutate()
            .webTestClient((client) -> client.defaultHeaders((headers) -> headers.setBasicAuth("admin", "ilovespring")))
            .build();
        authenticatedTester.document("{ greeting(name: \"Alice\") } ")
            .execute()
            .path("greeting")
            .entity(String.class)
            .isEqualTo("Hello, Alice!");
    }

}
```

**Kotlin**

```kotlin
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.graphql.tester.AutoConfigureHttpGraphQlTester
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.graphql.test.tester.HttpGraphQlTester
import org.springframework.http.HttpHeaders
import org.springframework.test.web.reactive.server.WebTestClient

@AutoConfigureHttpGraphQlTester
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class GraphQlIntegrationTests {

    @Test
    fun shouldGreetWithSpecificName(@Autowired graphQlTester: HttpGraphQlTester) {
        val authenticatedTester = graphQlTester.mutate()
            .webTestClient { client: WebTestClient.Builder ->
                client.defaultHeaders { headers: HttpHeaders ->
                    headers.setBasicAuth("admin", "ilovespring")
                }
            }.build()
        authenticatedTester.document("{ greeting(name: \"Alice\") } ").execute()
            .path("greeting").entity(String::class.java).isEqualTo("Hello, Alice!")
    }
}
```

## Test Data Cassandra được tự động cấu hình (Auto-configured Data Cassandra Tests)

Bạn có thể dùng `@DataCassandraTest` để kiểm thử các ứng dụng Cassandra. Theo mặc định, nó cấu hình một `CassandraTemplate`, quét các class `@Table`, và cấu hình các repository Spring Data Cassandra. Các bean `@Component` và `@ConfigurationProperties` thông thường không được quét khi dùng annotation `@DataCassandraTest`. `@EnableConfigurationProperties` có thể được dùng để đưa các bean `@ConfigurationProperties` vào. (Để biết thêm về việc dùng Cassandra với Spring Boot, xem "[Cassandra](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#data.nosql.cassandra)".)

> **MẸO (TIP):** Danh sách các thiết lập auto-configuration được bật bởi `@DataCassandraTest` có thể được [tìm thấy trong phụ lục](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#appendix.test-auto-configuration).

Ví dụ sau cho thấy một thiết lập điển hình để dùng test Cassandra trong Spring Boot:

**Java**

```java
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.cassandra.DataCassandraTest;

@DataCassandraTest
class MyDataCassandraTests {

    @Autowired
    private SomeRepository repository;

}
```

**Kotlin**

```kotlin
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.cassandra.DataCassandraTest

@DataCassandraTest
class MyDataCassandraTests(@Autowired val repository: SomeRepository)
```

## Test Data Couchbase được tự động cấu hình (Auto-configured Data Couchbase Tests)

Bạn có thể dùng `@DataCouchbaseTest` để kiểm thử các ứng dụng Couchbase. Theo mặc định, nó cấu hình một `CouchbaseTemplate` hoặc `ReactiveCouchbaseTemplate`, quét các class `@Document`, và cấu hình các repository Spring Data Couchbase. Các bean `@Component` và `@ConfigurationProperties` thông thường không được quét khi dùng annotation `@DataCouchbaseTest`. `@EnableConfigurationProperties` có thể được dùng để đưa các bean `@ConfigurationProperties` vào. (Để biết thêm về việc dùng Couchbase với Spring Boot, xem "[Couchbase](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#data.nosql.couchbase)", ở phần trước của chương này.)

> **MẸO (TIP):** Danh sách các thiết lập auto-configuration được bật bởi `@DataCouchbaseTest` có thể được [tìm thấy trong phụ lục](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#appendix.test-auto-configuration).

Ví dụ sau cho thấy một thiết lập điển hình để dùng test Couchbase trong Spring Boot:

**Java**

```java
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.couchbase.DataCouchbaseTest;

@DataCouchbaseTest
class MyDataCouchbaseTests {

    @Autowired
    private SomeRepository repository;

    // ...

}
```

**Kotlin**

```kotlin
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.couchbase.DataCouchbaseTest

@DataCouchbaseTest
class MyDataCouchbaseTests(@Autowired val repository: SomeRepository) {

    // ...

}
```

## Test Data Elasticsearch được tự động cấu hình (Auto-configured Data Elasticsearch Tests)

Bạn có thể dùng `@DataElasticsearchTest` để kiểm thử các ứng dụng Elasticsearch. Theo mặc định, nó cấu hình một `ElasticsearchRestTemplate`, quét các class `@Document`, và cấu hình các repository Spring Data Elasticsearch. Các bean `@Component` và `@ConfigurationProperties` thông thường không được quét khi dùng annotation `@DataElasticsearchTest`. `@EnableConfigurationProperties` có thể được dùng để đưa các bean `@ConfigurationProperties` vào. (Để biết thêm về việc dùng Elasticsearch với Spring Boot, xem "[Elasticsearch](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#data.nosql.elasticsearch)", ở phần trước của chương này.)

> **MẸO (TIP):** Danh sách các thiết lập auto-configuration được bật bởi `@DataElasticsearchTest` có thể được [tìm thấy trong phụ lục](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#appendix.test-auto-configuration).

Ví dụ sau cho thấy một thiết lập điển hình để dùng test Elasticsearch trong Spring Boot:

**Java**

```java
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.elasticsearch.DataElasticsearchTest;

@DataElasticsearchTest
class MyDataElasticsearchTests {

    @Autowired
    private SomeRepository repository;

    // ...

}
```

**Kotlin**

```kotlin
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.elasticsearch.DataElasticsearchTest

@DataElasticsearchTest
class MyDataElasticsearchTests(@Autowired val repository: SomeRepository) {

    // ...

}
```

## Test Data JPA được tự động cấu hình (Auto-configured Data JPA Tests)

Bạn có thể dùng annotation `@DataJpaTest` để kiểm thử các ứng dụng JPA. Theo mặc định, nó quét các class `@Entity` và cấu hình các repository Spring Data JPA. Nếu có một database nhúng trên classpath, nó cũng cấu hình database đó. Các câu truy vấn SQL được ghi log theo mặc định bằng cách đặt property `spring.jpa.show-sql` thành `true`. Điều này có thể được tắt bằng thuộc tính `showSql` của annotation.

Các bean `@Component` và `@ConfigurationProperties` thông thường không được quét khi dùng annotation `@DataJpaTest`. `@EnableConfigurationProperties` có thể được dùng để đưa các bean `@ConfigurationProperties` vào.

> **MẸO (TIP):** Danh sách các thiết lập auto-configuration được bật bởi `@DataJpaTest` có thể được [tìm thấy trong phụ lục](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#appendix.test-auto-configuration).

Theo mặc định, các test data JPA có tính transaction và rollback ở cuối mỗi test. Xem [phần liên quan](https://docs.spring.io/spring-framework/reference/6.1/testing/testcontext-framework/tx.html#testcontext-tx-enabling-transactions) trong tài liệu tham khảo Spring Framework để biết thêm chi tiết. Nếu đó không phải là điều bạn muốn, bạn có thể tắt quản lý transaction cho một test hoặc cho toàn bộ class như sau:

**Java**

```java
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class MyNonTransactionalTests {

    // ...

}
```

**Kotlin**

```kotlin
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class MyNonTransactionalTests {

    // ...

}
```

Các test data JPA cũng có thể inject một bean [`TestEntityManager`](https://github.com/spring-projects/spring-boot/tree/v3.2.5/spring-boot-project/spring-boot-test-autoconfigure/src/main/java/org/springframework/boot/test/autoconfigure/orm/jpa/TestEntityManager.java), cung cấp một lựa chọn thay thế cho `EntityManager` JPA tiêu chuẩn, được thiết kế riêng cho test.

> **MẸO (TIP):** `TestEntityManager` cũng có thể được tự động cấu hình cho bất kỳ class test dựa trên Spring nào của bạn bằng cách thêm `@AutoConfigureTestEntityManager`. Khi làm vậy, hãy đảm bảo test của bạn đang chạy trong một transaction, ví dụ bằng cách thêm `@Transactional` trên class hoặc phương thức test.

Một `JdbcTemplate` cũng có sẵn nếu bạn cần. Ví dụ sau cho thấy annotation `@DataJpaTest` đang được sử dụng:

**Java**

```java
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class MyRepositoryTests {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private UserRepository repository;

    @Test
    void testExample() {
        this.entityManager.persist(new User("sboot", "1234"));
        User user = this.repository.findByUsername("sboot");
        assertThat(user.getUsername()).isEqualTo("sboot");
        assertThat(user.getEmployeeNumber()).isEqualTo("1234");
    }

}
```

**Kotlin**

```kotlin
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager

@DataJpaTest
class MyRepositoryTests(@Autowired val entityManager: TestEntityManager, @Autowired val repository: UserRepository) {

    @Test
    fun testExample() {
        entityManager.persist(User("sboot", "1234"))
        val user = repository.findByUsername("sboot")
        assertThat(user?.username).isEqualTo("sboot")
        assertThat(user?.employeeNumber).isEqualTo("1234")
    }

}
```

Database nhúng in-memory thường hoạt động tốt cho test, vì chúng nhanh và không cần cài đặt gì. Tuy nhiên, nếu bạn muốn chạy test trên một database thực, bạn có thể dùng annotation `@AutoConfigureTestDatabase`, như trong ví dụ sau:

**Java**

```java
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
class MyRepositoryTests {

    // ...

}
```

**Kotlin**

```kotlin
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MyRepositoryTests {

    // ...

}
```

## Test JDBC được tự động cấu hình (Auto-configured JDBC Tests)

`@JdbcTest` tương tự như `@DataJpaTest` nhưng dành cho các test chỉ cần một `DataSource` và không dùng Spring Data JDBC. Theo mặc định, nó cấu hình một database nhúng in-memory và một `JdbcTemplate`. Các bean `@Component` và `@ConfigurationProperties` thông thường không được quét khi dùng annotation `@JdbcTest`. `@EnableConfigurationProperties` có thể được dùng để đưa các bean `@ConfigurationProperties` vào.

> **MẸO (TIP):** Danh sách các auto-configuration được bật bởi `@JdbcTest` có thể được [tìm thấy trong phụ lục](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#appendix.test-auto-configuration).

Theo mặc định, các test JDBC có tính transaction và rollback ở cuối mỗi test. Xem [phần liên quan](https://docs.spring.io/spring-framework/reference/6.1/testing/testcontext-framework/tx.html#testcontext-tx-enabling-transactions) trong tài liệu tham khảo Spring Framework để biết thêm chi tiết. Nếu đó không phải là điều bạn muốn, bạn có thể tắt quản lý transaction cho một test hoặc cho toàn bộ class, như sau:

**Java**

```java
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@JdbcTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class MyTransactionalTests {

}
```

**Kotlin**

```kotlin
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@JdbcTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class MyTransactionalTests
```

Nếu bạn muốn test chạy trên một database thực, bạn có thể dùng annotation `@AutoConfigureTestDatabase` theo cùng cách như với `@DataJpaTest`. (Xem "[Test Data JPA được tự động cấu hình (Auto-configured Data JPA Tests)](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.testing.spring-boot-applications.autoconfigured-spring-data-jpa)".)

## Test Data JDBC được tự động cấu hình (Auto-configured Data JDBC Tests)

`@DataJdbcTest` tương tự như `@JdbcTest` nhưng dành cho các test dùng repository Spring Data JDBC. Theo mặc định, nó cấu hình một database nhúng in-memory, một `JdbcTemplate`, và các repository Spring Data JDBC. Chỉ các subclass của `AbstractJdbcConfiguration` được quét khi dùng annotation `@DataJdbcTest`, các bean `@Component` và `@ConfigurationProperties` thông thường không được quét. `@EnableConfigurationProperties` có thể được dùng để đưa các bean `@ConfigurationProperties` vào.

> **MẸO (TIP):** Danh sách các auto-configuration được bật bởi `@DataJdbcTest` có thể được [tìm thấy trong phụ lục](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#appendix.test-auto-configuration).

Theo mặc định, các test Data JDBC có tính transaction và rollback ở cuối mỗi test. Xem [phần liên quan](https://docs.spring.io/spring-framework/reference/6.1/testing/testcontext-framework/tx.html#testcontext-tx-enabling-transactions) trong tài liệu tham khảo Spring Framework để biết thêm chi tiết. Nếu đó không phải là điều bạn muốn, bạn có thể tắt quản lý transaction cho một test hoặc cho toàn bộ class test [như trong ví dụ JDBC](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.testing.spring-boot-applications.autoconfigured-jdbc).

Nếu bạn muốn test chạy trên một database thực, bạn có thể dùng annotation `@AutoConfigureTestDatabase` theo cùng cách như với `@DataJpaTest`. (Xem "[Test Data JPA được tự động cấu hình (Auto-configured Data JPA Tests)](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.testing.spring-boot-applications.autoconfigured-spring-data-jpa)".)

## Test Data R2DBC được tự động cấu hình (Auto-configured Data R2DBC Tests)

`@DataR2dbcTest` tương tự như `@DataJdbcTest` nhưng dành cho các test dùng repository Spring Data R2DBC. Theo mặc định, nó cấu hình một database nhúng in-memory, một `R2dbcEntityTemplate`, và các repository Spring Data R2DBC. Các bean `@Component` và `@ConfigurationProperties` thông thường không được quét khi dùng annotation `@DataR2dbcTest`. `@EnableConfigurationProperties` có thể được dùng để đưa các bean `@ConfigurationProperties` vào.

> **MẸO (TIP):** Danh sách các auto-configuration được bật bởi `@DataR2dbcTest` có thể được [tìm thấy trong phụ lục](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#appendix.test-auto-configuration).

Theo mặc định, các test Data R2DBC không có tính transaction.

Nếu bạn muốn test chạy trên một database thực, bạn có thể dùng annotation `@AutoConfigureTestDatabase` theo cùng cách như với `@DataJpaTest`. (Xem "[Test Data JPA được tự động cấu hình (Auto-configured Data JPA Tests)](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.testing.spring-boot-applications.autoconfigured-spring-data-jpa)".)

## Test jOOQ được tự động cấu hình (Auto-configured jOOQ Tests)

Bạn có thể dùng `@JooqTest` theo cách tương tự như `@JdbcTest` nhưng cho các test liên quan đến jOOQ. Vì jOOQ phụ thuộc nhiều vào một schema dựa trên Java tương ứng với schema của database, `DataSource` hiện có sẽ được sử dụng. Nếu bạn muốn thay thế nó bằng một database in-memory, bạn có thể dùng `@AutoConfigureTestDatabase` để ghi đè các thiết lập đó. (Để biết thêm về việc dùng jOOQ với Spring Boot, xem "[Sử dụng jOOQ (Using jOOQ)](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#data.sql.jooq)".) Các bean `@Component` và `@ConfigurationProperties` thông thường không được quét khi dùng annotation `@JooqTest`. `@EnableConfigurationProperties` có thể được dùng để đưa các bean `@ConfigurationProperties` vào.

> **MẸO (TIP):** Danh sách các auto-configuration được bật bởi `@JooqTest` có thể được [tìm thấy trong phụ lục](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#appendix.test-auto-configuration).

`@JooqTest` cấu hình một `DSLContext`. Ví dụ sau cho thấy annotation `@JooqTest` đang được sử dụng:

**Java**

```java
import org.jooq.DSLContext;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jooq.JooqTest;

@JooqTest
class MyJooqTests {

    @Autowired
    private DSLContext dslContext;

    // ...

}
```

**Kotlin**

```kotlin
import org.jooq.DSLContext
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jooq.JooqTest

@JooqTest
class MyJooqTests(@Autowired val dslContext: DSLContext) {

    // ...

}
```

Các test JOOQ có tính transaction và rollback ở cuối mỗi test theo mặc định. Nếu đó không phải là điều bạn muốn, bạn có thể tắt quản lý transaction cho một test hoặc cho toàn bộ class test [như trong ví dụ JDBC](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.testing.spring-boot-applications.autoconfigured-jdbc).

## Test Data MongoDB được tự động cấu hình (Auto-configured Data MongoDB Tests)

Bạn có thể dùng `@DataMongoTest` để kiểm thử các ứng dụng MongoDB. Theo mặc định, nó cấu hình một `MongoTemplate`, quét các class `@Document`, và cấu hình các repository Spring Data MongoDB. Các bean `@Component` và `@ConfigurationProperties` thông thường không được quét khi dùng annotation `@DataMongoTest`. `@EnableConfigurationProperties` có thể được dùng để đưa các bean `@ConfigurationProperties` vào. (Để biết thêm về việc dùng MongoDB với Spring Boot, xem "[MongoDB](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#data.nosql.mongodb)".)

> **MẸO (TIP):** Danh sách các thiết lập auto-configuration được bật bởi `@DataMongoTest` có thể được [tìm thấy trong phụ lục](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#appendix.test-auto-configuration).

Class sau cho thấy annotation `@DataMongoTest` đang được sử dụng:

**Java**

```java
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.data.mongodb.core.MongoTemplate;

@DataMongoTest
class MyDataMongoDbTests {

    @Autowired
    private MongoTemplate mongoTemplate;

    // ...

}
```

**Kotlin**

```kotlin
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
import org.springframework.data.mongodb.core.MongoTemplate

@DataMongoTest
class MyDataMongoDbTests(@Autowired val mongoTemplate: MongoTemplate) {

    // ...

}
```

## Test Data Neo4j được tự động cấu hình (Auto-configured Data Neo4j Tests)

Bạn có thể dùng `@DataNeo4jTest` để kiểm thử các ứng dụng Neo4j. Theo mặc định, nó quét các class `@Node`, và cấu hình các repository Spring Data Neo4j. Các bean `@Component` và `@ConfigurationProperties` thông thường không được quét khi dùng annotation `@DataNeo4jTest`. `@EnableConfigurationProperties` có thể được dùng để đưa các bean `@ConfigurationProperties` vào. (Để biết thêm về việc dùng Neo4J với Spring Boot, xem "[Neo4j](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#data.nosql.neo4j)".)

> **MẸO (TIP):** Danh sách các thiết lập auto-configuration được bật bởi `@DataNeo4jTest` có thể được [tìm thấy trong phụ lục](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#appendix.test-auto-configuration).

Ví dụ sau cho thấy một thiết lập điển hình để dùng test Neo4J trong Spring Boot:

**Java**

```java
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.neo4j.DataNeo4jTest;

@DataNeo4jTest
class MyDataNeo4jTests {

    @Autowired
    private SomeRepository repository;

    // ...

}
```

**Kotlin**

```kotlin
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.neo4j.DataNeo4jTest

@DataNeo4jTest
class MyDataNeo4jTests(@Autowired val repository: SomeRepository) {

    // ...

}
```

Theo mặc định, các test Data Neo4j có tính transaction và rollback ở cuối mỗi test. Xem [phần liên quan](https://docs.spring.io/spring-framework/reference/6.1/testing/testcontext-framework/tx.html#testcontext-tx-enabling-transactions) trong tài liệu tham khảo Spring Framework để biết thêm chi tiết. Nếu đó không phải là điều bạn muốn, bạn có thể tắt quản lý transaction cho một test hoặc cho toàn bộ class, như sau:

**Java**

```java
import org.springframework.boot.test.autoconfigure.data.neo4j.DataNeo4jTest;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataNeo4jTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class MyDataNeo4jTests {

}
```

**Kotlin**

```kotlin
import org.springframework.boot.test.autoconfigure.data.neo4j.DataNeo4jTest
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@DataNeo4jTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class MyDataNeo4jTests
```

> **LƯU Ý (NOTE):** Test có transaction không được hỗ trợ với truy cập reactive. Nếu bạn đang dùng kiểu này, bạn phải cấu hình các test `@DataNeo4jTest` như mô tả ở trên.

## Test Data Redis được tự động cấu hình (Auto-configured Data Redis Tests)

Bạn có thể dùng `@DataRedisTest` để kiểm thử các ứng dụng Redis. Theo mặc định, nó quét các class `@RedisHash` và cấu hình các repository Spring Data Redis. Các bean `@Component` và `@ConfigurationProperties` thông thường không được quét khi dùng annotation `@DataRedisTest`. `@EnableConfigurationProperties` có thể được dùng để đưa các bean `@ConfigurationProperties` vào. (Để biết thêm về việc dùng Redis với Spring Boot, xem "[Redis](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#data.nosql.redis)".)

> **MẸO (TIP):** Danh sách các thiết lập auto-configuration được bật bởi `@DataRedisTest` có thể được [tìm thấy trong phụ lục](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#appendix.test-auto-configuration).

Ví dụ sau cho thấy annotation `@DataRedisTest` đang được sử dụng:

**Java**

```java
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.redis.DataRedisTest;

@DataRedisTest
class MyDataRedisTests {

    @Autowired
    private SomeRepository repository;

    // ...

}
```

**Kotlin**

```kotlin
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.redis.DataRedisTest

@DataRedisTest
class MyDataRedisTests(@Autowired val repository: SomeRepository) {

    // ...

}
```

## Test Data LDAP được tự động cấu hình (Auto-configured Data LDAP Tests)

Bạn có thể dùng `@DataLdapTest` để kiểm thử các ứng dụng LDAP. Theo mặc định, nó cấu hình một LDAP nhúng in-memory (nếu có), cấu hình một `LdapTemplate`, quét các class `@Entry`, và cấu hình các repository Spring Data LDAP. Các bean `@Component` và `@ConfigurationProperties` thông thường không được quét khi dùng annotation `@DataLdapTest`. `@EnableConfigurationProperties` có thể được dùng để đưa các bean `@ConfigurationProperties` vào. (Để biết thêm về việc dùng LDAP với Spring Boot, xem "[LDAP](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#data.nosql.ldap)".)

> **MẸO (TIP):** Danh sách các thiết lập auto-configuration được bật bởi `@DataLdapTest` có thể được [tìm thấy trong phụ lục](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#appendix.test-auto-configuration).

Ví dụ sau cho thấy annotation `@DataLdapTest` đang được sử dụng:

**Java**

```java
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.ldap.DataLdapTest;
import org.springframework.ldap.core.LdapTemplate;

@DataLdapTest
class MyDataLdapTests {

    @Autowired
    private LdapTemplate ldapTemplate;

    // ...

}
```

**Kotlin**

```kotlin
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.ldap.DataLdapTest
import org.springframework.ldap.core.LdapTemplate

@DataLdapTest
class MyDataLdapTests(@Autowired val ldapTemplate: LdapTemplate) {

    // ...

}
```

LDAP nhúng in-memory thường hoạt động tốt cho test, vì nó nhanh và không đòi hỏi lập trình viên phải cài đặt gì. Tuy nhiên, nếu bạn muốn chạy test trên một LDAP server thực, bạn nên loại trừ auto-configuration của LDAP nhúng, như trong ví dụ sau:

**Java**

```java
import org.springframework.boot.autoconfigure.ldap.embedded.EmbeddedLdapAutoConfiguration;
import org.springframework.boot.test.autoconfigure.data.ldap.DataLdapTest;

@DataLdapTest(excludeAutoConfiguration = EmbeddedLdapAutoConfiguration.class)
class MyDataLdapTests {

    // ...

}
```

**Kotlin**

```kotlin
import org.springframework.boot.autoconfigure.ldap.embedded.EmbeddedLdapAutoConfiguration
import org.springframework.boot.test.autoconfigure.data.ldap.DataLdapTest

@DataLdapTest(excludeAutoConfiguration = [EmbeddedLdapAutoConfiguration::class])
class MyDataLdapTests {

    // ...

}
```

## REST Client được tự động cấu hình (Auto-configured REST Clients)

Bạn có thể dùng annotation `@RestClientTest` để kiểm thử các REST client. Theo mặc định, nó tự động cấu hình hỗ trợ Jackson, GSON, và Jsonb, cấu hình một `RestTemplateBuilder` và một `RestClient.Builder`, và thêm hỗ trợ cho `MockRestServiceServer`. Các bean `@Component` và `@ConfigurationProperties` thông thường không được quét khi dùng annotation `@RestClientTest`. `@EnableConfigurationProperties` có thể được dùng để đưa các bean `@ConfigurationProperties` vào.

> **MẸO (TIP):** Danh sách các thiết lập auto-configuration được bật bởi `@RestClientTest` có thể được [tìm thấy trong phụ lục](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#appendix.test-auto-configuration).

Các bean cụ thể mà bạn muốn kiểm thử nên được chỉ định bằng thuộc tính `value` hoặc `components` của `@RestClientTest`.

Khi dùng một `RestTemplateBuilder` trong các bean đang được kiểm thử và `RestTemplateBuilder.rootUri(String rootUri)` đã được gọi khi xây dựng `RestTemplate`, thì root URI nên được bỏ qua trong các kỳ vọng của `MockRestServiceServer`, như trong ví dụ sau:

**Java**

```java
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@RestClientTest(RemoteVehicleDetailsService.class)
class MyRestTemplateServiceTests {

    @Autowired
    private RemoteVehicleDetailsService service;

    @Autowired
    private MockRestServiceServer server;

    @Test
    void getVehicleDetailsWhenResultIsSuccessShouldReturnDetails() {
        this.server.expect(requestTo("/greet/details")).andRespond(withSuccess("hello", MediaType.TEXT_PLAIN));
        String greeting = this.service.callRestService();
        assertThat(greeting).isEqualTo("hello");
    }

}
```

**Kotlin**

```kotlin
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers
import org.springframework.test.web.client.response.MockRestResponseCreators

@RestClientTest(RemoteVehicleDetailsService::class)
class MyRestTemplateServiceTests(
    @Autowired val service: RemoteVehicleDetailsService,
    @Autowired val server: MockRestServiceServer) {

    @Test
    fun getVehicleDetailsWhenResultIsSuccessShouldReturnDetails() {
        server.expect(MockRestRequestMatchers.requestTo("/greet/details"))
            .andRespond(MockRestResponseCreators.withSuccess("hello", MediaType.TEXT_PLAIN))
        val greeting = service.callRestService()
        assertThat(greeting).isEqualTo("hello")
    }

}
```

Khi dùng một `RestClient.Builder` trong các bean đang được kiểm thử, hoặc khi dùng một `RestTemplateBuilder` mà không gọi `rootUri(String rootURI)`, URI đầy đủ phải được dùng trong các kỳ vọng của `MockRestServiceServer`, như trong ví dụ sau:

**Java**

```java
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@RestClientTest(RemoteVehicleDetailsService.class)
class MyRestClientServiceTests {

    @Autowired
    private RemoteVehicleDetailsService service;

    @Autowired
    private MockRestServiceServer server;

    @Test
    void getVehicleDetailsWhenResultIsSuccessShouldReturnDetails() {
        this.server.expect(requestTo("https://example.com/greet/details"))
            .andRespond(withSuccess("hello", MediaType.TEXT_PLAIN));
        String greeting = this.service.callRestService();
        assertThat(greeting).isEqualTo("hello");
    }

}
```

**Kotlin**

```kotlin
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers
import org.springframework.test.web.client.response.MockRestResponseCreators

@RestClientTest(RemoteVehicleDetailsService::class)
class MyRestClientServiceTests(
    @Autowired val service: RemoteVehicleDetailsService,
    @Autowired val server: MockRestServiceServer) {

    @Test
    fun getVehicleDetailsWhenResultIsSuccessShouldReturnDetails() {
        server.expect(MockRestRequestMatchers.requestTo("https://example.com/greet/details"))
            .andRespond(MockRestResponseCreators.withSuccess("hello", MediaType.TEXT_PLAIN))
        val greeting = service.callRestService()
        assertThat(greeting).isEqualTo("hello")
    }

}
```

## Test Spring REST Docs được tự động cấu hình (Auto-configured Spring REST Docs Tests)

Bạn có thể dùng annotation `@AutoConfigureRestDocs` để dùng [Spring REST Docs](https://spring.io/projects/spring-restdocs) trong test với Mock MVC, REST Assured, hoặc WebTestClient. Nó loại bỏ nhu cầu dùng JUnit extension trong Spring REST Docs.

`@AutoConfigureRestDocs` có thể được dùng để ghi đè thư mục output mặc định (`target/generated-snippets` nếu bạn dùng Maven hoặc `build/generated-snippets` nếu bạn dùng Gradle). Nó cũng có thể được dùng để cấu hình host, scheme và port xuất hiện trong mọi URI được tài liệu hóa.

### Test Spring REST Docs được tự động cấu hình với Mock MVC (Auto-configured Spring REST Docs Tests With Mock MVC)

`@AutoConfigureRestDocs` tùy chỉnh bean `MockMvc` để dùng Spring REST Docs khi kiểm thử các ứng dụng web dựa trên servlet. Bạn có thể inject nó bằng `@Autowired` và dùng nó trong test như bạn vẫn làm khi dùng Mock MVC và Spring REST Docs, như trong ví dụ sau:

**Java**

```java
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@AutoConfigureRestDocs
class MyUserDocumentationTests {

    @Autowired
    private MockMvc mvc;

    @Test
    void listUsers() throws Exception {
        this.mvc.perform(get("/users").accept(MediaType.TEXT_PLAIN))
            .andExpect(status().isOk())
            .andDo(document("list-users"));
    }

}
```

**Kotlin**

```kotlin
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.restdocs.mockmvc.MockMvcRestDocumentation
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers

@WebMvcTest(UserController::class)
@AutoConfigureRestDocs
class MyUserDocumentationTests(@Autowired val mvc: MockMvc) {

    @Test
    fun listUsers() {
        mvc.perform(MockMvcRequestBuilders.get("/users").accept(MediaType.TEXT_PLAIN))
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andDo(MockMvcRestDocumentation.document("list-users"))
    }

}
```

Nếu bạn cần kiểm soát cấu hình Spring REST Docs nhiều hơn những gì các thuộc tính của `@AutoConfigureRestDocs` cung cấp, bạn có thể dùng một bean `RestDocsMockMvcConfigurationCustomizer`, như trong ví dụ sau:

**Java**

```java
import org.springframework.boot.test.autoconfigure.restdocs.RestDocsMockMvcConfigurationCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.restdocs.mockmvc.MockMvcRestDocumentationConfigurer;
import org.springframework.restdocs.templates.TemplateFormats;

@TestConfiguration(proxyBeanMethods = false)
public class MyRestDocsConfiguration implements RestDocsMockMvcConfigurationCustomizer {

    @Override
    public void customize(MockMvcRestDocumentationConfigurer configurer) {
        configurer.snippets().withTemplateFormat(TemplateFormats.markdown());
    }

}
```

**Kotlin**

```kotlin
import org.springframework.boot.test.autoconfigure.restdocs.RestDocsMockMvcConfigurationCustomizer
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.restdocs.mockmvc.MockMvcRestDocumentationConfigurer
import org.springframework.restdocs.templates.TemplateFormats

@TestConfiguration(proxyBeanMethods = false)
class MyRestDocsConfiguration : RestDocsMockMvcConfigurationCustomizer {

    override fun customize(configurer: MockMvcRestDocumentationConfigurer) {
        configurer.snippets().withTemplateFormat(TemplateFormats.markdown())
    }

}
```

Nếu bạn muốn tận dụng hỗ trợ của Spring REST Docs cho thư mục output có tham số, bạn có thể tạo một bean `RestDocumentationResultHandler`. Auto-configuration gọi `alwaysDo` với result handler này, nhờ đó khiến mỗi lời gọi `MockMvc` tự động sinh ra các snippet mặc định. Ví dụ sau cho thấy một `RestDocumentationResultHandler` được định nghĩa:

**Java**

```java
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.restdocs.mockmvc.MockMvcRestDocumentation;
import org.springframework.restdocs.mockmvc.RestDocumentationResultHandler;

@TestConfiguration(proxyBeanMethods = false)
public class MyResultHandlerConfiguration {

    @Bean
    public RestDocumentationResultHandler restDocumentation() {
        return MockMvcRestDocumentation.document("{method-name}");
    }

}
```

**Kotlin**

```kotlin
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.restdocs.mockmvc.MockMvcRestDocumentation
import org.springframework.restdocs.mockmvc.RestDocumentationResultHandler

@TestConfiguration(proxyBeanMethods = false)
class MyResultHandlerConfiguration {

    @Bean
    fun restDocumentation(): RestDocumentationResultHandler {
        return MockMvcRestDocumentation.document("{method-name}")
    }

}
```

### Test Spring REST Docs được tự động cấu hình với WebTestClient (Auto-configured Spring REST Docs Tests With WebTestClient)

`@AutoConfigureRestDocs` cũng có thể được dùng với `WebTestClient` khi kiểm thử các ứng dụng web reactive. Bạn có thể inject nó bằng `@Autowired` và dùng nó trong test như bạn vẫn làm khi dùng `@WebFluxTest` và Spring REST Docs, như trong ví dụ sau:

**Java**

```java
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.springframework.restdocs.webtestclient.WebTestClientRestDocumentation.document;

@WebFluxTest
@AutoConfigureRestDocs
class MyUsersDocumentationTests {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void listUsers() {
        this.webTestClient
            .get().uri("/")
        .exchange()
        .expectStatus()
            .isOk()
        .expectBody()
            .consumeWith(document("list-users"));
    }

}
```

**Kotlin**

```kotlin
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.restdocs.webtestclient.WebTestClientRestDocumentation
import org.springframework.test.web.reactive.server.WebTestClient

@WebFluxTest
@AutoConfigureRestDocs
class MyUsersDocumentationTests(@Autowired val webTestClient: WebTestClient) {

    @Test
    fun listUsers() {
        webTestClient
            .get().uri("/")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .consumeWith(WebTestClientRestDocumentation.document("list-users"))
    }

}
```

Nếu bạn cần kiểm soát cấu hình Spring REST Docs nhiều hơn những gì các thuộc tính của `@AutoConfigureRestDocs` cung cấp, bạn có thể dùng một bean `RestDocsWebTestClientConfigurationCustomizer`, như trong ví dụ sau:

**Java**

```java
import org.springframework.boot.test.autoconfigure.restdocs.RestDocsWebTestClientConfigurationCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.restdocs.webtestclient.WebTestClientRestDocumentationConfigurer;

@TestConfiguration(proxyBeanMethods = false)
public class MyRestDocsConfiguration implements RestDocsWebTestClientConfigurationCustomizer {

    @Override
    public void customize(WebTestClientRestDocumentationConfigurer configurer) {
        configurer.snippets().withEncoding("UTF-8");
    }

}
```

**Kotlin**

```kotlin
import org.springframework.boot.test.autoconfigure.restdocs.RestDocsWebTestClientConfigurationCustomizer
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.restdocs.webtestclient.WebTestClientRestDocumentationConfigurer

@TestConfiguration(proxyBeanMethods = false)
class MyRestDocsConfiguration : RestDocsWebTestClientConfigurationCustomizer {

    override fun customize(configurer: WebTestClientRestDocumentationConfigurer) {
        configurer.snippets().withEncoding("UTF-8")
    }

}
```

Nếu bạn muốn tận dụng hỗ trợ của Spring REST Docs cho thư mục output có tham số, bạn có thể dùng một `WebTestClientBuilderCustomizer` để cấu hình một consumer cho mọi kết quả trao đổi entity (entity exchange result). Ví dụ sau cho thấy một `WebTestClientBuilderCustomizer` như vậy được định nghĩa:

**Java**

```java
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.reactive.server.WebTestClientBuilderCustomizer;
import org.springframework.context.annotation.Bean;

import static org.springframework.restdocs.webtestclient.WebTestClientRestDocumentation.document;

@TestConfiguration(proxyBeanMethods = false)
public class MyWebTestClientBuilderCustomizerConfiguration {

    @Bean
    public WebTestClientBuilderCustomizer restDocumentation() {
        return (builder) -> builder.entityExchangeResultConsumer(document("{method-name}"));
    }

}
```

**Kotlin**

```kotlin
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.reactive.server.WebTestClientBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.restdocs.webtestclient.WebTestClientRestDocumentation
import org.springframework.test.web.reactive.server.WebTestClient

@TestConfiguration(proxyBeanMethods = false)
class MyWebTestClientBuilderCustomizerConfiguration {

    @Bean
    fun restDocumentation(): WebTestClientBuilderCustomizer {
        return WebTestClientBuilderCustomizer { builder: WebTestClient.Builder ->
            builder.entityExchangeResultConsumer(
                WebTestClientRestDocumentation.document("{method-name}")
            )
        }
    }

}
```

### Test Spring REST Docs được tự động cấu hình với REST Assured (Auto-configured Spring REST Docs Tests With REST Assured)

`@AutoConfigureRestDocs` cung cấp cho test của bạn một bean `RequestSpecification`, được cấu hình sẵn để dùng Spring REST Docs. Bạn có thể inject nó bằng `@Autowired` và dùng nó trong test như bạn vẫn làm khi dùng REST Assured và Spring REST Docs, như trong ví dụ sau:

**Java**

```java
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.springframework.restdocs.restassured.RestAssuredRestDocumentation.document;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureRestDocs
class MyUserDocumentationTests {

    @Test
    void listUsers(@Autowired RequestSpecification documentationSpec, @LocalServerPort int port) {
        given(documentationSpec)
            .filter(document("list-users"))
        .when()
            .port(port)
            .get("/")
        .then().assertThat()
            .statusCode(is(200));
    }

}
```

**Kotlin**

```kotlin
import io.restassured.RestAssured
import io.restassured.specification.RequestSpecification
import org.hamcrest.Matchers
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.restdocs.restassured.RestAssuredRestDocumentation

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureRestDocs
class MyUserDocumentationTests {

    @Test
    fun listUsers(@Autowired documentationSpec: RequestSpecification?, @LocalServerPort port: Int) {
        RestAssured.given(documentationSpec)
            .filter(RestAssuredRestDocumentation.document("list-users"))
            .`when`()
            .port(port)["/"]
            .then().assertThat()
            .statusCode(Matchers.`is`(200))
    }

}
```

Nếu bạn cần kiểm soát cấu hình Spring REST Docs nhiều hơn những gì các thuộc tính của `@AutoConfigureRestDocs` cung cấp, có thể dùng một bean `RestDocsRestAssuredConfigurationCustomizer`, như trong ví dụ sau:

**Java**

```java
import org.springframework.boot.test.autoconfigure.restdocs.RestDocsRestAssuredConfigurationCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.restdocs.restassured.RestAssuredRestDocumentationConfigurer;
import org.springframework.restdocs.templates.TemplateFormats;

@TestConfiguration(proxyBeanMethods = false)
public class MyRestDocsConfiguration implements RestDocsRestAssuredConfigurationCustomizer {

    @Override
    public void customize(RestAssuredRestDocumentationConfigurer configurer) {
        configurer.snippets().withTemplateFormat(TemplateFormats.markdown());
    }

}
```

**Kotlin**

```kotlin
import org.springframework.boot.test.autoconfigure.restdocs.RestDocsRestAssuredConfigurationCustomizer
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.restdocs.restassured.RestAssuredRestDocumentationConfigurer
import org.springframework.restdocs.templates.TemplateFormats

@TestConfiguration(proxyBeanMethods = false)
class MyRestDocsConfiguration : RestDocsRestAssuredConfigurationCustomizer {

    override fun customize(configurer: RestAssuredRestDocumentationConfigurer) {
        configurer.snippets().withTemplateFormat(TemplateFormats.markdown())
    }

}
```

## Test Spring Web Services được tự động cấu hình (Auto-configured Spring Web Services Tests)

### Test Spring Web Services Client được tự động cấu hình (Auto-configured Spring Web Services Client Tests)

Bạn có thể dùng `@WebServiceClientTest` để kiểm thử các ứng dụng gọi web service bằng project Spring Web Services. Theo mặc định, nó cấu hình một bean mock `WebServiceServer` và tự động tùy chỉnh `WebServiceTemplateBuilder` của bạn. (Để biết thêm về việc dùng Web Services với Spring Boot, xem "[Web Services](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#io.webservices)".)

> **MẸO (TIP):** Danh sách các thiết lập auto-configuration được bật bởi `@WebServiceClientTest` có thể được [tìm thấy trong phụ lục](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#appendix.test-auto-configuration).

Ví dụ sau cho thấy annotation `@WebServiceClientTest` đang được sử dụng:

**Java**

```java
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.webservices.client.WebServiceClientTest;
import org.springframework.ws.test.client.MockWebServiceServer;
import org.springframework.xml.transform.StringSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.ws.test.client.RequestMatchers.payload;
import static org.springframework.ws.test.client.ResponseCreators.withPayload;

@WebServiceClientTest(SomeWebService.class)
class MyWebServiceClientTests {

    @Autowired
    private MockWebServiceServer server;

    @Autowired
    private SomeWebService someWebService;

    @Test
    void mockServerCall() {
        this.server
            .expect(payload(new StringSource("<request/>")))
            .andRespond(withPayload(new StringSource("<response><status>200</status></response>")));
        assertThat(this.someWebService.test())
            .extracting(Response::getStatus)
            .isEqualTo(200);
    }

}
```

**Kotlin**

```kotlin
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.webservices.client.WebServiceClientTest
import org.springframework.ws.test.client.MockWebServiceServer
import org.springframework.ws.test.client.RequestMatchers
import org.springframework.ws.test.client.ResponseCreators
import org.springframework.xml.transform.StringSource

@WebServiceClientTest(SomeWebService::class)
class MyWebServiceClientTests(@Autowired val server: MockWebServiceServer, @Autowired val someWebService: SomeWebService) {

    @Test
    fun mockServerCall() {
        server
            .expect(RequestMatchers.payload(StringSource("<request/>")))
            .andRespond(ResponseCreators.withPayload(StringSource("<response><status>200</status></response>")))
        assertThat(this.someWebService.test()).extracting(Response::status).isEqualTo(200)
    }

}
```

### Test Spring Web Services Server được tự động cấu hình (Auto-configured Spring Web Services Server Tests)

Bạn có thể dùng `@WebServiceServerTest` để kiểm thử các ứng dụng hiện thực web service bằng project Spring Web Services. Theo mặc định, nó cấu hình một bean `MockWebServiceClient` có thể dùng để gọi các endpoint web service của bạn. (Để biết thêm về việc dùng Web Services với Spring Boot, xem "[Web Services](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#io.webservices)".)

> **MẸO (TIP):** Danh sách các thiết lập auto-configuration được bật bởi `@WebServiceServerTest` có thể được [tìm thấy trong phụ lục](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#appendix.test-auto-configuration).

Ví dụ sau cho thấy annotation `@WebServiceServerTest` đang được sử dụng:

**Java**

```java
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.webservices.server.WebServiceServerTest;
import org.springframework.ws.test.server.MockWebServiceClient;
import org.springframework.ws.test.server.RequestCreators;
import org.springframework.ws.test.server.ResponseMatchers;
import org.springframework.xml.transform.StringSource;

@WebServiceServerTest(ExampleEndpoint.class)
class MyWebServiceServerTests {

    @Autowired
    private MockWebServiceClient client;

    @Test
    void mockServerCall() {
        this.client
            .sendRequest(RequestCreators.withPayload(new StringSource("<ExampleRequest/>")))
            .andExpect(ResponseMatchers.payload(new StringSource("<ExampleResponse>42</ExampleResponse>")));
    }

}
```

**Kotlin**

```kotlin
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.webservices.server.WebServiceServerTest
import org.springframework.ws.test.server.MockWebServiceClient
import org.springframework.ws.test.server.RequestCreators
import org.springframework.ws.test.server.ResponseMatchers
import org.springframework.xml.transform.StringSource

@WebServiceServerTest(ExampleEndpoint::class)
class MyWebServiceServerTests(@Autowired val client: MockWebServiceClient) {

    @Test
    fun mockServerCall() {
        client
            .sendRequest(RequestCreators.withPayload(StringSource("<ExampleRequest/>")))
            .andExpect(ResponseMatchers.payload(StringSource("<ExampleResponse>42</ExampleResponse>")))
    }

}
```

## Auto-configuration bổ sung và cắt lát (Additional Auto-configuration and Slicing)

Mỗi lát cắt cung cấp một hoặc nhiều annotation `@AutoConfigure…` định nghĩa rõ các auto-configuration nên được bao gồm như một phần của lát cắt. Có thể thêm các auto-configuration bổ sung cho từng test bằng cách tạo một annotation `@AutoConfigure…` tùy chỉnh hoặc bằng cách thêm `@ImportAutoConfiguration` vào test như trong ví dụ sau:

**Java**

```java
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.integration.IntegrationAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;

@JdbcTest
@ImportAutoConfiguration(IntegrationAutoConfiguration.class)
class MyJdbcTests {

}
```

**Kotlin**

```kotlin
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.autoconfigure.integration.IntegrationAutoConfiguration
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest

@JdbcTest
@ImportAutoConfiguration(IntegrationAutoConfiguration::class)
class MyJdbcTests
```

> **LƯU Ý (NOTE):** Hãy đảm bảo không dùng annotation `@Import` thông thường để import các auto-configuration, vì chúng được Spring Boot xử lý theo một cách đặc thù.

Ngoài ra, các auto-configuration bổ sung có thể được thêm cho mọi lần dùng một slice annotation bằng cách đăng ký chúng trong một file lưu trong `META-INF/spring`, như trong ví dụ sau:

**META-INF/spring/org.springframework.boot.test.autoconfigure.jdbc.JdbcTest.imports**

```
com.example.IntegrationAutoConfiguration
```

Trong ví dụ này, `com.example.IntegrationAutoConfiguration` được bật trên mọi test được đánh annotation `@JdbcTest`.

> **MẸO (TIP):** Bạn có thể dùng comment với `#` trong file này.

> **MẸO (TIP):** Một lát cắt hoặc annotation `@AutoConfigure…` có thể được tùy chỉnh theo cách này miễn là nó được meta-annotate bằng `@ImportAutoConfiguration`.

## Cấu hình của người dùng và cắt lát (User Configuration and Slicing)

Nếu bạn [tổ chức code](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#using.structuring-your-code) một cách hợp lý, class `@SpringBootApplication` của bạn sẽ [được dùng theo mặc định](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.testing.spring-boot-applications.detecting-configuration) làm cấu hình cho các test.

Khi đó, điều quan trọng là không làm lộn xộn class chính của ứng dụng bằng các thiết lập cấu hình đặc thù cho một khu vực chức năng cụ thể.

Giả sử bạn đang dùng Spring Data MongoDB, bạn dựa vào auto-configuration cho nó, và bạn đã bật auditing. Bạn có thể định nghĩa `@SpringBootApplication` như sau:

**Java**

```java
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

@SpringBootApplication
@EnableMongoAuditing
public class MyApplication {

    // ...

}
```

**Kotlin**

```kotlin
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.data.mongodb.config.EnableMongoAuditing

@SpringBootApplication
@EnableMongoAuditing
class MyApplication {

    // ...

}
```

Vì class này là nguồn cấu hình cho test, mọi slice test thực sự đều cố gắng bật Mongo auditing, điều chắc chắn không phải là điều bạn muốn. Một cách tiếp cận được khuyến nghị là chuyển cấu hình đặc thù cho khu vực đó sang một class `@Configuration` riêng ở cùng cấp với ứng dụng, như trong ví dụ sau:

**Java**

```java
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

@Configuration(proxyBeanMethods = false)
@EnableMongoAuditing
public class MyMongoConfiguration {

    // ...

}
```

**Kotlin**

```kotlin
import org.springframework.context.annotation.Configuration
import org.springframework.data.mongodb.config.EnableMongoAuditing

@Configuration(proxyBeanMethods = false)
@EnableMongoAuditing
class MyMongoConfiguration {

    // ...

}
```

> **LƯU Ý (NOTE):** Tùy thuộc vào độ phức tạp của ứng dụng, bạn có thể có một class `@Configuration` duy nhất cho các tùy chỉnh hoặc một class cho mỗi khu vực domain. Cách tiếp cận sau cho phép bạn bật nó trong một test của mình, nếu cần, bằng annotation `@Import`. Xem [phần how-to này](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.testing.slice-tests) để biết thêm chi tiết về thời điểm bạn có thể muốn bật các class `@Configuration` cụ thể cho slice test.

Các test slice loại trừ các class `@Configuration` khỏi việc quét. Ví dụ, với một `@WebMvcTest`, cấu hình sau sẽ không đưa bean `WebMvcConfigurer` đã cho vào application context được nạp bởi test slice:

**Java**

```java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
public class MyWebConfiguration {

    @Bean
    public WebMvcConfigurer testConfigurer() {
        return new WebMvcConfigurer() {
            // ...
        };
    }

}
```

**Kotlin**

```kotlin
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration(proxyBeanMethods = false)
class MyWebConfiguration {

    @Bean
    fun testConfigurer(): WebMvcConfigurer {
        return object : WebMvcConfigurer {
            // ...
        }
    }

}
```

Tuy nhiên, cấu hình dưới đây sẽ khiến `WebMvcConfigurer` tùy chỉnh được nạp bởi test slice.

**Java**

```java
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Component
public class MyWebMvcConfigurer implements WebMvcConfigurer {

    // ...

}
```

**Kotlin**

```kotlin
import org.springframework.stereotype.Component
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Component
class MyWebMvcConfigurer : WebMvcConfigurer {

    // ...

}
```

Một nguồn gây nhầm lẫn khác là classpath scanning. Giả sử rằng, mặc dù bạn đã tổ chức code một cách hợp lý, bạn cần quét thêm một package. Ứng dụng của bạn có thể trông giống như đoạn code sau:

**Java**

```java
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan({ "com.example.app", "com.example.another" })
public class MyApplication {

    // ...

}
```

**Kotlin**

```kotlin
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.annotation.ComponentScan

@SpringBootApplication
@ComponentScan("com.example.app", "com.example.another")
class MyApplication {

    // ...

}
```

Làm như vậy thực chất sẽ ghi đè chỉ thị component scan mặc định, với tác dụng phụ là quét hai package đó bất kể lát cắt bạn chọn là gì. Ví dụ, một `@DataJpaTest` dường như đột nhiên quét các component và cấu hình người dùng của ứng dụng. Một lần nữa, chuyển chỉ thị tùy chỉnh sang một class riêng là một cách tốt để khắc phục vấn đề này.

> **MẸO (TIP):** Nếu đây không phải là lựa chọn cho bạn, bạn có thể tạo một `@SpringBootConfiguration` ở đâu đó trong cây phân cấp của test để nó được dùng thay thế. Ngoài ra, bạn có thể chỉ định một nguồn cho test, điều này sẽ vô hiệu hóa hành vi tìm kiếm nguồn mặc định.

## Sử dụng Spock để kiểm thử ứng dụng Spring Boot (Using Spock to Test Spring Boot Applications)

Spock 2.2 trở lên có thể được dùng để kiểm thử một ứng dụng Spring Boot. Để làm vậy, hãy thêm dependency tới phiên bản `-groovy-4.0` của module `spock-spring` của Spock vào bản build của ứng dụng. `spock-spring` tích hợp test framework của Spring vào Spock. Xem [tài liệu về module Spring của Spock](https://spockframework.org/spock/docs/2.2-M1/modules.html#_spring_module) để biết thêm chi tiết.

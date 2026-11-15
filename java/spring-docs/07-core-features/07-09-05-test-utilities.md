# 7.9.5. Các tiện ích kiểm thử (Test Utilities)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.testing.utilities>

Một vài class tiện ích kiểm thử thường hữu ích khi kiểm thử ứng dụng được đóng gói như một phần của `spring-boot`.

## ConfigDataApplicationContextInitializer

`ConfigDataApplicationContextInitializer` là một `ApplicationContextInitializer` mà bạn có thể áp dụng cho test để nạp các file `application.properties` của Spring Boot. Bạn có thể dùng nó khi không cần toàn bộ các tính năng do `@SpringBootTest` cung cấp, như trong ví dụ sau:

**Java**

```java
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.test.context.ContextConfiguration;

@ContextConfiguration(classes = Config.class, initializers = ConfigDataApplicationContextInitializer.class)
class MyConfigFileTests {

    // ...

}
```

**Kotlin**

```kotlin
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer
import org.springframework.test.context.ContextConfiguration

@ContextConfiguration(classes = [Config::class], initializers = [ConfigDataApplicationContextInitializer::class])
class MyConfigFileTests {

    // ...

}
```

> **LƯU Ý (NOTE):** Chỉ dùng `ConfigDataApplicationContextInitializer` một mình không cung cấp hỗ trợ cho việc inject `@Value("${…}")`. Nhiệm vụ duy nhất của nó là đảm bảo các file `application.properties` được nạp vào `Environment` của Spring. Để có hỗ trợ `@Value`, bạn cần cấu hình thêm một `PropertySourcesPlaceholderConfigurer` hoặc dùng `@SpringBootTest`, vốn tự động cấu hình một cái cho bạn.

## TestPropertyValues

`TestPropertyValues` cho phép bạn nhanh chóng thêm property vào một `ConfigurableEnvironment` hoặc `ConfigurableApplicationContext`. Bạn có thể gọi nó với các chuỗi `key=value`, như sau:

**Java**

```java
import org.junit.jupiter.api.Test;

import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class MyEnvironmentTests {

    @Test
    void testPropertySources() {
        MockEnvironment environment = new MockEnvironment();
        TestPropertyValues.of("org=Spring", "name=Boot").applyTo(environment);
        assertThat(environment.getProperty("name")).isEqualTo("Boot");
    }

}
```

**Kotlin**

```kotlin
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.util.TestPropertyValues
import org.springframework.mock.env.MockEnvironment

class MyEnvironmentTests {

    @Test
    fun testPropertySources() {
        val environment = MockEnvironment()
        TestPropertyValues.of("org=Spring", "name=Boot").applyTo(environment)
        assertThat(environment.getProperty("name")).isEqualTo("Boot")
    }

}
```

## OutputCapture

`OutputCapture` là một JUnit `Extension` mà bạn có thể dùng để bắt output của `System.out` và `System.err`. Để dùng nó, hãy thêm `@ExtendWith(OutputCaptureExtension.class)` và inject `CapturedOutput` làm tham số cho constructor của class test hoặc phương thức test như sau:

**Java**

```java
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class MyOutputCaptureTests {

    @Test
    void testName(CapturedOutput output) {
        System.out.println("Hello World!");
        assertThat(output).contains("World");
    }

}
```

**Kotlin**

```kotlin
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension

@ExtendWith(OutputCaptureExtension::class)
class MyOutputCaptureTests {

    @Test
    fun testName(output: CapturedOutput?) {
        println("Hello World!")
        assertThat(output).contains("World")
    }

}
```

## TestRestTemplate

`TestRestTemplate` là một lựa chọn thay thế tiện lợi cho `RestTemplate` của Spring, hữu ích trong các integration test. Bạn có thể lấy một template thông thường hoặc một template gửi xác thực HTTP Basic (với username và password). Trong cả hai trường hợp, template đều chịu lỗi tốt (fault tolerant). Điều này có nghĩa là nó hoạt động theo cách thân thiện với test bằng cách không ném exception với các lỗi 4xx và 5xx. Thay vào đó, những lỗi như vậy có thể được phát hiện thông qua `ResponseEntity` được trả về và status code của nó.

> **MẸO (TIP):** Spring Framework 5.0 cung cấp một `WebTestClient` mới hoạt động cho [các integration test WebFlux](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.testing.spring-boot-applications.spring-webflux-tests) và cả [kiểm thử end-to-end cho WebFlux và MVC](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.testing.spring-boot-applications.with-running-server). Nó cung cấp một API fluent cho assertion, không giống như `TestRestTemplate`.

Khuyến nghị, nhưng không bắt buộc, dùng Apache HTTP Client (phiên bản 5.1 trở lên). Nếu bạn có nó trên classpath, `TestRestTemplate` sẽ phản hồi bằng cách cấu hình client một cách phù hợp. Nếu bạn dùng HTTP client của Apache, một số tính năng thân thiện với test bổ sung sẽ được bật:

- Redirect không được theo (để bạn có thể assert vị trí của response).
- Cookie bị bỏ qua (để template không có trạng thái - stateless).

`TestRestTemplate` có thể được khởi tạo trực tiếp trong integration test, như trong ví dụ sau:

**Java**

```java
import org.junit.jupiter.api.Test;

import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class MyTests {

    private final TestRestTemplate template = new TestRestTemplate();

    @Test
    void testRequest() {
        ResponseEntity<String> headers = this.template.getForEntity("https://myhost.example.com/example", String.class);
        assertThat(headers.getHeaders().getLocation()).hasHost("other.example.com");
    }

}
```

**Kotlin**

```kotlin
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.web.client.TestRestTemplate

class MyTests {

    private val template = TestRestTemplate()

    @Test
    fun testRequest() {
        val headers = template.getForEntity("https://myhost.example.com/example", String::class.java)
        assertThat(headers.headers.location).hasHost("other.example.com")
    }

}
```

Ngoài ra, nếu bạn dùng annotation `@SpringBootTest` với `WebEnvironment.RANDOM_PORT` hoặc `WebEnvironment.DEFINED_PORT`, bạn có thể inject một `TestRestTemplate` đã được cấu hình đầy đủ và bắt đầu sử dụng nó. Nếu cần, các tùy chỉnh bổ sung có thể được áp dụng thông qua bean `RestTemplateBuilder`. Mọi URL không chỉ định host và port sẽ tự động kết nối tới server nhúng, như trong ví dụ sau:

**Java**

```java
import java.time.Duration;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class MySpringBootTests {

    @Autowired
    private TestRestTemplate template;

    @Test
    void testRequest() {
        HttpHeaders headers = this.template.getForEntity("/example", String.class).getHeaders();
        assertThat(headers.getLocation()).hasHost("other.example.com");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class RestTemplateBuilderConfiguration {

        @Bean
        RestTemplateBuilder restTemplateBuilder() {
            return new RestTemplateBuilder().setConnectTimeout(Duration.ofSeconds(1))
                .setReadTimeout(Duration.ofSeconds(1));
        }

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
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import java.time.Duration

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class MySpringBootTests(@Autowired val template: TestRestTemplate) {

    @Test
    fun testRequest() {
        val headers = template.getForEntity("/example", String::class.java).headers
        assertThat(headers.location).hasHost("other.example.com")
    }

    @TestConfiguration(proxyBeanMethods = false)
    internal class RestTemplateBuilderConfiguration {

        @Bean
        fun restTemplateBuilder(): RestTemplateBuilder {
            return RestTemplateBuilder().setConnectTimeout(Duration.ofSeconds(1))
                .setReadTimeout(Duration.ofSeconds(1))
        }

    }

}
```

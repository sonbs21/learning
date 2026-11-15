# 7.13. Hỗ trợ Kotlin (Kotlin Support)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.kotlin>

[Kotlin](https://kotlinlang.org) là một ngôn ngữ kiểu tĩnh (statically-typed) nhắm tới JVM (và các nền tảng khác), cho phép viết code ngắn gọn và thanh lịch, đồng thời cung cấp khả năng [tương tác (interoperability)](https://kotlinlang.org/docs/reference/java-interop.html) với các thư viện hiện có được viết bằng Java.

Spring Boot cung cấp hỗ trợ Kotlin bằng cách tận dụng hỗ trợ trong các project Spring khác như Spring Framework, Spring Data, và Reactor. Xem [tài liệu hỗ trợ Kotlin của Spring Framework](https://docs.spring.io/spring-framework/reference/6.1/languages/kotlin.html) để biết thêm thông tin.

Cách dễ nhất để bắt đầu với Spring Boot và Kotlin là làm theo [hướng dẫn toàn diện này](https://spring.io/guides/tutorials/spring-boot-kotlin/). Bạn có thể tạo các project Kotlin mới bằng [start.spring.io](https://start.spring.io/#!language=kotlin). Hãy thoải mái tham gia kênh #spring của [Kotlin Slack](https://slack.kotlinlang.org/) hoặc đặt câu hỏi với các thẻ `spring` và `kotlin` trên [Stack Overflow](https://stackoverflow.com/questions/tagged/spring+kotlin) nếu bạn cần hỗ trợ.

## 7.13.1. Yêu cầu (Requirements)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.kotlin.requirements>

Spring Boot yêu cầu tối thiểu Kotlin 1.7.x và quản lý một phiên bản Kotlin phù hợp thông qua dependency management. Để dùng Kotlin, `org.jetbrains.kotlin:kotlin-stdlib` và `org.jetbrains.kotlin:kotlin-reflect` phải có mặt trên classpath. Các biến thể `kotlin-stdlib-jdk7` và `kotlin-stdlib-jdk8` của `kotlin-stdlib` cũng có thể được sử dụng.

Vì [các class Kotlin mặc định là final](https://discuss.kotlinlang.org/t/classes-final-by-default/166), có thể bạn sẽ muốn cấu hình plugin [kotlin-spring](https://kotlinlang.org/docs/reference/compiler-plugins.html#spring-support) để tự động "mở" (open) các class được đánh annotation Spring để chúng có thể được tạo proxy.

[Module Kotlin của Jackson](https://github.com/FasterXML/jackson-module-kotlin) là bắt buộc để serialize / deserialize dữ liệu JSON trong Kotlin. Nó được tự động đăng ký khi được tìm thấy trên classpath. Một thông điệp cảnh báo được ghi log nếu có Jackson và Kotlin nhưng không có module Jackson Kotlin.

> **MẸO (TIP):** Các dependency và plugin này được cung cấp mặc định nếu bạn khởi tạo một project Kotlin trên [start.spring.io](https://start.spring.io/#!language=kotlin).

## 7.13.2. An toàn null (Null-safety)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.kotlin.null-safety>

Một trong những tính năng chính của Kotlin là [null-safety](https://kotlinlang.org/docs/reference/null-safety.html). Nó xử lý các giá trị `null` tại thời điểm biên dịch thay vì đẩy vấn đề sang runtime và gặp phải `NullPointerException`. Điều này giúp loại bỏ một nguồn lỗi phổ biến mà không phải trả giá cho các wrapper như `Optional`. Kotlin cũng cho phép dùng các cấu trúc hàm (functional construct) với các giá trị nullable như được mô tả trong [hướng dẫn toàn diện về null-safety trong Kotlin](https://www.baeldung.com/kotlin-null-safety) này.

Mặc dù Java không cho phép biểu diễn null-safety trong hệ thống kiểu của nó, Spring Framework, Spring Data, và Reactor hiện cung cấp null-safety cho API của chúng thông qua các annotation thân thiện với công cụ. Theo mặc định, các kiểu từ Java API được dùng trong Kotlin được nhận diện là [platform type](https://kotlinlang.org/docs/reference/java-interop.html#null-safety-and-platform-types), với các kiểm tra null được nới lỏng. [Hỗ trợ của Kotlin cho các annotation JSR 305](https://kotlinlang.org/docs/reference/java-interop.html#jsr-305-support) kết hợp với các annotation về khả năng null (nullability) cung cấp null-safety cho Spring API liên quan trong Kotlin.

Các kiểm tra JSR 305 có thể được cấu hình bằng cách thêm cờ compiler `-Xjsr305` với các tùy chọn sau: `-Xjsr305={strict|warn|ignore}`. Hành vi mặc định giống như `-Xjsr305=warn`. Giá trị `strict` là bắt buộc để null-safety được tính đến trong các kiểu Kotlin được suy ra từ Spring API, nhưng nên được dùng với hiểu biết rằng các khai báo nullability của Spring API có thể thay đổi ngay cả giữa các bản phát hành nhỏ (minor release) và nhiều kiểm tra hơn có thể được thêm vào trong tương lai.

> **CẢNH BÁO (WARNING):** Khả năng null của tham số kiểu generic, varargs và phần tử mảng chưa được hỗ trợ. Xem [SPR-15942](https://jira.spring.io/browse/SPR-15942) để có thông tin cập nhật. Cũng lưu ý rằng API của chính Spring Boot [chưa được đánh annotation](https://github.com/spring-projects/spring-boot/issues/10712).

## 7.13.3. Kotlin API

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.kotlin.api>

### runApplication

Spring Boot cung cấp một cách viết đặc trưng (idiomatic) để chạy ứng dụng bằng `runApplication<MyApplication>(*args)`, như trong ví dụ sau:

```kotlin
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class MyApplication

fun main(args: Array<String>) {
    runApplication<MyApplication>(*args)
}
```

Đây là một sự thay thế trực tiếp (drop-in replacement) cho `SpringApplication.run(MyApplication::class.java, *args)`. Nó cũng cho phép tùy chỉnh ứng dụng như trong ví dụ sau:

```kotlin
runApplication<MyApplication>(*args) {
    setBannerMode(OFF)
}
```

### Extension (Extensions)

Các [extension](https://kotlinlang.org/docs/reference/extensions.html) của Kotlin cung cấp khả năng mở rộng các class hiện có với chức năng bổ sung. Spring Boot Kotlin API tận dụng các extension này để thêm các tiện ích đặc thù cho Kotlin vào các API hiện có.

Các extension cho `TestRestTemplate`, tương tự như những extension do Spring Framework cung cấp cho `RestOperations` trong Spring Framework, được cung cấp. Ngoài các tiện ích khác, các extension này cho phép tận dụng các tham số kiểu reified của Kotlin.

## 7.13.4. Quản lý dependency (Dependency management)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.kotlin.dependency-management>

Để tránh trộn lẫn các phiên bản khác nhau của dependency Kotlin trên classpath, Spring Boot import BOM của Kotlin.

Với Maven, phiên bản Kotlin có thể được tùy chỉnh bằng cách đặt property `kotlin.version`, và plugin management được cung cấp cho `kotlin-maven-plugin`. Với Gradle, plugin Spring Boot tự động căn chỉnh `kotlin.version` theo phiên bản của plugin Kotlin.

Spring Boot cũng quản lý phiên bản của các dependency Coroutines bằng cách import BOM Kotlin Coroutines. Phiên bản có thể được tùy chỉnh bằng cách đặt property `kotlin-coroutines.version`.

> **MẸO (TIP):** Dependency `org.jetbrains.kotlinx:kotlinx-coroutines-reactor` được cung cấp mặc định nếu bạn khởi tạo một project Kotlin với ít nhất một dependency reactive trên [start.spring.io](https://start.spring.io/#!language=kotlin).

## 7.13.5. @ConfigurationProperties

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.kotlin.configuration-properties>

`@ConfigurationProperties`, khi được dùng kết hợp với [constructor binding](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.external-config.typesafe-configuration-properties.constructor-binding), hỗ trợ các class có property `val` bất biến, như trong ví dụ sau:

```kotlin
@ConfigurationProperties("example.kotlin")
data class KotlinExampleProperties(
        val name: String,
        val description: String,
        val myService: MyService) {

    data class MyService(
            val apiToken: String,
            val uri: URI
    )
}
```

> **MẸO (TIP):** Để sinh [metadata của riêng bạn](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#appendix.configuration-metadata.annotation-processor) bằng annotation processor, [`kapt` nên được cấu hình](https://kotlinlang.org/docs/reference/kapt.html) với dependency `spring-boot-configuration-processor`. Lưu ý rằng một số tính năng (chẳng hạn như phát hiện giá trị mặc định hoặc các mục deprecated) không hoạt động do những hạn chế trong mô hình mà kapt cung cấp.

## 7.13.6. Kiểm thử (Testing)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.kotlin.testing>

Mặc dù có thể dùng JUnit 4 để kiểm thử code Kotlin, JUnit 5 được cung cấp mặc định và được khuyến nghị. JUnit 5 cho phép một class test được khởi tạo một lần và tái sử dụng cho tất cả các test của class đó. Điều này cho phép dùng các annotation `@BeforeAll` và `@AfterAll` trên các phương thức không static, rất phù hợp với Kotlin.

Để mock các class Kotlin, [MockK](https://mockk.io/) được khuyến nghị. Nếu bạn cần tương đương `MockK` của các [annotation `@MockBean` và `@SpyBean`](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.testing.spring-boot-applications.mocking-beans) đặc thù cho Mockito, bạn có thể dùng [SpringMockK](https://github.com/Ninja-Squad/springmockk), cung cấp các annotation tương tự là `@MockkBean` và `@SpykBean`.

## 7.13.7. Tài nguyên (Resources)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.kotlin.resources>

### Đọc thêm (Further reading)

- [Tài liệu tham khảo ngôn ngữ Kotlin (Kotlin language reference)](https://kotlinlang.org/docs/reference/)
- [Kotlin Slack](https://kotlinlang.slack.com/) (với một kênh #spring chuyên biệt)
- [Stack Overflow với các thẻ `spring` và `kotlin`](https://stackoverflow.com/questions/tagged/spring+kotlin)
- [Thử Kotlin trên trình duyệt (Try Kotlin in your browser)](https://try.kotlinlang.org/)
- [Blog Kotlin (Kotlin blog)](https://blog.jetbrains.com/kotlin/)
- [Awesome Kotlin](https://kotlin.link/)
- [Hướng dẫn: xây dựng ứng dụng web với Spring Boot và Kotlin (Tutorial: building web applications with Spring Boot and Kotlin)](https://spring.io/guides/tutorials/spring-boot-kotlin/)
- [Phát triển ứng dụng Spring Boot với Kotlin (Developing Spring Boot applications with Kotlin)](https://spring.io/blog/2016/02/15/developing-spring-boot-applications-with-kotlin)
- [Một Geospatial Messenger với Kotlin, Spring Boot và PostgreSQL (A Geospatial Messenger with Kotlin, Spring Boot and PostgreSQL)](https://spring.io/blog/2016/03/20/a-geospatial-messenger-with-kotlin-spring-boot-and-postgresql)
- [Giới thiệu hỗ trợ Kotlin trong Spring Framework 5.0 (Introducing Kotlin support in Spring Framework 5.0)](https://spring.io/blog/2017/01/04/introducing-kotlin-support-in-spring-framework-5-0)
- [Kotlin API của Spring Framework 5, theo phong cách hàm (Spring Framework 5 Kotlin APIs, the functional way)](https://spring.io/blog/2017/08/01/spring-framework-5-kotlin-apis-the-functional-way)

### Ví dụ (Examples)

- [spring-boot-kotlin-demo](https://github.com/sdeleuze/spring-boot-kotlin-demo): project Spring Boot + Spring Data JPA thông thường
- [mixit](https://github.com/mixitconf/mixit): Spring Boot 2 + WebFlux + Reactive Spring Data MongoDB
- [spring-kotlin-fullstack](https://github.com/sdeleuze/spring-kotlin-fullstack): ví dụ fullstack WebFlux Kotlin với Kotlin2js cho frontend thay vì JavaScript hoặc TypeScript
- [spring-petclinic-kotlin](https://github.com/spring-petclinic/spring-petclinic-kotlin): phiên bản Kotlin của ứng dụng mẫu Spring PetClinic
- [spring-kotlin-deepdive](https://github.com/sdeleuze/spring-kotlin-deepdive): một quá trình chuyển đổi từng bước từ Boot 1.0 + Java sang Boot 2.0 + Kotlin
- [spring-boot-coroutines-demo](https://github.com/sdeleuze/spring-boot-coroutines-demo): project mẫu về Coroutines

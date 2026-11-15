# 8.6.2. GraphQL RuntimeWiring

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#web.graphql.runtimewiring>

`RuntimeWiring.Builder` của GraphQL Java có thể được dùng để đăng ký các scalar type tùy chỉnh, directive, type resolver, `DataFetcher`, và nhiều thứ khác. Bạn có thể khai báo các bean `RuntimeWiringConfigurer` trong cấu hình Spring để truy cập `RuntimeWiring.Builder`. Spring Boot phát hiện các bean như vậy và thêm chúng vào [GraphQlSource builder](https://docs.spring.io/spring-graphql/reference/1.2/#execution-graphqlsource).

Tuy nhiên, thông thường các ứng dụng sẽ không hiện thực trực tiếp `DataFetcher` mà thay vào đó sẽ tạo các [controller được đánh annotation](https://docs.spring.io/spring-graphql/reference/1.2/#controllers). Spring Boot sẽ tự động phát hiện các class `@Controller` có các phương thức handler được đánh annotation và đăng ký chúng thành các `DataFetcher`. Đây là một implementation mẫu cho query greeting của chúng ta với một class `@Controller`:

**Java**

```java
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

@Controller
public class GreetingController {

    @QueryMapping
    public String greeting(@Argument String name) {
        return "Hello, " + name + "!";
    }

}
```

**Kotlin**

```kotlin
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.stereotype.Controller

@Controller
class GreetingController {

    @QueryMapping
    fun greeting(@Argument name: String): String {
        return "Hello, $name!"
    }

}
```

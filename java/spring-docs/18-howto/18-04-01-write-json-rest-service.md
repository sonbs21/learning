# 18.4.1. Viết một JSON REST service (Write a JSON REST Service)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.spring-mvc.write-json-rest-service>

Bất kỳ Spring `@RestController` nào trong một ứng dụng Spring Boot đều sẽ render JSON response theo mặc định miễn là Jackson2 có trên classpath, như trong ví dụ sau:

**Java**

```java
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MyController {

    @RequestMapping("/thing")
    public MyThing thing() {
        return new MyThing();
    }

}
```

**Kotlin**

```kotlin
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class MyController {

    @RequestMapping("/thing")
    fun thing(): MyThing {
        return MyThing()
    }

}
```

Miễn là `MyThing` có thể được Jackson2 serialize (đúng với một POJO thông thường hoặc một Groovy object), thì `[localhost:8080/thing](http://localhost:8080/thing)` sẽ trả về biểu diễn JSON của nó theo mặc định. Lưu ý rằng, trong trình duyệt, đôi khi bạn có thể thấy XML response, vì các trình duyệt có xu hướng gửi accept header ưu tiên XML.

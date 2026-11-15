# 15.2.1. Ứng dụng mẫu (Sample Application)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#native-image.developing-your-first-application.sample-application>

Chúng ta cần một ứng dụng ví dụ để tạo native image. Cho mục đích của chúng ta, ứng dụng web "Hello World!" đơn giản được đề cập trong phần "[Phát triển ứng dụng Spring Boot đầu tiên (Developing Your First Spring Boot Application)](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#getting-started.first-application)" là đủ.

Tóm tắt lại, code ứng dụng chính của chúng ta trông như thế này:

```java
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@SpringBootApplication
public class MyApplication {

    @RequestMapping("/")
    String home() {
        return "Hello World!";
    }

    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }

}
```

Ứng dụng này dùng Spring MVC và Tomcat nhúng, cả hai đều đã được kiểm thử và xác nhận hoạt động với GraalVM native image.

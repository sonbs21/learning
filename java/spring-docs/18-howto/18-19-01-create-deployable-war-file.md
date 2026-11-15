# 18.19.1. Tạo file war có thể triển khai (Create a Deployable War File)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.traditional-deployment.war>

> **CẢNH BÁO (WARNING):** Vì Spring WebFlux không phụ thuộc chặt chẽ vào servlet API và các ứng dụng mặc định được triển khai trên một embedded Reactor Netty server, việc triển khai War không được hỗ trợ cho các ứng dụng WebFlux.

Bước đầu tiên để tạo ra một file war có thể triển khai là cung cấp một subclass của `SpringBootServletInitializer` và override phương thức `configure` của nó. Làm vậy sẽ tận dụng hỗ trợ servlet 3.0 của Spring Framework và cho phép bạn cấu hình ứng dụng khi nó được servlet container khởi chạy. Thông thường, bạn nên cập nhật main class của ứng dụng để kế thừa `SpringBootServletInitializer`, như trong ví dụ sau:

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
        return application.sources(MyApplication.class);
    }

    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
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
        return application.sources(MyApplication::class.java)
    }

}

fun main(args: Array<String>) {
    runApplication<MyApplication>(*args)
}
```

Bước tiếp theo là cập nhật cấu hình build sao cho dự án của bạn tạo ra một file war thay vì một file jar. Nếu bạn dùng Maven và `spring-boot-starter-parent` (vốn cấu hình war plugin của Maven cho bạn), tất cả những gì bạn cần làm là sửa `pom.xml` để đổi packaging thành war, như sau:

```xml
<packaging>war</packaging>
```

Nếu bạn dùng Gradle, bạn cần sửa `build.gradle` để áp dụng war plugin cho dự án, như sau:

```gradle
apply plugin: 'war'
```

Bước cuối cùng trong quy trình là đảm bảo rằng embedded servlet container không can thiệp vào servlet container mà file war được triển khai lên. Để làm vậy, bạn cần đánh dấu dependency embedded servlet container là provided.

Nếu bạn dùng Maven, ví dụ sau đánh dấu servlet container (trong trường hợp này là Tomcat) là provided:

```xml
<dependencies>
    <!-- ... -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-tomcat</artifactId>
        <scope>provided</scope>
    </dependency>
    <!-- ... -->
</dependencies>
```

Nếu bạn dùng Gradle, ví dụ sau đánh dấu servlet container (trong trường hợp này là Tomcat) là provided:

```gradle
dependencies {
    // ...
    providedRuntime 'org.springframework.boot:spring-boot-starter-tomcat'
    // ...
}
```

> **MẸO (TIP):** `providedRuntime` được ưu tiên hơn cấu hình `compileOnly` của Gradle. Trong số các hạn chế khác, các dependency `compileOnly` không có trên test classpath, vì vậy mọi integration test dựa trên web đều thất bại.

Nếu bạn dùng [các công cụ build của Spring Boot](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#build-tool-plugins), việc đánh dấu dependency embedded servlet container là provided sẽ tạo ra một file war thực thi được với các dependency provided được đóng gói trong thư mục `lib-provided`. Điều này có nghĩa là, ngoài việc có thể triển khai lên một servlet container, bạn cũng có thể chạy ứng dụng bằng `java -jar` trên dòng lệnh.

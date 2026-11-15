# 18.19.3. Triển khai WAR lên WebLogic (Deploying a WAR to WebLogic)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.traditional-deployment.weblogic>

Để triển khai một ứng dụng Spring Boot lên WebLogic, bạn phải đảm bảo rằng servlet initializer của bạn implement `WebApplicationInitializer` một cách **trực tiếp** (ngay cả khi bạn kế thừa từ một base class đã implement nó).

Một initializer điển hình cho WebLogic sẽ giống như ví dụ sau:

**Java**

```java
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.web.WebApplicationInitializer;

@SpringBootApplication
public class MyApplication extends SpringBootServletInitializer implements WebApplicationInitializer {

}
```

**Kotlin**

```kotlin
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer
import org.springframework.web.WebApplicationInitializer

@SpringBootApplication
class MyApplication : SpringBootServletInitializer(), WebApplicationInitializer
```

Nếu bạn dùng Logback, bạn cũng cần báo cho WebLogic ưu tiên phiên bản được đóng gói thay vì phiên bản đã được cài đặt sẵn cùng server. Bạn có thể làm vậy bằng cách thêm một file `WEB-INF/weblogic.xml` với nội dung sau:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<wls:weblogic-web-app
    xmlns:wls="http://xmlns.oracle.com/weblogic/weblogic-web-app"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://java.sun.com/xml/ns/javaee
        https://java.sun.com/xml/ns/javaee/ejb-jar_3_0.xsd
        http://xmlns.oracle.com/weblogic/weblogic-web-app
        https://xmlns.oracle.com/weblogic/weblogic-web-app/1.4/weblogic-web-app.xsd">
    <wls:container-descriptor>
        <wls:prefer-application-packages>
            <wls:package-name>org.slf4j</wls:package-name>
        </wls:prefer-application-packages>
    </wls:container-descriptor>
</wls:weblogic-web-app>
```

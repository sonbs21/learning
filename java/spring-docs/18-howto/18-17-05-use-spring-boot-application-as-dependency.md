# 18.17.5. Dùng một ứng dụng Spring Boot làm dependency (Use a Spring Boot Application as a Dependency)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.build.use-a-spring-boot-application-as-dependency>

Giống như một file war, một ứng dụng Spring Boot không được dự định để dùng làm dependency. Nếu ứng dụng của bạn chứa các class mà bạn muốn chia sẻ với các dự án khác, cách tiếp cận được khuyến nghị là chuyển code đó vào một module riêng. Module riêng đó sau đó có thể được ứng dụng của bạn và các dự án khác phụ thuộc vào.

Nếu bạn không thể sắp xếp lại code như khuyến nghị ở trên, các plugin Maven và Gradle của Spring Boot phải được cấu hình để tạo ra một artifact riêng phù hợp để dùng làm dependency. File nén thực thi không thể được dùng làm dependency vì [định dạng jar thực thi](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#appendix.executable-jar.nested-jars.jar-structure) đóng gói các class của ứng dụng trong `BOOT-INF/classes`. Điều này có nghĩa là chúng không thể được tìm thấy khi jar thực thi được dùng làm dependency.

Để tạo ra hai artifact, một cái có thể dùng làm dependency và một cái thực thi được, phải chỉ định một classifier. Classifier này được áp dụng cho tên của file nén thực thi, để lại file nén mặc định để dùng làm dependency.

Để cấu hình classifier `exec` trong Maven, bạn có thể dùng cấu hình sau:

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-maven-plugin</artifactId>
            <configuration>
                <classifier>exec</classifier>
            </configuration>
        </plugin>
    </plugins>
</build>
```

# 18.2.1. Tự động mở rộng property lúc build (Automatically Expand Properties at Build Time)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.properties-and-configuration.expand-properties>

Thay vì hardcode một số property cũng được chỉ định trong cấu hình build của dự án, bạn có thể tự động mở rộng (expand) chúng bằng cách dùng chính cấu hình build hiện có. Điều này khả thi ở cả Maven và Gradle.

## Tự động mở rộng property bằng Maven (Automatic Property Expansion Using Maven)

Bạn có thể tự động mở rộng các property từ dự án Maven bằng cách dùng resource filtering. Nếu bạn dùng `spring-boot-starter-parent`, bạn có thể tham chiếu tới các 'project properties' của Maven bằng các placeholder `@..@`, như trong ví dụ sau:

**Properties**

```properties
[email protected]@
[email protected]@
```

**Yaml**

```yaml
app:
  encoding: "@project.build.sourceEncoding@"
  java:
    version: "@java.version@"
```

> **LƯU Ý (NOTE):** Chỉ cấu hình production được lọc theo cách đó (nói cách khác, không có filtering nào được áp dụng cho `src/test/resources`).

> **MẸO (TIP):** Nếu bạn bật cờ `addResources`, goal `spring-boot:run` có thể thêm trực tiếp `src/main/resources` vào classpath (cho mục đích hot reloading). Làm như vậy sẽ bỏ qua resource filtering và tính năng này. Thay vào đó, bạn có thể dùng goal `exec:java` hoặc tùy chỉnh cấu hình của plugin. Xem [trang hướng dẫn sử dụng plugin](https://docs.spring.io/spring-boot/docs/3.2.5/maven-plugin/reference/htmlsingle/#getting-started) để biết thêm chi tiết.

Nếu bạn không dùng starter parent, bạn cần đưa phần tử sau vào bên trong phần tử `<build/>` của `pom.xml`:

```xml
<resources>
    <resource>
        <directory>src/main/resources</directory>
        <filtering>true</filtering>
    </resource>
</resources>
```

Bạn cũng cần đưa phần tử sau vào bên trong `<plugins/>`:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-resources-plugin</artifactId>
    <version>2.7</version>
    <configuration>
        <delimiters>
            <delimiter>@</delimiter>
        </delimiters>
        <useDefaultDelimiters>false</useDefaultDelimiters>
    </configuration>
</plugin>
```

> **LƯU Ý (NOTE):** Property `useDefaultDelimiters` quan trọng nếu bạn dùng các placeholder chuẩn của Spring (chẳng hạn như `${placeholder}`) trong cấu hình. Nếu property đó không được đặt thành `false`, các placeholder này có thể bị mở rộng bởi quá trình build.

## Tự động mở rộng property bằng Gradle (Automatic Property Expansion Using Gradle)

Bạn có thể tự động mở rộng các property từ dự án Gradle bằng cách cấu hình task `processResources` của Java plugin để làm điều đó, như trong ví dụ sau:

```gradle
tasks.named('processResources') {
    expand(project.properties)
}
```

Sau đó bạn có thể tham chiếu tới các property của dự án Gradle bằng cách dùng placeholder, như trong ví dụ sau:

**Properties**

```properties
app.name=${name}
app.description=${description}
```

**Yaml**

```yaml
app:
  name: "${name}"
  description: "${description}"
```

> **LƯU Ý (NOTE):** Phương thức `expand` của Gradle dùng `SimpleTemplateEngine` của Groovy, vốn biến đổi các token `${..}`. Kiểu `${..}` xung đột với cơ chế property placeholder của chính Spring. Để dùng các property placeholder của Spring cùng với việc mở rộng tự động, hãy escape các property placeholder của Spring như sau: `\${..}`.

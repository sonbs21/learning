# 18.4.2. Viết một XML REST service (Write an XML REST Service)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.spring-mvc.write-xml-rest-service>

Nếu bạn có Jackson XML extension (`jackson-dataformat-xml`) trên classpath, bạn có thể dùng nó để render XML response. Ví dụ trước mà chúng ta đã dùng cho JSON sẽ hoạt động. Để dùng Jackson XML renderer, hãy thêm dependency sau vào dự án:

```xml
<dependency>
    <groupId>com.fasterxml.jackson.dataformat</groupId>
    <artifactId>jackson-dataformat-xml</artifactId>
</dependency>
```

Nếu Jackson XML extension không có sẵn và JAXB có sẵn, XML có thể được render với yêu cầu bổ sung là `MyThing` phải được annotate với `@XmlRootElement`, như trong ví dụ sau:

**Java**

```java
import jakarta.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class MyThing {

    private String name;

    // getters/setters ...

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

}
```

**Kotlin**

```kotlin
import jakarta.xml.bind.annotation.XmlRootElement

@XmlRootElement
class MyThing {

    var name: String? = null

}
```

Bạn cần đảm bảo rằng thư viện JAXB là một phần của dự án, ví dụ bằng cách thêm:

```xml
<dependency>
    <groupId>org.glassfish.jaxb</groupId>
    <artifactId>jaxb-runtime</artifactId>
</dependency>
```

> **LƯU Ý (NOTE):** Để server render XML thay vì JSON, bạn có thể phải gửi một header `Accept: text/xml` (hoặc dùng trình duyệt).

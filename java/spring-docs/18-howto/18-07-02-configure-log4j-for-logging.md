# 18.7.2. Cấu hình Log4j cho logging (Configure Log4j for Logging)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.logging.log4j>

Spring Boot hỗ trợ [Log4j 2](https://logging.apache.org/log4j/2.x/) cho cấu hình logging nếu nó có trên classpath. Nếu bạn dùng các starter để tập hợp dependency, bạn phải loại bỏ Logback và sau đó bao gồm Log4j 2 thay thế. Nếu bạn không dùng các starter, bạn cần cung cấp (ít nhất) `spring-jcl` bên cạnh Log4j 2.

Cách được khuyến nghị là thông qua các starter, mặc dù nó đòi hỏi một chút xoay xở. Ví dụ sau cho thấy cách thiết lập các starter trong Maven:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter</artifactId>
    <exclusions>
        <exclusion>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-logging</artifactId>
        </exclusion>
    </exclusions>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-log4j2</artifactId>
</dependency>
```

Gradle cung cấp một vài cách khác nhau để thiết lập các starter. Một cách là dùng [module replacement](https://docs.gradle.org/current/userguide/resolution_rules.html#sec:module_replacement). Để làm vậy, hãy khai báo một dependency vào Log4j 2 starter và báo cho Gradle biết rằng mọi lần xuất hiện của logging starter mặc định nên được thay thế bằng Log4j 2 starter, như trong ví dụ sau:

```gradle
dependencies {
    implementation "org.springframework.boot:spring-boot-starter-log4j2"
    modules {
        module("org.springframework.boot:spring-boot-starter-logging") {
            replacedBy("org.springframework.boot:spring-boot-starter-log4j2", "Use Log4j2 instead of Logback")
        }
    }
}
```

> **LƯU Ý (NOTE):** Các starter Log4j tập hợp các dependency cho các yêu cầu logging thông dụng (chẳng hạn như để Tomcat dùng `java.util.logging` nhưng cấu hình output bằng Log4j 2).

> **LƯU Ý (NOTE):** Để đảm bảo rằng debug logging được thực hiện bằng `java.util.logging` được định tuyến vào Log4j 2, hãy cấu hình [JDK logging adapter](https://logging.apache.org/log4j/2.x/log4j-jul.html) của nó bằng cách đặt system property `java.util.logging.manager` thành `org.apache.logging.log4j.jul.LogManager`.

## Dùng YAML hoặc JSON để cấu hình Log4j 2 (Use YAML or JSON to Configure Log4j 2)

Ngoài định dạng cấu hình XML mặc định, Log4j 2 cũng hỗ trợ các file cấu hình YAML và JSON. Để cấu hình Log4j 2 dùng một định dạng file cấu hình thay thế, hãy thêm các dependency phù hợp vào classpath và đặt tên các file cấu hình khớp với định dạng file bạn chọn, như trong ví dụ sau:

| Định dạng | Dependency | Tên file |
|---|---|---|
| YAML | `com.fasterxml.jackson.core:jackson-databind` + `com.fasterxml.jackson.dataformat:jackson-dataformat-yaml` | `log4j2.yaml` + `log4j2.yml` |
| JSON | `com.fasterxml.jackson.core:jackson-databind` | `log4j2.json` + `log4j2.jsn` |

## Dùng cấu hình tổng hợp để cấu hình Log4j 2 (Use Composite Configuration to Configure Log4j 2)

Log4j 2 hỗ trợ kết hợp nhiều file cấu hình thành một cấu hình tổng hợp (composite) duy nhất. Để dùng hỗ trợ này trong Spring Boot, hãy cấu hình `logging.log4j2.config.override` với vị trí của một hoặc nhiều file cấu hình phụ. Các file cấu hình phụ sẽ được hợp nhất với cấu hình chính, bất kể nguồn của cấu hình chính là các giá trị mặc định của Spring Boot, một vị trí chuẩn như `log4j.xml`, hay vị trí được cấu hình bởi property `logging.config`.

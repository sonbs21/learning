# 18.3.1. Dùng web server khác (Use Another Web Server)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.webserver.use-another>

Nhiều starter của Spring Boot bao gồm các container nhúng mặc định.

- Với các ứng dụng servlet stack, `spring-boot-starter-web` bao gồm Tomcat thông qua việc bao gồm `spring-boot-starter-tomcat`, nhưng bạn có thể dùng `spring-boot-starter-jetty` hoặc `spring-boot-starter-undertow` thay thế.
- Với các ứng dụng reactive stack, `spring-boot-starter-webflux` bao gồm Reactor Netty thông qua việc bao gồm `spring-boot-starter-reactor-netty`, nhưng bạn có thể dùng `spring-boot-starter-tomcat`, `spring-boot-starter-jetty`, hoặc `spring-boot-starter-undertow` thay thế.

Khi chuyển sang một HTTP server khác, bạn cần thay các dependency mặc định bằng những dependency bạn cần. Để hỗ trợ quá trình này, Spring Boot cung cấp một starter riêng cho mỗi HTTP server được hỗ trợ.

Ví dụ Maven sau cho thấy cách loại bỏ Tomcat và bao gồm Jetty cho Spring MVC:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
    <exclusions>
        <!-- Exclude the Tomcat dependency -->
        <exclusion>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-tomcat</artifactId>
        </exclusion>
    </exclusions>
</dependency>
<!-- Use Jetty instead -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-jetty</artifactId>
</dependency>
```

Ví dụ Gradle sau cấu hình các dependency cần thiết và một [module replacement](https://docs.gradle.org/current/userguide/resolution_rules.html#sec:module_replacement) để dùng Undertow thay cho Reactor Netty cho Spring WebFlux:

```gradle
dependencies {
    implementation "org.springframework.boot:spring-boot-starter-undertow"
    implementation "org.springframework.boot:spring-boot-starter-webflux"
    modules {
        module("org.springframework.boot:spring-boot-starter-reactor-netty") {
            replacedBy("org.springframework.boot:spring-boot-starter-undertow", "Use Undertow instead of Reactor Netty")
        }
    }
}
```

> **LƯU Ý (NOTE):** `spring-boot-starter-reactor-netty` là bắt buộc để dùng class `WebClient`, vì vậy bạn có thể cần giữ một dependency vào Netty ngay cả khi bạn cần bao gồm một HTTP server khác.

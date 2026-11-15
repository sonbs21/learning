# 4.2. Yêu cầu hệ thống (System Requirements)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#getting-started.system-requirements>

Spring Boot 3.2.5 yêu cầu [Java 17](https://www.java.com) và tương thích đến tối đa Java 22 (bao gồm cả Java 22). [Spring Framework 6.1.6](https://docs.spring.io/spring-framework/reference/6.1/) trở lên cũng là bắt buộc.

Hỗ trợ build tường minh được cung cấp cho các công cụ build sau:

| Công cụ build | Phiên bản |
|---|---|
| Maven | 3.6.3 trở lên |
| Gradle | 7.x (7.5 trở lên) và 8.x |

## 4.2.1. Servlet Container

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#getting-started.system-requirements.servlet-containers>

Spring Boot hỗ trợ các servlet container nhúng sau:

| Tên | Phiên bản Servlet |
|---|---|
| Tomcat 10.1 | 6.0 |
| Jetty 12.0 | 6.0 |
| Undertow 2.3 | 6.0 |

Bạn cũng có thể triển khai ứng dụng Spring Boot lên bất kỳ container nào tương thích servlet 5.0 trở lên.

## 4.2.2. GraalVM Native Image

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#getting-started.system-requirements.graal>

Các ứng dụng Spring Boot có thể được [chuyển đổi thành Native Image](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#native-image.introducing-graalvm-native-images) bằng GraalVM 22.3 trở lên.

Image có thể được tạo bằng các plugin Gradle/Maven [native build tools](https://github.com/graalvm/native-build-tools) hoặc công cụ `native-image` do GraalVM cung cấp. Bạn cũng có thể tạo native image bằng [native-image Paketo buildpack](https://github.com/paketo-buildpacks/native-image).

Các phiên bản sau được hỗ trợ:

| Tên | Phiên bản |
|---|---|
| GraalVM Community | 22.3 |
| Native Build Tools | 0.9.28 |

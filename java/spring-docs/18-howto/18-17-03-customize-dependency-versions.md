# 18.17.3. Tùy chỉnh phiên bản dependency (Customize Dependency Versions)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.build.customize-dependency-versions>

POM `spring-boot-dependencies` quản lý phiên bản của các dependency thông dụng. Các plugin Spring Boot cho Maven và Gradle cho phép tùy chỉnh các phiên bản dependency được quản lý này bằng các build property.

> **CẢNH BÁO (WARNING):** Mỗi bản phát hành Spring Boot được thiết kế và kiểm thử với tập dependency bên thứ ba cụ thể này. Việc ghi đè phiên bản có thể gây ra các vấn đề tương thích.

Để ghi đè phiên bản dependency với Maven, xem [phần này](https://docs.spring.io/spring-boot/docs/3.2.5/maven-plugin/reference/htmlsingle/#using) trong tài liệu của Maven plugin.

Để ghi đè phiên bản dependency trong Gradle, xem [phần này](https://docs.spring.io/spring-boot/docs/3.2.5/gradle-plugin/reference/htmlsingle/#managing-dependencies-dependency-management-plugin-customizing) trong tài liệu của Gradle plugin.

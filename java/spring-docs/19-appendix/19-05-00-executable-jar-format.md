# Phụ lục E: Định dạng jar thực thi (Appendix E: The Executable Jar Format)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#appendix.executable-jar>

Các module `spring-boot-loader` cho phép Spring Boot hỗ trợ các file jar và war thực thi. Nếu bạn dùng Maven plugin hoặc Gradle plugin, các jar thực thi được sinh tự động, và nhìn chung bạn không cần biết chi tiết về cách chúng hoạt động.

Nếu bạn cần tạo jar thực thi từ một hệ thống build khác hoặc nếu bạn chỉ tò mò về công nghệ bên dưới, phụ lục này cung cấp một số kiến thức nền.

# 17.4. Hỗ trợ các hệ thống build khác (Supporting Other Build Systems)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#build-tool-plugins.other-build-systems>

Nếu bạn muốn dùng một công cụ build khác ngoài Maven, Gradle, hoặc Ant, có lẽ bạn cần phát triển plugin của riêng mình. Các jar thực thi cần tuân theo một định dạng cụ thể và một số mục nhất định cần được ghi ở dạng không nén (xem phần "[định dạng jar thực thi](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#appendix.executable-jar)" trong phụ lục để biết chi tiết).

Cả plugin Spring Boot Maven và Gradle đều dùng `spring-boot-loader-tools` để thực sự sinh ra các jar. Nếu cần, bạn có thể dùng trực tiếp thư viện này.

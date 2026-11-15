# 18.15.2. Nạp lại template mà không khởi động lại container (Reload Templates without Restarting the Container)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.hotswapping.reload-templates>

Hầu hết các công nghệ template được Spring Boot hỗ trợ đều có một tùy chọn cấu hình để vô hiệu hóa caching (được mô tả ở phần sau của tài liệu này). Nếu bạn dùng module `spring-boot-devtools`, các property này được [cấu hình tự động](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#using.devtools.property-defaults) cho bạn trong lúc phát triển.

## Thymeleaf template (Thymeleaf Templates)

Nếu bạn dùng Thymeleaf, hãy đặt `spring.thymeleaf.cache` thành `false`. Xem [`ThymeleafAutoConfiguration`](https://github.com/spring-projects/spring-boot/tree/v3.2.5/spring-boot-project/spring-boot-autoconfigure/src/main/java/org/springframework/boot/autoconfigure/thymeleaf/ThymeleafAutoConfiguration.java) để biết các tùy chọn tùy chỉnh Thymeleaf khác.

## FreeMarker template (FreeMarker Templates)

Nếu bạn dùng FreeMarker, hãy đặt `spring.freemarker.cache` thành `false`. Xem [`FreeMarkerAutoConfiguration`](https://github.com/spring-projects/spring-boot/tree/v3.2.5/spring-boot-project/spring-boot-autoconfigure/src/main/java/org/springframework/boot/autoconfigure/freemarker/FreeMarkerAutoConfiguration.java) để biết các tùy chọn tùy chỉnh FreeMarker khác.

## Groovy template (Groovy Templates)

Nếu bạn dùng Groovy template, hãy đặt `spring.groovy.template.cache` thành `false`. Xem [`GroovyTemplateAutoConfiguration`](https://github.com/spring-projects/spring-boot/tree/v3.2.5/spring-boot-project/spring-boot-autoconfigure/src/main/java/org/springframework/boot/autoconfigure/groovy/template/GroovyTemplateAutoConfiguration.java) để biết các tùy chọn tùy chỉnh Groovy khác.

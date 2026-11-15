# 17.3. Module Spring Boot AntLib (Spring Boot AntLib Module)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#build-tool-plugins.antlib>

Module Spring Boot AntLib cung cấp hỗ trợ Spring Boot cơ bản cho Apache Ant. Bạn có thể dùng module này để tạo các jar thực thi. Để dùng module, bạn cần khai báo thêm một namespace `spring-boot` trong `build.xml`, như trong ví dụ sau:

```xml
<project xmlns:ivy="antlib:org.apache.ivy.ant"
    xmlns:spring-boot="antlib:org.springframework.boot.ant"
    name="myapp" default="build">
    ...
</project>
```

Bạn cần nhớ khởi động Ant bằng tùy chọn `-lib`, như trong ví dụ sau:

```shell
$ ant -lib <directory containing spring-boot-antlib-3.2.5.jar>
```

> **MẸO (TIP):** Phần "Sử dụng Spring Boot" bao gồm một ví dụ đầy đủ hơn về [việc dùng Apache Ant với `spring-boot-antlib`](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#using.build-systems.ant).

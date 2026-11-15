# 18.17.1. Sinh thông tin build (Generate Build Information)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.build.generate-info>

Cả Maven plugin và Gradle plugin đều cho phép sinh thông tin build chứa tọa độ, tên, và phiên bản của dự án. Các plugin cũng có thể được cấu hình để thêm các property bổ sung thông qua cấu hình. Khi có một file như vậy, Spring Boot sẽ auto-configure một bean `BuildProperties`.

Để sinh thông tin build với Maven, hãy thêm một execution cho goal `build-info`, như trong ví dụ sau:

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-maven-plugin</artifactId>
            <version>3.2.5</version>
            <executions>
                <execution>
                    <goals>
                        <goal>build-info</goal>
                    </goals>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

> **MẸO (TIP):** Xem [tài liệu Spring Boot Maven Plugin](https://docs.spring.io/spring-boot/docs/3.2.5/maven-plugin/reference/htmlsingle/#goals-build-info) để biết thêm chi tiết.

Ví dụ sau làm điều tương tự với Gradle:

```gradle
springBoot {
    buildInfo()
}
```

> **MẸO (TIP):** Xem [tài liệu Spring Boot Gradle Plugin](https://docs.spring.io/spring-boot/docs/3.2.5/gradle-plugin/reference/htmlsingle/#integrating-with-actuator-build-info) để biết thêm chi tiết.

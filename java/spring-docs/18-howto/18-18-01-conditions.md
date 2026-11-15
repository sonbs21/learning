# 18.18.1. Điều kiện (Conditions)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.aot.conditions>

Xử lý ahead-of-time tối ưu hóa ứng dụng và đánh giá các [điều kiện (conditions)](https://docs.spring.io/spring-framework/docs/6.1.6/javadoc-api/org/springframework/context/annotation/Conditional.html) dựa trên environment lúc build. [Profile](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.profiles) được hiện thực thông qua các điều kiện và do đó cũng bị ảnh hưởng.

Nếu bạn muốn có các bean được tạo dựa trên một điều kiện trong một ứng dụng được tối ưu hóa ahead-of-time, bạn phải thiết lập environment khi build ứng dụng. Các bean được tạo ra trong quá trình xử lý ahead-of-time lúc build sau đó luôn được tạo khi chạy ứng dụng và không thể tắt được. Để làm điều này, bạn có thể đặt các profile nên được dùng khi build ứng dụng.

Với Maven, điều này hoạt động bằng cách đặt cấu hình `profiles` của execution `spring-boot-maven-plugin:process-aot`:

```xml
<profile>
    <id>native</id>
    <build>
        <pluginManagement>
            <plugins>
                <plugin>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-maven-plugin</artifactId>
                    <executions>
                        <execution>
                            <id>process-aot</id>
                            <configuration>
                                <profiles>profile-a,profile-b</profiles>
                            </configuration>
                        </execution>
                    </executions>
                </plugin>
            </plugins>
        </pluginManagement>
    </build>
</profile>
```

Với Gradle, bạn cần cấu hình task `ProcessAot`:

```gradle
tasks.withType(org.springframework.boot.gradle.tasks.aot.ProcessAot).configureEach {
    args('--spring.profiles.active=profile-a,profile-b')
}
```

Các profile chỉ thay đổi các property cấu hình không ảnh hưởng đến điều kiện được hỗ trợ không giới hạn khi chạy các ứng dụng được tối ưu hóa ahead-of-time.

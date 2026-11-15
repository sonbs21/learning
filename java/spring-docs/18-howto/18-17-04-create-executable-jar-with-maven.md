# 18.17.4. Tạo JAR thực thi với Maven (Create an Executable JAR with Maven)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.build.create-an-executable-jar-with-maven>

`spring-boot-maven-plugin` có thể được dùng để tạo một JAR "béo" ("fat" JAR) thực thi được. Nếu bạn dùng POM `spring-boot-starter-parent`, bạn có thể khai báo plugin và các jar của bạn sẽ được đóng gói lại như sau:

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-maven-plugin</artifactId>
        </plugin>
    </plugins>
</build>
```

Nếu bạn không dùng parent POM, bạn vẫn có thể dùng plugin. Tuy nhiên, bạn phải thêm một phần `<executions>`, như sau:

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
                        <goal>repackage</goal>
                    </goals>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

Xem [tài liệu plugin](https://docs.spring.io/spring-boot/docs/3.2.5/maven-plugin/reference/htmlsingle/#repackage) để biết đầy đủ chi tiết cách sử dụng.

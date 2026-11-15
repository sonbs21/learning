# 18.17.7. Tạo JAR không thực thi có loại trừ (Create a Non-executable JAR with Exclusions)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.build.create-a-nonexecutable-jar>

Thông thường, nếu bạn có một jar thực thi và một jar không thực thi là hai sản phẩm build riêng biệt, phiên bản thực thi có thêm các file cấu hình không cần thiết trong một jar thư viện. Ví dụ, file cấu hình `application.yaml` có thể được loại trừ khỏi JAR không thực thi.

Trong Maven, jar thực thi phải là artifact chính và bạn có thể thêm một jar có classifier cho thư viện, như sau:

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-maven-plugin</artifactId>
        </plugin>
        <plugin>
            <artifactId>maven-jar-plugin</artifactId>
            <executions>
                <execution>
                    <id>lib</id>
                    <phase>package</phase>
                    <goals>
                        <goal>jar</goal>
                    </goals>
                    <configuration>
                        <classifier>lib</classifier>
                        <excludes>
                            <exclude>application.yaml</exclude>
                        </excludes>
                    </configuration>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

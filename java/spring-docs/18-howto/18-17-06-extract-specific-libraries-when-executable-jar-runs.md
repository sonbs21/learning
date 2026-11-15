# 18.17.6. Giải nén các thư viện cụ thể khi jar thực thi chạy (Extract Specific Libraries When an Executable Jar Runs)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.build.extract-specific-libraries-when-an-executable-jar-runs>

Hầu hết các thư viện lồng nhau trong một jar thực thi không cần được giải nén để chạy. Tuy nhiên, một số thư viện nhất định có thể gặp vấn đề. Ví dụ, JRuby có hỗ trợ jar lồng nhau của riêng nó, vốn giả định rằng `jruby-complete.jar` luôn có sẵn trực tiếp dưới dạng một file độc lập.

Để xử lý bất kỳ thư viện có vấn đề nào, bạn có thể đánh dấu rằng các jar lồng nhau cụ thể nên được tự động giải nén khi jar thực thi chạy lần đầu. Các jar lồng nhau như vậy được ghi vào bên dưới thư mục tạm được xác định bởi system property `java.io.tmpdir`.

> **CẢNH BÁO (WARNING):** Cần cẩn thận để đảm bảo rằng hệ điều hành của bạn được cấu hình sao cho nó sẽ không xóa các jar đã được giải nén vào thư mục tạm trong khi ứng dụng vẫn đang chạy.

Ví dụ, để chỉ ra rằng JRuby nên được đánh dấu để giải nén bằng Maven Plugin, bạn sẽ thêm cấu hình sau:

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-maven-plugin</artifactId>
            <configuration>
                <requiresUnpack>
                    <dependency>
                        <groupId>org.jruby</groupId>
                        <artifactId>jruby-complete</artifactId>
                    </dependency>
                </requiresUnpack>
            </configuration>
        </plugin>
    </plugins>
</build>
```

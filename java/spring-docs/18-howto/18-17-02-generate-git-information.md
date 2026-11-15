# 18.17.2. Sinh thông tin Git (Generate Git Information)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.build.generate-git-info>

Cả Maven và Gradle đều cho phép sinh một file `git.properties` chứa thông tin về trạng thái của repository mã nguồn `git` khi dự án được build.

Với người dùng Maven, POM `spring-boot-starter-parent` bao gồm một plugin được cấu hình sẵn để sinh file `git.properties`. Để dùng nó, hãy thêm khai báo sau cho [`Git Commit Id Plugin`](https://github.com/git-commit-id/git-commit-id-maven-plugin) vào POM của bạn:

```xml
<build>
    <plugins>
        <plugin>
            <groupId>io.github.git-commit-id</groupId>
            <artifactId>git-commit-id-maven-plugin</artifactId>
        </plugin>
    </plugins>
</build>
```

Người dùng Gradle có thể đạt được kết quả tương tự bằng cách dùng plugin [`gradle-git-properties`](https://plugins.gradle.org/plugin/com.gorylenko.gradle-git-properties), như trong ví dụ sau:

```gradle
plugins {
    id "com.gorylenko.gradle-git-properties" version "2.4.1"
}
```

Cả plugin Maven và Gradle đều cho phép cấu hình các property được đưa vào `git.properties`.

> **MẸO (TIP):** Thời gian commit trong `git.properties` được mong đợi khớp với định dạng sau: `yyyy-MM-dd’T’HH:mm:ssZ`. Đây là định dạng mặc định cho cả hai plugin được liệt kê ở trên. Dùng định dạng này cho phép thời gian được parse thành một `Date` và định dạng của nó, khi được serialize thành JSON, được kiểm soát bởi các thiết lập cấu hình serialize ngày tháng của Jackson.

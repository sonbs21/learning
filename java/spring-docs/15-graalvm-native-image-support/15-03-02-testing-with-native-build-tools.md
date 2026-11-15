# 15.3.2. Kiểm thử với Native Build Tools (Testing With Native Build Tools)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#native-image.testing.with-native-build-tools>

GraalVM Native Build Tools bao gồm khả năng chạy test bên trong một native image. Điều này có thể hữu ích khi bạn muốn kiểm thử sâu rằng các phần bên trong ứng dụng hoạt động trong một GraalVM native image.

Việc sinh native image chứa các test cần chạy có thể là một thao tác tốn thời gian, vì vậy hầu hết các lập trình viên có lẽ sẽ thích dùng JVM ở máy cục bộ hơn. Tuy nhiên, chúng có thể rất hữu ích như một phần của pipeline CI. Ví dụ, bạn có thể chọn chạy các test native mỗi ngày một lần.

Spring Framework bao gồm hỗ trợ ahead-of-time cho việc chạy test. Tất cả các tính năng kiểm thử Spring thông thường đều hoạt động với các test native image. Ví dụ, bạn có thể tiếp tục dùng annotation `@SpringBootTest`. Bạn cũng có thể dùng các [test slice](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.testing.spring-boot-applications.autoconfigured-tests) của Spring Boot để chỉ kiểm thử các phần cụ thể của ứng dụng.

Hỗ trợ kiểm thử native của Spring Framework hoạt động theo cách sau:

- Các test được phân tích để phát hiện mọi instance `ApplicationContext` sẽ cần đến.
- Xử lý ahead-of-time được áp dụng cho mỗi application context này và các tài nguyên được sinh ra.
- Một native image được tạo ra, với các tài nguyên được sinh ra được GraalVM xử lý.
- Native image cũng bao gồm `TestEngine` của JUnit được cấu hình với một danh sách các test đã được phát hiện.
- Native image được khởi động, kích hoạt engine, engine sẽ chạy từng test và báo cáo kết quả.

## Sử dụng Maven (Using Maven)

Để chạy test native bằng Maven, hãy đảm bảo file `pom.xml` dùng `spring-boot-starter-parent`. Bạn nên có một phần `<parent>` trông như thế này:

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.2.5</version>
</parent>
```

`spring-boot-starter-parent` khai báo một profile `nativeTest` cấu hình các execution cần thiết để chạy các test native. Bạn có thể kích hoạt profile bằng cờ `-P` trên dòng lệnh.

> **MẸO (TIP):** Nếu bạn không muốn dùng `spring-boot-starter-parent`, bạn sẽ cần cấu hình các execution cho goal `process-test-aot` từ plugin Spring Boot và goal `test` từ plugin Native Build Tools.

Để build image và chạy các test, hãy dùng goal `test` với profile `nativeTest` được kích hoạt:

```
$ mvn -PnativeTest test
```

## Sử dụng Gradle (Using Gradle)

Plugin Spring Boot Gradle tự động cấu hình các task test AOT khi plugin GraalVM Native Image được áp dụng. Bạn nên kiểm tra rằng bản build Gradle của bạn chứa một khối `plugins` bao gồm `org.graalvm.buildtools.native`.

Để chạy test native bằng Gradle, bạn có thể dùng task `nativeTest`:

```
$ gradle nativeTest
```

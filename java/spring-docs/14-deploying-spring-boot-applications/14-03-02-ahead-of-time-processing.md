# 14.3.2. Sử dụng xử lý Ahead-of-time với JVM (Using Ahead-of-time Processing With the JVM)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#deployment.efficient.aot>

Việc chạy ứng dụng bằng code khởi tạo được sinh ra bởi AOT có lợi cho thời gian khởi động. Trước tiên, bạn cần đảm bảo jar mà bạn đang build bao gồm code được AOT sinh ra.

Với Maven, điều này có nghĩa là bạn nên build với `-Pnative` để kích hoạt profile `native`:

```shell
$ mvn -Pnative package
```

Với Gradle, bạn cần đảm bảo bản build của mình bao gồm plugin `org.springframework.boot.aot`.

Khi JAR đã được build, hãy chạy nó với system property `spring.aot.enabled` được đặt thành `true`. Ví dụ:

```shell
$ java -Dspring.aot.enabled=true -jar myapplication.jar

........ Starting AOT-processed MyApplication ...
```

Hãy lưu ý rằng việc dùng xử lý ahead-of-time có những nhược điểm. Nó ngụ ý các hạn chế sau:

- Classpath là cố định và được định nghĩa đầy đủ tại thời điểm build
- Các bean được định nghĩa trong ứng dụng không thể thay đổi tại runtime, nghĩa là:
  - Annotation `@Profile` của Spring và cấu hình theo profile [có những hạn chế](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.aot.conditions).
  - Các property thay đổi việc một bean có được tạo hay không đều không được hỗ trợ (ví dụ, `@ConditionalOnProperty` và các property `.enable`).

Để tìm hiểu thêm về xử lý ahead-of-time, vui lòng xem [phần Hiểu về xử lý Ahead-of-Time của Spring (Understanding Spring Ahead-of-Time Processing)](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#native-image.introducing-graalvm-native-images.understanding-aot-processing).

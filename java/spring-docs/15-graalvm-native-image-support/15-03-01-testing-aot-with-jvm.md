# 15.3.1. Kiểm thử xử lý Ahead-of-time với JVM (Testing Ahead-of-time Processing With the JVM)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#native-image.testing.with-the-jvm>

Khi một ứng dụng Spring Boot chạy, nó cố gắng phát hiện xem nó có đang chạy dưới dạng native image hay không. Nếu nó đang chạy dưới dạng native image, nó sẽ khởi tạo ứng dụng bằng code đã được engine Spring AOT sinh ra tại thời điểm build.

Nếu ứng dụng đang chạy trên một JVM thông thường, thì mọi code được AOT sinh ra sẽ bị bỏ qua.

Vì giai đoạn biên dịch `native-image` có thể mất một lúc để hoàn tất, đôi khi sẽ hữu ích nếu chạy ứng dụng trên JVM nhưng để nó dùng code khởi tạo được AOT sinh ra. Làm như vậy giúp bạn nhanh chóng xác nhận rằng không có lỗi trong code được AOT sinh ra và không thiếu gì khi ứng dụng cuối cùng được chuyển đổi thành native image.

Để chạy một ứng dụng Spring Boot trên JVM và để nó dùng code được AOT sinh ra, bạn có thể đặt system property `spring.aot.enabled` thành `true`.

Ví dụ:

```shell
$ java -Dspring.aot.enabled=true -jar myapplication.jar
```

> **LƯU Ý (NOTE):** Bạn cần đảm bảo jar mà bạn đang kiểm thử bao gồm code được AOT sinh ra. Với Maven, điều này có nghĩa là bạn nên build với `-Pnative` để kích hoạt profile `native`. Với Gradle, bạn cần đảm bảo bản build của mình bao gồm plugin `org.graalvm.buildtools.native`.

Nếu ứng dụng của bạn khởi động với property `spring.aot.enabled` được đặt thành `true`, thì bạn có độ tin cậy cao hơn rằng nó sẽ hoạt động khi được chuyển đổi thành native image.

Bạn cũng có thể cân nhắc chạy integration test trên ứng dụng đang chạy. Ví dụ, bạn có thể dùng `WebClient` của Spring để gọi các REST endpoint của ứng dụng. Hoặc bạn có thể cân nhắc dùng một project như Selenium để kiểm tra các response HTML của ứng dụng.

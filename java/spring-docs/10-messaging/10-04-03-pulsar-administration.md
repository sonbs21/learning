# 10.4.3. Kết nối tới Pulsar Administration (Connecting to Pulsar Administration)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#messaging.pulsar.admin>

Client `PulsarAdministration` của Spring for Apache Pulsar cũng được tự động cấu hình.

Theo mặc định, ứng dụng cố gắng kết nối tới một instance Pulsar cục bộ tại `http://localhost:8080`. Điều này có thể được điều chỉnh bằng cách đặt property `spring.pulsar.admin.service-url` thành một giá trị khác ở dạng `(http|https)://<host>:<port>`.

Nếu bạn cần kiểm soát nhiều hơn đối với cấu hình, hãy cân nhắc đăng ký một hoặc nhiều bean `PulsarAdminBuilderCustomizer`.

## Xác thực (Authentication)

Khi truy cập một cluster Pulsar yêu cầu xác thực, admin client cần cấu hình bảo mật giống như Pulsar client thông thường. Bạn có thể dùng [cấu hình xác thực](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#messaging.pulsar.connecting.auth) đã nói ở trên bằng cách thay `spring.pulsar.client.authentication` bằng `spring.pulsar.admin.authentication`.

> **MẸO (TIP):** Để tạo một topic khi khởi động, hãy thêm một bean kiểu `PulsarTopic`. Nếu topic đã tồn tại, bean sẽ bị bỏ qua.

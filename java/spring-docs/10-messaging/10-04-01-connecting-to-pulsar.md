# 10.4.1. Kết nối tới Pulsar (Connecting to Pulsar)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#messaging.pulsar.connecting>

Khi bạn dùng starter Pulsar, Spring Boot sẽ tự động cấu hình và đăng ký một bean `PulsarClient`.

Theo mặc định, ứng dụng cố gắng kết nối tới một instance Pulsar cục bộ tại `pulsar://localhost:6650`. Điều này có thể được điều chỉnh bằng cách đặt property `spring.pulsar.client.service-url` thành một giá trị khác.

> **LƯU Ý (NOTE):** Giá trị phải là một URL [Pulsar Protocol](https://pulsar.apache.org/docs/client-libraries-java/#connection-urls) hợp lệ.

Bạn có thể cấu hình client bằng cách chỉ định bất kỳ application property nào có tiền tố `spring.pulsar.client.*`.

Nếu bạn cần kiểm soát nhiều hơn đối với cấu hình, hãy cân nhắc đăng ký một hoặc nhiều bean `PulsarClientBuilderCustomizer`.

## Xác thực (Authentication)

Để kết nối tới một cluster Pulsar yêu cầu xác thực, bạn cần chỉ định plugin xác thực nào cần dùng bằng cách đặt `pluginClassName` và mọi tham số mà plugin yêu cầu. Bạn có thể đặt các tham số dưới dạng một map từ tên tham số tới giá trị tham số. Ví dụ sau cho thấy cách cấu hình plugin `AuthenticationOAuth2`.

**Properties**

```properties
spring.pulsar.client.authentication.plugin-class-name=org.apache.pulsar.client.impl.auth.oauth2.AuthenticationOAuth2
spring.pulsar.client.authentication.param[issuerUrl]=https://auth.server.cloud/
spring.pulsar.client.authentication.param[privateKey]=file:///Users/some-key.json
spring.pulsar.client.authentication.param.audience=urn:sn:acme:dev:my-instance
```

**Yaml**

```yaml
spring:
  pulsar:
    client:
      authentication:
        plugin-class-name: org.apache.pulsar.client.impl.auth.oauth2.AuthenticationOAuth2
        param:
          issuerUrl: https://auth.server.cloud/
          privateKey: file:///Users/some-key.json
          audience: urn:sn:acme:dev:my-instance
```

> **LƯU Ý (NOTE):** Bạn cần đảm bảo các tên được định nghĩa bên dưới `spring.pulsar.client.authentication.param.*` khớp chính xác với những tên mà plugin xác thực của bạn mong đợi (thường ở dạng camel case). Spring Boot sẽ không thử bất kỳ kiểu relaxed binding nào cho các mục này.
>
> Ví dụ, nếu bạn muốn cấu hình issuer url cho plugin xác thực `AuthenticationOAuth2`, bạn phải dùng `spring.pulsar.client.authentication.param.issuerUrl`. Nếu bạn dùng các dạng khác, như `issuerurl` hoặc `issuer-url`, thiết lập sẽ không được áp dụng cho plugin.
>
> Việc thiếu relaxed binding này cũng khiến việc dùng biến môi trường cho các tham số xác thực trở nên khó khăn, vì tính phân biệt hoa thường bị mất trong quá trình chuyển đổi. Nếu bạn dùng biến môi trường cho các tham số thì bạn sẽ cần làm theo [các bước này](https://docs.spring.io/spring-pulsar/docs/1.0.5/reference/reference/pulsar.html#client-authentication-env-vars) trong tài liệu tham khảo Spring for Apache Pulsar để nó hoạt động đúng.

## SSL

Theo mặc định, các client Pulsar giao tiếp với các service Pulsar ở dạng văn bản thuần (plain text). Bạn có thể làm theo [các bước này](https://docs.spring.io/spring-pulsar/docs/1.0.5/reference/reference/pulsar.html#tls-encryption) trong tài liệu tham khảo Spring for Apache Pulsar để bật mã hóa TLS.

Để biết đầy đủ chi tiết về client và xác thực, xem [tài liệu tham khảo](https://docs.spring.io/spring-pulsar/docs/1.0.5/reference/reference/pulsar.html#pulsar-client) của Spring for Apache Pulsar.

# 18.3.7. Cấu hình SSL (Configure SSL)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.webserver.configure-ssl>

SSL có thể được cấu hình theo cách khai báo bằng cách đặt các property `server.ssl.*` khác nhau, thường là trong `application.properties` hoặc `application.yaml`. Ví dụ sau cho thấy việc đặt các property SSL bằng một file Java KeyStore:

**Properties**

```properties
server.port=8443
server.ssl.key-store=classpath:keystore.jks
server.ssl.key-store-password=secret
server.ssl.key-password=another-secret
```

**Yaml**

```yaml
server:
  port: 8443
  ssl:
    key-store: "classpath:keystore.jks"
    key-store-password: "secret"
    key-password: "another-secret"
```

Dùng cấu hình như ví dụ trên nghĩa là ứng dụng không còn hỗ trợ HTTP connector thuần ở port 8080 nữa. Spring Boot không hỗ trợ cấu hình cả HTTP connector lẫn HTTPS connector thông qua `application.properties`. Nếu bạn muốn có cả hai, bạn cần cấu hình một trong số chúng bằng lập trình. Chúng tôi khuyến nghị dùng `application.properties` để cấu hình HTTPS, vì HTTP connector là cái dễ cấu hình bằng lập trình hơn trong hai cái.

## Sử dụng file mã hóa PEM (Using PEM-encoded files)

Bạn có thể dùng các file mã hóa PEM thay cho các file Java KeyStore. Bạn nên dùng các file key PKCS#8 bất cứ khi nào có thể. Các file key PKCS#8 mã hóa PEM bắt đầu bằng header `-----BEGIN PRIVATE KEY-----` hoặc `-----BEGIN ENCRYPTED PRIVATE KEY-----`.

Nếu bạn có các file ở định dạng khác, ví dụ PKCS#1 (`-----BEGIN RSA PRIVATE KEY-----`) hoặc SEC 1 (`-----BEGIN EC PRIVATE KEY-----`), bạn có thể chuyển đổi chúng sang PKCS#8 bằng OpenSSL:

```shell
openssl pkcs8 -topk8 -nocrypt -in <input file> -out <output file>
```

Ví dụ sau cho thấy việc đặt các property SSL bằng các file certificate và private key mã hóa PEM:

**Properties**

```properties
server.port=8443
server.ssl.certificate=classpath:my-cert.crt
server.ssl.certificate-private-key=classpath:my-cert.key
server.ssl.trust-certificate=classpath:ca-cert.crt
```

**Yaml**

```yaml
server:
  port: 8443
  ssl:
    certificate: "classpath:my-cert.crt"
    certificate-private-key: "classpath:my-cert.key"
    trust-certificate: "classpath:ca-cert.crt"
```

Ngoài ra, SSL trust material có thể được cấu hình trong một [SSL bundle](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.ssl) và áp dụng cho web server như trong ví dụ này:

**Properties**

```properties
server.port=8443
server.ssl.bundle=example
```

**Yaml**

```yaml
server:
  port: 8443
  ssl:
    bundle: "example"
```

> **LƯU Ý (NOTE):** Property `server.ssl.bundle` không thể được kết hợp với các tùy chọn property Java KeyStore hoặc PEM riêng lẻ trong `server.ssl`.

Xem [`Ssl`](https://github.com/spring-projects/spring-boot/tree/v3.2.5/spring-boot-project/spring-boot/src/main/java/org/springframework/boot/web/server/Ssl.java) để biết chi tiết về tất cả các property được hỗ trợ.

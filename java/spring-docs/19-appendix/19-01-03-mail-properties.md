# A.3. Property mail (Mail Properties)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#appendix.application-properties.mail>

| Tên | Mô tả | Giá trị mặc định |
|---|---|---|
| [`spring.mail.default-encoding`](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#application-properties.mail.spring.mail.default-encoding) | Encoding mặc định của MimeMessage. | `UTF-8` |
| [`spring.mail.host`](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#application-properties.mail.spring.mail.host) | Host của SMTP server. Chẳng hạn, 'smtp.example.com'. |  |
| [`spring.mail.jndi-name`](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#application-properties.mail.spring.mail.jndi-name) | Tên JNDI của Session. Khi được đặt, nó được ưu tiên hơn các thiết lập Session khác. |  |
| [`spring.mail.password`](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#application-properties.mail.spring.mail.password) | Mật khẩu đăng nhập của SMTP server. |  |
| [`spring.mail.port`](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#application-properties.mail.spring.mail.port) | Port của SMTP server. |  |
| [`spring.mail.properties.*`](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#application-properties.mail.spring.mail.properties) | Các property bổ sung của JavaMail Session. |  |
| [`spring.mail.protocol`](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#application-properties.mail.spring.mail.protocol) | Giao thức được dùng bởi SMTP server. | `smtp` |
| [`spring.mail.test-connection`](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#application-properties.mail.spring.mail.test-connection) | Có kiểm tra mail server sẵn sàng lúc khởi động hay không. | `false` |
| [`spring.mail.username`](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#application-properties.mail.spring.mail.username) | Người dùng đăng nhập của SMTP server. |  |
| [`spring.sendgrid.api-key`](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#application-properties.mail.spring.sendgrid.api-key) | API key của SendGrid. |  |
| [`spring.sendgrid.proxy.host`](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#application-properties.mail.spring.sendgrid.proxy.host) | Host proxy của SendGrid. |  |
| [`spring.sendgrid.proxy.port`](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#application-properties.mail.spring.sendgrid.proxy.port) | Port proxy của SendGrid. |  |

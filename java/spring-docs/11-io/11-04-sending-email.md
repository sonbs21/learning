# 11.4. Gửi email (Sending Email)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#io.email>

Spring Framework cung cấp một abstraction để gửi email bằng interface `JavaMailSender`, và Spring Boot cung cấp auto-configuration cho nó cũng như một module starter.

> **MẸO (TIP):** Xem [tài liệu tham khảo](https://docs.spring.io/spring-framework/reference/6.1/integration/email.html) để có giải thích chi tiết về cách bạn có thể dùng `JavaMailSender`.

Nếu có `spring.mail.host` và các thư viện liên quan (như được định nghĩa bởi `spring-boot-starter-mail`), một `JavaMailSender` mặc định sẽ được tạo nếu chưa có. Sender có thể được tùy chỉnh thêm bằng các mục cấu hình từ namespace `spring.mail`. Xem [`MailProperties`](https://github.com/spring-projects/spring-boot/tree/v3.2.5/spring-boot-project/spring-boot-autoconfigure/src/main/java/org/springframework/boot/autoconfigure/mail/MailProperties.java) để biết thêm chi tiết.

Đặc biệt, một số giá trị timeout mặc định là vô hạn, và bạn có thể muốn thay đổi điều đó để tránh việc một thread bị chặn bởi một mail server không phản hồi, như trong ví dụ sau:

**Properties**

```properties
spring.mail.properties[mail.smtp.connectiontimeout]=5000
spring.mail.properties[mail.smtp.timeout]=3000
spring.mail.properties[mail.smtp.writetimeout]=5000
```

**Yaml**

```yaml
spring:
  mail:
    properties:
      "[mail.smtp.connectiontimeout]": 5000
      "[mail.smtp.timeout]": 3000
      "[mail.smtp.writetimeout]": 5000
```

Cũng có thể cấu hình một `JavaMailSender` với một `Session` hiện có từ JNDI:

**Properties**

```properties
spring.mail.jndi-name=mail/Session
```

**Yaml**

```yaml
spring:
  mail:
    jndi-name: "mail/Session"
```

Khi `jndi-name` được đặt, nó được ưu tiên hơn tất cả các thiết lập khác liên quan đến Session.

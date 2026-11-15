# 18.2.8. Thay đổi cấu hình tùy theo môi trường (Change Configuration Depending on the Environment)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.properties-and-configuration.change-configuration-depending-on-the-environment>

Spring Boot hỗ trợ các file YAML và Properties nhiều tài liệu (xem [Làm việc với file nhiều tài liệu (Working With Multi-Document Files)](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.external-config.files.multi-document) để biết chi tiết), có thể được kích hoạt có điều kiện dựa trên các profile đang hoạt động.

Nếu một tài liệu chứa key `spring.config.activate.on-profile`, thì giá trị profile (một danh sách profile phân tách bằng dấu phẩy hoặc một biểu thức profile) được đưa vào phương thức `Environment.acceptsProfiles()` của Spring. Nếu biểu thức profile khớp thì tài liệu đó được đưa vào lần hợp nhất cuối cùng (ngược lại thì không), như trong ví dụ sau:

**Properties**

```properties
server.port=9000
#---
spring.config.activate.on-profile=development
server.port=9001
#---
spring.config.activate.on-profile=production
server.port=0
```

**Yaml**

```yaml
server:
  port: 9000
---
spring:
  config:
    activate:
      on-profile: "development"
server:
  port: 9001
---
spring:
  config:
    activate:
      on-profile: "production"
server:
  port: 0
```

Trong ví dụ trên, port mặc định là 9000. Tuy nhiên, nếu Spring profile tên 'development' đang hoạt động, thì port là 9001. Nếu 'production' đang hoạt động, thì port là 0.

> **LƯU Ý (NOTE):** Các tài liệu được hợp nhất theo thứ tự mà chúng được gặp. Các giá trị sau ghi đè các giá trị trước.

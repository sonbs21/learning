# 14.1.8. Google Cloud

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#deployment.cloud.google>

Google Cloud có một số tùy chọn có thể dùng để khởi chạy ứng dụng Spring Boot. Cách dễ nhất để bắt đầu có lẽ là App Engine, nhưng bạn cũng có thể tìm cách chạy Spring Boot trong một container với Container Engine hoặc trên một máy ảo với Compute Engine.

Để triển khai ứng dụng đầu tiên lên môi trường tiêu chuẩn của App Engine, hãy làm theo [hướng dẫn này](https://codelabs.developers.google.com/codelabs/cloud-app-engine-springboot#0).

Ngoài ra, App Engine Flex yêu cầu bạn tạo một file `app.yaml` để mô tả các tài nguyên mà ứng dụng của bạn cần. Thông thường, bạn đặt file này trong `src/main/appengine`, và nó nên trông giống như file sau:

```yaml
service: "default"

runtime: "java17"
env: "flex"

handlers:
- url: "/.*"
  script: "this field is required, but ignored"

manual_scaling:
  instances: 1

health_check:
  enable_health_check: false

env_variables:
  ENCRYPT_KEY: "your_encryption_key_here"
```

Bạn có thể triển khai ứng dụng (ví dụ, bằng một Maven plugin) bằng cách thêm project ID vào cấu hình build, như trong ví dụ sau:

```xml
<plugin>
    <groupId>com.google.cloud.tools</groupId>
    <artifactId>appengine-maven-plugin</artifactId>
    <version>2.4.4</version>
    <configuration>
        <project>myproject</project>
    </configuration>
</plugin>
```

Sau đó triển khai bằng `mvn appengine:deploy` (bạn cần xác thực trước, nếu không bản build sẽ thất bại).

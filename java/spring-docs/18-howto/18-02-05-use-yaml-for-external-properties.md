# 18.2.5. Dùng YAML cho property bên ngoài (Use YAML for External Properties)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.properties-and-configuration.yaml>

YAML là một tập cha (superset) của JSON và vì thế là một cú pháp tiện lợi để lưu trữ các property bên ngoài theo định dạng phân cấp, như trong ví dụ sau:

```yaml
spring:
  application:
    name: "cruncher"
  datasource:
    driver-class-name: "com.mysql.jdbc.Driver"
    url: "jdbc:mysql://localhost/test"
server:
  port: 9000
```

Tạo một file tên là `application.yaml` và đặt nó ở gốc classpath. Sau đó thêm `snakeyaml` vào các dependency (tọa độ Maven `org.yaml:snakeyaml`, đã được bao gồm sẵn nếu bạn dùng `spring-boot-starter`). Một file YAML được parse thành một `Map<String,Object>` của Java (giống như một JSON object), và Spring Boot làm phẳng map đó để nó chỉ sâu một cấp và có các key phân tách bằng dấu chấm, như nhiều người đã quen với các file `Properties` trong Java.

YAML ví dụ ở trên tương ứng với file `application.properties` sau:

```properties
spring.application.name=cruncher
spring.datasource.driver-class-name=com.mysql.jdbc.Driver
spring.datasource.url=jdbc:mysql://localhost/test
server.port=9000
```

Xem "[Làm việc với YAML (Working With YAML)](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.external-config.yaml)" trong phần 'Các tính năng của Spring Boot' để biết thêm thông tin về YAML.

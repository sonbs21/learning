# 18.2.4. Dùng tham số dòng lệnh 'ngắn' (Use 'Short' Command Line Arguments)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.properties-and-configuration.short-command-line-arguments>

Một số người thích dùng (ví dụ) `--port=9000` thay vì `--server.port=9000` để đặt các property cấu hình trên dòng lệnh. Bạn có thể bật hành vi này bằng cách dùng placeholder trong `application.properties`, như trong ví dụ sau:

**Properties**

```properties
server.port=${port:8080}
```

**Yaml**

```yaml
server:
  port: "${port:8080}"
```

> **MẸO (TIP):** Nếu bạn kế thừa từ POM `spring-boot-starter-parent`, filter token mặc định của `maven-resources-plugins` đã được đổi từ `${*}` thành `@` (tức là `@maven.token@` thay vì `${maven.token}`) để tránh xung đột với các placeholder kiểu Spring. Nếu bạn đã bật Maven filtering trực tiếp cho `application.properties`, bạn cũng có thể muốn đổi filter token mặc định để dùng [các dấu phân cách khác](https://maven.apache.org/plugins/maven-resources-plugin/resources-mojo.html#delimiters).

> **LƯU Ý (NOTE):** Trong trường hợp cụ thể này, việc bind port hoạt động trong môi trường PaaS như Heroku hoặc Cloud Foundry. Trên hai nền tảng đó, biến môi trường `PORT` được đặt tự động và Spring có thể bind tới các từ đồng nghĩa viết hoa của các property `Environment`.

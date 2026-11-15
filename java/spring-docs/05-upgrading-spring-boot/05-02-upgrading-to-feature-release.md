# 5.2. Nâng cấp lên bản phát hành tính năng mới (Upgrading to a New Feature Release)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#upgrading.to-feature>

Khi nâng cấp lên một bản phát hành tính năng mới, một số property có thể đã bị đổi tên hoặc bị xóa. Spring Boot cung cấp một cách để phân tích environment của ứng dụng và in ra thông tin chẩn đoán khi khởi động, đồng thời tạm thời chuyển đổi (migrate) các property giúp bạn tại runtime. Để bật tính năng đó, hãy thêm dependency sau vào project của bạn:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-properties-migrator</artifactId>
    <scope>runtime</scope>
</dependency>
```

> **CẢNH BÁO (WARNING):** Các property được thêm muộn vào environment, chẳng hạn như khi dùng `@PropertySource`, sẽ không được tính đến.

> **LƯU Ý (NOTE):** Khi bạn hoàn tất việc chuyển đổi, hãy đảm bảo xóa module này khỏi các dependency của project.

# 13.1. Bật các tính năng sẵn sàng cho production (Enabling Production-ready Features)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#actuator.enabling>

Module [`spring-boot-actuator`](https://github.com/spring-projects/spring-boot/tree/v3.2.5/spring-boot-project/spring-boot-actuator) cung cấp tất cả các tính năng sẵn sàng cho production của Spring Boot. Cách được khuyến nghị để bật các tính năng này là thêm dependency tới "Starter" `spring-boot-starter-actuator`.

> **Định nghĩa Actuator (Definition of Actuator)**
>
> Actuator là một thuật ngữ trong sản xuất, chỉ một thiết bị cơ khí dùng để di chuyển hoặc điều khiển một thứ gì đó. Actuator có thể tạo ra một lượng chuyển động lớn từ một thay đổi nhỏ.

Để thêm actuator vào một project dựa trên Maven, hãy thêm dependency "Starter" sau:

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>
</dependencies>
```

Với Gradle, hãy dùng khai báo sau:

```gradle
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
}
```

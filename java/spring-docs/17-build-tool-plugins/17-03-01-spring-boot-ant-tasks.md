# 17.3.1. Các task Ant của Spring Boot (Spring Boot Ant Tasks)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#build-tool-plugins.antlib.tasks>

Sau khi namespace `spring-boot-antlib` đã được khai báo, các task bổ sung sau có sẵn:

- [Sử dụng task "exejar" (Using the "exejar" Task)](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#build-tool-plugins.antlib.tasks.exejar)
- [Sử dụng task "findmainclass" (Using the "findmainclass" Task)](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#build-tool-plugins.antlib.findmainclass)

## Sử dụng task "exejar" (Using the "exejar" Task)

Bạn có thể dùng task `exejar` để tạo một jar thực thi Spring Boot. Các thuộc tính sau được task hỗ trợ:

| Thuộc tính | Mô tả | Bắt buộc |
|---|---|---|
| `destfile` | File jar đích cần tạo | Có |
| `classes` | Thư mục gốc của các file class Java | Có |
| `start-class` | Class ứng dụng chính cần chạy | Không *(mặc định là class đầu tiên được tìm thấy có khai báo phương thức `main`)* |

Các phần tử lồng nhau sau có thể được dùng với task:

| Phần tử | Mô tả |
|---|---|
| `resources` | Một hoặc nhiều [Resource Collection](https://ant.apache.org/manual/Types/resources.html#collection) mô tả một tập hợp [Resource](https://ant.apache.org/manual/Types/resources.html) nên được thêm vào nội dung của file jar được tạo ra. |
| `lib` | Một hoặc nhiều [Resource Collection](https://ant.apache.org/manual/Types/resources.html#collection) nên được thêm vào tập hợp các thư viện jar tạo thành classpath dependency runtime của ứng dụng. |

## Ví dụ (Examples)

Phần này trình bày hai ví dụ về các task Ant.

**Chỉ định start-class (Specify start-class)**

```xml
<spring-boot:exejar destfile="target/my-application.jar"
        classes="target/classes" start-class="com.example.MyApplication">
    <resources>
        <fileset dir="src/main/resources" />
    </resources>
    <lib>
        <fileset dir="lib" />
    </lib>
</spring-boot:exejar>
```

**Tự phát hiện start-class (Detect start-class)**

```xml
<exejar destfile="target/my-application.jar" classes="target/classes">
    <lib>
        <fileset dir="lib" />
    </lib>
</exejar>
```

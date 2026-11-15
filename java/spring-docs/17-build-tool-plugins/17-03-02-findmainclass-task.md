# 17.3.2. Sử dụng task "findmainclass" (Using the "findmainclass" Task)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#build-tool-plugins.antlib.findmainclass>

Task `findmainclass` được `exejar` dùng nội bộ để định vị một class có khai báo `main`. Nếu cần, bạn cũng có thể dùng task này trực tiếp trong bản build. Các thuộc tính sau được hỗ trợ:

| Thuộc tính | Mô tả | Bắt buộc |
|---|---|---|
| `classesroot` | Thư mục gốc của các file class Java | Có *(trừ khi `mainclass` được chỉ định)* |
| `mainclass` | Có thể được dùng để bỏ qua việc tìm kiếm class `main` | Không |
| `property` | Property Ant nên được đặt với kết quả | Không *(kết quả sẽ được ghi log nếu không được chỉ định)* |

## Ví dụ (Examples)

Phần này chứa ba ví dụ về việc dùng `findmainclass`.

**Tìm và ghi log (Find and log)**

```xml
<findmainclass classesroot="target/classes" />
```

**Tìm và đặt giá trị (Find and set)**

```xml
<findmainclass classesroot="target/classes" property="main-class" />
```

**Ghi đè và đặt giá trị (Override and set)**

```xml
<findmainclass mainclass="com.example.MainClass" property="main-class" />
```

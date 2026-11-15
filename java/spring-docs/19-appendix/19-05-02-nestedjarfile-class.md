# E.2. Class "NestedJarFile" của Spring Boot (Spring Boot's "NestedJarFile" Class)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#appendix.executable-jar.jarfile-class>

Class cốt lõi được dùng để hỗ trợ nạp các jar lồng nhau là `org.springframework.boot.loader.jar.NestedJarFile`. Nó cho phép bạn nạp nội dung jar từ dữ liệu của jar con lồng nhau. Khi được nạp lần đầu, vị trí của mỗi `JarEntry` được ánh xạ tới một offset file vật lý của jar bên ngoài, như trong ví dụ sau:

```
myapp.jar
+-------------------+-------------------------+
| /BOOT-INF/classes | /BOOT-INF/lib/mylib.jar |
|+-----------------+||+-----------+----------+|
||     A.class      |||  B.class  |  C.class ||
|+-----------------+||+-----------+----------+|
+-------------------+-------------------------+
 ^                    ^           ^
 0063                 3452        3980
```

Ví dụ trên cho thấy cách `A.class` có thể được tìm thấy trong `/BOOT-INF/classes` trong `myapp.jar` tại vị trí `0063`. `B.class` từ jar lồng nhau thực ra có thể được tìm thấy trong `myapp.jar` tại vị trí `3452`, và `C.class` ở vị trí `3980`.

Với thông tin này, chúng ta có thể nạp các mục lồng nhau cụ thể bằng cách di chuyển (seek) tới phần thích hợp của jar bên ngoài. Chúng ta không cần giải nén file nén, và không cần đọc toàn bộ dữ liệu của các mục vào bộ nhớ.

## Tương thích với "JarFile" chuẩn của Java (Compatibility With the Standard Java "JarFile")

Spring Boot Loader cố gắng duy trì khả năng tương thích với code và thư viện hiện có. `org.springframework.boot.loader.jar.NestedJarFile` kế thừa từ `java.util.jar.JarFile` và có thể hoạt động như một sự thay thế trực tiếp (drop-in replacement). Các URL JAR lồng nhau có dạng `jar:nested:/path/myjar.jar/!BOOT-INF/lib/mylib.jar!/B.class` được hỗ trợ và mở một kết nối tương thích với `java.net.JarURLConnection`. Chúng có thể được dùng với `URLClassLoader` của Java.

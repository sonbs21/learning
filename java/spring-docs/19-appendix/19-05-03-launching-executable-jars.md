# E.3. Khởi chạy jar thực thi (Launching Executable Jars)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#appendix.executable-jar.launching>

Class `org.springframework.boot.loader.launch.Launcher` là một class bootstrap đặc biệt được dùng làm điểm vào chính của một jar thực thi. Nó là `Main-Class` thực sự trong file jar của bạn, và nó được dùng để thiết lập một `ClassLoader` phù hợp và cuối cùng gọi phương thức `main()` của bạn.

Có ba subclass launcher (`JarLauncher`, `WarLauncher`, và `PropertiesLauncher`). Mục đích của chúng là nạp các resource (file `.class`, v.v.) từ các file jar lồng nhau hoặc các file war trong thư mục (trái ngược với những resource nằm tường minh trên classpath). Trong trường hợp `JarLauncher` và `WarLauncher`, các đường dẫn lồng nhau là cố định. `JarLauncher` tìm trong `BOOT-INF/lib/`, và `WarLauncher` tìm trong `WEB-INF/lib/` và `WEB-INF/lib-provided/`. Bạn có thể thêm jar vào những vị trí đó nếu muốn có thêm. `PropertiesLauncher` mặc định tìm trong `BOOT-INF/lib/` trong file nén ứng dụng của bạn. Bạn có thể thêm các vị trí bổ sung bằng cách đặt một biến môi trường tên là `LOADER_PATH` hoặc `loader.path` trong `loader.properties` (là một danh sách các thư mục, file nén, hoặc thư mục bên trong file nén, phân tách bằng dấu phẩy).

## Manifest của launcher (Launcher Manifest)

Bạn cần chỉ định một `Launcher` phù hợp làm thuộc tính `Main-Class` của `META-INF/MANIFEST.MF`. Class thực sự mà bạn muốn khởi chạy (tức là class chứa phương thức `main`) nên được chỉ định trong thuộc tính `Start-Class`.

Ví dụ sau cho thấy một `MANIFEST.MF` điển hình cho một file jar thực thi:

```
Main-Class: org.springframework.boot.loader.launch.JarLauncher
Start-Class: com.mycompany.project.MyApplication
```

Đối với một file war, nó sẽ như sau:

```
Main-Class: org.springframework.boot.loader.launch.WarLauncher
Start-Class: com.mycompany.project.MyApplication
```

> **LƯU Ý (NOTE):** Bạn không cần chỉ định các mục `Class-Path` trong file manifest. Classpath được suy ra từ các jar lồng nhau.

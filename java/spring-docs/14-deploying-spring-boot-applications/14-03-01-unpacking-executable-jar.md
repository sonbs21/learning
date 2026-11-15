# 14.3.1. Giải nén jar thực thi (Unpacking the Executable JAR)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#deployment.efficient.unpacking>

Nếu bạn chạy ứng dụng từ một container, bạn có thể dùng một jar thực thi, nhưng việc giải nén nó và chạy theo một cách khác cũng thường có lợi. Một số implementation PaaS cũng có thể chọn giải nén file nén trước khi chạy. Ví dụ, Cloud Foundry hoạt động theo cách này. Một cách để chạy một file nén đã giải nén là khởi động launcher phù hợp, như sau:

```shell
$ jar -xf myapp.jar
$ java org.springframework.boot.loader.launch.JarLauncher
```

Cách này thực ra nhanh hơn một chút khi khởi động (tùy thuộc vào kích thước của jar) so với chạy từ một file nén chưa giải nén. Sau khi khởi động, bạn không nên mong đợi bất kỳ sự khác biệt nào.

Sau khi đã giải nén file jar, bạn cũng có thể tăng thêm tốc độ khởi động bằng cách chạy ứng dụng bằng phương thức main "tự nhiên" của nó thay vì `JarLauncher`. Ví dụ:

```shell
$ jar -xf myapp.jar
$ java -cp "BOOT-INF/classes:BOOT-INF/lib/*" com.example.MyApplication
```

> **LƯU Ý (NOTE):** Dùng `JarLauncher` thay vì phương thức main của ứng dụng có thêm lợi ích là thứ tự classpath có thể dự đoán được. Jar chứa một file `classpath.idx` được `JarLauncher` dùng khi xây dựng classpath.

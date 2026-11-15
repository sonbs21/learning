# E.5. Các hạn chế của jar thực thi (Executable Jar Restrictions)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#appendix.executable-jar.restrictions>

Bạn cần cân nhắc các hạn chế sau khi làm việc với một ứng dụng được đóng gói bằng Spring Boot Loader:

- Nén mục zip (Zip entry compression): `ZipEntry` của một jar lồng nhau phải được lưu bằng phương thức `ZipEntry.STORED`. Điều này là bắt buộc để chúng ta có thể di chuyển (seek) trực tiếp tới từng nội dung riêng lẻ bên trong jar lồng nhau. Bản thân nội dung của file jar lồng nhau vẫn có thể được nén, cũng như bất kỳ mục nào khác trong jar bên ngoài.
- System classLoader: Các ứng dụng được khởi chạy nên dùng `Thread.getContextClassLoader()` khi nạp class (hầu hết các thư viện và framework làm vậy theo mặc định). Việc cố gắng nạp các class của jar lồng nhau bằng `ClassLoader.getSystemClassLoader()` sẽ thất bại. `java.util.Logging` luôn dùng system classloader. Vì lý do này, bạn nên cân nhắc một implementation logging khác.

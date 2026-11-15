# 18.9.2. Khởi tạo cơ sở dữ liệu bằng Hibernate (Initialize a Database Using Hibernate)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.data-initialization.using-hibernate>

Bạn có thể đặt tường minh `spring.jpa.hibernate.ddl-auto` thành một trong các giá trị property chuẩn của Hibernate là `none`, `validate`, `update`, `create`, và `create-drop`. Spring Boot chọn một giá trị mặc định cho bạn dựa trên việc nó cho rằng cơ sở dữ liệu của bạn có phải là embedded hay không. Nó mặc định là `create-drop` nếu không phát hiện schema manager nào hoặc `none` trong tất cả các trường hợp khác. Một embedded database được phát hiện bằng cách xem kiểu `Connection` và JDBC url. `hsqldb`, `h2`, và `derby` là các ứng viên, còn những cái khác thì không. Hãy cẩn thận khi chuyển từ cơ sở dữ liệu in-memory sang một cơ sở dữ liệu 'thực', đừng giả định về sự tồn tại của các bảng và dữ liệu trên nền tảng mới. Bạn hoặc phải đặt `ddl-auto` một cách tường minh hoặc dùng một trong các cơ chế khác để khởi tạo cơ sở dữ liệu.

> **LƯU Ý (NOTE):** Bạn có thể xuất ra quá trình tạo schema bằng cách bật logger `org.hibernate.SQL`. Điều này được làm tự động cho bạn nếu bạn bật [chế độ debug](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.logging.console-output).

Ngoài ra, một file tên là `import.sql` ở gốc classpath sẽ được thực thi lúc khởi động nếu Hibernate tạo schema từ đầu (tức là nếu property `ddl-auto` được đặt thành `create` hoặc `create-drop`). Điều này có thể hữu ích cho demo và cho kiểm thử nếu bạn cẩn thận nhưng có lẽ không phải là thứ bạn muốn có trên classpath trong production. Đây là một tính năng của Hibernate (và không liên quan gì đến Spring).

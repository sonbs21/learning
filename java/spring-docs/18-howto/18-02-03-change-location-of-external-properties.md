# 18.2.3. Thay đổi vị trí các property bên ngoài của ứng dụng (Change the Location of External Properties of an Application)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.properties-and-configuration.external-properties-location>

Theo mặc định, các property từ các nguồn khác nhau được thêm vào `Environment` của Spring theo một thứ tự xác định (xem "[Cấu hình bên ngoài (Externalized Configuration)](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.external-config)" trong phần 'Các tính năng của Spring Boot' để biết thứ tự chính xác).

Bạn cũng có thể cung cấp các System property (hoặc biến môi trường) sau để thay đổi hành vi:

- `spring.config.name` (`SPRING_CONFIG_NAME`): Mặc định là `application`, làm phần gốc của tên file.
- `spring.config.location` (`SPRING_CONFIG_LOCATION`): File cần nạp (chẳng hạn như một classpath resource hoặc một URL). Một property source `Environment` riêng được thiết lập cho tài liệu này và nó có thể bị ghi đè bởi system property, biến môi trường, hoặc dòng lệnh.

Bất kể bạn đặt gì trong environment, Spring Boot luôn nạp `application.properties` như đã mô tả ở trên. Theo mặc định, nếu YAML được dùng, thì các file có phần mở rộng '.yaml' và '.yml' cũng được thêm vào danh sách.

> **MẸO (TIP):** Nếu bạn muốn thông tin chi tiết về các file đang được nạp, bạn có thể [đặt mức logging](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.logging.log-levels) của `org.springframework.boot.context.config` thành `trace`.

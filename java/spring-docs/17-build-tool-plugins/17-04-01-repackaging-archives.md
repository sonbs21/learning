# 17.4.1. Đóng gói lại file nén (Repackaging Archives)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#build-tool-plugins.other-build-systems.repackaging-archives>

Để đóng gói lại một file nén hiện có sao cho nó trở thành một file nén thực thi khép kín, hãy dùng `org.springframework.boot.loader.tools.Repackager`. Class `Repackager` nhận một tham số constructor duy nhất tham chiếu tới một file nén jar hoặc war hiện có. Dùng một trong hai phương thức `repackage()` có sẵn để thay thế file gốc hoặc ghi vào một đích mới. Nhiều thiết lập khác nhau cũng có thể được cấu hình trên repackager trước khi nó được chạy.

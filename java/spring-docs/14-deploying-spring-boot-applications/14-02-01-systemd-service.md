# 14.2.1. Cài đặt như một service systemd (Installation as a systemd Service)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#deployment.installing.system-d>

`systemd` là hệ thống kế nhiệm của hệ thống init System V và hiện đang được nhiều bản phân phối Linux hiện đại sử dụng. Các ứng dụng Spring Boot có thể được khởi chạy bằng các script 'service' của `systemd`.

Giả sử bạn có một ứng dụng Spring Boot được đóng gói dưới dạng uber jar trong `/var/myapp`, để cài đặt nó như một service `systemd`, hãy tạo một script tên là `myapp.service` và đặt nó trong thư mục `/etc/systemd/system`. Script sau cung cấp một ví dụ:

```
[Unit]
Description=myapp
After=syslog.target network.target

[Service]
User=myapp
Group=myapp

Environment="JAVA_HOME=/path/to/java/home"

ExecStart=${JAVA_HOME}/bin/java -jar /var/myapp/myapp.jar
ExecStop=/bin/kill -15 $MAINPID
SuccessExitStatus=143

[Install]
WantedBy=multi-user.target
```

> **QUAN TRỌNG (IMPORTANT):** Hãy nhớ thay đổi các trường `Description`, `User`, `Group`, `Environment` và `ExecStart` cho ứng dụng của bạn.

> **LƯU Ý (NOTE):** Trường `ExecStart` không khai báo lệnh hành động của script, nghĩa là lệnh `run` được dùng theo mặc định.

User chạy ứng dụng, file PID, và file log console được chính `systemd` quản lý và do đó phải được cấu hình bằng các trường phù hợp trong script 'service'. Tham khảo [trang man về cấu hình service unit](https://www.freedesktop.org/software/systemd/man/systemd.service.html) để biết thêm chi tiết.

Để đánh dấu ứng dụng tự động khởi động khi hệ thống boot, hãy dùng lệnh sau:

```shell
$ systemctl enable myapp.service
```

Chạy `man systemctl` để biết thêm chi tiết.

# 18.12.3. Chạy từ dòng lệnh (Running From the Command Line)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.batch.running-from-the-command-line>

Spring Boot chuyển đổi mọi tham số dòng lệnh bắt đầu bằng `--` thành một property để thêm vào `Environment`, xem [truy cập các property dòng lệnh](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.external-config.command-line-args). Điều này không nên được dùng để truyền tham số cho các batch job. Để chỉ định tham số batch trên dòng lệnh, hãy dùng định dạng thông thường (tức là không có `--`), như trong ví dụ sau:

```shell
$ java -jar myapp.jar someParameter=someValue anotherParameter=anotherValue
```

Nếu bạn chỉ định một property của `Environment` trên dòng lệnh, nó sẽ bị job bỏ qua. Hãy xem lệnh sau:

```shell
$ java -jar myapp.jar --server.port=7070 someParameter=someValue
```

Lệnh này chỉ cung cấp một tham số cho batch job: `someParameter=someValue`.

# 16.2.2. Sử dụng shell nhúng (Using the Embedded Shell)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#cli.using-the-cli.embedded-shell>

Spring Boot bao gồm các script tự động hoàn thành lệnh cho các shell BASH và zsh. Nếu bạn không dùng shell nào trong số này (có thể bạn là người dùng Windows), bạn có thể dùng lệnh `shell` để khởi chạy một shell tích hợp, như trong ví dụ sau:

```shell
$ spring shell
Spring Boot (v3.2.5)
Hit TAB to complete. Type \'help' and hit RETURN for help, and \'exit' to quit.
```

Từ bên trong shell nhúng, bạn có thể chạy trực tiếp các lệnh khác:

```shell
$ version
Spring CLI v3.2.5
```

Shell nhúng hỗ trợ output có màu ANSI cũng như tự động hoàn thành bằng phím `tab`. Nếu bạn cần chạy một lệnh gốc (native) của hệ thống, bạn có thể dùng tiền tố `!`. Để thoát khỏi shell nhúng, hãy nhấn `ctrl-c`.

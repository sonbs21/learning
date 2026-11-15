# A.16. Property Docker Compose (Docker Compose Properties)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#appendix.application-properties.docker-compose>

| Tên | Mô tả | Giá trị mặc định |
|---|---|---|
| [`spring.docker.compose.enabled`](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#application-properties.docker-compose.spring.docker.compose.enabled) | Hỗ trợ docker compose có được bật hay không. | `true` |
| [`spring.docker.compose.file`](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#application-properties.docker-compose.spring.docker.compose.file) | Đường dẫn tới một file cấu hình docker compose cụ thể. |  |
| [`spring.docker.compose.host`](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#application-properties.docker-compose.spring.docker.compose.host) | Hostname hoặc IP của máy mà các docker container được khởi động trên đó. |  |
| [`spring.docker.compose.lifecycle-management`](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#application-properties.docker-compose.spring.docker.compose.lifecycle-management) | Quản lý vòng đời Docker compose. | `start-and-stop` |
| [`spring.docker.compose.profiles.active`](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#application-properties.docker-compose.spring.docker.compose.profiles.active) | Các profile Docker compose nên được kích hoạt. |  |
| [`spring.docker.compose.readiness.tcp.connect-timeout`](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#application-properties.docker-compose.spring.docker.compose.readiness.tcp.connect-timeout) | Timeout cho kết nối. | `200ms` |
| [`spring.docker.compose.readiness.tcp.read-timeout`](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#application-properties.docker-compose.spring.docker.compose.readiness.tcp.read-timeout) | Timeout cho việc đọc. | `200ms` |
| [`spring.docker.compose.readiness.timeout`](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#application-properties.docker-compose.spring.docker.compose.readiness.timeout) | Timeout của các kiểm tra readiness. | `2m` |
| [`spring.docker.compose.readiness.wait`](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#application-properties.docker-compose.spring.docker.compose.readiness.wait) | Wait strategy cần dùng. | `always` |
| [`spring.docker.compose.skip.in-tests`](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#application-properties.docker-compose.spring.docker.compose.skip.in-tests) | Có bỏ qua trong test hay không. | `true` |
| [`spring.docker.compose.start.command`](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#application-properties.docker-compose.spring.docker.compose.start.command) | Lệnh dùng để khởi động docker compose. | `up` |
| [`spring.docker.compose.start.log-level`](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#application-properties.docker-compose.spring.docker.compose.start.log-level) | Log level cho output. | `info` |
| [`spring.docker.compose.stop.command`](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#application-properties.docker-compose.spring.docker.compose.stop.command) | Lệnh dùng để dừng docker compose. | `stop` |
| [`spring.docker.compose.stop.timeout`](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#application-properties.docker-compose.spring.docker.compose.stop.timeout) | Timeout cho việc dừng Docker Compose. Dùng '0' để buộc dừng. | `10s` |

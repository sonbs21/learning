# 18.20.2. Chia sẻ service giữa nhiều ứng dụng (Sharing services between multiple applications)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.docker-compose.sharing-services>

Nếu bạn muốn chia sẻ service giữa nhiều ứng dụng, hãy tạo file `compose.yaml` trong một trong các ứng dụng và sau đó dùng property cấu hình `spring.docker.compose.file` trong các ứng dụng khác để tham chiếu tới file `compose.yaml`. Bạn cũng nên đặt `spring.docker.compose.lifecycle-management` thành `start-only`, vì nó mặc định là `start-and-stop` và việc dừng một ứng dụng cũng sẽ tắt các service dùng chung đối với các ứng dụng khác vẫn đang chạy. Đặt nó thành `start-only` sẽ không dừng các service dùng chung khi ứng dụng dừng, nhưng có một lưu ý là nếu bạn tắt tất cả các ứng dụng, các service vẫn tiếp tục chạy. Bạn có thể dừng các service thủ công bằng cách chạy `docker compose stop` trên dòng lệnh trong thư mục chứa file `compose.yaml`.

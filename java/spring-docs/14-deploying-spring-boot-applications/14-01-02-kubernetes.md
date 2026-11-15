# 14.1.2. Kubernetes

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#deployment.cloud.kubernetes>

Spring Boot tự động phát hiện các môi trường triển khai Kubernetes bằng cách kiểm tra môi trường có các biến `"*_SERVICE_HOST"` và `"*_SERVICE_PORT"` hay không. Bạn có thể ghi đè việc phát hiện này bằng configuration property `spring.main.cloud-platform`.

Spring Boot giúp bạn [quản lý trạng thái của ứng dụng](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.spring-application.application-availability) và xuất nó bằng [HTTP Kubernetes Probe dùng Actuator](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#actuator.endpoints.kubernetes-probes).

## Vòng đời container Kubernetes (Kubernetes Container Lifecycle)

Khi Kubernetes xóa một instance ứng dụng, quá trình tắt liên quan đến nhiều hệ thống con đồng thời: shutdown hook, hủy đăng ký service, loại bỏ instance khỏi load-balancer… Vì quá trình tắt này diễn ra song song (và do bản chất của các hệ thống phân tán), có một khoảng thời gian mà traffic có thể được định tuyến tới một pod cũng đã bắt đầu quá trình tắt.

Bạn có thể cấu hình một lệnh sleep trong preStop handler để tránh các request được định tuyến tới một pod đã bắt đầu tắt. Thời gian sleep này nên đủ dài để các request mới ngừng được định tuyến tới pod, và khoảng thời gian của nó sẽ khác nhau giữa các lần triển khai. preStop handler có thể được cấu hình bằng PodSpec trong file cấu hình của pod như sau:

```yaml
spec:
  containers:
  - name: "example-container"
    image: "example-image"
    lifecycle:
      preStop:
        exec:
          command: ["sh", "-c", "sleep 10"]
```

Sau khi pre-stop hook hoàn tất, SIGTERM sẽ được gửi tới container và [graceful shutdown](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#web.graceful-shutdown) sẽ bắt đầu, cho phép mọi request đang thực hiện dở còn lại hoàn tất.

> **LƯU Ý (NOTE):** Khi Kubernetes gửi tín hiệu SIGTERM tới pod, nó chờ trong một khoảng thời gian xác định gọi là termination grace period (mặc định là 30 giây). Nếu các container vẫn đang chạy sau grace period, chúng sẽ nhận tín hiệu SIGKILL và bị loại bỏ cưỡng chế. Nếu pod mất hơn 30 giây để tắt, có thể do bạn đã tăng `spring.lifecycle.timeout-per-shutdown-phase`, hãy đảm bảo tăng termination grace period bằng cách đặt tùy chọn `terminationGracePeriodSeconds` trong YAML của Pod.

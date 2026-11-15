# 14.1.6. CloudCaptain và Amazon Web Services (CloudCaptain and Amazon Web Services)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#deployment.cloud.boxfuse>

[CloudCaptain](https://cloudcaptain.sh/) hoạt động bằng cách biến jar hoặc war thực thi Spring Boot của bạn thành một image VM tối thiểu có thể được triển khai nguyên trạng trên VirtualBox hoặc trên AWS. CloudCaptain có tích hợp sâu với Spring Boot và dùng thông tin từ file cấu hình Spring Boot để tự động cấu hình các cổng và URL health check. CloudCaptain tận dụng thông tin này cho cả các image mà nó tạo ra lẫn tất cả các tài nguyên mà nó cấp phát (instance, security group, elastic load balancer, v.v.).

Sau khi bạn đã tạo một [tài khoản CloudCaptain](https://console.cloudcaptain.sh), kết nối nó với tài khoản AWS, cài đặt phiên bản mới nhất của CloudCaptain Client, và đảm bảo ứng dụng đã được build bằng Maven hoặc Gradle (ví dụ, bằng `mvn clean package`), bạn có thể triển khai ứng dụng Spring Boot lên AWS bằng một lệnh tương tự như sau:

```shell
$ boxfuse run myapp-1.0.jar -env=prod
```

Xem [tài liệu `boxfuse run`](https://cloudcaptain.sh/docs/commandline/run.html) để biết thêm các tùy chọn. Nếu có file [`boxfuse.conf`](https://cloudcaptain.sh/docs/commandline/#configuration) trong thư mục hiện tại, nó sẽ được xem xét.

> **MẸO (TIP):** Theo mặc định, CloudCaptain kích hoạt một Spring profile tên là `boxfuse` khi khởi động. Nếu jar hoặc war thực thi của bạn chứa file [`application-boxfuse.properties`](https://cloudcaptain.sh/docs/payloads/springboot.html#configuration), CloudCaptain sẽ dựa cấu hình của nó vào các property mà file đó chứa.

Lúc này, CloudCaptain tạo một image cho ứng dụng của bạn, tải nó lên, rồi cấu hình và khởi động các tài nguyên cần thiết trên AWS, tạo ra output tương tự như ví dụ sau:

```
Fusing Image for myapp-1.0.jar ...
Image fused in 00:06.838s (53937 K) -> axelfontaine/myapp:1.0
Creating axelfontaine/myapp ...
Pushing axelfontaine/myapp:1.0 ...
Verifying axelfontaine/myapp:1.0 ...
Creating Elastic IP ...
Mapping myapp-axelfontaine.boxfuse.io to 52.28.233.167 ...
Waiting for AWS to create an AMI for axelfontaine/myapp:1.0 in eu-central-1 (this may take up to 50 seconds) ...
AMI created in 00:23.557s -> ami-d23f38cf
Creating security group boxfuse-sg_axelfontaine/myapp:1.0 ...
Launching t2.micro instance of axelfontaine/myapp:1.0 (ami-d23f38cf) in eu-central-1 ...
Instance launched in 00:30.306s -> i-92ef9f53
Waiting for AWS to boot Instance i-92ef9f53 and Payload to start at https://52.28.235.61/ ...
Payload started in 00:29.266s -> https://52.28.235.61/
Remapping Elastic IP 52.28.233.167 to i-92ef9f53 ...
Waiting 15s for AWS to complete Elastic IP Zero Downtime transition ...
Deployment completed successfully. axelfontaine/myapp:1.0 is up and running at https://myapp-axelfontaine.boxfuse.io/
```

Ứng dụng của bạn giờ sẽ chạy trên AWS.

Xem bài blog về [triển khai ứng dụng Spring Boot trên EC2](https://cloudcaptain.sh/blog/spring-boot-ec2.html) cũng như [tài liệu về tích hợp Spring Boot của CloudCaptain](https://cloudcaptain.sh/docs/payloads/springboot.html) để bắt đầu với một bản build Maven để chạy ứng dụng.

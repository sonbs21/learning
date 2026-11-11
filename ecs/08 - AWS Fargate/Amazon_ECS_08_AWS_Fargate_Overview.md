# Architect for AWS Fargate for Amazon ECS

> Đây là bản overview bám theo đúng mục lục của trang [Architect for AWS Fargate for Amazon ECS](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/AWS_Fargate.html). Mục tiêu là hiểu bức tranh tổng thể trước khi học chi tiết từng nhánh tài liệu.

AWS Fargate là công nghệ compute serverless dùng với Amazon ECS để chạy container mà không cần tự quản lý máy chủ hoặc cụm Amazon EC2. AWS đảm nhiệm việc cấp phát và bảo trì compute; người dùng tập trung vào container image, CPU, memory, networking, IAM và cách ứng dụng được triển khai.

Luồng hoạt động tổng quát:

```text
Container image
      │
      ▼
Task definition: CPU, memory, IAM, network, logging
      │
      ▼
Amazon ECS lập lịch task hoặc service
      │
      ▼
AWS Fargate cấp compute và chạy container
```

Mỗi Fargate task có ranh giới cô lập riêng. Các task không dùng chung kernel, CPU, memory hoặc elastic network interface. Để khai báo một task definition dùng Fargate, trường `requiresCompatibilities` phải chứa giá trị `FARGATE`.

## Walkthroughs

Phần Walkthroughs là điểm bắt đầu thực hành. AWS cung cấp hướng dẫn tạo cả Linux task và Windows task bằng hai cách:

- **AWS Management Console:** phù hợp để làm quen với cluster, task definition, service và cấu hình mạng qua giao diện.
- **AWS CLI:** phù hợp cho automation, script triển khai và quy trình có thể lặp lại.

Các walkthrough thường dẫn qua chuỗi thao tác:

1. Chuẩn bị container image hoặc chọn image mẫu.
2. Tạo task definition tương thích với Fargate.
3. Chọn CPU, memory, operating system và architecture.
4. Cấu hình task execution role, task role và log.
5. Chọn VPC, subnet, security group và public/private networking.
6. Chạy task hoặc tạo ECS service.
7. Kiểm tra trạng thái task, log và kết nối tới ứng dụng.

Linux và Windows cần walkthrough riêng vì platform family, image, tài nguyên và tập tính năng hỗ trợ không hoàn toàn giống nhau.

Tài liệu liên quan:

- [Create an Amazon ECS Linux task for Fargate](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/getting-started-fargate.html)
- [Create an Amazon ECS Windows task for Fargate](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/getting-started-windows.html)

## Capacity providers

Capacity provider xác định loại năng lực compute mà ECS dùng để đặt task. Với Fargate có hai lựa chọn chính:

- **FARGATE:** dùng capacity theo nhu cầu thông thường, phù hợp với workload cần độ ổn định cao.
- **FARGATE_SPOT:** dùng lượng compute dư thừa với mức giá thấp hơn, nhưng task có thể bị gián đoạn khi AWS cần thu hồi capacity. AWS gửi cảnh báo trước hai phút.

Capacity provider strategy cho phép phối hợp hai loại capacity. Hai tham số quan trọng là:

- `base`: số task tối thiểu được đặt trên một capacity provider trước khi áp dụng tỷ lệ.
- `weight`: tỷ lệ phân phối các task còn lại giữa các capacity provider.

Ví dụ về tư duy thiết kế:

```text
FARGATE       → API, workload quan trọng, task không dễ bị gián đoạn
FARGATE_SPOT  → worker, xử lý batch, task có retry hoặc checkpoint
```

Ứng dụng dùng Fargate Spot phải xử lý tín hiệu dừng, shutdown graceful, retry an toàn và không lưu trạng thái quan trọng duy nhất trong task.

Xem thêm: [Amazon ECS clusters for Fargate](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/fargate-capacity-providers.html).

## Task definitions

Task definition là bản thiết kế mô tả ECS phải chạy container như thế nào. Với Fargate, task definition cần xác định tối thiểu:

- `requiresCompatibilities: ["FARGATE"]`;
- CPU và memory ở cấp task theo tổ hợp được AWS hỗ trợ;
- `networkMode: "awsvpc"`;
- container image, port mapping và biến môi trường;
- task execution role và task role nếu workload cần;
- log driver, health check và storage nếu có;
- operating system family và CPU architecture phù hợp.

Fargate không hỗ trợ toàn bộ tham số task definition của ECS on EC2. Những cấu hình phụ thuộc quyền điều khiển host, chẳng hạn `privileged`, `links`, `devices` hoặc một số Linux parameter, có thể không được hỗ trợ hoặc bị giới hạn.

Mỗi task dùng `awsvpc` nên nhận một elastic network interface riêng. Khi chạy task hoặc tạo service, người dùng phải chọn subnet và security group. Khả năng pull image, ghi log hoặc gọi dịch vụ AWS phụ thuộc vào route Internet/NAT hoặc VPC endpoint tương ứng.

Ý chính: task definition không chỉ khai báo container; nó là hợp đồng về tài nguyên, quyền, mạng, storage và runtime của toàn bộ task.

Xem thêm: [Amazon ECS task definition differences for Fargate](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/fargate-tasks-services.html).

## Platform versions

Platform version đại diện cho môi trường runtime của hạ tầng Fargate, bao gồm kernel và container runtime. Người dùng chọn platform version khi chạy task hoặc tạo deployment cho service.

Các nguyên tắc chính:

- Có thể chọn một version cụ thể hoặc `LATEST`.
- Mỗi task giữ nguyên platform revision trong suốt vòng đời.
- Task đang chạy không tự nâng cấp tại chỗ khi AWS phát hành revision mới.
- Task mới được chạy trên revision mới nhất của platform version đã chọn.
- Muốn service hiện tại nhận revision mới, cần tạo deployment mới.
- Linux và Windows có chuỗi platform version riêng.

AWS phát hành revision mới để bổ sung tính năng, sửa lỗi, cập nhật hệ điều hành hoặc vá bảo mật. Khi một revision cần bị loại bỏ, AWS có thể thực hiện task retirement. Vì vậy ứng dụng phải chịu được việc task bị thay thế và không nên phụ thuộc vào trạng thái cục bộ lâu dài.

Platform version ảnh hưởng tới các khả năng như networking, ephemeral storage, EFS, metadata endpoint và một số tính năng quan sát hệ thống. Khi nâng version cần kiểm tra change log, giới hạn hệ điều hành và compatibility của task definition.

Xem thêm: [Fargate platform versions for Amazon ECS](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/platform-fargate.html).

## Service load balancing

ECS service chạy trên Fargate có thể dùng Elastic Load Balancing để phân phối traffic giữa các task. Các loại load balancer được hỗ trợ gồm:

- **Application Load Balancer (ALB):** phù hợp với HTTP/HTTPS và routing Layer 7 như host hoặc path.
- **Network Load Balancer (NLB):** phù hợp với TCP, TLS, UDP và nhu cầu hiệu năng cao ở Layer 4.
- **Gateway Load Balancer (GWLB):** phù hợp khi đưa traffic qua các virtual network appliance.

Fargate task dùng network mode `awsvpc`, vì vậy target group phải dùng target type **`ip`**, không phải `instance`. Load balancer gửi traffic trực tiếp đến private IP của task ENI.

Luồng request tổng quát:

```text
Client
  │
  ▼
Load Balancer
  │
  ▼
Target group với target type = ip
  │
  ├──► Fargate task A
  ├──► Fargate task B
  └──► Fargate task C
```

Health check của target group cần khớp với port và endpoint của ứng dụng. Security group của task chỉ nên cho phép traffic cần thiết từ load balancer. Với Network Load Balancer, khả năng route UDP tới Fargate task yêu cầu platform version phù hợp theo tài liệu AWS.

Xem thêm: [Use load balancing to distribute Amazon ECS service traffic](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/service-load-balancing.html).

## Usage metrics

AWS Fargate usage metrics cung cấp khả năng quan sát mức sử dụng tài nguyên ở cấp tài khoản thông qua Amazon CloudWatch. Các metric này gắn với service quota của Fargate.

Usage metrics giúp trả lời các câu hỏi:

- Tài khoản đang dùng bao nhiêu Fargate On-Demand hoặc Fargate Spot resource?
- Mức sử dụng có đang tiến gần service quota của Region không?
- Khi scale out hoặc deployment đồng thời, quota có nguy cơ làm task không được đặt không?

Quy trình vận hành đề xuất:

1. Hiển thị usage metrics trên CloudWatch dashboard.
2. Xác định quota tương ứng trong Service Quotas.
3. Tạo CloudWatch alarm trước khi usage chạm ngưỡng nguy hiểm.
4. Yêu cầu tăng quota sớm nếu dự báo workload sẽ vượt giới hạn.
5. Theo dõi riêng từng Region vì usage và quota có phạm vi theo Region.

Cần phân biệt hai nhóm metric:

- **Usage metrics:** đo mức sử dụng so với quota của dịch vụ.
- **Application metrics:** CPU, memory, request, latency và lỗi của workload; thường được theo dõi qua Container Insights, CloudWatch Logs hoặc hệ thống observability khác.

Xem thêm: [AWS Fargate usage metrics](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/monitoring-fargate-usage.html).

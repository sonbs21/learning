# Amazon ECS chuyên sâu — AWS SAA-C03

> Tài liệu này giải thích Amazon Elastic Container Service từ nền tảng đến kiến trúc thực tế. Mục tiêu không chỉ là nhớ tên dịch vụ, mà là hiểu một container đi từ image trong ECR thành task đang chạy, nhận traffic qua Load Balancer, dùng IAM role, kết nối VPC, tự phục hồi, tự mở rộng và được giám sát như thế nào.

## 1. ECS là gì?

Amazon Elastic Container Service, viết tắt là ECS, là dịch vụ điều phối container do AWS quản lý.

Một container image chỉ là gói chứa ứng dụng và các dependency. Muốn chạy image ổn định trong production, hệ thống còn phải giải quyết nhiều việc:

- Chạy bao nhiêu bản sao của ứng dụng.
- Chạy trên loại compute nào.
- Đặt container vào subnet và Security Group nào.
- Cấp CPU, memory và storage ra sao.
- Container lấy quyền truy cập S3, DynamoDB hoặc SQS bằng cách nào.
- Đưa traffic từ Load Balancer đến đúng container.
- Khởi động lại container khi bị lỗi.
- Tăng hoặc giảm số lượng container theo tải.
- Triển khai phiên bản mới và rollback khi deployment lỗi.
- Thu thập log, metric và sự kiện vận hành.

ECS là control plane điều phối các công việc đó. ECS không phải là Docker registry, Load Balancer, VPC hay máy chủ vật lý. Nó phối hợp với ECR, Fargate hoặc EC2, IAM, VPC, ELB, CloudWatch và nhiều dịch vụ AWS khác.

~~~text
Developer
   │
   ├── build container image
   ▼
Amazon ECR
   │
   │ image URI
   ▼
ECS Task Definition
   │
   ├── CPU / memory
   ├── ports
   ├── IAM roles
   ├── environment / secrets
   ├── logging
   └── health check
          │
          ▼
      ECS Service
          │
          ├── duy trì desired count
          ├── deployment
          ├── auto scaling
          └── load balancing
          │
          ▼
     ECS Tasks đang chạy
          │
          ├── Fargate
          ├── ECS Managed Instances
          └── EC2 capacity
~~~

Cách ghi nhớ ngắn:

> ECR giữ image; Task Definition mô tả cách chạy; Task là một lần chạy; Service duy trì nhiều task; Cluster là phạm vi logic; Fargate hoặc EC2 cung cấp compute.

---

# Phần I — Kiến thức container cần biết

## 2. Container khác virtual machine như thế nào?

Virtual machine thường bao gồm cả guest operating system. Container đóng gói application, runtime, library và configuration cần thiết, nhưng dùng chung kernel của host.

| Tiêu chí | Container | Virtual machine |
|---|---|---|
| Đơn vị đóng gói | Image | Machine image |
| Hệ điều hành | Dùng chung kernel host | Có guest OS riêng |
| Khởi động | Thường nhanh | Thường chậm hơn |
| Kích thước | Thường nhỏ hơn | Thường lớn hơn |
| Mức cách ly | Process/container boundary | Hypervisor/VM boundary |
| Phù hợp | Microservice, worker, batch | Workload cần OS riêng hoặc kiểm soát sâu |

Container không tự động làm ứng dụng stateless. Nếu ứng dụng ghi dữ liệu quan trọng vào filesystem cục bộ của container, dữ liệu vẫn có thể mất khi task bị thay thế.

## 3. Image, container và registry

- Image là package bất biến dùng để tạo container.
- Container là một instance đang chạy của image.
- Registry là nơi lưu image; trên AWS thường dùng Amazon ECR.
- Repository trong ECR là nhóm các image liên quan.
- Tag như v1.4.2 hoặc latest là nhãn.
- Digest định danh nội dung image chính xác hơn tag.

~~~text
Source code
   ↓ docker build
Image
   ↓ docker push
ECR repository
   ↓ ECS pull
Container trong ECS task
~~~

Production nên dùng version rõ ràng hoặc image digest. Tag latest có thể trỏ sang nội dung khác và làm deployment khó truy vết.

## 4. ECS không phải EKS

ECS và EKS đều điều phối container, nhưng khác control plane và hệ sinh thái.

| Nhu cầu | ECS | EKS |
|---|---|---|
| Tích hợp AWS đơn giản | Rất phù hợp | Có thể làm được nhưng phức tạp hơn |
| Muốn dùng Kubernetes API/ecosystem | Không | Phù hợp |
| Ít vận hành control plane/orchestrator | Phù hợp | Cần hiểu Kubernetes |
| Portability theo chuẩn Kubernetes | Hạn chế hơn | Tốt hơn |
| Mức SAA-C03 | Cần hiểu khá rõ | Chủ yếu nhận biết và chọn đúng use case |

Nếu đề không yêu cầu Kubernetes, ECS thường là lựa chọn đơn giản hơn.

---

# Phần II — Mô hình thành phần cốt lõi

## 5. Cluster là gì?

ECS cluster là nhóm logic chứa service và task, đồng thời liên kết với nguồn compute thông qua capacity provider.

Cluster không nhất thiết là một nhóm EC2 cố định. Với Fargate, bạn vẫn có cluster dù không trực tiếp quản lý server.

Có thể tổ chức cluster theo:

- Môi trường: development, staging, production.
- Business domain.
- Boundary bảo mật.
- Loại workload hoặc ownership của team.

Không nên tạo cluster chỉ vì mỗi container cần một cluster. Một cluster có thể chứa nhiều service độc lập.

## 6. Task Definition là gì?

Task Definition là bản thiết kế có version mô tả cách ECS chạy một hoặc nhiều container có quan hệ chặt chẽ.

Nó có thể chứa:

- Container image.
- CPU và memory.
- Port mapping.
- Environment variables.
- Secrets lấy từ Secrets Manager hoặc Systems Manager Parameter Store.
- Task role.
- Task execution role.
- Network mode.
- Log driver.
- Container health check.
- Volume và mount point.
- Startup dependency giữa các container.
- Linux parameter, user và read-only filesystem.
- Fargate hoặc EC2 compatibility.

Task Definition có family và revision:

~~~text
orders-api:1
orders-api:2
orders-api:3
~~~

Đăng ký thay đổi sẽ tạo revision mới. Task đang chạy bằng revision cũ không tự biến thành revision mới. Muốn cập nhật service, phải tạo deployment dùng revision mới.

### Một task có thể có nhiều container

Ví dụ:

~~~text
Task: orders-api
├── application container
├── telemetry sidecar
└── log router sidecar
~~~

Các container nên nằm chung task khi chúng có cùng lifecycle và cần được schedule cùng nhau. Không nên nhét mọi microservice vào một task lớn chỉ để tiện triển khai; khi đó chúng không thể scale và release độc lập.

## 7. Task là gì?

Task là một lần chạy cụ thể của Task Definition.

Ví dụ Task Definition orders-worker:7 có thể được chạy thành 20 task song song. Mỗi task có lifecycle riêng:

~~~text
PROVISIONING → PENDING → ACTIVATING → RUNNING → DEACTIVATING → STOPPING → STOPPED
~~~

Task có thể dừng vì:

- Main container thoát.
- Không đủ memory và bị kill.
- Health check thất bại.
- Deployment thay thế phiên bản cũ.
- Service scale in.
- Fargate Spot bị thu hồi.
- Người vận hành chủ động stop.
- Hạ tầng gặp sự cố.

Stopped task không tự khởi động lại nếu chỉ chạy dạng standalone task. ECS Service mới là thành phần duy trì desired count.

## 8. Standalone Task và Service

### Standalone Task

Dùng RunTask để chạy một công việc hữu hạn hoặc theo yêu cầu:

- Migration database.
- Batch job.
- Import/export.
- Xử lý file một lần.
- Task được EventBridge Scheduler kích hoạt.

Task chạy xong thì dừng. Nếu cần retry, orchestration hoặc lịch chạy, phải thiết kế bằng EventBridge Scheduler, Step Functions, AWS Batch hoặc cơ chế phù hợp.

### ECS Service

Service duy trì số lượng task mong muốn cho workload chạy lâu dài.

~~~text
desired count = 3

Task A RUNNING
Task B RUNNING
Task C STOPPED
        │
        └── Service scheduler tạo Task D thay thế
~~~

Service phù hợp với:

- Web application.
- REST API.
- Consumer đọc SQS liên tục.
- Background daemon.
- Internal microservice.

Service cung cấp self-healing ở mức task, nhưng không sửa lỗi logic trong ứng dụng. Nếu image luôn crash, service sẽ liên tục tạo task rồi task lại dừng.

## 9. Desired count

Desired count là số task service muốn duy trì.

- Desired count 3 không có nghĩa chắc chắn luôn có đúng 3 task trong mọi mili giây.
- Khi deployment, số task có thể tạm thời cao hơn.
- Khi thiếu capacity hoặc cấu hình sai, số running task có thể thấp hơn desired count.
- Service Auto Scaling có thể thay đổi desired count trong giới hạn min/max.

Production thường dùng ít nhất hai task ở nhiều Availability Zone nếu cần high availability. Một task duy nhất vẫn là single point of failure ở tầng application.

---

# Phần III — Compute capacity

## 10. Fargate

AWS Fargate là serverless compute cho container. Bạn khai báo CPU, memory, network và image; AWS quản lý host bên dưới.

Bạn vẫn phải quản lý:

- Container image và dependency.
- Task Definition.
- IAM.
- Security Group và subnet.
- Scaling.
- Application patch.
- Logging, monitoring và cost.

Bạn không phải quản lý:

- EC2 instance trong cluster.
- ECS agent trên host.
- Host operating system.
- Capacity của Auto Scaling Group.

Fargate phù hợp khi:

- Muốn giảm operational overhead.
- Workload thay đổi hoặc số lượng service chưa lớn.
- Cần isolation theo task.
- Muốn triển khai nhanh.
- Không cần tùy chỉnh host sâu.

Hạn chế cần cân nhắc:

- Chỉ dùng các tổ hợp CPU/memory được hỗ trợ.
- Không kiểm soát host như EC2.
- Workload ổn định quy mô lớn có thể tối ưu chi phí hơn trên EC2 trong một số trường hợp.
- Cần thiết kế network egress, log và image pull; serverless không có nghĩa là không cần VPC.

## 11. ECS trên EC2

Ở mô hình này, ECS đặt task lên các EC2 container instances đã đăng ký vào cluster.

Bạn chịu trách nhiệm nhiều hơn:

- Chọn instance type.
- AMI và ECS agent.
- Patch và bảo mật host.
- Auto Scaling Group.
- Capacity còn trống cho task.
- Draining khi thay instance.
- Bin packing CPU/memory/port.
- Spot interruption handling.

Đổi lại, EC2 phù hợp khi:

- Workload lớn và ổn định, cần tối ưu utilization.
- Cần GPU, instance type đặc biệt hoặc host customization.
- Cần mô hình network/storage không phù hợp Fargate.
- Có đội ngũ vận hành compute tốt.
- Muốn dùng Reserved Instances, Savings Plans hoặc Spot theo chiến lược riêng.

Một EC2 instance có thể chạy nhiều task. Vì vậy phải bảo vệ host và không cấp quyền quá rộng qua EC2 instance profile.

## 12. Amazon ECS Managed Instances

ECS Managed Instances là lựa chọn compute mới hơn, trong đó AWS quản lý vòng đời EC2 bên dưới nhiều hơn so với mô hình ECS on EC2 tự quản. Nó hướng tới sự cân bằng giữa tính linh hoạt của EC2 và giảm công việc vận hành.

Đối với SAA-C03, trọng tâm truyền thống vẫn là phân biệt Fargate và ECS on EC2. Khi gặp kiến trúc hiện đại, cần biết Managed Instances là một capacity option, nhưng không nhầm nó với Fargate: nền compute và mô hình định giá/khả năng vẫn khác nhau.

## 13. Fargate Spot

Fargate Spot dùng spare capacity với giá thấp hơn nhưng task có thể bị gián đoạn. AWS gửi tín hiệu cảnh báo trước khi dừng, nhưng ứng dụng vẫn phải chịu interruption.

Phù hợp:

- Worker đọc SQS.
- Batch job có checkpoint.
- Workload retry được.
- Development/test.
- Stateless task có nhiều replica.

Không phù hợp làm toàn bộ capacity cho service cần luôn sẵn sàng. Có thể kết hợp FARGATE và FARGATE_SPOT bằng capacity provider strategy để có baseline ổn định và phần burst tiết kiệm.

## 14. Capacity Provider

Capacity Provider mô tả nguồn compute và cách ECS phân phối task vào nguồn đó.

Ví dụ:

- FARGATE.
- FARGATE_SPOT.
- Auto Scaling Group capacity provider.
- ECS Managed Instances capacity provider.

Hai tham số quan trọng trong strategy:

- base: số task tối thiểu đặt vào một provider trước.
- weight: tỷ lệ tương đối cho các task còn lại.

Ví dụ tư duy:

~~~text
base FARGATE = 2
weight FARGATE = 1
weight FARGATE_SPOT = 3

Hai task đầu dùng FARGATE.
Task tiếp theo được phân phối theo tỷ lệ tương đối 1:3.
~~~

Weight không phải phần trăm tuyệt đối. Fargate Spot cũng không tự fallback sang Fargate On-Demand khi Spot hết capacity; strategy và mức dự phòng phải được thiết kế rõ.

## 15. Hai lớp scaling khi dùng EC2

Với ECS on EC2 có hai vấn đề riêng:

~~~text
Lớp 1: ECS Service Auto Scaling
        thay đổi số task

Lớp 2: Cluster capacity scaling
        thay đổi số EC2 instance
~~~

Nếu chỉ scale task nhưng cluster không đủ CPU/memory, task sẽ PENDING. Nếu chỉ scale EC2 mà desired count không tăng, thêm instance cũng không tạo thêm task.

Với Fargate, AWS lo lớp host capacity; bạn chủ yếu scale số task.

---

# Phần IV — ECR và vòng đời image

## 16. Amazon ECR

Amazon Elastic Container Registry là registry được quản lý để lưu container image.

Các chức năng quan trọng:

- Private repository.
- IAM-based access.
- Encryption.
- Image vulnerability scanning.
- Lifecycle policy để xóa image cũ.
- Replication khi cần.
- Tag immutability để tránh ghi đè version.
- Tích hợp với ECS.

Luồng deployment phổ biến:

~~~text
Git push
   ↓
CI chạy test
   ↓
Build image
   ↓
Scan image
   ↓
Push ECR với tag commit/version
   ↓
Đăng ký Task Definition revision mới
   ↓
Update ECS Service
   ↓
Theo dõi deployment
~~~

ECR lưu image, không chạy container. ECS orchestration chạy task; Fargate hoặc EC2 cung cấp compute.

## 17. Image pull cần những gì?

Để task pull private image từ ECR, thường cần:

- Task execution role có quyền ECR phù hợp.
- Network path đến ECR API và registry.
- Truy cập S3 vì các image layer phía sau dùng S3.
- DNS hoạt động.
- Đúng image URI, Region, repository và tag/digest.

Task trong private subnet có thể đi qua NAT Gateway hoặc dùng VPC endpoints phù hợp cho ECR, S3 và các dịch vụ liên quan.

---

# Phần V — IAM và secrets

## 18. Bốn loại role dễ nhầm

| Role | Ai sử dụng? | Mục đích |
|---|---|---|
| Task role | Code bên trong container | Gọi S3, DynamoDB, SQS, Secrets Manager... |
| Task execution role | ECS/Fargate agent | Pull ECR image, gửi log, lấy secret được khai báo trong task |
| EC2 instance role | ECS container instance | ECS agent và tác vụ cấp host khi chạy ECS on EC2 |
| Service-linked role | Dịch vụ ECS | Thao tác với ELB, network và tài nguyên AWS thay mặt ECS |

Câu ghi nhớ:

> Application dùng task role; nền tảng khởi động task dùng execution role.

### Ví dụ

Orders API đọc DynamoDB:

~~~text
Application container
   │
   └── Task role
          └── dynamodb:GetItem, PutItem trên bảng Orders
~~~

ECS pull image và gửi log:

~~~text
ECS/Fargate agent
   │
   └── Task execution role
          ├── ECR pull permissions
          └── CloudWatch Logs permissions
~~~

Không đưa quyền DynamoDB của application vào execution role. Code trong container không nên dùng access key tĩnh.

## 19. Secret và configuration

Không lưu password, token hoặc API key trong:

- Dockerfile.
- Image layer.
- Source control.
- Plain environment file trong repository.
- Log.
- Task Definition dưới dạng plain text.

Dùng:

- AWS Secrets Manager cho secret có lifecycle/rotation.
- Systems Manager Parameter Store cho parameter và secure string phù hợp.
- KMS để kiểm soát encryption key khi cần.
- Task role hoặc execution role với least privilege.

Khi secret được inject vào environment variable lúc task khởi động, version mới của secret thường không tự xuất hiện trong process đang chạy. Cần tạo deployment hoặc restart task; hoặc ứng dụng chủ động đọc secret runtime và cache an toàn.

---

# Phần VI — Networking

## 20. awsvpc network mode

Với awsvpc, mỗi task nhận một Elastic Network Interface và private IP trong VPC. Đây là network mode bắt buộc phổ biến với Fargate và cũng là lựa chọn khuyến nghị cho nhiều workload ECS trên EC2.

Hệ quả:

- Task nằm trong subnet cụ thể.
- Security Group có thể gắn vào task.
- Load Balancer có thể route đến private IP của task.
- Task tiêu thụ địa chỉ IP trong subnet.
- Phải tính đủ CIDR và ENI capacity khi scale lớn.

~~~text
VPC
├── Public subnet
│   └── ALB
│
├── Private app subnet AZ-a
│   ├── ECS Task 10.0.11.21
│   └── ECS Task 10.0.11.22
│
└── Private app subnet AZ-b
    ├── ECS Task 10.0.12.31
    └── ECS Task 10.0.12.32
~~~

Security Group của ALB cho phép inbound từ client. Security Group của task chỉ nên cho phép inbound từ Security Group của ALB trên container port.

## 21. Public subnet không đồng nghĩa có Internet

Một resource có đường tới Internet cần đồng thời đúng nhiều điều kiện:

- Route table.
- Internet Gateway hoặc NAT.
- Public/private IP phù hợp.
- Security Group.
- Network ACL.
- DNS.
- Application lắng nghe đúng port.

Fargate task trong public subnet chỉ có outbound Internet trực tiếp khi được gán public IP và route tới Internet Gateway. Production thường đặt task ở private subnet, nhận inbound qua ALB và đi outbound qua NAT hoặc VPC endpoints.

## 22. Private subnet: NAT hay VPC endpoint?

Task private thường cần gọi:

- ECR API.
- ECR registry.
- S3 để lấy image layer.
- CloudWatch Logs.
- Secrets Manager hoặc Parameter Store.
- Các AWS API mà application sử dụng.
- API bên ngoài Internet.

Hai hướng:

### NAT Gateway

Cho phép outbound đến Internet và public AWS endpoints. Đơn giản hơn nhưng có chi phí theo giờ và data processing; nên thiết kế theo AZ để tránh single point of failure và cross-AZ charge.

### VPC endpoints

Giữ traffic tới AWS services trong mạng AWS và có thể giảm phụ thuộc NAT. Cần tạo đúng endpoint, route, DNS và endpoint Security Group/policy.

Không phải mọi external API đều có VPC endpoint. Nếu app cần Internet, vẫn cần NAT hoặc giải pháp egress khác.

## 23. Port mapping

Container port là port ứng dụng lắng nghe bên trong container. Host port liên quan nhiều hơn khi dùng ECS on EC2 và network mode như bridge.

Với Fargate awsvpc:

- Task có IP riêng.
- Target Group thường đăng ký target type ip.
- ALB forward đến private IP và container port.

Lỗi phổ biến là application chỉ bind localhost 127.0.0.1 thay vì 0.0.0.0, khiến Load Balancer không kết nối được dù port mapping đúng.

## 24. Bridge và host mode

Trên ECS on EC2 còn có các network mode khác:

- bridge: dùng Docker bridge, có thể dynamic host port mapping.
- host: container dùng network namespace của host, ít isolation port hơn.
- none: không có external networking.

Ở cấp SAA, awsvpc là mô hình dễ bảo mật và dễ suy luận nhất. Cần nhận biết bridge có thể cho nhiều task dùng cùng container port qua host port khác nhau.

---

# Phần VII — Load balancing và service discovery

## 25. ECS với Application Load Balancer

ALB phù hợp HTTP/HTTPS và hỗ trợ:

- Host-based routing.
- Path-based routing.
- TLS termination.
- Health check.
- Nhiều target group.
- Routing nhiều ECS service sau một ALB.

~~~text
Route 53
   ↓
ALB :443
   ├── /orders/* → orders target group → ECS orders tasks
   └── /users/*  → users target group  → ECS users tasks
~~~

Với Fargate/awsvpc, target type thường là ip, không phải instance.

## 26. Khi nào dùng NLB?

NLB phù hợp khi:

- TCP, UDP hoặc TLS ở Layer 4.
- Cần hiệu năng connection cao.
- Cần static IP hoặc Elastic IP theo thiết kế.
- Không cần content-based routing Layer 7.

ALB hiểu HTTP và route theo host/path; NLB xử lý connection ở Layer 4. Không chọn NLB chỉ vì nghĩ nó luôn nhanh hơn; requirement protocol và routing mới quyết định.

## 27. Health check có nhiều lớp

Có thể tồn tại ba lớp kiểm tra:

1. Container health check trong Task Definition.
2. Target Group health check của Load Balancer.
3. Application-level readiness/dependency check.

Health endpoint nên:

- Trả kết quả nhanh.
- Không tạo side effect.
- Phân biệt process sống và application sẵn sàng nếu cần.
- Không phụ thuộc quá sâu vào mọi downstream service, tránh lỗi dây chuyền.
- Có timeout, threshold và grace period phù hợp thời gian startup.

Nếu ALB health check sai path, sai port, sai success code hoặc Security Group chặn, target sẽ unhealthy và service có thể liên tục thay task.

## 28. Service discovery và Service Connect

Không phải mọi service nội bộ đều cần public hoặc internet-facing ALB.

Các lựa chọn:

- Internal ALB: phù hợp HTTP routing nội bộ, có health check và load balancing.
- AWS Cloud Map service discovery: client phân giải DNS để tìm task endpoint.
- ECS Service Connect: cung cấp service-to-service connectivity, tên ngắn và observability nhất quán hơn trong ECS.

~~~text
frontend service
      │ gọi http://orders
      ▼
Service Connect / discovery namespace
      ▼
orders service tasks
~~~

Service discovery chỉ giúp tìm endpoint; ứng dụng vẫn cần timeout, retry có backoff, circuit breaker và idempotency.

---

# Phần VIII — Storage

## 29. Ephemeral storage

Filesystem writable của task là tạm thời. Khi task bị thay thế, dữ liệu local không nên được xem là durable.

Dùng local ephemeral storage cho:

- Temporary file.
- Cache có thể tái tạo.
- Working directory.
- Intermediate processing.

Không dùng làm nguồn duy nhất cho:

- Upload của khách hàng.
- Database production.
- Dữ liệu cần tồn tại sau deployment.
- Shared state giữa nhiều task.

## 30. Chọn storage phù hợp

| Nhu cầu | Dịch vụ phù hợp |
|---|---|
| Object/file upload | S3 |
| Shared POSIX filesystem | EFS |
| Relational database | RDS/Aurora |
| Key-value/document | DynamoDB |
| Cache | ElastiCache |
| Queue state | SQS |
| Temporary task data | Ephemeral storage |
| Block storage cho trường hợp được hỗ trợ | EBS theo capability và requirement |

EFS có thể được mount vào nhiều task và nhiều AZ, phù hợp shared filesystem. Tuy nhiên EFS không biến ứng dụng thành stateless và không thay database.

---

# Phần IX — Auto Scaling

## 31. ECS Service Auto Scaling

Service Auto Scaling thay đổi desired count thông qua Application Auto Scaling.

Các kiểu phổ biến:

- Target tracking.
- Step scaling.
- Scheduled scaling.
- Predictive scaling khi phù hợp.
- Custom metric scaling.

Metric thường dùng:

- Average CPU utilization.
- Average memory utilization.
- ALB request count per target.
- SQS backlog per task.
- Business metric tùy workload.

### Target tracking

Giống thermostat: đặt target và hệ thống điều chỉnh số task để giữ metric gần target.

Ví dụ CPU target 60%:

~~~text
CPU trung bình tăng cao
   ↓
desired count tăng
   ↓
thêm task
   ↓
load chia đều hơn
~~~

### Queue-based scaling

Với worker đọc SQS, CPU thấp không có nghĩa queue không bị backlog. Metric tốt hơn là:

~~~text
backlog per task = số message đang chờ / số task đang chạy
~~~

Scale theo thời gian xử lý mục tiêu và backlog. Worker phải idempotent vì SQS có thể giao lại message.

## 32. Những điểm dễ sai khi scaling

- Scale task không làm database tự tăng capacity.
- Task startup chậm cần cooldown và health check grace period phù hợp.
- Scale in quá nhanh có thể ngắt request hoặc job đang xử lý.
- Metric trung bình có thể che giấu hotspot.
- Min capacity 1 vẫn có rủi ro mất availability khi task/AZ lỗi.
- Scale to zero tiết kiệm nhưng có cold start và cần metric kích hoạt được.
- ALB request count phù hợp web traffic hơn CPU trong nhiều trường hợp.

Scaling tốt luôn xét bottleneck downstream: connection pool, database, third-party API, NAT, subnet IP và quota.

---

# Phần X — Deployment

## 33. Rolling deployment

Rolling update dần thay task cũ bằng task mới.

Hai tham số tư duy quan trọng:

- minimum healthy percent: mức capacity healthy tối thiểu trong deployment.
- maximum percent: mức tổng task tối đa được phép trong deployment.

Ví dụ desired count 4, cấu hình cho phép chạy thêm task mới trước khi dừng task cũ sẽ giảm downtime nhưng cần đủ subnet IP, quota và compute capacity.

## 34. Deployment Circuit Breaker

Circuit Breaker phát hiện deployment không đạt steady state. Có thể cấu hình rollback về deployment thành công trước đó.

Nó hữu ích khi:

- Image crash khi startup.
- Container health check fail.
- Task không thể start.
- Target không healthy.

Nên gửi ECS deployment state change đến EventBridge/SNS để cảnh báo. Rollback hạ tầng không đồng nghĩa rollback được database migration không tương thích; schema change phải backward-compatible.

## 35. Blue/green, canary và linear

Blue/green duy trì môi trường cũ và mới song song:

~~~text
Blue: version hiện tại nhận production traffic
Green: version mới được kiểm tra
                ↓
      chuyển traffic có kiểm soát
                ↓
      giữ hoặc rollback về Blue
~~~

Ưu điểm:

- Kiểm tra bản mới trước khi chuyển toàn bộ traffic.
- Rollback nhanh hơn.
- Giảm rủi ro downtime.

Đổi lại:

- Tốn capacity tạm thời.
- Cấu hình phức tạp hơn.
- Stateful change và database migration vẫn phải tương thích.
- Cần target group/listener/test traffic đúng.

Canary chuyển một phần nhỏ traffic trước; linear tăng tỷ lệ theo từng bước. Chọn theo khả năng quan sát và mức chấp nhận rủi ro.

## 36. Graceful shutdown

Khi task bị dừng do deployment hoặc scale in:

1. Target nên ngừng nhận request mới.
2. Load Balancer deregister target.
3. Container nhận tín hiệu stop.
4. Application hoàn tất request/job đang xử lý.
5. Process thoát trước khi hết timeout.

Application phải xử lý tín hiệu kết thúc, đóng connection và checkpoint công việc. Nếu worker đang xử lý SQS, visibility timeout và shutdown logic phải tránh mất hoặc xử lý trùng không kiểm soát.

---

# Phần XI — Logging, monitoring và vận hành

## 37. Logging

Cấu hình awslogs log driver thường gửi stdout/stderr đến CloudWatch Logs.

Best practice:

- Log dạng structured JSON.
- Có request ID, trace ID và business correlation ID.
- Không log password, token, session hoặc dữ liệu nhạy cảm.
- Đặt retention; mặc định giữ log quá lâu có thể tăng chi phí.
- Tạo metric filter/alarm cho lỗi quan trọng.
- Không chỉ dựa vào console log thủ công.

FireLens có thể route log qua Fluent Bit đến nhiều destination khi cần pipeline nâng cao.

## 38. Metric cần theo dõi

Ở mức service/task:

- Running task count và desired count.
- Pending task count.
- CPU và memory utilization.
- Deployment failures.
- Task stopped reasons.
- Restart/crash pattern.
- Application latency và error rate.
- ALB healthy host count.
- Target response time và HTTP 5xx.
- SQS backlog đối với worker.

Ở mức EC2 cluster còn cần:

- CPU/memory reservation.
- Disk.
- ECS agent health.
- Container instance count.
- ASG desired/in-service capacity.

Container Insights cung cấp metric và telemetry chi tiết hơn nhưng có chi phí; bật theo nhu cầu quan sát thực tế.

## 39. ECS Exec

ECS Exec cho phép mở session vào container đang chạy thông qua Systems Manager, có kiểm soát IAM và audit phù hợp.

Dùng cho troubleshooting có kiểm soát, không dùng như cách vận hành thường xuyên để sửa file trực tiếp trong container. Thay đổi thủ công sẽ mất khi task bị thay thế và gây configuration drift.

Production cần:

- IAM least privilege.
- Audit/log session nếu yêu cầu.
- KMS và network path phù hợp.
- Quy trình break-glass.
- Không chia sẻ shell credential.

---

# Phần XII — Security

## 40. Security checklist

- Task chạy trong private subnet khi không cần public inbound.
- Chỉ ALB Security Group được phép gọi task port.
- Task role least privilege theo từng service.
- Execution role tách khỏi task role.
- Không dùng access key tĩnh.
- Secret nằm trong Secrets Manager hoặc Parameter Store.
- Image được scan và cập nhật base image.
- ECR bật tag immutability/lifecycle phù hợp.
- Container chạy non-root khi có thể.
- Read-only root filesystem khi workload hỗ trợ.
- Giảm Linux capabilities và không chạy privileged nếu không cần.
- Encrypt log, secret và storage theo requirement.
- Giới hạn egress nếu threat model yêu cầu.
- Tách production account/environment.
- Theo dõi CloudTrail, ECS events và runtime signals.
- Patch host nếu dùng ECS on EC2.
- Không đưa Docker socket của host vào container không tin cậy.

## 41. Shared Responsibility Model

AWS quản lý hạ tầng vật lý và phần dịch vụ managed. Khách hàng vẫn chịu trách nhiệm cho:

- Vulnerability trong application/dependency.
- IAM policy.
- Network exposure.
- Secret.
- Data classification.
- Container configuration.
- Logging và incident response.
- Host OS khi dùng ECS on EC2 tự quản.

Fargate giảm trách nhiệm quản lý host, không loại bỏ trách nhiệm bảo mật application.

---

# Phần XIII — Reliability và kiến trúc mẫu

## 42. Web service HA trên Fargate

~~~text
Users
  │
Route 53
  │
ALB public subnets, Multi-AZ
  │ Security Group reference
  ▼
ECS Service trên Fargate
  ├── Task AZ-a, private subnet
  ├── Task AZ-b, private subnet
  └── Auto Scaling
       │
       ├── RDS/Aurora Multi-AZ
       ├── ElastiCache
       ├── S3
       └── CloudWatch Logs
~~~

Điểm quan trọng:

- ALB ở ít nhất hai public subnet/AZ.
- Task ở private subnet nhiều AZ.
- Desired count ít nhất hai nếu yêu cầu HA.
- Task stateless; session nằm ở external store hoặc token phù hợp.
- Health check đúng.
- Database và downstream cũng phải HA.
- NAT hoặc endpoints được thiết kế không tạo single point of failure.

## 43. SQS worker

~~~text
Producer
   ↓
SQS queue
   ↓
ECS worker service
   ├── Task 1
   ├── Task 2
   └── Task N
        │
        ├── scale theo backlog per task
        ├── idempotent processing
        ├── visibility timeout
        └── DLQ
~~~

Mô hình này decouple producer và consumer. Nếu worker lỗi, message có thể được retry. Cần đặt visibility timeout dài hơn thời gian xử lý hợp lý và gia hạn nếu job lâu.

## 44. Scheduled task

~~~text
EventBridge Scheduler
   ↓ IAM execution role
ECS RunTask
   ↓
Fargate task xử lý job
   ↓
Task kết thúc
~~~

Phù hợp job theo lịch không cần service chạy liên tục. Scheduler trigger không đảm bảo business operation chỉ chạy đúng một lần; task vẫn cần idempotent.

## 45. Internal microservices

~~~text
Internet
   ↓
Public ALB
   ↓
frontend ECS service
   ↓
Service Connect / internal ALB / Cloud Map
   ├── orders service
   ├── payments service
   └── inventory service
~~~

Chỉ frontend cần public entry point. Service nội bộ nằm private, dùng Security Group và service identity phù hợp.

---

# Phần XIV — Chi phí

## 46. Các nguồn chi phí

Không chỉ tính CPU và memory của task. Tổng chi phí có thể gồm:

- Fargate vCPU/memory và ephemeral storage bổ sung.
- EC2/ASG nếu tự quản.
- ALB/NLB.
- NAT Gateway và data processing.
- Cross-AZ data.
- CloudWatch Logs và Container Insights.
- ECR storage và data transfer.
- Public IPv4.
- EFS/EBS.
- Data transfer đến Internet.
- Secrets Manager.

## 47. Tối ưu chi phí

- Right-size CPU và memory dựa trên metric.
- Scale in khi tải giảm nhưng giữ HA tối thiểu.
- Dùng Fargate Spot hoặc EC2 Spot cho workload chịu interruption.
- Dùng Savings Plans cho baseline compute ổn định khi phù hợp.
- Xóa image cũ bằng ECR lifecycle policy.
- Đặt log retention và giảm log vô ích.
- Cân nhắc VPC endpoints so với NAT theo traffic và chi phí thực tế.
- Tránh cross-AZ traffic không cần thiết nhưng không hy sinh HA.
- Cân nhắc Graviton nếu image/dependency hỗ trợ Arm.
- Với quy mô lớn ổn định, benchmark Fargate và EC2 thay vì giả định.

Fargate thường giảm chi phí vận hành con người; EC2 có thể giảm unit cost khi cluster được sử dụng tốt. Cost optimization phải tính cả engineering effort và reliability.

---

# Phần XV — Troubleshooting có hệ thống

## 48. Task bị kẹt PENDING

Kiểm tra:

- Cluster có compute capacity không.
- Với EC2, còn CPU/memory/port/ENI không.
- Capacity provider có scale được không.
- Subnet còn IP không.
- Fargate CPU/memory combination có hợp lệ không.
- Availability Zone có capacity issue không.
- Placement constraint có quá chặt không.
- Service event báo nguyên nhân gì.

## 49. CannotPullContainerError

Nguyên nhân thường gặp:

- Image URI/tag không tồn tại.
- Execution role thiếu ECR permission.
- Task private không có NAT hoặc VPC endpoints cần thiết.
- Security Group/NACL/DNS chặn.
- Cross-account ECR repository policy sai.
- Image architecture không phù hợp runtime.
- Registry rate/network timeout.

Đọc stopped reason và service event trước, không chỉ restart task.

## 50. ResourceInitializationError

Thường liên quan giai đoạn khởi tạo:

- Không lấy được secret.
- Không tạo được log stream.
- Không truy cập ECR.
- Execution role thiếu quyền.
- Endpoint/DNS/network path lỗi.
- KMS key policy không cho decrypt.

Tách câu hỏi thành hai nhóm: quyền có đúng không và network có đi được không.

## 51. Target ALB unhealthy

Kiểm tra theo thứ tự:

1. Application process có đang chạy không.
2. App có bind 0.0.0.0 và đúng port không.
3. Target Group dùng đúng target type ip không.
4. Health check path, port, protocol và success code có đúng không.
5. Task Security Group có cho inbound từ ALB Security Group không.
6. NACL và route có hợp lệ không.
7. Startup có lâu hơn grace period không.
8. Health endpoint có phụ thuộc downstream đang lỗi không.
9. Container/task health check có xung đột không.

## 52. Task chạy rồi liên tục STOPPED

Xem:

- Exit code.
- Stopped reason.
- OutOfMemory.
- Application log.
- Essential container nào dừng.
- Secret/configuration có đúng không.
- Dependency container startup order.
- Health check.
- Architecture CPU của image.
- Permission filesystem.
- SIGTERM handling.

Nếu container exit code 0 vì chạy script ngắn rồi kết thúc, ECS Service vẫn xem task đã dừng và tạo task mới. Workload hữu hạn nên chạy standalone task, không phải long-running service.

## 53. App không gọi được AWS API

Kiểm tra:

- Code dùng AWS SDK credential provider chain chuẩn chưa.
- Task role có gắn vào Task Definition không.
- Policy task role cho đúng action/resource/condition không.
- KMS key policy hoặc resource policy có chặn không.
- VPC có đường tới service endpoint không.
- Có nhầm cấp quyền cho execution role thay vì task role không.
- System clock/DNS/network có vấn đề không.

## 54. Service không đạt steady state

Đọc ECS Service events theo thứ tự thời gian. Các nguyên nhân hay gặp:

- Task không start được.
- Load Balancer health check fail.
- Thiếu capacity.
- Deployment configuration không cho stop/start đủ task.
- Subnet hết IP.
- Image hoặc secret lỗi.
- Application crash.
- Task mới không tương thích schema/database.

Event và stopped reason thường có bằng chứng tốt hơn việc đoán từ CPU.

---

# Phần XVI — So sánh dịch vụ

## 55. ECS/Fargate, Lambda, EC2, EKS và Batch

| Requirement | Lựa chọn thường phù hợp |
|---|---|
| Event ngắn, scale rất nhanh, không quản container host | Lambda |
| Container service dài hạn, muốn giảm quản server | ECS + Fargate |
| Container cần host control hoặc tối ưu cluster sâu | ECS + EC2 |
| Bắt buộc Kubernetes API/ecosystem | EKS |
| Batch queue, dependency và compute environment | AWS Batch |
| Full OS control, app không container hóa | EC2 |

Lambda có execution duration và runtime model khác. ECS phù hợp process chạy lâu, custom runtime, daemon, worker hoặc container đã có sẵn. AWS Batch có thể dùng ECS/Fargate/EC2 bên dưới nhưng thêm scheduler dành cho batch workload.

---

# Phần XVII — Bẫy thi SAA-C03

## 56. Những nhầm lẫn cần tránh

1. ECS không lưu image; ECR mới lưu image.
2. Cluster không đồng nghĩa một EC2 instance.
3. Task Definition không phải task đang chạy.
4. Standalone task dừng không tự được thay thế; Service mới duy trì desired count.
5. Fargate không có nghĩa ứng dụng tự scale; cần Service Auto Scaling.
6. Task role dành cho code; execution role dành cho ECS agent khởi động task.
7. Task trong private subnet cần NAT hoặc endpoints để pull image/gửi log/lấy secret.
8. ALB cho Fargate awsvpc thường dùng target type ip.
9. Security Group của task nên cho phép từ Security Group của ALB, không mở toàn Internet.
10. ECS Service Auto Scaling scale task; ECS on EC2 còn phải scale cluster instances.
11. Fargate Spot có thể bị interrupt và không mặc định fallback sang Fargate.
12. Local container filesystem không phải durable storage.
13. ALB health check pass không đảm bảo toàn bộ business flow đúng.
14. Multi-AZ cần cả nhiều subnet, nhiều task và downstream resilient.
15. Scale out application có thể làm database quá tải.
16. Rolling deployment cần đủ headroom.
17. Dùng latest làm deployment khó truy vết.
18. Secret inject lúc startup không tự refresh trong process.
19. ECS Exec là công cụ troubleshooting, không phải cách chỉnh production lâu dài.
20. Container hóa không tự tạo high availability, security hoặc observability.

## 57. Cách đọc câu hỏi kiến trúc ECS

Khi gặp đề, lần lượt hỏi:

1. Workload là web service, worker, batch hay scheduled job?
2. Chạy lâu hay event ngắn?
3. Có cần kiểm soát host, GPU hoặc custom AMI không?
4. Có chịu interruption không?
5. Inbound protocol là HTTP/HTTPS hay TCP/UDP?
6. Task cần gọi AWS service nào và role nào phải có quyền?
7. Task nằm public hay private subnet?
8. State nằm ở đâu?
9. Scale theo CPU, request hay queue backlog?
10. Deployment và rollback yêu cầu mức nào?
11. Cần Multi-AZ và bao nhiêu replica?
12. Bottleneck downstream là gì?

---

# Phần XVIII — Lab thực hành đề xuất

## 58. Lab 1: Fargate service sau ALB

Mục tiêu:

- Build một web container đơn giản.
- Push image lên ECR.
- Tạo Task Definition với awslogs.
- Tạo execution role và task role tách biệt.
- Tạo ECS Service với hai task trong private subnets.
- Tạo public ALB và target group type ip.
- Chỉ cho task nhận traffic từ ALB Security Group.
- Kiểm tra health, log và rolling deployment.

Tiêu chí hoàn thành:

- Truy cập được qua DNS của ALB.
- Không truy cập trực tiếp task từ Internet.
- Dừng thủ công một task và quan sát service tạo task thay thế.
- Push version mới và quan sát deployment.

## 59. Lab 2: SQS worker auto scaling

- Tạo SQS queue và DLQ.
- Worker dùng task role để ReceiveMessage/DeleteMessage.
- Chạy worker bằng ECS Service.
- Publish nhiều message.
- Tạo custom backlog-per-task metric.
- Scale out rồi scale in.
- Kiểm tra duplicate handling và visibility timeout.

## 60. Lab 3: Secret và private networking

- Task ở private subnet.
- Secret trong Secrets Manager.
- Execution/task role least privilege.
- Dùng NAT hoặc endpoints phù hợp.
- Rotate secret.
- Force new deployment và xác nhận task nhận version mới.
- Cố tình xóa permission để quan sát ResourceInitializationError.

## 61. Lab 4: Deployment failure và rollback

- Deploy image hoạt động.
- Bật deployment circuit breaker với rollback.
- Deploy image có health check fail.
- Quan sát service event và EventBridge event.
- Xác nhận service rollback về revision cũ.

---

# Phần XIX — Câu hỏi tự kiểm tra

## 62. Câu hỏi

### Câu 1

Một API chạy container, không cần quản lý server, cần chạy liên tục và nhận traffic HTTP. Chọn gì?

**Đáp án:** ECS Service trên Fargate sau ALB. Fargate cung cấp compute không cần quản EC2; Service duy trì task; ALB phân phối HTTP traffic.

### Câu 2

Container cần đọc object từ một S3 bucket. Cấp quyền ở đâu?

**Đáp án:** Task role. Không đặt quyền application vào task execution role và không nhúng access key.

### Câu 3

Fargate task private không pull được image ECR. Cần kiểm tra gì?

**Đáp án:** Execution role, image URI, DNS, route và đường network qua NAT hoặc ECR/S3 endpoints phù hợp.

### Câu 4

Service có desired count 10 nhưng chỉ 6 task chạy trên ECS on EC2; bốn task PENDING vì thiếu memory. Chỉ tăng desired count có giải quyết không?

**Đáp án:** Không. Phải thêm cluster capacity hoặc sửa task sizing/placement. Service scaling và EC2 capacity scaling là hai lớp.

### Câu 5

Worker đọc SQS có CPU chỉ 20% nhưng backlog tăng nhanh. Nên scale theo gì?

**Đáp án:** Backlog per task hoặc metric phản ánh tốc độ xử lý, không chỉ CPU.

### Câu 6

Cần tiết kiệm cho worker stateless, retry được nhưng vẫn giữ một baseline ổn định. Chọn gì?

**Đáp án:** Capacity provider strategy kết hợp FARGATE baseline và FARGATE_SPOT cho phần workload chịu interruption.

### Câu 7

Tại sao target ALB phải dùng type ip với Fargate awsvpc?

**Đáp án:** Mỗi task có ENI/private IP riêng; ALB đăng ký IP của task thay vì EC2 instance host.

### Câu 8

Task Definition revision mới đã được đăng ký nhưng service vẫn chạy code cũ. Vì sao?

**Đáp án:** Đăng ký revision không tự update service. Phải update service sang revision mới và tạo deployment.

### Câu 9

Ứng dụng ghi upload vào filesystem container. Sau deployment file mất. Cách sửa?

**Đáp án:** Đưa dữ liệu durable ra S3 hoặc storage phù hợp; không coi filesystem local của task là persistent.

### Câu 10

Một job chạy mỗi đêm trong 20 phút. Có cần ECS Service chạy 24/7 không?

**Đáp án:** Không nhất thiết. EventBridge Scheduler có thể kích hoạt standalone Fargate task.

### Câu 11

Task execution role có quyền DynamoDB nhưng application vẫn AccessDenied. Vì sao?

**Đáp án:** Code dùng credential của task role. Cần cấp DynamoDB permission cho task role, không phải execution role.

### Câu 12

Deployment mới liên tục fail health check. Cơ chế nào tự rollback rolling deployment?

**Đáp án:** ECS deployment circuit breaker với rollback được bật.

---

# Phần XX — Checklist ôn thi

## 63. Checklist

- [ ] Giải thích được ECS, ECR, Task Definition, Task, Service và Cluster.
- [ ] Phân biệt standalone task và long-running service.
- [ ] Phân biệt Fargate, Fargate Spot và ECS on EC2.
- [ ] Hiểu capacity provider, base và weight.
- [ ] Phân biệt task role và task execution role.
- [ ] Hiểu awsvpc, ENI, subnet IP và Security Group.
- [ ] Biết private task pull ECR qua NAT hoặc endpoints.
- [ ] Chọn ALB hay NLB theo protocol/routing.
- [ ] Biết target type ip cho Fargate awsvpc.
- [ ] Hiểu container health check và target group health check.
- [ ] Chọn Service Connect, Cloud Map hoặc internal ALB theo use case.
- [ ] Không lưu durable state trong local task filesystem.
- [ ] Scale service theo CPU, memory, ALB requests hoặc SQS backlog.
- [ ] Nhớ hai lớp scaling khi dùng ECS on EC2.
- [ ] Hiểu rolling, circuit breaker và blue/green.
- [ ] Biết xem service event, stopped reason, exit code và log.
- [ ] Thiết kế Multi-AZ và graceful shutdown.
- [ ] Bảo mật image, secret, IAM, network và runtime.
- [ ] Tính cả NAT, log, ALB và data transfer trong chi phí.

---

# 64. Bản tóm tắt một trang

~~~text
ECR
  giữ container image
     │
     ▼
Task Definition
  mô tả image, CPU, memory, port, role, log, secret, volume
     │
     ▼
Task
  một lần chạy Task Definition
     │
     ▼
ECS Service
  duy trì desired count, health, deployment, auto scaling
     │
     ▼
ECS Cluster
  nhóm logic và kết nối capacity
     │
     ├── Fargate / Fargate Spot
     ├── ECS Managed Instances
     └── EC2 capacity provider

Inbound:
Route 53 → ALB/NLB → Target Group → Task ENI

Outbound:
Task private → NAT hoặc VPC endpoints → ECR/S3/Logs/Secrets/API

IAM:
Application → Task role
ECS agent  → Task execution role

State:
Task local = tạm thời
Durable data = S3/EFS/RDS/DynamoDB/ElastiCache tùy nhu cầu

Scaling:
Service Auto Scaling → số task
EC2 capacity scaling → số instance

Reliability:
Multi-AZ + nhiều task + health check + stateless + resilient downstream

Operations:
CloudWatch Logs/Metrics + Container Insights + ECS events + ECS Exec
~~~

# 65. Tài liệu AWS chính thức

- [Amazon ECS Developer Guide](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/Welcome.html)
- [ECS clusters](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/clusters.html)
- [ECS Task Definitions](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task_definitions.html)
- [Amazon ECS services](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/ecs_services.html)
- [Fargate cho Amazon ECS](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/AWS_Fargate.html)
- [Launch types và capacity providers](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/capacity-launch-type-comparison.html)
- [ECS task networking](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task-networking.html)
- [ECS task IAM role](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task-iam-roles.html)
- [ECS task execution IAM role](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task_execution_IAM_role.html)
- [ECS Service Auto Scaling](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/service-auto-scaling.html)
- [Deployment Circuit Breaker](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/deployment-circuit-breaker.html)
- [ECS blue/green deployment](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/deployment-type-blue-green.html)
- [ECS Service Connect](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/service-connect.html)
- [ECS Exec](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/ecs-exec.html)
- [Amazon ECR User Guide](https://docs.aws.amazon.com/AmazonECR/latest/userguide/what-is-ecr.html)


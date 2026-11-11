# IAM roles for Amazon ECS

> Nguồn: **Amazon ECS Developer Guide — IAM roles for Amazon ECS**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/ecs-iam-role-overview.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: **bản dịch** sang tiếng Việt, đi đúng thứ tự và đúng nội dung của trang gốc, không thêm, không bớt ý.
> - Khối **💡 Giải thích thêm**: chỉ giải thích lại những gì **chính trang này** đã nói, cho dễ hiểu hơn. Không đưa thông tin từ trang khác hay ví dụ tự đặt.
> - Tên role giữ nguyên tiếng Anh.
> - Tài liệu AWS được phát hành theo giấy phép **Creative Commons Attribution-ShareAlike 4.0**; bản dịch này ghi rõ nguồn ở trên.

---

## Mục lục

0. [Phần mở đầu](#0-phần-mở-đầu)
1. [Bảng các IAM role](#1-bảng-các-iam-role)
2. [Giải thích thêm](#2-giải-thích-thêm)

---

## 0. Phần mở đầu

**📘 Theo docs AWS**

IAM role là một danh tính IAM mà bạn có thể tạo trong account, có các quyền cụ thể. Trong Amazon ECS, bạn có thể tạo role để cấp quyền cho tài nguyên Amazon ECS như container hoặc service.

Các role mà Amazon ECS cần phụ thuộc vào **launch type của task definition** và **các tính năng bạn dùng**. Dùng bảng sau để xác định bạn cần những IAM role nào cho Amazon ECS.

---

## 1. Bảng các IAM role

**📘 Theo docs AWS**

| Role | Định nghĩa | Khi nào cần | Thông tin thêm |
|---|---|---|---|
| **Task execution role** | Cho phép Amazon ECS dùng các dịch vụ AWS khác thay cho bạn. | Task chạy trên **AWS Fargate** hoặc **external instance** và: • pull container image từ repository **Amazon ECR private**; • pull container image từ repository Amazon ECR private ở **account khác** với account chạy task; • gửi log container tới CloudWatch Logs bằng log driver **`awslogs`**. Task chạy trên **AWS Fargate hoặc Amazon EC2 instance** và: • dùng **xác thực private registry**; • dùng **Runtime Monitoring**; • task definition tham chiếu **dữ liệu nhạy cảm** bằng secret của Secrets Manager hoặc parameter của AWS Systems Manager Parameter Store. | *Amazon ECS task execution IAM role* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task_execution_IAM_role.html> |
| **Task role** | Cho phép **code ứng dụng** (trong container) dùng các dịch vụ AWS khác. | Ứng dụng truy cập dịch vụ AWS khác, như Amazon S3. | *Amazon ECS task IAM role* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task-iam-roles.html> |
| **Container instance role** | Cho phép EC2 instance hoặc external instance **đăng ký vào cluster**. | Task chạy trên Amazon EC2 instance hoặc external instance. | *Amazon ECS container instance IAM role* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/instance_IAM_role.html> |
| **Amazon ECS Anywhere role** | Cho phép external instance truy cập AWS API. | Task chạy trên external instance. | *Amazon ECS Anywhere IAM role* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/iam-role-ecsanywhere.html> |
| **Amazon ECS infrastructure for load balancers role** | Cho phép Amazon ECS quản lý tài nguyên load balancer trong cluster thay cho bạn, phục vụ **blue/green deployment**. | Bạn muốn dùng blue/green deployment của Amazon ECS. | *Amazon ECS infrastructure IAM role for load balancers* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/AmazonECSInfrastructureRolePolicyForLoadBalancers.html> |
| **Amazon ECS CodeDeploy role** | Cho phép CodeDeploy cập nhật service của bạn. | Bạn dùng kiểu deploy **CodeDeploy blue/green** để deploy service. | *Amazon ECS CodeDeploy IAM Role* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/codedeploy_IAM_role.html> |
| **Amazon ECS EventBridge role** | Cho phép EventBridge cập nhật service của bạn. | Bạn dùng **rule và target của EventBridge** để lên lịch task. | *Amazon ECS EventBridge IAM Role* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/CWE_IAM_role.html> |
| **Amazon ECS infrastructure role** | Cho phép Amazon ECS quản lý tài nguyên hạ tầng trong cluster. | • Bạn muốn dùng **Amazon ECS Managed Instances** làm capacity. • Bạn muốn gắn **Amazon EBS volume** vào task Amazon ECS dùng launch type Fargate hoặc EC2 — infrastructure role cho phép Amazon ECS quản lý EBS volume cho task. • Bạn muốn dùng **TLS** để mã hoá traffic giữa các service **Amazon ECS Service Connect**. • Bạn muốn tạo **target group VPC Lattice**. | *Amazon ECS infrastructure IAM role* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/infrastructure_IAM_role.html> |
| **Instance profile** | Cho phép Amazon ECS Managed Instances **assume infrastructure role** một cách an toàn. | Bạn dùng Amazon ECS Managed Instances trong cluster. | *Amazon ECS Managed Instances instance profile* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/managed-instances-instance-profile.html> |

---

## 2. Giải thích thêm

**💡 Giải thích thêm**

### Ai dùng role nào

Theo cột "Định nghĩa" của bảng:

| Ai dùng quyền | Role |
|---|---|
| **Amazon ECS** (thay cho bạn) | Task execution role; Amazon ECS infrastructure for load balancers role; Amazon ECS infrastructure role |
| **Code ứng dụng trong container** | Task role |
| **EC2 instance / external instance** | Container instance role; Amazon ECS Anywhere role |
| **Amazon ECS Managed Instances** | Instance profile (để assume infrastructure role) |
| **Dịch vụ khác cập nhật service** | Amazon ECS CodeDeploy role (CodeDeploy); Amazon ECS EventBridge role (EventBridge) |

### Phân biệt task execution role và task role

| | Task execution role | Task role |
|---|---|---|
| Ai dùng | Amazon ECS | Code ứng dụng trong container |
| Ví dụ trong bảng | Pull image từ ECR private, gửi log `awslogs`, lấy secret từ Secrets Manager / Parameter Store, xác thực private registry, Runtime Monitoring | Truy cập Amazon S3 |

### Cần role nào theo hạ tầng

Theo cột "Khi nào cần":

| Hạ tầng | Role liên quan trong bảng |
|---|---|
| **Fargate** | Task execution role (khi dùng ECR private, `awslogs`, secret, private registry, Runtime Monitoring); task role (nếu ứng dụng gọi dịch vụ AWS) |
| **EC2 instance** | Container instance role; task execution role (khi dùng private registry, Runtime Monitoring, secret); task role |
| **External instance** (ECS Anywhere) | Container instance role; Amazon ECS Anywhere role; task execution role (ECR private, `awslogs`); task role |
| **Amazon ECS Managed Instances** | Amazon ECS infrastructure role; instance profile; (task role nếu ứng dụng gọi dịch vụ AWS) |

### Cần role nào theo tính năng

| Tính năng | Role |
|---|---|
| Blue/green deployment của Amazon ECS | Amazon ECS infrastructure for load balancers role |
| CodeDeploy blue/green | Amazon ECS CodeDeploy role |
| Lên lịch task bằng EventBridge rule/target | Amazon ECS EventBridge role |
| Gắn EBS volume vào task (Fargate hoặc EC2) | Amazon ECS infrastructure role |
| TLS giữa các service Service Connect | Amazon ECS infrastructure role |
| Target group VPC Lattice | Amazon ECS infrastructure role |

Ghi chú nhỏ: ô định nghĩa của **Instance profile** trên trang gốc bị lặp chữ ("allows allows").

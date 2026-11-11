# Creating an Amazon ECS cluster for Amazon EC2 workloads

> Nguồn: **Amazon ECS Developer Guide — Creating an Amazon ECS cluster for Amazon EC2 workloads**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/create-ec2-cluster-console-v2.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: diễn đạt lại bằng tiếng Việt, bám sát từng bước và đúng thứ tự của trang gốc, không thêm thông tin ngoài trang. Đây không phải bản dịch từng chữ; khi cần câu chữ chính xác, đọc link gốc ở trên.
> - Khối **💡 Giải thích thêm**: phần giải thích để dễ hiểu hơn, chỉ làm rõ những gì docs đã nói.
> - Tên nút trên console giữ tiếng Anh (in đậm).

---

## Mục lục

0. [Tổng quan](#0-tổng-quan)
1. [Tuỳ chọn mặc định có thể đổi](#1-tuỳ-chọn-mặc-định-có-thể-đổi)
2. [Tuỳ chọn Auto Scaling group](#2-tuỳ-chọn-auto-scaling-group)
3. [Tuỳ chọn mạng](#3-tuỳ-chọn-mạng)
4. [Các bước tạo cluster trên console](#4-các-bước-tạo-cluster-trên-console)
5. [Bước tiếp theo](#5-bước-tiếp-theo)
6. [Ghi chú và chỗ không khớp](#6-ghi-chú-và-chỗ-không-khớp)
7. [So sánh tạo cluster ba loại](#7-so-sánh-tạo-cluster-ba-loại)
8. [Tóm tắt](#8-tóm-tắt)

---

## 0. Tổng quan

**📘 Theo docs AWS**

- Tạo cluster để định nghĩa hạ tầng cho task và service.
- Trước khi bắt đầu: làm xong *Set up to use Amazon ECS* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/get-set-up-for-amazon-ecs.html>) và gán quyền IAM phù hợp (*Amazon ECS cluster examples* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/security_iam_id-based-policy-examples.html#IAM_cluster_policies>).
- Console tạo tài nguyên cần cho cluster bằng một **CloudFormation stack**.
- Console có sẵn nhiều **lựa chọn mặc định**, và có **help panel** ở hầu hết các mục.
- Có thể **đăng ký EC2 instance** ngay khi tạo cluster, hoặc thêm instance **sau** khi đã tạo.

---

## 1. Tuỳ chọn mặc định có thể đổi

**📘 Theo docs AWS**

| Tuỳ chọn | Nội dung |
|---|---|
| **Subnet** | Đổi subnet nơi instance được launch |
| **Security group** | Đổi security group kiểm soát traffic vào container instance |
| **Namespace** | Thêm namespace — service trong cluster kết nối được với service cùng namespace không cần cấu hình thêm. (Xem *Interconnect Amazon ECS services* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/interconnecting-services.html>.) |
| **Task events** | Nhận thông báo EventBridge khi task đổi trạng thái |
| **KMS cho managed storage** | (Xem *Create a KMS key* — <https://docs.aws.amazon.com/kms/latest/developerguide/create-keys.html>.) |
| **KMS cho Fargate ephemeral storage** | (Như trên) |
| **ECS Exec** | KMS key và logging |
| **Tag** | Nhận diện cluster |

**💡 Giải thích thêm**

Cluster EC2 tạo từ console vẫn có tuỳ chọn KMS cho **Fargate ephemeral storage** — vì bước 6 chọn **Fargate and Self-managed instances**, cluster dùng được cả Fargate (file *Amazon ECS clusters*: cluster chứa lẫn được các loại capacity provider).

---

## 2. Tuỳ chọn Auto Scaling group

**📘 Theo docs AWS**

- Dùng EC2 instance thì **bắt buộc** chỉ định một **Auto Scaling group** để quản lý hạ tầng.
- Tạo **ASG mới** thì ASG tự được cấu hình:
  - **ECS quản lý** scale-in và scale-out của ASG;
  - ECS **không ngăn** EC2 instance **đang chứa task** trong ASG bị **terminate** khi scale-in. (Xem *Instance Protection* — <https://docs.aws.amazon.com/autoscaling/ec2/userguide/as-instance-termination.html#instance-protection>.)
- Thuộc tính ASG bạn cấu hình (quyết định loại và số instance):
  - **AMI** ECS-optimized;
  - **instance type**;
  - **SSH key pair** (để chứng minh danh tính khi kết nối instance — xem *Amazon EC2 key pairs and Linux instances* — <https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/ec2-key-pairs.html>);
  - **số instance tối thiểu**;
  - **số instance tối đa** — phải **> 0** thì ASG mới scale out được.
- ECS tạo **launch template** và **ASG** thay bạn trong CloudFormation stack. AMI, instance type, SSH key pair nằm trong **launch template**.
  - Launch template có tiền tố **`EC2ContainerService-<ClusterName>`**.
  - ASG có tiền tố **`<ClusterName>-ECS-Infra-ECSAutoScalingGroup`**.
- Instance launch cho ASG dùng launch template đó.

**💡 Giải thích thêm**

Điểm cần chú ý: ASG tạo từ console **không bật** instance scale-in protection → khi ECS scale-in, instance **đang có task** vẫn có thể bị terminate. Kết hợp với **managed instance draining** (bật mặc định — file *Amazon ECS capacity providers for EC2 workloads*) thì task được drain trước khi instance bị terminate. Nếu tự tạo bằng Terraform, có thể chọn bật `protect_from_scale_in` + `managed_termination_protection` (ví dụ ở file *capacity providers for EC2*) — so sánh của mình.

---

## 3. Tuỳ chọn mạng

**📘 Theo docs AWS**

- Mặc định instance launch vào **subnet mặc định** của Region, dùng **security group** đang gắn với các subnet đó. Có thể đổi subnet và security group.
- Chọn subnet có sẵn; dùng security group có sẵn hoặc tạo mới.
- Chạy task **IPv6-only**: dùng subnet **chỉ có IPv6 CIDR block**.
- Tạo security group mới thì phải có **ít nhất một inbound rule**, gồm: **giao thức**, **dải port**, **nguồn**.

| Nguồn (**Source**) | Ý nghĩa |
|---|---|
| **Custom** | Cho phép từ địa chỉ / CIDR cụ thể |
| **Anywhere** | Mở cho mọi nơi — tự thêm `0.0.0.0/0` (IPv4) và `::/0` (IPv6) |
| **Source group** | Tự thêm **IP hiện tại của máy bạn** làm nguồn |

**💡 Giải thích thêm**

- Tránh **Anywhere** cho port quản trị (vd SSH 22) — gợi ý bảo mật của mình. Với task `awsvpc`, security group của **task** mới là thứ quan trọng cho traffic ứng dụng; security group của **instance** chủ yếu cho traffic tới chính instance (và tới task `bridge`/`host`).
- Mô tả **Source group** trên trang ("thêm IP máy của bạn") nghe giống tuỳ chọn **My IP** trên console EC2; trang có thể đặt nhầm tên — kiểm tra trên console.
- IPv6-only: xem thêm các yêu cầu ở file *task networking options for EC2* (`--enable-primary-ipv6`, agent ≥ 1.99.1, ECS-optimized Linux AMI).

---

## 4. Các bước tạo cluster trên console

**📘 Theo docs AWS**

1. Mở console tại <https://console.aws.amazon.com/ecs/v2>.
2. Chọn **Region**.
3. Thanh bên → **Clusters**.
4. **Create cluster**.
5. **Cluster configuration**:
   - **Cluster name**: tên duy nhất, tối đa **255** ký tự (chữ, số, gạch nối).
   - (Tuỳ chọn) **Service Connect defaults** → **Default namespace**: tên khác tên cluster, hoặc **ARN** của shared namespace. (Xem <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/service-connect-shared-namespaces.html>.)
6. **Infrastructure** → chọn **Fargate and Self-managed instances**, rồi cấu hình ASG làm capacity provider:
   - **ASG có sẵn**: chọn trong **Auto Scaling group (ASG)**.
   - **Tạo ASG mới** (**Create new group**):

| Trường | Nội dung |
|---|---|
| **Provisioning model** | **On-demand** hoặc **Spot** |
| **Allocation Strategy** (khi chọn Spot) | Chọn Spot capacity pool (instance type + AZ). Đa số workload: **Price capacity optimized**. (Xem *Allocation strategies for Spot Instances* — <https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/ec2-fleet-allocation-strategy.html>.) |
| **Container instance AMI** | AMI ECS-optimized |
| **EC2 instance type** | Instance type cho workload. **Managed scaling hoạt động tốt nhất** khi ASG dùng instance type **giống hoặc tương tự nhau** |
| **EC2 instance role** | Container instance role có sẵn hoặc tạo mới. (Xem *Amazon ECS container instance IAM role* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/instance_IAM_role.html>.) |
| **Capacity** | Số instance **tối thiểu** và **tối đa** |
| **SSH key pair** | Key pair để kết nối instance |
| **Root EBS volume size** | GiB — tăng để chứa image và dữ liệu lớn hơn |

7. (Tuỳ chọn) **Networking for Amazon EC2 instances**:
   - Gỡ subnet: **X** ở subnet cần gỡ.
   - Đổi VPC: chọn **VPC** khác **default**, rồi chọn **Subnets**. IPv6-only: VPC có IPv6 CIDR, subnet chỉ có IPv6 CIDR.
   - **Security group**: **Use an existing security group**, hoặc **Create a new security group** → **Add rule** cho từng inbound rule.
   - **Auto-assign public IP**: **Use subnet setting** (gán IP public khi subnet là public) hoặc **Turn on** (luôn gán).
8. (Tuỳ chọn) **Monitoring**: **Container Insights with enhanced observability** (khuyến nghị) hoặc **Container Insights**.
9. (Tuỳ chọn) **Task events** → **Enable task events** → ECS gửi task state change event tới EventBridge.
10. (Tuỳ chọn) **Troubleshooting configuration** — ECS Exec: **AWS KMS key for ECS Exec**; **ECS Exec logging**: **Amazon CloudWatch** / **Amazon S3** / **None**.
11. (Tuỳ chọn) Dùng **GuardDuty Runtime Monitoring** chế độ **thủ công** và muốn GuardDuty giám sát cluster này: **Add tag** với Key **`guardDutyRuntimeMonitoringManaged`**, Value **`true`**.
12. (Tuỳ chọn) **Encryption** → **Managed storage**: ARN KMS key.
13. (Tuỳ chọn) **Tags**: thêm / xoá tag.
14. **Create**.

**💡 Giải thích thêm**

- Bước 11 nối với khuyến nghị **GuardDuty Runtime Monitoring** ở file *EC2 container instance security considerations*.
- Bước 6 — **SSH key pair**: nếu không thật sự cần SSH, có thể cân nhắc dùng **Session Manager** / **ECS Exec** thay vì mở SSH (gợi ý bảo mật của mình; trang không nói).
- **Root EBS volume size**: image container được lưu trên đĩa instance; image lớn hoặc dùng `ECS_IMAGE_PULL_BEHAVIOR=prefer-cached` (tắt dọn image) cần đĩa lớn hơn — liên hệ file *Container image pull behavior*.
- **Spot** + **Price capacity optimized** là cách giảm chi phí cho workload chịu gián đoạn; nên bật managed instance draining để task được dời trước khi Spot instance bị thu hồi (liên hệ file *capacity providers for EC2*).

---

## 5. Bước tiếp theo

**📘 Theo docs AWS**

Tạo task definition rồi chạy dưới dạng standalone task hoặc service:
- *Amazon ECS task definitions* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task_definitions.html>
- *Running an application as an Amazon ECS task* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/standalone-task-create.html>
- *Creating an Amazon ECS rolling update deployment* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/create-service-console-v2.html>

---

## 6. Ghi chú và chỗ không khớp

**💡 Giải thích thêm**

| Chủ đề | Trang này | Trang khác |
|---|---|---|
| Tiền tố tên ASG | `<ClusterName>-ECS-Infra-ECSAutoScalingGroup` | *capacity providers for EC2*: launch template **và** ASG đều có tiền tố `EC2ContainerService-<ClusterName>` |
| Tiền tố launch template | `EC2ContainerService-<ClusterName>` | khớp |
| Khuyến nghị ASG | Có thể chọn ASG **có sẵn** | *capacity providers for EC2*: khuyến nghị dùng ASG **mới, rỗng** |

Khi tìm tài nguyên do console tạo, tìm theo **cả hai** tiền tố, hoặc xem trong **CloudFormation stack** của cluster.

---

## 7. So sánh tạo cluster ba loại

**💡 Giải thích thêm**

Ghép ba file "Creating a cluster":

| | EC2 (trang này) | Fargate | Managed Instances |
|---|---|---|---|
| Infrastructure trên console | **Fargate and Self-managed instances** | Fargate (tự gắn FARGATE + FARGATE_SPOT) | Amazon ECS Managed Instances |
| Bạn cấu hình | ASG: AMI, instance type, instance role, min/max, SSH key, root EBS, On-demand/Spot | Không gì về hạ tầng | Instance profile, infrastructure role, thuộc tính instance |
| Mạng | Subnet, SG, public IP cho **instance** | (cấu hình khi chạy task/service) | Subnet, SG trong capacity provider |
| GuardDuty tag | Có (`guardDutyRuntimeMonitoringManaged`) | — | — |
| Task events, ECS Exec | Có | Có | (trang không nhắc) |
| CloudFormation phía sau | Có | Có | (trang không nhắc) |

---

## 8. Tóm tắt

- Console tạo cluster EC2 qua **CloudFormation stack**; đăng ký instance ngay hoặc sau.
- **Đổi được**: subnet, security group, namespace, task events, KMS (managed storage, Fargate ephemeral storage), ECS Exec, tag.
- **ASG** bắt buộc với EC2. ASG mới: ECS quản lý scale; **không** ngăn instance có task bị terminate khi scale-in. Cấu hình AMI ECS-optimized, instance type, SSH key, min/max (max > 0). Launch template tiền tố `EC2ContainerService-<ClusterName>`, ASG tiền tố `<ClusterName>-ECS-Infra-ECSAutoScalingGroup`.
- **Mạng**: mặc định subnet mặc định của Region; SG mới cần ít nhất một inbound rule (Custom / Anywhere / Source group); IPv6-only dùng subnet chỉ IPv6.
- **Console (14 bước)**: tên + namespace → **Fargate and Self-managed instances** + ASG (có sẵn hoặc mới: On-demand/Spot, allocation strategy, AMI, instance type, instance role, capacity, SSH key, root EBS) → mạng → Container Insights → task events → ECS Exec → tag GuardDuty (tuỳ chọn) → KMS → tag → **Create**.
- **Tiếp theo**: tạo task definition, chạy standalone task hoặc service.

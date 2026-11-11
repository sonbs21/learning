# Migrate from AWS Fargate to Amazon ECS Managed Instances

> Nguồn: **Amazon ECS Developer Guide — Migrate from AWS Fargate to Amazon ECS Managed Instances**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/migrate-fargate-to-managed-instances.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: diễn đạt lại bằng tiếng Việt, bám sát từng ý và đúng thứ tự của trang gốc, không thêm thông tin ngoài trang. Đây không phải bản dịch từng chữ; khi cần câu chữ chính xác, đọc link gốc ở trên.
> - Khối **💡 Giải thích thêm**: phần giải thích để dễ hiểu hơn, chỉ làm rõ những gì docs đã nói.

---

## Mục lục

0. [Tổng quan](#0-tổng-quan)
1. [Những điều cần cân nhắc khi migrate](#1-những-điều-cần-cân-nhắc-khi-migrate)
2. [Điều kiện tiên quyết](#2-điều-kiện-tiên-quyết)
3. [Bước 1 — Cập nhật cluster dùng Managed Instances](#3-bước-1--cập-nhật-cluster-dùng-managed-instances)
4. [Bước 2 — Cập nhật task definition](#4-bước-2--cập-nhật-task-definition)
5. [Bước 3 — Cập nhật service](#5-bước-3--cập-nhật-service)
6. [Bước 4 — Migrate standalone task](#6-bước-4--migrate-standalone-task)
7. [Checklist migrate](#7-checklist-migrate)
8. [Tóm tắt](#8-tóm-tắt)

---

## 0. Tổng quan

**📘 Theo docs AWS**

Có thể migrate workload đang chạy trên **Fargate** sang **Managed Instances**. Việc này cho phép dùng **toàn bộ dải EC2 instance type**, **capacity reservation** và các **năng lực nâng cao**, trong khi hạ tầng vẫn do **AWS quản lý**.

---

## 1. Những điều cần cân nhắc khi migrate

**📘 Theo docs AWS**

| Chủ đề | Nội dung |
|---|---|
| **Tương thích task** | Task definition cấu hình cho Fargate **phần lớn tương thích** với Managed Instances. (Xem *Amazon ECS task definition differences for Amazon ECS Managed Instances* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/managed-instances-tasks-services.html>.) |
| **Thay đổi mô hình bảo mật** | Managed Instances mặc định cho **nhiều task trên một instance**. Cân nhắc bật **single-task mode** nếu cần cô lập mạnh hơn. |
| **Vòng đời instance** | Instance sống tối đa **14 ngày**. Lên kế hoạch thay task và dùng **ECS service** để tự quản lý task. |
| **Thay đổi giá** | Trả tiền cho **toàn bộ instance** cộng **phí quản lý (management fee)**, thay vì trả theo tài nguyên **từng task** như Fargate. |
| **Cửa sổ bảo trì** | Cấu hình bằng **EC2 event windows** để kiểm soát lúc instance được thay để vá. |

**💡 Giải thích thêm**

- Trang này là trang **đầu tiên** trong loạt Managed Instances nhắc tới **phí quản lý** cộng thêm ngoài tiền instance (file *Architect for Amazon ECS Managed Instances* chỉ nói "tính tiền cả instance").
- "Tối đa 14 ngày" là cách nói gọn; file *Patching* chi tiết hơn: drain từ ngày 14, terminate chậm nhất ngày 21.

So sánh nhanh những gì thay đổi (ghép từ các file trước):

| | Fargate | Managed Instances |
|---|---|---|
| Cô lập | Mỗi task môi trường riêng | Nhiều task/instance (có single-task mode) |
| Tính tiền | Theo vCPU/memory của task | Theo instance + phí quản lý |
| Vòng đời | Task chạy tới khi dừng | Instance thay sau 14–21 ngày |
| Network mode | `awsvpc` | `awsvpc` (mặc định) hoặc `host` |
| GPU, capability đặc quyền | Không | Có |

---

## 2. Điều kiện tiên quyết

**📘 Theo docs AWS**

- Task Fargate hiện tại chạy trên **platform version 1.4.0 trở lên**.
- Có các **IAM role** cần cho Managed Instances:

| Role | Vai trò | Tài liệu |
|---|---|---|
| **Infrastructure role** | Cho ECS gọi dịch vụ AWS thay bạn để quản lý hạ tầng Managed Instances | *Amazon ECS infrastructure IAM role* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/infrastructure_IAM_role.html> |
| **Instance profile** | Cấp quyền cho **ECS container agent** và **Docker daemon** chạy trên managed instance | *Amazon ECS Managed Instances instance profile* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/managed-instances-instance-profile.html> |

- Hiểu **khác biệt mô hình bảo mật** giữa Fargate và Managed Instances.

> **Important (docs):** Managed Instances dùng mô hình bảo mật **khác** Fargate. Mặc định nhiều task chạy chung instance, có thể khiến task **lộ trước lỗ hổng** từ task khác. Xem lại yêu cầu bảo mật **trước khi** migrate.

**💡 Giải thích thêm**

Task role và task execution role của Fargate **giữ nguyên** — hai role trên là **thêm** cho phía hạ tầng (file *Architect for Amazon ECS Managed Instances*, bảng 4 role).

---

## 3. Bước 1 — Cập nhật cluster dùng Managed Instances

**📘 Theo docs AWS**

- Tạo **capacity provider** Managed Instances. Capacity provider này **chỉ dùng được trong cluster** đã chỉ định khi tạo.
- (Xem *Creating a capacity provider for Amazon ECS Managed Instances* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/create-capacity-provider-managed-instances.html>.)

**💡 Giải thích thêm**

Ví dụ CLI (cấu trúc theo các ví dụ ở file *Security considerations* và *GPU auto repair*; giá trị là placeholder):

```bash
aws ecs create-capacity-provider \
  --cluster <CLUSTER> \
  --name <MI_CAPACITY_PROVIDER> \
  --managed-instances-provider '{
    "infrastructureRoleArn": "<INFRA_ROLE_ARN>",
    "instanceLaunchTemplate": {
      "ec2InstanceProfileArn": "<INSTANCE_PROFILE_ARN>",
      "networkConfiguration": { "subnets": ["<SUBNET_ID>"], "securityGroups": ["<SG_ID>"] }
    }
  }'
```

Có thể dùng lại **subnet và security group** mà service Fargate đang dùng. Lưu ý cách chọn subnet ảnh hưởng tới bin packing và trải AZ (file *Managed Instances auto scaling and task placement*).

---

## 4. Bước 2 — Cập nhật task definition

**📘 Theo docs AWS**

- Cập nhật task definition để có khả năng tương thích (compatibility) với Managed Instances.
- (Xem *Updating an Amazon ECS task definition using the console* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/update-task-definition-console-v2.html>.)

**💡 Giải thích thêm**

Trang gốc viết tên trường là "requiresCapabilities", nhưng tên trường đúng trong task definition là **`requiresCompatibilities`** (theo mọi trang khác trong loạt này). Thêm `MANAGED_INSTANCES` và có thể giữ `FARGATE` để chạy được cả hai trong giai đoạn chuyển tiếp (file *Architect for Amazon ECS Managed Instances*):

```json
"requiresCompatibilities": ["FARGATE", "MANAGED_INSTANCES"]
```

Những thứ cần soát khi register revision mới (file *Managed Instances task definition differences*):
- Không dùng tham số không hợp lệ trên Managed Instances (vd `ephemeralStorage`, `proxyConfiguration`, `inferenceAccelerator`…).
- Bind mount `sourcePath` (nếu có) phải nằm trong `/var` hoặc `/tmp`.
- Khai báo `cpuArchitecture` nếu image chỉ có một kiến trúc.

---

## 5. Bước 3 — Cập nhật service

**📘 Theo docs AWS**

- Cập nhật service ECS hiện có để dùng **capacity provider Managed Instances**.
- (Xem *Updating an Amazon ECS service to use a capacity provider* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/update-service-managed-instances.html>.)

**💡 Giải thích thêm**

Ví dụ CLI (bổ sung của mình):

```bash
aws ecs update-service \
  --cluster <CLUSTER> \
  --service <SERVICE> \
  --task-definition <FAMILY>:<REVISION_MỚI> \
  --capacity-provider-strategy capacityProvider=<MI_CAPACITY_PROVIDER>,weight=1 \
  --force-new-deployment
```

Lưu ý:
- Một capacity provider strategy chỉ chứa **một loại** provider (file *Architect for Amazon ECS Managed Instances*) → **không** trộn được `FARGATE` với Managed Instances trong cùng strategy để chuyển dần theo weight. Muốn chuyển dần, có thể chạy **service song song** rồi chuyển traffic (vd blue/green ở file *task networking*) — gợi ý của mình.
- Deploy chuyển từ Fargate sang Managed Instances sẽ **thay toàn bộ task**; deployment configuration (`minimumHealthyPercent`, `maximumPercent`) quyết định có gián đoạn hay không.
- Target group của load balancer vẫn dùng target type **`ip`** (`awsvpc`) — không cần đổi.

---

## 6. Bước 4 — Migrate standalone task

**📘 Theo docs AWS**

- Với **standalone task**, chỉ định **capacity provider Managed Instances** khi chạy task.
- (Xem *Running an application as an Amazon ECS task* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/standalone-task-create.html>.)

**💡 Giải thích thêm**

```bash
aws ecs run-task \
  --cluster <CLUSTER> \
  --task-definition <FAMILY>:<REVISION> \
  --capacity-provider-strategy capacityProvider=<MI_CAPACITY_PROVIDER>,weight=1 \
  --network-configuration 'awsvpcConfiguration={subnets=[<SUBNET_ID>],securityGroups=[<SG_ID>]}'
```

Nếu standalone task được chạy qua **EventBridge Scheduler**, cập nhật **Compute options** của schedule sang capacity provider Managed Instances (file *Using Amazon EventBridge Scheduler*).

Nhắc lại: standalone task **không** được tự thay khi instance hết vòng đời hoặc bị gom (files *Security considerations*, *Infrastructure optimization*) → nên ≤ 14 ngày và chạy lại được.

---

## 7. Checklist migrate

**💡 Giải thích thêm**

Ghép từ trang này và các file trước:

```
Trước khi migrate
[ ] Task Fargate đang ở platform 1.4.0+
[ ] Đánh giá mô hình bảo mật: multi-task hay cần single-task mode
[ ] Ứng dụng chịu được việc instance bị thay sau 14–21 ngày (tắt êm, SIGTERM, stopTimeout)
[ ] Không có task chạy liên tục > 14 ngày
[ ] Ước tính chi phí: tiền instance + phí quản lý so với Fargate

Chuẩn bị
[ ] Infrastructure role + instance profile
[ ] (Nếu dùng SCP giới hạn AMI) cho phép AMI account của Managed Instances
[ ] EC2 event window vào giờ thấp điểm

Thực hiện
[ ] Bước 1: tạo capacity provider Managed Instances trong cluster
[ ] Bước 2: task definition revision mới có MANAGED_INSTANCES; soát tham số không hợp lệ
[ ] Bước 3: update service sang capacity provider mới
[ ] Bước 4: standalone task / EventBridge Scheduler dùng capacity provider mới

Sau migrate
[ ] Kiểm tra task RUNNING, target health, metric CPU/memory
[ ] Theo dõi mức lấp đầy instance để tối ưu chi phí
```

---

## 8. Tóm tắt

- Migrate Fargate → Managed Instances để dùng mọi EC2 instance type, capacity reservation, năng lực nâng cao, vẫn do AWS quản lý hạ tầng.
- **Cân nhắc**: task definition phần lớn tương thích; mặc định **nhiều task/instance** (có single-task mode); instance sống tối đa **14 ngày** → dùng service; trả tiền **instance + phí quản lý**; cấu hình **EC2 event windows**.
- **Tiên quyết**: Fargate platform **1.4.0+**; **infrastructure role** và **instance profile**; hiểu khác biệt bảo mật.
- **Bước 1**: tạo capacity provider Managed Instances (chỉ dùng trong cluster đó).
- **Bước 2**: cập nhật task definition có `MANAGED_INSTANCES` trong `requiresCompatibilities`.
- **Bước 3**: cập nhật service dùng capacity provider Managed Instances.
- **Bước 4**: standalone task chỉ định capacity provider Managed Instances khi chạy.

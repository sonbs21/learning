# Migrate from Amazon EC2 to Amazon ECS Managed Instances

> Nguồn: **Amazon ECS Developer Guide — Migrate from Amazon EC2 to Amazon ECS Managed Instances**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/migrate-ec2-to-managed-instances.html>
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
7. [Những thứ trên EC2 cần thay thế](#7-những-thứ-trên-ec2-cần-thay-thế)
8. [Checklist migrate](#8-checklist-migrate)
9. [Tóm tắt](#9-tóm-tắt)

---

## 0. Tổng quan

**📘 Theo docs AWS**

Migrate workload đang chạy trên **Amazon EC2** sang **Managed Instances** để dùng **toàn bộ dải EC2 instance type**, **capacity reservation** và **năng lực nâng cao**, trong khi hạ tầng do **AWS quản lý**.

**💡 Giải thích thêm**

Trang này gần như **giống hệt** trang *Migrate from AWS Fargate to Amazon ECS Managed Instances* (cùng 5 điều cân nhắc, cùng 4 bước). Khác biệt chính:
- Không yêu cầu "platform version 1.4.0" (đó là khái niệm của Fargate).
- Không có khung Important riêng về bảo mật (nhưng vẫn yêu cầu hiểu khác biệt mô hình bảo mật).

Vì vậy phần giải thích của file này tập trung vào **những gì đặc thù khi rời EC2 tự quản lý** (mục 7).

---

## 1. Những điều cần cân nhắc khi migrate

**📘 Theo docs AWS**

| Chủ đề | Nội dung |
|---|---|
| **Tương thích task** | Task definition cấu hình cho EC2 **phần lớn tương thích** với Managed Instances. Danh sách khác biệt: *Amazon ECS task definition differences for Amazon ECS Managed Instances* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/managed-instances-tasks-services.html>. |
| **Thay đổi mô hình bảo mật** | Mặc định **nhiều task trên một instance**. Cân nhắc bật **single-task mode** nếu cần cô lập mạnh hơn. |
| **Vòng đời instance** | Instance sống tối đa **14 ngày**. Lên kế hoạch thay task, dùng **ECS service** để tự quản lý task. |
| **Thay đổi giá** | Trả tiền **toàn bộ instance** cộng **phí quản lý**, đổi lại AWS lo **chi phí vận hành hạ tầng**. |
| **Cửa sổ bảo trì** | Cấu hình bằng **EC2 event windows** để kiểm soát lúc instance được thay để vá. |

**💡 Giải thích thêm**

So với EC2 tự quản lý, hai thay đổi lớn nhất về vận hành:
- **Không còn instance chạy lâu dài**: instance bị thay sau 14–21 ngày (file *Patching*). Trên EC2, instance có thể chạy hàng tháng.
- **Chi phí**: vẫn trả theo instance như EC2, nhưng **cộng thêm phí quản lý** — đổi lấy việc không phải tự patch AMI, tự scale ASG, tự quản lý agent.

---

## 2. Điều kiện tiên quyết

**📘 Theo docs AWS**

- Có các **IAM role** cho Managed Instances:

| Role | Vai trò | Tài liệu |
|---|---|---|
| **Infrastructure role** | Cho ECS gọi dịch vụ AWS thay bạn để quản lý hạ tầng Managed Instances | <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/infrastructure_IAM_role.html> |
| **Instance profile** | Cấp quyền cho **ECS container agent** và **Docker daemon** trên managed instance | <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/managed-instances-instance-profile.html> |

- Hiểu **khác biệt mô hình bảo mật** giữa EC2 và Managed Instances.

**💡 Giải thích thêm**

Trên EC2 bạn đã có **instance profile** cho container instance (thường là `ecsInstanceRole`). Managed Instances cần instance profile riêng theo hướng dẫn ở link trên — không mặc định dùng lại được role cũ; nên kiểm tra quyền theo tài liệu. Đây là gợi ý thận trọng của mình.

---

## 3. Bước 1 — Cập nhật cluster dùng Managed Instances

**📘 Theo docs AWS**

- Tạo **capacity provider** Managed Instances; capacity provider này **chỉ dùng được trong cluster** đã chỉ định.
- (Xem *Creating a capacity provider for Amazon ECS Managed Instances* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/create-capacity-provider-managed-instances.html>.)

**💡 Giải thích thêm**

Có thể thêm capacity provider Managed Instances vào **cùng cluster** đang có capacity provider Auto Scaling group — hai loại cùng tồn tại trong cluster, nhưng **không** trộn trong cùng một capacity provider strategy (file *Architect for Amazon ECS Managed Instances*). Nhờ vậy migrate được **từng service một**.

Khi chuyển từ instance type đang dùng trên ASG, có thể khai báo `allowedInstanceTypes` cùng family (file *Managed Instances instance types*) để hiệu năng tương đương, hoặc dùng chọn theo thuộc tính để linh hoạt hơn (file *Instance selection best practices*).

---

## 4. Bước 2 — Cập nhật task definition

**📘 Theo docs AWS**

- Cập nhật task definition để có khả năng tương thích với Managed Instances.
- (Xem *Updating an Amazon ECS task definition using the console* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/update-task-definition-console-v2.html>.)

**💡 Giải thích thêm**

Giống trang Fargate, trang này viết "requiresCapabilities"; tên trường đúng là **`requiresCompatibilities`**:

```json
"requiresCompatibilities": ["EC2", "MANAGED_INSTANCES"]
```

Giữ cả `EC2` trong giai đoạn chuyển tiếp chỉ được nếu task definition hợp lệ cho cả hai — vd `networkMode` phải là `awsvpc` hoặc `host` (không `bridge`). Đây là chỗ khác nhiều nhất so với migrate từ Fargate — xem mục 7.

---

## 5. Bước 3 — Cập nhật service

**📘 Theo docs AWS**

- Cập nhật service hiện có để dùng **capacity provider Managed Instances**.
- (Xem *Updating an Amazon ECS service to use a capacity provider* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/update-service-managed-instances.html>.)

**💡 Giải thích thêm**

```bash
aws ecs update-service \
  --cluster <CLUSTER> \
  --service <SERVICE> \
  --task-definition <FAMILY>:<REVISION_MỚI> \
  --capacity-provider-strategy capacityProvider=<MI_CAPACITY_PROVIDER>,weight=1 \
  --force-new-deployment
```

Nếu service cũ dùng mode **`bridge`** với ALB target type **`instance`**, chuyển sang `awsvpc` thì target group phải là type **`ip`** → cần **target group mới** (target type không đổi được trên target group có sẵn — kiến thức chung về ELB). Cách an toàn: tạo service mới song song rồi chuyển traffic. Gợi ý của mình.

---

## 6. Bước 4 — Migrate standalone task

**📘 Theo docs AWS**

- Với **standalone task**, chỉ định **capacity provider Managed Instances** khi chạy task.
- (Xem *Running an application as an Amazon ECS task* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/standalone-task-create.html>.)

**💡 Giải thích thêm**

Cập nhật cả các nơi gọi `RunTask` tự động: EventBridge Scheduler, pipeline CI/CD, Lambda… để dùng capacity provider mới. Standalone task không được thay tự động khi instance bị drain → nên ≤ 14 ngày và chạy lại được (files *Security considerations*, *Infrastructure optimization*).

---

## 7. Những thứ trên EC2 cần thay thế

**💡 Giải thích thêm**

Trang gốc không đi vào chi tiết; bảng dưới mình ghép từ các file đã làm để thấy những gì thường dùng trên EC2 tự quản lý mà Managed Instances **không có** hoặc **làm khác**:

| Trên EC2 tự quản lý | Trên Managed Instances | File tham khảo |
|---|---|---|
| Network mode `bridge` (dynamic port), `none` | Chỉ `awsvpc` / `host` | *task networking for Managed Instances* |
| `links`, `hostname`, `dnsServers`, `extraHosts`, `disableNetworking` | Không hợp lệ | *Managed Instances task definition differences* |
| `maxSwap`, `swappiness`, `sharedMemorySize` | Không hợp lệ | *container swap*, *Managed Instances task definition differences* |
| Docker volume (`dockerVolumeConfiguration`) | Không hợp lệ → dùng bind mount (`/var`, `/tmp`), EFS, EBS | *Managed Instances task definition differences* |
| Bind mount `sourcePath` bất kỳ | Phải ở thư mục ghi được (`/var`, `/tmp`) | *Managed Instances task definition differences* |
| Custom AMI, user data, `/etc/ecs/ecs.config` (`ECS_IMAGE_PULL_BEHAVIOR`, `ECS_AVAILABLE_LOGGING_DRIVERS`…) | **Không có** — AMI Bottlerocket do AWS quản lý | *Architect for Amazon ECS Managed Instances*, *Managed Instances pull behavior* |
| SSH vào instance | Không có → dùng **ECS Exec** | *Architect for Amazon ECS Managed Instances* |
| Placement **strategy** (`binpack`, `spread`, custom attribute) | Không hỗ trợ strategy; constraint chỉ 4 thuộc tính; điều khiển gián tiếp qua subnet | *How Amazon ECS places tasks*, *Managed Instances auto scaling and task placement* |
| Service strategy **`DAEMON`** | Dùng **Managed Daemons** | *Amazon ECS Managed Daemons* |
| `privileged`, capability đặc quyền | Có, nhưng phải **bật trên capacity provider** | *Security considerations* |
| Log driver `fluentd`, `gelf`, `syslog`… | Chỉ `awslogs`, `splunk`, `awsfirelens` | *task definition parameters for Managed Instances* |
| Windows container | **Không** — chỉ Linux | *Architect for Amazon ECS Managed Instances* |
| Instance chạy lâu dài | Thay sau 14–21 ngày | *Patching* |
| ASG + cluster auto scaling | Capacity provider tự scale, consolidation, `ScaleInAfter` | *Infrastructure optimization* |
| SCP giới hạn AMI của công ty | Phải cho phép AMI account của Managed Instances | *Architect for Amazon ECS Managed Instances* |

---

## 8. Checklist migrate

**💡 Giải thích thêm**

```
Đánh giá
[ ] Workload là Linux (không Windows)
[ ] Không có task chạy liên tục > 14 ngày
[ ] Chấp nhận multi-task/instance hoặc dùng single-task mode
[ ] So chi phí: instance + phí quản lý vs ASG hiện tại

Task definition
[ ] networkMode → awsvpc (hoặc host); bỏ bridge/none
[ ] Gỡ links, hostname, dnsServers, extraHosts, swap, sharedMemorySize, dockerVolumeConfiguration
[ ] Bind mount sourcePath → /var hoặc /tmp; hoặc chuyển sang EFS/EBS
[ ] Log driver → awslogs / splunk / awsfirelens
[ ] requiresCompatibilities có MANAGED_INSTANCES; khai báo cpuArchitecture nếu cần

Hạ tầng
[ ] Infrastructure role + instance profile cho Managed Instances
[ ] SCP / EC2 Allowed AMIs cho phép AMI account Managed Instances
[ ] Capacity provider Managed Instances (instance type / thuộc tính, subnet, storage)
[ ] Bật capability đặc quyền trên capacity provider nếu cần privileged
[ ] EC2 event window giờ thấp điểm
[ ] Agent chạy kiểu DAEMON → chuyển sang Managed Daemons

Chuyển đổi
[ ] Target group mới type ip (nếu trước đây bridge + instance)
[ ] Update service (hoặc service song song + chuyển traffic)
[ ] Standalone task / scheduler / pipeline dùng capacity provider mới

Sau migrate
[ ] Task RUNNING, target health, metric
[ ] Gỡ capacity provider ASG cũ khi không còn dùng
```

---

## 9. Tóm tắt

- Migrate EC2 → Managed Instances để dùng mọi EC2 instance type, capacity reservation, năng lực nâng cao, mà AWS lo hạ tầng.
- **Cân nhắc** (giống trang Fargate): task definition phần lớn tương thích; mặc định nhiều task/instance (có single-task mode); instance tối đa **14 ngày** → dùng service; trả **instance + phí quản lý**; cấu hình **EC2 event windows**.
- **Tiên quyết**: **infrastructure role**, **instance profile**; hiểu khác biệt bảo mật.
- **4 bước**: tạo capacity provider Managed Instances (chỉ trong cluster đó) → task definition có `MANAGED_INSTANCES` → update service dùng capacity provider mới → standalone task chỉ định capacity provider mới.
- Khác biệt thực tế lớn nhất khi rời EC2 (giải thích thêm): bỏ `bridge`, Docker volume, swap, custom AMI/agent config, placement strategy, Windows; chuyển `DAEMON` sang Managed Daemons; bật capability đặc quyền trên capacity provider.

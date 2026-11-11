# Amazon ECS Managed Instances capacity providers

> Nguồn: **Amazon ECS Developer Guide — Amazon ECS Managed Instances capacity providers**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/managed-instances-capacity-providers-concept.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: diễn đạt lại bằng tiếng Việt, bám sát từng ý và đúng thứ tự của trang gốc, không thêm thông tin ngoài trang. Đây không phải bản dịch từng chữ; khi cần câu chữ chính xác, đọc link gốc ở trên.
> - Khối **💡 Giải thích thêm**: phần giải thích để dễ hiểu hơn, chỉ làm rõ những gì docs đã nói.

---

## Mục lục

0. [Capacity provider Managed Instances là gì](#0-capacity-provider-managed-instances-là-gì)
1. [Khi nào dùng custom capacity provider](#1-khi-nào-dùng-custom-capacity-provider)
2. [Lựa chọn giám sát](#2-lựa-chọn-giám-sát)
3. [Cân nhắc về capacity provider](#3-cân-nhắc-về-capacity-provider)
4. [Lan truyền tag](#4-lan-truyền-tag)
5. [Bức tranh tổng hợp: một capacity provider Managed Instances gồm những gì](#5-bức-tranh-tổng-hợp-một-capacity-provider-managed-instances-gồm-những-gì)
6. [Tóm tắt](#6-tóm-tắt)

---

## 0. Capacity provider Managed Instances là gì

**📘 Theo docs AWS**

- Capacity provider Managed Instances cung cấp mô hình compute container cho phép dùng **toàn bộ năng lực AWS và EC2**, trong khi **AWS gánh trách nhiệm vận hành và bảo mật**.
- AWS lo **vá phần mềm và hệ điều hành**, **scale instance**, **bảo trì** → có lợi ích vận hành giống **Fargate** mà vẫn dùng được mọi năng lực và tích hợp của AWS.
- ECS **tạo launch template** cho Managed Instances. Launch template định nghĩa cách ECS launch instance, gồm:
  - **instance profile** cho task;
  - cấu hình **mạng** và **lưu trữ**;
  - **capacity option**;
  - **instance requirements** để chọn instance type linh hoạt.

**💡 Giải thích thêm**

Các phần của launch template trên trang khớp với khối `instanceLaunchTemplate` trong các ví dụ CLI ở file trước:

| Thành phần launch template (theo trang) | Trường tương ứng (các file trước) |
|---|---|
| Instance profile | `ec2InstanceProfileArn` |
| Mạng | `networkConfiguration` (subnets, securityGroups) |
| Lưu trữ | `storageConfiguration`, `localStorageConfiguration` |
| Capacity option | `capacityOptionType` (On-Demand / `Spot` / `Reserved`) |
| Instance requirements | `instanceRequirements`, `allowedInstanceTypes`, `excludedInstanceTypes` |

Ánh xạ là mình ghép từ các file *Architect for Amazon ECS Managed Instances*, *Managed Instances instance types*, *Updating a cluster*.

---

## 1. Khi nào dùng custom capacity provider

**📘 Theo docs AWS**

Cân nhắc custom capacity provider khi workload cần:

| Nhu cầu | Ví dụ theo docs |
|---|---|
| **Yêu cầu compute cụ thể** | Tăng tốc phần cứng, tập lệnh CPU cụ thể, mạng hiệu năng cao, memory lớn — những thứ **không có** trong lựa chọn Fargate tiêu chuẩn |
| **Workload GPU** | ML inference, render ảnh thời gian thực, mã hoá video, ứng dụng tăng tốc GPU cần **GPU NVIDIA hoặc AMD** |
| **Capacity reservation** | Workload trọng yếu cần **capacity sẵn có, dự đoán được** |
| **Observability nâng cao** | Công cụ bảo mật và giám sát cần **quyền đặc quyền** vào OS, vd giải pháp giám sát dựa trên **eBPF**, công cụ phân tích mạng |
| **Tối ưu chi phí** | Workload hưởng lợi từ **nhiều task trên một instance**, **dùng chung** thành phần hạ tầng, hoặc cần tận dụng tối đa instance lớn |

**💡 Giải thích thêm**

| Nhu cầu | Cấu hình custom liên quan | File tham khảo |
|---|---|---|
| GPU | `allowedInstanceTypes` (G, P…), GPU auto repair | *Managed Instances instance types*, *GPU auto repair* |
| Capacity reservation | `capacityOptionType: Reserved` + reservation group | *Managed Instances instance types*, *Instance selection best practices* |
| eBPF / quyền đặc quyền | Bật capability đặc quyền trên capacity provider; chạy agent bằng Managed Daemons | *Security considerations*, *Daemon task definitions* |
| Memory lớn | Thuộc tính tỉ lệ memory/vCPU, family R/X/U | *Managed Instances instance types* |

Trang này nhắc **GPU AMD**, trong khi file *GPU auto repair* chỉ nói về theo dõi **GPU NVIDIA** (DCGM, mã Xid) — tức auto repair GPU có thể chỉ áp dụng cho NVIDIA. Đây là suy luận ghép từ hai trang.

---

## 2. Lựa chọn giám sát

**📘 Theo docs AWS**

Managed Instances có nhiều mức giám sát để theo dõi hiệu năng, sức khoẻ, mức dùng tài nguyên:

| Mức | Nội dung | Chi phí |
|---|---|---|
| **Basic monitoring** | Metric thiết yếu, chu kỳ **5 phút** cho phần lớn metric và **1 phút** cho status check. **Bật mặc định** | **Không tính thêm** |
| **Detailed monitoring** | **Mọi metric** chu kỳ **1 phút**, phát hiện và phản ứng sự cố nhanh hơn. (Xem *Detailed monitoring for Amazon ECS Managed Instances* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/monitoring-managed-instances.html#detailed-monitoring-managed-instances>) | (trang không nói) |

Cả hai tích hợp với **CloudWatch** cho dashboard, alarm và phản ứng tự động.

**💡 Giải thích thêm**

Đây là giám sát **mức instance** (giống basic/detailed monitoring của EC2), khác với **Container Insights** ở mức cluster/task (file *Amazon ECS clusters*). Có thể dùng cả hai. Với EC2 thường, detailed monitoring tính phí thêm — với Managed Instances trang không nói, nên kiểm tra bảng giá. Phần so sánh với EC2 là kiến thức chung, không có trên trang.

---

## 3. Cân nhắc về capacity provider

**📘 Theo docs AWS**

- Cluster có thể chứa **lẫn** capacity provider Managed Instances, Auto Scaling group và Fargate.
- Một **capacity provider strategy** chỉ chứa **một loại**: Managed Instances, **hoặc** Auto Scaling group, **hoặc** Fargate.

**💡 Giải thích thêm**

Quy tắc này đã xuất hiện ở *Amazon ECS clusters* và *Architect for Amazon ECS Managed Instances*. Trong cùng loại Managed Instances, một strategy vẫn có thể chứa **nhiều** capacity provider Managed Instances (vd một On-Demand, một Spot, chia bằng base/weight) — suy luận từ việc quy tắc chỉ giới hạn theo **loại**.

---

## 4. Lan truyền tag

**📘 Theo docs AWS**

- Capacity provider Managed Instances hỗ trợ **lan truyền tag (tag propagation)**.
- Bật lên thì **mọi tài nguyên** do capacity provider quản lý được gắn **cùng tag** khai báo ở mức capacity provider:
  - **managed instance**;
  - **ECS container instance**;
  - **launch template**;
  - **volume**;
  - **ENI**.
- Khai báo tag khi **tạo** capacity provider và bật lan truyền bằng **`propagateTags`** = **`CAPACITY_PROVIDER`**.
- (Xem *Tags for Amazon ECS Managed Instances* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/instance-details-tags-managed-instances.html>.)

**💡 Giải thích thêm**

Công dụng thực tế (gợi ý của mình):

| Mục đích | Tag ví dụ |
|---|---|
| Phân bổ chi phí theo team/dự án trong Cost Explorer | `Team=backend`, `Project=nks` |
| Lọc tài nguyên khi managed instance bị **ẩn** khỏi console EC2 (từ 22/4/2026 — file *Security considerations*) | `Environment=prod` |
| Kiểm soát truy cập theo tag trong IAM | `Owner=platform` |

Lưu ý: trang nói khai báo tag "khi tạo capacity provider" — nên lên kế hoạch tag ngay từ đầu.

```bash
aws ecs create-capacity-provider \
  --cluster <CLUSTER> \
  --name <MI_CAPACITY_PROVIDER> \
  --tags key=Team,value=backend key=Environment,value=prod \
  --propagate-tags CAPACITY_PROVIDER \
  --managed-instances-provider '{ ... }'
```

Tên cờ CLI `--tags` / `--propagate-tags` là mình suy ra từ tên tham số `propagateTags` trên trang; kiểm tra CLI reference trước khi dùng.

---

## 5. Bức tranh tổng hợp: một capacity provider Managed Instances gồm những gì

**💡 Giải thích thêm**

Ghép trang này với các file trước:

```
Capacity provider Managed Instances (gắn với một cluster)
├─ infrastructureRoleArn                      → ECS quản lý hạ tầng
├─ instanceLaunchTemplate (ECS tạo launch template)
│   ├─ ec2InstanceProfileArn                  → quyền cho agent / Docker
│   ├─ networkConfiguration                   → subnet (ảnh hưởng trải AZ / bin packing), SG
│   ├─ storageConfiguration                   → EBS data volume 30–16.384 GiB (mặc định 80)
│   ├─ localStorageConfiguration              → instance store (RAID 0, tạm thời)
│   ├─ instanceRequirements / allowed / excluded instance types
│   ├─ capacityOptionType                     → On-Demand | Spot | Reserved
│   └─ fipsEnabled                            → FIPS (GovCloud)
├─ autoRepairConfiguration.actionsStatus      → GPU + daemon auto repair
├─ ScaleInAfter                               → tối ưu instance idle/underutilized
├─ (bật) capability đặc quyền                 → privileged, eBPF…
├─ giám sát: basic (mặc định) | detailed
└─ tags + propagateTags = CAPACITY_PROVIDER
```

Một số tên trường (vị trí chính xác, cách viết hoa của ScaleInAfter, trường bật capability đặc quyền) chưa được các trang nêu rõ — nên đối chiếu API reference khi viết IaC.

---

## 6. Tóm tắt

- Capacity provider Managed Instances: dùng mọi năng lực EC2/AWS, AWS lo vá OS, scale, bảo trì — vận hành nhẹ như Fargate.
- ECS **tạo launch template**: instance profile, mạng, lưu trữ, capacity option, instance requirements.
- **Dùng custom** khi cần: compute đặc thù (tăng tốc, tập lệnh, mạng cao, memory lớn), **GPU** (NVIDIA hoặc AMD), **capacity reservation**, **observability đặc quyền** (eBPF), **tối ưu chi phí** (nhiều task/instance, instance lớn).
- **Giám sát**: **basic** (5 phút, status check 1 phút, mặc định, miễn phí) hoặc **detailed** (mọi metric 1 phút); tích hợp CloudWatch.
- Cluster chứa lẫn được các loại capacity provider; **một strategy chỉ một loại**.
- **Tag propagation**: `propagateTags = CAPACITY_PROVIDER` → tag gắn lên managed instance, container instance, launch template, volume, ENI.

# Updating a cluster to use Amazon ECS Managed Instances

> Nguồn: **Amazon ECS Developer Guide — Updating a cluster to use Amazon ECS Managed Instances**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/update-cluster-managed-instances.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: diễn đạt lại bằng tiếng Việt, bám sát từng bước và đúng thứ tự của trang gốc, không thêm thông tin ngoài trang. Đây không phải bản dịch từng chữ; khi cần câu chữ hoặc ví dụ lệnh chính xác, đọc link gốc ở trên.
> - Khối **💡 Giải thích thêm**: phần giải thích để dễ hiểu hơn, chỉ làm rõ những gì docs đã nói.
> - Lệnh CLI dùng placeholder (`<...>`); tên nút trên console giữ tiếng Anh (in đậm).

---

## Mục lục

0. [Tổng quan](#0-tổng-quan)
1. [Điều kiện tiên quyết](#1-điều-kiện-tiên-quyết)
2. [Những điều cần cân nhắc khi cập nhật](#2-những-điều-cần-cân-nhắc-khi-cập-nhật)
3. [Cập nhật cluster bằng console](#3-cập-nhật-cluster-bằng-console)
4. [Cập nhật cluster bằng AWS CLI](#4-cập-nhật-cluster-bằng-aws-cli)
5. [Chỗ chưa rõ / không khớp trên trang](#5-chỗ-chưa-rõ--không-khớp-trên-trang)
6. [Tóm tắt](#6-tóm-tắt)

---

## 0. Tổng quan

**📘 Theo docs AWS**

- Có thể cập nhật một cluster **đang có** để dùng Managed Instances.
- Khi thêm Managed Instances vào cluster, ECS **tự tạo** capacity provider Managed Instances với **cấu hình mặc định** — chọn instance **general-purpose tối ưu chi phí nhất**.
- Có thể tạo thêm **custom capacity provider** nếu cần thuộc tính hoặc instance type cụ thể.

**💡 Giải thích thêm**

Đây là bước 1 trong cả hai file *Migrate from Fargate / EC2 to Managed Instances*: cluster cũ (đang chạy Fargate hoặc ASG) được bổ sung capacity provider Managed Instances, các loại capacity provider cùng tồn tại trong cluster (file *Amazon ECS clusters*).

---

## 1. Điều kiện tiên quyết

**📘 Theo docs AWS**

- Không khai báo `instanceRequirements` → ECS tự chọn instance tối ưu chi phí theo task definition. Muốn thuộc tính hoặc type cụ thể → tạo capacity provider có `instanceRequirements`.
- Có các IAM role cho Managed Instances:

| Role | Vai trò | Tài liệu |
|---|---|---|
| **Infrastructure role** | Cho ECS gọi dịch vụ AWS thay bạn để quản lý hạ tầng Managed Instances | <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/infrastructure_IAM_role.html> |
| **Instance profile** | Cấp quyền cho ECS container agent và Docker daemon trên managed instance | <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/managed-instances-instance-profile.html> |

---

## 2. Những điều cần cân nhắc khi cập nhật

**📘 Theo docs AWS**

| Chủ đề | Nội dung |
|---|---|
| **Task đang chạy** | Cập nhật cài đặt cluster **không ảnh hưởng** task đang chạy; thay đổi áp dụng cho task **mới** launch sau khi cập nhật |
| **Thay đổi capacity provider** | Sửa cài đặt capacity provider thì managed instance **hiện có vẫn chạy tiếp**, instance **mới** mới dùng cấu hình mới |
| **Thay đổi giám sát** | Bật/tắt **Container Insights** ảnh hưởng việc thu metric của **toàn cluster** |

**💡 Giải thích thêm**

Hệ quả của dòng "thay đổi capacity provider": cấu hình mới (vd đổi instance type, bật VPC encryption) chỉ "phủ" dần khi instance cũ được thay — qua drain theo vòng đời 14–21 ngày (file *Patching*), qua consolidation (file *Infrastructure optimization*), hoặc khi deploy lại service khiến instance mới được tạo. Ví dụ thực tế ở file *VPC Encryption control*: phải chờ mọi instance cũ được thay rồi mới chuyển sang Enforced.

---

## 3. Cập nhật cluster bằng console

**📘 Theo docs AWS**

1. Mở console tại <https://console.aws.amazon.com/ecs/v2>.
2. Chọn **Region** trên thanh điều hướng.
3. Thanh bên → **Clusters**.
4. Trang **Clusters** → chọn cluster cần cập nhật.
5. **Update cluster**.
6. (Tuỳ chọn) Sửa cài đặt capacity provider ở **Custom Capacity Provider**:
   - **Instance profile**: chọn instance profile role khác nếu cần.
   - **Infrastructure role**: chọn infrastructure role khác nếu cần.
   - Dùng custom capacity provider: ở **Instance selection**, cập nhật các **Attribute value**.
7. **Update**.

---

## 4. Cập nhật cluster bằng AWS CLI

**📘 Theo docs AWS**

- Dùng **phiên bản AWS CLI mới nhất** (<https://docs.aws.amazon.com/cli/latest/userguide/getting-started-install.html>).

> **Note (docs):** Có thể dùng **dual-stack service endpoint** cho CLI, SDK, API trên cả IPv4 và IPv6. (Xem <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/dual-stack-endpoint.html>.)

Hai bước theo docs:

1. **Tạo capacity provider** Managed Instances bằng `create-capacity-provider`. Ví dụ docs khai báo: tên capacity provider, instance profile, infrastructure role, và `instanceRequirements` với **vCPU 2–8** và **memory 4096–16384 MiB**.
2. **Gắn capacity provider vào cluster** và đặt **strategy mặc định** bằng `put-cluster-capacity-providers` (với `--capacity-providers` và `--default-capacity-provider-strategy capacityProvider=<…>,weight=1`).

**💡 Giải thích thêm**

Lệnh ở bước 1 trên trang gốc có vẻ **bị lỗi cú pháp** (xem mục 5). Dưới đây là dạng mình viết lại, theo cấu trúc JSON của `--managed-instances-provider` được dùng ở các trang *Security considerations*, *GPU auto repair*, *Architect for Amazon ECS Managed Instances*. Vị trí chính xác của `instanceRequirements` trong JSON nên đối chiếu API reference trước khi chạy:

```bash
# 1. Tạo capacity provider Managed Instances (custom: vCPU 2–8, memory 4–16 GiB)
aws ecs create-capacity-provider \
  --cluster <CLUSTER_NAME> \
  --name <MI_CAPACITY_PROVIDER> \
  --managed-instances-provider '{
    "infrastructureRoleArn": "<INFRA_ROLE_ARN>",
    "instanceLaunchTemplate": {
      "ec2InstanceProfileArn": "<INSTANCE_PROFILE_ARN>",
      "networkConfiguration": { "subnets": ["<SUBNET_ID>"], "securityGroups": ["<SG_ID>"] },
      "instanceRequirements": {
        "vCpuCount": { "min": 2, "max": 8 },
        "memoryMiB": { "min": 4096, "max": 16384 }
      }
    }
  }'

# 2. Gắn vào cluster + strategy mặc định
aws ecs put-cluster-capacity-providers \
  --cluster <CLUSTER_NAME> \
  --capacity-providers <MI_CAPACITY_PROVIDER> \
  --default-capacity-provider-strategy capacityProvider=<MI_CAPACITY_PROVIDER>,weight=1
```

**Cẩn thận với `put-cluster-capacity-providers`** (kiến thức chung về lệnh này, trang không nói): lệnh **thay thế toàn bộ** danh sách capacity provider và strategy mặc định của cluster. Nếu cluster đang có `FARGATE`, `FARGATE_SPOT` hoặc capacity provider ASG mà service còn dùng, phải **liệt kê lại cả chúng** trong `--capacity-providers`, nếu không lệnh có thể bị từ chối hoặc gỡ mất provider đang dùng. Ví dụ:

```bash
aws ecs put-cluster-capacity-providers \
  --cluster <CLUSTER_NAME> \
  --capacity-providers FARGATE FARGATE_SPOT <MI_CAPACITY_PROVIDER> \
  --default-capacity-provider-strategy capacityProvider=<MI_CAPACITY_PROVIDER>,weight=1
```

Nhớ rằng strategy **mặc định** vẫn chỉ được chứa **một loại** provider (file *Amazon ECS clusters*).

---

## 5. Chỗ chưa rõ / không khớp trên trang

**💡 Giải thích thêm**

| Chỗ | Vấn đề | Cách xử lý gợi ý |
|---|---|---|
| Bước 1 CLI ghi "Create a capacity provider for ." | Thiếu chữ (có lẽ "for Amazon ECS Managed Instances") | — |
| Lệnh `create-capacity-provider` ví dụ | `--managed-instances-provider` **không có giá trị**, rồi tiếp các cờ `--instance-profile`, `--infrastructure-role-arn`, `--instance-requirements` như cờ riêng; **thiếu dấu nháy đóng** sau JSON | Dùng dạng JSON trong `--managed-instances-provider` như mục 4 (khớp các trang khác) |
| Thiếu `--cluster` trong `create-capacity-provider` | Trang *Migrate* nói capacity provider Managed Instances **chỉ dùng trong cluster đã chỉ định**; các ví dụ ở trang khác đều có `--cluster` | Thêm `--cluster` |
| Mục *Update considerations* nhắc **Container Insights** | Nhưng các bước console/CLI trên trang không có bước đổi monitoring | Đổi qua cài đặt cluster (vd `update-cluster-settings`) hoặc console — trang không hướng dẫn |

---

## 6. Tóm tắt

- Cập nhật cluster có sẵn để dùng Managed Instances; ECS tự tạo capacity provider **mặc định** (instance general-purpose rẻ nhất); tạo **custom** nếu cần `instanceRequirements`.
- **Tiên quyết**: **infrastructure role** và **instance profile**.
- **Cân nhắc**: task đang chạy **không bị ảnh hưởng** (chỉ task mới); đổi capacity provider thì instance **cũ vẫn chạy**, chỉ instance **mới** theo cấu hình mới; bật/tắt Container Insights ảnh hưởng **cả cluster**.
- **Console**: Clusters → chọn cluster → **Update cluster** → (tuỳ chọn) đổi instance profile, infrastructure role, attribute của instance selection → **Update**.
- **CLI**: `create-capacity-provider` (ví dụ vCPU 2–8, memory 4–16 GiB) → `put-cluster-capacity-providers` (gắn vào cluster + strategy mặc định weight 1). Lệnh ví dụ trên trang có lỗi cú pháp — dùng dạng JSON `--managed-instances-provider` và nhớ liệt kê lại các capacity provider đang dùng khi `put-cluster-capacity-providers`.

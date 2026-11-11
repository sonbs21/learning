# Creating an Amazon ECS cluster for Fargate workloads

> Nguồn: **Amazon ECS Developer Guide — Creating an Amazon ECS cluster for Fargate workloads**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/create-cluster-console-v2.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: diễn đạt lại bằng tiếng Việt, bám sát từng bước và đúng thứ tự của trang gốc, không thêm thông tin ngoài trang. Đây không phải bản dịch từng chữ; khi cần câu chữ chính xác, đọc link gốc ở trên.
> - Khối **💡 Giải thích thêm**: phần giải thích để dễ hiểu hơn, chỉ làm rõ những gì docs đã nói.
> - Tên nút trên console giữ tiếng Anh (in đậm); lệnh CLI dùng placeholder (`<...>`).

---

## Mục lục

0. [Tổng quan và điều kiện](#0-tổng-quan-và-điều-kiện)
1. [Mặc định và tuỳ chọn](#1-mặc-định-và-tuỳ-chọn)
2. [Các bước tạo cluster trên console](#2-các-bước-tạo-cluster-trên-console)
3. [Bước tiếp theo](#3-bước-tiếp-theo)
4. [So sánh với tạo cluster Managed Instances](#4-so-sánh-với-tạo-cluster-managed-instances)
5. [Tương đương CLI](#5-tương-đương-cli)
6. [Tóm tắt](#6-tóm-tắt)

---

## 0. Tổng quan và điều kiện

**📘 Theo docs AWS**

- Tạo cluster để định nghĩa hạ tầng mà task và service chạy trên đó.
- Trước khi bắt đầu: làm xong *Set up to use Amazon ECS* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/get-set-up-for-amazon-ecs.html>) và gán quyền IAM phù hợp (*Amazon ECS cluster examples* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/security_iam_id-based-policy-examples.html#IAM_cluster_policies>).
- Console tạo tài nguyên cần cho cluster bằng cách tạo một **CloudFormation stack**.

**💡 Giải thích thêm**

Vì console dùng CloudFormation phía sau, nếu tạo cluster lỗi có thể xem chi tiết trong **CloudFormation → Stacks** (gợi ý của mình). Nếu bạn quản lý hạ tầng bằng Terraform, nên tạo cluster bằng Terraform thay vì console để tránh tài nguyên "trôi" ngoài IaC.

---

## 1. Mặc định và tuỳ chọn

**📘 Theo docs AWS**

**Mặc định:** console **tự gắn** capacity provider **Fargate** và **Fargate Spot** vào cluster.

**Có thể thay đổi:**

| Tuỳ chọn | Nội dung |
|---|---|
| **Namespace** | Thêm namespace cho cluster — service trong cluster kết nối được với service khác cùng namespace **không cần cấu hình thêm**. (Xem *Interconnect Amazon ECS services* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/interconnecting-services.html>.) |
| **Task events** | Bật để nhận thông báo **EventBridge** khi task đổi trạng thái |
| **Tag** | Nhận diện cluster |
| **KMS key cho managed storage** | (Xem *Create a KMS key* — <https://docs.aws.amazon.com/kms/latest/developerguide/create-keys.html>.) |
| **KMS key cho Fargate ephemeral storage** | (Như trên) |
| **ECS Exec** | Cấu hình **KMS key** và **logging** cho ECS Exec |

**💡 Giải thích thêm**

So với file *Creating a cluster for Amazon ECS Managed Instances*: cluster Fargate có thêm **task events**, **KMS cho ephemeral storage** và **cấu hình ECS Exec**; không cần infrastructure role hay instance profile.

---

## 2. Các bước tạo cluster trên console

**📘 Theo docs AWS**

1. Mở console tại <https://console.aws.amazon.com/ecs/v2>.
2. Chọn **Region**.
3. Thanh bên → **Clusters**.
4. **Create cluster**.
5. **Cluster configuration**:
   - **Cluster name**: tên duy nhất, tối đa **255** ký tự (chữ hoa/thường, số, gạch nối).
   - (Tuỳ chọn) **Service Connect defaults** → **Default namespace**: chọn hoặc nhập tên namespace nếu muốn khác tên cluster. Dùng **shared namespace** thì chọn hoặc nhập **ARN** của namespace. (Xem *Amazon ECS Service Connect with shared AWS Cloud Map namespaces* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/service-connect-shared-namespaces.html>.)
6. (Tuỳ chọn) **Monitoring** — Container Insights:
   - **Container Insights with enhanced observability** (được khuyến nghị); hoặc
   - **Container Insights**.
7. (Tuỳ chọn) **Task events** → bật **Enable task events**: ECS gửi **task state change event** tới EventBridge, giúp giám sát và tự động phản ứng với thay đổi vòng đời task.
8. (Tuỳ chọn) **Troubleshooting configuration** — dùng **ECS Exec** để debug task:
   - **AWS KMS key for ECS Exec**: ARN KMS key để mã hoá dữ liệu phiên ECS Exec.
   - **ECS Exec logging**: **Amazon CloudWatch**, **Amazon S3**, hoặc **None** (tắt log).
9. (Tuỳ chọn) **Encryption**:
   - **Fargate ephemeral storage**: ARN KMS key mã hoá dữ liệu ephemeral storage của Fargate.
   - **Managed storage**: ARN KMS key mã hoá dữ liệu managed storage.
10. (Tuỳ chọn) **Tags**: **Add tag** (Key, Value) hoặc **Remove**.
11. **Create**.

**💡 Giải thích thêm**

```
Create cluster
 ├─ Tên + (namespace Service Connect, có thể dùng shared namespace qua ARN)
 ├─ Monitoring: Container Insights (enhanced / thường)      ← tính phí
 ├─ Task events → EventBridge
 ├─ ECS Exec: KMS + log (CloudWatch / S3 / None)
 ├─ Encryption: KMS cho ephemeral storage, managed storage
 ├─ Tags
 └─ (tự động) FARGATE + FARGATE_SPOT
```

- **Task events** chính là luồng sự kiện `ECS Task State Change` mà file *Amazon ECS clusters for Fargate* dùng để bắt **Spot interruption**.
- **ECS Exec logging** nên bật (CloudWatch hoặc S3) để có nhật ký kiểm toán khi người dùng vào container — gợi ý bảo mật của mình.
- Ephemeral storage của Fargate liên quan tham số `ephemeralStorage` (file *task definition parameters for Fargate*).

---

## 3. Bước tiếp theo

**📘 Theo docs AWS**

Tạo task definition rồi chạy dưới dạng standalone task hoặc service:
- *Amazon ECS task definitions* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task_definitions.html>
- *Running an application as an Amazon ECS task* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/standalone-task-create.html>
- *Creating an Amazon ECS rolling update deployment* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/create-service-console-v2.html>

---

## 4. So sánh với tạo cluster Managed Instances

**💡 Giải thích thêm**

| | Cluster Fargate (trang này) | Cluster Managed Instances |
|---|---|---|
| Capacity provider tự gắn | `FARGATE`, `FARGATE_SPOT` | Capacity provider Managed Instances mặc định |
| IAM role cần khi tạo | Không thêm | Infrastructure role + instance profile; `iam:CreateServiceLinkedRole` |
| Namespace | Service Connect defaults (hỗ trợ shared namespace) | Namespace Cloud Map trùng tên cluster |
| Task events | Có tuỳ chọn | (trang Managed Instances không nhắc) |
| ECS Exec config | Có (KMS, logging) | (trang Managed Instances không nhắc) |
| KMS | Ephemeral storage + managed storage | Managed storage |
| Cơ chế phía sau | CloudFormation stack | (trang không nhắc) |

---

## 5. Tương đương CLI

**💡 Giải thích thêm**

Trang chỉ hướng dẫn console. Lệnh CLI tương đương (bổ sung của mình — tên tham số nên kiểm tra CLI reference):

```bash
aws ecs create-cluster \
  --cluster-name <CLUSTER_NAME> \
  --capacity-providers FARGATE FARGATE_SPOT \
  --default-capacity-provider-strategy capacityProvider=FARGATE,weight=1 \
  --settings name=containerInsights,value=enhanced \
  --service-connect-defaults namespace=<NAMESPACE_NAME_OR_ARN> \
  --tags key=Environment,value=dev
```

Lưu ý `weight=1`: qua CLI, weight mặc định là **0** (file *Amazon ECS clusters for Fargate*) — luôn khai báo rõ.

Cấu hình ECS Exec (KMS, logging) và KMS cho storage nằm trong `--configuration` của `create-cluster` (khối `executeCommandConfiguration`, `managedStorageConfiguration`) — tên khối là kiến thức chung về API, không có trên trang này.

---

## 6. Tóm tắt

- Tạo cluster Fargate trên console; console dùng **CloudFormation stack** phía sau. Cần setup ECS và quyền IAM trước.
- Console **tự gắn** `FARGATE` và `FARGATE_SPOT`.
- **Tuỳ chọn**: namespace Service Connect (có thể dùng shared namespace qua ARN), **Container Insights** (enhanced hoặc thường), **task events** tới EventBridge, **ECS Exec** (KMS + log CloudWatch/S3/None), **KMS** cho Fargate ephemeral storage và managed storage, **tag**.
- Tên cluster tối đa **255** ký tự (chữ, số, gạch nối).
- **Tiếp theo**: tạo task definition, chạy standalone task hoặc service.

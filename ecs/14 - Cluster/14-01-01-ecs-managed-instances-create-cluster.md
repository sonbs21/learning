# Creating a cluster for Amazon ECS Managed Instances

> Nguồn: **Amazon ECS Developer Guide — Creating a cluster for Amazon ECS Managed Instances**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/create-cluster-managed-instances.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: diễn đạt lại bằng tiếng Việt, bám sát từng bước và đúng thứ tự của trang gốc, không thêm thông tin ngoài trang. Đây không phải bản dịch từng chữ; khi cần câu chữ hoặc ví dụ lệnh chính xác, đọc link gốc ở trên.
> - Khối **💡 Giải thích thêm**: phần giải thích để dễ hiểu hơn, chỉ làm rõ những gì docs đã nói.
> - Lệnh CLI dùng placeholder (`<...>`); tên nút trên console giữ tiếng Anh (in đậm).

---

## Mục lục

0. [Tổng quan](#0-tổng-quan)
1. [Mặc định và tuỳ chọn khi tạo cluster](#1-mặc-định-và-tuỳ-chọn-khi-tạo-cluster)
2. [Điều kiện tiên quyết](#2-điều-kiện-tiên-quyết)
3. [Tạo cluster bằng console](#3-tạo-cluster-bằng-console)
4. [Tạo cluster bằng AWS CLI](#4-tạo-cluster-bằng-aws-cli)
5. [Bước tiếp theo](#5-bước-tiếp-theo)
6. [Ghi chú về CLI và Terraform](#6-ghi-chú-về-cli-và-terraform)
7. [Tóm tắt](#7-tóm-tắt)

---

## 0. Tổng quan

**📘 Theo docs AWS**

- Tạo cluster để định nghĩa hạ tầng mà task và service chạy trên đó.
- Khi tạo cluster cho Managed Instances, ECS **tự tạo** một capacity provider Managed Instances với **cấu hình mặc định** — chọn instance type **tối ưu chi phí nhất** cho workload.
- Có thể tạo thêm **custom capacity provider** nếu cần thuộc tính hoặc instance type cụ thể.

---

## 1. Mặc định và tuỳ chọn khi tạo cluster

**📘 Theo docs AWS**

Console có sẵn nhiều lựa chọn mặc định để việc tạo cluster đơn giản nhất:
- Tạo **namespace mặc định** trong **AWS Cloud Map**, trùng tên cluster. Namespace cho phép service trong cluster kết nối với service khác cùng namespace **không cần cấu hình thêm**. (Xem *Interconnect Amazon ECS services* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/interconnecting-services.html>.)

Có thể thay đổi:
- **Namespace mặc định** gắn với cluster (mặc định trùng tên cluster).
- Gán **AWS KMS key** cho **managed storage**. (Xem *Create a KMS key* — <https://docs.aws.amazon.com/kms/latest/developerguide/create-keys.html>.)
- Thêm **tag** để nhận diện cluster.

**💡 Giải thích thêm**

Namespace này phục vụ **Service Connect** (file *Amazon ECS clusters*: namespace Service Connect mặc định của cluster). "Managed storage" ở đây có lẽ là EBS data volume của managed instance (file *Architect for Amazon ECS Managed Instances*) — trang không định nghĩa rõ, đây là cách hiểu của mình.

---

## 2. Điều kiện tiên quyết

**📘 Theo docs AWS**

- Đã làm các bước trong *Set up to use Amazon ECS* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/get-set-up-for-amazon-ecs.html>) và gán quyền IAM phù hợp (xem *Amazon ECS cluster examples* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/security_iam_id-based-policy-examples.html#IAM_cluster_policies>).
- Người tạo cluster cần thêm quyền **`iam:CreateServiceLinkedRole`**.
- Không khai báo `instanceRequirements` → ECS tự chọn instance tối ưu chi phí theo task definition. Muốn thuộc tính hoặc type cụ thể → tạo capacity provider có `instanceRequirements`.
- Hiểu cách chọn instance: *Instance selection best practices for Amazon ECS Managed Instances* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/managed-instances-instance-selection-best-practices.html>.
- Có các IAM role cho Managed Instances:

| Role | Vai trò | Tài liệu |
|---|---|---|
| **Infrastructure role** | Cho ECS gọi dịch vụ AWS thay bạn để quản lý hạ tầng Managed Instances | <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/infrastructure_IAM_role.html> |
| **Instance profile** | Cấp quyền cho ECS container agent và Docker daemon trên managed instance | <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/managed-instances-instance-profile.html> |

**💡 Giải thích thêm**

`iam:CreateServiceLinkedRole` cần vì ECS có thể phải tạo **service-linked role** trong lần đầu (file *task networking for Fargate* có nhắc service-linked role của ECS). Trong account đã có role này, quyền có thể không bị dùng tới — suy luận của mình.

---

## 3. Tạo cluster bằng console

**📘 Theo docs AWS**

1. Mở console tại <https://console.aws.amazon.com/ecs/v2>.
2. Chọn **Region** trên thanh điều hướng.
3. Thanh bên → **Clusters**.
4. Trang **Clusters** → **Create cluster**.
5. **Cluster configuration**:
   - **Cluster name**: tên duy nhất, tối đa **255** ký tự (chữ hoa/thường, số, gạch nối).
   - (Tuỳ chọn) **Namespace**: tên khác với tên cluster nếu muốn namespace Service Connect khác.
6. **Custom Capacity Provider**:
   - **Select a method of obtaining EC2 capacity** → **Amazon ECS Managed Instances**.
   - **Instance profile**: chọn instance profile role.
   - **Infrastructure role**: chọn infrastructure role.
   - Muốn custom capacity provider: **Instance selection** → **Use custom**, rồi nhập **Attribute value** cho từng thuộc tính.
7. (Tuỳ chọn) **Monitoring** — Container Insights:
   - **Container Insights with enhanced observability** (được khuyến nghị); hoặc
   - **Container Insights**.
8. (Tuỳ chọn) **Encryption** → **Managed storage**: nhập ARN KMS key để mã hoá dữ liệu managed storage.
9. (Tuỳ chọn) **Tags** → **Add tag** → nhập **Key**, **Value**.
10. **Create**.

**💡 Giải thích thêm**

```
Create cluster
 ├─ Cluster name (+ Namespace)
 ├─ Capacity: Amazon ECS Managed Instances
 │    ├─ Instance profile, Infrastructure role
 │    └─ Instance selection: mặc định (rẻ nhất) | Use custom (thuộc tính)
 ├─ Monitoring: Container Insights (enhanced / thường)   ← tính phí thêm
 ├─ Encryption: KMS key cho managed storage
 └─ Tags
```

Bước 6 là nơi capacity provider Managed Instances được tạo cùng lúc với cluster (file *Clusters for Amazon ECS Managed Instances*: "với custom, ECS tạo luôn capacity provider"). Container Insights **tính phí thêm** (file *Amazon ECS clusters*).

---

## 4. Tạo cluster bằng AWS CLI

**📘 Theo docs AWS**

- Dùng **phiên bản AWS CLI mới nhất**. (Xem <https://docs.aws.amazon.com/cli/latest/userguide/getting-started-install.html>.)

> **Note (docs):** Có thể dùng **dual-stack service endpoint** để làm việc với ECS qua CLI, SDK, API trên cả IPv4 và IPv6. (Xem *Using Amazon ECS dual-stack endpoints* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/dual-stack-endpoint.html>.)

1. Tạo cluster với tên duy nhất:

```bash
aws ecs create-cluster --cluster-name <CLUSTER_NAME>
```

Kết quả (theo ví dụ docs) gồm cluster ở trạng thái **`ACTIVE`**, `capacityProviders` **rỗng**, `defaultCapacityProviderStrategy` **rỗng**, setting `containerInsights` = `disabled`, các bộ đếm (instance đăng ký, task pending/running, service active) đều 0, và `clusterArn`.

2. (Tuỳ chọn) Bật **Container Insights with enhanced observability**:

```bash
aws ecs put-account-setting --name containerInsights --value enhanced
```

3. (Tuỳ chọn) Gắn tag cho cluster:

```bash
aws ecs tag-resource --resource-arn <CLUSTER_ARN> --tags key=Environment,value=Production
```

**💡 Giải thích thêm**

Hai điểm cần để ý ở luồng CLI (suy luận từ output và lệnh của docs):

- **`create-cluster` không tạo capacity provider Managed Instances** — output cho thấy `capacityProviders` rỗng. Khác với console (bước 6 tạo luôn). Với CLI, cần tạo capacity provider riêng bằng `create-capacity-provider` (cấu trúc như ở file *Security considerations* / *GPU auto repair*) rồi gắn vào cluster / đặt strategy mặc định.
- **`put-account-setting`** là cài đặt **mức account** (mặc định cho các cluster **mới** về sau), không phải riêng cho cluster vừa tạo. Muốn bật cho đúng một cluster, dùng setting của cluster (vd `--settings name=containerInsights,value=enhanced` khi `create-cluster`) — tham số này trang không nêu, cần kiểm tra trong CLI reference.

Luồng CLI đầy đủ gợi ý:

```bash
# 1. Cluster
aws ecs create-cluster --cluster-name <CLUSTER_NAME>

# 2. Capacity provider Managed Instances
aws ecs create-capacity-provider \
  --cluster <CLUSTER_NAME> \
  --name <MI_CAPACITY_PROVIDER> \
  --managed-instances-provider '{
    "infrastructureRoleArn": "<INFRA_ROLE_ARN>",
    "instanceLaunchTemplate": {
      "ec2InstanceProfileArn": "<INSTANCE_PROFILE_ARN>",
      "networkConfiguration": { "subnets": ["<SUBNET_ID>"], "securityGroups": ["<SG_ID>"] }
    }
  }'

# 3. (Tuỳ chọn) strategy mặc định của cluster
aws ecs put-cluster-capacity-providers \
  --cluster <CLUSTER_NAME> \
  --capacity-providers <MI_CAPACITY_PROVIDER> \
  --default-capacity-provider-strategy capacityProvider=<MI_CAPACITY_PROVIDER>,weight=1
```

Bước 3 (`put-cluster-capacity-providers`) là lệnh chung của ECS cho strategy mặc định; trang này không nhắc — kiểm tra lại có cần với capacity provider Managed Instances (vốn đã gắn cluster khi tạo) hay không.

---

## 5. Bước tiếp theo

**📘 Theo docs AWS**

- Tạo **task definition** cho Managed Instances. (Xem *Creating an Amazon ECS task definition using the console* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/create-task-definition.html>.)
- Chạy ứng dụng dưới dạng **standalone task** hoặc **service**:
  - *Running an application as an Amazon ECS task* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/standalone-task-create.html>
  - *Creating an Amazon ECS rolling update deployment* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/create-service-console-v2.html>

---

## 6. Ghi chú về CLI và Terraform

**💡 Giải thích thêm**

Phần này là mình bổ sung (tên resource theo Terraform AWS provider; kiểm tra phiên bản provider có hỗ trợ Managed Instances chưa):

| Việc | Terraform |
|---|---|
| Cluster | `aws_ecs_cluster` (có `setting { name = "containerInsights" ... }`) |
| Capacity provider | `aws_ecs_capacity_provider` (khối cấu hình Managed Instances) |
| Gắn vào cluster + strategy mặc định | `aws_ecs_cluster_capacity_providers` |
| Namespace Service Connect | `aws_service_discovery_http_namespace` + `service_connect_defaults` của cluster |

---

## 7. Tóm tắt

- Tạo cluster cho Managed Instances → ECS **tự tạo** capacity provider mặc định (chọn instance rẻ nhất); có thể tạo **custom** nếu cần thuộc tính/type cụ thể.
- Console mặc định tạo **namespace Cloud Map** trùng tên cluster; có thể đổi namespace, gán **KMS key** cho managed storage, thêm **tag**.
- **Tiên quyết**: setup ECS + quyền IAM; thêm **`iam:CreateServiceLinkedRole`**; **infrastructure role** và **instance profile**; hiểu cách chọn instance.
- **Console**: Clusters → Create cluster → tên (+ namespace) → **Amazon ECS Managed Instances** + instance profile + infrastructure role (+ **Use custom** nếu cần) → Container Insights (tuỳ chọn) → KMS (tuỳ chọn) → tag → **Create**.
- **CLI**: `create-cluster` (trạng thái `ACTIVE`, chưa có capacity provider); tuỳ chọn `put-account-setting containerInsights=enhanced`, `tag-resource`. Có thể dùng dual-stack endpoint.
- **Tiếp theo**: tạo task definition, chạy standalone task hoặc service.

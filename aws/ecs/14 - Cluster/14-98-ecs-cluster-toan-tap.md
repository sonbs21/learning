# Amazon ECS Cluster — Toàn tập

> Tổng hợp từ **toàn bộ folder `14 - Cluster`**:
>
> | Nhóm | File | Nội dung |
> |---|---|---|
> | Chung | `14-00-ecs-clusters.md` | Cluster là gì, ba loại hạ tầng, trạng thái, capacity provider |
> | Managed Instances | `14-01-00` → `14-01-02` | Cluster cho Managed Instances: tạo, cập nhật |
> | | `14-01-03-00` → `14-01-03-04` | Capacity provider Managed Instances: khái niệm, best practice cập nhật, tạo, đổi giám sát, xoá |
> | | `14-01-04-00`, `14-01-04-01` | Task scale-in protection và endpoint của agent |
> | Fargate | `14-02-00`, `14-02-01` | Cluster Fargate, Fargate Spot, tạo cluster |
> | EC2 | `14-03-00` → `14-03-02` | ASG capacity provider, bảo mật container instance, tạo cluster EC2 |
> | Thuyết trình | `14-99-thuyet-trinh-cto.md` | Góc nhìn quyết định |
>
> **Ký hiệu**
> - Không ký hiệu = lấy từ docs AWS đã dịch trong folder (mỗi mục ghi file nguồn).
> - 🔎 = kiến thức ngoài folder hoặc suy luận của mình — **nên tự kiểm tra lại** với docs AWS hiện tại.
> - ✍️ = ví dụ tự đặt (tên, giá trị, ID là placeholder).
> - ⚠️ = bẫy hay gặp.

---

## Mục lục

0. [Tóm tắt một trang](#0-tóm-tắt-một-trang)
1. [Cluster là gì, chứa gì](#1-cluster-là-gì-chứa-gì)
2. [Ba loại hạ tầng và cách chọn](#2-ba-loại-hạ-tầng-và-cách-chọn)
3. [Trạng thái của cluster](#3-trạng-thái-của-cluster)
4. [Capacity provider và capacity provider strategy](#4-capacity-provider-và-capacity-provider-strategy)
5. [Quy tắc của strategy: base, weight, giới hạn](#5-quy-tắc-của-strategy-base-weight-giới-hạn)
6. [Tuỳ chọn cấp cluster](#6-tuỳ-chọn-cấp-cluster)
7. [Cluster Managed Instances: khái niệm](#7-cluster-managed-instances-khái-niệm)
8. [Tạo cluster Managed Instances](#8-tạo-cluster-managed-instances)
9. [Cập nhật cluster sang Managed Instances](#9-cập-nhật-cluster-sang-managed-instances)
10. [Capacity provider Managed Instances: tạo và cấu hình](#10-capacity-provider-managed-instances-tạo-và-cấu-hình)
11. [Giám sát và tag của capacity provider Managed Instances](#11-giám-sát-và-tag-của-capacity-provider-managed-instances)
12. [Cập nhật capacity provider: coi là bất biến](#12-cập-nhật-capacity-provider-coi-là-bất-biến)
13. [Xoá capacity provider Managed Instances](#13-xoá-capacity-provider-managed-instances)
14. [Task scale-in protection](#14-task-scale-in-protection)
15. [Endpoint task protection của agent](#15-endpoint-task-protection-của-agent)
16. [Cluster Fargate và Fargate Spot](#16-cluster-fargate-và-fargate-spot)
17. [Tạo cluster Fargate](#17-tạo-cluster-fargate)
18. [Cluster EC2: ASG capacity provider](#18-cluster-ec2-asg-capacity-provider)
19. [Tạo cluster EC2](#19-tạo-cluster-ec2)
20. [Bảo mật container instance EC2](#20-bảo-mật-container-instance-ec2)
21. [So sánh tạo cluster ba loại](#21-so-sánh-tạo-cluster-ba-loại)
22. [Triển khai từ đầu đến cuối (CLI)](#22-triển-khai-từ-đầu-đến-cuối-cli)
23. [Troubleshooting](#23-troubleshooting)
24. [Chỗ docs không khớp nhau](#24-chỗ-docs-không-khớp-nhau)
25. [Checklist](#25-checklist)
26. [Câu hỏi tự kiểm tra (40 câu)](#26-câu-hỏi-tự-kiểm-tra-40-câu)
27. [Bản đồ nguồn](#27-bản-đồ-nguồn)

---

## 0. Tóm tắt một trang

```
┌──────────────────────────── Cluster (gắn với 1 Region) ────────────────────────────┐
│  Capacity providers (một hoặc nhiều)                                                │
│   ├─ Managed Instances: default (ECS tự tạo) / custom (instanceRequirements)       │
│   ├─ FARGATE, FARGATE_SPOT (có sẵn, chỉ cần gắn)                                   │
│   └─ Auto Scaling group (EC2, bạn tự quản lý)                                      │
│  Default capacity provider strategy  ← chỉ dùng khi task/service không chỉ định gì │
│  Mạng: VPC + subnet (AZ, Local Zone, Wavelength, Outposts cho MI / EC2)            │
│  Namespace Service Connect mặc định (tuỳ chọn)                                     │
│  Container Insights (tính phí) · task events · ECS Exec · KMS · tag               │
│  Services / tasks                                                                  │
└────────────────────────────────────────────────────────────────────────────────────┘
```

| Chủ đề | Ý phải nhớ |
|---|---|
| Cluster | **Nhóm logic** task/service + capacity hạ tầng; gắn **một Region**; tách biệt tài nguyên |
| Loại hạ tầng | **Managed Instances** (docs khuyến nghị cho đa số workload mới) · **Fargate** (serverless) · **EC2** (toàn quyền) |
| Trộn | Cluster chứa lẫn cả ba loại; **một strategy chỉ một loại** |
| Strategy | ≤ **20** provider; chỉ **một** provider có `base` (mặc định 0); `weight` mặc định console **1**, API/CLI **0**; tất cả weight 0 → lỗi |
| Default strategy | Chỉ áp khi task/service **không** chỉ định launch type hay strategy |
| EC2 launch type | ECS **không** theo dõi, **không** scale ASG |
| Managed Instances | Default CP (ECS tự tạo, rẻ nhất) hoặc custom CP (`instanceRequirements`); cần **infrastructure role** + **instance profile** + `iam:CreateServiceLinkedRole` |
| Cập nhật CP | Coi là **bất biến**: tạo mới → chuyển từng service → xoá cũ. Sửa tại chỗ chỉ áp cho instance mới, **không** kích hoạt deployment |
| Giám sát MI | Basic (mặc định, miễn phí, 5 phút) / detailed (1 phút); đổi được không cần tạo lại |
| Tag MI | `propagateTags = CAPACITY_PROVIDER` khi tạo; tag thêm sau **không** áp cho tài nguyên cũ |
| Xoá CP | Gỡ khỏi strategy của mọi service (force new deployment) trước → `INACTIVE` |
| Task protection | `ProtectionEnabled`, mặc định **2 giờ**, tối đa **48 giờ**; chỉ task của **service**; khuyến nghị gọi **endpoint agent** |
| Fargate Spot | Cảnh báo **2 phút** (EventBridge + SIGTERM), `stopCode = SpotInterruption`; **không** tự sang on-demand |
| EC2 ASG | ASG **mới, rỗng**; `MaxSize > 0`; không instance weighting; managed scaling, managed draining |
| Bảo mật EC2 | Không admin; task role tối thiểu; `ECS_AWSVPC_BLOCK_IMDS=true`; GuardDuty Runtime Monitoring |

---

## 1. Cluster là gì, chứa gì

> Nguồn: `14-00` (mục 0, 2, 3).

- **ECS cluster** là một **nhóm logic** các task hoặc service, cung cấp **capacity hạ tầng** cho ứng dụng container.
- Tạo cluster để **tách biệt** tài nguyên; cluster **gắn với một Region**.
- Ngoài capacity, cluster gồm:

| Thành phần | Nội dung |
|---|---|
| **Mạng** (VPC, subnet) | Với Managed Instances hoặc EC2, subnet có thể ở **AZ, Local Zone, Wavelength Zone, AWS Outposts** |
| **Namespace** (tuỳ chọn) | Cho Service Connect; đặt namespace mặc định thì service **mới** chỉ cần bật Service Connect là thành client của namespace |
| **Giám sát** | **CloudWatch Container Insights** — **tính phí thêm**; tự thu thập, tổng hợp, tóm tắt metric và log ECS |

Các khái niệm khác của trang:
- Một cluster chứa **lẫn** task chạy trên Managed Instances, Fargate, EC2, external instance (theo launch type hoặc strategy).
- Instance type khác nhau dùng được cho capacity provider Managed Instances và ASG; **một instance chỉ đăng ký vào một cluster** tại một thời điểm.
- Giới hạn truy cập cluster bằng **IAM policy tuỳ chỉnh**.
- Scale task Fargate bằng **Service Auto Scaling**.

🔎 Cách chia cluster hay gặp (`14-99`): theo **môi trường** (dev / staging / prod), team, hoặc mức độ quan trọng, kèm IAM giới hạn ai thao tác được cluster nào.

---

## 2. Ba loại hạ tầng và cách chọn

> Nguồn: `14-00` (mục 1, 4).

| | **Managed Instances** ⭐ | **Fargate** | **EC2 instances** |
|---|---|---|---|
| Docs mô tả | Phù hợp đa số workload; AWS quản lý EC2: cấp phát, vá, scale | Serverless, trả theo tài nguyên task dùng | Toàn quyền: tự chọn instance, cấu hình, bảo trì |
| Dùng khi | Muốn AWS lo hạ tầng; compute tiết kiệm có tối ưu tự động; hiệu năng ổn định, scale linh hoạt | Muốn hoàn toàn serverless; workload khó đoán; giảm tối đa vận hành; deploy và scale nhanh | Cần instance type / cấu hình cụ thể; tận dụng EC2 sẵn có; **custom AMI**, phần mềm đặc thù; kiểm soát tối đa |
| Capacity provider | Default hoặc custom | `FARGATE`, `FARGATE_SPOT` định sẵn | Auto Scaling group |
| Bạn chuẩn bị | Infrastructure role + instance profile | Không | Launch template + ASG + instance role |

> **Note (docs):** Managed Instances là lựa chọn **khuyến nghị** cho đa số workload **mới**.

Lựa chọn chi tiết từng loại có ở các toàn tập: `06-98` (Managed Instances), `08-98` (Fargate), `09-98` (EC2).

---

## 3. Trạng thái của cluster

> Nguồn: `14-00` (mục 3), `15-04-07-01-02` (namespace).

| Trạng thái | Ý nghĩa |
|---|---|
| **`ACTIVE`** | Sẵn sàng nhận task; đăng ký được container instance (nếu áp dụng) |
| **`PROVISIONING`** | Tài nguyên cần cho capacity provider **đang được tạo** |
| **`DEPROVISIONING`** | Tài nguyên cho capacity provider **đang được xoá** |
| **`FAILED`** | Tạo tài nguyên cho capacity provider **thất bại** |
| **`INACTIVE`** | Cluster **đã bị xoá**; có thể còn thấy một thời gian — ⚠️ **đừng dựa vào** việc này |

```
create (có capacity provider / namespace) ─► PROVISIONING ─► ACTIVE
                                                  └────────► FAILED
delete ─► DEPROVISIONING ─► INACTIVE
```

🔎 Ghép từ folder 15: tạo cluster kèm **namespace Service Connect mặc định** cũng làm cluster chờ ở `PROVISIONING` trong lúc ECS tạo namespace; xem attachment bằng `describe-clusters --include ATTACHMENTS`.

---

## 4. Capacity provider và capacity provider strategy

> Nguồn: `14-00` (mục 4), `14-01-00` (mục 1).

- **Capacity provider** quản lý việc **scale hạ tầng** cho task trong cluster.
- Mỗi cluster có **một hoặc nhiều** capacity provider và một **strategy mặc định** (tuỳ chọn).
- **Strategy** quyết định task được **phân bổ** qua các capacity provider thế nào.
- Chạy standalone task hoặc tạo service: dùng strategy **mặc định** của cluster, hoặc strategy **ghi đè**.

⚠️ Strategy mặc định **chỉ áp dụng** khi **không** chỉ định **launch type** hay **capacity provider strategy**. Chỉ định một trong hai → không dùng mặc định.

| Loại capacity provider | Docs mô tả |
|---|---|
| **Managed Instances** | AWS quản lý hoàn toàn EC2: cấp phát, vá, scale, vòng đời; tự tối ưu chọn instance |
| **Fargate** | Serverless; chỉ cần **gắn** `FARGATE` và `FARGATE_SPOT` (định sẵn) vào cluster |
| **Auto Scaling group** | EC2 instance; ASG quản lý số instance theo tải; bạn **toàn quyền** kiểm soát |

⚠️ Dùng EC2 dưới dạng **launch type** (không qua capacity provider) → ECS **không theo dõi và không scale** ASG.

```
Service / RunTask
   ├─ chỉ định capacityProviderStrategy → dùng strategy đó
   ├─ chỉ định launchType              → dùng launch type (không qua capacity provider)
   └─ không chỉ định gì                 → defaultCapacityProviderStrategy của cluster
```

---

## 5. Quy tắc của strategy: base, weight, giới hạn

> Nguồn: `14-02-00` (mục 2), `14-03-00` (mục 5), `14-01-03-00` (mục 3).

| # | Quy tắc |
|---|---|
| 1 | Phải **gắn** capacity provider vào cluster **trước** khi đưa vào strategy |
| 2 | Tối đa **20** capacity provider trong một strategy |
| 3 | Một strategy chỉ chứa **một loại**: Managed Instances **hoặc** ASG **hoặc** Fargate |
| 4 | `weight` không khai báo: **console = 1**, **API/CLI = 0** |
| 5 | Ít nhất một provider có weight **> 0**; provider weight 0 không được dùng đặt task; **tất cả 0** → `RunTask` / `CreateService` **thất bại** |
| 6 | Chỉ **một** provider có `base`; mặc định **0** |
| 7 | Cluster chứa lẫn service / standalone task dùng launch type và strategy; chuyển service từ launch type sang strategy phải **force new deployment** |
| 8 | `14-02-00`, `14-03-00`: **không** cập nhật service từ ASG sang Fargate và ngược lại (⚠️ xem mục 24) |

**Cách tính:** `base` đặt trước số task tối thiểu trên provider đó; phần còn lại chia theo tỉ lệ `weight`.

✍️ 10 task, strategy `FARGATE base=2 weight=1` + `FARGATE_SPOT weight=3`:

```
base:           2 task → FARGATE
còn 8 task:     chia 1 : 3 → 2 FARGATE + 6 FARGATE_SPOT
tổng:           4 FARGATE + 6 FARGATE_SPOT
```

✍️ Đặt strategy mặc định cho cluster:

```bash
aws ecs put-cluster-capacity-providers --cluster prod \
  --capacity-providers FARGATE FARGATE_SPOT \
  --default-capacity-provider-strategy capacityProvider=FARGATE,base=2,weight=1 capacityProvider=FARGATE_SPOT,weight=3
```

⚠️ Luôn ghi `weight` rõ khi dùng CLI/API — mặc định 0 rất dễ khiến mọi provider đều 0.

---

## 6. Tuỳ chọn cấp cluster

> Nguồn: `14-00`, `14-01-01`, `14-02-01`, `14-03-02`.

| Tuỳ chọn | Nội dung | Ghi chú |
|---|---|---|
| **Cluster name** | Duy nhất, ≤ **255** ký tự (chữ hoa/thường, số, gạch nối) | |
| **Namespace Service Connect mặc định** | Console Managed Instances tạo namespace Cloud Map **trùng tên cluster**; đổi tên khác hoặc dùng **ARN** namespace chia sẻ | Service mới bật Service Connect là thành client |
| **Container Insights** | **With enhanced observability** (được khuyến nghị) hoặc thường | ⚠️ **Tính phí**; bật/tắt ảnh hưởng **toàn cluster** |
| **Task events** | Gửi task state change event tới **EventBridge** | Có ở console Fargate và EC2 |
| **ECS Exec** | KMS key mã hoá phiên; logging **CloudWatch / S3 / None** | |
| **Encryption** | KMS cho **managed storage**; KMS cho **Fargate ephemeral storage** | |
| **GuardDuty** | Chế độ thủ công: tag `guardDutyRuntimeMonitoringManaged = true` | Trang EC2 |
| **Tag** | Nhận diện cluster | |

✍️ CLI bật Container Insights (docs `14-01-01` dùng account setting):

```bash
aws ecs put-account-setting --name containerInsights --value enhanced
# 🔎 Bật cho riêng một cluster:
aws ecs update-cluster-settings --cluster prod --settings name=containerInsights,value=enhanced
```

---

## 7. Cluster Managed Instances: khái niệm

> Nguồn: `14-01-00`, `14-01-03-00`.

### 7.1. Hai loại capacity provider

| Loại | Chọn instance theo | Ai tạo |
|---|---|---|
| **Default** | Instance **tiết kiệm nhất** đáp ứng task definition (`operatingSystemFamily`, `cpuArchitecture`, `cpu`, `memory`) và service (`placementConstraints`, `placementStrategy`) | ECS tự tạo khi tạo cluster Managed Instances |
| **Custom** | Thuộc tính và instance type bạn khai báo (`instanceRequirements`) | Bạn tạo (console tạo cùng cluster, hoặc `create-capacity-provider`) |

ECS **launch instance** và gắn chúng vào capacity provider.

### 7.2. Capacity provider Managed Instances làm gì

- Dùng toàn bộ năng lực EC2, còn **AWS gánh vận hành và bảo mật**: vá OS và phần mềm, scale instance, bảo trì — lợi ích vận hành giống Fargate.
- ECS **tạo launch template** gồm: instance profile, cấu hình **mạng** và **lưu trữ**, **capacity option**, **instance requirements**.

### 7.3. Khi nào cần custom capacity provider

| Nhu cầu | Ví dụ theo docs |
|---|---|
| Compute cụ thể | Tăng tốc phần cứng, tập lệnh CPU, mạng hiệu năng cao, memory lớn |
| GPU | ML inference, render, mã hoá video — GPU NVIDIA / AMD |
| Capacity reservation | Workload trọng yếu cần capacity dự đoán được |
| Observability nâng cao | Công cụ cần quyền đặc quyền vào OS (eBPF, phân tích mạng) |
| Tối ưu chi phí | Nhiều task trên một instance, dùng chung hạ tầng, tận dụng instance lớn |

### 7.4. IAM bắt buộc

| Role / quyền | Vai trò |
|---|---|
| **Infrastructure role** | ECS gọi dịch vụ AWS thay bạn để quản lý hạ tầng Managed Instances |
| **Instance profile** | Quyền cho ECS container agent và Docker daemon trên managed instance |
| **`iam:CreateServiceLinkedRole`** | Người tạo cluster cần thêm quyền này |

---

## 8. Tạo cluster Managed Instances

> Nguồn: `14-01-01`.

### 8.1. Console

1. Console ECS → chọn **Region** → **Clusters** → **Create cluster**.
2. **Cluster configuration**: tên; (tuỳ chọn) namespace khác tên cluster.
3. **Custom Capacity Provider** → *Select a method of obtaining EC2 capacity* = **Amazon ECS Managed Instances**:
   - **Instance profile**, **Infrastructure role**.
   - Muốn custom: **Instance selection → Use custom**, nhập **Attribute value**.
4. (Tuỳ chọn) **Monitoring**: Container Insights with enhanced observability (khuyến nghị) / Container Insights.
5. (Tuỳ chọn) **Encryption → Managed storage**: ARN KMS key.
6. (Tuỳ chọn) **Tags** → **Create**.

Console tự tạo: capacity provider **default** (instance tối ưu chi phí), namespace Cloud Map trùng tên cluster.

### 8.2. CLI

```bash
aws ecs create-cluster --cluster-name <CLUSTER_NAME>
```

Kết quả theo docs: `ACTIVE`, `capacityProviders` **rỗng**, `defaultCapacityProviderStrategy` **rỗng**, `containerInsights = disabled`, các bộ đếm = 0.

⚠️ Tạo bằng CLI **không** có capacity provider nào — phải tạo capacity provider Managed Instances và gắn vào cluster (mục 9.2, 10.2).

```bash
aws ecs put-account-setting --name containerInsights --value enhanced              # tuỳ chọn
aws ecs tag-resource --resource-arn <CLUSTER_ARN> --tags key=Environment,value=Production
```

Bước tiếp: tạo task definition cho Managed Instances, chạy standalone task hoặc service.

---

## 9. Cập nhật cluster sang Managed Instances

> Nguồn: `14-01-02`.

- Cluster **đang có** thêm được Managed Instances; ECS tự tạo capacity provider **default** (general-purpose tối ưu chi phí nhất).

### 9.1. Cân nhắc

| Chủ đề | Theo docs |
|---|---|
| Task đang chạy | **Không** bị ảnh hưởng; thay đổi áp cho task **mới** |
| Đổi capacity provider | Instance **hiện có chạy tiếp**; instance **mới** mới dùng cấu hình mới |
| Container Insights | Bật/tắt ảnh hưởng **toàn cluster** |

### 9.2. Các bước

Console: **Clusters → cluster → Update cluster →** sửa instance profile, infrastructure role, attribute value → **Update**.

CLI (2 bước theo docs):

```bash
# 1) Tạo capacity provider (docs ví dụ vCPU 2–8, memory 4096–16384 MiB)
aws ecs create-capacity-provider --cli-input-json file://mi-cp.json

# 2) Gắn vào cluster + strategy mặc định
aws ecs put-cluster-capacity-providers --cluster <CLUSTER_NAME> \
  --capacity-providers <CP_NAME> \
  --default-capacity-provider-strategy capacityProvider=<CP_NAME>,weight=1
```

⚠️ `put-cluster-capacity-providers` **thay toàn bộ** danh sách provider của cluster 🔎 — liệt kê đủ provider muốn giữ (vd `FARGATE FARGATE_SPOT <CP_NAME>`).

🔎 Sau khi có capacity provider, service hiện có chuyển sang bằng `update-service --capacity-provider-strategy … --force-new-deployment` (cách chuyển ở `05-01`).

---

## 10. Capacity provider Managed Instances: tạo và cấu hình

> Nguồn: `14-01-03-02`.

- **Không** có `instanceRequirements` → ECS chọn instance **general-purpose** tối ưu chi phí nhất.
- **Có** `instanceRequirements` → chọn theo **thuộc tính** (attribute-based): yêu cầu được tự chuyển thành **mọi** EC2 instance type khớp.

### 10.1. Console

**Clusters → cluster → tab Infrastructure → Capacity providers → Create capacity provider**:

| Bước | Nội dung |
|---|---|
| Configuration | Tên duy nhất; type **Amazon ECS Managed Instances** |
| Instance configuration | Instance profile, infrastructure role |
| Instance requirements | Kết hợp tuỳ ý (bảng dưới) |
| Advanced configuration | Monitoring **Basic** (status check) / **Detailed** (mọi metric) |
| Tags | *Propagate tags from → Capacity provider* |

| Thuộc tính | Ví dụ docs |
|---|---|
| vCPU count | `4`, `8-16` |
| Memory (MiB) | `8192`, `16384-32768` |
| Instance types | `m5.large,m5.xlarge,c5.large` |
| CPU manufacturers | `intel`, `amd`, `amazon-web-services` |
| Accelerator types | `gpu`, `fpga`, `inference` |
| Accelerator count | `1`, `2-4` |

### 10.2. CLI

```bash
aws ecs create-capacity-provider --cli-input-json file://capacity-provider-definition.json
aws ecs describe-capacity-providers --capacity-providers <CP_NAME>
```

```json
{
  "name": "<CP_NAME>",
  "cluster": "<CLUSTER_NAME>",
  "tags": [{ "key": "version", "value": "test" }],
  "managedInstancesProvider": {
    "infrastructureRoleArn": "<INFRA_ROLE_ARN>",
    "instanceLaunchTemplate": {
      "ec2InstanceProfileArn": "<INSTANCE_PROFILE_ARN>",
      "instanceRequirements": {
        "vCpuCount": { "min": 4, "max": 8 },
        "memoryMiB": { "min": 8192, "max": 16384 }
      },
      "networkConfiguration": { "subnets": ["<SUBNET_1>", "<SUBNET_2>"], "securityGroups": ["<SG_ID>"] },
      "storageConfiguration": { "storageSizeGiB": 100 },
      "monitoring": "basic"
    },
    "propagateTags": "CAPACITY_PROVIDER"
  }
}
```

| Trường | Ý nghĩa |
|---|---|
| `infrastructureRoleArn` | Role ECS dùng quản lý EC2 |
| `instanceLaunchTemplate.ec2InstanceProfileArn` | Instance profile cho agent / Docker |
| `instanceRequirements` | Dải vCPU, memory… (thiếu → chọn general-purpose rẻ nhất) |
| `networkConfiguration` | Subnet, security group của instance |
| `storageConfiguration.storageSizeGiB` | Dung lượng đĩa (ví dụ docs 100) |
| `monitoring` | `basic` / detailed |
| `propagateTags` | `CAPACITY_PROVIDER` để lan tag |

Lời khuyên chọn instance (theo thuộc tính cho đa số, type cụ thể chỉ khi cần phần cứng đặc thù, Capacity Reservation thì `minimumHealthyPercent` < 100): xem `06-02` / `06-98`.

---

## 11. Giám sát và tag của capacity provider Managed Instances

> Nguồn: `14-01-03-00` (mục 2, 4), `14-01-03-03`.

### 11.1. Mức giám sát

| Mức | Nội dung | Chi phí |
|---|---|---|
| **Basic** (mặc định) | Metric thiết yếu, **5 phút**; status check **1 phút** | Không tính thêm |
| **Detailed** | **Mọi** metric, **1 phút** | (trang không nói) 🔎 theo giá EC2 detailed monitoring |

Đổi được **không cần tạo lại** capacity provider:

```bash
aws ecs update-capacity-provider --name <CP_NAME> \
  --managed-instances-provider '{ "instanceLaunchTemplateUpdate": { "monitoring": "DETAILED" } }'
aws ecs update-capacity-provider --name <CP_NAME> \
  --managed-instances-provider '{ "instanceLaunchTemplateUpdate": { "monitoring": "BASIC" } }'
```

Console: **Clusters → cluster → Infrastructure → Advanced configuration → Basic / Detailed → Update**.

### 11.2. Lan truyền tag

- `propagateTags = CAPACITY_PROVIDER` (khai báo khi **tạo**) → tag lan sang **managed instance, ECS container instance, launch template, volume, ENI**.
- Sửa tag trên console: **Infrastructure → capacity provider → Tags → Manage tags**; tối đa **50** tag.

> **Note (docs):** Tag **thêm hoặc xoá sau khi** tạo capacity provider **không áp dụng** cho tài nguyên đã tạo trước đó.

🔎 Hệ quả: lên kế hoạch tag phục vụ báo cáo chi phí (cost allocation) **ngay lúc tạo**; muốn tag mới áp cho mọi instance → tạo capacity provider mới (mục 12).

---

## 12. Cập nhật capacity provider: coi là bất biến

> Nguồn: `14-01-03-01`.

```
CP-v1 (đang dùng)
   │ 1. Tạo CP-v2 với cấu hình mới (KHÔNG sửa CP-v1)
   ▼
   2. Chuyển TỪNG service sang CP-v2, chờ deployment xong, theo dõi
        └─ có lỗi → đưa service về CP-v1
   ▼
   3. Không còn ai dùng CP-v1 → xoá CP-v1
```

| Lợi ích | Nội dung |
|---|---|
| Rollout có kiểm soát | Từng service một |
| Rollback dễ | Đưa service về CP cũ |
| Giảm blast radius | Lỗi cấu hình mới không lập tức ảnh hưởng mọi workload |

**Vì sao không sửa tại chỗ:** áp cho **capacity mới** từ nay, nhưng **không** kích hoạt deployment → lỗi cấu hình có thể chỉ lộ ra **rất lâu sau**, đúng lúc service cần **scale**.

> **Note (docs):** Dùng CloudFormation → **giữ CP cũ tới lần deploy sau** để còn rollback stack.

Ngoại lệ an toàn khi sửa tại chỗ: mức giám sát (mục 11).

✍️ Chuyển một service:

```bash
aws ecs update-service --cluster prod --service api \
  --capacity-provider-strategy capacityProvider=mi-cp-v2,weight=1 \
  --force-new-deployment
aws ecs wait services-stable --cluster prod --services api
```

---

## 13. Xoá capacity provider Managed Instances

> Nguồn: `14-01-03-04`, `14-99` (mục 3).

1. **Gỡ** capacity provider khỏi strategy của **mọi service** (`UpdateService` hoặc console), dùng **Force new deployment** để task chuyển sang provider còn lại.
2. Console: **Clusters → cluster → Infrastructure →** chọn CP → **Delete** → gõ `delete <tên>` → **Delete**.
3. Sau khi xoá: trạng thái **`INACTIVE`**, có thể còn thấy một thời gian — ⚠️ đừng dựa vào.

🔎 Checklist "ai còn dùng CP này" (`14-99`): strategy của mọi service, **default strategy** của cluster, standalone task đang chạy, **EventBridge Scheduler** gọi RunTask, **Managed Daemons** (folder 07).

---

## 14. Task scale-in protection

> Nguồn: `14-01-04-00`.

### 14.1. Mục đích

Bảo vệ task khỏi bị **dừng** bởi **scale-in** của service auto scaling hoặc bởi **deployment**. Ví dụ docs:
- Xử lý hàng đợi bất đồng bộ (chuyển mã video) — task chạy hàng giờ.
- Game server — chạy tiếp sau khi người chơi thoát để tránh độ trễ khởi động lại.
- Deploy code mới khi **xử lý lại rất tốn kém**.

### 14.2. Thuộc tính

| Thuộc tính | Giá trị |
|---|---|
| `ProtectionEnabled` | `true` bật (CLI `--protection-enabled`), `false` tắt (`--no-protection-enabled`) |
| Thời hạn mặc định | **2 giờ** |
| `ExpiresInMinutes` | **1 → 2880** phút (48 giờ); hết hạn → tự về `false` |

### 14.3. Hai cơ chế

| Cơ chế | Hợp với | Cách |
|---|---|---|
| **Endpoint agent** ⭐ (khuyến nghị — có retry, giao diện đơn giản) | Task tự biết lúc cần bảo vệ (queue, job) | Trong container gọi `$ECS_AGENT_URI/task-protection/v1/state` |
| **ECS API** | Có controller bên ngoài theo dõi trạng thái (game server: người chơi vào → bật, ra → tắt) | `UpdateTaskProtection`, `GetTaskProtection` |

Kết hợp được: bật từ trong container, gỡ từ controller ngoài.

### 14.4. Cân nhắc

- Chỉ task **của service**; hỗ trợ task service trên **Managed Instances** (trang này).
- Gọi lại `UpdateTaskProtection` cho task đang bảo vệ → **đặt lại** hạn.
- Đặt `expiresInMinutes` vừa đủ — dài quá **tốn chi phí** và **chậm deploy**.

| Tình huống deploy | Theo docs |
|---|---|
| **Rolling update** | Task mới vẫn tạo; task cũ **không bị dừng** tới khi gỡ / hết hạn bảo vệ; chỉnh **maximum percent** để có chỗ cho task mới |
| **CloudFormation** | `update-stack` **timeout 3 giờ** → bảo vệ > 3 giờ có thể `UPDATE_FAILED` và rollback |
| **`DEPLOYMENT_BLOCKED`** | Service có **nhiều task được bảo vệ hơn desired count** → chờ hết hạn, gỡ bảo vệ task có thể dừng, hoặc tăng desired count |

### 14.5. IAM

Task role cần `ecs:GetTaskProtection` và `ecs:UpdateTaskProtection`.

✍️ Dùng ECS API:

```bash
aws ecs update-task-protection --cluster prod --tasks <TASK_ARN> \
  --protection-enabled --expires-in-minutes 60
aws ecs get-task-protection --cluster prod --tasks <TASK_ARN>
```

---

## 15. Endpoint task protection của agent

> Nguồn: `14-01-04-01`.

- Agent tự chèn biến **`ECS_AGENT_URI`** vào container.
- **PUT** `$ECS_AGENT_URI/task-protection/v1/state` để đặt; **GET** để đọc.

| Request | Kiểu | Bắt buộc | Ý nghĩa |
|---|---|---|---|
| `ProtectionEnabled` | Boolean | Có | `true` bảo vệ / `false` gỡ |
| `ExpiresInMinutes` | Integer | Không | 1–2880; mặc định **120** |

```bash
curl --request PUT --header 'Content-Type: application/json' \
  "${ECS_AGENT_URI}/task-protection/v1/state" \
  --data '{"ProtectionEnabled":true,"ExpiresInMinutes":60}'
curl --request GET "${ECS_AGENT_URI}/task-protection/v1/state"
```

| Response | Trường |
|---|---|
| Thành công `protection` | `ExpirationDate` (null nếu không bảo vệ), `ProtectionEnabled`, `TaskArn` |
| Thất bại `failure` | `Arn`, `Detail`, `Reason` (vd `TASK_NOT_VALID`) |
| Lỗi `error` | `requestID`, `Arn`, `Code`, `Message` |

| `Code` | Khi nào |
|---|---|
| `AccessDeniedException` | Task role thiếu quyền |
| `RequestCanceled` | Agent không nhận được phản hồi ECS (mạng, sự cố control plane) |
| `ThrottlingException` | Bị giới hạn tốc độ ("Rate exceeded") |

> **Note (docs):** `RequestError` / `RequestTimeout` thường do **mạng** → cân nhắc **VPC endpoint cho Amazon ECS**.

✍️ Mẫu worker (giả mã):

```
loop:
  msg = sqs.receive()
  PUT protection {ProtectionEnabled:true, ExpiresInMinutes: ước lượng thời gian xử lý}
  process(msg)
  sqs.delete(msg)
  PUT protection {ProtectionEnabled:false}
```

---

## 16. Cluster Fargate và Fargate Spot

> Nguồn: `14-02-00`.

- Capacity provider Fargate cho task dùng cả **Fargate** và **Fargate Spot**.
- **Fargate Spot**: capacity dư, **rẻ hơn**, bị **gián đoạn** khi AWS cần lại, **cảnh báo trước 2 phút**.
- Task dừng → **task state change event** tới EventBridge kèm **stopped reason**.

### 16.1. Khi Spot thiếu hoặc bị thu hồi

```
Nhu cầu cực cao → không có capacity Spot → service THỬ LẠI tới khi có
                                         ⚠️ KHÔNG tự chuyển sang on-demand
Bị thu hồi → báo trước 2 phút:
   ├─ EventBridge: ECS Task State Change, stopCode = SpotInterruption
   └─ SIGTERM tới task
Service → cố launch task mới trên Spot nếu còn capacity
⚠️ Service chỉ 1 task → gián đoạn tới khi có capacity lại
```

### 16.2. Tắt êm

| Cấu hình | Nội dung |
|---|---|
| `stopTimeout` | ≤ **120 s**, mặc định **30 s** — đặt dài hơn để có thời gian dọn dẹp |
| Xử lý SIGTERM | Bắt buộc; không xử lý → SIGKILL sau `stopTimeout`, có thể **mất / hỏng dữ liệu** |

### 16.3. EventBridge rule

```json
{
  "source": ["aws.ecs"],
  "detail-type": ["ECS Task State Change"],
  "detail": { "clusterArn": ["<CLUSTER_ARN>"], "stopCode": ["SpotInterruption"] }
}
```

(Docs đưa pattern lọc `source`, `detail-type`, `clusterArn`; thêm `stopCode` 🔎 để chỉ bắt Spot interruption.)

🔎 Giảm rủi ro: đặt `base` trên `FARGATE` on-demand cho phần bắt buộc chạy (`14-99`, `15-99`).

---

## 17. Tạo cluster Fargate

> Nguồn: `14-02-01`.

- Console tạo tài nguyên qua **CloudFormation stack**; **tự gắn** `FARGATE` và `FARGATE_SPOT`.
- Các bước: tên + **Default namespace** (hoặc ARN namespace chia sẻ) → **Monitoring** → **Task events** → **ECS Exec** (KMS, logging CloudWatch / S3 / None) → **Encryption** (Fargate ephemeral storage, managed storage) → **Tags** → **Create**.

✍️ CLI tương đương:

```bash
aws ecs create-cluster --cluster-name app-fargate \
  --capacity-providers FARGATE FARGATE_SPOT \
  --default-capacity-provider-strategy capacityProvider=FARGATE,weight=1 \
  --settings name=containerInsights,value=enhanced \
  --service-connect-defaults namespace=app-fargate
```

🔎 Tên flag theo AWS CLI `ecs create-cluster`; kiểm tra lại bằng `aws ecs create-cluster help`.

---

## 18. Cluster EC2: ASG capacity provider

> Nguồn: `14-03-00`. Chi tiết vận hành EC2 ở `09-98`.

- Dùng **Auto Scaling group** để quản lý EC2 instance của cluster.
- **Managed scaling**: ECS scale-in / scale-out ASG (cluster auto scaling); hoặc tự quản lý.
- **Managed instance draining** (mặc định bật): drain task trước khi ASG terminate instance. ⚠️ Không liên quan sản phẩm *Managed Instances*.

| # | Quy tắc riêng cho ASG |
|---|---|
| 1 | Dùng **ASG mới, rỗng** (desired = 0); ASG có sẵn có thể có instance không gắn đúng CP → kiểm tra `DescribeContainerInstances` |
| 2 | `MaxSize` **> 0** |
| 3 | **Không** instance weighting |
| 4 | ASG không scale đủ → task **kẹt `PROVISIONING`** |
| 5 | **Không sửa** scaling policy do capacity provider quản lý |
| 6 | Bật managed scaling → desired count ASG đặt **0** |
| 7 | Hỗ trợ **warm pool** |

Console tạo **launch template + ASG** qua CloudFormation (tiền tố tên ở mục 19, 24).

---

## 19. Tạo cluster EC2

> Nguồn: `14-03-02`.

- ASG **bắt buộc**. ASG mới do console tạo: ECS quản lý scale; ⚠️ **không ngăn** instance đang chứa task bị terminate khi scale-in (không bật scale-in protection).
- Launch template tiền tố `EC2ContainerService-<ClusterName>`; ASG tiền tố `<ClusterName>-ECS-Infra-ECSAutoScalingGroup`.

| Bước console | Nội dung |
|---|---|
| Infrastructure | **Fargate and Self-managed instances** |
| ASG mới | On-demand / Spot (+ allocation strategy, đa số: **Price capacity optimized**), AMI ECS-optimized, instance type (giống / tương tự nhau để managed scaling tốt), instance role, min / max, SSH key, root EBS size |
| Networking | VPC, subnet, SG (ít nhất 1 inbound rule: Custom / Anywhere / Source group), auto-assign public IP; IPv6-only: subnet chỉ IPv6 |
| Monitoring, task events, ECS Exec | Như Fargate |
| GuardDuty (thủ công) | Tag `guardDutyRuntimeMonitoringManaged = true` |
| Encryption, tags | Managed storage KMS; tag |

⚠️ Không mở **Anywhere** cho port quản trị (SSH); 🔎 ưu tiên Session Manager / ECS Exec.

---

## 20. Bảo mật container instance EC2

> Nguồn: `14-03-01`.

- Đưa **container instance** vào threat model: task bị xâm nhập có thể dùng quyền IAM của task khác trên cùng instance.

| # | Khuyến nghị AWS |
|---|---|
| 1 | Không dùng quyền **admin** khi chạy task |
| 2 | **Task role quyền tối thiểu** (agent tạo token với credential ID riêng) |
| 3 | Task `awsvpc`: **`ECS_AWSVPC_BLOCK_IMDS=true`** trong `/etc/ecs/ecs.config` + restart agent → chặn credential instance profile |
| 4 | **GuardDuty Runtime Monitoring** — quan sát file, tiến trình, kết nối mạng |

```bash
#!/bin/bash
cat <<'CFG' >> /etc/ecs/ecs.config
ECS_CLUSTER=<CLUSTER_NAME>
ECS_AWSVPC_BLOCK_IMDS=true
CFG
```

🔎 Task `bridge` / `host` không được biến này bảo vệ → IMDSv2 hop limit 1 hoặc iptables (chi tiết `09-98` mục 16).

---

## 21. So sánh tạo cluster ba loại

> Nguồn: `14-01-01`, `14-02-01`, `14-03-02`.

| | Managed Instances | Fargate | EC2 |
|---|---|---|---|
| Infrastructure trên console | Amazon ECS Managed Instances | Fargate | Fargate and Self-managed instances |
| Capacity provider có sẵn | Default (ECS tạo) | FARGATE, FARGATE_SPOT (tự gắn) | Không — tạo ASG + CP |
| Bạn chuẩn bị | Infrastructure role, instance profile, `iam:CreateServiceLinkedRole` | Không | Instance role, AMI, instance type, min/max, SSH key, EBS |
| Mạng | Subnet, SG trong capacity provider | Khai báo khi chạy task / service | Subnet, SG, public IP cho instance |
| Namespace mặc định | Tự tạo trùng tên cluster | Tuỳ chọn | Tuỳ chọn |
| Task events, ECS Exec | (trang không nhắc) | Có | Có |
| KMS ephemeral storage Fargate | — | Có | Có |
| CloudFormation phía sau | (trang không nhắc) | Có | Có |
| GuardDuty tag | — | — | Có |

---

## 22. Triển khai từ đầu đến cuối (CLI)

> ✍️ Ghép từ lệnh trong docs; tên, ARN là placeholder. Mình **chưa chạy thử** — kiểm tra bằng `aws ecs <lệnh> help`.

### 22.1. Cluster hỗn hợp: Fargate + Managed Instances

```bash
# 1) Cluster + Fargate làm mặc định
aws ecs create-cluster --cluster-name prod \
  --capacity-providers FARGATE FARGATE_SPOT \
  --default-capacity-provider-strategy capacityProvider=FARGATE,base=2,weight=1 capacityProvider=FARGATE_SPOT,weight=3

# 2) Capacity provider Managed Instances (custom) — file mi-cp-v1.json như mục 10.2
aws ecs create-capacity-provider --cli-input-json file://mi-cp-v1.json
aws ecs describe-capacity-providers --capacity-providers mi-cp-v1

# 3) Gắn thêm vào cluster (liệt kê ĐỦ provider muốn giữ)
aws ecs put-cluster-capacity-providers --cluster prod \
  --capacity-providers FARGATE FARGATE_SPOT mi-cp-v1 \
  --default-capacity-provider-strategy capacityProvider=FARGATE,base=2,weight=1 capacityProvider=FARGATE_SPOT,weight=3

# 4) Service chạy trên Managed Instances
aws ecs create-service --cluster prod --service-name api --task-definition api \
  --desired-count 3 \
  --capacity-provider-strategy capacityProvider=mi-cp-v1,weight=1 \
  --network-configuration 'awsvpcConfiguration={subnets=[<SUBNET_A>,<SUBNET_B>],securityGroups=[<SG>]}'
```

### 22.2. Đổi cấu hình capacity provider (bất biến)

```bash
aws ecs create-capacity-provider --cli-input-json file://mi-cp-v2.json
aws ecs put-cluster-capacity-providers --cluster prod \
  --capacity-providers FARGATE FARGATE_SPOT mi-cp-v1 mi-cp-v2 \
  --default-capacity-provider-strategy capacityProvider=FARGATE,base=2,weight=1 capacityProvider=FARGATE_SPOT,weight=3
aws ecs update-service --cluster prod --service api \
  --capacity-provider-strategy capacityProvider=mi-cp-v2,weight=1 --force-new-deployment
aws ecs wait services-stable --cluster prod --services api
# … lần lượt từng service; khi không còn ai dùng mi-cp-v1:
aws ecs put-cluster-capacity-providers --cluster prod \
  --capacity-providers FARGATE FARGATE_SPOT mi-cp-v2 \
  --default-capacity-provider-strategy capacityProvider=FARGATE,base=2,weight=1 capacityProvider=FARGATE_SPOT,weight=3
aws ecs delete-capacity-provider --capacity-provider mi-cp-v1
```

### 22.3. Cảnh báo Spot interruption

```bash
aws events put-rule --name prod-spot-interruption \
  --event-pattern '{"source":["aws.ecs"],"detail-type":["ECS Task State Change"],"detail":{"clusterArn":["<CLUSTER_ARN>"],"stopCode":["SpotInterruption"]}}'
aws events put-targets --rule prod-spot-interruption --targets Id=sns,Arn=<SNS_TOPIC_ARN>
```

### 22.4. Kiểm tra

```bash
aws ecs describe-clusters --clusters prod --include ATTACHMENTS SETTINGS STATISTICS \
  --query 'clusters[0].{status:status,cps:capacityProviders,default:defaultCapacityProviderStrategy}'
aws ecs list-container-instances --cluster prod
```

---

## 23. Troubleshooting

| Triệu chứng | Nguyên nhân | Xử lý |
|---|---|---|
| `RunTask` / `CreateService` lỗi với strategy | Mọi weight = 0 (API mặc định 0); provider chưa gắn cluster; trộn loại provider | Ghi weight > 0; `put-cluster-capacity-providers` trước; một loại / strategy (`14-02-00`) |
| Service không dùng default strategy | Đã chỉ định launch type hoặc strategy | Bỏ chỉ định, hoặc sửa strategy của service (`14-00`) |
| Task kẹt `PROVISIONING` (EC2) | ASG không scale đủ, `MaxSize` = 0 hoặc chạm trần | Tăng `MaxSize`, kiểm tra ASG (`14-03-00`) |
| ASG không scale | Chạy bằng EC2 **launch type** | Dùng ASG capacity provider (`14-00`) |
| Cluster kẹt `PROVISIONING` / `FAILED` | Tạo tài nguyên capacity provider / namespace lỗi | `describe-clusters --include ATTACHMENTS` |
| Đổi capacity provider nhưng instance cũ vẫn cấu hình cũ | Sửa tại chỗ chỉ áp instance mới | Tạo CP mới, chuyển service (`14-01-03-01`) |
| Tag mới không xuất hiện trên instance | Tag thêm sau khi tạo không lan sang tài nguyên cũ | Khai báo tag lúc tạo / CP mới (`14-01-03-03`) |
| Không xoá được capacity provider | Còn service dùng | Gỡ khỏi strategy + force new deployment (`14-01-03-04`) |
| Deploy kẹt, `DEPLOYMENT_BLOCKED` | Quá nhiều task được bảo vệ | Chờ hết hạn, gỡ bảo vệ, tăng desired count (`14-01-04-00`) |
| CloudFormation `UPDATE_FAILED` sau 3 giờ | Task protection > 3 giờ | Hạn bảo vệ ngắn hơn (`14-01-04-00`) |
| Endpoint protection `AccessDeniedException` | Task role thiếu `ecs:*TaskProtection` | Thêm quyền (`14-01-04-01`) |
| Endpoint `RequestCanceled` / timeout | Mạng tới ECS | VPC endpoint ECS (`14-01-04-01`) |
| Service Spot không có task | Hết capacity Spot, không tự sang on-demand | `base` on-demand (`14-02-00`) |
| Mất dữ liệu khi Spot bị thu hồi | Không xử lý SIGTERM, `stopTimeout` ngắn | Bắt SIGTERM, `stopTimeout` ≤ 120 s (`14-02-00`) |
| Container đọc được credential instance (EC2) | IMDS chưa chặn | `ECS_AWSVPC_BLOCK_IMDS=true` (`14-03-01`) |
| Không tạo được cluster Managed Instances | Thiếu `iam:CreateServiceLinkedRole`, infrastructure role, instance profile | Bổ sung IAM (`14-01-01`) |

---

## 24. Chỗ docs không khớp nhau

| Chủ đề | Chỗ A | Chỗ B | Cách xử lý |
|---|---|---|---|
| Đổi service ASG ↔ Fargate | `14-02-00`, `14-03-00`: **không** được | `05-01` (service mutability): ASG ↔ Fargate **được** | 🔎 `05-01` mới hơn; thử trên môi trường test |
| Tiền tố tên ASG console tạo | `14-03-02`: `<ClusterName>-ECS-Infra-ECSAutoScalingGroup` | `14-03-00`: `EC2ContainerService-<ClusterName>` | Tìm theo cả hai / xem CloudFormation stack |
| ASG có sẵn | `14-03-02`: console cho chọn | `14-03-00`: khuyến nghị ASG mới, rỗng | Theo khuyến nghị |
| Tạo cluster MI bằng CLI | `create-cluster` ra `capacityProviders` rỗng | Console tự tạo CP default | CLI phải tự tạo + gắn CP (mục 9.2) |
| Container Insights qua CLI | `14-01-01` dùng `put-account-setting` (mức **account**) | Console bật theo **cluster** | 🔎 Dùng `update-cluster-settings` cho riêng một cluster |
| Giá trị `monitoring` | Tạo CP: `"basic"` (chữ thường) | Update: `"DETAILED"` / `"BASIC"` | Theo đúng ví dụ từng API |
| Chi phí detailed monitoring | Không nêu | — | 🔎 Kiểm tra giá CloudWatch / EC2 |
| Loại capacity provider | `14-00`: Managed Instances, Fargate, ASG | `05-09-04`: EC2, Fargate, Fargate Spot | `05-09-04` viết trước khi có Managed Instances |
| Nguồn "Source group" | `14-03-02`: thêm IP máy bạn | (giống **My IP** của EC2) | Kiểm tra trên console |

---

## 25. Checklist

```
Thiết kế
[ ] Chia cluster theo môi trường / team / mức quan trọng; IAM giới hạn theo cluster
[ ] Loại hạ tầng mặc định cho workload mới (docs: Managed Instances)
[ ] Default strategy rõ ràng; base on-demand cho phần bắt buộc, Spot cho phần còn lại
[ ] Luôn ghi weight khi dùng CLI/API

Managed Instances
[ ] Infrastructure role, instance profile, iam:CreateServiceLinkedRole
[ ] Default CP hay custom (instanceRequirements theo thuộc tính)
[ ] Tag + propagateTags=CAPACITY_PROVIDER ngay khi tạo
[ ] Chọn basic / detailed monitoring
[ ] Quy trình đổi CP: tạo mới → chuyển từng service → xoá cũ; CloudFormation giữ CP cũ thêm 1 lần deploy

Fargate
[ ] Gắn FARGATE, FARGATE_SPOT; app bắt SIGTERM, stopTimeout ≤ 120 s
[ ] EventBridge rule cho SpotInterruption
[ ] Không để service quan trọng chỉ 1 task trên Spot

EC2
[ ] ASG mới, rỗng, MaxSize > 0, không instance weighting
[ ] Managed scaling + managed instance draining
[ ] ECS_AWSVPC_BLOCK_IMDS=true, task role tối thiểu, GuardDuty Runtime Monitoring

Task scale-in protection
[ ] Task role có ecs:GetTaskProtection, ecs:UpdateTaskProtection
[ ] Bật / tắt qua endpoint agent quanh mỗi job; ExpiresInMinutes vừa đủ (< 3 giờ nếu dùng CloudFormation)

Tuỳ chọn cluster
[ ] Container Insights ở cluster cần (có phí)
[ ] Task events, ECS Exec logging, KMS managed storage / ephemeral storage
[ ] Namespace Service Connect mặc định nếu dùng Service Connect
[ ] Tạo cluster bằng IaC, không tạo tay
```

---

## 26. Câu hỏi tự kiểm tra (40 câu)

**1. Cluster ECS là gì?**
<details><summary>Đáp án</summary>

Nhóm logic các task hoặc service, cung cấp capacity hạ tầng cho ứng dụng container; gắn với một Region; dùng để tách biệt tài nguyên.
</details>

**2. Ba loại hạ tầng của cluster và loại docs khuyến nghị?**
<details><summary>Đáp án</summary>

Managed Instances (khuyến nghị cho đa số workload mới), Fargate (serverless), EC2 instances (toàn quyền).
</details>

**3. Ngoài capacity, cluster gồm gì?**
<details><summary>Đáp án</summary>

Mạng (VPC, subnet — với MI/EC2 có thể ở AZ, Local Zone, Wavelength, Outposts), namespace Service Connect tuỳ chọn, tuỳ chọn giám sát Container Insights (tính phí).
</details>

**4. Năm trạng thái của cluster?**
<details><summary>Đáp án</summary>

ACTIVE, PROVISIONING, DEPROVISIONING, FAILED, INACTIVE.
</details>

**5. Có nên dựa vào việc cluster INACTIVE còn thấy được không?**
<details><summary>Đáp án</summary>

Không; hành vi này có thể thay đổi.
</details>

**6. Một instance có thuộc nhiều cluster cùng lúc không?**
<details><summary>Đáp án</summary>

Không, một instance chỉ đăng ký vào một cluster tại một thời điểm.
</details>

**7. Cluster có chứa lẫn Fargate, Managed Instances và ASG được không? Strategy thì sao?**
<details><summary>Đáp án</summary>

Cluster chứa lẫn được; một strategy chỉ chứa một loại.
</details>

**8. Khi nào default strategy của cluster được dùng?**
<details><summary>Đáp án</summary>

Chỉ khi task/service không chỉ định launch type hay capacity provider strategy.
</details>

**9. Dùng EC2 launch type thì ECS có scale ASG không?**
<details><summary>Đáp án</summary>

Không; ECS không theo dõi và không scale capacity của ASG.
</details>

**10. Tối đa bao nhiêu capacity provider trong một strategy?**
<details><summary>Đáp án</summary>

20.
</details>

**11. Weight mặc định khi không khai báo?**
<details><summary>Đáp án</summary>

Console 1, API/CLI 0.
</details>

**12. Strategy mọi provider weight 0 thì sao?**
<details><summary>Đáp án</summary>

RunTask / CreateService thất bại.
</details>

**13. Bao nhiêu provider được có base?**
<details><summary>Đáp án</summary>

Chỉ một; mặc định base 0.
</details>

**14. 10 task, FARGATE base 2 weight 1, FARGATE_SPOT weight 3 → phân bố?**
<details><summary>Đáp án</summary>

2 base lên FARGATE; 8 còn lại chia 1:3 → 2 FARGATE + 6 Spot. Tổng 4 FARGATE, 6 Spot.
</details>

**15. Chuyển service từ launch type sang strategy cần gì?**
<details><summary>Đáp án</summary>

Force new deployment.
</details>

**16. Hai loại capacity provider Managed Instances?**
<details><summary>Đáp án</summary>

Default (instance rẻ nhất theo task definition và placement của service, ECS tự tạo) và custom (theo instanceRequirements bạn khai báo).
</details>

**17. Default capacity provider chọn instance dựa trên trường nào?**
<details><summary>Đáp án</summary>

Task definition: operatingSystemFamily, cpuArchitecture, cpu, memory; service: placementConstraints, placementStrategy.
</details>

**18. Hai IAM role cần cho Managed Instances và vai trò?**
<details><summary>Đáp án</summary>

Infrastructure role (ECS gọi AWS quản lý hạ tầng) và instance profile (quyền cho agent và Docker daemon). Người tạo cluster cần thêm iam:CreateServiceLinkedRole.
</details>

**19. Tạo cluster bằng `aws ecs create-cluster` có capacity provider Managed Instances chưa?**
<details><summary>Đáp án</summary>

Chưa — capacityProviders rỗng; phải create-capacity-provider rồi put-cluster-capacity-providers.
</details>

**20. Khi nào nên dùng custom capacity provider?**
<details><summary>Đáp án</summary>

Cần compute cụ thể, GPU, capacity reservation, observability đặc quyền (eBPF), hoặc tối ưu chi phí bằng nhiều task trên instance lớn.
</details>

**21. Launch template Managed Instances gồm những gì?**
<details><summary>Đáp án</summary>

Instance profile, cấu hình mạng và lưu trữ, capacity option, instance requirements.
</details>

**22. Attribute-based instance selection là gì?**
<details><summary>Đáp án</summary>

Khai báo tập thuộc tính (vCPU, memory, hãng CPU, accelerator…), được tự chuyển thành mọi instance type khớp.
</details>

**23. Hai mức giám sát Managed Instances?**
<details><summary>Đáp án</summary>

Basic (mặc định, miễn phí, 5 phút; status check 1 phút) và detailed (mọi metric 1 phút). Đổi được không cần tạo lại CP.
</details>

**24. Bật tag propagation thế nào, tag lan tới đâu?**
<details><summary>Đáp án</summary>

propagateTags = CAPACITY_PROVIDER khi tạo; lan tới managed instance, container instance, launch template, volume, ENI.
</details>

**25. Thêm tag sau khi tạo capacity provider có áp cho instance cũ không?**
<details><summary>Đáp án</summary>

Không, chỉ không áp cho tài nguyên đã tạo trước đó.
</details>

**26. Ba bước cập nhật capacity provider theo best practice?**
<details><summary>Đáp án</summary>

Tạo CP mới; chuyển từng service, chờ deployment xong; xoá CP cũ khi đã ổn.
</details>

**27. Vì sao không nên sửa capacity provider tại chỗ?**
<details><summary>Đáp án</summary>

Chỉ áp cho capacity mới, không kích hoạt deployment → lỗi cấu hình có thể lộ ra rất muộn khi service scale; blast radius lớn.
</details>

**28. Dùng CloudFormation thì lưu ý gì khi đổi capacity provider?**
<details><summary>Đáp án</summary>

Giữ CP cũ tới lần deploy sau để còn rollback stack.
</details>

**29. Điều kiện xoá capacity provider Managed Instances?**
<details><summary>Đáp án</summary>

Gỡ khỏi strategy của mọi service (UpdateService, force new deployment). Sau khi xoá → INACTIVE.
</details>

**30. Task scale-in protection bảo vệ khỏi gì?**
<details><summary>Đáp án</summary>

Bị dừng bởi scale-in của service auto scaling hoặc bởi deployment.
</details>

**31. Thời hạn mặc định và tối đa?**
<details><summary>Đáp án</summary>

Mặc định 2 giờ (120 phút); ExpiresInMinutes 1–2880 (48 giờ).
</details>

**32. Hai cơ chế đặt bảo vệ và cái được khuyến nghị?**
<details><summary>Đáp án</summary>

Endpoint agent `$ECS_AGENT_URI/task-protection/v1/state` (khuyến nghị: có retry, đơn giản) và ECS API UpdateTaskProtection / GetTaskProtection.
</details>

**33. Task protection áp cho standalone task được không?**
<details><summary>Đáp án</summary>

Không, chỉ task được deploy từ service.
</details>

**34. Rolling update gặp task được bảo vệ thì sao?**
<details><summary>Đáp án</summary>

Task mới vẫn tạo, task cũ không bị dừng tới khi gỡ / hết hạn; chỉnh maximum percent để có chỗ cho task mới.
</details>

**35. Vì sao bảo vệ quá 3 giờ nguy hiểm với CloudFormation?**
<details><summary>Đáp án</summary>

update-stack timeout 3 giờ → UPDATE_FAILED và rollback.
</details>

**36. `DEPLOYMENT_BLOCKED` nghĩa là gì, xử lý thế nào?**
<details><summary>Đáp án</summary>

Nhiều task được bảo vệ hơn desired count. Chờ hết hạn, gỡ bảo vệ task có thể dừng, hoặc tăng desired count.
</details>

**37. Task role cần quyền gì cho task protection?**
<details><summary>Đáp án</summary>

ecs:GetTaskProtection và ecs:UpdateTaskProtection.
</details>

**38. Fargate Spot bị thu hồi được báo thế nào?**
<details><summary>Đáp án</summary>

Trước 2 phút: task state change event tới EventBridge (stopCode SpotInterruption) và SIGTERM tới task.
</details>

**39. Hết capacity Spot thì service làm gì?**
<details><summary>Đáp án</summary>

Thử lại tới khi có capacity; không tự chuyển sang on-demand. Service 1 task sẽ bị gián đoạn.
</details>

**40. Bốn khuyến nghị bảo mật container instance EC2?**
<details><summary>Đáp án</summary>

Không quyền admin; task role tối thiểu; ECS_AWSVPC_BLOCK_IMDS=true cho task awsvpc; GuardDuty Runtime Monitoring.
</details>

---

## 27. Bản đồ nguồn

| Mục | File nguồn |
|---|---|
| 1–6 | `14-00-ecs-clusters.md`, `14-02-00` (quy tắc strategy), `14-01-01`, `14-02-01`, `14-03-02` (tuỳ chọn) |
| 7 | `14-01-00-ecs-managed-instances-clusters.md`, `14-01-03-00` |
| 8 | `14-01-01-ecs-managed-instances-create-cluster.md` |
| 9 | `14-01-02-ecs-managed-instances-update-cluster.md` |
| 10 | `14-01-03-02-ecs-managed-instances-create-capacity-provider.md` |
| 11 | `14-01-03-00`, `14-01-03-03-ecs-managed-instances-update-monitoring.md` |
| 12 | `14-01-03-01-ecs-managed-instances-capacity-provider-update-best-practices.md` |
| 13 | `14-01-03-04-ecs-managed-instances-delete-capacity-provider.md` |
| 14 | `14-01-04-00-ecs-managed-instances-task-scale-in-protection.md` |
| 15 | `14-01-04-01-ecs-managed-instances-task-protection-endpoint.md` |
| 16–17 | `14-02-00-ecs-fargate-clusters.md`, `14-02-01-ecs-fargate-create-cluster.md` |
| 18–20 | `14-03-00`, `14-03-02`, `14-03-01` |
| 21 | Ghép `14-01-01`, `14-02-01`, `14-03-02` |
| 22 | ✍️ ghép lệnh từ các file trên |
| 23–25 | Tổng hợp; `14-99-thuyet-trinh-cto.md` |

Liên quan: `06-98` (Managed Instances), `08-98` (Fargate), `09-98` (EC2), `15-98` (strategy, auto scaling), `05-01` (đổi compute cho service).

Docs gốc:
- Amazon ECS clusters — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/clusters.html>
- Clusters for Managed Instances — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/managed-instances-clusters.html>
- Managed Instances capacity providers — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/managed-instances-capacity-providers-concept.html>
- Task scale-in protection — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task-scale-in-protection.html>
- Clusters for Fargate — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/fargate-capacity-providers.html>
- Capacity providers for EC2 — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/asg-capacity-providers.html>

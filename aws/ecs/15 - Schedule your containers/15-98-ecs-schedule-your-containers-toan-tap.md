# Schedule your containers on Amazon ECS — Toàn tập

> Tổng hợp từ **toàn bộ folder `15 - Schedule your containers`** (`15-00` → `15-04-07-03-01`, `15-99`). Hai phần lớn đã có file toàn tập riêng nên ở đây chỉ tóm lược các ý chính và trỏ link sang:
> - Load balancing → [`15-04-05-99-ecs-load-balancing-toan-tap.md`](15-04-05-99-ecs-load-balancing-toan-tap.md)
> - Service Connect → [`15-04-07-01-99-ecs-service-connect-toan-tap.md`](15-04-07-01-99-ecs-service-connect-toan-tap.md)
>
> **Ký hiệu**
> - Không ký hiệu = lấy từ docs AWS đã dịch trong folder (có ghi file nguồn ở đầu mỗi mục).
> - 🔎 = kiến thức ngoài folder hoặc suy luận của mình — **nên tự kiểm tra lại** với docs AWS hiện tại.
> - ✍️ = ví dụ tự đặt (tên, giá trị, ID là placeholder).
> - ⚠️ = bẫy hay gặp.

---

## Mục lục

0. [Tóm tắt một trang](#0-tóm-tắt-một-trang)
1. [Folder này nói về gì: ECS scheduler](#1-folder-này-nói-về-gì-ecs-scheduler)
2. [Ba cách chạy workload](#2-ba-cách-chạy-workload)
3. [Chọn compute: capacity provider strategy và launch type](#3-chọn-compute-capacity-provider-strategy-và-launch-type)
4. [Vòng đời task](#4-vòng-đời-task)
5. [Task placement: tổng quan theo nền tảng](#5-task-placement-tổng-quan-theo-nền-tảng)
6. [Placement strategy](#6-placement-strategy)
7. [Task group](#7-task-group)
8. [Placement constraint và cluster query language](#8-placement-constraint-và-cluster-query-language)
9. [Đặt task và auto scaling trên Managed Instances](#9-đặt-task-và-auto-scaling-trên-managed-instances)
10. [Standalone task](#10-standalone-task)
11. [Scheduled task với EventBridge Scheduler](#11-scheduled-task-với-eventbridge-scheduler)
12. [Service: service scheduler làm gì](#12-service-service-scheduler-làm-gì)
13. [Load balancing (tóm lược)](#13-load-balancing-tóm-lược)
14. [Service auto scaling: nền tảng chung](#14-service-auto-scaling-nền-tảng-chung)
15. [Năm kiểu auto scaling](#15-năm-kiểu-auto-scaling)
16. [Scale theo hàng đợi SQS](#16-scale-theo-hàng-đợi-sqs)
17. [Kết nối các service với nhau: chọn cách nào](#17-kết-nối-các-service-với-nhau-chọn-cách-nào)
18. [Service Connect (tóm lược)](#18-service-connect-tóm-lược)
19. [Service discovery (Cloud Map DNS)](#19-service-discovery-cloud-map-dns)
20. [Amazon VPC Lattice](#20-amazon-vpc-lattice)
21. [Deploy nhanh hơn: các nút chỉnh thời gian](#21-deploy-nhanh-hơn-các-nút-chỉnh-thời-gian)
22. [Triển khai từng bước (CLI)](#22-triển-khai-từng-bước-cli)
23. [Checklist](#23-checklist)
24. [Troubleshooting](#24-troubleshooting)
25. [Chỗ docs có lỗi / không khớp](#25-chỗ-docs-có-lỗi--không-khớp)
26. [Câu hỏi tự kiểm tra (có đáp án)](#26-câu-hỏi-tự-kiểm-tra-có-đáp-án)
27. [Bản đồ nguồn](#27-bản-đồ-nguồn)

---

## 0. Tóm tắt một trang

| Chủ đề | Điều cần nhớ |
|---|---|
| Scheduler | ECS là hệ **shared state, optimistic concurrency**; scheduler dùng **cùng trạng thái cluster** với ECS API |
| 3 cách chạy | **Service** (lâu dài, giữ N task), **standalone task** (`RunTask`, chạy xong dừng), **scheduled task** (EventBridge Scheduler: rate / cron / one-time) |
| Compute | **Capacity provider strategy** (bắt buộc với Managed Instances) hoặc **launch type** (Fargate / EC2 đăng ký tay) |
| base / weight | `base` = số task tối thiểu trên **một** provider; `weight` = tỉ lệ chia phần còn lại. 12 task, FARGATE base 2 w1 + SPOT w4 → **4 + 8** |
| Vòng đời | `PROVISIONING → PENDING → ACTIVATING → RUNNING → DEACTIVATING → STOPPING → DEPROVISIONING → STOPPED → DELETED` |
| Dừng container | `STOPSIGNAL` (mặc định `SIGTERM`) → chờ `stopTimeout` → `SIGKILL` |
| Placement | **Strategy** = best effort (`binpack`, `spread`, `random`); **constraint** = bắt buộc (`distinctInstance`, `memberOf`); **task group** gom task cho `spread` |
| Theo nền tảng | **EC2**: strategy + constraint; **Managed Instances**: không strategy, constraint 4 thuộc tính; **Fargate**: không cả hai, tự trải AZ |
| Mặc định | Service: `spread` theo AZ. Standalone: **ngẫu nhiên** |
| Service scheduler | Thay task lỗi/unhealthy; start-before-stop nếu `maximumPercent` cho phép; **throttle** khi task liên tục fail trước `RUNNING` |
| Load balancer | ALB (mặc định), NLB, GWLB; `awsvpc` → target type **`ip`**; ≤ **5** target group/service; health check 30s×5, draining **300s** |
| Auto scaling | Application Auto Scaling; 5 kiểu: **target tracking** (mặc định), step, scheduled, predictive, SQS backlog/task |
| Hành vi | Metric 1 phút; scale out nhanh, scale in thận trọng; điểm xuất phát = **số task đang chạy**; trong lúc deploy **scale-in tắt** |
| SQS | Scale theo **backlog per task** = queue depth ÷ running tasks (cần **Container Insights**); TargetValue = độ trễ chấp nhận ÷ thời gian xử lý 1 message |
| Service ↔ service | **Service Connect** (khuyến nghị), **service discovery** (Cloud Map DNS, ≤ **1.000 task/service**), **VPC Lattice** (cross-account, có phí) |
| ⚠️ Bẫy lớn nhất | Draining 300 s làm deploy chậm; scale in giết worker đang xử lý (dùng task protection); DNS TTL trỏ vào task đã chết; Spot hết capacity không tự sang on-demand; Container Insights tắt thì SQS scaling không chạy |

---

## 1. Folder này nói về gì: ECS scheduler

> Nguồn: `15-00`, `15-99`

**Vị trí trong cả loạt bài:** đây là bước **vận hành**. Thiết kế ở folder 05, mô tả app ở folder 13 (task definition), gom capacity ở folder 14 (cluster), rồi tới folder 15 để **chạy, đặt, scale và nối** các container.

```
13 task definition ──► 15 service / job / cron ──► chạy trên cluster 14
                              ├─ đặt ở đâu: placement (mục 5–9)
                              ├─ bao nhiêu: auto scaling (mục 14–16)
                              ├─ traffic vào: ALB/NLB (mục 13) hoặc ECS Gateway (folder 12)
                              └─ gọi nhau: Service Connect / Cloud Map / VPC Lattice (mục 17–20)
```

**ECS scheduler hoạt động thế nào:**
- ECS là hệ thống **shared state, optimistic concurrency**: nhiều scheduler cùng đọc một trạng thái cluster chung và quyết định mà không khoá toàn cluster. Nếu có xung đột thì xử lý sau 🔎 (docs không đi sâu cơ chế).
- Scheduler dùng **cùng dữ liệu** với ECS API, nên những gì bạn thấy qua `describe-*` chính là dữ liệu scheduler dùng để đặt task.
- ECS có **service scheduler** cho workload lâu dài, và khả năng chạy **standalone/scheduled task** cho batch.
- Có thể dùng **placement strategy/constraint**, hoặc tích hợp **scheduler tự viết / bên thứ ba**.

---

## 2. Ba cách chạy workload

> Nguồn: `15-00` mục 2, `15-03-00`, `15-04-00`

```
Chạy liên tục, tự thay task hỏng, có load balancer?   → Service
Chạy một lần theo sự kiện (vd có message trong queue)? → Standalone task (RunTask)
Chạy định kỳ theo giờ giấc?                            → Scheduled task (EventBridge Scheduler)
```

| | **Service** | **Standalone task** | **Scheduled task** |
|---|---|---|---|
| Vòng đời | Lâu dài, ECS giữ đúng `desiredCount` | Chạy xong rồi dừng | Chạy xong rồi dừng, lặp theo lịch |
| Ai kích hoạt | ECS service scheduler | App / bạn gọi `RunTask` | EventBridge Scheduler |
| Tự thay khi chết | ✅ | ❌ | ❌ (mỗi lần chạy là một task mới) |
| Load balancer | Tuỳ chọn | — | — |
| Placement mặc định | `spread` theo AZ | **Ngẫu nhiên** (`random`) | 🔎 như `RunTask` |
| Auto scaling | ✅ Service Auto Scaling | — | — |
| Service Connect | ✅ | ❌ (không hỗ trợ standalone) | ❌ |
| Ví dụ | API, web, gateway | Xử lý job từ queue, migration DB | Backup, quét log, báo cáo đêm |

⚠️ Hệ quả quan trọng ở các folder khác: **standalone task không được ECS thay tự động** khi Fargate retire task (folder 08), khi Managed Instances drain máy (folder 06) hay khi gom instance. Job dài phải có checkpoint và chạy lại được.

---

## 3. Chọn compute: capacity provider strategy và launch type

> Nguồn: `15-00` mục 3–4, `15-04-00` mục 1, `15-03-01`

### 3.1 Hai lựa chọn

| | **Capacity provider strategy** | **Launch type** |
|---|---|---|
| Fargate | `FARGATE`, `FARGATE_SPOT` (chia tỉ lệ được) | Fargate |
| EC2 | Auto Scaling group (ECS scale instance theo nhu cầu) | EC2 instance **đăng ký thủ công** |
| Managed Instances | ✅ **bắt buộc** dùng lựa chọn này | ❌ |
| Chia task qua nhiều nguồn | ✅ (base + weight) | ❌ |
| Điều kiện | Provider đã **gắn với cluster**, trạng thái **`ACTIVE`** hoặc **`UPDATING`** | — |

### 3.2 Console chọn mặc định thế nào (khi tạo service / chạy task)

```
Cluster có strategy mặc định?            ─ có ─► dùng strategy đó
        │ không
Có capacity provider Fargate?            ─ có ─► strategy tuỳ chỉnh dùng FARGATE
        │ không
Có capacity provider Auto Scaling group? ─ có ─► "Use custom (Advanced)" — bạn tự định nghĩa
        │ không
        ▼
Fargate launch type
```

### 3.3 base và weight

- **`base`** (tuỳ chọn): số task **tối thiểu** chạy trên một provider. Trong một strategy **chỉ một** provider được có `base`.
- **`weight`**: tỉ lệ tương đối chia phần còn lại. A weight 1, B weight 4 → cứ 1 task trên A thì 4 task trên B.

✍️ 12 task:

```json
"capacityProviderStrategy": [
  { "capacityProvider": "FARGATE",      "base": 2, "weight": 1 },
  { "capacityProvider": "FARGATE_SPOT",            "weight": 4 }
]
```

```
Bước 1 — base:   2 task đầu → FARGATE
Bước 2 — weight: 10 task còn lại chia 1 : 4 → FARGATE 2, FARGATE_SPOT 8
Tổng:            FARGATE 4, FARGATE_SPOT 8
```

Cách dùng phổ biến 🔎: đặt `base` trên provider ổn định (on-demand) để luôn có vài task không bị gián đoạn, rồi dùng `weight` đẩy phần lớn task sang Spot cho rẻ.

⚠️ Quy tắc từ folder 05 và 14 cần nhớ đi kèm:
- Một strategy chỉ chứa **một loại** provider (Fargate/Fargate Spot, **hoặc** ASG, **hoặc** Managed Instances).
- **Fargate Spot hết capacity thì ECS không tự chuyển sang on-demand** → phần bắt buộc phải chạy nên nằm trong `base` on-demand.
- Đã chuyển sang capacity provider thì không quay về launch type khác được (folder 05).

---

## 4. Vòng đời task

> Nguồn: `15-01`, `15-03-00`

### 4.1 `lastStatus` và `desiredStatus`

```
desiredStatus = nơi ECS muốn task đi tới   (RUNNING, hoặc STOPPED khi bạn gọi stop-task)
lastStatus    = nơi task thực sự đang ở    (PENDING, ACTIVATING, RUNNING...)
```

```bash
aws ecs describe-tasks --cluster <CLUSTER> --tasks <TASK_ID> \
  --query 'tasks[].{last:lastStatus,desired:desiredStatus,reason:stoppedReason}'
```

### 4.2 Các trạng thái

```
 PROVISIONING   chuẩn bị trước khi launch (vd tạo ENI cho awsvpc; tìm capacity)
      │
 PENDING        chờ agent / chờ đủ tài nguyên
      │
 ACTIVATING     pull image, tạo container, cấu hình mạng,
      │         ĐĂNG KÝ target group, cấu hình service discovery
      ▼
 RUNNING  ─────────────────────────►  DEACTIVATING   (vd HUỶ ĐĂNG KÝ target group, draining)
                                          │
                                     STOPPING        (STOPSIGNAL → chờ StopTimeout → SIGKILL)
                                          │
                                     DEPROVISIONING  (tháo và xoá ENI)
                                          │
                                     STOPPED
                                          │
                                     DELETED         (chỉ thấy trong describe-tasks, không có trên console)
```

| Trạng thái | Loại | Kẹt ở đây thường do 🔎 |
|---|---|---|
| `PROVISIONING` | Chuẩn bị | Tạo ENI (subnet hết IP); thiếu capacity (có managed scaling thì task **chờ** thay vì fail) |
| `PENDING` | Chuyển tiếp | Không đủ CPU/memory, không instance nào thoả constraint |
| `ACTIVATING` | Chuyển tiếp | Pull image (quyền ECR, NAT/VPC endpoint), đăng ký target group, service discovery |
| `RUNNING` | Ổn định | — |
| `DEACTIVATING` | Chuyển tiếp | Deregistration delay của target group (mặc định 300 s) |
| `STOPPING` | Chuyển tiếp | App không xử lý `SIGTERM` → phải chờ hết `stopTimeout` |
| `DEPROVISIONING` | Dọn dẹp | Tháo/xoá ENI |
| `STOPPED` | Kết thúc | Xem `stoppedReason` / *stopped task errors* |

### 4.3 Một task standalone đi từ yêu cầu tới `RUNNING` (`15-03-00`)

```
RunTask ─► PROVISIONING (ECS tìm capacity theo launch type / capacity provider)
              ├─ có managed scaling: thiếu capacity thì CHỜ ở PROVISIONING (không fail ngay)
              └─ đủ capacity: cấp attachment (vd ENI)
         ─► agent pull image ─► chạy container ─► RUNNING
Vị trí đặt: theo CPU/memory trong task definition + placement constraint/strategy
```

### 4.4 Dừng container: graceful shutdown

```
STOPPING:
  t = 0           gửi STOPSIGNAL (chỉ thị trong Dockerfile; mặc định SIGTERM)
  t = StopTimeout nếu container còn sống → SIGKILL
```

| Nền tảng | `stopTimeout` (theo folder 13) |
|---|---|
| Fargate | Tối đa **120 giây** |
| Managed Instances | Mặc định 30, tối đa **86400 giây** |
| EC2 | `ECS_CONTAINER_STOP_TIMEOUT` (mặc định **30 giây**) hoặc `stopTimeout` trong container definition |

Quy tắc: app **bắt `SIGTERM`**, ngừng nhận việc mới, làm nốt việc dở, rồi thoát **trước** `stopTimeout`. Tiến trình chính không chuyển signal cho tiến trình con thì cân nhắc `initProcessEnabled`.

**Thời gian từ khi cần task tới khi task nhận traffic** = `PROVISIONING` + `PENDING` + `ACTIVATING` (gồm pull image) + health check của target group.

---

## 5. Task placement: tổng quan theo nền tảng

> Nguồn: `15-02-00`

Placement = quyết định **đặt task lên instance nào** (và **dừng task nào** khi scale in). Gồm ba thành phần:

| Thành phần | Bản chất | Không thoả thì |
|---|---|---|
| **Strategy** | Thuật toán chọn instance / chọn task để dừng — **best effort** | Vẫn đặt được, chỉ là không tối ưu |
| **Task group** | Nhóm task liên quan, dùng cho `spread` và `memberOf` | — |
| **Constraint** | Quy tắc **bắt buộc** về instance | Task nằm ở **`PENDING`** |

| | **EC2** | **Managed Instances** | **Fargate** |
|---|---|---|---|
| Strategy | ✅ binpack / spread / random, kết hợp được | ❌ | ❌ |
| Constraint | ✅ đầy đủ | ✅ chỉ `distinctInstance` + `memberOf` với 4 thuộc tính | ❌ |
| Mặc định | Service: `spread` theo AZ; standalone: ngẫu nhiên | Trải AZ ưu tiên hơn bin packing | Tự trải qua AZ (độc lập cho Fargate và Fargate Spot) |
| Quy trình chọn | Tài nguyên → constraint → **strategy** → chọn | Tài nguyên → constraint → **instance requirements** → chọn | — |

Quy trình trên EC2:

```
1. Lọc instance đủ CPU, memory, port, GPU… theo task definition
2. Lọc tiếp theo placement constraint (bắt buộc)
3. Áp placement strategy theo thứ tự (best effort)
4. Chọn instance và đặt task
```

---

## 6. Placement strategy

> Nguồn: `15-02-02`, `15-03-01` mục placement

Chỉ dành cho **EC2**. Khai báo `placementStrategy` (mảng, mỗi phần tử `type` + `field`) qua `RunTask`, `CreateService`, `UpdateService`. **Cập nhật được** cho service đang chạy.

| Strategy | `field` | Đặt task | Scale in dừng task nào |
|---|---|---|---|
| **`binpack`** | `cpu` hoặc `memory` | Dồn vào instance còn **ít** tài nguyên trống nhất → ít instance, rẻ | Task ở instance còn **nhiều** tài nguyên nhất sau khi dừng |
| **`spread`** | `instanceId` (hoặc `host`), hoặc thuộc tính platform/custom như `attribute:ecs.availability-zone` | Rải đều theo service / task group → chịu lỗi tốt | Giữ cân bằng (vd giữa AZ); trong một AZ chọn ngẫu nhiên |
| **`random`** | không dùng `field` | Ngẫu nhiên | Ngẫu nhiên |

```
binpack (memory):   i-1: [T][T][T][T]   i-2: [ ]   i-3: [ ]     → dồn, ít instance, rẻ
spread  (instanceId): i-1: [T][T]       i-2: [T]   i-3: [T]     → rải, chịu lỗi tốt
random:             phân bố bất kỳ
```

**Kết hợp nhiều strategy** — áp theo thứ tự trong mảng:

```json
"placementStrategy": [
  { "type": "spread",  "field": "attribute:ecs.availability-zone" },
  { "type": "binpack", "field": "memory" }
]
```

```
Bước 1 (spread AZ):      chia đều task cho AZ-a, AZ-b, AZ-c
Bước 2 (binpack memory): trong mỗi AZ, dồn task vào instance còn ít memory trống nhất
```

**Mẫu có sẵn trên console khi Run task** (`15-03-01`):

| Mẫu console | Tương đương 🔎 |
|---|---|
| AZ Balanced Spread | `spread` AZ + `spread` instanceId |
| AZ Balanced BinPack | `spread` AZ + `binpack` memory |
| BinPack | `binpack` memory |
| One Task Per Host | constraint `distinctInstance` |
| Custom | Tự chọn: **≤ 5 strategy**, **≤ 10 constraint** |

---

## 7. Task group

> Nguồn: `15-02-03`

- **Task group** = tập task liên quan; task cùng tên group được xem là **một tập** khi dùng `spread`.
- Dùng với `spread`: ✍️ group `databases` để cân bằng database qua các AZ.
- Dùng với `memberOf`: task chỉ đặt lên instance **đang chạy task của group chỉ định**.

| | Group mặc định |
|---|---|
| Standalone task | `family:<tên task definition>` |
| Task của service | **Tên service** (không đổi được) |

Yêu cầu: tên ≤ **255** ký tự; mỗi task thuộc **đúng một** group; **không sửa** group sau khi launch.

---

## 8. Placement constraint và cluster query language

> Nguồn: `15-02-04`

### 8.1 Hai loại constraint

```json
"placementConstraints": [
  { "type": "<LOẠI_CONSTRAINT>", "expression": "<BIỂU_THỨC>" }
]
```

| Loại | Ý nghĩa | Dùng ở |
|---|---|---|
| **`distinctInstance`** | Mỗi task trên một instance **khác nhau** | `RunTask`, `CreateService` |
| **`memberOf`** | Instance phải thoả **biểu thức cluster query language** | `RunTask`, `CreateService`, `RegisterTaskDefinition`, `UpdateService` |

⚠️ `distinctInstance` dựa vào **desired status**, nên đôi khi vẫn thấy 2 task `RUNNING` trên cùng instance (vd task cũ đang dừng). Cần cô lập mạnh thật sự → **Fargate**.

### 8.2 Thuộc tính dùng trong biểu thức

| Loại | Thuộc tính |
|---|---|
| **Built-in** | `ecs.ami-id`, `ecs.availability-zone`, `ecs.instance-type`, `ecs.os-type`, `ecs.os-family`, `ecs.cpu-architecture`, `ecs.vpc-id`, `ecs.subnet-id` |
| **Optional** | `ecs.awsvpc-trunk-id`, `ecs.outpost-arn`, `ecs.capability.external` |
| **Custom** | Bạn tự gắn; tên và giá trị 1–128 ký tự theo bộ ký tự cho phép (vd `stack=prod`) |
| **Managed Instances** | Chỉ `ecs.subnet-id`, `ecs.availability-zone`, `ecs.instance-type`, `ecs.cpu-architecture` |

### 8.3 Ví dụ

```json
"placementConstraints": [
  { "type": "distinctInstance" },
  { "type": "memberOf", "expression": "attribute:ecs.instance-type =~ t3.*" }
]
```

Thuộc tính custom:

```bash
aws ecs put-attributes --cluster <CLUSTER> \
  --attributes name=stack,value=prod,targetType=container-instance,targetId=<CONTAINER_INSTANCE_ARN>
```

```json
"placementConstraints": [
  { "type": "memberOf", "expression": "attribute:stack == prod" }
]
```

✍️ Thêm vài biểu thức hay dùng:

| Mục đích | Biểu thức |
|---|---|
| Chỉ 2 AZ | `attribute:ecs.availability-zone in [ap-southeast-1a, ap-southeast-1b]` |
| Chỉ máy GPU | `attribute:ecs.instance-type =~ g5.*` |
| Chỉ ARM | `attribute:ecs.cpu-architecture == arm64` |
| Đặt cạnh task group `cache` | `task:group == cache` |

Windows container phải khớp phiên bản OS với host → dùng `memberOf` với `ecs.os-family`.

---

## 9. Đặt task và auto scaling trên Managed Instances

> Nguồn: `15-02-01` (chi tiết thêm ở `06-98` toàn tập Managed Instances)

- Managed Instances **tự scale và tự đặt task**, cân bằng **sẵn sàng**, **tài nguyên** và **mạng**.
- **Trải AZ là mặc định và ưu tiên hơn bin packing.** Service nhiều task → cố dùng **ít nhất 3 instance ở các AZ khác nhau**.
- **Bin packing** thật sự cần capacity provider có **một subnet**; dễ xảy ra lúc **mới launch** hơn lúc scale.
- `awsvpc`: mỗi task một ENI → **giới hạn ENI** của instance type giới hạn mật độ task.
- `host`: đặt task chủ yếu theo tài nguyên; `cpuArchitecture` chọn `X86_64`/`ARM64`.
- Capacity provider quyết định dựa trên: tài nguyên task đang chờ, instance sẵn có, ràng buộc mạng, phân bố AZ.

| Cấu hình subnet | Dùng cho |
|---|---|
| **Nhiều subnet** (mặc định) | Production — sẵn sàng cao |
| **Một subnet** | Dev hoặc tối ưu chi phí (xếp chặt) |

Phân bố "spread" hay "concentrated" đều bình thường tuỳ cấu hình; **không đều bất thường** → xem giới hạn ENI và tài nguyên.

---

## 10. Standalone task

> Nguồn: `15-03-00`, `15-03-01`, `15-03-03`

### 10.1 Khi nào dùng

Ứng dụng **làm việc rồi dừng** (batch, migration, job từ queue), chạy một lần qua console, CLI, API, SDK. Chạy theo lịch → EventBridge Scheduler (mục 11).

### 10.2 Chạy trên console: các nhóm tuỳ chọn

Bắt đầu từ **Cluster → Tasks → Run task**, hoặc **Task definition revision → Deploy → Run task**.

| Nhóm | Nội dung |
|---|---|
| **Compute** | Capacity provider strategy (cluster default hoặc custom: provider, base, weight — **bắt buộc với Managed Instances**) hoặc launch type (Fargate có platform version, mặc định `LATEST`) |
| **Deployment** | Task definition (console kiểm tra tương thích), **desired tasks**, **task group** |
| **Networking** (`awsvpc`) | VPC, subnet, security group, **Public IP** (trường này chỉ Fargate dùng được) |
| **Volume EBS** lúc chạy | Tên/loại cố định theo revision; size **1–16.384 GiB**; IOPS (io1/io2/gp3), throughput (gp3); snapshot + tốc độ khởi tạo **100–300 MiB/s**; mặc định **xoá khi task kết thúc**; filesystem phải khớp snapshot (Linux mặc định **XFS**); cần **infrastructure role**; mã hoá Default/Custom/None; tag |
| **Placement** (tuỳ chọn) | Mẫu ở mục 6; Custom ≤ 5 strategy, ≤ 10 constraint |
| **Overrides** (tuỳ chọn) | Task role (trust `ecs-tasks.amazonaws.com`), execution role; **command** và **biến môi trường** của container |
| **Tags** | ECS managed tags, tag tuỳ chỉnh |

### 10.3 Chạy bằng CLI ✍️

```bash
aws ecs run-task \
  --cluster <CLUSTER> \
  --task-definition <FAMILY>:<REV> \
  --count 1 \
  --capacity-provider-strategy capacityProvider=FARGATE,weight=1 \
  --network-configuration 'awsvpcConfiguration={subnets=[<SUBNET_ID>],securityGroups=[<SG_ID>],assignPublicIp=DISABLED}' \
  --overrides '{"containerOverrides":[{"name":"app","command":["python","migrate.py"],"environment":[{"name":"MODE","value":"once"}]}]}' \
  --group migration \
  --enable-ecs-managed-tags --propagate-tags TASK_DEFINITION
```

⚠️ `RunTask` **không tự retry** khi bị throttle hoặc thiếu capacity (folder 05) → gọi từ pipeline thì tự retry với exponential backoff, hoặc dùng Step Functions.

### 10.4 Dừng task

- Task đã dừng **không khởi động lại** được.
- Console: **Clusters → cluster → Tasks → Stop**:
  - **Stop selected**: dừng task đã chọn.
  - **Stop all**: dừng **mọi** task (**cả task của service**), phải gõ `Stop all tasks` để xác nhận — ⚠️ cẩn thận.
- Dừng service thì xoá service (hoặc đặt desired count 0).

```bash
aws ecs stop-task --cluster <CLUSTER> --task <TASK_ID> --reason "manual stop"
```

---

## 11. Scheduled task với EventBridge Scheduler

> Nguồn: `15-03-02`

### 11.1 EventBridge Scheduler là gì

- Scheduler **serverless**, tạo lịch **một lần** hoặc **định kỳ**, **không phụ thuộc** event bus/rule; scale tốt hơn scheduled rule kiểu cũ; console Scheduler nhiều chức năng hơn console ECS.
- Kiểu lịch:

| Kiểu | Ví dụ ✍️ |
|---|---|
| **rate** | `rate(15 minutes)`, `rate(1 day)` |
| **cron** (mọi múi giờ) | `cron(0 2 * * ? *)` + timezone `Asia/Ho_Chi_Minh` = 2 giờ sáng mỗi ngày |
| **one-time** (mọi múi giờ) | `at(2026-12-31T23:00:00)` |

```
EventBridge Scheduler ──(execution role của Scheduler)──► ecs:RunTask
                                                             │
                                          ECS chạy task với task role / task execution role
```

### 11.2 Chuẩn bị

- Subnet ID + security group ID.
- **Execution role của Scheduler** (cho phép gọi `ecs:RunTask` và 🔎 `iam:PassRole` cho task role / execution role).
- Capacity provider đã gắn cluster (nếu dùng strategy).

### 11.3 Các bước tạo lịch

```
Tên, mô tả, schedule group
  → One-time (ngày, giờ 24h, múi giờ) hoặc Recurring (cron/rate, flexible time window, timeframe)
  → Target: Amazon ECS → RunTask
       cluster, task definition, capacity provider strategy hoặc launch type,
       subnet, SG, placement constraint, tag
  → Settings: enable, retry (≤ 24 giờ & ≤ 185 lần), DLQ SQS (cùng/khác account), KMS key,
       execution role có sẵn hoặc tạo mới
  → Review → Create schedule → kiểm tra Status = Enabled
```

### 11.4 CLI

```bash
aws scheduler create-schedule \
  --name nightly-report \
  --schedule-expression "cron(0 2 * * ? *)" \
  --schedule-expression-timezone "Asia/Ho_Chi_Minh" \
  --flexible-time-window Mode=OFF \
  --target '{
    "Arn": "arn:aws:ecs:<REGION>:<ACCOUNT_ID>:cluster/<CLUSTER>",
    "RoleArn": "arn:aws:iam::<ACCOUNT_ID>:role/<SCHEDULER_ROLE>",
    "EcsParameters": {
      "TaskDefinitionArn": "arn:aws:ecs:<REGION>:<ACCOUNT_ID>:task-definition/<FAMILY>",
      "LaunchType": "FARGATE",
      "NetworkConfiguration": {
        "awsvpcConfiguration": {
          "Subnets": ["<SUBNET_ID>"],
          "SecurityGroups": ["<SG_ID>"],
          "AssignPublicIp": "DISABLED"
        }
      }
    },
    "RetryPolicy": { "MaximumRetryAttempts": 3, "MaximumEventAgeInSeconds": 3600 }
  }'
```

Terraform tương ứng có ở `15-03-02` (`aws_scheduler_schedule`).

🔎 Lưu ý thực tế:
- `TaskDefinitionArn` **không có revision** thì chạy revision mới nhất; muốn cố định thì ghi rõ `:<REV>`.
- Job chạy lâu hơn chu kỳ lịch → hai bản chạy chồng nhau; app nên có khoá (vd DynamoDB lock) nếu không được chạy song song.
- Cấu hình **DLQ** để không mất dấu những lần `RunTask` thất bại.

---

## 12. Service: service scheduler làm gì

> Nguồn: `15-04-00`

### 12.1 Trách nhiệm

| Việc | Khi nào |
|---|---|
| Giữ đúng `desiredCount` task | Luôn luôn |
| Khởi chạy task thay thế | Task lỗi, dừng, hạ tầng bên dưới lỗi |
| Thay task **unhealthy** | **Container health check** hoặc **health check target group** thất bại |
| **Làm chậm** việc khởi chạy lại + gửi service event | Task liên tục dừng mà **chưa vào `RUNNING`** (throttle logic) |
| Tuân thủ placement strategy/constraint | Khi đặt và dừng task |

Khuyến nghị dùng cho ứng dụng **stateless chạy lâu dài**.

### 12.2 Thay task unhealthy

```
Task bị đánh dấu unhealthy
   │
   ├─ maximumPercent CHO PHÉP chạy thêm task trước:
   │     khởi động task thay thế
   │       ├─ task thay thế HEALTHY   → dừng task unhealthy
   │       └─ task thay thế UNHEALTHY → dừng task thay thế HOẶC task cũ, cho tổng = desiredCount
   │
   └─ maximumPercent KHÔNG cho phép:
         dừng từng task unhealthy (ngẫu nhiên) → khởi động task thay thế → lặp lại
   ▼
Mọi task healthy; nếu tổng > desiredCount → dừng ngẫu nhiên task healthy cho bằng desiredCount
```

### 12.3 Throttle logic

- Task dừng **trước khi vào `RUNNING`** liên tục → scheduler **giãn khoảng** giữa các lần thử và gửi **service event message**.
- Mục đích: không đốt tài nguyên cho task lỗi trước khi bạn kịp sửa.
- **Update service** (vd revision mới) → scheduler quay về hành vi bình thường.

🔎 Kết hợp với **deployment circuit breaker** (bật rollback) để deploy hỏng tự quay về revision cũ, thay vì lặp start → fail.

### 12.4 Ba nhánh mở rộng của service

| Nhánh | Mục |
|---|---|
| Cân bằng tải (ALB, NLB, GWLB) | 13 |
| Auto scaling (Application Auto Scaling) | 14–16 |
| Nối service với nhau (Service Connect, service discovery, VPC Lattice) | 17–20 |

---

## 13. Load balancing (tóm lược)

> Nguồn: `15-04-05-00` → `15-04-05-06`. **Chi tiết đầy đủ:** [`15-04-05-99-ecs-load-balancing-toan-tap.md`](15-04-05-99-ecs-load-balancing-toan-tap.md)

| | ALB | NLB | GWLB |
|---|---|---|---|
| Tầng | 7 (HTTP/HTTPS, gRPC, WebSocket) | 4 (TCP/UDP/TLS) | 3 (mọi gói IP) |
| Dùng cho | Web, REST API — **mặc định** | Không phải HTTP, UDP, mã hoá end-to-end, giữ IP client | Firewall, IDS/IPS, deep packet inspection |
| Host/path routing | ✅ (nhiều service chung một listener) | ❌ | ❌ |
| Network mode | Mọi mode | Mọi mode | **Chỉ `awsvpc`** |

Điểm phải nhớ:
- `awsvpc` (bắt buộc với Fargate) → target type **`ip`**. `bridge` → `instance` + **dynamic host port** (`hostPort: 0`, dải 32768–61000).
- **Mỗi service một target group**; tối đa **5 target group / service** (ALB/NLB, controller `ECS`, tạo bằng API/CLI/CloudFormation, console chỉ xem).
- ECS **tự đăng ký** task ở `ACTIVATING` và **tự huỷ đăng ký** ở `DEACTIVATING` (qua service-linked role).
- **Health check**: mặc định interval 30 s × healthy threshold 5. Target **mới** chỉ cần **1 lần pass**. App khởi động chậm → `healthCheckGracePeriodSeconds`.
- **Deregistration delay** mặc định **300 s** — thủ phạm số 1 làm deploy chậm; request ngắn (< 1 s) thì hạ xuống 5 s.
- NLB không gửi được thông điệp đóng kết nối tầng ứng dụng → đặt deregistration delay **dài hơn timeout của client**.
- Cấu hình LB của service **không sửa trên console** sau khi tạo; sửa bằng CLI/SDK/CloudFormation (controller `ECS`), mỗi lần sửa là **một deployment mới**.
- Traffic từ ngoài → LB; **ECS gọi ECS** → Service Connect.

---

## 14. Service auto scaling: nền tảng chung

> Nguồn: `15-04-06-00`, `15-04-00` mục 2

### 14.1 Cơ chế

```
ECS ─ đẩy metric CPU/memory trung bình của service (chu kỳ 1 phút) ─► CloudWatch
                                                                         │ alarm
Application Auto Scaling ◄───────────────────────────────────────────────┘
      │ scalable target: ecs:service:DesiredCount  (min, max)
      ▼
UpdateService desiredCount ─► service scheduler thêm/bớt task
      ▼
Capacity bên dưới: Fargate (AWS lo) · ASG capacity provider · Managed Instances (tự scale instance)
```

### 14.2 Những quy tắc phải nhớ

| Quy tắc | Nội dung |
|---|---|
| Metric | Gửi **mỗi 1 phút**; metric chưa tồn tại thì chưa tạo alarm được |
| Cooldown scale-out | Scale out **liên tục nhưng không quá mức**: trong cooldown không tăng thêm, **trừ khi** có lần scale-out **lớn hơn**; capacity vừa thêm được tính vào lần sau |
| Cooldown scale-in | **Thận trọng**: scale-in **bị chặn** tới khi hết cooldown; nếu alarm khác gây scale-out thì scale out **ngay**, cooldown scale-in dừng |
| Desired count đặt tay | Có thể bị **ghi đè** khi service có policy/alarm đang hoạt động |
| Desired **< min** | Scale-out → nâng lên **min** rồi scale tiếp; scale-in → không đổi |
| Desired **> max** | Scale-in → đưa về **max** rồi scale tiếp; scale-out → không đổi |
| Điểm xuất phát | **Số task đang chạy thực tế**, không phải desired count → tránh scale "runaway" khi thiếu tài nguyên |
| Scale về 0 | Đặt **min = 0**; target tracking chờ **1 data point** rồi scale out mức nhỏ nhất |
| Trong lúc deploy | **Scale-in tắt**, scale-out **vẫn chạy** (trừ khi tạm dừng); không áp dụng cho **external deployment controller** |

### 14.3 Tạm dừng scale-out trong lúc deploy

```
1. describe-scalable-targets  (resource ID, vd service/default/sample-webapp) → ghi lại output
2. register-scalable-target   DynamicScalingInSuspended=true, DynamicScalingOutSuspended=true
   ... deploy ...
3. register-scalable-target   (sau khi deploy xong) → bật lại
```

```bash
aws application-autoscaling register-scalable-target \
  --service-namespace ecs --scalable-dimension ecs:service:DesiredCount \
  --resource-id service/<CLUSTER>/<SERVICE> \
  --suspended-state DynamicScalingInSuspended=true,DynamicScalingOutSuspended=true,ScheduledScalingSuspended=false
```

### 14.4 Chọn metric (từ folder 05) 🔎 ghép

| Kiểu app | Metric nên dùng |
|---|---|
| CPU-bound (Go, Node…) | CPU utilization |
| Java, .NET, Ruby | CPU — **không** dùng memory (heap giữ sẵn) |
| Worker (Apache, Gunicorn) | Mức đồng thời trung bình / replica |
| Job processor (SQS, Kinesis) | **Backlog per task** (mục 16) |
| Web sau ALB | `ALBRequestCountPerTarget` (⚠️ không hỗ trợ với blue/green) |
| Đột biến biết trước | Scheduled scaling |

Kiểm tra metric tốt: **gấp đôi số task ở cùng mức tải → metric giảm một nửa**. Target utilization nên **60–80%** để đỡ độ trễ của autoscaling; HA: 2 AZ chạy 2×, 3 AZ chạy 1,5× (folder 05).

---

## 15. Năm kiểu auto scaling

> Nguồn: `15-04-06-01` → `15-04-06-05`

| Kiểu | Dựa trên | Hợp với | Ai tạo alarm |
|---|---|---|---|
| **Target tracking** ⭐ | Giá trị mục tiêu của một metric ("máy điều nhiệt") | Đa số workload | Service Auto Scaling |
| **Step scaling** | Step adjustment theo **mức vượt ngưỡng** alarm | Muốn kiểm soát bước, phản ứng nhanh | **Bạn** |
| **Scheduled** | Ngày giờ cụ thể | Tải dự đoán được (đỉnh giữa tuần, sale) | — |
| **Predictive** | Dữ liệu lịch sử (mẫu ngày/tuần) | Traffic có chu kỳ, app khởi động lâu | — |
| **SQS backlog per task** | Message tồn ÷ số task | Worker bất đồng bộ | (target tracking với metric math) |

### 15.1 Target tracking

- Bạn chọn **metric + mục tiêu**; Service Auto Scaling **tạo và quản lý alarm**, tính mức điều chỉnh, hạn chế dao động.
- Chỉ scale out khi metric **cao hơn** mục tiêu (không làm ngược được).
- Metric **thiếu dữ liệu** → **không scale** (không coi là tải thấp).
- Lệch giữa mục tiêu và thực tế là bình thường (làm tròn thận trọng).
- Scale out **nhanh**, scale in **từ từ**.
- **Nhiều policy** (mỗi cái một metric khác nhau): scale **out** nếu **bất kỳ** policy nào muốn; scale **in** chỉ khi **tất cả** policy (có bật scale-in) đều muốn.
- ⚠️ **Không sửa/xoá** alarm do Service Auto Scaling tạo; chúng tự xoá khi xoá policy.
- ⚠️ `ALBRequestCountPerTarget` **không hỗ trợ** với blue/green.

✍️ CLI:

```bash
aws application-autoscaling register-scalable-target \
  --service-namespace ecs --scalable-dimension ecs:service:DesiredCount \
  --resource-id service/<CLUSTER>/<SERVICE> --min-capacity 2 --max-capacity 20

aws application-autoscaling put-scaling-policy \
  --service-namespace ecs --scalable-dimension ecs:service:DesiredCount \
  --resource-id service/<CLUSTER>/<SERVICE> \
  --policy-name cpu70 --policy-type TargetTrackingScaling \
  --target-tracking-scaling-policy-configuration '{
    "TargetValue": 70,
    "PredefinedMetricSpecification": { "PredefinedMetricType": "ECSServiceAverageCPUUtilization" },
    "ScaleOutCooldown": 60, "ScaleInCooldown": 300
  }'
```

### 15.2 Step scaling

```
Bạn tạo CloudWatch alarm ─► alarm vượt ngưỡng ─► chạy step scaling policy
    vượt ngưỡng 1 → step adjustment 1
    vượt ngưỡng 2 → step adjustment 2 ...
```

Docs **khuyến nghị mạnh target tracking** cho metric như CPU trung bình hay số request trung bình mỗi target — loại metric **giảm khi capacity tăng, tăng khi capacity giảm**. Step scaling cho phép phản ứng **nhanh hơn** (bạn định nghĩa ngưỡng và số task thêm/bớt, giảm thời gian alarm ở trạng thái vi phạm).

### 15.3 Scheduled scaling

- **Scheduled action** đổi số task (hoặc khoảng **min/max**) vào thời điểm cụ thể → scale **chủ động**.
- Dùng **cùng** scaling policy: scheduled action đặt khoảng [min, max], policy tiếp tục scale **trong khoảng đó**.
- Cấu hình bằng **AWS CLI**.

✍️

```bash
aws application-autoscaling put-scheduled-action \
  --service-namespace ecs --scalable-dimension ecs:service:DesiredCount \
  --resource-id service/<CLUSTER>/<SERVICE> \
  --scheduled-action-name flash-sale \
  --schedule "cron(0 19 ? * FRI *)" --timezone "Asia/Ho_Chi_Minh" \
  --scalable-target-action MinCapacity=10,MaxCapacity=50
```

### 15.4 Predictive scaling

| Thông số | Giá trị |
|---|---|
| Dữ liệu tối thiểu | **24 giờ** (lý tưởng **2 tuần**) |
| Dữ liệu phân tích | Tối đa **14 ngày** trước |
| Dự báo | **Theo giờ**, cho **48 giờ** tới, cập nhật **mỗi 6 giờ** |
| Chế độ | **forecast only** (mặc định khi bật lần đầu, chỉ dự báo) → **forecast and scale** |
| Thời điểm scale | **Đầu mỗi giờ**; sớm hơn bằng **`SchedulingBufferTime`** để task kịp khởi động |
| Xem dự báo | `GetPredictiveScalingForecast` hoặc console |
| Scale in | **Tự nó không scale in** → dùng kèm target tracking/step |
| Nhiều policy | Desired = **MAX** của các policy |
| Max task | Mặc định không vượt; có thể cho **tự tăng max** — ⚠️ max mới **giữ nguyên, không tự giảm** |
| Xoá service tạo lại | Chờ 24 giờ mới có dự báo; tăng tốc bằng custom metric gộp cũ + mới |
| Lỗi "SLR" | Dùng custom role khi `register-scalable-target` → gọi lại **không** có `role-arn` |

Hợp với: traffic có chu kỳ (giờ hành chính), workload bật/tắt lặp lại, **app khởi động lâu**. Predictive chỉ scale **task**; capacity bên dưới do Fargate / ASG capacity provider lo.

---

## 16. Scale theo hàng đợi SQS

> Nguồn: `15-04-06-05`

### 16.1 Vì sao không scale thẳng theo độ sâu hàng đợi

`ApproximateNumberOfMessagesVisible` **không tỉ lệ** với số task: số task cần còn phụ thuộc thời gian xử lý 1 message và độ trễ chấp nhận được. Target tracking theo queue depth sẽ hiểu "hàng đợi lớn" = "cần tỉ lệ thuận nhiều task" → **scale out quá mức, tốn tiền**.

### 16.2 Backlog per task

```
backlog per task (đang có)      = số message trong hàng đợi ÷ số task đang chạy
TargetValue (backlog chấp nhận) = độ trễ chấp nhận được ÷ thời gian xử lý 1 message
```

| Bước | Phép tính | Kết quả |
|---|---|---|
| Mục tiêu | 10 s ÷ 0,1 s | **100** message/task |
| Hiện tại | 1.500 message ÷ 10 task | **150** (> 100) |
| Sau khi thêm 5 task | 1.500 ÷ 15 | **100** (= mục tiêu) |

### 16.3 Cấu hình (metric math, không cần code tự đẩy metric)

```
1. Quyền IAM
2. BẬT Container Insights cho cluster (để có RunningTaskCount)  ⚠️ thiếu là không scale
3. register-scalable-target   ecs:service:DesiredCount, min 1, max 10
4. config.json: m1 = ApproximateNumberOfMessagesVisible (AWS/SQS, Sum)
                m2 = RunningTaskCount (ECS/ContainerInsights, Average)
                e1 = m1 / m2 (ReturnData: true), TargetValue: 100
5. put-scaling-policy --policy-type TargetTrackingScaling → tự tạo 2 alarm (out, in)
```

```json
{
  "CustomizedMetricSpecification": {
    "Metrics": [
      { "Id": "m1", "ReturnData": false, "Label": "queue depth",
        "MetricStat": { "Stat": "Sum", "Metric": { "Namespace": "AWS/SQS", "MetricName": "ApproximateNumberOfMessagesVisible",
          "Dimensions": [{ "Name": "QueueName", "Value": "my-queue" }] } } },
      { "Id": "m2", "ReturnData": false, "Label": "running tasks",
        "MetricStat": { "Stat": "Average", "Metric": { "Namespace": "ECS/ContainerInsights", "MetricName": "RunningTaskCount",
          "Dimensions": [{ "Name": "ClusterName", "Value": "my-cluster" }, { "Name": "ServiceName", "Value": "my-service" }] } } },
      { "Id": "e1", "Expression": "m1 / m2", "ReturnData": true, "Label": "backlog per task" }
    ]
  },
  "TargetValue": 100
}
```

### 16.4 Step scaling cho hàng đợi (kiểm soát nhiều hơn)

- Bước scale lớn dần theo backlog (theo **%** hoặc **số task cố định**).
- Phản ứng nhanh hơn: metric **độ phân giải cao 10 giây**, alarm vượt với **1 data point**.
- Scale-in thận trọng riêng: vd backlog phải thấp **vài phút** mới bớt task.

### 16.5 Bảo vệ worker đang xử lý message

```
Worker lấy message ─► ProtectionEnabled = true   (scale-in không dừng task này)
                  ─► xử lý
                  ─► ProtectionEnabled = false
                  ─► xoá message
```

- Khuyến nghị task **tự bảo vệ** qua endpoint agent `$ECS_AGENT_URI/task-protection/v1/state` (folder 14: mặc định 2 giờ, `ExpiresInMinutes` 1–2880).
- Message chưa xong khi task bị dừng **quay lại** hàng đợi; task khác xử lý nếu **visibility timeout** phù hợp.
- TargetValue **thấp** → chờ ngắn, nhiều task; **cao** → rẻ hơn, chờ lâu hơn. Chọn theo **SLA**.

🔎 Nếu min = 0: lúc 0 task, `m1 / m2` chia cho 0 → không có dữ liệu. Thường đặt min ≥ 1, hoặc thêm một step scaling policy theo `m1 > 0` để đánh thức service.

---

## 17. Kết nối các service với nhau: chọn cách nào

> Nguồn: `15-04-07-00`, `15-04-00` mục 4

| Nhu cầu | Khuyến nghị |
|---|---|
| Kết nối từ **internet** | Elastic Load Balancing (hoặc ECS Gateway, folder 12) |
| **ECS service ↔ ECS service** | Service Connect ⭐, service discovery, hoặc VPC Lattice — **không cần load balancer** |

| | **Service Connect** | **Service discovery (Cloud Map DNS)** | **VPC Lattice** |
|---|---|---|---|
| Cơ chế | Proxy (Envoy) trong mỗi task, tên ngắn + port chuẩn | Route 53 DNS → IP task trực tiếp | Dịch vụ mạng ứng dụng được quản lý, target group IP |
| Đổi đích | Theo **deployment** của client | Chờ **TTL** của DNS | Theo listener rule / target group |
| Retry, bỏ backend hỏng | Proxy lo (retry 2 lần, outlier detection) | **App tự viết** | Health check của Lattice; ECS thay task unhealthy |
| Độ trễ | Thêm một hop proxy cục bộ | **Thấp nhất**, không trung gian | Qua Lattice |
| Phạm vi | Cùng namespace, cùng Region (khác cluster/VPC/account nếu chia sẻ) | Trong VPC | **Nhiều VPC và account** |
| Giới hạn | Proxy tốn CPU/RAM của task | **1.000 task / service** | ≤ 5 target group / service |
| Phí | **Không** phí riêng (TLS tốn Private CA) | Route 53 + Cloud Map API | **Có phí** |

**Tương thích network mode** (client = app tạo kết nối trong task):

| | Bridged | `awsvpc` | Host |
|---|---|---|---|
| Service discovery | ⚠️ cần client hiểu **SRV** khi không có `hostPort` | ✅ | ⚠️ cần SRV khi không có `hostPort` |
| Service Connect | ✅ | ✅ | ❌ |
| VPC Lattice | ✅ | ✅ | ✅ |

Chỉ `awsvpc` dùng được cả ba không kèm điều kiện → lý do chuẩn hoá `awsvpc` (folder 13).

---

## 18. Service Connect (tóm lược)

> Nguồn: `15-04-07-01-00` → `15-04-07-01-07`. **Chi tiết đầy đủ:** [`15-04-07-01-99-ecs-service-connect-toan-tap.md`](15-04-07-01-99-ecs-service-connect-toan-tap.md)

- **Là gì:** ECS service gọi nhau bằng **tên ngắn** (vd `http://user:8080`); vừa service discovery vừa service mesh gọn nhẹ, cấu hình hoàn toàn bằng task definition + service.
- **Cơ chế:** ECS chèn **proxy sidecar (Envoy)** vào mỗi task; danh bạ là **AWS Cloud Map namespace** (mặc định HTTP namespace). App tìm proxy qua `/etc/hosts` được sinh lúc task khởi động 🔎.
- **Thuật ngữ:** `portMappings[].name` (port name, bắt buộc với server), `appProtocol` (`http`/`http2`/`grpc`), `discoveryName`, `clientAliases` (`port`, `dnsName`), `ingressPortOverride`. Service **client** (chỉ gọi) vs **client-server** (có `services[]`).
- **Proxy mặc định:** round-robin; outlier detection (≥ 5 lỗi / 30 s → tránh 30–300 s); retry **2** lần tránh host lỗi; timeout **15 s**; `idleTimeout` HTTP 5 phút / TCP 1 giờ; `perRequestTimeout` 15 s.
- **Quy tắc vàng:** task client **hiện có** không thấy endpoint mới → **deploy lại client**. Thứ tự: backend (client-server) trước, frontend sau.
- **Zone-aware routing:** bật mặc định, > 80% traffic ở lại cùng AZ; cần ≥ **2 × số AZ** endpoint đích.
- **TLS:** AWS Private CA, chỉ `awsvpc`.
- **Không hỗ trợ:** `host` mode, Windows, standalone task, blue/green CodeDeploy, ECS Anywhere.
- **Tài nguyên:** cấp thêm cho proxy khoảng 256 CPU unit và 64 MiB.

---

## 19. Service discovery (Cloud Map DNS)

> Nguồn: `15-04-07-02-00`, `15-04-07-02-01`, `15-04-07-00` mục 2

### 19.1 Khái niệm

```
Service discovery namespace   (vd example.com — tên miền chung; private DNS → Route 53 private hosted zone)
 └─ Service discovery service (tên service + cấu hình DNS; cung cấp service registry)
     └─ Service discovery instance (mỗi task: IP, port, AZ, Region, tên service/cluster…)
ECS health check mức container: endpoint không đạt → gỡ khỏi DNS, đánh dấu unhealthy
```

Thuộc tính của instance: `AWS_INSTANCE_IPV4` (bản ghi A), `AWS_INSTANCE_IPV6` (AAAA), `AWS_INSTANCE_PORT`, `AVAILABILITY_ZONE` (EC2: AZ instance; Fargate: AZ ENI), `REGION`, `ECS_SERVICE_NAME`, `ECS_CLUSTER_NAME`, `EC2_INSTANCE_ID` (không có với Fargate), `ECS_TASK_DEFINITION_FAMILY`, `ECS_TASK_SET_EXTERNAL_ID` (external deployment).

### 19.2 Bản ghi DNS theo network mode

| Network mode | `A` | `AAAA` | `SRV` |
|---|---|---|---|
| `awsvpc` (IPv4) | ✅ | — | ✅ (cần port) |
| `awsvpc` dualstack | ✅ | ✅ | ✅ |
| `awsvpc` IPv6-only | ❌ | ✅ | ✅ |
| `bridge`, `host` | ❌ | ❌ | ✅ **duy nhất** (tên container + container port) |
| `none` | Không hỗ trợ | | |

### 19.3 Hành vi và giới hạn

| Điểm | Nội dung |
|---|---|
| Tên truy vấn | `<service-discovery-service-name>.<namespace>`, chỉ **trong VPC** |
| IP trả về | Luôn **IP private** của task (kể cả public namespace) |
| ≤ 8 bản ghi healthy | Trả **mọi** bản ghi healthy |
| Mọi bản ghi unhealthy | Trả **tối đa 8** bản ghi unhealthy |
| Đăng ký | `UNHEALTHY` → health check đạt → `HEALTHY`; fail → **huỷ đăng ký** |
| Health check | Khuyến nghị **`HealthCheckCustomConfig`** (ECS quản lý, miễn phí) |
| Fargate | Platform ≥ **1.1.0** |
| Task / service | Tối đa **1.000** (quota Route 53) — ⚠️ không tăng được |
| Console Create Service | Chỉ **private DNS namespace** |
| Namespace chia sẻ | ❌ không hỗ trợ |
| VPC | Phải bật **DNS support / DNS hostnames** |
| Sau load balancer | Được, nhưng traffic discovery đi **thẳng tới task** |
| Classic Load Balancer | ❌ |
| Dọn tài nguyên Cloud Map | **Thủ công** |
| Phí | Route 53 hosted zone + truy vấn Cloud Map; health check container miễn phí; health check mạng thêm cho task public tính phí |

⚠️ Nhược điểm chính: **TTL** → client có thể phân giải ra task **đã chết**; app phải tự **retry** và bỏ qua backend hỏng.

### 19.4 Các bước (tutorial của docs)

```bash
# 1. Namespace (private DNS) — tự tạo Route 53 private hosted zone
aws servicediscovery create-private-dns-namespace --name tutorial --vpc <VPC_ID>
aws servicediscovery get-operation --operation-id <OPERATION_ID>        # lấy NAMESPACE ID

# 2. Service discovery service (bản ghi A, TTL 300, health check do ECS quản lý)
aws servicediscovery create-service --name myapplication \
  --dns-config "NamespaceId=<NS_ID>,DnsRecords=[{Type=A,TTL=300}]" \
  --health-check-custom-config FailureThreshold=1                       # lấy ARN

# 3. ECS service trỏ tới registry
#    ecs-service-discovery.json: "serviceRegistries": [{ "registryArn": "<SRV_ARN>" }], awsvpcConfiguration…
aws ecs create-service --cli-input-json file://ecs-service-discovery.json

# 4. Kiểm tra
aws servicediscovery list-instances --service-id <SRV_ID>
aws servicediscovery discover-instances --namespace-name tutorial --service-name myapplication \
  --query-parameters ECS_CLUSTER_NAME=tutorial
dig +short myapplication.tutorial          # từ một instance trong VPC

# 5. Dọn dẹp: deregister-instance, delete-service, delete-namespace (Cloud Map) + xoá ECS service
```

Subnet và security group của ECS service phải thuộc **cùng VPC** với namespace.

---

## 20. Amazon VPC Lattice

> Nguồn: `15-04-07-03-00`, `15-04-07-03-01`

### 20.1 Là gì

- Dịch vụ mạng ứng dụng **được quản lý hoàn toàn**: kết nối, bảo mật, quan sát ứng dụng xuyên **compute, VPC, account** — **không sửa code**.
- ECS service gắn với **target group VPC Lattice**; task là **IP target**, ECS **tự đăng ký** khi task khởi chạy và **tự thay** task unhealthy theo health check của Lattice.

```
Client ─► VPC Lattice service: listener (giao thức + port)
              └─ listener rule (điều kiện path/header/method) ─► target group ─► task ECS (IP target)
```

| Lợi ích | Nội dung |
|---|---|
| Năng suất | Lattice lo mạng, bảo mật, quan sát thống nhất |
| Bảo mật | Xác thực, **mã hoá khi truyền**, **VPC Lattice Auth policy** (IAM) |
| Scale | Định tuyến path/header/method; nhiều cluster, hàng triệu req/s, không tốn tài nguyên workload |
| Hạ tầng hỗn hợp | Nhất quán trên ECS, Fargate, EC2, EKS, Lambda |
| Chéo account | Qua **AWS Resource Access Manager** |

### 20.2 Quan hệ với thành phần khác

| Thành phần | Với VPC Lattice |
|---|---|
| ALB | Không cần ALB riêng làm target group trong Lattice; vẫn dùng ALB song song được |
| Rolling deployment | ✅ |
| ECS blue/green (cả linear, canary) | ✅ — thêm `advancedConfiguration` (alternate target group + listener rule production/test) |
| Blue/green CodeDeploy (`CODE_DEPLOY`) | ❌ |
| ECS Anywhere | ❌ |
| 5 cấu hình Lattice | Deploy có thể **lâu hơn một chút** |

### 20.3 Tạo service

Console: Create service → **Turn on VPC Lattice** → **ECS infrastructure role** → VPC (với `awsvpc` tự chọn theo Networking) → **Target groups 1–5** (Port name, Protocol, Port) → sang console VPC Lattice đưa target group vào **default action** hoặc **rule** của listener.

CLI:

```bash
# tg-config.json: { "ipAddressType": "IPV4", "port": 443, "protocol": "HTTPS", "protocolVersion": "HTTP1", "vpcIdentifier": "<VPC_ID>" }
aws vpc-lattice create-target-group --name my-lattice-tg --type IP --config file://tg-config.json
```

```json
{
  "serviceName": "ecs-service-vpc-lattice",
  "taskDefinition": "ecs-task-def",
  "vpcLatticeConfigurations": [{
    "targetGroupArn": "<LATTICE_TG_ARN>",
    "portName": "testvpclattice",
    "roleArn": "arn:aws:iam::<ACCOUNT_ID>:role/ecsInfrastructureRoleVpcLattice"
  }],
  "desiredCount": 5
}
```

| Điểm | Nội dung |
|---|---|
| `portName` | Phải khớp `portMappings[].name` trong task definition |
| Target group có sẵn | Thêm bằng CLI (`register-targets`) |
| Một target group | Chỉ thuộc **một** service |
| IPv6-only | Target group loại địa chỉ **IPv6** |
| ⚠️ Security group | Phải có **inbound rule cho prefix list `vpc-lattice`**, nếu không task và health check thất bại |
| Health check mặc định | interval 30, timeout 5, healthy 5, unhealthy 2, path `/`, matcher 200 |

---

## 21. Deploy nhanh hơn: các nút chỉnh thời gian

> Nguồn: `15-04-05-01`, `15-04-05-02`, `15-01`, `15-99` (chi tiết ở toàn tập load balancing)

```
t=0     ECS DeregisterTargets → LB ngừng gửi request mới tới task cũ
t=0..D  LB chờ kết nối cũ xong (D = deregistration delay)
t=D     target UNUSED → ECS gửi SIGTERM
t=D..S  app shutdown êm (S ≤ stop timeout)
t=D+S   SIGKILL nếu chưa thoát
```

| Nút | Mặc định | Tăng tốc | Điều kiện |
|---|---|---|---|
| Health check interval × healthy threshold | 30 s × 5 = **150 s** | 5 s × 2 = **10 s** | App khởi động và ổn định < 10 s |
| Deregistration delay | **300 s** | **5 s** | Request < 1 s; không dùng cho upload/stream/WebSocket |
| Stop timeout (`ECS_CONTAINER_STOP_TIMEOUT` / `stopTimeout`) | **30 s** | **2 s** | App xử lý SIGTERM nhanh (đặt = thời gian tắt × 2) |
| `minimumHealthyPercent` / `maximumPercent` | 100 / 200 | Giữ 200% để chạy task mới trước | Cần đủ capacity (reservation thì xem folder 06) |
| Image | — | Image nhỏ, ECR cùng Region, cache image trên EC2 | Folder 05, 09 |

---

## 22. Triển khai từng bước (CLI)

> ✍️ Ví dụ end-to-end cho một hệ thống nhỏ: `api` (service sau ALB, autoscale theo CPU), `worker` (service đọc SQS, autoscale theo backlog), `report` (scheduled task chạy đêm). Ghép từ các mục trên; giá trị là placeholder.

**Bước 0 — Tiên quyết:** cluster có capacity provider `FARGATE` + `FARGATE_SPOT`; VPC với subnet private ≥ 2 AZ; ALB + target group type `ip`; task definition cho `api`, `worker`, `report` (`awsvpc`, có `healthCheck`, app bắt `SIGTERM`); bật **Container Insights** cho cluster.

```bash
aws ecs update-cluster-settings --cluster <CLUSTER> --settings name=containerInsights,value=enabled
```

**Bước 1 — Service `api` sau ALB, trộn on-demand + Spot**

```bash
aws ecs create-service --cluster <CLUSTER> --service-name api \
  --task-definition api:<REV> --desired-count 4 \
  --capacity-provider-strategy capacityProvider=FARGATE,base=2,weight=1 capacityProvider=FARGATE_SPOT,weight=1 \
  --network-configuration 'awsvpcConfiguration={subnets=[<SUBNET_A>,<SUBNET_B>],securityGroups=[<SG_API>]}' \
  --load-balancers targetGroupArn=<TG_ARN>,containerName=api,containerPort=8080 \
  --health-check-grace-period-seconds 60 \
  --deployment-configuration 'minimumHealthyPercent=100,maximumPercent=200,deploymentCircuitBreaker={enable=true,rollback=true}'

aws elbv2 modify-target-group --target-group-arn <TG_ARN> \
  --health-check-interval-seconds 5 --healthy-threshold-count 2
aws elbv2 modify-target-group-attributes --target-group-arn <TG_ARN> \
  --attributes Key=deregistration_delay.timeout_seconds,Value=5
```

**Bước 2 — Auto scaling cho `api` (target tracking CPU 70%)** — lệnh ở mục 15.1, `min 2`, `max 20`.

**Bước 3 — Service `worker` + scale theo backlog** — `register-scalable-target` (min 1, max 10) + `put-scaling-policy` với `config.json` ở mục 16.3. Trong code worker bật/tắt `ProtectionEnabled` quanh mỗi message.

**Bước 4 — Service Connect cho `api` gọi `worker` nội bộ (nếu cần)** — xem mục 9 của toàn tập Service Connect: namespace, `portMappings[].name`, `serviceConnectConfiguration`; deploy `worker` (client-server) **trước**, `api` (client) **sau**.

**Bước 5 — Scheduled task `report`** — lệnh `aws scheduler create-schedule` ở mục 11.4, cron 2 giờ sáng giờ Việt Nam, retry 3, DLQ.

**Bước 6 — Kiểm tra**

```bash
aws ecs describe-services --cluster <CLUSTER> --services api worker \
  --query 'services[].{name:serviceName,running:runningCount,desired:desiredCount,events:events[0:3].message}'
aws application-autoscaling describe-scaling-activities --service-namespace ecs --resource-id service/<CLUSTER>/api
aws elbv2 describe-target-health --target-group-arn <TG_ARN>
aws scheduler get-schedule --name nightly-report
```

---

## 23. Checklist

```
Chọn cách chạy
[ ] Workload lâu dài → service; theo sự kiện → RunTask; theo lịch → EventBridge Scheduler
[ ] Standalone/scheduled task chạy lại được (idempotent, checkpoint), có retry/DLQ

Compute & placement
[ ] Capacity provider strategy (không dùng launch type); base on-demand cho phần bắt buộc
[ ] EC2: strategy spread AZ (+ binpack nếu muốn rẻ); constraint đúng (GPU, ARM, AZ)
[ ] Managed Instances: nhiều subnet cho prod; Fargate: không cần placement
[ ] Không để constraint quá chặt → task kẹt PENDING

Task & lifecycle
[ ] App bắt SIGTERM, stopTimeout đủ; container healthCheck có
[ ] healthCheckGracePeriodSeconds cho app khởi động chậm

Service & load balancer
[ ] desiredCount ≥ 2, trải AZ; deployment circuit breaker + rollback
[ ] Target type ip (awsvpc); health check path nhẹ; interval/threshold hợp lý
[ ] Deregistration delay hợp với độ dài request (300 → 5 nếu request ngắn)

Auto scaling
[ ] Target tracking với metric tương quan tải (CPU, ALBRequestCountPerTarget, backlog/task)
[ ] min/max hợp lý; target 60–80%; cooldown out ngắn, in dài
[ ] Worker SQS: Container Insights bật, metric math m1/m2, task scale-in protection
[ ] Predictive: chạy forecast only trước; cẩn thận "tự tăng max"

Service ↔ service
[ ] Mặc định Service Connect; DNS discovery khi cần độ trễ thấp nhất và app tự retry
[ ] Cloud Map: ≤ 1.000 task/service; dọn tài nguyên thủ công
[ ] VPC Lattice khi cần chéo account/VPC; SG mở prefix vpc-lattice
```

---

## 24. Troubleshooting

> 🔎 Tổng hợp từ các mục trên; không phải danh sách đầy đủ.

| Triệu chứng | Nguyên nhân có thể | Cách xử lý |
|---|---|---|
| Task kẹt `PROVISIONING` | Thiếu capacity (đang chờ managed scaling), subnet hết IP cho ENI | Kiểm tra capacity provider, IP trống của subnet |
| Task kẹt `PENDING` | Không instance nào đủ CPU/memory/port hoặc thoả constraint | Nới constraint, thêm capacity, giảm `cpu`/`memory` |
| Kẹt `ACTIVATING` / `CannotPullContainerError` | Không ra được ECR (NAT, VPC endpoint), thiếu quyền execution role | Sửa route/endpoint, quyền ECR |
| Task start → fail → stop liên tục | Health check target group sai path/port/SG; app khởi động chậm | Sửa health check, thêm grace period; xem service events (throttle) |
| Service event "unable to place a task" | Constraint/strategy hoặc thiếu tài nguyên | `describe-container-instances` xem tài nguyên còn trống |
| Deploy rất chậm | Deregistration delay 300 s, health check 30 s × 5, stop timeout 30 s | Chỉnh theo mục 21 |
| Mất request khi deploy | App không bắt SIGTERM; draining quá ngắn cho request dài | Graceful shutdown, tăng deregistration delay |
| Desired count tự đổi sau khi đặt tay | Scaling policy đang hoạt động ghi đè | Sửa min/max hoặc tạm dừng scaling |
| Không scale in | Một policy khác chưa muốn scale in; đang deploy; cooldown | Kiểm tra từng policy, scaling activities |
| Không scale (target tracking) | Metric thiếu dữ liệu | Kiểm tra metric có data point |
| SQS scaling không chạy | Container Insights tắt → không có `RunningTaskCount`; 0 task | Bật Container Insights; min ≥ 1 |
| Worker xử lý trùng / mất tiến độ khi scale in | Không dùng task scale-in protection | Bật `ProtectionEnabled` quanh mỗi message |
| `DEPLOYMENT_BLOCKED` | Quá nhiều task đang được protection | Tắt protection khi xong, chỉnh max percent |
| Client gọi vào task đã chết (DNS) | TTL cache | Retry ở app, giảm TTL, hoặc chuyển sang Service Connect |
| Service discovery không phân giải | VPC chưa bật DNS support/hostnames; truy vấn ngoài VPC | Bật thuộc tính DNS của VPC |
| Không tạo thêm task với service discovery | Đã chạm **1.000 task/service** | Tách service hoặc chuyển Service Connect |
| Client Service Connect không thấy service mới | Task client cũ | Deploy lại client |
| VPC Lattice health check fail | SG thiếu inbound prefix `vpc-lattice`; `portName` không khớp | Mở SG, sửa `portName` |
| Scheduled task không chạy | Schedule disabled; role Scheduler thiếu `ecs:RunTask`/`iam:PassRole`; subnet/SG sai | Kiểm tra Status, role, DLQ |
| Spot task biến mất, không có task thay | Spot hết capacity, không tự sang on-demand | Tăng `base` on-demand |

---

## 25. Chỗ docs có lỗi / không khớp

| Chủ đề | Chỗ không khớp | Nên hiểu |
|---|---|---|
| LB cho service | `15-04-00` mục 3: câu dẫn nói service "chạy trên Fargate", nhưng bảng ghi ALB hỗ trợ cả Fargate lẫn EC2 | LB dùng được cho cả hai |
| Desired > max | `15-04-06-00`: "scales the desired count **out** to the maximum" | Ý là **đưa về** max |
| Vòng đời task | Mô tả hình `15-01` chỉ có 6 trạng thái, mô tả chi tiết có 9 | Dùng danh sách 9 trạng thái |
| Placement trên Managed Instances | `06-02`/`14-01-00` nói default CP xét `placementStrategy`; `15-02-00` nói Managed Instances **không** có strategy | Không dựa vào strategy trên Managed Instances |
| GWLB | Ví dụ dynamic port (instance + port) trong khi GWLB chỉ hỗ trợ `awsvpc` + `ip` | Thực tế: `awsvpc` + `ip` |
| Tên thuộc tính Cloud Map | `AWS_INSTANCE_IPV4` vs `AWS_INSTANCE_IPv4` viết lẫn | Dùng chữ hoa như API |
| Tutorial service discovery | Output `list-instances` thiếu dấu phẩy; ID namespace lúc xoá khác ID dùng xuyên suốt | Lỗi ví dụ, không ảnh hưởng cách làm |
| VPC Lattice CLI | `--serviceName` (camelCase) dư thừa; Region trong ARN `us-east-2` vs `us-west-2`; nhắc mode `network` không giải thích | Dùng `--cli-input-json` với `serviceName` trong JSON |
| Predictive | Lỗi đánh máy "predicitive", "wait 24 hours for before" | Không ảnh hưởng nghĩa |

---

## 26. Câu hỏi tự kiểm tra (có đáp án)

**1. Ba cách chạy workload trên ECS là gì, chọn thế nào?**
<details><summary>Đáp án</summary>

**Service** cho workload lâu dài cần giữ N task và tự thay task hỏng; **standalone task** (`RunTask`) cho việc chạy một lần theo sự kiện; **scheduled task** (EventBridge Scheduler) cho việc định kỳ theo rate/cron/one-time.
</details>

**2. "Shared state, optimistic concurrency" có ý nghĩa thực tế gì?**
<details><summary>Đáp án</summary>

Scheduler dùng **cùng trạng thái cluster** với ECS API, nên dữ liệu từ `describe-*` chính là thứ scheduler dùng để đặt task; nhiều scheduler cùng ra quyết định mà không khoá cả cluster.
</details>

**3. Strategy `FARGATE` base 2 weight 1 + `FARGATE_SPOT` weight 4, chạy 12 task. Chia thế nào?**
<details><summary>Đáp án</summary>

2 task base → FARGATE; 10 còn lại chia 1:4 → FARGATE 2, SPOT 8. Tổng **FARGATE 4, FARGATE_SPOT 8**.
</details>

**4. Có thể đặt `base` cho cả hai provider trong một strategy không?**
<details><summary>Đáp án</summary>

Không. **Chỉ một** capacity provider trong strategy được có `base`.
</details>

**5. Muốn chạy trên Managed Instances thì chọn launch type hay capacity provider strategy?**
<details><summary>Đáp án</summary>

**Bắt buộc** capacity provider strategy.
</details>

**6. Cluster không có strategy mặc định, không có capacity provider nào. Console chọn compute gì?**
<details><summary>Đáp án</summary>

**Fargate launch type**.
</details>

**7. `lastStatus` và `desiredStatus` khác gì? Task có `desiredStatus=RUNNING`, `lastStatus=PENDING` lâu nghĩa là gì?**
<details><summary>Đáp án</summary>

`lastStatus` là trạng thái thực tế gần nhất, `desiredStatus` là trạng thái ECS muốn tới. Kẹt `PENDING` nghĩa là task đang **chờ đủ tài nguyên** (CPU/memory/constraint).
</details>

**8. Ở trạng thái nào task đăng ký vào target group, và ở trạng thái nào huỷ đăng ký?**
<details><summary>Đáp án</summary>

Đăng ký ở **`ACTIVATING`**; huỷ đăng ký ở **`DEACTIVATING`**.
</details>

**9. Mô tả chuỗi dừng container trên Linux.**
<details><summary>Đáp án</summary>

Agent gửi `STOPSIGNAL` (mặc định `SIGTERM`), chờ `stopTimeout`, còn sống thì gửi `SIGKILL`.
</details>

**10. Trạng thái `DELETED` thấy ở đâu?**
<details><summary>Đáp án</summary>

Chỉ trong **`describe-tasks`**, không hiện trên console.
</details>

**11. Strategy và constraint khác nhau ở điểm cốt lõi nào?**
<details><summary>Đáp án</summary>

Strategy là **best effort** (không thoả vẫn đặt được); constraint là **bắt buộc** (không instance nào thoả thì task ở `PENDING`).
</details>

**12. Placement mặc định của service và của standalone task là gì?**
<details><summary>Đáp án</summary>

Service: `spread` theo `attribute:ecs.availability-zone`. Standalone task: **ngẫu nhiên**, không có constraint mặc định.
</details>

**13. `binpack` theo memory khi scale in dừng task nào?**
<details><summary>Đáp án</summary>

Task trên instance còn **nhiều tài nguyên nhất** sau khi dừng (để dồn task lại, giải phóng instance).
</details>

**14. Viết strategy trải đều AZ rồi dồn memory trong mỗi AZ.**
<details><summary>Đáp án</summary>

`[{ "type": "spread", "field": "attribute:ecs.availability-zone" }, { "type": "binpack", "field": "memory" }]` — áp theo thứ tự.
</details>

**15. Fargate có hỗ trợ placement strategy/constraint không? Managed Instances thì sao?**
<details><summary>Đáp án</summary>

Fargate: **không** cả hai, tự trải AZ. Managed Instances: không strategy; constraint chỉ `distinctInstance` và `memberOf` với `subnet-id`, `availability-zone`, `instance-type`, `cpu-architecture`.
</details>

**16. Task group mặc định của service và standalone task là gì? Đổi được không?**
<details><summary>Đáp án</summary>

Service: **tên service** (không đổi được). Standalone: `family:<tên task definition>`. Không sửa group sau khi launch; mỗi task đúng một group, tên ≤ 255 ký tự.
</details>

**17. Vì sao `distinctInstance` đôi khi vẫn thấy 2 task `RUNNING` trên một instance?**
<details><summary>Đáp án</summary>

Vì nó dựa vào **desired status** (task cũ đang dừng vẫn có thể `RUNNING`). Cần cô lập mạnh thật sự thì dùng Fargate.
</details>

**18. Gắn thuộc tính custom `stack=prod` rồi chỉ đặt task lên máy đó thế nào?**
<details><summary>Đáp án</summary>

`aws ecs put-attributes ... name=stack,value=prod,targetType=container-instance,targetId=<ARN>`, rồi constraint `memberOf` với `attribute:stack == prod`.
</details>

**19. Trên Managed Instances, muốn bin packing chặt thì cấu hình gì? Có nên dùng cho production không?**
<details><summary>Đáp án</summary>

Capacity provider **một subnet**. Không nên cho prod — trải AZ (nhiều subnet) là mặc định để sẵn sàng cao.
</details>

**20. Trường Public IP khi Run task dùng được cho nền tảng nào? EBS volume lúc chạy mặc định có bị xoá không?**
<details><summary>Đáp án</summary>

Public IP chỉ **Fargate**. EBS volume mặc định **xoá khi task kết thúc**; size 1–16.384 GiB, cần infrastructure role.
</details>

**21. Nút "Stop all" trên tab Tasks nguy hiểm ở điểm nào?**
<details><summary>Đáp án</summary>

Dừng **mọi** task, kể cả task của service; phải gõ `Stop all tasks` để xác nhận.
</details>

**22. EventBridge Scheduler: giới hạn retry là bao nhiêu? Có những kiểu lịch nào?**
<details><summary>Đáp án</summary>

Retry ≤ **24 giờ** và ≤ **185 lần**, có DLQ SQS. Kiểu lịch: **rate**, **cron** (mọi múi giờ), **one-time**.
</details>

**23. Task bị đánh dấu unhealthy, `maximumPercent` không cho chạy thêm task. Scheduler làm gì?**
<details><summary>Đáp án</summary>

Dừng **ngẫu nhiên từng task unhealthy** để giải phóng capacity rồi mới khởi động task thay thế, lặp tới khi hết task unhealthy.
</details>

**24. Throttle logic của service scheduler là gì?**
<details><summary>Đáp án</summary>

Task liên tục dừng **trước khi vào `RUNNING`** → scheduler giãn các lần thử và gửi service event; update service thì quay lại bình thường.
</details>

**25. Trong lúc deploy, auto scaling hành xử thế nào? Muốn tạm dừng scale-out thì làm sao?**
<details><summary>Đáp án</summary>

Scale-in **tắt**, scale-out **vẫn chạy** (không áp dụng external controller). Tạm dừng: `register-scalable-target` với `DynamicScalingInSuspended=true`, `DynamicScalingOutSuspended=true`, deploy xong thì bật lại.
</details>

**26. Service Auto Scaling dùng gì làm điểm xuất phát khi scale? Vì sao?**
<details><summary>Đáp án</summary>

**Số task đang chạy thực tế**, không phải desired count — tránh scale quá mức khi không đủ tài nguyên để đặt task.
</details>

**27. Hai target tracking policy CPU và memory: CPU cao, memory thấp. Chuyện gì xảy ra? CPU thấp, memory chưa thấp?**
<details><summary>Đáp án</summary>

CPU cao → **scale out** (bất kỳ policy nào muốn out). CPU thấp nhưng memory chưa thấp → **không scale in** (cần tất cả policy đồng ý).
</details>

**28. Vì sao không nên scale worker SQS trực tiếp theo `ApproximateNumberOfMessagesVisible`?**
<details><summary>Đáp án</summary>

Độ sâu hàng đợi **không tỉ lệ** với số task; target tracking sẽ scale out quá mức. Dùng **backlog per task** = queue depth ÷ running tasks.
</details>

**29. Độ trễ chấp nhận 10 s, xử lý 1 message 0,1 s, hàng đợi 1.500 message, đang 10 task. TargetValue là bao nhiêu và cần thêm bao nhiêu task?**
<details><summary>Đáp án</summary>

TargetValue = 10 ÷ 0,1 = **100**. Hiện 150/task → thêm **5 task** (1.500 ÷ 15 = 100).
</details>

**30. Metric `RunningTaskCount` cần điều kiện gì? Thiếu thì sao?**
<details><summary>Đáp án</summary>

Bật **CloudWatch Container Insights** cho cluster. Thiếu thì biểu thức không có số chia → **không scale**.
</details>

**31. Predictive scaling cần bao nhiêu dữ liệu, dự báo bao xa, có tự scale in không?**
<details><summary>Đáp án</summary>

Tối thiểu 24 giờ (lý tưởng 2 tuần), phân tích tối đa 14 ngày, dự báo theo giờ cho **48 giờ**, cập nhật mỗi 6 giờ. **Không tự scale in** → dùng kèm dynamic scaling.
</details>

**32. Rủi ro của tuỳ chọn cho phép predictive scaling tự tăng max là gì?**
<details><summary>Đáp án</summary>

Max mới **giữ nguyên và không tự giảm** tới khi bạn sửa tay → có thể chạy nhiều task hơn dự định.
</details>

**33. Scheduled action và scaling policy dùng chung thì quan hệ thế nào?**
<details><summary>Đáp án</summary>

Scheduled action đặt khoảng **[min, max]** cho thời điểm đó; policy tiếp tục scale nhưng luôn nằm trong khoảng ấy.
</details>

**34. So sánh Service Connect và service discovery DNS về việc đổi đích và xử lý lỗi.**
<details><summary>Đáp án</summary>

Service Connect: đổi đích theo **deployment** của client, proxy lo retry/outlier detection. DNS: đổi đích chờ **TTL**, app tự viết retry và bỏ qua backend hỏng; bù lại độ trễ thấp nhất.
</details>

**35. Với mode `bridge`, service discovery cần loại bản ghi gì và vì sao?**
<details><summary>Đáp án</summary>

**SRV** (IP + port), vì nhiều container chung IP và dynamic port; bản ghi `A` không đủ. Client phải hỗ trợ SRV.
</details>

**36. Service discovery có giới hạn task nào? Có đăng ký vào namespace chia sẻ được không?**
<details><summary>Đáp án</summary>

Tối đa **1.000 task/service** (quota Route 53). **Không** hỗ trợ namespace Cloud Map chia sẻ.
</details>

**37. Network mode nào không dùng được Service Connect?**
<details><summary>Đáp án</summary>

**`host`**.
</details>

**38. Tạo service với VPC Lattice: `portName` phải khớp gì, security group cần gì?**
<details><summary>Đáp án</summary>

Khớp `portMappings[].name` trong task definition. Security group cần **inbound rule cho prefix list `vpc-lattice`**, nếu không task và health check thất bại.
</details>

**39. VPC Lattice có dùng được với blue/green CodeDeploy không?**
<details><summary>Đáp án</summary>

Không. Rolling và **ECS** blue/green (kể cả linear/canary) được; `CODE_DEPLOY` và ECS Anywhere không.
</details>

**40. Ba nút làm deploy chậm nhất mặc định là gì và giá trị tăng tốc?**
<details><summary>Đáp án</summary>

Health check 30 s × 5 → 5 s × 2; deregistration delay 300 s → 5 s (request ngắn); stop timeout 30 s → 2 s (app tắt nhanh).
</details>

---

## 27. Bản đồ nguồn

| Mục | File nguồn |
|---|---|
| 1, 2, 3 | `15-00-ecs-schedule-containers.md`, `15-04-00-ecs-services.md`, `15-99-thuyet-trinh-cto.md` |
| 4 | `15-01-ecs-task-lifecycle.md`, `15-03-00-ecs-standalone-tasks.md` |
| 5 | `15-02-00-ecs-task-placement.md` |
| 6 | `15-02-02-ecs-task-placement-strategies.md`, `15-03-01` (mẫu console) |
| 7 | `15-02-03-ecs-task-groups.md` |
| 8 | `15-02-04-ecs-task-placement-constraints.md` |
| 9 | `15-02-01-ecs-managed-instances-auto-scaling-placement.md` |
| 10 | `15-03-00`, `15-03-01-ecs-run-standalone-task.md`, `15-03-03-ecs-stop-task.md` |
| 11 | `15-03-02-ecs-eventbridge-scheduler-tasks.md` |
| 12 | `15-04-00-ecs-services.md` |
| 13, 21 | `15-04-05-00` → `15-04-05-06`, `15-04-05-99-ecs-load-balancing-toan-tap.md` |
| 14 | `15-04-06-00-ecs-service-auto-scaling.md` (+ folder 05 cho chọn metric) |
| 15 | `15-04-06-01` → `15-04-06-04` |
| 16 | `15-04-06-05-ecs-service-autoscaling-sqs-queue.md` (+ folder 14 task protection) |
| 17 | `15-04-07-00-ecs-interconnect-services.md` |
| 18 | `15-04-07-01-00` → `15-04-07-01-07`, `15-04-07-01-99-ecs-service-connect-toan-tap.md` |
| 19 | `15-04-07-02-00-ecs-service-discovery.md`, `15-04-07-02-01-ecs-create-service-with-service-discovery.md` |
| 20 | `15-04-07-03-00-ecs-vpc-lattice.md`, `15-04-07-03-01-ecs-vpc-lattice-create-service.md` |
| 22, 23, 24 | ✍️ / 🔎 tổng hợp |

Liên quan: `15-99-thuyet-trinh-cto.md` (slide cho CTO), các file toàn tập *Architect* (`05-98`), *Managed Instances* (`06-98`), *Fargate* (`08-98`), *Task definition* (`13-98`).

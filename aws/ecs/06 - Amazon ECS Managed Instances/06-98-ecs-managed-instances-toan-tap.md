# Amazon ECS Managed Instances — Toàn tập

> Tổng hợp từ **toàn bộ folder `06 - Amazon ECS Managed Instances`** (`06-00` → `06-10`, `06-99`), bổ sung các phần về Managed Instances đã có ở folder 13 (task definition, networking), 14 (cluster, capacity provider, task protection), 15 (placement, auto scaling) và 07 (Managed Daemons). Cùng cách làm với các file toàn tập trước.
>
> **Ký hiệu**
> - Không ký hiệu = lấy từ docs AWS đã dịch trong folder (có ghi file nguồn ở đầu mỗi mục).
> - 🔎 = kiến thức ngoài folder hoặc suy luận của mình — **nên tự kiểm tra lại** với docs AWS hiện tại.
> - ✍️ = ví dụ tự đặt (tên, giá trị, ID là placeholder).
> - ⚠️ = bẫy hay gặp.

---

## Mục lục

0. [Tóm tắt một trang](#0-tóm-tắt-một-trang)
1. [Managed Instances là gì, giải quyết vấn đề gì](#1-managed-instances-là-gì-giải-quyết-vấn-đề-gì)
2. [So sánh EC2 – Managed Instances – Fargate](#2-so-sánh-ec2--managed-instances--fargate)
3. [Khi nào dùng, khi nào không](#3-khi-nào-dùng-khi-nào-không)
4. [Các thành phần: cluster, capacity provider, launch template, role](#4-các-thành-phần-cluster-capacity-provider-launch-template-role)
5. [Cách ECS chọn instance type](#5-cách-ecs-chọn-instance-type)
6. [Instance type được hỗ trợ và hình thức mua](#6-instance-type-được-hỗ-trợ-và-hình-thức-mua)
7. [Best practice chọn instance](#7-best-practice-chọn-instance)
8. [Đặt task và auto scaling](#8-đặt-task-và-auto-scaling)
9. [Tự tối ưu hạ tầng: idle, underutilized, ScaleInAfter](#9-tự-tối-ưu-hạ-tầng-idle-underutilized-scaleinafter)
10. [Vòng đời instance 14–21 ngày và patching](#10-vòng-đời-instance-1421-ngày-và-patching)
11. [Task definition cho Managed Instances](#11-task-definition-cho-managed-instances)
12. [Networking](#12-networking)
13. [Storage: EBS data volume và instance store](#13-storage-ebs-data-volume-và-instance-store)
14. [Image pull và cache](#14-image-pull-và-cache)
15. [Bảo mật](#15-bảo-mật)
16. [Tuân thủ: FIPS, CMK, VPC Encryption Controls](#16-tuân-thủ-fips-cmk-vpc-encryption-controls)
17. [Organization controls: SCP và Allowed AMIs](#17-organization-controls-scp-và-allowed-amis)
18. [GPU auto repair](#18-gpu-auto-repair)
19. [Managed Daemons và task scale-in protection](#19-managed-daemons-và-task-scale-in-protection)
20. [Giám sát, giá, quota](#20-giám-sát-giá-quota)
21. [Vận hành capacity provider: tạo, cập nhật, xoá](#21-vận-hành-capacity-provider-tạo-cập-nhật-xoá)
22. [Triển khai từng bước (CLI)](#22-triển-khai-từng-bước-cli)
23. [Migrate từ Fargate và từ EC2](#23-migrate-từ-fargate-và-từ-ec2)
24. [Checklist](#24-checklist)
25. [Troubleshooting](#25-troubleshooting)
26. [Chỗ docs có lỗi / không khớp](#26-chỗ-docs-có-lỗi--không-khớp)
27. [Câu hỏi tự kiểm tra (có đáp án)](#27-câu-hỏi-tự-kiểm-tra-có-đáp-án)
28. [Bản đồ nguồn](#28-bản-đồ-nguồn)

---

## 0. Tóm tắt một trang

| Chủ đề | Điều cần nhớ |
|---|---|
| Là gì | Compute **được quản lý hoàn toàn**: chạy container trên **toàn bộ dải EC2 instance type**, AWS lo **cấp phát, scale, patch, bảo trì** |
| Vị trí | Nằm **giữa** EC2 tự quản lý và Fargate |
| OS / CPU | Chỉ **Linux**, AMI **Bottlerocket** do AWS quản lý; `X86_64` hoặc `ARM64` |
| Chọn instance | **Mặc định** (rẻ nhất vừa task) · **theo thuộc tính** (`instanceRequirements`) · **type cụ thể** (`allowedInstanceTypes`, wildcard) |
| Mua | On-Demand (mặc định), **Spot**, **Capacity Reservations**; Savings Plans/RI tự áp dụng |
| Đặt task | Mặc định **nhiều task/instance**; **trải AZ** ưu tiên hơn bin packing; không có placement strategy; constraint chỉ 4 thuộc tính |
| Tự tối ưu | **Idle** detection (xoá instance trống) + **underutilized** detection (gom task của service); `ScaleInAfter` 0–3600 s, `-1` = tắt |
| Vòng đời | Drain từ **ngày 14**, terminate chậm nhất **ngày 21**; thay instance chứ không vá tại chỗ; **EC2 event window** chọn giờ bảo trì |
| Task definition | `requiresCompatibilities` có `MANAGED_INSTANCES`; tương thích Fargate platform **1.4.0**; network mode **bắt buộc** (`awsvpc`/`host`) |
| Storage | EBS data volume **30–16.384 GiB** (mặc định **80**), chứa cả image; hoặc **instance store** (RAID 0, tạm thời, miễn phí) |
| Image | Cache trên instance; **xoá sau 15 phút** không còn task dùng; tag → digest làm lệch bin packing |
| Bảo mật | Không SSH (dùng **ECS Exec**), root FS bất biến, **SELinux**; AWS không đăng nhập instance; **single-task mode** để cô lập mức VM |
| Đặc quyền | `CAP_NET_ADMIN`, `CAP_SYS_ADMIN`, `CAP_BPF`, `CAP_PERFMON` — phải **bật trên capacity provider** |
| IAM | **Infrastructure role** (ECS quản lý EC2) + **instance profile** (agent, Docker) + task role + execution role |
| GPU | **DCGM** theo dõi 18 mã **Xid**; auto repair bật mặc định; tối đa 20% instance drain cùng lúc |
| Giá | Trả **cả instance + phí quản lý** |
| ⚠️ Bẫy lớn nhất | Capacity Reservation vừa đủ + deploy mặc định (max 200%) → **deploy kẹt**; task chạy > 14 ngày; SCP giới hạn AMI chặn launch; EC2 ẩn managed instance từ 22/4/2026 |

---

## 1. Managed Instances là gì, giải quyết vấn đề gì

> Nguồn: `06-00`, `06-99`

**Định nghĩa.** Amazon ECS Managed Instances là lựa chọn compute **được quản lý hoàn toàn**, chạy container trên **toàn bộ dải EC2 instance type**, trong khi **AWS lo hạ tầng**: cấp phát, scale, vá lỗi, bảo trì. Bạn đóng gói ứng dụng thành container và khai báo yêu cầu compute; AWS chọn instance general-purpose **tối ưu chi phí nhất**, hoặc bạn tự khai báo thuộc tính (instance type, hãng CPU, accelerator).

**Vấn đề nó giải quyết** — khoảng trống giữa hai lựa chọn cũ:

| Lựa chọn cũ | Điểm mạnh | Điểm đau |
|---|---|---|
| EC2 tự quản lý | Chọn được mọi phần cứng (GPU, Graviton, mạng cao, instance store), nhiều task/instance | Phải tự patch AMI, quản lý ASG, agent, drain máy |
| Fargate | Không quản lý server | Không chọn được phần cứng, không GPU, không capability đặc quyền, trả tiền theo từng task |
| **Managed Instances** | **Phần cứng như EC2 + vận hành gần như Fargate** | Trả thêm **phí quản lý**; instance bị thay định kỳ; không custom AMI, không SSH |

```
EC2 tự quản lý  ←──────  Managed Instances  ──────→  Fargate
(tự lo AMI, patch,        (chọn được instance type,     (không chọn instance,
 scale, toàn quyền)        AWS lo AMI/patch/scale)       mỗi task cô lập)
```

**Năng lực chính** (`06-00` mục 6):

| Năng lực | Nội dung |
|---|---|
| Chọn instance type cụ thể | GPU, CPU đặc thù, memory lớn, mạng hiệu năng cao |
| Nhiều task trên một instance | Mặc định — tận dụng tài nguyên, task khởi động nhanh hơn |
| Tuân thủ, vá định kỳ | ECS drain instance từ ngày 14, tự chuyển task của service sang instance mới |
| Capability đặc quyền | `CAP_NET_ADMIN`, `CAP_SYS_ADMIN`, `CAP_BPF`, `CAP_PERFMON` |

**Bắt đầu** gồm ba bước: (1) tạo IAM role và bật Managed Instances trong account; (2) tạo capacity provider; (3) chạy task/service dùng capacity provider đó.

---

## 2. So sánh EC2 – Managed Instances – Fargate

> Nguồn: `06-00` mục 19, `06-09`, `06-10`, `06-99`

| | EC2 tự quản lý | **Managed Instances** | Fargate |
|---|---|---|---|
| Chọn instance type | Có | **Có** (mặc định / thuộc tính / type cụ thể) | Không |
| AMI | Tự chọn, custom được | **Bottlerocket** do AWS quản lý, **không custom** | AWS |
| Patch, scale instance | Bạn (hoặc ASG) | **AWS** | AWS |
| Vòng đời instance | Không giới hạn | **14–21 ngày** | — |
| SSH | Có | **Không** (ECS Exec) | Không |
| Nhiều task / instance | Có | **Có (mặc định)**, hoặc single-task mode | Không — mỗi task cô lập |
| GPU | Có | **Có** (NVIDIA, AMD; cả GPU phân đoạn G6f) | Không |
| Network mode | `awsvpc`, `bridge`, `host`, `none` | `awsvpc`, `host` | `awsvpc` |
| Placement strategy | Có | **Không** (điều khiển gián tiếp qua subnet) | Không |
| Placement constraint | Đầy đủ | Chỉ `subnet-id`, `availability-zone`, `instance-type`, `cpu-architecture` + `distinctInstance` | Không |
| Capability đặc quyền | Có | Có, phải bật trên capacity provider | Chỉ thêm `SYS_PTRACE` |
| Daemon | Strategy `DAEMON` | **Managed Daemons** | Không |
| Ephemeral storage | Đĩa instance | EBS data volume / instance store dùng chung | 20–200 GiB mỗi task |
| OS | Linux, Windows | **Chỉ Linux** | Linux, Windows |
| FIPS | Tự lo | **Mức capacity provider** (AMI FIPS chỉ GovCloud US) | Mức account |
| Tính tiền | Theo instance | **Instance + phí quản lý** | Theo vCPU/memory task |
| Savings Plans | Compute, EC2 Instance SP, RI 🔎 | **Compute & Instance SP**, RI tự áp dụng | Compute SP 🔎 |

⚠️ Một **capacity provider strategy** chỉ chứa **một loại** provider: Managed Instances, **hoặc** Auto Scaling group, **hoặc** Fargate/Fargate Spot. Cluster thì chứa lẫn được các loại.

---

## 3. Khi nào dùng, khi nào không

> Nguồn: `06-00`, `06-05`, `06-09`, `14-01-03-00`; phần quyết định là 🔎 tổng hợp

**Nên dùng khi:**

| Tình huống | Lý do |
|---|---|
| Cần **GPU** (inference, training ngắn có checkpoint) | Fargate không có GPU; không muốn tự vận hành EC2 GPU |
| Cần **Graviton thế hệ mới**, CPU tần số cao, mạng cao, memory lớn | Chọn được family cụ thể |
| Cần **Capacity Reservations** / tận dụng RI, Savings Plans đã mua | Fargate không dùng reservation EC2 |
| Agent **observability đặc quyền** (eBPF, Datadog, Wireshark) | Capability `BPF`, `PERFMON`, `NET_ADMIN` + Managed Daemons |
| Nhiều task nhỏ, muốn **bin packing** rẻ hơn Fargate | Trả theo instance, nhiều task/instance |
| Muốn rời EC2 tự quản lý mà vẫn giữ phần cứng | Bỏ việc patch AMI, ASG, drain tay |
| Workload cần **I/O cục bộ nhanh** | Instance store (NVMe) làm data volume |

**Không nên / không được dùng khi:**

| Tình huống | Lý do |
|---|---|
| Task **chạy liên tục > 14 ngày** (vd long-running standalone job, stateful không tái tạo được) | Instance bị thay ở ngày 14–21 |
| Cần **Windows container** | Chỉ Linux |
| Cần **custom AMI**, user data, chỉnh `/etc/ecs/ecs.config`, kernel module riêng | AMI do AWS quản lý |
| Cần **SSH** vào máy để vận hành | Không có SSH |
| Cần `bridge` mode, Docker volume plugin, swap, `links`… | Không hợp lệ (mục 11) |
| Yêu cầu **cô lập từng task** mà không muốn trả nhiều instance | Fargate rẻ và đơn giản hơn single-task mode 🔎 |
| Cần **FIPS** ngoài GovCloud (US) | AMI FIPS chỉ có ở GovCloud (US) |

---

## 4. Các thành phần: cluster, capacity provider, launch template, role

> Nguồn: `06-00` mục 2, 7; `14-01-00`, `14-01-01`, `14-01-03-00`, `14-01-03-02`; `06-09` mục 2

### 4.1 Mô hình

```
Cluster
 ├─ Capacity provider (Managed Instances)  ← chỉ dùng trong cluster đã chỉ định khi tạo
 │    ├─ infrastructureRoleArn             ← ECS dùng để launch/terminate EC2
 │    └─ instanceLaunchTemplate            ← ECS tạo launch template từ đây
 │         ├─ ec2InstanceProfileArn
 │         ├─ networkConfiguration (subnets, securityGroups)
 │         ├─ storageConfiguration (storageSizeGiB)
 │         ├─ instanceRequirements (tuỳ chọn)
 │         ├─ capacityOptionType (On-Demand / Spot / Reserved)
 │         ├─ monitoring (BASIC / DETAILED)
 │         └─ fipsEnabled, localStorageConfiguration…
 ├─ Capacity provider strategy mặc định của cluster
 └─ Service / task  → dùng strategy mặc định hoặc strategy ghi đè
```

### 4.2 Default và custom capacity provider

| Loại | Cách có | ECS chọn instance thế nào |
|---|---|---|
| **Default** | ECS **tự tạo** khi tạo/cập nhật cluster cho Managed Instances | Instance general-purpose **rẻ nhất** khớp `operatingSystemFamily`, `cpuArchitecture`, `cpu`, `memory` (task definition) và `placementConstraints`, `placementStrategy` (service) |
| **Custom** | Bạn tạo (console **Use custom – advanced** hoặc CLI) | Theo `instanceRequirements` / `allowedInstanceTypes` |

**Dùng custom khi cần** (`14-01-03-00`): compute đặc thù (accelerator, tập lệnh, mạng cao, memory lớn), **GPU** (NVIDIA hoặc AMD), **capacity reservation**, **observability đặc quyền** (eBPF), **tối ưu chi phí** (instance lớn, nhiều task), **VPC Encryption Controls**, **instance store**, **tắt FIPS**.

### 4.3 IAM role

| Role | Ai dùng | Để làm gì |
|---|---|---|
| **Infrastructure role** | ECS | Gọi EC2 thay bạn: launch, terminate, quản lý managed instance. **SCP luôn áp dụng** cho role này |
| **Instance profile** | EC2 instance (ECS agent, Docker daemon) | Quyền của agent trên instance, cho AWS quản lý instance |
| **Task execution role** | ECS agent thay task | Pull ECR private, ghi `awslogs`, lấy secret |
| **Task role** | Code ứng dụng | Gọi dịch vụ AWS từ container |

Tạo cluster còn cần quyền **`iam:CreateServiceLinkedRole`** cho người tạo (`14-01-01`).

⚠️ Instance profile của EC2 tự quản lý (vd `ecsInstanceRole`) không mặc định dùng lại được — làm theo hướng dẫn *Managed Instances instance profile* 🔎.

### 4.4 Giám sát và tag của capacity provider

- **Monitoring**: **basic** (metric 5 phút, status check 1 phút, mặc định, miễn phí) hoặc **detailed** (mọi metric 1 phút). Đổi được **không cần tạo lại** capacity provider (`14-01-03-03`).
- **Tag propagation**: `propagateTags = CAPACITY_PROVIDER` → tag gắn lên managed instance, container instance, launch template, volume, ENI. Tối đa **50** tag; tag thêm/xoá **sau** khi tạo không áp cho tài nguyên đã có.

---

## 5. Cách ECS chọn instance type

> Nguồn: `06-01`, `06-00` mục 3, `14-01-03-02`

### 5.1 Ba cách

| Cách | Cấu hình | Khi nào |
|---|---|---|
| **Tự động** | Không khai báo `instanceRequirements` | Không có yêu cầu đặc biệt |
| **Theo thuộc tính** | `instanceRequirements` (vCPU, memory, hãng CPU…) | Đa số workload — linh hoạt, dễ đặt task |
| **Type / family cụ thể** | `allowedInstanceTypes` / `excludedInstanceTypes`, hỗ trợ wildcard | Cần phần cứng đặc thù |

Wildcard:

| Mẫu | Ý nghĩa |
|---|---|
| `m6i.xlarge` | Đúng một type |
| `c6i.*` | Mọi size của family c6i |
| `m6*.*` | Mọi family thế hệ m6 (m6i, m6a, m6g…) |

### 5.2 Logic của chọn theo thuộc tính

- Nhiều **thuộc tính** → instance phải thoả **tất cả** (AND).
- Một thuộc tính có nhiều **giá trị** → chỉ cần thoả **một** (OR).

```
vCPU 2–8   AND   memory 4–32 GiB   AND   hãng CPU ∈ {intel, amd}
                                         └── OR giữa các giá trị
```

| Nhóm | Thuộc tính |
|---|---|
| CPU & memory | Số vCPU, memory (MiB), tỉ lệ memory/vCPU, hãng CPU (`intel`, `amd`, `amazon-web-services`), thế hệ instance |
| Local storage | include / exclude / require; HDD hoặc SSD; tổng dung lượng |
| Mạng & hiệu năng | Băng thông mạng (Gbps), số network interface, băng thông EBS (Mbps), burstable, bare metal |
| Accelerator | Số lượng, hãng, tên chip, loại (`gpu`, `fpga`, `inference`), tổng memory |
| Price protection | Giá Spot / On-Demand tối đa theo **% so với type rẻ nhất** khớp yêu cầu |

✍️ Ví dụ — chỉ Graviton, 2–16 vCPU, bỏ burstable:

```json
"instanceRequirements": {
  "vCpuCount": { "min": 2, "max": 16 },
  "memoryMiB": { "min": 4096 },
  "cpuManufacturers": ["amazon-web-services"],
  "excludedInstanceTypes": ["t4g.*"]
}
```

⚠️ Chọn Graviton (`*g`) thì image phải build cho **ARM64** và task definition nên khai báo `cpuArchitecture: ARM64`.

### 5.3 Cơ chế tối ưu đi kèm

| Cơ chế | Ý nghĩa |
|---|---|
| **Multi-task placement** | Đặt nhiều task nhỏ lên instance lớn |
| **Active workload consolidation** | Phát hiện instance rảnh, gom task — tôn trọng min/max task, start-before-stop, task protection (chi tiết mục 9) |
| **Right-sizing** | Launch instance thay thế có kích thước hợp hơn khi nhu cầu đổi |
| **Single-task mode** | Mỗi task một instance — cô lập **mức VM** |

---

## 6. Instance type được hỗ trợ và hình thức mua

> Nguồn: `06-01` mục 5, 6

### 6.1 Family được hỗ trợ

| Nhóm | Ví dụ family |
|---|---|
| Compute optimized | C5…C8 (gồm C6g, C7g, C7gn, C8g, C7i-flex…) |
| General purpose | M5…M9g, **T3, T3a, T4g** |
| Memory optimized | R5…R8, U (vài TB RAM), X2, X8g, Z1d |
| Storage optimized | D3, I3en, I4i, I7i, I8g, Im4gn, Is4gen |
| Accelerated | DL1, G4dn, G5, G6, **G6f**, G7e, Inf1/2, P3dn, P4d, P5, P6, Trn1 |
| HPC | Hpc6a, Hpc6id, Hpc7a, Hpc7g |

Danh sách đầy đủ ở `06-01` mục 6. Thêm hai quy tắc: Managed Instances **chỉ tạo** instance có **> 1 vCPU** và **không phải** size `nano`/`micro`.

Đọc tên nhanh 🔎: `g` = Graviton, `a` = AMD, `i` = Intel, `d` = có NVMe cục bộ, `n` = mạng cao, `-flex` = biến thể giá tốt.

### 6.2 Hình thức mua

| Mô hình | Cấu hình | Ghi chú |
|---|---|---|
| **On-Demand** | Mặc định | Không cam kết |
| **Spot** | `capacityOptionType: Spot` | Giá thấp, **có thể bị thu hồi**; ⚠️ **không** có underutilized detection (mục 9) |
| **Capacity Reservations** | `capacityOptionType: Reserved` + capacity reservation group | Ưu tiên: `reservations-only`, `reservations-first`, `reservations-excluded`; ⚠️ bẫy deploy (mục 7.2) |

**Savings Plans** (Compute, Instance) và **Reserved Instances** tự áp dụng — không cấu hình gì thêm.

🔎 Muốn trộn On-Demand và Spot: tạo **hai capacity provider Managed Instances** (một Spot, một On-Demand) và dùng base/weight trong cùng strategy — hợp lệ vì cùng một loại provider, nhưng nên thử trước.

---

## 7. Best practice chọn instance

> Nguồn: `06-02`

### 7.1 Sáu best practice

| # | Best practice | Ý chính |
|---|---|---|
| 1 | Dùng **chọn mặc định** khi không có yêu cầu đặc biệt | ECS chọn rẻ nhất theo task definition + service |
| 2 | **Chọn theo thuộc tính** cho đa số workload | Dải type rộng → đặt task dễ thành công, chọn loại rẻ nhất lúc launch |
| 3 | **Type cụ thể** chỉ khi cần phần cứng đặc thù | GPU, CPU tần số cao, mạng chuyên dụng |
| 4 | **Cân đối tài nguyên**, tránh dư | Dựa trên số liệu giám sát; dùng tỉ lệ memory/vCPU |
| 5 | **Nhiều capacity provider** cho workload đa dạng | Mỗi thành phần một cấu hình phù hợp |
| 6 | **Capacity Reservations**: chỉnh deployment config | Xem 7.2 |

✍️ Mẫu nhiều capacity provider:

```
Cluster
├─ CP "general"  (theo thuộc tính, Graviton)  → web, API
├─ CP "gpu"      (allowedInstanceTypes g5.*)  → inference
└─ CP "batch"    (Spot)                       → job từ queue
```

### 7.2 ⚠️ Capacity Reservations và deployment

Deployment mặc định của service: `minimumHealthyPercent = 100%`, `maximumPercent = 200%` → khởi động task mới **trước** rồi mới dừng task cũ → tạm thời cần **tới 200%** capacity. Nếu service đã dùng hết reservation, deploy **thất bại**.

```
Reservation vừa đủ 4 task
  Mặc định (min 100% / max 200%): 4 cũ + 4 mới = 8  → ❌ kẹt
  Đề xuất  (min 75%  / max 100%): dừng 1 cũ → chạy 1 mới → lặp lại → ✅ không vượt 4
  Hoặc giữ headroom trong reservation → ✅ nhưng trả tiền phần dự phòng
```

| Cấu hình | Ưu | Nhược |
|---|---|---|
| min 100% / max 200% | Không giảm số task khi deploy | Cần gấp đôi capacity tạm thời |
| min 75% / max 100% | Không cần capacity dư | Có lúc chỉ còn 75% task phục vụ |
| Giữ headroom | Deploy mượt với mặc định | Trả tiền dự phòng |

Bẫy này cũng ảnh hưởng **patching** (mục 10): drain với max 200% cũng cần chỗ cho task mới.

---

## 8. Đặt task và auto scaling

> Nguồn: `15-02-01`, `13-02-07`, `13-09`, `06-00`

### 8.1 Thứ tự ưu tiên khi đặt task

1. **Trải AZ** là mặc định và **ưu tiên hơn bin packing**. Service nhiều task → cố gắng dùng **ít nhất 3 instance ở các AZ khác nhau**.
2. **Bin packing** (xếp chặt) chỉ thật sự xảy ra khi capacity provider có **một subnet**; dễ xảy ra lúc **mới launch** hơn lúc scale.
3. Capacity provider quyết định dựa trên: tài nguyên của task đang chờ, instance sẵn có, ràng buộc mạng, phân bố AZ.

| Cấu hình subnet | Dùng cho |
|---|---|
| **Nhiều subnet** (mặc định) | Production — sẵn sàng cao |
| **Một subnet** | Dev hoặc tối ưu chi phí (xếp chặt hơn) |

### 8.2 Ràng buộc mạng ảnh hưởng mật độ

- `awsvpc`: mỗi task một **ENI** → **giới hạn ENI** của instance type giới hạn số task/instance. ENI trunking giúp tăng mật độ.
- `host`: đặt task chủ yếu theo **tài nguyên** (CPU/memory/port).
- Phân bố "spread" hay "concentrated" đều là bình thường tuỳ cấu hình; phân bố **không đều** bất thường → xem giới hạn ENI và tài nguyên.

### 8.3 Placement constraint

- **Không có placement strategy** (binpack/spread/random không áp dụng).
- Tối đa **10 constraint**: `distinctInstance`, và `memberOf` chỉ với 4 thuộc tính:

| Thuộc tính | Ví dụ ✍️ |
|---|---|
| `ecs.subnet-id` | `attribute:ecs.subnet-id == subnet-aaa` |
| `ecs.availability-zone` | `attribute:ecs.availability-zone in [ap-southeast-1a, ap-southeast-1b]` |
| `ecs.instance-type` | `attribute:ecs.instance-type =~ g5.*` |
| `ecs.cpu-architecture` | `attribute:ecs.cpu-architecture == arm64` |

### 8.4 Auto scaling hai tầng

| Tầng | Ai lo |
|---|---|
| Số **task** của service | **Service auto scaling** (target tracking, step…) — bạn cấu hình như Fargate |
| Số **instance** | **Capacity provider Managed Instances** tự launch khi có task chờ, tự xoá/gom khi thừa (mục 9) |

Không còn ASG, không còn `targetCapacity` của cluster auto scaling như EC2 🔎.

---

## 9. Tự tối ưu hạ tầng: idle, underutilized, ScaleInAfter

> Nguồn: `06-07`

### 9.1 Hai loại tối ưu

| | **Idle detection** | **Underutilized detection** |
|---|---|---|
| Phát hiện | Instance **không còn task nào** | Task rải rác, instance dùng chưa hết |
| Kích hoạt | **Task cuối** trên instance dừng | **Mỗi lần** một task dừng **hoặc** một instance mới launch |
| Hành động | `DEREGISTERING` → terminate | `DRAINING` → task được thay sang instance khác → `DEREGISTERING` |
| Áp dụng | Mọi instance (cả Spot) | Chỉ instance chạy **task của service**, **On-Demand** |
| Không áp dụng | — | Instance có **standalone task**; **Spot** |

```
Trước:  i-1 [A] ~30%    i-2 [B] ~30%    i-3 [C] ~30%
           │ DRAINING i-2, i-3 → B, C được thay sang i-1 (hoặc instance mới)
Sau:    i-1 [A][B][C]                     (i-2, i-3 → DEREGISTERING → terminate)
```

Gom an toàn nhờ tôn trọng: **min/max số task** của service, deploy **start-before-stop**, **task protection**.

### 9.2 ScaleInAfter

Độ trễ (giây) từ lúc instance trở nên idle/underutilized tới lúc bắt đầu tối ưu. Áp dụng cho **cả hai** loại.

| Giá trị | Ý nghĩa |
|---|---|
| `0`–`3600` | Độ trễ, giây |
| `-1` | **Tắt** tối ưu hạ tầng |
| Không khai báo | ECS tự chọn |

- **Idle**: có task mới trong lúc chờ → **huỷ** terminate.
- **Underutilized**: có task mới launch hoặc task khác dừng → **reset timer**, hết giờ thì đánh giá lại.

```
ScaleInAfter = 600
Idle:          t=0 task cuối dừng … t=300 task mới → huỷ   (không có → t=600 DEREGISTERING)
Underutilized: t=0 task dừng … t=200 task khác dừng → reset … t=800 đánh giá lại → DRAINING
```

| Chọn 🔎 | Khi nào |
|---|---|
| Thấp (0–300) | Tải ổn định, muốn tiết kiệm tối đa |
| Cao (1800–3600) | Tải dao động, job theo đợt — tránh xoá rồi tạo lại, giữ cache image |
| `-1` | Không muốn task bị dời do gom (chấp nhận tốn tiền) |

⚠️ Khi gom, task của service **bị dời chỗ** → ứng dụng phải tắt êm (`SIGTERM`, `stopTimeout`).

---

## 10. Vòng đời instance 14–21 ngày và patching

> Nguồn: `06-04`, `06-05` mục 3.2, 7.2

### 10.1 Patching = thay instance

AWS **không vá tại chỗ**. Instance cũ bị drain, task chạy trên instance mới đã có phần mềm mới nhất. Bạn không tự vá được (không custom AMI, không SSH).

```
Ngày 0 ─────────────── Ngày 14 ─────────── Ngày 21
launch                  bắt đầu drain       terminate chậm nhất
   (có thể drain sớm hơn: lỗ hổng bảo mật, phần cứng suy giảm, để khớp event window)
```

### 10.2 Event window

- Gắn Managed Instances vào **EC2 event window** qua **instance ID** hoặc **instance tag**.
- ECS lên lịch bảo trì trong khung giờ đó theo kiểu **best effort**, có thể **sớm hơn** mốc 14 ngày để rơi vào window.
- Thay đổi event window **chỉ áp cho instance launch mới**.
- Trước giờ drain, task vẫn được đặt **bình thường** lên instance.

✍️ Event window "Chủ nhật 01:00–05:00 giờ Việt Nam" → instance launch thứ Tư tuần 1, mốc 14 ngày rơi vào thứ Tư tuần 3 → ECS có thể drain vào Chủ nhật tuần 2. (Thuật toán chọn chính xác docs không nêu 🔎.)

### 10.3 Trình tự bảo trì

```
Đến giờ bảo trì
  ├─ UpdateContainerInstancesState → DRAINING
  ├─ thay task theo deployment config của service
  │     maximumPercent 200%: chạy task mới → chờ RUNNING → dừng task cũ
  │     maximumPercent 100%: dừng task cũ → chạy task mới
  ├─ drain xong trước ngày 21 → instance được thay
  └─ ngày 21 chưa xong → DeregisterContainerInstance → dừng cưỡng bức task còn lại
```

### 10.4 Chuẩn bị

| Rủi ro | Cách giảm |
|---|---|
| Drain kẹt tới ngày 21 | Giữ headroom; với reservation dùng min < 100% / max 100% |
| Mất request khi thay task | Bắt `SIGTERM`, đặt `stopTimeout` (mặc định 30 s, tối đa 86400 s); ALB deregistration delay 🔎 |
| Drain giờ cao điểm | Event window giờ thấp điểm |
| Service 1 task | Desired count ≥ 2, nhiều AZ |
| Task chạy > 14–21 ngày | ⚠️ Không phù hợp Managed Instances |
| Standalone task dài | Không có service thay thế → ≤ 14 ngày, chạy lại được, có checkpoint |

---

## 11. Task definition cho Managed Instances

> Nguồn: `06-00` mục 4, 5; `13-02-07`; `13-09`

### 11.1 Khai báo

```json
"requiresCompatibilities": ["MANAGED_INSTANCES"]
```

- Tương thích task definition Fargate **platform version 1.4.0** → migrate dễ.
- Khai báo được **cả** `FARGATE` và `MANAGED_INSTANCES` (hoặc `EC2` và `MANAGED_INSTANCES`); khi đó task definition phải hợp lệ với **cả hai** 🔎.
- Khai báo `MANAGED_INSTANCES` lúc register để ECS **kiểm tra hợp lệ**.

### 11.2 Tham số chính

| Tham số | Giá trị trên Managed Instances |
|---|---|
| `networkMode` | **Bắt buộc**: `awsvpc` (mặc định) hoặc `host` |
| `operatingSystemFamily` | Chỉ `LINUX` |
| `cpuArchitecture` | `X86_64` hoặc `ARM64` |
| `cpu` (task) | 0,125 – 10 vCPU |
| `memory` (task) | Tuỳ ý theo instance |
| `executionRoleArn` | Bắt buộc khi pull ECR private hoặc dùng `awslogs` |
| `placementConstraints` | Tối đa 10; `distinctInstance`, `memberOf` (4 thuộc tính) |
| Volume | Bind mount (`sourcePath` trong **`/var`** hoặc **`/tmp`**), **EFS**, **EBS** (`configuredAtLaunch`, một volume mỗi revision) |
| `versionConsistency` | Mặc định resolve tag → digest |
| Container `memory` / `memoryReservation` | Hard limit (vượt bị kill) / soft limit; tối thiểu 6 MiB |
| Restart policy | 60–1800 s, mặc định 300 |
| Health check | interval 30, timeout 5, retries 3, startPeriod tắt |
| `environmentFiles` | ≤ 10 file `.env` trên S3 |
| GPU | Có, cả **GPU phân đoạn trên G6f**; Neuron (Inf/Trn) |
| `startTimeout` / `stopTimeout` | ≤ 600 s / mặc định 30, tối đa **86400 s** |
| `dependsOn` | `START`, `COMPLETE`, `SUCCESS`, `HEALTHY` |
| Log driver | Chỉ **`awslogs`**, **`splunk`**, **`awsfirelens`** |
| `privileged`, `linuxParameters.capabilities` | Có — cần bật trên capacity provider |

### 11.3 ⛔ Không hợp lệ

`disableNetworking`, `dnsSearchDomains`, `dnsServers`, `dockerSecurityOptions`, `dockerVolumeConfiguration`, `ephemeralStorage`, `extraHosts`, `fsxWindowsFileServerVolumeConfiguration`, `hostname`, `inferenceAccelerator`, `ipcMode`, `links`, `maxSwap`, `proxyConfiguration`, `sharedMemorySize`, volume `sourcePath` ngoài thư mục ghi được, `swappiness`, `tmpfs`.

⚠️ Hay vướng nhất khi migrate:
- Từ **Fargate**: `ephemeralStorage` (không còn — dùng data volume của instance), `proxyConfiguration` (App Mesh).
- Từ **EC2**: `bridge` mode, `links`, swap, Docker volume, `hostname`, `dnsServers`, log driver `fluentd`/`gelf`/`syslog`.

✍️ Task definition tối thiểu:

```json
{
  "family": "<APP>",
  "requiresCompatibilities": ["MANAGED_INSTANCES"],
  "networkMode": "awsvpc",
  "cpu": "512",
  "memory": "1024",
  "runtimePlatform": { "operatingSystemFamily": "LINUX", "cpuArchitecture": "ARM64" },
  "executionRoleArn": "<EXECUTION_ROLE_ARN>",
  "taskRoleArn": "<TASK_ROLE_ARN>",
  "containerDefinitions": [{
    "name": "app",
    "image": "<ACCOUNT>.dkr.ecr.<REGION>.amazonaws.com/<REPO>:<GIT_SHA>",
    "essential": true,
    "portMappings": [{ "containerPort": 8080, "name": "http" }],
    "logConfiguration": {
      "logDriver": "awslogs",
      "options": {
        "awslogs-region": "<REGION>",
        "awslogs-group": "/ecs/<APP>",
        "awslogs-stream-prefix": "app"
      }
    },
    "stopTimeout": 60
  }]
}
```

---

## 12. Networking

> Nguồn: `06-00` mục 9, 12; `13-02-02`; `15-02-01`

| Mode | Đặc điểm |
|---|---|
| **`awsvpc`** (mặc định) | Mỗi task một **ENI + IP private** → security group, NACL **mức task**; tương thích Fargate; ENI trunking tăng mật độ |
| **`host`** | Dùng chung network namespace của instance; không 2 task cùng port trên một instance |

**Load balancing**: ALB, NLB, **Gateway Load Balancer**; target type **`ip`** (vì `awsvpc` gắn ENI, không gắn instance). ECS Gateway cũng hỗ trợ Managed Instances với `awsvpc` (folder 12).

**IPv6-only** (`13-02-02`):
- VPC có IPv6 CIDR, subnet chỉ IPv6, route table + SG rule IPv6.
- Load balancer **dualstack**, target group IPv6.
- Tới endpoint chỉ IPv4: **DNS64 + NAT64**.
- Pull ECR: **image URI dualstack**; ECR không có interface VPC endpoint dualstack.
- ⚠️ **ECS Exec không hỗ trợ** ở IPv6-only.
- Chuyển sang IPv6-only: update trực tiếp hoặc service song song + blue/green.

Đường ra ngoài 🔎: subnet private cần NAT gateway **hoặc** VPC endpoint (ECR api, ECR dkr, S3 gateway, CloudWatch Logs, Secrets Manager, SSM cho ECS Exec) để instance và task pull image, đăng ký với ECS.

---

## 13. Storage: EBS data volume và instance store

> Nguồn: `06-00` mục 10, 11

### 13.1 EBS data volume của instance

- Gắn **khi tạo instance**, **dùng chung** cho mọi task trên instance; dùng cho **bind mount**, chia sẻ giữa container qua `volumes`, `mountPoints`, `volumesFrom`.
- Khai báo bằng `storageConfiguration.storageSizeGiB` khi tạo capacity provider.

| | Giá trị |
|---|---|
| Tối thiểu | **30 GiB** |
| Tối đa | **16.384 GiB** |
| Mặc định | **80 GiB** |

- ⚠️ Image đã pull (cả bản **nén** lẫn **giải nén**) nằm trên volume này → dung lượng bind mount thực = tổng − image.
- Hiệu năng EBS theo instance type (EBS-optimized).
- Snapshot được để **pháp chứng / debug**.
- **EBS encryption by default** → mã hoá bằng KMS key trong cài đặt đó.
- Ngoài ra có **EBS volume cho từng task** (`configuredAtLaunch`).

### 13.2 Instance store (local storage)

- Bật bằng `localStorageConfiguration.useLocalStorage = true` trên capacity provider **mới**.
- Instance có instance store → dùng **thay cho** EBS data volume: **rẻ hơn**, **I/O nhanh hơn**, **không tính phí thêm**.
- Nhiều đĩa → ECS gộp thành **một volume RAID 0**.
- ⚠️ **Tạm thời**: mất dữ liệu khi instance dừng/terminate/hỏng — không lưu dữ liệu cần giữ.
- Kiểm tra: thuộc tính **`ecs.capability.storage.local-storage-enabled`** trên container instance.
- ⚠️ Cluster tạo bằng console: capacity provider mặc định **không** có `localStorageConfiguration`.

| Muốn | Cấu hình |
|---|---|
| Luôn dùng EBS | Không bật `useLocalStorage` (mặc định) |
| Instance store khi có, không có thì EBS | `useLocalStorage: true`, bỏ `instanceRequirements` (→ `localStorage` = `INCLUDED`) |
| **Bắt buộc** instance store ≥ N GiB | `useLocalStorage: true` + `localStorage: REQUIRED` + `totalLocalStorageGB.min: N` (+ `localStorageTypes: ["ssd"]`) |

`storageSizeGiB` = kích thước **EBS dự phòng**; `totalLocalStorageGB` = **tối thiểu instance store**.

---

## 14. Image pull và cache

> Nguồn: `06-03`

| Cơ chế | Nội dung |
|---|---|
| Kích thước image | Image đầy đủ (Debian, Ubuntu, AL2) chậm hơn slim / Alpine |
| Cache | Instance mới → cache **trống**; task đầu **pull**, task sau cùng image dùng **cache** |
| Tự dọn | Task cuối dùng image dừng → **timer 15 phút** → không có task mới dùng image thì **xoá** |
| Tag → digest | ECS **đặt 1 task trước** để resolve digest, rồi mới đặt các task còn lại → **ảnh hưởng bin packing** |

```
Instance mới (cache trống)
  Task 1 (image A) → pull A   ← chậm
  Task 2 (image A) → cache    ← nhanh
  … task cuối dùng A dừng → 15 phút không ai dùng → xoá A
```

So với EC2: **không có** `ECS_IMAGE_PULL_BEHAVIOR`, không bake image vào AMI.

Gợi ý 🔎:
- Image nhỏ, ít layer; **ECR cùng Region** + VPC endpoint.
- Tag **bất biến** (git SHA) hoặc digest.
- Tăng `storageSizeGiB` nếu image lớn/nhiều.
- Workload chạy thưa (scheduled mỗi giờ) gần như luôn pull lại; instance thay mỗi 14–21 ngày và instance mới do scale out cũng bắt đầu cache trống.

---

## 15. Bảo mật

> Nguồn: `06-05`, `06-00` mục 8, 17

### 15.1 Mô hình bảo mật

| Đặc điểm | Nội dung |
|---|---|
| Hạ tầng AWS quản lý | AWS kiểm soát vòng đời và vá → bớt lỗi con người, bớt can thiệp trái phép |
| Không quyền quản trị | Không SSH, cấm truy cập admin vào instance; debug bằng **ECS Exec**, quản lý qua **ECS API** |
| Nhiều task/instance | ⚠️ **Nới lỏng** cô lập so với Fargate — task có thể bị ảnh hưởng bởi lỗ hổng của task khác trên cùng instance |
| Cô lập dữ liệu | AWS **không đăng nhập** instance, **không truy cập** dữ liệu; chỉ thu metric/log cần cho tính năng |

### 15.2 Biện pháp có sẵn

| Lớp | Nội dung |
|---|---|
| OS | **Bottlerocket**, **root filesystem bất biến**, **SELinux** (mandatory access control mức kernel) |
| Vá | Tự động, tôn trọng maintenance window |
| Vòng đời | Tối đa 14 ngày (drain), 21 (terminate) |
| Đặc quyền | Tắt mặc định; opt-in `CAP_NET_ADMIN`, `CAP_SYS_ADMIN`, `CAP_BPF`, `CAP_PERFMON` **trên capacity provider** |

> **Important (docs):** Bật capability đặc quyền đặt task trước rủi ro bổ sung. Chỉ bật khi thật sự cần.

### 15.3 Managed instance trong account của bạn

- Instance nằm **trong account bạn**, nhưng **ECS là operator**: bạn **không** terminate hay sửa trực tiếp được.
- Nhận diện:

| Dấu hiệu | Giá trị |
|---|---|
| `Operator` trong `DescribeInstances` | `ecs.amazonaws.com` |
| Tag `aws:ec2:managed-launch` | `ecs-managed-instances` |

- ⚠️ Từ **22/4/2026**, EC2 **mặc định ẩn** managed instance mới khỏi console EC2 và thao tác **list** của API — **vẫn chạy và vẫn tính tiền**. Script kiểm kê / báo cáo chi phí cần chỉnh cài đặt hiển thị hoặc lọc theo `Operator`/tag.

### 15.4 Single-task mode

| | Multi-task (mặc định) | Single-task |
|---|---|---|
| Cô lập | Chung instance | **Mức VM**, như Fargate |
| Chi phí | Tối ưu | Cao hơn (mỗi task một instance) |
| Khởi động | Nhanh hơn (instance có sẵn, cache image) | Chậm hơn 🔎 |

### 15.5 Best practice bảo mật

1. Đánh giá có chủ đích mô hình **multi-task**.
2. Dùng **single-task** cho workload cần cô lập mạnh.
3. **Giảm thiểu** capability đặc quyền.
4. Thiết kế cho **gián đoạn** (vòng đời 14 ngày): dùng service, standalone ≤ 14 ngày, tắt êm.
5. Cấu hình **EC2 event windows**.
6. **Giám sát và kiểm toán** cấu hình, sự kiện bảo mật.

Shared responsibility 🔎: AWS lo OS, agent, vá, vòng đời instance; bạn lo image, code, task role/least privilege, security group, secret, cấu hình capacity provider.

---

## 16. Tuân thủ: FIPS, CMK, VPC Encryption Controls

> Nguồn: `06-05` mục 4–6, `06-06`

### 16.1 Chương trình tuân thủ

**PCI-DSS**, **HIPAA**, **FedRAMP** — cùng phạm vi như ECS. Hỗ trợ **CMK** (KMS) — vd cho managed storage của cluster (`14-01-01`) và EBS.

### 16.2 FIPS-140

| Điểm | Nội dung |
|---|---|
| Cấu hình ở | **Mức capacity provider** (Fargate: mức account) — vì FIPS là cấu hình theo instance |
| AMI FIPS | ⚠️ Chỉ có ở **AWS GovCloud (US)**, **bật mặc định** ở đó |
| Chuẩn | **FIPS-140-3** |
| Kiến trúc | ⚠️ Task phải `X86_64` (không ARM64/Graviton) |
| Tắt | `instanceLaunchTemplate.fipsEnabled: false` khi tạo capacity provider mới |
| Kiểm tra | ECS Exec: `cat /proc/sys/crypto/fips_enabled` → `1` = FIPS |

### 16.3 VPC Encryption Controls

Kiểm soát tập trung, giám sát và **bắt buộc encryption in transit** cho mọi traffic trong/giữa VPC trong Region.

- Cần **custom** capacity provider với `allowedInstanceTypes` chỉ gồm type hỗ trợ.
- Type hợp lệ = (hỗ trợ encryption in transit) ∩ (Managed Instances hỗ trợ) ∩ (yêu cầu workload).

```bash
aws ec2 describe-instance-types \
  --filters Name=network-info.encryption-in-transit-supported,Values=true \
  --query "InstanceTypes[*].[InstanceType]" --output text | sort
```

| Điểm | Nội dung |
|---|---|
| Burstable | ⚠️ **T3, T3a, T4g không hỗ trợ** → không dùng ở chế độ **Enforced** |
| Monitor → Enforced | Chỉ chuyển khi **mọi instance đang chạy** đều hỗ trợ |
| Enforced + type không hỗ trợ | Task **launch thất bại** |
| Tìm tài nguyên chặn | `GetVpcResourcesBlockingEncryptionEnforcement` |

🔎 Lộ trình: Monitor → đổi capacity provider (tốt nhất tạo CP mới, mục 21) → chờ instance cũ được thay / deploy lại → kiểm tra không còn tài nguyên chặn → Enforced.

---

## 17. Organization controls: SCP và Allowed AMIs

> Nguồn: `06-00` mục 18

- ECS dùng **infrastructure role** để launch EC2; **SCP luôn áp dụng** cho role này → SCP có thể chặn Managed Instances.
- Hay gặp nhất: SCP **giới hạn AMI** được launch → phải cho phép AMI từ **account AMI của Managed Instances** theo từng Region.
- Dùng **EC2 Allowed AMIs** → mở rộng tiêu chí gồm các account đó.
- ⚠️ **Không** dùng `ec2:Owner` — giá trị luôn là `amazon`, sẽ mở cửa cho **mọi** AMI của Amazon.

```json
{
  "Version": "2012-10-17",
  "Statement": [{
    "Sid": "DenyAMI",
    "Effect": "Deny",
    "Action": "ec2:RunInstances",
    "Resource": "arn:*:ec2:*::image/ami-*",
    "Condition": {
      "StringNotEquals": { "aws:ResourceAccount": ["<MI_AMI_ACCOUNT_REGION_1>", "<MI_AMI_ACCOUNT_REGION_2>"] }
    }
  }]
}
```

| Region | Account AMI Managed Instances |
|---|---|
| ap-southeast-1 (Singapore) | 832199679391 |
| ap-northeast-1 (Tokyo) | 679336465495 |
| us-east-1 | 260073348889 |
| us-west-2 | 491085424538 |

Bảng đầy đủ 33 Region ở link docs trong `06-00`. 🔎 Thực tế SCP thường còn phải cho phép các AMI khác công ty dùng — ghép account trên vào điều kiện hiện có thay vì thay thế.

---

## 18. GPU auto repair

> Nguồn: `06-08`

- ECS dùng **NVIDIA DCGM** theo dõi GPU; lỗi nghiêm trọng → instance **impaired**. **Bật mặc định**.
- Thay theo **start-before-stop**:

```
DCGM phát hiện lỗi → impaired → DRAINING (không nhận task mới)
  → cấp instance GPU mới → task cũ dừng êm (stopTimeout) → terminate instance hỏng
```

- **Giới hạn tốc độ**: tối đa **20%** instance của capacity provider drain cùng lúc; **< 9 instance** → tối đa **1**.
- **18 mã Xid** theo dõi:

| Nhóm 🔎 | Xid |
|---|---|
| Bộ nhớ / ECC | 48, 64, 95, 136, 140, 142, 143 |
| GPU treo / mất kết nối | 46, 62, 79, 109, 110 |
| NVLink / interconnect | 74, 151, 155, 156 |
| Nguồn / firmware | 54, 158 |

- Giám sát: `DescribeContainerInstances`, **container instance health change events** (EventBridge).
- Tắt: `autoRepairConfiguration.actionsStatus = DISABLED` (create/update capacity provider hoặc console). Vẫn theo dõi, không tự thay.
- ⚠️ **Tắt GPU auto repair cũng tắt auto repair của Managed Daemons.**

🔎 Job GPU dài (training): standalone task không được thay → cần **checkpoint**; đặt `stopTimeout` đủ để lưu trạng thái khi nhận `SIGTERM`.

---

## 19. Managed Daemons và task scale-in protection

> Nguồn: `06-00` mục 13; folder 07; `14-01-04-00`, `14-01-04-01`

### 19.1 Managed Daemons

- Cách chạy **agent mỗi instance** (log, metric, security, eBPF) — **chỉ có trên Managed Instances**, thay cho strategy `DAEMON` của EC2.
- Daemon được khởi động trên instance **trước** task ứng dụng; drain instance kéo theo daemon (folder 07).
- Agent đặc quyền dùng capability `BPF`, `PERFMON`… → cần bật trên capacity provider.
- Auto repair của daemon gắn với `autoRepairConfiguration` của capacity provider (mục 18).

Chi tiết ở các file của folder `07 - Amazon ECS Managed Daemons`.

### 19.2 Task scale-in protection

- Giữ task **của service** không bị dừng bởi **scale-in** hoặc **deployment** — job xử lý dài, game server, việc tốn kém nếu làm lại.
- Được Managed Instances tôn trọng khi **gom task** (mục 9) và khi drain.
- Bật `ProtectionEnabled = true` (mặc định **2 giờ**), `ExpiresInMinutes` **1–2880** (48 giờ).
- Hai cách: endpoint agent **`$ECS_AGENT_URI/task-protection/v1/state`** từ trong container (**khuyến nghị**), hoặc API **`UpdateTaskProtection`** / **`GetTaskProtection`**.
- Task role cần `ecs:GetTaskProtection`, `ecs:UpdateTaskProtection`.
- ⚠️ Lỗi **`DEPLOYMENT_BLOCKED`** khi số task được bảo vệ vượt desired count; CloudFormation timeout **3 giờ**.

✍️ Trong container:

```bash
curl -X PUT "$ECS_AGENT_URI/task-protection/v1/state" \
  -H "Content-Type: application/json" \
  -d '{"ProtectionEnabled": true, "ExpiresInMinutes": 60}'
```

🔎 Protection **không** kéo dài vòng đời instance quá ngày 21 — mốc terminate cuối vẫn áp dụng (`06-04`: ngày 21 chưa xong thì dừng cưỡng bức).

---

## 20. Giám sát, giá, quota

> Nguồn: `06-00` mục 13–15, `06-09` mục 1, `14-01-03-00`

### 20.1 Giám sát

| Công cụ | Nội dung |
|---|---|
| CloudWatch metrics | CPU, memory, network, storage **mức task và mức instance**; monitoring basic/detailed |
| Container Insights | Metric và log chi tiết; bật/tắt ảnh hưởng **cả cluster** |
| Bên thứ ba | Qua capability đặc quyền + Managed Daemons |
| Sự kiện | Container instance health events, task state change (EventBridge) 🔎 |

### 20.2 Giá

- Trả tiền **toàn bộ EC2 instance** + **phí quản lý** (management fee).
- Mức **lấp đầy instance** quyết định chi phí → multi-task, consolidation, cân đối tài nguyên.
- **Compute SP**, **Instance SP**, **RI** tự áp dụng; Spot rẻ hơn cho workload chịu gián đoạn.
- ⚠️ Instance bị ẩn khỏi `describe-instances` (từ 22/4/2026) **vẫn tính tiền**.

🔎 So với Fargate: Managed Instances rẻ hơn khi instance được lấp đầy tốt và tận dụng SP/RI/Spot; Fargate rẻ hơn khi tải nhỏ, rời rạc, ít task (không phải trả instance rảnh).

### 20.3 Quota

- Workload chịu **quota On-Demand instance của EC2** (vCPU theo family).
- Service chịu **quota của ECS**.

---

## 21. Vận hành capacity provider: tạo, cập nhật, xoá

> Nguồn: `14-01-01`, `14-01-02`, `14-01-03-01`, `14-01-03-02`, `14-01-03-03`, `14-01-03-04`

### 21.1 Coi capacity provider là bất biến

⚠️ **Cập nhật tại chỗ** chỉ áp cho **capacity mới** và **không** kích hoạt deployment → lỗi cấu hình có thể chỉ lộ ra **rất lâu sau**, khi service cần scale.

Quy trình khuyến nghị:

```
1. Tạo capacity provider MỚI với cấu hình mới
2. Chuyển từng service sang CP mới (update-service + force new deployment), chờ ổn định
3. Xoá CP cũ khi đã ổn  (CloudFormation: giữ CP cũ tới lần deploy sau để rollback)
```

Lợi ích: rollout có kiểm soát, rollback dễ, giảm blast radius.

### 21.2 Cập nhật cluster có sẵn

- Task đang chạy **không bị ảnh hưởng**; đổi capacity provider thì instance **cũ vẫn chạy**, chỉ instance **mới** theo cấu hình mới.
- `put-cluster-capacity-providers` **ghi đè** danh sách → nhớ liệt kê lại các capacity provider đang dùng.

### 21.3 Xoá

1. Gỡ CP khỏi strategy của **mọi service** (`UpdateService` + **Force new deployment**) để task chuyển sang CP còn lại.
2. Xoá (console: nhập `delete <tên>`).
3. Trạng thái **`INACTIVE`**; có thể còn thấy một thời gian nhưng không được dựa vào.

---

## 22. Triển khai từng bước (CLI)

> ✍️ Ghép từ `06-05`, `06-08`, `06-09`, `14-01-01`, `14-01-02`, `14-01-03-02`. Tên trường đúng như docs; vị trí chi tiết nên đối chiếu API reference.

**Bước 0 — Tiên quyết**
- Infrastructure role, instance profile, task execution role, task role.
- Quyền `iam:CreateServiceLinkedRole`.
- Kiểm tra SCP / Allowed AMIs (mục 17).
- VPC: subnet private ở ≥ 2–3 AZ, NAT hoặc VPC endpoint.

**Bước 1 — Cluster**

```bash
aws ecs create-cluster --cluster-name <CLUSTER>
```

**Bước 2 — Capacity provider**

```bash
aws ecs create-capacity-provider --cli-input-json file://cp.json
```

```json
{
  "name": "<CP_GENERAL>",
  "cluster": "<CLUSTER>",
  "managedInstancesProvider": {
    "infrastructureRoleArn": "<INFRA_ROLE_ARN>",
    "instanceLaunchTemplate": {
      "ec2InstanceProfileArn": "<INSTANCE_PROFILE_ARN>",
      "networkConfiguration": {
        "subnets": ["<SUBNET_AZ_A>", "<SUBNET_AZ_B>", "<SUBNET_AZ_C>"],
        "securityGroups": ["<SG_ID>"]
      },
      "storageConfiguration": { "storageSizeGiB": 100 },
      "monitoring": "BASIC",
      "instanceRequirements": {
        "vCpuCount": { "min": 2, "max": 8 },
        "memoryMiB": { "min": 4096, "max": 16384 }
      }
    },
    "propagateTags": "CAPACITY_PROVIDER"
  },
  "tags": [{ "key": "team", "value": "<TEAM>" }]
}
```

**Bước 3 — Gắn vào cluster + strategy mặc định**

```bash
aws ecs put-cluster-capacity-providers \
  --cluster <CLUSTER> \
  --capacity-providers <CP_GENERAL> \
  --default-capacity-provider-strategy capacityProvider=<CP_GENERAL>,weight=1
```

**Bước 4 — Task definition** (mục 11.3 mẫu tối thiểu)

```bash
aws ecs register-task-definition --cli-input-json file://taskdef.json
```

**Bước 5 — Service**

```bash
aws ecs create-service \
  --cluster <CLUSTER> --service-name <SERVICE> \
  --task-definition <FAMILY>:<REV> --desired-count 3 \
  --capacity-provider-strategy capacityProvider=<CP_GENERAL>,weight=1 \
  --network-configuration 'awsvpcConfiguration={subnets=[<SUBNET_AZ_A>,<SUBNET_AZ_B>,<SUBNET_AZ_C>],securityGroups=[<SG_ID>]}' \
  --load-balancers targetGroupArn=<TG_ARN_TYPE_IP>,containerName=app,containerPort=8080 \
  --deployment-configuration minimumHealthyPercent=100,maximumPercent=200
```

**Bước 6 — Event window** (EC2): tạo event window giờ thấp điểm, gắn bằng **tag** của managed instance (vd tag propagate từ capacity provider) 🔎.

**Bước 7 — Kiểm tra**

```bash
aws ecs describe-capacity-providers --capacity-providers <CP_GENERAL>
aws ecs list-container-instances --cluster <CLUSTER>
aws ecs describe-services --cluster <CLUSTER> --services <SERVICE>
aws ecs execute-command --cluster <CLUSTER> --task <TASK_ID> --container app --interactive --command "sh"
```

**Standalone task**

```bash
aws ecs run-task --cluster <CLUSTER> --task-definition <FAMILY>:<REV> \
  --capacity-provider-strategy capacityProvider=<CP_GENERAL>,weight=1 \
  --network-configuration 'awsvpcConfiguration={subnets=[<SUBNET_AZ_A>],securityGroups=[<SG_ID>]}'
```

---

## 23. Migrate từ Fargate và từ EC2

> Nguồn: `06-09`, `06-10`, `06-00` mục 16

### 23.1 Năm điều cân nhắc (chung cho cả hai)

| Chủ đề | Nội dung |
|---|---|
| Tương thích task | Phần lớn tương thích (xem 11.3) |
| Mô hình bảo mật | Multi-task mặc định → cân nhắc **single-task mode** |
| Vòng đời | Instance tối đa 14 ngày → dùng **service** để tự thay task |
| Giá | **Instance + phí quản lý** |
| Bảo trì | **EC2 event windows** |

### 23.2 Bốn bước

```
1. Tạo capacity provider Managed Instances (chỉ dùng trong cluster đó)
2. Task definition revision mới: requiresCompatibilities thêm MANAGED_INSTANCES
3. update-service dùng capacity provider mới (+ force new deployment)
4. Standalone task, EventBridge Scheduler, CI/CD, Lambda gọi RunTask → trỏ sang CP mới
```

### 23.3 Khác biệt theo nguồn

| Từ | Điểm cần soát |
|---|---|
| **Fargate** | Platform **1.4.0+**; bỏ `ephemeralStorage`, `proxyConfiguration`; bind mount ở `/var`/`/tmp`; cô lập thay đổi; target group `ip` giữ nguyên |
| **EC2** | Bỏ `bridge`/`none`, `links`, `hostname`, `dnsServers`, `extraHosts`, swap, `sharedMemorySize`, Docker volume; log driver → `awslogs`/`splunk`/`awsfirelens`; bỏ custom AMI/user data/`ecs.config`; không placement strategy; `DAEMON` → **Managed Daemons**; `privileged` → bật trên CP; Windows → không chuyển được |

⚠️ Từ EC2 `bridge` + target type `instance` → `awsvpc` cần **target group mới** type `ip` (không đổi target type trên TG có sẵn 🔎) → an toàn nhất là service song song rồi chuyển traffic.

⚠️ Không trộn `FARGATE` với Managed Instances trong cùng strategy để chuyển dần theo weight. Muốn chuyển dần: **service song song** + chuyển traffic (weighted target group / blue-green) 🔎.

Migrate được **từng service một** trong cùng cluster — cluster chứa lẫn CP ASG/Fargate và Managed Instances.

---

## 24. Checklist

```
Thiết kế
[ ] Workload Linux; không task chạy liên tục > 14 ngày
[ ] Chọn multi-task hay single-task cho từng workload
[ ] Chọn cách chọn instance: mặc định / thuộc tính / type cụ thể; bao nhiêu CP
[ ] On-Demand / Spot / Reservation; nếu Reservation → min 75% / max 100% hoặc headroom
[ ] Kiến trúc CPU: image multi-arch hoặc khai báo cpuArchitecture

Tiên quyết
[ ] Infrastructure role, instance profile, execution role, task role (least privilege)
[ ] iam:CreateServiceLinkedRole cho người/pipeline tạo cluster
[ ] SCP & EC2 Allowed AMIs cho phép account AMI Managed Instances (không dùng ec2:Owner)
[ ] Quota EC2 On-Demand vCPU đủ cho family dự định
[ ] Subnet private ở ≥ 3 AZ, NAT hoặc VPC endpoint (ECR, S3, Logs, SSM)

Capacity provider
[ ] storageSizeGiB đủ cho image (nén + giải nén) + bind mount
[ ] Instance store nếu cần I/O nhanh (dữ liệu tạm)
[ ] Capability đặc quyền chỉ bật khi cần
[ ] ScaleInAfter phù hợp kiểu tải
[ ] Monitoring basic/detailed; propagateTags = CAPACITY_PROVIDER
[ ] FIPS / VPC Encryption Controls nếu có yêu cầu tuân thủ

Task definition
[ ] requiresCompatibilities có MANAGED_INSTANCES
[ ] networkMode awsvpc/host; không tham số ⛔
[ ] Log driver awslogs/splunk/awsfirelens
[ ] stopTimeout đủ; app bắt SIGTERM; health check
[ ] Image tag bất biến; image nhỏ

Service
[ ] Desired count ≥ 2, nhiều AZ; target group type ip
[ ] Task scale-in protection cho job dài (nếu cần)
[ ] Service auto scaling cho số task

Vận hành
[ ] EC2 event window giờ thấp điểm
[ ] Alarm health events (GPU, instance), task stopped
[ ] Script kiểm kê / cost lọc theo tag aws:ec2:managed-launch
[ ] Đổi cấu hình bằng CP mới, không update tại chỗ
```

---

## 25. Troubleshooting

> 🔎 Tổng hợp từ các mục trên; nguyên nhân là các khả năng thường gặp, không phải danh sách đầy đủ.

| Triệu chứng | Nguyên nhân có thể | Cách xử lý |
|---|---|---|
| Không instance nào được launch, task kẹt `PROVISIONING`/`PENDING` | SCP / Allowed AMIs chặn AMI; thiếu quyền infrastructure role; hết quota EC2; không type nào khớp `instanceRequirements`; hết capacity type cụ thể ở AZ | Kiểm tra SCP (mục 17), quota, nới `instanceRequirements` / chọn theo thuộc tính |
| Deploy kẹt, không đủ task mới | Capacity Reservation đã dùng hết + max 200% | min < 100%, max 100%, hoặc headroom (7.2) |
| Register task definition lỗi | Tham số ⛔ (mục 11.3), thiếu `networkMode` | Bỏ tham số, khai báo `awsvpc`/`host` |
| Container lỗi ghi file bind mount | `sourcePath` ngoài `/var`, `/tmp` (root FS bất biến) | Đổi đường dẫn, hoặc dùng EFS/EBS |
| Task bị dừng "tự nhiên" định kỳ | Instance drain theo vòng đời 14 ngày / gom underutilized / GPU impaired | Bình thường — đảm bảo tắt êm, desired ≥ 2, event window; xem `stoppedReason` |
| Task bị dời liên tục | ScaleInAfter thấp + tải dao động | Tăng ScaleInAfter hoặc `-1` |
| Task khởi động chậm | Instance mới, cache trống; image lớn; image bị dọn sau 15 phút | Image nhỏ, ECR cùng Region, ScaleInAfter cao hơn |
| Ít task/instance hơn mong đợi | Giới hạn ENI với `awsvpc`; trải AZ ưu tiên hơn bin packing | ENI trunking, instance lớn hơn, một subnet cho dev |
| Task launch thất bại ở subnet Enforced | Type không hỗ trợ encryption in transit (T3/T3a/T4g…) | `allowedInstanceTypes` chỉ type hỗ trợ |
| Không thấy instance trong EC2 console | Ẩn mặc định từ 22/4/2026 | Chỉnh visibility setting, lọc `Operator`/tag |
| Không SSH được để debug | Không có SSH | **ECS Exec** (không hỗ trợ IPv6-only) |
| Agent giám sát lỗi quyền | Capability đặc quyền chưa bật trên CP | Bật trên capacity provider |
| Đổi CP xong không thấy khác | Update tại chỗ chỉ áp cho instance mới | Tạo CP mới + chuyển service |
| `DEPLOYMENT_BLOCKED` | Quá nhiều task đang được scale-in protection | Tắt protection khi xong việc, chỉnh max percent |
| Xoá CP không được | Còn service dùng trong strategy | Gỡ khỏi strategy + force new deployment |

---

## 26. Chỗ docs có lỗi / không khớp

| Chủ đề | Chỗ không khớp | Nên hiểu |
|---|---|---|
| Vòng đời | `06-00`, `06-05`, `06-09`: "tối đa **14 ngày**"; `06-04`: **14–21 ngày** | 14 = bắt đầu drain, 21 = terminate chậm nhất |
| Tên trường storage | `06-00`: `storageSizeinGiB` ở ví dụ đầu, `storageSizeGiB` ở chỗ khác | Dùng `storageSizeGiB`, đối chiếu API reference |
| `requiresCompatibilities` | `06-09`, `06-10` viết "requiresCapabilities" | Tên đúng: **`requiresCompatibilities`** |
| Placement strategy | `06-02`, `14-01-00` nói default CP xét `placementStrategy`; folder 15 nói Managed Instances **không hỗ trợ** strategy | Không dựa vào strategy; điều khiển bằng subnet/constraint |
| Ví dụ SCP | `06-00`: ví dụ nói cho us-west-2/us-east-1 nhưng một account ID là của **us-west-1** | Lấy account ID từ bảng theo đúng Region |
| Đổi FIPS | `06-05` mục 4: "tạo **hoặc cập nhật**"; mục 6: tắt khi tạo **mới** | An toàn nhất: tạo CP mới |
| Phí quản lý | `06-00` chỉ nói trả tiền instance; `06-09`/`06-10` thêm **phí quản lý** | Tổng = instance + phí quản lý |
| Lệnh CLI update cluster | `14-01-02`: ví dụ có lỗi cú pháp | Dùng dạng JSON `--managed-instances-provider` |

---

## 27. Câu hỏi tự kiểm tra (có đáp án)

**1. Managed Instances khác Fargate ở điểm cốt lõi nào?**
<details><summary>Đáp án</summary>

Bạn **chọn được instance type** (GPU, Graviton, mạng cao, instance store) và mặc định **nhiều task chạy chung một instance**; tiền trả theo **instance + phí quản lý** thay vì theo vCPU/memory từng task. Fargate thì không chọn phần cứng, mỗi task cô lập.
</details>

**2. Khác EC2 tự quản lý ở điểm nào?**
<details><summary>Đáp án</summary>

AWS lo AMI (Bottlerocket), patch, scale, thay máy; **không custom AMI**, **không SSH**, instance chỉ sống **14–21 ngày**, không placement strategy, network chỉ `awsvpc`/`host`, chỉ Linux, daemon dùng Managed Daemons.
</details>

**3. Có trộn được Managed Instances với `FARGATE_SPOT` trong cùng capacity provider strategy không?**
<details><summary>Đáp án</summary>

**Không.** Một strategy chỉ chứa **một loại** provider. Cluster thì chứa lẫn được nhiều loại.
</details>

**4. Không khai báo `instanceRequirements` thì ECS chọn instance dựa trên gì?**
<details><summary>Đáp án</summary>

Instance general-purpose **rẻ nhất** khớp `operatingSystemFamily`, `cpuArchitecture`, `cpu`, `memory` của task definition và `placementConstraints`/`placementStrategy` của service.
</details>

**5. Trong chọn theo thuộc tính, hai thuộc tính và hai giá trị của một thuộc tính kết hợp thế nào?**
<details><summary>Đáp án</summary>

Giữa các thuộc tính là **AND**; giữa các giá trị trong một thuộc tính là **OR**.
</details>

**6. Vì sao docs khuyên chọn theo thuộc tính cho đa số workload?**
<details><summary>Đáp án</summary>

Khớp **nhiều type** hơn → ít bị hết capacity, đặt task dễ thành công, và ECS chọn được loại **rẻ nhất đang có** lúc launch.
</details>

**7. `m6*.*` trong `allowedInstanceTypes` nghĩa là gì?**
<details><summary>Đáp án</summary>

Mọi size của mọi family thế hệ m6: m6i, m6a, m6g, m6gd…
</details>

**8. Có chạy được `t3.micro` trên Managed Instances không?**
<details><summary>Đáp án</summary>

Không. Managed Instances chỉ tạo instance **> 1 vCPU** và **không phải** `nano`/`micro`.
</details>

**9. Service có 4 task, Capacity Reservation vừa đủ 4 task, deployment mặc định. Chuyện gì xảy ra khi deploy, và sửa thế nào?**
<details><summary>Đáp án</summary>

Mặc định min 100% / max 200% cần tới 8 task tạm thời → không đủ capacity → **deploy kẹt/thất bại**. Sửa: `minimumHealthyPercent` < 100% (vd 75%) và `maximumPercent` = 100%, hoặc giữ headroom trong reservation.
</details>

**10. Vòng đời instance chính xác là bao lâu? Khi nào bị drain sớm hơn?**
<details><summary>Đáp án</summary>

Bắt đầu drain **ngày 14**, terminate chậm nhất **ngày 21**. Drain sớm hơn khi có **lỗ hổng bảo mật**, **phần cứng suy giảm**, hoặc để **khớp event window**.
</details>

**11. Tới ngày 21 instance vẫn chưa drain xong thì sao?**
<details><summary>Đáp án</summary>

ECS gọi **`DeregisterContainerInstance`**, dừng instance và **workload còn lại** (cưỡng bức).
</details>

**12. Đổi event window có ảnh hưởng instance đang chạy không?**
<details><summary>Đáp án</summary>

Không. Thay đổi chỉ áp cho instance **launch mới**. Event window dùng theo kiểu **best effort**.
</details>

**13. Idle detection và underutilized detection khác nhau thế nào? Cái nào không chạy với Spot?**
<details><summary>Đáp án</summary>

Idle: instance **không có task** → `DEREGISTERING` → terminate, kích hoạt khi task cuối dừng. Underutilized: **gom task của service** vào ít instance hơn (`DRAINING` → thay task → `DEREGISTERING`), kích hoạt mỗi lần task dừng hoặc instance mới launch. **Underutilized không hỗ trợ Spot** và không xét instance có standalone task.
</details>

**14. `ScaleInAfter = -1` nghĩa là gì? Giá trị hợp lệ?**
<details><summary>Đáp án</summary>

`-1` **tắt** tối ưu hạ tầng. Hợp lệ: `0`–`3600` giây; không khai báo thì ECS tự chọn.
</details>

**15. Với ScaleInAfter = 600, task cuối dừng lúc t=0, task mới chạy lúc t=300. Instance có bị xoá không?**
<details><summary>Đáp án</summary>

Không. Có task mới trong lúc chờ → instance không còn idle → **huỷ** terminate.
</details>

**16. Container ghi log ra bind mount `/data/logs` bị lỗi. Vì sao?**
<details><summary>Đáp án</summary>

Root filesystem của Bottlerocket **bất biến**; bind mount `sourcePath` phải nằm trong thư mục ghi được **`/var`** hoặc **`/tmp`**. Đổi sang `/var/...` hoặc dùng EFS/EBS.
</details>

**17. Nêu 5 tham số task definition không hợp lệ trên Managed Instances.**
<details><summary>Đáp án</summary>

Bất kỳ 5 trong: `ephemeralStorage`, `proxyConfiguration`, `inferenceAccelerator`, `dockerVolumeConfiguration`, `links`, `hostname`, `dnsServers`, `dnsSearchDomains`, `extraHosts`, `maxSwap`, `swappiness`, `sharedMemorySize`, `tmpfs`, `ipcMode`, `disableNetworking`, `dockerSecurityOptions`, `fsxWindowsFileServerVolumeConfiguration`.
</details>

**18. Network mode nào được hỗ trợ? Target group của ALB phải là type gì?**
<details><summary>Đáp án</summary>

`awsvpc` (mặc định) và `host`; network mode **bắt buộc** khai báo. Với `awsvpc`, target type **`ip`**.
</details>

**19. EBS data volume của instance: min/max/mặc định? Chứa những gì?**
<details><summary>Đáp án</summary>

30 / 16.384 / **80 GiB**. Dùng chung cho mọi task (bind mount) và chứa cả image đã pull (bản nén + giải nén).
</details>

**20. Muốn mọi instance bắt buộc có instance store SSD ≥ 100 GiB thì cấu hình gì?**
<details><summary>Đáp án</summary>

Capacity provider mới với `localStorageConfiguration.useLocalStorage: true` + `instanceRequirements.localStorage: REQUIRED`, `totalLocalStorageGB.min: 100`, `localStorageTypes: ["ssd"]`. Kiểm tra bằng thuộc tính `ecs.capability.storage.local-storage-enabled`.
</details>

**21. Image bị xoá khỏi cache khi nào?**
<details><summary>Đáp án</summary>

Khi task cuối dùng image dừng → bắt đầu **timer 15 phút**; không có task mới dùng image đó trên instance trong 15 phút → xoá.
</details>

**22. Vì sao resolve tag → digest ảnh hưởng bin packing?**
<details><summary>Đáp án</summary>

ECS phải **đặt 1 task trước** để lấy digest rồi mới đặt các task còn lại → các task không được xếp cùng lúc một cách tối ưu.
</details>

**23. Capability đặc quyền nào được hỗ trợ? Bật ở đâu?**
<details><summary>Đáp án</summary>

`CAP_NET_ADMIN`, `CAP_SYS_ADMIN`, `CAP_BPF`, `CAP_PERFMON` (và các capability đặc quyền khác). Phải **bật tường minh trên capacity provider**, ngoài khai báo ở task definition.
</details>

**24. Làm sao nhận diện một EC2 instance là managed instance của ECS? Vì sao có thể không thấy nó trong `describe-instances`?**
<details><summary>Đáp án</summary>

`Operator = ecs.amazonaws.com` hoặc tag `aws:ec2:managed-launch = ecs-managed-instances`. Từ **22/4/2026**, EC2 mặc định **ẩn** managed instance mới khỏi console và list API (vẫn tính tiền).
</details>

**25. Cần FIPS-140 ở ap-southeast-1 trên Graviton. Được không?**
<details><summary>Đáp án</summary>

Không. AMI FIPS chỉ có ở **GovCloud (US)** và task phải **`X86_64`**.
</details>

**26. Bật VPC Encryption Controls ở chế độ Enforced, capacity provider dùng `t3.*` thì sao?**
<details><summary>Đáp án</summary>

T3/T3a/T4g **không hỗ trợ** encryption in transit → task **launch thất bại**. Cần custom CP với `allowedInstanceTypes` chỉ gồm type hỗ trợ (lọc bằng `describe-instance-types` với `network-info.encryption-in-transit-supported=true`).
</details>

**27. Công ty có SCP chỉ cho launch AMI nội bộ. Bật Managed Instances bị lỗi. Sửa thế nào? Vì sao không dùng `ec2:Owner = amazon`?**
<details><summary>Đáp án</summary>

SCP áp lên **infrastructure role** → thêm các **account AMI Managed Instances** của Region liên quan vào điều kiện `aws:ResourceAccount` (và vào EC2 Allowed AMIs nếu dùng). `ec2:Owner = amazon` sẽ cho phép **mọi** AMI của Amazon, quá rộng.
</details>

**28. GPU auto repair drain tối đa bao nhiêu instance cùng lúc? Tắt nó có tác dụng phụ gì?**
<details><summary>Đáp án</summary>

Tối đa **20%** instance của CP; < 9 instance thì tối đa **1**. Tắt (`autoRepairConfiguration.actionsStatus = DISABLED`) thì **daemon auto repair cũng bị tắt**; ECS vẫn theo dõi nhưng không tự thay.
</details>

**29. Vì sao nên coi capacity provider là bất biến?**
<details><summary>Đáp án</summary>

Update tại chỗ chỉ áp cho capacity **mới** và **không** kích hoạt deployment → lỗi cấu hình có thể lộ ra rất lâu sau khi scale. Tạo CP mới → chuyển từng service → xoá CP cũ cho phép rollout có kiểm soát và rollback dễ.
</details>

**30. Migrate từ EC2 `bridge` mode + ALB target type `instance` cần làm thêm gì?**
<details><summary>Đáp án</summary>

Chuyển `networkMode` sang `awsvpc` (hoặc `host`), tạo **target group mới type `ip`**, bỏ các tham số ⛔, đổi log driver nếu cần; an toàn nhất là chạy service song song rồi chuyển traffic.
</details>

**31. Một job xử lý video chạy 3 ngày dưới dạng task của service. Bảo vệ nó khỏi bị dừng khi gom instance thế nào? Có giới hạn gì?**
<details><summary>Đáp án</summary>

Bật **task scale-in protection** (`ProtectionEnabled: true`, `ExpiresInMinutes` tối đa 2880 = 48 giờ → phải gia hạn định kỳ). Consolidation tôn trọng protection. Nhưng 🔎 vòng đời instance vẫn có mốc terminate **ngày 21**, nên job cần checkpoint.
</details>

**32. Managed Instances tính tiền gồm những gì? Savings Plans nào áp dụng?**
<details><summary>Đáp án</summary>

Tiền **cả EC2 instance** + **phí quản lý**. **Compute SP**, **Instance SP** và **RI** tự áp dụng, không cần cấu hình.
</details>

---

## 28. Bản đồ nguồn

| Mục | File nguồn |
|---|---|
| 1, 2, 4, 11, 12, 13, 15, 17, 20 | `06-00-ecs-managed-instances-architect.md` |
| 5, 6 | `06-01-ecs-managed-instances-instance-types.md` |
| 7 | `06-02-ecs-managed-instances-selection-best-practices.md` |
| 14 | `06-03-ecs-managed-instances-pull-behavior.md` |
| 10 | `06-04-ecs-managed-instances-patching.md` |
| 15, 16 | `06-05-ecs-managed-instances-security.md` |
| 16 | `06-06-ecs-managed-instances-vpc-encryption.md` |
| 9 | `06-07-ecs-managed-instances-infrastructure-optimization.md` |
| 18 | `06-08-ecs-managed-instances-gpu-auto-repair.md` |
| 23 | `06-09-ecs-migrate-fargate-to-managed-instances.md`, `06-10-ecs-migrate-ec2-to-managed-instances.md` |
| 11 | Folder 13: `13-02-07`, `13-09` |
| 12 | Folder 13: `13-02-02` |
| 8 | Folder 15: `15-02-01` (+ `15-02-00`, `15-02-04`) |
| 4, 21 | Folder 14: `14-01-00` → `14-01-03-04` |
| 19 | Folder 07 (Managed Daemons); folder 14: `14-01-04-00`, `14-01-04-01` |
| 3, 22, 24, 25 | ✍️ / 🔎 tổng hợp |

Liên quan: `06-99-thuyet-trinh-cto.md` (slide cho CTO), file toàn tập *Fargate* (`08-98`), *Architect* (`05-98`), *Task definition* (`13-98`).

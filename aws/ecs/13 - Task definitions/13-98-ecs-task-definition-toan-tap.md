# ECS Task Definition — Toàn tập

> Tổng hợp từ **toàn bộ folder `13 - Task definitions`** (`13-01` → `13-11`, `13-99`), cùng cách làm với hai file toàn tập *Service Connect* và *Load balancing* ở folder 15.
>
> **Ký hiệu**
> - Không ký hiệu = lấy từ docs AWS đã dịch trong folder (có ghi file nguồn ở đầu mỗi mục).
> - 🔎 = kiến thức ngoài folder hoặc suy luận của mình — **nên tự kiểm tra lại** với docs AWS hiện tại.
> - ✍️ = ví dụ tự đặt (tên, giá trị, ARN là placeholder).
> - ⚠️ = bẫy hay gặp.

---

## Mục lục

0. [Tóm tắt một trang](#0-tóm-tắt-một-trang)
1. [Task definition là gì, nằm ở đâu trong ECS](#1-task-definition-là-gì-nằm-ở-đâu-trong-ecs)
2. [Cấu trúc: mức task và mức container](#2-cấu-trúc-mức-task-và-mức-container)
3. [Family, revision và vòng đời trạng thái](#3-family-revision-và-vòng-đời-trạng-thái)
4. [Ba nguyên tắc thiết kế](#4-ba-nguyên-tắc-thiết-kế)
5. [Nền tảng chạy: `requiresCompatibilities` và runtime platform](#5-nền-tảng-chạy-requirescompatibilities-và-runtime-platform)
6. [IAM: task role và execution role](#6-iam-task-role-và-execution-role)
7. [CPU và memory: reservation, limit, mức task, mức container](#7-cpu-và-memory-reservation-limit-mức-task-mức-container)
8. [Network mode và port mapping](#8-network-mode-và-port-mapping)
9. [Container definition chi tiết](#9-container-definition-chi-tiết)
10. [Logging](#10-logging)
11. [Storage: volume, EBS, EFS, bind mount, swap](#11-storage-volume-ebs-efs-bind-mount-swap)
12. [Khác biệt giữa EC2, Managed Instances, Fargate, Windows](#12-khác-biệt-giữa-ec2-managed-instances-fargate-windows)
13. [Dual-stack và IPv6-only (phần liên quan task definition)](#13-dual-stack-và-ipv6-only-phần-liên-quan-task-definition)
14. [Tăng tốc khởi động: SOCI lazy loading](#14-tăng-tốc-khởi-động-soci-lazy-loading)
15. [Triển khai từng bước](#15-triển-khai-từng-bước)
16. [Checklist best practice](#16-checklist-best-practice)
17. [Troubleshooting](#17-troubleshooting)
18. [Chỗ docs không khớp nhau](#18-chỗ-docs-không-khớp-nhau)
19. [Câu hỏi tự kiểm tra (có đáp án)](#19-câu-hỏi-tự-kiểm-tra-có-đáp-án)
20. [Bản đồ nguồn](#20-bản-đồ-nguồn)

---

## 0. Tóm tắt một trang

| Ý | Nội dung |
|---|---|
| Là gì | Bản mô tả (JSON) cách chạy **một nhóm container**: image, CPU/memory, network mode, IAM role, log, volume… |
| Bắt buộc tối thiểu | `family` + `containerDefinitions` (mỗi container cần `name`, `image`). Fargate còn bắt buộc `networkMode: awsvpc`, `cpu`, `memory` mức task |
| Bất biến | Mỗi lần sửa = **register revision mới** (`my-app:1`, `my-app:2`…). Không sửa được revision cũ |
| Trạng thái | `ACTIVE` → (deregister) `INACTIVE` → (delete) `DELETE_IN_PROGRESS` → xoá vĩnh viễn |
| Deploy | Deploy = **trỏ service sang revision mới**. Rollback = trỏ lại revision cũ |
| 3 nguyên tắc | 1 family = 1 mục đích nghiệp vụ · commit ↔ image tag ↔ revision là 1–1 · 1 family = 1 IAM role |
| 2 role | **Task role** = quyền của code app. **Execution role** = quyền của ECS agent/Fargate để pull image, ghi log, lấy secret |
| Tài nguyên | CPU tính bằng unit (1024 = 1 vCPU), memory tính bằng MiB. Vượt CPU → throttle; **vượt memory → container bị kill** |
| Network | Khuyến nghị `awsvpc` mọi nơi; Fargate bắt buộc. EC2 Linux còn `bridge` (mặc định), `host`, `none` |
| Kiểm tra sớm | Luôn khai báo `requiresCompatibilities` (`FARGATE` / `EC2` / `MANAGED_INSTANCES`) để ECS validate lúc register |

---

## 1. Task definition là gì, nằm ở đâu trong ECS

> Nguồn: `13-02-00`, `13-99`

Theo docs, bạn **thiết kế kiến trúc ứng dụng trên ECS bằng cách tạo task definition**. Task definition trả lời 5 câu hỏi:

| Thành phần | Câu hỏi |
|---|---|
| Capacity | Task chạy trên hạ tầng nào (EC2 / Managed Instances / Fargate; GPU cần tham số riêng)? |
| Container image | Chạy code gì (code + toàn bộ dependency)? |
| Network mode | Task giao tiếp mạng thế nào? |
| Logging | Log đi đâu? |
| Data volume | Container lưu/chia sẻ dữ liệu ở đâu? |

🔎 Hình dung quan hệ các khái niệm:

```
Task definition (bản thiết kế, có revision)    ≈ "class"
      │ RunTask / Service dùng nó
      ▼
Task (bản đang chạy của task definition)      ≈ "object"
      │ gồm 1..n container chạy cùng nhau trên cùng host, cùng network namespace (awsvpc)
      ▼
Service (giữ N task luôn chạy, gắn LB, deploy revision mới)
      │
Cluster (nhóm logic chứa service/task + capacity)
```

Vị trí trong folder tổng thể (theo `13-99`):

```
13 Task definition ──► chạy trên compute (EC2 / Managed Instances / Fargate) — mỗi nền tảng hỗ trợ tham số khác nhau
                   ──► được service (15) deploy — deploy = trỏ service tới revision mới
                   ──► gateway / Service Connect dùng network mode awsvpc + port có tên (portMappings.name)
```

---

## 2. Cấu trúc: mức task và mức container

> Nguồn: `13-09`, `13-10`, `13-11`

Docs: task definition gồm **family, IAM task role, network mode, container definitions, volumes, launch type**. Bắt buộc chỉ có `family` và container definitions.

```
Task definition
├── Mức task
│   ├── family                       (bắt buộc)
│   ├── requiresCompatibilities      FARGATE | EC2 | MANAGED_INSTANCES
│   ├── taskRoleArn / executionRoleArn
│   ├── networkMode                  awsvpc | bridge | host | none | default(Windows)
│   ├── runtimePlatform              operatingSystemFamily, cpuArchitecture
│   ├── cpu / memory                 (bắt buộc trên Fargate)
│   ├── volumes[]                    bind mount, EFS, EBS, Docker volume, FSx Windows
│   ├── placementConstraints[]       (EC2, Managed Instances)
│   ├── ephemeralStorage, pidMode, ipcMode, proxyConfiguration, enableFaultInjection
│   └── tags[]
└── containerDefinitions[]           (bắt buộc, ≥ 1, ít nhất 1 container essential)
    ├── name, image, versionConsistency, repositoryCredentials
    ├── cpu, memory (hard), memoryReservation (soft)
    ├── portMappings[]               containerPort, hostPort, protocol, name, appProtocol, containerPortRange
    ├── essential, entryPoint, command, workingDirectory
    ├── environment[], environmentFiles[], secrets[]
    ├── healthCheck, restartPolicy, dependsOn[], startTimeout, stopTimeout
    ├── logConfiguration, firelensConfiguration
    ├── mountPoints[], volumesFrom[], readonlyRootFilesystem
    ├── linuxParameters (capabilities, initProcessEnabled, maxSwap, swappiness, tmpfs, devices…)
    ├── ulimits[], user, privileged, systemControls[], dockerLabels
    └── interactive, pseudoTerminal, credentialSpecs
```

⚠️ `networkConfiguration` (subnet, security group, `assignPublicIp`) **không nằm trong task definition** — nó là tham số của **service / RunTask** (`13-02-08`). Task definition chỉ khai báo `networkMode`.

🔎 Phân biệt nhanh cái gì đặt ở đâu:

| Đặt trong task definition | Đặt trong service / RunTask |
|---|---|
| Image, CPU/memory, port, env, secret, log, volume, role, network mode | Subnet, SG, public IP, desired count, LB/target group, capacity provider strategy, deployment config, EBS volume config (khi `configuredAtLaunch`) |

---

## 3. Family, revision và vòng đời trạng thái

> Nguồn: `13-01`, `13-10`

### 3.1 Family và revision

- `family` là tên; revision đầu là **1**, mỗi lần register cùng family thì tăng dần.
- Trạng thái áp dụng cho **từng revision**, không phải cả family. Một family có thể vừa có revision `ACTIVE` vừa có `INACTIVE`.
- 🔎 Revision là **bất biến**: "sửa" task definition trên console thực chất là tạo revision mới. Gọi `my-app` không kèm số revision thì ECS lấy revision `ACTIVE` mới nhất.

### 3.2 Ba trạng thái

```
register            deregister              gửi yêu cầu xoá             không còn ai tham chiếu
────────► ACTIVE ─────────────► INACTIVE ──────────────────► DELETE_IN_PROGRESS ──────────────────► (xoá vĩnh viễn)
```

| Trạng thái | Chạy task / tạo service mới | Ảnh hưởng task & service đang chạy | `DescribeTaskDefinition` |
|---|---|---|---|
| `ACTIVE` | Được | — | Được |
| `INACTIVE` | **Không** | Không | Được |
| `DELETE_IN_PROGRESS` | **Không** | Không | Được (và hiện trên console) |

### 3.3 Xoá revision

- Phải deregister (`INACTIVE`) trước rồi mới gửi xoá. 🔎 API: `DeregisterTaskDefinition`, `DeleteTaskDefinitions`.
- Việc xoá **bị chặn** khi còn: standalone task, task của service, **service deployment hoặc task set** đang tham chiếu revision.
- Sau khi gỡ tài nguyên chặn: tối đa **1 giờ** (task) hoặc **24 giờ** (deployment / task set) để xoá xong — ECS kiểm tra định kỳ.
- Xoá hết revision → tên biến mất khỏi console/API, nhưng ECS **giữ tên**: register lại thì revision **tiếp tục tăng** (xoá 1–5 rồi tạo lại → revision 6). Nhờ vậy `family:revision` không bao giờ trỏ tới hai nội dung khác nhau.

### 3.4 Chi phí AWS Config

| Thao tác | Phí AWS Config (nếu bật ghi tài nguyên task definition) |
|---|---|
| Register | Có, **mọi lần** |
| Deregister revision `ACTIVE` mới nhất | Có |
| Deregister revision khác | Không |
| Xoá | Không |

⚠️ Pipeline CI/CD register revision mỗi lần build → mỗi lần đều tính phí Config.

---

## 4. Ba nguyên tắc thiết kế

> Nguồn: `13-02-00`

**Nguyên tắc 1 — Mỗi family chỉ cho một mục đích nghiệp vụ.** Gộp web + API vào một task thì mỗi task luôn có đúng 1 web + 1 API, không scale riêng được.

```
Gộp (không nên):  task = [web + api]  → 6 web thì cũng phải 6 api
Tách (nên):       family "web" → service web (6)     family "api" → service api (2)
```

Ngoại lệ (theo `13-99`): **sidecar phục vụ trực tiếp cho app** (log router, proxy) thì để chung task.

**Nguyên tắc 2 — Mỗi phiên bản code ứng với một revision.** Ánh xạ 1–1: commit ↔ image tag (git SHA) ↔ revision.

```
git commit a1b2c3d → image my-app:a1b2c3d → task definition my-app:42 → update-service → deploy 42
```

Lợi ích: nhìn revision biết đang chạy commit nào; rollback = deploy lại revision cũ.

**Nguyên tắc 3 — Mỗi family một IAM role riêng.** Ví dụ docs: auth service được vào DB mật khẩu, chỉ order service được vào dữ liệu thẻ. Nguyên tắc 1 là điều kiện của nguyên tắc 3: gộp nhiều thành phần vào một task thì buộc phải dùng chung quyền.

---

## 5. Nền tảng chạy: `requiresCompatibilities` và runtime platform

> Nguồn: `13-02-07`, `13-02-08`, `13-09`, `13-10`, `13-11`

### 5.1 `requiresCompatibilities`

- Giá trị: `FARGATE`, `EC2`, `MANAGED_INSTANCES`.
- Khi khai báo, ECS **kiểm tra mọi tham số** có hợp lệ với nền tảng đó **ngay lúc register**; không hợp lệ thì trả client exception.
- Khai báo qua: console (*Requires Compatibilities*), CLI `--requires-compatibilities`, API `requiresCompatibilities`.
- Managed Instances: **phải có** `MANAGED_INSTANCES`.

### 5.2 `runtimePlatform`

| Trường | EC2 | Managed Instances | Fargate |
|---|---|---|---|
| `operatingSystemFamily` | Linux + Windows Server 2016/2019/2022/2025/2004/20H2 | Chỉ `LINUX` | `LINUX`, Windows Server 2019/2022/2025 Full/Core (bắt buộc khai báo) |
| `cpuArchitecture` | Không mặc định | Không mặc định (theo capacity provider) | Mặc định `X86_64`; `ARM64` cho Linux |

- Mọi task definition trong **một service** phải cùng `operatingSystemFamily` và `cpuArchitecture`; `operatingSystemFamily` phải khớp `platformFamily` của service.
- Windows trên Fargate: chỉ `X86_64`.
- Container Linux chỉ dùng **kernel** của host, nên image dựng từ distro nào (Ubuntu, Alpine…) cũng chạy được, **miễn đúng kiến trúc CPU**.

⚠️ Image ARM64 (Graviton) mà quên khai báo `cpuArchitecture: ARM64` trên Fargate → task chạy trên x86 → lỗi `exec format error` (🔎 thông báo lỗi cụ thể).

---

## 6. IAM: task role và execution role

> Nguồn: `13-02-04` (bảng luồng traffic), `13-09`, `13-10`, `13-11`, `13-02-09`

| | Task role (`taskRoleArn`) | Execution role (`executionRoleArn`) |
|---|---|---|
| Ai dùng | **Code trong container** | **ECS agent / Fargate** |
| Dùng để | App gọi AWS API (S3, DynamoDB, SQS…), gửi log qua **FireLens**, truy cập **EFS** (khi bật IAM authorization) | Lấy credential ECR, **pull image ECR private**, gửi log qua log driver (**`awslogs`**), lấy **secret** từ Secrets Manager / SSM |
| Bắt buộc? | Không | **Có điều kiện** — bắt buộc khi pull ECR private hoặc dùng `awslogs` |

Bảng của docs (Fargate) — ai dùng role nào:

| Hành động | IAM permission |
|---|---|
| Lấy thông tin đăng nhập ECR | Execution role |
| Pull image | Execution role |
| Gửi log qua log driver | Execution role |
| Gửi log qua FireLens | **Task role** |
| Lấy secret (Secrets Manager / SSM) | Execution role |
| Traffic EFS | **Task role** |
| Traffic ứng dụng | **Task role** |

- Windows trên EC2: task role cần **cấu hình bổ sung** — bật bằng tuỳ chọn khởi tạo `-EnableTaskIAMRole` trên AMI Windows (`13-11`).
- Nguyên tắc "mỗi family một role" (mục 4) nói về **task role**.

🔎 Execution role thường gắn managed policy `AmazonECSTaskExecutionRolePolicy` (ECR + CloudWatch Logs); muốn đọc secret phải thêm quyền `secretsmanager:GetSecretValue` / `ssm:GetParameters` (và `kms:Decrypt` nếu secret dùng KMS key riêng).

---

## 7. CPU và memory: reservation, limit, mức task, mức container

> Nguồn: `13-02-01`, `13-02-08`, `13-09`, `13-10`, `13-11`, `13-02-09`

### 7.1 Đơn vị

- CPU: **1024 unit = 1 vCPU** (256 = 0,25 vCPU). Có thể ghi `"1 vCPU"`, khi register được đổi sang unit.
- Memory: **MiB**. Có thể ghi `"3 GB"`, khi register được đổi sang MiB.

### 7.2 Reservation và limit

```
0 ──────── reservation ─────────────── limit ──────►
           ▲ luôn được đảm bảo          ▲ trần cứng
           │◄──── vùng bursting ───────►│
```

| | Ý nghĩa | Vượt thì sao |
|---|---|---|
| **Reservation** (`memoryReservation` = soft limit) | Mức tối thiểu, **được đảm bảo**; trên EC2 scheduler không đặt task lên instance không đủ reservation | Được phép burst lên tới limit |
| **Limit** (`memory` = hard limit) | Mức tối đa | CPU vượt → **throttle** (chậm nhưng sống). Memory vượt → **container bị kill** |

- `memory` container tối thiểu: Docker 20.10.0+ giữ **6 MiB**; Docker 19.03.13-ce trở xuống giữ **4 MiB**.
- Tổng `memory` / `cpu` của các container phải **thấp hơn** giá trị mức task (nếu có).
- `cpu` container trên Linux là **CPU share** (2–262144); trên Windows là **quota tuyệt đối**.
- `memoryReservation` không hỗ trợ Windows.

### 7.3 Mức task theo nền tảng

| | EC2 | Managed Instances | Fargate | EC2 Windows |
|---|---|---|---|---|
| `cpu`/`memory` mức task | Tuỳ chọn, CPU 0,125–192 vCPU; memory phải có ở mức task **hoặc** container | Tuỳ chọn, CPU 0,125–10 vCPU | **Bắt buộc**, theo bảng tổ hợp | **Không hỗ trợ** — đặt ở mức container |
| Trả tiền theo | Instance | Instance | **Kích thước task** | Instance |

Bảng tổ hợp Fargate:

| CPU | Memory | OS |
|---|---|---|
| 256 (0,25 vCPU) | 512 MiB, 1 GB, 2 GB | Linux |
| 512 (0,5 vCPU) | 1 → 4 GB | Linux |
| 1024 (1 vCPU) | 2 → 8 GB | Linux, Windows |
| 2048 (2 vCPU) | 4 → 16 GB, bước 1 GB | Linux, Windows |
| 4096 (4 vCPU) | 8 → 30 GB, bước 1 GB | Linux, Windows |
| 8192 (8 vCPU) — Linux platform 1.4.0+ | 16 → 60 GB, bước 4 GB | Linux |
| 16384 (16 vCPU) — 1.4.0+ | 32 → 120 GB, bước 8 GB | Linux |
| 32768 (32 vCPU) — 1.4.0+ | 60, 120, 244 GB | Linux |

- Windows trên Fargate: `cpu` ≥ 1 vCPU; CPU/memory mức task chỉ dùng để chọn kích thước, **không áp đặt lúc chạy** (Windows không có cơ chế giới hạn chung cho nhóm container) → muốn giới hạn thì đặt ở mức container.

### 7.4 Chọn con số

- Luôn dựa trên **load test**.
- **Stateless scale ngang**: đo memory khi đang phục vụ request (`ps`, `top`, Container Insights). CPU nhỏ (256) → scale mịn, rẻ, nhưng có thể không kịp spike; CPU lớn → bắt kịp nhanh hơn, đắt hơn.
- **Không scale ngang** (singleton worker, database): ưu tiên capacity và chi phí, chọn số để đạt **SLO**.

🔎 Với JVM (Java/Spring), nhớ đặt heap theo memory container (`-XX:MaxRAMPercentage=75`) để phần non-heap không đẩy container vượt hard limit.

---

## 8. Network mode và port mapping

> Nguồn: `13-02-02`, `13-02-03`, `13-02-04`, `13-10`, `13-11`

### 8.1 Các network mode

| Mode | EC2 Linux | EC2 Windows | Managed Instances | Fargate | Mô tả |
|---|---|---|---|---|---|
| `awsvpc` | ✅ | ✅ | ✅ (mặc định trên trang tham số) | ✅ **bắt buộc** | Mỗi task một **ENI** + IP private riêng, SG theo task |
| `bridge` | ✅ **mặc định** | ❌ | ❌ | ❌ | Mạng ảo Docker (driver `bridge`) |
| `host` | ✅ | ❌ | ✅ | ❌ | Dùng thẳng mạng host; không dynamic port; không chạy 2 task cùng task def trên 1 instance |
| `none` | ✅ | ❌ | ❌ | ❌ | Không có mạng ra ngoài, không port mapping |
| `default` | ❌ | ✅ **mặc định** | ❌ | ❌ | Mạng ảo Docker Windows (driver `nat`) |

```
awsvpc:   Task A ── ENI A (IP A)        Task B ── ENI B (IP B)
bridge:   Task A ─┐
          Task B ─┴─ docker bridge ── ENI instance (IP instance), port host động
host:     Task A ── port 8080 thẳng trên ENI instance (task A thứ 2 không lấy được 8080)
```

- AWS **khuyến nghị `awsvpc`** cho mọi nền tảng. Chuẩn hoá `awsvpc` → chạy được cả 3 nền tảng, dùng được ECS Gateway/VPC Lattice, VPC Flow Logs theo task.
- Managed Instances: trang networking ghi **bắt buộc khai báo** network mode (xem mục 18 về chỗ không khớp).
- 💬 Đánh đổi `awsvpc` (theo `13-99`): khởi động chậm hơn vài giây vì phải gắn ENI, tốn ENI; trên EC2 bù bằng **ENI trunking**.
- Fargate: mỗi task 1 ENI; container trong task nói chuyện qua **`localhost`**; giới hạn `awsVpcConfiguration`: **16 subnet, 5 SG**.

### 8.2 Port mapping

| Trường | Ghi chú |
|---|---|
| `containerPort` | Port app lắng nghe. Windows trên Fargate không dùng được **3150** |
| `hostPort` | `awsvpc`: bỏ qua (= containerPort). `bridge`: **static** hoặc **dynamic** (bỏ trống / `0` → port ngẫu nhiên trong dải ephemeral). `host`: phải khai báo cụ thể |
| `protocol` | `tcp` / `udp` (phân biệt hoa thường), mặc định `tcp`. Service Connect chỉ `tcp` |
| `name` | **Cần cho Service Connect và VPC Lattice** (và ECS Gateway tham chiếu qua `portName`) |
| `appProtocol` | `http`, `http2`, `grpc` (chữ thường) — cho Service Connect |
| `containerPortRange` | Dải port (vd `"40000-40100"`), chỉ qua API; `awsvpc` (EC2 còn `bridge`); tối đa 100 dải; agent ≥ 1.67.0 |

EC2 dynamic port: dải ephemeral đọc từ `/proc/sys/net/ipv4/ip_local_port_range` (không có thì `49153–65535`); đổi bằng biến agent **`ECS_DYNAMIC_HOST_PORT_RANGE`** khi bị trùng port. Port giữ sẵn: `22`, `2375`, `2376`, `51678–51680`.

Windows: port mapping dùng gateway `NetNAT`, **không truy cập port container từ chính host** (không có loopback).

### 8.3 Network settings khác

`links`, `hostname`, `extraHosts`, `dnsServers`, `dnsSearchDomains`, `disableNetworking`: chỉ có ý nghĩa trên EC2 Linux **không dùng `awsvpc`** (chủ yếu `bridge`). Fargate và Managed Instances đều không dùng được.

---

## 9. Container definition chi tiết

> Nguồn: `13-09`, `13-10`, `13-11`

### 9.1 Image

| Trường | Ghi chú |
|---|---|
| `name` | Bắt buộc, ≤ 255 ký tự (chữ, số, `-`, `_`) |
| `image` | Bắt buộc. Không ghi tag/digest → `latest`. Đổi image trong registry **không lan sang task đang chạy**. ECR: `registry/repo:tag` hoặc `@sha256:digest` |
| `versionConsistency` | `enabled` (mặc định): ECS **resolve tag → digest** lúc deploy, mọi task trong deployment chạy cùng digest. `disabled`: dùng nguyên URI |
| `repositoryCredentials.credentialsParameter` | ARN secret chứa user/password cho **private registry ngoài ECR** |

⚠️ Đừng dùng tag `latest` — vi phạm nguyên tắc 2 và khó rollback.

### 9.2 Khởi chạy

| Trường | Ghi chú |
|---|---|
| `essential` | Mặc định `true`. Container essential dừng → **cả task dừng**. Mỗi task cần ≥ 1 container essential |
| `entryPoint`, `command`, `workingDirectory` | Ghi đè `ENTRYPOINT` / `CMD` / `WORKDIR` của image (🔎 ánh xạ Docker). Agent đời cũ xử lý `entryPoint` chưa đúng → cập nhật agent hoặc dùng `command` |

### 9.3 Cấu hình và bí mật

| Trường | Ghi chú |
|---|---|
| `environment` | Biến môi trường plaintext — **không** để thông tin nhạy cảm |
| `environmentFiles` | ≤ 10 file `.env` trên S3 (`type: s3`); biến khai báo trực tiếp ưu tiên hơn. Không hỗ trợ Windows. EC2 + FIPS: bucket có dấu `.` không dùng được |
| `secrets` | `name` + `valueFrom` (ARN Secrets Manager hoặc Parameter Store; khác Region phải ARN đầy đủ). Do **execution role** lấy |

### 9.4 Health check và restart

| Nhóm | Tham số | Giá trị |
|---|---|---|
| `healthCheck` | `command` (`["CMD", ...]` hoặc `["CMD-SHELL", "..."]`), `interval`, `timeout`, `retries`, `startPeriod` | interval 5–300 (mặc định 30), timeout 2–60 (mặc định 5), retries 1–10 (mặc định 3), startPeriod 0–300 (mặc định tắt) |
| `restartPolicy` | `enabled`, `ignoredExitCodes` (≤ 50), `restartAttemptPeriod` | 60–1800 giây, mặc định 300 |

🔎 Khác biệt cần nắm:
- **Container health check** (trong task definition) do agent chạy **bên trong** container; lệnh cần có công cụ trong image (vd `curl`).
- **LB health check** (trong target group) gọi từ ngoài vào. Xem file toàn tập Load balancing.
- `restartPolicy` khởi động lại **container** tại chỗ, không thay cả task; container essential bị restart thì task không bị dừng (🔎 hành vi chi tiết — kiểm tra docs *container restart policy*).

### 9.5 Thứ tự khởi động / tắt

| Tham số | Ghi chú |
|---|---|
| `dependsOn` | `containerName` + `condition`: `START`, `COMPLETE` (đã thoát), `SUCCESS` (thoát mã 0), `HEALTHY` (qua health check, chỉ xét lúc khởi động). `COMPLETE`/`SUCCESS` **không đặt được trên container essential**. Khi tắt, thứ tự **đảo ngược** |
| `startTimeout` | Thời gian chờ dependency. EC2/Fargate ≤ 120s; Managed Instances ≤ 600s |
| `stopTimeout` | Thời gian từ **SIGTERM** tới **SIGKILL**. Mặc định 30s. Fargate ≤ 120s; Managed Instances ≤ 86400s; EC2 mặc định lấy từ `ECS_CONTAINER_STOP_TIMEOUT` |

Yêu cầu phiên bản: Fargate platform **1.3.0+** (Linux) / **1.0.0** (Windows); EC2 agent **≥ 1.26.0**.

Mẫu phổ biến:

```
migrate (essential: false, chạy xong thoát 0)
        │  dependsOn SUCCESS
        ▼
app (essential: true)  ◄── dependsOn START/HEALTHY ── log-router (FireLens)
```

### 9.6 Bảo mật và giới hạn

| Tham số | EC2 Linux | Fargate | Ghi chú |
|---|---|---|---|
| `privileged` | ✅ (không khuyến nghị) | ❌ | Quyền root trên host |
| `linuxParameters.capabilities.add` | Danh sách đầy đủ | **Chỉ `SYS_PTRACE`** | `drop` thì thoải mái (kể cả `ALL`) — cách hardening tốt |
| `linuxParameters.initProcessEnabled` | ✅ | ✅ | Chạy `init` để chuyển signal, dọn zombie |
| `linuxParameters.devices`, `sharedMemorySize` | ✅ | ❌ | |
| `user` | ✅ | ✅ (Linux) | `uid:gid`…; không hỗ trợ Windows |
| `readonlyRootFilesystem` | ✅ | ✅ (Linux) | Mặc định `false` |
| `ulimits` | ✅ | ✅ (Linux) | Fargate: `nofile` mặc định 65535/65535, tối đa **1048576** |
| `systemControls` (sysctl) | ✅ | Linux platform 1.4.0+ | Fargate chỉ nhận sysctl có namespace bên trong container (`net.*`, IPC) |
| `dockerSecurityOptions` | ✅ (cần `ECS_SELINUX_CAPABLE` / `ECS_APPARMOR_CAPABLE`) | ❌ | |
| `credentialSpecs` | ✅ | ✅ | Active Directory (gMSA), tối đa 1 ARN; dạng `credentialspecdomainless:` không cần instance join domain |
| `pidMode` | `host` / `task` | Chỉ `task` (1.4.0+) | Sidecar giám sát cần thấy tiến trình container khác |
| `ipcMode` | `host` / `task` / `none` | ❌ | `host` tăng nguy cơ lộ IPC namespace |

---

## 10. Logging

> Nguồn: `13-02-08`, `13-10`, `13-11`

### 10.1 Ba lớp log (Fargate)

| Loại | Nguồn | Dùng để |
|---|---|---|
| Event | **EventBridge** | Biết task/service đổi trạng thái, tự động hoá phản ứng |
| Task lifecycle | Timestamp từng trạng thái của task | Đo thời gian pull image, khởi động |
| Application | Log driver | Log app ghi ra stdout/stderr |

### 10.2 Log driver

- Fargate và Managed Instances: **`awslogs`**, **`splunk`**, **`awsfirelens`**.
- EC2: 8 driver (`awslogs`, `fluentd`, `gelf`, `json-file`, `journald`, `splunk`, `syslog`, `awsfirelens`), agent phải đăng ký qua `ECS_AVAILABLE_LOGGING_DRIVERS`.

`awslogs` (→ CloudWatch Logs):

| Option | Ghi chú |
|---|---|
| `awslogs-group` | Bắt buộc |
| `awslogs-region` | Bắt buộc |
| `awslogs-stream-prefix` | Bắt buộc trên Fargate/Managed Instances (EC2 ghi optional); stream dạng `prefix/container-name/task-id`; cần để log hiện trên console |
| `awslogs-create-group` | Mặc định `false`; bật thì cần quyền `logs:CreateLogGroup` |
| `awslogs-datetime-format` / `awslogs-multiline-pattern` | Gộp log nhiều dòng (stack trace); không dùng cùng lúc |

Option chung cho mọi driver:

| Option | Ghi chú |
|---|---|
| `mode` | `non-blocking` / `blocking`. Từ **25/6/2025** mặc định đổi sang **`non-blocking`** (ưu tiên task sống). Đổi mặc định Region bằng account setting `defaultLogDriverMode` |
| `max-buffer-size` | Buffer cho `non-blocking`, mặc định `10m` (trang Managed Instances ghi `1m`). **Buffer đầy → mất log mới**. Memory mức task nên lớn hơn tổng memory container + buffer |

🔎 `blocking`: nếu CloudWatch chậm/không ghi được, app bị chặn khi ghi stdout → có thể treo. `non-blocking`: app không bị chặn nhưng có thể mất log.

### 10.3 FireLens

- `logDriver: awsfirelens` + một container router (`firelensConfiguration.type` = `fluentbit` / `fluentd`).
- Đẩy log tới Firehose, Kinesis Data Streams, OpenSearch, S3, đối tác…; file cấu hình có thể lấy từ S3 (`config-file-type: s3`) hoặc file trong image.
- Quyền gửi log qua FireLens thuộc **task role** (không phải execution role).

---

## 11. Storage: volume, EBS, EFS, bind mount, swap

> Nguồn: `13-02-06`, `13-02-08`, `13-09`, `13-10`, `13-11`

### 11.1 Loại volume

| Loại | EC2 | Managed Instances | Fargate | Đặc điểm |
|---|---|---|---|---|
| **Bind mount** | ✅ (`sourcePath` tuỳ ý) | ✅ (`sourcePath` chỉ dưới `/var`, `/tmp`) | ✅ (**không** `sourcePath` → ephemeral storage của task) | Tạm thời, mất khi task dừng (trừ khi gắn host path trên EC2) |
| **Amazon EFS** | ✅ | ✅ | ✅ | Bền vững, chia sẻ giữa nhiều task |
| **Amazon EBS** | ✅ | ✅ | ✅ (Linux) | Block storage; **1 volume / task**; cần `configuredAtLaunch: true` |
| **Docker volume** (`dockerVolumeConfiguration`) | ✅ | ❌ | ❌ | `scope` `task`/`shared`, `driver`… |
| **FSx for Windows File Server** | ✅ | ❌ | ❌ (Windows Fargate không hỗ trợ) | Cần AD |

Cách gắn: khai báo `volumes[]` ở mức task (đặt `name`) → container tham chiếu bằng `mountPoints[].sourceVolume` + `containerPath` (+ `readOnly`). `volumesFrom` để mount toàn bộ volume của container khác.

### 11.2 EBS và `configuredAtLaunch`

- `configuredAtLaunch: true` → task definition **chỉ khai báo tên volume**, cấu hình EBS thật (size, type, snapshot…) đưa vào lúc **RunTask / tạo-cập nhật service**.
- Lợi ích: **một task definition dùng lại được cho nhiều môi trường** (dev 20 GB, prod 200 GB).

### 11.3 EFS

| Trường | Ghi chú |
|---|---|
| `fileSystemId` | Bắt buộc |
| `rootDirectory` | Bỏ trống hoặc `/` = gốc. Có access point thì phải trống hoặc `/` |
| `transitEncryption` | `ENABLED` / `DISABLED` (mặc định) |
| `authorizationConfig.accessPointId` | Có → **phải bật transit encryption** |
| `authorizationConfig.iam` | `ENABLED` → mount bằng **task role**, **phải bật transit encryption** |

Quy tắc gói gọn: **access point hoặc IAM → bắt buộc `transitEncryption: ENABLED`**.

### 11.4 Ephemeral storage (Fargate)

- `ephemeralStorage.sizeInGiB` mở rộng dung lượng tạm vượt mặc định; cần platform 1.4.0+ (Linux) / 1.0.0+ (Windows). Managed Instances: ⛔.
- 🔎 Mặc định 20 GiB, tối đa 200 GiB.

### 11.5 Swap theo container (chỉ EC2 Linux)

| Tham số (trong `linuxParameters`) | Ý nghĩa |
|---|---|
| `maxSwap` | MiB swap tối đa; `0` = tắt; bỏ trống = theo cấu hình instance. Ánh xạ `--memory-swap = memory + maxSwap` |
| `swappiness` | 0–100, mặc định 60; chỉ có hiệu lực khi có `maxSwap`. **Amazon Linux 2023 không hỗ trợ** |

- Instance phải **tự bật swap** — ECS-optimized AMI mặc định **không có**. Khai báo `maxSwap` không tạo swap.
- Bỏ trống cả hai → swappiness 60, swap tối đa **gấp đôi memory** container (nếu instance có swap).
- Fargate, Managed Instances, Windows: **không có**.
- Dùng: container nhạy độ trễ → `maxSwap: 0`; container có memory tăng đột biến → bật swap.

---

## 12. Khác biệt giữa EC2, Managed Instances, Fargate, Windows

> Nguồn: `13-02-07`, `13-02-08`, `13-02-09`, `13-09`, `13-10`, `13-11`, `13-99`

Mô hình nhớ (theo `13-99`): **EC2 đầy đủ nhất → Managed Instances bỏ bớt → Fargate bỏ bớt nữa**.

| Tham số / đặc điểm | EC2 | Managed Instances | Fargate |
|---|---|---|---|
| `networkMode` | `awsvpc`/`bridge`/`host`/`none` (Linux, mặc định `bridge`); `awsvpc`/`default` (Windows) | `awsvpc` / `host` | **Chỉ `awsvpc`** |
| OS | Linux + nhiều bản Windows | Chỉ Linux | Linux + Windows 2019/2022/2025 |
| CPU/memory mức task | Tuỳ chọn (≤ 192 vCPU) | Tuỳ chọn (0,125–10 vCPU) | **Bắt buộc** theo bảng |
| `privileged` | ✅ | (mô tả, không ⛔) | ❌ |
| `capabilities.add` | Đầy đủ | Không giới hạn trong trang | Chỉ `SYS_PTRACE` |
| GPU | ✅ | ✅ | ❌ |
| `maxSwap` / `swappiness` | ✅ | ❌ | ❌ |
| Docker volume | ✅ | ❌ | ❌ |
| `ephemeralStorage` | — | ❌ | ✅ |
| `placementConstraints` | `memberOf` + cluster query language (cả custom attribute) | Chỉ `distinctInstance`, `memberOf` với 4 thuộc tính: `ecs.subnet-id`, `ecs.availability-zone`, `ecs.instance-type`, `ecs.cpu-architecture` | ❌ |
| Log driver | 8 | 3 | 3 |
| `startTimeout` / `stopTimeout` max | 120 / theo agent | 600 / 86400 | 120 / 120 |
| `proxyConfiguration` (App Mesh) | ✅ | ❌ | ✅ (Linux) |
| `pidMode` | `host`/`task` | `host`/`task` | Chỉ `task` |

**Không hợp lệ trên Fargate:** `disableNetworking`, `dnsSearchDomains`, `dnsServers`, `dockerSecurityOptions`, `extraHosts`, `gpu`, `ipcMode`, `links`, `placementConstraints`, `privileged`, `maxSwap`, `swappiness`; `linuxParameters` không có `devices`/`sharedMemorySize`/`tmpfs`; không `dockerVolumeConfiguration`.

**Không hợp lệ trên Managed Instances:** `disableNetworking`, `dnsSearchDomains`, `dnsServers`, `dockerSecurityOptions`, `dockerVolumeConfiguration`, `ephemeralStorage`, `extraHosts`, `fsxWindowsFileServerVolumeConfiguration`, `hostname`, `inferenceAccelerator`, `ipcMode`, `links`, `maxSwap`, `proxyConfiguration`, `sharedMemorySize`, `swappiness`, `tmpfs`, volume `sourcepath` (xem mục 18).

**EC2 Windows không hỗ trợ:** trong container: `disableNetworking`, `dnsServers`, `dnsSearchDomains`, `extraHosts`, `links`, `linuxParameters`, `privileged`, `readonlyRootFilesystem`, `user`, `ulimits`; volume `dockerVolumeConfiguration`; mức task: `cpu`, `memory` (đặt ở container), `proxyConfiguration`, `ipcMode`, `pidMode`; `taskRoleArn` cần cấu hình bổ sung.

Checklist migrate EC2 → Managed Instances (`13-02-07`):

```
[ ] requiresCompatibilities có MANAGED_INSTANCES
[ ] networkMode khai báo rõ: awsvpc hoặc host — không bridge
[ ] Gỡ maxSwap/swappiness, links/hostname/extraHosts/dnsServers, dockerVolumeConfiguration, tmpfs, sharedMemorySize
[ ] placementConstraints chỉ dùng 4 thuộc tính cho phép
[ ] Bind mount sourcePath dưới /var hoặc /tmp
[ ] Container Linux
```

---

## 13. Dual-stack và IPv6-only (phần liên quan task definition)

> Nguồn: `13-02-02`, `13-02-03`, `13-02-04`

Phần lớn là cấu hình **VPC/service**, không phải task definition. Điểm cần nhớ:

| | Nội dung |
|---|---|
| VPC IPv6-only | IPv6 CIDR cho VPC, subnet chỉ IPv6, route IPv6, SG rule IPv6 |
| Image | Phải dùng **ECR image URI dualstack**; ECR **không có interface VPC endpoint dualstack** |
| Gọi endpoint IPv4-only | **DNS64 + NAT64** |
| Load balancer | Dualstack + target group IPv6 |
| Hạn chế | **Không ECS Exec**, không Windows; Fargate cần platform ≥ 1.4.0; không có CloudWatch dualstack FIPS endpoint |
| EC2 riêng | ECS-optimized Linux AMI, `--enable-primary-ipv6`, agent ≥ 1.99.1, `ECS_INSTANCE_IP_COMPATIBILITY` |
| Fargate dual-stack | Cần account setting `dualStackIPv6` (chỉ qua API/CLI), subnet auto-assign IPv6; dịch vụ lúc launch (ECR, SSM, Secrets Manager) **vẫn cần route IPv4 `0.0.0.0/0`** |
| Chuyển đổi | Update trực tiếp, hoặc service song song + **blue/green** |

---

## 14. Tăng tốc khởi động: SOCI lazy loading

> Nguồn: `13-02-08`

- **Seekable OCI**: tạo index các file trong image → container khởi động sau **vài giây pull**, phần còn lại tải ở nền.
- Fargate **tự phát hiện** image có SOCI index; không có thì pull đầy đủ như thường. **Không cần sửa task definition**.
- Điều kiện: Linux platform **1.4.0**, X86_64/ARM64, image ở **ECR private**, nén **gzip** hoặc không nén (**không zstd**); không hỗ trợ Windows.
- Khuyến nghị cho image **> 250 MiB (nén)**.
- Manifest **v2** (bắt buộc với người mới): tạo index **đổi digest** của image → phải push lại; nếu pin theo digest thì lấy digest **sau** bước này.
- Có thể phải tăng **health check grace period** của LB vì thời gian khởi động thay đổi.
- Kiểm tra: task metadata endpoint v4 → trường `Snapshotter` = `soci` (hay `overlayfs`).
- Tạo index: `soci-snapshotter` CLI (CI/CD) hoặc CloudFormation *SOCI Index Builder* (serverless).

---

## 15. Triển khai từng bước

> ✍️ Ví dụ tự đặt, ghép từ các tham số trong folder. 🔎 Lệnh CLI là kiến thức ngoài folder — kiểm tra lại tên cờ với `aws ecs <lệnh> help`.

### 15.1 Bài toán

Service `order-api` (Spring Boot, port 8080) trên Fargate ARM64:
- Chạy migration DB trước khi app khởi động.
- Đọc mật khẩu DB từ Secrets Manager.
- Gửi log lên CloudWatch.
- App được phép đọc/ghi một bảng DynamoDB (task role).

### 15.2 IAM

```
order-api-execution-role   ← AmazonECSTaskExecutionRolePolicy + secretsmanager:GetSecretValue trên secret DB
order-api-task-role        ← dynamodb:GetItem/PutItem/Query trên bảng orders
```

Cả hai role cần trust policy cho principal `ecs-tasks.amazonaws.com` (🔎).

### 15.3 Task definition `order-api.json`

```json
{
  "family": "order-api",
  "requiresCompatibilities": ["FARGATE"],
  "networkMode": "awsvpc",
  "runtimePlatform": {
    "operatingSystemFamily": "LINUX",
    "cpuArchitecture": "ARM64"
  },
  "cpu": "1024",
  "memory": "2048",
  "executionRoleArn": "arn:aws:iam::<ACCOUNT_ID>:role/order-api-execution-role",
  "taskRoleArn": "arn:aws:iam::<ACCOUNT_ID>:role/order-api-task-role",
  "containerDefinitions": [
    {
      "name": "migrate",
      "image": "<ACCOUNT_ID>.dkr.ecr.<REGION>.amazonaws.com/order-api:a1b2c3d",
      "essential": false,
      "command": ["java", "-jar", "app.jar", "--spring.profiles.active=migrate"],
      "secrets": [
        { "name": "DB_PASSWORD", "valueFrom": "arn:aws:secretsmanager:<REGION>:<ACCOUNT_ID>:secret:order-db-XXXX" }
      ],
      "logConfiguration": {
        "logDriver": "awslogs",
        "options": {
          "awslogs-group": "/ecs/order-api",
          "awslogs-region": "<REGION>",
          "awslogs-stream-prefix": "migrate"
        }
      }
    },
    {
      "name": "app",
      "image": "<ACCOUNT_ID>.dkr.ecr.<REGION>.amazonaws.com/order-api:a1b2c3d",
      "essential": true,
      "portMappings": [
        { "name": "http", "containerPort": 8080, "protocol": "tcp", "appProtocol": "http" }
      ],
      "environment": [
        { "name": "JAVA_TOOL_OPTIONS", "value": "-XX:MaxRAMPercentage=75" },
        { "name": "ORDERS_TABLE", "value": "orders" }
      ],
      "secrets": [
        { "name": "DB_PASSWORD", "valueFrom": "arn:aws:secretsmanager:<REGION>:<ACCOUNT_ID>:secret:order-db-XXXX" }
      ],
      "dependsOn": [
        { "containerName": "migrate", "condition": "SUCCESS" }
      ],
      "healthCheck": {
        "command": ["CMD-SHELL", "curl -f http://localhost:8080/actuator/health || exit 1"],
        "interval": 10,
        "timeout": 5,
        "retries": 3,
        "startPeriod": 60
      },
      "stopTimeout": 60,
      "readonlyRootFilesystem": false,
      "linuxParameters": {
        "initProcessEnabled": true,
        "capabilities": { "drop": ["ALL"] }
      },
      "ulimits": [
        { "name": "nofile", "softLimit": 65535, "hardLimit": 65535 }
      ],
      "logConfiguration": {
        "logDriver": "awslogs",
        "options": {
          "awslogs-group": "/ecs/order-api",
          "awslogs-region": "<REGION>",
          "awslogs-stream-prefix": "app",
          "mode": "non-blocking",
          "max-buffer-size": "25m"
        }
      }
    }
  ]
}
```

Giải thích các lựa chọn:

| Chỗ | Vì sao |
|---|---|
| `requiresCompatibilities: FARGATE` | ECS validate ngay lúc register |
| `cpuArchitecture: ARM64` | Image build cho Graviton; Fargate mặc định `X86_64` |
| `cpu 1024 / memory 2048` | Tổ hợp hợp lệ trong bảng Fargate (1 vCPU: 2–8 GB) |
| Image tag `a1b2c3d` | Git SHA — nguyên tắc 2 |
| `migrate` `essential: false` + `dependsOn SUCCESS` | Migration chạy xong thoát 0 rồi app mới chạy; `SUCCESS` không đặt được trên container essential |
| `secrets` | Không để mật khẩu ở `environment`; execution role đọc |
| `portMappings.name` + `appProtocol` | Dùng được cho Service Connect sau này |
| `healthCheck.startPeriod 60` | Cho Spring Boot thời gian khởi động 🔎 (cần `curl` trong image) |
| `stopTimeout 60` | Đủ cho graceful shutdown; Fargate tối đa 120 |
| `capabilities.drop ALL` | Hardening; app web thường không cần capability nào 🔎 |
| `mode non-blocking` + buffer | Mặc định từ 25/6/2025; buffer đầy thì mất log — nhớ cộng vào memory |
| `MaxRAMPercentage=75` | 🔎 chừa chỗ cho non-heap, tránh vượt hard limit 2048 MiB |

⚠️ Hai container cùng image nên cùng tag — bump tag phải sửa cả hai chỗ (pipeline nên sinh file này từ template).

### 15.4 Register, chạy, deploy

```bash
# 1. Register (tạo revision mới)
aws ecs register-task-definition --cli-input-json file://order-api.json
# → trả về taskDefinitionArn ...:task-definition/order-api:1

# 2. Xem revision
aws ecs describe-task-definition --task-definition order-api        # revision ACTIVE mới nhất
aws ecs list-task-definitions --family-prefix order-api --status ACTIVE

# 3a. Chạy thử standalone task
aws ecs run-task \
  --cluster <CLUSTER> \
  --launch-type FARGATE \
  --task-definition order-api:1 \
  --network-configuration "awsvpcConfiguration={subnets=[<SUBNET_ID>],securityGroups=[<SG_ID>],assignPublicIp=DISABLED}"

# 3b. Hoặc deploy qua service (service đã có)
aws ecs update-service --cluster <CLUSTER> --service order-api --task-definition order-api:1

# 4. Rollback = trỏ lại revision cũ
aws ecs update-service --cluster <CLUSTER> --service order-api --task-definition order-api:<REV_CŨ>

# 5. Dọn revision cũ
aws ecs deregister-task-definition --task-definition order-api:<REV>
aws ecs delete-task-definitions --task-definitions order-api:<REV>
```

Ghi chú cho bước 3a:
- Private subnet cần **NAT gateway** hoặc **interface VPC endpoint** của ECR (cùng các endpoint log/secret) để pull image và lấy secret.
- Public subnet cần `assignPublicIp=ENABLED`.

### 15.5 Pipeline CI/CD điển hình

```
git push ─► build image ─► push ECR :<git-sha> ─► (tuỳ chọn) tạo SOCI index
        ─► render order-api.json với tag mới ─► register-task-definition ─► update-service
        ─► chờ deployment ổn định (circuit breaker / alarm) ─► dọn revision cũ định kỳ
```

🔎 Có thể thay bằng CloudFormation/Terraform/CDK (`AWS::ECS::TaskDefinition`, `aws_ecs_task_definition`). Mỗi lần đổi thuộc tính sẽ tạo revision mới; Terraform thường deregister revision cũ (cẩn thận khi rollback).

### 15.6 Biến thể EC2 `bridge` (để so sánh)

```json
{
  "family": "legacy-web",
  "requiresCompatibilities": ["EC2"],
  "networkMode": "bridge",
  "containerDefinitions": [
    {
      "name": "web",
      "image": "<IMAGE>",
      "memoryReservation": 256,
      "memory": 512,
      "cpu": 256,
      "portMappings": [{ "containerPort": 80, "hostPort": 0 }],
      "linuxParameters": { "maxSwap": 0 }
    }
  ]
}
```

- Không có `cpu`/`memory` mức task → phải có memory ở container.
- `hostPort: 0` → dynamic port, nhiều task cùng chạy trên một instance; target group dùng type `instance`.
- `memoryReservation` 256 để bin-pack, burst được tới 512.

---

## 16. Checklist best practice

```
Thiết kế
[ ] 1 family = 1 mục đích nghiệp vụ (sidecar phục vụ trực tiếp app thì để chung)
[ ] Image tag = git SHA, không dùng latest; để versionConsistency = enabled
[ ] 1 family = 1 task role, quyền tối thiểu; execution role tách riêng

Khai báo
[ ] requiresCompatibilities luôn có
[ ] networkMode: awsvpc
[ ] cpuArchitecture khai báo rõ (đặc biệt ARM64)
[ ] CPU/memory từ load test; memory hard limit có dư so với peak
[ ] Mỗi task có ≥ 1 container essential; sidecar/init đặt essential đúng ý

Bảo mật
[ ] Bí mật qua secrets (Secrets Manager / SSM), không qua environment, không qua tags
[ ] Không privileged; drop capability không cần; cân nhắc readonlyRootFilesystem + user không root
[ ] EFS dùng access point / IAM → bật transitEncryption

Vận hành
[ ] awslogs có stream-prefix; quyết định blocking / non-blocking có chủ đích
[ ] healthCheck + startPeriod hợp lý; stopTimeout đủ cho graceful shutdown
[ ] portMappings có name (sẵn sàng cho Service Connect / Gateway)
[ ] Chính sách dọn revision cũ (nhớ AWS Config tính phí register)
```

---

## 17. Troubleshooting

| Triệu chứng | Nguyên nhân hay gặp | Cách xử lý |
|---|---|---|
| Register báo `ClientException` | Tham số không hợp lệ với nền tảng trong `requiresCompatibilities` (vd `privileged` trên Fargate), tổ hợp CPU/memory sai | Đối chiếu mục 12 và bảng Fargate mục 7.3 |
| Task Managed Instances không chạy | Thiếu `networkMode` hoặc dùng `bridge` | Khai báo `awsvpc` / `host` |
| `CannotPullContainerError` | Không có đường ra (public subnet thiếu public IP, private subnet thiếu NAT / VPC endpoint); execution role thiếu quyền ECR; sai kiến trúc | Mục 8, mục 6; kiểm tra `cpuArchitecture` |
| Task dừng ngay, lỗi lấy secret | Execution role thiếu quyền secret / KMS; không có đường tới Secrets Manager | Thêm quyền, thêm endpoint/NAT |
| Container bị kill, exit code 137 (🔎 OOM) | Vượt `memory` hard limit | Tăng memory, chỉnh heap, hoặc bật swap (EC2) |
| App chậm, CPU 100% nhưng không chết | Bị **throttle** khi vượt CPU limit | Tăng CPU hoặc scale out |
| App container không bao giờ chạy | `dependsOn` không thoả (migrate thoát mã ≠ 0 với `SUCCESS`, health check không qua với `HEALTHY`, hết `startTimeout`) | Xem log container phụ thuộc |
| Log không hiện trên console | Thiếu `awslogs-stream-prefix`, log group chưa tồn tại (và `awslogs-create-group` = false), execution role thiếu quyền | Mục 10 |
| Mất một phần log | `non-blocking` + buffer đầy | Tăng `max-buffer-size` hoặc chuyển `blocking` |
| Revision kẹt `DELETE_IN_PROGRESS` | Còn task / deployment / task set tham chiếu | Dừng chúng; chờ tối đa 1h / 24h |
| EC2 `host` mode: task thứ 2 không lên | Trùng `hostPort` trên cùng instance | Dùng `awsvpc` hoặc `bridge` + dynamic port |
| EC2 `bridge`: lỗi trùng port lúc khởi động | Dải ephemeral đụng kết nối ra ngoài | `ECS_DYNAMIC_HOST_PORT_RANGE` |
| `maxSwap` không có tác dụng | Instance không bật swap; hoặc Fargate/Managed Instances | Bật swap trên AMI; mục 11.5 |
| Task Fargate không có IPv6 | Thiếu `dualStackIPv6`, subnet chưa auto-assign IPv6, platform < 1.4.0 | Mục 13 |
| Graceful shutdown bị cắt ngang | `stopTimeout` quá ngắn; app không xử lý SIGTERM; PID 1 không chuyển signal | Tăng `stopTimeout` (≤ 120 trên Fargate), bật `initProcessEnabled` |

---

## 18. Chỗ docs không khớp nhau

> Nguồn: mục "chỗ không khớp" trong `13-09`, `13-10`, `13-11`, `13-02-07`, `13-02-08`

Khi cần chắc chắn: register với `requiresCompatibilities` tương ứng để ECS tự kiểm tra.

| Chủ đề | Chỗ 1 | Chỗ 2 |
|---|---|---|
| Network mode Managed Instances | Trang networking: **bắt buộc khai báo** | Trang tham số: không bắt buộc, mặc định `awsvpc` |
| Network mode Fargate | Phần mở đầu: tuỳ chọn | Mục `networkMode`: bắt buộc `awsvpc` |
| Bind mount `sourcePath` (Managed Instances) | Trang differences: volume `sourcepath` không hợp lệ | Cùng trang: dùng được nếu trỏ vào `/var`, `/tmp` |
| `sourcePath` (Fargate) | Danh sách volume: tuỳ chọn | Ghi chú `host`: chỉ EC2 / Managed Instances |
| `nofile` mặc định Fargate | 65535 / 65535 | Trang Managed Instances: soft 1024 / hard 65535 |
| `max-buffer-size` mặc định | `10m` (EC2, Fargate) | `1m` (Managed Instances) |
| Log driver Fargate | Danh sách có 8 giá trị | Cùng trang: chỉ hỗ trợ 3 |
| Windows Server trên Fargate | Trang tham số có 2025 | Trang differences chỉ 2019, 2022 |
| `tmpfs` trên Fargate | Trang tham số không ghi ⛔ | Trang differences: không hỗ trợ |
| `awslogs-stream-prefix` | EC2: optional | Fargate/Managed Instances: bắt buộc |
| Ví dụ `ulimits` Fargate | Câu dẫn "đã nhân đôi" | Giá trị ví dụ (2048/8192) thấp hơn mặc định 65535 |

---

## 19. Câu hỏi tự kiểm tra (có đáp án)

**1. Task definition, task và service khác nhau thế nào?**
<details><summary>Đáp án</summary>

Task definition là bản thiết kế (JSON, có revision, bất biến). Task là một bản đang chạy của task definition, gồm 1..n container. Service giữ N task luôn chạy, gắn LB và thực hiện deploy bằng cách trỏ sang revision mới. Subnet/SG/desired count thuộc service hoặc RunTask, không thuộc task definition.
</details>

**2. Hai tham số nào bắt buộc trong mọi task definition? Fargate cần thêm gì?**
<details><summary>Đáp án</summary>

`family` và `containerDefinitions` (mỗi container có `name`, `image`). Fargate cần thêm `networkMode: awsvpc`, `cpu` và `memory` mức task theo bảng tổ hợp, và (trên trang tham số) `operatingSystemFamily`.
</details>

**3. Muốn đổi image của task definition `my-app:5` thì làm sao?**
<details><summary>Đáp án</summary>

Không sửa được revision 5 vì revision là bất biến. Phải register revision mới (`my-app:6`) với image mới rồi `update-service` sang revision 6. Revision 5 vẫn còn để rollback.
</details>

**4. Deregister revision đang được service dùng thì service có bị dừng không?**
<details><summary>Đáp án</summary>

Không. `INACTIVE` chỉ cấm chạy task mới hoặc tạo service mới với revision đó, không ảnh hưởng task và service hiện có. Ngay cả gửi xoá (`DELETE_IN_PROGRESS`) cũng không phá service; việc xoá thật chỉ hoàn tất khi không còn task, deployment hay task set tham chiếu.
</details>

**5. Xoá hết revision của family `my-app` (1–5), rồi register lại `my-app`. Revision mới là bao nhiêu? Vì sao?**
<details><summary>Đáp án</summary>

Revision 6. ECS giữ lại tên và tiếp tục đánh số, để một cặp `family:revision` không bao giờ trỏ tới hai nội dung khác nhau theo thời gian.
</details>

**6. Vì sao không nên gộp web và API vào một task definition?**
<details><summary>Đáp án</summary>

Vì không scale riêng được: mỗi task luôn có 1 web + 1 API, muốn 6 web thì cũng phải 6 API. Gộp lại cũng buộc hai thành phần dùng chung task role (vi phạm nguyên tắc 3). Ngoại lệ là sidecar phục vụ trực tiếp app (log router, proxy).
</details>

**7. Task role và execution role khác nhau ra sao? Pull image ECR private, đọc secret, ghi DynamoDB, gửi log qua FireLens — mỗi việc dùng role nào?**
<details><summary>Đáp án</summary>

Task role là quyền của code trong container. Execution role là quyền của ECS agent/Fargate để chuẩn bị và vận hành task.
- Pull ECR private: execution role.
- Đọc secret khai báo trong `secrets`: execution role.
- Ghi DynamoDB từ app: task role.
- Gửi log qua FireLens: task role. Còn `awslogs` thì dùng execution role.
</details>

**8. Container vượt CPU limit và vượt memory limit thì chuyện gì xảy ra? Cái nào nguy hiểm hơn?**
<details><summary>Đáp án</summary>

Vượt CPU thì bị throttle: app chậm nhưng vẫn sống. Vượt memory hard limit thì container bị kill. Nếu đó là container essential thì cả task dừng và service thay task mới. Vì vậy memory nguy hiểm hơn, nên chừa dư so với peak.
</details>

**9. `memory` và `memoryReservation` ở mức container khác gì nhau?**
<details><summary>Đáp án</summary>

`memoryReservation` là soft limit, mức được đảm bảo; scheduler EC2 dùng nó để bin-pack, và container được burst vượt mức này. `memory` là hard limit, vượt là bị kill. `memoryReservation` không hỗ trợ Windows.
</details>

**10. Fargate: `cpu: 512`, `memory: 8192` có hợp lệ không?**
<details><summary>Đáp án</summary>

Không. Với 512 CPU (0,5 vCPU) memory chỉ nhận 1–4 GB. Muốn 8 GB thì cần 1024 CPU (2–8 GB) hoặc 2048 CPU (4–16 GB).
</details>

**11. Task definition cho EC2 Windows đặt CPU/memory ở đâu?**
<details><summary>Đáp án</summary>

Ở mức container. `cpu`/`memory` mức task không được hỗ trợ trên EC2 Windows. Ngược lại với Fargate, nơi hai giá trị này bắt buộc ở mức task.
</details>

**12. So sánh `awsvpc`, `bridge`, `host`. Vì sao AWS khuyến nghị `awsvpc`?**
<details><summary>Đáp án</summary>

- `awsvpc`: mỗi task một ENI, IP riêng, SG riêng.
- `bridge`: dùng mạng ảo Docker, chung IP instance, nhiều task trên một instance nhờ dynamic host port.
- `host`: dùng thẳng mạng host, port cố định, không chạy được 2 task cùng task definition trên một instance.

AWS khuyến nghị `awsvpc` vì: chạy được trên cả EC2, Managed Instances và Fargate; SG và VPC Flow Logs áp được theo từng task; dùng được ECS Gateway/VPC Lattice; migrate không phải sửa network mode. Đánh đổi là tốn ENI và khởi động chậm hơn một chút.
</details>

**13. Dynamic port mapping là gì, dùng ở mode nào?**
<details><summary>Đáp án</summary>

Ở mode `bridge` trên EC2, đặt `hostPort` là `0` hoặc bỏ trống thì Docker chọn một port trong dải ephemeral. Nhờ vậy chạy được nhiều task giống nhau trên cùng instance, và LB đăng ký theo instance + port. Nếu bị trùng port thì chỉnh dải bằng `ECS_DYNAMIC_HOST_PORT_RANGE`. Mode `host` không có dynamic port. Với `awsvpc` thì `hostPort` bị bỏ qua.
</details>

**14. Trường `name` và `appProtocol` trong `portMappings` để làm gì?**
<details><summary>Đáp án</summary>

`name` là tên port mà Service Connect, VPC Lattice và ECS Gateway tham chiếu tới. `appProtocol` (`http`, `http2`, `grpc`) cho Service Connect biết giao thức, để proxy xử lý đúng: metric HTTP, retry, gRPC qua HTTP/2.
</details>

**15. Làm sao để container migration chạy xong rồi app mới khởi động?**
<details><summary>Đáp án</summary>

1. Đặt container `migrate` với `essential: false`.
2. Ở container `app`, khai báo `dependsOn: [{ containerName: "migrate", condition: "SUCCESS" }]`.

`SUCCESS` nghĩa là `migrate` phải thoát với mã 0. Điều kiện `COMPLETE`/`SUCCESS` không đặt được trên container essential. Nếu `migrate` thất bại thì `app` không chạy.
</details>

**16. App Spring Boot cần 90 giây để graceful shutdown. Chạy trên Fargate có được không? Còn 5 phút thì sao?**
<details><summary>Đáp án</summary>

90 giây thì được: đặt `stopTimeout: 90` (Fargate tối đa 120 giây, cần platform 1.3.0+). 5 phút thì không đặt được trên Fargate. Các lựa chọn là rút ngắn thời gian shutdown, hoặc chuyển sang Managed Instances (tối đa 86400 giây) hoặc EC2 (đặt qua `ECS_CONTAINER_STOP_TIMEOUT`). Ngoài ra nên bật `initProcessEnabled` để SIGTERM tới đúng tiến trình app.
</details>

**17. Từ 25/6/2025 log driver mode mặc định là gì? Rủi ro của nó, và cách đổi?**
<details><summary>Đáp án</summary>

Mặc định là `non-blocking`, ưu tiên cho task sống. Rủi ro: khi buffer (`max-buffer-size`) đầy thì log mới bị mất. Muốn giữ `blocking`: đặt `mode: blocking` trong `logConfiguration`, hoặc đặt account setting `defaultLogDriverMode` = `blocking`. Nhớ tính buffer vào memory mức task.
</details>

**18. Muốn một task definition dùng cho cả dev (EBS 20 GB) và prod (EBS 200 GB) thì làm sao?**
<details><summary>Đáp án</summary>

Khai báo volume với `configuredAtLaunch: true`. Task definition chỉ giữ tên volume, còn kích thước và loại EBS truyền vào lúc RunTask hoặc khi tạo/cập nhật service. Mỗi task gắn được 1 EBS volume.
</details>

**19. Khai báo `maxSwap: 512` trên Fargate, hoặc trên EC2 dùng ECS-optimized AMI mặc định — chuyện gì xảy ra?**
<details><summary>Đáp án</summary>

- Fargate: không hợp lệ. Register với `requiresCompatibilities: FARGATE` sẽ bị từ chối.
- EC2 với AMI mặc định: register được, nhưng không có tác dụng, vì ECS-optimized AMI không bật swap. Phải tự bật swap trên instance, thường làm qua user data. Nếu AMI là Amazon Linux 2023 thì cũng không dùng được `swappiness`.
</details>

**20. Chuyển một task definition từ EC2 (`bridge`, có `links`, `maxSwap`, Docker volume) sang Managed Instances cần sửa gì?**
<details><summary>Đáp án</summary>

1. Thêm `requiresCompatibilities: ["MANAGED_INSTANCES"]`.
2. Đổi `networkMode` sang `awsvpc` (hoặc `host`).
3. Gỡ `links`, `maxSwap`/`swappiness`, `dockerVolumeConfiguration`; thay Docker volume bằng EFS, EBS hoặc bind mount dưới `/var` hoặc `/tmp`.
4. Kiểm tra `placementConstraints` chỉ dùng 4 thuộc tính được phép, và container là Linux.
5. Register để ECS validate.
</details>

**21. Image ARM64, Fargate, quên `cpuArchitecture`. Điều gì xảy ra?**
<details><summary>Đáp án</summary>

Fargate mặc định `X86_64`, nên task chạy trên x86 và container không khởi động được vì sai kiến trúc. Luôn khai báo `cpuArchitecture: ARM64` cho image Graviton.
</details>

**22. SOCI giúp gì, khi nào nên dùng, và cần lưu ý gì với digest?**
<details><summary>Đáp án</summary>

SOCI giúp container khởi động sau vài giây pull, phần còn lại của image tải ở nền. Nên dùng khi image lớn hơn 250 MiB (nén), lưu ở ECR private, nén gzip hoặc không nén, chạy Fargate Linux 1.4.0. Không cần sửa task definition.

Với manifest v2, tạo index sẽ đổi digest của image, nên phải push lại. Nếu pin theo digest thì lấy digest sau bước này. Có thể phải tăng health check grace period của LB.
</details>

---

## 20. Bản đồ nguồn

| Mục trong file này | File nguồn |
|---|---|
| 1, 4 | `13-02-00-ecs-architect-your-application.md`, `13-99-thuyet-trinh-cto.md` |
| 3 | `13-01-ecs-task-definition-states.md` |
| 7 | `13-02-01-ecs-task-size-best-practices.md`, `13-02-08`, `13-10`, `13-11`, `13-02-09` |
| 8, 13 | `13-02-02-ecs-managed-instances-networking.md`, `13-02-03-ecs-task-networking-ec2.md`, `13-02-04-ecs-task-networking-fargate.md` |
| 11.5 | `13-02-06-ecs-container-swap.md` |
| 5, 12 | `13-02-07-…-managed-instances-…-differences.md`, `13-02-08-…-fargate-…-differences.md`, `13-02-09-…-windows-ec2-…-differences.md` |
| 2, 6, 9, 10, 11 | `13-09-…-managed-instances-…-parameters.md`, `13-10-…-fargate-…-parameters.md`, `13-11-…-ec2-…-parameters.md` |
| 14 | `13-02-08` (mục SOCI) |
| 15, 16, 17 | ✍️ / 🔎 tổng hợp |
| 18 | Mục "chỗ không khớp" của `13-09`, `13-10`, `13-11` |

Liên quan ở folder khác: file toàn tập *Load balancing* và *Service Connect* (`15 - Schedule your containers/`) — nơi dùng `portMappings.name`, `appProtocol`, `stopTimeout`, health check.

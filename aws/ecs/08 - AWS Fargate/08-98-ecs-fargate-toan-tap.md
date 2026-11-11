    # AWS Fargate cho Amazon ECS — Toàn tập

> Tổng hợp từ **toàn bộ folder `08 - AWS Fargate`** (`08-00` → `08-08-01`, `08-99`, `Amazon_ECS_08_*`), bổ sung các phần liên quan tới Fargate đã có ở folder 05 (kiến trúc), 13 (task definition) và 15 (load balancing). Cùng cách làm với các file toàn tập trước.
>
> **Ký hiệu**
> - Không ký hiệu = lấy từ docs AWS đã dịch trong folder (có ghi file nguồn ở đầu mỗi mục).
> - 🔎 = kiến thức ngoài folder hoặc suy luận của mình — **nên tự kiểm tra lại** với docs AWS hiện tại.
> - ✍️ = ví dụ tự đặt (tên, giá trị, ID là placeholder).
> - ⚠️ = bẫy hay gặp.

---

## Mục lục

0. [Tóm tắt một trang](#0-tóm-tắt-một-trang)
1. [Fargate là gì, giải quyết vấn đề gì](#1-fargate-là-gì-giải-quyết-vấn-đề-gì)
2. [Cách Fargate hoạt động bên dưới](#2-cách-fargate-hoạt-động-bên-dưới)
3. [Khi nào dùng Fargate, khi nào không](#3-khi-nào-dùng-fargate-khi-nào-không)
4. [Capacity provider: FARGATE và FARGATE_SPOT](#4-capacity-provider-fargate-và-fargate_spot)
5. [Task definition cho Fargate](#5-task-definition-cho-fargate)
6. [Platform version và revision](#6-platform-version-và-revision)
7. [Networking của task Fargate](#7-networking-của-task-fargate)
8. [Load balancing cho service Fargate](#8-load-balancing-cho-service-fargate)
9. [Image pull và thời gian khởi động](#9-image-pull-và-thời-gian-khởi-động)
10. [Ephemeral storage](#10-ephemeral-storage)
11. [Mã hoá ephemeral storage bằng customer managed key](#11-mã-hoá-ephemeral-storage-bằng-customer-managed-key)
12. [Bảo mật: mô hình cô lập và giới hạn](#12-bảo-mật-mô-hình-cô-lập-và-giới-hạn)
13. [Bảo mật: best practice](#13-bảo-mật-best-practice)
14. [Task retirement và bảo trì](#14-task-retirement-và-bảo-trì)
15. [Chuẩn bị cho task retirement](#15-chuẩn-bị-cho-task-retirement)
16. [Usage metric, quota và giám sát](#16-usage-metric-quota-và-giám-sát)
17. [Triển khai từng bước](#17-triển-khai-từng-bước)
18. [Checklist](#18-checklist)
19. [Troubleshooting](#19-troubleshooting)
20. [Chỗ docs có lỗi / không khớp](#20-chỗ-docs-có-lỗi--không-khớp)
21. [Câu hỏi tự kiểm tra (có đáp án)](#21-câu-hỏi-tự-kiểm-tra-có-đáp-án)
22. [Bản đồ nguồn](#22-bản-đồ-nguồn)

---

## 0. Tóm tắt một trang

| Chủ đề | Điều cần nhớ |
|---|---|
| Là gì | Compute **serverless** cho ECS: chạy container **không quản lý server/EC2** |
| Bạn làm | Đóng gói container · chọn CPU/memory · định nghĩa mạng + IAM · chạy |
| AWS làm | Cấp phát, vá, scale hạ tầng; chọn host; bảo trì platform |
| Cô lập | **Mỗi task** = một môi trường ảo hoá phần cứng riêng: **không chung** kernel, CPU, memory, ENI, ephemeral storage với task khác. Container **trong cùng task** thì dùng chung |
| Task definition | `requiresCompatibilities: ["FARGATE"]`, `networkMode: awsvpc` (bắt buộc), `cpu` + `memory` mức task theo **bảng tổ hợp** (256/512 MiB → 32 vCPU/244 GB) |
| Capacity provider | `FARGATE` (on-demand) và `FARGATE_SPOT` (rẻ hơn, bị thu hồi sau **cảnh báo 2 phút**) — có sẵn trên mọi cluster |
| Platform version | Linux `1.4.0` (mới nhất, nên dùng) / `1.3.0`; Windows `1.0.0`. Task giữ **một revision suốt đời**; task mới luôn chạy revision mới nhất |
| Mạng | 1 ENI / task, IP private (+ public tuỳ chọn, + IPv6 nếu dual-stack); pull image cần **public IP / NAT / VPC endpoint ECR** |
| Load balancer | ALB, NLB, GWLB; target type **`ip`**; UDP qua NLB cần platform ≥ 1.4 |
| Image | **Không cache** giữa task → mỗi task pull toàn bộ image. Tối ưu: cùng Region + VPC endpoint, image nhỏ, **zstd**, **SOCI** (> 250 MB) |
| Ephemeral storage | Mặc định **20 GiB**, tăng tới **200 GiB** (`ephemeralStorage`); image chiếm chỗ trong đó; mã hoá AES-256 (khoá Fargate hoặc **CMK**) |
| Giới hạn | Không privileged, không Docker-in-Docker, không vào host, chỉ thêm được capability **`SYS_PTRACE`**, không GPU, không placement constraint |
| Debug | **ECS Exec** (không SSH) |
| Retirement | AWS **retire** task trên revision cũ — **không từ chối được**. Service task tự thay; **standalone task không tự thay**. Wait time `0/7/14` ngày hoặc **EC2 event windows** |

---

## 1. Fargate là gì, giải quyết vấn đề gì

> Nguồn: `08-00`, `Amazon_ECS_08_AWS_Fargate_Overview`

**AWS Fargate** là công nghệ dùng với Amazon ECS để chạy container **mà không phải quản lý server** hay cluster EC2.

| Không còn phải làm | Vẫn phải làm |
|---|---|
| Cấp phát, cấu hình, scale cluster máy ảo | Đóng gói ứng dụng thành container |
| Chọn loại server | Chọn CPU và memory |
| Quyết định khi nào scale cluster | Định nghĩa mạng (VPC, subnet, SG) và IAM |
| Tối ưu xếp task (bin-packing) | Khởi chạy và vận hành ứng dụng |

Luồng tổng quát:

```
Container image
      │
Task definition (requiresCompatibilities=FARGATE, CPU, memory, IAM, network, log)
      │
ECS lập lịch task / service
      │
Fargate cấp một môi trường ảo hoá riêng đúng CPU/memory → chạy container
```

🔎 So sánh nhanh với hai lựa chọn compute khác của ECS (chi tiết ở file toàn tập folder 05):

| | Fargate | Managed Instances | EC2 tự quản |
|---|---|---|---|
| Quản lý instance | Không có instance | AWS quản lý EC2 trong account bạn | Bạn |
| Tính tiền | Theo vCPU + memory (+ storage vượt 20 GiB) của task, theo giây 🔎 | Theo instance 🔎 | Theo instance |
| Cô lập | Mỗi task một môi trường ảo hoá riêng | Nhiều task chung instance 🔎 | Nhiều task chung instance |
| GPU, privileged, custom AMI | ❌ | Một phần 🔎 | ✅ |

---

## 2. Cách Fargate hoạt động bên dưới

> Nguồn: `08-00`, `08-01`, `08-02-01`, `08-05`, `13-02-04`

### 2.1 Mỗi task một môi trường riêng

- Fargate chạy **mỗi task** trong một **môi trường ảo hoá phần cứng** riêng.
- Instance đó là **single use, single tenant**: dùng một lần, chỉ của một khách hàng.
- Task Fargate **không dùng chung** kernel, CPU, memory, ENI, ephemeral storage với task khác.
- 🔎 Công nghệ bên dưới là micro-VM (Firecracker).

```
Fargate
 ├─ Task A  ── môi trường riêng: kernel, CPU, memory, ENI, ephemeral storage riêng
 │    ├─ container app
 │    └─ sidecar (log router, proxy...)
 └─ Task B  ── môi trường riêng (cô lập hoàn toàn với Task A)
```

### 2.2 Ranh giới cô lập là **task**, không phải container

| Tài nguyên | Giữa các task | Giữa container trong cùng task |
|---|---|---|
| Host | Riêng | **Chung** |
| CPU, memory | Riêng | **Chung** (trong giới hạn mức task) |
| Ephemeral storage | Riêng | **Chung** |
| Network namespace (IP, port) | Riêng | **Chung** (Linux) → gọi nhau qua `localhost`, không trùng port |

⚠️ Hệ quả: chỉ đặt các container **tin cậy lẫn nhau** trong cùng task (app + sidecar của chính team). Hai tenant / hai workload không tin nhau → **task riêng**.

### 2.3 Hệ quả vận hành của "dùng một lần"

| Đặc điểm | Hệ quả |
|---|---|
| Không cache image | Mỗi task mới pull **toàn bộ** image → thời gian pull ≈ thời gian khởi động (mục 9) |
| Không vào host | Debug bằng log, metric, trace, **ECS Exec** |
| Platform cố định theo revision | Muốn bản vá mới → task mới; AWS có thể **retire** task (mục 14) |
| Không trạng thái bền trên task | Dữ liệu cần giữ → EFS, EBS, S3, DB |

---

## 3. Khi nào dùng Fargate, khi nào không

> Nguồn: `05-00`, `08-01`, `08-02-01`, `05-03`

**Nên dùng khi:**
- Muốn tập trung vào ứng dụng, không quản lý hạ tầng.
- Workload biến động, khó dự đoán; hoặc mới bắt đầu với container.
- Cần **cô lập mạnh** giữa các task (docs khuyến nghị Fargate cho trường hợp này).
- Batch / dev / staging chịu gián đoạn → Fargate Spot để tiết kiệm.

**Không phù hợp khi:**

| Nhu cầu | Lý do | Thay bằng |
|---|---|---|
| GPU / HPC | Fargate không có `gpu` | EC2 (hoặc Managed Instances) |
| Privileged, Docker-in-Docker | Không hỗ trợ privileged | EC2 |
| `CAP_SYS_ADMIN`, `CAP_NET_ADMIN`, kernel module | Chỉ thêm được `SYS_PTRACE` | EC2 |
| Custom AMI, truy cập thiết bị host | Không vào host | EC2 |
| Chạy ở Local Zones, Wavelength, Outposts | Fargate không có ở đó | EC2 |
| Graceful shutdown > 120 giây | `stopTimeout` tối đa 120s | Managed Instances / EC2 |
| Cache image để khởi động cực nhanh | Không cache | EC2 + `prefer-cached` (hoặc SOCI trên Fargate) |
| Swap theo container | Không có `maxSwap` | EC2 |

---

## 4. Capacity provider: FARGATE và FARGATE_SPOT

> Nguồn: `08-00` mục 2, `05-09-04`, `05-01`, `Amazon_ECS_08_AWS_Fargate_Overview`

| | `FARGATE` | `FARGATE_SPOT` |
|---|---|---|
| Capacity | On-demand | Capacity compute **dư** của AWS |
| Giá | Giá Fargate | **Giảm** (🔎 tới ~70%) |
| Gián đoạn | — | Bị thu hồi khi AWS cần, **cảnh báo trước 2 phút** |
| Phù hợp | API, workload quan trọng | Worker, batch, dev/staging, task có retry/checkpoint |

- Hai provider này được ECS **tạo sẵn** trên mọi cluster; mỗi task nhận **một tài nguyên Fargate riêng** đúng CPU/memory mức task (quan hệ 1:1).
- Trộn được trong **cùng một strategy** (cùng loại Fargate), dùng `base` và `weight`:

```json
"capacityProviderStrategy": [
  { "capacityProvider": "FARGATE",      "base": 2, "weight": 1 },
  { "capacityProvider": "FARGATE_SPOT",            "weight": 3 }
]
```

→ 2 task đầu luôn on-demand; phần còn lại chia 1 on-demand : 3 Spot → có capacity burst rẻ hơn.

- ⚠️ Không trộn Fargate với ASG/Managed Instances trong một strategy.
- App chạy Spot phải: xử lý `SIGTERM` (🔎 ECS gửi SIGTERM khi Spot bị thu hồi), shutdown êm, retry an toàn, không giữ trạng thái quan trọng duy nhất trong task.
- 🔎 Có sự kiện EventBridge "ECS Task State Change" với `stopCode: SpotInterruption` để theo dõi.

---

## 5. Task definition cho Fargate

> Nguồn: `08-00` mục 3, `13-02-08`, `13-10` (chi tiết đầy đủ: file toàn tập *Task definition* folder 13)

### 5.1 Bắt buộc

| Trường | Giá trị |
|---|---|
| `requiresCompatibilities` | `["FARGATE"]` → ECS **validate ngay lúc register** |
| `networkMode` | `awsvpc` |
| `cpu`, `memory` (mức task) | Theo bảng tổ hợp dưới; không cần cộng overhead |
| `runtimePlatform.operatingSystemFamily` | `LINUX` hoặc `WINDOWS_SERVER_2019/2022/2025_FULL/CORE` |
| `runtimePlatform.cpuArchitecture` | Mặc định `X86_64`; image Graviton phải ghi `ARM64` (Linux) |

### 5.2 Bảng tổ hợp CPU – memory

| CPU | Memory | OS |
|---|---|---|
| 256 (0,25 vCPU) | 512 MiB, 1 GB, 2 GB | Linux |
| 512 (0,5 vCPU) | 1 – 4 GB | Linux |
| 1024 (1 vCPU) | 2 – 8 GB | Linux, Windows |
| 2048 (2 vCPU) | 4 – 16 GB, bước 1 GB | Linux, Windows |
| 4096 (4 vCPU) | 8 – 30 GB, bước 1 GB | Linux, Windows |
| 8192 (8 vCPU) — Linux 1.4.0+ | 16 – 60 GB, bước 4 GB | Linux |
| 16384 (16 vCPU) — Linux 1.4.0+ | 32 – 120 GB, bước 8 GB | Linux |
| 32768 (32 vCPU) — Linux 1.4.0+ | 60, 120, 244 GB | Linux |

Cách chọn (theo `05-09-05`): CPU/memory container tuỳ chọn; limit mức task ≥ mọi reservation → đặt = **tổng reservation các container** rồi **làm tròn lên** kích thước gần nhất. Chọn số bằng **load test**.

### 5.3 Không hợp lệ / bị giới hạn trên Fargate

| Nhóm | Tham số |
|---|---|
| **Không hợp lệ** | `disableNetworking`, `dnsSearchDomains`, `dnsServers`, `dockerSecurityOptions`, `extraHosts`, `gpu`, `ipcMode`, `links`, `placementConstraints`, `privileged`, `maxSwap`, `swappiness` |
| `linuxParameters` | `capabilities.add` chỉ `SYS_PTRACE`; không `devices`, `sharedMemorySize`, `tmpfs` (drop capability thoải mái) |
| `volumes` | Không `dockerVolumeConfiguration`; bind mount không `sourcePath` |
| Windows | `cpu` ≥ 1 vCPU; không `ulimits`, `linuxParameters`, `memoryReservation`, `readonlyRootFilesystem`, `user`, … |
| Timeout | `startTimeout` ≤ 120s; `stopTimeout` mặc định 30s, tối đa **120s** |
| `ulimits` (Linux) | `nofile` mặc định 65535/65535, tối đa 1048576 |
| Log driver | `awslogs`, `splunk`, `awsfirelens` |
| Storage | EBS (Linux, 1 volume/task, `configuredAtLaunch`), EFS, bind mount, `ephemeralStorage` |

### 5.4 Task definition mẫu ✍️

```json
{
  "family": "orders-api",
  "requiresCompatibilities": ["FARGATE"],
  "networkMode": "awsvpc",
  "runtimePlatform": { "operatingSystemFamily": "LINUX", "cpuArchitecture": "ARM64" },
  "cpu": "512",
  "memory": "1024",
  "ephemeralStorage": { "sizeInGiB": 30 },
  "executionRoleArn": "arn:aws:iam::<ACCOUNT_ID>:role/orders-execution-role",
  "taskRoleArn": "arn:aws:iam::<ACCOUNT_ID>:role/orders-task-role",
  "containerDefinitions": [
    {
      "name": "app",
      "image": "<ACCOUNT_ID>.dkr.ecr.<REGION>.amazonaws.com/orders-api:a1b2c3d",
      "essential": true,
      "portMappings": [{ "name": "http", "containerPort": 8080, "protocol": "tcp", "appProtocol": "http" }],
      "secrets": [{ "name": "DB_PASSWORD", "valueFrom": "arn:aws:secretsmanager:<REGION>:<ACCOUNT_ID>:secret:orders-db-XXXX" }],
      "linuxParameters": { "initProcessEnabled": true, "capabilities": { "drop": ["ALL"] } },
      "stopTimeout": 60,
      "logConfiguration": {
        "logDriver": "awslogs",
        "options": {
          "awslogs-group": "/ecs/orders-api",
          "awslogs-region": "<REGION>",
          "awslogs-stream-prefix": "app"
        }
      }
    }
  ]
}
```

---

## 6. Platform version và revision

> Nguồn: `08-00` mục 4, `Amazon_ECS_08_AWS_Fargate_Overview`, `13-02-04`

### 6.1 Khái niệm

- **Platform version** = môi trường runtime của hạ tầng Fargate = **kernel + container runtime**.
- Chọn khi chạy task / tạo service (`--platform-version`, có thể `LATEST`).
- Docs nêu: Amazon Linux 2 (**1.3.0**), Bottlerocket (**1.4.0**), Windows Server 2019 Full/Core (🔎 Windows platform `1.0.0`).
- Linux và Windows có chuỗi version riêng.

### 6.2 Revision

```
Platform version 1.4.0
   └─ revision 1 ─► revision 2 ─► revision 3 (mới nhất)
      (cập nhật kernel/OS, tính năng, sửa lỗi, vá bảo mật)

Task đang chạy:  giữ revision lúc khởi chạy SUỐT ĐỜI (không tự nâng)
Task mới:        luôn chạy revision MỚI NHẤT
Lỗ hổng:         AWS tạo revision vá → RETIRE task trên revision cũ
```

| Câu hỏi | Trả lời |
|---|---|
| Muốn task lên revision mới? | Khởi chạy task mới (service: `--force-new-deployment`) |
| Task đang chạy tự lên revision mới? | Không |
| Revision có lỗ hổng? | AWS retire task trên revision đó (mục 14) |

### 6.3 1.4.0 so với 1.3.0 (những gì folder nêu)

| | Linux 1.4.0 (và Windows 1.0.0) | Linux 1.3.0 trở xuống |
|---|---|---|
| ENI | **Một task ENI** cho mọi traffic, hiện trong VPC Flow Logs | Thêm **Fargate-owned ENI** cho credential ECR + secret (không hiện trong flow logs) |
| Ephemeral storage | ≥ 20 GiB, tăng tới 200 GiB, mã hoá AES-256 | 10 GB (layer) + 4 GB (volume) |
| EFS | ✅ | ❌ |
| UDP qua NLB | ✅ | ❌ |
| SOCI, `systemControls`, `pidMode: task` | ✅ | ❌ |
| 8/16/32 vCPU | ✅ | ❌ |
| IPv6 dual-stack / IPv6-only | ✅ | ❌ |
| `dependsOn`, `start/stopTimeout` | ✅ (từ 1.3.0) | ✅ (1.3.0) |

💬 Luôn dùng **1.4.0 / LATEST** cho Linux.

---

## 7. Networking của task Fargate

> Nguồn: `08-02-01` mục 2, `13-02-04`, `05-06-01`, `05-06-03`

### 7.1 Cơ bản

```
┌──────────── Task Fargate ────────────┐
│ container A ◄── localhost ──► container B │
└───────────────┬──────────────────────┘
                ENI (1 ENI / task, do Fargate quản lý)
                ├─ IPv4 private (luôn có)
                ├─ IPv4 public (tuỳ chọn, chỉ ở public subnet: assignPublicIp=ENABLED)
                └─ IPv6 (nếu VPC/subnet dual-stack + account setting dualStackIPv6)
```

- Bắt buộc `awsvpc`; khi chạy task/tạo service phải chỉ định **subnet** (tối đa 16) và **security group** (tối đa 5).
- Kiểm soát traffic bằng **security group** (theo task) + **network ACL** (theo subnet).
- ⚠️ Không tự xoá/sửa ENI do Fargate tạo; muốn giải phóng thì **dừng task**.
- Đổi DHCP options set không áp cho task đang chạy → phải thay task.
- Bật `enableDnsHostnames` + `enableDnsSupport` để hostname task là DNS của Amazon.

### 7.2 Đường ra để pull image / lấy secret

| Subnet | Cách |
|---|---|
| Public | `assignPublicIp=ENABLED` (⚠️ quên bật → không pull được image) |
| Private | NAT gateway (1 NAT / AZ) |
| Private hoàn toàn | VPC endpoint: `ecr.api`, `ecr.dkr`, **S3 gateway** (layer image), `logs`, `secretsmanager`/`ssm` (🔎 danh sách endpoint) |

Từ platform 1.4.0, **mọi** traffic (kể cả credential ECR, secret) đi qua task ENI → SG, route, NAT, endpoint của subnet task phải cho phép cả những luồng này.

### 7.3 Ai dùng role nào (bảng luồng traffic 1.4.0)

| Hành động | Role |
|---|---|
| Lấy credential ECR, pull image, log qua `awslogs`, lấy secret | **Task execution role** |
| Log qua FireLens, traffic EFS, traffic ứng dụng | **Task role** |

### 7.4 Dual-stack và IPv6-only (tóm tắt)

- Dual-stack: cần account setting `dualStackIPv6`, subnet auto-assign IPv6, platform ≥ 1.4.0; dịch vụ lúc launch (ECR, SSM, Secrets Manager) **vẫn cần route IPv4 `0.0.0.0/0`** (IGW hoặc NAT).
- IPv6-only: không Windows, ECR dùng URI dualstack (không có interface endpoint dualstack), không ECS Exec, không CloudWatch dualstack FIPS endpoint; gọi đích IPv4 qua DNS64 + NAT64.

---

## 8. Load balancing cho service Fargate

> Nguồn: `08-00` mục 5 (chi tiết: file toàn tập *Load balancing* folder 15)

| Loại | Dùng cho |
|---|---|
| **ALB** | HTTP/HTTPS (tầng 7), host/path routing, gRPC, websocket |
| **NLB** | TCP/TLS/UDP (tầng 4); **UDP cần platform ≥ 1.4** |
| **GWLB** | Đưa traffic qua network appliance |
| Classic LB | ❌ không hỗ trợ |

- Target group phải dùng target type **`ip`** (task gắn với ENI, không có "instance").
- SG của task chỉ cho phép traffic từ **SG của load balancer** vào đúng port app.
- Health check của target group khớp port/path của app; cân nhắc `healthCheckGracePeriodSeconds` vì Fargate phải pull image mỗi lần.

```
Client → LB → Target group (type = ip) → Task A / B / C (IP private của ENI)
```

---

## 9. Image pull và thời gian khởi động

> Nguồn: `08-05`, `13-02-08` (SOCI), `05-09-03`

### 9.1 Vì sao quan trọng

Mỗi task chạy trên instance **dùng một lần** → image/layer **không được cache** → mỗi task phải pull **toàn bộ** image → **thời gian pull ≈ thời gian khởi động task** (ảnh hưởng trực tiếp tới tốc độ scale out và deploy).

### 9.2 Bốn cách tối ưu

| Cách | Giảm phần nào | Làm thế nào |
|---|---|---|
| **Đặt image gần** | Thời gian tải | ECR **cùng Region**; dùng **VPC interface endpoint** của ECR; tránh internet / khác Region |
| **Image nhỏ** | Thời gian tải | Ít layer, base image nhẹ (vd **AL2023 minimal**), multi-stage build |
| **Nén zstd / task to hơn** | Thời gian **giải nén** | Giải nén phụ thuộc thuật toán nén + số vCPU → dùng **zstd** hoặc tăng kích thước task |
| **Lazy loading (SOCI)** | Không phải tải hết | Image **> 250 MB** |

⚠️ SOCI và zstd **loại trừ nhau**: SOCI chỉ hỗ trợ image nén **gzip** hoặc không nén.

### 9.3 SOCI (Seekable OCI)

- Tạo **index** các file trong image → container khởi động sau **vài giây**, phần còn lại tải ở nền.
- Fargate **tự phát hiện** image có SOCI index; không cần sửa task definition.
- Điều kiện: Linux platform **1.4.0**, X86_64/ARM64, **ECR private**, gzip hoặc không nén.
- Index lưu như artifact trong **cùng repository**; manifest **v2** đổi **digest** image → phải push lại.
- Tạo index: `soci-snapshotter` CLI (CI/CD) hoặc CloudFormation **SOCI Index Builder**.
- Kiểm tra: task metadata endpoint v4 → `Snapshotter` = `soci` (mặc định `overlayfs`).
- Có thể phải chỉnh **health check grace period** của LB.

### 9.4 Đo thời gian khởi động

- Fargate công bố **timestamp từng trạng thái** của task (xem trên console hoặc `describe-tasks`): 🔎 `createdAt`, `pullStartedAt`, `pullStoppedAt`, `startedAt`.
- So `StartedAt` của container với task (task metadata endpoint) → biết phần thời gian do app.

---

## 10. Ephemeral storage

> Nguồn: `08-07-00`, `08-02-00`

### 10.1 Là gì

Ổ đĩa tạm theo vòng đời task, dùng cho:
- image đã pull (cả **nén** và **giải nén**);
- bind mount chia sẻ giữa container (`volumes`, `mountPoints`, `volumesFrom`);
- file tạm, cache ứng dụng.

Chỉ áp dụng cho **Linux** container (Windows trên Fargate không hỗ trợ bind mount kiểu này).

### 10.2 Theo platform version

| | ≥ 1.4.0 | ≤ 1.3.0 |
|---|---|---|
| Dung lượng | **≥ 20 GiB**, tăng tới **200 GiB** qua `ephemeralStorage.sizeInGiB` | 10 GB (Docker layer) + 4 GB (volume mount) |
| Image chiếm chỗ | Trong khối chung → **trừ khỏi tổng** | Trong phần 10 GB |
| Mã hoá | AES-256 (task từ 28/5/2020), khoá Fargate hoặc **CMK** | — |
| Báo mức dùng | Task metadata endpoint **v4** (task từ 18/11/2022) + Container Insights | — |

```
dung lượng app dùng được = ephemeral storage được cấp − dung lượng image (nén + giải nén)
vd: 20 GiB − 6 GiB image ≈ 14 GiB
```

- Fargate **dành riêng** thêm một phần đĩa cho chính nó: không tính phí, không có trong metric, nhưng thấy trong `df` → số liệu `df` và metric có thể lệch.
- 🔎 Phần vượt 20 GiB bị tính phí theo GB.
- ⚠️ "Ephemeral" ≠ "không cần mã hoá": trong lúc task chạy, dữ liệu vẫn có thể nhạy cảm.
- Dữ liệu cần giữ sau task → **EFS** (bền, chia sẻ), **EBS** (block, 1 volume/task), S3, DB.

---

## 11. Mã hoá ephemeral storage bằng customer managed key

> Nguồn: `08-07-01-00`, `08-02-00`, `Amazon_ECS_08_02_Fargate_Security_Deep_Dive`

| | Mặc định | Customer managed key (CMK) |
|---|---|---|
| Ai quản lý khoá | Fargate (AWS owned key) | **Bạn** (tạo hoặc import vào AWS KMS) |
| Vòng đời khoá | — | Rotate, disable, delete |
| Kiểm toán | — | CloudTrail ghi truy cập và sử dụng khoá |
| Phù hợp | Mặc định | Dữ liệu tài chính, sức khoẻ; yêu cầu compliance |
| Phí Fargate thêm | — | **Không** — chỉ phí KMS chuẩn |

Giới hạn:

```
1 KMS key  → 50.000 grant (mặc định)
1 task     → 1 grant
⇒ tối đa 50.000 task ĐỒNG THỜI dùng chung 1 key (xin tăng theo từng trường hợp)
```

Rủi ro vận hành (theo deep dive trong folder):
1. Disable / lên lịch xoá / key policy sai → **task mới không khởi chạy được** với cấu hình storage mong muốn.
2. Key policy theo least privilege; cảnh báo thay đổi khoá qua CloudTrail.
3. Kiểm thử trước khi rotate/thay khoá; có quy trình phê duyệt disable/delete.

🔎 Cách cấu hình: key được gắn ở mức **cluster** (`managedStorageConfiguration.fargateEphemeralStorageKmsKeyId` trong `create-cluster`/`update-cluster`), key policy phải cho phép principal Fargate tạo grant. Xem trang *Create an encryption key for Fargate ephemeral storage*.

---

## 12. Bảo mật: mô hình cô lập và giới hạn

> Nguồn: `08-01`, `08-02-01`, `Amazon_ECS_08_02_Fargate_Security_Deep_Dive`

### 12.1 Trách nhiệm chung

| AWS lo | Bạn lo |
|---|---|
| Host, lớp ảo hoá, container runtime, vá hạ tầng, platform revision | Image và thư viện; task definition; task role + execution role; VPC/subnet/SG/NACL/route; secret, dữ liệu, khoá mã hoá; log, monitoring, phản ứng sự cố; cách nhóm container vào task |

### 12.2 Bốn giới hạn của môi trường Fargate

| Giới hạn | Hệ quả | Thay thế |
|---|---|---|
| **Không privileged container** | Không Docker-in-Docker, không thao tác thiết bị host | EC2 nếu bắt buộc |
| **Hạn chế Linux capability** (`CAP_SYS_ADMIN`, `CAP_NET_ADMIN`… bị chặn) | Không agent sửa routing/interface, không kernel module | Chỉ thêm được **`CAP_SYS_PTRACE`** cho công cụ quan sát/bảo mật |
| **Không truy cập host** | Cả khách hàng **lẫn nhân viên AWS** không kết nối được host; container không chạm filesystem, device, network, runtime của host | **ECS Exec** để chạy lệnh / mở shell / thu thập chẩn đoán |
| **Mạng** | IP lấy từ subnet của bạn | Security group + network ACL |

⚠️ Đừng "mô phỏng" privileged bằng cách đưa socket, credential hay API quản trị mạnh vào container — phá vỡ ranh giới Fargate cung cấp.

### 12.3 ECS Exec — đường truy cập đặc quyền

- Chỉ cấp `ecs:ExecuteCommand` cho người/role cần.
- 🔎 Task role cần quyền `ssmmessages:*` (CreateControlChannel, CreateDataChannel, OpenControlChannel, OpenDataChannel); bật bằng `--enable-execute-command` trên service/task.
- Bật log session nếu chính sách yêu cầu.
- Không dùng ECS Exec để sửa production; sửa nguồn → build image → redeploy (**immutable workload**).

```bash
# 🔎
aws ecs execute-command --cluster <CLUSTER> --task <TASK_ID> --container app \
  --interactive --command "/bin/sh"
```

### 12.4 Những hiểu lầm hay gặp

| Hiểu lầm | Thực tế |
|---|---|
| "Serverless nên AWS lo toàn bộ bảo mật" | AWS lo hạ tầng; bạn vẫn lo image, IAM, network, secret, data, monitoring |
| "Container trong cùng task cô lập như hai task" | Chung compute, storage, network namespace |
| "Ephemeral thì không cần mã hoá" | Dữ liệu vẫn tồn tại khi task chạy |
| "Có GuardDuty thì khỏi hardening" | GuardDuty là lớp phát hiện, không thay preventive control |
| "Không SSH được thì không debug được" | Log, metric, trace, ECS Exec |

---

## 13. Bảo mật: best practice

> Nguồn: `08-02-00`, `Amazon_ECS_08_02_Fargate_Security_Deep_Dive`

### 13.1 Ba best practice của docs

| # | Best practice | Ý chính |
|---|---|---|
| 1 | **Mã hoá ephemeral storage bằng KMS / CMK** | Mặc định đã AES-256 với khoá Fargate; CMK khi cần kiểm soát + audit |
| 2 | **`SYS_PTRACE` chỉ khi cần** | Capability duy nhất thêm được; cho debugger, profiler, syscall tracing (vd **Falco**) |
| 3 | **GuardDuty Runtime Monitoring** | Agent nhẹ, AWS quản lý; quan sát truy cập file, thực thi process, kết nối mạng → phát hiện leo thang đặc quyền, credential lộ, IP/domain độc hại, malware |

`SYS_PTRACE` nhạy cảm vì process có nó quan sát được memory, tham số, syscall của process khác. Chỉ cấp cho **container quan sát**:

```json
"linuxParameters": { "capabilities": { "add": ["SYS_PTRACE"] } }
```

### 13.2 Các lớp kiểm soát bổ sung cho nhau

```
Trước khi chạy        Khi triển khai              Khi đang chạy
──────────────        ─────────────────────       ─────────────────────
Quét image (ECR)      IAM tối thiểu, SG chặt,     GuardDuty Runtime
Pin digest            storage mã hoá, secret      Monitoring, log, alarm
```

### 13.3 Network hardening

- **Inbound**: chỉ mở port app; nguồn là SG của LB; không public IP nếu không cần; SG tách theo chức năng.
- **Outbound**: đừng coi là vô hại (container bị chiếm có thể tải payload, tuồn dữ liệu). Liệt kê đích thật sự cần (ECR, Logs, Secrets, DB, API ngoài) → ưu tiên **VPC endpoint**; kiểm thử cùng luồng khởi động (chặn quá tay → không pull image / lấy secret / gửi log).

### 13.4 Quy trình xử lý finding GuardDuty

1. Đưa finding vào hệ thống cảnh báo / ticket.
2. Xác định severity cần phản ứng ngay.
3. Lưu task ARN, cluster, service, image digest, thời điểm.
4. Cô lập / thay task bằng deployment sạch.
5. Thu hồi credential có thể bị lộ.
6. Điều tra image, log ứng dụng, CloudTrail, network.
7. Sửa nguyên nhân gốc rồi mới khôi phục.

---

## 14. Task retirement và bảo trì

> Nguồn: `08-08-00`

### 14.1 Retirement là gì

- AWS **bảo trì hạ tầng Fargate**, định kỳ ra **platform revision mới** (vá OS, container runtime, phần mềm Fargate).
- Khi một revision bị retire, **mọi task chạy trên revision đó bị dừng** → task thay thế lên revision mới nhất.
- **Không từ chối được** (mô hình trách nhiệm chung — đây chính là lợi ích của Fargate).
- Nếu **host** bên dưới có vấn đề, ECS thay host **không** gửi thông báo retirement.

### 14.2 Service task vs standalone task

| | Service task | Standalone task |
|---|---|---|
| Khởi chạy bởi | Service (ECS scheduler) | `RunTask` trực tiếp, EventBridge Scheduler, AWS Batch, Step Functions |
| Khi bị retire | **ECS tự thay** | **ECS không thay** |
| Bạn làm gì | Không cần (trừ khi muốn thay sớm) | Thay **trước ngày** retirement; giám sát và tự viết logic thay thế |

Thứ tự thay ở service phụ thuộc cấu hình deployment:

| Cấu hình | Thứ tự |
|---|---|
| `maximumPercent` 200% / `minimumHealthyPercent` 100% (mặc định) | Chạy task mới **trước** → chờ chạy → dừng task cũ |
| `maximumPercent` 100% | Dừng task cũ **trước** → chạy task mới |

Nhận biết task bị dừng do retirement: `describe-tasks` → `stoppedReason` = `ECS is performing maintenance on the underlying infrastructure hosting the task`.

### 14.3 Thông báo

- Gửi qua **AWS Health Dashboard** + **email liên hệ chính** của account.
- Nội dung: **ngày retirement** (task dừng vào hoặc sau ngày này); ID task (standalone) hoặc ID cluster + service (service task); bước tiếp theo.
- Thường một thông báo mỗi loại (service / standalone) mỗi Region; có thể nhiều hơn nếu quá nhiều task.
- Event EventBridge: `source: aws.health`, `detail-type: AWS Health Event`, `detail.service: ECS`, `detail.eventTypeCode: AWS_ECS_TASK_PATCHING_RETIREMENT`, `eventTypeCategory: scheduledChange`.

Trạng thái tài nguyên trong thông báo:

```
Impaired (task còn chạy trên revision sắp retire)
   │ task dừng (AWS retire HOẶC bạn tự thay)
   ▼
Resolved (tự động, thường trong 24h) → mọi tài nguyên Resolved → thông báo đóng
```

### 14.4 Khi task thay thế không chạy được

Nguyên nhân do cấu hình phía khách hàng: IAM role thiếu/sai, subnet thiếu IP/capacity, SG sai, task definition lỗi → task cũ **vẫn bị dừng**, không có task thay → **service giảm capacity**. Giám sát số task chạy + CloudWatch metric trong đợt retirement.

### 14.5 Best practice cho service

- Nhiều replica (≥ 2, trải nhiều AZ).
- `minimumHealthyPercent` = 100%.
- App stateless, bắt `SIGTERM`, không phụ thuộc trạng thái cục bộ.

---

## 15. Chuẩn bị cho task retirement

> Nguồn: `08-08-01`

Ba bước:

```
1. Đặt wait time (0/7/14 ngày) HOẶC bật EC2 event windows
2. Bắt thông báo retirement → báo team, tự động hoá
3. (Tuỳ chọn) Chủ động redeploy trước ngày retirement
```

### 15.1 Bước 1a — `fargateTaskRetirementWaitPeriod`

| `value` | Sau khi gửi thông báo |
|---|---|
| `0` | Retire **ngay** |
| `7` | Chờ 7 ngày (**mặc định**) |
| `14` | Chờ 14 ngày |

```bash
# Chạy bằng root hoặc administrative user
aws ecs put-account-setting-default --name fargateTaskRetirementWaitPeriod --value 14
```

Docs khuyến nghị wait time **ngắn** để lên revision mới sớm. Lịch đã lên **không đổi được**; cấu hình mới chỉ áp cho retirement tương lai.

### 15.2 Bước 1b — EC2 event windows (từ 18/12/2025)

Cho phép chọn **chính xác khung giờ** retire (vd cuối tuần). Được **ưu tiên hơn** wait time.

```bash
# 1. Bật một lần
aws ecs put-account-setting-default --name fargateEventWindows --value enabled

# 2. Tạo window (time range hoặc cron) — đã sửa lỗi thiếu "}]" của docs
aws ec2 create-instance-event-window \
  --time-range StartWeekDay=saturday,StartHour=0,EndWeekDay=saturday,EndHour=4 \
  --tag-specifications "ResourceType=instance-event-window,Tags=[{Key=env,Value=prod}]" \
  --name weekend-maintenance

aws ec2 create-instance-event-window \
  --cron-expression "* 21-23 * * 2,3" \
  --name tue-wed-night

# 3. Gắn window
aws ec2 associate-instance-event-window --instance-event-window-id iew-0abcdef1234567890 \
  --association-target "InstanceTags=[{Key=aws:ecs:serviceArn,Value=<SERVICE_ARN>}]"
```

| Phạm vi | Tag |
|---|---|
| Service | `aws:ecs:serviceArn` = ARN service |
| Cluster | `aws:ecs:clusterArn` = ARN cluster |
| Mọi task Fargate | `aws:ecs:fargateTask` = `true` |

Thứ tự chọn window cho một task:

```
window của SERVICE (không áp cho standalone task)
  → window của CLUSTER
    → window cho MỌI task Fargate
      → fargateTaskRetirementWaitPeriod
```

Ràng buộc:

| Điểm | Giá trị |
|---|---|
| Mỗi window | Mở ≥ **4 giờ/tuần**, mỗi khoảng ≥ **2 giờ** |
| Cluster/service lớn | Khoảng ≥ **8 giờ** hoặc xuất hiện **ít nhất mỗi 3 ngày** |
| Task chạy tối thiểu trước khi retire | **3 ngày** (trừ khi người dùng dừng / sự cố phần cứng nghiêm trọng) |
| Đảm bảo | **Best effort** — bị retire ngoài window thì kéo dài / tăng tần suất |

Ví dụ docs (3 window):

| Window | Thời gian | Gắn với |
|---|---|---|
| Mặc định | 0h–4h mỗi ngày | `aws:ecs:fargateTask` (mọi task) |
| Dev | Thứ 7 + CN cả ngày | `aws:ecs:clusterArn` (cluster dev) |
| Thanh toán | Thứ 7 + CN 0h–4h | `aws:ecs:serviceArn` (service thanh toán) |

### 15.3 Bước 2 — Bắt thông báo

```
Fargate → AWS Health Dashboard ─┬─► email liên hệ chính
                                 └─► EventBridge rule
                                      (detail-type "AWS Health Event",
                                       eventTypeCode "AWS_ECS_TASK_PATCHING_RETIREMENT")
                                       └─► Lambda → Slack Incoming Webhook / ChatOps
```

✍️ 🔎 Event pattern cho rule:

```json
{
  "source": ["aws.health"],
  "detail-type": ["AWS Health Event"],
  "detail": {
    "service": ["ECS"],
    "eventTypeCode": ["AWS_ECS_TASK_PATCHING_RETIREMENT"]
  }
}
```

Có sẵn mẫu: **AWS Health Aware** (Chime/Slack/Teams) và repo *capturing-aws-fargate-task-retirement-notifications*.

### 15.4 Bước 3 — Chủ động thay task

| Loại | Cách |
|---|---|
| Service rolling update | `aws ecs update-service --cluster <C> --service <S> --force-new-deployment` trước ngày retirement |
| Service blue/green (CodeDeploy) | Tạo deployment mới cùng task definition revision (`aws deploy create-deployment`) |
| Standalone task | Tự dừng và chạy task thay thế |

---

## 16. Usage metric, quota và giám sát

> Nguồn: `08-00` mục 6, `05-08-01`, `Amazon_ECS_08_AWS_Fargate_Overview`

- **Usage metric** của Fargate ứng với **service quota** (số task/vCPU On-Demand và Spot đồng thời mỗi Region; quota tính cả EKS pod chạy Fargate).
- Vẽ lên dashboard CloudWatch; đặt **alarm khi gần chạm quota**; xin tăng quota sớm; theo dõi **từng Region**.
- 🔎 Metric nằm trong namespace `AWS/Usage`, dimension `Service=Fargate`, `Resource=OnDemand` / `Spot`, metric `ResourceCount`; quota hiện tại tính theo **vCPU** (🔎 kiểm tra).

Phân biệt:

| Loại metric | Đo gì | Ở đâu |
|---|---|---|
| Usage metric | Mức dùng so với quota | CloudWatch `AWS/Usage` |
| Application metric | CPU, memory, request, latency, lỗi, ephemeral storage | Container Insights, CloudWatch Logs, X-Ray |

Các nguồn giám sát khác:
- **Container Insights**: CPU, memory, network, ephemeral storage reserved/used.
- **EventBridge**: task state change, retirement (Health), throttling.
- **Task metadata endpoint v4**: dung lượng ephemeral storage, `Snapshotter`, mốc thời gian.

---

## 17. Triển khai từng bước

> ✍️ / 🔎 Ghép từ các mục trên; lệnh CLI cần kiểm tra lại với `aws <service> <lệnh> help`. Production nên dùng IaC.

### 17.1 Chuẩn bị mạng

```
VPC 2–3 AZ
├── Public subnet: ALB, NAT gateway (1/AZ)
└── Private subnet: task Fargate (assignPublicIp=DISABLED)
VPC endpoint (tuỳ chọn, giảm phí NAT): S3 gateway, ecr.api, ecr.dkr, logs, secretsmanager
SG: sg-alb (443 từ internet) → sg-task (8080 từ sg-alb)
```

### 17.2 IAM

```bash
# Execution role (ECR, logs, secret)
aws iam create-role --role-name orders-execution-role \
  --assume-role-policy-document '{"Version":"2012-10-17","Statement":[{"Effect":"Allow","Principal":{"Service":"ecs-tasks.amazonaws.com"},"Action":"sts:AssumeRole"}]}'
aws iam attach-role-policy --role-name orders-execution-role \
  --policy-arn arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy
# + inline policy secretsmanager:GetSecretValue cho secret cần dùng

# Task role: chỉ quyền app cần (+ ssmmessages:* nếu bật ECS Exec)
```

### 17.3 Image

```bash
aws ecr create-repository --repository-name orders-api --image-tag-mutability IMMUTABLE \
  --image-scanning-configuration scanOnPush=true
docker buildx build --platform linux/arm64 -t <ECR_URI>/orders-api:<GIT_SHA> --push .
# Image > 250 MB (gzip): cân nhắc tạo SOCI index
```

### 17.4 Cluster

```bash
aws ecs create-cluster --cluster-name prod \
  --capacity-providers FARGATE FARGATE_SPOT \
  --default-capacity-provider-strategy capacityProvider=FARGATE,base=1,weight=1 \
  --settings name=containerInsights,value=enabled
```

### 17.5 Task definition + service

```bash
aws ecs register-task-definition --cli-input-json file://orders-api.json   # mẫu ở mục 5.4

aws ecs create-service --cluster prod --service-name orders-api \
  --task-definition orders-api --desired-count 3 \
  --capacity-provider-strategy capacityProvider=FARGATE,base=2,weight=1 capacityProvider=FARGATE_SPOT,weight=1 \
  --platform-version LATEST \
  --network-configuration "awsvpcConfiguration={subnets=[<PRIV_A>,<PRIV_B>,<PRIV_C>],securityGroups=[<SG_TASK>],assignPublicIp=DISABLED}" \
  --load-balancers targetGroupArn=<TG_ARN_TYPE_IP>,containerName=app,containerPort=8080 \
  --health-check-grace-period-seconds 60 \
  --deployment-configuration "maximumPercent=200,minimumHealthyPercent=100,deploymentCircuitBreaker={enable=true,rollback=true}" \
  --enable-execute-command
```

### 17.6 Standalone task (batch)

```bash
aws ecs run-task --cluster prod --task-definition nightly-job \
  --capacity-provider-strategy capacityProvider=FARGATE_SPOT,weight=1 \
  --network-configuration "awsvpcConfiguration={subnets=[<PRIV_A>],securityGroups=[<SG_JOB>],assignPublicIp=DISABLED}"
```

⚠️ `RunTask` không tự retry khi bị throttle, và standalone task **không được thay** khi retirement → điều phối bằng Step Functions / EventBridge, có retry.

### 17.7 Vận hành

- Account setting: `fargateTaskRetirementWaitPeriod` (hoặc event windows) + rule EventBridge cho retirement.
- Alarm: usage metric gần quota, số task chạy < desired, ephemeral storage gần đầy.
- GuardDuty Runtime Monitoring cho Fargate.
- Định kỳ `--force-new-deployment` để lên revision mới chủ động.

---

## 18. Checklist

```
Thiết kế
[ ] Workload không cần GPU, privileged, kernel capability, host access, edge zone
[ ] stopTimeout cần ≤ 120 s
[ ] Container trong cùng task tin cậy lẫn nhau; workload khác mức tin cậy → task riêng
[ ] Spot chỉ cho workload chịu gián đoạn (base trên FARGATE)

Task definition
[ ] requiresCompatibilities FARGATE, awsvpc, cpu/memory đúng bảng
[ ] cpuArchitecture khớp image (ARM64 cho Graviton)
[ ] execution role / task role tách riêng, tối thiểu
[ ] secrets qua Secrets Manager / SSM; log awslogs có stream-prefix
[ ] ephemeralStorage đủ (trừ dung lượng image)
[ ] capabilities: drop ALL, chỉ add SYS_PTRACE cho container quan sát

Mạng
[ ] Private subnet, nhiều AZ, assignPublicIp=DISABLED
[ ] Đường ra: NAT hoặc VPC endpoint (ECR + S3 + Logs + Secrets)
[ ] SG task chỉ nhận từ SG của LB; target group type ip

Khởi động
[ ] ECR cùng Region, image nhỏ, zstd hoặc SOCI (không dùng cả hai)
[ ] healthCheckGracePeriodSeconds đủ cho pull image + app start

Vận hành
[ ] ≥ 2 replica, minimumHealthyPercent 100%
[ ] Retirement: wait time / event windows + EventBridge → ChatOps
[ ] Standalone task có cơ chế thay thế
[ ] Usage metric alarm, GuardDuty Runtime Monitoring, ECS Exec có kiểm soát
[ ] CMK cho ephemeral storage nếu compliance yêu cầu (nhớ giới hạn 50.000 grant)
```

---

## 19. Troubleshooting

| Triệu chứng | Nguyên nhân hay gặp | Cách xử lý |
|---|---|---|
| `CannotPullContainerError` | Public subnet không bật public IP; private subnet thiếu NAT/endpoint; thiếu S3 gateway endpoint; execution role thiếu quyền ECR | Mục 7.2, 7.3 |
| Task dừng ngay, lỗi lấy secret | Execution role thiếu `secretsmanager:GetSecretValue` / `kms:Decrypt`; không có đường tới Secrets Manager | Thêm quyền, endpoint/NAT |
| Register task definition báo lỗi | Tham số không hợp lệ (`privileged`, `gpu`, `links`…), CPU/memory sai tổ hợp | Mục 5.2, 5.3 |
| `exec format error` (🔎) | Image ARM64 nhưng task X86_64 (hoặc ngược lại) | Sửa `cpuArchitecture` |
| Khởi động chậm, scale out chậm | Image lớn, pull qua internet/khác Region | Mục 9 |
| LB đánh dấu target unhealthy lúc mới chạy | Grace period ngắn so với pull + start | Tăng `healthCheckGracePeriodSeconds` |
| Target group không nhận task | Target type `instance` | Dùng `ip` |
| UDP qua NLB không chạy | Platform < 1.4 | Dùng 1.4.0 / LATEST |
| "No space left on device" | Ephemeral storage hết (image chiếm chỗ) | Tăng `ephemeralStorage`, giảm image, ghi ra EFS/S3 |
| Task bị dừng với `stoppedReason` "ECS is performing maintenance…" | Task retirement | Service: tự thay; standalone: tự chạy lại |
| Service hụt capacity sau retirement | Task thay thế không chạy được (IAM, subnet hết IP, SG, task def) | Sửa cấu hình; alarm số task |
| Task không mount được EFS | Platform 1.3.0; SG chặn NFS 2049; access point/IAM mà chưa bật transit encryption | Mục 6.3, file toàn tập task definition |
| Task mới không chạy sau khi đổi KMS key | Key bị disable/xoá, key policy sai | Mục 11 |
| Không chạy được task Fargate mới, "quota" | Chạm Fargate quota của Region | Usage metric, xin tăng quota |
| Task Spot bị dừng giữa chừng | Spot bị thu hồi (cảnh báo 2 phút) | Bắt SIGTERM, retry, `base` trên FARGATE |

---

## 20. Chỗ docs có lỗi / không khớp

| Chỗ | Vấn đề | Cách hiểu / sửa |
|---|---|---|
| `08-02-00` lệnh `run-task` | Dòng `--count 1` thiếu `\` | Thêm `\` |
| `08-08-00` mở đầu vs mục 6 | Một chỗ nói scheduler dùng `maximumPercent`, chỗ khác nói `minimumHealthyPercent` | Cả hai mặc định đều dẫn tới "chạy task mới trước, dừng cũ sau" |
| `08-08-00` event mẫu | `affectedEntities` đóng ngoặc sai (`}` trước `]`) | Không phải JSON hợp lệ |
| `08-08-00` event mẫu | Lệnh trong mô tả dùng `-service`, `-force-new-deployment` (một gạch) | Đúng là `--service`, `--force-new-deployment` |
| `08-08-01` `create-instance-event-window` | `Tags=[{Key=K1,Value=V1"` thiếu `}]` | Sửa như mục 15.2 |
| `08-08-01` `update-service` | `—-service` (gạch dài) | `--service` |
| `08-08-01` | Văn nói `force-deployment`, lệnh dùng `--force-new-deployment` | Dùng `--force-new-deployment` |
| `08-08-01` | Câu "task chạy ít nhất 3 ngày" lặp hai lần | — |
| `08-00` | Liệt kê Windows Server 2019; trang tham số (`13-10`) có cả 2022, 2025 | Kiểm tra trang platform version hiện tại |
| `08-07-00` | Dùng GiB cho ≥ 1.4.0 nhưng GB cho ≤ 1.3.0 | Giữ như docs |

---

## 21. Câu hỏi tự kiểm tra (có đáp án)

**1. Fargate là gì? Dùng Fargate thì bạn còn phải làm gì và không còn phải làm gì?**
<details><summary>Đáp án</summary>

Fargate là compute serverless cho ECS, chạy container mà không phải quản lý server hay cluster EC2.
- Không còn phải: cấp phát, cấu hình, scale máy ảo; chọn loại server; quyết định khi nào scale cluster; tối ưu việc xếp task.
- Vẫn phải: đóng gói container; chọn CPU/memory; định nghĩa mạng (VPC, subnet, SG) và IAM; vận hành ứng dụng (image, log, secret, monitoring).
</details>

**2. "Mỗi task Fargate có ranh giới cô lập riêng" nghĩa là gì? Container trong cùng task có cô lập với nhau không?**
<details><summary>Đáp án</summary>

Mỗi task chạy trong một môi trường ảo hoá phần cứng riêng (dùng một lần, một khách hàng), không dùng chung kernel, CPU, memory, ENI, ephemeral storage với task khác.

Container trong cùng task thì không cô lập với nhau: chúng dùng chung host, CPU/memory, ephemeral storage và (Linux) network namespace, gọi nhau qua `localhost`, không được trùng port. Vì vậy chỉ đặt các container tin cậy lẫn nhau vào cùng một task.
</details>

**3. Kể 5 trường hợp không nên dùng Fargate.**
<details><summary>Đáp án</summary>

1. Cần GPU.
2. Cần privileged / Docker-in-Docker.
3. Cần `CAP_SYS_ADMIN`, `CAP_NET_ADMIN` hoặc kernel module.
4. Cần custom AMI hoặc truy cập thiết bị host.
5. Chạy ở Local Zones, Wavelength, Outposts.

Thêm: graceful shutdown lâu hơn 120 giây; cần swap theo container; cần cache image trên host.
</details>

**4. FARGATE và FARGATE_SPOT khác nhau thế nào? Trộn hai loại ra sao?**
<details><summary>Đáp án</summary>

- FARGATE: capacity on-demand.
- FARGATE_SPOT: chạy trên capacity dư, giá giảm, nhưng có thể bị thu hồi với cảnh báo trước 2 phút; hợp batch, worker, dev/staging.

Cả hai có sẵn trên mọi cluster và trộn được trong cùng một strategy bằng `base` và `weight`. Ví dụ `FARGATE base=2 weight=1` cộng `FARGATE_SPOT weight=3`: 2 task đầu chạy on-demand, phần còn lại chia 1:3.
</details>

**5. Bốn tham số bắt buộc trong task definition Fargate là gì? `cpu: 512, memory: 8192` có được không?**
<details><summary>Đáp án</summary>

Bắt buộc: `requiresCompatibilities: ["FARGATE"]`, `networkMode: awsvpc`, `cpu` và `memory` mức task theo bảng tổ hợp, `operatingSystemFamily`.

`cpu: 512, memory: 8192` không hợp lệ: 0,5 vCPU chỉ đi với 1–4 GB. Muốn 8 GB cần 1 vCPU (2–8 GB) hoặc 2 vCPU (4–16 GB).
</details>

**6. Image ARM64 chạy trên Fargate cần khai báo gì? Nếu quên thì sao?**
<details><summary>Đáp án</summary>

Cần `runtimePlatform.cpuArchitecture: ARM64`. Fargate mặc định `X86_64`, nên nếu quên thì task chạy trên x86 và container không khởi động được vì sai kiến trúc.
</details>

**7. Platform version là gì? Task đang chạy có tự lên revision mới không? Muốn lên thì làm gì?**
<details><summary>Đáp án</summary>

Platform version là môi trường runtime của Fargate, gồm kernel và container runtime. Mỗi version có các revision (vá OS, sửa lỗi, tính năng mới).

Task giữ nguyên revision lúc khởi chạy suốt vòng đời; task mới luôn chạy revision mới nhất. Muốn lên revision mới phải khởi chạy task mới, ví dụ với service thì `update-service --force-new-deployment`.
</details>

**8. Kể 4 khác biệt giữa platform 1.4.0 và 1.3.0.**
<details><summary>Đáp án</summary>

1. 1.4.0 chỉ có một task ENI cho mọi traffic, kể cả lấy credential ECR và secret; 1.3.0 có thêm Fargate-owned ENI không hiện trong flow logs.
2. Ephemeral storage: 1.4.0 có ≥ 20 GiB và tăng được tới 200 GiB; 1.3.0 có 10 GB + 4 GB.
3. 1.4.0 hỗ trợ EFS, UDP qua NLB, SOCI, IPv6, task 8/16/32 vCPU.
4. Ephemeral storage mã hoá AES-256 và có báo cáo mức dùng qua task metadata v4.
</details>

**9. Task Fargate ở public subnet không pull được image. Nguyên nhân hay gặp nhất? Còn ở private subnet thì cần gì?**
<details><summary>Đáp án</summary>

- Public subnet: nguyên nhân hay gặp nhất là quên `assignPublicIp=ENABLED`; không có IP public thì task không ra internet được.
- Private subnet: cần NAT gateway, hoặc VPC endpoint gồm `ecr.api`, `ecr.dkr`, S3 gateway endpoint (vì layer image nằm trên S3), cùng `logs` và `secretsmanager` nếu dùng.

Từ platform 1.4.0, mọi traffic này đều đi qua task ENI.
</details>

**10. Pull image, gửi log `awslogs`, lấy secret, gửi log FireLens, mount EFS, app gọi S3 — mỗi việc dùng role nào?**
<details><summary>Đáp án</summary>

- Execution role: pull image (cả credential ECR), log qua `awslogs`, lấy secret.
- Task role: log qua FireLens, traffic EFS, app gọi S3.
</details>

**11. Service Fargate sau ALB: target group cần cấu hình gì? Muốn route UDP thì sao?**
<details><summary>Đáp án</summary>

Target type phải là `ip`, không phải `instance`, vì task dùng `awsvpc` và gắn với ENI. SG của task chỉ nhận traffic từ SG của LB vào đúng port app. Route UDP thì dùng NLB, và cần platform ≥ 1.4. Classic LB không được hỗ trợ.
</details>

**12. Vì sao thời gian pull image quan trọng với Fargate hơn với EC2? Kể 4 cách tối ưu.**
<details><summary>Đáp án</summary>

Mỗi task Fargate chạy trên instance dùng một lần, không cache image, nên task nào cũng phải pull toàn bộ image. Thời gian pull xấp xỉ thời gian khởi động task.

Bốn cách tối ưu:
1. Đặt image ở ECR cùng Region, dùng VPC interface endpoint.
2. Giảm kích thước image: ít layer, base nhẹ như AL2023 minimal.
3. Dùng nén zstd hoặc tăng kích thước task để giải nén nhanh hơn.
4. Dùng SOCI lazy loading cho image > 250 MB.
</details>

**13. Có thể dùng cả zstd và SOCI cho cùng một image không?**
<details><summary>Đáp án</summary>

Không. SOCI chỉ hỗ trợ image nén gzip hoặc không nén; image nén zstd không dùng được SOCI. Phải chọn một trong hai: zstd để giải nén nhanh hơn, hoặc SOCI để không phải tải hết image.
</details>

**14. Task được cấp 20 GiB ephemeral storage, image nén + giải nén chiếm 7 GiB. App còn bao nhiêu? Vì sao `df` có thể khác metric?**
<details><summary>Đáp án</summary>

App còn khoảng 13 GiB, vì image (cả bản nén lẫn giải nén) nằm trên chính ephemeral storage. `df` khác metric vì Fargate dành riêng thêm một phần đĩa cho chính nó: phần này không tính phí, không có trong metric, nhưng vẫn hiện trong `df`. Muốn nhiều hơn thì tăng `ephemeralStorage.sizeInGiB`, tối đa 200.
</details>

**15. Khi nào dùng customer managed key cho ephemeral storage? Giới hạn và rủi ro là gì?**
<details><summary>Đáp án</summary>

Dùng khi cần kiểm soát key policy, tự rotate, audit việc dùng khoá qua CloudTrail, hoặc compliance bắt buộc (dữ liệu tài chính, sức khoẻ). Fargate không thu thêm phí, chỉ tính phí KMS chuẩn.

- Giới hạn: mỗi task dùng 1 grant, mỗi key mặc định có 50.000 grant, nên tối đa 50.000 task đồng thời dùng chung một key.
- Rủi ro: disable, xoá khoá hoặc key policy sai khiến task mới không khởi chạy được.
</details>

**16. Kể 4 giới hạn bảo mật của môi trường Fargate.**
<details><summary>Đáp án</summary>

1. Không có privileged container, nên không chạy được Docker-in-Docker.
2. Linux capability bị hạn chế (`CAP_SYS_ADMIN`, `CAP_NET_ADMIN`…); chỉ thêm được `CAP_SYS_PTRACE`.
3. Không truy cập được host: cả khách hàng lẫn nhân viên AWS đều không vào được; debug bằng ECS Exec.
4. Mạng kiểm soát bằng SG + NACL; IP lấy từ subnet của bạn.
</details>

**17. `SYS_PTRACE` dùng để làm gì và vì sao không nên bật mặc định?**
<details><summary>Đáp án</summary>

Đây là capability duy nhất thêm được trên Fargate, dùng cho debugger, profiler, công cụ trace syscall và agent bảo mật như Falco. Không nên bật mặc định vì process có `SYS_PTRACE` quan sát được memory, tham số và syscall của process khác; nếu công cụ bị xâm nhập thì rủi ro lớn. Chỉ cấp cho đúng container quan sát.
</details>

**18. GuardDuty Runtime Monitoring cho Fargate phát hiện được gì? Có thay được việc hardening không?**
<details><summary>Đáp án</summary>

Nó dùng một agent nhẹ do AWS quản lý, quan sát truy cập file, thực thi process và kết nối mạng, để phát hiện leo thang đặc quyền, dùng credential bị lộ, giao tiếp với IP/domain độc hại, và malware.

Nó không thay được hardening: đây là lớp phát hiện, không sửa được IAM hay SG cấu hình sai, cũng không thay được việc quét image.
</details>

**19. Task retirement là gì? Có từ chối được không? Service task và standalone task bị ảnh hưởng ra sao?**
<details><summary>Đáp án</summary>

Task retirement là khi AWS cho nghỉ một platform revision cũ (để vá bảo mật hoặc cập nhật) và dừng mọi task đang chạy trên revision đó. Không từ chối được, vì việc bảo trì hạ tầng thuộc trách nhiệm của AWS.
- Service task: ECS scheduler tự thay. Với `minimumHealthyPercent` 100% (mặc định), task mới chạy trước rồi task cũ mới dừng.
- Standalone task (`RunTask`, EventBridge Scheduler, Batch, Step Functions): ECS không thay. Bạn phải tự thay trước ngày retirement và có logic giám sát.
</details>

**20. Làm sao biết một task bị dừng do retirement?**
<details><summary>Đáp án</summary>

Chạy `describe-tasks`: `stoppedReason` sẽ là `ECS is performing maintenance on the underlying infrastructure hosting the task`. Trước đó, thông báo đến qua AWS Health Dashboard và email, với event EventBridge có `eventTypeCode = AWS_ECS_TASK_PATCHING_RETIREMENT`. Tài nguyên trong thông báo chuyển từ trạng thái Impaired sang Resolved.
</details>

**21. Cấu hình thời điểm retirement như thế nào? So sánh wait time và EC2 event windows.**
<details><summary>Đáp án</summary>

- `fargateTaskRetirementWaitPeriod`: giá trị `0` (retire ngay), `7` (mặc định) hoặc `14` ngày, đặt bằng `put-account-setting(-default)` với quyền root/admin.
- EC2 event windows (từ 18/12/2025): bật `fargateEventWindows=enabled`, tạo window bằng `create-instance-event-window` (time range hoặc cron), rồi gắn vào service, cluster hoặc mọi task bằng tag `aws:ecs:serviceArn` / `aws:ecs:clusterArn` / `aws:ecs:fargateTask`. Window phải mở ≥ 4 giờ/tuần, mỗi khoảng ≥ 2 giờ; task được chạy ít nhất 3 ngày; chỉ là best effort.

Event window được ưu tiên hơn wait time. Lịch retirement đã lên thì không đổi được.
</details>

**22. Một task thuộc service thanh toán, cluster của nó và account đều có event window. Task dùng window nào? Standalone task trong cluster đó dùng window nào?**
<details><summary>Đáp án</summary>

Thứ tự ưu tiên là service → cluster → mọi task → wait time.
- Task của service thanh toán dùng window của service.
- Standalone task không áp dụng window của service, nên dùng window của cluster.
</details>

**23. Retirement xảy ra nhưng service bị hụt task. Vì sao và phòng thế nào?**
<details><summary>Đáp án</summary>

ECS không khởi chạy được task thay thế do cấu hình sai: IAM role thiếu/sai, subnet hết IP/capacity, SG sai, task definition lỗi. Task cũ vẫn bị dừng nên service giảm capacity.

Cách phòng:
- Giám sát số task chạy và CloudWatch metric.
- Chạy nhiều replica trên nhiều AZ.
- Thỉnh thoảng `--force-new-deployment` để phát hiện sớm cấu hình hỏng.
</details>

**24. Thiết kế pipeline nhận thông báo retirement và báo vào Slack.**
<details><summary>Đáp án</summary>

Fargate → AWS Health Dashboard (gửi kèm email) → EventBridge rule lọc `source=aws.health`, `detail-type="AWS Health Event"`, `detail.service=ECS`, `eventTypeCode=AWS_ECS_TASK_PATCHING_RETIREMENT` → Lambda → Slack Incoming Webhook. Có sẵn mẫu: AWS Health Aware, và repo capturing-aws-fargate-task-retirement-notifications.
</details>

**25. Không SSH được vào Fargate thì debug thế nào? Cần lưu ý gì khi bật ECS Exec?**
<details><summary>Đáp án</summary>

Debug bằng log (CloudWatch/FireLens), metric (Container Insights), trace (X-Ray), và ECS Exec để chạy lệnh hoặc mở shell trong container.

Khi bật ECS Exec, coi nó là đường truy cập đặc quyền:
- Chỉ cấp `ecs:ExecuteCommand` cho người cần.
- Task role cần quyền `ssmmessages` (🔎).
- Bật log session.
- Không dùng để sửa production; sửa image rồi redeploy.
</details>

**26. Usage metric của Fargate dùng để làm gì? Khác application metric ra sao?**
<details><summary>Đáp án</summary>

Usage metric đo mức dùng tài nguyên của account so với service quota của Fargate (On-Demand/Spot, theo Region, tính cả EKS pod). Dùng nó để vẽ dashboard, đặt alarm khi gần chạm quota và xin tăng quota sớm. Application metric (CPU, memory, latency, lỗi, ephemeral storage) đo sức khoẻ của workload, qua Container Insights, Logs, X-Ray.
</details>

**27. Batch job đêm chạy bằng `RunTask` trên FARGATE_SPOT. Có những rủi ro gì và giảm thiểu thế nào?**
<details><summary>Đáp án</summary>

Rủi ro:
1. Spot bị thu hồi với cảnh báo 2 phút.
2. Retirement không tự thay standalone task.
3. `RunTask` không tự retry khi bị throttle.

Giảm thiểu:
- Job phải idempotent, có checkpoint ra S3/DB, bắt SIGTERM.
- Điều phối bằng Step Functions (hoặc EventBridge) có retry.
- Theo dõi event task state change.
- Cân nhắc fallback sang FARGATE on-demand.
</details>

---

## 22. Bản đồ nguồn

| Mục | File nguồn |
|---|---|
| 1, 2, 4, 6, 8, 16 | `08-00-ecs-architect-for-fargate.md`, `Amazon_ECS_08_AWS_Fargate_Overview.md` |
| 2, 3, 12 | `08-01-ecs-fargate-security-considerations.md`, `08-02-01-ecs-fargate-security-considerations-detail.md` |
| 13 | `08-02-00-ecs-fargate-security-best-practices.md`, `Amazon_ECS_08_02_Fargate_Security_Deep_Dive.md` |
| 9 | `08-05-ecs-fargate-linux-image-pull-behavior.md` (+ SOCI ở `13-02-08`) |
| 10 | `08-07-00-ecs-fargate-task-ephemeral-storage.md` |
| 11 | `08-07-01-00-ecs-fargate-ephemeral-storage-customer-managed-keys.md` |
| 14 | `08-08-00-ecs-fargate-task-retirement-maintenance.md` |
| 15 | `08-08-01-ecs-fargate-prepare-task-retirement.md` |
| 5, 7 | Folder 13: `13-02-04`, `13-02-08`, `13-10` |
| 3, 4, 16 | Folder 05: `05-00`, `05-01`, `05-03`, `05-08-01`, `05-09-04`, `05-09-05` |
| 17, 18, 19 | ✍️ / 🔎 tổng hợp |

Liên quan: `08-99-thuyet-trinh-cto.md` (slide cho CTO), file toàn tập *Architect* (`05-98`), *Task definition* (`13-98`), *Load balancing* (`15-04-05-99`), *Service Connect* (`15-04-07-01-99`).

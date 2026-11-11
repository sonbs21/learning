# Amazon ECS task definition parameters for Amazon EC2

> Nguồn: **Amazon ECS Developer Guide — Amazon ECS task definition parameters for Amazon EC2**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task_definition_parameters_ec2.html>
>
> **Cách đọc file này**
> - Trang gốc là **trang tham chiếu rất dài**. File này **tóm lược** từng tham số thành bảng, cùng cách làm với hai file *task definition parameters* cho Managed Instances và Fargate. Không phải bản dịch từng chữ; khi cần câu chữ hoặc ví dụ code chính xác, đọc link gốc.
> - Vì nhiều tham số mô tả **giống hệt** trang Fargate / Managed Instances, file này tập trung vào **điểm riêng của EC2** và ghi "như trang Fargate" ở những chỗ trùng.
> - Khối **📘 Theo docs AWS**: nội dung từ trang gốc, diễn đạt lại. Khối **💡 Giải thích thêm**: giải thích hoặc đối chiếu.
> - Ký hiệu: **🪟✗ = không hỗ trợ cho Windows container**; **🔌✗ = không hỗ trợ với network mode `awsvpc`**; **🐧 = chỉ Linux**.

---

## Mục lục

1. [Cấu trúc task definition](#1-cấu-trúc-task-definition)
2. [Tham số mức task](#2-tham-số-mức-task)
   - [2.1 Family, capacity, IAM role](#21-family-capacity-iam-role)
   - [2.2 Network mode](#22-network-mode)
   - [2.3 Runtime platform](#23-runtime-platform)
   - [2.4 Task size](#24-task-size)
3. [Container definitions — tham số tiêu chuẩn](#3-container-definitions--tham-số-tiêu-chuẩn)
4. [Container definitions — tham số nâng cao](#4-container-definitions--tham-số-nâng-cao)
   - [4.1 Restart policy, health check](#41-restart-policy-health-check)
   - [4.2 Environment](#42-environment)
   - [4.3 Network settings](#43-network-settings)
   - [4.4 Storage và logging](#44-storage-và-logging)
   - [4.5 Security](#45-security)
   - [4.6 Resource limits, Docker labels](#46-resource-limits-docker-labels)
5. [Container definitions — tham số khác](#5-container-definitions--tham-số-khác)
6. [Elastic Inference, placement constraints, proxy](#6-elastic-inference-placement-constraints-proxy)
7. [Volumes](#7-volumes)
8. [Tags và tham số mức task khác](#8-tags-và-tham-số-mức-task-khác)
9. [Biến cấu hình ECS agent xuất hiện trong trang](#9-biến-cấu-hình-ecs-agent-xuất-hiện-trong-trang)
10. [So sánh EC2 – Fargate – Managed Instances](#10-so-sánh-ec2--fargate--managed-instances)
11. [Chỗ không khớp giữa các trang docs](#11-chỗ-không-khớp-giữa-các-trang-docs)
12. [Tóm tắt](#12-tóm-tắt)

---

## 1. Cấu trúc task definition

**📘 Theo docs AWS**

Task definition gồm: **family**, **IAM task role**, **network mode**, **container definitions**, **volumes**, **task placement constraints** và **capacity**.
- **Bắt buộc**: `family` và container definitions.
- **Tuỳ chọn**: task role, network mode, volumes, task placement constraints, launch type.

**💡 Giải thích thêm**

Khác Fargate: trên EC2, **placement constraints** là một phần chính thức của task definition, và **network mode thật sự tuỳ chọn** (có mặc định).

---

## 2. Tham số mức task

### 2.1 Family, capacity, IAM role

**📘 Theo docs AWS**

| Tham số | Kiểu | Bắt buộc | Ghi chú |
|---|---|---|---|
| `family` | String | **Có** | Revision bắt đầu từ 1 và tăng dần. |
| `requiresCompatibilities` | String array | Không | Giá trị: `EC2`. ECS kiểm tra tham số có đáp ứng yêu cầu của EC2. |
| `taskRoleArn` | String | Không | IAM role cho container gọi AWS API. **Windows**: khi launch **ECS-optimized Windows Server AMI**, phải bật tuỳ chọn **`-EnableTaskIAMRole`**, và container phải chạy thêm đoạn code cấu hình. (Xem *Amazon EC2 Windows instance additional configuration* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task-iam-roles.html#windows_task_IAM_roles>.) |
| `executionRoleArn` | String | Có điều kiện | Role cho ECS agent; cần hay không tuỳ yêu cầu của task. |

**💡 Giải thích thêm**

Đây chính là phần "cấu hình bổ sung" cho task role trên Windows mà file *Windows EC2 task definition differences* nhắc tới: tuỳ chọn `-EnableTaskIAMRole` khi khởi tạo agent trên AMI Windows.

### 2.2 Network mode

**📘 Theo docs AWS**

| OS của instance | Giá trị hợp lệ | Mặc định khi bỏ trống |
|---|---|---|
| **Linux** | `none`, `bridge`, `awsvpc`, `host` | **`bridge`** |
| **Windows** | `default`, `awsvpc` | **`default`** |

- **`none`**: container không có kết nối ra ngoài; **không khai báo được port mapping**.
- **`bridge`**: mạng ảo tích hợp của Docker trên Linux (driver `bridge`).
- **`host`**: dùng mạng của host, map port container thẳng vào ENI của instance; **không dùng được dynamic port mapping**; phải khai báo `hostPort` cụ thể; không chạy được nhiều task của cùng task definition trên một instance.

  > **Important (docs):** Với `host`, không chạy container bằng user root (UID 0).

- **`awsvpc`**: task có ENI riêng; phải chỉ định `NetworkConfiguration` khi tạo service hoặc chạy task. (Xem *Amazon ECS task networking options for EC2* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task-networking.html>.)
- **`default`**: mạng ảo tích hợp của Docker trên Windows (driver `nat`).
- `host` và `awsvpc` cho hiệu năng mạng cao nhất (dùng network stack EC2), nhưng không dùng được dynamic host port mapping.

**💡 Giải thích thêm**

Nội dung này khớp file *task networking options for EC2*. Điểm thêm của trang này: mode `none` **không cho khai báo port mapping**.

### 2.3 Runtime platform

**📘 Theo docs AWS**

| Tham số | Bắt buộc | Mặc định | Giá trị hợp lệ | Ghi chú |
|---|---|---|---|---|
| `operatingSystemFamily` | Có điều kiện | `LINUX` | `LINUX`, `WINDOWS_SERVER_2025_FULL`, `WINDOWS_SERVER_2025_CORE`, `WINDOWS_SERVER_2022_CORE`, `WINDOWS_SERVER_2022_FULL`, `WINDOWS_SERVER_2019_FULL`, `WINDOWS_SERVER_2019_CORE`, `WINDOWS_SERVER_2016_FULL`, `WINDOWS_SERVER_2004_CORE`, `WINDOWS_SERVER_20H2_CORE` | Mọi task definition trong service cùng giá trị; khớp `platformFamily` của service. |
| `cpuArchitecture` | Có điều kiện | (không có) | `X86_64`, `ARM64` | Không khai báo → ECS đặt theo cấu hình capacity provider. Muốn chắc kiến trúc thì khai báo. `ARM64` cho task Linux. |

**💡 Giải thích thêm**

EC2 hỗ trợ **nhiều phiên bản Windows hơn** Fargate (thêm 2016 Full, 2004 Core, 20H2 Core). `cpuArchitecture` hoạt động như Managed Instances (không có mặc định), khác Fargate (mặc định `X86_64`).

### 2.4 Task size

**📘 Theo docs AWS**

- Trên EC2, CPU và memory mức task là **tuỳ chọn**.

  > **Note (docs):** CPU/memory mức task **bị bỏ qua với Windows container**; khuyến nghị đặt ở mức container.

| Tham số | Kiểu | Bắt buộc | Ghi chú |
|---|---|---|---|
| `cpu` | String | Có điều kiện | 🪟✗. Hard limit CPU unit (`1024` hoặc `1 vCPU`). Tuỳ chọn. Cluster không có instance đủ CPU thì task thất bại. Hỗ trợ từ **0,125** đến **192 vCPU**. |
| `memory` | String | Có điều kiện | 🪟✗. Hard limit memory (`3072` hoặc `3 GB`). Tuỳ chọn, giá trị bất kỳ. Có memory mức task thì memory mức container là tuỳ chọn. Cluster không có instance đủ memory thì task thất bại. (Xem *Reserving Amazon ECS Linux container instance memory* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/memory-management.html>.) |

**💡 Giải thích thêm**

| | EC2 | Managed Instances | Fargate |
|---|---|---|---|
| CPU mức task | Tuỳ chọn, 0,125–**192** vCPU | Tuỳ chọn, 0,125–10 vCPU | **Bắt buộc**, theo bảng tổ hợp (tới 32 vCPU) |

---

## 3. Container definitions — tham số tiêu chuẩn

**📘 Theo docs AWS**

### Name, image, versionConsistency

Như trang Fargate: `name` (bắt buộc, ≤ 255 ký tự), `image` (bắt buộc; không tag → `latest`; cập nhật image không lan sang task đang chạy), `versionConsistency` (`enabled` mặc định / `disabled`).

### Memory

| Tham số | Ghi chú riêng của trang EC2 |
|---|---|
| `memory` | Hard limit. **Phải khai báo memory ở mức task hoặc mức container** (ít nhất một trong hai). Khai báo cả `memory` và `memoryReservation` thì `memory` > `memoryReservation`. |
| `memoryReservation` | Soft limit. 🪟✗. Ví dụ 128/300 như các trang khác. |

Tối thiểu 6 MiB (Docker 20.10.0+) / 4 MiB (Docker 19.03.13-ce trở xuống).

### Port mappings — điểm riêng của EC2

| Trường | Điểm riêng |
|---|---|
| `containerPort` | Với capacity provider EC2, khai báo `containerPort` mà **không** có `hostPort` → container tự nhận host port trong **dải ephemeral**. Port tự gán **không tính** vào quota 100 port được giữ. |
| `containerPortRange` | Dùng được với mode **`bridge`** hoặc **`awsvpc`** (Fargate/Managed Instances chỉ `awsvpc`). Với `bridge`, agent tìm host port trống trong dải ephemeral mặc định rồi đưa cho Docker bind. Linux và Windows đều được. Agent ≥ 1.67.0, `ecs-init` ≥ 1.67.0-1; tối đa 100 dải. |
| `hostPort` | Hai kiểu: **static** (khai báo host port không bị giữ) hoặc **dynamic** (bỏ trống hoặc đặt `0` → nhận port trong dải ephemeral theo OS và phiên bản Docker). Dải ephemeral mặc định đọc từ `/proc/sys/net/ipv4/ip_local_port_range`, không có thì `49153–65535`; không khai báo host port trong dải này. Có thể đặt dải tuỳ chỉnh bằng biến agent **`ECS_DYNAMIC_HOST_PORT_RANGE`** — hữu ích khi task lỗi khởi động vì **trùng port** với tiến trình khác (vd kết nối ra ngoài cũng dùng dải ephemeral). Port giữ sẵn: `22`, `2375`, `2376`, `51678–51680`; tối đa 100 port giữ mỗi instance. |
| `protocol` | `tcp` (mặc định) / `udp`. **UDP** chỉ có trên instance chạy agent **1.2.0+** (vd AMI `amzn-ami-2015.03.c-amazon-ecs-optimized`) hoặc agent đã cập nhật lên **1.3.0+**. Service Connect chỉ `tcp`. |
| `appProtocol` | Service Connect: `http`, `http2`, `grpc`. |
| `name` | Cần cho Service Connect và VPC Lattice. |

Windows: port mapping dùng địa chỉ gateway **`NetNAT`**, không có loopback → không truy cập port container từ chính host.

### Private repository credentials

Như các trang khác (`credentialsParameter`, console phải dùng ARN đầy đủ).

**💡 Giải thích thêm**

Đây là trang duy nhất giải thích đầy đủ **dynamic port mapping**: ở mode `bridge`, bỏ trống `hostPort` (hoặc `0`) → nhiều task giống nhau chạy được trên cùng instance, ALB dùng target type `instance` với port động. File *task networking options for EC2* chỉ nhắc rằng `host` không có tính năng này.

---

## 4. Container definitions — tham số nâng cao

### 4.1 Restart policy, health check

**📘 Theo docs AWS**

Giống trang Fargate / Managed Instances:
- Restart policy: `enabled` (bắt buộc), `ignoredExitCodes` (≤ 50), `restartAttemptPeriod` 60–1800 giây, mặc định 300.
- Health check: `command` (`CMD`/`CMD-SHELL`), `interval` 5–300 (30), `timeout` 2–60 (5), `retries` 1–10 (3), `startPeriod` 0–300 (tắt).

### 4.2 Environment

**📘 Theo docs AWS**

| Tham số | Điểm riêng của trang EC2 |
|---|---|
| `cpu` (container) | Số CPU unit của instance type = vCPU × 1024. Linux: CPU share 2–262144. Hành vi theo phiên bản agent: **≤ 1.1.0** — null/0 → 1024 share, 1 → 2 share; **≥ 1.2.0** — null, 0, 1 → 2 share; **≥ 1.84.0** — giá trị > 256 vCPU được đưa cho Docker là 256 (tương đương 262144 share). Windows: quota tuyệt đối, null/0 = 1% một CPU. |
| `gpu` | Số nguyên, số thập phân (GPU phân đoạn, đặt task lên instance **G6f**) hoặc `ALL`. 🪟✗. (Xem <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/ecs-gpu.html>.) |
| Elastic Inference accelerator | 🪟✗ (dịch vụ đã ngừng). |
| `essential`, `entryPoint`, `command`, `workingDirectory` | Như các trang khác. |
| `environmentFiles` | 🪟✗. Tối đa 10 file `.env` trên S3. **Khi bật FIPS**, tên bucket có **dấu chấm** (`.`) **không hỗ trợ** — agent không pull được file, task không khởi động. |
| `environment`, `secrets` | Như các trang khác. |

**💡 Giải thích thêm**

Ghi chú FIPS + tên bucket có dấu chấm chỉ xuất hiện trên trang EC2; nếu dùng `environmentFiles` trong môi trường FIPS, đặt tên bucket không có dấu chấm.

### 4.3 Network settings

**📘 Theo docs AWS**

| Tham số | Trạng thái | Ghi chú |
|---|---|---|
| `disableNetworking` | 🪟✗ 🔌✗ | Mặc định `false`. |
| `links` | 🪟✗ 🔌✗ | Chỉ mode `bridge`. Cô lập mạng trên instance do security group và VPC quyết định. |
| `hostname` | 🔌✗ | — |
| `dnsServers` | 🪟✗ 🔌✗ | — |
| `dnsSearchDomains` | 🪟✗ 🔌✗ | Mẫu ký tự hợp lệ: chữ, số, `-`, `.`, tối đa khoảng 254 ký tự. Ánh xạ `--dns-search`. |
| `extraHosts` | 🪟✗ 🔌✗ | Thêm dòng vào `/etc/hosts` (`hostname`, `ipAddress`). |

**💡 Giải thích thêm**

Trên EC2 Linux với mode `bridge` hoặc `host`, **cả sáu tham số này đều dùng được** — khác hẳn Fargate và Managed Instances. Nếu chuyển sang `awsvpc` (ví dụ để dùng ECS Gateway), phải bỏ chúng.

### 4.4 Storage và logging

**📘 Theo docs AWS**

**`readonlyRootFilesystem`** — 🪟✗, mặc định `false`.

**`mountPoints`**, **`volumesFrom`** — như trang Fargate (Windows chỉ mount trên cùng ổ với `$env:ProgramData`; để `readOnly` mặc định `false` với Windows).

**`logConfiguration`** — điểm riêng của EC2:
- **Driver được hỗ trợ đầy đủ**: `awslogs`, `fluentd`, `gelf`, `json-file`, `journald`, `syslog`, `splunk`, `awsfirelens`.
- ECS agent trên instance phải **đăng ký** các log driver có sẵn qua biến **`ECS_AVAILABLE_LOGGING_DRIVERS`** trước khi container dùng được. (Xem *Amazon ECS container agent configuration* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/ecs-agent-config.html>.)
- Muốn dùng driver khác daemon Docker thì hệ thống log phải được cấu hình đúng trên instance (hoặc server log từ xa).
- Option `awslogs`: `awslogs-create-group`, `awslogs-region` (bắt buộc), `awslogs-group` (bắt buộc), `awslogs-stream-prefix` (trang EC2 ghi **"Optional"**), `awslogs-datetime-format`, `awslogs-multiline-pattern`.
- `mode` / `max-buffer-size`: như trang Fargate — mặc định `non-blocking` từ **25/6/2025**, đổi được bằng `mode` hoặc account setting `defaultLogDriverMode`; `max-buffer-size` mặc định **`10 m`**; memory mức task nên lớn hơn tổng memory container cộng buffer log.
- `splunk`, `awsfirelens` (`log-driver-buffer-limit`, option theo đích), `secretOptions`: như trang Fargate.

**`firelensConfiguration`** — `type` `fluentd`/`fluentbit`; `config-file-type` `s3` hoặc `file`.

**💡 Giải thích thêm**

Trên EC2, bạn kiểm soát daemon Docker và agent, nên dùng được nhiều log driver hơn (`fluentd`, `gelf`, `syslog`…) — với điều kiện khai báo trong `ECS_AVAILABLE_LOGGING_DRIVERS` (cùng file `/etc/ecs/ecs.config` như `ECS_IMAGE_PULL_BEHAVIOR` ở file *image pull behavior*).

### 4.5 Security

**📘 Theo docs AWS**

| Tham số | Ghi chú |
|---|---|
| `credentialSpecs` | Như trang Fargate: tối đa 1 ARN (SSM hoặc S3) tới file `CredSpec` cho Active Directory; dạng `credentialspecdomainless:` (instance không cần join domain) hoặc `credentialspec:` (instance phải join domain trước). Khuyến nghị dùng thay `dockerSecurityOptions`. |
| `privileged` | Quyền cao trên host (giống root). **AWS khuyến nghị không dùng**; trong đa số trường hợp có thể khai báo đúng quyền cần thiết bằng các tham số cụ thể. 🪟✗, không hỗ trợ Fargate. Mặc định `false`. |
| `user` | Định dạng `user`, `user:group`, `uid`, `uid:gid`, `user:gid`, `uid:group`. Với `host` không chạy root. 🪟✗. |
| `dockerSecurityOptions` | Giá trị: `no-new-privileges`, `apparmor:PROFILE`, `label:value`, `credentialspec:CredentialSpecFilePath`. Linux: nhãn tuỳ chỉnh SELinux/AppArmor; cũng dùng tham chiếu credential spec cho AD. Ánh xạ `--security-opt`. Agent phải đăng ký với **`ECS_SELINUX_CAPABLE=true`** hoặc **`ECS_APPARMOR_CAPABLE=true`** trước khi container dùng được các tuỳ chọn này. |

**💡 Giải thích thêm**

`privileged` và `dockerSecurityOptions` chỉ thực sự có ý nghĩa trên EC2 (Fargate và Managed Instances đều không hỗ trợ). `no-new-privileges` là một tuỳ chọn hardening đơn giản, chặn tiến trình trong container tự nâng quyền — phần giải thích ý nghĩa này là của mình.

### 4.6 Resource limits, Docker labels

**📘 Theo docs AWS**

- **`ulimits`** — 🪟✗; ghi đè giới hạn mặc định của OS; cần Docker Remote API ≥ 1.18. Mỗi phần tử có `name` (`core`, `cpu`, `data`, `fsize`, `locks`, `memlock`, `msgqueue`, `nice`, `nofile`, `nproc`, `rss`, `rtprio`, `rttime`, `sigpending`, `stack`), `softLimit`, `hardLimit` (đều bắt buộc). Trang EC2 **không** nêu giá trị mặc định `nofile` (khác trang Fargate).
- **`dockerLabels`** — map label; Docker Remote API ≥ 1.18.

---

## 5. Container definitions — tham số khác

**📘 Theo docs AWS**

### 5.1 Linux parameters (🪟✗)

| Trường | Điểm riêng của EC2 |
|---|---|
| `capabilities.add` | Danh sách giá trị đầy đủ: `ALL` và từng capability (`AUDIT_CONTROL`, `AUDIT_READ`, `CHOWN`, `NET_ADMIN`, `SYS_ADMIN`, `SYS_PTRACE`…). (Trang cũng có một mục `add` thứ hai chỉ ghi `SYS_PTRACE` — xem mục 11.) |
| `capabilities.drop` | `ALL` hoặc từng capability. |
| `devices` | **Hỗ trợ**: `hostPath` (bắt buộc), `containerPath`, `permissions` (`read`/`write`/`mknod`, mặc định cả ba). |
| `initProcessEnabled` | Docker Remote API ≥ 1.25. |
| `maxSwap` | **Hỗ trợ** — `0` = không swap; bỏ trống = theo instance; cần có để `swappiness` có hiệu lực. |
| `swappiness` | **Hỗ trợ** — 0–100, mặc định 60; bị bỏ qua nếu không có `maxSwap`. **Amazon Linux 2023 không hỗ trợ.** |
| `sharedMemorySize` | **Hỗ trợ** — kích thước `/dev/shm` (MiB). |
| `tmpfs` | **Hỗ trợ** — `containerPath` (bắt buộc), `mountOptions`, `size` (MiB, bắt buộc). |

### 5.2 Container dependency và timeout

| Tham số | Điểm riêng của EC2 |
|---|---|
| `dependsOn` | Cần agent **≥ 1.26.0** (khuyến nghị bản mới nhất). AMI ECS-optimized Amazon Linux cần `ecs-init` **≥ 1.26.0-1**; instance launch từ AMI **`20190301`** trở đi đã có sẵn. Condition: `START`, `COMPLETE`, `SUCCESS` (hai cái này không đặt trên container essential), `HEALTHY`. |
| `startTimeout` | Tối đa **120 giây**. |
| `stopTimeout` | Không khai báo → dùng biến agent **`ECS_CONTAINER_STOP_TIMEOUT`**; không có cả hai → **30 giây** (Linux và Windows). Cần agent ≥ 1.26.0 / `ecs-init` ≥ 1.26.0-1 (AMI `20190301`+ có sẵn). Trang EC2 **không nêu** giá trị tối đa của `stopTimeout`. |

### 5.3 System controls, interactive, pseudo terminal

- **`systemControls`** — 🪟✗. Không khuyến nghị đặt sysctl mạng cho nhiều container trong task `awsvpc` hoặc `host`:
  - `awsvpc`: đặt cho một container là áp cả task; khác nhau thì container khởi động sau cùng thắng;
  - **`host`: sysctl namespace mạng không được hỗ trợ.**
  - IPC `host` → không hỗ trợ sysctl IPC; IPC `task` → áp cho mọi container.
  - Namespace IPC: `kernel.msgmax`, `kernel.msgmnb`, `kernel.msgmni`, `kernel.sem`, `kernel.shmall`, `kernel.shmmax`, `kernel.shmmni`, `kernel.shm_rmid_forced`, `fs.mqueue.*`; namespace mạng: `net.*`.
- **`interactive`**, **`pseudoTerminal`** — mặc định `false`.

**💡 Giải thích thêm**

EC2 là môi trường **duy nhất** trong ba trang hỗ trợ đầy đủ `devices`, `maxSwap`/`swappiness`, `sharedMemorySize`, `tmpfs` và thêm tuỳ ý capability. Liên hệ file *container swap*: hai tham số swap chỉ có tác dụng nếu instance đã bật swap.

---

## 6. Elastic Inference, placement constraints, proxy

**📘 Theo docs AWS**

**Elastic Inference accelerator (deprecated)** — dịch vụ đã ngừng; `deviceName`, `deviceType` (bắt buộc).

**Task placement constraints** — tuỳ chỉnh cách ECS đặt task, theo **AZ, instance type hoặc custom attribute**. (Xem <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task-placement-constraints.html>.)

| Trường | Bắt buộc | Ghi chú |
|---|---|---|
| `expression` | Không | Biểu thức **cluster query language**. (Xem <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/cluster-query-language.html>.) |
| `type` | **Có** | Dùng `memberOf` để giới hạn tập ứng viên. |

**`proxyConfiguration`** (App Mesh) — 🪟✗. Trên EC2 cần agent **≥ 1.26.0** và `ecs-init` **≥ 1.26.0-1** (AMI `20190301`+ có sẵn). Trường `type` (`APPMESH`), `containerName` (bắt buộc), `properties` (`IgnoredUID`/`IgnoredGID`, `AppPorts`, `ProxyIngressPort`, `ProxyEgressPort`, `EgressIgnoredPorts`, `EgressIgnoredIPs`) — như trang Fargate.

**💡 Giải thích thêm**

Placement constraint trên EC2 hỗ trợ **custom attribute** (thuộc tính bạn gắn lên instance) — điểm mà Managed Instances chỉ cho 4 thuộc tính cố định, còn Fargate không có placement constraint.

---

## 7. Volumes

**📘 Theo docs AWS**

| Loại | Trên EC2 (theo trang này) |
|---|---|
| **Amazon EBS** | Hỗ trợ cho task **Linux**; 1 volume mỗi task (standalone task hoặc tạo/cập nhật service). |
| **Amazon EFS** | Hỗ trợ. |
| **FSx for Windows File Server** | Hỗ trợ cho task Windows. |
| **Docker volumes** | **Chỉ có trên EC2**. Tạo dưới `/var/lib/docker/volumes` trên host. Dùng driver `local` có sẵn hoặc driver bên thứ ba (plugin) để tích hợp lưu trữ ngoài (vd EBS). **Windows chỉ dùng driver `local`.** Khai báo bằng `dockerVolumeConfiguration`. |
| **Bind mount** | Hỗ trợ; `host` + `sourcePath` tuỳ chọn. |

Tham số:

| Tham số | Ghi chú |
|---|---|
| `name` | ≤ 255 ký tự (chữ, số, `-`, `_`); tham chiếu ở `sourceVolume`. |
| `host` | Gắn vòng đời bind mount với **EC2 host** thay vì task. Trống → Docker tự gán đường dẫn, dữ liệu không đảm bảo còn. |
| `host.sourcePath` | Đường dẫn trên host. Có giá trị → dữ liệu tồn tại tới khi xoá tay; chưa có thì Docker tạo; có rồi thì nội dung được export. Chỉ hỗ trợ trên EC2 và Managed Instances. |
| `configuredAtLaunch` | `true` → cấu hình volume lúc chạy; bắt buộc `true` để gắn EBS; giúp task definition dùng lại được. |
| `dockerVolumeConfiguration` | `scope` (`task` — tạo khi task bắt đầu, xoá khi task dừng; `shared` — tồn tại sau khi task dừng), `autoprovision` (mặc định `false`; chỉ dùng với `shared`; với `task` phải bỏ trống), `driver` (phải khớp tên driver của Docker vì dùng cho placement; xem bằng `docker plugin ls`), `driverOpts`, `labels`. |
| `efsVolumeConfiguration` | Như trang Fargate: `fileSystemId`, `rootDirectory`, `transitEncryption`, `transitEncryptionPort`, `authorizationConfig` (`accessPointId`, `iam`). Access point hoặc IAM → phải bật transit encryption. |
| `FSxWindowsFileServerVolumeConfiguration` | `fileSystemId`, `rootDirectory`, `authorizationConfig` (`credentialsParameter` — ARN Secrets Manager/SSM; `domain` — FQDN AD). |

**💡 Giải thích thêm**

| Nhu cầu trên EC2 | Lựa chọn |
|---|---|
| Dữ liệu tạm, chỉ sống trong task | Docker volume `scope: task`, hoặc bind mount không `sourcePath` |
| Dữ liệu ở lại trên instance sau khi task dừng | Docker volume `scope: shared`, hoặc bind mount có `sourcePath` |
| Dữ liệu dùng chung nhiều instance / bền vững | EFS (Linux), FSx for Windows (Windows) |
| Block storage riêng cho một task | EBS (`configuredAtLaunch`) |

Lưu ý: dữ liệu `shared` hay `sourcePath` gắn với **instance cụ thể** — task chạy lại trên instance khác sẽ không thấy (suy luận từ việc volume gắn vòng đời với host).

---

## 8. Tags và tham số mức task khác

**📘 Theo docs AWS**

**Tags** — `key`, `value` (không bắt buộc).
> **Important (docs):** Không đưa thông tin định danh cá nhân hay dữ liệu nhạy cảm vào tag (tag được nhiều dịch vụ, kể cả billing, truy cập).

**Tham số mức task khác** (dùng qua **Configure via JSON** trên console):

| Tham số | Ghi chú |
|---|---|
| `ipcMode` | **Hỗ trợ** trên EC2: `host` (chia sẻ IPC với host — tăng nguy cơ lộ IPC namespace), `task` (chia sẻ trong task), `none` (riêng tư). Không khai báo → theo cấu hình Docker daemon. Ràng buộc với `systemControls` IPC như mục 5.3. |
| `pidMode` | `host` / `task` (cả hai hợp lệ trên EC2). Ví dụ: sidecar giám sát cần xem tiến trình khác trong task. Mặc định namespace riêng. `host` tăng nguy cơ lộ process namespace. 🪟✗. |
| `enableFaultInjection` | Boolean, mặc định `false`; `true` → ECS nhận yêu cầu fault injection từ container. |

**💡 Giải thích thêm**

Trang EC2 **không có** `ephemeralStorage` (chỉ dành cho Fargate). Trên EC2, dung lượng lưu trữ tạm phụ thuộc đĩa của instance.

---

## 9. Biến cấu hình ECS agent xuất hiện trong trang

**💡 Giải thích thêm**

Trang EC2 nhắc tới nhiều biến của ECS agent (đặt trong `/etc/ecs/ecs.config`, thường qua user data). Gom lại để tiện tra:

| Biến | Liên quan tới | Theo trang |
|---|---|---|
| `ECS_DYNAMIC_HOST_PORT_RANGE` | `hostPort` | Dải tuỳ chỉnh cho host port gán động, tránh trùng port. |
| `ECS_AVAILABLE_LOGGING_DRIVERS` | `logConfiguration` | Agent phải đăng ký log driver có sẵn trên instance. |
| `ECS_SELINUX_CAPABLE=true` | `dockerSecurityOptions` | Cho phép dùng nhãn SELinux. |
| `ECS_APPARMOR_CAPABLE=true` | `dockerSecurityOptions` | Cho phép dùng profile AppArmor. |
| `ECS_CONTAINER_STOP_TIMEOUT` | `stopTimeout` | Giá trị mặc định khi task definition không khai báo. |
| `-EnableTaskIAMRole` (tuỳ chọn khởi tạo, Windows) | `taskRoleArn` | Bật IAM role cho task trên AMI Windows. |

Yêu cầu phiên bản xuất hiện trong trang: agent ≥ 1.2.0 / 1.3.0 (UDP), ≥ 1.26.0 + `ecs-init` ≥ 1.26.0-1 (`dependsOn`, `stopTimeout`, `proxyConfiguration`; AMI `20190301`+ có sẵn), ≥ 1.67.0 + `ecs-init` ≥ 1.67.0-1 (`containerPortRange`), ≥ 1.84.0 (giới hạn CPU > 256 vCPU); Docker Remote API ≥ 1.18 / 1.19 / 1.25 cho một số tham số.

Cùng file cấu hình còn có `ECS_IMAGE_PULL_BEHAVIOR` (file *image pull behavior*) và `ECS_INSTANCE_IP_COMPATIBILITY` (file *task networking options for EC2*).

---

## 10. So sánh EC2 – Fargate – Managed Instances

**💡 Giải thích thêm**

Ghép từ ba trang tham chiếu. "—" = trang đó không có / không nêu.

| Tham số / đặc điểm | EC2 | Fargate | Managed Instances |
|---|---|---|---|
| `networkMode` | Linux: `none`/`bridge`/`awsvpc`/`host` (mặc định `bridge`); Windows: `default`/`awsvpc` | Bắt buộc `awsvpc` | `awsvpc` (mặc định) / `host` |
| OS | Linux + Windows 2016/2019/2022/2025/2004/20H2 | Linux + Windows 2019/2022/2025 | Linux |
| CPU mức task | Tuỳ chọn, 0,125–192 vCPU | Bắt buộc, theo bảng | Tuỳ chọn, 0,125–10 vCPU |
| Dynamic host port (`bridge`) | Có | — | — |
| `containerPortRange` | `bridge` hoặc `awsvpc` | `awsvpc` | `awsvpc` |
| DNS / hosts / links | Có (trừ `awsvpc` và Windows) | Không | ⛔ |
| `privileged` | Có (không khuyến nghị) | Không | Mô tả, không ghi ⛔ |
| `dockerSecurityOptions` | Có (cần agent capable) | — | ⛔ |
| `capabilities.add` | Danh sách đầy đủ | Chỉ `SYS_PTRACE` | Không giới hạn trong trang |
| `devices`, `tmpfs`, `sharedMemorySize` | Có | `devices`, `sharedMemorySize` ⛔ | `sharedMemorySize` ⛔ |
| `maxSwap` / `swappiness` | Có (AL2023 không có `swappiness`) | ⛔ | ⛔ |
| Log driver | 8 driver (cần `ECS_AVAILABLE_LOGGING_DRIVERS`) | `awslogs`, `splunk`, `awsfirelens` | `awslogs`, `splunk`, `awsfirelens` |
| `startTimeout` tối đa | 120 | 120 | 600 |
| `stopTimeout` | Mặc định từ `ECS_CONTAINER_STOP_TIMEOUT` hoặc 30 | Mặc định 30, tối đa 120 | Mặc định 30, tối đa 86400 |
| Placement constraint | `memberOf` + cluster query language (AZ, instance type, custom attribute) | — | `distinctInstance`, `memberOf` (4 thuộc tính) |
| Docker volume | Có | Không | ⛔ |
| FSx for Windows | Có | Windows Fargate không | ⛔ |
| `ephemeralStorage` | — | Có | ⛔ |
| `ipcMode` | Có | ⛔ | Mô tả, không ghi ⛔ |
| `pidMode` | `host` / `task` | Chỉ `task` (1.4.0+) | `host` / `task` |

---

## 11. Chỗ không khớp giữa các trang docs

**💡 Giải thích thêm**

Mình liệt kê để bạn biết, không tự quyết bên nào đúng. Khi cần chắc chắn, register task definition với `requiresCompatibilities: ["EC2"]` để ECS kiểm tra.

| Chủ đề | Chỗ 1 | Chỗ 2 |
|---|---|---|
| `capabilities.add` | Trang EC2 có mục `add` liệt kê đầy đủ capability | Ngay sau đó trang EC2 có thêm một mục `add` thứ hai chỉ ghi `SYS_PTRACE` (giống trang Fargate) — có vẻ bị trùng/sót |
| `awslogs-stream-prefix` | Trang EC2: "Optional" | Trang Fargate và Managed Instances: bắt buộc |
| `startTimeout` tối đa | EC2 và Fargate: 120 giây | Managed Instances: 600 giây |
| `stopTimeout` tối đa | EC2: không nêu | Fargate: 120 giây; Managed Instances: 86400 giây |
| `max-buffer-size` mặc định | EC2 và Fargate: `10m` | Managed Instances: `1m` |
| Windows Server | EC2: thêm 2016 Full, 2004 Core, 20H2 Core | Fargate: chỉ 2019/2022/2025 |
| Network mode | Phần mở đầu: tuỳ chọn | (khớp với mục `networkMode`: có mặc định `bridge`/`default`) — **khớp** |

---

## 12. Tóm tắt

- **Bắt buộc**: `family`, container definitions; memory phải có ở mức task **hoặc** container.
- **Network mode**: Linux `none`/`bridge`/`awsvpc`/`host` (mặc định `bridge`), Windows `default`/`awsvpc` (mặc định `default`); `none` không có port mapping; `host` không chạy root.
- **OS**: Linux và nhiều phiên bản Windows Server; `cpuArchitecture` không có mặc định.
- **Task size**: tuỳ chọn; CPU 0,125–192 vCPU; bị bỏ qua với Windows.
- **Port**: static hoặc dynamic host port (`bridge`, `hostPort` trống hoặc 0); `ECS_DYNAMIC_HOST_PORT_RANGE` để tránh trùng port; `containerPortRange` cho `bridge`/`awsvpc`; UDP cần agent 1.2.0+/1.3.0+.
- **Chỉ có / đầy đủ trên EC2**: DNS/hosts/links (khi không `awsvpc`), `privileged`, `dockerSecurityOptions` (cần `ECS_SELINUX_CAPABLE` / `ECS_APPARMOR_CAPABLE`), capability đầy đủ, `devices`, `maxSwap`/`swappiness`, `sharedMemorySize`, `tmpfs`, Docker volume, placement constraint với custom attribute, `ipcMode`, `pidMode: host`, 8 log driver (cần `ECS_AVAILABLE_LOGGING_DRIVERS`).
- **Timeout**: `startTimeout` ≤ 120; `stopTimeout` lấy từ `ECS_CONTAINER_STOP_TIMEOUT` hoặc 30 giây; `dependsOn`/`stopTimeout`/`proxyConfiguration` cần agent 1.26.0+ (AMI `20190301`+).
- **Windows**: task role cần `-EnableTaskIAMRole`; không có `memoryReservation`, `environmentFiles`, `readonlyRootFilesystem`, `user`, `ulimits`, `linuxParameters`, `systemControls`, `pidMode`, `proxyConfiguration`, `gpu`; Docker volume chỉ driver `local`.
- **Khác**: `credentialSpecs` cho AD; `environmentFiles` + FIPS không dùng bucket có dấu chấm; log mặc định `non-blocking` từ 25/6/2025; tag không chứa dữ liệu nhạy cảm; `enableFaultInjection`.
- Có vài chỗ không khớp giữa các trang docs (mục 11).

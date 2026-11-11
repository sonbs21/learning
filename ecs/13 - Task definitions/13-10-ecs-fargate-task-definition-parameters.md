# Amazon ECS task definition parameters for Fargate

> Nguồn: **Amazon ECS Developer Guide — Amazon ECS task definition parameters for Fargate**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task_definition_parameters.html>
>
> **Cách đọc file này**
> - Trang gốc là **trang tham chiếu rất dài**. File này **tóm lược** từng tham số thành bảng (kiểu, bắt buộc, giá trị hợp lệ / mặc định / giới hạn, ghi chú riêng cho Fargate), cùng cách làm với file *task definition parameters for Managed Instances*. Không phải bản dịch từng chữ; khi cần câu chữ hoặc ví dụ code chính xác, đọc link gốc.
> - Khối **📘 Theo docs AWS**: nội dung từ trang gốc, diễn đạt lại.
> - Khối **💡 Giải thích thêm**: giải thích hoặc đối chiếu với các file trước.
> - Ký hiệu: **⛔ = trang này ghi "không hỗ trợ trên Fargate"**; **🪟✗ = không hỗ trợ cho Windows container**.

---

## Mục lục

1. [Cấu trúc task definition](#1-cấu-trúc-task-definition)
2. [Tham số mức task](#2-tham-số-mức-task)
   - [2.1 Family, capacity, IAM role, network mode](#21-family-capacity-iam-role-network-mode)
   - [2.2 Runtime platform](#22-runtime-platform)
   - [2.3 Task size](#23-task-size)
3. [Container definitions — tham số tiêu chuẩn](#3-container-definitions--tham-số-tiêu-chuẩn)
4. [Container definitions — tham số nâng cao](#4-container-definitions--tham-số-nâng-cao)
   - [4.1 Restart policy và health check](#41-restart-policy-và-health-check)
   - [4.2 Environment](#42-environment)
   - [4.3 Network settings](#43-network-settings)
   - [4.4 Storage và logging](#44-storage-và-logging)
   - [4.5 Security](#45-security)
   - [4.6 Resource limits và Docker labels](#46-resource-limits-và-docker-labels)
5. [Container definitions — tham số khác](#5-container-definitions--tham-số-khác)
   - [5.1 Linux parameters](#51-linux-parameters)
   - [5.2 Dependency và timeout](#52-dependency-và-timeout)
   - [5.3 System controls, interactive, pseudo terminal](#53-system-controls-interactive-pseudo-terminal)
6. [Elastic Inference và proxy configuration](#6-elastic-inference-và-proxy-configuration)
7. [Volumes](#7-volumes)
8. [Tags](#8-tags)
9. [Tham số mức task khác](#9-tham-số-mức-task-khác)
10. [So sánh nhanh với Managed Instances](#10-so-sánh-nhanh-với-managed-instances)
11. [Chỗ không khớp giữa các trang docs](#11-chỗ-không-khớp-giữa-các-trang-docs)
12. [Tóm tắt](#12-tóm-tắt)

---

## 1. Cấu trúc task definition

**📘 Theo docs AWS**

Task definition gồm: **family**, **IAM task role**, **network mode**, **container definitions**, **volumes** và **launch type**.
- **Bắt buộc**: `family` và container definitions.
- **Tuỳ chọn**: task role, network mode, volumes, launch type.

**💡 Giải thích thêm**

Phần mở đầu nói network mode là "tuỳ chọn", nhưng mục `networkMode` bên dưới ghi **bắt buộc** với Fargate (`awsvpc`). Khi dùng Fargate, hãy coi `networkMode: awsvpc` là bắt buộc.

---

## 2. Tham số mức task

### 2.1 Family, capacity, IAM role, network mode

**📘 Theo docs AWS**

| Tham số | Kiểu | Bắt buộc | Ghi chú |
|---|---|---|---|
| `family` | String | **Có** | Revision đầu là 1, sau đó tăng dần. |
| `requiresCompatibilities` | String array | Không | Giá trị: `FARGATE`. ECS kiểm tra mọi tham số đáp ứng yêu cầu của Fargate; không đáp ứng thì trả client exception. |
| `taskRoleArn` | String | Không | IAM role cho container trong task gọi AWS API. (Xem <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task-iam-roles.html>.) |
| `executionRoleArn` | String | **Có điều kiện** | Role cho ECS agent gọi AWS API thay bạn; cần hay không tuỳ yêu cầu của task. (Xem <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task_execution_IAM_role.html>.) |
| `networkMode` | String | **Có** | Fargate **bắt buộc `awsvpc`**. |

### 2.2 Runtime platform

**📘 Theo docs AWS**

| Tham số | Kiểu | Bắt buộc | Mặc định | Giá trị hợp lệ | Ghi chú |
|---|---|---|---|---|---|
| `operatingSystemFamily` | String | **Có điều kiện** (bắt buộc với Fargate) | `LINUX` | `LINUX`, `WINDOWS_SERVER_2025_FULL`, `WINDOWS_SERVER_2025_CORE`, `WINDOWS_SERVER_2022_FULL`, `WINDOWS_SERVER_2022_CORE`, `WINDOWS_SERVER_2019_FULL`, `WINDOWS_SERVER_2019_CORE` | Mọi task definition trong một service phải cùng giá trị; phải khớp `platformFamily` của service. |
| `cpuArchitecture` | String | Có điều kiện | `X86_64` | `X86_64`, `ARM64` | Để `null` thì gán mặc định khi task khởi chạy. Mọi task definition trong service phải cùng giá trị. `ARM64` dùng cho task **Linux**. (Xem <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/ecs-arm64.html>.) |

**💡 Giải thích thêm**

Khác Managed Instances: trên Fargate, `cpuArchitecture` có mặc định `X86_64` (Managed Instances không có mặc định, ECS tự chọn theo capacity provider). Image ARM64 trên Fargate phải khai báo rõ `ARM64`.

### 2.3 Task size

**📘 Theo docs AWS**

- Với Fargate (cả Linux và Windows), `cpu` và `memory` mức task là **bắt buộc** và chỉ nhận một số giá trị nhất định.

  > **Note (docs):** CPU/memory mức task dùng để xác định loại và kích thước "instance" mà task chạy trên. Với **Windows**, các giá trị này **không được áp đặt lúc chạy**, vì Windows không có cơ chế giới hạn tài nguyên chung cho một nhóm container; muốn áp đặt thì dùng tài nguyên **mức container**.

| Tham số | Kiểu | Bắt buộc | Ghi chú |
|---|---|---|---|
| `cpu` | String | **Có** | Hard limit CPU unit. Ghi `1024` hoặc `1 vCPU`; vCPU được đổi sang CPU unit khi register. Giá trị chọn sẽ quyết định dải `memory` hợp lệ. |
| `memory` | String | **Có** | Hard limit memory. Ghi `3072` (MiB) hoặc `3 GB`; GB được đổi sang MiB khi register. Giá trị chọn quyết định dải `cpu` hợp lệ. |

Tổ hợp CPU–memory hợp lệ (bảng trong mục `cpu`):

| CPU | Memory | OS |
|---|---|---|
| 256 (0,25 vCPU) | 512 MiB, 1 GB, 2 GB | Linux |
| 512 (0,5 vCPU) | 1, 2, 3, 4 GB | Linux |
| 1024 (1 vCPU) | 2 → 8 GB | Linux, Windows |
| 2048 (2 vCPU) | 4 → 16 GB, bước 1 GB | Linux, Windows |
| 4096 (4 vCPU) | 8 → 30 GB, bước 1 GB | Linux, Windows |
| 8192 (8 vCPU) — Linux platform **1.4.0+** | 16 → 60 GB, bước 4 GB | Linux |
| 16384 (16 vCPU) — Linux platform **1.4.0+** | 32 → 120 GB, bước 8 GB | Linux |
| 32768 (32 vCPU) — Linux platform **1.4.0+** | 60, 120, 244 GB | Linux |

**💡 Giải thích thêm**

Bảng này giống bảng trong file *Fargate task definition differences*. Lưu ý: bảng thứ hai (tra theo `memory`) trên trang này **không có** dòng 32 vCPU — xem mục 11.

---

## 3. Container definitions — tham số tiêu chuẩn

**📘 Theo docs AWS**

### Name, image

| Tham số | Kiểu | Bắt buộc | Ghi chú |
|---|---|---|---|
| `name` | String | **Có** | Tối đa 255 ký tự (chữ, số, gạch nối, gạch dưới). |
| `image` | String | **Có** | Như trang Managed Instances: không tag/digest → `latest`; cập nhật image không lan sang task đang chạy; hỗ trợ private registry; định dạng ECR `registry/repository:tag` hoặc `@digest`. |
| `versionConsistency` | String | Không | `enabled` (mặc định) / `disabled` — resolve tag thành digest hay không. (Xem <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/deployment-type-ecs.html#deployment-container-image-stability>.) |

### Memory

| Tham số | Kiểu | Bắt buộc | Ghi chú |
|---|---|---|---|
| `memory` | Integer (MiB) | Không | Hard limit, vượt là bị kill; tổng phải thấp hơn `memory` mức task. |
| `memoryReservation` | Integer (MiB) | Không | Soft limit. 🪟✗ (không hỗ trợ Windows). Ví dụ docs 128/300 như trang Managed Instances. |

Tối thiểu: Docker **20.10.0+** giữ **6 MiB**; Docker **19.03.13-ce trở xuống** giữ **4 MiB**.

### Port mappings

Như trang Managed Instances, kèm các điểm riêng:
- Với `awsvpc` chỉ khai báo `containerPort`; `hostPort` bị bỏ qua.
- **Windows**: port mapping dùng địa chỉ gateway **`NetNAT`** thay vì `localhost`; Windows không có loopback cho port mapping, nên **không truy cập được port của container từ chính host**.
- `appProtocol` (Service Connect): ghi chữ thường `http`, `http2`, `grpc`.
- `containerPort`: **Windows trên Fargate không dùng được port 3150** (đã được giữ).
- `containerPortRange`: chỉ đặt qua API; bắt buộc `awsvpc`; dùng được cho cả Linux và Windows; agent ≥ 1.67.0, `ecs-init` ≥ 1.67.0-1; tối đa 100 dải; giá trị 1–65535; không chồng lấn.
- `protocol`: `tcp` / `udp` (**phân biệt hoa thường**), mặc định `tcp`; giá trị khác bị coi là `tcp`. Service Connect chỉ hỗ trợ `tcp`.
- `name`: cần cho Service Connect và VPC Lattice.

### Private repository credentials

`repositoryCredentials.credentialsParameter`: ARN secret chứa thông tin đăng nhập; qua API/CLI/SDK cùng Region dùng ARN hoặc tên được, qua console phải ARN đầy đủ.

**💡 Giải thích thêm**

Như ở file Managed Instances, trường `name` của port mapping là thứ **ECS Gateway** tham chiếu qua `portName`.

---

## 4. Container definitions — tham số nâng cao

### 4.1 Restart policy và health check

**📘 Theo docs AWS**

Giống trang Managed Instances:

| Nhóm | Tham số | Giá trị chính |
|---|---|---|
| Restart policy | `enabled` (bắt buộc), `ignoredExitCodes` (≤ 50), `restartAttemptPeriod` | 60–1800 giây, mặc định 300 |
| Health check | `command` (`CMD` / `CMD-SHELL`), `interval`, `timeout`, `retries`, `startPeriod` | interval 5–300 (mặc định 30), timeout 2–60 (mặc định 5), retries 1–10 (mặc định 3), startPeriod 0–300 (mặc định tắt) |

### 4.2 Environment

**📘 Theo docs AWS**

| Tham số | Trạng thái | Ghi chú |
|---|---|---|
| `cpu` (container) | Hỗ trợ | Tuỳ chọn trên Fargate; tổng phải thấp hơn `cpu` mức task. Linux: CPU share 2–262144. **Windows**: CPU là **quota tuyệt đối**; null/0 = 1% của một CPU. |
| `gpu` | ⛔ | Không hỗ trợ trên Fargate. |
| Elastic Inference accelerator | ⛔ | Không hỗ trợ trên Fargate. |
| `essential` | Hỗ trợ | Mặc định `true`; mỗi task cần ít nhất một container essential. |
| `entryPoint` | Hỗ trợ | Agent đời cũ xử lý chưa đúng → cập nhật agent hoặc dùng `command`. |
| `command`, `workingDirectory` | Hỗ trợ | — |
| `environmentFiles` | 🪟✗ trên Fargate | Tối đa 10 file `.env` trên S3 (`type` = `s3`); biến khai báo trực tiếp ưu tiên hơn. |
| `environment` | Hỗ trợ | Không nên để thông tin nhạy cảm ở dạng plaintext. |
| `secrets` | Hỗ trợ | `name` + `valueFrom` (ARN Secrets Manager hoặc Parameter Store; khác Region phải ARN đầy đủ). |

### 4.3 Network settings

**📘 Theo docs AWS**

| Tham số | Trạng thái | Ghi chú |
|---|---|---|
| `disableNetworking` | ⛔ | — |
| `links` | Không hỗ trợ với `awsvpc` | Chỉ cho mode `bridge`. |
| `hostname` | Không hỗ trợ với `awsvpc` | — |
| `dnsServers` | ⛔ | — |
| `extraHosts` | Không hỗ trợ với `awsvpc` | — |

**💡 Giải thích thêm**

Vì Fargate luôn dùng `awsvpc`, cả năm tham số trong bảng này thực tế đều **không dùng được** trên Fargate.

### 4.4 Storage và logging

**📘 Theo docs AWS**

**`readonlyRootFilesystem`** — root filesystem chỉ đọc; mặc định `false`; 🪟✗.

**`mountPoints`** — `sourceVolume` (bắt buộc), `containerPath` (bắt buộc), `readOnly` (mặc định `false`). Phải khai báo mount point để gắn **EBS volume** vào task. Windows: chỉ mount được thư mục trên cùng ổ với `$env:ProgramData`, không mount chéo ổ.

**`volumesFrom`** — `sourceContainer` (bắt buộc), `readOnly` (mặc định `false`).

**`logConfiguration`**:
- Danh sách `logDriver` mà trang liệt kê: `awslogs`, `fluentd`, `gelf`, `json-file`, `journald`, `splunk`, `syslog`, `awsfirelens`; nhưng trang cũng nói driver **được hỗ trợ** là **`awslogs`, `splunk`, `awsfirelens`**.
- Cần Docker Remote API ≥ 1.18; phần mềm bổ sung (vd Fluentd aggregator, Logstash nhận Gelf) phải cài ngoài task.
- Option `awslogs`: `awslogs-create-group` (mặc định `false`, cần quyền `logs:CreateLogGroup`), `awslogs-region` (bắt buộc), `awslogs-group` (bắt buộc), `awslogs-stream-prefix` (bắt buộc; stream dạng `prefix-name/container-name/ecs-task-id`; cần để log hiện trên console), `awslogs-datetime-format` / `awslogs-multiline-pattern` (không dùng cùng lúc; có thể ảnh hưởng hiệu năng).
- Option áp cho **mọi** log driver:

| Option | Ghi chú |
|---|---|
| `mode` | `non-blocking` / `blocking`. Đặt mặc định cho cả Region bằng account setting **`defaultLogDriverMode`**. Không khai báo `mode` và không đặt account setting → mặc định **`non-blocking`**. |
| `max-buffer-size` | Mặc định **`10m`**. Dùng với `non-blocking`; buffer đầy thì log mới bị mất. Memory mức task nên lớn hơn tổng memory các container **cộng** buffer log. |

> **Note (docs):** Ngày **25/6/2025**, ECS đổi mặc định log driver mode từ `blocking` sang **`non-blocking`** để ưu tiên tính sẵn sàng của task. Muốn giữ `blocking`: đặt `mode: blocking` trong `logConfiguration`, hoặc đặt account setting `defaultLogDriverMode` = `blocking`.

- `splunk`: cần `splunk-token`, `splunk-url`.
- `awsfirelens`: `log-driver-buffer-limit` giới hạn số dòng log buffer trong memory; option khác theo đích (Firehose, Kinesis Data Streams, OpenSearch, S3) như trang Managed Instances.
- `secretOptions`: secret cho cấu hình log.

**`firelensConfiguration`** — `type` (`fluentd` / `fluentbit`, bắt buộc), `options` (vd `enable-ecs-log-metadata`; `config-file-type` có thể là **`s3`** hoặc `file`, `config-file-value` là ARN S3 hoặc đường dẫn file).

**💡 Giải thích thêm**

So với trang Managed Instances: trang Fargate có thêm account setting `defaultLogDriverMode`, ghi chú đổi mặc định sang `non-blocking` (25/6/2025), và `max-buffer-size` mặc định `10m` (trang Managed Instances ghi `1m`) — xem mục 11. Với FireLens trên Fargate, file cấu hình có thể lấy từ **S3**.

### 4.5 Security

**📘 Theo docs AWS**

(Xem *Amazon ECS task and container security best practices* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/security-tasks-containers.html>.)

| Tham số | Ghi chú |
|---|---|
| `credentialSpecs` | String array (tối đa **1** ARN) trỏ tới file `CredSpec` trong SSM hoặc S3 để cấu hình container xác thực **Active Directory**. Khuyến nghị dùng thay cho `dockerSecurityOptions`. Hai dạng: **`credentialspecdomainless:MyARN`** — có thêm phần secret Secrets Manager chứa thông tin đăng nhập domain; instance **không cần** join domain; các task trên cùng instance có thể join domain khác nhau. **`credentialspec:MyARN`** — một domain; instance **phải join domain** trước khi chạy task. (Xem gMSA cho Windows — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/windows-gmsa.html> và Linux — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/linux-gmsa.html>.) |
| `user` | Định dạng `user`, `user:group`, `uid`, `uid:gid`, `user:gid`, `uid:group`; UID/GID là số nguyên dương. Với mode `host` không chạy bằng root. 🪟✗. |

**💡 Giải thích thêm**

`credentialSpecs` không xuất hiện trên trang Managed Instances. Trên Fargate (không join domain được cho "instance"), dạng phù hợp theo logic là **domainless** — đây là suy luận của mình, trang không nói thẳng.

### 4.6 Resource limits và Docker labels

**📘 Theo docs AWS**

**`ulimits`** — 🪟✗; cần Docker Remote API ≥ 1.18.
- Trên Fargate, mặc định dùng giới hạn của hệ điều hành, **trừ `nofile`**: soft **65535**, hard **65535**, đặt tối đa **1048576**.
- Mỗi phần tử: `name` (bắt buộc; một trong `core`, `cpu`, `data`, `fsize`, `locks`, `memlock`, `msgqueue`, `nice`, `nofile`, `nproc`, `rss`, `rtprio`, `rttime`, `sigpending`, `stack`), `softLimit` và `hardLimit` (bắt buộc; đơn vị byte, giây hoặc số đếm tuỳ loại).

**`dockerLabels`** — map string→string gắn label cho container; cần Docker Remote API ≥ 1.18.

---

## 5. Container definitions — tham số khác

**📘 Theo docs AWS**

Dùng được khi register qua console với **Configure via JSON**.

### 5.1 Linux parameters

`linuxParameters` — 🪟✗.

| Trường | Trạng thái | Ghi chú |
|---|---|---|
| `capabilities.add` | Hỗ trợ | Giá trị hợp lệ duy nhất: **`SYS_PTRACE`**. Ánh xạ `--cap-add`. |
| `capabilities.drop` | Hỗ trợ | Bỏ capability khỏi mặc định của Docker; nhận `ALL` hoặc từng capability (vd `CHOWN`, `NET_RAW`, `SYS_ADMIN`…). Ánh xạ `--cap-drop`. |
| `devices` | ⛔ | (`hostPath` bắt buộc, `containerPath`, `permissions` `read`/`write`/`mknod`.) |
| `initProcessEnabled` | Hỗ trợ | Chạy `init` để chuyển signal và dọn tiến trình; cần Docker Remote API ≥ 1.25. |
| `maxSwap` | ⛔ | — |
| `sharedMemorySize` | ⛔ | — |
| `tmpfs` | (không ghi ⛔ trên trang này) | `containerPath` (bắt buộc), `mountOptions` (danh sách tuỳ chọn mount), `size` (MiB, bắt buộc). |

**💡 Giải thích thêm**

Về bảo mật: dù chỉ **thêm** được `SYS_PTRACE`, bạn có thể **bỏ** nhiều capability (thậm chí `ALL`) để giảm quyền container — cách làm cứng (hardening) hữu ích trên Fargate.

### 5.2 Dependency và timeout

**📘 Theo docs AWS**

| Tham số | Ghi chú |
|---|---|
| `dependsOn` | Cần platform **1.3.0+** (Linux) hoặc **1.0.0** (Windows). `containerName` + `condition`: `START`, `COMPLETE` (không đặt trên container essential), `SUCCESS` (như `COMPLETE` nhưng exit 0, không đặt trên container essential), `HEALTHY` (cần health check; chỉ xác nhận lúc khởi động). Khi tắt thì thứ tự đảo ngược. |
| `startTimeout` | Cần platform **1.3.0+** (Linux). Tối đa **120 giây**. |
| `stopTimeout` | Cần platform **1.3.0+** (Linux). Mặc định **30 giây**, tối đa **120 giây**. |

> **Note (docs):** Container không đáp ứng dependency hoặc hết thời gian thì ECS không đưa container phụ thuộc sang trạng thái tiếp theo.

**💡 Giải thích thêm**

Khác lớn với Managed Instances: trên Fargate, `startTimeout` và `stopTimeout` tối đa **120 giây** (Managed Instances: 600 và 86400). Ứng dụng cần thời gian tắt êm dài hơn 2 phút thì không đặt được trên Fargate.

### 5.3 System controls, interactive, pseudo terminal

**📘 Theo docs AWS**

**`systemControls`** — 🪟✗; trên Fargate chỉ hỗ trợ với platform **1.4.0+** (Linux).
- Không khuyến nghị đặt `systemControls` mạng khác nhau cho nhiều container trong một task `awsvpc`/`host` (áp cho cả task; container khởi động sau cùng quyết định).
- IPC mode `host` → không hỗ trợ `systemControls` IPC; `task` → áp cho mọi container.
- Namespace IPC hợp lệ: `kernel.msgmax`, `kernel.msgmnb`, `kernel.msgmni`, `kernel.sem`, `kernel.shmall`, `kernel.shmmax`, `kernel.shmmni`, `kernel.shm_rmid_forced`, `fs.mqueue.*`. Namespace mạng: `net.*` — **trên Fargate chỉ chấp nhận sysctl có namespace tồn tại bên trong container**.

| Tham số | Mặc định | Ghi chú |
|---|---|---|
| `interactive` | `false` | Cho ứng dụng cần `stdin`/`tty`. |
| `pseudoTerminal` | `false` | Cấp TTY. |

---

## 6. Elastic Inference và proxy configuration

**📘 Theo docs AWS**

**Elastic Inference accelerator (deprecated)** — Amazon Elastic Inference **không còn cung cấp**. Trường `deviceName` (bắt buộc, phải được tham chiếu trong container definition) và `deviceType` (bắt buộc).

**`proxyConfiguration`** — cấu hình proxy **App Mesh**; 🪟✗.

| Trường | Ghi chú |
|---|---|
| `type` | Chỉ `APPMESH`. |
| `containerName` | Bắt buộc — container làm proxy App Mesh. |
| `properties` | Cặp key-value cho CNI plugin: `IgnoredUID` / `IgnoredGID` (UID/GID của container proxy, để proxy bỏ qua traffic của chính nó; có một thì cái kia có thể trống), `AppPorts` (port ứng dụng, traffic được chuyển tới proxy), `ProxyIngressPort`, `ProxyEgressPort`, `EgressIgnoredPorts`, `EgressIgnoredIPs` (có thể là danh sách rỗng). |

**💡 Giải thích thêm**

Trên trang Managed Instances, `proxyConfiguration` bị ghi ⛔; trên Fargate trang này **mô tả là dùng được** (cho Linux).

---

## 7. Volumes

**📘 Theo docs AWS**

Loại volume theo trang:

| Loại | Trên Fargate (theo trang này) |
|---|---|
| **Amazon EBS** | Hỗ trợ cho task **Linux** trên Fargate. Gắn được **1** EBS volume mỗi task khi chạy standalone task hoặc tạo/cập nhật service. |
| **Amazon EFS** | Hỗ trợ. |
| **FSx for Windows File Server** | **Windows container trên Fargate không hỗ trợ**. |
| **Bind mount** | Hỗ trợ (`host` + `sourcePath` tuỳ chọn). |

Tham số:

| Tham số | Ghi chú |
|---|---|
| `name` | Tối đa 255 ký tự (chữ, số, `-`, `_`); tham chiếu ở `sourceVolume`. |
| `host` | Gắn vòng đời bind mount với EC2 host thay vì task; trống thì Docker tự gán đường dẫn, dữ liệu không đảm bảo còn. **Note:** `sourcePath` chỉ hỗ trợ khi task chạy trên **EC2 hoặc Managed Instances**. |
| `configuredAtLaunch` | `true` → cấu hình volume lúc chạy task/tạo service; khi đó không khai báo cấu hình volume khác trong task definition. **Bắt buộc `true`** để gắn EBS. Giúp task definition dùng lại được ở nhiều môi trường. |
| `dockerVolumeConfiguration` | Chỉ hỗ trợ trên **EC2**. (`scope` `task`/`shared`, `autoprovision` mặc định `false` — chỉ dùng với `shared`, `driver`, `driverOpts`, `labels`.) |
| `efsVolumeConfiguration` | Xem bảng dưới. |
| `FSxWindowsFileServerVolumeConfiguration` | `fileSystemId`, `rootDirectory`, `authorizationConfig` (`credentialsParameter` — ARN Secrets Manager hoặc SSM; `domain` — FQDN của AWS Managed Microsoft AD hoặc AD tự quản lý trên EC2). |

`efsVolumeConfiguration`:

| Trường | Bắt buộc | Ghi chú |
|---|---|---|
| `fileSystemId` | Có | ID file system EFS. |
| `rootDirectory` | Không | Thư mục trong EFS mount làm root; bỏ trống hoặc `/` = gốc EFS. Có access point thì phải bỏ trống hoặc `/`. |
| `transitEncryption` | Không | `ENABLED` / `DISABLED` (mặc định `DISABLED`). Dùng IAM authorization thì phải bật. |
| `transitEncryptionPort` | Không | Port gửi dữ liệu mã hoá; không khai báo thì theo chiến lược của EFS mount helper. |
| `authorizationConfig.accessPointId` | Không | Có access point thì `rootDirectory` phải trống hoặc `/`, và phải bật transit encryption. |
| `authorizationConfig.iam` | Không | `ENABLED` / `DISABLED` (mặc định `DISABLED`). Dùng task IAM role khi mount; bật thì phải bật transit encryption. |

**💡 Giải thích thêm**

Mâu thuẫn nhỏ trên trang: phần danh sách loại volume nói bind mount dùng `host` và `sourcePath` tuỳ chọn, nhưng ghi chú ở `host` lại nói `sourcePath` **chỉ hỗ trợ** trên EC2 và Managed Instances. Trên Fargate, nên dùng bind mount **không có `sourcePath`** (ephemeral storage của task).

Quy tắc EFS gói gọn: dùng **access point** hoặc **IAM authorization** → phải bật **`transitEncryption`**.

---

## 8. Tags

**📘 Theo docs AWS**

- Tag gồm `key` và `value` (đều không bắt buộc). (Xem *Tagging Amazon ECS resources* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/ecs-using-tags.html>.)

> **Important (docs):** **Không** đưa thông tin định danh cá nhân hoặc thông tin bí mật/nhạy cảm vào tag. Tag được nhiều dịch vụ AWS truy cập, kể cả billing.

---

## 9. Tham số mức task khác

**📘 Theo docs AWS**

Dùng được khi register qua console với **Configure via JSON**.

| Tham số | Trạng thái trên Fargate | Ghi chú |
|---|---|---|
| `ephemeralStorage` | Hỗ trợ | Mở rộng ephemeral storage (GB) vượt mức mặc định cho task Fargate. Cần platform **1.4.0+** (Linux) hoặc **1.0.0+** (Windows). (Xem <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/bind-mounts.html>.) |
| `ipcMode` | ⛔ | Không hỗ trợ cho Windows và cho task Fargate. (Mô tả `host`/`task`/`none` như trang Managed Instances; `host` tăng nguy cơ lộ IPC namespace.) |
| `pidMode` | Hỗ trợ có điều kiện | Giá trị `host` / `task`; với Linux container, giá trị hợp lệ **duy nhất là `task`**. Ví dụ dùng: sidecar giám sát cần xem tiến trình của container khác trong task. Mặc định namespace riêng cho từng container. Trên Fargate cần platform **1.4.0+** (Linux); không hỗ trợ Windows trên Fargate. |
| `enableFaultInjection` | Hỗ trợ | Boolean, mặc định `false`. `true` → ECS và Fargate nhận **yêu cầu fault injection** từ container của task. |

**💡 Giải thích thêm**

- `ephemeralStorage` là điểm khác rõ với Managed Instances (⛔ ở đó). Bind mount trên Fargate dùng chính vùng ephemeral storage này.
- `enableFaultInjection` không có trên trang Managed Instances; hữu ích cho thử nghiệm chaos engineering — phần nhận xét này là của mình.

---

## 10. So sánh nhanh với Managed Instances

**💡 Giải thích thêm**

Đặt cạnh file *task definition parameters for Managed Instances*, chỉ dựa trên hai trang tham chiếu:

| Tham số / đặc điểm | Fargate | Managed Instances |
|---|---|---|
| `networkMode` | Bắt buộc `awsvpc` | `awsvpc` (mặc định) hoặc `host` |
| `operatingSystemFamily` | Linux + Windows Server 2019/2022/2025 | Chỉ `LINUX` |
| `cpuArchitecture` mặc định | `X86_64` | Không có (theo capacity provider) |
| `cpu`/`memory` mức task | **Bắt buộc**, theo bảng tổ hợp | Tuỳ chọn; CPU 0,125–10 vCPU |
| `gpu`, Neuron | ⛔ | Hỗ trợ (kể cả GPU phân đoạn G6f) |
| `ephemeralStorage` | Hỗ trợ (1.4.0+ / 1.0.0+) | ⛔ |
| `proxyConfiguration` | Hỗ trợ (Linux) | ⛔ |
| `ipcMode` | ⛔ | Mô tả, không ghi ⛔ |
| `pidMode` | Chỉ `task` (Linux, 1.4.0+) | `host` / `task` |
| `devices` | ⛔ | Mô tả, không ghi ⛔ |
| `capabilities.add` | Chỉ `SYS_PTRACE` | Không giới hạn trong trang |
| `startTimeout` / `stopTimeout` tối đa | 120 / 120 giây | 600 / 86400 giây |
| `max-buffer-size` mặc định | `10m` | `1m` |
| `credentialSpecs` | Có | Không có trên trang |
| `enableFaultInjection` | Có | Không có trên trang |
| Bind mount `sourcePath` | Chỉ EC2 / Managed Instances | Phải ở `/var`, `/tmp` |
| EBS | Linux, 1 volume / task | `configuredAtLaunch` |

---

## 11. Chỗ không khớp giữa các trang docs

**💡 Giải thích thêm**

Mình liệt kê để bạn biết, không tự quyết bên nào đúng. Khi cần chắc, register task definition với `requiresCompatibilities: ["FARGATE"]` để ECS kiểm tra.

| Chủ đề | Chỗ 1 | Chỗ 2 |
|---|---|---|
| Network mode | Phần mở đầu trang này: tuỳ chọn | Mục `networkMode`: bắt buộc `awsvpc` |
| Bảng tra theo `memory` | Không có dòng 32 vCPU | Bảng tra theo `cpu` có 32 vCPU (60/120/244 GB) |
| Windows Server 2025 | Có trong `operatingSystemFamily` trang này | File *Fargate task definition differences* chỉ liệt kê 2019 và 2022 |
| `logDriver` | Danh sách giá trị có `fluentd`, `gelf`, `json-file`, `journald`, `syslog` | Cùng trang nói chỉ hỗ trợ `awslogs`, `splunk`, `awsfirelens` (file *Fargate differences* cũng chỉ nêu 3 driver này) |
| `max-buffer-size` mặc định | `10m` (trang này) | `1m` (trang Managed Instances) |
| `nofile` mặc định trên Fargate | Soft 65535 / hard 65535 (trang này và file *Fargate differences*) | Soft 1024 / hard 65535 (đoạn `ulimits` trên trang Managed Instances) |
| `sourcePath` của bind mount | Danh sách loại volume: tuỳ chọn khi dùng bind mount | Ghi chú ở `host`: chỉ hỗ trợ trên EC2 / Managed Instances |
| `hostname` | Trang này: không hỗ trợ với `awsvpc` | File *Fargate differences* không liệt kê `hostname` |
| `tmpfs` | Trang này: mô tả, không ghi ⛔ | File *Fargate differences*: `tmpfs` không hỗ trợ trong `linuxParameters` |

---

## 12. Tóm tắt

- **Bắt buộc**: `family`, container definitions, `networkMode: awsvpc`, `operatingSystemFamily`, và `cpu` + `memory` mức task theo bảng tổ hợp.
- **IAM**: `taskRoleArn` cho ứng dụng; `executionRoleArn` khi task cần (ECR, log, secret…).
- **Runtime**: Linux hoặc Windows Server 2019/2022/2025 (Full/Core); `cpuArchitecture` mặc định `X86_64`, `ARM64` cho Linux. Windows không áp đặt CPU/memory mức task lúc chạy.
- **Không dùng được trên Fargate**: `gpu`, Elastic Inference, `disableNetworking`, `dnsServers`, `devices`, `maxSwap`, `sharedMemorySize`, `ipcMode`, `dockerVolumeConfiguration`; `links`, `hostname`, `extraHosts` (vì luôn `awsvpc`).
- **Windows trên Fargate không có**: `memoryReservation`, `environmentFiles`, `readonlyRootFilesystem`, `user`, `ulimits`, `linuxParameters`, `systemControls`, `proxyConfiguration`, `pidMode`, FSx Windows; port 3150 bị giữ; không loopback cho port mapping.
- **Linux**: `capabilities.add` chỉ `SYS_PTRACE`, `drop` thoải mái; `pidMode` chỉ `task` (1.4.0+); `systemControls` 1.4.0+; `nofile` mặc định 65535, tối đa 1048576.
- **Timeout**: `startTimeout` ≤ 120, `stopTimeout` mặc định 30 / tối đa 120 giây (platform 1.3.0+).
- **Logging**: driver hỗ trợ `awslogs`, `splunk`, `awsfirelens`; mặc định `non-blocking` từ 25/6/2025 (đổi được bằng `mode` hoặc `defaultLogDriverMode`); `max-buffer-size` mặc định `10m`.
- **Storage**: EBS (Linux, 1 volume/task, `configuredAtLaunch: true`), EFS (access point hoặc IAM → phải bật transit encryption), bind mount (không `sourcePath`), `ephemeralStorage` (1.4.0+ / 1.0.0+).
- **Khác**: `credentialSpecs` cho Active Directory (gMSA), `proxyConfiguration` App Mesh, `enableFaultInjection`, tag không chứa dữ liệu nhạy cảm.
- Có nhiều chỗ không khớp giữa các trang docs (mục 11).

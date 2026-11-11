# Daemon task definitions

> Nguồn: **Amazon ECS Developer Guide — Daemon task definitions**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/managed-daemons-task-definitions.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: diễn đạt lại bằng tiếng Việt, bám sát từng ý và đúng thứ tự của trang gốc, không thêm thông tin ngoài trang. Đây không phải bản dịch từng chữ; khi cần câu chữ hoặc ví dụ code chính xác, đọc link gốc ở trên.
> - Khối **💡 Giải thích thêm**: phần giải thích để dễ hiểu hơn, chỉ làm rõ những gì docs đã nói.
> - Ví dụ JSON trong file được viết lại với placeholder (`<...>`); tên trường giữ đúng như docs.

---

## Mục lục

1. [Daemon task definition là gì](#1-daemon-task-definition-là-gì)
2. [Tạo daemon task definition](#2-tạo-daemon-task-definition)
3. [Tham số của daemon task definition](#3-tham-số-của-daemon-task-definition)
4. [Network mode `daemon_bridge`](#4-network-mode-daemon_bridge)
5. [Quyền đặc biệt (privileged capabilities)](#5-quyền-đặc-biệt-privileged-capabilities)
6. [Chia sẻ PID và IPC namespace](#6-chia-sẻ-pid-và-ipc-namespace)
7. [Volumes](#7-volumes)
8. [Tham số container được hỗ trợ](#8-tham-số-container-được-hỗ-trợ)
9. [So sánh với task definition thường trên Managed Instances](#9-so-sánh-với-task-definition-thường-trên-managed-instances)
10. [Tóm tắt](#10-tóm-tắt)

---

## 1. Daemon task definition là gì

**📘 Theo docs AWS**

- Daemon task definition là **bản thiết kế (blueprint)** cho daemon.
- Managed Daemons dùng một **loại tài nguyên riêng** — daemon task definition — **khác** với task definition ECS thông thường.
- Bạn đăng ký daemon task definition để khai báo **container image**, **yêu cầu tài nguyên** và **cấu hình** cho daemon.

**💡 Giải thích thêm**

Vì là tài nguyên riêng, daemon task definition có **API riêng** (`register-daemon-task-definition`, mục 2) và **menu riêng** trên console, không trộn với danh sách task definition thường.

---

## 2. Tạo daemon task definition

### 2.1 Bằng AWS Management Console

**📘 Theo docs AWS**

1. Mở console ECS → thanh điều hướng chọn **Daemon task definitions**.
2. Chọn **Create new daemon task definition**.
3. **Daemon task definition family**: tên duy nhất, tối đa **255** ký tự (chữ số, gạch nối, gạch dưới).
4. (Tuỳ chọn) **Task role**: IAM role cấp quyền cho ứng dụng trong container; để trống nếu container không gọi AWS API.
5. (Tuỳ chọn) **Task execution role**: chọn `ecsTaskExecutionRole` — ECS cần role này để **pull image** và **đẩy log**.
6. (Tuỳ chọn) **Task size**: CPU và memory dành cho daemon (ví dụ docs: `0.25 vCPU` và `512 MB`).
7. Cấu hình container:
   - **Container name**: tối đa 255 ký tự (chữ số, gạch nối, gạch dưới).
   - **Essential container**: chọn **Yes**. Mỗi daemon task definition cần **ít nhất một** container essential; container essential lỗi thì ECS **dừng cả task**.
   - **Image URI**: URI của image (ECR, Docker Hub hoặc registry khác).
8. (Tuỳ chọn) Cấu hình phân bổ tài nguyên, health check, biến môi trường, thu thập log, tag.
9. Xem lại và chọn **Create**.

### 2.2 Bằng AWS CLI

**📘 Theo docs AWS**

Tạo file JSON rồi dùng lệnh **`register-daemon-task-definition`**. Ví dụ của docs là một daemon dùng image busybox chạy vòng lặp in log mỗi 30 giây, gồm các trường `family`, `containerDefinitions` (`name`, `image`, `essential`, `command`, `memoryReservation`).

```json
{
  "family": "<DAEMON_FAMILY>",
  "containerDefinitions": [
    {
      "name": "<CONTAINER_NAME>",
      "image": "<IMAGE_URI>",
      "essential": true,
      "memoryReservation": 512
    }
  ]
}
```

```bash
aws ecs register-daemon-task-definition --cli-input-json file://daemon-taskdef.json
```

---

## 3. Tham số của daemon task definition

**📘 Theo docs AWS**

**Bắt buộc:**

| Tham số | Ý nghĩa |
|---|---|
| `family` | Tên family, nhóm các revision của cùng daemon task definition |
| `containerDefinitions` | Mảng các object **`DaemonContainerDefinition`** mô tả container của daemon |

**Tuỳ chọn:**

| Tham số | Ý nghĩa |
|---|---|
| `cpu` | CPU unit mức task, dạng chuỗi (vd `"256"`) |
| `memory` | Memory mức task (MiB), dạng chuỗi (vd `"512"`) |
| `taskRoleArn` | IAM role cấp quyền cho container trong daemon |
| `executionRoleArn` | IAM role cho ECS agent gọi AWS API (vd pull image từ ECR) |
| `volumes` | Mảng **`DaemonVolume`**. **Chỉ** hỗ trợ bind mount với `host` + `sourcePath`. **Không** hỗ trợ EBS, EFS, FSx for Windows, Docker volume, ephemeral storage |
| `tags` | Cặp key-value gắn lên daemon task definition |
| `pidMode` | Chế độ PID namespace (mục 6) |
| `ipcMode` | Chế độ IPC namespace (mục 6) |

**💡 Giải thích thêm**

Không có `networkMode` trong danh sách — vì network mode do ECS tự đặt (mục 4). Cũng không có `requiresCompatibilities` hay `placementConstraints`, vì daemon luôn chạy một bản trên mỗi instance Managed Instances (file *Managed Daemons*).

---

## 4. Network mode `daemon_bridge`

**📘 Theo docs AWS**

- Daemon dùng network mode đặc biệt **`daemon_bridge`**, do **ECS tự đặt**. **Không khai báo** được network mode trong daemon task definition.
- **Mọi daemon trên một instance dùng chung một network namespace**, truy cập cục bộ qua **IP tĩnh của daemon bridge**:
  - IPv4: **`169.254.172.2`**;
  - IPv6: **`fd00:ec2::172:2`**.
- Task ứng dụng (không phải daemon) giao tiếp với daemon qua địa chỉ này **không cần cấu hình mạng thêm**.
- Vì daemon chạy ở **namespace mạng riêng**, task ứng dụng **không lo trùng port** với daemon.
- Nhưng vì **các daemon dùng chung namespace**, hai daemon trên cùng instance **không bind cùng một port** được. Triển khai nhiều daemon thì mỗi daemon phải dùng **port riêng**.

**💡 Giải thích thêm**

```
┌──────────────────── Instance (Managed Instances) ────────────────────┐
│  Namespace daemon (chung cho mọi daemon) — IP 169.254.172.2          │
│     daemon-log   :24224                                              │
│     daemon-otel  :4317       ← các daemon phải khác port nhau        │
│                                                                      │
│  Task app A (awsvpc, ENI riêng) ──► gửi log tới 169.254.172.2:24224  │
│  Task app B (awsvpc, ENI riêng) ──► gửi trace tới 169.254.172.2:4317 │
└──────────────────────────────────────────────────────────────────────┘
```

Port trong hình là ví dụ của mình. Hệ quả thực tế: ứng dụng cấu hình endpoint của agent là `169.254.172.2:<port>` — cố định trên mọi instance, không cần service discovery.

---

## 5. Quyền đặc biệt (privileged capabilities)

**📘 Theo docs AWS**

- Trên Managed Instances, daemon hỗ trợ **Linux capability đặc quyền** cho thao tác mức hệ thống. Agent bảo mật, công cụ giám sát mạng và agent observability thường cần **truy cập mức kernel**.
- Hai cách cấp quyền:

| Cách | Khi nào dùng | Cấu hình |
|---|---|---|
| **Full privileged mode** | Agent cần truy cập hệ thống rộng | `"privileged": true` trong container definition → cấp **mọi** Linux capability |
| **Từng capability riêng** | Theo nguyên tắc **least privilege** | `linuxParameters.capabilities.add` với các giá trị như **`SYS_ADMIN`**, **`NET_ADMIN`**, **`SYS_PTRACE`**, **`BPF`**, **`PERFMON`** |

```json
"linuxParameters": {
  "capabilities": { "add": ["NET_ADMIN", "SYS_PTRACE"] }
}
```

**💡 Giải thích thêm**

Nên ưu tiên cách thứ hai: chỉ thêm đúng capability agent cần. `BPF` và `PERFMON` thường dành cho agent dùng eBPF để quan sát mạng và hiệu năng — phần giải thích công dụng này là của mình.

Đây là khác biệt lớn với task thường trên Fargate (chỉ thêm được `SYS_PTRACE`) — xem mục 9.

---

## 6. Chia sẻ PID và IPC namespace

**📘 Theo docs AWS**

Trên Managed Instances, daemon hỗ trợ chia sẻ **PID** và **IPC** namespace, phục vụ agent bảo mật, công cụ giám sát và daemon observability cần **nhìn thấy tiến trình** hoặc **tài nguyên IPC** của task ứng dụng.

| Tham số | Kiểu | Bắt buộc | Giá trị | Mặc định | Ý nghĩa |
|---|---|---|---|---|---|
| `pidMode` | String | Không | `none` \| `shared` | `none` | `none`: daemon có PID namespace **riêng**, cô lập. `shared`: daemon **tham gia PID namespace của host**, chia sẻ với task thường dùng `pidMode: "host"` hoặc daemon khác dùng `pidMode: "shared"`. |
| `ipcMode` | String | Không | `none` \| `shared` | `none` | `none`: IPC namespace **riêng**. `shared`: daemon **tham gia IPC namespace của host**, chia sẻ với task thường dùng `ipcMode: "host"` hoặc daemon khác dùng `ipcMode: "shared"`. |

Để chia sẻ với daemon:
- **PID**: task thường phải khai báo **`pidMode: "host"`** trong task definition → container của task thấy được tiến trình của daemon (dùng cho giám sát tiến trình, gửi signal).
- **IPC**: task thường phải khai báo **`ipcMode: "host"`** → container của task truy cập tài nguyên IPC của daemon (shared memory, message queue).

```json
{
  "family": "<DAEMON_FAMILY>",
  "pidMode": "shared",
  "ipcMode": "shared",
  "containerDefinitions": [
    { "name": "<AGENT>", "image": "<IMAGE_URI>", "essential": true }
  ]
}
```

**💡 Giải thích thêm**

Lưu ý tên giá trị khác nhau giữa hai loại task definition:

| | Daemon task definition | Task definition thường |
|---|---|---|
| Chia sẻ với host | `shared` | `host` |
| Không chia sẻ | `none` | (mặc định riêng / `task`) |

Chia sẻ PID/IPC cần **cả hai phía** cùng bật: daemon `shared` **và** task ứng dụng `host`. Với task ứng dụng, chia sẻ namespace host làm giảm cô lập — cân nhắc chỉ bật cho task thực sự cần (theo cảnh báo về `pidMode: host` trong các file *task definition parameters*).

---

## 7. Volumes

**📘 Theo docs AWS**

- Managed Daemons hỗ trợ **bind mount** bằng host volume có **`sourcePath`** → container daemon mount được **thư mục trên host** để đọc log, metric, thông tin hệ thống của EC2 instance bên dưới. Agent thu log, exporter metric, công cụ quét bảo mật thường dùng cách này.
- Volume của daemon **tồn tại theo vòng đời của container instance** bên dưới.

> **Note (docs):** **Không hỗ trợ** EBS, EFS, FSx for Windows File Server, Docker volume và ephemeral storage trong daemon task definition.

> **Note (docs):** Các daemon **không chia sẻ volume** với nhau; mỗi daemon có cấu hình volume riêng.

Ví dụ của docs: daemon **thu log** (fluentd) mount thư mục `/var/log` của host vào `/var/log` trong container, qua `volumes[].host.sourcePath` và `mountPoints`.

```json
{
  "containerDefinitions": [{
    "name": "<LOG_COLLECTOR>",
    "image": "<IMAGE_URI>",
    "mountPoints": [{ "sourceVolume": "host-logs", "containerPath": "/var/log" }]
  }],
  "volumes": [{ "name": "host-logs", "host": { "sourcePath": "/var/log" } }]
}
```

**💡 Giải thích thêm**

Với task thường trên Managed Instances, `sourcePath` phải trỏ vào thư mục ghi được như `/var` hoặc `/tmp` (file *Managed Instances task definition differences*). Trang daemon không nhắc giới hạn đó; mục đích chính ở đây là **đọc** dữ liệu của host. Nếu daemon cần **ghi**, vẫn nên giả định phần lớn filesystem của host là chỉ đọc — đây là suy luận ghép từ hai trang.

---

## 8. Tham số container được hỗ trợ

**📘 Theo docs AWS**

| Nhóm | Tham số |
|---|---|
| Cơ bản | `image` (bắt buộc), `name` (bắt buộc), `essential` (mặc định `true`) |
| Tài nguyên | `cpu`, `memory` / `memoryReservation` (hard / soft limit), `ulimits` |
| Lệnh | `command` / `entryPoint`, `workingDirectory` |
| Môi trường | `environment`, `environmentFiles`, `secrets` |
| Bảo mật | `privileged`, `user`, `readonlyRootFilesystem`, `linuxParameters.capabilities` (thêm / bỏ) |
| Lưu trữ | `mountPoints` (chỉ bind mount với `host` + `sourcePath`) |
| Log | `logConfiguration` (hỗ trợ `awslogs`, `splunk`, `awsfirelens`) |
| Sức khoẻ, khởi động | `healthCheck`, `dependsOn`, `restartPolicy` |
| Kernel, tiến trình | `systemControls`, `linuxParameters.initProcessEnabled` |
| Registry | `repositoryCredentials` |

**💡 Giải thích thêm**

Không có `portMappings` trong danh sách — hợp lý vì daemon dùng `daemon_bridge` với IP cố định, mỗi daemon tự lắng nghe trên port riêng (mục 4). Ý này là suy luận của mình.

`healthCheck` quan trọng với daemon **critical**: daemon unhealthy → ECS drain và thay instance (file *Managed Daemons*).

---

## 9. So sánh với task definition thường trên Managed Instances

**💡 Giải thích thêm**

Ghép từ trang này và các file *Managed Instances task definition differences* / *task definition parameters for Managed Instances*:

| | Daemon task definition | Task definition thường (Managed Instances) |
|---|---|---|
| Loại tài nguyên / API | Riêng (`register-daemon-task-definition`) | `register-task-definition` |
| Network mode | `daemon_bridge` (tự đặt, không khai báo) | `awsvpc` (mặc định) hoặc `host` |
| Truy cập | IP tĩnh `169.254.172.2` / `fd00:ec2::172:2` | ENI riêng (`awsvpc`) hoặc mạng host |
| `privileged` | **Hỗ trợ** | Trang *parameters* mô tả, không ghi ⛔ |
| Capability | `SYS_ADMIN`, `NET_ADMIN`, `SYS_PTRACE`, `BPF`, `PERFMON`… | Không giới hạn trong trang |
| `pidMode` / `ipcMode` | `none` / `shared` | `host` / `task` (`ipcMode` bị trang *differences* ghi là không hợp lệ) |
| Volume | Chỉ bind mount `sourcePath` | Bind mount (`/var`, `/tmp`), EFS, EBS |
| Số bản chạy | Đúng 1 mỗi instance | Theo service / RunTask |

---

## 10. Tóm tắt

- **Daemon task definition** là tài nguyên riêng, blueprint cho daemon (image, tài nguyên, cấu hình).
- Tạo qua **console** (menu **Daemon task definitions**) hoặc **CLI** `register-daemon-task-definition`.
- **Bắt buộc**: `family`, `containerDefinitions` (mảng `DaemonContainerDefinition`); cần ít nhất một container essential.
- **Tuỳ chọn**: `cpu`, `memory`, `taskRoleArn`, `executionRoleArn` (pull image, đẩy log), `volumes`, `tags`, `pidMode`, `ipcMode`.
- **Network**: `daemon_bridge` tự đặt; mọi daemon chung một namespace, IP `169.254.172.2` / `fd00:ec2::172:2`; app không trùng port với daemon, nhưng các daemon phải dùng port khác nhau.
- **Quyền**: `privileged: true` hoặc thêm từng capability (`SYS_ADMIN`, `NET_ADMIN`, `SYS_PTRACE`, `BPF`, `PERFMON`).
- **PID/IPC**: `none` (mặc định) / `shared`; task app phải dùng `host` để chia sẻ với daemon.
- **Volume**: chỉ bind mount `host.sourcePath`, sống theo vòng đời instance; không EBS/EFS/FSx/Docker volume/ephemeral; daemon không chia sẻ volume với nhau.
- **Container**: hỗ trợ các tham số phổ biến (image, tài nguyên, env/secrets, privileged, user, log `awslogs`/`splunk`/`awsfirelens`, health check, dependsOn, ulimits, systemControls, capabilities, init process, restart policy…).

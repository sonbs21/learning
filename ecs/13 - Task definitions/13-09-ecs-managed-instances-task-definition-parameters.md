# Amazon ECS task definition parameters for Amazon ECS Managed Instances

> Nguồn: **Amazon ECS Developer Guide — Amazon ECS task definition parameters for Amazon ECS Managed Instances**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task_definition_parameters-managed-instances.html>
>
> **Cách đọc file này**
> - Trang gốc là **trang tham chiếu rất dài** (từng tham số một). File này **tóm lược** từng tham số thành bảng: kiểu dữ liệu, bắt buộc hay không, giá trị hợp lệ / mặc định / giới hạn, và ghi chú riêng cho Managed Instances. Không phải bản dịch từng chữ; khi cần câu chữ hoặc ví dụ code chính xác, đọc link gốc.
> - Khối **📘 Theo docs AWS**: nội dung lấy từ trang gốc, diễn đạt lại.
> - Khối **💡 Giải thích thêm**: phần giải thích hoặc đối chiếu với các file trước.
> - Ký hiệu: **⛔ = trang này ghi "không hỗ trợ trên Managed Instances"**.

---

## Mục lục

1. [Cấu trúc task definition](#1-cấu-trúc-task-definition)
2. [Tham số mức task](#2-tham-số-mức-task)
   - [2.1 Family, capacity, IAM role](#21-family-capacity-iam-role)
   - [2.2 Network mode](#22-network-mode)
   - [2.3 Runtime platform](#23-runtime-platform)
   - [2.4 Task size](#24-task-size)
   - [2.5 Tham số mức task khác](#25-tham-số-mức-task-khác)
   - [2.6 Volumes](#26-volumes)
3. [Container definitions — tham số cơ bản](#3-container-definitions--tham-số-cơ-bản)
   - [3.1 Name, image](#31-name-image)
   - [3.2 Memory, CPU](#32-memory-cpu)
   - [3.3 Port mappings](#33-port-mappings)
   - [3.4 Private repository, essential, entry point, command, working directory](#34-private-repository-essential-entry-point-command-working-directory)
4. [Container definitions — tham số nâng cao](#4-container-definitions--tham-số-nâng-cao)
   - [4.1 Restart policy](#41-restart-policy)
   - [4.2 Health check](#42-health-check)
   - [4.3 Environment](#43-environment)
   - [4.4 Security](#44-security)
   - [4.5 Network settings](#45-network-settings)
   - [4.6 Storage và logging](#46-storage-và-logging)
   - [4.7 Resource requirements](#47-resource-requirements)
   - [4.8 Timeout và dependency](#48-timeout-và-dependency)
   - [4.9 System controls, interactive, pseudo terminal](#49-system-controls-interactive-pseudo-terminal)
5. [Linux parameters](#5-linux-parameters)
6. [Chỗ không khớp giữa các trang docs](#6-chỗ-không-khớp-giữa-các-trang-docs)
7. [Tóm tắt](#7-tóm-tắt)

---

## 1. Cấu trúc task definition

**📘 Theo docs AWS**

Task definition gồm các phần: **family**, **IAM task role**, **network mode**, **container definitions**, **volumes** và **capacity**.
- **Bắt buộc**: `family` và container definitions.
- **Tuỳ chọn**: task role, network mode, volumes, capacity.

Các tham số này dùng được trong file JSON để cấu hình task definition.

---

## 2. Tham số mức task

### 2.1 Family, capacity, IAM role

**📘 Theo docs AWS**

| Tham số | Kiểu | Bắt buộc | Ghi chú |
|---|---|---|---|
| `family` | String | **Có** | Giống "tên" chung cho nhiều phiên bản. Revision đầu tiên của family là 1, các lần register sau tăng dần. |
| `requiresCompatibilities` | String array | Không | Giá trị hợp lệ: `MANAGED_INSTANCES`. ECS kiểm tra mọi tham số có đáp ứng yêu cầu của Managed Instances; không đáp ứng thì trả về client exception. (Xem *Amazon ECS launch types* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/launch_types.html>.) |
| `taskRoleArn` | String | Không | IAM role cho phép **container trong task** gọi AWS API theo policy của role. (Xem *Amazon ECS task IAM role* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task-iam-roles.html>.) |
| `executionRoleArn` | String | **Có điều kiện** | ARN của task execution role, cho **ECS container agent** gọi AWS API thay bạn. **Bắt buộc** khi pull image từ **ECR private** hoặc dùng log driver **`awslogs`**. (Xem *Amazon ECS task execution IAM role* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task_execution_IAM_role.html>.) |

**💡 Giải thích thêm**

Khớp với bảng IAM trong file *task networking for Fargate*: execution role dùng cho ECR, pull image, log driver; task role dùng cho chính ứng dụng.

### 2.2 Network mode

**📘 Theo docs AWS**

| Tham số | Kiểu | Bắt buộc | Mặc định | Giá trị hợp lệ |
|---|---|---|---|---|
| `networkMode` | String | Không | **`awsvpc`** | `awsvpc`, `host` |

- **`host`**: task bỏ qua cô lập mạng, container dùng thẳng network stack của host.

  > **Important (docs):** Với mode `host`, **không chạy container bằng user root (UID 0)**; luôn dùng user không phải root.

- **`awsvpc`**: task được cấp một ENI; phải chỉ định **`NetworkConfiguration`** khi tạo service hoặc chạy task. (Xem *Amazon ECS task networking for Amazon ECS Managed Instances* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/managed-instance-networking.html>.)
- `host` và `awsvpc` cho **hiệu năng mạng cao nhất** vì dùng network stack của EC2. Port container được map thẳng vào port của host (`host`) hoặc port của ENI (`awsvpc`), nên **không dùng được dynamic host port mapping**.

**💡 Giải thích thêm**

Có điểm khác với file *task networking for Managed Instances*: trang đó nói **bắt buộc khai báo** network mode, còn trang này ghi network mode **không bắt buộc, mặc định `awsvpc`**. Xem mục 6.

### 2.3 Runtime platform

**📘 Theo docs AWS**

| Tham số | Kiểu | Bắt buộc | Mặc định | Giá trị hợp lệ | Ghi chú |
|---|---|---|---|---|---|
| `operatingSystemFamily` | String | Không | `LINUX` | `LINUX` | Mọi task definition dùng trong một service phải cùng giá trị. Khi thuộc service, phải khớp `platformFamily` của service. |
| `cpuArchitecture` | String | **Có điều kiện** | — | `X86_64`, `ARM64` | Không khai báo thì ECS cố đặt task lên kiến trúc có sẵn theo cấu hình capacity provider. Muốn chắc chắn kiến trúc thì khai báo. Mọi task definition trong một service phải cùng giá trị. (Xem *64-bit ARM workloads* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/ecs-arm64.html>.) |

**💡 Giải thích thêm**

Image build cho ARM64 mà để trống `cpuArchitecture` có thể bị đặt lên instance x86 và lỗi khi chạy. Nếu image chỉ có một kiến trúc, nên khai báo rõ.

### 2.4 Task size

**📘 Theo docs AWS**

- CPU và memory tổng của task khai báo **riêng** với `cpu`/`memory` ở mức container. Với task chạy trên EC2 instance, các trường này là tuỳ chọn.

  > **Note (docs):** CPU/memory mức task **bị bỏ qua với Windows container**; khuyến nghị đặt ở mức container cho Windows.

| Tham số | Kiểu | Bắt buộc | Ghi chú |
|---|---|---|---|
| `cpu` | String | Có điều kiện | **Hard limit** CPU unit cho task. Ghi được dạng CPU unit (`1024`) hoặc vCPU (`1 vCPU`); khi register, vCPU được đổi sang CPU unit. Tuỳ chọn. Cluster không có instance đủ CPU thì task thất bại. Hỗ trợ từ **0,125** đến **10 vCPU**. |
| `memory` | String | Có điều kiện | **Hard limit** memory cho task. Ghi dạng MiB (`3072`) hoặc GB (`3 GB`); khi register, GB đổi sang MiB. Tuỳ chọn, giá trị bất kỳ. Có memory mức task thì memory mức container là tuỳ chọn. Cluster không có instance đủ memory thì task thất bại. (Xem *Reserving Amazon ECS Linux container instance memory* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/memory-management.html>.) |

**💡 Giải thích thêm**

Khác Fargate (bảng tổ hợp CPU–memory cố định): trên Managed Instances, CPU mức task nằm trong khoảng 0,125–10 vCPU, memory không bị ràng theo bảng.

### 2.5 Tham số mức task khác

**📘 Theo docs AWS**

Các tham số này dùng được khi register qua console với tuỳ chọn **Configure via JSON**. (Xem *Creating an Amazon ECS task definition using the console* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/create-task-definition.html>.)

| Tham số | Trạng thái | Nội dung chính |
|---|---|---|
| `ephemeralStorage` | ⛔ | Dùng để mở rộng ephemeral storage cho task trên **Fargate**. |
| `ipcMode` | (không ghi ⛔ trên trang này) | Giá trị `host`, `task`, `none`. `host`: chia sẻ IPC với EC2 host; `task`: chia sẻ trong task; `none`: riêng tư. Không khai báo thì phụ thuộc container runtime. |
| `pidMode` | (không ghi ⛔ trên trang này) | Giá trị `host`, `task`. `host`: chia sẻ process namespace với EC2 host; `task`: chia sẻ trong task. Mặc định là namespace riêng. Dùng `host` tăng nguy cơ lộ process namespace. |
| `proxyConfiguration` | ⛔ | Cấu hình proxy App Mesh. |
| Tags | Hỗ trợ | Xem bảng dưới. |
| `inferenceAccelerator` | ⛔ | Elastic Inference đã **không còn cung cấp** cho khách hàng. |
| `placementConstraints` | Hỗ trợ | Tối đa **10** constraint mỗi task (tính cả task definition và lúc chạy). Hỗ trợ `distinctInstance` và `memberOf`. Với `memberOf`, thuộc tính được hỗ trợ: `ecs.subnet-id`, `ecs.availability-zone`, `ecs.cpu-architecture`, `ecs.instance-type`. (Xem <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task-placement-constraints.html>.) |

**Quy tắc tag** (theo docs):

| Quy tắc | Giá trị |
|---|---|
| Số tag tối đa mỗi tài nguyên | 50 |
| Key | Duy nhất trong mỗi tài nguyên, mỗi key một value |
| Độ dài key tối đa | 128 ký tự Unicode (UTF-8) |
| Độ dài value tối đa | 256 ký tự Unicode (UTF-8) |
| Ký tự nên dùng | Chữ, số, khoảng trắng và `+ - = . _ : / @` |
| Phân biệt hoa thường | Có |
| Tiền tố `aws:` (mọi kiểu hoa thường) | Dành riêng cho AWS; không sửa/xoá được; không tính vào giới hạn 50 |

Mỗi tag có `key` (String, không bắt buộc) và `value` (String, không bắt buộc).

### 2.6 Volumes

**📘 Theo docs AWS**

(Xem *Storage options for Amazon ECS tasks* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/using_data_volumes.html>.)

| Tham số | Kiểu | Bắt buộc | Trạng thái / ghi chú |
|---|---|---|---|
| `name` | String | **Có** | Tối đa 255 ký tự (chữ hoa/thường, số, gạch nối). Được tham chiếu ở `sourceVolume` trong `mountPoints`. |
| `host` | HostVolumeProperties | Không | Dùng cho **bind mount host volume**. Để trống thì hệ thống tự gán đường dẫn trên host, dữ liệu **không đảm bảo** còn sau khi container dừng. |
| `host.sourcePath` | String | Không | Đường dẫn trên host đưa vào container. Trống → hệ thống tự gán. Có giá trị → dữ liệu **tồn tại** ở đó đến khi bạn xoá tay. Chưa tồn tại → tự tạo; đã tồn tại → nội dung được export. **Trên Managed Instances, một phần filesystem là chỉ đọc; `sourcePath` phải trỏ vào thư mục ghi được như `/var` hoặc `/tmp`.** |
| `dockerVolumeConfiguration` | Object | Không | ⛔ |
| `efsVolumeConfiguration` | Object | Không | Dùng khi lưu trữ bằng **Amazon EFS**. |
| `fsxWindowsFileServerVolumeConfiguration` | Object | Không | ⛔ |
| `configuredAtLaunch` | Boolean | Không | Volume được cấu hình lúc launch — dùng để tạo **EBS volume** cho standalone task hoặc task của service. Mỗi revision chỉ có **một** volume configured-at-launch. |

**💡 Giải thích thêm**

Trên Managed Instances, các lựa chọn lưu trữ thực tế: bind mount (`/var`, `/tmp`), EFS, EBS (qua `configuredAtLaunch`).

---

## 3. Container definitions — tham số cơ bản

**📘 Theo docs AWS**

Khi register, phải khai báo danh sách container definition (được chuyển cho Docker daemon trên container instance).

### 3.1 Name, image

| Tham số | Kiểu | Bắt buộc | Ghi chú |
|---|---|---|---|
| `name` | String | **Có** | Tối đa 255 ký tự (chữ hoa/thường, số, gạch nối, gạch dưới). |
| `image` | String | **Có** | Chuyển thẳng cho Docker daemon. Mặc định lấy từ Docker Hub; registry khác dùng `repository-url/image:tag` hoặc `repository-url/image@digest`. Tối đa 255 ký tự. |
| `versionConsistency` | String | Không | `enabled` (mặc định) / `disabled`. `enabled`: ECS **resolve tag thành digest**; `disabled`: dùng nguyên URI image trong container definition. (Xem *Container image resolution* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/deployment-type-ecs.html#deployment-container-image-stability>.) |

Các điểm về `image` theo docs:
- Task mới khởi động thì agent pull phiên bản mới nhất của image + tag; cập nhật image về sau **không lan** sang task đang chạy.
- Không có tag hoặc digest → agent dùng tag **`latest`**.
- Hỗ trợ image ở private registry. (Xem *Using non-AWS container images in Amazon ECS* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/private-auth.html>.)
- Image ECR: dạng đầy đủ `registry/repository:tag` hoặc `registry/repository@digest`.
- Docker Hub official: một tên (vd `ubuntu`); repository khác trên Docker Hub: thêm tên tổ chức; registry khác: thêm domain.

**💡 Giải thích thêm**

`versionConsistency` chính là cơ chế "resolve tag sang digest" mà mình nhắc ở file *Container image pull behavior*: bật mặc định, giúp các task trong một deployment chạy cùng digest dù tag bị ghi đè.

### 3.2 Memory, CPU

| Tham số | Kiểu | Bắt buộc | Ghi chú |
|---|---|---|---|
| `memory` | Integer (MiB) | Không | **Hard limit**: vượt là container **bị kill**. Tổng memory của container trong task phải thấp hơn `memory` mức task (nếu có). Ánh xạ `--memory`. |
| `memoryReservation` | Integer (MiB) | Không | **Soft limit**: khi bộ nhớ hệ thống bị tranh chấp, Docker cố giữ container ở mức này; container vẫn dùng thêm được tới hard limit hoặc tới hết memory của instance. Ánh xạ `--memory-reservation`. |
| `cpu` | Integer | Không | Số CPU unit đặt trước cho container. Ánh xạ `--cpu-shares`. Tổng CPU của container phải thấp hơn `cpu` mức task. |

Quy tắc memory theo docs:
- Không có memory mức task → phải khai báo số nguyên khác 0 cho `memory` hoặc `memoryReservation` (hoặc cả hai). Khai báo cả hai thì `memory` > `memoryReservation`.
- Có `memoryReservation` thì giá trị này bị trừ khỏi memory còn trống của instance; không thì dùng `memory`.
- Ví dụ docs: container thường dùng 128 MiB, thỉnh thoảng lên 256 MiB → `memoryReservation` 128, `memory` 300.
- Docker **20.10.0+** giữ tối thiểu **6 MiB** cho container; Docker **19.03.13-ce trở xuống** giữ tối thiểu **4 MiB** → đừng khai báo thấp hơn.

Quy tắc CPU theo docs:
- Số CPU unit của một instance type = số vCPU × 1024.
- Linux container chia CPU chưa cấp phát theo tỉ lệ đã cấp. Ví dụ docs: một container 512 unit trên instance một lõi, chạy một mình → dùng được hết 1024; chạy thêm bản sao → mỗi task được đảm bảo tối thiểu 512, có thể dùng thêm nếu bên kia rảnh, cả hai bận 100% thì mỗi bên 512.
- Linux kernel cho phép CPU share tối thiểu **2** (và tối đa 262144 — theo mục Environment). Giá trị dưới 2 (kể cả null): agent **≤ 1.1.0** → null/0 thành 1024 share, 1 thành 2 share; agent **≥ 1.2.0** → null, 0, 1 đều thành 2.
- Windows: CPU là **giới hạn tuyệt đối (quota)**; null/0 được hiểu là 1% của một CPU.

**💡 Giải thích thêm**

Liên hệ file *Best practices for task sizes*: `memoryReservation` = reservation (được đảm bảo, dùng để bin-pack), `memory` = limit (vượt là bị dừng). Ví dụ 128/300 của docs chính là mô hình "reservation + bursting".

Ghi chú: trang này nhắc tới Windows container ở phần CPU và mountPoints dù Managed Instances chỉ dùng `LINUX` — đó là đoạn mô tả chung của tham số.

### 3.3 Port mappings

**📘 Theo docs AWS**

`portMappings` (Object array, không bắt buộc): mở port mạng của container ra ngoài và dùng cho giao tiếp giữa container trong cùng task.
- Với `awsvpc`: **chỉ khai báo `containerPort`**; `hostPort` luôn bị bỏ qua.
- Với `host`: `hostPort` phải để trống hoặc **bằng** `containerPort`.
- Khi task `RUNNING`, port được gán hiển thị ở: console (**Network Bindings**), CLI (`networkBindings` trong `describe-tasks`), API (`DescribeTasks`), task metadata endpoint.

| Trường | Kiểu | Bắt buộc | Ghi chú |
|---|---|---|---|
| `appProtocol` | String | Không | Chỉ áp dụng cho **Service Connect**. `HTTP`, `HTTP2`, `GRPC`. Có đặt thì ECS thêm xử lý kết nối và telemetry theo giao thức; không đặt thì dùng TCP (không có telemetry riêng). (Xem *Service Connect* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/service-connect.html>.) |
| `containerPort` | Integer | **Có** (khi dùng `portMappings`) | Port trên container. Với `awsvpc`, đây là cách khai báo port mở. |
| `containerPortRange` | String | Không | Chỉ đặt được qua API `register-task-definition`. Quy tắc: bắt buộc `awsvpc`; agent ≥ **1.67.0** và `ecs-init` ≥ **1.67.0-1**; tối đa **100** dải mỗi container; không khai báo `hostPortRange` (với `awsvpc`, host port = container port — map tĩnh); giá trị 1–65535; mỗi port chỉ thuộc một mapping; không chồng lấn; port đầu < port cuối; nhiều port thì Docker khuyên tắt docker-proxy. Dải port **không có** trong task event gửi EventBridge. |
| `hostPortRange` | String | Không | Dải port trên host, do Docker gán và agent trả về. |
| `hostPort` | Integer | Không | Để trống hoặc bằng `containerPort`. Không dùng port trong dải ephemeral (mặc định đọc từ `/proc/sys/net/ipv4/ip_local_port_range`, không có thì `49153–65535`). Port mặc định đã giữ: `22`, `2375`, `2376`, `51678–51680`. Tối đa **100** port được giữ mỗi instance (port tự gán không tính). |
| `name` | String | Không (bắt buộc cho Service Connect và VPC Lattice) | Tên port mapping, dùng trong cấu hình Service Connect / VPC Lattice của service. |
| `protocol` | String | Không | `tcp` (mặc định) hoặc `udp`. Service Connect **chỉ hỗ trợ `tcp`**. |

**💡 Giải thích thêm**

Trường `name` cũng là thứ **ECS Gateway** dùng: `portName` của gateway connector phải khớp `name` của port mapping (file *Connect services to a gateway* và *Troubleshoot*).

### 3.4 Private repository, essential, entry point, command, working directory

**📘 Theo docs AWS**

| Tham số | Kiểu | Bắt buộc | Ghi chú |
|---|---|---|---|
| `repositoryCredentials` | Object | Không | Thông tin đăng nhập private registry. |
| `repositoryCredentials.credentialsParameter` | String | **Có** (khi dùng `repositoryCredentials`) | ARN của secret chứa thông tin đăng nhập. Qua API/CLI/SDK, nếu secret cùng Region thì dùng ARN hoặc tên đều được; qua console phải dùng **ARN đầy đủ**. |
| `essential` | Boolean | Không | `true`: container lỗi/dừng → **mọi container khác trong task dừng**. `false`: không ảnh hưởng container khác. Mặc định coi là essential. Mỗi task cần **ít nhất một** container essential. |
| `entryPoint` | String array | Không | Ánh xạ `--entrypoint`. Docs lưu ý agent đời cũ xử lý `entryPoint` chưa đúng → cập nhật agent hoặc chuyển sang `command`. |
| `command` | String array | Không | Ánh xạ `COMMAND` của `docker run`; mỗi đối số một phần tử. |
| `workingDirectory` | String | Không | Ánh xạ `--workdir`. |

---

## 4. Container definitions — tham số nâng cao

### 4.1 Restart policy

**📘 Theo docs AWS**

`restartPolicy`: ECS restart container mà **không cần thay task**. (Xem <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/container-restart-policy.html>.)

| Trường | Kiểu | Bắt buộc | Ghi chú |
|---|---|---|---|
| `enabled` | Boolean | **Có** | Bật/tắt restart policy. |
| `ignoredExitCodes` | Integer array | Không | Exit code ECS bỏ qua, không restart. Tối đa **50**. Mặc định không bỏ qua code nào. |
| `restartAttemptPeriod` | Integer (giây) | Không | Container phải chạy ít nhất bấy lâu mới được restart; mỗi khoảng này chỉ restart một lần; thoát sớm hơn thì không restart. Tối thiểu **60**, tối đa **1800**, mặc định **300**. |

### 4.2 Health check

**📘 Theo docs AWS**

`healthCheck`: lệnh kiểm tra sức khoẻ container và cấu hình. (Xem <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/healthcheck.html>.)

| Trường | Giá trị / mặc định | Ghi chú |
|---|---|---|
| `command` | String array | Bắt đầu bằng `CMD` (chạy trực tiếp) hoặc `CMD-SHELL` (chạy qua shell mặc định); không ghi thì là `CMD`. Exit code 0 và không có `stderr` = thành công; khác 0 = thất bại. Console: danh sách phân cách dấu phẩy; JSON/CLI/API: mảng trong ngoặc vuông. |
| `interval` | 5–300 giây, mặc định **30** | Khoảng cách giữa các lần kiểm tra. |
| `timeout` | 2–60 giây, mặc định **5** | Thời gian chờ một lần kiểm tra. |
| `retries` | 1–10, mặc định **3** | Số lần thử lại trước khi coi là unhealthy. |
| `startPeriod` | 0–300 giây, mặc định **tắt** | Thời gian ân hạn khi khởi động; lỗi trong thời gian này không tính vào `retries`. Thành công trong `startPeriod` thì từ đó các lỗi sau mới tính. |

**💡 Giải thích thêm**

Đây là health check **của container** (do ECS/Docker chạy bên trong), khác với health check **của target group ALB** trong file *Connect services to a gateway* (mặc định `/` mỗi 30 giây, timeout 5 giây). Có thể dùng cả hai. Health check container cũng là điều kiện cho `dependsOn` với `HEALTHY` (mục 4.8).

### 4.3 Environment

**📘 Theo docs AWS**

| Tham số | Kiểu | Ghi chú |
|---|---|---|
| `cpu` | Integer | (Nhắc lại) CPU unit đặt trước; tuỳ chọn trên Managed Instances; tổng phải thấp hơn `cpu` mức task. Kernel Linux cho phép share từ **2** đến **262144**. (Xem thêm blog *How Amazon ECS manages CPU and memory resources* — <https://aws.amazon.com/blogs/containers/how-amazon-ecs-manages-cpu-and-memory-resources/>.) |
| `gpu` | ResourceRequirement | Số GPU vật lý đặt cho container: số nguyên, số thập phân cho **GPU phân đoạn**, hoặc `ALL`. Giá trị thập phân (`0.125`, `0.25`, `0.5`) → task được đặt lên instance GPU phân đoạn **G6f**. Tổng GPU không vượt số GPU của instance. (Xem <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/ecs-gpu.html>.) |
| Neuron device | ResourceRequirement | Chỉ hỗ trợ `ALL` — container độc quyền mọi Neuron device trên instance. (Xem <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/ecs-inference.html>.) |
| Elastic Inference accelerator | ResourceRequirement | ⛔ |
| `essential`, `entryPoint`, `command`, `workingDirectory` | — | Nhắc lại như mục 3.4. |
| `environmentFiles` | Object array | Ánh xạ `--env-file`. Tối đa **10** file, đuôi **`.env`**, mỗi dòng `VARIABLE=VALUE`, dòng bắt đầu `#` là chú thích. Biến khai báo trực tiếp **ưu tiên** hơn biến trong file; nhiều file trùng biến thì xử lý từ trên xuống. Trường: `value` (ARN object S3, bắt buộc), `type` (chỉ `s3`, bắt buộc). |
| `environment` | Object array | Ánh xạ `--env`. Mỗi phần tử có `name`, `value`. **Không khuyến nghị** để thông tin nhạy cảm ở dạng plaintext. |
| `secrets` | Object array | Đưa secret vào container. `name` (tên biến môi trường), `valueFrom` (ARN đầy đủ của secret Secrets Manager hoặc parameter Parameter Store). Cùng Region thì dùng ARN hoặc tên; khác Region phải ARN đầy đủ. (Xem <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/specifying-sensitive-data.html>.) |

**💡 Giải thích thêm**

| Cần truyền | Dùng |
|---|---|
| Biến thường | `environment` |
| Nhiều biến từ file trên S3 | `environmentFiles` |
| Mật khẩu, token, key | `secrets` (cần execution role có quyền đọc secret — theo bảng IAM ở file Fargate networking) |

### 4.4 Security

**📘 Theo docs AWS**

| Tham số | Kiểu | Trạng thái / ghi chú |
|---|---|---|
| `privileged` | Boolean | `true` → container có quyền cao trên host (giống root). Ánh xạ `--privileged`. (Trang này không ghi ⛔.) |
| `user` | String | User trong container, ánh xạ `--user`. Định dạng: `user`, `user:group`, `uid`, `uid:gid`, `user:gid`, `uid:group`; UID/GID phải là số nguyên dương. **Important (docs):** với mode `host`, không chạy bằng root (UID 0). |
| `readonlyRootFilesystem` | Boolean | `true` → root filesystem chỉ đọc. Ánh xạ `--read-only`. |
| `dockerSecurityOptions` | String array | ⛔ (nhãn SELinux/AppArmor). |
| `ulimits` | Array of Ulimit | Ghi đè giá trị mặc định của Docker. Ánh xạ `--ulimit`. Cần Docker Remote API **≥ 1.18**. |
| `dockerLabels` | Map string→string | Gắn label cho container, ánh xạ `--label`. Cần Docker Remote API ≥ 1.18. |

**💡 Giải thích thêm**

Mode `host` + user root là tổ hợp docs cảnh báo **hai lần** (ở `networkMode` và `user`). Khi dùng `host`, nên đặt `user` không phải root.

### 4.5 Network settings

**📘 Theo docs AWS**

| Tham số | Trạng thái | Ghi chú |
|---|---|---|
| `disableNetworking` | ⛔ | Tắt mạng trong container (mặc định `false`). |
| `links` | ⛔ | Chỉ dùng với mode `bridge`. Docs lưu ý container cùng instance có thể giao tiếp mà không cần link; cô lập mạng do security group và VPC quyết định. |
| `hostname` | ⛔ | Hostname của container, ánh xạ `--hostname`. |
| `dnsServers` | ⛔ | Danh sách DNS server cho container. |
| `extraHosts` | Không hỗ trợ với **`awsvpc`** | Thêm dòng vào `/etc/hosts` (`hostname`, `ipAddress`, cả hai bắt buộc khi dùng). Ánh xạ `--add-host`. |

### 4.6 Storage và logging

**📘 Theo docs AWS**

**`readonlyRootFilesystem`** — như mục 4.4, mặc định `false`.

**`mountPoints`** (Object array) — điểm mount data volume, ánh xạ `--volume`. Phải khai báo mount point để gắn **EBS volume** trực tiếp vào task.

| Trường | Bắt buộc | Ghi chú |
|---|---|---|
| `sourceVolume` | Có | Tên volume (khớp `volumes[].name`). |
| `containerPath` | Có | Đường dẫn trong container. |
| `readOnly` | Không | Mặc định `false`. |

**`volumesFrom`** (Object array) — mount volume từ container khác, ánh xạ `--volumes-from`. Trường `sourceContainer` (bắt buộc), `readOnly` (mặc định `false`).

**`logConfiguration`** — cấu hình log, ánh xạ `--log-driver`. Mặc định container dùng log driver của Docker daemon; ECS hỗ trợ một **tập con** log driver; cần Docker Remote API ≥ 1.18.

| Trường | Ghi chú |
|---|---|
| `logDriver` | **Bắt buộc** khi dùng `logConfiguration`. Giá trị: `awslogs`, `splunk`, `awsfirelens`. Driver tuỳ chỉnh: có thể fork ECS agent trên GitHub, nhưng AWS không hỗ trợ chạy bản sửa. |
| `options` | Map tuỳ chọn gửi cho log driver (cần Docker Remote API ≥ 1.19). |
| `secretOptions` | Secret cho cấu hình log (token, cert, key…), mỗi phần tử có `name`, `valueFrom`. |

Option của `awslogs` theo docs:

| Option | Bắt buộc | Ghi chú |
|---|---|---|
| `awslogs-create-group` | Không | Tự tạo log group; mặc định `false`. Cần quyền `logs:CreateLogGroup`. |
| `awslogs-region` | **Có** | Region nhận log; log group phải tồn tại ở Region đó. |
| `awslogs-group` | **Có** | Log group nhận log stream. |
| `awslogs-stream-prefix` | **Có** | Log stream có dạng `prefix-name/container-name/ecs-task-id`. Không có prefix thì stream mang tên container ID của Docker — khó truy ngược, nên AWS khuyến nghị đặt prefix (có thể dùng tên service). **Phải có prefix** để log hiện ở pane Log trên console. |
| `awslogs-datetime-format` | Không | Mẫu bắt đầu log nhiều dòng theo `strftime` (vd gom stack trace thành một bản ghi). **Không** dùng cùng `awslogs-multiline-pattern`. Có thể ảnh hưởng hiệu năng log. |
| `awslogs-multiline-pattern` | Không | Mẫu bắt đầu log nhiều dòng bằng regex. Bị bỏ qua nếu đã có `awslogs-datetime-format`. Có thể ảnh hưởng hiệu năng log. |
| `mode` | Không | `non-blocking` hoặc `blocking`. `blocking`: luồng log tới CloudWatch gián đoạn thì ghi `stdout`/`stderr` bị **chặn** → ứng dụng có thể treo, health check lỗi. `non-blocking`: log vào buffer trong memory (`max-buffer-size`) → ứng dụng không treo, nhưng có thể **mất log**. AWS khuyến nghị `non-blocking` nếu ưu tiên tính sẵn sàng. |
| `max-buffer-size` | Không | Mặc định **`1m`**. Kích thước buffer cho `non-blocking`; đầy thì log mới bị mất. |

Option khác theo docs:
- **`splunk`**: cần `splunk-token` và `splunk-url`.
- **`awsfirelens`**: `log-driver-buffer-limit` giới hạn số event buffer trong memory trước khi gửi tới container log router (giảm mất log khi throughput cao — xem <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/firelens-docker-buffer-limit.html>). Các option khác tuỳ đích:
  - Data Firehose: `region`, `delivery_stream`;
  - Kinesis Data Streams: `region`, `stream`;
  - OpenSearch Service: `Name`, `Host`, `Port`, `Index`, `Type`, `Aws_auth`, `Aws_region`, `Suppress_Type_Name`, `tls`;
  - S3: `bucket`, `region`, `total_file_size`, `upload_timeout`, `use_put_object`.

**`firelensConfiguration`** — cấu hình log router cho container. (Xem <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/using_firelens.html>.)

| Trường | Bắt buộc | Ghi chú |
|---|---|---|
| `type` | Có | `fluentd` hoặc `fluentbit`. |
| `options` | Không | Ví dụ bật metadata ECS vào log (`enable-ecs-log-metadata`), chỉ định file cấu hình tuỳ chỉnh (`config-file-type`, `config-file-value`). (Xem <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/firelens-taskdef.html>.) |

**💡 Giải thích thêm**

Lựa chọn `mode` cho `awslogs` là đánh đổi rõ ràng: `blocking` = không mất log nhưng ứng dụng có thể treo; `non-blocking` = ứng dụng không treo nhưng có thể mất log khi buffer đầy.

### 4.7 Resource requirements

**📘 Theo docs AWS**

`resourceRequirements` (array): loại và lượng tài nguyên gán cho container. Hỗ trợ **GPU** và **Neuron device**.

| Trường | Ghi chú |
|---|---|
| `type` | `GPU` hoặc `NeuronDevice` (bắt buộc). |
| `value` | `GPU`: số nguyên (GPU nguyên), thập phân `0.125`/`0.25`/`0.5` (GPU phân đoạn trên G6f), hoặc `ALL`; tổng không vượt GPU của instance. `NeuronDevice`: chỉ `ALL`. Cả GPU và Neuron đều **không có trên Fargate**. |

**💡 Giải thích thêm**

Đây là điểm Managed Instances làm được mà Fargate không làm được: GPU (kể cả GPU phân đoạn) và Neuron.

### 4.8 Timeout và dependency

**📘 Theo docs AWS**

| Tham số | Ghi chú |
|---|---|
| `startTimeout` | Integer (giây). Thời gian chờ resolve dependency của container trước khi bỏ cuộc. Ví dụ docs: `containerA` phụ thuộc `containerB` đạt `COMPLETE`/`SUCCESS`/`HEALTHY`; `containerB` không đạt trong `startTimeout` thì `containerA` không khởi động. Tối đa **600** giây. |
| `stopTimeout` | Integer (giây). Thời gian chờ trước khi kill cưỡng bức container không tự thoát. Mặc định **30**, tối đa **86400** (24 giờ). |
| `dependsOn` | Array. Phụ thuộc khi khởi động và tắt (khi tắt thì đảo ngược). Cần platform version **1.3.0+** (Linux) hoặc **1.0.0** (Windows). Mỗi phần tử có `containerName` và `condition`. |

> **Note (docs):** Container không đáp ứng ràng buộc dependency hoặc hết thời gian trước khi đáp ứng thì ECS **không** đưa các container phụ thuộc sang trạng thái tiếp theo.

Các `condition`:

| Condition | Ý nghĩa | Đặt được trên container essential? |
|---|---|---|
| `START` | Container phụ thuộc đã **khởi động** | (docs không giới hạn) |
| `COMPLETE` | Container phụ thuộc đã **chạy xong (thoát)** — hữu ích cho script chạy rồi thoát | **Không** |
| `SUCCESS` | Như `COMPLETE` nhưng phải thoát với mã **0** | **Không** |
| `HEALTHY` | Container phụ thuộc **qua health check** (phải cấu hình health check); chỉ xác nhận lúc task khởi động | (docs không giới hạn) |

**💡 Giải thích thêm**

Mẫu dùng phổ biến: container "init" chạy migration (`essential: false`) → container ứng dụng `dependsOn` với `SUCCESS`. Hoặc sidecar log router phải `START`/`HEALTHY` trước ứng dụng.

### 4.9 System controls, interactive, pseudo terminal

**📘 Theo docs AWS**

**`systemControls`** — tham số kernel theo namespace cho container, ánh xạ `--sysctl` (vd `net.ipv4.tcp_keepalive_time` để giữ kết nối lâu hơn).
- **Không khuyến nghị** đặt `systemControls` liên quan mạng cho nhiều container trong cùng task dùng `awsvpc` hoặc `host`: đặt cho một container là áp cho cả task; đặt khác nhau thì container khởi động **sau cùng** quyết định giá trị.
- Với IPC mode: `host` → không hỗ trợ `systemControls` IPC; `task` → giá trị IPC áp cho mọi container trong task.
- `namespace` hợp lệ:
  - IPC: `kernel.msgmax`, `kernel.msgmnb`, `kernel.msgmni`, `kernel.sem`, `kernel.shmall`, `kernel.shmmax`, `kernel.shmmni`, `kernel.shm_rmid_forced`, và `fs.mqueue.*`;
  - Network: `net.*`.
- `value`: giá trị cho tham số kernel tương ứng.

| Tham số | Kiểu | Mặc định | Ghi chú |
|---|---|---|---|
| `interactive` | Boolean | `false` | `true` → cho ứng dụng cần `stdin` hoặc `tty`. Ánh xạ `--interactive`. |
| `pseudoTerminal` | Boolean | `false` | `true` → cấp TTY. Ánh xạ `--tty`. |

---

## 5. Linux parameters

**📘 Theo docs AWS**

`linuxParameters` (LinuxParameters, không bắt buộc): tuỳ chỉnh riêng Linux cho container, ví dụ kernel capabilities.

| Trường | Kiểu | Trạng thái | Ghi chú |
|---|---|---|---|
| `capabilities` | KernelCapabilities | Hỗ trợ | Thêm hoặc bỏ Linux capability so với mặc định của Docker. |
| `devices` | Array of Device | (không ghi ⛔) | Đưa thiết bị của host vào container, ánh xạ `--device`. |
| `initProcessEnabled` | Boolean | Hỗ trợ | Chạy tiến trình `init` trong container để chuyển tiếp signal và dọn tiến trình zombie, ánh xạ `--init`. |
| `maxSwap` | Integer | ⛔ | (Xem file *container swap*.) |
| `swappiness` | Integer | ⛔ | (Xem file *container swap*.) |
| `sharedMemorySize` | Integer (MiB) | ⛔ | Kích thước `/dev/shm`, ánh xạ `--shm-size`. |
| `tmpfs` | Array of Tmpfs | (không ghi ⛔) | Mount tmpfs, ánh xạ `--tmpfs`. |

**💡 Giải thích thêm**

`initProcessEnabled` hữu ích khi ứng dụng không tự xử lý signal (ví dụ `SIGTERM` khi ECS dừng task) — kết hợp với `stopTimeout` ở mục 4.8 để tắt êm.

---

## 6. Chỗ không khớp giữa các trang docs

**💡 Giải thích thêm**

Đối chiếu trang này với file *task definition differences for Managed Instances* và file *task networking for Managed Instances*. Mình liệt kê để bạn biết, không tự quyết bên nào đúng; khi cần chắc chắn, hãy thử register task definition với `requiresCompatibilities: ["MANAGED_INSTANCES"]` để ECS kiểm tra.

| Tham số | Trang *differences* | Trang này (*parameters*) |
|---|---|---|
| `ipcMode` | Không hợp lệ | Mô tả bình thường, không ghi ⛔ |
| `tmpfs` | Không hợp lệ | Mô tả bình thường, không ghi ⛔ |
| `extraHosts` | Không hợp lệ | Chỉ ghi "không hỗ trợ với `awsvpc`" |
| `hostname`, `dnsServers`, `links`, `disableNetworking`, `dockerSecurityOptions`, `maxSwap`, `swappiness`, `sharedMemorySize`, `ephemeralStorage`, `proxyConfiguration`, `inferenceAccelerator`, `dockerVolumeConfiguration`, `fsxWindowsFileServerVolumeConfiguration` | Không hợp lệ | ⛔ — **khớp** |
| `dnsSearchDomains` | Không hợp lệ | Không có mục riêng trên trang này |
| `placementConstraints` | 4 thuộc tính | 4 thuộc tính + nêu rõ constraint `distinctInstance` và `memberOf` — **khớp, chi tiết hơn** |
| `networkMode` | — | Không bắt buộc, mặc định `awsvpc` |
| Network mode (trang *networking*) | Bắt buộc khai báo | (như trên) — **không khớp** |
| `ulimits` — `nofile` | — | Trang này: soft mặc định `1024`, hard `65535` cho **Fargate**; trang *Fargate differences*: soft và hard đều `65535` — **không khớp** |

Ngoài ra, trang này có một số đoạn mô tả chung cho mọi môi trường (Windows, Fargate) dù đang là trang dành cho Managed Instances; mình giữ nguyên nhưng ghi chú ở chỗ liên quan.

---

## 7. Tóm tắt

- **Bắt buộc**: `family`, container definitions (mỗi container cần `name`, `image`; mỗi task cần ít nhất một container `essential`).
- **Mức task**: `requiresCompatibilities: MANAGED_INSTANCES`; `taskRoleArn` (cho ứng dụng); `executionRoleArn` (bắt buộc khi pull ECR private hoặc dùng `awslogs`); `networkMode` `awsvpc` (mặc định) hoặc `host`; `operatingSystemFamily` chỉ `LINUX`; `cpuArchitecture` `X86_64`/`ARM64`; `cpu` 0,125–10 vCPU; `memory` tuỳ ý.
- **Placement**: tối đa 10 constraint; `distinctInstance`, `memberOf` với subnet-id / availability-zone / cpu-architecture / instance-type.
- **Volume**: bind mount (`sourcePath` trong `/var`, `/tmp`), EFS, EBS (`configuredAtLaunch`, một volume mỗi revision); không Docker volume, không FSx Windows.
- **Image**: `versionConsistency` mặc định resolve tag → digest; không tag thì dùng `latest`.
- **Memory/CPU container**: `memory` = hard limit (vượt bị kill), `memoryReservation` = soft limit; tối thiểu 6 MiB (Docker 20.10+); CPU share 2–262144.
- **Port**: với `awsvpc` chỉ khai báo `containerPort`; `name` cần cho Service Connect / VPC Lattice (và ECS Gateway); `containerPortRange` tối đa 100 dải.
- **Nâng cao**: restart policy (60–1800 giây, mặc định 300); health check (interval 30, timeout 5, retries 3, startPeriod tắt); `environment` / `environmentFiles` (≤ 10 file `.env` trên S3) / `secrets`; GPU (cả phân đoạn trên G6f) và Neuron; `startTimeout` ≤ 600, `stopTimeout` mặc định 30 / tối đa 86400; `dependsOn` với `START` / `COMPLETE` / `SUCCESS` / `HEALTHY`.
- **Logging**: `awslogs` (cần region, group, stream-prefix; chọn `blocking` vs `non-blocking`), `splunk`, `awsfirelens`.
- **Không hỗ trợ (⛔ trên trang này)**: `ephemeralStorage`, `proxyConfiguration`, `inferenceAccelerator`, `dockerVolumeConfiguration`, `fsxWindowsFileServerVolumeConfiguration`, `dockerSecurityOptions`, `disableNetworking`, `links`, `hostname`, `dnsServers`, `maxSwap`, `swappiness`, `sharedMemorySize`, Elastic Inference; `extraHosts` không dùng với `awsvpc`.
- Có một số chỗ không khớp với các trang docs khác (mục 6).

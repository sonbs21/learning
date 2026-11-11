# Amazon ECS task definition differences for Fargate

> Nguồn: **Amazon ECS Developer Guide — Amazon ECS task definition differences for Fargate**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/fargate-tasks-services.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: diễn đạt lại bằng tiếng Việt, bám sát từng ý và đúng thứ tự của trang gốc, không thêm thông tin ngoài trang. Đây không phải bản dịch từng chữ; khi cần câu chữ hoặc ví dụ code chính xác, đọc link gốc ở trên.
> - Khối **💡 Giải thích thêm**: phần giải thích để dễ hiểu hơn, chỉ làm rõ những gì docs đã nói.
> - Tên tham số, giá trị và số liệu giữ nguyên như docs; đoạn code dùng placeholder (`<...>`).

---

## Mục lục

1. [Tổng quan](#1-tổng-quan)
2. [Tham số task definition](#2-tham-số-task-definition)
3. [Hệ điều hành và kiến trúc CPU](#3-hệ-điều-hành-và-kiến-trúc-cpu)
4. [CPU và memory của task](#4-cpu-và-memory-của-task)
5. [Networking của task](#5-networking-của-task)
6. [Giới hạn tài nguyên (ulimits)](#6-giới-hạn-tài-nguyên-ulimits)
7. [Logging](#7-logging)
8. [Lưu trữ của task](#8-lưu-trữ-của-task)
9. [Lazy loading image với Seekable OCI (SOCI)](#9-lazy-loading-image-với-seekable-oci-soci)
10. [So sánh với Managed Instances](#10-so-sánh-với-managed-instances)
11. [Tóm tắt](#11-tóm-tắt)

---

## 1. Tổng quan

**📘 Theo docs AWS**

Muốn dùng **Fargate**, bạn phải cấu hình task definition dùng **launch type Fargate**. Khi dùng Fargate có thêm một số điều cần cân nhắc (các mục bên dưới).

---

## 2. Tham số task definition

**📘 Theo docs AWS**

Task Fargate **không hỗ trợ tất cả** tham số task definition: một số hoàn toàn không hỗ trợ, một số hoạt động khác đi.

**Không hợp lệ trên Fargate:**

| | | |
|---|---|---|
| `disableNetworking` | `extraHosts` | `placementConstraints` |
| `dnsSearchDomains` | `gpu` | `privileged` |
| `dnsServers` | `ipcMode` | `maxSwap` |
| `dockerSecurityOptions` | `links` | `swappiness` |

**Hợp lệ nhưng có giới hạn:**

| Tham số | Giới hạn trên Fargate |
|---|---|
| `linuxParameters` | Với `capabilities`, capability **duy nhất** được thêm là **`CAP_SYS_PTRACE`**. Không hỗ trợ `devices`, `sharedMemorySize`, `tmpfs`. (Xem *Linux parameters* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task_definition_parameters.html#container_definition_linuxparameters>.) |
| `volumes` | Chỉ hỗ trợ **bind mount host volume**, nên **không hỗ trợ** `dockerVolumeConfiguration`. (Xem *Volumes* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task_definition_parameters.html#volumes>.) |
| `cpu` | Với **Windows container** trên Fargate, giá trị **không được nhỏ hơn 1 vCPU**. |
| `networkConfiguration` | Task Fargate **luôn** dùng network mode **`awsvpc`**. |

**Đảm bảo task definition được kiểm tra hợp lệ cho Fargate** khi register:
- Console: trường **Requires Compatibilities** → `FARGATE`.
- AWS CLI: tuỳ chọn **`--requires-compatibilities`**.
- ECS API: cờ **`requiresCompatibilities`**.

**💡 Giải thích thêm**

- `maxSwap` và `swappiness` không dùng được → khớp file *container swap* (swap chỉ cho EC2).
- `placementConstraints` không có, vì Fargate không cho bạn chọn host.
- `privileged` và `gpu` không có → workload cần quyền root trên host hoặc GPU thì phải dùng EC2 (hoặc Managed Instances cho trường hợp phù hợp).
- `CAP_SYS_PTRACE` thường dùng cho công cụ debug/profiling đọc tiến trình khác trong container — phần giải thích này là của mình.

---

## 3. Hệ điều hành và kiến trúc CPU

**📘 Theo docs AWS**

Khi cấu hình task và container definition cho Fargate, **phải chỉ định hệ điều hành** container chạy. Fargate hỗ trợ:

- **Amazon Linux 2**

  > **Note (docs):** Container Linux chỉ dùng **kernel** và **cấu hình kernel** của hệ điều hành host (ví dụ các điều khiển `sysctl`). Image Linux có thể dựng từ base image của **bất kỳ distro Linux nào**. Nếu **kiến trúc CPU khớp**, container từ bất kỳ image Linux nào cũng chạy được trên bất kỳ hệ điều hành nào.

- **Windows Server 2019 Full**
- **Windows Server 2019 Core**
- **Windows Server 2022 Full**
- **Windows Server 2022 Core**

Kiến trúc CPU:
- **Windows** trên Fargate: bắt buộc **X86_64**.
- **Linux** trên Fargate: **X86_64** hoặc **ARM64** (cho ứng dụng chạy ARM). (Xem *Amazon ECS task definitions for 64-bit ARM workloads* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/ecs-arm64.html>.)

**💡 Giải thích thêm**

Note về kernel nghĩa là: image của bạn dựng từ Ubuntu, Debian hay Alpine vẫn chạy bình thường trên Fargate, vì "Amazon Linux 2" ở đây là hệ điều hành của host cung cấp kernel. Điều kiện quan trọng là **kiến trúc**: image build cho ARM64 phải chạy trên task ARM64, image x86 chạy trên task X86_64.

---

## 4. CPU và memory của task

**📘 Theo docs AWS**

- Task definition cho Fargate **bắt buộc** khai báo CPU và memory ở **mức task**. Phần lớn trường hợp chỉ cần khai báo ở mức task là đủ.
- Memory có thể ghi dạng chuỗi theo **MiB** hoặc **GB** (ví dụ `3072` MiB hoặc `3 GB`).
- CPU có thể ghi dạng chuỗi theo **CPU unit** hoặc **vCPU** (ví dụ `1024` hoặc `1 vCPU`).

Các tổ hợp CPU–memory hợp lệ ở mức task:

| CPU | Memory | Hệ điều hành |
|---|---|---|
| 256 (0,25 vCPU) | 512 MiB, 1 GB, 2 GB | Linux |
| 512 (0,5 vCPU) | 1 GB, 2 GB, 3 GB, 4 GB | Linux |
| 1024 (1 vCPU) | 2 GB → 8 GB (2, 3, 4, 5, 6, 7, 8 GB) | Linux, Windows |
| 2048 (2 vCPU) | 4 GB → 16 GB, bước 1 GB | Linux, Windows |
| 4096 (4 vCPU) | 8 GB → 30 GB, bước 1 GB | Linux, Windows |
| 8192 (8 vCPU) — *cần Linux platform `1.4.0`+* | 16 GB → 60 GB, bước 4 GB | Linux |
| 16384 (16 vCPU) — *cần Linux platform `1.4.0`+* | 32 GB → 120 GB, bước 8 GB | Linux |
| 32768 (32 vCPU) — *cần Linux platform `1.4.0`+* | 60 GB, 120 GB, 244 GB | Linux |

**💡 Giải thích thêm**

- Khác với EC2 (bạn tự chọn instance và bin-pack), trên Fargate bạn **chọn kích thước task** từ bảng này và trả tiền theo đúng kích thước đó.
- Windows chỉ có từ **1 vCPU** đến **4 vCPU** — khớp với giới hạn `cpu` ≥ 1 vCPU ở mục 2.
- Liên hệ file *Best practices for task sizes*: load test để chọn tổ hợp; ví dụ `256` CPU là kích thước nhỏ nhất, scale mịn nhưng chậm theo kịp đợt tăng tải.

---

## 5. Networking của task

**📘 Theo docs AWS**

- Task Fargate **bắt buộc** dùng `awsvpc`, mỗi task có một ENI. Khi chạy task hoặc tạo service, phải chỉ định **một hoặc nhiều subnet** để gắn ENI và **một hoặc nhiều security group** áp lên ENI.
- **Public subnet**: cân nhắc có gán IP public cho ENI không. Để pull image, task ở public subnet cần **IP public** trên ENI, với route ra internet hoặc NAT gateway có thể định tuyến ra internet.
- **Private subnet**: cần **NAT gateway** trong subnet để định tuyến ra internet khi pull image.
- **Image trên ECR**: có thể cấu hình ECR dùng **interface VPC endpoint**; khi đó việc pull image dùng **IPv4 private** của task. (Xem *Amazon ECR interface VPC endpoints (AWS PrivateLink)* — <https://docs.aws.amazon.com/AmazonECR/latest/userguide/vpc-endpoints.html>.)

Docs đưa ví dụ phần `networkConfiguration` của một service Fargate, gồm `awsvpcConfiguration` với ba trường: `assignPublicIp`, `securityGroups`, `subnets`. Dạng tương tự:

```json
"networkConfiguration": {
  "awsvpcConfiguration": {
    "assignPublicIp": "ENABLED",
    "securityGroups": ["<SG_ID>"],
    "subnets": ["<SUBNET_ID>"]
  }
}
```

**💡 Giải thích thêm**

Phần này tóm lại nội dung của file *Amazon ECS task networking options for Fargate* (mục 1–2). Lưu ý `networkConfiguration` là cấu hình của **service/run-task**, không nằm trong task definition — task definition chỉ có `networkMode: awsvpc`. Đây là phần mình giải thích thêm dựa trên việc docs gọi đây là "networkConfiguration section for a Fargate service".

---

## 6. Giới hạn tài nguyên (ulimits)

**📘 Theo docs AWS**

- Task definition cho **Linux** trên Fargate hỗ trợ tham số **`ulimits`** để đặt giới hạn tài nguyên cho container.
- Task definition cho **Windows** trên Fargate **không hỗ trợ** `ulimits`.
- Task trên Fargate dùng giá trị giới hạn mặc định của hệ điều hành, **trừ** `nofile`:
  - `nofile` giới hạn **số file mở** mà container dùng được;
  - trên Fargate, mặc định **soft limit = 65535**, **hard limit = 65535**;
  - có thể đặt cả hai lên tối đa **1048576**.

Docs có ví dụ đặt `nofile` tuỳ chỉnh trong mảng `ulimits`, với ba trường `name`, `softLimit`, `hardLimit`. Dạng tương tự:

```json
"ulimits": [
  { "name": "nofile", "softLimit": <SOFT>, "hardLimit": <HARD> }
]
```

Các giới hạn tài nguyên khác có thể chỉnh: *Resource limits* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task_definition_parameters.html#container_definition_limits>.

**💡 Giải thích thêm**

`nofile` quan trọng với ứng dụng mở nhiều kết nối mạng hoặc file cùng lúc (mỗi socket tính là một file mở). Mặc định 65535 đã khá rộng; chỉ tăng (tối đa 1048576) khi ứng dụng thực sự cần, ví dụ proxy hay server có rất nhiều kết nối đồng thời.

Ghi chú: giá trị trong ví dụ gốc của docs (soft 2048, hard 8192) thấp hơn mặc định 65535 dù câu dẫn của docs nói là "đã nhân đôi". Mình không chép lại ví dụ đó để tránh gây hiểu nhầm; khi dùng hãy đặt giá trị phù hợp với nhu cầu và nằm trong giới hạn 1048576.

---

## 7. Logging

### 7.1. Event logging

**📘 Theo docs AWS**

ECS ghi các hành động nó thực hiện vào **EventBridge**. Dùng sự kiện ECS trên EventBridge để nhận thông báo gần thời gian thực về trạng thái cluster, service, task, và tự động hoá phản hồi. (Xem *Automate responses to Amazon ECS errors using EventBridge* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/cloudwatch_event_stream.html>.)

### 7.2. Task lifecycle logging

**📘 Theo docs AWS**

- Task trên Fargate công bố **timestamp** theo từng trạng thái trong vòng đời task.
- Xem timestamp trong chi tiết task trên console, hoặc khi describe task bằng CLI/SDK.
- Ví dụ dùng: đánh giá thời gian task **tải container image**, từ đó quyết định có nên **giảm kích thước image** hoặc dùng **Seekable OCI** (mục 9). (Xem *Best practices for Amazon ECS container images* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/container-considerations.html>.)

### 7.3. Application logging

**📘 Theo docs AWS**

- Fargate hỗ trợ ba log driver: **`awslogs`**, **`splunk`**, **`awsfirelens`**.
- `awslogs` gửi log tới **CloudWatch Logs**. Docs có ví dụ `logConfiguration` với `logDriver: awslogs` và các option `awslogs-group`, `awslogs-region`, `awslogs-stream-prefix`.

```json
"logConfiguration": {
  "logDriver": "awslogs",
  "options": {
    "awslogs-group": "<LOG_GROUP>",
    "awslogs-region": "<REGION>",
    "awslogs-stream-prefix": "<PREFIX>"
  }
}
```

Tài liệu liên quan:
- `awslogs`: *Send Amazon ECS logs to CloudWatch* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/using_awslogs.html>
- `awsfirelens`: *Send Amazon ECS logs to an AWS service or AWS Partner* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/using_firelens.html>
- `splunk`: *splunk log driver* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/example_task_definitions.html#example_task_definition-splunk>

**💡 Giải thích thêm**

| Loại log | Nguồn | Dùng để |
|---|---|---|
| Event | EventBridge | Biết task/service đổi trạng thái, tự động hoá phản ứng |
| Task lifecycle | Timestamp của task | Đo thời gian pull image, khởi động |
| Application | Log driver (`awslogs`, `splunk`, `awsfirelens`) | Log do ứng dụng ghi ra |

Liên hệ file *task networking for Fargate*: log qua log driver dùng **task execution role**, log qua FireLens dùng **task role**.

---

## 8. Lưu trữ của task

**📘 Theo docs AWS**

Task trên Fargate hỗ trợ các loại lưu trữ:

| Loại | Mô tả theo docs | Tài liệu |
|---|---|---|
| **Amazon EBS** | Block storage bền, hiệu năng cao, tiết kiệm chi phí cho workload container nặng dữ liệu | <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/ebs-volumes.html> |
| **Amazon EFS** | Lưu trữ **bền vững (persistent)** | <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/efs-volumes.html> |
| **Bind mount** | Lưu trữ **tạm thời (ephemeral)** | <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/bind-mounts.html> |

**💡 Giải thích thêm**

Khớp với mục 2: Fargate không hỗ trợ `dockerVolumeConfiguration` (Docker volume), nên lưu trữ chỉ qua ba cách trên. Bind mount là tạm thời — dữ liệu mất khi task dừng.

---

## 9. Lazy loading image với Seekable OCI (SOCI)

### 9.1. SOCI là gì

**📘 Theo docs AWS**

- Task Fargate dùng **Linux platform version `1.4.0`** có thể dùng **Seekable OCI (SOCI)** để khởi động task nhanh hơn.
- Với SOCI, container chỉ mất **vài giây** cho bước pull image rồi bắt đầu chạy; phần còn lại của image được tải **ở nền** trong lúc môi trường và ứng dụng khởi tạo. Cơ chế này gọi là **lazy loading**.
- Khi khởi động task, Fargate **tự phát hiện** image có SOCI index không; có thì khởi động container mà không chờ tải hết image.
- Container **không có** SOCI index thì image được **tải hết** trước khi container chạy — giống các platform version Fargate khác và giống ECS-optimized AMI trên EC2.
- SOCI là công nghệ **mã nguồn mở do AWS phát triển**. Nó tạo một **index (SOCI Index)** các file bên trong image có sẵn, cho phép lấy từng file riêng lẻ trước khi tải toàn bộ image.
- SOCI index phải được lưu như một **artifact trong cùng repository** với image.
- Chỉ dùng SOCI index từ **nguồn tin cậy**, vì index là nguồn có thẩm quyền về nội dung image. (Xem *Introducing Seekable OCI for lazy loading container images* — <https://aws.amazon.com/about-aws/whats-new/2022/09/introducing-seekable-oci-lazy-loading-container-images/>.)

**Phiên bản manifest:**
- Người dùng mới chỉ dùng được **SOCI index manifest v2**.
- Người đã dùng SOCI trên Fargate trước đây vẫn dùng được **manifest v1**, nhưng AWS **khuyến nghị mạnh** chuyển sang v2.
- Manifest v2 tạo **quan hệ tường minh** giữa image và SOCI index của nó để deploy nhất quán.

**💡 Giải thích thêm**

```
Không SOCI:  [==== tải toàn bộ image ====] → start container
Có SOCI:     [tải vài giây] → start container
                              └── phần còn lại của image tải ở nền khi cần
```

Liên hệ file *Container image pull behavior*: đó là cách tránh pull lại image trên **EC2** (dùng cache). Trên Fargate không có cache image giữa các task theo cách đó, nên SOCI là cách giảm thời gian chờ pull.

### 9.2. Những điều cần cân nhắc

**📘 Theo docs AWS**

- Chỉ task chạy **Linux platform version `1.4.0`** dùng được SOCI index; **Windows** container trên Fargate không hỗ trợ.
- Hỗ trợ cả **X86_64** và **ARM64**.
- Image phải nằm trong registry tương thích: **Amazon ECR private registry**.
- Chỉ hỗ trợ image nén **gzip** hoặc **không nén**; image nén **zstd** không hỗ trợ.
- Với manifest **v2**: tạo SOCI index manifest sẽ **sửa manifest của image** (thêm annotation cho SOCI index) → image có **digest mới**. Nội dung các layer filesystem **không đổi**.
- Với manifest **v2**: nếu image đã có trong repository, sau khi tạo SOCI index bạn cần **push lại** image. Push lại **không tăng chi phí lưu trữ** do nhân đôi layer; chỉ upload manifest mới.
- AWS khuyến nghị thử lazy loading với image **lớn hơn 250 MiB (đã nén)**; image nhỏ hơn ít khi thấy cải thiện.
- Lazy loading có thể đổi thời gian khởi động task, nên có thể phải chỉnh các timeout như **health check grace period** của Elastic Load Balancing.
- Muốn một image **không** bị lazy load, phải **push lại** image mà **không gắn** SOCI index.

**💡 Giải thích thêm**

Điểm cần lưu ý với pipeline CI/CD:
- Manifest v2 làm **đổi digest** của image. Nếu bạn ghim image theo digest (khuyến nghị ở file *image pull behavior*), digest cần lấy **sau** khi tạo SOCI index và push lại.
- Kiểm tra định dạng nén của image (gzip vs zstd) trước khi kỳ vọng SOCI hoạt động.

### 9.3. Tạo SOCI index

**📘 Theo docs AWS**

Để image được lazy load, cần tạo SOCI index (file metadata) và lưu trong repository cạnh image. Hai cách:

| Cách | Mô tả theo docs |
|---|---|
| **soci-snapshotter CLI** (mã nguồn mở) | Linh hoạt hơn trong việc tạo index, tích hợp được vào **pipeline CI/CD**. <https://github.com/awslabs/soci-snapshotter> |
| **CloudFormation AWS SOCI Index Builder** | Giải pháp **serverless**, tự tạo và push SOCI index khi image được push lên ECR. Cách tự động hoá để bắt đầu nhanh. <https://awslabs.github.io/cfn-ecr-aws-soci-index-builder/> |

> **Note (docs):** Để tạo SOCI index cho một image, image phải có trong **containerd image store** trên máy chạy `soci-snapshotter`. Image nằm trong **Docker image store** thì không tìm thấy.

### 9.4. Kiểm tra task có dùng lazy loading không

**📘 Theo docs AWS**

- Từ **bên trong task**, gọi **task metadata endpoint version 4**:
  - ở path mặc định của container đang gọi có trường **`Snapshotter`**;
  - ở path **`/task`** có trường `Snapshotter` cho từng container;
  - giá trị mặc định là **`overlayfs`**; nếu dùng SOCI thì là **`soci`**.
- Kiểm tra image có **SOCI Index Manifest v2** hay không: lấy **Image Index** từ ECR bằng AWS CLI (docs dùng `aws ecr batch-get-image`, lấy `imageManifest`, rồi lọc các manifest có `artifactType` của SOCI index v2).
- Kiểm tra image có **SOCI Index Manifest v1**: dùng **OCI Referrers API** của ECR với digest của image và `artifactType` của SOCI index v1.

Ví dụ dạng tương tự cho kiểm tra v2:

```bash
aws ecr batch-get-image \
  --repository-name <REPO> \
  --image-ids imageTag=<TAG> \
  --query 'images[0].imageManifest' --output text \
| jq -r '.manifests[] | select(.artifactType=="application/vnd.amazon.soci.index.v2+json")'
```

Lệnh cho v1 (dùng `curl` tới endpoint `referrers` của ECR với thông tin đăng nhập ECR) xem trực tiếp trên trang gốc.

**💡 Giải thích thêm**

| Muốn biết | Cách |
|---|---|
| Task đang chạy có lazy load không | Task metadata endpoint v4 → `Snapshotter` = `soci` hay `overlayfs` |
| Image có index v2 không | `aws ecr batch-get-image` + lọc `artifactType` v2 |
| Image có index v1 không | OCI Referrers API của ECR |

Kết hợp với mục 7.2: so timestamp vòng đời task trước và sau khi bật SOCI để đo mức cải thiện.

---

## 10. So sánh với Managed Instances

**💡 Giải thích thêm**

Đặt cạnh file *task definition differences for Managed Instances*, chỉ dựa trên hai trang docs:

| | Fargate | Managed Instances |
|---|---|---|
| `requiresCompatibilities` | `FARGATE` | `MANAGED_INSTANCES` |
| Network mode | Chỉ `awsvpc` | `awsvpc` hoặc `host` |
| `placementConstraints` | **Không hợp lệ** | Hợp lệ, chỉ 4 thuộc tính |
| `maxSwap` / `swappiness` | Không hợp lệ | Không hợp lệ |
| `privileged`, `gpu` | Không hợp lệ | (trang không liệt kê) |
| `dockerVolumeConfiguration` | Không hỗ trợ | Không hợp lệ |
| `sharedMemorySize`, `tmpfs` | Không hỗ trợ (trong `linuxParameters`) | Không hợp lệ |
| `ephemeralStorage` | (trang không liệt kê là không hợp lệ) | Không hợp lệ |
| Hệ điều hành | Linux + Windows Server 2019/2022 | Chỉ `LINUX` |
| CPU/memory mức task | Bắt buộc, theo bảng tổ hợp | (trang không nêu) |

---

## 11. Tóm tắt

- Dùng Fargate cần cấu hình task definition cho launch type Fargate; khai báo `FARGATE` khi register để được kiểm tra hợp lệ.
- **Không hợp lệ**: `disableNetworking`, `dnsSearchDomains`, `dnsServers`, `dockerSecurityOptions`, `extraHosts`, `gpu`, `ipcMode`, `links`, `placementConstraints`, `privileged`, `maxSwap`, `swappiness`.
- **Có giới hạn**: `linuxParameters` chỉ thêm được `CAP_SYS_PTRACE`, không `devices`/`sharedMemorySize`/`tmpfs`; volume chỉ bind mount (không `dockerVolumeConfiguration`); Windows `cpu` ≥ 1 vCPU; luôn `awsvpc`.
- **OS**: Amazon Linux 2 (image Linux bất kỳ distro, miễn đúng kiến trúc), Windows Server 2019/2022 Full/Core. Windows chỉ X86_64; Linux X86_64 hoặc ARM64.
- **CPU/memory** bắt buộc ở mức task, theo bảng tổ hợp từ 256 CPU / 512 MiB tới 32 vCPU / 244 GB; từ 8 vCPU trở lên cần Linux platform 1.4.0+.
- **Networking**: `awsvpc`, phải chỉ định subnet và SG; pull image cần IP public, NAT gateway hoặc interface endpoint của ECR.
- **ulimits**: Linux hỗ trợ, Windows không; `nofile` mặc định 65535/65535, tối đa 1048576.
- **Logging**: EventBridge, timestamp vòng đời task, log driver `awslogs` / `splunk` / `awsfirelens`.
- **Storage**: EBS, EFS (bền vững), bind mount (tạm thời).
- **SOCI**: lazy loading cho Linux platform 1.4.0, X86_64/ARM64, ECR private, gzip hoặc không nén; khuyến nghị cho image > 250 MiB nén; manifest v2 đổi digest và cần push lại; kiểm tra qua `Snapshotter` trong task metadata v4.

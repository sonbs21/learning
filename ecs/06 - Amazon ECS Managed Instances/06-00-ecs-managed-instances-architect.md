# Architect for Amazon ECS Managed Instances

> Nguồn: **Amazon ECS Developer Guide — Architect for Amazon ECS Managed Instances**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/ManagedInstances.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: diễn đạt lại bằng tiếng Việt, bám sát từng ý và đúng thứ tự của trang gốc, không thêm thông tin ngoài trang. Đây không phải bản dịch từng chữ; khi cần câu chữ, ví dụ JSON hoặc bảng số liệu chính xác, đọc link gốc ở trên.
> - Khối **💡 Giải thích thêm**: phần giải thích để dễ hiểu hơn, chỉ làm rõ những gì docs đã nói.
> - Ví dụ cấu hình được viết lại với placeholder (`<...>`); tên trường giữ đúng như docs.

---

## Mục lục

0. [Managed Instances là gì](#0-managed-instances-là-gì)
1. [Bắt đầu](#1-bắt-đầu)
2. [Capacity provider](#2-capacity-provider)
3. [Chọn và tối ưu instance](#3-chọn-và-tối-ưu-instance)
4. [Task definition](#4-task-definition)
5. [Hệ điều hành và kiến trúc CPU](#5-hệ-điều-hành-và-kiến-trúc-cpu)
6. [Tính năng chính](#6-tính-năng-chính)
7. [IAM role](#7-iam-role)
8. [Bảo mật và tuân thủ](#8-bảo-mật-và-tuân-thủ)
9. [Networking](#9-networking)
10. [Instance storage (EBS data volume)](#10-instance-storage-ebs-data-volume)
11. [Local storage (instance store)](#11-local-storage-instance-store)
12. [Load balancing cho service](#12-load-balancing-cho-service)
13. [Giám sát](#13-giám-sát)
14. [Giá và tối ưu chi phí](#14-giá-và-tối-ưu-chi-phí)
15. [Service quota](#15-service-quota)
16. [Cân nhắc khi migrate](#16-cân-nhắc-khi-migrate)
17. [Giới hạn](#17-giới-hạn)
18. [Organization controls (SCP, Allowed AMIs)](#18-organization-controls-scp-allowed-amis)
19. [So sánh EC2 – Managed Instances – Fargate](#19-so-sánh-ec2--managed-instances--fargate)
20. [Tóm tắt](#20-tóm-tắt)

---

## 0. Managed Instances là gì

**📘 Theo docs AWS**

- **Amazon ECS Managed Instances** là lựa chọn compute **được quản lý hoàn toàn**, cho phép chạy container trên **toàn bộ dải EC2 instance type**, trong khi **AWS lo hạ tầng**.
- Dùng được các năng lực compute cụ thể như **GPU**, **kiến trúc CPU** nhất định, **mạng hiệu năng cao**, **instance chuyên dụng**; AWS lo **cấp phát, scale, vá lỗi (patch), bảo trì**.
- Bạn đóng gói ứng dụng thành container và khai báo yêu cầu compute. AWS tự chọn instance **general-purpose tối ưu chi phí nhất** phù hợp, hoặc bạn tự khai báo thuộc tính mong muốn (instance type, hãng CPU, accelerator).
- Managed Instances quản lý toàn bộ hạ tầng — **scale, patch, tối ưu chi phí** — mà **không đánh đổi** khả năng dùng tính năng AWS và tích hợp EC2.
- Hỗ trợ **container Linux**, có tối ưu và cấu hình bảo mật riêng theo nền tảng.
- Mặc định, tối ưu tài nguyên bằng cách **đặt nhiều task nhỏ lên instance lớn**, giúp giảm chi phí và **khởi động task nhanh hơn**.

**💡 Giải thích thêm**

Vị trí của Managed Instances nằm giữa EC2 và Fargate:

```
EC2 tự quản lý  ←──────  Managed Instances  ──────→  Fargate
(tự lo AMI, patch,        (chọn được instance type,     (không chọn instance,
 scale, nhiều quyền)       AWS lo AMI/patch/scale)       mỗi task cô lập)
```

So sánh chi tiết ở mục 19.

---

## 1. Bắt đầu

**📘 Theo docs AWS**

1. Tạo các **IAM role** cần thiết và **bật Managed Instances** trong account.
2. Tạo **capacity provider**.
3. Chạy task hoặc service dùng capacity provider Managed Instances.

Hướng dẫn chi tiết:
- *Learn how to create a task for Amazon ECS Managed Instances* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/getting-started-managed-instances.html>
- *… with the AWS CLI* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/getting-started-managed-instances-cli.html>

---

## 2. Capacity provider

**📘 Theo docs AWS**

- Managed Instances dùng **capacity provider** để quản lý compute capacity. Khi tạo, dùng chọn instance **mặc định** hoặc khai báo yêu cầu tuỳ chỉnh bằng **`instanceRequirements`**.

| Loại | Ý nghĩa |
|---|---|
| **Default capacity provider** | Tự chọn instance general-purpose **tối ưu chi phí nhất** theo yêu cầu workload |
| **Custom capacity provider** | Khai báo thuộc tính instance (chọn theo thuộc tính): **số vCPU**, **memory**, **hãng CPU**, **loại accelerator**, **instance type cụ thể** |

- Một **capacity provider strategy** chỉ được chứa **một loại** capacity provider trong số: **Managed Instances**, **Auto Scaling group**, **Fargate/Fargate_SPOT**.

**💡 Giải thích thêm**

Hệ quả của quy tắc "một loại": **không** trộn được Managed Instances với Fargate Spot trong cùng strategy (như ví dụ base/weight ở file *Schedule your containers*, vốn trộn `FARGATE` và `FARGATE_SPOT` — hai cái đó cùng một loại).

---

## 3. Chọn và tối ưu instance

**📘 Theo docs AWS**

**Cách ECS chọn instance type:**

| Cách | Khi nào |
|---|---|
| **Automatic selection** | Dùng default capacity provider → ECS tự chọn instance general-purpose tối ưu chi phí, đáp ứng CPU và memory trong task definition |
| **Attribute-based selection** | Dùng custom capacity provider → ECS chọn trong mọi instance type khớp thuộc tính bạn khai báo |

**Cơ chế tối ưu tài nguyên và chi phí:**

| Cơ chế | Ý nghĩa |
|---|---|
| **Multi-task placement** | Mặc định đặt nhiều task nhỏ lên instance lớn để tận dụng tối đa và giảm chi phí |
| **Active workload consolidation** | ECS nhận ra khi instance **thật sự rảnh**, nhưng cố tránh dừng quá sớm làm ảnh hưởng tính sẵn sàng hay hiệu năng deploy. Hệ thống tôn trọng **min/max số task** của service, hành vi **start before stop**, và **task protection** |
| **Right-sizing** | Khi nhu cầu thay đổi, ECS launch instance thay thế có **kích thước phù hợp** hơn |

- ECS dùng **EC2 event windows** để lên lịch bảo trì vào khoảng thời gian bạn muốn — định nghĩa khung giờ định kỳ cho phép AWS bảo trì instance, giảm gián đoạn. (Xem *Scheduled events for your instances* — <https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/event-windows.html>.)
- Cần **cô lập mạnh**: cấu hình Managed Instances chạy **mỗi task trên một instance riêng**, với ranh giới cô lập bảo mật **mức VM**.

**💡 Giải thích thêm**

Liên hệ file *Managed Instances auto scaling and task placement*: multi-task placement chính là bin packing; consolidation và right-sizing là phần "auto scaling" tự động. Với người ở Việt Nam, đặt event window vào **đêm giờ Việt Nam** giúp bảo trì ít ảnh hưởng người dùng — gợi ý của mình.

---

## 4. Task definition

**📘 Theo docs AWS**

- Task trên Managed Instances hỗ trợ **phần lớn** tham số task definition.
- **Tương thích** với task definition Fargate hiện có dùng **platform version 1.4.0** → migrate dễ.
- Đặt `requiresCompatibilities` có **`MANAGED_INSTANCES`**. Một task definition có thể khai báo **cả** `FARGATE` và `MANAGED_INSTANCES` để linh hoạt khi deploy.

**💡 Giải thích thêm**

```json
"requiresCompatibilities": ["FARGATE", "MANAGED_INSTANCES"]
```

Khi khai báo cả hai, task definition phải hợp lệ với **cả hai** — tức là tránh các tham số mà một trong hai môi trường không hỗ trợ (xem file *task definition differences* cho Fargate và Managed Instances). Đây là suy luận từ cơ chế kiểm tra `requiresCompatibilities`.

---

## 5. Hệ điều hành và kiến trúc CPU

**📘 Theo docs AWS**

- Hệ điều hành hỗ trợ: **Bottlerocket**.
- Hai kiến trúc cho task definition: **ARM** và **X86_64**.
- Container Linux trên Managed Instances dùng **X86_64**, hoặc **ARM64** cho ứng dụng chạy ARM.

**💡 Giải thích thêm**

Bottlerocket là hệ điều hành tối giản dành cho container của AWS — điều này giải thích các đặc điểm ở mục 8 (root filesystem bất biến, không SSH) và việc một phần filesystem **chỉ đọc** (file *Managed Instances task definition differences*: bind mount phải ở `/var`, `/tmp`). Phần nối ý này là của mình.

---

## 6. Tính năng chính

**📘 Theo docs AWS**

| Tính năng | Nội dung |
|---|---|
| **Chọn instance type cụ thể** | Truy cập phần cứng đặc biệt: GPU, năng lực CPU riêng, memory lớn |
| **Nhiều task trên một instance** | Mặc định, khác Fargate (mỗi task môi trường cô lập riêng) → tối ưu tài nguyên và chi phí |
| **Tuân thủ bảo mật, vá định kỳ** | ECS **bắt đầu drain instance sau 14 ngày** và tự chuyển task của service sang instance mới |
| **Capability đặc quyền** | Cho phép chức năng mạng và quản trị hệ thống nâng cao trong container: **`CAP_NET_ADMIN`**, **`CAP_SYS_ADMIN`**, **`CAP_BPF`**, **`CAP_PERFMON`** |

---

## 7. IAM role

**📘 Theo docs AWS**

Managed Instances cần **hai** IAM role:

| Role | Vai trò |
|---|---|
| **Infrastructure role** | Cho AWS quản lý Managed Instances thay bạn |
| **Instance profile** | Cách truyền IAM role cho Managed Instances, dùng để: định nghĩa quyền IAM cho instance chạy container; cho AWS quản lý instance thay bạn; cho instance truy cập dịch vụ AWS theo quyền trong profile |

**💡 Giải thích thêm**

Ngoài hai role này, task vẫn dùng **task role** và **task execution role** như bình thường (các file *task definition parameters*). Tổng cộng:

| Role | Ai dùng |
|---|---|
| Infrastructure role | ECS — quản lý instance |
| Instance profile | EC2 instance |
| Task execution role | ECS agent — pull image, log, secret |
| Task role | Code ứng dụng trong container |

---

## 8. Bảo mật và tuân thủ

**📘 Theo docs AWS**

| Lớp bảo mật | Nội dung |
|---|---|
| **Cấu hình an toàn** | Theo best practice của AWS: **không SSH**, **root filesystem bất biến**, kiểm soát truy cập bắt buộc mức kernel qua **SELinux** |
| **Tự động vá** | AWS cập nhật bản vá bảo mật thường xuyên, **tôn trọng maintenance window** bạn cấu hình |
| **Giới hạn vòng đời instance** | ECS tự drain instance **sau 14 ngày** → ứng dụng luôn chạy trên instance cấu hình đúng và có bản vá mới |
| **Capability đặc quyền** | Tuỳ chọn bật cho workload cần (vd giám sát mạng, observability) |

- Hỗ trợ cùng các chương trình tuân thủ như ECS: **PCI-DSS**, **HIPAA**, **FedRAMP**.
- Ở Region hỗ trợ, cấu hình được **FIPS endpoint** ở **mức capacity provider** để hỗ trợ đạt FedRAMP.

---

## 9. Networking

**📘 Theo docs AWS**

- Hỗ trợ mode **`awsvpc`** và **`host`**.
- `awsvpc`: mỗi task có **ENI** và **IP private riêng** trong VPC → kiểm soát security group và network ACL **ở mức task**.
- `host`: task dùng chung network namespace của instance.
- Chi tiết: *Amazon ECS task networking for Amazon ECS Managed Instances* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/managed-instance-networking.html>.

---

## 10. Instance storage (EBS data volume)

**📘 Theo docs AWS**

- Cấu hình được **kích thước EBS data volume** gắn vào instance.
- Volume này **dùng chung** cho mọi task trên instance, dùng cho **bind mount**; chia sẻ và mount giữa các container qua `volumes`, `mountPoint`, `volumesFrom`.
- Volume được gắn **khi tạo instance**; khai báo kích thước (GiB) qua tham số **`storageConfiguration`** khi tạo capacity provider (trong `managedInstancesProvider.instanceLaunchTemplate`, cùng với `infrastructureRoleArn`, `ec2InstanceProfileArn`, `networkConfiguration`).

| Thuộc tính | Giá trị |
|---|---|
| Tối thiểu | **30 GiB** |
| Tối đa | **16.384 GiB** |
| Mặc định | **80 GiB** |

- Image container đã pull — cả dạng **nén** và **giải nén** — được lưu trên volume này. Dung lượng thực sự dùng được cho bind mount = **tổng dung lượng** − **dung lượng image**.
- Hiệu năng EBS tương ứng hiệu năng của EC2 instance type đó. (Xem *Amazon EBS-optimized instances* — <https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/ebs-optimized.html>.)
- Có thể tạo **snapshot** volume để phân tích pháp chứng sự cố bảo mật hoặc debug. (Xem *Amazon EBS snapshots* — <https://docs.aws.amazon.com/ebs/latest/userguide/ebs-snapshots.html>.)
- Bật **EBS encryption by default** thì volume được mã hoá bằng KMS key trong cài đặt đó. (Xem <https://docs.aws.amazon.com/ebs/latest/userguide/encryption-by-default.html>.)
- Ngoài volume của instance, có thể cấu hình **data volume cho từng task**. (Xem *Storage options for Amazon ECS tasks* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/using_data_volumes.html>.)

```json
"managedInstancesProvider": {
  "infrastructureRoleArn": "<INFRA_ROLE_ARN>",
  "instanceLaunchTemplate": {
    "ec2InstanceProfileArn": "<INSTANCE_PROFILE_ARN>",
    "networkConfiguration": { "subnets": ["<SUBNET_1>", "<SUBNET_2>"], "securityGroups": ["<SG_ID>"] },
    "storageConfiguration": { "storageSizeGiB": 100 }
  }
}
```

**💡 Giải thích thêm**

- Trên trang gốc, ví dụ đầu viết tên trường là `storageSizeinGiB` (chữ "in" thường), ví dụ ở mục local storage lại viết `storageSizeGiB`. Mình dùng `storageSizeGiB` theo phần mô tả chữ; nên kiểm tra API reference.
- Image lớn chiếm cả bản nén lẫn giải nén → với nhiều task dùng image to, 80 GiB mặc định có thể không đủ. Liên hệ file *Container image pull behavior*: image được cache trên volume này.

---

## 11. Local storage (instance store)

**📘 Theo docs AWS**

- **EC2 instance store** là block storage **tạm thời**, trên đĩa **gắn vật lý** với host. (Xem <https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/InstanceStorage.html>.)
- Có thể cấu hình Managed Instances dùng **instance store làm data volume** cho task. Khi bật local storage và instance **có** instance store, ECS dùng instance store **thay vì** cấp EBS data volume → **giảm chi phí lưu trữ**, **tăng hiệu năng I/O** cho workload nhạy độ trễ. **Không tính thêm phí** khi dùng instance store với Managed Instances.
- Instance có **nhiều** instance store volume → ECS tự gộp thành **một volume RAID 0** liên tục.
- Instance **không có** instance store, hoặc **tắt** local storage → ECS cấp EBS data volume theo `storageSizeGiB`.
- Instance store là **tạm thời**: dữ liệu **mất** khi instance dừng, bị terminate, hoặc phần cứng hỏng. **Không** dùng cho dữ liệu cần giữ lâu.

**Bật local storage:** tạo capacity provider **mới** với `localStorageConfiguration.useLocalStorage = true`. Có thể dùng `instanceRequirements` để đảm bảo instance có local storage đủ kích thước. (Xem *create a new capacity provider* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/create-capacity-provider-managed-instances.html>.)

> **Note (docs):** Tạo cluster bằng console thì capacity provider Managed Instances **mặc định** do ECS tạo **không** có `localStorageConfiguration`.

Ví dụ docs: bật local storage, yêu cầu instance store **SSD** tối thiểu **50 GiB** — các trường liên quan:

| Trường | Giá trị trong ví dụ | Ý nghĩa |
|---|---|---|
| `storageConfiguration.storageSizeGiB` | 50 | Kích thước EBS **dự phòng** khi không có instance store |
| `localStorageConfiguration.useLocalStorage` | `true` | Bật dùng instance store |
| `instanceRequirements.localStorage` | `REQUIRED` | Chỉ chọn instance **có** instance store |
| `instanceRequirements.totalLocalStorageGB.min` | 50 | Dung lượng instance store tối thiểu |
| `instanceRequirements.localStorageTypes` | `["ssd"]` | Loại instance store |
| `instanceRequirements.vCpuCount.min`, `memoryMiB.min` | 0 | (không ràng buộc) |

**Quy tắc mặc định:**
- Bỏ `instanceRequirements` → `localStorage` mặc định **`INCLUDED`**: ECS xét **cả** instance có và không có instance store. Có thì dùng instance store, không thì dùng EBS.
- Không khai báo `totalLocalStorageGB` → ECS dùng **toàn bộ** instance store có sẵn, **không** ép kích thước tối thiểu.
- `storageSizeGiB` chỉ định kích thước **EBS** (dùng khi không có instance store); `totalLocalStorageGB` định **tối thiểu instance store**.
- Muốn **mọi** instance đều dùng local storage: `localStorage = REQUIRED` + `totalLocalStorageGB` tối thiểu.

**Kiểm tra** một instance có dùng local storage: xem thuộc tính **`ecs.capability.storage.local-storage-enabled`** trên container instance.

**💡 Giải thích thêm**

| Muốn | Cấu hình |
|---|---|
| Luôn dùng EBS | Không bật `useLocalStorage` (mặc định) |
| Dùng instance store khi có, không có thì EBS | `useLocalStorage: true`, bỏ `instanceRequirements` (→ `INCLUDED`) |
| **Bắt buộc** instance store ≥ N GiB | `useLocalStorage: true` + `localStorage: REQUIRED` + `totalLocalStorageGB.min: N` |

Thuộc tính `ecs.capability.storage.local-storage-enabled` cũng dùng được trong placement constraint nếu cần (file *Define which container instances…* — thuộc tính tuỳ chọn); tuy nhiên Managed Instances chỉ hỗ trợ 4 thuộc tính trong `memberOf`, nên có thể không dùng được trong constraint — suy luận, cần thử.

---

## 12. Load balancing cho service

**📘 Theo docs AWS**

- Service trên Managed Instances dùng được **Elastic Load Balancing** để phân phối traffic đều cho task.
- Hỗ trợ **Application Load Balancer** (HTTP/HTTPS — tầng 7), **Network Load Balancer** (TCP/UDP — tầng 4), và **Gateway Load Balancer**.
- Khi tạo target group, phải chọn target type **`ip`**, không phải `instance`, vì task mode `awsvpc` gắn với ENI chứ không gắn trực tiếp với EC2 instance.

**💡 Giải thích thêm**

Ngoài ELB tự cấu hình, có thể dùng **ECS Gateway** (file *Amazon ECS Gateway*) — hỗ trợ Managed Instances với `awsvpc`.

---

## 13. Giám sát

**📘 Theo docs AWS**

| Công cụ | Nội dung |
|---|---|
| **CloudWatch metrics** | CPU, memory, network, storage ở **mức task và mức instance** |
| **Container Insights** | Metric hiệu năng và log chi tiết cho ứng dụng container |
| **Tích hợp bên thứ ba** | Có capability đặc quyền thì chạy được giải pháp giám sát/observability cần quyền Linux cao |

**💡 Giải thích thêm**

Agent giám sát bên thứ ba thường triển khai bằng **Managed Daemons** (các file *Managed Daemons*) với capability như `BPF`, `PERFMON`.

---

## 14. Giá và tối ưu chi phí

**📘 Theo docs AWS**

- Tính tiền **toàn bộ EC2 instance** chạy task; giá phụ thuộc instance type được chọn.
- Tối ưu: **multi-task** — chạy nhiều task trên instance kích thước phù hợp để tận dụng tối đa.
- **Compute Savings Plans** và **Instance Savings Plans** áp dụng cho Managed Instances.

**💡 Giải thích thêm**

Khác Fargate (tính theo vCPU/memory của từng task): ở đây trả theo **instance**, nên mức lấp đầy instance quyết định chi phí — liên hệ cấu hình subnet và bin packing ở file *Managed Instances auto scaling and task placement*.

---

## 15. Service quota

**📘 Theo docs AWS**

- Workload Managed Instances chịu **quota On-Demand instance của EC2**.
- Service ECS dùng Managed Instances chịu **quota của ECS**.
- (Xem *Amazon EC2 endpoints and quotas* — <https://docs.aws.amazon.com/general/latest/gr/ec2-service.html> và *Amazon ECS service quotas* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/service-quotas.html>.)

---

## 16. Cân nhắc khi migrate

**📘 Theo docs AWS**

| Từ | Cách migrate |
|---|---|
| **Fargate** | Chỉ cần **đổi cấu hình capacity provider** và **deploy lại**. Task definition dùng **platform version 1.4.0** tương thích hoàn toàn |
| **EC2** | Tương tự migrate sang Fargate, nhưng **vẫn giữ** khả năng dùng tính năng EC2 như chọn instance type cụ thể |

Khi lên kế hoạch:
- Ứng dụng phải chịu được **vòng đời instance 14 ngày** và **cửa sổ bảo trì** định trước.
- **Task chạy liên tục quá 14 ngày** **không phù hợp** với Managed Instances.
- **Không hỗ trợ custom AMI** — Managed Instances dùng AMI do AWS quản lý, tối ưu bảo mật.

**💡 Giải thích thêm**

Từ EC2 sang, cần kiểm tra thêm các tham số không hợp lệ trên Managed Instances (file *Managed Instances task definition differences*): mode `bridge`, `links`, swap, `dockerVolumeConfiguration`… và thay thế user data / cấu hình agent tự viết (vì không có custom AMI).

---

## 17. Giới hạn

**📘 Theo docs AWS**

| Giới hạn | Nội dung |
|---|---|
| **Custom AMI** | AMI do AWS sở hữu và quản lý |
| **Vòng đời instance** | Tối đa **14 ngày** mỗi instance để đảm bảo vá bảo mật và tuân thủ |
| **SSH** | **Không có** vì lý do bảo mật. Debug bằng **ECS Exec**; thao tác quản lý chỉ qua **ECS API** |

---

## 18. Organization controls (SCP, Allowed AMIs)

**📘 Theo docs AWS**

- Một số kiểm soát cấp tổ chức có thể làm Managed Instances **không hoạt động đúng**; khi đó phải sửa để ECS có quyền quản lý EC2 thay bạn.
- ECS dùng **infrastructure role** để launch EC2 instance cho Managed Instances. **SCP luôn áp dụng** cho hành động thực hiện bằng infrastructure role → SCP có thể **chặn** thao tác của Managed Instances.
- Trường hợp hay gặp nhất: SCP **giới hạn AMI** được launch. Cần sửa SCP để **cho phép AMI từ các account AMI của Managed Instances**.
- Dùng tính năng **EC2 Allowed AMIs** để giới hạn AMI từ account khác thì phải **mở rộng tiêu chí** để gồm cả account AMI của Managed Instances ở các Region liên quan. (Xem <https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/ec2-allowed-amis.html>.)

**Ví dụ SCP của docs:** một statement **Deny** hành động `ec2:RunInstances` trên tài nguyên AMI, với điều kiện `aws:ResourceAccount` **không thuộc** danh sách account AMI của Managed Instances.

> **Note (docs):** **Không** dùng context key **`ec2:Owner`**. Amazon sở hữu các account AMI của Managed Instances nên giá trị key này luôn là `amazon` — SCP cho phép khi `ec2:Owner = amazon` sẽ cho launch **mọi AMI do Amazon sở hữu**, không chỉ AMI của Managed Instances.

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

**Account AMI của Managed Instances theo Region:** docs có bảng đầy đủ (33 Region). Một số Region thường dùng:

| Region | Account |
|---|---|
| ap-southeast-1 (Singapore) | 832199679391 |
| ap-northeast-1 (Tokyo) | 679336465495 |
| us-east-1 | 260073348889 |
| us-west-2 | 491085424538 |

Bảng đầy đủ xem tại link gốc (mục *Amazon ECS Managed Instances AMI accounts*).

**💡 Giải thích thêm**

Trang gốc nói ví dụ SCP dành cho **us-west-2 hoặc us-east-1**, nhưng một trong hai account ID trong ví dụ (`187296253231`) theo bảng của chính trang lại là của **us-west-1**; account của us-west-2 là `491085424538`. Khi viết SCP thật, hãy **lấy account ID từ bảng** theo đúng Region bạn dùng.

---

## 19. So sánh EC2 – Managed Instances – Fargate

**💡 Giải thích thêm**

Ghép từ trang này và các file trước:

| | EC2 tự quản lý | Managed Instances | Fargate |
|---|---|---|---|
| Chọn instance type | Có | Có (default hoặc theo thuộc tính) | Không |
| AMI | Tự chọn / custom | AWS quản lý (Bottlerocket), không custom | AWS quản lý |
| Patch, scale instance | Bạn (hoặc ASG) | AWS | AWS |
| Vòng đời instance | Không giới hạn | **Tối đa 14 ngày** | — |
| SSH | Có | **Không** (dùng ECS Exec) | Không |
| Nhiều task / instance | Có | Có (mặc định) | Không (mỗi task cô lập) |
| GPU | Có | Có | Không |
| Network mode | `awsvpc`, `bridge`, `host`, `none` | `awsvpc`, `host` | `awsvpc` |
| Placement strategy | Có | Không (điều khiển qua subnet) | Không |
| Capability đặc quyền | Có | Có (`NET_ADMIN`, `SYS_ADMIN`, `BPF`, `PERFMON`) | Chỉ thêm `SYS_PTRACE` |
| Daemon | Strategy `DAEMON` của service | **Managed Daemons** | Không |
| Tính tiền | Theo instance | Theo instance | Theo vCPU/memory task |
| Savings Plans | Có | Compute & Instance SP | Compute SP |

Dòng cuối (Savings Plans cho EC2 và Fargate) là kiến thức chung, không có trên trang này.

---

## 20. Tóm tắt

- **Managed Instances**: compute được quản lý hoàn toàn, chọn được mọi EC2 instance type (GPU, ARM, mạng cao…), AWS lo cấp phát, scale, patch, bảo trì; chỉ Linux (**Bottlerocket**), X86_64 hoặc ARM64.
- **Capacity provider**: default (tự chọn rẻ nhất) hoặc custom (`instanceRequirements`). Strategy chỉ chứa **một loại** provider.
- **Tối ưu**: nhiều task/instance, consolidation (tôn trọng min/max task, start-before-stop, task protection), right-sizing; bảo trì theo **EC2 event windows**; tuỳ chọn mỗi task một instance để cô lập.
- **Task definition**: `requiresCompatibilities` có `MANAGED_INSTANCES`; tương thích Fargate platform 1.4.0; có thể khai báo cả `FARGATE` và `MANAGED_INSTANCES`.
- **IAM**: infrastructure role + instance profile.
- **Bảo mật**: không SSH, root filesystem bất biến, SELinux, tự vá, **drain sau 14 ngày**, capability đặc quyền tuỳ chọn; PCI-DSS, HIPAA, FedRAMP; FIPS endpoint ở capacity provider.
- **Network**: `awsvpc` hoặc `host`. **ELB**: ALB, NLB, GWLB; target type `ip`.
- **Storage**: EBS data volume 30–16.384 GiB (mặc định 80), chứa cả image; hoặc **instance store** (`useLocalStorage`, RAID 0, tạm thời, không tính thêm phí); kiểm tra bằng thuộc tính `ecs.capability.storage.local-storage-enabled`.
- **Giá**: trả theo instance; Compute & Instance Savings Plans áp dụng. **Quota**: EC2 On-Demand + ECS.
- **Migrate**: từ Fargate chỉ đổi capacity provider; ứng dụng phải chịu vòng đời 14 ngày; task > 14 ngày không phù hợp; không custom AMI.
- **Organization**: SCP và EC2 Allowed AMIs phải cho phép AMI từ account Managed Instances theo Region; **không** dùng `ec2:Owner`.

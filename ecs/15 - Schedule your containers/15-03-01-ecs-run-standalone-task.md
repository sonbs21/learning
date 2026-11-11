# Running an application as an Amazon ECS task

> Nguồn: **Amazon ECS Developer Guide — Running an application as an Amazon ECS task**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/standalone-task-create.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: diễn đạt lại bằng tiếng Việt, bám sát từng bước và đúng thứ tự của trang gốc, không thêm thông tin ngoài trang. Đây không phải bản dịch từng chữ; khi cần câu chữ chính xác, đọc link gốc ở trên.
> - Khối **💡 Giải thích thêm**: phần giải thích để dễ hiểu hơn, chỉ làm rõ những gì docs đã nói.
> - Tên nút, tên trường trên console giữ nguyên tiếng Anh (in đậm) để bạn dễ tìm.

---

## Mục lục

0. [Tổng quan các bước](#0-tổng-quan-các-bước)
1. [Mở console và chọn nơi bắt đầu](#1-mở-console-và-chọn-nơi-bắt-đầu)
2. [Chọn cluster](#2-chọn-cluster)
3. [Compute configuration](#3-compute-configuration)
4. [Deployment configuration](#4-deployment-configuration)
5. [Networking (mode `awsvpc`)](#5-networking-mode-awsvpc)
6. [Volume (EBS cấu hình lúc chạy)](#6-volume-ebs-cấu-hình-lúc-chạy)
7. [Task placement](#7-task-placement)
8. [Task overrides (IAM role)](#8-task-overrides-iam-role)
9. [Container overrides](#9-container-overrides)
10. [Tags và tạo task](#10-tags-và-tạo-task)
11. [Đối chiếu console với CLI](#11-đối-chiếu-console-với-cli)
12. [Tóm tắt](#12-tóm-tắt)

---

## 0. Tổng quan các bước

**📘 Theo docs AWS**

Trang hướng dẫn tạo task cho tiến trình **chạy một lần** bằng **AWS Management Console**.

**💡 Giải thích thêm**

```
Mở console → chọn chỗ bắt đầu (cluster / task definition)
 → chọn cluster
 → Compute configuration (capacity provider strategy hoặc launch type)
 → Deployment configuration (task definition, số task, task group)
 → Networking (nếu awsvpc)
 → Volume (nếu có volume cấu hình lúc chạy, vd EBS)
 → [tuỳ chọn] Task placement, Task overrides, Container overrides, Tags
 → Create
```

---

## 1. Mở console và chọn nơi bắt đầu

**📘 Theo docs AWS**

1. Mở console tại <https://console.aws.amazon.com/ecs/v2>.
2. Có thể tạo standalone task từ **trang chi tiết cluster** hoặc từ **danh sách revision của task definition**:

| Bắt đầu từ | Các bước |
|---|---|
| **Trang chi tiết cluster** | Trang **Clusters** → chọn cluster → tab **Tasks** → **Run task** |
| **Trang revision task definition** | Trang **Task definitions** → chọn family → chọn revision → menu **Deploy** → **Run task** |

**💡 Giải thích thêm**

Tiêu đề cột trong bảng của docs ghi "To start a service from", nhưng nội dung là chạy **task** — có vẻ là lỗi biên tập trên trang.

---

## 2. Chọn cluster

**📘 Theo docs AWS**

3. Ở **Existing cluster**, chọn cluster. Hoặc chọn **Create cluster** để chạy task trên cluster mới.

---

## 3. Compute configuration

**📘 Theo docs AWS**

4. Ở **Compute configuration**, chọn cách phân bổ task lên hạ tầng của cluster:
   - Muốn dùng **capacity provider strategy** thì phải cấu hình capacity provider ở **mức cluster**.
   - Cluster chưa cấu hình capacity provider thì dùng **launch type**.
   - Muốn chạy trên **Managed Instances** thì **bắt buộc** dùng **capacity provider strategy**.

| Cách phân bổ | Các bước |
|---|---|
| **Capacity provider strategy** | Ở **Compute options** chọn **Capacity provider strategy**, rồi: **Use cluster default** (dùng strategy mặc định của cluster), hoặc **Use custom** → **Add capacity provider strategy** → khai báo **Base**, **Capacity provider**, **Weight**. *Note:* capacity provider phải được gắn với cluster. |
| **Launch type** | Ở **Compute options** chọn **Launch type** → chọn launch type. Tuỳ chọn (Fargate): **Platform version**; không chỉ định thì dùng **`LATEST`**. |

**💡 Giải thích thêm**

Base và weight được giải thích ở file *Schedule your containers*. Managed Instances không có launch type riêng trong cách chạy này, nên chỉ đi qua capacity provider strategy.

---

## 4. Deployment configuration

**📘 Theo docs AWS**

5. Ở **Deployment configuration**:
   1. **Task definition**: nhập task definition.

      > **Important (docs):** Console kiểm tra family và revision đã chọn có **tương thích** với compute configuration hay không.

   2. **Desired tasks**: số task muốn chạy.
   3. **Task group**: tên task group.

**💡 Giải thích thêm**

- Kiểm tra tương thích ở đây chính là `requiresCompatibilities` (`EC2` / `FARGATE` / `MANAGED_INSTANCES`) trong các file *task definition parameters*.
- **Task group** bỏ trống thì mặc định là `family:<tên task definition>` (file *Group related Amazon ECS tasks*).

---

## 5. Networking (mode `awsvpc`)

**📘 Theo docs AWS**

6. Nếu task definition dùng `awsvpc`, mở **Networking**:
   1. **VPC**: chọn VPC.
   2. **Subnets**: chọn một hoặc nhiều subnet mà scheduler xét khi đặt task.
   3. **Security group**: chọn security group có sẵn, hoặc **Create a new security group** (nhập tên, mô tả, và một hoặc nhiều inbound rule).
   4. **Public IP**: có tự gán IP public cho ENI của task không.
      - Task **Fargate** ở public subnet có thể được gán IP public để có route ra internet.
      - Task **EC2** **không** gán IP public qua trường này.
      - (Xem *Amazon ECS task networking options for Fargate* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/fargate-task-networking.html> và *Allocate a network interface for an Amazon ECS task* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task-networking-awsvpc.html>.)

**💡 Giải thích thêm**

Khớp file *task networking for Fargate*: task Fargate ở public subnet **không bật Public IP** sẽ không pull được image (trừ khi có NAT hoặc VPC endpoint).

---

## 6. Volume (EBS cấu hình lúc chạy)

**📘 Theo docs AWS**

7. Nếu task dùng volume **cấu hình được lúc deploy**, mở **Volume** để cấu hình.
   - **Tên** và **loại** volume được đặt khi tạo task definition revision, **không đổi được** khi chạy standalone task. Muốn đổi phải tạo revision mới và chạy task bằng revision đó.

Cấu hình **Amazon EBS**:

| Trường | Ghi chú theo docs |
|---|---|
| **EBS volume type** | Loại EBS volume gắn vào task. |
| **Size (GiB)** | **1** đến **16.384** GiB. Bắt buộc, trừ khi có snapshot ID. |
| **IOPS** | Chỉ cấu hình được cho `io1`, `io2`, `gp3`. |
| **Throughput (MiB/s)** | Chỉ cấu hình được cho `gp3`. |
| **Snapshot ID** | Tạo volume từ snapshot (chọn hoặc nhập ARN); bỏ trống thì tạo volume trống. |
| **Volume initialization rate (MiB/s)** | Khi có snapshot ID: **100–300** MiB/s, tốc độ nạp dữ liệu từ snapshot. |
| **Termination policy** | Mặc định volume **bị xoá khi task kết thúc**; bỏ chọn checkbox để **giữ lại** volume. |
| **File system type** | Mặc định của OS hoặc chọn cụ thể; Linux mặc định **`XFS`**. Volume từ snapshot phải dùng **đúng** filesystem lúc tạo snapshot, sai thì task **không khởi động**. |
| **Infrastructure role** | IAM role cho ECS quản lý EBS volume; có thể gắn managed policy **`AmazonECSInfrastructureRolePolicyForVolumes`** hoặc tự viết policy dựa theo đó. (Xem *Amazon ECS infrastructure IAM role* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/infrastructure_IAM_role.html>.) |
| **Encryption** | **Default**: theo cài đặt *encryption by default* của EBS (bật thì mã hoá bằng KMS key trong cài đặt; chưa bật thì volume **không mã hoá**). **Custom**: tự chọn KMS key. **None**: không mã hoá, trừ khi đã bật encryption by default hoặc tạo từ snapshot đã mã hoá. (Xem *Encryption by default* — <https://docs.aws.amazon.com/ebs/latest/userguide/encryption-by-default.html>.) |
| **KMS key** | Khi chọn **Custom**: chọn key hoặc nhập ARN. Dùng symmetric customer managed key thì key policy phải có quyền phù hợp. (Xem *Data encryption for Amazon EBS volumes* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/ebs-volumes.html?icmpid=docs_ecs_hp-deploy#ebs-kms-encryption>.) |
| **Tags** (tuỳ chọn) | **Propagate tags from** → **Task definition** để lan tag từ task definition; **Do not propagate** hoặc bỏ trống thì không lan. Hoặc **Add tag** để tự thêm. (Xem *Tagging Amazon EBS volumes* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/specify-ebs-config.html#ebs-volume-tagging>.) |

**💡 Giải thích thêm**

Liên hệ các file *task definition parameters*: volume này là loại khai báo `configuredAtLaunch: true` trong task definition — loại volume cố định trong task definition, còn kích thước, snapshot, mã hoá… được chọn lúc chạy.

Hai điểm dễ sai:
- **Filesystem không khớp snapshot** → task không khởi động.
- **Termination policy** mặc định xoá volume khi task kết thúc → muốn giữ dữ liệu thì bỏ chọn.

---

## 7. Task placement

**📘 Theo docs AWS**

8. (Tuỳ chọn) Dùng strategy khác mặc định: mở **Task Placement** và chọn:

| Lựa chọn | Ý nghĩa theo docs |
|---|---|
| **AZ Balanced Spread** | Trải task qua các AZ và qua các instance trong mỗi AZ |
| **AZ Balanced BinPack** | Trải task qua các AZ, trong mỗi AZ dồn vào instance còn **ít memory nhất** |
| **BinPack** | Dồn task theo lượng CPU hoặc memory còn ít nhất |
| **One Task Per Host** | Tối đa **một** task trên mỗi container instance |
| **Custom** | Tự định nghĩa strategy và constraint |

Với **Custom**:
- **Strategy**: chọn **Type** và **Field**; tối đa **5** strategy.
- **Constraint**: chọn **Type** và **Expression**; tối đa **10** constraint. Ví dụ docs: đặt task lên instance T2 bằng expression `attribute:ecs.instance-type =~ t2.*`.

(Xem *How Amazon ECS places tasks on container instances* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task-placement.html>.)

**💡 Giải thích thêm**

Ánh xạ các lựa chọn console sang cấu hình (suy ra từ mô tả của docs và file *Use strategies* / *Define which container instances…*):

| Console | Tương ứng |
|---|---|
| AZ Balanced Spread | `spread` theo `attribute:ecs.availability-zone`, rồi `spread` theo `instanceId` |
| AZ Balanced BinPack | `spread` theo `attribute:ecs.availability-zone`, rồi `binpack` theo `memory` |
| BinPack | `binpack` theo `cpu` hoặc `memory` |
| One Task Per Host | constraint `distinctInstance` |

Placement chỉ có tác dụng với EC2 (Fargate và Managed Instances không hỗ trợ strategy — file *How Amazon ECS places tasks*). Mô tả "One Task Per Host" trong docs ghi "task from the service" dù đây là trang standalone task.

---

## 8. Task overrides (IAM role)

**📘 Theo docs AWS**

9. (Tuỳ chọn) Ghi đè **task role** hoặc **task execution role** đã khai báo trong task definition: mở **Task overrides**:
   1. **Task role**: chọn IAM role. Chỉ role có trust relationship với **`ecs-tasks.amazonaws.com`** mới hiện ra. (Xem *Amazon ECS task IAM role* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task-iam-roles.html> và *Creating the task IAM role* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task-iam-roles.html#create_task_iam_policy_and_role>.)
   2. **Task execution role**: chọn execution role. (Xem *Amazon ECS task execution IAM role* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task_execution_IAM_role.html>.)

**💡 Giải thích thêm**

Lưu ý phân biệt principal: task role tin `ecs-tasks.amazonaws.com`, còn infrastructure role của **ECS Gateway** (file *Create and manage a gateway*) tin `ecs.amazonaws.com`.

---

## 9. Container overrides

**📘 Theo docs AWS**

10. (Tuỳ chọn) Ghi đè lệnh và biến môi trường: mở **Container Overrides** rồi mở container cần sửa.
    - **Command override**: nhập lệnh Docker thay cho lệnh trong task definition.
    - **Add Environment Variable**: nhập **Key** (tên biến) và **Value** (chuỗi, **không** có dấu ngoặc kép bao quanh). AWS sẽ tự bao chuỗi bằng dấu ngoặc kép khi truyền vào container, dạng `TÊN_BIẾN="giá trị"`.

**💡 Giải thích thêm**

Hữu ích cho batch job: cùng một task definition, mỗi lần chạy truyền tham số khác nhau (vd ngày cần xử lý) qua override, không cần tạo revision mới.

---

## 10. Tags và tạo task

**📘 Theo docs AWS**

11. (Tuỳ chọn) Mở **Tags** để gắn tag nhận diện task:
    - Chọn **Turn on Amazon ECS managed tags** rồi chọn **Task definitions** để ECS tự gắn **tên cluster** và **tag của task definition** lên mọi task mới.
    - **Add tag**: nhập **Key** và **Value**. **Remove tag**: xoá tag.
12. Chọn **Create**.

---

## 11. Đối chiếu console với CLI

**💡 Giải thích thêm**

Trang chỉ hướng dẫn console. Bảng dưới là mình đối chiếu sang tham số của `aws ecs run-task` để tiện tự động hoá; tên tham số CLI không có trên trang này.

| Console | `aws ecs run-task` |
|---|---|
| Existing cluster | `--cluster` |
| Capacity provider strategy | `--capacity-provider-strategy` |
| Launch type / Platform version | `--launch-type` / `--platform-version` |
| Task definition | `--task-definition` |
| Desired tasks | `--count` |
| Task group | `--group` |
| Networking | `--network-configuration` |
| Volume (EBS) | `--volume-configurations` |
| Task placement | `--placement-strategy`, `--placement-constraints` |
| Task overrides / Container overrides | `--overrides` |
| Turn on Amazon ECS managed tags / Propagate | `--enable-ecs-managed-tags`, `--propagate-tags` |
| Tags | `--tags` |

```bash
aws ecs run-task \
  --cluster <CLUSTER> \
  --task-definition <FAMILY>:<REVISION> \
  --count 1 \
  --group <TASK_GROUP> \
  --capacity-provider-strategy capacityProvider=FARGATE,weight=1 \
  --network-configuration 'awsvpcConfiguration={subnets=[<SUBNET_ID>],securityGroups=[<SG_ID>],assignPublicIp=DISABLED}' \
  --overrides '{"containerOverrides":[{"name":"<CONTAINER>","environment":[{"name":"RUN_DATE","value":"2026-10-26"}]}]}'
```

---

## 12. Tóm tắt

- Console: <https://console.aws.amazon.com/ecs/v2>; bắt đầu từ **cluster → Tasks → Run task**, hoặc **task definition revision → Deploy → Run task**.
- Chọn cluster (hoặc tạo mới).
- **Compute**: capacity provider strategy (cluster default hoặc custom: base, provider, weight — provider phải gắn cluster; bắt buộc với Managed Instances) hoặc launch type (Fargate có platform version, mặc định `LATEST`).
- **Deployment**: task definition (console kiểm tra tương thích), desired tasks, task group.
- **Networking** (`awsvpc`): VPC, subnet, security group, Public IP (chỉ Fargate dùng được trường này).
- **Volume EBS** lúc chạy: tên/loại cố định theo revision; size 1–16.384 GiB; IOPS (io1/io2/gp3), throughput (gp3); snapshot + tốc độ khởi tạo 100–300 MiB/s; mặc định xoá khi task kết thúc; filesystem phải khớp snapshot (Linux mặc định XFS); infrastructure role; mã hoá Default/Custom/None; tag.
- **Placement** (tuỳ chọn): AZ Balanced Spread, AZ Balanced BinPack, BinPack, One Task Per Host, Custom (≤ 5 strategy, ≤ 10 constraint).
- **Overrides** (tuỳ chọn): task role (trust `ecs-tasks.amazonaws.com`), execution role; command và biến môi trường của container.
- **Tags** (tuỳ chọn): ECS managed tags, tag tuỳ chỉnh → **Create**.

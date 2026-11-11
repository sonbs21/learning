# Amazon ECS task lifecycle

> Nguồn: **Amazon ECS Developer Guide — Amazon ECS task lifecycle**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task-lifecycle-explanation.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: diễn đạt lại bằng tiếng Việt, bám sát từng ý và đúng thứ tự của trang gốc, không thêm thông tin ngoài trang. Đây không phải bản dịch từng chữ; khi cần câu chữ chính xác, đọc link gốc ở trên.
> - Khối **💡 Giải thích thêm**: phần giải thích để dễ hiểu hơn, chỉ làm rõ những gì docs đã nói.
> - Tên trạng thái giữ nguyên tiếng Anh như docs.

---

## Mục lục

1. [Tổng quan vòng đời task](#1-tổng-quan-vòng-đời-task)
2. [`lastStatus` và `desiredStatus`](#2-laststatus-và-desiredstatus)
3. [Sơ đồ vòng đời](#3-sơ-đồ-vòng-đời)
4. [Các trạng thái](#4-các-trạng-thái)
5. [Quá trình dừng container (STOPPING)](#5-quá-trình-dừng-container-stopping)
6. [Liên hệ với các file trước](#6-liên-hệ-với-các-file-trước)
7. [Tóm tắt](#7-tóm-tắt)

---

## 1. Tổng quan vòng đời task

**📘 Theo docs AWS**

- Khi một task được khởi động — **thủ công** hoặc **trong một service** — nó có thể đi qua nhiều trạng thái trước khi **tự kết thúc** hoặc **bị dừng thủ công**.
- Một số task là **batch job**, tự nhiên đi từ `PENDING` → `RUNNING` → `STOPPED`.
- Một số task khác (có thể thuộc service) được thiết kế để **chạy liên tục**, hoặc được **scale lên/xuống** khi cần.

**💡 Giải thích thêm**

Khớp với file *Schedule your containers*: standalone task và scheduled task thường là kiểu batch (`PENDING → RUNNING → STOPPED`), còn task của service là kiểu chạy lâu dài.

---

## 2. `lastStatus` và `desiredStatus`

**📘 Theo docs AWS**

- Khi có yêu cầu đổi trạng thái task (ví dụ **dừng task**, hoặc **đổi desired count** của service để scale), ECS container agent theo dõi hai giá trị:
  - **`lastStatus`** — trạng thái **đã biết gần nhất** của task;
  - **`desiredStatus`** — trạng thái **mong muốn** của task.
- Xem được cả hai trên **console**, hoặc khi describe task bằng **API** hay **AWS CLI**.

**💡 Giải thích thêm**

```
desiredStatus = nơi ECS muốn task đi tới   (vd RUNNING, hoặc STOPPED khi bạn gọi stop-task)
lastStatus    = nơi task thực sự đang ở     (vd PENDING, ACTIVATING, RUNNING...)
```

Khi debug: nếu `desiredStatus` là `RUNNING` mà `lastStatus` cứ đứng ở `PENDING`, task đang chờ tài nguyên (xem mục 4). Nếu `desiredStatus` đã là `STOPPED` mà `lastStatus` vẫn là `RUNNING`/`DEACTIVATING`, task đang trong quá trình dừng.

Lệnh xem nhanh (cách viết lệnh là của mình, docs chỉ nói "describe task bằng API hoặc CLI"):

```bash
aws ecs describe-tasks --cluster <CLUSTER> --tasks <TASK_ID> \
  --query 'tasks[].{last:lastStatus,desired:desiredStatus}'
```

---

## 3. Sơ đồ vòng đời

**📘 Theo docs AWS**

Docs có một flow chart vòng đời task; theo mô tả hình, các trạng thái trên sơ đồ gồm `PROVISIONING`, `PENDING`, `ACTIVATING`, `RUNNING`, `DEACTIVATING`, `STOPPING`.

**💡 Giải thích thêm**

Ghép với danh sách trạng thái ở mục 4 (có thêm `DEPROVISIONING`, `STOPPED`, `DELETED`), vòng đời đầy đủ có thể hình dung như sau:

```
 Khởi động                                        Dừng
 ─────────                                        ────
 PROVISIONING   (chuẩn bị trước khi launch, vd tạo ENI)
      │
 PENDING        (chờ agent / chờ tài nguyên)
      │
 ACTIVATING     (pull image, tạo container, cấu hình mạng,
      │          đăng ký target group, service discovery)
      ▼
 RUNNING  ───────────────────────────────►  DEACTIVATING  (vd deregister khỏi target group)
                                                 │
                                            STOPPING      (gửi STOPSIGNAL → chờ StopTimeout → SIGKILL)
                                                 │
                                            DEPROVISIONING (vd tháo và xoá ENI)
                                                 │
                                            STOPPED
                                                 │
                                            DELETED       (chỉ thấy qua describe-tasks)
```

Mô tả hình trên docs chỉ liệt kê 6 trạng thái, trong khi phần mô tả chi tiết có 9; sơ đồ ở đây là mình ghép từ cả hai.

---

## 4. Các trạng thái

**📘 Theo docs AWS**

| Trạng thái | Loại | Ý nghĩa theo docs |
|---|---|---|
| **`PROVISIONING`** | Chuẩn bị | ECS phải làm thêm vài bước **trước khi launch** task. Ví dụ: task dùng mode `awsvpc` cần **tạo ENI**. |
| **`PENDING`** | Chuyển tiếp | ECS **chờ container agent** làm bước tiếp theo. Task ở trạng thái này **cho tới khi có đủ tài nguyên**. |
| **`ACTIVATING`** | Chuyển tiếp | ECS làm thêm bước **sau khi launch nhưng trước `RUNNING`**: **pull container image**, **tạo container**, **cấu hình mạng** của task, **đăng ký target group** của load balancer, **cấu hình service discovery**. |
| **`RUNNING`** | Ổn định | Task đang **chạy thành công**. |
| **`DEACTIVATING`** | Chuyển tiếp | ECS làm thêm bước **trước khi dừng** task. Ví dụ: task thuộc service dùng target group của ELB thì **deregister khỏi target group** diễn ra ở đây. |
| **`STOPPING`** | Chuyển tiếp | ECS **chờ container agent** làm bước tiếp theo (xem mục 5). |
| **`DEPROVISIONING`** | Dọn dẹp | ECS làm thêm bước **sau khi task dừng nhưng trước `STOPPED`**. Ví dụ: task `awsvpc` cần **tháo và xoá ENI**. |
| **`STOPPED`** | Kết thúc | Task đã **dừng thành công**. Nếu task dừng vì lỗi, xem *Viewing Amazon ECS stopped task errors* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/stopped-task-errors.html>. |
| **`DELETED`** | Chuyển tiếp | Trạng thái chuyển tiếp khi task dừng. **Không hiện trên console**, nhưng hiện trong **`describe-tasks`**. |

**💡 Giải thích thêm**

Task kẹt ở trạng thái nào thường gợi ý hướng điều tra (gợi ý của mình, dựa trên mô tả từng trạng thái):

| Kẹt ở | Gợi ý theo nội dung docs |
|---|---|
| `PROVISIONING` | Liên quan tạo ENI (mode `awsvpc`) — vd subnet hết IP |
| `PENDING` | Chưa có đủ tài nguyên (CPU/memory trên cluster, hoặc capacity) |
| `ACTIVATING` | Pull image (quyền ECR, đường ra internet / VPC endpoint), tạo container, đăng ký target group, service discovery |
| `DEACTIVATING` | Deregister khỏi target group (thời gian draining của target group) |
| `STOPPING` | Ứng dụng không xử lý `SIGTERM`, phải chờ hết `StopTimeout` |
| `DEPROVISIONING` | Tháo/xoá ENI |

---

## 5. Quá trình dừng container (STOPPING)

**📘 Theo docs AWS**

Với **Linux container**, ở trạng thái `STOPPING`:
1. Container agent gửi **stop signal** được định nghĩa trong container image qua chỉ thị **`STOPSIGNAL`**, để báo ứng dụng cần kết thúc và tắt. Mặc định là **`SIGTERM`**.
2. Sau khi chờ hết thời gian **`StopTimeout`** đặt trong task definition, agent gửi **`SIGKILL`**.

**💡 Giải thích thêm**

```
STOPPING:
  t = 0         → gửi STOPSIGNAL (mặc định SIGTERM)  → ứng dụng nên tự đóng kết nối, flush dữ liệu, thoát
  t = StopTimeout → nếu container còn sống → SIGKILL (dừng cưỡng bức, không kịp dọn dẹp)
```

Để tắt êm (graceful shutdown):
- Ứng dụng phải **bắt `SIGTERM`** và thoát trước `StopTimeout`.
- Nếu tiến trình chính không chuyển signal cho tiến trình con, cân nhắc `initProcessEnabled` (file *task definition parameters*).
- Đặt `stopTimeout` đủ cho việc dọn dẹp, trong giới hạn của từng môi trường: Fargate tối đa 120 giây; Managed Instances tối đa 86400 giây; EC2 mặc định lấy từ `ECS_CONTAINER_STOP_TIMEOUT` hoặc 30 giây (theo các file *task definition parameters*).

---

## 6. Liên hệ với các file trước

**💡 Giải thích thêm**

| Trạng thái | Liên quan tới file |
|---|---|
| `PROVISIONING`, `DEPROVISIONING` (ENI) | *task networking* (EC2, Fargate, Managed Instances) — mode `awsvpc` mỗi task một ENI |
| `ACTIVATING` — pull image | *Container image pull behavior* (`ECS_IMAGE_PULL_BEHAVIOR`), SOCI lazy loading trong *Fargate task definition differences* |
| `ACTIVATING` — đăng ký target group | *Connect services to a gateway*, *Monitor a gateway* (target health) |
| `DEACTIVATING` — deregister target group | *Architect for EC2 capacity* (load balancing, rolling update) |
| `STOPPING` — `StopTimeout` | Các file *task definition parameters* |
| `PENDING` — chờ tài nguyên | *Best practices for task sizes*, *Schedule your containers* (capacity provider) |

Nhận xét: thời gian một task từ khi được yêu cầu tới khi nhận traffic = `PROVISIONING` + `PENDING` + `ACTIVATING` (gồm cả pull image) + thời gian health check của target group. Các file trước bàn về cách rút ngắn từng phần đó.

---

## 7. Tóm tắt

- Task được khởi động thủ công hoặc trong service, đi qua nhiều trạng thái trước khi tự kết thúc hoặc bị dừng.
- Batch job: `PENDING` → `RUNNING` → `STOPPED`; task của service: chạy liên tục hoặc scale theo nhu cầu.
- Agent theo dõi **`lastStatus`** (thực tế) và **`desiredStatus`** (mong muốn); xem qua console, API, CLI.
- Trạng thái: `PROVISIONING` (vd tạo ENI) → `PENDING` (chờ tài nguyên) → `ACTIVATING` (pull image, tạo container, mạng, target group, service discovery) → `RUNNING` → `DEACTIVATING` (vd deregister target group) → `STOPPING` → `DEPROVISIONING` (vd xoá ENI) → `STOPPED` → `DELETED` (chỉ thấy trong `describe-tasks`).
- Linux: khi dừng, agent gửi `STOPSIGNAL` (mặc định `SIGTERM`), chờ `StopTimeout`, rồi `SIGKILL`.
- Task dừng vì lỗi: xem trang *stopped task errors*.

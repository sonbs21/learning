# Daemon deployments

> Nguồn: **Amazon ECS Developer Guide — Daemon deployments**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/managed-daemons-deployments.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: diễn đạt lại bằng tiếng Việt, bám sát từng ý và đúng thứ tự của trang gốc, không thêm thông tin ngoài trang. Đây không phải bản dịch từng chữ; khi cần câu chữ hoặc ví dụ code chính xác, đọc link gốc ở trên.
> - Khối **💡 Giải thích thêm**: phần giải thích để dễ hiểu hơn, chỉ làm rõ những gì docs đã nói.
> - Lệnh CLI dùng placeholder (`<...>`); tên lệnh, tham số, trường và trạng thái giữ đúng như docs.

---

## Mục lục

1. [Tổng quan](#1-tổng-quan)
2. [Cách daemon deployment hoạt động](#2-cách-daemon-deployment-hoạt-động)
3. [Tham số cấu hình deployment](#3-tham-số-cấu-hình-deployment)
4. [Theo dõi deployment](#4-theo-dõi-deployment)
5. [Các kịch bản deployment](#5-các-kịch-bản-deployment)
   - [5.1 Capacity provider chưa có instance](#51-capacity-provider-chưa-có-instance)
   - [5.2 Capacity provider đã có instance](#52-capacity-provider-đã-có-instance)
   - [5.3 Thêm capacity provider cho daemon có sẵn](#53-thêm-capacity-provider-cho-daemon-có-sẵn)
   - [5.4 Cập nhật daemon lên revision mới](#54-cập-nhật-daemon-lên-revision-mới)
6. [Lưu ý và liên hệ với các file trước](#6-lưu-ý-và-liên-hệ-với-các-file-trước)
7. [Tóm tắt](#7-tóm-tắt)

---

## 1. Tổng quan

**📘 Theo docs AWS**

Khi **tạo** hoặc **cập nhật** daemon, ECS thực hiện **rolling deployment** để chạy daemon task trên **mọi instance** của các capacity provider được gắn. Trang này mô tả cách deployment hoạt động và cách cấu hình nó.

---

## 2. Cách daemon deployment hoạt động

**📘 Theo docs AWS**

- Daemon deployment theo mô hình **drain → provision → replace**:
  - khi **tạo daemon** trên capacity provider **đã có instance**, hoặc **cập nhật** daemon sang revision mới,
  - ECS **drain một đợt instance**, **cấp instance thay thế** chạy daemon mới, và **tự thay task ứng dụng**;
  - lặp lại tới khi **mọi instance** chạy revision daemon mới.

**Trạng thái của deployment:**

| Thứ tự | Trạng thái | Ý nghĩa theo docs |
|---|---|---|
| 1 | **`PENDING`** | ECS đã tạo deployment và đang chuẩn bị bắt đầu |
| 2 | **`IN_PROGRESS`** | ECS đang drain instance và cấp instance thay thế |
| 3 | **`SUCCESSFUL`** | Mọi instance đã chạy revision daemon mục tiêu |
| 4 | **`STOPPED`** | ECS dừng deployment vì **thất bại** hoặc bị **deployment mới thay thế**. Daemon có thể ở trạng thái **lẫn lộn**: một số instance chạy revision cũ, một số chạy revision mới |

- ECS **tự rollback** về revision daemon trước nếu **deployment circuit breaker** phát hiện lỗi, hoặc **CloudWatch alarm** kích hoạt trong lúc deploy.
- Trong deployment, daemon task **non-critical** bị lỗi **không** làm drain hay thay instance — instance vẫn active và chạy task ứng dụng. Tuy nhiên, **lỗi khởi động daemon task vẫn được tính** vào circuit breaker, nên revision mục tiêu không ổn định vẫn có thể bị rollback.

**💡 Giải thích thêm**

```
            PENDING
               │
            IN_PROGRESS ──► (lặp) drain X% instance
               │                  → cấp instance mới với daemon revision mới
               │                  → dời task ứng dụng sang instance mới
               │
     ┌─────────┴──────────┐
     ▼                    ▼
 SUCCESSFUL           STOPPED   (lỗi / bị thay thế — có thể lẫn cũ & mới)
     │                    │
 (bake time + alarm)   rollback (circuit breaker hoặc alarm)
```

Điểm tinh tế với daemon **non-critical**: không ảnh hưởng instance, nhưng **vẫn chặn được** revision lỗi nhờ circuit breaker đếm lỗi khởi động. Nghĩa là non-critical bảo vệ workload, còn circuit breaker bảo vệ chất lượng bản phát hành daemon.

---

## 3. Tham số cấu hình deployment

**📘 Theo docs AWS**

Tuỳ chỉnh bằng tham số **`deploymentConfiguration`** khi tạo hoặc cập nhật daemon.

| Trường | Giá trị | Mặc định | Ý nghĩa |
|---|---|---|---|
| **`drainPercent`** | 1.0–100.0 | **25.0** | % instance được drain **cùng lúc**. Cao → deploy nhanh hơn nhưng có thể **tạm giảm capacity** sẵn có. Ví dụ `20.0` = drain 20% mỗi lần. |
| **`alarms`** | `DaemonAlarmConfiguration` | — | CloudWatch alarm theo dõi trong deployment. Alarm nào chuyển sang **`ALARM`** → ECS tự rollback. Alarm **đã ở `ALARM` từ lúc bắt đầu** deployment thì **bị bỏ qua**. |
| **`bakeTimeInMinutes`** | 0–1440 | **0** | Số phút ECS chờ **sau khi** cập nhật xong mọi instance, trước khi hoàn tất deployment. Trong thời gian này ECS theo dõi alarm và tự rollback nếu alarm kích hoạt. |

Ví dụ docs: tạo daemon với `drainPercent` 20 và `bakeTimeInMinutes` 5.

```bash
aws ecs create-daemon \
  --cluster-arn <CLUSTER_ARN> \
  --daemon-name <DAEMON_NAME> \
  --daemon-task-definition-arn <DAEMON_TASK_DEFINITION_ARN> \
  --capacity-provider-arns <CAPACITY_PROVIDER_ARN> \
  --deployment-configuration '{"drainPercent":20.0,"bakeTimeInMinutes":5}'
```

**💡 Giải thích thêm**

Trang này cho biết tên trường của `DaemonDeploymentConfiguration` mà file *Creating and managing daemons* chưa nêu — khớp với ba thiết lập trên console: **Drain percentage**, **Use CloudWatch alarm(s)**, **Bake time**.

| Chọn | Ưu tiên |
|---|---|
| `drainPercent` thấp (vd 10) | An toàn, ít task bị dời cùng lúc, deploy chậm |
| `drainPercent` cao (vd 50) | Deploy nhanh, có lúc thiếu capacity |
| `bakeTimeInMinutes` = 0 | Hoàn tất ngay khi cập nhật xong |
| `bakeTimeInMinutes` > 0 + alarm | Có thời gian "quan sát" sau deploy, bắt lỗi chậm xuất hiện |

Lưu ý: alarm đã đỏ sẵn trước khi deploy thì **không** được dùng để rollback — nên dọn các alarm đang đỏ trước khi deploy nếu muốn chúng có tác dụng.

---

## 4. Theo dõi deployment

**📘 Theo docs AWS**

```bash
aws ecs list-daemon-deployments --daemon-arn <DAEMON_ARN>

aws ecs describe-daemon-deployments --daemon-deployment-arns <DAEMON_DEPLOYMENT_ARN>
```

Kết quả `describe-daemon-deployments` gồm:
- **trạng thái** deployment;
- **revision daemon mục tiêu**;
- trường **`totalRunningInstanceCount`** — số instance đang chạy daemon mục tiêu.

**💡 Giải thích thêm**

Daemon deployment ARN có dạng `arn:aws:ecs:<REGION>:<ACCOUNT_ID>:daemon-deployment/<CLUSTER>/<ID>` (theo ví dụ trên trang). Theo dõi tiến độ bằng cách so `totalRunningInstanceCount` với tổng số instance của các capacity provider.

---

## 5. Các kịch bản deployment

### 5.1 Capacity provider chưa có instance

**📘 Theo docs AWS**

Daemon deploy lên capacity provider **chưa có instance**; instance chỉ được tạo khi bạn lập lịch task ứng dụng.

**Tiên quyết:** cluster + capacity provider Managed Instances **không có instance đang chạy**.

1. Đăng ký daemon task definition.
2. Tạo daemon (`create-daemon`). ECS **hoàn tất việc tạo ngay** dù chưa có instance.
3. Tạo **service** dùng **cùng capacity provider** (ví dụ docs: desired count 2, `capacity-provider-strategy` trỏ tới capacity provider đó). Khi ECS cấp instance cho service, nó **khởi động daemon task trước**, rồi mới chuyển task ứng dụng sang **`RUNNING`**.
4. Kiểm tra daemon task chạy trên mỗi instance bằng `describe-daemons` hoặc tab **Daemons** trên console.

> **Note (docs):** Deployment có thể ở **`PENDING`** một lúc trong khi ECS cấp instance và khởi động daemon task. Chờ tới **`SUCCESSFUL`** rồi mới kiểm tra vị trí daemon task.

```bash
aws ecs create-service \
  --cluster <CLUSTER> \
  --service-name <APP_SERVICE> \
  --task-definition <APP_TASK_DEFINITION> \
  --desired-count 2 \
  --capacity-provider-strategy capacityProvider=<CAPACITY_PROVIDER>,weight=1
```

### 5.2 Capacity provider đã có instance

**📘 Theo docs AWS**

Daemon deploy lên capacity provider **đã có instance và task ứng dụng đang chạy**.

**Tiên quyết:** cluster + capacity provider Managed Instances **có instance đang chạy**.

1. Đăng ký daemon task definition.
2. Tạo daemon → ECS bắt đầu **rolling deployment** để đặt daemon lên mọi instance có sẵn (ví dụ docs: `drainPercent` 25, `bakeTimeInMinutes` 3). ECS drain một đợt instance theo `drainPercent`, cấp instance thay thế có daemon, thay task ứng dụng; lặp lại tới khi mọi instance có daemon.
3. Theo dõi bằng `list-daemon-deployments`.

**💡 Giải thích thêm**

Khác với 5.1: daemon **không được "chèn" vào instance đang chạy** — instance cũ bị **drain và thay bằng instance mới** có daemon. Nghĩa là task ứng dụng hiện có sẽ bị **dời chỗ**.

### 5.3 Thêm capacity provider cho daemon có sẵn

**📘 Theo docs AWS**

Thêm capacity provider thứ hai cho daemon đang chạy; daemon **tự deploy** lên instance của capacity provider mới.

**Tiên quyết:** daemon đang chạy trên capacity provider thứ nhất; capacity provider thứ hai đã tạo và gắn với cluster.

1. `update-daemon` với **cả hai** capacity provider trong `--capacity-provider-arns` (kèm daemon task definition hiện tại).
2. Theo dõi deployment, xác nhận daemon task chạy trên instance của **cả hai** capacity provider.

```bash
aws ecs update-daemon \
  --daemon-arn <DAEMON_ARN> \
  --daemon-task-definition-arn <DAEMON_TASK_DEFINITION_ARN_HIỆN_TẠI> \
  --capacity-provider-arns <CAPACITY_PROVIDER_1_ARN> <CAPACITY_PROVIDER_2_ARN>
```

### 5.4 Cập nhật daemon lên revision mới

**📘 Theo docs AWS**

1. Đăng ký **revision mới** của daemon task definition (image hoặc cấu hình mới).
2. `update-daemon` với revision mới. ECS rolling deploy: drain instance chạy revision cũ, cấp instance thay thế với revision mới, tự thay task ứng dụng. Circuit breaker phát hiện lỗi → rollback về revision trước.
3. Theo dõi bằng `describe-daemon-deployments`, xác nhận mọi instance chạy revision mới.

**💡 Giải thích thêm**

Ví dụ `update-daemon` ở kịch bản này trên docs **chỉ truyền** `--daemon-arn` và `--daemon-task-definition-arn`. Theo ghi chú Important ở file *Creating and managing daemons*: `UpdateDaemon` **không giữ** thiết lập cũ, thiết lập bỏ sót **về mặc định**, `critical` mặc định `true`. Vì vậy khi làm thật, nên truyền **đầy đủ**: capacity provider, `--no-critical` (nếu daemon đang non-critical), deployment configuration, tag, ECS Exec. Hai trang docs có vẻ chưa khớp nhau ở ví dụ này.

```bash
aws ecs update-daemon \
  --daemon-arn <DAEMON_ARN> \
  --daemon-task-definition-arn <DAEMON_TASK_DEFINITION_ARN_REVISION_MỚI> \
  --capacity-provider-arns <CAPACITY_PROVIDER_ARN> \
  --no-critical \
  --deployment-configuration '{"drainPercent":20.0,"bakeTimeInMinutes":10}'
```

(Giá trị `--no-critical` và deployment configuration ở trên chỉ là ví dụ — giữ đúng cấu hình hiện tại của daemon bạn.)

---

## 6. Lưu ý và liên hệ với các file trước

**💡 Giải thích thêm**

| Nội dung | File liên quan |
|---|---|
| Critical vs non-critical | *Amazon ECS Managed Daemons* |
| `drainPercent`, alarm, bake time trên console | *Creating and managing daemons* |
| `UpdateDaemon` ghi đè toàn bộ | *Creating and managing daemons* (giống `UpdateGateway` trong *Create and manage an Amazon ECS Gateway*) |
| Daemon khởi động trước task app `RUNNING` | *Amazon ECS Managed Daemons*, *Task lifecycle* |
| Task app bị dời khi drain | *Task lifecycle* (`DEACTIVATING`, `STOPPING` — tắt êm với `SIGTERM`) |
| Rollback bằng circuit breaker / alarm | Cùng ý tưởng với deployment của service (file *Architect for EC2 capacity* nhắc rolling update) |

Một ý thực tế: vì mọi kịch bản có instance sẵn đều **dời task ứng dụng**, service nên có **desired count ≥ 2** và chạy trên nhiều instance, để khi một đợt `drainPercent` bị drain vẫn còn task phục vụ — suy luận của mình từ mô hình drain → replace.

---

## 7. Tóm tắt

- Tạo/cập nhật daemon → **rolling deployment** theo mô hình **drain → provision → replace**, lặp tới khi mọi instance chạy revision mới.
- Trạng thái: **`PENDING`** → **`IN_PROGRESS`** → **`SUCCESSFUL`** hoặc **`STOPPED`** (lỗi/bị thay thế, có thể lẫn cũ và mới).
- **Tự rollback** khi circuit breaker phát hiện lỗi hoặc CloudWatch alarm kích hoạt. Daemon non-critical lỗi không drain instance, nhưng lỗi khởi động vẫn tính vào circuit breaker.
- `deploymentConfiguration`: **`drainPercent`** 1–100 (mặc định 25), **`alarms`** (bỏ qua alarm đã đỏ từ đầu), **`bakeTimeInMinutes`** 0–1440 (mặc định 0).
- Theo dõi: `list-daemon-deployments`, `describe-daemon-deployments` (trạng thái, revision mục tiêu, `totalRunningInstanceCount`).
- **Kịch bản**: capacity provider trống (tạo xong ngay, daemon chạy khi service tạo instance); có instance (drain và thay dần); thêm capacity provider (`update-daemon` với cả hai); revision mới (`update-daemon`, rollback nếu lỗi).
- Khi `update-daemon`, truyền **đầy đủ** thiết lập vì thiết lập bỏ sót sẽ về mặc định.

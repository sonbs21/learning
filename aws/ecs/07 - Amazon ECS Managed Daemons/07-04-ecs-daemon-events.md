# Daemon events

> Nguồn: **Amazon ECS Developer Guide — Daemon events**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/managed-daemons-events.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: diễn đạt lại bằng tiếng Việt, bám sát từng ý và đúng thứ tự của trang gốc, không thêm thông tin ngoài trang. Đây không phải bản dịch từng chữ; khi cần câu chữ hoặc ví dụ JSON chính xác, đọc link gốc ở trên.
> - Khối **💡 Giải thích thêm**: phần giải thích để dễ hiểu hơn, chỉ làm rõ những gì docs đã nói.
> - Tên detail-type, tên sự kiện, tên trường giữ nguyên như docs. Ví dụ sự kiện mẫu trên docs được tóm thành bảng trường thay vì chép lại nguyên văn.

---

## Mục lục

1. [Tổng quan](#1-tổng-quan)
2. [Daemon service action events](#2-daemon-service-action-events)
3. [Daemon deployment state change events](#3-daemon-deployment-state-change-events)
4. [Tạo rule cho daemon events](#4-tạo-rule-cho-daemon-events)
5. [Liên hệ với các file trước](#5-liên-hệ-với-các-file-trước)
6. [Tóm tắt](#6-tóm-tắt)

---

## 1. Tổng quan

**📘 Theo docs AWS**

- ECS gửi **daemon events** (tới EventBridge) để cung cấp khả năng quan sát việc **quản lý daemon task** và các thay đổi trong **vòng đời deployment**.
- Dùng các event này để **giám sát sức khoẻ daemon**, **theo dõi tiến độ deployment** và **kích hoạt workflow tự động**.
- Hai loại event:

| Loại | `detail-type` | Nói về |
|---|---|---|
| **Daemon service action events** | `ECS Daemon Service Action` | Vấn đề khi **đặt** và **khởi động** daemon task |
| **Daemon deployment state change events** | `ECS Daemon Deployment State Change` | Chuyển trạng thái trong **vòng đời deployment** |

**💡 Giải thích thêm**

Câu mở đầu trên trang gốc bị thiếu chữ ("sends daemon events to to…"); theo ngữ cảnh và mô tả trang, đích là **Amazon EventBridge**.

---

## 2. Daemon service action events

**📘 Theo docs AWS**

- `detail-type`: **`ECS Daemon Service Action`**.
- Báo khi ECS gặp vấn đề **khởi động daemon task** trên container instance.
- ECS phát event khi daemon task **không khởi động được**, cho **cả critical lẫn non-critical** daemon.

### 2.1 `DAEMON_TASK_START_IMPAIRED`

**📘 Theo docs AWS**

Phát khi ECS **không khởi động thành công** daemon task trên một container instance. Event có trường **`failureType`** cho biết nguyên nhân:

| `failureType` | Ý nghĩa | Nguyên nhân thường gặp (theo docs) | Có `taskArn`? |
|---|---|---|---|
| **`TASK_FAILED_TO_RUN`** | Daemon task **đã được tạo** nhưng **không đạt `RUNNING`** | Pull image lỗi, health check của container lỗi, container essential thoát | **Có** |
| **`TASK_CREATION_FAILED`** | Daemon task **không tạo được** trên instance | Thiếu CPU, memory hoặc tài nguyên khác trên instance | **Không** (vì chưa có task nào được tạo) |

### 2.2 Các trường trong event (theo hai ví dụ trên docs)

**📘 Theo docs AWS**

Docs đưa hai ví dụ: một event `TASK_FAILED_TO_RUN` do **pull image thất bại** (lý do có `CannotPullContainerError`), và một event `TASK_CREATION_FAILED` do **thiếu CPU** (lý do bắt đầu bằng `RESOURCE:CPU`). Các trường xuất hiện:

| Trường | Vị trí | Ý nghĩa |
|---|---|---|
| `detail-type` | gốc | `ECS Daemon Service Action` |
| `source` | gốc | `aws.ecs` |
| `resources` | gốc | ARN task (với `TASK_FAILED_TO_RUN`); **rỗng** với `TASK_CREATION_FAILED` |
| `eventType` | `detail` | `WARNING` |
| `eventName` | `detail` | `DAEMON_TASK_START_IMPAIRED` |
| `clusterArn` | `detail` | ARN cluster |
| `containerInstanceArn` | `detail` | ARN container instance bị ảnh hưởng |
| `taskArn` | `detail` | ARN daemon task — **chỉ có** khi `TASK_FAILED_TO_RUN` |
| `daemonRevisionArn` | `detail` | ARN **daemon revision** |
| `capacityProviderName` | `detail` | Tên capacity provider |
| `daemonArn` | `detail` | ARN daemon |
| `daemonTaskDefinitionArn` | `detail` | ARN daemon task definition (kèm revision) |
| `failureType` | `detail` | `TASK_FAILED_TO_RUN` hoặc `TASK_CREATION_FAILED` |
| `createdAt` | `detail` | Thời điểm |
| `reason` | `detail` | Mô tả lý do lỗi |

**💡 Giải thích thêm**

| Thấy | Hướng xử lý (gợi ý của mình) |
|---|---|
| `TASK_FAILED_TO_RUN` + `CannotPullContainerError` | Kiểm tra image URI, quyền execution role với ECR, đường mạng tới registry (file *Container image pull behavior*, *task networking*) |
| `TASK_FAILED_TO_RUN` do health check / container essential thoát | Xem log của daemon, cấu hình `healthCheck` (file *Daemon task definitions*) |
| `TASK_CREATION_FAILED` + `RESOURCE:CPU` / memory | Giảm `cpu`/`memory` của daemon, hoặc dùng instance lớn hơn — daemon phải "vừa" trên mọi instance |

Xuất hiện trường **`daemonRevisionArn`** — một khái niệm "daemon revision" khác với revision của daemon task definition; trang này không giải thích thêm, nhưng nó cũng có trong event deployment (`targetDaemonRevisionArn`, mục 3).

Với daemon **critical**, `TASK_FAILED_TO_RUN`/`TASK_CREATION_FAILED` thường đi kèm việc instance không trở thành active (file *Amazon ECS Managed Daemons*). Với **non-critical**, instance vẫn chạy app — event này là **tín hiệu duy nhất** để biết daemon đang thiếu trên instance đó, nên rất nên tạo rule cảnh báo (mục 4). Đây là suy luận ghép từ hai trang.

---

## 3. Daemon deployment state change events

**📘 Theo docs AWS**

- `detail-type`: **`ECS Daemon Deployment State Change`**.
- ECS phát event **mỗi khi trạng thái daemon deployment thay đổi**.

### 3.1 Các loại event

**📘 Theo docs AWS**

**INFO:**

| `eventName` | Ý nghĩa |
|---|---|
| `DAEMON_DEPLOYMENT_PENDING` | ECS đã **khởi tạo** daemon deployment |
| `DAEMON_DEPLOYMENT_IN_PROGRESS` | ECS đã **bắt đầu** và đang **thay daemon task** |
| `DAEMON_DEPLOYMENT_SUCCESSFUL` | Hoàn tất thành công, mọi daemon task **chạy và khoẻ** |
| `DAEMON_DEPLOYMENT_STOPPED` | ECS đã **dừng** deployment — do **thất bại** hoặc bị **deployment mới thay thế** |
| `DAEMON_DEPLOYMENT_STOP_REQUESTED` | ECS **ngừng tiến tiếp** và sắp bắt đầu **rollback** |
| `DAEMON_DEPLOYMENT_ROLLBACK_IN_PROGRESS` | ECS bắt đầu **rollback** do deployment lỗi hoặc **circuit breaker** kích hoạt |
| `DAEMON_DEPLOYMENT_ROLLBACK_SUCCESSFUL` | Rollback **hoàn tất thành công** |

**ERROR:**

| `eventName` | Ý nghĩa |
|---|---|
| `DAEMON_DEPLOYMENT_ROLLBACK_FAILED` | ECS **không hoàn tất được rollback** |

### 3.2 Các trường trong event (theo ví dụ trên docs)

**📘 Theo docs AWS**

Docs đưa ví dụ event **`DAEMON_DEPLOYMENT_PENDING`**. Các trường:

| Trường | Vị trí | Ý nghĩa |
|---|---|---|
| `detail-type` | gốc | `ECS Daemon Deployment State Change` |
| `source` | gốc | `aws.ecs` |
| `resources` | gốc | ARN daemon |
| `eventType` | `detail` | `INFO` (hoặc `ERROR`) |
| `eventName` | `detail` | Tên event (bảng 3.1) |
| `clusterArn` | `detail` | ARN cluster |
| `daemonArn` | `detail` | ARN daemon |
| `daemonDeploymentArn` | `detail` | ARN daemon deployment |
| `targetDaemonRevisionArn` | `detail` | ARN daemon revision **mục tiêu** |
| `updatedAt` | `detail` | Thời điểm cập nhật |

**💡 Giải thích thêm**

File *Daemon deployments* liệt kê 4 trạng thái deployment (`PENDING`, `IN_PROGRESS`, `SUCCESSFUL`, `STOPPED`). Event có thêm các bước **rollback**, cho thấy chi tiết hơn những gì xảy ra sau khi deployment bị dừng:

```
PENDING → IN_PROGRESS → SUCCESSFUL
                │
                └─ (lỗi / circuit breaker / alarm)
                     → STOP_REQUESTED
                     → ROLLBACK_IN_PROGRESS
                          ├─ ROLLBACK_SUCCESSFUL
                          └─ ROLLBACK_FAILED   ← ERROR, cần xử lý tay
         (bị deployment mới thay thế) → STOPPED
```

Thứ tự trong sơ đồ là mình sắp xếp từ ý nghĩa từng event; docs không vẽ luồng này.

**Event nên cảnh báo ngay:** `DAEMON_DEPLOYMENT_ROLLBACK_FAILED` (loại ERROR duy nhất) — daemon có thể ở trạng thái **lẫn revision cũ và mới** (file *Daemon deployments*).

---

## 4. Tạo rule cho daemon events

**📘 Theo docs AWS**

Tạo **EventBridge rule** để nhận thông báo khi có daemon event. (Xem *Creating a rule* — <https://docs.aws.amazon.com/eventbridge/latest/userguide/eb-create-rule.html>.)

Các mẫu lọc docs đưa ra:

| Mục đích | Điều kiện lọc |
|---|---|
| **Mọi** service action event | `source` = `aws.ecs`; `detail-type` = `ECS Daemon Service Action` |
| **Một loại lỗi** cụ thể | Như trên + `detail.eventName` = `DAEMON_TASK_START_IMPAIRED` + `detail.failureType` = `TASK_CREATION_FAILED` |
| Event của **một cluster** | `detail-type` = `ECS Daemon Service Action` + `detail.eventName` = `DAEMON_TASK_START_IMPAIRED` + `detail.clusterArn` = ARN cluster |
| **Mọi** deployment state change event | `source` = `aws.ecs`; `detail-type` = `ECS Daemon Deployment State Change` |

Ví dụ event pattern (viết lại theo mẫu thứ hai):

```json
{
  "source": ["aws.ecs"],
  "detail-type": ["ECS Daemon Service Action"],
  "detail": {
    "eventName": ["DAEMON_TASK_START_IMPAIRED"],
    "failureType": ["TASK_CREATION_FAILED"]
  }
}
```

**💡 Giải thích thêm**

Mẫu chỉ bắt **rollback thất bại** (tự viết theo cùng cú pháp, dựa trên tên event ở mục 3):

```json
{
  "source": ["aws.ecs"],
  "detail-type": ["ECS Daemon Deployment State Change"],
  "detail": { "eventName": ["DAEMON_DEPLOYMENT_ROLLBACK_FAILED"] }
}
```

Gợi ý bộ rule tối thiểu (của mình):

| Rule | Lọc | Đích gợi ý |
|---|---|---|
| Daemon không khởi động | `ECS Daemon Service Action` + `DAEMON_TASK_START_IMPAIRED` | SNS → email/Slack |
| Rollback | `ECS Daemon Deployment State Change` + `DAEMON_DEPLOYMENT_ROLLBACK_IN_PROGRESS` | SNS |
| Rollback thất bại | `... State Change` + `DAEMON_DEPLOYMENT_ROLLBACK_FAILED` | SNS (ưu tiên cao) |
| Theo dõi tiến độ | `... State Change` (tất cả) | CloudWatch Logs để tra cứu |

Terraform tương ứng dùng `aws_cloudwatch_event_rule` (với `event_pattern`) và `aws_cloudwatch_event_target` — tên resource là bổ sung của mình, trang không đề cập.

---

## 5. Liên hệ với các file trước

**💡 Giải thích thêm**

| Nội dung | File liên quan |
|---|---|
| ECS phát EventBridge event + service action log khi daemon không khởi động | *Amazon ECS Managed Daemons* |
| Trạng thái deployment, circuit breaker, rollback | *Daemon deployments* |
| Pull image lỗi | *Container image pull behavior*, *task networking* |
| Thiếu CPU/memory | *Best practices for task sizes*, *Daemon task definitions* (`cpu`, `memory`) |
| Health check container | *Daemon task definitions* (`healthCheck`), *task definition parameters* |
| EventBridge cho ECS nói chung | *Fargate task definition differences* (event logging) |

---

## 6. Tóm tắt

- ECS gửi daemon events tới EventBridge để giám sát sức khoẻ daemon, theo dõi deployment, tự động hoá phản ứng.
- **`ECS Daemon Service Action`** → **`DAEMON_TASK_START_IMPAIRED`** (eventType `WARNING`), cho cả critical và non-critical:
  - `TASK_FAILED_TO_RUN`: task đã tạo nhưng không `RUNNING` (pull image, health check, container essential thoát) — có `taskArn`;
  - `TASK_CREATION_FAILED`: không tạo được task (thiếu CPU/memory…) — không có `taskArn`.
- **`ECS Daemon Deployment State Change`**: INFO — `PENDING`, `IN_PROGRESS`, `SUCCESSFUL`, `STOPPED`, `STOP_REQUESTED`, `ROLLBACK_IN_PROGRESS`, `ROLLBACK_SUCCESSFUL`; ERROR — `ROLLBACK_FAILED`.
- Event deployment có `daemonDeploymentArn`, `targetDaemonRevisionArn`; event service action có `containerInstanceArn`, `daemonRevisionArn`, `capacityProviderName`, `failureType`, `reason`.
- Tạo **EventBridge rule** lọc theo `source`, `detail-type`, `detail.eventName`, `detail.failureType`, `detail.clusterArn`.

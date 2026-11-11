# Using Amazon EventBridge Scheduler to schedule Amazon ECS tasks

> Nguồn: **Amazon ECS Developer Guide — Using Amazon EventBridge Scheduler to schedule Amazon ECS tasks**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/tasks-scheduled-eventbridge-scheduler.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: diễn đạt lại bằng tiếng Việt, bám sát từng bước và đúng thứ tự của trang gốc, không thêm thông tin ngoài trang. Đây không phải bản dịch từng chữ; khi cần câu chữ chính xác, đọc link gốc ở trên.
> - Khối **💡 Giải thích thêm**: phần giải thích để dễ hiểu hơn, chỉ làm rõ những gì docs đã nói.
> - Tên nút, tên trường trên console giữ nguyên tiếng Anh (in đậm).

---

## Mục lục

1. [EventBridge Scheduler là gì](#1-eventbridge-scheduler-là-gì)
2. [Chuẩn bị trước khi tạo lịch](#2-chuẩn-bị-trước-khi-tạo-lịch)
3. [Bước 1 — Thông tin lịch](#3-bước-1--thông-tin-lịch)
4. [Bước 2 — Kiểu lịch và khung thời gian](#4-bước-2--kiểu-lịch-và-khung-thời-gian)
5. [Bước 3 — Chọn target (ECS RunTask)](#5-bước-3--chọn-target-ecs-runtask)
6. [Bước 4 — Settings (trạng thái, retry, DLQ, mã hoá, quyền)](#6-bước-4--settings-trạng-thái-retry-dlq-mã-hoá-quyền)
7. [Bước 5 — Review và tạo](#7-bước-5--review-và-tạo)
8. [Bước tiếp theo](#8-bước-tiếp-theo)
9. [Ví dụ CLI và Terraform](#9-ví-dụ-cli-và-terraform)
10. [Tóm tắt](#10-tóm-tắt)

---

## 1. EventBridge Scheduler là gì

**📘 Theo docs AWS**

- **EventBridge Scheduler** là scheduler **serverless**, cho phép tạo, chạy và quản lý tác vụ từ **một dịch vụ quản lý tập trung**.
- Cung cấp lịch **một lần** và **định kỳ**, **không phụ thuộc** event bus và rule.
- Tuỳ biến cao, **scale tốt hơn** so với **scheduled rule của EventBridge**, hỗ trợ nhiều API đích và dịch vụ AWS hơn.
- Các kiểu lịch cấu hình được trong console EventBridge Scheduler:
  - **Rate-based** (theo tần suất);
  - **Cron-based** — cấu hình được ở **bất kỳ múi giờ** nào;
  - **One-time** (một lần) — cấu hình được ở **bất kỳ múi giờ** nào.
- Dù có thể tạo scheduled task trong console ECS, **hiện tại console EventBridge Scheduler có nhiều chức năng hơn**.

**💡 Giải thích thêm**

So với cách cũ (EventBridge **rule** có lịch), Scheduler hỗ trợ **múi giờ** cho cron. Với người ở Việt Nam, có thể đặt lịch theo `Asia/Ho_Chi_Minh` thay vì tự quy đổi sang UTC — ví dụ múi giờ là của mình; docs chỉ nói "bất kỳ múi giờ".

---

## 2. Chuẩn bị trước khi tạo lịch

**📘 Theo docs AWS**

1. Dùng **VPC console** để lấy **subnet ID** nơi task chạy và **security group ID** cho các subnet đó. (Xem *Subnets for your VPC* — <https://docs.aws.amazon.com/vpc/latest/userguide/configure-subnets.html> và *Control traffic to your AWS resources using security groups* — <https://docs.aws.amazon.com/vpc/latest/userguide/vpc-security-groups.html>.)
2. Cấu hình **execution role của EventBridge Scheduler**. (Xem *Set up the execution role* — <https://docs.aws.amazon.com/scheduler/latest/UserGuide/setting-up.html#setting-up-execution-role>.)
3. Muốn chạy task bằng **capacity provider strategy** thì cluster phải có **capacity provider đã được gắn**.

**💡 Giải thích thêm**

Execution role này là role **của EventBridge Scheduler** (để Scheduler gọi `ecs:RunTask`), khác với task role và task execution role của ECS. Thông thường role cần quyền `ecs:RunTask` và `iam:PassRole` cho các role mà task definition dùng — chi tiết quyền nằm ở link *Set up the execution role*, trang này không liệt kê.

```
EventBridge Scheduler ──(execution role của Scheduler)──► ecs:RunTask
                                                             │
                                          ECS khởi chạy task với task role / task execution role
```

---

## 3. Bước 1 — Thông tin lịch

**📘 Theo docs AWS**

1. Mở console EventBridge Scheduler: <https://console.aws.amazon.com/scheduler/home>.
2. Trang **Schedules** → **Create schedule**.
3. Trang **Specify schedule detail**, phần **Schedule name and description**:
   - **Schedule name**: tên lịch (ví dụ docs: `MyTestSchedule`).
   - **Description** (tuỳ chọn).
   - **Schedule group**: chọn nhóm lịch; chưa có thì chọn **default**; muốn tạo nhóm mới thì chọn **create your own schedule**. Schedule group dùng để **gắn tag cho nhóm lịch**.

---

## 4. Bước 2 — Kiểu lịch và khung thời gian

**📘 Theo docs AWS**

4. Chọn kiểu lịch:

| Kiểu | Ý nghĩa | Cấu hình |
|---|---|---|
| **One-time schedule** | Gọi target **đúng một lần** vào ngày giờ chỉ định | **Date and time**: ngày dạng `YYYY/MM/DD`, giờ dạng 24h `hh:mm`; **Timezone**: chọn múi giờ |
| **Recurring schedule** | Gọi target **định kỳ** theo biểu thức **cron** hoặc **rate** | **Schedule type**: **Cron-based schedule** (nhập cron expression) hoặc **Rate-based schedule** (nhập rate expression). (Xem *Schedule types on EventBridge Scheduler* — <https://docs.aws.amazon.com/scheduler/latest/UserGuide/schedule-types.html#cron-based>.) **Flexible time window**: **Off** hoặc một khung thời gian có sẵn. Ví dụ docs: chọn **15 minutes** cho lịch mỗi giờ → lịch chạy trong vòng 15 phút sau đầu mỗi giờ. |

5. (Tuỳ chọn, chỉ với **Recurring schedule**) phần **Timeframe**:
   - **Timezone**: múi giờ;
   - **Start date and time**: ngày `YYYY/MM/DD` + giờ `hh:mm`;
   - **End date and time**: ngày `YYYY/MM/DD` + giờ `hh:mm`.
6. Chọn **Next**.

**💡 Giải thích thêm**

Ví dụ biểu thức (do mình đặt; cú pháp chi tiết xem link *Schedule types*):

| Nhu cầu | Biểu thức |
|---|---|
| Mỗi 15 phút | `rate(15 minutes)` |
| Mỗi ngày lúc 2:00 sáng | `cron(0 2 * * ? *)` |
| Thứ Hai–Thứ Sáu lúc 8:30 | `cron(30 8 ? * MON-FRI *)` |
| Một lần | `at(2026-11-01T02:00:00)` |

**Flexible time window**: cho phép Scheduler chạy lệch trong một khoảng thay vì đúng giờ — hữu ích khi nhiều lịch cùng chạy một thời điểm và không cần chính xác tuyệt đối. Job cần đúng giờ thì để **Off**.

**Timeframe**: dùng để giới hạn lịch chỉ hoạt động trong một khoảng (vd chạy báo cáo hằng ngày trong một học kỳ).

---

## 5. Bước 3 — Chọn target (ECS RunTask)

**📘 Theo docs AWS**

7. Trang **Select target**:
   1. Chọn **All APIs**, gõ **ECS** vào ô tìm kiếm.
   2. Chọn **Amazon ECS**.
   3. Gõ **RunTask** và chọn **RunTask**.
   4. **ECS cluster**: chọn cluster.
   5. **ECS task**: chọn task definition.
   6. Mở **Compute options** và chọn:

| Compute option | Các bước |
|---|---|
| **Capacity provider strategy** | **Use cluster default**, hoặc **Use custom** rồi nhập **Base**, **Capacity provider**, **Weight**. Với EC2, capacity provider là **Auto Scaling group**. |
| **Launch type** | Chọn **Launch type**; nếu là Fargate thì chỉ định **Platform version**. |

   7. **Subnets**: nhập subnet ID để chạy task.
   8. **Security groups**: nhập security group ID cho subnet.
   9. (Tuỳ chọn) Dùng placement khác mặc định: mở **Placement constraint** và nhập constraint. (Xem *How Amazon ECS places tasks on container instances* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task-placement.html>.)
   10. (Tuỳ chọn) **Tags**: cấu hình tag; chọn **Enable Amazon ECS managed tags** để ECS tự gắn tag của task definition lên mọi task mới.
8. Chọn **Next**.

**💡 Giải thích thêm**

- Target ở đây chính là API **`RunTask`** — lịch chỉ là cách tự động gọi `RunTask` (file *Schedule your containers*, *Running an application as a task*). Mọi kiến thức về standalone task (capacity, placement, networking) áp dụng nguyên vẹn.
- Docs nói "dùng placement strategy khác mặc định" nhưng mục trên console lại tên **Placement constraint** — có vẻ lệch chữ trên trang.
- Không thấy bước bật **Public IP** trong hướng dẫn: nếu task Fargate chạy ở public subnet không có IP public thì sẽ không pull được image (file *task networking for Fargate*) — nên chạy ở private subnet có NAT hoặc VPC endpoint. Đây là suy luận ghép từ các file trước.

---

## 6. Bước 4 — Settings (trạng thái, retry, DLQ, mã hoá, quyền)

**📘 Theo docs AWS**

9. Trang **Settings**:
   1. **Schedule state**: bật **Enable schedule**.
   2. **Retry policy and dead-letter queue (DLQ)**:
      - bật **Retry**;
      - **Maximum retention time of event**: số giờ/phút tối đa Scheduler giữ một event chưa xử lý — **tối đa 24 giờ**;
      - **Maximum retries**: số lần thử lại tối đa khi target trả lỗi — **tối đa 185**.
      - Có retry policy thì khi lịch gọi target thất bại, Scheduler **chạy lại**; nếu bật thì phải đặt cả thời gian giữ tối đa và số lần retry.
   3. Nơi lưu event **không gửi được**:

| Lựa chọn DLQ | Cách làm |
|---|---|
| Không lưu | Chọn **None** |
| Lưu trong **cùng account** | **Select an Amazon SQS queue in my AWS account as a DLQ** → chọn ARN của SQS queue |
| Lưu ở **account khác** | **Specify an Amazon SQS queue in other AWS accounts as a DLQ** → nhập ARN của SQS queue |

   4. **Encryption**: muốn dùng **customer managed key** để mã hoá input của target thì chọn **Customize encryption settings (advanced)**, nhập ARN KMS key có sẵn hoặc **Create an AWS KMS key**. (Xem *Encryption at rest* — <https://docs.aws.amazon.com/scheduler/latest/UserGuide/encryption-rest.html>.)
   5. **Permissions**: **Use existing role** rồi chọn role; hoặc **Create new role for this schedule** và nhập **Role name** — khi đó Scheduler tự gắn quyền cần cho target đã chọn.
10. Chọn **Next**.

**💡 Giải thích thêm**

| Thiết lập | Ý nghĩa thực tế |
|---|---|
| Retry | Xử lý lỗi **khi gọi `RunTask`** (vd thiếu quyền tạm thời, throttle). Không phải retry khi **task chạy rồi bị lỗi** — đó là chuyện của ứng dụng / ECS. (suy luận từ mô tả "target trả lỗi") |
| DLQ (SQS) | Giữ lại event không gửi được để điều tra hoặc xử lý lại; nên bật cho job quan trọng |
| Create new role | Nhanh để thử; production nên dùng role có sẵn, quản lý bằng IaC |

---

## 7. Bước 5 — Review và tạo

**📘 Theo docs AWS**

11. Trang **Review and create schedule**: xem lại; chọn **Edit** ở từng phần để sửa.
12. Chọn **Create schedule**. Xem danh sách lịch ở trang **Schedules**; kiểm tra cột **Status** của lịch mới là **Enabled**.

---

## 8. Bước tiếp theo

**📘 Theo docs AWS**

Quản lý lịch bằng **console EventBridge Scheduler** hoặc **AWS CLI**. (Xem *Managing a schedule* — <https://docs.aws.amazon.com/scheduler/latest/UserGuide/managing-schedule.html>.)

---

## 9. Ví dụ CLI và Terraform

**💡 Giải thích thêm**

Trang docs chỉ hướng dẫn console. Phần này là mình viết thêm để tự động hoá; tên tham số và resource lấy theo AWS CLI / Terraform AWS provider, nên kiểm tra lại với phiên bản bạn dùng.

**AWS CLI:**

```bash
aws scheduler create-schedule \
  --name nightly-report \
  --schedule-expression "cron(0 2 * * ? *)" \
  --schedule-expression-timezone "Asia/Ho_Chi_Minh" \
  --flexible-time-window Mode=OFF \
  --target '{
    "Arn": "arn:aws:ecs:<REGION>:<ACCOUNT_ID>:cluster/<CLUSTER>",
    "RoleArn": "arn:aws:iam::<ACCOUNT_ID>:role/<SCHEDULER_ROLE>",
    "EcsParameters": {
      "TaskDefinitionArn": "arn:aws:ecs:<REGION>:<ACCOUNT_ID>:task-definition/<FAMILY>",
      "LaunchType": "FARGATE",
      "NetworkConfiguration": {
        "awsvpcConfiguration": {
          "Subnets": ["<SUBNET_ID>"],
          "SecurityGroups": ["<SG_ID>"],
          "AssignPublicIp": "DISABLED"
        }
      }
    },
    "RetryPolicy": { "MaximumRetryAttempts": 3, "MaximumEventAgeInSeconds": 3600 }
  }'
```

**Terraform:**

```hcl
resource "aws_scheduler_schedule" "nightly_report" {
  name                         = "nightly-report"
  schedule_expression          = "cron(0 2 * * ? *)"
  schedule_expression_timezone = "Asia/Ho_Chi_Minh"

  flexible_time_window {
    mode = "OFF"
  }

  target {
    arn      = aws_ecs_cluster.main.arn
    role_arn = aws_iam_role.scheduler.arn

    ecs_parameters {
      task_definition_arn = aws_ecs_task_definition.report.arn
      launch_type         = "FARGATE"

      network_configuration {
        subnets          = var.private_subnet_ids
        security_groups  = [aws_security_group.task.id]
        assign_public_ip = false
      }
    }

    retry_policy {
      maximum_retry_attempts       = 3
      maximum_event_age_in_seconds = 3600
    }
  }
}
```

Lưu ý: `task_definition_arn` không kèm revision → Scheduler dùng revision mới nhất của family (theo hành vi chung của ECS `RunTask` khi chỉ đưa family); muốn ghim revision thì thêm `:<REVISION>`. Đây là suy luận, trang docs không nói.

---

## 10. Tóm tắt

- **EventBridge Scheduler**: scheduler serverless, lịch một lần hoặc định kỳ, không phụ thuộc event bus/rule; scale tốt hơn scheduled rule; console Scheduler có nhiều chức năng hơn console ECS.
- Kiểu lịch: **rate**, **cron** (mọi múi giờ), **one-time** (mọi múi giờ).
- **Chuẩn bị**: subnet ID + security group ID; execution role của Scheduler; capacity provider đã gắn cluster (nếu dùng strategy).
- **Tạo lịch**: tên, mô tả, schedule group → one-time (ngày, giờ 24h, múi giờ) hoặc recurring (cron/rate, flexible time window, timeframe) → target **Amazon ECS → RunTask** (cluster, task definition, capacity provider strategy hoặc launch type, subnet, SG, placement constraint, tag) → settings (enable, retry ≤ 24 giờ & ≤ 185 lần, DLQ SQS cùng/khác account, KMS key, execution role có sẵn hoặc tạo mới) → review → **Create schedule**; kiểm tra Status **Enabled**.
- Quản lý lịch bằng console hoặc AWS CLI.

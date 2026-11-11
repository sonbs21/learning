# Amazon ECS standalone tasks

> Nguồn: **Amazon ECS Developer Guide — Amazon ECS standalone tasks**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/standalone-tasks.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: diễn đạt lại bằng tiếng Việt, bám sát từng ý và đúng thứ tự của trang gốc, không thêm thông tin ngoài trang. Đây không phải bản dịch từng chữ; khi cần câu chữ chính xác, đọc link gốc ở trên.
> - Khối **💡 Giải thích thêm**: phần giải thích để dễ hiểu hơn, chỉ làm rõ những gì docs đã nói.

---

## Mục lục

1. [Khi nào dùng standalone task](#1-khi-nào-dùng-standalone-task)
2. [Luồng xử lý của task](#2-luồng-xử-lý-của-task)
   - [2.1 Bắt đầu ở `PROVISIONING`](#21-bắt-đầu-ở-provisioning)
   - [2.2 Chọn compute capacity](#22-chọn-compute-capacity)
   - [2.3 Khi thiếu capacity và khi đã có capacity](#23-khi-thiếu-capacity-và-khi-đã-có-capacity)
3. [Liên hệ với các file trước](#3-liên-hệ-với-các-file-trước)
4. [Tóm tắt](#4-tóm-tắt)

---

## 1. Khi nào dùng standalone task

**📘 Theo docs AWS**

- Chạy ứng dụng dưới dạng **task** khi ứng dụng **làm một việc rồi dừng**, ví dụ một **batch process**.
- Muốn chạy task **một lần**: dùng **console**, **AWS CLI**, **API** hoặc **SDK**.
- Muốn chạy theo **lịch** — theo **tần suất (rate)**, theo **cron**, hoặc **một lần vào thời điểm định trước** — thì tạo lịch bằng **EventBridge Scheduler**.

**💡 Giải thích thêm**

Khớp với file *Schedule your containers*: standalone task (gọi `RunTask`) và scheduled task (EventBridge Scheduler) đều thuộc nhóm "chạy xong rồi dừng", khác với service chạy lâu dài.

Ví dụ chạy một lần bằng CLI (lệnh là phần mình bổ sung; trang này chỉ nói "dùng CLI"):

```bash
aws ecs run-task \
  --cluster <CLUSTER> \
  --task-definition <FAMILY>:<REVISION> \
  --capacity-provider-strategy capacityProvider=FARGATE,weight=1 \
  --network-configuration 'awsvpcConfiguration={subnets=[<SUBNET_ID>],securityGroups=[<SG_ID>]}'
```

---

## 2. Luồng xử lý của task

### 2.1 Bắt đầu ở `PROVISIONING`

**📘 Theo docs AWS**

- Khi launch task — dù là **standalone** hay **do service** tạo — task được tạo và **ban đầu** chuyển sang trạng thái **`PROVISIONING`**.
- Ở `PROVISIONING`, **cả task lẫn container đều chưa tồn tại**, vì ECS còn phải **tìm compute capacity** để đặt task.

### 2.2 Chọn compute capacity

**📘 Theo docs AWS**

- ECS chọn compute capacity phù hợp dựa trên **launch type** hoặc **cấu hình capacity provider**.
- Capacity provider và capacity provider strategy dùng được với cả **Fargate** và **EC2**.
- **Fargate**: không cần lo việc cấp phát, cấu hình hay scale capacity của cluster — Fargate quản lý toàn bộ hạ tầng cho task.
- **EC2**: hai cách quản lý capacity:
  - tự **đăng ký EC2 instance** vào cluster; hoặc
  - dùng **cluster auto scaling** để đơn giản hoá — nó tự **scale capacity** động để bạn tập trung chạy task.
- ECS quyết định đặt task ở đâu dựa trên **yêu cầu trong task definition** (CPU, memory…) cùng **placement constraint** và **strategy**. (Xem *How Amazon ECS places tasks on container instances* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task-placement.html>.)

### 2.3 Khi thiếu capacity và khi đã có capacity

**📘 Theo docs AWS**

- Nếu dùng capacity provider **bật managed scaling**, task **không khởi động được vì thiếu compute capacity** sẽ được chuyển vào **`PROVISIONING`**, **thay vì thất bại ngay**.
- Sau khi tìm được capacity:
  1. ECS cấp phát các **attachment** cần thiết (ví dụ **ENI** cho task mode `awsvpc`);
  2. dùng **ECS container agent** để **pull container image**;
  3. **khởi động container**.
- Khi cấp phát xong và container đã chạy, ECS chuyển task sang **`RUNNING`**. (Xem *Amazon ECS task lifecycle* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task-lifecycle-explanation.html>.)

**💡 Giải thích thêm**

```
RunTask / service tạo task
        │
        ▼
 PROVISIONING   ← task & container chưa tồn tại; ECS tìm capacity
        │          (thiếu capacity + managed scaling → chờ ở đây, không fail ngay)
        │
   tìm được capacity (launch type / capacity provider, placement constraint & strategy)
        │
        ├─ cấp phát attachment (vd ENI cho awsvpc)
        ├─ agent pull image
        └─ khởi động container
        │
        ▼
     RUNNING
```

| Có managed scaling? | Thiếu capacity thì |
|---|---|
| Có | Task chờ ở `PROVISIONING` trong lúc capacity provider scale thêm |
| Không | Docs trang này không nói rõ; theo câu "thay vì thất bại ngay", hiểu là task có thể **thất bại** (suy luận) |

---

## 3. Liên hệ với các file trước

**💡 Giải thích thêm**

| Nội dung | File liên quan |
|---|---|
| Standalone task vs service vs scheduled task | *Schedule your containers* |
| Capacity provider strategy, base, weight | *Schedule your containers* |
| Placement constraint / strategy | *How Amazon ECS places tasks*, *Use strategies*, *Define which container instances…* |
| Standalone task mặc định phân bổ ngẫu nhiên, task group mặc định là `family:...` | *Schedule your containers*, *Group related Amazon ECS tasks* |
| ENI cho `awsvpc` | *task networking* (EC2, Fargate, Managed Instances) |
| Pull image | *Container image pull behavior* |
| Trạng thái task | *Task lifecycle* |

**Chỗ không khớp nhẹ giữa các trang:**
- File *Task lifecycle* mô tả `PROVISIONING` là bước chuẩn bị trước khi launch (vd tạo ENI), còn **chờ tài nguyên** là việc của **`PENDING`**.
- Trang này lại nói `PROVISIONING` là lúc **tìm compute capacity**, và task thiếu capacity (có managed scaling) **chờ ở `PROVISIONING`**; còn việc tạo ENI xảy ra **sau** khi đã tìm được capacity.

Hai trang mô tả ranh giới giữa `PROVISIONING` và `PENDING` hơi khác nhau. Khi debug, coi cả hai trạng thái này là "chưa có đủ điều kiện để chạy" và kiểm tra capacity, ENI/subnet, constraint.

---

## 4. Tóm tắt

- Dùng standalone task cho ứng dụng **làm việc rồi dừng** (batch); chạy một lần qua console, CLI, API, SDK.
- Chạy theo lịch (rate, cron, một lần) → **EventBridge Scheduler**.
- Mọi task (standalone hay service) bắt đầu ở **`PROVISIONING`** — lúc đó task và container chưa tồn tại, ECS đang tìm capacity.
- ECS chọn capacity theo **launch type** hoặc **capacity provider** (Fargate hoặc EC2); EC2 có thể tự đăng ký instance hoặc dùng **cluster auto scaling**.
- Vị trí đặt task phụ thuộc yêu cầu task definition (CPU, memory) và placement constraint/strategy.
- Có **managed scaling**: thiếu capacity thì task chờ ở `PROVISIONING` thay vì fail ngay.
- Có capacity → cấp phát attachment (vd ENI) → agent pull image → chạy container → **`RUNNING`**.

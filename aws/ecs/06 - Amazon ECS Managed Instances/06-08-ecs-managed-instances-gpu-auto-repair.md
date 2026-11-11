# GPU auto repair for Amazon ECS managed instances

> Nguồn: **Amazon ECS Developer Guide — GPU auto repair for Amazon ECS managed instances**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/managed-instances-gpu-auto-repair.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: diễn đạt lại bằng tiếng Việt, bám sát từng ý và đúng thứ tự của trang gốc, không thêm thông tin ngoài trang. Đây không phải bản dịch từng chữ; khi cần câu chữ hoặc ví dụ lệnh chính xác, đọc link gốc ở trên.
> - Khối **💡 Giải thích thêm**: phần giải thích để dễ hiểu hơn, chỉ làm rõ những gì docs đã nói.
> - Lệnh CLI dùng placeholder (`<...>`); tên lệnh, tham số, trường và mã lỗi giữ đúng như docs.

---

## Mục lục

0. [GPU auto repair là gì](#0-gpu-auto-repair-là-gì)
1. [Cách hoạt động](#1-cách-hoạt-động)
2. [Giới hạn tốc độ sửa chữa](#2-giới-hạn-tốc-độ-sửa-chữa)
3. [Giám sát sức khoẻ GPU](#3-giám-sát-sức-khoẻ-gpu)
4. [Mã lỗi Xid được theo dõi](#4-mã-lỗi-xid-được-theo-dõi)
5. [Bật / tắt auto repair](#5-bật--tắt-auto-repair)
6. [Liên hệ với các file trước](#6-liên-hệ-với-các-file-trước)
7. [Tóm tắt](#7-tóm-tắt)

---

## 0. GPU auto repair là gì

**📘 Theo docs AWS**

- ECS **theo dõi sức khoẻ GPU NVIDIA** trên các Managed Instances có phần cứng GPU.
- Khi phát hiện **lỗi phần cứng GPU**, ECS có thể **tự thay** instance bị suy giảm.
- GPU auto repair **bật mặc định** cho Managed Instances.

---

## 1. Cách hoạt động

**📘 Theo docs AWS**

- ECS dùng **NVIDIA Data Center GPU Manager (DCGM)** để theo dõi sức khoẻ GPU trên instance có GPU.
- Khi DCGM báo **lỗi GPU nghiêm trọng**, ECS đánh dấu instance là **impaired** (bị suy giảm).
- Khi auto repair đang bật, ECS thay instance impaired theo quy trình **start-before-stop**:
  1. Đặt instance impaired sang **`DRAINING`** → không đặt task mới lên đó.
  2. **Cấp instance thay thế**.
  3. Cho task hiện có **dừng êm**, tôn trọng **stop timeout** của task trên instance.
  4. Hết thời gian drain → **terminate** instance impaired.

**💡 Giải thích thêm**

```
DCGM phát hiện lỗi GPU nghiêm trọng
   → instance = impaired
   → DRAINING (không nhận task mới)
   → cấp instance GPU mới                 ← "start" trước
   → task cũ dừng êm (theo stopTimeout)    ← rồi mới "stop"
   → terminate instance hỏng
```

Task của **service** sẽ được đặt lại lên instance mới theo deployment configuration; **standalone task** thì không được thay (file *Managed Instances infrastructure optimization*) — với job GPU dài (vd training), nên có cơ chế checkpoint để chạy lại. Phần này là suy luận/gợi ý của mình.

`stopTimeout` trên Managed Instances tối đa 86400 giây (file *task definition parameters for Managed Instances*) — đặt đủ để job GPU lưu trạng thái khi nhận `SIGTERM`.

---

## 2. Giới hạn tốc độ sửa chữa

**📘 Theo docs AWS**

ECS **giới hạn tốc độ** sửa chữa để tránh **thay hàng loạt dây chuyền**:
- Tối đa **20%** instance của capacity provider được drain **cùng lúc**.
- Capacity provider có **ít hơn 9** instance → tối đa **1** instance được drain mỗi lần.

**💡 Giải thích thêm**

| Số instance trong capacity provider | Drain tối đa cùng lúc |
|---|---|
| 1–8 | 1 |
| 10 | 2 (20%) |
| 20 | 4 (20%) |
| 50 | 10 (20%) |

Số 10, 20, 50 là mình tính theo quy tắc 20%; trang không nói cách làm tròn khi có đúng 9 instance hoặc khi 20% ra số lẻ.

---

## 3. Giám sát sức khoẻ GPU

**📘 Theo docs AWS**

- Dùng API **`DescribeContainerInstances`** để kiểm tra sức khoẻ GPU. (Xem *Monitor Amazon ECS container instance health* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/container-instance-health.html>.)
- Theo dõi thay đổi sức khoẻ GPU qua **container instance health change events** (EventBridge). (Xem <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/ecs_container_instance_health_events.html>.)

**💡 Giải thích thêm**

```bash
aws ecs describe-container-instances --cluster <CLUSTER> --container-instances <CONTAINER_INSTANCE_ARN>
```

Có thể tạo EventBridge rule cho health change events để cảnh báo khi GPU hỏng — tương tự cách làm ở file *Daemon events*. Lệnh và gợi ý là bổ sung của mình.

---

## 4. Mã lỗi Xid được theo dõi

**📘 Theo docs AWS**

ECS theo dõi các mã lỗi **NVIDIA Xid** sau. Gặp bất kỳ mã nào, ECS đánh dấu instance **impaired** và **thay** instance.

| Xid | Ý nghĩa |
|---|---|
| 46 | GPU ngừng xử lý |
| 48 | Lỗi ECC hai bit (Double Bit ECC) |
| 54 | Đầu nối nguồn phụ không được cắm |
| 62 | Vi điều khiển nội bộ bị dừng |
| 64 | Lỗi ánh xạ lại bộ nhớ GPU |
| 74 | Lỗi NVLink |
| 79 | GPU rơi khỏi bus |
| 95 | Lỗi bộ nhớ không khoanh vùng được |
| 109 | Hết thời gian chuyển ngữ cảnh (context switch timeout) |
| 110 | GPU biến mất khỏi bus |
| 136 | Vượt giới hạn số trang bộ nhớ GPU bị loại bỏ |
| 140 | Lỗi ECC không phục hồi được |
| 142 | Trang bộ nhớ GPU bị loại bỏ do lỗi không sửa được |
| 143 | Trang bộ nhớ GPU bị loại bỏ do vượt ngưỡng lỗi sửa được |
| 151 | Lỗi kết nối GPU – CPU |
| 155 | Lỗi CRC flit trên NVLink |
| 156 | Lỗi lane NVLink |
| 158 | InfoROM của GPU bị hỏng |

Tìm hiểu thêm về Xid: *Xid Errors* (NVIDIA) — <https://docs.nvidia.com/deploy/xid-errors/index.html#topic_5_1>, *Understanding Xid Messages* — <https://docs.nvidia.com/deploy/gpu-debug-guidelines/index.html#understanding-xid-messages>.

**💡 Giải thích thêm**

Nhóm lỗi (cách nhóm là của mình):

| Nhóm | Xid |
|---|---|
| Bộ nhớ / ECC | 48, 64, 95, 136, 140, 142, 143 |
| GPU mất kết nối / treo | 46, 62, 79, 109, 110 |
| NVLink / interconnect | 74, 151, 155, 156 |
| Phần cứng / nguồn / firmware | 54, 158 |

Các lỗi này là lỗi **phần cứng** — ứng dụng không tự sửa được, nên thay instance là cách xử lý hợp lý.

---

## 5. Bật / tắt auto repair

**📘 Theo docs AWS**

- GPU auto repair **bật mặc định**.
- Tắt: đặt **`actionsStatus`** = **`DISABLED`** trong **`autoRepairConfiguration`** khi tạo hoặc cập nhật capacity provider. Cũng tắt được trên **console** khi tạo/cập nhật capacity provider.
- Khi tắt, ECS **vẫn theo dõi** sức khoẻ GPU, nhưng **không tự thay** instance impaired.

> **Note (docs):** **Tắt GPU auto repair cũng tắt auto repair của Managed Daemons.** (Xem *Amazon ECS Managed Daemons auto repair* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/managed-daemons-auto-repair.html>.)

**Tắt** (bật thì đổi `DISABLED` thành `ENABLED`):

```bash
aws ecs update-capacity-provider \
  --name <CAPACITY_PROVIDER_NAME> \
  --managed-instances-provider '{
    "infrastructureRoleArn": "<INFRA_ROLE_ARN>",
    "instanceLaunchTemplate": {
      "ec2InstanceProfileArn": "<INSTANCE_PROFILE_ARN>",
      "networkConfiguration": { "subnets": ["<SUBNET_ID>"], "securityGroups": ["<SG_ID>"] }
    },
    "autoRepairConfiguration": { "actionsStatus": "DISABLED" }
  }'
```

**Kiểm tra cấu hình:**

```bash
aws ecs describe-capacity-providers --capacity-providers <CAPACITY_PROVIDER_NAME>
```

**💡 Giải thích thêm**

- `autoRepairConfiguration` nằm ở **mức capacity provider** → áp dụng cho **mọi instance** của capacity provider đó, ảnh hưởng **cả GPU lẫn daemon** (theo Note).
- Ví dụ `update-capacity-provider` của docs truyền **lại đầy đủ** `infrastructureRoleArn`, `instanceLaunchTemplate`… cùng với `autoRepairConfiguration`. Giống bài học `UpdateDaemon` / `UpdateGateway` ở các file trước, nên truyền đủ cấu hình hiện tại khi update — suy luận từ cách docs viết ví dụ, trang không nói rõ thiết lập bỏ sót có bị reset không.

| Khi nào cân nhắc tắt (gợi ý của mình) | Đánh đổi |
|---|---|
| Đang debug lỗi GPU, muốn giữ instance để điều tra | Instance hỏng không tự được thay; daemon auto repair cũng tắt |
| Job không chịu được việc bị dừng giữa chừng | Task chạy trên GPU hỏng có thể lỗi hoặc cho kết quả sai |

---

## 6. Liên hệ với các file trước

**💡 Giải thích thêm**

| Nội dung | File liên quan |
|---|---|
| Instance GPU (G, P, Inf, Trn…), GPU phân đoạn G6f | *Managed Instances instance types*, *task definition parameters for Managed Instances* |
| Daemon auto repair cùng bị tắt | *Daemon auto repair* |
| `DRAINING`, `DEREGISTERING`, `UpdateContainerInstancesState` | *Patching*, *Infrastructure optimization* |
| Start-before-stop, stop timeout | *Task lifecycle*, *Instance selection best practices* |
| Event-driven monitoring | *Daemon events*, *Infrastructure optimization* |

---

## 7. Tóm tắt

- ECS theo dõi sức khoẻ **GPU NVIDIA** trên Managed Instances bằng **DCGM**; GPU auto repair **bật mặc định**.
- Lỗi GPU nghiêm trọng → instance **impaired** → **start-before-stop**: `DRAINING` (không nhận task mới) → cấp instance thay thế → task dừng êm theo stop timeout → terminate instance hỏng.
- **Giới hạn tốc độ**: tối đa **20%** instance của capacity provider drain cùng lúc; **< 9 instance** thì tối đa **1**.
- Giám sát qua **`DescribeContainerInstances`** và **container instance health change events**.
- Theo dõi **18 mã Xid** (46, 48, 54, 62, 64, 74, 79, 95, 109, 110, 136, 140, 142, 143, 151, 155, 156, 158).
- Tắt bằng `autoRepairConfiguration.actionsStatus = DISABLED` (CLI hoặc console); vẫn theo dõi nhưng không tự thay. **Tắt GPU auto repair cũng tắt daemon auto repair.**

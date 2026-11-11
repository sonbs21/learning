# Daemon auto repair

> Nguồn: **Amazon ECS Developer Guide — Daemon auto repair**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/managed-daemons-auto-repair.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: diễn đạt lại bằng tiếng Việt, bám sát từng ý và đúng thứ tự của trang gốc, không thêm thông tin ngoài trang. Đây không phải bản dịch từng chữ; khi cần câu chữ chính xác, đọc link gốc ở trên.
> - Khối **💡 Giải thích thêm**: phần giải thích để dễ hiểu hơn, chỉ làm rõ những gì docs đã nói.

---

## Mục lục

1. [Auto repair là gì](#1-auto-repair-là-gì)
2. [Các bước auto repair](#2-các-bước-auto-repair)
3. [Health check của daemon](#3-health-check-của-daemon)
4. [Giám sát sức khoẻ daemon](#4-giám-sát-sức-khoẻ-daemon)
5. [Chỗ không khớp với các trang Managed Daemons khác](#5-chỗ-không-khớp-với-các-trang-managed-daemons-khác)
6. [Liên hệ với các file trước](#6-liên-hệ-với-các-file-trước)
7. [Tóm tắt](#7-tóm-tắt)

---

## 1. Auto repair là gì

**📘 Theo docs AWS**

- Trang này nói ECS coi **mọi daemon** là **quan trọng (critical)** với sức khoẻ instance.
- Nếu **bất kỳ** daemon task nào **dừng** hoặc **unhealthy**, ECS coi instance là **bị suy giảm (impaired)** và **tự drain rồi thay** instance đó.

**💡 Giải thích thêm**

Câu "mọi daemon đều critical" **không khớp** với các trang khác về Managed Daemons, nơi có lựa chọn non-critical — xem mục 5.

---

## 2. Các bước auto repair

**📘 Theo docs AWS**

1. ECS **phát hiện** daemon task **dừng** hoặc **unhealthy**.
2. ECS đánh dấu instance là **draining** → instance **không nhận task ứng dụng mới**.
3. ECS **cấp instance thay thế** và **khởi động daemon task** trên đó.
4. Khi daemon task trên instance mới đạt trạng thái **khoẻ**, ECS **lập lịch các task ứng dụng** từ instance đang draining sang instance thay thế.
5. ECS **terminate** instance cũ.

**💡 Giải thích thêm**

```
Instance A (daemon lỗi)                 Instance B (thay thế)
───────────────────────                 ─────────────────────
1. phát hiện daemon dừng/unhealthy
2. A → DRAINING (không nhận task mới)
                                        3. cấp B, chạy daemon trên B
                                        4. daemon trên B khoẻ
   task app trên A  ───────────────────►    task app được đặt lên B
5. terminate A
```

Điểm hay của thứ tự này: task ứng dụng chỉ được dời **sau khi** daemon trên instance mới đã khoẻ — đúng tinh thần "không instance nào chạy app mà thiếu daemon" (file *Amazon ECS Managed Daemons*).

Khi task ứng dụng bị dời khỏi instance A, chúng đi qua quá trình dừng bình thường (`DEACTIVATING` → `STOPPING` với `SIGTERM` → … — file *Task lifecycle*), nên ứng dụng vẫn cần tắt êm. Đây là suy luận ghép từ hai trang.

---

## 3. Health check của daemon

**📘 Theo docs AWS**

> **Important (docs):** Health check của daemon là **tuỳ chọn** nhưng **rất nên có**. Không có health check thì ECS **chỉ phát hiện lỗi khi daemon task dừng**.

**💡 Giải thích thêm**

| | Có `healthCheck` | Không có `healthCheck` |
|---|---|---|
| Daemon **chết hẳn** (process thoát) | Phát hiện | Phát hiện |
| Daemon **treo** / chạy nhưng không làm việc | Phát hiện (unhealthy) | **Không phát hiện** — instance tiếp tục chạy app mà daemon không hoạt động |

Ví dụ health check cho daemon (giá trị do mình đặt; tham số `healthCheck` được hỗ trợ trong daemon task definition — file *Daemon task definitions*):

```json
"healthCheck": {
  "command": ["CMD-SHELL", "curl -f http://localhost:<PORT>/health || exit 1"],
  "interval": 30,
  "timeout": 5,
  "retries": 3,
  "startPeriod": 30
}
```

Lưu ý: health check quá "nhạy" (vd timeout ngắn, retries thấp) có thể khiến daemon bị coi là unhealthy khi chỉ chậm thoáng qua → **thay instance không cần thiết**. Nên dùng `startPeriod` cho thời gian khởi động của agent. Đây là gợi ý của mình.

---

## 4. Giám sát sức khoẻ daemon

**📘 Theo docs AWS**

Giám sát sức khoẻ daemon bằng API **`DescribeContainerInstances`** hoặc **`DescribeTasks`**.

**💡 Giải thích thêm**

Lệnh CLI tương ứng (bổ sung của mình):

```bash
aws ecs describe-container-instances --cluster <CLUSTER> --container-instances <CONTAINER_INSTANCE_ARN>
aws ecs describe-tasks --cluster <CLUSTER> --tasks <DAEMON_TASK_ARN>
```

Kết hợp với các file trước:
- **Daemon events**: `DAEMON_TASK_START_IMPAIRED` khi daemon không khởi động được.
- **Daemon metrics**: `CPUUtilization`/`MemoryUtilization` với `ServiceName = daemon:<tên>` — daemon thiếu memory có thể bị kill → kích hoạt auto repair.

---

## 5. Chỗ không khớp với các trang Managed Daemons khác

**💡 Giải thích thêm**

| Trang | Nói gì về criticality |
|---|---|
| **Daemon auto repair** (trang này) | ECS coi **mọi** daemon là critical; daemon nào dừng/unhealthy cũng gây drain và thay instance |
| *Amazon ECS Managed Daemons* | Có **critical** (mặc định) và **non-critical** (`critical: false`); non-critical **không bao giờ** gây drain |
| *Creating and managing daemons* | Console có tuỳ chọn **Critical**; CLI có `critical` / `--no-critical` |
| *Daemon deployments* | Daemon non-critical lỗi trong deployment **không** drain instance |

Cách hiểu hợp lý (suy luận của mình): quy trình 5 bước ở trang này mô tả hành vi của daemon **critical** — vốn là **mặc định**. Daemon đặt `critical: false` thì không đi theo quy trình này. Trang auto repair có thể được viết trước khi có lựa chọn non-critical. Khi cần chắc chắn, hãy thử trên môi trường test hoặc hỏi AWS Support.

---

## 6. Liên hệ với các file trước

**💡 Giải thích thêm**

| Nội dung | File liên quan |
|---|---|
| Critical vs non-critical | *Amazon ECS Managed Daemons* |
| Drain + thay instance cũng là cơ chế của rolling deployment | *Daemon deployments* |
| `healthCheck` có trong daemon container | *Daemon task definitions* |
| Theo dõi lỗi khởi động daemon | *Daemon events* |
| Theo dõi tài nguyên daemon | *Daemon metrics* |
| Task bị dời đi qua quá trình dừng | *Task lifecycle* |
| Drain/thay instance trên Managed Instances | *Managed Instances auto scaling and task placement* |

---

## 7. Tóm tắt

- Theo trang này, ECS coi daemon là critical: daemon task **dừng** hoặc **unhealthy** → instance bị coi là impaired → **tự drain và thay**.
- Quy trình: phát hiện → đánh dấu **draining** (không nhận task mới) → cấp instance thay thế + chạy daemon → daemon khoẻ thì dời task app sang → **terminate** instance cũ.
- **Health check** daemon là tuỳ chọn nhưng **rất nên có**; không có thì chỉ phát hiện khi daemon **dừng hẳn**.
- Giám sát qua **`DescribeContainerInstances`** hoặc **`DescribeTasks`**.
- Câu "mọi daemon đều critical" không khớp với các trang có lựa chọn non-critical; hiểu hợp lý là quy trình này áp dụng cho daemon critical (mặc định).

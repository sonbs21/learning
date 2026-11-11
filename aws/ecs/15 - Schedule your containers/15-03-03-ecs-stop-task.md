# Stopping an Amazon ECS task

> Nguồn: **Amazon ECS Developer Guide — Stopping an Amazon ECS task**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/standalone-task-stop.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: diễn đạt lại bằng tiếng Việt, bám sát từng bước và đúng thứ tự của trang gốc, không thêm thông tin ngoài trang. Đây không phải bản dịch từng chữ; khi cần câu chữ chính xác, đọc link gốc ở trên.
> - Khối **💡 Giải thích thêm**: phần giải thích để dễ hiểu hơn, chỉ làm rõ những gì docs đã nói.
> - Tên nút trên console giữ nguyên tiếng Anh (in đậm).

---

## Mục lục

1. [Tổng quan](#1-tổng-quan)
2. [Các bước dừng standalone task trên console](#2-các-bước-dừng-standalone-task-trên-console)
3. [Chuyện gì xảy ra khi dừng task](#3-chuyện-gì-xảy-ra-khi-dừng-task)
4. [Dừng task bằng CLI](#4-dừng-task-bằng-cli)
5. [Tóm tắt](#5-tóm-tắt)

---

## 1. Tổng quan

**📘 Theo docs AWS**

- Khi không cần standalone task chạy nữa, bạn có thể **dừng** nó. Console ECS giúp dừng **một hoặc nhiều** task dễ dàng.
- **Không thể khởi động lại** một standalone task đã dừng.
- Muốn dừng **service**, xem *Deleting an Amazon ECS service using the console* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/delete-service-v2.html>.

**💡 Giải thích thêm**

"Không khởi động lại được" nghĩa là task đã `STOPPED` thì xong vòng đời (file *Task lifecycle*). Muốn chạy lại phải **chạy task mới** (`RunTask`), task mới có ID mới.

Lý do tách riêng service: dừng một task **của service** thì service sẽ **tạo task thay thế** để giữ desired count. Muốn task của service dừng hẳn thì phải giảm desired count hoặc xoá service. Ý này là mình giải thích thêm từ cách service hoạt động; trang này chỉ dẫn link.

---

## 2. Các bước dừng standalone task trên console

**📘 Theo docs AWS**

1. Mở console tại <https://console.aws.amazon.com/ecs/v2>.
2. Ở thanh điều hướng, chọn **Clusters**.
3. Trên trang **Clusters**, chọn cluster để vào trang chi tiết.
4. Trên trang chi tiết cluster, chọn tab **Tasks**.
5. Có thể lọc task theo launch type bằng danh sách **Filter launch type**.

| Muốn dừng | Các bước |
|---|---|
| **Một hoặc nhiều task** | Chọn các task → **Stop** → **Stop selected** → ở **Stop task confirmation page** chọn **Stop**. |
| **Tất cả task** | **Stop** → **Stop all** → ở **Stop task confirmation page** nhập **Stop all tasks** rồi chọn **Stop**. |

> **Important (docs):** Chọn dừng **tất cả** task trên console thì ECS dừng **mọi standalone task và cả task thuộc service**. AWS khuyến nghị **cẩn trọng** khi dùng lựa chọn này.

**💡 Giải thích thêm**

| Lựa chọn | Phạm vi | Mức rủi ro |
|---|---|---|
| **Stop selected** | Chỉ các task bạn tích chọn | Thấp — kiểm soát được |
| **Stop all** | **Mọi task trong cluster**, kể cả task của service | Cao — có thể làm gián đoạn service đang phục vụ traffic |

Với "Stop all", các service vẫn còn desired count nên sẽ tạo task mới thay thế, nhưng trong khoảng thời gian đó có thể **mất traffic**. Đây là suy luận từ cách service duy trì desired count; docs chỉ cảnh báo "cẩn trọng".

---

## 3. Chuyện gì xảy ra khi dừng task

**💡 Giải thích thêm**

Trang này không mô tả quá trình dừng; phần dưới ghép từ file *Task lifecycle*:

```
Bạn chọn Stop
   │  desiredStatus = STOPPED
   ▼
DEACTIVATING     (nếu có target group: deregister)
   ▼
STOPPING         (gửi STOPSIGNAL — mặc định SIGTERM; chờ StopTimeout; rồi SIGKILL)
   ▼
DEPROVISIONING   (awsvpc: tháo và xoá ENI)
   ▼
STOPPED
```

Liên hệ:
- Ứng dụng nên **bắt `SIGTERM`** để tắt êm trước khi hết `stopTimeout` (giới hạn khác nhau giữa EC2 / Fargate / Managed Instances — xem các file *task definition parameters*).
- Task đã dừng không còn chặn việc **xoá task definition** (file *Task definition states*: xoá task definition có thể mất tới 1 giờ sau khi task dừng).

---

## 4. Dừng task bằng CLI

**💡 Giải thích thêm**

Trang chỉ hướng dẫn console. Lệnh dưới đây là mình bổ sung để tiện tự động hoá:

```bash
# Dừng một task, kèm lý do (hiện trong stoppedReason)
aws ecs stop-task \
  --cluster <CLUSTER> \
  --task <TASK_ID_HOẶC_ARN> \
  --reason "Dừng thủ công sau khi batch xong"

# Liệt kê standalone task đang chạy theo task group (vd family của task definition)
aws ecs list-tasks --cluster <CLUSTER> --started-by <STARTED_BY>   # nếu bạn đặt --started-by khi RunTask
aws ecs list-tasks --cluster <CLUSTER> --family <FAMILY> --desired-status RUNNING
```

Gợi ý: khi chạy task bằng `RunTask`, đặt `--started-by` hoặc `--group` (file *Group related Amazon ECS tasks*) để sau này lọc và dừng đúng nhóm task, tránh phải dùng "Stop all".

---

## 5. Tóm tắt

- Dừng standalone task khi không cần nó nữa; task đã dừng **không khởi động lại** được.
- Dừng **service** thì xem trang xoá service.
- Console: **Clusters** → chọn cluster → tab **Tasks** → (lọc theo launch type) → **Stop**:
  - **Stop selected**: dừng task đã chọn, xác nhận **Stop**;
  - **Stop all**: dừng **mọi** task (cả task của service), phải nhập **Stop all tasks** để xác nhận — dùng cẩn trọng.

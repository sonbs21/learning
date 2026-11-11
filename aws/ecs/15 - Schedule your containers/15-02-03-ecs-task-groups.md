# Group related Amazon ECS tasks

> Nguồn: **Amazon ECS Developer Guide — Group related Amazon ECS tasks**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task-groups.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: diễn đạt lại bằng tiếng Việt, bám sát từng ý và đúng thứ tự của trang gốc, không thêm thông tin ngoài trang. Đây không phải bản dịch từng chữ; khi cần câu chữ chính xác, đọc link gốc ở trên.
> - Khối **💡 Giải thích thêm**: phần giải thích để dễ hiểu hơn, chỉ làm rõ những gì docs đã nói.

---

## Mục lục

1. [Task group là gì](#1-task-group-là-gì)
2. [Task group với strategy `spread`](#2-task-group-với-strategy-spread)
3. [Task group với constraint `memberOf`](#3-task-group-với-constraint-memberof)
4. [Tên task group mặc định](#4-tên-task-group-mặc-định)
5. [Yêu cầu đối với task group](#5-yêu-cầu-đối-với-task-group)
6. [Liên hệ với các file trước](#6-liên-hệ-với-các-file-trước)
7. [Tóm tắt](#7-tóm-tắt)

---

## 1. Task group là gì

**📘 Theo docs AWS**

Bạn có thể xác định một tập **task có liên quan** và đặt chúng vào một **task group**. Mọi task có **cùng tên task group** được xem là **một tập** khi dùng placement strategy `spread`.

**💡 Giải thích thêm**

File *How Amazon ECS places tasks* đã nêu task group là một trong ba thành phần của task placement (cùng strategy và constraint). Trang này giải thích task group được dùng ở đâu và đặt tên thế nào.

---

## 2. Task group với strategy `spread`

**📘 Theo docs AWS**

- Ví dụ docs: một cluster chạy nhiều ứng dụng khác nhau, như **database** và **web server**.
- Để **database** được **cân bằng qua các AZ**, đưa chúng vào một task group tên **`databases`** rồi dùng strategy **`spread`**.
- (Xem *Use strategies to define Amazon ECS task placement* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task-placement-strategies.html>.)

**💡 Giải thích thêm**

```
Cluster:  web-1  web-2  web-3  db-1  db-2  db-3   (6 task)

Không có task group riêng:
  spread có thể cân bằng "6 task" chung → db có thể dồn vào 1–2 AZ

Có task group "databases" + spread theo AZ:
  spread chỉ xét 3 task db với nhau → AZ-a: db-1, AZ-b: db-2, AZ-c: db-3
```

Ý cuối là minh hoạ của mình cho câu "task cùng group được xem là một tập".

---

## 3. Task group với constraint `memberOf`

**📘 Theo docs AWS**

- Task group cũng dùng được làm **placement constraint**.
- Khi chỉ định task group trong constraint **`memberOf`**, task **chỉ được gửi tới** container instance **đang chạy task thuộc task group đó**.
- Ví dụ: *Example Amazon ECS task placement constraints* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/constraint-examples.html>.

**💡 Giải thích thêm**

Đây là cách "đặt cạnh nhau" (co-location): ví dụ, một task cache chỉ được đặt lên instance đang chạy task thuộc group `databases`. Ví dụ cú pháp (theo dạng cluster query language; trang này không đưa cú pháp, xem trang ví dụ constraint ở link trên):

```json
"placementConstraints": [
  { "type": "memberOf", "expression": "task:group == databases" }
]
```

Liên hệ file *How Amazon ECS places tasks*: constraint là **bắt buộc** — nếu không instance nào đang chạy task của group đó, task mới sẽ **ở lại `PENDING`**.

---

## 4. Tên task group mặc định

**📘 Theo docs AWS**

| Loại task | Tên task group mặc định | Đổi được không |
|---|---|---|
| **Standalone task** | Tên **family** của task definition, dạng `family:my-task-definition`, nếu không chỉ định tên tuỳ chỉnh | Có thể đặt tên tuỳ chỉnh |
| **Task của service** | **Tên service** | **Không đổi được** |

**💡 Giải thích thêm**

- Vì task của service luôn có group là tên service, strategy `spread` của service tự động trải task **trong phạm vi service đó** — khớp với file *Use strategies*: "task của service được trải dựa trên các task của service đó".
- Với standalone task, có thể đặt group khi chạy task: API `RunTask` có trường `group` (CLI: `--group`). Tên trường là phần mình bổ sung; trang này chỉ nói "nếu không chỉ định tên tuỳ chỉnh".

```bash
aws ecs run-task --cluster <CLUSTER> --task-definition <FAMILY> --group databases
```

---

## 5. Yêu cầu đối với task group

**📘 Theo docs AWS**

- Tên task group tối đa **255 ký tự**.
- Mỗi task thuộc **đúng một** group.
- Sau khi launch task, **không sửa được** task group của nó.

**💡 Giải thích thêm**

Muốn đổi group của một task đang chạy: phải dừng task rồi chạy task mới với group mới (suy ra từ quy tắc "không sửa được sau khi launch").

---

## 6. Liên hệ với các file trước

**💡 Giải thích thêm**

| Nội dung | File liên quan |
|---|---|
| Task group là một trong ba thành phần placement | *How Amazon ECS places tasks* |
| `spread` trải theo service hoặc task group | *Use strategies to define Amazon ECS task placement* |
| `memberOf` là constraint bắt buộc | *How Amazon ECS places tasks*, *task definition parameters for EC2* |
| Placement strategy chỉ có trên EC2 | *How Amazon ECS places tasks* — vì vậy dùng task group với `spread` chủ yếu có ý nghĩa trên EC2 (suy luận) |

---

## 7. Tóm tắt

- **Task group** = tập task liên quan; task cùng tên group được xem là một tập khi dùng `spread`.
- Dùng với **`spread`**: vd group `databases` để cân bằng database qua các AZ.
- Dùng với **`memberOf`**: task chỉ đặt lên instance đang chạy task của group chỉ định.
- **Mặc định**: standalone task dùng `family:<tên task definition>`; task của service dùng **tên service** (không đổi được).
- **Yêu cầu**: tên ≤ 255 ký tự; mỗi task đúng một group; không sửa group sau khi launch.

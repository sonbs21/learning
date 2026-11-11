# Safely stop Amazon ECS workloads running on Amazon ECS Managed Instances

> Nguồn: **Amazon ECS Developer Guide — Safely stop Amazon ECS workloads running on Amazon ECS Managed Instances**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/managed-instance-task-scale-in-protect.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: diễn đạt lại bằng tiếng Việt, bám sát từng ý và đúng thứ tự của trang gốc, không thêm thông tin ngoài trang. Đây không phải bản dịch từng chữ; khi cần câu chữ chính xác, đọc link gốc ở trên.
> - Khối **💡 Giải thích thêm**: phần giải thích để dễ hiểu hơn, chỉ làm rõ những gì docs đã nói.
> - Lệnh CLI / ví dụ dùng placeholder (`<...>`); tên thuộc tính, API giữ đúng như docs.

---

## Mục lục

0. [Task scale-in protection là gì](#0-task-scale-in-protection-là-gì)
1. [Bật / tắt và thời hạn bảo vệ](#1-bật--tắt-và-thời-hạn-bảo-vệ)
2. [Hai cơ chế đặt bảo vệ](#2-hai-cơ-chế-đặt-bảo-vệ)
3. [Những điều cần cân nhắc](#3-những-điều-cần-cân-nhắc)
4. [Quyền IAM cần thiết](#4-quyền-iam-cần-thiết)
5. [Ví dụ thực tế](#5-ví-dụ-thực-tế)
6. [Liên hệ với các file trước](#6-liên-hệ-với-các-file-trước)
7. [Tóm tắt](#7-tóm-tắt)

---

## 0. Task scale-in protection là gì

**📘 Theo docs AWS**

- **Task scale-in protection** bảo vệ task khỏi bị **dừng** bởi sự kiện **scale-in** — từ **service scaling** hoặc từ **deployment**.
- Một số ứng dụng cần cơ chế giữ các task quan trọng không bị dừng lúc tải thấp hoặc lúc deploy. Ví dụ docs:
  - Ứng dụng **xử lý hàng đợi bất đồng bộ** như **chuyển mã video**, có task chạy hàng giờ dù tổng mức sử dụng của service thấp.
  - Ứng dụng **game** chạy game server dưới dạng task, cần chạy tiếp dù mọi người chơi đã thoát, để tránh độ trễ khởi động lại server.
  - Khi **deploy** phiên bản code mới, cần task chạy tiếp vì **xử lý lại rất tốn kém**.

**💡 Giải thích thêm**

```
Không có protection:  service scale in / deploy → ECS chọn task để dừng → job đang làm dở bị dừng
Có protection:        task đang được bảo vệ → ECS bỏ qua task đó khi scale in / deploy
```

---

## 1. Bật / tắt và thời hạn bảo vệ

**📘 Theo docs AWS**

| Thao tác | Cách làm |
|---|---|
| **Bật** bảo vệ | Đặt thuộc tính **`ProtectionEnabled`** = `true` (CLI: `--protection-enabled`) |
| **Thời hạn mặc định** | **2 giờ** |
| **Tuỳ chỉnh thời hạn** | Thuộc tính **`ExpiresInMinutes`**: tối thiểu **1 phút**, tối đa **2880 phút (48 giờ)** |
| **Tắt** bảo vệ (khi task xong việc) | Đặt `ProtectionEnabled` = `false` (CLI: `--no-protection-enabled`) → task có thể bị dừng ở lần scale-in sau |

**💡 Giải thích thêm**

```
t=0          bật protection (mặc định 120 phút, hoặc ExpiresInMinutes)
t=0..hết hạn task được bảo vệ khỏi scale-in / deploy
xong việc    tắt protection → task có thể bị dừng
hết hạn      protection tự hết → task có thể bị dừng
```

---

## 2. Hai cơ chế đặt bảo vệ

**📘 Theo docs AWS**

| Cơ chế | Khi nào dùng | Cách làm |
|---|---|---|
| **Endpoint của ECS container agent** (khuyến nghị) | Task **tự biết** khi nào cần bảo vệ — workload **dựa trên hàng đợi** hoặc **xử lý job** | Từ **bên trong container**, gọi endpoint `$ECS_AGENT_URI/task-protection/v1/state` để đặt `ProtectionEnabled` khi bắt đầu xử lý (vd lấy message SQS); xong việc thì xoá bảo vệ qua cùng endpoint. (Xem *Amazon ECS task scale-in protection endpoint* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/managed-instance-task-scale-in-protection-endpoint.html>.) |
| **ECS API** | Ứng dụng có **thành phần theo dõi trạng thái** task đang hoạt động | **`UpdateTaskProtection`** để đánh dấu một hoặc nhiều task được bảo vệ; **`GetTaskProtection`** để đọc trạng thái. Ví dụ docs: game server — người chơi đăng nhập vào session (task) thì bật bảo vệ; người chơi thoát thì xoá bảo vệ cho task đó, hoặc định kỳ xoá bảo vệ cho các task không còn session, tuỳ có muốn giữ server rảnh hay không. (Xem *UpdateTaskProtection* — <https://docs.aws.amazon.com/AmazonECS/latest/APIReference/API_UpdateTaskProtection.html>, *GetTaskProtection* — <https://docs.aws.amazon.com/AmazonECS/latest/APIReference/API_GetTaskProtection.html>.) |

- Có thể **kết hợp** hai cách: vd dùng endpoint agent để bật bảo vệ từ trong container, và dùng ECS API để gỡ bảo vệ từ **controller bên ngoài**.

**💡 Giải thích thêm**

```
Endpoint agent (trong task):         ECS API (bên ngoài):
  worker nhận message                  matchmaking service thấy người chơi vào
   → PUT …/task-protection/v1/state     → UpdateTaskProtection(task, true)
   → xử lý                             người chơi thoát
   → PUT … (tắt)                        → UpdateTaskProtection(task, false)
```

---

## 3. Những điều cần cân nhắc

**📘 Theo docs AWS**

- Chỉ hỗ trợ task **được deploy từ service**.
- Hỗ trợ với task của service chạy trên **Managed Instances**.
- AWS **khuyến nghị dùng endpoint agent** vì agent có **sẵn cơ chế retry** và giao diện **đơn giản hơn**.
- Gọi lại `UpdateTaskProtection` cho task **đang được bảo vệ** để **đặt lại** thời hạn hết hạn.
- Ước lượng task cần bao lâu để xong việc và đặt **`expiresInMinutes`** tương ứng. Đặt **dài hơn cần thiết** → **tốn chi phí** và **chậm deploy** task mới.

**Cân nhắc khi deploy:**

| Tình huống | Theo docs |
|---|---|
| **Rolling update** | Task mới vẫn được tạo, nhưng task phiên bản cũ **không bị dừng** cho tới khi `protectionEnabled` được xoá hoặc **hết hạn**. Có thể chỉnh **maximum percent** trong deployment configuration để cho phép tạo task mới khi task cũ đang được bảo vệ. |
| **CloudFormation** | `update-stack` có **timeout 3 giờ** → bảo vệ **dài hơn 3 giờ** có thể làm deployment **thất bại và rollback**. Trong thời gian task cũ được bảo vệ, stack ở **`UPDATE_IN_PROGRESS`**; bảo vệ được gỡ hoặc hết hạn trong 3 giờ thì thành **`UPDATE_COMPLETE`**; kẹt quá 3 giờ thì **`UPDATE_FAILED`** và rollback về task set cũ. |
| **Service event / `DEPLOYMENT_BLOCKED`** | ECS phát **service event** khi task được bảo vệ chặn deployment (rolling hoặc blue/green) đạt trạng thái ổn định. Khi cập nhật bảo vệ mà nhận lỗi **`DEPLOYMENT_BLOCKED`**: service có **nhiều task được bảo vệ hơn desired count**. Cách xử lý: **chờ** bảo vệ hiện tại hết hạn rồi đặt lại; **chọn task có thể dừng** và đặt `protectionEnabled` = `false` cho chúng qua `UpdateTaskProtection`; hoặc **tăng desired count** cao hơn số task được bảo vệ. |

**💡 Giải thích thêm**

- Trang ghi `maximumPercentage`, nhưng tên trường thực tế trong deployment configuration của service là **`maximumPercent`** (như ở các file *Instance selection best practices*, *Patching*).
- Ví dụ rolling update (con số do mình đặt): desired 4, cả 4 task cũ đang được bảo vệ, `maximumPercent` = 200% → ECS tạo được 4 task mới chạy song song; nếu `maximumPercent` = 100% thì không có chỗ cho task mới → deploy đứng chờ.
- Standalone task **không** dùng được protection — job dài chạy standalone vẫn có thể bị dừng khi instance bị drain (files *Infrastructure optimization*, *Patching*). Muốn bảo vệ job dài thì chạy dưới dạng service.

---

## 4. Quyền IAM cần thiết

**📘 Theo docs AWS**

Task phải có **task role** với quyền:

| Quyền | Cho phép |
|---|---|
| `ecs:GetTaskProtection` | ECS container agent gọi `GetTaskProtection` |
| `ecs:UpdateTaskProtection` | ECS container agent gọi `UpdateTaskProtection` |

**💡 Giải thích thêm**

Policy tối thiểu cho task role (viết theo hai quyền trên; nên giới hạn `Resource` theo cluster/task nếu được):

```json
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Action": ["ecs:GetTaskProtection", "ecs:UpdateTaskProtection"],
    "Resource": "*"
  }]
}
```

Lưu ý: đây là **task role** (`taskRoleArn`), không phải task execution role (file *task definition parameters*). Controller bên ngoài gọi ECS API cũng cần `ecs:UpdateTaskProtection` trong role của nó.

---

## 5. Ví dụ thực tế

**💡 Giải thích thêm**

Phần này là mình bổ sung; định dạng body của endpoint nên đối chiếu trang *task scale-in protection endpoint*.

**Từ trong container (worker SQS):**

```bash
# Bật bảo vệ 60 phút trước khi xử lý message
curl -s -X PUT "$ECS_AGENT_URI/task-protection/v1/state" \
  -H "Content-Type: application/json" \
  -d '{"ProtectionEnabled": true, "ExpiresInMinutes": 60}'

# ... xử lý message ...

# Tắt bảo vệ khi xong
curl -s -X PUT "$ECS_AGENT_URI/task-protection/v1/state" \
  -H "Content-Type: application/json" \
  -d '{"ProtectionEnabled": false}'
```

**Từ bên ngoài (CLI):**

```bash
aws ecs update-task-protection \
  --cluster <CLUSTER> --tasks <TASK_ARN> \
  --protection-enabled --expires-in-minutes 120

aws ecs get-task-protection --cluster <CLUSTER> --tasks <TASK_ARN>

aws ecs update-task-protection \
  --cluster <CLUSTER> --tasks <TASK_ARN> --no-protection-enabled
```

Mẫu worker an toàn: bật protection → xử lý → tắt protection → lấy message tiếp; nếu job dài hơn thời hạn, **gia hạn** định kỳ (gọi lại để reset hạn, theo mục 3).

---

## 6. Liên hệ với các file trước

**💡 Giải thích thêm**

| Nội dung | File liên quan |
|---|---|
| Consolidation tôn trọng task protection | *Infrastructure optimization* (instance underutilized được drain có tôn trọng task protection) |
| Drain instance theo vòng đời 14–21 ngày | *Patching* — trang này không nói protection có giữ instance quá ngày 21 không; theo *Patching*, ngày 21 ECS dừng instance và workload còn lại (suy luận: protection không vượt được hạn 21 ngày) |
| `maximumPercent`, `minimumHealthyPercent` | *Instance selection best practices*, *Patching* |
| Tắt êm khi bị dừng | *Task lifecycle* (`SIGTERM`, `stopTimeout`) |
| Task role vs execution role | *task definition parameters for Managed Instances* |

---

## 7. Tóm tắt

- **Task scale-in protection**: giữ task không bị dừng bởi scale-in (service scaling) hoặc deployment — hợp với job xử lý dài (chuyển mã video), game server, công việc tốn kém nếu phải làm lại.
- Bật bằng **`ProtectionEnabled = true`** (mặc định **2 giờ**), tuỳ chỉnh **`ExpiresInMinutes`** từ **1 tới 2880 phút (48 giờ)**; tắt bằng `false` khi xong việc.
- **Hai cơ chế**: endpoint agent `$ECS_AGENT_URI/task-protection/v1/state` từ trong container (**khuyến nghị**, có retry), hoặc ECS API **`UpdateTaskProtection`** / **`GetTaskProtection`** từ bên ngoài; có thể kết hợp.
- **Cân nhắc**: chỉ cho task **của service** (có hỗ trợ trên Managed Instances); gọi lại `UpdateTaskProtection` để reset hạn; đặt hạn vừa đủ; rolling update chờ task cũ hết bảo vệ (chỉnh max percent); CloudFormation timeout **3 giờ**; lỗi **`DEPLOYMENT_BLOCKED`** khi số task bảo vệ vượt desired count.
- **IAM**: task role cần `ecs:GetTaskProtection` và `ecs:UpdateTaskProtection`.

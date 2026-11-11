# How Amazon ECS places tasks on container instances

> Nguồn: **Amazon ECS Developer Guide — How Amazon ECS places tasks on container instances**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task-placement.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: diễn đạt lại bằng tiếng Việt, bám sát từng ý và đúng thứ tự của trang gốc, không thêm thông tin ngoài trang. Đây không phải bản dịch từng chữ; khi cần câu chữ chính xác, đọc link gốc ở trên.
> - Khối **💡 Giải thích thêm**: phần giải thích để dễ hiểu hơn, chỉ làm rõ những gì docs đã nói.

---

## Mục lục

1. [Task placement là gì](#1-task-placement-là-gì)
2. [Ba thành phần của task placement](#2-ba-thành-phần-của-task-placement)
3. [Managed Instances](#3-managed-instances)
4. [EC2](#4-ec2)
5. [Fargate](#5-fargate)
6. [So sánh ba môi trường](#6-so-sánh-ba-môi-trường)
7. [Tóm tắt](#7-tóm-tắt)

---

## 1. Task placement là gì

**📘 Theo docs AWS**

Task placement giúp cấu hình để ECS đặt task lên các container instance **đáp ứng tiêu chí nhất định**, ví dụ một **Availability Zone** hoặc một **instance type**. ECS có **thuật toán khác nhau** cho từng loại capacity.

---

## 2. Ba thành phần của task placement

**📘 Theo docs AWS**

| Thành phần | Ý nghĩa theo docs | Ví dụ docs |
|---|---|---|
| **Task placement strategy** | Thuật toán chọn container instance để **đặt task**, hoặc chọn **task để dừng** (khi scale xuống). | Chọn instance ngẫu nhiên, hoặc chọn sao cho task được phân bố đều trên một nhóm instance. |
| **Task group** | Một nhóm task có liên quan. | Các task database. |
| **Task placement constraint** | Quy tắc **bắt buộc** phải thoả để đặt task lên instance. Không thoả thì task **không được đặt** và **ở lại `PENDING`**. | Chỉ đặt task lên một instance type cụ thể. |

**💡 Giải thích thêm**

```
Strategy   = "nên đặt thế nào"       (ưu tiên, cố gắng hết sức)
Constraint = "chỉ được đặt ở đâu"   (bắt buộc — không thoả thì không đặt)
Task group = "nhóm task để strategy/constraint xét chung"
```

Liên hệ file *Task lifecycle*: task kẹt ở `PENDING` có thể do **constraint không thoả**, ngoài lý do thiếu tài nguyên.

---

## 3. Managed Instances

**📘 Theo docs AWS**

- Với task chạy trên Managed Instances, ECS phải quyết định **đặt task ở đâu**, và khi scale xuống thì **dừng task nào**.
- ECS dựa vào:
  - **instance requirements** khai báo trong **launch template của capacity provider**;
  - yêu cầu trong task definition như **CPU** và **memory**;
  - **task placement constraints**.

> **Note (docs):** Managed Instances **không hỗ trợ task placement strategy**. ECS sẽ cố gắng hết sức **trải task qua các AZ** có thể truy cập.

Quy trình chọn container instance khi đặt task:

1. Tìm các instance đáp ứng yêu cầu **CPU, GPU, memory và port** trong task definition.
2. Tìm các instance thoả **task placement constraints**.
3. Tìm các instance thoả **instance requirements** trong launch template của capacity provider.
4. **Chọn** instance để đặt task.

**💡 Giải thích thêm**

Khớp với file *task definition parameters for Managed Instances*: placement constraint trên Managed Instances chỉ hỗ trợ `distinctInstance` và `memberOf` với 4 thuộc tính (`ecs.subnet-id`, `ecs.availability-zone`, `ecs.instance-type`, `ecs.cpu-architecture`). Không có strategy, nên không chọn được kiểu `binpack` hay `spread` tuỳ ý.

---

## 4. EC2

**📘 Theo docs AWS**

- Với task dùng launch type EC2, ECS quyết định đặt task ở đâu dựa trên yêu cầu trong task definition (CPU, memory…). Khi giảm số task, ECS cũng phải chọn **task nào để dừng**.
- Bạn có thể dùng **placement strategy** và **constraint** để tuỳ chỉnh cách ECS **đặt** và **dừng** task.

**Mặc định:**
- Task **thuộc service**: strategy mặc định là **`spread`** theo **`attribute:ecs.availability-zone`**.
- Task **không thuộc service**: **không có constraint mặc định**. (Xem *Schedule your containers on Amazon ECS* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/scheduling_tasks.html>.)

> **Note (docs):** Placement **strategy** là **best effort** — ECS vẫn cố đặt task dù phương án tối ưu không có. Nhưng placement **constraint** là **ràng buộc bắt buộc**, có thể **ngăn** việc đặt task.

- Có thể **kết hợp** strategy và constraint. Ví dụ docs: **trải task qua các AZ**, trong mỗi AZ **bin pack theo memory**, nhưng **chỉ trên instance G2**.

Quy trình chọn container instance:

1. Tìm instance đáp ứng yêu cầu **CPU, GPU, memory và port** trong task definition.
2. Tìm instance thoả **placement constraints**.
3. Tìm instance thoả **placement strategies**.
4. **Chọn** instance để đặt task.

**💡 Giải thích thêm**

Ví dụ của docs viết dưới dạng cấu hình (cách viết là của mình; tên strategy `spread`/`binpack` và thuộc tính `ecs.instance-type` là các giá trị chuẩn của ECS, trang này không liệt kê):

```json
"placementStrategy": [
  { "type": "spread",  "field": "attribute:ecs.availability-zone" },
  { "type": "binpack", "field": "memory" }
],
"placementConstraints": [
  { "type": "memberOf", "expression": "attribute:ecs.instance-type =~ g2.*" }
]
```

```
Bước 1: lọc theo tài nguyên (CPU, GPU, memory, port)
Bước 2: lọc theo constraint   → chỉ còn instance G2         (bắt buộc)
Bước 3: xếp theo strategy     → trải AZ, rồi dồn theo memory (cố gắng)
Bước 4: chọn instance
```

Về mặc định của standalone task: trang này nói "không có constraint mặc định", còn file *Schedule your containers* nói `RunTask` dùng strategy mặc định **phân bổ ngẫu nhiên**. Hai ý không mâu thuẫn — một nói về constraint, một nói về strategy.

Liên hệ file *Architect for EC2 capacity*: mặc định `spread` theo AZ giải thích vì sao các task frontend trong ví dụ nằm rải trên nhiều instance.

---

## 5. Fargate

**📘 Theo docs AWS**

- Task trên Fargate **không hỗ trợ** placement strategy và constraint.
- Fargate sẽ cố gắng hết sức **trải task qua các AZ** có thể truy cập.
- Nếu capacity provider gồm **cả Fargate và Fargate Spot**, việc trải task diễn ra **độc lập cho từng capacity provider**.

**💡 Giải thích thêm**

Khớp với file *Fargate task definition differences*: `placementConstraints` không hợp lệ trên Fargate.

Ý "trải độc lập" có thể hình dung (ví dụ của mình): strategy chia 2 task `FARGATE` và 4 task `FARGATE_SPOT` trên 2 AZ → Fargate cố trải 2 task on-demand qua 2 AZ, và riêng 4 task Spot cũng được trải qua 2 AZ; hai nhóm không được cân bằng chung với nhau.

---

## 6. So sánh ba môi trường

**💡 Giải thích thêm**

| | Managed Instances | EC2 | Fargate |
|---|---|---|---|
| Placement strategy | **Không** | Có (best effort) | **Không** |
| Placement constraint | Có (giới hạn thuộc tính) | Có (bắt buộc) | **Không** |
| Hành vi mặc định | Cố trải qua các AZ | Service: `spread` theo AZ; standalone: không constraint mặc định | Cố trải qua các AZ (độc lập cho Fargate và Fargate Spot) |
| Yếu tố khi chọn instance | Tài nguyên → constraint → **instance requirements trong launch template** | Tài nguyên → constraint → **strategy** | (AWS quản lý) |

---

## 7. Tóm tắt

- Task placement đặt task lên instance theo tiêu chí như AZ hoặc instance type.
- Ba thành phần: **strategy** (thuật toán đặt/dừng task — best effort), **task group** (nhóm task liên quan), **constraint** (bắt buộc — không thoả thì task ở `PENDING`).
- **Managed Instances**: không có strategy; dựa vào instance requirements của launch template, CPU/memory, constraint; quy trình: tài nguyên → constraint → instance requirements → chọn.
- **EC2**: có strategy và constraint, kết hợp được (vd trải AZ + bin pack memory + chỉ G2); service mặc định `spread` theo AZ; quy trình: tài nguyên → constraint → strategy → chọn.
- **Fargate**: không hỗ trợ strategy và constraint; cố trải qua AZ, độc lập cho Fargate và Fargate Spot.

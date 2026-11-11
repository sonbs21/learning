# Use strategies to define Amazon ECS task placement

> Nguồn: **Amazon ECS Developer Guide — Use strategies to define Amazon ECS task placement**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task-placement-strategies.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: diễn đạt lại bằng tiếng Việt, bám sát từng ý và đúng thứ tự của trang gốc, không thêm thông tin ngoài trang. Đây không phải bản dịch từng chữ; khi cần câu chữ chính xác, đọc link gốc ở trên.
> - Khối **💡 Giải thích thêm**: phần giải thích để dễ hiểu hơn, chỉ làm rõ những gì docs đã nói.

---

## Mục lục

1. [Phạm vi: launch type EC2](#1-phạm-vi-launch-type-ec2)
2. [Strategy mặc định và nguyên tắc chung](#2-strategy-mặc-định-và-nguyên-tắc-chung)
3. [Khai báo `placementStrategy`](#3-khai-báo-placementstrategy)
4. [Ba loại strategy](#4-ba-loại-strategy)
5. [Kết hợp nhiều strategy](#5-kết-hợp-nhiều-strategy)
6. [Lưu ý và liên hệ với các file trước](#6-lưu-ý-và-liên-hệ-với-các-file-trước)
7. [Tóm tắt](#7-tóm-tắt)

---

## 1. Phạm vi: launch type EC2

**📘 Theo docs AWS**

- Với task dùng launch type **EC2**, ECS quyết định đặt task ở đâu dựa trên yêu cầu trong task definition (CPU, memory…), và khi giảm số task thì quyết định **dừng task nào**.
- Bạn dùng **placement strategy** và **constraint** để tuỳ chỉnh cách ECS đặt và dừng task.

**💡 Giải thích thêm**

Theo file *How Amazon ECS places tasks*: strategy **chỉ có trên EC2**. Fargate và Managed Instances không hỗ trợ strategy.

---

## 2. Strategy mặc định và nguyên tắc chung

**📘 Theo docs AWS**

- Strategy mặc định phụ thuộc task chạy **thủ công (standalone)** hay **trong service**:
  - task **thuộc service**: **`spread`** theo **`attribute:ecs.availability-zone`**;
  - task **không thuộc service**: **không có placement constraint mặc định**. (Xem *Schedule your containers on Amazon ECS* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/scheduling_tasks.html>.)

> **Note (docs):** Strategy là **best effort** — ECS vẫn đặt task dù phương án tối ưu không có. **Constraint** thì **bắt buộc** và có thể ngăn việc đặt task.

- Có thể kết hợp strategy và constraint. Ví dụ docs: trải task qua các AZ, bin pack theo memory trong mỗi AZ, nhưng chỉ trên instance G2.
- Quy trình chọn container instance:
  1. Lọc instance đáp ứng **CPU, GPU, memory, port** trong task definition.
  2. Lọc instance thoả **constraint**.
  3. Lọc instance thoả **strategy**.
  4. **Chọn** instance để đặt task.

**💡 Giải thích thêm**

Phần này trùng với mục EC2 trong file *How Amazon ECS places tasks*; xem file đó cho sơ đồ và ví dụ cấu hình.

---

## 3. Khai báo `placementStrategy`

**📘 Theo docs AWS**

- Khai báo strategy bằng tham số **`placementStrategy`** — docs nói đặt trong **service definition** hoặc **task definition**.
- Mỗi phần tử có hai trường:
  - **`field`** — trường mà strategy áp dụng lên;
  - **`type`** — loại strategy.
- Chỉ định strategy khi:
  - chạy task — **`RunTask`** (<https://docs.aws.amazon.com/AmazonECS/latest/APIReference/API_RunTask.html>);
  - tạo service — **`CreateService`** (<https://docs.aws.amazon.com/AmazonECS/latest/APIReference/API_CreateService.html>);
  - cập nhật service — **`UpdateService`** (<https://docs.aws.amazon.com/AmazonECS/latest/APIReference/API_UpdateService.html>).
- Strategy của service đang chạy **cập nhật được**. (Xem *How Amazon ECS places tasks on container instances* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task-placement.html>.)

```json
"placementStrategy": [
  { "type": "<LOẠI_STRATEGY>", "field": "<TRƯỜNG>" }
]
```

**💡 Giải thích thêm**

Trang này nói có thể đặt `placementStrategy` trong task definition, nhưng file *task definition parameters for Amazon EC2* chỉ liệt kê **placement constraints** ở mức task definition, không có placement strategy. Ba API docs liệt kê (`RunTask`, `CreateService`, `UpdateService`) đều là thao tác ở mức **task/service**. Cách an toàn: khai báo strategy khi chạy task hoặc tạo/cập nhật service. Xem mục 6.

---

## 4. Ba loại strategy

**📘 Theo docs AWS**

| `type` | Cách đặt task | Khi scale in (giảm task) | `field` hợp lệ |
|---|---|---|---|
| **`binpack`** | Đặt sao cho **CPU hoặc memory còn thừa ít nhất** → **giảm số instance** đang dùng. | ECS dừng task dựa trên tài nguyên còn lại trên instance **sau khi** dừng: instance nào **còn nhiều tài nguyên nhất** sau khi dừng task thì task đó bị dừng. | `cpu`, `memory` |
| **`random`** | Đặt **ngẫu nhiên**. | (docs không nêu) | Không dùng |
| **`spread`** | Đặt **đều** theo giá trị chỉ định. Task của **service** được trải dựa trên các task **của service đó**; **standalone task** được trải dựa trên các task **cùng task group**. (Xem *Group related Amazon ECS tasks* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task-groups.html>.) | ECS chọn task để dừng sao cho **giữ cân bằng giữa các AZ**; trong một AZ, chọn **ngẫu nhiên**. | `instanceId` (hoặc `host`, tác dụng như nhau); hoặc **bất kỳ thuộc tính platform hay custom** gắn trên container instance, vd `attribute:ecs.availability-zone` |

**💡 Giải thích thêm**

Hình dung trên 3 instance, đặt 4 task:

```
binpack (memory):   i-1: [T][T][T][T]   i-2: [ ]   i-3: [ ]     → dồn, ít instance, rẻ
spread  (instanceId): i-1: [T][T]       i-2: [T]   i-3: [T]     → rải, chịu lỗi tốt
random:             phân bố bất kỳ
```

| | `binpack` | `spread` | `random` |
|---|---|---|---|
| Mục tiêu | Tiết kiệm instance | Chịu lỗi, cân bằng | Đơn giản, tránh dồn một chỗ |
| Đánh đổi | Một instance hỏng ảnh hưởng nhiều task | Dùng nhiều instance hơn | Không tối ưu theo tiêu chí nào |
| Phù hợp (gợi ý) | Batch, dev, tối ưu chi phí | Service production | Standalone task đơn giản |

Cột "phù hợp" là gợi ý của mình; docs không phân loại như vậy.

Liên hệ file *Schedule your containers*: `RunTask` để mặc định thì phân bổ ngẫu nhiên — tương ứng `random`.

---

## 5. Kết hợp nhiều strategy

**📘 Theo docs AWS**

- Tạo strategy gồm **nhiều strategy** bằng một **mảng**, theo **thứ tự** muốn áp dụng.
- Ví dụ docs: muốn **trải task qua các AZ** rồi **bin pack theo memory trong mỗi AZ** → khai báo strategy AZ **trước**, strategy memory **sau**.
- Ví dụ khác: *Example Amazon ECS task placement strategies* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/strategy-examples.html>.

**💡 Giải thích thêm**

```json
"placementStrategy": [
  { "type": "spread",  "field": "attribute:ecs.availability-zone" },
  { "type": "binpack", "field": "memory" }
]
```

```
Bước 1 (spread AZ):      chia đều task cho AZ-a, AZ-b, AZ-c
Bước 2 (binpack memory): trong mỗi AZ, dồn task vào instance còn ít memory trống nhất
```

Thứ tự quan trọng: đảo lại (binpack trước, spread sau) thì ưu tiên dồn task trước, có thể làm task tập trung vào một AZ — đây là suy luận từ nguyên tắc "theo thứ tự muốn áp dụng".

---

## 6. Lưu ý và liên hệ với các file trước

**💡 Giải thích thêm**

| Điểm | Ghi chú |
|---|---|
| Strategy chỉ trên EC2 | Fargate và Managed Instances không hỗ trợ (*How Amazon ECS places tasks*). Managed Instances điều khiển gián tiếp qua subnet (*Managed Instances auto scaling and task placement*). |
| Vị trí khai báo strategy | Trang này: service definition hoặc task definition. Trang *task definition parameters for EC2*: task definition chỉ có placement constraints. Nên khai báo ở `RunTask` / `CreateService` / `UpdateService`. |
| `spread` dựa trên task group | Standalone task trải theo task group; service tự là một nhóm. |
| Scale in | `binpack` dừng task ở instance sẽ trống nhiều nhất → giúp instance đó dễ trống hẳn để scale in instance. `spread` giữ cân bằng AZ khi giảm task. Phần "giúp scale in instance" là suy luận của mình. |
| Kết hợp với capacity provider | Với capacity provider Auto Scaling group (*Schedule your containers*), `binpack` giúp dồn task để ASG có thể giảm số instance — suy luận, docs trang này không nói. |

---

## 7. Tóm tắt

- Strategy dùng cho launch type **EC2**: quyết định đặt task ở đâu và dừng task nào khi scale in.
- Mặc định: task của service `spread` theo `attribute:ecs.availability-zone`; standalone task không có constraint mặc định.
- Strategy là **best effort**; constraint là **bắt buộc**. Quy trình: tài nguyên → constraint → strategy → chọn instance.
- Khai báo bằng `placementStrategy` (mỗi phần tử có `type`, `field`), qua `RunTask`, `CreateService`, `UpdateService`; cập nhật được cho service đang chạy.
- **`binpack`** (`cpu`/`memory`): dồn task, ít instance; scale in dừng task ở instance còn nhiều tài nguyên nhất sau khi dừng.
- **`random`**: đặt ngẫu nhiên, không dùng `field`.
- **`spread`** (`instanceId`/`host` hoặc thuộc tính platform/custom như AZ): rải đều theo service hoặc task group; scale in giữ cân bằng AZ, trong AZ chọn ngẫu nhiên.
- Kết hợp nhiều strategy bằng mảng theo thứ tự áp dụng (vd spread AZ rồi binpack memory).

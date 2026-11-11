# Schedule your containers on Amazon ECS

> Nguồn: **Amazon ECS Developer Guide — Schedule your containers on Amazon ECS**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/scheduling_tasks.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: diễn đạt lại bằng tiếng Việt, bám sát từng ý và đúng thứ tự của trang gốc, không thêm thông tin ngoài trang. Đây không phải bản dịch từng chữ; khi cần câu chữ chính xác, đọc link gốc ở trên.
> - Khối **💡 Giải thích thêm**: phần giải thích để dễ hiểu hơn, chỉ làm rõ những gì docs đã nói.

---

## Mục lục

1. [ECS scheduler là gì](#1-ecs-scheduler-là-gì)
2. [Ba cách chạy task](#2-ba-cách-chạy-task)
3. [Compute options: capacity provider strategy và launch type](#3-compute-options-capacity-provider-strategy-và-launch-type)
4. [Base và weight](#4-base-và-weight)
5. [Tóm tắt](#5-tóm-tắt)

---

## 1. ECS scheduler là gì

**📘 Theo docs AWS**

- ECS là hệ thống **shared state, optimistic concurrency** (trạng thái dùng chung, xử lý đồng thời kiểu lạc quan), cung cấp khả năng lập lịch linh hoạt cho workload container.
- Các scheduler của ECS dùng **cùng thông tin trạng thái cluster** với ECS API để quyết định đặt task ở đâu.
- ECS cung cấp:
  - **service scheduler** cho task và ứng dụng chạy lâu dài;
  - khả năng chạy **standalone task** hoặc **scheduled task** cho batch job hoặc tác vụ chạy một lần.
- Bạn có thể chỉ định **task placement strategy** và **constraint** phù hợp nhu cầu, ví dụ task chạy trải nhiều AZ hay trong một AZ.
- Tuỳ chọn: tích hợp task với scheduler **tự viết** hoặc **của bên thứ ba**.

**💡 Giải thích thêm**

"Shared state, optimistic concurrency" nghĩa là các scheduler cùng đọc một trạng thái cluster chung và ra quyết định mà không khoá toàn bộ cluster trước; nếu có xung đột thì xử lý sau. Docs không đi sâu cơ chế; phần giải thích thuật ngữ này là của mình.

Điểm thực tế: vì scheduler dùng cùng dữ liệu với ECS API, những gì bạn thấy qua `describe-*` chính là dữ liệu scheduler dùng để đặt task.

---

## 2. Ba cách chạy task

**📘 Theo docs AWS**

| Cách | Khi nào dùng (theo docs) | Tài liệu |
|---|---|---|
| **Service** | *Service scheduler* hợp với **service và ứng dụng stateless chạy lâu dài**. Tuỳ chọn đăng ký task vào load balancer của Elastic Load Balancing. Có thể cập nhật service, ví dụ deploy task definition mới hoặc đổi số task mong muốn. **Mặc định trải task qua nhiều AZ**; có thể dùng placement strategy và constraint để tuỳ chỉnh. | *Amazon ECS services* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/ecs_services.html> |
| **Standalone task** | Hợp với tiến trình như **batch job làm việc rồi dừng**. Ví dụ: một tiến trình gọi **`RunTask`** khi có việc vào hàng đợi; task lấy việc từ hàng đợi, xử lý rồi thoát. Với `RunTask`, có thể để placement strategy mặc định **phân bổ task ngẫu nhiên** trong cluster, giảm khả năng một instance nhận quá nhiều task. | *Amazon ECS standalone tasks* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/standalone-tasks.html> |
| **Scheduled task** | Hợp khi cần chạy task **theo chu kỳ** trong cluster; dùng **EventBridge Scheduler** để tạo lịch. Ví dụ: backup, quét log. Lịch có thể chạy một hoặc nhiều task vào thời điểm định sẵn, theo **khoảng thời gian** (mỗi N phút, giờ, ngày) hoặc biểu thức **`cron`** cho lịch phức tạp hơn. | *Using Amazon EventBridge Scheduler to schedule Amazon ECS tasks* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/tasks-scheduled-eventbridge-scheduler.html> |

**💡 Giải thích thêm**

```
Cần chạy liên tục, tự thay task hỏng, có load balancer?   → Service
Cần chạy một lần theo sự kiện (vd có message trong queue)? → Standalone task (RunTask)
Cần chạy định kỳ theo giờ giấc?                            → Scheduled task (EventBridge Scheduler)
```

| | Service | Standalone task | Scheduled task |
|---|---|---|---|
| Vòng đời | Lâu dài, ECS duy trì số task | Chạy xong rồi dừng | Chạy xong rồi dừng, lặp theo lịch |
| Ai kích hoạt | ECS (theo desired count) | Bạn / ứng dụng gọi `RunTask` | EventBridge Scheduler |
| Load balancer | Tuỳ chọn | — | — |
| Placement mặc định | Trải nhiều AZ | Ngẫu nhiên | (docs không nêu) |

Liên hệ các file trước: ECS Gateway và rolling/blue-green deployment đều thuộc nhánh **Service**. Liên hệ file *task definition states*: standalone task và task của service là hai loại tài nguyên có thể **chặn việc xoá** task definition.

---

## 3. Compute options: capacity provider strategy và launch type

**📘 Theo docs AWS**

- ECS cho phép chỉ định hạ tầng mà task hoặc service chạy trên đó, bằng **capacity provider strategy** hoặc **launch type**.
- Capacity provider:
  - **Fargate**: capacity provider là **Fargate** và **Fargate Spot**;
  - **EC2**: capacity provider là **Auto Scaling group** chứa các container instance đã đăng ký.
- Capacity provider strategy **phân bổ task** qua các capacity provider gắn với cluster.
- Chỉ dùng được trong strategy những capacity provider **đã gắn với cluster** và có trạng thái **`ACTIVE`** hoặc **`UPDATING`**. Có thể gắn capacity provider với cluster khi tạo cluster.
- **Launch type** (compute option trực tiếp) chạy task thẳng trên **Fargate** hoặc trên **EC2 instance bạn đăng ký thủ công** vào cluster.

**💡 Giải thích thêm**

| | Capacity provider strategy | Launch type |
|---|---|---|
| Fargate | Fargate, Fargate Spot (chia tỉ lệ được) | Fargate |
| EC2 | Auto Scaling group (ECS có thể scale instance theo nhu cầu) | Instance đăng ký thủ công |
| Chia task qua nhiều nguồn | Có (base + weight) | Không |

Trang này không nhắc Managed Instances; file *Architect your application* nói Managed Instances cũng là một capacity provider.

---

## 4. Base và weight

**📘 Theo docs AWS**

- **`base`** (tuỳ chọn): số task **tối thiểu** chạy trên một capacity provider chỉ định. Trong một strategy, **chỉ một** capacity provider được có `base`.
- **`weight`**: tỉ lệ phần trăm tương đối của tổng số task được launch dùng capacity provider đó.
- Ví dụ docs:
  - Hai capacity provider đều weight **1** → sau khi đạt base, task chia **đều** cho hai bên.
  - *capacityProviderA* weight **1**, *capacityProviderB* weight **4** → cứ **1** task trên A thì có **4** task trên B.

**💡 Giải thích thêm**

Ví dụ minh hoạ (con số do mình đặt): strategy gồm `FARGATE` (base 2, weight 1) và `FARGATE_SPOT` (weight 4), chạy 12 task:

```
Bước 1 — base:   2 task đầu → FARGATE
Bước 2 — weight: 10 task còn lại chia theo 1 : 4
                 → FARGATE 2, FARGATE_SPOT 8
Tổng:            FARGATE 4, FARGATE_SPOT 8
```

Dạng khai báo tương ứng:

```json
"capacityProviderStrategy": [
  { "capacityProvider": "FARGATE",      "base": 2, "weight": 1 },
  { "capacityProvider": "FARGATE_SPOT",            "weight": 4 }
]
```

Cách dùng phổ biến (suy luận, docs không nói): đặt `base` trên capacity provider ổn định (Fargate on-demand) để luôn có vài task không bị gián đoạn, rồi dùng `weight` để đẩy phần lớn task sang Spot cho rẻ.

---

## 5. Tóm tắt

- ECS là hệ thống shared state, optimistic concurrency; scheduler dùng cùng trạng thái cluster với ECS API.
- **Service**: ứng dụng stateless chạy lâu dài, tuỳ chọn gắn ELB, cập nhật được, mặc định trải nhiều AZ.
- **Standalone task**: batch job chạy rồi dừng, gọi `RunTask` (vd khi có việc trong queue), placement mặc định ngẫu nhiên.
- **Scheduled task**: chạy định kỳ bằng EventBridge Scheduler — theo khoảng thời gian hoặc `cron`.
- Có thể dùng placement strategy/constraint, và tích hợp scheduler tự viết hoặc bên thứ ba.
- **Compute**: capacity provider strategy (Fargate / Fargate Spot / Auto Scaling group; phải gắn cluster và ở `ACTIVE`/`UPDATING`) hoặc launch type (Fargate hoặc EC2 đăng ký thủ công).
- **`base`**: số task tối thiểu trên một provider (chỉ một provider có base). **`weight`**: tỉ lệ chia phần còn lại (vd 1 : 4).

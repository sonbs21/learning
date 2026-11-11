# Amazon ECS clusters for Fargate

> Nguồn: **Amazon ECS Developer Guide — Amazon ECS clusters for Fargate**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/fargate-capacity-providers.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: diễn đạt lại bằng tiếng Việt, bám sát từng ý và đúng thứ tự của trang gốc, không thêm thông tin ngoài trang. Đây không phải bản dịch từng chữ; khi cần câu chữ hoặc ví dụ JSON chính xác, đọc link gốc ở trên.
> - Khối **💡 Giải thích thêm**: phần giải thích để dễ hiểu hơn, chỉ làm rõ những gì docs đã nói.
> - Ví dụ sự kiện trên docs được tóm thành bảng trường.

---

## Mục lục

0. [Fargate và Fargate Spot](#0-fargate-và-fargate-spot)
1. [Sự kiện khi task dừng](#1-sự-kiện-khi-task-dừng)
2. [Những điều cần cân nhắc về capacity provider](#2-những-điều-cần-cân-nhắc-về-capacity-provider)
3. [Thông báo dừng của Fargate Spot](#3-thông-báo-dừng-của-fargate-spot)
4. [Tắt êm khi bị gián đoạn](#4-tắt-êm-khi-bị-gián-đoạn)
5. [Sự kiện và rule EventBridge](#5-sự-kiện-và-rule-eventbridge)
6. [Liên hệ với các file trước](#6-liên-hệ-với-các-file-trước)
7. [Tóm tắt](#7-tóm-tắt)

---

## 0. Fargate và Fargate Spot

**📘 Theo docs AWS**

- Với capacity provider Fargate, task ECS dùng được **cả Fargate lẫn Fargate Spot**.
- **Fargate Spot**: chạy task **chịu được gián đoạn** với giá **rẻ hơn** Fargate. Fargate Spot dùng **capacity compute dư**; khi AWS cần lại capacity, task bị **gián đoạn** kèm **cảnh báo trước 2 phút**.

**💡 Giải thích thêm**

| | Fargate | Fargate Spot |
|---|---|---|
| Giá | Giá chuẩn | Rẻ hơn |
| Gián đoạn | Không do Spot | Có thể bị thu hồi, báo trước 2 phút |
| Phù hợp | Workload cần ổn định | Batch, xử lý hàng đợi, môi trường dev, workload chịu được gián đoạn |

---

## 1. Sự kiện khi task dừng

**📘 Theo docs AWS**

- Task dùng capacity provider Fargate hoặc Fargate Spot **dừng** → **task state change event** được gửi tới **EventBridge**; **stopped reason** mô tả nguyên nhân. (Xem *Amazon ECS task state change events* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/ecs_task_events.html>.)
- Cluster có thể chứa **lẫn** capacity provider Fargate và Auto Scaling group, nhưng **một capacity provider strategy** chỉ chứa **Fargate hoặc ASG**, không cả hai. (Xem *Auto Scaling Group Capacity Providers* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/cluster-auto-scaling.html#asg-capacity-providers>.)

---

## 2. Những điều cần cân nhắc về capacity provider

**📘 Theo docs AWS**

| # | Quy tắc |
|---|---|
| 1 | Phải **gắn** capacity provider vào cluster **trước** khi đưa nó vào capacity provider strategy |
| 2 | Một strategy có **tối đa 20** capacity provider |
| 3 | **Không** cập nhật được service đang dùng capacity provider **ASG** sang capacity provider **Fargate**, và ngược lại |
| 4 | Không khai báo `weight`: trên **console** mặc định **1**; qua **API / CLI** mặc định **0** |
| 5 | Strategy có nhiều capacity provider thì **ít nhất một** phải có weight **> 0**. Provider weight 0 **không** được dùng để đặt task. Tất cả đều weight 0 → `RunTask` / `CreateService` **thất bại** |
| 6 | Chỉ **một** capacity provider trong strategy được có **base**; không khai báo base → mặc định **0** |
| 7 | Cluster chứa lẫn được ASG và Fargate, nhưng một strategy chỉ chứa **một** trong hai loại |
| 8 | Cluster chứa lẫn được service và standalone task dùng cả hai loại capacity provider. Service có thể chuyển từ **launch type** sang **capacity provider strategy**, nhưng phải **force new deployment** |

**💡 Giải thích thêm**

- **Bẫy weight mặc định (quy tắc 4–5):** tạo strategy bằng **CLI/Terraform** mà quên `weight` → weight = 0 → task không được đặt hoặc lệnh thất bại. Luôn khai báo `weight` rõ ràng khi dùng API/IaC.

```bash
# Sai: weight mặc định 0 qua CLI → không đặt được task
--capacity-provider-strategy capacityProvider=FARGATE

# Đúng
--capacity-provider-strategy capacityProvider=FARGATE,weight=1
```

- **Quy tắc 3** nghĩa là muốn chuyển service giữa ASG và Fargate, phải **tạo service mới** rồi chuyển traffic — giống lưu ý ở file *Migrate from EC2 to Managed Instances*. Trang này chỉ nói về ASG ↔ Fargate; trang *Migrate from Fargate to Managed Instances* lại hướng dẫn **update service** từ Fargate sang Managed Instances — tức quy tắc 3 không chặn trường hợp đó (suy luận từ hai trang).
- Trang này chỉ nhắc **Fargate và ASG**, không nhắc Managed Instances; quy tắc "một strategy chỉ một loại" ở file *Amazon ECS clusters* đã bao gồm cả ba loại.
- Ví dụ base + weight Fargate/Fargate Spot: xem file *Schedule your containers*.

---

## 3. Thông báo dừng của Fargate Spot

**📘 Theo docs AWS**

- Lúc **nhu cầu cực cao**, capacity Fargate Spot có thể **không có** → task Fargate Spot bị **chậm khởi chạy**. Khi đó, ECS service **thử lại** việc launch task tới khi có capacity. Fargate **không** tự thay capacity Spot bằng capacity **on-demand**.
- Task Fargate Spot bị dừng vì **Spot interruption**: có **cảnh báo trước 2 phút**, gửi dưới dạng:
  - **task state change event** tới EventBridge;
  - tín hiệu **SIGTERM** tới task đang chạy.
- Dùng Fargate Spot trong **service**: service scheduler nhận tín hiệu gián đoạn và **cố launch task mới** trên Fargate Spot nếu còn capacity. Service **chỉ có 1 task** sẽ **bị gián đoạn** tới khi có capacity lại.
- Tắt êm: xem *Graceful shutdowns with ECS* — <https://aws.amazon.com/blogs/containers/graceful-shutdowns-with-ecs/>.

**💡 Giải thích thêm**

```
AWS cần lại capacity
  │
  ├─ t = 0:  event "ECS Task State Change" (stopCode SpotInterruption) + SIGTERM tới task
  │          service scheduler cố launch task thay thế trên Fargate Spot
  ├─ t ≤ stopTimeout (tối đa 120 giây): ứng dụng tự dọn dẹp và thoát
  └─ hết stopTimeout: SIGKILL
```

Vì Fargate **không** tự chuyển sang on-demand, cách giảm rủi ro phổ biến là strategy có **base trên `FARGATE`** (on-demand) và phần còn lại trên `FARGATE_SPOT` — gợi ý của mình, ghép với ví dụ base/weight ở file *Schedule your containers*.

---

## 4. Tắt êm khi bị gián đoạn

**📘 Theo docs AWS**

Để container thoát êm trước khi task dừng:

| Cấu hình | Nội dung |
|---|---|
| **`stopTimeout`** | Khai báo trong container definition, **≤ 120 giây**. Mặc định **30 giây**. Đặt dài hơn để có thêm thời gian giữa lúc nhận event và lúc container bị dừng cưỡng bức. (Xem *Container timeouts* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task_definition_parameters.html#container_definition_timeout>.) |
| **Xử lý SIGTERM** | Container phải **nhận SIGTERM** để dọn dẹp. Không xử lý → task nhận **SIGKILL** sau `stopTimeout`, có thể **mất hoặc hỏng dữ liệu** |

**💡 Giải thích thêm**

Cảnh báo Spot là **2 phút**, `stopTimeout` tối đa trên Fargate là **120 giây** — khớp nhau: đặt `stopTimeout: 120` để tận dụng hết thời gian báo trước (nhận xét của mình, ghép từ hai con số). Liên hệ file *Task lifecycle* (STOPPING: SIGTERM → chờ StopTimeout → SIGKILL) và *task definition parameters for Fargate* (`stopTimeout` tối đa 120).

---

## 5. Sự kiện và rule EventBridge

**📘 Theo docs AWS**

Ví dụ trên docs là một **task state change event** cho Spot interruption. Các trường đáng chú ý:

| Trường | Giá trị trong ví dụ |
|---|---|
| `detail-type` | `ECS Task State Change` |
| `source` | `aws.ecs` |
| `detail.desiredStatus` | `STOPPED` |
| `detail.lastStatus` | `RUNNING` |
| `detail.stoppedReason` | Thông điệp cho biết **Spot task đã bị gián đoạn** |
| `detail.stopCode` | **`SpotInterruption`** |
| `detail.clusterArn`, `detail.taskArn` | ARN cluster, ARN task |

Event pattern để tạo **EventBridge rule** cho task state change event: lọc `source` = `aws.ecs`, `detail-type` = `ECS Task State Change`, và (tuỳ chọn) `detail.clusterArn` để chỉ nhận event của **một cluster**. (Xem *Getting started with Amazon EventBridge* — <https://docs.aws.amazon.com/eventbridge/latest/userguide/eb-get-started.html>.)

```json
{
  "source": ["aws.ecs"],
  "detail-type": ["ECS Task State Change"],
  "detail": { "clusterArn": ["<CLUSTER_ARN>"] }
}
```

**💡 Giải thích thêm**

Chỉ bắt **Spot interruption** (lọc thêm theo `stopCode`, tự viết theo cùng cú pháp):

```json
{
  "source": ["aws.ecs"],
  "detail-type": ["ECS Task State Change"],
  "detail": { "stopCode": ["SpotInterruption"] }
}
```

Lưu ý `desiredStatus = STOPPED` trong khi `lastStatus = RUNNING` — đúng mô hình `desiredStatus` / `lastStatus` ở file *Task lifecycle*: event đến **trước** khi task thực sự dừng, cho ứng dụng thời gian phản ứng.

---

## 6. Liên hệ với các file trước

**💡 Giải thích thêm**

| Nội dung | File liên quan |
|---|---|
| Base, weight, strategy | *Schedule your containers* |
| Một strategy một loại provider | *Amazon ECS clusters* |
| `stopTimeout` ≤ 120 trên Fargate | *task definition parameters for Fargate* |
| SIGTERM → stopTimeout → SIGKILL | *Task lifecycle* |
| `desiredStatus` vs `lastStatus` | *Task lifecycle* |
| EventBridge rule | *Daemon events* (cùng cách viết event pattern) |
| Spot trên Managed Instances | *Managed Instances instance types* (`capacityOptionType: Spot`), *Infrastructure optimization* (Spot không có underutilized detection) |

---

## 7. Tóm tắt

- Capacity provider Fargate gồm **Fargate** và **Fargate Spot** (rẻ hơn, dùng capacity dư, có thể bị gián đoạn với **cảnh báo 2 phút**).
- Task dừng → **task state change event** tới EventBridge, có stopped reason.
- **Quy tắc**: gắn capacity provider vào cluster trước khi dùng trong strategy; tối đa **20** provider/strategy; **không** chuyển service giữa ASG và Fargate; `weight` mặc định **1** (console) / **0** (API/CLI); cần ít nhất một weight > 0; chỉ một provider có **base** (mặc định 0); một strategy chỉ **một loại**; chuyển service từ launch type sang strategy cần **force new deployment**.
- **Fargate Spot** thiếu capacity → service thử lại, **không** tự chuyển sang on-demand. Gián đoạn → event + **SIGTERM**, service cố launch thay thế; service 1 task sẽ gián đoạn tới khi có capacity.
- **Tắt êm**: `stopTimeout` ≤ **120** giây (mặc định 30); bắt **SIGTERM**, nếu không sẽ bị **SIGKILL**.
- Event Spot: `stopCode = SpotInterruption`; tạo EventBridge rule lọc `aws.ecs` + `ECS Task State Change` (+ cluster).

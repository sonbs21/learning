# Instance selection best practices for Amazon ECS Managed Instances

> Nguồn: **Amazon ECS Developer Guide — Instance selection best practices for Amazon ECS Managed Instances**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/managed-instances-instance-selection-best-practices.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: diễn đạt lại bằng tiếng Việt, bám sát từng ý và đúng thứ tự của trang gốc, không thêm thông tin ngoài trang. Đây không phải bản dịch từng chữ; khi cần câu chữ chính xác, đọc link gốc ở trên.
> - Khối **💡 Giải thích thêm**: phần giải thích để dễ hiểu hơn, chỉ làm rõ những gì docs đã nói.

---

## Mục lục

0. [Tổng quan](#0-tổng-quan)
1. [Dùng chọn instance mặc định](#1-dùng-chọn-instance-mặc-định)
2. [Dùng chọn theo thuộc tính cho đa số workload](#2-dùng-chọn-theo-thuộc-tính-cho-đa-số-workload)
3. [Chỉ dùng instance type cụ thể khi có yêu cầu phần cứng](#3-chỉ-dùng-instance-type-cụ-thể-khi-có-yêu-cầu-phần-cứng)
4. [Chọn tài nguyên cân đối, tránh dư thừa](#4-chọn-tài-nguyên-cân-đối-tránh-dư-thừa)
5. [Trộn instance type cho workload đa dạng](#5-trộn-instance-type-cho-workload-đa-dạng)
6. [Capacity Reservations và cấu hình deployment](#6-capacity-reservations-và-cấu-hình-deployment)
7. [Bảng quyết định tóm gọn](#7-bảng-quyết-định-tóm-gọn)
8. [Tóm tắt](#8-tóm-tắt)

---

## 0. Tổng quan

**📘 Theo docs AWS**

Chọn đúng cấu hình instance cho Managed Instances rất quan trọng để tối ưu **hiệu năng**, **chi phí** và **mức sử dụng tài nguyên**. ECS cho nhiều lựa chọn linh hoạt để cân bằng yêu cầu ứng dụng với hiệu quả chi phí. Trang đưa ra **6** best practice.

---

## 1. Dùng chọn instance mặc định

**📘 Theo docs AWS**

Khi **không** khai báo `instanceRequirements`, ECS chọn instance **tiết kiệm chi phí nhất** đáp ứng các tham số sau:

| Nguồn | Tham số |
|---|---|
| **Task definition** | `operatingSystemFamily`, `cpuArchitecture`, `cpu`, `memory` |
| **Service definition** | `placementConstraints`, `placementStrategy` |

**💡 Giải thích thêm**

- Vì ECS dựa vào `cpu`/`memory` và `cpuArchitecture` trong task definition, khai báo các giá trị này **chính xác** (file *Best practices for task sizes*) giúp ECS chọn instance phù hợp hơn.
- Trang này liệt kê `placementStrategy` của service là yếu tố được xét, trong khi file *How Amazon ECS places tasks* nói Managed Instances **không hỗ trợ** placement strategy. Có thể hiểu là ECS xét nếu có, nhưng các trang khác cho thấy strategy không áp dụng cho Managed Instances — cần kiểm tra thêm.

---

## 2. Dùng chọn theo thuộc tính cho đa số workload

**📘 Theo docs AWS**

- Chọn theo thuộc tính cho phép ECS chọn trong **dải instance type rộng hơn** đáp ứng yêu cầu.
- Tăng khả năng **đặt task thành công**, và tối ưu chi phí tốt hơn vì ECS chọn được instance **rẻ nhất đang có** tại thời điểm launch.

**💡 Giải thích thêm**

Càng nhiều instance type khớp, càng ít khả năng "hết hàng" ở một AZ. Ví dụ: yêu cầu `vCPU 2–8, memory ≥ 8 GiB` khớp hàng chục type thuộc nhiều family, so với chỉ định cứng một type (file *Managed Instances instance types*).

---

## 3. Chỉ dùng instance type cụ thể khi có yêu cầu phần cứng

**📘 Theo docs AWS**

- Dành việc chọn instance type cụ thể cho workload cần **tính năng phần cứng đặc thù**: **tăng tốc GPU**, **CPU tần số cao**, **mạng chuyên dụng**.
- Với ứng dụng general-purpose, chọn theo thuộc tính thường **linh hoạt** và **tối ưu chi phí** hơn.

**💡 Giải thích thêm**

Ví dụ (của mình): model ML cần đúng GPU → `allowedInstanceTypes: ["g5.*"]`; API web thông thường → chọn theo thuộc tính.

---

## 4. Chọn tài nguyên cân đối, tránh dư thừa

**📘 Theo docs AWS**

- Chọn cấu hình instance **sát với** yêu cầu CPU và memory của ứng dụng.
- Tránh **cấp dư** quá nhiều tài nguyên — tốn chi phí, giảm hiệu quả.
- Dùng **dữ liệu giám sát** để hiểu mức sử dụng thực tế và điều chỉnh việc chọn instance.

**💡 Giải thích thêm**

Liên hệ:
- File *Best practices for task sizes*: load test để biết nhu cầu thật.
- File *Architect for Amazon ECS Managed Instances*: CloudWatch metric mức task và instance, Container Insights.
- Ví dụ: ứng dụng cần nhiều memory so với CPU → dùng thuộc tính **tỉ lệ memory/vCPU** (file *Managed Instances instance types*) để ECS ưu tiên instance memory-optimized thay vì chọn instance lớn toàn diện.

---

## 5. Trộn instance type cho workload đa dạng

**📘 Theo docs AWS**

- Với ứng dụng có **yêu cầu hiệu năng khác nhau** hoặc **tải biến động**, cân nhắc dùng **nhiều capacity provider** với **cấu hình instance khác nhau**.
- Cách này tối ưu chi phí bằng cách dùng instance type phù hợp cho **từng thành phần** của ứng dụng, mà vẫn giữ hiệu năng ở chỗ cần.

**💡 Giải thích thêm**

```
Cluster
├─ capacity provider "general"  (theo thuộc tính, Graviton)  → service web, API
├─ capacity provider "gpu"      (allowedInstanceTypes g5.*)  → service inference
└─ capacity provider "batch"    (Spot)                       → job xử lý hàng đợi
```

Ví dụ do mình đặt. Mỗi service chọn capacity provider phù hợp qua capacity provider strategy (file *Schedule your containers*). Cùng tinh thần "mỗi thành phần một task definition, scale riêng" ở file *Architect your application*.

---

## 6. Capacity Reservations và cấu hình deployment

**📘 Theo docs AWS**

Khi dùng capacity provider Managed Instances với **`capacityOptionType=Reserved`**:

- Service ECS mặc định dùng deployment configuration **`minimumHealthyPercent=100%`** và **`maximumPercent=200%`** → deployment **khởi động task mới trước** rồi mới dừng task cũ, và **tạm thời cần tới 200%** capacity so với lúc ổn định.
- Nếu ở trạng thái ổn định service đã **dùng hết** capacity trong EC2 Capacity Reservation, deployment sẽ **thất bại** vì không còn capacity để launch task mới.
- Cách tránh:
  - đặt **`minimumHealthyPercent` < 100%** (vd **75%**);
  - cân nhắc đặt **`maximumPercent` = 100%** để service **dừng task trước** rồi mới khởi động task mới — giải phóng capacity trước khi launch task thay thế, giúp deployment hoàn tất.
- Ngoài ra, nên **theo dõi mức sử dụng capacity** thường xuyên để giữ **khoảng dư (headroom)** trong reservation cho deployment và traffic tăng đột biến.

**💡 Giải thích thêm**

```
Mặc định (min 100%, max 200%) với 4 task, reservation vừa đủ 4 task:
  cần chạy 4 cũ + tối đa 4 mới = 8 → reservation không đủ → deployment kẹt

Đề xuất (min 75%, max 100%):
  dừng 1 task cũ (còn 3 = 75%) → chạy 1 task mới → lặp lại
  → không bao giờ vượt 4 task → nằm gọn trong reservation
```

| Cấu hình | Ưu | Nhược |
|---|---|---|
| min 100% / max 200% (mặc định) | Không giảm số task khi deploy | Cần **gấp đôi** capacity tạm thời |
| min 75% / max 100% | Không cần capacity dư | Tạm thời chỉ còn **75%** task phục vụ |
| Giữ headroom trong reservation | Deploy mượt với mặc định | Trả tiền cho phần capacity dự phòng |

Liên hệ file *Architect for EC2 capacity* (rolling update cần chỗ trống) và file *Managed Instances instance types* (Capacity Reservations, `reservations-only` / `-first`). Với `reservations-first`, phần thiếu có thể được bù bằng On-Demand nên vấn đề này nhẹ hơn — suy luận, trang không nói.

---

## 7. Bảng quyết định tóm gọn

**💡 Giải thích thêm**

| Tình huống | Khuyến nghị (theo docs) |
|---|---|
| Không có yêu cầu đặc biệt | Chọn **mặc định** (không khai báo `instanceRequirements`) |
| Workload thông thường, muốn linh hoạt và dễ đặt task | **Theo thuộc tính** |
| Cần GPU, CPU tần số cao, mạng chuyên dụng | **Instance type cụ thể** |
| Muốn tiết kiệm | Cấu hình **sát nhu cầu**, dựa trên số liệu giám sát |
| Nhiều thành phần với nhu cầu khác nhau | **Nhiều capacity provider** |
| Dùng **Capacity Reservations** | `minimumHealthyPercent` < 100%, cân nhắc `maximumPercent` = 100%, giữ headroom |

---

## 8. Tóm tắt

1. **Mặc định**: không khai báo `instanceRequirements` → ECS chọn rẻ nhất theo `operatingSystemFamily`, `cpuArchitecture`, `cpu`, `memory` (task definition) và `placementConstraints`, `placementStrategy` (service).
2. **Theo thuộc tính** cho đa số workload → dải instance rộng, đặt task dễ thành công, chọn được loại rẻ nhất lúc launch.
3. **Type cụ thể** chỉ khi cần phần cứng đặc thù (GPU, CPU tần số cao, mạng chuyên dụng).
4. **Cân đối tài nguyên**, tránh dư thừa; dựa vào dữ liệu giám sát.
5. **Nhiều capacity provider** với cấu hình khác nhau cho các thành phần khác nhau.
6. **Capacity Reservations** (`capacityOptionType=Reserved`): mặc định deploy cần tới 200% capacity → đặt `minimumHealthyPercent` < 100% (vd 75%), cân nhắc `maximumPercent` = 100%, và giữ headroom trong reservation.

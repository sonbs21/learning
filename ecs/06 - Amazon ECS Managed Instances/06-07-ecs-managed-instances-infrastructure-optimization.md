# Amazon ECS Managed Instances infrastructure optimization

> Nguồn: **Amazon ECS Developer Guide — Amazon ECS Managed Instances infrastructure optimization**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/managed-instances-infrastructure-optimization.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: diễn đạt lại bằng tiếng Việt, bám sát từng ý và đúng thứ tự của trang gốc, không thêm thông tin ngoài trang. Đây không phải bản dịch từng chữ; khi cần câu chữ chính xác, đọc link gốc ở trên.
> - Khối **💡 Giải thích thêm**: phần giải thích để dễ hiểu hơn, chỉ làm rõ những gì docs đã nói.

---

## Mục lục

0. [Tổng quan](#0-tổng-quan)
1. [Phát hiện instance rảnh (idle)](#1-phát-hiện-instance-rảnh-idle)
2. [Phát hiện instance dùng chưa hết (underutilized)](#2-phát-hiện-instance-dùng-chưa-hết-underutilized)
3. [Cơ chế kích hoạt](#3-cơ-chế-kích-hoạt)
4. [ScaleInAfter](#4-scaleinafter)
5. [Liên hệ với các file trước](#5-liên-hệ-với-các-file-trước)
6. [Tóm tắt](#6-tóm-tắt)

---

## 0. Tổng quan

**📘 Theo docs AWS**

- Managed Instances tự cấp EC2 instance **đúng kích thước** theo cấu hình capacity provider và nhu cầu hiện tại, bảo đảm ứng dụng có tài nguyên phù hợp **ngay từ khi deploy**.
- Khi traffic và nhu cầu thay đổi theo thời gian, Managed Instances **liên tục giám sát và tối ưu** hạ tầng:
  - điều chỉnh **kích thước instance** cho khớp nhu cầu hiện tại;
  - chủ động **thay** các instance đã **lệch** khỏi cấu hình tối ưu;
  - cân bằng linh hoạt giữa **chi phí**, **hiệu năng** và **độ tin cậy**.
- Hệ thống chạy **không cần can thiệp tay**, giảm chi phí mà vẫn giữ tính sẵn sàng cao.

**Lợi ích:**

| Lợi ích | Nội dung |
|---|---|
| **Tối ưu chi phí** | Tận dụng tối đa tài nguyên, loại bỏ capacity rảnh |
| **Cải thiện hiệu năng** | Đặt workload theo yêu cầu tài nguyên và đặc tính hiệu năng |
| **Vận hành đơn giản** | Tự động hoá quyết định quản lý tài nguyên phức tạp |
| **Tăng độ tin cậy** | Giữ sẵn sàng cao qua phân bổ workload và theo dõi sức khoẻ |

Managed Instances thực hiện **hai loại** tối ưu hạ tầng (mục 1 và 2).

**💡 Giải thích thêm**

Đây là phần chi tiết của "active workload consolidation" và "right-sizing" được nhắc ở file *Architect for Amazon ECS Managed Instances*.

---

## 1. Phát hiện instance rảnh (idle)

**📘 Theo docs AWS**

- Tìm và loại bỏ EC2 instance **không có task nào đang chạy**, tránh tốn tiền cho capacity không dùng.
- Khi phát hiện instance rảnh, quá trình tối ưu đánh dấu container instance là **`DEREGISTERING`** → bắt đầu chuỗi dọn dẹp, **terminate an toàn** EC2 instance bên dưới.

**💡 Giải thích thêm**

```
Instance không còn task nào
   → (chờ ScaleInAfter, mục 4)
   → DEREGISTERING
   → terminate EC2 instance
```

---

## 2. Phát hiện instance dùng chưa hết (underutilized)

**📘 Theo docs AWS**

- Phân tích cách task phân bố trên các instance để tìm cơ hội phân bổ tài nguyên tốt hơn.
- Khi task chạy rải rác **chưa tối ưu** trên nhiều instance, Managed Instances **gom** workload vào **ít instance hơn**, sử dụng hiệu quả hơn → giảm chi phí mà vẫn giữ hiệu năng.
- Quy trình:
  1. Đánh dấu instance underutilized là **`DRAINING`** → kích hoạt **thay task**, dời workload sang instance **có sẵn** hoặc **mới** hiệu quả hơn.
  2. Khi mọi task đã được dời an toàn → instance chuyển sang **`DEREGISTERING`** và được dọn.
- Tối ưu này áp dụng cho instance chạy **task của service**, và bảo đảm gom an toàn bằng cách tôn trọng:
  - **giới hạn số task tối thiểu và tối đa** của service;
  - hành vi deploy **start-before-stop** (chạy task mới trước khi dừng task cũ);
  - cài đặt **task protection** trong suốt quá trình drain.
- Instance chạy **standalone task** **không** được xét, vì Managed Instances **không thay thế** standalone task.

> **Important (docs):** Phát hiện instance underutilized **chưa hỗ trợ Spot instance**. Nếu dùng Spot với Managed Instances, tối ưu hạ tầng vẫn **phát hiện và xoá instance rảnh** (không có task), nhưng **không gom** workload từ Spot instance dùng chưa hết.

**💡 Giải thích thêm**

```
Trước:  i-1 [A]        i-2 [B]        i-3 [C]       (mỗi instance dùng ~30%)
                │ DRAINING i-2, i-3 → task B, C được thay sang i-1 (hoặc instance mới)
Sau:    i-1 [A][B][C]                                 (i-2, i-3 → DEREGISTERING → terminate)
```

| Loại instance / task | Idle detection | Underutilized detection |
|---|---|---|
| On-Demand, task của service | Có | Có |
| On-Demand, có standalone task | Có (khi hết task) | **Không** (không thay standalone task) |
| **Spot** | Có | **Không** |

Hệ quả thực tế (suy luận của mình):
- Task của service sẽ **bị dời chỗ** khi gom — ứng dụng cần tắt êm (file *Task lifecycle*).
- Standalone task dài hạn có thể giữ một instance "dùng chưa hết" mãi đến khi task xong hoặc instance tới hạn vòng đời (file *Patching*).

---

## 3. Cơ chế kích hoạt

**📘 Theo docs AWS**

- Hai cơ chế phối hợp để hạ tầng **liên tục thích ứng** với nhu cầu thực tế, tự loại bỏ lãng phí mà **không ảnh hưởng** tính sẵn sàng.
- Cả hai dùng **giám sát hướng sự kiện (event-driven)**, phản ứng với sự kiện vòng đời task và instance để tìm cơ hội tối ưu **gần thời gian thực**:
  - **Idle**: ECS phát hiện khi **task cuối cùng dừng** trên một instance.
  - **Underutilized**: **mỗi lần một task dừng** hoặc **một instance mới launch** đều kích hoạt phân tích để tìm cơ hội gom workload.

---

## 4. ScaleInAfter

**📘 Theo docs AWS**

- Cả hai tối ưu đều tìm cơ hội **terminate** instance để tăng hiệu quả và giảm chi phí.
- Điều khiển **thời điểm** bằng cấu hình **ScaleInAfter** trong cài đặt capacity provider Managed Instances — áp dụng cho **cả** idle lẫn underutilized.
- ScaleInAfter = **độ trễ (giây)** từ lúc instance trở nên idle/underutilized tới lúc Managed Instances bắt đầu tối ưu.

| Giá trị | Ý nghĩa |
|---|---|
| **0 – 3600** | Độ trễ tính bằng giây |
| **-1** | **Tắt** tối ưu hạ tầng |
| Không khai báo (tuỳ chọn) | Managed Instances **tự xác định** thời điểm tối ưu theo cấu hình mặc định |

**Với instance idle:**
- ECS chờ đúng khoảng thời gian chỉ định **sau khi task cuối dừng**, rồi mới deregister instance.
- Nếu có **task mới** khởi động trong thời gian chờ → instance không còn idle, **huỷ** việc terminate.

**Với instance underutilized:**
- ECS chờ khoảng thời gian chỉ định **sau sự kiện dừng task** làm instance trở nên underutilized, rồi mới drain.
- Nếu trong thời gian chờ có **task mới launch** hoặc **task khác dừng** trên instance đó → timer **đặt lại** tính từ sự kiện gần nhất; hết thời gian chờ mới, ECS **đánh giá lại** và hành động nếu cần.

**💡 Giải thích thêm**

```
ScaleInAfter = 600 giây

Idle:
  t=0    task cuối dừng → bắt đầu chờ
  t=300  task mới khởi động → huỷ, instance tiếp tục dùng
  (nếu không có task mới) t=600 → DEREGISTERING

Underutilized:
  t=0    task dừng → instance underutilized → bắt đầu chờ
  t=200  task khác dừng → reset timer
  t=800  hết chờ → đánh giá lại → DRAINING nếu vẫn underutilized
```

| Chọn ScaleInAfter | Khi nào (gợi ý của mình) |
|---|---|
| Thấp (vd 0–300) | Tải ổn định, muốn tiết kiệm tối đa |
| Cao (vd 1800–3600) | Tải lên xuống liên tục (vd job định kỳ), tránh vừa xoá instance đã phải tạo lại |
| `-1` | Cần instance ổn định, không muốn task bị dời do gom (chấp nhận tốn chi phí) |

Tên trường chính xác trong API (vd `scaleInAfter`) và vị trí trong cấu hình capacity provider: trang chỉ gọi là "ScaleInAfter", nên đối chiếu API reference khi dùng CLI hoặc Terraform.

Liên hệ file *Managed Instances pull behavior*: instance bị xoá sớm thì cache image mất; instance mới phải pull lại — ScaleInAfter cao giúp giữ cache lâu hơn cho workload chạy theo đợt (suy luận).

---

## 5. Liên hệ với các file trước

**💡 Giải thích thêm**

| Nội dung | File liên quan |
|---|---|
| Consolidation, right-sizing | *Architect for Amazon ECS Managed Instances* |
| Trạng thái `DRAINING`, `DEREGISTERING` của instance | *Patching in Amazon ECS Managed Instances* (`UpdateContainerInstancesState`, `DeregisterContainerInstance`) |
| Start-before-stop, min/max task | *Instance selection best practices* (`maximumPercent`, `minimumHealthyPercent`) |
| Spot | *Managed Instances instance types* (`capacityOptionType: Spot`) |
| Bin packing, trải AZ | *Managed Instances auto scaling and task placement* |
| Task bị dời | *Task lifecycle* (tắt êm với `SIGTERM`) |
| Standalone task không được thay | *Amazon ECS standalone tasks*, *Security considerations* (standalone ≤ 14 ngày) |

---

## 6. Tóm tắt

- Managed Instances **tự cấp đúng kích thước**, **liên tục tối ưu**: chỉnh kích thước, thay instance lệch cấu hình, cân bằng chi phí – hiệu năng – độ tin cậy; không cần can thiệp tay.
- **Idle detection**: instance **không có task** → `DEREGISTERING` → terminate.
- **Underutilized detection**: gom task của **service** vào ít instance hơn → instance cũ `DRAINING` → task được thay → `DEREGISTERING`. Tôn trọng min/max task, start-before-stop, task protection. **Không** áp dụng cho instance có **standalone task**, và **chưa hỗ trợ Spot** (Spot chỉ có idle detection).
- Kích hoạt **theo sự kiện**: task cuối dừng (idle); mỗi lần task dừng hoặc instance mới launch (underutilized).
- **ScaleInAfter**: độ trễ **0–3600 giây** trước khi tối ưu; **-1** để tắt; không khai báo thì ECS tự chọn. Idle: task mới trong lúc chờ → huỷ. Underutilized: task mới hoặc task dừng → reset timer rồi đánh giá lại.

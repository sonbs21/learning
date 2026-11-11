# Patching in Amazon ECS Managed Instances

> Nguồn: **Amazon ECS Developer Guide — Patching in Amazon ECS Managed Instances**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/managed-instances-patching.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: diễn đạt lại bằng tiếng Việt, bám sát từng ý và đúng thứ tự của trang gốc, không thêm thông tin ngoài trang. Đây không phải bản dịch từng chữ; khi cần câu chữ chính xác, đọc link gốc ở trên.
> - Khối **💡 Giải thích thêm**: phần giải thích để dễ hiểu hơn, chỉ làm rõ những gì docs đã nói.

---

## Mục lục

0. [Patching là gì](#0-patching-là-gì)
1. [Vòng đời instance (14–21 ngày)](#1-vòng-đời-instance-1421-ngày)
2. [Event window và lịch bảo trì](#2-event-window-và-lịch-bảo-trì)
3. [Trình tự bảo trì](#3-trình-tự-bảo-trì)
4. [Ảnh hưởng tới service và cách chuẩn bị](#4-ảnh-hưởng-tới-service-và-cách-chuẩn-bị)
5. [Chỗ không khớp với trang khác](#5-chỗ-không-khớp-với-trang-khác)
6. [Tóm tắt](#6-tóm-tắt)

---

## 0. Patching là gì

**📘 Theo docs AWS**

Trên Managed Instances, **patching** là quá trình bảo trì trong đó **AWS tự quản lý và cập nhật phần mềm** trên container instance, nhằm đảm bảo **bảo mật** và **tuân thủ**, đồng thời giữ **tính sẵn sàng** của workload thông qua một quy trình **có kiểm soát** và **cấu hình được**.

**💡 Giải thích thêm**

Managed Instances không "vá tại chỗ" instance đang chạy. Cách vá là **thay instance**: drain instance cũ, chạy task trên instance mới đã có phần mềm mới nhất (mục 3). Vì không có custom AMI và không SSH (file *Architect for Amazon ECS Managed Instances*), bạn không tự vá được.

---

## 1. Vòng đời instance (14–21 ngày)

**📘 Theo docs AWS**

- Mặc định, container instance của Managed Instances có vòng đời chuẩn **14–21 ngày**:
  - ECS bắt đầu **drain êm** workload vào **ngày 14** tính từ lúc launch;
  - **terminate cuối cùng** chậm nhất vào **ngày 21**.
- ECS có thể drain **sớm hơn** trong các trường hợp:
  - phát hiện **lỗ hổng bảo mật** trong phần mềm;
  - **sức khoẻ phần cứng** suy giảm;
  - để **tuân theo event window** do khách hàng cấu hình.
- Cách này giữ tuân thủ mà vẫn tôn trọng yêu cầu bảo trì của khách hàng.

**💡 Giải thích thêm**

```
Ngày 0 ─────────────── Ngày 14 ─────────── Ngày 21
launch                  bắt đầu drain       terminate chậm nhất
   (có thể drain sớm hơn: lỗ hổng bảo mật, phần cứng lỗi, event window)
```

---

## 2. Event window và lịch bảo trì

**📘 Theo docs AWS**

- AWS quản lý vòng đời instance bằng các **tiến trình nền tự động**, theo dõi **thời điểm tạo** instance và **lịch bảo trì**.
- Khi instance launch, AWS đặt lịch drain mặc định **14 ngày** và xét các **event window** khách hàng đã cấu hình.
- Bạn lên lịch bảo trì bằng **event window**:
  - gắn một hoặc nhiều Managed Instances vào event window qua **instance ID** hoặc **instance tag**;
  - khi event window được gắn tag với một giá trị cụ thể, ECS ánh xạ tag đó tới các Managed Instances tương ứng của cluster và lên lịch bảo trì trong khung giờ đã định, theo kiểu **cố gắng hết sức (best effort)**.
  - (Xem *Create custom event windows for scheduled events that affect your Amazon EC2 instances* — <https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/event-windows.html>.)
- Có event window thì AWS **điều chỉnh lịch drain** cho khớp, có thể **sớm hơn** mốc 14 ngày để rơi đúng vào event window.
- Thay đổi event window **chỉ áp dụng cho instance launch mới** → lịch bảo trì dễ dự đoán.
- Trước thời điểm drain, ECS vẫn **đặt task bình thường** lên instance theo cấu hình của bạn.

**💡 Giải thích thêm**

Ví dụ (của mình): event window "Chủ nhật 01:00–05:00 giờ Việt Nam".

```
Instance launch thứ Tư tuần 1
  Mốc 14 ngày rơi vào thứ Tư tuần 3 (ngoài event window)
  → AWS chọn drain vào event window gần trước mốc đó, vd Chủ nhật tuần 2 lúc 01:00
```

Cách chọn chính xác thời điểm là suy luận từ câu "có thể drain sớm hơn để khớp event window"; docs không nêu thuật toán.

Lưu ý:
- Event window được dùng **best effort** — không bảo đảm tuyệt đối.
- Đổi event window **không** ảnh hưởng instance đang chạy, chỉ instance launch sau đó.
- Event window là tính năng của EC2 (cùng khái niệm với file *Architect for Amazon ECS Managed Instances*, mục "EC2 event windows").

---

## 3. Trình tự bảo trì

**📘 Theo docs AWS**

1. Đến thời điểm bảo trì, ECS gọi API **`UpdateContainerInstancesState`** để bắt đầu **drain êm** workload. Trong thời gian kết thúc êm này, ECS cố dừng workload trên instance đang drain.
2. ECS dừng task và khởi chạy task thay thế theo **deployment configuration** của service (**`maximumPercent`** và **`minimumHealthyPercent`**):
   - **`maximumPercent` = 200%** (mặc định): ECS **lập lịch task mới trước**, chờ chúng **chạy** rồi mới dừng task cũ → giảm gián đoạn;
   - **`maximumPercent` = 100%**: ECS **dừng task trước**, rồi mới thay.
   - Trong suốt quá trình, service tuân theo mọi cấu hình deployment, và ECS tiếp tục cố drain **cho tới ngày 21**.
3. Nếu tới **ngày 21** vẫn chưa drain xong, ECS gọi **`DeregisterContainerInstance`** để **dừng** instance và **workload còn lại**, nhằm giữ tuân thủ và vá instance bằng phần mềm mới nhất.

**💡 Giải thích thêm**

```
Thời điểm bảo trì
  │
  ├─ UpdateContainerInstancesState → instance DRAINING
  │
  ├─ thay task theo deployment config của service
  │     max 200%: chạy task mới → chờ RUNNING → dừng task cũ
  │     max 100%: dừng task cũ → chạy task mới
  │
  ├─ drain xong trước ngày 21 → instance được thay
  │
  └─ ngày 21 chưa xong → DeregisterContainerInstance → dừng cưỡng bức task còn lại
```

Task bị dừng đi qua quá trình dừng bình thường (`DEACTIVATING` → `STOPPING` với `SIGTERM` → … — file *Task lifecycle*).

---

## 4. Ảnh hưởng tới service và cách chuẩn bị

**💡 Giải thích thêm**

Phần này là gợi ý của mình, ghép từ trang này và các file trước:

| Rủi ro | Nguyên nhân | Cách giảm |
|---|---|---|
| Drain kẹt tới ngày 21 rồi bị dừng cưỡng bức | Không đủ capacity cho task mới khi `maximumPercent` = 200%, hoặc task không dừng được | Giữ headroom capacity; với Capacity Reservations dùng `minimumHealthyPercent` < 100% và `maximumPercent` = 100% (file *Instance selection best practices*) |
| Mất request khi thay task | Ứng dụng không tắt êm | Xử lý `SIGTERM`, đặt `stopTimeout` hợp lý (file *Task lifecycle*) |
| Gián đoạn lúc cao điểm | Drain rơi vào giờ đông người dùng | Cấu hình **event window** vào giờ thấp điểm |
| Service chỉ có 1 task | Thay task → có lúc không còn task nào | Desired count ≥ 2, trải nhiều AZ (file *Managed Instances auto scaling and task placement*) |
| Task chạy liên tục > 14–21 ngày | Bị dừng khi instance hết vòng đời | Không phù hợp Managed Instances (file *Architect for Amazon ECS Managed Instances*) |
| Standalone task đang chạy dài | Không có service để thay thế | Thiết kế batch có thể chạy lại / tiếp tục (suy luận) |

Liên hệ **Managed Daemons**: drain instance kéo theo daemon trên instance đó; instance mới khởi động daemon trước rồi mới nhận task ứng dụng (file *Amazon ECS Managed Daemons*).

---

## 5. Chỗ không khớp với trang khác

**💡 Giải thích thêm**

| Trang | Nói về vòng đời instance |
|---|---|
| *Architect for Amazon ECS Managed Instances* | "Tối đa **14 ngày** mỗi instance"; drain sau 14 ngày |
| **Patching** (trang này) | Vòng đời **14–21 ngày**: bắt đầu drain ngày 14, terminate chậm nhất ngày 21 |

Hai trang không mâu thuẫn hoàn toàn: **14 ngày** là lúc **bắt đầu drain**, **21 ngày** là hạn **cuối cùng** instance bị dừng. Trang *Architect* viết gọn hơn ("tối đa 14 ngày"). Khi lập kế hoạch, dùng mốc của trang này cho chính xác.

---

## 6. Tóm tắt

- AWS tự vá Managed Instances bằng cách **thay instance**, có kiểm soát và cấu hình được.
- **Vòng đời 14–21 ngày**: drain bắt đầu ngày **14**, terminate chậm nhất ngày **21**. Có thể drain **sớm hơn** khi có lỗ hổng bảo mật, phần cứng lỗi, hoặc để khớp **event window**.
- **Event window**: gắn instance qua instance ID hoặc tag; ECS lên lịch bảo trì trong khung giờ đó (**best effort**), có thể sớm hơn mốc 14 ngày; thay đổi chỉ áp dụng cho instance **mới**; trước giờ drain, task vẫn được đặt bình thường.
- **Trình tự**: `UpdateContainerInstancesState` (drain) → thay task theo `maximumPercent` / `minimumHealthyPercent` (200%: chạy mới trước; 100%: dừng trước) → nếu tới ngày 21 chưa xong thì `DeregisterContainerInstance` dừng instance và task còn lại.

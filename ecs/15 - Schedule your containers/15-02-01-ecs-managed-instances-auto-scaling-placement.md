# Amazon ECS Managed Instances auto scaling and task placement

> Nguồn: **Amazon ECS Developer Guide — Amazon ECS Managed Instances auto scaling and task placement**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/managed-instance-auto-scaling.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: diễn đạt lại bằng tiếng Việt, bám sát từng ý và đúng thứ tự của trang gốc, không thêm thông tin ngoài trang. Đây không phải bản dịch từng chữ; khi cần câu chữ chính xác, đọc link gốc ở trên.
> - Khối **💡 Giải thích thêm**: phần giải thích để dễ hiểu hơn, chỉ làm rõ những gì docs đã nói.

---

## Mục lục

1. [Tổng quan](#1-tổng-quan)
2. [Thuật toán đặt task](#2-thuật-toán-đặt-task)
   - [2.1 Trải qua Availability Zone](#21-trải-qua-availability-zone)
   - [2.2 Bin packing](#22-bin-packing)
   - [2.3 Mật độ ENI](#23-mật-độ-eni)
3. [Logic quyết định của capacity provider](#3-logic-quyết-định-của-capacity-provider)
4. [Các lựa chọn cấu hình](#4-các-lựa-chọn-cấu-hình)
5. [Xử lý sự cố khi đặt task](#5-xử-lý-sự-cố-khi-đặt-task)
6. [Best practices](#6-best-practices)
7. [Liên hệ với các file trước](#7-liên-hệ-với-các-file-trước)
8. [Tóm tắt](#8-tóm-tắt)

---

## 1. Tổng quan

**📘 Theo docs AWS**

Managed Instances dùng thuật toán để **tự động scale capacity** của cluster và **đặt task hiệu quả**. Hiểu cách thuật toán hoạt động giúp tối ưu cấu hình service và xử lý khi task được đặt không như mong đợi.

**💡 Giải thích thêm**

File *How Amazon ECS places tasks* nói Managed Instances **không hỗ trợ placement strategy**. Trang này giải thích ECS tự đặt task thế nào, và bạn **gián tiếp** điều khiển hành vi đó bằng **cấu hình subnet**, **network mode** và **instance type**, thay vì khai báo strategy.

---

## 2. Thuật toán đặt task

**📘 Theo docs AWS**

Thuật toán cân bằng giữa **tính sẵn sàng**, **mức sử dụng tài nguyên** và **yêu cầu mạng** khi lập lịch task.

### 2.1 Trải qua Availability Zone

**📘 Theo docs AWS**

Mặc định, Managed Instances **ưu tiên tính sẵn sàng** bằng cách trải task qua nhiều AZ:
- Với service có nhiều task, ECS cố gắng phân bố trên **ít nhất 3 instance ở các AZ khác nhau** khi có thể.
- Cách này tăng **khả năng chịu lỗi**, nhưng có thể làm **mức sử dụng tài nguyên mỗi instance thấp hơn**.
- Trải AZ **được ưu tiên hơn** tối ưu bin packing.

### 2.2 Bin packing

**📘 Theo docs AWS**

Managed Instances có thể bin pack để tận dụng tài nguyên tối đa, nhưng hành vi này **phụ thuộc cấu hình mạng**:
- Muốn bin pack: cấu hình service dùng **một subnet duy nhất**.
- Cấu hình **nhiều subnet** ưu tiên phân bố AZ hơn mật độ tài nguyên.
- Bin packing dễ xảy ra **lúc service mới khởi động** hơn là lúc **scale**.

### 2.3 Mật độ ENI

**📘 Theo docs AWS**

Với service dùng mode `awsvpc`, ECS xét **mật độ ENI** khi đặt task:
- Mỗi task `awsvpc` cần một **ENI riêng**.
- Mỗi instance type có **giới hạn ENI** khác nhau, ảnh hưởng số task chạy được trên một instance.
- ECS tính tới số ENI còn trống khi chọn instance.

> **Note (docs):** AWS liên tục cải thiện cách tính mật độ ENI để tối ưu quyết định đặt task.

**💡 Giải thích thêm**

```
Thứ tự ưu tiên (theo docs):
  1. Trải AZ (chịu lỗi)          ← mạnh nhất khi có nhiều subnet
  2. Bin packing (tiết kiệm)     ← chỉ rõ rệt khi một subnet, nhất là lúc mới launch
  Ràng buộc nền: ENI còn trống trên instance (với awsvpc)
```

Liên hệ file *task networking for Managed Instances*: ENI trunking giúp tăng mật độ task `awsvpc`, nhưng giới hạn ENI theo instance type vẫn là yếu tố khi đặt task.

---

## 3. Logic quyết định của capacity provider

**📘 Theo docs AWS**

Capacity provider của Managed Instances quyết định scale và đặt task dựa trên:

| Yếu tố | Nội dung |
|---|---|
| **Yêu cầu tài nguyên** | CPU, memory, yêu cầu mạng của các task đang chờ |
| **Instance sẵn có** | Capacity và mức sử dụng hiện tại trên các instance |
| **Ràng buộc mạng** | Cấu hình subnet và số ENI còn trống |
| **Phân bố AZ** | Duy trì khả năng chịu lỗi qua nhiều AZ |

**💡 Giải thích thêm**

"Task đang chờ" ở đây là task ở trạng thái `PENDING` (file *Task lifecycle*): khi không instance nào đáp ứng được, capacity provider dựa vào các yếu tố trên để quyết định có cần thêm instance hay không.

---

## 4. Các lựa chọn cấu hình

### 4.1 Chiến lược chọn subnet

**📘 Theo docs AWS**

Cấu hình subnet ảnh hưởng lớn tới cách đặt task:

| | **Nhiều subnet (mặc định)** | **Một subnet** |
|---|---|---|
| Ưu tiên | Trải AZ, sẵn sàng cao | Bin packing, sử dụng tài nguyên cao |
| Đánh đổi | Mức sử dụng mỗi instance có thể thấp hơn | Giảm khả năng chịu lỗi vì dồn task vào một AZ |
| Phù hợp | **Production** cần chịu lỗi | **Dev** hoặc workload tối ưu chi phí |

### 4.2 Network mode

**📘 Theo docs AWS**

| Mode | Ảnh hưởng tới việc đặt task |
|---|---|
| `awsvpc` | Mỗi task cần một ENI riêng → **giới hạn mật độ task** mỗi instance |
| `host` | Task dùng thẳng mạng của host → đặt task chủ yếu dựa vào **tài nguyên sẵn có** |

### 4.3 Kiến trúc CPU

**📘 Theo docs AWS**

- `cpuArchitecture` trong task definition được dùng để đặt task lên kiến trúc cụ thể: `X86_64` hoặc `ARM64`.
- Không khai báo → ECS cố đặt task lên bất kỳ kiến trúc nào có sẵn theo cấu hình capacity provider.

**💡 Giải thích thêm**

Điểm cần lưu ý (khớp file *task definition parameters for Managed Instances*): image chỉ build cho một kiến trúc thì nên khai báo `cpuArchitecture`, tránh bị đặt lên kiến trúc khác.

Với mode `host`, theo file *task networking options for EC2*, port container gắn thẳng vào port host, nên hai task cùng task definition không chạy chung một instance được nếu dùng cùng port — đây là suy luận áp dụng sang Managed Instances, trang này không nói.

---

## 5. Xử lý sự cố khi đặt task

### 5.1 Các kiểu phân bố thường gặp

**📘 Theo docs AWS**

| Kiểu | Biểu hiện | Ý nghĩa theo docs |
|---|---|---|
| **Phân tán (spread)** | Task rải trên nhiều instance, mỗi instance dùng một phần | **Bình thường** khi dùng nhiều subnet; ưu tiên sẵn sàng hơn hiệu quả tài nguyên |
| **Tập trung (concentrated)** | Nhiều task trên ít instance, mức sử dụng cao | **Đúng kỳ vọng** khi dùng một subnet; có thể xảy ra lúc service mới khởi động |
| **Không đều (uneven)** | Vài instance dùng nhiều, vài instance dùng ít | Có thể do **giới hạn ENI** hoặc **ràng buộc tài nguyên** → xem lại instance type và cấu hình mạng |

### 5.2 Tối ưu hành vi đặt task

**📘 Theo docs AWS**

1. Cân nhắc yêu cầu **sẵn sàng** so với nhu cầu **tối ưu chi phí**.
2. Chọn cấu hình **subnet** phù hợp ưu tiên.
3. Chọn **instance type** có đủ **ENI** cho network mode đang dùng.
4. **Theo dõi** kiểu phân bố và điều chỉnh khi cần.

**💡 Giải thích thêm**

```
Thấy task rải mỏng trên nhiều instance?
  ├─ Đang dùng nhiều subnet → bình thường (ưu tiên AZ)
  └─ Muốn dồn task cho rẻ   → cân nhắc một subnet (chấp nhận giảm chịu lỗi)

Thấy task dồn vào ít instance?
  ├─ Đang dùng một subnet   → đúng kỳ vọng
  └─ Mới launch service     → có thể do bin packing lúc đầu

Thấy phân bố lệch bất thường?
  └─ Kiểm tra giới hạn ENI của instance type (awsvpc) và tài nguyên CPU/memory
```

---

## 6. Best practices

**📘 Theo docs AWS**

| Tình huống | Khuyến nghị |
|---|---|
| **Production** | Dùng **nhiều subnet** ở các AZ khác nhau để sẵn sàng cao, chấp nhận mức sử dụng tài nguyên thấp hơn |
| **Dev / test** | Cân nhắc **một subnet** để tận dụng tài nguyên và giảm chi phí |
| **Mode `awsvpc`** | Chọn instance type đủ **ENI** để tránh bị giới hạn khi đặt task |
| **Tối ưu chi phí** | Theo dõi mức sử dụng và điều chỉnh cấu hình service để cân bằng sẵn sàng và hiệu quả |
| **Xử lý sự cố** | Xem lại **cấu hình subnet** và **network mode** khi task được đặt bất thường |

---

## 7. Liên hệ với các file trước

**💡 Giải thích thêm**

| Nội dung trang này | Liên quan tới file |
|---|---|
| Không có placement strategy, điều khiển gián tiếp qua subnet | *How Amazon ECS places tasks* |
| Trải AZ mặc định | *How Amazon ECS places tasks* (Managed Instances cố trải qua AZ) |
| Nhiều subnet vs một subnet | *ECS Gateway*: gateway phải phủ **mọi AZ** của service — dùng một subnet thì gateway dễ phủ đủ, nhiều subnet thì gateway cần phủ nhiều AZ hơn |
| ENI theo task (`awsvpc`) | *task networking for Managed Instances* (ENI trunking) |
| `cpuArchitecture` | *task definition parameters for Managed Instances* |
| Bin packing vs spread | *Best practices for task sizes* (kích thước task ảnh hưởng mức lấp đầy instance) |

Ghi chú về con số "ít nhất 3 instance ở các AZ khác nhau": nếu service chỉ chạy trong subnet thuộc 2 AZ, ECS không thể đạt 3 AZ — docs dùng chữ "khi có thể". Đây là suy luận từ cách docs diễn đạt.

---

## 8. Tóm tắt

- Managed Instances tự scale và đặt task, cân bằng **sẵn sàng**, **tài nguyên** và **mạng**.
- **Trải AZ** là mặc định và ưu tiên hơn bin packing; với service nhiều task, cố gắng dùng ít nhất 3 instance ở các AZ khác nhau.
- **Bin packing**: cần **một subnet**; dễ xảy ra lúc mới launch hơn lúc scale.
- **`awsvpc`**: mỗi task một ENI; giới hạn ENI của instance type ảnh hưởng mật độ task.
- Capacity provider quyết định dựa trên: tài nguyên task chờ, instance sẵn có, ràng buộc mạng, phân bố AZ.
- **Nhiều subnet** (mặc định) cho production; **một subnet** cho dev hoặc tối ưu chi phí.
- `host` mode: đặt task chủ yếu theo tài nguyên; `cpuArchitecture` chọn `X86_64`/`ARM64`.
- Phân bố spread / concentrated là bình thường tuỳ cấu hình; uneven thì xem giới hạn ENI và tài nguyên.

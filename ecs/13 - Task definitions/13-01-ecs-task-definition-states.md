# Amazon ECS task definition states

> Nguồn: **Amazon ECS Developer Guide — Amazon ECS task definition states**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task-definition-state.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: diễn đạt lại bằng tiếng Việt, bám sát từng ý và đúng thứ tự của trang gốc, không thêm thông tin ngoài trang. Đây không phải bản dịch từng chữ; khi cần câu chữ chính xác, đọc link gốc ở trên.
> - Khối **💡 Giải thích thêm**: phần giải thích để dễ hiểu hơn, chỉ làm rõ những gì docs đã nói.
> - Tên trạng thái và tên API giữ nguyên như docs.

---

## Mục lục

1. [Tổng quan](#1-tổng-quan)
2. [Các trạng thái của task definition](#2-các-trạng-thái-của-task-definition)
3. [Tên task definition sau khi xoá](#3-tên-task-definition-sau-khi-xoá)
4. [Chi phí khi dùng AWS Config](#4-chi-phí-khi-dùng-aws-config)
5. [Tài nguyên ECS có thể chặn việc xoá](#5-tài-nguyên-ecs-có-thể-chặn-việc-xoá)
6. [Thời gian xoá sau khi gỡ tài nguyên chặn](#6-thời-gian-xoá-sau-khi-gỡ-tài-nguyên-chặn)
7. [Tóm tắt](#7-tóm-tắt)

---

## 1. Tổng quan

**📘 Theo docs AWS**

Task definition đổi trạng thái khi bạn **tạo (register)**, **deregister** hoặc **xoá** nó. Bạn xem trạng thái trên console, hoặc qua API `DescribeTaskDefinition`.

**💡 Giải thích thêm**

Trạng thái áp dụng cho **từng revision** của task definition (ví dụ `my-app:1`, `my-app:2`…), không phải cho cả "family". Một family có thể có revision `ACTIVE` và revision `INACTIVE` cùng lúc.

---

## 2. Các trạng thái của task definition

### 2.1. `ACTIVE`

**📘 Theo docs AWS**

- Task definition ở trạng thái `ACTIVE` sau khi được **register** với ECS.
- Task definition `ACTIVE` dùng được để **chạy task** hoặc **tạo service**.

### 2.2. `INACTIVE`

**📘 Theo docs AWS**

- Chuyển từ `ACTIVE` sang `INACTIVE` khi bạn **deregister** task definition.
- Vẫn lấy được task definition `INACTIVE` bằng `DescribeTaskDefinition`.
- **Không** chạy được task mới hay tạo service mới với task definition `INACTIVE`.
- **Không ảnh hưởng** tới service hoặc task đang tồn tại.

### 2.3. `DELETE_IN_PROGRESS`

**📘 Theo docs AWS**

- Chuyển từ `INACTIVE` sang `DELETE_IN_PROGRESS` sau khi bạn **gửi yêu cầu xoá** task definition.
- Ở trạng thái này, ECS **định kỳ kiểm tra** xem task definition còn được tham chiếu bởi task hay deployment nào đang hoạt động không, rồi **xoá vĩnh viễn**.
- **Không** chạy được task mới hay tạo service mới với task definition `DELETE_IN_PROGRESS`.
- Có thể gửi yêu cầu xoá **bất cứ lúc nào** mà không ảnh hưởng task và service hiện có.
- Task definition `DELETE_IN_PROGRESS` vẫn **xem được** trên console và lấy được qua `DescribeTaskDefinition`.

**💡 Giải thích thêm**

Vòng đời theo docs:

```
register            deregister              gửi yêu cầu xoá             không còn ai tham chiếu
────────► ACTIVE ─────────────► INACTIVE ──────────────────► DELETE_IN_PROGRESS ──────────────────► (bị xoá vĩnh viễn)
```

Bảng so sánh, chỉ dựa trên những gì docs nêu:

| Trạng thái | Chạy task mới / tạo service mới | Ảnh hưởng task & service đang chạy | Xem được qua `DescribeTaskDefinition` |
|---|---|---|---|
| `ACTIVE` | Được | — | Được |
| `INACTIVE` | **Không** | Không ảnh hưởng | Được |
| `DELETE_IN_PROGRESS` | **Không** | Không ảnh hưởng | Được (và hiện trên console) |

Theo trình tự docs mô tả, muốn xoá thì revision phải đi qua `INACTIVE` trước (deregister rồi mới gửi xoá). API tương ứng là `DeregisterTaskDefinition` và `DeleteTaskDefinitions` — hai tên này là phần mình bổ sung để bạn tra cứu, trang này của docs không nêu.

---

## 3. Tên task definition sau khi xoá

**📘 Theo docs AWS**

- Khi bạn xoá **hết** các revision `INACTIVE`, **tên** task definition không còn hiện trên console và không còn được API trả về.
- Nếu còn một revision ở `DELETE_IN_PROGRESS`, tên vẫn hiện trên console và vẫn được API trả về.
- ECS **giữ lại** tên task definition; lần sau bạn tạo task definition cùng tên, số revision sẽ **tiếp tục tăng**.

**💡 Giải thích thêm**

Ví dụ minh hoạ cho ý cuối: family `my-app` có revision 1–5, bạn xoá hết. Lần sau register lại `my-app`, revision mới sẽ là **6**, không quay về 1. Nhờ vậy một cặp `family:revision` không bao giờ trỏ tới hai nội dung khác nhau theo thời gian.

---

## 4. Chi phí khi dùng AWS Config

**📘 Theo docs AWS**

Nếu dùng **AWS Config** để quản lý task definition:
- AWS Config tính phí cho **mọi lần register** task definition.
- Với deregister, chỉ tính phí khi deregister task definition **`ACTIVE` mới nhất**.
- **Xoá** task definition thì **không mất phí**.

Bảng giá: *AWS Config Pricing* — <https://aws.amazon.com/config/pricing/>.

**💡 Giải thích thêm**

| Thao tác | Phí AWS Config (theo docs) |
|---|---|
| Register | Có, mọi lần |
| Deregister revision `ACTIVE` mới nhất | Có |
| Deregister revision khác | Không |
| Xoá | Không |

Điểm này chỉ liên quan nếu account có bật AWS Config ghi lại tài nguyên task definition. Pipeline CI/CD register revision mới mỗi lần deploy thì mỗi lần đó đều tính phí.

---

## 5. Tài nguyên ECS có thể chặn việc xoá

**📘 Theo docs AWS**

Yêu cầu xoá task definition **không hoàn tất** khi còn tài nguyên ECS phụ thuộc vào revision đó. Các tài nguyên có thể chặn việc xoá:

- **Standalone task** — task cần task definition để tiếp tục khoẻ mạnh.
- **Task của service** — task cần task definition để tiếp tục khoẻ mạnh.
- **Service deployment và task set** — cần task definition khi có sự kiện scaling được khởi động cho deployment hoặc task set đó.

Nếu task definition cứ nằm ở `DELETE_IN_PROGRESS`, dùng console hoặc AWS CLI để **tìm rồi dừng** các tài nguyên đang chặn việc xoá.

**💡 Giải thích thêm**

Lý do của danh sách này: ECS vẫn cần nội dung task definition để vận hành những thứ đang chạy — thay task hỏng, hoặc scale một deployment/task set thêm task mới. Vì thế "gửi xoá" an toàn (không phá service), nhưng việc **xoá thật** phải chờ tới khi không còn ai cần.

```
DELETE_IN_PROGRESS kéo dài?
  ├─ Còn standalone task dùng revision này?        → dừng task
  ├─ Còn task của service dùng revision này?       → service phải chuyển sang revision khác / dừng
  └─ Còn service deployment hoặc task set tham chiếu? → deployment / task set phải bị xoá
```

---

## 6. Thời gian xoá sau khi gỡ tài nguyên chặn

**📘 Theo docs AWS**

Sau khi gỡ các tài nguyên chặn:

| Tài nguyên chặn | Thời gian để việc xoá hoàn tất |
|---|---|
| **ECS task** | Tối đa **1 giờ** sau khi task dừng |
| **Service deployment và task set** | Tối đa **24 giờ** sau khi deployment hoặc task set bị xoá |

**💡 Giải thích thêm**

Vì ECS kiểm tra **định kỳ** (mục 2.3), việc revision vẫn ở `DELETE_IN_PROGRESS` ngay sau khi bạn dừng task là bình thường. Chỉ nên coi là bị kẹt khi đã quá các mốc 1 giờ / 24 giờ ở trên mà tài nguyên chặn thực sự đã được gỡ.

---

## 7. Tóm tắt

- Ba trạng thái: **`ACTIVE`** (sau register, dùng được) → **`INACTIVE`** (sau deregister) → **`DELETE_IN_PROGRESS`** (sau khi gửi xoá) → bị xoá vĩnh viễn.
- `INACTIVE` và `DELETE_IN_PROGRESS`: không chạy task mới hay tạo service mới, **không ảnh hưởng** task/service hiện có, vẫn xem được qua `DescribeTaskDefinition`.
- Xoá hết revision thì tên biến mất khỏi console/API, nhưng ECS **giữ tên** và revision tiếp tục tăng khi tạo lại.
- AWS Config: tính phí mọi lần register, deregister revision `ACTIVE` mới nhất; xoá thì miễn phí.
- Việc xoá bị chặn bởi standalone task, task của service, service deployment và task set đang tham chiếu revision.
- Sau khi gỡ tài nguyên chặn: tối đa 1 giờ (task) hoặc 24 giờ (deployment / task set).

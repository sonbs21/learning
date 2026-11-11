# Best practices for Amazon ECS task sizes

> Nguồn: **Amazon ECS Developer Guide — Best practices for Amazon ECS task sizes**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/capacity-tasksize.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: diễn đạt lại bằng tiếng Việt, bám sát từng ý và đúng thứ tự của trang gốc, không thêm thông tin ngoài trang. Đây không phải bản dịch từng chữ; khi cần câu chữ chính xác, đọc link gốc ở trên.
> - Khối **💡 Giải thích thêm**: phần giải thích để dễ hiểu hơn, chỉ làm rõ những gì docs đã nói.

---

## Mục lục

1. [Kích thước task và đơn vị đo](#1-kích-thước-task-và-đơn-vị-đo)
2. [Reservation (mức đặt trước)](#2-reservation-mức-đặt-trước)
3. [Limit (mức giới hạn)](#3-limit-mức-giới-hạn)
4. [Chọn giá trị: load test là chìa khoá](#4-chọn-giá-trị-load-test-là-chìa-khoá)
5. [Ứng dụng stateless](#5-ứng-dụng-stateless)
6. [Các ứng dụng khác](#6-các-ứng-dụng-khác)
7. [Tóm tắt](#7-tóm-tắt)

---

## 1. Kích thước task và đơn vị đo

**📘 Theo docs AWS**

- Kích thước **container** và **task** đều rất quan trọng cho việc scale và lập kế hoạch capacity.
- Trong ECS, **CPU** và **memory** là hai chỉ số tài nguyên dùng cho capacity.
  - CPU đo bằng đơn vị **1/1024 vCPU**: **1024 đơn vị = 1 vCPU**.
  - Memory đo bằng **mebibyte (MiB)**.
- Trong task definition, bạn cấu hình được **reservation** và **limit** cho tài nguyên.

**💡 Giải thích thêm**

Quy đổi nhanh CPU unit:

| CPU unit | vCPU |
|---|---|
| 256 | 0,25 |
| 512 | 0,5 |
| 1024 | 1 |
| 2048 | 2 |

MiB (mebibyte) = 1024 × 1024 byte, hơi khác MB (1000 × 1000 byte). Ví dụ 512 MiB ≈ 537 MB.

---

## 2. Reservation (mức đặt trước)

**📘 Theo docs AWS**

- Reservation là lượng tài nguyên **tối thiểu** mà task cần. Task được cấp **ít nhất** lượng đã yêu cầu.
- Ứng dụng có thể dùng **nhiều hơn** reservation, nhưng vẫn bị chặn bởi limit (nếu có khai báo). Việc dùng vượt reservation gọi là **bursting**.
- Trong ECS, reservation được **đảm bảo**. Ví dụ: khi dùng EC2 instance làm capacity, ECS **không đặt task** lên instance nào không đáp ứng được reservation.

**💡 Giải thích thêm**

```
0 ──────── reservation ─────────────── limit ──────►
           ▲ luôn được đảm bảo          ▲ trần cứng
           │◄──── vùng bursting ───────►│
```

- Trên EC2, reservation là con số scheduler dùng để **bin-pack**: tổng reservation của các task trên một instance không vượt tài nguyên của instance đó.
- Bursting dùng phần tài nguyên đang trống trên host; không có gì đảm bảo phần burst này luôn sẵn có.

---

## 3. Limit (mức giới hạn)

**📘 Theo docs AWS**

- Limit là lượng CPU unit hoặc memory **tối đa** mà container hoặc task được dùng.
- Dùng CPU vượt limit → bị **throttle** (bóp hiệu năng).
- Dùng memory vượt limit → container **bị dừng**.

**💡 Giải thích thêm**

| Vượt limit | Hậu quả theo docs | Biểu hiện thường thấy |
|---|---|---|
| CPU | Throttle | Ứng dụng chậm đi, latency tăng, nhưng vẫn chạy |
| Memory | Container bị dừng | Task dừng, ECS thay task mới (nếu là service) |

Vì vậy đặt limit memory quá sát mức dùng thực tế nguy hiểm hơn nhiều so với đặt limit CPU sát.

Về tên trường (phần mình bổ sung, trang này không nêu): ở mức container, `memoryReservation` là soft limit (reservation) và `memory` là hard limit; `cpu` khai báo CPU unit. Ở mức task cũng có trường `cpu` và `memory` (bắt buộc với Fargate).

---

## 4. Chọn giá trị: load test là chìa khoá

**📘 Theo docs AWS**

- Chọn các giá trị này **không dễ**, vì giá trị phù hợp nhất phụ thuộc nhiều vào nhu cầu tài nguyên của chính ứng dụng.
- **Load test** ứng dụng là chìa khoá để lập kế hoạch tài nguyên thành công và hiểu rõ hơn nhu cầu của ứng dụng.

**💡 Giải thích thêm**

Không có con số "chuẩn" cho mọi ứng dụng. Docs chia làm hai nhóm, mỗi nhóm có cách tiếp cận riêng (mục 5 và 6), nhưng cả hai đều dựa trên số liệu đo được khi chạy tải thật hoặc tải giả lập.

---

## 5. Ứng dụng stateless

**📘 Theo docs AWS**

Với ứng dụng **stateless scale ngang** (ví dụ ứng dụng đứng sau load balancer):

**Memory:**
- Trước tiên xác định lượng memory ứng dụng tiêu thụ **khi đang phục vụ request**.
- Có thể dùng công cụ truyền thống như `ps`, `top`, hoặc giải pháp giám sát như **CloudWatch Container Insights**.

**CPU reservation** — cân nhắc bạn muốn scale ứng dụng thế nào để đáp ứng yêu cầu nghiệp vụ:
- **Reservation nhỏ** (ví dụ **256 CPU unit = 1/4 vCPU**): scale out **mịn**, tối thiểu chi phí, nhưng có thể **không kịp** với đợt tăng tải lớn.
- **Reservation lớn**: scale in/out **nhanh hơn**, bắt kịp đợt tăng tải nhanh hơn, nhưng **tốn kém hơn**.

**💡 Giải thích thêm**

| | CPU reservation nhỏ (vd 256) | CPU reservation lớn |
|---|---|---|
| Độ mịn khi scale | Cao — thêm từng chút một | Thấp — mỗi bước thêm nhiều |
| Chi phí | Thấp hơn | Cao hơn |
| Đáp ứng đợt tăng tải lớn | Có thể không kịp | Nhanh hơn |

Trực giác đằng sau: muốn tăng thêm 2 vCPU năng lực xử lý, với task 256 unit phải thêm 8 task, với task 1024 unit chỉ thêm 2 task. Ít task phải khởi động hơn thì bắt kịp tải nhanh hơn, nhưng mỗi bước scale "to" hơn nên dễ dư thừa tài nguyên hơn.

Quy trình gợi ý từ docs:

```
1. Load test → đo memory khi phục vụ request (ps / top / Container Insights)
2. Đặt memory dựa trên số đo
3. Chọn CPU reservation theo ưu tiên: tiết kiệm chi phí (nhỏ) ↔ phản ứng nhanh với spike (lớn)
```

---

## 6. Các ứng dụng khác

**📘 Theo docs AWS**

- Với ứng dụng **không scale ngang** — ví dụ **singleton worker** hoặc **database server** — hai yếu tố quan trọng nhất là **capacity sẵn có** và **chi phí**.
- Chọn memory và CPU dựa trên kết quả **load test** cho thấy cần bao nhiêu để phục vụ traffic và đạt **service-level objective (SLO)**.
- ECS bảo đảm ứng dụng được đặt lên host có **đủ capacity**.

**💡 Giải thích thêm**

| | Stateless scale ngang | Không scale ngang |
|---|---|---|
| Ví dụ | App sau load balancer | Singleton worker, database server |
| Tăng năng lực bằng | Thêm task | Không thêm task được → phải đủ lớn từ đầu |
| Yếu tố chính | Memory khi phục vụ request; CPU theo cách muốn scale | Capacity sẵn có và chi phí |
| Căn cứ chọn số | Load test | Load test để đạt SLO |

Với nhóm này, task phải "đủ to" ngay từ đầu, vì không có cách thêm bản sao để chia tải. Câu cuối của docs ("ECS đặt lên host đủ capacity") liên hệ với mục 2: reservation được đảm bảo.

---

## 7. Tóm tắt

- CPU đo bằng 1/1024 vCPU (1024 = 1 vCPU); memory đo bằng MiB.
- **Reservation**: mức tối thiểu, được đảm bảo; ECS không đặt task lên host không đáp ứng được. Dùng vượt reservation = **bursting**.
- **Limit**: mức tối đa; vượt CPU → throttle, vượt memory → container bị dừng.
- Chọn giá trị dựa vào **load test**.
- **Stateless scale ngang**: đo memory khi phục vụ request; CPU nhỏ (vd 256) = scale mịn, rẻ nhưng chậm theo kịp spike; CPU lớn = nhanh hơn nhưng đắt hơn.
- **Không scale ngang** (singleton, database): ưu tiên capacity và chi phí; chọn CPU/memory theo load test để đạt SLO.

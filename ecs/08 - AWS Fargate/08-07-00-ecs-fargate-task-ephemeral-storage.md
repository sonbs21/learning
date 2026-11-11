# Fargate task ephemeral storage for Amazon ECS

> Nguồn: **Amazon ECS Developer Guide — Fargate task ephemeral storage for Amazon ECS**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/fargate-task-storage.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: **bản dịch** sang tiếng Việt, đi đúng thứ tự và đúng nội dung của trang gốc, không thêm, không bớt ý.
> - Khối **💡 Giải thích thêm**: chỉ giải thích lại những gì **chính trang này** đã nói, cho dễ hiểu hơn. Không đưa thông tin từ trang khác hay ví dụ tự đặt.
> - Tên tham số, platform version giữ nguyên tiếng Anh.
> - Tài liệu AWS được phát hành theo giấy phép **Creative Commons Attribution-ShareAlike 4.0**; bản dịch này ghi rõ nguồn ở trên.

---

## Mục lục

0. [Phần mở đầu](#0-phần-mở-đầu)
1. [Platform version 1.4.0 trở lên](#1-platform-version-140-trở-lên)
2. [Platform version 1.3.0 trở về trước](#2-platform-version-130-trở-về-trước)
3. [So sánh hai nhóm platform version](#3-so-sánh-hai-nhóm-platform-version)

---

## 0. Phần mở đầu

**📘 Theo docs AWS**

Khi được cấp phát, mỗi task Amazon ECS chạy **Linux container** trên AWS Fargate nhận ephemeral storage sau đây cho **bind mount**. Storage này có thể được mount và **chia sẻ giữa các container** dùng các tham số **`volumes`**, **`mountPoints`** và **`volumesFrom`** trong task definition. Điều này **không được hỗ trợ** cho **Windows container** trên AWS Fargate.

**💡 Giải thích thêm**

| Điểm | Theo trang |
|---|---|
| Áp dụng cho | **Linux** container trên Fargate (không áp dụng Windows) |
| Dùng cho | Bind mount, chia sẻ giữa các container trong task |
| Tham số task definition | `volumes`, `mountPoints`, `volumesFrom` |

---

## 1. Platform version 1.4.0 trở lên

**📘 Theo docs AWS**

Mặc định, task Amazon ECS chạy trên Fargate dùng platform version **`1.4.0` trở lên** nhận **tối thiểu 20 GiB** ephemeral storage. Tổng lượng ephemeral storage có thể tăng, **tối đa 200 GiB**. Bạn làm việc này bằng cách chỉ định tham số **`ephemeralStorage`** trong task definition.

**Container image đã pull**, cả bản **nén** và **giải nén**, của task được **lưu trên ephemeral storage**. Để xác định tổng lượng ephemeral storage task **thực sự dùng được**, bạn phải **trừ** dung lượng container image chiếm khỏi tổng ephemeral storage được cấp cho task.

Với task dùng platform version `1.4.0` trở lên được khởi chạy **từ ngày 28 tháng 5 năm 2020**, ephemeral storage được mã hoá bằng thuật toán **AES-256**. Thuật toán này dùng một **AWS owned encryption key**, hoặc bạn có thể tạo **customer managed key** của riêng mình. Thông tin thêm, xem *Customer managed keys for AWS Fargate ephemeral storage* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/fargate-storage-encryption.html>).

Với task dùng platform version `1.4.0` trở lên được khởi chạy **từ ngày 18 tháng 11 năm 2022**, **mức dùng ephemeral storage** được báo cáo qua **task metadata endpoint**. Ứng dụng trong task có thể truy vấn **task metadata endpoint version 4** để lấy **kích thước ephemeral storage được dành riêng** và **lượng đã dùng**.

Ngoài ra, kích thước ephemeral storage được dành riêng và lượng đã dùng được gửi tới **Amazon CloudWatch Container Insights** nếu bạn bật Container Insights.

> **Note:** Fargate **dành riêng** không gian trên đĩa. Phần này **chỉ Fargate dùng**. Bạn **không bị tính phí** cho nó. Nó **không hiện** trong các metric này. Tuy nhiên, bạn có thể thấy phần storage bổ sung này trong các công cụ khác như **`df`**.

**💡 Giải thích thêm**

| Điểm | Theo trang |
|---|---|
| Mặc định | ≥ **20 GiB** |
| Tối đa | **200 GiB** (qua `ephemeralStorage`) |
| Image chiếm chỗ | Image đã pull (cả nén lẫn giải nén) nằm **trên** ephemeral storage |
| Mã hoá (task từ 28/5/2020) | AES-256; AWS owned key hoặc customer managed key |
| Báo cáo mức dùng (task từ 18/11/2022) | Task metadata endpoint **v4**; Container Insights (nếu bật) |
| Phần Fargate dành riêng | Không tính phí, không có trong metric, nhưng thấy trong `df` |

Cách tính dung lượng dùng được theo trang:

```
dung lượng dùng được = ephemeral storage được cấp − dung lượng image (nén + giải nén)
```

Ví dụ áp dụng công thức trên: cấp 20 GiB, image nén + giải nén chiếm 6 GiB → ứng dụng còn khoảng **14 GiB**.

Vì phần Fargate dành riêng **có** trong `df` nhưng **không** có trong metric, con số từ `df` và từ metric (task metadata / Container Insights) có thể khác nhau.

---

## 2. Platform version 1.3.0 trở về trước

**📘 Theo docs AWS**

Với task Amazon ECS trên Fargate dùng platform version **`1.3.0` trở về trước**, mỗi task nhận ephemeral storage sau.

- **10 GB** storage cho **Docker layer**

  > **Note:** Lượng này **gồm cả** artifact container image **nén** và **giải nén**.

- Thêm **4 GB** cho **volume mount**. Phần này có thể được mount và chia sẻ giữa các container dùng các tham số `volumes`, `mountPoints` và `volumesFrom` trong task definition.

**💡 Giải thích thêm**

| Phần | Dung lượng | Dùng cho |
|---|---|---|
| Docker layer | 10 GB | Image (nén + giải nén) |
| Volume mount | 4 GB | Chia sẻ giữa container qua `volumes` / `mountPoints` / `volumesFrom` |

---

## 3. So sánh hai nhóm platform version

**💡 Giải thích thêm**

Tổng hợp từ hai mục trên của chính trang:

| | Platform version ≥ 1.4.0 | Platform version ≤ 1.3.0 |
|---|---|---|
| Dung lượng | Một khối chung: ≥ 20 GiB, tăng được tới 200 GiB | Chia hai phần cố định: 10 GB (Docker layer) + 4 GB (volume mount) |
| Image chiếm chỗ ở đâu | Trong khối chung → trừ khỏi tổng | Trong phần 10 GB |
| Tăng được không | Có (`ephemeralStorage`) | (trang không nêu) |
| Mã hoá | AES-256 (task từ 28/5/2020), có thể dùng customer managed key | (trang không nêu) |
| Báo cáo mức dùng | Task metadata endpoint v4 (task từ 18/11/2022), Container Insights | (trang không nêu) |

Trang dùng hai đơn vị khác nhau: **GiB** cho platform version ≥ 1.4.0 và **GB** cho ≤ 1.3.0; bản dịch giữ nguyên như trang gốc.

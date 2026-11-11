# Optimize Amazon ECS task launch time

> Nguồn: **Amazon ECS Developer Guide — Optimize Amazon ECS task launch time**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task-recommendations.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: **bản dịch** sang tiếng Việt, đi đúng thứ tự và đúng nội dung của trang gốc, không thêm, không bớt ý.
> - Khối **💡 Giải thích thêm**: chỉ giải thích lại những gì **chính trang này** đã nói, cho dễ hiểu hơn. Không đưa thông tin từ trang khác hay ví dụ tự đặt.
> - Tên tham số, network mode, strategy, instance type giữ nguyên tiếng Anh.
> - Tài liệu AWS được phát hành theo giấy phép **Creative Commons Attribution-ShareAlike 4.0**; bản dịch này ghi rõ nguồn ở trên.

---

## Mục lục

0. [Phần mở đầu](#0-phần-mở-đầu)
1. [Cache container image và binpack instance](#1-cache-container-image-và-binpack-instance)
2. [Chọn network mode tối ưu](#2-chọn-network-mode-tối-ưu)
3. [Theo dõi vòng đời khởi chạy task để tìm cơ hội tối ưu](#3-theo-dõi-vòng-đời-khởi-chạy-task-để-tìm-cơ-hội-tối-ưu)
4. [Chọn instance type tối ưu (cho EC2)](#4-chọn-instance-type-tối-ưu-cho-ec2)
5. [Tổng hợp](#5-tổng-hợp)

---

## 0. Phần mở đầu

**📘 Theo docs AWS**

Để tăng tốc việc khởi chạy task, hãy cân nhắc các khuyến nghị sau.

---

## 1. Cache container image và binpack instance

**📘 Theo docs AWS**

Nếu dùng EC2, bạn có thể cấu hình hành vi pull của Amazon ECS container agent thành `ECS_IMAGE_PULL_BEHAVIOR`: `prefer-cached`. Image được pull từ xa nếu chưa có image cache. Ngược lại, image đã cache trên instance được dùng. Việc **tự động dọn image** bị tắt cho container để đảm bảo image đã cache không bị xoá. Điều này giảm thời gian pull image cho các lần khởi chạy sau.

Tác dụng của cache còn lớn hơn khi bạn có **mật độ task cao** trên container instance, điều bạn có thể cấu hình bằng placement strategy **`binpack`**. Cache container image đặc biệt có lợi cho workload **Windows**, vốn thường có container image rất lớn (hàng chục GB).

Khi dùng placement strategy `binpack`, bạn cũng có thể cân nhắc dùng **Elastic Network Interface (ENI) trunking** để đặt nhiều task network mode `awsvpc` hơn trên mỗi container instance. ENI trunking tăng số task bạn có thể chạy ở mode `awsvpc`. Ví dụ, một instance **c5.large** có thể chỉ hỗ trợ chạy đồng thời **2** task, có thể chạy tới **10** task với ENI trunking.

**💡 Giải thích thêm**

`ECS_IMAGE_PULL_BEHAVIOR=prefer-cached` theo đoạn trên:

| Có image cache trên instance? | Hành vi |
|---|---|
| Có | Dùng image cache |
| Không | Pull từ xa |
| Dọn image tự động | **Tắt** — để image cache không bị xoá |

Ba thứ bổ trợ nhau theo trang:

```
prefer-cached         → lần sau không phải pull lại image
binpack               → nhiều task trên cùng instance → nhiều task hưởng cache hơn
ENI trunking (awsvpc) → instance chứa được nhiều task awsvpc hơn (vd c5.large: 2 → 10)
```

Lợi ích lớn nhất với **image Windows** (hàng chục GB).

---

## 2. Chọn network mode tối ưu

**📘 Theo docs AWS**

Dù có nhiều trường hợp network mode `awsvpc` là lý tưởng, mode này về bản chất có thể **làm tăng độ trễ khởi chạy task**, vì với mỗi task ở mode `awsvpc`, quy trình của Amazon ECS cần **cấp và gắn một ENI** bằng cách gọi API của Amazon EC2, việc này thêm **vài giây** vào thời gian khởi chạy task. Ngược lại, một ưu điểm then chốt của network mode `awsvpc` là mỗi task có một **security group** để cho phép hoặc từ chối traffic. Nghĩa là bạn linh hoạt hơn trong việc kiểm soát giao tiếp giữa các task và service ở mức chi tiết hơn. Nếu **tốc độ deploy** là ưu tiên, bạn có thể cân nhắc dùng mode **`bridge`** để tăng tốc khởi chạy task. Thông tin thêm, xem *Allocate a network interface for an Amazon ECS task* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task-networking-awsvpc.html>).

**💡 Giải thích thêm**

| | `awsvpc` | `bridge` |
|---|---|---|
| Khởi chạy task | Chậm hơn vài giây — ECS gọi API EC2 để cấp và gắn ENI cho mỗi task | Nhanh hơn |
| Kiểm soát traffic | Security group **riêng mỗi task** — kiểm soát chi tiết | (trang không nêu) |
| Trang gợi ý khi | Cần kiểm soát giao tiếp chi tiết | **Tốc độ deploy** là ưu tiên |

---

## 3. Theo dõi vòng đời khởi chạy task để tìm cơ hội tối ưu

**📘 Theo docs AWS**

Thường khó biết ứng dụng mất bao lâu để khởi động. Việc khởi chạy container image, chạy script khởi động và các cấu hình khác trong lúc ứng dụng khởi động có thể tốn thời gian đáng ngạc nhiên. Bạn có thể dùng **Task metadata endpoint** để đẩy metric theo dõi thời gian khởi động ứng dụng, tính từ **`StartedAt`** trong response metadata của **container** tới thời điểm **`StartedAt`** của **task** hoặc service. Với dữ liệu này, bạn hiểu được ứng dụng đóng góp bao nhiêu vào tổng thời gian khởi chạy, và tìm ra chỗ có thể giảm overhead không cần thiết riêng của ứng dụng và tối ưu container image. Thông tin thêm, xem *Amazon ECS Auto scaling and capacity management best practices* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/capacity-availability.html>).

**💡 Giải thích thêm**

Những thứ trang nêu là tốn thời gian khi ứng dụng khởi động:

| Nguồn tốn thời gian |
|---|
| Khởi chạy container image |
| Chạy script khởi động |
| Các cấu hình khác lúc khởi động |

Cách đo theo trang: dùng **Task metadata endpoint**, so mốc **`StartedAt`** của container với mốc **`StartedAt`** của task (hoặc service), rồi đẩy thành metric. Mục đích: biết phần thời gian **do ứng dụng gây ra** trong tổng thời gian khởi chạy, để cắt overhead và tối ưu image.

---

## 4. Chọn instance type tối ưu (cho EC2)

**📘 Theo docs AWS**

Việc chọn đúng instance type dựa trên **lượng tài nguyên dành riêng (reservation)** — ví dụ CPU, memory — mà bạn cấu hình cho task. Vì vậy, khi chọn kích thước instance, bạn có thể tính được bao nhiêu task đặt vừa trên một instance. Một ví dụ đơn giản về task được đặt hợp lý là chạy **4** task, mỗi task cần reservation **0.5 vCPU** và **2GB memory**, trên một instance **m5.large** (hỗ trợ **2 vCPU** và **8 GB memory**). Reservation của task definition này tận dụng trọn vẹn tài nguyên của instance.

**💡 Giải thích thêm**

Phép tính của ví dụ trên trang:

| | Mỗi task | × 4 task | m5.large |
|---|---|---|---|
| vCPU | 0.5 | 2 | 2 |
| Memory | 2 GB | 8 GB | 8 GB |

Tổng reservation của 4 task **bằng đúng** tài nguyên của m5.large → không thừa.

---

## 5. Tổng hợp

**💡 Giải thích thêm**

Tổng hợp từ các mục trên của chính trang:

| Khuyến nghị | Áp dụng cho | Ý chính |
|---|---|---|
| Cache image (`prefer-cached`) + `binpack` | EC2 | Giảm thời gian pull image; hiệu quả hơn khi mật độ task cao; lợi nhất với image Windows |
| ENI trunking | EC2, mode `awsvpc` | Nhiều task `awsvpc` hơn mỗi instance (vd c5.large: 2 → 10) |
| Chọn network mode | — | `awsvpc` thêm vài giây (cấp ENI) nhưng có security group mỗi task; `bridge` nhanh hơn nếu ưu tiên tốc độ deploy |
| Theo dõi vòng đời khởi chạy | — | Task metadata endpoint, so `StartedAt` container với task/service |
| Chọn instance type | EC2 | Tính số task vừa một instance theo reservation (vd 4 × 0.5 vCPU/2 GB trên m5.large) |

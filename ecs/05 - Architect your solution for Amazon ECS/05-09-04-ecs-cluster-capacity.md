# Amazon ECS cluster capacity

> Nguồn: **Amazon ECS Developer Guide — Amazon ECS cluster capacity**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/capacity-cluster-best-practice.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: **bản dịch** sang tiếng Việt, đi đúng thứ tự và đúng nội dung của trang gốc, không thêm, không bớt ý.
> - Khối **💡 Giải thích thêm**: chỉ giải thích lại những gì **chính trang này** đã nói, cho dễ hiểu hơn. Không đưa thông tin từ trang khác hay ví dụ tự đặt.
> - Tên capacity provider, dịch vụ giữ nguyên tiếng Anh.
> - Tài liệu AWS được phát hành theo giấy phép **Creative Commons Attribution-ShareAlike 4.0**; bản dịch này ghi rõ nguồn ở trên.

---

## Mục lục

1. [Các cách cung cấp capacity](#1-các-cách-cung-cấp-capacity)
2. [Fargate và Fargate Spot](#2-fargate-và-fargate-spot)
3. [Amazon EC2 capacity provider](#3-amazon-ec2-capacity-provider)
4. [Tổng hợp](#4-tổng-hợp)

---

## 1. Các cách cung cấp capacity

**📘 Theo docs AWS**

Bạn có thể cung cấp capacity cho một Amazon ECS cluster theo nhiều cách. Ví dụ, bạn có thể launch Amazon EC2 instance và đăng ký chúng vào cluster lúc khởi động bằng Amazon ECS container agent. Tuy nhiên, cách này có thể khó vì bạn phải **tự quản lý việc scale**. Vì vậy, chúng tôi khuyến nghị dùng **Amazon ECS capacity provider**. Capacity provider quản lý việc scale tài nguyên cho bạn. Có ba loại capacity provider: **Amazon EC2**, **Fargate** và **Fargate Spot**. Thông tin thêm về capacity provider Fargate, xem *Amazon ECS clusters for Fargate workloads* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/fargate-capacity-providers.html>), và cho workload EC2, xem *Amazon ECS clusters for EC2 workloads* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/asg-capacity-providers.html>).

**💡 Giải thích thêm**

| Cách cung cấp capacity | Ai lo scale |
|---|---|
| Tự launch EC2 instance, đăng ký vào cluster qua container agent | **Bạn** — trang nói cách này khó |
| **Capacity provider** (khuyến nghị) | Capacity provider |

Ba loại capacity provider trang liệt kê: Amazon EC2, Fargate, Fargate Spot.

---

## 2. Fargate và Fargate Spot

**📘 Theo docs AWS**

Capacity provider Fargate và Fargate Spot **tự lo vòng đời** của task Fargate. Fargate cung cấp capacity **on-demand**, còn Fargate Spot cung cấp capacity **Spot**. Khi bạn khởi chạy một task, Amazon ECS cấp một tài nguyên Fargate cho bạn. Tài nguyên Fargate này đi kèm memory và CPU unit **tương ứng trực tiếp** với limit mức task mà bạn khai báo trong task definition. Mỗi task nhận **tài nguyên Fargate riêng**, tạo quan hệ **1:1** giữa task và tài nguyên compute.

Task chạy trên Fargate Spot có thể bị **gián đoạn**. Gián đoạn xảy ra sau **cảnh báo trước hai phút**. Chúng xảy ra trong giai đoạn nhu cầu cao. Fargate Spot phù hợp nhất cho workload **chịu được gián đoạn** như batch job, môi trường phát triển hoặc staging. Chúng cũng phù hợp với mọi kịch bản khác **không yêu cầu** tính sẵn sàng cao và độ trễ thấp.

Bạn có thể chạy task Fargate Spot **cùng với** task Fargate on-demand. Khi dùng chung, bạn có được capacity "bùng nổ" (burst) với chi phí thấp hơn.

**💡 Giải thích thêm**

```
Task definition (limit mức task: CPU, memory)
        │
        ▼
Một tài nguyên Fargate riêng, đúng CPU/memory đó   ← quan hệ 1 task : 1 tài nguyên
```

| | Fargate | Fargate Spot |
|---|---|---|
| Loại capacity | On-demand | Spot |
| Gián đoạn | — | Có, báo trước 2 phút, khi nhu cầu cao |
| Phù hợp | — | Batch job, dev/staging, mọi thứ không cần sẵn sàng cao và độ trễ thấp |
| Dùng chung | Chạy được cùng nhau → có capacity burst với chi phí thấp hơn | |

---

## 3. Amazon EC2 capacity provider

**📘 Theo docs AWS**

Amazon ECS cũng có thể quản lý capacity Amazon EC2 instance cho task của bạn. Mỗi Amazon EC2 capacity provider gắn với một **Amazon EC2 Auto Scaling group** mà bạn chỉ định. Khi dùng Amazon EC2 capacity provider, **cluster auto scaling** duy trì kích thước của Amazon EC2 Auto Scaling group để đảm bảo **mọi task đã được lên lịch đều đặt được**.

**💡 Giải thích thêm**

```
EC2 capacity provider ─── gắn với ───► Auto Scaling group (bạn chỉ định)
                                            ▲
             cluster auto scaling điều chỉnh kích thước
             để mọi task đã lên lịch đều có chỗ đặt
```

---

## 4. Tổng hợp

**💡 Giải thích thêm**

Tổng hợp từ các mục trên của chính trang:

| Capacity provider | Ai lo gì | Đặc điểm theo trang |
|---|---|---|
| **Fargate** | ECS cấp tài nguyên Fargate cho từng task | On-demand; mỗi task một tài nguyên, đúng CPU/memory của task definition |
| **Fargate Spot** | Như Fargate | Spot; có thể bị gián đoạn (báo trước 2 phút); hợp batch, dev/staging; chạy cùng Fargate để có burst rẻ hơn |
| **Amazon EC2** | Cluster auto scaling điều chỉnh Auto Scaling group | Mỗi capacity provider gắn một Auto Scaling group; đảm bảo mọi task đã lên lịch đều đặt được |

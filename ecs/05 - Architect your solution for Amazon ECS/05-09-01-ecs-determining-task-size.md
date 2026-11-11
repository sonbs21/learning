# Determining the task size for Amazon ECS

> Nguồn: **Amazon ECS Developer Guide — Determining the task size for Amazon ECS**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/capacity-tasksize-best-practice.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: **bản dịch** sang tiếng Việt, đi đúng thứ tự và đúng nội dung của trang gốc, không thêm, không bớt ý.
> - Khối **💡 Giải thích thêm**: chỉ giải thích lại những gì **chính trang này** đã nói, cho dễ hiểu hơn. Không đưa thông tin từ trang khác hay ví dụ tự đặt.
> - Thuật ngữ reservation, limit, bursting giữ nguyên tiếng Anh kèm giải nghĩa.
> - Tài liệu AWS được phát hành theo giấy phép **Creative Commons Attribution-ShareAlike 4.0**; bản dịch này ghi rõ nguồn ở trên.

---

## Mục lục

0. [Phần mở đầu](#0-phần-mở-đầu)
1. [Ứng dụng stateless](#1-ứng-dụng-stateless)
2. [Các ứng dụng khác](#2-các-ứng-dụng-khác)

---

## 0. Phần mở đầu

**📘 Theo docs AWS**

Một trong những lựa chọn quan trọng nhất khi deploy container trên Amazon ECS là **kích thước container và task**. Kích thước container và task là yếu tố cốt lõi cho việc scale và lập kế hoạch capacity.

Amazon ECS dùng hai thước đo tài nguyên cho capacity: **CPU** và **memory**. Amazon ECS đo CPU theo đơn vị **1/1024 của một vCPU đầy đủ** (1024 đơn vị bằng 1 vCPU trọn vẹn). Amazon ECS đo memory theo **megabyte**.

Trong task definition, bạn có thể khai báo **reservation** (dành riêng) và **limit** (giới hạn) cho tài nguyên.

Khi khai báo reservation, bạn khai báo lượng tài nguyên **tối thiểu** mà task cần. Task của bạn nhận **ít nhất** lượng tài nguyên bạn yêu cầu. Ứng dụng có thể dùng nhiều CPU hoặc memory hơn mức reservation bạn khai báo. Tuy nhiên, điều này phụ thuộc vào limit mà bạn cũng đã khai báo.

Dùng nhiều hơn lượng reservation được gọi là **bursting**. Bursting nghĩa là ứng dụng dùng nhiều tài nguyên hơn mức bạn đã dành riêng nhưng vẫn nằm trong limit đã khai báo. Amazon ECS **đảm bảo** reservation. Ví dụ, nếu bạn dùng Amazon EC2 instance để cung cấp capacity, Amazon ECS **không đặt** task lên instance không thể đáp ứng reservation.

**Limit** là lượng CPU unit hoặc memory **tối đa** mà container hoặc task có thể dùng. Nếu container cố dùng nhiều CPU hơn limit này, Amazon ECS **throttle** nó. Nếu container cố dùng nhiều memory hơn limit này, Amazon ECS **dừng** container.

Việc chọn các giá trị này có thể khó. Giá trị phù hợp nhất cho ứng dụng phụ thuộc nhiều vào yêu cầu tài nguyên của chính ứng dụng.

**Load test** ứng dụng là chìa khoá để lập kế hoạch yêu cầu tài nguyên thành công. Load test giúp bạn hiểu rõ hơn yêu cầu của ứng dụng.

**💡 Giải thích thêm**

| Tài nguyên | Đơn vị đo theo trang |
|---|---|
| CPU | 1/1024 vCPU (1024 đơn vị = 1 vCPU) |
| Memory | Megabyte |

Ba khái niệm:

| Khái niệm | Nghĩa | ECS làm gì |
|---|---|---|
| **Reservation** | Lượng **tối thiểu** task cần | **Đảm bảo** — không đặt task lên instance không đủ |
| **Bursting** | Dùng **vượt** reservation nhưng **trong** limit | Cho phép |
| **Limit** | Lượng **tối đa** được dùng | CPU vượt → **throttle**; memory vượt → **dừng** container |

```
0 ─────── reservation ─────────────── limit ──────►
          (đảm bảo có)   bursting     CPU: throttle
                                      memory: container bị dừng
```

Cách chọn giá trị mà trang khuyến nghị: **load test** ứng dụng.

---

## 1. Ứng dụng stateless

**📘 Theo docs AWS**

Với ứng dụng stateless scale theo chiều ngang, chẳng hạn ứng dụng phía sau load balancer, chúng tôi khuyến nghị trước tiên hãy xác định ứng dụng **tiêu thụ bao nhiêu memory** khi phục vụ request.

Để làm vậy, bạn có thể dùng các công cụ truyền thống như `ps` hoặc `top`. Bạn cũng có thể dùng giải pháp giám sát như CloudWatch Container Insights.

Khi xác định **CPU reservation**, hãy cân nhắc cách bạn muốn scale ứng dụng để đáp ứng yêu cầu nghiệp vụ.

Bạn có thể dùng CPU reservation **nhỏ hơn**, như **256 CPU unit** (hay **1/4 vCPU**), để scale out một cách **chi tiết** và giảm tối đa chi phí. Nhưng có thể không scale đủ nhanh để đáp ứng các đợt tăng nhu cầu đáng kể.

Bạn có thể dùng CPU reservation **lớn hơn** để scale in và scale out **nhanh hơn**. Điều này giúp bạn bắt kịp các đợt tăng nhu cầu nhanh hơn. Tuy nhiên, CPU reservation lớn hơn **tốn chi phí hơn**.

**💡 Giải thích thêm**

Thứ tự trang khuyến nghị cho ứng dụng stateless:

```
1. Đo memory khi đang phục vụ request  (ps, top, CloudWatch Container Insights)
2. Chọn CPU reservation theo cách muốn scale
```

| CPU reservation | Ưu | Nhược |
|---|---|---|
| **Nhỏ** (vd 256 unit = 1/4 vCPU) | Scale out chi tiết, chi phí thấp | Có thể không kịp với đợt tăng đột biến |
| **Lớn** | Scale in/out nhanh, bắt kịp đột biến | Tốn chi phí hơn |

---

## 2. Các ứng dụng khác

**📘 Theo docs AWS**

Với ứng dụng **không** scale theo chiều ngang, chẳng hạn singleton worker hoặc database server, **capacity khả dụng** và **chi phí** là những cân nhắc quan trọng nhất.

Chọn lượng memory và CPU dựa trên những gì **load test** cho thấy bạn cần để phục vụ traffic và đáp ứng **service-level objective**. Amazon ECS đảm bảo ứng dụng được đặt lên host có đủ capacity.

**💡 Giải thích thêm**

| Loại ứng dụng | Ví dụ theo trang | Cách chọn kích thước |
|---|---|---|
| Stateless, scale ngang | Ứng dụng sau load balancer | Đo memory khi phục vụ request; chọn CPU reservation theo cách muốn scale |
| Không scale ngang | Singleton worker, database server | Theo kết quả load test để phục vụ traffic và đạt service-level objective; ưu tiên capacity khả dụng và chi phí |

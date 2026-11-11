# Speeding up Amazon ECS cluster capacity provisioning with capacity providers on Amazon EC2

> Nguồn: **Amazon ECS Developer Guide — Speeding up Amazon ECS cluster capacity provisioning with capacity providers on Amazon EC2**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/capacity-cluster-speed-up-ec2-best-practice.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: **bản dịch** sang tiếng Việt, đi đúng thứ tự và đúng nội dung của trang gốc, không thêm, không bớt ý.
> - Khối **💡 Giải thích thêm**: chỉ giải thích lại những gì **chính trang này** đã nói, cho dễ hiểu hơn. Không đưa thông tin từ trang khác hay ví dụ tự đặt.
> - Tên tham số, API, metric giữ nguyên tiếng Anh.
> - Tài liệu AWS được phát hành theo giấy phép **Creative Commons Attribution-ShareAlike 4.0**; bản dịch này ghi rõ nguồn ở trên.

---

## Mục lục

0. [Phần mở đầu](#0-phần-mở-đầu)
1. [Kích thước bước scale của capacity provider](#1-kích-thước-bước-scale-của-capacity-provider)
2. [Thời gian khởi động instance (warm-up)](#2-thời-gian-khởi-động-instance-warm-up)
3. [Capacity dự phòng](#3-capacity-dự-phòng)
4. [Tổng hợp](#4-tổng-hợp)

---

## 0. Phần mở đầu

**📘 Theo docs AWS**

Khách hàng chạy Amazon ECS trên Amazon EC2 có thể tận dụng **Amazon ECS Cluster Auto Scaling (CAS)** (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/cluster-auto-scaling.html>) để quản lý việc scale các Amazon EC2 Auto Scaling group (ASG). Với CAS, bạn có thể cấu hình Amazon ECS tự động scale ASG, và chỉ cần tập trung chạy task. Amazon ECS sẽ đảm bảo ASG scale in và out khi cần mà không cần can thiệp thêm. Amazon ECS capacity provider được dùng để quản lý hạ tầng trong cluster bằng cách đảm bảo có đủ container instance để đáp ứng nhu cầu của ứng dụng. Để hiểu cách Amazon ECS CAS hoạt động, xem *Deep Dive on Amazon ECS Cluster Auto Scaling* (<https://aws.amazon.com/blogs/containers/deep-dive-on-amazon-ecs-cluster-auto-scaling/>).

Vì CAS dựa vào tích hợp với ASG thông qua CloudWatch để điều chỉnh capacity của cluster, nó có độ trễ vốn có liên quan tới: việc **đẩy CloudWatch metric**, thời gian để metric **`CapacityProviderReservation`** vượt các CloudWatch alarm (cả cao lẫn thấp), và thời gian để một Amazon EC2 instance mới launch **khởi động (warm-up)**. Bạn có thể thực hiện các hành động sau để CAS phản hồi nhanh hơn, giúp deploy nhanh hơn:

**💡 Giải thích thêm**

Ba nguồn độ trễ của CAS theo trang:

```
1. Đẩy CloudWatch metric
2. Chờ CapacityProviderReservation vượt alarm (cao hoặc thấp)
3. Chờ EC2 instance mới warm-up
```

Ba hành động tương ứng ở các mục sau: kích thước bước scale (mục 1), thời gian warm-up (mục 2), capacity dự phòng (mục 3).

---

## 1. Kích thước bước scale của capacity provider

**📘 Theo docs AWS**

Amazon ECS capacity provider cuối cùng sẽ tăng/giảm số container instance để đáp ứng nhu cầu của ứng dụng. Số instance **tối thiểu** mà Amazon ECS launch mỗi lần được đặt mặc định là **1**. Điều này có thể làm deploy lâu hơn nếu cần nhiều instance để đặt các task đang chờ. Bạn có thể tăng **`minimumScalingStepSize`** (<https://docs.aws.amazon.com/AmazonECS/latest/APIReference/API_ManagedScaling.html>) bằng Amazon ECS API để tăng số instance tối thiểu mà Amazon ECS scale in hoặc out mỗi lần. Một **`maximumScalingStepSize`** (<https://docs.aws.amazon.com/AmazonECS/latest/APIReference/API_ManagedScaling.html>) quá thấp có thể giới hạn số container instance được scale in hoặc out mỗi lần, làm deploy chậm đi.

> **Note:** Cấu hình này hiện **chỉ** có qua API **`CreateCapacityProvider`** (<https://docs.aws.amazon.com/AmazonECS/latest/APIReference/API_CreateCapacityProvider.html>) hoặc **`UpdateCapacityProvider`** (<https://docs.aws.amazon.com/AmazonECS/latest/APIReference/API_UpdateCapacityProvider.html>).

**💡 Giải thích thêm**

| Tham số | Mặc định theo trang | Ảnh hưởng |
|---|---|---|
| `minimumScalingStepSize` | 1 instance | Thấp → khi cần nhiều instance, phải scale nhiều lần → deploy lâu. **Tăng lên** để mỗi lần thêm nhiều instance hơn |
| `maximumScalingStepSize` | (trang không nêu) | **Quá thấp** → giới hạn số instance mỗi lần → deploy chậm |

Chỉnh được qua `CreateCapacityProvider` / `UpdateCapacityProvider` (theo Note).

---

## 2. Thời gian khởi động instance (warm-up)

**📘 Theo docs AWS**

**Instance warm-up period** là khoảng thời gian sau đó một Amazon EC2 instance mới launch mới được tính vào CloudWatch metric của Auto Scaling group. Sau khi thời gian warm-up chỉ định hết, instance được tính vào metric tổng hợp của ASG, và CAS tiếp tục vòng tính toán tiếp theo để ước tính số instance cần.

Giá trị mặc định của **`instanceWarmupPeriod`** (<https://docs.aws.amazon.com/AmazonECS/latest/APIReference/API_ManagedScaling.html#ECS-Type-ManagedScaling-instanceWarmupPeriod>) là **300 giây**, bạn có thể đặt **thấp hơn** bằng API `CreateCapacityProvider` (<https://docs.aws.amazon.com/AmazonECS/latest/APIReference/API_CreateCapacityProvider.html>) hoặc `UpdateCapacityProvider` (<https://docs.aws.amazon.com/AmazonECS/latest/APIReference/API_UpdateCapacityProvider.html>) để scale phản hồi nhanh hơn.

**💡 Giải thích thêm**

```
EC2 instance mới launch
   │  ← instanceWarmupPeriod (mặc định 300 giây): chưa tính vào metric của ASG
   ▼
được tính vào metric ─► CAS tính vòng tiếp theo (cần thêm bao nhiêu instance)
```

Giảm `instanceWarmupPeriod` → instance mới được tính sớm hơn → CAS ra quyết định tiếp theo sớm hơn.

---

## 3. Capacity dự phòng

**📘 Theo docs AWS**

Nếu capacity provider không có container instance nào sẵn để đặt task, nó cần tăng (scale out) capacity cluster bằng cách launch Amazon EC2 instance ngay lúc đó, và chờ chúng khởi động xong rồi mới khởi chạy container trên đó. Điều này có thể làm **giảm đáng kể** tốc độ khởi chạy task. Bạn có hai lựa chọn ở đây.

Trong trường hợp này, có sẵn **capacity Amazon EC2 dự phòng** đã launch và sẵn sàng chạy task sẽ tăng tốc độ khởi chạy task thực tế. Bạn có thể dùng cấu hình **`Target Capacity`** để cho biết bạn muốn duy trì capacity dự phòng trong cluster. Ví dụ, đặt `Target Capacity` ở **80%** nghĩa là cluster cần **20% capacity dự phòng** mọi lúc. Capacity dự phòng này cho phép mọi standalone task được khởi chạy **ngay**, đảm bảo việc khởi chạy task không bị throttle. Đánh đổi của cách này là có thể **tăng chi phí** do giữ capacity cluster dự phòng.

Một cách thay thế bạn có thể cân nhắc là thêm **khoảng dư cho service**, không phải cho capacity provider. Nghĩa là thay vì giảm cấu hình `Target Capacity` để có capacity dự phòng, bạn có thể **tăng số replica** của service bằng cách sửa metric target tracking scaling hoặc các ngưỡng step scaling của service auto scaling. Lưu ý cách này **chỉ hữu ích cho workload có đột biến (spiky)**, nhưng **không có tác dụng** khi bạn deploy service mới và đi từ **0 lên N task** lần đầu. Thông tin thêm về các scaling policy liên quan, xem *Target Tracking Scaling Policies* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/service-autoscaling-targettracking.html>) hoặc *Step Scaling Policies* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/service-autoscaling-stepscaling.html>).

**💡 Giải thích thêm**

Hai lựa chọn theo trang:

| | Dư ở **capacity provider** | Dư ở **service** |
|---|---|---|
| Cách làm | Đặt `Target Capacity` < 100% (vd 80% → 20% dự phòng) | Tăng số replica qua target tracking metric hoặc ngưỡng step scaling |
| Lợi ích | Standalone task khởi chạy ngay, không bị throttle | Đỡ được workload đột biến |
| Hạn chế | Tốn chi phí giữ capacity dự phòng | **Không** giúp khi deploy service mới từ 0 lên N task |

---

## 4. Tổng hợp

**💡 Giải thích thêm**

Tổng hợp từ các mục trên của chính trang:

| Hành động | Tham số / cấu hình | Mặc định theo trang | Cách chỉnh |
|---|---|---|---|
| Tăng bước scale tối thiểu | `minimumScalingStepSize` | 1 | API `CreateCapacityProvider` / `UpdateCapacityProvider` |
| Không để bước scale tối đa quá thấp | `maximumScalingStepSize` | (không nêu) | API `CreateCapacityProvider` / `UpdateCapacityProvider` |
| Giảm thời gian warm-up | `instanceWarmupPeriod` | 300 giây | API `CreateCapacityProvider` / `UpdateCapacityProvider` |
| Giữ capacity dự phòng | `Target Capacity` (vd 80%) | (không nêu) | Cấu hình capacity provider |
| Hoặc dư ở service | Target tracking / step scaling của service | — | Service auto scaling |

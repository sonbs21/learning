# Amazon ECS clusters

> Nguồn: **Amazon ECS Developer Guide — Amazon ECS clusters**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/clusters.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: diễn đạt lại bằng tiếng Việt, bám sát từng ý và đúng thứ tự của trang gốc, không thêm thông tin ngoài trang. Đây không phải bản dịch từng chữ; khi cần câu chữ chính xác, đọc link gốc ở trên.
> - Khối **💡 Giải thích thêm**: phần giải thích để dễ hiểu hơn, chỉ làm rõ những gì docs đã nói.

---

## Mục lục

0. [Cluster là gì](#0-cluster-là-gì)
1. [Chọn loại hạ tầng cho cluster](#1-chọn-loại-hạ-tầng-cho-cluster)
2. [Thành phần của cluster](#2-thành-phần-của-cluster)
3. [Khái niệm về cluster](#3-khái-niệm-về-cluster)
4. [Capacity provider](#4-capacity-provider)
5. [Chỗ không khớp trên trang](#5-chỗ-không-khớp-trên-trang)
6. [Liên hệ với các file trước](#6-liên-hệ-với-các-file-trước)
7. [Tóm tắt](#7-tóm-tắt)

---

## 0. Cluster là gì

**📘 Theo docs AWS**

- **ECS cluster** là một **nhóm logic** các task hoặc service, cung cấp **capacity hạ tầng** cho ứng dụng container.
- Khi tạo cluster, bạn chọn một trong **ba loại hạ tầng chính**, mỗi loại tối ưu cho trường hợp sử dụng và yêu cầu vận hành khác nhau.

---

## 1. Chọn loại hạ tầng cho cluster

**📘 Theo docs AWS**

| Loại | Mô tả | Dùng khi |
|---|---|---|
| **Amazon ECS Managed Instances** (**khuyến nghị**) | **Phù hợp đa số workload**. AWS quản lý hoàn toàn EC2 instance bên dưới: cấp phát, vá, scale. Cân bằng tốt nhất giữa hiệu năng, chi phí và đơn giản vận hành | Muốn AWS lo hạ tầng; cần compute tiết kiệm có tối ưu tự động; muốn tập trung vào ứng dụng; cần hiệu năng ổn định với scale linh hoạt |
| **Fargate** | **Serverless** — chỉ trả cho tài nguyên task dùng, không quản lý hạ tầng. Hợp với workload biến động và bắt đầu nhanh | Muốn hoàn toàn serverless; workload khó đoán hoặc biến động; muốn giảm tối đa vận hành; cần deploy và scale nhanh |
| **Amazon EC2 instances** | **Toàn quyền kiểm soát** — tự quản lý EC2 instance: chọn instance, cấu hình, bảo trì | Cần instance type hoặc cấu hình cụ thể; có hạ tầng EC2 sẵn muốn tận dụng; cần **custom AMI** hoặc phần mềm đặc thù; cần kiểm soát tối đa |

> **Note (docs):** **Managed Instances** là lựa chọn **khuyến nghị** cho đa số workload **mới**, vì kết hợp tốt nhất hiệu năng, tối ưu chi phí và đơn giản vận hành, trong khi AWS lo quản lý hạ tầng.

**💡 Giải thích thêm**

Đây là "điểm vào" của cả loạt file. Bảng quyết định nhanh, ghép với các file trước:

| Nếu bạn cần… | Chọn |
|---|---|
| Chạy nhanh, không quan tâm instance, tải lên xuống thất thường | **Fargate** |
| GPU, ARM chọn được, nhiều task/instance để rẻ, mà không muốn tự vá AMI | **Managed Instances** |
| Windows container, custom AMI, `bridge`/dynamic port, Docker volume, swap, placement strategy, instance chạy lâu dài | **EC2** |
| Task chạy liên tục > 14 ngày trên cùng máy | **EC2** hoặc **Fargate** (không phải Managed Instances) |

---

## 2. Thành phần của cluster

**📘 Theo docs AWS**

Ngoài capacity hạ tầng, cluster gồm:

| Thành phần | Nội dung |
|---|---|
| **Mạng (VPC và subnet)** nơi task và service chạy | Với **Managed Instances** hoặc **EC2**, subnet có thể nằm ở **Availability Zone**, **Local Zone**, **Wavelength Zone** hoặc **AWS Outposts** |
| **Namespace** (tuỳ chọn) | Dùng cho giao tiếp service-to-service qua **Service Connect** |
| **Tuỳ chọn giám sát** | **CloudWatch Container Insights** — **tính phí thêm**, dịch vụ được quản lý hoàn toàn, tự **thu thập, tổng hợp, tóm tắt** metric và log của ECS |

**💡 Giải thích thêm**

Câu về Local Zone / Wavelength / Outposts chỉ nêu cho Managed Instances và EC2 — Fargate không được nhắc. Liên hệ thuộc tính `ecs.outpost-arn` ở file *Define which container instances…*.

---

## 3. Khái niệm về cluster

**📘 Theo docs AWS**

- Tạo cluster để **tách biệt** tài nguyên.
- Cluster **gắn với một Region**.
- **Trạng thái** của cluster:

| Trạng thái | Ý nghĩa |
|---|---|
| **`ACTIVE`** | Sẵn sàng nhận task; nếu áp dụng, có thể đăng ký container instance vào cluster |
| **`PROVISIONING`** | Cluster có capacity provider, tài nguyên cần cho capacity provider **đang được tạo** |
| **`DEPROVISIONING`** | Cluster có capacity provider, tài nguyên cần cho capacity provider **đang được xoá** |
| **`FAILED`** | Cluster có capacity provider, tài nguyên cần cho capacity provider **tạo thất bại** |
| **`INACTIVE`** | Cluster **đã bị xoá**. Cluster `INACTIVE` có thể vẫn **thấy được** trong account một thời gian — hành vi này **có thể thay đổi**, đừng dựa vào việc cluster `INACTIVE` còn tồn tại |

- Một cluster có thể chứa **lẫn** task chạy trên **Managed Instances**, **Fargate**, **EC2**, hoặc **external instance**. Task chạy trên Managed Instances, Fargate hoặc EC2 theo **launch type** hoặc **capacity provider strategy**.
- Một cluster có thể chứa **lẫn** capacity provider Managed Instances, Auto Scaling group và Fargate. Nhưng **một capacity provider strategy** chỉ chứa **một loại** trong số đó.
- Dùng được **instance type khác nhau** cho capacity provider Managed Instances và EC2 / Auto Scaling group. **Một instance chỉ đăng ký vào một cluster** tại một thời điểm.
- Giới hạn truy cập cluster bằng **IAM policy tuỳ chỉnh**. (Xem *Amazon ECS cluster examples* trong *Identity-based policy examples* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/security_iam_id-based-policy-examples.html#IAM_cluster_policies>.)
- Dùng **Service Auto Scaling** để scale task Fargate. (Xem *Automatically scale your Amazon ECS service* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/service-auto-scaling.html>.)
- Cấu hình được **namespace Service Connect mặc định** cho cluster. Sau đó, mọi service **mới** trong cluster có thể được thêm làm **client service** của namespace chỉ bằng cách **bật Service Connect**, không cần cấu hình thêm. (Xem *Use Service Connect to connect Amazon ECS services with short names* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/service-connect.html>.)

**💡 Giải thích thêm**

```
              ACTIVE  ◄── (tạo xong)
                 ▲
PROVISIONING ────┘
     │
     └──► FAILED            (tạo tài nguyên capacity provider lỗi)

DEPROVISIONING ──► INACTIVE (đã xoá; có thể còn thấy một thời gian)
```

Sơ đồ là mình sắp xếp từ ý nghĩa từng trạng thái; docs không vẽ luồng.

Cách tách cluster thường gặp (gợi ý của mình, theo ý "tạo cluster để tách biệt tài nguyên"): theo **môi trường** (dev / staging / prod), theo **team**, hoặc theo **yêu cầu bảo mật** — kết hợp IAM policy giới hạn theo cluster.

---

## 4. Capacity provider

**📘 Theo docs AWS**

- Capacity provider **quản lý việc scale hạ tầng** cho task trong cluster.
- Mỗi cluster có **một hoặc nhiều** capacity provider và một **capacity provider strategy** tuỳ chọn. Có thể gán **strategy mặc định** cho cluster.
- Strategy quyết định task được **phân bổ** qua các capacity provider của cluster thế nào.
- Khi chạy standalone task hoặc tạo service: dùng **strategy mặc định của cluster**, hoặc một strategy **ghi đè** mặc định.
- Strategy mặc định của cluster **chỉ áp dụng** khi bạn **không** chỉ định **launch type** hay **capacity provider strategy** cho task/service. Chỉ định một trong hai thì **không dùng** mặc định.

Ba loại capacity provider:

| Loại | Nội dung |
|---|---|
| **Managed Instances** | AWS quản lý hoàn toàn EC2 instance: cấp phát, vá, scale, vòng đời. Tự tối ưu chọn instance và scale theo workload. Lợi ích: tự cấp phát và scale; vá và cập nhật bảo mật được quản lý; tối ưu chi phí nhờ chọn instance thông minh; giảm vận hành |
| **Fargate** | Serverless, trả theo tài nguyên task dùng. Chỉ cần **gắn** các capacity provider định sẵn (**Fargate** và **Fargate Spot**) vào cluster |
| **Auto Scaling group** | Dùng khi capacity là EC2 instance; ASG quản lý số lượng EC2 instance cho phù hợp tải. Bạn **toàn quyền** kiểm soát hạ tầng |

- Cluster chứa được lẫn task trên Managed Instances, Fargate, EC2 hoặc external instance. Dùng EC2 dưới dạng **launch type** thì ECS **không theo dõi và không scale** capacity của Auto Scaling group.
- Cluster chứa được lẫn ba loại capacity provider, nhưng một strategy chỉ chứa **một loại**.

**💡 Giải thích thêm**

```
Task / service có chỉ định launch type?          → dùng launch type
Task / service có chỉ định capacity provider strategy? → dùng strategy đó
Không chỉ định gì?                                → dùng strategy mặc định của cluster
```

| | Launch type EC2 | Capacity provider ASG |
|---|---|---|
| ECS scale số instance | **Không** | Có (managed scaling — file *Amazon ECS standalone tasks*) |
| Task chờ khi thiếu capacity | Có thể fail | Chờ ở `PROVISIONING` (managed scaling) |

Liên hệ file *Schedule your containers* (base, weight) và *Architect for Amazon ECS Managed Instances* (một strategy chỉ một loại provider).

---

## 5. Chỗ không khớp trên trang

**💡 Giải thích thêm**

Trang có hai câu gần giống nhau nhưng nói khác:

| Vị trí | Câu |
|---|---|
| Mục *Cluster concepts* | Nếu dùng **EC2 capacity providers**, ECS không theo dõi và không scale capacity của ASG |
| Mục *Capacity providers* | Nếu dùng **EC2 as a launch type**, ECS không theo dõi và không scale capacity của ASG |

Câu thứ hai hợp lý hơn: dùng **capacity provider ASG** (có managed scaling) chính là để ECS scale ASG; còn dùng **launch type EC2** thì ECS không quản lý số instance. Câu ở mục *Cluster concepts* có thể là lỗi biên tập.

---

## 6. Liên hệ với các file trước

**💡 Giải thích thêm**

| Nội dung | File liên quan |
|---|---|
| Capacity provider strategy, base, weight | *Schedule your containers* |
| Managed Instances | *Architect for Amazon ECS Managed Instances* và loạt file Managed Instances |
| Fargate | *Fargate task definition differences*, *task networking for Fargate* |
| EC2 | *Architect for EC2 capacity*, *task networking options for EC2* |
| Một instance một cluster; instance được drain/thay | *Patching*, *Infrastructure optimization* |
| Service Connect, `portMappings.name` | Các file *task definition parameters* |
| Gateway gắn với cluster | *Amazon ECS Gateway*, *Create and manage a gateway* |

---

## 7. Tóm tắt

- **Cluster** = nhóm logic task/service, cung cấp capacity hạ tầng; gắn với **một Region**; tạo cluster để **tách biệt** tài nguyên.
- **Ba loại hạ tầng**: **Managed Instances** (khuyến nghị cho đa số workload mới), **Fargate** (serverless), **EC2** (toàn quyền, custom AMI).
- **Thành phần**: VPC/subnet (Managed Instances và EC2 hỗ trợ cả Local Zone, Wavelength, Outposts), namespace Service Connect (tuỳ chọn), Container Insights (tính phí).
- **Trạng thái**: `ACTIVE`, `PROVISIONING`, `DEPROVISIONING`, `FAILED`, `INACTIVE` (đã xoá, đừng dựa vào việc còn thấy).
- Cluster chứa được **lẫn** task và capacity provider của các loại; **một strategy chỉ một loại** provider; **một instance chỉ thuộc một cluster**.
- Giới hạn truy cập bằng IAM policy; scale Fargate bằng Service Auto Scaling; có thể đặt **namespace Service Connect mặc định**.
- **Capacity provider**: Managed Instances, Fargate/Fargate Spot, Auto Scaling group. **Strategy mặc định của cluster** chỉ dùng khi task/service không chỉ định launch type hay strategy. Launch type EC2 thì ECS không scale ASG.

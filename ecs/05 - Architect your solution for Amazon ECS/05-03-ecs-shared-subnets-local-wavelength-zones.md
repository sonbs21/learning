# Amazon ECS applications in shared subnets, Local Zones, and Wavelength Zones

> Nguồn: **Amazon ECS Developer Guide — Amazon ECS applications in shared subnets, Local Zones, and Wavelength Zones**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/cluster-regions-zones.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: **bản dịch** sang tiếng Việt, đi đúng thứ tự và đúng nội dung của trang gốc, không thêm, không bớt ý.
> - Khối **💡 Giải thích thêm**: chỉ giải thích lại những gì **chính trang này** đã nói, cho dễ hiểu hơn. Không đưa thông tin từ trang khác hay ví dụ tự đặt.
> - Tên dịch vụ, tính năng giữ nguyên tiếng Anh.
> - Tài liệu AWS được phát hành theo giấy phép **Creative Commons Attribution-ShareAlike 4.0**; bản dịch này ghi rõ nguồn ở trên.

---

## Mục lục

0. [Phần mở đầu](#0-phần-mở-đầu)
1. [Shared subnets](#1-shared-subnets)
2. [Local Zones](#2-local-zones)
3. [Wavelength Zones](#3-wavelength-zones)

---

## 0. Phần mở đầu

**📘 Theo docs AWS**

Amazon ECS hỗ trợ workload dùng Local Zones, Wavelength Zones và AWS Outposts cho trường hợp yêu cầu độ trễ thấp hoặc xử lý dữ liệu tại chỗ.

- Bạn có thể dùng **Local Zones** như một phần mở rộng của AWS Region để đặt tài nguyên ở nhiều địa điểm gần người dùng cuối hơn.
- Bạn có thể dùng **Wavelength Zones** để xây dựng ứng dụng mang lại độ trễ cực thấp cho thiết bị 5G và người dùng cuối. Wavelength triển khai các dịch vụ compute và storage tiêu chuẩn của AWS tới rìa (edge) mạng 5G của các nhà mạng viễn thông.
- **AWS Outposts** mang các dịch vụ, hạ tầng và mô hình vận hành gốc của AWS tới gần như bất kỳ trung tâm dữ liệu, không gian thuê đặt chỗ (co-location) hoặc cơ sở on-premises nào.

> **Important:** Hiện tại, workload Amazon ECS trên AWS Fargate **không** được hỗ trợ trong Local Zones, Wavelength Zones hay trên AWS Outposts.

Thông tin về sự khác nhau giữa Local Zones, Wavelength Zones và AWS Outposts, xem *How should I think about when to use AWS Wavelength, AWS Local Zones, or AWS Outposts for applications requiring low latency or local data processing* trong AWS Wavelength FAQs (<https://aws.amazon.com/wavelength/faqs/>).

**💡 Giải thích thêm**

| Lựa chọn | Đặt tài nguyên ở đâu (theo đoạn trên) | Mục đích |
|---|---|---|
| Local Zones | Mở rộng của Region, gần người dùng cuối | Độ trễ thấp |
| Wavelength Zones | Rìa mạng 5G của nhà mạng | Độ trễ **cực** thấp cho thiết bị 5G |
| AWS Outposts | Trung tâm dữ liệu / co-location / on-premises của bạn | Dịch vụ AWS ngay tại chỗ |

Điểm cần nhớ từ khung Important: **Fargate không chạy được** ở cả ba nơi này.

Dù tiêu đề trang nhắc Outposts ở phần mở đầu, nội dung chi tiết của trang chỉ có ba mục: Shared subnets, Local Zones, Wavelength Zones.

---

## 1. Shared subnets

**📘 Theo docs AWS**

Bạn có thể dùng *VPC sharing* để chia sẻ subnet với các AWS account khác trong cùng AWS Organizations.

Bạn có thể dùng shared VPC cho **EC2** với các lưu ý sau:

- Chủ sở hữu (owner) của VPC subnet phải chia sẻ subnet với account tham gia (participant) trước khi account đó có thể dùng subnet cho tài nguyên Amazon ECS.
- Bạn không thể dùng security group mặc định của VPC cho container instance, vì nó thuộc về owner. Ngoài ra, participant không thể launch instance bằng security group thuộc về participant khác hoặc owner.
- Trong shared subnet, participant và owner kiểm soát riêng security group trong account của mỗi bên. Owner của subnet nhìn thấy security group do participant tạo nhưng không thể thực hiện thao tác nào trên chúng. Nếu owner muốn xoá hoặc sửa các security group này, participant đã tạo security group đó phải thực hiện.
- Owner của shared VPC không thể xem, cập nhật hoặc xoá cluster mà participant tạo trong shared subnet. Điều này nằm ngoài các tài nguyên VPC mà mỗi account có quyền truy cập khác nhau. Thông tin thêm, xem *Responsibilities and permissions for owners and participants* (<https://docs.aws.amazon.com/vpc/latest/userguide/vpc-sharing.html#vpc-share-limitations>) trong *Amazon VPC User Guide*.

Bạn có thể dùng shared VPC cho **Fargate** với các lưu ý sau:

- Owner của VPC subnet phải chia sẻ subnet với account participant trước khi account đó có thể dùng subnet cho tài nguyên Amazon ECS.
- Bạn không thể tạo service hoặc chạy task bằng security group mặc định của VPC, vì nó thuộc về owner. Ngoài ra, participant không thể tạo service hoặc chạy task bằng security group thuộc về participant khác hoặc owner.
- Trong shared subnet, participant và owner kiểm soát riêng security group trong account của mỗi bên. Owner của subnet nhìn thấy security group do participant tạo nhưng không thể thực hiện thao tác nào trên chúng. Nếu owner muốn xoá hoặc sửa các security group này, participant đã tạo security group đó phải thực hiện.
- Owner của shared VPC không thể xem, cập nhật hoặc xoá cluster mà participant tạo trong shared subnet. Điều này nằm ngoài các tài nguyên VPC mà mỗi account có quyền truy cập khác nhau. Thông tin thêm, xem *Responsibilities and permissions for owners and participants* (<https://docs.aws.amazon.com/vpc/latest/userguide/vpc-sharing.html#vpc-share-limitations>) trong *Amazon VPC User Guide*.

Thông tin thêm về chia sẻ VPC subnet, xem *Share your VPC with other accounts* (<https://docs.aws.amazon.com/vpc/latest/userguide/vpc-sharing.html#vpc-share-limitations>) trong *Amazon VPC User Guide*.

**💡 Giải thích thêm**

Hai danh sách EC2 và Fargate gần như giống nhau; chỉ khác ở **điểm thứ hai** — security group được dùng ở chỗ nào:

| | EC2 | Fargate |
|---|---|---|
| Owner phải share subnet trước | Có | Có |
| Không dùng security group mặc định của VPC (thuộc owner) | Cho **container instance** | Khi **tạo service / chạy task** |
| Không dùng security group của participant khác hoặc owner | Khi **launch instance** | Khi **tạo service / chạy task** |
| Mỗi bên tự quản security group của mình; owner chỉ **xem**, không sửa/xoá | Có | Có |
| Owner **không** xem / sửa / xoá cluster của participant | Có | Có |

Quan hệ owner – participant:

```
Owner account                       Participant account
─────────────                       ───────────────────
VPC + subnet  ── share subnet ──►   dùng subnet cho tài nguyên ECS
SG mặc định của VPC (của owner)  ✗  participant không dùng được
                                    SG do participant tự tạo  ✓
nhìn thấy SG của participant  ◄──   (owner không sửa/xoá được)
không thấy cluster của participant  cluster của participant
```

---

## 2. Local Zones

**📘 Theo docs AWS**

*Local Zone* là phần mở rộng của một AWS Region, ở gần người dùng của bạn về mặt địa lý. Local Zones có kết nối internet riêng và hỗ trợ Direct Connect. Tài nguyên tạo trong Local Zone có thể phục vụ người dùng tại chỗ với giao tiếp độ trễ thấp. Thông tin thêm, xem *AWS Local Zones* (<https://aws.amazon.com/about-aws/global-infrastructure/localzones/>).

Một Local Zone được biểu diễn bằng mã Region theo sau là một định danh chỉ địa điểm (ví dụ `us-west-2-lax-1a`).

Để dùng Local Zone, bạn phải **opt in** (đăng ký sử dụng) zone đó. Sau khi opt in, bạn phải tạo Amazon VPC và subnet trong Local Zone.

Bạn có thể launch Amazon EC2 instance, Amazon FSx file server và Application Load Balancer để dùng cho Amazon ECS cluster và task.

Thông tin thêm, xem *What is AWS Local Zones?* (<https://docs.aws.amazon.com/local-zones/latest/ug/what-is-aws-local-zones.html>) trong *AWS Local Zones User Guide*.

**💡 Giải thích thêm**

| Điểm | Theo đoạn trên |
|---|---|
| Là gì | Phần mở rộng của Region, gần người dùng |
| Kết nối | Có internet riêng, hỗ trợ Direct Connect |
| Tên | Mã Region + định danh địa điểm, vd `us-west-2-lax-1a` |
| Các bước | Opt in zone → tạo VPC và subnet trong Local Zone |
| Dùng được cho ECS | EC2 instance, FSx file server, Application Load Balancer |

Trong ví dụ `us-west-2-lax-1a`: `us-west-2` là mã Region, phần `lax-1a` là định danh địa điểm.

---

## 3. Wavelength Zones

**📘 Theo docs AWS**

Bạn có thể dùng *AWS Wavelength* để xây dựng ứng dụng mang lại độ trễ cực thấp cho thiết bị di động và người dùng cuối. Wavelength triển khai các dịch vụ compute và storage tiêu chuẩn của AWS tới rìa mạng 5G của các nhà mạng viễn thông. Bạn có thể mở rộng một Amazon Virtual Private Cloud ra một hoặc nhiều Wavelength Zone. Sau đó, bạn có thể dùng tài nguyên AWS như Amazon EC2 instance để chạy ứng dụng cần độ trễ cực thấp và kết nối tới các dịch vụ AWS trong Region.

Wavelength Zone là một Zone biệt lập tại địa điểm của nhà mạng, nơi hạ tầng Wavelength được triển khai. Wavelength Zone gắn với một AWS Region. Wavelength Zone là phần mở rộng logic của một Region, và được quản lý bởi control plane trong Region.

Một Wavelength Zone được biểu diễn bằng mã Region theo sau là một định danh chỉ Wavelength Zone (ví dụ `us-east-1-wl1-bos-wlz-1`).

Để dùng Wavelength Zone, bạn phải **opt in** zone đó. Sau khi opt in, bạn phải tạo Amazon VPC và subnet trong Wavelength Zone. Sau đó, bạn có thể launch Amazon EC2 instance trong Zone để dùng cho Amazon ECS cluster và task.

Thông tin thêm, xem *Get started with AWS Wavelength* (<https://docs.aws.amazon.com/wavelength/latest/developerguide/get-started-wavelength.html>) trong *AWS Wavelength Developer Guide*.

Wavelength Zones không có ở mọi AWS Region. Thông tin về các Region hỗ trợ Wavelength Zones, xem *Available Wavelength Zones* (<https://docs.aws.amazon.com/wavelength/latest/developerguide/available-wavelength-zones.html>) trong *AWS Wavelength Developer Guide*.

**💡 Giải thích thêm**

| Điểm | Theo đoạn trên |
|---|---|
| Là gì | Zone biệt lập tại địa điểm nhà mạng, rìa mạng 5G |
| Quan hệ với Region | Gắn với một Region; là phần mở rộng **logic**, do control plane của Region quản lý |
| Tên | Mã Region + định danh Wavelength Zone, vd `us-east-1-wl1-bos-wlz-1` |
| Các bước | Opt in zone → tạo VPC và subnet trong Wavelength Zone → launch EC2 instance cho ECS |
| Dùng được cho ECS | EC2 instance |
| Phạm vi | Không có ở mọi Region |

So sánh nhanh hai loại zone theo chính trang này:

| | Local Zones | Wavelength Zones |
|---|---|---|
| Đặt ở đâu | Gần người dùng về địa lý | Tại nhà mạng, rìa mạng 5G |
| Opt in + tạo VPC/subnet trong zone | Có | Có |
| Tài nguyên trang nêu dùng cho ECS | EC2, FSx, ALB | EC2 |
| Fargate | Không (theo khung Important) | Không (theo khung Important) |

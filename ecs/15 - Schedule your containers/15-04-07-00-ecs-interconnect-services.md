# Interconnect Amazon ECS services

> Nguồn: **Amazon ECS Developer Guide — Interconnect Amazon ECS services**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/interconnecting-services.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: **bản dịch** sang tiếng Việt, đi đúng thứ tự và đúng nội dung của trang gốc, không thêm, không bớt ý.
> - Khối **💡 Giải thích thêm**: chỉ giải thích lại những gì **chính trang này** đã nói, cho dễ hiểu hơn. Không đưa thông tin từ trang khác hay ví dụ tự đặt.
> - Tên dịch vụ, network mode, loại bản ghi DNS, tham số giữ nguyên tiếng Anh.
> - Tài liệu AWS được phát hành theo giấy phép **Creative Commons Attribution-ShareAlike 4.0**; bản dịch này ghi rõ nguồn ở trên.

---

## Mục lục

0. [Phần mở đầu](#0-phần-mở-đầu)
1. [Amazon ECS Service Connect](#1-amazon-ecs-service-connect)
2. [Amazon ECS service discovery](#2-amazon-ecs-service-discovery)
3. [Amazon VPC Lattice](#3-amazon-vpc-lattice)
4. [Bảng tương thích network mode](#4-bảng-tương-thích-network-mode)

---

## 0. Phần mở đầu

**📘 Theo docs AWS**

Ứng dụng chạy trong task Amazon ECS thường cần nhận kết nối từ internet, hoặc kết nối tới các ứng dụng khác chạy trong Amazon ECS service. Nếu bạn cần kết nối từ bên ngoài internet, chúng tôi khuyến nghị dùng **Elastic Load Balancing**. Thông tin thêm về cân bằng tải tích hợp, xem *Use load balancing to distribute Amazon ECS service traffic* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/service-load-balancing.html>).

Nếu bạn cần một ứng dụng kết nối tới các ứng dụng khác chạy trong Amazon ECS service, Amazon ECS cung cấp các cách sau để làm việc này **không cần load balancer**:

**💡 Giải thích thêm**

| Nhu cầu | Khuyến nghị theo trang |
|---|---|
| Nhận kết nối từ **internet** | Elastic Load Balancing |
| Kết nối **giữa các ECS service** | Service Connect, service discovery, hoặc VPC Lattice — không cần load balancer |

---

## 1. Amazon ECS Service Connect

**📘 Theo docs AWS**

Chúng tôi **khuyến nghị Service Connect**, cung cấp cấu hình Amazon ECS cho service discovery, kết nối và giám sát traffic. Với Service Connect, ứng dụng có thể dùng **tên ngắn** và **port chuẩn** để kết nối tới Amazon ECS service trong cùng cluster, trong các cluster khác, kể cả giữa các VPC trong cùng AWS Region.

Khi dùng Service Connect, Amazon ECS quản lý mọi phần của service discovery: tạo các tên có thể được tìm thấy, quản lý động các mục cho từng task khi task khởi động và dừng, chạy một **agent trong mỗi task** được cấu hình để tìm các tên đó. Ứng dụng có thể tra các tên bằng chức năng chuẩn cho tên DNS và tạo kết nối. Nếu ứng dụng đã làm vậy, bạn **không cần sửa** ứng dụng để dùng Service Connect.

Bạn cung cấp cấu hình đầy đủ trong mỗi service và task definition. Amazon ECS quản lý thay đổi cấu hình này trong mỗi lần deploy service, để đảm bảo mọi task trong một deployment hoạt động giống nhau. Ví dụ, một vấn đề thường gặp khi dùng DNS làm service discovery là kiểm soát quá trình chuyển đổi. Nếu bạn đổi một tên DNS để trỏ tới các địa chỉ IP thay thế mới, có thể mất tới thời gian **TTL tối đa** trước khi mọi client bắt đầu dùng service mới. Với Service Connect, deployment của client cập nhật cấu hình bằng cách **thay các task client**. Bạn có thể cấu hình deployment circuit breaker và các cấu hình deployment khác để áp dụng thay đổi Service Connect theo cùng cách như mọi deployment khác.

Thông tin thêm, xem *Use Service Connect to connect Amazon ECS services with short names* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/service-connect.html>).

**💡 Giải thích thêm**

| ECS lo | Ứng dụng lo |
|---|---|
| Tạo tên tìm thấy được; cập nhật mục khi task bật/tắt; chạy agent trong mỗi task | Tra tên DNS như bình thường, mở kết nối |

| | DNS thuần làm service discovery | Service Connect |
|---|---|---|
| Đổi đích | Chờ tới TTL tối đa | Deployment thay task client |
| Kiểm soát | Cache DNS của client | Circuit breaker, cấu hình deployment |

---

## 2. Amazon ECS service discovery

**📘 Theo docs AWS**

Một cách khác cho giao tiếp service-to-service là giao tiếp **trực tiếp** dùng service discovery. Với cách này, bạn có thể dùng tích hợp service discovery của **AWS Cloud Map** với Amazon ECS. Khi dùng service discovery, Amazon ECS đồng bộ danh sách task đã launch sang AWS Cloud Map, nơi duy trì một DNS hostname phân giải ra địa chỉ IP nội bộ của một hoặc nhiều task thuộc service đó. Các service khác trong Amazon VPC có thể dùng DNS hostname này để gửi traffic trực tiếp tới container khác bằng địa chỉ IP nội bộ của nó.

Cách giao tiếp service-to-service này cho **độ trễ thấp**. Không có thành phần nào thêm giữa các container. Traffic đi trực tiếp từ container này sang container kia.

Cách này phù hợp khi dùng network mode **`awsvpc`**, nơi mỗi task có địa chỉ IP riêng duy nhất. Phần lớn phần mềm chỉ hỗ trợ dùng bản ghi DNS **`A`**, phân giải trực tiếp ra địa chỉ IP. Khi dùng network mode `awsvpc`, địa chỉ IP của mỗi task là một bản ghi `A`. Tuy nhiên, nếu dùng network mode **`bridge`**, nhiều container có thể dùng chung một địa chỉ IP. Ngoài ra, dynamic port mapping khiến container được gán port ngẫu nhiên trên địa chỉ IP đó. Lúc này, bản ghi `A` không còn đủ cho service discovery. Bạn phải dùng thêm bản ghi **`SRV`**. Loại bản ghi này theo dõi được cả địa chỉ IP lẫn số port, nhưng yêu cầu bạn cấu hình ứng dụng phù hợp. Một số ứng dụng dựng sẵn mà bạn dùng có thể không hỗ trợ bản ghi `SRV`.

Một ưu điểm khác của network mode `awsvpc` là bạn có **security group riêng cho mỗi service**. Bạn có thể cấu hình security group này chỉ cho phép kết nối đến từ đúng các service upstream cần nói chuyện với service đó.

Nhược điểm chính của giao tiếp service-to-service trực tiếp dùng service discovery là bạn phải tự viết thêm logic để **retry** và **xử lý lỗi kết nối**. Bản ghi DNS có thời gian sống (time-to-live — TTL) kiểm soát chúng được cache bao lâu. Cần một thời gian để bản ghi DNS được cập nhật và cache hết hạn, để ứng dụng lấy được phiên bản mới nhất của bản ghi DNS. Vì vậy, ứng dụng có thể phân giải bản ghi DNS ra một container **không còn tồn tại**. Ứng dụng cần xử lý retry và có logic bỏ qua các backend hỏng.

Thông tin thêm, xem *Use service discovery to connect Amazon ECS services with DNS names* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/service-discovery.html>).

**💡 Giải thích thêm**

```
ECS ─ đồng bộ task ─► AWS Cloud Map ─ DNS hostname ─► IP nội bộ của task
service khác trong VPC ─ tra DNS ─► gửi traffic thẳng tới container
```

| Network mode | Bản ghi DNS cần |
|---|---|
| `awsvpc` | `A` (mỗi task một IP) |
| `bridge` (dynamic port) | Thêm `SRV` (IP + port); ứng dụng phải hỗ trợ |

| Ưu | Nhược |
|---|---|
| Độ trễ thấp, không thành phần trung gian | Tự viết retry, xử lý lỗi kết nối |
| `awsvpc`: security group riêng mỗi service | TTL của DNS → có thể trỏ tới container đã mất |

---

## 3. Amazon VPC Lattice

**📘 Theo docs AWS**

**Amazon VPC Lattice** là dịch vụ mạng ứng dụng được quản lý, mà khách hàng Amazon ECS dùng để quan sát, bảo mật và giám sát ứng dụng xây dựng trên các dịch vụ compute của AWS, VPC và account **mà không phải sửa code**.

VPC Lattice dùng **target group**, là tập hợp các tài nguyên compute. Các target này chạy ứng dụng hoặc service của bạn và có thể là Amazon EC2 instance, địa chỉ IP, Lambda function, và Application Load Balancer. Bằng cách gắn Amazon ECS service với một target group VPC Lattice, khách hàng có thể bật task Amazon ECS làm **IP target** trong VPC Lattice. Amazon ECS **tự động đăng ký** task vào target group VPC Lattice khi task của service đã đăng ký được khởi chạy.

Thông tin thêm, xem *Use Amazon VPC Lattice to connect, observe, and secure your Amazon ECS services* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/ecs-vpc-lattice.html>).

**💡 Giải thích thêm**

```
ECS service ─ gắn với ─► VPC Lattice target group
   task mới khởi chạy ─ ECS tự đăng ký ─► làm IP target
```

| Điểm | Theo đoạn trên |
|---|---|
| Phạm vi | Nhiều dịch vụ compute, VPC, account |
| Sửa code | Không cần |
| Loại target của target group | EC2 instance, địa chỉ IP, Lambda function, Application Load Balancer |
| Task ECS là | IP target, được ECS tự đăng ký |

---

## 4. Bảng tương thích network mode

**📘 Theo docs AWS**

Bảng sau trình bày mức tương thích giữa các lựa chọn này và network mode của task. Trong bảng, "client" là ứng dụng tạo kết nối từ bên trong một task Amazon ECS.

| Lựa chọn kết nối | Bridged | `awsvpc` | Host |
|---|---|---|---|
| **Service discovery** | Có, nhưng yêu cầu client hiểu bản ghi **SRV** trong DNS khi không có `hostPort`. | Có | Có, nhưng yêu cầu client hiểu bản ghi **SRV** trong DNS khi không có `hostPort`. |
| **Service Connect** | Có | Có | **Không** |
| **VPC Lattice** | Có | Có | Có |

**💡 Giải thích thêm**

| | Bridged | `awsvpc` | Host |
|---|---|---|---|
| Service discovery | ⚠️ cần client hỗ trợ SRV | ✅ | ⚠️ cần client hỗ trợ SRV |
| Service Connect | ✅ | ✅ | ❌ |
| VPC Lattice | ✅ | ✅ | ✅ |

Chỉ có `awsvpc` là dùng được cả ba lựa chọn mà không kèm điều kiện. Điều kiện SRV của service discovery khớp với giải thích ở mục 2: khi port không cố định, bản ghi `A` không đủ.

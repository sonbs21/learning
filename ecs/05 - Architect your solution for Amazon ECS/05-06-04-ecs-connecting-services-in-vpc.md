# Best practices for connecting Amazon ECS services in a VPC

> Nguồn: **Amazon ECS Developer Guide — Best practices for connecting Amazon ECS services in a VPC**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/networking-connecting-services.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: **bản dịch** sang tiếng Việt, đi đúng thứ tự và đúng nội dung của trang gốc, không thêm, không bớt ý.
> - Khối **💡 Giải thích thêm**: chỉ giải thích lại những gì **chính trang này** đã nói, cho dễ hiểu hơn. Không đưa thông tin từ trang khác hay ví dụ tự đặt.
> - Tên dịch vụ, tính năng, network mode, loại bản ghi DNS giữ nguyên tiếng Anh.
> - Tài liệu AWS được phát hành theo giấy phép **Creative Commons Attribution-ShareAlike 4.0**; bản dịch này ghi rõ nguồn ở trên.

---

## Mục lục

0. [Phần mở đầu](#0-phần-mở-đầu)
1. [Dùng Service Connect](#1-dùng-service-connect)
2. [Dùng service discovery](#2-dùng-service-discovery)
3. [Dùng internal load balancer](#3-dùng-internal-load-balancer)
4. [So sánh ba cách](#4-so-sánh-ba-cách)

---

## 0. Phần mở đầu

**📘 Theo docs AWS**

Khi dùng task Amazon ECS trong VPC, bạn có thể tách ứng dụng nguyên khối (monolithic) thành các phần riêng biệt, có thể deploy và scale độc lập trong một môi trường an toàn. Kiến trúc này được gọi là kiến trúc hướng dịch vụ (service-oriented architecture — SOA) hoặc microservices. Tuy nhiên, việc đảm bảo mọi phần này, cả trong lẫn ngoài VPC, giao tiếp được với nhau có thể là một thách thức. Có nhiều cách để hỗ trợ việc giao tiếp, mỗi cách có ưu và nhược điểm khác nhau.

---

## 1. Dùng Service Connect

**📘 Theo docs AWS**

Chúng tôi **khuyến nghị Service Connect**, cung cấp cấu hình Amazon ECS cho service discovery, kết nối và giám sát traffic. Với Service Connect, ứng dụng có thể dùng **tên ngắn** và **port chuẩn** để kết nối tới service trong cùng cluster, trong các cluster khác, kể cả giữa các VPC trong cùng Region. Thông tin thêm, xem *Amazon ECS Service Connect* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/service-connect.html>).

Khi dùng Service Connect, Amazon ECS quản lý mọi phần của service discovery: tạo các tên có thể được tìm thấy, quản lý động các mục cho từng task khi task khởi động và dừng, chạy một **agent trong mỗi task** được cấu hình để tìm các tên đó. Ứng dụng có thể tra các tên bằng chức năng chuẩn cho tên DNS và tạo kết nối. Nếu ứng dụng đã làm vậy, bạn **không cần sửa** ứng dụng để dùng Service Connect.

*(Hình trên trang gốc: sơ đồ kiến trúc mạng dùng Service Connect.)*

### Thay đổi chỉ xảy ra trong lúc deploy

Bạn cung cấp cấu hình đầy đủ trong mỗi service và task definition. Amazon ECS quản lý thay đổi cấu hình này trong mỗi lần deploy service, để đảm bảo mọi task trong một deployment hoạt động giống nhau. Ví dụ, một vấn đề thường gặp khi dùng DNS làm service discovery là kiểm soát quá trình chuyển đổi (migration). Nếu bạn đổi một tên DNS để trỏ tới các địa chỉ IP thay thế mới, có thể mất tới thời gian **TTL tối đa** trước khi mọi client bắt đầu dùng service mới. Với Service Connect, deployment của client cập nhật cấu hình bằng cách **thay các task client**. Bạn có thể cấu hình deployment circuit breaker và các cấu hình deployment khác để áp dụng thay đổi Service Connect theo cùng cách như mọi deployment khác.

**💡 Giải thích thêm**

ECS lo những gì khi dùng Service Connect, theo đoạn trên:

| Phần của service discovery | Ai làm |
|---|---|
| Tạo tên có thể tìm thấy | ECS |
| Cập nhật mục cho từng task khi task bật/tắt | ECS |
| Agent trong mỗi task để tìm tên | ECS chạy |
| Tra tên, mở kết nối | Ứng dụng — dùng DNS như bình thường |

Khác biệt với DNS thuần khi chuyển đổi:

| | DNS làm service discovery | Service Connect |
|---|---|---|
| Đổi đích | Đổi bản ghi DNS → chờ tới TTL tối đa để client dùng bản mới | Deployment thay task client → client mới có cấu hình mới |
| Kiểm soát | Phụ thuộc cache DNS của client | Theo deployment (circuit breaker, cấu hình deployment) |

---

## 2. Dùng service discovery

**📘 Theo docs AWS**

Một cách khác cho giao tiếp service-to-service là giao tiếp **trực tiếp** dùng service discovery. Với cách này, bạn có thể dùng tích hợp service discovery của **AWS Cloud Map** với Amazon ECS. Khi dùng service discovery, Amazon ECS đồng bộ danh sách task đã launch sang AWS Cloud Map, nơi duy trì một DNS hostname phân giải ra địa chỉ IP nội bộ của một hoặc nhiều task thuộc service đó. Các service khác trong Amazon VPC có thể dùng DNS hostname này để gửi traffic trực tiếp tới container khác bằng địa chỉ IP nội bộ của nó. Thông tin thêm, xem *Service discovery* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/service-discovery.html>).

*(Hình trên trang gốc: sơ đồ kiến trúc mạng dùng service discovery.)*

Trong sơ đồ trên có ba service. `service-a-local` có một container và giao tiếp với `service-b-local`, service này có hai container. `service-b-local` cũng phải giao tiếp với `service-c-local`, service này có một container. Mỗi container trong cả ba service đều có thể dùng tên DNS nội bộ từ AWS Cloud Map để tìm địa chỉ IP nội bộ của một container thuộc service downstream mà nó cần giao tiếp.

Cách giao tiếp service-to-service này cho **độ trễ thấp**. Thoạt nhìn, nó cũng đơn giản vì **không có thành phần nào thêm** giữa các container. Traffic đi trực tiếp từ container này sang container kia.

Cách này phù hợp khi dùng network mode **`awsvpc`**, nơi mỗi task có địa chỉ IP riêng duy nhất. Phần lớn phần mềm chỉ hỗ trợ dùng bản ghi DNS **`A`**, phân giải trực tiếp ra địa chỉ IP. Khi dùng network mode `awsvpc`, địa chỉ IP của mỗi task là một bản ghi `A`. Tuy nhiên, nếu dùng network mode **`bridge`**, nhiều container có thể dùng chung một địa chỉ IP. Ngoài ra, dynamic port mapping khiến container được gán port ngẫu nhiên trên địa chỉ IP đó. Lúc này, bản ghi `A` không còn đủ cho service discovery. Bạn phải dùng thêm bản ghi **`SRV`**. Loại bản ghi này theo dõi được cả địa chỉ IP lẫn số port, nhưng yêu cầu bạn cấu hình ứng dụng phù hợp. Một số ứng dụng dựng sẵn mà bạn dùng có thể không hỗ trợ bản ghi `SRV`.

Một ưu điểm khác của network mode `awsvpc` là bạn có **security group riêng cho mỗi service**. Bạn có thể cấu hình security group này chỉ cho phép kết nối đến từ đúng các service upstream cần nói chuyện với service đó.

Nhược điểm chính của giao tiếp service-to-service trực tiếp dùng service discovery là bạn phải tự viết thêm logic để **retry** và **xử lý lỗi kết nối**. Bản ghi DNS có thời gian sống (time-to-live — TTL) kiểm soát chúng được cache bao lâu. Cần một thời gian để bản ghi DNS được cập nhật và cache hết hạn, để ứng dụng lấy được phiên bản mới nhất của bản ghi DNS. Vì vậy, ứng dụng có thể phân giải bản ghi DNS ra một container **không còn tồn tại**. Ứng dụng cần xử lý retry và có logic bỏ qua các backend hỏng.

**💡 Giải thích thêm**

Ví dụ trong sơ đồ của trang:

```
service-a-local (1 container) ──► service-b-local (2 container) ──► service-c-local (1 container)
        mỗi container tra tên DNS nội bộ trong AWS Cloud Map → IP nội bộ của container downstream
```

Bản ghi DNS cần dùng theo network mode:

| Network mode | IP | Bản ghi đủ dùng |
|---|---|---|
| `awsvpc` | Mỗi task một IP riêng | `A` (phần lớn phần mềm hỗ trợ) |
| `bridge` | Nhiều container chung IP, port ngẫu nhiên (dynamic port mapping) | Cần thêm `SRV` (IP + port) — ứng dụng phải hỗ trợ |

| Ưu | Nhược |
|---|---|
| Độ trễ thấp, không thành phần trung gian | Phải tự retry, xử lý lỗi kết nối |
| `awsvpc`: security group riêng mỗi service, chỉ mở cho upstream cần thiết | TTL của DNS → có thể trỏ tới container đã mất |

---

## 3. Dùng internal load balancer

**📘 Theo docs AWS**

Một cách khác cho giao tiếp service-to-service là dùng **internal load balancer**. Internal load balancer nằm hoàn toàn bên trong VPC và chỉ các service trong VPC mới truy cập được.

*(Hình trên trang gốc: sơ đồ kiến trúc mạng dùng internal load balancer.)*

Load balancer duy trì tính sẵn sàng cao bằng cách triển khai tài nguyên dự phòng vào mỗi subnet. Khi một container của `serviceA` cần giao tiếp với một container của `serviceB`, nó mở kết nối tới load balancer. Sau đó load balancer mở kết nối tới một container của `service B`. Load balancer đóng vai trò nơi tập trung để quản lý mọi kết nối giữa các service.

Nếu một container của `serviceB` dừng, load balancer có thể gỡ container đó khỏi pool. Load balancer cũng thực hiện **health check** với từng target downstream trong pool và có thể tự động gỡ target hỏng khỏi pool cho tới khi chúng khoẻ lại. Ứng dụng không còn cần biết có bao nhiêu container downstream. Chúng chỉ cần mở kết nối tới load balancer.

Cách này có lợi với **mọi network mode**. Load balancer theo dõi được địa chỉ IP của task khi dùng network mode `awsvpc`, cũng như các tổ hợp IP và port phức tạp hơn khi dùng network mode `bridge`. Nó phân phối traffic đều trên mọi tổ hợp IP và port, kể cả khi nhiều container thực ra chạy trên cùng một Amazon EC2 instance, chỉ khác port.

Nhược điểm duy nhất của cách này là **chi phí**. Để sẵn sàng cao, load balancer cần có tài nguyên ở mỗi Availability Zone. Điều này làm tăng chi phí do phải trả cho load balancer và cho lượng traffic đi qua load balancer.

Tuy nhiên, bạn có thể giảm chi phí bằng cách cho **nhiều service dùng chung một load balancer**. Điều này đặc biệt phù hợp với service REST dùng Application Load Balancer. Bạn có thể tạo quy tắc định tuyến theo path để định tuyến traffic tới các service khác nhau. Ví dụ, `/api/user/*` có thể định tuyến tới container thuộc service `user`, còn `/api/order/*` có thể định tuyến tới service `order` tương ứng. Với cách này, bạn chỉ trả tiền cho một Application Load Balancer, và có một URL thống nhất cho API. Tuy nhiên, bạn vẫn tách được traffic ra các microservice khác nhau ở backend.

**💡 Giải thích thêm**

```
container serviceA ──► internal load balancer ──► container serviceB (một trong pool)
                         │  health check từng target
                         └─ gỡ target dừng/hỏng khỏi pool
```

Dùng chung một ALB bằng định tuyến theo path (ví dụ của trang):

```
/api/user/*   ──► service user
/api/order/*  ──► service order
        (một ALB, một URL API)
```

| Ưu | Nhược |
|---|---|
| Nơi tập trung quản lý kết nối; ứng dụng không cần biết số container downstream | Chi phí: tài nguyên ở mỗi AZ + phí theo traffic |
| Health check, tự gỡ target hỏng | Giảm được bằng cách dùng chung một ALB cho nhiều service |
| Dùng được với mọi network mode (`awsvpc`: IP; `bridge`: IP + port) | |

---

## 4. So sánh ba cách

**💡 Giải thích thêm**

Tổng hợp từ ba mục trên của chính trang:

| | Service Connect | Service discovery (Cloud Map) | Internal load balancer |
|---|---|---|---|
| Trang đánh giá | **Khuyến nghị** | Lựa chọn khác | Lựa chọn khác |
| Thành phần giữa các container | Agent trong mỗi task (ECS quản lý) | Không có | Load balancer |
| Phạm vi | Cùng cluster, cluster khác, VPC khác trong cùng Region | Service trong VPC | Service trong VPC |
| Thay đổi đích | Qua deployment (thay task client) | Chờ TTL của DNS | Load balancer cập nhật pool |
| Retry / bỏ qua backend hỏng | (trang không nêu chi tiết) | Ứng dụng tự làm | Load balancer health check |
| Network mode | (trang không nêu) | Hợp `awsvpc` (bản ghi `A`); `bridge` cần `SRV` | Mọi network mode |
| Độ trễ | (trang không nêu) | Thấp | (trang không nêu) |
| Chi phí theo trang | (trang không nêu) | (trang không nêu) | Có — giảm bằng dùng chung ALB |
| Sửa ứng dụng | Không, nếu đã dùng DNS chuẩn | Thêm logic retry, xử lý lỗi | Chỉ mở kết nối tới load balancer |

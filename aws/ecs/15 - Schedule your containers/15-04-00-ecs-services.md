# Amazon ECS services

> Nguồn: **Amazon ECS Developer Guide — Amazon ECS services**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/ecs_services.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: **bản dịch** sang tiếng Việt, đi đúng thứ tự và đúng nội dung của trang gốc, không thêm, không bớt ý.
> - Khối **💡 Giải thích thêm**: chỉ giải thích lại những gì **chính trang này** đã nói, cho dễ hiểu hơn. Không đưa thông tin từ trang khác hay ví dụ tự đặt.
> - Tên tham số, trạng thái, tuỳ chọn console, loại load balancer giữ nguyên tiếng Anh.
> - Tài liệu AWS được phát hành theo giấy phép **Creative Commons Attribution-ShareAlike 4.0**; bản dịch này ghi rõ nguồn ở trên.

---

## Mục lục

0. [Phần mở đầu](#0-phần-mở-đầu)
1. [Lựa chọn compute cho hạ tầng](#1-lựa-chọn-compute-cho-hạ-tầng)
2. [Service auto scaling](#2-service-auto-scaling)
3. [Cân bằng tải cho service](#3-cân-bằng-tải-cho-service)
4. [Kết nối các service với nhau](#4-kết-nối-các-service-với-nhau)

---

## 0. Phần mở đầu

**📘 Theo docs AWS**

Bạn có thể dùng Amazon ECS service để chạy và duy trì **một số lượng chỉ định** các bản thể (instance) của một task definition **đồng thời** trong Amazon ECS cluster. Nếu một task lỗi hoặc dừng, **Amazon ECS service scheduler** khởi chạy một bản thể khác của task definition để thay thế. Điều này giúp duy trì số task mong muốn trong service.

Bạn cũng có thể tuỳ chọn chạy service **phía sau load balancer**. Load balancer phân phối traffic tới các task gắn với service.

Chúng tôi khuyến nghị dùng service scheduler cho các service và ứng dụng **stateless chạy lâu dài**. Service scheduler đảm bảo scheduling strategy bạn chỉ định được tuân theo, và lập lịch lại task khi task lỗi. Ví dụ, nếu hạ tầng bên dưới lỗi, service scheduler lập lịch lại task. Bạn có thể dùng task placement strategy và constraint để tuỳ chỉnh cách scheduler đặt và kết thúc task. Nếu một task trong service dừng, scheduler khởi chạy task mới thay thế. Quá trình này tiếp tục cho tới khi service đạt số task mong muốn dựa trên scheduling strategy mà service dùng.

Service scheduler cũng **thay thế task được xác định là unhealthy** sau khi **container health check** hoặc **health check của target group load balancer** thất bại. Việc thay thế này phụ thuộc vào các tham số service definition **`maximumPercent`** và **`desiredCount`**. Nếu một task bị đánh dấu unhealthy, service scheduler sẽ **khởi động task thay thế trước**. Sau đó, điều sau xảy ra.

- Nếu task thay thế có trạng thái sức khoẻ **`HEALTHY`**, service scheduler **dừng task unhealthy**.
- Nếu task thay thế có trạng thái sức khoẻ **`UNHEALTHY`**, scheduler sẽ dừng **hoặc** task thay thế unhealthy **hoặc** task unhealthy hiện có, để tổng số task bằng `desiredCount`.

Nếu tham số `maximumPercent` khiến scheduler **không thể khởi động task thay thế trước**, scheduler sẽ dừng **từng task unhealthy một, ngẫu nhiên**, để giải phóng capacity, rồi mới khởi động task thay thế. Quá trình khởi động và dừng tiếp tục cho tới khi mọi task unhealthy được thay bằng task healthy. Khi mọi task unhealthy đã được thay và chỉ còn task healthy đang chạy, nếu tổng số task **vượt** `desiredCount`, các task healthy sẽ bị dừng **ngẫu nhiên** cho tới khi tổng số task bằng `desiredCount`. Thông tin thêm về `maximumPercent` và `desiredCount`, xem *Service definition parameters* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/service_definition_parameters.html>).

Service scheduler có logic **giới hạn tần suất** khởi động lại task nếu task liên tục khởi động thất bại. Nếu một task dừng mà **chưa vào trạng thái `RUNNING`**, service scheduler bắt đầu **làm chậm** các lần thử khởi chạy và gửi một **service event message**. Hành vi này ngăn tài nguyên không cần thiết bị dùng cho task lỗi trước khi bạn kịp khắc phục. Sau khi service được cập nhật, service scheduler tiếp tục hành vi lập lịch bình thường. Thông tin thêm, xem *Amazon ECS service throttle logic* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/service-throttle-logic.html>) và *Viewing Amazon ECS service event messages* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/service-event-messages.html>).

**💡 Giải thích thêm**

Service scheduler làm gì, theo đoạn trên:

| Việc | Khi nào |
|---|---|
| Khởi chạy task thay thế | Task lỗi, dừng, hoặc hạ tầng bên dưới lỗi |
| Thay task unhealthy | Container health check hoặc health check target group thất bại |
| Làm chậm việc khởi chạy lại + gửi service event | Task liên tục dừng mà chưa vào `RUNNING` |

Luồng thay task unhealthy:

```
Task bị đánh dấu unhealthy
   │
   ├─ maximumPercent CHO PHÉP chạy thêm task trước:
   │     khởi động task thay thế
   │       ├─ task thay thế HEALTHY   → dừng task unhealthy
   │       └─ task thay thế UNHEALTHY → dừng task thay thế HOẶC task cũ, cho tổng = desiredCount
   │
   └─ maximumPercent KHÔNG cho phép:
         dừng từng task unhealthy (ngẫu nhiên) → khởi động task thay thế → lặp lại
   │
   ▼
Mọi task đã healthy; nếu tổng > desiredCount → dừng ngẫu nhiên task healthy cho tới khi bằng desiredCount
```

Hai tham số quyết định cách thay thế: **`maximumPercent`** (có chỗ chạy thêm task trước không) và **`desiredCount`** (số task cuối cùng cần có).

---

## 1. Lựa chọn compute cho hạ tầng

**📘 Theo docs AWS**

Có hai lựa chọn compute để phân bổ task.

- **Capacity provider strategy** khiến Amazon ECS phân bổ task vào một hoặc nhiều capacity provider.

  Nếu bạn muốn chạy workload trên **Amazon ECS Managed Instances**, bạn **phải** dùng lựa chọn Capacity provider strategy.

  Với **capacity provider strategy**, console chọn sẵn một lựa chọn compute theo mặc định. Sau đây là thứ tự console dùng để chọn mặc định:

  - Nếu cluster có **strategy capacity provider mặc định** được định nghĩa, strategy đó được chọn.
  - Nếu cluster **không** có strategy mặc định nhưng bạn đã thêm **capacity provider Fargate** vào cluster, một strategy tuỳ chỉnh dùng capacity provider **`FARGATE`** được chọn.
  - Nếu cluster **không** có strategy mặc định nhưng bạn đã thêm **một hoặc nhiều capacity provider Auto Scaling group** vào cluster, lựa chọn **Use custom (Advanced)** được chọn và bạn cần **tự định nghĩa** strategy.
  - Nếu cluster **không** có strategy mặc định và **không** có capacity provider nào được thêm vào cluster, **Fargate launch type** được chọn.

- **Launch type** khiến Amazon ECS khởi chạy task trực tiếp trên Fargate hoặc trên các EC2 instance đã đăng ký vào cluster.

  Nếu bạn muốn chạy workload trên Amazon ECS Managed Instances, bạn phải dùng lựa chọn Capacity provider strategy.

  Theo mặc định, service khởi động trong các subnet thuộc VPC của cluster.

**💡 Giải thích thêm**

| Lựa chọn | Task chạy ở đâu | Managed Instances |
|---|---|---|
| Capacity provider strategy | Một hoặc nhiều capacity provider | **Bắt buộc** dùng lựa chọn này |
| Launch type | Fargate hoặc EC2 instance đã đăng ký | Không dùng được |

Console chọn mặc định theo thứ tự:

```
Cluster có strategy mặc định?            ─ có ─► dùng strategy đó
        │ không
Có capacity provider Fargate?            ─ có ─► strategy tuỳ chỉnh dùng FARGATE
        │ không
Có capacity provider Auto Scaling group? ─ có ─► "Use custom (Advanced)" — bạn tự định nghĩa
        │ không
        ▼
Fargate launch type
```

Câu về Managed Instances xuất hiện ở **cả hai** gạch đầu dòng trên trang gốc, nhưng cùng một ý: muốn dùng Managed Instances thì phải chọn Capacity provider strategy.

---

## 2. Service auto scaling

**📘 Theo docs AWS**

**Service auto scaling** là khả năng tự động tăng hoặc giảm số task mong muốn trong Amazon ECS service. Amazon ECS dùng dịch vụ **Application Auto Scaling** để cung cấp chức năng này.

Thông tin thêm, xem *Automatically scale your Amazon ECS service* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/service-auto-scaling.html>).

---

## 3. Cân bằng tải cho service

**📘 Theo docs AWS**

Amazon ECS service chạy trên AWS Fargate hỗ trợ **Application Load Balancer**, **Network Load Balancer** và **Gateway Load Balancer**. Dùng bảng sau để biết nên dùng loại load balancer nào.

| Loại load balancer | Dùng trong các trường hợp |
|---|---|
| **Application Load Balancer** | Định tuyến traffic HTTP/HTTPS (tầng 7). Application Load Balancer có một số tính năng khiến nó hấp dẫn khi dùng với Amazon ECS service: • Mỗi service có thể nhận traffic từ **nhiều load balancer** và mở **nhiều port** được cân bằng tải bằng cách chỉ định nhiều target group. • Được hỗ trợ bởi task chạy trên **cả Fargate và EC2 instance**. • Application Load Balancer cho phép container dùng **dynamic host port mapping** (để **nhiều task của cùng service** chạy được trên mỗi container instance). • Application Load Balancer hỗ trợ **định tuyến theo path** và **quy tắc ưu tiên** (để **nhiều service** dùng chung một listener port trên **một** Application Load Balancer). |
| **Network Load Balancer** | Định tuyến traffic TCP hoặc UDP (tầng 4). |
| **Gateway Load Balancer** | Định tuyến traffic TCP hoặc UDP (tầng 4). Dùng **thiết bị ảo (virtual appliance)**, như firewall, hệ thống phát hiện và ngăn chặn xâm nhập, và hệ thống kiểm tra gói tin sâu (deep packet inspection). |

Thông tin thêm, xem *Use load balancing to distribute Amazon ECS service traffic* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/service-load-balancing.html>).

**💡 Giải thích thêm**

| Loại | Tầng | Điểm nổi bật theo bảng |
|---|---|---|
| Application Load Balancer | 7 (HTTP/HTTPS) | Nhiều target group / nhiều port; Fargate và EC2; dynamic host port mapping; định tuyến theo path và quy tắc ưu tiên |
| Network Load Balancer | 4 (TCP/UDP) | — |
| Gateway Load Balancer | 4 (TCP/UDP) | Cho virtual appliance: firewall, IDS/IPS, deep packet inspection |

Câu dẫn của mục nói về service "chạy trên AWS Fargate", trong khi chính dòng ALB của bảng ghi ALB hỗ trợ cả Fargate lẫn EC2 instance.

---

## 4. Kết nối các service với nhau

**📘 Theo docs AWS**

Nếu bạn cần một ứng dụng kết nối tới các ứng dụng khác chạy dưới dạng Amazon ECS service, Amazon ECS cung cấp các cách sau để làm việc này **không cần load balancer**:

- **Service Connect** — Cho phép giao tiếp service-to-service với **tự động tìm thấy (discovery)** bằng **tên ngắn** và **port chuẩn**.
- **Service discovery** — Service discovery dùng **Route 53** để tạo namespace cho service, giúp service được tìm thấy qua **DNS**.
- **Amazon VPC Lattice** — VPC Lattice là dịch vụ mạng ứng dụng được quản lý hoàn toàn, dùng để kết nối, bảo mật và giám sát service **giữa nhiều account và VPC**. Dịch vụ này **có tính phí**.

Thông tin thêm, xem *Interconnect Amazon ECS services* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/interconnecting-services.html>).

**💡 Giải thích thêm**

| Cách | Cơ chế theo trang | Ghi chú |
|---|---|---|
| Service Connect | Tự động discovery, tên ngắn, port chuẩn | — |
| Service discovery | Route 53 tạo namespace, tìm qua DNS | — |
| Amazon VPC Lattice | Dịch vụ mạng ứng dụng được quản lý, nhiều account và VPC | Có phí |

Cả ba đều là cách kết nối **không cần load balancer**.

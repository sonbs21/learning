# Amazon ECS Gateway

> Nguồn: **Amazon ECS Developer Guide — Amazon ECS Gateway**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/gateway-overview.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: diễn đạt lại bằng tiếng Việt, bám sát từng ý và đúng thứ tự của trang gốc, không thêm thông tin ngoài trang. Đây không phải bản dịch từng chữ; khi cần câu chữ chính xác, đọc link gốc ở trên.
> - Khối **💡 Giải thích thêm**: phần giải thích để dễ hiểu hơn, chỉ làm rõ những gì docs đã nói.

---

## Mục lục

1. [Tổng quan](#1-tổng-quan)
2. [Cách Amazon ECS Gateway hoạt động](#2-cách-amazon-ecs-gateway-hoạt-động)
3. [Lợi ích](#3-lợi-ích)
4. [Các khái niệm chính](#4-các-khái-niệm-chính)
5. [Hạ tầng do Gateway quản lý](#5-hạ-tầng-do-gateway-quản-lý)
6. [Giá](#6-giá)
7. [Phạm vi hỗ trợ (Availability)](#7-phạm-vi-hỗ-trợ-availability)
8. [Giới hạn dịch vụ (Service Limits)](#8-giới-hạn-dịch-vụ-service-limits)
9. [Những điều cần cân nhắc (Considerations)](#9-những-điều-cần-cân-nhắc-considerations)
10. [Tóm tắt](#10-tóm-tắt)

---

## 1. Tổng quan

**📘 Theo docs AWS**

Amazon ECS Gateway giúp đơn giản hoá phần **ingress networking** (đưa traffic từ bên ngoài vào) cho ứng dụng container. Nó tự động tạo và quản lý hạ tầng định tuyến traffic tới các ECS service của bạn.

Thay vì phải cấu hình riêng cho **từng service** các thành phần như Application Load Balancer, listener, target group, security group và certificate, bạn chỉ cần khai báo cấu hình ingress **một lần trên gateway**, rồi dùng chung hạ tầng đó cho nhiều service.

Cách dùng:
- Bạn tạo một gateway với cấu hình mạng mong muốn và liên kết nó với một hoặc nhiều ECS cluster.
- Từng service tự "đăng ký tham gia" (opt in) bằng cách thêm tham số **gateway connector**.
- Sau đó ECS tạo và quản lý hạ tầng ingress **trong chính AWS account của bạn**, và cấp cho mỗi service được kết nối **một hostname HTTPS riêng**.

Trang liên quan:
- Bắt đầu: *Create and manage a gateway* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/gateway-managing.html>
- Kết nối service vào gateway có sẵn: *Connect services to a gateway* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/gateway-connect-service.html>

**💡 Giải thích thêm**

Trước đây, muốn expose một service ra ngoài qua HTTPS, bạn thường phải tự dựng chuỗi: ALB → listener HTTPS → certificate ACM → listener rule → target group → security group cho ALB và cho service. Mỗi service mới lại lặp lại phần lớn công việc này.

Gateway gom phần dùng chung (ALB, listener…) về một nơi, còn phần riêng của từng service (target group, rule, certificate, hostname…) thì ECS tự tạo khi service kết nối.

```
Trước:   Service A ─ ALB A + listener + TG + SG + cert
         Service B ─ ALB B + listener + TG + SG + cert
         ...

Với Gateway:
                       ┌─ rule + TG + cert + hostname ─► Service A
  Gateway ─ ALB chung ─┼─ rule + TG + cert + hostname ─► Service B
  (cấu hình 1 lần)     └─ rule + TG + cert + hostname ─► Service C
```

---

## 2. Cách Amazon ECS Gateway hoạt động

### 2.1. Hai vai trò (persona)

**📘 Theo docs AWS**

Gateway **tách việc quản lý hạ tầng khỏi việc triển khai ứng dụng**, phục vụ hai nhóm chính:

**Platform team — sở hữu gateway**
- Tạo và cấu hình gateway, liên kết gateway với ECS cluster.
- Cấu hình của gateway áp dụng cho **mọi service** kết nối vào nó, gồm ba nhóm thiết lập:
  - **Networking**: service có truy cập được từ internet hay chỉ trong nội bộ VPC; load balancer dùng những subnet nào.
  - **Security**: security group nào kiểm soát traffic đi vào; web ACL nào của AWS WAF được áp dụng.
  - **Operations**: access log được gửi về đâu; cho phép bao nhiêu capacity của load balancer; header nào trong request dùng để nhận diện **traffic test** khi triển khai dạng progressive deployment.
- Một số thiết lập trong đó **không thể đổi** sau khi tạo gateway. (Xem *Customizing a gateway* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/gateway-managing.html#gateway-customize>.)

**Application team — đưa service vào gateway**
- Thêm tham số **gateway connector** khi tạo hoặc cập nhật service.
- Connector chỉ định **tên container port** nhận traffic, và có thể đặt thêm **bộ lọc access type** và **health check**.

**💡 Giải thích thêm**

Có thể hình dung như sau:

| | Platform team | Application team |
|---|---|---|
| Làm việc với | Gateway, association với cluster | Service (gateway connector) |
| Quyết định | Public/internal, subnet, SG, WAF, log, capacity, header test | Port nào nhận traffic, access type, health check |
| Cần hiểu networking? | Có | Docs nói là không cần (xem mục 3) |

"Progressive deployment" ở đây là kiểu triển khai mà một phần traffic (traffic test) được đưa vào phiên bản mới trước; header mà platform team cấu hình chính là dấu hiệu để phân biệt traffic test đó.

### 2.2. Ba bước thiết lập ingress

**📘 Theo docs AWS**

1. **Tạo gateway** — platform team tạo gateway và khai báo cấu hình ingress, gồm thiết lập mạng và bảo mật.
2. **Liên kết gateway với cluster** — platform team gắn gateway với một hoặc nhiều ECS cluster, để service trong các cluster đó dùng được gateway.
3. **Kết nối service** — từng service tự opt in. Application team thêm gateway connector khi tạo hoặc cập nhật service, chỉ rõ container port nhận traffic.

ECS **chưa tạo hạ tầng nào** cho tới khi service đầu tiên kết nối. Khi đó, ECS sẽ:
- tạo mới hoặc dùng lại các tài nguyên dùng chung;
- tạo các tài nguyên riêng cho service;
- tạo một **gateway route** để đưa traffic tới service qua một URL HTTPS riêng.

**💡 Giải thích thêm**

```
[Platform] Tạo gateway ──► [Platform] Associate với cluster ──► [App] Thêm connector vào service
     (chưa tạo gì)              (vẫn chưa tạo gì)                    │
                                                                       ▼
                                              ECS tạo/reuse ALB, listener, SG chung
                                              + tạo TG, rule, cert, hostname, SG cho service
                                              + tạo gateway route → URL https://...
```

Điểm đáng chú ý: tạo gateway và associate **không tốn tiền ALB** cho tới khi có service đầu tiên kết nối, vì lúc đó mới có tài nguyên thật.

---

## 3. Lợi ích

**📘 Theo docs AWS**

Gateway giúp việc thiết lập và quản lý Application Load Balancer cho ứng dụng đơn giản hơn, với các lợi ích:

- **Cấu hình tập trung** — platform team khai báo thiết lập mạng, bảo mật, quản lý traffic một lần; mọi service kết nối vào gateway đều thừa hưởng, kể cả ở nhiều cluster khác nhau. Application team có ingress cho service mà không phải tự cấu hình tài nguyên hay cần chuyên môn về networking.
- **Tự động quản lý hạ tầng** — khi service opt in, ECS tự tạo và quản lý ALB, listener, target group, security group, certificate và DNS hostname.
- **Tối ưu chi phí** — nhiều service dùng chung cấu hình mạng có thể **dùng chung ALB**, giảm chi phí.
- **Hạ tầng minh bạch** — mọi tài nguyên được tạo trong AWS account của bạn; bạn xem và quản lý chúng bằng AWS Management Console và AWS API.

**💡 Giải thích thêm**

- "Tối ưu chi phí" đến từ việc mỗi ALB có phí cố định theo giờ; gom nhiều service vào một ALB thì số ALB giảm. Theo mục 5, tối đa 25 service dùng chung một ALB.
- "Minh bạch" nghĩa là đây không phải hạ tầng ẩn do AWS giữ riêng — bạn nhìn thấy ALB, target group… trong console. Tuy vậy, mục 9 cảnh báo **không nên tự sửa** các tài nguyên này.

---

## 4. Các khái niệm chính

**📘 Theo docs AWS**

| Khái niệm | Ý nghĩa |
|---|---|
| **Gateway** | Tài nguyên cấp **account**, có ARN riêng. Nó định nghĩa cấu hình ingress và quản lý hạ tầng bên dưới cho các service kết nối vào. Tên gateway là **duy nhất trong một account + Region**. |
| **Gateway-cluster association** | Liên kết giữa một gateway và một cluster, giúp service trong cluster đó dùng được gateway. Quan hệ **nhiều–nhiều**: một gateway phục vụ nhiều cluster, một cluster dùng nhiều gateway (giới hạn xem mục 8). |
| **Gateway connector** | Tham số opt-in trên **service**, kết nối service với **tất cả gateway tương thích** đã liên kết với cluster của nó. Connector chỉ định tên container port cần expose, kèm tuỳ chọn access type và health check. |
| **Gateway route** | Tài nguyên có ARN riêng, đại diện cho **một kết nối** giữa một service và một gateway. ECS tạo route khi service kết nối và xoá khi service ngắt kết nối. Route cho biết trạng thái kết nối, URL của service và các tài nguyên riêng của service bên dưới. |

**💡 Giải thích thêm**

Quan hệ giữa các khái niệm:

```
Gateway ◄──(association, N–N)──► Cluster
                                   │
                                   └── Service (có 1 gateway connector)
                                          │
              Gateway route = 1 cặp (service, gateway)
              → nếu service khớp 2 gateway thì có 2 route
```

Gateway route là chỗ để xem "service của tôi đã được expose chưa, URL là gì".

---

## 5. Hạ tầng do Gateway quản lý

**📘 Theo docs AWS**

ECS tạo hai nhóm hạ tầng ingress trong account của bạn:

| Loại | Tài nguyên được quản lý | Mô tả |
|---|---|---|
| **Hạ tầng dùng chung** | Application Load Balancer, listener của load balancer, security group của load balancer | Do gateway quản lý và chia sẻ cho nhiều ECS service. |
| **Hạ tầng riêng cho từng service** | Certificate của AWS Certificate Manager, DNS hostname, listener rule, target group, security group của service | ECS tạo cho từng gateway route để đưa traffic tới đúng service. |

**Định tuyến:**
- ECS dùng **host-based routing** để chuyển request từ ALB dùng chung tới đúng service.
- Mỗi service có một hostname được sinh tự động dạng `https://<unique-id>.ecs.<region>.on.aws`. Tên miền này khác nhau tùy AWS partition.

**Vòng đời tài nguyên:**
- Tạo gateway hoặc liên kết với cluster: **chưa tạo gì**.
- Service đầu tiên kết nối: ECS tạo ALB đầu tiên, listener và security group của nó, rồi thêm tài nguyên riêng của service đó.
- Thêm service: ECS dùng lại một ALB còn chỗ, hoặc tạo thêm ALB mới.
- Tối đa **25 service dùng chung một ALB**; mỗi service có hostname, certificate và listener rule riêng.

**Tag:**
- ECS gắn tag `AmazonECSManaged=true` và system tag `aws:ecs:gateway-arn` (giá trị là ARN của gateway) lên các tài nguyên nó quản lý.
- Target group, listener rule và security group của service — các tài nguyên tạo cho từng gateway route — có thêm tag `aws:ecs:gateway-route-arn`.
- Dùng các tag này để tìm tài nguyên trong console của Elastic Load Balancing, Amazon EC2 và AWS Certificate Manager.

**💡 Giải thích thêm**

Hình dung một ALB dùng chung:

```
Client ── HTTPS :443 ──► ALB dùng chung (listener HTTPS)
                            │  host-based routing theo Host header
                            ├─ Host = abc123.ecs.<region>.on.aws ──► TG service A
                            ├─ Host = def456.ecs.<region>.on.aws ──► TG service B
                            └─ ... (tối đa 25 service / ALB)
```

- Vì định tuyến theo **hostname**, mỗi service cần hostname riêng, nên mỗi service cũng có **certificate** riêng — đó là lý do certificate nằm ở nhóm "riêng cho từng service".
- Khi cần tìm "ALB nào đang chạy service X", lọc theo tag `aws:ecs:gateway-route-arn` của route tương ứng.

---

## 6. Giá

**📘 Theo docs AWS**

Dùng Amazon ECS Gateway **không mất thêm phí**. Bạn chỉ trả tiền cho các tài nguyên AWS được tạo trong account khi service kết nối vào gateway, bao gồm phí Application Load Balancer, log và metric của Amazon CloudWatch, và phí truyền dữ liệu (data transfer) nếu có.

**💡 Giải thích thêm**

Chi phí thực tế phụ thuộc chủ yếu vào **số ALB** gateway tạo ra (mỗi ALB chứa tối đa 25 service) và lượng traffic. Kết hợp với mục 9 (ECS không gom ALB), số ALB có thể cao hơn mức tối thiểu sau khi nhiều service rời đi.

---

## 7. Phạm vi hỗ trợ (Availability)

**📘 Theo docs AWS**

- Có ở **tất cả commercial AWS Region**.
- Hỗ trợ ECS service dùng network mode **`awsvpc`**, trên mọi loại launch type: **EC2, Fargate và Amazon ECS Managed Instances**.
- Tạo và quản lý gateway được qua **ECS console, AWS CLI, AWS SDK và CloudFormation**.

**💡 Giải thích thêm**

Docs không nêu Terraform trong danh sách công cụ. Nếu bạn quản lý hạ tầng bằng Terraform, cần kiểm tra AWS provider đã hỗ trợ tài nguyên gateway hay chưa; docs trang này không đề cập.

---

## 8. Giới hạn dịch vụ (Service Limits)

**📘 Theo docs AWS**

| Giới hạn | Giá trị |
|---|---|
| Số gateway-cluster association tối đa cho mỗi cluster | 5 |
| Số cluster tối đa cho mỗi gateway | 10.000 |
| Số gateway connector tối đa cho mỗi service | 1 |
| Số service tối đa trên mỗi Application Load Balancer | 25 |
| Số Application Load Balancer tối đa cho mỗi gateway | 200 |

Các tài nguyên AWS bên dưới cũng có quota riêng. Xem *Quotas for your Application Load Balancers* — <https://docs.aws.amazon.com/elasticloadbalancing/latest/application/load-balancer-limits.html>.

**💡 Giải thích thêm**

Từ hai con số 25 và 200 suy ra: một gateway phục vụ tối đa khoảng **25 × 200 = 5.000 service** (nếu các ALB đều được lấp đầy). Vì ECS không gom ALB (mục 9), con số thực tế có thể thấp hơn.

---

## 9. Những điều cần cân nhắc (Considerations)

**📘 Theo docs AWS**

1. **Chỉ phục vụ traffic `HTTPS`.** Gateway dùng ALB với host-based routing, nên phục vụ web app và API truy cập theo hostname. Listener chỉ dùng `HTTPS` trên **port 443**; không hỗ trợ giao thức nào khác.

2. **Service phải dùng network mode `awsvpc`.** Service dùng `bridge`, `host` hoặc `none` không kết nối được với gateway.

3. **Gateway chỉ hoạt động trong một account.** Gateway và các cluster của nó phải cùng một AWS account; không liên kết được với cluster ở account khác.

4. **Không dùng được với Express Mode service.** Gateway chỉ phục vụ ECS service (thông thường).

5. **Mỗi service chỉ có một gateway connector.** Không thêm được connector thứ hai. Tuy vậy, một connector vẫn có thể kết nối service tới **nhiều** gateway.

6. **Service không chọn gateway theo tên.** Connector kết nối service tới **mọi gateway tương thích** đã liên kết với cluster. Gateway được coi là tương thích khi thoả cả ba điều kiện:
   - khớp bộ lọc access type (nếu service có đặt);
   - nằm **cùng VPC** với service;
   - bao phủ **mọi Availability Zone** mà service đang chạy.

   ECS loại mọi gateway không khớp; nếu trong cluster không có gateway nào thoả cả ba điều kiện thì request sẽ thất bại. (Xem *How Amazon ECS selects compatible gateways* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/gateway-connect-service.html#gateway-select-compatible>.)

7. **Một số thiết lập không đổi được sau khi tạo.** Không đổi được: **tên, type, scheme, listener port, listener protocol, subnet, IP address type** của gateway. Muốn đổi, phải tạo gateway mới rồi chuyển service sang. (Xem *Update a gateway* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/gateway-managing.html#gateway-update>.)

8. **ECS không gom (consolidate) load balancer.** Gateway thêm ALB khi các ALB hiện có đã đầy và xoá ALB khi nó trống hẳn. Gateway không di chuyển service giữa các ALB, nên sau khi service rời đi có thể còn lại nhiều ALB chỉ chứa một phần.

9. **ECS không sửa lại thay đổi làm bên ngoài nó.** Nếu bạn sửa hoặc xoá một tài nguyên do gateway quản lý, ECS sẽ không khôi phục. Traffic tới service bị ảnh hưởng có thể dừng, và các lần deploy service, cập nhật gateway hoặc dọn dẹp về sau có thể thất bại. (Xem *Changes made outside Amazon ECS* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/gateway-troubleshooting.html#gateway-changes-outside-ecs>.)

**💡 Giải thích thêm**

Ý nghĩa thực tế của từng điểm:

| # | Hệ quả |
|---|---|
| 1 | Không dùng cho gRPC qua giao thức khác, TCP/UDP thuần, hay HTTP không mã hoá; cần NLB hoặc giải pháp khác cho các trường hợp đó. |
| 2 | Service EC2 đang dùng `bridge` (dynamic port) phải chuyển sang `awsvpc` trước khi dùng gateway. |
| 3 | Mô hình multi-account (mỗi team một account) cần gateway riêng ở từng account. |
| 5–6 | Việc service vào gateway nào do **cấu hình của platform team** (association, VPC, AZ, access type) quyết định, không phải do app team chọn tên. Muốn tách public/internal thì dùng bộ lọc access type. |
| 7 | Cần thiết kế kỹ subnet, scheme (public/internal) ngay từ đầu; đổi về sau nghĩa là di chuyển service sang gateway mới. |
| 8 | Theo dõi số ALB để kiểm soát chi phí, vì ALB dư không tự được gom lại. |
| 9 | Không chỉnh tay ALB, listener rule, target group, SG có tag `AmazonECSManaged=true`; mọi thay đổi nên đi qua cấu hình gateway/service. |

---

## 10. Tóm tắt

- **ECS Gateway** cấu hình ingress HTTPS **một lần**, dùng chung cho nhiều service; ECS tự tạo và quản lý ALB, listener, target group, SG, certificate, hostname trong account của bạn.
- **Hai vai trò**: platform team (gateway + association), application team (gateway connector trên service).
- **Ba bước**: tạo gateway → associate với cluster → service thêm connector. Chưa có service thì chưa tạo tài nguyên.
- **Hạ tầng**: phần dùng chung (ALB, listener, SG của ALB) và phần riêng mỗi service (cert, hostname, rule, TG, SG service); định tuyến theo host, hostname dạng `https://<unique-id>.ecs.<region>.on.aws`; tối đa 25 service / ALB.
- **Giá**: gateway miễn phí, chỉ trả cho tài nguyên bên dưới.
- **Hỗ trợ**: mọi commercial Region; service `awsvpc` trên EC2, Fargate, Managed Instances.
- **Giới hạn chính**: 5 association / cluster, 1 connector / service, 25 service / ALB, 200 ALB / gateway.
- **Lưu ý**: chỉ HTTPS :443; chỉ `awsvpc`; cùng account; không hỗ trợ Express Mode; không chọn gateway theo tên; nhiều thiết lập bất biến; không gom ALB; không tự sửa tài nguyên do ECS quản lý.

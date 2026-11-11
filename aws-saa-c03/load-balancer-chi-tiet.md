# Load Balancer chuyên sâu — AWS SAA-C03

> Tài liệu tách riêng phần Load Balancer từ Tuần 5. Mục tiêu là hiểu load balancer không chỉ như “một địa chỉ nhận request”, mà như một lớp điều phối traffic, health check, TLS termination, routing và failure isolation giữa client với backend.

## 1. Load Balancer là gì?

Load Balancer là thành phần đứng trước một nhóm backend và phân phối traffic tới các target phù hợp.

~~~text
Client
  ↓
Load Balancer
  ↓
Target 1
Target 2
Target 3
~~~

Nếu client kết nối trực tiếp tới một EC2 duy nhất:

- Một instance lỗi có thể làm service unavailable.
- Client phải biết địa chỉ backend.
- Khó scale ngang.
- Khó thay instance.
- TLS, routing và health check bị phân tán vào application.
- Có thể tạo điểm lỗi đơn.

Load Balancer giúp:

- Phân phối traffic.
- Loại target unhealthy.
- Hỗ trợ Multi-AZ.
- Tạo một endpoint ổn định cho client.
- Terminate TLS.
- Route theo host/path/header tùy loại.
- Hỗ trợ rolling, blue/green hoặc canary deployment.
- Kết hợp với Auto Scaling.
- Giảm coupling giữa client và backend.

Load Balancer không tự động:

- Tạo thêm EC2.
- Sửa lỗi application.
- Replicate database.
- Làm application stateless.
- Thay thế Security Group.
- Thay thế health check của business logic.
- Bảo đảm database không quá tải.

## 2. Mối quan hệ giữa các thành phần

~~~mermaid
flowchart TB
    Client[Client / Internet]
    DNS[Route 53]
    LB[Load Balancer]
    Listener[Listener]
    Rule[Listener Rules]
    TG[Target Group]
    HC[Health Check]
    T1[Target 1]
    T2[Target 2]
    T3[Target 3]
    ASG[Auto Scaling Group]
    SG[Security Groups]
    NACL[Network ACL]
    App[Application]
    DB[Database]

    Client --> DNS
    DNS --> LB
    LB --> Listener
    Listener --> Rule
    Rule --> TG
    TG --> T1
    TG --> T2
    TG --> T3
    TG -. kiểm tra .-> HC
    HC -. trạng thái target .-> TG
    ASG -. tạo/thay thế .-> T1
    ASG -. tạo/thay thế .-> T2
    SG -. bảo vệ .-> LB
    SG -. bảo vệ .-> T1
    NACL -. bảo vệ subnet .-> LB
    NACL -. bảo vệ subnet .-> T1
    T1 --> App
    T2 --> App
    T3 --> App
    App --> DB
~~~

## 3. Các khái niệm cốt lõi

| Thành phần | Vai trò |
|---|---|
| Load balancer | Nhận và phân phối traffic |
| Listener | Lắng nghe một port/protocol |
| Listener rule | Quyết định routing/action |
| Default action | Xử lý request không match rule cụ thể |
| Target group | Nhóm target cùng cách xử lý traffic |
| Target | EC2, IP, container, Lambda hoặc resource hỗ trợ |
| Health check | Kiểm tra target có phục vụ được không |
| Security Group | Firewall cho load balancer/target |
| NACL | Firewall ở cấp subnet |
| Auto Scaling | Tạo, tăng, giảm và thay target |
| Route 53 | DNS trỏ client tới load balancer |

Luồng request thường là:

~~~text
Client
  → DNS
  → Load Balancer
  → Listener
  → Listener Rule
  → Target Group
  → Healthy Target
  → Application
~~~

---

# Phần I — Các loại AWS Load Balancer

## 4. Application Load Balancer

Application Load Balancer, hay ALB, hoạt động ở Layer 7 và hiểu HTTP/HTTPS.

ALB có thể đọc:

- Host header.
- URL path.
- HTTP header.
- Query string.
- HTTP method trong pattern phù hợp.
- Cookie hoặc thông tin liên quan theo rule hỗ trợ.

ALB phù hợp với:

- Web application.
- REST API.
- Microservices.
- Container application.
- Host-based routing.
- Path-based routing.
- HTTP to HTTPS redirect.
- TLS termination.
- Authentication integration theo tính năng hỗ trợ.
- Target là EC2, IP, container hoặc Lambda trong pattern phù hợp.

Ví dụ:

~~~text
api.example.com        → API target group
admin.example.com      → Admin target group
example.com/static/*   → Static target group
example.com/api/*      → API target group
~~~

## 5. Network Load Balancer

Network Load Balancer, hay NLB, hoạt động chủ yếu ở Layer 4.

NLB phù hợp với:

- TCP.
- UDP.
- TLS.
- High throughput.
- Low latency.
- Static IP hoặc Elastic IP theo AZ.
- Protocol không phải HTTP.
- TLS pass-through.
- Preserve client source IP trong pattern hỗ trợ.
- PrivateLink endpoint service.

NLB không route theo URL path hoặc Host header như ALB. Nếu requirement là:

~~~text
/api/*      → API servers
/static/*   → static servers
~~~

thì ALB thường phù hợp hơn.

Nếu requirement là:

~~~text
TCP 3306
UDP 53
TCP custom protocol
Static IP theo AZ
~~~

thì NLB thường là lựa chọn phù hợp hơn.

## 6. Gateway Load Balancer

Gateway Load Balancer, hay GWLB, dùng để triển khai và scale network appliance.

Use case:

- Firewall.
- Intrusion Detection System.
- Intrusion Prevention System.
- Deep packet inspection.
- Traffic inspection appliance.
- Network security appliance.

Mô hình:

~~~text
Client traffic
  ↓
GWLB Endpoint
  ↓
Gateway Load Balancer
  ↓
Network Appliance Fleet
  ↓
Destination
~~~

Ở mức SAA-C03 cần nhớ:

- GWLB không phải load balancer cho HTTP path routing.
- GWLB phân phối traffic tới appliance.
- GWLB dùng cho transparent appliance pattern.
- GWLB thường đi cùng GENEVE trên port 6081.
- GWLB Endpoint giúp chèn appliance vào network path.

## 7. Classic Load Balancer

Classic Load Balancer là thế hệ cũ hơn.

Nó có thể xuất hiện trong đề hoặc hệ thống legacy, nhưng với kiến trúc mới thường ưu tiên:

- ALB cho HTTP/HTTPS Layer 7.
- NLB cho TCP/UDP/TLS Layer 4.
- GWLB cho network appliance.

Không nên chọn Classic Load Balancer cho requirement hiện đại nếu ALB/NLB đáp ứng tốt hơn.

## 8. Bảng so sánh

| Tiêu chí | ALB | NLB | GWLB |
|---|---|---|---|
| Layer | Layer 7 | Layer 4 | Appliance insertion |
| Protocol | HTTP/HTTPS | TCP/UDP/TLS | GENEVE/appliance |
| Host/path routing | Có | Không | Không |
| Static IP | Không phải điểm mạnh | Có theo AZ | Qua endpoint pattern |
| TLS termination | Có | Có | Không phải use case chính |
| Target | EC2, IP, container, Lambda | EC2, IP, container, appliance | Network appliance |
| Use case | Web/API/microservices | TCP/UDP/high throughput | Firewall/IDS/IPS |
| WAF integration | Phù hợp | Không như ALB | Không phải mục tiêu |
| PrivateLink service | Không phải use case chính | Có | Endpoint service pattern |

## 9. Decision tree chọn Load Balancer

~~~text
Có phải HTTP/HTTPS web hoặc API không?
  ├── Có
  │   ├── Cần host/path/header routing?
  │   │   └── ALB
  │   ├── Cần WAF hoặc application-aware behavior?
  │   │   └── ALB
  │   └── Không cần L7 nhưng vẫn là HTTP?
  │       └── ALB thường vẫn là lựa chọn tự nhiên
  │
  └── Không
      ├── Cần TCP, UDP hoặc TLS?
      │   └── NLB
      ├── Cần static IP hoặc low latency/high throughput?
      │   └── NLB
      ├── Cần phân phối traffic qua firewall/IDS/IPS?
      │   └── GWLB
      └── Workload legacy?
          └── Xem Classic Load Balancer nếu requirement bắt buộc
~~~

---

# Phần II — ALB chi tiết

## 10. Cấu trúc của ALB

~~~text
Internet-facing ALB
├── Listener 80 HTTP
│   └── Redirect sang HTTPS 443
├── Listener 443 HTTPS
│   ├── Rule host api.example.com
│   ├── Rule path /admin/*
│   ├── Rule path /static/*
│   └── Default action
└── Target groups
    ├── API targets
    ├── Admin targets
    └── Static targets
~~~

ALB phải được triển khai trong ít nhất hai Availability Zone nếu cần high availability.

ALB node xuất hiện trong các subnet được chọn. Client không truy cập trực tiếp vào target private nếu request đi qua ALB.

## 11. Internet-facing và internal ALB

### Internet-facing ALB

Có địa chỉ public và nhận traffic từ Internet nếu:

- Đặt trong public subnet.
- Public route table có route tới Internet Gateway.
- Security Group cho phép traffic.
- DNS trỏ tới ALB.

Mô hình:

~~~text
Internet
  ↓
Internet-facing ALB
  ↓
Private application targets
~~~

### Internal ALB

Có địa chỉ private và dùng cho:

- Internal API.
- Service-to-service.
- Microservice.
- Shared service.
- Private application ingress.
- Workload trong VPC hoặc network connected.

Mô hình:

~~~text
Private client
  ↓
Internal ALB
  ↓
Private targets
~~~

Internal ALB không nhận Internet trực tiếp. Client phải có network path tới VPC.

## 12. Listener

Listener xác định:

- Protocol.
- Port.
- Certificate nếu là TLS listener.
- Default action.
- Listener rules.

Ví dụ:

~~~text
HTTP listener:
  Port 80
  Action: redirect sang HTTPS 443

HTTPS listener:
  Port 443
  Certificate: ACM certificate
  Action: forward tới target group
~~~

Một listener có thể có nhiều rule. Nếu request không match rule cụ thể, default action được dùng.

## 13. Listener rule

Một rule thường gồm:

- Priority.
- Condition.
- Action.

Conditions có thể dựa trên:

- Host header.
- Path pattern.
- HTTP header.
- Query string.
- Source IP trong pattern hỗ trợ.

Actions có thể gồm:

- Forward tới target group.
- Weighted forward tới nhiều target group.
- Redirect.
- Fixed response.
- Authenticate theo tính năng hỗ trợ.

### Host-based routing

~~~text
api.example.com
  → API Target Group

admin.example.com
  → Admin Target Group
~~~

### Path-based routing

~~~text
example.com/api/*
  → API Target Group

example.com/static/*
  → Static Target Group

example.com/admin/*
  → Admin Target Group
~~~

### Weighted target group

Có thể chia traffic giữa hai target group:

~~~text
Version blue  → 90%
Version green → 10%
~~~

Use case:

- Canary deployment.
- Blue/green migration.
- Gradual rollout.
- Test version mới với một phần traffic.

Không nhầm weighted forward của ALB với weighted routing của Route 53:

- ALB chia traffic sau khi request đã đến load balancer.
- Route 53 chia DNS answer trước khi client kết nối endpoint.

## 14. Rule priority và default action

Rule có priority. Cần thiết kế sao cho:

- Rule cụ thể hơn được match trước.
- Rule tổng quát không che mất rule cụ thể.
- Có default action rõ ràng.
- Không để request không match đi tới backend sai.

Ví dụ logic:

~~~text
Priority 1:
  Host = api.example.com
  → API target group

Priority 2:
  Path = /admin/*
  → Admin target group

Priority 3:
  Path = /static/*
  → Static target group

Default:
  → Main target group
~~~

Nếu một request match nhiều rule, rule theo priority được áp dụng theo thứ tự cấu hình.

## 15. TLS termination trên ALB

ALB có thể terminate TLS ở listener 443.

~~~text
Client
  ↓ HTTPS
ALB listener 443
  ↓ HTTP hoặc HTTPS
Target
~~~

Lợi ích:

- Centralize certificate management.
- Giảm TLS processing trên từng target.
- Dễ redirect HTTP sang HTTPS.
- Dễ áp dụng policy TLS.
- Backend có thể dùng HTTP trong private network nếu threat model cho phép.

Nếu cần mã hóa end-to-end:

~~~text
Client
  ↓ HTTPS
ALB listener 443
  ↓ HTTPS
Target listener 8443
~~~

Trong pattern này:

- Target SG chỉ allow HTTPS port từ ALB-SG.
- Target có certificate hoặc cơ chế TLS phù hợp.
- Kiểm tra health check dùng HTTPS nếu backend yêu cầu.
- Xác định rõ nơi terminate TLS.

### ACM

ACM có thể cung cấp và quản lý certificate cho ALB. Khi dùng certificate, chú ý:

- Domain name khớp hostname.
- Certificate còn hiệu lực.
- Certificate ở Region phù hợp với ALB.
- DNS validation record tồn tại.
- Listener gắn đúng certificate.
- Có default certificate khi nhiều domain.

## 16. ALB Security Group

Mẫu phổ biến:

~~~text
ALB-SG
  Inbound:
    TCP 443 từ Internet hoặc CloudFront/WAF
  Outbound:
    Application port tới App-SG

App-SG
  Inbound:
    TCP 8080 từ ALB-SG
~~~

Nếu CloudFront đứng trước ALB, có thể giới hạn ALB chỉ nhận traffic từ CloudFront trong pattern và tài liệu hỗ trợ phù hợp. Không mở backend target cho Internet chỉ vì ALB public.

Target không cần public IP để nhận traffic từ ALB. ALB giao tiếp với target bằng private IP.

## 17. ALB và Auto Scaling

ALB và Auto Scaling có vai trò khác nhau:

| Thành phần | Vai trò |
|---|---|
| ALB | Phân phối request tới target healthy |
| Target Group | Nhóm target và health check |
| Auto Scaling | Tạo, xóa, thay thế và scale target |
| CloudWatch | Cung cấp metric/alarm cho scaling |

Luồng khi instance lỗi:

~~~text
1. Health check fail
2. ALB ngừng gửi request tới target
3. Auto Scaling phát hiện unhealthy instance
4. Instance được terminate/replaced
5. Instance mới đăng ký target group
6. Health check pass
7. ALB đưa target mới vào traffic
~~~

Cần cấu hình:

- Health check grace period.
- Deregistration delay.
- Launch template đúng.
- Startup time đủ.
- User data/application boot ổn định.
- Capacity ở nhiều AZ.

---

# Phần III — Target group và health check

## 18. Target group

Target group là tập hợp target được ALB hoặc NLB quản lý theo cùng một cấu hình.

Target group thường định nghĩa:

- Protocol tới target.
- Port tới target.
- Health check.
- Target type.
- Deregistration behavior.
- Slow start hoặc stickiness theo tính năng hỗ trợ.

Target type có thể là:

- Instance.
- IP.
- Lambda với ALB trong pattern hỗ trợ.
- Container task thông qua IP/instance tùy kiến trúc.

## 19. Target type instance

Khi target type là instance:

- Load balancer gửi traffic tới private IP của instance và port target.
- Instance phải đăng ký vào target group.
- Security Group của instance phải allow traffic từ ALB-SG/NLB path.
- Phù hợp EC2 hoặc container chạy trên EC2.
- Khi instance bị thay thế, cần đăng ký/deregister đúng.

## 20. Target type IP

IP target phù hợp với:

- Container task có IP riêng.
- Workload không phải EC2 trực tiếp.
- Service nằm trong network connected pattern hỗ trợ.
- Backend cần đăng ký bằng IP.

Cần bảo đảm:

- IP reachable.
- Route đúng.
- Security Group/NACL đúng.
- IP không bị thay đổi ngoài cơ chế registration.
- Health check tới đúng port.

## 21. Target type Lambda

ALB có thể tích hợp Lambda trong pattern được hỗ trợ để xử lý HTTP request.

Cần chú ý:

- Latency và timeout.
- Lambda permission cho load balancer invoke.
- Error handling.
- Concurrency.
- Request/response format.
- Không chọn Lambda target chỉ vì muốn thay thế mọi web server; so sánh với API Gateway và Lambda architecture.

## 22. Health check

Health check quyết định target có được đưa vào traffic hay không.

Thông số thường gồm:

- Protocol.
- Port.
- Path.
- Interval.
- Timeout.
- Healthy threshold.
- Unhealthy threshold.
- Matcher/status code.

Ví dụ:

~~~text
Protocol: HTTP
Port: 8080
Path: /health
Expected: 200
~~~

Health check nên:

- Nhẹ.
- Nhanh.
- Ổn định.
- Có status code rõ.
- Kiểm tra application process.
- Không phụ thuộc quá sâu vào database hoặc third-party nếu dependency lỗi tạm thời không nên làm toàn bộ target bị loại.

### Shallow và deep health check

Shallow check:

~~~text
/health
  → application process đang sống
~~~

Deep check:

~~~text
/health
  → application
  → database
  → cache
  → third-party API
~~~

Deep check có thể phản ánh user experience tốt hơn, nhưng dễ tạo cascade failure. Nếu database tạm chậm, toàn bộ target có thể bị đánh unhealthy dù vẫn phục vụ được một số request.

## 23. Health check troubleshooting

Nếu target unhealthy:

1. Target có đang running không?
2. Application có listen đúng port không?
3. Application bind vào đúng network interface không?
4. Health check path có tồn tại không?
5. Status code trả về có match không?
6. ALB-SG có được App-SG allow không?
7. NACL có cho phép hai chiều không?
8. Route table có route đúng không?
9. App có yêu cầu Host header đặc biệt không?
10. TLS certificate/backend protocol có đúng không?
11. Container có restart không?
12. Application log có lỗi không?

## 24. Deregistration delay

Khi target bị remove hoặc unhealthy, load balancer có thể tiếp tục giữ connection hiện tại trong một khoảng thời gian để request hoàn tất.

Deregistration delay giúp:

- Tránh cắt ngang request đang chạy.
- Hỗ trợ graceful deployment.
- Giảm lỗi khi scale-in.

Nếu delay quá ngắn:

- Request dài có thể bị terminate.
- Upload/download có thể fail.

Nếu delay quá dài:

- Target cũ giữ capacity lâu.
- Deployment chậm.
- Scale-in chậm.

## 25. Connection draining và deployment

Khi deploy phiên bản mới:

~~~text
1. Đăng ký target mới
2. Chờ target mới healthy
3. Chuyển traffic dần hoặc chuyển rule
4. Deregister target cũ
5. Chờ request cũ hoàn tất
6. Terminate target cũ
~~~

Đây là lý do health check và deregistration delay ảnh hưởng trực tiếp đến zero-downtime deployment.

---

# Phần IV — NLB chi tiết

## 26. NLB listener

NLB listener thường nhận:

- TCP.
- UDP.
- TLS.

NLB có thể:

- Terminate TLS.
- Pass through TCP tới target.
- Phân phối connection tới target.
- Cung cấp static IP/Elastic IP theo AZ theo pattern hỗ trợ.

NLB không đọc HTTP path như ALB.

## 27. TLS termination và pass-through

### TLS termination tại NLB

~~~text
Client
  ↓ TLS
NLB listener
  ↓ TCP hoặc TLS
Target
~~~

NLB quản lý certificate ở listener và giải mã traffic tại NLB.

### TLS pass-through

~~~text
Client
  ↓ TLS
NLB TCP listener
  ↓ TLS nguyên trạng
Target tự terminate TLS
~~~

Chọn pass-through khi:

- Backend cần tự quản lý certificate.
- Cần end-to-end TLS đến target.
- Load balancer không nên đọc nội dung.
- Protocol/security requirement bắt buộc.

Chọn TLS termination tại NLB khi muốn centralize certificate hoặc giảm xử lý TLS trên target.

## 28. NLB source IP và proxy behavior

NLB thường phù hợp khi backend cần biết client source IP trong pattern hỗ trợ. Tuy nhiên, phải đọc kỹ behavior theo protocol, target type và cấu hình.

Khi troubleshooting source IP:

- Kiểm tra target nhận source IP nào.
- Kiểm tra proxy protocol nếu dùng.
- Kiểm tra application parse header/protocol.
- Không giả định ALB và NLB truyền source information giống nhau.

## 29. Khi không nên chọn NLB

Không chọn NLB nếu requirement chính là:

- Host-based routing.
- Path-based routing.
- Header routing.
- HTTP redirect.
- Application-aware authentication.
- WAF integration theo pattern ALB.

Trong những trường hợp này, ALB thường phù hợp hơn.

---

# Phần V — Gateway Load Balancer

## 30. GWLB và network appliance

GWLB cung cấp điểm phân phối cho appliance fleet:

~~~text
Traffic
  ↓
GWLB Endpoint
  ↓
GWLB
  ↓
Firewall / IDS / IPS
  ↓
GWLB
  ↓
Destination
~~~

Điểm cần nhớ:

- Appliance có thể là third-party hoặc self-managed.
- GWLB hỗ trợ scale và health check appliance.
- Traffic được encapsulate theo GENEVE.
- Endpoint giúp consumer VPC sử dụng service qua private path.
- Route table phải steering traffic qua endpoint.
- Cần tránh asymmetric routing.
- Appliance fleet cần nhiều AZ để HA.

GWLB không phải lựa chọn cho web API cần path-based routing.

---

# Phần VI — Load Balancer và Route 53

## 31. DNS tới load balancer

Route 53 thường đứng trước ALB/NLB:

~~~text
app.example.com
  ↓
Route 53 Alias
  ↓
ALB hoặc NLB
~~~

Alias record có lợi ích:

- Trỏ tới AWS resource.
- Dùng được ở zone apex.
- Không cần hard-code IP của load balancer.
- Theo dõi target health theo behavior hỗ trợ.

Không nên trỏ domain trực tiếp tới IP của ALB vì IP của load balancer có thể thay đổi.

## 32. Route 53 weighted routing và ALB weighted forward

Hai lớp traffic splitting khác nhau:

### Route 53 weighted routing

~~~text
Client
  ↓
Route 53
  ├── ALB blue
  └── ALB green
~~~

Quyết định endpoint ở lớp DNS. Resolver cache và TTL ảnh hưởng behavior.

### ALB weighted target group

~~~text
Client
  ↓
ALB
  ├── Target group blue
  └── Target group green
~~~

Request đã tới ALB rồi mới được chia vào target group.

Chọn Route 53 khi muốn chia giữa Region/endpoint/load balancer khác nhau. Chọn ALB weighted forward khi muốn chia giữa backend version trong cùng một ALB.

## 33. Route 53 failover với load balancer

Mô hình active-passive:

~~~text
Route 53 Failover
├── Primary ALB ở Region A
└── Secondary ALB ở Region B
~~~

Cần có:

- Health check hoặc Evaluate Target Health.
- Secondary application đã sẵn sàng.
- Data replication hoặc restore plan.
- DNS TTL phù hợp.
- Runbook failover.
- Kiểm tra các dependency ngoài Region.
- Cơ chế chuyển write traffic nếu database tách biệt.

Route 53 failover chỉ thay đổi DNS answer. Nó không copy application, không replicate database và không làm warm standby tự động.

---

# Phần VII — Load Balancer và CloudFront

## 34. CloudFront trước ALB

~~~text
User
  ↓
CloudFront Edge
  ↓
WAF
  ↓
ALB
  ↓
Private application targets
~~~

Lợi ích:

- Cache static hoặc dynamic content phù hợp.
- Giảm request về ALB.
- Giảm latency.
- Giảm tải backend.
- Bảo vệ edge bằng WAF.
- Có thể dùng ACM certificate ở CloudFront.
- Hỗ trợ signed URL/cookie.

## 35. ALB làm origin

ALB phù hợp làm CloudFront origin khi:

- Có dynamic application.
- Cần cache một phần path.
- Cần WAF/CloudFront ở edge.
- Origin application nằm trong AWS.
- Muốn CloudFront forward request tới ALB.

Cần kiểm tra:

- ALB nhận request từ CloudFront.
- Host header và forwarded header đúng.
- Cache key không làm lộ dữ liệu.
- Dynamic API không bị cache sai.
- TLS giữa CloudFront và ALB nếu requirement cần.
- Origin SG và WAF behavior.

## 36. Khi không cache qua ALB

Không cache hoặc đặt TTL rất thấp khi response:

- Chứa dữ liệu cá nhân.
- Phụ thuộc Authorization header.
- Phụ thuộc cookie nhưng không đưa vào cache key.
- Thay đổi liên tục.
- Có side effect.
- Là POST/PUT/DELETE.
- Cần freshness gần như tức thời.

Một cache sai có thể gây data leak nghiêm trọng hơn việc không cache.

---

# Phần VIII — Scaling, resilience và performance

## 37. Load balancer không thay thế Auto Scaling

~~~text
Load Balancer:
  phân phối traffic và loại target unhealthy

Auto Scaling:
  tạo, thay thế và điều chỉnh số lượng target
~~~

Cần kết hợp:

- ALB/NLB.
- Target group.
- Auto Scaling Group hoặc ECS Service.
- CloudWatch alarm/metric.
- Health check.
- Multi-AZ.
- Capacity limits.

## 38. Scaling theo nhiều AZ

Thiết kế cơ bản:

~~~text
AZ-a:
  Public subnet → ALB node
  Private subnet → App target

AZ-b:
  Public subnet → ALB node
  Private subnet → App target
~~~

Nếu chỉ có target ở một AZ:

- Mất AZ đó có thể mất toàn bộ backend.
- Load balancer có thể không còn target healthy.
- Cross-zone behavior không thay thế việc triển khai backend nhiều AZ.

## 39. Cross-zone load balancing

Cross-zone load balancing ảnh hưởng cách load balancer phân phối traffic giữa các AZ.

Cần cân nhắc:

- Phân bố target có đều không?
- Cross-AZ data transfer có phát sinh không?
- Backend có cần traffic đi cùng AZ không?
- Khi một AZ có ít target, traffic có bị lệch không?
- Requirement availability và cost là gì?

Không nên trả lời mọi câu hỏi bằng “bật cross-zone”. Phải xét target distribution, network cost và failure behavior.

## 40. Session state và sticky session

Load balancer phù hợp nhất với application stateless.

Nếu application giữ session trong local memory:

~~~text
Request 1 → Instance A tạo session
Request 2 → Instance B không thấy session
~~~

Có thể dùng sticky session để cố định client vào target, nhưng đây thường là giải pháp có trade-off:

- Failover kém linh hoạt.
- Traffic có thể lệch.
- Instance lỗi làm session mất.
- Scale và deployment phức tạp.
- Không giải quyết shared state tổng thể.

Pattern tốt hơn thường là externalize session vào:

- ElastiCache.
- DynamoDB.
- Database.
- Shared session service.

## 41. Connection draining khi scale-in

Khi Auto Scaling terminate instance:

- Load balancer cần stop gửi request mới.
- Request đang chạy cần thời gian hoàn tất.
- Application cần xử lý SIGTERM/shutdown đúng.
- Worker cần hoàn tất hoặc trả job về queue.
- Connection timeout phải nhất quán.

Nếu cấu hình không đúng, scale-in có thể gây:

- 502/503.
- Upload bị ngắt.
- Job bị xử lý dở.
- Message bị duplicate.

## 42. ALB 4xx, 5xx và NLB failure

### ALB 4xx

Có thể do:

- Client request sai.
- Authentication/authorization.
- WAF rule.
- Listener rule.
- Fixed response.
- Invalid path/method.

### ALB 5xx

Có thể do:

- Target không phản hồi.
- Target connection timeout.
- Application crash.
- Backend reset connection.
- Health check/target group sai.
- Dependency downstream lỗi.

Cần phân biệt ALB-generated error với target-generated error bằng access log, metric và application log.

### NLB connection failure

Kiểm tra:

- Listener port/protocol.
- Target port.
- Target health.
- Route.
- SG/NACL.
- TLS certificate nếu NLB terminate TLS.
- Protocol mismatch.
- Source IP/proxy protocol.

---

# Phần IX — Security và Load Balancer

## 43. Security Group pattern

~~~text
Client
  ↓
ALB-SG: allow 443
  ↓
App-SG: allow app port từ ALB-SG
  ↓
DB-SG: allow DB port từ App-SG
~~~

Không nên:

- Mở App-SG cho toàn Internet.
- Mở DB-SG cho toàn VPC nếu chỉ app cần truy cập.
- Gắn cùng một SG rộng cho ALB, app và database.
- Cho phép SSH từ 0.0.0.0/0 khi có SSM/VPN.

## 44. WAF và Load Balancer

WAF phù hợp với HTTP/HTTPS request và có thể bảo vệ ALB hoặc CloudFront theo pattern hỗ trợ.

WAF xử lý:

- SQL injection.
- XSS.
- Rate limiting.
- IP blocking.
- Managed rule groups.
- Geo restriction.

WAF không thay thế:

- Security Group.
- NACL.
- IAM.
- TLS.
- Network routing.
- Database authorization.

## 45. TLS và end-to-end encryption

Các pattern:

~~~text
Pattern A:
Client HTTPS → ALB HTTPS termination → Target HTTP

Pattern B:
Client HTTPS → ALB HTTPS termination → Target HTTPS

Pattern C:
Client HTTPS → NLB TCP pass-through → Target HTTPS
~~~

Chọn dựa trên:

- Nơi quản lý certificate.
- Có cần load balancer đọc HTTP request không.
- Requirement end-to-end encryption.
- Protocol.
- Client source IP.
- Operational overhead.

## 46. Load Balancer logs

Dùng log để debug:

- ALB access logs.
- CloudWatch metrics.
- Target application logs.
- VPC Flow Logs.
- CloudTrail cho thay đổi configuration.
- WAF logs.
- Route 53 health check status.

Đừng kết luận từ một metric duy nhất. Ví dụ target unhealthy có thể do:

- Application lỗi.
- Security Group.
- NACL.
- Route.
- TLS.
- Health check path.
- Database dependency.

---

# Phần X — Lab thực hành

## 47. Lab 1: ALB trước EC2 Multi-AZ

### Mục tiêu

Tạo đường đi Internet → ALB → private EC2.

### Thiết kế

~~~text
VPC
├── Public-A / Public-B
│   └── Internet-facing ALB
└── App-A / App-B
    └── EC2 targets
~~~

### Thực hiện

1. Tạo hoặc dùng VPC Multi-AZ.
2. Tạo ALB internet-facing trong public subnets.
3. Tạo target group HTTP hoặc HTTPS.
4. Tạo EC2 targets ở hai private app subnets.
5. Gán App-SG cho EC2.
6. Gán ALB-SG cho ALB.
7. Cho App-SG allow application port từ ALB-SG.
8. Cấu hình health check.
9. Truy cập DNS của ALB.
10. Kiểm tra target health.
11. Dừng một instance.
12. Xác nhận traffic được chuyển sang target còn healthy.

## 48. Lab 2: Path-based routing

Tạo hai target group:

~~~text
/api/*     → API target group
/static/*  → Static target group
~~~

Các bước:

1. Tạo listener 80 hoặc 443.
2. Tạo rule cho path API.
3. Tạo rule cho path static.
4. Đặt default action.
5. Kiểm tra rule priority.
6. Gửi request tới từng path.
7. Kiểm tra access log.
8. Tạo một path không match để kiểm tra default action.

## 49. Lab 3: Host-based routing

Tạo:

~~~text
api.example.com
admin.example.com
~~~

Các bước:

1. Tạo hai target group.
2. Tạo DNS records hoặc test bằng Host header.
3. Tạo host-based listener rule.
4. Cho từng hostname đi tới target group tương ứng.
5. Kiểm tra certificate nếu dùng HTTPS.
6. Kiểm tra request không có hostname hợp lệ.

## 50. Lab 4: HTTP redirect và TLS

Các bước:

1. Tạo certificate ACM cho domain test.
2. Tạo listener HTTPS 443.
3. Gắn certificate.
4. Tạo listener HTTP 80.
5. Cấu hình redirect HTTP sang HTTPS.
6. Kiểm tra application response.
7. Kiểm tra target protocol là HTTP hoặc HTTPS theo thiết kế.
8. Kiểm tra security group chỉ mở port cần thiết.

## 51. Lab 5: Weighted target group

Tạo hai target group:

~~~text
Version blue  → weight 90
Version green → weight 10
~~~

Thực hiện:

1. Đưa phiên bản mới vào green.
2. Kiểm tra green health.
3. Chia một phần traffic.
4. Theo dõi error rate và latency.
5. Tăng dần weight nếu ổn định.
6. Rollback bằng cách đưa weight về 0.
7. Dùng deregistration delay khi loại target cũ.

## 52. Lab 6: Health check failure

1. Tạo endpoint health check.
2. Xác nhận target healthy.
3. Đổi health check path sai.
4. Quan sát target unhealthy.
5. Kiểm tra metric và log.
6. Sửa path.
7. Xác nhận target quay lại healthy.
8. Dừng instance và theo dõi Auto Scaling replacement.

## 53. Lab 7: NLB TCP

1. Tạo TCP service trên EC2.
2. Tạo NLB internal hoặc internet-facing tùy lab.
3. Tạo TCP listener.
4. Tạo target group TCP.
5. Cấu hình health check.
6. Test kết nối TCP.
7. So sánh với ALB: NLB không route path/host.
8. Xóa resource tính phí sau lab.

## 54. Checklist dọn lab

- [ ] Xóa Load Balancer.
- [ ] Xóa target group.
- [ ] Xóa listener rule.
- [ ] Terminate EC2 test.
- [ ] Xóa NAT Gateway nếu được tạo.
- [ ] Release Elastic IP không dùng.
- [ ] Xóa certificate test nếu không cần.
- [ ] Tắt access log destination hoặc lifecycle log.
- [ ] Kiểm tra CloudWatch alarm và log retention.
- [ ] Kiểm tra billing sau lab.

---

# Phần XI — Bẫy AWS SAA-C03

## 55. Nhầm ALB với NLB

- ALB cho HTTP/HTTPS và Layer 7 routing.
- NLB cho TCP/UDP/TLS và Layer 4.
- ALB không phải lựa chọn cho UDP.
- NLB không route theo path/host.
- GWLB dùng cho firewall/appliance, không phải web API.

## 56. Nghĩ load balancer tự scale backend

Load balancer chỉ phân phối request. Cần Auto Scaling hoặc ECS Service để:

- Tạo target mới.
- Xóa target cũ.
- Thay target unhealthy.
- Điều chỉnh capacity.

## 57. Chỉ đặt ALB ở một AZ

ALB nên có subnet ở nhiều AZ để tăng HA. Backend cũng cần target ở nhiều AZ. Một ALB có nhiều AZ nhưng chỉ có target ở một AZ vẫn có failure risk.

## 58. Mở target cho Internet

Target private phía sau ALB không cần public IP. App-SG nên chỉ allow traffic từ ALB-SG, không phải từ toàn Internet.

## 59. Health check quá sâu

Health check phụ thuộc vào database hoặc third-party có thể loại cả target khi dependency lỗi tạm thời. Chọn mức kiểm tra phù hợp với failure behavior mong muốn.

## 60. Sticky session thay cho stateless design

Sticky session có thể giúp application cũ, nhưng không phải giải pháp lý tưởng cho scale và HA. Externalize session thường bền vững hơn.

## 61. Nhầm Route 53 với load balancing

Route 53 điều phối ở lớp DNS. ALB/NLB điều phối request/connection sau khi client đã tới endpoint. DNS TTL và resolver cache ảnh hưởng Route 53 routing.

## 62. Nhầm CloudFront với Load Balancer

CloudFront là CDN/cache ở edge. ALB/NLB phân phối traffic tới backend. Có thể dùng cả hai:

~~~text
Client → CloudFront → ALB → targets
~~~

## 63. Nhầm health check với monitoring

Load balancer health check quyết định target có nhận traffic hay không. CloudWatch/observability giúp phân tích và cảnh báo. Business health cần endpoint/logic phù hợp.

---

# Phần XII — Câu hỏi tự luyện

## Câu 1

Một web application cần route request theo URL path và host header. Chọn gì?

A. Network Load Balancer  
B. Application Load Balancer  
C. Gateway Load Balancer  
D. NAT Gateway

Đáp án: B.

ALB hoạt động ở Layer 7 và hỗ trợ host/path-based routing.

## Câu 2

Một workload dùng UDP và cần static IP theo Availability Zone. Chọn gì?

A. ALB  
B. NLB  
C. CloudFront  
D. Route 53 Simple routing

Đáp án: B.

## Câu 3

Một công ty cần chèn firewall appliance vào traffic path giữa nhiều VPC. Chọn gì?

A. ALB  
B. NLB  
C. GWLB và endpoint  
D. Route 53

Đáp án: C.

## Câu 4

EC2 target ở private subnet cần nhận traffic từ public ALB. Security Group nào nên allow?

A. App-SG allow từ 0.0.0.0/0  
B. App-SG allow application port từ ALB-SG  
C. DB-SG allow từ Internet  
D. NACL allow mà không cần SG

Đáp án: B.

## Câu 5

ALB target unhealthy nhưng EC2 đang running. Cần kiểm tra gì?

A. Health check path/port, App-SG, NACL và application listener  
B. S3 lifecycle  
C. Route 53 TTL duy nhất  
D. KMS key rotation

Đáp án: A.

## Câu 6

Một tổ chức muốn chuyển 10% traffic sang phiên bản backend mới trong cùng một ALB. Chọn gì?

A. Weighted target groups trên ALB  
B. Tạo public IP cho backend  
C. Xóa target cũ ngay  
D. Dùng NACL

Đáp án: A.

## Câu 7

Một application có session lưu trong memory của từng EC2. Khi scale ngang, request thường mất session. Giải pháp dài hạn phù hợp?

A. Chỉ tăng listener timeout  
B. Externalize session vào ElastiCache hoặc datastore phù hợp  
C. Mở database public  
D. Tạo thêm Internet Gateway

Đáp án: B.

## Câu 8

Một domain cần trỏ tới ALB ở zone apex. Dùng record nào?

A. CNAME bắt buộc  
B. Alias record  
C. MX record  
D. TXT record

Đáp án: B.

## Câu 9

Một API response phụ thuộc Authorization cookie nhưng CloudFront đang cache response theo key không chứa cookie. Rủi ro chính là gì?

A. Cache hit thấp  
B. Người dùng có thể nhận response của user khác  
C. NAT Gateway bị lỗi  
D. NACL trở thành stateful

Đáp án: B.

## Câu 10

Một tổ chức muốn failover DNS giữa ALB ở hai Region. Cần gì ngoài Route 53 failover record?

A. Secondary application và data recovery/replication plan  
B. Chỉ giảm TTL xuống 0  
C. Chỉ tạo thêm listener  
D. Chỉ bật sticky session

Đáp án: A.

## Câu 11

Requirement là TCP custom protocol và low latency. Load balancer nào phù hợp?

A. ALB  
B. NLB  
C. CloudFront  
D. GWLB bắt buộc

Đáp án: B.

## Câu 12

Requirement là cache static assets ở edge và giảm origin request. Chọn gì?

A. NLB  
B. CloudFront  
C. NAT Gateway  
D. Route 53 health check

Đáp án: B.

---

# Phần XIII — Checklist ghi nhớ

## Chọn loại Load Balancer

- [ ] HTTP/HTTPS và host/path routing: ALB.
- [ ] TCP/UDP/TLS, static IP, high throughput: NLB.
- [ ] Firewall/IDS/IPS/network appliance: GWLB.
- [ ] Legacy requirement: xem CLB.
- [ ] Caching ở edge: CloudFront, không phải load balancer.

## ALB

- [ ] Có listener.
- [ ] Có listener rule.
- [ ] Có default action.
- [ ] Có target group.
- [ ] Có health check.
- [ ] Có Security Group.
- [ ] Có subnet ở nhiều AZ.
- [ ] Có TLS certificate nếu dùng HTTPS.
- [ ] Có target ở nhiều AZ.
- [ ] Có deregistration delay phù hợp.

## NLB

- [ ] Đúng protocol TCP/UDP/TLS.
- [ ] Đúng listener port.
- [ ] Đúng target port.
- [ ] Kiểm tra TLS termination hoặc pass-through.
- [ ] Kiểm tra source IP/proxy protocol.
- [ ] Kiểm tra static IP requirement.
- [ ] Kiểm tra target health.

## Security

- [ ] Client → ALB-SG.
- [ ] ALB → App-SG.
- [ ] App → DB-SG.
- [ ] Không mở target cho Internet nếu không cần.
- [ ] WAF dùng cho HTTP/HTTPS.
- [ ] TLS termination đặt ở nơi phù hợp.
- [ ] Cache key không làm lộ dữ liệu.

## Troubleshooting

- [ ] DNS resolve đúng.
- [ ] Load balancer scheme đúng.
- [ ] Subnet route đúng.
- [ ] Listener đúng port/protocol.
- [ ] Rule priority đúng.
- [ ] Target group đúng.
- [ ] Health check đúng.
- [ ] Security Group đúng.
- [ ] NACL hai chiều đúng.
- [ ] Application thực sự listen.
- [ ] Log và metric đã được kiểm tra.

---

## Tóm tắt cuối

- ALB là Layer 7 cho HTTP/HTTPS và routing theo nội dung request.
- NLB là Layer 4 cho TCP/UDP/TLS, low latency, high throughput và static IP.
- GWLB dùng để phân phối traffic qua firewall hoặc network appliance.
- Listener nhận traffic; listener rule quyết định action.
- Target group chứa target và định nghĩa health check.
- Health check quyết định target có nhận traffic hay không.
- Auto Scaling tạo và thay target; load balancer phân phối traffic.
- Security Group bảo vệ resource; NACL bảo vệ subnet.
- Route 53 điều phối ở lớp DNS; ALB/NLB điều phối traffic sau DNS.
- CloudFront cache ở edge; load balancer phân phối tới backend.
- Application stateless giúp scale và failover tốt hơn sticky session.
- Load balancer nhiều AZ và target nhiều AZ là nền tảng của high availability.

## Tài liệu tham khảo chính thức

- [Elastic Load Balancing](https://docs.aws.amazon.com/elasticloadbalancing/latest/userguide/what-is-load-balancing.html)
- [Application Load Balancer](https://docs.aws.amazon.com/elasticloadbalancing/latest/application/introduction.html)
- [Network Load Balancer](https://docs.aws.amazon.com/elasticloadbalancing/latest/network/introduction.html)
- [Gateway Load Balancer](https://docs.aws.amazon.com/elasticloadbalancing/latest/gateway/introduction.html)
- [Listeners and rules for ALB](https://docs.aws.amazon.com/elasticloadbalancing/latest/application/load-balancer-listeners.html)
- [Target groups](https://docs.aws.amazon.com/elasticloadbalancing/latest/application/load-balancer-target-groups.html)
- [ALB health checks](https://docs.aws.amazon.com/elasticloadbalancing/latest/application/target-group-health-checks.html)
- [Route 53 routing policies](https://docs.aws.amazon.com/Route53/latest/DeveloperGuide/routing-policy.html)
- [Amazon CloudFront Developer Guide](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/Introduction.html)
- [AWS Global Accelerator](https://docs.aws.amazon.com/global-accelerator/latest/dg/what-is-global-accelerator.html)


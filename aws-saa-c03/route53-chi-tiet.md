# Route 53 chuyên sâu — AWS SAA-C03

> Route 53 là dịch vụ DNS managed của AWS. Tài liệu này giải thích cách Route 53 phân giải domain, quản lý hosted zone/record, chọn routing policy, thực hiện health check và kết hợp với ALB, CloudFront, S3, API Gateway, VPC và hybrid DNS.

## 1. Route 53 là gì?

Route 53 cung cấp:

- Domain registration.
- Public hosted zone.
- Private hosted zone.
- DNS record.
- Health check.
- DNS routing policy.
- Private DNS trong VPC.
- Route 53 Resolver cho hybrid network.
- Alias tới nhiều AWS resource.

Route 53 không phải load balancer Layer 4 hoặc Layer 7. Route 53 trả lời DNS query; sau khi client nhận DNS answer, client mới tạo network connection tới endpoint.

~~~text
Client
  ↓ DNS query
Route 53
  ↓ DNS answer
ALB / CloudFront / S3 / API Gateway / IP
  ↓
Client tạo connection tới endpoint
~~~

Phân biệt:

| Thành phần | Nhiệm vụ |
|---|---|
| Route 53 | Chọn DNS answer/endpoint |
| ALB | Phân phối HTTP/HTTPS request tới target |
| NLB | Phân phối TCP/UDP/TLS connection |
| CloudFront | Cache content tại edge |
| Auto Scaling | Tạo, thay thế và scale backend |
| Route table | Quyết định network next hop |

## 2. DNS lookup hoạt động thế nào?

~~~text
Client application
  ↓
Local DNS cache
  ↓
Recursive resolver
  ↓
Authoritative DNS server
  ↓
Route 53 hosted zone
  ↓
DNS record
~~~

Client thường hỏi recursive resolver. Resolver có thể trả answer từ cache nếu TTL chưa hết. Nếu cache hết hạn, resolver query authoritative DNS server.

Vì vậy, thay đổi record không phải lúc nào cũng có hiệu lực ngay. Thời gian chuyển đổi phụ thuộc:

- TTL.
- Cache của recursive resolver.
- Cache của client/OS/browser.
- Connection hiện tại.
- DNS propagation.
- Route 53 health state.

DNS chỉ trả lời tên hoặc endpoint; DNS không truyền application payload.

---

# Phần I — Hosted zone

## 3. Hosted zone là gì?

Hosted zone là vùng chứa các DNS record cho một domain hoặc namespace.

Có hai loại:

- Public hosted zone.
- Private hosted zone.

~~~text
Hosted zone
├── Domain/namespace
├── DNS records
├── Routing policy
├── TTL
└── Health evaluation tùy cấu hình
~~~

## 4. Public hosted zone

Public hosted zone trả lời DNS query từ Internet.

Ví dụ:

~~~text
example.com
├── www.example.com
├── api.example.com
├── static.example.com
└── mail.example.com
~~~

Để public hosted zone hoạt động:

1. Tạo hosted zone.
2. Tạo record.
3. Lấy Route 53 name servers.
4. Cập nhật NS delegation tại domain registrar.
5. Chờ cache/propagation.
6. Kiểm tra bằng DNS query tool.

Nếu tạo hosted zone nhưng chưa delegate name servers tại registrar, Internet có thể vẫn query authoritative DNS cũ.

## 5. Private hosted zone

Private hosted zone chỉ trả lời query trong VPC được associate hoặc network có Resolver path phù hợp.

Ví dụ:

~~~text
internal.example.com

api.internal.example.com
db.internal.example.com
cache.internal.example.com
~~~

Use case:

- Internal ALB.
- Private API.
- Database alias.
- Service discovery đơn giản.
- Environment-specific endpoint.
- Hybrid DNS.

Điều kiện:

- Associate với đúng VPC.
- VPC DNS support bật.
- VPC DNS hostnames bật khi cần.
- Client dùng resolver phù hợp.
- Record tồn tại đúng.
- Không bị conflicting hosted zone hoặc resolver rule.

Private hosted zone không publicize record ra Internet.

## 6. Split-horizon DNS

Có thể tạo public và private hosted zone có cùng domain:

~~~text
Public zone:
example.com
  api.example.com → public ALB

Private zone:
example.com
  api.example.com → internal ALB
~~~

Khi đó:

- Client Internet nhận public answer.
- Client trong VPC nhận private answer nếu private zone được associate.
- Cùng hostname có thể trỏ tới endpoint khác nhau tùy nơi query.

Đây là split-horizon DNS.

Khi debug, cần xác định:

- Query đến từ Internet hay VPC.
- VPC đã associate private zone chưa.
- Resolver chọn zone nào.
- Có zone chồng lấn không.
- Record public/private có khác nhau không.

## 7. Hosted zone không phải domain registrar

| Thành phần | Vai trò |
|---|---|
| Domain registrar | Đăng ký và quản lý quyền sở hữu domain |
| Hosted zone | Lưu DNS record |
| Authoritative DNS | Trả lời có thẩm quyền |
| Recursive resolver | Nhận query từ client và tìm answer |

Có thể đăng ký domain ở một registrar khác nhưng dùng Route 53 làm authoritative DNS bằng cách cập nhật NS delegation.

---

# Phần II — DNS records

## 8. Record gồm những gì?

Một record thường có:

- Record name.
- Record type.
- Value hoặc target.
- TTL.
- Routing policy.
- Health check hoặc evaluate target health tùy policy.

## 9. A record

A record ánh xạ hostname tới IPv4.

~~~text
api.example.com
  A
  203.0.113.10
~~~

Dùng khi trỏ tới IPv4 address tĩnh hoặc endpoint không hỗ trợ Alias.

Không nên hard-code IP của ALB, NLB hoặc CloudFront nếu AWS resource hỗ trợ Alias.

## 10. AAAA record

AAAA record ánh xạ hostname tới IPv6.

~~~text
api.example.com
  AAAA
  2001:db8::10
~~~

Khi dùng IPv6, kiểm tra riêng:

- Security Group IPv6 rule.
- NACL IPv6 rule.
- IPv6 route.
- Egress-only Internet Gateway.
- Application/backend support.
- Client có ưu tiên IPv6 không.

## 11. CNAME record

CNAME ánh xạ một hostname tới hostname khác.

~~~text
www.example.com
  CNAME
  app.example.net
~~~

Đặc điểm:

- Dùng cho subdomain.
- Không dùng ở zone apex.
- Resolver tiếp tục query hostname đích.
- CNAME không phải HTTP redirect.
- CNAME không tự làm endpoint private hoặc public.

## 12. Alias record

Alias là cơ chế Route 53 để trỏ tới AWS resource hoặc endpoint được hỗ trợ.

Use case phổ biến:

- ALB.
- NLB.
- CloudFront.
- API Gateway.
- S3 website endpoint.
- Global Accelerator.

~~~text
example.com
  Alias → CloudFront

api.example.com
  Alias → ALB
~~~

Ưu điểm:

- Dùng được ở zone apex.
- Không cần biết IP thật của AWS resource.
- AWS quản lý thay đổi endpoint.
- Có thể kết hợp Evaluate Target Health theo resource/policy hỗ trợ.

## 13. Alias và CNAME

| Tiêu chí | Alias | CNAME |
|---|---|---|
| Loại | Route 53-specific | DNS standard |
| Zone apex | Có | Không |
| Trỏ AWS resource | Có | Không theo cùng cơ chế |
| HTTP redirect | Không | Không |
| Health evaluation | Có thể hỗ trợ | Không giống Alias |
| Dùng cho subdomain | Có thể | Có |

Nếu muốn root domain trỏ tới ALB hoặc CloudFront, thường dùng Alias. Nếu muốn subdomain trỏ tới hostname khác, CNAME là một lựa chọn.

## 14. MX, TXT, NS và SOA

- MX: mail exchange.
- TXT: domain verification, SPF, DKIM, DMARC.
- NS: name servers và delegation.
- SOA: authority metadata của zone.

Ở SAA-C03, cần hiểu mục đích tổng quan, không cần đi sâu thành DNS administrator.

---

# Phần III — TTL và caching

## 15. TTL là gì?

TTL là thời gian resolver cache một DNS answer trước khi query lại authoritative DNS.

~~~text
TTL thấp
  → thay đổi nhanh hơn
  → query nhiều hơn

TTL cao
  → cache lâu hơn
  → thay đổi chậm hơn
~~~

TTL ảnh hưởng:

- DNS failover.
- Blue/green migration.
- IP/endpoint change.
- Cost query.
- Thời gian traffic chuyển sang endpoint mới.

TTL không đảm bảo mọi client chuyển endpoint ngay sau khi record thay đổi.

## 16. Chọn TTL

### TTL thấp

Phù hợp:

- Đang migration.
- Chuẩn bị failover.
- Canary cần điều chỉnh nhanh.
- Endpoint thay đổi thường xuyên.

Nhược điểm:

- Nhiều DNS query hơn.
- Có thể tăng cost.
- Không triệt tiêu hoàn toàn cache cũ.

### TTL cao

Phù hợp:

- Endpoint ổn định.
- Domain ít thay đổi.
- Muốn giảm query.

Nhược điểm:

- Thay đổi chậm.
- DNS failover phản ứng chậm hơn.

### Migration pattern

~~~text
1. Giảm TTL trước migration
2. Chờ TTL cũ giảm ở resolver
3. Thay record
4. Theo dõi traffic/error
5. Tăng TTL khi ổn định
~~~

---

# Phần IV — Routing policies

## 17. Simple routing

Simple routing dùng cho endpoint đơn giản hoặc một tập value không cần logic routing phức tạp.

Phù hợp:

- Một ALB.
- Một CloudFront distribution.
- Một static endpoint.
- Một website đơn giản.

Simple routing không phải policy để primary/secondary failover.

## 18. Weighted routing

Weighted routing chia traffic theo weight giữa nhiều record cùng name/type.

~~~text
api.example.com

Blue:
  weight 90

Green:
  weight 10
~~~

Use case:

- Canary.
- Blue/green.
- Migration từng phần.
- A/B test đơn giản.
- Thử Region mới.

Weight là tỷ lệ tương đối:

~~~text
Weight A = 1
Weight B = 9

Traffic kỳ vọng:
A gần 10%
B gần 90%
~~~

Lưu ý:

- DNS cache làm traffic thực tế khác tỷ lệ lý thuyết.
- Weighted routing chọn DNS answer, không điều khiển từng HTTP request.
- Client có thể giữ endpoint cũ tới khi TTL hết.
- Health check có thể khiến record không healthy bị loại.

## 19. Latency-based routing

Latency-based routing trả endpoint được AWS đánh giá có network latency thấp hơn cho client.

Phù hợp:

- Application chạy nhiều Region.
- Muốn user đi tới Region có latency tốt hơn.
- Global application.
- Active-active multi-Region.

Không dùng để:

- Route theo country.
- Chia traffic theo phần trăm.
- Chọn primary/secondary cố định.
- Đo chính xác p99 application latency.

~~~text
User khu vực A
  → Region có network latency tốt hơn cho A

User khu vực B
  → Region có network latency tốt hơn cho B
~~~

## 20. Failover routing

Failover routing có:

- Primary record.
- Secondary record.

~~~text
Primary healthy
  → trả Primary

Primary unhealthy
  → trả Secondary
~~~

Phù hợp:

- Active-passive DR.
- Primary Region và backup Region.
- Primary ALB và secondary ALB.
- Primary website và static backup.

Route 53 failover không tự:

- Replicate database.
- Copy object.
- Deploy application.
- Copy secret.
- Tạo network path.
- Chuyển write traffic.

Các thành phần đó phải được thiết kế riêng.

## 21. Geolocation routing

Geolocation routing chọn endpoint dựa trên vị trí địa lý của user hoặc resolver.

Có thể route theo:

- Country.
- Continent.
- Default location.

Phù hợp:

- Data residency.
- Regulatory requirement.
- Nội dung theo quốc gia.
- Ngôn ngữ.
- Region-specific endpoint.

Ví dụ:

~~~text
Vietnam
  → Asia endpoint

Europe
  → EU endpoint

Default
  → fallback endpoint
~~~

Geolocation không đồng nghĩa endpoint có latency thấp nhất.

## 22. Geoproximity routing

Geoproximity routing chọn endpoint theo khoảng cách địa lý và có thể dùng bias để mở rộng/thu hẹp vùng traffic.

Phù hợp:

- Điều chỉnh vùng traffic.
- Mở rộng vùng của Region có capacity.
- Thu hẹp vùng của Region bảo trì.
- Điều phối theo khoảng cách linh hoạt.

Phân biệt:

- Geolocation: country/continent.
- Geoproximity: distance/bias.
- Latency: network latency.

## 23. Multivalue answer routing

Multivalue answer trả về nhiều healthy record cho cùng một hostname.

Phù hợp:

- Nhiều endpoint độc lập.
- Availability đơn giản.
- Client có thể thử một trong nhiều IP.
- Không cần application-aware load balancer.

Multivalue không thay thế ALB/NLB vì:

- Không quản lý connection giống load balancer.
- Không route theo HTTP path/host.
- Client/resolver quyết định sử dụng answer nào.
- Không cung cấp đầy đủ target management.

## 24. IP-based routing

IP-based routing match client IP range với endpoint tương ứng.

Dùng khi:

- Một số network source cần endpoint riêng.
- Enterprise routing theo CIDR.
- Private network hoặc partner mapping đặc biệt.

Không nhầm với geolocation:

- IP-based match CIDR.
- Geolocation suy ra vị trí.

## 25. Bảng chọn routing policy

| Requirement | Policy |
|---|---|
| Một endpoint | Simple |
| Chia traffic theo tỷ lệ | Weighted |
| Region có network latency tốt hơn | Latency-based |
| Primary/backup | Failover |
| Theo quốc gia/continent | Geolocation |
| Theo khoảng cách và bias | Geoproximity |
| Nhiều healthy answer | Multivalue |
| Theo client IP range | IP-based |

---

# Phần V — Health check và DNS failover

## 26. Route 53 health check

Health check có thể kiểm tra endpoint bằng:

- HTTP.
- HTTPS.
- TCP.
- CloudWatch alarm trong pattern hỗ trợ.
- Calculated health check.

Health check có thể kiểm tra:

- Endpoint reachable.
- Port mở.
- Response status.
- Response string tùy cấu hình.
- CloudWatch metric/alarm theo pattern hỗ trợ.

## 27. Thiết kế health check tốt

Health endpoint nên:

- Nhẹ.
- Nhanh.
- Ổn định.
- Có status code rõ.
- Phản ánh khả năng phục vụ phù hợp.
- Không phụ thuộc quá sâu vào database/third-party nếu không cần.

Health check quá nông:

~~~text
Process còn sống
  → health check pass
  → business request vẫn fail
~~~

Health check quá sâu:

~~~text
Database tạm chậm
  → health check fail
  → Route 53 loại endpoint
  → traffic dồn sang endpoint khác
  → có thể cascade overload
~~~

Cần chọn mức health signal phù hợp với failure cần xử lý.

## 28. Health check private endpoint

Route 53 health checker bên ngoài không thể tùy ý kiểm tra private IP trong VPC.

Với private service, có thể dùng:

- CloudWatch alarm.
- Calculated health check.
- Internal monitoring.
- Evaluate Target Health cho AWS resource hỗ trợ.
- Public health endpoint được bảo vệ nếu thật sự cần.

Không expose private service ra Internet chỉ để health check nếu có phương án private phù hợp.

## 29. Calculated health check

Calculated health check kết hợp nhiều health check con.

~~~text
Health check A: Region 1
Health check B: Region 2
Health check C: Region 3

Calculated health:
  healthy nếu ít nhất 2/3 check healthy
~~~

Use case:

- Giảm ảnh hưởng của một health check false positive.
- Kết hợp nhiều endpoint.
- Tạo health signal tổng hợp.
- Chỉ failover khi nhiều thành phần thực sự lỗi.

## 30. Evaluate Target Health

Alias record tới AWS resource có thể sử dụng Evaluate Target Health theo behavior hỗ trợ. Route 53 sẽ xem xét trạng thái target/resource đích khi quyết định DNS answer.

Cần kiểm tra:

- Resource có hỗ trợ không.
- Dùng health check riêng hay target health.
- Nested alias có phù hợp không.
- Target health có phản ánh business health không.
- Có thể tạo false failover không.

## 31. DNS failover và TTL

Khi primary fail:

~~~text
Route 53 đổi answer
  ↓
Query mới có thể nhận secondary
  ↓
Client kết nối secondary
~~~

Client đang giữ answer cũ có thể tiếp tục dùng primary tới khi:

- TTL hết.
- Resolver refresh.
- Connection hiện tại kết thúc.
- Application thực hiện query mới.

DNS failover không phải failover tức thời.

---

# Phần VI — Private DNS và hybrid DNS

## 32. Route 53 Private Hosted Zone

Private hosted zone dùng cho internal name resolution.

~~~text
internal.example.com

api.internal.example.com
db.internal.example.com
cache.internal.example.com
~~~

Cần:

- Associate với VPC.
- Bật VPC DNS support.
- Bật VPC DNS hostnames khi cần.
- Dùng resolver đúng.
- Kiểm tra conflicting zone.
- Kiểm tra record.

## 33. Route 53 Resolver inbound endpoint

Inbound endpoint cho phép DNS server từ on-premises query private domain trong AWS.

~~~text
On-premises DNS
  ↓
VPN hoặc Direct Connect
  ↓
Inbound Resolver Endpoint
  ↓
Private Hosted Zone
~~~

Cần kiểm tra:

- Endpoint ENI ở subnet đúng.
- Security Group cho phép DNS.
- NACL cho phép UDP/TCP 53.
- Route từ on-premises tới VPC.
- Private zone association.
- Redundancy qua nhiều AZ.

## 34. Route 53 Resolver outbound endpoint

Outbound endpoint cho phép workload trong VPC query DNS server on-premises.

~~~text
AWS workload
  ↓
Outbound Resolver Endpoint
  ↓
VPN hoặc Direct Connect
  ↓
On-premises DNS
~~~

Resolver rule quyết định domain nào forward tới on-premises.

Ví dụ:

~~~text
corp.example.com
  → On-premises DNS

internal.example.com
  → Route 53 Private Hosted Zone
~~~

## 35. Hybrid DNS checklist

- [ ] VPC DNS support bật.
- [ ] VPC DNS hostnames bật khi cần.
- [ ] Resolver endpoint ở subnet đúng.
- [ ] Endpoint Security Group cho phép DNS.
- [ ] NACL cho phép UDP/TCP 53.
- [ ] VPN hoặc Direct Connect reachable.
- [ ] Route table có route tới network đích.
- [ ] Forwarding rule đúng.
- [ ] Không có conflicting private hosted zone.
- [ ] Có redundancy cho Resolver endpoint.
- [ ] Có DNS query logging nếu cần điều tra.

---

# Phần VII — Route 53 kết hợp AWS services

## 36. Route 53 và ALB/NLB

~~~text
api.example.com
  ↓ Alias
ALB hoặc NLB
  ↓
Target group
  ↓
Backend
~~~

Route 53 xử lý:

- Domain resolution.
- Weighted endpoint selection.
- Latency routing.
- DNS failover.
- Multi-Region endpoint selection.

ALB/NLB xử lý:

- Listener.
- Target group.
- Health check target.
- Request/connection distribution.
- TLS/protocol behavior.

Không trỏ domain trực tiếp tới IP của ALB/NLB vì IP có thể thay đổi.

## 37. Route 53 và CloudFront

~~~text
www.example.com
  ↓ Alias
CloudFront distribution
  ↓
S3 / ALB / custom origin
~~~

Route 53 xử lý DNS. CloudFront xử lý:

- Edge caching.
- Origin request.
- Signed URL/cookie.
- WAF integration.
- Viewer/origin protocol.

## 38. Route 53 và S3

Có thể dùng Alias tới S3 website endpoint trong pattern phù hợp.

Cần phân biệt:

- S3 website endpoint là website hosting.
- S3 REST endpoint không tự là website endpoint.
- CloudFront + private S3 origin thường phù hợp khi cần HTTPS/CDN/OAC.
- Route 53 không biến bucket private thành public website.
- Bucket policy và Block Public Access vẫn quan trọng.

## 39. Route 53 và API Gateway

~~~text
api.example.com
  ↓ Alias/custom domain
API Gateway
  ↓
API stage/integration
~~~

Cần kiểm tra:

- ACM certificate.
- Region/certificate requirement.
- Base path mapping.
- DNS record.
- API authorization.
- Throttling.
- WAF nếu cần.

## 40. Route 53 và Global Accelerator

~~~text
app.example.com
  ↓ Alias
Global Accelerator static anycast IP
  ↓
ALB / NLB / supported endpoint
~~~

Global Accelerator phù hợp khi:

- Cần static anycast IP.
- Cần TCP/UDP hoặc application không cache.
- Muốn traffic đi qua AWS global network.
- Cần endpoint health/failover.
- Không cần CDN cache như CloudFront.

---

# Phần VIII — Architecture pattern

## 41. Single-Region public application

~~~text
User
  ↓
Route 53 Alias
  ↓
CloudFront
  ↓
WAF
  ↓
Public ALB
  ↓
Private EC2/ECS
  ↓
RDS Multi-AZ
~~~

Phù hợp khi:

- Một Region đáp ứng latency.
- Cần CDN.
- Database chính ở một Region.
- HA cần Multi-AZ.

## 42. Multi-Region latency routing

~~~text
                 ┌── ALB Region A
User → Route 53 ┤
                 └── ALB Region B
~~~

Cần bổ sung:

- Data replication.
- Conflict strategy.
- Health check.
- Deployment ở cả Region.
- Regional dependency.
- Global observability.
- Failover plan.

## 43. Active-passive DR

~~~text
Route 53 Failover
├── Primary ALB
└── Secondary ALB
~~~

Secondary có thể là:

- Backup and restore.
- Pilot light.
- Warm standby.
- Full active environment nhưng không nhận traffic chính.

Chọn theo RTO/RPO. Route 53 chỉ đổi DNS answer.

## 44. Weighted canary

~~~text
Route 53 Weighted
├── Endpoint A: 95
└── Endpoint B: 5
~~~

Nếu hai version nằm trong cùng ALB, ALB weighted target group có thể phù hợp hơn vì request đã đến ALB.

## 45. Split-horizon DNS

~~~text
Internet:
  app.example.com → CloudFront hoặc public ALB

VPC:
  app.example.com → internal ALB
~~~

Dùng khi cùng hostname cần endpoint khác nhau tùy nơi query.

---

# Phần IX — Troubleshooting

## 46. Domain không resolve

1. Domain đã đăng ký chưa?
2. Registrar đã delegate tới Route 53 name servers chưa?
3. Public hosted zone đúng domain chưa?
4. Record name/type/value đúng chưa?
5. Có nhiều hosted zone cùng domain không?
6. NS record có đúng không?
7. TTL/cache còn giữ answer cũ không?
8. Query từ Internet hay VPC?
9. DNSSEC có lỗi nếu đang dùng không?

## 47. Alias tới ALB không hoạt động

1. ALB active chưa?
2. Alias target đúng resource chưa?
3. ALB internal hay internet-facing?
4. Client có network path tới ALB không?
5. Listener đúng port/protocol không?
6. Security Group/NACL đúng không?
7. Target group có healthy target không?
8. Public/private hosted zone có bị chọn nhầm không?
9. DNS answer còn bị cache không?

## 48. Failover không chuyển secondary

1. Primary health check có thực sự fail không?
2. Health check kiểm tra đúng endpoint chưa?
3. Record primary/secondary có role đúng không?
4. Secondary có reachable không?
5. TTL/cache còn giữ primary không?
6. Evaluate Target Health có cấu hình đúng không?
7. Secondary application/data đã sẵn sàng chưa?
8. Query đang tới đúng hosted zone không?
9. Health check có false positive/negative không?

## 49. Private hostname không resolve trong VPC

1. Private zone đã associate VPC chưa?
2. VPC DNS support/hostnames đã bật chưa?
3. Record đúng tên chưa?
4. Client dùng AmazonProvidedDNS hoặc resolver đúng chưa?
5. Có conflicting zone không?
6. Resolver rule có forward nhầm không?
7. SG/NACL tới DNS có allow không?
8. Query đang đi tới AWS resolver hay on-premises DNS?

## 50. Weighted routing không chia đúng tỷ lệ

Nguyên nhân có thể:

- DNS resolver cache.
- TTL khác nhau.
- Client cache.
- Query volume không đều.
- Một record unhealthy.
- Weight là tỷ lệ tương đối, không phải guarantee từng request.
- Connection giữ lâu.
- CloudFront hoặc proxy cache DNS/response.

Weighted DNS không phải load balancer request-level.

---

# Phần X — Bẫy AWS SAA-C03

- Route 53 không phải load balancer request-level.
- Alias không phải HTTP redirect.
- CNAME không dùng ở zone apex.
- TTL thấp không bảo đảm failover tức thì.
- Latency routing không phải country routing.
- Geolocation không phải latency routing.
- Route 53 failover không replicate database/application.
- Public health check không tự kiểm tra private endpoint.
- Weighted DNS không chính xác từng request.
- Public và private hosted zone có thể cùng tên.
- Route 53 không thay thế Security Group hoặc route table.
- DNS answer đúng nhưng network path vẫn có thể sai.
- CloudFront cache không giống Route 53 DNS cache.
- Global Accelerator không phải CDN cache.
- Multivalue answer không thay thế ALB/NLB.
- Resolver endpoint cần SG, route, NACL và port DNS đúng.

---

# Phần XI — Lab thực hành

## 51. Lab 1: Public hosted zone và Alias tới ALB

1. Tạo domain test hoặc subdomain.
2. Tạo public hosted zone.
3. Cập nhật NS delegation tại registrar.
4. Tạo ALB.
5. Tạo Alias A record tới ALB.
6. Truy cập domain.
7. Kiểm tra DNS answer.
8. Kiểm tra ALB access log.
9. Thay đổi target và theo dõi response.
10. Xóa record/resource sau lab.

## 52. Lab 2: Weighted routing

Tạo hai endpoint:

~~~text
blue.example.com
green.example.com
~~~

Tạo weighted records cùng hostname:

~~~text
app.example.com → blue, weight 90
app.example.com → green, weight 10
~~~

Thực hiện:

1. Kiểm tra hai endpoint.
2. Tạo weighted records.
3. Đặt TTL thấp cho lab.
4. Gửi nhiều DNS query.
5. Quan sát tỷ lệ answer.
6. Thay weight.
7. Chờ cache cũ giảm.
8. Ghi nhận tỷ lệ không bảo đảm từng request.

## 53. Lab 3: Failover routing

1. Tạo primary endpoint.
2. Tạo secondary endpoint.
3. Tạo health check cho primary.
4. Tạo failover record primary.
5. Tạo failover record secondary.
6. Kiểm tra primary answer.
7. Làm primary health check fail.
8. Query lại sau thời gian phù hợp.
9. Kiểm tra secondary.
10. Restore primary.
11. Ghi lại TTL và thời gian chuyển đổi.

## 54. Lab 4: Private hosted zone

1. Tạo private hosted zone internal.example.com.
2. Associate với VPC test.
3. Tạo record internal ALB hoặc private IP.
4. Query từ EC2 trong VPC.
5. Query từ Internet và xác nhận không nhận private answer.
6. Associate thêm VPC nếu cần.
7. Kiểm tra DNS support/hostnames.
8. Xóa private zone sau lab.

## 55. Lab 5: Split-horizon DNS

1. Tạo public zone example.com.
2. Tạo private zone example.com.
3. Public record trỏ tới public ALB.
4. Private record trỏ tới internal ALB.
5. Query từ Internet.
6. Query từ EC2 trong VPC.
7. So sánh hai answer.
8. Kiểm tra hosted zone association.

## 56. Lab 6: Route 53 Resolver hybrid

1. Tạo inbound Resolver endpoint.
2. Tạo outbound Resolver endpoint.
3. Gắn Security Group cho endpoint.
4. Tạo forwarding rule.
5. Thêm route qua VPN/Direct Connect.
6. Query AWS private zone từ on-premises.
7. Query on-prem domain từ AWS.
8. Kiểm tra UDP/TCP port 53.
9. Kiểm tra redundancy endpoint.
10. Xóa endpoint sau lab nếu không dùng.

## 57. Dọn dẹp lab

- [ ] Xóa health check.
- [ ] Xóa record test.
- [ ] Xóa hosted zone không dùng.
- [ ] Cập nhật lại NS delegation nếu dùng domain thật.
- [ ] Xóa Resolver endpoint nếu không cần.
- [ ] Xóa ALB/CloudFront test.
- [ ] Kiểm tra log và billing.

---

# Phần XII — Câu hỏi tự luyện

## Câu 1

Một công ty cần trỏ root domain tới CloudFront distribution. Record nào phù hợp?

A. CNAME ở zone apex  
B. Alias record  
C. MX record  
D. TXT record

Đáp án: B.

## Câu 2

Công ty muốn chia traffic 90/10 giữa blue và green endpoint. Chính sách nào phù hợp?

A. Weighted routing  
B. Geolocation routing  
C. Simple routing  
D. Mail routing

Đáp án: A.

## Câu 3

Một application có ALB ở ba Region và muốn user dùng Region có network latency thấp hơn. Chọn gì?

A. Geolocation routing  
B. Latency-based routing  
C. Failover routing  
D. Multivalue routing

Đáp án: B.

## Câu 4

Một công ty cần route user ở Việt Nam tới endpoint châu Á và user châu Âu tới endpoint EU để đáp ứng data residency. Chọn gì?

A. Latency-based routing  
B. Geolocation routing  
C. Weighted routing  
D. Simple routing

Đáp án: B.

## Câu 5

Primary endpoint cần chuyển sang backup khi unhealthy. Chọn gì?

A. Failover routing  
B. Simple routing  
C. Geoproximity routing  
D. CNAME

Đáp án: A.

## Câu 6

Một private hostname chỉ được phân giải bên trong VPC. Dùng gì?

A. Public hosted zone  
B. Private hosted zone  
C. Public CNAME duy nhất  
D. Internet Gateway

Đáp án: B.

## Câu 7

Route 53 failover đã trả secondary nhưng user vẫn kết nối primary trong một khoảng thời gian. Nguyên nhân có thể là gì?

A. DNS TTL và resolver cache  
B. NACL cache HTTP  
C. ALB không có listener  
D. KMS key chưa rotate

Đáp án: A.

## Câu 8

Một application trong AWS cần resolve domain do DNS server on-premises quản lý. Dùng gì?

A. Route 53 outbound Resolver endpoint  
B. Public hosted zone bắt buộc  
C. S3 Gateway Endpoint  
D. CloudFront

Đáp án: A.

## Câu 9

Một domain cần HTTP redirect sang HTTPS. Dùng CNAME có làm được không?

A. Có, CNAME là HTTP redirect  
B. Không, CNAME chỉ là DNS alias; cần ALB/CloudFront/application redirect  
C. Có, nếu TTL thấp  
D. Có, nếu dùng MX

Đáp án: B.

## Câu 10

Một API response chứa dữ liệu riêng theo user nhưng CloudFront cache key không chứa cookie/authorization. Rủi ro chính là gì?

A. User có thể nhận response của user khác  
B. DNS không resolve  
C. Weighted routing không hoạt động  
D. Route 53 không có health check

Đáp án: A.

---

# Phần XIII — Checklist ghi nhớ

## DNS và hosted zone

- [ ] Phân biệt registrar, hosted zone và resolver.
- [ ] Phân biệt public và private hosted zone.
- [ ] Hiểu NS delegation.
- [ ] Biết public/private split-horizon.
- [ ] Biết VPC DNS attributes.
- [ ] Biết Route 53 Resolver inbound/outbound.

## Record

- [ ] A cho IPv4.
- [ ] AAAA cho IPv6.
- [ ] CNAME trỏ hostname, không dùng ở zone apex.
- [ ] Alias trỏ AWS resource, dùng được ở zone apex.
- [ ] TXT cho verification/policy.
- [ ] MX cho mail.
- [ ] Alias không phải HTTP redirect.

## Routing policy

- [ ] Simple: endpoint đơn.
- [ ] Weighted: chia traffic theo tỷ lệ.
- [ ] Latency: endpoint có network latency tốt hơn.
- [ ] Failover: primary/secondary.
- [ ] Geolocation: country/continent.
- [ ] Geoproximity: distance/bias.
- [ ] Multivalue: nhiều healthy answer.
- [ ] IP-based: match client IP range.

## Health và failover

- [ ] Health check phù hợp endpoint.
- [ ] Private endpoint có health signal phù hợp.
- [ ] TTL ảnh hưởng failover.
- [ ] DNS failover không replicate data.
- [ ] Secondary thực sự sẵn sàng.
- [ ] Evaluate Target Health dùng đúng pattern.
- [ ] Test failover định kỳ.

## Kết hợp service

- [ ] Route 53 → ALB/NLB bằng Alias.
- [ ] Route 53 → CloudFront bằng Alias.
- [ ] CloudFront có thể đứng trước ALB.
- [ ] ALB weighted target group khác Route 53 weighted routing.
- [ ] Global Accelerator phù hợp static anycast IP/TCP/UDP.
- [ ] CloudFront phù hợp CDN/cache.

---

## Tóm tắt cuối

1. Route 53 là managed DNS, không phải load balancer request-level.
2. Public hosted zone phục vụ Internet; private hosted zone phục vụ VPC.
3. Alias phù hợp AWS resource và zone apex; CNAME không dùng ở zone apex.
4. TTL quyết định thời gian resolver cache DNS answer.
5. Weighted routing dùng cho traffic split.
6. Latency routing chọn endpoint có network latency tốt hơn.
7. Geolocation routing chọn theo country/continent.
8. Geoproximity routing dùng distance và bias.
9. Failover routing cần primary, secondary và health signal.
10. Multivalue answer trả nhiều healthy record nhưng không thay thế ALB/NLB.
11. DNS failover không tự replicate database/application.
12. Route 53 Resolver hỗ trợ private và hybrid DNS.
13. Route 53 Alias tới ALB/NLB/CloudFront tránh hard-code IP.
14. Route 53 và ALB hoạt động ở hai lớp khác nhau.
15. TTL, cache và connection reuse khiến traffic thực tế có thể khác routing policy dự kiến.
16. Health check phải phản ánh failure cần xử lý nhưng tránh cascade failure.

## Tài liệu tham khảo chính thức

- [Amazon Route 53 Developer Guide](https://docs.aws.amazon.com/Route53/latest/DeveloperGuide/Welcome.html)
- [Hosted zones](https://docs.aws.amazon.com/Route53/latest/DeveloperGuide/hosted-zones-working-with.html)
- [Working with records](https://docs.aws.amazon.com/Route53/latest/DeveloperGuide/rrsets-working-with.html)
- [Routing policies](https://docs.aws.amazon.com/Route53/latest/DeveloperGuide/routing-policy.html)
- [Health checks and DNS failover](https://docs.aws.amazon.com/Route53/latest/DeveloperGuide/dns-failover.html)
- [Private hosted zones](https://docs.aws.amazon.com/Route53/latest/DeveloperGuide/hosted-zones-private.html)
- [Route 53 Resolver](https://docs.aws.amazon.com/Route53/latest/DeveloperGuide/resolver.html)
- [Alias records for load balancers](https://docs.aws.amazon.com/Route53/latest/DeveloperGuide/routing-to-elb-load-balancer.html)
- [Latency-based routing](https://docs.aws.amazon.com/Route53/latest/DeveloperGuide/routing-policy-latency.html)
- [Failover routing](https://docs.aws.amazon.com/Route53/latest/DeveloperGuide/routing-policy-failover.html)
- [Geolocation routing](https://docs.aws.amazon.com/Route53/latest/DeveloperGuide/routing-policy-geo.html)
- [Resolver endpoints](https://docs.aws.amazon.com/Route53/latest/DeveloperGuide/resolver-endpoints.html)


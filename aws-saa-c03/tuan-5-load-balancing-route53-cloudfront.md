# Tuần 5 — Load balancing, Route 53 và CloudFront

> Mục tiêu: chọn đúng load balancer theo protocol và routing requirement, thiết kế DNS routing/failover bằng Route 53 và dùng CloudFront để cache content gần user, giảm latency và tải origin.

## 1. Kết quả cần đạt

- Phân biệt ALB, NLB và Gateway Load Balancer.
- Chọn ALB cho HTTP/HTTPS Layer 7, host/path routing và target group.
- Chọn NLB cho TCP/UDP/TLS, static IP và high throughput.
- Hiểu listener, listener rule, target group và health check.
- Hiểu hosted zone, record, alias, CNAME và TTL.
- Chọn simple, weighted, latency, failover, geolocation, geoproximity và multivalue routing.
- Dùng Route 53 health check và DNS failover.
- Hiểu CloudFront distribution, origin, behavior, cache policy và origin request policy.
- Cấu hình private S3 origin bằng Origin Access Control.
- Phân biệt signed URL, signed cookie, invalidation và versioned object.
- So sánh CloudFront với Global Accelerator.

## 2. Lịch học gợi ý

| Buổi | Nội dung | Thời lượng |
|---|---|---:|
| 1 | ALB, NLB, target group và health check | 3 giờ |
| 2 | Route 53 record, TTL và routing policy | 2 giờ |
| 3 | Route 53 health check/failover | 2 giờ |
| 4 | CloudFront cache, origin và access control | 3 giờ |
| 5 | Lab tích hợp và câu hỏi SAA-C03 | 2–3 giờ |

Cách suy luận: protocol và routing trước khi chọn load balancer; user location và failover trước khi chọn Route 53 policy; cacheability và access control trước khi chọn CloudFront behavior.

---

## 3. Load balancing fundamentals

Load balancer là điểm vào phân phối request đến nhiều target. Nó giúp tăng availability, scale-out, terminate TLS, route theo protocol/host/path và giảm việc client phải biết IP từng instance.

Load balancer không tự làm application stateless, không tự sửa database và không thay thế Auto Scaling. ASG tạo/thay instance; load balancer phân phối traffic.

| Thành phần | Vai trò |
|---|---|
| Listener | nhận traffic trên port/protocol |
| Listener rule | quyết định action/routing |
| Target group | tập hợp target và health check |
| Target | EC2, IP, Lambda hoặc resource hỗ trợ |
| Health check | xác định target phục vụ được không |
| Load balancer node | endpoint trong AZ đã bật |

Bật load balancer ở ít nhất hai AZ để tăng HA. Target group nên có target ở nhiều AZ nếu cần chịu mất một AZ.

## 4. Application Load Balancer

ALB là Layer 7 load balancer cho HTTP/HTTPS. Nó hiểu request và route theo host, path, header, query string hoặc method.

### Use case

- web application và REST API;
- host-based routing: app.example.com và api.example.com;
- path-based routing: /api và /static;
- blue/green hoặc canary;
- redirect HTTP sang HTTPS;
- TLS termination;
- target là EC2, container, IP hoặc Lambda trong pattern hỗ trợ.

| Condition | Action |
|---|---|
| Host = api.example.com | API target group |
| Path = /static/* | static target group |
| Path = /api/* | API target group |
| HTTP 80 | redirect HTTPS 443 |
| không match | default target group |

### Listener và rule

Listener gồm protocol/port và default action. Rule có priority; request match rule phù hợp trước khi đi default action.

Action phổ biến: forward, redirect, fixed response và authenticate theo cơ chế hỗ trợ. Cấu hình default action an toàn.

### TLS termination

ALB có thể terminate TLS tại listener 443 bằng ACM certificate rồi forward HTTP/HTTPS tới target. Nếu cần end-to-end encryption, forward HTTPS và giới hạn target SG chỉ nhận từ ALB-SG.

### Target group và health check

Target group cần cấu hình protocol/port, health check path/port, success code, deregistration delay và target type.

Health check nên nhẹ, nhanh, phản ánh khả năng phục vụ và không phụ thuộc quá sâu vào database/third-party tạm thời. Khi bật ELB health check cho ASG, target unhealthy có thể bị terminate/replaced; grace period phải đủ cho boot.

### Security group

ALB public thường cho phép inbound 80/443 từ client. Target SG chỉ cho phép app port từ ALB-SG.

~~~text
Internet → ALB-SG: 443
ALB → App-SG: 8080
App → DB-SG: 5432
~~~

## 5. Network Load Balancer

NLB là Layer 4 cho TCP, UDP và TLS. Nó không hiểu path/host như ALB.

Chọn NLB khi cần:

- TCP/UDP workload;
- high throughput và low latency;
- static IP hoặc Elastic IP theo AZ;
- preserve client source IP trong pattern hỗ trợ;
- TLS pass-through hoặc TLS termination;
- protocol không phải HTTP;
- AWS PrivateLink endpoint service trong pattern phù hợp.

Nếu cần HTTP path-based routing, ALB thường đúng hơn. NLB listener nhận TCP/UDP/TLS trên port và target group có health check phù hợp.

NLB có thể terminate TLS hoặc pass through TCP tới target. Chọn theo end-to-end encryption, certificate management, client IP và protocol.

## 6. Gateway Load Balancer

Gateway Load Balancer dùng để deploy, scale và phân phối traffic qua network appliances như firewall, IDS/IPS hoặc deep packet inspection.

Ở mức SAA cần nhớ:

- dùng cho transparent appliance;
- dùng GENEVE port 6081;
- có endpoint để chèn appliance vào traffic path;
- hỗ trợ scale appliance fleet;
- không phải load balancer cho HTTP path routing.

## 7. So sánh load balancer

| Tiêu chí | ALB | NLB | GWLB |
|---|---|---|---|
| Layer | 7 | 4 | appliance insertion |
| Protocol | HTTP/HTTPS | TCP/UDP/TLS | GENEVE/appliance |
| Routing | host/path/header/query | IP/port/connection | tới appliance |
| Static IP | không phải điểm mạnh | có theo AZ | endpoint-based |
| Use case | web/API/container | TCP/UDP/high throughput | firewall/IDS/IPS |

---

## 8. Route 53 fundamentals

Route 53 là dịch vụ DNS managed, domain registration và health checking.

### Hosted zone

- Public hosted zone trả lời DNS cho Internet.
- Private hosted zone trả lời DNS trong VPC đã associate hoặc network phù hợp.

Một domain có thể có public và private hosted zone cùng tên; answer phụ thuộc nơi query.

### Record

| Record | Use case |
|---|---|
| A | hostname tới IPv4 |
| AAAA | hostname tới IPv6 |
| CNAME | hostname tới hostname khác, không dùng zone apex |
| Alias | AWS resource/endpoint, dùng được ở apex |
| MX | mail exchange |
| TXT | verification/policy |
| NS | name server delegation |
| SOA | authority metadata |

### Alias và CNAME

Alias là Route 53-specific record trỏ tới ALB, CloudFront, S3 website endpoint, API Gateway, Global Accelerator hoặc resource hỗ trợ. Alias dùng được ở zone apex như example.com.

CNAME trỏ hostname tới hostname nhưng không dùng ở zone apex. Alias không phải HTTP redirect; nó là DNS answer.

### TTL

TTL quyết định resolver giữ DNS answer bao lâu.

- TTL thấp: thay đổi/failover phản ứng nhanh hơn nhưng query nhiều hơn.
- TTL cao: giảm query nhưng thay đổi chậm hơn.

TTL không đảm bảo mọi client chuyển ngay vì resolver có cache riêng. Khi chuyển traffic, giảm TTL trước sự kiện nếu có thể.

---

## 9. Route 53 routing policies

### Simple

Một endpoint hoặc tập value đơn giản, phù hợp endpoint đơn.

### Weighted

Phân phối theo weight giữa nhiều record cùng tên. Dùng cho canary, blue/green và migration từng phần. Weight là tỷ lệ tương đối.

### Latency-based

Trả endpoint có latency thấp nhất theo AWS measurements. Dùng tối ưu user experience toàn cầu, không phải định tuyến theo quốc gia.

### Failover

Có primary và secondary. Khi primary unhealthy, Route 53 trả secondary nếu health/evaluate target health đúng. Phù hợp active-passive DR.

### Geolocation

Route theo continent, country hoặc default. Dùng khi requirement là content/regulation theo quốc gia. Cần default record cho location không match.

### Geoproximity

Route theo khoảng cách địa lý giữa user và resource, có thể dùng bias để mở rộng/thu hẹp vùng.

### Multivalue answer

Trả nhiều healthy record để client chọn. Có thể tăng availability đơn giản nhưng không thay thế load balancer.

| Requirement | Policy |
|---|---|
| Một endpoint | Simple |
| Chia traffic theo tỷ lệ | Weighted |
| Endpoint latency thấp | Latency |
| Primary/backup | Failover |
| Theo quốc gia/continent | Geolocation |
| Điều chỉnh vùng theo khoảng cách | Geoproximity |
| Nhiều healthy IP đơn giản | Multivalue |

---

## 10. Health check và DNS failover

Health check có thể kiểm tra endpoint bằng HTTP/HTTPS/TCP hoặc dùng CloudWatch alarm trong pattern hỗ trợ.

Phân biệt:

- Route 53 health check kiểm tra endpoint từ bên ngoài;
- load balancer health check kiểm tra target phía sau LB;
- application monitoring kiểm tra business health;
- DNS failover chịu ảnh hưởng TTL và resolver cache.

Alias tới AWS resource có thể dùng Evaluate Target Health theo behavior hỗ trợ. Không thêm health check trùng lặp nếu chưa hiểu tác động.

Failover tốt cần secondary sẵn sàng, data được replicate/restore, DNS/TTL đúng, health check không false positive và runbook được test. Route 53 failover không tự replicate database hoặc deploy secondary application.

---

## 11. CloudFront fundamentals

CloudFront là CDN phân phối content qua edge location gần user. Nó giảm latency, origin request và origin load.

### Distribution và origin

- Distribution: cấu hình CloudFront và domain.
- Origin: S3, ALB, NLB, API Gateway hoặc custom HTTP server.
- Behavior: rule theo path để chọn origin, cache policy, methods và security.
- Origin group: primary/secondary failover theo pattern hỗ trợ.

~~~text
User → CloudFront edge
          ├── cache hit → trả ngay
          └── cache miss → origin → cache → trả user
~~~

### Cache hit/miss

- Cache hit: object còn trong cache và cache key phù hợp.
- Cache miss: edge gọi origin rồi cache object.
- TTL: thời gian object ở cache.
- Invalidation: yêu cầu xóa object/path khỏi cache.

Không cache bừa dữ liệu cá nhân hoặc response phụ thuộc cookie/header/query nếu cache key không chứa chúng.

### Cache policy và origin request policy

Cache policy quyết định cache key và TTL, gồm header, cookie, query string đưa vào cache key.

Origin request policy quyết định request nào forward tới origin nhưng không nhất thiết đưa vào cache key.

Forward một header tới origin không tự động làm mỗi giá trị header tạo cache entry riêng. Nếu response khác nhau theo header/cookie/query, phải cấu hình cache key đúng hoặc không cache response riêng tư.

### Behavior

Behavior thường match path pattern:

| Path | Origin | Policy |
|---|---|---|
| /static/* | S3 | cache dài, GET/HEAD |
| /api/* | ALB/API | TTL thấp hoặc no-cache |
| default | web origin | policy mặc định |

Behavior cụ thể hơn phải được ưu tiên đúng; default behavior xử lý path còn lại.

### Methods và protocol

Static content thường chỉ cần GET/HEAD. API có thể cần OPTIONS/POST/PUT/DELETE theo use case. Viewer protocol policy có thể redirect HTTP sang HTTPS; origin protocol policy quyết định CloudFront tới origin bằng HTTP/HTTPS.

---

## 12. CloudFront với private S3 origin

Pattern khuyến nghị:

~~~text
User → CloudFront → Origin Access Control → private S3 bucket
~~~

Thiết kế:

- bật Block Public Access;
- không public bucket chỉ để CloudFront đọc;
- tạo Origin Access Control;
- bucket policy chỉ cho phép distribution/principal cần thiết;
- kiểm tra signing behavior, encryption và custom domain certificate.

OAC giúp CloudFront ký request tới S3. User không cần S3 permission trực tiếp, nhưng CloudFront phải có resource policy đúng.

---

## 13. Signed URL, signed cookie và invalidation

### Signed URL

Cấp quyền cho object hoặc URL cụ thể trong thời gian giới hạn. Phù hợp download/upload hoặc file riêng lẻ.

### Signed cookie

Cấp quyền cho nhiều file/path mà không cần URL riêng từng object. Phù hợp video segments, private site hoặc nhóm content.

| Tiêu chí | Signed URL | Signed cookie |
|---|---|---|
| Phạm vi | object/url cụ thể | nhiều object/path |
| Client | truyền URL ký | giữ cookie |
| Use case | download riêng lẻ | nhiều request private |

Signature không thay thế application authorization. Cần quản lý key, expiry, revocation và logging.

Invalidation xóa object/path khỏi edge cache, nhưng có thể phát sinh cost/limit và không instant tuyệt đối. Với static asset, versioned filename thường tốt hơn purge liên tục.

---

## 14. CloudFront security và Global Accelerator

Security controls thường gặp: ACM certificate, AWS WAF, signed URL/cookie, OAC, geo restriction, response headers policy, compression và Shield theo architecture.

CloudFront không thay thế WAF, IAM, bucket policy hoặc authorization. Cache sensitive response sai cách có thể làm lộ dữ liệu.

| Tiêu chí | CloudFront | Global Accelerator |
|---|---|---|
| Bản chất | CDN/cache | global network accelerator |
| Protocol | chủ yếu HTTP/HTTPS content | TCP/UDP/HTTP application |
| Cache | có | không |
| Endpoint | S3/ALB/custom origin | ALB/NLB/EC2/EIP tùy hỗ trợ |
| IP | domain/edge behavior | static anycast IP |
| Chọn khi | cache và giảm origin load | static IP, TCP/UDP, failover |

Nếu đề nhấn mạnh cache content gần user, chọn CloudFront. Nếu nhấn mạnh static anycast IP/TCP/UDP mà không cần cache, xem Global Accelerator.

---

## 15. Lab bắt buộc

### Lab 1 — ALB trước ASG

- Dùng ASG Multi-AZ của Tuần 4.
- Tạo target group và health check.
- Đặt ALB trong public subnets.
- Mở SG client → ALB và ALB → app.
- Kiểm tra target health và terminate một target.

### Lab 2 — Path-based routing

Tạo hai service/target group:

| Request | Target |
|---|---|
| /api/* | API service |
| /static/* | static service |
| default | web service |

Test priority, default action, 404 và health check từng group.

### Lab 3 — Route 53 alias

- Tạo public hosted zone hoặc domain lab.
- Tạo alias A/AAAA tới ALB.
- Kiểm tra DNS answer và TTL.
- So sánh alias với CNAME.
- Không tạo A record trỏ IP tạm thời của ALB.

### Lab 4 — Routing policy

Trên endpoint test, thử weighted 80/20, latency giữa hai Region nếu có, failover primary/secondary và health check. Ghi policy, health state, answer và thời gian client thấy thay đổi.

### Lab 5 — CloudFront với S3

1. Tạo private S3 bucket và bật Block Public Access.
2. Upload static object.
3. Tạo CloudFront distribution với S3 origin.
4. Dùng OAC và bucket policy giới hạn distribution.
5. Tạo behavior cho static content.
6. Quan sát cache hit/miss và TTL.
7. Dùng versioned filename.
8. Thử invalidation có kiểm soát.
9. Xác nhận truy cập trực tiếp S3 bị chặn.

### Lab 6 — CloudFront với ALB

Tạo origin ALB, behavior /api/* TTL thấp/no-cache và /static/* TTL dài. Kiểm tra forwarded headers/query/cookie và xác nhận response không bị cache nhầm theo user.

---

## 16. Bẫy đề thi SAA-C03

- ALB là Layer 7; NLB là Layer 4.
- ALB phù hợp host/path routing; NLB phù hợp TCP/UDP/TLS, static IP và throughput.
- ASG không tự load balance nếu không gắn ALB/NLB.
- Target unhealthy bị LB loại khỏi traffic nhưng nguyên nhân app vẫn phải xử lý.
- Target SG nên chỉ nhận traffic từ ALB-SG.
- CNAME không dùng ở zone apex; alias dùng được cho AWS resource và apex.
- TTL thấp không bảo đảm failover tức thời.
- Latency routing theo latency của AWS, không theo quốc gia.
- Geolocation routing theo location, không phải latency.
- Weighted routing không tự có health/failover nếu chưa cấu hình.
- Route 53 failover không replicate data hoặc deploy secondary.
- CloudFront behavior dựa trên path pattern.
- Cache policy quyết định cache key; origin request policy quyết định forwarding.
- Forward header/cookie/query không đồng nghĩa cache key đã chứa nó.
- Không cache private response nếu cache key không phân biệt user.
- Private S3 origin nên dùng OAC và bucket policy.
- Signed URL dành URL/object cụ thể; signed cookie phù hợp nhiều object/path.
- Invalidation không phải giải pháp tốt nhất cho mọi thay đổi; versioned filename thường tốt hơn.
- CloudFront cache content; Global Accelerator không phải CDN/cache.
- GWLB dành network appliances, không phải HTTP path routing.

---

## 17. Câu hỏi tự luyện

### Câu 1

Ứng dụng HTTP cần route /api/* và /static/* tới hai service khác nhau. Chọn gì?

**Đáp án:** ALB với listener rules và target groups theo path.

### Câu 2

Workload cần TCP/UDP, throughput cao và static IP theo AZ. Chọn gì?

**Đáp án:** NLB.

### Câu 3

Cần đưa traffic qua firewall appliance fleet. Chọn gì?

**Đáp án:** Gateway Load Balancer và endpoint pattern phù hợp.

### Câu 4

Muốn tạo example.com trỏ tới ALB. Dùng record nào?

**Đáp án:** Route 53 alias A/AAAA tới ALB; CNAME không dùng ở zone apex.

### Câu 5

Muốn chia traffic 90/10 giữa version cũ và mới. Dùng policy nào?

**Đáp án:** Weighted routing, kết hợp health check/rollback nếu cần.

### Câu 6

Muốn user tới Region có latency thấp nhất. Dùng policy nào?

**Đáp án:** Latency-based routing, không phải geolocation.

### Câu 7

Muốn route theo quốc gia vì compliance. Dùng policy nào?

**Đáp án:** Geolocation routing, kèm default record.

### Câu 8

Static asset ở S3 cần cache gần user nhưng bucket không public. Thiết kế nào?

**Đáp án:** CloudFront với S3 origin, OAC, Block Public Access và bucket policy giới hạn distribution.

### Câu 9

API response khác nhau theo Authorization cookie nhưng CloudFront cache chung. Rủi ro?

**Đáp án:** cache nhầm response giữa user. Cần cache key/forwarding đúng hoặc không cache response riêng tư.

### Câu 10

Cần static anycast IP cho global TCP app, không cần cache. Chọn gì?

**Đáp án:** Global Accelerator.

### Câu 11

Đổi file đã cache và cần cập nhật sớm. Có lựa chọn nào?

**Đáp án:** invalidation có kiểm soát hoặc versioned filename; versioning thường tốt hơn cho static asset.

---

## 18. Checklist hoàn thành tuần

### Kiến thức

- [ ] Phân biệt ALB, NLB và GWLB.
- [ ] Chọn ALB cho Layer 7/path/host routing.
- [ ] Chọn NLB cho Layer 4/TCP/UDP/TLS/static IP.
- [ ] Hiểu listener, rule, target group và health check.
- [ ] Phân biệt public/internal load balancer.
- [ ] Giải thích hosted zone, record, alias, CNAME và TTL.
- [ ] Chọn đúng Route 53 routing policy.
- [ ] Hiểu health check và DNS failover.
- [ ] Phân biệt CloudFront distribution, origin và behavior.
- [ ] Phân biệt cache policy với origin request policy.
- [ ] Dùng OAC cho private S3 origin.
- [ ] Phân biệt signed URL và signed cookie.
- [ ] Hiểu invalidation và versioned object.
- [ ] Phân biệt CloudFront và Global Accelerator.

### Thực hành

- [ ] Đã đặt ALB trước ASG.
- [ ] Đã tạo path-based routing cho /api/* và /static/*.
- [ ] Đã tạo Route 53 alias tới ALB.
- [ ] Đã thử weighted/failover routing trên môi trường test.
- [ ] Đã tạo CloudFront trước S3 hoặc ALB.
- [ ] Đã quan sát cache hit/miss và TTL.
- [ ] Đã kiểm tra private S3 origin bằng OAC.
- [ ] Đã test response không bị cache nhầm theo user.
- [ ] Đã dọn distribution, ALB, record test và resource tính phí sau lab.

Bạn sẵn sàng sang Tuần 6 khi có thể chọn ALB/NLB theo protocol và routing, chọn Route 53 policy theo location/failover, và giải thích cách CloudFront cache content gần user mà vẫn giữ origin private.

---

## 19. Tài liệu tham khảo

- [Elastic Load Balancing](https://docs.aws.amazon.com/elasticloadbalancing/latest/userguide/what-is-load-balancing.html) — tổng quan load balancing.
- [Application Load Balancer](https://docs.aws.amazon.com/elasticloadbalancing/latest/application/introduction.html) — Layer 7, listener và routing.
- [Network Load Balancer](https://docs.aws.amazon.com/elasticloadbalancing/latest/network/introduction.html) — Layer 4 và static IP.
- [Gateway Load Balancer](https://docs.aws.amazon.com/elasticloadbalancing/latest/gateway/introduction.html) — network appliance insertion.
- [Amazon Route 53 Developer Guide](https://docs.aws.amazon.com/Route53/latest/DeveloperGuide/Welcome.html) — hosted zone và record.
- [Route 53 routing policies](https://docs.aws.amazon.com/Route53/latest/DeveloperGuide/routing-policy.html) — routing policy.
- [Route 53 health checks](https://docs.aws.amazon.com/Route53/latest/DeveloperGuide/dns-failover.html) — health-aware DNS/failover.
- [Amazon CloudFront Developer Guide](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/Introduction.html) — distribution, origin và cache.
- [CloudFront cache policies](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/controlling-the-cache-key.html) — cache key và TTL.
- [CloudFront Origin Access Control](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/private-content-restricting-access-to-s3.html) — private S3 origin.
- [AWS Global Accelerator](https://docs.aws.amazon.com/global-accelerator/latest/dg/what-is-global-accelerator.html) — static anycast IP và global routing.


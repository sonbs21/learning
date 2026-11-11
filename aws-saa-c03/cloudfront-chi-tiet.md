# CloudFront chuyên sâu — AWS SAA-C03

> Tài liệu tách riêng phần CloudFront từ Tuần 5. Mục tiêu là hiểu CloudFront như một CDN và edge delivery service: request đi qua edge location thế nào, origin được gọi ra sao, cache key quyết định điều gì, làm thế nào bảo vệ S3 private origin, và cách tránh cache nhầm dữ liệu riêng tư.

## 1. CloudFront là gì?

Amazon CloudFront là một content delivery network phân phối nội dung qua mạng lưới edge location gần người dùng.

~~~text
User
  ↓
CloudFront Edge Location
  ├── Cache hit  → trả content ngay
  └── Cache miss → gọi origin → lưu cache → trả user
~~~

CloudFront giúp:

- Giảm latency cho user ở xa origin.
- Cache static và dynamic content phù hợp.
- Giảm số request tới origin.
- Giảm tải cho ALB, EC2, S3 hoặc API.
- Tích hợp WAF ở edge.
- Bảo vệ private S3 origin.
- Hỗ trợ signed URL và signed cookie.
- Dùng custom domain và TLS.
- Giới hạn phương thức HTTP.
- Điều chỉnh request/response ở edge.
- Hỗ trợ origin failover theo behavior phù hợp.

CloudFront không phải:

- Database.
- Load balancer thay thế ALB/NLB.
- DNS thay thế Route 53.
- Authorization system hoàn chỉnh.
- Cơ chế replicate dữ liệu origin.
- Bảo đảm mọi response đều được cache.

## 2. CloudFront trong architecture

~~~mermaid
flowchart LR
    User[User]
    DNS[Route 53]
    CF[CloudFront Distribution]
    WAF[AWS WAF]
    Edge[Edge Location]
    S3[S3 Private Origin]
    ALB[ALB Origin]
    API[API Gateway Origin]
    App[Private Application]
    OAC[Origin Access Control]

    User --> DNS
    DNS --> CF
    CF --> WAF
    WAF --> Edge
    Edge --> S3
    Edge --> ALB
    Edge --> API
    ALB --> App
    CF --> OAC
    OAC --> S3
~~~

Một request thường đi qua:

1. User query domain qua Route 53.
2. DNS trả CloudFront distribution endpoint.
3. User kết nối tới edge location gần hoặc phù hợp.
4. CloudFront kiểm tra cache.
5. Nếu cache hit, CloudFront trả response.
6. Nếu cache miss, CloudFront gọi origin.
7. Origin trả response.
8. CloudFront trả response cho user và có thể lưu cache.
9. Các request sau có thể được phục vụ từ edge.

## 3. Các khái niệm chính

| Thành phần | Vai trò |
|---|---|
| Distribution | Cấu hình CloudFront tổng thể |
| Edge location | Điểm hiện diện gần user |
| Origin | Nguồn content/backend |
| Origin group | Primary/secondary origin cho failover |
| Behavior | Rule theo path và policy |
| Cache policy | Quyết định cache key và TTL |
| Origin request policy | Quyết định request forward tới origin |
| Viewer request | Request từ user tới CloudFront |
| Origin request | Request CloudFront gửi tới origin |
| Viewer response | Response CloudFront trả user |
| Origin response | Response CloudFront nhận từ origin |
| OAC | Cấp quyền CloudFront truy cập S3 private |
| Invalidation | Xóa object/path khỏi cache |
| Signed URL/cookie | Kiểm soát private content |
| WAF | Lọc request ở lớp web |

---

# Phần I — Distribution, edge và origin

## 4. Distribution

CloudFront distribution là cấu hình logic của một CDN endpoint.

Distribution định nghĩa:

- Origin.
- Default behavior.
- Additional behaviors.
- Cache policy.
- Origin request policy.
- Viewer protocol policy.
- Allowed methods.
- Certificate.
- WAF web ACL.
- Geo restriction.
- Logging.
- Compression.
- Error response.
- Price class theo cấu hình phù hợp.

Distribution có domain name do CloudFront cung cấp. Bạn có thể dùng Route 53 Alias để trỏ custom domain tới distribution.

~~~text
www.example.com
  ↓ Route 53 Alias
CloudFront distribution
  ↓
Origin
~~~

## 5. Edge location

Edge location là nơi CloudFront xử lý request gần user.

Edge location có thể:

- Nhận connection.
- Kiểm tra cache.
- Gọi origin nếu cache miss.
- Cache response.
- Chạy CloudFront Functions hoặc Lambda@Edge theo event phù hợp.
- Áp dụng WAF/geo/security behavior theo architecture.

Edge location không phải bản sao đầy đủ của origin. Chỉ content được cache hoặc edge logic mới xuất hiện ở edge.

## 6. Regional edge cache

CloudFront có lớp cache trung gian theo architecture. Khi edge location cache miss, request có thể đi qua regional edge cache trước khi đến origin.

Mục đích:

- Giảm số lần nhiều edge location gọi origin.
- Tăng cache efficiency.
- Giảm origin load.
- Cải thiện content delivery toàn cầu.

Không nên giả định mọi request cache hit ở edge. Hãy theo dõi cache status, TTL, cache key và invalidation.

## 7. Origin

Origin là nguồn content hoặc application mà CloudFront gọi khi không thể trả response từ cache.

Origin có thể là:

- S3 bucket.
- ALB.
- NLB trong pattern hỗ trợ.
- API Gateway.
- EC2/custom HTTP server.
- Media service hoặc HTTP origin khác.
- Origin group.

### S3 origin

Phù hợp:

- Static website asset.
- Image/video/file.
- Data download.
- Static frontend.
- Private object phân phối qua OAC.

### ALB origin

Phù hợp:

- Dynamic web application.
- API.
- Application cần compute backend.
- CloudFront cache static path nhưng forward API path tới ALB.

### API Gateway origin

Phù hợp:

- API managed.
- Serverless backend.
- Endpoint có authorization/throttling.
- API cần edge distribution bổ sung.

### Custom origin

Phù hợp:

- HTTP server bên ngoài AWS.
- On-premises application public reachable.
- Partner endpoint.
- Origin không phải AWS service trực tiếp.

Custom origin cần kiểm tra:

- DNS.
- TLS certificate.
- Port.
- Origin protocol.
- Firewall allow CloudFront.
- Timeout.
- Origin response behavior.

## 8. Origin group

Origin group có primary và secondary origin.

~~~text
CloudFront
├── Primary origin
└── Secondary origin
~~~

Dùng khi:

- Primary origin trả lỗi thuộc failover criteria.
- Có static backup.
- Có secondary ALB.
- Có S3 backup bucket.
- Muốn origin-level failover.

Origin failover của CloudFront khác Route 53 failover:

- CloudFront failover xảy ra trong request path của distribution.
- Route 53 failover xảy ra ở DNS answer.
- CloudFront cần behavior/origin group phù hợp.
- Route 53 cần record/health check phù hợp.

---

# Phần II — Cache behavior và path routing

## 9. Behavior là gì?

Behavior là rule xác định CloudFront xử lý request theo path như thế nào.

Ví dụ:

~~~text
/static/*
  → S3 origin
  → cache dài
  → chỉ GET/HEAD

/api/*
  → ALB origin
  → cache thấp hoặc không cache
  → cho phép methods phù hợp

Default
  → web origin
~~~

Behavior có thể định nghĩa:

- Path pattern.
- Origin.
- Cache policy.
- Origin request policy.
- Viewer protocol policy.
- Allowed methods.
- Cached methods.
- Cookie/header/query handling.
- Compress objects.
- Field-level encryption theo pattern hỗ trợ.
- Lambda@Edge/CloudFront Functions association.

## 10. Path pattern

Path pattern route request tới origin/behavior phù hợp.

~~~text
/images/*
  → S3 image origin

/api/*
  → ALB API origin

/video/*
  → media origin

default
  → web origin
~~~

Cần kiểm tra behavior cụ thể hơn có được match trước behavior tổng quát hay không.

Nếu cấu hình sai:

- API có thể bị gửi tới S3.
- Static object có thể đi tới ALB.
- Response private có thể bị cache sai.
- TTL/path behavior không đúng.
- Request method bị chặn.

## 11. Default behavior

Một distribution cần default behavior xử lý request không match additional behavior.

Default behavior nên:

- Trỏ tới origin mặc định hợp lý.
- Có viewer protocol policy rõ.
- Có cache policy phù hợp.
- Không vô tình cache dynamic/private response.
- Cho phép methods tối thiểu cần thiết.
- Có error behavior phù hợp.

## 12. Allowed methods và cached methods

Static content thường chỉ cần:

~~~text
Allowed:
  GET, HEAD

Cached:
  GET, HEAD
~~~

API có thể cần:

~~~text
Allowed:
  GET, HEAD, OPTIONS, PUT, POST, PATCH, DELETE
Cached:
  thường chỉ GET, HEAD hoặc không cache API
~~~

Không nên cho phép mọi method nếu application không cần. Methods như POST, PUT, DELETE thường không cache và có side effect.

OPTIONS cần thiết cho CORS preflight trong nhiều frontend/API architecture.

---

# Phần III — Cache key, cache policy và origin request policy

## 13. Cache key là gì?

Cache key quyết định hai request có được xem là cùng một cache object hay không.

Cache key có thể phụ thuộc vào:

- Path.
- Query string.
- Header.
- Cookie.
- Host.
- Các thành phần được cấu hình trong cache policy.

Ví dụ hai request:

~~~text
GET /profile
Cookie: user=A

GET /profile
Cookie: user=B
~~~

Nếu cookie không có trong cache key, CloudFront có thể coi hai request là cùng object. Nếu response khác nhau theo user, đây là rủi ro cache leak.

## 14. Cache policy

Cache policy quyết định:

- Thành phần nào nằm trong cache key.
- TTL tối thiểu.
- TTL mặc định.
- TTL tối đa.
- Query string nào được include.
- Cookie nào được include.
- Header nào được include.

Cache policy trả lời:

> Request nào được coi là cùng một cache object?

### Cache key càng rộng

Ưu điểm:

- Correctness tốt hơn khi response phụ thuộc nhiều input.
- Giảm nguy cơ trả nhầm response.

Nhược điểm:

- Cache hit ratio thấp hơn.
- Nhiều cache variant.
- Tăng origin request.
- Cache invalidation phức tạp.

### Cache key càng hẹp

Ưu điểm:

- Cache hit ratio cao hơn.
- Ít object variant.
- Giảm origin load.

Nhược điểm:

- Có thể trả response sai.
- Có thể làm lộ dữ liệu riêng tư.
- Không phù hợp response phụ thuộc user/header/cookie/query.

## 15. Origin request policy

Origin request policy quyết định thành phần nào CloudFront forward tới origin nhưng không nhất thiết đưa vào cache key.

Nó trả lời:

> Origin cần thấy những header, cookie, query string nào?

Hai policy có thể khác nhau:

~~~text
Cache policy:
  query string không nằm trong cache key

Origin request policy:
  query string được forward tới origin
~~~

Khi đó hai request có thể dùng chung cache object nhưng origin vẫn nhận query string trong cache miss. Điều này chỉ an toàn nếu response không khác nhau theo query string hoặc cache behavior được thiết kế đúng.

## 16. Cache key và forwarding phải đồng bộ

Nếu response phụ thuộc vào một input, input đó thường cần được xem xét trong cache key.

Ví dụ response phụ thuộc:

- Authorization header.
- Session cookie.
- Language header.
- Device type.
- Query parameter.
- Tenant ID.

Các lựa chọn:

1. Include input vào cache key.
2. Không cache response đó.
3. Tách behavior/path.
4. Chuẩn hóa request để input không ảnh hưởng response.
5. Dùng application cache khác có control rõ ràng.

Nguyên tắc:

> Forward một header/cookie/query tới origin không đồng nghĩa cache key đã phân biệt giá trị đó.

## 17. Ví dụ cache sai

~~~text
Request A:
GET /account
Cookie: user-A

Response A:
Account data của user-A

Request B:
GET /account
Cookie: user-B

Nếu cache key chỉ là /account:
  Request B có thể nhận response A
~~~

Đây là lỗi nghiêm trọng. Với dữ liệu riêng tư:

- Không cache.
- Hoặc include authorization/session input trong cache key.
- Hoặc dùng signed/private content pattern.
- Hoặc tách private request khỏi cacheable static content.

## 18. Cache policy cho static content

Static asset thường phù hợp:

- Cache key chỉ gồm path hoặc một số query cần thiết.
- TTL dài.
- Compression bật.
- GET/HEAD.
- Versioned filename.
- Origin request tối thiểu.

Ví dụ:

~~~text
/static/app.abc123.js
/images/logo.v2.png
/fonts/main.9f21.woff2
~~~

## 19. Cache policy cho API

API cần phân loại:

### Public cacheable API

- Response không phụ thuộc user.
- Có thể chấp nhận stale trong một khoảng thời gian.
- Cache key chứa query filter cần thiết.
- TTL ngắn/vừa.

### Private API

- Response phụ thuộc user/token/session.
- Không cache hoặc cache key phân biệt user/session rất cẩn thận.
- Cần forward authorization/cookie phù hợp.
- Có thể dùng CloudFront chỉ cho TLS/edge/WAF nhưng không cache.

### Mutation API

POST/PUT/PATCH/DELETE thường:

- Không cache response như static object.
- Cần forward method/body/header.
- Cần timeout và error handling.
- Cần authorization.

---

# Phần IV — TTL, cache hit và invalidation

## 20. TTL

TTL quyết định object ở cache bao lâu trước khi CloudFront kiểm tra origin lại.

Có ba giá trị thường cần hiểu:

- Minimum TTL.
- Default TTL.
- Maximum TTL.

TTL phù hợp phụ thuộc:

- Content thay đổi thường xuyên không.
- Có dùng versioned filename không.
- Chấp nhận stale bao lâu.
- Chi phí origin.
- Requirement freshness.
- Khả năng invalidation.

## 21. Cache hit và cache miss

### Cache hit

~~~text
User request
  ↓
Edge cache có object hợp lệ
  ↓
CloudFront trả ngay
  ↓
Không gọi origin
~~~

### Cache miss

~~~text
User request
  ↓
Edge cache không có object hoặc object hết TTL
  ↓
CloudFront gọi origin
  ↓
Nhận response
  ↓
Có thể lưu response vào cache
  ↓
Trả user
~~~

Cache hit ratio cao thường giúp giảm:

- Origin request.
- Origin compute.
- Data transfer từ origin.
- Latency.

Nhưng cache hit ratio cao không đảm bảo architecture đúng nếu đang cache nhầm private response.

## 22. Nguyên nhân cache miss nhiều

- TTL quá thấp.
- Cache key quá rộng.
- Query string không cần thiết nằm trong key.
- Cookie/header không cần thiết nằm trong key.
- Object chưa từng được request.
- Invalidation thường xuyên.
- Nhiều path/hostname variant.
- Content thay đổi và dùng tên file cố định.
- Distribution mới deploy/chưa warm cache.
- Request method không cache được.

## 23. Invalidation

Invalidation yêu cầu CloudFront loại object/path khỏi cache.

Ví dụ:

~~~text
/static/app.js
/images/*
/*
~~~

Dùng invalidation khi:

- Fix khẩn cấp.
- Object cũ chứa nội dung sai.
- Security response cần loại khỏi cache.
- Không thể đổi filename ngay.

Trade-off:

- Có giới hạn hoặc chi phí theo usage.
- Không phải lúc nào hoàn tất tức thì.
- Purge toàn bộ distribution có thể đắt và không cần thiết.
- Không nên dùng invalidation thay cho versioning liên tục.

## 24. Versioned object

Pattern tốt cho static asset:

~~~text
app.v1.js
app.v2.js
app.abc123.js
~~~

Khi deploy version mới:

- Upload file mới.
- HTML tham chiếu filename mới.
- File cũ có thể giữ theo retention.
- Không cần purge toàn bộ cache.
- TTL có thể dài hơn.
- Rollback dễ hơn.

Versioning thường tốt hơn invalidation cho immutable asset.

## 25. Cache-Control và origin headers

Origin có thể trả header điều khiển cache:

- Cache-Control.
- Expires.
- ETag.
- Last-Modified.
- Vary trong context phù hợp.

Cần hiểu:

- CloudFront behavior/policy và origin headers cùng ảnh hưởng caching.
- Không nên thiết kế TTL ở nhiều nơi mà không biết precedence.
- Kiểm tra response headers để biết browser cache và edge cache khác nhau.
- Browser cache không phải CloudFront cache.

---

# Phần V — Origin Access Control và S3 private origin

## 26. Vì sao không public S3 bucket?

Một pattern kém an toàn:

~~~text
User
  ↓
CloudFront
  ↓
S3 bucket public-read
~~~

Rủi ro:

- User có thể bypass CloudFront và truy cập S3 trực tiếp.
- Bucket policy quá rộng.
- Không kiểm soát mọi request qua WAF/CDN.
- Dễ expose object không chủ ý.

Pattern khuyến nghị:

~~~text
User
  ↓
CloudFront
  ↓
Origin Access Control
  ↓
Private S3 bucket
~~~

## 27. Origin Access Control

OAC cho phép CloudFront ký request tới S3 origin.

Thiết kế:

- Bật Block Public Access.
- Giữ bucket private.
- Tạo OAC.
- Gắn OAC vào S3 origin.
- Bucket policy chỉ cho phép CloudFront distribution cần thiết.
- Kiểm tra signing behavior.
- Kiểm tra KMS permission nếu object dùng SSE-KMS.

User không cần permission S3 trực tiếp. CloudFront truy cập S3 theo resource policy và OAC.

## 28. OAC và bucket policy

Bucket policy cần giới hạn:

- Principal phù hợp.
- Distribution ARN hoặc condition phù hợp.
- Actions tối thiểu, thường là read object.
- Resource đúng bucket/object path.
- Không mở public.

Nếu bucket dùng SSE-KMS:

- Kiểm tra KMS key policy.
- Cho phép principal/service cần decrypt.
- Kiểm tra Region/key.
- Không nhầm S3 permission với KMS permission.

## 29. S3 website endpoint và S3 REST origin

Cần phân biệt:

- S3 website endpoint: có website hosting behavior.
- S3 REST origin: dùng cho object API/private access.
- OAC thường dùng cho S3 origin/private content pattern.
- CloudFront + private S3 thường phù hợp khi cần HTTPS và access control.

Nếu cần custom error page, redirect hoặc SPA routing, có thể phải cấu hình CloudFront error response/behavior phù hợp.

---

# Phần VI — Private content

## 30. Signed URL

Signed URL cấp quyền truy cập có thời hạn cho URL/object cụ thể.

Phù hợp:

- Download file riêng lẻ.
- Object private.
- Link có expiry.
- Video segment cụ thể.
- Temporary access.

Một signed URL thường chứa thông tin signature, expiry và policy theo cơ chế sử dụng.

Cần quản lý:

- Private key.
- Key rotation.
- Expiry.
- Revocation strategy.
- Logging.
- Application authorization trước khi cấp URL.

Signed URL không tự xác minh user business permission nếu application cấp URL sai.

## 31. Signed cookie

Signed cookie cấp quyền cho nhiều object/path mà không cần tạo URL ký riêng cho từng object.

Phù hợp:

- Private website.
- Nhiều file trong một path.
- Video segments.
- Một phiên truy cập gồm nhiều object.
- User đã authenticate trước đó.

So sánh:

| Tiêu chí | Signed URL | Signed cookie |
|---|---|---|
| Phạm vi | Object/URL cụ thể | Nhiều object/path |
| Cách truyền quyền | Query/signature trong URL | Cookie |
| Phù hợp | Download riêng lẻ | Private site/video/content set |
| Quản lý client | Link riêng | Cookie theo session |

## 32. CloudFront signed access không thay application auth

Cần phân biệt:

- Application authentication: user là ai, có được dùng chức năng không.
- CloudFront signed access: user được tải content private nào trong thời gian nào.
- S3 authorization: CloudFront có quyền đọc object không.
- IAM: service/role được làm gì.

Các lớp có thể kết hợp:

~~~text
User login
  ↓
Application authorize
  ↓
Application cấp signed URL/cookie
  ↓
CloudFront kiểm tra signature
  ↓
OAC đọc S3 private object
~~~

---

# Phần VII — TLS, custom domain và viewer behavior

## 33. Custom domain

CloudFront distribution có domain mặc định. Có thể dùng custom domain:

~~~text
www.example.com
  ↓ Route 53 Alias
CloudFront distribution
~~~

Cần:

- Alternate domain name.
- ACM certificate.
- Certificate khớp hostname.
- Route 53 record.
- DNS delegation.
- Viewer protocol policy.

## 34. TLS certificate

Khi dùng HTTPS ở CloudFront:

- Certificate phải có domain phù hợp.
- Certificate phải ở Region yêu cầu của CloudFront.
- DNS validation record phải tồn tại.
- Certificate còn hiệu lực.
- Distribution phải gắn certificate đúng.
- SNI/client compatibility cần cân nhắc trong legacy scenario.

## 35. Viewer protocol policy

Các lựa chọn thường gặp:

- HTTP and HTTPS.
- Redirect HTTP to HTTPS.
- HTTPS only.

Best practice cho public web:

~~~text
HTTP request
  ↓
Redirect
  ↓
HTTPS request
~~~

Nếu cần strict security, dùng HTTPS only nhưng phải bảo đảm client/app hỗ trợ.

## 36. Origin protocol policy

Viewer-to-CloudFront và CloudFront-to-origin là hai chặng riêng.

~~~text
Client ── HTTPS ──> CloudFront ── HTTP/HTTPS ──> Origin
~~~

Có thể cấu hình:

- HTTP only tới origin.
- HTTPS only tới origin.
- Match viewer trong behavior hỗ trợ.

Nếu requirement end-to-end encryption, dùng HTTPS tới origin và kiểm tra certificate/hostname.

---

# Phần VIII — CloudFront Functions và Lambda@Edge

## 37. Edge computation

CloudFront cho phép chạy logic gần user.

Use case:

- Redirect.
- Rewrite URL.
- Normalize header.
- Geolocation-based behavior.
- A/B test đơn giản.
- Security/header manipulation.
- JWT/token logic trong pattern phù hợp.

## 38. CloudFront Functions

Phù hợp với logic nhẹ, đơn giản và latency thấp ở edge:

- Viewer request.
- Viewer response.
- URL rewrite.
- Header manipulation.
- Redirect.
- Normalize request.

Không phù hợp với:

- Logic nặng.
- Truy cập database.
- Thời gian chạy dài.
- Dependency phức tạp.
- Xử lý backend đầy đủ.

## 39. Lambda@Edge

Phù hợp với logic edge phức tạp hơn theo event hỗ trợ:

- Viewer request.
- Viewer response.
- Origin request.
- Origin response.

Có thể làm nhiều việc hơn nhưng:

- Deploy phức tạp hơn.
- Cần quản lý version/replication.
- Có latency/cost khác.
- Debug khó hơn.
- Cần hiểu Region/deployment behavior.

Ở SAA-C03, hãy chọn theo wording:

- Logic rewrite/redirect nhẹ: CloudFront Functions.
- Logic edge phức tạp hơn hoặc origin event: Lambda@Edge.
- Business logic chính: Lambda/API/backend, không nhồi vào edge nếu không cần.

---

# Phần IX — CloudFront security

## 40. WAF

WAF có thể bảo vệ CloudFront distribution:

~~~text
User
  ↓
CloudFront
  ↓
AWS WAF
  ↓
Origin
~~~

WAF phù hợp:

- SQL injection.
- XSS.
- Rate-based rule.
- IP allow/block.
- Managed rule group.
- Geo restriction nâng cao.
- Custom request rule.

WAF không thay thế:

- OAC.
- S3 bucket policy.
- IAM.
- Security Group.
- Origin authentication.
- Application authorization.

## 41. Origin Shield

Origin Shield tạo một lớp caching tập trung giữa edge và origin, có thể giảm origin fetch và tăng cache efficiency trong một số architecture.

Cân nhắc:

- Origin load.
- Multi-edge request pattern.
- Cost.
- Region của Origin Shield.
- Latency.
- Cache behavior.

Không bật chỉ vì “thêm cache luôn tốt”. Đánh giá origin request, traffic và cost.

## 42. Geo restriction

CloudFront có thể giới hạn content theo country trong pattern hỗ trợ.

Phân biệt:

- CloudFront geo restriction: chặn/cho phép truy cập content ở edge.
- Route 53 geolocation routing: chọn DNS endpoint theo vị trí.
- WAF geo rule: request filtering theo rule.
- Application authorization: quyết định business access.

Có thể dùng nhiều lớp nhưng phải tránh logic chồng chéo khó debug.

## 43. Response headers policy

CloudFront có thể thêm hoặc quản lý response headers như:

- CORS.
- Security headers.
- Cache-related headers.
- Custom response headers.

Cần test:

- Browser behavior.
- CORS preflight.
- Cache key.
- Header từ origin có bị override không.
- Không expose sensitive header.

## 44. DDoS và Shield

CloudFront là một thành phần trong kiến trúc edge defense. Shield cung cấp DDoS protection theo dịch vụ/tier phù hợp.

Không nên kết luận CloudFront tự giải quyết:

- Application abuse.
- Credential attack.
- SQL injection.
- Authorization.
- Bot abuse.

Kết hợp:

~~~text
Route 53
  ↓
CloudFront
  ↓
WAF
  ↓
ALB/API/S3
  ↓
Security Group/IAM/Bucket policy
~~~

---

# Phần X — CloudFront với API

## 45. Cache API

Có thể đặt CloudFront trước API, nhưng phải hiểu cacheability.

Cache được khi:

- Response public.
- Không phụ thuộc user.
- TTL chấp nhận được.
- Query/header/cookie được đưa vào cache key đúng.
- API idempotent/read-oriented.

Không cache hoặc TTL rất thấp khi:

- Response chứa dữ liệu riêng.
- Phụ thuộc Authorization.
- Có mutation.
- Freshness gần realtime.
- Có side effect.
- Cache key khó mô tả đầy đủ.

## 46. CORS

Nếu browser gọi API ở domain khác:

~~~text
Frontend domain
  ↓
CloudFront
  ↓
API origin
~~~

Cần kiểm tra:

- OPTIONS method.
- Access-Control-Allow-Origin.
- Access-Control-Allow-Headers.
- Access-Control-Allow-Methods.
- Credentials/cookie behavior.
- Cache key của preflight response.
- Response headers từ origin hoặc CloudFront policy.

CORS là browser enforcement; nó không thay thế authentication.

## 47. Query strings

Query string có thể:

- Được bỏ khỏi cache key.
- Được include toàn bộ.
- Được include một số parameter.
- Được forward tới origin.

Ví dụ static asset:

~~~text
/app.js?v=1
/app.js?v=2
~~~

Nếu version query chỉ dùng để cache busting, cần chọn policy có chủ đích. Nếu query là filter business làm response khác nhau, phải include trong cache key hoặc không cache.

---

# Phần XI — Monitoring và troubleshooting

## 48. Các trạng thái cache quan trọng

Khi debug CloudFront, quan sát:

- Hit.
- Miss.
- RefreshHit.
- Error.
- Redirect.
- Origin latency.
- Bytes downloaded.
- Requests.
- 4xx/5xx.
- Origin response.

Cache hit ratio cao là tốt cho cost/performance, nhưng phải kết hợp kiểm tra correctness/security.

## 49. CloudFront không trả object mới

Checklist:

1. Object có tồn tại ở origin không?
2. Path và case có đúng không?
3. Behavior match path đúng không?
4. Cache key có query/header/cookie bất ngờ không?
5. Object đang bị cache phiên bản cũ không?
6. TTL còn hiệu lực không?
7. Invalidation đã hoàn tất chưa?
8. Origin access policy/OAC đúng chưa?
9. S3 object permission/KMS permission đúng chưa?
10. Origin protocol/host header đúng chưa?

## 50. CloudFront trả response của user khác

Đây là lỗi security nghiêm trọng. Kiểm tra:

- Cache policy.
- Authorization header.
- Cookie.
- Query string.
- Host header.
- Behavior.
- Signed URL/cookie.
- Response Cache-Control.
- Có đang cache API private không.

Nếu không mô hình hóa được cache key an toàn, tắt cache cho response riêng tư.

## 51. CloudFront 403

Nguyên nhân có thể:

- S3 bucket policy không cho CloudFront/OAC.
- Object không tồn tại nhưng origin trả 403.
- OAC signing behavior sai.
- WAF block.
- Geo restriction.
- Signed URL/cookie hết hạn.
- Origin yêu cầu header/auth chưa forward.
- CloudFront distribution không có permission tới KMS/S3.
- Request path sai.

## 52. CloudFront 502/504

Nguyên nhân có thể:

- Origin không reachable.
- ALB/NLB health hoặc listener sai.
- TLS handshake tới origin fail.
- Origin timeout.
- Security Group/NACL chặn CloudFront/origin path.
- DNS origin sai.
- Application response quá chậm.
- Header/method không được origin chấp nhận.
- Origin certificate hostname mismatch.

## 53. CloudFront request không tới behavior mong muốn

1. Path pattern có đúng không?
2. Behavior cụ thể có priority đúng không?
3. Có ký tự URL encoding khác dự kiến không?
4. Query string có ảnh hưởng path không?
5. Default behavior đang bắt request không?
6. Distribution đã deploy configuration mới chưa?
7. Edge location còn cache behavior cũ không?

---

# Phần XII — Architecture patterns

## 54. Static website với private S3

~~~text
User
  ↓
Route 53 Alias
  ↓
CloudFront
  ↓
OAC
  ↓
Private S3 bucket
~~~

Security:

- Block Public Access.
- Bucket policy chỉ cho CloudFront distribution.
- OAC ký request.
- HTTPS custom domain.
- WAF nếu cần.
- Versioned asset.

## 55. Dynamic application với ALB

~~~text
User
  ↓
Route 53
  ↓
CloudFront
  ├── /static/* → S3
  └── /api/*    → ALB
                     ↓
                Private EC2/ECS
~~~

Behavior:

- Static path TTL dài.
- API path TTL thấp/no-cache.
- API methods phù hợp.
- Header/cookie/query forward đúng.
- ALB/target SG không mở public ngoài requirement.

## 56. Private download

~~~text
User login
  ↓
Application authorization
  ↓
Signed URL/cookie
  ↓
CloudFront
  ↓
OAC
  ↓
Private S3
~~~

Use case:

- File cá nhân.
- Video private.
- Document download.
- Content có expiry.

## 57. CloudFront origin failover

~~~text
CloudFront
├── Primary S3/ALB origin
└── Secondary S3/ALB origin
~~~

Cần:

- Failover criteria.
- Secondary content/application.
- Data synchronization.
- Behavior đúng.
- Error response.
- Test failure.
- Observability.

## 58. CloudFront và Global Accelerator

~~~text
CloudFront:
  HTTP/HTTPS content
  Cache tại edge
  S3/ALB/custom origin

Global Accelerator:
  TCP/UDP/HTTP application
  Static anycast IP
  Không cache content
  ALB/NLB/endpoint
~~~

Nếu đề nói “cache static content gần user”, chọn CloudFront. Nếu nói “static IP, TCP/UDP, global network path và không cần cache”, cân nhắc Global Accelerator.

---

# Phần XIII — Lab thực hành

## 59. Lab 1: CloudFront với private S3

1. Tạo S3 bucket.
2. Bật Block Public Access.
3. Upload static object.
4. Tạo CloudFront distribution.
5. Chọn S3 origin.
6. Tạo và gắn OAC.
7. Cập nhật bucket policy chỉ cho distribution.
8. Truy cập object qua CloudFront.
9. Truy cập trực tiếp S3 và xác nhận bị chặn.
10. Kiểm tra response headers và cache status.

## 60. Lab 2: Cache behavior cho static/API

Tạo:

~~~text
/static/*
  → S3
  → TTL dài
  → GET/HEAD

/api/*
  → ALB
  → TTL thấp hoặc không cache
  → forward header/query cần thiết
~~~

Thực hiện:

1. Tạo hai origin.
2. Tạo default behavior.
3. Tạo behavior cho static path.
4. Tạo behavior cho API path.
5. Kiểm tra behavior priority.
6. Gửi request hai path.
7. Quan sát origin access log.
8. Kiểm tra cache hit/miss.
9. Kiểm tra API response không cache nhầm.

## 61. Lab 3: Versioned object

1. Upload app.v1.js.
2. Cấu hình TTL dài.
3. Truy cập qua CloudFront.
4. Upload app.v2.js.
5. Sửa HTML tham chiếu v2.
6. Không invalidation toàn distribution.
7. Quan sát request mới.
8. Giữ v1 để rollback.
9. Đặt lifecycle cho asset cũ.

## 62. Lab 4: Invalidation

1. Tạo object có tên cố định.
2. Cache object qua CloudFront.
3. Thay object ở S3.
4. Gọi lại và quan sát object cũ.
5. Tạo invalidation cho path cụ thể.
6. Chờ trạng thái completed.
7. Kiểm tra object mới.
8. Ghi chú cost/limit.
9. So sánh với versioned filename.

## 63. Lab 5: Signed URL

1. Giữ S3 private.
2. Tạo cơ chế cấp signed URL ở application.
3. Cấu hình expiry ngắn.
4. Truy cập bằng URL hợp lệ.
5. Truy cập sau khi hết hạn.
6. Thử sửa path hoặc signature.
7. Kiểm tra logging.
8. Thu hồi/rotate key theo design.

## 64. Lab 6: CloudFront trước ALB

1. Tạo ALB origin.
2. Tạo behavior /api/*.
3. Đặt TTL thấp/no-cache cho API.
4. Cấu hình forwarded headers/query/cookie cần thiết.
5. Tạo behavior /static/* với TTL dài.
6. Kiểm tra response của nhiều user.
7. Xác nhận dữ liệu private không bị cache chung.
8. Kiểm tra ALB logs và CloudFront logs.

## 65. Lab 7: WAF và response headers

1. Gắn WAF web ACL vào distribution.
2. Tạo rate-based rule trong môi trường test.
3. Thêm security headers policy.
4. Gửi request bình thường.
5. Gửi request vượt rate.
6. Kiểm tra WAF log.
7. Kiểm tra response headers.
8. Xóa rule/distribution test sau lab.

## 66. Dọn dẹp lab

- [ ] Xóa CloudFront distribution hoặc disable rồi xóa.
- [ ] Xóa OAC không dùng.
- [ ] Xóa S3 bucket/object test.
- [ ] Xóa invalidation test nếu không cần theo dõi.
- [ ] Xóa WAF web ACL.
- [ ] Xóa ACM certificate test.
- [ ] Tắt access logging destination hoặc đặt lifecycle.
- [ ] Kiểm tra ALB/NAT/EC2 có còn chạy không.
- [ ] Kiểm tra billing.

---

# Phần XIV — Bẫy AWS SAA-C03

- CloudFront là CDN/cache, không phải ALB.
- CloudFront không tự làm origin private; cần OAC/bucket policy.
- Block Public Access không tự cấp quyền cho CloudFront.
- OAC không thay bucket policy.
- Cache policy quyết định cache key.
- Origin request policy quyết định forwarding.
- Forward header/cookie/query không có nghĩa cache key đã chứa input đó.
- Không cache API private nếu response khác nhau theo user mà cache key không phân biệt.
- Signed URL dành URL/object cụ thể.
- Signed cookie phù hợp nhiều object/path.
- Invalidation không phải giải pháp tốt nhất cho static asset thay đổi thường xuyên.
- Versioned filename thường tốt hơn purge liên tục.
- TTL thấp không bảo đảm content thay đổi tức thì.
- Origin failover khác Route 53 DNS failover.
- CloudFront Functions phù hợp edge logic nhẹ.
- Lambda@Edge phù hợp edge logic phức tạp hơn.
- Global Accelerator không cache content.
- Route 53 chỉ trả DNS answer.
- WAF không thay authentication hoặc IAM.
- ALB origin vẫn cần Security Group và health check đúng.
- S3 SSE-KMS cần kiểm tra cả S3 permission và KMS key policy.

---

# Phần XV — Câu hỏi tự luyện

## Câu 1

Một website có static assets trong S3 và bucket không được public. Cách thiết kế phù hợp?

A. Public-read bucket  
B. CloudFront với OAC và private S3 bucket  
C. NAT Gateway trước S3  
D. NACL deny Internet

Đáp án: B.

## Câu 2

Một API response phụ thuộc Authorization cookie nhưng CloudFront cache key không chứa cookie. Rủi ro là gì?

A. Cache hit ratio thấp  
B. User có thể nhận response của user khác  
C. S3 object bị xóa  
D. Route 53 không resolve

Đáp án: B.

## Câu 3

Static file được cập nhật thường xuyên và muốn tránh invalidation rộng. Giải pháp tốt?

A. Dùng versioned filename  
B. Tăng TTL vô hạn cho tên file cố định  
C. Public bucket  
D. Dùng NACL

Đáp án: A.

## Câu 4

Một user cần download một file private trong 10 phút. Chọn gì?

A. Signed URL  
B. Public bucket  
C. CNAME  
D. Route 53 geolocation

Đáp án: A.

## Câu 5

Một user cần truy cập hàng trăm private video segment trong một phiên. Chọn gì?

A. Signed cookie  
B. Một public IP cho mỗi file  
C. NACL  
D. MX record

Đáp án: A.

## Câu 6

CloudFront trả 403 khi đọc S3 private origin. Kiểm tra đầu tiên?

A. OAC và bucket policy  
B. RDS read replica  
C. NAT Gateway  
D. Route 53 weighted record

Đáp án: A.

## Câu 7

Application cần /static/* tới S3 và /api/* tới ALB. Dùng gì?

A. CloudFront behaviors và nhiều origins  
B. Chỉ một A record  
C. Chỉ NLB  
D. Chỉ S3 lifecycle

Đáp án: A.

## Câu 8

Cần static anycast IP cho global TCP application nhưng không cần cache. Chọn gì?

A. CloudFront  
B. Global Accelerator  
C. S3 Transfer Acceleration bắt buộc  
D. Route 53 TXT

Đáp án: B.

## Câu 9

CloudFront cần forward Authorization header tới origin nhưng response vẫn không được cache theo user. Thiết kế nào an toàn hơn?

A. Cache chung response bất kể Authorization  
B. Không cache response riêng tư hoặc đưa input phân biệt user vào cache key  
C. Xóa Security Group  
D. Tăng TTL

Đáp án: B.

## Câu 10

CloudFront đang trả object cũ sau khi S3 object đã thay đổi. Các lựa chọn phù hợp?

A. Invalidation có kiểm soát hoặc versioned filename  
B. Tạo thêm RDS  
C. Xóa Route 53  
D. Đổi Security Group

Đáp án: A.

## Câu 11

Cần redirect HTTP sang HTTPS ở edge. Chọn gì?

A. Viewer protocol policy redirect  
B. CNAME record  
C. S3 lifecycle  
D. NACL

Đáp án: A.

## Câu 12

Cần chạy URL rewrite đơn giản ở edge với overhead thấp. Chọn gì?

A. CloudFront Functions  
B. RDS  
C. NAT Gateway  
D. Transit Gateway

Đáp án: A.

---

# Phần XVI — Checklist ghi nhớ

## CloudFront fundamentals

- [ ] Hiểu distribution, edge location, origin và behavior.
- [ ] Biết cache hit/miss.
- [ ] Biết origin group và failover.
- [ ] Biết Route 53 Alias tới CloudFront.
- [ ] Phân biệt CloudFront với ALB/NLB/Global Accelerator.

## Cache

- [ ] Hiểu cache key.
- [ ] Phân biệt cache policy và origin request policy.
- [ ] Biết TTL.
- [ ] Biết cache static content.
- [ ] Biết khi nào không cache API/private response.
- [ ] Hiểu query/header/cookie ảnh hưởng cache.
- [ ] Biết invalidation và versioned object.
- [ ] Biết cache hit ratio không quan trọng hơn correctness/security.

## Private S3

- [ ] Bật Block Public Access.
- [ ] Dùng OAC cho private S3 origin.
- [ ] Cập nhật bucket policy.
- [ ] Kiểm tra S3 và KMS permission.
- [ ] Xác nhận user không bypass trực tiếp S3.
- [ ] Phân biệt S3 website endpoint và REST origin.

## Security

- [ ] Dùng ACM cho HTTPS custom domain.
- [ ] Chọn viewer/origin protocol đúng.
- [ ] Gắn WAF khi cần.
- [ ] Phân biệt signed URL và signed cookie.
- [ ] Không cache private data sai cache key.
- [ ] Hiểu OAC không thay bucket policy.
- [ ] Hiểu WAF không thay authentication.

## Troubleshooting

- [ ] Kiểm tra behavior/path.
- [ ] Kiểm tra cache key.
- [ ] Kiểm tra TTL/invalidation.
- [ ] Kiểm tra origin access.
- [ ] Kiểm tra OAC/bucket policy.
- [ ] Kiểm tra WAF.
- [ ] Kiểm tra TLS/certificate.
- [ ] Kiểm tra ALB/target health.
- [ ] Kiểm tra CloudFront/ALB/S3 logs.

---

## Tóm tắt cuối

1. CloudFront là CDN và edge cache, không phải load balancer.
2. Distribution chứa origin, behavior, policy và security configuration.
3. Origin là nguồn content khi cache miss.
4. Behavior route request theo path tới origin và policy phù hợp.
5. Cache policy quyết định cache key và TTL.
6. Origin request policy quyết định request forward tới origin.
7. Nếu response khác nhau theo user, cache key phải phân biệt user hoặc response không nên cache.
8. Versioned filename thường tốt hơn invalidation cho static asset.
9. S3 private origin nên dùng OAC, Block Public Access và bucket policy giới hạn distribution.
10. Signed URL phù hợp object/link cụ thể; signed cookie phù hợp nhiều object/path.
11. CloudFront Functions dành edge logic nhẹ; Lambda@Edge dành logic phức tạp hơn.
12. WAF bảo vệ HTTP request nhưng không thay IAM/authentication.
13. CloudFront có thể đứng trước S3, ALB, API Gateway hoặc custom origin.
14. Route 53 phân giải domain; CloudFront xử lý edge request/cache.
15. Global Accelerator cung cấp static anycast IP và global network path nhưng không cache content.
16. Troubleshooting phải đi theo thứ tự behavior → cache → access → origin → security → logs.

## Tài liệu tham khảo chính thức

- [Amazon CloudFront Developer Guide](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/Introduction.html)
- [CloudFront distributions](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/distribution-working-with.html)
- [CloudFront origins](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/DownloadDistS3AndCustomOrigins.html)
- [CloudFront behaviors](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/DownloadDistValuesCacheBehavior.html)
- [CloudFront cache key](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/controlling-the-cache-key.html)
- [CloudFront cache policies](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/using-managed-cache-policies.html)
- [CloudFront origin request policies](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/controlling-origin-requests.html)
- [CloudFront Origin Access Control](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/private-content-restricting-access-to-s3.html)
- [CloudFront signed URLs and cookies](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/private-content-signed-urls.html)
- [CloudFront invalidation](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/Invalidation.html)
- [CloudFront Functions](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/edge-functions.html)
- [Lambda@Edge](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/lambda-at-the-edge.html)
- [AWS WAF with CloudFront](https://docs.aws.amazon.com/waf/latest/developerguide/cloudfront-features.html)
- [AWS Global Accelerator](https://docs.aws.amazon.com/global-accelerator/latest/dg/what-is-global-accelerator.html)


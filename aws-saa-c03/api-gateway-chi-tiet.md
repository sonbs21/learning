# Amazon API Gateway chuyên sâu — AWS SAA-C03

> Tài liệu này tách riêng và mở rộng phần API Gateway từ bài Tuần 9. Mục tiêu là hiểu API Gateway như một lớp cửa vào được quản lý cho backend, biết một request đi qua hệ thống như thế nào, chọn đúng REST API, HTTP API hoặc WebSocket API, đồng thời xử lý đúng authentication, authorization, throttling, CORS, caching, private integration, deployment và observability.

## 1. API Gateway là gì?

Amazon API Gateway là dịch vụ được quản lý dùng để tạo, công bố, bảo vệ, theo dõi và vận hành API.

Một client thường không nên gọi trực tiếp Lambda, service nội bộ hoặc AWS service. API Gateway đứng trước backend và cung cấp một endpoint ổn định:

~~~text
Web / Mobile / Partner / Service
              │
              ▼
        API Gateway
        ├── TLS
        ├── routing
        ├── authentication
        ├── authorization
        ├── validation
        ├── throttling
        ├── transformation
        └── logging/metrics
              │
              ▼
 Lambda / HTTP service / AWS service / private backend
~~~

API Gateway có thể giải quyết:

- Public endpoint hoặc private endpoint.
- Route theo HTTP method và path.
- Authentication và authorization.
- Giới hạn tốc độ để bảo vệ backend.
- Biến đổi request/response.
- Tích hợp với Lambda, HTTP endpoint và AWS service.
- Custom domain và TLS.
- Stage và deployment.
- Access log, execution log, metric và tracing.
- WebSocket connection cho giao tiếp hai chiều.

API Gateway không phải nơi chạy business logic. Nó cũng không thay database, queue, CDN hay application code.

Cách ghi nhớ:

> API Gateway quản lý cửa vào của API; backend thực thi nghiệp vụ.

---

# Phần I — Mô hình tư duy

## 2. Một request đi qua API Gateway như thế nào?

~~~text
1. Client gửi HTTPS request
2. DNS phân giải custom domain hoặc execute-api endpoint
3. API Gateway chọn API, stage và route/method
4. Kiểm tra resource policy và authorization
5. Áp dụng throttling/quota
6. Validate hoặc transform request nếu đã cấu hình
7. Gọi integration backend
8. Nhận kết quả hoặc lỗi từ backend
9. Transform response nếu cần
10. Ghi log/metric rồi trả HTTP response cho client
~~~

Ví dụ:

~~~text
POST https://api.example.com/orders
Authorization: Bearer <token>
Content-Type: application/json

{
  "productId": "p-100",
  "quantity": 2
}
~~~

Luồng có thể là:

~~~text
Client
  ↓ HTTPS
Route 53
  ↓ alias
API Gateway custom domain
  ↓ POST /orders
JWT authorizer
  ↓ allow
Lambda integration
  ↓
DynamoDB
  ↓
201 Created
~~~

Khi lỗi xảy ra, phải xác định nó nằm ở lớp nào:

- DNS/TLS.
- API route.
- Authorization.
- Throttling.
- Request validation.
- Integration permission.
- Backend timeout/error.
- Response format.
- CORS ở browser.
- Logging hoặc deployment chưa cập nhật.

## 3. API Gateway và Load Balancer khác nhau thế nào?

| Nhu cầu | API Gateway | ALB |
|---|---|---|
| API management | Mạnh | Cơ bản |
| JWT/Lambda authorizer | Có theo API type | OIDC/Cognito auth theo tính năng ALB |
| Usage plan và API key | REST API | Không phải chức năng chính |
| Request transformation | Có | Hạn chế |
| Lambda integration | Có | Có |
| Path/host routing | Có | Có |
| WebSocket | Có WebSocket API riêng | Hỗ trợ WebSocket pass-through |
| Target EC2/ECS/IP | Qua integration phù hợp | Trực tiếp |
| Pricing model | Theo request/connection và feature | Theo thời gian và capacity unit |
| Phù hợp | Managed API facade | Load balancing cho web/service |

Không có đáp án tuyệt đối. Nếu chỉ cần route HTTP tới ECS service với traffic lớn, ALB có thể đơn giản và kinh tế hơn. Nếu cần API product, authorizer, throttling, validation, transformation hoặc serverless integration, API Gateway thường phù hợp hơn.

## 4. API Gateway không tự làm mọi thứ

API Gateway không tự đảm bảo:

- Business operation idempotent.
- Database scale theo traffic.
- Lambda không bị throttled.
- Backend không timeout.
- Secret được quản lý đúng.
- Payload hợp lệ về mặt nghiệp vụ.
- Multi-Region disaster recovery.
- Client retry an toàn.
- Không có duplicate request.

Nó bảo vệ và điều phối cửa vào, nhưng kiến trúc phía sau vẫn phải được thiết kế tốt.

---

# Phần II — Ba loại API

## 5. REST API

REST API là sản phẩm API Gateway có bộ tính năng quản trị API phong phú nhất.

Phù hợp khi cần:

- API key và usage plan.
- Per-client quota hoặc throttling.
- Request validation.
- Mapping template phức tạp.
- API Gateway cache.
- Edge-optimized endpoint.
- Private API endpoint.
- AWS WAF integration theo khả năng hỗ trợ.
- Gateway response tùy chỉnh.
- Khả năng tương thích với REST API configuration hiện có.

Tên REST API trong API Gateway là tên một sản phẩm cụ thể, không chỉ nói chung về phong cách REST.

## 6. HTTP API

HTTP API là lựa chọn gọn hơn cho API HTTP phổ biến. Nó thường có chi phí và độ phức tạp thấp hơn REST API.

Phù hợp khi:

- Backend là Lambda hoặc HTTP service.
- Cần routing cơ bản.
- Cần JWT authorizer.
- Cần IAM hoặc Lambda authorizer theo khả năng hỗ trợ.
- Không cần API key, usage plan, cache và mapping nâng cao của REST API.
- Muốn triển khai serverless API đơn giản.

Không chọn REST API chỉ vì endpoint dùng HTTP. Hãy chọn theo feature requirement.

## 7. WebSocket API

WebSocket API dành cho kết nối hai chiều tồn tại lâu hơn mô hình request-response thông thường.

Use case:

- Chat.
- Live notification.
- Collaborative editing.
- Dashboard cập nhật gần real time.
- Game hoặc signaling.
- Backend chủ động gửi message về client.

Các route đặc biệt:

- $connect: khi client tạo kết nối.
- $disconnect: khi kết nối đóng.
- $default: khi không match route khác.
- Custom route: chọn theo route selection expression.

~~~text
Client
   │ WebSocket connect
   ▼
$connect route → Lambda auth/setup
   │
   ├── message action=sendMessage
   │        ↓
   │   custom route → backend
   │
   └── backend gọi Management API
            ↓
       push message về connectionId
~~~

API Gateway không tự lưu business session. Thường phải lưu connectionId và metadata ở DynamoDB hoặc storage phù hợp. Client phải hỗ trợ reconnect vì connection không tồn tại vĩnh viễn.

Với WebSocket API, IAM hoặc Lambda authorizer được kiểm tra khi thiết lập kết nối ở route $connect. Lambda authorizer của WebSocket chỉ gắn vào $connect; quyền đối với từng loại message sau khi kết nối vẫn phải được backend kiểm tra dựa trên identity/context đã lưu và nội dung message.

## 8. Bảng chọn nhanh

| Requirement | REST API | HTTP API | WebSocket API |
|---|---:|---:|---:|
| HTTP request-response | Có | Có | Không phải mục đích chính |
| Hai chiều lâu dài | Không | Không | Có |
| Lambda integration | Có | Có | Có |
| HTTP backend | Có | Có | Có theo integration |
| JWT authorizer tích hợp trực tiếp | Không theo cùng mô hình HTTP API | Có | Không; dùng IAM hoặc Lambda authorizer ở $connect |
| Cognito User Pool authorizer | Có | Dùng JWT authorizer với issuer phù hợp | Không tích hợp trực tiếp; có thể dùng Lambda authorizer |
| API key/usage plan | Có | Không | Không phải lựa chọn auth; kiểm tra hỗ trợ hiện hành nếu cần metering |
| API Gateway cache | Có | Không | Không |
| Private API endpoint | Có | Không theo cùng loại endpoint REST private |
| Edge-optimized endpoint | Có | Không | Không |
| Mapping/validation nâng cao | Mạnh | Gọn hơn | Theo message route |
| Chi phí/độ đơn giản | Cao hơn | Thường thấp hơn | Theo message/connection |

Feature của AWS có thể thay đổi. Khi một tính năng quyết định kiến trúc, kiểm tra bảng so sánh chính thức thay vì dựa vào trí nhớ.

---

# Phần III — Endpoint và phạm vi truy cập

## 9. Edge-optimized REST endpoint

Edge-optimized endpoint đưa request qua mạng edge do AWS quản lý trước khi tới API Gateway ở Region.

Phù hợp khi client phân tán toàn cầu và muốn endpoint REST đơn giản có điểm vào gần người dùng.

Lưu ý:

- Đây không phải CloudFront distribution do bạn toàn quyền cấu hình.
- Nếu cần kiểm soát CloudFront behavior, cache policy, WAF hoặc nhiều origin theo cách riêng, có thể dùng Regional API làm origin cho CloudFront do bạn quản lý.
- Custom domain và certificate phải theo yêu cầu Region của loại endpoint.

## 10. Regional endpoint

Regional endpoint phục vụ trực tiếp tại Region nơi API được triển khai.

Phù hợp khi:

- Client chủ yếu cùng Region hoặc gần Region.
- Dùng Route 53 cho routing nhiều Region.
- Muốn đặt CloudFront distribution do mình quản lý phía trước.
- Muốn kiểm soát kiến trúc edge riêng.
- Dùng HTTP API, vốn sử dụng Regional endpoint model.

~~~text
Global users
   ↓
CloudFront do team quản lý
   ↓
Regional API Gateway
   ↓
Backend trong Region
~~~

## 11. Private REST API endpoint

Private REST API chỉ được truy cập qua interface VPC endpoint dành cho API Gateway.

~~~text
EC2/ECS/Lambda trong VPC
          │
          ▼
Interface VPC Endpoint
          │
          ▼
Private REST API
          │
          ▼
Integration backend
~~~

Cần phân biệt:

- Private API: client gọi API qua VPC endpoint; đây là cách expose API.
- Private integration: API Gateway gọi backend riêng trong VPC; đây là cách kết nối backend.
- Hai khái niệm có thể dùng độc lập hoặc kết hợp.

Private API thường cần:

- Interface VPC endpoint.
- Private DNS hoặc URL phù hợp.
- API resource policy.
- VPC endpoint policy.
- Security Group của endpoint.
- Route/DNS đúng.
- IAM/authorizer nếu yêu cầu.

## 12. Disable default execute-api endpoint

Khi dùng custom domain, có thể tắt default execute-api endpoint nếu muốn buộc client đi qua domain và control plane đã thiết kế.

Nếu không tắt, client có thể bỏ qua một số lớp đặt trước custom domain, chẳng hạn CloudFront distribution riêng. Tuy nhiên phải kiểm tra quy trình deployment và khả năng quản trị trước khi tắt để tránh tự khóa đường truy cập.

---

# Phần IV — Resources, methods, routes, stages và deployments

## 13. Resource và method trong REST API

REST API tổ chức endpoint theo resource tree và HTTP method:

~~~text
/
├── /orders
│   ├── GET
│   └── POST
└── /orders/{orderId}
    ├── GET
    ├── PATCH
    └── DELETE
~~~

Mỗi method có thể có:

- Authorization.
- API key requirement.
- Request parameters.
- Request model/validator.
- Integration request.
- Integration response.
- Method response.

Nên dùng HTTP semantics hợp lý:

- GET: đọc, không tạo side effect.
- POST: tạo hoặc command.
- PUT: thay thế/idempotent update.
- PATCH: cập nhật một phần.
- DELETE: xóa.
- Status code phải phản ánh kết quả.

## 14. Route trong HTTP API

HTTP API dùng route key gồm HTTP method và path:

~~~text
GET /orders
POST /orders
GET /orders/{id}
ANY /{proxy+}
$default
~~~

Route cụ thể thường dễ kiểm soát authorization và observability hơn một catch-all route. ANY /{proxy+} tiện cho framework nhưng có thể đẩy toàn bộ routing, validation và security vào backend.

## 15. Stage

Stage là một reference có thể gọi được tới deployment/configuration của API.

Ví dụ:

- dev.
- staging.
- prod.
- v1.

URL mặc định thường chứa stage tùy loại API và cấu hình:

~~~text
https://{api-id}.execute-api.{region}.amazonaws.com/prod/orders
~~~

Stage có thể chứa:

- Stage variables đối với feature được hỗ trợ.
- Logging.
- Metrics.
- Throttling.
- Cache đối với REST API.
- Canary settings đối với REST API.
- Auto-deploy đối với HTTP API.
- Deployment reference.

Không nên dùng stage như giải pháp duy nhất để cô lập production. Account và deployment pipeline riêng thường tạo boundary tốt hơn.

## 16. Deployment

Deployment là snapshot có thể triển khai của API. Với REST API, thay đổi resource/method/integration thường phải được deploy lại tới stage mới có hiệu lực.

Lỗi kinh điển:

> Console đã sửa đúng nhưng client vẫn thấy behavior cũ vì chưa redeploy stage.

Với HTTP API có thể bật auto-deploy. Auto-deploy tiện cho development nhưng production thường cần pipeline, review và rollback rõ ràng.

## 17. Custom domain và API mapping

Thay vì URL execute-api khó nhớ, dùng:

~~~text
https://api.example.com
~~~

Có thể map:

~~~text
api.example.com/orders  → Orders API
api.example.com/users   → Users API
api.example.com/v1      → stage/version phù hợp
~~~

Các thành phần:

- Custom domain trong API Gateway.
- ACM certificate.
- API mapping hoặc base path mapping.
- Route 53 alias record.
- TLS policy.
- Ownership/renewal certificate.

Certificate Region phụ thuộc loại endpoint. Không áp dụng quy tắc CloudFront hoặc Regional một cách máy móc cho mọi API type.

---

# Phần V — Integration

## 18. Integration là gì?

Integration xác định API Gateway gọi backend nào và chuyển dữ liệu ra sao.

Các backend phổ biến:

- Lambda.
- Public HTTP endpoint.
- Private HTTP service trong VPC.
- AWS service.
- Mock integration trong REST API.

~~~text
Method/Route
    ↓
Integration
    ├── Lambda
    ├── HTTP endpoint
    ├── VPC Link
    ├── AWS service
    └── Mock
~~~

## 19. Lambda proxy integration

Proxy integration gửi gần như toàn bộ request context cho Lambda. Lambda chịu trách nhiệm parse input và tạo response đúng format.

Request có thể chứa:

- HTTP method.
- Path.
- Headers.
- Query string.
- Path parameters.
- Body.
- Request context.
- Authorizer context.

Response tư duy:

~~~json
{
  "statusCode": 200,
  "headers": {
    "Content-Type": "application/json"
  },
  "body": "{\"id\":\"o-123\",\"status\":\"CREATED\"}"
}
~~~

Body trong response proxy thường phải đúng kiểu/format được payload version yêu cầu. Nếu Lambda trả sai cấu trúc, client có thể nhận lỗi 5xx dù business code đã chạy.

Ưu điểm:

- Cấu hình nhanh.
- Phù hợp framework.
- Ít mapping template.
- Backend kiểm soát response.

Trade-off:

- Backend phụ thuộc event schema API Gateway.
- Validation/transformation chuyển nhiều vào code.
- Dễ trả sai status code hoặc body.
- Phải xử lý CORS/error nhất quán.

HTTP API có payload format version khác nhau; code phải khớp version đã cấu hình.

## 20. Lambda non-proxy/custom integration

REST API custom integration cho phép mapping request trước khi gọi Lambda và mapping response sau khi Lambda trả về.

Phù hợp khi:

- Cần giữ public contract khác backend contract.
- Cần map legacy payload.
- Muốn chọn status code từ integration response.
- Cần transformation bằng Velocity Template Language.

Trade-off:

- Cấu hình phức tạp.
- Mapping template khó debug.
- Cần test kỹ escaping, null, binary và error path.
- Có thể tạo logic khó bảo trì trong gateway.

Nguyên tắc: transformation nhỏ ở gateway; business logic nằm trong code/service.

## 21. HTTP proxy integration

API Gateway chuyển request đến HTTP backend và chuyển response về client với ít biến đổi.

Backend có thể là:

- Public web service.
- ALB/NLB endpoint theo kiến trúc.
- Service trong VPC qua VPC Link.
- ECS/EKS application.

API Gateway không làm backend trở nên an toàn nếu backend vẫn public và có thể bị gọi trực tiếp. Cần Security Group, private integration, secret/header validation hoặc policy phù hợp.

## 22. AWS service integration

API Gateway có thể gọi một số AWS service trực tiếp mà không cần Lambda trung gian.

Ví dụ kiến trúc:

~~~text
Client → API Gateway → SQS
Client → API Gateway → Step Functions
Client → API Gateway → Kinesis
~~~

Lợi ích:

- Ít code.
- Giảm Lambda hop/cost/cold start.
- Dùng IAM service role rõ ràng.
- Phù hợp thao tác mapping đơn giản.

Cần cấu hình:

- IAM role để API Gateway gọi service.
- Request mapping.
- Response/error mapping.
- Input validation.
- Idempotency và retry semantics.

Không dùng Lambda chỉ để gọi nguyên xi một AWS API nếu direct integration đáp ứng đầy đủ requirement.

## 23. Private integration và VPC Link

VPC Link cho phép API Gateway kết nối backend private trong VPC qua loại resource được hỗ trợ.

~~~text
Internet client
      ↓
API Gateway public endpoint
      ↓
VPC Link
      ↓
Private Load Balancer / service resource
      ↓
ECS, EKS hoặc EC2 backend
~~~

Mục tiêu là giữ backend không public trong khi API Gateway vẫn là public facade.

Private integration không có nghĩa client endpoint là private. Nếu muốn cả client lẫn backend chỉ trong VPC, cần xem xét Private REST API cùng private integration.

---

# Phần VI — Authentication và authorization

## 24. Authentication khác authorization

- Authentication: người gọi là ai?
- Authorization: người đó được làm gì?

Ví dụ:

~~~text
JWT hợp lệ → authentication thành công
scope orders:read → được GET /orders
không có orders:write → bị từ chối POST /orders
~~~

Đừng chỉ kiểm tra token tồn tại. Phải kiểm tra signature, issuer, audience, expiry và scope/claim phù hợp.

## 25. Không authentication

Public endpoint có thể để authorization type NONE, ví dụ health endpoint hoặc public catalog.

Ngay cả public API vẫn cần:

- Throttling.
- Validation.
- WAF khi phù hợp.
- Abuse monitoring.
- Không lộ dữ liệu nhạy cảm.
- Backend capacity protection.

## 26. IAM authorization

AWS_IAM yêu cầu client ký request bằng Signature Version 4 và có IAM permission execute-api:Invoke.

Phù hợp:

- Service-to-service trong AWS.
- Internal tooling.
- Client có AWS credentials tạm thời.
- Cross-account access có policy rõ ràng.

Không phù hợp trực tiếp cho browser public nếu không có cơ chế cấp temporary credentials an toàn.

Luồng:

~~~text
Caller IAM role
   ↓ SigV4 signed request
API Gateway
   ↓ evaluate IAM/resource policy
Backend
~~~

## 27. Cognito và JWT authorizer

Với HTTP API, JWT authorizer xác minh token dựa trên issuer và audience, sau đó có thể kiểm tra scope theo route.

Với REST API, Cognito User Pool authorizer tích hợp với token của Cognito; nếu dùng provider hoặc logic khác, Lambda authorizer có thể phù hợp.

Cần phân biệt:

- ID token mô tả identity/profile.
- Access token dùng để authorize API theo scope.
- Refresh token dùng để lấy token mới, không gửi như access token tới API.

Không chỉ decode JWT. Decode chỉ đọc payload; verify mới kiểm tra chữ ký và tính hợp lệ.

## 28. Lambda authorizer

Lambda authorizer dùng code tùy biến để quyết định cho phép hay từ chối request.

Use case:

- Token riêng.
- Legacy authentication.
- Multi-tenant policy.
- Kết hợp nhiều header/query/context.
- Gọi identity provider hoặc policy engine riêng.

Flow:

~~~text
Client request
   ↓
API Gateway
   ↓ identity source
Lambda authorizer
   ├── allow + context
   └── deny/unauthorized
   ↓
Backend integration
~~~

Trade-off:

- Thêm latency và cost.
- Authorizer có thể lỗi hoặc bị throttled.
- Cache policy sai có thể cấp nhầm quyền.
- Phải bảo vệ secret và dependency.
- Cần trả đúng authorizer response format.

Khi bật authorizer cache, cache key phải chứa đủ yếu tố ảnh hưởng quyết định. Nếu quyền khác nhau theo route nhưng cache key chỉ chứa token, policy trả về phải phù hợp cho toàn bộ phạm vi cache hoặc thêm route vào identity source theo khả năng API type.

## 29. Resource policy

Resource policy gắn với API để giới hạn ai hoặc nguồn nào được invoke.

Có thể giới hạn theo:

- AWS account/principal.
- Source VPC.
- VPC endpoint.
- Source IP.
- Organization hoặc condition key phù hợp.

Resource policy và identity policy có thể cùng tham gia quyết định. Với Private API, resource policy là lớp kiểm soát đặc biệt quan trọng.

## 30. API key không phải authentication

API key chủ yếu dùng để:

- Nhận diện consumer trong usage plan.
- Metering.
- Quota.
- Per-client throttling trong REST API.

API key có thể bị copy và không đại diện cho identity mạnh. Không dùng API key đơn lẻ để bảo vệ dữ liệu nhạy cảm.

Mô hình đúng:

~~~text
JWT/IAM/Lambda authorizer → xác thực và phân quyền
API key + usage plan      → quota và metering
~~~

---

# Phần VII — Request validation, mapping và CORS

## 31. Request validation

REST API có thể validate:

- Required path/query/header parameter.
- Request body dựa trên model/schema theo khả năng hỗ trợ.

Validation ở gateway giúp từ chối request sai trước khi gọi backend, giảm cost và tải.

Tuy nhiên backend vẫn phải validate:

- Business rules.
- Authorization theo resource.
- Cross-field constraints.
- Data từ nguồn khác.
- Giới hạn độ dài/range.
- Injection và unsafe content.

Gateway validation là lớp đầu, không thay validation trong application.

## 32. Mapping template

Mapping template có thể đổi shape của payload:

~~~text
Public request
{
  "name": "An"
}

Backend payload
{
  "customerName": "An",
  "requestId": "...",
  "source": "public-api"
}
~~~

Mapping giúp decouple contract nhưng có thể trở thành code ẩn khó test. Cần version control bằng OpenAPI/IaC và có test contract.

## 33. CORS là gì?

CORS là cơ chế browser kiểm soát JavaScript ở một origin gọi sang origin khác.

Ví dụ:

~~~text
Frontend: https://app.example.com
API:      https://api.example.com
~~~

Đây là cross-origin dù cùng domain gốc example.com vì host khác.

Browser có thể gửi preflight:

~~~http
OPTIONS /orders
Origin: https://app.example.com
Access-Control-Request-Method: POST
Access-Control-Request-Headers: Authorization,Content-Type
~~~

API phải trả header phù hợp:

~~~http
Access-Control-Allow-Origin: https://app.example.com
Access-Control-Allow-Methods: POST,OPTIONS
Access-Control-Allow-Headers: Authorization,Content-Type
~~~

## 34. CORS không phải authorization

CORS chỉ được browser thực thi. curl, Postman hoặc attacker server-side không bị CORS ngăn.

Do đó:

- CORS không bảo vệ API thay JWT/IAM.
- Security Group cũng không hiểu browser origin.
- Allowed origin phải hẹp khi dùng credential.
- Wildcard và credential có constraint.
- Cả success lẫn error response cần CORS header phù hợp nếu browser phải đọc chúng.

Lỗi “Postman chạy nhưng browser lỗi” thường là CORS, preflight hoặc browser policy.

---

# Phần VIII — Throttling, quota và bảo vệ backend

## 35. Throttling

Throttling giới hạn tốc độ request. Tư duy thường dùng token bucket:

- Rate: tốc độ duy trì.
- Burst: khả năng nhận đợt tăng ngắn.

Có thể có nhiều lớp limit:

- AWS account/Region quota.
- Stage.
- Method/route.
- Usage plan/client đối với REST API.
- Lambda concurrency.
- Downstream database/API.

Effective limit thường là lớp chặt nhất.

## 36. Khi bị throttled

Client thường nhận 429 Too Many Requests. Client nên:

- Exponential backoff.
- Jitter.
- Tôn trọng Retry-After khi có.
- Giới hạn số lần retry.
- Chỉ retry operation an toàn/idempotent.
- Không tạo retry storm.

Throttling bảo vệ backend, nhưng limit quá thấp làm giảm availability. Limit quá cao có thể khiến Lambda hoặc database bị quá tải.

## 37. Usage plan và quota

Usage plan của REST API liên kết API stage với API key và thiết lập throttling/quota cho consumer.

Quota thường là mục tiêu quản lý usage, không phải ranh giới bảo mật tuyệt đối hay cơ chế billing chính xác tuyệt đối. Thiết kế billing cần hệ thống metering đáng tin cậy riêng nếu có yêu cầu tài chính.

## 38. Bảo vệ downstream

Ví dụ API gọi Lambda rồi RDS:

~~~text
API Gateway throttle
        ↓
Lambda reserved concurrency
        ↓
RDS Proxy / connection control
        ↓
RDS capacity
~~~

Mỗi lớp phải thống nhất. Nếu API nhận 10.000 request đồng thời nhưng database chỉ chịu 100 connection, việc “serverless tự scale” có thể làm database sập nhanh hơn.

Với tác vụ không cần trả kết quả ngay:

~~~text
Client → API Gateway → SQS → worker
~~~

Queue hấp thụ burst và decouple backend. API trả 202 Accepted cùng job ID thay vì giữ HTTP request quá lâu.

---

# Phần IX — Caching

## 39. API Gateway cache

REST API có thể provision cache theo stage và cấu hình cache theo method/key.

Lợi ích:

- Giảm latency.
- Giảm số lần gọi backend.
- Giảm tải Lambda/database.
- Hữu ích cho GET/read-heavy data.

~~~text
Request GET /products?id=10
          ↓
       Cache key
        ├── hit  → trả response
        └── miss → gọi backend → lưu cache
~~~

Cần xác định cache key đầy đủ, ví dụ path, query hoặc header ảnh hưởng response.

## 40. Rủi ro cache

- Cache nhầm dữ liệu user A cho user B.
- TTL quá dài gây stale data.
- Cache key thiếu query/header.
- Response có dữ liệu nhạy cảm.
- Invalidation khó.
- Cache cluster có chi phí cố định.
- Cache miss đồng loạt gây backend spike.

Không cache response cá nhân hóa nếu chưa thiết kế cache key và authorization isolation đúng.

## 41. API Gateway cache và CloudFront

Hai lớp khác nhau:

- API Gateway cache: cache integration response trong REST API stage.
- CloudFront cache: cache tại edge theo cache policy.

CloudFront phù hợp phân phối toàn cầu và kiểm soát edge behavior rộng hơn. Có thể kết hợp, nhưng nhiều lớp cache làm invalidation và debugging phức tạp hơn.

---

# Phần X — Timeout, error và idempotency

## 42. Timeout budget

Một request synchronous có chuỗi timeout:

~~~text
Client timeout
   >
API Gateway integration timeout
   >
Lambda/backend timeout logic
   >
Downstream SDK timeout
~~~

Không phải mọi tầng đều cần timeout lớn hơn tầng sau theo đúng một công thức cứng, nhưng phải dành thời gian để tầng ngoài nhận lỗi và phản hồi có kiểm soát.

API Gateway có quota timeout phụ thuộc loại API/integration và có thể thay đổi theo capability hiện hành. Không thiết kế long-running workflow bằng cách chỉ tăng timeout.

Nếu job lâu:

~~~text
POST /reports
   ↓
validate + enqueue
   ↓
202 Accepted { jobId }
   ↓
SQS / Step Functions / ECS worker
   ↓
GET /reports/{jobId} hoặc callback
~~~

## 43. Nhóm status code

- 2xx: thành công.
- 400: request sai.
- 401: chưa xác thực hoặc credential không hợp lệ.
- 403: đã nhận diện nhưng không được phép, hoặc bị policy chặn.
- 404: resource/route không tồn tại hoặc cố ý che giấu.
- 409: conflict.
- 422: semantic validation theo API contract.
- 429: throttled.
- 500: lỗi nội bộ.
- 502: integration/backend response không hợp lệ hoặc proxy failure.
- 503: tạm thời không sẵn sàng.
- 504: integration timeout.

Không trả 200 kèm body error cho mọi trường hợp. Client, monitor và retry policy cần status code đúng.

## 44. Lambda error với proxy integration

Lambda proxy nên bắt lỗi dự kiến và trả response API rõ ràng. Lỗi không xử lý hoặc response sai format có thể trở thành 5xx.

Không gửi ra client:

- Stack trace.
- SQL error.
- Secret/token.
- Internal hostname.
- AWS ARN nhạy cảm không cần thiết.
- Chi tiết implementation có thể hỗ trợ tấn công.

Log chi tiết server-side với correlation ID; trả error code ổn định cho client.

## 45. Idempotency

Client, network proxy hoặc SDK có thể retry. POST tạo order có thể chạy hai lần nếu không có idempotency.

Pattern:

~~~text
Client gửi Idempotency-Key
        ↓
Backend kiểm tra key trong durable store
        ├── đã xử lý → trả kết quả cũ
        └── chưa có  → xử lý atomically và lưu kết quả
~~~

Idempotency phải nằm trong business layer/durable store, không chỉ dựa vào API Gateway cache.

---

# Phần XI — Security

## 46. Security checklist

- Chỉ dùng HTTPS.
- Chọn authorizer phù hợp.
- Kiểm tra scope/claim và authorization theo resource.
- Dùng least privilege cho integration role.
- Giới hạn Lambda resource-based permission bằng source ARN.
- Dùng resource policy cho source/account/VPC endpoint khi phù hợp.
- Không coi API key là authentication.
- Validate input ở gateway và backend.
- Giới hạn payload/request rate.
- Dùng AWS WAF cho public REST API hoặc kiến trúc CloudFront phù hợp theo feature.
- Không log token, API key, cookie hoặc PII đầy đủ.
- Tắt default execute-api endpoint nếu kiến trúc yêu cầu.
- Backend private khi không cần public.
- Dùng Secrets Manager/Parameter Store cho secret.
- Bật access log và alarm.
- Quản lý certificate/TLS policy.
- Dùng separate account/stage cho production theo mức độ yêu cầu.
- Kiểm tra dependency và code backend, vì gateway không loại bỏ application vulnerability.

## 47. WAF, Shield và DDoS

AWS Shield Standard bảo vệ nền tảng trước một số lớp tấn công phổ biến theo dịch vụ hỗ trợ. AWS WAF có thể lọc:

- IP.
- Geo.
- Rate-based rule.
- Known bad input.
- SQL injection/XSS pattern.
- Managed rule groups.

WAF không thay authentication, validation hay rate limit backend. Rule quá chặt có thể block traffic hợp lệ; cần monitor sampled request và rollout có kiểm soát.

## 48. Integration permission

API Gateway cần quyền gọi backend.

Với Lambda thường có resource-based policy cho principal API Gateway và source ARN phù hợp.

Nếu permission thiếu, API có thể route đúng nhưng vẫn trả 5xx. Nếu source ARN quá rộng, API hoặc stage khác có thể invoke Lambda ngoài ý định.

Với AWS service integration, API Gateway thường assume IAM role có policy tối thiểu cho action/resource cần gọi.

## 49. Multi-tenant authorization

Không chỉ xác thực tenantId client gửi trong path:

~~~text
GET /tenants/t-200/orders
Authorization: token của tenant t-100
~~~

Backend phải so sánh tenant claim/context với resource được yêu cầu. Không tin path parameter, query hay header tự khai báo.

Authorizer có thể đưa tenant context xuống backend, nhưng backend vẫn cần enforcement nhất quán.

---

# Phần XII — Observability

## 50. Access log và execution log

Access log ghi một record cho mỗi request, nên chứa:

- Request ID.
- Extended request ID khi phù hợp.
- Method/route.
- Path.
- Status.
- Response latency.
- Integration latency.
- Source IP hoặc user agent theo chính sách.
- Principal/authorizer identity không nhạy cảm.
- Error summary.
- Correlation ID.

Execution log của REST API có thể chi tiết hơn về pipeline, nhưng không nên bật full payload nhạy cảm vô thời hạn.

Dùng structured JSON để CloudWatch Logs Insights và log pipeline phân tích dễ hơn.

## 51. Metric quan trọng

- Count.
- 4XXError.
- 5XXError.
- Latency.
- IntegrationLatency.
- CacheHitCount/CacheMissCount nếu dùng cache.
- WebSocket connection/message metric.
- Lambda Errors, Duration, Throttles, ConcurrentExecutions.
- Backend ALB/ECS/RDS metric tương ứng.

Phân biệt:

~~~text
Latency
= tổng thời gian API Gateway xử lý request

IntegrationLatency
= phần thời gian chờ backend integration
~~~

Nếu IntegrationLatency cao, điều tra backend. Nếu Latency cao hơn đáng kể dù integration nhanh, kiểm tra authorizer, mapping, gateway processing và network path.

## 52. Tracing và correlation

AWS X-Ray hoặc distributed tracing giúp theo dõi request qua API Gateway, Lambda và downstream theo tính năng hỗ trợ.

Mỗi request nên có correlation ID:

~~~text
Client request ID
  ↓
API Gateway request context
  ↓
Lambda/application log
  ↓
downstream call/message
~~~

Không để client tùy ý ghi đè trusted trace/security field mà không validate.

## 53. Alarm

Alarm thực tế:

- 5xx rate vượt baseline.
- 4xx/401/403 tăng bất thường.
- P95/P99 latency cao.
- Integration timeout tăng.
- Lambda throttle/error.
- Authorizer failure.
- SQS backlog tăng nếu API enqueue.
- WAF blocked request spike.
- Không có traffic khi đáng lẽ phải có.

Alarm theo tỷ lệ thường hữu ích hơn số lượng tuyệt đối khi traffic biến động.

---

# Phần XIII — Deployment và versioning

## 54. Infrastructure as Code

API cấu hình bằng console dễ drift. Nên version bằng:

- OpenAPI.
- AWS SAM.
- CloudFormation.
- AWS CDK.
- Terraform nếu tổ chức sử dụng.

Pipeline nên:

1. Lint OpenAPI/IaC.
2. Test contract.
3. Deploy environment thử nghiệm.
4. Chạy integration test.
5. Promote có kiểm soát.
6. Theo dõi metric.
7. Rollback khi lỗi.

## 55. Canary deployment

REST API stage có thể chuyển một phần traffic sang deployment canary theo capability hỗ trợ.

~~~text
95% → deployment hiện tại
 5% → deployment mới
~~~

Theo dõi:

- Error rate.
- Latency.
- Business conversion.
- Backend saturation.
- Log bất thường.

Canary API không giải quyết Lambda alias/database schema nếu các thành phần đó không tương thích. Toàn bộ release phải có chiến lược versioning.

## 56. API versioning

Các cách:

- Path: /v1/orders.
- Host: v1.api.example.com.
- Header/media type.
- Stage mapping.

Path version dễ thấy và vận hành, nhưng không có một cách đúng cho mọi hệ thống.

Nguyên tắc:

- Thay đổi backward-compatible khi có thể.
- Không xóa field đột ngột.
- Consumer contract test.
- Có deprecation policy.
- Theo dõi usage version cũ.
- Không dùng stage dev/prod như đồng nghĩa API version v1/v2.

---

# Phần XIV — Kiến trúc mẫu

## 57. Public serverless API

~~~text
Route 53
   ↓
API Gateway HTTP API
   ↓ JWT authorizer
Lambda
   ├── DynamoDB
   ├── S3
   └── EventBridge
~~~

Phù hợp API gọn, JWT auth, traffic biến động và không cần REST API advanced features.

## 58. Managed partner API

~~~text
Partner
   ↓
Regional REST API
   ├── WAF
   ├── IAM/Lambda authorizer
   ├── API key
   ├── usage plan
   ├── request validation
   └── access logs
         ↓
      Lambda / private service
~~~

API key dùng metering/quota; IAM hoặc authorizer mới là lớp xác thực/phân quyền.

## 59. API tới ECS private service

~~~text
Client
  ↓
API Gateway
  ↓
VPC Link
  ↓
Private load balancer/resource
  ↓
ECS tasks trong private subnets
~~~

Backend không cần public IP. ECS task Security Group chỉ nhận traffic từ đường private đã thiết kế.

## 60. Asynchronous API

~~~text
POST /video-jobs
   ↓
API Gateway
   ↓ direct integration hoặc Lambda mỏng
SQS
   ↓
ECS/Lambda worker
   ↓
S3 + DynamoDB job status

GET /video-jobs/{id}
   ↓
trả trạng thái
~~~

Ưu điểm:

- Không phụ thuộc integration timeout cho job dài.
- Hấp thụ burst.
- Retry và DLQ rõ ràng.
- Worker scale độc lập.

## 61. Private enterprise API

~~~text
On-premises
   ↓ Direct Connect / VPN
VPC
   ↓ Interface VPC Endpoint
Private REST API
   ↓ resource policy
Private integration/backend
~~~

Cần kiểm tra DNS hybrid, endpoint policy, resource policy, route và identity authorization.

## 62. WebSocket notification

~~~text
Client connect
   ↓
WebSocket API $connect
   ↓
Lambda lưu connectionId vào DynamoDB

Business event
   ↓
EventBridge / SNS / Lambda
   ↓
API Gateway Management API
   ↓
push tới connectionId
~~~

Khi Management API trả connection đã mất, xóa stale connection record. Thiết kế fan-out và quota khi số connection lớn.

---

# Phần XV — Chi phí

## 63. Thành phần chi phí

Chi phí có thể gồm:

- API request.
- WebSocket message và connection duration.
- Data transfer.
- REST API cache cluster.
- Lambda/HTTP backend.
- CloudWatch Logs và custom metrics.
- X-Ray tracing.
- WAF.
- NAT Gateway hoặc VPC Link/private networking.
- CloudFront nếu đặt phía trước.
- Custom domain không phải lúc nào cũng là phần lớn chi phí nhưng certificate/DNS vẫn cần quản lý.

## 64. Tối ưu chi phí

- Chọn HTTP API nếu feature đáp ứng.
- Direct AWS service integration khi không cần Lambda.
- Giảm log payload và đặt retention.
- Cache read-heavy response có hit ratio đủ cao.
- Không provision REST cache nếu traffic thấp.
- Dùng asynchronous queue cho burst/job dài.
- Right-size backend và concurrency.
- Tránh retry storm.
- Đo cost theo endpoint/consumer.
- Xóa stage, API, VPC Link và log group không dùng.

Không chọn rẻ hơn nếu thiếu feature bảo mật hoặc vận hành bắt buộc.

---

# Phần XVI — Giới hạn và Service Quotas

## 65. Vì sao phải học giới hạn API Gateway?

Giới hạn của API Gateway ảnh hưởng trực tiếp tới kiến trúc. Một thiết kế đúng về routing và security vẫn có thể thất bại nếu:

- Request body lớn hơn payload limit.
- Backend xử lý lâu hơn integration timeout.
- Traffic vượt account-level throttle.
- WebSocket message hoặc frame quá lớn.
- Kết nối WebSocket idle quá lâu.
- API có quá nhiều resource, route, stage hoặc authorizer.
- Header chứa JWT, cookie và metadata vượt kích thước cho phép.

Không nên học các con số như những dữ kiện rời rạc. Hãy gắn mỗi giới hạn với câu hỏi:

~~~text
Giới hạn áp dụng cho:
  account, Region, API, stage, route hay request?

Giới hạn là:
  hard limit hay quota có thể xin tăng?

Khi vượt giới hạn:
  client nhận lỗi gì?

Nếu không thể tăng:
  phải đổi kiến trúc như thế nào?
~~~

Các quota có thể thay đổi theo Region và thời điểm. Trước khi production hoặc làm capacity planning, luôn kiểm tra Service Quotas và trang quota chính thức.

## 66. Hard limit và quota có thể tăng

### Hard limit

Hard limit không thể tăng bằng support request. Ví dụ phổ biến:

- HTTP API payload tối đa 10 MB.
- HTTP API integration timeout tối đa 30 giây.
- HTTP API integrations tối đa 300 trên mỗi API.
- WebSocket frame tối đa 32 KB.
- WebSocket message tối đa 128 KB.
- WebSocket connection có thời gian sống tối đa.

Khi chạm hard limit, phải thay đổi contract hoặc kiến trúc.

### Adjustable quota

Adjustable quota có default value nhưng có thể yêu cầu tăng, tùy Region, account, workload và phê duyệt của AWS.

Ví dụ:

- Account-level request rate.
- Số route hoặc resource trong một số trường hợp.
- Số stage.
- Số authorizer.
- Số custom domain.
- Một số VPC Link quota.

Được đánh dấu “có thể tăng” không có nghĩa chắc chắn được tăng ngay hoặc tăng vô hạn. Cần gửi yêu cầu sớm và load test sau khi được duyệt.

## 67. Account-level throttling quota

Quota mặc định phổ biến theo account, mỗi Region:

| Quota | Giá trị mặc định phổ biến | Có thể tăng? |
|---|---:|---|
| Steady-state request rate | 10.000 request/giây | Có |
| Burst bucket capacity | 5.000 request | Không yêu cầu tăng trực tiếp |

Quota này dùng chung cho:

- REST APIs.
- HTTP APIs.
- WebSocket APIs.
- WebSocket callback APIs.

Nó không phải 10.000 RPS riêng cho từng API.

~~~text
Account A, Region ap-southeast-1
├── REST API Orders     4.000 RPS
├── HTTP API Users      3.000 RPS
├── WebSocket messages  2.000 RPS
└── Callback API        1.000 RPS
                         ─────────
Tổng                    10.000 RPS
~~~

Nếu một API dùng gần hết quota, API khác trong cùng account/Region có thể bị ảnh hưởng.

Một số Region có default thấp hơn: 2.500 RPS và burst 1.250. Vì vậy không được mặc định mọi Region đều có 10.000/5.000.

### Rate và burst khác nhau

- Rate là tốc độ request duy trì.
- Burst là khả năng hấp thụ đợt request tăng ngắn bằng token bucket.
- Burst không phải số concurrent request được đảm bảo.
- Throttling là best effort target, không nên dùng như ranh giới billing hoặc security tuyệt đối.

Khi vượt giới hạn, client thường nhận 429 Too Many Requests.

### Thứ tự effective throttle

Request có thể chịu nhiều lớp:

~~~text
AWS service-level protection
        ↓
Account/Region throttle
        ↓
Stage throttle
        ↓
Method/route throttle
        ↓
Usage plan/API key throttle
        ↓
Backend capacity/concurrency
~~~

Effective throughput bị giới hạn bởi lớp chặt nhất.

## 68. Các giới hạn REST API quan trọng

Các giá trị dưới đây là default hoặc hard limit phổ biến trong tài liệu AWS hiện hành:

| Resource hoặc operation | Giá trị | Có thể tăng? |
|---|---:|---|
| Payload size | 10 MB | Không |
| Integration timeout mặc định | 50 ms đến 29 giây | Tùy endpoint type |
| Resources trên mỗi API | 300 | Có |
| Stages trên mỗi API | 10 | Có |
| Lambda/Cognito authorizers trên mỗi API | 10 | Có |
| Tổng header size, public API | 20.480 byte | Không |
| Tổng header size, private API | 8.000 byte | Không |
| Mapping template size | 300 KB | Không |
| Mapping template foreach iterations | 1.000 | Không |
| API resource policy size | 8.192 ký tự | Có |
| Access log template size | 3 KB | Không |
| API cache TTL | mặc định 300 giây; từ 0 đến 3.600 giây | Không tăng upper bound |
| Cached response size | 1.048.576 byte | Không |
| Idle connection timeout | 310 giây | Không |
| OpenAPI definition khi import/put | 6 MB | Không |

### Integration timeout của REST API

- Edge-optimized REST API: tối đa 29 giây, không tăng.
- Regional REST API: mặc định tối đa 29 giây; có thể yêu cầu quota cao hơn.
- Private REST API: mặc định tối đa 29 giây; có thể yêu cầu quota cao hơn.

Tăng timeout cho Regional hoặc Private API có thể yêu cầu giảm account-level throttle quota. Lý do là request tồn tại lâu hơn sẽ giữ tài nguyên hệ thống lâu hơn.

Không xem khả năng xin tăng timeout là lý do để chạy job nhiều phút theo synchronous HTTP. Async pattern vẫn thường an toàn hơn.

### Payload 10 MB

Payload limit 10 MB áp dụng cho request/response buffered ở lớp API Gateway. Nếu backend có giới hạn thấp hơn, effective limit là giới hạn thấp nhất trên toàn đường đi.

Ví dụ, Lambda synchronous invocation buffered có giới hạn 6 MB cho request và 6 MB cho response. Event mà API Gateway gửi tới Lambda còn chứa header, path, context và metadata, nên phần body ứng dụng sử dụng được có thể thấp hơn 6 MB. Vì vậy không được thấy API Gateway ghi 10 MB rồi kết luận Lambda integration cũng nhận được trọn 10 MB.

Regional REST API hiện có response streaming cho các proxy integration được hỗ trợ. Streaming có thể vượt giới hạn response buffered 10 MB; với Lambda response streaming, Lambda hỗ trợ response lớn hơn buffered response theo quota Lambda hiện hành. Đây là capability dành cho response, không làm request upload vào Lambda lớn hơn và phải kiểm tra Region/integration support trước khi thiết kế.

Không upload file lớn qua JSON/base64:

~~~text
Client
  ↓ yêu cầu upload
API Gateway + Lambda
  ↓ tạo pre-signed URL
Client
  ↓ upload trực tiếp
Amazon S3
~~~

Base64 còn làm dữ liệu lớn hơn, khiến request chạm limit sớm hơn.

### Header limit

Header size bao gồm tên, giá trị, khoảng trắng và ký tự kết thúc dòng theo cách AWS tính.

JWT lớn, nhiều cookie, trace baggage hoặc custom metadata có thể làm vượt header limit. Không đưa toàn bộ user profile hoặc policy lớn vào token.

## 69. Các giới hạn HTTP API quan trọng

| Resource hoặc operation | Giá trị | Có thể tăng? |
|---|---:|---|
| Payload size | 10 MB | Không |
| Maximum integration timeout | 30 giây | Không |
| Request line + toàn bộ header values | 10.240 byte | Không |
| Routes trên mỗi API | 300 | Có |
| Integrations trên mỗi API | 300 | Không |
| Stages trên mỗi API | 10 | Có |
| Authorizers trên mỗi API | 10 | Có |
| Scopes trên mỗi route | 10 | Không |
| Lambda authorizer response timeout | 10 giây | Không |
| Audiences trên mỗi authorizer | 50 | Không |
| Stage variables trên mỗi stage | 100 | Không |
| Multi-level API mappings trên mỗi domain | 200 | Không |
| Access log template size | 3 KB | Không |
| CloudWatch Logs log entry | 1 MB | Không |
| VPC Links trên mỗi account/Region | 10 | Có |
| Subnets trên mỗi VPC Link | 10 | Có |

### Timeout 30 giây là hard limit của HTTP API

Nếu backend cần lâu hơn:

~~~text
Client → POST /jobs
           ↓
       enqueue work
           ↓
       202 + jobId

Worker xử lý riêng

Client → GET /jobs/{jobId}
~~~

Không thể khắc phục bằng cách tăng Lambda timeout lên cao hơn. API Gateway có thể timeout trước khi Lambda kết thúc, trong khi Lambda vẫn tiếp tục chạy và tạo side effect.

### Authorizer timeout

Lambda authorizer phải trả kết quả trong giới hạn của authorizer. Không để authorizer:

- Gọi nhiều identity provider tuần tự.
- Query database chậm.
- Thực hiện business logic.
- Tải policy khổng lồ.

Cache authorizer result khi an toàn và cache key được thiết kế đúng.

## 70. Các giới hạn WebSocket API quan trọng

| Giới hạn | Giá trị |
|---|---:|
| Message payload | 128 KB |
| Frame size | 32 KB |
| Idle connection timeout | 10 phút |
| Maximum connection duration | 2 giờ |
| Integration timeout | tối đa 29 giây |
| Binary frame | Không được hỗ trợ trực tiếp |
| Stages trên mỗi API | mặc định 10 |

### Message và frame

Một message tối đa 128 KB nhưng mỗi frame tối đa 32 KB. Message lớn hơn 32 KB phải được chia thành nhiều frame hợp lệ. Nếu frame hoặc message vượt giới hạn, connection có thể bị đóng với close code 1009.

Binary frame không được hỗ trợ trực tiếp và có thể làm connection đóng với code 1003. Dữ liệu binary lớn nên đưa vào S3, rồi gửi URL hoặc object key qua WebSocket.

### Idle và connection lifetime

- Không có traffic trong 10 phút có thể bị xem là idle.
- Một connection tồn tại tối đa 2 giờ, kể cả vẫn hoạt động.
- Khi đạt các giới hạn này, API Gateway có thể đóng với code 1001.

Client bắt buộc phải có:

- Reconnect logic.
- Exponential backoff với jitter.
- Re-authentication khi reconnect.
- Resubscribe room/topic.
- Xử lý message duplicate hoặc gap.
- Heartbeat hợp lý nếu use case yêu cầu.

Không giả định connectionId tồn tại vĩnh viễn. Khi callback nhận GoneException hoặc connection không còn hợp lệ, xóa record stale khỏi datastore.

### Connection rate và concurrent connection

WebSocket scaling phụ thuộc cả:

- New connections mỗi giây.
- Số connection đồng thời.
- Message rate.
- Callback rate.
- Kích thước message.
- Thời lượng connection.

Công thức ước lượng:

~~~text
Concurrent connections
≈ new connections/giây × average connection duration
~~~

Ví dụ 100 connection mới mỗi giây, thời lượng trung bình 30 phút:

~~~text
100 × 1.800 = khoảng 180.000 connection đồng thời
~~~

Đây là capacity estimate, không phải cam kết quota. Phải kiểm tra quota WebSocket theo Region/account trước khi launch.

## 71. Chuyện gì xảy ra khi vượt giới hạn?

| Tình huống | Kết quả thường gặp | Hướng xử lý |
|---|---|---|
| Vượt request rate | 429 | Backoff, jitter, quota request, buffer |
| HTTP integration quá lâu | 504 | Tối ưu backend hoặc chuyển async |
| Lambda proxy response sai/quá giới hạn downstream | 5xx/502 | Sửa contract, giảm payload |
| Request payload quá lớn | 413 hoặc request bị từ chối | S3 pre-signed URL |
| URL/path quá dài | 414 | Chuyển dữ liệu sang body, rút gọn path |
| Header quá lớn | 4xx | Giảm JWT/cookie/header |
| WebSocket idle hoặc quá 2 giờ | Close 1001 | Reconnect |
| WebSocket binary frame | Close 1003 | Dùng text/reference đến S3 |
| WebSocket message/frame quá lớn | Close 1009 | Chunk hợp lệ hoặc external storage |
| Stage/resource/route quota | Deploy/create thất bại | Xin tăng hoặc tách API |

Không dựa duy nhất vào status code để kết luận. Xem access log, execution log, Service Quotas, backend log và metric cùng nhau.

## 72. Thiết kế để không bị giới hạn

### File lớn

~~~text
Không nên:
Client → API Gateway → Lambda → S3
         file hàng chục/hàng trăm MB

Nên:
Client → API xin pre-signed URL
Client → upload/download trực tiếp S3
~~~

### Job lâu

~~~text
API → SQS / Step Functions → worker
 ↓
202 Accepted + jobId
~~~

### Traffic burst

- Đặt route/stage throttle có chủ đích.
- Dùng SQS cho tác vụ asynchronous.
- Giới hạn Lambda concurrency để bảo vệ database.
- Request account quota increase trước sự kiện.
- Load test với payload và timeout thực tế.
- Cân nhắc Multi-Region khi requirement và data architecture cho phép.

### Quá nhiều route/resource

- Tách API theo bounded context.
- Dùng custom domain mapping để giữ URL thống nhất.
- Reuse authorizer khi policy phù hợp.
- Dùng proxy route có cân nhắc, không đánh đổi toàn bộ validation/authorization chỉ để giảm resource count.

### Response lớn

- Trả pagination.
- Trả object URL.
- Nén theo capability client/gateway/backend.
- Dùng response streaming khi API type và integration hỗ trợ.
- Không trả toàn bộ dataset qua một request.

### Checklist capacity planning

- [ ] Xác định API type và Region.
- [ ] Kiểm tra account-level RPS hiện tại.
- [ ] Tính peak RPS và burst.
- [ ] Tính payload/header P95 và P99.
- [ ] Tính integration latency P95 và P99.
- [ ] Kiểm tra backend concurrency.
- [ ] Kiểm tra file upload/download path.
- [ ] Kiểm tra WebSocket connection/message rate.
- [ ] Gửi quota increase đủ sớm.
- [ ] Load test và kiểm tra 429/5xx/latency.
- [ ] Có client retry với exponential backoff và jitter.
- [ ] Có alarm khi usage gần quota.

---

# Phần XVII — Troubleshooting

## 73. 403 Forbidden

Kiểm tra:

- IAM signature/permission.
- Resource policy.
- VPC endpoint policy.
- Lambda/Cognito/JWT authorizer.
- WAF block.
- API key required nhưng thiếu.
- Route/stage URL sai.
- Default endpoint đã bị tắt.
- Private API bị gọi ngoài endpoint cho phép.

403 không luôn có nghĩa application backend từ chối; request có thể chưa chạm backend.

## 74. 401 Unauthorized

Thường liên quan:

- Thiếu Authorization header.
- Token hết hạn.
- Signature JWT không hợp lệ.
- Issuer/audience sai.
- Identity source authorizer thiếu.
- Lambda authorizer trả unauthorized.
- Token type sai.

401 là chưa xác thực; 403 thường là đã xác định nhưng bị cấm, dù một số policy/gateway response có thể làm behavior khác.

## 75. 500 hoặc 502

Kiểm tra:

- API Gateway có permission invoke Lambda không.
- Lambda response proxy có đúng format không.
- Lambda có exception không.
- Integration URI/method sai.
- Mapping template lỗi.
- Backend reset/invalid response.
- Authorizer trả sai format.
- Execution/access log và Lambda log.

502 thường gặp khi Lambda chạy nhưng response không đúng contract.

## 76. 504 Integration Timeout

- Backend xử lý quá lâu.
- Downstream timeout.
- Connection pool cạn.
- Lambda cold start hoặc duration lớn.
- API synchronous không phù hợp workload.

Không chỉ tăng timeout. Chuyển job dài sang async nếu caller không cần kết quả ngay.

## 77. 429 Too Many Requests

Xác định lớp throttling:

- API Gateway account/stage/route.
- Usage plan.
- Lambda concurrency.
- Backend.
- WAF rate-based rule.
- AWS service integration quota.

Xem metric cả gateway và backend; client phải backoff với jitter.

## 78. CORS lỗi

Checklist:

1. Browser có gửi OPTIONS không.
2. OPTIONS route/method tồn tại không.
3. Allow-Origin có khớp origin chính xác không.
4. Allow-Methods có method cần gọi không.
5. Allow-Headers có Authorization/Content-Type không.
6. Response lỗi 4xx/5xx có CORS header không.
7. Có dùng credential với wildcard sai không.
8. CloudFront/cache có trả header của origin khác không.
9. Lambda proxy có tự trả CORS header đúng không.

## 79. Thay đổi không có hiệu lực

- REST API chưa redeploy stage.
- Client gọi nhầm stage/custom-domain mapping.
- DNS/cache chưa cập nhật.
- HTTP API auto-deploy tắt.
- Lambda alias/version vẫn trỏ code cũ.
- CloudFront hoặc API cache giữ response cũ.
- IaC pipeline deploy sang Region/account khác.

## 80. Private integration không kết nối

Kiểm tra:

- VPC Link status.
- Load balancer/listener/target health.
- Security Group/NACL.
- Integration URI.
- DNS.
- TLS hostname/certificate.
- Backend route.
- Subnet và cross-zone behavior.
- Access log/integration latency.

---

# Phần XVIII — Bẫy thi SAA-C03

## 81. Những nhầm lẫn thường gặp

1. HTTP API và REST API là hai sản phẩm khác nhau trong API Gateway.
2. REST API nhiều feature hơn; HTTP API thường gọn và rẻ hơn.
3. WebSocket dành cho hai chiều, không phải REST request thông thường.
4. API key không phải authentication mạnh.
5. Usage plan chủ yếu áp dụng cho REST API consumer management.
6. Private API và private integration là hai khái niệm khác nhau.
7. Lambda proxy integration yêu cầu response đúng format.
8. API Gateway cần permission invoke Lambda.
9. CORS chỉ là browser policy, không phải authorization.
10. Throttling bảo vệ backend nhưng không thay Lambda concurrency control.
11. Stage không tự cập nhật nếu deployment chưa được gắn/deploy.
12. Job dài nên dùng async pattern thay vì giữ request.
13. Gateway validation không thay business validation.
14. Cache key thiếu identity có thể rò dữ liệu giữa user.
15. 502 có thể do malformed Lambda response.
16. 403 có thể đến từ resource policy/WAF/IAM trước backend.
17. Regional API có thể đặt sau CloudFront do bạn quản lý.
18. Private REST API cần interface VPC endpoint và policy phù hợp.
19. Lambda authorizer cache sai key có thể cấp quyền sai.
20. Multi-Region API cần Route 53/CloudFront, data strategy và deployment riêng; API Gateway không tự biến thành active-active toàn cầu.
21. Payload limit 10 MB của API Gateway không loại bỏ giới hạn thấp hơn của Lambda hoặc backend.
22. HTTP API timeout tối đa 30 giây; tăng Lambda timeout không làm API synchronous chờ lâu hơn.
23. WebSocket connection không tồn tại vĩnh viễn: idle 10 phút hoặc đạt 2 giờ có thể bị đóng.

## 82. Cách chọn trong đề thi

~~~text
Cần WebSocket/two-way?
├── Có  → WebSocket API
└── Không
    │
    ├── Cần API key, usage plan, cache, private endpoint,
    │   request validation hoặc mapping nâng cao?
    │      ├── Có  → REST API
    │      └── Không → HTTP API thường phù hợp
    │
    ├── Backend Lambda?
    │      └── proxy integration thường đơn giản
    │
    ├── Backend private ECS/EC2?
    │      └── private integration/VPC Link
    │
    ├── AWS service đơn giản?
    │      └── cân nhắc direct service integration
    │
    └── Job dài?
           └── enqueue + 202 + status API
~~~

Luôn kiểm tra security, timeout, cost và feature compatibility trước khi chốt.

---

# Phần XIX — Lab thực hành

## 83. Lab 1: HTTP API gọi Lambda

- Tạo Lambda trả JSON.
- Tạo HTTP API.
- Tạo GET /hello và POST /orders.
- Cấu hình Lambda proxy integration.
- Cấp invoke permission.
- Bật access log.
- Test success, validation error và Lambda exception.
- Quan sát 2xx, 4xx, 5xx, latency.

## 84. Lab 2: JWT authorization

- Tạo Cognito User Pool hoặc OIDC issuer phù hợp.
- Tạo app client.
- Lấy access token.
- Cấu hình JWT authorizer.
- Yêu cầu scope cho POST /orders.
- Test thiếu token, token sai audience, token hết hạn và thiếu scope.
- Xác nhận backend nhận claim cần thiết nhưng vẫn kiểm tra authorization theo resource.

## 85. Lab 3: REST API usage plan

- Tạo REST API.
- Tạo stage dev.
- Tạo API key và usage plan.
- Cấu hình throttle/quota.
- Gửi burst request.
- Quan sát 429.
- Thêm authorizer để chứng minh API key không thay authentication.

## 86. Lab 4: CORS

- Dùng frontend ở origin khác.
- Cấu hình OPTIONS/preflight.
- Cho phép origin cụ thể.
- Test request có Authorization.
- Cố ý bỏ Allow-Headers để quan sát lỗi.
- Bảo đảm cả 4xx/5xx có CORS header.

## 87. Lab 5: Asynchronous API

- POST /jobs.
- API Gateway gửi SQS qua direct integration hoặc Lambda mỏng.
- Trả 202 và job ID.
- Worker xử lý message.
- GET /jobs/{id} đọc trạng thái.
- Test duplicate request bằng Idempotency-Key.
- Thêm DLQ và alarm backlog.

## 88. Lab 6: Private backend

- Chạy service trên ECS private subnets.
- Đặt private load balancer/resource được hỗ trợ.
- Tạo VPC Link.
- Tích hợp API Gateway.
- Xác nhận backend không có public inbound.
- Kiểm tra access log, target health và integration latency.

---

# Phần XX — Câu hỏi tự kiểm tra

## 89. Câu hỏi

### Câu 1

Một API Lambda đơn giản cần JWT auth, custom domain và chi phí thấp; không cần API key, cache hoặc request mapping phức tạp. Chọn gì?

**Đáp án:** HTTP API, nếu toàn bộ feature requirement được hỗ trợ.

### Câu 2

Partner cần quota riêng theo từng API key và request validation tại gateway. Chọn gì?

**Đáp án:** REST API với usage plan/API key và request validator; vẫn dùng authorizer phù hợp cho authentication.

### Câu 3

Ứng dụng chat cần backend chủ động gửi message cho client. Chọn gì?

**Đáp án:** WebSocket API, lưu connectionId và dùng Management API để callback.

### Câu 4

API Gateway trả 502 nhưng Lambda log cho thấy function đã chạy thành công. Nguyên nhân dễ gặp?

**Đáp án:** Lambda proxy response sai format hoặc integration response/mapping không hợp lệ.

### Câu 5

Muốn API public gọi ECS service không public trong VPC. Kiến trúc?

**Đáp án:** API Gateway với private integration qua VPC Link tới backend/load balancer private được hỗ trợ.

### Câu 6

API key có đủ bảo vệ dữ liệu tài chính không?

**Đáp án:** Không. API key dùng nhận diện usage/quota; cần IAM, JWT/Cognito hoặc Lambda authorizer và authorization nghiệp vụ.

### Câu 7

Browser báo CORS nhưng Postman gọi được. Có phải IAM sai không?

**Đáp án:** Chưa chắc. Kiểm tra preflight OPTIONS và CORS response headers; Postman không thực thi browser CORS policy.

### Câu 8

Job tạo báo cáo mất vài phút. Có nên tăng timeout API đến vài phút không?

**Đáp án:** Nên dùng async pattern: validate/enqueue, trả 202 + job ID, xử lý bằng worker/Step Functions và cung cấp status/callback.

### Câu 9

Private API và private integration có giống nhau không?

**Đáp án:** Không. Private API hạn chế phía client access qua VPC endpoint; private integration là đường API Gateway gọi backend private trong VPC.

### Câu 10

REST API đã sửa integration nhưng production vẫn gọi backend cũ. Kiểm tra đầu tiên?

**Đáp án:** Kiểm tra deployment đã được redeploy/gắn vào đúng stage và custom-domain mapping chưa.

### Câu 11

Lambda authorizer cache chỉ theo token, nhưng cùng token có quyền khác nhau theo route. Rủi ro?

**Đáp án:** Cached decision/policy có thể không phù hợp route khác. Cần policy đủ phạm vi hoặc cache key chứa context route theo mô hình hỗ trợ.

### Câu 12

Traffic burst làm Lambda scale nhanh và RDS hết connection. Giải pháp?

**Đáp án:** Phối hợp API throttling, Lambda reserved concurrency, connection management/RDS Proxy; nếu tác vụ không cần synchronous, dùng queue để buffer.

### Câu 13

Cần public REST API toàn cầu và muốn tự cấu hình CloudFront/WAF/cache policy. Chọn endpoint nào?

**Đáp án:** Regional REST API sau CloudFront distribution do bạn quản lý thường cho nhiều quyền kiểm soát hơn edge-optimized endpoint do API Gateway quản lý.

### Câu 14

Cần service AWS nhận request trực tiếp mà transformation đơn giản. Có bắt buộc Lambda không?

**Đáp án:** Không. Cân nhắc AWS service integration để giảm code, hop và cold start.

---

# Phần XXI — Checklist

## 90. Checklist ôn thi

- [ ] Giải thích được vai trò API Gateway.
- [ ] Phân biệt REST API, HTTP API và WebSocket API.
- [ ] Chọn Edge-optimized, Regional hoặc Private REST endpoint.
- [ ] Phân biệt Private API và private integration.
- [ ] Hiểu resource/method, route, stage và deployment.
- [ ] Cấu hình custom domain, ACM và Route 53 theo loại endpoint.
- [ ] Phân biệt Lambda proxy và custom integration.
- [ ] Biết direct AWS service integration.
- [ ] Hiểu VPC Link.
- [ ] Phân biệt IAM, Cognito/JWT và Lambda authorizer.
- [ ] Không coi API key là authentication.
- [ ] Hiểu resource policy và integration permission.
- [ ] Biết request validation và mapping template.
- [ ] Giải thích được CORS và preflight.
- [ ] Thiết kế throttling, quota và backend protection.
- [ ] Dùng cache an toàn với cache key đúng.
- [ ] Thiết kế timeout budget và async API.
- [ ] Trả status code/error contract đúng.
- [ ] Thiết kế idempotency cho request retry.
- [ ] Bật access log, metric, trace và alarm.
- [ ] Dùng IaC, deployment và canary có kiểm soát.
- [ ] Troubleshoot 401, 403, 429, 500, 502 và 504.
- [ ] Phân biệt hard limit và adjustable quota.
- [ ] Nhớ account-level throttle dùng chung theo account/Region.
- [ ] Biết payload, timeout và header limit của REST/HTTP API.
- [ ] Biết message, frame, idle và connection-duration limit của WebSocket.
- [ ] Thiết kế S3 pre-signed URL cho file lớn và async job cho tác vụ lâu.
- [ ] Tính cost cả request, log, cache, WAF và backend.

---

# 91. Bản tóm tắt một trang

~~~text
API TYPES
REST API:
  nhiều feature, API key/usage plan, cache, validation,
  private/edge/regional endpoint, mapping nâng cao

HTTP API:
  gọn hơn, thường rẻ hơn, Lambda/HTTP integration, JWT auth

WebSocket API:
  kết nối hai chiều, route $connect/$disconnect/$default,
  lưu connectionId và callback qua Management API

REQUEST FLOW
Client
  ↓ TLS/DNS
API Gateway route
  ↓ policy/auth
throttling + validation + mapping
  ↓ integration
Lambda / HTTP / VPC Link / AWS service
  ↓
response mapping + log/metric
  ↓
Client

AUTH
IAM        → SigV4, service-to-service
JWT/Cognito→ user/app token
Lambda auth→ logic tùy biến
API key    → usage/quota, không phải auth mạnh

PRIVATE
Private API         → client vào qua VPC endpoint
Private integration → gateway gọi backend private qua VPC Link

RELIABILITY
throttle + backend concurrency + timeout + idempotency
job dài → queue/workflow + 202 + status API

LIMITS CẦN NHỚ
Account/Region → thường 10.000 RPS + burst 5.000, dùng chung các API
REST buffered   → payload 10 MB, timeout mặc định 29 giây
HTTP API        → payload 10 MB, timeout tối đa 30 giây
Lambda sync     → buffered request/response 6 MB mỗi chiều
WebSocket       → message 128 KB, frame 32 KB,
                  idle 10 phút, connection tối đa 2 giờ
File lớn        → S3 pre-signed URL
Job lâu         → asynchronous queue/workflow

OBSERVABILITY
access logs + 4xx/5xx + latency + integration latency
+ backend metrics + tracing + correlation ID
~~~

# 92. Tài liệu AWS chính thức

- [Amazon API Gateway Developer Guide](https://docs.aws.amazon.com/apigateway/latest/developerguide/)
- [Chọn REST API hay HTTP API](https://docs.aws.amazon.com/apigateway/latest/developerguide/http-api-vs-rest.html)
- [API endpoint types cho REST API](https://docs.aws.amazon.com/apigateway/latest/developerguide/api-gateway-api-endpoint-types.html)
- [Khái niệm API Gateway](https://docs.aws.amazon.com/apigateway/latest/developerguide/api-gateway-basic-concept.html)
- [Lambda integrations cho REST API](https://docs.aws.amazon.com/apigateway/latest/developerguide/set-up-lambda-integrations.html)
- [Lambda proxy integration](https://docs.aws.amazon.com/apigateway/latest/developerguide/set-up-lambda-proxy-integrations.html)
- [Lambda authorizer](https://docs.aws.amazon.com/apigateway/latest/developerguide/apigateway-use-lambda-authorizer.html)
- [Cognito User Pool authorizer](https://docs.aws.amazon.com/apigateway/latest/developerguide/apigateway-integrate-with-cognito.html)
- [Private REST APIs](https://docs.aws.amazon.com/apigateway/latest/developerguide/apigateway-private-apis.html)
- [Private integrations](https://docs.aws.amazon.com/apigateway/latest/developerguide/set-up-private-integration.html)
- [API Gateway throttling](https://docs.aws.amazon.com/apigateway/latest/developerguide/api-gateway-request-throttling.html)
- [REST API caching](https://docs.aws.amazon.com/apigateway/latest/developerguide/api-gateway-caching.html)
- [CORS cho REST APIs](https://docs.aws.amazon.com/apigateway/latest/developerguide/how-to-cors.html)
- [WebSocket API overview](https://docs.aws.amazon.com/apigateway/latest/developerguide/apigateway-websocket-api-overview.html)
- [Monitoring API Gateway](https://docs.aws.amazon.com/apigateway/latest/developerguide/monitoring-overview.html)
- [API Gateway account-level quotas](https://docs.aws.amazon.com/apigateway/latest/developerguide/limits.html)
- [REST API quotas](https://docs.aws.amazon.com/apigateway/latest/developerguide/api-gateway-execution-service-limits-table.html)
- [HTTP API quotas](https://docs.aws.amazon.com/apigateway/latest/developerguide/http-api-quotas.html)
- [WebSocket message và frame limits](https://docs.aws.amazon.com/apigateway/latest/developerguide/apigateway-websocket-api-routes-integrations.html)
- [AWS Lambda quotas](https://docs.aws.amazon.com/lambda/latest/dg/gettingstarted-limits.html)
- [API Gateway response streaming](https://docs.aws.amazon.com/apigateway/latest/developerguide/response-transfer-mode.html)


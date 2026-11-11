# Tuần 15 — Tổng hợp theo domain và làm practice questions

> Mục tiêu: kết nối toàn bộ kiến thức AWS SAA-C03 thành các quyết định kiến trúc có thể giải thích được; luyện cách đọc requirement, loại trừ đáp án sai, nhận diện trade-off và xây dựng nhật ký lỗi trước khi bước vào tuần mock exam.

## 1. Tư duy của tuần tổng hợp

Trong 14 tuần trước, bạn đã học từng nhóm dịch vụ riêng lẻ. Tuần 15 không nên được dùng để đọc lại toàn bộ tài liệu theo thứ tự từ đầu đến cuối. Việc quan trọng hơn là luyện khả năng trả lời câu hỏi theo bối cảnh:

- Người dùng cần gì?
- Constraint nào là bắt buộc?
- Đáp án đang tối ưu cho security, reliability, performance, cost hay operational overhead?
- Có từ khóa nào thay đổi hoàn toàn lựa chọn?
- Dịch vụ nào giải quyết trực tiếp vấn đề được nêu?
- Đáp án nào chỉ “nghe có vẻ đúng” nhưng không đáp ứng đầy đủ requirement?

AWS SAA-C03 thường không hỏi:

> Dịch vụ X là gì?

Mà thường hỏi:

> Một hệ thống có các yêu cầu A, B, C. Phương án nào đáp ứng tốt nhất với ít vận hành nhất, chi phí thấp nhất hoặc độ tin cậy cao nhất?

Vì vậy, học thuộc định nghĩa là chưa đủ. Bạn cần luyện chuyển từ requirement sang architecture decision.

## 2. Kết quả cần đạt

Cuối tuần này, bạn cần:

- Tóm tắt được bốn domain trong một trang ghi chú.
- Nhận diện service và pattern chính từ một scenario mới.
- Giải thích tại sao đáp án đúng phù hợp với requirement.
- Giải thích tại sao từng đáp án còn lại không phù hợp.
- Phân biệt các cặp dịch vụ dễ nhầm.
- Hoàn thành các block practice questions có bấm giờ.
- Ghi lại mọi câu sai theo nguyên nhân, không chỉ ghi đáp án đúng.
- Có danh sách weak areas để chuyển sang Tuần 16.
- Đạt độ chính xác tăng dần và hiểu trade-off, thay vì chỉ ghi nhớ vị trí đáp án.
- Hoàn thành mục tiêu tổng cộng 300–500 câu chất lượng trước kỳ thi.

---

## 3. Bốn domain và cách ưu tiên ôn tập

| Domain | Trọng tâm | Câu hỏi thường kiểm tra |
|---|---|---|
| Domain 1 — Secure Architectures | IAM, network security, encryption, data protection | Ai được phép làm gì? Dữ liệu được bảo vệ thế nào? |
| Domain 2 — Resilient Architectures | Multi-AZ, backup, retry, failover, DR | Hệ thống phản ứng ra sao khi thành phần lỗi? |
| Domain 3 — High-Performing Architectures | Scaling, caching, database access, CDN, streaming | Làm sao đạt throughput/latency yêu cầu? |
| Domain 4 — Cost-Optimized Architectures | Pricing, right-sizing, lifecycle, governance | Làm sao đáp ứng requirement với chi phí phù hợp? |

Một câu hỏi có thể liên quan nhiều domain. Ví dụ:

- Dùng VPC Endpoint cho S3 vừa liên quan security vừa cost.
- Dùng CloudFront signed URL vừa liên quan performance vừa security.
- Dùng SQS giữa các service vừa liên quan resilience vừa performance.
- Dùng Multi-AZ RDS vừa liên quan resilience vừa cost.

Khi đó, hãy xác định domain hoặc requirement đang được ưu tiên trong wording. Một đáp án có thể tốt về performance nhưng không phải đáp án đúng nếu câu hỏi nhấn mạnh security hoặc operational overhead.

---

## 4. Quy trình làm một câu hỏi

Dùng quy trình sau cho mọi câu, kể cả câu tưởng như dễ.

### Bước 1: Đọc câu hỏi trước khi đọc toàn bộ scenario

Xác định đề đang hỏi điều gì:

- Most secure?
- Most cost-effective?
- Least operational overhead?
- Highest availability?
- Lowest latency?
- Near-real-time?
- Durable?
- Scalable?
- Fault tolerant?
- Minimal changes?
- Best long-term solution?

Từ khóa này là mục tiêu tối ưu của câu hỏi.

### Bước 2: Tách requirement bắt buộc và mong muốn

Ví dụ:

    Bắt buộc:
    - Dữ liệu không được public.
    - Có failover tự động.
    - Không quản lý server.

    Mong muốn:
    - Chi phí thấp.
    - Latency thấp.
    - Ít thay đổi ứng dụng.

Không để một requirement phụ làm lu mờ requirement bắt buộc.

### Bước 3: Xác định workload

Hỏi:

- Read-heavy hay write-heavy?
- Request/response hay asynchronous?
- Stateful hay stateless?
- Traffic ổn định hay burst?
- Dữ liệu quan hệ hay key-value/document?
- File nhỏ hay object lớn?
- Dữ liệu nóng hay archive?
- Cần consistency mạnh hay eventual consistency chấp nhận được?

### Bước 4: Vẽ mental model ngắn

Không cần vẽ sơ đồ đẹp. Chỉ cần xác định đường đi:

    User → DNS → CDN/WAF → Load Balancer → Compute → Database
                                                   ↓
                                                 Cache
    Async side path → Queue → Worker → Notification

Mental model giúp nhận ra:

- Thành phần nào public.
- Thành phần nào private.
- Điểm lỗi đơn.
- Chỗ có thể cache.
- Chỗ cần queue.
- Chỗ phát sinh cross-AZ hoặc NAT cost.

### Bước 5: Loại trừ đáp án sai rõ ràng

Loại các đáp án:

- Không đáp ứng requirement bắt buộc.
- Dùng dịch vụ sai mục đích.
- Tăng operational overhead trái với wording.
- Làm mất durability hoặc availability.
- Cần thay đổi ứng dụng quá nhiều khi đề yêu cầu minimal changes.
- Dùng một tính năng không tồn tại.
- Giải quyết triệu chứng nhưng không giải quyết bottleneck.

### Bước 6: So sánh hai đáp án gần đúng

Đây thường là phần khó nhất. Hãy đặt cạnh nhau:

| Tiêu chí | Đáp án A | Đáp án B |
|---|---|---|
| Đáp ứng requirement chính | | |
| Operational overhead | | |
| Cost | | |
| Performance | | |
| Failure behavior | | |
| Thay đổi ứng dụng | | |
| Từ khóa trong đề | | |

Chọn đáp án có mức phù hợp tổng thể cao hơn với mục tiêu mà đề nhấn mạnh.

### Bước 7: Viết lý do trước khi xem giải thích

Với mỗi câu, ghi:

- Tôi chọn gì?
- Requirement quyết định là gì?
- Vì sao lựa chọn này phù hợp?
- Hai đáp án gần nhất sai ở điểm nào?

Làm như vậy giúp bạn phát hiện mình sai do thiếu kiến thức hay do đọc sai wording.

### Bước 8: Phân loại lỗi

Mỗi câu sai nên thuộc ít nhất một nhóm:

- Service gap: chưa biết service hoặc feature.
- Architecture gap: biết service nhưng chưa biết ghép vào hệ thống.
- Requirement gap: bỏ sót điều kiện trong đề.
- Wording trap: bị đánh lừa bởi từ khóa.
- Comparison gap: nhầm hai dịch vụ gần giống.
- Calculation/cost gap: nhầm đơn vị hoặc cost driver.
- Time-management gap: biết nhưng không kịp suy luận.

---

# Domain 1 — Secure Architectures

## 5. Mục tiêu của Domain 1

Bạn cần thiết kế hệ thống:

- Chỉ cho đúng principal truy cập đúng resource.
- Giảm blast radius khi credential hoặc service bị compromise.
- Bảo vệ dữ liệu khi lưu trữ và truyền tải.
- Tách public/private boundary rõ ràng.
- Có audit trail và khả năng điều tra.
- Không để secrets hoặc key xuất hiện trong source code.
- Có thể đáp ứng yêu cầu cross-account và governance.

Security trong đề thi thường là bài toán “đặt quyền ở đâu, trên đối tượng nào và với điều kiện nào”.

## 6. IAM: identity, permission và policy evaluation

### 6.1. Các principal cơ bản

| Principal | Cách dùng |
|---|---|
| IAM user | User hoặc ứng dụng cũ cần identity riêng; không nên dùng cho workload mới |
| IAM group | Gom user để cấp permission |
| IAM role | Identity có thể assume, phù hợp cho workload và cross-account |
| AWS service role | Role để AWS service thực hiện hành động |
| Resource policy | Policy gắn trên resource như S3 bucket, SQS queue, SNS topic, KMS key |

Best practice quan trọng:

- Người dùng dùng IAM Identity Center hoặc federation.
- Workload dùng IAM role.
- Không hardcode access key trong code.
- Dùng temporary credentials.
- Root user chỉ dùng cho tác vụ đặc biệt.
- Least privilege.
- MFA cho các tài khoản nhạy cảm.
- Rotate hoặc loại bỏ credential dài hạn không cần thiết.

### 6.2. Identity policy và resource policy

Identity-based policy gắn vào user, group hoặc role và mô tả principal có thể làm gì.

Resource-based policy gắn vào resource và mô tả principal nào được phép truy cập resource.

Ví dụ:

- S3 bucket policy.
- SQS queue policy.
- SNS topic policy.
- KMS key policy.
- IAM role trust policy.

Một lỗi phổ biến là nhầm:

- Trust policy: ai được assume role.
- Permission policy: role được làm gì sau khi assume.

### 6.3. Role trust policy

Role có hai phần logic:

    Trust policy:
    Ai được phép assume role?

    Permission policy:
    Principal sau khi assume được phép làm gì?

Ví dụ cross-account:

    Account A:
    User hoặc role cần truy cập

    Account B:
    Target role
    ├── Trust policy cho phép principal từ Account A assume
    └── Permission policy cho phép truy cập resource

Cả hai phía phải đúng. Account A cần permission gọi sts:AssumeRole; role ở Account B phải trust principal đó. Sau khi assume thành công, quyền sử dụng resource đến từ permission policy của role và các resource policy/boundary liên quan.

### 6.4. Permission evaluation

Khi phân tích câu hỏi IAM, nhớ các nguyên tắc:

- Mặc định là deny.
- Explicit deny thắng allow.
- SCP không tự cấp quyền.
- Permission boundary giới hạn quyền tối đa của identity.
- Session policy có thể giới hạn session.
- Resource policy có thể tham gia quyết định truy cập.
- IAM policy phải dùng action và resource đúng.
- Một allow ở policy này không vượt qua explicit deny ở policy khác.

Mental model:

    Effective permission
    = quyền được grant
    − các giới hạn và explicit deny

Đừng kết luận “role có AdministratorAccess nên chắc chắn làm được”. SCP, permission boundary, resource policy hoặc key policy vẫn có thể chặn.

## 7. Cross-account access

### Khi nào dùng cross-account role?

- Central security account cần audit member accounts.
- Shared services account cung cấp tài nguyên dùng chung.
- Developer account cần deploy vào staging.
- Một account cần đọc dữ liệu ở account khác.
- Tách production khỏi non-production.

### Checklist cross-account

1. Target account có role không?
2. Trust policy có tin cậy đúng principal không?
3. Source principal có quyền sts:AssumeRole không?
4. Permission policy của role có đúng action/resource không?
5. SCP có chặn không?
6. Resource policy có yêu cầu thêm không?
7. KMS key policy có cho phép principal/role không?
8. External ID có cần cho third-party không?
9. Session duration và MFA có đúng requirement không?

### Những lỗi thường gặp

- Dùng user access key thay vì role.
- Chỉ sửa trust policy nhưng quên permission policy.
- Cho phép toàn bộ account thay vì role cụ thể.
- Dùng Principal quá rộng.
- Quên KMS key policy khi dữ liệu được mã hóa bằng customer managed key.
- Quên SCP ở organization root hoặc OU.

## 8. VPC security

### 8.1. Public và private subnet

Subnet được xem là public khi route table có route đến Internet Gateway. Một resource có public IP không tự làm subnet trở thành public nếu route table không có đường phù hợp; ngược lại, public subnet không có nghĩa mọi resource bắt buộc phải public.

Pattern an toàn thường gặp:

    Internet
       ↓
    Internet-facing ALB
       ↓
    Private application subnet
       ↓
    Private database subnet

Private resource cần outbound:

- NAT Gateway cho Internet.
- Gateway Endpoint cho S3/DynamoDB.
- Interface Endpoint cho service hỗ trợ PrivateLink.

Không đặt database ở public subnet chỉ vì application cần kết nối đến database.

### 8.2. Security Group

Security Group:

- Stateful.
- Áp dụng ở ENI/resource.
- Chỉ có allow rules.
- Return traffic được cho phép tự động nếu flow hợp lệ.
- Có thể reference SG khác làm source/destination.

Ví dụ:

    ALB SG:
    inbound 443 từ Internet

    App SG:
    inbound application port từ ALB SG

    DB SG:
    inbound database port từ App SG

Đây là pattern tốt hơn việc mở DB port cho 0.0.0.0/0 hoặc cho toàn bộ subnet không cần thiết.

### 8.3. Network ACL

NACL:

- Stateless.
- Áp dụng ở subnet.
- Có allow và deny.
- Cần rules cho cả chiều vào và chiều ra.
- Có rule number; rule phù hợp đầu tiên được áp dụng.

Dùng NACL cho subnet-level guardrail hoặc deny cụ thể. Dùng Security Group cho kiểm soát truy cập giữa các application components.

So sánh:

| Đặc điểm | Security Group | NACL |
|---|---|---|
| Phạm vi | ENI/resource | Subnet |
| State | Stateful | Stateless |
| Rule | Allow | Allow và deny |
| Return traffic | Tự động | Phải cho phép riêng |
| Use case | App-to-app access | Subnet boundary/deny |

### 8.4. VPC Endpoint

- Gateway Endpoint: thường dùng cho S3 và DynamoDB, tích hợp route table.
- Interface Endpoint: ENI private IP, dùng AWS PrivateLink, có phí theo giờ và data processing.

Security vẫn phải kiểm soát qua:

- Endpoint policy.
- IAM policy.
- Bucket/queue/resource policy.
- Security group của endpoint ENI.
- DNS configuration.

Dùng endpoint không có nghĩa là mọi principal được truy cập mọi resource.

## 9. Encryption và key management

### 9.1. Encryption at rest

Các ví dụ:

- EBS encryption.
- RDS encryption.
- S3 SSE-S3 hoặc SSE-KMS.
- DynamoDB encryption.
- EFS encryption.
- Secrets Manager encryption.
- CloudWatch Logs encryption tùy thiết kế.

Cần phân biệt:

- AWS owned key.
- AWS managed key.
- Customer managed KMS key.

Customer managed key cho nhiều quyền kiểm soát hơn:

- Key policy.
- Rotation.
- Disable/delete schedule.
- Cross-account access.
- Audit.
- Grant.

Nhưng cũng tăng operational responsibility.

### 9.2. KMS key policy

KMS có điểm dễ nhầm: IAM policy không phải lúc nào cũng đủ để cho phép sử dụng key; key policy phải cho phép phù hợp hoặc có delegation hợp lệ.

Khi câu hỏi có cross-account encryption:

- Kiểm tra IAM permission.
- Kiểm tra KMS key policy.
- Kiểm tra resource policy của dịch vụ.
- Kiểm tra Region của key và resource.
- Kiểm tra role đang được dùng thực sự.

### 9.3. Encryption in transit

Dùng TLS cho:

- Client đến ALB/CloudFront.
- Service-to-service communication.
- Database connection khi được hỗ trợ.
- API Gateway.
- S3 HTTPS endpoint.
- Internal service nếu threat model yêu cầu.

ACM dùng để quản lý certificate. Hãy chú ý:

- Certificate phải ở Region phù hợp với service.
- CloudFront thường yêu cầu certificate ở Region đặc biệt theo quy định của dịch vụ.
- Certificate public và private có use case khác nhau.
- TLS termination ở load balancer không đồng nghĩa traffic bên trong luôn được mã hóa; nếu requirement yêu cầu end-to-end encryption, cần cấu hình tiếp đoạn backend.

### 9.4. Secrets Manager và Parameter Store

Dùng Secrets Manager khi:

- Lưu database credential hoặc secret cần rotation.
- Cần quản lý vòng đời secret.
- Có requirement rotation tự động.

Parameter Store phù hợp với:

- Configuration.
- Non-secret parameter.
- Secret đơn giản theo nhu cầu.
- Tích hợp với IAM và KMS.

Không lưu secret trong:

- Source code.
- AMI không được bảo vệ.
- User data dạng plain text.
- Environment variable nếu không có kiểm soát và encryption phù hợp.
- Log.

## 10. S3 security

Checklist:

- Block Public Access bật ở account/bucket khi có thể.
- Bucket policy không mở public ngoài chủ đích.
- IAM policy theo least privilege.
- SSE-KMS nếu cần key control/audit.
- Bucket owner enforced cho object ownership.
- Versioning cho khả năng phục hồi.
- Access logging hoặc CloudTrail data events tùy nhu cầu audit.
- Pre-signed URL cho truy cập tạm thời thay vì bucket public.
- VPC Endpoint và endpoint policy cho private access.
- Lifecycle cho object và version cũ.
- Không nhầm encryption với authorization.

### Public access và pre-signed URL

Nếu người dùng cần tải object trực tiếp nhưng bucket không được public:

1. Ứng dụng xác thực user.
2. Ứng dụng tạo pre-signed URL thời hạn ngắn.
3. Client tải trực tiếp từ S3.
4. Bucket vẫn private.

Nếu cần kiểm soát phân phối qua CDN, có thể dùng CloudFront signed URL/cookie.

## 11. WAF, Shield, CloudTrail và Config

### AWS WAF

WAF lọc request ở lớp ứng dụng, ví dụ:

- SQL injection pattern.
- XSS pattern.
- IP reputation.
- Rate-based rule.
- Geo restriction.
- Custom header hoặc path rule.

WAF không thay thế Security Group và không phải công cụ chống mọi loại DDoS.

### AWS Shield

Shield bảo vệ chống DDoS ở các lớp được hỗ trợ. Shield Advanced cung cấp mức bảo vệ và visibility cao hơn với chi phí/điều kiện riêng.

### CloudTrail

CloudTrail ghi API activity và hỗ trợ audit:

- Ai gọi API?
- Gọi lúc nào?
- Từ đâu?
- Action gì?
- Resource nào?
- Thành công hay thất bại?

Pattern production tốt:

- Bật organization trail.
- Ghi về log archive account.
- Bảo vệ log bucket.
- Bật validation hoặc kiểm soát integrity phù hợp.
- Tách quyền đọc/xóa log.
- Theo dõi các API nhạy cảm.

### AWS Config

AWS Config theo dõi configuration state và compliance rule, ví dụ:

- Security group mở 0.0.0.0/0.
- S3 bucket public.
- EBS chưa mã hóa.
- Resource thiếu tag.
- Resource ở Region không được phép.

CloudTrail trả lời “ai đã gọi API gì”; Config trả lời “resource hiện có configuration/compliance như thế nào”.

---

# Domain 2 — Resilient Architectures

## 12. Mục tiêu của Domain 2

Bạn cần thiết kế hệ thống có thể:

- Tiếp tục phục vụ khi một instance, AZ hoặc thành phần lỗi.
- Tự phục hồi thay vì phụ thuộc vào thao tác thủ công.
- Không mất message khi consumer tạm thời lỗi.
- Khôi phục dữ liệu theo RPO/RTO.
- Có retry, timeout, backoff và DLQ hợp lý.
- Giảm blast radius của một failure.
- Có chiến lược DR phù hợp với business requirement.

Resilience không chỉ là “deploy nhiều server”. Cần xem cả state, dependency, retry behavior và cách restore.

## 13. Multi-AZ, health check và Auto Scaling

### 13.1. Multi-AZ

Multi-AZ bảo vệ khỏi lỗi ở một Availability Zone. Nhưng để có giá trị:

- Resource phải thực sự được phân bố ở nhiều AZ.
- Load balancer phải có target ở nhiều AZ.
- Auto Scaling Group phải dùng subnet ở nhiều AZ.
- Database phải có cơ chế failover hoặc replica phù hợp.
- Session state không được gắn vào một instance duy nhất.
- Shared storage hoặc replication phải được thiết kế đúng.

### 13.2. Health check

Health check cần kiểm tra đúng loại failure:

- Instance còn running không?
- Process còn sống không?
- Application có trả response hợp lệ không?
- Dependency quan trọng có bị lỗi không?
- Có nên loại target khỏi traffic khi dependency tạm thời chậm không?

Health check quá nông có thể giữ một instance “healthy” nhưng thực tế không phục vụ được. Health check quá sâu có thể làm cascade failure nếu dependency tạm thời lỗi.

### 13.3. Auto Scaling

Auto Scaling giúp:

- Thay instance unhealthy.
- Tăng capacity khi traffic tăng.
- Giảm capacity khi traffic giảm.
- Chạy nhiều AZ.
- Kết hợp On-Demand và Spot.

Cần thiết kế:

- Min/desired/max.
- Health check type.
- Warm-up.
- Graceful termination.
- Lifecycle hook nếu cần drain request.
- Launch template/AMI đúng.
- Metric và alarm phản ánh workload.

Auto Scaling không sửa được ứng dụng stateful nếu session hoặc local data không được externalize.

## 14. Load balancing và failure isolation

### ALB

Phù hợp với:

- HTTP/HTTPS.
- Path-based routing.
- Host-based routing.
- WebSocket hoặc HTTP-aware behavior.
- Integration với WAF.

### NLB

Phù hợp với:

- TCP/UDP/TLS.
- High throughput và low latency.
- Static IP hoặc Elastic IP use case.
- Non-HTTP protocol.

### Gateway Load Balancer

Dùng để triển khai và scale network virtual appliances như firewall hoặc inspection appliance.

### Health check và deregistration

Khi target lỗi:

1. Load balancer ngừng gửi request mới.
2. Target có thể cần thời gian drain request hiện tại.
3. Auto Scaling thay target hoặc sửa capacity.
4. Traffic tiếp tục qua target khỏe ở AZ khác.

Đừng nhầm “load balancer có nhiều AZ” với “application đã resilient”. Backend, database và dependency cũng phải xử lý failure.

## 15. Decoupling: SQS, SNS và EventBridge

### 15.1. SQS

SQS tạo buffer giữa producer và consumer.

Dùng khi:

- Consumer xử lý chậm hơn producer.
- Muốn retry.
- Muốn scale worker độc lập.
- Muốn giảm coupling.
- Chấp nhận xử lý asynchronous.
- Cần giữ message khi consumer tạm thời không hoạt động.

Các khái niệm:

- Visibility timeout.
- Message retention.
- Long polling.
- Dead-letter queue.
- Standard queue.
- FIFO queue.
- Deduplication.
- Message group ID.
- Approximate receive count.

### 15.2. Visibility timeout

Khi consumer nhận message:

- Message tạm thời không hiển thị với consumer khác.
- Consumer phải xử lý và delete message trước khi visibility timeout hết.
- Nếu xử lý lâu hơn timeout, message có thể xuất hiện lại.
- Nếu xử lý fail, message xuất hiện lại để retry.

Cần đặt visibility timeout đủ dài hơn thời gian xử lý thông thường, nhưng không quá dài khiến retry bị trì hoãn.

### 15.3. Idempotency

SQS có thể giao message nhiều lần. Consumer phải idempotent:

- Dùng idempotency key.
- Lưu trạng thái đã xử lý.
- Dùng conditional write.
- Tránh charge payment hoặc gửi notification trùng.
- Tách “nhận message” khỏi “đảm bảo business operation chỉ xảy ra một lần”.

Exactly-once business effect thường cần thiết kế ở application/data layer, không chỉ dựa vào tên FIFO.

### 15.4. SNS

SNS phù hợp với pub/sub fan-out:

    Một event
      ├── SQS queue cho billing
      ├── SQS queue cho analytics
      ├── Lambda cho notification
      └── HTTP endpoint cho integration

SNS decouple publisher khỏi nhiều subscriber. Nếu subscriber chậm hoặc cần retry độc lập, kết hợp SNS với SQS.

### 15.5. EventBridge

EventBridge phù hợp với event bus và routing theo rule:

- Event pattern filtering.
- Nhiều event source.
- Cross-account event routing.
- SaaS integration.
- Scheduled event.
- Archive/replay tùy use case.

So sánh nhanh:

| Nhu cầu | Dịch vụ thường phù hợp |
|---|---|
| Buffer và worker pull | SQS |
| Fan-out đơn giản | SNS |
| Event bus, routing theo event pattern | EventBridge |
| Xử lý tuần tự giữa các bước | Step Functions |
| Streaming record throughput cao | Kinesis |

## 16. Retry, backoff và DLQ

### Retry đúng cách

Một retry policy cần:

- Giới hạn số lần thử.
- Exponential backoff.
- Jitter.
- Phân biệt lỗi tạm thời và lỗi vĩnh viễn.
- Timeout riêng.
- Idempotency.
- Circuit breaker hoặc rate limit khi cần.

Retry không có backoff có thể tạo retry storm:

    Service A lỗi
      ↓
    Service B retry liên tục
      ↓
    A càng quá tải
      ↓
    B retry mạnh hơn
      ↓
    Cascade failure

### Dead-letter queue

DLQ giữ message không xử lý được sau số lần retry nhất định. DLQ giúp:

- Không làm kẹt main queue.
- Điều tra payload lỗi.
- Reprocess sau khi sửa bug.
- Tách poison message khỏi traffic bình thường.

DLQ không phải nơi để bỏ quên dữ liệu. Cần:

- Alarm theo số lượng hoặc age.
- Quy trình điều tra.
- Retention đủ dài.
- Cách replay an toàn.
- Idempotency khi replay.

## 17. RDS, Aurora và database resilience

### 17.1. RDS Multi-AZ

RDS Multi-AZ dùng standby cho availability và failover. Nó không phải read scaling solution.

Phù hợp với:

- Failover tự động.
- Database production cần HA.
- Giảm thời gian khôi phục khi primary lỗi.

Không nên dùng Multi-AZ để trả lời yêu cầu “tăng read throughput”. Khi đó xem read replica, caching hoặc scale database.

### 17.2. Read replica

Read replica phù hợp với:

- Read scaling.
- Reporting.
- Read workload có thể chấp nhận replica lag.
- Offload query khỏi primary.

Read replica không thay thế Multi-AZ cho automatic failover cùng mục đích. Có thể dùng cả hai.

### 17.3. Aurora

Aurora cung cấp storage phân tán và nhiều tính năng HA/read scaling. Nhưng vẫn cần kiểm tra:

- Cluster endpoint và reader endpoint.
- Failover behavior.
- Số read replica.
- Region và cross-Region replication.
- Chi phí.
- Engine compatibility.
- Backup/PITR.

### 17.4. Backup và PITR

Backup strategy cần xác định:

- RPO: mất tối đa bao nhiêu dữ liệu?
- RTO: khôi phục trong bao lâu?
- Retention.
- Cross-Region copy.
- Cross-account isolation.
- Restore test.
- Điểm khôi phục cần thiết.
- Ai có quyền xóa backup?

Backup chưa được test restore thì chưa phải là recovery plan đáng tin cậy.

## 18. S3 và DynamoDB resilience

### S3

Các cơ chế:

- Versioning.
- Lifecycle.
- Replication.
- Object Lock cho retention/WORM.
- Cross-Region Replication.
- Replication Time Control nếu requirement cần kiểm soát thời gian replication.
- MFA Delete trong use case phù hợp.

Versioning giúp khôi phục object bị overwrite hoặc xóa nhầm, nhưng không tự động bảo vệ khỏi mọi lỗi logic. Nếu ứng dụng xóa hợp lệ cả current và noncurrent version, cần thêm retention/replication/backup phù hợp.

### DynamoDB

Các cơ chế:

- Point-in-time recovery.
- On-demand backup.
- Global Tables cho multi-Region active-active.
- Streams cho event/change capture.
- Conditional writes.
- GSI/LSI theo access pattern.

Global Tables tăng availability và giảm latency đa Region nhưng có complexity về conflict, consistency, replication và cost. Không chọn chỉ vì nghe “global”.

## 19. RTO, RPO và disaster recovery

### RPO

Recovery Point Objective là lượng dữ liệu tối đa có thể mất.

Ví dụ:

- RPO 24 giờ: có thể chấp nhận mất dữ liệu trong một ngày.
- RPO gần bằng 0: cần replication hoặc cơ chế capture liên tục.

### RTO

Recovery Time Objective là thời gian tối đa để khôi phục dịch vụ.

Ví dụ:

- RTO 5 phút: cần automation, warm capacity hoặc active-active.
- RTO 8 giờ: backup và restore có thể đủ.

### DR strategies

| Strategy | RTO/RPO tương đối | Cost/complexity |
|---|---|---|
| Backup and restore | Cao hơn | Thấp |
| Pilot light | Trung bình | Thấp–trung bình |
| Warm standby | Thấp hơn | Trung bình–cao |
| Multi-site active-active | Thấp nhất | Cao nhất |

Câu hỏi chọn DR phải dựa trên RTO/RPO, không dựa trên việc architecture nào “xịn” hơn.

---

# Domain 3 — High-Performing Architectures

## 20. Mục tiêu của Domain 3

Bạn cần có khả năng:

- Nhận diện bottleneck.
- Chọn đúng compute và storage.
- Scale horizontally hoặc vertically hợp lý.
- Dùng cache mà không phá correctness.
- Thiết kế database theo access pattern.
- Dùng asynchronous processing để hấp thụ burst.
- Tối ưu latency, throughput và tail latency.
- Chọn dịch vụ ingestion/streaming phù hợp.

Performance không đồng nghĩa với việc dùng tài nguyên lớn nhất. Performance phải được đo trong bối cảnh SLO và workload thực tế.

## 21. Performance methodology

Quy trình:

1. Định nghĩa latency, throughput và error rate cần đạt.
2. Ghi baseline.
3. Xác định bottleneck.
4. Đặt giả thuyết.
5. Thử thay đổi nhỏ.
6. Benchmark với traffic đại diện.
7. Kiểm tra correctness và failure behavior.
8. Đo cost.
9. Giữ hoặc rollback.

Metric quan trọng:

- p50, p95, p99 latency.
- Requests per second.
- Queue age.
- Error rate.
- CPU/memory.
- Database connections.
- Cache hit ratio.
- EBS IOPS/throughput.
- Network throughput.
- Throttles.
- Cold starts.

Không tối ưu chỉ dựa trên average latency vì tail latency có thể quyết định trải nghiệm người dùng.

## 22. EC2 family và scaling

### Chọn instance family

| Family | Workload |
|---|---|
| General purpose | Web/app phổ biến |
| Compute optimized | CPU-heavy |
| Memory optimized | In-memory, large cache, analytics |
| Storage optimized | Local high I/O |
| Accelerated computing | GPU/FPGA/ML/specialized |

Cần xét:

- vCPU.
- Memory.
- Network bandwidth.
- EBS bandwidth.
- Local NVMe.
- Architecture x86/ARM.
- Burst capability.
- Price/performance.

### Horizontal và vertical scaling

Horizontal scaling:

- Thêm instance.
- Phù hợp stateless service.
- Có thể dùng Auto Scaling.
- Cần load balancing và externalized state.

Vertical scaling:

- Đổi instance lớn hơn.
- Đơn giản nhưng có giới hạn.
- Có thể tạo downtime hoặc failover.
- Phù hợp một số database/stateful workload.

Trong đề, nếu service stateless và traffic tăng, horizontal scaling thường là hướng phù hợp hơn một máy lớn duy nhất.

## 23. EBS, EFS và S3

### EBS

- Block storage cho một instance hoặc pattern attach phù hợp.
- Latency thấp hơn object storage.
- Có loại volume với IOPS/throughput khác nhau.
- Snapshot incremental.
- Không phải shared filesystem mặc định.

### EFS

- Shared file system.
- Nhiều compute node truy cập cùng dữ liệu.
- Elastic storage.
- Phù hợp container/serverless cần shared POSIX filesystem.
- Có latency/cost khác EBS và S3.

### S3

- Object storage.
- Durability cao.
- Scale lớn.
- Phù hợp static assets, file, data lake, backup.
- Không được dùng như POSIX filesystem cho mọi application.

Câu hỏi chọn storage phải dựa trên access semantics, latency, sharing, durability và cost.

## 24. CloudFront, Route 53, ALB và NLB

### CloudFront

Dùng cho:

- Static asset.
- Dynamic content có thể cache.
- Global edge delivery.
- Giảm latency.
- Giảm origin load.
- Kết hợp WAF và signed URL/cookie.

### Route 53

Các routing policy chính:

- Simple.
- Weighted.
- Latency-based.
- Failover.
- Geolocation/geoproximity tùy use case.
- Multi-value answer.

Nhớ phân biệt:

- Latency-based routing chọn Region có latency tốt hơn, không phải route dựa trên tải backend.
- Weighted routing hỗ trợ traffic split/canary.
- Failover cần health check và primary/secondary logic.
- Alias record hỗ trợ AWS resource phù hợp.

### ALB và NLB

- ALB: Layer 7, HTTP-aware, path/host routing.
- NLB: Layer 4, TCP/UDP/TLS, high performance, static IP use case.

Nếu cần redirect/path-based routing, ALB thường phù hợp. Nếu cần TCP/UDP hoặc static IP, xem NLB.

## 25. Caching

### Cache ở đâu?

- CloudFront: edge/global content.
- API Gateway cache: response API trong phạm vi API stage.
- ElastiCache: application/database cache.
- DAX: DynamoDB-specific read cache.
- Browser cache: client-side.
- S3/CloudFront cache cho static object.

### Cache-aside

Quy trình:

1. Ứng dụng đọc cache.
2. Nếu hit, trả dữ liệu.
3. Nếu miss, đọc database.
4. Ghi kết quả vào cache với TTL.
5. Trả response.

Ưu điểm:

- Ứng dụng kiểm soát dữ liệu cần cache.
- Không phải mọi dữ liệu đều đi qua cache.

Rủi ro:

- Stale data.
- Cache stampede.
- Hot key.
- Invalidation phức tạp.
- Memory pressure.

### Write-through và write-behind

Write-through ghi cache đồng thời với database, giúp cache mới hơn nhưng tăng write path.

Write-behind ghi vào cache trước và ghi database sau, giảm latency nhưng rủi ro mất dữ liệu nếu cache lỗi và phức tạp hơn.

### DAX và ElastiCache

| Nhu cầu | Dịch vụ |
|---|---|
| Cache cho DynamoDB với thay đổi ứng dụng ít | DAX |
| Cache linh hoạt cho nhiều database/app | ElastiCache |
| Edge cache toàn cầu | CloudFront |
| Cache response API managed | API Gateway cache |

Không dùng DAX để giải quyết thiết kế partition key sai hoặc workload write-heavy không phù hợp.

## 26. RDS read scaling và DynamoDB key design

### RDS read scaling

Các hướng:

- Read replica.
- ElastiCache.
- Query optimization.
- Connection pooling.
- Read/write splitting.
- Aurora reader endpoint.
- Partitioning/sharding khi phù hợp.

Nếu bottleneck là write lock hoặc storage I/O, thêm read replica có thể không giải quyết được.

### DynamoDB access patterns

DynamoDB yêu cầu thiết kế key theo query pattern trước:

- Partition key có cardinality đủ cao.
- Tránh hot partition.
- Sort key cho range query hoặc grouping.
- GSI cho access pattern bổ sung.
- LSI cho alternate sort key trong cùng partition.
- Conditional write để bảo vệ concurrency.
- Query thay vì Scan.
- Projection phù hợp.

Các vấn đề thường gặp:

- Partition key có giá trị cố định.
- Ghi quá nhiều vào một partition.
- Dùng Scan cho request online.
- Tạo quá nhiều GSI không cần thiết.
- Không đặt TTL cho dữ liệu hết hạn.
- Nhầm eventual với strong consistency.

## 27. Lambda concurrency và API Gateway

### Lambda concurrency

Các khái niệm:

- Account concurrency limit.
- Reserved Concurrency.
- Provisioned Concurrency.
- Burst concurrency.
- Throttling.
- Retry behavior.

Reserved Concurrency:

- Đặt giới hạn cho function.
- Bảo vệ downstream khỏi quá tải.
- Đồng thời giữ phần capacity dành riêng cho function đó.

Provisioned Concurrency:

- Giảm cold start.
- Tạo execution environment sẵn.
- Có thêm chi phí.
- Phù hợp latency-sensitive function.

Nếu downstream chỉ chịu được 100 concurrent calls, không nên để Lambda scale tự do lên hàng nghìn mà không có concurrency control.

### API Gateway

Có thể dùng:

- Throttling.
- Usage plan/API key cho client phù hợp.
- Caching.
- Authorization.
- Integration với Lambda, HTTP hoặc AWS service.
- REST API, HTTP API theo tính năng và cost requirement.

API Gateway không thay thế authentication/authorization toàn bộ; cần chọn Cognito, IAM, Lambda authorizer hoặc JWT phù hợp.

## 28. Kinesis và Firehose

### Kinesis Data Streams

Phù hợp:

- Streaming cần đọc lại/replay.
- Nhiều consumer độc lập.
- Control shard/partition.
- Near-real-time processing.
- Custom consumer logic.

Cần chú ý:

- Partition key.
- Shard capacity.
- Ordering trong shard.
- Retention.
- Consumer lag.
- Hot shard.

### Kinesis Data Firehose

Phù hợp:

- Ingest và deliver managed đến S3, Redshift, OpenSearch hoặc destination hỗ trợ.
- Không cần tự quản lý consumer.
- Near-real-time nhưng thường có buffering delay.
- Pipeline đơn giản hơn Data Streams.

Chọn Data Streams nếu cần replay/custom consumers; chọn Firehose nếu cần delivery managed.

## 29. Asynchronous processing và partitioning

Asynchronous processing giúp:

- Tách request latency khỏi công việc dài.
- Hấp thụ burst.
- Scale worker độc lập.
- Retry.
- Giảm coupling.

Nhưng phải xử lý:

- Eventual consistency.
- Duplicate message.
- Job status.
- DLQ.
- User experience khi kết quả chưa sẵn sàng.

Partitioning/sharding giúp scale dữ liệu và throughput nhưng tăng complexity. Chỉ dùng khi một node hoặc một partition không đủ; đừng partition trước khi hiểu access pattern.

---

# Domain 4 — Cost-Optimized Architectures

## 30. Mục tiêu của Domain 4

Bạn cần biết:

- Cost driver nằm ở đâu.
- Khi nào chọn On-Demand, Reserved, Savings Plans hoặc Spot.
- Cách giảm idle capacity.
- Cách dùng lifecycle và tiering.
- Cách giảm NAT và data transfer cost.
- Cách gắn cost với owner/project.
- Cách dùng budget và governance để ngăn cost drift.

Cost optimization luôn phải giữ các requirement bắt buộc về security, availability và performance.

## 31. Pricing model và baseline

### On-Demand

- Linh hoạt.
- Phù hợp workload chưa ổn định.
- Đơn giá cao hơn nếu chạy liên tục.

### Reserved Instances

- Phù hợp baseline ổn định.
- Có Standard/Convertible với mức linh hoạt khác nhau.
- Cần commitment.

### Savings Plans

- Commitment theo mức compute spend.
- Compute Savings Plans linh hoạt hơn.
- Phù hợp usage ổn định nhưng có thể thay đổi loại compute.

### Spot

- Giá thấp.
- Có thể bị interrupt.
- Phù hợp batch, stateless hoặc checkpointable workload.

### Right-sizing

Đánh giá:

- CPU.
- Memory.
- Network.
- IOPS/throughput.
- Connections.
- p95/p99 latency.
- Peak.
- Downstream limit.

## 32. Storage, request và transfer cost

Nhớ ba nhóm:

    Storage cost:
    GB-month, backup, snapshot, version, archive

    Request cost:
    PUT/GET, API call, Lambda invocation, message, state transition

    Transfer cost:
    Internet egress, cross-AZ, cross-Region, NAT processing

Cách giảm:

- Lifecycle.
- Intelligent-Tiering.
- Delete orphan resources.
- Cache.
- Compression.
- Batch.
- Gateway Endpoint.
- CloudFront.
- Schedule non-production.
- Giảm polling.
- Đặt retention logs.

## 33. Governance cho cost

### Cost allocation tags

Nên có:

- Application.
- Environment.
- Owner.
- CostCenter.
- Project.
- ManagedBy.
- DataClass.
- ExpirationDate.

### Cost Explorer

Dùng để phân tích theo:

- Service.
- Account.
- Region.
- Usage type.
- Tag.
- Cost category.
- Thời gian.

### Budgets

Tạo alert theo:

- Actual cost.
- Forecasted cost.
- Usage.
- Coverage/utilization nếu phù hợp.

### Cost Anomaly Detection

Phát hiện pattern bất thường, nhưng cần người xử lý và runbook. Anomaly hợp lệ do traffic campaign vẫn cần được phân biệt với lỗi cấu hình.

### Organizations và SCP

- Consolidated billing.
- Tách account.
- OU.
- SCP guardrail.
- Central logging/security.
- Region restriction.
- Chặn thao tác nguy hiểm.

---

# 34. Bảng phân biệt các cặp dịch vụ dễ nhầm

## 34.1. IAM role và IAM policy

| Thành phần | Trả lời câu hỏi |
|---|---|
| Role | Ai có thể assume identity này? |
| Permission policy | Identity này được làm gì? |
| Trust policy | Principal nào được assume role? |
| Resource policy | Resource cho principal nào truy cập? |

## 34.2. Security Group và NACL

| Khi cần | Chọn |
|---|---|
| Stateful app-to-app access | Security Group |
| Deny cụ thể ở subnet boundary | NACL |
| Chỉ allow rules | Security Group |
| Allow và deny, stateless | NACL |

## 34.3. NAT Gateway và Internet Gateway

| NAT Gateway | Internet Gateway |
|---|---|
| Cho private resource outbound Internet | Kết nối VPC với Internet |
| Không nhận inbound connection khởi tạo từ Internet vào private resource | Public resource có thể nhận inbound nếu route/SG cho phép |
| Có phí theo giờ/data processing | Không tính theo kiểu NAT |
| Đặt trong public subnet | Gắn với VPC |

## 34.4. Gateway Endpoint và Interface Endpoint

| Gateway Endpoint | Interface Endpoint |
|---|---|
| S3/DynamoDB | AWS service/partner hỗ trợ PrivateLink |
| Route table | ENI private IP |
| Không có hourly charge kiểu interface | Có phí theo giờ mỗi AZ và data |
| Không cần NAT cho traffic tương ứng | Có SG/DNS/ENI cần quản lý |

## 34.5. SQS và SNS

| SQS | SNS |
|---|---|
| Queue/buffer | Pub/sub topic |
| Consumer pull | Push đến subscriber |
| Một message thường được worker xử lý | Một event fan-out đến nhiều subscriber |
| DLQ và visibility timeout | Subscription/filtering |
| Phù hợp decouple producer/worker | Phù hợp broadcast/fan-out |

## 34.6. SNS và EventBridge

| SNS | EventBridge |
|---|---|
| Fan-out notification đơn giản | Event bus và rule-based routing |
| Topic/subscription | Event pattern, source, detail |
| Push delivery | Nhiều target và event integration |
| Phù hợp broadcast | Phù hợp event-driven integration |

## 34.7. Multi-AZ và read replica

| Multi-AZ | Read replica |
|---|---|
| High availability/failover | Read scaling |
| Standby thường không phục vụ read trực tiếp | Có thể phục vụ read |
| Đồng bộ tùy dịch vụ | Thường có replication lag |
| Không phải cách chính để tăng read throughput | Không tự động thay thế HA |

## 34.8. EBS, EFS và S3

| EBS | EFS | S3 |
|---|---|---|
| Block storage | Shared file system | Object storage |
| Thường gắn với EC2 | Nhiều compute truy cập | API/object access |
| Low-latency block I/O | POSIX shared access | Durable, scale lớn |
| Không phải shared storage mặc định | Cost/latency khác EBS | Không phải POSIX filesystem |

## 34.9. CloudFront và Global Accelerator

| CloudFront | Global Accelerator |
|---|---|
| CDN, cache content | Network acceleration |
| HTTP/HTTPS content | TCP/UDP application |
| Edge cache | Anycast static IP |
| Giảm origin request | Đưa traffic qua AWS global network |
| Phù hợp static/dynamic cacheable content | Phù hợp non-cacheable, low-latency global access |

## 34.10. RDS và DynamoDB

| RDS | DynamoDB |
|---|---|
| Relational, SQL, joins | NoSQL key-value/document |
| Schema/transaction pattern truyền thống | Access pattern và partition key |
| Vertical/read replica/scaling theo engine | Horizontal partition scaling |
| Query linh hoạt hơn | Throughput thấp latency ở access pattern xác định |
| Phù hợp relational requirement | Phù hợp scale lớn/key-value/document |

## 34.11. ALB và NLB

| ALB | NLB |
|---|---|
| Layer 7 | Layer 4 |
| HTTP/HTTPS | TCP/UDP/TLS |
| Path/host routing | Static IP/high throughput |
| WAF integration | Protocol-agnostic hơn |

## 34.12. CloudWatch, CloudTrail và Config

| CloudWatch | CloudTrail | Config |
|---|---|---|
| Metrics, logs, alarms | API activity/audit | Configuration/compliance |
| Hệ thống đang chạy thế nào? | Ai đã gọi API gì? | Resource đang cấu hình ra sao? |

---

# 35. Practice questions: chiến lược luyện tập

## 35.1. Không làm câu hỏi theo kiểu đoán đáp án

Mỗi câu nên có bốn lượt:

### Lượt 1 — Làm có bấm giờ

- Đọc kỹ requirement.
- Chọn đáp án.
- Đánh dấu confidence: cao, vừa hoặc thấp.
- Không tra tài liệu giữa block.

### Lượt 2 — Review đáp án đúng

Ghi:

- Keyword quyết định.
- Service/pattern đúng.
- Trade-off chính.

### Lượt 3 — Review mọi đáp án sai

Với từng đáp án sai, ghi một câu:

- Sai vì không đáp ứng requirement nào?
- Sai vì dùng service không đúng mục đích nào?
- Sai vì operational overhead/cost nào?
- Sai vì giả định nào không có trong đề?

### Lượt 4 — Tạo flashcard hoặc rule

Ví dụ rule tốt:

    Nếu đề nói private subnet cần truy cập S3 và muốn giảm NAT cost,
    nghĩ đến S3 Gateway Endpoint trước.

Rule không nên chỉ là:

    Đáp án câu 42 là B.

## 35.2. Phân bổ 300–500 câu

Một kế hoạch phù hợp:

| Giai đoạn | Số câu | Mục tiêu |
|---|---:|---|
| Ôn theo domain | 160–200 | Tìm gap từng domain |
| Mixed questions | 80–120 | Luyện chuyển context |
| Timed blocks | 40–80 | Luyện tốc độ |
| Full mock | 65 × 2–4 | Mô phỏng kỳ thi |
| Câu review lại | 50–100 | Sửa lỗi lặp lại |

Không chạy theo số lượng nếu câu hỏi chất lượng thấp hoặc giải thích không đáng tin. Một câu được review kỹ có giá trị hơn nhiều câu chỉ nhìn đáp án.

## 35.3. Timeboxing

Đề thực tế có thể có nhiều câu và cần đọc scenario dài. Hãy luyện:

- Block 10 câu trong khoảng thời gian cố định.
- Block 20 câu không tra cứu.
- Full mock trong 130 phút theo điều kiện thi.
- Đánh dấu câu cần quay lại.
- Không để một câu khó tiêu tốn quá nhiều thời gian.

Một quy tắc thực tế:

1. Đọc câu hỏi và keyword.
2. Loại trừ nhanh đáp án rõ ràng.
3. Chọn đáp án tốt nhất trong thời gian hợp lý.
4. Đánh dấu nếu confidence thấp.
5. Chuyển câu.
6. Review cuối block.

## 35.4. Cách xử lý câu “best answer”

Có thể có nhiều đáp án technically possible. Chọn đáp án:

- Đáp ứng toàn bộ requirement.
- Có ít operational overhead hơn nếu đề nói managed/least effort.
- Có cost phù hợp nếu đề nói cost-effective.
- Có security boundary đúng nếu đề nói secure.
- Có failure behavior phù hợp nếu đề nói resilient.
- Ít thay đổi ứng dụng hơn nếu đề nói minimal changes.

“Có thể làm được” chưa chắc là “best answer”.

---

# 36. Template nhật ký câu hỏi

Dùng template này cho mọi câu sai hoặc confidence thấp:

    Question ID:
    Domain:
    Chủ đề:

    Requirement chính:
    Requirement phụ:
    Keyword quyết định:

    Đáp án tôi chọn:
    Đáp án đúng:

    Vì sao tôi sai:
    [ ] Service gap
    [ ] Architecture gap
    [ ] Requirement gap
    [ ] Wording trap
    [ ] Comparison gap
    [ ] Cost/calculation gap
    [ ] Time-management gap

    Vì sao đáp án đúng phù hợp:

    Vì sao đáp án A sai:
    Vì sao đáp án B sai:
    Vì sao đáp án C sai:
    Vì sao đáp án D sai:

    Rule rút ra:
    Cặp dịch vụ cần ôn lại:
    Có cần thêm flashcard không?
    Ngày review lại:

### Ví dụ một entry tốt

    Question ID: Q-017
    Domain: Secure Architectures
    Chủ đề: Private access đến S3

    Requirement chính:
    - EC2 trong private subnet truy cập S3.
    - Không muốn traffic đi qua Internet.
    - Giảm chi phí NAT.

    Đáp án tôi chọn:
    - NAT Gateway vì EC2 ở private subnet.

    Đáp án đúng:
    - S3 Gateway Endpoint.

    Vì sao tôi sai:
    - Requirement gap: chưa để ý “S3” và “giảm NAT cost”.

    Rule:
    - Khi private resource chỉ cần S3/DynamoDB, kiểm tra Gateway Endpoint trước NAT.

    Cặp cần ôn:
    - Gateway Endpoint và Interface Endpoint.
    - NAT Gateway và Internet Gateway.

---

# 37. Mini practice set tổng hợp

## Câu 1 — IAM role cho EC2

Một ứng dụng chạy trên EC2 cần đọc object từ một S3 bucket. Security team cấm lưu access key trong instance. Phương án nào phù hợp nhất?

A. Lưu access key trong user data  
B. Gán IAM role cho EC2 instance profile  
C. Tạo bucket public-read  
D. Lưu secret trong source code

Đáp án: B.

Giải thích: EC2 nên sử dụng IAM role và temporary credentials. Bucket public phá vỡ security; user data và source code có thể làm lộ credential.

## Câu 2 — Cross-account access

Một role ở Account B cần được assume bởi deployment role ở Account A. Đã cấp permission sts:AssumeRole cho deployment role nhưng vẫn bị AccessDenied. Cần kiểm tra thêm gì?

A. CloudFront distribution  
B. Trust policy của role ở Account B  
C. S3 lifecycle  
D. Auto Scaling policy

Đáp án: B.

Giải thích: Target role phải trust đúng principal từ Account A. Permission ở source chỉ là một nửa cấu hình.

## Câu 3 — Security Group

Một application server cần truy cập database, nhưng database không được truy cập từ Internet. Cách cấu hình tốt nhất?

A. Mở database port cho 0.0.0.0/0  
B. Cho DB Security Group nhận traffic từ Application Security Group  
C. Chỉ dùng NACL deny Internet  
D. Đưa database ra public subnet

Đáp án: B.

Giải thích: Security Group reference giới hạn source đúng theo application tier và không cần public database.

## Câu 4 — S3 private access

EC2 trong private subnet cần truy cập S3 và tổ chức muốn tránh NAT data processing. Dùng gì?

A. Internet Gateway  
B. S3 Gateway Endpoint  
C. Public IP cho EC2  
D. NLB

Đáp án: B.

## Câu 5 — Encryption

Một tổ chức cần kiểm soát key policy, rotation, disable và audit cho dữ liệu S3. Lựa chọn phù hợp?

A. SSE-S3 trong mọi trường hợp  
B. SSE-KMS với customer managed key  
C. Không encryption vì bucket private  
D. Chỉ dùng TLS

Đáp án: B.

Giải thích: TLS bảo vệ in transit; bucket private kiểm soát authorization nhưng không thay thế encryption at rest. Customer managed KMS key cho control cao hơn.

## Câu 6 — CloudTrail

Security team muốn biết ai đã xóa một bucket policy. Dịch vụ chính cần kiểm tra?

A. CloudTrail  
B. CloudFront  
C. Auto Scaling  
D. Route 53

Đáp án: A.

## Câu 7 — Multi-AZ

Một web application chạy trên EC2 cần chịu lỗi một AZ. Thiết kế nào phù hợp?

A. Một EC2 instance lớn trong một AZ  
B. Auto Scaling Group trải trên nhiều AZ phía sau ALB  
C. Một Lambda function trong một subnet  
D. Một RDS read replica duy nhất

Đáp án: B.

Giải thích: Cần compute phân bố nhiều AZ, load balancing và khả năng thay thế instance.

## Câu 8 — RDS Multi-AZ

Một team muốn giảm read latency bằng cách bật RDS Multi-AZ. Nhận định đúng?

A. Multi-AZ chủ yếu cung cấp HA/failover, không phải read scaling  
B. Multi-AZ luôn route read query đến standby  
C. Multi-AZ biến RDS thành DynamoDB  
D. Multi-AZ chỉ dùng cho backup

Đáp án: A.

## Câu 9 — SQS visibility timeout

Một worker xử lý message trung bình 2 phút nhưng visibility timeout chỉ là 30 giây. Điều gì có thể xảy ra?

A. Message bị xóa ngay lập tức  
B. Message xuất hiện lại khi worker chưa xử lý xong  
C. SQS tự chuyển message sang SNS  
D. Queue tự chuyển thành FIFO

Đáp án: B.

## Câu 10 — DLQ

Một số message luôn thất bại vì payload không hợp lệ và đang làm queue chính bị retry liên tục. Giải pháp phù hợp?

A. Tăng vô hạn retry  
B. Cấu hình DLQ sau số lần nhận tối đa  
C. Xóa toàn bộ queue  
D. Dùng CloudFront

Đáp án: B.

## Câu 11 — DR

Một workload có RTO 10 phút và RPO gần bằng 0. Chiến lược nào phù hợp hơn backup-and-restore?

A. Backup-and-restore duy nhất  
B. Warm standby hoặc active-active tùy requirement  
C. Chỉ tạo AMI mỗi tháng  
D. Không cần replication

Đáp án: B.

Giải thích: RTO/RPO thấp cần có capacity và dữ liệu sẵn sàng hơn ở môi trường phục hồi.

## Câu 12 — CloudFront

Một website phân phối nhiều ảnh tĩnh cho người dùng toàn cầu và origin đang chịu quá nhiều request. Lựa chọn phù hợp?

A. CloudFront caching  
B. Tăng database connections  
C. Chuyển ảnh vào RDS  
D. Dùng NACL

Đáp án: A.

## Câu 13 — DynamoDB key design

Một bảng DynamoDB dùng userType làm partition key và chỉ có ba giá trị, dẫn đến hot partition. Hướng xử lý chính?

A. Dùng Scan thường xuyên hơn  
B. Thiết kế partition key có phân bố tốt hơn  
C. Tăng TTL cho mọi item  
D. Thêm WAF

Đáp án: B.

## Câu 14 — Kinesis

Một hệ thống cần nhiều consumer độc lập, replay record và xử lý custom gần real-time. Dịch vụ phù hợp?

A. Kinesis Data Streams  
B. S3 Lifecycle  
C. Route 53 Simple routing  
D. NAT Gateway

Đáp án: A.

## Câu 15 — Spot

Một công việc batch có thể checkpoint và chạy lại khi worker bị thu hồi. Cách giảm compute cost?

A. Dùng Spot Instances với Auto Scaling và checkpoint  
B. Dùng Dedicated Host  
C. Dùng một EC2 On-Demand lớn duy nhất  
D. Dùng RDS Multi-AZ

Đáp án: A.

## Câu 16 — Cost governance

Một công ty muốn biết chi phí theo project và owner, đồng thời cảnh báo khi forecast vượt ngân sách. Cần kết hợp gì?

A. Tags và AWS Budgets  
B. NACL và Route 53  
C. KMS và CloudFront  
D. SNS và EFS

Đáp án: A.

---

# 38. Các lỗi tư duy cần tránh

## 38.1. Chọn dịch vụ “mạnh nhất”

Dịch vụ nhiều tính năng hơn không tự động là đáp án tốt hơn. Tính năng thêm có thể mang theo:

- Cost.
- Complexity.
- Operational overhead.
- Configuration risk.
- Latency.
- Vendor-specific coupling.

## 38.2. Thấy “private” là dùng NAT

Private subnet không đồng nghĩa mọi outbound traffic phải qua NAT. Với S3/DynamoDB, Gateway Endpoint thường phù hợp hơn. Với service hỗ trợ PrivateLink, Interface Endpoint có thể là lựa chọn khác.

## 38.3. Thấy “scale” là tăng instance

Scale có thể là:

- Horizontal scaling.
- Vertical scaling.
- Caching.
- Queue.
- Partitioning.
- Read replica.
- CDN.
- Compression.
- Batch.

Hãy tìm bottleneck trước.

## 38.4. Thấy “backup” là đã có DR

Backup chỉ là một thành phần. Cần biết:

- Backup ở đâu.
- Restore trong bao lâu.
- Dữ liệu có sẵn ở Region khác không.
- Ai thực hiện restore.
- Restore đã được test chưa.
- RPO/RTO có đạt không.

## 38.5. Thấy “encrypted” là đã secure

Còn phải kiểm tra:

- Ai có quyền decrypt?
- Key policy thế nào?
- Dữ liệu truyền có TLS không?
- Bucket/resource có public không?
- Log có chứa secret không?
- Credential có bị hardcode không?

## 38.6. Chỉ ghi lại đáp án đúng

Bạn có thể gặp lại cùng một distractor trong nhiều câu với wording khác. Ghi vì sao đáp án sai giúp nhận diện pattern nhanh hơn.

## 38.7. Học theo tên dịch vụ thay vì trade-off

Thay vì ghi:

    SQS là queue.

Hãy ghi:

    Nếu producer và consumer cần tách rời, traffic có burst, cần retry và chấp nhận
    asynchronous processing, SQS tạo buffer phù hợp; phải xử lý visibility timeout,
    duplicate và DLQ.

---

# 39. Kế hoạch học trong 7 ngày

## Ngày 1 — Secure Architectures

- Ôn IAM policy evaluation.
- Role trust và permission policy.
- Cross-account.
- SG/NACL.
- VPC endpoint.
- KMS, Secrets Manager, ACM.
- S3 security, WAF, CloudTrail.
- Làm 30–40 câu.
- Viết lại toàn bộ câu sai.

## Ngày 2 — Resilient Architectures

- Multi-AZ và health check.
- Auto Scaling.
- ALB/NLB.
- SQS/SNS/EventBridge.
- Retry, DLQ, idempotency.
- RDS Multi-AZ/read replica.
- Backup/PITR.
- Làm 30–40 câu.

## Ngày 3 — Resilient và DR

- S3 versioning/replication.
- DynamoDB PITR/Global Tables.
- RTO/RPO.
- Backup-and-restore, pilot light, warm standby, active-active.
- Làm 25–35 câu.
- Vẽ một DR decision table.

## Ngày 4 — High-Performing Architectures

- EC2 family.
- EBS/EFS/S3.
- ALB/NLB/CloudFront/Route 53.
- Cache.
- RDS read scaling.
- DynamoDB key design.
- Làm 35–45 câu.

## Ngày 5 — High-performing và cost

- Lambda concurrency.
- API Gateway.
- Kinesis/Firehose.
- Async processing.
- Pricing model.
- Right-sizing.
- S3 lifecycle.
- NAT/Endpoint.
- Làm 35–45 câu.

## Ngày 6 — Cost và mixed questions

- Tags, Cost Explorer, Budgets.
- Organizations/SCP.
- Trusted Advisor/Compute Optimizer.
- Làm 50–65 câu mixed có bấm giờ.
- Review mọi câu confidence thấp.

## Ngày 7 — Consolidation day

- Xem error log.
- Ôn các cặp dịch vụ dễ nhầm.
- Làm một block mixed cuối.
- Cập nhật weak-area list.
- Chuẩn bị mock exam cho Tuần 16.
- Không học thêm quá nhiều chủ đề mới.

---

# 40. Decision trees cần nhớ

## 40.1. Chọn compute

    Workload có traffic thấp/burst và execution ngắn?
        ├── Có → Lambda/serverless có thể phù hợp
        └── Không
            Workload chạy ổn định 24/7?
                ├── Có → EC2/Fargate + commitment có thể phù hợp
                └── Không
                    Có thể bị gián đoạn và chạy lại?
                        ├── Có → Spot
                        └── Không → On-Demand + Auto Scaling

## 40.2. Chọn database

    Có relational schema, joins, transaction phức tạp?
        ├── Có → RDS/Aurora
        └── Không
            Access pattern key-value/document, scale lớn?
                ├── Có → DynamoDB
                └── Không
                    Phân tích yêu cầu query, consistency và operational model

## 40.3. Chọn messaging

    Cần buffer worker và retry?
        ├── Có → SQS
        └── Không
            Cần fan-out một event đến nhiều subscriber?
                ├── Có → SNS
                └── Không
                    Cần event bus và routing theo event pattern?
                        ├── Có → EventBridge
                        └── Không
                            Cần workflow nhiều bước? → Step Functions

## 40.4. Chọn storage

    Cần block storage cho một compute node?
        ├── Có → EBS
        └── Không
            Nhiều node cần shared file system?
                ├── Có → EFS
                └── Không
                    Lưu object/file, static, backup hoặc data lake?
                        └── S3

## 40.5. Chọn DR strategy

    RTO/RPO rất thấp?
        ├── Có → Warm standby hoặc active-active
        └── Không
            Có thể chấp nhận khởi động từ baseline?
                ├── Có → Pilot light
                └── Không → Backup and restore

## 40.6. Chọn endpoint/network path

    Private resource truy cập S3/DynamoDB?
        ├── Có → Gateway Endpoint
        └── Không
            AWS/service/partner hỗ trợ PrivateLink?
                ├── Có → Interface Endpoint
                └── Không
                    Cần Internet outbound?
                        └── NAT Gateway

---

# 41. Checklist tự đánh giá theo domain

## Domain 1 — Secure Architectures

- [ ] Phân biệt role, permission policy và trust policy.
- [ ] Giải thích được explicit deny và SCP.
- [ ] Thiết kế được cross-account role.
- [ ] Phân biệt SG và NACL.
- [ ] Thiết kế public/private subnet.
- [ ] Chọn Gateway Endpoint hoặc Interface Endpoint.
- [ ] Phân biệt encryption at rest và in transit.
- [ ] Hiểu customer managed KMS key.
- [ ] Biết khi nào dùng Secrets Manager.
- [ ] Bảo vệ S3 bằng Block Public Access, bucket policy và pre-signed URL.
- [ ] Biết WAF giải quyết lớp nào.
- [ ] Phân biệt CloudTrail, Config và CloudWatch.

## Domain 2 — Resilient Architectures

- [ ] Thiết kế Multi-AZ compute.
- [ ] Chọn health check phù hợp.
- [ ] Cấu hình Auto Scaling với failure behavior.
- [ ] Phân biệt ALB và NLB.
- [ ] Hiểu visibility timeout và DLQ.
- [ ] Thiết kế idempotent consumer.
- [ ] Phân biệt SQS, SNS và EventBridge.
- [ ] Phân biệt RDS Multi-AZ và read replica.
- [ ] Biết backup/PITR.
- [ ] Biết versioning/replication của S3.
- [ ] Hiểu DynamoDB Global Tables trade-off.
- [ ] Chọn DR theo RTO/RPO.

## Domain 3 — High-Performing Architectures

- [ ] Chọn EC2 family dựa trên bottleneck.
- [ ] Phân biệt horizontal và vertical scaling.
- [ ] Chọn EBS, EFS hoặc S3.
- [ ] Chọn CloudFront, ALB, NLB và Route 53.
- [ ] Biết cache-aside và cache invalidation.
- [ ] Phân biệt ElastiCache và DAX.
- [ ] Thiết kế RDS read scaling.
- [ ] Thiết kế DynamoDB partition key/GSI.
- [ ] Hiểu Lambda concurrency.
- [ ] Phân biệt reserved và provisioned concurrency.
- [ ] Chọn Kinesis Data Streams và Firehose.
- [ ] Biết khi nào dùng asynchronous processing.

## Domain 4 — Cost-Optimized Architectures

- [ ] Chọn pricing model đúng workload.
- [ ] Biết right-size theo metric.
- [ ] Biết scheduling non-production.
- [ ] Thiết kế S3 lifecycle.
- [ ] Nhận diện NAT data processing.
- [ ] Nhận diện cross-AZ/cross-Region/Internet egress.
- [ ] Biết dùng CloudFront/cache để giảm origin cost.
- [ ] Biết serverless không luôn rẻ nhất.
- [ ] Dùng cost allocation tags.
- [ ] Biết Cost Explorer, Budgets, Anomaly Detection.
- [ ] Hiểu Trusted Advisor và Compute Optimizer.
- [ ] Hiểu Organizations, consolidated billing và SCP.

---

# 42. Tiêu chí sẵn sàng chuyển sang Tuần 16

Bạn có thể chuyển sang mock exam khi:

- Đã hoàn thành tối thiểu 300 câu chất lượng.
- Mỗi câu sai có ghi nguyên nhân.
- Có thể giải thích tại sao các đáp án khác sai.
- Không còn nhầm thường xuyên các cặp dịch vụ chính.
- Có thể đọc câu hỏi và xác định mục tiêu tối ưu trong vài chục giây.
- Không bị kẹt quá lâu ở một câu.
- Có thể giải thích RTO/RPO và chọn DR strategy.
- Có thể giải thích IAM role/policy và cross-account.
- Có thể nhận diện bottleneck và chọn cache/queue/scale phù hợp.
- Có thể nhận diện cost driver từ request path.
- Biết các weak area còn lại thay vì nghĩ mình “đã học xong mọi thứ”.

Nếu một domain dưới khoảng 70% trong practice questions, không nên chỉ làm thêm câu mới. Hãy quay lại:

1. Tóm tắt lại domain bằng một trang.
2. Làm lại các câu sai sau vài ngày.
3. Học theo cặp dịch vụ dễ nhầm.
4. Làm câu hỏi scenario khác cách diễn đạt.
5. Kiểm tra xem lỗi là kiến thức hay cách đọc đề.

---

# 43. Tóm tắt cuối tuần

1. Đọc requirement trước khi chạy theo service name.
2. Tách bắt buộc và mong muốn.
3. Xác định mục tiêu tối ưu: security, resilience, performance, cost hay operations.
4. Dùng mental model để hình dung request path và failure path.
5. Loại trừ đáp án không đáp ứng requirement chính.
6. So sánh hai đáp án gần đúng bằng trade-off.
7. Ghi vì sao từng đáp án sai.
8. IAM role cho workload; trust policy và permission policy là hai phần khác nhau.
9. Security Group stateful; NACL stateless.
10. Multi-AZ cho HA; read replica cho read scaling.
11. SQS cho buffer; SNS cho fan-out; EventBridge cho event bus/routing.
12. RTO/RPO quyết định DR strategy.
13. Cache, queue, partitioning và scaling giải quyết các bottleneck khác nhau.
14. NAT Gateway, cross-AZ và Internet egress là các cost driver cần nhận diện.
15. Tags, Budgets và Organizations biến cost thành một phần của governance.
16. Practice question chỉ có giá trị khi bạn review reasoning, không chỉ đếm số câu đúng.

---

## Tài liệu tham khảo chính thức

- AWS Certified Solutions Architect — Associate: https://aws.amazon.com/certification/certified-solutions-architect-associate/
- AWS Well-Architected Framework: https://docs.aws.amazon.com/wellarchitected/latest/framework/welcome.html
- IAM best practices: https://docs.aws.amazon.com/IAM/latest/UserGuide/best-practices.html
- IAM policy evaluation logic: https://docs.aws.amazon.com/IAM/latest/UserGuide/reference_policies_evaluation-logic.html
- Amazon VPC security groups: https://docs.aws.amazon.com/vpc/latest/userguide/vpc-security-groups.html
- Amazon VPC network ACLs: https://docs.aws.amazon.com/vpc/latest/userguide/vpc-network-acls.html
- AWS KMS concepts: https://docs.aws.amazon.com/kms/latest/developerguide/overview.html
- Amazon S3 security: https://docs.aws.amazon.com/AmazonS3/latest/userguide/security-best-practices.html
- Elastic Load Balancing: https://docs.aws.amazon.com/elasticloadbalancing/latest/userguide/what-is-load-balancing.html
- Amazon SQS: https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/welcome.html
- Amazon SNS: https://docs.aws.amazon.com/sns/latest/dg/welcome.html
- Amazon EventBridge: https://docs.aws.amazon.com/eventbridge/latest/userguide/eb-what-is.html
- Amazon RDS Multi-AZ: https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/Concepts.MultiAZ.html
- Amazon RDS read replicas: https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/Concepts.ReadReplicas.html
- AWS disaster recovery: https://docs.aws.amazon.com/whitepapers/latest/disaster-recovery-workloads-on-aws/disaster-recovery-workloads-on-aws.html
- Amazon CloudFront: https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/Introduction.html
- Amazon DynamoDB best practices: https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/best-practices.html
- AWS Lambda concurrency: https://docs.aws.amazon.com/lambda/latest/dg/lambda-concurrency.html
- Amazon Kinesis Data Streams: https://docs.aws.amazon.com/streams/latest/dev/introduction.html
- AWS Cost Management: https://aws.amazon.com/aws-cost-management/
- AWS Organizations: https://docs.aws.amazon.com/organizations/latest/userguide/orgs_introduction.html
- Service Control Policies: https://docs.aws.amazon.com/organizations/latest/userguide/orgs_manage_policies_scps.html


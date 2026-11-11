# Tuần 11 — Security nâng cao và data protection

> Mục tiêu: thiết kế defense in depth cho workload AWS, bảo vệ data bằng encryption/key management, quản lý secret đúng cách, quan sát security event và giảm rủi ro public exposure/data exfiltration.

## 1. Kết quả cần đạt

- Hiểu KMS key, key policy, grants và envelope encryption.
- Phân biệt customer managed key, AWS managed key và AWS owned key.
- Chọn SSE-S3, SSE-KMS hoặc client-side encryption.
- Phân biệt Secrets Manager với Systems Manager Parameter Store.
- Quản lý ACM certificate và TLS termination trên ALB, CloudFront, API Gateway.
- Thiết kế WAF web ACL, managed rules và rate-based rule.
- Phân biệt Shield Standard và Shield Advanced.
- Phân biệt CloudTrail management events với data events.
- Nhận biết AWS Config rules và conformance packs.
- Hiểu use case của GuardDuty, Macie, Inspector và Security Hub.
- Phân biệt CloudTrail Logs, CloudWatch Logs và VPC Flow Logs.
- Giảm data exfiltration bằng least privilege, private endpoints và egress control.
- Xây defense-in-depth cho public API.

## 2. Lịch học gợi ý

| Buổi | Nội dung | Thời lượng |
|---|---|---:|
| 1 | KMS, envelope encryption và data protection | 3 giờ |
| 2 | Secrets Manager, Parameter Store, ACM và TLS | 2–3 giờ |
| 3 | WAF, Shield và public API defense | 2 giờ |
| 4 | CloudTrail, Config, GuardDuty, Macie, Inspector, Security Hub | 3 giờ |
| 5 | Flow Logs, data exfiltration, lab và câu hỏi thi | 2–3 giờ |

Cách suy luận: phân biệt confidentiality, integrity, availability và audit trước khi chọn control. Encryption bảo vệ dữ liệu; IAM quyết định ai được dùng key; logging giúp biết điều gì đã xảy ra; detection giúp phản ứng.

---

## 3. Defense in depth

Một workload public cần nhiều lớp bảo vệ:

~~~text
Identity → authorization → network boundary → WAF/DDoS
        → encryption/key → secret management → logging
        → detection/response → backup/recovery
~~~

Không có một service nào thay thế toàn bộ security.

### 3.1. Các lớp chính

| Lớp | Control tiêu biểu | Mục tiêu |
|---|---|---|
| Identity | IAM role, MFA, SCP, least privilege | giảm quyền trái phép |
| Network | VPC, SG, NACL, endpoint, egress control | giới hạn đường đi |
| Edge/API | CloudFront, WAF, Shield, API auth | chống abuse/attack |
| Data | KMS, TLS, Object Lock, backup | confidentiality/integrity |
| Secrets | Secrets Manager, Parameter Store | không hard-code credential |
| Audit | CloudTrail, Config | biết ai làm gì/cấu hình ra sao |
| Detection | GuardDuty, Macie, Inspector, Security Hub | phát hiện rủi ro |
| Response | alarm, quarantine, runbook | giảm impact và khôi phục |

### 3.2. Security of the cloud vs in the cloud

AWS bảo vệ hạ tầng cloud; khách hàng bảo vệ dữ liệu, IAM, cấu hình, OS/application và network rule theo shared responsibility. Managed service giảm patch/server work nhưng không loại bỏ trách nhiệm policy, key, secret và logging.

---

## 4. AWS KMS

KMS là managed key management service để tạo/quản lý key và thực hiện cryptographic operations qua AWS API.

### 4.1. Loại key

- **AWS owned key:** AWS sở hữu/quản lý, khách hàng thường không xem/quản lý trực tiếp.
- **AWS managed key:** AWS service tạo và quản lý trong account, khách hàng có ít quyền kiểm soát hơn.
- **Customer managed key:** customer tạo/quản lý policy, alias, rotation, disable, schedule deletion và audit.

Customer managed key phù hợp khi cần:

- key policy riêng;
- audit/CloudTrail;
- cross-account access;
- disable/revoke;
- compliance/control rotation;
- tách key theo data classification.

KMS key là regional resource. Copy encrypted snapshot/object sang Region khác thường cần key ở Region đích.

### 4.2. Key policy

Key policy là resource-based policy chính của KMS key. IAM policy một mình không luôn đủ nếu key policy không cho phép account/IAM principal sử dụng.

Key policy cần kiểm tra:

- principal;
- actions như Encrypt, Decrypt, GenerateDataKey, ReEncrypt, DescribeKey;
- resource;
- condition;
- account ownership;
- service grant;
- admin vs key user separation.

Không cấp quyền admin key cho application role nếu role chỉ cần Encrypt/Decrypt.

### 4.3. Grants

Grant là quyền tạm/chi tiết cho principal hoặc AWS service sử dụng key mà không cần sửa key policy mỗi lần. Dùng trong service integration, delegation và permission lifecycle.

Grant vẫn phải được audit và thu hồi khi không cần. Không tạo grant rộng hơn requirement.

---

## 5. Envelope encryption

KMS thường không mã hóa toàn bộ file lớn trực tiếp. Pattern phổ biến:

1. KMS tạo data key.
2. Application dùng plaintext data key để encrypt data bằng symmetric algorithm.
3. KMS encrypt data key bằng KMS key.
4. Lưu ciphertext data và encrypted data key.
5. Khi decrypt, KMS giải mã data key rồi application decrypt data.

~~~text
KMS key
   ↓ encrypt data key
Encrypted data key + encrypted data
~~~

Lợi ích:

- KMS chỉ xử lý data key nhỏ;
- data lớn được encrypt hiệu quả ở client/service;
- key rotation không cần re-encrypt mọi data ngay;
- data key có thể lưu cùng ciphertext nhưng không được lưu plaintext.

KMS key policy không tự giải quyết application authorization. Người dùng cần quyền đọc data và quyền decrypt key đúng context.

### 5.1. GenerateDataKey và decrypt

Service như S3/RDS có thể thực hiện envelope encryption managed. Với client-side encryption, application cần dùng SDK/KMS cẩn thận, xử lý data key, nonce/IV và metadata.

Không tự phát minh crypto algorithm. Dùng AWS Encryption SDK hoặc managed encryption pattern khi có thể.

### 5.2. Rotation và deletion

Key rotation giúp tạo material mới theo chính sách nhưng ciphertext cũ thường vẫn decrypt được nhờ key metadata. Rotation không tự sửa policy hoặc revoke quyền.

Schedule deletion là hành động nguy hiểm. Xóa key có thể làm dữ liệu encrypted không thể decrypt. Cần backup/export strategy, owner approval và kiểm tra dependency trước.

---

## 6. Encryption choices

### SSE-S3

S3 quản lý key và encryption. Đơn giản, phù hợp khi cần encryption at rest nhưng không cần key policy riêng.

### SSE-KMS

S3 dùng KMS key. Có audit, permission control, key rotation và cross-account design rõ hơn, nhưng thêm KMS request/cost/quota.

### Client-side encryption

Application encrypt trước khi gửi data lên AWS. Customer kiểm soát plaintext/key nhưng phải quản lý encryption library, key lifecycle, rotation, recovery và access.

| Type | Key control | Complexity | Use case |
|---|---|---|---|
| SSE-S3 | S3 managed | thấp | encryption at rest phổ thông |
| SSE-KMS | KMS/customer policy | vừa | audit/compliance/access control |
| Client-side | application/customer | cao | plaintext không đi vào service hoặc control đặc thù |

Encryption in transit dùng TLS; encryption at rest không thay TLS, IAM, WAF hoặc access policy.

### 6.1. KMS với S3/RDS

S3 SSE-KMS cần S3 permission và KMS permission. RDS encrypted cần KMS key lúc tạo; snapshot/copy/restore có thể cần quyền key ở account/Region liên quan.

Nếu app bị AccessDenied khi đọc object encrypted, kiểm tra cả:

- IAM role;
- bucket policy;
- key policy;
- KMS grant;
- Region/account;
- encryption context;
- VPC endpoint policy nếu có.

---

## 7. Secrets Manager và Parameter Store

### 7.1. Secrets Manager

Secrets Manager dành cho secret có lifecycle, rotation và access control.

Use case:

- DB credentials;
- API token;
- OAuth client secret;
- third-party credential;
- secret cần rotation tự động/managed.

Tính năng cần nhớ:

- versioning/staging labels;
- automatic rotation theo integration hỗ trợ;
- KMS encryption;
- IAM resource policy;
- cross-account pattern;
- CloudTrail audit;
- application lấy secret runtime.

### 7.2. Systems Manager Parameter Store

Parameter Store lưu configuration và parameter theo hierarchy. Hỗ trợ String, StringList và SecureString với KMS.

Use case:

- non-secret config;
- feature flag đơn giản;
- environment/region parameter;
- secret nhỏ với rotation đơn giản hoặc managed ngoài;
- chi phí thấp/đơn giản.

Parameter Store có Standard/Advanced tier với khác biệt quota/feature/cost. Không coi mọi SecureString là tương đương đầy đủ với Secrets Manager; chọn theo rotation, audit, retrieval và lifecycle requirement.

### 7.3. So sánh

| Tiêu chí | Secrets Manager | Parameter Store |
|---|---|---|
| Mục tiêu | secret lifecycle | config/parameter hierarchy |
| Rotation | mạnh/managed pattern | thường tự xây hoặc đơn giản hơn |
| Versioning | có | có |
| KMS | có | SecureString dùng KMS |
| Cost | cao hơn | có tier/cost thấp hơn |
| Chọn khi | DB/API credential và rotation | config, feature flag, parameter |

Không nhúng secret vào code, image, AMI, user data, Git hoặc log. Lambda/EC2/ECS dùng IAM role để đọc runtime.

---

## 8. ACM và TLS

AWS Certificate Manager (ACM) provision/manage TLS certificates cho dịch vụ AWS hỗ trợ.

### 8.1. Certificate

Certificate có thể được validate bằng DNS hoặc email tùy loại. DNS validation thường dễ tự động renew nếu CNAME validation record còn tồn tại.

Theo dõi:

- domain/SAN;
- expiry;
- validation record;
- certificate Region requirement;
- rotation/renewal;
- custom domain mapping.

CloudFront có yêu cầu Region riêng cho ACM certificate so với ALB/API Gateway; kiểm tra service documentation thay vì giả định mọi certificate dùng ở mọi Region.

### 8.2. TLS termination

TLS có thể terminate tại:

- CloudFront edge;
- ALB/NLB listener;
- API Gateway;
- application server.

Terminate ở edge/LB giúp centralize certificate và giảm crypto workload trên backend. Nếu cần end-to-end encryption, dùng HTTPS từ edge/LB tới origin/backend và validate certificate/trust.

### 8.3. TLS security

- redirect HTTP → HTTPS;
- chọn security policy/protocol phù hợp;
- tắt protocol/cipher yếu theo compatibility;
- không log token/cookie nhạy cảm;
- bảo vệ private key bằng ACM thay vì tự phân phối khi có thể.

---

## 9. AWS WAF

AWS WAF là web application firewall cho HTTP/HTTPS resource như CloudFront, ALB, API Gateway và một số service hỗ trợ.

### 9.1. Web ACL và rule

Web ACL chứa rule theo thứ tự priority. Rule có thể match:

- IP set;
- country;
- string/regex;
- header/cookie/query/body;
- SQL injection/XSS pattern;
- rate;
- managed rule group.

Action thường là Allow, Block, Count, CAPTCHA/Challenge theo khả năng hỗ trợ.

Rule order quan trọng. Một rule Allow/Block sớm có thể quyết định request trước rule sau.

### 9.2. Managed rules

Managed rule groups cung cấp rule chống common web exploit. Cần:

- đọc scope/managed rule;
- monitor Count trước Block nếu có thể;
- xử lý false positive;
- exclude field/rule cần thiết;
- cập nhật version;
- theo dõi WAF logs/metrics.

Không bật mọi managed rule mù quáng trong production; có thể chặn traffic hợp lệ.

### 9.3. Rate-based rule

Rate-based rule giới hạn request từ một IP hoặc aggregation key trong cửa sổ thời gian. Dùng để giảm abuse, bot hoặc request flood tầng HTTP.

Rate rule không thay DDoS protection toàn diện, authentication, quota business hoặc backend throttling. NAT/proxy có thể khiến nhiều user dùng chung source IP; chọn threshold cẩn thận.

### 9.4. WAF placement

- CloudFront WAF bảo vệ global edge và origin sau distribution.
- ALB WAF bảo vệ HTTP target qua ALB.
- API Gateway WAF bảo vệ API endpoint.

Chọn placement theo traffic path. Nếu request đi CloudFront rồi ALB, có thể có control ở edge và origin nhưng tránh rule trùng không cần thiết.

---

## 10. AWS Shield

### Shield Standard

Bảo vệ managed mặc định chống một số common network/transport DDoS cho AWS service/edge. Không phải WAF và không cung cấp mọi tính năng advanced response.

### Shield Advanced

Dùng cho workload có risk DDoS cao, cần visibility/response và protection mở rộng theo requirement/eligibility. Có cost và operational process riêng.

Phân biệt:

- Shield: DDoS protection;
- WAF: HTTP request filtering/rate/rules;
- CloudFront/Route 53/Global Accelerator: edge/network distribution;
- SG/NACL: network access control;
- application auth: user/tenant authorization.

Không chọn WAF để giải quyết volumetric DDoS ở mọi layer và không chọn Shield để lọc SQL injection.

---

## 11. CloudTrail

CloudTrail ghi AWS API activity để audit, investigation và governance.

### 11.1. Management events

Management events là control-plane operations như:

- create/delete/modify resource;
- IAM policy/role changes;
- security group changes;
- KMS key administration;
- route/configuration changes.

### 11.2. Data events

Data events là data-plane operations như:

- S3 object-level GET/PUT/DELETE;
- Lambda function invocation;
- DynamoDB item-level activity theo cấu hình.

Data events có volume/cost lớn hơn; bật theo resource cần audit, không mở toàn account mà không có lý do.

### 11.3. Trail design

Best practice:

- organization trail cho multi-account;
- lưu log vào S3 bucket riêng/protected;
- encryption KMS;
- log file validation;
- hạn chế delete/modify bằng policy/SCP;
- gửi CloudWatch Logs/SIEM để alert;
- retention/lifecycle;
- monitor trail tampering.

CloudTrail log ai gọi API, khi nào, từ đâu và request context. Nó không phải application log hay packet log.

---

## 12. AWS Config

AWS Config ghi configuration state/history và đánh giá compliance bằng rules.

Use case:

- kiểm tra bucket public;
- security group mở 0.0.0.0/0;
- volume/database chưa encrypted;
- resource thiếu tag;
- CloudTrail chưa bật;
- drift/config change;
- conformance pack tổng hợp nhiều rule.

Config trả lời “resource được cấu hình thế nào và có compliant không?”. CloudTrail trả lời “ai đã gọi API thay đổi gì?”. Hai service bổ sung cho nhau.

### Conformance pack

Conformance pack gom rules/remediation theo baseline compliance. Ở mức SAA, nhớ dùng để áp policy/compliance baseline trên nhiều account/Region.

Config không tự sửa mọi issue trừ khi cấu hình remediation. Remediation automation cần least privilege và change approval.

---

## 13. GuardDuty, Macie, Inspector và Security Hub

### GuardDuty

Threat detection managed cho account/resource, phân tích CloudTrail, VPC Flow Logs, DNS logs và signal khác để phát hiện hành vi đáng ngờ.

Use case:

- credential compromise;
- unusual API activity;
- crypto mining;
- malicious IP/domain;
- suspicious instance behavior.

GuardDuty là detection, không phải preventive firewall.

### Macie

Phát hiện và phân loại sensitive data trong S3, như PII/secrets theo pattern hỗ trợ. Dùng cho data discovery/classification và privacy risk.

Macie không thay encryption, bucket policy hoặc DLP process hoàn chỉnh.

### Inspector

Quét vulnerability của EC2, ECR container image hoặc workload supported. Dùng để phát hiện package/OS/CVE/configuration risk.

Inspector không phải network IDS và không thay patching/secure coding.

### Security Hub

Tập hợp security findings từ GuardDuty, Macie, Inspector, Config và partner để có view tập trung, standards/compliance và workflow.

Security Hub không tự tạo mọi finding; cần bật nguồn phù hợp và cấu hình aggregation/action.

| Service | Trả lời câu hỏi |
|---|---|
| GuardDuty | Có threat/behavior đáng ngờ không? |
| Macie | S3 có sensitive data gì/ở đâu? |
| Inspector | Workload/image có vulnerability không? |
| Security Hub | Gom findings và compliance view ở đâu? |
| Config | Resource config có compliant không? |

---

## 14. Logging: chọn đúng service

| Nhu cầu | Dịch vụ |
|---|---|
| AWS API/control-plane audit | CloudTrail |
| S3/Lambda/DynamoDB data-plane audit | CloudTrail data events |
| App/system/service logs | CloudWatch Logs |
| Metrics/alarms | CloudWatch Metrics/Alarms |
| VPC accept/reject flow metadata | VPC Flow Logs |
| Resource config/compliance | AWS Config |
| Threat detection | GuardDuty |
| Sensitive data discovery | Macie |
| Vulnerability scan | Inspector |
| Central findings | Security Hub |

### VPC Flow Logs

Flow Logs ghi metadata như source/destination, port, protocol, action accepted/rejected theo scope. Không phải full packet capture và không chứa toàn bộ application payload.

Dùng để debug route/SG/NACL và điều tra exfiltration cùng DNS/CloudTrail/application logs.

### CloudWatch Logs

CloudWatch Logs nhận application/system/service logs. Cấu hình retention, encryption, access control và subscription tới destination/SIEM phù hợp. Không giữ log vô hạn mặc định vì cost và privacy.

---

## 15. Data exfiltration và private access

Data exfiltration là dữ liệu rời khỏi boundary ngoài ý muốn hoặc trái phép.

### 15.1. Giảm exfiltration

- least-privilege IAM;
- deny public S3;
- SCP giới hạn Region/service;
- VPC endpoints và endpoint policy;
- private subnets;
- egress control/NAT inspection/proxy;
- SG/NACL;
- DNS filtering;
- KMS key policy;
- CloudTrail data events;
- VPC Flow Logs;
- GuardDuty/Macie;
- resource tagging và data classification;
- alert upload/transfer bất thường.

### 15.2. VPC endpoint

Private endpoint cho S3/DynamoDB/AWS services giúp giảm đường đi Internet/NAT và có thể giới hạn endpoint policy. Private path không tự cấp quyền; IAM/resource policy vẫn áp dụng.

### 15.3. S3 data exfiltration

Controls:

- Block Public Access;
- bucket policy deny public/cross-account ngoài ý muốn;
- require TLS/encryption;
- VPC endpoint condition;
- role prefix restriction;
- S3 Access Analyzer;
- CloudTrail data events;
- Macie;
- versioning/Object Lock cho recovery.

### 15.4. Cross-account

Review trust/resource policy, KMS key policy, external ID với third-party role và session duration. Cross-account Allow có chủ đích cần owner, audit và lifecycle.

---

## 16. Public API defense-in-depth

Ví dụ lớp bảo vệ:

~~~text
Client
  ↓ TLS/ACM
CloudFront/ALB/API Gateway
  ↓ WAF managed + rate rule + auth/throttle
Private service
  ↓ IAM role + SG
Database/secret/private endpoint
  ↓ KMS + CloudTrail + CloudWatch + GuardDuty
~~~

Checklist:

- TLS enforced;
- authentication/authorization;
- WAF rules và rate limit;
- API throttling;
- input validation;
- least privilege execution role;
- private backend/database;
- secret runtime;
- encryption at rest;
- audit logs;
- alerts/runbook;
- backup và recovery.

WAF không thay authentication. IAM không thay input validation. KMS không thay access policy.

---

## 17. Lab bắt buộc

### Lab 1 — KMS và S3/RDS encryption

1. Tạo customer managed KMS key với alias rõ ràng.
2. Tạo key policy tách key admin và key user.
3. Mã hóa S3 object bằng SSE-KMS.
4. Mã hóa RDS test nếu ngân sách cho phép.
5. Cho role được đọc S3 nhưng thiếu KMS Decrypt để quan sát AccessDenied.
6. Thêm permission đúng và test lại.
7. Kiểm tra CloudTrail KMS events.
8. Không schedule deletion key dùng cho data cần giữ.

### Lab 2 — Secrets Manager

- Tạo secret test.
- Cấp Lambda/EC2 role chỉ đọc secret.
- Đọc secret runtime.
- Kiểm tra không log plaintext.
- Test rotation hoặc version stage nếu môi trường hỗ trợ.
- Thu hồi role và xác nhận access bị chặn.

### Lab 3 — CloudTrail

1. Tạo trail lưu vào S3 protected bucket.
2. Bật organization/account scope phù hợp.
3. Ghi management events.
4. Bật data event cho S3 test bucket có kiểm soát.
5. Thực hiện create/put/get/modify test.
6. Truy vấn principal, source IP, action, resource và timestamp.
7. Bật log validation/retention theo baseline.

### Lab 4 — WAF trước ALB/API Gateway

- Tạo web ACL.
- Thêm managed rule ở Count trước khi Block nếu cần tuning.
- Thêm rate-based rule threshold thấp trong môi trường test.
- Gắn vào ALB hoặc API Gateway.
- Gửi request hợp lệ và request vượt threshold.
- Kiểm tra WAF metrics/logs.
- Document false positive và rollback.

### Lab 5 — VPC Flow Logs

1. Bật Flow Logs cho VPC/subnet/ENI test.
2. Gửi traffic được phép và bị chặn.
3. Xác định accepted/rejected flow.
4. Đối chiếu route, SG, NACL và CloudWatch/application logs.
5. Đặt retention và access control cho log.

### Lab 6 — Security findings

Nếu có quyền/budget:

- bật GuardDuty;
- kiểm tra Security Hub integration;
- chạy Inspector scan test image/instance;
- tạo S3 sample cho Macie;
- review finding owner, severity, evidence và remediation;
- không tạo hành vi độc hại ngoài lab được phê duyệt.

---

## 18. Bẫy đề thi SAA-C03

- KMS key policy quan trọng; IAM Allow một mình có thể không đủ.
- KMS key là regional; cross-Region cần key/permission phù hợp ở Region đích.
- Envelope encryption dùng data key cho dữ liệu lớn; không encrypt file lớn trực tiếp bằng KMS API.
- SSE-KMS cần cả service permission và KMS permission.
- Encryption at rest không thay TLS/IAM/access policy.
- Secrets Manager phù hợp secret rotation; Parameter Store phù hợp config/hierarchy đơn giản.
- Environment variable không tự là secret vault.
- ACM certificate Region/association phụ thuộc dịch vụ; CloudFront có yêu cầu riêng.
- WAF lọc HTTP request; Shield bảo vệ DDoS, hai service khác nhau.
- Rate-based WAF không thay authentication hoặc quota business.
- CloudTrail audit API; CloudWatch Logs là app/service logs; Flow Logs là network metadata.
- Management events khác data events; S3 object access cần bật data events nếu cần audit.
- Config đánh giá configuration/compliance, không thay CloudTrail.
- GuardDuty phát hiện threat, Macie tìm sensitive data, Inspector tìm vulnerability.
- Security Hub gom findings, không tự thay nguồn detection.
- VPC endpoint private path không tự cấp authorization.
- Block Public Access không thay bucket policy/least privilege.
- WAF managed rules cần tuning, có thể false positive.
- Key deletion có thể làm mất khả năng decrypt dữ liệu.
- KMS Decrypt permission không đồng nghĩa S3 GetObject permission.
- VPC Flow Logs không phải full packet capture.
- Public API cần defense in depth, không chỉ một WAF rule.
- Cross-account KMS cần xem key policy, IAM policy và trust/resource policy.

---

## 19. Câu hỏi tự luyện

### Câu 1

S3 object cần encryption, audit key usage và quyền decrypt riêng cho một role. Chọn gì?

**Đáp án:** SSE-KMS với customer managed key, key policy/IAM policy đúng và CloudTrail audit.

### Câu 2

Role có s3:GetObject nhưng đọc object SSE-KMS bị AccessDenied. Vì sao?

**Đáp án:** thiếu KMS Decrypt/key policy/grant hoặc encryption context không phù hợp; cần cả S3 và KMS permission.

### Câu 3

Ứng dụng cần DB credential tự rotation. Chọn gì?

**Đáp án:** Secrets Manager với rotation integration phù hợp, ứng dụng đọc runtime qua IAM role.

### Câu 4

Config theo environment không nhạy cảm, cần hierarchy và chi phí thấp. Chọn gì?

**Đáp án:** Systems Manager Parameter Store; nếu là secret cần lifecycle/rotation mạnh thì Secrets Manager.

### Câu 5

Muốn chặn SQL injection/XSS trên public ALB. Dùng gì?

**Đáp án:** AWS WAF web ACL với managed rules/custom rules, kết hợp input validation.

### Câu 6

Muốn giảm HTTP request flood từ một nguồn trong cửa sổ thời gian. Dùng gì?

**Đáp án:** WAF rate-based rule, nhưng vẫn cần auth/throttling/DDoS control phù hợp.

### Câu 7

Muốn bảo vệ khỏi volumetric DDoS ở edge. Dịch vụ nào liên quan?

**Đáp án:** Shield Standard/Advanced theo requirement; WAF xử lý web request layer.

### Câu 8

Muốn biết ai thay đổi security group và khi nào. Dùng gì?

**Đáp án:** CloudTrail management events.

### Câu 9

Muốn biết ai đọc/xóa object S3 cụ thể. Dùng gì?

**Đáp án:** CloudTrail S3 data events cho bucket/object scope.

### Câu 10

Muốn kiểm tra bucket nào public hoặc resource nào không encrypted theo compliance. Dùng gì?

**Đáp án:** AWS Config rules/conformance pack; có thể dùng Access Analyzer/Security Hub bổ sung.

### Câu 11

Muốn phát hiện API call bất thường từ credential bị compromise. Dùng gì?

**Đáp án:** GuardDuty, kết hợp CloudTrail và response workflow.

### Câu 12

Muốn tìm PII trong S3. Dùng gì?

**Đáp án:** Macie; sau đó áp policy/encryption/access control phù hợp.

### Câu 13

Muốn tìm CVE trong EC2/container image. Dùng gì?

**Đáp án:** Inspector, kết hợp patch/remediation process.

### Câu 14

Lambda private cần truy cập Secrets Manager nhưng không có Internet/NAT. Thiết kế gì?

**Đáp án:** VPC interface endpoint cho Secrets Manager, route/DNS/endpoint SG đúng, IAM role và KMS permission nếu secret encrypted.

---

## 20. Checklist hoàn thành tuần

### Kiến thức

- [ ] Phân biệt customer managed, AWS managed và AWS owned key.
- [ ] Hiểu key policy, IAM policy và grants.
- [ ] Giải thích envelope encryption.
- [ ] Chọn SSE-S3, SSE-KMS hoặc client-side encryption.
- [ ] Hiểu key rotation, disable và schedule deletion.
- [ ] Phân biệt Secrets Manager với Parameter Store.
- [ ] Hiểu ACM và TLS termination.
- [ ] Thiết kế WAF managed/rate-based rule.
- [ ] Phân biệt WAF với Shield.
- [ ] Phân biệt CloudTrail management/data events.
- [ ] Hiểu AWS Config và conformance pack.
- [ ] Phân biệt GuardDuty, Macie, Inspector và Security Hub.
- [ ] Chọn CloudTrail Logs, CloudWatch Logs hoặc VPC Flow Logs.
- [ ] Thiết kế private endpoint và anti-exfiltration controls.
- [ ] Vẽ defense-in-depth cho public API.

### Thực hành

- [ ] Đã tạo KMS key và mã hóa S3/RDS test.
- [ ] Đã kiểm tra thiếu KMS permission gây AccessDenied.
- [ ] Đã đặt secret trong Secrets Manager và cấp role đọc.
- [ ] Đã bật CloudTrail và truy vấn event.
- [ ] Đã tạo WAF rate rule trước ALB/API Gateway.
- [ ] Đã bật VPC Flow Logs và kiểm tra accepted/rejected flow.
- [ ] Đã review finding/metric/log liên quan.
- [ ] Đã kiểm tra không log secret/PII.
- [ ] Đã dọn key/resource/test finding theo quy trình an toàn.

Bạn sẵn sàng sang Tuần 12 khi có thể chọn đúng encryption/key/secret/logging/detection control, giải thích data exfiltration path và thiết kế defense-in-depth thay vì chỉ thêm một lớp bảo vệ.

---

## 21. Tài liệu tham khảo

- [AWS Key Management Service Developer Guide](https://docs.aws.amazon.com/kms/latest/developerguide/overview.html) — KMS, key policy và grants.
- [AWS KMS envelope encryption](https://docs.aws.amazon.com/kms/latest/developerguide/concepts.html) — data key và envelope encryption.
- [Amazon S3 server-side encryption](https://docs.aws.amazon.com/AmazonS3/latest/userguide/serv-side-encryption.html) — SSE-S3/SSE-KMS/SSE-C.
- [AWS Secrets Manager](https://docs.aws.amazon.com/secretsmanager/latest/userguide/intro.html) — secrets và rotation.
- [Systems Manager Parameter Store](https://docs.aws.amazon.com/systems-manager/latest/userguide/systems-manager-parameter-store.html) — parameters/configuration.
- [AWS Certificate Manager](https://docs.aws.amazon.com/acm/latest/userguide/acm-overview.html) — certificate và TLS.
- [AWS WAF Developer Guide](https://docs.aws.amazon.com/waf/latest/developerguide/what-is-aws-waf.html) — web ACL/rules.
- [AWS Shield](https://docs.aws.amazon.com/waf/latest/developerguide/ddos-overview.html) — DDoS protection.
- [AWS CloudTrail](https://docs.aws.amazon.com/awscloudtrail/latest/userguide/cloudtrail-user-guide.html) — API audit.
- [AWS Config](https://docs.aws.amazon.com/config/latest/developerguide/WhatIsConfig.html) — configuration/compliance.
- [Amazon GuardDuty](https://docs.aws.amazon.com/guardduty/latest/ug/what-is-guardduty.html) — threat detection.
- [Amazon Macie](https://docs.aws.amazon.com/macie/latest/user/what-is-macie.html) — sensitive data discovery.
- [Amazon Inspector](https://docs.aws.amazon.com/inspector/latest/user/what-is-amazon-inspector.html) — vulnerability management.
- [AWS Security Hub](https://docs.aws.amazon.com/securityhub/latest/userguide/what-is-securityhub.html) — central findings/compliance.
- [VPC Flow Logs](https://docs.aws.amazon.com/vpc/latest/userguide/flow-logs.html) — network flow metadata.


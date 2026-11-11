# Tuần 2 — IAM, accounts và security fundamentals

> Mục tiêu: xây dựng nền tảng identity và access control đúng nguyên tắc least privilege, hiểu policy evaluation, dùng role/temporary credentials và tổ chức nhiều AWS account ở mức SAA-C03.

## 1. Kết quả cần đạt

- Phân biệt root user, IAM user, group, role và policy.
- Đọc policy qua `Effect`, `Action`, `Resource`, `Principal` và `Condition`.
- Phân biệt identity-based policy với resource-based policy.
- Giải thích implicit deny, explicit deny và policy evaluation logic.
- Viết least-privilege policy cho workload đọc một prefix S3.
- Dùng IAM role cho EC2, Lambda và ECS thay cho access key tĩnh.
- Giải thích STS, `AssumeRole`, temporary credentials và cross-account access.
- Hiểu MFA, password policy, access key rotation và IAM Access Analyzer.
- Phân biệt Organizations, account, SCP, permissions boundary và IAM Identity Center.
- Chẩn đoán nguyên nhân thường gặp của `AccessDenied`.

## 2. Lịch học gợi ý

| Buổi | Nội dung | Thời lượng |
|---|---|---:|
| 1 | Root, IAM identity, policy và ARN | 2 giờ |
| 2 | Policy evaluation, least privilege và policy lab | 3 giờ |
| 3 | Role, STS, temporary credentials và cross-account | 2 giờ |
| 4 | Organizations, SCP, boundaries, Identity Center | 2 giờ |
| 5 | MFA, Access Analyzer và câu hỏi thi | 2 giờ |

Khi debug một request bị từ chối, kiểm tra theo thứ tự: principal, action, resource, identity policy, resource policy, trust policy, boundary, SCP, session policy và condition.

---

## 3. IAM identities

### 3.1. Root user

Root user là identity tạo cùng AWS account và có quyền cao nhất. Một số tác vụ quản trị account yêu cầu root, nhưng root không dùng cho công việc hằng ngày hoặc cho ứng dụng.

Việc cần làm:

- bật MFA;
- không tạo access key cho root;
- bảo vệ email và thông tin recovery;
- theo dõi hoạt động root bằng CloudTrail.

Root không bị giới hạn bởi IAM policy thông thường, nên bảo vệ root là security control nền tảng.

### 3.2. IAM user

IAM user là identity lâu dài trong một account, có thể có password console hoặc access key. User phù hợp cho một số legacy case, nhưng nên ưu tiên IAM Identity Center, federation, role và temporary credentials.

Không tạo user dùng chung cho cả team: sẽ mất khả năng audit và khó thu hồi quyền đúng người.

### 3.3. Group

Group là tập hợp IAM user để gắn identity-based policy theo chức năng. Group không phải principal để assume role và không thể chứa role.

Ví dụ: `ReadOnlyDevelopers`, `BillingViewers`, `PlatformAdmins`.

### 3.4. Role

IAM role là identity có permissions policy nhưng không có credential dài hạn cố định. Principal được tin cậy có thể assume role để nhận temporary credentials.

Role thường dùng cho:

- EC2 instance profile;
- Lambda execution role;
- ECS task role;
- cross-account access;
- federation và IAM Identity Center;
- AWS service gọi AWS service khác.

Một role cần xem hai phần:

1. **Trust policy:** ai được phép assume role.
2. **Permissions policy:** role được làm gì sau khi assume.

Có permissions Allow nhưng trust policy không cho principal assume thì request vẫn thất bại.

### 3.5. Policy

Policy là tài liệu JSON mô tả quyền. Các field quan trọng:

- `Effect`: `Allow` hoặc `Deny`;
- `Principal`: principal, thường dùng trong resource/trust policy;
- `Action`/`NotAction`: API action;
- `Resource`/`NotResource`: tài nguyên;
- `Condition`: điều kiện của request.

Không phải mọi loại policy hỗ trợ mọi field. Identity-based policy thường không có `Principal`; trust policy bắt buộc mô tả principal được assume role.

---

## 4. ARN, action, resource và condition

### 4.1. ARN

ARN định danh tài nguyên AWS:

```text
arn:partition:service:region:account-id:resource
```

Ví dụ:

```text
arn:aws:s3:::company-documents
arn:aws:s3:::company-documents/reports/report.pdf
arn:aws:iam::123456789012:role/AppReadOnlyRole
arn:aws:lambda:ap-southeast-1:123456789012:function:ProcessOrder
```

Không phải dịch vụ nào cũng dùng region hoặc account ID. S3 bucket ARN thường không có region; IAM ARN thường không có region. Gắn sai ARN là nguyên nhân phổ biến của `AccessDenied`.

### 4.2. Action và resource

`s3:ListBucket` dùng bucket ARN, còn `s3:GetObject` dùng object ARN. Ví dụ policy đúng cho một prefix:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "ListReportsPrefix",
      "Effect": "Allow",
      "Action": "s3:ListBucket",
      "Resource": "arn:aws:s3:::company-documents",
      "Condition": {
        "StringLike": {
          "s3:prefix": ["reports", "reports/*"]
        }
      }
    },
    {
      "Sid": "ReadReportObjects",
      "Effect": "Allow",
      "Action": "s3:GetObject",
      "Resource": "arn:aws:s3:::company-documents/reports/*"
    }
  ]
}
```

### 4.3. Condition

`Condition` giới hạn request theo context, ví dụ source IP, VPC endpoint, tag, MFA, TLS, thời gian hoặc principal.

```json
"Condition": {
  "Bool": {
    "aws:MultiFactorAuthPresent": "true"
  }
}
```

Condition mạnh nhưng dễ gây lỗi nếu key không có trong context hoặc dùng sai operator. Khi debug, xem request context và CloudTrail.

---

## 5. Identity-based và resource-based policy

### Identity-based policy

Gắn vào user, group hoặc role; mô tả identity được làm gì trên resource nào.

```json
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Action": "s3:GetObject",
    "Resource": "arn:aws:s3:::company-documents/reports/*"
  }]
}
```

### Resource-based policy

Gắn trực tiếp vào resource, ví dụ S3 bucket, SQS queue, SNS topic, KMS key hoặc Lambda function. Policy có `Principal` để chỉ ra ai được truy cập.

```json
{
  "Version": "2012-10-17",
  "Statement": [{
    "Sid": "AllowPartnerRead",
    "Effect": "Allow",
    "Principal": {
      "AWS": "arn:aws:iam::210987654321:role/PartnerAuditRole"
    },
    "Action": "s3:GetObject",
    "Resource": "arn:aws:s3:::company-documents/audit/*"
  }]
}
```

| Đặc điểm | Identity-based | Resource-based |
|---|---|---|
| Gắn vào | user, group, role | resource |
| Mô tả | identity được làm gì | principal nào được truy cập |
| Có `Principal`? | thường không | có |
| Ví dụ | role đọc S3 | bucket cho account khác đọc |

Cross-account có thể dùng resource policy trực tiếp hoặc role assumption. Đọc kỹ đề để xác định policy nằm ở account nào.

---

## 6. Policy evaluation logic

### 6.1. Quy tắc cốt lõi

1. Mọi request bắt đầu là **implicit deny**.
2. `Allow` phù hợp có thể cho phép request.
3. `Explicit Deny` phù hợp luôn thắng `Allow`.
4. Policy sai action, resource hoặc condition không cấp quyền.

```text
Implicit deny
      ↓
Có Allow phù hợp? ── không ──> Deny
      ↓ có
Có Explicit Deny? ── có ──> Deny
      ↓ không
Kiểm tra boundary/SCP/session/condition → Allow hoặc Deny
```

- **Implicit deny:** không có Allow phù hợp.
- **Explicit deny:** statement `Deny` trực tiếp chặn request, kể cả có Allow ở policy khác.

### 6.2. Các lớp giới hạn

Ở mức SAA, quyền hiệu dụng còn bị giới hạn bởi:

- identity-based policy;
- resource-based policy;
- permissions boundary;
- Organizations SCP/RCP nếu áp dụng;
- session policy;
- trust policy với `AssumeRole`;
- condition và request context.

SCP và permissions boundary không tự cấp quyền; chúng chỉ đặt **maximum permissions**. Vẫn cần một `Allow` thực tế.

### 6.3. Debug `AccessDenied`

1. Principal thực sự là user, role hay assumed-role nào?
2. Action API chính xác là gì?
3. Resource ARN có đúng loại và path không?
4. Có identity policy Allow phù hợp không?
5. Có resource policy yêu cầu principal khác không?
6. Trust policy có cho phép assume role không?
7. Có explicit deny không?
8. SCP, boundary hoặc session policy có chặn không?
9. Condition có yêu cầu MFA, TLS, source IP, tag hoặc endpoint không?
10. Request có đi đúng account và Region không?

CloudTrail giúp xác định principal, action, resource và error. IAM Policy Simulator hỗ trợ test policy nhưng vẫn phải đối chiếu resource policy, SCP, boundary và runtime context.

---

## 7. Least privilege

Least privilege là cấp đúng quyền cần thiết, trên đúng resource, trong đúng thời gian và context.

Quy trình:

1. Xác định principal/workload.
2. Liệt kê API action thực sự cần.
3. Chọn resource ARN cụ thể.
4. Thêm condition nếu có thể.
5. Tránh `Action: "*"` và `Resource: "*"` nếu không có lý do.
6. Test happy path và denied path.
7. Theo dõi CloudTrail/Access Analyzer rồi thu hẹp dần.

Đừng giải quyết `AccessDenied` bằng cách thêm `AdministratorAccess` ngay. Hãy tìm chính xác lớp policy hoặc ARN gây lỗi.

Quyền lâu dài làm tăng blast radius. Ưu tiên temporary credentials, role assumption, federation, permission set và access review định kỳ.

---

## 8. IAM role cho workload

### EC2 instance role

Ứng dụng trên EC2 nên lấy credential qua instance profile từ role:

```text
EC2 → instance profile → IAM role → temporary credentials → AWS API
```

Trust policy tối thiểu:

```json
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Principal": {"Service": "ec2.amazonaws.com"},
    "Action": "sts:AssumeRole"
  }]
}
```

Không đặt access key trong source code, user data, AMI hoặc file cấu hình trên máy.

### Lambda execution role

Lambda dùng execution role để gọi AWS API. Role cần trust principal `lambda.amazonaws.com` và permissions chỉ cho log, S3, DynamoDB, SNS… theo đúng function.

### ECS task role và execution role

- **Task role:** credential mà code trong container dùng để gọi AWS API.
- **Task execution role:** ECS/Fargate dùng để pull image từ ECR, ghi log hoặc lấy secret theo task configuration.

Không gắn quyền ứng dụng vào execution role nếu code trong container không cần quyền đó.

---

## 9. STS và cross-account access

### 9.1. Temporary credentials

STS cấp temporary credentials gồm access key ID, secret access key và session token. Chúng có thời hạn, giảm rủi ro so với access key dài hạn.

Flow `AssumeRole`:

```text
Principal A --sts:AssumeRole--> Trust policy của Role B
                                  ↓ cho phép
                        STS cấp temporary credentials
```

Thường cần hai điều kiện:

1. Principal A được phép gọi `sts:AssumeRole` trên Role B.
2. Trust policy của Role B tin cậy A.

### 9.2. Cross-account role

```text
Account A: team role
        │ AssumeRole
        ▼
Account B: AuditReadOnlyRole
        │
        ▼
Resource trong Account B
```

Ưu điểm: không chia sẻ access key, account đích kiểm soát role/thời hạn, audit rõ ai assume và dễ thu hồi.

Cross-account S3 có thể dùng bucket policy hoặc role trong account consumer. Kiểm tra principal ARN, trust relationship, identity policy, bucket/object ARN và KMS key policy nếu có encryption bằng customer managed key.

---

## 10. MFA, password policy và access key

### MFA

MFA bổ sung yếu tố xác thực cho console và flow nhạy cảm. Bắt buộc MFA cho root và identity có quyền cao.

Có thể dùng condition yêu cầu MFA cho hành động nhạy cảm, nhưng phải test để không tự khóa account quản trị.

### Password policy

Có thể yêu cầu độ dài, độ phức tạp, password history, expiration và self-service change. Password policy không thay thế MFA hoặc federation.

### Access key

Nếu bắt buộc dùng access key:

- lưu trong secret/credential store an toàn;
- không commit vào Git, log hoặc image;
- rotate và revoke khi không cần;
- theo dõi last-used;
- ưu tiên role/temporary credentials cho AWS workload.

Rotation không chữa được key đã lộ: deactivate/delete key, tìm nơi sử dụng và điều tra CloudTrail ngay.

---

## 11. IAM Access Analyzer

IAM Access Analyzer hỗ trợ:

- phát hiện resource policy cho phép public/cross-account ngoài ý muốn;
- validate policy;
- generate policy từ hoạt động quan sát được;
- review findings và xử lý theo risk.

Access Analyzer không thay thế threat modeling, code review, CloudTrail hoặc security monitoring. Không có finding không có nghĩa workload an toàn tuyệt đối.

---

## 12. Organizations và account strategy

### 12.1. Vì sao tách account?

AWS account là security và billing boundary hữu ích. Tách account giúp cô lập blast radius, tách production/non-production, áp dụng policy khác nhau, phân bổ billing và hỗ trợ compliance.

Mô hình tham khảo:

```text
Organization
├── Security/Audit account
├── Log Archive account
├── Infrastructure account
├── Production account
└── Development/Sandbox account
```

Không cần tạo account chỉ để tạo account; hãy tách khi có requirement về isolation, billing, compliance, ownership hoặc blast radius.

### 12.2. Organizations

- **Management account:** quản lý organization; không chạy workload thường ngày.
- **Member account:** account thuộc organization.
- **OU:** nhóm account theo environment, business unit hoặc control boundary.
- **Consolidated billing:** gom billing, không tự động cấp quyền giữa account.

### 12.3. Service Control Policy

SCP đặt maximum permissions cho account hoặc OU. SCP không cấp quyền cho user/role.

```text
SCP cho phép tối đa S3 + EC2
Identity policy cho phép s3:GetObject
→ request có thể được phép

SCP chỉ cho EC2
Identity policy cho phép s3:GetObject
→ request bị chặn
```

SCP thường dùng để deny Region ngoài danh sách, deny tắt CloudTrail/security service, bảo vệ log archive hoặc giới hạn service ở sandbox. Explicit deny trong SCP thắng Allow ở member account, kể cả `AdministratorAccess`.

### 12.4. Permissions boundary

Permissions boundary là policy xác định maximum permissions mà một user/role có thể nhận. Nó hữu ích khi developer được phép tạo role nhưng role mới chỉ được nằm trong phạm vi an toàn.

| Cơ chế | Phạm vi | Mục đích |
|---|---|---|
| IAM policy | user/group/role/resource | cấp quyền cụ thể |
| Permissions boundary | một user/role | giới hạn tối đa của identity |
| SCP | account/OU | giới hạn tối đa trong organization |
| Trust policy | role | quyết định ai được assume role |

Boundary không tự cấp `Allow`; action cần vừa có Allow, vừa không bị boundary giới hạn.

### 12.5. IAM Identity Center

IAM Identity Center (AWS SSO trước đây) quản lý workforce access vào nhiều account và ứng dụng. Người dùng đăng nhập qua identity source rồi nhận permission set/role ở account đích.

Mức SAA cần nhớ:

- workforce identity khác application identity;
- Identity Center phù hợp human access;
- role phù hợp workload và cross-account;
- không đưa password/access key của nhân viên vào ứng dụng.

---

## 13. Security fundamentals liên quan IAM

IAM chỉ là một lớp của defense in depth:

```text
Identity → authorization → network boundary → encryption → logging → detection → recovery
```

### Data protection

- **In transit:** TLS/HTTPS và policy yêu cầu secure transport.
- **At rest:** encryption bằng AWS managed key hoặc customer managed KMS key.
- **Secrets:** Secrets Manager hoặc Parameter Store, không đặt trong source code.
- **KMS key policy:** một lớp authorization cần kiểm tra thêm.

Encryption không tự giải quyết authorization; người dùng vẫn cần quyền decrypt và data access đúng.

### Network security cần nhớ trước Tuần 3

- Security group là **stateful**, gắn với ENI/resource.
- Network ACL là **stateless**, áp dụng ở subnet level.
- SG chủ yếu có allow rule; NACL có allow/deny theo rule number.
- IAM policy không thay thế SG/NACL.

---

## 14. Lab bắt buộc

### Lab 1 — Bảo vệ account

- [ ] Bật MFA cho root.
- [ ] Kiểm tra root không có access key.
- [ ] Tạo role/user quản trị học tập, không dùng root hằng ngày.
- [ ] Đặt password policy.
- [ ] Bật CloudTrail theo baseline môi trường học.
- [ ] Kiểm tra IAM credential report.

Không tạo credential thật trong Markdown, Git repository, shell history hoặc screenshot.

### Lab 2 — EC2 role đọc S3

1. Tạo bucket lab và bật Block Public Access.
2. Tạo role với trust policy cho `ec2.amazonaws.com`.
3. Cấp `s3:ListBucket` trên bucket và `s3:GetObject` trên một prefix.
4. Gắn role qua instance profile vào EC2.
5. Kiểm tra `aws sts get-caller-identity` từ EC2.
6. Đọc object được phép và thử đọc prefix khác.
7. Thử delete object để xác nhận bị từ chối.
8. Xóa EC2, role và bucket/object sau lab.

### Lab 3 — Policy evaluation

Tạo identity policy Allow đọc S3, sau đó thêm explicit Deny cho một prefix. Kiểm tra:

| Principal | Action | Resource | Expected | Lý do |
|---|---|---|---|---|
| LabRole | GetObject | allowed prefix | Allow | có Allow phù hợp |
| LabRole | GetObject | denied prefix | Deny | explicit Deny thắng |
| LabRole | DeleteObject | any object | Deny | implicit deny |

### Lab 4 — Cross-account AssumeRole

Chỉ làm với hai account test an toàn:

1. Account B tạo `AuditReadOnlyRole`.
2. Trust policy chỉ tin cậy role cụ thể ở Account A.
3. Permissions policy ở B chỉ read-only.
4. Account A được phép `sts:AssumeRole`.
5. Assume role và kiểm tra ARN bằng `get-caller-identity`.
6. Thử action ngoài phạm vi.

Không dùng trust `Principal: "*"` trong production.

### Lab 5 — Access Analyzer

- [ ] Tạo resource policy cross-account có chủ đích.
- [ ] Quan sát finding và principal/resource.
- [ ] Xóa quyền hoặc xử lý finding theo quy trình.
- [ ] Validate policy với ARN/action đúng.

---

## 15. Bẫy đề thi SAA-C03

- Không cấp access key tĩnh cho EC2, Lambda hoặc ECS nếu có IAM role.
- SCP không cấp quyền; chỉ giới hạn maximum permissions.
- Permissions boundary không cấp quyền.
- Có Allow vẫn bị từ chối nếu có explicit Deny.
- Role cần cả trust policy và permissions policy.
- `s3:ListBucket` dùng bucket ARN; `s3:GetObject` dùng object ARN.
- Group không được assume role.
- Resource-based policy cần `Principal`; identity policy thường không có.
- Cross-account có thể cần policy ở cả hai phía tùy pattern.
- Security group stateful, NACL stateless; đây là lớp khác IAM.
- Root không dùng cho ứng dụng hoặc công việc hằng ngày.
- Rotation không thay thế revoke và điều tra key đã lộ.
- `AdministratorAccess` trong member account vẫn có thể bị SCP chặn.
- IAM policy không tự mở network path; phải kiểm tra SG, NACL, route, endpoint và DNS.

---

## 16. Câu hỏi tự luyện

### Câu 1

EC2 cần đọc object từ S3. Cách an toàn nhất là gì?

**Đáp án:** gắn IAM role qua instance profile, cấp quyền tối thiểu và dùng temporary credentials; không nhúng access key.

### Câu 2

Role có `Allow s3:GetObject` nhưng vẫn bị `AccessDenied`. Nguyên nhân có thể là gì?

**Đáp án:** sai object ARN, thiếu quyền prefix, explicit deny, SCP, boundary, session policy, bucket policy/condition, sai account/Region hoặc context không thỏa.

### Câu 3

IAM user có `AdministratorAccess` nhưng không tạo resource ở Region mới. Vì sao?

**Đáp án:** SCP có thể giới hạn Region/service; SCP đặt maximum permissions và explicit deny ở SCP thắng Allow trong account.

### Câu 4

Developer được phép tạo role nhưng role mới không được vượt phạm vi quyền phê duyệt. Dùng gì?

**Đáp án:** permissions boundary, kết hợp policy yêu cầu boundary phải được gắn vào role mới.

### Câu 5

Account A cần truy cập resource của Account B mà không chia sẻ access key. Thiết kế nào?

**Đáp án:** role ở B, trust policy cho A assume, identity policy ở A cho `sts:AssumeRole`, dùng temporary credentials.

### Câu 6

Bucket policy cho phép role account khác đọc nhưng vẫn bị từ chối. Kiểm tra gì?

**Đáp án:** principal ARN, object ARN, identity policy, trust policy, SCP/boundary, KMS key policy nếu SSE-KMS và condition.

### Câu 7

Team cần truy cập nhiều account bằng một lần đăng nhập, không tạo IAM user trong từng account. Dùng gì?

**Đáp án:** IAM Identity Center với permission set/federation.

### Câu 8

Muốn biết resource policy nào cho phép account ngoài tổ chức truy cập. Dùng gì?

**Đáp án:** IAM Access Analyzer, sau đó review finding.

---

## 17. Checklist hoàn thành tuần

### Kiến thức

- [ ] Phân biệt root, user, group, role và policy.
- [ ] Giải thích trust policy và permissions policy.
- [ ] Đọc `Effect`, `Action`, `Resource`, `Principal`, `Condition`.
- [ ] Phân biệt identity-based và resource-based policy.
- [ ] Giải thích implicit deny và explicit deny.
- [ ] Biết SCP/boundary chỉ giới hạn maximum permissions.
- [ ] Giải thích STS và AssumeRole.
- [ ] Biết flow cross-account access.
- [ ] Hiểu MFA, password policy và access key rotation.
- [ ] Nêu use case của Access Analyzer, Organizations và Identity Center.

### Thực hành

- [ ] Root đã bật MFA và không có access key.
- [ ] Đã tạo EC2 role đọc một prefix S3.
- [ ] Đã test allowed path và denied path.
- [ ] Đã tạo explicit deny và quan sát kết quả.
- [ ] Đã kiểm tra policy bằng Simulator/CloudTrail.
- [ ] Đã thực hành hoặc mô phỏng AssumeRole cross-account.
- [ ] Đã chạy Access Analyzer và xử lý finding.
- [ ] Đã dọn resource và credential sau lab.

Bạn sẵn sàng sang Tuần 3 khi có thể giải thích một `AccessDenied` bằng policy evaluation thay vì chỉ thêm `AdministratorAccess`, và viết được policy role đọc đúng một prefix S3 mà không dùng credential tĩnh.

---

## 18. Tài liệu tham khảo

- [AWS IAM](https://docs.aws.amazon.com/IAM/latest/UserGuide/introduction.html) — tổng quan IAM.
- [IAM policy evaluation logic](https://docs.aws.amazon.com/IAM/latest/UserGuide/reference_policies_evaluation-logic.html) — cách AWS đánh giá policy.
- [IAM roles](https://docs.aws.amazon.com/IAM/latest/UserGuide/id_roles.html) — role và trust policy.
- [STS temporary security credentials](https://docs.aws.amazon.com/IAM/latest/UserGuide/id_credentials_temp.html) — temporary credentials và AssumeRole.
- [AWS Organizations SCPs](https://docs.aws.amazon.com/organizations/latest/userguide/orgs_manage_policies_scps.html) — giới hạn quyền account/OU.
- [IAM permissions boundaries](https://docs.aws.amazon.com/IAM/latest/UserGuide/access_policies_boundaries.html) — maximum permissions cho user/role.
- [IAM Access Analyzer](https://docs.aws.amazon.com/IAM/latest/UserGuide/what-is-access-analyzer.html) — phát hiện quyền truy cập ngoài dự kiến.
- [AWS Security Pillar](https://docs.aws.amazon.com/wellarchitected/latest/security-pillar/welcome.html) — security best practices.


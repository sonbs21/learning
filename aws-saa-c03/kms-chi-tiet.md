# AWS Key Management Service chuyên sâu — AWS SAA-C03

> Tài liệu này tách riêng và mở rộng phần AWS KMS từ Tuần 11. Mục tiêu là hiểu KMS như một hệ thống quản lý quyền sử dụng khóa mật mã, không chỉ là nút “Enable encryption”. Sau khi học xong, bạn phải giải thích được dữ liệu được mã hóa bằng khóa nào, plaintext xuất hiện ở đâu, principal nào cần quyền gì, vì sao IAM Allow vẫn có thể bị AccessDenied, rotation thực sự thay đổi điều gì và chuyện gì xảy ra nếu key bị disable hoặc xóa.

## 1. Tình huống mở đầu: role đọc được S3 nhưng vẫn không mở được file

Một application role có quyền:

~~~json
{
  "Effect": "Allow",
  "Action": "s3:GetObject",
  "Resource": "arn:aws:s3:::financial-reports/*"
}
~~~

Object tồn tại, bucket policy không chặn, nhưng application nhận `AccessDenied` khi tải file.

Nguyên nhân: object được mã hóa bằng SSE-KMS. Đọc object cần đi qua hai cánh cửa độc lập:

~~~text
Application role
      │
      ├── 1. s3:GetObject được phép?
      │
      └── 2. kms:Decrypt trên đúng KMS key được phép?
                    │
                    ├── key policy
                    ├── IAM policy
                    ├── grant
                    ├── SCP
                    └── VPC endpoint policy/condition
~~~

Quyền đọc ciphertext không tự động cho quyền giải mã. Ngược lại, có `kms:Decrypt` cũng không cho phép đọc object nếu thiếu `s3:GetObject`.

Đây là tư duy trung tâm khi học KMS: **bảo vệ dữ liệu và cho phép sử dụng khóa là hai lớp authorization liên quan nhưng không đồng nhất**.

---

# Phần I — KMS giải quyết vấn đề gì?

## 2. AWS KMS là gì?

AWS Key Management Service là dịch vụ quản lý khóa được AWS vận hành, cho phép:

- Tạo KMS key.
- Quản lý policy, alias, tag và lifecycle của key.
- Mã hóa, giải mã hoặc re-encrypt dữ liệu nhỏ.
- Tạo data key cho envelope encryption.
- Ký và xác minh chữ ký số với asymmetric key.
- Tạo và xác minh HMAC.
- Tích hợp khóa với nhiều dịch vụ AWS.
- Audit hoạt động quản lý và sử dụng key qua CloudTrail.

AWS quản lý hạ tầng HSM, tính sẵn sàng và bảo vệ key material cho standard KMS key. Bạn chịu trách nhiệm:

- Chọn đúng loại key.
- Thiết kế key policy và IAM policy.
- Tách key administrator khỏi key user.
- Chọn phạm vi key phù hợp.
- Rotation, disable và deletion governance.
- Theo dõi usage, error và quota.
- Đảm bảo dữ liệu vẫn khôi phục được khi key gặp sự cố.

## 3. KMS key không phải password hay secret

KMS key là logical key resource đại diện cho key material và metadata quản lý nó.

KMS không phải nơi phù hợp để lưu:

- Database password.
- API token.
- OAuth client secret.
- Private configuration string.

Các giá trị đó nên nằm trong Secrets Manager hoặc Parameter Store SecureString tùy yêu cầu. KMS là lớp mật mã dùng để bảo vệ secret/dữ liệu đó.

~~~text
Secrets Manager secret
       │ encrypted at rest by
       ▼
    KMS key
~~~

## 4. KMS key và key material

Phân biệt hai khái niệm:

| Khái niệm | Ý nghĩa |
|---|---|
| KMS key | AWS resource có key ID, ARN, policy, state, origin, usage và metadata |
| Key material | Bit mật mã thực sự dùng cho cryptographic operation |

Một KMS key có thể có nhiều phiên bản key material sau rotation. Key ID và ARN vẫn giữ nguyên; KMS chọn đúng phiên bản key material để decrypt ciphertext cũ.

## 5. Key material có rời khỏi KMS không?

Với key material do AWS KMS tạo trong standard key store:

- Key material được tạo và sử dụng trong HSM của KMS.
- Không thể export plaintext key material.
- Application gọi API để yêu cầu KMS thực hiện cryptographic operation.
- Plaintext data key do `GenerateDataKey` trả về có thể rời KMS, nhưng đó là **data key**, không phải KMS key material.

Đây là khác biệt rất quan trọng:

~~~text
KMS key material     → nằm trong KMS/HSM, không export plaintext
Plaintext data key   → tạm trả cho application/service để mã hóa dữ liệu
~~~

## 6. KMS không thay thế những control nào?

KMS không thay thế:

- IAM authorization cho dữ liệu.
- TLS khi truyền dữ liệu.
- Backup và disaster recovery.
- Data classification.
- Input validation.
- Secret rotation.
- WAF hoặc network control.
- Application-level idempotency.
- Object Lock hoặc integrity workflow.

Encryption làm ciphertext khó sử dụng nếu không có key. Nếu attacker đồng thời có quyền đọc dữ liệu và quyền dùng key, encryption không chặn được truy cập đó.

---

# Phần II — Các lớp khóa cần phân biệt

## 7. AWS owned key

AWS owned key là tập hợp key do một AWS service sở hữu và quản lý để bảo vệ tài nguyên trong nhiều account.

Đặc điểm:

- Không xuất hiện như một KMS key trong account của bạn.
- Bạn không xem hoặc sửa key policy.
- Không disable, rotate hoặc schedule deletion.
- AWS service quyết định lifecycle và cách sử dụng.
- Thường tạo trải nghiệm encryption mặc định đơn giản nhất.

Chọn khi service-managed encryption đáp ứng yêu cầu và không cần quyền kiểm soát/audit riêng trên một key resource.

## 8. AWS managed key

AWS managed key được AWS service tạo trong account và Region của bạn, thường có alias dạng:

~~~text
alias/aws/s3
alias/aws/ebs
alias/aws/rds
~~~

Đặc điểm:

- Có thể xem key và CloudTrail usage liên quan.
- AWS service quản lý policy và rotation.
- Bạn không sửa key policy.
- Không disable hoặc schedule deletion theo cách customer managed key cho phép.
- Thường chỉ dùng trong service đã tạo key.
- AWS tự động rotate key material hằng năm theo tài liệu hiện hành.

AWS managed key thuận tiện nhưng hạn chế cho cross-account sharing và policy tùy chỉnh.

## 9. Customer managed key

Customer managed key do bạn tạo và quản lý.

Bạn kiểm soát:

- Key policy.
- IAM delegation.
- Grants.
- Alias và tag.
- Enable/disable.
- Automatic/on-demand/manual rotation strategy.
- Schedule/cancel deletion.
- Multi-Region property lúc tạo.
- Key material origin.

Customer managed key phù hợp khi cần:

- Least-privilege policy riêng.
- Cross-account use.
- Audit theo key/data classification.
- Disable nhanh để revoke access.
- Rotation policy riêng.
- Tách key theo workload hoặc tenant boundary hợp lý.
- Compliance yêu cầu customer control.

## 10. So sánh ba lớp key ownership

| Tiêu chí | AWS owned | AWS managed | Customer managed |
|---|---|---|---|
| Hiển thị trong account | Không | Có | Có |
| Sửa key policy | Không | Không | Có |
| Tự disable/delete | Không | Không | Có |
| Tùy chỉnh rotation | Không | Không | Có |
| Cross-account linh hoạt | Không | Hạn chế | Có thể thiết kế |
| Audit/control chi tiết | Thấp nhất | Trung bình | Cao nhất |
| Vận hành | Ít nhất | Ít | Nhiều nhất |

Không chọn customer managed key chỉ vì “bảo mật hơn” theo cảm tính. Nó cung cấp nhiều quyền kiểm soát hơn, đồng thời tạo thêm chi phí, quota và rủi ro tự khóa dữ liệu.

---

# Phần III — Key spec và key usage

## 11. Symmetric encryption KMS key

Đây là loại phổ biến nhất cho AWS service integration.

Đặc điểm:

- Một secret key logic dùng cho encrypt và decrypt.
- Key spec thường là `SYMMETRIC_DEFAULT`.
- KMS sử dụng AES-256-GCM cho symmetric encryption theo thiết kế dịch vụ.
- Hỗ trợ `Encrypt`, `Decrypt`, `ReEncrypt` và data key operations.
- Hỗ trợ encryption context.
- Hầu hết AWS services tích hợp KMS yêu cầu symmetric key.

Nếu đề nói mã hóa S3, EBS, RDS, Secrets Manager hoặc Lambda environment variables, mặc định nghĩ tới symmetric KMS key.

## 12. Asymmetric KMS key

Asymmetric key có public key và private key.

~~~text
Public key  → có thể tải về để encrypt hoặc verify ngoài KMS
Private key → nằm trong KMS, không export plaintext
~~~

Key usage có thể là:

- `ENCRYPT_DECRYPT`.
- `SIGN_VERIFY`.
- `KEY_AGREEMENT` với key spec được hỗ trợ.

Use case:

- Bên ngoài AWS cần dùng public key.
- Digital signature cần verify ngoài KMS.
- Giao thức yêu cầu RSA/ECC public-private key semantics.

Asymmetric key không phải lựa chọn mặc định cho mã hóa dữ liệu lớn hoặc AWS managed service encryption.

## 13. HMAC KMS key

HMAC key dùng secret symmetric material để:

- `GenerateMac`.
- `VerifyMac`.

HMAC cung cấp integrity và authenticity: dữ liệu thay đổi hoặc dùng secret khác sẽ tạo MAC khác.

HMAC không mã hóa dữ liệu; nội dung vẫn có thể đọc được. Nó chứng minh message chưa bị sửa và được tạo bởi bên có secret phù hợp.

## 14. Key usage không đổi tùy ý

Khi tạo key, phải chọn purpose/key usage. Không thể dùng một signing key cho encrypt/decrypt chỉ vì policy cấp `kms:Encrypt`.

Authorization không thay đổi khả năng mật mã của key:

~~~text
Policy Allow + operation không tương thích key spec/usage → request vẫn fail
~~~

## 15. Chọn symmetric hay asymmetric?

| Requirement | Loại key |
|---|---|
| S3/EBS/RDS encryption | Symmetric |
| Envelope encryption | Symmetric |
| Cả hai phía dùng KMS và cần encryption | Thường symmetric |
| Client ngoài AWS phải encrypt bằng public key | Asymmetric encrypt/decrypt |
| Ký trong KMS, verify ngoài KMS | Asymmetric sign/verify |
| Message authentication bằng shared secret | HMAC |

---

# Phần IV — Direct encryption và envelope encryption

## 16. Direct `Encrypt` không dành cho file lớn

KMS `Encrypt` với symmetric key nhận tối đa 4.096 byte plaintext. Giới hạn asymmetric còn phụ thuộc algorithm/key spec và thường nhỏ hơn.

Do đó, không gửi file 100 MB trực tiếp vào `kms:Encrypt`.

Direct encryption phù hợp cho:

- Data key nhỏ.
- Token/configuration rất nhỏ.
- Secret blob nhỏ trong một số thiết kế.

Dữ liệu lớn phải dùng envelope encryption.

## 17. Envelope encryption là gì?

Envelope encryption dùng hai tầng key:

~~~text
KMS key (key-encryption key)
      │ bảo vệ
      ▼
Data key
      │ mã hóa
      ▼
Application data
~~~

KMS key không trực tiếp mã hóa toàn bộ file. Nó bảo vệ data key; data key mới mã hóa dữ liệu.

## 18. Quy trình mã hóa chi tiết

~~~text
Application                 AWS KMS
    │                          │
    │ GenerateDataKey          │
    ├─────────────────────────►│
    │                          │ tạo random data key
    │ plaintext data key       │ encrypt data key bằng KMS key
    │ encrypted data key       │
    ◄──────────────────────────┤
    │
    │ dùng plaintext data key mã hóa dữ liệu cục bộ
    │
    ├── ciphertext data
    ├── encrypted data key
    └── metadata/algorithm/context

    x xóa plaintext data key khỏi memory sớm nhất có thể
~~~

`GenerateDataKey` trả về:

- Plaintext data key.
- Ciphertext copy của data key được KMS key bảo vệ.

KMS không lưu data key để application lấy lại về sau. Bạn lưu encrypted data key cùng ciphertext data.

## 19. Quy trình giải mã chi tiết

~~~text
Storage
  ├── ciphertext data
  └── encrypted data key
            │
            ▼
Application ── Decrypt(encrypted data key, context) ──► KMS
            ◄──────────── plaintext data key ───────────
            │
            ├── decrypt ciphertext data cục bộ
            └── xóa plaintext data key khỏi memory
~~~

Nếu application thiếu `kms:Decrypt`, đọc được ciphertext và encrypted data key vẫn không giúp mở dữ liệu.

## 20. Vì sao envelope encryption hiệu quả?

- Dữ liệu lớn được mã hóa cục bộ bằng thuật toán symmetric nhanh.
- KMS chỉ xử lý data key nhỏ.
- Giảm số byte gửi tới KMS.
- Có thể cache data key có kiểm soát để giảm request/latency.
- KMS key policy tập trung kiểm soát khả năng mở data key.
- Rotation KMS key material không buộc mã hóa lại mọi file ngay.

## 21. `GenerateDataKeyWithoutPlaintext`

API này chỉ trả encrypted data key, không trả plaintext. Hữu ích khi một component cần provision encrypted key material nhưng không được phép nhìn thấy plaintext data key.

Ví dụ EBS có thể dùng operation này trong workflow tạo volume từ encrypted snapshot.

## 22. `ReEncrypt`

`ReEncrypt` cho phép chuyển ciphertext từ protection của key cũ sang key mới trong KMS:

~~~text
Ciphertext under Key A
       │ ReEncrypt
       ▼
Ciphertext under Key B
~~~

Plaintext không được trả về cho caller. Caller cần permission thích hợp trên source và destination key.

Use case:

- Chuyển data key sang customer managed key khác.
- Tách workload/account.
- Manual key migration.

Với dữ liệu service-managed như EBS snapshot hoặc S3 object, thường dùng copy operation của service để re-encrypt thay vì tự gọi KMS `ReEncrypt` lên toàn bộ dữ liệu.

## 23. Data key caching

AWS Encryption SDK có thể cache data key/material để giảm KMS calls, cost và latency.

Trade-off:

- Cache lâu làm tăng lượng dữ liệu dùng chung một data key.
- Plaintext material tồn tại trong memory lâu hơn.
- Phải giới hạn tuổi, số message và số byte mã hóa.

Không tự xây cache mật mã tùy tiện. Dùng implementation đã được kiểm chứng và security policy rõ ràng.

## 24. Không tự phát minh crypto format

Client-side encryption phải quản lý:

- Algorithm suite.
- Nonce/IV uniqueness.
- Authentication tag.
- Encrypted data key.
- Encryption context/AAD.
- Key commitment nếu format hỗ trợ.
- Versioning và migration.

Dùng AWS Encryption SDK hoặc SDK/service integration được hỗ trợ thay vì tự ghép AES, IV và metadata.

---

# Phần V — Encryption context

## 25. Encryption context là gì?

Encryption context là tập key-value **không bí mật** truyền vào cryptographic operation với symmetric KMS key.

Ví dụ:

~~~json
{
  "tenantId": "tenant-42",
  "purpose": "invoice",
  "invoiceId": "inv-1001"
}
~~~

KMS dùng context như Additional Authenticated Data. Nó được cryptographically bound với ciphertext.

## 26. Decrypt phải dùng context khớp chính xác

Nếu encrypt với:

~~~json
{"tenantId":"tenant-42"}
~~~

thì decrypt với context thiếu, khác value hoặc khác cách biểu diễn sẽ thất bại.

Context phân biệt hoa thường. Application phải lưu hoặc tái tạo chính xác.

## 27. Encryption context không phải metadata bí mật

Encryption context:

- Không được mã hóa.
- Có thể xuất hiện trong CloudTrail.
- Không nên chứa password, token, PII nhạy cảm hoặc secret.

Nó phù hợp cho resource ID, tenant ID không nhạy cảm, purpose hoặc classification label.

## 28. Dùng context để tăng authorization

Policy có thể yêu cầu context:

~~~json
{
  "Effect": "Allow",
  "Action": "kms:Decrypt",
  "Resource": "*",
  "Condition": {
    "StringEquals": {
      "kms:EncryptionContext:tenantId": "tenant-42"
    }
  }
}
~~~

Điều này giúp role chỉ decrypt ciphertext được gắn đúng context.

Encryption context không tự chứng minh caller thuộc tenant. Application vẫn phải authenticate/authorize tenant trước khi gọi KMS.

## 29. Context trong dịch vụ AWS

AWS services thường tự gửi encryption context. Ví dụ S3 SSE-KMS:

- Không bật S3 Bucket Key: context mặc định có object ARN.
- Bật S3 Bucket Key: context mặc định dùng bucket ARN.

Nếu policy condition dựa trên context, phải hiểu chính xác service gửi giá trị nào; bật Bucket Key có thể thay đổi granularity context cần xem xét.

## 30. Asymmetric/HMAC không dùng encryption context

Encryption context áp dụng cho symmetric encryption KMS operations. Không dùng nó với asymmetric key hoặc HMAC operation.

---

# Phần VI — Identifier, ARN và alias

## 31. Key ID và key ARN

Ví dụ key ID:

~~~text
1234abcd-12ab-34cd-56ef-1234567890ab
~~~

Key ARN:

~~~text
arn:aws:kms:ap-southeast-1:111122223333:key/1234abcd-12ab-34cd-56ef-1234567890ab
~~~

Key ARN xác định rõ partition, Region, account và key.

## 32. Alias

Alias là tên thân thiện trỏ tới KMS key:

~~~text
alias/prod-orders
alias/team-a-data
~~~

Alias có thể được cập nhật để trỏ sang key khác. Đây là công cụ hữu ích cho manual rotation/migration.

## 33. Alias không phải key

Alias:

- Là resource riêng.
- Không chứa key material.
- Có ARN dạng `...:alias/name`, khác key ARN.
- Region/account scoped.
- Có thể đổi target nhưng key ID/ARN của ciphertext cũ không đổi.

Đừng xóa key cũ chỉ vì alias đã chuyển sang key mới. Dữ liệu cũ có thể vẫn cần key cũ để decrypt.

## 34. Dùng alias trong policy

Có thể dùng condition như `kms:ResourceAliases`, nhưng phải hiểu:

- Alias có thể đổi target.
- Quyền cập nhật alias trở thành quyền nhạy cảm.
- Policy theo key ARN thường rõ ràng hơn cho boundary quan trọng.

Người có `UpdateAlias` có thể thay key mà application dùng nếu application tham chiếu alias. Tách quyền alias administration hợp lý.

---

# Phần VII — Authorization model của KMS

## 35. Bốn lớp cần kiểm tra

KMS có thể đánh giá đồng thời:

~~~text
1. Key policy
2. IAM identity policy
3. KMS grants
4. Boundary policies: SCP, permissions boundary, session policy,
   VPC endpoint policy và explicit deny khác
~~~

Một explicit deny phù hợp sẽ thắng allow.

## 36. Key policy là policy chính

Mỗi KMS key có đúng một key policy. Đây là resource-based policy gắn với key trong account và Region sở hữu key.

Khác nhiều resource khác, IAM Allow một mình không luôn đủ. Key policy phải:

- Trực tiếp cho principal sử dụng key; hoặc
- Cho account quyền dùng IAM policy để delegate.

## 37. Statement “Enable IAM User Permissions”

Default key policy thường có statement dạng:

~~~json
{
  "Sid": "Enable IAM User Permissions",
  "Effect": "Allow",
  "Principal": {
    "AWS": "arn:aws:iam::111122223333:root"
  },
  "Action": "kms:*",
  "Resource": "*"
}
~~~

Trong ngữ cảnh này, account principal không đơn thuần có nghĩa chỉ root user trực tiếp dùng key. Statement cho account khả năng sử dụng IAM policies để delegate KMS permissions cho principal trong account.

Nếu bỏ statement này và không trực tiếp liệt kê role trong key policy, một IAM Allow có thể không có hiệu lực.

## 38. Key administrator và key user

Tách hai vai trò:

### Key administrator

Quản lý metadata/lifecycle:

- Update key policy.
- Enable/disable key.
- Configure rotation.
- Manage aliases/tags.
- Schedule/cancel deletion theo governance.

### Key user

Dùng cryptographic operations:

- Encrypt.
- Decrypt.
- GenerateDataKey.
- ReEncrypt.
- Sign/Verify tùy key.

Quyền admin key không nên mặc nhiên có quyền decrypt business data. Tuy nhiên, người có quyền sửa key policy có thể tự cấp thêm quyền; do đó đây vẫn là quyền cực kỳ nhạy cảm.

## 39. Key user policy ví dụ

~~~json
{
  "Sid": "AllowApplicationUse",
  "Effect": "Allow",
  "Principal": {
    "AWS": "arn:aws:iam::111122223333:role/OrdersApplicationRole"
  },
  "Action": [
    "kms:Encrypt",
    "kms:Decrypt",
    "kms:ReEncrypt*",
    "kms:GenerateDataKey*",
    "kms:DescribeKey"
  ],
  "Resource": "*"
}
~~~

Trong key policy, `Resource: "*"` thường có nghĩa key đang chứa policy này, vì policy gắn trực tiếp với một key. Không sao chép pattern sang IAM policy mà không giới hạn resource ARN.

## 40. IAM policy cho application role

~~~json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "kms:Decrypt",
        "kms:GenerateDataKey"
      ],
      "Resource": "arn:aws:kms:ap-southeast-1:111122223333:key/1234abcd-12ab-34cd-56ef-1234567890ab"
    }
  ]
}
~~~

IAM policy nên trỏ key ARN cụ thể thay vì `Resource: "*"` khi operation hỗ trợ resource-level permission.

## 41. `kms:ViaService`

Condition `kms:ViaService` giới hạn key chỉ được dùng khi request đi qua một AWS service cụ thể.

Ví dụ khái niệm:

~~~json
{
  "Condition": {
    "StringEquals": {
      "kms:ViaService": "s3.ap-southeast-1.amazonaws.com"
    }
  }
}
~~~

Role có thể dùng key thông qua S3 nhưng không gọi trực tiếp `kms:Decrypt` cho mục đích khác, tùy toàn bộ policy.

## 42. `kms:GrantIsForAWSResource`

AWS services như EBS thường tạo grant thay mặt user để dùng KMS key cho resource.

Condition:

~~~json
"kms:GrantIsForAWSResource": true
~~~

cho phép tạo grant khi grant dành cho AWS resource, thay vì cho user tùy ý tạo grant rộng.

## 43. Explicit deny và boundary

Khi key policy và IAM đều Allow nhưng request vẫn fail, kiểm tra:

- SCP của AWS Organizations.
- Permissions boundary.
- Role session policy.
- VPC endpoint policy.
- Key state.
- Encryption context condition.
- Region/key ARN.
- Grant constraint.
- Resource policy của service chứa dữ liệu.

Không kết luận “KMS lỗi” chỉ từ một `AccessDeniedException`.

---

# Phần VIII — KMS grants

## 44. Grant là gì?

Grant là cơ chế cho principal hoặc AWS service quyền dùng một KMS key trong các operation cụ thể mà không phải sửa key policy liên tục.

Grant thường chứa:

- Grantee principal.
- Operations.
- Constraints.
- Retiring principal.
- Name tùy chọn.

## 45. Vì sao AWS services dùng grants?

Ví dụ EBS volume cần EC2/EBS dùng key trong vòng đời volume. Service có thể tạo một grant giới hạn cho resource đó.

~~~text
User tạo encrypted EBS volume
      │
      ├── có quyền EC2/EBS
      ├── có quyền dùng KMS key
      └── cho phép service tạo grant dành cho AWS resource
                         │
                         ▼
                    EBS dùng key
~~~

## 46. Grant khác key policy như thế nào?

| Key policy | Grant |
|---|---|
| Policy chính của key | Delegation bổ sung, hẹp hơn |
| Quản lý lâu dài | Thường gắn integration/resource lifecycle |
| Có nhiều loại action quản trị/sử dụng | Chỉ hỗ trợ tập grant operation xác định |
| Mỗi key có một policy | Một key có thể có nhiều grant |

## 47. Grant constraint

Grant có thể giới hạn bằng encryption context:

- `EncryptionContextEquals`.
- `EncryptionContextSubset`.

Constraint giúp grant chỉ dùng cho ciphertext/resource context phù hợp.

## 48. Eventual consistency và grant token

Việc tạo grant có eventual consistency. Nếu cần dùng grant ngay, caller có thể truyền grant token nhận được từ `CreateGrant` vào cryptographic request.

Đây là chi tiết hữu ích khi provisioning resource vừa tạo grant xong đã dùng key ngay.

## 49. Retire và revoke

- `RetireGrant`: retiring principal hoặc grantee tự kết thúc grant theo quyền.
- `RevokeGrant`: key administrator thu hồi grant.

Theo dõi grant lifecycle. Grant mồ côi có thể tích tụ và chạm quota.

---

# Phần IX — Cross-account access

## 50. Hai phía đều phải cho phép

Account A sở hữu key. Role ở account B muốn dùng key.

~~~text
Account A — key policy
   cho account/role B quyền dùng key

AND

Account B — IAM policy
   cho role quyền gọi KMS trên key ARN của A
~~~

Thiếu một phía thì không hoạt động.

## 51. Key policy ở account sở hữu key

~~~json
{
  "Effect": "Allow",
  "Principal": {
    "AWS": "arn:aws:iam::444455556666:root"
  },
  "Action": [
    "kms:Decrypt",
    "kms:DescribeKey"
  ],
  "Resource": "*"
}
~~~

Statement này cho external account khả năng delegate quyền cho principal trong account đó. Có thể chỉ định role ARN cụ thể để hẹp hơn.

## 52. IAM policy ở account gọi

~~~json
{
  "Effect": "Allow",
  "Action": [
    "kms:Decrypt",
    "kms:DescribeKey"
  ],
  "Resource": "arn:aws:kms:ap-southeast-1:111122223333:key/1234abcd-12ab-34cd-56ef-1234567890ab"
}
~~~

Cross-account permission chỉ có hiệu lực cho một số operation như cryptographic operations, grant và describe-related operations. External principal không thể quản trị toàn bộ key chỉ vì key policy ghi một management action không hỗ trợ cross-account.

## 53. Chia sẻ encrypted resource

Chia sẻ ciphertext thường cần chia sẻ cả:

- Resource/data access.
- KMS key use.

Ví dụ EBS snapshot mã hóa:

1. Share snapshot cho account B.
2. Cho account B dùng customer managed key.
3. Account B có IAM permission phù hợp.
4. Nên copy snapshot và re-encrypt bằng key account B kiểm soát.

Snapshot mã hóa bằng AWS managed key mặc định thường không chia sẻ cross-account như customer managed key.

## 54. Vì sao nên re-encrypt về key của account nhận?

Nếu account B tiếp tục phụ thuộc key của account A:

- A disable/delete key thì B mất khả năng decrypt.
- A đổi policy thì workload B có thể dừng.
- Incident boundary khó quản lý.

Copy và re-encrypt giúp account B sở hữu lifecycle của bản dữ liệu đã nhận.

## 55. Audit cross-account

Cross-account KMS operation được ghi CloudTrail ở account caller và account sở hữu key theo hành vi dịch vụ. Dùng event để xác định:

- Ai gọi.
- Key nào.
- Region nào.
- Via service nào.
- Encryption context gì, nếu có.

---

# Phần X — Regional và multi-Region keys

## 56. KMS key mặc định là Regional resource

Key ARN có Region. Một single-Region key ở Singapore không thể được gọi như một local key ở Tokyo.

Copy encrypted data sang Region khác thường cần:

- Decrypt bằng key/source integration ở Region nguồn.
- Encrypt bản đích bằng key ở Region đích.

AWS service có thể thực hiện quy trình này thay bạn khi copy snapshot/object và permission đúng.

## 57. Multi-Region key là gì?

Multi-Region key là tập related KMS keys ở nhiều Region có:

- Cùng key ID bắt đầu bằng `mrk-`.
- Cùng key material.
- Một số shared properties.

Bạn tạo primary key rồi chủ động replicate sang Region chọn trước.

## 58. Multi-Region key không phải global key

Mỗi Region có một KMS key resource riêng:

- ARN riêng vì Region khác.
- Key policy riêng.
- Alias riêng.
- Grant riêng.
- Enabled/disabled state riêng.
- Tag riêng.

AWS không tự tạo replica ở mọi Region. Bạn phải quản lý từng replica.

## 59. Shared và independent properties

Shared properties gồm key ID, key material và một số cryptographic properties. Các thuộc tính quản trị như policy, grants, alias, tag và state không tự đồng bộ.

~~~text
Primary ap-southeast-1
  ├── same key material ── Replica ap-northeast-1
  └── same key ID       ── Replica eu-west-1

Policy/alias/tag/state: quản lý riêng từng Region
~~~

## 60. Khi nào multi-Region key hữu ích?

- Client-side encryption cần mã hóa ở Region A, giải mã local ở Region B mà không re-encrypt ciphertext.
- Global application cần cùng cryptographic identity.
- Digital signature cần cùng public key across Regions.
- Disaster recovery cần local KMS operation với compatible ciphertext.

## 61. Không mặc định dùng multi-Region key

Single-Region key tạo isolation boundary rõ ràng. Nhiều AWS services vẫn xem related multi-Region keys như các key độc lập và có thể decrypt/re-encrypt trong replication workflow.

Ví dụ S3 Cross-Region Replication vẫn xử lý data key và destination encryption theo service workflow, ngay cả khi hai key là related multi-Region keys.

Chỉ chọn multi-Region khi requirement thực sự cần interoperable ciphertext/key identity giữa Region.

## 62. Rotation multi-Region key

Automatic/on-demand rotation được khởi tạo trên primary. KMS đồng bộ key material mới tới replica liên quan.

Policy và alias vẫn không tự đồng bộ. Monitoring phải kiểm tra từng Region.

## 63. Xóa multi-Region key

Replica có thể schedule deletion riêng. Primary không thể bị xóa hoàn tất cho tới khi mọi replica đã bị xóa. Điều này bảo vệ relationship nhưng làm runbook deletion phức tạp hơn.

---

# Phần XI — Rotation và key lifecycle

## 64. Rotation thay đổi điều gì?

Automatic/on-demand rotation tạo key material mới bên trong cùng KMS key.

Không thay đổi:

- Key ID.
- Key ARN.
- Alias.
- Key policy.
- Grants.
- Application reference.

KMS giữ key material cũ để decrypt ciphertext cũ.

## 65. Rotation không tự re-encrypt dữ liệu cũ

Sau rotation:

~~~text
Ciphertext cũ → decrypt bằng old key material retained by KMS
Ciphertext mới → encrypt bằng current key material
~~~

Nếu requirement bắt buộc ciphertext cũ được mã hóa lại dưới material/key mới, phải có migration/re-encryption workflow riêng.

## 66. Automatic rotation

Với customer managed symmetric encryption key có key material do KMS tạo, có thể bật automatic rotation.

Theo tài liệu hiện hành:

- Rotation period có thể từ 90 đến 2.560 ngày.
- Nếu không đặt custom period, mặc định 365 ngày.
- Có thể bật hoặc tắt theo permission.

AWS managed keys tự động rotate hằng năm; bạn không điều chỉnh lịch.

## 67. On-demand rotation

Authorized principal có thể gọi rotation ngay mà không đổi automatic schedule.

Giới hạn hiện hành:

- Tối đa 25 lần on-demand rotation cho mỗi KMS key.
- Hỗ trợ symmetric encryption key phù hợp.
- Không hỗ trợ asymmetric, HMAC hoặc key trong custom key store.
- Với multi-Region set, thực hiện trên primary.

On-demand rotation không phải nút “xóa ảnh hưởng của key compromise”. Key material cũ vẫn được giữ để decrypt ciphertext cũ.

## 68. Manual rotation

Manual rotation nghĩa là:

1. Tạo KMS key mới.
2. Cập nhật alias/application/service config sang key mới.
3. Dùng key mới cho write mới.
4. Giữ key cũ để read dữ liệu cũ.
5. Re-encrypt dữ liệu nếu requirement yêu cầu.
6. Chỉ retire key cũ sau inventory và verification đầy đủ.

Manual rotation áp dụng khi automatic rotation không hỗ trợ hoặc cần đổi policy/key spec/account boundary.

## 69. Disable key

Disable là reversible nhưng có impact lớn:

- Cryptographic operation dùng key sẽ thất bại.
- Service phụ thuộc key có thể không đọc/ghi dữ liệu.
- Existing running resource có thể biểu hiện khác nhau tùy data key cache và service integration.

Disable hữu ích để:

- Cô lập key trong incident.
- Kiểm tra dependency trước deletion.
- Tạm revoke usage.

Không disable production key mà không có inventory, approval và rollback plan.

## 70. Schedule key deletion

Customer managed key deletion có waiting period từ 7 đến 30 ngày; mặc định 30 ngày.

Trong thời gian chờ:

- Key ở state `PendingDeletion`.
- Không dùng cho cryptographic operations.
- Có thể cancel deletion trước deadline.
- Nên alarm nếu có attempt sử dụng key.

Sau khi deletion hoàn tất, AWS KMS không thể khôi phục key. Dữ liệu chỉ có ciphertext dưới key đó có thể mất vĩnh viễn.

## 71. Runbook xóa key an toàn

1. Xác định owner và data classification.
2. Inventory mọi resource/ciphertext phụ thuộc key.
3. Kiểm tra CloudTrail usage đủ dài.
4. Chuyển write mới sang key khác.
5. Re-encrypt/copy dữ liệu cần giữ.
6. Disable key trong observation period.
7. Tạo alarm cho failed decrypt/key usage.
8. Có approval từ security/data owner.
9. Schedule deletion với waiting period phù hợp.
10. Cancel ngay nếu phát hiện dependency.

Không dùng “không thấy request vài ngày” làm bằng chứng duy nhất; một backup chỉ restore mỗi quý vẫn có thể cần key.

## 72. Key states cần biết

Các state thường gặp:

- `Enabled`.
- `Disabled`.
- `PendingDeletion`.
- `PendingImport`.
- `Unavailable` trong một số custom key store scenario.
- State liên quan rotation/import/multi-Region tùy loại key.

Policy Allow không vượt qua key state không cho phép cryptographic operation.

---

# Phần XII — Key material origin và custom key store

## 73. Origin `AWS_KMS`

AWS KMS tạo key material trong HSM do dịch vụ quản lý. Đây là lựa chọn mặc định, đơn giản và có availability/operational model tốt nhất cho phần lớn workload.

## 74. Imported key material — origin `EXTERNAL`

Bạn tạo key material ngoài KMS rồi import vào KMS key.

Use case:

- Compliance yêu cầu nguồn key material do tổ chức tạo.
- Cần khả năng xóa/reimport material theo quy trình riêng.
- Cần kiểm soát lifecycle ngoài AWS.

Trade-off:

- Bạn phải giữ bản gốc an toàn.
- Material hết hạn/bị xóa làm key unusable.
- AWS không khôi phục material đã mất.
- Rotation support và procedure khác AWS-generated material.
- Tăng rủi ro availability và vận hành.

## 75. Expiration của imported material

Imported material có thể cấu hình expiration. Theo tài liệu hiện hành, thời điểm hết hạn có thể tối đa 365 ngày từ lúc import cho lần cấu hình đó.

Khi material hết hạn hoặc bị xóa, key có thể chuyển `PendingImport` và không dùng được cho cryptographic operations cho tới khi reimport đúng material cần thiết.

Nếu mất bản external material, dữ liệu có thể không phục hồi được.

## 76. AWS CloudHSM custom key store

CloudHSM key store đặt key material trong CloudHSM cluster do bạn sở hữu/quản lý.

Bạn nhận thêm control nhưng chịu trách nhiệm:

- HSM cluster availability.
- Backup và durability.
- User/credential của HSM.
- Capacity và latency.
- Cost CloudHSM.
- Kết nối custom key store.

Custom key store không mặc nhiên “an toàn hơn”; nó có security và responsibility model khác.

## 77. External key store — XKS

XKS giữ external key material trong key manager ngoài AWS. AWS KMS gọi qua XKS proxy do bạn/nhà cung cấp vận hành.

Phù hợp chỉ khi có requirement rõ ràng, không thể thay đổi, rằng key material phải nằm và được sử dụng ngoài AWS.

Trade-off lớn:

- Latency cao hơn.
- Throughput thấp hơn.
- Availability phụ thuộc network/proxy/external manager.
- Bạn chịu trách nhiệm durability và capacity bên ngoài.
- Không có cùng service availability model như standard KMS key.

Không chọn XKS chỉ vì khẩu hiệu “hold your own key” nếu không có requirement và đội vận hành tương xứng.

## 78. Feature limitation của custom key store

Custom key store tập trung vào symmetric encryption key và không hỗ trợ nhiều feature như asymmetric/HMAC, automatic rotation, multi-Region hoặc imported material theo cùng cách standard store hỗ trợ.

Kiểm tra compatibility với service và quota trước khi thiết kế.

---

# Phần XIII — Tích hợp với Amazon S3

## 79. SSE-S3 và SSE-KMS

| SSE-S3 | SSE-KMS |
|---|---|
| S3 quản lý key | KMS key bảo vệ data key |
| Đơn giản | Policy/audit/control chi tiết hơn |
| Không cần KMS permission riêng | Cần S3 permission và KMS permission |
| Không có customer key lifecycle | Có thể dùng customer managed key |

Chọn SSE-KMS khi cần control key policy, cross-account design, disable, audit KMS usage hoặc compliance boundary.

## 80. S3 SSE-KMS envelope flow

### PUT object

~~~text
Client ── PutObject ──► S3
                        │ GenerateDataKey
                        ▼
                       KMS
                        │ plaintext + encrypted data key
                        ▼
S3 mã hóa object bằng data key
S3 lưu ciphertext object + encrypted data key
S3 xóa plaintext data key khỏi memory sớm nhất có thể
~~~

### GET object

~~~text
Client ── GetObject ──► S3
                        │ Decrypt encrypted data key
                        ▼
                       KMS
                        │ plaintext data key
                        ▼
S3 giải mã object và trả dữ liệu qua TLS
~~~

## 81. Permission cho S3 SSE-KMS

Thông thường:

- Upload cần `s3:PutObject` và `kms:GenerateDataKey`.
- Download cần `s3:GetObject` và `kms:Decrypt`.
- Multipart upload có thể cần cả `kms:GenerateDataKey` và `kms:Decrypt`.

Ngoài IAM, key policy phải cho phép hoặc cho account delegate permission.

## 82. KMS key phải cùng Region với S3 bucket

SSE-KMS key dùng cho bucket/object phải ở cùng Region với bucket. Cross-Region Replication dùng destination key tại destination Region theo cấu hình replication.

## 83. S3 Bucket Key

S3 Bucket Key giảm số direct KMS request bằng cách dùng bucket-level key hierarchy trong một khoảng thời gian, từ đó có thể giảm KMS request cost cho workload nhiều object.

Trade-off:

- Encryption context mặc định chuyển từ object ARN sang bucket ARN.
- CloudTrail KMS event granularity khác vì không phải mỗi object access đều tạo KMS request trực tiếp.
- Policy dựa trên encryption context phải được kiểm tra.

Bucket Key không thay đổi việc object vẫn được SSE-KMS bảo vệ.

## 84. Cross-account S3 SSE-KMS

Account B đọc object ở account A cần:

- Bucket policy/IAM cho `s3:GetObject`.
- Customer managed key policy ở A cho phép B/role B.
- IAM policy của role B cho `kms:Decrypt`.
- Không bị SCP/endpoint policy chặn.

AWS managed key `aws/s3` không linh hoạt cho pattern cross-account như customer managed key.

## 85. Đổi key cho object S3

Thay bucket default encryption không tự re-encrypt object cũ. Nó ảnh hưởng write mới theo rule.

Muốn đổi key cho object cũ cần:

- Copy object lên chính nó với encryption mới; hoặc
- Dùng S3 Batch Operations; hoặc
- Migration workflow phù hợp.

Luôn xem xét versioning, metadata, ETag, Object Lock và replication khi rewrite object.

---

# Phần XIV — EBS, EC2, AMI và snapshots

## 86. EBS encryption

EBS dùng KMS và envelope encryption để bảo vệ:

- Data at rest trong volume.
- Snapshot.
- Data di chuyển giữa volume và EC2 instance theo service design.

Encryption/decryption diễn ra trong EBS/EC2 infrastructure; application trong instance không cần tự gọi KMS cho từng block I/O.

## 87. EBS encryption by default

Có thể bật encryption by default theo account và Region. Khi bật, volume/snapshot copy mới theo rule sẽ được mã hóa bằng default EBS KMS key nếu không chỉ định key khác.

Default có thể là AWS managed key `aws/ebs` hoặc customer managed key được cấu hình.

## 88. Không đổi key trực tiếp trên existing EBS volume

Không thể gán KMS key khác trực tiếp cho volume/snapshot hiện có.

Pattern:

1. Tạo snapshot.
2. Copy snapshot và chọn KMS key mới.
3. Tạo volume từ encrypted snapshot copy.
4. Thay volume theo runbook.

Encrypted volume/snapshot không thể trở thành unencrypted copy.

## 89. Share encrypted snapshot

Không thể share encrypted snapshot dùng default AWS managed EBS key theo pattern customer-managed sharing. Dùng customer managed key và chia sẻ cả snapshot lẫn quyền key.

Account nhận nên copy/re-encrypt snapshot bằng key của mình để tránh phụ thuộc source account.

## 90. Grants trong EBS

EC2/EBS sử dụng grant để có quyền dùng key cho volume/resource. Key user thường cần quyền tạo grant với condition dành cho AWS resource.

Nếu key policy quá hẹp và bỏ quyền grant cần thiết, tạo/attach encrypted volume có thể thất bại dù role có `kms:Decrypt`.

## 91. Disable key và EBS impact

Disable key có thể ngăn các operation cần tạo/decrypt data key, như tạo volume từ snapshot, attach/restart trong một số luồng hoặc snapshot operation.

Đừng dùng việc một instance hiện tại vẫn chạy làm bằng chứng key không cần thiết; data key có thể đang được service sử dụng/cached cho resource đang hoạt động.

---

# Phần XV — RDS, Aurora, snapshots và backup

## 92. RDS encryption at rest

RDS/Aurora encryption bảo vệ storage, automated backups, read replicas và snapshots liên quan theo khả năng dịch vụ.

Chọn KMS key khi tạo encrypted database/cluster. Nhiều trường hợp không thể bật encryption trực tiếp cho database unencrypted đang tồn tại.

Pattern migration thường là:

1. Tạo snapshot.
2. Copy snapshot với encryption/KMS key.
3. Restore database mới từ encrypted snapshot.
4. Migrate/cut over.

## 93. Không đổi key trực tiếp cho database hiện có

Muốn chuyển sang key khác thường phải snapshot/copy/restore hoặc dùng cơ chế migration của engine/service. Rotation key material trong cùng KMS key không đổi key ARN của database.

## 94. Cross-Region/cross-account snapshot

Cần xem đồng thời:

- Snapshot sharing/copy permission.
- Source key permission.
- Destination key ở Region/account đích.
- Service role/grant.
- AWS managed key limitation.

Re-encrypt copy bằng destination-owned customer managed key giúp DR account tự chủ hơn.

## 95. Key availability là database availability dependency

Nếu key bị disable/deleted hoặc policy/grant bị phá vỡ, database và backup restore có thể mất khả năng hoạt động theo service behavior.

Key policy change phải được xem như production change, có review, test và rollback.

---

# Phần XVI — Lambda, Secrets Manager, SQS và logs

## 96. Lambda environment variables

Lambda mã hóa environment variables at rest. Có thể dùng customer managed KMS key để kiểm soát key policy và audit.

Phân biệt:

- Lambda service cần dùng key để quản lý encrypted configuration.
- Execution role có thể cần decrypt tùy workflow/client-side helper và cấu hình.
- Secret không nên đặt trong environment variable nếu cần rotation/lifecycle mạnh; dùng Secrets Manager runtime.

## 97. Secrets Manager

Secrets Manager dùng KMS để mã hóa secret at rest.

Application cần:

- `secretsmanager:GetSecretValue` trên secret.
- Quyền `kms:Decrypt` nếu dùng customer managed key và policy yêu cầu.

Rotation của secret và rotation của KMS key là hai việc khác nhau:

~~~text
Secret rotation → đổi password/token value
KMS rotation    → đổi key material bảo vệ secret ciphertext
~~~

## 98. Systems Manager Parameter Store

`SecureString` dùng KMS. Standard `SecureString` và Advanced tier có workflow encryption khác nhau theo tài liệu dịch vụ, nhưng caller vẫn cần SSM permission và KMS permission phù hợp.

Không cho role rộng `kms:Decrypt` trên mọi key chỉ để đọc một parameter.

## 99. SQS SSE-KMS

SQS có thể dùng KMS để mã hóa message body at rest.

Producer/service gửi message có thể cần quyền tạo data key; consumer cần decrypt. Queue policy, IAM policy và KMS key policy phải khớp.

SSE không mã hóa mọi metadata như queue name, message ID, timestamp hoặc metric.

## 100. CloudWatch Logs và log groups

CloudWatch Logs có thể associate customer managed KMS key để mã hóa log data. Key policy phải cho logs service principal đúng Region và có thể giới hạn bằng encryption context/log group ARN.

Nếu policy sai hoặc key bị disable, log delivery/query có thể bị ảnh hưởng. Không schedule deletion key khi retention log vẫn còn cần truy cập.

---

# Phần XVII — CloudTrail, monitoring và audit

## 101. CloudTrail ghi gì?

KMS tích hợp CloudTrail để ghi:

- Management operations: create, enable, disable, policy change, rotation, deletion.
- Cryptographic operations như Encrypt, Decrypt, GenerateDataKey tùy event category/behavior.
- Principal, time, Region, key ARN và request context.

CloudTrail giúp điều tra ai đã dùng hoặc thay đổi key; nó không lưu plaintext data/key trong log.

## 102. Encryption context trong CloudTrail

Encryption context có thể xuất hiện plaintext trong CloudTrail. Vì vậy không đặt secret/PII nhạy cảm trong context.

Mặt tích cực: context giúp audit key được dùng cho tenant/resource/purpose nào.

## 103. Event nhạy cảm cần alert

- `DisableKey`.
- `ScheduleKeyDeletion`.
- `PutKeyPolicy`.
- `CreateGrant` bất thường.
- `RevokeGrant` hoặc `RetireGrant` quan trọng.
- `DeleteImportedKeyMaterial`.
- `ImportKeyMaterial`.
- `ReplicateKey`/thay đổi primary Region.
- Rotation configuration change.
- Decrypt spike hoặc AccessDenied spike theo monitoring pipeline.

## 104. Không dùng CloudTrail history ngắn hạn làm dependency inventory duy nhất

Một key có thể bảo vệ:

- Backup chỉ restore khi disaster.
- Archive truy cập mỗi năm.
- AMI hiếm khi launch.
- Snapshot giữ theo compliance.

Usage log không xuất hiện gần đây không có nghĩa key có thể xóa.

## 105. CloudWatch metrics và alarms

Theo dõi:

- KMS request/throttle/error qua service/application metrics.
- Custom key store connection/latency/throttle.
- CloudTrail event alarm cho destructive changes.
- Service-specific decrypt/access failures.

KMS standard key không cung cấp một dashboard tự động biết tất cả ciphertext phụ thuộc key. Cần asset inventory/tag/configuration management riêng.

---

# Phần XVIII — Performance, quota và cost

## 106. KMS nằm trên request path

Mỗi direct KMS call thêm:

- Network latency.
- Request quota consumption.
- Chi phí theo pricing hiện hành.
- Failure dependency.

Envelope encryption và service-side caching giảm việc gọi KMS cho từng byte/block.

## 107. Request quota là shared quota

Nhiều cryptographic operations trên cùng key type dùng shared request quota theo account và Region.

Ví dụ khái niệm:

~~~text
GenerateDataKey rate
+ Decrypt rate
+ Encrypt rate
= shared symmetric cryptographic request usage
~~~

Con số quota khác theo Region/key type và có thể thay đổi. Kiểm tra Service Quotas thay vì học thuộc một TPS duy nhất.

## 108. Ai tiêu thụ quota khi AWS service dùng key?

KMS request do AWS service thực hiện thay mặt caller có thể tính vào quota của account theo quy tắc KMS. Ví dụ S3 SSE-KMS upload/download tạo `GenerateDataKey`/`Decrypt` request nếu không được giảm bằng Bucket Key/cache.

Traffic S3 tăng đột biến có thể gây KMS throttling dù application không gọi KMS SDK trực tiếp.

## 109. Xử lý throttling

- Exponential backoff với jitter.
- Dùng AWS SDK retry behavior phù hợp.
- S3 Bucket Keys cho workload S3 phù hợp.
- Data key caching có security limit.
- Phân phối workload/keys không phải cách thay thế quota planning mù quáng.
- Request quota increase khi có justification.
- Load test cryptographic path.

Không retry tight loop vì sẽ làm throttling nặng hơn.

## 110. Resource quotas đáng biết

Theo tài liệu hiện hành, default resource quota theo account và Region gồm các giá trị như:

- 100.000 customer managed KMS keys.
- 50 aliases cho mỗi key.
- 50.000 grants cho mỗi customer managed key.
- 10 custom key stores.
- 25 on-demand rotations cho mỗi key; quota này không adjustable.

Các quota có thể thay đổi hoặc được tăng, trừ giới hạn được ghi không adjustable. Luôn kiểm tra Service Quotas/tài liệu trước thiết kế production.

## 111. Cost model

Customer managed key thường có:

- Phí lưu key theo tháng.
- Phí API request.
- Chi phí thêm nếu dùng CloudHSM/XKS infrastructure.

AWS owned/AWS managed key có cost model khác tùy service và KMS requests. Không học thuộc giá dollar trong tài liệu kiến trúc; dùng trang pricing hiện hành.

## 112. Đừng tạo một key cho mỗi object

Key-per-object thường tạo:

- Quá nhiều key.
- Policy và lifecycle không quản nổi.
- Chi phí và quota lớn.
- Deletion/rotation inventory phức tạp.

Envelope encryption đã cho phép mỗi object có data key riêng dưới một KMS key dùng chung theo security boundary hợp lý.

## 113. Cũng đừng dùng một key cho mọi thứ

Một key toàn doanh nghiệp làm blast radius lớn:

- Policy rộng.
- Disable ảnh hưởng mọi workload.
- Audit khó phân loại.
- Cross-account phức tạp.
- Quota/cost attribution khó.

Chia key theo environment, data classification, application/account và lifecycle boundary; tránh cả hai cực đoan.

---

# Phần XIX — Network và private connectivity

## 114. KMS endpoint

KMS là regional AWS service endpoint. Workload trong VPC gọi KMS qua:

- Public regional endpoint qua NAT/egress phù hợp; hoặc
- Interface VPC endpoint powered by AWS PrivateLink.

## 115. Interface VPC endpoint

VPC endpoint giúp:

- Giữ traffic trên AWS private connectivity.
- Giảm phụ thuộc NAT cho KMS access.
- Áp endpoint policy.
- Dùng private DNS để SDK gọi hostname chuẩn qua endpoint.

Cần cấu hình:

- Endpoint subnet/AZ.
- Security group cho HTTPS.
- Private DNS.
- Endpoint policy.
- Application DNS/network path.

## 116. Endpoint policy không cấp quyền KMS

Endpoint policy là lớp giới hạn bổ sung, không thay key policy hoặc IAM policy.

~~~text
IAM/key policy Allow
AND endpoint policy cho phép path
AND không có explicit deny
~~~

Endpoint policy quá hẹp có thể gây AccessDenied chỉ khi request đi qua endpoint, trong khi request từ nơi khác hoạt động.

## 117. FIPS endpoints

KMS có FIPS endpoint ở Region hỗ trợ cho requirement sử dụng cryptographic module/network endpoint theo compliance. Kiểm tra Region, SDK và service documentation hiện hành.

Không đồng nhất “dùng KMS” với “mọi compliance requirement tự động được đáp ứng”; architecture và operational controls vẫn phải được đánh giá.

---

# Phần XX — Thiết kế key hierarchy và governance

## 118. Key scope theo environment

Production và non-production nên có key/account boundary riêng.

~~~text
prod-orders-key
staging-orders-key
dev-orders-key
~~~

Không cho developer role ở dev có decrypt permission trên production key.

## 119. Key scope theo data classification

Ví dụ:

- Public/internal data: service-managed encryption có thể đủ.
- Confidential business data: customer managed key theo workload.
- Regulated PII/payment data: key riêng, policy/context/audit chặt hơn.

Phân loại dữ liệu trước, chọn key sau. Không tạo key topology ngẫu nhiên theo từng ticket.

## 120. Centralized hay decentralized keys?

### Centralized security account

Ưu điểm:

- Governance và policy tập trung.
- Audit tập trung.
- Security team kiểm soát lifecycle.

Trade-off:

- Cross-account policy phức tạp.
- Dependency vào security account/key.
- Blast radius nếu policy sai.
- Một số service workflow không phù hợp external-account key.

### Key trong workload account

Ưu điểm:

- Ownership gần resource.
- Service integration đơn giản hơn.
- Failure boundary theo account.

Trade-off:

- Cần guardrail để policy/rotation/tag nhất quán.
- Nhiều team quản lý key.

Thực tế thường dùng workload-owned keys với organization guardrails, hoặc centralized key cho use case được kiểm chứng.

## 121. Separation of duties

Tách ít nhất:

- Key administrator.
- Key user/application role.
- Security auditor.
- Break-glass role.

Key admin quản lý key nhưng không mặc nhiên đọc data. Data admin đọc storage nhưng không mặc nhiên decrypt. Auditor xem config/log nhưng không sử dụng key.

## 122. Tagging

Tag gợi ý:

- `Owner`.
- `Application`.
- `Environment`.
- `DataClassification`.
- `CostCenter`.
- `DeletionApprovalGroup`.
- `RecoveryTier`.

Tag có thể dùng cho ABAC nhưng quyền tag/untag trở thành security-sensitive nếu policy dựa trên tag.

## 123. Infrastructure as Code

Quản lý key/policy/alias bằng IaC giúp review và drift detection. Nhưng deletion behavior của KMS key cần thiết kế cẩn thận:

- Stack deletion có schedule key deletion không?
- Retain policy có được bật không?
- Alias và key lifecycle có tách khỏi workload stack ngắn hạn không?
- Replacement có làm dữ liệu cũ phụ thuộc key orphan không?

Key thường nên có lifecycle dài hơn compute stack.

## 124. Guardrails

Có thể dùng:

- SCP hạn chế schedule deletion/disable ngoài admin role.
- AWS Config/custom rule kiểm tra rotation/tag/policy.
- CloudTrail + EventBridge alert thay đổi nhạy cảm.
- IAM Access Analyzer cho resource policy phù hợp.
- Approval workflow cho key deletion.
- Backup/restore test dùng encrypted data.

---

# Phần XXI — Những hiểu nhầm phổ biến

## 125. “Có KMS encryption thì data owner cũng không đọc được”

Sai. Nếu data owner role có cả data access và `kms:Decrypt`, họ đọc được. Encryption không thay authorization.

## 126. “Rotation làm ciphertext cũ dùng key mới”

Sai. Automatic rotation đổi current key material cho encryption mới; material cũ được giữ để decrypt ciphertext cũ. Không có mass re-encryption tự động.

## 127. “Alias là key”

Sai. Alias chỉ là con trỏ có thể đổi target. Ciphertext và resource vẫn liên quan key identity thực tế.

## 128. “IAM Allow là đủ”

Không luôn đúng. Key policy phải trực tiếp allow hoặc enable IAM delegation; còn có grants, SCP, boundary, state và endpoint policy.

## 129. “Admin key luôn decrypt được”

Không mặc nhiên. Management permission và cryptographic usage permission tách nhau. Tuy nhiên, admin có quyền sửa policy có thể tự cấp quyền, nên vẫn là privileged role.

## 130. “Multi-Region key được tự động có ở mọi Region”

Sai. Bạn tạo replica ở Region cần thiết và quản lý policy/alias/grant/state riêng.

## 131. “Customer managed key luôn tốt nhất”

Không. Nó tốt khi cần control tương ứng. Nếu không, chi phí và nguy cơ disable/delete/policy sai có thể lớn hơn lợi ích.

## 132. “Xóa encrypted data là đủ, key không quan trọng nữa”

Có thể còn snapshot, backup, replica, log hoặc archive dùng key. Phải inventory toàn bộ dependency.

## 133. “KMS mã hóa file lớn trực tiếp”

Sai. Direct symmetric `Encrypt` chỉ nhận tối đa 4 KiB. Dữ liệu lớn dùng envelope encryption.

## 134. “Encryption context là secret”

Sai. Context không mã hóa và có thể xuất hiện trong CloudTrail.

---

# Phần XXII — Troubleshooting

## 135. `AccessDeniedException` khi decrypt

Kiểm tra theo thứ tự:

1. Caller identity thật sự là role/session nào?
2. Key ARN và Region đúng không?
3. Key state có `Enabled` không?
4. Key policy cho principal/account chưa?
5. IAM policy có đúng action/resource chưa?
6. Có SCP, permissions boundary hoặc session policy deny không?
7. Encryption context có khớp không?
8. `kms:ViaService` có đúng service/Region không?
9. VPC endpoint policy có chặn không?
10. Nếu qua service, service resource policy/data permission có đúng không?

## 136. `InvalidCiphertextException`

Nguyên nhân có thể:

- Encryption context sai hoặc thiếu.
- Ciphertext bị hỏng/truncate.
- Dùng sai algorithm với asymmetric key.
- Dùng sai key/ciphertext format.
- Base64 encode/decode sai khi truyền qua JSON/storage.

Với symmetric KMS ciphertext blob, metadata thường cho KMS biết key nào; đừng sửa/parse ciphertext blob thủ công.

## 137. S3 GetObject bị AccessDenied

Kiểm tra hai nhánh:

~~~text
S3 branch
- IAM s3:GetObject
- bucket policy
- object ownership/access point
- VPC endpoint policy

KMS branch
- kms:Decrypt
- key policy
- encryption context/ViaService
- key state/Region
~~~

CloudTrail giúp xác định request fail ở S3 hay KMS.

## 138. Encrypted EBS volume không attach được

Kiểm tra:

- Instance/service role và caller permission.
- Key policy.
- Grant creation permission.
- `kms:GrantIsForAWSResource` condition.
- Key enabled không.
- Snapshot/key cross-account sharing.
- Key có đúng Region không.

## 139. Application bị KMS throttling

Kiểm tra:

- Request rate theo account/Region/key type.
- S3/EBS/service traffic có tạo KMS call thay mặt application không.
- Retry có backoff/jitter không.
- Có thể dùng S3 Bucket Key/data key caching không.
- Batch/architecture có gọi KMS cho từng record nhỏ không.
- Service Quotas và quota increase.

## 140. Key policy làm mất quyền quản trị

Key policy sai có thể khóa administrator khỏi key. Khi chỉnh policy:

- Luôn giữ đường quản trị được phê duyệt.
- Dùng policy safety check của KMS.
- Review bằng IaC/code review.
- Không xóa account delegation/admin statement mù quáng.
- Có break-glass procedure.

## 141. Key pending deletion nhưng phát hiện dependency

Nếu còn trong waiting period:

1. Gọi cancel key deletion.
2. Enable key nếu cần.
3. Khôi phục policy/grants.
4. Xác minh resource hoạt động.
5. Điều tra inventory/runbook đã bỏ sót gì.

Sau khi deletion hoàn tất, KMS không khôi phục key.

---

# Phần XXIII — Bẫy đề thi SAA-C03

## 142. S3 permission không đủ cho SSE-KMS

Đề cho role có `s3:GetObject` nhưng vẫn lỗi → kiểm tra `kms:Decrypt`, key policy và key state.

## 143. Cross-account cần cả hai policy

Key policy ở account sở hữu cho phép + IAM policy ở account caller cho phép.

## 144. Chia sẻ EBS snapshot mã hóa

Dùng customer managed key, share snapshot và key permission; account nhận nên copy/re-encrypt bằng key của mình. Snapshot dùng default AWS managed EBS key không đáp ứng pattern sharing đó.

## 145. Regional key và copy snapshot

Copy sang Region khác dùng destination KMS key ở Region đích. KMS key là Regional trừ thiết kế multi-Region rõ ràng.

## 146. Rotation không đổi ARN

Automatic rotation trong cùng key không yêu cầu đổi application reference và không re-encrypt data cũ.

## 147. Xóa key là destructive

Waiting period 7–30 ngày; hết hạn thì ciphertext phụ thuộc key có thể không bao giờ giải mã được.

## 148. Envelope encryption

Data key mã hóa data; KMS key mã hóa data key. Đây thường là đáp án khi dữ liệu lớn.

## 149. Encryption context

Là AAD, phải khớp khi decrypt, có thể dùng trong policy và audit, nhưng không phải secret.

## 150. CloudHSM vs KMS

- KMS: managed key service, tích hợp AWS rộng, vận hành đơn giản.
- CloudHSM/custom key store: customer quản HSM và availability/capacity, dùng khi có control/compliance requirement đặc thù.

Không chọn CloudHSM chỉ vì “dedicated HSM” nếu đề ưu tiên managed, highly available và low operational overhead.

## 151. Secrets Manager vs KMS

Secrets Manager lưu và rotate secret. KMS cung cấp cryptographic key để mã hóa secret. Hai dịch vụ bổ sung cho nhau.

---

# Phần XXIV — Bài lab thực hành

## 152. Lab 1 — Direct encrypt/decrypt với encryption context

### Mục tiêu

Hiểu ciphertext blob, context và permission.

### Các bước

1. Tạo customer managed symmetric key.
2. Tạo alias `alias/saa-kms-lab`.
3. Encrypt một chuỗi nhỏ với context `purpose=lab`.
4. Decrypt với đúng context.
5. Thử decrypt không context và context sai.
6. Kiểm tra CloudTrail event.
7. Xác nhận context xuất hiện plaintext trong audit log.

### Cần giải thích được

- Vì sao context sai làm decrypt thất bại?
- Vì sao không đặt secret vào context?
- Vì sao direct Encrypt không dùng cho file lớn?

## 153. Lab 2 — Envelope encryption

1. Gọi `GenerateDataKey`.
2. Dùng plaintext data key qua library chuẩn để mã hóa file test.
3. Lưu ciphertext file và encrypted data key.
4. Xóa plaintext data key khỏi memory/process.
5. Gọi KMS Decrypt trên encrypted data key.
6. Giải mã file.
7. Thử disable KMS key và lặp lại.

Không triển khai crypto custom vào production chỉ từ lab này; mục tiêu là hiểu flow.

## 154. Lab 3 — Key policy và IAM policy

Tạo ba role:

- `KmsAdminRole` quản lý key nhưng không có decrypt.
- `AppEncryptRole` chỉ encrypt/generate data key.
- `AppDecryptRole` chỉ decrypt với context phù hợp.

Thử từng operation và ghi lại kết quả. Sau đó thêm một SCP/permissions boundary giả lập nếu môi trường lab cho phép để quan sát explicit deny.

## 155. Lab 4 — S3 SSE-KMS

1. Tạo bucket và customer managed key cùng Region.
2. Bật default SSE-KMS.
3. Upload object bằng role có `PutObject` nhưng thiếu `GenerateDataKey`.
4. Quan sát lỗi.
5. Cấp quyền tối thiểu và upload lại.
6. Đọc bằng role có `GetObject` nhưng thiếu `Decrypt`.
7. Quan sát CloudTrail.
8. Bật S3 Bucket Key và so sánh KMS request/context behavior.

## 156. Lab 5 — EBS encrypted snapshot copy

1. Tạo encrypted EBS volume bằng Key A.
2. Tạo snapshot.
3. Copy snapshot và chọn Key B.
4. Tạo volume từ bản copy.
5. Xác nhận volume mới dùng Key B.
6. Không xóa key trong lab; chỉ thử disable/re-enable nếu resource không chứa dữ liệu quan trọng.

## 157. Lab 6 — Rotation

1. Bật automatic rotation với period phù hợp môi trường lab/IaC review.
2. Xem `GetKeyRotationStatus`.
3. Thực hiện on-demand rotation nếu account cho phép.
4. Encrypt dữ liệu trước và sau rotation.
5. Xác nhận ciphertext cũ vẫn decrypt bằng cùng key ARN.
6. Giải thích vì sao dữ liệu cũ chưa được re-encrypt.

## 158. Lab 7 — Cross-account policy review

Nếu có hai account lab:

1. Account A tạo key.
2. Key policy cho role/account B quyền decrypt.
3. Account B thêm IAM permission tới key ARN A.
4. Thử khi thiếu từng phía.
5. Kiểm tra CloudTrail ở cả hai account.

Nếu không có hai account, viết policy và dùng IAM policy simulator/review thủ công; không mở quyền public để thử.

## 159. Lab 8 — Deletion safety drill

Không dùng production key.

1. Tạo disposable test key và ciphertext test.
2. Schedule deletion với waiting period tối thiểu cho phép.
3. Quan sát state và decrypt failure.
4. Tạo alert CloudTrail.
5. Cancel deletion.
6. Enable lại key.
7. Xác nhận decrypt hoạt động.

Mục tiêu là luyện rollback trước khi thao tác trên key thật.

---

# Phần XXV — Câu hỏi tự kiểm tra

## 160. Câu 1

Một role có `s3:GetObject` nhưng không đọc được object SSE-KMS. Nguyên nhân có khả năng nhất?

**Đáp án:** Thiếu `kms:Decrypt`, key policy không cho phép, key bị disable hoặc condition/context không khớp. Phải kiểm tra cả nhánh S3 và KMS.

## 161. Câu 2

Một file 2 GB cần client-side encryption bằng KMS. Có nên gọi `kms:Encrypt` trực tiếp với file không?

**Đáp án:** Không. Dùng envelope encryption/AWS Encryption SDK: data key mã hóa file, KMS key bảo vệ data key.

## 162. Câu 3

Automatic rotation có đổi key ARN không?

**Đáp án:** Không. Key ID, ARN, alias và policy giữ nguyên; current key material thay đổi, material cũ được giữ để decrypt.

## 163. Câu 4

Sau rotation, toàn bộ object S3 cũ có được mã hóa lại tự động không?

**Đáp án:** Không. Object cũ vẫn dùng encrypted data key/ciphertext cũ; KMS giữ material cũ. Muốn re-encrypt phải rewrite/copy object.

## 164. Câu 5

Account B muốn dùng key ở account A. Chỉ thêm IAM Allow trong B có đủ không?

**Đáp án:** Không. Key policy ở A cũng phải cho phép account/role B. Cần cả hai phía và không có explicit deny.

## 165. Câu 6

Key administrator có cần `kms:Decrypt` không?

**Đáp án:** Không nhất thiết. Nên tách management và usage. Tuy nhiên quyền sửa key policy là đặc quyền cao vì admin có thể thay đổi quyền.

## 166. Câu 7

Encryption context có thể chứa password không?

**Đáp án:** Không. Context không được mã hóa và có thể xuất hiện trong CloudTrail.

## 167. Câu 8

Tại sao account nhận shared encrypted EBS snapshot nên copy và re-encrypt?

**Đáp án:** Để sở hữu key/lifecycle của bản copy, tránh mất truy cập nếu source account đổi policy, disable hoặc xóa key.

## 168. Câu 9

Một key đã `PendingDeletion` có decrypt được không?

**Đáp án:** Không. Cancel deletion trong waiting period rồi enable/khôi phục policy nếu cần. Sau khi xóa hoàn tất không thể phục hồi key.

## 169. Câu 10

Multi-Region replica có tự dùng cùng key policy với primary không?

**Đáp án:** Không. Key material/ID là shared properties nhưng policy, alias, grants, tags và state được quản lý riêng từng Region.

## 170. Câu 11

Ứng dụng cần lưu database password và rotate định kỳ. Dùng KMS trực tiếp hay Secrets Manager?

**Đáp án:** Secrets Manager để lưu/lifecycle/rotation secret; secret được mã hóa bằng KMS. KMS không phải secret store thay thế.

## 171. Câu 12

S3 traffic tăng làm KMS throttling. Hai hướng tối ưu cần xem xét?

**Đáp án:** S3 Bucket Keys và quota/retry planning; ngoài ra xem data pattern, request rate, key policy và Service Quotas.

## 172. Câu 13

Role có IAM Allow `kms:Decrypt`, key policy không liệt kê role và cũng không enable account IAM delegation. Kết quả?

**Đáp án:** IAM Allow không đủ; request bị từ chối.

## 173. Câu 14

Muốn ký dữ liệu trong KMS nhưng bên thứ ba verify bên ngoài AWS. Chọn key nào?

**Đáp án:** Asymmetric KMS key với usage `SIGN_VERIFY`; private key ở KMS, public key có thể tải để verify.

## 174. Câu 15

HMAC có che giấu nội dung message không?

**Đáp án:** Không. HMAC bảo vệ integrity/authenticity, không cung cấp confidentiality.

## 175. Câu 16

Một compliance rule yêu cầu key material phải nằm ngoài AWS và tổ chức phải có khả năng cắt quyền AWS truy cập. Chọn gì?

**Đáp án:** Cân nhắc external key store chỉ khi requirement bắt buộc; chấp nhận latency, throughput, availability và operational responsibility lớn hơn.

---

# Phần XXVI — Checklist production

## 176. Thiết kế key

- [ ] Data đã được classification.
- [ ] Đã chọn AWS owned, AWS managed hay customer managed có lý do.
- [ ] Key spec/usage đúng requirement.
- [ ] Single-Region hay multi-Region được quyết định có chủ đích.
- [ ] Scope không quá rộng hoặc quá vụn.
- [ ] Alias/tag/owner rõ ràng.

## 177. Authorization

- [ ] Key policy giữ đường quản trị an toàn.
- [ ] Key admin và key user tách nhau.
- [ ] IAM policy giới hạn key ARN.
- [ ] Cross-account có permission cả hai phía.
- [ ] `ViaService`, encryption context hoặc grant constraint được dùng khi phù hợp.
- [ ] SCP, boundary và endpoint policy đã được kiểm tra.
- [ ] Không có wildcard decrypt không cần thiết.

## 178. Lifecycle

- [ ] Rotation strategy được xác định.
- [ ] Hiểu rotation không re-encrypt data cũ.
- [ ] Disable/deletion có approval và runbook.
- [ ] Inventory ciphertext/resource dependency.
- [ ] Backup/restore test gồm quyền KMS.
- [ ] Imported/custom material có recovery plan.

## 179. Monitoring

- [ ] CloudTrail được bật và lưu an toàn.
- [ ] Alert `DisableKey`, `ScheduleKeyDeletion`, `PutKeyPolicy`.
- [ ] Theo dõi grant/policy change.
- [ ] Theo dõi throttling và AccessDenied spike.
- [ ] Encryption context không chứa secret.
- [ ] Có owner xử lý alarm.

## 180. Service integrations

- [ ] S3/EBS/RDS resource và key cùng Region khi yêu cầu.
- [ ] Service role/grant permission đúng.
- [ ] Cross-account snapshot/object đã test.
- [ ] S3 Bucket Key được đánh giá về cost/context/audit.
- [ ] Key deletion không phá backup, logs hoặc DR.
- [ ] VPC endpoint policy không vô tình chặn service/caller.

---

# Phần XXVII — Bảng ghi nhớ một trang

## 181. KMS cheat sheet

~~~text
AWS KMS = managed key control plane + cryptographic API

KEY OWNERSHIP
AWS owned    → không thấy/không quản lý
AWS managed  → alias/aws/service, service quản lý
Customer     → policy, rotation, disable, deletion, cross-account

KEY TYPES
Symmetric  → AWS service integration, Encrypt/Decrypt/DataKey
Asymmetric → Encrypt/Decrypt hoặc Sign/Verify/Key Agreement
HMAC       → GenerateMac/VerifyMac, không mã hóa

ENVELOPE ENCRYPTION
KMS key → mã hóa data key
Data key → mã hóa dữ liệu lớn
Lưu ciphertext data + encrypted data key
Không lưu plaintext data key

DIRECT ENCRYPT
Symmetric plaintext tối đa 4 KiB

AUTHORIZATION
Key policy là chính
+ IAM policy/delegation
+ grants
+ không có SCP/boundary/endpoint explicit deny
+ key state/context đúng

ENCRYPTION CONTEXT
- AAD, phải khớp khi decrypt
- dùng cho policy/audit
- không bí mật, có thể vào CloudTrail

ROTATION
- key ID/ARN không đổi
- material mới dùng cho encryption mới
- material cũ giữ để decrypt
- không tự re-encrypt data cũ
- automatic period hiện hành: 90–2.560 ngày

DELETION
- waiting period 7–30 ngày
- PendingDeletion không dùng crypto
- xóa xong không khôi phục

MULTI-REGION
- cùng key ID/material
- không phải global
- policy/alias/grant/tag/state riêng từng Region

S3 SSE-KMS
PUT → s3:PutObject + kms:GenerateDataKey
GET → s3:GetObject + kms:Decrypt

CROSS-ACCOUNT
Key policy account sở hữu
AND IAM policy account caller

EXAM TRAPS
- KMS không phải secret store
- Encrypt permission không cho data access
- Data access không cho Decrypt
- Alias không phải key
- Admin không mặc nhiên là key user
~~~

---

# 182. Tài liệu AWS chính thức

- [AWS KMS Developer Guide](https://docs.aws.amazon.com/kms/latest/developerguide/overview.html)
- [AWS KMS concepts](https://docs.aws.amazon.com/kms/latest/developerguide/concepts.html)
- [KMS key types](https://docs.aws.amazon.com/kms/latest/developerguide/symm-asymm-compare.html)
- [Key policies](https://docs.aws.amazon.com/kms/latest/developerguide/key-policies.html)
- [Default key policy](https://docs.aws.amazon.com/kms/latest/developerguide/key-policy-default.html)
- [IAM policies với AWS KMS](https://docs.aws.amazon.com/kms/latest/developerguide/iam-policies.html)
- [KMS policy evaluation](https://docs.aws.amazon.com/kms/latest/developerguide/policy-evaluation.html)
- [KMS grants](https://docs.aws.amazon.com/kms/latest/developerguide/grants.html)
- [Cross-account KMS key use](https://docs.aws.amazon.com/kms/latest/developerguide/key-policy-modifying-external-accounts.html)
- [Encryption context](https://docs.aws.amazon.com/kms/latest/developerguide/encrypt_context.html)
- [Envelope encryption](https://docs.aws.amazon.com/kms/latest/developerguide/concepts.html#enveloping)
- [Rotating KMS keys](https://docs.aws.amazon.com/kms/latest/developerguide/rotate-keys.html)
- [On-demand rotation](https://docs.aws.amazon.com/kms/latest/developerguide/rotating-keys-on-demand.html)
- [Deleting KMS keys](https://docs.aws.amazon.com/kms/latest/developerguide/deleting-keys.html)
- [Multi-Region keys](https://docs.aws.amazon.com/kms/latest/developerguide/multi-region-keys-overview.html)
- [Imported key material](https://docs.aws.amazon.com/kms/latest/developerguide/importing-keys.html)
- [Custom key stores](https://docs.aws.amazon.com/kms/latest/developerguide/key-store-overview.html)
- [KMS request quotas](https://docs.aws.amazon.com/kms/latest/developerguide/requests-per-second.html)
- [KMS resource quotas](https://docs.aws.amazon.com/kms/latest/developerguide/resource-limits.html)
- [Logging AWS KMS API calls with CloudTrail](https://docs.aws.amazon.com/kms/latest/developerguide/logging-using-cloudtrail.html)
- [Amazon S3 SSE-KMS](https://docs.aws.amazon.com/AmazonS3/latest/userguide/UsingKMSEncryption.html)
- [Amazon EBS encryption](https://docs.aws.amazon.com/ebs/latest/userguide/ebs-encryption.html)
- [Amazon RDS encryption](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/Overview.Encryption.html)


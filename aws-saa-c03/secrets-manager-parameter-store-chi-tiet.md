# AWS Secrets Manager và Systems Manager Parameter Store chuyên sâu — AWS SAA-C03

> Tài liệu này tách riêng và mở rộng phần Secrets Manager và Parameter Store từ Tuần 11. Mục tiêu là chọn đúng nơi lưu database credential, API token, endpoint, AMI ID và application configuration; hiểu đầy đủ rotation, version, KMS, IAM, caching, cross-account, cross-Region và các failure mode khi application lấy giá trị lúc runtime.

## 1. Tình huống mở đầu: một application có hai loại dữ liệu cấu hình

Một service xử lý đơn hàng cần:

~~~text
Database username/password    → bí mật, phải rotate
Payment provider API token   → bí mật, cần audit chặt
Database endpoint            → configuration
Maximum retry count          → configuration
Approved AMI ID              → configuration có version
Feature toggle               → configuration thay đổi thường xuyên
~~~

Nếu đặt tất cả vào source code hoặc environment file trong Git:

- Secret có thể bị lộ trong commit history.
- Rotation đòi hỏi build/deploy lại application.
- Nhiều service giữ các bản credential khác nhau.
- Không biết ai đã đọc hoặc sửa giá trị.
- Production và development dễ dùng nhầm cấu hình.
- Secret có thể xuất hiện trong CI logs, container image hoặc AMI.

Thiết kế hợp lý hơn:

~~~text
                    ┌──────────────────────────────┐
Application role ──►│ Secrets Manager              │
                    │ DB password / API token      │
                    │ version + rotation + audit   │
                    └──────────────────────────────┘

                    ┌──────────────────────────────┐
Application role ──►│ Parameter Store              │
                    │ endpoint / AMI / retry count │
                    │ hierarchy + version          │
                    └──────────────────────────────┘
~~~

Cả hai dịch vụ đều có thể bảo vệ dữ liệu nhạy cảm, nhưng trọng tâm khác nhau:

- **Secrets Manager** quản lý vòng đời của credential/secret.
- **Parameter Store** quản lý configuration theo tên và hierarchy.

---

# Phần I — Chọn dịch vụ trước khi đi vào chi tiết

## 2. Secret là gì?

Secret là dữ liệu cho phép một principal hoặc application chứng minh danh tính hoặc truy cập tài nguyên:

- Database password.
- API key.
- OAuth client secret.
- Private token.
- SSH private key trong use case phù hợp.
- Certificate private material.
- Third-party credential.

Nếu secret bị lộ, attacker có thể sử dụng trực tiếp để truy cập hệ thống. Secret cần rotation, audit, scope và lifecycle chặt.

## 3. Configuration là gì?

Configuration là dữ liệu điều khiển application nhưng không nhất thiết là credential:

- Service endpoint.
- Port.
- Retry count.
- Log level.
- Resource ARN/ID.
- Approved AMI ID.
- Environment name.
- Tuning parameter.

Configuration vẫn có thể nhạy cảm. Ví dụ internal endpoint hoặc tenant mapping có thể không nên public. Nhưng “nhạy cảm” không tự động biến mọi config thành credential cần rotation.

## 4. Quy tắc chọn nhanh

| Requirement | Chọn ưu tiên |
|---|---|
| Database password/API token | Secrets Manager |
| Automatic credential rotation | Secrets Manager |
| Secret replication cross-Region | Secrets Manager |
| Static endpoint/resource ID | Parameter Store `String` |
| Configuration cần hierarchy | Parameter Store |
| Config nhỏ cần mã hóa | Parameter Store `SecureString` |
| Parameter expiration/no-change notification | Advanced Parameter Store |
| Feature flag cần rollout/validation/rollback | AWS AppConfig, có thể dùng Parameter Store làm source |
| Secret cần retrieval qua service đã hỗ trợ SSM reference | Secrets Manager qua `/aws/reference/secretsmanager/...` nếu phù hợp |

## 5. Không chọn chỉ vì giá

Parameter Store Standard có chi phí thấp và thường không tính phí lưu parameter, nhưng không cung cấp đầy đủ secret rotation lifecycle như Secrets Manager.

Chọn Parameter Store cho database password chỉ để tiết kiệm có thể khiến bạn phải tự xây:

- Rotation scheduler.
- Credential update ở database.
- Version cutover.
- Rollback.
- Cross-Region replication.
- Failure recovery.

Chi phí dịch vụ thấp hơn không có nghĩa tổng chi phí vận hành thấp hơn.

## 6. Bảng so sánh tổng quan

| Tiêu chí | Secrets Manager | Parameter Store |
|---|---|---|
| Mục tiêu chính | Secret/credential lifecycle | Configuration key-value hierarchy |
| Automatic rotation | Có | Không có native credential rotation |
| Versioning | Version ID + staging labels | Số version + labels |
| Encryption | Luôn bảo vệ secret value bằng KMS | `SecureString` dùng KMS; `String`/`StringList` không phải secret type |
| Hierarchy | Có thể đặt `/` trong tên nhưng không phải hierarchy API như SSM | Có path hierarchy và `GetParametersByPath` |
| Cross-Region replication | Native replica secrets | Không phải tính năng replication tương đương |
| Cross-account | Resource policy + IAM + KMS | Advanced parameter qua AWS RAM |
| Kích thước hiện hành | Tối đa 65.536 byte | Standard 4 KB, Advanced 8 KB |
| Chi phí | Theo secret và API call | Standard thấp; Advanced/higher throughput tính phí |
| Phù hợp nhất | Credential, token, secret | Endpoint, ARN, AMI, tuning value |

---

# Phần II — AWS Secrets Manager căn bản

## 7. Secrets Manager là gì?

AWS Secrets Manager là dịch vụ được quản lý để:

- Lưu secret value mã hóa.
- Kiểm soát truy cập bằng IAM/resource policy.
- Quản lý version và staging labels.
- Rotate credential tự động hoặc qua Lambda.
- Replicate secret sang Region khác.
- Audit API calls qua CloudTrail.
- Tích hợp với RDS, Aurora và nhiều workload AWS.

Secrets Manager không tự động làm application an toàn nếu role có quyền quá rộng hoặc application ghi secret vào log.

## 8. Một secret gồm những gì?

Một secret có hai nhóm thành phần:

### Secret value

- Text string; hoặc
- Binary value.

### Metadata

- Name.
- ARN.
- Description.
- Tags.
- KMS key ARN.
- Resource policy.
- Rotation configuration.
- Version metadata.
- Replication status.

Chỉ secret value được coi là nội dung bí mật. Name, description và tags không phải nơi lưu password/PII.

## 9. JSON secret

Secrets Manager có thể lưu một JSON text string:

~~~json
{
  "engine": "postgres",
  "host": "orders.cluster-example.ap-southeast-1.rds.amazonaws.com",
  "port": 5432,
  "dbname": "orders",
  "username": "orders_app",
  "password": "EXAMPLE-NOT-A-REAL-PASSWORD"
}
~~~

JSON thuận tiện vì:

- Một credential bundle nằm cùng version.
- Rotation function có đủ connection metadata.
- Application parse theo field.

Không nên gom mọi secret của cả hệ thống vào một JSON khổng lồ. Nếu role chỉ cần payment token nhưng secret chứa cả DB password và admin key, least privilege bị phá vỡ.

## 10. Kích thước secret

Giới hạn hiện hành của secret value là 65.536 byte.

Secrets Manager không phải object store. Không lưu certificate chain/file lớn hoặc config bundle lớn nếu S3/AppConfig phù hợp hơn.

## 11. Secret ARN có hậu tố ngẫu nhiên

Secrets Manager thêm sáu ký tự ngẫu nhiên vào ARN:

~~~text
arn:aws:secretsmanager:ap-southeast-1:111122223333:
secret:prod/orders/database-a1b2c3
~~~

Mục đích:

- Secret bị xóa rồi tạo lại cùng tên vẫn có ARN khác.
- Permission tới ARN cũ không tự động áp dụng cho resource mới nếu policy dùng ARN đầy đủ.

Tránh đặt tên secret kết thúc bằng dấu gạch ngang và đúng sáu ký tự vì partial ARN có thể bị phân tích nhầm. Dùng full ARN hoặc exact name trong API khi có thể.

## 12. Naming convention

Ví dụ:

~~~text
prod/orders/database
prod/orders/payment-provider
staging/orders/database
dev/orders/test-api-token
~~~

Tên có `/` giúp con người tổ chức và policy wildcard, nhưng Secrets Manager không có `GetSecretsByPath` tương đương Parameter Store.

Không đặt secret value trong name:

~~~text
Sai: prod/db/password-SuperSecret123
Đúng: prod/orders/database
~~~

## 13. Secret được mã hóa như thế nào?

Secrets Manager dùng symmetric KMS key và envelope encryption để bảo vệ secret value.

~~~text
KMS key
   │ bảo vệ data key
   ▼
Encrypted data key
   │ data key bảo vệ
   ▼
Encrypted secret value
~~~

Bạn có thể dùng:

- AWS managed key `aws/secretsmanager`.
- Customer managed symmetric KMS key.

Không dùng asymmetric KMS key cho secret encryption.

## 14. AWS managed hay customer managed KMS key?

| AWS managed `aws/secretsmanager` | Customer managed key |
|---|---|
| Thiết lập đơn giản | Policy và lifecycle tự quản |
| Không sửa key policy | Fine-grained/cross-account policy |
| Phù hợp same-account đơn giản | Bắt buộc cho nhiều cross-account pattern |
| Service quản rotation | Customer kiểm soát rotation/disable/deletion |

Chọn customer managed key khi cần cross-account, compliance, revoke/disable riêng hoặc boundary chi tiết.

## 15. Hai lớp quyền khi đọc secret

Với customer managed KMS key, caller thường cần:

~~~text
secretsmanager:GetSecretValue trên secret
AND
kms:Decrypt trên KMS key
~~~

Secret policy/IAM cho phép đọc secret nhưng KMS policy từ chối decrypt → request thất bại.

---

# Phần III — Version và staging labels

## 16. Vì sao secret cần version?

Rotation không thể ghi đè credential cũ ngay lập tức rồi hy vọng mọi application chuyển cùng lúc. Version cho phép tồn tại:

- Credential hiện tại.
- Credential đang chuẩn bị.
- Credential trước đó để rollback có kiểm soát.

## 17. Version ID

Mỗi secret version có version ID. Khi gọi `PutSecretValue`, Secrets Manager tạo version mới thay vì sửa ciphertext của version cũ.

Ứng dụng thường không hard-code version ID; nó đọc qua staging label như `AWSCURRENT`.

## 18. Staging labels chuẩn

| Label | Ý nghĩa |
|---|---|
| `AWSCURRENT` | Version hiện tại; `GetSecretValue` mặc định trả version này |
| `AWSPREVIOUS` | Version trước đó sau rotation thành công |
| `AWSPENDING` | Version đang trong quá trình rotation |

Một staging label chỉ gắn với một version tại một thời điểm. Di chuyển label là cơ chế cutover giữa version.

## 19. Rotation timeline

~~~text
Version V1 [AWSCURRENT]
        │ bắt đầu rotation
        ▼
Version V1 [AWSCURRENT]
Version V2 [AWSPENDING]
        │ set + test thành công
        ▼
Version V1 [AWSPREVIOUS]
Version V2 [AWSCURRENT]
~~~

Application đọc `AWSCURRENT` sẽ chuyển sang V2 mà không cần biết version ID.

## 20. Đọc version trước để rollback/điều tra

~~~bash
aws secretsmanager get-secret-value \
  --secret-id prod/orders/database \
  --version-stage AWSPREVIOUS
~~~

Không tự động fallback sang `AWSPREVIOUS` trong application nếu chưa đánh giá security. Credential cũ có thể đã bị revoke và fallback vô tình kéo dài thời gian sống của credential.

## 21. Custom staging label

Có thể tạo label tùy chỉnh như `BLUE`, `GREEN` hoặc `LAST_KNOWN_GOOD`, nhưng phải có runbook rõ ràng.

Không dùng label như một secret value hoặc business database. Tổng số staging labels trên các version của một secret có quota.

## 22. Idempotency khi ghi secret

`ClientRequestToken` có thể làm version ID cho write/rotation workflow. Dùng token nhất quán giúp retry không tạo version mới ngoài ý muốn.

Không reuse cùng token với secret value khác; operation có thể bị từ chối để bảo vệ idempotency.

## 23. Quota version

Theo quota hiện hành:

- Tối đa 100 versions mỗi secret.
- Tối đa 20 staging labels trên tất cả version của một secret.
- Secrets Manager dọn unlabeled versions khi vượt ngưỡng, nhưng không xóa version mới hơn 24 giờ theo cơ chế quota.

AWS khuyến nghị không cập nhật secret value liên tục nhanh hơn khoảng một lần mỗi 10 phút trong thời gian dài, nếu không version được tạo nhanh hơn khả năng dọn dẹp.

Secrets Manager không phải high-frequency configuration database.

---

# Phần IV — Lấy secret an toàn

## 24. Lấy secret bằng IAM role

Application trên Lambda, EC2, ECS hoặc EKS nên dùng temporary credentials từ execution/task/instance/workload role.

Không hard-code AWS access key để gọi Secrets Manager.

~~~text
Application
   │ dùng IAM role credentials
   ▼
Secrets Manager GetSecretValue
   │ authorization + KMS decrypt
   ▼
Secret plaintext qua TLS
~~~

## 25. Policy đọc một secret

~~~json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "secretsmanager:GetSecretValue",
        "secretsmanager:DescribeSecret"
      ],
      "Resource": "arn:aws:secretsmanager:ap-southeast-1:111122223333:secret:prod/orders/database-a1b2c3"
    },
    {
      "Effect": "Allow",
      "Action": "kms:Decrypt",
      "Resource": "arn:aws:kms:ap-southeast-1:111122223333:key/1234abcd-12ab-34cd-56ef-1234567890ab"
    }
  ]
}
~~~

Nếu dùng `aws/secretsmanager`, application thường không cần một customer key policy do bạn quản, nhưng vẫn cần Secrets Manager permission đúng.

## 26. Không cấp `secretsmanager:*`

Runtime application thường không cần:

- Delete secret.
- Change resource policy.
- Replicate secret.
- Configure rotation.
- Put secret value.

Tách runtime reader, rotation function và secret administrator thành các role khác nhau.

## 27. Caching

Không nên gọi `GetSecretValue` cho mọi request web nếu secret thay đổi theo giờ/ngày.

Caching giúp:

- Giảm latency.
- Giảm API calls/cost.
- Giảm throttling risk.
- Tăng khả năng chịu lỗi ngắn của control plane/network.

AWS cung cấp client-side caching libraries và Parameters and Secrets Lambda Extension cho use case phù hợp.

## 28. Cache tạo trade-off rotation

Cache quá lâu làm application tiếp tục dùng credential cũ sau rotation.

Chọn TTL dựa trên:

- Rotation frequency.
- Thời gian overlap giữa credential cũ/mới.
- SLA revoke credential.
- API/database behavior.
- Failure tolerance.

~~~text
TTL cache < thời gian credential cũ còn được chấp nhận
~~~

Đây là guideline thiết kế, không phải công thức đủ cho mọi rotation strategy.

## 29. Cache failure strategy

Khi authentication bằng cached secret thất bại:

1. Không retry vô hạn với cùng credential.
2. Invalidate/refresh cache có giới hạn.
3. Thử lại một lần theo policy.
4. Nếu vẫn lỗi, surface failure và alert.

Phải tránh hàng nghìn request cùng refresh secret một lúc; dùng locking/single-flight và jitter.

## 30. Secret plaintext trong memory

Sau retrieval, plaintext tồn tại trong process memory. KMS encryption at rest không bảo vệ application đã được phép nhận plaintext.

Giảm exposure:

- Không log secret.
- Không trả secret trong error/HTTP response.
- Không đưa vào metrics/traces.
- Giới hạn process dump/debug access.
- Giữ trong memory thời gian cần thiết.
- Không truyền qua command-line argument nếu process listing/history có thể thấy.

## 31. CLI và shell history

Sai:

~~~bash
aws secretsmanager create-secret \
  --name prod/orders/database \
  --secret-string '{"password":"REAL_PASSWORD"}'
~~~

Secret có thể nằm trong shell history/process list. Khi dùng CLI, đọc từ file tạm được bảo vệ hoặc input mechanism phù hợp; xóa an toàn và tránh CI logs. Tốt hơn, dùng deployment pipeline có secret handling chuẩn.

## 32. `BatchGetSecretValue`

Batch API lấy nhiều secret trong một request, nhưng caller vẫn cần `GetSecretValue` trên từng secret. Nếu dùng filter còn cần list permission phù hợp.

Không dùng batch để cấp wildcard access tiện tay. Một secret không được phép phải được xử lý đúng theo API/policy behavior.

---

# Phần V — Secrets Manager automatic rotation

## 33. Rotation thực sự phải thay đổi hai nơi

Rotation không chỉ tạo chuỗi password mới trong Secrets Manager.

~~~text
1. Secret store value thay đổi
AND
2. Credential ở database/service đích thay đổi
~~~

Nếu chỉ cập nhật secret store, application lấy password mới nhưng database vẫn chấp nhận password cũ → outage.

Nếu chỉ đổi database, application vẫn lấy password cũ → outage.

## 34. Các hình thức rotation hiện hành

Secrets Manager có các mô hình:

- Managed rotation cho secret/resource được hỗ trợ, không cần bạn quản Lambda rotation function.
- Managed external secret rotation cho integration partner được hỗ trợ.
- Rotation bằng Lambda function cho use case khác.

Không phải mọi secret đều tự rotate chỉ vì được lưu trong Secrets Manager. Bạn phải cấu hình schedule và rotation mechanism.

## 35. Bốn bước Lambda rotation

Rotation function theo contract có bốn bước:

~~~text
createSecret
    ↓
setSecret
    ↓
testSecret
    ↓
finishSecret
~~~

## 36. `createSecret`

Nhiệm vụ:

- Kiểm tra version với rotation token đã tồn tại chưa.
- Sinh credential mới an toàn.
- Lưu version mới với `AWSPENDING`.
- Bảo đảm idempotent khi step bị retry.

Không thay credential database trong bước này.

## 37. `setSecret`

Nhiệm vụ:

- Dùng quyền được cấp để đổi credential ở target database/service.
- Xác minh `AWSCURRENT` và `AWSPENDING` thuộc cùng resource/user dự kiến.
- Chống confused deputy: không để attacker sửa host trong secret rồi ép rotation function đổi credential ở database khác.

Rotation function là privileged component; validation phải chặt.

## 38. `testSecret`

Nhiệm vụ:

- Dùng `AWSPENDING` kết nối target.
- Thực hiện operation kiểm tra an toàn.
- Xác nhận credential mới có đúng quyền cần thiết.

Chỉ kiểm tra TCP connection chưa đủ nếu application cần query/permission cụ thể.

## 39. `finishSecret`

Nhiệm vụ:

- Di chuyển `AWSCURRENT` sang version mới.
- Version cũ nhận `AWSPREVIOUS`.
- Hoàn tất cutover.

Không tự ý gỡ `AWSPENDING` giữa quy trình; staging labels sai có thể làm lần rotation sau nghĩ rotation cũ vẫn chưa hoàn tất.

## 40. Rotation function phải idempotent

Secrets Manager có thể retry step. Mỗi step phải an toàn nếu gọi lại với cùng token:

- Không tạo thêm user vô hạn.
- Không đổi password khác ở mỗi retry.
- Không phá version đã hoàn tất.
- Có thể tiếp tục từ state dở dang.

## 41. Single-user rotation

Một database user được đổi password trực tiếp.

~~~text
orders_app password old → password new
~~~

Ưu điểm:

- Đơn giản.
- Không cần clone user.

Trade-off:

- Có khoảng chuyển tiếp khi cached old credential có thể thất bại.
- Application phải refresh nhanh và retry an toàn.

## 42. Alternating-users rotation

Hai database user luân phiên:

~~~text
Rotation 1: app_user_a current, chuẩn bị app_user_b
Rotation 2: app_user_b current, chuẩn bị app_user_a
~~~

Ưu điểm:

- Giảm downtime vì user cũ có thể tồn tại trong lúc chuyển.

Trade-off:

- Phức tạp hơn.
- Cần superuser/master secret để quản user còn lại.
- Phải clone/đồng bộ privilege đúng.
- Cần kiểm soát credential đặc quyền rất chặt.

## 43. Rotation schedule và window

Secrets Manager hỗ trợ `rate()` hoặc `cron()` theo UTC và rotation window.

Theo tài liệu hiện hành:

- Có thể rotate thường xuyên đến mỗi 4 giờ.
- Rotation window nhỏ nhất 1 giờ.
- Maximum rotation period theo schedule hiện hành là 999 ngày.

Không rotate dày chỉ vì dịch vụ cho phép. Database, application cache và third-party rate limit phải chịu được.

## 44. Network cho rotation Lambda

Rotation function phải truy cập được:

- Target database/service.
- Secrets Manager API.
- KMS nếu dùng customer managed key.
- DNS và dependency cần thiết.

Nếu Lambda nằm trong private subnet:

- Security group phải cho phép tới database.
- Route/DNS đúng.
- Cần NAT hoặc interface VPC endpoint cho Secrets Manager/KMS tùy thiết kế.

Nhiều rotation failure thực tế là network path, không phải code.

## 45. IAM cho rotation function

Rotation role thường cần:

- Read/write đúng secret/version stages.
- Dùng KMS key đúng phạm vi.
- Kết nối/quyền target resource.
- Logging không lộ secret.

Không cho rotation function quyền đọc mọi secret hoặc quản mọi database.

## 46. Lambda resource policy

Secrets Manager cần permission invoke rotation function. Giới hạn source secret/account khi khả thi bằng condition như `aws:SourceArn` và `aws:SourceAccount` để giảm confused deputy.

## 47. Rotation failure

Kiểm tra:

- `AWSPENDING` bị kẹt ở version nào?
- Step nào lỗi?
- Lambda logs có lỗi network/IAM/KMS không?
- Database đang chấp nhận credential nào?
- Rotation token/version state có nhất quán không?
- Cache application đang dùng version nào?

Không sửa labels mù quáng khi chưa biết target credential state.

## 48. Rotation monitoring

Theo dõi:

- `LastRotatedDate`.
- `NextRotationDate`/schedule.
- Rotation success/failure events.
- Lambda errors/throttles/duration.
- Authentication failure ở application.
- Secret age vượt policy.

Một rotation function được cấu hình nhưng luôn lỗi không tạo ra security benefit.

---

# Phần VI — Secrets Manager IAM và resource policy

## 49. Identity-based policy

Gắn vào IAM role/user và xác định principal được làm gì với secret ARN nào.

Runtime role thường chỉ cần:

~~~text
secretsmanager:GetSecretValue
secretsmanager:DescribeSecret (nếu code cần)
kms:Decrypt (nếu customer managed key)
~~~

## 50. Resource-based policy

Secret có thể có resource policy để cấp access cho principal, đặc biệt cross-account.

Resource policy quá rộng có thể làm secret public/cross-account ngoài ý muốn. Dùng:

- `ValidateResourcePolicy`.
- `BlockPublicPolicy=true` với `PutResourcePolicy`.
- IAM Access Analyzer.

## 51. Cross-account Secrets Manager

Account A sở hữu secret; role ở B cần đọc.

Cần ba lớp:

~~~text
1. Secret resource policy ở A cho role/account B
2. IAM policy trên role B cho GetSecretValue
3. Customer managed KMS key policy + IAM cho B decrypt
~~~

Không dùng AWS managed key `aws/secretsmanager` cho cross-account access; dùng customer managed key.

## 52. Cross-account không phải replication

- Cross-account access: role B gọi secret vẫn nằm ở Region/account A.
- Replication: Secrets Manager tạo replica secret ở Region khác trong account theo replication model.

Cross-account call phụ thuộc availability/network/permission của source Region. Replication cho local regional copy và DR pattern khác.

## 53. Tag-based access

Có thể dùng tags cho ABAC, ví dụ `Environment=Prod`. Nhưng nếu quyền phụ thuộc tag:

- `TagResource`/`UntagResource` trở thành quyền nhạy cảm.
- Phải ngăn user tự đổi tag để nâng quyền.
- Resource và principal tag convention phải ổn định.

## 54. ARN wildcard an toàn

Secret ARN có sáu ký tự suffix. Có thể dùng `??????` để match chính xác sáu ký tự khi policy phải áp dụng cho secret cùng logical name được tạo sau này.

Wildcard `*` rộng hơn và có thể match các tên ngoài ý muốn. Ưu tiên full ARN khi resource đã tồn tại.

## 55. VPC endpoint

Secrets Manager hỗ trợ interface VPC endpoint qua AWS PrivateLink.

Lợi ích:

- Private subnet không cần NAT chỉ để gọi Secrets Manager.
- Traffic không cần đi qua public internet path.
- Có endpoint policy.

Cần cấu hình private DNS, subnet, endpoint security group và policy đúng.

## 56. Endpoint policy là lớp giới hạn

Endpoint policy không thay IAM/resource policy. Request phải vượt qua tất cả lớp authorization.

Giới hạn bằng `aws:SourceVpc`/`aws:SourceVpce` cần thận trọng vì AWS service gọi secret thay bạn có thể không đi qua endpoint của VPC bạn.

---

# Phần VII — Replication, deletion và recovery của secret

## 57. Multi-Region replication

Secrets Manager có thể replicate secret từ primary Region tới replica Regions.

~~~text
Primary secret — ap-southeast-1
      ├── replica — ap-northeast-1
      └── replica — eu-west-1
~~~

Secret data mã hóa và metadata như tags/resource policies được replicate theo service behavior.

## 58. KMS key ở replica Region

Replica dùng KMS key ở chính replica Region. Có thể chọn key phù hợp cho từng Region.

Không tham chiếu single-Region KMS key ở Region nguồn như local key cho replica.

## 59. Rotation của replicated secret

Rotation chạy ở primary secret. Giá trị mới sau rotation được propagate tới replicas. Không cấu hình rotation độc lập trên mỗi replica trong relationship đó.

Theo dõi replication status và lag/failure; không giả định mọi replica cập nhật tức thời tuyệt đối.

## 60. Replica chứa connection info nào?

Replication sao chép value. Nếu primary secret chứa endpoint database Region nguồn, replica cũng chứa endpoint đó trừ khi thiết kế value có regional fields.

Replication secret không tự biến database source thành database local Region.

## 61. Promote replica

Có thể promote replica thành standalone secret cho DR/failover workflow. Sau promote, phải quản lý rotation/replication/lifecycle riêng.

Runbook phải phối hợp:

- Database failover.
- DNS/endpoint.
- Secret value.
- Application configuration.
- KMS/IAM.

## 62. Delete secret

Secrets Manager mặc định schedule deletion thay vì xóa ngay.

- Recovery window: tối thiểu 7 ngày, tối đa 30 ngày.
- Secret trở nên inaccessible trong thời gian chờ.
- Có thể restore trước deletion date.
- Có API/CLI force delete without recovery, cực kỳ nguy hiểm.

## 63. Primary có replica không xóa trực tiếp được

Phải remove/delete replicas trước rồi mới delete primary. Replica removal có lifecycle khác và có thể xóa ngay; đọc tài liệu/runbook trước khi thao tác.

## 64. Không xóa trực tiếp một version

Bạn loại staging labels để version trở thành deprecated/unlabeled; Secrets Manager có thể dọn version trong background theo behavior dịch vụ.

Đừng dùng Secrets Manager làm immutable archive version lâu dài.

## 65. Deletion safety runbook

1. Inventory application/role đang dùng secret.
2. Kiểm tra CloudTrail `GetSecretValue` history.
3. Chuyển application sang secret mới.
4. Revoke credential cũ ở target system.
5. Schedule deletion với recovery window.
6. Alarm access attempt trong recovery window.
7. Restore nếu phát hiện dependency.

CloudTrail history ngắn không chứng minh secret không còn dùng; có thể có disaster job chạy theo quý.

---

# Phần VIII — Monitoring, quota và cost của Secrets Manager

## 66. CloudTrail

CloudTrail ghi Secrets Manager API calls, gồm retrieval và management events theo service behavior.

Audit câu hỏi:

- Principal nào gọi `GetSecretValue`?
- Secret nào được truy cập?
- Từ Region/IP/VPC endpoint nào?
- Ai thay resource policy?
- Ai bật/tắt rotation?
- Ai schedule deletion?

CloudTrail không nên ghi plaintext secret value. Application logs mới là nơi thường vô tình làm lộ plaintext.

## 67. Events cần alert

- `DeleteSecret`/`RestoreSecret`.
- `PutResourcePolicy`/`DeleteResourcePolicy`.
- `UpdateSecret`/`PutSecretValue` bất thường.
- `RotateSecret`/`CancelRotateSecret`.
- Rotation failure.
- Replication change.
- KMS key disable/policy change.
- Spike `GetSecretValue` từ principal lạ.

## 68. Quota chính hiện hành

| Quota | Giá trị đáng nhớ |
|---|---:|
| Secret value size | 65.536 byte |
| Secrets mỗi account/Region | 500.000 |
| Versions mỗi secret | 100 |
| Staging labels mỗi secret | 20 |
| `GetSecretValue` | 10.000 request/giây/Region |
| `BatchGetSecretValue` | 100 request/giây/Region |

Quotas có thể thay đổi. Kiểm tra tài liệu/Service Quotas ở Region thực tế.

## 69. Cost

Secrets Manager thường tính phí:

- Theo secret mỗi tháng, gồm replica như resource riêng theo pricing.
- Theo API calls.
- KMS/customer key và VPC endpoint có thể phát sinh chi phí riêng.

Caching giảm API cost nhưng phải cân bằng freshness/revocation.

Không ghi giá USD cố định vào architecture decision; dùng trang pricing hiện hành.

---

# Phần IX — Parameter Store căn bản

## 70. Parameter Store là gì?

Parameter Store là capability của AWS Systems Manager để lưu named key-value configuration theo hierarchy.

Use case:

- Endpoint URL.
- Resource ARN/ID.
- AMI ID.
- Environment name.
- Retry count.
- License/config value.
- SecureString nhỏ.
- Input cho SSM documents, CloudFormation hoặc deployment workflow được hỗ trợ.

## 71. Ba parameter types

Parameter Store hỗ trợ:

- `String`.
- `StringList`.
- `SecureString`.

## 72. `String`

Dùng cho plaintext configuration:

~~~text
/prod/orders/api/base-url = https://orders.internal.example
/prod/orders/retry/max   = 5
~~~

“Plaintext” ở đây có nghĩa caller được phép lấy trực tiếp value mà không có lớp `WithDecryption`/customer KMS permission của `SecureString`. Systems Manager vẫn có service-side protection at rest cho dữ liệu của dịch vụ, nhưng bạn không có security boundary KMS riêng trên value như `SecureString`.

Không lưu password/token trong `String`.

## 73. `StringList`

Là danh sách comma-separated:

~~~text
subnet-aaa,subnet-bbb,subnet-ccc
~~~

Không phù hợp khi item chứa dấu phẩy hoặc cần nested structure. Parameter Store không phải JSON schema/config document system.

## 74. `SecureString`

Parameter value được mã hóa bằng symmetric KMS key.

~~~text
Parameter name/description/tags → không bí mật
Parameter value                 → KMS protected
~~~

Chỉ value được bảo vệ như SecureString. Không đặt secret trong tên/description/tag.

## 75. SecureString không biến Parameter Store thành Secrets Manager

SecureString cung cấp encrypted storage và access control, nhưng Parameter Store không tự:

- Đổi password trong database.
- Rotate credential.
- Quản `AWSCURRENT/AWSPREVIOUS` cutover.
- Replicate secret cross-Region theo Secrets Manager model.

Dùng SecureString khi giá trị nhạy cảm nhưng lifecycle đơn giản hoặc rotation được quản ngoài. Credential có lifecycle nên ưu tiên Secrets Manager.

## 76. Data type `aws:ec2:image`

Với `String`, có thể đặt data type `aws:ec2:image` để Parameter Store validate AMI ID theo khả năng dịch vụ.

Use case:

~~~text
/prod/base-ami/latest = ami-0123456789abcdef0
~~~

Pipeline cập nhật parameter; launch template/automation đọc AMI approved thay vì hard-code.

---

# Phần X — Hierarchy và naming Parameter Store

## 77. Hierarchical names

Tên parameter có thể dùng `/`:

~~~text
/prod/orders/api/base-url
/prod/orders/database/port
/prod/orders/worker/max-concurrency
/staging/orders/api/base-url
~~~

Hierarchy hỗ trợ:

- Tổ chức theo environment/application/component.
- IAM scope theo path.
- Query bằng `GetParametersByPath`.
- Quản trị hàng nghìn config dễ hơn flat names.

## 78. Tối đa hierarchy depth

Parameter hierarchy hỗ trợ tối đa 15 levels.

Đừng dùng đủ 15 level chỉ vì có thể. Path quá sâu làm policy và vận hành khó hiểu.

## 79. Naming convention gợi ý

~~~text
/{environment}/{application}/{component}/{name}
~~~

Ví dụ:

~~~text
/prod/orders/api/timeout-ms
/prod/orders/db/endpoint
/prod/orders/worker/batch-size
~~~

Không trộn:

~~~text
/prod/orders/...
/production/order-service/...
/prd/order/...
~~~

nếu chúng đại diện cùng environment/application.

## 80. Query theo path

~~~bash
aws ssm get-parameters-by-path \
  --path /prod/orders \
  --recursive \
  --with-decryption
~~~

`--recursive` trả parameters ở các level bên dưới. Đây cũng là nơi có bẫy IAM quan trọng.

## 81. Bẫy `GetParametersByPath`

Parameter Store authorize `GetParametersByPath` dựa trên path request, không đánh giá lại từng parameter con theo cách nhiều người kỳ vọng.

Nếu principal được phép recursive trên `/a`, họ có thể nhận `/a/b`, kể cả khi policy khác deny read `/a/b` cho một số action.

Để bảo vệ parameter nhạy cảm:

- Tách sang path riêng.
- Không Allow ancestor path quá rộng.
- Deny `GetParametersByPath` trên mọi ancestor có thể trả parameter.
- Hạn chế `kms:Decrypt` bằng customer managed key.
- Test tất cả retrieval APIs.

## 82. Không lồng parameter reference trong value

Parameter value không tự resolve một parameter khác chỉ vì chứa:

~~~text
{{ssm:/prod/orders/db/host}}
~~~

Một số service/template engine hỗ trợ dynamic reference riêng, nhưng Parameter Store bản thân không thực hiện recursive interpolation trong stored value.

---

# Phần XI — Standard, Advanced và Intelligent-Tiering

## 83. Standard parameter

Theo quota hiện hành:

- Tối đa 10.000 Standard parameters mỗi account/Region.
- Value tối đa 4 KB.
- Không hỗ trợ parameter policies.
- Không hỗ trợ cross-account sharing.
- Không tính phí lưu parameter theo standard tier model hiện hành.

Phù hợp phần lớn configuration nhỏ, traffic thấp.

## 84. Advanced parameter

Theo quota hiện hành:

- Tối đa 100.000 Advanced parameters mỗi account/Region.
- Value tối đa 8 KB.
- Hỗ trợ parameter policies.
- Hỗ trợ sharing cross-account qua AWS RAM.
- Có phí lưu/API theo pricing hiện hành.

## 85. Upgrade nhưng không downgrade

Có thể chuyển Standard thành Advanced. Không thể chuyển Advanced trực tiếp ngược về Standard vì khác size/policy model.

Muốn “downgrade” thường phải tạo parameter Standard mới phù hợp rồi migrate consumer, không phải flip tier tại chỗ.

## 86. Intelligent-Tiering

Parameter Store có account/Region default tier setting, trong đó Intelligent-Tiering có thể chọn Standard khi request đủ điều kiện và tự tạo Advanced khi request cần feature/limit Advanced.

Ví dụ trigger Advanced có thể gồm:

- Value lớn hơn giới hạn Standard.
- Gắn parameter policy.
- Đã vượt quota số Standard parameters.

Intelligent-Tiering giảm việc chọn tier thủ công nhưng có thể tạo charge Advanced; cần tag/budget/monitoring.

## 87. Bảng so sánh tier

| Thuộc tính | Standard | Advanced |
|---|---:|---:|
| Số parameters/account/Region | 10.000 | 100.000 |
| Maximum value | 4 KB | 8 KB |
| Parameter policies | Không | Có |
| Cross-account AWS RAM sharing | Không | Có |
| Storage charge | Không thêm phí theo model hiện hành | Có |
| Downgrade trực tiếp | — | Không |

---

# Phần XII — KMS với Parameter Store SecureString

## 88. Chỉ dùng symmetric KMS key

Parameter Store SecureString chỉ hỗ trợ symmetric KMS key.

Có thể dùng:

- AWS managed key `alias/aws/ssm`.
- Customer managed key.

## 89. Standard SecureString encryption

Với Standard SecureString tối đa 4 KB, Parameter Store gọi KMS `Encrypt` trực tiếp cho value.

~~~text
Plaintext parameter value
        │ KMS Encrypt
        ▼
KMS ciphertext stored by Parameter Store
~~~

Khi lấy với `WithDecryption=true`, Parameter Store gọi KMS `Decrypt` thay mặt caller theo authorization.

## 90. Advanced SecureString encryption

Advanced SecureString dùng envelope encryption qua AWS Encryption SDK:

~~~text
KMS key → bảo vệ data key
Data key → bảo vệ parameter value
~~~

Đây là một lý do Advanced hỗ trợ value lớn hơn direct KMS plaintext limit.

## 91. `WithDecryption`

~~~bash
aws ssm get-parameter \
  --name /prod/orders/vendor/token \
  --with-decryption
~~~

Nếu không đặt `WithDecryption`, API trả encrypted value/không trả plaintext theo operation behavior, không giúp application dùng secret.

## 92. Quyền đọc SecureString

Caller cần:

~~~text
ssm:GetParameter/GetParameters/GetParametersByPath/GetParameterHistory
AND
kms:Decrypt trên key
~~~

Action SSM nào cần phụ thuộc API được gọi. Đừng chỉ deny `GetParameter` rồi nghĩ secret đã được bảo vệ.

## 93. Rủi ro `alias/aws/ssm`

AWS managed key mặc định cho Parameter Store cho phép principals trong account sử dụng theo service policy khi họ có SSM access phù hợp.

Nếu cần phân tách ai được decrypt nhóm parameters nào, dùng customer managed key với key policy/IAM conditions rõ ràng.

## 94. Parameter Store encryption context

Parameter Store gửi encryption context khi dùng KMS theo service design. Có thể dùng context/`kms:ViaService` để giới hạn key usage khi hiểu rõ exact values.

Không tự đặt policy context đoán mò; đối chiếu CloudTrail và tài liệu KMS/SSM.

## 95. Metadata vẫn lộ

SecureString chỉ bảo vệ value. Name, ARN, description, tier, tags và modification metadata không được bảo vệ như secret value.

Sai:

~~~text
/prod/customer-email/alice@example.com/password
~~~

Đúng hơn:

~~~text
/prod/orders/database/credential
~~~

---

# Phần XIII — Parameter versions, labels và policies

## 96. Parameter version

Mỗi lần overwrite value, Parameter Store tạo version mới.

~~~text
Version 1 = timeout 1000
Version 2 = timeout 2000
Version 3 = timeout 1500
~~~

Có thể lấy version cụ thể để rollback/điều tra.

## 97. Version retention

Parameter Store giữ tối đa 100 versions mỗi parameter.

Khi tạo version 101, version cũ nhất thường bị xóa. Nếu version cũ nhất đang có label, write mới có thể thất bại để tránh mất labeled version; phải di chuyển label trước.

Version history không phải archive vô hạn.

## 98. Parameter labels

Label là tên thân thiện trỏ tới version:

~~~text
/prod/orders/api/timeout:current
/prod/orders/api/timeout:previous
/prod/orders/api/timeout:canary
~~~

Di chuyển label thay vì sửa application hard-code version number.

Không nhầm parameter label với Secrets Manager staging labels. Ý tưởng tương tự nhưng API/lifecycle khác.

## 99. Parameter policies chỉ có ở Advanced

Ba loại chính:

- `Expiration`.
- `ExpirationNotification`.
- `NoChangeNotification`.

Có thể gắn tối đa 10 policies trên một Advanced parameter theo quota hiện hành.

## 100. `Expiration`

Xóa parameter khi tới thời điểm cấu hình.

Đây là destructive behavior. Nếu application vẫn phụ thuộc parameter, retrieval sẽ fail.

Dùng cho temporary access/config có owner và monitoring rõ ràng.

## 101. `ExpirationNotification`

Phát EventBridge event trước khi parameter hết hạn. Bạn cấu hình rule target như SNS/Lambda/ticket automation.

Notification không tự gia hạn parameter.

## 102. `NoChangeNotification`

Phát EventBridge event khi parameter không thay đổi trong khoảng thời gian định trước.

Hữu ích cho:

- Nhắc review approved AMI.
- Phát hiện config/credential tự quản chưa được cập nhật.
- Compliance workflow.

Nó không tự rotate value; chỉ phát event.

## 103. Policy scan là asynchronous

Parameter Store thực thi policies qua periodic asynchronous scans. Không dùng expiration như scheduler chính xác đến từng giây cho business transaction.

## 104. EventBridge parameter events

Parameter Store có thể phát event best-effort cho:

- Create/update/delete.
- Label change.
- Policy expiration/notification/no-change.

Consumer event phải idempotent và không coi EventBridge notification là audit source duy nhất; CloudTrail/Config có vai trò khác.

---

# Phần XIV — IAM cho Parameter Store

## 105. Policy đọc một parameter

~~~json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": "ssm:GetParameter",
      "Resource": "arn:aws:ssm:ap-southeast-1:111122223333:parameter/prod/orders/api/base-url"
    }
  ]
}
~~~

ARN bỏ slash đầu trong resource segment sau `parameter/`.

## 106. Policy đọc một path

~~~json
{
  "Effect": "Allow",
  "Action": [
    "ssm:GetParameters",
    "ssm:GetParametersByPath"
  ],
  "Resource": "arn:aws:ssm:ap-southeast-1:111122223333:parameter/prod/orders/*"
}
~~~

Chỉ cấp ancestor path nếu mọi parameter con đều an toàn cho role đó.

## 107. Bốn retrieval actions cần nhớ

- `ssm:GetParameter`.
- `ssm:GetParameters`.
- `ssm:GetParametersByPath`.
- `ssm:GetParameterHistory`.

Deny một action không deny các action còn lại.

## 108. `GetParameterHistory` có thể lộ current value

History gồm cả current version. Principal bị deny `GetParameter` nhưng còn `GetParameterHistory` vẫn có thể thấy current value, kể cả SecureString nếu có KMS decrypt.

Để chặn đọc hoàn toàn, deny/không allow mọi retrieval path.

## 109. Batch delete actions

`DeleteParameter` và `DeleteParameters` là hai actions khác nhau. Deny action đơn lẻ không tự chặn batch API.

Use wildcard cẩn thận hoặc liệt kê cả hai trong destructive-operation guardrail.

## 110. Tách sensitive path

Tốt:

~~~text
/prod/orders/public-config/*
/prod/orders/restricted/*
~~~

Khó bảo vệ:

~~~text
/prod/orders/config/db-password
/prod/orders/config/log-level
~~~

nếu cùng role cần recursive read parent path cho log-level nhưng không được đọc password.

## 111. KMS là lớp chặn cuối cho SecureString

Deny `kms:Decrypt` trên customer managed key ngăn plaintext được lấy qua các retrieval API, dù path permission bị cấp rộng ngoài ý muốn.

Tuy vậy, metadata/name vẫn có thể lộ. KMS không thay IAM path design.

---

# Phần XV — Parameter Store cross-account sharing

## 112. Hỗ trợ hiện hành

Parameter Store hiện hỗ trợ share **Advanced parameters** qua AWS Resource Access Manager.

Có thể share tới:

- Account cụ thể.
- Organizational unit.
- Cả AWS Organization.

Các bài viết cũ nói Parameter Store không hỗ trợ cross-account có thể đã lỗi thời.

## 113. Điều kiện để share

- Parameter thuộc account bạn.
- Parameter phải ở Advanced tier.
- Tạo AWS RAM resource share.
- Nếu share SecureString, phải dùng customer managed KMS key.
- Share/configure KMS key permission riêng.
- Enable RAM sharing with Organizations nếu share theo org/OU.

AWS managed `aws/ssm` key không share cross-account.

## 114. Consumer access

Consumer dùng full parameter ARN:

~~~bash
aws ssm get-parameter \
  --name arn:aws:ssm:ap-southeast-1:111122223333:parameter/shared/platform/base-url
~~~

Shared parameter không nhất thiết xuất hiện ở cùng chỗ UI như parameter do account consumer sở hữu.

## 115. Read-only model

Consumer nhận read-only permission theo AWS RAM managed permission set được chọn. Consumer không:

- Update parameter.
- Delete parameter.
- Share tiếp cho account thứ ba.

Owner giữ lifecycle control.

## 116. Integration limitations

Không phải mọi service hỗ trợ shared parameters. Tài liệu hiện hành liệt kê một số integration được hỗ trợ và không hỗ trợ; ví dụ CloudFormation dynamic references, một số environment-variable integrations và ECS secret value có limitation.

Kiểm tra service-specific docs trước khi thiết kế central parameter account.

## 117. Availability dependency

Nếu owner stop sharing, xóa parameter, disable KMS key hoặc đóng account, consumer mất access.

Cross-account centralization cần:

- Owner/SLA.
- Change management.
- Replication/copy strategy nếu cần isolation.
- Monitoring ở owner và consumer.

---

# Phần XVI — Throughput, caching và cost Parameter Store

## 118. Default throughput

Theo quota hiện hành, `GetParameter`, `GetParameters` và `GetParametersByPath` mặc định dùng chung 40 TPS mỗi account/Region.

Một Lambda burst hàng trăm concurrent invocation, mỗi invocation gọi nhiều parameter, có thể throttle rất nhanh.

## 119. Higher throughput

Khi bật higher throughput, quota hiện hành:

- `GetParameter`: 10.000 TPS.
- `GetParameters`: 1.000 TPS.
- `GetParametersByPath`: 100 TPS.

Higher throughput có phí. SecureString còn có thể bị giới hạn bởi KMS throughput.

## 120. Control-plane throughput

Các API write/history/label có quota thấp hơn read API. Parameter Store không dành cho update mỗi request hoặc distributed counter.

## 121. Cache parameters

Cache application-side hoặc dùng Parameters and Secrets Lambda Extension khi phù hợp.

Config cache TTL thường có thể dài hơn secret, nhưng phụ thuộc:

- Tần suất thay đổi.
- SLA propagation.
- Risk dùng stale config.
- Rollback strategy.

## 122. Batch retrieval

`GetParameters` lấy nhiều parameter đã biết tên trong một API call. `GetParametersByPath` lấy theo hierarchy nhưng throughput thấp hơn ở high-throughput mode.

Đừng gọi từng parameter riêng nếu có thể batch/cache.

## 123. Stampede khi cache hết hạn

Hàng nghìn instances cache cùng TTL có thể refresh cùng lúc.

Dùng:

- TTL jitter.
- Background refresh.
- Single-flight locking trong process.
- Stale-while-revalidate có giới hạn nếu config cho phép.

## 124. Cost drivers

- Advanced parameter storage.
- Advanced API interactions.
- Higher throughput setting.
- KMS requests/customer key.
- Interface VPC endpoints.
- Cross-account shared parameter usage.

Standard “không thêm phí” không có nghĩa toàn bộ architecture không tốn phí.

---

# Phần XVII — Parameter Store với AppConfig, CloudFormation, ECS và Lambda

## 125. Parameter Store và AWS AppConfig

Parameter Store là storage/source cho named values. AppConfig bổ sung:

- Validators.
- Gradual deployment.
- Canary rollout.
- Monitoring bằng CloudWatch alarms.
- Automatic rollback.
- Feature flag workflows.

Nếu config thay đổi có thể làm outage toàn fleet, chỉ sửa Parameter Store value tức thời có blast radius lớn. Dùng AppConfig deployment safety.

## 126. CloudFormation dynamic references

CloudFormation có thể resolve SSM/Secrets Manager dynamic references theo cú pháp được hỗ trợ, giúp template không chứa plaintext.

Nhưng:

- Không phải mọi property hỗ trợ dynamic reference.
- CloudFormation có thể không tự redeploy resource khi external value đổi.
- Secret có thể được service đích lưu/hiển thị theo behavior riêng.
- Shared parameter có limitation với dynamic references hiện hành.

Đọc service-specific docs, không giả định dynamic reference tự động rotate running application.

## 127. ECS secrets injection

ECS task definition có thể tham chiếu Secrets Manager hoặc Parameter Store để inject value vào container environment/secrets.

Điểm cần nhớ:

- Task execution role cần quyền retrieval/KMS.
- Value được lấy khi task khởi động.
- Secret rotate không tự thay environment của container đang chạy.
- Cần redeploy/restart tasks hoặc ứng dụng lấy runtime để nhận value mới.
- Environment variable có thể lộ qua process/debugging; runtime retrieval có security trade-off khác.

## 128. Lambda

Lambda có thể:

- SDK call tới Secrets Manager/Parameter Store.
- Dùng Parameters and Secrets Lambda Extension.
- Cache value giữa warm invocations.

Execution role cần least privilege. Nếu Lambda trong VPC private, cần network path tới service endpoint qua NAT hoặc interface VPC endpoint.

## 129. EC2

EC2 dùng instance profile role để lấy parameter/secret runtime.

Không đặt secret plaintext trong user data vì user data có thể được đọc qua API/instance access và xuất hiện trong launch config/log.

## 130. CI/CD

Pipeline role có thể deploy reference thay vì in plaintext secret. Tránh:

- Echo secret.
- Command tracing `set -x`.
- Store secret trong artifact.
- Put secret into Docker build args/layers.
- Expose secret trong PR plan output.

Build role và runtime role nên khác quyền.

---

# Phần XVIII — Parameter Store tham chiếu Secrets Manager

## 131. Reserved path

Parameter Store có pass-through integration:

~~~text
/aws/reference/secretsmanager/prod/orders/database
~~~

Gọi `GetParameter` với path này làm SSM lấy secret từ Secrets Manager.

## 132. Không tạo một parameter copy

Parameter Store không lưu bản secret hoặc metadata như một parameter thật. Nó là stateless pass-through reference.

Secret vẫn được quản lý trong Secrets Manager và permission của Secrets Manager vẫn được tôn trọng.

## 133. Limitations

- Chỉ retrieval qua `GetParameter`/`GetParameters` được hỗ trợ cho reference.
- Không dùng `GetParametersByPath`/`DescribeParameters` như parameter thường.
- Không dùng Parameter Store version/history cho reference.
- Tên phải có prefix `/aws/reference/secretsmanager/`.
- Có thể tham chiếu secret version ID/stage theo syntax được hỗ trợ.

## 134. Khi nào dùng pass-through?

Khi một tool/service đã tích hợp Parameter Store API nhưng secret đúng ra phải nằm trong Secrets Manager.

Không dùng pass-through để né IAM hoặc billing; caller vẫn phải có quyền Secrets Manager/KMS phù hợp.

---

# Phần XIX — Security design patterns

## 135. Runtime retrieval pattern

~~~text
Application starts
  → assume workload role
  → retrieve secret/config
  → cache in memory
  → refresh theo TTL/rotation error
~~~

Ưu điểm:

- Không bake secret vào image.
- Nhận rotation mà không rebuild artifact.
- IAM role định danh workload.

## 136. Sidecar/extension pattern

Extension hoặc sidecar lấy/cache secret thay application.

Ưu điểm:

- Chuẩn hóa caching/retry.
- Giảm SDK code.

Trade-off:

- Thêm local trust boundary.
- Phải bảo vệ local port/socket.
- Extension cache behavior ảnh hưởng freshness.

## 137. Secret per application boundary

Tách:

~~~text
prod/orders/database
prod/billing/database
prod/analytics/warehouse
~~~

Không dùng `prod/all-secrets` chứa mọi credential. Secret granularity nên theo access/lifecycle boundary, không nhất thiết một secret cho mỗi field.

## 138. Config per deployment boundary

Parameter path theo environment/application cho phép role chỉ đọc config của nó:

~~~text
/prod/orders/*
/prod/billing/*
~~~

Tách restricted values sang customer KMS key/path riêng.

## 139. Break-glass

Emergency role đọc secret production phải:

- MFA/approval.
- Time-bound session.
- CloudTrail alert.
- Reason/ticket.
- Không dùng thường ngày.

Không chia sẻ plaintext secret qua chat sau khi break-glass; rotate sau incident nếu secret đã được human xem/export.

## 140. Secret zero problem

Application cần credential để lấy secret. Trên AWS, giải pháp thường là workload IAM role với temporary credentials, không phải một static AWS key khác hard-coded.

~~~text
EC2 instance profile / ECS task role / Lambda execution role
        ↓
GetSecretValue/GetParameter
~~~

## 141. Multi-tenant isolation

Không chỉ gắn tenant ID trong secret JSON rồi cho mọi role đọc. Tách resource/key/policy theo threat model và scale:

- Secret per tenant nếu số lượng/lifecycle cho phép.
- Secret per tenant group/security boundary.
- Application-level encrypted tenant secret store cho quy mô rất lớn.

Đánh giá quota, cost và blast radius.

---

# Phần XX — Observability và incident response

## 142. Không log plaintext

Sanitize:

- SDK response.
- Exception object.
- HTTP request/response.
- Environment dump.
- Debug configuration endpoint.
- Tracing span attributes.
- CI/CD output.

Log secret ARN/name/version ID có thể đủ để điều tra, nhưng name cũng không nên chứa data nhạy cảm.

## 143. CloudTrail vs application audit

CloudTrail cho biết API call tới service. Application audit cho biết business action sau khi lấy secret.

~~~text
CloudTrail: role X gọi GetSecretValue lúc T
App log: order service dùng DB connection cho request Y
~~~

Không cần ghi secret value để correlate; dùng request ID, role, resource ARN và version.

## 144. Secret leak response

1. Xác định secret/value/version bị lộ.
2. Revoke/rotate ở target system ngay.
3. Cập nhật Secrets Manager và cutover.
4. Kiểm tra application cache.
5. Điều tra CloudTrail và data access logs.
6. Xóa secret khỏi Git history/artifact/log theo incident process.
7. Tìm các secret tương tự.
8. Sửa root cause.

Chỉ xóa chuỗi khỏi file mới nhất không làm credential an toàn trở lại.

## 145. Rotation outage response

1. Xác định DB/service chấp nhận version nào.
2. So sánh `AWSCURRENT`, `AWSPENDING`, `AWSPREVIOUS`.
3. Dừng retry storm nếu có.
4. Khôi phục credential nhất quán giữa store và target.
5. Refresh/restart application cache có kiểm soát.
6. Sửa rotation function/network/IAM.
7. Test trước khi resume schedule.

---

# Phần XXI — Troubleshooting

## 146. `AccessDenied` khi lấy secret

Kiểm tra:

- `secretsmanager:GetSecretValue` trên đúng ARN.
- Secret resource policy.
- Customer KMS key policy và `kms:Decrypt`.
- SCP, permissions boundary, session policy.
- VPC endpoint policy.
- Secret Region/account.
- ARN suffix/full ARN.
- Secret đang scheduled deletion không.

## 147. Secret lấy được nhưng authentication target fail

- Cached version cũ?
- `AWSCURRENT` sai?
- Rotation `setSecret` thành công nhưng `finishSecret` lỗi?
- Database endpoint/user trong JSON sai?
- Credential target đã bị manual change?
- Application parse field sai?
- Replica chưa cập nhật hoặc chứa endpoint source Region?

## 148. Rotation Lambda timeout

- Lambda có route tới database không?
- Security groups đúng hai chiều stateful path?
- DNS resolve được không?
- Có endpoint/NAT tới Secrets Manager/KMS không?
- Database connection pool/limit?
- Function timeout và dependency timeout?
- Rotation code có retry vô hạn?

## 149. Parameter Store throttling

- Có gọi mỗi request không?
- Lambda concurrency burst?
- Dùng `GetParametersByPath` quá nhiều?
- Có batch/cache không?
- Higher throughput đã bật chưa?
- KMS có throttle SecureString không?

## 150. SecureString vẫn đọc được dù deny `GetParameter`

Caller có thể còn:

- `GetParameters`.
- `GetParametersByPath` ancestor.
- `GetParameterHistory`.
- `kms:Decrypt`.

Test toàn bộ retrieval APIs và path ancestors.

## 151. Shared parameter không lấy được

- Có phải Advanced tier không?
- RAM invitation/share active không?
- Consumer dùng full ARN không?
- Permission set cho phép operation không?
- SecureString dùng customer managed key và key policy đã share chưa?
- Integration đó có hỗ trợ shared parameter không?

## 152. ECS/Lambda không nhận secret mới sau rotation

Nếu value được inject lúc startup, running task/function environment không tự thay đổi. Redeploy/restart hoặc chuyển sang runtime retrieval/cache refresh pattern.

## 153. Partial ARN không tìm thấy secret

Dùng full ARN hoặc exact secret name. Đặc biệt tránh secret name tự kết thúc bằng `-xxxxxx` vì có thể giống suffix hệ thống.

---

# Phần XXII — Bẫy SAA-C03

## 154. Database credential cần automatic rotation

Chọn Secrets Manager, không chọn Parameter Store chỉ vì SecureString có encryption.

## 155. Static config theo hierarchy

Chọn Parameter Store. Nếu cần safe rollout/validation, kết hợp AppConfig.

## 156. SecureString cần cả SSM và KMS permission

`ssm:GetParameter` không đủ nếu caller thiếu `kms:Decrypt` trên customer managed key.

## 157. Secrets Manager cross-account

Cần secret resource policy + caller IAM policy + customer managed KMS key permission. Không dùng `aws/secretsmanager` cho cross-account.

## 158. Rotation không chỉ cập nhật stored value

Phải đổi credential ở database/service và test trước khi chuyển `AWSCURRENT`.

## 159. ECS injected secret không tự refresh

Rotate secret không thay environment của task đang chạy. Cần new task deployment hoặc runtime retrieval.

## 160. Parameter Store path permission

Recursive access vào parent path có thể trả parameter con. Thiết kế path boundary trước khi viết IAM.

## 161. Advanced parameter mới có policy/share

Expiration, no-change notification và AWS RAM cross-account sharing cần Advanced tier.

## 162. Secrets Manager replication không replicate database

Replica secret chỉ là secret copy. Database/data plane phải có DR/replication riêng.

## 163. Parameter Store không phải AppConfig rollout

Sửa parameter có thể làm mọi consumer đọc value mới; không có canary/validation/automatic rollback đầy đủ như AppConfig.

---

# Phần XXIII — Bài lab

## 164. Lab 1 — Secrets Manager retrieval

1. Tạo customer managed KMS key cho lab.
2. Tạo secret JSON `lab/orders/database`.
3. Tạo reader role chỉ có `GetSecretValue` và `kms:Decrypt` đúng key.
4. Lấy secret bằng CLI/SDK nhưng không in plaintext vào shared log.
5. Gỡ `kms:Decrypt` và quan sát lỗi.
6. Gỡ `GetSecretValue`, giữ KMS permission và quan sát lỗi.
7. Kiểm tra CloudTrail.

## 165. Lab 2 — Secret versions

1. Đọc `AWSCURRENT`.
2. Put một version mới với `AWSPENDING`.
3. Xem `VersionIdsToStages`.
4. Di chuyển labels trong lab.
5. Đọc `AWSCURRENT` và `AWSPREVIOUS`.
6. Giải thích cutover và rollback risk.

## 166. Lab 3 — Rotation mô phỏng

Không dùng production database.

1. Tạo test database/user.
2. Dùng rotation template phù hợp.
3. Cấu hình network/IAM/KMS.
4. Trigger rotation on demand.
5. Theo dõi bốn steps.
6. Kiểm tra old/new credential.
7. Giả lập `testSecret` fail và quan sát labels.

## 167. Lab 4 — Parameter hierarchy

Tạo:

~~~text
/lab/orders/api/base-url
/lab/orders/api/timeout
/lab/orders/restricted/token
~~~

Sau đó:

1. Query `/lab/orders` recursive.
2. Tạo role chỉ đọc `/lab/orders/api/*`.
3. Thử đọc restricted path.
4. Test `GetParameter`, `GetParameters`, `GetParametersByPath`, `GetParameterHistory`.
5. Ghi lại hành vi IAM.

## 168. Lab 5 — Standard và Advanced

1. Tạo Standard String/SecureString.
2. Quan sát size/tier metadata.
3. Upgrade một parameter lên Advanced.
4. Gắn `NoChangeNotification`.
5. Tạo EventBridge rule.
6. Xác nhận không downgrade trực tiếp được.

## 169. Lab 6 — SecureString KMS separation

1. Tạo hai customer KMS keys: ConfigKey và RestrictedKey.
2. Mã hóa hai nhóm parameters khác nhau.
3. Role A chỉ decrypt ConfigKey.
4. Role B chỉ decrypt RestrictedKey.
5. Cấp path read rộng có chủ đích trong lab.
6. Xác nhận KMS vẫn chặn plaintext nhóm không được phép.

## 170. Lab 7 — Caching và rotation

1. Viết app nhỏ cache secret 5 phút.
2. Rotate/update secret.
3. Quan sát app còn dùng old version.
4. Thêm refresh khi authentication fail.
5. Thêm TTL jitter.
6. Đo API calls trước/sau cache.

## 171. Lab 8 — Pass-through reference

1. Tạo secret `lab/reference-demo`.
2. Gọi SSM `GetParameter` với `/aws/reference/secretsmanager/lab/reference-demo`.
3. Thử `GetParametersByPath` và xem limitation.
4. Gỡ Secrets Manager permission và xác nhận reference không bypass IAM.

## 172. Lab 9 — Cross-account design review

Nếu có hai account lab:

- Share một Secrets Manager secret bằng resource policy + customer KMS key.
- Share một Advanced parameter bằng AWS RAM.
- So sánh full ARN, policy và integration limitations.

Nếu không có, viết IaC/policy draft và review bằng Access Analyzer; không mở wildcard principal để thử.

---

# Phần XXIV — Câu hỏi tự kiểm tra

## 173. Câu 1

Ứng dụng cần database password rotate mỗi 30 ngày. Chọn gì?

**Đáp án:** Secrets Manager với rotation được hỗ trợ/rotation Lambda. Parameter Store SecureString chỉ mã hóa storage, không tự đổi password database.

## 174. Câu 2

Ứng dụng cần lưu endpoint, retry count và AMI ID theo `/prod/app/...`. Chọn gì?

**Đáp án:** Parameter Store; cân nhắc AppConfig nếu cần rollout/validation an toàn.

## 175. Câu 3

Role có `GetSecretValue` nhưng secret dùng customer managed key và request bị AccessDenied. Thiếu gì?

**Đáp án:** Có thể thiếu `kms:Decrypt` hoặc key policy không cho role/account sử dụng key.

## 176. Câu 4

Rotation function chỉ cập nhật secret value nhưng không đổi password database. Kết quả?

**Đáp án:** Application lấy credential mới nhưng database không chấp nhận, gây outage. Rotation phải cập nhật cả store và target.

## 177. Câu 5

`AWSPENDING` có ý nghĩa gì?

**Đáp án:** Version đang được chuẩn bị/test trong rotation, chưa phải version mặc định cho application.

## 178. Câu 6

ECS task được inject secret lúc start. Secret rotate nhưng task vẫn dùng cũ. Vì sao?

**Đáp án:** Injection xảy ra lúc task khởi động; environment không tự refresh. Redeploy task hoặc dùng runtime retrieval/cache strategy.

## 179. Câu 7

Standard Parameter Store value lớn 6 KB có lưu được không?

**Đáp án:** Không. Standard tối đa 4 KB; dùng Advanced tối đa 8 KB hoặc service khác nếu data lớn.

## 180. Câu 8

Principal bị deny `GetParameter` nhưng vẫn có `GetParameterHistory`. Có thể thấy current value không?

**Đáp án:** Có. History gồm current version. Phải kiểm soát mọi retrieval actions và KMS decrypt.

## 181. Câu 9

Parameter `/prod/public` và `/prod/restricted/password` cùng dưới `/prod`; role được recursive `GetParametersByPath` trên `/prod`. Role có thể nhận password không?

**Đáp án:** Có nguy cơ có. Tách path, thu hẹp Allow và deny ancestors đúng cách; dùng KMS key riêng cho SecureString.

## 182. Câu 10

Parameter Store có hỗ trợ cross-account không?

**Đáp án:** Hiện có, nhưng chỉ Advanced parameters qua AWS RAM; SecureString cần customer managed KMS key được share riêng và integration có limitations.

## 183. Câu 11

Replicate secret sang Region B có tạo database replica không?

**Đáp án:** Không. Nó chỉ replicate secret value/metadata. Database replication/failover là workflow riêng.

## 184. Câu 12

Tại sao cache secret?

**Đáp án:** Giảm latency, API calls, cost và throttling. Nhưng TTL phải phù hợp rotation/revocation.

## 185. Câu 13

Secret bị lộ trong Git. Chỉ xóa dòng khỏi commit mới nhất có đủ không?

**Đáp án:** Không. Phải revoke/rotate credential ở target, điều tra usage, xóa khỏi history/artifact theo incident process và sửa root cause.

## 186. Câu 14

Feature flag cần canary 10%, validation và rollback khi error tăng. Parameter Store một mình đủ không?

**Đáp án:** Không tối ưu. Dùng AWS AppConfig cho deployment safety; Parameter Store có thể là configuration source trong pattern hỗ trợ.

## 187. Câu 15

Secret cross-account có thể dùng `aws/secretsmanager` key không?

**Đáp án:** Không cho pattern cross-account access. Dùng customer managed KMS key và cấu hình cả secret resource policy, caller IAM và key policy.

## 188. Câu 16

`NoChangeNotification` có rotate parameter không?

**Đáp án:** Không. Nó phát EventBridge event khi parameter không đổi trong khoảng thời gian; automation/owner phải xử lý.

---

# Phần XXV — Checklist production

## 189. Secrets Manager

- [ ] Secret có owner và naming convention.
- [ ] Granularity phù hợp access/lifecycle boundary.
- [ ] Runtime role chỉ có `GetSecretValue` cần thiết.
- [ ] Customer KMS key policy đúng nếu sử dụng.
- [ ] Rotation thực sự đổi target credential.
- [ ] Rotation function idempotent và chống confused deputy.
- [ ] Network path tới database/Secrets Manager/KMS đã test.
- [ ] Cache TTL phù hợp rotation.
- [ ] CloudTrail và rotation alarms được bật.
- [ ] Replication/DR runbook đã test.
- [ ] Deletion có recovery window và approval.

## 190. Parameter Store

- [ ] Chọn đúng String/StringList/SecureString.
- [ ] Standard/Advanced tier có lý do.
- [ ] Hierarchy phản ánh environment/application boundary.
- [ ] Sensitive parameters nằm path/key riêng.
- [ ] Đã test cả bốn retrieval actions.
- [ ] KMS decrypt giới hạn đúng.
- [ ] Version/label lifecycle rõ ràng.
- [ ] Advanced policies có EventBridge owner.
- [ ] Throughput/caching phù hợp concurrency.
- [ ] Shared parameter integration đã được xác nhận hỗ trợ.

## 191. Application

- [ ] Dùng workload IAM role, không dùng static AWS key.
- [ ] Không log secret/config nhạy cảm.
- [ ] Có timeout, retry backoff và cache stampede protection.
- [ ] Có refresh khi credential rotate.
- [ ] Không bake secret vào image/artifact/user data.
- [ ] Không trả config dump chứa secret qua debug endpoint.
- [ ] Failure behavior khi service/KMS unavailable đã được test.

---

# Phần XXVI — Bảng ghi nhớ một trang

## 192. Cheat sheet

~~~text
SECRETS MANAGER
- dành cho credential/token/secret lifecycle
- secret value tối đa 65.536 byte
- KMS encryption
- AWSCURRENT / AWSPREVIOUS / AWSPENDING
- automatic managed hoặc Lambda rotation
- cross-Region replication
- resource policy + IAM + KMS
- cache để giảm latency/cost, nhưng phải refresh khi rotate

ROTATION LAMBDA
createSecret → tạo AWSPENDING
setSecret    → đổi credential target
testSecret   → test credential mới
finishSecret → AWSPENDING thành AWSCURRENT

PARAMETER STORE
- dành cho named configuration/hierarchy
- String / StringList / SecureString
- Standard: 10.000 params, 4 KB
- Advanced: 100.000 params, 8 KB, policies, AWS RAM sharing
- hierarchy tối đa 15 levels
- version history tối đa 100
- không có native credential rotation

PARAMETER POLICIES — ADVANCED ONLY
- Expiration
- ExpirationNotification
- NoChangeNotification

DEFAULT THROUGHPUT
GetParameter + GetParameters + GetParametersByPath
= 40 TPS shared/account/Region

SECURESTRING
SSM permission + kms:Decrypt
Standard → direct KMS Encrypt
Advanced → envelope encryption

IAM TRAPS
- GetParameter deny không chặn GetParameterHistory
- recursive parent path có thể trả restricted child
- test mọi retrieval API

CROSS-ACCOUNT
Secrets Manager:
  resource policy + caller IAM + customer KMS key
Parameter Store:
  Advanced + AWS RAM + customer KMS key cho SecureString

APP RUNTIME
- IAM role
- retrieve + cache
- không log plaintext
- injected environment không tự refresh sau rotation
~~~

---

# 193. Tài liệu AWS chính thức

## Secrets Manager

- [AWS Secrets Manager User Guide](https://docs.aws.amazon.com/secretsmanager/latest/userguide/intro.html)
- [Nội dung của một secret](https://docs.aws.amazon.com/secretsmanager/latest/userguide/whats-in-a-secret.html)
- [Secrets Manager quotas](https://docs.aws.amazon.com/secretsmanager/latest/userguide/reference_limits.html)
- [Lấy secret value](https://docs.aws.amazon.com/secretsmanager/latest/userguide/retrieving-secrets.html)
- [Secrets Manager best practices](https://docs.aws.amazon.com/secretsmanager/latest/userguide/best-practices.html)
- [Secret encryption và KMS](https://docs.aws.amazon.com/secretsmanager/latest/userguide/security-encryption.html)
- [Rotate secrets](https://docs.aws.amazon.com/secretsmanager/latest/userguide/rotating-secrets.html)
- [Lambda rotation functions](https://docs.aws.amazon.com/secretsmanager/latest/userguide/rotate-secrets_lambda-functions.html)
- [Rotation schedules](https://docs.aws.amazon.com/secretsmanager/latest/userguide/rotate-secrets_schedule.html)
- [Resource-based policies](https://docs.aws.amazon.com/secretsmanager/latest/userguide/auth-and-access_resource-policies.html)
- [Cross-account secret access](https://docs.aws.amazon.com/secretsmanager/latest/userguide/auth-and-access_examples_cross.html)
- [Replicate secrets across Regions](https://docs.aws.amazon.com/secretsmanager/latest/userguide/replicate-secrets.html)
- [Delete và restore secret](https://docs.aws.amazon.com/secretsmanager/latest/userguide/manage_delete-secret.html)
- [Secrets Manager VPC endpoint](https://docs.aws.amazon.com/secretsmanager/latest/userguide/vpc-endpoint-overview.html)
- [CloudTrail với Secrets Manager](https://docs.aws.amazon.com/secretsmanager/latest/userguide/monitoring-cloudtrail.html)

## Parameter Store

- [Systems Manager Parameter Store](https://docs.aws.amazon.com/systems-manager/latest/userguide/systems-manager-parameter-store.html)
- [Parameter Store reference](https://docs.aws.amazon.com/systems-manager/latest/userguide/what-is-a-parameter.html)
- [Parameter hierarchies](https://docs.aws.amazon.com/systems-manager/latest/userguide/sysman-paramstore-hierarchies.html)
- [Parameter versions](https://docs.aws.amazon.com/systems-manager/latest/userguide/sysman-paramstore-versions.html)
- [Parameter policies](https://docs.aws.amazon.com/systems-manager/latest/userguide/parameter-store-policies.html)
- [SecureString và KMS](https://docs.aws.amazon.com/systems-manager/latest/userguide/secure-string-parameter-kms-encryption.html)
- [Restrict SecureString decryption](https://docs.aws.amazon.com/systems-manager/latest/userguide/ps-restrict-decryption.html)
- [Restrict parameter/path access](https://docs.aws.amazon.com/systems-manager/latest/userguide/ps-restrict-parameter-access.html)
- [Parameter Store throughput](https://docs.aws.amazon.com/systems-manager/latest/userguide/parameter-store-throughput.html)
- [Parameter Store quotas](https://docs.aws.amazon.com/general/latest/gr/ssm.html#limits_ssm)
- [Share Advanced parameters qua AWS RAM](https://docs.aws.amazon.com/systems-manager/latest/userguide/parameter-store-shared-parameters.html)
- [Parameter Store events](https://docs.aws.amazon.com/systems-manager/latest/userguide/sysman-paramstore-cwe.html)
- [Tham chiếu Secrets Manager từ Parameter Store](https://docs.aws.amazon.com/systems-manager/latest/userguide/integration-ps-secretsmanager.html)


# Amazon S3 chi tiết — Từ nền tảng đến tư duy thiết kế SAA-C03

> Tài liệu này mở rộng phần S3 trong `tuan-6-s3-va-he-thong-file.md`. Mục tiêu không chỉ là nhớ tính năng, mà là hiểu S3 lưu gì, cấp quyền ra sao, bảo vệ dữ liệu thế nào và chọn cấu hình nào trong từng tình huống kiến trúc.

## 1. Bắt đầu bằng một tình huống thực tế

Giả sử một hệ thống thương mại điện tử cần lưu:

- ảnh sản phẩm được đọc hàng triệu lần;
- hóa đơn PDF riêng tư của khách hàng;
- log ứng dụng để phân tích sau 30–90 ngày;
- bản sao lưu phải giữ 7 năm;
- file video vài chục GB do người dùng tải lên.

Một thiết kế S3 có thể là:

~~~text
Người dùng
   │
   ├── xem ảnh ──> CloudFront ──OAC──> S3 private
   │
   ├── xin tải hóa đơn ──> API ──> presigned URL ──> S3
   │
   └── tải video ──> multipart upload trực tiếp ──> S3
                                                │
                                                ├── EventBridge/SQS ──> worker xử lý
                                                ├── Versioning
                                                ├── SSE-KMS
                                                └── Lifecycle ──> Glacier
~~~

S3 không chỉ là “ổ đĩa trên cloud”. Nó là dịch vụ **object storage** có API, policy, versioning, lifecycle, replication và event. Mỗi tính năng giải quyết một vấn đề khác nhau; bật tất cả không mặc nhiên tạo ra kiến trúc tốt.

---

## 2. S3 là gì?

Amazon Simple Storage Service lưu dữ liệu dưới dạng **object** bên trong **bucket**. Một object gồm:

- dữ liệu thật, chẳng hạn ảnh, video, JSON hoặc file backup;
- key dùng để định danh object;
- metadata mô tả object;
- có thể có tags, version ID, checksum và thông tin mã hóa.

Ứng dụng tương tác với S3 chủ yếu qua HTTP API hoặc SDK: `PutObject`, `GetObject`, `DeleteObject`, `ListObjectsV2`... S3 rất phù hợp cho static asset, backup, archive, data lake, log, tài liệu và nội dung do người dùng tải lên.

### 2.1. Object storage khác file storage và block storage

| Loại | Cách truy cập | Đặc tính chính | Ví dụ AWS |
|---|---|---|---|
| Object | API theo bucket và key | scale rất lớn, metadata, không có POSIX đầy đủ | S3 |
| File | mount filesystem, đường dẫn file | thư mục, quyền file, shared access | EFS, FSx |
| Block | volume gồm các block | gắn vào máy, filesystem do OS quản lý | EBS |

S3 không phải ổ đĩa EBS và cũng không phải shared filesystem EFS. Không nên chọn S3 nếu ứng dụng cần random write tại một offset, file lock, append theo semantics POSIX hoặc mount filesystem truyền thống mà không sửa ứng dụng.

### 2.2. S3 là dịch vụ theo Region

Khi tạo bucket general purpose, ta chọn một Region. Object được lưu trong Region đó trừ khi chủ động cấu hình replication. Bucket name lại phải duy nhất trong toàn **AWS partition**, không chỉ duy nhất trong account hay Region.

Tên bucket nên tránh chứa thông tin nhạy cảm vì có thể xuất hiện trong URL, log và DNS. Sau khi xóa bucket, người khác có thể đăng ký lại tên đó; nếu không còn dùng, cân nhắc giữ bucket rỗng và khóa quyền thay vì xóa tên quan trọng.

---

## 3. Bucket, object, key, prefix và “folder”

### 3.1. Bucket

Bucket là container logic chứa object và là ranh giới cho nhiều cấu hình như:

- Region;
- versioning;
- default encryption;
- bucket policy và Block Public Access;
- lifecycle;
- replication;
- event notification;
- logging và Object Lock.

Một general purpose bucket có thể chứa số lượng object rất lớn và không có giới hạn tổng dung lượng cố định. Quota mặc định hiện hành thường là 10.000 general purpose bucket mỗi account, có thể xin tăng. Không nên vì vậy tạo một bucket cho mỗi user; hãy chia bucket theo security boundary, data ownership, lifecycle hoặc môi trường.

### 3.2. Key

Key là tên đầy đủ, duy nhất của object trong bucket:

~~~text
invoices/2026/10/customer-123/invoice-9812.pdf
~~~

Hai object sau có key khác nhau:

~~~text
images/product-1/front.jpg
images/product-1/back.jpg
~~~

S3 nhìn thấy hai chuỗi key. Dấu `/` chỉ giúp con người và công cụ mô phỏng cấu trúc thư mục; trong general purpose bucket không có cây thư mục vật lý giống Linux.

### 3.3. Prefix

Prefix là phần đầu của key, ví dụ:

- `invoices/2026/`;
- `logs/prod/`;
- `tenant-123/private/`.

Prefix hữu ích để list object, đặt lifecycle filter, tách quyền hoặc phân phối request. Có thể dùng `s3:prefix` để giới hạn thao tác list, nhưng quyền đọc object vẫn phải áp vào object ARN tương ứng.

### 3.4. ARN của bucket và object khác nhau

~~~text
Bucket ARN: arn:aws:s3:::company-data
Object ARN: arn:aws:s3:::company-data/reports/*
~~~

`s3:ListBucket` áp lên bucket ARN. `s3:GetObject`, `PutObject`, `DeleteObject` áp lên object ARN. Đây là lỗi policy rất phổ biến.

### 3.5. Kích thước object

Tài liệu cũ thường ghi object tối đa 5 TB. Theo giới hạn S3 hiện hành, tài liệu AWS thường gọi là **50 TB**; giới hạn chính xác từ multipart là 48,8 TiB, tức khoảng 53,7 TB thập phân. Multipart upload có tối đa 10.000 part, mỗi part từ 5 MiB đến 5 GiB, ngoại trừ part cuối không có mức tối thiểu.

AWS khuyến nghị cân nhắc multipart khi object đạt khoảng 100 MB. Một request `PUT` đơn lẻ không phải lựa chọn phù hợp cho file rất lớn.

> Khi làm đề hoặc đọc tài liệu, luôn để ý ngày xuất bản. Con số 5 TB từng đúng nhưng không còn là giới hạn object hiện hành.

---

## 4. Metadata, tags, checksum và ETag

### 4.1. Metadata

System metadata gồm các thuộc tính như `Content-Type`, `Content-Length`, storage class và encryption. User-defined metadata được gán lúc upload, thường có header `x-amz-meta-*`.

Một số metadata không sửa trực tiếp tại chỗ. Muốn thay đổi, ta thường copy object lên chính nó với metadata mới. Vì vậy đừng coi metadata như record trong database được update liên tục.

### 4.2. Object tags

Mỗi tag là cặp key-value, hữu ích cho lifecycle, access control, replication filter và phân bổ chi phí. Tags có thể thay đổi độc lập với nội dung object, nhưng thao tác tag vẫn cần permission riêng.

Ví dụ:

~~~text
data-classification=confidential
retention=7-years
department=finance
~~~

Không đặt bí mật vào key, metadata hoặc tag; các giá trị này có thể xuất hiện trong log và công cụ quản trị.

### 4.3. Checksum và ETag

Checksum giúp phát hiện dữ liệu hỏng trong quá trình truyền/lưu. S3 hỗ trợ nhiều thuật toán checksum. ETag **không phải lúc nào cũng là MD5 của object**, đặc biệt với multipart upload hoặc một số cấu hình mã hóa. Ứng dụng cần kiểm tra tính toàn vẹn nên dùng checksum API/header rõ ràng, không mặc định suy ETag là MD5.

---

## 5. Consistency, atomicity và cập nhật đồng thời

S3 cung cấp strong read-after-write consistency cho PUT, overwrite và DELETE. Sau khi request thành công, GET và LIST tiếp theo sẽ quan sát trạng thái mới.

### 5.1. Điều strong consistency bảo đảm

- upload object rồi GET ngay sẽ thấy object;
- overwrite object rồi GET sẽ thấy phiên bản hoàn chỉnh mới;
- delete xong thì GET/LIST phản ánh việc xóa;
- update trên một key có tính atomic: reader thấy object cũ hoặc mới, không thấy nửa file.

### 5.2. Điều strong consistency không bảo đảm

- không có transaction ACID trên nhiều key;
- không tự giải quyết hai writer cùng ghi một key;
- không biến event notification thành exactly-once;
- không ngăn một writer ghi đè dữ liệu của writer khác.

Nếu hai client cùng ghi `profile.json`, kết quả thường theo request hoàn thành sau. Với dữ liệu cần optimistic concurrency, dùng conditional request như `If-Match`/ETag hoặc `If-None-Match`, versioning và cơ chế phối hợp ở tầng ứng dụng.

Mẫu create-only:

~~~text
PUT object với If-None-Match: *
~~~

Nếu key đã tồn tại, request thất bại thay vì âm thầm overwrite.

---

## 6. Durability khác availability

- **Durability** trả lời: xác suất dữ liệu có bị mất không?
- **Availability** trả lời: ngay lúc này có truy cập được dữ liệu không?

S3 Standard được thiết kế cho durability 99,999999999% (11 số 9), nhưng availability mục tiêu thấp hơn. Một object có thể vẫn bền vững dù tạm thời request lỗi. Ứng dụng vẫn cần timeout, retry với exponential backoff và idempotency.

One Zone-IA và S3 Express One Zone lưu trong một Availability Zone. Chúng có thể vẫn có durability cao trong AZ, nhưng không phù hợp làm bản duy nhất nếu business không chấp nhận mất dữ liệu khi AZ bị phá hủy.

---

## 7. General purpose bucket và directory bucket

Phần lớn câu hỏi SAA truyền thống nói về **general purpose bucket**: dữ liệu phân tán qua nhiều AZ, endpoint regional và tập tính năng rộng.

**Directory bucket** được dùng với S3 Express One Zone, tối ưu độ trễ rất thấp trong một AZ. Nó có naming, endpoint zonal, session-based authorization và tập tính năng khác general purpose bucket. Đừng mặc định policy, API hoặc feature của hai loại hoàn toàn giống nhau.

Chọn S3 Express One Zone khi workload cần request latency ổn định ở mức single-digit millisecond và dữ liệu có thể nằm trong một AZ, chẳng hạn intermediate data cho analytics/ML. Không chọn chỉ vì “S3 Express nhanh hơn”; phải cân nhắc AZ dependency, chi phí và feature compatibility.

---

## 8. Storage classes — chọn theo access pattern

| Storage class | Phạm vi | Availability thiết kế | Thời gian lưu tối thiểu | Truy cập | Use case |
|---|---:|---:|---:|---|---|
| S3 Standard | ≥ 3 AZ | 99,99% | Không | millisecond | dữ liệu truy cập thường xuyên |
| Intelligent-Tiering | ≥ 3 AZ | 99,9% | Không | tùy tier | pattern không biết trước |
| Standard-IA | ≥ 3 AZ | 99,9% | 30 ngày | millisecond, phí retrieval | ít đọc nhưng cần lấy ngay |
| One Zone-IA | 1 AZ | 99,5% | 30 ngày | millisecond, phí retrieval | dữ liệu có thể tạo lại |
| Glacier Instant Retrieval | ≥ 3 AZ | 99,9% | 90 ngày | millisecond, phí retrieval | archive cần đọc tức thời |
| Glacier Flexible Retrieval | ≥ 3 AZ | sau restore | 90 ngày | phút đến giờ | backup/archive |
| Glacier Deep Archive | ≥ 3 AZ | sau restore | 180 ngày | nhiều giờ | lưu dài hạn, rất hiếm đọc |
| S3 Express One Zone | 1 AZ | 99,95% | Không | single-digit ms | workload hiệu năng cao |

Các phần trăm trên là đặc tính thiết kế tiêu biểu, không nên dùng thay cho SLA/price page khi lập hợp đồng hoặc dự toán production.

### 8.1. S3 Standard

Phù hợp khi truy cập thường xuyên hoặc pattern chưa rõ nhưng không muốn phí monitoring. Không có phí retrieval và không có minimum storage duration.

### 8.2. Standard-IA

Chi phí lưu thấp hơn Standard nhưng có phí retrieval, minimum 30 ngày và minimum billable object size 128 KB. Một object 10 KB vẫn có thể bị tính như 128 KB. Chuyển dữ liệu nhỏ hoặc xóa trước 30 ngày có thể đắt hơn Standard.

### 8.3. One Zone-IA

Rẻ hơn Standard-IA vì chỉ nằm trong một AZ. Phù hợp thumbnail có thể tái tạo hoặc secondary copy. Không dùng làm bản duy nhất của hồ sơ pháp lý hay backup quan trọng.

### 8.4. Glacier Instant Retrieval

Dành cho archive ít đọc nhưng khi cần phải trả về ở millisecond, ví dụ ảnh y tế cũ vẫn phải mở ngay. Đổi lại có minimum 90 ngày, minimum billable size và phí retrieval.

### 8.5. Glacier Flexible Retrieval

Object cần được restore trước khi đọc. Các lựa chọn retrieval điển hình:

- Expedited: khoảng 1–5 phút khi được hỗ trợ;
- Standard: khoảng 3–5 giờ;
- Bulk: khoảng 5–12 giờ, tối ưu chi phí.

### 8.6. Glacier Deep Archive

Rẻ cho retention dài hạn, minimum 180 ngày. Standard retrieval thường trong khoảng 12 giờ, Bulk có thể đến 48 giờ. Không phù hợp yêu cầu khôi phục tức thời.

### 8.7. Intelligent-Tiering

Phù hợp khi không dự đoán được access pattern. S3 theo dõi và tự chuyển object:

~~~text
Frequent Access
   └── 30 ngày không truy cập ──> Infrequent Access
          └── 90 ngày không truy cập ──> Archive Instant Access
~~~

Có thể bật thêm Archive Access và Deep Archive Access; các tier này cần restore. Intelligent-Tiering không có retrieval fee ở các access tier, nhưng có phí monitoring/automation theo object. Object dưới 128 KB không được theo dõi/chuyển tự động và nằm ở Frequent Access, nên không phải lúc nào cũng tiết kiệm.

### 8.8. Quy tắc chọn nhanh

~~~text
Đọc thường xuyên?                       -> Standard
Không đoán được tần suất?               -> Intelligent-Tiering
Ít đọc, cần lấy ngay, multi-AZ?         -> Standard-IA
Có thể tạo lại, chấp nhận một AZ?       -> One Zone-IA
Archive nhưng cần lấy ngay?             -> Glacier Instant Retrieval
Chấp nhận chờ phút/giờ?                 -> Glacier Flexible Retrieval
Lưu rất lâu, hiếm đọc, chờ nhiều giờ?   -> Deep Archive
~~~

---

## 9. Lifecycle — tự động quản lý vòng đời

Lifecycle có hai nhóm hành động:

- **Transition:** chuyển storage class;
- **Expiration:** hết hạn object hoặc version.

Rule có thể lọc theo prefix, tags, kích thước hoặc tổ hợp điều kiện.

### 9.1. Ví dụ thực tế

~~~text
logs/prod/*
Day 0:   Standard
Day 30:  Standard-IA
Day 90:  Glacier Flexible Retrieval
Day 365: Expire
~~~

Không áp máy móc timeline này. Nếu log gần như không bao giờ đọc sau ngày đầu, class hoặc thời điểm khác có thể tối ưu hơn.

### 9.2. Current version và noncurrent version

Khi versioning bật, lifecycle phải xử lý riêng:

- current version;
- noncurrent versions;
- expired object delete markers;
- incomplete multipart uploads.

Chỉ expire current version có thể tạo delete marker nhưng vẫn giữ các version cũ, nên dung lượng và chi phí chưa chắc giảm.

### 9.3. Object nhỏ hơn 128 KB

Với hành vi Lifecycle hiện hành, object dưới 128 KB mặc định không được transition sang storage class khác trừ khi rule có size filter/override phù hợp. Ngoài ra, nhiều class tính tối thiểu 128 KB, nên gom nhiều record nhỏ thành object lớn hơn thường hiệu quả cho data lake/archive.

### 9.4. Minimum duration và phí xóa sớm

Nếu object chuyển vào Standard-IA rồi bị xóa sau 5 ngày, vẫn có thể bị tính phí đến minimum 30 ngày. Glacier class có minimum dài hơn. Lifecycle giảm công vận hành nhưng không xóa bỏ điều kiện tính phí.

### 9.5. Abort incomplete multipart upload

Các part đã upload nhưng chưa complete vẫn phát sinh storage cost. Nên có lifecycle rule hủy incomplete multipart upload sau số ngày hợp lý.

---

## 10. Versioning, delete marker và khôi phục

### 10.1. Ba trạng thái

Bucket versioning có thể là:

- chưa từng bật: unversioned;
- enabled;
- suspended.

Sau khi đã bật, ta có thể suspend nhưng không quay hoàn toàn về trạng thái “chưa từng bật”. Object cũ và version ID vẫn tồn tại.

### 10.2. Overwrite khi versioning bật

~~~text
PUT report.csv  -> version v1
PUT report.csv  -> version v2 (current)
~~~

v1 không bị ghi đè vật lý mà trở thành noncurrent version. Muốn rollback, có thể copy v1 thành current version mới hoặc xóa v2 tùy quy trình.

### 10.3. Delete marker

DELETE không kèm version ID thường tạo delete marker:

~~~text
report.csv
├── delete marker (current)
├── version v2
└── version v1
~~~

GET thông thường trả về trạng thái như object không tồn tại, nhưng dữ liệu v1/v2 vẫn còn. Xóa delete marker sẽ làm version trước đó hiện lại.

DELETE kèm version ID nhắm thẳng vào một version và có thể xóa vĩnh viễn. Vì vậy cần tách quyền `s3:DeleteObject` và `s3:DeleteObjectVersion`.

### 10.4. Versioning không phải backup hoàn chỉnh

Versioning bảo vệ tốt trước overwrite/delete nhầm, nhưng người có quyền xóa version vẫn có thể phá hủy dữ liệu. Backup mạnh hơn thường kết hợp cross-account replication, Object Lock, restrictive policy và giám sát.

### 10.5. MFA Delete

MFA Delete yêu cầu MFA cho một số thao tác versioning/xóa version. Đây là lớp bảo vệ bổ sung nhưng có giới hạn vận hành và thường được cấu hình bởi root qua CLI/API. Trong đề thi, hãy nhận ra nó bảo vệ thao tác xóa nhạy cảm, không thay thế IAM hay Object Lock.

---

## 11. S3 Object Lock — WORM

Object Lock cung cấp mô hình Write Once Read Many trên từng object version và cần versioning.

### 11.1. Retention period

- **Governance mode:** principal có quyền `s3:BypassGovernanceRetention` và gửi header bypass có thể rút ngắn/xóa.
- **Compliance mode:** không thể rút ngắn hoặc xóa protected version trong retention, kể cả root user. Phải chờ hết hạn.

Compliance mode rất mạnh; cấu hình sai có thể khiến dữ liệu không thể xóa và tiếp tục phát sinh chi phí.

### 11.2. Legal hold

Legal hold không có ngày hết hạn cố định. Nó tồn tại đến khi principal có quyền gỡ bỏ. Retention và legal hold có thể cùng áp lên một version.

### 11.3. Object Lock khác Glacier Vault Lock

Trong bài S3 hiện đại, Object Lock là đáp án phổ biến cho WORM theo object. Storage class Glacier không tự tạo WORM; archive rẻ và chống xóa là hai yêu cầu khác nhau.

---

## 12. Mã hóa dữ liệu

S3 cần bảo vệ dữ liệu **in transit** và **at rest**.

### 12.1. In transit

Dùng HTTPS/TLS. Có thể dùng bucket policy explicit Deny khi `aws:SecureTransport` là `false`. Mã hóa at rest không bảo vệ dữ liệu đang truyền qua HTTP.

### 12.2. SSE-S3

S3 quản lý key hoàn toàn. Đây là lựa chọn đơn giản, ít phụ thuộc và hiện là nền tảng mã hóa mặc định cho object mới.

### 12.3. SSE-KMS

S3 dùng KMS key. Lợi ích:

- kiểm soát key policy;
- audit hoạt động key qua CloudTrail;
- phân tách quyền đọc object và quyền dùng key;
- hỗ trợ customer managed key cho yêu cầu compliance.

Principal thường cần cả S3 permission lẫn KMS permission. Upload có thể cần `kms:GenerateDataKey`; download cần `kms:Decrypt`. Nếu chỉ cho `s3:GetObject` mà key policy không cho decrypt, GET vẫn thất bại.

Trade-off là KMS request cost, quota và dependency. Với request rate cao, cân nhắc **S3 Bucket Key** để giảm số request trực tiếp tới KMS và chi phí, đồng thời hiểu sự thay đổi encryption context khi viết policy.

### 12.4. DSSE-KMS

Dual-layer server-side encryption with AWS KMS dùng hai lớp mã hóa độc lập ở phía server, phù hợp yêu cầu compliance cần hai lớp cryptographic protection. Không chọn nếu đề chỉ yêu cầu “mã hóa bằng customer managed key”; SSE-KMS thường đủ và đơn giản hơn.

### 12.5. SSE-C

Client gửi encryption key trong mỗi request; S3 dùng key để mã hóa/giải mã nhưng không lưu key. Mất key đồng nghĩa mất khả năng đọc dữ liệu. Từ tháng 4/2026, SSE-C bị vô hiệu hóa mặc định cho general purpose bucket mới và nhiều bucket chưa dùng SSE-C; workload thật sự cần phải chủ động bật theo cơ chế hiện hành.

SSE-C có gánh nặng vận hành lớn và không phải “customer managed KMS key”. Hai khái niệm hoàn toàn khác nhau.

### 12.6. Client-side encryption

Ứng dụng mã hóa trước khi upload, nên S3 chỉ thấy ciphertext. Khách hàng chịu trách nhiệm key, thuật toán, metadata và rotation. Dùng khi yêu cầu không cho AWS thấy plaintext, nhưng complexity cao hơn.

### 12.7. Default encryption

Object mới được S3 mã hóa mặc định bằng SSE-S3 nếu không chỉ định kiểu khác. Cấu hình default encryption thành SSE-KMS giúp chuẩn hóa key mong muốn. Thay default encryption **không tự mã hóa lại object cũ**; muốn đổi key/algorithm phải copy hoặc dùng Batch Operations phù hợp.

---

## 13. Mô hình cấp quyền S3

Quyết định Allow/Deny có thể liên quan nhiều lớp:

~~~text
Organization SCP
    ↓
IAM identity policy / permission boundary / session policy
    ↓
Bucket policy / Access Point policy
    ↓
Block Public Access
    ↓
KMS key policy (nếu SSE-KMS)
~~~

Một explicit Deny ở bất kỳ lớp áp dụng nào thường thắng Allow. Vì vậy lỗi `AccessDenied` không nên chỉ nhìn IAM policy.

### 13.1. IAM policy và bucket policy

- IAM policy gắn với user/role và nói principal được làm gì.
- Bucket policy gắn với bucket và nói ai được làm gì trên resource đó.

Cross-account thường cần trust ở cả hai phía: resource policy cho principal ngoài account và identity policy cho principal, trừ những trường hợp AWS policy evaluation cho phép khác. Nếu object dùng customer managed KMS key, còn cần key policy/grant.

### 13.2. Block Public Access

Bốn thiết lập Block Public Access ngăn public ACL/policy ở account, bucket hoặc access point. Với bucket private, nên bật ở account và bucket khi có thể.

Block Public Access không phải firewall chặn mọi truy cập. IAM role hợp lệ hoặc CloudFront OAC vẫn có thể truy cập private bucket nếu policy cho phép.

### 13.3. Object Ownership và ACL

Thiết kế mới nên dùng **Bucket owner enforced** để vô hiệu ACL và để bucket owner sở hữu object. Dùng policy thay cho ACL giúp mô hình quyền dễ hiểu hơn. Khi tạo bucket bằng IaC/CLI, nên khai báo rõ thay vì phụ thuộc mặc định của công cụ.

ACL là cơ chế legacy. Chỉ dùng khi có integration cũ thực sự cần; “public-read ACL” thường không hoạt động nếu Block Public Access đang chặn, và không nên tắt guardrail chỉ để sửa lỗi thiếu hiểu biết.

### 13.4. Ví dụ bắt buộc HTTPS

~~~json
{
  "Version": "2012-10-17",
  "Statement": [{
    "Sid": "DenyInsecureTransport",
    "Effect": "Deny",
    "Principal": "*",
    "Action": "s3:*",
    "Resource": [
      "arn:aws:s3:::company-data",
      "arn:aws:s3:::company-data/*"
    ],
    "Condition": {
      "Bool": { "aws:SecureTransport": "false" }
    }
  }]
}
~~~

### 13.5. Access Points

Một bucket dùng chung cho analytics, app và audit có thể dẫn đến bucket policy khổng lồ. Access Point tạo endpoint và policy riêng cho từng workload:

~~~text
Bucket data-lake
├── access point analytics-read
├── access point ingestion-write
└── access point audit-read
~~~

Access Point không tự cấp quyền; effective permission vẫn là giao của các policy liên quan. Có thể giới hạn access point chỉ hoạt động từ VPC.

### 13.6. CORS không phải authorization

CORS chỉ hướng dẫn browser cho phép JavaScript từ origin nào gọi endpoint. Nó không cấp `s3:GetObject` hay `PutObject`. Request vẫn cần IAM, bucket policy hoặc chữ ký hợp lệ.

---

## 14. Private access từ VPC

**Gateway VPC endpoint for S3** cho phép resource trong VPC đi tới S3 qua mạng AWS mà không cần Internet Gateway hay NAT Gateway. Gateway endpoint không tính phí theo giờ/data-processing như nhiều interface endpoint, nhưng có phạm vi và routing riêng.

Ta có thể kết hợp:

- route table tới S3 prefix list;
- endpoint policy giới hạn bucket/action;
- bucket policy dùng `aws:SourceVpce` hoặc điều kiện phù hợp;
- IAM role trên EC2/ECS/Lambda.

Đừng hard-code access key trên EC2. Dùng instance profile/task role/execution role thích hợp.

Một bucket policy giới hạn sai VPC endpoint có thể khóa cả console access vì request console không đi qua endpoint đó. Luôn chuẩn bị đường khôi phục và kiểm thử policy trước khi áp rộng.

---

## 15. Presigned URL và presigned POST

Presigned URL là request đã ký có thời hạn. Client không cần AWS credential trực tiếp, nhưng URL mang quyền tạm thời của principal ký.

### 15.1. Flow upload an toàn

~~~text
1. Client gọi API: xin upload avatar.jpg
2. API xác thực user, sinh key ngẫu nhiên và presigned URL
3. Client PUT trực tiếp tới S3
4. S3 kiểm tra chữ ký, thời hạn và policy
5. S3 phát event để xử lý ảnh
~~~

App server không phải nhận rồi chuyển tiếp file, nên giảm bandwidth và tải compute.

### 15.2. Thời hạn

Console cho phép thời hạn ngắn hơn; SDK/CLI dùng SigV4 có thể tạo URL tối đa 7 ngày khi dùng long-lived IAM user credential. Nếu ký bằng temporary credential, URL hết hiệu lực khi credential hết hạn dù URL ghi thời hạn dài hơn.

Trong production nên ký bằng IAM role temporary credential và để expiry ngắn theo nhu cầu, không tạo IAM user key dài hạn chỉ để đạt 7 ngày.

### 15.3. URL là bearer token

Ai có URL có thể dùng quyền đã ký trong thời hạn. Không đưa URL vào log công khai, analytics, chat hoặc referrer không kiểm soát. Có thể hạn chế thêm bằng policy như signature age, network path hoặc điều kiện request.

### 15.4. Presigned POST

Presigned POST phù hợp browser form upload và có policy condition như:

- key phải bắt đầu bằng prefix nhất định;
- `content-length-range`;
- content type;
- yêu cầu encryption.

Không tin `Content-Type` do client gửi để kết luận file an toàn. Hãy kiểm tra magic bytes, scan malware và xử lý file ở vùng quarantine.

---

## 16. Multipart upload

Multipart chia file thành các part upload độc lập, có thể song song và retry riêng:

~~~text
CreateMultipartUpload
   ├── UploadPart 1
   ├── UploadPart 2
   ├── UploadPart 3
   └── CompleteMultipartUpload
~~~

Lợi ích:

- tăng throughput nhờ song song;
- lỗi một part chỉ upload lại part đó;
- pause/resume dễ hơn ở tầng client;
- bắt buộc thực tế cho object rất lớn.

Các giới hạn quan trọng:

- tối đa 10.000 part;
- mỗi part 5 MiB–5 GiB, part cuối có thể nhỏ hơn;
- object tối đa 48,8 TiB;
- cân nhắc multipart từ khoảng 100 MB.

Chỉ khi gọi Complete thì object hoàn chỉnh mới xuất hiện. Nếu bỏ dở, các part vẫn chiếm storage và tính phí; dùng lifecycle abort incomplete multipart upload.

Part size phải được chọn sao cho số part không vượt 10.000. File 40 TiB không thể chia part 100 MiB.

---

## 17. Hiệu năng và truyền dữ liệu

### 17.1. Request rate theo prefix

S3 general purpose hỗ trợ ít nhất khoảng 3.500 request ghi (`PUT/COPY/POST/DELETE`) hoặc 5.500 request đọc (`GET/HEAD`) mỗi giây cho mỗi partitioned prefix. Có thể dùng nhiều prefix để scale ngang. S3 tự thích nghi nhưng tăng tải đột ngột có thể tạm gặp `503 Slow Down`; client phải retry với backoff.

Không còn cần thêm chuỗi hash ngẫu nhiên vào đầu key chỉ vì giới hạn tuần tự kiểu cũ. Hãy chọn key dễ quản lý, rồi phân tán khi access pattern thật sự đòi hỏi.

### 17.2. Byte-range GET

Với object lớn, client có thể tải các byte range song song. Điều này tăng throughput, hỗ trợ resume và chỉ đọc phần cần thiết của format có cấu trúc.

### 17.3. S3 Transfer Acceleration

Client upload/download qua edge location gần nhất rồi dữ liệu đi trên mạng backbone AWS tới bucket. Phù hợp người dùng phân tán toàn cầu và đường truyền xa Region. Có thêm phí, không đảm bảo mọi tuyến đều nhanh hơn; dùng speed comparison và đo thực tế.

### 17.4. CloudFront khác Transfer Acceleration

- CloudFront cache và phân phối nội dung được đọc nhiều tới end user.
- Transfer Acceleration tối ưu truyền object tới/từ chính bucket, đặc biệt upload khoảng cách xa.
- Multipart tối ưu cách chia và retry file lớn.

Ba công cụ có thể bổ sung nhau nhưng giải quyết vấn đề khác nhau.

---

## 18. Replication

### 18.1. SRR và CRR

- Same-Region Replication: đích trong cùng Region;
- Cross-Region Replication: đích ở Region khác.

Use case gồm compliance, data locality, cross-account isolation, giảm latency đọc hoặc DR. Source và destination bucket cần versioning.

### 18.2. Replication là asynchronous

Replication thông thường không đồng bộ với PUT gốc. Object đã ghi thành công ở source nhưng có thể chưa xuất hiện tại destination. Nếu cần mục tiêu thời gian chặt chẽ, S3 Replication Time Control được thiết kế để replicate 99,99% object mới trong 15 phút theo SLA áp dụng.

### 18.3. Existing objects

Rule live replication chủ yếu áp dụng object mới sau khi cấu hình. Để replicate object đã tồn tại hoặc retry dữ liệu cũ, dùng S3 Batch Replication/Batch Operations theo nhu cầu.

### 18.4. Delete và delete marker

Delete marker replication có thể bật riêng và mặc định không nên được giả định. Xóa vĩnh viễn một version ở source không nhất thiết được replicate sang destination—đây là lớp bảo vệ trước xóa nhầm. Hãy đọc chính xác requirement thay vì nghĩ hai bucket là mirror block-level.

### 18.5. Encryption và ownership

Replication SSE-KMS cần cấu hình key đích, replication role và KMS permissions. Cross-account nên đặt Object Ownership phù hợp để account đích kiểm soát bản sao. Replication thất bại thường đến từ IAM/KMS policy hơn là “S3 hỏng”.

### 18.6. Không tự chain như suy đoán

Nếu A replicate sang B và B replicate sang C, object đến B bằng replication không mặc nhiên tiếp tục sang C theo cách người mới thường giả định. Thiết kế fan-out rõ ràng từ source hoặc dùng cơ chế thích hợp.

---

## 19. Event Notifications và kiến trúc event-driven

S3 có thể phát event khi object được tạo, xóa, restore, đổi tag hoặc hoàn tất replication tùy loại event.

### 19.1. Destination trực tiếp

- Lambda;
- SQS Standard;
- SNS Standard;
- EventBridge.

Destination trực tiếp thường phải cùng Region với bucket. S3 không gửi trực tiếp tới SQS FIFO hoặc SNS FIFO; nếu cần SQS FIFO, route qua EventBridge.

### 19.2. Delivery semantics

S3 Event Notifications được thiết kế **at least once**, có thể trùng lặp và không đảm bảo thứ tự tổng quát. Consumer phải idempotent.

Một idempotency key có thể kết hợp bucket, object key, version ID và event sequencer. Không chỉ dựa vào timestamp.

### 19.3. Tránh recursive loop

Nếu Lambda đọc object rồi ghi kết quả về đúng prefix đang trigger:

~~~text
S3 upload -> Lambda -> S3 upload -> Lambda -> ...
~~~

Hãy dùng bucket khác hoặc prefix `incoming/` và `processed/`, rồi filter event chỉ cho `incoming/`.

### 19.4. SQS làm buffer

~~~text
S3 -> SQS -> worker fleet
~~~

SQS hấp thụ burst, retry và giúp worker scale độc lập. Đây thường an toàn hơn invoke Lambda trực tiếp nếu downstream chậm hoặc cần kiểm soát backpressure.

### 19.5. EventBridge khi nào?

Chọn EventBridge khi cần rule lọc phong phú, nhiều target, cross-account routing hoặc tích hợp rộng. Khi bật EventBridge cho bucket, các event S3 được gửi tới bus rồi rule quyết định target.

---

## 20. Phân phối website và nội dung

### 20.1. Static website endpoint

S3 có website hosting endpoint nhưng endpoint website chỉ phục vụ HTTP và thường cần nội dung public. Đây không phải lựa chọn tốt cho website production cần bucket private và HTTPS.

### 20.2. CloudFront + private S3 + OAC

Thiết kế khuyến nghị:

~~~text
Browser --HTTPS--> CloudFront --OAC--> private S3 bucket
~~~

CloudFront cung cấp HTTPS, cache, custom domain, WAF và signed URL/cookie. Origin Access Control cho phép bucket policy chỉ tin CloudFront distribution. Giữ Block Public Access bật.

OAC thường dùng với S3 REST endpoint, không phải website endpoint theo cùng cơ chế. Đừng bật public bucket chỉ vì CloudFront báo `AccessDenied`; hãy sửa OAC và bucket policy.

### 20.3. Presigned URL và CloudFront signed URL

- S3 presigned URL: cấp quyền trực tiếp cho một S3 operation/object.
- CloudFront signed URL/cookie: kiểm soát nội dung phân phối qua CDN, phù hợp nhiều file hoặc cache toàn cầu.

---

## 21. Logging, audit và inventory

### 21.1. CloudTrail

Bucket-level management event và object-level data event là hai nhóm khác nhau. S3 object data events có thể rất nhiều và không mặc định được log giống mọi management event; cần cấu hình trail/event data store phù hợp và tính chi phí.

### 21.2. Server access logging

Ghi request access log tới bucket đích. Hữu ích cho phân tích truy cập nhưng delivery là best effort và log có độ trễ; không dùng nó làm nguồn audit duy nhất cho requirement nghiêm ngặt.

### 21.3. S3 Inventory

Tạo danh sách object theo lịch, gồm metadata như size, storage class, encryption hoặc replication status. Dùng Inventory thay vì chạy LIST/HEAD hàng tỷ object để audit hoặc làm manifest cho Batch Operations.

### 21.4. Storage Lens

Cung cấp số liệu tổng hợp về dung lượng, object count, activity, cost efficiency và data protection trên nhiều bucket/account. Dùng để tìm noncurrent versions, incomplete multipart upload hoặc bucket không bật protection.

### 21.5. Macie

Amazon Macie dùng machine learning/pattern matching để khám phá dữ liệu nhạy cảm trong S3 và đánh giá security posture. Macie không thay thế encryption hay policy; nó giúp phát hiện và phân loại rủi ro.

---

## 22. Chi phí S3 phải tính những gì?

Không chỉ có GB-tháng. Tổng chi phí có thể gồm:

- storage theo class và dung lượng;
- request PUT/GET/LIST/lifecycle;
- retrieval và early deletion;
- data transfer ra Internet hoặc cross-Region;
- replication;
- KMS API;
- Intelligent-Tiering monitoring;
- Inventory, Storage Lens nâng cao, CloudTrail data events;
- Transfer Acceleration;
- giữ nhiều noncurrent version và incomplete multipart part.

Ví dụ: một triệu object 10 KB chuyển sang Standard-IA có thể không tiết kiệm như tưởng tượng vì minimum billable size, request transition và minimum duration. “Storage class rẻ nhất mỗi GB” không đồng nghĩa “kiến trúc rẻ nhất”.

Các cách tối ưu phổ biến:

- lifecycle theo access pattern thật;
- xóa noncurrent version đúng retention;
- abort multipart bỏ dở;
- gom object rất nhỏ khi workload cho phép;
- dùng CloudFront giảm GET tới origin và data transfer phù hợp;
- S3 Bucket Key cho SSE-KMS request volume lớn;
- nén đúng loại dữ liệu;
- quan sát bằng Storage Lens và Cost Explorer.

---

## 23. Các kiến trúc mẫu

### 23.1. Upload trực tiếp và xử lý bất đồng bộ

~~~text
Client -> API xin presigned URL
Client -> S3 quarantine/incoming
S3 -> SQS -> Lambda/ECS scan và xử lý
Worker -> S3 processed
Metadata -> DynamoDB/RDS
~~~

Ưu điểm: application server không gánh file, SQS hấp thụ burst, tách vùng chưa kiểm duyệt khỏi vùng phát hành.

### 23.2. Static content an toàn

~~~text
User -> Route 53 -> CloudFront + WAF -> OAC -> private S3
~~~

Không cần public bucket. CloudFront cache ở edge; invalidation hoặc versioned filename giải quyết cập nhật nội dung.

### 23.3. Backup chống ransomware

~~~text
Production account S3
  └── CRR/SRR -> Backup account S3
                    ├── Object Lock
                    ├── restrictive role
                    └── lifecycle -> Deep Archive
~~~

Tách account và quyền giúp credential production bị chiếm không dễ xóa cả backup. Phải diễn tập restore; “replicated” không đồng nghĩa “khôi phục được đúng RTO”.

### 23.4. Data lake

~~~text
raw/       -> dữ liệu nguyên bản, immutable
curated/   -> dữ liệu đã làm sạch
analytics/ -> output truy vấn
~~~

Dùng prefix/Access Point/policy tách workload, định dạng cột như Parquet để giảm dữ liệu scan, lifecycle theo từng zone và Glue/Lake Formation khi cần catalog/governance.

---

## 24. Cách điều tra lỗi `AccessDenied`

Kiểm tra theo thứ tự:

1. Principal thật sự là ai? IAM role, assumed-role session hay anonymous?
2. Action và resource ARN có đúng không?
3. Có explicit Deny trong SCP, IAM, bucket policy, VPC endpoint policy không?
4. Block Public Access có chặn policy/ACL public không?
5. Object Ownership/ACL có gây khác biệt với object cũ không?
6. Nếu SSE-KMS, principal và S3 có quyền dùng đúng KMS key không?
7. Presigned URL có hết hạn, credential signer bị revoke hoặc header bị thay đổi không?
8. Request có đi qua đúng endpoint/Region/VPC endpoint không?
9. CloudTrail có ghi reason/context gì?

`403 AccessDenied` sau khi cấp `s3:GetObject` thường là dấu hiệu phải nhìn toàn bộ policy evaluation, đặc biệt KMS và explicit Deny.

---

## 25. Bẫy SAA-C03 thường gặp

1. **“S3 có folder.”** General purpose S3 có key và prefix; console mô phỏng folder.
2. **“S3 eventual consistency.”** Đây là kiến thức cũ; PUT/overwrite/DELETE và LIST hiện có strong consistency.
3. **“11 số 9 nghĩa là luôn truy cập được.”** Đó là durability, không phải availability.
4. **“Versioning sẽ giải phóng dung lượng khi DELETE.”** DELETE thường chỉ thêm delete marker; version cũ vẫn tốn tiền.
5. **“Glacier nghĩa là phải chờ nhiều giờ.”** Glacier Instant Retrieval trả về ở millisecond; Flexible/Deep cần restore.
6. **“SSE-KMS chỉ cần S3 permission.”** Cần cả KMS permission/key policy phù hợp.
7. **“Default encryption sửa toàn bộ dữ liệu cũ.”** Chỉ tự áp cho object ghi mới.
8. **“CORS cấp quyền upload.”** CORS không phải authorization.
9. **“Presigned URL có quyền riêng.”** Nó dùng quyền và lifetime hiệu dụng của signer.
10. **“S3 event exactly-once và đúng thứ tự.”** Consumer phải chịu duplicate/out-of-order.
11. **“CRR replicate luôn object cũ.”** Cần Batch Replication cho existing objects.
12. **“Lifecycle luôn giảm chi phí.”** Minimum size, duration, request và retrieval có thể làm tổng chi phí tăng.
13. **“CloudFront yêu cầu public bucket.”** OAC cho phép private origin.
14. **“S3 object tối đa 5 TB.”** Đây là giới hạn cũ; hiện tài liệu AWS nêu khoảng 50 TB.

---

## 26. Bài lab gợi ý

### Lab 1 — Private bucket chuẩn an toàn

- tạo bucket có tên ngẫu nhiên;
- bật Block Public Access;
- đặt Object Ownership là Bucket owner enforced;
- bật versioning;
- đặt default SSE-KMS hoặc SSE-S3;
- thêm policy Deny non-TLS;
- upload, overwrite, delete rồi khôi phục version.

### Lab 2 — Lifecycle và version

- tạo nhiều version của cùng key;
- quan sát delete marker;
- tạo lifecycle cho noncurrent version;
- thêm abort incomplete multipart upload;
- kiểm tra rule có filter kích thước/prefix đúng không.

### Lab 3 — Presigned upload

- API tạo key server-side, không nhận key tùy ý từ client;
- sinh URL expiry 5 phút;
- giới hạn size/type bằng presigned POST nếu phù hợp;
- upload trực tiếp;
- xác minh object vẫn private;
- thử dùng URL sau khi credential/URL hết hạn.

### Lab 4 — Event idempotent

- S3 gửi event tới SQS;
- worker lưu idempotency key;
- gửi lại cùng message và chứng minh không xử lý hai lần;
- cấu hình DLQ;
- thử upload vào prefix output và xác minh không tạo loop.

> Sau lab, xóa tài nguyên có phí và kiểm tra KMS key, CloudTrail data event, replication hoặc Object Lock trước khi dọn. Object đang compliance retention có thể không xóa được.

---

## 27. Câu hỏi tự kiểm tra

### Câu 1

Ảnh sản phẩm được đọc liên tục trên toàn cầu, bucket phải private. Thiết kế nào phù hợp?

**Đáp án:** CloudFront với OAC tới private S3, Block Public Access bật. Route 53 có thể trỏ domain tới CloudFront.

### Câu 2

Backup giữ 10 năm, gần như không đọc, chấp nhận chờ 24 giờ khi restore. Chọn class nào?

**Đáp án:** Glacier Deep Archive, đồng thời kiểm tra minimum duration, Object Lock và lifecycle.

### Câu 3

Người dùng cần upload video 30 GB mà không đi qua application server.

**Đáp án:** multipart upload trực tiếp bằng presigned URLs/temporary credentials; lifecycle abort upload bỏ dở.

### Câu 4

Object dùng SSE-KMS. Role có `s3:GetObject` nhưng vẫn nhận AccessDenied.

**Đáp án:** kiểm tra `kms:Decrypt`, KMS key policy/grant, explicit Deny và encryption context.

### Câu 5

Cần xử lý event theo kiểu chống mất burst, downstream có thể chậm.

**Đáp án:** S3 → SQS Standard → consumer idempotent; cấu hình visibility timeout, retry và DLQ.

### Câu 6

DELETE object trong bucket versioned nhưng hóa đơn vẫn phải khôi phục được.

**Đáp án:** DELETE thường tạo delete marker; xóa marker hoặc copy version cũ thành version mới. Quy trình cần tránh `DeleteObjectVersion` ngoài ý muốn.

### Câu 7

Dữ liệu ít đọc nhưng mỗi lần đọc phải phản hồi millisecond. “Glacier” nào có thể phù hợp?

**Đáp án:** Glacier Instant Retrieval. Không chọn Flexible/Deep nếu không chấp nhận restore delay.

### Câu 8

S3 gửi hai event giống nhau. Đây có phải lỗi dịch vụ không?

**Đáp án:** Không nhất thiết. Event Notifications là at-least-once; consumer phải idempotent.

---

## 28. Checklist thiết kế production

### Data và vòng đời

- [ ] Đã xác định access frequency, latency và retention chưa?
- [ ] Storage class có minimum duration/retrieval fee phù hợp không?
- [ ] Lifecycle xử lý cả current và noncurrent version chưa?
- [ ] Có abort incomplete multipart upload không?
- [ ] Đã kiểm tra object nhỏ và minimum billable size chưa?

### Security

- [ ] Block Public Access bật ở scope phù hợp chưa?
- [ ] Object Ownership có vô hiệu ACL không?
- [ ] Chỉ dùng IAM role, không hard-code key?
- [ ] Bucket policy có least privilege và bắt buộc TLS?
- [ ] SSE-KMS có đúng key policy, rotation và quota?
- [ ] Presigned URL ngắn hạn, key do server kiểm soát?

### Reliability

- [ ] Versioning có bật và lifecycle không xóa quá sớm?
- [ ] Có cần Object Lock/cross-account replication không?
- [ ] Replication có giám sát failure không?
- [ ] Đã diễn tập restore theo RPO/RTO?
- [ ] Event consumer có idempotency, retry và DLQ?

### Cost và vận hành

- [ ] Tính cả request, retrieval, transfer và KMS?
- [ ] Có Inventory/Storage Lens để quan sát quy mô lớn?
- [ ] CloudTrail data events có bật cho dữ liệu quan trọng?
- [ ] Có alert cho public access, policy change và replication failure?

---

## 29. Cheat sheet

| Yêu cầu | Câu trả lời thường phù hợp |
|---|---|
| Object hot, truy cập thường xuyên | S3 Standard |
| Không biết access pattern | Intelligent-Tiering |
| Ít truy cập, lấy ngay, multi-AZ | Standard-IA |
| Có thể tái tạo, chấp nhận một AZ | One Zone-IA |
| Archive nhưng GET millisecond | Glacier Instant Retrieval |
| Archive, chờ phút/giờ | Glacier Flexible Retrieval |
| Archive dài hạn, chi phí thấp nhất | Deep Archive |
| Khôi phục overwrite/delete | Versioning |
| WORM/compliance | Object Lock |
| Upload tạm thời không cấp credential | Presigned URL/POST |
| File lớn | Multipart upload |
| Người dùng toàn cầu upload xa Region | Transfer Acceleration |
| Phân phối nội dung cache toàn cầu | CloudFront |
| DR/đặt bản sao Region khác | CRR |
| Bản sao cùng Region/account khác | SRR/cross-account replication |
| Buffer event và xử lý burst | S3 → SQS → consumer |
| Nhiều rule/target | S3 → EventBridge |
| Truy cập S3 private từ VPC | Gateway VPC endpoint |

---

## 30. Tài liệu AWS chính thức

- [Amazon S3 User Guide — What is Amazon S3?](https://docs.aws.amazon.com/AmazonS3/latest/userguide/Welcome.html)
- [Working with Amazon S3 objects](https://docs.aws.amazon.com/AmazonS3/latest/userguide/UsingObjects.html)
- [S3 storage classes](https://docs.aws.amazon.com/AmazonS3/latest/userguide/storage-class-intro.html)
- [S3 Lifecycle transition considerations](https://docs.aws.amazon.com/AmazonS3/latest/userguide/lifecycle-transition-general-considerations.html)
- [Using versioning in S3 buckets](https://docs.aws.amazon.com/AmazonS3/latest/userguide/Versioning.html)
- [S3 Object Lock](https://docs.aws.amazon.com/AmazonS3/latest/userguide/object-lock.html)
- [S3 data protection and encryption](https://docs.aws.amazon.com/AmazonS3/latest/userguide/DataDurability.html)
- [S3 Object Ownership](https://docs.aws.amazon.com/AmazonS3/latest/userguide/about-object-ownership.html)
- [Block Public Access](https://docs.aws.amazon.com/AmazonS3/latest/userguide/access-control-block-public-access.html)
- [Presigned URLs](https://docs.aws.amazon.com/AmazonS3/latest/userguide/using-presigned-url.html)
- [Multipart upload limits](https://docs.aws.amazon.com/AmazonS3/latest/userguide/qfacts.html)
- [S3 replication](https://docs.aws.amazon.com/AmazonS3/latest/userguide/replication.html)
- [S3 Event Notifications](https://docs.aws.amazon.com/AmazonS3/latest/userguide/EventNotifications.html)
- [S3 performance guidelines](https://docs.aws.amazon.com/AmazonS3/latest/userguide/optimizing-performance.html)

> Các quota, giá, SLA và tính năng AWS có thể thay đổi. Khi triển khai production, hãy kiểm tra tài liệu và trang pricing của Region tại thời điểm thực hiện.

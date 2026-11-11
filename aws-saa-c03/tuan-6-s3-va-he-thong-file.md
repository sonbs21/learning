# Tuần 6 — S3 và hệ thống file

> Mục tiêu: chọn đúng storage theo access pattern, latency, durability, availability và cost; thiết kế S3 private/public access an toàn; phân biệt object, block và file storage.

## 1. Kết quả cần đạt

- Giải thích bucket, object, key, prefix và consistency hiện hành của S3.
- Chọn Standard, Intelligent-Tiering, Standard-IA, One Zone-IA và các Glacier class.
- Thiết kế versioning, delete marker, lifecycle và Object Lock.
- Phân biệt SSE-S3, SSE-KMS và SSE-C.
- Viết bucket policy least privilege và dùng Block Public Access.
- Dùng presigned URL, multipart upload và Transfer Acceleration đúng use case.
- Thiết kế Same-Region Replication và Cross-Region Replication.
- Gửi S3 event tới SNS, SQS, Lambda hoặc EventBridge.
- Phân biệt EBS block storage, EFS shared file system và S3 object storage.
- Nhận biết use case của FSx for Windows, Lustre, OpenZFS và ONTAP.
- Hiểu Storage Gateway và DataSync ở mức migration.
- Chọn storage theo access pattern thay vì chỉ theo dung lượng.

## 2. Lịch học gợi ý

| Buổi | Nội dung | Thời lượng |
|---|---|---:|
| 1 | S3 object model, consistency và storage class | 3 giờ |
| 2 | Versioning, lifecycle, Object Lock và encryption | 2–3 giờ |
| 3 | Policy, presigned URL, multipart và replication | 3 giờ |
| 4 | Event integration, EBS, EFS và FSx | 3 giờ |
| 5 | Storage Gateway, DataSync, lab và câu hỏi thi | 2 giờ |

Cách suy luận: xác định object/block/file, access pattern, latency, sharing, retention, durability, RPO/RTO và cost trước khi chọn dịch vụ.

---

## 3. S3 object storage

Amazon S3 lưu dữ liệu dưới dạng object trong bucket. S3 phù hợp dữ liệu không cần mount như filesystem và cần durability, scale, lifecycle hoặc truy cập qua API.

### 3.1. Bucket, object, key và prefix

- **Bucket:** namespace chứa object, tên cần global unique.
- **Object:** dữ liệu + metadata + key.
- **Key:** chuỗi định danh object; không phải thư mục vật lý.
- **Prefix:** phần đầu của key, dùng để tổ chức/list/filter.
- **Metadata:** system metadata và user-defined metadata.
- **Tag:** key-value dùng cho quản lý/lifecycle/cost theo khả năng hỗ trợ.

Ví dụ key:

~~~text
logs/2026/10/10/app-001.json
images/products/sku-123/front.jpg
~~~

Prefix giúp tạo logical hierarchy nhưng S3 không có directory giống filesystem POSIX.

### 3.2. S3 consistency

S3 cung cấp strong read-after-write consistency cho PUT và DELETE object trong mọi Region: sau khi request thành công, GET/LIST phản ánh thay đổi theo mô hình dịch vụ hiện hành.

Dù consistency mạnh, ứng dụng vẫn phải xử lý retry, idempotency, versioning, concurrent update và event delivery semantics. Consistency không biến S3 thành transactional database.

### 3.3. Durability, availability và access

S3 Standard được thiết kế cho durability rất cao và availability theo SLA dịch vụ. Durability không đồng nghĩa object luôn phục vụ được ở mọi thời điểm; hãy phân biệt hai khái niệm.

S3 scale theo object/request nhưng application vẫn phải quan tâm request rate, prefix/access pattern, network, multipart và cost request/data transfer.

### 3.4. S3 không phải filesystem

S3 không cung cấp semantics POSIX như lock, append ngẫu nhiên hoặc mount local theo mặc định. Dùng S3 cho object/file content, backup, data lake, static asset và archive; dùng EFS/FSx cho ứng dụng cần filesystem semantics.

---

## 4. S3 storage classes

Chọn storage class theo frequency, latency, minimum storage duration, retrieval cost, availability và cost.

| Class | Đặc điểm | Use case |
|---|---|---|
| Standard | truy cập thường xuyên, latency thấp | active content, web asset, data lake hot |
| Intelligent-Tiering | tự chuyển tier theo access pattern | pattern không đoán trước |
| Standard-IA | ít truy cập nhưng cần truy cập nhanh | backup/DR, dữ liệu không thường xuyên |
| One Zone-IA | IA trong một AZ, rẻ hơn | dữ liệu có thể recreate, secondary copy |
| Glacier Instant Retrieval | archive nhưng cần millisecond access | archive thường xuyên cần đọc |
| Glacier Flexible Retrieval | archive, retrieval từ nhanh tới bulk | backup dài hạn |
| Glacier Deep Archive | chi phí lưu thấp nhất, retrieval chậm | compliance/archive hiếm đọc |

Không chọn One Zone-IA cho bản copy duy nhất của dữ liệu quan trọng nếu mất một AZ là không chấp nhận được. IA/Glacier có minimum storage duration và retrieval/request cost cần tính.

### 4.1. Intelligent-Tiering

Intelligent-Tiering phù hợp access pattern không biết trước. S3 tự chuyển object giữa các tier theo access, nhưng vẫn có monitoring/automation charge và không thay thế lifecycle cho dữ liệu có pattern rõ.

### 4.2. Glacier retrieval

Glacier classes có trade-off giữa storage cost và retrieval time/cost. Đề thi thường hỏi “lưu dài hạn, hiếm truy cập, cost thấp” → Deep Archive; “archive nhưng cần đọc nhanh” → Instant Retrieval hoặc Flexible tùy latency.

### 4.3. Lifecycle và storage class

Lifecycle rule có thể chuyển object theo age hoặc expire/delete. Một rule tốt thường gồm:

~~~text
Day 0      → Standard
Day 30     → Standard-IA
Day 90     → Glacier Flexible
Day 365    → Deep Archive hoặc expire
~~~

Chỉ dùng các transition phù hợp minimum duration và business retention. Không transition object nhỏ, tạm thời sang class có overhead nếu cost tổng tăng.

---

## 5. Versioning, delete marker và Object Lock

### 5.1. Versioning

Versioning giữ nhiều phiên bản của cùng key. Nó giúp:

- phục hồi object bị overwrite/delete;
- hỗ trợ replication;
- bảo vệ khỏi thao tác nhầm;
- giữ lịch sử dữ liệu.

Khi versioning bật, DELETE thông thường tạo delete marker thay vì xóa vĩnh viễn version cũ. Xóa vĩnh viễn cần chỉ định version ID và permission phù hợp.

Versioning không tự dọn version cũ. Dùng lifecycle cho noncurrent versions và delete markers khi retention cho phép.

### 5.2. Object Lock

Object Lock cung cấp WORM: Write Once Read Many, ngăn object bị overwrite/delete trong thời gian retention.

- **Governance mode:** người có permission đặc biệt có thể bypass.
- **Compliance mode:** retention khó bypass, phù hợp compliance nghiêm ngặt.
- **Legal hold:** giữ object đến khi gỡ hold, không dựa riêng vào thời gian.

Object Lock thường cần versioning và phải thiết kế cùng backup, legal requirement, key management và recovery process. Không bật retention tùy tiện cho bucket lab/production nếu chưa có quy trình xóa hợp lệ.

### 5.3. Delete protection

Bảo vệ dữ liệu quan trọng bằng kết hợp:

- versioning;
- MFA Delete trong use case hỗ trợ;
- Object Lock;
- restrictive bucket policy;
- separate backup/account;
- CloudTrail và alert.

Versioning không bảo vệ khỏi mọi tình huống như credential có quyền xóa version vĩnh viễn hoặc mất key encryption.

---

## 6. S3 encryption

### SSE-S3

S3 quản lý key và encryption. Đơn giản, phù hợp dữ liệu cần encryption at rest mà không yêu cầu kiểm soát KMS key riêng.

### SSE-KMS

Dùng AWS KMS key để có key policy, audit qua CloudTrail, rotation và permission control. Người/role cần cả S3 permission và KMS permission phù hợp như Encrypt/Decrypt/GenerateDataKey tùy operation.

Trade-off: thêm KMS request/cost, quota và dependency.

### SSE-C

Customer cung cấp encryption key trong request; AWS không lưu key. Khó vận hành hơn, cần quản lý key rất cẩn thận và không để mất key.

| Type | Ai quản lý key | Use case |
|---|---|---|
| SSE-S3 | S3 | đơn giản, managed |
| SSE-KMS | KMS/customer policy | audit, access control, compliance |
| SSE-C | customer trong request | kiểm soát key đặc thù |

Encryption at rest không thay thế IAM, bucket policy, TLS hoặc Block Public Access.

### Default encryption và secure transport

Bật default encryption cho bucket để giảm object quên mã hóa. Dùng bucket policy yêu cầu TLS bằng condition secure transport khi phù hợp.

---

## 7. Bucket policy, IAM và public access

### 7.1. Bucket policy

Bucket policy là resource-based policy. Dùng để:

- cho phép CloudFront OAC đọc;
- cho phép account/role khác truy cập;
- yêu cầu encryption/TLS;
- giới hạn prefix;
- giới hạn VPC endpoint hoặc principal.

Policy phải giới hạn principal, action, resource và condition. Bucket ARN và object ARN khác nhau.

~~~json
{
  "Version": "2012-10-17",
  "Statement": [{
    "Sid": "ReadReports",
    "Effect": "Allow",
    "Principal": {
      "AWS": "arn:aws:iam::123456789012:role/ReportReader"
    },
    "Action": "s3:GetObject",
    "Resource": "arn:aws:s3:::company-documents/reports/*"
  }]
}
~~~

### 7.2. Block Public Access

Block Public Access là guardrail ngăn bucket/object policy hoặc ACL mở public theo cấu hình. Bật ở account, bucket hoặc access point tùy scope.

Private bucket nên bật Block Public Access và chỉ mở access qua IAM role, CloudFront OAC, presigned URL hoặc controlled endpoint.

### 7.3. ACL

ACL là cơ chế cũ ở mức nhận biết. Với thiết kế mới, ưu tiên policy và S3 Object Ownership để giảm phức tạp. Không trộn ACL/policy/public access mà không hiểu effective permission.

### 7.4. Access point

S3 Access Point cung cấp endpoint và policy riêng theo application/team, hữu ích khi một bucket có nhiều access pattern. Ở mức SAA, nhớ access point là cách tách policy/entry point chứ không thay thế IAM/bucket security.

---

## 8. Presigned URL

Presigned URL là URL có chữ ký và thời hạn, được tạo bởi principal có quyền trên object. Người nhận có thể upload/download mà không cần AWS credential trực tiếp.

Use case:

- client upload trực tiếp vào private S3;
- download file riêng lẻ có thời hạn;
- giảm việc proxy file qua application server.

Flow:

~~~text
Application có IAM role
        ↓ tạo presigned URL
Client dùng URL trong thời hạn
        ↓
S3 kiểm tra chữ ký và permission của signer
~~~

Lưu ý:

- ai tạo URL phải có permission;
- URL là bearer token, ai có URL có thể dùng trong thời hạn;
- giới hạn expiry;
- validate object key, content type và size;
- không log URL đầy đủ ở nơi không an toàn;
- presigned URL không vượt quá permission của signer;
- revoke sớm không đơn giản, có thể đổi object/key, deny policy hoặc revoke credential tùy pattern.

Presigned POST phù hợp browser upload với policy về key prefix, content length và content type.

---

## 9. Multipart upload và Transfer Acceleration

### Multipart upload

Chia file lớn thành nhiều part để upload song song, retry từng part và tăng throughput. Phù hợp file lớn hoặc mạng không ổn định.

Phải abort multipart upload không hoàn tất để tránh giữ part và phát sinh cost. Lifecycle có thể tự abort incomplete multipart uploads.

### Transfer Acceleration

Transfer Acceleration dùng edge location để tăng tốc upload/download tới bucket khi user ở xa Region. Có charge thêm và không phải lúc nào cũng nhanh hơn; benchmark theo geography.

Phân biệt:

- CloudFront: phân phối/cache content tới user;
- Transfer Acceleration: tăng tốc transfer tới S3 origin;
- multipart: tối ưu upload file lớn bằng parallel parts.

---

## 10. S3 replication

### Same-Region Replication

Replicate object sang bucket khác trong cùng Region. Dùng cho isolation, compliance, environment hoặc backup logical trong Region.

### Cross-Region Replication

Replicate sang bucket ở Region khác. Dùng cho DR, compliance, data locality hoặc giảm latency ở Region khác.

Replication thường cần:

- versioning ở source và destination;
- IAM replication role;
- rule theo prefix/tag;
- KMS permission nếu encrypted;
- delete marker/version behavior;
- monitoring và retry;
- destination ownership/access control.

Replication bất đồng bộ, không phải synchronous transaction. RPO không phải zero. Nếu cần replicate object đã tồn tại, dùng batch replication/pattern tương ứng; bật replication không luôn copy mọi historical object.

### Replication và delete

Cần đọc kỹ delete marker replication, permanent delete replication và ownership. Không giả định xóa source luôn xóa destination nếu policy không cấu hình như vậy; giữ destination có thể cần cho recovery.

---

## 11. S3 events và integration

S3 có thể phát event khi object tạo/xóa/restore/replicate, tùy event type và cấu hình.

Target phổ biến:

| Target | Use case |
|---|---|
| SNS | fan-out notification tới nhiều subscriber |
| SQS | buffer, retry, decouple consumer |
| Lambda | xử lý nhẹ, event-driven |
| EventBridge | routing/filtering/workflow integration |

Đặc điểm cần nhớ:

- event delivery có thể duplicate;
- consumer phải idempotent;
- order không nên giả định nếu không có guarantee cụ thể;
- object notification có thể tạo loop nếu function ghi lại cùng bucket/prefix;
- dùng prefix/suffix filter để giảm event không cần;
- SQS giúp absorb burst và retry tốt hơn direct Lambda khi xử lý dài/không ổn định.

Ví dụ:

~~~text
S3 upload image
   → SQS
   → image processor
   → thumbnail bucket
   → EventBridge/SNS notification
~~~

S3 event notification và EventBridge có capability/format khác nhau; chọn theo filtering, fan-out, ordering, retry và workflow.

---

## 12. EBS, EFS và S3

### EBS

Block storage cho một EC2 trong một AZ, latency thấp và filesystem do OS quản lý. Phù hợp boot volume, database hoặc application cần block device.

### EFS

Managed elastic NFS file system, có thể mount từ nhiều EC2 ở nhiều AZ trong Region. Phù hợp shared filesystem, content upload dùng chung, web fleet và home directory.

EFS cung cấp file semantics, không phải block device. Chọn performance/throughput mode và access point theo workload.

### S3

Object storage qua API, scale lớn, lifecycle/replication mạnh, không mount POSIX mặc định. Phù hợp object, static asset, backup, data lake và archive.

| Tiêu chí | EBS | EFS | S3 |
|---|---|---|---|
| Model | block | file/NFS | object/API |
| Sharing | thường một AZ/instance pattern | nhiều EC2/AZ | nhiều client qua API |
| Latency | thấp, block | file latency | object request |
| Scale | volume capacity | elastic file | rất lớn |
| Use case | OS/DB | shared files | object/backup/archive |
| AZ | volume một AZ | regional/multi-AZ design | regional service |
| Cost model | volume/IOPS | storage/throughput | storage/request/retrieval |

### Chọn nhanh

- “Một EC2 cần boot/database disk” → EBS.
- “Nhiều EC2 cần cùng mount và POSIX” → EFS.
- “File/object lớn, API, lifecycle, archive” → S3.

---

## 13. EFS

EFS là file system managed, elastic và có thể truy cập từ nhiều AZ qua mount target.

Use case:

- shared web content;
- user uploads cho nhiều instance;
- home directories;
- container shared filesystem;
- ứng dụng cần NFS/POSIX semantics.

Cần cân nhắc:

- mount target ở mỗi AZ cần dùng;
- security group của mount target;
- NFS port 2049;
- IAM authorization và access point;
- throughput/performance mode;
- encryption at rest/in transit;
- backup và lifecycle tới IA/Archive tier theo hỗ trợ.

EFS không thay EBS cho boot volume và không phải lựa chọn mặc định cho database latency-sensitive.

---

## 14. FSx family

### FSx for Windows File Server

Managed Windows file system với SMB, Active Directory integration, Windows ACL và enterprise file shares. Phù hợp ứng dụng Windows/SMB cần compatibility.

### FSx for Lustre

High-performance parallel filesystem cho HPC, ML, video processing và workload cần throughput cao. Có thể tích hợp S3 làm data repository/import-export.

### FSx for OpenZFS

Managed file system với NFS và OpenZFS semantics, phù hợp workload cần ZFS features/low latency hoặc migration từ ZFS.

### FSx for NetApp ONTAP

Enterprise shared storage với NFS/SMB/iSCSI, snapshots, replication và NetApp features. Phù hợp migration workload NetApp hoặc multi-protocol enterprise.

Chọn FSx theo protocol, OS compatibility, throughput/IOPS, data management feature và migration requirement; không chọn chỉ vì “filesystem managed”.

---

## 15. Storage Gateway và DataSync

### Storage Gateway

Storage Gateway kết nối on-premises với cloud storage.

| Mode | Ý nghĩa |
|---|---|
| File Gateway | NFS/SMB file interface, object lưu ở S3 |
| Volume Gateway | iSCSI block interface, cache/stored volumes với S3 backend |
| Tape Gateway | virtual tape library cho backup |

Use case: hybrid access, backup migration, giữ ứng dụng legacy dùng protocol cũ.

### DataSync

DataSync dùng để transfer data giữa on-premises và AWS hoặc giữa storage service, hỗ trợ scheduling, verification và incremental transfer.

Use case:

- migrate NFS/SMB tới EFS/FSx/S3;
- replication định kỳ;
- transfer large dataset;
- giảm việc tự viết tool copy/retry.

Phân biệt:

- Storage Gateway: access/bridge hybrid liên tục;
- DataSync: migration/transfer/scheduled copy;
- Snow Family: offline/edge data transfer khi network không phù hợp.

---

## 16. Lab bắt buộc

### Lab 1 — Private S3 bucket

1. Tạo bucket và bật Block Public Access.
2. Bật default encryption.
3. Tạo prefix logs/ và uploads/.
4. Tạo IAM role chỉ đọc prefix logs/.
5. Tạo bucket policy deny insecure transport nếu phù hợp.
6. Test allowed và denied request.
7. Kiểm tra CloudTrail/access logging theo môi trường.

### Lab 2 — Presigned upload

- Application role tạo presigned URL giới hạn thời gian.
- Client upload object vào prefix uploads/.
- Giới hạn key prefix, content type và kích thước theo use case.
- Thử URL hết hạn.
- Xác nhận client không cần access key.
- Không ghi URL đầy đủ vào log.

### Lab 3 — Versioning và lifecycle

1. Bật versioning.
2. Upload hai phiên bản cùng key.
3. Delete object và quan sát delete marker.
4. Restore version cũ.
5. Tạo lifecycle cho noncurrent versions.
6. Transition object test sang Glacier class.
7. Kiểm tra minimum duration và retrieval cost.

### Lab 4 — EFS với hai EC2

1. Tạo EFS ở VPC lab.
2. Tạo mount target ở hai AZ.
3. SG EFS cho phép NFS 2049 từ EC2-SG.
4. Mount cùng file system trên hai EC2.
5. Tạo file từ instance A và đọc từ instance B.
6. Kiểm tra encryption, access point và lifecycle.
7. Xóa resource sau lab.

### Lab 5 — Event notification

- S3 object create → SQS hoặc Lambda.
- Lọc theo prefix/suffix.
- Xử lý duplicate event bằng idempotency.
- Nếu dùng SQS, cấu hình visibility timeout và DLQ phù hợp.
- Tránh loop khi processor ghi output vào cùng prefix event.

### Lab 6 — So sánh storage

Điền bảng cho một workload upload ảnh:

| Requirement | EBS | EFS | S3 |
|---|---|---|---|
| Nhiều instance đọc chung |  |  |  |
| Object lớn, lifecycle |  |  |  |
| POSIX directory |  |  |  |
| Boot volume |  |  |  |
| Archive dài hạn |  |  |  |
| Presigned client upload |  |  |  |

---

## 17. Bẫy đề thi SAA-C03

- S3 là object storage, không phải block/file system POSIX mặc định.
- EBS volume thường thuộc một AZ; EFS có thể dùng nhiều AZ.
- S3 strong consistency không biến S3 thành transaction database.
- Standard-IA/Glacier có retrieval/minimum storage cost.
- One Zone-IA không phù hợp bản copy duy nhất của dữ liệu quan trọng.
- Versioning không tự xóa version cũ; dùng lifecycle.
- DELETE khi versioning bật thường tạo delete marker.
- Object Lock là WORM, cần retention/legal hold design.
- SSE-KMS cần cả S3 permission và KMS key permission.
- Block Public Access là guardrail quan trọng nhưng không thay thế IAM/policy.
- Presigned URL là bearer token; ai có URL dùng được trong thời hạn.
- Multipart upload chưa hoàn tất có thể phát sinh cost; abort incomplete upload.
- Replication bất đồng bộ, không phải zero-RPO transaction.
- Replication thường yêu cầu versioning source/destination và role.
- S3 event có thể duplicate; consumer phải idempotent.
- SQS phù hợp buffer/retry; direct Lambda không luôn phù hợp burst lớn.
- EFS cần mount target, SG NFS 2049 và network path đúng.
- FSx for Windows dùng SMB/AD; FSx for Lustre cho HPC/high throughput.
- Storage Gateway là hybrid access; DataSync là migration/transfer.
- CloudFront/S3 access và presigned URL vẫn cần policy/authorization đúng.
- Lifecycle transition phải tính minimum duration, retrieval và request cost.

---

## 18. Câu hỏi tự luyện

### Câu 1

Ứng dụng có nhiều EC2 cần mount cùng thư mục upload với POSIX semantics. Chọn gì?

**Đáp án:** EFS, với mount target ở các AZ cần dùng, SG NFS 2049 và access control phù hợp.

### Câu 2

Một EC2 cần boot disk và database block device latency thấp. Chọn gì?

**Đáp án:** EBS volume, chọn type theo IOPS/throughput và backup requirement.

### Câu 3

User upload file trực tiếp vào bucket private mà không có AWS credential. Dùng gì?

**Đáp án:** presigned URL hoặc presigned POST do application tạo bằng role có permission giới hạn.

### Câu 4

Ảnh được truy cập thường xuyên trong 30 ngày đầu, sau đó hiếm đọc và cần giữ 7 năm. Thiết kế gì?

**Đáp án:** Standard/Intelligent-Tiering ban đầu, lifecycle transition sang IA/Glacier class rồi Deep Archive theo retention và retrieval requirement.

### Câu 5

Cần bảo vệ backup không bị xóa trong 7 năm. Dùng gì?

**Đáp án:** S3 Object Lock với retention mode phù hợp, kết hợp versioning, least privilege và quy trình legal/compliance.

### Câu 6

Bucket encrypted SSE-KMS nhưng role có s3:GetObject vẫn bị AccessDenied. Vì sao?

**Đáp án:** role có thể thiếu KMS key permission, key policy không trust role hoặc context/account/Region không đúng.

### Câu 7

Cần replicate object sang Region khác để DR. Điều kiện chính?

**Đáp án:** bật versioning ở source/destination, cấu hình replication rule/role, KMS permission nếu cần và hiểu replication bất đồng bộ.

### Câu 8

S3 upload kích hoạt xử lý ảnh nhưng cùng function ghi output vào prefix cũng trigger event. Rủi ro?

**Đáp án:** event loop. Dùng prefix/suffix filter hoặc bucket/output prefix riêng.

### Câu 9

Workload legacy dùng SMB và Windows ACL. Dùng storage nào?

**Đáp án:** FSx for Windows File Server.

### Câu 10

On-premises backup application cần virtual tape interface. Dùng gì?

**Đáp án:** Storage Gateway Tape Gateway.

### Câu 11

Cần migrate hàng TB từ NFS on-premises tới EFS theo lịch incremental. Dùng gì?

**Đáp án:** DataSync, với task schedule và verification phù hợp.

### Câu 12

File lớn upload qua mạng không ổn định. Dùng kỹ thuật nào?

**Đáp án:** multipart upload để retry từng part và upload song song.

---

## 19. Checklist hoàn thành tuần

### Kiến thức

- [ ] Giải thích bucket, object, key và prefix.
- [ ] Hiểu S3 strong consistency và giới hạn object storage.
- [ ] Chọn đúng storage class theo access pattern.
- [ ] Hiểu versioning, delete marker, lifecycle và Object Lock.
- [ ] Phân biệt SSE-S3, SSE-KMS và SSE-C.
- [ ] Viết bucket policy least privilege.
- [ ] Biết tác dụng của Block Public Access.
- [ ] Dùng presigned URL và multipart upload đúng use case.
- [ ] Hiểu Transfer Acceleration.
- [ ] Phân biệt Same-Region và Cross-Region Replication.
- [ ] Thiết kế S3 event tới SQS/SNS/Lambda/EventBridge.
- [ ] Phân biệt EBS, EFS và S3.
- [ ] Nhận biết FSx for Windows/Lustre/OpenZFS/ONTAP.
- [ ] Phân biệt Storage Gateway với DataSync.

### Thực hành

- [ ] Đã tạo private S3 bucket và bật Block Public Access.
- [ ] Đã upload bằng presigned URL.
- [ ] Đã bật versioning và test delete marker/restore.
- [ ] Đã tạo lifecycle transition thử nghiệm.
- [ ] Đã tạo bucket policy cho một role.
- [ ] Đã mount EFS trên hai EC2.
- [ ] Đã test S3 event và idempotent consumer.
- [ ] Đã hoàn thành bảng so sánh EBS/EFS/S3.
- [ ] Đã xóa resource test và kiểm tra storage/retrieval cost.

Bạn sẵn sàng sang Tuần 7 khi có thể chọn EBS, EFS hoặc S3 từ access pattern, thiết kế S3 lifecycle/permission đúng và giải thích trade-off giữa latency, durability, sharing và cost.

---

## 20. Tài liệu tham khảo

- [Amazon S3 User Guide](https://docs.aws.amazon.com/AmazonS3/latest/userguide/Welcome.html) — bucket, object, policy và storage.
- [S3 storage classes](https://aws.amazon.com/s3/storage-classes/) — storage class và access pattern.
- [S3 consistency](https://docs.aws.amazon.com/AmazonS3/latest/userguide/Welcome.html) — consistency model hiện hành.
- [S3 Versioning](https://docs.aws.amazon.com/AmazonS3/latest/userguide/Versioning.html) — version và delete marker.
- [S3 Object Lock](https://docs.aws.amazon.com/AmazonS3/latest/userguide/object-lock.html) — WORM, retention và legal hold.
- [S3 server-side encryption](https://docs.aws.amazon.com/AmazonS3/latest/userguide/serv-side-encryption.html) — SSE-S3, SSE-KMS và SSE-C.
- [S3 presigned URLs](https://docs.aws.amazon.com/AmazonS3/latest/userguide/using-presigned-url.html) — temporary access.
- [S3 Replication](https://docs.aws.amazon.com/AmazonS3/latest/userguide/replication.html) — SRR/CRR.
- [S3 Event Notifications](https://docs.aws.amazon.com/AmazonS3/latest/userguide/EventNotifications.html) — SNS, SQS và Lambda.
- [Amazon EBS](https://docs.aws.amazon.com/ebs/latest/userguide/what-is-ebs.html) — block storage.
- [Amazon EFS](https://docs.aws.amazon.com/efs/latest/ug/whatisefs.html) — elastic file system.
- [Amazon FSx](https://docs.aws.amazon.com/fsx/latest/WindowsGuide/what-is.html) — managed file systems.
- [AWS Storage Gateway](https://docs.aws.amazon.com/storagegateway/latest/userguide/WhatIsStorageGateway.html) — hybrid storage.
- [AWS DataSync](https://docs.aws.amazon.com/datasync/latest/userguide/what-is-datasync.html) — data transfer/migration.


# Tuần 12 — Reliability, backup và disaster recovery

> Mục tiêu: thiết kế workload có thể chịu failure, chọn backup/replication/DR theo RTO-RPO và ngân sách, đồng thời có runbook failover/failback có thể kiểm thử.

## 1. Kết quả cần đạt

- Phân biệt failure domain ở mức instance, AZ và Region.
- Thiết kế Multi-AZ, Multi-Region, active-active và active-passive.
- Phân biệt backup/restore với replication.
- Chọn backup/restore, pilot light, warm standby hoặc multi-site.
- Chuyển RTO/RPO thành architecture và cost trade-off.
- Hiểu AWS Backup, EBS snapshot, RDS/Aurora backup và DynamoDB PITR.
- Dùng S3 versioning, replication và Object Lock cho data protection.
- Thiết kế Route 53 failover và health check.
- Nhận biết cross-Region read replica, Aurora Global Database và DynamoDB Global Tables.
- Viết runbook failover/failback và dependency map.
- Áp dụng chaos testing, game day và restore test.
- Thiết kế graceful degradation, retry backoff, circuit breaker và idempotency.

## 2. Lịch học gợi ý

| Buổi | Nội dung | Thời lượng |
|---|---|---:|
| 1 | Failure domain, HA và Multi-AZ | 3 giờ |
| 2 | RTO/RPO và DR strategies | 3 giờ |
| 3 | Backup/restore cho AWS data services | 3 giờ |
| 4 | Multi-Region, failover và runbook | 2–3 giờ |
| 5 | Chaos/game day, resilience patterns và lab | 2 giờ |

Cách suy luận: bắt đầu từ business impact và RTO/RPO, sau đó chọn failure domain, data protection, traffic failover và operational runbook. Không bắt đầu bằng việc chọn service.

---

## 3. Reliability và resilience

### 3.1. Reliability

Reliability là khả năng workload thực hiện đúng chức năng và phục hồi khi có lỗi. Nó bao gồm:

- provision capacity phù hợp;
- monitor và detect failure;
- tự động recover;
- test recovery;
- quản lý thay đổi;
- bảo vệ dữ liệu;
- giảm blast radius.

### 3.2. High availability

High availability giảm thời gian dịch vụ không phục vụ bằng redundancy, health check và failover. HA không đồng nghĩa zero downtime.

Một ứng dụng có ALB Multi-AZ nhưng database một AZ vẫn có single point of failure ở tầng data. Review phải đi theo toàn dependency chain.

### 3.3. Resilience và fault tolerance

- **Resilience:** có thể chịu lỗi và phục hồi.
- **Fault tolerance:** tiếp tục hoạt động gần như không gián đoạn khi một thành phần lỗi.
- **Disaster recovery:** khôi phục workload sau sự cố lớn như Region outage, data corruption hoặc ransomware.

Fault tolerance thường đắt hơn resilience. Chọn theo business impact, không mặc định active-active.

---

## 4. Failure domain

~~~text
Process/instance → host → AZ → Region → nhiều Region
~~~

### Instance/host failure

Giải pháp:

- Auto Scaling;
- load balancer health check;
- immutable AMI;
- instance replacement;
- EBS snapshot/backup;
- stateless application.

### AZ failure

Giải pháp:

- ALB/NLB Multi-AZ;
- ASG ở nhiều AZ;
- RDS/Aurora Multi-AZ;
- EFS regional/multi-AZ;
- NAT Gateway theo AZ hoặc thiết kế chấp nhận dependency;
- queue/cache/database topology không phụ thuộc một AZ.

### Region failure

Giải pháp:

- cross-Region backup/replication;
- Route 53/Global Accelerator failover;
- pilot light/warm standby/active-active;
- Aurora Global Database;
- DynamoDB Global Tables;
- S3 CRR;
- runbook và data consistency plan.

Mở rộng failure domain tăng cost, latency, transfer và complexity. Không phải mọi workload cần Multi-Region.

---

## 5. Multi-AZ, Multi-Region và traffic strategy

### 5.1. Multi-AZ

Multi-AZ dùng nhiều Availability Zone trong một Region. Mục tiêu chính là chịu AZ/instance failure với latency thấp và network nội vùng.

Phù hợp:

- web/app ASG;
- load balancer;
- RDS Multi-AZ;
- EFS mount target;
- distributed worker.

Multi-AZ không bảo vệ khỏi Region-wide outage, lỗi logic hoặc xóa dữ liệu nhầm nếu không có backup.

### 5.2. Multi-Region

Multi-Region triển khai workload/data ở nhiều Region. Mục tiêu là regional resilience, data locality hoặc global latency.

Trade-off:

- data replication lag;
- cross-Region transfer;
- DNS/routing;
- key/secret/config sync;
- deployment;
- conflict resolution;
- operational complexity;
- compliance/data residency.

### 5.3. Active-passive

Primary phục vụ traffic; secondary được backup hoặc khởi động ở mức thấp. Khi primary lỗi, promote/scale secondary rồi chuyển traffic.

Ưu điểm: cost thấp hơn active-active, dễ kiểm soát write ownership. Nhược điểm: failover có downtime, warm-up và data promotion.

### 5.4. Active-active

Nhiều Region cùng phục vụ traffic. Cần:

- global routing;
- data replication/multi-writer strategy;
- conflict resolution;
- idempotency;
- deployment đồng bộ;
- observability mỗi Region;
- test routing/failure.

Ưu điểm RTO thấp và tận dụng capacity. Nhược điểm cost/complexity và consistency khó hơn.

---

## 6. RTO và RPO

- **RTO:** thời gian tối đa để khôi phục dịch vụ sau failure.
- **RPO:** lượng dữ liệu tối đa có thể mất, tính theo thời gian.

Ví dụ:

| Requirement | Ý nghĩa |
|---|---|
| RTO 24 giờ | có thể restore thủ công trong ngày |
| RTO 1 giờ | cần backup sẵn, runbook và automation |
| RTO 5 phút | cần warm standby/replication/failover nhanh |
| RPO 24 giờ | backup hằng ngày có thể đủ |
| RPO 15 phút | backup/replication tối đa 15 phút |
| RPO gần 0 | synchronous/near-real-time replication theo service |

RTO/RPO của ứng dụng bị giới hạn bởi dependency chậm nhất. Có database replicate tốt nhưng DNS, secret, image, queue hoặc third-party chưa sẵn sàng thì end-to-end RTO vẫn không đạt.

### 6.1. Availability vs recovery

- HA giảm xác suất downtime trong failure thông thường.
- Backup giúp khôi phục dữ liệu.
- Replication giúp giảm RPO và có thể giảm RTO.
- DR strategy khôi phục toàn workload sau disaster.

Replication có thể sao chép lỗi/xóa nhầm; backup immutable/versioned cần thiết để phục hồi logical corruption.

---

## 7. DR strategies

### 7.1. Backup and restore

Chỉ giữ backup ở secondary location; khi disaster xảy ra, tạo infrastructure và restore data.

~~~text
Primary → backup/copy → secondary storage
Disaster → provision → restore → validate → switch traffic
~~~

- Cost thấp nhất;
- RTO cao;
- RPO phụ thuộc backup frequency;
- phù hợp workload không cần khôi phục nhanh.

### 7.2. Pilot light

Giữ các thành phần tối thiểu ở secondary, thường data replication và core configuration. Khi failover, scale compute/app lên.

- Cost thấp hơn warm standby;
- RTO trung bình;
- cần automation/provisioning;
- data layer phải sẵn sàng.

### 7.3. Warm standby

Secondary chạy phiên bản tối thiểu nhưng có thể nhận traffic sau scale/promote.

- RTO thấp hơn pilot light;
- cost trung bình/cao;
- cần test capacity/scale;
- traffic failover nhanh hơn.

### 7.4. Multi-site active-active

Hai hoặc nhiều site/Region phục vụ cùng lúc.

- RTO thấp nhất;
- cost và complexity cao;
- cần data conflict/consistency;
- deployment, observability và security phải đồng nhất.

### 7.5. Bảng so sánh

| Strategy | Cost | RTO | RPO | Complexity |
|---|---:|---:|---:|---:|
| Backup/restore | thấp | cao | cao/trung bình | thấp |
| Pilot light | thấp/trung | trung/cao | thấp hơn | trung |
| Warm standby | trung/cao | thấp | thấp | cao |
| Active-active | cao | thấp nhất | thấp/near-zero tùy data | cao nhất |

Chọn strategy từ RTO/RPO và business impact, không chọn vì architecture “xịn” hơn.

---

## 8. Backup và replication

### Backup

Backup là bản sao theo thời điểm, dùng để restore về state trước đó. Backup có thể bảo vệ khỏi:

- xóa nhầm;
- corruption;
- ransomware;
- bad deployment;
- logical error;
- retention/compliance.

Backup không tự làm application failover tức thời.

### Replication

Replication sao chép dữ liệu sang standby/secondary gần thời gian thực hoặc theo lịch. Nó giảm RPO và hỗ trợ read scaling/failover.

Replication có thể lan truyền:

- lỗi application;
- delete nhầm;
- corrupted data;
- encrypted data với key/policy sai.

Vì vậy cần backup versioned/immutable song song.

### Backup design checklist

- What: dữ liệu/service nào phải backup?
- When: frequency và backup window?
- Where: cùng AZ/Region hay cross-Region?
- How long: retention?
- Who: ai được restore/delete?
- Encrypted by which key?
- Restore time thực tế?
- Có application consistency không?
- Có test restore định kỳ không?
- Có alert khi backup fail không?

---

## 9. AWS Backup

AWS Backup quản lý policy, schedule, retention và copy backup cho nhiều AWS resource được hỗ trợ.

Thành phần:

- backup plan;
- backup rule;
- backup vault;
- backup selection;
- lifecycle/cold storage;
- copy tới Region/account khác;
- vault access policy;
- Vault Lock theo requirement.

Lợi ích:

- centralized governance;
- tag/resource selection;
- cross-account/cross-Region backup;
- compliance reporting;
- lifecycle automation.

AWS Backup không tự backup mọi service/data type. Kiểm tra service support, application consistency và restore procedure.

### Backup vault và security

- tách account backup/log archive;
- giới hạn delete;
- encryption bằng KMS;
- Vault Lock/immutability theo requirement;
- monitor backup job/compliance;
- test restore với role tối thiểu.

---

## 10. Backup theo dịch vụ

### EBS

Dùng EBS snapshot:

- snapshot incremental;
- copy cross-Region;
- encrypt bằng KMS;
- restore volume ở AZ đích;
- có thể tạo AMI.

Snapshot volume không luôn application-consistent cho database. Flush/quiesce hoặc dùng backup database-aware.

### RDS/Aurora

- automated backup và PITR;
- manual snapshot cho retention/clone;
- Multi-AZ cho HA;
- read replica/cross-Region replica cho read/DR;
- Aurora Global Database cho global read/DR pattern.

Restore thường tạo instance/cluster mới; cần update endpoint, secret, SG và validate schema/data.

### DynamoDB

- point-in-time recovery;
- on-demand backup;
- Global Tables cho multi-Region;
- export/backup theo requirement.

PITR giúp phục hồi về thời điểm trong window; không thay Global Tables nếu cần regional serving.

### S3

- versioning;
- delete marker;
- lifecycle;
- replication;
- Object Lock;
- cross-account/Region copy;
- backup/access policy.

Replication không thay versioning/Object Lock khi cần phục hồi xóa nhầm hoặc corruption.

---

## 11. Route 53 failover

Route 53 failover routing có primary và secondary record. Health check/Evaluate Target Health quyết định answer.

~~~text
User → DNS query
        ├── primary healthy → Primary endpoint
        └── primary unhealthy → Secondary endpoint
~~~

### Failover checklist

- health check kiểm tra đúng endpoint;
- endpoint secondary thật sự ready;
- data replicated/restore test;
- certificate/secret/IAM tồn tại;
- DNS TTL và cache hiểu rõ;
- application có retry/reconnect;
- smoke test sau switch;
- rollback/failback criteria rõ.

Route 53 không tự promote database, restore backup hoặc sync configuration. Runbook phải có các bước đó.

### TTL và DNS cache

TTL thấp giúp thay đổi nhanh hơn nhưng không đảm bảo client chuyển ngay. Có thể có resolver cache, connection reuse và application DNS caching.

Nếu RTO yêu cầu rất thấp, cân nhắc Global Accelerator/traffic manager pattern thay vì chỉ DNS failover, tùy workload.

---

## 12. Cross-Region data services

### RDS cross-Region read replica

Dùng cho read local, DR hoặc promote khi primary Region lỗi.

Cần biết:

- async replication/lag;
- promote là hành động failover;
- endpoint/secret/app config cần đổi;
- cross-Region transfer/cost;
- data consistency trước switch.

### Aurora Global Database

Một primary Region và secondary Region đọc/DR. Hỗ trợ global read latency thấp hơn snapshot-only nhưng vẫn cần RPO/RTO, failover và promotion plan.

Không coi secondary mặc định là writer active-active.

### DynamoDB Global Tables

Multi-Region multi-active, local read/write và replication. Cần xử lý conflict/concurrent writes, last-writer behavior, routing và eventual replication.

### S3 CRR

Copy object cross-Region bất đồng bộ. Cần versioning, replication role, KMS permission và monitoring. Đừng giả định object đã tồn tại tự động được replicate khi chỉ bật rule mới.

---

## 13. Failover và failback runbook

### Trước disaster

- xác định trigger và authority;
- freeze risky changes;
- kiểm tra health/data lag;
- thông báo stakeholder;
- ghi timestamp;
- mở incident channel;
- chọn failover checkpoint.

### Failover

1. Confirm primary failure, tránh split-brain.
2. Chặn write hoặc chuyển application mode nếu cần.
3. Promote/restore secondary data.
4. Scale/enable secondary compute.
5. Cập nhật secret/config/certificate.
6. Chuyển Route 53/Global Accelerator traffic.
7. Smoke test read/write/auth/background jobs.
8. Theo dõi metrics, error rate, queue và lag.
9. Ghi kết quả và quyết định tiếp theo.

### Failback

1. Xác nhận primary đã ổn định.
2. Xác định dữ liệu phát sinh ở secondary.
3. Replicate/sync ngược và resolve conflict.
4. Chọn thời điểm maintenance nếu cần.
5. Switch traffic có kiểm soát.
6. Validate và giữ secondary sẵn sàng.
7. Post-incident review.

Failback phức tạp hơn failover nếu secondary đã nhận writes. Active-active cần conflict strategy; active-passive cần rõ write ownership.

---

## 14. Dependency map

Vẽ dependency map trước khi tuyên bố DR ready:

~~~text
DNS/edge
  ↓
Load balancer
  ↓
Application compute
  ├── database
  ├── cache
  ├── queue/event bus
  ├── object storage
  ├── secret/KMS
  ├── observability
  └── third-party/payment
~~~

Với mỗi dependency ghi:

- Region/AZ;
- primary/secondary;
- backup/replication;
- RTO/RPO;
- failover owner;
- credentials/keys;
- external dependency;
- test method;
- failure behavior.

Các điểm thường bị quên:

- KMS key;
- secret;
- DNS/certificate;
- container image/ECR;
- IAM role/policy/SCP;
- queue/DLQ;
- scheduled job;
- webhook/third-party allowlist;
- monitoring/alert;
- data migration/replication lag.

---

## 15. Resilience patterns

### Retry with exponential backoff

Retry transient error với backoff và jitter. Giới hạn max attempts/total time. Không retry validation/auth/permanent error.

Retry storm xảy ra khi hàng nghìn client retry cùng lúc. Jitter, circuit breaker, rate limit và queue giúp giảm.

### Circuit breaker

Nếu downstream fail liên tục:

- mở circuit, ngừng gọi tạm thời;
- trả fallback/degraded response;
- sau cooldown thử half-open;
- đóng khi downstream khỏe.

### Graceful degradation

Khi dependency lỗi, vẫn giữ chức năng cốt lõi:

- hiển thị dữ liệu cache/stale;
- tắt recommendation;
- nhận order nhưng xử lý async;
- trả read-only mode;
- bỏ notification không critical;
- queue request để xử lý sau.

### Idempotency

Retry/failover không được tạo duplicate side effect. Dùng idempotency key, conditional write, transaction hoặc outbox/inbox pattern.

### Bulkhead

Tách pool/thread/concurrency/queue theo dependency để một workload không chiếm hết capacity của hệ thống.

---

## 16. Chaos testing và game day

### Chaos testing

Có chủ đích inject failure để kiểm tra giả định:

- terminate instance;
- disable target/AZ capacity;
- revoke network path trong môi trường test;
- delay downstream;
- fill queue;
- expire secret trong staging;
- simulate Region failover runbook.

Không gây disruption production nếu chưa có approval/scope. Bắt đầu nhỏ và có rollback.

### Game day

Game day là exercise có người thật thực hiện incident/runbook:

- inject hoặc mô phỏng scenario;
- quan sát detection;
- thực hiện failover;
- đo RTO/RPO;
- ghi gap;
- cập nhật architecture/runbook;
- lặp lại.

Restore test và game day là bằng chứng, không chỉ tài liệu.

---

## 17. Lab bắt buộc

### Lab 1 — DR plan RTO 1 giờ/RPO 15 phút

Viết plan cho web app gồm ALB, ASG, RDS, S3, queue và secret.

| Hạng mục | Quyết định |
|---|---|
| Failure scenario |  |
| RTO/RPO | RTO 1 giờ, RPO 15 phút |
| Primary Region |  |
| Secondary strategy |  |
| Database protection |  |
| Object protection |  |
| DNS/traffic failover |  |
| Secret/KMS |  |
| Owner/runbook |  |
| Restore/failover test |  |
| Cost estimate |  |

### Lab 2 — Backup/restore RDS hoặc DynamoDB

- Bật automated backup/PITR.
- Tạo test data.
- Xóa/thay đổi một phần data.
- Restore snapshot/PITR.
- Đo thời gian restore.
- Validate count/checksum/business invariant.
- Ghi RPO thực tế và RTO thực tế.
- Xóa bản restore sau test.

### Lab 3 — Route 53 failover

1. Tạo primary/secondary endpoint test.
2. Tạo health check.
3. Tạo failover records.
4. Làm primary unhealthy có kiểm soát.
5. Query DNS và test application.
6. Ghi TTL/cache delay.
7. Khôi phục primary và test failback.

### Lab 4 — Mô phỏng mất AZ

Với môi trường test:

- disable/terminate target ở một AZ;
- quan sát ALB/ASG capacity;
- kiểm tra RDS Multi-AZ failover nếu ngân sách;
- theo dõi error/latency;
- ghi dependency còn single-AZ.

### Lab 5 — Dependency map

Vẽ tất cả dependency và đánh dấu:

- được backup;
- được replicate;
- không được backup;
- cần recreate;
- owner;
- failover action;
- validation.

---

## 18. Bẫy đề thi SAA-C03

- Multi-AZ không bảo vệ Region outage.
- Multi-AZ tăng HA, không đồng nghĩa read scaling.
- Backup/restore không giống replication.
- Replication có thể sao chép corruption/xóa nhầm.
- RTO thấp cần warm/active strategy, không chỉ backup.
- RPO thấp cần backup frequency/replication phù hợp.
- Pilot light khác warm standby: pilot light ít compute hơn.
- Active-active có conflict/consistency complexity.
- Route 53 failover không tự promote database hoặc restore data.
- DNS TTL không đảm bảo failover tức thời.
- RDS read replica không phải Multi-AZ standby.
- Cross-Region replica thường async và có lag.
- Aurora Global Database/DynamoDB Global Tables cần conflict/failover plan.
- S3 replication không thay versioning/Object Lock.
- EBS snapshot có thể không application-consistent database backup.
- PITR restore thường tạo resource mới, cần đổi endpoint/config.
- Backup chưa restore test chưa chứng minh RTO.
- KMS key/secret/certificate/IAM thường bị quên trong DR.
- Retry không idempotent gây duplicate side effect.
- Retry storm có thể làm downstream sập hơn.
- Circuit breaker/graceful degradation giảm blast radius nhưng cần business decision.
- Queue/DLQ cần được đưa vào dependency map và DR plan.
- Chaos test phải có scope/rollback, không thử tùy tiện production.

---

## 19. Câu hỏi tự luyện

### Câu 1

Một web app phải chịu lỗi một AZ nhưng không cần DR Region. Thiết kế nào?

**Đáp án:** ALB/ASG Multi-AZ, database Multi-AZ hoặc topology phù hợp, private subnets/route/health check và state externalized.

### Câu 2

Business RTO 8 giờ, RPO 24 giờ, ngân sách thấp. Chọn DR strategy nào?

**Đáp án:** backup/restore với backup đủ tần suất, cross-Region copy nếu cần, IaC/runbook để provision và restore.

### Câu 3

Business RTO 5 phút, RPO gần 0 cho global service. Hướng nào?

**Đáp án:** warm standby hoặc active-active với replication gần real-time, global traffic failover và test thực tế; cost/complexity cao hơn.

### Câu 4

RDS Multi-AZ có giúp scale read không?

**Đáp án:** không phải mục tiêu chính. Dùng read replica/Aurora reader cho read scaling.

### Câu 5

Replication đã chạy nhưng user xóa nhầm dữ liệu và xóa được replicate sang secondary. Cần gì?

**Đáp án:** backup/PITR/versioning/immutable copy để phục hồi logical corruption; replication đơn thuần không đủ.

### Câu 6

Route 53 primary unhealthy nhưng client vẫn tới primary vài phút. Vì sao?

**Đáp án:** DNS resolver/TTL/cache/connection reuse; DNS failover không instant absolute.

### Câu 7

Cần backup cross-account chống xóa nhầm bởi account production. Thiết kế gì?

**Đáp án:** AWS Backup/copy vào backup account, vault policy/lock, KMS key/access control và restore test.

### Câu 8

DynamoDB multi-Region cần local read/write. Chọn gì?

**Đáp án:** Global Tables, kèm conflict resolution, replication lag, routing và failover plan.

### Câu 9

Một downstream chậm khiến toàn hệ thống retry liên tục. Áp dụng gì?

**Đáp án:** exponential backoff + jitter, timeout, circuit breaker, bulkhead, queue/backpressure và graceful degradation.

### Câu 10

Làm thế nào chứng minh backup đạt RTO 1 giờ?

**Đáp án:** restore game day có đo thời gian từ detection tới service ready, validate data/config/secret/DNS và ghi evidence.

### Câu 11

Failover sang secondary nhưng application không kết nối được database. Dependency nào thường bị quên?

**Đáp án:** SG/route/DNS, secret, KMS key, endpoint, IAM role, parameter/config hoặc database schema migration.

### Câu 12

Một message được xử lý hai lần sau failover. Làm sao bảo vệ business?

**Đáp án:** idempotency key, conditional write/transaction, dedup record và side effect API có idempotency.

---

## 20. Checklist hoàn thành tuần

### Kiến thức

- [ ] Phân biệt instance, AZ và Region failure domain.
- [ ] Phân biệt Multi-AZ và Multi-Region.
- [ ] Phân biệt active-active và active-passive.
- [ ] Phân biệt backup/restore với replication.
- [ ] Giải thích backup/restore, pilot light, warm standby và multi-site.
- [ ] Chọn strategy theo RTO/RPO/cost.
- [ ] Hiểu AWS Backup, EBS/RDS/DynamoDB backup.
- [ ] Hiểu S3 versioning, replication và Object Lock.
- [ ] Thiết kế Route 53 failover/health check.
- [ ] Nhận biết cross-Region replica, Aurora Global Database và Global Tables.
- [ ] Viết failover/failback runbook.
- [ ] Vẽ dependency map.
- [ ] Áp dụng retry/backoff, circuit breaker và idempotency.
- [ ] Hiểu chaos testing/game day.

### Thực hành

- [ ] Đã viết DR plan RTO 1 giờ/RPO 15 phút.
- [ ] Đã thực hiện backup/restore RDS hoặc DynamoDB.
- [ ] Đã đo RTO/RPO thực tế.
- [ ] Đã tạo Route 53 failover test.
- [ ] Đã mô phỏng mất một AZ.
- [ ] Đã vẽ dependency map.
- [ ] Đã xác định dependency không được backup/replicate.
- [ ] Đã test failover và failback.
- [ ] Đã cập nhật runbook sau game day.

Bạn sẵn sàng sang Tuần 13 khi có thể giải thích architecture chịu failure nào, dữ liệu phục hồi ra sao, traffic failover thế nào và có bằng chứng test chứ không chỉ có sơ đồ.

---

## 21. Tài liệu tham khảo

- [AWS Well-Architected Reliability Pillar](https://docs.aws.amazon.com/wellarchitected/latest/reliability-pillar/welcome.html) — reliability principles.
- [AWS Backup Developer Guide](https://docs.aws.amazon.com/aws-backup/latest/devguide/whatisbackup.html) — centralized backup.
- [Amazon EBS snapshots](https://docs.aws.amazon.com/ebs/latest/userguide/ebs-snapshots.html) — backup/copy/restore.
- [Amazon RDS backup and restore](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/CHAP_CommonTasks.BackupRestore.html) — automated backup, snapshot, PITR.
- [DynamoDB backup and restore](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/BackupRestore.html) — PITR/on-demand backup.
- [S3 Versioning](https://docs.aws.amazon.com/AmazonS3/latest/userguide/Versioning.html) — version recovery.
- [S3 Replication](https://docs.aws.amazon.com/AmazonS3/latest/userguide/replication.html) — SRR/CRR.
- [S3 Object Lock](https://docs.aws.amazon.com/AmazonS3/latest/userguide/object-lock.html) — immutable retention.
- [Route 53 DNS failover](https://docs.aws.amazon.com/Route53/latest/DeveloperGuide/dns-failover.html) — health-aware routing.
- [Amazon Aurora Global Database](https://docs.aws.amazon.com/AmazonRDS/latest/AuroraUserGuide/aurora-global-database.html) — cross-Region Aurora.
- [DynamoDB Global Tables](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/GlobalTables.html) — multi-Region tables.


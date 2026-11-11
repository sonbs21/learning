# Tuần 7 — Database: RDS, Aurora và caching

> Mục tiêu: chọn database relational managed phù hợp, phân biệt HA với read scaling, thiết kế backup/restore theo RTO/RPO và dùng ElastiCache để giảm latency/tải database.

## 1. Kết quả cần đạt

- Hiểu RDS engine, DB subnet group, parameter group và option group.
- Thiết kế RDS trong private subnets với security flow đúng.
- Phân biệt Multi-AZ standby với read replica.
- Chọn automated backup, manual snapshot và point-in-time recovery.
- Hiểu Aurora cluster, writer/reader endpoint và failover.
- Nhận biết Aurora Serverless và Aurora Global Database.
- Chọn read replica, cross-Region read replica hoặc Aurora reader theo workload.
- Phân biệt ElastiCache Redis và Memcached.
- Thiết kế cache-aside, session cache, TTL và invalidation.
- Nhận diện cache stampede, stale data và cache failure.
- Hiểu RDS Proxy ở mức use case.
- Áp dụng encryption, KMS, Secrets Manager và secret rotation.
- Chọn database/cache theo consistency, latency, availability, scale và cost.

## 2. Lịch học gợi ý

| Buổi | Nội dung | Thời lượng |
|---|---|---:|
| 1 | RDS, subnet, engine và security | 3 giờ |
| 2 | Multi-AZ, read replica, backup và PITR | 3 giờ |
| 3 | Aurora, endpoints và cross-Region | 2 giờ |
| 4 | Redis/Memcached và cache patterns | 3 giờ |
| 5 | RDS Proxy, lab và câu hỏi SAA-C03 | 2 giờ |

Cách suy luận: xác định relational hay non-relational, read/write pattern, transaction/consistency, RTO/RPO, connection count, latency và cost trước khi chọn dịch vụ.

---

## 3. Relational database fundamentals

Relational database lưu dữ liệu trong table với schema, relationship, transaction và query language. RDS phù hợp khi ứng dụng cần SQL, joins, constraints, transactions hoặc engine relational quen thuộc.

### 3.1. Khi chọn relational database

RDS/Aurora thường phù hợp khi:

- dữ liệu có schema rõ;
- cần ACID transaction;
- query có joins và filtering phong phú;
- ứng dụng đã dùng MySQL, PostgreSQL, MariaDB, Oracle hoặc SQL Server;
- cần managed patching, backup, monitoring và failover;
- team muốn giảm vận hành OS/database host.

RDS không phải lựa chọn mặc định cho mọi workload. Key-value cực lớn, schema linh hoạt và scale partitioned có thể phù hợp DynamoDB hơn; search có thể phù hợp OpenSearch; cache không nên lưu làm source of truth.

### 3.2. Từ requirement tới database

| Requirement | Câu hỏi |
|---|---|
| Consistency | Có cần transaction/strong consistency không? |
| Read/write | Tỷ lệ read/write, burst và hot query là gì? |
| Scale | scale vertical, read replica hay sharding? |
| Availability | mất instance/AZ/Region chịu được bao lâu? |
| Recovery | RTO/RPO và retention? |
| Latency | p95/p99 cần bao nhiêu? |
| Connection | bao nhiêu client/connection đồng thời? |
| Operations | team muốn managed tới mức nào? |
| Cost | storage, I/O, compute, replica và transfer? |

---

## 4. Amazon RDS

RDS là managed relational database service. AWS quản lý nhiều phần hạ tầng, provisioning, patching option, backup capability và failover mechanism; khách hàng vẫn quản lý schema, query, indexes, credentials, network access và data.

### 4.1. Engine

RDS hỗ trợ nhiều engine, trong đó cần nhớ ở mức SAA:

- Amazon Aurora;
- MySQL;
- PostgreSQL;
- MariaDB;
- Oracle;
- SQL Server.

Chọn engine dựa trên compatibility, license, feature, migration effort, performance và cost. “Managed” không có nghĩa mọi engine có cùng behavior hoặc cùng feature.

### 4.2. DB instance và storage

RDS DB instance có compute, memory, storage, network và engine configuration. Storage type/size/IOPS/throughput ảnh hưởng performance và cost.

RDS có thể scale instance class để tăng CPU/memory và scale storage theo capability. Read-heavy workload có thể dùng read replica thay vì chỉ tăng writer.

### 4.3. DB subnet group

DB subnet group là tập subnet ở nhiều AZ để RDS chọn network placement. Nên đặt database trong private subnets, thường ít nhất hai AZ.

DB subnet group không tự mở quyền network. Vẫn cần route, security group, NACL, DNS và credential.

### 4.4. Parameter group và option group

- **Parameter group:** database engine settings, connection limit, logging, time zone hoặc tuning parameter.
- **Option group:** engine-specific options, thường quan trọng với một số engine như Oracle/SQL Server.

Thay đổi parameter có thể dynamic hoặc cần reboot. Kiểm tra apply method và maintenance window trước production.

### 4.5. Maintenance và version

RDS có maintenance window, engine patch và minor/major version. Thiết kế nên có:

- test upgrade ở staging;
- backup/snapshot trước thay đổi;
- maintenance window;
- monitoring sau upgrade;
- rollback/restore plan;
- kiểm tra compatibility và downtime.

---

## 5. RDS network và security

### 5.1. Database private subnets

Pattern phổ biến:

~~~text
Internet
   ↓
ALB public subnets
   ↓
App EC2/ECS private subnets
   ↓
RDS private data subnets
~~~

Database không nên có public accessibility nếu không cần. Application truy cập qua private IP/DNS trong VPC.

### 5.2. Security group flow

Ví dụ PostgreSQL:

~~~text
ALB-SG → App-SG: 8080
App-SG → DB-SG: 5432
~~~

DB-SG chỉ cho phép inbound database port từ App-SG, không mở 5432 từ Internet. SG stateful; NACL là lớp khác và phải kiểm tra route/network path.

RDS không cần inbound từ internet để app private truy cập. Không dùng public IP như giải pháp thay thế cho security group đúng.

### 5.3. Encryption

RDS encryption at rest dùng KMS key. Encryption thường áp dụng cho storage, automated backup, read replica và snapshot theo quy tắc dịch vụ.

Cần kiểm tra:

- KMS key policy;
- quyền user/role;
- copy snapshot sang Region;
- secret/password;
- TLS in transit;
- rotation và audit.

Bật encryption lúc tạo thường đơn giản hơn migrate database unencrypted sang encrypted. Nếu cần encryption cho database đã tồn tại, có thể snapshot/copy/restore hoặc dùng migration strategy.

### 5.4. Credentials và Secrets Manager

Không commit password vào source, AMI, user data hoặc environment không kiểm soát. Dùng Secrets Manager/Parameter Store và IAM role.

Secret rotation cần ứng dụng xử lý credential mới mà không làm gián đoạn connection. RDS managed rotation có thể hỗ trợ theo engine/configuration; vẫn cần kiểm tra app connection pool.

---

## 6. Multi-AZ và high availability

RDS Multi-AZ tạo standby ở AZ khác để tăng availability và failover. Standby thường không dùng cho read scaling; ứng dụng kết nối qua endpoint để RDS chuyển writer sau failover.

### 6.1. Multi-AZ standby

Ưu điểm:

- tự động failover theo cơ chế dịch vụ;
- giảm ảnh hưởng khi AZ/instance lỗi;
- synchronous replication theo engine/service behavior;
- endpoint giúp ứng dụng không hard-code instance IP.

Trade-off:

- thêm compute/storage cost;
- failover có thời gian;
- không tăng read throughput như read replica;
- vẫn cần test ứng dụng reconnect/retry.

### 6.2. Multi-AZ DB cluster

Một số RDS engine hỗ trợ topology Multi-AZ DB cluster với writer và readable standby/reader để cải thiện availability và read capacity. Đọc kỹ service/engine capability hiện hành; không áp dụng khái niệm giống nhau cho mọi RDS engine.

### 6.3. Failover behavior

Khi failover:

- DNS endpoint có thể trỏ sang instance mới;
- connection hiện tại có thể bị ngắt;
- application cần retry/backoff;
- transaction đang chạy có thể rollback;
- health check/monitoring phải phát hiện;
- không hard-code IP của DB.

Test failover trong staging và ghi runbook.

---

## 7. Read replica và read scaling

Read replica là bản sao phục vụ read, thường replicate bất đồng bộ từ source. Dùng để:

- scale read-heavy workload;
- chạy reporting/analytics;
- tách workload read khỏi writer;
- cross-Region read hoặc DR tùy pattern.

### 7.1. Eventual consistency

Replication bất đồng bộ nghĩa reader có thể trễ writer. Không đọc từ replica ngay sau write nếu business cần read-after-write mạnh.

Ứng dụng có thể:

- đọc critical data từ writer;
- chấp nhận stale read;
- route theo request type;
- dùng session stickiness/consistency token theo design;
- monitor replica lag.

### 7.2. Cross-Region read replica

Cross-Region read replica giúp:

- giảm read latency ở Region khác;
- hỗ trợ DR;
- phục vụ reporting gần user/analyst.

Trade-off:

- replication lag;
- cross-Region data transfer;
- promote/failover cần runbook;
- security/KMS/network/engine limitation;
- không phải active-active writer mặc định.

### 7.3. Multi-AZ vs read replica

| Tiêu chí | Multi-AZ standby | Read replica |
|---|---|---|
| Mục đích | HA/failover | read scaling/analytics/DR |
| Replication | thường synchronous service behavior | asynchronous |
| Read traffic | standby không dùng như reader thông thường | có |
| Consistency | cao hơn theo HA model | có lag/eventual |
| Failover | managed automatic pattern | promote/runbook tùy use case |
| Số lượng | theo engine/service limit | theo engine/service limit |
| Câu hỏi thi | “high availability” | “increase read capacity” |

Đây là cặp phân biệt quan trọng nhất của tuần.

---

## 8. RDS backup và recovery

### 8.1. Automated backup

Automated backup tạo backup trong retention window và hỗ trợ point-in-time recovery trong phạm vi dữ liệu/log được giữ. Dùng cho operational recovery.

Cần cấu hình:

- backup window;
- retention;
- copy/retention requirement;
- encryption;
- backup storage cost;
- restore test.

Automated backup không thay thế DR cross-Region nếu Region failure nằm trong requirement.

### 8.2. Manual snapshot

Manual snapshot giữ đến khi xóa, phù hợp:

- backup trước thay đổi lớn;
- clone môi trường;
- migration;
- retention dài;
- restore database instance mới.

Snapshot có thể copy sang Region khác theo capability và cần kiểm tra KMS key.

### 8.3. Point-in-time recovery

PITR restore database về thời điểm trong retention window. Phù hợp user/app vô tình xóa dữ liệu hoặc cần quay lại trước incident.

PITR thường tạo database mới, không “rewind” instance đang chạy. Sau restore cần update endpoint/configuration và validate application.

### 8.4. RTO/RPO mapping

| Requirement | Hướng suy nghĩ |
|---|---|
| RPO vài giờ, RTO nhiều giờ | snapshot/backup restore |
| RPO nhỏ, RTO thấp trong Region | Multi-AZ + automated backup |
| DR Region | cross-Region replica/snapshot/Aurora Global Database |
| Read scaling | read replica/Aurora reader |
| Zero/near-zero data loss | replication và synchronous strategy theo engine |

Backup phải được restore test. Có backup nhưng không restore được chưa phải recovery strategy đáng tin cậy.

---

## 9. Aurora

Aurora là relational database compatible với MySQL/PostgreSQL, dùng storage architecture phân tán được quản lý bởi AWS.

### 9.1. Aurora cluster

Aurora cluster thường có:

- writer instance;
- reader instance/replica;
- cluster storage;
- writer/cluster endpoint;
- reader endpoint;
- instance endpoint;
- custom endpoint theo use case hỗ trợ.

### 9.2. Endpoint

| Endpoint | Dùng cho |
|---|---|
| Cluster/writer endpoint | read/write tới writer |
| Reader endpoint | read load balancing giữa reader |
| Instance endpoint | kết nối một instance cụ thể |
| Custom endpoint | nhóm instance theo thuộc tính/workload |

Ứng dụng không nên hard-code IP. Dùng endpoint để Aurora xử lý failover/reader selection theo thiết kế.

### 9.3. Aurora reader và failover

Aurora replicas có thể phục vụ read và được chọn làm failover target theo priority. Reader endpoint phân phối read giữa replicas theo behavior dịch vụ.

Aurora reader không tự biến mọi read thành strongly consistent với writer. Application vẫn cần hiểu replica lag và read-after-write requirement.

### 9.4. Aurora Serverless

Aurora Serverless phù hợp workload biến động hoặc không muốn provision capacity liên tục. Aurora Serverless v2 có khả năng scale compute theo load với behavior khác provisioned cluster.

Cân nhắc:

- cold/warm behavior;
- min/max capacity;
- connection handling;
- scaling latency;
- feature/engine compatibility;
- cost theo capacity;
- workload ổn định có thể phù hợp provisioned hơn.

### 9.5. Aurora Global Database

Aurora Global Database hỗ trợ một primary Region và secondary Regions để read local/DR. Replication giữa Region được tối ưu cho độ trễ thấp hơn snapshot-only pattern nhưng vẫn cần hiểu RPO, failover, write forwarding/secondary behavior và promotion process.

Dùng khi requirement là global read latency và cross-Region DR, không phải chỉ vì muốn nhiều Region.

---

## 10. RDS Proxy

RDS Proxy là managed database proxy giúp pool và reuse connections giữa application và RDS/Aurora.

Use case:

- Lambda tạo nhiều connection ngắn;
- burst traffic;
- database connection limit;
- failover/credential handling theo pattern hỗ trợ;
- tách application khỏi connection management.

Proxy không làm query nhanh hơn một cách tự động, không thay cache và không thay read replica. Cần kiểm tra transaction/session behavior, TLS, IAM authentication, Secrets Manager và target database support.

---

## 11. Caching fundamentals

Cache lưu bản sao dữ liệu gần application để giảm latency và số lần đọc database. Cache không nên là source of truth trừ khi workload chấp nhận mất/rebuild state.

### 11.1. Cache-aside

Flow:

~~~text
Application
   ↓ read
Cache hit? ── có → trả dữ liệu
   │ không
Database → trả dữ liệu → set cache → trả user
~~~

Write flow phổ biến:

~~~text
Write database → invalidate/update cache → response
~~~

Ưu điểm: application kiểm soát dữ liệu cần cache. Nhược điểm: cache miss, stale data, stampede và invalidation complexity.

### 11.2. TTL

TTL giới hạn thời gian key tồn tại. Chọn TTL theo:

- mức chấp nhận stale;
- tần suất thay đổi;
- cost DB;
- memory capacity;
- security/privacy;
- cache invalidation.

TTL không thay thế invalidation cho dữ liệu cần cập nhật ngay.

### 11.3. Cache stampede

Khi nhiều request cùng miss một key hot, tất cả truy vấn database đồng thời. Giảm bằng:

- request coalescing/single flight;
- lock ngắn hạn;
- jitter TTL;
- pre-warm;
- stale-while-revalidate;
- rate limit;
- fallback.

### 11.4. Cache failure

Cache có thể miss, evict, restart hoặc mất node. Application cần graceful fallback tới database, timeout ngắn, circuit breaker và không retry vô hạn.

---

## 12. ElastiCache Redis và Memcached

### Redis

Redis là in-memory data store có nhiều feature:

- data structures;
- TTL;
- replication/failover theo engine/deployment;
- persistence/snapshot theo cấu hình;
- pub/sub;
- sorted sets/counters;
- session/cache use case.

Phù hợp session store, cache-aside, leaderboard, rate limiting, distributed lock cẩn trọng và data structure cần feature.

### Memcached

Memcached là cache đơn giản, distributed memory cache:

- key-value đơn giản;
- scale ngang bằng node;
- không persistence;
- không replication/failover theo cách Redis;
- phù hợp ephemeral cache và giảm read load.

### So sánh

| Tiêu chí | Redis | Memcached |
|---|---|---|
| Data model | phong phú | key-value đơn giản |
| Persistence | có tùy cấu hình | không |
| Replication/failover | có tùy engine/topology | không theo pattern tương đương |
| Scale | replication/sharding theo thiết kế | dễ scale ngang |
| Use case | session, feature cache, counters | simple ephemeral cache |
| Độ phức tạp | cao hơn | đơn giản hơn |

Chọn Redis vì feature cần thiết, không phải chỉ vì “Redis luôn tốt hơn”. Chọn Memcached cho cache disposable đơn giản cần scale ngang và không cần persistence.

---

## 13. Session cache

Không giữ session quan trọng chỉ trên local EC2 nếu app chạy nhiều instance. Externalize session vào Redis/ElastiCache hoặc database phù hợp.

Pattern:

~~~text
User → ALB → bất kỳ app instance
                ↓
          Redis session store
~~~

Cần thiết kế:

- TTL session;
- logout/invalidation;
- encryption/TLS;
- failover behavior;
- memory eviction;
- session size;
- serialization compatibility;
- fallback nếu cache mất.

Sticky session ở load balancer có thể giảm thay đổi app nhưng làm scale/failover kém linh hoạt; externalized session thường bền vững hơn.

---

## 14. Cache invalidation và consistency

Các pattern:

| Pattern | Đặc điểm |
|---|---|
| Cache-aside | app đọc DB khi miss, tự set/invalidate |
| Write-through | ghi cache đồng thời/qua cache rồi DB theo implementation |
| Write-behind | cache ghi trước, flush sau; latency thấp nhưng rủi ro mất data |
| Read-through | cache layer tự lấy source khi miss nếu hỗ trợ |

SAA thường tập trung cache-aside. Khi write:

1. ghi source of truth;
2. invalidate hoặc update cache;
3. trả response sau khi state cần thiết đã an toàn.

Nếu update cache trước database và DB write fail, cache có thể chứa dữ liệu sai. Nếu DB trước rồi cache invalidate fail, stale data tồn tại đến TTL; ứng dụng cần retry/eventual repair.

---

## 15. Security, observability và cost

### Security

- đặt RDS/ElastiCache trong private subnets;
- SG chỉ cho app SG truy cập database/cache port;
- encryption at rest/in transit;
- Secrets Manager/Parameter Store;
- IAM role và IAM authentication nếu hỗ trợ;
- không log password/query chứa PII;
- rotate secret và test connection pool.

### Observability

RDS metrics: CPU, memory, storage, connections, IOPS, throughput, replica lag, free storage và deadlocks tùy engine.

ElastiCache metrics: cache hits/misses, evictions, memory, connections, CPU, replication lag và failover event.

Alert theo symptom/business impact, không chỉ CPU. Cache hit ratio cao nhưng dữ liệu stale vẫn là vấn đề correctness.

### Cost

Tính cả:

- DB instance;
- storage và IOPS;
- Multi-AZ standby;
- read replica;
- cross-Region transfer;
- backup/snapshot;
- KMS;
- cache nodes;
- data transfer;
- RDS Proxy.

Không thêm read replica hoặc cache chỉ vì “tăng hiệu năng”; đo bottleneck trước.

---

## 16. Lab bắt buộc

### Lab 1 — RDS private subnet

1. Tạo DB subnet group trong hai private subnets.
2. Tạo RDS engine test, không bật public access.
3. Tạo DB-SG chỉ nhận port từ App-SG.
4. Kết nối từ app/EC2 private.
5. Xác nhận Internet không truy cập trực tiếp database.
6. Bật encryption và lưu secret ngoài source.
7. Xóa database sau lab để tránh cost.

### Lab 2 — Multi-AZ và read replica

Nếu ngân sách cho phép:

- bật Multi-AZ để quan sát failover;
- tạo read replica;
- đọc từ reader và ghi vào writer;
- quan sát replica lag;
- test endpoint sau failover;
- ghi rõ Multi-AZ không phải read scaling.

Không chạy load test lớn trên account học tập.

### Lab 3 — Backup, snapshot và PITR

1. Bật automated backup retention.
2. Tạo manual snapshot.
3. Ghi dữ liệu test.
4. Xóa/thay đổi dữ liệu.
5. Restore snapshot sang instance mới.
6. Thử PITR về thời điểm trước thay đổi nếu engine hỗ trợ.
7. Xác nhận endpoint, SG, secret và data.
8. Xóa bản restore sau lab.

### Lab 4 — Cache-aside với ElastiCache

1. Tạo Redis/Memcached phù hợp budget.
2. Đặt cache trong private subnet.
3. SG chỉ cho app SG truy cập cache port.
4. Application đọc cache trước, miss thì đọc DB rồi set TTL.
5. Write DB rồi invalidate key.
6. Đo hit/miss, latency và fallback khi cache unavailable.
7. Xóa node/cache sau lab.

### Lab 5 — Database architecture review

Điền bảng:

| Requirement | Thiết kế | Lý do | Trade-off |
|---|---|---|---|
| HA trong AZ failure |  |  |  |
| Read scaling |  |  |  |
| RPO 15 phút |  |  |  |
| Global read latency |  |  |  |
| Session cho nhiều app instance |  |  |  |
| Giảm query lặp lại |  |  |  |

---

## 17. Bẫy đề thi SAA-C03

- Multi-AZ chủ yếu là HA/failover, không phải read scaling.
- Read replica dùng cho read scaling và có eventual consistency.
- RDS standby không phải reader thông thường.
- Aurora reader endpoint khác writer/cluster endpoint.
- Read replica không tự làm write multi-primary.
- RDS không phù hợp key-value cực lớn/schema linh hoạt như DynamoDB.
- RDS private database cần DB subnet group, route và SG đúng.
- SG database nên nhận từ application SG, không mở Internet.
- Backup có nhưng chưa test restore thì chưa chứng minh RTO.
- Automated backup/PITR khác manual snapshot.
- Snapshot restore thường tạo database instance mới.
- Cross-Region read replica có lag và transfer cost.
- Cache không phải source of truth nếu có thể mất/rebuild.
- TTL giúp giới hạn stale nhưng không giải quyết mọi invalidation.
- Redis và Memcached không phải lựa chọn giống nhau.
- RDS Proxy quản lý connection, không phải query cache.
- Cache hit ratio cao không chứng minh data correctness.
- Secrets không đặt trong code, AMI hoặc log.
- Read-after-write requirement có thể không phù hợp đọc ngay từ read replica/cache.
- Aurora Serverless không phải luôn rẻ hơn provisioned; workload pattern quyết định.
- ElastiCache private network path và SG vẫn cần cấu hình.

---

## 18. Câu hỏi tự luyện

### Câu 1

Một RDS primary bị lỗi và ứng dụng phải tiếp tục hoạt động trong cùng Region. Chọn gì?

**Đáp án:** Multi-AZ deployment/standby và ứng dụng dùng RDS endpoint, kèm retry/reconnect.

### Câu 2

Website read-heavy cần tăng read throughput nhưng không cần automatic failover writer. Chọn gì?

**Đáp án:** read replica hoặc Aurora reader tùy engine/workload.

### Câu 3

Read replica trả dữ liệu cũ ngay sau khi user update. Vì sao?

**Đáp án:** asynchronous replication tạo replica lag/eventual consistency; đọc write-critical data từ writer hoặc thiết kế consistency phù hợp.

### Câu 4

Business cần phục hồi database về thời điểm ngay trước thao tác xóa nhầm. Dùng gì?

**Đáp án:** point-in-time recovery trong retention window nếu hỗ trợ, hoặc restore backup/snapshot phù hợp.

### Câu 5

Cần giữ snapshot lâu dài trước migration. Dùng gì?

**Đáp án:** manual snapshot với retention, encryption và access control phù hợp.

### Câu 6

Ứng dụng Lambda tạo quá nhiều connection tới RDS. Giải pháp nào?

**Đáp án:** RDS Proxy/connection pooling, giới hạn concurrency và tối ưu lifecycle connection; không chỉ tăng DB instance.

### Câu 7

Nhiều EC2 cần dùng chung session khi scale-out. Chọn gì?

**Đáp án:** externalize session vào Redis/ElastiCache hoặc store phù hợp, không giữ session duy nhất trên local instance.

### Câu 8

Cache mất dữ liệu vẫn có thể rebuild từ database; cần key-value cache đơn giản và scale ngang. Chọn gì?

**Đáp án:** Memcached nếu không cần persistence/replication feature.

### Câu 9

Cần session, TTL, counters và data structures phong phú. Chọn gì?

**Đáp án:** Redis/ElastiCache for Redis.

### Câu 10

Sau khi ghi database, cache vẫn trả giá trị cũ. Cải thiện gì?

**Đáp án:** invalidate/update cache sau write, xử lý retry, TTL, versioning hoặc read-through consistency theo requirement.

### Câu 11

Cần global read latency thấp và DR cross-Region cho Aurora. Chọn gì?

**Đáp án:** Aurora Global Database theo requirement RTO/RPO, replication lag và failover plan.

### Câu 12

Database có encryption SSE/KMS nhưng app bị AccessDenied khi kết nối hoặc decrypt secret. Kiểm tra gì?

**Đáp án:** KMS/key policy, IAM role, Secrets Manager permission, SG/network, TLS và secret rotation/config.

---

## 19. Checklist hoàn thành tuần

### Kiến thức

- [ ] Giải thích RDS engine, DB subnet group, parameter/option group.
- [ ] Vẽ database private subnet và SG flow.
- [ ] Phân biệt Multi-AZ standby với read replica.
- [ ] Hiểu replica lag và eventual consistency.
- [ ] Phân biệt automated backup, manual snapshot và PITR.
- [ ] Chọn strategy theo RTO/RPO.
- [ ] Hiểu Aurora writer/reader/cluster endpoint.
- [ ] Nhận biết Aurora Serverless và Global Database.
- [ ] Biết use case của RDS Proxy.
- [ ] Phân biệt Redis và Memcached.
- [ ] Thiết kế cache-aside, TTL và invalidation.
- [ ] Nhận diện cache stampede và stale data.
- [ ] Hiểu KMS, Secrets Manager và secret rotation.

### Thực hành

- [ ] Đã tạo RDS trong private subnets.
- [ ] Đã kết nối app qua SG reference.
- [ ] Đã bật encryption và quản lý secret ngoài code.
- [ ] Đã test Multi-AZ/read replica nếu ngân sách cho phép.
- [ ] Đã tạo snapshot, restore và PITR demo.
- [ ] Đã triển khai cache-aside.
- [ ] Đã đo cache hit/miss và database load.
- [ ] Đã test fallback khi cache unavailable.
- [ ] Đã xóa DB, replica, cache, snapshot test sau lab.

Bạn sẵn sàng sang Tuần 8 khi có thể phân biệt HA, read scaling, backup/recovery và caching; đồng thời chọn RDS/Aurora/ElastiCache theo access pattern và RTO/RPO thay vì chỉ theo tên dịch vụ.

---

## 20. Tài liệu tham khảo

- [Amazon RDS User Guide](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/Welcome.html) — RDS engine, network và backup.
- [Amazon RDS Multi-AZ](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/Concepts.MultiAZ.html) — HA/failover.
- [Working with read replicas](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/USER_ReadRepl.html) — read scaling và replica.
- [Backing up and restoring an Amazon RDS DB instance](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/CHAP_CommonTasks.BackupRestore.html) — backup, snapshot và PITR.
- [Amazon Aurora User Guide](https://docs.aws.amazon.com/AmazonRDS/latest/AuroraUserGuide/Welcome.html) — cluster, endpoints và Aurora features.
- [Amazon RDS Proxy](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/rds-proxy.html) — connection pooling/proxy.
- [Amazon ElastiCache User Guide](https://docs.aws.amazon.com/AmazonElastiCache/latest/dg/WhatIs.html) — Redis và Memcached.
- [ElastiCache caching strategies](https://docs.aws.amazon.com/AmazonElastiCache/latest/dg/Strategies.html) — cache patterns.
- [AWS Secrets Manager](https://docs.aws.amazon.com/secretsmanager/latest/userguide/intro.html) — secret storage và rotation.
- [AWS KMS](https://docs.aws.amazon.com/kms/latest/developerguide/overview.html) — key management và encryption.


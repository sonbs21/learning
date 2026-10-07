# Tuần 15 — Security, HA và Disaster Recovery

> Mục tiêu: bảo vệ Kafka bằng authentication/authorization, thiết kế cluster chịu lỗi và có kế hoạch backup/restore với RPO/RTO rõ ràng.

## 1. Kết quả cần đạt

Sau bài học, bạn có thể:

- Phân biệt authentication và authorization.
- Hiểu TLS, SASL/SCRAM và SASL/OAUTH ở mức kiến trúc.
- Thiết kế ACL theo topic, group, cluster và transactional ID.
- Áp dụng principal, service account và least privilege.
- Lập kế hoạch secret rotation/certificate rotation.
- Phân biệt encryption in transit và encryption at rest.
- Hiểu multi-broker, replication factor và min.insync.replicas.
- Thiết kế rack awareness, availability zone và failure domain.
- Thực hiện rolling restart/rolling upgrade có rollback.
- Phân biệt replication, retention và backup.
- Định nghĩa RPO/RTO cho Kafka platform.
- Mô tả mirror/replication giữa cluster.
- Viết backup/restore runbook.
- Thực hiện failure drill an toàn trong lab.

## 2. Security model tổng quát

Kafka security có nhiều lớp:

    Client identity
        |
        v
    Authentication
        |
        v
    Principal
        |
        v
    Authorization / ACL
        |
        v
    Encryption in transit
        |
        v
    Data at rest / infrastructure security

Cần phân biệt:

- **Authentication:** client là ai?
- **Authorization:** client được làm gì?
- **Encryption:** dữ liệu trên đường truyền/đĩa có bị đọc trộm không?
- **Audit:** ai đã truy cập/thay đổi gì?
- **Secret management:** credential được lưu/rotate thế nào?

Bật TLS mà không có ACL vẫn có thể cho user đã authenticate đọc quá nhiều topic. Có ACL mà không mã hóa transport vẫn có thể lộ payload trên network.

## 3. Authentication

### 3.1 SSL/TLS client authentication

TLS bảo vệ:

- encryption in transit;
- server identity;
- tùy cấu hình, client identity bằng certificate/mTLS.

Mô hình:

    Client --TLS handshake--> Broker
       |                         |
       +-- verify server cert   |
       +-- present client cert  |

Cần quản lý:

- CA;
- broker certificate;
- client certificate;
- private key;
- truststore;
- keystore;
- hostname verification;
- certificate expiry;
- rotation.

TLS không tự quyết định client được đọc topic nào. Sau khi authenticate, principal phải được map vào ACL.

### 3.2 SASL/SCRAM

SASL/SCRAM dùng username/password đã hash/được broker quản lý theo cơ chế hỗ trợ.

Ưu điểm:

- dễ tích hợp service account;
- không cần client certificate cho mỗi service;
- rotation credential đơn giản hơn certificate trong một số môi trường.

Rủi ro:

- password leak;
- ghi password vào config/log;
- rotate không đồng bộ;
- credential dùng chung nhiều service;
- brute force nếu không có control.

Không commit SASL password vào Git. Dùng secret manager hoặc secret injection.

### 3.3 SASL/OAUTH

SASL/OAUTH dùng token/identity provider. Phù hợp môi trường có OAuth/OIDC và token lifecycle.

Cần thiết kế:

- issuer/audience;
- token expiry;
- refresh;
- clock skew;
- broker validation;
- scope/role mapping;
- behavior khi identity provider unavailable.

Đây là cơ chế phức tạp hơn SCRAM. Chọn khi ecosystem/security platform hỗ trợ tốt, không bật chỉ vì “enterprise”.

### 3.4 Chọn mechanism

| Cơ chế | Phù hợp | Cần lưu ý |
|---|---|---|
| TLS/mTLS | Identity bằng certificate, encryption mạnh | CA/cert rotation |
| SASL/SCRAM | Service account/password | Secret rotation |
| SASL/OAUTH | Identity provider/token | Token expiry/refresh |
| PLAINTEXT | Local lab cô lập | Không dùng production |

## 4. Authorization bằng ACL

### 4.1 ACL là gì?

ACL quyết định principal được:

- READ;
- WRITE;
- DESCRIBE;
- CREATE;
- DELETE;
- ALTER;
- ALTER_CONFIGS;
- CLUSTER_ACTION;
- IDEMPOTENT_WRITE;
- DESCRIBE_CONFIGS;

trên resource nào.

Resource thường gồm:

- cluster;
- topic;
- consumer group;
- transactional ID;
- delegation token tùy setup.

### 4.2 Producer ACL

Producer thường cần:

- WRITE topic;
- DESCRIBE topic;
- có thể cần IDEMPOTENT_WRITE trên cluster nếu bật idempotence;
- transactional ID permissions nếu dùng transaction.

Ví dụ conceptual:

    principal=User:order-service
      WRITE on Topic:prod.orders.events.v1
      DESCRIBE on Topic:prod.orders.events.v1
      IDEMPOTENT_WRITE on Cluster
      WRITE/DESCRIBE on TransactionalId:order-service-*

Exact ACL command phụ thuộc Kafka version/security config. Luôn test bằng service account thật.

### 4.3 Consumer ACL

Consumer thường cần:

- READ topic;
- DESCRIBE topic;
- READ group;
- DESCRIBE group tùy client/tool.

    principal=User:payment-service
      READ on Topic:prod.orders.events.v1
      READ on Group:order-payment

Consumer không nên có WRITE vào topic nghiệp vụ nếu không cần.

### 4.4 Admin ACL

Admin/operator có thể cần:

- DESCRIBE/ALTER topic;
- CREATE/DELETE topic;
- ALTER_CONFIGS;
- cluster operations;
- group inspection;
- metadata operations.

Không dùng admin principal cho application runtime. Admin quyền cao phải có break-glass, audit và MFA/approval theo platform.

### 4.5 ACL matrix

| Principal | Resource | Operations | Scope | Lý do |
|---|---|---|---|---|
| order-service | orders topic | WRITE, DESCRIBE | exact topic | publish order events |
| payment-service | orders topic | READ | exact topic | consume orders |
| payment-service | payment group | READ | exact group | commit offsets |
| analytics-stream | orders topic | READ | analytics group | aggregate |
| connect-sink | target topic | READ | exact topic/group | sink |
| platform-admin | cluster/topics | admin | controlled | operations |

Least privilege nghĩa là application chỉ có quyền cần thiết cho runtime.

## 5. Principal và service account

Một service nên có identity riêng:

    order-service
    payment-service
    inventory-service
    analytics-stream
    connect-worker

Không dùng:

    shared-kafka-user

vì:

- khó audit;
- không biết service nào gây lỗi;
- rotate ảnh hưởng mọi service;
- không thể cấp quyền khác nhau;
- compromise một service mở rộng blast radius.

Principal naming nên ổn định, không chứa secret. Ghi owner/on-call của mỗi identity.

## 6. Least privilege

Checklist:

- topic exact name thay vì wildcard rộng;
- group exact name;
- không cấp cluster admin cho producer;
- không cấp DELETE cho application;
- không cấp READ tất cả topic nếu chỉ cần một topic;
- transactional ID prefix giới hạn;
- Connect worker chỉ đọc/ghi topic được giao;
- Schema Registry quyền theo subject;
- secret scope theo service/environment;
- audit mọi thay đổi ACL.

Wildcard có thể cần trong một số platform automation, nhưng phải có lý do và review.

## 7. Encryption in transit

TLS bảo vệ:

- Kafka client ↔ broker;
- broker ↔ broker;
- controller ↔ controller;
- Connect ↔ Kafka;
- Schema Registry ↔ client;
- monitoring ↔ endpoint;
- cross-cluster replication.

Cần kiểm tra:

- listener protocol;
- truststore/keystore;
- hostname verification;
- cipher/protocol policy;
- certificate chain;
- expiry;
- client compatibility;
- performance overhead.

Không chỉ bật TLS ở external listener rồi để internal traffic PLAINTEXT mà không đánh giá threat model.

## 8. Secret rotation

### 8.1 Rotation objective

Secret rotation cần không làm gián đoạn dài:

    old credential valid
         |
         +--> distribute new credential
         |
         +--> test new credential
         |
         +--> switch clients
         |
         +--> revoke old credential

### 8.2 Quy trình

1. tạo credential mới;
2. cấp quyền tương đương;
3. đưa secret vào manager;
4. rollout client dùng secret mới;
5. kiểm tra auth errors;
6. chờ mọi client migrate;
7. revoke secret cũ;
8. audit và cập nhật inventory.

Không revoke credential cũ trước khi xác nhận toàn bộ instance đã reload.

### 8.3 Password hot reload

Không mặc định client tự reload secret. Có thể cần:

- restart pod;
- rolling restart;
- dynamic config;
- sidecar/secret reload;
- client-specific callback.

Kiểm tra behavior của version client/deployment.

## 9. Certificate rotation

Certificate rotation phức tạp hơn password:

- CA trust;
- server cert;
- client cert;
- chain;
- hostname;
- expiry;
- reload/restart;
- cross-cluster trust.

Chiến lược:

1. thêm CA/cert mới vào trust;
2. deploy truststore mới;
3. xác nhận trust cả old/new;
4. đổi server/client certificate;
5. rollout;
6. remove cert cũ sau grace period.

Theo dõi certificate expiry trước nhiều ngày. Expiry không được phát hiện khi production đã mất kết nối.

## 10. Encryption at rest

Encryption at rest thường do:

- disk encryption;
- cloud volume encryption;
- storage-level key management;
- filesystem encryption;
- backup encryption;
- object storage SSE/KMS.

Kafka broker không tự biến mọi storage thành encrypted nếu hạ tầng không cấu hình. Cần kiểm tra:

- data log;
- metadata log;
- internal topics;
- backup;
- connector offsets;
- secrets;
- snapshots;
- temp files;
- logs.

Encryption at rest không thay thế ACL và least privilege. User có quyền đọc data vẫn đọc được plaintext qua Kafka client.

## 11. High Availability

### 11.1 Multi-broker

HA cần nhiều broker để:

- replicate partition;
- chịu mất một broker;
- phân tán leader;
- rolling maintenance;
- scale traffic/storage.

Một broker với replication factor 1 chỉ là local lab, không phải HA.

### 11.2 Replication factor

Replication factor 3 thường có thể chịu mất một replica mà vẫn duy trì quorum/availability tùy min.insync.replicas và producer acks.

Cần ghi:

- RF;
- min.insync.replicas;
- producer acks;
- unclean leader election policy;
- rack/AZ placement;
- failure assumption.

### 11.3 min.insync.replicas

Ví dụ:

    RF=3
    min.insync.replicas=2
    acks=all

Khi ISR giảm còn 1, producer có thể fail thay vì xác nhận write không đủ durability. Đây là trade-off durability vs availability.

### 11.4 Leader distribution

HA không chỉ có đủ replica. Cần leader phân bố đều để:

- produce/fetch không dồn một broker;
- CPU/network/disk cân bằng;
- mất broker không gây overload broker còn lại.

## 12. Rack awareness và failure domain

### 12.1 Rack awareness

Broker metadata có thể mô tả rack/AZ/failure domain:

    broker-1 -> az-a
    broker-2 -> az-b
    broker-3 -> az-c

Replica assignment nên tránh đặt toàn bộ replica cùng failure domain.

### 12.2 Availability zone

Nếu ba replica cùng một AZ:

    AZ-A mất -> mất cả ba replica

RF=3 nhưng không có AZ diversity thì HA thực tế thấp hơn con số 3.

### 12.3 Failure domain

Xác định domain có thể cùng chết:

- host;
- rack;
- availability zone;
- region;
- storage system;
- network segment;
- control plane.

Capacity/DR plan phải nói rõ chịu được failure domain nào.

## 13. Retention, replication và backup

### Retention

Giữ record online trong một khoảng thời gian. Không phải backup bất biến.

### Replication

Giữ nhiều bản sao trong cluster để chịu broker/replica failure. Không bảo vệ mọi loại:

- xóa nhầm topic;
- producer phát dữ liệu sai;
- schema/logic lỗi;
- cluster bị compromise;
- region mất hoàn toàn.

### Backup

Bản sao để phục hồi sau deletion/corruption/cluster loss. Có thể gồm:

- topic data;
- topic config;
- partition/replica metadata;
- ACL;
- schemas;
- Connect configs/offsets;
- Streams app config/state plan;
- KRaft metadata/config;
- secrets reference;
- dashboards/runbooks.

## 14. RPO và RTO

### 14.1 RPO

RPO là lượng dữ liệu tối đa có thể mất sau disaster.

    RPO = 5 phút

Có nghĩa tổ chức chấp nhận mất tối đa khoảng dữ liệu tương đương 5 phút, tùy pipeline/backup.

### 14.2 RTO

RTO là thời gian tối đa để khôi phục dịch vụ.

    RTO = 30 phút

Cần đo từ incident đến service usable, không chỉ process start.

### 14.3 Ví dụ

| Workload | RPO | RTO | Thiết kế |
|---|---:|---:|---|
| Audit events | gần 0 | 1h | RF, backup, cross-cluster |
| Analytics | 1h | 4h | replay/source rebuild |
| Payment commands | gần 0 | 15m | RF, DR, idempotency |
| Dev telemetry | 1d | 1d | backup đơn giản |

RPO/RTO là business decision, không chỉ là config Kafka.

## 15. Backup scope

Backup cần trả lời:

- backup cái gì;
- tần suất;
- retention backup;
- encrypted ở đâu;
- immutable hay không;
- ai được restore;
- restore tới cluster mới hay cluster cũ;
- data/schema version;
- topic name migration;
- offset/group recovery;
- validation sau restore.

### 15.1 Topic data

Có thể backup:

- raw Kafka log bằng công cụ phù hợp;
- replicate sang cluster khác;
- sink sang object storage;
- export/replay event.

Không coi một cách là phù hợp cho mọi topic.

### 15.2 Metadata/config

Backup:

- topic names/configs;
- partition count;
- replication assignments;
- ACL;
- quotas;
- Schema Registry subjects/versions/config;
- Connect connector configs;
- consumer group policies;
- dashboards/alerts/runbooks.

Data không có metadata/schema có thể restore nhưng không dùng được.

## 16. Cross-cluster replication

Mirror/replication giữa cluster có thể dùng để:

- DR region;
- migration;
- active-passive;
- data sharing;
- backup online.

Cần thiết kế:

- source/destination topic naming;
- loop prevention;
- offset translation;
- ACL;
- schema registry;
- consumer failover;
- replication lag;
- bandwidth;
- conflict/active-active;
- RPO.

Cross-cluster replication không tự giải quyết business duplicate. Failover/failback có thể replay record.

## 17. Rolling restart

### Trước restart

- xác nhận cluster đủ broker/ISR;
- kiểm tra under-replicated/offline partition = 0;
- kiểm tra capacity còn lại;
- xác nhận maintenance window;
- backup/config snapshot;
- thông báo owner.

### Trong restart

1. restart từng broker;
2. chờ broker rejoin;
3. chờ ISR recover;
4. kiểm tra offline/under-replicated;
5. kiểm tra request error/latency;
6. mới chuyển broker tiếp theo.

Không restart nhiều broker cùng lúc nếu quorum/replica không chịu được.

### Sau restart

- all brokers up;
- controller/quorum healthy;
- ISR stable;
- no offline partition;
- producer/consumer errors normal;
- lag catch up;
- metrics stable.

## 18. Rolling upgrade

### Compatibility

Kiểm tra:

- broker/client protocol;
- metadata version;
- Kafka Streams;
- Connect;
- Schema Registry;
- JDK;
- serializers;
- monitoring;
- security mechanism.

### Quy trình

1. đọc release notes/upgrade guide của version pin;
2. test trên staging;
3. backup metadata/config;
4. xác định rollback version;
5. upgrade từng broker theo thứ tự hỗ trợ;
6. theo dõi ISR/latency/lag;
7. hoàn thành protocol/feature change theo procedure;
8. upgrade clients;
9. validate end-to-end;
10. đóng change khi metrics ổn định.

Không chỉ đổi image tag rồi coi là upgrade hoàn tất.

### Rollback

Cần biết:

- rollback có được hỗ trợ không;
- metadata feature đã nâng chưa;
- client cũ có tương thích không;
- schema/transaction behavior;
- data format;
- restore cluster mới nếu rollback in-place nguy hiểm.

## 19. Authentication/ACL migration

Khi bật security cho cluster hiện có:

1. inventory client/service;
2. tạo principal/service account;
3. cấp ACL tối thiểu;
4. enable listener mới;
5. test một client;
6. rollout client;
7. disable PLAINTEXT sau grace period;
8. monitor auth/ACL errors.

Không disable listener cũ trước khi migrate toàn bộ client.

## 20. Failure drill an toàn

Tất cả drill cần:

- môi trường lab/staging;
- scope rõ;
- approval;
- timeout;
- cleanup;
- success criteria;
- rollback;
- không dùng credential/data production.

### 20.1 Tắt một broker

Mục tiêu:

- kiểm tra leader election;
- ISR;
- producer/consumer reconnect;
- lag;
- data availability.

Theo dõi:

- offline partition;
- under-replicated;
- request errors;
- consumer lag;
- recovery time.

### 20.2 Làm consumer mất kết nối

Mục tiêu:

- xác nhận consumer reconnect;
- duplicate/replay behavior;
- lag growth/catch-up;
- alert.

### 20.3 Làm disk gần đầy

Trong lab dùng volume giới hạn hoặc dữ liệu synthetic. Không dùng command phá hoại trên host thật.

Theo dõi:

- disk usage;
- broker write error;
- retention cleanup;
- ISR;
- producer backpressure;
- runbook escalation.

### 20.4 Chặn Schema Registry

Mục tiêu:

- producer/consumer behavior khi registry unavailable;
- cache/fail closed;
- error/alert;
- data safety.

### 20.5 Rotate credential

1. tạo credential mới;
2. deploy test client;
3. rollout;
4. revoke cũ;
5. kiểm tra auth errors;
6. rollback nếu cần.

### 20.6 Restore topic/cluster

Khôi phục vào cluster/topic test:

1. provision cluster;
2. restore topic/config/schema;
3. restore data hoặc replicate;
4. kiểm tra key/partition/schema;
5. tạo consumer test;
6. xác minh event count/oldest/newest;
7. đo RTO;
8. ghi gap so với RPO.

## 21. ACL matrix cho Order Platform

| Principal | Topic | Group | Transactional ID | Quyền |
|---|---|---|---|---|
| order-service | orders.events | none | order-* | write/describe |
| payment-service | orders.events | payment | none | read/describe |
| inventory-service | orders.events | inventory | none | read/describe |
| analytics-stream | orders.events | analytics | analytics-* | read/write/transaction |
| connect-sink | orders.events | connect-orders | none | read |
| platform-admin | all controlled | all controlled | all controlled | admin/audit |

Điều chỉnh theo exact topic/environment. Không dùng wildcard nếu exact permission đủ.

## 22. Restore runbook

### Trước restore

- xác định incident và scope;
- chọn backup point;
- xác định RPO/RTO;
- kiểm tra backup integrity;
- provision target;
- chuẩn bị schema/ACL/config;
- thông báo consumer/downstream;
- quyết định topic name và group offset.

### Restore

1. restore cluster/network/security;
2. restore topic/config/partition;
3. restore Schema Registry;
4. restore data/replication;
5. kiểm tra earliest/latest offsets;
6. deploy test consumer;
7. validate sample event;
8. migrate application;
9. bật traffic từng bước;
10. theo dõi lag/error/duplicate.

### Sau restore

- compare event counts/checkpoints;
- kiểm tra missing/duplicate;
- verify schema;
- verify ACL;
- verify connector/Streams state;
- ghi actual RPO/RTO;
- cập nhật runbook.

## 23. Sai lầm phổ biến

### Nghĩ RF=3 là backup

Replication không bảo vệ xóa nhầm, corruption hay region loss.

### Chỉ mã hóa external listener

Threat model có thể yêu cầu internal encryption.

### Dùng shared admin account

Mất audit và tăng blast radius.

### ACL wildcard cho application

Vi phạm least privilege.

### Rotate secret bằng revoke trước rollout

Có thể làm toàn bộ service mất kết nối.

### Rolling restart nhiều broker cùng lúc

Có thể mất quorum/availability.

### Upgrade không có rollback

Metadata/protocol feature có thể làm rollback khó.

### Backup data nhưng quên schema/ACL/config

Restore dữ liệu nhưng không có cách đọc/vận hành.

### Restore consumer group offset mù

Có thể bỏ qua hoặc replay hàng loạt event.

### Failure drill trên production không có scope

Chaos không kiểm soát thành outage thật.

### Chỉ test broker down

DR còn cần credential, registry, schema, connector, downstream và application.

## 24. Câu hỏi tự kiểm tra

1. Authentication và authorization khác nhau thế nào?
2. TLS, SCRAM và OAUTH dùng khi nào?
3. Principal/service account là gì?
4. ACL producer cần những quyền nào?
5. Vì sao không dùng shared account?
6. Secret rotation nên làm theo thứ tự nào?
7. Certificate rotation khác password rotation ra sao?
8. Encryption at rest bảo vệ gì?
9. RF, min.insync.replicas và acks liên quan thế nào?
10. Rack/AZ awareness giải quyết failure domain nào?
11. Replication khác backup thế nào?
12. RPO và RTO là gì?
13. Backup cần gồm những metadata nào?
14. Cross-cluster replication có rủi ro duplicate nào?
15. Rolling restart cần kiểm tra gì trước broker tiếp theo?
16. Rolling upgrade cần rollback plan vì sao?
17. Khi nào ACL grant cluster-level cần thiết?
18. Restore group offset cần cẩn thận điều gì?
19. Failure drill cần điều kiện gì?
20. Vì sao Schema Registry/Connect cũng thuộc DR scope?

### Đáp án ngắn

1. Xác định danh tính so với quyền được phép.
2. Cơ chế identity/encryption khác nhau theo environment.
3. Identity riêng của service.
4. Write/describe topic, idempotent/transaction quyền nếu dùng.
5. Không audit/rotate/least privilege.
6. Add new, deploy clients, validate, revoke old.
7. Có CA/trust/hostname/restart và chain.
8. Dữ liệu trên storage/backup.
9. RF bản sao; ISR tối thiểu; acks mức xác nhận.
10. Tránh cùng failure domain.
11. Replication online/availability; backup recovery.
12. Mất dữ liệu tối đa và thời gian khôi phục.
13. Topic/config/ACL/schema/Connect/metadata.
14. Failover/failback có thể replay.
15. ISR/offline/capacity/metrics.
16. Protocol/metadata feature có thể không downgrade.
17. Khi client cần cluster-level operation như idempotence/transaction.
18. Replay/bỏ qua và group offset phải khớp data restore.
19. Lab scope, approval, timeout, cleanup, rollback.
20. Mất schema/connect config có thể khiến data không dùng được.

## 25. Checklist hoàn thành Tuần 15

- [ ] Tôi phân biệt authentication và authorization.
- [ ] Tôi hiểu TLS/mTLS, SASL/SCRAM và SASL/OAUTH.
- [ ] Tôi viết được ACL matrix producer/consumer/admin.
- [ ] Tôi dùng principal/service account riêng.
- [ ] Tôi áp dụng least privilege.
- [ ] Tôi có secret rotation procedure.
- [ ] Tôi có certificate rotation procedure.
- [ ] Tôi hiểu encryption at rest.
- [ ] Tôi hiểu RF, ISR, min.insync.replicas và acks.
- [ ] Tôi hiểu rack/AZ/failure domain.
- [ ] Tôi phân biệt replication, retention và backup.
- [ ] Tôi định nghĩa RPO/RTO giả định.
- [ ] Tôi có backup scope gồm data/metadata/schema/ACL/config.
- [ ] Tôi hiểu cross-cluster replication ở mức kiến trúc.
- [ ] Tôi có rolling restart procedure.
- [ ] Tôi có rolling upgrade và rollback procedure.
- [ ] Tôi hoàn thành failure drill broker down.
- [ ] Tôi thử credential rotation/schema registry block trong lab.
- [ ] Tôi có restore runbook và validation criteria.

## 26. Câu chốt cần nhớ

Security, HA và DR là ba lớp khác nhau nhưng phải được thiết kế cùng nhau:

1. Authentication biết client là ai; ACL quyết định client được làm gì.
2. Replication giúp chịu lỗi online; backup giúp phục hồi sau deletion/corruption/cluster loss.
3. RF và min.insync.replicas phải phù hợp failure domain và RPO/SLO.
4. Rotation/upgrade phải có overlap, monitoring và rollback.
5. Một DR plan chỉ đáng tin sau khi đã restore thử và đo được RPO/RTO thực tế.


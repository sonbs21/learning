# Tuần 16 — Testing và Capstone

> Mục tiêu: kiểm chứng toàn bộ kiến thức Kafka bằng test có chủ đích và hoàn thiện Order Event Platform có reliability, schema, observability, security và runbook.

## 1. Kết quả cần đạt

Sau bài học, bạn có thể:

- Viết unit test cho serializer, partitioner, schema, retry và idempotency.
- Viết integration test với Testcontainers.
- Dùng Spring Kafka EmbeddedKafka/Testcontainers.
- Viết contract test với Schema Registry.
- Test topology bằng Kafka Streams TopologyTestDriver.
- Kiểm thử broker unavailable, consumer crash, duplicate và rebalance.
- Chạy load test và soak test có metric.
- Chạy chaos test có scope, timeout và cleanup.
- Thiết kế và triển khai Order Event Platform.
- Viết architecture/topic/schema catalog và ADR.
- Chứng minh commit, retry, DLT, outbox và idempotency behavior.
- Xuất dashboard, benchmark report và operational runbook.
- Đánh giá Definition of Done bằng evidence thay vì “chạy được trên máy”.

## 2. Tư duy testing cho Kafka

Kafka system có nhiều boundary:

    HTTP/API
       |
       v
    Database transaction
       |
       v
    Outbox
       |
       v
    Producer -> Kafka
                   |
                   v
              Consumer
                   |
                   v
              Database/API
                   |
                   v
              Streams/Connect/Observability

Mỗi boundary có failure mode riêng. Một test happy path không chứng minh:

- không mất event;
- không duplicate side effect;
- schema tương thích;
- rebalance an toàn;
- DLT replay đúng;
- broker restart không phá flow;
- lag catch up trong SLA.

### 2.1 Test theo guarantee

Không viết test chỉ kiểm tra “message xuất hiện”. Hãy ghi guarantee:

    Given: producer commit order + outbox
    When: publisher crash sau Kafka ack
    Then: event có thể duplicate nhưng consumer không tạo duplicate payment

Hoặc:

    Given: Kafka-to-Kafka transaction abort
    When: output consumer read_committed
    Then: output aborted không được nhìn thấy

### 2.2 Test pyramid

    Unit tests
        |
    Component tests
        |
    Integration tests
        |
    Contract tests
        |
    E2E tests
        |
    Load/soak/failure/chaos tests

Unit nhanh và nhiều. E2E ít hơn nhưng kiểm tra flow thật. Chaos chỉ chạy trong scope được kiểm soát.

## 3. Test layers

### 3.1 Unit test

Test một class/function không cần broker:

- serializer/deserializer;
- partitioner;
- event validation;
- retry classifier;
- backoff;
- idempotency decision;
- mapper;
- topic naming;
- schema envelope.

### 3.2 Component test

Test một service với dependency giả hoặc broker local:

- producer callback;
- consumer listener;
- ack mode;
- error handler;
- DLT recoverer;
- database transaction;
- outbox publisher.

### 3.3 Integration test

Test nhiều thành phần thật:

- Kafka broker;
- PostgreSQL;
- Schema Registry;
- Connect;
- Spring Boot service;
- Kafka Streams topology.

Dùng Testcontainers hoặc EmbeddedKafka phù hợp mục tiêu.

### 3.4 Contract test

Test compatibility producer/consumer:

- schema v1/v2;
- serializer/Deserializer;
- subject/compatibility;
- event envelope;
- required/default/enum;
- consumer cũ với data mới.

### 3.5 End-to-end test

Test flow:

    POST /orders
       |
       v
    order-service
       |
       v
    Kafka
       |
       +--> payment
       +--> inventory
       +--> notification
       +--> analytics
       +--> audit

E2E cần assert business result, không chỉ assert log.

### 3.6 Non-functional test

- load test;
- soak test;
- failover;
- restart;
- rebalance;
- disk pressure;
- network outage;
- schema registry outage;
- credential rotation.

## 4. Test data và fixture

Fixture tốt cần:

- eventId ổn định;
- orderId/customerId rõ;
- timestamp kiểm soát được;
- schema version;
- correlationId;
- payload hợp lệ và invalid case;
- duplicate fixture;
- out-of-order fixture;
- large payload;
- tombstone nếu có.

Ví dụ:

    OrderCreated(
        eventId = "evt-001",
        orderId = "ORD-001",
        customerId = "CUS-001",
        totalAmount = 1250000,
        occurredAt = fixedInstant
    )

Không dùng thời gian hiện tại tùy tiện trong test window/ordering. Dùng Clock cố định hoặc timestamp fixture.

## 5. Unit test serializer

### 5.1 Happy path

Kiểm tra:

- object serialize thành bytes;
- deserialize trả object tương đương;
- null handling;
- field names/types;
- timestamp format;
- eventVersion.

### 5.2 Invalid input

Test:

- eventId rỗng;
- orderId null;
- amount âm;
- currency sai;
- occurredAt null;
- payload quá lớn;
- unsupported version.

Kỳ vọng:

- exception đúng loại;
- không gửi Kafka;
- error message không lộ secret;
- không retry mù.

### 5.3 Round-trip property

    object -> serialize -> deserialize -> object

So sánh field business, không nhất thiết so sánh object reference.

### 5.4 Compatibility fixture

Giữ fixture schema v1/v2 trong repository test để consumer đảm bảo đọc lịch sử.

## 6. Unit test partitioner

Kiểm tra:

- cùng key cho cùng partition trong config cố định;
- null key phân bố theo policy;
- key cardinality;
- partition count;
- custom partitioner;
- empty/invalid key;
- hot key detection nếu có logic.

Không assert partition cụ thể nếu partitioner/config version không đảm bảo mapping cố định. Assert property cần thiết, ví dụ cùng key nhất quán.

## 7. Unit test retry và idempotency

### Retry classifier

Cases:

- transient exception -> retry;
- permanent exception -> recover;
- serialization -> no retry;
- auth -> alert/fail;
- timeout -> retry với idempotency;
- max attempts -> DLT.

### Idempotency

Cases:

1. event mới -> process;
2. eventId đã xử lý -> skip/return existing result;
3. cùng business key khác eventId -> business policy;
4. crash trước commit -> retry;
5. duplicate concurrent -> unique constraint thắng;
6. result đã tồn tại -> không tạo side effect lần hai.

Test cả database constraint thật ở integration layer, vì mock không chứng minh race condition.

## 8. Unit test schema và envelope

Assert:

- eventType;
- eventVersion;
- eventId;
- occurredAt;
- correlationId;
- producer;
- payload;
- unknown field policy;
- required/default;
- PII field policy.

Một envelope invalid phải bị reject ở boundary, không để lọt vào topic chính nếu contract yêu cầu.

## 9. Testcontainers

Testcontainers chạy dependency thật trong container:

    @Container
    static KafkaContainer kafka =
        new KafkaContainer(
            DockerImageName.parse("apache/kafka:PINNED_VERSION")
        );

    @Container
    static PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>(
            "postgres:PINNED_VERSION"
        );

API class/image thay đổi theo version. Pin image, không dùng latest.

### 9.1 Khi dùng Testcontainers

Phù hợp khi cần test:

- network/bootstrap;
- broker thật;
- PostgreSQL transaction/outbox;
- schema registry;
- connector;
- serializer integration;
- container lifecycle.

### 9.2 Test isolation

Mỗi test hoặc test class cần:

- topic unique hoặc cleanup rõ;
- groupId unique;
- database schema riêng;
- container reset strategy;
- timeout;
- no dependency on previous offsets.

Không để test pass vì record còn sót từ test trước.

## 10. EmbeddedKafka và Spring Kafka test

EmbeddedKafka tiện cho:

- Spring listener;
- KafkaTemplate;
- ack/commit;
- listener container;
- basic topic behavior.

Hạn chế:

- không mô phỏng đầy đủ multi-broker HA;
- network/container khác production;
- không chứng minh replication/ISR;
- behavior version/config có thể khác cluster thật.

Dùng EmbeddedKafka cho test nhanh; dùng Testcontainers cho integration gần production hơn.

## 11. Integration test producer/consumer

Flow:

1. start broker;
2. create topic;
3. start producer service;
4. start consumer/listener;
5. publish event;
6. await business result;
7. assert eventId/orderId;
8. assert metadata nếu cần;
9. assert committed offset;
10. cleanup.

Không dùng sleep cố định dài. Dùng Awaitility hoặc cơ chế await có timeout.

Test assertion:

- event tới đúng group;
- key đúng;
- partition hợp lệ;
- side effect đúng một lần theo policy;
- error/retry/DLT đúng;
- test fail nếu listener không nhận.

## 12. Integration test outbox

### Happy path

    DB transaction:
      insert order
      insert outbox
      commit

Publisher đọc outbox và publish Kafka. Assert:

- order tồn tại;
- outbox tồn tại;
- Kafka event đúng;
- published marker/event state được cập nhật.

### DB rollback

Cố tình fail business transaction. Assert:

- order không tồn tại;
- outbox không tồn tại;
- Kafka không có event.

### Kafka unavailable

1. commit DB + outbox;
2. Kafka unavailable;
3. publisher retry;
4. Kafka phục hồi;
5. event cuối cùng được publish.

### Crash sau publish

1. publisher gửi Kafka thành công;
2. process crash trước mark published;
3. publisher restart;
4. event có thể duplicate;
5. consumer dedup không tạo side effect hai lần.

## 13. Integration test consumer/inbox

Flow:

    Kafka event
       |
       v
    insert inbox eventId + business update
       |
       v
    DB commit
       |
       v
    Kafka offset commit

Test:

- event mới;
- duplicate cùng eventId;
- crash trước DB commit;
- crash sau DB commit trước offset commit;
- DB unavailable;
- unique constraint race;
- restart consumer.

Assert business state, processed marker và offset riêng rẽ.

## 14. Contract test Schema Registry

Test matrix:

| Producer schema | Consumer | Kỳ vọng |
|---|---|---|
| v1 | v1 | pass |
| v2 optional | v1 | theo forward policy |
| v1 | v2 | theo backward policy |
| v2 | v2 | pass |
| breaking v3 | v1 | reject/fail có chủ đích |
| enum mới | consumer cũ | unknown policy rõ |

CI phải fail nếu schema incompatible theo policy. Không đợi deploy staging mới biết registry reject.

## 15. Test Kafka Streams bằng TopologyTestDriver

Test:

- stateless filter/map/branch;
- aggregate/count/reduce;
- window boundary;
- late event/grace;
- stream-table join;
- stream-stream join;
- tombstone;
- repartition expectation;
- state store;
- output topic.

Tách topology builder khỏi application bootstrap để test dễ:

    Topology topology = new OrderTopology(props).build();

TopologyTestDriver cho output deterministic với input timestamp kiểm soát được.

Không dùng TopologyTestDriver để kết luận:

- broker HA;
- network failure;
- rebalance;
- replication;
- Schema Registry outage;
- production disk restore.

## 16. Test failure broker unavailable

Scope:

1. start broker;
2. publish/consume baseline;
3. stop broker hoặc block connection trong lab;
4. observe producer timeout/retry;
5. observe consumer reconnect/lag;
6. start broker;
7. verify ISR/metadata;
8. verify catch-up;
9. check duplicate/lost event theo guarantee.

Ghi timeline:

    t0 baseline
    t1 failure injected
    t2 first error
    t3 alert
    t4 recovery
    t5 lag catch-up

Không dùng lệnh phá hủy trên workspace/production. Dùng container stop/network isolation có rollback.

## 17. Test consumer crash

Các điểm crash:

- sau poll trước process;
- sau side effect trước commit;
- sau commit;
- trong retry;
- trong DLT publish;
- trong rebalance revoke.

Assert:

- offset behavior;
- duplicate;
- inbox/idempotency;
- lag;
- group rejoin;
- no silent loss.

## 18. Test duplicate

Tạo duplicate từ:

- producer retry;
- outbox crash;
- consumer crash;
- DLT replay;
- manual republish;
- same eventId;
- same business operation khác eventId.

Assert:

- eventId dedup;
- business key unique;
- payment idempotency key;
- no duplicate inventory reservation;
- audit ghi nhận duplicate nếu cần.

## 19. Test rebalance

1. topic có ít nhất 3 partition;
2. group có 2 consumer;
3. publish traffic;
4. add consumer;
5. stop consumer;
6. xử lý record chậm;
7. observe revoke/assign;
8. kiểm tra commit contiguous offset;
9. assert no uncontrolled side effect duplicate;
10. đo rebalance duration.

Đây là integration test, không unit mock consumer assignment.

## 20. Load test

### 20.1 Mục tiêu

- throughput target;
- max sustainable rate;
- p95/p99 latency;
- lag behavior;
- CPU/disk/network;
- failure recovery;
- scale-out curve.

### 20.2 Giữ biến cố định

Ghi rõ:

- record size/distribution;
- key distribution;
- partition count;
- RF;
- compression;
- acks;
- producer count;
- consumer count/concurrency;
- batch;
- retention;
- downstream behavior.

### 20.3 Pass criteria

Ví dụ:

    peak 20.000 records/s
    producer p99 < 200ms
    consumer oldest age < 30s
    error rate < 0.1%
    no uncontrolled DLT
    catch-up < 10 phút sau 5 phút outage

Các giá trị chỉ là ví dụ. Capstone phải chọn SLA cụ thể.

## 21. Soak test

Soak test chạy lâu để lộ:

- memory leak;
- disk growth;
- state store growth;
- segment cleanup;
- connector offset issue;
- consumer lag drift;
- rebalance flapping;
- retry storm;
- metric cardinality growth;
- certificate/credential expiry;
- log volume.

Thời lượng tùy môi trường nhưng phải đủ qua warm-up và nhiều chu kỳ batch/cleanup.

Ghi:

- baseline;
- peak;
- drift theo thời gian;
- restart;
- alert;
- final data reconciliation.

## 22. Chaos test

Chaos test phải có:

- hypothesis;
- scope;
- blast radius;
- timeout;
- abort switch;
- expected signals;
- rollback;
- cleanup;
- owner;
- evidence.

Ví dụ hypothesis:

    Nếu mất một broker, producer/consumer vẫn phục vụ
    trong giới hạn SLA và ISR phục hồi trong 5 phút.

Chaos test không phải chạy lệnh ngẫu nhiên. Không test production khi chưa có approval/runbook.

## 23. Capstone: Order Event Platform

### 23.1 Bài toán

Khi người dùng tạo order, nhiều service độc lập xử lý:

- payment;
- inventory;
- notification;
- audit;
- analytics.

Mục tiêu là chứng minh hệ thống chịu được retry, duplicate, failure, schema evolution và vận hành quan sát được.

### 23.2 Thành phần

- order-service: REST API, PostgreSQL và transactional outbox;
- outbox-publisher: publish schema event, idempotent producer;
- payment-service: consumer group xử lý thanh toán;
- inventory-service: giữ/trừ tồn kho;
- notification-service: gửi email giả lập;
- analytics-stream: Kafka Streams aggregate theo customer/product;
- Kafka Connect: sink event hoặc CDC sang PostgreSQL/object storage;
- Schema Registry: quản lý event schema;
- Prometheus/Grafana: metrics và dashboard.

### 23.3 Topic design

    orders.v1.events
    payments.v1.commands
    inventory.v1.commands
    notifications.v1.commands
    orders.v1.dlt
    payments.v1.dlt
    analytics.v1.repartition

Key:

- order event: orderId để giữ ordering của một order;
- customer event: customerId nếu cần ordering theo customer;
- không dùng một key cố định cho mọi record.

### 23.4 Event envelope

    {
      "eventId": "uuid",
      "eventType": "order.created",
      "eventVersion": 1,
      "occurredAt": "2026-10-02T10:00:00Z",
      "correlationId": "uuid",
      "causationId": "uuid",
      "producer": "order-service",
      "payload": {
        "orderId": "ORD-1001",
        "customerId": "CUS-10",
        "total": 125000
      }
    }

### 23.5 Required behavior

1. Tạo order và outbox trong cùng database transaction.
2. Outbox publisher dùng idempotent producer và kiểm tra send result.
3. Event có eventId, type, version, timestamp, correlationId và payload.
4. Consumer commit offset sau business operation.
5. Duplicate event không tạo duplicate payment/inventory.
6. Lỗi transient retry có backoff; lỗi permanent vào DLT.
7. Có DLT replay command an toàn.
8. Kafka Streams tạo dashboard doanh thu theo cửa sổ thời gian.
9. Kafka Connect đồng bộ dữ liệu tới sink.
10. Có metrics lag, DLT, processing latency, throughput và broker health.
11. Có ACL cho từng service và secret ngoài Git.
12. Có Docker Compose cho local và tài liệu deploy staging.

## 24. Capstone phases

### Phase 1 — Foundation

Deliverables:

- repository;
- Docker Compose;
- pinned versions;
- Kafka local;
- PostgreSQL;
- Schema Registry;
- README setup;
- health check;
- basic topic creation.

Acceptance:

- stack khởi động reproducibly;
- no latest tags;
- README commands pass;
- secrets không nằm trong Git.

### Phase 2 — Order service và outbox

Deliverables:

- POST /orders;
- DB transaction;
- outbox table;
- event ID/correlation ID;
- validation;
- unit/integration test.

Acceptance:

- DB rollback không có outbox;
- success có order + outbox;
- invalid input không tạo event.

### Phase 3 — Outbox publisher

Deliverables:

- poll outbox;
- publish schema-aware event;
- send result;
- retry;
- mark published;
- idempotency.

Acceptance:

- Kafka down không mất outbox;
- restart publish được;
- crash after ack không tạo duplicate business effect.

### Phase 4 — Payment/inventory/notification

Deliverables:

- group riêng;
- consumer commit strategy;
- idempotency/inbox;
- transient retry;
- permanent DLT;
- metrics.

Acceptance:

- mỗi group nhận event độc lập;
- duplicate không double-charge/double-reserve;
- DLT record có metadata;
- replay subset an toàn.

### Phase 5 — Schema Registry

Deliverables:

- schema catalog;
- subject/version;
- compatibility;
- contract test;
- migration fixture.

Acceptance:

- optional field evolution pass;
- breaking change bị reject;
- consumer version cũ/mới có migration plan.

### Phase 6 — Kafka Streams

Deliverables:

- order count/revenue by customer/product;
- tumbling window;
- state store;
- changelog;
- topology test.

Acceptance:

- window boundary đúng;
- late event policy rõ;
- state restore test pass;
- hot key được document.

### Phase 7 — Kafka Connect

Deliverables:

- sink PostgreSQL/object storage;
- connector config;
- secret reference;
- task status;
- DLQ/retry;
- runbook.

Acceptance:

- schema mapping đúng;
- task restart recover;
- target unavailable có backoff;
- connector config không có plaintext secret.

### Phase 8 — Observability/security

Deliverables:

- Prometheus/Grafana;
- lag/DLT/latency dashboard;
- structured logs;
- trace/correlation/event ID;
- ACL matrix;
- TLS/SASL design;
- runbook.

Acceptance:

- biết lag do producer hay consumer;
- broker health dashboard;
- security identity tách service;
- alert có owner/runbook.

### Phase 9 — Failure/load/DR

Deliverables:

- broker restart test;
- consumer crash test;
- duplicate test;
- rebalance test;
- load/soak report;
- backup/restore plan;
- RPO/RTO assumption.

Acceptance:

- defined failure không gây message loss ngoài guarantee;
- catch-up time đo được;
- restore validation pass;
- rollback/runbook rõ.

## 25. Test matrix capstone

| Scenario | Expected behavior | Evidence |
|---|---|---|
| Happy path order | Payment/inventory/notification nhận | DB/log/metrics |
| DB rollback | Không có Kafka event | DB/outbox test |
| Kafka unavailable | Outbox giữ event, retry | publisher logs/status |
| Publisher crash after ack | Duplicate kỹ thuật, không duplicate business | eventId/inbox |
| Consumer crash before commit | Reprocess, idempotent result | offset/DB |
| Transient downstream | Retry backoff rồi success | attempt metrics |
| Permanent validation | DLT, không loop | DLT headers |
| DLT replay | Kết quả đúng, không side effect lặp | audit/idempotency |
| Broker restart | Reconnect/recover | ISR/lag timeline |
| Rebalance | assignment đổi, commit an toàn | listener logs |
| Schema optional field | Consumer cũ/mới theo policy | contract CI |
| Breaking schema | Registry/CI reject | compatibility report |
| State restore | Aggregate phục hồi | state/changelog test |
| Connector target down | Retry/task status rõ | connector metrics |
| Security denied | Request fail, audit | ACL/auth logs |

## 26. README và tài liệu bắt buộc

README phải có:

- mục tiêu;
- architecture diagram;
- quick start;
- pinned versions;
- env variables;
- topic catalog;
- schema catalog;
- consumer groups;
- ownership;
- local/staging difference;
- test commands;
- benchmark commands;
- failure drill commands;
- dashboard links;
- runbook links;
- cleanup instructions.

Không tạo top-level doc trùng lặp nếu repository đã có cấu trúc docs. Đặt thông tin vào docs hiện có theo ownership.

## 27. Architecture Decision Records

Tối thiểu có ADR:

### ADR-001: Partition key

- context;
- decision;
- orderId/customerId rationale;
- ordering;
- hot key risk;
- alternatives;
- consequences.

### ADR-002: Schema format

- JSON/Avro/Protobuf;
- tooling;
- compatibility;
- language;
- migration.

### ADR-003: Delivery semantics

- at-least-once/effectively-once;
- idempotency;
- transaction boundary;
- non-goals.

### ADR-004: Retry/DLT

- transient/permanent;
- blocking/non-blocking;
- ordering;
- replay.

### ADR-005: Outbox/inbox

- DB/Kafka gap;
- publisher;
- duplicate handling;
- cleanup.

### ADR-006: Security/HA

- auth;
- ACL;
- TLS;
- RF/min ISR;
- failure domain;
- DR.

## 28. Definition of Done

Capstone hoàn thành khi:

- README có architecture diagram, topic catalog, schema catalog và ownership.
- Có integration test happy path, duplicate, retry, DLT, rebalance và broker restart.
- Có benchmark report với partition count, producer config và consumer concurrency.
- Có dashboard và runbook.
- Có ADR giải thích partition key, retention, schema format, retry và outbox.
- Có threat model ngắn cho ACL, TLS, PII và secret.
- Không có message loss trong test failure đã định nghĩa.
- Có test report với pass/fail và limitation.
- Có rollback/cleanup cho lab và deployment.
- Có RPO/RTO assumption và restore evidence.

## 29. Lịch học tuần 16

| Buổi | Thời lượng | Nội dung |
|---|---:|---|
| Buổi 1 | 90 phút | Thiết kế test matrix và topology |
| Buổi 2 | 120 phút | Viết unit/integration/contract test |
| Buổi 3 | 120 phút | Dựng capstone happy path |
| Buổi 4 | 120 phút | Failure/rebalance/replay test |
| Buổi 5 | 120 phút | Load/soak/chaos có kiểm soát |
| Buổi 6 | 90 phút | Dashboard, runbook, ADR |
| Buổi 7 | 60 phút | Demo, review, Definition of Done |

Tỷ lệ nên dùng:

- 20% thiết kế;
- 40% code;
- 25% test/failure;
- 15% tài liệu/review.

## 30. Capstone review rubric

### Architecture — 20%

- topology rõ;
- boundary đúng;
- topic/key hợp lý;
- ownership.

### Reliability — 20%

- outbox/inbox;
- retry/DLT;
- idempotency;
- consistency guarantee.

### Testing — 20%

- unit;
- integration;
- contract;
- failure;
- load/soak.

### Operations — 15%

- metrics;
- dashboard;
- logs/traces;
- alerts;
- runbook.

### Security/HA/DR — 15%

- ACL;
- secret;
- TLS design;
- RF/failure domain;
- backup/RPO/RTO.

### Documentation — 10%

- README;
- ADR;
- schema/topic catalog;
- benchmark/failure report.

Không chấm “nhiều code” nếu không có evidence test và operation.

## 31. Sai lầm phổ biến

### Chỉ test happy path

Kafka failure thường nằm ở window giữa side effect và commit.

### Mock mọi thứ

Mock không chứng minh serializer, offset, rebalance, network hay broker behavior.

### Dùng latest image

Kết quả không reproducible và version compatibility không rõ.

### Test không unique group/topic

Offset cũ làm test pass/fail ngẫu nhiên.

### Sleep cố định trong integration test

Test chậm/flaky. Dùng await timeout.

### Chaos không có cleanup

Làm môi trường còn lỗi sau test.

### Đo load không ghi assumption

Con số không thể tái hiện hoặc so sánh.

### E2E không kiểm tra duplicate

Happy path pass nhưng retry payment nguy hiểm.

### DLT replay toàn bộ

Có thể tạo side effect trùng lặp.

### Capstone quá rộng ngay từ đầu

Nên làm vertical slice order -> event -> một consumer trước, sau đó mở rộng.

### Không ghi giới hạn

Một bài capstone tốt phải nói rõ guarantee nào không cung cấp.

## 32. Câu hỏi tự kiểm tra

1. Unit test Kafka nên test gì?
2. Vì sao integration test cần broker thật?
3. EmbeddedKafka khác Testcontainers thế nào?
4. Contract test cần kiểm tra các version nào?
5. TopologyTestDriver không chứng minh điều gì?
6. Load test cần cố định những biến nào?
7. Soak test tìm loại lỗi nào?
8. Chaos test cần guardrail gì?
9. Capstone outbox giải quyết failure window nào?
10. Vì sao consumer cần inbox/idempotency?
11. Broker restart test phải assert gì?
12. Rebalance test phải assert gì?
13. Schema breaking test phải assert gì?
14. Connector test cần kiểm tra task/status/offset gì?
15. Definition of Done cần evidence nào?
16. Vì sao README/topic/schema catalog quan trọng?
17. Khi nào test pass nhưng hệ thống vẫn chưa reliable?
18. RPO/RTO cần thể hiện trong capstone ra sao?

### Đáp án ngắn

1. Serializer, partitioner, validation, retry, idempotency.
2. Để kiểm tra protocol/offset/network/listener thực.
3. Embedded nhanh; Testcontainers gần broker/dependency thật hơn.
4. Producer/consumer cũ-mới và breaking case.
5. HA/network/rebalance/replication.
6. Payload, partition, RF, compression, producer/consumer config.
7. Leak, drift, disk/lag/rebalance dài hạn.
8. Scope, timeout, abort, rollback, cleanup.
9. DB business change và event intent cùng transaction.
10. Chặn side effect duplicate sau crash/retry.
11. Reconnect, ISR, lag, no undefined loss.
12. Assignment, commit ordering, duplicate policy.
13. Registry/CI reject hoặc migration rõ.
14. Mapping, failure/retry/DLQ/restart/lag.
15. Test report, metrics, logs, DB/output và docs.
16. Giúp setup, ownership và vận hành reproducible.
17. Chưa test failure/duplicate/restore hoặc chỉ mock.
18. Assumption, target, measured restore/catch-up time.

## 33. Checklist hoàn thành Tuần 16

- [ ] Tôi có test pyramid cho project.
- [ ] Tôi viết unit test serializer/partitioner/schema.
- [ ] Tôi test retry và idempotency.
- [ ] Tôi chạy integration test với broker/database thật.
- [ ] Tôi dùng EmbeddedKafka hoặc Testcontainers đúng mục tiêu.
- [ ] Tôi có contract test với Schema Registry.
- [ ] Tôi có TopologyTestDriver test.
- [ ] Tôi test broker unavailable.
- [ ] Tôi test consumer crash và duplicate.
- [ ] Tôi test rebalance.
- [ ] Tôi có load/soak test.
- [ ] Tôi có chaos test scope/timeout/cleanup.
- [ ] Tôi hoàn thành Order Event Platform vertical slice.
- [ ] Tôi có outbox, consumer groups, DLT, schema và metrics.
- [ ] Tôi có topic/schema/ownership catalog.
- [ ] Tôi có benchmark report.
- [ ] Tôi có dashboard/runbook.
- [ ] Tôi có ADR và threat model.
- [ ] Tôi có RPO/RTO và restore evidence.
- [ ] Tôi kiểm tra Definition of Done bằng evidence.

## 34. Câu chốt cần nhớ

Capstone không phải là gom thật nhiều công nghệ vào Docker Compose. Capstone là chứng minh một hệ thống event-driven có thể hiểu được, kiểm thử được, quan sát được và phục hồi được khi lỗi.

Năm ý cần nhớ:

1. Test phải mô phỏng failure window, không chỉ happy path.
2. Integration/contract/failure test mới chứng minh các boundary Kafka thực sự hoạt động.
3. Order Event Platform nên xây theo vertical slice rồi mở rộng dần.
4. Mọi guarantee phải có evidence: test, metric, log, trace hoặc report.
5. Một hệ thống hoàn chỉnh cần code, schema, security, observability, runbook và rollback cùng nhau.


# Tuần 12 — Kafka Connect và Data Integration

> Mục tiêu: biết khi nào dùng Kafka Connect thay vì tự viết consumer/producer, cấu hình source/sink connector an toàn và vận hành task khi lỗi hoặc lag tăng.

## 1. Kết quả cần đạt

Sau bài học, bạn có thể:

- Phân biệt source connector và sink connector.
- Hiểu Connect worker, connector, task, config, offset và status.
- So sánh standalone mode và distributed mode.
- Cấu hình JDBC source/sink ở mức cơ bản.
- Mô tả tích hợp object storage và Elasticsearch/OpenSearch.
- Hiểu SMT và data mapping.
- Phân biệt JDBC polling với CDC bằng Debezium.
- Cấu hình tolerance, retry và dead-letter queue.
- Quản lý plugin path và version compatibility.
- Không ghi plaintext secret trong connector config.
- Theo dõi connector/task restart và lag.
- Viết runbook khi task FAILED, connector PAUSED hoặc lag tăng.
- Quyết định khi nào dùng Connect và khi nào tự viết application.

## 2. Kafka Connect là gì?

Kafka Connect là framework để di chuyển dữ liệu giữa Kafka và hệ thống bên ngoài:

    Database --------> Kafka
    Kafka -----------> Database
    Kafka -----------> Object storage
    Kafka -----------> Search index

Connect cung cấp:

- worker runtime;
- connector plugin;
- task để chạy song song;
- REST API;
- config storage;
- offset storage;
- status storage;
- error handling;
- retry/DLQ;
- lifecycle management.

Connect không tự biến dữ liệu thành business event đúng nghĩa. Nó là data integration framework. Semantics của payload, key, schema, ordering, duplicate và consistency vẫn phải được thiết kế.

## 3. Source và sink connector

### 3.1 Source connector

Source đọc hệ thống bên ngoài và ghi vào Kafka:

    PostgreSQL --> JDBC Source --> Kafka topic
    Database log --> Debezium --> Kafka topic
    File/API/object storage --> Source --> Kafka

Source connector cần quản lý:

- vị trí đã đọc;
- polling hoặc log position;
- mapping key/value;
- schema;
- duplicate khi restart;
- snapshot;
- retry;
- backpressure.

### 3.2 Sink connector

Sink đọc Kafka và ghi ra hệ thống bên ngoài:

    Kafka topic --> JDBC Sink --> PostgreSQL
    Kafka topic --> S3 Sink --> Object storage
    Kafka topic --> Elasticsearch Sink --> Search

Sink connector cần quản lý:

- consumer group;
- offset;
- batch insert/bulk request;
- upsert/delete;
- schema mapping;
- retry;
- DLQ;
- idempotency;
- target availability.

### 3.3 Không nhầm connector với application business logic

Connect phù hợp cho:

- copy dữ liệu;
- CDC;
- data lake ingestion;
- search indexing;
- JDBC integration;
- routing/field mapping đơn giản.

Application phù hợp cho:

- business validation phức tạp;
- gọi nhiều service theo workflow;
- transaction nghiệp vụ;
- orchestration;
- side effect cần domain logic;
- retry theo trạng thái business.

## 4. Connect architecture

Mô hình:

    Connect REST API
          |
          v
    Worker cluster
      +-- Worker 1
      +-- Worker 2
      +-- Worker 3
          |
          +--> Connector A
          |      +--> Task 0
          |      +--> Task 1
          |
          +--> Connector B
                 +--> Task 0

### 4.1 Worker

Worker chạy Connect runtime và quản lý connector/task. Trong distributed mode, nhiều worker cùng chia workload và dùng internal topics để lưu config/status/offset.

### 4.2 Connector

Connector là logical job/config:

    source connector: đọc PostgreSQL
    sink connector: ghi Elasticsearch

Một connector có thể có nhiều task.

### 4.3 Task

Task là đơn vị thực thi thực tế. Connector quyết định số task và cách chia workload.

Tăng tasks không luôn tăng throughput:

- source không chia được table;
- target bottleneck;
- một partition/table hot;
- connector implementation giới hạn;
- database connection pool không đủ.

## 5. Standalone và distributed mode

### 5.1 Standalone mode

Một worker process giữ config/offset cục bộ hoặc theo file.

Phù hợp:

- local development;
- demo;
- pipeline nhỏ;
- không cần high availability.

Nhược điểm:

- worker chết thì connector dừng;
- scale/rebalance hạn chế;
- config/offset governance kém hơn;
- không phù hợp production quan trọng.

### 5.2 Distributed mode

Nhiều worker cùng cluster, Connect tự phân phối connector/task.

Phù hợp:

- production;
- cần failover;
- scale task;
- rolling restart;
- quản lý config/status/offset tập trung.

Cần cấu hình:

- bootstrap servers;
- group.id;
- config.storage.topic;
- offset.storage.topic;
- status.storage.topic;
- key/value converters;
- REST advertised host;
- plugin.path;
- replication factor internal topics.

### 5.3 Internal topics

Distributed Connect dùng internal topics cho:

- connector/task config;
- source offsets;
- connector/task status.

Các topic này quan trọng. Cần:

- replication factor phù hợp;
- retention không quá ngắn;
- ACL;
- monitoring;
- backup/recovery plan;
- không xóa tùy tiện.

## 6. Connector lifecycle

Một connector/task thường có trạng thái:

- RUNNING;
- PAUSED;
- FAILED;
- UNASSIGNED;
- RESTARTING;
- STOPPED tùy API/version.

Phân biệt:

- connector config hợp lệ không có nghĩa task chạy;
- connector RUNNING có thể có task FAILED;
- task FAILED có thể chỉ lỗi một partition/table;
- restart task không luôn sửa root cause;
- PAUSED không giống FAILED, nhưng dữ liệu vẫn có thể lag.

Kiểm tra qua REST API thường:

    GET /connectors
    GET /connectors/{name}/status
    GET /connectors/{name}/config
    GET /connectors/{name}/tasks

Endpoint và auth tùy deployment. Không expose Connect REST API public không bảo vệ.

## 7. Connector config cơ bản

Một config nên có:

- name;
- connector.class;
- tasks.max;
- source/sink connection;
- topic/table mapping;
- key/value converter;
- transforms;
- errors policy;
- retry;
- DLQ;
- secrets reference;
- ownership labels.

Ví dụ conceptual source:

    {
      "name": "orders-jdbc-source",
      "config": {
        "connector.class": "JdbcSourceConnector",
        "tasks.max": "1",
        "connection.url": "jdbc:postgresql://db:5432/orders",
        "mode": "timestamp+incrementing",
        "incrementing.column.name": "id",
        "timestamp.column.name": "updated_at",
        "topic.prefix": "prod.db."
      }
    }

Config thực tế phụ thuộc JDBC connector vendor/version. Đọc documentation của plugin đang pin, không copy config từ plugin khác.

## 8. JDBC Source Connector

### 8.1 Các mode phổ biến

JDBC source có thể đọc theo:

- incrementing column;
- timestamp column;
- timestamp+incrementing;
- bulk;
- query mode tùy connector.

### 8.2 Incrementing mode

Dùng cột tăng dần:

    id = 1, 2, 3, 4

Ưu điểm:

- đơn giản;
- hiệu quả;
- dễ theo dõi offset.

Nhược điểm:

- không bắt được update row cũ nếu id không đổi;
- delete thường không được phản ánh;
- có thể bỏ qua row nếu transaction/visibility behavior không phù hợp.

### 8.3 Timestamp mode

Dùng updated_at:

    updated_at = thời điểm thay đổi

Ưu điểm:

- bắt được update;
- phù hợp bảng có timestamp tin cậy.

Nhược điểm:

- timestamp trùng;
- precision thấp;
- clock/transaction timing;
- có thể duplicate hoặc bỏ sót nếu query boundary sai.

### 8.4 Timestamp + incrementing

Kết hợp timestamp và id làm tie-breaker:

- timestamp bắt thay đổi;
- id phân biệt các row cùng timestamp.

Thường an toàn hơn nếu schema có cột phù hợp, nhưng vẫn cần test update/delete/concurrency.

### 8.5 Bulk mode

Đọc toàn bảng mỗi lần không phù hợp bảng lớn hoặc production realtime. Chỉ dùng cho snapshot nhỏ/demo hoặc pipeline có chủ đích.

### 8.6 JDBC source không phải CDC đầy đủ

JDBC polling thường không bắt được mọi delete và có thể có khoảng hở giữa các lần poll. Nếu cần transaction log/change semantics đầy đủ, cân nhắc CDC.

## 9. JDBC Sink Connector

### 9.1 Sink cơ bản

    {
      "name": "orders-jdbc-sink",
      "config": {
        "connector.class": "JdbcSinkConnector",
        "tasks.max": "2",
        "topics": "prod.orders.order-created.v1",
        "connection.url": "jdbc:postgresql://db:5432/orders",
        "insert.mode": "upsert",
        "pk.mode": "record_key",
        "pk.fields": "order_id",
        "auto.create": "false",
        "auto.evolve": "false"
      }
    }

Tên config phụ thuộc connector version. Mục tiêu là hiểu các quyết định, không copy mù.

### 9.2 Insert, upsert và update

- insert: record duplicate có thể fail;
- upsert: phù hợp state/idempotent sink nếu key đúng;
- update: cần row tồn tại;
- delete: cần tombstone/config hỗ trợ.

Chọn theo semantics topic:

- event history thường cần bảng append/audit hoặc processed event;
- state topic compacted phù hợp upsert;
- tombstone có thể biểu thị delete.

### 9.3 Primary key mode

Có thể lấy key từ:

- record key;
- value field;
- composite key;
- Kafka metadata tùy connector.

Nếu key Kafka không được thiết kế đúng, sink khó tạo unique identity. Không dùng offset làm business primary key trừ khi chỉ là ingestion audit.

### 9.4 Auto create/evolve

Tiện cho local, nguy hiểm ở production:

- auto-create schema không theo database governance;
- auto-evolve tạo cột ngoài kiểm soát;
- type mapping có thể sai;
- migration không qua review.

Production nên quản lý schema/table bằng migration tool và đặt auto.create/auto.evolve theo policy.

## 10. Object storage sink

Object storage sink thường gom record thành file theo:

- topic;
- partition;
- time;
- size;
- schema;
- partitioner.

Use case:

- data lake;
- archive;
- batch analytics;
- audit export;
- replay source.

Cần quan tâm:

- file format JSON/Avro/Parquet;
- rotation interval;
- exactly-once/duplicate file semantics;
- partition path;
- schema evolution;
- late data;
- retention/lifecycle object storage;
- credential/secret;
- small files problem.

Kafka offset commit chỉ nói connector đã đọc; không tự đảm bảo object downstream đã được query/audit như mong muốn nếu sink crash giữa upload và commit.

## 11. Elasticsearch/OpenSearch sink

Sink search thường dùng bulk API:

    Kafka topic -> connector task -> bulk index/update

Cần thiết kế:

- document ID từ event/business key;
- upsert vs append;
- tombstone/delete;
- mapping;
- retryable HTTP status;
- rejected bulk items;
- versioning;
- refresh policy;
- index rollover;
- mapping conflict;
- DLQ.

Nếu document ID không ổn định, retry có thể tạo duplicate document. Nếu mapping field đổi type, task có thể fail cho cả batch.

## 12. SMT — Single Message Transform

SMT biến đổi record nhẹ trước khi gửi tiếp:

- rename field;
- add/remove field;
- route topic;
- extract key;
- mask field;
- insert metadata;
- flatten structure;
- cast type tùy transform.

### 12.1 Dùng SMT khi nào?

SMT phù hợp cho:

- mapping đơn giản;
- rename;
- routing;
- metadata;
- remove PII field;
- key extraction.

### 12.2 Không dùng SMT cho business logic phức tạp

SMT không phù hợp cho:

- gọi database/API;
- aggregate;
- workflow;
- validation nhiều bước;
- retry nghiệp vụ;
- join;
- enrichment phức tạp.

Khi logic vượt mapping nhỏ, viết Kafka Streams hoặc application rõ ràng hơn.

### 12.3 SMT order

Nhiều transform chạy theo thứ tự. Nếu transform sau phụ thuộc field do transform trước tạo, thứ tự sai sẽ gây lỗi.

Ghi lại:

- transform name;
- order;
- input/output schema;
- PII impact;
- test fixture.

## 13. Data mapping

Khi mapping giữa DB và Kafka, quyết định:

- key;
- value;
- null;
- timestamp;
- decimal;
- date/time;
- array/map;
- binary;
- schema;
- tombstone;
- delete;
- naming convention.

Ví dụ:

    DB column: customer_id
    Kafka field: customerId
    Search field: customer_id

Mapping nên được document. Đừng để connector default âm thầm quyết định business contract.

## 14. CDC và Debezium ở mức kiến trúc

### 14.1 CDC là gì?

CDC đọc database transaction log:

    PostgreSQL WAL/binlog
             |
             v
       CDC connector
             |
             v
        Kafka topic

CDC bắt insert/update/delete gần transaction order hơn JDBC polling.

### 14.2 CDC event

CDC event thường có:

- before;
- after;
- source metadata;
- operation c/u/d/r;
- transaction metadata;
- timestamp.

Đây là database change event, không nhất thiết là business event.

### 14.3 Snapshot và streaming

CDC connector thường có:

1. snapshot dữ liệu hiện tại;
2. chuyển sang streaming transaction log;
3. lưu offset log position;
4. tiếp tục sau restart.

Cần hiểu snapshot có thể tạo một lượng event lớn, ảnh hưởng downstream.

### 14.4 CDC vs outbox

CDC bảng nghiệp vụ:

    orders row change -> Kafka

Outbox CDC:

    outbox event row -> Kafka

Outbox thường có business event payload và event type tốt hơn, trong khi CDC trực tiếp phản ánh row change.

### 14.5 CDC delete và tombstone

Delete có thể được biểu diễn bằng:

- operation delete;
- before có record;
- after null;
- tombstone sau đó.

Sink/consumer phải hiểu semantics này. Không coi value null luôn là lỗi.

## 15. Offset và delivery semantics của Connect

Connect lưu source/sink offset để tiếp tục sau restart. Nhưng:

- offset không đồng nghĩa downstream side effect exactly-once;
- task crash sau write trước offset commit có thể duplicate;
- sink cần idempotent key/upsert;
- source có thể đọc lại boundary;
- batch commit có thể lặp một phần.

Thiết kế:

    source offset -> Kafka
    Kafka offset -> sink
    sink write -> external system

Mỗi boundary cần guarantee và dedup riêng.

## 16. Error tolerance

Các config thường gặp về ý tưởng:

- errors.tolerance=none;
- errors.tolerance=all;
- errors.log.enable;
- errors.log.include.messages;
- errors.deadletterqueue.topic.name;
- errors.deadletterqueue.context.headers.enable;
- retry timeout/backoff.

### 16.1 tolerance none

Task fail ngay khi gặp lỗi không recover:

- dễ nhận biết;
- bảo vệ dữ liệu khỏi silently skip;
- cần operator/recovery.

Phù hợp contract quan trọng nếu có runbook.

### 16.2 tolerance all

Connector tiếp tục và bỏ qua lỗi theo cấu hình:

- pipeline không bị block;
- nguy cơ mất dữ liệu;
- phải có DLQ/metric/alert;
- không dùng mù cho dữ liệu quan trọng.

### 16.3 DLQ của Connect

DLQ nên chứa:

- original topic/partition/offset;
- connector/task;
- error class;
- error message;
- timestamp;
- payload/key nếu policy cho phép;
- schema/version;
- source metadata.

DLQ connector không giống application DLT hoàn toàn. Đọc plugin documentation và kiểm tra thực tế.

## 17. Retry và backoff trong Connect

Retry phù hợp:

- database unavailable tạm thời;
- HTTP 503;
- network timeout;
- object storage transient error.

Không retry vô hạn:

- schema incompatible;
- mapping type sai;
- permission denied;
- invalid payload;
- target index mapping conflict nếu không sửa schema.

Retry cần:

- max elapsed time;
- backoff;
- jitter nếu có;
- alert;
- DLQ/fail behavior;
- downstream capacity.

## 18. Secret management

Không ghi:

    connection.password=super-secret

trong file connector config commit vào Git.

Cần dùng:

- environment/secret injection;
- ConfigProvider;
- secret manager;
- Kubernetes Secret;
- cloud secret service;
- ACL hạn chế REST API;
- credential rotation.

Kiểm tra secret ở:

- connector config REST response;
- logs;
- error message;
- DLQ headers;
- metrics labels;
- task stack trace.

Tắt hoặc redact config/log nếu connector hỗ trợ. Cấu hình worker không nên public.

## 19. Plugin path và classloader

Connect plugin thường được load từ plugin.path:

    plugin.path=/usr/share/java,/usr/share/confluent-hub-components

Cần kiểm tra:

- plugin directory;
- connector class;
- version;
- dependencies;
- classloader isolation;
- duplicate jar;
- license;
- CVE/security patch;
- restart worker sau install.

Lỗi thường gặp:

- Class not found;
- NoClassDefFoundError;
- incompatible kafka-clients;
- connector không hiển thị trong plugin list;
- một worker có plugin, worker khác không có.

Distributed cluster phải có plugin nhất quán trên mọi worker.

## 20. Version compatibility

Kiểm tra matrix:

| Thành phần | Cần tương thích |
|---|---|
| Kafka broker | protocol/feature |
| Connect worker | runtime |
| Connector plugin | worker/runtime |
| Converter | schema/format |
| Database driver | DB version |
| Debezium | DB/Kafka/Connect |
| Sink target client | target API |
| JDK | connector support |

Không nâng connector plugin riêng lẻ trong distributed cluster mà không test task restart/offset/schema.

## 21. REST API vận hành

Các thao tác thường gặp:

    GET    /connectors
    GET    /connectors/{name}
    GET    /connectors/{name}/status
    GET    /connectors/{name}/config
    POST   /connectors
    PUT    /connectors/{name}/config
    POST   /connectors/{name}/restart
    POST   /connectors/{name}/tasks/{id}/restart
    DELETE /connectors/{name}

Cần:

- auth;
- TLS;
- audit;
- RBAC;
- không expose public;
- validate config trước update;
- biết update có restart task không;
- không xóa connector khi chỉ muốn pause.

Endpoint và behavior tùy Connect distribution/version.

## 22. Bài lab 1 — Kafka sink vào PostgreSQL

1. Khởi động Kafka và PostgreSQL local.
2. Tạo topic OrderCreated.
3. Tạo bảng target với primary key orderId.
4. Cấu hình JDBC sink.
5. Publish event.
6. Kiểm tra row trong PostgreSQL.
7. Gửi duplicate event.
8. So sánh insert và upsert.
9. Dừng PostgreSQL.
10. Quan sát task status/lag.
11. Khởi động lại database.
12. Kiểm tra task recover và duplicate behavior.

Ghi lại:

- task count;
- batch size;
- insert mode;
- pk mode;
- connector offset;
- failure log;
- data consistency.

## 23. Bài lab 2 — JDBC source hoặc CDC đọc outbox

### JDBC source

1. Tạo outbox table.
2. Insert row với id/incrementing và created_at.
3. Cấu hình source mode phù hợp.
4. Kiểm tra topic output.
5. Update row và xem connector có bắt được không.
6. Test duplicate boundary/restart.

### CDC

1. Bật logical replication/binlog phù hợp.
2. Cấu hình connector.
3. Tạo snapshot.
4. Insert/update/delete.
5. Kiểm tra event before/after/op.
6. Restart task.
7. Kiểm tra offset/log position.

CDC setup phụ thuộc database và connector. Không xem JDBC polling và CDC là thay thế hoàn toàn.

## 24. Bài lab 3 — Schema error và connector status

1. Cấu hình converter/schema.
2. Gửi record schema sai.
3. Quan sát task status.
4. Kiểm tra error log.
5. Bật DLQ.
6. Gửi lại record lỗi.
7. Kiểm tra DLQ metadata.
8. Sửa schema.
9. Replay một record vào topic test.
10. Đảm bảo không xóa DLQ mù.

## 25. Bài lab 4 — Retry và DLQ

1. Làm target unavailable.
2. Đặt retry/backoff có giới hạn.
3. Quan sát status.
4. Gửi payload permanent error.
5. Kiểm tra retry có dừng không.
6. Kiểm tra DLQ.
7. Khôi phục target.
8. Restart task có kiểm soát.
9. Kiểm tra offset và duplicate.

## 26. Bài lab 5 — Task restart và lag

1. Tạo connector có nhiều task.
2. Gửi traffic đều.
3. Theo dõi task status.
4. Restart một task.
5. Quan sát lag.
6. Làm một task chậm hoặc fail.
7. Kiểm tra task khác có tiếp tục không.
8. Restart worker trong distributed mode.
9. Ghi thời gian recover và dữ liệu duplicate/mất.

## 27. Khi nào dùng Connect, khi nào tự viết application?

### Dùng Connect khi

- mapping tương đối thẳng;
- connector đã được hỗ trợ tốt;
- cần CDC;
- sink/source phổ biến;
- cần vận hành config/task chuẩn;
- retry/DLQ theo connector đủ.

### Tự viết application khi

- business logic phức tạp;
- nhiều API call;
- cần orchestration;
- cần transaction/inbox/outbox tùy domain;
- validation/decision thay đổi thường xuyên;
- connector không biểu đạt được semantics;
- cần custom observability/flow control.

### Dùng kết hợp

Ví dụ:

    Order DB -> outbox CDC -> Kafka
    Kafka -> Connect sink -> data lake
    Kafka -> Spring application -> Payment API

Không ép Connect làm workflow nghiệp vụ.

## 28. Chẩn đoán connector

### Connector không start

Kiểm tra:

- connector class;
- plugin path;
- worker plugin inventory;
- JSON config;
- required property;
- network/DNS;
- credentials;
- database driver;
- converter.

### Task FAILED

Kiểm tra:

1. status API;
2. task trace;
3. error class;
4. topic/partition/offset;
5. schema;
6. target availability;
7. auth;
8. mapping;
9. retry/DLQ;
10. task restart sau khi root cause được sửa.

Restart trước khi sửa root cause chỉ làm task fail lại.

### Lag tăng

Khả năng:

- target chậm;
- batch nhỏ;
- task count thấp;
- partition skew;
- retry/backoff;
- database lock;
- sink bulk rejected;
- connector task FAILED;
- network.

Xem lag theo connector/task/partition, không chỉ tổng consumer group.

### Dữ liệu duplicate

Kiểm tra:

- task restart giữa write và offset commit;
- sink insert thay vì upsert;
- key không ổn định;
- source polling boundary;
- CDC snapshot/restart;
- replay/DLQ;
- connector semantics.

### Dữ liệu thiếu

Kiểm tra:

- errors.tolerance=all;
- DLQ không được monitor;
- source mode không bắt update/delete;
- offset reset;
- retention;
- filter/SMT;
- schema mapping.

## 29. Sai lầm phổ biến

### Dùng Connect cho business workflow

Connector không phải orchestration engine.

### Cấu hình plaintext password

Config REST/log có thể làm lộ secret.

### Auto-create/auto-evolve production

Schema DB có thể bị thay đổi ngoài migration.

### Tăng tasks.max vô hạn

Target/DB/partition không scale tương ứng.

### DLQ không có alert

DLQ tăng = dữ liệu không đến đích.

### Restart task liên tục

Tạo duplicate và che root cause.

### Plugin version không đồng nhất

Distributed worker có hành vi khác nhau hoặc task fail khi migrate.

### Không kiểm tra key

Sink upsert/delete sai nếu key null hoặc không ổn định.

### Bỏ qua tombstone/delete

State sink hoặc search index giữ record đã bị xóa.

### Nhầm offset với exactly-once

Offset commit không tự atomic với target write.

## 30. Câu hỏi tự kiểm tra

1. Source connector và sink connector khác nhau thế nào?
2. Worker, connector và task là gì?
3. Standalone và distributed mode dùng khi nào?
4. Internal topics của Connect có vai trò gì?
5. JDBC incrementing khác timestamp mode thế nào?
6. Vì sao JDBC polling không phải CDC đầy đủ?
7. Sink insert và upsert khác nhau thế nào?
8. SMT phù hợp với loại biến đổi nào?
9. CDC event khác business event ra sao?
10. errors.tolerance=none/all trade-off gì?
11. DLQ cần metadata nào?
12. Vì sao plaintext secret nguy hiểm?
13. plugin.path và version compatibility quan trọng thế nào?
14. Task FAILED khác connector config invalid thế nào?
15. Vì sao restart task không luôn sửa lỗi?
16. Connector offset có bảo đảm target exactly-once không?
17. Khi nào dùng Connect thay vì tự viết consumer?
18. Khi nào phải dùng outbox/CDC/application?

### Đáp án ngắn

1. Source đưa dữ liệu vào Kafka; sink đưa dữ liệu ra hệ thống khác.
2. Worker chạy runtime, connector là job logic, task là execution unit.
3. Local đơn giản so với production HA/scale.
4. Lưu config, offset và status distributed.
5. Một dùng id tăng; một dựa timestamp/update.
6. Không bắt đầy đủ transaction log/delete và có polling gaps.
7. Insert append; upsert cập nhật theo key.
8. Mapping/routing nhẹ, không business workflow.
9. CDC là row change; business event là semantics nghiệp vụ.
10. Fail-fast/data safety so với tiếp tục nhưng nguy cơ bỏ qua.
11. Original metadata, error, connector/task, timestamp, payload policy.
12. Có thể lộ qua Git, REST, log và DLQ.
13. Plugin phải load được và hành vi nhất quán mọi worker.
14. Config có thể hợp lệ nhưng task runtime fail ở data/target.
15. Root cause còn nguyên.
16. Không; target write và offset có thể không atomic.
17. Mapping/data integration chuẩn, connector sẵn.
18. Khi cần transaction/business semantics/CDC đáng tin cậy.

## 31. Checklist hoàn thành Tuần 12

- [ ] Tôi phân biệt source và sink connector.
- [ ] Tôi hiểu worker, connector, task, config, offset và status.
- [ ] Tôi phân biệt standalone với distributed mode.
- [ ] Tôi biết internal topics của Connect.
- [ ] Tôi cấu hình được JDBC sink local.
- [ ] Tôi hiểu JDBC source modes.
- [ ] Tôi phân biệt polling với CDC/Debezium.
- [ ] Tôi biết dùng SMT cho mapping đơn giản.
- [ ] Tôi hiểu sink insert/upsert/delete.
- [ ] Tôi có DLQ/retry policy cho connector.
- [ ] Tôi theo dõi được task FAILED/PAUSED và lag.
- [ ] Tôi không lưu plaintext secret.
- [ ] Tôi kiểm tra plugin path và version compatibility.
- [ ] Tôi có runbook restart task sau khi sửa root cause.
- [ ] Tôi hiểu connector offset không tự exactly-once target.
- [ ] Tôi biết khi nào dùng Connect, app hoặc kết hợp.
- [ ] Tôi hoàn thành lab PostgreSQL, outbox/CDC, schema error, DLQ và task restart.

## 32. Câu chốt cần nhớ

Kafka Connect là công cụ data integration mạnh khi bài toán là di chuyển và ánh xạ dữ liệu có cấu trúc. Nó không thay thế business application, transaction boundary hay idempotency design.

Năm ý cần nhớ:

1. Source đưa dữ liệu vào Kafka; sink đưa dữ liệu ra hệ thống bên ngoài.
2. Connector config hợp lệ không có nghĩa task đang xử lý thành công.
3. Offset Connect không tự atomic với external side effect.
4. DLQ, secret, plugin version và task lag đều là phần vận hành bắt buộc.
5. Dùng Connect cho plumbing; dùng application/outbox/inbox cho business semantics phức tạp.


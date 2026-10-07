# Tuần 14 — Observability và Operations

> Mục tiêu: nhìn thấy tình trạng cluster, producer, consumer và downstream đủ sớm để phát hiện lỗi, định lượng impact và xử lý bằng runbook thay vì đoán.

## 1. Kết quả cần đạt

Sau bài học, bạn có thể:

- Phân biệt metric, log, trace và profile.
- Theo dõi broker bytes in/out, request latency và request errors.
- Nhận diện under-replicated, offline partition và ISR shrink.
- Kiểm tra controller health và metadata propagation.
- Theo dõi consumer lag theo group/topic/partition.
- Theo dõi JVM heap, GC, CPU, disk, network và page cache.
- Export JMX bằng JMX Exporter.
- Đưa metrics vào Prometheus và xây dashboard Grafana.
- Thiết kế structured log có trace ID, correlation ID, event ID.
- Định nghĩa SLI/SLO cho latency, lag, availability và DLT rate.
- Viết alert có threshold, impact, owner và bước xử lý.
- Viết runbook cho broker down, lag spike và ISR shrink.
- Phân biệt lag do producer tăng với lag do consumer chậm.
- Thực hiện capacity review dựa trên dữ liệu vận hành.

## 2. Observability là gì?

Observability là khả năng suy ra trạng thái bên trong hệ thống từ tín hiệu bên ngoài.

Ba tín hiệu chính:

- **Metrics:** số liệu theo thời gian, dùng alert/dashboard.
- **Logs:** sự kiện có ngữ cảnh, dùng điều tra chi tiết.
- **Traces:** đường đi của một request/event qua nhiều service.

Có thể thêm:

- **Profiles:** CPU/memory allocation/lock để tìm bottleneck.
- **Events:** deployment, restart, config change, rebalance.
- **Status:** connector/task/consumer health.

Kafka operations cần kết hợp:

    Metric báo lag tăng
        |
        v
    Log cho biết task/partition/exception
        |
        v
    Trace nối HTTP request với eventId
        |
        v
    Runbook xác định action

Không dashboard nào tự trả lời toàn bộ nguyên nhân.

## 3. Bốn câu hỏi observability

Khi sự cố xảy ra, dashboard/log phải giúp trả lời:

1. Hệ thống có đang available không?
2. Dữ liệu có đang đi qua không?
3. Độ trễ và backlog có vượt SLA không?
4. Nguyên nhân nằm ở broker, client, downstream hay deploy mới?

Ví dụ lag tăng:

- producer traffic tăng gấp đôi;
- consumer processing chậm;
- một partition hot;
- DB downstream lock;
- consumer bị rebalance;
- DLT/retry tăng;
- broker network/disk bận.

Tổng lag chỉ là triệu chứng. Cần drill down theo group, topic, partition và stage.

## 4. Broker metrics

### 4.1 Traffic

Theo dõi:

- bytes in per broker;
- bytes out per broker;
- messages in;
- produce request rate;
- fetch request rate;
- replication bytes;
- request queue time;
- response queue time.

Traffic cần nhìn cả rate và distribution giữa broker. Một broker bytes in cao hơn có thể do leader skew.

### 4.2 Request latency

Tách:

- request queue time;
- local processing time;
- response queue time;
- total request latency;
- p50/p95/p99;
- theo request type: Produce, Fetch, Metadata, ListOffsets.

Latency tăng có thể do:

- disk;
- CPU;
- network;
- replication;
- request queue;
- broker GC;
- leader overload.

### 4.3 Request errors

Theo dõi count/rate theo error:

- timeout;
- not enough replicas;
- not leader;
- message too large;
- authentication/authorization;
- invalid request;
- coordinator unavailable.

Alert theo rate và impact, không chỉ một error đơn lẻ trong lab.

## 5. Replication và partition health

### 5.1 Under-replicated partition

Under-replicated partition là partition có số replica trong ISR thấp hơn replication assignment.

Rủi ro:

- durability giảm;
- broker failure có thể làm partition unavailable;
- min.insync.replicas có thể làm produce fail;
- cluster đang catch up hoặc network/disk có vấn đề.

Theo dõi:

- số under-replicated partition;
- danh sách topic/partition;
- thời gian ở trạng thái này;
- broker replica đang chậm;
- ISR shrink/expand.

### 5.2 Offline partition

Offline partition không có leader phục vụ request.

Impact:

- producer/consumer lỗi hoặc timeout;
- dữ liệu không đi qua partition;
- SLA có thể vi phạm;
- cần xử lý khẩn cấp.

Offline partition nên là alert critical.

### 5.3 ISR shrink/expand

ISR shrink:

    ISR: 1,2,3 -> 1,2

Có thể do:

- follower chậm;
- disk latency;
- network;
- broker CPU/GC;
- broker down;
- fetcher lag.

ISR expand cho biết follower đã catch up, nhưng không nên chỉ nhìn trạng thái hiện tại. Alert theo event/rate giúp thấy flapping.

### 5.4 Leader distribution

Theo dõi leader count per broker. Leader skew gây:

- một broker nhận nhiều produce/fetch;
- latency không đều;
- CPU/network/disk không đều;
- một broker trở thành bottleneck.

## 6. Controller và metadata health

Trong KRaft, theo dõi:

- active controller;
- controller quorum health;
- metadata log progress;
- metadata propagation latency;
- controller changes;
- broker registration;
- topic/partition metadata update errors.

Dấu hiệu bất thường:

- active controller thay đổi liên tục;
- metadata request latency tăng;
- broker join/leave lặp;
- topic creation/config change chậm;
- client metadata stale;
- controller quorum không đủ majority.

Controller có thể khỏe trong khi data path lỗi, hoặc ngược lại. Tách dashboard control plane và data plane.

## 7. Consumer observability

### 7.1 Lag

Theo dõi theo:

- consumer group;
- topic;
- partition;
- consumer instance;
- oldest event age;
- current offset;
- log end offset.

Tổng lag hữu ích cho overview nhưng không đủ để xử lý hot partition.

### 7.2 Processing metrics

Application consumer cần export:

- records consumed;
- records processed;
- processing latency p50/p95/p99;
- deserialization errors;
- business errors;
- retry count;
- DLT count;
- commit success/failure;
- poll interval;
- paused duration;
- rebalance count;
- worker queue depth.

### 7.3 Consumer health

Kiểm tra:

- member count;
- assignment;
- last poll time;
- last successful commit;
- consumer state;
- max poll interval violations;
- heartbeat/session errors;
- restart count.

Consumer process còn sống không đồng nghĩa đang xử lý dữ liệu. Cần metric tiến độ.

## 8. JVM, CPU, disk, network và page cache

### 8.1 JVM heap

Theo dõi:

- heap used;
- heap committed/max;
- allocation rate;
- old generation;
- GC pause;
- full GC;
- OOM.

Heap cao có thể do:

- batch quá lớn;
- buffer/queue vô hạn;
- object allocation;
- state store/cache;
- memory leak;
- record payload lớn.

### 8.2 GC

GC pause dài có thể:

- tăng request latency;
- làm consumer poll chậm;
- gây rebalance;
- làm producer timeout;
- làm controller miss liveness.

Correlate GC timestamp với latency, lag và rebalance.

### 8.3 CPU

Tách CPU theo:

- broker;
- Kafka Streams/app;
- Connect task;
- serializer/compression;
- TLS;
- compaction;
- GC.

CPU cao với throughput thấp thường là dấu hiệu lỗi config/code hoặc retry storm.

### 8.4 Disk

Theo dõi:

- usage percentage;
- free bytes;
- read/write throughput;
- IOPS;
- latency;
- filesystem errors;
- segment count;
- cleanup/compaction activity.

Disk gần đầy là incident nghiêm trọng. Retention không phải lúc nào cleanup kịp trước khi disk đầy.

### 8.5 Network

Theo dõi:

- bytes in/out;
- interface utilization;
- packet errors/drops;
- replication traffic;
- client traffic;
- Connect/Streams internal topics.

Network saturation có thể làm ISR shrink và request timeout cùng lúc.

### 8.6 Page cache

Kafka cần OS page cache để đọc log hiệu quả. Khi memory pressure hoặc JVM heap quá lớn:

- cache hit giảm;
- disk read tăng;
- fetch latency tăng;
- CPU wait/I/O wait tăng.

Không đánh giá broker chỉ bằng heap usage.

## 9. JMX Exporter, Prometheus và Grafana

### 9.1 Kiến trúc

    Kafka/JVM JMX
          |
          v
    JMX Exporter
          |
          v
    Prometheus scrape
          |
          v
    Grafana dashboard/alert

Prometheus lưu time series theo metric name và labels. Grafana query/visualize.

### 9.2 JMX Exporter

Cần cấu hình:

- javaagent;
- port metrics;
- whitelist/rules;
- label mapping;
- cardinality control.

Không expose JMX metrics public. Bảo vệ endpoint và giới hạn metric.

### 9.3 Prometheus labels

Labels có thể gồm:

- broker;
- topic;
- partition;
- consumer group;
- client id;
- instance.

Cẩn thận cardinality:

- eventId không được làm label;
- request ID không được làm label;
- user/customer ID không được làm label;
- topic/group/partition thường có kiểm soát hơn.

High-cardinality label có thể làm Prometheus tốn RAM và chậm.

### 9.4 Grafana dashboard

Dashboard nên có drill-down:

    Cluster overview
       -> broker
          -> topic
             -> partition
                -> consumer group
                   -> application logs/traces

Mỗi panel nên có:

- đơn vị;
- time range;
- threshold;
- description;
- query owner;
- link runbook.

## 10. Structured logging

Log dạng key-value/JSON:

    {
      "timestamp": "2026-10-04T10:00:00Z",
      "level": "INFO",
      "service": "payment-service",
      "event": "kafka_record_processed",
      "topic": "prod.orders.order-created.v1",
      "partition": 2,
      "offset": 1042,
      "key": "ORD-1001",
      "eventId": "evt-123",
      "correlationId": "req-456",
      "traceId": "trace-789",
      "consumerGroup": "order-payment",
      "durationMs": 28
    }

### 10.1 Field tối thiểu

- timestamp;
- level;
- service;
- environment;
- event name;
- topic;
- partition;
- offset;
- key;
- eventId;
- correlationId;
- traceId;
- consumer group;
- error class;
- duration;
- deployment version.

### 10.2 Không log

- password;
- token;
- SASL credential;
- full PII;
- card/payment secret;
- payload nhạy cảm;
- stack trace chứa secret.

Dùng eventId/offset để tra record thay vì log toàn payload.

### 10.3 Log event lifecycle

Có thể log các mốc:

    http_received
    event_published
    record_consumed
    processing_started
    processing_succeeded
    processing_failed
    retry_scheduled
    dlt_published
    side_effect_committed

Dùng correlation/causation để nối các mốc.

## 11. Trace ID, correlation ID và event ID

Ba ID có vai trò khác nhau:

- traceId: trace execution hiện tại qua service;
- correlationId: nối một business workflow/request;
- eventId: định danh một event business cụ thể.

Ví dụ:

    HTTP request
      traceId=req-trace
      correlationId=order-flow
      creates eventId=evt-order-created

Consumer retry cùng eventId có thể có traceId mới nhưng correlationId/eventId giữ quan hệ business.

Không đưa eventId thành Prometheus label. Dùng log/trace attributes với sampling phù hợp.

## 12. SLI và SLO

### 12.1 SLI

SLI là chỉ số đo service:

- event publish success rate;
- end-to-end latency;
- consumer lag;
- oldest event age;
- broker availability;
- DLT rate;
- commit success rate;
- processing success rate.

### 12.2 SLO

SLO là mục tiêu:

    99.9% OrderCreated được Payment xử lý
    trong 30 giây trong tháng

Hoặc:

    consumer lag < 1.000 records
    và oldest event age < 60 giây
    trong 99.5% thời gian

SLO phải nói rõ:

- scope;
- window;
- percentile/threshold;
- exclusions;
- measurement source;
- owner;
- error budget.

### 12.3 End-to-end latency

Đo từ:

    occurredAt / HTTP accepted
       -> event published
       -> consumer received
       -> side effect completed

Không dùng producer ack latency thay cho end-to-end latency.

### 12.4 DLT rate

    DLT rate = DLT records / input records

DLT rate thấp không có nghĩa data quality tốt nếu errors.tolerance=all và DLQ không monitor. Cần đo skipped/error records nữa.

## 13. Alert design

Mỗi alert cần:

- tên;
- severity;
- condition;
- duration;
- impact;
- owner;
- runbook link;
- query;
- silence policy;
- escalation.

Ví dụ:

| Alert | Severity | Điều kiện | Impact |
|---|---|---|---|
| OfflinePartition | Critical | >0 trong 1 phút | Partition unavailable |
| UnderReplicated | High | >0 trong 5 phút | Durability giảm |
| ConsumerLagSLO | High | age > 60s trong 10 phút | Event xử lý trễ |
| DLTSpike | High | rate vượt baseline | Record lỗi tăng |
| DiskLow | Critical | free < 15% | Nguy cơ broker dừng |
| RebalanceLoop | High | nhiều lần trong 5 phút | Throughput/lag |
| CommitFailure | High | rate vượt ngưỡng | Duplicate/reprocess |
| ControllerFlap | Critical | active controller đổi liên tục | Metadata instability |

### 13.1 Alert tốt

Alert tốt là actionable:

- biết impact;
- có owner;
- có bước đầu tiên;
- có threshold hợp lý;
- không alert mỗi lần traffic burst bình thường;
- có link dashboard/log/runbook.

### 13.2 Alert fatigue

Tránh:

- threshold quá thấp;
- alert cho metric không có action;
- alert trùng;
- alert không có suppression;
- alert không phân biệt maintenance.

## 14. Dashboard đề xuất

### Dashboard 1: Cluster

Panels:

- broker up/down;
- active controller;
- under-replicated partitions;
- offline partitions;
- ISR shrink/expand;
- leader distribution;
- metadata request latency;
- bytes in/out.

### Dashboard 2: Traffic

Panels:

- produce records/s;
- fetch records/s;
- bytes in/out;
- request latency p50/p95/p99;
- request error rate;
- replication traffic;
- request queue time.

### Dashboard 3: Consumer

Panels:

- lag by group/topic;
- lag by partition;
- oldest event age;
- records consumed/processed;
- processing latency;
- rebalance;
- commit error;
- paused duration;
- DLT/retry rate.

### Dashboard 4: Storage

Panels:

- disk usage/free;
- log size;
- retention growth;
- segment count;
- cleanup/compaction;
- disk latency/IOPS;
- page cache/I/O wait.

### Dashboard 5: Application

Panels:

- HTTP request rate/latency/error;
- event publish success/error;
- DB latency/connection pool;
- downstream API status/latency;
- retry;
- DLT;
- trace sampling;
- deployment version.

## 15. Runbook nguyên tắc

Một runbook tốt có:

1. triệu chứng;
2. scope/impact;
3. xác nhận alert;
4. truy vấn/dashboard;
5. immediate mitigation;
6. root cause branches;
7. safe commands/actions;
8. rollback;
9. escalation;
10. recovery verification;
11. post-incident follow-up.

Không ghi lệnh destructive thiếu cảnh báo. Ghi rõ command chỉ dùng lab hay production.

## 16. Runbook: broker down

### Triệu chứng

- broker up metric = 0;
- client connection error;
- leader/ISR change;
- under-replicated tăng;
- request timeout.

### Kiểm tra

1. kiểm tra instance/container/process;
2. kiểm tra host disk/network;
3. xem broker logs;
4. kiểm tra controller/metadata;
5. xác định partition leader/replica bị ảnh hưởng;
6. xem min.insync.replicas;
7. kiểm tra consumer lag;
8. xem deployment/maintenance event.

### Mitigation

- không restart lặp vô hạn;
- khôi phục process/host theo procedure;
- kiểm tra disk trước khi start;
- không xóa log;
- tạm giảm producer rate nếu cần;
- bảo vệ downstream khỏi burst catch-up.

### Recovery

- broker rejoin;
- ISR expand;
- offline partition = 0;
- request errors giảm;
- consumer lag catch up;
- không có data loss ngoài guarantee.

## 17. Runbook: consumer lag spike

### Phân loại nguyên nhân

- producer arrival tăng;
- consumer processing chậm;
- downstream chậm;
- partition hot;
- consumer FAILED;
- rebalance loop;
- retry/DLT;
- broker fetch latency.

### Kiểm tra

1. lag group/topic/partition;
2. oldest event age;
3. producer rate;
4. records processed/s;
5. processing p95/p99;
6. assignment;
7. paused partition;
8. DB/downstream latency;
9. rebalance count;
10. task/consumer error.

### Mitigation

- scale consumer nếu còn partition;
- giảm producer rate nếu được;
- pause/limit downstream;
- sửa/restart task sau root cause;
- tách hot key/workload;
- xử lý DLT/retry theo policy.

Không chỉ restart consumer mà không biết lag do đâu.

### Recovery

- lag slope giảm;
- oldest age về dưới SLO;
- downstream ổn định;
- không có duplicate/error spike bất thường;
- ghi incident timeline.

## 18. Runbook: ISR shrink

### Triệu chứng

- ISR count giảm;
- under-replicated tăng;
- follower lag;
- produce acks=all fail có thể xảy ra.

### Kiểm tra

- broker disk latency;
- replication bytes;
- network packet/drop;
- follower CPU/GC;
- partition size;
- broker logs;
- rack/AZ issue;
- throttling/recovery.

### Mitigation

- không tắt safety config chỉ để hết alert;
- giảm traffic nếu cần;
- xử lý disk/network;
- restart follower theo quy trình;
- kiểm tra broker capacity;
- tránh rebalance/reassign hàng loạt khi cluster không khỏe.

### Recovery

- ISR trở lại;
- under-replicated = 0;
- no offline partition;
- replication latency bình thường;
- kiểm tra durability trước khi đóng incident.

## 19. Bài lab 1 — Export Kafka/JMX metrics

1. Khởi động Kafka local.
2. Bật JMX Exporter hoặc metrics endpoint.
3. Cấu hình Prometheus scrape.
4. Kiểm tra target UP.
5. Query bytes in/out.
6. Query request latency.
7. Query broker/JVM metrics.
8. Ghi metric name và labels.

Mục tiêu là hiểu đường đi:

    JMX -> exporter -> Prometheus -> Grafana

Không expose metrics endpoint public trong môi trường thật.

## 20. Bài lab 2 — Dashboard lag và under-replicated

1. Tạo topic/consumer group.
2. Cố tình làm consumer chậm.
3. Tạo lag.
4. Dựng panel lag theo partition.
5. Dựng panel oldest event age.
6. Dựng panel under-replicated nếu lab nhiều broker.
7. Thêm link tới log/runbook.
8. Đặt threshold test.

Dashboard phải giúp phân biệt:

- lag toàn group;
- một partition hot;
- consumer không assignment;
- producer tăng traffic.

## 21. Bài lab 3 — Alert consumer lag vượt SLA

1. Chọn SLO giả định: oldest event age < 60s.
2. Tạo downstream sleep.
3. Xác nhận alert firing sau duration.
4. Kiểm tra owner/severity/runbook.
5. Khôi phục consumer.
6. Xác nhận alert resolved.
7. Ghi false positive nếu có.

Không đặt alert chỉ trên tổng lag nếu SLA thực tế là event age.

## 22. Bài lab 4 — Correlate HTTP trace với Kafka record

Flow:

    HTTP request
       |
       v
    Order service
       |
       v
    Kafka event
       |
       v
    Payment consumer

Gắn:

- traceId;
- correlationId;
- eventId;
- topic/partition/offset trong log;
- consumer processing span.

Tạo một order, tra từ HTTP log sang producer log, Kafka metadata và consumer log. Kiểm tra retry có giữ eventId nhưng có thể tạo attempt/trace khác hay không.

## 23. Bài lab 5 — Viết runbook broker down, lag spike, ISR shrink

Mỗi runbook phải có:

- alert;
- impact;
- first checks;
- safe commands;
- mitigation;
- escalation;
- recovery criteria;
- rollback/cleanup;
- owner.

Thử tabletop exercise với team: một người đóng vai on-call, một người cung cấp symptoms/log/metrics.

## 24. Capacity review vận hành

Review định kỳ:

- traffic average/peak;
- lag SLO;
- broker CPU/disk/network;
- partition growth;
- replication/ISR event;
- retention growth;
- consumer count;
- DLT/retry rate;
- deployment/rebalance;
- incident/error budget.

Câu hỏi:

- Peak tháng tới tăng bao nhiêu?
- Một broker down có còn đủ capacity không?
- Disk còn bao nhiêu ngày theo growth?
- Consumer catch up sau outage mất bao lâu?
- Có topic/partition hot mới không?
- Alert nào đã noisy?
- Runbook nào không còn đúng?

## 25. Sai lầm phổ biến

### Chỉ có dashboard, không có alert/runbook

Nhìn thấy lỗi không đồng nghĩa xử lý được.

### Alert trên tổng lag בלבד

Một partition hot có thể bị che bởi tổng.

### Log payload đầy đủ

Có thể lộ PII/secret và làm log quá lớn.

### Dùng eventId làm metric label

Tạo high cardinality và làm Prometheus quá tải.

### Không đo oldest event age

Lag record không phản ánh SLA nếu arrival rate biến động.

### Không correlate deploy với metric

Nhiều sự cố bắt đầu ngay sau release/config change.

### Restart mọi thứ trước khi chẩn đoán

Có thể làm mất evidence, tạo duplicate và khiến recovery khó hơn.

### Alert under-replicated không có duration

Một event ngắn có thể không đáng pager; kéo dài mới là vấn đề.

### Không tách control plane/data plane

Controller health và data traffic có symptom khác nhau.

### Không kiểm tra page cache

Heap đẹp nhưng disk read latency cao vẫn làm broker chậm.

## 26. Câu hỏi tự kiểm tra

1. Metric, log và trace khác nhau thế nào?
2. Vì sao tổng lag không đủ?
3. Under-replicated partition báo hiệu gì?
4. Offline partition khác under-replicated thế nào?
5. ISR shrink có thể do đâu?
6. Controller health cần theo dõi gì?
7. JMX Exporter, Prometheus và Grafana liên kết ra sao?
8. Structured log cần field Kafka nào?
9. TraceId, correlationId và eventId khác nhau thế nào?
10. SLI và SLO khác nhau ra sao?
11. Vì sao oldest event age hữu ích?
12. Alert tốt cần có gì?
13. Broker down runbook cần kiểm tra gì?
14. Lag spike phân loại theo những nguyên nhân nào?
15. Vì sao không dùng eventId làm Prometheus label?
16. GC pause ảnh hưởng Kafka thế nào?
17. Disk/page cache ảnh hưởng data path ra sao?
18. ISR shrink runbook không nên làm gì?
19. Capacity review nên xem những dữ liệu nào?
20. Khi nào cần escalation?

### Đáp án ngắn

1. Số liệu, ngữ cảnh sự kiện, đường đi qua service.
2. Có thể che một partition hot hoặc event age cao.
3. Replica không theo kịp, durability giảm.
4. Offline không có leader phục vụ; under-replicated vẫn có thể phục vụ nhưng thiếu replica.
5. Disk/network/CPU/GC/broker down.
6. Active controller, quorum, metadata progress/latency.
7. Export JMX, lưu time series, visualize/alert.
8. Topic, partition, offset, key, eventId, correlation/trace.
9. Trace execution, business workflow, business event identity.
10. SLI đo; SLO là mục tiêu.
11. Gắn trực tiếp với thời gian chậm của event.
12. Condition, duration, severity, impact, owner, runbook.
13. Process, logs, disk/network, partition/ISR, controller.
14. Producer rate, processing, downstream, hot partition, failure/retry.
15. High cardinality.
16. Tăng latency, miss poll/heartbeat, rebalance.
17. Quyết định read/write latency và fetch performance.
18. Không tắt safety config mù hoặc restart hàng loạt.
19. Rate, size, lag, disk, CPU, network, retention, incidents.
20. Khi impact/SLO/durability không thể khôi phục trong runbook.

## 27. Checklist hoàn thành Tuần 14

- [ ] Tôi phân biệt metrics, logs và traces.
- [ ] Tôi theo dõi được broker bytes in/out.
- [ ] Tôi theo dõi request latency/errors.
- [ ] Tôi nhận diện under-replicated/offline partition.
- [ ] Tôi hiểu ISR shrink/expand.
- [ ] Tôi kiểm tra được controller health.
- [ ] Tôi theo dõi lag theo group/topic/partition.
- [ ] Tôi theo dõi JVM, GC, CPU, disk, network và page cache.
- [ ] Tôi export được JMX metrics.
- [ ] Tôi tạo được dashboard Prometheus/Grafana cơ bản.
- [ ] Tôi viết structured log có topic/partition/offset.
- [ ] Tôi dùng đúng traceId, correlationId và eventId.
- [ ] Tôi định nghĩa SLI/SLO cho latency/lag/availability/DLT.
- [ ] Tôi viết alert có threshold, impact, owner và runbook.
- [ ] Tôi hoàn thành lab export metrics và dashboard.
- [ ] Tôi correlate được HTTP trace với Kafka record.
- [ ] Tôi có runbook broker down, lag spike và ISR shrink.
- [ ] Tôi thực hiện capacity review dựa trên dữ liệu.

## 28. Câu chốt cần nhớ

Operations tốt không phải là có thật nhiều dashboard. Đó là khả năng đi từ symptom đến impact, từ impact đến root cause và từ root cause đến một action an toàn có thể lặp lại.

Năm ý cần nhớ:

1. Lag cần drill down theo group/topic/partition và oldest event age.
2. Broker health gồm data path, replication path và controller path.
3. Log phải nối được topic, partition, offset, eventId và correlation/trace ID.
4. Mỗi alert phải actionable: threshold, impact, owner, duration và runbook.
5. Observability chỉ có giá trị khi được dùng trong incident response và capacity review.


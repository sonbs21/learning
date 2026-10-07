# Tuần 9: Kafka architecture

## 1. Mục tiêu bài học

Sau bài này, bạn có thể:

- Mô tả Kafka cluster gồm broker, controller và metadata.
- Phân biệt topic, partition, record và offset.
- Giải thích vì sao partition là đơn vị ordering và parallelism.
- Hiểu leader/follower replication và replication factor.
- Phân biệt consumer position, committed offset và log end offset.
- Giải thích consumer group và cơ chế chia partition.
- Biết vì sao số consumer active không thể vượt số partition trong một group.
- Hiểu key ảnh hưởng đến partition assignment và ordering.
- Phân biệt retention theo thời gian/kích thước với log compaction.
- So sánh Kafka với RabbitMQ ở mức kiến trúc.

> Bài này tập trung vào kiến trúc. Producer config, consumer poll loop, offset commit, retry và Spring Kafka sẽ được học ở các tuần tiếp theo.

---

## 2. Kafka là message queue hay event streaming?

Kafka có thể dùng cho messaging, nhưng mental model chính là distributed append-only log.

~~~text
Producer ──▶ Topic ──▶ Partition 0 ──▶ Consumer group A
                    ├─▶ Partition 1 ──▶ Consumer group A
                    └─▶ Partition 2 ──▶ Consumer group B
~~~

Record sau khi được ghi vào partition không biến mất chỉ vì một consumer đã đọc. Record tồn tại theo retention policy. Consumer lưu vị trí đọc riêng và có thể đọc lại từ offset cũ nếu dữ liệu vẫn còn.

~~~text
Queue truyền thống:
message → consumer xử lý → message bị remove/ack

Kafka:
record → append vào log → consumer đọc theo offset
      → record tiếp tục tồn tại theo retention
~~~

Kafka phù hợp với:

- Event-driven architecture.
- Data pipeline.
- Nhiều consumer group độc lập.
- Replay và backfill.
- Stream processing.
- CDC hoặc database changelog.
- Throughput lớn với partitioning.
- Lưu event theo thời gian hoặc theo trạng thái cuối của key.

Kafka không tự động giải quyết:

- Exactly-once business effect.
- Idempotency của external API.
- Schema evolution.
- Ordering toàn hệ thống.
- Retry/DLQ giống SQS hoặc RabbitMQ.
- Việc chọn partition key đúng cho domain.

---

## 3. Các thành phần kiến trúc

~~~text
Lớp ứng dụng
├── Producer
├── Consumer
└── Admin client

Lớp dữ liệu
├── Topic
├── Partition
├── Record
├── Offset
└── Replicated log

Lớp cluster
├── Broker
├── Controller
├── Metadata quorum
└── Leader election
~~~

### 3.1. Producer

Producer tạo record và gửi đến topic. Producer có thể chỉ định:

- Topic.
- Key.
- Value.
- Headers.
- Timestamp.
- Partition cụ thể nếu thật sự cần.

Producer không gửi record trực tiếp vào consumer. Producer gửi vào topic partition; consumer tự fetch từ broker.

### 3.2. Consumer

Consumer fetch record, xử lý record và giữ vị trí đọc. Consumer không xóa record sau khi xử lý.

Consumer có thể:

- Đọc từ đầu topic.
- Đọc từ committed offset.
- Đọc từ timestamp.
- Rewind và replay.
- Pause/resume partition.
- Dùng consumer group hoặc manual assignment.

### 3.3. Broker

Broker là Kafka server lưu partition data và phục vụ:

- Produce record.
- Fetch record.
- Metadata.
- Offset commit.
- Group coordination.
- Replication.
- Leader/follower operations.

Cluster nhiều broker giúp phân bố storage, throughput và fault tolerance.

### 3.4. Controller

Controller quản lý metadata và các quyết định điều phối:

- Broker membership.
- Topic/partition metadata.
- Leader election.
- Assignment metadata.
- Cluster configuration state.

Kafka hiện đại dùng KRaft để quản lý metadata thay cho phụ thuộc ZooKeeper trong deployment mới.

~~~text
Broker     = lưu và phục vụ data
Controller = điều phối metadata/control plane
Cluster    = tập hợp broker/controller theo topology triển khai
~~~

Development có thể chạy combined broker/controller để đơn giản. Production nên cân nhắc tách vai trò để controller không bị ảnh hưởng bởi data-plane load.

---

## 4. Broker và cluster

### 4.1. Một broker

~~~text
Kafka broker 1
├── topic orders, partition 0
├── topic orders, partition 1
└── topic payments, partition 0
~~~

Broker đơn lẻ phù hợp cho local learning nhưng không chịu được lỗi máy.

### 4.2. Nhiều broker

~~~text
Kafka cluster
├── Broker 1
│   ├── orders-0 leader
│   └── orders-1 follower
├── Broker 2
│   ├── orders-1 leader
│   └── orders-2 follower
└── Broker 3
    ├── orders-2 leader
    └── orders-0 follower
~~~

Một partition được đặt trên nhiều broker khi replication factor lớn hơn 1. Leader được phân bố để tránh mọi write dồn vào một broker.

### 4.3. Bootstrap server

Client dùng bootstrap server để kết nối ban đầu:

~~~text
bootstrap.servers = broker-1:9092,broker-2:9092,broker-3:9092
~~~

Sau đó client lấy metadata để biết:

- Topic có partition nào.
- Partition leader ở broker nào.
- Group coordinator ở broker nào.
- Partition nào cần fetch hoặc produce.

Bootstrap server không phải broker duy nhất client luôn dùng.

### 4.4. Metadata

~~~text
orders partition 0 → leader broker 2
orders partition 1 → leader broker 3
orders partition 2 → leader broker 1
~~~

Nếu leader thay đổi, client refresh metadata và gửi request đến leader mới.

---

## 5. Topic

Topic là tên logic của một stream hoặc category record.

~~~text
order-events
payment-events
inventory-events
user-state
audit-events
~~~

Topic được chia thành một hoặc nhiều partition.

### 5.1. Đặt tên topic

Quy ước có thể là:

~~~text
prod.order.events.v1
prod.payment.commands.v1
prod.user.profile-changelog.v1
~~~

Cần thống nhất:

- Environment nằm trong topic name hay cluster riêng?
- Event hay command?
- Version ở topic hay envelope?
- Retention policy.
- Key.
- Owner team.
- Deprecation plan.

Không nên tạo topic cho mọi Java class. Topic là boundary của stream, retention và consumer contract.

### 5.2. Một topic, nhiều consumer group

~~~text
order-events
├── notification-group
├── analytics-group
├── search-indexer-group
└── audit-group
~~~

Mỗi group có offset riêng. Group này đọc record không làm group khác mất record.

### 5.3. Topic không tự đảm bảo schema

Cần có:

- Event type.
- Version.
- Schema compatibility policy.
- Serializer/deserializer.
- Validation.
- Owner.
- Deprecation plan.

---

## 6. Partition: đơn vị cốt lõi

Partition là ordered append-only log bên trong topic.

~~~text
topic: order-events

partition 0: [record 0] [record 1] [record 2] [record 3]
partition 1: [record 0] [record 1] [record 2]
partition 2: [record 0] [record 1] [record 2] [record 3]
~~~

Mỗi partition có:

- Thứ tự riêng.
- Offset riêng.
- Leader riêng.
- Replica riêng.
- Consumer assignment riêng.

### 6.1. Ordering trong partition

~~~text
partition 0:
A0 → A1 → A2

partition 1:
B0 → B1 → B2
~~~

Kafka không có một thứ tự global giữa A và B. Nếu business cần order event theo orderId, dùng orderId làm key để các event liên quan đi cùng partition.

### 6.2. Partition là đơn vị parallelism

Topic có 6 partition:

~~~text
6 partitions + 3 consumers
├── C1: P0, P1
├── C2: P2, P3
└── C3: P4, P5
~~~

Nếu có 8 consumer:

~~~text
6 partitions + 8 consumers
├── 6 consumer có partition
└── 2 consumer idle
~~~

Số consumer active trong một group không thể vượt số partition đang consume.

### 6.3. Partition count là quyết định dài hạn

Tăng partition có thể tăng parallelism nhưng ảnh hưởng:

- Reassignment khi group rebalance.
- Metadata và storage overhead.
- Network/request load.
- Key-to-partition mapping của record mới.
- Ordering/key affinity.
- Downstream concurrency.

Không chọn partition count chỉ theo số consumer hiện tại.

### 6.4. Hot partition

~~~text
partition 0: 90% traffic
partition 1:  3% traffic
partition 2:  3% traffic
partition 3:  4% traffic
~~~

Nguyên nhân:

- Key có cardinality thấp.
- Một key cố định.
- Một tenant cực lớn.
- Phân bố hash không tốt.

Tăng consumer không giải quyết hot partition nếu record vẫn dồn vào một partition.

---

## 7. Record

Record là đơn vị dữ liệu được append vào partition:

~~~text
Record
├── topic
├── partition
├── offset
├── key
├── value
├── timestamp
└── headers
~~~

### 7.1. Key

Key giúp định tuyến:

~~~text
key = order-123
value = OrderCreated
→ partition hash(order-123)
~~~

Key ảnh hưởng:

- Ordering.
- Load distribution.
- Consumer parallelism.
- Compaction identity.
- State locality.

### 7.2. Value

Value chứa event hoặc command payload:

- JSON.
- Avro.
- Protobuf.
- String.
- Bytes.

Schema và serialization sẽ học ở các tuần producer/consumer.

### 7.3. Headers

Headers phù hợp với metadata kỹ thuật:

~~~text
correlationId
traceId
schemaVersion
sourceService
contentType
~~~

Không nhét toàn bộ business payload vào headers.

### 7.4. Timestamp

Phân biệt:

~~~text
occurredAt       = event xảy ra ở business service
record timestamp = record được append vào Kafka
processedAt      = consumer xử lý
~~~

Timestamp hỗ trợ điều tra, tìm offset theo thời gian và đo latency.

---

## 8. Offset

Offset là vị trí của record trong một partition.

~~~text
partition 0:
offset 0 → OrderCreated
offset 1 → OrderPaid
offset 2 → OrderShipped
offset 3 → OrderDelivered
~~~

Offset chỉ có ý nghĩa trong topic-partition:

~~~text
(topic=orders, partition=0, offset=3)
~~~

### 8.1. Offset không phải global ID

Offset 3 ở partition 0 không liên quan offset 3 ở partition 1. Nếu cần event ID global, tạo event ID riêng trong envelope.

### 8.2. Consumer position

~~~text
đã đọc:   0 1 2 3 4
sắp đọc:  5
~~~

Đọc record không đồng nghĩa offset đã commit.

### 8.3. Committed offset

~~~text
position hiện tại  = 100
committed offset   = 95
consumer crash
restart từ         = 95
có thể xử lý lại    95–99
~~~

Nếu commit trước business processing, crash sau commit có thể làm record bị bỏ qua. Nếu commit sau processing, crash trước commit có thể tạo duplicate. Business effect cần idempotent.

### 8.4. Log end offset và lag

~~~text
lag ≈ log end offset - consumer position/committed offset
~~~

Cần biết metric đang dùng position, committed offset hay high watermark.

### 8.5. Offset cũ bị retention xóa

~~~text
record cũ đã bị xóa
consumer offset vẫn trỏ tới vị trí cũ
consumer không thể replay đầy đủ
~~~

Starting offset policy như earliest/latest/error phải được chọn theo business.

---

## 9. Leader, follower và replication

### 9.1. Leader

Leader của partition:

- Nhận produce request.
- Phục vụ read/write theo client configuration.
- Quyết định thứ tự append.
- Theo dõi replica synchronization.

### 9.2. Follower

Follower:

- Fetch data từ leader.
- Ghi bản sao local.
- Có thể thành leader khi leader failure.
- Không phải consumer application.

~~~text
orders partition 0
├── Broker 1: leader
├── Broker 2: follower
└── Broker 3: follower
~~~

### 9.3. Một partition, một leader

Một leader duy nhất giúp Kafka quyết định thứ tự ghi:

~~~text
Producer → partition leader → append offset
~~~

Khi leader failure, controller chọn replica phù hợp và client refresh metadata.

### 9.4. Replication factor

Replication factor là tổng số replica của partition, gồm leader:

~~~text
replication factor = 3
partition P0:
├── leader
├── follower
└── follower
~~~

RF 1 không có bản sao dự phòng. RF 3 thường là điểm bắt đầu production, nhưng cần xét broker count, failure domain và chi phí.

### 9.5. ISR

ISR là tập replica đang sufficiently in-sync:

~~~text
replicas: Broker 1, Broker 2, Broker 3
ISR:      Broker 1, Broker 2
~~~

Follower quá chậm hoặc mất kết nối có thể bị loại khỏi ISR. Khi bắt kịp, nó có thể quay lại.

### 9.6. RF không tự đảm bảo zero loss

Cần phối hợp:

- Producer acks.
- min.insync.replicas.
- Replica synchronization.
- Leader election policy.
- Storage health.
- Failure domain.
- Transaction/isolation nếu dùng.

Replication factor 3 không có nghĩa exactly-once.

---

## 10. Consumer group

Consumer group là logical subscriber gồm consumer instance cùng dùng group ID.

~~~text
topic order-events: P0, P1, P2, P3

group order-indexer
├── consumer A: P0, P1
└── consumer B: P2, P3
~~~

Mỗi partition trong một group được gán cho tối đa một consumer tại một thời điểm.

### 10.1. Group như work queue

~~~text
order-events
    ↓
group notification-worker
├── instance 1
├── instance 2
└── instance 3
~~~

Mỗi record được xử lý bởi một instance trong group.

### 10.2. Nhiều group như pub/sub

~~~text
order-events
├── group email
├── group analytics
└── group search
~~~

Mỗi group đọc độc lập toàn bộ stream theo tốc độ riêng.

### 10.3. Rebalance

Khi consumer join hoặc leave:

- Kafka rebalance partition assignment.
- Partition có thể chuyển sang consumer khác.
- Record có thể xử lý lại nếu offset chưa commit.
- Scale quá nhanh có thể tạo group instability.

Rebalance là bình thường nhưng có cost. Cần graceful shutdown và thời gian xử lý phù hợp với poll/session configuration.

### 10.4. Consumer nhiều hơn partition

~~~text
topic: 3 partitions
group: 5 consumers
~~~

Chỉ tối đa 3 consumer có partition; 2 consumer idle nhưng vẫn tốn process, memory và coordination.

### 10.5. Partition nhiều hơn consumer

~~~text
topic: 6 partitions
group: 2 consumers
├── C1: P0, P1, P2
└── C2: P3, P4, P5
~~~

Có thể thêm consumer để tăng throughput nếu downstream chịu được.

---

## 11. Partition assignment và key

### 11.1. Key-based partitioning

~~~text
key order-101 → partition 0
key order-102 → partition 2
key order-103 → partition 0
~~~

Nếu order-101 luôn dùng cùng key và mapping ổn định, event của order đó có thể giữ ordering trong partition.

~~~text
order-101:
OrderCreated → OrderPaid → OrderShipped
~~~

### 11.2. Key null

Record không có key có thể được phân phối theo strategy của producer. Event cùng aggregate có thể rơi vào partition khác nhau, nên null key không phù hợp cho ordering theo entity.

### 11.3. Key cardinality

Key quá ít:

~~~text
key = orders
mọi record → một partition
~~~

Key nên có:

- Độ phân bố đủ rộng.
- Semantics đúng với ordering.
- Stable format.
- Không chứa PII không cần thiết.

### 11.4. Tăng số partition

Tăng partition có thể ảnh hưởng hash-to-partition mapping cho record mới. Đây không nên là thay đổi tùy tiện nếu ứng dụng phụ thuộc key affinity lâu dài.

---

## 12. Retention

Kafka giữ record theo retention policy, không theo việc consumer đã xử lý.

### 12.1. Retention theo thời gian

~~~text
retention = 7 ngày
record cũ hơn 7 ngày → đủ điều kiện cleanup
~~~

### 12.2. Retention theo kích thước

~~~text
retention.bytes = giới hạn dung lượng
~~~

Segment cũ được cleanup khi vượt chính sách. Record có thể tồn tại lâu hơn một chút do segment rolling và background cleanup.

### 12.3. Consumer lag và retention

Nếu consumer lag lâu hơn retention:

~~~text
record cũ đã bị xóa
consumer không thể replay đầy đủ
~~~

SLA replay phải nhỏ hơn retention window hoặc cần archive khác.

### 12.4. Chọn retention theo use case

| Use case | Policy cần cân nhắc |
|---|---|
| Log/telemetry | Time/size retention |
| Order event | Replay và audit window |
| CDC | Khoảng thời gian đồng bộ |
| State topic | Compaction |
| Analytics raw event | Backfill requirement |
| Security audit | Compliance requirement |

Retention là một phần của data contract, không chỉ là cấu hình storage.

---

## 13. Log compaction

### 13.1. Compaction là gì?

Log compaction giữ lại ít nhất trạng thái cuối đã biết của mỗi key trong một partition.

~~~text
key=user-1
  v1: email=a@example.com
  v2: email=b@example.com
  v3: email=c@example.com
~~~

Sau compaction, log có thể giữ record cuối cho user-1:

~~~text
user-1 → email=c@example.com
~~~

Compaction chạy background và không có nghĩa mọi record cũ biến mất ngay lập tức.

### 13.2. Use case

- User profile state.
- Product catalog state.
- Latest configuration.
- Materialized-view changelog.
- Cache rebuild.
- Database table change stream.

### 13.3. Compaction không giữ full history

Nếu cần biết mọi lần user đổi email, dùng event history topic hoặc retention theo thời gian. Compacted topic phù hợp để khôi phục state hiện tại, không phải audit đầy đủ.

### 13.4. Tombstone

Record có key và value null thường biểu thị delete:

~~~text
key = user-1
value = null
~~~

Tombstone được giữ một khoảng thời gian trước khi cleaner xóa. Consumer lag quá lâu có thể bỏ lỡ tombstone.

### 13.5. Offset sau compaction

Compaction xóa record cũ nhưng không đánh lại offset:

~~~text
offset 10 không còn record
offset 11 vẫn tồn tại
consumer đọc từ 10 sẽ bắt đầu ở record kế tiếp còn tồn tại
~~~

Ordering của record còn lại vẫn được giữ.

---

## 14. Producer và consumer trong architecture

### 14.1. Producer flow

~~~text
1. tạo record
2. chọn topic
3. chọn key
4. partitioner chọn partition
5. gửi đến partition leader
6. broker append và cấp offset
7. producer nhận response theo ack setting
~~~

Producer cần quyết định key, serializer, batching, compression, acks, retry, idempotence và timeout.

### 14.2. Consumer flow

~~~text
1. join consumer group
2. subscribe topic
3. nhận partition assignment
4. poll records
5. xử lý record
6. commit offset
7. poll tiếp
~~~

Consumer cần quyết định group ID, starting offset, commit mode, batch/record processing, error handling, replay và concurrency.

### 14.3. Offset commit không phải business transaction

Flow nguy hiểm:

~~~text
commit offset
  ↓
update database
~~~

Nếu crash giữa hai bước, record có thể bị bỏ qua.

Flow thường an toàn hơn:

~~~text
poll record
  ↓
business transaction/idempotent effect
  ↓
commit offset
~~~

Crash trước commit có thể gây xử lý lại; đó là lý do business effect phải idempotent.

---

## 15. Kafka và RabbitMQ

| RabbitMQ | Kafka |
|---|---|
| Message thường bị loại sau ack | Record giữ theo retention |
| Queue là work distribution | Topic/partition là distributed log |
| Exchange/binding/routing key | Topic/key/partition |
| Broker push delivery phổ biến | Consumer fetch/poll |
| Ack message | Commit offset |
| Competing consumers trong queue | Consumer instances trong group |
| Fanout queue riêng | Consumer groups độc lập |
| Ordering theo queue/flow | Ordering trong partition |
| Retry/DLQ thường là broker topology | Retry/DLT thường là app/topic pattern |
| Scale bằng consumers/prefetch | Scale bằng partitions/groups |
| Replay không phải mental model chính | Replay bằng offset/retention |

Chọn theo ordering, replay, throughput, routing, retention, consumer model và năng lực vận hành.

---

## 16. Ví dụ topology order-events

### 16.1. Topic

~~~text
order-events
├── partition 0
├── partition 1
├── partition 2
├── partition 3
├── partition 4
└── partition 5
~~~

### 16.2. Consumer groups

~~~text
order-events
├── notification-group
│   ├── N1: P0, P1
│   ├── N2: P2, P3
│   └── N3: P4, P5
├── analytics-group
│   ├── A1: P0, P1, P2
│   └── A2: P3, P4, P5
└── search-group
    └── S1: P0...P5
~~~

Mỗi group nhận toàn bộ stream theo assignment riêng. Notification lag không làm analytics group dừng, nhưng mỗi group có lag riêng.

### 16.3. Key

~~~text
key = orderId
~~~

Các event của một order đi cùng partition trong điều kiện mapping ổn định:

~~~text
order-123
├── OrderCreated
├── OrderPaid
└── OrderShipped
~~~

Ordering được giữ trong partition; không có ordering global giữa các order ở các partition khác nhau.

---

## 17. Ngộ nhận thường gặp

### “Kafka là queue, đọc xong record biến mất”

Không đúng. Record tồn tại theo retention hoặc compaction; consumer lưu offset.

### “Offset là ID của event toàn hệ thống”

Không đúng. Offset chỉ có ý nghĩa trong topic-partition.

### “Nhiều consumer luôn tăng throughput”

Không nếu consumer nhiều hơn partition, partition bị hot hoặc downstream là bottleneck.

### “Kafka đảm bảo ordering toàn topic”

Không. Ordering được đảm bảo trong partition.

### “Cùng key luôn tuyệt đối cùng partition dù tăng partition”

Không nên giả định như vậy. Tăng partition có thể ảnh hưởng mapping của record mới.

### “Replication factor 3 nghĩa không thể mất data”

Không. Cần producer ack, ISR, min in-sync replicas và failure policy.

### “Compaction giống delete record cũ theo thời gian”

Không. Compaction giữ latest value theo key và chạy background; nó không phải audit history đầy đủ.

### “Hai service cùng group đều nhận mọi event”

Không. Hai service cùng group chia partition. Muốn cả hai nhận mọi event, dùng group ID khác nhau.

### “Restart consumer luôn đọc từ đầu”

Không. Consumer thường tiếp tục từ committed offset nếu offset còn tồn tại và starting policy cho phép.

---

## 18. Bài tập thực hành

### Bài 1: Vẽ cluster

Vẽ cluster 3 broker với topic order-events có 6 partition và replication factor 3.

Ghi rõ:

- Partition leader.
- Follower.
- Broker chứa replica.
- Nếu Broker 2 chết, partition nào cần leader election?
- Nếu ISR thiếu replica, rủi ro là gì?

### Bài 2: Partition assignment

Topic có 6 partition:

~~~text
P0 P1 P2 P3 P4 P5
~~~

Tính assignment cho 1, 2, 3, 6 và 8 consumer. Ghi rõ consumer idle và parallelism tối đa.

### Bài 3: Key routing

Tạo 20 record cho 5 order ID với 3 partition.

1. Gửi tất cả với key là order ID.
2. Gửi lại với key null.
3. Quan sát partition distribution.
4. Kiểm tra event cùng order có ordering không.
5. Tạo hot key và xem ảnh hưởng.

### Bài 4: Consumer groups

Tạo topic 4 partition và ba group:

~~~text
notification-group
analytics-group
audit-group
~~~

Chạy 2 consumer cho notification, 4 cho analytics và 6 cho audit. Dự đoán assignment và giải thích vì sao group đọc độc lập.

### Bài 5: Offset replay

Với một consumer group:

1. Đọc từ latest.
2. Commit offset.
3. Dừng consumer.
4. Reset hoặc seek về offset cũ.
5. Replay record.
6. Chứng minh side effect phải idempotent.

### Bài 6: Retention và lag

Giả sử producer tạo 1.000 record/phút, consumer xử lý 600 record/phút, retention là 2 giờ.

1. Tính backlog sau 30 phút và 2 giờ.
2. Xác định thời điểm consumer có thể không replay đầy đủ record cũ.
3. Đề xuất archive hoặc scaling strategy.

### Bài 7: Compaction

Tạo compacted topic với key user ID:

~~~text
user-1 → A
user-2 → X
user-1 → B
user-1 → C
user-2 → Y
user-3 → Z
user-1 → tombstone
~~~

Giải thích trạng thái cuối và vì sao compacted topic không thay thế event history topic.

### Bài 8: So sánh RabbitMQ/Kafka

Chọn RabbitMQ hoặc Kafka cho:

1. Email background job.
2. Order event cần nhiều consumer group.
3. Request/reply ngắn.
4. Data pipeline có replay.
5. Routing theo nhiều pattern.
6. Changelog để rebuild cache.
7. Task xử lý xong rồi bỏ message.

---

## 19. Câu hỏi tự kiểm tra

1. Kafka lưu record ở đâu?
2. Topic khác partition như thế nào?
3. Vì sao partition là đơn vị parallelism?
4. Offset có global trong topic không?
5. Leader và follower khác nhau ở đâu?
6. Replication factor bao gồm leader không?
7. ISR có ý nghĩa gì?
8. Consumer position và committed offset khác nhau ra sao?
9. Vì sao consumer nhiều hơn partition không tăng parallelism?
10. Hai consumer group khác nhau đọc cùng topic thế nào?
11. Key ảnh hưởng partition assignment và ordering ra sao?
12. Vì sao null key không phù hợp cho ordering theo aggregate?
13. Retention khác compaction như thế nào?
14. Tombstone dùng cho mục đích gì?
15. Vì sao compaction không phải full audit history?
16. Consumer lag có thể làm mất khả năng replay ra sao?
17. Vì sao tăng partition có thể ảnh hưởng key mapping?
18. Kafka khác RabbitMQ ở mental model nào?
19. KRaft controller chịu trách nhiệm gì?
20. Vì sao replication factor 3 không tự đảm bảo exactly-once?

---

## 20. Checklist hoàn thành Tuần 9

- [ ] Mình hiểu broker, controller và cluster.
- [ ] Mình hiểu topic là stream logic được chia thành partition.
- [ ] Mình phân biệt record, key, value, header và offset.
- [ ] Mình biết ordering chỉ được đảm bảo trong partition.
- [ ] Mình biết partition là đơn vị parallelism.
- [ ] Mình dự đoán được assignment khi số consumer thay đổi.
- [ ] Mình hiểu leader/follower và replication factor.
- [ ] Mình hiểu ISR ở mức khái niệm.
- [ ] Mình phân biệt consumer position với committed offset.
- [ ] Mình hiểu consumer group là logical subscriber.
- [ ] Mình biết group khác nhau đọc độc lập cùng topic.
- [ ] Mình biết key ảnh hưởng routing và ordering.
- [ ] Mình hiểu hot partition và hot key.
- [ ] Mình phân biệt retention với log compaction.
- [ ] Mình hiểu tombstone và compacted topic.
- [ ] Mình biết consumer lag có thể vượt retention.
- [ ] Mình so sánh được Kafka với RabbitMQ.
- [ ] Mình biết partition count là quyết định kiến trúc dài hạn.
- [ ] Mình hoàn thành bài partition assignment và consumer groups.

## Kết luận

Kafka là một distributed append-only log. Topic được chia thành partition; mỗi partition có thứ tự riêng, offset riêng và một leader phục vụ việc ghi. Replication tạo bản sao trên nhiều broker, còn consumer group chia partition cho nhiều consumer để vừa giữ ordering trong partition vừa scale processing.

Bốn nguyên tắc cần nhớ:

~~~text
Topic = stream logic
Partition = ordering + parallelism
Offset = vị trí đọc trong một partition
Consumer group = logical subscriber + load balancing
~~~

Key là quyết định business quan trọng: nó quyết định record đi đâu và event nào có thể giữ ordering cùng nhau. Retention quyết định thời gian replay; compaction quyết định cách giữ trạng thái cuối theo key.

Ở Tuần 10, các khái niệm này sẽ được triển khai bằng Kafka producer, consumer, consumer group, offset commit và Spring Kafka.

## Nguồn Apache Kafka chính thức

- [Apache Kafka Design](https://kafka.apache.org/design/)
- [Apache Kafka 4.0 Design](https://kafka.apache.org/40/design/design/)
- [Apache Kafka Consumer API](https://kafka.apache.org/40/javadoc/org/apache/kafka/clients/consumer/KafkaConsumer.html)
- [Apache Kafka KRaft](https://kafka.apache.org/41/operations/kraft/)
- [Apache Kafka Monitoring](https://kafka.apache.org/40/operations/monitoring/)
- [Apache Kafka Producer configurations](https://kafka.apache.org/35/configuration/producer-configs/)

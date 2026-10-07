# Tuần 13 — Performance và Capacity Planning

> Mục tiêu: đo đúng throughput/latency, tìm bottleneck theo broker/partition/producer/consumer và xây capacity model cho Kafka thay vì tăng partition theo cảm tính.

## 1. Kết quả cần đạt

Sau bài học, bạn có thể:

- Phân biệt throughput, latency, capacity và utilization.
- Đo throughput theo broker, partition, producer và consumer.
- Hiểu batch size, linger, compression và buffer memory.
- Hiểu fetch.min.bytes, fetch.max.wait.ms và max.poll.records.
- Ước lượng partition count dựa trên throughput và consumer parallelism.
- Nhận diện giới hạn I/O, disk, network, CPU, JVM và page cache.
- Đọc consumer lag cùng processing time và arrival rate.
- Nhận diện hot partition, key skew và uneven assignment.
- Thiết kế benchmark có warm-up, steady state và p50/p95/p99.
- Tính retention storage theo event rate, record size, compression và replication.
- Xây capacity model có headroom và failure scenario.
- Chọn scaling action dựa trên bottleneck thật.

## 2. Performance không chỉ là throughput

Một hệ thống Kafka có nhiều chỉ số:

- throughput: records/s hoặc bytes/s;
- latency: thời gian produce/consume/process;
- lag: backlog chưa xử lý;
- availability: thời gian broker/consumer sẵn sàng;
- durability: mức an toàn khi broker lỗi;
- utilization: CPU, disk, network, memory;
- recovery time: thời gian catch up/restore.

Tối ưu một chỉ số có thể làm xấu chỉ số khác:

| Tối ưu | Có thể đánh đổi |
|---|---|
| Tăng batch/linger | latency tăng |
| Tăng compression | CPU tăng |
| Tăng partition | metadata/rebalance/disk overhead |
| Tăng consumer | downstream contention |
| Tăng retention | disk cost |
| Tăng replication | network/disk/write cost |
| Tăng fetch wait | latency cao hơn, batch tốt hơn |

Không nói “Kafka nhanh” mà không nêu workload, record size, replication, compression, partition và SLA.

## 3. Mô hình throughput

### 3.1 Producer arrival rate

Gọi:

    λ = arrival rate của record
    μ = processing/service rate

Nếu:

    λ < μ

hệ thống có khả năng catch up khi steady state.

Nếu:

    λ > μ

backlog tăng dần. Consumer lag sẽ tăng nếu không scale hoặc giảm workload.

### 3.2 Consumer capacity

Consumer capacity phụ thuộc:

- số partition được assign;
- records/s mỗi consumer;
- thời gian xử lý mỗi record;
- batch size;
- downstream latency;
- số worker;
- serialization/deserialization;
- commit overhead.

Ước lượng đơn giản:

    consumer_capacity
    ≈ số consumer active × records/s mỗi consumer

Nhưng nếu một partition hot hoặc downstream bị giới hạn, tổng capacity không đạt công thức này.

### 3.3 Broker capacity

Broker chịu:

- produce bytes;
- fetch bytes;
- replication traffic;
- disk write/read;
- network;
- request handling;
- page cache;
- metadata/partition overhead.

Replication factor 3 làm internal network/disk traffic lớn hơn traffic application. Capacity model phải tính replication.

## 4. Throughput và latency

### 4.1 Throughput

Đo:

    records_per_second
    bytes_per_second

Tách:

- producer send rate;
- broker bytes in;
- broker bytes out;
- consumer records consumed;
- sink write rate.

Tổng records/s không đủ để so sánh nếu record size khác nhau. Luôn ghi cả records/s và MB/s.

### 4.2 Latency

Các mốc có thể khác nhau:

    application create
       -> producer ack
       -> broker append
       -> consumer poll
       -> listener process
       -> downstream side effect

Định nghĩa metric trước:

- producer ack latency;
- Kafka consume latency;
- processing latency;
- end-to-end event age;
- sink commit latency.

### 4.3 p50, p95, p99

- p50: median, đại diện trải nghiệm thường gặp;
- p95: tail latency phổ biến cần xử lý;
- p99: tail xấu, thường lộ GC/network/downstream spike.

Average có thể che tail:

    99 record: 5ms
    1 record: 5s
    average gần thấp
    p99 rất cao

SLA nên dùng percentile phù hợp. Với consumer processing, p99 quan trọng vì batch/poll/rebalance có thể bị record chậm kéo dài.

## 5. Benchmark methodology

### 5.1 Xác định câu hỏi

Ví dụ:

- zstd hay lz4 tốt hơn cho payload thật?
- tăng partition từ 3 lên 6 có tăng throughput không?
- consumer chịu được downstream latency bao nhiêu?
- retention 14 ngày cần bao nhiêu disk?
- một broker chịu được bao nhiêu MB/s với RF=3?

Không benchmark chỉ để có con số đẹp. Mỗi test phải trả lời quyết định vận hành.

### 5.2 Warm-up

Warm-up để hệ thống đạt steady state:

- JVM JIT đã tối ưu;
- connection pool mở;
- page cache ổn định;
- producer batch hình thành;
- consumer assignment ổn định;
- schema/cache warm;
- database pool sẵn sàng.

Bỏ qua warm-up có thể đo startup artifact thay vì production behavior.

### 5.3 Steady state

Chỉ lấy số liệu sau khi:

- producer rate ổn định;
- consumer assignment ổn định;
- không còn startup restore;
- disk/network không đang burst;
- không có rebalance ngoài ý muốn;
- queue/backlog không đang tăng do warm-up.

### 5.4 Test matrix

Mỗi benchmark ghi:

- Kafka/broker version;
- client version;
- record size;
- payload distribution;
- partition count;
- replication factor;
- producer config;
- consumer config;
- compression;
- rate;
- duration;
- hardware/container limit;
- JVM;
- result percentile.

### 5.5 Lặp lại

Chạy mỗi profile nhiều lần. Ghi median và variance. Một lần chạy không đủ để kết luận.

## 6. Producer tuning

### 6.1 Batch size

Batch lớn giúp:

- giảm số request;
- tăng compression ratio;
- tăng network efficiency;
- tăng throughput.

Nhược điểm:

- chờ đủ batch;
- memory cao hơn;
- latency tail có thể tăng;
- batch theo partition nên traffic phân tán có thể vẫn nhỏ.

### 6.2 linger.ms

linger cho producer chờ thêm record cùng partition:

- 0/nhỏ: latency thấp;
- vừa: batching tốt;
- quá lớn: latency tăng và shutdown/timeout phức tạp.

Chọn theo SLA và arrival rate, không theo giá trị phổ biến trên Internet.

### 6.3 Compression

Compression ảnh hưởng:

- CPU producer/broker/consumer;
- network;
- disk;
- batch efficiency;
- latency.

Benchmark với payload thật. JSON có nhiều field lặp thường nén tốt hơn payload random/binary đã nén.

### 6.4 buffer.memory

buffer.memory là vùng producer dùng để buffer record trước khi gửi. Nếu producer nhanh hơn broker:

- buffer đầy;
- send chờ;
- timeout tăng;
- application backpressure.

Tăng buffer chỉ trì hoãn vấn đề nếu broker/downstream không catch up.

### 6.5 max.request.size

Record/batch quá lớn có thể fail do:

- producer max.request.size;
- broker message.max.bytes;
- topic max.message.bytes;
- consumer fetch limits;
- connector/sink limits.

Các giới hạn phải tương thích toàn pipeline. Không tăng mọi limit mù vì record lớn làm memory/network/latency xấu.

## 7. Consumer fetch và poll tuning

### 7.1 fetch.min.bytes

Consumer chờ tới khi broker có lượng dữ liệu tối thiểu trước khi trả response, tùy timeout.

- lớn hơn: batch/network efficiency tốt hơn;
- latency có thể cao hơn khi traffic thấp.

### 7.2 fetch.max.wait.ms

Giới hạn thời gian broker chờ đủ fetch.min.bytes.

Kết hợp:

    fetch.min.bytes cao
    fetch.max.wait.ms cao
    => throughput tốt hơn nhưng latency tail tăng

### 7.3 max.poll.records

Giới hạn record trả về mỗi poll. Cần cân bằng:

    records per poll × processing time
    < max.poll.interval.ms

Quá lớn:

- batch xử lý lâu;
- rebalance risk;
- memory/queue lớn;
- commit boundary rộng.

Quá nhỏ:

- overhead poll/commit;
- throughput giảm.

### 7.4 fetch.max.bytes và max.partition.fetch.bytes

Giới hạn lượng dữ liệu fetch ở các scope khác nhau. Record lớn cần kiểm tra mọi giới hạn, nhưng tăng quá cao có thể tạo memory pressure.

### 7.5 Consumer concurrency

Tăng consumer chỉ hiệu quả khi:

- còn partition chưa được xử lý;
- downstream chịu được;
- broker có capacity;
- key phân bố không skew;
- processing CPU/network có thể song song.

## 8. Partition count và giới hạn I/O

### 8.1 Partition giúp scale

Partition cho phép:

- producer gửi song song;
- broker phân tán leader;
- consumer group xử lý song song;
- disk/log tách workload.

### 8.2 Partition không miễn phí

Mỗi partition có:

- metadata;
- segment files;
- index;
- replica;
- open file/IO activity;
- state cho consumer;
- rebalance/assignment overhead.

Quá nhiều partition có thể làm cluster chậm dù traffic không cao.

### 8.3 Ước lượng partition theo throughput

Giả sử:

    incoming = 120 MB/s
    target per partition = 20 MB/s
    headroom = 30%

Partition sơ bộ:

    ceil(120 / 20 × 1.3)
    = ceil(7.8)
    = 8 partitions

Đây chỉ là starting point. Cần benchmark với record size, key skew, replication và failure scenario.

### 8.4 Ước lượng theo consumer

Nếu mỗi consumer xử lý 5.000 records/s và peak là 20.000 records/s:

    consumer cần khoảng 4 active capacity units

Topic cần ít nhất đủ partition để assignment cho 4 consumer, cộng headroom scale. Nhưng partition count còn phụ thuộc ordering key và broker capacity.

### 8.5 Không tăng partition để chữa mọi lỗi

Nếu bottleneck là:

- database;
- hot key;
- broker disk;
- network;
- serialization;
- downstream API;

tăng partition có thể chỉ tăng concurrency vào bottleneck và làm hệ thống tệ hơn.

## 9. Disk throughput và page cache

### 9.1 Disk

Kafka cần disk cho:

- append log;
- segment roll;
- index;
- replica fetch;
- cleanup/retention;
- compaction;
- recovery.

Theo dõi:

- write MB/s;
- read MB/s;
- IOPS;
- latency;
- disk utilization;
- free space;
- segment count;
- fsync/flush behavior tùy storage;
- replica catch-up.

### 9.2 Page cache

Kafka dựa nhiều vào OS page cache để đọc log hiệu quả. Nếu JVM heap quá lớn nhưng page cache quá nhỏ:

- fetch performance giảm;
- broker đọc disk nhiều hơn;
- GC pressure tăng;
- cache hit giảm.

Đừng cấp toàn bộ RAM cho JVM. Cần chừa RAM cho page cache và hệ điều hành theo deployment model.

### 9.3 Retention và disk growth

Dung lượng sơ bộ:

    raw_data
    = ingress_bytes_per_sec × retention_seconds

Tính thêm:

    total_storage
    = raw_data × replication_factor
      × overhead_factor
      + safety_headroom

Overhead gồm segment/index, filesystem, compression variation và operational headroom.

## 10. Network bandwidth

Broker network gồm:

- producer -> leader;
- leader -> follower replication;
- broker -> consumer;
- inter-broker movement/recovery;
- controller/metadata traffic;
- Connect/Streams internal topic traffic.

Ví dụ application produce 100 MB/s với RF=3:

- broker nhận khoảng 100 MB/s client write;
- replication tạo thêm traffic inter-broker;
- consumer fetch tạo traffic outbound;
- tổng network phụ thuộc leader/follower/consumer placement.

Không capacity plan chỉ bằng bytes in metric.

### 10.1 Compression ảnh hưởng network

Compression có thể giảm bytes truyền, nhưng:

- producer/broker/consumer dùng CPU;
- ratio phụ thuộc payload;
- compressed batch theo partition;
- sink có thể decompress/re-encode.

Benchmark end-to-end chứ không chỉ đo producer CPU.

## 11. CPU, JVM và garbage collection

CPU dùng cho:

- compression/decompression;
- serialization/deserialization;
- request handling;
- replication;
- compaction;
- TLS/SASL;
- application processing.

JVM concerns:

- heap quá nhỏ: OOM/GC;
- heap quá lớn: page cache thiếu;
- allocation nhiều: p99 latency tăng;
- schema/JSON object creation: CPU/GC;
- long GC pause: heartbeat/poll/rebalance risk.

Theo dõi:

- heap used/max;
- GC pause;
- allocation rate;
- CPU per broker/client;
- task processing CPU;
- thread pool saturation.

## 12. Consumer lag

### 12.1 Lag là backlog

Khái niệm:

    lag = log_end_offset - current/committed_offset

Lag tăng nếu:

- producer arrival rate tăng;
- processing time tăng;
- consumer thiếu capacity;
- partition hot;
- downstream chậm;
- consumer paused;
- task failed;
- commit không tiến.

### 12.2 Lag không phải luôn lỗi

Lag có thể bình thường trong:

- batch processing;
- replay;
- traffic burst ngắn;
- sink retry có deadline.

Quan trọng là:

- lag có tăng đều không;
- oldest event age có vượt SLA không;
- consumer có catch up sau peak không;
- partition nào lag;
- lag do producer hay consumer.

### 12.3 Oldest event age

Nếu record timestamp tin cậy:

    oldest_event_age
    = now - timestamp của record cũ nhất chưa xử lý

Age thường phản ánh SLA tốt hơn tổng lag khi event rate biến động.

## 13. Processing time và arrival rate

Đo per-record hoặc per-batch:

- processing p50/p95/p99;
- downstream latency;
- queue wait;
- poll interval;
- commit latency;
- serialization time.

Ví dụ:

    arrival = 10.000 records/s
    processing capacity = 8.000 records/s
    lag growth = 2.000 records/s

Tăng consumer lên 2x chỉ giúp nếu processing có thể scale và downstream không giới hạn.

### 13.1 Little’s Law ở mức trực giác

Trong hệ thống ổn định:

    concurrency ≈ throughput × latency

Nếu cần xử lý 1.000 records/s và mỗi record mất 50ms:

    concurrency ≈ 1000 × 0.05 = 50 in-flight records

Đây là ước lượng; batching, partition và backpressure làm thực tế phức tạp hơn.

## 14. Hot partition và skew

### 14.1 Hot partition

Một partition có:

- bytes in cao;
- records/s cao;
- lag cao;
- leader/broker tải cao.

Nguyên nhân thường:

- hot key;
- key cardinality thấp;
- custom partitioner;
- producer không phân bố;
- một partition nhận traffic đặc biệt.

### 14.2 Uneven assignment

Consumer assignment có thể không đều:

    consumer-A -> partition 0,1,2
    consumer-B -> partition 3
    consumer-C -> partition 4

Nếu partition 0 hot, consumer-A lag cao dù B/C rảnh.

### 14.3 Cách xử lý

- đổi partition key;
- tăng cardinality;
- bucket key nếu ordering cho phép;
- tách tenant lớn;
- tạo topic riêng;
- scale consumer không giải quyết single hot partition;
- xử lý downstream theo aggregate/version;
- rebalance/assignment strategy phù hợp.

## 15. Capacity model

### 15.1 Input assumptions

Ghi rõ:

- average records/s;
- peak records/s;
- average record bytes;
- p99 record bytes;
- producer count;
- consumer group count;
- retention;
- replication factor;
- compression ratio;
- partition throughput target;
- broker disk/network;
- headroom;
- failure scenario.

### 15.2 Storage

Ví dụ:

    peak ingress = 50 MB/s
    retention = 7 days
    RF = 3
    compression ratio = 0.5
    overhead/headroom = 1.3

Thời gian:

    7 × 24 × 3600 = 604.800s

Raw compressed logical storage:

    50 × 0.5 × 604.800
    = 15.120.000 MB
    ≈ 15.12 TB

Replication:

    15.12 × 3 = 45.36 TB

Headroom:

    45.36 × 1.3 ≈ 58.97 TB

Đây là ước lượng sơ bộ. Cần tính distribution, segment/index, cleanup lag, broker imbalance và failure reserve.

### 15.3 Broker count

Nếu một broker có usable disk an toàn D:

    broker_count_storage
    = ceil(total_required_storage / D)

Sau đó kiểm tra thêm:

- network;
- disk throughput;
- CPU;
- partition count;
- replica placement;
- failure domain;
- maintenance capacity.

Bottleneck lớn nhất quyết định số broker, không chỉ disk.

### 15.4 Consumer capacity

Ví dụ:

    peak = 24.000 records/s
    one consumer = 4.000 records/s
    utilization target = 70%

Capacity mỗi consumer:

    4.000 × 0.7 = 2.800 records/s

Consumer count:

    ceil(24.000 / 2.800) = 9

Topic cần ít nhất 9 partition cho group, cộng headroom partition nếu ordering/business design cho phép.

## 16. Benchmark producer compression

### Mục tiêu

So sánh none, lz4 và zstd với payload OrderCreated thật.

### Profile

| Profile | Compression | Batch | Linger |
|---|---|---:|---:|
| baseline | none | default | 0 |
| low-latency | lz4 | nhỏ | 0 |
| balanced | lz4 | vừa | 5ms |
| storage | zstd | vừa | 5ms |

### Đo

- records/s;
- MB/s raw;
- bytes sent;
- p50/p95/p99 ack latency;
- CPU;
- GC;
- error;
- broker disk/network.

### Kết luận

Chọn profile dựa trên SLA và resource budget, không chỉ chọn compression ratio cao nhất.

## 17. Bài lab 2 — Throughput khi tăng partition

1. Tạo topic 1 partition.
2. Chạy producer cố định payload/rate.
3. Chạy consumer group.
4. Đo throughput/lag.
5. Tạo topic 3 partition.
6. Lặp lại.
7. Tạo topic 6 partition.
8. So sánh broker CPU/disk/network.
9. Kiểm tra key distribution.
10. Ghi điểm mà tăng partition không còn cải thiện.

Giữ mọi biến khác giống nhau. Không so sánh test có record size hoặc compression khác nhau.

## 18. Bài lab 3 — Consumer lag khi downstream chậm

1. Producer gửi rate ổn định.
2. Consumer xử lý bình thường.
3. Đo lag baseline.
4. Thêm sleep vào downstream.
5. Theo dõi lag theo partition.
6. Khôi phục downstream.
7. Đo thời gian catch up.
8. Tăng consumer hoặc batch.
9. Kiểm tra downstream có thành bottleneck mới không.

Ghi:

- arrival rate;
- processing rate;
- lag slope;
- oldest event age;
- recovery time;
- duplicate/retry nếu có.

## 19. Bài lab 4 — Hot key và đổi partition key

1. Tạo topic nhiều partition.
2. Gửi key phân bố đều.
3. Gửi 80% record cùng một key.
4. Quan sát bytes/records/lag mỗi partition.
5. Đổi key sang customerId/orderId phù hợp.
6. Thử bucket key nếu được phép.
7. So sánh ordering và skew.
8. Viết trade-off document.

Không chỉ xem tổng throughput; xem partition-level metrics.

## 20. Bài lab 5 — Tính retention storage

Dùng input:

- average MB/s;
- peak MB/s;
- retention days;
- compression ratio;
- replication factor;
- overhead;
- free-space reserve.

Tính:

1. raw bytes;
2. compressed bytes;
3. replica bytes;
4. headroom;
5. broker count;
6. disk usage khi mất một broker.

Báo cáo phải nêu assumption. Không đưa một con số capacity mà không có input.

## 21. Bảng chẩn đoán

| Triệu chứng | Khả năng | Kiểm tra |
|---|---|---|
| Lag tăng đều | Consumer không theo kịp | Processing time, arrival rate, partition count |
| Chỉ một partition lag | Hot key/skew | Key distribution, partition bytes, leader load |
| Producer timeout | Broker/network/disk bận | Request latency, buffer, ISR, disk |
| Rebalance liên tục | Poll chậm/session lỗi | max.poll.interval, heartbeat, GC |
| Disk tăng nhanh | Retention/segment sai | Bytes in, compression, retention config |
| CPU producer cao | Compression/serialization | CPU profile, codec, payload |
| P99 tăng | GC, batch, downstream | p99 stage latency, GC pause |
| Consumer rảnh nhưng lag cao | Hot partition/uneven assignment | Assignment và lag theo partition |
| Tăng partition không cải thiện | Bottleneck ngoài partition | Disk/network/downstream/key skew |
| Catch-up rất chậm | Consumer capacity thấp | Processing rate, fetch, batch, sink |

## 22. Scaling playbook

### Producer bottleneck

Kiểm tra:

- serializer CPU;
- compression;
- batch/linger;
- buffer exhaustion;
- broker ack latency;
- network.

Actions:

- batch hợp lý;
- compression benchmark;
- tăng producer instance;
- điều chỉnh partition;
- giảm payload;
- sửa network/broker.

### Broker bottleneck

Kiểm tra:

- disk utilization/latency;
- network;
- CPU;
- leader skew;
- replication;
- partition count.

Actions:

- phân bố leader/replica;
- thêm broker;
- tách disk/workload;
- giảm traffic/batch;
- xem retention;
- không chỉ tăng JVM.

### Consumer bottleneck

Kiểm tra:

- processing p99;
- downstream;
- assignment;
- hot partition;
- max.poll.records;
- commit/retry.

Actions:

- tối ưu logic;
- tăng consumer trong giới hạn partition;
- batch downstream;
- pause/backpressure;
- đổi key/topic;
- scale downstream.

## 23. Sai lầm phổ biến

### Dùng average thay p99

Tail latency gây timeout/rebalance dù average tốt.

### Benchmark chưa warm-up

Kết quả phản ánh startup/JIT chứ không phải steady state.

### Tăng partition vô hạn

Partition có metadata, disk, replication và rebalance cost.

### Tăng buffer để chữa broker chậm

Chỉ tích backlog trong memory.

### Tăng consumer khi một partition hot

Một partition vẫn chỉ có một owner trong group.

### Không tính replication vào storage/network

RF=3 không miễn phí.

### Chừa quá ít page cache

Heap lớn không thay thế OS cache của Kafka.

### Đo records/s mà quên bytes/s

Record size khác làm con số records/s gây hiểu lầm.

### Tối ưu compression trên payload giả

Payload thật có distribution khác.

### Không đo catch-up time

Lag giảm chậm sau outage có thể vi phạm SLA dù steady state pass.

### Capacity chỉ theo average

Peak, failure và maintenance cần headroom.

## 24. Câu hỏi tự kiểm tra

1. Throughput khác latency thế nào?
2. Vì sao cần p95/p99?
3. Warm-up và steady state là gì?
4. Batch/linger ảnh hưởng gì?
5. Compression đánh đổi CPU với tài nguyên nào?
6. fetch.min.bytes và fetch.max.wait.ms làm gì?
7. max.poll.records liên quan max.poll.interval thế nào?
8. Vì sao partition giúp scale nhưng cũng có cost?
9. Lag tăng có thể do những nguyên nhân nào?
10. Oldest event age hữu ích khi nào?
11. Hot partition khác tổng lag thế nào?
12. Page cache quan trọng ra sao?
13. Vì sao RF phải tính vào network/storage?
14. Tại sao consumer rảnh nhưng group vẫn lag?
15. Khi nào tăng consumer không giúp?
16. Capacity model cần những input nào?
17. Vì sao cần failure headroom?
18. Làm sao benchmark compression công bằng?
19. Producer timeout nên kiểm tra gì?
20. Rebalance loop liên quan performance ra sao?

### Đáp án ngắn

1. Throughput là lượng xử lý; latency là thời gian chờ/xử lý.
2. Average che tail failure.
3. Warm-up ổn định runtime; steady state là giai đoạn đo chính.
4. Batch tăng efficiency nhưng có thể tăng latency.
5. Giảm network/disk nhưng tăng CPU.
6. Điều khiển lượng dữ liệu/thời gian broker chờ fetch.
7. Batch xử lý phải hoàn thành trong poll interval.
8. Tăng parallelism nhưng tăng metadata/disk/rebalance.
9. Arrival tăng, consumer chậm, hot key, retry, paused/task fail.
10. Đo tuổi record cũ nhất theo SLA.
11. Hot partition là nghẽn cục bộ, tổng lag có thể che giấu.
12. Hỗ trợ đọc log nhanh mà không tăng JVM heap.
13. Replica tạo write/read/replication traffic và disk.
14. Partition hot hoặc assignment không đều.
15. Hết partition hoặc downstream bottleneck.
16. Rate, size, retention, RF, compression, partition, hardware, headroom.
17. Peak, broker down, rebuild và maintenance.
18. Cùng payload, config, duration, warm-up và đo CPU/network/percentile.
19. Broker, network, disk, buffer, ISR, request latency.
20. Processing/GC/poll chậm làm member rời group.

## 25. Checklist hoàn thành Tuần 13

- [ ] Tôi phân biệt throughput, latency và lag.
- [ ] Tôi đo được records/s và bytes/s.
- [ ] Tôi hiểu batch, linger, compression và buffer memory.
- [ ] Tôi hiểu fetch.min.bytes và fetch.max.wait.ms.
- [ ] Tôi liên hệ max.poll.records với processing time.
- [ ] Tôi biết partition có giới hạn/cost.
- [ ] Tôi theo dõi disk, network, CPU, JVM và page cache.
- [ ] Tôi đọc lag theo partition và oldest event age.
- [ ] Tôi nhận diện hot partition/skew/uneven assignment.
- [ ] Tôi benchmark có warm-up và steady state.
- [ ] Tôi ghi p50/p95/p99.
- [ ] Tôi có capacity model producer/consumer/broker/disk.
- [ ] Tôi tính storage có compression, RF và headroom.
- [ ] Tôi đo catch-up time sau downstream outage.
- [ ] Tôi không tăng partition để chữa mọi bottleneck.
- [ ] Tôi hoàn thành 5 bài lab của tuần.
- [ ] Tôi có bảng chẩn đoán triệu chứng-performance.
- [ ] Tôi có ngưỡng alert lag và oldest event age.

## 26. Câu chốt cần nhớ

Performance tuning Kafka bắt đầu từ việc xác định bottleneck, không phải từ việc tăng một config ngẫu nhiên.

Năm ý cần nhớ:

1. Đo records/s cùng bytes/s và p50/p95/p99.
2. Lag phải được phân tích cùng arrival rate, processing time và partition distribution.
3. Batch/compression/fetch là trade-off giữa throughput, latency và CPU.
4. Capacity model phải tính partition, replication, disk, network, page cache và failure headroom.
5. Hot partition và downstream bottleneck không được giải quyết bằng cách tăng partition vô hạn.


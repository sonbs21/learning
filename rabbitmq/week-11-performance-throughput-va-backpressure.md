# Tuần 11 — Performance, throughput và backpressure

> Performance không phải là tăng consumer vô hạn. Mục tiêu là đo bottleneck, giữ latency trong giới hạn, đạt throughput ổn định và làm chậm producer trước khi broker, JVM hoặc downstream bão hòa.

## 1. Kết quả cần đạt

Sau bài học, bạn có thể:

- Phân biệt throughput, latency, concurrency và saturation.
- Dùng Little's Law để ước lượng số message in-flight.
- Chọn prefetch theo processing time.
- Đánh giá số consumer, channel và connection.
- Đo batch publish, batch ack và asynchronous confirms.
- Phân tích message size, serialization, compression và network cost.
- Nhận biết queue growth và thiết kế backpressure.
- Theo dõi JVM heap, GC, thread pool và connection pool.
- Thiết kế benchmark có warm-up, steady state, p50, p95 và p99.
- Giới hạn producer hoặc pause consumer khi downstream quá tải.

## 2. Throughput, latency, concurrency, saturation

### Throughput

~~~text
throughput = số message xử lý thành công / giây
~~~

Ghi rõ đang đo publish accepted, broker delivery, business success hay ack. Publish rate cao không có nghĩa business throughput cao.

### Latency

Đo riêng:

~~~text
publish latency
confirm latency
queue wait latency
consumer processing latency
delivery-to-ack latency
end-to-end latency
~~~

End-to-end:

~~~text
event created
  -> publish
  -> queue wait
  -> consume
  -> processing
  -> ack
~~~

### Concurrency

Concurrency là số operation đang diễn ra cùng lúc:

~~~text
consumer count
in-flight messages
threads
channels
database connections
HTTP calls
~~~

### Saturation

Saturation xảy ra khi resource chạm giới hạn:

- CPU;
- JVM heap/GC;
- disk I/O;
- database pool;
- HTTP rate limit;
- RabbitMQ memory/disk alarm;
- queue ready tăng liên tục.

Sau saturation, thêm concurrency thường làm latency/error tăng.

## 3. Little's Law

Trong trạng thái ổn định:

~~~text
L = throughput × latency
~~~

Ví dụ:

~~~text
throughput = 100 messages/second
latency = 0.2 second
in-flight ≈ 20 messages
~~~

Nếu prefetch/concurrency giữ nhiều hơn rất nhiều mức cần thiết, hệ thống có thể đang giữ backlog thay vì xử lý nhanh hơn.

## 4. Bottleneck map

~~~text
Producer
  -> serialization
  -> network
  -> connection/channel
  -> broker
  -> queue
  -> consumer
  -> deserialization
  -> thread pool
  -> database/downstream
  -> ack
~~~

| Lớp | Dấu hiệu | Kiểm tra |
|---|---|---|
| Producer | Publish chậm | CPU, serialization, channel |
| Network | Confirm/delivery chậm | RTT, bandwidth |
| Broker | Confirm chậm/alarm | Disk, memory, rate |
| Queue | Ready tăng | Consumer throughput |
| Consumer | Unacked tăng | Processing, prefetch |
| Database | Thread chờ | Pool/query latency |
| Downstream | Timeout/429 | API metrics |
| JVM | GC pause | Heap, allocation, GC |

Không kết luận RabbitMQ chậm chỉ vì business handler chậm.

## 5. Prefetch và concurrency

Prefetch là số delivery chưa ack mà consumer/channel có thể giữ.

### Prefetch thấp

~~~text
prefetch=1
  -> fair hơn
  -> ít message bị giữ khi crash
  -> memory thấp
  -> throughput có thể thấp
~~~

### Prefetch cao

~~~text
prefetch=100
  -> pipeline tốt hơn trong workload nhanh
  -> consumer có thể hoard message
  -> unacked/memory/redelivery tăng
~~~

Nếu processing time không đồng đều, prefetch cao làm worker chậm giữ job của worker nhanh.

Concurrency cần dừng tăng khi:

- throughput không tăng;
- p95/p99 tăng;
- database pool cạn;
- downstream rate limit;
- CPU/context switching tăng.

Không mở connection theo request. Reuse connection; tạo channel/consumer theo workload và thư viện.

## 6. Batch và asynchronous confirms

### Batch publish

~~~text
publish nhiều message
  -> chờ confirm cả batch
~~~

Ưu điểm là giảm chờ và tăng throughput. Trade-off là retry batch có thể duplicate, pending memory tăng và latency message đầu tiên tăng.

Có thể flush khi:

~~~text
batch đủ 100 message
hoặc đã chờ 20 ms
~~~

### Batch ack

~~~java
channel.basicAck(lastDeliveryTag, true);
~~~

Chỉ dùng multiple ack khi mọi delivery trước lastDeliveryTag đã hoàn tất trên cùng channel. Với xử lý song song không theo thứ tự, dùng multiple ack có thể ack nhầm message chưa xong.

### Asynchronous confirm

~~~text
publish -> lưu sequence/pending
confirm callback -> mark confirmed
nack/timeout -> retry hoặc alert
~~~

Pending confirm phải có giới hạn. Producer nhanh hơn broker lâu dài sẽ làm memory tăng.

## 7. Message size và serialization

Message lớn làm tăng:

- network bandwidth;
- broker memory/disk;
- serialization CPU;
- JVM allocation;
- queue recovery time;
- latency.

Không gửi file/image lớn trực tiếp. Gửi reference:

~~~json
{
  "fileId": "file-100",
  "storageKey": "object/path/file",
  "contentType": "image/png",
  "sizeBytes": 1048576
}
~~~

Đo riêng object-to-JSON và JSON-to-object. Compression có thể giảm network nhưng tăng CPU. Payload nhỏ thường không đáng nén; payload lớn/repetitive mới nên benchmark.

## 8. JVM và downstream pool

Consumer blocking ở database/HTTP sẽ giữ delivery unacked:

~~~text
consumer thread
  -> chờ database
  -> chờ HTTP
  -> giữ message unacked
~~~

Theo dõi:

~~~text
heap used/max
GC pause
live threads
executor queue
database pool
HTTP pool
processing duration
~~~

Concurrency hiệu quả không nên vượt bottleneck nhỏ nhất:

~~~text
consumer concurrency
  <= database capacity
  <= downstream rate limit
  <= CPU/I/O capacity
~~~

Thread pool vô hạn chỉ chuyển backlog từ RabbitMQ sang JVM memory.

## 9. Backpressure

Nếu:

~~~text
producer rate > consumer rate
~~~

thì ready và oldest message age tăng. Backpressure làm chậm producer hoặc giới hạn in-flight work trước khi hệ thống cạn tài nguyên.

Chiến lược:

- bounded local buffer;
- rate limit/token bucket;
- pause/resume consumer;
- queue length/bytes limit;
- load shedding với message không quan trọng;
- upstream feedback 429/503;
- giảm payload hoặc processing cost.

Pause consumer bảo vệ downstream nhưng không giải quyết queue growth vô hạn. Cần alert và giới hạn backlog.

### Tính backlog

~~~text
producer = 1000 msg/s
consumer = 800 msg/s
growth = 200 msg/s
sau 600 giây = 120000 message
~~~

Nếu message trung bình 10 KB, payload backlog khoảng 1.2 GB trước overhead.

## 10. Benchmark methodology

### Câu hỏi benchmark

Ví dụ:

~~~text
Prefetch 1, 20 hay 200 tốt hơn cho worker 100 ms?
~~~

### Giữ biến ổn định

Ghi:

- RabbitMQ version/image;
- CPU/memory;
- topology;
- payload size;
- serialization;
- producer rate;
- consumer code;
- downstream latency;
- số lần chạy.

### Warm-up và steady state

Ví dụ:

~~~text
warm-up: 30 giây
measurement: 5 phút
cooldown: 30 giây
~~~

Không lấy giây đầu làm kết quả chính vì JVM, connection, cache và queue chưa ổn định.

### Percentile

~~~text
p50 = median
p95 = 95% operation nhanh hơn giá trị này
p99 = 99% operation nhanh hơn giá trị này
~~~

Mean có thể che tail latency. Ghi cả throughput, p50/p95/p99, max ready, max unacked, errors và resource usage.

## 11. Benchmark report

~~~text
Question:
Environment:
Payload size:
Producer rate:
Consumers:
Prefetch:
Batch size:
Warm-up:
Measurement:

Throughput:
Confirm p50/p95/p99:
Queue wait p50/p95/p99:
Processing p50/p95/p99:
End-to-end p50/p95/p99:
Max ready:
Max unacked:
Redelivery:
Error rate:
CPU:
Heap/GC:
Decision:
Trade-offs:
~~~

Một cấu hình throughput cao nhưng p99/error/memory xấu hơn chưa chắc là cấu hình tốt.

## 12. Lab 1 — Benchmark 1, 4 và 16 consumer

Giữ cố định:

~~~text
message count = 100000
payload size = 2 KB
processing time = 20 ms
prefetch = 20
~~~

Chạy:

~~~text
run A: 1 consumer
run B: 4 consumers
run C: 16 consumers
~~~

| Run | Consumers | Throughput | p95 | p99 | Max ready | Error |
|---|---:|---:|---:|---:|---:|---:|
| A | 1 | | | | | |
| B | 4 | | | | | |
| C | 16 | | | | | |

Nếu 16 consumer không nhanh hơn 4, tìm bottleneck CPU, database, thread pool hoặc downstream.

## 13. Lab 2 — Prefetch 1, 20 và 200

Giữ consumer count cố định:

~~~text
run A: prefetch=1
run B: prefetch=20
run C: prefetch=200
~~~

Đo throughput, ready, max unacked, p50/p95/p99, memory và redelivery sau crash.

Dự đoán:

~~~text
prefetch=1   -> fair hơn
prefetch=20  -> có thể cân bằng
prefetch=200 -> ready thấp nhưng unacked/memory cao
~~~

Không chỉ chọn run có ready thấp nhất.

## 14. Lab 3 — Batch confirm latency

So sánh:

~~~text
run A: confirm từng message
run B: batch 50
run C: batch 200
run D: asynchronous confirm
~~~

Ghi publish throughput, confirm p50/p95/p99, pending peak, memory, retry count, duplicate sau broker restart và returned count.

## 15. Lab 4 — Producer nhanh hơn consumer

Cấu hình:

~~~text
producer = 1000 msg/s
consumer = 700 msg/s
~~~

Quan sát 10 phút:

- ready;
- oldest message age;
- unacked;
- memory/disk;
- consumer utilization;
- end-to-end latency.

Thử:

1. tăng consumer từ 3 lên 6;
2. giới hạn producer rate;
3. pause consumer;
4. giảm payload;
5. giảm processing time;
6. đặt queue limit/alert.

Ghi chiến lược nào giải quyết root cause và chiến lược nào chỉ trì hoãn.

## 16. Lab 5 — Downstream quá tải

Tạo downstream mock:

~~~text
success rate = 95%
latency = 100 ms
rate limit = 200 request/s
~~~

Chạy consumer cao hơn capacity. Quan sát 429/timeout, retry, unacked, pool, queue growth và p99.

Sau đó:

1. giảm consumer;
2. đặt rate limit;
3. giảm prefetch;
4. pause consumer;
5. resume sau downstream hồi phục.

Mục tiêu là chứng minh broker không nên bị dùng để đẩy áp lực vô hạn sang downstream.

## 17. Bảng chẩn đoán

| Triệu chứng | Khả năng | Kiểm tra |
|---|---|---|
| Ready tăng | Consumer chậm/ít | Processing, consumer count |
| Unacked tăng | Ack muộn, prefetch cao, treo | Logs, prefetch, thread |
| Confirm latency tăng | Broker/disk/network | Disk, memory, publish rate |
| Redelivery tăng | Crash/requeue/retry | Error rate, restart |
| Memory alarm | Queue/payload quá lớn | Queue depth, size |
| CPU app cao | Serialize/GC/processing | CPU profile, GC |
| DB pool cạn | Concurrency vượt DB | Pool/query latency |
| p99 tăng, p50 ổn | Tail/downstream bottleneck | Stage percentiles |
| Ready thấp, unacked cao | Consumer hoard | Prefetch, processing |
| Thêm consumer không tăng throughput | Bottleneck downstream | DB/API/CPU |

## 18. Tối ưu theo thứ tự

~~~text
1. correctness và ack/idempotency
2. đo baseline
3. tìm bottleneck
4. tối ưu payload/serialization
5. điều chỉnh prefetch
6. điều chỉnh consumer/concurrency
7. batch/async confirm
8. backpressure/rate limit
9. benchmark lại
~~~

Không bắt đầu bằng prefetch 200 hoặc tăng consumer vô hạn khi chưa có baseline.

## 19. Checklist

- [ ] Có định nghĩa throughput và latency.
- [ ] Benchmark có warm-up và steady state.
- [ ] Ghi p50/p95/p99.
- [ ] Payload size được kiểm soát.
- [ ] Prefetch được benchmark.
- [ ] Concurrency không vượt downstream capacity.
- [ ] Connection/channel được reuse.
- [ ] Batch có giới hạn size và thời gian.
- [ ] Pending confirms có bound.
- [ ] Queue growth có alert/limit.
- [ ] Producer có rate limit/backpressure.
- [ ] Theo dõi heap/GC/thread/pool.
- [ ] Test downstream chậm/rate limit.
- [ ] Không hy sinh ack/idempotency để lấy throughput.

## 20. Deliverable cuối tuần

Tạo:

~~~text
rabbitmq/week-11/
├── README.md
├── benchmark-report.md
├── workload/
│   ├── producer-config.yaml
│   └── consumer-config.yaml
└── results/
    ├── consumers-1.csv
    ├── consumers-4.csv
    ├── consumers-16.csv
    ├── prefetch-1.csv
    ├── prefetch-20.csv
    └── prefetch-200.csv
~~~

README/report cần có:

1. Sơ đồ workload và bottleneck hypothesis.
2. Environment/version/resource.
3. Benchmark methodology.
4. Kết quả consumer 1/4/16.
5. Kết quả prefetch 1/20/200.
6. Batch confirm comparison.
7. Queue growth khi producer nhanh hơn consumer.
8. Backpressure strategy.
9. JVM/GC/pool observations.
10. Decision và trade-off.
11. Alert/limit trước memory/disk exhaustion.

## 21. Tiêu chí tự kiểm tra

1. Throughput khác latency thế nào?
2. Concurrency tăng tới khi nào không còn hiệu quả?
3. Saturation nhận biết qua dấu hiệu nào?
4. Little's Law ước lượng điều gì?
5. Prefetch cao làm ready thấp nhưng unacked cao ra sao?
6. Vì sao processing time không đồng đều cần prefetch thận trọng?
7. Batch confirm có trade-off gì?
8. Message size ảnh hưởng resource nào?
9. Compression khi nào có lợi?
10. Vì sao consumer count không vượt downstream capacity?
11. Backpressure khác retry thế nào?
12. Khi producer nhanh hơn consumer, backlog tăng ra sao?
13. Vì sao đo p95/p99 thay vì chỉ mean?
14. Warm-up/steady state có tác dụng gì?
15. Khi ready tăng, kiểm tra những lớp nào?
16. Vì sao pause consumer không giải quyết queue growth vô hạn?
17. Khi nào dùng rate limit, load shedding hoặc queue limit?
18. Vì sao không tăng consumer vô hạn?

### Đáp án cần nhớ

> Performance là bài toán đo bottleneck, không phải cuộc thi tăng consumer. Prefetch và concurrency phải phù hợp processing time và downstream capacity. Benchmark cần warm-up, steady state và p50/p95/p99. Khi producer nhanh hơn consumer, queue tăng theo phần chênh lệch; backpressure phải giới hạn input hoặc in-flight work trước khi broker, JVM, database hoặc downstream bão hòa.

## 22. Chuẩn bị cho Tuần 12

Tuần sau sẽ đưa metric và failure signal vào vận hành:

- ready, unacked, total, publish/deliver/ack rate;
- consumer, connection, channel và blocked connection;
- disk/memory alarm;
- Prometheus/Grafana;
- structured logging và correlation ID;
- SLI/SLO và alert;
- runbook cho queue buildup, broker unreachable và DLQ spike.

Giữ benchmark report và metric definitions của tuần này để biến chúng thành dashboard và alert có owner.

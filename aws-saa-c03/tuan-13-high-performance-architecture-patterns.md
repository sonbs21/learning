# Tuần 13 — High performance và architecture patterns

> Mục tiêu: tối ưu workload dựa trên bottleneck thực tế, chọn đúng cache/scale/ingestion/processing pattern và hiểu trade-off giữa latency, throughput, cost, consistency và operations.

## 1. Tư duy tối ưu performance

Performance không đồng nghĩa với việc dùng instance lớn nhất hoặc thêm cache vào mọi nơi. Một quyết định tốt phải trả lời:

- User cần latency bao nhiêu?
- Throughput là bao nhiêu request/giây, record/giây hoặc MB/giây?
- Traffic ổn định hay burst?
- Bottleneck nằm ở compute, storage, database, network hay downstream?
- Có chấp nhận xử lý asynchronous không?
- Dữ liệu được phép stale trong bao lâu?
- Cost và operational overhead tăng bao nhiêu?

Quy trình:

1. Xác định SLI/SLO: p50, p95, p99, error rate và throughput.
2. Đo baseline.
3. Tìm bottleneck bằng metric, log và tracing.
4. Đặt giả thuyết cho thay đổi.
5. Benchmark lại ở traffic tương tự.
6. Kiểm tra correctness, cost và failure behavior.
7. Ghi trade-off và rollback plan.

Không dùng cache để che giấu query/database design sai. Không scale compute nếu bottleneck là database lock, connection hoặc network.

## 2. Kết quả cần đạt

- Phân tích performance theo compute, storage, database, network và ingestion.
- Chọn CloudFront, ElastiCache, DAX hoặc API Gateway cache.
- Thiết kế read/write separation và asynchronous processing.
- Hiểu CDN, compression, connection reuse và keep-alive.
- Chọn metric Auto Scaling theo bottleneck.
- Nhận biết EC2 placement, enhanced networking và EBS optimization.
- Chọn Kinesis Data Streams, Firehose hoặc MSK ở mức use case.
- Thiết kế S3 data lake với Glue và Athena.
- Nhận biết AWS Batch và Fargate.
- Giải thích fan-out, event-driven, strangler migration và serverless pattern.
- Hiểu quota, throttling, retry storm và backpressure.
- Tránh tối ưu làm latency tốt hơn nhưng correctness, cost hoặc resilience xấu đi.

## 3. Lịch học gợi ý

| Buổi | Nội dung | Thời lượng |
|---|---|---:|
| 1 | Performance methodology, caching và CDN | 3 giờ |
| 2 | Scaling metric, compute, storage và database | 3 giờ |
| 3 | Streaming, ingestion và data lake | 3 giờ |
| 4 | Architecture patterns và backpressure | 2–3 giờ |
| 5 | Lab benchmark, queue scaling và Athena | 2 giờ |

---

## 4. Các chỉ số performance

### Latency

Latency là thời gian từ khi nhận request đến khi trả response. Đừng chỉ nhìn average; p95 và p99 cho biết nhóm request chậm.

| Chỉ số | Ý nghĩa |
|---|---|
| p50 | request điển hình |
| p95 | 95% request nhanh hơn giá trị này |
| p99 | tail latency |
| Throughput | số lượng công việc mỗi giây |
| Error rate | tỷ lệ request thất bại |
| Queue age | thời gian message lâu nhất đang chờ |

Một cache có thể giảm p50 nhưng không cải thiện p99 nếu cache miss hoặc downstream vẫn chậm.

### Bottleneck

Ví dụ:

    Client → ALB → App → Cache → Database → Third-party
                                  ↑
                             bottleneck

Tăng số app instance không giúp nếu database đã hết connection hoặc IOPS.

### Capacity và headroom

Capacity là lượng tải hệ thống đáp ứng được trong SLO. Headroom là phần dự phòng cho burst và failure.

Không đặt ngưỡng scale chỉ bằng cảm tính. Cần tính startup time, traffic burst, AZ failure và thời gian scale-out.

---

## 5. Caching

Cache lưu dữ liệu gần nơi sử dụng để giảm latency, database load và origin request.

### 5.1. Các tầng cache

    Browser → CloudFront → API Gateway cache
            → ElastiCache/DAX → Database/Object origin

Mỗi tầng có cache key, TTL, invalidation và failure behavior khác nhau.

### 5.2. CloudFront

CloudFront phù hợp:

- static assets;
- image/video;
- public hoặc controlled dynamic content;
- API response có thể cache;
- user phân bố toàn cầu.

Lợi ích:

- edge gần user;
- giảm round trip;
- giảm origin request;
- giảm bandwidth và origin load;
- hỗ trợ TLS/WAF.

Cache miss vẫn gọi origin. Cache response chứa dữ liệu cá nhân sai cache key có thể làm lộ dữ liệu.

### 5.3. ElastiCache

Redis/Memcached phù hợp:

- session;
- product/catalog;
- query result;
- counter/rate limit;
- computed object;
- hot data.

Application phải quyết định cache key, TTL, invalidation và fallback. Cache không nên là source of truth nếu dữ liệu có thể rebuild.

### 5.4. DAX

DAX là cache native cho DynamoDB, phù hợp read-heavy và latency thấp khi eventual consistency chấp nhận.

DAX không thay DynamoDB, không giải quyết partition key sai và không phù hợp strongly consistent read requirement.

### 5.5. API Gateway cache

API Gateway cache phù hợp response API có pattern ổn định. Cache key phải xét path, query/header và authorization scope. Không cache response riêng tư chung cho nhiều user.

### 5.6. Cache-aside

Read flow:

    Application → Cache
          ├── hit  → trả dữ liệu
          └── miss → Database → set cache → trả dữ liệu

Write flow:

    Application → ghi source of truth
                → invalidate/update cache

Ưu điểm là application kiểm soát dữ liệu cần cache. Nhược điểm là stale data, cache miss, stampede, invalidation và cache outage.

### 5.7. TTL và invalidation

TTL thấp giảm stale nhưng tăng database load. TTL cao tăng hit ratio nhưng dữ liệu cũ lâu hơn.

Dùng versioned key hoặc event-driven invalidation nếu dữ liệu cần cập nhật nhanh. Jitter TTL giúp tránh nhiều hot key hết hạn cùng lúc.

### 5.8. Cache stampede

Khi hot key hết hạn, nhiều request cùng miss và gọi database. Giảm bằng:

- request coalescing;
- lock ngắn hạn;
- stale-while-revalidate;
- pre-warm;
- TTL jitter;
- rate limit;
- fallback.

### 5.9. Khi không nên cache

Không nên cache khi:

- dữ liệu thay đổi mỗi request;
- response phụ thuộc user nhưng cache key không có user context;
- chưa có invalidation strategy;
- source query đã đủ nhanh;
- cache cost cao hơn lợi ích.

---

## 6. Read/write separation

Read/write separation tách workload đọc và ghi:

    Write API → primary/writer database
    Read API  → read replica/cache/search index

### Lợi ích

- scale read độc lập;
- giảm tải writer;
- tách reporting;
- đặt read replica gần user;
- query đọc không ảnh hưởng transaction.

### Trade-off

- replica lag;
- eventual consistency;
- read-after-write;
- routing logic;
- failover/promotion;
- thêm schema/index.

Không route read-critical request sang replica nếu user vừa ghi và cần thấy kết quả ngay.

### Read model

    Write model → event/CDC → read model/cache/search
          ↓                         ↓
       transaction              fast query

Pattern này phù hợp read-heavy nhưng chấp nhận eventual consistency và projection complexity.

---

## 7. Asynchronous processing

Nếu request không cần kết quả ngay, dùng queue/event:

    API → accept job → queue → worker → result/status

Use case:

- gửi email;
- tạo report;
- resize ảnh;
- xử lý video;
- batch import;
- notification;
- workflow nhiều bước.

Lợi ích:

- response nhanh;
- hấp thụ burst;
- retry;
- scale worker;
- giảm coupling.

Cần có:

- job status;
- idempotency;
- DLQ;
- retry/backoff;
- timeout;
- observability;
- thông báo khi job hoàn thành.

Asynchronous không phù hợp nếu user bắt buộc cần transaction hoàn tất trước khi nhận response.

---

## 8. CDN, compression và connection reuse

### CDN

CDN đặt content ở edge gần user. Tối ưu CDN bằng:

- cache key nhỏ nhưng đúng;
- TTL phù hợp;
- versioned static asset;
- compression;
- methods tối thiểu;
- origin connection reuse;
- invalidation có kiểm soát.

### Compression

Compression giảm bytes qua network cho JSON, HTML, CSS và JavaScript. Trade-off là CPU compress/decompress.

Không kỳ vọng nén thêm nhiều cho JPEG, PNG hoặc ZIP. Cache cần phân biệt response theo Accept-Encoding khi cần.

### Connection reuse

TCP/TLS handshake có cost. Keep-alive và connection pooling giúp giảm latency/CPU.

Best practice:

- HTTP keep-alive;
- reuse client SDK;
- connection pool có giới hạn;
- timeout rõ;
- Lambda reuse client ngoài handler khi phù hợp;
- xử lý stale connection/reconnect.

Connection reuse không thay idempotency và không được mở pool lớn hơn capacity downstream.

---

## 9. Auto Scaling và metric

### Scale theo CPU

Phù hợp compute-bound workload. Không phù hợp khi CPU thấp nhưng queue/database latency cao.

### Scale theo request count

ALB request count per target phù hợp web/API có request cost tương đối ổn định. Nếu request complexity rất khác nhau, request count không phản ánh load thật.

### Scale theo queue depth

Worker nên scale theo queue depth, age hoặc backlog per worker:

    Backlog tăng → thêm worker
    Backlog giảm → scale in

Backlog per worker thường tốt hơn queue depth tuyệt đối khi số worker thay đổi.

### Custom metric

Dùng khi CPU/request count không thể hiện business load:

- active jobs;
- database connections;
- p95 latency;
- messages per worker;
- queue age;
- concurrent sessions;
- records/second.

Metric phải có unit, aggregation, delay và alarm behavior rõ.

### Warm-up và cooldown

Nếu instance mất 5 phút để ready, policy phải tính warm-up. Scale-out quá chậm làm latency tăng; scale-in quá nhanh gây oscillation.

Cân nhắc:

- scale-out nhanh;
- scale-in chậm;
- minimum capacity;
- headroom cho AZ failure;
- startup time;
- cooldown/warmup.

### Scheduled và predictive scaling

- Scheduled: traffic có lịch rõ.
- Predictive: pattern lịch sử dự đoán được.
- Target tracking: giữ metric gần target.
- Step scaling: scale khác nhau theo mức alarm.

---

## 10. Compute, placement và network performance

### EC2 instance selection

Chọn family theo bottleneck:

- compute optimized cho CPU;
- memory optimized cho working set;
- storage optimized cho local I/O;
- network optimized cho throughput/packet rate;
- accelerated cho GPU/ML.

Cùng số vCPU không đồng nghĩa performance giống nhau. Benchmark workload thật.

### Placement groups

| Type | Dùng khi | Đánh đổi |
|---|---|---|
| Cluster | latency thấp/throughput cao trong AZ | failure domain tập trung |
| Spread | ít instance quan trọng cần tách hardware | giới hạn số instance |
| Partition | distributed system cần failure domain | app phải hiểu partition |

Placement group không thay Multi-AZ hoặc application replication.

### Enhanced networking

Enhanced networking tăng packets per second và giảm CPU/network latency khi instance, OS và driver hỗ trợ. Kiểm tra instance type, AMI/driver, route, SG/NACL và cross-AZ path.

### EBS optimization

EBS-optimized instance dành bandwidth riêng cho EBS. Nó hữu ích với database/storage-heavy workload nhưng không thay việc chọn volume type, IOPS, throughput và queue depth đúng.

---

## 11. Kinesis, Firehose và MSK

### Kinesis Data Streams

Kinesis Data Streams phù hợp real-time ingestion:

- clickstream;
- telemetry;
- IoT;
- logs;
- analytics;
- event processing.

Cần thiết kế:

- partition key phân phối đều;
- shard/throughput;
- ordering trong shard;
- retention;
- consumer checkpoint;
- retry;
- hot shard.

Partition key lệch có thể làm một shard quá tải dù tổng stream capacity còn dư.

### Kinesis Data Firehose

Firehose là delivery stream managed, buffer và đẩy data tới S3, Redshift, OpenSearch hoặc target hỗ trợ.

Chọn Firehose khi:

- cần đưa dữ liệu vào storage/analytics;
- không muốn tự quản consumer/shard;
- chấp nhận near-real-time;
- cần buffering và batch delivery;
- muốn transform nhẹ.

Firehose không phải lựa chọn cho consumer cần đọc từng record với control cao.

### Amazon MSK

MSK phù hợp khi:

- đã dùng Apache Kafka;
- cần Kafka API/ecosystem;
- cần topic/partition/consumer group;
- migration muốn ít rewrite;
- cần Kafka-specific stream processing.

Kinesis thường đơn giản hơn cho AWS-native stream; MSK phù hợp Kafka compatibility.

| Tiêu chí | Kinesis Streams | Firehose | MSK |
|---|---|---|---|
| Mục tiêu | real-time stream | delivery | managed Kafka |
| Consumer control | cao | thấp hơn | Kafka model |
| Partition/shard | có | AWS quản lý | Kafka partition |
| Use case | real-time processing | data lake ingestion | Kafka migration/ecosystem |

---

## 12. Glue, Athena và S3 data lake

### S3 data lake

S3 phù hợp storage layer vì scale lớn, nhiều format, lifecycle, tách storage/compute và hỗ trợ nhiều engine.

Tổ chức phổ biến:

    S3 data lake
    ├── raw/
    ├── processed/
    └── curated/

Raw zone nên immutable/append-only nếu cần audit hoặc reprocessing.

### Glue

Glue cung cấp Data Catalog, crawler và ETL. Catalog mô tả schema/table để Athena và engine khác query dữ liệu S3.

Cần cân nhắc schema drift, partition, job cost, retry, data quality và security.

### Athena

Athena query trực tiếp dữ liệu S3 bằng SQL. Tối ưu bằng:

- Parquet/ORC;
- partition theo field thường query;
- tránh SELECT toàn bộ cột;
- lọc partition;
- compression;
- compaction small files;
- catalog schema đúng.

Athena tính theo dữ liệu scanned theo pricing model. Partition và columnar format cải thiện cả latency lẫn cost.

### Small files problem

Hàng triệu file nhỏ làm tăng metadata overhead và query latency. Dùng Firehose, compaction job hoặc ETL để gom thành file có kích thước hợp lý.

### Data lake security

- bucket private;
- IAM/Lake Formation theo requirement;
- KMS encryption;
- classification/masking;
- CloudTrail audit;
- lifecycle;
- access theo raw/curated zone.

---

## 13. Batch processing

### AWS Batch

AWS Batch điều phối batch job, queue, compute environment, job definition và dependency.

Phù hợp:

- nhiều job độc lập;
- job chạy lâu;
- queue/prioritization;
- retry;
- EC2/Spot/Fargate compute;
- resource requirement khác nhau.

### Fargate

Fargate chạy container mà không quản lý EC2. Phù hợp container task/service cần runtime linh hoạt hơn Lambda nhưng không muốn quản server.

| Requirement | Lambda | Fargate | AWS Batch |
|---|---|---|---|
| Function ngắn/event | tốt | có thể quá nặng | không cần |
| Container runtime | hạn chế hơn | tốt | tốt |
| Job dài/parallel | hạn chế | phù hợp | rất phù hợp |
| Queue/prioritization | tự xây | tự xây | có pattern sẵn |

---

## 14. Architecture patterns

### Fan-out

Một event gửi tới nhiều consumer:

    Producer → SNS/EventBridge
                  ├── Billing
                  ├── Shipping
                  └── Analytics

Nếu mỗi consumer cần retry/backpressure riêng, đặt một SQS queue phía sau mỗi nhánh.

### Event-driven

Service phát event khi state thay đổi. Consumer phản ứng mà không cần publisher gọi trực tiếp.

Ưu điểm: coupling thấp, thêm consumer dễ, scale độc lập.

Trade-off: eventual consistency, schema evolution, duplicate/replay, trace khó và transaction cross-service phức tạp.

### Strangler migration

Thay dần legacy system:

1. đặt facade/API trước hệ thống cũ;
2. chọn capability nhỏ;
3. route capability mới tới service mới;
4. đồng bộ/di chuyển data;
5. quan sát và rollback;
6. mở rộng từng bước;
7. retire legacy.

Tránh dual-write không kiểm soát và phải xác định rõ ownership dữ liệu.

### Serverless

API Gateway → Lambda → managed service phù hợp workload stateless, bursty và muốn giảm server operations.

Cần kiểm tra cold start, concurrency, timeout, connection tới downstream, VPC path, retry và cost per request.

### CQRS/read model

Tách command/write model và query/read model. Event/CDC cập nhật read model hoặc cache. Phù hợp read-heavy nhưng chấp nhận eventual consistency.

### Queue-based load leveling

    API → Queue → worker pool → database/storage

Queue bảo vệ downstream khỏi burst. Worker scale theo backlog/age. Cần DLQ, retry, idempotency và queue-age alarm.

---

## 15. Quotas, throttling và backpressure

### Quota

Service có quota về API request, concurrency, connection, shard, ENI, storage và resource count. Kiểm tra Service Quotas trước production.

### Throttling

Khi request vượt capacity/quota:

- backoff;
- jitter;
- client rate limit;
- queue/buffer;
- batch;
- giảm concurrency;
- request quota increase;
- partition workload.

Retry ngay lập tức không phải cách xử lý throttling.

### Retry storm

Dependency lỗi và mọi client retry cùng lúc sẽ làm traffic tăng hơn. Giảm bằng:

- exponential backoff + jitter;
- circuit breaker;
- bounded retry;
- queue;
- fallback;
- token bucket/rate limit;
- load shedding;
- idempotency.

### Backpressure

Backpressure truyền tín hiệu từ consumer chậm về upstream:

- queue backlog tăng;
- worker giảm polling;
- producer bị rate limit;
- API trả 429 hoặc 202;
- batch size giảm;
- request không critical bị load shed.

Không có backpressure, hệ thống có thể sập theo dây chuyền.

### Little’s Law ở mức trực giác

    Số lượng đang xử lý ≈ throughput × thời gian xử lý

Nếu nhận 100 request/giây và mỗi request mất 2 giây, cần khoảng 200 request slots/concurrency trong trạng thái ổn định, chưa tính burst và failure.

---

## 16. Lab 1 — CloudFront trước S3/ALB

1. Tạo static object hoặc web origin.
2. Đặt CloudFront trước origin.
3. Cấu hình behavior/cache policy.
4. Đo request đầu tiên: cache miss.
5. Gửi lại: cache hit.
6. Thay đổi TTL hoặc dùng versioned asset.
7. So sánh origin request count trước/sau.
8. Ghi p50/p95 latency, hit ratio và payload size.

Cần kiểm tra cache key và dữ liệu cá nhân không bị cache nhầm.

---

## 17. Lab 2 — Scale worker theo queue

1. Tạo SQS queue.
2. Tạo worker có processing duration cố định.
3. Gửi burst message.
4. Theo dõi queue depth và age.
5. Tạo policy theo backlog hoặc backlog per worker.
6. Quan sát thời gian drain.
7. So sánh với scale theo CPU.
8. Test poison message/DLQ.
9. Điều chỉnh batch size và visibility timeout.

Mục tiêu: thấy CPU worker có thể thấp nhưng queue vẫn tăng; queue metric mới phản ánh bottleneck.

---

## 18. Lab 3 — Kinesis hoặc Firehose

Nếu có ngân sách:

- tạo Kinesis Data Stream hoặc Firehose;
- gửi event theo partition key;
- quan sát phân phối shard;
- ghi dữ liệu vào S3;
- catalog bằng Glue;
- query bằng Athena;
- theo dõi lag/throttling.

Nếu không có ngân sách, mô phỏng bằng file batch vào S3 và tập trung vào partition, Parquet, compression và Athena scan cost.

---

## 19. Lab 4 — Athena data lake

1. Tạo raw/processed/curated prefix.
2. Đặt sample CSV và Parquet.
3. Tạo Glue catalog/table.
4. Query bằng Athena.
5. So sánh SELECT toàn bộ cột với projection.
6. So sánh unpartitioned với partitioned data.
7. Ghi số dữ liệu scanned.
8. Đề xuất compaction, lifecycle và security.

---

## 20. Lab 5 — Benchmark cache

1. Chọn API đọc product/catalog.
2. Đo latency khi chưa cache.
3. Thêm ElastiCache hoặc API cache.
4. Đo hit/miss và database read load.
5. Test stale data/invalidation.
6. Mô phỏng cache unavailable.
7. Kiểm tra fallback.
8. So sánh latency, cost, complexity và correctness.

Không kết luận cache tốt chỉ vì latency giảm.

---

## 21. Bẫy đề thi SAA-C03

- Không tối ưu trước khi biết bottleneck.
- Cache không sửa query/index/database design sai.
- Cache response riêng tư sai key có thể làm lộ dữ liệu.
- CloudFront cache miss vẫn gọi origin.
- ElastiCache/DAX có stale, eviction và failure behavior.
- CPU thấp không có nghĩa worker đủ capacity; queue age có thể tăng.
- Scale theo metric không liên quan không sửa bottleneck.
- Read replica giúp read scaling nhưng có lag.
- Async processing giảm response latency nhưng tạo eventual consistency.
- Cluster placement group không thay Multi-AZ.
- Enhanced networking cần instance/driver/OS support.
- EBS optimization không thay chọn volume IOPS/throughput đúng.
- Kinesis partition key lệch gây hot shard.
- Firehose phù hợp delivery, không phải consumer stream control đầy đủ.
- Athena scan nhiều dữ liệu làm tăng cost/latency.
- MSK phù hợp Kafka compatibility, không mặc định tốt hơn Kinesis.
- AWS Batch phù hợp batch queue/orchestration; Fargate phù hợp container task.
- Event-driven cần schema evolution, idempotency và replay plan.
- Retry tức thì gây retry storm.
- Throttling không nên xử lý bằng retry vô hạn.
- Backpressure phải truyền về upstream.
- Cross-AZ/cross-Region traffic có latency và cost.
- Performance tốt hơn nhưng cost/correctness/resilience xấu hơn chưa chắc là đáp án đúng.

---

## 22. Câu hỏi tự luyện

### Câu 1

Static asset được user toàn cầu truy cập nhiều, origin quá tải. Giải pháp đầu tiên?

**Đáp án:** CloudFront với TTL/versioned asset và origin access control phù hợp.

### Câu 2

Worker CPU thấp nhưng queue age tăng. Scale theo gì?

**Đáp án:** queue depth, queue age hoặc backlog per worker; không chỉ CPU.

### Câu 3

Database read-heavy, query lặp lại và có thể stale vài phút. Chọn gì?

**Đáp án:** ElastiCache cache-aside; nếu dữ liệu là DynamoDB, xem DAX.

### Câu 4

Cần ingest telemetry real-time với nhiều consumer checkpoint riêng. Chọn gì?

**Đáp án:** Kinesis Data Streams hoặc MSK nếu Kafka compatibility là requirement.

### Câu 5

Chỉ cần đưa log liên tục vào S3/Redshift, không cần tự quản shard. Chọn gì?

**Đáp án:** Kinesis Data Firehose.

### Câu 6

Athena query chậm và scan quá nhiều dữ liệu. Cải thiện gì?

**Đáp án:** partition, Parquet/ORC, compression, projection, filter partition và compaction small files.

### Câu 7

Cần hàng triệu batch job có queue, retry và priority. Chọn gì?

**Đáp án:** AWS Batch, có thể dùng EC2/Spot/Fargate compute environment.

### Câu 8

Muốn thay từng phần legacy system thay vì rewrite một lần. Pattern nào?

**Đáp án:** strangler migration với facade và route theo capability.

### Câu 9

Downstream lỗi, tất cả client retry ngay lập tức. Vấn đề và giải pháp?

**Đáp án:** retry storm; dùng backoff, jitter, circuit breaker, queue, rate limit và bounded retry.

### Câu 10

Cần latency thấp giữa instance tightly coupled trong cùng AZ. Chọn gì?

**Đáp án:** cluster placement group, sau khi chấp nhận failure-domain trade-off.

### Câu 11

Vì sao scale theo CPU không phù hợp worker xử lý queue?

**Đáp án:** worker có thể chờ I/O nên CPU thấp trong khi backlog tăng; queue metrics phản ánh load tốt hơn.

### Câu 12

Cache giảm latency nhưng dữ liệu trả về đôi khi cũ hơn business cho phép. Cần làm gì?

**Đáp án:** giảm TTL, invalidate sau write, dùng versioned key/event invalidation hoặc bỏ cache cho request critical.

---

## 23. Checklist hoàn thành tuần

### Kiến thức

- [ ] Đo được latency p50/p95/p99 và throughput.
- [ ] Biết xác định bottleneck trước khi tối ưu.
- [ ] Phân biệt CloudFront, ElastiCache, DAX và API Gateway cache.
- [ ] Thiết kế cache-aside, TTL và invalidation.
- [ ] Hiểu read/write separation và eventual consistency.
- [ ] Hiểu asynchronous processing và queue-based load leveling.
- [ ] Chọn metric Auto Scaling theo bottleneck.
- [ ] Nhận biết placement, enhanced networking và EBS optimization.
- [ ] Phân biệt Kinesis Data Streams, Firehose và MSK.
- [ ] Thiết kế S3 data lake với Glue/Athena.
- [ ] Nhận biết AWS Batch và Fargate.
- [ ] Giải thích fan-out, event-driven, strangler và serverless pattern.
- [ ] Hiểu quota, throttling, retry storm và backpressure.

### Thực hành

- [ ] Đã đặt CloudFront trước S3/ALB.
- [ ] Đã quan sát cache hit/miss và origin load.
- [ ] Đã scale worker theo queue depth/age.
- [ ] Đã kiểm tra batch, visibility và DLQ.
- [ ] Đã tạo ingestion flow hoặc mô phỏng Kinesis/Firehose.
- [ ] Đã query dữ liệu S3 bằng Athena.
- [ ] Đã so sánh partitioned/columnar với dữ liệu thô.
- [ ] Đã đo latency trước và sau cache.
- [ ] Đã ghi trade-off performance, cost, correctness và operations.

Bạn nắm chắc Tuần 13 khi có thể nhìn một bottleneck và giải thích nên cache, scale, batch, async, partition, replicate hay đổi architecture pattern nào; đồng thời nêu được tác động tới cost, consistency và failure behavior.

---

## 24. Tài liệu tham khảo

- [AWS Well-Architected Performance Efficiency Pillar](https://docs.aws.amazon.com/wellarchitected/latest/performance-efficiency-pillar/welcome.html) — performance principles.
- [Amazon CloudFront Developer Guide](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/Introduction.html) — CDN/cache.
- [Amazon ElastiCache](https://docs.aws.amazon.com/AmazonElastiCache/latest/dg/WhatIs.html) — application cache.
- [Amazon DynamoDB Accelerator](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/DAX.html) — DAX.
- [Amazon EC2 Auto Scaling](https://docs.aws.amazon.com/autoscaling/ec2/userguide/what-is-amazon-ec2-auto-scaling.html) — scaling policies.
- [Amazon Kinesis Data Streams](https://docs.aws.amazon.com/streams/latest/dev/introduction.html) — real-time streams.
- [Amazon Data Firehose](https://docs.aws.amazon.com/firehose/latest/dev/what-is-this-service.html) — managed delivery.
- [Amazon MSK](https://docs.aws.amazon.com/msk/latest/developerguide/what-is-msk.html) — managed Kafka.
- [AWS Glue](https://docs.aws.amazon.com/glue/latest/dg/what-is-glue.html) — catalog/ETL.
- [Amazon Athena](https://docs.aws.amazon.com/athena/latest/ug/what-is.html) — SQL trên S3.
- [AWS Batch](https://docs.aws.amazon.com/batch/latest/userguide/what-is-batch.html) — batch orchestration.
- [AWS Fargate](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/AWS_Fargate.html) — serverless containers.


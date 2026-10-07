# Tuần 15: Performance và observability

## 1. Mục tiêu

Sau bài này, bạn có thể:

- Đo Redis latency p50/p95/p99 và throughput.
- Theo dõi hit ratio, miss ratio, memory, fragmentation và eviction.
- Phát hiện big key, hot key, blocked client và connection pool exhaustion.
- Dùng INFO, Slowlog, Latency Doctor và keyspace notifications.
- Tích hợp Micrometer/OpenTelemetry.
- Xây dashboard và runbook điều tra incident.

---

## 2. Vì sao cần observability?

Redis nằm giữa application và database:

~~~text
Application → Redis → Database fallback
~~~

Khi API chậm, nguyên nhân có thể là:

- Redis command latency.
- Network latency.
- Connection pool cạn.
- Big key hoặc hot key.
- Serialization chậm.
- Eviction làm cache miss tăng.
- Database fallback tăng.
- Redis failover hoặc persistence rewrite.

Vì vậy phải quan sát đồng thời application, Redis và database.

---

## 3. Bộ metrics tối thiểu

### Application

- Request rate.
- API p50/p95/p99.
- Cache hit/miss.
- Redis timeout/error.
- Serialization latency.
- Database fallback rate.
- Thread/connection pool.

### Redis

- Command latency.
- Commands per second.
- Connected/blocked clients.
- Used memory/RSS.
- Fragmentation.
- Evicted/expired keys.
- Keyspace hits/misses.
- Slowlog.
- Replication lag.
- AOF/RDB status.

### Database

- Query rate.
- p95/p99 latency.
- Connection pool.
- Lock wait.
- CPU/IO.

---

## 4. Latency

Không chỉ xem average:

~~~text
p50 = 2 ms
p95 = 8 ms
p99 = 250 ms
~~~

Cần tách:

- API end-to-end latency.
- Redis round-trip latency.
- Redis server command time.
- Network time.
- Serialization time.
- Database fallback time.

Ví dụ:

~~~text
API p99 = 300 ms
Redis GET p99 = 3 ms
Database fallback p99 = 280 ms
~~~

Root cause có thể là miss/database, không phải Redis command.

---

## 5. Throughput và ops/sec

Phân biệt:

- HTTP request rate.
- Redis ops/sec.
- Database query rate.

Một HTTP request có thể tạo nhiều command:

~~~text
GET cache
HGET session
SET refreshed value
~~~

Theo dõi:

~~~text
instantaneous_ops_per_sec
total_commands_processed
total_connections_received
~~~

Ops/sec cao chưa chắc xấu; cần xem command mix, latency, payload và CPU.

---

## 6. Hit ratio và miss ratio

Công thức:

~~~text
hit ratio = hits / (hits + misses)
miss ratio = misses / (hits + misses)
~~~

Theo dõi theo cache name nếu có thể:

~~~text
products: hit 98%
search-results: hit 65%
permissions: hit 92%
~~~

Liên kết miss với:

- Database query rate.
- Database latency.
- Eviction.
- Expiration.
- Deploy/key format.
- Restart/failover.
- Key distribution.

Hit ratio cao không chứng minh dữ liệu fresh hoặc hệ thống an toàn.

---

## 7. Memory observability

Commands:

~~~text
INFO memory
MEMORY STATS
MEMORY USAGE some:key
~~~

Theo dõi:

- used_memory.
- used_memory_rss.
- used_memory_peak.
- maxmemory.
- mem_fragmentation_ratio.
- Client buffers.
- Replication/AOF buffers.
- lazyfree pending objects.

Cảnh báo:

- Used memory gần maxmemory.
- RSS cao hơn nhiều used memory.
- Fragmentation tăng.
- Memory peak sát giới hạn.
- Dataset tăng nhanh.
- Rewrite/fork làm memory spike.

---

## 8. Eviction và expiration

Theo dõi:

~~~text
evicted_keys
expired_keys
keyspace_hits
keyspace_misses
~~~

Ý nghĩa:

- expired_keys tăng: key tự hết TTL.
- evicted_keys tăng: memory pressure hoặc policy xóa key.
- miss tăng sau eviction: working set bị mất.
- miss tăng không có eviction: TTL ngắn, key format đổi hoặc traffic mới.

Eviction có thể bình thường với cache thuần túy, nhưng nguy hiểm với session, idempotency hoặc token blacklist.

---

## 9. Connected và blocked clients

Commands:

~~~text
INFO clients
CLIENT LIST
~~~

Theo dõi:

- connected_clients.
- blocked_clients.
- maxclients.
- Input/output buffer.
- Pool active/idle/exhausted.
- Reconnect count.

Connected tăng có thể do connection leak, deploy, reconnect storm hoặc subscriber lâu dài.

Blocked client có thể bình thường nếu dùng BLPOP/XREAD BLOCK. Nhưng cũng có thể do command nặng, script dài, transaction hoặc pool cạn. So sánh với baseline expected.

---

## 10. INFO và commandstats

INFO sections:

~~~text
INFO server
INFO clients
INFO memory
INFO persistence
INFO stats
INFO replication
INFO cpu
INFO commandstats
INFO keyspace
~~~

Commandstats giúp phát hiện:

- HGETALL tăng bất thường.
- SMEMBERS trên Set lớn.
- LRANGE toàn List.
- KEYS xuất hiện.
- Lua CPU cao.
- DEL value lớn.
- XREADGROUP pending/retry tăng.

Không poll INFO quá dày từ mọi instance. Dùng exporter hoặc sampling.

---

## 11. Slowlog

Commands:

~~~text
SLOWLOG GET 20
SLOWLOG LEN
SLOWLOG RESET
~~~

Cấu hình ngưỡng bằng microseconds:

~~~text
CONFIG GET slowlog-log-slower-than
CONFIG SET slowlog-log-slower-than 10000
~~~

Slowlog cho biết server-side command execution chậm, nhưng không bao gồm toàn bộ network và pool wait.

Điều tra:

1. Xác định command/key pattern.
2. Kiểm tra key size.
3. Kiểm tra caller.
4. Giới hạn range/payload.
5. Đo lại.

Cẩn thận vì slowlog argument có thể chứa dữ liệu nhạy cảm.

---

## 12. Latency Doctor

Commands:

~~~text
LATENCY DOCTOR
LATENCY LATEST
LATENCY HISTORY event
~~~

Có thể phát hiện event loop delay, fork/RDB latency, AOF fsync latency và spike.

Latency Doctor không thay thế tracing hoặc system metrics. Dùng cùng INFO, Slowlog, CPU, disk và application spans.

---

## 13. Big key và hot key

### Big key

Value hoặc collection quá lớn:

- JSON vài MB.
- Hash hàng triệu field.
- List/Set cực lớn.
- Stream không trim.

Rủi ro:

- GET/serialization chậm.
- Memory spike.
- Replication/AOF nặng.
- DEL tốn CPU.
- Response lớn.

Đo:

~~~text
MEMORY USAGE key
HLEN hash
LLEN list
SCARD set
XLEN stream
~~~

### Hot key

Một key nhận traffic lớn bất thường.

Rủi ro:

- Traffic tập trung một node.
- Hết hạn gây stampede.
- Serialization lặp lại.
- Hot slot trong Cluster.

Giảm bằng L1 cache, single-flight, refresh-ahead, TTL jitter, chia nhỏ value hoặc giới hạn request.

Big key là vấn đề kích thước; hot key là vấn đề traffic.

---

## 14. Keyspace notifications

Keyspace notifications phát event khi key thay đổi hoặc hết hạn, tùy cấu hình.

Có thể dùng cho:

- Quan sát expired key.
- Debug TTL.
- Cleanup phụ.
- Lab kiểm tra expiration.

Hạn chế:

- Có overhead.
- Subscriber offline có thể mất event.
- Không durable.
- Không thay thế outbox/Stream.
- Không nên dùng làm source of truth business.

Dùng cho observability hoặc best-effort coordination, không dùng cho payment/order event quan trọng.

---

## 15. Micrometer và OpenTelemetry

Metrics nên có tag:

~~~text
cache_name
operation
outcome
command_group
instance
environment
~~~

Không tag bằng raw user ID hoặc raw key vì cardinality tăng vô hạn.

Ví dụ:

~~~text
cache_get_total{cache="products",outcome="hit"}
cache_get_total{cache="products",outcome="miss"}
redis_errors_total{type="timeout"}
~~~

Trace một request:

~~~text
HTTP request
  ├─ Redis GET
  ├─ Redis SET
  └─ Database query nếu miss
~~~

Không ghi token, session ID hoặc payment data vào span.

---

## 16. Dashboard tối thiểu

### Cache

- Hit/miss ratio.
- Miss theo cache name.
- Rebuild count.
- Database fallback.

### Redis

- Command p50/p95/p99.
- Commands/sec.
- Memory/RSS/fragmentation.
- Evicted/expired keys.
- Connected/blocked clients.
- Slowlog.
- Timeout/error.

### HA và streams

- Replication lag.
- Sentinel failover.
- Cluster slot health.
- Stream length.
- Pending count.
- Consumer lag.

---

## 17. Incident drill: API chậm

Điều tra:

1. API p95/p99 có tăng không?
2. Redis client latency có tăng không?
3. Redis server command latency có tăng không?
4. Pool có cạn không?
5. Connected/blocked clients có bất thường không?
6. Hit ratio có giảm không?
7. Eviction/expiration có tăng không?
8. Có hot key/big key không?
9. Database fallback/query rate có tăng không?
10. Database p99 có tăng không?
11. Có deploy, failover hoặc persistence rewrite không?

Không tăng Redis size trước khi xác định bottleneck.

---

## 18. Incident drill: memory tăng

1. Kiểm tra used_memory, RSS, fragmentation.
2. Kiểm tra maxmemory/policy.
3. Kiểm tra evicted/expired.
4. Tìm big key.
5. Tìm key không TTL.
6. Kiểm tra Stream/List không trim.
7. Kiểm tra AOF/RDB rewrite.
8. Kiểm tra replication/client buffer.
9. Đối chiếu deploy/traffic.
10. Cleanup đúng namespace.
11. Scale hoặc đổi policy có đánh giá.
12. Không flush toàn Redis mù quáng.

---

## 19. Incident drill: hit ratio giảm

Nguyên nhân có thể:

- Đổi key prefix.
- Đổi serializer.
- TTL giảm.
- Eviction tăng.
- Redis restart/cold start.
- Query pattern mới.
- Invalidation quá rộng.
- Hot key bị mất.
- Negative cache không hoạt động.

Phân tích:

~~~text
Hit ratio giảm
    ↓
Miss tăng?
    ↓
Database query tăng?
    ↓
Database latency tăng?
    ↓
Eviction/expiry/deploy correlation?
~~~

Không tăng TTL nếu freshness requirement không cho phép.

---

## 20. Bài thực hành latency

Tạo workload GET/SET/HGET/LRANGE với nhiều payload. Đo:

- p50/p95/p99.
- Ops/sec.
- CPU.
- Network.
- Payload size.
- Slowlog.

So sánh GET nhỏ, JSON lớn, HGETALL Hash lớn và LRANGE toàn List.

---

## 21. Bài thực hành memory và eviction

Tạo:

- Key có TTL.
- Key không TTL.
- Key nóng/lạnh.
- Big key.
- Stream cần trim.

Thử:

~~~text
noeviction
allkeys-lru
allkeys-lfu
volatile-lru
volatile-ttl
~~~

Ghi lại key bị xóa, write error, hit/miss, fallback và expired/evicted count.

---

## 22. Bài thực hành persistence và tracing

Thử RDB và AOF trong lab:

- Ghi dataset.
- Restart.
- Đo restore time.
- Kiểm tra file size.
- So sánh write loss window.
- Theo dõi rewrite/fork.

Tạo endpoint product và trace hai flow:

~~~text
Cache hit:
HTTP → Redis GET → response

Cache miss:
HTTP → Redis GET → Database → Redis SET → response
~~~

Đảm bảo trace không lộ secret.

---

## 23. Câu hỏi tự kiểm tra

1. Vì sao average latency không đủ?
2. Request rate khác Redis ops/sec thế nào?
3. Hit ratio được tính ra sao?
4. Evicted và expired khác nhau thế nào?
5. Big key khác hot key thế nào?
6. Connected clients tăng có thể do nguyên nhân gì?
7. Blocked clients luôn là lỗi không?
8. Slowlog không bao gồm loại latency nào?
9. Latency Doctor có thay tracing không?
10. Keyspace notifications có durable không?
11. Vì sao raw key không nên làm metric tag?
12. Incident app chậm nên kiểm tra các lớp nào?
13. Vì sao không flush Redis khi memory tăng?
14. Cần dashboard tối thiểu những nhóm metrics nào?

---

## 24. Tiêu chí hoàn thành

Bạn hoàn thành Tuần 15 khi có thể:

- Đo p50/p95/p99 và ops/sec.
- Theo dõi hit/miss, memory, RSS và fragmentation.
- Phát hiện eviction, expiration, big key và hot key.
- Theo dõi clients, blocked clients và pool.
- Dùng INFO, Slowlog và Latency Doctor.
- Hiểu keyspace notifications và giới hạn.
- Tích hợp Micrometer/OpenTelemetry.
- Xây dashboard Redis tối thiểu.
- Điều tra incident API chậm, memory tăng và hit ratio giảm.
- Viết runbook có evidence, mitigation và rollback.
- Chạy lab performance, eviction, persistence và tracing.

Kết luận:

> Performance tuning Redis phải bắt đầu từ số liệu: latency, throughput, hit/miss, memory, eviction, clients, persistence và database fallback. Observability tốt nối được triệu chứng ở application với nguyên nhân ở Redis và database, giúp tối ưu đúng bottleneck thay vì chỉ tăng tài nguyên mù quáng.


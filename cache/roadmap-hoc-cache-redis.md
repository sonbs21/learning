# Roadmap học Cache và Redis cho Backend trong 16 tuần

Lộ trình này dành cho người muốn dùng cache và Redis trong hệ thống backend Java Spring Boot. Trọng tâm là tư duy caching, Redis data structures, consistency, hiệu năng, high availability và cách vận hành an toàn.

Thời lượng đề xuất: **10–14 giờ/tuần** trong 16 tuần.

Redis không chỉ là nơi lưu key/value. Redis cung cấp strings, hashes, lists, sets, sorted sets, streams, transactions, scripting, Pub/Sub, persistence, replication, Sentinel và Cluster. [Redis data types](https://redis.io/docs/latest/develop/data-types/)

## Điều kiện đầu vào

- Java/Spring Boot cơ bản.
- REST API và HTTP.
- SQL và transaction cơ bản.
- Docker, Git và Linux command line.
- Biết JSON, serialization và logging.
- Biết đo latency ở mức cơ bản.

## Mục tiêu cuối lộ trình

Sau 16 tuần, bạn có thể:

- Hiểu cache là gì và khi nào không nên dùng cache.
- Chọn cache-aside, write-through, write-behind hoặc refresh-ahead.
- Thiết kế key, namespace, TTL và serialization format.
- Dùng Redis strings, hashes, lists, sets, sorted sets và streams.
- Tích hợp Spring Cache, RedisCacheManager và RedisTemplate.
- Xử lý cache miss, stampede, penetration, avalanche và stale data.
- Dùng Redis cho session, rate limit, lock, leaderboard và idempotency.
- Phân biệt Pub/Sub với Redis Streams.
- Cấu hình persistence, eviction, replication, Sentinel và Cluster.
- Đo hit ratio, miss ratio, latency, memory và command throughput.
- Test cache integration, failure, serialization và concurrency.
- Xây một project Spring Boot production-like có cache strategy và runbook.

## Môi trường lab

~~~yaml
services:
  redis:
    image: redis:latest
    ports:
      - "6379:6379"
  redis-insight:
    image: redis/redisinsight:latest
    ports:
      - "5540:5540"
~~~

Trong project thật nên pin version cụ thể. Không dùng Redis như database duy nhất nếu chưa có persistence, backup và recovery plan.

---

# Giai đoạn 1 — Tư duy caching

## Tuần 1: Vì sao cần cache

### Học

- Latency của database, network và serialization.
- Cache hit, cache miss.
- Hit ratio và miss ratio.
- Read-heavy và write-heavy workload.
- Hot key.
- Working set.
- Cacheability.
- Freshness và staleness.
- Cache capacity.
- Cache-aside.
- Source of truth.

### Bài tập

Chọn một endpoint đọc sản phẩm và vẽ hai flow:

~~~text
Request → Database → Response

Request → Cache hit → Response
             ↓ miss
          Database → Cache → Response
~~~

Đo latency trước và sau cache bằng cùng một workload.

### Tiêu chí hoàn thành

- Biết cache giảm latency nhưng không làm database nhất quán hơn.
- Biết cache miss vẫn phải trả giá bằng database query.
- Biết một cache có hit ratio cao nhưng vẫn có thể làm hệ thống lỗi vì stale data.

## Tuần 2: Cache strategy và consistency

### Học

- Cache-aside/lazy loading.
- Read-through.
- Write-through.
- Write-behind/write-back.
- Refresh-ahead.
- Cache invalidation.
- TTL-based freshness.
- Event-based invalidation.
- Versioned key.
- Strong, eventual và causal consistency ở mức khái niệm.

### So sánh

| Strategy | Ưu điểm | Rủi ro |
|---|---|---|
| Cache-aside | Dễ áp dụng, kiểm soát rõ | Cache miss và stale data |
| Write-through | Cache luôn được cập nhật khi ghi | Tăng latency write |
| Write-behind | Write nhanh | Rủi ro mất dữ liệu, phức tạp |
| Refresh-ahead | Giảm miss cho key nóng | Tốn tài nguyên refresh |

### Bài tập

Viết quyết định caching cho:

- Product detail.
- User session.
- Inventory.
- Search result.
- Permission.

Với mỗi loại ghi rõ source of truth, TTL, invalidation và chấp nhận stale bao lâu.

---

# Giai đoạn 2 — Redis fundamentals

## Tuần 3: Redis command, key và TTL

### Học

- Redis server và client.
- RESP ở mức nhận biết.
- SET, GET, DEL.
- MGET, MSET.
- EXISTS, TYPE.
- EXPIRE, TTL, PERSIST.
- SET với NX, XX, EX, PX.
- INCR, DECR.
- Atomic command.
- Database index trong Redis.

### Thực hành

~~~text
SET app:product:123 "{...}" EX 300
GET app:product:123
TTL app:product:123
SET lock:order:123 token NX PX 5000
INCR ratelimit:user:123
~~~

### Quy tắc key

- Dùng namespace rõ ràng: app:environment:resource:id.
- Không dùng key không có TTL cho dữ liệu cache.
- Key phải predictable và không chứa dữ liệu nhạy cảm.
- Không dùng KEYS trong production.
- Dùng SCAN khi cần quét keyspace.

## Tuần 4: Redis data structures

### Học và thực hành

| Structure | Use case |
|---|---|
| String | Cache object, counter, token |
| Hash | Session, profile, object fields |
| List | Feed, queue đơn giản |
| Set | Unique membership, tags |
| Sorted set | Leaderboard, delay queue |
| Stream | Durable event stream |
| HyperLogLog | Approximate unique count |
| Bitmap | Flag theo user hoặc ngày |

Bài tập:

- Lưu product cache bằng String.
- Lưu session bằng Hash.
- Tạo unique visitors bằng Set.
- Tạo leaderboard bằng Sorted set.
- Tạo activity feed bằng List.
- Đếm page views gần đúng bằng HyperLogLog.

### Tiêu chí hoàn thành

- Chọn được structure dựa trên thao tác cần thực hiện.
- Biết lệnh Redis trên một instance có tính atomic ở cấp command.
- Biết workflow nhiều command không tự động atomic.

---

# Giai đoạn 3 — Spring Boot và Redis

## Tuần 5: Spring Cache abstraction

### Học

- EnableCaching.
- Cacheable.
- CachePut.
- CacheEvict.
- Caching.
- CacheManager.
- CacheResolver.
- Condition và unless.
- Cache key.
- Cache null value.
- Self-invocation limitation.
- Proxy-based caching.

Spring Boot tự cấu hình cache khi bật caching support; Redis có thể được dùng làm provider thông qua RedisCacheManager. [Spring Boot caching](https://docs.spring.io/spring-boot/reference/io/caching.html)

### Thực hành

~~~java
@Cacheable(cacheNames = "products", key = "#id")
public Product getById(Long id) {
    return repository.findById(id)
        .orElseThrow();
}

@CacheEvict(cacheNames = "products", key = "#id")
public void delete(Long id) {
    repository.deleteById(id);
}
~~~

### Cần hiểu

- Annotation cache không thay thế cache design.
- Method phải đi qua Spring proxy thì annotation mới hoạt động.
- Key phải ổn định và không gây collision giữa cache.
- Không cache exception hoặc dữ liệu nhạy cảm tùy tiện.

## Tuần 6: RedisCacheManager và serialization

### Học

- RedisCacheManager.
- Per-cache TTL.
- Key prefix.
- Disable caching nulls.
- JSON serializer.
- String serializer.
- Generic Jackson serializer.
- Java native serialization và rủi ro deserialization.
- Schema/version compatibility.
- Cache value size.

Spring Data Redis cảnh báo Java native serialization không phù hợp với input không tin cậy; nên cân nhắc JSON serializer và schema rõ ràng. [RedisTemplate and serializers](https://docs.spring.io/spring-data/redis/reference/redis/template.html)

### Thực hành

- Cấu hình TTL khác nhau cho products, sessions và permissions.
- Dùng prefix tách cache.
- Kiểm tra dữ liệu thực tế bằng redis-cli.
- Thay đổi DTO version và xử lý cache cũ.
- Không cache object có kích thước quá lớn.

## Tuần 7: RedisTemplate và low-level operations

### Học

- RedisTemplate.
- StringRedisTemplate.
- ValueOperations.
- HashOperations.
- ListOperations.
- SetOperations.
- ZSetOperations.
- StreamOperations.
- Pipelining.
- Transactions với MULTI/EXEC.
- Lua script.
- Connection factory.
- Lettuce và Jedis ở mức lựa chọn.

RedisTemplate cung cấp abstraction cho serialization, connection management và các thao tác Redis phổ biến. [Spring Data Redis](https://docs.spring.io/spring-data/redis/reference/redis.html)

### Bài tập

- Tạo product repository bằng RedisTemplate.
- Dùng hash cho session.
- Dùng sorted set cho leaderboard.
- Dùng pipeline cho batch read.
- Viết Lua script atomic cho rate limit.
- So sánh nhiều round-trip với pipeline.

---

# Giai đoạn 4 — Cache correctness

## Tuần 8: Invalidation, stampede, penetration và avalanche

### Học

- Cache invalidation khi create/update/delete.
- Thundering herd/cache stampede.
- Cache penetration.
- Cache avalanche.
- Null caching.
- Probabilistic early expiration.
- Request coalescing.
- Single-flight.
- Distributed lock cho rebuild cache.
- Jitter cho TTL.

### Thực hành

Thiết kế flow khi product hết TTL:

~~~text
Request 1 → acquire rebuild lock → query DB → set cache
Request 2 → chờ ngắn hoặc trả stale value
Request 3 → không tạo thêm query DB
~~~

### Kiểm tra

- Một key hết hạn dưới tải cao.
- Database bị chậm.
- Product không tồn tại.
- Hàng nghìn key hết hạn cùng thời điểm.
- Một node application chết khi đang rebuild cache.

## Tuần 9: Consistency và transaction

### Học

- DB-first rồi invalidate cache.
- Cache-first và rủi ro.
- Write-through.
- Transaction database không tự bao gồm Redis.
- TransactionAwareCacheManager.
- Outbox/invalidation event.
- Versioned cache value.
- Read-after-write.
- Stale-while-revalidate.
- Negative caching.

### Bài tập

Thiết kế update product an toàn:

1. Update database.
2. Commit transaction.
3. Invalidate hoặc update cache.
4. Publish invalidation event nếu có nhiều instance.
5. Xử lý trường hợp bước 3 thất bại.

### Tiêu chí hoàn thành

- Biết Redis cache không nên là source of truth cho dữ liệu nghiệp vụ quan trọng.
- Có strategy khi cache và database tạm thời không đồng bộ.
- Có metric cho invalidation failure.

---

# Giai đoạn 5 — Redis patterns

## Tuần 10: Session, token, rate limiting và idempotency

### Học

- Distributed session.
- Session TTL và sliding expiration.
- Token blacklist.
- Fixed-window rate limit.
- Sliding-window rate limit bằng sorted set.
- Token bucket ở mức khái niệm.
- Idempotency key cho POST.
- Deduplication record.
- Atomic INCR và Lua.

### Thực hành

- Lưu session với Hash và TTL.
- Rate limit theo user và IP.
- Chặn duplicate payment request bằng idempotency key.
- Dùng Lua để cập nhật counter và expiry atomic.

### Cảnh báo

- Không tin IP header nếu proxy chưa được cấu hình đúng.
- Không lưu access token dạng plaintext nếu yêu cầu bảo mật cao.
- Không dùng in-memory counter trong nhiều instance.

## Tuần 11: Distributed lock và coordination

### Học

- SET NX PX.
- Lock token.
- Safe release bằng Lua.
- Lock TTL.
- Lock renewal.
- Clock và network partition.
- Fencing token.
- Redis lock không thay thế transaction database.
- Khi nào nên dùng database lock hoặc queue.

### Thực hành

- Chống hai worker cùng phát invoice.
- Chống hai instance cùng refresh cache hot key.
- Chống scheduled job chạy trùng.
- Mô phỏng worker chết trước khi release lock.

### Quy tắc

- Luôn có TTL cho lock.
- Chỉ owner token mới được release.
- Release trong finally.
- Không giữ lock khi gọi dependency chậm nếu không cần.
- Với nghiệp vụ tiền hoặc consistency cao, đánh giá fencing token và DB constraint.

## Tuần 12: Pub/Sub, Lists và Redis Streams

### Học

- Redis Pub/Sub.
- Fire-and-forget.
- Subscriber không online sẽ mất message.
- Redis Streams.
- XADD, XREAD.
- Consumer group.
- XREADGROUP.
- XACK.
- Pending entries.
- Claim message.
- Replay.
- Retention và trimming.

Pub/Sub phù hợp cho broadcast không cần replay; nếu cần persistence, consumer group hoặc at-least-once processing thì dùng Streams. [Redis Pub/Sub](https://redis.io/docs/latest/develop/use-cases/pub-sub/) · [Redis Streams](https://redis.io/docs/latest/develop/data-types/streams/)

### Thực hành

- Pub/Sub cho cập nhật WebSocket.
- Stream cho order events.
- Tạo consumer group.
- Xử lý pending message khi consumer chết.
- XACK sau khi side effect thành công.
- XTRIM theo retention policy.

---

# Giai đoạn 6 — Persistence, scale và vận hành

## Tuần 13: Memory, TTL, eviction và persistence

### Học

- Redis memory model.
- maxmemory.
- RDB snapshot.
- AOF.
- AOF fsync policy.
- RDB và AOF trade-off.
- Eviction policy.
- noeviction.
- allkeys-lru.
- allkeys-lfu.
- volatile-lru.
- volatile-ttl.
- Fragmentation.
- Big key và hot key.

### Lab

- Tạo dataset lớn.
- Đo memory usage.
- Tạo key có và không có TTL.
- Kích hoạt maxmemory.
- So sánh eviction policy.
- Thử restart với RDB và AOF.

Redis persistence có RDB snapshot và AOF log; lựa chọn phụ thuộc vào durability và latency cần thiết. [Redis persistence](https://redis.io/docs/latest/operate/oss_and_stack/management/persistence/)

### Cần nhớ

- Cache thường có thể dùng allkeys-lru hoặc allkeys-lfu.
- Dữ liệu quan trọng không nên bị eviction vô tình.
- Always set TTL cho cache key.
- Tránh một key quá lớn hoặc một hot key tập trung mọi traffic.

## Tuần 14: Replication, Sentinel và Cluster

### Học

- Primary/replica.
- Replication lag.
- Read from replica.
- Sentinel discovery.
- Automatic failover.
- Quorum.
- Redis Cluster.
- Hash slot.
- Key distribution.
- Multi-key command trong Cluster.
- Hash tags.
- Cluster-aware client.

### Thực hành

- Chạy Redis primary và replica.
- Mô phỏng primary failure.
- Dùng Sentinel để discover master.
- Tạo Cluster lab.
- Kiểm tra key bị phân phối trên slot.
- Thử multi-key operation với và không có hash tag.

### Tiêu chí hoàn thành

- Biết Sentinel và Cluster giải quyết các vấn đề khác nhau.
- Biết replica read có thể stale.
- Biết thiết kế key để multi-key operation không phá Cluster slot.

## Tuần 15: Performance và observability

### Học

- Command latency.
- Hit ratio và miss ratio.
- Ops per second.
- Memory usage.
- Evicted keys.
- Expired keys.
- Connected clients.
- Blocked clients.
- Slowlog.
- Latency doctor.
- INFO command.
- Keyspace notifications.
- OpenTelemetry/Micrometer.

### Dashboard tối thiểu

- Cache hit ratio.
- Cache miss ratio.
- Redis command latency p50/p95/p99.
- Memory used và fragmentation.
- Evicted/expired keys.
- Connected/blocked clients.
- Commands per second.
- Hot key.
- Big key.
- Replication lag.
- Stream pending entries.

### Incident drill

Điều tra lần lượt:

1. Ứng dụng chậm do Redis hay database?
2. Có connection pool cạn không?
3. Có hot key hoặc big key không?
4. Có eviction tăng đột biến không?
5. Hit ratio giảm vì deploy hay TTL?
6. Có command block lâu không?
7. Replica lag hoặc failover có xảy ra không?

## Tuần 16: Testing, security và capstone

### Học

- Unit test cache key và TTL.
- Integration test với Testcontainers Redis.
- Serialization compatibility test.
- Concurrency test.
- Failure/reconnect test.
- Contract test cho cache value.
- Authentication và ACL.
- TLS.
- Network isolation.
- Secret management.
- Backup/restore.

### Security checklist

- Không expose Redis trực tiếp ra Internet.
- Bật authentication/ACL phù hợp.
- Tách Redis cache và Redis data quan trọng nếu cần.
- Giới hạn command hoặc quyền theo service.
- Không log session/token/payload nhạy cảm.
- Dùng TLS khi traffic đi qua network không tin cậy.
- Có backup nếu Redis chứa dữ liệu không thể tái tạo.

---

# Capstone: Production-ready Product Platform

## Chức năng

- Product detail API dùng cache-aside.
- Search result có TTL và versioned key.
- Product update có DB transaction rồi invalidate cache.
- Session store bằng Redis Hash.
- Rate limit bằng sorted set hoặc Lua.
- Idempotency key cho create order.
- Distributed lock cho rebuild hot cache.
- Pub/Sub cho WebSocket notification.
- Redis Stream cho asynchronous audit events.
- Redis Sentinel hoặc Cluster trong môi trường lab.

## Yêu cầu đo lường

- So sánh latency trước/sau cache.
- Hit ratio tối thiểu theo endpoint.
- Benchmark cold cache và warm cache.
- Mô phỏng 100 request cùng lúc khi key hết hạn.
- Đo memory và key count.
- Đo p95/p99 Redis latency.
- Kiểm tra duplicate side effect.
- Kiểm tra failover/reconnect.

## Deliverables

- Cache decision record.
- Key naming document.
- TTL matrix.
- Invalidation matrix.
- Serialization/version policy.
- Docker Compose.
- Integration test.
- Load test script.
- Dashboard.
- Incident runbook.
- Backup/restore runbook.
- README có cách chạy từ đầu.

---

# Cache decision template

Với mỗi dữ liệu cần cache, ghi:

~~~text
Name:
Source of truth:
Read/write ratio:
Acceptable staleness:
Key:
Value format:
TTL:
Jitter:
Invalidation trigger:
Maximum size:
Failure behavior:
Metrics:
Security classification:
~~~

---

# Checklist sau 16 tuần

## Caching

- Chọn đúng cache strategy.
- Có TTL và invalidation policy.
- Xử lý stampede, penetration và avalanche.
- Biết cache nào được phép stale.
- Không cache dữ liệu nhạy cảm tùy tiện.

## Redis

- Chọn đúng data structure.
- Thiết kế key có namespace.
- Dùng pipeline và Lua khi cần atomicity.
- Tránh KEYS và big key.
- Biết eviction và persistence.

## Spring Boot

- Dùng Spring Cache.
- Cấu hình RedisCacheManager.
- Dùng RedisTemplate.
- Chọn serializer an toàn.
- Test với Testcontainers.

## Distributed systems

- Dùng lock có token và TTL.
- Dùng Pub/Sub đúng với fire-and-forget.
- Dùng Streams khi cần replay/consumer group.
- Hiểu replica lag và failover.
- Có idempotency cho side effect.

## Production

- Có dashboard.
- Có alert.
- Có load test.
- Có failure drill.
- Có runbook.
- Có phương án khi Redis unavailable.

---

# Nguồn học chính thức

- [Redis data types](https://redis.io/docs/latest/develop/data-types/)
- [Redis persistence](https://redis.io/docs/latest/operate/oss_and_stack/management/persistence/)
- [Redis Pub/Sub](https://redis.io/docs/latest/develop/use-cases/pub-sub/)
- [Redis Streams](https://redis.io/docs/latest/develop/data-types/streams/)
- [Redis eviction](https://redis.io/docs/latest/develop/reference/eviction/)
- [Redis Sentinel](https://redis.io/docs/latest/operate/oss_and_stack/management/sentinel/)
- [Redis Cluster](https://redis.io/docs/latest/operate/oss_and_stack/management/scaling/)
- [Spring Boot Caching](https://docs.spring.io/spring-boot/reference/io/caching.html)
- [Spring Data Redis](https://docs.spring.io/spring-data/redis/reference/redis.html)
- [RedisTemplate](https://docs.spring.io/spring-data/redis/reference/redis/template.html)
- [Spring Data Redis Cache](https://docs.spring.io/spring-data/redis/reference/redis/redis-cache.html)

Cache production-ready không chỉ là thêm Redis vào application. Bạn phải chứng minh được cache giúp giảm tải, vẫn kiểm soát được stale data, không tạo stampede, không làm lộ dữ liệu và có thể phục hồi khi Redis gặp sự cố.

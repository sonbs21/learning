# Tuần 8: Invalidation, stampede, penetration và avalanche

## 1. Mục tiêu

Sau bài này, bạn có thể:

- Thiết kế invalidation khi create, update và delete.
- Phân biệt cache stampede, cache penetration và cache avalanche.
- Dùng negative caching, TTL jitter, single-flight và distributed lock.
- Thiết kế stale-while-revalidate và probabilistic early expiration.
- Viết flow chống cache miss đồng thời.
- Kiểm thử hệ thống khi key hot hết hạn, ID không tồn tại và nhiều key hết hạn cùng lúc.

---

## 2. Cache correctness

Cache correctness không chỉ là cache hit. Một cache đúng phải:

- Không trả dữ liệu stale quá mức cho phép.
- Không làm database bị dồn tải khi cache miss.
- Không để hàng nghìn request cùng rebuild một key.
- Không để ID không tồn tại liên tục đánh vào database.
- Không để nhiều key hết hạn cùng lúc gây spike.
- Có behavior rõ ràng khi Redis hoặc database lỗi.

Câu hỏi trung tâm:

~~~text
Cache có trả đúng dữ liệu và chịu được tải xấu hay không?
~~~

---

## 3. Cache invalidation

Cache invalidation là làm cho bản sao trong cache không còn được dùng sau khi source of truth thay đổi.

### Create

Flow cơ bản:

~~~text
1. Ghi database
2. Invalidate các list/search/index liên quan
3. Lần đọc sau nạp dữ liệu mới
~~~

Detail key của resource mới thường chưa tồn tại, nhưng list cache, search result hoặc count cache có thể đã tồn tại.

### Update

Flow thường dùng:

~~~text
Update database thành công
    ↓
Delete detail cache
    ↓
Invalidate list/search/projection liên quan
~~~

Search result có thể chứa product trong rất nhiều query, nên không phải lúc nào cũng xóa chính xác được. Các lựa chọn:

- TTL ngắn cho search result.
- Search index version.
- Event-based invalidation.
- Namespace versioning.
- Rebuild bất đồng bộ.

### Delete

~~~text
Delete database thành công
    ↓
Delete detail cache
    ↓
Invalidate list/index/cache liên quan
~~~

Ghi database trước rồi evict giúp tránh xóa cache khi transaction database cuối cùng rollback.

### DB-first rồi invalidate

Pattern dễ reasoning:

~~~text
1. Ghi source of truth
2. Transaction commit
3. Invalidate cache
4. Read sau đó cache miss và nạp dữ liệu mới
~~~

Nếu evict thất bại, cần TTL làm safety net, retry event, outbox và monitoring stale key.

---

## 4. Race condition khi invalidation

Flow nguy hiểm:

~~~text
Request A đọc database, nhận giá cũ 100
Request B update database thành 120
Request B delete cache
Request A ghi giá cũ 100 vào cache
~~~

Giải pháp:

- Ghi database rồi delete cache.
- Dùng version trong value hoặc key.
- Chỉ ghi cache nếu version mới hơn.
- Dùng lock khi rebuild.
- Dùng event có sequence/version.
- Dùng TTL làm safety net.

DEL và SET ở hai request khác nhau không tự tạo transaction logic.

---

## 5. Cache stampede / thundering herd

Stampede xảy ra khi một key phổ biến bị miss/expire và nhiều request cùng rebuild:

~~~text
Key hot hết hạn
    ↓
10.000 request cùng thấy miss
    ↓
10.000 query database
    ↓
Database quá tải
~~~

Stampede khác cache miss bình thường:

~~~text
Miss bình thường: 1 request → 1 query
Stampede: N request → N query cho cùng một key
~~~

Mục tiêu là biến N query thành 1 query.

---

## 6. Request coalescing và single-flight

Single-flight gom các request cùng chờ một lần rebuild:

~~~text
Request 1 ─┐
Request 2 ─┼─> một loader → database → cache
Request 3 ─┘
~~~

Ví dụ trong một JVM:

~~~java
private final ConcurrentHashMap<String, CompletableFuture<Value>> inFlight =
    new ConcurrentHashMap<>();

public Value get(String key) {
    Value cached = cache.get(key);
    if (cached != null) {
        return cached;
    }

    CompletableFuture<Value> created = new CompletableFuture<>();
    CompletableFuture<Value> existing =
        inFlight.putIfAbsent(key, created);

    if (existing != null) {
        return existing.join();
    }

    try {
        Value loaded = database.load(key);
        cache.set(key, loaded, TTL);
        created.complete(loaded);
        return loaded;
    } catch (Throwable error) {
        created.completeExceptionally(error);
        throw error;
    } finally {
        inFlight.remove(key, created);
    }
}
~~~

Single-flight trong JVM không phối hợp nhiều instance. Cần distributed lock hoặc coordinator nếu muốn giới hạn rebuild trên toàn cluster.

Cần timeout, complete exceptionally, cleanup trong finally và giới hạn số request được phép chờ.

---

## 7. Distributed lock cho rebuild

Primitive cơ bản:

~~~text
SET lock:product:123 unique-token NX PX 5000
~~~

Flow:

~~~text
Request A: miss → acquire lock thành công
Request B: miss → không lấy được lock
Request C: miss → không lấy được lock

Request A: query DB → set cache → release lock
Request B/C: đọc lại cache → trả kết quả
~~~

### Double-check

Sau khi lấy lock, phải đọc cache lần hai:

~~~text
1. Đọc cache → miss
2. Acquire lock
3. Đọc cache lại
4. Nếu đã có value → dùng value
5. Nếu vẫn miss → query database
~~~

Nếu bỏ bước 3, request lấy lock sau có thể rebuild lại dù request trước đã nạp cache.

### Release an toàn

Không DEL lock mù quáng. Owner A có thể hết lease, owner B lấy lock, rồi A xóa lock của B.

Dùng token và Lua:

~~~lua
if redis.call('GET', KEYS[1]) == ARGV[1] then
    return redis.call('DEL', KEYS[1])
end
return 0
~~~

### Giới hạn

Lock không giải quyết hoàn toàn process pause, network partition, failover, lease expiry hoặc query database lâu hơn TTL lock. Với yêu cầu nghiêm ngặt, cần fencing token và source of truth bảo vệ thao tác cuối.

---

## 8. Stale-while-revalidate

Cho phép trả value cũ trong thời gian ngắn trong khi background refresh value mới:

~~~text
Cache còn stale value
    ↓
Trả stale ngay
    ↓
Một worker refresh bất đồng bộ
    ↓
Request sau nhận value mới
~~~

Phù hợp với product description, recommendation, dashboard và feed có thể trễ.

Không phù hợp với inventory checkout, permission nhạy cảm, số dư hoặc trạng thái thanh toán.

Có thể lưu soft expiry và hard expiry:

~~~json
{
  "data": {
    "id": 123,
    "name": "Keyboard"
  },
  "softExpiry": "2026-10-02T10:05:00Z",
  "hardExpiry": "2026-10-02T10:10:00Z"
}
~~~

- Trước soft expiry: fresh.
- Sau soft expiry trước hard expiry: trả stale và refresh.
- Sau hard expiry: load source hoặc trả lỗi.

Refresh vẫn cần single-flight hoặc lock.

---

## 9. Probabilistic early expiration

TTL cố định có thể làm nhiều key hết hạn gần nhau. Early expiration cho phép một số request refresh sớm với xác suất tăng khi key gần hết hạn.

~~~text
TTL còn nhiều → xác suất refresh thấp
TTL sắp hết → xác suất refresh cao
~~~

Pseudo-code:

~~~java
Duration remaining = cache.ttl(key);
Duration refreshWindow = Duration.ofSeconds(30);

boolean refreshEarly =
    remaining.compareTo(refreshWindow) <= 0
    && ThreadLocalRandom.current().nextDouble() < 0.2;
~~~

Early expiration giảm đỉnh miss nhưng không thay thế lock hoặc single-flight.

---

## 10. Cache penetration

Penetration xảy ra khi request liên tục hỏi dữ liệu không tồn tại hoặc không thể cache:

~~~text
Request product:999999
    ↓ cache miss
Database: không tồn tại
    ↓ không cache
Request lặp lại
    ↓ database lặp lại
~~~

Nguồn phổ biến:

- ID ngẫu nhiên không tồn tại.
- Attacker quét ID.
- Input không hợp lệ.
- Cache null bị tắt.
- Negative TTL quá ngắn.
- Key mapping sai.

### Negative caching

Lưu marker NOT_FOUND với TTL ngắn:

~~~text
app:product:999999 → NOT_FOUND, TTL 30 giây
~~~

Đọc cache phải phân biệt:

- Cache miss thật.
- Resource không tồn tại.
- Redis bị lỗi.

Không biến timeout hoặc database exception thành NOT_FOUND. Khi resource được tạo, cần evict negative key.

### Validate input

~~~java
if (id == null || id <= 0) {
    throw new InvalidProductIdException(id);
}
~~~

Validate ID, query length, filter count và page size trước khi query source.

### Bloom filter

Bloom filter có thể trả lời:

~~~text
Chắc chắn không có → không query database
Có thể có → vẫn query database
~~~

Bloom filter có false positive nhưng không có false negative nếu được cập nhật đúng. Nó là optimization bổ sung, không thay thế authorization hoặc database.

---

## 11. Cache avalanche

Avalanche xảy ra khi nhiều key hết hạn hoặc mất cùng lúc:

~~~text
Nhiều key tạo cùng thời điểm với TTL 300 giây
    ↓
300 giây sau cùng hết hạn
    ↓
Hàng nghìn cache miss
    ↓
Database spike
~~~

Avalanche cũng có thể do Redis restart, failover, flush namespace, eviction hàng loạt hoặc network outage.

So sánh:

| Vấn đề | Phạm vi |
|---|---|
| Stampede | Một/vài key hot bị nhiều request rebuild |
| Penetration | Request vào dữ liệu không tồn tại |
| Avalanche | Nhiều key cùng mất hoặc cùng hết hạn |

### TTL jitter

Thay vì TTL cố định:

~~~text
TTL = 300 giây
~~~

Dùng:

~~~text
TTL = 300 + random(0..60) giây
~~~

Java:

~~~java
long ttlSeconds = 300 + ThreadLocalRandom.current()
    .nextLong(0, 61);

cache.set(key, value, Duration.ofSeconds(ttlSeconds));
~~~

Jitter phân tán expiry time nhưng không được vượt stale budget.

### Warm-up và refresh-ahead

Với key quan trọng:

- Warm cache sau deploy.
- Refresh trước expiry.
- Preload working set.
- Giới hạn concurrency refresh.
- Theo dõi database load.

Warm-up hàng nghìn key đồng thời cũng có thể tự tạo avalanche.

---

## 12. Multi-level cache

Có thể dùng:

~~~text
L1 local cache → L2 Redis → Database
~~~

L1 giảm request đến Redis, L2 giảm database load. Nhưng consistency và invalidation phức tạp hơn:

- Local cache ở instance A có thể khác instance B.
- Evict Redis không tự evict L1.
- TTL hai tầng có thể tạo stale lâu hơn.
- Cần event hoặc version để invalidate local cache.

Chỉ dùng khi có số liệu chứng minh lợi ích.

---

## 13. Outbox cho invalidation event

Nếu update database và publish event là hai bước riêng:

~~~text
Update DB thành công
Publish event thất bại
Cache không bị invalidate
~~~

Outbox lưu event cùng transaction:

~~~text
Transaction:
  update product
  insert ProductUpdated vào outbox

Worker:
  đọc outbox
  invalidate cache
  đánh dấu event đã xử lý
~~~

Cần:

- Event idempotent.
- Retry.
- Dead-letter hoặc failed state.
- Version/sequence khi cần ordering.
- Monitoring event lag.

Outbox không biến Redis và database thành một transaction; nó giảm khoảng trống giữa commit và invalidation.

---

## 14. Pattern đọc an toàn

~~~text
1. Validate input
2. Đọc cache
3. Fresh value → trả
4. NOT_FOUND marker → trả 404
5. Stale value được phép → trả stale và refresh single-flight
6. Miss → acquire single-flight/lock
7. Double-check cache
8. Load database
9. Not found → negative cache TTL ngắn
10. Found → set cache với TTL + jitter
11. Release lock
12. Trả kết quả
~~~

Không phải endpoint nào cũng cần toàn bộ flow. Chọn phần phù hợp với freshness, traffic và risk.

---

## 15. Bảng tổng hợp failure mode

| Vấn đề | Nguyên nhân | Hậu quả | Cách giảm |
|---|---|---|---|
| Invalidation sai | Cache không bị xóa/cập nhật | Stale data | DB-first, event, TTL, version |
| Stampede | Một key hot cùng miss | Nhiều query cho một key | Single-flight, lock, stale |
| Penetration | Key không tồn tại liên tục | Query DB lặp lại | Validate, negative cache, Bloom |
| Avalanche | Nhiều key cùng mất | Database spike diện rộng | Jitter, warm-up, refresh-ahead |

---

## 16. Load testing

### Hot key hết TTL

Thiết lập một key hot, TTL ngắn và database query chậm. Đo:

- Số database query lúc expiry.
- p95/p99 latency.
- Timeout.
- Lock contention.
- Request chờ single-flight.

Mục tiêu: N request miss không tạo N query.

### ID không tồn tại

Gửi nhiều ID rác và so sánh:

- Không negative cache.
- Negative cache TTL ngắn.
- Có validation/Bloom filter.

Đo database query rate.

### Nhiều key cùng expiry

Tạo hàng nghìn key với TTL cố định rồi TTL jitter. So sánh database load, Redis ops và latency.

### Redis unavailable

Kiểm tra timeout, fallback, circuit breaker, database load và behavior khi stale value còn sẵn.

---

## 17. Metrics cần theo dõi

Ngoài hit ratio:

- Cache miss theo cache name/key pattern.
- Rebuild count.
- Concurrent rebuild count.
- Lock success/failure.
- Lock wait duration.
- Single-flight waiters.
- Negative cache hit.
- Database query rate.
- Database p95/p99 latency.
- Expiration rate.
- Eviction rate.
- TTL distribution.
- Outbox invalidation lag.
- Stale response count.
- Redis timeout/error count.

Alert nên dựa trên backend load và business impact, không chỉ một ngưỡng hit ratio.

---

## 18. Bài thực hành

### Bài 1: Invalidation product

Thiết kế create/update/delete cho detail cache, category list, search result và recommendation. Ghi rõ key nào xóa ngay, key nào dùng TTL, key nào dùng event/version.

### Bài 2: Chống stampede

Implement single-flight trong JVM hoặc Redis lock. Test 100 thread cùng đọc key hết hạn. Xác nhận database nhận ít query hơn đáng kể.

### Bài 3: Chống penetration

Implement validation, sentinel NOT_FOUND và negative cache TTL 30 giây. Đo query database trước và sau.

### Bài 4: Chống avalanche

Tạo 10.000 key với TTL cố định rồi TTL jitter. Ghi số key hết hạn theo từng khoảng thời gian.

### Bài 5: Outbox invalidation

Khi update product, update product và insert event trong cùng transaction. Worker đọc event, xóa cache, retry khi Redis lỗi và đảm bảo xử lý lặp không gây sai.

---

## 19. Câu hỏi tự kiểm tra

1. Invalidation khác TTL như thế nào?
2. Stampede khác avalanche ở phạm vi nào?
3. Vì sao một hot key hết hạn làm database quá tải?
4. Single-flight trong JVM có giải quyết nhiều instance không?
5. Vì sao phải double-check cache sau khi lấy lock?
6. Vì sao release lock cần token?
7. Cache penetration là gì?
8. Negative caching có rủi ro gì khi resource vừa được tạo?
9. Bloom filter có false positive hay false negative?
10. TTL jitter giảm avalanche như thế nào?
11. Stale-while-revalidate không phù hợp với dữ liệu nào?
12. Outbox giảm failure nào trong invalidation?
13. Cần đo gì ngoài hit ratio?
14. Nếu Redis mất toàn bộ key, fallback nào ngăn database sập?

---

## 20. Tiêu chí hoàn thành

Bạn hoàn thành Tuần 8 khi có thể:

- Vẽ flow invalidation cho create/update/delete.
- Phân biệt stampede, penetration và avalanche.
- Implement negative caching với TTL ngắn.
- Dùng validation chặn request không hợp lệ.
- Dùng single-flight hoặc distributed lock chống rebuild trùng.
- Double-check cache và release lock an toàn.
- Dùng TTL jitter cho nhóm key lớn.
- Thiết kế stale-while-revalidate có soft/hard expiry.
- Giải thích giới hạn của Bloom filter.
- Thiết kế outbox/event retry cho invalidation.
- Chạy load test cho hot key, ID rác và expiry đồng loạt.
- Theo dõi metrics để phát hiện cache correctness issue.

Kết luận:

> Cache correctness không chỉ là xóa cache sau update. Hệ thống phải kiểm soát stale data, nhiều request cùng rebuild một key, request vào dữ liệu không tồn tại và hàng loạt key cùng hết hạn. Invalidation, single-flight/lock, negative caching, TTL jitter và stale-while-revalidate là các công cụ khác nhau cho các failure mode khác nhau.


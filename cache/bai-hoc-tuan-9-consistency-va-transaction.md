# Tuần 9: Consistency và transaction

## 1. Mục tiêu

Sau bài này, bạn có thể:

- Phân biệt DB-first, cache-first và write-through.
- Hiểu vì sao transaction database không tự bao gồm Redis.
- Thiết kế cache update/invalidation theo transaction boundary.
- Hiểu vai trò của TransactionAwareCacheManager.
- Dùng outbox/invalidation event để giảm khoảng trống consistency.
- Dùng versioned cache value để tránh ghi đè dữ liệu cũ.
- Xử lý read-after-write.
- Thiết kế stale-while-revalidate và negative caching có kiểm soát.
- Phân tích failure khi database, Redis hoặc event worker lỗi.

---

## 2. Consistency trong hệ thống có cache

Khi có database và cache, cùng một dữ liệu tồn tại ở nhiều nơi:

~~~text
Database: source of truth
Cache:    bản sao tối ưu cho đọc
Replica:  có thể là một bản sao khác
Search:   projection hoặc index khác
~~~

Consistency trả lời:

~~~text
Sau khi write thành công, request đọc tiếp theo sẽ thấy phiên bản nào?
~~~

Các mức thường gặp:

- Strong consistency: read sau write thấy ngay dữ liệu mới theo contract.
- Eventual consistency: các bản sao có thể lệch tạm thời rồi hội tụ.
- Read-after-write consistency: chính client vừa ghi được đảm bảo đọc thấy write của mình.
- Causal consistency: các thay đổi có quan hệ nguyên nhân-kết quả được quan sát đúng thứ tự.

Cache thường đưa hệ thống về eventual consistency trừ khi có invalidation/update rất chặt và read path được thiết kế đặc biệt.

---

## 3. Transaction database không bao gồm Redis

Một transaction database có thể bảo đảm:

~~~text
BEGIN
  UPDATE products
COMMIT
~~~

Nhưng Redis nằm ngoài transaction đó:

~~~text
Database transaction: commit/rollback trong database
Redis command:       request riêng qua network
~~~

Flow nguy hiểm:

~~~text
1. Database update thành công
2. Transaction commit
3. Redis DEL thất bại
4. Cache vẫn chứa dữ liệu cũ
~~~

Hoặc:

~~~text
1. Redis SET dữ liệu mới
2. Database transaction rollback
3. Cache chứa dữ liệu không tồn tại trong database
~~~

Không nên gọi Redis và database là một transaction chỉ vì chúng nằm trong cùng method Java hoặc cùng có annotation Transactional.

---

## 4. DB-first rồi invalidate

Đây là pattern dễ reasoning nhất cho cache-aside:

~~~text
1. Ghi database
2. Commit database thành công
3. Xóa cache
4. Read sau đó cache miss
5. Load dữ liệu mới từ database
6. Ghi lại cache
~~~

Ví dụ:

~~~java
@Transactional
public Product update(Long id, UpdateProductCommand command) {
    Product updated = repository.update(id, command);
    return updated;
}

public void evictProduct(Long id) {
    redis.delete("product:" + id);
}
~~~

Trong thực tế, eviction nên được kích hoạt sau commit, không chỉ gọi ngay sau repository update trong cùng method nếu transaction chưa commit.

### Ưu điểm

- Database vẫn là source of truth.
- Nếu cache mất, data có thể rebuild.
- Không ghi cache value mới trước khi database chắc chắn commit.
- Dễ giải thích và debug.

### Rủi ro

- Eviction có thể thất bại.
- Có khoảng thời gian cache stale.
- Request đọc đồng thời có thể ghi lại value cũ.
- Cần retry/event/TTL làm safety net.

---

## 5. Cache-first và rủi ro

Cache-first nghĩa là ghi cache trước rồi mới ghi database:

~~~text
1. Ghi cache
2. Trả response hoặc tiếp tục
3. Ghi database
~~~

Rủi ro:

- Database write fail nhưng cache đã có dữ liệu mới.
- Process chết giữa hai bước.
- Request khác đọc cache rồi tin value chưa được persist.
- Database rollback không tự rollback Redis.

Cache-first chỉ phù hợp trong write-behind hoặc use case chấp nhận eventual write persistence, có queue/retry/recovery rõ ràng. Không dùng cho số dư, thanh toán, inventory commit hoặc dữ liệu cần database là authority ngay lập tức.

---

## 6. Write-through

Write-through đưa cache vào write path:

~~~text
Application → cache layer → database
                    ↓
                cache update
~~~

Mục tiêu là cache được cập nhật cùng workflow ghi. Tuy nhiên, Redis và database vẫn thường không có distributed transaction thật sự. Write-through giảm khoảng stale nhưng không loại bỏ partial failure.

Cần có policy:

- Database ghi trước hay cache ghi trước?
- Nếu một bên lỗi, response là success hay failure?
- Có retry không?
- Nếu retry không theo thứ tự thì sao?
- Nếu database rollback thì cache xử lý thế nào?

Write-through phù hợp khi toàn bộ đường ghi đi qua một abstraction thống nhất và team chấp nhận độ phức tạp.

---

## 7. Read-after-write

Read-after-write là yêu cầu:

~~~text
Client update product thành công
Client đọc product ngay sau đó
Client phải thấy giá trị mới
~~~

Với cache-aside thông thường, có thể xảy ra:

~~~text
Writer: update DB thành công
Reader: đọc cache trước khi invalidation hoàn thành
Reader: nhận value cũ
~~~

### Cách xử lý

#### Evict sau commit

Writer chỉ phát invalidation sau khi transaction commit.

#### Cập nhật cache sau commit

Ghi cache bằng version mới sau khi database commit. Cần chắc chắn object cache đại diện đúng dữ liệu sau commit.

#### Version hoặc client token

Writer trả version:

~~~json
{
  "productId": 123,
  "version": 42
}
~~~

Reader có thể yêu cầu version tối thiểu. Nếu cache version nhỏ hơn, đọc database hoặc chờ projection cập nhật.

#### Sticky read source

Trong một khoảng thời gian ngắn sau write, client đọc từ primary database hoặc một route bảo đảm thấy write của mình. Cách này làm tăng load và cần giới hạn thời gian.

Không phải endpoint nào cũng cần read-after-write mạnh. Hãy ghi rõ requirement theo business.

---

## 8. TransactionAwareCacheManager

TransactionAwareCacheManager có thể trì hoãn một số cache operation cho đến khi transaction hoàn tất, tùy provider và cấu hình.

Mục tiêu khái niệm:

~~~text
Transaction chưa commit
    ↓
Cache operation chưa thực hiện ngay
    ↓
Commit thành công
    ↓
Cache operation được thực hiện
~~~

Điều này hữu ích để tránh put/evict cache trước khi database commit. Nhưng cần hiểu giới hạn:

- Không biến Redis thành participant của transaction database.
- Redis operation sau commit vẫn có thể thất bại.
- Không tự đảm bảo retry hoặc durable event.
- Behavior phụ thuộc cache provider và loại operation.
- Không thay thế outbox khi invalidation phải đáng tin cậy.

Vì vậy hãy kiểm tra tài liệu version Spring đang dùng và viết integration test cho commit/rollback.

---

## 9. Invalidation sau commit

Một cách rõ ràng là đăng ký event và xử lý sau commit:

~~~java
public record ProductChangedEvent(Long productId) {
}
~~~

Phát event trong service:

~~~java
@Transactional
public Product update(Long id, UpdateProductCommand command) {
    Product updated = repository.update(id, command);
    publisher.publishEvent(new ProductChangedEvent(id));
    return updated;
}
~~~

Listener sau commit:

~~~java
@TransactionalEventListener(
    phase = TransactionPhase.AFTER_COMMIT
)
public void onProductChanged(ProductChangedEvent event) {
    redis.delete("product:" + event.productId());
}
~~~

Cách này giúp không evict khi transaction rollback. Nhưng nếu process chết sau commit trước khi listener xử lý, event in-memory có thể mất. Với invalidation quan trọng, outbox bền vững hơn.

---

## 10. Outbox pattern

Outbox ghi domain event cùng transaction database:

~~~text
Transaction:
  UPDATE product
  INSERT ProductUpdated vào outbox
  COMMIT

Worker:
  đọc outbox
  invalidate cache
  retry nếu Redis lỗi
  đánh dấu event đã xử lý
~~~

### Ưu điểm

- Database update và event record commit cùng nhau.
- Event không mất chỉ vì process chết ngay sau commit.
- Có thể retry invalidation.
- Có thể audit và quan sát lag.

### Rủi ro

- Event có thể được xử lý nhiều lần.
- Worker cần idempotency.
- Outbox có thể bị backlog.
- Redis vẫn có thể lỗi lâu.
- Event ordering có thể cần version/sequence.

### Idempotency

Xóa cùng một key nhiều lần thường an toàn:

~~~text
DEL product:123
DEL product:123
~~~

Nhưng update cache bằng event cũ không idempotent nếu không kiểm tra version. Event handler cần có logic bỏ qua event đã xử lý hoặc event có version thấp hơn hiện tại.

---

## 11. Versioned cache value

Thay vì cache chỉ có data, lưu thêm version:

~~~json
{
  "version": 42,
  "data": {
    "id": 123,
    "name": "Keyboard",
    "price": 1200000
  }
}
~~~

Khi ghi cache:

~~~text
Nếu incoming.version >= cached.version
    mới được ghi
Nếu incoming.version < cached.version
    bỏ qua
~~~

Điều này giúp tránh request cũ ghi đè request mới:

~~~text
Request A đọc version 41
Request B ghi version 42
Request B set cache version 42
Request A chậm hơn cố set version 41
Cache từ chối version 41
~~~

Version có thể là:

- Database row version.
- Monotonic sequence.
- Updated-at timestamp nếu clock đáng tin.
- Event sequence do domain phát.

Versioned value hữu ích khi invalidation không đủ để ngăn stale writer.

---

## 12. Versioned key

Một lựa chọn khác là version trong key:

~~~text
product:v41:123
product:v42:123
~~~

Reader biết version hiện tại hoặc đọc pointer:

~~~text
product:current-version:123 → 42
product:v42:123 → data
~~~

Ưu điểm:

- Không cần overwrite cùng key.
- Tránh đọc nhầm format cũ.
- Hữu ích khi migration schema.

Rủi ro:

- Key cũ cần TTL/cleanup.
- Cần quản lý pointer/version.
- Nhiều version có thể làm tăng memory.
- Reader phải biết cách resolve version.

---

## 13. Read path có version

Một flow có thể là:

~~~text
1. Đọc cache value
2. Nếu value.version hợp lệ → trả
3. Nếu version thấp hơn required version → đọc database
4. Ghi value mới nếu version mới hơn
5. Trả response
~~~

Client có thể gửi version tối thiểu sau write:

~~~text
GET product:123 with minVersion=42
~~~

Nếu cache chỉ có version 41, service không trả version cũ mà đọc source hoặc chờ event. Đây là cách xây read-after-write có kiểm soát, nhưng phức tạp hơn cache-aside thông thường.

---

## 14. Stale-while-revalidate trong consistency

Stale-while-revalidate cố ý trả dữ liệu cũ trong một stale budget:

~~~text
Fresh window → trả fresh
Soft expired → trả stale và refresh async
Hard expired → bắt buộc load source
~~~

Value có thể chứa:

~~~json
{
  "data": {
    "id": 123,
    "price": 1200000
  },
  "createdAt": "2026-10-02T10:00:00Z",
  "softExpiry": "2026-10-02T10:05:00Z",
  "hardExpiry": "2026-10-02T10:10:00Z"
}
~~~

Phù hợp với nội dung, dashboard và recommendation. Không dùng làm nguồn quyết định cuối cho inventory, permission hoặc payment.

Refresh phải có single-flight/lock, nếu không stale-while-revalidate vẫn có thể tạo stampede.

---

## 15. Negative caching và consistency

Negative caching lưu kết quả resource không tồn tại:

~~~text
product:999 → NOT_FOUND, TTL 30 giây
~~~

Khi resource được tạo trong thời gian negative cache còn sống:

~~~text
Database: product 999 đã tồn tại
Cache:    vẫn NOT_FOUND
~~~

Đây là consistency bug. Cần:

- Evict negative key sau create.
- TTL negative ngắn.
- Không cache negative nếu create/read cần phản ánh ngay.
- Dùng version hoặc event để invalidate.
- Phân biệt not found với provider error.

Negative caching là một trade-off giữa chống penetration và read-after-create.

---

## 16. Transaction và delete cache

Một flow update phổ biến:

~~~java
@Transactional
public void update(Long id, UpdateProductCommand command) {
    repository.update(id, command);
    redis.delete("product:" + id);
}
~~~

Điểm cần hỏi:

- Lệnh delete chạy trước hay sau commit?
- Nếu database rollback thì cache có bị xóa không?
- Nếu Redis delete timeout thì retry ở đâu?
- Request đọc đồng thời có thể ghi dữ liệu cũ lại không?
- List/search cache nào cũng cần invalidate?

Nếu cần correctness cao, dùng after-commit event hoặc outbox. TTL chỉ là safety net, không phải invalidation guarantee.

---

## 17. Cache-first failure scenario

Giả sử:

~~~text
1. SET cache price=120
2. Database UPDATE thất bại
3. API trả lỗi
4. Cache vẫn trả price=120
~~~

Cách phục hồi:

- Delete cache khi database write fail.
- Ghi database trước.
- Dùng write-behind queue có retry/recovery nếu thật sự cần.
- Lưu version và source-of-truth check.
- Không trả success trước khi persistence contract hoàn tất.

Cache-first chỉ phù hợp khi business chấp nhận dữ liệu tạm thời chưa persist và có cơ chế durable write.

---

## 18. Failure matrix

| Database | Redis | Event/outbox | Behavior |
|---|---|---|---|
| Thành công | Thành công | Thành công | Cache được invalidate/cập nhật |
| Thành công | Lỗi | Có outbox | Retry invalidation |
| Thành công | Lỗi | Không có outbox | Có thể stale đến TTL, cần alert |
| Rollback | Evict trước commit | Không có | Có thể miss nhưng không nên cache value mới |
| Rollback | Cache-first đã ghi | Không có | Cache sai, cần cleanup |
| Thành công | Thành công | Event trễ | Eventual consistency |
| Thành công | Thành công | Event trùng | Handler phải idempotent |

Thiết kế phải mô tả behavior ở từng ô quan trọng, không chỉ happy path.

---

## 19. Metrics consistency

Theo dõi:

- Cache value version.
- Số lần cache version thấp hơn database version.
- Invalidation event lag.
- Outbox backlog.
- Event retry count.
- Event duplicate count.
- Read-after-write violation.
- Negative cache hit sau create.
- Stale response count.
- Redis evict/delete failure.
- Database fallback rate.
- Cache rebuild duration.

Một hệ thống có hit ratio cao nhưng nhiều version mismatch vẫn không đúng.

---

## 20. Bài thực hành

### Bài 1: DB-first invalidate

Thiết kế service update product:

1. Update DB trong transaction.
2. Chỉ invalidate sau commit.
3. Retry khi Redis lỗi.
4. Đặt TTL safety net.
5. Test rollback.

### Bài 2: Read-after-write

Viết test:

~~~text
Client update product
Client đọc ngay lập tức
Assert đọc thấy version mới
~~~

Thử ba cách:

- Evict sau commit.
- Put cache sau commit.
- Đọc primary trong thời gian ngắn.

So sánh latency và độ phức tạp.

### Bài 3: Outbox

Tạo bảng outbox và worker invalidation. Test process chết sau database commit, trước khi Redis delete. Xác nhận worker xử lý event sau đó.

### Bài 4: Versioned value

Hai thread đọc version cũ và ghi khác thời điểm. Chỉ cho phép version mới hơn ghi vào cache. Test stale writer không overwrite value mới.

### Bài 5: Negative caching

Cache NOT_FOUND 30 giây, tạo resource ngay sau đó và kiểm tra read. Thêm eviction trong create flow rồi so sánh.

### Bài 6: Stale-while-revalidate

Cài soft expiry/hard expiry. Test nhiều request sau soft expiry chỉ tạo một refresh.

---

## 21. Câu hỏi tự kiểm tra

1. Vì sao database transaction không tự bao gồm Redis?
2. DB-first khác cache-first ở failure scenario nào?
3. Vì sao nên invalidate sau commit?
4. TransactionAwareCacheManager giải quyết gì và không giải quyết gì?
5. Khi nào dùng after-commit event?
6. Outbox khác in-memory event listener ở điểm nào?
7. Vì sao event handler phải idempotent?
8. Versioned cache value ngăn stale writer thế nào?
9. Read-after-write có phải mọi endpoint đều cần không?
10. Negative caching gây bug gì sau khi resource được tạo?
11. Stale-while-revalidate phù hợp với dữ liệu nào?
12. Cần metric nào để phát hiện consistency violation?

---

## 22. Tiêu chí hoàn thành

Bạn hoàn thành Tuần 9 khi có thể:

- Vẽ flow DB-first, cache-first và write-through.
- Giải thích transaction boundary giữa database và Redis.
- Thiết kế invalidation sau commit.
- Dùng TransactionAwareCacheManager với hiểu biết về giới hạn.
- Thiết kế after-commit event và outbox retry.
- Dùng versioned cache value/key để chống stale overwrite.
- Xử lý read-after-write theo business requirement.
- Dùng stale-while-revalidate có stale budget.
- Dùng negative cache mà không che mất resource vừa được tạo.
- Viết failure matrix và metrics consistency.
- Test rollback, Redis failure, event trễ/trùng và stale writer.

Kết luận:

> Consistency với cache là bài toán về thứ tự, thời điểm và failure boundary. Database transaction chỉ bảo vệ database; Redis cần được đồng bộ bằng after-commit invalidation, outbox, version, retry và policy rõ ràng. Không có một strategy duy nhất cho mọi dữ liệu: hãy chọn mức consistency dựa trên hậu quả của stale data.


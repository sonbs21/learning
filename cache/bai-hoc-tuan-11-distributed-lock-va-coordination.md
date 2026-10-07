# Tuần 11: Distributed lock và coordination

## 1. Mục tiêu

Sau bài này, bạn có thể:

- Giải thích distributed lock giải quyết vấn đề gì.
- Acquire lock bằng SET NX PX.
- Dùng lock token để xác định ownership.
- Release lock an toàn bằng Lua.
- Thiết kế TTL, retry, backoff và lock renewal.
- Hiểu process pause, clock, network partition và failover ảnh hưởng lock thế nào.
- Hiểu fencing token và vì sao lock không thay thế transaction database.
- Chống hai worker cùng phát invoice hoặc cùng refresh cache.
- Biết khi nào nên dùng database lock hoặc queue thay vì Redis lock.

---

## 2. Coordination là gì?

Coordination là cách nhiều process, thread hoặc instance phối hợp để không cùng thực hiện một critical section trái với business rule.

Ví dụ:

~~~text
Instance A ─┐
Instance B ─┼─> cùng nhận scheduled job
Instance C ─┘
~~~

Nếu không coordination, cả ba có thể:

- Phát cùng một invoice.
- Trừ tồn kho nhiều lần.
- Refresh cùng một hot key.
- Chạy cùng một migration.
- Gửi cùng một notification.
- Xử lý cùng một message.

Distributed lock tạo một quyền sở hữu tạm thời:

~~~text
Tại một thời điểm, chỉ một owner được phép vào critical section
~~~

Lock không tự làm operation idempotent và không tự tạo transaction với database.

---

## 3. Khi nào cần distributed lock?

Use case phù hợp:

- Một hot key chỉ nên có một worker rebuild.
- Scheduled job không muốn chạy trùng giữa nhiều instance.
- Một resource cần xử lý tuần tự trong thời gian ngắn.
- Chọn một leader tạm thời cho công việc không quá critical.
- Tránh nhiều worker phát cùng một invoice trong mô hình đơn giản.
- Coordination nhẹ giữa các application instance.

Không nên dùng lock để che giấu:

- Thiếu unique constraint.
- Thiếu idempotency.
- Transaction database sai.
- Workflow có thể xử lý bằng queue.
- Critical payment correctness không có fencing/database validation.

Trước khi dùng lock, hỏi:

1. Nếu lock bị mất, hậu quả là gì?
2. Operation có idempotent không?
3. Database có constraint bảo vệ lần cuối không?
4. Có thể dùng queue để serialize công việc không?
5. Lock holder chết thì hệ thống có tự phục hồi không?

---

## 4. Primitive SET NX PX

Acquire lock cơ bản:

~~~text
SET lock:order:123 unique-token NX PX 5000
~~~

Ý nghĩa:

- Key: lock:order:123.
- Value: unique-token.
- NX: chỉ ghi nếu key chưa tồn tại.
- PX 5000: lock hết hạn sau 5 giây.

Nếu response OK, client tạm thời có lock. Nếu response nil, lock đang được giữ hoặc command không acquire được.

Trong Spring:

~~~java
String key = "lock:order:" + orderId;
String token = UUID.randomUUID().toString();

Boolean acquired = stringRedisTemplate.opsForValue()
    .setIfAbsent(key, token, Duration.ofSeconds(5));

if (!Boolean.TRUE.equals(acquired)) {
    throw new LockNotAcquiredException(key);
}
~~~

Luôn đặt TTL. Lock không có TTL có thể bị kẹt vĩnh viễn nếu process chết trước release.

---

## 5. Lock token và ownership

Value của lock phải là token duy nhất của owner:

~~~text
lock:order:123 → 8b9f...unique-token
~~~

Không dùng value cố định như:

~~~text
lock:order:123 → locked
~~~

Vì khi release, process cần chứng minh nó là owner hiện tại.

Token nên:

- Ngẫu nhiên đủ mạnh.
- Gắn với một lần acquire, không chỉ process ID.
- Không chứa secret business không cần thiết.
- Được giữ trong scope của operation.
- Không ghi log đầy đủ nếu không cần.

Ownership chỉ hợp lệ khi:

1. Acquire command trả OK.
2. Token trong lock vẫn bằng token của owner.
3. Lease chưa hết hạn hoặc đã được renew an toàn.

---

## 6. Release lock an toàn

Sai:

~~~text
DEL lock:order:123
~~~

Race condition:

~~~text
Owner A giữ lock
A bị pause lâu hơn TTL
Lock hết hạn
Owner B acquire lock
A resume và DEL lock
Lock của B bị xóa
~~~

Đúng hơn là compare-and-delete atomic bằng Lua:

~~~lua
if redis.call('GET', KEYS[1]) == ARGV[1] then
    return redis.call('DEL', KEYS[1])
end
return 0
~~~

Spring:

~~~java
private static final DefaultRedisScript<Long> RELEASE_SCRIPT =
    new DefaultRedisScript<>(
        """
        if redis.call('GET', KEYS[1]) == ARGV[1] then
            return redis.call('DEL', KEYS[1])
        end
        return 0
        """,
        Long.class
    );

Long released = stringRedisTemplate.execute(
    RELEASE_SCRIPT,
    List.of(lockKey),
    token
);
~~~

Release trong finally:

~~~java
boolean acquired = false;

try {
    acquired = acquire(lockKey, token);
    if (!acquired) {
        throw new LockNotAcquiredException(lockKey);
    }

    return doCriticalWork();
} finally {
    if (acquired) {
        release(lockKey, token);
    }
}
~~~

Nếu token không match, không xóa lock của owner khác.

---

## 7. Lock TTL

TTL là lease tối đa của lock, không phải thời gian operation được phép chạy vô hạn.

Ví dụ:

~~~text
Operation p95 = 200 ms
Operation p99 = 800 ms
Lock TTL = 5 giây
~~~

TTL 5 giây có thể đủ trong điều kiện bình thường, nhưng cần phân tích:

- Database chậm.
- GC pause.
- Thread starvation.
- Network latency.
- Redis failover.
- Dependency timeout.
- Queue backlog.

TTL quá ngắn:

- Lock hết hạn khi owner vẫn đang chạy.
- Hai owner cùng nghĩ mình hợp lệ.
- Safe release vẫn không ngăn side effect của owner cũ.

TTL quá dài:

- Lock holder chết khiến resource bị chặn lâu.
- Recovery chậm.
- Retry user phải chờ lâu.

Luôn đặt giới hạn operation timeout nhỏ hơn hoặc có quan hệ rõ với lock TTL.

---

## 8. Retry và backoff

Nếu không acquire được lock, không retry tight loop:

~~~text
while true:
    SET NX
    thất bại
    retry ngay
~~~

Tight loop tạo tải Redis và lock contention.

Dùng:

- Retry count giới hạn.
- Exponential backoff.
- Jitter.
- Timeout tổng.
- Behavior rõ khi không lấy được lock.

Ví dụ:

~~~java
Duration delay = Duration.ofMillis(20);

for (int attempt = 0; attempt < 5; attempt++) {
    if (acquire(key, token)) {
        return true;
    }

    sleepWithJitter(delay);
    delay = delay.multipliedBy(2);
}

return false;
~~~

Không retry lock vô hạn trong HTTP request vì user sẽ giữ connection quá lâu.

---

## 9. Lock renewal

Nếu operation có thể lâu hơn TTL, owner có thể renew lease trước khi hết hạn.

Renew chỉ được phép nếu token vẫn match:

~~~lua
if redis.call('GET', KEYS[1]) == ARGV[1] then
    return redis.call('PEXPIRE', KEYS[1], ARGV[2])
end
return 0
~~~

Flow:

~~~text
Acquire TTL 5s
    ↓
Worker làm việc
    ↓
Trước khi còn 2s → renew nếu còn token
    ↓
Tiếp tục
    ↓
Hoàn thành → safe release
~~~

Rủi ro:

- Renewal thread chết.
- Process pause lâu.
- Redis không reachable.
- Owner tưởng vẫn giữ lock nhưng lease đã hết.
- Renewal vô tình gia hạn lock của owner khác nếu không check token.

Nếu phải renewal phức tạp, xem xét queue, worker lease hoặc thiết kế operation ngắn hơn.

---

## 10. Process pause và GC pause

Một process có thể bị pause vì:

- Stop-the-world GC.
- CPU starvation.
- Container bị throttling.
- Thread pool cạn.
- Máy ảo bị pause.
- Debugger breakpoint.
- Scheduling delay.

Nếu pause lâu hơn TTL:

~~~text
A acquire lock
A pause
TTL hết
B acquire lock
A resume
A tiếp tục critical work
~~~

Safe release chỉ ngăn A xóa lock của B; nó không tự ngăn A thực hiện side effect sau khi mất lease. Đây là lý do lock không đủ cho critical operation nếu downstream không kiểm tra ownership/fencing.

---

## 11. Fencing token

Fencing token là số tăng dần cấp cho mỗi lần acquire:

~~~text
Owner A acquire → fencing token 41
Owner A pause
Owner B acquire → fencing token 42
Owner B ghi resource với token 42
Owner A resume, gửi token 41
Resource từ chối token 41
~~~

Downstream phải lưu và kiểm tra token:

~~~text
Nếu incoming_token < stored_token → reject
Nếu incoming_token >= stored_token → accept và cập nhật
~~~

Fencing token bảo vệ resource downstream khỏi owner cũ đã mất lock nhưng vẫn tiếp tục chạy.

Redis lock một mình không tạo fencing safety cho database hoặc service khác. Cần:

- Counter monotonic.
- Database column/version.
- Downstream API hỗ trợ fencing.
- Kiểm tra token ngay trước side effect.

---

## 12. Lock và database transaction

Redis lock không thay thế database transaction:

~~~text
Acquire Redis lock
    ↓
UPDATE database
    ↓
COMMIT
    ↓
Release Redis lock
~~~

Nếu database transaction rollback, lock không tự rollback. Nếu process chết sau commit nhưng trước release, TTL mới giải phóng lock.

Database vẫn cần:

- Transaction.
- Unique constraint.
- Optimistic/pessimistic locking khi phù hợp.
- Idempotency.
- Validation.

Lock chỉ giảm concurrency; nó không phải bằng chứng cuối cùng rằng side effect không trùng.

---

## 13. Khi nào dùng database lock?

Database lock phù hợp khi:

- Resource đã nằm trong database.
- Cần transaction cùng dữ liệu.
- Cần row-level lock.
- Correctness phụ thuộc trực tiếp vào commit database.
- Có thể dùng SELECT FOR UPDATE hoặc optimistic version.

Ví dụ:

~~~text
BEGIN
SELECT inventory FOR UPDATE
kiểm tra quantity
UPDATE inventory
COMMIT
~~~

Ưu điểm:

- Lock và mutation nằm trong cùng transaction.
- Database là source of truth.
- Dễ reasoning cho data consistency.

Rủi ro:

- Giữ lock lâu làm giảm throughput.
- Deadlock.
- Database contention.
- Không phù hợp cho coordination ngoài database.

---

## 14. Khi nào dùng queue?

Queue phù hợp khi công việc:

- Không cần xử lý ngay trong HTTP request.
- Có thể serialize theo key.
- Cần retry/durable delivery.
- Có worker xử lý độc lập.
- Muốn tránh nhiều instance cùng làm một việc.

Thay vì lock refresh invoice:

~~~text
Producer → queue → một worker xử lý invoice
~~~

Queue có thể cần:

- Message key/partition.
- At-least-once handling.
- Idempotent consumer.
- Retry/dead-letter.
- Visibility timeout.
- Ordering policy.

Queue thường tốt hơn lock cho công việc dài hoặc cần retry bền vững.

---

## 15. Chống hai worker phát invoice

Thiết kế yếu:

~~~text
Worker A kiểm tra invoice chưa có
Worker B kiểm tra invoice chưa có
A tạo invoice
B tạo invoice
~~~

Thiết kế nhiều lớp:

1. Redis lock giảm xử lý đồng thời.
2. Database unique constraint trên order/invoice type.
3. Transaction tạo invoice.
4. Nếu duplicate constraint, đọc invoice đã có.
5. Idempotent retry.

Redis lock giúp giảm duplicate work; unique constraint bảo vệ correctness cuối.

---

## 16. Chống hai instance refresh hot key

Flow:

~~~text
1. Instance A cache miss
2. Instance B cache miss
3. A acquire rebuild lock
4. B không acquire, chờ ngắn
5. A query database và set cache
6. A release lock
7. B đọc lại cache
~~~

Bắt buộc double-check sau lock. Nếu B chờ quá lâu:

- Trả stale value nếu có.
- Trả 503/retry-after.
- Query database trong giới hạn.
- Fail fast.

Không để toàn bộ request thread chờ lock vô hạn.

---

## 17. Scheduled job chạy trùng

Nhiều instance có cùng cron:

~~~text
Instance A: 00:00 chạy job
Instance B: 00:00 chạy job
Instance C: 00:00 chạy job
~~~

Có thể dùng lock:

~~~text
SET lock:job:daily-report token NX PX 300000
~~~

Nhưng job dài cần:

- TTL phù hợp.
- Renewal hoặc job lease.
- Safe release.
- Monitoring owner.
- Idempotent output.
- Recovery khi worker chết.

Nếu job cần đảm bảo delivery và retry, scheduler/queue chuyên dụng có thể tốt hơn Redis lock.

---

## 18. Lock key design

Key nên:

~~~text
lock:{resource-type}:{resource-id}
~~~

Ví dụ:

~~~text
lock:order:123
lock:cache-rebuild:product:123
lock:job:daily-report
~~~

Quy tắc:

- Namespace rõ.
- Không chứa secret.
- Resource ID canonical.
- Có environment prefix nếu dùng chung Redis.
- Không dùng key quá dài.
- Lock key không trùng data key.
- TTL luôn được đặt ở acquire.

---

## 19. Redis Cluster và lock

Trong Redis Cluster:

- Một key được route theo hash slot.
- Lua script truy cập nhiều key có thể lỗi CROSSSLOT nếu các key khác slot.
- Dùng hash tag để các key liên quan cùng slot:

~~~text
lock:{order:123}
order-data:{order:123}
~~~

Không dùng hash tag tùy tiện làm hotspot. Cần cân bằng giữa atomic multi-key và phân tán tải.

Failover có thể làm lock state thay đổi tùy replication timing. Với correctness cao, không nên xem Redis lock là bảo đảm tuyệt đối nếu chưa phân tích topology và failure model.

---

## 20. Observability

Theo dõi:

- Lock acquire success/failure.
- Lock wait duration.
- Lock hold duration.
- Lock renewal success/failure.
- Lock expired while owner active.
- Contention theo lock key pattern.
- Operation timeout.
- Redis command latency.
- Release mismatch.
- Duplicate side effect.
- Fencing rejection.
- Worker/job recovery count.

Log nên có:

- Resource type/id đã được mask nếu nhạy cảm.
- Owner/request correlation ID.
- Acquire timestamp.
- TTL.
- Operation duration.
- Outcome.

Không log token đầy đủ trong production.

---

## 21. Failure matrix

| Failure | Hậu quả | Cách giảm |
|---|---|---|
| Owner chết trước release | Lock giữ đến TTL | Luôn đặt TTL |
| TTL quá ngắn | Hai owner cùng chạy | Chọn TTL/renewal/fencing |
| Release mù bằng DEL | Xóa lock owner khác | Token + Lua |
| Redis timeout khi acquire | Không biết lock state | Timeout, fail policy |
| Network partition | Owner cũ vẫn chạy | Fencing/downstream validation |
| Process pause | Lease hết hạn | Fencing, operation ngắn |
| Renewal thất bại | Lock mất khi đang chạy | Stop work hoặc fencing |
| DB rollback | Side effect/cache sai nếu xử lý trước | DB transaction, DB-first |
| Worker retry | Duplicate work | Idempotency/unique constraint |

---

## 22. Bài thực hành lock cơ bản

Implement:

~~~java
boolean acquire(String key, String token, Duration ttl);
boolean release(String key, String token);
~~~

Yêu cầu:

- Acquire bằng SET NX PX.
- Release bằng compare-and-delete Lua.
- Token ngẫu nhiên mỗi lần acquire.
- Release trong finally.
- Test owner khác không thể release.
- Test lock tự hết hạn.

---

## 23. Bài thực hành cache rebuild

Tạo endpoint đọc product hot với 50 thread đồng thời khi key hết hạn.

So sánh:

1. Không lock.
2. Single-flight trong một JVM.
3. Redis distributed lock.
4. Distributed lock + stale-while-revalidate.

Đo:

- Số query database.
- p95/p99.
- Số request chờ.
- Lock contention.
- Redis command count.

---

## 24. Bài thực hành invoice

Thiết kế worker có thể chạy ở ba instance nhưng chỉ một instance phát invoice cho order.

Yêu cầu:

- Redis lock có TTL.
- Database unique constraint.
- Transaction tạo invoice.
- Nếu duplicate, đọc invoice hiện có.
- Worker retry không tạo invoice thứ hai.
- Test worker chết trước release.
- Test worker pause lâu hơn TTL.

---

## 25. Bài thực hành fencing token

Thiết kế bảng/resource lưu last fencing token:

~~~text
resource_id
last_fencing_token
value
~~~

Khi worker ghi:

~~~text
Nếu token mới < last token → reject
Nếu token mới >= last token → accept
~~~

Mô phỏng:

1. Worker A nhận token 41.
2. A pause.
3. Worker B nhận token 42 và ghi.
4. A resume và cố ghi token 41.
5. Resource phải từ chối A.

---

## 26. Câu hỏi tự kiểm tra

1. Distributed lock giải quyết vấn đề gì?
2. Vì sao lock value cần unique token?
3. Vì sao SET NX PX tốt hơn SET rồi EXPIRE?
4. Vì sao DEL lock mù quáng nguy hiểm?
5. TTL lock quá ngắn gây lỗi gì?
6. TTL lock quá dài gây lỗi gì?
7. Khi nào cần lock renewal?
8. Process pause ảnh hưởng lock thế nào?
9. Fencing token bảo vệ resource downstream ra sao?
10. Vì sao Redis lock không thay thế database transaction?
11. Khi nào database lock tốt hơn Redis lock?
12. Khi nào queue tốt hơn distributed lock?
13. Vì sao scheduled job vẫn cần idempotency dù có lock?
14. Redis Cluster ảnh hưởng Lua multi-key thế nào?

---

## 27. Tiêu chí hoàn thành

Bạn hoàn thành Tuần 11 khi có thể:

- Acquire lock bằng SET NX PX.
- Dùng token để xác định owner.
- Release an toàn bằng Lua.
- Đặt TTL và thiết kế retry/backoff.
- Renew lock có kiểm tra token.
- Giải thích process pause, clock và network partition.
- Thiết kế fencing token cho downstream.
- Chống hai worker phát invoice.
- Chống hai instance refresh hot key.
- Ngăn scheduled job chạy trùng.
- Biết khi nào dùng database lock hoặc queue.
- Kết hợp lock với idempotency, unique constraint và transaction.
- Theo dõi contention, expiry và failure.

Kết luận:

> Redis distributed lock là cơ chế coordination tạm thời, không phải transaction và không phải bảo đảm tuyệt đối rằng chỉ một side effect xảy ra. Thiết kế an toàn cần TTL, unique token, safe release, retry có giới hạn, fencing hoặc database constraint khi cần, cùng một policy rõ ràng cho process chết và network failure.


# Tuần 10: Session, token, rate limiting và idempotency

## 1. Mục tiêu

Sau bài này, bạn có thể:

- Thiết kế distributed session bằng Redis Hash và TTL.
- Phân biệt fixed expiration và sliding expiration.
- Hiểu token blacklist và trade-off bảo mật.
- Xây fixed-window và sliding-window rate limit.
- Nắm ý tưởng token bucket.
- Dùng idempotency key cho POST và payment request.
- Thiết kế deduplication record.
- Dùng atomic INCR và Lua cho counter/expiry.
- Xử lý nhiều instance application.
- Tránh tin IP header hoặc lưu token plaintext một cách không an toàn.

---

## 2. Vì sao Redis phù hợp cho các pattern này?

Session, token blacklist, rate limiting và idempotency đều cần dữ liệu ngắn hạn, truy cập nhanh và có expiration.

~~~text
Application instance A ─┐
Application instance B ─┼─> Redis shared state
Application instance C ─┘
~~~

Nếu lưu state trong memory của từng instance:

~~~text
User → Instance A: session tồn tại
User → Instance B: session không tồn tại
~~~

Redis giúp các instance dùng chung state. Tuy nhiên, Redis không tự biến state thành source of truth cho mọi nghiệp vụ. Payment, order và quyền truy cập vẫn cần database/domain service làm authority cuối cùng.

---

## 3. Distributed session

Distributed session là session được lưu ở một nơi dùng chung thay vì memory của một application instance.

### 3.1. Session model bằng Hash

~~~text
session:{sessionId}
  user_id
  role
  created_at
  last_seen_at
  device_id
~~~

Redis commands:

~~~text
HSET session:s-abc user_id 42 role customer
EXPIRE session:s-abc 1800
HGETALL session:s-abc
~~~

Trong Spring:

~~~java
HashOperations<String, String, String> sessions =
    redisTemplate.opsForHash();

String key = "session:" + sessionId;

sessions.put(key, "user_id", userId.toString());
sessions.put(key, "role", role);
redisTemplate.expire(key, Duration.ofMinutes(30));
~~~

TTL thuộc toàn bộ Hash, không thuộc từng field.

### 3.2. Session key

Session ID nên:

- Có entropy đủ lớn.
- Không đoán được.
- Không chứa user ID plaintext nếu không cần.
- Không chứa password hoặc secret khác.
- Có TTL.
- Được rotate sau login hoặc privilege change nếu threat model yêu cầu.

Không dùng session ID tuần tự như:

~~~text
session:user:42
~~~

vì attacker có thể đoán hoặc enumerate.

---

## 4. Session expiration

Có hai loại expiration phổ biến.

### 4.1. Fixed expiration

Session hết hạn sau một thời điểm cố định:

~~~text
Login 10:00
Session TTL 30 phút
Expire 10:30 dù user vẫn hoạt động
~~~

Ưu điểm:

- Dễ hiểu.
- Giới hạn tối đa rõ ràng.
- Giảm session tồn tại vô hạn.

### 4.2. Sliding expiration

Mỗi request hợp lệ gia hạn session:

~~~text
10:00 request → expire 10:30
10:20 request → expire 10:50
10:45 request → expire 11:15
~~~

Redis:

~~~java
String key = "session:" + sessionId;

if (Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
    redisTemplate.expire(key, Duration.ofMinutes(30));
}
~~~

Không nên gia hạn mù quáng mọi request nếu traffic cao; mỗi request thêm lệnh EXPIRE và có thể kéo session tồn tại vô hạn.

### 4.3. Idle timeout và absolute timeout

Thiết kế tốt thường kết hợp:

~~~text
Idle timeout:    30 phút không hoạt động
Absolute max:    8 giờ kể từ khi login
~~~

Sliding TTL chỉ kiểm soát idle timeout. Cần lưu created_at hoặc absolute_expiry để không cho session sống mãi.

---

## 5. Session security

Không lưu password trong session. Hạn chế lưu access token plaintext nếu yêu cầu bảo mật cao.

Thay vì:

~~~text
session:s-abc → access_token=eyJ...
~~~

Có thể lưu hash của token:

~~~text
SHA-256(token) → token_digest
token_digest → session metadata
~~~

Khi request gửi token:

1. Hash token nhận được.
2. Tìm digest trong Redis.
3. Kiểm tra TTL, revoke status và context.
4. Không log token gốc.

Cần cân nhắc:

- Redis access control.
- TLS.
- Không log value session.
- Xóa session khi logout.
- Revoke toàn bộ session khi đổi password hoặc phát hiện account compromise.
- Giới hạn số session trên mỗi user.

---

## 6. Session invalidation

Các sự kiện cần xóa session:

- Logout.
- Password reset.
- User bị khóa.
- Refresh token bị revoke.
- Admin revoke tất cả device.
- Phát hiện hành vi đáng ngờ.

Xóa một session:

~~~text
DEL session:s-abc
~~~

Xóa mọi session của user không nên dùng KEYS trong request path. Duy trì index:

~~~text
user:sessions:42 → Set {s-abc, s-def}
session:s-abc    → Hash
session:s-def    → Hash
~~~

Khi logout:

1. Xóa session key.
2. Xóa session ID khỏi Set index.
3. Nếu thao tác nhiều key cần consistency cao, dùng Lua hoặc workflow retry idempotent.
4. Dọn index stale định kỳ.

---

## 7. Token blacklist

Với access token stateless, server thường không cần đọc session cho mỗi request. Nhưng khi cần revoke trước expiry, có thể lưu token blacklist.

~~~text
blacklist:{tokenDigest} → revoked
TTL = thời gian còn lại của token
~~~

Nếu token còn 10 phút, blacklist chỉ cần sống 10 phút. Không cần giữ blacklist vô hạn.

### 7.1. Kiểm tra

~~~text
Token hợp lệ về chữ ký
    ↓
Tính token digest
    ↓
EXISTS blacklist:{digest}
    ↓
Có → từ chối
Không → tiếp tục
~~~

### 7.2. Trade-off

Ưu điểm:

- Revoke token trước expiry.
- TTL tự dọn record.
- Phù hợp với token cần revoke tức thời.

Rủi ro:

- Mỗi request cần thêm Redis lookup.
- Redis outage ảnh hưởng authentication.
- Blacklist rất lớn nếu token TTL dài và revoke nhiều.
- Nếu lưu token plaintext, Redis compromise làm tăng rủi ro.

Alternative:

- Access token TTL ngắn.
- Refresh token stateful.
- Session introspection.
- Token version trong user record.
- Key rotation.

---

## 8. Token version

Một cách revoke toàn bộ token của user là lưu token version:

~~~text
user:42:token-version → 7
Token claim: token_version=6
~~~

Request so sánh claim với version hiện tại. Nếu khác, token bị revoke.

Khi revoke tất cả:

~~~text
INCR user:42:token-version
~~~

Ưu điểm:

- Không cần blacklist từng token.
- Revoke tất cả device đơn giản.

Rủi ro:

- Mỗi request cần đọc version hoặc có cache.
- Không revoke riêng một token dễ như blacklist.
- Cache version stale có thể làm token cũ sống lâu hơn policy.

---

## 9. Rate limiting là gì?

Rate limiting giới hạn số request trong một khoảng thời gian.

Ví dụ:

~~~text
Một user tối đa 100 request mỗi phút
Một IP tối đa 20 request mỗi giây
Một token tối đa 10 lần login mỗi phút
~~~

Mục tiêu:

- Chống abuse.
- Bảo vệ database và downstream service.
- Giảm brute force.
- Kiểm soát chi phí.
- Đảm bảo fairness giữa user.

Rate limit không thay thế authentication, authorization hoặc WAF. Nó là một lớp bảo vệ.

---

## 10. Chọn identity cho rate limit

Có thể limit theo:

- User ID.
- API key.
- Access token digest.
- IP.
- Combination: user + endpoint.
- Tenant.
- Device fingerprint.

Không tin X-Forwarded-For hoặc header IP tùy ý. Chỉ dùng client IP từ proxy header khi reverse proxy/load balancer đã được cấu hình trusted và overwrite header đúng cách.

Nếu không:

~~~text
Attacker gửi:
X-Forwarded-For: 1.2.3.4
~~~

Application có thể bị bypass limit bằng cách đổi header.

Nên xác định:

- Trusted proxy list.
- Header chuẩn.
- Cách parse chain.
- Fallback khi header sai.
- Limit theo cả user và IP cho endpoint nhạy cảm.

---

## 11. Fixed-window rate limit

Fixed window chia thời gian thành các cửa sổ cố định.

Ví dụ window 60 giây:

~~~text
Key: ratelimit:user:42:window:291234
Limit: 100
TTL: 60 giây
~~~

Mỗi request:

~~~text
INCR ratelimit:user:42:window:291234
EXPIRE ratelimit:user:42:window:291234 60
~~~

### 11.1. Ưu điểm

- Dễ cài đặt.
- Ít memory.
- Dễ giải thích.
- Tốt cho nhiều API cơ bản.

### 11.2. Boundary effect

User có thể gửi:

~~~text
00:59 → 100 request
01:00 → 100 request
~~~

Trong khoảng nhỏ, user gửi gần 200 request dù limit là 100/phút. Đây là nhược điểm fixed window.

### 11.3. Atomic counter + expiry

INCR và EXPIRE tách riêng có race:

~~~text
INCR thành công
Process chết trước EXPIRE
Counter không hết hạn
~~~

Dùng Lua:

~~~lua
local current = redis.call('INCR', KEYS[1])

if current == 1 then
    redis.call('EXPIRE', KEYS[1], ARGV[1])
end

return current
~~~

Chỉ đặt TTL khi counter mới được tạo. Nếu đặt EXPIRE lại mỗi request, window có thể bị kéo dài thành sliding expiration ngoài ý muốn.

---

## 12. Fixed-window bằng RedisTemplate

~~~java
private static final DefaultRedisScript<Long> RATE_LIMIT_SCRIPT =
    new DefaultRedisScript<>(
        """
        local current = redis.call('INCR', KEYS[1])
        if current == 1 then
            redis.call('EXPIRE', KEYS[1], ARGV[1])
        end
        return current
        """,
        Long.class
    );

public RateLimitResult check(String key, long windowSeconds, long limit) {
    Long current = redisTemplate.execute(
        RATE_LIMIT_SCRIPT,
        List.of(key),
        String.valueOf(windowSeconds)
    );

    long count = current == null ? 0 : current;
    return new RateLimitResult(
        count <= limit,
        count,
        limit
    );
}
~~~

Trong production, cần thêm:

- Validate limit/window.
- Key namespace.
- Metrics.
- Timeout.
- Behavior khi Redis unavailable.
- Không trả raw Redis exception cho client.

---

## 13. Sliding-window rate limit bằng Sorted Set

Sliding window xét các request trong N giây gần nhất thay vì window cố định.

Dùng Sorted Set:

~~~text
Key: ratelimit:user:42
Member: request-id duy nhất
Score: timestamp milliseconds
~~~

Mỗi request:

~~~text
1. Xóa member có score <= now - window
2. Đếm số member còn lại
3. Nếu count < limit, thêm request mới
4. Đặt TTL cho key
~~~

Lua giúp các bước atomic:

~~~lua
local key = KEYS[1]
local now = tonumber(ARGV[1])
local window = tonumber(ARGV[2])
local limit = tonumber(ARGV[3])
local member = ARGV[4]

redis.call('ZREMRANGEBYSCORE', key, 0, now - window)
local count = redis.call('ZCARD', key)

if count >= limit then
    redis.call('EXPIRE', key, math.ceil(window / 1000))
    return {0, count}
end

redis.call('ZADD', key, now, member)
redis.call('EXPIRE', key, math.ceil(window / 1000))
return {1, count + 1}
~~~

Cần member duy nhất. Nếu dùng timestamp làm member và hai request cùng millisecond, request có thể overwrite nhau.

### Trade-off

Ưu điểm:

- Chính xác hơn fixed-window.
- Không có boundary burst lớn như fixed-window.

Rủi ro:

- Mỗi request lưu member.
- Memory tăng theo limit và traffic.
- Lua script dài hơn.
- Cần cleanup và giới hạn max request.

---

## 14. Token bucket ở mức khái niệm

Token bucket có:

- Capacity: số token tối đa.
- Refill rate: tốc độ tạo token.
- Mỗi request tiêu thụ một hoặc nhiều token.

~~~text
Bucket đầy → request dùng token
Bucket rỗng → request bị từ chối
Theo thời gian → token được nạp lại
~~~

Ưu điểm:

- Cho phép burst nhỏ.
- Kiểm soát tốc độ trung bình.
- Linh hoạt hơn fixed window.

Cần lưu state:

~~~text
tokens còn lại
last refill time
capacity
refill rate
~~~

Có thể dùng Hash + Lua để cập nhật atomic. Tuần này cần hiểu mô hình; implementation production cần test clock, precision, concurrency và failover.

---

## 15. Idempotency là gì?

Một operation idempotent có thể được thực hiện nhiều lần nhưng kết quả nghiệp vụ tương đương thực hiện một lần.

Điều này rất quan trọng cho POST/payment vì client có thể retry khi timeout:

~~~text
Client gửi payment
Server xử lý thành công
Response bị mất trên network
Client retry
Server nhận lại payment
~~~

Không có idempotency, có thể tạo hai payment/order.

---

## 16. Idempotency key

Client gửi key duy nhất:

~~~http
POST /payments
Idempotency-Key: pay-2026-10-02-abc123
~~~

Server lưu:

~~~text
idempotency:{userId}:{key}
  status: COMPLETED
  request_hash: ...
  response: ...
  resource_id: ...
  expires_at: ...
~~~

Khi request lặp:

- Key chưa tồn tại → xử lý.
- Key đang PROCESSING → trả conflict hoặc cho client retry poll.
- Key COMPLETED → trả lại response cũ.
- Key FAILED → policy tùy nghiệp vụ.
- Key dùng lại với payload khác → reject.

Idempotency key phải gắn với user/merchant/tenant để tránh collision giữa các principal.

---

## 17. Idempotency state machine

~~~text
ABSENT
  ↓ acquire key
PROCESSING
  ├─ success → COMPLETED
  ├─ business failure → FAILED hoặc FAILED_RETRYABLE
  └─ crash/timeout → PROCESSING hết TTL hoặc cần recovery
~~~

Record nên chứa request fingerprint:

~~~text
idempotency:{tenant}:{key}
  state = COMPLETED
  request_hash = SHA-256(canonical request)
  response_status = 201
  response_body = {...}
  resource_id = order-123
~~~

Nếu cùng key nhưng request hash khác:

~~~text
HTTP 409 Conflict
Reason: idempotency key reused with different request
~~~

Không lưu body tùy tiện nếu chứa card data, secret hoặc PII nhạy cảm. Có thể lưu resource ID và reconstruct response từ database.

---

## 18. Acquire idempotency key atomically

Primitive:

~~~text
SET idempotency:user42:key123 PROCESSING NX EX 300
~~~

Nếu response OK, request được quyền xử lý. Nếu nil, key đã tồn tại.

Nhưng chỉ key PROCESSING chưa đủ. Cần xử lý:

- Process chết giữa processing.
- TTL hết hạn khi payment vẫn đang chạy.
- Request retry trong lúc request đầu tiên đang xử lý.
- Response lưu thành công nhưng client không nhận.
- Database commit thành công nhưng Redis SET COMPLETED thất bại.

Idempotency cho payment cần kết hợp database unique constraint hoặc transaction nghiệp vụ. Redis không nên là nơi duy nhất bảo vệ việc tạo payment.

---

## 19. Database unique constraint

Một design an toàn hơn:

~~~text
Database:
  UNIQUE(merchant_id, idempotency_key)
~~~

Flow:

~~~text
1. Acquire Redis key để giảm duplicate concurrent request
2. Tạo payment trong database với unique constraint
3. Commit
4. Lưu kết quả vào idempotency record
5. Retry request đọc payment theo unique key
~~~

Nếu Redis mất, database constraint vẫn bảo vệ correctness cuối cùng.

Redis giúp giảm công việc trùng và tăng tốc response; database/domain transaction làm authority cuối.

---

## 20. Lưu response hay resource ID?

### Lưu toàn bộ response

Ưu điểm:

- Retry trả response nhanh.
- Không cần query database lần nữa.

Rủi ro:

- Payload lớn.
- Có PII/secret.
- Response schema đổi.
- TTL record dài làm memory tăng.

### Lưu resource ID

Ưu điểm:

- Nhỏ.
- Response mới có thể rebuild từ source.
- Ít dữ liệu nhạy cảm hơn.

Rủi ro:

- Retry cần query database.
- Database phải còn resource.
- Response có thể khác format cũ nếu API schema đổi.

Với payment, thường ưu tiên lưu trạng thái/resource ID và chỉ lưu response fields cần thiết, không lưu secret.

---

## 21. Idempotency khi Redis lỗi

Các policy:

### Fail closed

Không xử lý request nếu không thể xác định idempotency key state.

Phù hợp với thao tác tiền nếu database chưa có cơ chế bảo vệ đủ mạnh.

### Fail open

Tiếp tục xử lý nếu Redis lỗi.

Chỉ an toàn khi database có unique constraint hoặc operation vốn đã idempotent. Nếu không, Redis outage có thể tạo duplicate payment.

### Database-first idempotency

Dùng database unique constraint làm authority, Redis chỉ làm optimization. Đây thường là hướng an toàn hơn cho nghiệp vụ quan trọng.

---

## 22. TTL cho idempotency record

TTL phải dài hơn khoảng thời gian client có thể retry và nghiệp vụ cần dedup:

~~~text
Payment request retry window: 24 giờ
Idempotency record TTL:        24–48 giờ
~~~

TTL quá ngắn:

- Retry muộn có thể xử lý payment lần hai nếu database không có unique constraint.

TTL quá dài:

- Tăng memory.
- Giữ PII lâu.
- Tăng chi phí cleanup.

Không dùng một TTL mặc định cho login, API request và payment.

---

## 23. Kết hợp session, rate limit và idempotency

Một payment endpoint có thể áp dụng nhiều lớp:

~~~text
Request
  ↓
Authentication/session
  ↓
Rate limit theo user + IP
  ↓
Validate idempotency key
  ↓
Acquire idempotency record
  ↓
Database transaction + unique constraint
  ↓
Lưu result
  ↓
Response
~~~

Mỗi lớp giải quyết vấn đề khác nhau:

- Session: user là ai?
- Rate limit: user được gọi nhanh đến mức nào?
- Idempotency: retry có tạo side effect trùng không?
- Database transaction: side effect có commit đúng không?

Không dùng rate limit thay idempotency hoặc idempotency thay authorization.

---

## 24. Cảnh báo bảo mật

- Không tin IP header nếu proxy chưa được cấu hình trusted.
- Không dùng IP làm identity duy nhất cho user đã đăng nhập.
- Không lưu access token plaintext nếu threat model yêu cầu hash.
- Không log session ID, token, payment request body hoặc idempotency response chứa PII.
- Dùng key namespace theo tenant/user.
- Validate độ dài idempotency key để tránh key abuse.
- Giới hạn TTL và payload response.
- Không cho user đoán session key.
- Kiểm tra authorization trước khi trả idempotency result.
- Không để một user đọc record idempotency của user khác.

---

## 25. Failure matrix

| Tình huống | Rủi ro | Cách xử lý |
|---|---|---|
| Redis mất session | User bị logout/fallback auth | Chấp nhận re-login hoặc dùng token strategy |
| Redis mất rate limit | Abuse tăng | WAF/local fallback/DB policy |
| Redis mất idempotency | Duplicate side effect | DB unique constraint/fail closed |
| Process chết khi PROCESSING | Key treo | TTL, recovery, trạng thái query |
| Payment DB commit, Redis chưa COMPLETED | Retry lại | Query theo unique key rồi rebuild result |
| Key cùng idempotency nhưng payload khác | Data confusion | So request hash, trả 409 |
| IP header giả | Bypass limit | Trusted proxy configuration |
| Token blacklist stale | Token revoke chậm | TTL/primary lookup/token ngắn |

---

## 26. Bài thực hành session

Tạo session bằng Hash:

~~~text
session:{randomSessionId}
  user_id
  role
  created_at
  last_seen_at
~~~

Yêu cầu:

- Fixed TTL 30 phút.
- Idle sliding TTL 30 phút.
- Absolute timeout 8 giờ.
- Logout xóa key và index.
- Revoke tất cả session của user.
- Không log session ID.
- Test nhiều instance đọc cùng session.

---

## 27. Bài thực hành rate limit

Cài:

1. Fixed window theo user.
2. Fixed window theo IP sau trusted proxy.
3. Sliding window bằng Sorted Set.
4. Lua atomic counter + expiry.

So sánh:

- Memory.
- Boundary burst.
- Latency.
- Độ chính xác.
- Behavior khi Redis timeout.
- Khả năng scale nhiều instance.

---

## 28. Bài thực hành idempotency payment

Thiết kế endpoint:

~~~http
POST /payments
Idempotency-Key: unique-key
~~~

Yêu cầu:

- Validate key.
- Hash canonical request.
- Acquire PROCESSING bằng SET NX EX.
- Database unique constraint.
- Trạng thái PROCESSING/COMPLETED/FAILED.
- Retry cùng key trả kết quả cũ.
- Cùng key khác payload trả 409.
- Redis lỗi không được tạo duplicate payment.
- Không lưu secret/card data trong record.

---

## 29. Metrics

Session:

- Active session count.
- Session creation/deletion.
- Expired session.
- Revoke count.
- Redis lookup latency.

Rate limit:

- Allowed/blocked request.
- Limit key cardinality.
- Redis script latency.
- Counter TTL.
- Rate limit bypass/error.

Idempotency:

- New key.
- Duplicate request.
- Key conflict.
- PROCESSING timeout.
- Completed replay.
- Hash mismatch.
- Database unique violation.
- Redis failure fallback.
- Response replay latency.

---

## 30. Câu hỏi tự kiểm tra

1. Vì sao session in-memory không phù hợp nhiều instance?
2. Fixed expiration khác sliding expiration thế nào?
3. Vì sao cần cả idle timeout và absolute timeout?
4. Token blacklist nên có TTL bao nhiêu?
5. Vì sao token version có thể thay thế blacklist trong một số trường hợp?
6. Fixed-window có boundary effect gì?
7. Vì sao INCR và EXPIRE nên chạy atomic?
8. Sliding window bằng Sorted Set lưu gì?
9. Vì sao không tin X-Forwarded-For tùy ý?
10. Idempotency key bảo vệ vấn đề nào?
11. Vì sao cùng key khác request hash phải bị reject?
12. Redis có nên là authority duy nhất cho payment idempotency không?
13. Khi process chết ở trạng thái PROCESSING, hệ thống phục hồi thế nào?
14. Khi nào fail closed và khi nào fail open?

---

## 31. Tiêu chí hoàn thành

Bạn hoàn thành Tuần 10 khi có thể:

- Lưu distributed session bằng Hash và TTL.
- Implement fixed và sliding session expiration.
- Revoke session/token an toàn.
- Thiết kế token blacklist hoặc token version.
- Rate limit theo user, tenant và IP đáng tin cậy.
- Implement fixed-window bằng atomic INCR/Lua.
- Implement sliding-window bằng Sorted Set.
- Giải thích token bucket.
- Thiết kế idempotency key và request hash.
- Kết hợp Redis guard với database unique constraint.
- Xử lý PROCESSING timeout và duplicate request.
- Thiết kế behavior khi Redis unavailable.
- Theo dõi metrics và không lộ secret/PII.

Kết luận:

> Redis là shared state rất phù hợp cho session, rate limit và idempotency guard, nhưng không phải lúc nào cũng là source of truth. Session cần TTL và security; rate limit cần atomicity và identity đáng tin; idempotency của payment cần thêm database constraint hoặc transaction để bảo vệ side effect cuối cùng.


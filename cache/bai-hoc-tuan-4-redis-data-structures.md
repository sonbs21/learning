# Tuần 4: Redis data structures

## 1. Mục tiêu

Sau bài này, bạn có thể:

- Chọn đúng Redis data structure theo access pattern.
- Dùng String cho object cache, counter và token.
- Dùng Hash cho session, profile và object nhiều field.
- Dùng List cho feed hoặc queue đơn giản.
- Dùng Set cho membership duy nhất và tags.
- Dùng Sorted Set cho leaderboard, ranking và delay queue.
- Dùng Stream cho event có thể đọc lại và consumer group.
- Dùng HyperLogLog cho unique count gần đúng.
- Dùng Bitmap cho trạng thái boolean theo user hoặc ngày.
- Hiểu giới hạn memory, TTL và atomicity của từng structure.

---

## 2. Redis data structure là gì?

Redis không chỉ lưu một chuỗi `key → value`. Mỗi key có thể trỏ đến một cấu trúc với command và cách sử dụng riêng:

| Structure | Ý nghĩa | Use case điển hình |
|---|---|---|
| String | Một value | Cache object, token, counter |
| Hash | Nhiều field/value | Session, profile |
| List | Danh sách có thứ tự | Feed, queue đơn giản |
| Set | Phần tử duy nhất, không thứ tự | Membership, tags |
| Sorted Set | Member + score | Leaderboard, ranking |
| Stream | Log các entry có ID | Event processing |
| HyperLogLog | Cardinality gần đúng | Unique visitor |
| Bitmap | Chuỗi bit 0/1 | Active user, feature flag |

Chọn đúng structure giúp giảm code application, giảm round trip và tận dụng atomic command. Chọn sai có thể làm payload quá lớn, query chậm hoặc mất khả năng replay event.

Trước khi chọn, hãy hỏi:

1. Có cần giữ thứ tự không?
2. Có cần phần tử duy nhất không?
3. Có cần sắp xếp theo score không?
4. Có cần cập nhật từng field không?
5. Event có cần đọc lại sau khi consumer offline không?
6. Count cần chính xác tuyệt đối hay gần đúng là đủ?

---

## 3. String

String chứa một value: text, số, JSON hoặc binary.

```text
SET shop:prod:product:123 '{"id":123,"name":"Keyboard","price":1200000}' EX 300
GET shop:prod:product:123
TYPE shop:prod:product:123
DEL shop:prod:product:123
```

Redis không hiểu JSON; application serialize và deserialize.

### Khi nên dùng

- Cache cả object hoặc API response.
- Token, session ID.
- Counter và feature flag đơn giản.
- Primitive cho distributed lock.

### Counter

```text
INCR shop:prod:views:product:123
INCRBY shop:prod:views:product:123 10
DECR shop:prod:views:product:123
```

`INCR` và `INCRBY` atomic ở cấp command. Nhưng `GET → tự tính → SET` không atomic.

### Hạn chế

Nếu chỉ sửa một field của JSON, application phải đọc, deserialize, sửa và ghi lại toàn bộ value. Nếu thường xuyên cập nhật từng field, Hash có thể phù hợp hơn.

---

## 4. Hash

Hash lưu nhiều field/value dưới một key:

```text
app:user:42
  name  → Alice
  role  → admin
  locale → vi-VN
```

Command cơ bản:

```text
HSET app:user:42 name Alice role admin locale vi-VN
HGET app:user:42 name
HMGET app:user:42 name role
HGETALL app:user:42
HEXISTS app:user:42 role
HDEL app:user:42 role
HLEN app:user:42
HINCRBY app:user:42 login_count 1
```

Session:

```text
HSET shop:prod:session:s-abc user_id 42 role customer locale vi-VN
EXPIRE shop:prod:session:s-abc 1800
```

TTL áp dụng cho toàn bộ Hash, không áp dụng riêng cho từng field.

### Nên dùng Hash khi

- Cần cập nhật từng field.
- Object tương đối phẳng.
- Cần `HINCRBY` cho counter.
- Thường chỉ đọc một vài field.

### Không nên lạm dụng Hash khi

- Object lồng nhau phức tạp.
- Cần TTL khác nhau cho từng field.
- Cần query theo value trên nhiều Hash.
- Luôn đọc toàn bộ object và JSON đã là format chuẩn.

Tránh `HGETALL` trên Hash rất lớn vì có thể tạo response lớn và làm tăng latency.

---

## 5. List

List là danh sách có thứ tự, hỗ trợ thêm/xóa ở hai đầu.

```text
LPUSH app:feed:user:42 activity:1 activity:2
RPUSH app:feed:user:42 activity:3
LRANGE app:feed:user:42 0 19
LLEN app:feed:user:42
LPOP app:feed:user:42
RPOP app:feed:user:42
LTRIM app:feed:user:42 0 99
```

### Activity feed

```text
LPUSH shop:prod:feed:user:42 post:9001
LPUSH shop:prod:feed:user:42 post:9002
LTRIM shop:prod:feed:user:42 0 99
```

`LTRIM` giữ tối đa 100 item gần nhất.

### Queue đơn giản

Producer:

```text
RPUSH shop:prod:queue:email job-123
```

Consumer:

```text
BLPOP shop:prod:queue:email 30
```

List phù hợp với queue đơn giản hoặc feed ngắn. Nếu cần acknowledgment, retry, consumer group và đọc lại event, Stream thường phù hợp hơn.

### Hạn chế

- Không đảm bảo uniqueness.
- Tìm membership không phải access pattern chính.
- `LRANGE 0 -1` trên List lớn tạo response lớn.
- Không có progress tracking như Stream.

---

## 6. Set

Set là tập String duy nhất và không có thứ tự.

```text
SADD shop:prod:tag:redis product:1 product:2 product:3
SISMEMBER shop:prod:tag:redis product:1
SMEMBERS shop:prod:tag:redis
SREM shop:prod:tag:redis product:2
SCARD shop:prod:tag:redis
```

### Phép tập hợp

```text
SINTER shop:prod:tag:redis shop:prod:java
SUNION shop:prod:tag:redis shop:prod:java
SDIFF shop:prod:tag:redis shop:prod:java
```

Dùng Set cho:

- Tags.
- Unique membership.
- User đã nhận promotion.
- Follower/following trong quy mô phù hợp.
- Dedup ID.

Set không có thứ tự và không có score. Nếu cần lấy top user theo điểm, dùng Sorted Set.

Tránh `SMEMBERS` trên Set khổng lồ. TTL thuộc về toàn bộ Set, không thuộc từng member.

---

## 7. Sorted Set / ZSet

Sorted Set chứa member duy nhất và score:

```text
alice → 100
bob   → 80
```

Command cơ bản:

```text
ZADD game:leaderboard 100 alice 80 bob 120 charlie
ZRANGE game:leaderboard 0 -1 WITHSCORES
ZREVRANGE game:leaderboard 0 9 WITHSCORES
ZSCORE game:leaderboard alice
ZRANK game:leaderboard alice
ZREVRANK game:leaderboard alice
ZREM game:leaderboard bob
ZCARD game:leaderboard
```

### Leaderboard

```text
ZINCRBY game:leaderboard 50 player:42
ZREVRANGE game:leaderboard 0 9 WITHSCORES
```

### Range theo score

```text
ZRANGEBYSCORE game:leaderboard 100 500 WITHSCORES
ZCOUNT game:leaderboard 100 500
```

### Delay queue đơn giản

Dùng thời điểm hết hạn làm score:

```text
ZADD shop:delayed-jobs 1727770300 job:123
ZRANGEBYSCORE shop:delayed-jobs -inf 1727770300
```

Worker lấy job đến hạn, xử lý và xóa member. Cần thiết kế lock/claim để nhiều worker không xử lý trùng.

Sorted Set phù hợp với leaderboard, top N, ranking, recent item và delay queue. Nếu cần metadata, thường kết hợp ZSet với Hash hoặc String.

---

## 8. Stream

Stream là append-only log gồm các entry có ID và field/value. Nó phù hợp với event cần đọc lại, consumer group và theo dõi tiến độ.

### Thêm và đọc event

```text
XADD shop:events * type product-updated product_id 123
XRANGE shop:events - + COUNT 10
XREAD COUNT 10 STREAMS shop:events 0
```

`*` để Redis tạo ID. Stream có thể đọc tiếp từ một ID cụ thể.

### Consumer group

```text
XGROUP CREATE shop:events catalog-group 0 MKSTREAM
XREADGROUP GROUP catalog-group worker-1 COUNT 10 STREAMS shop:events >
XACK shop:events catalog-group <entry-id>
XPENDING shop:events catalog-group
```

Nếu consumer đọc nhưng chưa `XACK`, entry nằm trong pending entries list. Đây là nền tảng cho retry hoặc claim lại message.

### Giới hạn stream

Stream không nên tăng vô hạn:

```text
XADD shop:events MAXLEN ~ 10000 * type page-view page 42
```

### Stream và Pub/Sub

| Đặc điểm | Pub/Sub | Stream |
|---|---|---|
| Lưu message | Không bền | Có entry lưu lại |
| Consumer offline | Thường mất message | Có thể đọc lại |
| Acknowledgment | Không | Có với group |
| Replay | Không phù hợp | Phù hợp |

Nếu event quan trọng và phải xử lý lại, không mặc định dùng Pub/Sub.

---

## 9. HyperLogLog

HyperLogLog ước lượng số phần tử duy nhất với memory rất nhỏ. Nó không lưu danh sách member để trả lại.

```text
PFADD analytics:uv:2026-10-01 user:1 user:2 user:3 user:3
PFCOUNT analytics:uv:2026-10-01
PFADD analytics:uv:2026-10-02 user:2 user:4
PFMERGE analytics:uv:week analytics:uv:2026-10-01 analytics:uv:2026-10-02
PFCOUNT analytics:uv:week
```

Phù hợp với unique visitor, unique IP hoặc cardinality lớn khi sai số nhỏ có thể chấp nhận.

Không dùng khi:

- Cần danh sách user cụ thể.
- Số đếm phải chính xác tuyệt đối.
- Cần xóa một member riêng lẻ.

Tên structure là HyperLogLog nhưng command bắt đầu bằng `PF`.

---

## 10. Bitmap

Bitmap thao tác trên String như một dãy bit 0/1.

```text
SETBIT analytics:active:2026-10-01 42 1
GETBIT analytics:active:2026-10-01 42
BITCOUNT analytics:active:2026-10-01
EXPIRE analytics:active:2026-10-01 2592000
```

Offset `42` có thể đại diện user ID 42. Đây là cách lưu trạng thái boolean tiết kiệm memory nếu ID mapping phù hợp.

### Toán tử bitmap

```text
BITOP AND analytics:active:both day1 day2
BITOP OR analytics:active:any day1 day2
BITOP XOR analytics:active:changed day1 day2
```

Dùng Bitmap cho active user theo ngày, feature flag theo user hoặc attendance.

Hạn chế:

- Offset rất lớn có thể làm String phình to.
- ID thưa và lớn cần mapping riêng.
- Chỉ lưu trạng thái bit, không lưu metadata.

---

## 11. TTL và data structure

TTL thuộc về key, không thuộc từng field/member/entry.

```text
HSET session:abc user_id 42 role customer
EXPIRE session:abc 1800
```

Cả Hash hết hạn sau 1800 giây. Nếu từng member cần expiration riêng, dùng key riêng hoặc Sorted Set với score là thời điểm hết hạn.

Ví dụ key riêng:

```text
promo:user:42 EX 86400
promo:user:43 EX 3600
```

Mỗi loại structure vẫn cần lifecycle rõ ràng:

- Feed: giới hạn bằng `LTRIM` và có thể thêm TTL.
- Stream: giới hạn bằng `MAXLEN`.
- Set tags: xóa index khi resource bị xóa.
- Bitmap theo ngày: TTL dài hơn thời gian báo cáo cần giữ.
- HyperLogLog theo ngày: TTL theo thời gian analytics cần truy vấn.

---

## 12. Atomicity

Các command đơn như sau thường atomic ở cấp command:

```text
INCR counter
HINCRBY profile points 10
SADD tags redis
ZINCRBY leaderboard 10 player:1
XADD events * type order-created
SETBIT active 42 1
```

Nhưng chuỗi command không tự động atomic:

```text
ZSCORE leaderboard player:1
ZADD leaderboard new-score player:1
```

Khi logic phụ thuộc vào giá trị cũ, cân nhắc command chuyên dụng, `MULTI/EXEC`, `WATCH`, Lua script hoặc thiết kế idempotent.

Redis nhanh không có nghĩa mọi workflow nhiều command đều an toàn.

---

## 13. Kết hợp nhiều structure

### Leaderboard + profile

```text
game:leaderboard       → Sorted Set: player → score
game:player:42:profile → Hash: name, avatar, level
```

### Tag index + product cache

```text
shop:product:123 → String hoặc Hash
shop:tag:redis   → Set product IDs
```

Khi xóa product, cần cập nhật các Set index liên quan.

### Stream + current status

```text
events:orders          → Stream: lịch sử event
order:123:status       → String/Hash: trạng thái hiện tại
```

Stream phục vụ processing/replay; status key phục vụ read nhanh. Hai dữ liệu có thể có consistency khác nhau.

---

## 14. Bảng chọn nhanh

| Câu hỏi | Chọn |
|---|---|
| Cache toàn bộ object? | String |
| Cập nhật từng field? | Hash |
| Lấy phần tử gần đây theo thứ tự? | List |
| Kiểm tra một member có thuộc nhóm? | Set |
| Lấy top hoặc rank theo điểm? | Sorted Set |
| Event cần replay/ack/retry? | Stream |
| Đếm unique với memory nhỏ? | HyperLogLog |
| Lưu trạng thái 0/1 theo offset? | Bitmap |

---

## 15. Lab Redis CLI

### String

```text
SET lab:product:123 '{"id":123,"name":"Keyboard"}' EX 300
GET lab:product:123
TTL lab:product:123
```

### Hash

```text
HSET lab:session:abc user_id 42 role customer
HMGET lab:session:abc user_id role
HINCRBY lab:session:abc login_count 1
EXPIRE lab:session:abc 1800
```

### List

```text
LPUSH lab:feed:42 post:1 post:2 post:3
LRANGE lab:feed:42 0 2
LTRIM lab:feed:42 0 1
RPUSH lab:queue job:1 job:2
BLPOP lab:queue 5
```

### Set

```text
SADD lab:tag:redis product:1 product:2
SADD lab:tag:java product:2 product:3
SINTER lab:tag:redis lab:tag:java
SCARD lab:tag:redis
```

### Sorted Set

```text
ZADD lab:leaderboard 100 alice 150 bob 120 charlie
ZREVRANGE lab:leaderboard 0 -1 WITHSCORES
ZINCRBY lab:leaderboard 25 alice
ZREVRANK lab:leaderboard alice
```

### Stream

```text
XADD lab:events * type order-created order_id 1001
XRANGE lab:events - +
XGROUP CREATE lab:events billing-group 0 MKSTREAM
XREADGROUP GROUP billing-group worker-1 COUNT 10 STREAMS lab:events >
```

### HyperLogLog và Bitmap

```text
PFADD lab:uv:day1 user:1 user:2 user:3
PFCOUNT lab:uv:day1
SETBIT lab:active:day1 42 1
GETBIT lab:active:day1 42
BITCOUNT lab:active:day1
```

---

## 16. Bài tập Java/Spring Boot

1. Viết cache-aside lưu `Product` bằng String JSON, TTL 5 phút.
2. Lưu session bằng Hash; đọc một field, nhiều field và tăng `loginCount` bằng `HINCRBY`.
3. Xây service leaderboard với `addScore`, `top` và `rankOf` bằng Sorted Set.
4. Lưu tối đa 100 activity gần nhất của user bằng List.
5. Tạo producer và consumer group cho order event bằng Stream; kiểm tra pending entry khi không `XACK`.
6. Dùng HyperLogLog đếm unique visitor và Bitmap lưu user active theo ngày.
7. Với mỗi bài, ghi rõ key, TTL, giới hạn kích thước và behavior khi Redis lỗi.

---

## 17. Câu hỏi tự kiểm tra

1. Khi nào String phù hợp hơn Hash?
2. TTL của Hash áp dụng cho field hay toàn bộ key?
3. Vì sao List không phù hợp để kiểm tra membership?
4. Set khác Sorted Set ở điểm nào?
5. `ZINCRBY` phù hợp với use case nào?
6. Stream khác Pub/Sub ở khả năng replay và acknowledgment thế nào?
7. Consumer group và `XACK` giải quyết vấn đề gì?
8. HyperLogLog có trả danh sách member không?
9. Bitmap phù hợp với dữ liệu nào?
10. Vì sao cần trim List hoặc Stream?
11. Một command atomic có làm cả workflow nhiều command atomic không?
12. Khi nào nên kết hợp Sorted Set với Hash?

---

## 18. Tiêu chí hoàn thành

Bạn hoàn thành Tuần 4 khi có thể:

- Chọn structure dựa trên access pattern.
- Dùng String, Hash, List, Set và Sorted Set trong các bài lab.
- Thêm, đọc, acknowledge và theo dõi pending event trong Stream.
- Dùng HyperLogLog cho unique count gần đúng.
- Dùng Bitmap cho trạng thái boolean theo offset.
- Giải thích TTL áp dụng theo key.
- Giới hạn kích thước collection bằng trim hoặc lifecycle phù hợp.
- Nhận diện vấn đề memory, payload lớn và workflow không atomic.

Kết luận cần nhớ:

> String dành cho value, Hash dành cho field, List dành cho thứ tự, Set dành cho uniqueness, Sorted Set dành cho score, Stream dành cho event có thể đọc lại, HyperLogLog dành cho unique count gần đúng và Bitmap dành cho trạng thái bit.


# Tuần 7: RedisTemplate và low-level operations

## 1. Mục tiêu

Sau bài này, bạn có thể:

- Hiểu vai trò của `RedisTemplate` và `StringRedisTemplate`.
- Dùng `ValueOperations`, `HashOperations`, `ListOperations`, `SetOperations`, `ZSetOperations` và `StreamOperations`.
- Cấu hình serializer và connection factory.
- Viết product repository bằng `RedisTemplate`.
- Dùng pipeline cho batch read/write.
- Hiểu `MULTI/EXEC`, transaction và giới hạn của transaction Redis.
- Viết Lua script atomic cho rate limit.
- Phân biệt Lettuce và Jedis ở mức lựa chọn.
- Biết khi nào nên dùng Spring Cache abstraction và khi nào cần low-level API.

---

## 2. RedisTemplate là gì?

`RedisTemplate<K, V>` là abstraction cấp thấp hơn Spring Cache, cho phép application gọi trực tiếp các nhóm command Redis.

~~~text
Application
    ↓
RedisTemplate
    ├─ ValueOperations
    ├─ HashOperations
    ├─ ListOperations
    ├─ SetOperations
    ├─ ZSetOperations
    └─ StreamOperations
    ↓
RedisConnectionFactory
    ↓
Redis server
~~~

So sánh:

| Spring Cache | RedisTemplate |
|---|---|
| Cache theo method | Gọi command/data structure trực tiếp |
| Tự xử lý hit/miss cơ bản | Application tự viết workflow |
| Phù hợp cache method result | Phù hợp session, counter, lock, leaderboard, stream |
| Ít code hơn | Kiểm soát chi tiết hơn |

Dùng `RedisTemplate` khi cần thao tác Redis không thể mô tả tốt bằng `@Cacheable`, hoặc khi Redis là một primitive nghiệp vụ như rate limit, session, queue, leaderboard hay idempotency key.

---

## 3. Khi nào dùng RedisTemplate?

Use case phù hợp:

- Lưu session bằng Hash.
- Tăng counter bằng `INCR` hoặc `HINCRBY`.
- Leaderboard bằng Sorted Set.
- Rate limit.
- Distributed lock primitive.
- Batch read bằng pipeline.
- Stream và consumer group.
- Lua script atomic.
- Key cần format hoặc TTL đặc biệt.

Không nên dùng `RedisTemplate` chỉ để thay thế mọi `@Cacheable`. Nếu chỉ cần cache kết quả method đơn giản, abstraction thường dễ đọc và dễ maintain hơn.

---

## 4. Connection factory

Spring Data Redis thường dùng `LettuceConnectionFactory` hoặc `JedisConnectionFactory`.

~~~yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
      timeout: 2s
~~~

Tạo factory rõ ràng:

~~~java
@Configuration
public class RedisConnectionConfig {

    @Bean
    RedisConnectionFactory redisConnectionFactory(
        RedisProperties properties
    ) {
        RedisStandaloneConfiguration server =
            new RedisStandaloneConfiguration(
                properties.getHost(),
                properties.getPort()
            );

        return new LettuceConnectionFactory(server);
    }
}
~~~

Production cần cân nhắc timeout, pooling, TLS/authentication, Sentinel/Cluster, graceful shutdown và metrics. Không tạo connection mới cho từng request bằng code thủ công.

---

## 5. Lettuce và Jedis

### Lettuce

- Hỗ trợ non-blocking/reactive.
- Connection có thể được Spring quản lý và chia sẻ.
- Phù hợp với nhiều ứng dụng Spring Boot.
- Hỗ trợ Cluster, Sentinel và command hiện đại.

### Jedis

- API quen thuộc, đơn giản.
- Thường gắn với connection pool.
- Cần cấu hình pool và lifecycle cẩn thận.
- Phù hợp với một số hệ thống hiện có.

Khi chọn, xét mô hình blocking/reactive, Cluster/Sentinel, pool, compatibility với Spring Data Redis, observability và behavior khi timeout. Với project Spring Boot mới, Lettuce thường là mặc định hợp lý; không cần đổi client đang ổn định nếu chưa có vấn đề đo được.

---

## 6. RedisTemplate và serializer

Cấu hình minh họa:

~~~java
@Bean
RedisTemplate<String, Object> redisTemplate(
    RedisConnectionFactory factory,
    ObjectMapper objectMapper
) {
    RedisTemplate<String, Object> template = new RedisTemplate<>();
    template.setConnectionFactory(factory);

    StringRedisSerializer stringSerializer =
        new StringRedisSerializer();

    GenericJackson2JsonRedisSerializer jsonSerializer =
        new GenericJackson2JsonRedisSerializer(objectMapper);

    template.setKeySerializer(stringSerializer);
    template.setHashKeySerializer(stringSerializer);
    template.setValueSerializer(jsonSerializer);
    template.setHashValueSerializer(jsonSerializer);
    template.afterPropertiesSet();

    return template;
}
~~~

Nguyên tắc:

- Key thường dùng String serializer để dễ debug.
- Value dùng JSON hoặc format có schema rõ ràng.
- Hash key thường là String.
- Không trộn nhiều serializer trên cùng keyspace nếu không có namespace/version.
- Mọi instance phải dùng format tương thích.

Nếu value đã là String, JSON String hoặc counter text, dùng:

~~~java
@Bean
StringRedisTemplate stringRedisTemplate(
    RedisConnectionFactory factory
) {
    return new StringRedisTemplate(factory);
}
~~~

---

## 7. ValueOperations

~~~java
ValueOperations<String, String> values =
    stringRedisTemplate.opsForValue();
~~~

Ghi và đọc:

~~~java
values.set("shop:product:123", json, Duration.ofMinutes(5));

String cached = values.get("shop:product:123");
~~~

Set có điều kiện:

~~~java
Boolean created = values.setIfAbsent(
    "lock:order:123",
    token,
    Duration.ofSeconds(5)
);
~~~

Counter:

~~~java
Long count = values.increment("views:product:123");
Long amount = values.increment("views:product:123", 10);
~~~

Batch read:

~~~java
List<String> products = values.multiGet(List.of(
    "shop:product:123",
    "shop:product:456",
    "shop:product:789"
));
~~~

Kết quả `multiGet` giữ thứ tự theo danh sách key; key không tồn tại có thể là null. Không gọi `hasKey` rồi mới `get` nếu chỉ cần value vì sẽ tạo thêm round trip.

---

## 8. Product repository bằng RedisTemplate

DTO:

~~~java
public record ProductCacheValue(
    Long id,
    String name,
    long price
) {
}
~~~

Repository:

~~~java
@Repository
public class RedisProductRepository {

    private final RedisTemplate<String, ProductCacheValue> template;

    public ProductCacheValue findById(Long id) {
        return template.opsForValue().get(key(id));
    }

    public void save(ProductCacheValue product) {
        template.opsForValue().set(
            key(product.id()),
            product,
            Duration.ofMinutes(10)
        );
    }

    public Boolean delete(Long id) {
        return template.delete(key(id));
    }

    private String key(Long id) {
        return "shop:product:" + id;
    }
}
~~~

Repository cần có contract rõ ràng cho key format, TTL, serializer, null value, eviction, metrics và fallback. Không trả object rỗng khi serialization lỗi vì sẽ biến lỗi hạ tầng thành dữ liệu giả.

---

## 9. HashOperations

~~~java
HashOperations<String, String, String> hashes =
    stringRedisTemplate.opsForHash();
~~~

Session:

~~~java
String key = "shop:session:s-abc";

hashes.put(key, "user_id", "42");
hashes.put(key, "role", "customer");
hashes.put(key, "locale", "vi-VN");

stringRedisTemplate.expire(key, Duration.ofMinutes(30));
~~~

Đọc field:

~~~java
String userId = hashes.get(key, "user_id");
~~~

Đọc nhiều field:

~~~java
List<String> fields = hashes.multiGet(
    key,
    List.of("user_id", "role", "locale")
);
~~~

Tăng counter:

~~~java
Long loginCount = hashes.increment(
    "shop:user:42",
    "login_count",
    1
);
~~~

TTL thuộc toàn bộ Hash, không thuộc riêng từng field. Nếu field có lifecycle khác nhau, dùng key riêng hoặc structure khác.

---

## 10. ListOperations

~~~java
ListOperations<String, String> lists =
    stringRedisTemplate.opsForList();
~~~

Activity feed:

~~~java
String key = "shop:feed:user:42";

lists.leftPush(key, "activity:9001");
lists.leftPush(key, "activity:9002");
lists.trim(key, 0, 99);

List<String> latest = lists.range(key, 0, 19);
~~~

Queue:

~~~java
lists.rightPush("shop:queue:email", "job:123");

List<String> result =
    lists.leftPop("shop:queue:email", Duration.ofSeconds(30));
~~~

List queue không có acknowledgment. Consumer chết sau khi pop có thể làm message mất. Với event quan trọng, dùng Stream và consumer group. Luôn giới hạn feed bằng `trim` và đặt TTL nếu đó là dữ liệu tạm.

---

## 11. SetOperations

~~~java
SetOperations<String, String> sets =
    stringRedisTemplate.opsForSet();
~~~

Tags:

~~~java
sets.add(
    "shop:tag:redis",
    "product:1",
    "product:2"
);

Boolean exists = sets.isMember(
    "shop:tag:redis",
    "product:1"
);

Long size = sets.size("shop:tag:redis");
sets.remove("shop:tag:redis", "product:2");
~~~

Phép giao:

~~~java
Set<String> both = sets.intersect(
    "shop:tag:redis",
    "shop:tag:java"
);
~~~

Set không có thứ tự, TTL thuộc toàn key và `members` trên Set lớn có thể tạo payload lớn. Nếu cần ranking, dùng ZSet; nếu cần quét collection lớn, dùng scan thay vì tải toàn bộ về memory.

---

## 12. ZSetOperations

~~~java
ZSetOperations<String, String> zsets =
    stringRedisTemplate.opsForZSet();
~~~

Leaderboard:

~~~java
String key = "game:leaderboard:weekly";

zsets.add(key, "player:42", 1500.0);
zsets.incrementScore(key, "player:42", 50.0);
~~~

Top và rank:

~~~java
Set<ZSetOperations.TypedTuple<String>> top =
    zsets.reverseRangeWithScores(key, 0, 9);

Double score = zsets.score(key, "player:42");
Long rank = zsets.reverseRank(key, "player:42");
~~~

Delay queue:

~~~java
long dueAt = Instant.now()
    .plusSeconds(30)
    .toEpochMilli();

zsets.add("jobs:delayed", "job:123", dueAt);
~~~

Worker lấy job có score nhỏ hơn hiện tại. Cần lock/claim để nhiều worker không xử lý trùng. Khi hai member cùng score, cần quy định tie-break nếu business yêu cầu thứ hạng ổn định.

---

## 13. StreamOperations

Thêm event:

~~~java
StreamOperations<String, String, String> streams =
    stringRedisTemplate.opsForStream();

Map<String, String> body = Map.of(
    "type", "order-created",
    "order_id", "1001"
);

RecordId id = streams.add(
    StreamRecords.newRecord()
        .in("shop:events:orders")
        .ofMap(body)
);
~~~

Trong production cần:

- Consumer group và consumer name.
- Acknowledgment.
- Pending entries.
- Retry/claim.
- Idempotent handler.
- Stream trimming.

Low-level Stream API có nhiều overload; nên khóa version Spring Data Redis và viết integration test với Redis thật.

---

## 14. Pipeline

Không pipeline:

~~~text
SET → chờ → GET → chờ → TTL → chờ
~~~

Pipeline:

~~~text
gửi SET, GET, TTL → nhận nhiều response
~~~

Pipeline giảm network round trip cho các command độc lập.

~~~java
List<String> keys = List.of(
    "product:1",
    "product:2",
    "product:3"
);

List<Object> results = stringRedisTemplate.executePipelined(
    (RedisOperations<String, String> operations) -> {
        for (String key : keys) {
            operations.opsForValue().get(key);
        }
        return null;
    }
);
~~~

Cần kiểm tra:

- Response có đúng thứ tự key không.
- Batch có quá lớn không.
- Response có làm đầy memory không.
- Có command nào phụ thuộc kết quả command trước không.

Pipeline không tự atomic và không rollback command đã chạy. Không dùng pipeline cho workflow cần conditional consistency.

---

## 15. MULTI/EXEC transaction

Redis transaction gom command:

~~~text
MULTI
SET account:1:balance 90
INCR account:1:version
EXEC
~~~

Spring callback:

~~~java
List<Object> results = stringRedisTemplate.execute(
    new SessionCallback<>() {
        @Override
        public List<Object> execute(
            RedisOperations operations
        ) {
            operations.multi();
            operations.opsForValue()
                .set("tx:key", "value");
            operations.opsForValue()
                .increment("tx:counter");
            return operations.exec();
        }
    }
);
~~~

Giới hạn:

- Không rollback như database transaction đầy đủ.
- Không bao gồm database application.
- Runtime command error không tự hoàn tác command khác.
- Không thay thế optimistic locking.
- Không nên dùng transaction chỉ để giảm round trip; pipeline có thể phù hợp hơn.

---

## 16. WATCH và optimistic locking

Khái niệm:

~~~text
WATCH balance
GET balance
MULTI
SET balance new-value
EXEC
~~~

Nếu client khác đổi `balance` sau `WATCH`, `EXEC` có thể abort. Application cần retry có giới hạn và backoff.

Dùng khi phải:

1. Đọc giá trị cũ.
2. Tính giá trị mới.
3. Chỉ ghi nếu giá trị chưa bị client khác thay đổi.

Nếu có command atomic chuyên dụng như `INCRBY` hoặc `HINCRBY`, ưu tiên command đó thay vì `GET → SET`.

---

## 17. Lua script

Lua script chạy bên trong Redis, giúp nhiều bước atomic trên Redis server. Use case phổ biến:

- Rate limit.
- Compare-and-delete.
- Conditional update.
- Lock release có kiểm tra token.
- Atomic read-modify-write.

Rate limit script:

~~~lua
local current = redis.call('INCR', KEYS[1])

if current == 1 then
    redis.call('EXPIRE', KEYS[1], ARGV[1])
end

return current
~~~

Gọi bằng Spring:

~~~java
DefaultRedisScript<Long> script =
    new DefaultRedisScript<>();

script.setScriptText("""
    local current = redis.call('INCR', KEYS[1])
    if current == 1 then
        redis.call('EXPIRE', KEYS[1], ARGV[1])
    end
    return current
    """);
script.setResultType(Long.class);

Long current = stringRedisTemplate.execute(
    script,
    List.of("ratelimit:user:42"),
    "60"
);
~~~

Nguyên tắc:

- Script ngắn và bounded.
- Validate arguments.
- Không loop theo input không giới hạn.
- Test dữ liệu sai và timeout.
- Redis Cluster yêu cầu các key script truy cập cùng hash slot.
- Không dùng Lua để che giấu data model quá phức tạp.

---

## 18. Release lock an toàn bằng Lua

Không xóa lock mù quáng:

~~~text
DEL lock:order:123
~~~

Client A có thể hết lease, client B lấy lock, rồi A xóa lock của B. Dùng token:

~~~lua
if redis.call('GET', KEYS[1]) == ARGV[1] then
    return redis.call('DEL', KEYS[1])
end

return 0
~~~

- `KEYS[1]`: lock key.
- `ARGV[1]`: token của owner.

Đây là primitive cơ bản; distributed lock production vẫn cần phân tích lease expiry, network partition, process pause và fencing.

---

## 19. Connection và error handling

Lỗi RedisTemplate có thể do:

- Connection timeout.
- Serialization failure.
- Wrong Redis type.
- Cluster redirection.
- Transaction result null.
- Lua script error.
- Pool exhaustion.

Không nên nuốt mọi exception:

~~~java
try {
    return template.opsForValue().get(key);
} catch (Exception ignored) {
    return null;
}
~~~

Cách này biến Redis error thành cache miss âm thầm và có thể làm database quá tải.

Nên:

- Catch exception phù hợp.
- Có timeout.
- Ghi metric và log không chứa secret.
- Phân biệt cache miss thật với Redis failure.
- Có fallback có giới hạn.
- Test Redis unavailable.

---

## 20. Bài tập product repository

Tạo repository:

~~~java
ProductCacheValue findById(Long id);
void save(ProductCacheValue product);
void delete(Long id);
~~~

Yêu cầu:

- Dùng `RedisTemplate`.
- Key dạng `shop:product:{id}`.
- TTL 10 phút.
- Value là JSON hoặc typed DTO.
- Không dùng `KEYS`.
- Có metric hit, miss và serialization error.
- Test Redis unavailable.

Flow:

~~~text
RedisTemplate GET
    ↓ hit → trả DTO
    ↓ miss → đọc database → SET có TTL → trả DTO
~~~

---

## 21. Bài tập session Hash

Dùng `HashOperations`:

~~~text
shop:session:{sessionId}
  user_id
  role
  locale
  login_count
~~~

Yêu cầu:

- Đặt TTL cho toàn Hash.
- Đọc nhiều field bằng `multiGet`.
- Tăng login count bằng `HINCRBY`.
- Xóa session bằng delete key.
- Không lưu access token dài hạn nếu không cần.

---

## 22. Bài tập leaderboard

Dùng `ZSetOperations`:

~~~java
void addScore(String playerId, double score);
Set<TypedTuple<String>> top(int limit);
Long rankOf(String playerId);
~~~

Yêu cầu:

- Dùng `incrementScore`.
- Lấy top 10 giảm dần.
- Lấy score và rank.
- Quy định tie-break.
- Test nhiều thread update cùng player.

---

## 23. Bài tập pipeline

Tạo 1.000 product key và đọc bằng:

1. 1.000 lần `GET` riêng.
2. Một pipeline.

Đo tổng thời gian, p50/p95, CPU application, memory response và số round trip nếu có metrics. Kết luận pipeline không luôn nhanh hơn nếu value lớn hoặc batch quá lớn.

---

## 24. Bài tập Lua rate limit

Thiết kế fixed window:

~~~text
Key: ratelimit:user:{userId}:{window}
Limit: 100 request
Window: 60 giây
~~~

Script phải:

1. Atomic increment.
2. Chỉ đặt TTL khi counter vừa được tạo.
3. Trả counter hiện tại.
4. Application từ chối khi counter > 100.

Test nhiều thread, Redis timeout, TTL mất, window mới và user ID không hợp lệ.

---

## 25. Câu hỏi tự kiểm tra

1. RedisTemplate khác Spring Cache abstraction thế nào?
2. Khi nào dùng StringRedisTemplate?
3. ValueOperations và HashOperations khác nhau ở access pattern nào?
4. Vì sao List queue đơn giản có thể mất message?
5. Khi nào dùng Stream thay List?
6. Pipeline có atomic không?
7. MULTI/EXEC có rollback như database transaction không?
8. Khi nào cần WATCH?
9. Vì sao Lua giúp rate limit atomic?
10. Vì sao release lock cần kiểm tra token?
11. Serializer của RedisTemplate ảnh hưởng dữ liệu thế nào?
12. Làm sao phân biệt cache miss với Redis connection error?
13. Vì sao không nên tạo connection mới trong mỗi request?
14. Redis Cluster ảnh hưởng Lua script thế nào?

---

## 26. Tiêu chí hoàn thành

Bạn hoàn thành Tuần 7 khi có thể:

- Cấu hình connection factory và RedisTemplate.
- Dùng StringRedisTemplate cho String, JSON và counter.
- Dùng operations cho String, Hash, List, Set, ZSet và Stream.
- Tạo product repository bằng RedisTemplate.
- Tạo session Hash.
- Tạo leaderboard Sorted Set.
- Dùng pipeline cho batch read và đo trade-off.
- Dùng MULTI/EXEC đúng giới hạn.
- Viết Lua script atomic cho rate limit.
- Xử lý serializer error, timeout và fallback.
- Phân biệt lúc nào dùng annotation abstraction và lúc nào cần low-level operation.

Kết luận:

> RedisTemplate cho phép kiểm soát trực tiếp data structure và command của Redis. Quyền kiểm soát cao đi kèm trách nhiệm: application phải tự quản lý serializer, TTL, atomicity, transaction boundary, error handling, pipeline size và behavior khi Redis không sẵn sàng.


# Tuần 5: Spring Cache abstraction

## 1. Mục tiêu

Sau bài này, bạn có thể:

- Bật Spring caching với `@EnableCaching`.
- Sử dụng `@Cacheable`, `@CachePut`, `@CacheEvict` và `@Caching`.
- Hiểu `CacheManager`, `CacheResolver`, key và TTL.
- Dùng `condition` và `unless` đúng mục đích.
- Hiểu proxy, self-invocation và lý do annotation đôi khi không hoạt động.
- Thiết kế null caching, serialization, transaction boundary và fallback.
- Viết test cho cache hit, miss, eviction và lỗi serialization.

---

## 2. Spring Cache abstraction là gì?

Spring Cache abstraction cung cấp API và annotation chung cho caching, giúp code service không phụ thuộc trực tiếp vào Redis, Caffeine hay một provider cụ thể.

```text
Service method
     ↓
Spring Cache abstraction
     ↓
CacheManager
     ↓
Redis / Caffeine / provider khác
```

Ví dụ:

```java
@Cacheable(cacheNames = "products", key = "#id")
public Product getById(Long id) {
    return repository.findById(id).orElseThrow();
}
```

Khi method được gọi:

1. Spring tạo key từ biểu thức `#id`.
2. Spring kiểm tra cache.
3. Cache hit: trả value và không chạy method body.
4. Cache miss: chạy method body.
5. Spring lưu kết quả vào cache.

Abstraction không tự quyết định TTL, invalidation, consistency, serialization hoặc cache failure policy. Những phần đó vẫn là trách nhiệm của thiết kế ứng dụng.

---

## 3. Cấu hình tối thiểu

Dependency Maven thường dùng:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-cache</artifactId>
</dependency>

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

Bật cache:

```java
@SpringBootApplication
@EnableCaching
public class Application {
}
```

Cấu hình local:

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
```

Trong production, dùng environment variables/secret manager cho credential; cấu hình timeout, serializer và TTL rõ ràng; không dùng Redis `latest` một cách mù quáng.

---

## 4. CacheManager

`CacheManager` là abstraction quản lý các cache theo tên:

```java
public interface CacheManager {
    Cache getCache(String name);
    Collection<String> getCacheNames();
}
```

Khi viết:

```java
@Cacheable(cacheNames = "products")
```

Spring tìm cache có logical name `products` trong CacheManager.

Nên tách cache name theo domain/lifecycle:

```text
products
users
permissions
search-results
```

Cache name rõ ràng giúp cấu hình TTL, metrics và eviction theo nhóm. Không nên dùng một cache name chung cho mọi dữ liệu.

---

## 5. `@Cacheable`

`@Cacheable` dùng cho read path:

```java
@Service
public class ProductService {

    @Cacheable(cacheNames = "products", key = "#id")
    public Product getById(Long id) {
        return repository.findById(id)
            .orElseThrow(() -> new ProductNotFoundException(id));
    }
}
```

Cache hit:

```text
getById(123) → value trong cache → trả value
```

Cache miss:

```text
getById(123) → repository → lưu result → trả value
```

Nếu đặt breakpoint trong method, cache hit có thể không chạy vào method body.

`value` và `cacheNames` tương đương:

```java
@Cacheable(value = "products")
@Cacheable(cacheNames = "products")
```

`cacheNames` thường dễ đọc hơn khi code có nhiều annotation.

---

## 6. Thiết kế key

Key quyết định hai lời gọi có được coi là cùng một dữ liệu hay không.

```java
@Cacheable(cacheNames = "products", key = "#id")
public Product getById(Long id) { ... }
```

Với nhiều tham số:

```java
@Cacheable(
    cacheNames = "search-results",
    key = "#query + ':' + #page + ':' + #size"
)
public List<Product> search(String query, int page, int size) { ... }
```

Key cần:

- Ổn định và predictable.
- Bao gồm đủ input ảnh hưởng đến kết quả.
- Không chứa password, token hoặc dữ liệu nhạy cảm.
- Không quá dài.
- Không collision giữa resource khác nhau.

Nếu không chỉ định `key`, Spring có thể tạo key từ toàn bộ method arguments. Cách này tiện cho ví dụ đơn giản, nhưng nên khai báo rõ khi method có request object phức tạp, nhiều overload hoặc yêu cầu compatibility giữa version.

Có thể đưa logic tạo key vào helper:

```java
key = "T(com.example.cache.KeyFactory).search(#query, #page, #size)"
```

Key factory cần deterministic và có unit test riêng.

---

## 7. `@CachePut`

`@CachePut` luôn chạy method, sau đó ghi result vào cache:

```java
@CachePut(cacheNames = "products", key = "#id")
public Product update(Long id, UpdateProductCommand command) {
    return repository.update(id, command);
}
```

So sánh:

```text
@Cacheable → hit thì skip method
@CachePut  → luôn chạy method và cập nhật cache
```

Dùng `@CachePut` khi muốn refresh cache sau write thành công. Không nên đặt `@Cacheable` và `@CachePut` trên cùng method một cách tùy tiện vì một annotation muốn skip method, annotation kia muốn luôn chạy method.

Nếu update method trả object chưa phản ánh giá trị sau transaction, trigger hoặc default của database, cache có thể chứa object không chính xác. Khi không chắc, update database rồi evict key thường dễ reasoning hơn.

---

## 8. `@CacheEvict`

Xóa một key sau khi method chạy thành công:

```java
@CacheEvict(cacheNames = "products", key = "#id")
public void delete(Long id) {
    repository.deleteById(id);
}
```

Xóa toàn bộ cache:

```java
@CacheEvict(cacheNames = "products", allEntries = true)
public void invalidateAll() {
}
```

`allEntries = true` có thể xóa rất nhiều dữ liệu và tạo cache miss storm; không dùng tùy tiện trong request path.

Xóa trước khi method chạy:

```java
@CacheEvict(
    cacheNames = "products",
    key = "#id",
    beforeInvocation = true
)
public void riskyUpdate(Long id) { ... }
```

Với `beforeInvocation = true`, key bị xóa kể cả khi method ném exception. Đây là policy khác với xóa sau khi write thành công, nên phải chọn có chủ đích.

---

## 9. `@Caching`

`@Caching` gom nhiều operation:

```java
@Caching(
    put = {
        @CachePut(cacheNames = "products", key = "#result.id")
    },
    evict = {
        @CacheEvict(cacheNames = "search-results", allEntries = true)
    }
)
public Product update(Long id, UpdateProductCommand command) {
    return repository.update(id, command);
}
```

Khi product update, detail cache được cập nhật và search cache bị invalidate. Tuy nhiên, evict toàn bộ search cache có thể đắt; có thể cần TTL ngắn, versioned key hoặc event invalidation thay thế.

---

## 10. `condition` và `unless`

### `condition`

Kiểm tra trước khi method chạy:

```java
@Cacheable(
    cacheNames = "products",
    key = "#id",
    condition = "#id != null && #id > 0"
)
public Product getById(Long id) { ... }
```

Nếu false, cache operation bị bỏ qua nhưng method vẫn chạy bình thường.

### `unless`

Kiểm tra sau khi method chạy; nếu true thì không lưu result:

```java
@Cacheable(
    cacheNames = "products",
    key = "#id",
    unless = "#result == null"
)
public Product getById(Long id) { ... }
```

Không cache list rỗng:

```java
@Cacheable(
    cacheNames = "search-results",
    key = "#query",
    unless = "#result == null || #result.isEmpty()"
)
public List<Product> search(String query) { ... }
```

Tóm tắt:

```text
condition → quyết định trước khi method chạy
unless    → quyết định sau khi method đã có result
```

Điều kiện SpEL nên đơn giản. Logic phức tạp nên đưa vào helper/service để dễ test.

---

## 11. Cache null và negative caching

Nếu database không tìm thấy product, cần quyết định có cache kết quả “không tồn tại” không.

### Cache null/negative result

Ưu điểm:

- Giảm query lặp lại cho ID rác.
- Giảm cache penetration.

Rủi ro:

- Product vừa được tạo có thể bị che bởi negative cache.
- Cần TTL ngắn.
- Provider có behavior khác nhau với null.

### Không cache null

Đơn giản hơn và resource mới có thể được nhìn thấy ngay, nhưng ID không tồn tại liên tục vẫn đánh vào database.

Nếu cache negative result, nên dùng TTL ngắn hơn dữ liệu hợp lệ và evict khi resource được tạo.

Ví dụ cấu hình không lưu null:

```java
RedisCacheConfiguration.defaultCacheConfig()
    .disableCachingNullValues();
```

---

## 12. `@EnableCaching`, proxy và self-invocation

Khi bật `@EnableCaching`, Spring thường tạo proxy quanh bean:

```text
Caller → Spring proxy → cache lookup → target method
```

Vì vậy self-invocation thường không đi qua proxy:

```java
public Product loadForPage(Long id) {
    return getById(id); // gọi nội bộ cùng object
}

@Cacheable(cacheNames = "products", key = "#id")
public Product getById(Long id) { ... }
```

`getById` có thể không được cache.

### Cách nên dùng

Tách cached method thành bean riêng:

```java
@Service
public class CachedProductReader {

    @Cacheable(cacheNames = "products", key = "#id")
    public Product getById(Long id) {
        return repository.findById(id).orElseThrow();
    }
}
```

Bean khác gọi `CachedProductReader` thì lời gọi đi qua proxy. Tách component rõ ràng hơn self-injection.

---

## 13. CacheResolver

`CacheResolver` cho phép chọn cache runtime:

```java
@Cacheable(
    cacheResolver = "tenantCacheResolver",
    key = "#id"
)
public Product getById(Long id) { ... }
```

Dùng khi:

- Cache phụ thuộc tenant.
- Có nhiều CacheManager/provider.
- Cache được chọn theo region hoặc loại request.

Không nên dùng CacheResolver để che giấu naming/key design lộn xộn. Resolver phải deterministic, đơn giản và có test.

`CacheManager` phù hợp với cache routing tĩnh theo name; `CacheResolver` phù hợp với routing động theo runtime context.

---

## 14. RedisCacheManager và TTL

Ví dụ cấu hình TTL theo cache name:

```java
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    RedisCacheManager cacheManager(RedisConnectionFactory factory) {
        RedisCacheConfiguration defaults =
            RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(5))
                .disableCachingNullValues();

        Map<String, RedisCacheConfiguration> caches = Map.of(
            "products", defaults.entryTtl(Duration.ofMinutes(10)),
            "search-results", defaults.entryTtl(Duration.ofSeconds(30)),
            "permissions", defaults.entryTtl(Duration.ofSeconds(15))
        );

        return RedisCacheManager.builder(factory)
            .cacheDefaults(defaults)
            .withInitialCacheConfigurations(caches)
            .build();
    }
}
```

TTL phải phản ánh stale budget, không chỉ là một default tùy tiện. Cache name nên được khai báo tập trung; tránh typo tạo ra cache mới ngoài ý muốn nếu configuration cho phép.

---

## 15. Serialization và DTO cache

Redis cần serialize value. JSON dễ debug và tương thích đa ngôn ngữ, nhưng có thể lớn hơn binary format. JDK serialization tiện nhưng không nên là contract lâu dài tùy tiện.

Không nên cache trực tiếp JPA entity có lazy proxy, quan hệ vòng hoặc dữ liệu nhạy cảm. Dùng DTO cache riêng:

```java
public record ProductCacheValue(
    Long id,
    String name,
    long price
) {
}
```

Lợi ích:

- Kiểm soát payload.
- Tránh serialize persistence proxy.
- Tách cache contract khỏi entity.
- Dễ version hóa schema.

Khi đổi format, dùng versioned cache name/key hoặc kế hoạch invalidate. Serializer phải tương thích giữa các version application đang chạy đồng thời.

---

## 16. Transaction và cache

`@Transactional` và cache annotation không tự tạo distributed transaction giữa database và Redis.

Rủi ro:

```text
Database chưa commit
Cache đã được cập nhật
Transaction rollback
Cache chứa dữ liệu không tồn tại trong database
```

Với cache-aside, flow dễ reasoning thường là:

```text
Update database thành công → evict cache → read sau đó nạp dữ liệu mới
```

Nếu cần event, phát domain event sau commit rồi consumer thực hiện invalidation. Test rõ rollback, retry và failure của lệnh evict.

---

## 17. Cache failure và metrics

Khi Redis timeout, cần policy rõ ràng:

```text
Cache read error → timeout ngắn → fallback database nếu an toàn
```

Cần có log/metric, nhưng không log secret hoặc toàn bộ dữ liệu nhạy cảm. Cân nhắc circuit breaker để Redis outage không làm database bị dồn tải.

Metrics tối thiểu:

- Hit count, miss count, hit ratio.
- Cache get/put/evict latency.
- Serialization error.
- Cache connection/timeout error.
- Database query rate khi fallback.
- Kích thước value và memory usage.

Hit ratio cao không đồng nghĩa hệ thống an toàn nếu stale data, p99 latency hoặc serialization error vẫn cao.

---

## 18. Các lỗi thường gặp

- Quên `@EnableCaching`.
- Key không bao gồm đủ tham số ảnh hưởng đến kết quả.
- Self-invocation khiến annotation không chạy.
- Dùng `@CachePut` nhưng mong method được skip khi hit.
- Dùng `allEntries = true` quá thường xuyên.
- Cache entity JPA thay vì DTO.
- Cache null quá lâu.
- Không đặt TTL cho cache.
- Cập nhật cache trước khi transaction commit.
- Nuốt lỗi Redis mà không có metric/fallback.
- Nghĩ rằng annotation đã giải quyết consistency và invalidation.

---

## 19. Bài thực hành

### Bài 1: Product detail

Tạo `getById` với:

- `@Cacheable` và cache name `products`.
- Key là product ID.
- Không cache ID không hợp lệ.
- Cache DTO thay vì entity.

### Bài 2: Update và delete

Thêm `update` và `delete`. So sánh hai phương án:

```text
@CachePut sau update
Update DB rồi @CacheEvict
```

Test khi repository ném exception và khi Redis evict thất bại.

### Bài 3: Self-invocation

Tạo method `loadForPage` gọi cached method trong cùng class. Kiểm tra behavior, sau đó tách cached reader thành bean riêng.

### Bài 4: Condition/unless

Viết test chứng minh:

- `condition = false`: method chạy nhưng cache không được dùng.
- `unless = true`: method chạy nhưng result không được lưu.

### Bài 5: TTL theo cache name

Cấu hình:

```text
products       10 phút
search-results 30 giây
permissions    15 giây
```

Gọi method rồi kiểm tra TTL trong Redis.

### Bài 6: Cache null

Chọn policy cache hoặc không cache product không tồn tại. Ghi rõ TTL, lý do và cách invalidate khi product được tạo.

---

## 20. Câu hỏi tự kiểm tra

1. Spring Cache abstraction giải quyết vấn đề gì?
2. `@Cacheable` khác `@CachePut` ở điểm nào?
3. `@CacheEvict` xóa một key và toàn bộ cache như thế nào?
4. `condition` và `unless` chạy ở thời điểm nào?
5. Vì sao self-invocation làm cache annotation không hoạt động?
6. `CacheManager` có vai trò gì?
7. Khi nào cần `CacheResolver`?
8. Cache null có lợi ích và rủi ro gì?
9. Vì sao nên cache DTO thay vì JPA entity?
10. Vì sao update cache trong transaction có thể sai khi rollback?
11. Vì sao `allEntries = true` cần thận trọng?
12. Ngoài hit ratio, cần theo dõi metrics nào?

---

## 21. Tiêu chí hoàn thành

Bạn hoàn thành Tuần 5 khi có thể:

- Bật caching và kết nối Redis provider.
- Dùng đúng bốn annotation chính.
- Tạo key ổn định bằng SpEL hoặc key factory.
- Dùng `condition` và `unless` đúng mục đích.
- Giải thích proxy, self-invocation và null caching.
- Cấu hình TTL khác nhau theo cache name.
- Chọn eviction phù hợp với transaction boundary.
- Cache DTO có serialization contract rõ ràng.
- Có timeout, logging, metrics và fallback khi Redis lỗi.
- Viết test cho hit, miss, eviction, exception và serialization.

Kết luận:

> Spring Cache abstraction giúp đưa caching vào method một cách nhất quán, nhưng annotation không thay thế cache design. Vẫn phải quyết định key, TTL, serialization, invalidation, consistency, transaction boundary và behavior khi Redis lỗi.


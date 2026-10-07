# Tuần 6: RedisCacheManager và serialization

## 1. Mục tiêu

Sau bài này, bạn có thể:

- Giải thích vai trò của `RedisCacheManager` trong Spring Cache.
- Cấu hình TTL mặc định và TTL riêng theo cache name.
- Thiết kế key prefix, null value và dynamic cache creation.
- Phân biệt key serializer với value serializer.
- So sánh JSON, JDK serialization và format nhị phân.
- Cache DTO thay vì JPA entity.
- Xử lý schema evolution, rolling deployment và versioned key.
- Chẩn đoán lỗi `SerializationException`, `ClassCastException` và cache value cũ.
- Viết integration test cho Redis cache và serializer.

---

## 2. RedisCacheManager nằm ở đâu?

Flow của Spring Cache với Redis:

```text
@Cacheable
    ↓
Spring Cache interceptor
    ↓
CacheManager
    ↓
RedisCacheManager
    ↓
RedisCache
    ↓
RedisConnectionFactory
    ↓
Redis server
```

`RedisCacheManager` tạo và quản lý cache mà application dùng. Nó cung cấp cấu hình cho:

- Cache name.
- TTL mặc định và TTL riêng.
- Key prefix.
- Key serializer.
- Value serializer.
- Null caching.
- Cách tạo cache chưa khai báo.

Nó không tự giải quyết source of truth, invalidation, consistency, stampede hoặc fallback khi Redis lỗi.

---

## 3. Logical cache name và Redis key thực tế

Trong code:

```java
@Cacheable(cacheNames = "products", key = "#id")
public ProductCacheValue getById(Long id) { ... }
```

`products` là logical cache name. Redis key thực tế thường được tạo từ prefix, cache name và key expression:

```text
shop:prod::products::123
```

Prefix giúp:

- Tách application và environment.
- Tránh collision.
- Dễ quan sát keyspace.
- Dễ migration hoặc cleanup.

Ví dụ:

```text
shop:dev::products::123
shop:prod::products::123
```

Không dùng chung Redis giữa dev và production nếu không có namespace hoặc network boundary rõ ràng. Không hardcode logic application dựa vào format key nội bộ nếu format đó chưa được coi là contract.

---

## 4. Cấu hình RedisCacheManager cơ bản

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

        return RedisCacheManager.builder(factory)
            .cacheDefaults(defaults)
            .build();
    }
}
```

Ý nghĩa:

- Cache mặc định có TTL 5 phút.
- Null value không được lưu.
- Cache không có cấu hình riêng dùng defaults.

Trong production nên cấu hình rõ serializer, prefix, timeout và TTL thay vì phụ thuộc hoàn toàn vào default.

---

## 5. TTL riêng cho từng cache

Các dữ liệu có freshness khác nhau:

```text
products        10 phút
search-results   30 giây
permissions      15 giây
sessions         30 phút
```

Cấu hình:

```java
@Bean
RedisCacheManager cacheManager(RedisConnectionFactory factory) {
    RedisCacheConfiguration defaults =
        RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(5))
            .disableCachingNullValues();

    Map<String, RedisCacheConfiguration> perCache = Map.of(
        "products", defaults.entryTtl(Duration.ofMinutes(10)),
        "search-results", defaults.entryTtl(Duration.ofSeconds(30)),
        "permissions", defaults.entryTtl(Duration.ofSeconds(15)),
        "sessions", defaults.entryTtl(Duration.ofMinutes(30))
    );

    return RedisCacheManager.builder(factory)
        .cacheDefaults(defaults)
        .withInitialCacheConfigurations(perCache)
        .build();
}
```

TTL phải dựa trên stale budget. TTL quá dài làm dữ liệu stale; TTL quá ngắn làm hit ratio thấp và tăng database load.

---

## 6. Key prefix

Có thể cấu hình prefix:

```java
RedisCacheConfiguration defaults =
    RedisCacheConfiguration.defaultCacheConfig()
        .computePrefixWith(name -> "shop:prod::" + name + "::")
        .entryTtl(Duration.ofMinutes(5));
```

Key trở thành:

```text
shop:prod::products::123
```

Prefix nên ngắn, ổn định và chứa application/environment. Không đưa token, password hoặc toàn bộ request vào prefix. Đổi prefix là một dạng migration vì application mới sẽ thấy cache miss hàng loạt.

---

## 7. Dynamic cache creation

Nếu cho phép cache tạo động, typo có thể tạo cache sai:

```java
@Cacheable(cacheNames = "produts") // typo
```

Application có thể tạo cache `produts` bằng TTL mặc định thay vì báo lỗi. Khi muốn fail fast, khai báo cache name và tắt tạo cache khi thiếu:

```java
RedisCacheManager.builder(factory)
    .cacheDefaults(defaults)
    .initialCacheNames(Set.of("products", "search-results"))
    .disableCreateOnMissingCache()
    .build();
```

Cách này yêu cầu mọi cache hợp lệ được khai báo rõ, nhưng giúp phát hiện typo sớm.

---

## 8. Null value và negative caching

### Tắt cache null

```java
RedisCacheConfiguration.defaultCacheConfig()
    .disableCachingNullValues();
```

Ưu điểm:

- Không lưu marker null vào Redis.
- Tránh negative cache tồn tại quá lâu.
- Behavior dễ hiểu.

### Khi nào cache null?

Negative caching có thể giảm query lặp lại cho ID không tồn tại hoặc chống cache penetration. Nhưng cần:

- TTL ngắn.
- Evict khi resource được tạo.
- Theo dõi negative hit.
- Phân biệt “not found” với database error.

Không biến timeout, exception hoặc lỗi authorization thành kết quả null. Nếu cache negative result, hãy coi nó là một data policy riêng.

---

## 9. Serialization là gì?

Serialization chuyển object Java thành format có thể lưu vào Redis:

```text
Java object → bytes/string → Redis
Redis → bytes/string → Java object
```

Serializer phải trả lời:

- Instance version mới có đọc được value cũ không?
- Service khác ngôn ngữ có đọc được không?
- Payload có quá lớn không?
- Type metadata có an toàn không?
- Schema thay đổi thế nào?
- Debug raw value có dễ không?

Serialization là một phần của cache data contract, không chỉ là chi tiết implementation.

---

## 10. Key serializer và value serializer

Key và value có thể dùng serializer khác nhau:

```java
RedisCacheConfiguration.defaultCacheConfig()
    .serializeKeysWith(
        SerializationPair.fromSerializer(
            new StringRedisSerializer()
        )
    )
    .serializeValuesWith(
        SerializationPair.fromSerializer(
            new GenericJackson2JsonRedisSerializer()
        )
    );
```

Key thường nên là String để dễ quan sát:

```text
shop:prod::products::123
```

Value cần serializer ổn định giữa mọi instance đang chạy. Nếu instance A ghi format mà instance B không đọc được, rolling deployment sẽ lỗi.

---

## 11. JSON serialization

Ví dụ JSON:

```json
{
  "id": 123,
  "name": "Keyboard",
  "price": 1200000
}
```

### Ưu điểm

- Dễ debug trong RedisInsight.
- Tương thích với service khác ngôn ngữ.
- Schema dễ quan sát.
- Thêm field mới tương đối mềm dẻo.

### Hạn chế

- Payload thường lớn hơn binary.
- Có chi phí serialize/deserialize.
- Cần cấu hình date/time và naming.
- Polymorphic type cần kiểm soát.

JSON phù hợp với cache DTO và API response có kích thước hợp lý.

---

## 12. JDK serialization

JDK serialization lưu object Java dạng binary. Nó tiện cho một số hệ thống cũ nhưng không nên là cache contract lâu dài nếu có thể tránh.

Rủi ro:

- Khó debug.
- Phụ thuộc class và package name.
- Đổi class có thể làm deserialization fail.
- Không thuận tiện cho service khác ngôn ngữ.
- Cần đánh giá security khi deserialize dữ liệu không tin cậy.

Nếu cache tồn tại qua deploy hoặc được nhiều service dùng chung, JSON hoặc format có schema thường dễ quản lý hơn.

---

## 13. Type metadata và generic JSON serializer

Một số serializer thêm type metadata để biết class cần tạo khi deserialize. Điều này thuận tiện cho polymorphic object nhưng làm cache phụ thuộc vào class type.

Cần kiểm soát:

- Type nào được phép deserialize.
- Không deserialize type lấy từ input không tin cậy.
- Không cache implementation class không ổn định.
- Có kế hoạch khi đổi package hoặc tên class.

Với cache thông thường, DTO cụ thể và JSON contract rõ ràng thường dễ kiểm soát hơn việc serialize mọi subtype tùy ý.

---

## 14. Cache DTO thay vì JPA entity

Không nên mặc định cache entity:

```java
@Cacheable("products")
public ProductEntity getById(Long id) { ... }
```

Entity có thể chứa lazy proxy, quan hệ vòng, field nhạy cảm, persistence state và nhiều field thừa.

Dùng DTO cache:

```java
public record ProductCacheValue(
    Long id,
    String name,
    long price,
    String currency
) {
}
```

```java
@Cacheable(cacheNames = "products", key = "#id")
public ProductCacheValue getById(Long id) {
    ProductEntity entity = repository.findById(id).orElseThrow();

    return new ProductCacheValue(
        entity.getId(),
        entity.getName(),
        entity.getPrice(),
        entity.getCurrency()
    );
}
```

DTO cache giúp:

- Kiểm soát payload.
- Tránh serialize proxy.
- Tách cache schema khỏi database schema.
- Dễ version hóa.
- Giảm nguy cơ lộ field không nên cache.

---

## 15. Schema evolution

Cache có thể tồn tại qua nhiều version application. Khi đổi schema:

### Thêm field

Reader mới phải chịu được value cũ thiếu field và dùng default phù hợp.

### Đổi tên hoặc xóa field

Cần kiểm tra version cũ trong rolling deployment. Version cũ và mới có thể cùng đọc cache.

### Thay đổi lớn

Dùng versioned cache name hoặc key:

```text
products:v1::123
products:v2::123
```

Hoặc:

```text
cache:v1:products:123
cache:v2:products:123
```

Key mới tạo cache miss có kiểm soát. Key cũ có thể được thu hồi bằng TTL hoặc cleanup tool dùng `SCAN`.

Không flush toàn Redis nếu Redis chứa nhiều domain.

---

## 16. Rolling deployment

Trong lúc deploy:

```text
Instance v1 vẫn đọc/ghi cache
Instance v2 bắt đầu đọc/ghi cache
```

Các lỗi có thể xảy ra:

- v2 ghi field mà v1 không hiểu.
- v1 ghi format mà v2 không đọc được.
- Cùng key nhưng hai version hiểu khác meaning.

Chiến lược an toàn:

1. Làm reader mới tương thích format cũ.
2. Deploy reader.
3. Chuyển writer sang format mới.
4. Thu hồi format cũ sau khi mọi instance đã nâng cấp.

Hoặc dùng versioned key để tách hoàn toàn hai format.

---

## 17. Cấu hình hoàn chỉnh minh họa

```java
@Configuration
@EnableCaching
public class RedisCacheConfig {

    @Bean
    RedisCacheConfiguration defaults() {
        return RedisCacheConfiguration.defaultCacheConfig()
            .computePrefixWith(name -> "shop:prod::" + name + "::")
            .serializeKeysWith(
                SerializationPair.fromSerializer(
                    new StringRedisSerializer()
                )
            )
            .serializeValuesWith(
                SerializationPair.fromSerializer(
                    new GenericJackson2JsonRedisSerializer()
                )
            )
            .entryTtl(Duration.ofMinutes(5))
            .disableCachingNullValues();
    }

    @Bean
    RedisCacheManager cacheManager(
        RedisConnectionFactory factory,
        RedisCacheConfiguration defaults
    ) {
        Map<String, RedisCacheConfiguration> configurations = Map.of(
            "products", defaults.entryTtl(Duration.ofMinutes(10)),
            "search-results", defaults.entryTtl(Duration.ofSeconds(30)),
            "permissions", defaults.entryTtl(Duration.ofSeconds(15))
        );

        return RedisCacheManager.builder(factory)
            .cacheDefaults(defaults)
            .withInitialCacheConfigurations(configurations)
            .build();
    }
}
```

Đây là cấu hình minh họa. Hệ thống có contract chặt có thể dùng serializer riêng cho từng cache name.

---

## 18. Transaction và cache

Redis và database thường không nằm trong cùng distributed transaction.

Rủi ro:

```text
Database chưa commit
Cache đã được cập nhật
Transaction rollback
Cache chứa dữ liệu không tồn tại trong database
```

Với cache-aside, flow dễ reasoning:

```text
Update database thành công → evict cache → read sau đó nạp dữ liệu mới
```

Nếu dùng event, phát domain event sau commit rồi consumer thực hiện invalidation. Test rõ rollback, retry và lỗi Redis.

---

## 19. Lỗi serialization thường gặp

### `SerializationException`

Nguyên nhân:

- Raw value khác serializer hiện tại.
- Class không còn tồn tại.
- JSON field không map được.
- Date/time module chưa cấu hình.
- Byte array bị đọc như String.

### `ClassCastException`

Application mong DTO nhưng cache đang chứa entity, Map hoặc format cũ.

### `MismatchedInputException`

JSON có shape khác DTO: field sai type, object null hoặc cấu trúc đã đổi.

### Cách xử lý

1. Kiểm tra key và raw value trong Redis.
2. Kiểm tra serializer ở mọi instance.
3. Xác định cache version.
4. Fallback đọc source of truth nếu an toàn.
5. Xóa key hỏng và nạp lại.
6. Thêm compatibility test.
7. Không catch rồi trả dữ liệu sai im lặng.

Cache là bản sao, nên xóa value hỏng và rebuild từ database thường an toàn hơn hack deserializer.

---

## 20. Payload lớn và compression

Payload lớn gây:

- Memory usage cao.
- Network latency.
- CPU serialization cao.
- Eviction pressure.

Ưu tiên:

- Cache DTO nhỏ.
- Bỏ field không cần.
- Chia cache theo access pattern.
- Giới hạn kích thước value.
- Compression chỉ khi đã đo CPU và latency.

Compression giảm bytes nhưng tăng CPU và làm debug khó. Không bật mặc định chỉ vì payload nhìn có vẻ lớn.

---

## 21. Bảo mật

Không cache tùy tiện:

- Password.
- Access token dài hạn.
- Secret key.
- Dữ liệu cá nhân không cần thiết.
- Thông tin thanh toán nhạy cảm.

Cần có:

- TTL phù hợp.
- Network access control.
- Authentication/TLS khi cần.
- Log không lộ raw value.
- Xóa cache khi revoke/logout.
- Policy rõ khi user bị disable hoặc quyền thay đổi.

Serializer an toàn không biến dữ liệu nhạy cảm thành dữ liệu không nhạy cảm.

---

## 22. Kiểm tra Redis cache

Với JSON String:

```text
GET shop:prod::products::123
TTL shop:prod::products::123
TYPE shop:prod::products::123
```

Nếu value là binary, `GET` có thể khó đọc. Dùng RedisInsight hoặc tool debug phù hợp, nhưng raw cache value không nên trở thành API contract.

Quét key bằng `SCAN`, không dùng `KEYS` trong production:

```text
SCAN 0 MATCH shop:prod::products::* COUNT 100
```

---

## 23. Integration test

Unit test annotation chưa đủ để phát hiện lỗi serializer. Nên có integration test với Redis thật hoặc container.

Cần test:

- Cache miss gọi repository.
- Cache hit không gọi repository lần hai.
- DTO deserialize đúng.
- TTL được đặt đúng.
- Evict xóa đúng key.
- Null policy đúng.
- Value format cũ được xử lý đúng.
- Redis timeout có fallback phù hợp.
- Hai instance đọc cùng format.
- Corrupted value không làm API trả dữ liệu sai.

Ví dụ:

```java
@Test
void cacheHitShouldAvoidSecondRepositoryCall() {
    service.getById(123L);
    service.getById(123L);

    verify(repository, times(1)).findById(123L);
}
```

Test trên kiểm tra behavior nhưng chưa đủ xác minh serializer; cần test với Redis provider thật.

---

## 24. Bài thực hành

### Bài 1: Cấu hình manager

Cấu hình:

```text
products        10 phút
search-results   30 giây
permissions      15 giây
```

Prefix:

```text
shop:dev::<cache-name>::
```

### Bài 2: JSON DTO

Tạo `ProductCacheValue`, cấu hình JSON serializer và kiểm tra raw value trong Redis.

### Bài 3: Schema evolution

Tạo v1 và v2 của DTO. Thử thêm field, đổi tên field và chuyển từ `products` sang `products:v2`. Ghi lại behavior khi đọc key cũ.

### Bài 4: Rolling deployment

Mô phỏng hai serializer hoặc hai DTO version dùng chung Redis. Sau đó sửa bằng versioned key.

### Bài 5: Corrupted cache

Ghi raw value không hợp lệ vào Redis. Xác định exception, log/metric, fallback và cách xóa/rebuild key.

---

## 25. Câu hỏi tự kiểm tra

1. RedisCacheManager nằm ở đâu trong flow của Spring Cache?
2. Logical cache name khác Redis key thực tế thế nào?
3. Vì sao mỗi cache nên có TTL riêng?
4. Key serializer và value serializer khác nhau thế nào?
5. Vì sao JSON thường dễ vận hành hơn JDK serialization?
6. Tại sao không nên cache JPA entity tùy tiện?
7. Schema evolution ảnh hưởng cache thế nào?
8. Khi nào dùng versioned cache key?
9. Vì sao rolling deployment có thể làm deserialize fail?
10. Tại sao không nên flush toàn Redis khi đổi serializer?
11. Cache null có lợi ích và rủi ro gì?
12. Cần test gì ngoài việc method trả đúng result?

---

## 26. Tiêu chí hoàn thành

Bạn hoàn thành Tuần 6 khi có thể:

- Cấu hình RedisCacheManager với default TTL và TTL riêng.
- Đặt key prefix theo application/environment.
- Quyết định dynamic cache creation có được phép hay không.
- Cấu hình key serializer và value serializer.
- Cache DTO JSON thay vì entity.
- Giải thích schema evolution và rolling deployment.
- Dùng versioned cache key cho migration lớn.
- Chẩn đoán lỗi deserialize và value cũ.
- Viết integration test với Redis thật.
- Định nghĩa policy cho null, payload lớn, dữ liệu nhạy cảm và fallback.

Kết luận:

> RedisCacheManager quyết định cách Spring tạo và cấu hình cache; serialization quyết định dữ liệu được biểu diễn và tồn tại qua các version application như thế nào. Cấu hình production-ready phải kiểm soát TTL, prefix, serializer, schema evolution, null value, security và khả năng phục hồi khi cache value lỗi.


# Tuần 16: Testing, security và capstone

## 1. Mục tiêu

Sau bài này, bạn có thể:

- Viết unit test cho cache key, TTL, hit/miss và invalidation.
- Viết integration test với Redis thật bằng Testcontainers.
- Kiểm tra serialization compatibility và cache value contract.
- Test concurrency, stampede, lock và duplicate side effect.
- Test Redis failure, timeout, reconnect và failover.
- Thiết kế authentication, ACL, TLS, network isolation và secret management.
- Xây capstone Product Platform production-like.
- Đo latency, hit ratio, memory, p95/p99 và duplicate side effect.
- Viết cache decision record, runbook và backup/restore guide.

---

## 2. Vì sao testing cache khó?

Cache có nhiều state và timing:

~~~text
Database state
Redis state
TTL/expiration
Application instance
Concurrency
Network
Serializer version
Failover
~~~

Một test chỉ gọi method một lần không phát hiện được:

- Self-invocation.
- Cache hit không skip database.
- TTL bị mất sau update.
- Hai request cùng rebuild.
- Redis timeout làm database quá tải.
- Serializer version mới không đọc value cũ.
- Failover làm retry duplicate payment.
- Eviction xóa session/idempotency record.

Testing cần kiểm tra cả happy path, failure path, concurrency và operational behavior.

---

## 3. Test pyramid cho Redis application

### Unit test

Kiểm tra logic thuần:

- Cache key factory.
- TTL calculation.
- Jitter.
- Request hash.
- Version comparison.
- Rate limit decision.
- Lock token.
- Event handler idempotency.

### Integration test

Kiểm tra với Redis thật:

- Serializer.
- TTL.
- Data structure command.
- Lua script.
- Pipeline.
- Transaction.
- Stream consumer group.
- Wrong type/error.

### Contract test

Kiểm tra cache value contract:

- Field bắt buộc.
- Format JSON.
- Version.
- Backward/forward compatibility.
- Null/default behavior.

### Concurrency test

Kiểm tra:

- Nhiều request cùng cache miss.
- Lock ownership.
- Rate limit atomicity.
- Duplicate idempotency key.
- Concurrent leaderboard updates.
- Stream redelivery.

### Failure test

Kiểm tra:

- Redis down.
- Timeout.
- Connection reset.
- Reconnect.
- Primary failover.
- Replica lag.
- Corrupted cache value.
- Database rollback.

Không cần mọi test đều end-to-end; chọn tầng test phù hợp để feedback nhanh và vẫn có evidence đáng tin.

---

## 4. Unit test cache key

Key phải deterministic và không collision.

Ví dụ key factory:

~~~java
public final class CacheKeys {

    private CacheKeys() {
    }

    public static String product(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Invalid product id");
        }

        return "shop:product:" + id;
    }
}
~~~

Test:

~~~java
@Test
void productKeyShouldBeDeterministic() {
    assertThat(CacheKeys.product(123L))
        .isEqualTo("shop:product:123");
}

@Test
void productKeyShouldRejectInvalidId() {
    assertThatThrownBy(() -> CacheKeys.product(0L))
        .isInstanceOf(IllegalArgumentException.class);
}
~~~

Cần test:

- Cùng input tạo cùng key.
- Resource khác không collision.
- Environment prefix đúng.
- Version prefix đúng.
- Input null/âm/empty.
- Không chứa secret.
- Search key bao gồm đủ query/filter/page/sort.

---

## 5. Unit test TTL

TTL nên được tính từ policy rõ ràng:

~~~java
public Duration productTtl() {
    return Duration.ofMinutes(10);
}

public Duration negativeTtl() {
    return Duration.ofSeconds(30);
}
~~~

Test:

~~~java
@Test
void productTtlShouldMatchPolicy() {
    assertThat(policy.productTtl())
        .isEqualTo(Duration.ofMinutes(10));
}
~~~

Nếu có jitter:

~~~java
@Test
void ttlJitterShouldStayWithinRange() {
    Duration ttl = policy.ttlWithJitter(
        Duration.ofMinutes(5),
        Duration.ofSeconds(60)
    );

    assertThat(ttl)
        .isBetween(
            Duration.ofMinutes(5),
            Duration.ofMinutes(6)
        );
}
~~~

Không test TTL bằng sleep dài nếu không cần. Test hàm tính TTL riêng; integration test mới kiểm tra Redis expiration thật.

---

## 6. Unit test cache hit/miss

Với service cache-aside, mock cache/repository để kiểm tra flow:

### Cache hit

~~~text
Cache trả value
Repository không được gọi
Service trả value
~~~

### Cache miss

~~~text
Cache trả null
Repository được gọi một lần
Cache được set với TTL
Service trả value
~~~

### Not found

~~~text
Cache miss
Repository không có data
Negative cache theo policy hoặc không cache
Response 404
~~~

Test cần verify:

- Repository call count.
- Cache get/set/delete.
- Key truyền vào.
- TTL truyền vào.
- Không cache exception nhầm thành NOT_FOUND.
- Fallback khi cache read error.

---

## 7. Integration test với Testcontainers Redis

Testcontainers chạy Redis thật trong test:

~~~java
@Testcontainers
@SpringBootTest
class RedisIntegrationTest {

    @Container
    static GenericContainer<?> redis =
        new GenericContainer<>("redis:7.4")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(
        DynamicPropertyRegistry registry
    ) {
        registry.add(
            "spring.data.redis.host",
            redis::getHost
        );
        registry.add(
            "spring.data.redis.port",
            () -> redis.getMappedPort(6379)
        );
    }
}
~~~

Trong project thật nên pin version Redis thay vì dùng tag không cố định.

Integration test nên kiểm tra:

- Connection.
- Key/value serializer.
- TTL.
- Hash/List/Set/ZSet/Stream.
- Lua script.
- Pipeline.
- Transaction.
- Wrong data type.
- Redis restart/reconnect ở test phù hợp.

Không mock Redis khi mục tiêu là xác minh behavior thực tế của Redis.

---

## 8. Test serialization compatibility

Cache value thường tồn tại qua deploy. Tạo fixture đại diện value version cũ:

~~~json
{
  "id": 123,
  "name": "Keyboard",
  "price": 1200000
}
~~~

Test version mới đọc được:

~~~java
@Test
void newDtoShouldReadOldCacheValue() {
    String oldJson = """
        {
          "id": 123,
          "name": "Keyboard",
          "price": 1200000
        }
        """;

    ProductCacheValue result =
        mapper.readValue(oldJson, ProductCacheValue.class);

    assertThat(result.id()).isEqualTo(123L);
}
~~~

Cần test:

- Thêm field mới có default.
- Field missing.
- Unknown field.
- Null field.
- Đổi type.
- Đổi tên field.
- Date/time format.
- Versioned key.
- Value hỏng.
- Serializer khác giữa v1/v2.

Nếu breaking change không thể tương thích, dùng cache version mới và chấp nhận cold miss có kiểm soát.

---

## 9. Contract test cho cache value

Cache contract nên ghi rõ:

~~~text
Cache name:
Key format:
Value format:
Required fields:
Optional fields:
Version:
TTL:
Null policy:
Serializer:
Migration policy:
~~~

Contract test giúp nhiều service không ghi value sai schema.

Ví dụ required:

~~~java
@Test
void productCacheContractShouldContainRequiredFields() {
    ProductCacheValue value = fixture.product();

    assertThat(value.id()).isNotNull();
    assertThat(value.name()).isNotBlank();
    assertThat(value.price()).isGreaterThanOrEqualTo(0);
}
~~~

Không để cache value phụ thuộc ngầm vào JPA proxy hoặc object implementation khó ổn định.

---

## 10. Concurrency test

### Cache stampede

Tạo N thread cùng đọc key chưa có:

~~~text
N request cache miss
Một loader database
N response cùng nhận value
~~~

Assert:

- Database query count gần bằng 1.
- Không có exception ngẫu nhiên.
- Không có value sai.
- Lock/single-flight được release.
- Future không bị treo.

### Rate limit

N thread cùng increment counter. Assert:

- Counter không mất update.
- Số request allowed không vượt policy.
- TTL được đặt.
- Window không bị kéo dài sai.

### Idempotency

N request cùng idempotency key. Assert:

- Một side effect database.
- Các request còn lại nhận cùng result hoặc trạng thái đang xử lý.
- Payload khác cùng key bị reject.
- Retry sau response loss không tạo duplicate.

### Leaderboard

N thread cùng tăng score một player. Assert tổng score đúng.

Test concurrency nên có barrier để các thread bắt đầu gần đồng thời, nhưng tránh sleep tùy tiện làm test flaky.

---

## 11. Failure và reconnect test

Mô phỏng:

- Redis container stop.
- Redis port timeout.
- Connection reset.
- Redis restart.
- Application reconnect.
- Command đang chạy bị lỗi.
- Database fallback.
- Circuit breaker mở/đóng.

Kiểm tra:

- API timeout có bounded không.
- Exception có bị nuốt không.
- Database có bị dồn tải không.
- Cache miss và Redis failure có được phân biệt không.
- Request retry có idempotent không.
- Metrics/log có evidence không.
- Sau Redis phục hồi, cache rebuild có tạo stampede không.

Một test tốt kiểm tra cả khi Redis down trong lúc read và trong lúc write/invalidation.

---

## 12. Test failover

Với Sentinel/Cluster lab:

- Stop primary.
- Chờ replica promote.
- Kiểm tra client refresh topology.
- Gọi read/write trong failover window.
- Kiểm tra command timeout.
- Kiểm tra retry.
- Kiểm tra duplicate side effect.
- Kiểm tra replication lag và write loss window.

Không assert rằng mọi request đều thành công trong failover; assert behavior đúng policy đã định.

---

## 13. Security threat model

Các tài sản cần bảo vệ:

- Session.
- Access/refresh token.
- Payment idempotency record.
- User data.
- Cache value.
- Redis credentials.
- Backup/RDB/AOF files.
- Network traffic.

Threats:

- Redis exposed Internet.
- Credential leak.
- Unauthorized command.
- Data exfiltration.
- Plaintext token trong log.
- Cross-tenant key collision.
- Malicious oversized key/value.
- Untrusted deserialization.
- Backup bị truy cập trái phép.
- Lateral movement trong network.

Security design phải bắt đầu từ asset và threat, không chỉ bật một password.

---

## 14. Không expose Redis trực tiếp ra Internet

Redis nên nằm trong private network:

~~~text
Internet → Load balancer/API
             ↓
          Application
             ↓ private network
           Redis
~~~

Không mở port Redis public. Dùng:

- Private subnet/VPC.
- Security group/firewall.
- Network policy.
- Allowlist source.
- Bastion/tunnel cho admin.
- TLS nếu traffic qua network không tin cậy.

Kiểm tra firewall bằng deployment/lab scan phù hợp. Không dùng Redis port public chỉ vì local test tiện.

---

## 15. Authentication và ACL

Dùng authentication/ACL phù hợp với Redis version/deployment.

Nguyên tắc least privilege:

~~~text
Service cache:
  được GET/SET/DEL/EXPIRE cần thiết
  không được CONFIG/SHUTDOWN/FLUSHALL

Service stream:
  được XADD/XREADGROUP/XACK cần thiết
  không cần quyền admin
~~~

Tách user Redis theo service hoặc workload nếu có thể. Không dùng một admin credential trong mọi application.

ACL cần kiểm tra:

- Username/password.
- Command allowlist.
- Key pattern allowlist.
- Admin command bị cấm.
- Rotation.
- Audit/monitoring.
- Break-glass access.

---

## 16. TLS

Dùng TLS khi traffic đi qua network không tin cậy hoặc requirement bảo mật yêu cầu.

Cần xác minh:

- Server certificate validation.
- CA trust.
- Hostname verification.
- Client certificate nếu mTLS.
- Certificate rotation.
- Connection pool reconnect sau rotation.
- Không tắt certificate validation để “sửa nhanh”.

TLS tăng CPU/latency nhỏ; đo và cấu hình timeout phù hợp. Không truyền password/token qua channel không được bảo vệ.

---

## 17. Secret management

Không hardcode:

- Redis password.
- TLS private key.
- Connection URI có credential.
- Cloud token.
- Backup encryption key.

Dùng:

- Environment injection từ secret manager.
- Vault/KMS/cloud secret service.
- Rotation policy.
- Secret scanning.
- Không ghi secret vào log/trace/error.

Nếu secret từng commit hoặc lộ trong output:

1. Rotate ngay.
2. Kiểm tra audit.
3. Tìm nơi khác bị lặp.
4. Xóa khỏi history theo quy trình.
5. Cập nhật deployment.

---

## 18. Network isolation và data separation

Tách Redis theo:

- Environment.
- Application/domain.
- Sensitivity.
- Eviction policy.
- Availability requirement.

Ví dụ:

~~~text
Redis cache:
  allkeys-LRU
  data rebuild được

Redis critical:
  noeviction hoặc policy riêng
  session/idempotency/stream cần bảo vệ
~~~

Không trộn cache có thể mất với dữ liệu quan trọng chỉ để tiết kiệm một instance nếu failure behavior khác nhau.

---

## 19. Input validation và abuse protection

Validate tại boundary:

- Session ID length/format.
- Idempotency key length/charset.
- User ID/tenant ID.
- Page size.
- Query length.
- Rate limit parameters không lấy tùy tiện từ client.
- Stream payload size.
- Hash field count.
- List batch size.

Giới hạn payload để tránh:

- Memory exhaustion.
- Big key.
- Slow serialization.
- Oversized Stream event.
- Cardinality explosion trong metrics.

Không đưa input raw vào key nếu chưa normalize và giới hạn.

---

## 20. Serialization security

Không deserialize object không tin cậy tùy tiện. Ưu tiên:

- DTO cụ thể.
- JSON schema rõ.
- Allowed type list.
- Không lưu entity/proxy.
- Field validation.
- Versioned cache contract.
- Không serialize secret.

Raw Redis value có thể bị sửa bởi operator, bug hoặc attacker có access. Application phải coi value là dữ liệu bên ngoài và validate/deserialization an toàn.

---

## 21. Backup và restore

Nếu Redis chứa dữ liệu không thể tái tạo, cần:

- RDB/AOF policy.
- Backup storage riêng.
- Encryption at rest.
- Access control.
- Retention.
- Cross-zone/region copy nếu cần.
- Restore drill định kỳ.
- RPO/RTO rõ.

Backup không đủ nếu chưa từng restore thử.

Runbook restore:

~~~text
1. Xác định incident và scope
2. Chọn backup đúng thời điểm
3. Restore vào môi trường cô lập
4. Validate key/value/schema
5. Kiểm tra application compatibility
6. Cut over có kế hoạch
7. Theo dõi stale/duplicate/missing data
~~~

Không restore đè production mà chưa validate backup.

---

## 22. Capstone: Production-ready Product Platform

Xây một Spring Boot service có các chức năng:

### Product detail

- Cache-aside.
- RedisTemplate hoặc Spring Cache.
- TTL rõ.
- DTO cache.
- Fallback database.
- Metrics hit/miss.

### Search result

- TTL ngắn.
- Versioned key.
- Query normalization.
- Giới hạn page/filter.
- Invalidation hoặc eventual consistency rõ.

### Product update

- Database transaction.
- Invalidation sau commit.
- Outbox nếu cần reliability.
- Version hoặc event.

### Session

- Redis Hash.
- TTL.
- Idle/absolute timeout.
- Logout/revoke.

### Rate limit

- Sorted Set hoặc Lua.
- Theo user và IP đáng tin cậy.
- Metrics allowed/blocked.
- Fallback policy.

### Create order

- Idempotency key.
- Request hash.
- Database unique constraint.
- Duplicate request trả result cũ.
- Payload khác cùng key trả conflict.

### Hot cache rebuild

- Distributed lock.
- Lock token.
- Safe release.
- Double-check.
- TTL.
- Stale-while-revalidate nếu phù hợp.

### Notifications

- Pub/Sub cho WebSocket update.

### Audit

- Redis Stream cho asynchronous audit events.
- Consumer group.
- XACK sau side effect.
- Pending/retry/dead-letter.

### HA lab

- Sentinel hoặc Cluster trong môi trường lab.
- Client reconnect.
- Failover test.
- Không cần production-grade cloud deployment nếu chưa có hạ tầng.

---

## 23. Capstone architecture

~~~text
Client
  ↓
Spring Boot API
  ├─ Product service
  ├─ Search service
  ├─ Session service
  ├─ Rate limit service
  ├─ Order/idempotency service
  └─ Audit producer
       ↓
Redis
  ├─ Cache keys
  ├─ Sessions
  ├─ Rate limit keys
  ├─ Idempotency records
  ├─ Locks
  ├─ Pub/Sub
  └─ Streams
       ↓
PostgreSQL/source of truth
~~~

Mỗi loại key phải có namespace, TTL, serializer, owner và failure policy rõ ràng.

---

## 24. Capstone measurement plan

### Latency

So sánh:

- Không cache.
- Cold cache.
- Warm cache.
- Redis timeout.
- Database fallback.

Đo p50/p95/p99.

### Hit ratio

Theo endpoint:

~~~text
GET /products/{id}
GET /search
GET /permissions
~~~

### Stampede

Mô phỏng 100 request cùng lúc khi key hết hạn. Đo:

- Database query count.
- Lock contention.
- Rebuild count.
- p95/p99.

### Memory

Đo:

- Used memory.
- Key count.
- Average value size.
- Big key.
- Evicted/expired.
- Fragmentation.

### Correctness

Kiểm tra:

- Duplicate payment/order.
- Read-after-write.
- Stale data window.
- Idempotency conflict.
- Event redelivery.
- Lock owner mismatch.

### Failover

Kiểm tra:

- Redis reconnect.
- Sentinel/Cluster failover.
- Command retry.
- Error rate.
- Data loss window.
- Duplicate side effect.

---

## 25. Capstone deliverables

### Cache decision record

Với mỗi data type:

~~~text
Name:
Source of truth:
Read/write ratio:
Acceptable staleness:
Strategy:
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

### Key naming document

Ghi:

- Application.
- Environment.
- Domain/resource.
- Identifier.
- Version.
- TTL.
- Sensitive data policy.
- Cleanup policy.

### TTL matrix

Ví dụ:

| Data | TTL | Reason |
|---|---:|---|
| Product detail | 10 phút | Mô tả stale ngắn |
| Search result | 30 giây | Index eventual |
| Session | 30 phút idle | Security |
| Negative product | 30 giây | Chống penetration |
| Idempotency | 24 giờ | Retry window |
| Lock | 5 giây | Lease ngắn |

### Invalidation matrix

Ghi create/update/delete ảnh hưởng key nào:

~~~text
Product update
  → product detail
  → category list
  → search version
  → recommendation event
~~~

### Serialization/version policy

Ghi:

- Serializer.
- DTO.
- Field compatibility.
- Cache version.
- Rolling deployment strategy.
- Corrupted value behavior.
- Migration/cleanup.

### Docker Compose

Bao gồm:

- Application.
- Redis.
- Database.
- RedisInsight tùy chọn.
- Sentinel/Cluster lab tùy scope.
- Healthcheck.
- Version pinning.
- Network isolation.

### Tests

Bao gồm:

- Unit.
- Integration với Testcontainers.
- Serialization compatibility.
- Concurrency.
- Failure/reconnect.
- Contract.
- Load smoke test.

### Observability

Bao gồm:

- Dashboard.
- Metrics.
- Traces.
- Logs.
- Alerts.
- Correlation ID.

### Runbooks

Bao gồm:

- Redis unavailable.
- Memory pressure.
- Eviction spike.
- Hit ratio drop.
- Replication lag.
- Failover.
- Backup restore.
- Corrupted cache value.
- Stream pending backlog.

### README

README phải chạy được từ đầu:

~~~text
1. Prerequisites
2. Start dependencies
3. Configure environment
4. Run migration
5. Start application
6. Run tests
7. Run load test
8. Open dashboard
9. Simulate failure
10. Restore environment
~~~

---

## 26. Security checklist

- Redis không expose trực tiếp Internet.
- Có authentication/ACL.
- Service chỉ có command/key permission cần thiết.
- TLS khi network không tin cậy.
- Secret dùng secret manager.
- Không log session/token/payment payload.
- Key không chứa secret.
- Input được validate và giới hạn.
- Cache và critical data được tách khi cần.
- Serializer không deserialize type không tin cậy.
- Backup được mã hóa và giới hạn access.
- Restore đã được kiểm thử.
- Có rotation credential/certificate.
- Có network isolation.
- Có audit access admin.
- Có rate limit cho endpoint nhạy cảm.

---

## 27. Definition of done

Capstone hoàn thành khi:

- Chức năng product/cache/session/rate limit/idempotency hoạt động.
- Invalidation và consistency policy được viết rõ.
- Redis failure có behavior đã test.
- Duplicate side effect được ngăn bằng idempotency/DB constraint.
- Hot key rebuild không tạo hàng trăm database query.
- Stream consumer xử lý retry/pending.
- Metrics và dashboard hiển thị được trạng thái.
- Memory/eviction được đo.
- Sentinel hoặc Cluster lab failover được kiểm tra.
- Security checklist không còn mục critical chưa xử lý.
- Backup/restore runbook đã chạy thử.
- README người khác có thể dùng để dựng và kiểm tra project.

---

## 28. Checklist sau 16 tuần

### Caching

- Chọn đúng strategy.
- Có TTL và invalidation.
- Xử lý stampede, penetration và avalanche.
- Biết cache nào được phép stale.
- Không cache dữ liệu nhạy cảm tùy tiện.

### Redis

- Chọn đúng data structure.
- Thiết kế key namespace.
- Dùng pipeline/Lua khi cần atomicity.
- Tránh KEYS và big key.
- Biết eviction và persistence.
- Hiểu replication, Sentinel và Cluster.

### Spring Boot

- Dùng Spring Cache.
- Cấu hình RedisCacheManager.
- Dùng RedisTemplate.
- Chọn serializer an toàn.
- Test với Testcontainers.
- Có metrics/tracing.

### Distributed systems

- Hiểu eventual/strong consistency.
- Có idempotency.
- Có safe lock/coordination.
- Có retry/backoff.
- Có failure/runbook.
- Không dựa vào Redis lock để thay transaction database.

---

## 29. Câu hỏi tự kiểm tra cuối khóa

1. Khi nào dùng Spring Cache và khi nào dùng RedisTemplate?
2. Cache-aside update nên ghi DB/cache theo thứ tự nào?
3. Redis unavailable thì product, permission và payment nên khác nhau ra sao?
4. Cache stampede và avalanche khác nhau thế nào?
5. Vì sao idempotency payment cần database constraint?
6. Vì sao Stream consumer phải idempotent?
7. Khi nào Sentinel đủ và khi nào cần Cluster?
8. RDB/AOF có thay backup database không?
9. Metrics nào chứng minh cache đang làm database nhẹ hơn?
10. Làm sao kiểm tra serializer tương thích qua rolling deployment?
11. Lock TTL hết hạn khi owner còn chạy thì rủi ro gì?
12. Security boundary của Redis nằm ở network, ACL, serializer và secret như thế nào?
13. Nếu hit ratio cao nhưng API vẫn chậm, điều tra gì?
14. Nếu memory đầy và eviction tăng, quyết định đầu tiên là gì?
15. Deliverable nào giúp team vận hành cache sau khi bạn rời project?

Kết luận:

> Một hệ thống cache production-ready không chỉ có Redis và vài annotation. Nó cần strategy, consistency policy, test của failure/concurrency, security boundary, observability, backup/recovery và runbook. Capstone là bài kiểm tra khả năng nối tất cả các phần đó thành một hệ thống có thể đo, kiểm thử và vận hành được.


# Tuần 15 — Testing, contract test và chaos engineering

> Một hệ thống RabbitMQ chỉ đáng tin khi test chứng minh được cả đường đi thành công lẫn các trạng thái khó chịu: publish không route, confirm chưa về, consumer crash trước ack, database timeout, broker unavailable và message bị redeliver.

## 1. Kết quả cần đạt

Sau bài học, bạn có thể:

- Viết unit test cho routing key, message envelope, retry policy và idempotency.
- Dùng Testcontainers RabbitMQ cho integration test, không phụ thuộc broker cài sẵn trên máy.
- Test topology declaration và permission.
- Viết contract test giữa producer và consumer.
- Test publisher confirm, returned message, consumer ack/nack/reject.
- Test retry, backoff, max attempt, DLQ và replay.
- Test duplicate event, DB rollback và crash trước/sau ack.
- Viết failure test cho broker down, network timeout, consumer crash và DB timeout.
- Chạy load test, burst test và soak test.
- Thiết kế chaos experiment nhỏ, có expected result, blast radius và cleanup.
- Tạo ma trận test chạy phù hợp trong pull request, nightly pipeline và staging.

## 2. Testing pyramid cho messaging

Không nên đưa mọi test lên broker thật. Một pipeline cân bằng:

```text
                    E2E / chaos nhỏ
                 /                  \
          staging failure test     soak test
              /          \
       contract test   integration test
           /                  \
      unit test          component test
```

| Tầng | Mục tiêu | Tốc độ | Môi trường |
|---|---|---:|---|
| Unit | Logic thuần: routing, envelope, retry | Rất nhanh | Không broker |
| Component | Consumer/publisher với dependency fake | Nhanh | Fake DB/downstream |
| Integration | Protocol, topology, ack, confirm | Vừa | Testcontainers RabbitMQ |
| Contract | Producer-consumer cùng message contract | Vừa | Broker + schema fixture |
| E2E | Business flow xuyên service | Chậm | Compose/staging |
| Chaos | Behavior khi dependency/node lỗi | Chậm, rủi ro | Lab/staging cô lập |

Nguyên tắc: lỗi business thuần nên bắt ở unit test; lỗi AMQP phải có integration test; lỗi tương tác service phải có contract/E2E; lỗi vận hành phải có failure/chaos test.

## 3. Ma trận test tối thiểu

| Nhóm | Ca kiểm thử |
|---|---|
| Publish | Confirm success, unroutable, broker unavailable |
| Consume | Ack, nack, reject, redelivery, graceful shutdown |
| Retry | Backoff, max attempt, DLQ, replay |
| Contract | Version cũ, field thiếu, field thêm, malformed payload |
| Consistency | DB rollback, duplicate event, crash trước/sau ack |
| Ops | Disk alarm, memory alarm, node restart, reconnect |
| Performance | Prefetch, concurrency, burst, sustained load |

Mỗi ô trong bảng cần có ít nhất:

```text
setup -> action -> expected broker state -> expected application state -> cleanup
```

Không ghi “test retry hoạt động”. Hãy ghi “lần xử lý thứ nhất fail, message retry sau 1s, 2s, 4s, sau lần thứ N đi DLQ, không tạo thêm retry vô hạn”.

## 4. Unit test: routing key và envelope

### Routing key

Routing key thường là pure function: event type và version được ánh xạ thành key. Test trực tiếp các case hợp lệ và không hợp lệ.

```java
class RoutingKeyTest {

    @Test
    void mapsOrderCreatedV1() {
        String key = RoutingKeys.forEvent("OrderCreated", 1);

        assertThat(key).isEqualTo("order.created.v1");
    }

    @Test
    void rejectsUnsupportedVersion() {
        assertThatThrownBy(() -> RoutingKeys.forEvent("OrderCreated", 99))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
```

Test thêm:

- event type không tồn tại;
- version bằng 0 hoặc âm;
- ký tự không hợp lệ;
- routing key không vượt giới hạn thiết kế;
- domain khác không bị map nhầm vào exchange hiện tại.

### Envelope

Envelope nên chứa metadata tối thiểu:

```java
public record MessageEnvelope(
    String messageId,
    String eventType,
    int schemaVersion,
    String correlationId,
    String causationId,
    Instant occurredAt,
    Object payload
) {}
```

Unit test cần bắt:

```text
[ ] messageId không rỗng.
[ ] eventType không rỗng.
[ ] schemaVersion hợp lệ.
[ ] occurredAt có timezone rõ ràng.
[ ] payload không null.
[ ] correlationId được propagate.
[ ] secret/PII bị loại khỏi log representation.
```

Đừng snapshot toàn bộ JSON nếu payload có field thay đổi thường xuyên. Snapshot nên tập trung vào contract field quan trọng.

## 5. Unit test: retry policy

Retry policy nên là logic có thể test không cần sleep thật:

```java
record RetryDecision(boolean retry, Duration delay, String reason) {}

final class RetryPolicy {
    RetryDecision decide(int attempt, Throwable error) {
        if (error instanceof PermanentBusinessException) {
            return new RetryDecision(false, Duration.ZERO, "permanent");
        }
        if (attempt >= 3) {
            return new RetryDecision(false, Duration.ZERO, "max-attempt");
        }
        return new RetryDecision(
            true,
            Duration.ofSeconds(1L << attempt),
            "transient"
        );
    }
}
```

Test tối thiểu:

```java
@Test
void usesExponentialBackoff() {
    RetryPolicy policy = new RetryPolicy();

    assertThat(policy.decide(0, new TimeoutException()).delay())
        .isEqualTo(Duration.ofSeconds(1));
    assertThat(policy.decide(1, new TimeoutException()).delay())
        .isEqualTo(Duration.ofSeconds(2));
}

@Test
void stopsAtMaxAttempt() {
    RetryDecision decision =
        new RetryPolicy().decide(3, new TimeoutException());

    assertThat(decision.retry()).isFalse();
    assertThat(decision.reason()).isEqualTo("max-attempt");
}
```

Không `Thread.sleep()` trong unit test. Dùng clock injectable hoặc kiểm tra `Duration` được tính đúng.

## 6. Unit test: idempotency

Consumer at-least-once phải coi duplicate là trạng thái bình thường. Test hai lần nhận cùng `messageId`:

```java
@Test
void duplicateMessageDoesNotRepeatBusinessEffect() {
    String messageId = "msg-123";

    handler.handle(orderCreated(messageId));
    handler.handle(orderCreated(messageId));

    verify(orderRepository, times(1)).createOrder(any());
    verify(inboxRepository, times(2)).recordAttempt(messageId);
}
```

Lưu ý transaction boundary. Nếu `inboxRepository` và business write không nằm trong transaction phù hợp, test mock có thể pass nhưng production vẫn tạo duplicate.

Test thêm:

- cùng message ID, payload khác;
- cùng business key, message ID khác;
- duplicate đến sau khi consumer restart;
- duplicate trong lúc retry;
- duplicate khi ack bị timeout.

## 7. Testcontainers RabbitMQ

Integration test phải tự tạo broker tạm thời. Như vậy test:

- không phụ thuộc RabbitMQ cài sẵn;
- chạy được trên máy developer và CI;
- có version image xác định;
- cleanup sau mỗi suite;
- có thể tạo vhost/user/topology độc lập.

Testcontainers có module RabbitMQ cho Java. Dependency version nên được quản lý qua BOM/version catalog của project; không copy một version cũ mù quáng.

Ví dụ Gradle:

```kotlin
dependencies {
    testImplementation("org.testcontainers:testcontainers-rabbitmq:<version>")
    testImplementation("org.testcontainers:junit-jupiter:<version>")
}
```

Ví dụ JUnit 5:

```java
@Testcontainers
class RabbitMqIntegrationTest {

    @Container
    static RabbitMQContainer rabbit = new RabbitMQContainer(
        DockerImageName.parse("rabbitmq:<tested-version>-management")
    );

    @BeforeAll
    static void startApp() {
        rabbit.start();
    }

    @Test
    void brokerIsReachable() {
        assertThat(rabbit.getHost()).isNotBlank();
        assertThat(rabbit.getAmqpPort()).isPositive();
    }
}
```

Nếu dùng `@Container` static, không cần tự gọi `start()` trong `@BeforeAll` khi extension đã quản lý lifecycle. Chọn một cách nhất quán, tránh start/stop hai lần.

## 8. Test topology declaration

Test topology phải kiểm tra cả “được tạo đúng” và “không tạo sai”.

### Positive cases

```text
[ ] Exchange đúng name/type.
[ ] Exchange durable.
[ ] Queue đúng name/type.
[ ] Queue durable.
[ ] Binding đúng routing key.
[ ] DLX/DLQ tồn tại.
[ ] Policy/TTL/limit được áp dụng.
```

### Negative cases

```text
[ ] Declare cùng tên nhưng sai type bị fail rõ ràng.
[ ] Consumer không được declare ngoài permission.
[ ] Queue không tồn tại bị xử lý theo policy.
[ ] Binding sai không làm message silently disappear mà không có metric.
```

Ví dụ assertion topology qua Java client:

```java
AMQP.Queue.DeclareOk declareOk = channel.queueDeclarePassive(
    "order-created.notification"
);

assertThat(declareOk.getQueue())
    .isEqualTo("order-created.notification");
```

`queueDeclarePassive` chỉ kiểm tra queue tồn tại; muốn kiểm tra arguments như queue type hoặc DLX, hãy dùng Management API/CLI hoặc test declare với expected properties.

## 9. Test permission

Dùng user riêng trong test, không dùng admin cho mọi case.

```text
order-service-test:
  configure: topology orders
  write: orders.events
  read: none

notification-service-test:
  configure: none
  write: none
  read: order-created.notification
```

Ma trận test:

| Identity | Operation | Expected |
|---|---|---|
| order-service | Declare orders exchange | Cho phép |
| order-service | Publish orders.events | Cho phép |
| order-service | Consume payment queue | Từ chối |
| notification-service | Consume notification queue | Cho phép |
| notification-service | Publish orders.events | Từ chối |
| application user | Management admin API | Từ chối |

Test permission nên chạy trong một vhost tạm thời, sau đó xóa container. Không chạy test phá permission trên broker dùng chung.

## 10. Test publisher confirm

Publisher confirm và consumer ack là hai cơ chế độc lập. Integration test phải kiểm tra riêng từng phía.

### Confirm success

```text
1. Enable confirm mode.
2. Publish persistent message vào durable exchange/queue.
3. Chờ confirm.
4. Assert confirm ack.
5. Consume và assert body/metadata.
```

### Unroutable message

Publish `mandatory=true` tới exchange không có binding phù hợp:

```text
expected:
  basic.return được gọi
  confirm vẫn được xử lý theo semantics của broker
  application không coi message là đã đến consumer
```

Test phải phân biệt:

```text
exchange không tồn tại -> channel/protocol error
exchange tồn tại nhưng không route -> returned message khi mandatory=true
broker không available -> confirm timeout/connection error
```

### Test ack không đồng bộ

Không giả định confirm đến theo đúng thứ tự publish. Lưu `messageId`/sequence number và map pending publish với callback confirm.

```java
Map<Long, String> pending = new ConcurrentHashMap<>();

long sequence = channel.getNextPublishSeqNo();
pending.put(sequence, messageId);
channel.basicPublish(exchange, routingKey, mandatory, properties, body);

// confirm callback loại sequence khỏi pending
```

Test:

- nhiều publish outstanding;
- confirm callback dùng `multiple=true`;
- một publish nack;
- connection đóng khi còn pending;
- retry pending có thể tạo duplicate.

## 11. Test consumer ack, nack, reject

| Tình huống | Hành động | Expected |
|---|---|---|
| Xử lý thành công | `ack` | Message bị loại khỏi queue |
| Lỗi tạm thời | `nack(requeue=true)` hoặc retry flow | Có thể redeliver |
| Lỗi vĩnh viễn | `reject(requeue=false)` | DLX/DLQ hoặc drop theo policy |
| Consumer crash trước ack | Không gửi ack | Message được requeue sau detection |
| Double ack | Ack cùng delivery tag | Channel error |
| Ack trên channel khác | Ack sai channel | Protocol/channel error |

Test phải assert cả business side effect và broker side effect. Ví dụ consumer crash sau DB commit nhưng trước ack phải tạo duplicate delivery; business effect không được nhân đôi nhờ idempotency.

## 12. Integration test retry và DLQ

Một test retry tốt không chỉ assert message cuối cùng nằm trong DLQ. Nó kiểm tra timeline và metadata:

```text
messageId = msg-1
attempt 1 -> transient error
attempt 2 -> transient error
attempt 3 -> permanent/max-attempt
DLQ       -> msg-1
```

Assert:

- số attempt đúng;
- delay/backoff nằm trong khoảng cho phép;
- original exchange/routing key được giữ trong headers phù hợp;
- DLQ reason có thể điều tra;
- message không quay vòng vô hạn;
- một message lỗi không chặn vô hạn message khác;
- retry queue/DLX topology tồn tại.

### Test replay

Replay phải là operation có chủ ý:

```text
1. Lấy message từ DLQ.
2. Kiểm tra reason và schema version.
3. Chọn replay exchange/routing key.
4. Gắn replay metadata.
5. Publish với confirm.
6. Không xóa bản gốc trước khi replay success.
7. Theo dõi duplicate/idempotency.
```

Test replay với message malformed, message cũ, message đã xử lý một phần và message chứa schema version cũ.

## 13. Contract test producer–consumer

Contract test kiểm tra hai phía đồng ý về message contract mà không cần chạy toàn bộ business system.

### Producer contract

Producer cam kết:

```json
{
  "messageId": "msg-123",
  "eventType": "OrderCreated",
  "schemaVersion": 1,
  "correlationId": "corr-456",
  "occurredAt": "2026-10-04T10:00:00Z",
  "payload": {
    "orderId": "ord-123",
    "customerId": "cus-456",
    "totalAmount": 125000,
    "currency": "VND"
  }
}
```

Consumer kiểm tra:

- field bắt buộc tồn tại;
- kiểu dữ liệu đúng;
- enum/format đúng;
- version được hỗ trợ;
- unknown field xử lý theo compatibility policy;
- malformed payload đi DLQ, không retry vô hạn.

### Backward/forward compatibility

| Thay đổi | Compatibility thường an toàn |
|---|---|
| Thêm optional field | Có thể backward compatible |
| Thêm required field | Rủi ro consumer cũ |
| Xóa field đang dùng | Breaking |
| Đổi kiểu field | Breaking |
| Đổi meaning nhưng giữ tên | Rủi ro cao, khó phát hiện |
| Đổi event type/routing key | Có thể làm mất route |
| Tăng schema version | Cần migration/consumer support |

Contract test phải có fixture version cũ và version mới. Đừng chỉ test payload “đẹp nhất” của current version.

## 14. Contract test malformed payload

Tạo các fixture:

```text
missing-message-id.json
missing-order-id.json
wrong-total-type.json
unknown-schema-version.json
invalid-currency.json
payload-too-large.json
null-payload.json
```

Expected behavior cần rõ:

```text
malformed không retry vô hạn
malformed được log metadata, không log secret
malformed đi DLQ với reason
consumer ack/reject nhất quán
alert tăng theo lỗi contract
```

## 15. Consistency test: DB và ack

Test các điểm crash quan trọng:

```text
A. DB fail trước business write
B. DB rollback sau business write một phần
C. DB commit thành công, crash trước ack
D. Ack thành công, process crash ngay sau đó
E. Publish downstream fail sau DB commit
```

Kỳ vọng thường là:

| Điểm crash | Hành vi cần chứng minh |
|---|---|
| Trước DB commit | Message được retry/requeue, không có business effect |
| Sau DB commit trước ack | Redelivery nhưng idempotency chặn duplicate |
| Sau ack | Không xử lý lại từ queue; recovery dựa vào business state/outbox |
| Outbox publish fail | Outbox record còn pending để publisher retry |

Test này là lý do transactional outbox và inbox/idempotency cần được kiểm tra cùng messaging, không thể chỉ test broker.

## 16. Failure test: broker down

### Mục tiêu

Chứng minh producer và consumer không fail âm thầm khi broker unavailable.

### Kịch bản

```text
1. Start broker container.
2. Start application.
3. Publish message bình thường.
4. Stop broker hoặc chặn endpoint trong lab.
5. Publish thêm message.
6. Quan sát timeout, confirm pending, retry và log.
7. Start broker lại.
8. Quan sát reconnect và recovery.
```

Expected:

- application có health signal degraded;
- retry có backoff;
- không tạo reconnect storm;
- message chưa confirm được xử lý theo policy;
- duplicate có thể xảy ra và consumer xử lý idempotent;
- sau recovery, message flow trở lại bình thường.

## 17. Failure test: network timeout

Network timeout không giống connection refused. Có thể TCP write thành công nhưng confirm không về.

Test cần mô phỏng:

- latency tăng;
- packet loss;
- connection bị cắt giữa publish và confirm;
- consumer bị cắt giữa processing và ack;
- DNS/endpoint tạm thời không resolve.

Expected test artifact:

```text
pending message IDs:
confirm timeout:
retry count:
duplicate count:
final business effect count:
```

Mục tiêu không phải làm duplicate bằng 0 trong mọi failure; mục tiêu là duplicate không tạo sai business effect.

## 18. Failure test: consumer crash

Tạo consumer test có các điểm crash có chủ ý:

```text
CRASH_BEFORE_DB
CRASH_AFTER_DB_BEFORE_ACK
CRASH_AFTER_ACK
```

Quan sát:

- `redelivered` flag;
- delivery count/attempt metadata;
- unacked giảm/tăng;
- consumer reconnect;
- business write count;
- DLQ sau max attempt.

Consumer không nên ack trước business operation chỉ để tránh redelivery. Điều đó biến crash thành message loss.

## 19. Failure test: database timeout

Simulate DB timeout ở các lớp:

- connection acquisition;
- query execution;
- commit;
- lock wait;
- downstream call trong cùng business flow.

Kỳ vọng:

```text
transient DB timeout -> retry có giới hạn
transaction rollback -> không để trạng thái nửa vời
message chưa ack -> có thể redeliver
attempt count tăng đúng
DLQ sau max attempt
alert/log có correlationId
```

Không retry cả message vô hạn nếu lỗi là constraint/business permanent.

## 20. Graceful shutdown test

Khi deploy/restart consumer:

```text
1. Stop nhận delivery mới.
2. Chờ xử lý in-flight trong timeout.
3. Ack các message đã xử lý thành công.
4. Để message chưa xử lý được requeue khi connection đóng.
5. Đóng channel/connection sạch.
```

Test:

- shutdown khi không có message;
- shutdown khi có nhiều unacked;
- shutdown timeout;
- SIGTERM rồi SIGKILL;
- rolling deployment với hai phiên bản consumer.

## 21. Load test

Load test trả lời câu hỏi định lượng:

- throughput tối đa ở p95 latency chấp nhận được là bao nhiêu?
- prefetch/concurrency nào tốt?
- publisher confirm latency tăng từ mức nào?
- queue ready/unacked có tăng không?
- CPU, memory, disk I/O và network bottleneck ở đâu?
- retry/DLQ có tạo thêm load không?

### Kịch bản

```text
warm-up: 5 phút
steady load: 20 phút
burst: 2-5 lần baseline trong 1-2 phút
recovery: về baseline, theo dõi drain backlog
```

Đo:

```text
publish rate
confirm rate/latency
deliver rate
ack rate
redelivery rate
processing p50/p95/p99
ready/unacked/oldest age
CPU/memory/disk/network
error/DLQ rate
```

Không chỉ nhìn message/second. Một hệ thống có throughput cao nhưng p99 tăng vô hạn và queue age tăng là hệ thống đang mất khả năng phục vụ.

## 22. Soak test

Soak test chạy workload ổn định trong thời gian dài để bắt:

- memory leak;
- connection/channel leak;
- disk tăng không giảm;
- consumer chậm dần;
- retry loop;
- metrics cardinality tăng;
- log volume bất thường;
- replica/queue segment growth.

Một soak test nên có periodic checkpoint:

```text
T+00m, T+30m, T+60m, T+120m...
queue depth:
oldest age:
consumer count:
open connections/channels:
heap:
disk free:
DLQ count:
```

## 23. Chaos engineering là gì?

Chaos engineering là kiểm chứng có chủ đích rằng hệ thống phản ứng đúng khi dependency hoặc hạ tầng hỏng. Nó không phải “ngắt production xem chuyện gì xảy ra”.

Một experiment phải có:

```text
hypothesis
scope/blast radius
steady-state metrics
fault injection
expected result
abort condition
cleanup
evidence
follow-up action
```

Ví dụ hypothesis:

> Nếu một RabbitMQ node bị restart trong cluster ba node, publisher sẽ reconnect, quorum queue vẫn giữ message đã confirm, consumer xử lý hết backlog trong 10 phút và không có business duplicate nhờ idempotency.

Đây là hypothesis có thể đo. “Hệ thống vẫn ổn” thì không.

## 24. Chaos guardrails

Không chạy chaos experiment nếu thiếu:

```text
[ ] Có approval và owner.
[ ] Môi trường đã xác định.
[ ] Không ảnh hưởng tenant ngoài scope.
[ ] Có backup/restore hoặc recreate procedure.
[ ] Có dashboard realtime.
[ ] Có stop switch.
[ ] Có on-call theo dõi.
[ ] Có cleanup tự động.
[ ] Có post-experiment review.
```

### Abort condition mẫu

```text
- DLQ rate vượt ngưỡng nghiêm trọng.
- oldest message age vượt RTO.
- memory/disk alarm xuất hiện.
- confirm error tăng liên tục.
- business duplicate vượt ngưỡng.
- consumer reconnect storm.
- fault lan ra ngoài namespace/vhost test.
```

## 25. Chaos experiment 1 — Restart một broker node

### Hypothesis

Quorum queue có majority; node restart không làm mất message đã confirm.

### Fault

Restart đúng một node trong lab cluster.

### Observe

```text
leader election duration
publisher confirm latency
connection/reconnect count
consumer redelivery
ready/unacked
missing/duplicate business effects
```

### Cleanup

Xác nhận node rejoin, replica catch up, không còn alarm và backlog đã drain.

## 26. Chaos experiment 2 — Broker unavailable

### Fault

Chặn traffic đến AMQP endpoint trong thời gian ngắn ở lab.

### Expected

- producer không silently drop message;
- confirm timeout/error được ghi nhận;
- reconnect backoff hoạt động;
- consumer không spin loop;
- sau restore, message flow recovery;
- pending publish retry an toàn với idempotency.

### Không làm

- không chặn network của broker dùng chung;
- không xóa volume;
- không chạy lúc đang có migration không thể rollback;
- không coi một lần test pass là bằng chứng cho mọi failure mode.

## 27. Chaos experiment 3 — Consumer crash

### Fault

Kill process tại ba checkpoint:

```text
trước DB commit
sau DB commit, trước ack
sau ack
```

### Expected

Message chưa ack có thể redeliver. Business effect phải idempotent. Test phải phân biệt số lần delivery với số lần business effect.

## 28. Chaos experiment 4 — Downstream/DB timeout

### Fault

Inject timeout vào DB hoặc downstream service.

### Expected

```text
transient error -> retry/backoff
permanent error -> DLQ hoặc business rejection
không retry vô hạn
ack semantics nhất quán
metrics/log/tracing đủ correlation
```

## 29. Test performance theo prefetch và concurrency

Chạy ma trận:

| Prefetch | Concurrency | Cần quan sát |
|---:|---:|---|
| 1 | 1 | Latency ổn định, throughput thấp |
| 10 | 2 | Cân bằng cơ bản |
| 100 | 4 | Throughput, memory, unacked |
| 300 | 8 | Tail latency, downstream pressure |

Không coi giá trị prefetch lớn luôn tốt. Prefetch lớn có thể:

- làm tăng unacked;
- tăng memory consumer;
- làm một consumer giữ quá nhiều message;
- tăng duplicate work khi process crash;
- gây áp lực lên DB/downstream.

Chọn bằng measurement, không chọn bằng thói quen.

## 30. CI pipeline

Một pipeline thực dụng:

```text
Pull request:
  unit
  serialization/contract fixtures
  fast integration với Testcontainers

Merge/main:
  full integration
  permission/topology test
  retry/DLQ/ack/confirm test

Nightly:
  load smoke
  soak ngắn
  compatibility matrix

Staging window:
  failure test
  chaos nhỏ
  restore drill
```

### Test không được flaky

Tránh:

- `Thread.sleep()` cố định thay vì await condition;
- phụ thuộc queue còn message từ test trước;
- dùng tên queue chung giữa test chạy song song;
- assert timing quá hẹp;
- không drain/cleanup container;
- phụ thuộc internet hoặc broker ngoài CI.

Dùng:

- unique vhost/queue name theo test;
- polling với timeout rõ;
- correlation ID;
- test isolation;
- log artifact khi fail;
- cleanup trong `finally`/container lifecycle.

## 31. Report failure test

Mỗi failure test nên sinh report:

```text
experiment/test name:
commit/build:
RabbitMQ image/version:
client version:
topology:
fault injected:
start/end time:
expected result:
actual result:
metrics snapshot:
logs/traces:
message IDs affected:
business effect count:
cleanup status:
follow-up owner:
```

Nếu test fail nhưng không biết message nào bị ảnh hưởng, test chưa đủ khả năng điều tra.

## 32. Common mistakes

### Integration test dùng broker developer cài sẵn

Test pass trên laptop nhưng fail trên CI hoặc version khác. Dùng Testcontainers/containerized dependency.

### Chỉ test happy path

Messaging hỏng ở confirm timeout, redelivery, routing miss và crash boundary. Happy path không đủ.

### Assert “message đã ack” nhưng không assert business effect

Consumer có thể ack quá sớm. Test cần chứng minh ack xảy ra sau business operation theo contract.

### Test retry bằng sleep dài

Test chậm và flaky. Tách policy calculation khỏi scheduler, dùng virtual clock/await condition.

### Contract test chỉ kiểm tra producer

Consumer mới là bên chịu schema compatibility runtime. Test producer fixture với consumer parser thật.

### Chaos không có rollback

Fault injection không có cleanup sẽ biến lab thành incident. Mọi experiment phải có stop switch.

### Load test chỉ đo throughput

Throughput không nói được message age, p99, DLQ, error rate và recovery. Đo cả hệ thống.

### Dùng production để học cơ bản

Chaos production chỉ phù hợp khi tổ chức đã có guardrail và maturity phù hợp. Bắt đầu trong isolated lab/staging.

## 33. Checklist hoàn thành test suite

### Unit

```text
[ ] Routing key.
[ ] Envelope validation.
[ ] Retry/backoff/max attempt.
[ ] Idempotency.
[ ] Redaction/log metadata.
```

### Integration

```text
[ ] Testcontainers RabbitMQ.
[ ] Topology declaration.
[ ] Permission allow/deny.
[ ] Publisher confirm/nack/return.
[ ] Consumer ack/nack/reject/redelivery.
[ ] Retry/DLX/replay.
[ ] Graceful shutdown/reconnect.
```

### Contract

```text
[ ] Version hiện tại.
[ ] Version cũ.
[ ] Field thêm.
[ ] Field thiếu.
[ ] Field sai kiểu.
[ ] Unknown event/schema.
[ ] Malformed payload.
```

### Failure/performance

```text
[ ] Broker down.
[ ] Network timeout.
[ ] Consumer crash.
[ ] DB timeout.
[ ] Disk/memory alarm ở lab.
[ ] Node restart.
[ ] Burst.
[ ] Sustained load.
[ ] Soak.
```

### Chaos governance

```text
[ ] Hypothesis.
[ ] Owner/approval.
[ ] Blast radius.
[ ] Dashboard.
[ ] Abort condition.
[ ] Cleanup.
[ ] Evidence.
[ ] Follow-up action.
```

## 34. Deliverable cuối tuần

Tạo thư mục:

```text
rabbitmq/week-15/
├── README.md
├── unit/
├── integration/
│   ├── topology/
│   ├── permissions/
│   ├── confirms/
│   ├── acknowledgements/
│   └── retry-dlq/
├── contracts/
│   ├── fixtures-v1/
│   ├── fixtures-v2/
│   └── malformed/
├── performance/
├── chaos/
│   ├── broker-down.md
│   ├── consumer-crash.md
│   └── db-timeout.md
└── test-matrix.md
```

`README.md` phải ghi:

- cách chạy unit/integration/contract test;
- RabbitMQ image/version test;
- test nào chạy ở pull request;
- test nào chỉ chạy nightly/staging;
- chaos guardrails;
- cleanup procedure;
- link report/metrics khi test fail.

## 35. Tiêu chí hoàn thành

Bạn hoàn thành Tuần 15 khi:

- Integration test không phụ thuộc broker cài sẵn trên máy.
- Pipeline chạy được test quan trọng khi mở pull request.
- Có contract test cho current/old schema và malformed payload.
- Test được confirm, return, ack, nack, reject và redelivery.
- Test được retry, backoff, max attempt, DLQ và replay.
- Có failure test cho broker down, timeout, consumer crash và DB timeout.
- Có load/soak test với metrics rõ ràng.
- Chaos test có expected result, abort condition và cleanup.
- Kết quả test phân biệt delivery count với business effect count.

## 36. Câu hỏi tự kiểm tra

### Câu 1

Vì sao integration test không nên phụ thuộc RabbitMQ cài sẵn trên máy developer?

**Đáp án:** Vì version, config, topology và state khác nhau; test sẽ không reproducible trên CI. Container giúp tạo dependency cô lập và versioned.

### Câu 2

Publisher confirm có thay thế consumer ack không?

**Đáp án:** Không. Confirm kiểm tra broker đã nhận/chịu trách nhiệm ở phía publisher; ack xác nhận consumer đã xử lý delivery.

### Câu 3

Consumer crash sau DB commit nhưng trước ack có thể xảy ra điều gì?

**Đáp án:** Message có thể redeliver và business operation có thể chạy lại. Consumer cần idempotency.

### Câu 4

Contract test cần test field thêm và field thiếu để làm gì?

**Đáp án:** Để kiểm tra backward/forward compatibility và phát hiện producer-consumer lệch schema trước production.

### Câu 5

Một chaos experiment tối thiểu phải có gì?

**Đáp án:** Hypothesis, scope, steady-state metrics, fault, expected result, abort condition, cleanup và evidence.

### Câu 6

Load test chỉ có throughput có đủ không?

**Đáp án:** Không. Phải đo cả latency tail, queue age, unacked, confirm/error/DLQ rate, resource usage và recovery.

## 37. Tài liệu tham khảo chính thức

- [RabbitMQ Consumer Acknowledgements and Publisher Confirms](https://www.rabbitmq.com/docs/confirms)
- [RabbitMQ Reliability Guide](https://www.rabbitmq.com/docs/reliability)
- [RabbitMQ Consumers](https://www.rabbitmq.com/docs/consumers)
- [Testcontainers RabbitMQ Module](https://java.testcontainers.org/modules/rabbitmq/)

## 38. Chuẩn bị cho Tuần 16

Tuần 16 sẽ ghép các mảnh đã học thành **Order Processing Platform**: contract, outbox, routing, retry/DLQ, idempotency, observability, security, HA và test/failure drill trong một hệ thống hoàn chỉnh.

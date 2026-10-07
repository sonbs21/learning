# Tuần 17: Testing và chaos

## 1. Mục tiêu

Sau bài học này, bạn có thể:

- Phân biệt unit test, contract test, serialization test, integration test và chaos test.
- Thiết kế test cho producer, consumer, retry, DLQ, idempotency và ordering.
- Dựng broker/database bằng Testcontainers.
- Test consumer restart, broker unavailable và duplicate delivery.
- Chứng minh message không mất trong failure scenario được chọn.
- Chứng minh duplicate không tạo duplicate business side effect.
- Chứng minh retry có giới hạn.
- Chứng minh lỗi vĩnh viễn đi vào DLQ hoặc DLT.
- Đo processing latency, redelivery, lag và recovery time trong test.
- Thiết kế chaos experiment có hypothesis, blast radius, stop condition và success criteria.
- Viết báo cáo test có bằng chứng thay vì chỉ ghi test pass.

Nguyên tắc:

> Testing chứng minh hành vi trong các scenario đã mô hình hóa. Chaos testing tìm các failure interaction mà bạn chưa mô hình hóa, nhưng phải được giới hạn bởi invariant và stop condition.

---

## 2. Testing queue khác testing CRUD

CRUD test thường hỏi:

- API trả status gì?
- Row database có được tạo không?
- JSON response có đúng không?

Queue test phải hỏi thêm:

- Message có được publish không?
- Message có bị duplicate không?
- Consumer có ack sau side effect không?
- Consumer crash ở từng thời điểm thì sao?
- Retry có giới hạn không?
- Message sai schema đi đâu?
- DLQ có giữ đủ metadata không?
- Ordering có còn đúng theo key không?
- Broker restart có làm mất event không?
- Replay có tạo duplicate side effect không?
- Hệ thống recover trong bao lâu?
- Backlog có giảm sau khi dependency hồi phục không?

Một test queue tốt phải kiểm tra cả:

- Transport outcome.
- Persistence outcome.
- Business outcome.
- Observability outcome.
- Recovery outcome.

---

## 3. Test pyramid cho queue

### Unit test

Kiểm tra logic thuần:

- Retry classification.
- Backoff calculation.
- Exception mapping.
- Idempotency key.
- State transition.
- DLQ routing.
- Serialization mapper.
- Header propagation.

Đặc điểm:

- Nhanh.
- Deterministic.
- Không cần broker.
- Chạy mọi commit.

### Contract test

Kiểm tra producer và consumer hiểu cùng message contract:

- Event type.
- Required field.
- Optional field.
- Enum.
- Schema version.
- Unknown field.
- Backward/forward compatibility.
- Header metadata.

### Serialization test

Kiểm tra:

- Object serialize thành payload hợp lệ.
- Payload deserialize được.
- Timestamp và numeric type không đổi nghĩa.
- Null/missing field đúng policy.
- Schema ID/version được gắn đúng.
- Payload lỗi bị reject đúng loại.

### Integration test

Dùng database/broker thật hoặc container:

- Publish và consume.
- Ack/commit.
- Retry và DLQ.
- Outbox/Inbox transaction.
- Consumer group.
- Ordering.
- Restart.

### Chaos test

Cố ý tạo failure:

- Kill consumer.
- Ngắt broker.
- Delay network.
- Làm downstream timeout.
- Tạo duplicate.
- Tạo hot partition.
- Làm database chậm.
- Restart broker/container.

Chaos test không thay unit/integration test; nó kiểm tra hệ thống khi nhiều component tương tác trong failure.

---

## 4. Invariant là trung tâm của test

Invariant là điều phải luôn đúng dù failure xảy ra.

Ví dụ Order Processing Platform:

- Một event ID không tạo hai inventory reservation.
- Payment capture với cùng idempotency key không tạo hai charge.
- Order không chuyển CONFIRMED nếu payment chưa captured.
- Message invalid không retry vô hạn.
- Message hết retry vào DLT.
- Outbox commit cùng order.
- Consumer crash trước ack làm message có thể redeliver.
- Ack/commit chỉ xảy ra sau side effect an toàn.
- Replay không tạo duplicate business effect.
- Sau khi dependency hồi phục, backlog có thể giảm.
- Số message trong DLQ được giải thích bởi failure category.

Viết invariant trước khi viết chaos experiment.

---

## 5. Test contract và business contract

Một schema hợp lệ chưa chắc business hợp lệ.

### Transport contract

- Topic/queue đúng.
- Routing key đúng.
- Partition key đúng.
- Header có event ID.
- Correlation ID tồn tại.
- Schema version đúng.
- Content type đúng.

### Data contract

- Required field.
- Type.
- Range.
- Enum.
- Timestamp.
- Null/missing.
- Currency/unit.

### Business contract

- Total amount khớp line items.
- Order ID tồn tại.
- Payment chỉ capture một lần.
- Inventory không reserve vượt available.
- State transition hợp lệ.
- Event chỉ phát sau local commit.

Một test cần ghi rõ đang bảo vệ lớp nào.

---

## 6. Unit test producer

Producer unit test không cần broker nếu mục tiêu là kiểm tra logic.

Nên test:

- Tạo event có eventId.
- Gắn eventType và eventVersion.
- Gắn correlationId và causationId.
- Dùng aggregateId làm key.
- Payload snapshot đúng.
- Publish exception được phân loại.
- Publisher confirm failure được xử lý.
- Không publish event nếu business transaction rollback.
- Outbox record có status ban đầu đúng.

Ví dụ cases:

    create order success
      -> one order event

    create order validation failure
      -> no event

    database transaction rollback
      -> no committed outbox record

    same API idempotency key
      -> same business result

    same key with different payload
      -> conflict

Không mock mọi thứ đến mức test chỉ chứng minh mock được gọi. Cần assert business result và event content.

---

## 7. Unit test consumer

Consumer unit test nên tách:

- Deserialize.
- Validate.
- Classify exception.
- Apply business logic.
- Deduplicate.
- Decide ack/retry/DLQ.
- Build outgoing event.

Cases:

    valid OrderCreated
      -> inventory reserved

    duplicate OrderCreated
      -> no second reservation

    invalid schema
      -> permanent failure

    payment timeout
      -> retryable failure

    inventory insufficient
      -> business rejection or event

    unknown provider outcome
      -> reconciliation state

    valid message with unknown optional field
      -> accepted if policy allows

Không chỉ assert handler không throw. Assert state, outgoing event, Inbox row và ack decision.

---

## 8. Serialization test

Serialization test phải bao phủ cả data cũ và data mới.

Cases:

- Current schema round trip.
- Old payload đọc bởi new consumer.
- New payload đọc bởi old consumer nếu cần.
- Missing optional field.
- Null optional field.
- Unknown field.
- Unknown enum.
- Invalid type.
- Invalid timestamp.
- Large numeric value.
- Unicode.
- Empty collection.
- Duplicate field nếu format cho phép.
- Payload vượt size limit.

Round trip:

    object
      -> serialize
      -> deserialize
      -> compare normalized object

Không nhất thiết so sánh byte-for-byte nếu serializer không bảo đảm canonical byte representation. So sánh semantics.

Schema fixture nên được version hóa:

    fixtures/order-created/v1-valid.json
    fixtures/order-created/v2-valid.json
    fixtures/order-created/missing-total.json
    fixtures/order-created/unknown-enum.json

Không lấy payload production chứa PII làm fixture nếu chưa được sanitize.

---

## 9. Contract test producer-consumer

Consumer contract ghi rõ consumer cần gì:

- Event type.
- Required field.
- Field semantics.
- Enum behavior.
- Default behavior.
- Unknown field behavior.
- Error behavior.
- Business invariant.

Producer pipeline chạy:

    schema lint
      -> compatibility check
      -> serialization test
      -> consumer contract
      -> fixture replay
      -> privacy scan
      -> publish artifact

Consumer contract không nên assert toàn bộ JSON byte-for-byte nếu field mới được phép thêm.

Ví dụ expectation:

    event.type = OrderCreated
    data.orderId exists
    data.totalMinor >= 0
    missing discount behaves as zero
    unknown fields do not break parsing

Nếu consumer thật sự cần exact shape, decision đó phải được document vì nó làm evolution khó hơn.

---

## 10. Testcontainers

Testcontainers cung cấp các container tạm thời cho test và phù hợp với integration test cần database/broker thật. Test cần Docker-compatible runtime hoặc môi trường tương đương. [Testcontainers for Java](https://java.testcontainers.org/)

Dùng Testcontainers khi cần kiểm tra:

- Transaction database thật.
- Broker acknowledgement.
- Queue/topic topology.
- Consumer group.
- Retry/DLQ.
- Serialization qua client thật.
- Restart và network failure ở mức container.

Không dùng Testcontainers cho mọi unit test; startup chậm hơn và test phức tạp hơn.

### Container lifecycle

Chọn:

- Một container mỗi test class để giảm thời gian.
- Một container mỗi test khi isolation quan trọng.
- Reuse local development chỉ khi hiểu rủi ro state leakage.
- Topic/queue name riêng cho mỗi test.
- Database schema cleanup rõ ràng.

### Test isolation

Mỗi test nên có:

- Topic/queue unique.
- Consumer group unique.
- Idempotency key unique.
- Database transaction state sạch.
- Offset policy rõ.
- Không phụ thuộc thứ tự test.
- Timeout bounded.

---

## 11. Testcontainers với Kafka

Testcontainers có module Kafka hỗ trợ Kafka image và tạo broker tự động trong test. [Testcontainers Kafka module](https://java.testcontainers.org/modules/kafka/)

Mô hình test:

    start Kafka container
    create topic with known partitions
    configure producer and consumer bootstrap
    publish fixture
    consume and assert
    stop container

Cần test:

- Topic có đúng partition.
- Key đi đúng partition.
- Consumer group nhận record.
- Offset commit đúng.
- Consumer restart nhận lại record chưa commit.
- Retry/DLT topic tồn tại.
- Record giữ key, header và timestamp.
- Broker stop/start theo khả năng môi trường test.

Không dùng topic cố định chung cho toàn suite nếu không có cleanup/offset isolation.

Spring Kafka cũng cung cấp Embedded Kafka và Kafka test utilities; tài liệu khuyến nghị topic riêng cho test để tránh cross-talk. [Spring Kafka testing](https://docs.spring.io/spring-kafka/reference/testing.html)

### Embedded broker hay container

Embedded broker:

- Nhanh.
- Tiện cho listener/serialization test.
- Không luôn giống topology production.
- Không phù hợp mọi broker failure/chaos scenario.

Container:

- Gần broker thật hơn.
- Test topology và network rõ hơn.
- Có startup cost.
- Cần container runtime.

Chọn theo failure cần kiểm tra.

---

## 12. Testcontainers với database và Outbox

Integration test Outbox cần database thật:

    start database
    run migrations
    call createOrder
    assert order row
    assert outbox row
    rollback test
    cleanup

Cases:

- Order và Outbox commit cùng transaction.
- Exception trước Outbox insert.
- Exception sau Outbox insert.
- Unique idempotency key.
- Concurrent create với cùng key.
- Publisher claim bằng row lock.
- Lease expired được reclaim.
- Cleanup không xóa record chưa publish.

Không mock repository nếu mục tiêu là kiểm tra transaction atomicity, isolation hoặc unique constraint.

---

## 13. Integration test message flow

Một integration test end-to-end nhỏ:

    create order via application
      -> order committed
      -> outbox committed
      -> publisher sends
      -> consumer receives
      -> inbox committed
      -> inventory updated
      -> next outbox created
      -> message acknowledged

Assert:

- Một order.
- Một Outbox record.
- Một Inventory reservation.
- Một Inbox record.
- Một outgoing event.
- Không duplicate.
- Trace/correlation metadata tồn tại.
- Processing latency nằm trong test timeout.

Dùng await có timeout thay vì sleep cố định. Sleep làm test chậm và flaky.

Tạo helper:

    await until condition true
    timeout after bounded duration
    include diagnostics on failure

Diagnostics nên in:

- Queue depth.
- Consumer assignment.
- Lag.
- Outbox status.
- Inbox status.
- Last exception.
- Published event count.

---

## 14. Test retry

Retry test cần kiểm tra cả số lần và khoảng thời gian.

Cases:

- Dependency fail một lần rồi success.
- Dependency fail hai lần rồi success.
- Dependency fail hết budget.
- Permanent error không retry.
- Unknown outcome dùng status query.
- Retry delay tăng.
- Jitter nằm trong range.
- Retry count được truyền.
- DLT record có failure metadata.
- Retry không vượt overall deadline.

Test không nên phụ thuộc clock thật nếu có thể. Dùng fake clock hoặc controllable scheduler cho backoff calculation.

Assert:

    attempts = expected
    delays are within expected bounds
    permanent failure attempts = 1
    exhausted message goes to DLT
    original message identity is preserved

---

## 15. Test DLQ và DLT

DLQ test cần chứng minh:

- Invalid message không loop vô hạn.
- Retryable message có số lần retry đúng.
- Hết retry thì vào DLQ/DLT.
- Original topic/queue được giữ.
- Original partition/offset hoặc routing key được giữ.
- Exception category được ghi.
- Correlation ID không mất.
- Payload được bảo vệ.
- Replay source có thể xác định.
- DLT consumer không tự đưa message quay lại main queue.

Cases:

    schema error
      -> DLT immediately

    transient timeout
      -> retry then DLT

    business rejection
      -> business event or DLT

    DLT publish failure
      -> observable failure, no silent loss

Test cả DLT full/unavailable nếu đây là critical path.

---

## 16. Test idempotency

Idempotency test phải tạo duplicate delivery thật hoặc mô phỏng concurrency.

Cases:

- Cùng message ID hai lần tuần tự.
- Cùng message ID hai thread song song.
- Cùng business key khác event ID.
- Cùng idempotency key khác payload.
- Replay event cũ.
- Consumer crash sau side effect trước ack.
- Provider timeout sau success.
- Database unique constraint race.

Invariant:

    inbox rows = 1
    business side effect = 1
    outgoing event count = 1
    final state is valid

Nếu external provider không hỗ trợ idempotency, test phải chứng minh reconciliation hoặc manual state thay vì giả vờ exactly-once.

---

## 17. Test ordering

Ordering phải nêu scope:

- Global.
- Topic.
- Partition.
- Aggregate key.
- Queue group.

Cases:

- Cùng key A1, A2, A3.
- Khác key B1 có thể chạy song song.
- A1 fail và retry.
- A2 đến khi A1 pending.
- Consumer restart.
- Retry topic.
- Rebalance.
- Multiple producer instances.

Assertions:

- Event cùng partition giữ ordering nếu contract yêu cầu.
- Event khác partition không bị assert global ordering.
- Consumer không apply sequence 3 trước sequence 2.
- Gap được retry, buffer hoặc quarantine theo policy.
- Replay giữ identity và sequence phù hợp.

Không viết test đòi global ordering nếu kiến trúc chỉ bảo đảm ordering trong partition.

---

## 18. Test consumer restart

Các thời điểm crash cần test:

    before receive
    after receive before processing
    after side effect before ack
    after database commit before ack
    after ack or commit
    during downstream call
    during retry delay

Expected behavior:

- Crash trước commit: message redeliver.
- Crash sau commit trước ack: Inbox skip duplicate.
- Crash sau ack: không redeliver, side effect đã an toàn.
- Crash trong external call: unknown outcome path.
- Restart không làm mất retry state nếu retry durable.
- Consumer group rebalance eventually ổn định.

Test cần ghi lại:

- Message ID.
- Delivery attempt.
- Offset/ack state.
- Inbox state.
- Business state.
- Final broker state.

---

## 19. Test broker unavailable

Broker unavailable scenarios:

- Producer publish khi broker down.
- Consumer start khi broker down.
- Broker connection drop khi publish.
- Broker connection drop trước confirm.
- Broker restart sau commit Outbox.
- DLT broker/topic unavailable.
- Metadata unavailable.
- Network packet loss hoặc latency cao.

Expected:

- Producer có timeout bounded.
- Outbox giữ event nếu producer path dùng Outbox.
- Consumer reconnect với backoff.
- Không tạo connection storm.
- Message không silent drop.
- Alert rõ.
- Sau recovery backlog giảm.
- Duplicate được xử lý.

Không dùng test broker unavailable để kiểm tra tất cả failure cùng lúc. Mỗi experiment cần một failure variable chính.

---

## 20. Test message sai schema

Cases:

- Missing required field.
- Wrong type.
- Unknown enum.
- Unknown schema version.
- Invalid JSON.
- Invalid Avro/Protobuf bytes.
- Payload quá lớn.
- Null ở field không nullable.
- Event type không được hỗ trợ.
- Schema registry unavailable.

Expected:

- Deserialization/validation error được phân loại.
- Không retry vô hạn.
- Message vào DLT/quarantine.
- Error metadata đủ.
- Không tạo business side effect.
- Consumer không block queue/partition mãi.
- Producer/contract owner có thông tin điều tra.

Test cả old consumer với new event và new consumer với old event nếu compatibility yêu cầu.

---

## 21. Test downstream timeout

Tạo fake dependency có behavior:

    success
    503
    429
    timeout before processing
    success then connection close
    slow response
    permanent 400
    unknown status

Assert:

- Timeout bounded.
- Retry classification đúng.
- Idempotency key không đổi.
- Circuit breaker mở sau threshold.
- Retry budget không vượt.
- Message vào DLT nếu hết budget.
- Unknown outcome đi reconciliation.
- Không duplicate payment/email/inventory.
- Metrics timeout/retry tăng đúng.

Đây là test quan trọng nhất cho hệ thống có external side effect.

---

## 22. Test slow consumer

Producer rate lớn hơn consumer processing rate:

    producer = 100 messages/second
    consumer = 20 messages/second

Đo:

- Queue depth.
- Oldest age.
- Lag.
- Processing p95/p99.
- Retry.
- CPU/memory.
- Consumer concurrency.
- Recovery time sau khi tăng capacity.

Thử:

- Một consumer.
- Ba consumer.
- Mười consumer.
- Prefetch thấp/cao.
- Batch nhỏ/lớn.
- Downstream chậm.
- Rate limit producer.

Assertions:

- Backlog tăng theo dự đoán.
- Alert oldest age bật.
- Scale có tác dụng khi còn capacity/partition.
- Scale không giải quyết hot partition nếu key bị dồn.
- Khi producer giảm, backlog eventually giảm.
- Không mất message.

---

## 23. Test hot partition

Phân phối key lệch:

    key A = 70 percent
    key B = 10 percent
    key C = 10 percent
    key D = 10 percent

Test:

- Lag theo partition.
- Throughput theo partition.
- Consumer assignment.
- Processing latency theo key.
- Một key bị poison.
- Scale consumer.
- Đổi partition count hoặc key strategy trong lab riêng.

Expected:

- Tổng lag có thể thấp nhưng một partition lag cao.
- Consumer thêm không giúp nếu partition nóng không được chia.
- Ordering trade-off được document.
- Alert có top partition.
- Recovery không làm mất sequence.

---

## 24. Test duplicate delivery

Tạo duplicate từ:

- Consumer crash trước ack.
- SQS visibility timeout.
- Publisher retry sau timeout.
- Manual replay.
- Retry topic.
- Broker redelivery.
- Test gửi cùng event hai lần.

Assert:

- Event ID được giữ hoặc nhận diện.
- Inbox/unique constraint chặn duplicate.
- Side effect chạy một lần.
- Outgoing event không nhân đôi ngoài policy.
- Duplicate metric tăng.
- Log có attempt và reason.
- Replay không bỏ qua privacy/security policy.

Duplicate test phải chạy cả tuần tự và concurrent.

---

## 25. Chaos engineering

Chaos engineering là thực nghiệm có kiểm soát để kiểm tra resilience.

Một chaos experiment cần:

- Hypothesis.
- Target.
- Failure injection.
- Blast radius.
- Duration.
- Steady-state metrics.
- Stop conditions.
- Rollback/recovery.
- Evidence.
- Follow-up.

Mẫu:

    Hypothesis:
      consumer crash không làm mất message

    Target:
      one consumer instance trong test environment

    Failure:
      kill process sau database commit trước ack

    Expected:
      message redeliver và Inbox chặn duplicate

    Stop:
      business side effect duplicate hoặc queue tăng không giới hạn

    Recovery:
      restart consumer, wait until backlog returns to baseline

Không bắt đầu chaos production khi chưa có integration test và invariant rõ.

---

## 26. Chaos blast radius

Từ nhỏ đến lớn:

1. Một unit process.
2. Một consumer instance.
3. Một queue/topic test.
4. Một partition.
5. Một broker node.
6. Một dependency test.
7. Một service instance trong staging.
8. Một production canary.
9. Nhiều instance hoặc vùng.

Giới hạn:

- Chỉ environment được phép.
- Không dùng dữ liệu thật khi chưa được phê duyệt.
- Không kill database production tùy tiện.
- Không làm network partition toàn cluster nếu chưa có kế hoạch.
- Có owner quan sát.
- Có thời gian kết thúc.
- Có stop condition tự động.

Chaos không có nghĩa là phá bừa; chaos là failure injection có giả thuyết.

---

## 27. Steady-state metrics

Trước chaos, đo baseline:

- Queue depth.
- Oldest age.
- Consumer lag.
- Publish rate.
- Process rate.
- Ack/delete/commit rate.
- Error rate.
- Retry rate.
- DLQ rate.
- Processing p95/p99.
- Business success.
- Duplicate side effect.
- CPU/memory.
- Database/downstream latency.

Trong chaos, so sánh:

    baseline
      -> fault injected
      -> degradation
      -> recovery
      -> final state

Một hệ thống resilient có thể degradation nhưng phải:

- Không mất message.
- Không tạo duplicate side effect không kiểm soát.
- Không retry storm.
- Recover trong RTO.
- Backlog eventually giảm.
- Alert và trace hoạt động.

---

## 28. Stop conditions

Dừng experiment khi:

- Duplicate payment hoặc charge xuất hiện.
- Data corruption.
- Business invariant bị vi phạm.
- Queue depth tăng không giới hạn.
- Broker disk/memory vượt giới hạn.
- Retry storm làm dependency test sập.
- DLT không nhận được message lỗi.
- Consumer không recover sau timeout.
- Mất khả năng rollback fault.
- Blast radius vượt target.

Stop condition phải được viết trước khi chạy, không quyết định sau khi sự cố xảy ra.

---

## 29. Chaos scenario 1: Consumer chết trước ack

Hypothesis:

    consumer crash trước ack -> message redeliver -> side effect không duplicate

Setup:

- Một message có event ID cố định.
- Consumer xử lý database.
- Kill process ngay trước ack.
- Restart consumer.

Observe:

- Redelivery count.
- Inbox row.
- Business side effect count.
- Ack/commit.
- Queue depth.
- End-to-end latency.

Expected:

- Message được delivery lại.
- Side effect chỉ một lần.
- Inbox hoặc unique constraint bảo vệ.
- Consumer eventually ack/commit.

Failure:

- Ack trước side effect.
- Inbox không cùng transaction.
- Duplicate side effect.
- Message bị mất.
- Consumer loop vô hạn.

---

## 30. Chaos scenario 2: Crash sau database commit

Hypothesis:

    business commit + Outbox commit rồi service crash -> publisher/consumer eventually hoàn tất

Setup:

- Create order.
- Commit order và Outbox.
- Kill service trước publish.
- Restart publisher.

Observe:

- Order row.
- Outbox row.
- Broker message.
- Publish attempts.
- Duplicate possibility.
- Final consumer state.

Expected:

- Order không mất.
- Outbox còn.
- Event được publish sau restart.
- Nếu duplicate xảy ra, consumer dedup.

---

## 31. Chaos scenario 3: Broker mất kết nối

Hypothesis:

    broker unavailable -> producer/consumer timeout bounded, không silent loss, recover sau broker up

Setup:

- Publish steady traffic.
- Ngắt broker connection trong thời gian ngắn.
- Khôi phục broker.

Observe:

- Publish errors.
- Retry rate.
- Outbox pending.
- Consumer reconnect.
- Queue depth.
- Recovery time.
- Connection storm.

Expected:

- Error observable.
- Producer không block vô hạn.
- Event durable qua Outbox nếu đã commit.
- Consumer reconnect có backoff.
- Backlog giảm sau recovery.

---

## 32. Chaos scenario 4: Message sai schema

Hypothesis:

    invalid schema -> không retry vô hạn, không side effect, vào DLT

Setup:

- Gửi payload thiếu required field.
- Gửi unknown schema version.
- Gửi wrong type.
- Gửi invalid enum.

Observe:

- Deserialization/validation error.
- Attempt count.
- DLT count.
- Main queue/partition progress.
- Side effect count.

Expected:

- Không side effect.
- Không poison loop.
- DLT record có original metadata.
- Consumer tiếp tục xử lý message hợp lệ sau đó.

---

## 33. Chaos scenario 5: Downstream timeout

Hypothesis:

    downstream timeout -> retry bounded, circuit breaker/bulkhead bảo vệ consumer, unknown outcome được reconcile

Setup:

- Fake provider delay.
- Một số request success sau delay.
- Một số request timeout.
- Một số request success nhưng response mất.

Observe:

- Timeout count.
- Retry count.
- Circuit state.
- In-flight.
- Queue lag.
- Side effect count.
- Reconciliation result.

Expected:

- Không gọi provider vô hạn.
- Idempotency key giữ nguyên.
- Unknown outcome không tạo double effect.
- DLT chỉ nhận message thực sự exhausted.

---

## 34. Chaos scenario 6: Consumer chậm hơn producer

Hypothesis:

    consumer capacity thấp -> backlog tăng có kiểm soát, alert bật, scale hoặc backpressure khôi phục

Setup:

    producer = 100 messages/second
    consumer = 20 messages/second

Observe:

- Queue depth.
- Oldest age.
- Lag.
- Processing rate.
- Memory.
- Retry.
- Business SLA.

Actions:

- Scale consumer.
- Tăng concurrency.
- Giảm producer rate.
- Giảm downstream delay.
- Khôi phục normal workload.

Expected:

- Alert oldest age.
- Không mất message.
- Backlog giảm sau recovery.
- Capacity change có effect nếu partition/queue cho phép.

---

## 35. Chaos scenario 7: Hot partition

Hypothesis:

    hot key làm một partition lag dù tổng capacity còn dư

Setup:

- Dồn phần lớn messages vào một key.
- Có nhiều consumer.
- Theo dõi lag theo partition.

Observe:

- Partition rate.
- Partition lag.
- Consumer assignment.
- Processing latency.
- Top key.
- Overall throughput.

Expected:

- Nhìn thấy skew.
- Scale consumer không giải quyết hoàn toàn.
- Có quyết định rõ về key strategy và ordering.

---

## 36. Chaos scenario 8: Duplicate event

Hypothesis:

    duplicate event -> idempotent consumer tạo một business effect

Setup:

- Gửi cùng event ID hai hoặc nhiều lần.
- Chạy delivery concurrent.
- Replay event cũ.
- Làm provider response timeout sau success.

Observe:

- Inbox conflict.
- Unique constraint.
- Business side effect.
- Outgoing event.
- Duplicate metric.
- Provider requests.

Expected:

- Side effect exactly one theo business key.
- Duplicate được log và đo.
- Không ack sớm làm mất event.
- Không tạo event downstream vô hạn.

---

## 37. Chaos scenario 9: Database chậm

Hypothesis:

    database latency tăng -> timeout/backpressure hoạt động, không giữ consumer vô hạn

Setup:

- Inject query delay hoặc lock contention trong test.
- Consumer nhận message đều.
- Database response chậm.

Observe:

- DB p95/p99.
- Processing p95/p99.
- Unacked/in-flight.
- Queue depth.
- Timeout.
- Retry.
- Connection pool.
- Deadlock.

Expected:

- Query timeout bounded.
- Consumer không mở quá nhiều connection.
- Retry budget hoạt động.
- DLQ chỉ nhận permanent/exhausted errors.
- Sau khi DB hồi phục, backlog giảm.

---

## 38. Chaos report

Mỗi experiment phải có report:

    experiment_id
    date
    environment
    owner
    hypothesis
    target
    fault
    blast_radius
    duration
    baseline
    observations
    invariant_results
    stop_condition
    recovery_time
    final_state
    logs
    traces
    metrics
    failures
    follow_up_actions

Phần quan trọng nhất là invariant result:

    message loss = 0
    duplicate payment = 0
    retry exhausted handled = true
    DLT delivery = true
    backlog recovered within RTO = true

Không viết “system looked fine”. Viết số liệu và evidence.

---

## 39. Flaky test và thời gian

Queue test dễ flaky do:

- Async scheduling.
- Consumer startup delay.
- Broker metadata propagation.
- Rebalance.
- Network timing.
- Shared topics.
- Shared consumer group.
- Sleep cố định.
- Assertion quá sớm.
- Test order phụ thuộc nhau.

Giảm flaky:

- Dùng await với timeout.
- Dùng topic/group unique.
- Chờ consumer assignment.
- Chờ broker ready.
- Kiểm tra condition thay vì sleep.
- Cleanup resource.
- Bounded retry trong test helper.
- In diagnostics khi timeout.
- Không dùng global mutable state.
- Giữ test deterministic trước khi chaos.

Một test timeout phải báo trạng thái broker/queue/consumer để debug.

---

## 40. Test timeouts

Có nhiều timeout:

- Test method timeout.
- Broker startup timeout.
- Consumer assignment timeout.
- Poll timeout.
- Processing timeout.
- Database query timeout.
- External dependency timeout.
- Overall scenario timeout.

Đặt hierarchy:

    test timeout
      > scenario timeout
        > message processing deadline
          > dependency timeout

Nếu test timeout nhỏ hơn processing timeout, test fail sai. Nếu test timeout quá lớn, failure feedback chậm.

Mọi timeout phải có reason và metric.

---

## 41. Test data và cleanup

Test data cần:

- Event ID unique.
- Correlation ID unique.
- Idempotency key unique.
- Aggregate ID unique.
- Topic/queue isolation.
- Database cleanup.
- Consumer group isolation.

Không dùng random hoàn toàn nếu cần reproduce. Dùng seed được ghi trong test report.

Sau test:

- Stop consumers.
- Close producers.
- Stop containers.
- Delete topics/queues nếu cần.
- Truncate test tables.
- Xóa temporary files.
- Reset clock/scheduler.
- Clear retry state.

Test teardown lỗi cũng phải được log.

---

## 42. Security trong testing và chaos

Không dùng:

- Production secret.
- Production token.
- Production PII.
- Production payment credentials.
- Real customer payload.

Dùng:

- Synthetic data.
- Test credentials.
- Sanitized fixtures.
- Isolated account/region.
- Least privilege role.
- Network policy giới hạn.
- Audit log cho chaos operator.

Chaos tool có quyền kill container hoặc ngắt network phải được giới hạn theo environment.

Không đưa raw message vào CI log nếu có dữ liệu nhạy cảm.

---

## 43. Tiêu chí pass/fail

### Reliability

- [ ] Không mất message trong scenario được test.
- [ ] Duplicate không tạo duplicate side effect.
- [ ] Retry có giới hạn.
- [ ] Permanent error vào DLQ/DLT.
- [ ] Consumer restart recover.
- [ ] Broker reconnect recover.
- [ ] Backlog eventually giảm.
- [ ] Ordering đúng trong scope contract.

### Performance

- [ ] Throughput đạt target.
- [ ] p95/p99 trong SLA.
- [ ] Queue age trong SLA.
- [ ] Recovery time trong RTO.
- [ ] CPU/memory không vượt capacity.
- [ ] Retry không tạo storm.

### Observability

- [ ] Metrics xuất hiện.
- [ ] Logs structured.
- [ ] Correlation ID truy được.
- [ ] Trace xuyên message path.
- [ ] Alert bật đúng.
- [ ] DLT metadata đủ.
- [ ] Report có evidence.

### Safety

- [ ] Chaos blast radius đúng.
- [ ] Stop condition hoạt động.
- [ ] Có rollback/recovery.
- [ ] Không dùng production data ngoài scope.
- [ ] Có owner và approval.

---

## 44. Bài tập

### Bài 1

Viết test matrix cho producer, consumer, broker, database và downstream.

### Bài 2

Thiết kế contract test cho OrderCreated v1/v2.

### Bài 3

Viết integration test chứng minh Outbox và order cùng commit.

### Bài 4

Viết test duplicate event đồng thời bằng hai consumer thread.

### Bài 5

Viết test retry với dependency fail hai lần rồi success.

### Bài 6

Viết test permanent schema error đi vào DLT sau một lần.

### Bài 7

Viết test consumer crash sau side effect trước ack.

### Bài 8

Thiết kế chaos experiment broker unavailable với hypothesis, blast radius, stop condition và recovery.

### Bài 9

Thiết kế test hot partition và viết metrics cần thu thập.

### Bài 10

Viết format chaos report và điền cho một experiment giả lập.

---

## 45. Câu hỏi tự kiểm tra

1. Unit test queue khác CRUD test ở đâu?
2. Contract test bảo vệ điều gì?
3. Serialization test nên so sánh byte hay semantics?
4. Vì sao integration test cần broker thật trong một số scenario?
5. Testcontainers giải quyết vấn đề gì?
6. Khi nào Embedded Kafka đủ và khi nào cần container?
7. Invariant là gì?
8. Consumer crash sau side effect trước ack phải cho kết quả nào?
9. Vì sao DLT test cần test cả DLT publish failure?
10. Idempotency test cần chạy tuần tự và concurrent vì sao?
11. Ordering test phải nêu scope nào?
12. Chaos experiment cần những thành phần nào?
13. Blast radius là gì?
14. Stop condition dùng để làm gì?
15. Vì sao broker unavailable không nên test cùng lúc với database failure?
16. Làm thế nào phân biệt poison message với downstream outage?
17. Vì sao test nên dùng await thay vì sleep?
18. Chaos report cần evidence nào?
19. Tiêu chí nào chứng minh backlog đã recover?
20. Khi nào chaos test được coi là fail dù service không crash?

---

## 46. Checklist hoàn thành tuần

- [ ] Viết unit test producer.
- [ ] Viết unit test consumer.
- [ ] Viết contract test.
- [ ] Viết serialization test.
- [ ] Dựng database/broker bằng Testcontainers.
- [ ] Test message flow end-to-end.
- [ ] Test retry.
- [ ] Test DLQ/DLT.
- [ ] Test idempotency.
- [ ] Test ordering theo partition/key.
- [ ] Test consumer restart.
- [ ] Test broker unavailable.
- [ ] Test schema invalid.
- [ ] Test downstream timeout.
- [ ] Test slow consumer.
- [ ] Test hot partition.
- [ ] Test duplicate delivery.
- [ ] Chạy ít nhất ba chaos scenarios.
- [ ] Có hypothesis và stop condition.
- [ ] Có metrics/logs/traces.
- [ ] Có chaos report.
- [ ] Đạt invariant và recovery criteria.

---

## 47. Kết luận

Testing queue phải chứng minh cả happy path và failure path. Unit test bảo vệ logic nhanh; contract/serialization test bảo vệ message contract; integration test bảo vệ transaction và broker behavior; chaos test kiểm tra hệ thống khi component chết hoặc chậm.

Baseline test strategy:

    unit logic
      -> contract and serialization
      -> database and broker integration
      -> retry/DLQ/idempotency
      -> restart and unavailable dependency
      -> controlled chaos
      -> report and remediation

Một chaos experiment tốt luôn có:

- Hypothesis.
- Target rõ.
- Blast radius nhỏ nhất cần thiết.
- Steady-state metrics.
- Stop condition.
- Recovery plan.
- Invariant.
- Evidence.
- Follow-up action.

Mục tiêu không phải chứng minh hệ thống không bao giờ lỗi. Mục tiêu là chứng minh khi lỗi xảy ra, message không mất ngoài policy, side effect không duplicate ngoài policy, failure được quan sát, retry có giới hạn và hệ thống có thể khôi phục.

---

## 48. Tài liệu tham khảo

- Testcontainers for Java: https://java.testcontainers.org/
- Testcontainers Kafka module: https://java.testcontainers.org/modules/kafka/
- Spring Kafka testing: https://docs.spring.io/spring-kafka/reference/testing.html
- Spring Boot embedded Kafka testing: https://docs.spring.io/spring-boot/reference/messaging/kafka.html
- RabbitMQ publisher confirms: https://www.rabbitmq.com/docs/confirms
- RabbitMQ monitoring: https://www.rabbitmq.com/docs/monitoring
- OpenTelemetry logs and trace correlation: https://opentelemetry.io/docs/specs/otel/logs/


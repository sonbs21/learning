# Tuần 15: Retry, timeout, idempotency và DLQ operations

## 1. Mục tiêu

Sau bài học này, bạn có thể:

- Phân biệt lỗi retryable, permanent và unknown.
- Thiết kế retry budget thay vì retry vô hạn.
- Dùng exponential backoff và jitter.
- Đặt timeout theo từng dependency và toàn workflow.
- Kết hợp timeout, retry, circuit breaker, rate limit và backpressure.
- Thiết kế idempotent consumer và idempotency key.
- Phân biệt retry queue, DLQ, DLT và quarantine.
- Triage DLQ theo code, dữ liệu, dependency và contract.
- Replay message có giới hạn tốc độ.
- Xử lý poison message mà không làm nghẽn queue hoặc partition.
- Viết runbook DLQ production.
- Test consumer crash, downstream timeout, duplicate và retry exhaustion.

Nguyên tắc:

> Retry là cách mua thêm thời gian cho lỗi tạm thời. Retry không biến lỗi vĩnh viễn thành lỗi tạm thời, và không thay thế idempotency.

---

## 2. Failure model

Một message đi qua nhiều lớp:

    producer
      -> broker
      -> deserializer
      -> validator
      -> business handler
      -> database
      -> external dependency
      -> ack hoặc commit

Failure có thể xảy ra ở:

- Network timeout.
- Broker unavailable.
- Serialization hoặc deserialization error.
- Schema incompatibility.
- Validation error.
- Database deadlock.
- Database timeout.
- HTTP 500.
- HTTP 429.
- HTTP 400.
- Duplicate delivery.
- Consumer crash trước ack.
- Consumer crash sau side effect nhưng trước commit.
- Poison message luôn fail.

Không dùng một retry policy cho tất cả failure.

### Retryable error

Ví dụ:

- Connection reset.
- Broker unavailable tạm thời.
- Database deadlock.
- HTTP 503.
- HTTP 429 nếu có Retry-After.
- Temporary DNS hoặc network failure.
- Lock contention ngắn hạn.

### Permanent error

Ví dụ:

- Payload sai schema.
- Thiếu required field.
- Enum không hợp lệ.
- Business rule vi phạm vĩnh viễn.
- Authorization bị từ chối cố định.
- Message version không được consumer hỗ trợ.
- Database unique violation do dữ liệu sai.

### Unknown outcome

Ví dụ:

- Timeout nhưng downstream có thể đã xử lý.
- Connection reset sau khi server đã commit.
- Payment provider trả lỗi không rõ trạng thái.
- Broker request timeout nhưng record có thể đã ghi.

Unknown outcome không nên retry mù. Cần idempotency key, query trạng thái hoặc reconciliation.

---

## 3. Retry budget

Retry budget là giới hạn tài nguyên hệ thống dành cho retry.

Một budget nên giới hạn:

- Số lần retry.
- Tổng elapsed time.
- Số message đang retry.
- Request rate tới dependency.
- Tổng retry trong một khoảng thời gian.
- CPU và network tiêu thụ bởi retry.
- Retry riêng cho từng dependency hoặc tenant.

Ví dụ policy:

    initial delay: 500 ms
    maximum attempts: 4
    maximum elapsed time: 30 seconds
    maximum concurrency: 100
    retry only: 503, 429, timeout, deadlock
    do not retry: 400, 401, 403, schema error

Nếu có ba lần retry sau lần đầu, tổng số attempts là bốn. Cách đếm phải thống nhất trong metric và runbook.

### Per-message budget

Một message không nên giữ consumer thread vô hạn:

    attempt 1 -> immediate
    attempt 2 -> 1 second
    attempt 3 -> 5 seconds
    attempt 4 -> 30 seconds
    then DLT hoặc quarantine

### System-wide budget

Nếu có 100 consumer và mỗi consumer retry bốn lần, dependency có thể nhận một lượng request lớn hơn nhiều so với traffic gốc.

Cần kết hợp:

- Circuit breaker.
- Concurrency bulkhead.
- Rate limiter.
- Retry queue.
- Pause consumer.
- Load shedding.
- Downstream capacity limit.

Retry budget phải có metric và alert. Config tồn tại nhưng không quan sát được thì chưa đủ.

---

## 4. Exponential backoff

Công thức cơ bản:

    delay(n) = min(maxDelay, initialDelay * multiplier^n)

Ví dụ:

    initialDelay = 1 second
    multiplier = 2
    maxDelay = 60 seconds

    retry 1: 1 second
    retry 2: 2 seconds
    retry 3: 4 seconds
    retry 4: 8 seconds
    retry 5: 16 seconds
    retry 6: 32 seconds
    retry 7: 60 seconds

Lợi ích:

- Giảm áp lực lên dependency đang lỗi.
- Cho hệ thống thời gian recover.
- Tránh retry nóng.
- Tách retry khỏi traffic bình thường.

Rủi ro:

- Message latency tăng.
- Nhiều message cùng retry có thể tạo burst mới.
- Sleep trong consumer thread làm giảm capacity.
- Retry theo partition có thể block message sau.

Đặt maxDelay đủ nhỏ để hệ thống vẫn có visibility. Một message “đang retry” quá lâu có thể làm SLA bị phá mà không có alert rõ.

---

## 5. Jitter

Jitter thêm random delay để các worker không retry cùng thời điểm.

Không jitter:

    worker A: 10:00:01
    worker B: 10:00:01
    worker C: 10:00:01

Có jitter:

    worker A: 10:00:00.7
    worker B: 10:00:01.4
    worker C: 10:00:01.9

### Full jitter

    delay = random(0, exponentialDelay)

### Equal jitter

    delay = exponentialDelay / 2
         + random(0, exponentialDelay / 2)

Jitter đặc biệt hữu ích khi:

- Nhiều consumer cùng fail vì một dependency.
- Service restart đồng thời.
- Retry queue giải phóng nhiều message cùng lúc.
- Provider có rate limit.

Log scheduled retry time và attempt number để debug. Không log secret hoặc thông tin nhạy cảm chỉ vì cần debug random delay.

---

## 6. Timeout theo dependency

Timeout nên được đặt ở từng lớp:

    HTTP connect timeout
    HTTP read timeout
    database query timeout
    broker request timeout
    message processing timeout
    overall workflow deadline

Không có timeout, attempt đầu tiên có thể treo vô hạn và retry không bao giờ bắt đầu.

### Deadline propagation

Giả sử workflow có deadline 10 giây:

    overall deadline = 10 seconds
      database call = 2 seconds
      payment call = 4 seconds
      retry reserve = 2 seconds
      response overhead = 2 seconds

Không để mỗi dependency tự dùng 10 giây. Tổng latency sẽ vượt deadline.

Có thể truyền:

    x-request-deadline
    x-time-budget-ms
    correlationId
    traceId

Server phải áp max deadline riêng và không tin tuyệt đối deadline từ client.

### Timeout không phải cancellation hoàn toàn

Client timeout chỉ dừng chờ. Server hoặc provider vẫn có thể đang xử lý:

    request timeout
    provider may have committed
    retry needs idempotency key or status query

Timeout thường tạo unknown outcome, đặc biệt với payment, shipping và external write.

---

## 7. Retry và timeout kết hợp

Retry không được vượt overall deadline:

    while remaining_time > minimum_attempt_time:
        call dependency with bounded timeout
        if retryable:
            wait bounded backoff
        else:
            fail

Ví dụ:

    attempt 1 timeout = 2 seconds
    backoff = 500 ms
    attempt 2 timeout = 2 seconds
    backoff = 1 second
    attempt 3 timeout = 2 seconds
    total ~= 7.5 seconds

Nếu SLA là 5 giây, policy trên không phù hợp.

Sai lầm phổ biến:

- Timeout mỗi call 30 giây.
- Retry năm lần.
- Không có workflow deadline.
- Message giữ consumer thread hàng phút.
- Queue lag tăng.
- Scale thêm consumer làm downstream quá tải hơn.

Policy đúng cần:

- Per-call timeout.
- Per-message retry budget.
- Overall processing deadline.
- DLT khi hết budget.
- Metric timeout và retry exhaustion.

---

## 8. Circuit breaker

Circuit breaker ngăn gọi dependency đang hỏng.

Ba trạng thái:

    CLOSED
      request bình thường

    OPEN
      fail fast, không gọi dependency

    HALF_OPEN
      cho một số probe request kiểm tra recovery

Chuyển CLOSED sang OPEN khi:

- Error rate vượt ngưỡng.
- Timeout rate vượt ngưỡng.
- Slow call rate vượt ngưỡng.
- Consecutive failures vượt ngưỡng.

Khi OPEN:

- Không retry ngay trong consumer thread.
- Đẩy message vào delayed retry.
- Giảm concurrency.
- Pause consumer nếu cần.
- Alert dependency owner.

Circuit breaker không thay thế retry. Một flow hợp lý:

    transient error:
      retry ngắn với budget nhỏ

    dependency outage:
      circuit open
      chuyển message sang delayed retry
      không spam dependency

Phân biệt:

- Circuit breaker bảo vệ remote call.
- Retry queue lưu message chờ xử lý lại.
- DLQ giữ message không thể xử lý tự động.

---

## 9. Rate limit và backpressure

Nếu incoming rate lớn hơn processing rate:

    incoming rate > processing rate
    -> backlog tăng
    -> latency tăng
    -> timeout tăng
    -> retry tăng
    -> backlog tăng nhanh hơn

Biện pháp:

- Giới hạn producer rate.
- Giới hạn consumer concurrency.
- Rate limit remote call.
- Bulkhead cho dependency.
- Batch processing có giới hạn.
- Pause/resume consumer.
- Retry queue tách khỏi main queue.
- Scale theo partition hoặc queue capacity.
- Hạn chế prefetch hoặc in-flight message.
- Shed load với event không quan trọng.

### Rate limit producer

Các lựa chọn:

- Token bucket.
- Leaky bucket.
- Per-tenant quota.
- Per-topic limit.
- Adaptive concurrency.
- Queue depth based throttling.

Không retry HTTP 429 mà bỏ qua Retry-After hoặc rate limit policy.

Backpressure không chỉ là giảm tốc consumer. Nó là tín hiệu cho upstream biết hệ thống phía sau đang đầy.

---

## 10. Idempotency

Idempotency nghĩa là cùng operation chạy nhiều lần vẫn tạo cùng business result như chạy một lần.

Ví dụ:

    capture payment with key order-123-capture-v1
    capture payment with same key again
    result: one capture

Idempotency không có nghĩa:

- Message chỉ giao một lần.
- Handler chỉ gọi một lần.
- Broker không retry.
- Không có duplicate log.
- External side effect tự nhiên idempotent.

### Các lớp idempotency

Transport:

- Event ID.
- Message ID.
- Original offset hoặc delivery metadata.

Business:

- Create order key.
- Capture payment key.
- Reserve inventory key.
- Send email key.

Database:

- Unique constraint.
- Inbox table.
- Upsert.
- Conditional state transition.

External:

- Provider idempotency key.
- Status query.
- Reconciliation.

---

## 11. Thiết kế idempotency key

Key tốt cần:

- Ổn định qua retry cùng intent.
- Khác nhau giữa hai intent hợp lệ.
- Có scope rõ.
- Có retention đủ dài.
- Không chứa secret.
- Không random lại trong mỗi retry.
- Không collision.

Ví dụ:

    create-order:{customerId}:{clientRequestId}
    capture-payment:{orderId}:attempt-1
    reserve-inventory:{orderId}:v1
    send-email:{orderId}:confirmation

Nếu cùng order có hai payment attempt hợp lệ, key phải phân biệt attempt.

Bảng:

    create table idempotency_records (
        scope varchar(100) not null,
        idempotency_key varchar(300) not null,
        request_hash varchar(128) not null,
        status varchar(30) not null,
        response_snapshot jsonb,
        resource_id varchar(200),
        created_at timestamp with time zone not null,
        expires_at timestamp with time zone,
        primary key (scope, idempotency_key)
    );

Nếu request retry dùng cùng key nhưng payload khác:

- Không dùng kết quả cũ âm thầm.
- So sánh request_hash.
- Trả conflict.
- Ghi audit nếu cần.

---

## 12. Database idempotency

### Unique constraint

    create unique index uq_payment_capture_key
    on payment_attempts (idempotency_key);

### Insert-if-absent

    insert into processed_messages(
        consumer_name, message_id, processed_at
    )
    values (?, ?, now())
    on conflict (consumer_name, message_id) do nothing;

### Conditional state transition

    update orders
    set status = 'CONFIRMED',
        version = version + 1
    where id = ?
      and status = 'PAYMENT_CAPTURED';

Nếu affected rows bằng 0, có thể là:

- Event duplicate.
- Event out-of-order.
- State đã chuyển.
- State không hợp lệ.

Không coi mọi affected rows bằng 0 là retryable.

---

## 13. External side effect và unknown outcome

Ví dụ payment provider:

    send capture request
    wait for response
    network timeout

Không biết provider:

- Chưa nhận request.
- Đã nhận nhưng chưa xử lý.
- Đã xử lý thành công nhưng response mất.
- Đã xử lý một phần.

Retry mù có thể double charge.

Flow an toàn:

    capture with stable idempotency key
    timeout
    query provider by key or payment reference
    if succeeded:
        mark local succeeded
    if pending:
        wait or retry status query
    if not found:
        decide whether safe to send again

Nếu provider không hỗ trợ idempotency:

- Tạo payment attempt trước.
- Lưu provider request reference.
- Reconcile định kỳ.
- Không tự động retry write operation với unknown outcome.
- Escalate manual cho payment hoặc inventory quan trọng.

---

## 14. Retry topology

### In-process retry

Ưu điểm:

- Đơn giản.
- Phù hợp delay ngắn.
- Không cần queue thêm.

Nhược điểm:

- Giữ worker.
- Block partition hoặc queue.
- Restart có thể mất retry state.
- Không phù hợp delay dài.

### Broker requeue

Ưu điểm:

- Durable theo broker.
- Dễ triển khai.

Nhược điểm:

- Có thể retry nóng.
- Poison message quay vòng.
- Có thể phá ordering.
- Cần metadata retry count.

### Delayed retry queue hoặc topic

    main
      -> retry-10s
      -> retry-1m
      -> retry-10m
      -> DLT

Ưu điểm:

- Delay durable.
- Main queue ít bị block.
- Quan sát schedule dễ hơn.

Nhược điểm:

- Nhiều topology.
- Cần preserve metadata.
- Cần tránh vòng lặp vô hạn.
- Kafka retry topic làm ordering theo partition phức tạp hơn.

### Scheduled retry store

Lưu message và next_attempt_at trong database hoặc scheduler.

Ưu điểm:

- Audit, query và cancel dễ.
- Policy phức tạp linh hoạt.

Nhược điểm:

- Tăng database load.
- Cần scheduler, lease và dedup.

Chọn topology theo delay, ordering, throughput và năng lực vận hành.

---

## 15. DLQ, DLT và quarantine

Phân biệt:

- Retry queue: còn kỳ vọng xử lý tự động.
- DLQ: failed message cần triage.
- DLT: failed Kafka record theo topic convention.
- Quarantine: message bị cô lập, không tự redrive.
- Archive: lưu dài hạn, không nhất thiết là nơi xử lý lỗi.

Mỗi DLQ record nên có:

    originalMessageId
    eventType
    schemaVersion
    originalDestination
    originalPartition
    originalOffset
    originalRoutingKey
    consumerName
    consumerVersion
    attemptCount
    firstFailedAt
    lastFailedAt
    failureCategory
    exceptionType
    correlationId
    traceId
    replayCount

Không đưa vào metadata:

- Password.
- Access token.
- Full card number.
- Secret.
- Raw authorization header.
- PII không cần cho triage.

Payload hash giúp nhận diện cùng payload mà không log raw body.

DLQ không phải thùng rác. Mỗi message phải có owner, reason và next action.

---

## 16. Phân loại DLQ

### Code defect

Ví dụ:

- Null pointer.
- Bug mapping.
- Logic state transition sai.
- Regression sau deploy.

Xử lý:

- Sửa code.
- Viết regression test.
- Deploy fix.
- Replay batch nhỏ.
- Theo dõi duplicate side effect.

### Invalid data

Ví dụ:

- Thiếu required field.
- Sai enum.
- Amount âm.
- Schema version không hỗ trợ.

Xử lý:

- Không retry vô hạn.
- Sửa producer hoặc migrate message.
- Manual correction nếu cần.
- Replay message đã sửa vào destination riêng.

### Dependency outage

Ví dụ:

- Payment provider 503.
- Database unavailable.
- Rate limit.
- DNS outage.

Xử lý:

- Retry có budget.
- Circuit breaker.
- Chờ recover.
- Redrive rate-limited.

### Contract mismatch

Ví dụ:

- Producer deploy schema breaking.
- Consumer dùng schema cũ.
- Registry hoặc serializer lỗi.

Xử lý:

- Dừng rollout.
- Sửa compatibility.
- Deploy consumer hỗ trợ version mới.
- Replay sau khi contract ổn.

### Security hoặc policy

Ví dụ:

- PII không được phép.
- Tenant không có quyền.
- Signature invalid.

Xử lý:

- Quarantine.
- Security triage.
- Không replay tự động.
- Có audit.

---

## 17. Poison message

Poison message luôn fail với cùng code/path.

Dấu hiệu:

- Retry count tăng liên tục.
- Cùng message ID xuất hiện nhiều lần.
- Queue depth không giảm.
- Cùng stack trace lặp lại.
- Partition hoặc queue bị block.
- CPU tăng do retry nóng.

Nguyên nhân:

- Payload hỏng.
- Schema không tương thích.
- Business state không hợp lệ.
- Bug deterministic.
- Dependency request không thể thành công.
- Message quá lớn.

Cách xử lý:

- Retry limit.
- DLQ/DLT.
- Failure metadata.
- Không requeue vô hạn.
- Alert theo delivery attempt.
- Không replay trước khi fix hoặc migration.

Trong Kafka, record lỗi có thể block partition nếu handler không recover đúng. Trong RabbitMQ, requeue vô hạn tạo loop. Trong SQS, visibility timeout và maxReceiveCount quyết định khi message vào DLQ.

---

## 18. DLQ triage runbook

### Bước 1: đo phạm vi

Ghi nhận:

- DLQ count.
- Rate message mới.
- Oldest message age.
- Top failure reason.
- Top event type.
- Top producer.
- Top consumer.
- Partition, queue hoặc group.
- Business impact.

### Bước 2: lấy mẫu an toàn

- Lấy batch nhỏ.
- Redact PII.
- Kiểm tra message ID.
- Kiểm tra event type.
- Kiểm tra schema version.
- Kiểm tra attempt count.
- Kiểm tra original destination.
- Kiểm tra correlation ID.

### Bước 3: phân loại

    code defect
    invalid data
    dependency outage
    contract mismatch
    security or policy
    unknown outcome

### Bước 4: chọn remediation

- Code: fix, test, deploy.
- Data: migrate hoặc reject.
- Dependency: recover, redrive có rate.
- Contract: fix schema/consumer.
- Security: quarantine, không auto replay.
- Unknown: reconcile trước.

### Bước 5: replay canary

- Một message.
- Năm message.
- Batch nhỏ.
- Rate giới hạn.
- Theo dõi side effect.
- Dừng nếu error threshold vượt ngưỡng.

### Bước 6: replay mở rộng

- Có operator approval.
- Có replay ID.
- Có checkpoint.
- Có pause/resume.
- Có audit.
- Có dashboard.
- Có compensation plan.

### Bước 7: đóng incident

- Root cause.
- Timeline.
- Message impact.
- Side effect impact.
- Retry/DLQ effectiveness.
- Action items.
- Postmortem.

Không replay toàn bộ DLQ trực tiếp vào production nếu chưa kiểm tra idempotency và rate limit.

---

## 19. Replay có kiểm soát

Replay là operation có thể tạo side effect lần nữa. Trước replay cần biết:

- Message có thể đã xử lý thành công nhưng timeout không?
- Consumer có Inbox hoặc idempotency không?
- External provider có idempotency key không?
- Replay có tạo event downstream mới không?
- Có dry-run không?
- Có output destination riêng không?
- Có rate limit không?
- Có thể pause giữa chừng không?

Các mode:

### Dry run

Deserialize và validate, không tạo side effect.

### Shadow processing

Chạy consumer hoặc database test để so sánh output.

### Canary replay

Replay một message hoặc nhóm nhỏ.

### Controlled batch

Replay theo giới hạn messages/second.

### Full replay

Chỉ sau khi canary ổn, có approval và monitoring.

Metadata:

    replayId
    originalMessageId
    replayedAt
    replayedBy
    replayReason
    replayAttempt

Không thay eventId gốc nếu Inbox cần nhận diện cùng event; thêm replay metadata riêng.

---

## 20. Backpressure khi replay

Replay có thể tạo traffic lớn hơn production:

    normal traffic = 100 msg/s
    replay traffic = 10,000 msg/s
    downstream capacity = 200 msg/s

Hậu quả:

- Queue backlog tăng.
- Timeout tăng.
- Retry storm.
- DLQ mới tăng.
- Duplicate khó kiểm soát.

Replay controller nên có:

- Rate limit.
- Concurrency limit.
- Pause/resume.
- Error threshold.
- Circuit breaker.
- Progress checkpoint.
- Dead-letter riêng cho replay failure.
- Dashboard.

Tốc độ an toàn quan trọng hơn tốc độ lý thuyết.

---

## 21. Spring AMQP và RabbitMQ

Spring AMQP cho phép retry exception và route message tới DLQ tùy container và broker configuration. Khi listener throw exception, message có thể requeue, discard hoặc dead-letter. [Spring AMQP exception handling](https://docs.spring.io/spring-amqp/reference/4.0/amqp/receiving-messages/async-annotation-driven/error-handling.html)

Ví dụ concept:

~~~java
@Bean
public RetryOperationsInterceptor retryInterceptor() {
    return RetryInterceptorBuilder
        .stateless()
        .maxAttempts(4)
        .backOffOptions(1000L, 2.0, 10000L)
        .recoverer(messageRecoverer())
        .build();
}
~~~

Cần nhớ:

- Tổng attempts phải được document.
- Backoff có max interval.
- Recoverer quyết định DLQ hoặc reject.
- Lỗi fatal không nên requeue vô hạn.
- Message cần message ID ổn định.
- Retry consumer không thay publisher confirm.

RabbitMQ phân biệt consumer acknowledgement và publisher confirm; hai cơ chế bảo vệ hai chiều khác nhau của flow. [RabbitMQ confirms](https://www.rabbitmq.com/docs/confirms)

Khi requeue:

- Có retry count.
- Có dead-letter exchange.
- Có correlation ID.
- Có phân loại exception.
- Có cơ chế tránh poison loop.

---

## 22. Spring Kafka và DLT

Spring Kafka DefaultErrorHandler dùng BackOff để retry; DeadLetterPublishingRecoverer có thể publish record hết retry vào DLT. [Spring Kafka error handling](https://docs.spring.io/spring-kafka/reference/4.0-SNAPSHOT/kafka/annotation-error-handling.html)

Ví dụ concept:

~~~java
@Bean
public DefaultErrorHandler kafkaErrorHandler(
        KafkaTemplate<String, Object> template) {

    DeadLetterPublishingRecoverer recoverer =
        new DeadLetterPublishingRecoverer(
            template,
            (record, exception) ->
                new TopicPartition(
                    record.topic() + ".DLT",
                    record.partition()
                )
        );

    FixedBackOff backOff = new FixedBackOff(1000L, 2L);

    return new DefaultErrorHandler(recoverer, backOff);
}
~~~

Cần kiểm tra version Spring Kafka thực tế trước khi copy API.

Review:

- BackOff có đúng số attempts không.
- DLT giữ original topic, partition và offset không.
- Exception metadata có đủ không.
- Deserialization error có bị retry vô hạn không.
- Batch listener có semantics gì.
- Recoverer fail thì state thế nào.
- Delay dài có cần pause container không.

Không retry schema/deserialization error vô hạn. Hãy đưa record vào DLT với metadata đủ để sửa producer hoặc migrate dữ liệu.

---

## 23. SQS DLQ operations

SQS dùng redrive policy và maxReceiveCount để chuyển message sang DLQ sau số lần receive không thành công. DLQ phải cùng loại Standard hoặc FIFO với source queue. [SQS DLQ configuration](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-configure-dead-letter-queue.html)

Cần nhớ:

- Receive không đồng nghĩa xử lý thành công.
- Delete mới là xác nhận hoàn tất.
- Visibility timeout hết có thể làm message xuất hiện lại.
- maxReceiveCount quá thấp đẩy message quá sớm.
- maxReceiveCount quá cao làm poison message retry quá lâu.
- Redrive cần IAM permission.
- Replay cần giới hạn rate.
- FIFO DLQ có thể ảnh hưởng ordering của flow.

Flow:

    source queue
      -> visibility timeout
      -> receive count tăng
      -> maxReceiveCount exceeded
      -> DLQ
      -> triage
      -> controlled redrive

AWS có tài liệu riêng về permission và troubleshooting khi redrive từ DLQ. [SQS DLQ troubleshooting](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/troubleshooting-dlq-redrive.html)

---

## 24. Retry policy theo loại lỗi

| Lỗi | Retry? | Delay | Sau exhaustion |
|---|---|---|---|
| HTTP 503 | Có | Exponential + jitter | DLQ hoặc retry queue |
| HTTP 429 | Có | Theo Retry-After | Giảm rate |
| HTTP 400 | Không | Không | DLQ |
| Database deadlock | Có | Ngắn | DLQ nếu hết budget |
| Database constraint invalid | Thường không | Không | DLQ |
| Schema invalid | Không | Không | DLT |
| Network timeout | Có nhưng unknown | Có | Query status |
| Payment timeout | Status query | Có | Reconcile |
| Auth 401 | Thường không | Không | Alert config |
| Null pointer | Không vô hạn | Có thể một retry | DLT |
| Broker unavailable | Có | Backoff + breaker | Durable retry |

Bảng này là baseline; domain criticality và dependency contract có thể thay đổi policy.

---

## 25. Retry không được phá ordering

Nếu cùng key phải xử lý tuần tự:

    A1
    A2
    A3

A1 retry lâu nhưng A2 xử lý trước có thể làm state sai.

Lựa chọn:

- Block cùng key đến khi A1 thành công.
- Đưa cả key vào retry lane.
- Dùng sequence và gap detection.
- Giữ cùng partition/key.
- Thiết kế event commutative.
- Hy sinh strict ordering nếu business cho phép.

Kafka retry topic cần xem:

- Partition mapping.
- Original offset.
- Key.
- Consumer group.
- DLT routing.
- Lag ở main và retry topic.

Không thể retry A1 ở nơi khác, xử lý A2 tự do rồi vẫn tuyên bố ordering được bảo đảm.

---

## 26. Retry storm

Retry storm:

    dependency fails
      -> consumer throws
      -> immediate retry
      -> more load
      -> dependency worsens
      -> more timeout
      -> more retry

Giảm rủi ro bằng:

- Exponential backoff.
- Jitter.
- Retry budget.
- Circuit breaker.
- Concurrency limit.
- Rate limit.
- Delayed retry queue.
- Pause consumer.
- Alert trước khi dependency sập.
- Không retry 4xx hoặc schema error.

Metric:

- Original request rate.
- Retry request rate.
- Retry/original ratio.
- Dependency error rate.
- Timeout rate.
- Queue lag.
- DLQ rate.
- Circuit state.
- In-flight count.

Nếu retry/original ratio tăng mạnh, giảm retry trước khi scale consumer.

---

## 27. Retry ở nhiều tầng

Có thể có:

    HTTP client retry
    handler retry
    message consumer retry
    database transaction retry
    provider retry

Nếu mỗi tầng retry độc lập:

    HTTP 3 attempts
    x consumer 4 attempts
    x provider 3 attempts
    = tối đa 36 provider calls

Thiết kế:

- Chọn một layer owner của retry.
- Layer khác có retry nhỏ hoặc tắt.
- Truyền cùng idempotency key.
- Dùng overall deadline.
- Log attempt ở từng layer.
- Không tạo key mới cho cùng intent.

Đúng:

    message attempt 2
      handler attempt 1
        provider request with same key

Sai:

    message attempt 2
      provider request with random new key

---

## 28. Runbook DLQ production

### 1. Đếm message theo nguyên nhân

    DLQ count
    rate entering DLQ
    oldest message age
    top failure category
    top event type
    top producer
    top consumer

### 2. Lấy mẫu payload an toàn

- Chọn batch nhỏ.
- Redact PII.
- Kiểm tra message ID.
- Kiểm tra schema version.
- Kiểm tra attempt count.
- Kiểm tra correlation ID.
- Kiểm tra original destination.

### 3. Phân loại

    code
    data
    dependency
    contract
    security
    unknown outcome

### 4. Remediation

- Code: fix, test, deploy.
- Data: migrate hoặc reject.
- Dependency: recover, redrive rate-limited.
- Contract: fix schema/consumer.
- Security: quarantine.
- Unknown: reconcile trước.

### 5. Replay canary

- Một message.
- Năm message.
- Batch nhỏ.
- Rate giới hạn.
- Theo dõi side effect.
- Dừng nếu error threshold vượt ngưỡng.

### 6. Replay mở rộng

- Có operator approval.
- Có replayId.
- Có checkpoint.
- Có pause/resume.
- Có audit.
- Có dashboard.
- Có compensation plan.

### 7. Đóng incident

- Root cause.
- Timeline.
- Message impact.
- Side effect impact.
- Retry/DLQ effectiveness.
- Action items.
- Postmortem.

---

## 29. Lab 1: Retry classification

Tạo handler với:

    TransientNetworkException
    RateLimitException
    SchemaValidationException
    BusinessRuleException
    UnknownPaymentOutcomeException

Policy:

- Transient network: retry.
- Rate limit: retry theo delay.
- Schema validation: DLT.
- Business rule: DLT hoặc business rejection.
- Unknown payment: status query/reconciliation.

Deliverable:

- Exception hierarchy.
- Retry matrix.
- Unit test.
- Metric counter.
- Log an toàn.

---

## 30. Lab 2: Backoff và jitter

Mô phỏng 100 worker:

    Case A: fixed delay 1 second, no jitter
    Case B: exponential, no jitter
    Case C: exponential + full jitter
    Case D: exponential + max delay + retry budget

Đo:

- Request timestamp distribution.
- Peak requests/second.
- Dependency recovery time.
- Total attempts.
- Completion latency.
- Retry/original ratio.

Câu hỏi:

- Case nào tạo burst?
- Case nào bảo vệ dependency tốt?
- Case nào làm latency tăng quá nhiều?
- Budget nào phù hợp SLA?

---

## 31. Lab 3: Timeout và unknown outcome

Fake payment provider:

- Case A trả success.
- Case B trả 503.
- Case C delay rồi trả success.
- Case D xử lý success nhưng đóng connection trước response.
- Case E timeout và provider status pending.

Consumer phải:

- Dùng idempotency key cố định.
- Không capture hai lần.
- Query status khi unknown.
- Ghi payment attempt.
- Chỉ phát PaymentCaptured một lần.

Deliverable:

- Payment state machine.
- Provider request log.
- Idempotency table.
- Timeout test.
- Reconciliation job.

---

## 32. Lab 4: Circuit breaker và backpressure

Downstream capacity 20 request/s. Producer gửi 100 message/s.

Thử:

    Case A: không rate limit, không breaker
    Case B: retry immediate
    Case C: backoff + jitter
    Case D: breaker + delayed retry queue
    Case E: pause consumer khi circuit mở

Đo:

- Downstream error rate.
- Queue depth.
- Retry rate.
- Consumer throughput.
- Oldest message age.
- Recovery time.

Mục tiêu là hệ thống không sụp dây chuyền, không nhất thiết queue depth luôn bằng 0.

---

## 33. Lab 5: DLQ triage và controlled replay

Tạo DLQ:

    20 schema errors
    20 transient dependency errors
    10 invalid business data
    5 unknown payment outcomes
    5 code defects

Runbook:

1. Count theo category.
2. Lấy mẫu an toàn.
3. Sửa code hoặc dữ liệu giả lập.
4. Replay một message.
5. Replay năm message.
6. Replay batch rate-limited.
7. Theo dõi duplicate và side effect.
8. Ghi report.

Report gồm:

- replayId.
- operator.
- reason.
- source DLQ.
- destination.
- rate.
- start/end time.
- success/failure count.

---

## 34. Lab 6: Poison message

Tạo một message luôn làm handler throw.

Thử:

- Requeue không giới hạn.
- Retry limit 3.
- Delayed retry queue.
- DLT/quarantine.
- Alert theo delivery attempt.
- Pause/resume consumer.

Quan sát:

- Queue depth.
- Consumer throughput.
- CPU.
- Redelivery.
- Message age.
- DLQ count.

Phải chứng minh retry vô hạn làm hệ thống tệ hơn lỗi ban đầu.

---

## 35. Lab 7: Spring Kafka DLT

Cấu hình concept:

~~~java
@Bean
public DefaultErrorHandler errorHandler(
        KafkaTemplate<String, Object> template) {

    DeadLetterPublishingRecoverer recoverer =
        new DeadLetterPublishingRecoverer(
            template,
            (record, exception) ->
                new TopicPartition(
                    record.topic() + ".DLT",
                    record.partition()
                )
        );

    return new DefaultErrorHandler(
        recoverer,
        new FixedBackOff(1000L, 2L)
    );
}
~~~

Kiểm tra:

- Tổng attempts thực tế.
- Original topic/partition/offset.
- Exception metadata.
- Deserialization error.
- Batch listener behavior.
- Recoverer failure.
- Delay dài và container pause.

Spring Kafka mô tả DefaultErrorHandler dùng BackOff và DeadLetterPublishingRecoverer cho recovery; kiểm tra API theo version thực tế. [Spring Kafka error handling](https://docs.spring.io/spring-kafka/reference/4.0-SNAPSHOT/kafka/annotation-error-handling.html)

---

## 36. Lab 8: SQS receive count và DLQ

Thiết kế:

    orders-source
      -> visibility timeout
      -> maxReceiveCount
      -> orders-dlq

Thử:

- Handler thành công và delete.
- Handler fail trước delete.
- Visibility timeout quá ngắn.
- maxReceiveCount thấp.
- Redrive một message.
- Redrive batch nhỏ.

Quan sát:

- Receive count.
- Oldest message age.
- Source depth.
- DLQ depth.
- Redrive rate.
- Duplicate side effect.

AWS mô tả maxReceiveCount, DLQ cùng loại với source queue và các permission cần cho redrive. [SQS DLQ configuration](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-configure-dead-letter-queue.html)

---

## 37. Checklist retry

- [ ] Phân loại retryable, permanent và unknown.
- [ ] Có max attempts.
- [ ] Có max elapsed time.
- [ ] Có exponential backoff.
- [ ] Có jitter.
- [ ] Có retry budget toàn hệ thống.
- [ ] Không retry schema/validation vô hạn.
- [ ] Tôn trọng Retry-After.
- [ ] Có circuit breaker.
- [ ] Có rate limit.
- [ ] Có backpressure.
- [ ] Có retry/original metric.
- [ ] Có timeout từng dependency.
- [ ] Có overall deadline.
- [ ] Không nhân retry không kiểm soát giữa các layer.

## 38. Checklist idempotency

- [ ] Event/message identity ổn định.
- [ ] Business idempotency key có scope.
- [ ] Có unique constraint.
- [ ] Có Inbox hoặc processed table.
- [ ] Có request hash.
- [ ] External provider có idempotency.
- [ ] Unknown outcome có reconciliation.
- [ ] Replay không tạo side effect đôi.
- [ ] Retention key đủ dài.
- [ ] Không chứa secret trong key.

## 39. Checklist DLQ operations

- [ ] DLQ có owner.
- [ ] Có failure category.
- [ ] Có original destination.
- [ ] Có attempt count.
- [ ] Có correlation ID.
- [ ] Không log PII/secret thừa.
- [ ] Có alert DLQ count và rate.
- [ ] Có triage runbook.
- [ ] Có quarantine cho security/unknown.
- [ ] Có canary replay.
- [ ] Có replay rate limit.
- [ ] Có audit log.
- [ ] Có pause/resume.
- [ ] Có postmortem.
- [ ] Có retention policy.

---

## 40. Bài tập

### Bài 1

Phân loại và đề xuất retry cho:

- HTTP 503.
- HTTP 400.
- HTTP 429 có Retry-After 10 giây.
- Database deadlock.
- Database unique violation.
- Kafka deserialization error.
- Payment timeout.
- OAuth 401.
- DNS timeout.
- Inventory insufficient.

### Bài 2

Với initial delay 500 ms, multiplier 2, max delay 30 giây, tính delay cho sáu retry. Thêm full jitter.

### Bài 3

Một message đi qua HTTP client, handler và provider; mỗi tầng retry riêng. Tính số request tối đa và thiết kế lại retry ownership.

### Bài 4

Thiết kế idempotency key cho create order, reserve inventory, capture payment và send email.

### Bài 5

Payment timeout nhưng provider có thể đã capture. Viết state machine và reconciliation flow.

### Bài 6

Thiết kế DLQ metadata không chứa PII nhưng đủ triage.

### Bài 7

Kafka partition bị block bởi poison record. So sánh retry inline, retry topic và DLT.

### Bài 8

SQS consumer xử lý 45 giây nhưng visibility timeout 30 giây. Phân tích duplicate và đề xuất policy.

### Bài 9

Thiết kế runbook replay 10.000 DLQ messages với tốc độ tối đa 50 messages/second.

### Bài 10

Viết test chứng minh duplicate không tạo duplicate email, payment hoặc inventory side effect.

---

## 41. Câu hỏi tự kiểm tra

1. Retryable, permanent và unknown khác nhau thế nào?
2. Vì sao timeout có thể không đồng nghĩa operation thất bại?
3. Retry budget gồm những giới hạn nào?
4. Exponential backoff giải quyết vấn đề gì?
5. Jitter ngăn hiện tượng nào?
6. Vì sao retry vô hạn tạo retry storm?
7. Circuit breaker khác DLQ thế nào?
8. Rate limit và backpressure liên quan ra sao?
9. EventId khác business idempotency key thế nào?
10. Vì sao request hash cần thiết với cùng idempotency key?
11. Vì sao Inbox cần unique constraint?
12. Replay có thể tạo duplicate side effect khi nào?
13. Poison message cần quarantine khi nào?
14. Retry inline khác delayed retry queue thế nào?
15. DLT có phải archive không?
16. Vì sao không replay toàn bộ DLQ cùng lúc?
17. SQS maxReceiveCount và visibility timeout phối hợp thế nào?
18. Kafka retry topic ảnh hưởng ordering ra sao?
19. Vì sao các tầng retry độc lập có thể nhân số request?
20. DLQ runbook cần những bước nào?

---

## 42. Checklist hoàn thành tuần

- [ ] Phân loại retryable, permanent và unknown.
- [ ] Tính exponential backoff.
- [ ] Thêm jitter.
- [ ] Đặt timeout theo dependency.
- [ ] Tính overall deadline.
- [ ] Thiết kế retry budget.
- [ ] Hiểu circuit breaker.
- [ ] Hiểu rate limit và backpressure.
- [ ] Thiết kế idempotency key.
- [ ] Tạo unique constraint hoặc Inbox.
- [ ] Xử lý unknown external outcome.
- [ ] Phân biệt retry queue, DLQ, DLT và quarantine.
- [ ] Viết DLQ triage runbook.
- [ ] Replay canary batch nhỏ.
- [ ] Test poison message.
- [ ] Cấu hình Spring Kafka DLT.
- [ ] Hiểu Spring AMQP reject/requeue.
- [ ] Thử SQS receive count và redrive.
- [ ] Đo retry rate, DLQ rate và oldest age.
- [ ] Viết postmortem cho failure scenario.

---

## 43. Kết luận

Một hệ thống queue đáng tin không phải là hệ thống retry nhiều nhất. Đó là hệ thống biết:

- Lỗi nào nên retry.
- Retry bao nhiêu lần.
- Retry trong bao lâu.
- Khi nào dừng.
- Khi nào mở circuit.
- Khi nào đẩy vào DLQ.
- Khi nào quarantine.
- Khi nào replay.
- Làm thế nào replay không tạo duplicate side effect.

Baseline:

    classify error
      -> apply timeout
      -> retry with bounded exponential backoff + jitter
      -> protect dependency with circuit breaker/rate limit
      -> preserve idempotency key
      -> send exhausted message to DLQ/DLT
      -> triage safely
      -> canary replay
      -> monitor and close incident

Retry là một phần của failure handling, không phải toàn bộ reliability architecture. Idempotency bảo vệ business effect; DLQ bảo vệ pipeline khỏi poison message; runbook biến failure thành quy trình có thể khôi phục.

---

## 44. Tài liệu tham khảo

- Spring AMQP resilience and retry: https://docs.spring.io/spring-amqp/reference/amqp/resilience-recovering-from-errors-and-broker-failures.html
- Spring AMQP exception handling: https://docs.spring.io/spring-amqp/reference/4.0/amqp/receiving-messages/async-annotation-driven/error-handling.html
- Spring Kafka error handling: https://docs.spring.io/spring-kafka/reference/4.0-SNAPSHOT/kafka/annotation-error-handling.html
- RabbitMQ publisher confirms: https://www.rabbitmq.com/docs/confirms
- Amazon SQS DLQ configuration: https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-configure-dead-letter-queue.html
- Amazon SQS DLQ operations: https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-dead-letter-queues.html
- Amazon SQS DLQ troubleshooting: https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/troubleshooting-dlq-redrive.html


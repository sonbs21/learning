# Tuần 10 — Idempotency, consistency và transactional outbox

> At-least-once bảo vệ khỏi mất message nhưng chấp nhận duplicate. Tuần này giải quyết hai bài toán: xử lý lại message mà business result chỉ có hiệu lực một lần, và không để database commit thành công nhưng event bị mất.

## 1. Kết quả cần đạt

Sau bài học, bạn có thể:

- Giải thích vì sao at-least-once tạo duplicate.
- Phân biệt idempotent operation với deduplication.
- Thiết kế inbox/processed_messages với unique constraint.
- Đặt thứ tự database transaction và consumer ack đúng.
- Mô tả database-broker dual-write problem.
- Thiết kế transactional outbox.
- Tạo outbox worker dùng publisher confirms.
- Xử lý crash sau publish nhưng trước mark published.
- Kết hợp outbox ở producer với inbox ở consumer.
- Thiết kế retention, cleanup và monitoring.
- Hiểu choreography/Saga ở mức khái niệm.

## 2. Duplicate đến từ đâu?

Flow phổ biến:

~~~text
consumer nhận message
  -> ghi database hoặc gọi side effect
  -> process crash trước ack
  -> broker redeliver
  -> side effect chạy lần hai
~~~

Ví dụ cộng điểm:

~~~text
OrderPaid -> cộng 100 điểm loyalty
~~~

Nếu worker cộng điểm thành công rồi crash trước ack, lần giao lại có thể cộng thêm 100 điểm.

Nguồn duplicate:

- consumer crash trước ack;
- nack/requeue;
- connection reset;
- publisher retry sau outcome không chắc chắn;
- replay DLQ;
- outbox worker crash sau broker confirm;
- cùng event được phát lại;
- user gửi lại command với cùng idempotency key.

Broker không biết side effect bên ngoài đã thành công. Nó chỉ biết delivery đã được ack hay chưa.

## 3. Idempotency

Operation idempotent có thể chạy nhiều lần với cùng input nhưng kết quả cuối cùng tương đương một lần.

Idempotent:

~~~text
SET order.status = PAID
~~~

Không idempotent:

~~~text
balance = balance + 100
~~~

redelivered chỉ là tín hiệu delivery lại, không phải idempotency key. Dùng eventId, requestId hoặc business idempotency key ổn định.

### Effectively-once

~~~text
transport at-least-once
+ duplicate detection/idempotent business operation
= effectively-once business result
~~~

Đây là business-layer property, không phải exactly-once transport.

## 4. Chiến lược idempotency

### Natural idempotency

Thiết kế operation theo trạng thái đích:

~~~sql
UPDATE orders
SET status = 'PAID'
WHERE order_id = ?
  AND status = 'AUTHORIZED';
~~~

Lần đầu cập nhật row; lần sau không match hoặc nhận biết đã PAID.

### Unique event ID

~~~sql
CREATE TABLE processed_messages (
    consumer_name VARCHAR(100) NOT NULL,
    event_id VARCHAR(200) NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (consumer_name, event_id)
);
~~~

consumer_name cần nằm trong key vì hai consumer khác nhau có thể xử lý cùng event độc lập.

### Idempotency key

~~~sql
CREATE TABLE idempotency_records (
    consumer_name VARCHAR(100) NOT NULL,
    idempotency_key VARCHAR(200) NOT NULL,
    result_json TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (consumer_name, idempotency_key)
);
~~~

Nếu request đến lại, trả result cũ hoặc bỏ qua theo policy.

### Không dùng SELECT rồi INSERT

Không an toàn:

~~~text
SELECT processed WHERE event_id = ?
nếu chưa có:
    INSERT
~~~

Hai consumer concurrent có thể cùng thấy chưa có. Dùng INSERT có unique constraint và xử lý duplicate key.

## 5. Consumer ack order

Flow khuyến nghị:

~~~text
1. nhận message
2. mở database transaction
3. insert inbox record với unique constraint
4. cập nhật business data
5. commit transaction
6. basicAck
~~~

Nếu insert inbox bị duplicate:

~~~text
event đã xử lý
  -> không chạy side effect lần nữa
  -> ack message
~~~

Nếu transaction rollback, inbox và business update cùng rollback; message chưa ack có thể thử lại.

### Failure window

| Crash point | Kết quả | Cách xử lý |
|---|---|---|
| Trước DB transaction | Message redeliver | Xử lý lại |
| Sau inbox insert, trước business update | Transaction rollback | Xử lý lại |
| Sau DB commit, trước ack | Redeliver | Unique eventId bỏ qua |
| Sau ack | Hoàn tất | Không redeliver delivery đó |
| Side effect ngoài DB xong, DB chưa ghi inbox | Có thể duplicate | Provider idempotency |

## 6. Inbox pattern

Inbox là record ở consumer để ghi nhận message đã xử lý:

~~~text
RabbitMQ input
  -> inbox/processed_messages
  -> business transaction
  -> commit
  -> ack
~~~

Schema:

~~~sql
CREATE TABLE inbox_messages (
    consumer_name VARCHAR(100) NOT NULL,
    event_id VARCHAR(200) NOT NULL,
    event_type VARCHAR(200) NOT NULL,
    status VARCHAR(30) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    first_seen_at TIMESTAMP WITH TIME ZONE NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE,
    last_error TEXT,
    PRIMARY KEY (consumer_name, event_id)
);
~~~

Transaction:

~~~java
@Transactional
public void handle(OrderCreatedMessage message) {
    boolean inserted = inboxRepository.insertIfAbsent(
            "email-service",
            message.eventId()
    );

    if (!inserted) {
        return;
    }

    emailLogRepository.insert(
            message.orderId(),
            message.eventId()
    );
}
~~~

Listener ack sau khi transaction return thành công.

Inbox không làm email/payment atomic. Side effect ngoài database cần provider idempotency key, outbox riêng hoặc reconciliation.

## 7. Database-broker dual-write

Code nguy hiểm:

~~~java
orderRepository.save(order);
rabbitTemplate.convertAndSend(
        "orders.events",
        event
);
~~~

Có hai hệ thống cần ghi:

~~~text
Database business data
Broker event
~~~

Không có transaction tự động bao trùm cả hai.

### Failure matrix

| Thứ tự | Failure | Hậu quả |
|---|---|---|
| DB trước, broker sau | Crash trước publish | DB có, event mất |
| Broker trước, DB sau | DB rollback | Event giả |
| DB commit, publish timeout | Outcome không chắc | Mất/duplicate |
| Publish success, crash trước DB commit | Event giả | Consumer thấy data chưa commit |
| Cả hai success | Bình thường | Vẫn cần idempotency |

## 8. Transactional outbox

Ghi business data và outbox record trong cùng database transaction:

~~~text
DB transaction
  ├── INSERT orders
  └── INSERT outbox_events
commit
   |
   v
Outbox publisher
   -> publish broker
   -> publisher confirm
   -> mark published
~~~

Nếu transaction rollback, order và outbox event cùng không tồn tại. Nếu commit, outbox worker có thể publish sau.

Outbox không làm publish và mark published thành một transaction với broker. Crash sau confirm trước mark published vẫn có thể duplicate publish; downstream cần idempotency.

## 9. Outbox schema

~~~sql
CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id VARCHAR(200) NOT NULL,
    event_type VARCHAR(200) NOT NULL,
    event_version INTEGER NOT NULL,
    payload JSONB NOT NULL,
    headers JSONB,
    status VARCHAR(30) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE,
    last_error TEXT
);

CREATE INDEX idx_outbox_pending
ON outbox_events (status, next_attempt_at, created_at);
~~~

Status:

~~~text
NEW
PUBLISHING
PUBLISHED
FAILED
~~~

Nên có lease:

~~~text
locked_until
locked_by
~~~

Worker crash khi giữ lease không được làm event kẹt vĩnh viễn.

### Outbox payload

~~~json
{
  "eventId": "outbox-uuid",
  "eventType": "order.created",
  "eventVersion": 1,
  "occurredAt": "2026-10-04T10:00:00Z",
  "producer": "order-service",
  "payload": {
    "orderId": "ORD-1",
    "totalMinor": 125000
  }
}
~~~

Lưu snapshot contract cần phát, không serialize entity hiện tại một cách tùy tiện.

## 10. Ghi outbox cùng business transaction

~~~java
@Transactional
public Order createOrder(CreateOrderCommand command) {
    Order order = orderRepository.save(
            Order.create(command)
    );

    OrderCreatedEvent event = OrderCreatedEvent.from(order);

    outboxRepository.insert(
            OutboxEvent.newEvent(
                    event.eventId(),
                    "order.created",
                    1,
                    event
            )
    );

    return order;
}
~~~

Nếu order insert hoặc outbox insert fail, transaction rollback theo policy. Không publish broker trực tiếp trong transaction rồi nghĩ broker được bảo vệ.

## 11. Outbox publisher worker

Flow:

~~~text
1. claim NEW/FAILED đến hạn
2. tạo lease hoặc chuyển PUBLISHING
3. publish exchange/routing key
4. chờ publisher confirm
5. confirm -> mark PUBLISHED
6. fail -> tăng attempts, đặt next_attempt_at
7. max attempts -> FAILED + alert
~~~

Claim concurrency cần một trong:

- SELECT FOR UPDATE SKIP LOCKED;
- status transition có điều kiện;
- lease locked_until;
- optimistic version;
- partition theo aggregate.

Ví dụ:

~~~sql
SELECT id
FROM outbox_events
WHERE status IN ('NEW', 'FAILED')
  AND COALESCE(next_attempt_at, CURRENT_TIMESTAMP)
      <= CURRENT_TIMESTAMP
ORDER BY created_at
FOR UPDATE SKIP LOCKED
LIMIT 100;
~~~

Claim transaction nên ngắn. Không giữ database lock trong lúc chờ network broker.

Publish:

~~~java
for (OutboxEvent event : claimedEvents) {
    try {
        publisher.publishWithConfirm(event);
        outboxRepository.markPublished(
                event.id(),
                Instant.now()
        );
    } catch (Exception exception) {
        outboxRepository.markFailed(
                event.id(),
                nextAttemptAt(event.attempts()),
                sanitize(exception)
        );
    }
}
~~~

Không mark PUBLISHED trước confirm:

~~~text
mark PUBLISHED
  -> publish
  -> crash
  -> event mất nhưng database nói đã published
~~~

Đúng:

~~~text
publish -> confirm -> mark PUBLISHED
~~~

## 12. Outbox retry

~~~text
attempt 1: ngay hoặc 5s
attempt 2: 30s
attempt 3: 5m
max: FAILED + alert
~~~

Theo dõi:

~~~text
attempts
next_attempt_at
last_attempt_at
last_error
~~~

Serialization/contract failure thường không tự hết, không retry vô hạn. Network/broker failure có thể retry với backoff.

## 13. Inbox và outbox kết hợp

Một service consume event rồi phát event tiếp:

~~~text
RabbitMQ input
  -> transaction
       ├── insert inbox
       ├── update business data
       └── insert output outbox
  -> commit
  -> ack input
  -> outbox worker publish output
~~~

Kết quả:

- input không tạo business result hai lần;
- output event không mất sau DB commit;
- output publish có thể retry;
- downstream xử lý idempotent.

## 14. Effectively-once và side effect ngoài DB

~~~text
at-least-once delivery
+ unique event ID
+ inbox/idempotency
+ transactional outbox
+ provider idempotency key
= effectively-once business behavior
~~~

Với email/payment/HTTP provider:

- dùng eventId/requestId làm idempotency key nếu provider hỗ trợ;
- lưu send attempt và reconciliation;
- dùng outbox riêng cho side effect;
- không nghĩ inbox record đã làm provider call atomic.

## 15. Saga và choreography

Saga là chuỗi local transaction và compensation:

~~~text
OrderCreated
  -> inventory reserved
  -> PaymentRequested
  -> PaymentCaptured
  -> OrderPaid
  -> ShippingRequested
~~~

Khi payment fail:

~~~text
PaymentFailed
  -> release inventory
  -> OrderPaymentFailed
~~~

Mỗi service sở hữu local database, consume event, commit local change, phát event tiếp qua outbox và có compensation khi cần.

Saga không phải rollback thật. Cần correlationId, causationId, state machine, idempotency và observability.

## 16. Lab 1 — Outbox transaction

1. Tạo bảng orders và outbox_events.
2. Trong một transaction insert order và outbox.
3. Commit và kiểm tra cả hai row.
4. Ném exception trước commit.
5. Kiểm tra cả hai row không tồn tại.
6. Ném exception sau order insert nhưng trước outbox insert.
7. Kiểm tra rollback.
8. Xác nhận chưa có broker event trực tiếp.

| Kịch bản | Order row | Outbox row | Broker event |
|---|---:|---:|---:|
| Commit | Có | Có | Chưa publish |
| Rollback | Không | Không | Không |
| Worker sau commit | Có | PUBLISHED | Có |

## 17. Lab 2 — Outbox publisher và confirm

1. Tạo 100 outbox event NEW.
2. Worker claim theo batch.
3. Publish với publisher confirm.
4. Mark PUBLISHED sau confirm.
5. Tắt broker giữa batch.
6. Ghi event published, failed và pending.
7. Khởi động broker.
8. Retry event chưa published.
9. Kiểm tra duplicate publish ở consumer.
10. Thêm idempotency consumer.

Theo dõi:

~~~text
outbox_new
outbox_publishing
outbox_published
outbox_failed
outbox_attempts
publish_confirm_latency
~~~

## 18. Lab 3 — Inbox và crash sau DB commit

1. Tạo processed_messages với primary key consumer_name + event_id.
2. Consumer nhận OrderCreated.
3. Transaction insert processed_messages và update business table.
4. Commit.
5. Crash trước ack.
6. Broker redeliver.
7. Lần insert thứ hai gặp duplicate key.
8. Bỏ qua business update lần hai.
9. Ack duplicate delivery.

Kết quả mong đợi:

~~~text
deliveries       = 2 hoặc hơn
business effect  = 1
processed row    = 1
final ack        = có
~~~

## 19. Lab 4 — Rollback không phát event giả

1. Bắt đầu transaction create order.
2. Insert order.
3. Insert outbox event.
4. Ném exception.
5. Kiểm tra order không tồn tại.
6. Kiểm tra outbox không tồn tại.
7. Kiểm tra RabbitMQ không có event.
8. Chạy lại thành công.
9. Xác nhận đúng một outbox event được publish.

## 20. Lab 5 — Cleanup và retention

Outbox PUBLISHED không nên phình vô hạn:

~~~text
published_at < retention cutoff
  -> archive/delete theo policy
~~~

Không xóa event NEW, PUBLISHING còn lease hợp lệ hoặc FAILED chưa điều tra.

Ghi:

- retention bao nhiêu ngày;
- archive giữ field nào;
- cleanup có batch limit không;
- cleanup có ảnh hưởng worker không;
- inbox giữ bao lâu để chống replay/duplicate.

## 21. Monitoring

### Outbox

- NEW count;
- PUBLISHING quá lease;
- PUBLISHED count;
- FAILED count;
- oldest unpublished age;
- attempts distribution;
- confirm latency;
- return count;
- worker throughput.

### Inbox

- processed count;
- duplicate detected count;
- processing failure;
- stuck PROCESSING;
- event version rejected;
- DLQ count.

Alert khi oldest unpublished vượt SLA, FAILED tăng, PUBLISHING lease hết hạn hoặc duplicate rate tăng.

## 22. Lỗi thiết kế thường gặp

- Save DB rồi publish trực tiếp không có outbox.
- Publish trước DB commit.
- Ack trước DB commit.
- SELECT rồi INSERT không có unique constraint.
- Ghi inbox sau side effect ngoài DB.
- Mark published trước confirm.
- Giữ DB lock khi chờ broker.
- Không có lease cho worker crash.
- Retry serialization error vô hạn.
- Xóa outbox chưa published.
- Replay không giữ eventId/idempotency.
- Nghĩ inbox làm email/payment atomic.
- Không có consumer_name trong processed_messages.
- Không có retention.
- Coi effectively-once là exactly-once.

## 23. Checklist

- [ ] Consumer at-least-once.
- [ ] Side effect idempotent hoặc có idempotency key.
- [ ] Unique constraint eventId/consumer.
- [ ] Ack sau DB transaction commit.
- [ ] Có inbox cho consumer quan trọng.
- [ ] Business data và outbox insert cùng transaction.
- [ ] Outbox worker publish sau commit.
- [ ] Mark published sau confirm.
- [ ] Worker claim có lease/locking.
- [ ] Outbox retry có giới hạn.
- [ ] Contract failure không retry vô hạn.
- [ ] Downstream idempotent.
- [ ] Có retention outbox/inbox.
- [ ] Có metric oldest unpublished và duplicate count.
- [ ] Replay có audit.
- [ ] Có test crash ở failure windows.

## 24. Deliverable cuối tuần

Tạo:

~~~text
rabbitmq/week-10/
├── README.md
├── schema/
│   ├── outbox.sql
│   ├── inbox.sql
│   └── indexes.sql
├── src/
│   ├── OrderService.java
│   ├── OutboxPublisher.java
│   ├── InboxConsumer.java
│   └── ReplayCommand.java
└── tests/
    ├── OutboxTransactionTest.java
    ├── OutboxConfirmFailureTest.java
    ├── InboxDuplicateTest.java
    └── RollbackNoEventTest.java
~~~

README cần có:

1. Failure matrix database/broker.
2. Outbox schema và status lifecycle.
3. Inbox schema với unique constraint.
4. Transaction boundary business data + outbox.
5. Worker claim/lease/publish/confirm/mark flow.
6. Crash sau DB commit trước ack.
7. Crash sau publish confirm trước mark published.
8. Retry và retention policy.
9. Metric/alert.
10. Bằng chứng effectively-once ở business layer.
11. Giới hạn side effect ngoài DB.

## 25. Tiêu chí tự kiểm tra

1. Vì sao at-least-once tạo duplicate?
2. Idempotent operation khác deduplication thế nào?
3. Vì sao SELECT rồi INSERT không đủ an toàn?
4. Unique constraint eventId giải quyết gì?
5. Ack đặt trước hay sau DB commit?
6. Inbox pattern lưu gì?
7. Dual-write problem có failure window nào?
8. Transactional outbox giải quyết mất event ra sao?
9. Vì sao outbox worker vẫn duplicate publish?
10. Mark PUBLISHED đặt trước hay sau confirm?
11. Vì sao cần lease/locking?
12. Outbox và inbox kết hợp thế nào?
13. Vì sao inbox không làm email/payment atomic?
14. Khi nào cần provider idempotency key?
15. Cleanup cần cẩn thận gì?
16. Effectively-once khác exactly-once thế nào?
17. Saga choreography giải quyết consistency nào?
18. Metric nào cho biết outbox bị kẹt?

### Đáp án cần nhớ

> Ghi business data và publish broker là hai hệ thống; save rồi publish trực tiếp có dual-write risk. Transactional outbox ghi business row và event trong cùng DB transaction; worker publish sau commit và mark published sau confirm. Crash sau confirm vẫn có thể duplicate publish, nên downstream cần inbox/unique eventId/idempotency. Kết quả là effectively-once ở business layer, không phải exactly-once transport.

## 26. Chuẩn bị cho Tuần 11

Tuần sau sẽ đo và tối ưu performance:

- throughput, latency, concurrency và saturation;
- prefetch, consumer, channel và connection;
- batch publish và async confirms;
- message size, serialization và network cost;
- queue growth và backpressure;
- JVM heap, GC, thread pool và connection pool;
- benchmark p50/p95/p99.

Giữ lại outbox worker, inbox consumer và failure tests. Chúng là workload thực tế để benchmark mà không hy sinh correctness.

# Tuần 14: Outbox, Inbox và distributed consistency

## 1. Mục tiêu

Sau bài học này, bạn có thể:

- Giải thích dual-write problem giữa database và message broker.
- Thiết kế Transactional Outbox để business state và event record commit cùng transaction.
- Hiểu polling publisher và CDC ở mức kiến trúc.
- Thiết kế Inbox, idempotency key và deduplication table.
- Phân tích crash trước commit, sau commit, sau publish và trước acknowledge.
- Chọn transaction boundary cho producer và consumer.
- Hiểu Saga choreography, Saga orchestration và compensating action.
- Thiết kế retry, lease, cleanup và observability cho Outbox/Inbox.
- Viết flow Spring Boot/JPA có transaction rõ ràng.
- Hoàn thành lab chứng minh event không mất và duplicate không tạo duplicate side effect.

Nguyên tắc trung tâm:

> Business state và ý định phát event phải cùng nằm trong một local transaction. Việc publish broker là một bước có thể retry, không phải phần tự động của database transaction.

---

## 2. Distributed consistency là gì

Một local database transaction có thể atomic update nhiều bảng:

~~~text
BEGIN
  update orders
  insert order_items
  COMMIT
~~~

Nhưng một request event-driven thường cần:

~~~text
update database
publish message to broker
call downstream service
send email
~~~

Database và broker thường là hai resource độc lập. Không có commit duy nhất bao phủ cả hai nếu hệ thống không dùng distributed transaction.

Mục tiêu thực tế:

- Database commit thì event phải eventually được publish.
- Database rollback thì event không được publish.
- Broker lỗi tạm thời thì publisher retry.
- Publisher crash sau publish thì duplicate có thể xảy ra.
- Consumer phải idempotent.
- Không giữ database transaction trong lúc gọi broker hoặc HTTP downstream.

### Local atomicity và eventual consistency

Outbox tạo atomicity cục bộ:

~~~text
business state + outbox record
~~~

Sau commit có thể có khoảng trễ:

~~~text
database đã commit
outbox chưa publish
~~~

Downstream chưa biết thay đổi trong khoảng trễ này. Khi relay publish thành công, hệ thống eventually consistent.

---

## 3. Dual-write problem

Dual-write là một use case ghi vào hai hệ thống:

~~~text
write database
write message broker
~~~

### Database trước broker

~~~text
1. commit database
2. publish broker
~~~

Crash giữa hai bước làm business state tồn tại nhưng event biến mất.

### Broker trước database

~~~text
1. publish broker
2. commit database
~~~

Database rollback làm broker có event ma; consumer có thể xử lý entity chưa tồn tại.

### Publish bên trong database transaction

~~~text
BEGIN database transaction
  update database
  publish broker
COMMIT database transaction
~~~

Vẫn có failure window:

- Broker publish thành công nhưng database commit fail.
- Broker chậm làm database lock bị giữ lâu.
- Retry network kéo dài transaction.
- Remote call không tự tham gia transaction cục bộ.

Two-phase commit thường tăng coupling và operational complexity. Outbox là cách thực dụng để đạt eventual publication mà không cần một distributed transaction lớn.

---

## 4. Transactional Outbox

Transactional Outbox ghi business data và event cần phát vào cùng database transaction. Relay riêng đọc Outbox và publish tới broker.

~~~text
Application transaction:
  update business tables
  insert outbox event
  commit

Message relay:
  read unprocessed outbox event
  publish to broker
  mark published or retry
~~~

Outbox đảm bảo:

- Business state commit thì Outbox commit.
- Business state rollback thì Outbox rollback.
- Service crash sau commit vẫn còn event record.
- Relay có thể retry.

Outbox không tự đảm bảo:

- Broker nhận đúng một lần.
- Consumer xử lý đúng một lần.
- External side effect không duplicate.
- Event publish tức thời.
- Strict ordering trong mọi trường hợp.
- Schema và semantics luôn đúng.

Mô hình thường là:

~~~text
at-least-once publication
at-least-once delivery
idempotent business processing
~~~

Tài liệu Transactional Outbox cũng cảnh báo relay có thể publish nhiều lần nếu crash sau publish nhưng trước khi ghi nhận kết quả. [Transactional Outbox pattern](https://microservices.io/patterns/data/transactional-outbox)

---

## 5. Flow Create Order

### Local transaction

~~~text
validate command
check API idempotency key
create order
create order items
create event id
serialize immutable event snapshot
insert outbox record
commit
~~~

Sau commit, API có thể trả:

~~~text
201 Created
orderId = order-123
event publication = pending
~~~

Không trả “published successfully” chỉ vì Outbox insert thành công.

### Relay

~~~text
claim pending row
publish to broker
wait for broker acknowledgement
mark published
~~~

### Consumer

~~~text
receive message
deserialize
validate
insert inbox identity
apply side effect
insert next outbox if needed
commit
ack message
~~~

### Retry

Nếu broker publish fail:

- Giữ Outbox record.
- Tăng attempt count.
- Phân loại retryable/permanent.
- Đặt available_at cho lần retry kế tiếp.
- Quarantine nếu không thể xử lý tự động.

---

## 6. Bảng Outbox

Ví dụ PostgreSQL:

~~~sql
create table outbox_events (
    id uuid primary key,
    aggregate_type varchar(100) not null,
    aggregate_id varchar(200) not null,
    aggregate_sequence bigint,
    event_type varchar(200) not null,
    event_version integer not null,
    event_key varchar(200),
    schema_subject varchar(300),
    schema_id integer,
    payload jsonb not null,
    headers jsonb,
    status varchar(30) not null,
    attempt_count integer not null default 0,
    available_at timestamp with time zone not null,
    locked_at timestamp with time zone,
    locked_by varchar(200),
    published_at timestamp with time zone,
    last_error_code varchar(100),
    last_error_message varchar(1000),
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);
~~~

### Ý nghĩa field

- id: event identity ổn định qua retry.
- aggregate_type và aggregate_id: entity phát sinh event.
- aggregate_sequence: thứ tự theo aggregate nếu cần.
- event_type và event_version: contract.
- event_key: partition hoặc routing key.
- payload: snapshot của event.
- headers: correlationId, causationId, traceId.
- status, attempts, available_at: retry state.
- locked_at, locked_by: lease.
- published_at: audit.
- last_error: chẩn đoán.

Status gợi ý:

~~~text
PENDING
IN_FLIGHT
PUBLISHED
FAILED_RETRYABLE
QUARANTINED
~~~

Index:

~~~sql
create index idx_outbox_pending
on outbox_events (status, available_at, created_at);

create index idx_outbox_aggregate
on outbox_events (aggregate_type, aggregate_id, created_at);

create index idx_outbox_cleanup
on outbox_events (published_at)
where status = 'PUBLISHED';
~~~

Đừng thêm index theo cảm tính; mỗi index tăng chi phí ghi trong business transaction.

---

## 7. Snapshot hay aggregate reference

### Snapshot

Lưu payload đầy đủ tại thời điểm business transaction commit.

Ưu điểm:

- Event phản ánh đúng fact.
- Relay không cần đọc lại aggregate.
- Aggregate đổi sau đó không làm event cũ đổi.

Nhược điểm:

- Payload lớn hơn.
- Serialization nằm trong transaction.
- Phải review PII trong snapshot.

### Reference

Lưu aggregateId và eventType, relay đọc aggregate hiện tại rồi build payload.

Phù hợp hơn với notification “hãy refresh state”, nhưng nguy hiểm với domain fact:

- Aggregate có thể đã đổi.
- OrderCreated có thể chứa state sau khi update.
- Lịch sử bị sai semantics.

Quy tắc:

- Domain fact nên dùng snapshot.
- Notification refresh có thể dùng reference.
- Không gọi state hiện tại là lịch sử quá khứ.

---

## 8. Ordering của Outbox

Giả sử cùng order có:

~~~text
T1: OrderCreated
T2: OrderAddressChanged
~~~

Mong muốn:

~~~text
OrderCreated -> OrderAddressChanged
~~~

Ordering có thể bị phá bởi:

- Nhiều relay.
- T1 publish chậm, T2 publish nhanh.
- T1 retry sau khi T2 đã publish.
- Nhiều producer connection.
- Consumer xử lý song song.
- Database transaction concurrency.

Biện pháp:

- Dùng aggregateId làm message key.
- Cùng aggregate đi cùng partition.
- Ghi aggregate_sequence trong transaction.
- Consumer kiểm tra gap nếu strict ordering bắt buộc.
- Thiết kế consumer không phụ thuộc ordering nếu có thể.

Ví dụ:

~~~text
last_processed(order-123) = 1
incoming sequence = 3
=> chờ hoặc retry vì sequence 2 chưa tới
~~~

Ordering toàn cục thường đắt; ordering theo aggregate thường đủ.

---

## 9. Polling publisher

Polling publisher query Outbox theo chu kỳ:

~~~text
while running:
  claim available rows
  publish each row
  mark success or retry
  wait
~~~

Ưu điểm:

- Dùng được với hầu hết SQL database.
- Dễ debug.
- Không cần CDC platform.
- Retry nằm trong application code.

Nhược điểm:

- Tạo query load.
- Cần locking và lease.
- Poll interval tạo latency.
- Publish thành công nhưng status update fail vẫn có duplicate.

Claim an toàn:

~~~sql
select id
from outbox_events
where status in ('PENDING', 'FAILED_RETRYABLE')
  and available_at <= now()
order by created_at
for update skip locked
limit 100;
~~~

Sau claim, cập nhật:

~~~text
status = IN_FLIGHT
locked_by = worker-1
locked_at = now()
available_at = now() + lease
commit
~~~

Không giữ row lock trong lúc gọi broker.

Recover lease hết hạn:

~~~sql
update outbox_events
set status = 'FAILED_RETRYABLE',
    locked_by = null,
    locked_at = null,
    available_at = now()
where status = 'IN_FLIGHT'
  and locked_at < now() - interval '5 minutes';
~~~

Lease quá ngắn có thể làm duplicate tăng; lease quá dài làm recovery chậm.

---

## 10. Failure matrix của publisher

| Điểm crash | Database | Broker | Kết quả |
|---|---|---|---|
| Trước Outbox insert | Chưa commit | Chưa có | Không có event |
| Sau business update nhưng rollback | Rollback | Chưa có | Không có event |
| Sau commit | Có business + Outbox | Chưa có | Relay publish sau |
| Sau claim | IN_FLIGHT | Chưa có | Lease expiry để claim lại |
| Sau publish trước mark | IN_FLIGHT | Đã có | Retry có thể duplicate |
| Sau mark published | PUBLISHED | Đã có | Thành công |
| Timeout không rõ kết quả | IN_FLIGHT | Có thể đã có | Retry có thể duplicate |

Đây là lý do consumer phải idempotent. Không nên cố “đoán” rằng timeout đồng nghĩa broker chưa nhận.

---

## 11. CDC ở mức khái niệm

CDC đọc transaction log của database:

~~~text
application transaction:
  update business tables
  insert outbox row
  commit

database transaction log:
  committed outbox insert

CDC connector:
  read log
  route outbox row to broker
~~~

Debezium Outbox Event Router là một ví dụ về connector capture thay đổi ở Outbox table và route payload tới message. Tài liệu của Debezium mô tả các lựa chọn payload JSON/Avro và routing metadata. [Debezium Outbox Event Router](https://debezium.io/documentation/reference/transformations/outbox-event-router.html)

Polling phù hợp khi:

- Hệ thống vừa.
- Muốn code đơn giản.
- Latency vài giây chấp nhận được.
- Team chưa vận hành CDC.

CDC phù hợp khi:

- Throughput lớn.
- Có Kafka Connect hoặc nền tảng CDC.
- Muốn giảm polling query.
- Team vận hành tốt connector và offset.

CDC không loại bỏ duplicate hoặc nhu cầu Inbox. Connector restart và offset recovery vẫn cần idempotent consumer.

---

## 12. Inbox Pattern

Inbox là bảng hoặc store ghi nhận message đã nhận/xử lý.

Flow:

~~~text
BEGIN consumer transaction
  insert inbox(message_id)
  if duplicate:
    no-op
  else:
    apply side effect
    mark processed
COMMIT
ack message
~~~

Failure behavior:

- Crash trước commit: Inbox và side effect không commit; message redeliver.
- Crash sau commit trước ack: message redeliver; Inbox unique làm no-op.
- Ack trước commit: side effect có thể mất.

Bảng:

~~~sql
create table inbox_messages (
    consumer_name varchar(200) not null,
    message_id varchar(200) not null,
    event_type varchar(200) not null,
    aggregate_id varchar(200),
    payload_hash varchar(128),
    status varchar(30) not null,
    received_at timestamp with time zone not null,
    processed_at timestamp with time zone,
    attempt_count integer not null default 0,
    last_error_code varchar(100),
    last_error_message varchar(1000),
    primary key (consumer_name, message_id)
);
~~~

Key gồm consumer_name và message_id vì nhiều consumer độc lập có thể xử lý cùng một event.

Không dùng kiểu kiểm tra rồi mới insert:

~~~text
if not exists:
  insert
  apply side effect
~~~

Hai thread có thể cùng vượt qua điều kiện. Dùng unique constraint và insert-if-absent:

~~~sql
insert into inbox_messages(
    consumer_name, message_id, status, received_at
)
values (?, ?, 'RECEIVED', now())
on conflict (consumer_name, message_id) do nothing;
~~~

---

## 13. Event ID và idempotency key

Event ID đại diện cho event instance:

~~~text
eventId = evt-123
~~~

Retry cùng Outbox record giữ nguyên eventId.

Idempotency key đại diện cho business intent:

~~~text
POST /orders
Idempotency-Key: create-order-customer-9-001
~~~

Event có thể chứa:

~~~text
eventId = evt-123
causationId = request-456
idempotencyKey = create-order-customer-9-001
~~~

Khác biệt:

- EventId chống xử lý lặp cùng event.
- Idempotency key chống lặp cùng business intent.
- Compensation thường có business intent mới.
- Payment provider cần key riêng cho capture.
- Hai command khác nhau không nên dùng chung key chỉ vì cùng aggregate.

API idempotency cũng cần database unique constraint. Không chỉ lưu key trong memory hoặc cache ngắn hạn.

---

## 14. Transaction boundary của producer

Transaction nên gồm:

~~~text
validate command
load aggregate
apply state change
persist aggregate
create immutable event snapshot
insert outbox record
commit
~~~

Không nên gồm:

~~~text
publish broker
call payment service
send email
call remote HTTP
~~~

Remote call có latency và failure không kiểm soát. Gọi nó trong DB transaction làm tăng lock, timeout và connection pressure.

Ví dụ Spring:

~~~java
@Service
public class OrderApplicationService {

    @Transactional
    public OrderId createOrder(CreateOrderCommand command) {
        Order order = orderFactory.create(command);
        orderRepository.save(order);

        OrderCreated event = OrderCreated.from(order);
        outboxRepository.save(outboxMapper.toRecord(event));

        return order.id();
    }
}
~~~

Spring transaction là runtime infrastructure dựa trên metadata; annotation transaction không tự biến remote broker call thành một phần của database transaction. [Spring transaction implementation](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-decl-explained.html)

Spring cũng lưu ý declarative transaction thường dùng proxy; self-invocation có thể không đi qua proxy và không kích hoạt behavior như mong đợi. [Spring annotation transactions](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html)

---

## 15. Transaction boundary của consumer

Flow an toàn:

~~~text
receive message
  BEGIN local DB transaction
    insert inbox
    if new:
      apply business update
      insert next outbox if needed
  COMMIT
  acknowledge message
~~~

Inbox và side effect cần cùng transaction nếu cùng database.

External HTTP/email/payment không nằm trong local transaction:

~~~text
insert inbox
call external API
update local DB
commit
~~~

Nếu API thành công rồi local commit fail, message retry có thể gọi external API lần nữa. Cần:

- Idempotency key tới provider.
- Payment attempt state.
- Reconciliation.
- Outbox cho remote action.
- Compensation.

Inbox không tự tạo exactly-once cho external side effect.

---

## 16. Outbox và Inbox kết hợp

Một consumer cũng có thể là producer:

~~~text
consume OrderCreated
  -> insert inbox
  -> update inventory
  -> insert InventoryReserved outbox
  -> commit
  -> ack OrderCreated
~~~

Cùng transaction:

~~~text
inbox insert
business update
outbox insert
commit
~~~

Nếu process crash sau commit:

- Event đầu vào redeliver nhưng Inbox skip.
- Business state đã commit.
- Event đầu ra vẫn nằm trong Outbox.
- Publisher sẽ gửi event tiếp theo.

Đây là nền tảng cho local consistency trong Saga choreography.

---

## 17. Saga choreography

Trong choreography, service nghe event và tự quyết định bước kế tiếp:

~~~text
OrderCreated
  -> Inventory service reserve

InventoryReserved
  -> Payment service capture

PaymentCaptured
  -> Order service confirm

PaymentFailed
  -> Inventory service release
  -> Order service fail
~~~

Ưu điểm:

- Không có central coordinator.
- Tự nhiên với pub/sub.
- Service tương đối độc lập.
- Dễ thêm consumer.

Nhược điểm:

- Flow phân tán.
- Khó nhìn toàn bộ state machine.
- Timeout và compensation rải rác.
- Event chain dài khó debug.
- Có thể tạo circular dependency.

Phù hợp khi flow ít bước, event contracts tốt và tracing đủ mạnh.

---

## 18. Saga orchestration

Coordinator giữ state và gửi command:

~~~text
Coordinator
  -> ReserveInventory
  <- InventoryReserved

Coordinator
  -> CapturePayment
  <- PaymentCaptured

Coordinator
  -> ConfirmOrder
~~~

Nếu payment fail:

~~~text
Coordinator
  -> ReleaseInventory
  -> CancelOrder
~~~

Ưu điểm:

- State machine tập trung.
- Timeout, retry và compensation rõ.
- Dễ theo dõi workflow nhiều bước.

Nhược điểm:

- Coordinator có thể thành god service.
- Cần durable saga state.
- Coordinator cũng cần Inbox/Outbox.
- Coupling với participant tăng.

Orchestrator không biến workflow thành distributed transaction; nó chỉ làm điều phối rõ hơn.

---

## 19. Compensating action

| Action thành công | Failure sau đó | Compensation |
|---|---|---|
| Reserve inventory | Payment failed | Release inventory |
| Capture payment | Order invalid | Refund |
| Create shipment | Cancel order | Cancel shipment nếu khả thi |
| Grant coupon | Order rollback | Revoke coupon |

Compensation cần:

- Idempotency key.
- State transition hợp lệ.
- Retry và timeout.
- Audit trail.
- Manual intervention.
- Business owner.

Compensation không phải inverse tuyệt đối. Refund có thể pending, shipment đã rời kho có thể không cancel, email đã gửi không thể thu hồi.

UNKNOWN outcome cần query/reconciliation trước khi quyết định action tiếp theo.

---

## 20. Exactly-once nên hiểu thế nào

Exactly-once có thể nói về:

- Log write.
- Producer send.
- Transaction processing.
- Business side effect.
- Toàn workflow database, broker và HTTP.

Các lớp này không tương đương.

Outbox + Inbox thường cho:

~~~text
at-least-once publication
at-least-once delivery
idempotent business processing
~~~

Một side effect effectively-once nếu:

- Identity ổn định.
- Unique constraint hoạt động.
- Inbox và side effect cùng transaction.
- External API hỗ trợ idempotency hoặc reconciliation.
- Retry không tạo intent mới.
- Transaction boundary không bị thủng.

Không ghi “exactly-once guaranteed” nếu chưa nêu resource, failure model và giới hạn.

---

## 21. Cleanup và retention

### Outbox

Chỉ cleanup khi:

- Đã published.
- Đủ thời gian audit/replay.
- Không còn workflow phụ thuộc.
- Archive hoặc backup policy cho phép.

Ví dụ:

~~~text
PUBLISHED > 7 days -> archive/delete
FAILED_RETRYABLE -> giữ
QUARANTINED -> giữ đến khi triage
IN_FLIGHT expired -> retry
~~~

### Inbox

Giữ phù hợp replay window. Nếu broker retention là 14 ngày và có thể replay 14 ngày, Inbox identity không nên bị xóa sau 1 ngày.

Archive có thể giữ:

- message id.
- event type.
- aggregate id.
- processing result.
- processed time.
- payload hash.

Không nhất thiết lưu raw PII payload.

---

## 22. Observability

Outbox metrics:

- pending count.
- oldest age.
- publish rate.
- failure rate.
- retry count.
- quarantine count.
- expired lease count.
- publication latency.
- cleanup duration.
- CDC lag.

Inbox metrics:

- duplicate count.
- process rate.
- failure rate.
- oldest unprocessed age.
- processing latency.
- dedup hit ratio.
- quarantine count.
- external idempotency conflict count.

Saga metrics:

- active saga count.
- duration.
- timeout count.
- compensation count.
- compensation failure count.
- stuck state age.
- completed/failed ratio.

Trace fields:

~~~text
eventId
messageId
correlationId
causationId
aggregateId
sagaId
traceId
outboxId
inboxId
~~~

Không log raw payload có PII tùy tiện; log metadata và payload hash khi đủ cho điều tra.

---

## 23. Failure model đầy đủ

Producer crash trước commit:

- Business update rollback.
- Outbox rollback.
- Không có event.

Producer crash sau commit:

- Business state tồn tại.
- Outbox tồn tại.
- Publisher restart sẽ gửi.
- API retry cần idempotency key.

Publisher crash trước publish:

- Outbox pending hoặc lease hết hạn.
- Worker khác retry.
- Không mất event.

Publisher crash sau publish trước mark:

- Broker có event.
- Outbox chưa PUBLISHED.
- Retry có thể duplicate.
- Inbox skip duplicate.

Consumer crash trước Inbox commit:

- Message redeliver.
- Side effect chưa commit nếu cùng transaction.

Consumer crash sau commit trước ack:

- Inbox và side effect đã commit.
- Message redeliver.
- Inbox conflict, skip, ack.

Ack trước commit:

- Broker không redeliver.
- Side effect có thể mất.

External API timeout sau khi đã xử lý:

- Retry có thể gọi lần hai.
- Cần idempotency hoặc reconciliation.

Compensation thất bại:

- Saga chuyển COMPENSATION_FAILED.
- Alert và manual runbook.
- Không retry vô hạn không kiểm soát.

---

## 24. Spring Boot publisher

~~~java
@Component
public class OutboxPublisher {

    @Scheduled(fixedDelay = 1000)
    public void publishBatch() {
        List<OutboxEventEntity> events = claimer.claimBatch(100);

        for (OutboxEventEntity event : events) {
            try {
                brokerPublisher.publishAndAwaitConfirmation(
                    event.getEventType(),
                    event.getAggregateId(),
                    event.getPayload()
                );
                publisher.markPublished(event.getId());
            } catch (RetryableBrokerException ex) {
                publisher.releaseForRetry(event.getId(), ex);
            } catch (PermanentPublishException ex) {
                publisher.quarantine(event.getId(), ex);
            }
        }
    }
}
~~~

Production cần:

- Worker identity.
- Lease timeout.
- Batch limit.
- Backoff và jitter.
- Broker confirmation.
- Timeout.
- Graceful shutdown.
- Metrics.
- Structured logging.
- Không giữ DB transaction khi publish.
- Không mark published khi async send chưa acknowledgement.

Claim, publish và mark nên tách như sau:

~~~text
Transaction A:
  claim row
  mark IN_FLIGHT
  commit

Outside transaction:
  publish broker

Transaction B:
  mark PUBLISHED or FAILED_RETRYABLE
  commit
~~~

---

## 25. Spring Boot consumer

~~~java
@KafkaListener(topics = "order.events")
public void onMessage(OrderCreated message) {
    transactionTemplate.executeWithoutResult(status -> {
        boolean inserted = inboxRepository.insertIfAbsent(
            "inventory-service",
            message.eventId()
        );

        if (!inserted) {
            return;
        }

        inventoryService.reserve(message);
        inboxRepository.markProcessed(
            "inventory-service",
            message.eventId()
        );
    });
}
~~~

Cần kiểm tra:

- Ack mode.
- Exception có redelivery không.
- Unique constraint của Inbox.
- Cùng transaction manager.
- Duplicate callback có no-op không.
- Downstream call có giữ transaction quá lâu không.
- Lỗi vĩnh viễn đi đâu.

Nếu consumer tạo event tiếp theo, insert Outbox trong cùng local transaction.

---

## 26. Các lỗi thiết kế phổ biến

- Ghi Outbox sau commit.
- Mark published trước publish.
- Xóa trước broker confirmation.
- Không có lease cho IN_FLIGHT.
- Dùng exists rồi insert Inbox.
- Inbox khác transaction với side effect.
- Ack trước commit.
- Gọi HTTP trong DB transaction.
- Dùng eventId cho mọi business intent.
- Không có aggregate sequence khi cần ordering.
- Saga không có compensation.
- Cleanup Inbox sớm hơn replay window.
- Retry vô hạn poison message.
- Không phân biệt retryable, permanent và UNKNOWN.
- Không có metric cho Outbox oldest age.
- Không có unique constraint cho idempotency key.

---

## 27. Lab 1: Order và Outbox cùng transaction

### Success

~~~text
call createOrder
assert order exists
assert one outbox exists
assert event aggregate_id = order.id
assert status = PENDING
~~~

### Rollback trước Outbox

~~~text
save order
throw exception before outbox insert
rollback
~~~

Kỳ vọng: order và Outbox đều không tồn tại.

### Rollback sau Outbox

~~~text
save order
save outbox
throw exception
rollback
~~~

Kỳ vọng: order và Outbox đều không tồn tại.

Deliverable:

- Integration test.
- SQL schema.
- Transaction boundary explanation.
- Kiểm tra broker không được gọi trong local transaction.

---

## 28. Lab 2: Crash sau commit trước publish

Các bước:

1. Tạo order.
2. Commit order và Outbox.
3. Dừng publisher trước publish.
4. Kiểm tra order tồn tại.
5. Kiểm tra Outbox PENDING.
6. Khởi động publisher.
7. Event được publish.
8. Outbox chuyển PUBLISHED.
9. Consumer nhận event.

Kỳ vọng:

~~~text
order exists = true
outbox exists = true
broker event before restart = 0
broker event after restart = 1
~~~

Câu hỏi:

- API timeout sau commit thì client retry thế nào?
- Idempotency key của API nằm ở đâu?
- Publisher restart có thể duplicate không?
- Consumer chứng minh duplicate an toàn bằng cách nào?

---

## 29. Lab 3: Crash sau publish trước mark

Các bước:

1. Publisher claim Outbox.
2. Publish thành công.
3. Dừng process trước mark published.
4. Khởi động publisher.
5. Record được publish lại.
6. Consumer nhận hai delivery.
7. Inbox chỉ cho side effect chạy một lần.

Kỳ vọng:

~~~text
inbox rows = 1
business side effect = 1
outbox final status = PUBLISHED
~~~

Kết luận: duplicate transport không nhất thiết tạo duplicate business result.

---

## 30. Lab 4: Inbox chống duplicate

Gửi cùng eventId ba lần:

~~~text
evt-100
evt-100
evt-100
~~~

Consumer:

- Insert Inbox unique.
- Update inventory.
- Insert InventoryReserved Outbox.
- Commit.
- Ack.

Kỳ vọng:

~~~text
inbox rows = 1
inventory reservation = 1
outgoing events = 1
~~~

Chạy hai consumer thread song song với cùng eventId. Một transaction phải thắng unique constraint; transaction còn lại no-op hoặc retry rồi no-op.

---

## 31. Lab 5: Saga compensation

Flow thành công:

~~~text
OrderCreated
  -> InventoryReserved
  -> PaymentCaptured
  -> OrderConfirmed
~~~

Flow thất bại:

~~~text
OrderCreated
  -> InventoryReserved
  -> PaymentFailed
  -> ReleaseInventory
  -> OrderFailed
~~~

Thử:

- Inventory không đủ.
- Payment timeout.
- Provider trả UNKNOWN.
- Release inventory retry.
- Order service chết sau PaymentCaptured.
- Duplicate PaymentCaptured.
- PaymentFailed đến sau PaymentCaptured.

Deliverable:

- State machine.
- Event/command list.
- Outbox/Inbox table.
- Compensation rule.
- Timeout.
- Idempotency key.
- Integration test.

---

## 32. Lab 6: Polling publisher và lease

Setup:

- Hai publisher instance.
- Một batch Outbox.
- Row locking với skip locked.
- Lease duration.
- Delay publish giả lập.

Kiểm tra:

- Worker chia row thế nào?
- Có row bị claim đồng thời không?
- Worker chết giữa chừng có reclaim được không?
- Lease quá ngắn có làm duplicate tăng không?
- Lease quá dài có làm recovery chậm không?

Tránh claim trùng không loại bỏ duplicate sau broker publish; nó chỉ giảm duplicate do concurrent claim.

---

## 33. Checklist production

### Outbox

- [ ] Business update và Outbox insert cùng transaction.
- [ ] Payload snapshot đúng thời điểm.
- [ ] Event id giữ nguyên qua retry.
- [ ] Schema version được lưu.
- [ ] Aggregate key rõ.
- [ ] Sequence có nếu ordering cần.
- [ ] Publisher có claim và lease.
- [ ] IN_FLIGHT có expiry.
- [ ] Chờ broker confirmation.
- [ ] Mark published sau publish success.
- [ ] Retryable và permanent error phân loại.
- [ ] Quarantine poison record.
- [ ] Có cleanup policy.
- [ ] Có oldest age alert.
- [ ] Test crash sau commit và sau publish.

### Inbox

- [ ] Key gồm consumer identity và message identity.
- [ ] Có unique constraint.
- [ ] Inbox và side effect cùng transaction.
- [ ] Ack sau commit.
- [ ] Duplicate là no-op an toàn.
- [ ] External side effect có idempotency key.
- [ ] Có retry và quarantine.
- [ ] Retention phù hợp replay window.
- [ ] Có duplicate metric.
- [ ] Có concurrency test.

### Saga

- [ ] Saga state durable.
- [ ] Mỗi step có timeout.
- [ ] Correlation và causation đầy đủ.
- [ ] Command có idempotency key.
- [ ] Compensation được thiết kế trước production.
- [ ] Compensation idempotent.
- [ ] Có xử lý UNKNOWN outcome.
- [ ] Có retry budget.
- [ ] Có manual intervention.
- [ ] Có dashboard saga stuck.

---

## 34. Bài tập

### Bài 1

Vẽ sequence diagram:

~~~text
POST /orders
  -> Order DB
  -> Outbox
  -> Publisher
  -> Kafka
  -> Inventory consumer
  -> Inbox
  -> Inventory DB
~~~

Đánh dấu các điểm crash và kết quả.

### Bài 2

Thiết kế bảng Outbox PostgreSQL với index cho claim, aggregate query, cleanup và quarantine.

### Bài 3

Viết transaction boundary cho createOrder. Dòng nào nằm trong transaction và dòng nào không?

### Bài 4

Publisher publish thành công nhưng không update published_at. Phân tích retry và chứng minh reservation không duplicate.

### Bài 5

Consumer insert Inbox, gọi payment provider thành công, nhưng DB rollback. Thiết kế cách tránh capture lần hai.

### Bài 6

So sánh choreography và orchestration cho order gồm inventory, payment, shipment và notification.

### Bài 7

Order đã PaymentCaptured nhưng Order service không nhận event 10 phút. Đề xuất metric, query và recovery.

### Bài 8

Outbox có 10 triệu row PUBLISHED. Thiết kế cleanup không ảnh hưởng transaction ghi mới.

### Bài 9

Thiết kế idempotency key cho create order, reserve inventory, capture payment, release inventory và send email.

### Bài 10

Viết failure matrix cho toàn bộ order saga. Chỉ ra retry, compensation hoặc manual intervention.

---

## 35. Câu hỏi tự kiểm tra

1. Dual-write problem xảy ra khi nào?
2. Vì sao database trước broker có thể mất event?
3. Vì sao broker trước database có thể tạo event ma?
4. Outbox đảm bảo atomicity giữa những gì?
5. Outbox có đảm bảo exactly-once publication không?
6. Vì sao publisher cần lease?
7. Tại sao mark published trước publish nguy hiểm?
8. Inbox cần unique constraint để làm gì?
9. Vì sao Inbox và side effect nên cùng transaction?
10. Ack trước commit tạo failure mode nào?
11. EventId và idempotency key khác nhau ra sao?
12. Khi nào cần aggregate sequence?
13. Polling và CDC khác nhau thế nào?
14. Choreography và orchestration khác nhau thế nào?
15. Compensation có phải rollback database không?
16. Vì sao external HTTP cần idempotency key?
17. Khi nào snapshot tốt hơn aggregate reference?
18. Vì sao cleanup Inbox liên quan replay window?
19. Exactly-once transport có đồng nghĩa exactly-once business không?
20. Khi nào Saga cần manual intervention?

---

## 36. Checklist hoàn thành tuần

- [ ] Giải thích dual-write problem.
- [ ] Tạo order và Outbox cùng transaction.
- [ ] Viết polling publisher có retry.
- [ ] Hiểu claim, lease và reclaim.
- [ ] Mô phỏng crash sau database commit.
- [ ] Chứng minh event không mất.
- [ ] Mô phỏng crash sau broker publish.
- [ ] Chứng minh duplicate delivery có thể xảy ra.
- [ ] Tạo Inbox với unique constraint.
- [ ] Chứng minh side effect chỉ xảy ra một lần.
- [ ] Phân biệt eventId và idempotency key.
- [ ] Hiểu CDC ở mức kiến trúc.
- [ ] Vẽ Saga choreography.
- [ ] Vẽ Saga orchestration.
- [ ] Thiết kế compensating action.
- [ ] Có state machine cho order.
- [ ] Có metrics Outbox/Inbox/Saga.
- [ ] Có cleanup và replay policy.
- [ ] Có integration test.
- [ ] Có runbook cho stuck Outbox và failed compensation.

---

## 37. Kết luận

Outbox giải quyết dual-write bằng cách đảm bảo business state và ý định phát event cùng nằm trong một local transaction. Inbox xử lý duplicate ở consumer để side effect không lặp lại.

Flow production-ready:

~~~text
Producer transaction:
  update business state
  insert outbox
  commit

Relay:
  claim outbox
  publish
  retry hoặc mark published

Consumer transaction:
  insert inbox
  apply business state
  insert next outbox
  commit

Ack:
  chỉ sau local transaction commit
~~~

Từ đó:

- Producer có eventual publication.
- Relay chấp nhận duplicate.
- Consumer có idempotency.
- Saga có state và compensation.
- External side effect có idempotency key hoặc reconciliation.
- Observability phát hiện lag, stuck state và duplicate.

Không có annotation hay broker setting nào biến database, broker và HTTP workflow thành một transaction nguyên tử. Cần thiết kế transaction boundary, identity, retry, state machine và compensation rõ ràng.

---

## 38. Tài liệu tham khảo

- Transactional Outbox pattern: https://microservices.io/patterns/data/transactional-outbox
- Polling Publisher pattern: https://microservices.io/patterns/data/polling-publisher.html
- Spring transaction management: https://docs.spring.io/spring-framework/reference/data-access/transaction.html
- Spring transaction implementation: https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-decl-explained.html
- Spring annotation transactions: https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html
- Debezium Outbox Event Router: https://debezium.io/documentation/reference/transformations/outbox-event-router.html


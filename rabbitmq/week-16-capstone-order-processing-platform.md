# Tuần 16 — Capstone: Order Processing Platform

> Xây một hệ thống nhận đơn hàng không khó. Khó là chứng minh hệ thống không mất event khi database commit thành công, không tạo payment/inventory side effect hai lần khi message redeliver, không retry vô hạn, quan sát được queue buildup và vẫn khôi phục có kiểm soát khi RabbitMQ hoặc database gặp sự cố.

## 1. Bài toán

Xây một nền tảng xử lý order có flow:

```text
Client
  |
  v
order-service
  |  database transaction
  +--> orders table
  +--> outbox table
          |
          v
    outbox publisher
          |
          v
    RabbitMQ orders.events
      |       |          |          |
      v       v          v          v
  payment  inventory  notification audit
```

Khi client gọi tạo order:

1. `order-service` validate request.
2. Lưu order và outbox event trong cùng database transaction.
3. Outbox publisher đọc event pending.
4. Publish `OrderCreated.v1` với publisher confirm.
5. RabbitMQ route event đến payment, inventory, notification và audit queue.
6. Mỗi consumer xử lý side effect rồi manual ack.
7. Lỗi transient đi retry với backoff.
8. Lỗi permanent hoặc vượt max attempt đi DLQ.
9. Admin có thể replay DLQ có giới hạn và có audit log.

## 2. Mục tiêu học tập

Sau capstone, bạn có thể:

- Thiết kế một message flow nhiều consumer.
- Dùng transactional outbox để tránh dual-write giữa PostgreSQL và RabbitMQ.
- Thiết kế envelope có `eventId`, `version`, `correlationId`, `causationId`.
- Dùng exchange, queue, binding, retry queue và DLQ có naming rõ ràng.
- Xử lý publisher confirm, unroutable message và pending publish.
- Dùng manual ack sau business operation.
- Chống duplicate side effect bằng idempotency.
- Phân loại transient/permanent error.
- Thực hiện replay DLQ an toàn.
- Đo queue depth, processing latency, retry và DLQ.
- Chạy với service user least privilege.
- Viết integration test, contract test, load test và failure drill.
- Viết README, ADR và runbook đủ để người khác chạy/review hệ thống.

## 3. Definition of Done

Capstone chỉ hoàn thành khi có đủ cả code, test và vận hành:

```text
[ ] README có architecture diagram, local setup và demo flow.
[ ] Order API lưu order + outbox trong cùng transaction.
[ ] Outbox publisher dùng publisher confirms.
[ ] Envelope có eventId, version, correlationId, causationId.
[ ] Payment/inventory/notification/audit consumer có manual ack.
[ ] Transient error retry tối đa ba lần với backoff.
[ ] Permanent error đi DLQ với error metadata.
[ ] Duplicate message không nhân đôi business side effect.
[ ] Admin replay DLQ có giới hạn và audit log.
[ ] Metrics cho queue depth, processing latency, retry, DLQ.
[ ] Docker Compose chạy được toàn bộ hệ thống.
[ ] Integration test happy path, duplicate, retry, DLQ, broker restart.
[ ] Có load test report và lý do chọn prefetch/concurrency.
[ ] Có security checklist, không commit secret.
[ ] Có runbook queue buildup, DLQ replay, broker unavailable, DB outage.
[ ] Có ADR về exchange, queue type, retry strategy và outbox.
```

## 4. Phạm vi hệ thống

### Thành phần bắt buộc

| Thành phần | Trách nhiệm |
|---|---|
| `order-service` | REST API, PostgreSQL, order state, transactional outbox |
| `outbox-publisher` | Đọc outbox pending, publish có confirm |
| `payment-worker` | Xử lý `PaymentRequested` hoặc payment projection |
| `inventory-worker` | Giữ hoặc từ chối tồn kho |
| `notification-worker` | Gửi email giả lập |
| `audit-consumer` | Lưu audit event |
| RabbitMQ | Routing, queueing, retry, DLQ |
| PostgreSQL | Order, outbox, idempotency/audit tùy thiết kế |

### Thành phần tùy chọn

| Thành phần | Mục đích |
|---|---|
| Redis | Idempotency cache ngắn hạn |
| Prometheus | Scrape metrics |
| Grafana | Dashboard và alert |
| OpenTelemetry | Trace HTTP -> outbox -> consumer |
| MailHog | Xem email giả lập |

Để giảm độ phức tạp, có thể dùng PostgreSQL cho idempotency ở phiên bản đầu. Redis không nên trở thành nguồn sự thật duy nhất nếu duplicate protection phải tồn tại lâu hơn TTL cache.

## 5. Architecture diagram

```text
                                  +------------------+
                                  | payment-worker   |
                                  | payment queue    |
                                  +---------^--------+
                                            |
                                  +---------+--------+
                                  | inventory-worker |
                                  | inventory queue  |
                                  +---------^--------+
                                            |
+--------+       +----------------+         |
| Client | ----> | order-service  |         |
+--------+ HTTP  | PostgreSQL     |         |
                  | orders         |         |
                  | outbox         |         |
                  +-------+--------+         |
                          |                  |
                          v                  |
                  +---------------+          |
                  | outbox        | publish  |
                  | publisher     +----------+------+
                  +---------------+          |       |
                                             v       v
                                      orders.events  orders.dlx
                                      topic exchange topic exchange
                                       |       |       |
                                       v       v       v
                                  notification audit retry/DLQ
```

### Nguyên tắc ownership

```text
order-service sở hữu order state và OrderCreated event.
payment-worker sở hữu payment state.
inventory-worker sở hữu inventory reservation.
notification-worker sở hữu notification attempt.
audit-consumer sở hữu audit record.
RabbitMQ không sở hữu business truth; RabbitMQ vận chuyển message.
```

## 6. Business flow và state machine

### Order state

```text
NEW
 |
 v
PROCESSING
 |       \
 v        v
COMPLETED  REJECTED
              |
              v
           CANCELLED
```

Không nhất thiết phải dùng đúng state này. Điều quan trọng là state transition có rule rõ và không bị duplicate làm chuyển sai trạng thái.

### Các event chính

| Event | Producer | Consumer | Ý nghĩa |
|---|---|---|---|
| `OrderCreated.v1` | order-service | payment, inventory, notification, audit | Order đã được lưu |
| `PaymentRequested.v1` | order/payment flow | payment-worker | Yêu cầu xử lý payment |
| `PaymentApproved.v1` | payment-worker | order/audit/notification | Payment thành công |
| `PaymentRejected.v1` | payment-worker | order/audit/notification | Payment thất bại |
| `InventoryReserved.v1` | inventory-worker | order/audit/notification | Đã giữ hàng |
| `InventoryRejected.v1` | inventory-worker | order/audit/notification | Không đủ hàng |
| `NotificationRequested.v1` | order-service hoặc orchestration | notification-worker | Gửi thông báo |

Trong bản đầu, có thể chỉ phát `OrderCreated` và các worker ghi state riêng. Khi logic đạt độ ổn định, thêm event downstream có schema và contract riêng.

## 7. API tạo order

### Request

```http
POST /orders
Idempotency-Key: client-request-123
X-Correlation-Id: corr-456
Content-Type: application/json

{
  "customerId": "cus-001",
  "items": [
    {
      "sku": "SKU-RED-001",
      "quantity": 2,
      "unitPrice": 125000
    }
  ],
  "currency": "VND"
}
```

### Response

```http
HTTP/1.1 201 Created
Location: /orders/ord-001
Content-Type: application/json

{
  "orderId": "ord-001",
  "status": "NEW",
  "totalAmount": 250000,
  "currency": "VND",
  "createdAt": "2026-10-04T10:00:00Z"
}
```

### Validation

```text
customerId bắt buộc.
items không rỗng.
quantity > 0.
unitPrice >= 0.
currency thuộc allow-list.
Không nhận totalAmount từ client làm nguồn sự thật.
Không nhận orderId tùy ý nếu server sở hữu ID.
```

`Idempotency-Key` giúp retry HTTP request không tạo hai order. Nó không thay thế `eventId` và consumer idempotency.

## 8. Database schema

### Orders

```sql
create table orders (
    id                uuid primary key,
    customer_id       varchar(100) not null,
    status            varchar(30) not null,
    total_amount      numeric(19, 2) not null,
    currency          varchar(3) not null,
    created_at        timestamptz not null,
    updated_at        timestamptz not null,
    version           bigint not null default 0
);
```

### Order items

```sql
create table order_items (
    id                uuid primary key,
    order_id          uuid not null references orders(id),
    sku               varchar(100) not null,
    quantity          integer not null,
    unit_price        numeric(19, 2) not null
);
```

### Outbox

```sql
create table outbox_messages (
    id                uuid primary key,
    aggregate_type    varchar(100) not null,
    aggregate_id      uuid not null,
    event_type        varchar(200) not null,
    schema_version    integer not null,
    routing_key       varchar(200) not null,
    payload           jsonb not null,
    headers           jsonb not null default '{}'::jsonb,
    status            varchar(30) not null,
    attempts          integer not null default 0,
    available_at      timestamptz not null,
    published_at      timestamptz,
    last_error        text,
    created_at        timestamptz not null,
    updated_at        timestamptz not null
);

create index idx_outbox_pending
    on outbox_messages (status, available_at, created_at);
```

### Idempotency records

```sql
create table processed_messages (
    consumer_name     varchar(100) not null,
    message_id        uuid not null,
    processed_at      timestamptz not null,
    result             varchar(30) not null,
    primary key (consumer_name, message_id)
);
```

Unique key `(consumer_name, message_id)` cho phép các consumer độc lập xử lý cùng một event một lần theo identity của consumer.

## 9. Transactional outbox

### Vấn đề dual-write

Code dễ viết nhưng không an toàn:

```java
orderRepository.save(order);
rabbitTemplate.convertAndSend("orders.events", "order.created", event);
```

Nếu database commit thành công nhưng RabbitMQ publish fail, order tồn tại nhưng event mất. Nếu publish thành công rồi database rollback, consumer nhận event cho order không tồn tại.

### Cách làm

```java
@Transactional
public Order createOrder(CreateOrderCommand command) {
    Order order = orderFactory.create(command);
    orderRepository.save(order);

    OrderCreated event = eventFactory.orderCreated(order);
    outboxRepository.save(outboxMessageFactory.from(event));

    return order;
}
```

Cả order và outbox record commit hoặc rollback cùng transaction PostgreSQL.

### Outbox publisher

```text
1. Select batch outbox status=PENDING, available_at <= now.
2. Claim record an toàn cho publisher instance.
3. Publish với messageId/eventId và routing key.
4. Chờ publisher confirm.
5. Confirm ack -> mark PUBLISHED.
6. Nack/timeout -> tăng attempts, ghi error, schedule retry.
7. Không confirm -> coi là uncertain, retry idempotently.
```

### Claim record

Nếu có nhiều publisher instance, tránh hai instance xử lý cùng record vô hạn. Có thể dùng:

- `SELECT ... FOR UPDATE SKIP LOCKED`;
- trạng thái `CLAIMED` có lease expiry;
- advisory lock;
- một publisher đơn giản ở phiên bản đầu.

Mark `PUBLISHED` chỉ sau confirm. Tuy vậy, nếu broker đã nhận message nhưng confirm bị mất, retry có thể tạo duplicate. Consumer idempotency là bắt buộc.

## 10. Message envelope

Envelope thống nhất giúp trace, retry, schema evolution và điều tra:

```json
{
  "eventId": "evt-8db8c0",
  "eventType": "OrderCreated",
  "schemaVersion": 1,
  "occurredAt": "2026-10-04T10:00:00Z",
  "producer": "order-service",
  "correlationId": "corr-456",
  "causationId": "cmd-789",
  "payload": {
    "orderId": "ord-001",
    "customerId": "cus-001",
    "items": [
      {
        "sku": "SKU-RED-001",
        "quantity": 2,
        "unitPrice": 125000
      }
    ],
    "totalAmount": 250000,
    "currency": "VND"
  }
}
```

### Ý nghĩa ID

| Field | Mục đích |
|---|---|
| `eventId` | Identity của event, dùng dedup và audit |
| `eventType` | Tên logical event |
| `schemaVersion` | Version payload |
| `correlationId` | Nối toàn bộ flow của một request/order |
| `causationId` | Event/command trực tiếp gây ra event hiện tại |
| `producer` | Service phát event |
| `occurredAt` | Thời điểm business event xảy ra |

Không đưa password, token, private key hoặc PII không cần thiết vào payload.

## 11. RabbitMQ topology

Topology theo roadmap:

```text
orders.events (topic)
├── order.created -> payment.queue
├── order.created -> inventory.queue
├── order.created -> notification.queue
└── order.*       -> audit.queue

payment.retry.5s       -> payment.retry.30s -> payment.dlq
inventory.retry.5s     -> inventory.retry.30s -> inventory.dlq
notification.retry.5s  -> notification.dlq
```

### Resource naming

```text
vhost: /orders-dev
exchange: orders.events
queue: payment.queue
queue: inventory.queue
queue: notification.queue
queue: audit.queue
exchange: orders.dlx
queue: payment.retry.5s
queue: payment.retry.30s
queue: payment.dlq
```

Production thêm environment vào boundary/vhost, không nhất thiết nhét mọi thứ vào queue name nếu vhost đã tách.

### Exchange type

Chọn `topic` cho `orders.events` vì cần routing key linh hoạt:

```text
order.created
order.payment.approved
order.payment.rejected
order.inventory.reserved
order.inventory.rejected
```

`orders.dlx` cũng có thể là topic để route theo error type hoặc source. Retry queue có thể dùng direct exchange/routing key cụ thể để dễ kiểm soát.

## 12. Queue type và durability

Capstone có thể dùng quorum queue cho các queue business quan trọng:

```text
payment.queue       quorum, durable
inventory.queue     quorum, durable
notification.queue  quorum hoặc classic tùy RPO/RTO
audit.queue         quorum nếu audit event phải giữ an toàn
*.dlq               durable, type theo recovery requirement
```

Decision phải nằm trong ADR. Không chọn quorum chỉ vì “production thì luôn quorum”. Cân nhắc:

- message importance;
- queue lifetime;
- throughput/latency;
- resource budget;
- HA requirement;
- replay/backup strategy.

Queue type phải được quyết định khi declare. Migration từ classic sang quorum cần queue mới và plan drain/replay, không xóa queue tùy tiện.

## 13. Retry topology

### Tại sao không requeue vô hạn?

Nếu consumer cứ `nack(requeue=true)`:

```text
message -> consumer -> fail -> requeue -> consumer -> fail -> ...
```

Message lỗi có thể chiếm consumer, tạo CPU/log storm và chặn message khác.

### Retry stages

```text
main queue
  -> retry.5s
  -> retry.30s
  -> retry.5m hoặc DLQ
```

Mỗi stage cần metadata:

```text
x-attempt
x-first-failed-at
x-last-error-type
x-last-error-message-safe
x-original-routing-key
```

Không ghi stack trace đầy đủ vào message nếu payload có thể đi qua nhiều hệ thống. Lưu `errorId` rồi liên kết với structured log/tracing.

### Phân loại lỗi

```text
Transient:
  timeout, connection reset, downstream 503, temporary DB unavailable

Permanent:
  invalid schema, unknown SKU, unsupported currency, validation failure

Unknown:
  retry giới hạn, sau đó DLQ để điều tra
```

## 14. Consumer workflow

Consumer chuẩn:

```text
receive
  -> validate envelope
  -> check idempotency
  -> process business operation
  -> record processed message
  -> ack
```

Pseudo-code:

```java
public void handle(Message message, Channel channel, long tag) {
    Envelope envelope = parser.parse(message);

    try {
        if (processedMessages.exists("payment-worker", envelope.eventId())) {
            channel.basicAck(tag, false);
            return;
        }

        paymentService.process(envelope);
        processedMessages.record("payment-worker", envelope.eventId());
        channel.basicAck(tag, false);
    } catch (TransientException ex) {
        retryPublisher.publish(envelope, ex);
        channel.basicAck(tag, false);
    } catch (PermanentException ex) {
        dlqPublisher.publish(envelope, ex);
        channel.basicAck(tag, false);
    } catch (Exception ex) {
        channel.basicNack(tag, false, false);
    }
}
```

Điểm quan trọng: retry/DLQ publisher phải có confirm và idempotency strategy. Nếu publish retry fail mà code vẫn ack message gốc, có thể mất message. Một triển khai production cần quyết định rõ cách xử lý failure của chính retry publisher.

## 15. Idempotency design

### Payment

Payment side effect nên có business idempotency key:

```text
payment operation key = orderId + paymentAttemptType
```

Database có unique constraint hoặc payment provider hỗ trợ idempotency key.

### Inventory

Reservation key:

```text
reservation key = orderId + sku
```

Nếu `OrderCreated` redeliver, inventory không giữ thêm lần nữa.

### Notification

Notification có thể chọn:

- idempotency theo `eventId`;
- email provider idempotency key;
- business rule “mỗi order chỉ gửi một confirmation type”.

### Audit

Audit thường cần giữ cả delivery attempt và business event. Có thể unique theo `(eventId, auditType)` nếu không muốn record trùng, hoặc cố ý lưu mọi attempt để điều tra.

## 16. Payment worker

### Input

`OrderCreated.v1` hoặc `PaymentRequested.v1`.

### Processing

```text
1. Parse envelope.
2. Validate amount/currency/orderId.
3. Check payment idempotency key.
4. Gọi payment provider giả lập.
5. Lưu payment result.
6. Phát PaymentApproved/Rejected nếu cần.
7. Ack sau khi state/event đã an toàn.
```

### Error cases

```text
invalid amount -> permanent -> DLQ
unsupported currency -> permanent -> DLQ
provider timeout -> transient -> retry
provider 5xx -> transient -> retry
provider decline -> business result, không retry vô hạn
```

## 17. Inventory worker

### Processing

```text
1. Check eventId/orderId idempotency.
2. Lock/validate stock.
3. Nếu đủ: tạo reservation.
4. Nếu không đủ: ghi rejection event/state.
5. Ack sau commit.
```

Concurrency cần được kiểm soát. Hai message cho cùng SKU có thể chạy song song và vượt tồn kho nếu database locking/atomic update sai.

Test bắt buộc:

- hai order cùng SKU, stock chỉ còn một;
- duplicate event;
- DB timeout trong lúc reserve;
- consumer crash sau reservation trước ack.

## 18. Notification worker

Notification worker gửi email giả lập, ví dụ ghi vào bảng hoặc MailHog.

```text
success -> mark SENT -> ack
temporary mail failure -> retry
invalid recipient -> DLQ hoặc REJECTED
duplicate event -> không gửi email lần hai
```

Notification không nên làm chậm payment/inventory vì mỗi domain có queue riêng và consumer riêng.

## 19. Audit consumer

Audit consumer subscribe các event cần lưu:

```text
order.*
payment.*
inventory.*
notification.*
```

Audit record nên có:

```json
{
  "eventId": "evt-123",
  "eventType": "OrderCreated",
  "aggregateType": "Order",
  "aggregateId": "ord-001",
  "correlationId": "corr-456",
  "producer": "order-service",
  "occurredAt": "2026-10-04T10:00:00Z",
  "recordedAt": "2026-10-04T10:00:01Z"
}
```

Không lưu toàn bộ payload nhạy cảm nếu audit requirement không cần. Audit cũng phải có retention policy.

## 20. API và outbox transaction sample

```java
@Service
public class OrderApplicationService {

    private final OrderRepository orders;
    private final OrderItemRepository items;
    private final OutboxRepository outbox;

    @Transactional
    public OrderResponse create(CreateOrderRequest request,
                                String idempotencyKey,
                                String correlationId) {
        Optional<OrderResponse> existing =
            orders.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return existing.get();
        }

        Order order = Order.createFrom(request, idempotencyKey);
        orders.save(order);
        items.saveAll(order.items());

        OrderCreated event = OrderCreated.from(order);
        outbox.save(OutboxMessage.pending(
            event.eventId(),
            "Order",
            order.id(),
            "OrderCreated",
            1,
            "order.created",
            event,
            correlationId
        ));

        return OrderResponse.from(order);
    }
}
```

`idempotencyKey` phải có unique constraint để hai request concurrent không cùng tạo order.

## 21. Outbox publisher sample

```java
@Scheduled(fixedDelayString = "${outbox.poll-delay-ms:100}")
public void publishBatch() {
    List<OutboxMessage> batch = outbox.claimBatch(100, clock.instant());

    for (OutboxMessage record : batch) {
        try {
            rabbitTemplate.convertAndSend(
                "orders.events",
                record.routingKey(),
                record.toMessage(),
                message -> {
                    message.getMessageProperties()
                        .setMessageId(record.eventId().toString());
                    message.getMessageProperties()
                        .setHeader("eventType", record.eventType());
                    message.getMessageProperties()
                        .setHeader("schemaVersion", record.schemaVersion());
                    return message;
                }
            );

            // Cần chờ/nhận async confirm trước khi mark published.
        } catch (Exception ex) {
            outbox.markRetry(record.id(), safeError(ex));
        }
    }
}
```

Đoạn code chỉ minh họa flow. Production code cần xử lý callback confirm/return, timeout, pending map, batch boundary và concurrency.

## 22. Docker Compose local

Một Compose tối thiểu nên có:

```yaml
services:
  rabbitmq:
    image: rabbitmq:<tested-version>-management
    ports:
      - "5672:5672"
      - "15672:15672"
    environment:
      RABBITMQ_DEFAULT_USER: local-admin
      RABBITMQ_DEFAULT_PASS: ${RABBITMQ_DEFAULT_PASS}

  postgres:
    image: postgres:<tested-version>
    environment:
      POSTGRES_DB: orders
      POSTGRES_USER: orders
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}

  order-service:
    build: ./order-service
    depends_on:
      - rabbitmq
      - postgres

  payment-worker:
    build: ./payment-worker
    depends_on:
      - rabbitmq

  inventory-worker:
    build: ./inventory-worker
    depends_on:
      - rabbitmq

  notification-worker:
    build: ./notification-worker
    depends_on:
      - rabbitmq

  audit-consumer:
    build: ./audit-consumer
    depends_on:
      - rabbitmq
      - postgres
```

### Quy tắc local

- `.env` nằm trong `.gitignore`.
- Password local không dùng ở staging/production.
- Tên image/version được pin trong project.
- `depends_on` không đảm bảo service dependency đã ready; application phải retry connection.
- Management port chỉ mở local.

## 23. Cấu hình Spring AMQP mẫu

```yaml
spring:
  rabbitmq:
    host: ${RABBITMQ_HOST:localhost}
    port: ${RABBITMQ_PORT:5672}
    username: ${RABBITMQ_USERNAME:order-service-local}
    password: ${RABBITMQ_PASSWORD}
    virtual-host: ${RABBITMQ_VHOST:/orders-dev}
    publisher-confirm-type: correlated
    publisher-returns: true
    listener:
      simple:
        acknowledge-mode: manual
        prefetch: 20
        concurrency: 2
        max-concurrency: 8
        default-requeue-rejected: false
```

Property name có thể khác theo Spring Boot/Spring AMQP version. Hãy kiểm tra metadata của version dự án. Ý nghĩa cần giữ:

- publisher confirms bật;
- returns bật nếu cần phát hiện unroutable;
- consumer manual ack;
- prefetch/concurrency có giới hạn;
- lỗi permanent không requeue vô hạn.

## 24. Observability của capstone

### Metrics producer/outbox

```text
outbox_pending_count
outbox_oldest_age_seconds
outbox_publish_success_total
outbox_publish_failure_total
outbox_confirm_latency_seconds
outbox_claimed_stale_count
```

### Metrics RabbitMQ

```text
queue_ready_messages
queue_unacked_messages
queue_total_messages
queue_oldest_message_age
publish_rate
deliver_rate
ack_rate
redelivery_rate
consumer_count
connection_count
blocked_connection_count
```

### Metrics consumer

```text
message_processing_duration_seconds
message_processing_success_total
message_processing_failure_total
message_retry_total
message_dlq_total
message_duplicate_total
downstream_request_duration_seconds
downstream_error_total
```

### Structured log

```json
{
  "event": "message_processed",
  "service": "payment-worker",
  "messageId": "evt-123",
  "eventType": "OrderCreated",
  "schemaVersion": 1,
  "correlationId": "corr-456",
  "causationId": "cmd-789",
  "queue": "payment.queue",
  "routingKey": "order.created",
  "attempt": 1,
  "processingMs": 42,
  "result": "success"
}
```

Không log toàn bộ payment payload, token hoặc thông tin thẻ.

## 25. SLI/SLO cho capstone

Chọn SLO giả định, ví dụ:

| SLI | SLO mẫu |
|---|---|
| Order API success rate | 99.9% theo tháng |
| Outbox publish latency | p95 < 2 giây |
| Payment processing latency | p95 < 10 giây |
| DLQ rate | < 0.1% message |
| Oldest message age | < 60 giây khi steady state |
| Recovery after broker restart | < 5 phút |

Đây là giá trị học tập, không phải con số áp dụng mù quáng. SLO phải dựa trên business requirement và load test.

## 26. Security checklist

```text
[ ] Mỗi service có user riêng.
[ ] User chỉ có quyền configure/write/read cần thiết.
[ ] Không dùng admin user trong worker.
[ ] Vhost dev/staging/prod tách biệt.
[ ] Management UI không public.
[ ] Secret inject qua environment/secret manager.
[ ] Không commit .env/password/token/private key.
[ ] Message không chứa payment secret hoặc PII thừa.
[ ] Có TLS plan cho staging/production.
[ ] Có permission test allow/deny.
```

## 27. Integration test bắt buộc

### Happy path

```text
POST /orders
assert order được tạo
assert outbox được tạo
run publisher
assert confirm
assert payment/inventory/notification/audit nhận event
assert side effects đúng một lần
```

### Duplicate

```text
publish cùng eventId hai lần
assert consumer nhận hai delivery hoặc hai publish
assert business effect chỉ xảy ra một lần
assert duplicate metric tăng
```

### Retry

```text
mock downstream timeout lần 1 và 2
assert retry stage đúng
mock success lần 3
assert message không vào DLQ
```

### DLQ

```text
mock permanent error
assert không requeue vô hạn
assert DLQ chứa message + error metadata
assert audit log ghi lý do
```

### Broker restart

```text
publish batch có message IDs
restart broker/container
assert reconnect
assert message confirmed được xử lý hoặc redeliver an toàn
assert không có business duplicate
```

## 28. Contract test matrix

| Consumer | Fixture | Expected |
|---|---|---|
| Payment | `OrderCreated.v1` hiện tại | Parse + process |
| Payment | `OrderCreated.v1` field thêm | Ignore/accept theo policy |
| Payment | Missing `orderId` | Reject -> DLQ |
| Inventory | `OrderCreated.v1` version cũ | Process nếu supported |
| Notification | Unknown schema version | DLQ, không retry vô hạn |
| Audit | Event có unknown optional field | Lưu metadata chính |

Producer contract test cần đảm bảo event generated đúng schema. Consumer contract test cần đảm bảo consumer không phá khi producer thêm optional field.

## 29. Load test plan

### Workload

```text
baseline: 20 orders/s trong 10 phút
burst: 100 orders/s trong 60 giây
sustained: 40 orders/s trong 30 phút
recovery: về 20 orders/s, đo thời gian drain backlog
```

### Variables

```text
prefetch: 10, 20, 50
consumer concurrency: 1, 2, 4, 8
publisher batch size: 1, 20, 100
payment latency: 20ms, 200ms, 1s
inventory contention: thấp/cao
```

### Report

```text
throughput:
API p50/p95/p99:
outbox lag p95:
confirm latency p95:
consumer processing p95:
queue oldest age:
DLQ count:
redelivery rate:
CPU/memory/disk:
selected prefetch/concurrency:
reason:
```

## 30. Failure drill plan

### Drill A — Queue buildup

```text
1. Làm payment downstream chậm.
2. Theo dõi payment.queue ready/oldest age.
3. Không tăng consumer vô hạn.
4. Kiểm tra DB/downstream capacity.
5. Restore downstream.
6. Đo drain time và duplicate rate.
```

### Drill B — DLQ replay

```text
1. Tạo permanent error có chủ ý.
2. Xác nhận message vào payment.dlq.
3. Sửa nguyên nhân.
4. Replay tối đa một batch nhỏ.
5. Chờ confirm và xử lý.
6. Kiểm tra idempotency/audit.
7. Không replay toàn bộ DLQ mù quáng.
```

### Drill C — Broker unavailable

```text
1. Dừng broker lab.
2. Gọi API tạo order.
3. Xác nhận order + outbox vẫn commit nếu DB còn khỏe.
4. Theo dõi outbox pending.
5. Khởi động broker.
6. Publisher drain outbox.
7. Kiểm tra không mất event và không duplicate side effect.
```

### Drill D — Database outage

```text
1. Làm PostgreSQL unavailable.
2. Gọi API tạo order.
3. Xác nhận error response rõ ràng.
4. Không tạo outbox record mồ côi.
5. Consumer DB failure không ack sớm.
6. Restore DB.
7. Xác nhận retry/recovery theo policy.
```

## 31. Admin replay API

Replay là operation nguy hiểm, phải có authn/authz, giới hạn và audit.

```http
POST /admin/dlq/payment/replay
Authorization: Bearer <admin-token>
Content-Type: application/json

{
  "messageIds": ["evt-123", "evt-456"],
  "reason": "PAYMENT_PROVIDER_FIXED",
  "maxCount": 2,
  "targetRoutingKey": "order.created"
}
```

### Rule

```text
messageIds phải được allow-list từ DLQ.
maxCount có upper bound.
Không replay toàn DLQ qua một request.
Mỗi replay có operator, timestamp, reason.
Replay publish phải có confirm.
Message gốc không xóa trước replay thành công.
Replay có thể tạo duplicate; consumer idempotency vẫn bắt buộc.
```

## 32. Runbook: queue buildup

### Triệu chứng

- `ready` tăng liên tục;
- oldest message age vượt SLO;
- ack rate thấp hơn deliver/publish rate;
- downstream latency tăng;
- consumer capacity thấp.

### Điều tra

```text
1. Queue nào buildup? Domain nào bị ảnh hưởng?
2. Consumer count có đủ và healthy không?
3. Unacked tăng hay ready tăng?
4. Processing latency thay đổi không?
5. DB/downstream có timeout không?
6. Retry/DLQ có tạo storm không?
```

### Hành động

- sửa downstream/DB nếu đó là bottleneck;
- giảm ingress hoặc bật backpressure;
- tăng consumer có kiểm soát nếu downstream còn capacity;
- không tăng prefetch vô hạn;
- theo dõi drain time;
- ghi lại nguyên nhân và hành động.

## 33. Runbook: DLQ replay

```text
1. Đo DLQ count và oldest age.
2. Group theo error type/event version.
3. Xác định permanent cause đã được sửa chưa.
4. Chọn batch replay nhỏ.
5. Replay với audit reason.
6. Theo dõi confirm, processing, duplicate và DLQ return.
7. Dừng nếu DLQ tiếp tục tăng hoặc side effect bất thường.
8. Kết luận message còn lại: replay, sửa data hay business reject.
```

## 34. Runbook: broker unavailable

```text
1. Kiểm tra endpoint, TLS, DNS và RabbitMQ health.
2. Kiểm tra client reconnect/backoff.
3. Kiểm tra outbox pending và oldest age.
4. Không xóa outbox để giảm backlog.
5. Khôi phục broker/network.
6. Theo dõi publisher confirms và drain rate.
7. Kiểm tra duplicate business effect.
8. Ghi incident timeline.
```

## 35. Runbook: database outage

```text
1. Xác định DB không reachable hay query/lock timeout.
2. Kiểm tra API error rate.
3. Kiểm tra outbox publisher và consumer DB failures.
4. Không ack message khi business transaction chưa commit.
5. Khôi phục DB hoặc chuyển read/write theo DR plan.
6. Theo dõi redelivery và idempotency.
7. Kiểm tra outbox stale/failed records.
```

## 36. ADR cần viết

### ADR-001 — Exchange type

```text
Decision: orders.events dùng topic exchange.
Reason: nhiều event/routing key và consumer binding pattern.
Rejected: fanout vì không phải mọi consumer cần mọi event.
Rejected: direct vì cần pattern linh hoạt hơn.
```

### ADR-002 — Queue type

```text
Decision: payment/inventory dùng quorum queue ở production.
Reason: business critical, cần replicated HA.
Trade-off: disk/network/latency/resource cao hơn.
```

### ADR-003 — Retry strategy

```text
Decision: retry queue 5s -> 30s -> DLQ.
Reason: bounded retry, backoff, dễ quan sát.
Rejected: requeue vô hạn vì tạo poison loop.
```

### ADR-004 — Transactional outbox

```text
Decision: order và event commit cùng PostgreSQL transaction.
Reason: tránh mất event giữa DB commit và broker publish.
Trade-off: cần publisher, cleanup và monitoring outbox.
```

## 37. Cấu trúc repository đề xuất

```text
order-processing-platform/
├── order-service/
├── payment-worker/
├── inventory-worker/
├── notification-worker/
├── audit-consumer/
├── contracts/
│   ├── order-created-v1.json
│   └── fixtures/
├── infra/
│   ├── docker-compose.yml
│   ├── rabbitmq/
│   ├── postgres/
│   └── prometheus/
├── tests/
│   ├── integration/
│   ├── contract/
│   ├── performance/
│   └── chaos/
├── docs/
│   ├── architecture.md
│   ├── topology.md
│   ├── adrs/
│   └── runbooks/
└── README.md
```

Nếu monorepo quá nặng cho bài học, có thể gộp worker thành module nhưng vẫn giữ boundary package rõ ràng.

## 38. Lịch thực hiện capstone

| Buổi | Thời lượng | Việc cần làm | Output |
|---|---:|---|---|
| 1 | 90 phút | Đọc bài, vẽ flow và schema | Architecture + ADR draft |
| 2 | 120 phút | Code API, DB và outbox | Order tạo được |
| 3 | 120 phút | Code topology và publisher | Event có confirm |
| 4 | 120 phút | Code payment/inventory worker | Consumer manual ack |
| 5 | 90 phút | Retry, DLQ, idempotency | Failure path hoạt động |
| 6 | 90 phút | Notification, audit, replay | End-to-end flow |
| 7 | 120 phút | Integration/contract test | Test matrix pass |
| 8 | 90 phút | Metrics, log, dashboard | Observability |
| 9 | 90 phút | Security, permission, Compose | Local setup hoàn chỉnh |
| 10 | 120 phút | Load/failure/chaos drill | Report + runbooks |

Tỉ lệ thời gian:

```text
30% đọc và thiết kế
50% code/lab
20% test, quan sát và viết tài liệu
```

## 39. Checklist review trước demo

### Business flow

```text
[ ] POST /orders trả orderId.
[ ] Order và outbox commit atomically.
[ ] Publisher drain outbox.
[ ] Payment/inventory/notification/audit nhận đúng event.
[ ] Order có trạng thái có thể truy vấn.
```

### Reliability

```text
[ ] Confirm thành công được xử lý.
[ ] Unroutable message được phát hiện.
[ ] Consumer manual ack sau business operation.
[ ] Duplicate không tạo duplicate side effect.
[ ] Retry có max attempt/backoff.
[ ] Permanent error vào DLQ.
[ ] Replay có giới hạn/audit.
```

### Operations

```text
[ ] Queue depth/oldest age có metric.
[ ] Processing latency có histogram.
[ ] Correlation ID đi xuyên flow.
[ ] Alert/runbook có link.
[ ] Broker restart đã test.
[ ] DB outage đã test.
[ ] Load report có prefetch/concurrency rationale.
```

### Security

```text
[ ] Không secret trong Git.
[ ] User riêng cho service.
[ ] Permission matrix có negative test.
[ ] Management UI chỉ local/private.
[ ] Message không chứa token/PII thừa.
```

## 40. Tiêu chí demo thành công

Trong một buổi demo, hãy thực hiện đúng thứ tự:

```text
1. Start Compose.
2. Tạo một order.
3. Mở trace/log theo correlationId.
4. Chứng minh outbox record được publish/confirm.
5. Cho payment/inventory/notification/audit xử lý.
6. Làm payment timeout và xem retry.
7. Làm payment permanent error và xem DLQ.
8. Replay một message sau khi sửa nguyên nhân.
9. Publish duplicate eventId và chứng minh side effect không lặp.
10. Restart broker và chứng minh recovery.
11. Mở dashboard metrics.
```

Demo không nên chỉ hiển thị happy path. Giá trị của capstone nằm ở các failure path có bằng chứng.

## 41. Common mistakes

### Publish trực tiếp sau save order

Đây là dual-write không có consistency strategy. Dùng transactional outbox.

### Ack trước DB commit

Consumer crash sau ack sẽ làm mất business operation. Ack sau khi operation an toàn.

### Dùng eventId như idempotency duy nhất cho mọi business

Một event có thể cần nhiều business key. Payment, inventory và notification có thể có idempotency rule riêng.

### Retry mọi exception

Schema lỗi và business rejection không tự biến thành transient chỉ vì retry.

### Replay toàn bộ DLQ

Replay mù có thể tạo storm và duplicate side effect. Replay batch nhỏ, có reason và stop condition.

### Gộp mọi consumer vào một queue

Competing consumers trên một queue chia message, không broadcast cho payment/inventory/notification. Mỗi consumer cần queue riêng bind vào exchange.

### Dùng một user admin

Capstone phải thể hiện least privilege, không chỉ demo được.

### Không cleanup outbox

Outbox pending/published records cần retention và cleanup policy. Nếu không, bảng tăng vô hạn.

### Không đo oldest message age

Queue depth có thể ổn nhưng một message cũ vẫn bị mắc kẹt. Age thường nói rõ user impact hơn count đơn thuần.

## 42. Câu hỏi tự kiểm tra

### Câu 1

Tại sao `order-service` không publish thẳng sau khi save order?

**Đáp án:** Vì database commit và RabbitMQ publish là hai resource độc lập. Transactional outbox giúp event không mất khi DB commit thành công nhưng publish thất bại.

### Câu 2

Vì sao payment, inventory và notification cần queue riêng?

**Đáp án:** Mỗi consumer cần nhận bản sao event, có retry/concurrency/DLQ/ownership riêng. Một queue chung chỉ chia message giữa consumers.

### Câu 3

Consumer nên ack ở đâu?

**Đáp án:** Sau khi business operation và các ghi nhận cần thiết đã hoàn tất an toàn. Ack sớm có thể gây message loss.

### Câu 4

Nếu confirm của outbox publish bị mất nhưng broker đã nhận event thì sao?

**Đáp án:** Publisher có thể retry và tạo duplicate. Consumer phải idempotent theo eventId/business key.

### Câu 5

Khi nào message đi DLQ?

**Đáp án:** Khi lỗi permanent hoặc đã vượt max retry/attempt theo policy. DLQ phải giữ error metadata và có replay policy.

### Câu 6

Capstone chứng minh HA bằng cách nào?

**Đáp án:** Chạy broker restart/failure drill, theo dõi confirm, reconnect, redelivery, queue age và business effect; không chỉ nhìn process còn sống.

## 43. Tài liệu tham khảo chính thức

- [RabbitMQ Tutorials](https://www.rabbitmq.com/tutorials)
- [Spring AMQP Reference](https://docs.spring.io/spring-amqp/reference/)
- [RabbitMQ Consumer Acknowledgements and Publisher Confirms](https://www.rabbitmq.com/docs/confirms)
- [RabbitMQ Reliability Guide](https://www.rabbitmq.com/docs/reliability)
- [RabbitMQ Quorum Queues](https://www.rabbitmq.com/docs/quorum-queues)
- [RabbitMQ Backup and Restore](https://www.rabbitmq.com/docs/backup)
- [Testcontainers RabbitMQ Module](https://java.testcontainers.org/modules/rabbitmq/)

## 44. Kết thúc roadmap

Nếu capstone đạt Definition of Done, bạn không chỉ biết gọi RabbitMQ API. Bạn đã có một cách suy nghĩ hoàn chỉnh:

```text
business transaction
  -> durable event intent
  -> confirmed publish
  -> isolated consumer
  -> manual ack
  -> idempotent side effect
  -> bounded retry
  -> DLQ/replay
  -> metrics/logs/traces
  -> security/HA/runbook
```

Đó là mục tiêu của roadmap: thiết kế message flow có thể quan sát, chịu duplicate, chịu lỗi và vận hành an toàn trong production.

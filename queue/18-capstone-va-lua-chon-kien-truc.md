# Tuần 18: Capstone và lựa chọn kiến trúc

## 1. Mục tiêu capstone

Capstone của tuần là xây một Order Processing Platform có khả năng:

- Nhận request tạo order.
- Persist order và phát OrderCreated.
- Trừ inventory.
- Xử lý payment.
- Phát OrderPaid hoặc OrderFailed.
- Gửi notification.
- Xử lý retry lỗi tạm thời.
- Đưa lỗi vĩnh viễn vào DLQ hoặc DLT.
- Replay event hoặc redrive message an toàn.
- Theo dõi queue depth, lag, latency và failure rate.
- Chứng minh duplicate không tạo duplicate side effect.
- Chạy broker bằng một lệnh.
- Có README, dashboard và incident runbook.

Deliverables:

- Architecture diagram.
- Event catalog.
- Message schema và versioning policy.
- Producer/consumer code.
- Outbox table và publisher.
- Idempotency strategy.
- Retry/DLQ policy.
- Integration test.
- Dashboard.
- Docker Compose.
- README.
- Incident runbook.

Mục tiêu không phải xây một hệ thống thương mại hoàn chỉnh. Mục tiêu là chứng minh bạn có thể thiết kế một message flow production-like, giải thích trade-off và kiểm tra failure model.

---

## 2. Bài toán nghiệp vụ

Một customer tạo order gồm nhiều item.

Order service cần:

1. Validate request.
2. Tạo order.
3. Persist order.
4. Phát OrderCreated.
5. Inventory worker reserve stock.
6. Payment worker capture payment.
7. Order service cập nhật trạng thái.
8. Notification worker gửi email.
9. Analytics consumer ghi nhận event.

Flow thành công:

    POST /orders
      -> OrderCreated
      -> InventoryReserved
      -> PaymentCaptured
      -> OrderConfirmed
      -> NotificationSent

Flow inventory fail:

    POST /orders
      -> OrderCreated
      -> InventoryRejected
      -> OrderFailed

Flow payment fail:

    POST /orders
      -> OrderCreated
      -> InventoryReserved
      -> PaymentFailed
      -> InventoryReleased
      -> OrderFailed

Flow unknown payment:

    PaymentRequested
      -> provider timeout
      -> PaymentPending
      -> status query
      -> PaymentCaptured or PaymentFailed

Order state không được chuyển thẳng từ CREATED sang CONFIRMED nếu inventory hoặc payment chưa có kết quả hợp lệ.

---

## 3. Scope và non-goals

### In scope

- Order API.
- Order database.
- Outbox.
- Một broker được chọn rõ.
- Inventory worker.
- Payment worker giả lập.
- Notification worker giả lập.
- Analytics consumer.
- Retry.
- DLQ/DLT.
- Idempotency.
- Observability.
- Integration test.
- Chaos scenario.
- Docker Compose.
- Runbook.

### Out of scope

- Payment gateway thật.
- Inventory database sharding.
- Multi-region active-active.
- Full user authentication.
- Real email provider.
- Production-grade autoscaling.
- Cross-region disaster recovery.
- Exactly-once toàn workflow.
- UI hoàn chỉnh.

Không mở rộng scope chỉ để thêm công nghệ. Capstone tốt là capstone có failure model rõ và test được.

---

## 4. Functional requirements

### Create order

Request cần có:

- clientRequestId hoặc idempotency key.
- customerId.
- currency.
- items.
- quantity.
- price snapshot hoặc pricing reference.

Response cần cho biết:

- orderId.
- status.
- createdAt.
- correlationId.
- publication state nếu API expose.

### Order events

Tối thiểu:

- OrderCreated.
- InventoryReserved.
- InventoryRejected.
- PaymentRequested.
- PaymentCaptured.
- PaymentFailed.
- InventoryReleased.
- OrderConfirmed.
- OrderFailed.
- NotificationRequested.
- NotificationSent hoặc NotificationFailed.

### Consumer behavior

Inventory:

- Nhận OrderCreated.
- Deduplicate.
- Reserve stock.
- Phát InventoryReserved hoặc InventoryRejected.

Payment:

- Nhận InventoryReserved.
- Tạo payment attempt.
- Capture payment giả lập.
- Phát PaymentCaptured hoặc PaymentFailed.

Notification:

- Nhận OrderConfirmed hoặc PaymentCaptured.
- Gửi email giả lập.
- Có idempotency key.

Analytics:

- Đọc event độc lập.
- Có thể replay.
- Không ảnh hưởng business flow.

---

## 5. Non-functional requirements

### Reliability

- At-least-once delivery chấp nhận được.
- Consumer idempotent.
- Không mất event sau database commit nếu Outbox hoạt động.
- Retry có giới hạn.
- Permanent error vào DLQ/DLT.
- Unknown payment outcome không double charge.

### Performance

Đặt target cho lab:

- Create order API p95 dưới 300 ms khi broker và database khỏe.
- Event publication p95 dưới 2 giây.
- Consumer processing p95 dưới 500 ms trong normal path.
- Backlog recover sau test burst.
- Không retry storm khi payment provider fail.

Các target này là lab baseline, không phải universal production SLA.

### Operability

- Có structured logs.
- Có correlation ID và causation ID.
- Có metrics queue/lag/latency/retry/DLQ.
- Có dashboard.
- Có runbook.
- Có health/readiness endpoint.
- Có graceful shutdown.

### Security

- Không hardcode secret.
- Dùng environment variables hoặc secret manager.
- Không log payment token.
- Không đưa PII thừa vào event.
- Queue/topic name không chứa customer data.
- Test dùng synthetic data.
- Broker credentials tối thiểu quyền.

---

## 6. Lựa chọn kiến trúc tổng thể

Kiến trúc baseline:

    Order API
       |
       v
    Order Service
       |
       +--> Order Database
       |
       +--> Outbox Table
                  |
                  v
             Message Relay
                  |
                  v
             Broker
          /      |       |       \
         v       v       v        v
    Inventory  Payment  Notify  Analytics
      worker    worker  worker  consumer
         |       |       |
         v       v       v
    Inventory Payment Notification
      state    state    state
         \       |       /
          \      |      /
           +-----+----+
                 |
                 v
             Order events
             and status
                 |
                 v
             Order Service
                 |
                 v
             Order Database

Producer transaction:

    update orders
    insert outbox event
    commit

Consumer transaction:

    insert inbox identity
    update local state
    insert next outbox event
    commit
    ack or commit message

Đây là local transaction + eventual consistency + idempotent processing.

---

## 7. Chọn RabbitMQ, Kafka hay SQS

### Chọn RabbitMQ khi

- Capstone ưu tiên work queue.
- Cần routing bằng exchange và binding.
- Message thường xử lý xong rồi biến mất.
- Cần triển khai local nhanh.
- Muốn mô phỏng competing consumers.
- Muốn test ack, nack, prefetch và DLX.
- Throughput vừa phải.
- Replay không phải capability trung tâm.

RabbitMQ publisher confirms bảo vệ việc publisher biết broker đã nhận responsibility cho message; consumer acknowledgements bảo vệ chiều broker tới consumer. Hai cơ chế độc lập. [RabbitMQ publisher confirms](https://www.rabbitmq.com/docs/confirms)

### Chọn Kafka khi

- Capstone ưu tiên event log.
- Analytics cần replay.
- Nhiều consumer group độc lập.
- Cần partition và ordering theo orderId.
- Cần theo dõi lag.
- Muốn test retry topic và DLT.
- Throughput lớn hơn.
- Retention là một phần của design.

### Chọn SQS khi

- Muốn mô phỏng AWS managed queue.
- Không muốn vận hành broker.
- Workload chủ yếu là queue.
- Có thể dùng Standard cho throughput và chấp nhận duplicate/order variance.
- Dùng FIFO khi ordering theo MessageGroupId quan trọng.
- Chấp nhận poll/delete, visibility timeout và retention semantics.

SQS Standard cung cấp at-least-once và có thể duplicate hoặc out-of-order; FIFO dùng message group để giữ ordering theo group. [SQS Standard queues](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/standard-queues.html) · [SQS FIFO queues](https://docs.aws.amazon.com/en_en/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-fifo-queues.html)

### Quyết định khuyến nghị cho capstone

Làm một primary implementation bằng RabbitMQ hoặc Kafka, sau đó viết decision record so sánh SQS.

Khuyến nghị RabbitMQ nếu mục tiêu chính là hoàn thành full flow nhanh:

- Exchange/routing rõ.
- Queue/DLQ trực quan.
- Local Docker Compose đơn giản.
- Dễ test ack/requeue/prefetch.

Khuyến nghị Kafka nếu mục tiêu chính là event platform:

- Order events cần replay.
- Analytics là consumer quan trọng.
- Muốn thể hiện partition, consumer group và lag.

Không triển khai cả ba broker trong cùng một capstone bản đầu. Điều đó làm loãng thời gian và tăng surface area.

---

## 8. Decision record mẫu

### Decision

Chọn Kafka cho Order Processing Platform.

### Context

- OrderCreated có nhiều consumer độc lập.
- Analytics cần replay.
- Ordering theo orderId là cần thiết.
- Muốn theo dõi consumer lag.
- Retry và DLT là topic-level flow.

### Alternatives

RabbitMQ:

- Routing và work queue tốt.
- Local setup đơn giản.
- Replay history không tự nhiên bằng Kafka.

SQS:

- Managed service.
- Tích hợp AWS tốt.
- Cần quyết định Standard/FIFO.
- Replay dài hạn và nhiều consumer group không phải strength chính.

Kafka:

- Event log và retention.
- Consumer group độc lập.
- Partition theo orderId.
- Có thêm chi phí vận hành và config.

### Consequences

Positive:

- Analytics replay được.
- Ordering theo orderId.
- Lag đo được.
- Nhiều consumer group độc lập.

Negative:

- Cần quản lý topic/partition.
- Retry topic có thể làm ordering phức tạp.
- Local lab cần Kafka container.
- Team phải hiểu offset và consumer group.

### Revisit trigger

Đổi sang RabbitMQ hoặc SQS nếu:

- Workload chuyển thành task queue.
- Không cần replay.
- Kafka vận hành vượt năng lực team.
- AWS managed service là yêu cầu bắt buộc.

---

## 9. Kiến trúc chọn Kafka

### Topics

    order.events
    order.commands
    inventory.events
    payment.events
    notification.commands
    analytics.events

Retry/DLT:

    order.events.retry.10s
    order.events.retry.1m
    order.events.DLT
    inventory.events.DLT
    payment.events.DLT
    notification.commands.DLT

### Consumer groups

    inventory-service
    payment-service
    notification-service
    analytics-service
    order-state-projector

Mỗi business consumer cần group riêng nếu tất cả phải nhận event.

### Partition key

Dùng orderId cho event liên quan order:

    key = orderId

Lợi ích:

- Các event cùng order có cơ hội vào cùng partition.
- Ordering được giới hạn theo orderId.
- Consumer scale theo partition.

Không dùng customerId nếu một customer tạo quá nhiều order và tạo hot partition.

### Partition count

Lab baseline:

    order.events = 6 partitions
    inventory.events = 6 partitions
    payment.events = 6 partitions

Partition count phải đủ cho consumer parallelism nhưng không tạo topology thừa.

---

## 10. Kiến trúc chọn RabbitMQ

### Exchanges

    order.events
    order.commands
    inventory.events
    payment.events
    notification.commands
    dead-letter.exchange

### Queues

    inventory.order-created
    payment.inventory-reserved
    notification.order-confirmed
    analytics.order-events
    order-state-projector

Retry queues:

    inventory.order-created.retry.10s
    inventory.order-created.retry.1m

DLQ:

    inventory.order-created.dlq
    payment.inventory-reserved.dlq
    notification.order-confirmed.dlq

### Routing keys

    order.created
    inventory.reserved
    inventory.rejected
    payment.requested
    payment.captured
    payment.failed
    order.confirmed
    order.failed

### Delivery rules

- Producer dùng publisher confirms.
- Consumer dùng manual acknowledgement.
- Ack sau local transaction.
- Retry có retry count.
- Permanent error reject không requeue và dead-letter.
- Queue durable.
- Message persistent khi durability cần.

---

## 11. Kiến trúc chọn SQS

### Standard profile

Queues:

    order-events
    inventory-commands
    payment-commands
    notification-commands
    analytics-events

Dùng khi:

- Throughput là ưu tiên.
- Consumer idempotent.
- Ordering không strict.
- Duplicate delivery được xử lý.

### FIFO profile

Queues:

    order-events.fifo
    payment-commands.fifo
    inventory-commands.fifo

MessageGroupId:

    orderId

Dùng khi:

- Các message cùng order phải xử lý tuần tự.
- Throughput theo group đủ.
- Team hiểu FIFO group và DLQ effect.

MessageDeduplicationId nên là business event identity hoặc idempotency key phù hợp. Queue type được quyết định lúc tạo và không nên coi Standard/FIFO là switch runtime. [SQS CreateQueue](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/APIReference/API_CreateQueue.html)

### SQS delivery flow

    send
      -> receive
      -> visibility timeout
      -> process
      -> delete

Nếu process fail hoặc không delete, message có thể receive lại. Consumer phải idempotent.

---

## 12. Domain model

### Order

Fields:

- orderId.
- customerId.
- currency.
- totalMinor.
- status.
- version.
- createdAt.
- updatedAt.
- idempotencyKey.

Statuses:

    PENDING
    INVENTORY_RESERVED
    PAYMENT_PENDING
    CONFIRMED
    INVENTORY_REJECTED
    PAYMENT_FAILED
    FAILED
    COMPENSATING

### Order item

Fields:

- sku.
- quantity.
- unitPriceMinor.
- productSnapshot.
- lineTotalMinor.

Không đọc giá hiện tại từ product service khi replay OrderCreated nếu event đã chứa price snapshot.

### Payment attempt

Fields:

- paymentAttemptId.
- orderId.
- idempotencyKey.
- amountMinor.
- currency.
- providerReference.
- status.
- lastError.
- createdAt.
- updatedAt.

Statuses:

    PENDING
    PROCESSING
    CAPTURED
    FAILED_RETRYABLE
    FAILED_PERMANENT
    UNKNOWN
    REFUND_PENDING
    REFUNDED

### Inventory reservation

Fields:

- reservationId.
- orderId.
- idempotencyKey.
- sku.
- quantity.
- status.
- createdAt.

Unique key:

    orderId + sku + reservation version

### Notification request

Fields:

- notificationId.
- orderId.
- recipient.
- template.
- idempotencyKey.
- status.
- sentAt.

Không lưu raw payment credential trong domain hoặc event.

---

## 13. Event envelope

Envelope baseline:

    eventId
    eventType
    eventVersion
    occurredAt
    producer
    aggregateType
    aggregateId
    correlationId
    causationId
    traceId
    schemaVersion
    payload

Business events:

- OrderCreated.
- InventoryReserved.
- InventoryRejected.
- PaymentCaptured.
- PaymentFailed.
- InventoryReleased.
- OrderConfirmed.
- OrderFailed.

Commands:

- ReserveInventory.
- CapturePayment.
- ReleaseInventory.
- SendOrderNotification.

Không dùng command và event cùng một tên nếu semantics khác.

---

## 14. Event catalog

### OrderCreated

Owner:

    order-service

Purpose:

    Order đã được persist và sẵn sàng cho downstream processing.

Key:

    orderId

Consumers:

- inventory-service.
- analytics-service.
- order-state-projector.

Retry:

- Invalid schema vào DLT.
- Dependency error retry.
- Business invalid quarantine.

Retention:

    14 days for lab

PII:

- customerId.
- Có thể có shipping/contact data nếu cần.
- Không có secret.

### InventoryReserved

Owner:

    inventory-service

Purpose:

    Inventory đã được reserve cho order.

Consumers:

- payment-service.
- order-state-projector.
- analytics-service.

### PaymentCaptured

Owner:

    payment-service

Purpose:

    Payment provider xác nhận capture theo idempotency key.

Consumers:

- order-service.
- notification-service.
- analytics-service.

### PaymentFailed

Owner:

    payment-service

Purpose:

    Payment thất bại permanent hoặc đã exhaust retry.

Consumers:

- order-service.
- inventory-service nếu cần compensation.
- analytics-service.

Mỗi event catalog entry cần owner, schema, compatibility, retention, privacy và replay policy.

---

## 15. Message schema và versioning policy

Schema v1 của OrderCreated:

    orderId: required string
    customerId: required string
    currency: required ISO code
    totalMinor: required non-negative integer
    items: required non-empty list
    createdAt: required UTC timestamp

Schema v2 có thể thêm:

    discountMinor: optional non-negative integer
    shippingFeeMinor: optional non-negative integer
    customerSegment: optional enum

Rules:

- Không đổi ý nghĩa field cũ.
- Field mới thêm optional trước.
- Consumer cũ bỏ qua field mới.
- Consumer mới có default hợp lệ.
- Enum có UNKNOWN branch.
- Không đổi unit của totalMinor.
- Không đổi eventType cho semantics mới.
- Breaking change dùng event type hoặc major version mới.
- Schema compatibility test chạy trong CI.

Consumer v2 đọc v1:

    missing discountMinor -> zero only if semantics v1 confirms total already includes discount
    missing shippingFeeMinor -> unknown unless contract defines zero
    missing customerSegment -> UNKNOWN

Schema valid không đảm bảo business valid. Vẫn cần business invariant test.

---

## 16. Order API

### Endpoint

    POST /orders

Headers:

    Idempotency-Key
    X-Correlation-Id
    trace context

Request:

    customerId
    currency
    items
    clientRequestId

Response:

    orderId
    status = PENDING
    correlationId
    createdAt

### API idempotency

Lưu:

- Scope.
- Idempotency key.
- Request hash.
- Resource ID.
- Response snapshot.
- Status.
- Expiry.

Nếu cùng key và cùng request:

    return original result

Nếu cùng key nhưng payload khác:

    return conflict

API idempotency và event idempotency là hai lớp khác nhau.

---

## 17. Order service transaction

Transaction:

    validate command
    check idempotency key
    create order
    create order items
    create OrderCreated event snapshot
    insert outbox
    persist idempotency result
    commit

Không làm trong transaction:

    call payment provider
    call inventory service
    send email
    publish directly to broker

Outbox record cần:

- eventId.
- aggregateId.
- eventType.
- eventVersion.
- key.
- payload.
- headers.
- status.
- attempt count.
- availableAt.
- publishedAt.

Nếu transaction rollback:

- Không có order committed.
- Không có Outbox committed.
- API retry dùng cùng idempotency key.

---

## 18. Outbox publisher

Publisher flow:

    claim PENDING rows
      -> mark IN_FLIGHT with lease
      -> publish to broker
      -> wait confirmation
      -> mark PUBLISHED
      -> release or quarantine on error

Failure:

    crash before publish
      -> lease expires
      -> retry

    crash after publish before mark
      -> retry may duplicate
      -> consumer idempotency protects effect

Publisher metrics:

- outbox_pending.
- outbox_oldest_age.
- outbox_publish_rate.
- outbox_failure_rate.
- outbox_retry_count.
- outbox_quarantine_count.
- publication_latency.
- lease_expired_count.

Không xóa Outbox record ngay sau send nếu chưa có confirmation.

---

## 19. Inventory worker

Input:

    OrderCreated

Transaction:

    insert Inbox(eventId)
    if duplicate:
        no-op
    else:
        check stock
        create reservation
        update inventory
        insert InventoryReserved or InventoryRejected Outbox
    commit
    ack or commit message

Idempotency key:

    reserve-inventory:{orderId}:{inventoryVersion}

Failures:

- Database deadlock: retry.
- Inventory insufficient: business rejection.
- Schema invalid: DLT.
- Database unavailable: retry with budget.
- Duplicate event: no-op.

Invariant:

    available stock never becomes negative
    one event does not create two reservations
    InventoryRejected does not reserve stock

---

## 20. Payment worker

Input:

    InventoryReserved

Transaction A:

    insert Inbox
    create payment attempt with stable idempotency key
    mark PaymentPending
    insert PaymentRequested or internal work record
    commit
    ack

Provider call:

    capture with idempotency key
    receive success, failure, timeout or unknown

Transaction B:

    update payment attempt
    insert PaymentCaptured or PaymentFailed Outbox
    commit

Unknown outcome:

    status query provider
    if captured:
        mark CAPTURED
    if pending:
        retry status query
    if not found:
        follow provider-specific policy

Invariant:

    same idempotency key creates at most one capture
    PaymentFailed does not imply provider never captured
    PaymentCaptured emitted once per payment attempt

---

## 21. Notification worker

Input:

    OrderConfirmed or PaymentCaptured

Transaction:

    insert Inbox
    create notification request
    commit
    ack

Email worker:

    send with idempotency key
    mark sent
    retry temporary provider errors
    DLT permanent template/address errors

Không coi email send retry là safe nếu provider không hỗ trợ idempotency. Có thể cần notification attempt table và provider message ID.

Metrics:

- notification_processed.
- email_success.
- email_failure.
- provider_timeout.
- duplicate_notification_suppressed.
- DLT count.

---

## 22. Analytics consumer

Analytics không được block payment hoặc inventory.

Thiết kế:

- Consumer group riêng.
- Topic hoặc queue subscription riêng.
- Retry policy riêng.
- DLT riêng.
- Có replay.
- Có schema evolution.
- Có batch insert nếu phù hợp.

Analytics failure không được làm Order service rollback vì event đã commit. Đây là một lý do không dùng một shared consumer group cho mọi downstream.

Analytics có thể chấp nhận:

- Eventual consistency.
- Replay.
- Late events.
- Duplicate input nếu sink idempotent.

---

## 23. Saga choreography

Flow choreography:

    OrderCreated
      -> inventory-service

    InventoryReserved
      -> payment-service

    PaymentCaptured
      -> order-service

    PaymentFailed
      -> inventory-service release
      -> order-service fail

Ưu điểm:

- Ít central coordinator.
- Service tự sở hữu logic.
- Tự nhiên với event broker.
- Dễ thêm analytics consumer.

Nhược điểm:

- Flow phân tán.
- Debug khó hơn.
- Timeout rải rác.
- Compensation khó nhìn toàn cục.
- Có thể tạo event chain dài.

Chọn choreography cho capstone bản đầu nếu flow chỉ có vài bước và state transition đã rõ.

---

## 24. Saga orchestration

Nếu muốn coordinator:

    OrderSagaCoordinator
      -> ReserveInventory
      <- InventoryReserved

    OrderSagaCoordinator
      -> CapturePayment
      <- PaymentCaptured

    OrderSagaCoordinator
      -> ConfirmOrder

Payment fail:

    coordinator
      -> ReleaseInventory
      -> MarkOrderFailed

Coordinator cần:

- Saga ID.
- Current state.
- Current step.
- Deadline.
- Retry count.
- Compensation state.
- Inbox.
- Outbox.
- Idempotency.

Orchestration phù hợp khi flow nhiều bước, nhiều timeout và compensation. Không thêm coordinator nếu nó chỉ chuyển tiếp event mà không sở hữu workflow decision.

---

## 25. Compensation

| Success trước đó | Failure sau đó | Compensation |
|---|---|---|
| InventoryReserved | PaymentFailed | InventoryReleased |
| PaymentCaptured | Order invalid | RefundRequested |
| NotificationRequested | Email provider fail | Retry hoặc manual |
| ShipmentCreated | Order cancelled | CancelShipment nếu khả thi |

Compensation cần:

- Idempotency key.
- State transition.
- Retry budget.
- Timeout.
- Audit.
- Manual intervention.
- UNKNOWN handling.

Refund không phải rollback database. Nó là một workflow mới có trạng thái riêng.

---

## 26. Retry và DLQ policy

### Retryable

- Network timeout.
- HTTP 503.
- HTTP 429.
- Database deadlock.
- Broker unavailable.
- Temporary connection failure.

### Permanent

- Invalid schema.
- Required field missing.
- Invalid enum.
- Business rule violation.
- Unauthorized request.
- Unsupported event version.

### Unknown

- Payment timeout.
- Connection reset after provider may have committed.
- Broker timeout with uncertain acceptance.

Retry baseline:

    attempt 1: immediate
    attempt 2: 1 second
    attempt 3: 5 seconds
    attempt 4: 30 seconds
    then DLQ or DLT

Thêm jitter và overall deadline.

DLQ metadata:

- messageId.
- eventId.
- eventType.
- schemaVersion.
- original destination.
- partition/offset hoặc routing key.
- attempt count.
- failure category.
- exception type.
- correlationId.
- traceId.
- consumer version.
- first failed time.
- last failed time.

Không retry schema lỗi vô hạn.

---

## 27. Idempotency strategy

### API

    scope = order-api
    key = client-provided idempotency key
    unique(scope, key)

### Event consumer

    unique(consumer_name, event_id)

### Inventory

    unique(order_id, reservation_version)

### Payment

    unique(payment_attempt_id, provider_idempotency_key)

### Notification

    unique(order_id, notification_type, version)

### Replay

Giữ eventId gốc, thêm:

    replayId
    replayReason
    replayedBy
    replayedAt

Invariant:

- Duplicate transport không tạo duplicate business effect.
- Cùng idempotency key khác payload bị reject.
- Provider timeout không tạo double charge.
- Replay có rate limit và canary.

---

## 28. Ordering và partition strategy

Order events dùng key orderId.

Bảo đảm:

- Events cùng order có cùng partition trong Kafka.
- MessageGroupId = orderId nếu dùng SQS FIFO.
- RabbitMQ routing/prefetch policy không giả vờ global ordering.
- Consumer kiểm tra sequence nếu state transition yêu cầu.

Không bảo đảm:

- Global ordering giữa mọi order.
- Ordering giữa nhiều consumer group.
- Ordering sau replay nếu replay sai topology.
- Ordering của retry topic nếu không preserve partition/key.

Nếu event sequence 3 đến trước sequence 2:

- Buffer.
- Retry.
- Pause key.
- Quarantine.
- Hoặc xử lý no-op nếu event commutative.

Policy phải được document theo event type.

---

## 29. Chọn persistence

Order service:

    PostgreSQL
    orders
    order_items
    outbox_events
    idempotency_records

Inventory service:

    PostgreSQL
    inventory_items
    reservations
    inbox_messages
    outbox_events

Payment service:

    PostgreSQL
    payment_attempts
    inbox_messages
    outbox_events

Notification service:

    PostgreSQL
    notification_attempts
    inbox_messages

Analytics:

    database hoặc sink riêng
    event projection
    replayable input

Không dùng một database schema chung để giả lập microservice mà không document ownership. Nếu dùng một PostgreSQL container với nhiều schema, mỗi service vẫn phải chỉ truy cập bảng của mình.

---

## 30. Docker Compose

RabbitMQ profile:

    services:
      rabbitmq:
        image: rabbitmq:4-management
        ports:
          - "5672:5672"
          - "15672:15672"

Kafka profile:

    services:
      kafka:
        image: apache/kafka
        ports:
          - "9092:9092"

Database:

    services:
      postgres:
        image: postgres
        environment:
          POSTGRES_DB: orders
          POSTGRES_USER: app
          POSTGRES_PASSWORD: test-only
        ports:
          - "5432:5432"

Quy tắc:

- Pin version cụ thể trong project thật.
- Dùng test-only credential local.
- Không commit production secret.
- Có healthcheck.
- Có volume riêng cho lab nếu cần persistence.
- Có network riêng.
- Có profile để chọn RabbitMQ hoặc Kafka.
- Không khởi động cả ba broker nếu không cần.

Docker Compose deliverable cần có:

- compose.yaml.
- .env.example.
- README command.
- Healthcheck.
- Migration command.
- Seed command.
- Test command.
- Cleanup command.

---

## 31. README structure

README nên có:

### Overview

Mục tiêu và flow.

### Architecture

Diagram và decision record.

### Prerequisites

- Java version.
- Maven hoặc Gradle.
- Docker.
- AWS CLI nếu dùng SQS.
- Testcontainers requirements.

### Start

    docker compose up -d
    ./gradlew bootRun

### Test

    ./gradlew test
    ./gradlew integrationTest

### Create order

Mô tả request, response và idempotency key.

### Observe

- Management UI.
- Dashboard.
- Log query.
- Trace query.

### Failure demo

- Stop consumer.
- Stop broker.
- Force payment timeout.
- Replay DLT.
- Verify duplicate suppression.

### Operations

- Retry policy.
- DLQ triage.
- Replay.
- Cleanup.
- Recovery.

### Trade-offs

Ghi vì sao chọn broker, partition/queue, retention và ordering.

---

## 32. Integration test matrix

| Scenario | Expected result |
|---|---|
| Create order success | Order + Outbox commit |
| API retry same key | Không tạo order thứ hai |
| Producer crash after commit | Outbox publish sau restart |
| Inventory success | Reservation một lần |
| Inventory insufficient | InventoryRejected |
| Payment success | PaymentCaptured |
| Payment timeout | Retry/status query |
| Payment unknown | Không double charge |
| Consumer crash before ack | Redelivery |
| Duplicate event | Không duplicate side effect |
| Invalid schema | DLT/DLQ |
| Retry exhaustion | DLT/DLQ |
| Broker unavailable | Timeout, retry, no silent loss |
| Slow consumer | Backlog tăng rồi recover |
| Hot partition | Partition lag được quan sát |
| Analytics failure | Business flow không bị rollback |
| Replay DLT | Canary rồi controlled batch |

Mỗi test phải assert:

- Message state.
- Database state.
- Side effect count.
- Outgoing event count.
- Ack/commit.
- Retry count.
- DLQ/DLT.
- Observability evidence.

---

## 33. Testcontainers test plan

### Test 1: Database + Outbox

- Start Postgres.
- Run migration.
- Call create order.
- Assert order and Outbox.
- Force rollback.
- Assert both absent.

### Test 2: Broker publish

- Start broker.
- Start application.
- Publish event.
- Consume event.
- Assert key, header, payload.

### Test 3: Full flow

- Start database and broker.
- Start producers/consumers.
- Create order.
- Wait for confirmation.
- Assert all state transitions.

### Test 4: Duplicate

- Publish same event twice.
- Assert Inbox one row.
- Assert side effect once.

### Test 5: Retry/DLT

- Fake dependency fails.
- Assert attempts.
- Assert DLT.
- Assert main flow continues.

### Test 6: Restart

- Stop consumer.
- Publish message.
- Start consumer.
- Assert eventual process.

Spring Kafka có Embedded Kafka và KafkaTestUtils; nếu cần broker/container gần môi trường thật hơn, dùng Testcontainers Kafka. [Spring Kafka testing](https://docs.spring.io/spring-kafka/reference/testing.html) · [Testcontainers Kafka](https://java.testcontainers.org/modules/kafka/)

---

## 34. Dashboard capstone

### Business

- Orders created.
- Orders confirmed.
- Orders failed.
- Payments captured.
- Inventory reserved.
- Notifications sent.
- Saga stuck.

### Messaging

- Publish rate.
- Process rate.
- Ack/delete/commit rate.
- Redelivery.
- Retry.
- DLQ/DLT.
- Queue depth.
- Consumer lag.
- Oldest message age.

### Latency

- API p50/p95/p99.
- Publication latency.
- Inventory processing.
- Payment processing.
- Notification processing.
- End-to-end order confirmation.

### Dependencies

- Database latency.
- Payment provider timeout.
- Inventory DB errors.
- Broker request latency.
- Connection pool.
- Circuit breaker state.

### Deployment

- Producer version.
- Consumer version.
- Schema version.
- Config version.
- Deploy marker.

Alert:

- Order confirmation SLO breach.
- Payment DLT increase.
- Inventory queue oldest age.
- Consumer count zero.
- Kafka partition lag.
- RabbitMQ unacked stuck.
- SQS oldest message age.
- Broker unavailable.

---

## 35. Incident runbook: consumer không xử lý

### Symptoms

- Queue depth tăng.
- Oldest age tăng.
- Consumer count thấp hoặc capacity thấp.
- Ack rate giảm.

### Checks

1. Service health/readiness.
2. Consumer logs.
3. Broker connection.
4. Consumer assignment.
5. Database/downstream latency.
6. Error/retry/DLQ.
7. Recent deployment/config.
8. Thread pool và connection pool.
9. Partition/queue hot spot.

### Actions

- Nếu consumer chết: restart hoặc rollback.
- Nếu dependency down: mở circuit, giảm retry.
- Nếu schema lỗi: dừng producer rollout.
- Nếu partition poison: DLT/quarantine.
- Nếu backlog bình thường nhưng business fail: kiểm tra ack/commit order.
- Nếu cần scale: xác nhận partition/queue capacity trước.

### Recovery

- Xác nhận consumer xử lý message mới.
- Backlog rate âm.
- Oldest age giảm.
- DLQ không tăng bất thường.
- Business success phục hồi.
- Ghi timeline.

---

## 36. Incident runbook: payment timeout

### Symptoms

- Payment processing latency tăng.
- Timeout rate tăng.
- Retry rate tăng.
- PaymentPending tăng.
- Order confirmation giảm.

### Checks

- Provider status.
- Idempotency key.
- Payment attempt state.
- Unknown outcomes.
- Circuit breaker.
- DLT.
- Reconciliation lag.

### Actions

- Không retry mù capture.
- Query provider status.
- Giảm payment concurrency.
- Tạm pause capture nếu cần.
- Giữ order ở PAYMENT_PENDING.
- Chạy reconciliation.
- Chỉ phát PaymentCaptured khi provider xác nhận.

### Recovery

- Provider healthy.
- Pending attempts được reconcile.
- Không duplicate charge.
- PaymentCaptured/PaymentFailed phát đúng một lần.
- Order saga tiếp tục.

---

## 37. Incident runbook: DLQ/DLT tăng

### Bước 1

Đếm theo:

- Event type.
- Producer version.
- Consumer version.
- Schema version.
- Failure category.
- Time window.

### Bước 2

Lấy sample an toàn:

- Không log raw PII.
- Lấy event ID.
- Lấy correlation ID.
- Lấy exception category.
- Lấy original destination.

### Bước 3

Chọn remediation:

- Code bug: fix và deploy.
- Schema mismatch: compatibility fix.
- Data invalid: migrate hoặc reject.
- Dependency outage: chờ recover.
- Unknown payment: reconcile.
- Security issue: quarantine.

### Bước 4

Replay:

- Một message.
- Năm message.
- Batch nhỏ.
- Rate limit.
- Theo dõi side effect.
- Stop khi lỗi tăng.

Không replay toàn DLQ trong một lệnh duy nhất.

---

## 38. Incident runbook: broker unavailable

### RabbitMQ

- Kiểm tra connection/channel.
- Kiểm tra publisher confirm.
- Kiểm tra queue durability.
- Kiểm tra disk/memory alarm.
- Kiểm tra consumer reconnect.
- Kiểm tra Outbox pending.

### Kafka

- Kiểm tra broker health.
- Kiểm tra metadata.
- Kiểm tra offline partitions.
- Kiểm tra producer timeout.
- Kiểm tra consumer rebalance.
- Kiểm tra lag sau recovery.

### SQS

- Kiểm tra AWS API errors/throttling.
- Kiểm tra IAM.
- Kiểm tra receive/delete.
- Kiểm tra visibility timeout.
- Kiểm tra CloudWatch metrics.
- Kiểm tra DLQ/redrive permissions.

### Recovery criteria

- Producer/relay publish trở lại.
- Consumer reconnect.
- Backlog không tăng.
- Oldest age giảm.
- Không có silent loss.
- Retry ratio bình thường.

---

## 39. Security checklist

- [ ] Secrets ở environment/secret manager.
- [ ] Không hardcode password.
- [ ] Broker access least privilege.
- [ ] TLS nếu môi trường yêu cầu.
- [ ] SQS IAM role thay vì long-lived access key.
- [ ] KMS/SSE khi dùng AWS.
- [ ] Queue/topic name không chứa PII.
- [ ] Logs không chứa token/password/card data.
- [ ] DLQ access hạn chế.
- [ ] Replay cần authorization.
- [ ] Chaos tooling chỉ chạy environment được phép.
- [ ] Synthetic test data.
- [ ] Audit operator replay/redrive.

---

## 40. Capstone demo script

### Demo 1: Happy path

1. Start Compose.
2. Check health.
3. Create order.
4. Show OrderCreated.
5. Show inventory reservation.
6. Show payment capture.
7. Show OrderConfirmed.
8. Show notification.
9. Show analytics record.
10. Open trace và dashboard.

### Demo 2: Duplicate

1. Publish duplicate OrderCreated.
2. Show Inbox conflict.
3. Show reservation count vẫn bằng một.
4. Show duplicate metric.

### Demo 3: Retry

1. Làm payment provider fail tạm thời.
2. Show retry attempts.
3. Recover provider.
4. Show PaymentCaptured.
5. Show backlog recover.

### Demo 4: Permanent error

1. Gửi schema invalid.
2. Show no business side effect.
3. Show DLT.
4. Triage metadata.
5. Fix/migrate.
6. Replay canary.

### Demo 5: Crash

1. Kill consumer trước ack.
2. Show redelivery.
3. Restart consumer.
4. Show idempotent result.

### Demo 6: Broker outage

1. Stop broker trong lab.
2. Show error/Outbox pending.
3. Start broker.
4. Show publication recovery.
5. Show no silent loss.

---

## 41. Definition of Done

Capstone được coi là hoàn thành khi:

### Architecture

- [ ] Có diagram.
- [ ] Có broker decision record.
- [ ] Có failure model.
- [ ] Có ordering scope.
- [ ] Có retention/replay policy.

### Contract

- [ ] Có event catalog.
- [ ] Có schema v1.
- [ ] Có versioning policy.
- [ ] Có compatibility test.
- [ ] Có privacy classification.

### Code

- [ ] Order API.
- [ ] Outbox.
- [ ] Publisher.
- [ ] Inventory worker.
- [ ] Payment worker.
- [ ] Notification worker.
- [ ] Analytics consumer.
- [ ] Inbox/idempotency.

### Reliability

- [ ] Retry bounded.
- [ ] Timeout.
- [ ] Backpressure.
- [ ] DLQ/DLT.
- [ ] Replay canary.
- [ ] Unknown outcome handling.
- [ ] Compensation.

### Testing

- [ ] Unit.
- [ ] Contract.
- [ ] Serialization.
- [ ] Integration.
- [ ] Testcontainers.
- [ ] Duplicate.
- [ ] Restart.
- [ ] Broker unavailable.
- [ ] Downstream timeout.
- [ ] Slow consumer.
- [ ] Hot partition.
- [ ] Chaos report.

### Operations

- [ ] Dashboard.
- [ ] Metrics.
- [ ] Structured logs.
- [ ] Trace propagation.
- [ ] Alerts.
- [ ] README.
- [ ] Docker Compose.
- [ ] Incident runbook.
- [ ] Security policy.

---

## 42. Bài tập lựa chọn kiến trúc

### Bài 1

Chọn RabbitMQ, Kafka hoặc SQS cho Order Processing Platform. Viết decision record một trang.

### Bài 2

Thiết kế cùng flow OrderCreated trên:

- RabbitMQ exchange/queue.
- Kafka topic/partition/group.
- SQS Standard hoặc FIFO.

So sánh delivery, ordering, retry, DLQ và replay.

### Bài 3

Nếu chọn Kafka, chọn partition key và phân tích hot key.

### Bài 4

Nếu chọn RabbitMQ, thiết kế exchange, bindings, retry queues và DLX.

### Bài 5

Nếu chọn SQS, chọn Standard/FIFO, visibility timeout, maxReceiveCount và MessageGroupId.

### Bài 6

Thiết kế event catalog cho sáu event chính.

### Bài 7

Viết schema OrderCreated v1/v2 và compatibility matrix.

### Bài 8

Thiết kế Outbox và Inbox schema.

### Bài 9

Viết retry/DLQ policy cho payment timeout, schema error và inventory rejection.

### Bài 10

Viết incident runbook cho duplicate payment.

---

## 43. Câu hỏi tự kiểm tra

1. Vì sao OrderCreated cần Outbox?
2. Vì sao consumer group cần tách giữa payment và analytics?
3. Khi nào chọn RabbitMQ thay Kafka?
4. Khi nào chọn Kafka thay SQS?
5. Khi nào SQS FIFO phù hợp?
6. Vì sao Standard Queue yêu cầu idempotent consumer?
7. Partition key orderId giải quyết điều gì?
8. Vì sao không được tuyên bố global ordering?
9. Outbox publisher có thể duplicate không?
10. Inbox bảo vệ side effect thế nào?
11. Payment timeout cần xử lý unknown outcome ra sao?
12. Khi nào inventory rejection là business result, không phải DLQ?
13. Vì sao retry schema error là sai?
14. Replay DLT cần những guardrail nào?
15. Dashboard cần metric business nào?
16. Khi nào choreography trở nên khó maintain?
17. Khi nào orchestration đáng dùng?
18. Test nào chứng minh không double charge?
19. Chaos experiment cần stop condition gì?
20. Capstone pass khi nào?

---

## 44. Checklist cuối lộ trình

### Kiến thức nền

- [ ] Phân biệt queue, topic, partition và consumer group.
- [ ] Hiểu at-most-once, at-least-once và exactly-once effect.
- [ ] Biết duplicate message là bình thường.
- [ ] Hiểu backpressure và failure model.

### Reliability

- [ ] Publisher confirm hoặc cơ chế xác nhận.
- [ ] Consumer ack/commit sau xử lý.
- [ ] Retry giới hạn.
- [ ] DLQ/DLT.
- [ ] Idempotency.
- [ ] Timeout.
- [ ] Outbox/Inbox.
- [ ] Compensation.

### Scale

- [ ] Chọn partition key.
- [ ] Tăng consumer không phá ordering.
- [ ] Chỉnh prefetch hoặc max poll records.
- [ ] Theo dõi queue depth/lag.
- [ ] Biết SQS long polling/batch.
- [ ] Biết giới hạn FIFO MessageGroupId.

### Spring Boot

- [ ] Spring AMQP hoặc Spring Kafka hoặc Spring Cloud AWS.
- [ ] Listener container.
- [ ] Error handler.
- [ ] Testcontainers.
- [ ] Correlation ID.
- [ ] Structured logging.
- [ ] Integration test.

### Production

- [ ] Schema/version policy.
- [ ] Dashboard.
- [ ] Replay runbook.
- [ ] Incident runbook.
- [ ] Security policy.
- [ ] IAM/KMS cho SQS nếu dùng.
- [ ] Không log payload nhạy cảm.
- [ ] Có owner và operational budget.

---

## 45. Kết luận

Capstone là bài kiểm tra khả năng nối toàn bộ kiến thức queue backend thành một hệ thống có thể giải thích, chạy và phục hồi.

Một thiết kế tốt không bắt đầu từ câu hỏi “broker nào phổ biến nhất”. Nó bắt đầu từ:

- Message có cần replay không?
- Có bao nhiêu consumer độc lập?
- Ordering cần ở phạm vi nào?
- Duplicate có chấp nhận được không?
- Downstream failure được xử lý ra sao?
- Ai vận hành broker?
- Cần managed service hay local control?
- Retention và DLQ được quản lý thế nào?
- Test có chứng minh failure model không?

Kiến trúc baseline:

    API
      -> local database transaction
      -> Outbox
      -> broker
      -> idempotent consumers
      -> local state and next Outbox
      -> retry/DLQ/replay
      -> metrics/logs/traces

Mục tiêu cuối cùng không phải hứa hẹn hệ thống không bao giờ lỗi. Mục tiêu là:

- Message không mất ngoài policy.
- Duplicate không tạo duplicate side effect.
- Retry không tạo storm.
- Lỗi vĩnh viễn được cô lập.
- Replay có kiểm soát.
- Incident có dashboard và runbook.
- Lựa chọn RabbitMQ, Kafka hoặc SQS có lý do kỹ thuật rõ ràng.

---

## 46. Tài liệu tham khảo

- RabbitMQ publisher confirms and acknowledgements: https://www.rabbitmq.com/docs/confirms
- RabbitMQ quorum queues: https://www.rabbitmq.com/docs/quorum-queues
- Apache Kafka documentation: https://kafka.apache.org/documentation/
- Spring AMQP reference: https://docs.spring.io/spring-amqp/reference/
- Spring Kafka reference: https://docs.spring.io/spring-kafka/reference/
- Spring Kafka testing: https://docs.spring.io/spring-kafka/reference/testing.html
- SQS Standard queues: https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/standard-queues.html
- SQS FIFO queues: https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-fifo-queues.html
- SQS visibility timeout: https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-visibility-timeout.html
- SQS dead-letter queues: https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-configure-dead-letter-queue.html


# Tuần 6 — Publish/Subscribe, routing, topics và RPC

> Tuần này tập trung vào việc chọn đúng kiểu giao tiếp cho từng use case: fanout cho pub/sub, direct cho routing chính xác, topic cho lọc theo pattern và request/reply cho RPC có timeout.

## 1. Kết quả cần đạt

Sau bài học, bạn có thể:

- Dựng publish/subscribe bằng fanout exchange.
- Route command hoặc log bằng direct exchange.
- Thiết kế topic routing key với wildcard * và #.
- Phân biệt event notification, command và request/reply.
- Viết RPC tính phí vận chuyển qua RabbitMQ.
- Dùng reply queue, replyTo, correlationId và timeout.
- Xử lý reply đến trễ, reply thiếu, lỗi downstream và duplicate request.
- Chọn exchange type cho ít nhất 10 use case.
- Giải thích vì sao RPC qua broker không thay thế mọi REST call.
- Tách queue theo subscriber thay vì dùng một shared queue cho mọi event.

## 2. Bốn kiểu giao tiếp

~~~text
Publish/subscribe
  producer -> fanout exchange -> nhiều queue -> nhiều subscriber

Direct routing
  producer -> direct exchange -> queue theo routing key chính xác

Topic routing
  producer -> topic exchange -> queue theo pattern

RPC request/reply
  requester -> request queue -> worker
  requester <- reply queue    <- worker
~~~

| Kiểu | Mục đích | Primitive |
|---|---|---|
| Pub/sub | Nhiều subscriber cùng cần event | Fanout + nhiều queue |
| Direct | Route đến responsibility cụ thể | Direct + exact key |
| Topic | Subscriber cần một nhóm event | Topic + wildcard |
| RPC | Requester cần một reply | Request queue + reply queue |

Không chọn exchange type chỉ vì tên quen thuộc. Bắt đầu từ semantics của message và cách consumer cần nhận nó.

## 3. Event, command và request/reply

Event mô tả việc đã xảy ra:

~~~text
OrderCreated
PaymentCaptured
UserRegistered
~~~

Producer không cần biết subscriber cụ thể nào sẽ phản ứng.

Command yêu cầu một owner thực hiện hành động:

~~~text
SendOrderConfirmationEmail
ReserveInventory
CapturePayment
~~~

Request/reply yêu cầu kết quả:

~~~text
CalculateShippingFee -> ShippingFeeCalculated
~~~

Một request/reply qua broker vẫn có caller chờ kết quả, nên cần timeout và xử lý retry.

| Nhu cầu | Lựa chọn |
|---|---|
| Nhiều service phản ứng với event | Pub/sub |
| Một worker sở hữu command | Direct hoặc topic hẹp |
| Lọc nhóm event | Topic |
| Công việc nền không cần kết quả ngay | Command/work queue |
| Cần kết quả nhanh | REST hoặc RPC |
| Cần buffer khi worker tạm bận | Message/RPC |
| Truy vấn đơn giản | Thường REST |

## 4. Domain lab

~~~text
OrderCreated
OrderPaid
OrderCancelled

log.info
log.warn
log.error

CalculateShippingFee
~~~

Topology mục tiêu:

~~~text
order.events (fanout)
  ├── audit.queue
  ├── notification.queue
  └── analytics.queue

application.logs (direct)
  ├── log.info    -> info.queue
  ├── log.warn    -> warn.queue
  └── log.error   -> error.queue

order.topics (topic)
  ├── order.created.eu -> eu.queue
  ├── order.created.vn -> vn.queue
  └── order.#          -> all-orders.queue

shipping.requests (direct)
  └── shipping.request -> shipping.worker.queue
~~~

## 5. Publish/subscribe với fanout

### 5.1 Mô hình

Fanout bỏ qua routing key và gửi message tới mọi queue đã bind:

~~~text
OrderCreated
      |
      v
order.events (fanout)
  ├── audit.queue        -> audit service
  ├── notification.queue -> notification service
  └── analytics.queue    -> analytics service
~~~

Mỗi queue có delivery riêng. Consumer cùng một queue thì cạnh tranh; queue khác nhau thì đều nhận event.

### 5.2 Khi nào dùng fanout?

Dùng fanout khi:

- mọi subscriber hiện tại đều cần event;
- producer không muốn biết danh sách subscriber;
- mỗi subscriber cần retry, scale và retention riêng;
- event là notification, không phải command có một owner duy nhất.

Fanout không phải event store. Subscriber mới không tự đọc lịch sử cũ nếu không có cơ chế lưu trữ riêng.

### 5.3 Java client

~~~java
channel.exchangeDeclare(
        "order.events",
        BuiltinExchangeType.FANOUT,
        true
);

channel.queueDeclare("audit.queue", true, false, false, null);
channel.queueDeclare("notification.queue", true, false, false, null);
channel.queueDeclare("analytics.queue", true, false, false, null);

channel.queueBind("audit.queue", "order.events", "");
channel.queueBind("notification.queue", "order.events", "");
channel.queueBind("analytics.queue", "order.events", "");
~~~

Publish:

~~~java
channel.basicPublish(
        "order.events",
        "",
        properties,
        orderCreatedJson
);
~~~

Nên vẫn đặt eventId, messageId và correlationId dù fanout bỏ qua routing key.

### 5.4 Spring AMQP

~~~java
@Bean
public FanoutExchange orderEventsExchange() {
    return new FanoutExchange(
            "order.events",
            true,
            false
    );
}

@Bean
public Queue auditQueue() {
    return QueueBuilder.durable("audit.queue").build();
}

@Bean
public Queue notificationQueue() {
    return QueueBuilder.durable("notification.queue").build();
}

@Bean
public Queue analyticsQueue() {
    return QueueBuilder.durable("analytics.queue").build();
}

@Bean
public Binding auditBinding(
        Queue auditQueue,
        FanoutExchange orderEventsExchange
) {
    return BindingBuilder
            .bind(auditQueue)
            .to(orderEventsExchange);
}
~~~

Tạo binding tương tự cho notification và analytics.

Consumer độc lập:

~~~java
@RabbitListener(queues = "audit.queue")
public void audit(OrderCreatedMessage event) {
    auditService.record(event);
}

@RabbitListener(queues = "notification.queue")
public void notify(OrderCreatedMessage event) {
    notificationService.send(event);
}

@RabbitListener(queues = "analytics.queue")
public void analyze(OrderCreatedMessage event) {
    analyticsService.index(event);
}
~~~

Nếu notification chậm, audit và analytics vẫn có queue riêng để tiếp tục xử lý.

## 6. Direct routing cho log

### 6.1 Mô hình

~~~text
application.logs (direct)
  ├── info.queue  <- log.info
  ├── warn.queue  <- log.warn
  └── error.queue <- log.error
~~~

~~~text
log.info  -> info.queue
log.warn  -> warn.queue
log.error -> error.queue
log.debug -> không route nếu chưa bind
~~~

### 6.2 Khi nào dùng direct?

Dùng direct khi:

- key có tập giá trị rõ;
- route đến responsibility cụ thể;
- không cần wildcard;
- muốn phát hiện key không hợp lệ.

Một message vẫn có thể đến nhiều queue nếu nhiều queue bind cùng key.

### 6.3 Java client

~~~java
channel.exchangeDeclare(
        "application.logs",
        BuiltinExchangeType.DIRECT,
        true
);

channel.queueDeclare("info.queue", true, false, false, null);
channel.queueDeclare("warn.queue", true, false, false, null);
channel.queueDeclare("error.queue", true, false, false, null);

channel.queueBind("info.queue", "application.logs", "log.info");
channel.queueBind("warn.queue", "application.logs", "log.warn");
channel.queueBind("error.queue", "application.logs", "log.error");
~~~

Publish helper:

~~~java
void publishLog(
        Channel channel,
        String level,
        String message
) throws IOException {
    String routingKey = "log." + level;

    channel.basicPublish(
            "application.logs",
            routingKey,
            null,
            message.getBytes(StandardCharsets.UTF_8)
    );
}
~~~

Level phải được validate từ tập cho phép. Không để input bất kỳ tạo routing key tùy ý.

### 6.4 Nhiều queue cùng key

~~~text
application.logs (direct)
  ├── error.queue <- log.error
  └── alert.queue <- log.error
~~~

Một log.error sẽ vào cả hai queue. Đây là cách tách retry và scale của audit/error alert.

## 7. Topic routing

### 7.1 Routing key và wildcard

Routing key gồm các từ ngăn cách bởi dấu chấm:

~~~text
order.created.eu
order.created.vn
order.paid.eu
inventory.reserved.vn
~~~

- * khớp đúng một từ;
- # khớp không từ nào hoặc nhiều từ.

Topology:

~~~text
order.topics (topic)
  ├── eu.orders.queue  <- order.*.eu
  ├── vn.orders.queue  <- order.*.vn
  ├── created.queue    <- order.created.#
  └── all-orders.queue <- order.#
~~~

Bảng dự đoán:

| Routing key | EU | VN | Created | All orders |
|---|---:|---:|---:|---:|
| order.created.eu | Có | Không | Có | Có |
| order.created.vn | Không | Có | Có | Có |
| order.paid.eu | Có | Không | Không | Có |
| order.paid.vn | Không | Có | Không | Có |
| inventory.reserved.vn | Không | Không | Không | Không |

### 7.2 Spring binding

~~~java
@Bean
public TopicExchange orderTopicsExchange() {
    return new TopicExchange(
            "order.topics",
            true,
            false
    );
}

@Bean
public Queue euOrdersQueue() {
    return QueueBuilder.durable("eu.orders.queue").build();
}

@Bean
public Queue vnOrdersQueue() {
    return QueueBuilder.durable("vn.orders.queue").build();
}

@Bean
public Binding euOrdersBinding(
        Queue euOrdersQueue,
        TopicExchange orderTopicsExchange
) {
    return BindingBuilder
            .bind(euOrdersQueue)
            .to(orderTopicsExchange)
            .with("order.*.eu");
}

@Bean
public Binding vnOrdersBinding(
        Queue vnOrdersQueue,
        TopicExchange orderTopicsExchange
) {
    return BindingBuilder
            .bind(vnOrdersQueue)
            .to(orderTopicsExchange)
            .with("order.*.vn");
}
~~~

### 7.3 Quy ước topic

Routing key cần:

- số segment ổn định;
- tên có nghĩa domain;
- wildcard có thể diễn tả subscriber;
- không nhồi metadata ngẫu nhiên;
- có tài liệu và test matching.

Ví dụ:

~~~text
domain.event.region
order.created.eu
order.paid.vn
~~~

Nếu thêm version, quyết định rõ vị trí:

~~~text
order.created.v1.eu
order.v1.created.eu
~~~

Không đổi vị trí segment tùy tiện vì binding cũ có thể không match.

## 8. Chọn exchange type cho 10 use case

| Use case | Lựa chọn | Lý do |
|---|---|---|
| Audit mọi OrderCreated | Fanout | Subscriber độc lập |
| Email khi OrderCreated | Fanout/topic | Tùy pattern |
| Payment capture command | Direct | Owner rõ |
| Log info/warn/error | Direct | Key cố định |
| Event theo region | Topic | Lọc theo segment |
| Analytics toàn bộ order event | Topic với order.# | Lọc prefix |
| Broadcast cache invalidation | Fanout | Nhiều subscriber |
| Thumbnail work pool | Direct/default | Một queue, nhiều worker |
| Shipping fee cần kết quả | RPC hoặc REST | Có reply/timeout |
| UserRegistered cho mọi service | Fanout | Nhiều subscriber |

Exchange type không tự giải quyết retry, ordering, idempotency hoặc schema evolution.

## 9. RPC qua RabbitMQ

### 9.1 Mô hình

~~~text
Requester -> request exchange/queue -> Worker
Requester <- reply queue            <- Worker
~~~

Request thường có:

- requestId;
- payload;
- replyTo;
- correlationId;
- timeout budget.

Reply có:

- correlationId;
- success hoặc error;
- result hoặc errorCode.

### 9.2 Khi nào dùng RPC?

RPC qua broker phù hợp khi:

- worker có thể xử lý lâu hơn một request thông thường;
- cần buffer request khi worker tạm bận;
- caller chấp nhận timeout;
- requester không cần biết địa chỉ trực tiếp của worker;
- retry và idempotency đã được thiết kế.

REST thường phù hợp hơn khi cần API synchronous đơn giản, response nhanh, HTTP status rõ và không cần broker durability.

Không biến mọi function call thành RPC message.

## 10. Reply queue và correlation ID

Requester có thể tạo queue riêng:

~~~text
exclusive = true
auto-delete = true
server-named queue
~~~

Các request concurrent cần map:

~~~text
req-1 -> future-1
req-2 -> future-2
req-3 -> future-3
~~~

Khi reply đến, lấy correlationId để hoàn tất đúng future. Không giả định reply đến cùng thứ tự request.

Correlation ID phải:

- unique trong scope reply queue;
- được worker copy nguyên vẹn vào reply;
- được xóa khỏi pending map sau success/error/timeout;
- được log nếu không match request;
- không dùng orderId làm correlationId nếu một order có nhiều request.

## 11. RPC tính phí vận chuyển

### 11.1 Contract

Request:

~~~json
{
  "requestId": "req-100",
  "orderId": "order-100",
  "from": "VN",
  "to": "EU",
  "weightKg": 2.5
}
~~~

Properties:

~~~text
replyTo       = reply queue của requester
correlationId = corr-100
messageId     = req-100
~~~

Reply success:

~~~json
{
  "requestId": "req-100",
  "correlationId": "corr-100",
  "success": true,
  "fee": 25.50,
  "currency": "USD"
}
~~~

Reply error:

~~~json
{
  "requestId": "req-100",
  "correlationId": "corr-100",
  "success": false,
  "errorCode": "RATE_PROVIDER_TIMEOUT"
}
~~~

Không đưa stack trace, credential hoặc dữ liệu nội bộ vào reply.

### 11.2 Requester Java client

~~~java
String replyQueue = channel.queueDeclare(
        "",
        false,
        true,
        true,
        null
).getQueue();

String correlationId = UUID.randomUUID().toString();
String requestId = UUID.randomUUID().toString();

AMQP.BasicProperties properties =
        new AMQP.BasicProperties.Builder()
                .correlationId(correlationId)
                .replyTo(replyQueue)
                .messageId(requestId)
                .contentType("application/json")
                .build();

channel.basicPublish(
        "shipping.requests",
        "shipping.request",
        properties,
        requestBody.getBytes(StandardCharsets.UTF_8)
);
~~~

Requester cần có consumer trên reply queue và pending map theo correlationId.

Reply consumer:

~~~java
DeliverCallback replyCallback = (consumerTag, delivery) -> {
    String received =
            delivery.getProperties().getCorrelationId();

    CompletableFuture<String> pendingRequest =
            pending.remove(received);

    if (pendingRequest != null) {
        String body = new String(
                delivery.getBody(),
                StandardCharsets.UTF_8
        );
        pendingRequest.complete(body);

        channel.basicAck(
                delivery.getEnvelope().getDeliveryTag(),
                false
        );
    } else {
        logLateOrUnknownReply(received);
        channel.basicReject(
                delivery.getEnvelope().getDeliveryTag(),
                false
        );
    }
};
~~~

### 11.3 Timeout

~~~java
CompletableFuture<String> reply =
        pendingRequest.orTimeout(
                2,
                TimeUnit.SECONDS
        );
~~~

Timeout có thể xảy ra dù worker vẫn đang xử lý. Retry sau timeout có thể tạo duplicate request.

### 11.4 Worker

~~~java
DeliverCallback requestCallback = (consumerTag, delivery) -> {
    AMQP.BasicProperties requestProperties =
            delivery.getProperties();

    String replyTo = requestProperties.getReplyTo();
    String correlationId =
            requestProperties.getCorrelationId();

    try {
        ShippingFee fee = calculateFee(delivery.getBody());

        AMQP.BasicProperties replyProperties =
                new AMQP.BasicProperties.Builder()
                        .correlationId(correlationId)
                        .contentType("application/json")
                        .build();

        channel.basicPublish(
                "",
                replyTo,
                replyProperties,
                successJson(fee)
                        .getBytes(StandardCharsets.UTF_8)
        );

        channel.basicAck(
                delivery.getEnvelope().getDeliveryTag(),
                false
        );
    } catch (TemporaryDownstreamException exception) {
        channel.basicNack(
                delivery.getEnvelope().getDeliveryTag(),
                false,
                true
        );
    } catch (Exception exception) {
        publishErrorReply(replyTo, correlationId, exception);
        channel.basicAck(
                delivery.getEnvelope().getDeliveryTag(),
                false
        );
    }
};
~~~

Nếu publish reply thành công nhưng worker crash trước ack request, request có thể redeliver và worker gửi reply lần hai. Requester phải xử lý duplicate reply hoặc worker phải idempotent theo requestId.

## 12. RPC với Spring RabbitTemplate

Conceptual flow:

~~~java
Object reply = rabbitTemplate.convertSendAndReceive(
        "shipping.requests",
        "shipping.request",
        request
);
~~~

Cần thiết kế:

- reply timeout;
- message converter;
- correlation data;
- error handling;
- request/reply topology;
- behavior khi worker không trả reply.

Cấu hình timeout:

~~~java
rabbitTemplate.setReplyTimeout(2_000);
~~~

Không coi reply null là success. Phân biệt:

~~~text
reply success
reply business error
timeout
converter error
connection error
unknown/late reply
~~~

Tên API chi tiết có thể thay đổi theo Spring AMQP version; kiểm tra version project trước khi copy.

## 13. Timeout, late reply và duplicate

Timeline:

~~~text
t0 requester gửi request
t1 worker bắt đầu xử lý
t2 requester timeout
t3 worker xử lý xong
t4 worker gửi reply
~~~

Ở t4 requester có thể đã xóa pending entry. Late reply phải được log, metric và ack/reject theo policy; không được làm crash reply consumer.

Retry sau timeout:

~~~text
request-1 -> worker xử lý
timeout
request-1 retry -> worker xử lý lần hai
~~~

Nếu RPC tạo side effect, cần idempotency key. Worker có thể lưu requestId -> result và trả lại result cũ khi request được gửi lại.

## 14. RPC không thay REST

REST phù hợp khi:

~~~text
GET /shipping-fee
  -> response nhanh
  -> caller cần kết quả ngay
  -> HTTP status/error contract rõ
~~~

RPC qua broker phù hợp hơn khi:

~~~text
request có thể chờ worker
  -> cần queue/buffer
  -> worker scale độc lập
  -> caller chấp nhận timeout
~~~

Trade-off RPC:

- khó trace nếu thiếu correlation ID;
- timeout không cho biết worker đã xử lý hay chưa;
- reply queue lifecycle phức tạp;
- retry có thể duplicate;
- response contract vẫn tạo coupling;
- debug cần kiểm tra request và reply queue.

## 15. Lab 1 — Audit, notification và analytics

Dựng:

~~~text
order.events (fanout)
  ├── audit.queue
  ├── notification.queue
  └── analytics.queue
~~~

Thực hiện:

1. Declare exchange và ba durable queue.
2. Bind cả ba queue.
3. Publish 10 OrderCreated.
4. Kiểm tra mỗi queue nhận đủ 10.
5. Chạy hai consumer trên audit.queue.
6. Xác nhận hai consumer audit cạnh tranh, không cùng nhận mọi message.
7. Dừng notification consumer.
8. Publish thêm event.
9. Xác nhận notification.queue giữ message trong khi queue khác vẫn xử lý.

## 16. Lab 2 — Routing log

Dựng direct exchange:

~~~text
application.logs (direct)
  ├── info.queue  <- log.info
  ├── warn.queue  <- log.warn
  └── error.queue <- log.error
~~~

Thực hiện:

1. Publish 10 message cho mỗi key.
2. Kiểm tra queue tương ứng.
3. Publish log.debug.
4. Xác nhận không có queue nhận.
5. Bind alert.queue vào log.error.
6. Publish error mới.
7. Xác nhận error.queue và alert.queue đều nhận message.

## 17. Lab 3 — Topic theo khu vực

Dựng:

~~~text
order.topics (topic)
  ├── eu.queue  <- order.*.eu
  ├── vn.queue  <- order.*.vn
  └── all.queue <- order.#
~~~

Publish:

~~~text
order.created.eu
order.created.vn
order.paid.eu
order.paid.vn
inventory.reserved.vn
~~~

Dự đoán counts trước khi xem UI. inventory.reserved.vn không match order.# vì prefix không phải order.

## 18. Lab 4 — RPC tính phí vận chuyển

Requester chờ tối đa 2 giây.

Thực hiện:

1. Tạo request exchange/queue.
2. Tạo reply queue exclusive, auto-delete.
3. Gửi request có requestId, replyTo và correlationId.
4. Worker trả reply thành công.
5. Cho worker xử lý lâu hơn timeout.
6. Ghi nhận timeout và late reply.
7. Cho downstream ném lỗi.
8. Trả error reply có errorCode.
9. Retry sau timeout.
10. Kiểm tra duplicate request/reply.
11. Thêm idempotency map theo requestId.
12. Chạy lại và so sánh.

| Kịch bản | Requester | Worker | Reply | Cần xử lý |
|---|---|---|---|---|
| Thành công | chờ | xong | success | trả fee |
| Worker chậm | timeout | xong muộn | late | log/ignore |
| Downstream lỗi | chờ | fail | error | hiển thị lỗi |
| Reply mất | timeout | có thể đã xong | không thấy | retry/idempotency |
| Request duplicate | retry | xử lý lại | duplicate | requestId cache |

## 19. Lab 5 — Chọn exchange type

Hoàn thành bảng:

| Use case | Exchange type | Queue | Routing rule | Lý do |
|---|---|---|---|---|
| Audit OrderCreated | | | | |
| Notification UserRegistered | | | | |
| Analytics order events | | | | |
| Payment capture command | | | | |
| Log info | | | | |
| Log error cho alert | | | | |
| Event theo EU | | | | |
| Event theo VN | | | | |
| Thumbnail work pool | | | | |
| Shipping fee có reply | | | | |

Không chỉ ghi type; ghi queue ownership, timeout hoặc ack semantics nếu liên quan.

## 20. Lỗi thiết kế thường gặp

### Shared queue cho mọi subscriber

~~~text
events -> shared.queue
  ├── audit consumer
  ├── notification consumer
  └── analytics consumer
~~~

Đây là competing consumers, không phải pub/sub. Mỗi message chỉ đến một consumer.

### Fanout cho command

Fanout có thể gửi command tới nhiều queue ngoài ý muốn. Command có owner rõ nên thường dùng direct hoặc topic hẹp.

### Topic key không có convention

Wildcard chỉ hữu ích khi segment ổn định. order.created.eu và created.order.eu là hai contract khác nhau.

### RPC không có timeout

Caller có thể chờ vô hạn khi worker chết hoặc reply mất.

### Retry RPC không có idempotency

Timeout không chứng minh worker chưa xử lý. Retry có thể gây duplicate side effect.

### Reply không có correlation ID

Requester không biết reply thuộc request nào khi có nhiều request concurrent.

## 21. Checklist

- [ ] Pub/sub dùng queue riêng cho từng subscriber.
- [ ] Direct key được định nghĩa và validate.
- [ ] Topic segment có convention ổn định.
- [ ] Không dùng # quá rộng ngoài audit.
- [ ] RPC có requestId và correlationId.
- [ ] RPC có replyTo hợp lệ.
- [ ] RPC có timeout.
- [ ] Late reply không làm crash consumer.
- [ ] Reply error có errorCode rõ.
- [ ] Retry RPC có idempotency.
- [ ] Không dùng RPC thay mọi REST call.
- [ ] Queue ownership được ghi trong topology.
- [ ] Event và command không bị lẫn semantics.
- [ ] Có metric timeout, late reply và unknown correlationId.

## 22. Deliverable cuối tuần

Tạo:

~~~text
rabbitmq/week-06/
├── README.md
├── diagrams/
│   ├── pub-sub.md
│   ├── routing-topic.md
│   └── rpc-sequence.md
└── src/
    ├── EventPublisher.java
    ├── LogRouter.java
    ├── ShippingRequester.java
    └── ShippingWorker.java
~~~

README cần có:

1. Sơ đồ fanout audit, notification và analytics.
2. Sơ đồ direct routing cho log.
3. Bảng topic matching EU/VN.
4. Ma trận exchange type cho 10 use case.
5. Contract request/reply tính phí vận chuyển.
6. Timeout và late-reply policy.
7. Correlation ID/requestId strategy.
8. Kết quả thử duplicate request.
9. Giải thích shared queue không phải pub/sub.
10. Quyết định trường hợp dùng REST thay RPC.

## 23. Tiêu chí tự kiểm tra

1. Fanout khác direct và topic ở luật route nào?
2. Vì sao pub/sub cần queue riêng cho từng subscriber?
3. Direct exchange có thể gửi một message đến nhiều queue không?
4. * và # trong topic exchange khác nhau thế nào?
5. Khi nào command nên dùng direct?
6. Event notification nên dùng fanout khi nào?
7. Reply queue dùng để làm gì?
8. Correlation ID nối request và reply thế nào?
9. Vì sao RPC cần timeout?
10. Late reply xử lý ra sao?
11. Timeout rồi retry có thể tạo duplicate thế nào?
12. RequestId và correlationId khác nhau thế nào?
13. Vì sao reply thiếu correlation ID nguy hiểm?
14. Khi nào REST tốt hơn RPC qua broker?
15. Vì sao shared queue không đáp ứng pub/sub?
16. Vì sao topic convention là một phần của contract?
17. Vì sao không dùng # tùy tiện?

### Đáp án cần nhớ

> Fanout broadcast tới các queue subscriber; direct route theo key chính xác; topic route theo pattern. Pub/sub cần queue riêng cho từng responsibility. RPC thêm request/reply, replyTo, correlationId và timeout, nhưng timeout không cho biết worker đã xử lý hay chưa. Vì vậy retry RPC phải idempotent, late reply phải an toàn, và REST vẫn phù hợp hơn cho nhiều request/response đơn giản.

## 24. Chuẩn bị cho Tuần 7

Tuần sau đi sâu vào reliability:

- consumer ack, nack và reject;
- requeue và vòng lặp lỗi;
- publisher confirms;
- mandatory publish và return callback;
- durable entity và persistent delivery mode;
- connection recovery không đồng nghĩa business success.

Giữ lại topology fanout/direct/topic và RPC worker của tuần này. Chúng sẽ là nền để kiểm tra message có thực sự được broker nhận, route và xử lý thành công hay chỉ mới rời khỏi producer.

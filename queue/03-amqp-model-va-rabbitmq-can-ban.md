# Tuần 3: AMQP model và RabbitMQ căn bản

## 1. Mục tiêu bài học

Sau bài học này, bạn có thể:

- Giải thích AMQP 0-9-1 model trong RabbitMQ.
- Phân biệt connection, channel và virtual host.
- Mô tả đường đi của message từ producer đến consumer.
- Phân biệt exchange, queue, binding và routing key.
- Dự đoán queue nào nhận message với direct, fanout và topic exchange.
- Thiết kế topology cho nhóm event order.
- Phân biệt durable queue với persistent message.
- Hiểu exclusive queue và auto-delete queue.
- Chạy lab RabbitMQ bằng Docker Compose và kiểm tra routing.
- Chuẩn bị nền tảng cho Spring AMQP ở Tuần 4.

Reliability nâng cao như publisher confirms, acknowledgement chi tiết, dead-letter exchange, TTL và retry sẽ học ở các tuần sau.

---

## 2. RabbitMQ giải quyết bài toán gì?

Giả sử Order Service tạo order. Sau đó hệ thống cần gửi email, ghi audit và cập nhật analytics.

Cách gọi trực tiếp:

    Order Service
       ├── HTTP -> Email Service
       ├── HTTP -> Audit Service
       └── HTTP -> Analytics Service

Order Service phụ thuộc vào trạng thái, latency, retry và khả năng chịu tải của từng downstream.

Với RabbitMQ:

    Order Service
        |
        | publish OrderCreated
        v
    RabbitMQ exchange
        ├── order.email queue      -> Email Worker
        ├── order.analytics queue  -> Analytics Worker
        └── order.audit queue      -> Audit Worker

Producer chỉ publish message vào exchange. Broker dựa trên binding để route message đến queue phù hợp.

RabbitMQ không tự biến hệ thống thành reliable. Vẫn phải quyết định message có được lưu bền không, consumer ack khi nào, xử lý crash ra sao, queue đầy xử lý thế nào và message không route được đi đâu.

---

## 3. AMQP là gì?

AMQP là Advanced Message Queuing Protocol. Trong RabbitMQ, model phổ biến khi học queue là AMQP 0-9-1.

Các khái niệm chính:

- Connection.
- Channel.
- Virtual host.
- Exchange.
- Queue.
- Binding.
- Routing key.
- Message properties.
- Consumer.
- Acknowledgement.

Message flow:

    Producer
       |
       | publish(exchange, routingKey, properties, body)
       v
    Exchange
       |
       | evaluate bindings
       v
    Queue
       |
       | deliver
       v
    Consumer

Producer thường publish vào exchange. Exchange dùng routing key và binding để route message. Queue lưu message chờ consumer.

Exchange không phải nơi consumer đọc message. Queue không tự quyết định routing.

---

## 4. Connection và channel

### 4.1. Connection

Connection là kết nối network giữa application và RabbitMQ broker.

Connection chứa:

- Host và port.
- Authentication.
- Virtual host.
- TLS configuration.
- Heartbeat.
- Network socket.

Không nên mở connection cho mỗi message.

Mô hình không tốt:

    Mỗi lần publish:
        mở connection
        mở channel
        publish
        đóng channel
        đóng connection

Mô hình tốt hơn:

    Application startup:
        mở connection
        tạo channel hoặc channel pool

    Runtime:
        tái sử dụng theo lifecycle phù hợp

    Shutdown:
        đóng channel
        đóng connection

Connection có thể lỗi do network partition, broker restart, authentication failure, TLS mismatch hoặc heartbeat timeout.

### 4.2. Channel

Channel là logical session nằm bên trong connection.

    TCP connection
       ├── channel 1: publisher
       ├── channel 2: email consumer
       ├── channel 3: audit consumer
       └── channel 4: topology declaration

Channel là nơi thực hiện:

- Declare exchange.
- Declare queue.
- Create binding.
- Publish.
- Consume.
- Ack, reject hoặc nack.

Channel không tồn tại độc lập. Khi connection đóng, channel cũng đóng.

Không nên chia sẻ một channel giữa nhiều thread nếu client library không đảm bảo thread safety. Tách channel theo publisher, consumer hoặc worker context dễ reasoning hơn.

### 4.3. So sánh

| Đặc điểm | Connection | Channel |
|---|---|---|
| Bản chất | Kết nối network | Logical session |
| Chi phí | Nặng hơn | Nhẹ hơn |
| Phạm vi | Host, auth, vhost, TCP | Publish, consume, topology |
| Khi đóng | Channel bên trong cũng đóng | Chỉ channel bị đóng |
| Khuyến nghị | Tái sử dụng | Tách theo vai trò phù hợp |

### 4.4. Channel error

Một thao tác không hợp lệ có thể làm channel bị đóng. Ví dụ:

- Declare cùng tên exchange nhưng khác type.
- Declare queue cùng tên nhưng khác thuộc tính.
- Consume queue không tồn tại.
- Gọi operation sai trạng thái.

Khi lỗi:

1. Log operation và entity name.
2. Bỏ channel lỗi.
3. Tạo channel mới nếu lỗi có thể phục hồi.
4. Không retry vô hạn một topology declaration sai.

---

## 5. Virtual host

Virtual host, thường viết là vhost, là namespace logic độc lập trong RabbitMQ.

Ví dụ:

    /
    /learning-dev
    /learning-test
    /learning-prod

Mỗi vhost chứa entity riêng:

- Exchange.
- Queue.
- Binding.
- Permission.
- Policy liên quan.

Queue order.events trong learning-dev khác queue cùng tên trong learning-prod.

### 5.1. Vì sao cần vhost?

Vhost giúp:

- Tách môi trường.
- Tách team hoặc tenant.
- Giảm nguy cơ dev gửi vào production.
- Gắn permission theo namespace.
- Giữ topology rõ ràng.

Vhost không thay thế toàn bộ security. Vẫn phải cấu hình user, configure permission, write permission, read permission, TLS và network access.

### 5.2. Connection tới vhost

Một connection được mở trong một vhost cụ thể. Mọi operation trên connection đó nằm trong vhost đã chọn.

Ví dụ URI:

    amqp://user:password@localhost:5672/learning-dev

Credentials phải nằm ở environment variable hoặc secret manager, không hardcode trong source.

---

## 6. Exchange, queue, binding và routing key

### 6.1. Exchange

Exchange là nơi producer publish message và là thành phần thực hiện routing.

Exchange có:

- Name.
- Type.
- Durable flag.
- Auto-delete flag.
- Arguments hoặc policy.
- Binding tới queue, stream hoặc exchange khác.

Ví dụ:

    order.events

### 6.2. Queue

Queue là nơi lưu message chờ consumer.

Queue có:

- Name.
- Durable flag.
- Exclusive flag.
- Auto-delete flag.
- Arguments.
- Consumer.
- Ready messages.
- Unacknowledged messages.

Ví dụ:

    order.email
    order.audit
    order.analytics

Một queue có thể có nhiều consumer cùng đọc. Đây là competing consumers: message trong queue thường chỉ giao cho một consumer trong nhóm.

### 6.3. Binding

Binding là relationship giữa exchange và queue.

Một binding gồm:

- Source exchange.
- Destination queue.
- Binding key hoặc pattern.
- Arguments tùy exchange type.

Ví dụ:

    order.events
        -- order.created --> order.email

Binding là rule của topology, không phải message.

### 6.4. Routing key

Routing key là chuỗi producer gửi cùng message. Exchange dùng nó để quyết định route.

Ví dụ:

    order.created
    order.paid
    order.cancelled

Routing key chỉ có ý nghĩa khi exchange có binding phù hợp.

### 6.5. Default exchange

RabbitMQ có default exchange đặc biệt với tên là chuỗi rỗng.

Khi queue được declare, RabbitMQ tự tạo binding từ default exchange tới queue với routing key bằng tên queue.

Ví dụ:

    queue name: order.email
    exchange: empty string
    routing key: order.email

Default exchange tiện cho point-to-point đơn giản. Không nên dùng nó thay custom exchange khi cần broadcast, topic pattern hoặc nhiều subscriber độc lập.

---

## 7. Direct exchange

Direct exchange route message khi routing key bằng chính xác binding key.

Topology:

    order.direct
       ├── order.email       binding: order.created
       ├── order.audit       binding: order.created
       └── order.payment     binding: order.paid

Routing:

| Routing key | Queue nhận |
|---|---|
| order.created | order.email, order.audit |
| order.paid | order.payment |
| order.cancelled | Không queue nào nếu chưa bind |

Direct phù hợp khi routing key có tập giá trị rõ ràng và cần exact match.

Direct không nhất thiết chỉ route tới một queue. Nếu nhiều queue cùng binding key, tất cả đều nhận message.

Nếu không có binding match, message không vào queue nào. Đây là một failure mode cần kiểm tra.

---

## 8. Fanout exchange

Fanout exchange gửi một bản message tới mọi queue đã bind và bỏ qua routing key.

Topology:

    order.events
       ├── order.email
       ├── order.analytics
       └── order.audit

Mọi queue nhận mọi message publish vào exchange.

Fanout phù hợp cho:

- Broadcast event.
- Audit và analytics cùng nhận toàn bộ event.
- Notification tới nhiều consumer độc lập.
- Flow không cần lọc theo event type.

Ưu điểm:

- Dễ hiểu.
- Thêm queue mới không cần đổi producer.
- Không mất route vì sai routing key.

Nhược điểm:

- Queue nhận mọi message.
- Consumer phải tự bỏ qua event không liên quan.
- Traffic và storage tăng.
- Không phù hợp khi queue chỉ cần một nhóm event nhỏ.

---

## 9. Topic exchange

Topic exchange route theo pattern của routing key. Routing key được tách bằng dấu chấm.

Ví dụ:

    order.created
    order.paid
    order.cancelled
    payment.failed
    inventory.reserved

Wildcard:

- Dấu sao match đúng một segment.
- Dấu thăng match zero hoặc nhiều segment.

Topology:

    order.topic
       ├── order.email       binding: order.*
       ├── order.audit       binding: #
       ├── payment.audit     binding: payment.#
       └── order.created     binding: order.created

Bảng routing:

| Binding pattern | Match | Không match |
|---|---|---|
| order.* | order.created, order.paid | order.payment.failed |
| order.# | order.created, order.payment.failed | payment.failed |
| # | Gần như mọi routing key | - |
| order.created | Chỉ order.created | order.paid |

Ví dụ order.* không match order.payment.failed vì dấu sao chỉ khớp một segment. Khi cần nhiều segment, dùng order.#.

Không nên dùng pattern # quá rộng nếu queue không thật sự cần toàn bộ traffic.

---

## 10. So sánh direct, fanout và topic

| Exchange | Routing rule | Khi dùng | Rủi ro chính |
|---|---|---|---|
| Direct | Exact match | Command hoặc event key rõ ràng | Sai key làm unroutable |
| Fanout | Mọi queue bind | Broadcast toàn bộ event | Queue nhận quá nhiều |
| Topic | Pattern wildcard | Subscription theo hierarchy | Pattern khó kiểm tra |

Heuristic:

- Một routing key cụ thể: direct.
- Mọi subscriber nhận mọi message: fanout.
- Subscriber cần lọc theo nhóm event: topic.

Hãy chọn dựa trên topology, traffic và failure model.

---

## 11. Durable, persistent và lifecycle

### 11.1. Durable exchange

Durable exchange được broker khôi phục sau restart.

### 11.2. Durable queue

Durable queue được broker khôi phục sau restart. Điều này không đồng nghĩa mọi message trong queue chắc chắn được lưu an toàn.

Durable queue nói về lifecycle của queue, không phải guarantee độc lập cho từng message.

### 11.3. Persistent message

Message có delivery mode persistent yêu cầu broker lưu bền hơn thay vì chỉ giữ trong memory.

Persistent message có ý nghĩa thực tế khi route vào durable queue và topology phù hợp.

### 11.4. Các yếu tố thường đi cùng

    durable exchange
    + durable queue
    + durable binding
    + persistent message

Bốn yếu tố này vẫn chưa bao phủ:

- Publish fail hoặc timeout.
- Broker chưa xác nhận đã nhận.
- Consumer xử lý xong nhưng chưa ack.
- Disk hoặc node gặp lỗi.
- Replication chưa được thiết kế.

Không được nói đơn giản rằng queue durable là message không mất.

### 11.5. Durable không phải retention

Durable không nói:

- Message giữ bao lâu.
- Message có TTL không.
- Queue có giới hạn dung lượng không.
- Có replay được không.
- Có audit history dài hạn không.

---

## 12. Exclusive queue và auto-delete queue

### 12.1. Exclusive queue

Exclusive queue chỉ được dùng bởi connection tạo ra nó. Khi connection đóng, queue thường bị xóa.

Phù hợp cho reply queue tạm thời, RPC client, subscription một instance hoặc debug.

Không phù hợp cho work queue production, queue nhiều consumer hoặc event subscription lâu dài.

### 12.2. Auto-delete queue

Auto-delete queue được broker xóa khi không còn consumer phù hợp theo semantics của RabbitMQ.

Phù hợp cho temporary subscription, dynamic queue, ephemeral notification và debug.

Nếu consumer disconnect, queue có thể biến mất; message gửi khi đó không được lưu cho consumer quay lại.

### 12.3. Business queue thường chọn gì?

Một queue xử lý order production thường bắt đầu với:

    durable = true
    exclusive = false
    autoDelete = false

Sau đó mới bổ sung TTL, max length, dead-letter, quorum hoặc classic profile và monitoring.

---

## 13. Topology Order Events

Roadmap yêu cầu:

    order.events exchange
    ├── order.email queue
    ├── order.analytics queue
    └── order.audit queue

Routing key:

    order.created
    order.paid
    order.cancelled

### 13.1. Topic profile

    exchange:
        order.events
        type: topic
        durable: true

    order.email:
        order.created
        order.paid
        order.cancelled

    order.analytics:
        order.#

    order.audit:
        #

Kết quả:

| Message | Email | Analytics | Audit |
|---|---:|---:|---:|
| order.created | Có | Có | Có |
| order.paid | Có | Có | Có |
| order.cancelled | Có | Có | Có |
| payment.failed | Không | Có | Có |
| inventory.reserved | Không | Có | Có |

Nếu audit chỉ cần event order, đổi binding # thành order.#.

### 13.2. Direct profile

    exchange:
        order.events.direct
        type: direct

    order.email:
        order.created
        order.paid
        order.cancelled

    order.analytics:
        order.created
        order.paid
        order.cancelled

    order.audit:
        order.created
        order.paid
        order.cancelled

Direct verbose hơn nhưng dễ kiểm tra. Topic linh hoạt hơn khi event taxonomy tăng.

### 13.3. Fanout profile

    exchange:
        order.events.broadcast
        type: fanout

    order.email: bind
    order.analytics: bind
    order.audit: bind

Cả ba queue nhận mọi message.

---

## 14. Message envelope cơ bản

Payload không nên chỉ là business object trần:

    {
        "orderId": "o-123",
        "customerId": "c-456"
    }

Nên có envelope:

    {
        "eventId": "evt-001",
        "eventType": "OrderCreated",
        "eventVersion": 1,
        "occurredAt": "2026-09-26T10:00:00Z",
        "producer": "order-service",
        "aggregateType": "Order",
        "aggregateId": "o-123",
        "correlationId": "req-789",
        "causationId": null,
        "payload": {
            "orderId": "o-123",
            "customerId": "c-456",
            "items": [
                {
                    "sku": "sku-1",
                    "quantity": 2
                }
            ]
        }
    }

Ý nghĩa:

- eventId: định danh duy nhất.
- eventType: loại event.
- eventVersion: version schema.
- occurredAt: thời điểm business event xảy ra.
- producer: service phát event.
- aggregateId: khóa ordering hoặc idempotency thường dùng.
- correlationId: nối các message trong workflow.
- causationId: event trước gây ra event hiện tại.
- payload: dữ liệu business.

Một số transport metadata có thể đặt trong properties hoặc headers:

    contentType = application/json
    messageId = evt-001
    type = OrderCreated
    correlationId = req-789

Đừng phụ thuộc duy nhất vào header để giữ business contract. Envelope trong body dễ lưu trữ và kiểm tra hơn.

---

## 15. Declare topology idempotent

Khi startup:

- Entity chưa tồn tại thì tạo mới.
- Entity đã tồn tại cùng thuộc tính thì thành công.
- Entity đã tồn tại khác thuộc tính thì fail rõ ràng.

Ví dụ không được đổi type in-place:

    declare order.events as topic
    later declare order.events as direct

RabbitMQ có thể đóng channel vì precondition failed.

Quy tắc:

- Đặt tên entity ổn định.
- Centralize topology constants.
- Version topology khi có breaking change.
- Không đổi exchange type tùy tiện.
- Tạo exchange mới nếu migration cần đổi semantics.
- Test startup với broker thật.
- Log topology version khi application start.

Có thể tổ chức constants:

    RabbitTopology
        EXCHANGE_ORDER_EVENTS
        QUEUE_ORDER_EMAIL
        QUEUE_ORDER_AUDIT
        ROUTING_ORDER_CREATED
        ROUTING_ORDER_PAID
        ROUTING_ORDER_CANCELLED

Không rải string literal ở nhiều publisher và listener.

---

## 16. Lab 1: Docker Compose

Tạo file docker-compose.rabbitmq.yml:

    services:
      rabbitmq:
        image: rabbitmq:4-management
        container_name: learning-rabbitmq
        ports:
          - "5672:5672"
          - "15672:15672"
        environment:
          RABBITMQ_DEFAULT_USER: learning
          RABBITMQ_DEFAULT_PASS: learning-pass
        volumes:
          - rabbitmq-data:/var/lib/rabbitmq

    volumes:
      rabbitmq-data:

Khởi động:

    docker compose -f docker-compose.rabbitmq.yml up -d

Kiểm tra:

    docker compose -f docker-compose.rabbitmq.yml ps

Management UI:

    http://localhost:15672

Connection:

    amqp://learning:learning-pass@localhost:5672/

Trong môi trường thật:

- Không dùng password mẫu.
- Dùng secret manager.
- Bật TLS khi cần.
- Tách vhost theo environment.
- Giới hạn network access.
- Không expose management UI công khai.

---

## 17. Lab 2: Tạo topology bằng Management UI

Trong UI:

1. Tạo exchange order.events.
2. Chọn type topic.
3. Chọn durable.
4. Tạo queue order.email.
5. Tạo queue order.analytics.
6. Tạo queue order.audit.
7. Tạo bindings:
   - order.email với order.created.
   - order.email với order.paid.
   - order.email với order.cancelled.
   - order.analytics với order.#.
   - order.audit với #.
8. Publish từng routing key.
9. Kiểm tra message count.
10. Consume message thủ công.

Dự đoán:

| Routing key | order.email | order.analytics | order.audit |
|---|---:|---:|---:|
| order.created | Có | Có | Có |
| order.paid | Có | Có | Có |
| order.cancelled | Có | Có | Có |
| payment.failed | Không | Có | Có |
| inventory.reserved | Không | Có | Có |

Hãy viết dự đoán trước rồi mới kiểm tra UI.

---

## 18. Lab 3: Java AMQP publisher

Dependency Maven:

    <dependency>
        <groupId>com.rabbitmq</groupId>
        <artifactId>amqp-client</artifactId>
        <version>5.x.x</version>
    </dependency>

Ví dụ:

    import com.rabbitmq.client.Channel;
    import com.rabbitmq.client.Connection;
    import com.rabbitmq.client.ConnectionFactory;
    import java.nio.charset.StandardCharsets;

    public final class OrderPublisher {
        private static final String EXCHANGE = "order.events";

        public static void main(String[] args) throws Exception {
            ConnectionFactory factory = new ConnectionFactory();
            factory.setHost("localhost");
            factory.setPort(5672);
            factory.setUsername("learning");
            factory.setPassword("learning-pass");
            factory.setVirtualHost("/");

            try (Connection connection = factory.newConnection();
                 Channel channel = connection.createChannel()) {

                channel.exchangeDeclare(EXCHANGE, "topic", true);

                String body =
                    "{\"eventId\":\"evt-1\","
                    + "\"eventType\":\"OrderCreated\","
                    + "\"eventVersion\":1,"
                    + "\"orderId\":\"order-1\"}";

                channel.basicPublish(
                    EXCHANGE,
                    "order.created",
                    null,
                    body.getBytes(StandardCharsets.UTF_8)
                );
            }
        }
    }

Điểm cần quan sát:

- exchangeDeclare phải dùng cùng name, type và durable với topology.
- basicPublish chọn exchange và routing key.
- Producer không chọn queue order.email.
- Ví dụ chưa có publisher confirm.

---

## 19. Lab 4: Java AMQP consumer

Ví dụ:

    import com.rabbitmq.client.Channel;
    import com.rabbitmq.client.Connection;
    import com.rabbitmq.client.ConnectionFactory;
    import com.rabbitmq.client.DeliverCallback;
    import java.nio.charset.StandardCharsets;

    public final class OrderEmailConsumer {
        private static final String EXCHANGE = "order.events";
        private static final String QUEUE = "order.email";

        public static void main(String[] args) throws Exception {
            ConnectionFactory factory = new ConnectionFactory();
            factory.setHost("localhost");
            factory.setPort(5672);
            factory.setUsername("learning");
            factory.setPassword("learning-pass");

            Connection connection = factory.newConnection();
            Channel channel = connection.createChannel();

            channel.exchangeDeclare(EXCHANGE, "topic", true);
            channel.queueDeclare(QUEUE, true, false, false, null);
            channel.queueBind(QUEUE, EXCHANGE, "order.created");
            channel.queueBind(QUEUE, EXCHANGE, "order.paid");
            channel.queueBind(QUEUE, EXCHANGE, "order.cancelled");

            DeliverCallback callback = (consumerTag, delivery) -> {
                String body = new String(
                    delivery.getBody(),
                    StandardCharsets.UTF_8
                );

                System.out.println("Received: " + body);
            };

            channel.basicConsume(QUEUE, true, callback, consumerTag -> {});
        }
    }

Ví dụ dùng auto acknowledgement để làm quen flow. Không copy nguyên trạng cho production vì message có thể được coi là đã xử lý trước khi business side effect hoàn tất.

---

## 20. Lab 5: Dự đoán routing

Cho topology:

    exchange: events
    type: topic

    queue: q1
    binding: order.*

    queue: q2
    binding: order.#

    queue: q3
    binding: #

    queue: q4
    binding: order.created

Bảng cần điền:

| Routing key | q1 | q2 | q3 | q4 |
|---|---:|---:|---:|---:|
| order.created | ? | ? | ? | ? |
| order.paid | ? | ? | ? | ? |
| order.payment.failed | ? | ? | ? | ? |
| payment.failed | ? | ? | ? | ? |
| order | ? | ? | ? | ? |

Đáp án:

| Routing key | q1 | q2 | q3 | q4 |
|---|---:|---:|---:|---:|
| order.created | Có | Có | Có | Có |
| order.paid | Có | Có | Có | Không |
| order.payment.failed | Không | Có | Có | Không |
| payment.failed | Không | Không | Có | Không |
| order | Không | Có | Có | Không |

---

## 21. Các mô hình consumer

### 21.1. Một queue, một consumer

    order.email queue -> Email Worker

Đơn giản nhưng throughput phụ thuộc một worker.

### 21.2. Một queue, nhiều consumer cạnh tranh

    order.email queue
       ├── Email Worker 1
       ├── Email Worker 2
       └── Email Worker 3

Consumer chia sẻ workload. Một message trong queue thường chỉ giao cho một consumer trong nhóm.

### 21.3. Nhiều queue, mỗi queue một flow

    order.events exchange
       ├── order.email queue      -> Email Worker
       ├── order.audit queue      -> Audit Worker
       └── order.analytics queue  -> Analytics Worker

Mỗi flow nhận bản message riêng.

### 21.4. Sai lầm dùng chung queue cho broadcast

Nếu Email Service và Audit Service cùng đọc một queue, chúng cạnh tranh. Muốn cả hai nhận message, mỗi service cần queue riêng bind vào exchange.

---

## 22. Queue lifecycle và queue depth

Nếu queue durable và message phù hợp durability, message có thể chờ khi consumer tạm dừng. Khi consumer quay lại, nó tiếp tục đọc.

Queue depth thường gồm:

- Ready messages.
- Unacknowledged messages.
- Total messages tùy metric.

Queue depth tăng có thể do:

- Producer nhanh hơn consumer.
- Consumer lỗi.
- Downstream chậm.
- Prefetch quá lớn.
- Queue không có consumer.
- Routing gửi nhầm.
- Broker resource pressure.

Queue depth chưa cho biết processing latency. Cần thêm message age, processing latency và failure rate.

Queue thường là nơi chờ xử lý, không phải event log dài hạn. Nếu cần replay dài hạn, nhiều consumer group hoặc seek theo offset, cân nhắc Kafka hoặc event store.

---

## 23. Common mistakes

### Mistake 1: Publish nhầm exchange

Producer publish vào exchange khác exchange queue bind. Cách giảm lỗi là centralize constants, integration test và log exchange/routing key.

### Mistake 2: Sai exchange type

Exchange đã là topic nhưng code declare direct. Không đổi type in-place; tạo exchange mới và migrate có kế hoạch.

### Mistake 3: Nghĩ routing key tự tạo queue

Publish order.created không tự tạo queue. Queue phải tồn tại và được bind.

### Mistake 4: Dùng fanout nhưng kỳ vọng lọc event

Fanout bỏ qua routing key. Queue nhận mọi message.

### Mistake 5: Dùng wildcard một segment cho nhiều segment

order.* không match order.payment.failed. Cần order.# nếu thực sự cần.

### Mistake 6: Chỉ bật durable queue

Durable queue không biến publish thành confirmed publish và không đảm bảo consumer processing.

### Mistake 7: Dùng auto-delete cho business queue

Consumer restart có thể làm queue biến mất. Auto-delete phù hợp flow tạm thời.

### Mistake 8: Một queue cho subscriber độc lập

Subscriber sẽ cạnh tranh. Broadcast cần queue riêng.

### Mistake 9: Hardcode credentials

Dùng environment variable cho lab và secret manager cho production.

### Mistake 10: Không kiểm tra unroutable message

Message không match binding có thể không tới queue nào. Cần test và policy riêng.

---

## 24. Naming convention

Exchange:

    <domain>.<purpose>

Ví dụ:

    order.events
    payment.commands
    inventory.events

Queue:

    <consumer-purpose>.<flow>

Ví dụ:

    order.email
    order.audit
    order.analytics
    payment.capture

Routing key:

    <aggregate>.<event>

Ví dụ:

    order.created
    order.paid
    order.cancelled

Chọn convention nhất quán. Không trộn orderCreated, order.created và order.created.v1 nếu không có lý do rõ ràng.

---

## 25. Bài tập thực hành

### Bài 1: Xây topology order event

Tạo exchange order.events kiểu topic và durable. Tạo ba queue:

    order.email
    order.audit
    order.analytics

Bindings:

- Email nhận order.created, order.paid, order.cancelled.
- Audit nhận order.#.
- Analytics nhận #.

Publish năm message và ghi queue nhận từng message.

### Bài 2: Đổi topic sang direct

Tạo exchange order.events.direct kiểu direct. Bind queue bằng exact key.

So sánh số binding, khả năng thêm event type, khả năng kiểm tra routing, rủi ro typo và mức độ linh hoạt.

### Bài 3: Chứng minh exchange khác queue

1. Tạo exchange không có queue binding.
2. Publish message.
3. Quan sát queue không có message.
4. Tạo queue nhưng chưa bind.
5. Publish lại.
6. Bind queue.
7. Publish lần nữa.
8. So sánh kết quả.

### Bài 4: Chứng minh competing consumers

1. Tạo queue work.items.
2. Bind queue vào direct exchange.
3. Chạy hai consumer cùng đọc queue.
4. Publish 20 message.
5. Quan sát mỗi consumer nhận một phần.
6. Dừng một consumer.
7. Publish tiếp.
8. Quan sát consumer còn lại nhận workload.

### Bài 5: Chứng minh pub/sub

1. Tạo exchange broadcast.events kiểu fanout.
2. Tạo hai queue riêng.
3. Bind cả hai queue.
4. Publish 10 message.
5. Kiểm tra cả hai queue đều có 10 message.

Sau đó cho hai service dùng chung một queue và giải thích kết quả thay đổi.

### Bài 6: Phân tích lifecycle

Tạo ba queue:

- Durable, non-exclusive, non-auto-delete.
- Non-durable, non-exclusive, auto-delete.
- Exclusive, auto-delete.

Dừng và khởi động lại consumer/connection. Ghi nhận queue và message còn hay mất, rồi chọn profile cho từng use case.

### Bài 7: Routing matrix

Cho:

    q-email: order.*
    q-audit: order.#
    q-all: #

Tạo ít nhất 12 routing key gồm một, hai và ba segment, domain khác và từ khóa order. Viết expected result trước khi publish.

---

## 26. Câu hỏi tự kiểm tra

1. Producer publish vào exchange hay queue?
2. Queue có tự route message không?
3. Binding là gì?
4. Routing key khác binding key thế nào?
5. Direct exchange match ra sao?
6. Fanout có dùng routing key không?
7. order.* match order.created và order.payment.failed thế nào?
8. order.# match order không?
9. Default exchange có tên gì?
10. Vì sao queue name có thể dùng làm routing key với default exchange?
11. Connection và channel khác nhau ở đâu?
12. Một connection có thể có nhiều channel không?
13. Vhost cô lập những gì?
14. Durable queue có đồng nghĩa persistent message không?
15. Persistent message có đủ đảm bảo publish không mất không?
16. Khi nào dùng exclusive queue?
17. Khi nào dùng auto-delete queue?
18. Vì sao subscriber broadcast cần queue riêng?
19. Vì sao nhiều consumer cùng đọc một queue tạo competing consumers?
20. Message không match binding có thể xảy ra chuyện gì?
21. Vì sao không nên đổi exchange type đang tồn tại?
22. Vì sao queue depth tăng chưa đủ kết luận consumer lỗi?
23. Khi nào dùng direct thay topic?
24. Khi nào fanout đơn giản hơn topic?
25. Vì sao không hardcode RabbitMQ credentials?

---

## 27. Tiêu chí hoàn thành Tuần 3

- [ ] Vẽ flow producer -> exchange -> binding -> queue -> consumer.
- [ ] Phân biệt connection và channel.
- [ ] Giải thích vhost và permission boundary cơ bản.
- [ ] Declare exchange, queue và binding.
- [ ] Chọn direct, fanout hoặc topic cho use case.
- [ ] Dự đoán queue nhận message theo routing key.
- [ ] Giải thích wildcard sao và thăng.
- [ ] Mô tả default exchange.
- [ ] Phân biệt durable exchange, durable queue và persistent message.
- [ ] Giải thích exclusive và auto-delete queue.
- [ ] Tạo topology order.events.
- [ ] Chạy RabbitMQ bằng Docker Compose.
- [ ] Publish và consume message bằng UI hoặc client.
- [ ] Chứng minh competing consumers.
- [ ] Chứng minh pub/sub bằng nhiều queue.
- [ ] Viết routing matrix.
- [ ] Nêu ít nhất năm lỗi topology.
- [ ] Không hardcode secret.
- [ ] Có README ghi topology và cách chạy lab.

---

## 28. Chuẩn bị cho Tuần 4

Tuần 4 đưa topology vào Spring AMQP:

- RabbitTemplate.
- RabbitListener.
- Queue, exchange và binding bean.
- JSON message converter.
- Listener container.
- Automatic và manual acknowledgement.
- Prefetch.
- Consumer concurrency.
- Graceful shutdown.

Flow cần nhớ:

    RabbitTemplate / AMQP client
        -> exchange
        -> binding
        -> queue
        -> listener container
        -> business handler

Spring làm việc với các thành phần này thuận tiện hơn nhưng không thay đổi bản chất routing của RabbitMQ.

---

## 29. Tóm tắt

- Producer thường publish vào exchange.
- Exchange route message tới queue qua binding.
- Queue lưu message chờ consumer.
- Routing key được exchange dùng để quyết định route.
- Direct là exact match.
- Fanout broadcast tới mọi queue bind.
- Topic dùng pattern wildcard.
- Connection là network connection; channel là logical session.
- Vhost là namespace và permission boundary.
- Durable queue nói về lifecycle queue, không tự đảm bảo delivery.
- Persistent message cần đi cùng topology phù hợp.
- Exclusive và auto-delete phù hợp cho queue tạm thời.
- Broadcast cần queue riêng cho từng subscriber.
- Work queue dùng nhiều consumer cùng đọc một queue.
- Topology phải được declare nhất quán và kiểm thử bằng broker thật.

---

## Tài liệu tham khảo

- RabbitMQ AMQP 0-9-1 Model Explained: https://www.rabbitmq.com/tutorials/amqp-concepts
- RabbitMQ Exchanges: https://www.rabbitmq.com/docs/exchanges
- RabbitMQ Tutorials: https://www.rabbitmq.com/tutorials
- RabbitMQ Java Client API: https://rabbitmq.github.io/rabbitmq-java-client/api/current/
- Spring AMQP Reference: https://docs.spring.io/spring-amqp/reference/
- AMQP 0-9-1 Specification: https://www.rabbitmq.com/resources/specs/amqp0-9-1.pdf


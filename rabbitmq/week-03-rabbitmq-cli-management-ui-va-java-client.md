# Tuần 3 — RabbitMQ CLI, Management UI và Java client

> Mục tiêu của bài này là biến topology thành thứ có thể quan sát và kiểm tra. Bạn sẽ biết message đang nằm ở queue nào, consumer nào đang đọc, channel nào đang mở, và dùng Java client để tạo topology, publish, consume rồi quan sát redelivery khi consumer bị dừng.

## 1. Kết quả cần đạt

Sau bài học, bạn có thể:

- Đọc Overview, Connections, Channels, Exchanges, Queues, Consumers và Bindings trong Management UI.
- Phân biệt connection count, channel count, queue depth, ready, unacked và consumer count.
- Dùng rabbitmqctl, rabbitmq-diagnostics và rabbitmq-plugins ở mức vận hành cơ bản.
- Publish/consume từ UI hoặc CLI để cô lập lỗi broker khỏi lỗi ứng dụng.
- Viết Java producer dùng ConnectionFactory, Connection và Channel.
- Declare exchange, queue, binding; publish 100 message có routing key khác nhau.
- Viết consumer log delivery tag, redelivered flag và body.
- Quan sát ready/unacked khi consumer chậm hoặc bị dừng.
- Cấu hình heartbeat, connection timeout, automatic recovery và topology recovery.
- Hiểu vì sao không mở connection mới cho từng message.

## 2. Quy trình debug trước khi sửa code

Khi message không tới consumer, tách lỗi thành bốn lớp:

~~~text
1. Broker sống không?
2. Topology route đúng không?
3. Publish có thành công không?
4. Consumer có nhận và xử lý không?
~~~

Đường đi cần kiểm tra:

~~~text
Producer
  -> connection
  -> vhost
  -> channel
  -> exchange
  -> binding + routing key
  -> queue
  -> consumer
  -> acknowledgement
~~~

Nếu publish từ Management UI vào queue được nhưng Java producer không được, ưu tiên kiểm tra connection, vhost, permission, exchange và routing key trong Java. Nếu Java publish thành công nhưng consumer không nhận, kiểm tra queue, binding, consumer lifecycle và acknowledgement.

## 3. Chuẩn bị RabbitMQ local

Dùng RabbitMQ theo roadmap:

~~~yaml
services:
  rabbitmq:
    image: rabbitmq:4-management
    hostname: rabbitmq
    ports:
      - "5672:5672"
      - "15672:15672"
    environment:
      RABBITMQ_DEFAULT_USER: dev
      RABBITMQ_DEFAULT_PASS: dev-password
      RABBITMQ_DEFAULT_VHOST: /app
    volumes:
      - rabbitmq-data:/var/lib/rabbitmq

volumes:
  rabbitmq-data:
~~~

Khởi động và kiểm tra:

~~~bash
docker compose up -d
docker compose ps
docker compose exec rabbitmq rabbitmq-diagnostics status
~~~

Đăng nhập Management UI tại địa chỉ localhost:15672 bằng:

~~~text
username: dev
password: dev-password
vhost: /app
AMQP: localhost:5672
~~~

Trong production, không dùng credential demo, không expose Management UI công khai và nên pin image theo version cụ thể.

## 4. Management UI

### 4.1 Overview

Overview cho biết:

- số connection;
- số channel;
- số exchange;
- số queue;
- số consumer;
- message publish/deliver rate;
- node health;
- memory/disk alarm;
- ready và unacked ở mức tổng hợp.

Message rate là tốc độ tại một thời điểm. Queue depth và oldest message age mới giúp thấy backlog kéo dài.

### 4.2 Connections

Connections là các kết nối TCP tới broker. Hãy xem:

- client name;
- user và vhost;
- host/IP;
- số channel;
- state;
- heartbeat;
- send/receive rate;
- connection duration.

Câu hỏi debug:

- Service đang vào đúng vhost chưa?
- Có connection bất thường tăng liên tục không?
- Có request nào đang mở connection mới cho từng message không?
- Connection có bị đóng vì heartbeat không?

Một service thông thường tái sử dụng một số lượng connection nhỏ trong suốt lifecycle.

### 4.3 Channels

Channel là kênh logic bên trong connection. UI cho biết channel number, connection liên quan, consumer count và rates.

Ví dụ:

~~~text
connections = 2
channels    = 5
consumers   = 3
~~~

Không thể suy ra có ba instance service chỉ từ consumer count. Một process có thể tạo nhiều consumer; nhiều process có thể đọc cùng một queue.

### 4.4 Exchanges

Exchange detail giúp kiểm tra:

- tên;
- type;
- durable;
- auto-delete;
- internal;
- bindings;
- publish rate.

Nếu producer publish vào nhầm exchange hoặc nhầm type, code có thể không route như dự kiến.

### 4.5 Queues and Streams

Các field cần nhớ:

~~~text
ready         = message đang chờ consumer
unacked       = consumer đã nhận nhưng chưa ack
total         = ready + unacked
consumers     = số consumer đang subscribe
~~~

Diễn giải:

~~~text
ready tăng    = producer nhanh hơn consumer hoặc consumer không đọc
unacked tăng  = consumer xử lý chậm hoặc chưa ack
consumers=0   = queue không có consumer
~~~

Ready bằng 0 không có nghĩa công việc đã hoàn tất. Message có thể đang unacked hoặc đã bị ack quá sớm.

### 4.6 Bindings

Bindings trả lời message đi từ exchange nào đến queue nào bằng rule nào. Khi debug, đọc theo thứ tự:

~~~text
exchange
  -> type
  -> destination queue
  -> routing key/pattern
  -> arguments
~~~

Một topic binding # có thể khiến queue nhận mọi routing key. Một binding thiếu dấu chấm có thể khiến queue không nhận message.

### 4.7 Get messages

Get messages hữu ích trong lab nhưng có thể thay đổi trạng thái queue. Tùy ack/requeue option, message có thể bị ack, requeue hoặc bị lấy khỏi queue.

Không tùy tiện bấm Get messages trên queue production. Để quan sát không làm thay đổi hệ thống, ưu tiên metrics, logs hoặc một consumer debug riêng.

## 5. RabbitMQ CLI

### 5.1 rabbitmqctl

Kiểm tra node và vhost:

~~~bash
docker compose exec rabbitmq rabbitmqctl status
docker compose exec rabbitmq rabbitmqctl list_vhosts
~~~

User và permission:

~~~bash
docker compose exec rabbitmq rabbitmqctl list_users
docker compose exec rabbitmq rabbitmqctl list_permissions -p /app
~~~

Permission gồm:

~~~text
configure = được tạo/thay đổi entity nào
write     = được publish vào exchange nào
read      = được consume queue nào
~~~

Liệt kê topology:

~~~bash
docker compose exec rabbitmq rabbitmqctl list_exchanges name type durable auto_delete
docker compose exec rabbitmq rabbitmqctl list_queues name durable auto_delete consumers messages_ready messages_unacknowledged
docker compose exec rabbitmq rabbitmqctl list_bindings source destination_type destination routing_key
~~~

Liệt kê connection và channel:

~~~bash
docker compose exec rabbitmq rabbitmqctl list_connections name user vhost state channels
docker compose exec rabbitmq rabbitmqctl list_channels connection number user vhost consumer_count
~~~

Nếu output field khác giữa các bản RabbitMQ, xem trợ giúp:

~~~bash
docker compose exec rabbitmq rabbitmqctl help list_queues
docker compose exec rabbitmq rabbitmqctl help list_bindings
~~~

### 5.2 rabbitmq-diagnostics

Kiểm tra node:

~~~bash
docker compose exec rabbitmq rabbitmq-diagnostics status
docker compose exec rabbitmq rabbitmq-diagnostics check_running
docker compose exec rabbitmq rabbitmq-diagnostics alarms
docker compose exec rabbitmq rabbitmq-diagnostics check_local_alarms
docker compose exec rabbitmq rabbitmq-diagnostics listeners
docker compose exec rabbitmq rabbitmq-diagnostics version
~~~

Memory hoặc disk alarm có thể làm broker hạn chế publish. Luôn kiểm tra health trước khi kết luận producer hỏng.

### 5.3 rabbitmq-plugins

Liệt kê plugin:

~~~bash
docker compose exec rabbitmq rabbitmq-plugins list
docker compose exec rabbitmq rabbitmq-plugins list -e
~~~

Management image thường đã bật management plugin. Chỉ enable plugin khi hiểu tác động:

~~~bash
docker compose exec rabbitmq rabbitmq-plugins enable rabbitmq_management
~~~

## 6. Java RabbitMQ client

### 6.1 Dependency

Với Maven, thêm Java client. Version nên đặt ở dependency management/property trung tâm của project:

~~~xml
<dependency>
    <groupId>com.rabbitmq</groupId>
    <artifactId>amqp-client</artifactId>
    <version>RABBITMQ_CLIENT_VERSION</version>
</dependency>
~~~

Không copy một version khác nhau vào nhiều module.

### 6.2 ConnectionFactory

ConnectionFactory lưu cấu hình và tạo connection:

~~~java
ConnectionFactory factory = new ConnectionFactory();
factory.setHost("localhost");
factory.setPort(5672);
factory.setUsername("dev");
factory.setPassword("dev-password");
factory.setVirtualHost("/app");
factory.setConnectionTimeout(5_000);
factory.setRequestedHeartbeat(30);
factory.setAutomaticRecoveryEnabled(true);
factory.setTopologyRecoveryEnabled(true);
~~~

Production phải lấy credential từ environment hoặc secret manager:

~~~java
factory.setUsername(System.getenv("RABBITMQ_USERNAME"));
factory.setPassword(System.getenv("RABBITMQ_PASSWORD"));
~~~

### 6.3 Connection và Channel

Quan hệ:

~~~text
ConnectionFactory
  └── Connection: TCP connection dài hạn
       ├── Channel publish
       └── Channel consume
~~~

Ví dụ:

~~~java
try (Connection connection = factory.newConnection();
     Channel channel = connection.createChannel()) {
    // declare, publish, consume
}
~~~

Connection nặng hơn channel. Tái sử dụng connection trong lifecycle application và không tạo connection mới cho từng message.

Channel có state riêng như consumer và delivery tag. Không dùng cùng channel đồng thời từ nhiều thread nếu client library không đảm bảo thread-safety.

## 7. Declare topology bằng Java

~~~java
String exchange = "orders.events";
String queue = "orders.email";
String routingKey = "order.created";

channel.exchangeDeclare(exchange, BuiltinExchangeType.TOPIC, true);
channel.queueDeclare(queue, true, false, false, null);
channel.queueBind(queue, exchange, routingKey);
~~~

Ý nghĩa:

~~~text
exchangeDeclare(name, type, durable)
queueDeclare(name, durable, exclusive, autoDelete, arguments)
queueBind(queue, exchange, routingKey)
~~~

Topology declaration phải thống nhất với entity đã tồn tại. Nếu exchange orders.events đang là fanout nhưng code declare là topic, channel có thể bị đóng do thuộc tính xung đột.

### Queue server-named

Subscription tạm thời:

~~~java
String generatedQueue = channel.queueDeclare(
        "",
        false,
        true,
        true,
        null
).getQueue();

channel.queueBind(generatedQueue, "order.broadcast", "");
~~~

Không dùng server-named queue cho command queue cần tồn tại khi service offline.

### Default exchange

Có thể publish trực tiếp vào queue:

~~~java
channel.basicPublish(
        "",
        "orders.email",
        null,
        body
);
~~~

Cách này đơn giản nhưng producer phụ thuộc vào tên queue. Với domain topology, nên publish vào exchange có tên rõ ràng.

## 8. Publish bằng basicPublish

Publish cơ bản:

~~~java
byte[] body = "{\"orderId\":\"order-100\"}"
        .getBytes(StandardCharsets.UTF_8);

channel.basicPublish(
        "orders.events",
        "order.created",
        null,
        body
);
~~~

Message properties:

~~~java
AMQP.BasicProperties properties =
        new AMQP.BasicProperties.Builder()
                .contentType("application/json")
                .contentEncoding("UTF-8")
                .deliveryMode(2)
                .messageId("msg-100")
                .type("OrderCreated")
                .correlationId("trace-100")
                .build();

channel.basicPublish(
        "orders.events",
        "order.created",
        properties,
        body
);
~~~

deliveryMode(2) thường biểu thị persistent message. Nó không phải publisher confirm.

### Publish 100 message

~~~java
String[] routingKeys = {
        "order.created",
        "order.paid",
        "order.cancelled"
};

for (int index = 1; index <= 100; index++) {
    String routingKey = routingKeys[(index - 1) % routingKeys.length];
    String body = "{\"eventId\":\"evt-" + index
            + "\",\"orderId\":\"order-" + index
            + "\",\"type\":\"" + routingKey + "\"}";

    channel.basicPublish(
            "orders.events",
            routingKey,
            propertiesFor(index, routingKey),
            body.getBytes(StandardCharsets.UTF_8)
    );
}
~~~

basicPublish không ném exception không có nghĩa message đã được broker accept bền vững. Publisher confirms sẽ được học ở tuần reliability.

## 9. Consume bằng basicConsume

Consumer manual acknowledgement:

~~~java
DeliverCallback deliverCallback = (consumerTag, delivery) -> {
    long deliveryTag = delivery.getEnvelope().getDeliveryTag();
    boolean redelivered = delivery.getEnvelope().isRedeliver();
    String routingKey = delivery.getEnvelope().getRoutingKey();
    String body = new String(
            delivery.getBody(),
            StandardCharsets.UTF_8
    );

    System.out.printf(
            "tag=%d redelivered=%s routingKey=%s body=%s%n",
            deliveryTag,
            redelivered,
            routingKey,
            body
    );

    channel.basicAck(deliveryTag, false);
};

CancelCallback cancelCallback = consumerTag ->
        System.out.println("consumer cancelled: " + consumerTag);

channel.basicConsume(
        "orders.email",
        false,
        deliverCallback,
        cancelCallback
);
~~~

Với autoAck=false, consumer phải ack. Message chưa ack nằm ở unacked và có thể requeue khi channel/connection đóng.

### Delivery tag

Delivery tag là số thứ tự của delivery trong channel. Nó chỉ dùng để ack/reject trong channel đó:

~~~java
channel.basicAck(deliveryTag, false);
~~~

Nó không phải message ID và không phải idempotency key. Dùng messageId/eventId trong payload hoặc properties cho mục đích đó.

### Redelivered flag

isRedeliver() là tín hiệu broker đang giao lại delivery. Nó không thay thế idempotency:

- message có thể được giao lại sau crash;
- side effect trước crash có thể đã thành công;
- một message mới có thể có redelivered=false nhưng vẫn xử lý lỗi;
- consumer vẫn phải idempotent.

### Ack sau khi xử lý

Thứ tự cơ bản:

~~~text
1. nhận message
2. validate
3. business processing
4. ghi kết quả thành công
5. basicAck
~~~

Ack trước khi xử lý xong có thể làm broker xóa message trong khi database/downstream chưa thành công.

Sau basicConsume, callback cần một lifecycle giữ JVM sống. Trong lab có thể dùng:

~~~java
CountDownLatch shutdown = new CountDownLatch(1);
shutdown.await();
~~~

## 10. Producer và consumer mẫu

### RabbitConfig.java

~~~java
import com.rabbitmq.client.ConnectionFactory;

public final class RabbitConfig {
    private RabbitConfig() {
    }

    public static ConnectionFactory createFactory() {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(value("RABBITMQ_HOST", "localhost"));
        factory.setPort(integerValue("RABBITMQ_PORT", 5672));
        factory.setUsername(value("RABBITMQ_USERNAME", "dev"));
        factory.setPassword(value("RABBITMQ_PASSWORD", "dev-password"));
        factory.setVirtualHost(value("RABBITMQ_VHOST", "/app"));
        factory.setConnectionTimeout(5_000);
        factory.setRequestedHeartbeat(30);
        factory.setAutomaticRecoveryEnabled(true);
        factory.setTopologyRecoveryEnabled(true);
        return factory;
    }

    private static String value(String name, String fallback) {
        String configured = System.getenv(name);
        return configured == null || configured.isBlank()
                ? fallback
                : configured;
    }

    private static int integerValue(String name, int fallback) {
        String configured = System.getenv(name);
        return configured == null || configured.isBlank()
                ? fallback
                : Integer.parseInt(configured);
    }
}
~~~

### Producer.java

~~~java
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import java.nio.charset.StandardCharsets;

public final class Producer {
    private static final String EXCHANGE = "orders.events";

    public static void main(String[] args) throws Exception {
        ConnectionFactory factory = RabbitConfig.createFactory();

        try (Connection connection = factory.newConnection();
             Channel channel = connection.createChannel()) {

            channel.exchangeDeclare(EXCHANGE, BuiltinExchangeType.TOPIC, true);

            String[] keys = {
                    "order.created",
                    "order.paid",
                    "order.cancelled"
            };

            for (int index = 1; index <= 100; index++) {
                String routingKey = keys[(index - 1) % keys.length];
                String body = "{\"eventId\":\"evt-" + index
                        + "\",\"orderId\":\"order-" + index
                        + "\",\"type\":\"" + routingKey + "\"}";

                AMQP.BasicProperties properties =
                        new AMQP.BasicProperties.Builder()
                                .contentType("application/json")
                                .deliveryMode(2)
                                .messageId("msg-" + index)
                                .type("OrderEvent")
                                .build();

                channel.basicPublish(
                        EXCHANGE,
                        routingKey,
                        properties,
                        body.getBytes(StandardCharsets.UTF_8)
                );
            }

            System.out.println("Published 100 messages");
        }
    }
}
~~~

### Consumer.java

~~~java
import com.rabbitmq.client.CancelCallback;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;
import com.rabbitmq.client.Delivery;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;

public final class Consumer {
    private static final String EXCHANGE = "orders.events";
    private static final String QUEUE = "orders.audit";

    public static void main(String[] args) throws Exception {
        ConnectionFactory factory = RabbitConfig.createFactory();

        try (Connection connection = factory.newConnection();
             Channel channel = connection.createChannel()) {

            channel.exchangeDeclare(EXCHANGE, "topic", true);
            channel.queueDeclare(QUEUE, true, false, false, null);
            channel.queueBind(QUEUE, EXCHANGE, "order.#");

            DeliverCallback callback = (consumerTag, delivery) -> {
                log(delivery);
                channel.basicAck(
                        delivery.getEnvelope().getDeliveryTag(),
                        false
                );
            };

            CancelCallback cancelled = tag ->
                    System.out.println("cancelled: " + tag);

            String tag = channel.basicConsume(
                    QUEUE,
                    false,
                    callback,
                    cancelled
            );

            System.out.println("consumer started: " + tag);
            new CountDownLatch(1).await();
        }
    }

    private static void log(Delivery delivery) {
        var envelope = delivery.getEnvelope();
        String body = new String(
                delivery.getBody(),
                StandardCharsets.UTF_8
        );

        System.out.printf(
                "deliveryTag=%d redelivered=%s routingKey=%s body=%s%n",
                envelope.getDeliveryTag(),
                envelope.isRedeliver(),
                envelope.getRoutingKey(),
                body
        );
    }
}
~~~

Code production cần thêm validation, retry hữu hạn, DLQ, metric, graceful shutdown và idempotency. Không dùng nack requeue vô hạn cho poison message.

## 11. Heartbeat, timeout và recovery

### Heartbeat

~~~java
factory.setRequestedHeartbeat(30);
~~~

Heartbeat giúp phát hiện connection chết hoặc network path không còn hoạt động. Nó không phải business timeout và không có nghĩa message phải xử lý trong 30 giây.

Heartbeat quá thấp có thể false positive khi network chập chờn. Heartbeat quá cao làm phát hiện connection chết chậm hơn.

### Connection timeout

~~~java
factory.setConnectionTimeout(5_000);
~~~

Connection timeout áp dụng khi mở connection. Nó khác với heartbeat timeout, consumer processing timeout, HTTP timeout và message expiration.

### Automatic connection recovery

~~~java
factory.setAutomaticRecoveryEnabled(true);
~~~

Khi connection chết, client có thể:

1. phát hiện lỗi;
2. chờ recovery delay;
3. tạo connection mới;
4. mở lại channel;
5. khôi phục consumer theo cấu hình.

### Topology recovery

~~~java
factory.setTopologyRecoveryEnabled(true);
~~~

Client có thể khôi phục exchange, queue, binding và consumer đã declare qua connection đó.

Recovery không bảo đảm:

- publish ngay trước lúc đứt đã tới broker;
- message không bị duplicate;
- database transaction được rollback;
- HTTP/SMTP side effect được undo;
- consumer idempotent;
- thay đổi topology thủ công được khôi phục đúng.

Recovery là phục hồi transport, không phải transaction phân tán.

## 12. Lab 1 — Publish 100 message

### Mục tiêu

Tạo topology:

~~~text
exchange: orders.events, type topic
orders.all     <- #
orders.created <- order.created
orders.payment <- order.paid
~~~

Chạy Producer rồi kiểm tra:

~~~bash
docker compose exec rabbitmq rabbitmqctl list_queues \
  name messages_ready messages_unacknowledged consumers
~~~

Vì key lặp theo thứ tự created, paid, cancelled:

~~~text
orders.all     = 100
orders.created = 34
orders.payment = 33
cancelled      = 33 message không vào hai queue cụ thể
~~~

Nếu consumer đang chạy, messages_ready có thể về 0. Khi đó kiểm tra log consumer, unacked và delivered rate.

Ghi kết quả:

| Queue | Binding | Expected | Actual |
|---|---|---:|---:|
| orders.all | # | 100 | |
| orders.created | order.created | 34 | |
| orders.payment | order.paid | 33 | |

## 13. Lab 2 — Delivery tag và redelivery

1. Dừng consumer cũ.
2. Publish 5 message vào queue.
3. Chạy Consumer.
4. Ghi deliveryTag, redelivered, routingKey, body và thời điểm ack.
5. Kiểm tra queue trong UI.

Output mẫu:

~~~text
deliveryTag=1 redelivered=false routingKey=order.created body=...
deliveryTag=2 redelivered=false routingKey=order.paid body=...
~~~

Delivery tag thường tăng trong cùng channel. Khi channel mở lại, tag có thể bắt đầu lại.

## 14. Lab 3 — Dừng consumer trước ack

Tạm sửa callback:

~~~java
long deliveryTag = delivery.getEnvelope().getDeliveryTag();
Thread.sleep(10_000);
channel.basicAck(deliveryTag, false);
~~~

Thực hiện:

1. Publish 10 message.
2. Chạy consumer.
3. Trong 10 giây, xem ready giảm và unacked tăng.
4. Dừng process trước khi ack.
5. Refresh UI.
6. Chạy consumer lại.
7. Quan sát message redelivered.

Trả lời:

- Vì sao message chuyển từ ready sang unacked?
- Vì sao message quay lại queue sau khi channel đóng?
- Nếu side effect đã xong nhưng ack chưa gửi, điều gì xảy ra?
- Cần eventId/idempotency ở đâu?

## 15. Lab 4 — Restart container

Tạo và ghi thuộc tính:

~~~text
durable.exchange
durable.queue
non-durable.exchange
non-durable.queue
~~~

Kiểm tra:

~~~bash
docker compose exec rabbitmq rabbitmqctl list_exchanges name durable
docker compose exec rabbitmq rabbitmqctl list_queues name durable auto_delete
~~~

Restart:

~~~bash
docker compose restart rabbitmq
~~~

Kiểm tra lại entity và ghi kết luận:

- durable entity có mục tiêu tồn tại qua restart;
- non-durable entity có thể biến mất;
- message persistence là vấn đề riêng;
- consumer/connection/channel cần được tạo lại hoặc recovery;
- publish không có exception chưa phải publisher confirm.

## 16. Lab 5 — UI và topology-as-code

1. Trong UI tạo exchange manual.events.
2. Tạo queue manual.audit.
3. Bind audit.#.
4. Publish và xác nhận route.
5. Viết Java code declare cùng topology.
6. Chạy code nhiều lần.
7. Kiểm tra entity vẫn nhất quán.
8. Cố tình declare khác type và quan sát channel exception.
9. Khôi phục cấu hình đúng.

UI hữu ích cho quan sát và debug. Code/definitions mới là nơi nên lưu topology để review, tái tạo local/test và kiểm soát thay đổi contract.

## 17. Lỗi Java client thường gặp

### Sai vhost hoặc permission

Triệu chứng thường là access refused. Kiểm tra:

~~~bash
docker compose exec rabbitmq rabbitmqctl list_vhosts
docker compose exec rabbitmq rabbitmqctl list_permissions -p /app
~~~

Authenticate được không có nghĩa user có quyền configure/write/read.

### Exchange hoặc queue mismatch

Cùng tên entity nhưng khác type, durable, exclusive hoặc auto-delete có thể làm channel bị đóng. Không xóa entity production để “cho code chạy”; xác định owner và migration trước.

### Consumer không nhận message

Kiểm tra:

1. consumer subscribe đúng queue chưa;
2. queue có ready message không;
3. binding có đúng không;
4. callback có exception không;
5. channel có bị đóng không;
6. main thread có kết thúc sớm không;
7. consumer có ack quá sớm không.

### Body encoding sai

Publish JSON UTF-8 thì đọc bằng StandardCharsets.UTF_8 và đặt content type application/json. Không phụ thuộc encoding mặc định của máy.

### Channel bị đóng

AMQP protocol exception có thể đóng channel. Log exception gốc, kiểm tra topology/permission và tạo channel mới theo recovery policy.

## 18. Nguyên tắc connection/channel

Nên:

- tạo connection trong application lifecycle;
- reuse connection;
- tạo channel theo workload;
- đóng graceful khi shutdown;
- cấu hình heartbeat;
- kiểm thử automatic recovery;
- theo dõi connection/channel leak.

Không nên:

- mở connection mới cho từng message;
- chia sẻ channel giữa nhiều thread mà không biết thread-safety;
- dùng delivery tag làm business ID;
- ack trước khi xử lý;
- coi recovery là exactly-once;
- xóa topology tùy tiện để debug;
- hardcode credential.

Với N message:

~~~text
sai:  N connections + N handshakes
đúng: 1 connection + reuse channel/publish path
~~~

Connection churn làm tăng latency, file descriptor, CPU broker và nguy cơ quá tải.

## 19. Deliverable cuối tuần

Tạo:

~~~text
rabbitmq/week-03/
├── README.md
├── pom.xml
└── src/main/java/
    ├── RabbitConfig.java
    ├── Producer.java
    └── Consumer.java
~~~

README cần có:

1. Cách khởi động broker, host, port, user và vhost.
2. Output status, list_queues, list_connections và list_channels.
3. Ghi chú hoặc ảnh Management UI.
4. Sơ đồ topology producer/consumer.
5. Log 100 message với routing key.
6. Log delivery tag, redelivered và body.
7. Bảng ready/unacked trước và sau khi dừng consumer.
8. Kết quả restart durable/non-durable entity.
9. Cấu hình heartbeat, connection timeout và recovery.
10. Giải thích vì sao không mở connection cho từng message.

## 20. Tiêu chí tự kiểm tra

1. Overview, Connections, Channels và Queues khác nhau thế nào?
2. Queue có ready bằng 0 có chắc không còn message đang xử lý không?
3. Channel nằm ở đâu trong quan hệ với connection?
4. Lệnh nào xem queue depth và consumer count?
5. Khi nào dùng rabbitmqctl, rabbitmq-diagnostics và rabbitmq-plugins?
6. ConnectionFactory, Connection và Channel có trách nhiệm gì?
7. Vì sao declare topology trước publish/consume?
8. Delivery tag khác messageId thế nào?
9. redelivered cho biết và không cho biết điều gì?
10. Vì sao ack sau business processing?
11. Heartbeat khác connection timeout thế nào?
12. Automatic recovery có đảm bảo không duplicate không?
13. Vì sao durable queue vẫn cần persistent message và publisher confirm?
14. Vì sao không mở connection mới cho từng message?
15. Nếu producer publish không exception nhưng queue không có message, kiểm tra gì?

### Đáp án cần nhớ

> UI giúp quan sát state; CLI giúp kiểm tra node, entity và permission; Java client thực hiện AMQP operations. Connection là TCP, channel là kênh logic, queue giữ message và consumer đọc queue. Hãy reuse connection, declare topology rõ ràng, ack sau xử lý, log delivery tag/redelivered để debug, và xem recovery là phục hồi transport chứ không phải transaction hay exactly-once.

## 21. Chuẩn bị cho Tuần 4

Tuần sau đưa các thao tác này vào Spring AMQP:

- RabbitTemplate;
- AmqpAdmin;
- MessageListenerContainer;
- @RabbitListener;
- JSON message converter;
- queue/exchange/binding bằng Java configuration;
- correlation ID và custom headers.

Giữ lại Producer, Consumer, definitions và output CLI của tuần này. Spring AMQP giảm boilerplate, nhưng các nguyên tắc connection, channel, topology, acknowledgement và recovery vẫn không thay đổi.

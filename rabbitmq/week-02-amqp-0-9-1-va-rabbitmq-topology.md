# Tuần 2 — AMQP 0-9-1 và RabbitMQ topology

> Mục tiêu của bài này là đọc được một topology RabbitMQ như đọc một sơ đồ mạng: message được publish ở đâu, exchange route theo luật nào, queue nào giữ message và consumer nào nhận nó. Sau tuần này, bạn có thể dựng topology cho command, event, work queue, fan-out, routing và topic bằng RabbitMQ Management UI.

## 1. Kết quả cần đạt

Sau bài học, bạn có thể:

- Giải thích quan hệ giữa connection, channel, virtual host, exchange, queue, binding và consumer.
- Mô tả lifecycle cơ bản từ lúc client kết nối đến lúc consumer nhận message.
- Phân biệt direct, fanout, topic và headers exchange.
- Dự đoán message sẽ vào queue nào dựa trên exchange, binding và routing key.
- Thiết kế queue durable, exclusive, auto-delete và message persistent phù hợp với mục đích.
- Phân biệt queue tạm thời của subscription với queue nghiệp vụ cần tồn tại sau restart.
- Dùng Management UI để tạo, bind, publish, consume và quan sát topology.
- Hoàn thành topology cho payment, inventory và notification; fan-out cho OrderCreated; topic routing cho nhóm event order.

## 2. AMQP 0-9-1 là gì?

AMQP là giao thức để ứng dụng giao tiếp với message broker. RabbitMQ hỗ trợ nhiều protocol và abstraction, nhưng lộ trình này tập trung vào AMQP 0-9-1 vì đây là mô hình nền của RabbitMQ client và Spring AMQP.

AMQP 0-9-1 không chỉ định nghĩa “gửi một chuỗi vào một queue”. Nó định nghĩa các khái niệm và thao tác như:

- mở connection và channel;
- khai báo exchange, queue và binding;
- publish message vào exchange;
- consume hoặc get message từ queue;
- acknowledgement, reject và nack;
- property của message như routing key, delivery mode và headers.

Mental model cốt lõi:

~~~text
Application
    │
    │ connection TCP
    ▼
RabbitMQ node / virtual host
    │
    ├── exchange  ── binding ──> queue ──> consumer
    └── exchange  ── binding ──> queue ──> consumer
~~~

Producer không publish trực tiếp vào queue theo cách thông thường. Producer publish vào exchange; exchange quyết định message có được route đến queue nào. Có một ngoại lệ quan trọng là default exchange, sẽ học ở phần sau.

## 3. Toàn cảnh topology

Topology là tập hợp các entity và quan hệ route trong broker:

~~~text
Exchange + Binding + Queue + Consumer
~~~

Ví dụ topology cho đơn hàng:

~~~text
order.events (topic exchange)
  ├── order.email (queue)      <- order.*
  ├── order.analytics (queue)  <- order.#
  └── order.audit (queue)      <- #
~~~

Khi producer publish:

~~~text
exchange   = order.events
routingKey = order.created
payload    = {"orderId":"order-123"}
~~~

RabbitMQ xét từng binding:

- order.* khớp order.created nên message vào order.email;
- order.# cũng khớp nên message vào order.analytics;
- # khớp nên message vào order.audit.

Nếu không có binding phù hợp, message thường bị discard nếu publish không dùng mandatory flag và publisher không xử lý return. Message không tự động “đi tìm queue gần nhất”.

## 4. Connection, channel và virtual host

### 4.1 Connection

Connection là kết nối TCP dài hạn giữa application và RabbitMQ node.

Một connection:

- tốn tài nguyên mạng và broker hơn channel;
- có authentication, heartbeat và lifecycle riêng;
- có thể bị đóng khi network lỗi, broker restart hoặc heartbeat timeout;
- nên được tái sử dụng thay vì mở một connection cho mỗi message.

Thông thường một service có một số lượng nhỏ connection ổn định. Nếu thấy connection tăng theo số request, đó thường là dấu hiệu thiết kế sai.

### 4.2 Channel

Channel là một kênh logic chạy bên trong connection. Các thao tác AMQP như declare, publish và consume thường thực hiện trên channel.

~~~text
1 TCP connection
  ├── channel publish
  ├── channel consume
  └── channel administration
~~~

Channel nhẹ hơn connection, nhưng không phải vô hạn. Channel có state riêng như consumer, transaction và delivery tag. Delivery tag chỉ có ý nghĩa trong channel đã tạo ra nó; không được dùng delivery tag của channel này để ack trên channel khác.

Quy tắc thực tế:

- tái sử dụng connection;
- tạo channel theo workload và thư viện quản lý;
- không tạo channel mới cho từng message;
- không dùng cùng một channel đồng thời từ nhiều thread nếu client library không đảm bảo thread-safety.

### 4.3 Virtual host

Virtual host, viết tắt là vhost, là namespace và boundary quyền trong RabbitMQ.

~~~text
RabbitMQ cluster
  ├── /app-dev
  │   ├── exchange, queue, binding...
  │   └── users/permissions
  └── /app-prod
      ├── exchange, queue, binding...
      └── users/permissions
~~~

Hai vhost có thể có exchange và queue trùng tên mà không đụng nhau. User cũng được cấp quyền theo vhost.

Trong local:

~~~text
host: localhost
port: 5672
vhost: /app
user: dev
~~~

Trong môi trường thật, vhost không thay thế hoàn toàn network isolation hay account separation, nhưng là boundary quan trọng để tránh service dùng nhầm topology của môi trường khác.

### 4.4 Lifecycle khái quát

~~~text
Application
  -> open connection
  -> authenticate vào vhost
  -> open channel
  -> declare exchange/queue/binding
  -> publish hoặc consume
  -> acknowledge message
  -> close channel/connection khi shutdown
~~~

Topology thường được declare ở startup. Khai báo cùng một entity nhiều lần với cùng thuộc tính thường là idempotent; khai báo cùng tên nhưng khác thuộc tính có thể làm channel bị lỗi. Vì vậy, tên và thuộc tính topology phải được quản lý như contract.

## 5. Exchange

Exchange nhận message từ producer và quyết định queue đích dựa trên exchange type, binding và routing key hoặc headers.

Exchange không lưu message như queue. Nó thực hiện routing. Message chỉ được giữ lại ở queue sau khi route thành công.

### 5.1 Direct exchange

Direct exchange route khi routing key của message khớp chính xác binding key.

~~~text
exchange: order.commands

binding:
  payment.queue   <- payment
  inventory.queue <- inventory
  email.queue     <- email
~~~

Các publish sau:

~~~text
routing key = payment   -> payment.queue
routing key = inventory -> inventory.queue
routing key = shipping  -> không có queue phù hợp
~~~

Direct phù hợp cho command có owner rõ ràng, phân luồng theo loại worker và routing key đơn giản.

Nhiều queue có thể bind cùng một key. Khi đó message được route tới tất cả queue phù hợp; direct không đồng nghĩa với “chỉ một consumer nhận”.

### 5.2 Fanout exchange

Fanout bỏ qua routing key và broadcast message đến mọi queue đã bind vào exchange.

~~~text
order.events (fanout)
  ├── email.queue
  ├── analytics.queue
  └── audit.queue
~~~

Publish một lần, mỗi queue nhận một bản sao logic của message. Các consumer cạnh tranh trong cùng một queue, nhưng các queue khác nhau đều nhận broadcast.

Fanout phù hợp cho event cần nhiều subscriber độc lập, cache invalidation, audit và analytics.

Nếu một subscriber tạm thời không tồn tại và queue của nó là queue tạm thời chưa được bind, fanout không lưu lịch sử để subscriber mới đọc lại. Fanout không phải event store.

### 5.3 Topic exchange

Topic exchange route bằng routing key dạng các từ ngăn cách bởi dấu chấm.

Ví dụ routing key:

~~~text
order.created
order.paid
order.payment.failed
inventory.reserved
~~~

Wildcard:

- * khớp đúng một từ;
- # khớp không từ nào hoặc nhiều từ.

| Binding key | order.created | order.payment.failed | inventory.reserved |
|---|---:|---:|---:|
| order.* | Có | Không | Không |
| order.# | Có | Có | Không |
| # | Có | Có | Có |
| *.created | Có | Không | Không |
| order.*.* | Không | Có | Không |

Topic phù hợp khi subscriber cần một nhóm event:

~~~text
email.queue      <- order.created
payment.queue    <- order.payment.*
audit.queue      <- #
order-read.queue <- order.#
~~~

Quy ước routing key phải ổn định. Nếu lúc đầu dùng order.created nhưng sau đó publish created-order, binding topic sẽ không giúp sửa contract.

### 5.4 Headers exchange

Headers exchange route dựa trên message headers thay vì routing key.

Ví dụ binding yêu cầu:

~~~text
x-match = all
region  = vn
format  = pdf
~~~

Hoặc:

~~~text
x-match = any
priority = high
format   = urgent
~~~

x-match=all yêu cầu mọi header khai báo trong binding khớp; x-match=any chỉ cần một header khớp.

Headers exchange hữu ích khi rule route không tự nhiên biểu diễn bằng chuỗi topic. Trade-off:

- khó nhìn topology hơn routing key;
- khó kiểm tra bằng mắt và dễ tạo nhiều rule;
- producer phải gửi đúng tên và kiểu header;
- thường không phải lựa chọn mặc định cho event domain.

Ở tuần này chỉ cần nhận biết và thử một binding headers nhỏ. Với phần lớn domain event, direct hoặc topic dễ vận hành hơn.

## 6. Queue

Queue là nơi message chờ consumer. Queue có tên, thuộc tính và lifecycle.

### 6.1 Durable queue

Durable queue được định nghĩa để tồn tại qua broker restart. Durable không có nghĩa là mọi message chắc chắn được lưu bền vững.

Để giảm nguy cơ mất message khi restart, thường cần phối hợp:

~~~text
durable queue
+ persistent message
+ publisher confirms
+ consumer acknowledgements
~~~

Mỗi phần giải quyết một rủi ro khác nhau. Durable queue mà publish message non-persistent vẫn chưa đủ cho mục tiêu durability.

### 6.2 Non-durable queue

Non-durable queue thường chỉ tồn tại trong lifecycle hiện tại của broker. Nó phù hợp cho lab hoặc dữ liệu tạm thời, không phù hợp cho command nghiệp vụ cần xử lý sau restart.

### 6.3 Exclusive queue

Exclusive queue gắn với connection tạo ra nó. Khi connection đóng, queue bị xóa.

Phù hợp cho:

- subscription tạm thời;
- reply queue của một client;
- consumer chỉ tồn tại trong một session.

Không dùng exclusive queue cho work queue production cần nhiều instance cùng đọc hoặc cần tồn tại sau reconnect.

### 6.4 Auto-delete queue

Auto-delete queue được xóa khi không còn consumer phù hợp theo lifecycle của queue. Chi tiết thời điểm xóa phụ thuộc trạng thái queue và consumer, nên không nên dùng auto-delete như một cơ chế business cleanup duy nhất.

Dùng cho subscription tạm thời; dùng queue có tên và lifecycle rõ ràng cho dữ liệu nghiệp vụ.

### 6.5 Server-named queue

Nếu khai báo queue với tên rỗng, broker sinh tên duy nhất, ví dụ:

~~~text
amq.gen-9Vf0Qp...
~~~

Đây là lựa chọn tiện cho subscription tạm thời vì client không phải tự sinh tên tránh collision. Thường kết hợp với:

~~~text
exclusive = true
auto-delete = true
~~~

Một service cần queue ổn định để nhận command không nên dùng server-named queue: sau reconnect, queue mới sẽ tạo ra subscription mới và queue cũ có thể còn message chưa xử lý.

## 7. Binding và routing key

Binding là quan hệ nối exchange với queue, kèm một binding key hoặc tập headers.

~~~text
source exchange -- binding rule --> destination queue
~~~

Ví dụ:

~~~text
order.events -- order.paid --> payment-followup.queue
~~~

Binding key và message routing key là hai phía của luật route:

~~~text
message.routingKey == binding.routingKey
~~~

với direct exchange; topic exchange áp dụng wildcard matching.

Một message có thể được route đến:

- không queue nào;
- một queue;
- nhiều queue.

Một queue có thể nhận message từ nhiều exchange nếu có nhiều binding. Khi troubleshooting, không chỉ nhìn queue; phải truy ngược exchange, binding và routing key.

### Default exchange

RabbitMQ có default exchange với tên rỗng. Đây là direct exchange đặc biệt. Mỗi queue được bind ngầm vào default exchange bằng chính tên queue làm routing key.

Nếu queue tên email.queue, producer có thể publish:

~~~text
exchange   = ""
routingKey = "email.queue"
~~~

Message sẽ vào email.queue.

Default exchange tiện cho ví dụ đơn giản và work queue, nhưng không nên che giấu topology domain khi hệ thống lớn. Dùng exchange có tên rõ ràng giúp thấy được ý nghĩa route và tách producer khỏi tên queue.

## 8. Message properties và persistence

Message gồm payload và các thuộc tính/properties. Một số thuộc tính quan trọng:

| Thuộc tính | Ý nghĩa |
|---|---|
| content_type | Kiểu payload, ví dụ application/json |
| content_encoding | Encoding của payload |
| delivery_mode | Non-persistent hoặc persistent |
| message_id | Định danh message |
| correlation_id | Nối message với request/workflow |
| reply_to | Địa chỉ reply trong request/reply |
| expiration | Thời gian hết hạn message |
| type | Loại message, ví dụ OrderCreated |
| headers | Metadata mở rộng |

### Durable không bằng persistent

Phân biệt:

~~~text
durable exchange/queue = entity tồn tại sau restart
persistent message     = broker có thể lưu payload bền vững hơn
~~~

Muốn một flow có khả năng phục hồi tốt, phải xem cả:

1. Entity có durable không?
2. Message có persistent không?
3. Producer có biết broker đã nhận publish chưa?
4. Consumer có ack sau khi xử lý xong không?

Publisher confirm và consumer acknowledgement sẽ học kỹ hơn ở các tuần sau. Ở tuần này chỉ cần không nhầm “queue durable” với “message chắc chắn không mất”.

## 9. Quy ước đặt tên topology

Tên topology nên nói được domain và vai trò. Một bộ quy ước đơn giản:

~~~text
Exchange:
  order.events
  order.commands

Queue:
  order.email
  order.analytics
  payment.commands

Routing key:
  order.created
  order.paid
  payment.capture
~~~

Tránh tên quá chung:

~~~text
events
queue1
data
test
~~~

Một queue thường đại diện cho một consumer responsibility, không nhất thiết là một class Java. Nếu email và analytics cần retry, scale và retention khác nhau, chúng nên có queue riêng dù cùng nhận OrderCreated.

### Command và event trong topology

~~~text
order.commands (direct)
  └── payment.capture -> payment.commands

order.events (topic)
  ├── order.created -> order.email
  ├── order.#       -> order.analytics
  └── #             -> order.audit
~~~

Command exchange có route hẹp đến owner. Event exchange có thể có nhiều subscriber và dùng topic/fanout tùy nhu cầu.

## 10. Lab 0 — Khởi động RabbitMQ local

Tạo docker-compose.yml theo roadmap:

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

Khởi động:

~~~bash
docker compose up -d
~~~

Mở http://localhost:15672 với username dev, password dev-password và virtual host /app.

Trong local lab, thông tin này có thể dùng. Không dùng password mặc định hoặc expose Management UI công khai trong production.

### Kiểm tra ban đầu

Trong Management UI, kiểm tra:

1. Overview: broker đang hoạt động.
2. Connections: connection hiện tại.
3. Channels: channel của connection.
4. Exchanges: exchange mặc định và exchange ứng dụng.
5. Queues and Streams: queue hiện có.
6. Admin → Virtual Hosts: vhost /app.

## 11. Lab 1 — Direct exchange cho payment, inventory và notification

### Mục tiêu

Tạo command exchange route chính xác đến ba queue:

~~~text
orders.commands (direct)
  ├── payment.queue      <- payment
  ├── inventory.queue    <- inventory
  └── notification.queue <- notification
~~~

### Tạo exchange

Trong Management UI:

1. Vào tab Exchanges.
2. Chọn Add a new exchange.
3. Đặt tên orders.commands.
4. Chọn type direct.
5. Chọn durable nếu queue này phục vụ command nghiệp vụ.
6. Tạo exchange.

### Tạo queue

Tạo lần lượt:

~~~text
payment.queue
inventory.queue
notification.queue
~~~

Với command production-like, chọn queue durable và không chọn exclusive/auto-delete.

### Tạo binding

Ở từng queue, thêm binding từ orders.commands:

| Queue | Binding key |
|---|---|
| payment.queue | payment |
| inventory.queue | inventory |
| notification.queue | notification |

### Publish và kiểm tra

Trong orders.commands, dùng phần publish message:

~~~text
Routing key: payment
Payload: {"orderId":"order-100","amount":99000}
~~~

Kiểm tra payment.queue có một message ready. Publish lại với routing key inventory và xác nhận message chỉ vào inventory.queue.

Publish với routing key shipping. Kết quả mong đợi: không queue nào tăng message count. Ghi lại đây là unroutable publish; ở các tuần sau sẽ học cách phát hiện bằng mandatory publish/return hoặc publisher confirm.

### Câu hỏi lab

- Nếu thêm audit.queue bind key payment, message payment vào mấy queue?
- Nếu tạo hai consumer trên payment.queue, cả hai có nhận cùng một message không?
- Muốn mọi command được audit, dùng direct với nhiều binding key hay fanout/topic? Nêu trade-off.

## 12. Lab 2 — Fanout cho OrderCreated

### Mục tiêu

Broadcast một event tới ba subscriber độc lập:

~~~text
order.created (fanout)
  ├── email.queue
  ├── analytics.queue
  └── audit.queue
~~~

### Thực hiện

1. Tạo fanout exchange order.created.
2. Tạo ba queue durable: email.queue, analytics.queue, audit.queue.
3. Bind cả ba queue vào exchange.
4. Publish payload:

~~~json
{
  "eventId": "evt-100",
  "orderId": "order-100",
  "occurredAt": "2026-10-02T10:00:00Z"
}
~~~

5. Đặt routing key bất kỳ, ví dụ ignored.key.
6. Kiểm tra cả ba queue đều có message.

### Thử nghiệm subscription tạm thời

Tạo một queue server-named, exclusive và auto-delete. Bind queue đó vào fanout exchange rồi publish event. Queue tạm thời nhận message.

Tiếp theo, đóng connection hoặc xóa queue rồi publish event mới. Queue đã xóa không thể nhận lại event. Đây là lý do subscription tạm thời không phù hợp cho consumer cần đọc event trong lúc offline.

### Fanout và competing consumers

Tạo hai consumer trên email.queue, rồi publish một event. Chỉ một trong hai consumer nhận message đó vì chúng cùng cạnh tranh trong một queue.

Nếu muốn cả hai consumer đều nhận message, phải tạo hai queue riêng và bind cả hai vào exchange. Đây là khác biệt quan trọng:

~~~text
cùng queue       = load balance / competing consumers
khác queue       = broadcast / independent subscribers
~~~

## 13. Lab 3 — Topic exchange cho event order

### Mục tiêu

Tạo topology:

~~~text
order.events (topic)
  ├── order.email      <- order.created
  ├── order.payment    <- order.paid và order.payment.*
  ├── order.analytics  <- order.#
  └── order.audit      <- #
~~~

### Tạo queue và binding

| Queue | Binding key | Ý nghĩa |
|---|---|---|
| order.email | order.created | Chỉ email khi đơn được tạo |
| order.payment | order.paid | Theo dõi payment thành công |
| order.payment | order.payment.* | Nhận các event payment một tầng |
| order.analytics | order.# | Nhận toàn bộ event bắt đầu bằng order |
| order.audit | # | Nhận mọi routing key |

### Publish các routing key

Lần lượt publish:

~~~text
order.created
order.paid
order.payment.failed
order.cancelled
inventory.reserved
~~~

Dự đoán trước message count rồi kiểm tra:

| Routing key | email | payment | analytics | audit |
|---|---:|---:|---:|---:|
| order.created | 1 | 0 | 1 | 1 |
| order.paid | 0 | 1 | 1 | 1 |
| order.payment.failed | 0 | 1 | 1 | 1 |
| order.cancelled | 0 | 0 | 1 | 1 |
| inventory.reserved | 0 | 0 | 0 | 1 |

Nếu kết quả khác dự đoán, kiểm tra đúng ba thứ theo thứ tự:

1. Publish vào đúng exchange chưa?
2. Routing key có đúng từng ký tự và dấu chấm không?
3. Binding key có wildcard phù hợp không?

## 14. Lab 4 — Nhận biết headers exchange

### Mục tiêu

Route message theo metadata:

~~~text
document.exchange (headers)
  ├── pdf.vn.queue      <- format=pdf AND region=vn
  └── urgent.queue      <- priority=high OR format=urgent
~~~

### Thực hiện

1. Tạo exchange document.exchange, type headers.
2. Tạo queue pdf.vn.queue.
3. Bind với headers:

~~~text
x-match = all
format  = pdf
region  = vn
~~~

4. Tạo queue urgent.queue.
5. Bind với:

~~~text
x-match  = any
priority = high
format   = urgent
~~~

6. Publish message với các headers khác nhau và ghi kết quả.

| Headers message | pdf.vn.queue | urgent.queue |
|---|---:|---:|
| format=pdf, region=vn | Có | Không |
| format=pdf, region=us | Không | Không |
| priority=high, format=html | Không | Có |
| format=urgent, region=us | Không | Có |

Kết luận lab: headers có thể diễn tả điều kiện linh hoạt, nhưng topology khó đọc hơn topic. Chỉ chọn nó khi routing key không đủ rõ hoặc không phù hợp.

## 15. Lab 5 — Durable, auto-delete, exclusive và persistence

Tạo bốn queue để so sánh:

| Queue | Durable | Exclusive | Auto-delete | Mục đích |
|---|---:|---:|---:|---|
| durable.work | Có | Không | Không | Work queue nghiệp vụ |
| temporary.work | Không | Không | Có | Dữ liệu tạm |
| exclusive.session | Không | Có | Có | Gắn với một connection |
| server-named queue | tùy khai báo | Có | Có | Subscription tạm |

### Quan sát lifecycle

1. Tạo từng queue và ghi tên/thuộc tính.
2. Bind một exchange vào mỗi queue.
3. Publish message.
4. Đóng connection hoặc consumer liên quan.
5. Refresh Management UI.
6. Ghi queue nào còn tồn tại và message nào còn lại.
7. Restart container RabbitMQ.
8. Kiểm tra queue durable còn tồn tại hay không.

### Thử message persistence

Với queue durable, publish hai message:

- một message delivery mode persistent;
- một message delivery mode non-persistent.

Restart broker và quan sát. Kết quả còn phụ thuộc thời điểm flush, trạng thái broker và cách publish; lab này chỉ nhằm hiểu rằng durable queue và persistent message là hai cài đặt khác nhau. Không dùng thử nghiệm local này để tuyên bố rằng hệ thống đã có guarantee production. Publisher confirm là phần bắt buộc để biết broker đã accept publish.

## 16. Lab 6 — Tạo topology từ file definitions

Ngoài tạo bằng UI, RabbitMQ có thể import definitions để lặp lại topology. Đây là cách hữu ích cho môi trường dev/test và version control.

Một file definitions tối giản:

~~~json
{
  "vhosts": [
    {"name": "/app"}
  ],
  "exchanges": [
    {
      "name": "order.events",
      "vhost": "/app",
      "type": "topic",
      "durable": true,
      "auto_delete": false,
      "internal": false
    }
  ],
  "queues": [
    {
      "name": "order.analytics",
      "vhost": "/app",
      "durable": true,
      "auto_delete": false
    }
  ],
  "bindings": [
    {
      "source": "order.events",
      "vhost": "/app",
      "destination": "order.analytics",
      "destination_type": "queue",
      "routing_key": "order.#",
      "arguments": {}
    }
  ]
}
~~~

Khi dùng definitions:

- kiểm tra version/schema phù hợp với RabbitMQ đang chạy;
- không commit password hoặc secret thật;
- phân biệt definitions của local/test với production;
- coi việc đổi tên queue/exchange là migration, không phải thay đổi vô hại;
- review bindings để tránh tạo route quá rộng như # ngoài queue audit.

## 17. Troubleshooting topology theo đường đi của message

Khi message không tới queue, đừng bắt đầu bằng việc restart broker. Đi theo chuỗi sau:

~~~text
Producer
  -> vhost đúng chưa?
  -> exchange đúng chưa?
  -> exchange type đúng chưa?
  -> routing key/header đúng chưa?
  -> binding tồn tại chưa?
  -> queue đúng chưa?
  -> consumer có đang đọc không?
~~~

### Trường hợp A: queue không có message

Kiểm tra:

1. Producer có publish thành công trên đúng connection/vhost không?
2. Exchange có tồn tại trong vhost đó không?
3. Routing key có khớp binding không?
4. Queue có binding từ đúng exchange không?
5. Có consumer đã lấy message ngay sau khi publish không?
6. Message bị route vào queue khác do binding rộng không?

### Trường hợp B: nhiều queue cùng nhận ngoài dự kiến

Kiểm tra:

- có queue khác bind cùng routing key không;
- topic binding có dùng # quá rộng không;
- producer có publish vào fanout exchange không;
- queue có nhiều binding từ cùng exchange không.

### Trường hợp C: topology declare bị lỗi

Các nguyên nhân phổ biến:

- cùng tên exchange nhưng type khác;
- cùng tên queue nhưng durable/auto-delete/exclusive khác;
- client đang vào sai vhost;
- user không có quyền configure/write/read;
- exchange/queue có tên hoặc thuộc tính không hợp lệ.

Một entity đã tồn tại không nên bị đổi thuộc tính tùy tiện. Nếu cần thay đổi contract, dùng tên mới hoặc migration có kế hoạch.

### Trường hợp D: queue biến mất

Kiểm tra:

- queue có exclusive không;
- queue có auto-delete không;
- connection tạo queue còn sống không;
- queue có phải server-named không;
- broker có vừa restart và queue non-durable không.

## 18. Thiết kế topology mẫu cho hệ thống order

Một topology có thể bắt đầu như sau:

~~~text
                         ┌── order.email
                         │
order.events (topic) ────┼── order.analytics
                         │
                         └── order.audit

order.commands (direct) ── payment.commands
                       └── inventory.commands
~~~

Quy ước:

| Entity | Type | Binding | Trách nhiệm |
|---|---|---|---|
| order.events | topic | — | Phát domain event |
| order.email | queue | order.created | Gửi email |
| order.analytics | queue | order.# | Xử lý analytics |
| order.audit | queue | # | Lưu audit |
| order.commands | direct | — | Nhận command |
| payment.commands | queue | payment | Xử lý payment |
| inventory.commands | queue | inventory | Xử lý inventory |

Đây chỉ là topology khởi đầu. Cần xem thêm retention, retry, DLQ, throughput, security và HA trước khi gọi là production-ready.

## 19. Bài tập thiết kế

### Bài 1: Notification

Thiết kế topology cho các event:

~~~text
user.registered
order.created
order.cancelled
~~~

Yêu cầu:

- email service nhận user.registered và order.created;
- audit nhận mọi event;
- analytics nhận mọi event bắt đầu bằng order.;
- mỗi responsibility có queue riêng.

Chọn exchange type và viết đầy đủ exchange, queue, binding key.

### Bài 2: Payment command

Có các command:

~~~text
payment.authorize
payment.capture
payment.refund
~~~

Thiết kế để một payment worker xử lý tất cả command nhưng vẫn có thể thêm một fraud worker chỉ nhận payment.authorize.

Gợi ý: dùng topic exchange với queue payment bind payment.#, queue fraud bind payment.authorize.

### Bài 3: Phân tích topology sai

Một team tạo:

~~~text
events (fanout)
  └── shared.queue
      ├── email consumer
      ├── audit consumer
      └── analytics consumer
~~~

Nêu ít nhất ba vấn đề:

- consumer nào nhận message là không chắc chắn;
- analytics chậm có thể ảnh hưởng email;
- retry của một consumer có thể ảnh hưởng responsibility khác;
- scale độc lập khó;
- message contract và ownership không rõ.

Đề xuất topology sửa bằng các queue riêng.

## 20. Deliverable cuối tuần

Tạo thư mục:

~~~text
rabbitmq/week-02/
├── README.md
├── definitions.json
└── diagrams/
    └── order-topology.md
~~~

README.md cần có:

1. Bảng giải thích connection, channel, vhost, exchange, queue, binding và consumer.
2. Sơ đồ direct exchange cho payment/inventory/notification.
3. Sơ đồ fanout cho OrderCreated.
4. Bảng dự đoán kết quả của topic routing.
5. So sánh durable, exclusive, auto-delete và server-named queue.
6. Ảnh chụp Management UI hoặc log chứng minh từng lab.
7. Một bảng troubleshooting cho trường hợp unroutable message.
8. Definitions topology có thể import lại ở local/test.

order-topology.md có thể dùng Mermaid:

~~~mermaid
flowchart LR
    P[Producer] --> E[order.events<br/>topic]
    E -->|order.created| EMAIL[order.email]
    E -->|order.#| ANALYTICS[order.analytics]
    E -->|#| AUDIT[order.audit]
    EMAIL --> EC[Email Consumer]
    ANALYTICS --> AC[Analytics Consumer]
    AUDIT --> AU[Audit Consumer]
~~~

## 21. Tiêu chí tự kiểm tra

Bạn hoàn thành Tuần 2 khi có thể trả lời:

1. Producer publish vào đâu: queue hay exchange?
2. Exchange có lưu message lâu dài thay queue không?
3. Channel khác connection như thế nào?
4. Vhost giải quyết vấn đề namespace và permission ra sao?
5. Direct, fanout, topic và headers khác nhau ở luật route nào?
6. Vì sao hai consumer cùng một queue không tạo ra broadcast?
7. Làm thế nào để hai subscriber độc lập đều nhận cùng một event?
8. * và # trong topic exchange khác nhau thế nào?
9. Durable queue có đảm bảo message không mất không? Vì sao không?
10. Khi nào nên dùng exclusive hoặc server-named queue?
11. Vì sao một queue nghiệp vụ không nên auto-delete?
12. Nếu message không vào queue, bạn kiểm tra những thành phần nào theo thứ tự?

### Đáp án ngắn gọn cần nhớ

> Producer publish vào exchange; exchange route qua binding đến queue; consumer đọc queue. Direct khớp chính xác, fanout broadcast, topic khớp pattern, headers khớp metadata. Queue nghiệp vụ thường durable và có tên ổn định; queue subscription tạm thời có thể exclusive, auto-delete và server-named. Durable entity không thay thế persistent message, publisher confirm hay consumer ack.

## 22. Chuẩn bị cho Tuần 3

Tuần sau sẽ dùng RabbitMQ CLI, Management UI và Java client để kiểm tra topology bằng code:

- đọc connection, channel, queue và consumer;
- publish/consume để cô lập lỗi ứng dụng;
- dùng ConnectionFactory, Connection và Channel;
- declare exchange, queue, binding;
- quan sát delivery tag và redelivered flag;
- thử heartbeat, automatic recovery và topology recovery.

Hãy giữ lại definitions và sơ đồ của tuần này. Chúng sẽ là topology nền để thực hành acknowledgement, prefetch, retry và DLQ ở các tuần tiếp theo.

# Tuần 10 — Decoupling: SQS, SNS, EventBridge và messaging — phiên bản 2

> Bản này viết lại theo hướng giải thích từ nền tảng đến thiết kế thực tế. Mục tiêu là hiểu vì sao cần tách rời hệ thống, chọn đúng SQS/SNS/EventBridge/Step Functions và xử lý được retry, duplicate, ordering, DLQ.

## 1. Bức tranh lớn

Một hệ thống tightly coupled thường gọi trực tiếp giữa các service:

~~~text
Client → Order API → Payment → Inventory → Notification
~~~

Cách này dễ bắt đầu nhưng tạo nhiều phụ thuộc:

- Payment chậm thì Order API phải chờ.
- Inventory lỗi thì việc tạo order có thể thất bại.
- Traffic burst truyền thẳng xuống mọi dependency.
- Thêm consumer mới phải sửa publisher.
- Retry có thể tạo side effect trùng lặp.

Decoupling đặt queue, topic hoặc event bus giữa các thành phần:

~~~text
Producer → Queue/Topic/Event Bus → Consumer
~~~

Producer có thể tiếp tục sau khi gửi thành công; consumer xử lý theo tốc độ và chính sách retry riêng.

Bốn câu hỏi cần trả lời trước khi chọn dịch vụ:

1. Producer có cần kết quả ngay không?
2. Một message cần một consumer hay nhiều consumer?
3. Có cần buffer để hấp thụ burst không?
4. Có cần ordering, replay hoặc workflow nhiều bước không?

---

## 2. Kết quả cần đạt

Sau tuần này, bạn có thể:

- Giải thích decoupling làm tăng resilience và khả năng scale độc lập.
- Phân biệt message, event, queue, topic, event bus và workflow.
- Chọn SQS Standard hay FIFO theo ordering, throughput và duplicate tolerance.
- Giải thích visibility timeout bằng một message flow cụ thể.
- Thiết kế long polling, batching, retention, delay queue và DLQ.
- Hiểu vì sao at-least-once delivery yêu cầu consumer idempotent.
- Dùng SNS cho broadcast/fan-out và SNS → SQS cho consumer độc lập.
- Dùng EventBridge để filter và route event theo nội dung.
- Phân biệt EventBridge với SNS, SQS và Step Functions.
- Nhận biết khi Amazon MQ phù hợp cho migration.
- Viết failure path rõ ràng: retry → DLQ → alert → redrive.
- Theo dõi queue age, DLQ depth, failed invocation và consumer latency.

## 3. Lịch học gợi ý

| Buổi | Nội dung | Thời lượng |
|---|---|---:|
| 1 | Decoupling và SQS Standard | 3 giờ |
| 2 | FIFO, visibility timeout, retry và DLQ | 3 giờ |
| 3 | SNS, filter policy và fan-out | 2 giờ |
| 4 | EventBridge, replay và Step Functions | 3 giờ |
| 5 | Amazon MQ, lab và câu hỏi thi | 2–3 giờ |

---

## 4. Message và event

### 4.1. Message/command

Message thường mang ý nghĩa “hãy làm một việc”.

~~~json
{
  "type": "ChargePayment",
  "orderId": "O-1001",
  "amount": 125000,
  "currency": "VND"
}
~~~

Một consumer cụ thể thường sở hữu command này.

### 4.2. Event

Event mang ý nghĩa “một việc đã xảy ra”.

~~~json
{
  "type": "OrderCreated",
  "orderId": "O-1001",
  "customerId": "C-001",
  "occurredAt": "2026-10-10T09:30:00Z"
}
~~~

Nhiều consumer có thể quan tâm cùng event:

- Billing;
- Shipping;
- Analytics;
- Loyalty;
- Notification.

Producer chỉ cần publish event theo schema ổn định, không cần biết mọi consumer đang tồn tại.

### 4.3. Queue, topic, event bus và workflow

| Thành phần | Vai trò | Câu hỏi chính |
|---|---|---|
| Queue | giữ message cho worker | Ai sẽ xử lý và xử lý lúc nào? |
| Topic | broadcast message/event | Subscriber nào cần nhận? |
| Event bus | filter và route event | Event này đi tới target nào? |
| Workflow | điều phối các bước | Các bước chạy theo thứ tự/điều kiện nào? |

SQS chủ yếu là queue, SNS là pub/sub topic, EventBridge là event routing bus và Step Functions là workflow orchestration.

---

## 5. Vì sao decoupling quan trọng?

### 5.1. Hấp thụ traffic burst

Nếu API gọi trực tiếp worker, traffic tăng đột ngột sẽ truyền thẳng xuống worker:

~~~text
API → burst request → Worker quá tải
~~~

Nếu có SQS:

~~~text
API → SQS nhận burst → Worker xử lý theo tốc độ ổn định
~~~

Queue không làm công việc biến mất; nó biến một failure tức thời thành backlog có thể quan sát và xử lý.

### 5.2. Scale độc lập

Producer và consumer có thể scale khác nhau:

- API scale theo request;
- worker scale theo queue backlog;
- consumer chậm không giữ connection của producer;
- mỗi loại công việc có worker riêng.

### 5.3. Cô lập failure

Email provider lỗi không nhất thiết làm checkout thất bại. Order service có thể ghi order và gửi email job vào queue để retry sau.

### 5.4. Thêm consumer không sửa publisher

OrderCreated có thể được nhiều hệ thống sử dụng mà Order API không cần biết chi tiết từng consumer.

### 5.5. Trade-off

Decoupling thường thêm:

- eventual consistency;
- độ trễ;
- duplicate delivery;
- retry/DLQ;
- khó trace end-to-end;
- schema/version management;
- cost và operational complexity.

Không nên dùng messaging cho mọi lời gọi đơn giản. Chọn mức decoupling phù hợp với business requirement.

---

## 6. SQS là gì?

Amazon SQS là managed message queue. Producer gửi message vào queue; consumer chủ động lấy message để xử lý.

~~~text
Producer → SendMessage → SQS
Consumer → ReceiveMessage → process
Consumer → DeleteMessage
~~~

Message không biến mất ngay khi được receive. Nó bị ẩn trong visibility timeout. Consumer phải delete sau khi xử lý thành công.

Nếu consumer crash, timeout hoặc không delete, message xuất hiện lại để thử lại.

### 6.1. Use case

SQS phù hợp với:

- buffer giữa API và worker;
- xử lý batch;
- asynchronous job;
- retry workload;
- smoothing traffic burst;
- Lambda/EC2/ECS worker;
- microservice decoupling.

SQS không phải database chính, không phải event history vĩnh viễn và không tự chạy business logic.

### 6.2. Ví dụ order processing

~~~text
Order API
  ├── ghi Order = PENDING
  └── gửi ProcessOrder
          ↓
      OrderQueue
          ↓
      Worker xử lý payment/inventory
          ├── thành công → cập nhật trạng thái
          ├── lỗi tạm thời → retry
          └── lỗi cố định → DLQ
~~~

Trong production, việc ghi database và gửi message cần cân nhắc outbox/event pattern để tránh trạng thái đã ghi nhưng message chưa được gửi.

---

## 7. SQS Standard

SQS Standard ưu tiên throughput và khả năng scale. Semantics chính là at-least-once delivery và best-effort ordering.

### 7.1. Khi chọn Standard?

- throughput cao;
- message có thể xử lý song song;
- duplicate xử lý được;
- strict ordering không bắt buộc;
- consumer có idempotency;
- muốn thiết kế đơn giản.

Ví dụ: resize ảnh, gửi email, analytics, log processing và batch job.

### 7.2. At-least-once delivery

At-least-once nghĩa message có thể được giao nhiều lần. Duplicate có thể xảy ra khi:

- worker crash trước DeleteMessage;
- network timeout;
- visibility timeout hết hạn;
- client retry;
- message được redrive;
- event được replay.

Consumer nên:

1. lấy idempotency key;
2. kiểm tra key đã xử lý chưa;
3. thực hiện side effect;
4. ghi trạng thái xử lý;
5. delete message sau thành công;
6. nếu duplicate, trả kết quả an toàn và không lặp side effect.

### 7.3. Best-effort ordering

Standard không đảm bảo thứ tự toàn queue. Nếu producer gửi A rồi B, consumer có thể nhận B trước A.

Nếu order quan trọng:

- dùng FIFO;
- lưu sequence/version và xử lý ở application;
- hoặc thiết kế business không phụ thuộc thứ tự.

---

## 8. SQS FIFO

FIFO dành cho workload cần ordering và deduplication trong phạm vi được hỗ trợ.

### 8.1. Message group

Ordering được bảo đảm trong cùng MessageGroupId. Group khác nhau có thể chạy song song.

~~~text
Group customer-001: M1 → M2 → M3
Group customer-002: N1 → N2 → N3
~~~

MessageGroupId có thể là customerId, orderId, accountId hoặc deviceId, tùy entity cần tuần tự hóa.

Một group duy nhất cho toàn queue giữ thứ tự đơn giản nhưng biến queue thành bottleneck. Nhiều group tăng throughput nhưng chỉ giữ order trong từng group.

### 8.2. Deduplication

FIFO có deduplication ID hoặc content-based deduplication trong cửa sổ thời gian của dịch vụ.

Vẫn cần idempotency ở application vì:

- retry sau cửa sổ dedup;
- side effect hoàn tất trước khi response bị mất;
- downstream có thể xử lý duplicate;
- redrive/replay tạo lại business event.

### 8.3. FIFO không phải exactly-once toàn hệ thống

~~~text
Queue deduplication ≠ exactly-once business transaction
~~~

Nếu payment đã thành công nhưng worker timeout trước DeleteMessage, payment service vẫn phải nhận biết paymentId đã xử lý.

### 8.4. Khi chọn FIFO?

- order của cùng customer phải tuần tự;
- command của cùng account không chạy song song;
- thiết bị gửi command theo sequence;
- cập nhật trạng thái cần giữ trình tự.

Nếu chỉ cần throughput cao và duplicate có thể xử lý, Standard thường phù hợp hơn.

---

## 9. Visibility timeout

Visibility timeout là khoảng thời gian message bị ẩn sau khi consumer nhận.

~~~text
ReceiveMessage
      ↓
Message invisible
      ├── xử lý xong → DeleteMessage
      └── timeout/crash → message visible lại
~~~

### 9.1. Timeout quá ngắn

Nếu worker mất 120 giây nhưng visibility timeout chỉ 30 giây:

1. Worker A nhận message.
2. Sau 30 giây message xuất hiện lại.
3. Worker B nhận cùng message.
4. Hai worker cùng chạy side effect.
5. Downstream có thể bị xử lý duplicate.

### 9.2. Timeout quá dài

Nếu worker chết ngay sau khi nhận:

- message bị ẩn lâu;
- retry recovery chậm;
- operator khó nhận ra backlog;
- DLQ chuyển chậm.

### 9.3. Cách chọn

Visibility timeout nên lớn hơn thời gian xử lý tối đa hợp lý, cộng buffer cho network và delete. Job dài nên dùng ChangeMessageVisibility/heartbeat thay vì đặt timeout cực lớn cho mọi message.

### 9.4. Lambda đọc SQS

Khi Lambda dùng event source mapping:

- Lambda poll queue;
- nhiều message có thể vào một batch;
- function xử lý batch;
- message thành công được delete;
- message lỗi có thể retry tùy cấu hình.

Visibility timeout phải phù hợp với Lambda timeout và retry. Dùng partial batch response nếu muốn chỉ retry item lỗi.

---

## 10. Polling và batching

### 10.1. Long polling

Long polling cho phép consumer chờ message trong một khoảng thời gian.

Lợi ích:

- giảm empty receive;
- giảm số API request;
- giảm cost;
- giảm CPU/network lãng phí.

Long polling không thay visibility timeout.

### 10.2. Short polling

Short polling trả response ngay. Khi queue thường rỗng, consumer tạo nhiều empty response và tốn request không cần thiết.

### 10.3. Batching

Batch nhiều message giảm overhead và tăng throughput nhưng làm failure granularity lớn hơn.

Cân nhắc:

- batch size;
- processing time;
- memory;
- downstream rate limit;
- timeout;
- partial batch failure;
- idempotency.

---

## 11. Retention, delay và payload

### 11.1. Retention

Retention xác định message được giữ bao lâu. Chọn theo thời gian consumer có thể outage, thời gian xử lý backlog, replay need và cost.

Retention không phải backup lâu dài. Nếu cần history, ghi event vào S3 hoặc event store.

### 11.2. Delay queue

Delay queue làm message invisible trước lần nhận đầu tiên. Dùng cho deferred processing hoặc retry đơn giản.

| Cơ chế | Áp dụng khi |
|---|---|
| Delay | trước lần nhận đầu |
| Visibility timeout | sau khi consumer nhận |
| Retry/backoff | giữa các lần thử |
| DLQ | sau nhiều lần thất bại |

### 11.3. Payload lớn

Với file/payload lớn:

1. lưu dữ liệu vào S3;
2. gửi bucket/key/checksum qua SQS;
3. consumer dùng IAM role đọc object;
4. kiểm tra object và checksum;
5. lifecycle object sau xử lý.

Gửi pointer thường tốt hơn gửi cả file trong message.

---

## 12. Dead-letter queue

DLQ chứa message không xử lý được sau số lần thử. Nó giúp tách poison message khỏi healthy messages.

### 12.1. Poison message

Ví dụ:

- schema sai;
- field bắt buộc thiếu;
- permission sai;
- object không tồn tại;
- bug consumer;
- business rule bị từ chối vĩnh viễn.

Retry vô hạn làm worker lãng phí và backlog tăng.

### 12.2. maxReceiveCount

Chọn maxReceiveCount dựa trên:

- lỗi transient kéo dài bao lâu;
- retry/backoff;
- processing time;
- khả năng sửa tự động;
- cost;
- business impact.

Quá thấp khiến lỗi tạm thời vào DLQ sớm; quá cao khiến poison message quay vòng lâu.

### 12.3. Redrive an toàn

1. Đọc error và nhóm nguyên nhân.
2. Sửa consumer/schema/permission.
3. Test bằng một message.
4. Redrive batch nhỏ.
5. Theo dõi error rate, age và DLQ.
6. Dừng nếu lỗi quay lại.
7. Ghi root cause và prevention.

DLQ không phải backup chính thức và không thay retention/archive strategy.

---

## 13. SQS security

SQS hỗ trợ encryption at rest bằng key managed phù hợp. Khi dùng KMS, producer/consumer có thể cần thêm permission/key policy.

Queue policy có thể:

- cho phép SNS topic gửi message;
- giới hạn source account/ARN;
- yêu cầu TLS;
- kiểm soát cross-account access.

Encryption không thay authorization. Không gửi password/token nhạy cảm nếu không cần.

---

## 14. SNS — pub/sub và fan-out

SNS là managed pub/sub topic. Publisher gửi một message vào topic; SNS phân phối tới subscriber.

~~~text
Publisher → SNS Topic
              ├── SQS
              ├── Lambda
              ├── HTTP/S
              └── target hỗ trợ
~~~

### 14.1. Khi dùng SNS?

- broadcast event;
- notification;
- fan-out tới nhiều consumer;
- publisher không cần biết từng consumer;
- thêm subscriber mà không sửa publisher.

SNS không phải durable pull queue. Consumer cần buffer, backpressure, retry lâu hoặc xử lý theo tốc độ riêng nên subscribe SNS tới SQS.

### 14.2. Subscription

Khi SNS gửi tới SQS, cần:

- subscribe queue vào topic;
- queue policy trust đúng topic ARN;
- giới hạn source account/ARN;
- biết payload là SNS envelope hay raw message;
- consumer idempotent.

### 14.3. Filter policy

Filter policy giúp subscriber chỉ nhận message phù hợp.

~~~text
OrderEvents
  ├── BillingQueue: eventType = OrderPaid
  ├── ShippingQueue: eventType = OrderPaid và region = VN
  └── AnalyticsQueue: tất cả event
~~~

Producer phải gửi attribute/body đúng schema. Sai field hoặc data type có thể khiến message bị filter mà không tạo lỗi rõ ràng.

---

## 15. SNS → SQS fan-out

Pattern tiêu chuẩn:

~~~text
Order Service
      ↓ publish OrderCreated
SNS OrderEvents
      ├── BillingQueue → BillingWorker
      ├── ShippingQueue → ShippingWorker
      └── AnalyticsQueue → AnalyticsWorker
~~~

Mỗi consumer có:

- queue riêng;
- visibility timeout riêng;
- DLQ riêng;
- tốc độ scale riêng;
- retry/redrive riêng;
- metric và alarm riêng.

Billing chậm không chặn Shipping. Đây là lý do SNS → SQS thường tốt hơn việc publisher gửi trực tiếp tới nhiều queue.

Fan-out không bảo đảm exactly-once end-to-end. SNS có thể retry, SQS Standard có thể duplicate và mỗi consumer phải idempotent.

---

## 16. EventBridge

EventBridge là event bus managed: nhận event, match pattern và route tới target.

~~~text
Producer/AWS service → Event bus → Rule → Target
                                      ├── Lambda
                                      ├── SQS/SNS
                                      ├── Step Functions
                                      └── API/service target
~~~

### 16.1. Khi dùng EventBridge?

- nhận AWS service events;
- domain/application event;
- route theo source/detail-type/detail;
- nhiều rule và target;
- cần archive/replay;
- muốn producer không biết consumer.

### 16.2. Event envelope

Event thường có source, detail-type, time, account, region, resources và detail.

~~~json
{
  "source": ["com.shop.orders"],
  "detail-type": ["OrderCreated"],
  "detail": {
    "status": ["PAID"]
  }
}
~~~

Event contract nên có version, timestamp, correlation ID và business ID. Không đưa secret/PII không cần thiết vào event.

### 16.3. Event bus

- Default bus: AWS service events.
- Custom bus: application/domain events.
- Partner bus: partner integration.
- Cross-account bus: route event giữa account theo resource policy.

### 16.4. Rule và target

Một event có thể match nhiều rule. Mỗi rule route độc lập tới Lambda, SQS, SNS, Step Functions, API destination hoặc target hỗ trợ.

EventBridge có retry/failure behavior; có thể cấu hình DLQ cho failed target. Target vẫn phải idempotent.

### 16.5. Archive và replay

Archive/replay hữu ích khi:

- thêm consumer mới;
- rebuild projection;
- sửa bug consumer;
- khôi phục sau incident;
- test integration.

Replay có thể tạo duplicate side effect. Trước khi replay, phải kiểm tra idempotency, schema version, thời điểm replay và khả năng xử lý duplicate.

---

## 17. SNS và EventBridge khác nhau thế nào?

| Tiêu chí | SNS | EventBridge |
|---|---|---|
| Mô hình | topic/pub-sub | event bus/rule |
| Điểm mạnh | notification/fan-out | routing theo event pattern |
| Filtering | subscription filter | event pattern phong phú |
| AWS events | không phải trọng tâm | rất phù hợp |
| Archive/replay | không phải use case chính | hỗ trợ |
| Queue riêng | subscribe SQS | target có thể là SQS |
| Use case | broadcast đơn giản | integration/event routing |

Chọn SNS khi đề nhấn mạnh fan-out/pub-sub. Chọn EventBridge khi đề nhấn mạnh event filtering, AWS service event, custom bus hoặc replay.

---

## 18. Chọn giữa SQS, SNS, EventBridge và Step Functions

| Requirement | Lựa chọn |
|---|---|
| Buffer công việc cho worker | SQS |
| Một message được một worker xử lý | SQS |
| Broadcast tới nhiều subscriber | SNS |
| Fan-out có queue độc lập | SNS → SQS |
| Route event theo nội dung | EventBridge |
| AWS service events | EventBridge |
| Workflow nhiều bước có state | Step Functions |
| Strict ordering theo group | SQS FIFO |
| Broker protocol legacy | Amazon MQ |
| Replay event | EventBridge archive/replay/event store |
| Backpressure | SQS |

Ví dụ kết hợp:

~~~text
Checkout API
   ↓ publish OrderCreated
EventBridge
   ├── Step Functions: order workflow
   ├── SNS: notifications
   │     ├── EmailQueue
   │     └── SMSQueue
   └── SQS: analytics worker
~~~

Không phải architecture nào cũng cần tất cả dịch vụ. Chọn primitive nhỏ nhất đáp ứng requirement.

---

## 19. Amazon MQ

Amazon MQ là managed broker cho application cần compatibility với ActiveMQ/RabbitMQ và protocol như JMS, AMQP, STOMP, MQTT hoặc OpenWire.

### Chọn Amazon MQ khi:

- migration legacy muốn ít rewrite;
- đã phụ thuộc broker client/API;
- cần broker semantics, exchange/routing hoặc transaction;
- team chưa thể refactor sang AWS-native messaging.

### Không chọn Amazon MQ khi:

- application mới chỉ cần queue;
- cần cloud-native fan-out/event routing;
- muốn ít broker management nhất;
- không có requirement protocol compatibility.

SQS/SNS/EventBridge thường đơn giản hơn cho application mới, nhưng migration legacy có thể ưu tiên Amazon MQ.

---

## 20. Retry, backoff và idempotency

### 20.1. Transient và permanent error

Transient error có thể tự hết:

- timeout;
- throttling;
- network reset;
- downstream 503;
- service recovery.

Permanent error retry tiếp cũng không giải quyết:

- schema sai;
- thiếu field;
- authorization sai;
- business rule không hợp lệ;
- object không tồn tại.

Retry transient; đưa permanent error vào DLQ hoặc reject flow.

### 20.2. Exponential backoff và jitter

Nếu nhiều worker retry cùng lúc, chúng tạo retry storm. Exponential backoff tăng dần thời gian chờ; jitter làm các worker lệch thời điểm.

~~~text
Attempt 1 → chờ ngắn
Attempt 2 → chờ lâu hơn
Attempt 3 → chờ lâu hơn nữa
Cuối cùng → DLQ/alert
~~~

Retry cần giới hạn attempts, tổng thời gian và visibility timeout.

### 20.3. Idempotent consumer

Consumer idempotent cho cùng business message dù chạy một hay nhiều lần vẫn tạo ra kết quả đúng.

Pattern:

1. lấy idempotency key;
2. kiểm tra record đã xử lý chưa;
3. nếu đã xử lý, bỏ qua hoặc trả kết quả cũ;
4. nếu chưa, thực hiện side effect;
5. lưu trạng thái bằng conditional write/transaction;
6. xóa message sau thành công.

Ví dụ payment:

~~~text
paymentId = PAY-1001
Nếu PAY-1001 đã SUCCESS → không charge lại
Nếu chưa tồn tại → charge và lưu SUCCESS
~~~

Exactly-once cần được bảo vệ ở business boundary, không chỉ ở queue.

---

## 21. Observability

### SQS

Theo dõi:

- ApproximateNumberOfMessagesVisible;
- ApproximateNumberOfMessagesNotVisible;
- ApproximateAgeOfOldestMessage;
- messages sent/received/deleted;
- empty receives;
- DLQ depth.

AgeOfOldestMessage thường quan trọng hơn depth. Một message duy nhất nhưng chờ nhiều giờ vẫn là incident.

### SNS

Theo dõi publish count, delivery success/failure, subscription status và downstream queue depth.

### EventBridge

Theo dõi matched events, invocations, failed invocations, throttled rules, target DLQ và replay status.

### Consumer

Log message ID, business ID, correlation ID, attempt number, processing duration, outcome và failure category. Không log password, token hoặc full PII.

### Alarm gợi ý

- AgeOfOldestMessage vượt ngưỡng;
- DLQ depth lớn hơn 0;
- failed invocation tăng;
- queue backlog tăng liên tục;
- processing latency vượt SLA;
- retry count tăng;
- FIFO throughput thấp bất thường.

---

## 22. Failure path chuẩn

Một thiết kế messaging nên mô tả rõ flow khi thất bại:

~~~text
Message nhận
   ↓
Consumer xử lý
   ├── thành công → ghi kết quả → Delete
   ├── lỗi transient → retry/backoff
   ├── lỗi permanent → reject/DLQ
   └── timeout/crash → visibility hết hạn → retry
                        ↓
                   maxReceiveCount
                        ↓
                       DLQ
                        ↓
                 alarm → điều tra → redrive
~~~

Nếu sơ đồ không nói message lỗi đi đâu, thiết kế chưa hoàn chỉnh.

---

## 23. Lab 1 — Producer → SQS → worker

### Mục tiêu

Hiểu vòng đời message từ lúc gửi tới lúc xóa và quan sát queue hấp thụ backlog.

### Các bước

1. Tạo SQS Standard queue.
2. Tạo producer gửi order message.
3. Tạo Lambda/EC2 worker poll queue.
4. Cấu hình long polling và batch nhỏ.
5. Đặt visibility timeout lớn hơn processing time.
6. Chỉ delete sau khi side effect thành công.
7. Log message ID và order ID.
8. Quan sát visible, invisible và age metrics.
9. Làm worker fail trước DeleteMessage.
10. Xác nhận message xuất hiện lại.

### Cần tự trả lời

- Worker crash trước DeleteMessage thì chuyện gì xảy ra?
- Processing lâu hơn visibility timeout thì sao?
- Làm sao ngăn charge trùng?
- Scale thêm worker dựa trên metric nào?

---

## 24. Lab 2 — DLQ và redrive

1. Tạo source queue và DLQ.
2. Cấu hình redrive policy/maxReceiveCount.
3. Làm worker fail với payload đặc biệt.
4. Quan sát receive count và message chuyển DLQ.
5. Phân loại lỗi transient/permanent.
6. Sửa consumer.
7. Redrive một message hoặc batch nhỏ.
8. Theo dõi error rate, age và DLQ depth.
9. Dừng redrive nếu lỗi quay lại.
10. Ghi root cause và prevention.

Không redrive toàn bộ DLQ khi chưa biết nguyên nhân.

---

## 25. Lab 3 — FIFO ordering và deduplication

1. Tạo FIFO queue.
2. Gửi message vào group customer-001.
3. Gửi message vào group customer-002.
4. Dùng nhiều worker để quan sát parallelism.
5. Gửi lại cùng deduplication ID.
6. Kiểm tra thứ tự trong từng group.
7. So sánh throughput một group và nhiều group.
8. Thêm idempotency key ở business layer.

Kết luận cần ghi: một group giữ order tốt nhưng giới hạn throughput; nhiều group tăng throughput nhưng chỉ giữ order trong từng group.

---

## 26. Lab 4 — SNS fan-out tới hai SQS queue

~~~text
Publisher → SNS OrderEvents
               ├── BillingQueue → BillingWorker
               └── ShippingQueue → ShippingWorker
~~~

Các bước:

1. Tạo topic.
2. Tạo BillingQueue và ShippingQueue.
3. Tạo DLQ riêng cho mỗi queue.
4. Subscribe hai queue vào topic.
5. Cấu hình queue policy trust đúng topic ARN.
6. Gửi OrderCreated/OrderPaid.
7. Làm BillingWorker fail.
8. Xác nhận ShippingWorker vẫn xử lý.
9. Kiểm tra SNS envelope/raw delivery.
10. So sánh metric của hai queue.

---

## 27. Lab 5 — EventBridge rule

1. Tạo custom event bus hoặc dùng bus phù hợp.
2. Gửi event OrderCreated.
3. Tạo rule match source và detail-type.
4. Thêm điều kiện status=PAID.
5. Route tới SQS/Lambda/Step Functions.
6. Gửi event không match.
7. Cấu hình retry/DLQ.
8. Tạo archive nếu có thể.
9. Replay event test.
10. Xác nhận consumer không tạo duplicate side effect.

---

## 28. Bẫy đề thi SAA-C03

- Visibility timeout quá ngắn gây duplicate processing.
- Visibility timeout quá dài làm recovery chậm khi worker chết.
- SQS Standard là at-least-once và best-effort ordering.
- FIFO ordering nằm trong MessageGroupId, không phải toàn queue.
- FIFO deduplication không bảo đảm exactly-once business transaction.
- Một FIFO group duy nhất có thể là bottleneck.
- Delete message trước side effect hoàn tất có thể làm mất message.
- Long polling giảm empty receive, không thay visibility timeout.
- DLQ cần maxReceiveCount và retry strategy hợp lý.
- SNS là pub/sub/fan-out; SQS là durable pull-based queue.
- SNS → SQS cần subscription và queue policy đúng.
- Filter policy sai có thể khiến subscriber không nhận event.
- EventBridge là event routing bus, không phải worker queue.
- EventBridge replay có thể tạo duplicate side effect.
- Step Functions điều phối workflow có state, không thay queue buffer.
- Amazon MQ dành cho broker protocol/migration.
- Consumer phải idempotent với mọi delivery model.
- Queue depth thấp không có nghĩa queue khỏe; xem AgeOfOldestMessage.
- Retry permanent error làm poison message quay vòng.
- Payload lớn nên lưu S3 và gửi pointer.

---

## 29. Câu hỏi tự luyện

### Câu 1

Producer có burst traffic, worker xử lý chậm và cần scale độc lập. Chọn gì?

**Đáp án:** SQS làm buffer giữa producer và worker.

### Câu 2

Một event cần gửi tới Billing, Shipping và Analytics; mỗi consumer cần retry riêng. Thiết kế nào?

**Đáp án:** SNS topic fan-out tới một SQS queue riêng cho mỗi consumer.

### Câu 3

Cần route event theo source, detail-type và status. Chọn gì?

**Đáp án:** EventBridge event bus và event pattern.

### Câu 4

Order của cùng customer phải xử lý đúng thứ tự, customer khác xử lý song song. Chọn gì?

**Đáp án:** SQS FIFO với MessageGroupId là customerId.

### Câu 5

Worker xử lý 120 giây nhưng visibility timeout là 30 giây. Rủi ro?

**Đáp án:** message visible lại trước khi xử lý xong và bị duplicate. Tăng timeout/heartbeat và làm consumer idempotent.

### Câu 6

Message lỗi schema và retry mãi. Dùng gì?

**Đáp án:** DLQ với maxReceiveCount; sửa lỗi rồi redrive có kiểm soát.

### Câu 7

Payment thành công nhưng worker timeout trước DeleteMessage. Retry có thể gây gì?

**Đáp án:** duplicate charge nếu payment không có idempotency key. Bảo vệ side effect ở payment/business layer.

### Câu 8

Subscriber chỉ cần OrderPaid tại Việt Nam. Dùng gì?

**Đáp án:** SNS filter policy hoặc EventBridge event pattern tùy architecture.

### Câu 9

Workflow gồm validate, reserve stock, charge và notify, mỗi bước có retry/catch. Chọn gì?

**Đáp án:** Step Functions.

### Câu 10

Cần replay event sau khi sửa consumer. Chọn gì?

**Đáp án:** EventBridge archive/replay hoặc event store; consumer phải idempotent.

### Câu 11

Queue depth thấp nhưng message chờ rất lâu. Metric nào quan trọng?

**Đáp án:** ApproximateAgeOfOldestMessage.

### Câu 12

Ứng dụng legacy dùng JMS/AMQP và muốn ít rewrite. Chọn gì?

**Đáp án:** Amazon MQ nếu protocol/semantics phù hợp.

---

## 30. Checklist hoàn thành tuần

### Kiến thức

- [ ] Giải thích tightly coupled và loosely coupled.
- [ ] Phân biệt message với event.
- [ ] Phân biệt queue, topic, event bus và workflow.
- [ ] Hiểu SQS Standard/FIFO.
- [ ] Giải thích visibility timeout.
- [ ] Hiểu long polling, batch, retention và delay.
- [ ] Thiết kế DLQ, maxReceiveCount và redrive.
- [ ] Hiểu MessageGroupId, ordering và deduplication.
- [ ] Phân biệt SNS, subscription, filter và fan-out.
- [ ] Thiết kế SNS → SQS.
- [ ] Hiểu EventBridge bus, rule, target, pattern và replay.
- [ ] Chọn SQS/SNS/EventBridge/Step Functions/Amazon MQ.
- [ ] Thiết kế idempotent consumer.
- [ ] Áp dụng retry/backoff/jitter.
- [ ] Theo dõi queue age, DLQ depth và failed invocation.

### Thực hành

- [ ] Đã tạo producer → SQS → worker.
- [ ] Đã cấu hình visibility timeout lớn hơn processing time.
- [ ] Đã tạo DLQ và redrive.
- [ ] Đã test FIFO group/deduplication.
- [ ] Đã tạo SNS fan-out tới hai SQS queue.
- [ ] Đã tạo EventBridge rule lọc OrderCreated/OrderPaid.
- [ ] Đã test duplicate và idempotency.
- [ ] Đã tạo alarm cho AgeOfOldestMessage và DLQ depth.
- [ ] Đã dọn queue/topic/rule/archive sau lab.

Bạn nắm chắc Tuần 10 khi nhìn một requirement và trả lời được:

- Có cần buffer không?
- Có một hay nhiều consumer?
- Có cần ordering không, trong scope nào?
- Retry bao nhiêu lần?
- Message lỗi đi đâu?
- Nếu message chạy hai lần thì business có an toàn không?
- Có cần replay hoặc workflow nhiều bước không?

---

## 31. Tài liệu tham khảo

- [Amazon SQS Developer Guide](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/welcome.html) — queue, visibility và delivery.
- [Amazon SQS standard and FIFO queues](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-which-queue.html) — chọn Standard/FIFO.
- [Amazon SQS visibility timeout](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-visibility-timeout.html) — processing/retry.
- [Amazon SQS dead-letter queues](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-dead-letter-queues.html) — DLQ/redrive.
- [Amazon SNS Developer Guide](https://docs.aws.amazon.com/sns/latest/dg/welcome.html) — topic/subscription/fan-out.
- [SNS subscription filter policies](https://docs.aws.amazon.com/sns/latest/dg/sns-message-filtering.html) — filtering.
- [Amazon EventBridge User Guide](https://docs.aws.amazon.com/eventbridge/latest/userguide/eb-what-is.html) — bus/rule/target.
- [EventBridge event patterns](https://docs.aws.amazon.com/eventbridge/latest/userguide/eb-event-patterns.html) — pattern matching.
- [EventBridge archive and replay](https://docs.aws.amazon.com/eventbridge/latest/userguide/eb-archive.html) — replay.
- [AWS Step Functions Developer Guide](https://docs.aws.amazon.com/step-functions/latest/dg/welcome.html) — workflow.
- [Amazon MQ](https://docs.aws.amazon.com/amazon-mq/latest/developer-guide/what-is-amazon-mq.html) — managed broker/migration.


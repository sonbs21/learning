# Tuần 10 — Decoupling: SQS, SNS, EventBridge và messaging

> Mục tiêu: thiết kế hệ thống loosely coupled, chọn đúng queue/topic/event bus/workflow, xử lý retry, ordering, deduplication và failure bằng DLQ/idempotent consumer.

## 1. Kết quả cần đạt

- Giải thích vì sao decoupling tăng resilience và khả năng scale độc lập.
- Phân biệt SQS Standard với FIFO.
- Hiểu visibility timeout, long polling, retention, delay queue và message size.
- Thiết kế DLQ, redrive policy và maxReceiveCount.
- Hiểu throughput, best-effort ordering, message group và deduplication.
- Phân biệt SNS topic, subscription, filter policy và fan-out.
- Thiết kế SNS → SQS để tách publisher và consumer.
- Hiểu EventBridge event bus, rule, target, event pattern và archive/replay.
- Chọn SQS, SNS, EventBridge hoặc Step Functions theo workflow.
- Nhận biết Amazon MQ cho migration workload dùng broker protocol.
- Thiết kế consumer idempotent và xử lý duplicate message.
- Chọn visibility timeout, retry/backoff và metric phù hợp.

## 2. Lịch học gợi ý

| Buổi | Nội dung | Thời lượng |
|---|---|---:|
| 1 | Decoupling và SQS Standard | 3 giờ |
| 2 | FIFO, ordering, deduplication, DLQ | 3 giờ |
| 3 | SNS, fan-out và SNS → SQS | 2 giờ |
| 4 | EventBridge, archive/replay và Step Functions | 3 giờ |
| 5 | Amazon MQ, lab và câu hỏi thi | 2 giờ |

Cách suy luận: hỏi producer có cần chờ consumer không, một hay nhiều consumer, cần buffer hay broadcast, cần ordering/exactly-once ở scope nào, workflow có state hay chỉ là event routing.

---

## 3. Decoupling fundamentals

### 3.1. Tightly coupled

Producer gọi trực tiếp consumer và chờ response:

~~~text
Producer → Consumer
~~~

Nếu consumer chậm/lỗi, producer cũng timeout hoặc fail. Scale producer không giúp consumer, và deployment/failure của một service ảnh hưởng service kia.

### 3.2. Loosely coupled

Producer gửi message/event rồi tiếp tục:

~~~text
Producer → Queue/Topic/Event Bus → Consumer
~~~

Lợi ích:

- buffer traffic burst;
- scale producer và consumer độc lập;
- retry consumer;
- cô lập failure;
- deploy từng thành phần;
- fan-out tới nhiều consumer;
- giảm coupling về availability và timing.

Trade-off:

- eventual consistency;
- duplicate delivery;
- ordering phức tạp;
- observability khó hơn;
- poison message;
- cần idempotency và replay strategy;
- thêm latency/cost.

### 3.3. Queue, topic và event bus

| Abstraction | Ý nghĩa |
|---|---|
| Queue | một message được consumer xử lý, pull-based, buffer |
| Topic | một event/message broadcast tới nhiều subscriber |
| Event bus | route event theo pattern tới nhiều target |
| Workflow | điều phối các bước có state/retry/branch |

SQS chủ yếu là queue, SNS là pub/sub topic, EventBridge là event routing bus, Step Functions là workflow orchestration.

---

## 4. Amazon SQS

SQS là managed message queue để decouple producer và consumer. Producer gửi message vào queue; consumer poll và xử lý.

### 4.1. Flow cơ bản

~~~text
Producer → SendMessage → SQS
Consumer → ReceiveMessage → process
Consumer → DeleteMessage
~~~

Message vẫn tồn tại sau ReceiveMessage nhưng bị ẩn trong visibility timeout. Nếu xử lý thành công, consumer phải delete. Nếu không delete trước timeout, message có thể xuất hiện lại.

### 4.2. Use case

- buffer giữa API và worker;
- xử lý batch;
- decouple microservices;
- retry workload;
- smoothing burst;
- Lambda/EC2/ECS worker;
- asynchronous job.

SQS không tự chạy business logic; cần consumer. SQS cũng không phải database hoặc event history vĩnh viễn.

---

## 5. SQS Standard

SQS Standard cung cấp throughput cao và delivery ít nhất một lần theo mô hình dịch vụ. Duplicate message có thể xảy ra và ordering chỉ best-effort.

Chọn Standard khi:

- throughput cao;
- cần scale ngang;
- duplicate xử lý được;
- strict ordering không bắt buộc;
- consumer idempotent;
- cost/độ đơn giản ưu tiên.

Không chọn Standard nếu business không xử lý duplicate hoặc cần ordering nghiêm ngặt trong message group.

### 5.1. At-least-once delivery

At-least-once nghĩa message có thể được giao hơn một lần. Consumer phải:

- dùng idempotency key;
- ghi trạng thái xử lý;
- conditional write;
- tránh side effect lặp;
- delete message chỉ sau xử lý thành công;
- log message ID/correlation ID.

Không giả định ReceiveMessage một lần là xử lý một lần.

### 5.2. Best-effort ordering

Standard có thể trả message không đúng thứ tự gửi. Nếu order quan trọng, dùng FIFO hoặc thiết kế sequence/version ở application.

---

## 6. SQS FIFO

FIFO dùng khi cần ordering và deduplication trong phạm vi được hỗ trợ.

### 6.1. Message group

Ordering được bảo đảm trong cùng MessageGroupId. Các group khác nhau có thể xử lý song song.

~~~text
Group A: A1 → A2 → A3
Group B: B1 → B2 → B3
~~~

Nếu dùng một group duy nhất, throughput bị tuần tự hóa. Nếu cần scale, chọn group key phù hợp như customerId, orderId hoặc accountId.

FIFO không có nghĩa toàn queue chỉ có một thứ tự global trong mọi trường hợp; ordering gắn với message group.

### 6.2. Deduplication

FIFO có cơ chế deduplication theo deduplication ID/content-based setting trong thời gian dịch vụ quy định. Vẫn nên thiết kế application idempotent vì:

- side effect có thể xảy ra trước response timeout;
- retry từ client;
- downstream có thể duplicate;
- deduplication scope không thay thế business idempotency lâu dài.

### 6.3. FIFO use case

- cập nhật trạng thái order theo thứ tự;
- xử lý giao dịch theo customer/account;
- command cần sequence;
- workflow yêu cầu không xử lý song song trong cùng entity.

Nếu chỉ cần throughput cao và duplicate xử lý được, Standard thường đơn giản hơn.

---

## 7. Visibility timeout

Visibility timeout là khoảng thời gian message tạm ẩn sau khi consumer nhận.

~~~text
Receive → message invisible → process → Delete
                         └── timeout trước Delete → message visible lại
~~~

### 7.1. Chọn timeout

Visibility timeout nên lớn hơn thời gian xử lý tối đa hợp lý, có buffer cho network/delete. Nếu quá ngắn:

- message xuất hiện lại khi consumer còn xử lý;
- duplicate processing;
- nhiều worker cùng xử lý;
- downstream bị quá tải.

Nếu quá dài:

- worker chết làm message bị ẩn lâu;
- retry recovery chậm;
- DLQ chuyển chậm.

Dùng ChangeMessageVisibility/heartbeat cho job dài thay vì đặt timeout cực lớn cho mọi message.

### 7.2. Lambda với SQS

Khi Lambda đọc SQS qua event source mapping, visibility timeout của queue phải phù hợp với Lambda timeout và retry behavior. Thực hành phổ biến là visibility timeout lớn hơn tổng processing timeout có buffer.

Cấu hình batch size, maximum batching window, reserved concurrency và partial batch response để tránh một message lỗi làm retry cả batch nếu integration hỗ trợ.

---

## 8. Long polling, short polling và batching

### Long polling

Long polling chờ tối đa thời gian cấu hình để trả message nếu queue tạm rỗng. Lợi ích:

- giảm empty response;
- giảm API request/cost;
- giảm CPU/network của consumer.

### Short polling

Trả response ngay theo polling request. Có thể tạo nhiều empty response nếu queue ít message.

### Batching

Batch nhiều message trong một Receive/Delete hoặc Lambda invocation tăng throughput và giảm request cost, nhưng:

- một item lỗi có thể ảnh hưởng batch;
- thời gian xử lý batch dài hơn;
- memory/timeout tăng;
- cần partial batch failure/idempotency.

Chọn batch size theo processing time, downstream limit và retry granularity.

---

## 9. Message retention, delay và attributes

### Retention

Message retention xác định queue giữ message bao lâu. Retention không phải backup vô hạn; nếu consumer không xử lý trước hạn, message mất.

Chọn retention theo outage recovery, consumer downtime, replay need và cost.

### Delay queue

Delay queue làm message mới gửi invisible trong một khoảng thời gian trước khi consumer nhận. Dùng cho deferred processing đơn giản.

Delay message khác visibility timeout:

- delay áp dụng trước lần nhận đầu;
- visibility áp dụng sau khi receive.

### Message attributes

Attributes chứa metadata như event type, tenant, schema version hoặc trace ID. Đừng nhét payload lớn/secret không cần thiết vào attributes.

### Message size và payload lớn

Nếu payload vượt giới hạn hoặc muốn giảm queue cost, lưu object lớn ở S3 rồi gửi pointer/URI, checksum và metadata qua SQS. Consumer cần permission và xử lý object lifecycle.

---

## 10. Dead-letter queue

DLQ nhận message không xử lý được sau số lần receive/retry vượt maxReceiveCount.

### 10.1. Poison message

Poison message luôn lỗi do schema, data hoặc business rule. Retry vô hạn làm queue bị kẹt và lãng phí capacity.

DLQ giúp:

- tách message lỗi;
- giữ message để điều tra;
- alert;
- sửa consumer/data rồi redrive;
- bảo vệ healthy messages.

### 10.2. maxReceiveCount

maxReceiveCount là số lần message có thể được receive trước khi chuyển DLQ. Chọn theo:

- transient error probability;
- processing time;
- retry/backoff;
- downstream recovery;
- business side effect.

Không đặt quá thấp khiến lỗi tạm thời vào DLQ sớm; không đặt quá cao khiến poison message quay vòng quá lâu.

### 10.3. Redrive

Redrive message sau khi:

- xác định root cause;
- fix consumer/schema/permission;
- test với một phần message;
- theo dõi queue/DLQ;
- bảo đảm idempotency.

Không redrive toàn bộ DLQ mù quáng.

---

## 11. SQS encryption và access control

SQS hỗ trợ encryption at rest bằng SQS-managed key hoặc KMS key tùy cấu hình. Producer/consumer cần IAM permission đúng; dùng KMS có thể cần thêm key policy/grants.

Queue policy có thể:

- cho phép SNS topic gửi message;
- giới hạn source account/ARN;
- yêu cầu TLS;
- kiểm soát cross-account access.

SQS encryption at rest không thay thế application authorization hoặc secret management. Không gửi password/token nhạy cảm nếu không cần.

---

## 12. SNS

SNS là managed pub/sub notification service. Publisher gửi message vào topic; SNS fan-out tới nhiều subscription.

~~~text
Publisher → SNS Topic
              ├── SQS Queue A
              ├── SQS Queue B
              ├── Lambda
              └── HTTP/S hoặc target hỗ trợ
~~~

### 12.1. Use case

- broadcast event tới nhiều subscriber;
- notification;
- fan-out;
- decouple publisher khỏi consumer;
- gửi một event tới các queue/workflow khác nhau.

SNS không phải durable pull queue giống SQS. Nếu consumer cần xử lý độc lập, retry dài, backpressure hoặc durable buffering, subscribe SNS tới SQS.

### 12.2. SNS subscription

Subscription có thể tới SQS, Lambda, HTTP/S, email và service target hỗ trợ. Mỗi subscriber có delivery/retry behavior khác nhau.

SNS topic policy và SQS queue policy cần cho phép delivery. Khi SNS → SQS, queue phải trust đúng topic ARN và source condition.

### 12.3. Filter policy

Filter policy cho phép subscriber chỉ nhận message phù hợp attribute/body pattern.

Ví dụ:

~~~text
Topic OrderEvents
  Queue Billing: eventType = OrderPaid
  Queue Shipping: eventType = OrderPaid AND region = VN
  Queue Analytics: tất cả event
~~~

Filtering giúp giảm consumer work và chi phí. Producer phải gửi attribute/schema đúng; sai metadata có thể khiến event bị bỏ qua.

### 12.4. SNS message format

Khi SNS gửi tới SQS, payload có thể bọc trong SNS envelope theo cấu hình raw message delivery. Consumer phải biết format; đổi raw/envelope cần tương thích schema.

---

## 13. SNS → SQS fan-out

Pattern phổ biến:

~~~text
Order Service → SNS OrderCreated
                    ├── Billing Queue → Billing Worker
                    ├── Shipping Queue → Shipping Worker
                    └── Analytics Queue → Analytics Worker
~~~

Ưu điểm:

- publisher không biết consumer;
- mỗi consumer có queue, retry, visibility, DLQ riêng;
- consumer xử lý theo tốc độ riêng;
- một consumer lỗi không chặn consumer khác;
- dễ replay/redrive theo queue.

Nếu publisher gửi trực tiếp tới nhiều queue, coupling và permission/config phức tạp hơn.

### Fan-out consistency

SNS delivery có thể retry/duplicate. SQS consumer vẫn cần idempotent. Không coi fan-out là exactly-once end-to-end.

---

## 14. EventBridge

EventBridge là event bus serverless để nhận, lọc và route event tới target theo event pattern.

~~~text
Producer/Event Source → Event Bus → Rule → Target
                                      ├── Lambda
                                      ├── Step Functions
                                      ├── SQS/SNS
                                      └── AWS service/API
~~~

### 14.1. Event bus

- Default event bus nhận event từ AWS services.
- Custom event bus dành cho application/domain event.
- Partner event bus dùng integration từ partner hỗ trợ.
- Cross-account event bus có thể route event giữa account theo policy.

EventBridge phù hợp event schema có metadata source/detail-type/time/account/region và nhiều consumer cần filter theo pattern.

### 14.2. Event pattern

Rule match event fields như source, detail-type, detail, account, region hoặc array/value condition.

Ví dụ logic:

~~~json
{
  "source": ["com.shop.orders"],
  "detail-type": ["OrderCreated"],
  "detail": {
    "status": ["PAID"]
  }
}
~~~

Thiết kế event contract ổn định, version schema và tránh đưa secret/PII không cần thiết.

### 14.3. Target và retry

Rule route tới Lambda, Step Functions, SQS, SNS, API destination hoặc AWS service target hỗ trợ. EventBridge có retry/target failure behavior và có thể dùng DLQ cho failed invocation theo cấu hình.

Target phải idempotent. EventBridge không phải database event log vô hạn; dùng archive/replay hoặc event store phù hợp nếu cần lịch sử.

### 14.4. Archive và replay

Archive lưu event theo retention/filter để replay khi:

- thêm consumer mới;
- sửa bug consumer;
- rebuild projection;
- khôi phục sau incident;
- test workflow.

Replay có thể tạo duplicate side effects. Consumer phải idempotent và có cơ chế phân biệt replay nếu business cần.

### 14.5. EventBridge vs SNS

| Tiêu chí | SNS | EventBridge |
|---|---|---|
| Mô hình | topic pub/sub | event bus/rules |
| Filtering | subscription filter | event pattern mạnh |
| Schema | publisher-defined | event envelope/source/detail |
| Fan-out | rất phù hợp | phù hợp |
| AWS service events | không phải trọng tâm | mạnh |
| Archive/replay | không phải use case chính | có |
| Ordering | không phải guarantee global | không chọn vì ordering |
| Use case | notification/fan-out | event routing/integration |

---

## 15. SQS vs SNS vs EventBridge vs Step Functions

| Requirement | Dịch vụ |
|---|---|
| Buffer và worker xử lý | SQS |
| Broadcast một message | SNS |
| Fan-out có queue độc lập | SNS → SQS |
| Route event theo content | EventBridge |
| AWS service/application event bus | EventBridge |
| Workflow nhiều bước có state | Step Functions |
| Strict ordering trong group | SQS FIFO |
| Broker protocol migration | Amazon MQ |
| Replay event | EventBridge archive/replay hoặc event store |
| Backpressure | SQS |
| Notification đơn giản | SNS |

### Quy tắc nhanh

- Có một consumer xử lý từng message và cần buffer → SQS.
- Có nhiều subscriber nhận cùng event → SNS.
- Cần filter theo event metadata và target đa dạng → EventBridge.
- Cần validate/process/approval/retry theo sequence → Step Functions.
- Cần AMQP/MQTT/OpenWire hoặc broker semantics legacy → Amazon MQ.

Một architecture có thể dùng nhiều dịch vụ:

~~~text
API → EventBridge → Step Functions
                  ├── SNS → SQS Billing
                  └── SQS → Worker
~~~

---

## 16. Amazon MQ

Amazon MQ là managed message broker cho workload cần compatibility với broker protocol/API như ActiveMQ hoặc RabbitMQ.

Use case:

- migration ứng dụng legacy;
- JMS/AMQP/STOMP/MQTT/OpenWire compatibility;
- broker features như queue/exchange/transaction theo engine;
- giảm rewrite khi chuyển lên AWS.

So với SQS/SNS/EventBridge:

| Tiêu chí | Amazon MQ | SQS/SNS/EventBridge |
|---|---|---|
| Mục tiêu | compatibility với broker | cloud-native managed primitives |
| Vận hành | nhiều broker configuration hơn | serverless hơn |
| Scale | theo broker/engine | managed elasticity theo service |
| Migration | ít rewrite | có thể cần refactor |
| Chọn khi | protocol/semantics legacy | application mới/event-native |

Không chọn Amazon MQ chỉ vì “có message”; nếu application mới cần queue/topic cloud-native, SQS/SNS/EventBridge thường đơn giản hơn.

---

## 17. Reliability, retry và idempotency

### 17.1. Duplicate processing

Duplicate có thể đến từ:

- SQS Standard at-least-once;
- visibility timeout hết hạn;
- consumer timeout sau side effect;
- SNS/EventBridge retry;
- Lambda retry;
- redrive/replay;
- network response bị mất.

Consumer idempotency pattern:

- messageId/business idempotency key;
- conditional write;
- status table trong DynamoDB;
- transaction;
- dedup window;
- outbox/inbox pattern;
- side effect API hỗ trợ idempotency key.

### 17.2. Retry with backoff

Retry transient error với exponential backoff và jitter. Không retry permanent validation/auth/schema error.

Retry cần giới hạn:

- max attempts;
- total timeout;
- visibility timeout;
- DLQ;
- alarm;
- manual redrive.

### 17.3. Exactly-once

“Exactly-once” cần nói rõ scope:

- SQS FIFO deduplication không bảo đảm business side effect end-to-end;
- consumer vẫn có thể chạy lại sau timeout;
- SNS/EventBridge delivery không phải exactly-once toàn hệ thống;
- database conditional write/idempotency mới bảo vệ business invariant.

---

## 18. Observability

Metric quan trọng:

### SQS

- ApproximateNumberOfMessagesVisible;
- ApproximateNumberOfMessagesNotVisible;
- ApproximateAgeOfOldestMessage;
- NumberOfMessagesSent/Received/Deleted;
- empty receives;
- DLQ depth.

### SNS

- NumberOfMessagesPublished;
- delivery success/failure;
- subscription confirmation/failure;
- retry/delivery latency tùy protocol.

### EventBridge

- Invocations;
- FailedInvocations;
- ThrottledRules;
- matched events;
- DLQ depth;
- archive/replay metrics.

### Consumer

- processing duration;
- success/failure;
- retry count;
- duplicate count;
- business throughput;
- downstream latency.

Alarm AgeOfOldestMessage vì queue depth thấp vẫn có thể có một message bị kẹt lâu. DLQ depth > 0 thường cần investigation.

---

## 19. Lab bắt buộc

### Lab 1 — Producer → SQS → worker

1. Tạo SQS Standard queue.
2. Tạo producer gửi order message.
3. Tạo Lambda/EC2 worker poll queue.
4. Cấu hình long polling và batch.
5. Đặt visibility timeout lớn hơn processing time.
6. Delete message sau xử lý thành công.
7. Ghi metric queue depth/oldest age.

### Lab 2 — DLQ và redrive

- Tạo DLQ.
- Cấu hình redrive policy/maxReceiveCount.
- Làm worker fail có chủ đích.
- Quan sát message chuyển DLQ.
- Sửa lỗi rồi redrive từng phần.
- Kiểm tra consumer idempotency.

### Lab 3 — FIFO

- Tạo FIFO queue.
- Gửi message với MessageGroupId theo customerId.
- Gửi duplicate deduplication ID.
- Quan sát ordering trong cùng group.
- Tạo nhiều group để tăng parallelism.
- Ghi giới hạn của ordering.

### Lab 4 — SNS fan-out

~~~text
Publisher → SNS Topic
             ├── Queue Billing → Worker
             └── Queue Shipping → Worker
~~~

- Tạo hai SQS queue và DLQ riêng.
- Subscribe cả hai vào SNS.
- Cấu hình queue policy chỉ trust topic ARN.
- Gửi một OrderCreated.
- Xác nhận cả queue nhận event.
- Làm Billing fail và chứng minh Shipping vẫn hoạt động.

### Lab 5 — EventBridge rule

1. Gửi event OrderCreated lên custom/default event bus.
2. Tạo rule filter status=PAID.
3. Route tới SQS/Lambda/Step Functions.
4. Gửi event không match.
5. Cấu hình retry/DLQ.
6. Tạo archive/replay nếu môi trường cho phép.
7. Test duplicate/replay và idempotency.

---

## 20. Bẫy đề thi SAA-C03

- Visibility timeout quá ngắn gây duplicate processing.
- SQS Standard at-least-once, không tự exactly-once.
- FIFO ordering nằm trong MessageGroupId, không phải toàn queue.
- FIFO một group duy nhất có thể giới hạn throughput.
- Deduplication không thay thế business idempotency.
- Delete message trước khi xử lý xong có thể làm mất message.
- Delete quá muộn gây message xuất hiện lại.
- Long polling giảm empty receive, không thay visibility timeout.
- DLQ cần maxReceiveCount và retry strategy phù hợp.
- SQS là durable pull-based queue; SNS là pub/sub push/fan-out.
- SNS → SQS cần subscription và queue policy đúng.
- SNS filter sai attribute có thể làm subscriber không nhận event.
- EventBridge route theo event pattern, không phải durable work queue như SQS.
- EventBridge replay có thể tạo duplicate side effects.
- Step Functions là workflow state machine, không phải queue.
- Amazon MQ dùng cho broker protocol/migration, không phải lựa chọn mặc định cho application mới.
- Consumer phải idempotent dù dùng FIFO, SNS hay EventBridge.
- Queue depth thấp không đồng nghĩa khỏe; xem age of oldest message.
- Retry permanent error làm poison message quay vòng.
- Payload lớn nên lưu S3 và gửi pointer qua queue/event.
- Ordering và deduplication có scope; đọc kỹ đề requirement.

---

## 21. Câu hỏi tự luyện

### Câu 1

Producer có traffic burst, worker xử lý chậm và có thể scale độc lập. Chọn gì?

**Đáp án:** SQS làm buffer giữa producer và worker.

### Câu 2

Một event cần gửi tới Billing, Shipping và Analytics, mỗi consumer có tốc độ/retry riêng. Thiết kế nào?

**Đáp án:** SNS topic fan-out tới một SQS queue riêng cho mỗi consumer.

### Câu 3

Cần route AWS/application event theo source, detail-type và field status tới nhiều target. Chọn gì?

**Đáp án:** EventBridge event bus và rule event pattern.

### Câu 4

Order của cùng một customer phải xử lý đúng thứ tự nhưng customer khác có thể xử lý song song. Chọn gì?

**Đáp án:** SQS FIFO, MessageGroupId là customerId.

### Câu 5

SQS worker xử lý 120 giây nhưng visibility timeout là 30 giây. Rủi ro?

**Đáp án:** message visible lại trước khi xử lý xong, worker khác nhận duplicate. Tăng timeout/heartbeat và làm consumer idempotent.

### Câu 6

Message lỗi do schema không hợp lệ và retry mãi. Dùng gì?

**Đáp án:** DLQ với maxReceiveCount; sửa schema/consumer rồi điều tra/redrive có kiểm soát.

### Câu 7

Consumer hoàn tất charge nhưng timeout trước khi delete message. Retry sẽ gây gì?

**Đáp án:** duplicate charge nếu không có idempotency key/conditional record. Cần bảo vệ side effect ở payment/business layer.

### Câu 8

Một subscriber chỉ cần OrderPaid ở region VN. Dùng gì?

**Đáp án:** SNS subscription filter policy hoặc EventBridge event pattern tùy event bus/target requirement.

### Câu 9

Cần workflow validate → reserve stock → charge → notify, mỗi bước có retry/catch và trạng thái. Chọn gì?

**Đáp án:** Step Functions; queue/event có thể dùng cho integration nhưng không thay workflow state machine.

### Câu 10

Ứng dụng legacy dùng JMS/AMQP và muốn migration ít rewrite. Chọn gì?

**Đáp án:** Amazon MQ phù hợp hơn cloud-native rewrite sang SQS/SNS.

### Câu 11

Cần replay event sau khi thêm consumer mới. Chọn gì?

**Đáp án:** EventBridge archive/replay hoặc event store phù hợp, đồng thời consumer phải idempotent.

### Câu 12

Queue depth thấp nhưng một message chờ rất lâu. Metric nào cần xem?

**Đáp án:** ApproximateAgeOfOldestMessage; depth trung bình không phản ánh stuck/poison message.

---

## 22. Checklist hoàn thành tuần

### Kiến thức

- [ ] Giải thích lợi ích và trade-off của decoupling.
- [ ] Phân biệt queue, topic, event bus và workflow.
- [ ] Phân biệt SQS Standard/FIFO.
- [ ] Hiểu visibility timeout, long polling, retention và delay.
- [ ] Thiết kế DLQ, maxReceiveCount và redrive.
- [ ] Hiểu MessageGroupId, ordering và deduplication.
- [ ] Phân biệt SNS, subscription, filter policy và fan-out.
- [ ] Thiết kế SNS → SQS.
- [ ] Hiểu EventBridge bus, rule, target, event pattern và archive/replay.
- [ ] Chọn SQS/SNS/EventBridge/Step Functions đúng requirement.
- [ ] Nhận biết Amazon MQ cho migration.
- [ ] Thiết kế idempotent consumer.
- [ ] Theo dõi age of oldest message và DLQ.

### Thực hành

- [ ] Đã tạo producer → SQS → Lambda/EC2 worker.
- [ ] Đã cấu hình visibility timeout lớn hơn processing time.
- [ ] Đã tạo DLQ và thử redrive.
- [ ] Đã tạo SNS fan-out tới hai SQS queue.
- [ ] Đã tạo EventBridge rule lọc OrderCreated.
- [ ] Đã test FIFO group/deduplication.
- [ ] Đã test duplicate event và idempotency.
- [ ] Đã tạo alarm cho queue age/DLQ depth.
- [ ] Đã dọn queue, topic, rule, archive và test resource sau lab.

Bạn sẵn sàng sang Tuần 11 khi có thể chọn đúng primitive messaging theo buffer/broadcast/routing/workflow, thiết kế consumer idempotent và giải thích failure path từ retry tới DLQ.

---

## 23. Tài liệu tham khảo

- [Amazon SQS Developer Guide](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/welcome.html) — queue, visibility và delivery.
- [Amazon SQS standard and FIFO queues](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-which-queue.html) — chọn Standard/FIFO.
- [Amazon SQS visibility timeout](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-visibility-timeout.html) — processing/retry behavior.
- [Amazon SQS dead-letter queues](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-dead-letter-queues.html) — DLQ và redrive.
- [Amazon SNS Developer Guide](https://docs.aws.amazon.com/sns/latest/dg/welcome.html) — topic/subscription/fan-out.
- [SNS subscription filter policies](https://docs.aws.amazon.com/sns/latest/dg/sns-message-filtering.html) — filtering.
- [Amazon EventBridge User Guide](https://docs.aws.amazon.com/eventbridge/latest/userguide/eb-what-is.html) — event bus/rule/target.
- [EventBridge event patterns](https://docs.aws.amazon.com/eventbridge/latest/userguide/eb-event-patterns.html) — pattern matching.
- [EventBridge archive and replay](https://docs.aws.amazon.com/eventbridge/latest/userguide/eb-archive.html) — replay event.
- [AWS Step Functions Developer Guide](https://docs.aws.amazon.com/step-functions/latest/dg/welcome.html) — workflow orchestration.
- [Amazon MQ](https://docs.aws.amazon.com/amazon-mq/latest/developer-guide/what-is-amazon-mq.html) — managed broker/migration.


# Tuần 8 — Retry, TTL, DLX và DLQ

> Retry không phải là gửi lại message vô hạn. Một hệ thống retry tốt phải biết lỗi nào có thể tự hết, lỗi nào không thể sửa bằng retry, message đã thử bao nhiêu lần, retry tiếp theo lúc nào và khi nào cần chuyển sang DLQ để con người điều tra.

## 1. Kết quả cần đạt

Sau bài học, bạn có thể:

- Phân loại lỗi transient, permanent và poison message.
- Giải thích dead-letter exchange, dead-letter routing key và dead-letter queue.
- Phân biệt message TTL, queue TTL và queue expiration.
- Dựng retry 3 tầng với delay 5 giây, 30 giây và 5 phút.
- Dùng exponential backoff, jitter và giới hạn retry.
- So sánh nack/requeue trực tiếp với application republish vào retry exchange.
- Lưu error metadata, original routing key, exception class và retry count.
- Trace message từ queue chính qua retry tới DLQ.
- Viết replay từng message hoặc theo batch có kiểm soát.
- Ngăn poison message làm queue nóng liên tục.
- Tránh duplicate và retry loop khi replay DLQ.

## 2. Vì sao retry cần thiết kế?

Giả sử consumer gọi Shipping Provider:

~~~text
OrderCreated -> consumer -> Shipping Provider
~~~

Provider tạm thời timeout. Nếu bỏ message ngay, công việc bị mất. Nếu requeue vô hạn:

~~~text
consume -> timeout -> requeue
  -> consume -> timeout -> requeue
  -> lặp không giới hạn
~~~

Retry cần trả lời:

1. Lỗi có khả năng tự hết không?
2. Chờ bao lâu trước lần thử tiếp theo?
3. Thử tối đa bao nhiêu lần?
4. Retry bằng cách nào?
5. Message lỗi sẽ đi đâu sau khi hết retry?
6. Ai được cảnh báo?
7. Replay có thể tạo duplicate side effect không?

## 3. Phân loại lỗi

### 3.1 Transient error

Lỗi tạm thời, retry có thể thành công:

- HTTP 503;
- connection timeout;
- database unavailable trong thời gian ngắn;
- rate limit;
- service downstream đang restart;
- temporary network failure.

Hành động thường dùng:

~~~text
retry có delay + giới hạn số lần
~~~

### 3.2 Permanent error

Lỗi không tự hết nếu gửi lại cùng payload:

- business rule không hợp lệ;
- customer không tồn tại;
- currency không hỗ trợ;
- order đã ở trạng thái không thể chuyển;
- quyền bị từ chối vĩnh viễn.

Hành động:

~~~text
không retry vô hạn
-> đưa DLQ hoặc error workflow
~~~

### 3.3 Poison message

Poison message là message luôn làm consumer fail hoặc crash:

- JSON malformed;
- field bắt buộc bị thiếu;
- schema version không hỗ trợ;
- payload quá lớn;
- bug trong handler với một giá trị đặc biệt;
- dữ liệu làm vòng lặp hoặc exception deterministic.

Poison message cần bị tách ra nhanh để không chặn những message lành mạnh khác.

### 3.4 Ma trận lỗi

| Loại lỗi | Retry? | Delay? | DLQ? |
|---|---:|---:|---:|
| HTTP 503 | Có | Có | Sau giới hạn |
| Timeout ngắn | Có | Có | Sau giới hạn |
| JSON sai | Không hoặc rất ít | Không | Có |
| Thiếu field | Không | Không | Có |
| Rate limit | Có | Có, backoff | Sau giới hạn |
| Business rejected | Không | Không | Có/error workflow |
| Worker bug deterministic | Có giới hạn | Có | Có |

Đừng quyết định chỉ dựa vào tên exception. Cần phân loại theo khả năng thành công của cùng một message ở lần sau.

## 4. DLX và DLQ

### 4.1 Dead-letter exchange

Dead-letter exchange, viết tắt DLX, là exchange nhận message khi message bị dead-letter bởi queue.

Message có thể bị dead-letter khi:

- consumer reject/nack với requeue=false;
- message hết TTL;
- queue vượt giới hạn;
- một số policy/overflow condition của queue.

DLX chỉ là exchange. Muốn giữ message, phải có queue bind vào DLX.

### 4.2 Dead-letter queue

DLQ là queue dùng để giữ message lỗi:

~~~text
orders.dlx (direct)
  └── orders.dlq
~~~

Tên DLQ không tự tạo semantics. Một queue chỉ là DLQ vì topology và vận hành của team coi nó là nơi chứa message lỗi.

### 4.3 Topology gợi ý

~~~text
orders.exchange
  └── orders.process.queue
        |
        | lỗi tạm thời
        v
orders.retry.exchange
  ├── orders.retry.5s.queue
  ├── orders.retry.30s.queue
  └── orders.retry.5m.queue

dead-letter sau retry limit
  -> orders.dlx
       -> orders.dlq
~~~

Một topology đầy đủ cần xác định hướng quay lại queue chính:

~~~text
orders.retry.5s.queue
  -- TTL hết hạn + DLX -->
orders.exchange -> orders.process.queue
~~~

### 4.4 DLX không tự retry

DLX chỉ route message theo cấu hình. Nó không tự biết:

- lỗi transient hay permanent;
- retry lần thứ mấy;
- delay bao lâu;
- có nên replay không.

Application hoặc topology retry phải chịu trách nhiệm các quyết định đó.

## 5. TTL

### 5.1 Message TTL

Message TTL là thời gian message được phép tồn tại trong queue. Có thể đặt theo message property hoặc queue policy/config.

Ý nghĩa:

~~~text
message vào queue tại t0
TTL = 5 giây
message hết hạn sau khoảng thời gian TTL
~~~

TTL không phải scheduler thời gian chính xác. Message hết hạn có thể chỉ được xử lý khi broker kiểm tra nó, thường chịu ảnh hưởng bởi vị trí message trong queue và workload.

### 5.2 Queue message TTL

Queue có thể đặt x-message-ttl:

~~~java
Map<String, Object> arguments = new HashMap<>();
arguments.put("x-message-ttl", 5_000);

channel.queueDeclare(
        "orders.retry.5s.queue",
        true,
        false,
        false,
        arguments
);
~~~

Mọi message trong queue chịu TTL mặc định đó nếu không có policy khác.

### 5.3 Message-specific TTL

Producer có thể đặt expiration cho message:

~~~java
AMQP.BasicProperties properties =
        new AMQP.BasicProperties.Builder()
                .expiration("5000")
                .build();
~~~

Giá trị expiration trong AMQP Java client thường là string milliseconds.

Message-specific TTL và queue TTL có thể cùng tồn tại; policy hiệu lực cần được kiểm tra trong version/config thực tế.

### 5.4 Queue expiration khác message TTL

Phân biệt:

~~~text
message TTL   = message sống bao lâu trong queue
queue expires = queue idle bao lâu trước khi bị xóa
~~~

Queue expiration không phải retry delay. Không dùng x-expires để làm message retry.

### 5.5 TTL không phải delayed-message scheduler chính xác

Retry bằng TTL queue có các giới hạn:

- expired message có thể không rời queue đúng từng millisecond;
- message đứng sau message chưa hết TTL có thể bị ảnh hưởng bởi head-of-line;
- queue retry có thể phình;
- ordering thay đổi;
- delay dài giữ message trong queue;
- cần DLX để route message khi hết TTL.

Nếu cần scheduling chính xác hoặc nhiều lịch phức tạp, cân nhắc delayed-message mechanism, scheduler bên ngoài hoặc thiết kế retry khác. Đừng gọi TTL queue là timer chính xác.

## 6. Retry topology 3 tầng

Mục tiêu:

~~~text
lần 0: queue chính
lần 1: retry 5s
lần 2: retry 30s
lần 3: retry 5m
sau đó: DLQ
~~~

Topology:

~~~text
orders.exchange
  └── orders.process.queue

orders.retry.exchange
  ├── orders.retry.5s.queue  -- TTL 5s  --+
  ├── orders.retry.30s.queue -- TTL 30s -+--> orders.exchange
  └── orders.retry.5m.queue  -- TTL 5m --+

orders.dlx
  └── orders.dlq
~~~

Mỗi retry queue cần dead-letter exchange trỏ về exchange tiếp theo hoặc exchange chính.

### 6.1 Retry 5 giây bằng Java client

~~~java
Map<String, Object> retryArguments = new HashMap<>();
retryArguments.put("x-message-ttl", 5_000);
retryArguments.put(
        "x-dead-letter-exchange",
        "orders.exchange"
);
retryArguments.put(
        "x-dead-letter-routing-key",
        "orders.process"
);

channel.queueDeclare(
        "orders.retry.5s.queue",
        true,
        false,
        false,
        retryArguments
);

channel.queueBind(
        "orders.retry.5s.queue",
        "orders.retry.exchange",
        "retry.5s"
);
~~~

Khi message hết TTL, broker dead-letter message về orders.exchange với routing key orders.process.

### 6.2 Retry 30 giây và 5 phút

~~~java
Map<String, Object> retry30Arguments = new HashMap<>();
retry30Arguments.put("x-message-ttl", 30_000);
retry30Arguments.put(
        "x-dead-letter-exchange",
        "orders.exchange"
);
retry30Arguments.put(
        "x-dead-letter-routing-key",
        "orders.process"
);

Map<String, Object> retry5mArguments = new HashMap<>();
retry5mArguments.put("x-message-ttl", 300_000);
retry5mArguments.put(
        "x-dead-letter-exchange",
        "orders.exchange"
);
retry5mArguments.put(
        "x-dead-letter-routing-key",
        "orders.process"
);
~~~

Khai báo queue và binding tương ứng. Không dùng chung một queue nếu cần delay khác nhau.

## 7. Retry bằng application republish

Thay vì nack requeue trực tiếp, consumer có thể publish message sang retry exchange rồi ack message gốc.

~~~text
main queue
  -> consumer phát hiện transient error
  -> publish sang retry queue
  -> confirm publish retry thành công
  -> ack message gốc
~~~

Thứ tự cực kỳ quan trọng:

~~~text
publish retry thành công
  -> ack original
~~~

Nếu ack original trước khi publish retry, message có thể mất. Nếu publish retry xong nhưng chưa ack original rồi process crash, message có thể duplicate. Consumer retry phải idempotent và retry publish nên dùng publisher confirm.

### 7.1 Ưu điểm

- chủ động chọn delay queue;
- thêm error headers;
- tăng retry count;
- đổi routing key;
- giữ error metadata;
- phân loại transient/permanent trong application.

### 7.2 Trade-off

- có hai operation broker cần phối hợp;
- publish retry và ack original không cùng transaction;
- có thể duplicate;
- cần confirm cho retry publish;
- cần idempotency;
- code phức tạp hơn nack requeue.

### 7.3 Pseudocode

~~~text
on message:
  try process
    ack original
  catch transient:
    if retryCount < max:
      publish retry with incremented retryCount
      wait for publish confirm
      ack original
    else:
      publish DLQ/error message
      wait for confirm
      ack original
  catch permanent:
    publish DLQ/error message
    wait for confirm
    ack original
~~~

Nếu publish retry/DLQ không confirm, không nên ack original như thể message đã được bảo toàn.

## 8. Retry bằng nack/requeue trực tiếp

~~~java
channel.basicNack(
        deliveryTag,
        false,
        true
);
~~~

Ưu điểm:

- code đơn giản;
- message quay lại queue nhanh;
- không cần retry exchange.

Nhược điểm:

- không có delay tự nhiên;
- dễ hot loop;
- khó tăng retry count rõ ràng;
- dễ làm poison message chiếm consumer;
- khó phân biệt retry lần nào;
- có thể phá fairness.

Dùng requeue trực tiếp chỉ cho lỗi rất ngắn và có guard. Với retry thực tế, republish vào retry queue hoặc dùng framework retry policy có giới hạn.

## 9. Retry count và metadata

### 9.1 Header x-retry-count

Có thể lưu retry count:

~~~text
x-retry-count: 0
x-retry-count: 1
x-retry-count: 2
~~~

Khi republish, tăng count:

~~~java
int nextRetry = currentRetry + 1;

MessageProperties properties =
        new MessageProperties();
properties.setHeader("x-retry-count", nextRetry);
properties.setHeader(
        "x-original-routing-key",
        originalRoutingKey
);
~~~

Tên header phải thống nhất. Không tin mù quáng header do external producer gửi; validate upper bound.

### 9.2 Error metadata

Message retry/DLQ nên có:

~~~text
x-retry-count
x-first-failed-at
x-last-failed-at
x-error-type
x-error-message
x-original-exchange
x-original-routing-key
x-consumer
x-trace-id
~~~

Không nhét stack trace đầy đủ hoặc secret vào header/payload. Error message có thể chứa PII hoặc credential nếu exception không được sanitize.

### 9.3 RabbitMQ x-death metadata

Khi message bị dead-letter, RabbitMQ có thể thêm thông tin x-death vào headers. Metadata này hữu ích để trace nhưng:

- format cần kiểm tra theo broker/client version;
- có thể có nhiều entry;
- không nên là retry counter duy nhất của application;
- replay cần hiểu message đã đi qua queue nào.

Dùng x-death cùng application metadata để điều tra rõ hơn.

## 10. Dead-letter queue

### 10.1 Cấu hình DLX cho queue chính

~~~java
Map<String, Object> processArguments = new HashMap<>();
processArguments.put(
        "x-dead-letter-exchange",
        "orders.dlx"
);
processArguments.put(
        "x-dead-letter-routing-key",
        "orders.failed"
);

channel.queueDeclare(
        "orders.process.queue",
        true,
        false,
        false,
        processArguments
);
~~~

DLX:

~~~java
channel.exchangeDeclare(
        "orders.dlx",
        BuiltinExchangeType.DIRECT,
        true
);

channel.queueDeclare(
        "orders.dlq",
        true,
        false,
        false,
        null
);

channel.queueBind(
        "orders.dlq",
        "orders.dlx",
        "orders.failed"
);
~~~

### 10.2 Dùng policy thay arguments khi phù hợp

Hardcode x-dead-letter-exchange trong queue declaration có thể làm thay đổi policy khó hơn vì queue argument là thuộc tính declaration. Production thường cân nhắc RabbitMQ policy để cập nhật DLX linh hoạt hơn.

Dù dùng code arguments hay policy, phải document owner và test topology.

### 10.3 DLQ không phải thùng rác

DLQ cần:

- monitor queue depth;
- alert khi có message;
- lưu error metadata;
- phân quyền đọc/replay;
- retention policy;
- runbook điều tra;
- replay có dry-run/rate limit;
- tránh replay vòng lặp về cùng lỗi.

## 11. Replay DLQ

Replay là lấy message từ DLQ và gửi lại exchange/queue có kiểm soát.

### Replay an toàn cần

- xác định nguyên nhân đã được sửa;
- chọn message cụ thể hoặc batch nhỏ;
- giới hạn tốc độ;
- đổi routing key nếu cần;
- tăng replay attempt;
- giữ original eventId;
- thêm replayId;
- có idempotency;
- không replay tự động vô hạn;
- kiểm tra kết quả sau replay.

### Replay message

~~~text
DLQ
  -> operator chọn message
  -> validate error fixed
  -> publish main exchange
  -> publisher confirm
  -> chỉ ack/xóa DLQ message sau khi republish confirm
~~~

Nếu republish thất bại, giữ message trong DLQ. Không xóa trước khi biết message đã được publish lại.

### Replay duplicate

Replay cùng eventId có thể chạy side effect lần hai. Consumer cần deduplication hoặc business operation idempotent.

## 12. Exponential backoff và jitter

Retry delay có thể tăng:

~~~text
delay(n) = base * 2^n
~~~

Ví dụ base 5 giây:

~~~text
retry 1: 5s
retry 2: 10s
retry 3: 20s
retry 4: 40s
~~~

Topology roadmap dùng 5s, 30s, 5m để dễ quan sát. Production cần chọn delay dựa trên downstream recovery time.

Jitter thêm ngẫu nhiên:

~~~text
actualDelay = plannedDelay + random(0, jitter)
~~~

Jitter tránh hàng nghìn message retry cùng một thời điểm sau khi downstream vừa hồi phục.

Không retry nhanh đồng loạt:

~~~text
10000 message fail lúc 10:00:00
10000 message retry cùng lúc 10:00:05
downstream lại quá tải
~~~

## 13. Spring AMQP retry concept

Spring listener có thể kết hợp error handler/retry interceptor. Ví dụ khái niệm:

~~~java
@Bean
public RetryOperationsInterceptor retryInterceptor() {
    return RetryInterceptorBuilder
            .stateless()
            .maxAttempts(3)
            .backOffOptions(
                    5_000,
                    2.0,
                    30_000
            )
            .recoverer(recoverer())
            .build();
}
~~~

Cấu hình cụ thể phụ thuộc Spring AMQP version và cách dùng recoverer. Không bật retry mà không xác định:

- exception nào retry;
- max attempts;
- backoff;
- recoverer;
- requeue behavior;
- DLQ route;
- idempotency.

Một recoverer có thể republish failed message với error metadata sang exchange lỗi. Sau đó original delivery được xử lý theo container policy.

## 14. Phân biệt retry broker và retry application

### Broker requeue

~~~text
consumer nack/reject requeue
  -> broker đưa message lại queue
~~~

Đặc điểm:

- không cần application publish message mới;
- không có delay tự nhiên;
- có thể hot loop;
- message identity giữ nguyên;
- retry count khó quản lý nếu không có metadata.

### Application republish

~~~text
consumer
  -> publish message retry mới
  -> thêm header/retry metadata
  -> confirm
  -> ack original
~~~

Đặc điểm:

- có thể chọn delay queue;
- có thể thay routing key;
- có thể thêm metadata;
- có thể tạo duplicate;
- cần confirm và idempotency.

Ghi rõ retry mode trong README và metric. Đừng trộn hai mode khiến retry count khó giải thích.

## 15. Lab 1 — Retry 3 tầng bằng TTL và DLX

### Mục tiêu

Dựng:

~~~text
orders.process.queue
  -> retry.5s.queue
  -> retry.30s.queue
  -> retry.5m.queue
  -> orders.dlq
~~~

Thực hiện:

1. Tạo main exchange/queue.
2. Tạo retry exchange.
3. Tạo ba retry queue với TTL 5s, 30s, 5m.
4. Cấu hình DLX/routing key cho từng queue.
5. Tạo DLX và DLQ.
6. Publish message làm consumer transient fail.
7. Ghi timestamp mỗi lần message vào queue.
8. Kiểm tra x-death và x-retry-count nếu có.
9. Sau giới hạn, kiểm tra message vào DLQ.

Bảng trace:

| Step | Exchange | Queue | Time | Retry count | Error |
|---|---|---|---|---:|---|
| 0 | orders.exchange | process | | 0 | |
| 1 | retry.exchange | retry.5s | | 1 | |
| 2 | retry.exchange | retry.30s | | 2 | |
| 3 | retry.exchange | retry.5m | | 3 | |
| 4 | orders.dlx | orders.dlq | | max | |

## 16. Lab 2 — Chặn retry vô hạn

Tạo consumer luôn fail với một message.

Thực hiện:

1. Gắn x-retry-count=0.
2. Mỗi lần republish tăng count.
3. Đặt max retry bằng 3.
4. Khi count đạt max, publish DLQ message.
5. Ack original chỉ sau khi publish DLQ confirm.
6. Kiểm tra queue chính không nóng liên tục.
7. Kiểm tra DLQ có đúng một message cuối cùng.

Tiêu chí:

~~~text
retry count hữu hạn
main queue không loop vô hạn
DLQ có error metadata
original message không bị ack sớm
~~~

## 17. Lab 3 — Error payload vào DLQ

Tạo payload lỗi:

~~~json
{
  "failedMessage": {
    "eventId": "evt-100",
    "orderId": "order-100"
  },
  "error": {
    "type": "InvalidOrderState",
    "message": "Order is already cancelled"
  },
  "metadata": {
    "retryCount": 0,
    "originalExchange": "orders.exchange",
    "originalRoutingKey": "order.process",
    "consumer": "order-worker",
    "failedAt": "2026-10-04T10:00:00Z"
  }
}
~~~

Có thể đặt metadata trong headers, payload error envelope hoặc cả hai. Chọn một convention và document.

Không đưa stack trace đầy đủ, token hoặc dữ liệu nhạy cảm vào DLQ.

## 18. Lab 4 — Replay từng message và theo batch

### Replay một message

1. Lấy message từ DLQ.
2. Kiểm tra error type.
3. Kiểm tra version/payload.
4. Xác nhận bug đã sửa.
5. Publish lại main exchange.
6. Chờ publisher confirm.
7. Chỉ sau đó ack message trong DLQ.

### Replay batch

1. Chạy dry-run.
2. Chọn batch nhỏ.
3. Rate limit publish.
4. Theo dõi main queue, retry queue và DLQ.
5. Dừng nếu redelivery/error rate tăng.
6. Không replay tất cả khi chưa biết root cause.

Replay command nên có:

~~~text
--message-id
--limit
--rate-per-second
--dry-run
--target-exchange
--target-routing-key
--operator
~~~

Replay cần audit log để biết ai, khi nào và vì sao đã phát lại message.

## 19. Lab 5 — Poison message không làm queue nóng

1. Tạo poison message luôn làm consumer exception.
2. Chạy consumer với retry max 3.
3. Đo processing rate và CPU/log rate.
4. Kiểm tra message đi DLQ.
5. Kiểm tra message bình thường phía sau vẫn xử lý được.
6. Thử cấu hình requeue vô hạn trong môi trường lab riêng.
7. So sánh queue behavior và ghi vì sao không được dùng trong production.

## 20. TTL và ordering

TTL queue có thể tạo ảnh hưởng ordering:

~~~text
retry queue:
  message A TTL 5m ở đầu
  message B TTL 5s ở sau
~~~

Tùy cách broker xử lý expired message và vị trí trong queue, B có thể không được xử lý đúng như một scheduler độc lập. Vì vậy:

- không dùng một retry queue chung cho nhiều delay nếu ordering/timing nghiêm ngặt;
- chia queue theo delay;
- đo delay thực tế;
- không hứa SLA millisecond với TTL queue;
- cân nhắc scheduler/delayed mechanism nếu cần chính xác hơn.

TTL là công cụ thực dụng cho retry delay, không phải time-based workflow engine.

## 21. Lỗi thiết kế thường gặp

- Requeue mọi exception.
- Không giới hạn retry.
- Không lưu retry count.
- Ack original trước khi publish retry/DLQ thành công.
- Dùng TTL queue nhưng không có DLX quay lại main exchange.
- Cấu hình DLX nhưng không bind DLQ.
- Xóa DLQ message trước khi replay confirm.
- Replay toàn bộ DLQ sau khi sửa bug mà không rate limit.
- Không idempotent side effect.
- Dùng x-death như retry counter duy nhất.
- Đưa secret/PII/stack trace nhạy cảm vào error payload.
- Gọi TTL là delayed scheduler chính xác.
- Chỉ monitor main queue, bỏ qua retry queue và DLQ.

## 22. Checklist retry/DLQ

- [ ] Phân loại transient, permanent và poison error.
- [ ] Retry có max attempts.
- [ ] Retry có delay/backoff phù hợp.
- [ ] Có jitter nếu nhiều message retry đồng thời.
- [ ] Retry count được trace.
- [ ] Main queue không requeue vô hạn.
- [ ] Retry queue có TTL và DLX đúng.
- [ ] DLX có queue bind thực sự.
- [ ] Error metadata có original exchange/routing key.
- [ ] DLQ được monitor và alert.
- [ ] Replay có dry-run/rate limit.
- [ ] Replay chỉ xóa DLQ sau publish confirm.
- [ ] Consumer idempotent.
- [ ] Không đưa secret/PII vào error payload.
- [ ] Đo delay thực tế của TTL.
- [ ] Test poison message.

## 23. Deliverable cuối tuần

Tạo:

~~~text
rabbitmq/week-08/
├── README.md
├── diagrams/
│   └── retry-dlx-dlq.md
├── definitions.json
└── src/
    ├── RetryPublisher.java
    ├── RetryConsumer.java
    ├── DlqReplayCommand.java
    └── ErrorEnvelope.java
~~~

README cần có:

1. Sơ đồ main queue, retry queues, DLX và DLQ.
2. Bảng phân loại lỗi.
3. TTL của từng retry queue.
4. Retry count và metadata convention.
5. Trace một message qua 5s, 30s, 5m rồi DLQ.
6. So sánh nack/requeue với application republish.
7. Error payload/DLQ envelope.
8. Replay từng message và batch policy.
9. Kết quả poison-message lab.
10. Metric/alert cho retry queue và DLQ.
11. Giới hạn của TTL như delayed scheduler.
12. Giải thích cách tránh duplicate và retry loop.

## 24. Tiêu chí tự kiểm tra

1. Transient, permanent và poison error khác nhau thế nào?
2. DLX khác DLQ thế nào?
3. Vì sao DLX không tự tạo DLQ?
4. Message TTL khác queue expiration thế nào?
5. Vì sao TTL không phải scheduler chính xác?
6. Retry queue cần DLX để làm gì?
7. Nack requeue trực tiếp có hạn chế gì?
8. Khi application republish retry, ack original đặt ở đâu?
9. Vì sao retry publish cần publisher confirm?
10. Retry count nên lưu ở đâu?
11. x-death có nên là retry counter duy nhất không?
12. Vì sao poison message phải bị tách khỏi main queue?
13. DLQ replay vì sao phải rate limit?
14. Khi nào DLQ message được ack/xóa trong replay?
15. Retry có thể tạo duplicate như thế nào?
16. Vì sao consumer vẫn cần idempotency?
17. Retry 5s/30s/5m nên được trace bằng metadata nào?
18. Làm sao chứng minh message không retry vô hạn?

### Đáp án cần nhớ

> Retry phải hữu hạn và có phân loại lỗi. Nack/requeue đơn giản nhưng dễ hot loop; application republish cho phép delay, retry count và error metadata nhưng phải confirm publish trước khi ack original. TTL queue là công cụ delay thực dụng, không phải scheduler chính xác. DLX là exchange, DLQ là queue; DLQ phải được monitor và replay có kiểm soát để tránh duplicate hoặc retry loop.

## 25. Chuẩn bị cho Tuần 9

Tuần sau sẽ chuẩn hóa message contract và schema evolution:

- envelope, payload, eventId, eventType, eventVersion;
- occurredAt, correlationId, causationId và traceparent;
- backward compatibility;
- additive change và deprecated field;
- malformed JSON, missing field, enum và nullability;
- PII/secret không nên đưa vào message.

Giữ lại error envelope, retry headers và DLQ replay command của tuần này. Chúng sẽ giúp kiểm tra schema lỗi, version mismatch và cách consumer xử lý payload không tương thích.

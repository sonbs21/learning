# Tuần 2: Delivery semantics và failure model

## 1. Mục tiêu bài học

Sau bài này, bạn có thể:

- Phân biệt **at-most-once**, **at-least-once** và **exactly-once**.
- Tách bạch delivery guarantee, processing guarantee và business effect.
- Giải thích vì sao duplicate message xảy ra dù producer và consumer đều “đúng”.
- Phân tích các điểm lỗi khi producer publish, broker lưu trữ và consumer xử lý.
- Hiểu tác động của consumer crash, broker crash, network timeout và partial failure.
- Phân biệt lỗi tạm thời, lỗi vĩnh viễn và poison message.
- Thiết kế retry có giới hạn, exponential backoff và jitter.
- Biết khi nào dùng acknowledgement, deduplication, idempotency key và dead-letter queue.
- Thiết kế consumer theo nguyên tắc **at-least-once delivery + idempotent processing**.

> Bài này tiếp nối mental model của Tuần 1. RabbitMQ, Kafka và AWS SQS sẽ cung cấp các cơ chế khác nhau, nhưng failure model cơ bản vẫn cần được hiểu trước khi chọn cấu hình.

---

## 2. Vì sao delivery semantics quan trọng?

Giả sử consumer nhận message `PaymentCaptured` và thực hiện:

1. Gọi payment provider để trừ tiền.
2. Đánh dấu payment là `CAPTURED` trong database.

Nếu consumer crash sau bước 1 nhưng trước bước 2, message có thể được giao lại:

```text
Broker ──▶ Consumer
             ├── gọi payment provider thành công
             ├── process bị crash
             └── chưa ack message

Broker ──▶ Consumer lần 2
             └── có thể gọi payment provider lần nữa
```

Đây là khoảng thời gian giữa **side effect** và **acknowledgement**. Mọi hệ thống message cần một chiến lược cho khoảng thời gian này.

Delivery semantics trả lời:

> Một message được giao và được hệ thống cố gắng xử lý với mức đảm bảo nào?

Nó chưa tự trả lời:

- Handler có chạy side effect bao nhiêu lần?
- Database có ghi kết quả bao nhiêu lần?
- User có bị trừ tiền nhiều lần không?

Vì vậy cần phân biệt:

```text
Delivery guarantee
        ↓
Processing guarantee
        ↓
Business effect / side effect
```

---

## 3. Ba loại delivery semantics

### 3.1. At-most-once

At-most-once nghĩa là một message được giao **không quá một lần**. Đổi lại, message có thể bị mất.

Flow thường gặp:

```text
receive → ack → process
```

Nếu process crash sau ack, broker nghĩ message đã hoàn tất và không giao lại.

Ưu điểm:

- Giảm hoặc tránh duplicate.
- Throughput có thể cao.
- Logic retry đơn giản.

Nhược điểm:

- Có thể mất message.
- Không phù hợp với thanh toán, trừ tồn kho, tạo invoice hoặc tác vụ bắt buộc phải hoàn tất.

At-most-once có thể phù hợp với metric phụ, notification không quan trọng hoặc dữ liệu có thể tính lại từ nguồn khác.

### 3.2. At-least-once

At-least-once nghĩa là hệ thống cố gắng giao message ít nhất một lần. Nếu consumer chưa xác nhận thành công, message có thể được giao lại.

Flow thường gặp:

```text
receive → process → ack
```

Nếu consumer crash trước ack, broker có thể redeliver:

```text
Broker ──▶ Consumer
             ├── xử lý thành công hoặc đã tạo side effect
             ├── crash trước ack
             └── broker redeliver message
```

Ưu điểm:

- Giảm khả năng mất message do consumer crash.
- Phù hợp với command và event quan trọng.
- Cho phép retry lỗi tạm thời.

Nhược điểm:

- Duplicate delivery là hành vi bình thường.
- Consumer phải idempotent hoặc có deduplication.
- Side effect bên ngoài database có thể khó rollback.

At-least-once thường là lựa chọn thực tế cho backend, với điều kiện consumer xử lý duplicate an toàn.

### 3.3. Exactly-once

Exactly-once thường bị dùng với nhiều ý nghĩa. Cần tách ít nhất ba câu hỏi:

1. Message có được giao đúng một lần không?
2. Handler có được chạy đúng một lần không?
3. Business effect có xảy ra đúng một lần không?

Một hệ thống có thể đảm bảo xử lý một record đúng một lần trong một transaction nội bộ, nhưng vẫn không thể ngăn side effect bên ngoài bị gọi lại nếu process crash ở thời điểm không xác định.

Ví dụ:

```text
Consumer
  ├── DB transaction commit thành công
  ├── gọi Payment Provider
  └── crash trước khi lưu trạng thái provider response
```

Lần chạy lại có thể gọi provider thêm một lần. Transaction database không thể tự rollback một HTTP request đã gửi đến hệ thống bên ngoài.

Exactly-once effect thường cần phối hợp:

- Idempotency key ở downstream.
- Unique constraint hoặc deduplication store.
- Transactional outbox/inbox.
- Transaction hỗ trợ cả message offset và state cần cập nhật.
- API downstream có semantics idempotent.

> Không nên bắt đầu thiết kế bằng lời hứa “exactly-once”. Hãy xác định side effect nào cần idempotent, side effect nào có thể retry và mất message có chấp nhận được không.

---

## 4. Delivery, processing và effect

| Tầng | Câu hỏi | Ví dụ |
|---|---|---|
| Delivery | Broker giao message bao nhiêu lần? | Có thể redeliver nếu chưa ack |
| Processing | Handler chạy bao nhiêu lần? | `handle()` có thể chạy 2 lần |
| Effect | Business state thay đổi bao nhiêu lần? | Order chỉ chuyển `PAID` một lần |

Mục tiêu tốt thường là:

```text
At-least-once delivery
        +
Handler có thể chạy nhiều lần
        +
Business effect idempotent
        =
Không mất công việc và không nhân đôi kết quả
```

Ví dụ consumer nhận `OrderPaid` nhiều lần nhưng chỉ chuyển order từ `PENDING` sang `PAID` một lần. Các lần sau đọc thấy order đã `PAID` và kết thúc an toàn.

---

## 5. Failure model: các điểm có thể lỗi

Một message flow hoàn chỉnh có nhiều bước:

```text
Producer
  ├── serialize message
  ├── gửi qua network
  ▼
Broker
  ├── nhận và route message
  ├── lưu message
  └── gửi delivery
  ▼
Consumer
  ├── deserialize
  ├── xử lý business
  ├── gọi database/downstream
  └── gửi ack
```

Lỗi có thể xảy ra ở mọi mũi tên:

- Producer crash trước khi publish.
- Serialize lỗi hoặc payload không hợp lệ.
- Network ngắt khi publish.
- Broker nhận message nhưng chưa persist.
- Broker crash trước khi trả confirmation.
- Message route sai hoặc không có subscriber.
- Consumer nhận message nhưng process bị kill.
- Database timeout sau khi đã ghi một phần.
- Downstream trả lỗi hoặc trả chậm.
- Consumer xử lý xong nhưng ack bị mất trên network.

Trong hệ thống phân tán, “không nhận được response” không đồng nghĩa với “operation chưa xảy ra”. Đây là nguyên nhân chính của trạng thái **unknown outcome**.

---

## 6. Producer-side failures

### 6.1. Producer crash trước khi publish

```text
1. Order API ghi order vào database
2. Process crash
3. Chưa publish OrderCreated
```

Order tồn tại nhưng consumer không biết. Đây là lỗi **dual write** giữa database và broker.

Các hướng xử lý:

- Ghi business state và outbox record trong cùng database transaction.
- Một publisher riêng đọc outbox rồi gửi message.
- Có reconciliation job phát hiện record chưa publish.

Outbox Pattern sẽ được học ở phần nâng cao; ở tuần này chỉ cần nhận diện vấn đề.

### 6.2. Network timeout khi publish

Producer gửi message nhưng connection timeout trước khi nhận confirmation:

```text
Producer ── publish ──▶ Broker
   ▲                      │
   └── timeout ◀──────────┘
```

Có hai khả năng:

1. Broker chưa nhận message.
2. Broker đã nhận và lưu message nhưng response bị mất.

Producer không thể suy ra kết quả chỉ từ timeout. Retry mù quáng có thể tạo duplicate; không retry có thể làm mất message.

Giải pháp thường là:

- Dùng publisher confirmation nếu broker hỗ trợ.
- Gắn message ID duy nhất.
- Cho phép duplicate và deduplicate ở consumer.
- Dùng outbox để publisher retry có kiểm soát.

### 6.3. Publish thành công nhưng HTTP response thất bại

API publish thành công, sau đó process timeout trước khi trả response. Client retry request:

```text
Client ── POST /orders ──▶ API
                         ├── lưu order
                         ├── publish event thành công
                         ├── response bị mất
                         └── client retry
```

Nếu API không có idempotency key, có thể tạo hai order hoặc phát hai event. Idempotency không chỉ dành cho consumer; command HTTP cũng cần được xem xét.

---

## 7. Consumer-side failures

### 7.1. Crash trước khi xử lý

Message chưa tạo side effect và chưa ack. Với at-least-once, broker có thể giao lại. Đây là trường hợp tương đối dễ xử lý.

### 7.2. Crash sau side effect nhưng trước ack

```text
Broker ──▶ Consumer
             ├── gửi email thành công
             ├── process crash
             └── chưa ack

Broker ──▶ Consumer lần 2
             └── có thể gửi email lần nữa
```

Các hướng giảm rủi ro:

- Dùng idempotency key khi gọi provider nếu provider hỗ trợ.
- Lưu `messageId` hoặc operation ID đã xử lý trong database.
- Dùng unique constraint cho business key.
- Thiết kế side effect theo kiểu set state thay vì increment mù quáng.
- Chấp nhận duplicate với tác vụ không quan trọng.

### 7.3. Xử lý xong nhưng ack bị mất

```text
Consumer ── xử lý thành công ──▶ Database
Consumer ── ack ──X network failure
```

Broker không thấy ack và redeliver message. Ack ở cuối giúp giảm mất message, nhưng không loại bỏ duplicate. Handler vẫn cần idempotent.

### 7.4. Xử lý lâu hơn visibility timeout

Một số queue tạm ẩn message sau khi giao. Nếu consumer không hoàn tất hoặc không gia hạn trong thời gian đó, message có thể xuất hiện lại:

```text
visibility timeout = 30 giây
processing time    = 90 giây

0s  nhận message
30s message có thể được giao lại
90s consumer đầu tiên mới xong
```

Hậu quả là hai consumer có thể xử lý cùng message đồng thời. Cần:

- Đặt timeout lớn hơn thời gian xử lý dự kiến với margin.
- Gia hạn visibility khi xử lý dài.
- Làm handler idempotent.
- Đặt giới hạn processing time và dead-letter message quá khó.

---

## 8. Broker crash và durability

Nếu broker chỉ giữ message trong memory và bị crash, message có thể mất:

```text
Producer ──▶ Broker memory ──X broker crash
                         └── message mất
```

Durable infrastructure không đồng nghĩa durable message. Cần phân biệt:

- Broker hoặc queue tồn tại sau restart.
- Message được ghi bền vững.
- Producer nhận confirmation rằng message đã được xử lý.
- Replication đã hoàn tất.

Một queue durable nhưng message transient vẫn có thể mất sau restart. Persistent message trong queue không durable cũng không đảm bảo topology tồn tại sau restart.

```text
Durable topology
  + persistent message
  + publisher confirmation
  + replication / backup phù hợp
  ≠ exactly-once
```

Durability chủ yếu giảm nguy cơ **lost message**. Nó không loại bỏ duplicate hoặc lỗi business handler.

---

## 9. Network timeout và unknown outcome

Khi gọi dependency và bị timeout, kết quả có thể là:

| Khả năng | Operation đã xảy ra? |
|---|---|
| Request chưa rời khỏi client | Chưa |
| Request đến server nhưng server chưa xử lý | Có thể chưa |
| Server xử lý xong nhưng response mất | Có |
| Server xử lý một phần rồi timeout | Có thể |

Code sau rất nguy hiểm:

```java
try {
    paymentClient.capture(paymentId);
} catch (TimeoutException ex) {
    paymentClient.capture(paymentId); // retry mù quáng
}
```

Retry có thể tạo duplicate effect nếu lần gọi đầu đã thành công. Cần gửi cùng một idempotency key:

```java
paymentClient.capture(paymentId, "capture:" + paymentId);
```

Provider cần đảm bảo cùng một key trả về cùng một kết quả logic thay vì tạo thêm payment.

Nên phân biệt:

- `messageId`: định danh một message cụ thể; dùng cho trace và dedup.
- `correlationId`: liên kết nhiều message/HTTP request trong cùng flow.
- `idempotencyKey`: khóa để downstream không tạo effect mới cho cùng operation.

Ví dụ:

```text
messageId:       msg-001
correlationId:   checkout-789
idempotencyKey:  capture:payment-123
```

---

## 10. Partial failure

Partial failure nghĩa là một phần flow thành công, phần khác thất bại:

```text
Order Service
  ├── lưu order thành công
  ├── publish OrderCreated thành công
  ├── Email Consumer thành công
  ├── Analytics Consumer timeout
  └── Audit Consumer đang down
```

Không nên rollback toàn bộ order chỉ vì một consumer phụ thất bại nếu business không yêu cầu. Mỗi consumer nên có failure policy riêng:

| Consumer | Tác vụ | Chính sách gợi ý |
|---|---|---|
| Payment | Trừ tiền | Retry có kiểm soát, không bỏ message |
| Email | Gửi receipt | Retry, sau đó DLQ hoặc trạng thái cần gửi lại |
| Analytics | Ghi số liệu | Có thể replay hoặc chấp nhận trễ |
| Audit | Ghi lịch sử | Thường cần giữ, cần alert mạnh |

Failure policy phải phản ánh giá trị nghiệp vụ, không dùng một chính sách giống nhau cho mọi message.

---

## 11. Duplicate delivery và idempotency

### 11.1. Duplicate là bình thường

Duplicate có thể đến từ:

- Producer retry sau timeout.
- Consumer crash trước ack.
- Ack bị mất.
- Visibility timeout hết hạn.
- Broker redelivery.
- Consumer restart trong lúc xử lý.
- Operator replay message.
- Job reconciliation gửi lại message.

Không nên cố chứng minh duplicate “không bao giờ xảy ra”. Hãy thiết kế để duplicate không gây sai business.

### 11.2. Idempotency là gì?

Một operation là idempotent nếu thực hiện nhiều lần vẫn cho cùng kết quả logic như một lần:

```text
f(f(state)) = f(state)
```

Ví dụ idempotent:

```sql
UPDATE orders
SET status = 'PAID'
WHERE id = ? AND status = 'PENDING';
```

Ví dụ không idempotent nếu không có bảo vệ:

```sql
UPDATE wallets
SET balance = balance - 100000
WHERE user_id = ?;
```

Message bị xử lý hai lần sẽ trừ tiền hai lần.

### 11.3. Các chiến lược idempotency

**Set state thay vì apply delta mù quáng**

Thay vì trừ tiền trực tiếp, dùng payment operation ID duy nhất và kiểm tra trạng thái trước khi apply.

**Unique constraint**

```sql
CREATE TABLE processed_messages (
    consumer_name VARCHAR(100) NOT NULL,
    message_id VARCHAR(200) NOT NULL,
    processed_at TIMESTAMP NOT NULL,
    PRIMARY KEY (consumer_name, message_id)
);
```

Nếu insert vi phạm unique key, message đã được xử lý bởi consumer đó.

**Inbox pattern**

Consumer ghi message vào inbox trong cùng transaction với business state:

```text
BEGIN TRANSACTION
  INSERT inbox(message_id, consumer, ...)
  UPDATE business_state
COMMIT
```

Unique constraint hoặc trạng thái inbox giúp bỏ qua duplicate.

**Idempotency key ở downstream**

Gọi payment provider hoặc API bên ngoài với cùng key cho cùng một operation. Downstream lưu key và trả lại kết quả cũ khi nhận request lặp.

**State machine hợp lệ**

```text
PENDING ──▶ PAID ──▶ REFUNDED
   │
   └──▶ CANCELLED
```

Message `PayOrder` đến sau khi order đã `PAID` có thể trở thành no-op thay vì trừ tiền lại.

### 11.4. Message ID hay business ID?

`messageId` và business operation ID có thể khác nhau:

```text
messageId:          msg-001
orderId:            order-123
paymentOperationId: capture-payment-123
```

Nếu cùng event được republish với message ID mới, dedup chỉ theo `messageId` sẽ không đủ. Chọn key theo mục tiêu:

- Dedup một lần giao message: `messageId`.
- Dedup cùng operation: `paymentOperationId`.
- Dedup event của aggregate/version: `orderId + version`.

---

## 12. Poison message

Poison message là message liên tục thất bại vì bản thân message hoặc dữ liệu nghiệp vụ có vấn đề, không phải lỗi tạm thời của hạ tầng.

Ví dụ:

- JSON sai schema.
- Thiếu field bắt buộc.
- Giá trị tiền âm.
- Order không tồn tại và không bao giờ tồn tại.
- File đính kèm hỏng.
- Version message không được consumer hỗ trợ.

Nếu cứ requeue ngay:

```text
Queue ──▶ Consumer lỗi ──▶ requeue
  ▲                         │
  └─────────────────────────┘
```

Message có thể chiếm consumer liên tục và gây retry storm hoặc hot loop.

Cách xử lý:

1. Xác định lỗi có retry được không.
2. Giới hạn số lần retry.
3. Dùng backoff giữa các lần retry.
4. Đưa lỗi vĩnh viễn vào dead-letter queue.
5. Lưu reason, retry count và thời điểm lỗi.
6. Có quy trình inspect, sửa dữ liệu và replay có kiểm soát.

Không đưa secret, token hoặc toàn bộ payload nhạy cảm vào log DLQ.

---

## 13. Retry và retry storm

### 13.1. Phân loại lỗi

| Loại lỗi | Ví dụ | Hành động thường dùng |
|---|---|---|
| Lỗi tạm thời | Timeout, connection reset, HTTP 503 | Retry có backoff |
| Lỗi giới hạn | Rate limit, quota | Retry theo `Retry-After` hoặc backoff dài |
| Lỗi dữ liệu | JSON sai, thiếu field | Không retry vô hạn; DLQ |
| Lỗi nghiệp vụ | Order đã hủy, payment không hợp lệ | Đánh dấu failed hoặc DLQ |
| Lỗi không xác định | Exception chưa phân loại | Retry ít lần, alert và điều tra |

Không phải exception nào cũng nên retry. Retry validation error chỉ lặp lại cùng lỗi.

### 13.2. Exponential backoff

Một công thức thường dùng:

```text
wait(n) = min(maxDelay, baseDelay × 2^(n-1))
```

Ví dụ `baseDelay = 1s`, `maxDelay = 60s`:

```text
Lần 1: 1s
Lần 2: 2s
Lần 3: 4s
Lần 4: 8s
Lần 5: 16s
Lần 6: 32s
Lần 7: 60s
```

### 13.3. Jitter

Nếu hàng nghìn message cùng retry đúng một thời điểm, downstream có thể lại quá tải:

```text
1.000 consumer retry lúc 10:00:00
→ downstream lại quá tải
→ tất cả lại fail
```

Thêm một khoảng ngẫu nhiên:

```text
actualDelay = calculatedDelay + random(0, jitter)
```

### 13.4. Retry budget

Retry cần có giới hạn:

- Maximum attempts.
- Maximum elapsed time.
- Maximum message age.
- Maximum retry rate.
- Tổng số retry cho một dependency.

Sau giới hạn, chuyển sang DLQ hoặc trạng thái `FAILED_REQUIRES_REVIEW`.

### 13.5. Retry ở đâu?

**Trong consumer process**: đơn giản nhưng giữ worker bận trong thời gian chờ.

**Retry queue**:

```text
main queue
   ↓ lỗi tạm thời
retry queue 10s
   ↓
main queue
   ↓ lỗi quá số lần
DLQ
```

Retry queue giải phóng main consumer nhưng cần lưu retry metadata và định tuyến đúng. Delayed delivery là một biến thể do broker hoặc hệ thống trung gian thực hiện.

---

## 14. Acknowledgement và thời điểm ack

| Cách ack | Lợi ích | Rủi ro |
|---|---|---|
| Ack trước xử lý | Ít redelivery, throughput cao | Crash sau ack làm mất message |
| Ack sau xử lý thành công | Giảm mất message | Side effect xong nhưng ack lỗi sẽ tạo duplicate |
| Ack sau deserialize | Đơn giản về kỹ thuật | Parse thành công không có nghĩa business thành công |

Với at-least-once, flow thường là:

```text
receive → validate → process → persist side effect → ack
```

Consumer cần phân biệt:

- Retry vì lỗi tạm thời.
- Không retry vì message lỗi vĩnh viễn.
- Ack và bỏ qua vì duplicate hợp lệ.
- Chuyển DLQ vì vượt retry budget.

`requeue = true` không phải một chiến lược retry hoàn chỉnh; nếu không có backoff và giới hạn, nó tạo hot loop.

---

## 15. Exactly-once effect trong thực tế

### 15.1. Vì sao transaction database chưa đủ?

Flow 1:

```text
DB transaction commit
        ↓ process crash
CALL external provider chưa chạy
```

Retry có thể gọi provider. Nếu đổi thứ tự:

```text
CALL external provider thành công
        ↓ process crash
DB transaction chưa commit
```

Retry lại có thể gọi provider lần nữa. Không có transaction duy nhất bao phủ database, broker và HTTP provider thì vẫn còn khoảng không chắc chắn.

### 15.2. Thiết kế effect an toàn

```text
messageId / operationId
        ↓
Inbox hoặc unique constraint
        ↓
Business transaction
        ↓
Idempotency key ở external provider
        ↓
Ack sau cùng
```

Mục tiêu là nếu flow chạy lại, hệ thống nhận ra kết quả đã tồn tại hoặc downstream trả về cùng kết quả logic.

Nên mô tả cụ thể:

- “Database update được apply một lần theo `operationId`.”
- “Payment provider không tạo payment thứ hai với cùng idempotency key.”
- “Analytics pipeline deduplicate theo event ID.”

Mô tả này kiểm chứng được hơn câu “toàn hệ thống exactly-once”.

---

## 16. Ví dụ consumer idempotent trong Java

Payload:

```java
public record CapturePayment(
        String operationId,
        String paymentId,
        long amount,
        String currency
) {}
```

Pseudo-service:

```java
@Transactional
public void handle(CapturePayment command) {
    Payment payment = paymentRepository.findById(command.paymentId())
            .orElseThrow(() -> new PaymentNotFound(command.paymentId()));

    if (payment.isCaptured()) {
        return; // duplicate hợp lệ: không tạo side effect mới
    }

    if (!payment.canCapture(command.amount(), command.currency())) {
        throw new InvalidPaymentState(command.paymentId());
    }

    processedOperationRepository.insertIfAbsent(command.operationId());
    payment.markCaptured(command.operationId());
    paymentRepository.save(payment);
}
```

Trong code production, cần xử lý race condition khi hai instance xử lý cùng operation đồng thời bằng unique constraint, optimistic locking, pessimistic lock hoặc atomic update.

`insertIfAbsent` phải có semantics atomic ở database; chỉ `SELECT` rồi `INSERT` riêng biệt là chưa đủ.

Consumer loop ở mức khái niệm:

```java
while (running) {
    Message message = consumer.receive();

    try {
        handler.handle(message);
        consumer.ack(message);
    } catch (RetryableException ex) {
        consumer.retryWithBackoff(message, ex);
    } catch (PermanentException ex) {
        consumer.deadLetter(message, ex);
    }
}
```

Đây là pseudocode; API cụ thể phụ thuộc broker/client.

---

## 17. Bảng sự cố và cách giảm rủi ro

| Sự cố | Hậu quả có thể xảy ra | Cách giảm rủi ro |
|---|---|---|
| Consumer crash trước ack | Message redeliver | Idempotent handler |
| Ack bị mất | Duplicate delivery | Dedup hoặc business idempotency |
| Broker crash trước persist | Lost message | Persistent message, replication, confirmation |
| Producer timeout khi publish | Không biết message đã vào broker chưa | Publisher confirm, message ID, outbox |
| Handler lỗi validation | Poison message | Không retry vô hạn, DLQ |
| Downstream timeout | Có thể đã tạo side effect | Idempotency key, status query |
| Consumer chậm | Queue depth tăng | Scale, backpressure, prefetch |
| Retry ngay lập tức | Retry storm | Exponential backoff, jitter, budget |
| Visibility timeout quá ngắn | Concurrent duplicate | Gia hạn timeout, idempotency |
| Client retry HTTP request | Duplicate command/order | API idempotency key |
| Schema không tương thích | Consumer fail liên tục | Version contract, compatibility test |

---

## 18. Observability cho failure model

### Producer metrics

- Publish attempts.
- Publish confirmations.
- Publish failures và timeout.
- Số lần producer retry.

### Broker/queue metrics

- Ready message hoặc backlog.
- Unacked/in-flight message.
- Message age lớn nhất.
- Redelivery count.
- Dead-letter count.
- Queue throughput.
- Consumer count và connection health.

### Consumer metrics

- Processing success/failure.
- Processing latency p50/p95/p99.
- Retry count theo loại lỗi.
- Permanent failure count.
- Duplicate detected count.
- Ack latency.

Log nên có:

```text
messageId
messageType
messageVersion
operationId
correlationId
consumerName
attempt
receivedAt
completedAt
failureClass
```

Không log toàn bộ payload một cách tùy tiện vì payload có thể chứa thông tin cá nhân, token hoặc dữ liệu thanh toán.

Alert gợi ý:

- Queue depth tăng liên tục.
- Message age vượt SLA.
- Redelivery rate tăng đột biến.
- DLQ có message mới.
- Publish confirmation timeout tăng.
- Consumer không còn active.
- Retry storm ở một dependency.

---

## 19. Bài tập thực hành

### Bài 1: Phân tích thời điểm crash

Với flow:

```text
receive → gọi email provider → ghi DB → ack
```

Phân tích hậu quả nếu process crash:

1. Ngay sau `receive`.
2. Sau khi email provider trả success.
3. Sau khi ghi DB nhưng trước ack.
4. Sau khi ack thành công.

Với mỗi trường hợp, ghi message có được giao lại không, email/database có thể duplicate không và cơ chế nào giảm rủi ro.

### Bài 2: Phân loại lỗi

Phân loại thành retryable, permanent hoặc unknown outcome:

1. HTTP 503 từ email provider.
2. JSON thiếu `orderId`.
3. Database connection reset sau khi transaction commit chưa rõ.
4. Payment provider timeout.
5. Order đã bị cancel.
6. Schema version không được consumer hỗ trợ.
7. HTTP 429 kèm `Retry-After`.

Giải thích vì sao unknown outcome cần idempotency thay vì retry mù quáng.

### Bài 3: Thiết kế idempotent consumer

Thiết kế consumer cho `InventoryReserved`:

- Không trừ stock hai lần cho cùng `reservationId`.
- Có thể nhận duplicate message.
- Hai instance có thể xử lý cùng lúc.
- Database hỗ trợ unique constraint và optimistic locking.

Viết schema hoặc pseudo-SQL cho processed operation, cập nhật stock và điều kiện tránh stock âm.

### Bài 4: Thiết kế retry flow

Thiết kế flow cho `SendInvoice`:

- Retry tối đa 5 lần.
- Backoff tăng dần và có jitter.
- Lỗi validation không retry.
- Sau retry cuối, chuyển DLQ.

Ghi rõ metadata cần lưu: attempt, firstFailedAt, nextRetryAt, lastErrorCode và correlationId.

### Bài 5: Mô phỏng unknown outcome

Viết test hoặc console program:

1. Consumer đọc message.
2. Ghi side effect vào database giả lập.
3. Throw exception trước ack.
4. Giao lại cùng message.
5. Quan sát side effect bị nhân đôi khi chưa có idempotency.
6. Thêm unique constraint hoặc processed-message store.
7. Chứng minh duplicate không còn tạo effect mới.

### Bài 6: Phân tích exactly-once

Chọn flow thanh toán có database và external provider. Vẽ các điểm crash giữa:

```text
DB update ↔ provider call ↔ message ack
```

Đề xuất operation ID, idempotency key, inbox/outbox, cách reconcile trạng thái không rõ và alert cho payment ở trạng thái `UNKNOWN`.

---

## 20. Câu hỏi tự kiểm tra

1. At-most-once đánh đổi điều gì để tránh duplicate?
2. Tại sao at-least-once thường cần idempotent consumer?
3. Consumer crash sau side effect nhưng trước ack tạo ra tình huống nào?
4. Vì sao ack bị mất cũng có thể tạo duplicate?
5. Durable queue có đồng nghĩa mọi message đều không mất không?
6. Network timeout cho biết chắc chắn operation chưa xảy ra không?
7. Poison message khác lỗi tạm thời như thế nào?
8. Vì sao requeue ngay lập tức có thể tạo retry storm?
9. `messageId`, `correlationId` và `idempotencyKey` khác nhau ra sao?
10. Vì sao database transaction không tự đảm bảo exactly-once effect với external API?
11. Khi nào nên chuyển message vào DLQ?
12. Tại sao retry policy phải phụ thuộc giá trị nghiệp vụ?

---

## 21. Checklist hoàn thành Tuần 2

- [ ] Mình phân biệt được at-most-once, at-least-once và exactly-once.
- [ ] Mình phân biệt được delivery guarantee, processing guarantee và business effect.
- [ ] Mình giải thích được vì sao duplicate là bình thường trong at-least-once.
- [ ] Mình phân tích được consumer crash trước và sau side effect.
- [ ] Mình hiểu publish timeout tạo ra unknown outcome.
- [ ] Mình phân biệt durable topology, persistent message và publisher confirmation.
- [ ] Mình phân biệt retryable error, permanent error và poison message.
- [ ] Mình biết retry cần backoff, jitter và giới hạn.
- [ ] Mình có thể thiết kế idempotency bằng unique constraint, inbox hoặc idempotency key.
- [ ] Mình biết không nên hứa exactly-once cho toàn hệ thống nếu chưa chứng minh từng side effect.
- [ ] Mình biết metric cần theo dõi: redelivery, backlog, retry, DLQ và processing latency.
- [ ] Mình hoàn thành bài phân tích crash và thiết kế retry flow.

## Kết luận

Message queue không loại bỏ failure; nó thay đổi cách failure được quan sát và xử lý. At-most-once giảm duplicate nhưng có thể mất message. At-least-once giảm mất message nhưng yêu cầu consumer chịu được duplicate. Exactly-once chỉ có ý nghĩa khi nói rõ tầng nào và side effect nào được bảo vệ.

Trong backend thực tế, chiến lược bền vững thường là **at-least-once delivery + idempotent processing**. Muốn đạt điều đó, cần ack đúng thời điểm, retry có giới hạn, phân loại lỗi, xử lý poison message bằng DLQ, dùng operation ID/idempotency key và quan sát toàn bộ message flow.

Ở Tuần 3, các nguyên tắc này sẽ được ánh xạ vào mô hình AMQP của RabbitMQ: connection, channel, exchange, queue, binding, routing key, durable queue và persistent message.

# Tuần 8: SQS reliability, security và Spring Cloud AWS

## 1. Mục tiêu bài học

Sau bài này, bạn có thể:

- Chọn visibility timeout dựa trên thời gian xử lý thực tế.
- Dùng ChangeMessageVisibility cho job dài.
- Thiết kế consumer idempotent khi SQS redeliver message.
- Thiết kế Dead-Letter Queue và redrive policy.
- Hiểu maxReceiveCount và phân loại lỗi trước khi retry.
- Biết DLQ phải cùng loại Standard hoặc FIFO với source queue.
- Phân biệt IAM identity policy và SQS resource policy.
- Dùng IAM role thay vì access key hardcode.
- Hiểu SSE-SQS và SSE-KMS cùng các permission liên quan.
- Theo dõi queue depth, age of oldest message, in-flight message và DLQ.
- Cấu hình CloudWatch alarm có ý nghĩa vận hành.
- Hiểu chi phí theo API request và cách giảm request bằng long polling/batch.
- Gửi và nhận SQS message bằng Spring Cloud AWS.
- Cấu hình listener container, concurrency, acknowledgement và JSON conversion.
- Xây flow production-like cho Standard Queue và FIFO Queue.

> Bài này tiếp nối Tuần 7. Tuần 7 học SQS architecture và queue types; Tuần 8 tập trung đưa queue vào production an toàn, có thể quan sát và khôi phục.

---

## 2. Reliability của SQS cần giải quyết điều gì?

Một flow SQS production không dừng ở việc gửi và nhận message:

~~~text
Producer
   │
   ├── gửi message
   ├── nhận response
   └── retry nếu không rõ kết quả
        │
        ▼
Source Queue
   │
   ├── ReceiveMessage
   ├── visibility timeout
   ├── xử lý business
   ├── DeleteMessage khi thành công
   └── redelivery khi chưa delete
        │
        ├── retry lỗi tạm thời
        └── DLQ lỗi vĩnh viễn/quá retry
~~~

Có năm câu hỏi reliability chính:

1. Nếu consumer crash thì message có bị mất không?
2. Nếu consumer xử lý xong nhưng delete thất bại thì sao?
3. Nếu message luôn lỗi thì làm sao tránh retry vô hạn?
4. Nếu queue bị truy cập sai principal thì sao?
5. Nếu backlog tăng nhưng không ai phát hiện thì sao?

Một kiến trúc tốt cần đồng thời có:

- Delivery flow đúng.
- Idempotent business effect.
- Retry có giới hạn.
- DLQ và redrive runbook.
- IAM least privilege.
- Encryption phù hợp.
- Metrics và alarm.
- Test cho crash, duplicate và poison message.

---

## 3. SQS acknowledgement thực chất là DeleteMessage

SQS không có ack riêng giống một số broker. Với SQS, message được coi là hoàn tất khi consumer gọi DeleteMessage bằng receipt handle sau khi xử lý thành công.

~~~text
ReceiveMessage
      ↓
message invisible
      ↓
business processing
      ├── success → DeleteMessage
      └── failure → không delete hoặc xử lý retry/DLQ
~~~

### 3.1. Delete quá sớm

~~~text
receive → delete → process
~~~

Nếu process crash sau delete, message mất khỏi source queue.

### 3.2. Delete sau thành công

~~~text
receive → process → delete
~~~

Cách này giảm mất message nhưng tạo khả năng duplicate:

~~~text
receive → process thành công → network lỗi khi delete
                                      ↓
                          message visible lại sau timeout
~~~

Consumer phải idempotent. Đây là trade-off cốt lõi của at-least-once processing.

### 3.3. Delete không phải rollback

Nếu đã gọi payment provider rồi delete message thất bại, việc receive lại không thể rollback HTTP call trước đó. Downstream cần idempotency key hoặc status query.

---

## 4. Visibility timeout trong production

### 4.1. Chọn timeout từ p99

Visibility timeout mặc định hiện hành của SQS là 30 giây, nhưng không nên giữ mặc định một cách máy móc.

Đo thời gian xử lý thực tế:

~~~text
processing time
├── deserialize
├── database
├── external API
├── business logic
└── logging/metrics
~~~

Sau đó chọn:

~~~text
visibility timeout
  > p99 processing time
  + thời gian network/retry cần thiết
  + safety margin
~~~

Ví dụ:

| Thành phần | Thời gian |
|---|---:|
| Deserialize và validation | 100 ms |
| Database | 1.2 s |
| Payment provider p99 | 8 s |
| Business logic | 500 ms |
| Network margin | 5 s |
| Timeout ban đầu | khoảng 20–30 s |

Đây là điểm bắt đầu; cần benchmark và quan sát redelivery thực tế.

### 4.2. Timeout quá ngắn

~~~text
0s  receive
5s  visibility timeout hết
6s  consumer đầu tiên vẫn xử lý
7s  consumer thứ hai nhận lại
~~~

Hậu quả:

- Hai consumer chạy cùng business operation.
- Side effect có thể duplicate.
- Database lock contention tăng.
- Redelivery metric tăng dù handler không nhất thiết lỗi.
- FIFO group có thể bị block hoặc chuyển trạng thái ngoài ý muốn.

### 4.3. Timeout quá dài

Timeout quá dài cũng có rủi ro:

- Consumer crash thì message chờ lâu mới visible lại.
- Recovery latency tăng.
- Backlog thực tế bị che khuất trong nhóm in-flight.
- Số message đang xử lý tăng nhưng không thể reassign nhanh.

Không có một giá trị “càng dài càng tốt”.

### 4.4. ChangeMessageVisibility cho job dài

~~~text
receive
   ↓ timeout ban đầu 60s
process step 1
   ↓ ChangeMessageVisibility +60s
process step 2
   ↓ ChangeMessageVisibility +60s
hoàn tất
   ↓ DeleteMessage
~~~

Quy tắc:

- Chỉ gia hạn nếu job còn sống và có tiến triển.
- Không gia hạn vô hạn cho poison message.
- Log số lần gia hạn.
- Có giới hạn tổng thời gian xử lý.
- Nếu không thể tiếp tục, để message đi theo retry/DLQ policy.

### 4.5. Shutdown graceful

Khi ứng dụng shutdown:

1. Ngừng nhận message mới.
2. Chờ message đang xử lý trong thời gian hữu hạn.
3. Delete những message đã thành công.
4. Để message chưa xong visible lại sau timeout hoặc chủ động điều chỉnh visibility.
5. Đóng listener container và AWS client.

Không terminate process đột ngột trong lúc side effect đang chạy nếu có thể tránh.

---

## 5. Duplicate delivery và idempotent consumer

### 5.1. Duplicate là bình thường

Message có thể redeliver do:

- Consumer crash trước delete.
- Delete request timeout.
- Receipt handle không còn hợp lệ.
- Visibility timeout hết trước khi xử lý xong.
- Consumer restart.
- Manual redrive từ DLQ.
- Producer gửi lại cùng operation.
- Standard Queue at-least-once behavior.

Không nên coi duplicate là một incident bất thường cần khắc phục bằng cách tắt retry. Duplicate là trạng thái consumer phải chịu được.

### 5.2. Idempotency key

Message envelope nên có operation ID:

~~~json
{
  "messageId": "msg-001",
  "eventType": "CapturePayment",
  "eventVersion": 1,
  "operationId": "capture:payment-123",
  "correlationId": "checkout-789",
  "payload": {
    "paymentId": "payment-123",
    "amount": 250000,
    "currency": "VND"
  }
}
~~~

Chọn khóa theo mục tiêu:

| Mục tiêu | Key |
|---|---|
| Dedup một lần giao message | messageId |
| Dedup một business operation | operationId |
| Version của aggregate | aggregateId + version |
| External API idempotency | idempotencyKey |

### 5.3. Pattern ghi nhận message đã xử lý

~~~sql
CREATE TABLE processed_messages (
    consumer_name VARCHAR(100) NOT NULL,
    operation_id VARCHAR(200) NOT NULL,
    processed_at TIMESTAMP NOT NULL,
    PRIMARY KEY (consumer_name, operation_id)
);
~~~

Business transaction:

~~~text
BEGIN
  insert processed_messages
  nếu duplicate:
      kết thúc an toàn
  update business state
COMMIT

DeleteMessage
~~~

Insert phải có unique constraint hoặc cơ chế atomic. Pattern SELECT rồi INSERT riêng biệt có race condition.

### 5.4. Idempotency với external API

Nếu gọi payment, email hoặc webhook provider:

~~~text
idempotency key = capture:payment-123
~~~

Retry cùng operation phải giữ nguyên key. UUID mới ở mỗi retry có thể biến retry thành operation mới.

---

## 6. Retry policy cho SQS

### 6.1. Phân loại lỗi

| Loại lỗi | Ví dụ | Chính sách |
|---|---|---|
| Tạm thời | HTTP 503, timeout, connection reset | Retry có backoff |
| Rate limit | HTTP 429 | Theo Retry-After/backoff |
| Dữ liệu | JSON sai, thiếu field | Không retry vô hạn |
| Nghiệp vụ | Order đã hủy | DLQ hoặc trạng thái failed |
| Unknown outcome | Timeout sau khi provider có thể đã xử lý | Query status/idempotency |
| Bug code | NullPointerException lặp lại | Giới hạn retry, alert, DLQ |

Không phải lỗi nào cũng nên để SQS tự redeliver nhiều lần.

### 6.2. Retry qua visibility timeout

~~~text
receive
  ↓ lỗi tạm thời
không delete
  ↓ visibility timeout hết
message visible lại
  ↓ receive lần 2
~~~

Ưu điểm:

- Không cần queue retry riêng.
- Dễ bắt đầu.
- Phù hợp retry đơn giản.

Nhược điểm:

- Không kiểm soát delay linh hoạt nếu chỉ dùng một timeout.
- Message lỗi có thể chiếm flow lâu.
- Cần maxReceiveCount/DLQ để chặn vòng lặp.

### 6.3. Retry nội bộ trong handler

~~~text
receive
  ├── call dependency
  ├── wait
  ├── retry 1
  ├── retry 2
  └── success/delete hoặc fail
~~~

Ưu điểm là giảm số lần receive. Nhược điểm là giữ consumer capacity trong lúc chờ và visibility timeout phải đủ dài.

### 6.4. Retry queue/delay queue

~~~text
orders-standard
      ↓ lỗi tạm thời
orders-retry-10s
      ↓
orders-standard
      ↓ lỗi tiếp
orders-retry-60s
      ↓
orders-standard
      ↓ quá giới hạn
orders-dlq
~~~

Cần lưu:

- attempt.
- firstFailedAt.
- lastFailedAt.
- nextRetryAt.
- errorCode.
- correlationId.
- operationId.

Delay queue không thay thế DLQ. Retry queue cũng cần giới hạn và quan sát.

### 6.5. Backoff và jitter

~~~text
delay = min(maxDelay, baseDelay × 2^(attempt - 1))
actualDelay = delay + random(0, jitter)
~~~

Ví dụ:

~~~text
attempt 1: 1s + jitter
attempt 2: 2s + jitter
attempt 3: 4s + jitter
attempt 4: 8s + jitter
attempt 5: DLQ hoặc manual review
~~~

Jitter tránh nhiều message cùng retry tại một thời điểm.

---

## 7. Dead-Letter Queue

### 7.1. DLQ là gì?

Dead-Letter Queue là queue nhận message không thể xử lý thành công sau chính sách retry.

~~~text
source queue
     ↓ receive nhiều lần nhưng không delete
     ↓ maxReceiveCount vượt ngưỡng
dead-letter queue
~~~

DLQ là nơi giữ message cần:

- Điều tra.
- Sửa dữ liệu hoặc code.
- Replay/redrive có kiểm soát.
- Alert.
- Audit nguyên nhân thất bại.

### 7.2. Redrive policy

Redrive policy gắn source queue với DLQ và quy định maxReceiveCount:

~~~json
{
  "deadLetterTargetArn": "arn:aws:sqs:ap-southeast-1:123456789012:orders-dlq",
  "maxReceiveCount": "5"
}
~~~

Ý nghĩa:

- SQS theo dõi số lần message được receive.
- Khi vượt maxReceiveCount, message được chuyển sang DLQ.
- Message chưa chắc đã được xử lý hoàn chỉnh 5 lần; receive count có thể tăng khi consumer lấy message rồi crash hoặc timeout.
- Chọn giá trị theo thời gian xử lý và mức retry cần thiết.

### 7.3. DLQ cùng loại với source queue

Source Standard phải dùng DLQ Standard. Source FIFO phải dùng DLQ FIFO.

~~~text
orders-standard ──▶ orders-dlq
payment.fifo     ──▶ payment-dlq.fifo
~~~

Không nối Standard Queue vào FIFO DLQ hoặc ngược lại.

### 7.4. FIFO và blocked message group

Với FIFO, một message lỗi có thể chặn các message sau trong cùng MessageGroupId cho đến khi:

- Message được delete.
- Visibility timeout hết và flow tiếp tục theo policy.
- Message được chuyển DLQ sau ngưỡng.

Giữ message lỗi ở source giúp bảo toàn thứ tự; chuyển sớm vào DLQ giúp group chạy tiếp nhưng có thể bỏ qua state cần xử lý. Quyết định phải dựa trên business semantics.

### 7.5. DLQ retention và age

Cấu hình retention cho DLQ đủ dài để điều tra và redrive. Với Standard Queue, message age trong DLQ có thể phản ánh thời điểm chuyển vào DLQ thay vì thời điểm ban đầu; lưu timestamp gốc trong payload hoặc attributes nếu cần audit chính xác.

### 7.6. DLQ runbook

Khi DLQ có message:

1. Xem metric và alarm.
2. Kiểm tra message type/version.
3. Xem error code và correlation ID.
4. Xác định lỗi dữ liệu, code hay dependency.
5. Sửa hệ thống hoặc dữ liệu.
6. Chọn replay một phần hay toàn bộ.
7. Replay có idempotency.
8. Theo dõi source queue sau redrive.
9. Ghi incident note và nguyên nhân gốc.

Không xóa DLQ hàng loạt trước khi lưu bằng chứng và xác định message có thể bỏ hay không.

---

## 8. IAM cho SQS

### 8.1. Identity-based policy

Identity policy gắn với IAM user, group hoặc role. Với application, thường gắn vào execution role.

Producer chỉ nên có quyền gửi:

~~~json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "SendOrders",
      "Effect": "Allow",
      "Action": [
        "sqs:SendMessage",
        "sqs:SendMessageBatch"
      ],
      "Resource": "arn:aws:sqs:ap-southeast-1:123456789012:orders-standard"
    }
  ]
}
~~~

Consumer cần nhóm quyền khác:

~~~json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "ConsumeOrders",
      "Effect": "Allow",
      "Action": [
        "sqs:ReceiveMessage",
        "sqs:DeleteMessage",
        "sqs:DeleteMessageBatch",
        "sqs:ChangeMessageVisibility",
        "sqs:ChangeMessageVisibilityBatch",
        "sqs:GetQueueAttributes"
      ],
      "Resource": "arn:aws:sqs:ap-southeast-1:123456789012:orders-standard"
    }
  ]
}
~~~

Không cấp sqs:* cho application nếu chỉ cần send hoặc consume.

### 8.2. Resource-based queue policy

SQS queue policy gắn trực tiếp vào queue. Dùng khi:

- SNS publish vào SQS.
- EventBridge gửi vào SQS.
- Cross-account access.
- Giới hạn principal hoặc source ARN.
- Ép HTTPS/TLS.
- Giới hạn theo VPC endpoint.

Một policy cross-account cần phối hợp policy ở account sở hữu queue và permission của principal/role bên gọi.

### 8.3. Producer và consumer role khác nhau

~~~text
order-api-role
  └── sqs:SendMessage trên orders-standard

order-worker-role
  ├── sqs:ReceiveMessage
  ├── sqs:DeleteMessage
  └── sqs:ChangeMessageVisibility trên orders-standard
~~~

Không dùng một role có toàn bộ quyền cho cả API và worker nếu không cần.

### 8.4. Không hardcode credentials

Không viết access key/secret key trong source code, application.yml hoặc Docker image.

Nên dùng:

- IAM role cho ECS task.
- IAM role cho EC2.
- EKS Pod Identity hoặc IRSA.
- Lambda execution role.
- Default credential provider chain.
- Temporary credentials cho local/dev.

### 8.5. Ép HTTPS

Queue policy có thể deny request không mã hóa:

~~~json
{
  "Sid": "DenyInsecureTransport",
  "Effect": "Deny",
  "Principal": "*",
  "Action": "sqs:*",
  "Resource": "arn:aws:sqs:ap-southeast-1:123456789012:orders-standard",
  "Condition": {
    "Bool": {
      "aws:SecureTransport": "false"
    }
  }
}
~~~

Cẩn thận khi áp policy ở môi trường test; kiểm tra SDK/client luôn dùng HTTPS.

---

## 9. Server-side encryption

### 9.1. Encryption at rest

SQS hỗ trợ server-side encryption. Mục tiêu là bảo vệ message khi lưu tại AWS.

Hai hướng phổ biến:

- SQS-managed encryption key.
- Customer managed AWS KMS key.

Encryption at rest không thay thế IAM, TLS, masking payload trong log hoặc KMS key policy.

### 9.2. SQS-managed key

SQS-managed key đơn giản hơn về vận hành, phù hợp khi không cần kiểm soát key riêng hoặc cross-account key policy phức tạp.

### 9.3. Customer managed KMS key

Customer managed KMS key phù hợp khi cần:

- Kiểm soát key lifecycle.
- Key rotation policy.
- Audit key usage.
- Phân quyền producer/consumer rõ hơn.
- Tích hợp cross-account.

Đổi lại cần quản lý key policy, IAM permissions, Region, quota, cost và quy trình disable/schedule deletion.

### 9.4. Permission KMS

Khi dùng customer managed key, producer/consumer có thể cần quyền KMS ngoài quyền SQS. Ví dụ, principal gửi/nhận encrypted message có thể cần kms:GenerateDataKey và kms:Decrypt tùy flow và policy.

Nếu SNS/EventBridge gửi vào encrypted queue, service principal cũng cần được cấp quyền qua key policy và queue policy với điều kiện giới hạn SourceArn hoặc SourceAccount phù hợp.

### 9.5. Tránh confused deputy

Khi cho service khác gửi vào encrypted queue:

- Giới hạn aws:SourceArn.
- Giới hạn aws:SourceAccount.
- Giới hạn principal.
- Không dùng Principal: * nếu không cần.
- Kiểm tra IAM Access Analyzer.
- Test cả queue policy và KMS key policy.

---

## 10. CloudWatch metrics

SQS phát hành metrics trong namespace AWS/SQS. Nhiều metric có tính approximate do kiến trúc phân tán; đủ dùng cho vận hành nhưng không nên coi là số liệu giao dịch tuyệt đối.

### 10.1. Metrics quan trọng

| Metric | Ý nghĩa |
|---|---|
| ApproximateNumberOfMessagesVisible | Message sẵn sàng receive |
| ApproximateNumberOfMessagesNotVisible | Message in-flight/invisible |
| ApproximateNumberOfMessagesDelayed | Message đang delay |
| ApproximateAgeOfOldestMessage | Tuổi message chưa xử lý lâu nhất |
| NumberOfMessagesSent | Số message gửi |
| NumberOfMessagesReceived | Số message được receive |
| NumberOfMessagesDeleted | Số message delete |
| NumberOfEmptyReceives | Số receive không lấy được message |
| ApproximateNumberOfGroupsWithInflightMessages | Hữu ích cho FIFO |

### 10.2. Queue depth

Queue depth thường được quan sát qua visible messages:

~~~text
backlog ≈ ApproximateNumberOfMessagesVisible
~~~

Nhưng cần xem cả not visible:

~~~text
total pressure = visible + in-flight + delayed
~~~

Queue depth tăng liên tục có thể do producer nhanh hơn consumer, downstream chậm, visibility quá dài, FIFO group bị block hoặc worker thiếu quyền.

### 10.3. Age of oldest message

Age of oldest message phản ánh độ trễ mà message cũ nhất đang chờ. Đây thường là metric gần với SLA hơn queue depth đơn thuần.

Ví dụ:

~~~text
visible messages = 100
oldest age       = 2 seconds
~~~

có thể khỏe hơn:

~~~text
visible messages = 10
oldest age       = 2 hours
~~~

Cần cảnh giác với poison message và DLQ: trên Standard Queue, message bị receive nhiều lần ảnh hưởng cách metric age được tính; khi chuyển sang DLQ, age ở DLQ có thể reset theo thời điểm chuyển.

### 10.4. DLQ metrics

Alarm quan trọng cho DLQ thường là:

~~~text
ApproximateNumberOfMessagesVisible trên DLQ > 0
~~~

Không chỉ nhìn NumberOfMessagesSent, vì automatic redrive có semantics metric khác manual send.

### 10.5. FIFO metrics

Nếu FIFO queue có backlog nhưng ApproximateNumberOfGroupsWithInflightMessages thấp, có thể:

- Có quá ít active MessageGroupId.
- Một group đang bị block.
- Consumer concurrency chưa đủ.
- Có hot group.

### 10.6. Application metrics

Thêm custom metric:

~~~text
sqs.consumer.duplicate_detected
sqs.consumer.processing_failure
sqs.consumer.processing_latency
sqs.consumer.delete_failure
sqs.consumer.visibility_extension
~~~

---

## 11. CloudWatch alarm và SLA

Alarm nên gắn với SLA:

### Alarm backlog

~~~text
ApproximateNumberOfMessagesVisible > threshold
for 5 minutes
~~~

### Alarm message age

~~~text
ApproximateAgeOfOldestMessage > allowed_latency
for 3 evaluation periods
~~~

### Alarm DLQ

~~~text
ApproximateNumberOfMessagesVisible on DLQ > 0
~~~

### Alarm no consumer progress

Nếu producer vẫn gửi nhưng NumberOfMessagesDeleted không tăng, worker có thể down, bị AccessDenied hoặc downstream đang block.

Alarm nên có:

- Owner.
- Runbook URL.
- Severity.
- Dashboard.
- Hành động tiếp theo.
- Điều kiện resolve.

---

## 12. Chi phí và hiệu năng

SQS tính phí theo request và một số hoạt động liên quan. Tối ưu không chỉ là giảm processing time; còn là giảm request rỗng và request thừa.

### 12.1. Empty receive

Short polling quá thường xuyên tạo nhiều request không có message:

~~~text
poll → empty
poll → empty
poll → empty
poll → message
~~~

Long polling giúp giảm empty receive.

### 12.2. Batch

Batch send/delete xử lý nhiều message trong ít API request hơn. Cần xử lý partial failure và không đánh đổi correctness lấy số request thấp.

### 12.3. Delete batching

Nếu framework batch delete sau khi xử lý thành công:

- Visibility timeout phải đủ dài.
- Acknowledgement threshold/interval cần cân nhắc.
- Process crash trước batch delete làm nhiều message redeliver.
- Idempotency bắt buộc.

### 12.4. Concurrency và cost

Tăng concurrency có thể:

- Tăng throughput.
- Vượt rate limit downstream.
- Tăng database connection.
- Tăng memory và contention.
- Tăng duplicate khi processing chậm hơn visibility timeout.
- Tăng chi phí API/dependency.

Tuning theo:

~~~text
consumer concurrency
≤ database capacity
≤ external provider quota
≤ CPU/memory capacity
≤ desired in-flight window
~~~

---

## 13. Spring Cloud AWS SQS

Spring Cloud AWS cung cấp abstraction cho:

- SqsTemplate để gửi message.
- SqsMessageListenerContainer để poll và process.
- @SqsListener cho annotation-driven listener.
- JSON message conversion.
- Acknowledgement mode.
- Listener container factory.
- Concurrency và backpressure options.

Starter thường dùng:

~~~xml
<dependency>
    <groupId>io.awspring.cloud</groupId>
    <artifactId>spring-cloud-aws-starter-sqs</artifactId>
</dependency>
~~~

Quản lý version qua Spring Cloud AWS BOM tương thích với Spring Boot của project, không trộn các major version.

### 13.1. AWS credentials trong Spring

Production nên dùng IAM role:

~~~text
Spring Boot
   ↓
AWS SDK default credential provider chain
   ↓
ECS task role / EKS workload identity / EC2 role / Lambda role
   ↓
SQS
~~~

Local development dùng profile, SSO hoặc temporary credentials theo policy team.

### 13.2. SqsTemplate gửi message

~~~java
@Service
public class OrderEventPublisher {

    private final SqsTemplate sqsTemplate;

    public OrderEventPublisher(SqsTemplate sqsTemplate) {
        this.sqsTemplate = sqsTemplate;
    }

    public void publish(OrderCreated event) {
        sqsTemplate.send(to -> to
                .queue("orders-standard")
                .payload(event));
    }
}
~~~

Production publisher nên thêm event ID, correlation ID, operation ID, version, error handling, metrics và outbox nếu database state/message cần consistency.

### 13.3. JSON envelope

~~~java
public record EventEnvelope<T>(
        String eventId,
        String eventType,
        int eventVersion,
        Instant occurredAt,
        String correlationId,
        String operationId,
        T payload
) {}
~~~

Payload và envelope nên tách riêng để consumer xử lý metadata mà không phải biết mọi field nghiệp vụ.

### 13.4. SqsListener

~~~java
@Component
public class OrderEventListener {

    @SqsListener("orders-standard")
    public void handle(EventEnvelope<OrderCreated> message) {
        orderProjectionService.apply(message);
    }
}
~~~

Listener container thực hiện poll, dispatch, xử lý và acknowledgement. Khi listener method kết thúc thành công, framework có thể acknowledge theo mode đã cấu hình.

### 13.5. Acknowledgement mode

| Mode | Ý nghĩa |
|---|---|
| ON_SUCCESS | Delete message khi xử lý thành công |
| ALWAYS | Delete dù listener thành công hay lỗi |
| MANUAL | Application tự acknowledge |

Với command quan trọng, ON_SUCCESS thường là điểm bắt đầu an toàn hơn ALWAYS. ALWAYS có thể làm message mất khi listener ném exception.

MANUAL phù hợp khi cần ack tại thời điểm riêng, phối hợp async processing hoặc tự điều khiển visibility. Manual ack đòi hỏi team hiểu rõ lifecycle.

### 13.6. Listener container và backpressure

Listener container có pipeline poll, dispatch, processing và acknowledgement. Cần cấu hình:

- Số message mỗi poll.
- Poll timeout.
- Số message in-flight tối đa.
- Concurrency.
- Acknowledgement interval/threshold.
- Shutdown timeout.
- Error handler.
- Message converter.

Config minh họa:

~~~java
@Bean
SqsMessageListenerContainerFactory<Object> sqsListenerContainerFactory(
        SqsAsyncClient sqsAsyncClient) {

    return SqsMessageListenerContainerFactory
            .builder()
            .sqsAsyncClient(sqsAsyncClient)
            .configure(options -> options
                    .messagesPerPoll(10)
                    .pollTimeout(Duration.ofSeconds(20))
                    .maxConcurrentMessages(20)
                    .acknowledgementMode(AcknowledgementMode.ON_SUCCESS))
            .build();
}
~~~

Tên option có thể thay đổi theo major version; kiểm tra reference documentation và IDE API của version đang dùng.

### 13.7. Concurrency không phải throughput miễn phí

Tăng maxConcurrentMessages quá cao có thể làm database, provider hoặc memory quá tải. Tuning theo bottleneck thật:

~~~text
consumer concurrency
≤ database capacity
≤ external provider quota
≤ CPU/memory capacity
~~~

### 13.8. FIFO listener

Với FIFO:

- Message cùng MessageGroupId phải được xử lý theo thứ tự.
- Không dùng executor phá ordering.
- Group bị block khi message trước chưa thành công hoặc chưa hết visibility.
- Scale bằng nhiều MessageGroupId.
- Idempotency vẫn cần dù queue có FIFO deduplication.

---

## 14. Error handling trong Spring

### 14.1. Lỗi retryable

~~~java
public void handle(EventEnvelope<OrderCreated> message) {
    try {
        downstreamClient.notify(message.payload());
    } catch (TemporaryDownstreamException ex) {
        throw new RetryableMessageException(ex);
    }
}
~~~

Nếu listener không ack khi lỗi, message sẽ visible lại theo visibility timeout hoặc đi DLQ khi vượt maxReceiveCount.

### 14.2. Lỗi permanent

Ví dụ:

- Schema không hợp lệ.
- Thiếu order ID.
- Business state không thể tồn tại.
- Version không hỗ trợ.
- Payload vi phạm invariant.

Không retry vô hạn. Có thể để exception đi theo redrive policy để message vào DLQ và lưu error classification.

### 14.3. Không nuốt exception

Sai:

~~~java
@SqsListener("orders-standard")
public void handle(String body) {
    try {
        service.process(body);
    } catch (Exception ex) {
        log.error("failed", ex);
        // không throw lại: framework có thể coi là success và delete
    }
}
~~~

Nếu đã log nhưng không throw hoặc không manual strategy, framework có thể coi listener hoàn tất. Message có thể bị delete dù business thất bại.

Đúng hơn:

~~~java
@SqsListener("orders-standard")
public void handle(String body) {
    try {
        service.process(body);
    } catch (RetryableException ex) {
        throw ex;
    } catch (PermanentException ex) {
        throw ex;
    }
}
~~~

Mapping exception cụ thể phụ thuộc error handler và framework version.

---

## 15. Correlation ID và tracing

Envelope nên truyền:

~~~json
{
  "eventId": "evt-001",
  "correlationId": "checkout-789",
  "operationId": "order-created:order-123",
  "producer": "order-service",
  "eventType": "OrderCreated",
  "eventVersion": 1
}
~~~

Consumer log:

~~~text
messageId=msg-001
eventId=evt-001
correlationId=checkout-789
operationId=order-created:order-123
consumer=notification-worker
attempt=2
~~~

Correlation ID giúp nối HTTP request → Order API → SQS → worker → provider. Không đưa access token hoặc toàn bộ payload nhạy cảm vào tracing baggage.

---

## 16. Production-like lab

### 16.1. Topology

~~~text
Order API
   │
   ▼
orders-standard
   │ lỗi nhiều lần
   ▼
orders-dlq

Payment API
   │ MessageGroupId = orderId
   ▼
payment.fifo
   │ lỗi nhiều lần
   ▼
payment-dlq.fifo
~~~

### 16.2. Producer requirements

- IAM role chỉ có SendMessage.
- Envelope có eventId, operationId, correlationId.
- JSON serialization ổn định.
- Publish metrics.
- Không log secret.
- Có outbox nếu order DB và publish cần atomic intent.

### 16.3. Consumer requirements

- IAM role chỉ có Receive/Delete/Visibility/GetAttributes.
- Long polling.
- Visibility timeout phù hợp.
- Idempotent operation.
- Delete sau thành công.
- Không nuốt exception.
- Structured logs.
- Graceful shutdown.
- Custom metrics.

### 16.4. Failure scenarios

Chạy lần lượt:

1. Kill consumer trước khi xử lý.
2. Kill consumer sau business update nhưng trước delete.
3. Làm downstream trả 503.
4. Làm payload sai schema.
5. Làm visibility timeout ngắn hơn processing time.
6. Tắt quyền DeleteMessage.
7. Tắt quyền KMS decrypt nếu dùng customer key.
8. Đẩy message vào DLQ.
9. Sửa lỗi rồi redrive một message.
10. Tăng producer rate để tạo backlog.

Ghi lại visible/not visible, receive count, duplicate side effect, queue age, DLQ count, alarm và recovery time.

---

## 17. Checklist security

- [ ] Application dùng IAM role hoặc temporary credentials.
- [ ] Producer và consumer dùng role khác nhau nếu có thể.
- [ ] Policy giới hạn đúng queue ARN.
- [ ] Không cấp sqs:* cho runtime role.
- [ ] Queue không public ngoài chủ đích.
- [ ] Cross-account policy có principal và condition cụ thể.
- [ ] HTTPS/TLS được enforce.
- [ ] SSE được bật.
- [ ] KMS key policy cấp đúng producer/consumer/service principal.
- [ ] IAM Access Analyzer được dùng để rà cross-account access.
- [ ] Queue name không chứa PII hoặc secret.
- [ ] Payload nhạy cảm không xuất hiện trong log.
- [ ] S3 reference/payload lớn có IAM và lifecycle phù hợp.

---

## 18. Bài tập thực hành

### Bài 1: Visibility timeout

Tạo worker xử lý job trong 45 giây.

1. Đặt visibility timeout 10 giây.
2. Gửi một message.
3. Chạy hai consumer.
4. Quan sát duplicate processing.
5. Tăng timeout lên 60 giây.
6. Chạy lại.
7. Thêm ChangeMessageVisibility cho job dài.
8. So sánh queue metrics.

Giải thích vì sao tăng timeout không thay thế idempotency.

### Bài 2: Idempotent consumer

Thiết kế consumer cho PaymentCaptured:

- Có thể nhận cùng message nhiều lần.
- Không được ghi payment success hai lần.
- External provider hỗ trợ idempotency key.
- Database có unique constraint.

Đưa ra operation ID, bảng processed operation, transaction boundary, retry logic và trạng thái khi payment đã CAPTURED.

### Bài 3: DLQ và redrive

Tạo:

~~~text
orders-standard
orders-dlq
~~~

Cấu hình maxReceiveCount bằng 3. Gửi một message luôn throw exception.

Quan sát receive count, visibility timeout, thời điểm chuyển DLQ, CloudWatch metrics và cách redrive sau khi sửa handler.

### Bài 4: IAM least privilege

Tạo hai role policy:

1. Producer chỉ SendMessage/SendMessageBatch.
2. Consumer chỉ Receive/Delete/ChangeVisibility/GetQueueAttributes.

Thử gọi action ngoài quyền và ghi AccessDenied. Sau đó thêm KMS encryption rồi xác định permission KMS cần bổ sung.

### Bài 5: CloudWatch alarm

Tạo alarm:

- Visible messages vượt ngưỡng trong 5 phút.
- Oldest message age vượt SLA.
- DLQ visible messages lớn hơn 0.
- Không có delete progress trong lúc producer vẫn gửi.

Viết runbook ngắn cho từng alarm.

### Bài 6: Spring listener

Tạo Spring Boot app:

- SqsTemplate gửi OrderCreated.
- SqsListener nhận message.
- JSON envelope.
- ON_SUCCESS acknowledgement.
- Listener concurrency giới hạn.
- Fake downstream có lỗi tạm thời.
- Idempotency store giả lập.
- Metrics processing success/failure.

### Bài 7: FIFO blocking

Tạo FIFO queue với:

~~~text
group order-1: m1, m2, m3
group order-2: m4, m5, m6
~~~

Làm m1 thất bại nhiều lần. Quan sát message nào của order-1 bị block, order-2 có tiếp tục không và khi nào DLQ giúp group tiếp tục.

---

## 19. Câu hỏi tự kiểm tra

1. Vì sao SQS DeleteMessage đóng vai trò acknowledgement?
2. Điều gì xảy ra khi consumer crash trước khi delete?
3. Visibility timeout quá ngắn tạo ra rủi ro nào?
4. Visibility timeout quá dài ảnh hưởng recovery ra sao?
5. Vì sao ChangeMessageVisibility không nên được gọi vô hạn?
6. Idempotency key nên nằm ở đâu trong flow payment?
7. Khi nào lỗi nên để message redeliver và khi nào nên vào DLQ?
8. maxReceiveCount có nghĩa handler chắc chắn chạy đúng số lần đó không?
9. Vì sao DLQ Standard phải là Standard và DLQ FIFO phải là FIFO?
10. Identity policy khác resource policy ở đâu?
11. Vì sao producer và consumer nên dùng role khác nhau?
12. Khi dùng customer managed KMS key, vì sao SQS permission chưa đủ?
13. Metric nào phản ánh SLA tốt hơn queue depth trong nhiều trường hợp?
14. Vì sao DLQ cần alarm trên ApproximateNumberOfMessagesVisible?
15. Long polling và batch API giảm chi phí theo cơ chế nào?
16. ON_SUCCESS, ALWAYS và MANUAL khác nhau như thế nào?
17. Vì sao nuốt exception trong listener có thể làm mất message?
18. Concurrency quá cao gây những rủi ro nào?
19. Vì sao FIFO một group bị block nhưng group khác có thể tiếp tục?
20. Redrive message từ DLQ cần kiểm tra gì trước khi thực hiện?

---

## 20. Checklist hoàn thành Tuần 8

- [ ] Mình chọn visibility timeout dựa trên processing time và p99.
- [ ] Mình biết dùng ChangeMessageVisibility cho job dài.
- [ ] Mình hiểu DeleteMessage là acknowledgement của SQS.
- [ ] Mình có idempotency cho duplicate delivery.
- [ ] Mình phân loại retryable/permanent/unknown outcome.
- [ ] Mình cấu hình được source queue, DLQ và maxReceiveCount.
- [ ] Mình biết DLQ phải cùng loại Standard/FIFO với source.
- [ ] Mình có redrive runbook và không replay mù quáng.
- [ ] Mình phân biệt identity policy và resource policy.
- [ ] Mình dùng IAM role thay vì hardcode credentials.
- [ ] Mình hiểu SSE-SQS và SSE-KMS.
- [ ] Mình biết kiểm tra KMS key policy khi bị AccessDenied.
- [ ] Mình theo dõi visible, not visible, oldest age và DLQ.
- [ ] Mình biết long polling tối đa 20 giây và batch giúp giảm API request.
- [ ] Mình cấu hình được SqsTemplate và SqsListener.
- [ ] Mình hiểu acknowledgement mode ON_SUCCESS/ALWAYS/MANUAL.
- [ ] Mình giới hạn listener concurrency theo downstream capacity.
- [ ] Mình gắn correlation ID và operation ID.
- [ ] Mình chạy lab crash, duplicate, DLQ, IAM và CloudWatch.

## Kết luận

SQS reliability không đến từ một setting duy nhất. Visibility timeout giảm giao đồng thời nhưng không loại bỏ duplicate. DLQ ngăn poison message retry vô hạn nhưng không tự sửa nguyên nhân. IAM bảo vệ quyền truy cập nhưng cần phối hợp identity policy, queue policy và KMS key policy. CloudWatch cho biết queue đang khỏe hay nghẽn, còn application metrics giúp nhìn thấy duplicate, processing failure và delete failure.

Với Spring Cloud AWS, hãy bắt đầu bằng listener acknowledgement theo success, long polling, concurrency có giới hạn, JSON envelope có operation ID và consumer idempotent. Sau đó thêm retry classification, DLQ/redrive, IAM least privilege, encryption và alarm theo SLA.

Mục tiêu hoàn thành Tuần 8 là consumer không làm mất message khi process crash, không tạo duplicate business effect khi message redeliver, và team có thể phát hiện, điều tra, sửa rồi redrive message an toàn.

## Nguồn chính thức

- [Amazon SQS visibility timeout](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-visibility-timeout.html)
- [Amazon SQS dead-letter queues](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-dead-letter-queues.html)
- [Amazon SQS CloudWatch metrics](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-available-cloudwatch-metrics.html)
- [Amazon SQS IAM integration](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/security_iam_service-with-iam.html)
- [Amazon SQS security best practices](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-security-best-practices.html)
- [Least privilege for encrypted SQS queues](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-least-privilege-policy.html)
- [Spring Cloud AWS SQS reference](https://docs.awspring.io/spring-cloud-aws/docs/4.0.2/reference/html/index.html)
- [Spring Cloud AWS SQS architecture](https://github.com/awspring/spring-cloud-aws/blob/main/spring-cloud-aws-sqs/README.md)

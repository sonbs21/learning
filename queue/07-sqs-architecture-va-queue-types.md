# Tuần 7: SQS architecture và queue types

## 1. Mục tiêu bài học

Sau bài này, bạn có thể:

- Mô tả kiến trúc managed queue của Amazon SQS.
- Phân biệt AWS account, Region, queue URL và ARN.
- Hiểu vòng đời: SendMessage → ReceiveMessage → DeleteMessage.
- Phân biệt Standard Queue và FIFO Queue.
- Hiểu at-least-once delivery và best-effort ordering của Standard Queue.
- Dùng MessageGroupId, MessageDeduplicationId và content-based deduplication của FIFO.
- Hiểu visibility timeout, message retention, delivery delay và message timer.
- Phân biệt short polling và long polling.
- Biết khi nào dùng batch send, receive và delete.
- Nhận diện các giới hạn quan trọng về kích thước, thời gian và API.
- Chọn Standard hay FIFO theo ordering, throughput và duplicate control.
- Viết flow gửi/nhận/xóa message bằng AWS CLI và AWS SDK for Java.

> Bài này tập trung vào kiến trúc và queue types. Reliability nâng cao, IAM, KMS, CloudWatch, DLQ và Spring Cloud AWS sẽ được học ở Tuần 8.

---

## 2. SQS là gì?

Amazon Simple Queue Service (SQS) là dịch vụ message queue managed của AWS. AWS vận hành phần broker infrastructure; ứng dụng tương tác với queue thông qua API.

Khác với RabbitMQ, SQS không yêu cầu bạn vận hành cluster broker, exchange hoặc channel. Ứng dụng làm việc trực tiếp với queue URL:

~~~text
Producer
   │ SendMessage
   ▼
Amazon SQS queue
   │ ReceiveMessage
   ▼
Consumer
   │ xử lý thành công
   │ DeleteMessage
   ▼
Message biến mất khỏi queue
~~~

SQS phù hợp khi:

- Muốn dùng queue mà không tự vận hành broker.
- Hệ thống chạy chủ yếu trên AWS.
- Cần tích hợp với ECS, EKS, Lambda, EC2 hoặc EventBridge.
- Muốn scale theo nhu cầu mà không quản lý node broker.
- Chấp nhận mô hình poll/receive/delete.
- Có thể thiết kế consumer theo at-least-once và idempotency.

SQS không phải event log dài hạn như Kafka và không có exchange/routing model như RabbitMQ. Một queue SQS cũng không tự tạo nhiều subscription độc lập như pub/sub topic; muốn fan-out thường kết hợp SNS hoặc EventBridge với nhiều SQS queue.

---

## 3. Các khái niệm AWS cần nắm

### 3.1. AWS account

AWS account là ranh giới quản trị, billing, IAM và tài nguyên. Queue thuộc về một account cụ thể, nhưng có thể cấp quyền cross-account bằng IAM policy và resource policy.

### 3.2. Region

SQS queue nằm trong một AWS Region cụ thể:

~~~text
Account A
├── ap-southeast-1
│   └── orders-standard
└── us-east-1
    └── orders-standard
~~~

Hai queue cùng tên ở hai Region vẫn là hai queue khác nhau.

Region ảnh hưởng:

- Độ trễ network.
- Nơi lưu dữ liệu.
- Chi phí truyền dữ liệu.
- Yêu cầu compliance.
- Khoảng cách giữa producer, consumer và downstream.

Không nên đặt producer ở một Region và consumer ở Region khác nếu không có lý do rõ ràng.

### 3.3. Queue URL

Queue URL là địa chỉ dùng trong request API:

~~~text
https://sqs.ap-southeast-1.amazonaws.com/123456789012/orders-standard
~~~

AWS SDK thường cần queue URL để gửi, nhận, xem thuộc tính và xóa message.

### 3.4. ARN

ARN là định danh tài nguyên dùng nhiều trong IAM, policy, Lambda event source mapping và các dịch vụ AWS khác:

~~~text
arn:aws:sqs:ap-southeast-1:123456789012:orders-standard
~~~

Queue URL và ARN phục vụ các mục đích khác nhau; không nên thay thế lẫn nhau trong cấu hình.

### 3.5. Tên queue

Quy ước tên nên thể hiện môi trường và domain:

~~~text
dev-orders-standard
staging-orders-standard
prod-orders-standard
prod-payment.fifo
~~~

Không đưa PII, token, số thẻ hoặc dữ liệu nhạy cảm vào queue name. FIFO queue phải có hậu tố .fifo. Queue type không thể đổi sau khi queue được tạo; nếu chọn sai, thường phải tạo queue mới và migrate flow.

---

## 4. Vòng đời message trong SQS

### 4.1. SendMessage

Producer gửi message bằng SendMessage:

~~~text
Producer ── SendMessage ──▶ SQS
                              └── message visible sau delay nếu có
~~~

Response thành công cho biết SQS đã chấp nhận request theo semantics của API. Nó không có nghĩa consumer đã xử lý business operation.

Message body có thể là text, JSON hoặc XML hợp lệ. Trong backend, JSON envelope thường phù hợp:

~~~json
{
  "messageId": "msg-001",
  "eventType": "OrderCreated",
  "eventVersion": 1,
  "occurredAt": "2026-09-26T08:30:00Z",
  "correlationId": "checkout-789",
  "payload": {
    "orderId": "order-123",
    "customerId": "customer-456"
  }
}
~~~

Message ID do SQS cấp không thay thế hoàn toàn cho business operation ID. Producer vẫn nên tạo event ID hoặc operation ID để trace và dedup.

### 4.2. ReceiveMessage

Consumer gọi ReceiveMessage để lấy message. SQS trả về body, attributes và một ReceiptHandle:

~~~text
Consumer ── ReceiveMessage ──▶ SQS
Consumer ◀── body + receipt handle
~~~

Khi message được receive, message chưa bị xóa. Nó tạm thời invisible trong visibility timeout để consumer có cơ hội xử lý.

Nếu consumer crash hoặc không delete trước khi timeout kết thúc, message có thể xuất hiện lại.

### 4.3. DeleteMessage

Sau khi business processing thành công, consumer gọi DeleteMessage với ReceiptHandle:

~~~text
Receive
   ↓
process thành công
   ↓
DeleteMessage(receiptHandle)
   ↓
message bị xóa khỏi queue
~~~

Delete là bước xác nhận consumer đã xử lý xong. Receive thành công không phải ack cuối cùng.

Receipt handle là định danh của lần receive cụ thể. Nếu message được receive lại, lần receive mới có receipt handle mới; consumer phải dùng receipt handle mới nhất khi delete.

### 4.4. Không dùng MessageId để delete

Flow sai:

~~~text
receive message
delete bằng MessageId
~~~

Flow đúng:

~~~text
receive message
lấy ReceiptHandle
process thành công
delete bằng ReceiptHandle
~~~

MessageId dùng để trace hoặc nhận diện message. ReceiptHandle dùng để thao tác với bản message đang invisible.

---

## 5. Visibility timeout

### 5.1. Khái niệm

Visibility timeout là khoảng thời gian message tạm thời không xuất hiện cho consumer khác sau khi được receive:

~~~text
t = 0s       receive message
             message chuyển sang in-flight/invisible

t = 30s      visibility timeout kết thúc
             nếu chưa delete, message có thể visible lại
~~~

Giá trị mặc định hiện hành là 30 giây. Có thể cấu hình từ 0 giây đến tối đa 12 giờ.

Visibility timeout không phải retention và không phải delay:

| Cơ chế | Ý nghĩa |
|---|---|
| Visibility timeout | Ẩn message sau khi đã receive |
| Retention | Giữ message tối đa bao lâu nếu chưa delete |
| Delivery delay | Ẩn message ngay sau khi gửi |
| Message timer | Delay riêng cho một message |

### 5.2. Chọn visibility timeout

Nên bắt đầu từ p99 processing time, cộng thêm thời gian cho database, downstream và network:

~~~text
visibility timeout
  > p99 xử lý
  + thời gian retry nội bộ nếu có
  + margin network
~~~

Không nên chỉ đặt timeout bằng thời gian trung bình. Nếu phần lớn job xong trong 5 giây nhưng một số job mất 90 giây, timeout 10 giây sẽ tạo duplicate cho job dài.

### 5.3. Job dài

Với job có thời gian xử lý không cố định:

- Đặt timeout ban đầu vừa phải.
- Dùng ChangeMessageVisibility để gia hạn khi consumer còn làm việc.
- Có heartbeat hoặc lease có kiểm soát.
- Đảm bảo gia hạn không kéo dài vô hạn.
- Nếu job không thể hoàn tất, chuyển sang trạng thái failed hoặc DLQ theo policy.

~~~text
receive
  ↓ visibility 60s
xử lý vẫn chạy
  ↓ ChangeMessageVisibility +60s
xử lý tiếp
  ↓ thành công
DeleteMessage
~~~

### 5.4. Visibility timeout không loại bỏ duplicate

Visibility timeout giúp giảm khả năng hai consumer nhận cùng message cùng lúc, nhưng không phải lock tuyệt đối. Standard Queue vẫn có at-least-once delivery; consumer luôn cần idempotent.

---

## 6. Message retention và delivery delay

### 6.1. Message retention

SQS giữ message chưa bị delete trong khoảng retention được cấu hình:

- Mặc định: 4 ngày.
- Tối thiểu: 1 phút.
- Tối đa: 14 ngày.

Retention là giới hạn thời gian replay hoặc chờ consumer. Nó không phải backup dài hạn.

Nếu business yêu cầu replay sau nhiều tuần hoặc nhiều tháng, SQS không nên là event log chính. Có thể lưu event ở database, S3, Kafka hoặc hệ thống archive phù hợp.

### 6.2. Delivery delay

Delay queue làm message chưa visible trong một khoảng thời gian sau khi được gửi. Delay tối đa hiện hành là 15 phút:

~~~text
SendMessage
   ↓ DelaySeconds = 60
message chưa visible trong 60 giây
   ↓
consumer mới có thể receive
~~~

Delivery delay phù hợp cho notification ngắn hoặc tạo khoảng đệm trước lần xử lý đầu tiên.

Không nên dùng delay queue làm scheduler dài hạn. Với lịch chạy phức tạp hoặc thời gian xa, dùng EventBridge Scheduler hoặc workflow phù hợp hơn.

### 6.3. Message timer

Message timer cho phép đặt delay riêng trên từng message. FIFO không hỗ trợ timer riêng cho từng message. Nếu cần schedule FIFO message, cần thiết kế flow trung gian.

---

## 7. Short polling và long polling

### 7.1. Short polling

Short polling gọi ReceiveMessage và trả response ngay khi không lấy được message:

~~~text
Consumer ── poll ──▶ SQS
Consumer ◀── empty response
Consumer ── poll ──▶ SQS
Consumer ◀── message
~~~

Nhược điểm:

- Nhiều empty response.
- Tăng số API request.
- Có thể nhận empty dù message đang tồn tại ở server khác.
- Tăng chi phí và noise metrics.

### 7.2. Long polling

Long polling đặt WaitTimeSeconds lớn hơn 0; SQS chờ tối đa thời gian đó để trả message. Giá trị tối đa hiện hành là 20 giây:

~~~text
Consumer ── long poll ──▶ SQS
             chờ message
Consumer ◀── message hoặc timeout
~~~

Lợi ích:

- Giảm empty response.
- Giảm request không cần thiết.
- Giảm false empty trong nhiều tình huống.
- Phù hợp với worker luôn chạy và chờ job.

Khi dùng long polling:

- HTTP response timeout phải lớn hơn WaitTimeSeconds.
- Thường cấu hình worker poll riêng cho từng queue.
- Phải xử lý response rỗng sau khi wait hết thời gian.
- Dừng poll graceful khi ứng dụng shutdown.
- Không giữ một thread duy nhất chờ nhiều queue nếu queue khác có thể bị đói.

### 7.3. Khi nào short polling vẫn hợp lý?

Short polling có thể phù hợp khi test local, cần vòng lặp đơn giản hoặc client không thể giữ HTTP connection lâu. Với worker production, long polling thường là lựa chọn hợp lý hơn.

---

## 8. Batch API

SQS hỗ trợ batch để giảm số API request:

- SendMessageBatch.
- DeleteMessageBatch.
- ReceiveMessage trả nhiều message trong một lần gọi.

Một batch request có thể chứa tối đa 10 message theo quota hiện hành.

### 8.1. Batch send

~~~text
Producer
  └── SendMessageBatch([m1, m2, ..., m10])
                         ↓
                       SQS
~~~

Batch send không có nghĩa cả batch luôn atomic theo business. Có thể có partial success:

~~~text
batch result
├── m1 success
├── m2 success
├── m3 failure
└── m4 success
~~~

Producer phải retry riêng entry thất bại, tránh gửi lại toàn bộ batch nếu không có idempotency.

### 8.2. Batch receive

Một lần receive có thể trả nhiều message. Nếu xử lý song song:

- Theo dõi receipt handle riêng cho từng message.
- Delete riêng hoặc gom vào batch delete.
- Một message fail không nên làm mất trạng thái của message khác.
- Giới hạn concurrency để không overload downstream.

### 8.3. Batch delete

Delete batch cũng có thể partial failure:

~~~text
DeleteMessageBatch
├── m1 deleted
├── m2 deleted
├── m3 failed
└── m4 deleted
~~~

Message m3 có thể xuất hiện lại sau visibility timeout. Consumer phải retry delete phù hợp.

---

## 9. Standard Queue

### 9.1. Đặc điểm

Standard Queue ưu tiên throughput, scale và tính đơn giản vận hành:

- At-least-once delivery.
- Có thể giao duplicate.
- Không đảm bảo thứ tự toàn cục.
- Có thể dùng nhiều consumer song song.
- Phù hợp với work queue và task processing.
- Consumer phải idempotent.

~~~text
Producer ──▶ Standard Queue
                ├── Consumer A
                ├── Consumer B
                └── Consumer C
~~~

### 9.2. Best-effort ordering

Standard Queue có thể thường xuyên trả message theo thứ tự gửi trong một số tình huống, nhưng không được thiết kế để đảm bảo thứ tự nghiệp vụ.

Không được giả định:

~~~text
m1 = OrderStatus: PAID
m2 = OrderStatus: REFUNDED

m1 luôn được xử lý trước m2
~~~

Nếu ordering quan trọng, dùng FIFO theo group hoặc thiết kế state transition có version/sequence number để từ chối message cũ.

### 9.3. Use case phù hợp

Standard Queue phù hợp với:

- Gửi email.
- Tạo thumbnail.
- Xử lý file.
- Tạo report.
- Webhook delivery.
- Background job.
- Import từng record.
- Tác vụ có thể retry và idempotent.

### 9.4. Khi không nên dùng Standard

Không nên chọn Standard nếu:

- Không thể chịu duplicate.
- Business bắt buộc thứ tự trong một entity.
- Consumer không thể làm idempotency.
- Message đến trễ hoặc đảo thứ tự tạo ra sai kết quả không thể sửa.
- Cần replay dài hạn như event log.

---

## 10. FIFO Queue

### 10.1. Đặc điểm

FIFO Queue được thiết kế cho flow cần ordering và duplicate control cao hơn:

- Queue name phải kết thúc bằng .fifo.
- Mỗi message phải có MessageGroupId.
- Message trong cùng group được xử lý theo thứ tự.
- Message đang in-flight có thể chặn message sau trong cùng group.
- MessageDeduplicationId giúp SQS nhận diện message gửi trùng trong deduplication interval.
- Có thể bật content-based deduplication.
- Throughput phụ thuộc cách phân bố message group và cấu hình throughput.

FIFO không biến business flow thành transaction exactly-once. Nó giúp kiểm soát duplicate ở tầng queue trong một khoảng thời gian và giữ ordering theo group; side effect của consumer vẫn cần idempotency.

### 10.2. MessageGroupId

MessageGroupId là partition logic của FIFO queue:

~~~text
payment.fifo
├── group: order-101
│   ├── PaymentRequested
│   ├── PaymentCaptured
│   └── ReceiptRequested
└── group: order-102
    ├── PaymentRequested
    └── PaymentFailed
~~~

Trong cùng một group, message được xử lý tuần tự. Các group khác nhau có thể xử lý song song:

~~~text
order-101 ──▶ Consumer A
order-102 ──▶ Consumer B
order-103 ──▶ Consumer C
~~~

### 10.3. Chọn MessageGroupId

| Nhu cầu | Group ID gợi ý |
|---|---|
| Order phải tuần tự | orderId |
| Account balance phải tuần tự | accountId |
| Trạng thái thiết bị phải tuần tự | deviceId |
| Mọi message toàn hệ thống tuần tự | Một group cố định, thường không nên |

Một group cố định tạo global serialization:

~~~text
mọi message ──▶ MessageGroupId = "all"
             └── chỉ một chuỗi xử lý tuần tự
~~~

Điều này làm mất parallelism và tạo head-of-line blocking. Muốn FIFO song song, phải có nhiều group.

### 10.4. MessageDeduplicationId

MessageDeduplicationId là khóa để SQS nhận diện các message gửi trùng trong khoảng deduplication window của FIFO:

~~~text
MessageDeduplicationId = "capture:payment-123"
~~~

Nếu producer retry cùng operation, dùng cùng deduplication ID. Không nên tạo UUID mới cho mỗi retry nếu mục tiêu là nói “đây vẫn là cùng một operation”.

Tuy nhiên:

- Deduplication ở SQS không thay thế idempotency của consumer.
- Deduplication window có giới hạn.
- Republish sau window vẫn có thể tạo message mới.
- Downstream provider vẫn cần idempotency key.
- Cùng payload không nhất thiết nghĩa là cùng business operation.

### 10.5. Content-based deduplication

Nếu bật content-based deduplication, SQS tạo deduplication ID từ hash của message body. Message attributes không được tính vào hash.

Điều này tiện khi producer không tự cung cấp deduplication ID, nhưng có rủi ro:

- Hai operation khác nhau có cùng body có thể bị coi là duplicate trong window.
- Thay đổi metadata nhưng giữ body có thể không tạo message mới như kỳ vọng.
- Business operation ID explicit thường dễ kiểm soát hơn.

### 10.6. FIFO và concurrency

FIFO ordering chỉ nghiêm ngặt trong cùng MessageGroupId:

~~~text
1 group  ×  1 consumer flow  = ordering, ít parallelism
100 group × nhiều consumer    = ordering theo entity, có parallelism
~~~

Thêm consumer không thể phá giới hạn của một group. Nếu một group có job chậm, các message sau trong group bị chặn.

---

## 11. So sánh Standard và FIFO

| Tiêu chí | Standard Queue | FIFO Queue |
|---|---|---|
| Delivery | At-least-once | Duplicate control trong dedup window |
| Ordering | Không đảm bảo | Theo từng MessageGroupId |
| Parallelism | Cao, không theo group | Theo số group và throughput mode |
| MessageGroupId | Không dùng cho strict ordering | Bắt buộc |
| Deduplication ID | Không có FIFO dedup semantics | Có |
| Queue name | Tên thông thường | Phải kết thúc .fifo |
| Use case | Background job, email, work queue | Payment/order state theo entity |
| Rủi ro chính | Duplicate và out-of-order | Head-of-line blocking, group serialization |
| Consumer | Idempotent, chịu reorder | Idempotent, tôn trọng group order |

Quy tắc chọn nhanh:

~~~text
Không cần ordering theo entity?
  └── Standard

Cần ordering theo entity?
  ├── Có thể dùng group key?
  │   └── FIFO + MessageGroupId
  └── Cần event log/replay dài hạn?
      └── Xem xét Kafka thay vì SQS
~~~

---

## 12. API lifecycle bằng AWS CLI

### 12.1. Tạo Standard Queue

~~~bash
aws sqs create-queue \
  --queue-name dev-orders-standard \
  --region ap-southeast-1
~~~

Lấy queue URL:

~~~bash
aws sqs get-queue-url \
  --queue-name dev-orders-standard \
  --region ap-southeast-1
~~~

Lấy attributes:

~~~bash
aws sqs get-queue-attributes \
  --queue-url "$QUEUE_URL" \
  --attribute-names All \
  --region ap-southeast-1
~~~

Trong script thật, resolve queue URL từ environment hoặc config management; không commit credentials hoặc config nhạy cảm.

### 12.2. Gửi message

~~~bash
aws sqs send-message \
  --queue-url "$QUEUE_URL" \
  --message-body '{"eventType":"OrderCreated","orderId":"order-123"}' \
  --region ap-southeast-1
~~~

### 12.3. Receive message

~~~bash
aws sqs receive-message \
  --queue-url "$QUEUE_URL" \
  --max-number-of-messages 10 \
  --wait-time-seconds 20 \
  --visibility-timeout 60 \
  --attribute-names All \
  --region ap-southeast-1
~~~

Kết quả chứa body và receipt handle. Khi test thủ công, dùng chính receipt handle từ response.

### 12.4. Xóa message

~~~bash
aws sqs delete-message \
  --queue-url "$QUEUE_URL" \
  --receipt-handle "$RECEIPT_HANDLE" \
  --region ap-southeast-1
~~~

Chỉ delete sau khi xử lý thành công.

### 12.5. Tạo FIFO Queue

~~~bash
aws sqs create-queue \
  --queue-name dev-payment.fifo \
  --attributes FifoQueue=true,ContentBasedDeduplication=false \
  --region ap-southeast-1
~~~

Gửi FIFO message:

~~~bash
aws sqs send-message \
  --queue-url "$FIFO_QUEUE_URL" \
  --message-body '{"command":"CapturePayment","paymentId":"pay-123"}' \
  --message-group-id "order-123" \
  --message-deduplication-id "capture:pay-123:v1" \
  --region ap-southeast-1
~~~

Nếu không dùng content-based deduplication, producer cần cung cấp MessageDeduplicationId.

---

## 13. Ví dụ AWS SDK for Java 2.x

### 13.1. Khởi tạo client

~~~java
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;

SqsClient sqs = SqsClient.builder()
        .region(Region.AP_SOUTHEAST_1)
        .build();
~~~

AWS SDK nên lấy credentials từ default credential provider chain, IAM role hoặc workload identity. Không hardcode access key/secret key trong source code.

### 13.2. Gửi Standard message

~~~java
var request = SendMessageRequest.builder()
        .queueUrl(queueUrl)
        .messageBody(jsonPayload)
        .messageAttributes(Map.of(
                "eventType", MessageAttributeValue.builder()
                        .dataType("String")
                        .stringValue("OrderCreated")
                        .build()
        ))
        .build();

var response = sqs.sendMessage(request);
String messageId = response.messageId();
~~~

Message ID do SQS trả về hữu ích cho log, nhưng payload vẫn nên có event ID hoặc operation ID do ứng dụng quản lý.

### 13.3. Gửi FIFO message

~~~java
var request = SendMessageRequest.builder()
        .queueUrl(fifoQueueUrl)
        .messageBody(jsonPayload)
        .messageGroupId(orderId)
        .messageDeduplicationId("order-created:" + orderId)
        .build();

sqs.sendMessage(request);
~~~

Retry cùng operation cần dùng cùng deduplication ID. Nếu mỗi retry tạo ID mới, SQS có thể coi là message mới.

### 13.4. Receive bằng long polling

~~~java
var request = ReceiveMessageRequest.builder()
        .queueUrl(queueUrl)
        .maxNumberOfMessages(10)
        .waitTimeSeconds(20)
        .visibilityTimeout(60)
        .messageAttributeNames("All")
        .attributeNamesWithStrings("All")
        .build();

var response = sqs.receiveMessage(request);

for (var message : response.messages()) {
    try {
        handler.handle(message.body());
        sqs.deleteMessage(delete -> delete
                .queueUrl(queueUrl)
                .receiptHandle(message.receiptHandle()));
    } catch (RetryableException ex) {
        log.warn("Retryable SQS failure; message will reappear", ex);
    } catch (PermanentException ex) {
        log.error("Permanent SQS failure", ex);
    }
}
~~~

Đây là skeleton học tập. Production code cần thêm idempotency, timeout downstream, retry policy, metrics, structured logging, graceful shutdown, DLQ và xử lý partial failure khi batch.

### 13.5. Thay đổi visibility timeout

~~~java
sqs.changeMessageVisibility(request -> request
        .queueUrl(queueUrl)
        .receiptHandle(message.receiptHandle())
        .visibilityTimeout(120));
~~~

Gia hạn chỉ nên xảy ra khi consumer thật sự còn xử lý; không dùng visibility timeout để giữ message vô hạn.

---

## 14. SQS và acknowledgement

Trong SQS, flow tương đương acknowledgement là:

~~~text
ReceiveMessage
    ↓
xử lý thành công
    ↓
DeleteMessage
~~~

So sánh khái niệm:

| RabbitMQ | SQS |
|---|---|
| Consumer ack | DeleteMessage sau xử lý |
| Unacked message | Message invisible/in-flight |
| Requeue | Không delete và chờ visibility timeout |
| Queue | SQS queue |
| Exchange/binding | Không có trực tiếp; fan-out dùng dịch vụ khác hoặc nhiều queue |

Đây là so sánh về ý tưởng, không phải API tương đương tuyệt đối. SQS là managed pull queue; RabbitMQ có mô hình broker AMQP phong phú hơn.

---

## 15. Message size và giới hạn API

Các giới hạn cần nhớ:

- Message body tối đa hiện hành: 1 MiB.
- Batch tối đa: 10 message.
- Retention: 1 phút đến 14 ngày, mặc định 4 ngày.
- Visibility timeout: 0 giây đến 12 giờ, mặc định 30 giây.
- Delivery delay: 0 đến 15 phút.
- Long polling: tối đa 20 giây.
- FIFO queue cần MessageGroupId.
- FIFO queue phải có hậu tố .fifo.

Đây là quota dịch vụ, không phải SLA business. Kiểm tra quota theo Region và loại queue trước benchmark production.

### Payload lớn hơn 1 MiB

Không nên nhét payload lớn trực tiếp vào SQS:

~~~text
Producer
  ├── lưu payload lớn vào S3
  └── gửi SQS message chứa S3 bucket/key/version
~~~

Consumer:

~~~text
SQS message
  └── đọc object từ S3
      └── xử lý
~~~

Cần thiết kế:

- IAM permission cho object.
- TTL/lifecycle của object.
- Xóa object sau khi hoàn tất.
- Xử lý object không tồn tại.
- Mã hóa và audit.
- Không log S3 key hoặc payload nhạy cảm tùy tiện.

---

## 16. Kiến trúc fan-out với SQS

Một SQS queue phù hợp cho một work stream. Nếu một event cần đến nhiều consumer độc lập:

~~~text
Order Service
     │
     ▼
SNS topic hoặc EventBridge
     ├── orders-email queue
     ├── orders-analytics queue
     └── orders-audit queue
~~~

Mỗi queue có backlog, retry và DLQ riêng:

~~~text
orders-email     ──▶ orders-email-dlq
orders-analytics ──▶ orders-analytics-dlq
orders-audit     ──▶ orders-audit-dlq
~~~

Lợi ích:

- Consumer chậm không chặn consumer khác.
- Failure policy tách biệt.
- Scale độc lập.
- Replay hoặc redrive riêng cho từng flow.

Không nên cho email, analytics và audit cùng tranh một Standard Queue nếu cả ba đều phải nhận một bản event. Competing consumers chỉ phù hợp khi worker chia nhau cùng một loại job.

---

## 17. Chọn queue type theo yêu cầu

### Chọn Standard khi

- Mỗi message là một job độc lập.
- Duplicate có thể xử lý bằng idempotency.
- Không cần strict ordering.
- Throughput và scale quan trọng.
- Tác vụ có thể xử lý song song.
- Consumer chịu được message đến trễ hoặc đảo thứ tự.

Ví dụ: image resize, email delivery, webhook retry, report generation.

### Chọn FIFO khi

- Thứ tự quan trọng trong từng entity.
- Cần duplicate control ở tầng queue trong deduplication window.
- Có thể xác định MessageGroupId hợp lý.
- Có thể chấp nhận giới hạn parallelism theo group.
- Consumer cần xử lý state transition tuần tự.

Ví dụ:

~~~text
order-123:
  ReserveStock → CapturePayment → MarkPaid
~~~

### Cân nhắc Kafka khi

- Cần replay dài hơn retention ngắn của SQS.
- Nhiều consumer group đọc cùng event log.
- Cần partition/offset và stream processing.
- Throughput lớn theo event stream.
- Muốn giữ record sau khi consumer đã xử lý.

### Cân nhắc RabbitMQ khi

- Cần exchange, binding, routing key linh hoạt.
- Cần topology routing phức tạp.
- Cần request/reply hoặc command delivery theo AMQP.
- Team chấp nhận vận hành RabbitMQ hoặc dùng managed RabbitMQ.

---

## 18. Bài tập thực hành

### Bài 1: Tạo và kiểm tra hai queue

Tạo:

~~~text
dev-orders-standard
dev-payment.fifo
~~~

Với mỗi queue:

1. Gửi một message.
2. Receive bằng short polling.
3. Receive bằng long polling 20 giây.
4. Không delete message.
5. Chờ visibility timeout.
6. Receive lại message.
7. Delete bằng receipt handle mới nhất.
8. Ghi nhận khác biệt trong log.

### Bài 2: So sánh Standard và FIFO

Gửi 10 message với cùng order ID.

- Standard: dùng nhiều consumer và quan sát thứ tự.
- FIFO: dùng MessageGroupId bằng order ID.
- FIFO: dùng hai order ID khác nhau để quan sát parallelism theo group.
- FIFO: retry cùng MessageDeduplicationId.
- FIFO: retry với ID mới và giải thích kết quả.

### Bài 3: Đo short polling và long polling

Thiết kế thử nghiệm:

| Biến | Giá trị |
|---|---|
| Queue | Standard |
| Message rate | 1 message mỗi 30 giây |
| Short polling | WaitTimeSeconds = 0 |
| Long polling | WaitTimeSeconds = 20 |
| Thời gian đo | 10 phút |

So sánh số lần receive, empty response, message nhận được, API call tương đối và latency.

### Bài 4: Batch API và partial failure

Gửi 10 message bằng batch. Cố ý làm một entry invalid hoặc làm request retry.

Ghi lại:

- Entry nào thành công.
- Entry nào thất bại.
- Có gửi lại toàn bộ batch hay chỉ entry lỗi?
- Delete batch xử lý partial failure thế nào?
- Vì sao producer/consumer cần idempotency?

### Bài 5: Thiết kế MessageGroupId

Chọn group ID cho:

1. Trạng thái từng order.
2. Số dư từng account.
3. Resize ảnh độc lập.
4. Đồng bộ trạng thái từng device.
5. Workflow bắt buộc tuần tự toàn hệ thống.

Với mỗi bài, nêu FIFO có cần không, group ID là gì, có thể xử lý song song không và có nguy cơ hot group không.

### Bài 6: Thiết kế fan-out

Thiết kế flow OrderCreated cho email, analytics và audit:

- Mỗi consumer có SQS queue riêng.
- Mỗi queue có DLQ riêng.
- Email có thể retry chậm.
- Analytics có thể replay.
- Audit phải alert nếu message không xử lý được.
- Một consumer down không làm hai consumer còn lại dừng.

### Bài 7: SQS message chứa S3 reference

Thiết kế message cho file lớn hơn 1 MiB:

~~~json
{
  "eventType": "DocumentUploaded",
  "eventVersion": 1,
  "bucket": "private-documents",
  "key": "uploads/2026/09/document-123.bin",
  "versionId": "optional-version",
  "checksum": "sha256:..."
}
~~~

Ghi rõ IAM permission, lỗi object không tồn tại, thời điểm xóa object, cách tránh log dữ liệu nhạy cảm và key dedup theo document ID/object version.

---

## 19. Câu hỏi tự kiểm tra

1. SQS khác RabbitMQ ở điểm kiến trúc nào?
2. Vì sao SQS consumer phải gọi DeleteMessage?
3. ReceiptHandle khác MessageId ra sao?
4. Visibility timeout có xóa message khỏi queue không?
5. Consumer crash trước DeleteMessage thì điều gì xảy ra?
6. Standard Queue có đảm bảo thứ tự không?
7. FIFO ordering áp dụng toàn queue hay từng MessageGroupId?
8. Vì sao một MessageGroupId cố định làm giảm parallelism?
9. MessageDeduplicationId bảo vệ trong phạm vi nào?
10. Content-based deduplication có tính message attributes vào hash không?
11. Khi nào dùng long polling?
12. Vì sao batch send/delete có thể partial failure?
13. Retention period có thay thế event archive không?
14. Vì sao Standard Queue vẫn cần idempotent consumer?
15. Queue URL và ARN dùng trong ngữ cảnh nào?
16. Vì sao không nên chọn FIFO chỉ vì “FIFO nghe an toàn hơn”?
17. Khi nào nên kết hợp SNS/EventBridge với nhiều SQS queue?
18. Vì sao payload lớn nên để ở S3 và chỉ gửi reference trong SQS?

---

## 20. Checklist hoàn thành Tuần 7

- [ ] Mình hiểu account, Region, queue URL và ARN.
- [ ] Mình mô tả được flow SendMessage → ReceiveMessage → DeleteMessage.
- [ ] Mình hiểu receipt handle và không dùng MessageId để delete.
- [ ] Mình giải thích được visibility timeout.
- [ ] Mình phân biệt retention, delay và visibility timeout.
- [ ] Mình biết Standard Queue là at-least-once và không đảm bảo ordering.
- [ ] Mình biết FIFO ordering áp dụng theo MessageGroupId.
- [ ] Mình biết cách chọn MessageGroupId để vừa giữ thứ tự vừa có parallelism.
- [ ] Mình hiểu MessageDeduplicationId và content-based deduplication.
- [ ] Mình biết long polling tối đa 20 giây.
- [ ] Mình biết batch API có tối đa 10 message mỗi batch.
- [ ] Mình biết giới hạn chính về message size, retention, delay và visibility timeout.
- [ ] Mình có thể tạo và thao tác Standard/FIFO bằng AWS CLI.
- [ ] Mình có thể viết skeleton producer/consumer bằng AWS SDK for Java.
- [ ] Mình biết SQS không thay thế Kafka event log hoặc RabbitMQ exchange.
- [ ] Mình hoàn thành bài fan-out và bài phân tích MessageGroupId.

## Kết luận

SQS là managed queue tập trung vào việc gửi, nhận và xóa message qua API. Consumer receive message không có nghĩa message đã hoàn tất; chỉ sau khi xử lý thành công và delete đúng receipt handle, message mới được loại khỏi queue.

Standard Queue phù hợp với throughput và work processing khi consumer chịu được duplicate và reorder. FIFO Queue phù hợp khi cần ordering theo từng entity và duplicate control ở tầng queue, nhưng ordering chỉ có ý nghĩa trong cùng MessageGroupId; dùng một group duy nhất sẽ biến queue thành một chuỗi tuần tự và làm mất parallelism.

Khi thiết kế SQS, hãy quyết định theo bốn câu hỏi:

~~~text
Có cần ordering không?
Có cần duplicate control không?
Có thể scale song song theo entity không?
Có cần replay dài hạn như event log không?
~~~

Ở Tuần 8, ta sẽ đưa các kiến trúc này vào production reliability: visibility timeout động, idempotent consumer, DLQ/redrive, IAM, encryption, CloudWatch và Spring Cloud AWS.

## Nguồn AWS chính thức

- [Amazon SQS standard queues](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/standard-queues.html)
- [Amazon SQS FIFO queues](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-fifo-queues.html)
- [Amazon SQS visibility timeout](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-visibility-timeout.html)
- [Amazon SQS message quotas](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/quotas-messages.html)
- [Setting up Amazon SQS long polling](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/best-practices-setting-up-long-polling.html)
- [FIFO queue and message identifiers](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-fifo-queue-message-identifiers.html)

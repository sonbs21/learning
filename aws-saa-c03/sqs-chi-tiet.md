# Amazon SQS chuyên sâu — AWS SAA-C03

> Tài liệu này tách riêng và mở rộng phần Amazon SQS từ Tuần 10. Mục tiêu không chỉ là nhớ định nghĩa, mà là hiểu trọn vòng đời của một message: producer gửi gì, SQS giữ gì, consumer nhận ra sao, khi nào message xuất hiện lại, vì sao có duplicate, khi nào vào dead-letter queue và phải thiết kế thế nào để không tạo side effect hai lần.

## 1. Tình huống mở đầu: xử lý đơn hàng không dùng SQS

Giả sử một API nhận đơn hàng phải thực hiện tuần tự các bước:

~~~text
Client
  │ POST /orders
  ▼
Order API
  ├── trừ tồn kho
  ├── gửi email
  ├── tạo hóa đơn
  └── gọi đơn vị vận chuyển
~~~

Thiết kế này đơn giản lúc đầu, nhưng có nhiều vấn đề:

- API phải chờ tất cả hệ thống phía sau.
- Dịch vụ email chậm có thể làm request tạo đơn timeout.
- Một downstream ngừng hoạt động có thể làm toàn bộ luồng thất bại.
- Khi có đợt flash sale, API và worker phải chịu traffic burst cùng lúc.
- Producer phải biết địa chỉ, timeout và cách retry của từng consumer.
- Việc scale từng thành phần độc lập trở nên khó khăn.

Đặt một queue giữa API và worker:

~~~text
Client → Order API → SQS Queue → Order Worker → Database / Email / Shipping
                         │
                         └── lưu message trong lúc worker bận hoặc tạm lỗi
~~~

Order API chỉ cần kiểm tra request, ghi nhận yêu cầu và gửi message. Worker đọc message theo tốc độ mà nó xử lý được. Traffic đến ồ ạt được chuyển thành backlog trong queue thay vì đánh thẳng vào downstream.

Đây là giá trị cốt lõi của SQS: **tách producer và consumer theo thời gian, khả năng scale và failure domain**.

---

# Phần I — Hiểu đúng bản chất của SQS

## 2. Amazon SQS là gì?

Amazon Simple Queue Service là dịch vụ message queue được AWS quản lý. Producer gửi message vào queue; consumer chủ động lấy message về xử lý.

SQS quản lý:

- Hạ tầng queue có tính sẵn sàng cao.
- Lưu trữ message theo thời hạn retention.
- Phân phối message cho consumer.
- Visibility timeout.
- Cơ chế retry thông qua việc message xuất hiện lại.
- Dead-letter queue và redrive.
- Mã hóa phía server.
- Metrics tích hợp với CloudWatch.

Bạn vẫn chịu trách nhiệm cho:

- Thiết kế schema của message.
- Xác thực và kiểm tra dữ liệu.
- Logic consumer.
- Idempotency.
- Chọn timeout, retention, retry và DLQ.
- IAM và queue policy.
- Giám sát backlog, tuổi message và lỗi xử lý.
- Bảo vệ downstream khỏi concurrency quá lớn.

SQS không tự chạy business logic. Queue chỉ lưu và phân phối message; phải có Lambda, ECS, EC2 hoặc một application khác làm consumer.

## 3. Queue khác database và event log như thế nào?

| Thành phần | Mục tiêu chính |
|---|---|
| SQS queue | Chuyển công việc từ producer tới consumer và hấp thụ backlog |
| Database | Lưu trạng thái nghiệp vụ để truy vấn/cập nhật lâu dài |
| Event log/stream | Giữ chuỗi event để nhiều consumer đọc hoặc replay theo offset |
| SNS topic | Broadcast một message tới nhiều subscriber |
| EventBridge bus | Route event theo nội dung tới target phù hợp |

Sau khi consumer xóa message, SQS không còn cung cấp message đó để đọc lại. Vì vậy:

- Không dùng SQS làm system of record.
- Không coi retention là cơ chế backup dữ liệu nghiệp vụ.
- Nếu cần replay lịch sử lâu dài, hãy lưu event vào S3, database, stream hoặc event store phù hợp.

## 4. Pull-based delivery

SQS dùng mô hình pull:

~~~text
Producer --SendMessage--> SQS

Consumer --ReceiveMessage--> SQS
Consumer <--message--------- SQS
Consumer --DeleteMessage---> SQS
~~~

SQS không tự đẩy message vào process của worker truyền thống. Consumer gọi `ReceiveMessage` để poll queue.

Với Lambda, cảm giác giống push vì Lambda function tự được invoke. Tuy nhiên, phía sau là Lambda service poll SQS qua event source mapping rồi invoke function. Phân biệt này rất quan trọng khi phân tích IAM, retry, batching và scaling.

## 5. Ba trạng thái quan trọng của message

Một message thường đi qua ba trạng thái:

~~~text
1. Stored / delayed
   Message đã ở queue nhưng có thể chưa đến lúc được nhận.

2. Available / visible
   Consumer có thể nhận message.

3. In flight / invisible
   Một consumer đã nhận message; message tạm ẩn trong visibility timeout.
~~~

Sau khi xử lý thành công, consumer phải xóa message. Nếu không xóa trước khi visibility timeout kết thúc, message trở lại trạng thái visible và có thể được nhận lại.

## 6. Vòng đời đầy đủ của một message

~~~text
Producer
   │ SendMessage
   ▼
┌──────────────────┐
│   SQS Queue      │
│ message visible  │
└────────┬─────────┘
         │ ReceiveMessage
         ▼
┌──────────────────┐
│ message in flight│  ← visibility timeout bắt đầu
└──────┬───────┬───┘
       │       │
 process OK    process lỗi / worker chết / quên delete
       │       │
 DeleteMessage│ hết visibility timeout
       │       │
       ▼       ▼
    kết thúc   visible trở lại → retry
~~~

Một hiểu nhầm phổ biến là `ReceiveMessage` đã lấy message ra khỏi queue. Thực tế, receive chỉ làm message tạm ẩn. `DeleteMessage` mới hoàn tất việc loại message khỏi queue.

## 7. Message ID và receipt handle

Khi gửi message, SQS cấp một `MessageId`. Khi consumer nhận message, SQS trả thêm một `ReceiptHandle`.

| Giá trị | Ý nghĩa |
|---|---|
| `MessageId` | ID do SQS cấp để nhận diện message |
| `ReceiptHandle` | Token gắn với **một lần receive cụ thể** |

Muốn xóa message hoặc đổi visibility timeout, consumer phải dùng receipt handle, không dùng message ID.

Nếu cùng message được nhận lại nhiều lần, mỗi lần có một receipt handle mới. Consumer nên dùng receipt handle mới nhất.

---

# Phần II — Standard queue và FIFO queue

## 8. Hai loại queue

SQS có hai loại chính:

- Standard queue.
- FIFO queue.

Không có loại nào luôn tốt hơn. Lựa chọn phụ thuộc vào ordering, khả năng chấp nhận duplicate và throughput.

## 9. SQS Standard

Standard queue phù hợp cho phần lớn workload cần throughput rất cao và có thể thiết kế consumer idempotent.

Đặc điểm:

- Throughput rất cao.
- At-least-once delivery.
- Best-effort ordering.
- Có thể xuất hiện duplicate.
- Tự scale theo lượng message.

Chọn Standard khi:

- Xử lý ảnh, email, log hoặc background job độc lập.
- Không cần thứ tự tuyệt đối.
- Có thể xử lý duplicate an toàn.
- Cần throughput cao và nhiều consumer song song.
- Có thể gắn version/sequence vào dữ liệu nếu cần xử lý out-of-order.

## 10. At-least-once delivery nghĩa là gì?

At-least-once nghĩa là hệ thống cố gắng giao message ít nhất một lần, nhưng có thể giao nhiều hơn một lần.

Duplicate có thể xuất hiện vì:

- Bản sao message trên một storage node chưa được xóa đồng bộ.
- Consumer xử lý xong nhưng chết trước khi `DeleteMessage`.
- Request xóa thành công nhưng response bị mất, khiến application tưởng xóa thất bại.
- Visibility timeout hết khi consumer vẫn đang xử lý.
- Message được redrive hoặc producer retry.

Do đó, consumer phải được thiết kế sao cho xử lý cùng một yêu cầu hai lần không phá vỡ trạng thái nghiệp vụ.

## 11. Best-effort ordering

Standard queue cố gắng trả message theo thứ tự hợp lý nhưng không cam kết strict ordering.

Producer gửi:

~~~text
OrderCreated → PaymentConfirmed → OrderShipped
~~~

Consumer có thể nhận:

~~~text
PaymentConfirmed → OrderCreated → OrderShipped
~~~

Nếu dùng Standard nhưng business cần kiểm soát phiên bản, message có thể mang:

~~~json
{
  "aggregateId": "order-123",
  "eventType": "PaymentConfirmed",
  "version": 2
}
~~~

Consumer dùng `version` để bỏ qua event cũ hoặc trì hoãn event đến sớm. Nếu strict ordering thực sự là requirement, FIFO thường phù hợp hơn.

## 12. SQS FIFO

FIFO là First-In-First-Out. Queue FIFO dùng khi thứ tự xử lý và chống duplicate ở phía queue quan trọng.

Tên FIFO queue phải kết thúc bằng `.fifo`, ví dụ:

~~~text
order-events.fifo
~~~

Đặc điểm chính:

- Bảo toàn thứ tự trong cùng message group.
- Dùng deduplication ID để tránh đưa message trùng vào queue trong cửa sổ deduplication.
- Hỗ trợ nhiều message group để xử lý song song.
- Có thể dùng high-throughput FIFO khi cấu hình phù hợp.

## 13. MessageGroupId — chìa khóa của ordering

Mỗi message gửi vào FIFO queue phải có `MessageGroupId`.

~~~text
Group order-101: Created → Paid → Packed → Shipped
Group order-202: Created → Paid → Cancelled
~~~

SQS giữ đúng thứ tự trong từng group. Hai group khác nhau có thể được xử lý đồng thời.

### Chọn group ID như thế nào?

| Yêu cầu | Group ID gợi ý |
|---|---|
| Mọi thay đổi của một order phải tuần tự | `orderId` |
| Giao dịch của một tài khoản phải tuần tự | `accountId` |
| Command của một thiết bị phải tuần tự | `deviceId` |
| Toàn bộ queue phải tuần tự tuyệt đối | Một hằng số duy nhất |

Dùng một group ID duy nhất sẽ biến queue thành một lane tuần tự. Ordering rất mạnh nhưng khả năng song song thấp.

Dùng nhiều group ID phân phối đều giúp tăng concurrency. Group key không tốt, chẳng hạn mọi message đều dùng `default`, là một nguyên nhân phổ biến khiến FIFO consumer xử lý chậm.

## 14. Head-of-line blocking trong FIFO

Khi message đầu tiên của một group đang in flight, SQS không đưa message tiếp theo trong group đó cho consumer khác.

~~~text
Group A: A1 [đang lỗi] → A2 → A3
                       A2 và A3 phải chờ A1

Group B: B1 → B2
         vẫn có thể chạy độc lập
~~~

Vì vậy, poison message có thể chặn cả group. DLQ, maxReceiveCount hợp lý và xử lý lỗi nhanh đặc biệt quan trọng với FIFO.

## 15. MessageDeduplicationId

FIFO dùng deduplication ID để nhận diện các lần gửi trùng trong cửa sổ deduplication 5 phút.

Hai cách cung cấp ID:

1. Producer tự gửi `MessageDeduplicationId`.
2. Bật content-based deduplication để SQS tạo hash SHA-256 từ **message body**.

Ví dụ:

~~~text
MessageDeduplicationId = payment-request-789
MessageGroupId         = account-123
~~~

Nếu producer retry cùng deduplication ID trong cửa sổ 5 phút, SQS chấp nhận request nhưng không đưa thêm bản trùng vào queue.

### Cạm bẫy của content-based deduplication

Hash chỉ dựa trên body, không dựa trên message attributes. Hai message có cùng body nhưng attributes khác nhau vẫn có thể bị coi là trùng.

Ngược lại, body có các trường thay đổi như timestamp ngẫu nhiên sẽ tạo hash khác nhau dù business request giống nhau.

## 16. FIFO có thật sự exactly-once end-to-end không?

Tài liệu AWS mô tả FIFO có exactly-once processing trong phạm vi SQS deduplication: SQS không đưa bản gửi trùng vào queue trong cửa sổ deduplication.

Điều đó **không đồng nghĩa** side effect nghiệp vụ chỉ xảy ra đúng một lần.

~~~text
Consumer
  ├── gọi Payment API thành công
  └── chết trước DeleteMessage

Visibility timeout hết
  └── message được nhận lại
      └── Payment API có thể bị gọi lần hai
~~~

FIFO không biết consumer đã trừ tiền, gửi email hay ghi database. Consumer vẫn cần idempotency key, conditional write hoặc transaction.

## 17. Standard hay FIFO?

| Câu hỏi | Standard | FIFO |
|---|---|---|
| Cần strict ordering? | Không bảo đảm | Có, trong message group |
| Delivery | At least once | Queue không đưa duplicate trong dedup window; consumer vẫn cần idempotency |
| Throughput | Rất cao | Có quota; tăng bằng batching, nhiều group, high-throughput mode |
| MessageGroupId | Không bắt buộc cho queue thông thường | Bắt buộc |
| Tên queue | Tên bình thường | Phải có hậu tố `.fifo` |
| Use case | Job độc lập, email, resize ảnh | Command/order/giao dịch cần thứ tự |

Quy tắc thi nhanh:

- “Highest throughput, ordering not required” → Standard.
- “Strict order for each customer/order” → FIFO với `MessageGroupId` theo customer/order.
- “Avoid processing payment twice” → FIFO chưa đủ; cần consumer idempotent.

---

# Phần III — Visibility timeout và retry

## 18. Visibility timeout là gì?

Sau khi `ReceiveMessage`, message không bị xóa mà chỉ tạm ẩn. Khoảng thời gian ẩn đó là visibility timeout.

~~~text
t0: consumer nhận message
    └── visibility timeout bắt đầu

t1: consumer xử lý

t2a: DeleteMessage trước timeout
     └── hoàn tất

t2b: không delete và timeout kết thúc
     └── message visible trở lại
~~~

Mặc định visibility timeout là 30 giây. Có thể cấu hình từ 0 giây đến tối đa 12 giờ.

## 19. Timeout quá ngắn

Giả sử job cần 90 giây nhưng visibility timeout là 30 giây:

~~~text
0s   Worker A nhận M
30s  M visible trở lại
31s  Worker B nhận M
90s  Worker A hoàn tất
121s Worker B cũng có thể hoàn tất
~~~

Hậu quả:

- Hai worker xử lý cùng message.
- Downstream nhận request trùng.
- `ApproximateNumberOfMessagesNotVisible` tăng.
- Chi phí và tải hệ thống tăng.

## 20. Timeout quá dài

Job thường hoàn thành trong 10 giây nhưng visibility timeout đặt 12 giờ. Nếu worker chết, message bị ẩn gần 12 giờ trước khi retry.

Hậu quả:

- Recovery chậm.
- Tuổi backlog tăng.
- SLA có thể bị vi phạm.
- Message vào DLQ rất muộn.

## 21. Chọn visibility timeout

Một cách bắt đầu:

~~~text
visibility timeout > thời gian xử lý p99 + thời gian delete + network buffer
~~~

Không chỉ nhìn thời gian trung bình. Nếu phần lớn job chạy 5 giây nhưng 1% chạy 2 phút, timeout 10 giây sẽ tạo duplicate ở tail latency.

Sau khi triển khai, dùng metric processing duration và duplicate rate để điều chỉnh.

## 22. ChangeMessageVisibility và heartbeat

Nếu thời gian job khó dự đoán, không nhất thiết đặt timeout cực dài cho cả queue.

Consumer có thể:

1. Nhận message với timeout ban đầu vừa phải.
2. Trong lúc xử lý, định kỳ gọi `ChangeMessageVisibility`.
3. Mỗi lần gia hạn chỉ khi worker vẫn khỏe.
4. Hoàn tất thì gọi `DeleteMessage`.

~~~text
Receive ──30s── heartbeat ──30s── heartbeat ── finish/delete
~~~

Đây là lease/heartbeat pattern. Tổng visibility tính từ lúc receive đầu tiên không thể vượt giới hạn 12 giờ. Nếu job dài hơn nhiều giờ, hãy cân nhắc Step Functions, AWS Batch hoặc chia job thành nhiều bước nhỏ.

## 23. Trả message lại ngay

Consumer có thể đặt visibility timeout của message về `0` để message visible trở lại ngay, ví dụ khi worker biết nó không thể xử lý và muốn worker khác thử.

Không nên làm điều này trong vòng lặp không backoff vì có thể tạo hot loop, tăng chi phí và nhanh chóng đẩy message vào DLQ.

## 24. Receive count và retry

Mỗi lần message được nhận, thuộc tính `ApproximateReceiveCount` tăng. Redrive policy dùng số lần receive để quyết định khi nào chuyển message sang DLQ.

SQS không có một “retry scheduler” chạy business logic. Retry xảy ra tự nhiên như sau:

~~~text
receive → không delete → visibility hết → receive lại
~~~

Backoff có thể được thiết kế bằng:

- Thay đổi visibility timeout theo số lần retry.
- Chuyển qua các queue có delay khác nhau.
- EventBridge Scheduler cho lịch dài/chính xác hơn.
- Logic application kiểm soát `nextAttemptAt`.

---

# Phần IV — Polling, batching và throughput

## 25. Short polling

Với short polling, `ReceiveMessage` trả về ngay. SQS lấy mẫu một phần server, nên có thể trả response rỗng dù queue vẫn có message.

Short polling dễ tạo:

- Nhiều empty receive.
- Nhiều API request.
- Chi phí và CPU/network cao hơn.

## 26. Long polling

Long polling cho phép SQS chờ message trước khi trả response. `WaitTimeSeconds` có thể tối đa 20 giây.

~~~text
Consumer: ReceiveMessage(WaitTimeSeconds=20)

Queue rỗng:
  └── SQS chờ tối đa 20 giây

Message đến sau 3 giây:
  └── SQS trả response ngay
~~~

Lợi ích:

- Giảm empty response.
- Giảm số API call và chi phí.
- Giảm polling loop nóng.
- Thường là lựa chọn mặc định tốt cho worker tự quản.

HTTP client timeout phải lớn hơn `WaitTimeSeconds`; nếu client timeout trước, long polling không hoạt động đúng.

## 27. ReceiveMessageWaitTimeSeconds và WaitTimeSeconds

Có hai cấp cấu hình:

- `ReceiveMessageWaitTimeSeconds`: mặc định ở cấp queue.
- `WaitTimeSeconds`: ghi đè cho từng request `ReceiveMessage`.

Giá trị 0 là short polling. Giá trị lớn hơn 0 bật long polling.

## 28. Batching

SQS hỗ trợ batch tối đa 10 message cho các API như:

- `SendMessageBatch`.
- `DeleteMessageBatch`.
- `ChangeMessageVisibilityBatch`.

`ReceiveMessage` cũng có thể trả tối đa 10 message mỗi request.

Batching giúp:

- Giảm số API request.
- Tăng throughput trên mỗi connection.
- Giảm chi phí request.

Trade-off:

- Batch xử lý lâu hơn.
- Một item lỗi có thể làm logic retry phức tạp.
- Phải xử lý partial failure của batch API.

Batch API có thể trả HTTP thành công nhưng một số entry bên trong thất bại. Luôn kiểm tra danh sách `Successful` và `Failed`, không chỉ HTTP status.

## 29. In-flight messages

Message đã receive nhưng chưa delete được gọi là in flight.

Với Standard queue, quota in-flight thường xấp xỉ 120.000 message, tùy traffic và backlog. Khi chạm mức này:

- Short polling có thể trả lỗi `OverLimit`.
- Long polling có thể không trả thêm message cho đến khi số in-flight giảm.

Cách xử lý:

- Delete message ngay sau khi xử lý thành công.
- Giảm batch/concurrency nếu downstream quá chậm.
- Tăng throughput consumer nếu hệ thống phía sau chịu được.
- Chia workload sang nhiều queue khi hợp lý.
- Tránh visibility timeout quá dài không cần thiết.

## 30. Backpressure

SQS hấp thụ traffic burst nhưng không làm downstream có năng lực vô hạn.

~~~text
Producer: 10.000 msg/s
Consumer: 2.000 msg/s

Backlog tăng: 8.000 msg/s
~~~

Queue mua thời gian để hệ thống xử lý, nhưng nếu tốc độ vào luôn lớn hơn tốc độ ra, backlog sẽ tăng đến khi message hết retention hoặc SLA bị phá vỡ.

Thiết kế backpressure gồm:

- Giới hạn producer khi queue age quá cao.
- Auto Scaling consumer dựa trên backlog.
- Giới hạn concurrency để bảo vệ database/API.
- Load shedding hoặc ưu tiên message.
- Tách queue theo priority hoặc workload class.

## 31. Scale worker theo backlog

Không nên chỉ scale theo CPU. Với queue worker, các tín hiệu hữu ích hơn là:

- Số message visible.
- Tuổi message cũ nhất.
- Thời gian xử lý trung bình.
- SLA mục tiêu.

Công thức gần đúng:

~~~text
required workers ≈ backlog × average processing time / target drain time
~~~

Ví dụ:

~~~text
backlog              = 12.000 message
processing time      = 2 giây/message/worker
muốn drain trong     = 10 phút = 600 giây

workers ≈ 12.000 × 2 / 600 = 40
~~~

Cần cộng buffer và kiểm tra giới hạn downstream trước khi scale lên.

---

# Phần V — Retention, delay, payload và metadata

## 32. Message retention

Retention là thời gian SQS giữ message nếu message chưa bị xóa.

- Mặc định: 4 ngày.
- Tối thiểu: 1 phút.
- Tối đa: 14 ngày.

Khi hết retention, message bị xóa ngay cả khi chưa xử lý thành công.

Chọn retention dựa trên:

- Thời gian outage tối đa cần chịu được.
- Thời gian đội vận hành cần phát hiện và sửa lỗi.
- Backlog lớn nhất dự kiến.
- Yêu cầu replay và compliance.

Retention không thay thế backup. Nếu message có giá trị audit lâu dài, lưu một bản vào hệ thống bền vững khác.

## 33. Delay queue

Delay queue làm mọi message mới gửi vào queue bị ẩn một thời gian trước lần receive đầu tiên.

- Mặc định: 0 giây.
- Tối đa: 15 phút.

~~~text
SendMessage → delayed/invisible → hết delay → visible → ReceiveMessage
~~~

Dùng cho:

- Chờ vài phút trước khi gửi email nhắc việc.
- Trì hoãn retry ngắn.
- Cho hệ thống khác thời gian đạt eventual consistency.

Không dùng delay queue cho lịch chạy dài nhiều giờ/ngày hoặc lịch calendar phức tạp; cân nhắc EventBridge Scheduler.

## 34. Message timer

Producer có thể đặt `DelaySeconds` cho từng message trên Standard queue. FIFO không hỗ trợ timer riêng cho từng message; dùng delay ở cấp queue.

Phân biệt:

| Cơ chế | Bắt đầu khi nào? | Mục đích |
|---|---|---|
| Delay | Ngay sau khi send | Trì hoãn lần nhận đầu |
| Visibility timeout | Sau khi receive | Cho consumer thời gian xử lý |
| Retention | Từ lúc message được enqueue | Giới hạn thời gian lưu |

## 35. Kích thước message

Theo quota AWS hiện hành, một SQS message có kích thước từ 1 byte đến tối đa **1 MiB (1.048.576 byte)**.

> Lưu ý học thi: nhiều bài viết và khóa học cũ ghi 256 KiB. Đây là giới hạn cũ. Khi thiết kế thực tế, luôn kiểm tra tài liệu AWS hiện hành; trong câu hỏi chứng chỉ, dùng thông tin và ngữ cảnh mà đề cung cấp.

Kích thước tính cả body và một phần metadata theo cách API quy định. Message lớn làm tăng số request unit tính phí và ảnh hưởng batch payload.

## 36. Payload lớn và S3 pointer pattern

Không nên nhét file hoặc object rất lớn vào queue.

~~~text
Producer
  ├── PUT payload lớn vào S3
  └── gửi SQS message chứa bucket, key, versionId, checksum

Consumer
  ├── nhận SQS message
  ├── GET object từ S3
  ├── kiểm tra checksum/version
  ├── xử lý
  └── DeleteMessage
~~~

Ví dụ message:

~~~json
{
  "schemaVersion": 1,
  "jobId": "job-2026-001",
  "payload": {
    "bucket": "example-job-payloads",
    "key": "jobs/job-2026-001.json",
    "versionId": "3Lg...",
    "sha256": "..."
  }
}
~~~

Cần quyết định rõ:

- Ai xóa object S3?
- Object giữ bao lâu?
- Consumer có quyền `s3:GetObject` và `kms:Decrypt` không?
- Nếu message bị retry sau khi object đã bị xóa thì xử lý thế nào?
- Có cần S3 Versioning để tránh producer ghi đè object không?

AWS có Extended Client Library cho Java và Python để lưu payload lớn vào S3 và gửi reference qua SQS. Đừng để library che giấu lifecycle ownership của object.

## 37. Message attributes

Message attributes là metadata do application cung cấp, ví dụ:

- `eventType`.
- `schemaVersion`.
- `tenantId`.
- `traceId`.
- `contentType`.

System attributes do SQS cung cấp có thể gồm timestamp, receive count và FIFO metadata.

Không đặt secret vào attributes. Server-side encryption của SQS bảo vệ message body nhưng không mã hóa mọi metadata của queue/message theo cùng cách.

## 38. Thiết kế message contract

Một message tốt nên tự mô tả đủ để consumer xử lý và theo dõi:

~~~json
{
  "messageId": "01J...",
  "eventType": "OrderCreated",
  "schemaVersion": 2,
  "occurredAt": "2026-10-13T08:30:00Z",
  "correlationId": "req-abc",
  "idempotencyKey": "create-order-order-123",
  "data": {
    "orderId": "order-123",
    "customerId": "customer-88"
  }
}
~~~

Nguyên tắc:

- Có schema version.
- Dùng timestamp chuẩn UTC.
- Có correlation ID để trace.
- Có business idempotency key.
- Chỉ gửi dữ liệu consumer cần.
- Tránh payload phụ thuộc chặt vào database schema nội bộ.
- Thay đổi schema theo hướng backward-compatible khi có thể.

---

# Phần VI — Dead-letter queue và redrive

## 39. Dead-letter queue là gì?

Dead-letter queue là queue nhận message không được xử lý thành công sau số lần receive cho phép.

~~~text
Source Queue
   │ receive lần 1 → lỗi
   │ receive lần 2 → lỗi
   │ receive lần 3 → lỗi
   ▼ vượt maxReceiveCount
Dead-Letter Queue
~~~

DLQ giúp:

- Tách poison message khỏi luồng khỏe mạnh.
- Tránh retry vô hạn.
- Giữ message để điều tra.
- Tạo alarm rõ ràng.
- Cho phép sửa lỗi rồi redrive.

## 40. Redrive policy

Redrive policy gắn trên source queue và chỉ định:

- ARN của DLQ.
- `maxReceiveCount`.

Ví dụ khái niệm:

~~~json
{
  "deadLetterTargetArn": "arn:aws:sqs:ap-southeast-1:111122223333:orders-dlq",
  "maxReceiveCount": 5
}
~~~

`maxReceiveCount` là số lần message có thể được nhận trước khi SQS chuyển nó sang DLQ. Đặt 1 thường quá thấp vì một lỗi transient ngắn cũng đẩy message vào DLQ.

## 41. Chọn maxReceiveCount

Không có một số đúng cho mọi hệ thống. Xem xét:

- Loại lỗi thường gặp.
- Visibility timeout.
- Khoảng cách giữa các lần retry.
- Downstream thường mất bao lâu để phục hồi.
- SLA xử lý.
- Side effect và khả năng idempotent.

Ví dụ:

~~~text
visibility timeout = 2 phút
maxReceiveCount    = 5

Poison message có thể quay vòng khoảng 10 phút hoặc lâu hơn
trước khi vào DLQ, tùy thời gian xử lý và polling.
~~~

Nếu downstream thường outage 30 phút, retry 5 lần liên tiếp trong 10 phút có thể không hữu ích. Cần backoff dài hơn hoặc circuit breaker.

## 42. Redrive allow policy

Đây là policy trên DLQ quy định source queue nào được phép dùng queue đó làm DLQ.

Có thể:

- Cho mọi source queue.
- Chỉ cho một số source queue cụ thể.
- Không cho queue nào.

Đừng nhầm:

| Policy | Nằm ở đâu? | Trả lời câu hỏi gì? |
|---|---|---|
| Redrive policy | Source queue | Lỗi bao nhiêu lần thì chuyển tới DLQ nào? |
| Redrive allow policy | DLQ | Source queue nào được phép dùng tôi làm DLQ? |

## 43. Loại queue của source và DLQ

Source queue và DLQ phải cùng loại:

- Standard → Standard DLQ.
- FIFO → FIFO DLQ.

Nên đặt cùng Region và account khi không có lý do đặc biệt để đơn giản hóa permission và vận hành.

## 44. Retention của DLQ

Best practice là đặt retention của DLQ dài hơn source queue.

Với Standard queue, khi message chuyển sang DLQ, timestamp enqueue gốc được giữ để tính thời điểm hết hạn. Nếu message đã ở source queue lâu, thời gian còn lại trong DLQ có thể ngắn hơn bạn tưởng.

Với FIFO, enqueue timestamp được reset khi chuyển vào DLQ.

Đừng chỉ nhìn `ApproximateAgeOfOldestMessage` mà suy luận giống nhau cho Standard và FIFO; semantics về age/retention có khác biệt.

## 45. DLQ có thể phá ordering

Nếu một message FIFO bị chuyển sang DLQ, message sau nó có thể tiếp tục. Điều này có thể phá ý nghĩa business của một chuỗi bắt buộc tuyệt đối.

Ví dụ:

~~~text
1. Create account
2. Deposit money   ← vào DLQ
3. Withdraw money  ← tiếp tục xử lý
~~~

Nếu không thể chấp nhận bỏ qua bước 2, cần thiết kế workflow/compensation hoặc dừng group, không chỉ dựa vào DLQ mặc định.

## 46. Quy trình redrive an toàn

Không nhấn “redrive all” ngay khi thấy DLQ có message.

Quy trình tốt:

1. Dừng hoặc kiểm soát tốc độ redrive.
2. Lấy mẫu message, không làm lộ PII/secret trong log.
3. Xác định lỗi là code, schema, permission hay downstream.
4. Sửa root cause.
5. Kiểm tra consumer idempotent.
6. Redrive một lượng nhỏ.
7. Theo dõi success rate, queue age và downstream load.
8. Tăng tốc dần.

SQS hỗ trợ redrive velocity để kiểm soát tốc độ. Message redrive được coi là message mới ở destination, có message ID và enqueue time mới.

## 47. DLQ không phải nơi chôn lỗi

Một DLQ không có alarm và runbook chỉ là nơi lỗi bị lãng quên.

Tối thiểu cần:

- Alarm khi DLQ có message mới.
- Dashboard số lượng và tuổi message.
- Owner xử lý rõ ràng.
- Runbook điều tra/redrive.
- Log correlation ID và error category.
- Chính sách retention đủ dài.

---

# Phần VII — Consumer idempotent và xử lý lỗi

## 48. Idempotency là gì?

Một operation idempotent cho cùng kết quả business dù được gọi lặp lại bằng cùng một yêu cầu.

Không idempotent:

~~~text
Mỗi lần nhận message → cộng 100.000 đồng vào số dư
~~~

Idempotent:

~~~text
Nếu transactionId chưa xử lý:
    ghi transaction và cập nhật số dư trong một transaction
Nếu đã xử lý:
    trả kết quả cũ, không cộng lần nữa
~~~

## 49. Không dùng riêng SQS MessageId làm business key

Producer retry có thể tạo một message mới với SQS MessageId khác nhưng cùng business request. Redrive cũng có thể tạo ID mới.

Nên có business idempotency key ổn định, ví dụ:

~~~text
payment:{orderId}:{paymentAttemptId}
~~~

Key phải đại diện cho thao tác cần chống lặp, không chỉ đại diện cho một lần gửi kỹ thuật.

## 50. Idempotency table với DynamoDB

Pattern phổ biến:

~~~text
Consumer nhận message với idempotencyKey K
   │
   ├── PutItem K với condition attribute_not_exists(K)
   │      ├── thành công → được quyền xử lý
   │      └── condition fail → đã/đang xử lý
   │
   ├── thực hiện business action
   └── cập nhật K = COMPLETED + result + TTL
~~~

Cần xử lý trạng thái `IN_PROGRESS` bị bỏ lại khi worker chết. Có thể lưu lease expiry và cho phép takeover an toàn.

Nếu thao tác business nằm trong DynamoDB, dùng transactional write để ghi business state và idempotency record nguyên tử khi phù hợp.

## 51. Inbox pattern

Consumer lưu message ID/business event ID vào bảng inbox cùng transaction với thay đổi business:

~~~text
BEGIN
  INSERT inbox(event_id) ON CONFLICT FAIL
  UPDATE orders ...
COMMIT
~~~

Nếu event đã có trong inbox, consumer bỏ qua. Pattern này mạnh khi database hỗ trợ unique constraint và transaction.

## 52. Outbox pattern ở producer

Vấn đề dual write:

~~~text
1. Ghi order vào database thành công
2. Gửi SQS thất bại
~~~

Hoặc ngược lại:

~~~text
1. Gửi SQS thành công
2. Ghi database thất bại
~~~

Outbox pattern ghi business data và outbox record trong cùng database transaction. Một relay riêng đọc outbox và gửi message. Relay có thể gửi trùng, nên consumer vẫn phải idempotent.

## 53. Phân loại lỗi

| Loại lỗi | Ví dụ | Hành động |
|---|---|---|
| Transient | timeout, 503, throttling | Retry với backoff/jitter |
| Permanent data | schema sai, thiếu field bắt buộc | Không retry vô hạn; DLQ/quarantine |
| Permission/config | AccessDenied, KMS policy sai | Alert và sửa cấu hình |
| Business rejection | order đã hủy | Ghi kết quả business phù hợp; có thể không phải lỗi kỹ thuật |
| Resource exhaustion | DB hết connection | Giảm concurrency, backoff, bảo vệ downstream |

Retry không phân loại lỗi thường biến một lỗi vĩnh viễn thành hàng nghìn request vô ích.

## 54. Exponential backoff và jitter

Khi nhiều consumer cùng retry ngay lập tức, downstream vừa phục hồi có thể bị đánh sập lần nữa.

~~~text
delay = min(cap, base × 2^attempt) + random_jitter
~~~

Jitter phân tán các lần retry theo thời gian. Với SQS, có thể thể hiện backoff bằng `ChangeMessageVisibility` theo attempt hoặc qua các queue retry có delay khác nhau.

## 55. Delete message khi nào?

Chỉ delete sau khi tất cả side effect bắt buộc đã hoàn tất hoặc đã được ghi nhận bền vững.

Sai:

~~~text
Receive → Delete → process
                └── process lỗi thì công việc mất
~~~

Đúng hơn:

~~~text
Receive → validate → process idempotently → commit → Delete
~~~

Nếu `DeleteMessage` thất bại sau commit, message có thể quay lại; idempotency bảo vệ lần xử lý sau.

## 56. Poison pill và quarantine

Poison message là message luôn làm consumer lỗi, thường do:

- Schema không hỗ trợ.
- Dữ liệu hỏng.
- Reference không tồn tại.
- Bug chỉ xuất hiện với một payload cụ thể.

DLQ là quarantine. Đừng log toàn bộ payload nhạy cảm để debug. Nên log ID, schema version, error category và hash/checksum đủ để truy vết.

---

# Phần VIII — SQS với AWS Lambda

## 57. Ai poll queue?

Khi dùng Lambda:

~~~text
SQS Queue
    ▲
    │ Lambda service poll bằng event source mapping
    ▼
Lambda function nhận một batch message
~~~

SQS không trực tiếp invoke Lambda. Lambda service poll SQS, gom batch rồi invoke function.

## 58. Event source mapping

Event source mapping quản lý:

- Queue ARN.
- Batch size.
- Batching window.
- Enabled/disabled state.
- Filtering nếu được hỗ trợ.
- Maximum concurrency.
- Partial batch response.
- Polling/scaling mode.

Lambda function và SQS queue phải cùng Region, dù có thể khác account khi permission được cấu hình đúng.

## 59. IAM cho Lambda consumer

Execution role của Lambda thường cần:

~~~text
sqs:ReceiveMessage
sqs:DeleteMessage
sqs:GetQueueAttributes
~~~

Nếu queue dùng customer-managed KMS key, role có thể cần `kms:Decrypt` theo key policy phù hợp.

Đừng nhầm execution role với quyền cho principal tạo event source mapping. Một quyền dùng lúc cấu hình control plane; quyền kia dùng lúc runtime đọc queue.

## 60. Batch size và batch window

Với Lambda event source mapping:

- Standard queue có thể cấu hình batch size lên tới 10.000 records.
- FIFO queue tối đa 10 records mỗi batch.
- Nếu Standard batch size lớn hơn 10, batching window phải ít nhất 1 giây.
- Tổng event payload vẫn chịu giới hạn payload invoke Lambda; metadata cũng chiếm dung lượng.

Batch lớn:

- Tăng hiệu quả và giảm invocation.
- Có thể tăng latency chờ gom batch.
- Tăng memory/thời gian xử lý.
- Làm failure blast radius lớn nếu không dùng partial batch response.

## 61. Visibility timeout với Lambda

AWS khuyến nghị visibility timeout của source queue ít nhất bằng:

~~~text
6 × Lambda function timeout + MaximumBatchingWindowInSeconds
~~~

Khoảng dư cho phép Lambda retry khi invocation bị throttled.

Function timeout phải nhỏ hơn hoặc bằng queue visibility timeout. Nếu batch xử lý gần sát timeout, hãy giảm batch size hoặc tăng timeout hợp lý.

## 62. Mặc định: một record lỗi có thể retry cả batch

Giả sử batch có năm message:

~~~text
M1 OK
M2 OK
M3 FAIL
M4 OK
M5 OK
~~~

Nếu function throw exception và chưa bật partial batch response, cả batch có thể visible trở lại. M1, M2, M4 và M5 bị xử lý lại dù trước đó đã thành công.

Idempotency vẫn bắt buộc.

## 63. Partial batch response

Bật `ReportBatchItemFailures` để function trả về ID của những record thất bại:

~~~json
{
  "batchItemFailures": [
    { "itemIdentifier": "message-id-of-M3" }
  ]
}
~~~

Lambda xóa các record thành công và chỉ retry record được báo lỗi.

Lưu ý:

- Nếu handler throw exception toàn cục, cả batch vẫn bị coi là thất bại.
- Với FIFO, nên dừng xử lý sau failure đầu tiên và trả cả record lỗi lẫn các record chưa xử lý để giữ ordering.
- AWS Lambda Powertools có batch processor hỗ trợ pattern này.

## 64. Lambda scaling với Standard queue

Lambda dùng long polling và tăng số concurrent invocation khi backlog tăng. Scaling có giới hạn ở event source mapping và account concurrency.

Rủi ro:

~~~text
Queue backlog tăng
  → Lambda scale nhanh
  → hàng trăm invocation
  → database connection cạn
~~~

Dùng:

- Maximum concurrency ở event source mapping.
- Reserved concurrency ở function.
- Database proxy/connection pooling.
- Batch size phù hợp.
- Rate limit ở downstream.

Maximum concurrency của mapping và reserved concurrency là hai giới hạn khác nhau. Tổng maximum concurrency của các mapping không nên vượt reserved concurrency dành cho function.

## 65. Lambda scaling với FIFO queue

Concurrency FIFO bị giới hạn bởi số `MessageGroupId` đang hoạt động hoặc maximum concurrency, lấy giá trị thấp hơn.

Ví dụ:

~~~text
2 message groups + maximum concurrency 20
→ tối đa khoảng 2 luồng xử lý song song theo group
~~~

Muốn tăng parallelism, phải có đủ message group phân phối hợp lý. Tăng Lambda reserved concurrency không giải quyết được một hot group duy nhất.

## 66. Lambda destination/DLQ và SQS source DLQ

Với SQS event source, failure và retry chủ yếu do SQS visibility timeout + redrive policy điều khiển. Hãy cấu hình DLQ trên source queue.

Đừng máy móc áp dụng async invocation DLQ/destination của Lambda cho mọi trigger. Mỗi event source có failure semantics riêng.

---

# Phần IX — Security và network

## 67. Hai lớp permission

Quyền truy cập SQS có thể đến từ:

1. Identity-based policy trên IAM role/user.
2. Resource-based queue policy trên queue.

Ví dụ producer cần `sqs:SendMessage`; consumer cần receive/delete. Cross-account hoặc AWS service gửi vào queue thường cần queue policy rõ ràng.

## 68. Least privilege producer policy

~~~json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": "sqs:SendMessage",
      "Resource": "arn:aws:sqs:ap-southeast-1:111122223333:order-jobs"
    }
  ]
}
~~~

Producer không cần `ReceiveMessage` hoặc `DeleteMessage`.

## 69. Least privilege consumer policy

~~~json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "sqs:ReceiveMessage",
        "sqs:DeleteMessage",
        "sqs:ChangeMessageVisibility",
        "sqs:GetQueueAttributes"
      ],
      "Resource": "arn:aws:sqs:ap-southeast-1:111122223333:order-jobs"
    }
  ]
}
~~~

Không dùng `sqs:*` nếu application chỉ cần một số thao tác runtime.

## 70. Queue policy cho AWS service

Khi SNS topic hoặc S3 gửi event vào queue, queue policy phải cho phép service principal gọi `sqs:SendMessage`.

Giới hạn bằng:

- `aws:SourceArn`.
- `aws:SourceAccount`.
- `aws:PrincipalOrgID` nếu phù hợp.

Điều này giảm confused deputy risk. Chỉ cho `sns.amazonaws.com` gửi mà không giới hạn topic có thể vô tình cho topic ngoài ý muốn gửi vào queue.

## 71. Encryption at rest

SQS hỗ trợ:

- SSE-SQS: khóa do SQS quản lý.
- SSE-KMS: khóa AWS KMS quản lý, gồm AWS managed key hoặc customer-managed key.

Các queue mới hiện được bật server-side encryption mặc định. Tuy nhiên, phải kiểm tra cấu hình thực tế và requirement của tổ chức.

SSE bảo vệ body message khi lưu trữ. Nó không mã hóa mọi metadata như queue name, message ID, timestamp và metrics.

## 72. SSE-SQS hay SSE-KMS?

| Yêu cầu | Lựa chọn thường phù hợp |
|---|---|
| Muốn mã hóa đơn giản, ít quản lý | SSE-SQS |
| Cần kiểm soát key policy, audit KMS, rotation/compliance | Customer-managed KMS key |
| Cần revoke quyền dùng key độc lập với queue | Customer-managed KMS key |
| Muốn tránh KMS request/cost phức tạp | SSE-SQS |

Với SSE-KMS, producer có thể cần `kms:GenerateDataKey`; consumer có thể cần `kms:Decrypt`. AWS service gửi message cũng phải được key policy cho phép khi cần.

## 73. Encryption in transit

Dùng HTTPS/TLS. Có thể thêm explicit deny khi `aws:SecureTransport` là false trong queue policy.

Mã hóa at rest không bảo vệ traffic nếu client cố dùng transport không an toàn.

## 74. VPC endpoint

SQS là regional public AWS service endpoint, không nằm trong subnet của bạn. Workload trong private subnet có thể truy cập bằng:

- NAT gateway/NAT instance ra public endpoint.
- Interface VPC endpoint powered by AWS PrivateLink.

VPC endpoint giúp traffic đi qua private connectivity của AWS và có thể giảm phụ thuộc NAT. Cần xem xét:

- Endpoint policy.
- Queue policy giới hạn `aws:SourceVpce` hoặc `aws:SourceVpc`.
- Private DNS.
- Security group của interface endpoint.
- Chi phí endpoint so với NAT theo traffic/kiến trúc.

## 75. Không gửi secret không cần thiết

Queue có retention, DLQ, logs và nhiều consumer. Secret trong message có thể lan sang nhiều nơi.

Thay vì gửi credential:

- Gửi reference/ID.
- Consumer đọc secret từ Secrets Manager bằng role của chính nó.
- Không log body nhạy cảm.
- Áp dụng data minimization.

---

# Phần X — Monitoring và vận hành

## 76. Metrics quan trọng

| Metric | Ý nghĩa vận hành |
|---|---|
| `ApproximateNumberOfMessagesVisible` | Backlog sẵn sàng được nhận |
| `ApproximateNumberOfMessagesNotVisible` | Message đang in flight |
| `ApproximateNumberOfMessagesDelayed` | Message đang trong delay period |
| `ApproximateAgeOfOldestMessage` | Tuổi message cũ nhất còn chờ |
| `NumberOfMessagesSent` | Số message gửi vào |
| `NumberOfMessagesReceived` | Số lần nhận; có thể gồm retry/duplicate |
| `NumberOfMessagesDeleted` | Số thao tác xóa thành công |
| `NumberOfEmptyReceives` | Poll không nhận được message |

Các metric là approximate và không phải số liệu kế toán tuyệt đối.

## 77. Backlog depth chưa đủ

Queue có 100.000 message không nhất thiết xấu nếu consumer xử lý 1 triệu message/phút. Queue có 100 message có thể nghiêm trọng nếu message cũ nhất đã chờ hai giờ trong hệ thống SLA 5 phút.

Theo dõi đồng thời:

- Backlog depth.
- Age of oldest message.
- Incoming rate.
- Processing rate.
- Consumer error/throttle.
- Downstream latency/error.

## 78. Alarm gợi ý

### Alarm 1 — Tuổi message vượt SLA

~~~text
ApproximateAgeOfOldestMessage > ngưỡng SLA
~~~

### Alarm 2 — DLQ có message

~~~text
DLQ ApproximateNumberOfMessagesVisible > 0
~~~

Trong hệ thống traffic lớn, có thể alarm theo rate/age để tránh nhiễu, nhưng không được bỏ quên DLQ.

### Alarm 3 — Backlog tăng liên tục

Kết hợp visible messages và processing rate để phát hiện consumer không theo kịp.

### Alarm 4 — Nhiều empty receives

Có thể cho thấy polling không tối ưu, thiếu long polling hoặc quá nhiều worker so với lượng việc.

## 79. Correlation và tracing

Mỗi message nên mang correlation ID. Producer và consumer log cùng ID:

~~~text
requestId → messageId → correlationId → business entity ID
~~~

Không dựa hoàn toàn vào distributed tracing tự động; asynchronous boundary cần truyền trace context đúng cách và không tin mù quáng metadata từ nguồn ngoài.

## 80. Runbook khi queue age tăng

1. Kiểm tra producer rate có tăng bất thường không.
2. Kiểm tra consumer có error/throttle không.
3. Kiểm tra số message in flight.
4. Kiểm tra database/API downstream.
5. Kiểm tra visibility timeout và processing duration.
6. Kiểm tra deployment gần nhất.
7. Tăng consumer có kiểm soát nếu downstream còn capacity.
8. Nếu message lỗi hàng loạt, dừng retry storm và cô lập nguyên nhân.
9. Theo dõi DLQ và retention còn lại.

---

# Phần XI — Cost và tối ưu

## 81. SQS tính phí theo request

Chi phí phụ thuộc loại queue, Region, request và payload size theo pricing hiện hành. Một API call batch có thể chứa nhiều message, nhưng payload lớn có thể được tính thành nhiều request unit.

Luôn dùng AWS Pricing Calculator/trang pricing hiện tại cho con số cụ thể; không học thuộc giá dollar cố định cho kỳ thi.

## 82. Cách giảm chi phí hợp lý

- Long polling để giảm empty receive.
- Batch send/receive/delete.
- Tránh message body lớn không cần thiết.
- Xóa message ngay sau xử lý thành công.
- Điều chỉnh số worker khi queue rỗng.
- Không retry lỗi permanent liên tục.
- Cấu hình KMS data key reuse phù hợp nếu dùng SSE-KMS.
- Theo dõi `NumberOfEmptyReceives`.

Không tối ưu bằng cách tăng batch quá lớn nếu điều đó làm timeout, duplicate và retry tăng.

## 83. Fair queues trên Standard queue

Standard queue có thể dùng `MessageGroupId` để hỗ trợ fair queue behavior cho workload multi-tenant. Mục tiêu là giảm việc một tenant có backlog lớn làm tenant khác chờ quá lâu.

Điều này không biến Standard thành FIFO và không cung cấp strict ordering. Nếu đề hỏi ordering, vẫn chọn FIFO.

---

# Phần XII — Các architecture pattern quan trọng

## 84. Queue-based load leveling

~~~text
Traffic burst → SQS → fixed/auto-scaled workers → Database
~~~

Queue làm phẳng tải. Worker xử lý theo capacity an toàn của downstream.

Phù hợp:

- Resize ảnh.
- Tạo báo cáo.
- Gửi email.
- Import dữ liệu.
- Xử lý đơn hàng nền.

## 85. Competing consumers

Nhiều worker đọc cùng một queue:

~~~text
                  ┌→ Worker A
SQS shared queue ─┼→ Worker B
                  └→ Worker C
~~~

Mỗi message thường được một worker xử lý tại một thời điểm nhờ visibility timeout. Đây là cách scale ngang work distribution.

## 86. SNS → nhiều SQS queue

Khi nhiều hệ thống đều cần một event:

~~~text
Order Service → SNS Topic
                    ├→ Billing Queue  → Billing Worker
                    ├→ Shipping Queue → Shipping Worker
                    └→ Analytics Queue→ Analytics Worker
~~~

Không cho ba nhóm worker cạnh tranh trên cùng một queue nếu cả ba đều phải nhận mỗi event. Trên một queue, consumer cạnh tranh để chia việc; queue không broadcast message cho tất cả consumer.

## 87. Priority queue

SQS không có native message priority trong một queue. Pattern thường dùng:

~~~text
High-priority queue ─┐
Normal queue ────────┼→ Worker pool với scheduling policy
Low-priority queue ──┘
~~~

Phải tránh starvation của queue ưu tiên thấp và kiểm soát concurrency từng queue.

## 88. Retry queues nhiều tầng

~~~text
Main Queue
   │ transient failure
   ▼
Retry 1 (delay ngắn)
   │ vẫn lỗi
   ▼
Retry 2 (delay dài hơn)
   │ vẫn lỗi
   ▼
DLQ
~~~

Pattern này tạo backoff rõ hơn nhưng tăng số queue, policy và observability cần quản lý. Với delay trên 15 phút, cân nhắc scheduler/workflow thay vì ghép quá nhiều queue.

## 89. Command queue và event

Tên message nên phản ánh ý nghĩa:

- Command: `GenerateInvoice`, một consumer chịu trách nhiệm làm việc.
- Event: `InvoiceGenerated`, nhiều hệ thống có thể quan tâm.

SQS rất phù hợp phân phối command. Nếu cần broadcast event đến nhiều consumer độc lập, SNS → SQS hoặc EventBridge → SQS thường rõ ràng hơn.

## 90. S3 event → SQS

~~~text
S3 ObjectCreated → SQS → Worker
~~~

Queue buffer giúp worker không bị traffic burst từ upload. Consumer vẫn phải idempotent vì event notification có thể được giao lại và thứ tự có thể không như giả định.

Lưu ý compatibility: S3 Event Notifications không gửi trực tiếp tới FIFO queue. Nếu cần FIFO, có thể dùng EventBridge làm trung gian khi kiến trúc và target hỗ trợ.

## 91. Cross-account queue

Producer account A gửi vào queue account B cần phối hợp:

- Identity policy của producer role.
- Queue policy ở account B.
- KMS key policy nếu queue dùng customer-managed key.
- Source ARN/account conditions khi AWS service là producer.

Thiếu một trong các lớp này thường dẫn đến `AccessDenied` dù IAM role trông có vẻ đúng.

---

# Phần XIII — Quota và giới hạn cần nhớ

## 92. Bảng giới hạn quan trọng

| Thuộc tính | Giá trị hiện hành đáng nhớ |
|---|---:|
| Message size | 1 byte đến 1 MiB |
| Message retention | 1 phút đến 14 ngày, mặc định 4 ngày |
| Visibility timeout | 0 giây đến 12 giờ, mặc định 30 giây |
| Queue/message delay | 0 đến 15 phút |
| Long polling wait | Tối đa 20 giây |
| Batch API | Tối đa 10 entry |
| Standard queue backlog | Không giới hạn số message lưu trong queue theo quota dịch vụ công bố |
| Standard in-flight | Xấp xỉ 120.000, tùy traffic/backlog |
| FIFO deduplication interval | 5 phút |
| MessageGroupId | Bắt buộc với FIFO |

Quota throughput FIFO thay đổi theo Region, batching và high-throughput mode. Không học một con số TPS duy nhất mà quên bối cảnh.

## 93. High-throughput FIFO

Để bật high-throughput FIFO, các thiết lập liên quan gồm:

- Deduplication scope theo message group.
- FIFO throughput limit theo message group ID.

Thông lượng thực tế phụ thuộc:

- Region quota.
- Số message group hoạt động.
- Batching.
- Tốc độ send/receive/delete.
- Consumer processing time.

Một group nóng vẫn tuần tự dù queue được bật high throughput.

## 94. Những gì có thể yêu cầu tăng quota?

Một số throughput quota có thể điều chỉnh theo Region và tài khoản; một số giới hạn như maximum visibility timeout hoặc delay maximum là giới hạn thiết kế.

Trước khi yêu cầu tăng quota:

- Dùng batching.
- Dùng đủ message group cho FIFO.
- Kiểm tra consumer/downstream bottleneck.
- Kiểm tra empty receive và retry storm.
- Xem Service Quotas cho Region thực tế.

---

# Phần XIV — Troubleshooting

## 95. Queue có message nhưng consumer receive rỗng

Kiểm tra:

- Message đang delayed?
- Message đang in flight bởi consumer khác?
- FIFO group đang bị message đầu chặn?
- Short polling chỉ lấy mẫu một phần server?
- Consumer poll nhầm queue URL/Region/account?
- IAM có `ReceiveMessage`?
- Queue policy hoặc VPC endpoint policy chặn?
- Message đã hết retention?

## 96. Message xử lý lặp nhiều lần

Nguyên nhân thường gặp:

- Visibility timeout ngắn hơn processing time.
- Consumer không gọi `DeleteMessage`.
- Delete dùng receipt handle cũ.
- Consumer commit side effect rồi chết.
- Lambda cả batch retry vì một record lỗi.
- Producer gửi trùng.

Khắc phục:

- Idempotency trước tiên.
- Điều chỉnh visibility timeout.
- Partial batch response với Lambda.
- Kiểm tra delete success/failure.
- Dùng business idempotency key.

## 97. FIFO throughput thấp

Kiểm tra:

- Có phải mọi message dùng cùng group ID?
- Có poison message chặn đầu group?
- Consumer xử lý từng message quá lâu?
- Có dùng batch không?
- High-throughput FIFO đã cấu hình đúng chưa?
- Lambda maximum/reserved concurrency có bóp throughput không?
- Downstream có throttle không?

## 98. Message không vào DLQ

Kiểm tra:

- Source queue có redrive policy đúng ARN không?
- Source và DLQ cùng loại không?
- `maxReceiveCount` đã thực sự bị vượt chưa?
- Message có được receive lại hay chỉ nằm visible?
- Redrive allow policy của DLQ có chặn source không?
- Retention có hết trước khi đủ lần receive không?

## 99. `AccessDenied` với encrypted queue

Kiểm tra cả:

- IAM identity policy.
- Queue policy.
- KMS key policy.
- Producer cần quyền generate data key hay không.
- Consumer cần decrypt hay không.
- AWS service principal và SourceArn condition.
- VPC endpoint policy.

## 100. Queue age tăng nhưng CPU worker thấp

Có thể bottleneck không nằm ở CPU:

- I/O downstream chậm.
- Database connection pool cạn.
- Worker concurrency bị cấu hình thấp.
- FIFO chỉ có ít message group.
- Visibility timeout quá dài giữ nhiều message in flight.
- Lambda bị reserved concurrency/throttle.
- Batch size quá nhỏ hoặc polling không hiệu quả.

---

# Phần XV — Bẫy thường gặp trong SAA-C03

## 101. Nhầm queue với pub/sub

Nếu ba application cùng đọc một queue, không có nghĩa mỗi application nhận mọi message. Chúng là competing consumers.

Muốn mỗi application nhận một bản:

~~~text
SNS → Queue A
    → Queue B
    → Queue C
~~~

## 102. Nghĩ ReceiveMessage sẽ xóa message

Receive chỉ làm message invisible. Consumer phải delete sau thành công.

## 103. Nghĩ FIFO loại bỏ nhu cầu idempotency

FIFO chống duplicate trong phạm vi queue/dedup window; side effect consumer vẫn có thể chạy lại.

## 104. Nhầm delay với visibility timeout

- Delay: trước receive đầu tiên.
- Visibility: sau mỗi receive.

## 105. Chọn DLQ nhưng không có alarm

DLQ chỉ hữu ích khi có người/hệ thống phát hiện và xử lý.

## 106. Scale Lambda không giới hạn

Queue bảo vệ producer nhưng Lambda có thể scale đủ nhanh để làm nghẽn database. Dùng maximum concurrency/reserved concurrency và hiểu capacity downstream.

## 107. Dùng Standard khi đề yêu cầu strict order

Best-effort ordering không đáp ứng strict order. Chọn FIFO và thiết kế message group đúng scope.

## 108. Dùng một FIFO group cho toàn bộ workload

Điều này giữ global order nhưng làm mất parallelism. Nếu chỉ cần order theo customer, group theo customer.

## 109. Tưởng retention là archive

Retention tối đa 14 ngày và message biến mất sau delete/hết hạn. SQS không phải kho archive lâu dài.

## 110. Ghi nhớ message size theo tài liệu cũ

Quota hiện hành là 1 MiB. Tài liệu cũ thường ghi 256 KiB. Trong thực tế, kiểm tra tài liệu AWS tại thời điểm thiết kế.

---

# Phần XVI — Các bài lab thực hành

## 111. Lab 1 — Standard queue với worker thủ công

### Mục tiêu

Quan sát trực tiếp receive, visibility và delete.

### Các bước

1. Tạo Standard queue `saa-order-jobs`.
2. Giữ visibility timeout 30 giây.
3. Gửi một message JSON.
4. Poll message nhưng chưa delete.
5. Poll lại ngay và quan sát message đang invisible.
6. Chờ 30 giây rồi poll lại.
7. So sánh receipt handle giữa hai lần nhận.
8. Delete bằng receipt handle mới nhất.
9. Xác nhận message không xuất hiện lại.

### Cần giải thích được

- Vì sao receive không xóa message?
- Vì sao receipt handle thay đổi?
- Vì sao `ApproximateReceiveCount` tăng?

## 112. Lab 2 — Visibility timeout quá ngắn

1. Đặt visibility timeout 5 giây.
2. Viết worker giả lập xử lý 15 giây.
3. Chạy hai worker cùng poll queue.
4. Quan sát cùng message có thể được xử lý song song.
5. Thêm idempotency key và kiểm tra duplicate bị chặn.
6. Tăng timeout hoặc thêm heartbeat.

## 113. Lab 3 — DLQ

1. Tạo `saa-order-dlq`.
2. Gắn vào source queue với `maxReceiveCount = 3`.
3. Gửi một poison message.
4. Receive nhưng không delete qua nhiều vòng visibility.
5. Quan sát message chuyển sang DLQ.
6. Tạo CloudWatch alarm cho DLQ depth.
7. Giả lập sửa lỗi rồi redrive có kiểm soát.

## 114. Lab 4 — FIFO ordering

1. Tạo queue `saa-orders.fifo`.
2. Gửi message A1, A2, A3 với group A.
3. Gửi B1, B2, B3 với group B.
4. Dùng nhiều consumer.
5. Xác nhận thứ tự trong A và B được giữ, nhưng hai group chạy song song.
6. Không delete A1 và quan sát A2/A3 bị chặn.

## 115. Lab 5 — Deduplication

1. Gửi hai message cùng deduplication ID trong vòng 5 phút.
2. Quan sát chỉ một message được đưa vào queue để xử lý.
3. Thay body nhưng giữ deduplication ID.
4. Bật content-based deduplication và thử cùng body/attributes khác nhau.
5. Giải thích vì sao application idempotency vẫn cần thiết.

## 116. Lab 6 — Lambda partial batch failure

1. Tạo SQS Standard queue và Lambda consumer.
2. Batch size 5.
3. Làm record thứ ba luôn lỗi.
4. Quan sát mặc định cả batch được retry.
5. Bật `ReportBatchItemFailures`.
6. Trả riêng ID record lỗi.
7. Quan sát các record thành công không bị retry cùng batch nữa.
8. Thêm DLQ và alarm.

## 117. Lab 7 — Queue policy và encryption

1. Bật encryption.
2. Tạo producer role chỉ có `SendMessage`.
3. Tạo consumer role chỉ có receive/delete/get attributes.
4. Thử thao tác ngoài quyền để xác nhận bị từ chối.
5. Nếu dùng customer-managed KMS key, cấu hình key policy.
6. Thêm deny non-TLS bằng `aws:SecureTransport`.
7. Dùng IAM Access Analyzer kiểm tra public/cross-account access.

---

# Phần XVII — Câu hỏi tự kiểm tra

## 118. Câu 1

Một API phải nhận 50.000 yêu cầu trong vài phút, nhưng database chỉ xử lý ổn định 2.000 job/phút. Không được làm database quá tải. Giải pháp chính là gì?

**Đáp án:** Đặt SQS giữa API và worker để buffer burst; scale/giới hạn worker theo capacity database. Queue không làm tăng capacity database nhưng giúp load leveling.

## 119. Câu 2

Consumer nhận message, ghi database thành công rồi crash trước khi delete. Điều gì xảy ra?

**Đáp án:** Sau visibility timeout, message có thể visible và được xử lý lại. Consumer cần idempotency để không tạo side effect lặp.

## 120. Câu 3

Mọi update của cùng một tài khoản phải đúng thứ tự, nhưng các tài khoản khác có thể xử lý song song. Thiết kế nào phù hợp?

**Đáp án:** FIFO queue, dùng `accountId` làm `MessageGroupId`.

## 121. Câu 4

Ba service đều phải nhận event `OrderCreated`. Cho cả ba đọc cùng một SQS queue có đúng không?

**Đáp án:** Không. Chúng sẽ cạnh tranh message. Dùng SNS fan-out tới ba SQS queue riêng hoặc EventBridge route tới ba queue.

## 122. Câu 5

Job thường chạy 8 phút và đôi khi 11 phút. Visibility timeout 5 phút gây vấn đề gì?

**Đáp án:** Message có thể xuất hiện lại khi worker đầu vẫn xử lý, dẫn đến duplicate. Tăng timeout hợp lý hoặc dùng heartbeat `ChangeMessageVisibility`; vẫn phải idempotent.

## 123. Câu 6

Một Lambda batch 10 records có 1 record lỗi, và 9 record thành công liên tục bị xử lý lại. Cần thay đổi gì?

**Đáp án:** Bật partial batch response với `ReportBatchItemFailures`, trả đúng ID record lỗi và bảo đảm handler không throw toàn batch.

## 124. Câu 7

Message cần được xử lý sau 24 giờ. Có nên dùng SQS delay queue không?

**Đáp án:** Không phù hợp vì delay tối đa 15 phút. Dùng EventBridge Scheduler hoặc workflow/scheduling mechanism phù hợp.

## 125. Câu 8

FIFO queue vẫn có thể gọi Payment API hai lần không?

**Đáp án:** Có. Nếu lần đầu thành công nhưng consumer không delete được, message có thể được xử lý lại. Payment API/consumer cần idempotency key.

## 126. Câu 9

DLQ retention bằng source retention. Với Standard queue, điều này có thể gây rủi ro gì?

**Đáp án:** Message đã tiêu tốn một phần retention ở source; khi sang DLQ có thể còn ít thời gian trước khi bị xóa. Nên đặt DLQ retention dài hơn source.

## 127. Câu 10

Queue backlog thấp nhưng tuổi message cũ nhất vượt SLA. Hệ thống có khỏe không?

**Đáp án:** Không. Age cho thấy ít nhất một message bị chờ/lỗi quá lâu. Cần điều tra poison message, stuck FIFO group hoặc consumer failure.

## 128. Câu 11

Lambda consumer làm database hết connection khi backlog tăng. Có nên tăng reserved concurrency thêm không?

**Đáp án:** Không ngay lập tức. Cần giới hạn maximum concurrency, kiểm tra connection pooling/proxy và capacity database. Tăng concurrency có thể làm tình hình tệ hơn.

## 129. Câu 12

Producer role có `sqs:SendMessage` nhưng vẫn bị từ chối khi gửi vào queue dùng customer-managed KMS key. Thiếu gì?

**Đáp án:** Có thể thiếu quyền KMS/key policy như `kms:GenerateDataKey`, hoặc queue/resource policy khác đang chặn. Phải kiểm tra cả IAM, queue policy, KMS key policy và endpoint policy.

## 130. Câu 13

Standard queue trả message không đúng thứ tự. Đây có phải sự cố dịch vụ không?

**Đáp án:** Không. Standard chỉ cung cấp best-effort ordering. Nếu strict ordering là requirement, dùng FIFO.

## 131. Câu 14

Tại sao long polling thường rẻ và hiệu quả hơn short polling?

**Đáp án:** Nó chờ message tối đa 20 giây thay vì trả response rỗng ngay, nhờ đó giảm empty receives và số API request.

## 132. Câu 15

Một FIFO queue chỉ dùng một message group dù có hàng nghìn Lambda concurrency. Vì sao throughput không tăng tương ứng?

**Đáp án:** Các message trong cùng group phải xử lý tuần tự. Parallelism phụ thuộc số group hoạt động, không chỉ Lambda concurrency.

---

# Phần XVIII — Checklist thiết kế production

## 133. Producer

- [ ] Có timeout và retry với backoff/jitter.
- [ ] Có business idempotency key.
- [ ] Kiểm tra lỗi từng entry khi dùng batch API.
- [ ] Message có schema version và correlation ID.
- [ ] Không chứa secret/PII không cần thiết.
- [ ] Giải quyết dual-write bằng outbox hoặc cơ chế tương đương nếu cần.

## 134. Queue

- [ ] Chọn Standard/FIFO dựa trên requirement thực.
- [ ] Visibility timeout phù hợp processing p99.
- [ ] Retention đủ cho outage/recovery.
- [ ] Long polling được bật cho worker tự poll.
- [ ] Encryption được cấu hình.
- [ ] Queue policy least privilege và chống confused deputy.
- [ ] DLQ và redrive policy đã cấu hình.
- [ ] Tags, owner và environment rõ ràng.

## 135. Consumer

- [ ] Idempotent.
- [ ] Chỉ delete sau commit thành công.
- [ ] Phân biệt transient/permanent error.
- [ ] Có heartbeat cho job dài nếu cần.
- [ ] Giới hạn concurrency để bảo vệ downstream.
- [ ] Xử lý partial batch failure.
- [ ] Không log payload nhạy cảm.
- [ ] Có timeout cho mọi downstream call.

## 136. Operations

- [ ] Alarm queue age.
- [ ] Alarm DLQ.
- [ ] Dashboard backlog/in-flight/receive/delete/error.
- [ ] Runbook xử lý backlog và redrive.
- [ ] Load test traffic burst.
- [ ] Kiểm tra quota theo Region.
- [ ] Thử nghiệm failure: worker crash, DB throttle, poison message.

---

# Phần XIX — Bảng ghi nhớ một trang

## 137. SQS cheat sheet

~~~text
SQS = pull-based managed message queue

LIFECYCLE
Send → Visible → Receive → In flight → Delete
                              └→ timeout → Visible lại

STANDARD
- throughput rất cao
- at-least-once
- best-effort ordering
- consumer phải idempotent

FIFO
- order trong MessageGroupId
- deduplication window 5 phút
- nhiều group để tăng parallelism
- vẫn cần business idempotency

TIME
- visibility: mặc định 30 giây, tối đa 12 giờ
- retention: mặc định 4 ngày, tối đa 14 ngày
- delay: tối đa 15 phút
- long poll: tối đa 20 giây

MESSAGE
- tối đa 1 MiB theo quota hiện hành
- payload lớn: S3 + pointer
- batch API: tối đa 10 entry

FAILURE
- không delete → message xuất hiện lại
- maxReceiveCount → DLQ
- DLQ cần alarm + runbook + redrive có kiểm soát

LAMBDA
- Lambda service poll SQS
- Standard batch tới 10.000; FIFO batch tới 10
- visibility ≳ 6 × function timeout + batch window
- bật partial batch response
- giới hạn concurrency để bảo vệ downstream

SECURITY
- IAM identity policy + queue policy
- SSE-SQS hoặc SSE-KMS
- HTTPS / aws:SecureTransport
- SourceArn/SourceAccount chống confused deputy
- VPC endpoint nếu cần private connectivity
~~~

---

# 138. Tài liệu AWS chính thức

- [Amazon SQS Developer Guide](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/welcome.html)
- [Amazon SQS quotas](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-quotas.html)
- [SQS message quotas](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/quotas-messages.html)
- [Standard queue quotas](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/quotas-queues.html)
- [SQS Standard queues](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/standard-queues.html)
- [SQS FIFO queues](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-fifo-queues.html)
- [FIFO exactly-once processing và deduplication](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/FIFO-queues-exactly-once-processing.html)
- [High-throughput FIFO](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/enable-high-throughput-fifo.html)
- [Visibility timeout](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/working-with-visibility-timeouts.html)
- [Long polling](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-short-and-long-polling.html)
- [Delay queues](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-delay-queues.html)
- [Dead-letter queues](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-dead-letter-queues.html)
- [DLQ redrive](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-configure-dead-letter-queue-redrive.html)
- [SQS security best practices](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-security-best-practices.html)
- [SQS server-side encryption](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-server-side-encryption.html)
- [Lambda với SQS](https://docs.aws.amazon.com/lambda/latest/dg/with-sqs.html)
- [Cấu hình SQS event source mapping](https://docs.aws.amazon.com/lambda/latest/dg/services-sqs-configure.html)
- [Lambda partial batch response](https://docs.aws.amazon.com/lambda/latest/dg/services-sqs-errorhandling.html)
- [Lambda SQS scaling](https://docs.aws.amazon.com/lambda/latest/dg/services-sqs-scaling.html)


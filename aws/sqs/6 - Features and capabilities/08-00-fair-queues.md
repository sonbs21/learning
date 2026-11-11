# Fair queue trong Amazon SQS (Amazon SQS fair queues)

> Nguồn: [Amazon SQS fair queues](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-fair-queues.html)

Amazon SQS fair queue tự động giảm thiểu tác động "hàng xóm ồn ào" (noisy-neighbor impact) trong các queue đa người thuê (multi-tenant) chứa message từ nhiều thực thể logic khác nhau, chẳng hạn như khách hàng, ứng dụng client, hoặc loại message. Trong các môi trường queue dùng chung này, một metric hiệu năng quan trọng là dwell time, đo tổng thời gian message nằm trong queue từ khi đến cho tới khi được xử lý. Khi một tenant tạo ra tình trạng tồn đọng (backlog) trong queue bằng cách publish nhiều message hơn mức hệ thống có thể xử lý, fair queue giảm thiểu tác động lên dwell time của các tenant khác.

**Trạng thái ổn định (Steady state)**

Sơ đồ sau minh họa một queue multi-tenant chứa message từ bốn tenant riêng biệt (được gắn nhãn **A**, **B**, **C** và **D**). Queue hoạt động ở trạng thái ổn định, và không có message tồn đọng vì các consumer nhận message ngay khi chúng xuất hiện trong queue. Tất cả các tenant đều có dwell time thấp. Không phải toàn bộ năng lực (capacity) của consumer đều được sử dụng hết ở trạng thái ổn định này.

![Một queue multi-tenant chứa message từ bốn tenant khác nhau (được đại diện bởi A, B, C, D). Queue ở trạng thái ổn định với các message in-flight được phân bổ đều giữa các tenant, không có backlog và dwell time thấp cho tất cả các tenant.](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/images/FQ1_updated_aligned.png)

**Tác động noisy neighbor (Noisy neighbor impact)**

Tác động noisy neighbor xảy ra khi một tenant trong queue multi-tenant tạo ra backlog, làm tăng dwell time của message đối với tất cả các tenant khác. Một tenant có thể trở thành noisy neighbor bằng cách gửi khối lượng message lớn hơn các tenant khác, hoặc khi các consumer mất nhiều thời gian hơn để xử lý message từ tenant cụ thể đó.

Sơ đồ này minh họa cách lưu lượng tăng lên từ **Tenant A** tạo ra backlog trong queue. Các consumer bận xử lý message chỉ từ **Tenant A**, trong khi message từ các tenant khác chờ trong backlog, dẫn đến dwell time cao hơn cho tất cả các tenant.

![Kết quả khi tenant A tăng lưu lượng và tạo backlog trong queue. Message của tenant A chiếm tỷ lệ quá cao ở trạng thái in-flight và message từ các tenant khác bị kẹt trong backlog dẫn đến dwell time cao hơn.](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/images/FQ2_updated_again_aligned.png)

**Giảm thiểu bằng fair queue (Mitigation with fair queues)**

Amazon SQS phát hiện noisy neighbor bằng cách giám sát sự phân bổ message giữa các tenant trong quá trình xử lý (trạng thái "in-flight"). Khi một tenant có số lượng message in-flight lớn bất cân xứng so với các tenant khác, Amazon SQS xác định tenant đó là noisy neighbor và ưu tiên việc phân phối message cho các tenant khác. Cách tiếp cận này giảm tác động về dwell time đối với các tenant khác.

Sơ đồ này minh họa cách Amazon SQS fair queue giải quyết vấn đề noisy neighbor. Khi một tenant (**Tenant A**) trở nên ồn ào (noisy), Amazon SQS ưu tiên trả về message từ các tenant khác (**B**, **C** và **D**). Việc ưu tiên này giúp duy trì dwell time thấp cho các tenant yên tĩnh (quiet) là **Tenant B**, **C** và **D**, trong khi dwell time cho message của **Tenant A** tăng cao cho đến khi backlog của queue được tiêu thụ hết mà không ảnh hưởng đến các tenant khác.

![Minh họa cách fair queue giải quyết vấn đề noisy neighbor bằng cách giám sát trạng thái in-flight. Khi tenant A trở nên ồn ào, SQS cố gắng trả về message từ các tenant khác (B, C, D) để các message in-flight được phân bổ đều giữa các tenant. Dwell time cho các tenant (B, C, D) sẽ vẫn thấp, trong khi dwell time cho message của tenant A sẽ tăng cao cho đến khi backlog của queue được tiêu thụ hết.](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/images/FQ3_updated_aligned.png)

> **Lưu ý (Note)**
>
> Amazon SQS không giới hạn tốc độ tiêu thụ (consumption rate) theo từng tenant. Nó cho phép consumer nhận message từ các tenant noisy neighbor khi còn năng lực consumer và queue không còn message nào khác để trả về. Giống như Amazon SQS standard queue, fair queue cho phép thông lượng gần như không giới hạn, và không có giới hạn về số lượng tenant bạn có thể có trong queue của mình.

Để biết chi tiết về cách Amazon SQS phát hiện noisy neighbor và quản lý thứ tự phân phối message, xem [How Amazon SQS fair queues work](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-fair-queues-detailed.html).

## Cách sử dụng fair queue (How to use fair queues)

Để bật fair queue, các message producer nên thêm một định danh tenant (tenant identifier) bằng cách đặt `MessageGroupId` trên các message gửi đi:

```java
// Send message with tenant identifier 
SendMessageRequest request = new SendMessageRequest()    
    .withQueueUrl(queueUrl)    
    .withMessageBody(messageBody)    
    .withMessageGroupId("tenant-123"); // Tenant identifier 
sqs.sendMessage(request);
```

Khả năng đảm bảo công bằng (fairness) sẽ được áp dụng tự động trong tất cả các Amazon SQS standard queue đối với các message có thuộc tính MessageGroupId. Nó không yêu cầu bất kỳ thay đổi nào trong code của consumer, không có tác động lên độ trễ API (API latency), và không đi kèm bất kỳ giới hạn thông lượng nào.

> **Lưu ý (Note)**
>
> `MessageGroupId` trên standard queue với fair queue không có cùng hành vi như `MessageGroupId` trên FIFO queue. Trên standard queue, `MessageGroupId` chỉ được dùng làm định danh tenant cho fair queue và không áp đặt thứ tự message. Để biết chi tiết về `MessageGroupId` trên FIFO queue, xem [Using the message group ID with Amazon SQS FIFO Queues](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/using-messagegroupid-property.html).

## Khi nào nên sử dụng fair queue (When to use fair queues)

Hãy cân nhắc sử dụng fair queue khi tất cả các điều sau đây đều áp dụng cho queue của bạn:

- **Queue là multi-tenant.** Queue mang các message thuộc về nhiều thực thể logic, chẳng hạn như khách hàng, ứng dụng client, hoặc loại request, và bạn có thể nhận diện mỗi thực thể bằng một `MessageGroupId`.
- **Queue có thông lượng cao (high-throughput).** Ở thông lượng thấp, đợt tăng đột biến (burst) của một tenant hiếm khi tạo ra backlog ảnh hưởng đến các tenant khác. Ở thông lượng cao, việc mở rộng quá mức (over-scaling) đội consumer để hấp thụ mọi đợt burst là không thực tế, và ngay cả với auto-scaling, độ trễ trước khi các consumer mới sẵn sàng hoạt động có thể khiến backlog hình thành.
- **Dwell time là một phần trong chất lượng dịch vụ (quality of service) của ứng dụng.** Fair queue bảo vệ các tenant yên tĩnh khỏi dwell time tăng cao do một noisy neighbor gây ra. Nếu ứng dụng của bạn không nhạy cảm với dwell time, khả năng bảo vệ khỏi noisy-neighbor mà fair queue cung cấp có thể không cần thiết.

## Các metric CloudWatch của fair queue (Fair queues CloudWatch metrics)

Amazon SQS cung cấp các metric CloudWatch bổ sung để giúp bạn giám sát việc giảm thiểu tác động noisy neighbor. Ví dụ, bạn có thể so sánh các metric `Approximate..InQuietGroups` với các metric tiêu chuẩn ở cấp queue. Trong các đợt tăng lưu lượng đột biến của một tenant cụ thể, các metric chung ở cấp queue có thể cho thấy backlog đang tăng hoặc tuổi message (message age) cũ hơn. Tuy nhiên, khi xem xét riêng các quiet group, bạn có thể nhận ra rằng hầu hết các message group hoặc tenant không ồn ào đều không bị ảnh hưởng.

Dưới đây là một ví dụ trong đó metric backlog tiêu chuẩn của queue (ApproximateNumberOfMessagesVisible) tăng lên do một tenant ồn ào, trong khi backlog của các tenant không ồn ào (ApproximateNumberOfMessagesVisibleInQuietGroups) vẫn ở mức thấp.

![Biểu đồ cho thấy backlog của queue tăng vọt đối với các noisy group trong khi các quiet group vẫn ở mức thấp.](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/images/fair_queues_cw_metrics.png)

Để xem danh sách đầy đủ các metric CloudWatch của Amazon SQS và mô tả của chúng, xem [CloudWatch metrics for Amazon SQS](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-available-cloudwatch-metrics.html#sqs-metrics).

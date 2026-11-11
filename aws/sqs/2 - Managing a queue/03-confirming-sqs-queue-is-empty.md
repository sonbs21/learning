# Xác nhận một Amazon SQS queue đã trống (Confirming that an Amazon SQS queue is empty)

> Nguồn: [Confirming that an Amazon SQS queue is empty](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/confirm-queue-is-empty.html)

Trong hầu hết các trường hợp, bạn có thể sử dụng [long polling](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-short-and-long-polling.html#sqs-long-polling) để xác định một queue có trống hay không. Trong một số ít trường hợp, bạn có thể nhận được response rỗng ngay cả khi queue vẫn còn chứa message, đặc biệt nếu bạn đã chỉ định một giá trị thấp cho **Receive message wait time** khi tạo queue. Phần này mô tả cách xác nhận một queue đã trống.

**Để xác nhận một queue đã trống (console)**

1. Dừng tất cả các producer gửi message.
2. Mở Amazon SQS console tại [https://console.aws.amazon.com/sqs/](https://console.aws.amazon.com/sqs/).
3. Trong khung điều hướng (navigation pane), chọn **Queues**.
4. Trên trang **Queues**, chọn một queue.
5. Chọn tab **Monitoring**.
6. Ở góc trên bên phải của các Monitoring dashboard, chọn mũi tên xuống bên cạnh biểu tượng Refresh. Từ menu dropdown, chọn **Auto refresh**. Giữ nguyên **Refresh interval** ở mức **1 Minute**.
7. Quan sát các dashboard sau:
   - Approximate Number Of Messages Delayed
   - Approximate Number Of Messages Not Visible
   - Approximate Number Of Messages Visible

   Khi tất cả chúng đều hiển thị giá trị `0` trong vài phút, queue đã trống.

**Để xác nhận một queue đã trống (AWS CLI, AWS API)**

1. Dừng tất cả các producer gửi message.
2. Chạy lặp lại một trong các lệnh sau:
   - AWS CLI: [get-queue-attributes](https://docs.aws.amazon.com/cli/latest/reference/get-queue-attributes.html)
   - AWS API: [GetQueueAttributes](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/APIReference/API_GetQueueAttributes.html)
3. Quan sát các metric của các attribute sau:
   - `ApproximateNumberOfMessagesDelayed`
   - `ApproximateNumberOfMessagesNotVisible`
   - `ApproximateNumberOfMessagesVisible`

   Khi tất cả chúng đều bằng `0` trong vài phút, queue đã trống.

Nếu bạn dựa vào các metric của Amazon CloudWatch, hãy đảm bảo rằng bạn thấy nhiều data point bằng 0 liên tiếp trước khi coi queue đó là trống. Để biết thêm thông tin về các metric của CloudWatch, xem [Available CloudWatch metrics for Amazon SQS](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-available-cloudwatch-metrics.html).

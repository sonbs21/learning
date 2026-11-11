# Cấu hình dead-letter queue bằng Amazon SQS console (Configure a dead-letter queue using the Amazon SQS console)

> Nguồn: [Configure a dead-letter queue using the Amazon SQS console](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-configure-dead-letter-queue.html)

Dead-letter queue (DLQ) là một queue nhận các message không được xử lý thành công từ một queue khác, được gọi là source queue. Amazon SQS *không* tự động tạo dead-letter queue. Bạn phải tạo queue trước rồi mới sử dụng nó làm dead-letter queue. Khi cấu hình DLQ, loại queue phải khớp với loại của source queue — một [FIFO queue](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-fifo-queues.html) chỉ có thể sử dụng FIFO DLQ, và một [standard queue](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/standard-queues.html) chỉ có thể sử dụng standard DLQ. Bạn có thể cấu hình dead-letter queue khi tạo hoặc chỉnh sửa một queue. Để biết thêm chi tiết, xem [Using dead-letter queues in Amazon SQS](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-dead-letter-queues.html).

**Để cấu hình dead-letter queue cho một queue hiện có (console)**

1. Mở Amazon SQS console tại [https://console.aws.amazon.com/sqs/](https://console.aws.amazon.com/sqs/).
2. Trong khung điều hướng (navigation pane), chọn **Queues**.
3. Chọn **source queue** (queue sẽ gửi các message bị lỗi tới dead-letter queue), sau đó chọn **Edit**.
4. Cuộn tới phần **Dead-letter queue** và bật (toggle) **Enabled**.
5. Trong **Dead-letter queue settings**, chọn Amazon Resource Name (ARN) của một queue hiện có mà bạn muốn sử dụng làm **dead-letter queue**.
6. Đặt giá trị **Maximum receives**, xác định số lần một message có thể được nhận trước khi bị gửi tới dead-letter queue (phạm vi hợp lệ: **1 đến 1.000**).
7. Chọn **Save**.

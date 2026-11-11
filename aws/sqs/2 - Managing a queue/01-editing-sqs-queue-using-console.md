# Chỉnh sửa một Amazon SQS queue bằng console (Editing an Amazon SQS queue using the console)

> Nguồn: [Editing an Amazon SQS queue using the console](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-configure-edit-queue.html)

Bạn có thể sử dụng Amazon SQS console để chỉnh sửa các tham số cấu hình của queue (ngoại trừ loại queue) và sửa đổi hoặc gỡ bỏ các tính năng khi cần.

**Để chỉnh sửa một Amazon SQS queue (console)**

1. Mở [trang Queues](https://console.aws.amazon.com/sqs/#/queues) của Amazon SQS console.
2. Chọn một queue, sau đó chọn **Edit**.
3. (Tùy chọn) Trong phần **Configuration**, cập nhật các [tham số cấu hình (configuration parameters)](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-configure-queue-parameters.html) của queue.
4. (Tùy chọn) Để cập nhật [access policy](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-configure-add-permissions.html), trong phần **Access policy**, chỉnh sửa **JSON policy**.
5. (Tùy chọn) Để cập nhật [redrive allow policy](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-configure-dead-letter-queue-redrive.html) của dead-letter queue, mở rộng **Redrive allow policy**.
6. (Tùy chọn) Để cập nhật hoặc gỡ bỏ [mã hóa (encryption)](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-configure-sse-existing-queue.html), mở rộng **Encryption**.
7. (Tùy chọn) Để thêm, cập nhật hoặc gỡ bỏ một [dead-letter queue](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-configure-dead-letter-queue.html) (cho phép bạn nhận các message không thể phân phối – undeliverable), mở rộng **Dead-letter queue**.
8. (Tùy chọn) Để thêm, cập nhật hoặc gỡ bỏ các [tag](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-configure-tag-queue.html) của queue, mở rộng **Tags**.
9. Chọn **Save**.
   - Console hiển thị trang **Details** của queue.

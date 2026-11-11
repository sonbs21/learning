# Xóa một Amazon SQS queue (Deleting an Amazon SQS queue)

> Nguồn: [Deleting an Amazon SQS queue](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/step-delete-queue.html)

Nếu bạn không còn sử dụng một Amazon SQS queue và không có kế hoạch sử dụng nó trong tương lai gần, hãy xóa queue đó.

> **Mẹo (Tip)**
>
> Nếu bạn muốn kiểm tra rằng một queue đã trống trước khi xóa nó, xem [Confirming that an Amazon SQS queue is empty](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/confirm-queue-is-empty.html).

Bạn có thể xóa một queue ngay cả khi nó chưa trống. Để xóa các message trong queue nhưng không xóa bản thân queue, hãy [purge queue](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-using-purge-queue.html).

**Để xóa một queue (console)**

1. Mở Amazon SQS console tại [https://console.aws.amazon.com/sqs/](https://console.aws.amazon.com/sqs/).
2. Trong khung điều hướng (navigation pane), chọn **Queues**.
3. Trên trang **Queues**, chọn queue cần xóa.
4. Chọn **Delete**.
5. Trong hộp thoại **Delete queue**, xác nhận việc xóa bằng cách nhập **delete**.
6. Chọn **Delete**.

**Để xóa một queue (AWS CLI và API)**

Chọn phương thức phù hợp để xóa queue của bạn dựa trên nhu cầu:

- AWS CLI: [aws sqs delete-queue](https://docs.aws.amazon.com/cli/latest/reference/sqs/delete-queue.html)
- AWS API: [DeleteQueue](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/APIReference/API_DeleteQueue.html)

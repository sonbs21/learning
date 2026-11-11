# Cấu hình access policy trong Amazon SQS (Configuring an access policy in Amazon SQS)

> Nguồn: [Configuring an access policy in Amazon SQS](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-configure-add-permissions.html)

Khi bạn [chỉnh sửa](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-configure-edit-queue.html) một queue, bạn có thể cấu hình access policy của nó để kiểm soát ai có thể tương tác với queue.

- Access policy định nghĩa những account, user và role nào có permission truy cập queue.
- Nó chỉ định các action được phép, chẳng hạn như [`SendMessage`](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/APIReference/API_SendMessage.html), [`ReceiveMessage`](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/APIReference/API_ReceiveMessage.html), hoặc [`DeleteMessage`](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/APIReference/API_DeleteMessage.html).
- Theo mặc định, chỉ chủ sở hữu queue (queue owner) mới có permission gửi và nhận message.

**Để cấu hình access policy cho một queue hiện có (console)**

1. Mở Amazon SQS console tại [https://console.aws.amazon.com/sqs/](https://console.aws.amazon.com/sqs/).
2. Trong khung điều hướng (navigation pane), chọn **Queues**.
3. Chọn một queue và chọn **Edit**.
4. Cuộn tới phần **Access policy**.
5. Chỉnh sửa các **access policy statement** trong ô nhập liệu. Để biết thêm về các access policy statement, xem [Identity and access management in Amazon SQS](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/security-iam.html).
6. Khi bạn hoàn tất cấu hình access policy, chọn **Save**.

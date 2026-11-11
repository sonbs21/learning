# Cấu hình cost allocation tag cho queue bằng Amazon SQS console (Configuring cost allocation tags for a queue using the Amazon SQS console)

> Nguồn: [Configuring cost allocation tags for a queue using the Amazon SQS console](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-configure-tag-queue.html)

Để tổ chức và nhận diện các Amazon SQS queue của bạn, bạn có thể thêm các cost allocation tag (tag phân bổ chi phí). Để biết thêm thông tin, xem [Amazon SQS cost allocation tags](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-queue-tags.html).

- Tab Tagging trên trang Details hiển thị các tag của queue.
- Bạn có thể thêm hoặc sửa đổi tag khi [tạo](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/creating-sqs-standard-queues.html#step-create-standard-queue) hoặc [chỉnh sửa](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-configure-edit-queue.html) một queue.

**Để cấu hình tag cho một queue hiện có (console)**

1. Mở Amazon SQS console tại [https://console.aws.amazon.com/sqs/](https://console.aws.amazon.com/sqs/).
2. Trong khung điều hướng (navigation pane), chọn **Queues**.
3. Chọn một queue và chọn **Edit**.
4. Cuộn tới phần **Tags**.
5. Thêm, sửa đổi hoặc xóa các tag của queue:
   1. Để thêm một tag, chọn **Add new tag**, nhập **Key** và **Value**, sau đó chọn **Add new tag**.
   2. Để cập nhật một tag, thay đổi **Key** và **Value** của nó.
   3. Để xóa một tag, chọn **Remove** bên cạnh cặp key-value của nó.
6. Khi bạn hoàn tất cấu hình tag, chọn **Save**.

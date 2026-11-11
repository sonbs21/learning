# Dọn sạch (purge) message khỏi một queue bằng Amazon SQS console (Purging messages from an queue using the Amazon SQS console)

> Nguồn: [Purging messages from an queue using the Amazon SQS console](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-using-purge-queue.html)

Để giữ lại một Amazon SQS queue nhưng xóa tất cả các message của nó, bạn có thể purge queue. Thao tác này sẽ xóa tất cả các message, bao gồm cả những message hiện đang ở trạng thái ẩn (invisible – in flight). Quá trình purge có thể mất tới 60 giây, vì vậy hãy chờ đủ 60 giây bất kể kích thước của queue là bao nhiêu.

> **Quan trọng (Important)**
>
> Khi bạn purge một queue, bạn không thể truy xuất lại bất kỳ message nào đã bị xóa.

**Để purge một queue (console)**

1. Mở Amazon SQS console tại [https://console.aws.amazon.com/sqs/](https://console.aws.amazon.com/sqs/).
2. Trong khung điều hướng (navigation pane), chọn **Queues**.
3. Trên trang **Queues**, chọn queue cần purge.
4. Từ **Actions**, chọn **Purge**.
5. Trong hộp thoại **Purge queue**, xác nhận việc purge bằng cách nhập **purge** và chọn **Purge**.
   - Tất cả các message được purge khỏi queue. Console hiển thị một banner xác nhận.

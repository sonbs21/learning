# Xen kẽ nhiều message group có thứ tự trong Amazon SQS (Interleaving multiple ordered message groups in Amazon SQS)

> Nguồn: [Interleaving multiple ordered message groups in Amazon SQS](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/interleaving-multiple-ordered-message-groups.html)

Để xen kẽ (interleave) nhiều message group có thứ tự bên trong một FIFO queue duy nhất, hãy gán một [`MessageGroupId`](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/APIReference/API_SendMessage.html) cho mỗi group (ví dụ, dữ liệu session của các user khác nhau). Điều này cho phép nhiều consumer đọc từ queue đồng thời trong khi vẫn đảm bảo rằng các message trong cùng một group được xử lý theo thứ tự.

Khi một message với một `MessageGroupId` cụ thể đang được xử lý và ở trạng thái ẩn (invisible), không có consumer nào khác có thể xử lý các message từ cùng group đó cho đến khi visibility timeout hết hạn hoặc message bị xóa.

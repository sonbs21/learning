# Tránh tồn đọng message lớn với cùng message group ID trong Amazon SQS (Avoid large message backlogs with the same message group ID in Amazon SQS)

> Nguồn: [Avoid large message backlogs with the same message group ID in Amazon SQS](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/avoid-backlog-with-the-same-message-group-id.html)

FIFO queue hỗ trợ tối đa 120.000 message in-flight (các message đã được consumer nhận nhưng chưa bị xóa). Nếu đạt tới giới hạn này, Amazon SQS không trả về lỗi, nhưng việc xử lý có thể bị ảnh hưởng. Bạn có thể yêu cầu tăng vượt quá giới hạn này bằng cách liên hệ [AWS Support](https://docs.aws.amazon.com/awssupport/latest/user/create-service-quota-increase.html).

FIFO queue quét (scan) 120.000 message đầu tiên để xác định các message group khả dụng. Nếu một lượng tồn đọng (backlog) lớn tích tụ trong một message group duy nhất, các message từ các group khác được gửi sau đó sẽ vẫn bị chặn cho đến khi backlog được xử lý xong.

> **Lưu ý (Note)**
>
> Tình trạng tồn đọng message có thể xảy ra khi một consumer liên tục xử lý thất bại một message. Điều này có thể do các vấn đề về nội dung message hoặc lỗi phía consumer. Để ngăn chặn sự chậm trễ trong việc xử lý message, hãy cấu hình một [dead-letter queue](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-dead-letter-queues.html) để chuyển các message chưa được xử lý đi sau nhiều lần thử thất bại. Điều này đảm bảo rằng các message khác trong cùng message group có thể được xử lý, ngăn chặn nút thắt cổ chai (bottleneck) của hệ thống.

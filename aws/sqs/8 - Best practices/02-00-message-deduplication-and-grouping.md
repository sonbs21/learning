# Khử trùng lặp và nhóm message trong Amazon SQS (Amazon SQS message deduplication and grouping)

> Nguồn: [Amazon SQS message deduplication and grouping](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/best-practices-message-deduplication.html)

Chủ đề này cung cấp các best practice để đảm bảo việc xử lý message nhất quán trong Amazon SQS. Nó giải thích cách sử dụng:

- [`MessageDeduplicationId`](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/APIReference/API_SendMessage.html#API_SendMessage_RequestSyntax) để ngăn chặn các message trùng lặp trong FIFO queue.
- [`MessageGroupId`](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/APIReference/API_SendMessage.html) để quản lý thứ tự message bên trong các message group riêng biệt.

**Các chủ đề (Topics)**

- [Avoiding inconsistent message processing in Amazon SQS](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/avoiding-inconsistent-message-processing.html)
- [Using the message deduplication ID](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/using-messagededuplicationid-property.html)
- [Using the message group ID](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/using-messagegroupid-property.html)
- [Using the receive request attempt ID](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/using-receiverequestattemptid-request-parameter.html)

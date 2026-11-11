# Thu nhận các message có vấn đề trong Amazon SQS (Capturing problematic messages in Amazon SQS)

> Nguồn: [Capturing problematic messages in Amazon SQS](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/capturing-problematic-messages.html)

Để thu nhận tất cả các message không thể xử lý được, và để thu thập các metric CloudWatch chính xác, hãy cấu hình một [dead-letter queue](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-dead-letter-queues.html).

- Redrive policy chuyển hướng (redirect) message tới một dead-letter queue sau khi source queue xử lý một message thất bại một số lần được chỉ định.
- Việc sử dụng dead-letter queue làm giảm số lượng message và giảm khả năng khiến bạn gặp phải các message *poison pill* (các message được nhận nhưng không thể xử lý được).
- Việc có một message poison pill trong queue có thể làm sai lệch metric CloudWatch [`ApproximateAgeOfOldestMessage`](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-available-cloudwatch-metrics.html) do đưa ra tuổi (age) không chính xác của message poison pill. Việc cấu hình một dead-letter queue giúp tránh các cảnh báo sai (false alarm) khi sử dụng metric này.

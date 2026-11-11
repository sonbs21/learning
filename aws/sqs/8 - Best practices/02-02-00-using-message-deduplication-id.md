# Sử dụng message deduplication ID trong Amazon SQS (Using the message deduplication ID in Amazon SQS)

> Nguồn: [Using the message deduplication ID in Amazon SQS](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/using-messagededuplicationid-property.html)

[`MessageDeduplicationId`](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/APIReference/API_SendMessage.html) là một token chỉ được sử dụng trong Amazon SQS FIFO queue để ngăn chặn việc phân phối message trùng lặp. Nó đảm bảo rằng trong một khoảng thời gian khử trùng lặp (deduplication window) 5 phút, chỉ một instance của message có cùng deduplication ID được xử lý và phân phối.

Nếu Amazon SQS đã chấp nhận một message với một deduplication ID cụ thể, bất kỳ message nào sau đó có cùng ID sẽ được xác nhận (acknowledged) nhưng không được phân phối tới consumer.

> **Lưu ý (Note)**
>
> Amazon SQS tiếp tục theo dõi deduplication ID ngay cả sau khi message đã được nhận và xóa.

**Các chủ đề (Topics)**

- [When to provide a message deduplication ID in Amazon SQS](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/providing-message-deduplication-id.html)
- [Enabling deduplication for a single-producer/consumer system in Amazon SQS](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/single-producer-single-consumer.html)
- [Outage recovery scenarios in Amazon SQS](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/designing-for-outage-recovery-scenarios.html)
- [Configuring visibility timeouts in Amazon SQS](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/working-with-visibility-timeouts.html)

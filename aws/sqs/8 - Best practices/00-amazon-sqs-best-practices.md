# Các best practice của Amazon SQS (Amazon SQS best practices)

> Nguồn: [Amazon SQS best practices](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-best-practices.html)

Amazon SQS quản lý và xử lý các message queue, cho phép các phần khác nhau của một ứng dụng trao đổi message một cách tin cậy và ở quy mô lớn. Chủ đề này đề cập đến các best practice vận hành quan trọng, bao gồm việc sử dụng long polling để giảm các response rỗng, triển khai dead-letter queue để xử lý các lỗi xử lý (processing error), và tối ưu hóa permission của queue để đảm bảo bảo mật.

**Các chủ đề (Topics)**

- [Error handling and problematic messages](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/best-practices-error-handling.html)
- [Message deduplication and grouping](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/best-practices-message-deduplication.html)
- [Message processing and timing](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/best-practices-message-processing.html)

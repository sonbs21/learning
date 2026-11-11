# Sử dụng message group ID với Amazon SQS FIFO queue (Using the message group ID with Amazon SQS FIFO Queues)

> Nguồn: [Using the message group ID with Amazon SQS FIFO Queues](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/using-messagegroupid-property.html)

Trong FIFO (First-In-First-Out) queue, [`MessageGroupId`](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/APIReference/API_SendMessage.html) là một attribute tổ chức các message thành các nhóm riêng biệt. Các message trong cùng một message group luôn được xử lý từng cái một, theo thứ tự nghiêm ngặt, đảm bảo rằng không có hai message nào từ cùng một group được xử lý đồng thời. Trong standard queue, việc sử dụng `MessageGroupId` cho phép sử dụng [fair queue](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-fair-queues.html). Nếu cần thứ tự nghiêm ngặt, hãy sử dụng FIFO queue.

**Các chủ đề (Topics)**

- [Interleaving multiple ordered message groups in Amazon SQS](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/interleaving-multiple-ordered-message-groups.html)
- [Preventing duplicate processing in a multiple-producer/consumer system in Amazon SQS](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/avoding-processing-duplicates-in-multiple-producer-consumer-system.html)
- [Avoid large message backlogs with the same message group ID in Amazon SQS](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/avoid-backlog-with-the-same-message-group-id.html)
- [Avoid reusing the same message group ID with virtual queues in Amazon SQS](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/avoiding-reusing-message-group-id-with-virtual-queues.html)

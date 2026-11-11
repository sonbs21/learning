# Tránh tái sử dụng cùng message group ID với virtual queue trong Amazon SQS (Avoid reusing the same message group ID with virtual queues in Amazon SQS)

> Nguồn: [Avoid reusing the same message group ID with virtual queues in Amazon SQS](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/avoiding-reusing-message-group-id-with-virtual-queues.html)

Khi sử dụng virtual queue với một host queue dùng chung, hãy tránh tái sử dụng cùng một [`MessageGroupId`](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/APIReference/API_SendMessage.html) giữa các virtual queue khác nhau. Nếu nhiều virtual queue dùng chung cùng một host queue và chứa các message có cùng `MessageGroupId`, các message đó có thể chặn (block) lẫn nhau, cản trở việc xử lý hiệu quả. Để đảm bảo việc xử lý message diễn ra suôn sẻ, hãy gán các giá trị `MessageGroupId` duy nhất (unique) cho các message trong các virtual queue khác nhau.

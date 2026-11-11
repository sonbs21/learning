# Cấu hình visibility timeout trong Amazon SQS (Configuring visibility timeouts in Amazon SQS)

> Nguồn: [Configuring visibility timeouts in Amazon SQS](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/working-with-visibility-timeouts.html)

Để đảm bảo việc xử lý message tin cậy, hãy đặt visibility timeout dài hơn read timeout của AWS SDK. Điều này áp dụng khi sử dụng API [`ReceiveMessage`](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/APIReference/API_ReceiveMessage.html) với cả short polling và long polling. Một visibility timeout dài hơn sẽ ngăn các message trở nên sẵn có (available) với các consumer khác trước khi request ban đầu hoàn tất, giúp giảm rủi ro xử lý trùng lặp.

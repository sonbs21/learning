# Xử lý đúng một lần trong Amazon SQS (Exactly-once processing in Amazon SQS)

> Nguồn: [Exactly-once processing in Amazon SQS](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/FIFO-queues-exactly-once-processing.html)

Không giống như standard queue, FIFO queue không tạo ra message trùng lặp. FIFO queue giúp bạn tránh gửi các bản trùng lặp vào queue. Nếu bạn thử lại (retry) action `SendMessage` trong khoảng thời gian khử trùng lặp (deduplication interval) 5 phút, Amazon SQS không đưa bất kỳ bản trùng lặp nào vào queue.

Để cấu hình việc khử trùng lặp (deduplication), bạn phải thực hiện một trong các cách sau:

- Bật content-based deduplication (khử trùng lặp dựa trên nội dung). Điều này chỉ dẫn Amazon SQS sử dụng hàm băm SHA-256 để tạo message deduplication ID dựa trên nội dung (body) của message — nhưng không dựa trên các attribute của message. Để biết thêm thông tin, xem tài liệu về các action [`CreateQueue`](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/APIReference/API_CreateQueue.html), [`GetQueueAttributes`](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/APIReference/API_GetQueueAttributes.html) và [`SetQueueAttributes`](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/APIReference/API_SetQueueAttributes.html) trong *Amazon Simple Queue Service API Reference*.
- Cung cấp một cách tường minh (explicitly) message deduplication ID (hoặc xem sequence number) cho message. Để biết thêm thông tin, xem tài liệu về các action [`SendMessage`](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/APIReference/API_SendMessage.html), [`SendMessageBatch`](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/APIReference/API_SendMessageBatch.html) và [`ReceiveMessage`](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/APIReference/API_ReceiveMessage.html) trong *Amazon Simple Queue Service API Reference*.

# Các kịch bản khôi phục sau sự cố trong Amazon SQS (Outage recovery scenarios in Amazon SQS)

> Nguồn: [Outage recovery scenarios in Amazon SQS](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/designing-for-outage-recovery-scenarios.html)

Quá trình khử trùng lặp (deduplication) trong FIFO queue phụ thuộc vào thời gian (time-sensitive). Khi thiết kế ứng dụng, hãy đảm bảo rằng cả producer và consumer đều có thể khôi phục sau các sự cố (outage) của client hoặc mạng mà không tạo ra bản trùng lặp hay lỗi xử lý.

**Các cân nhắc cho producer (Producer considerations)**

- Amazon SQS áp dụng một khoảng thời gian khử trùng lặp (deduplication window) 5 phút.
- Nếu một producer retry một request [`SendMessage`](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/APIReference/API_SendMessage.html) sau 5 phút, Amazon SQS coi đó là một message mới, có khả năng tạo ra bản trùng lặp.

**Các cân nhắc cho consumer (Consumer considerations)**

- Nếu một consumer xử lý message thất bại trước khi visibility timeout hết hạn, một consumer khác có thể nhận và xử lý message đó, dẫn đến việc xử lý trùng lặp.
- Điều chỉnh visibility timeout dựa trên thời gian xử lý của ứng dụng.
- Sử dụng API [`ChangeMessageVisibility`](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/APIReference/API_ChangeMessageVisibility.html) để kéo dài timeout trong khi message vẫn đang được xử lý.
- Nếu một message liên tục xử lý thất bại, hãy định tuyến (route) nó tới một [dead-letter queue (DLQ)](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-dead-letter-queues.html) thay vì để nó bị xử lý lại vô thời hạn.
- Producer phải nắm được khoảng thời gian khử trùng lặp (deduplication interval) của queue. Amazon SQS có deduplication interval là 5 phút. Việc retry các request `SendMessage` sau khi deduplication interval hết hạn có thể đưa các message trùng lặp vào queue. Ví dụ, một thiết bị di động trong ô tô gửi các message mà thứ tự là quan trọng. Nếu ô tô mất kết nối di động (cellular connectivity) trong một khoảng thời gian trước khi nhận được xác nhận (acknowledgement), việc retry request sau khi có lại kết nối di động có thể tạo ra một bản trùng lặp.
- Consumer phải có một visibility timeout giúp giảm thiểu rủi ro không thể xử lý message trước khi visibility timeout hết hạn. Bạn có thể kéo dài visibility timeout trong khi các message đang được xử lý bằng cách gọi action `ChangeMessageVisibility`. Tuy nhiên, nếu visibility timeout hết hạn, một consumer khác có thể ngay lập tức bắt đầu xử lý các message đó, khiến một message bị xử lý nhiều lần. Để tránh kịch bản này, hãy cấu hình một [dead-letter queue](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-dead-letter-queues.html).

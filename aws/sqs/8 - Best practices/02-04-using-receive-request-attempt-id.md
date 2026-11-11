# Sử dụng receive request attempt ID của Amazon SQS (Using the Amazon SQS receive request attempt ID)

> Nguồn: [Using the Amazon SQS receive request attempt ID](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/using-receiverequestattemptid-request-parameter.html)

Receive request attempt ID là một token duy nhất được sử dụng để khử trùng lặp (deduplicate) các lời gọi [`ReceiveMessage`](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/APIReference/API_ReceiveMessage.html) trong Amazon SQS. Trong trường hợp mất kết nối mạng (network outage) hoặc có sự cố kết nối giữa ứng dụng của bạn và Amazon SQS, best practice là:

- Cung cấp một receive request attempt ID khi thực hiện lời gọi `ReceiveMessage`.
- Retry bằng cùng receive request attempt ID đó nếu thao tác thất bại.

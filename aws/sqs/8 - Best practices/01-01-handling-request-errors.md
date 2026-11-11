# Xử lý lỗi request trong Amazon SQS (Handling request errors in Amazon SQS)

> Nguồn: [Handling request errors in Amazon SQS](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/handling-request-errors.html)

Để xử lý lỗi request, hãy sử dụng một trong các chiến lược sau:

- Nếu bạn sử dụng một AWS SDK, bạn đã có sẵn logic *retry và backoff* tự động. Để biết thêm thông tin, xem [Error Retries and Exponential Backoff in AWS](https://docs.aws.amazon.com/general/latest/gr/api-retries.html) trong *Amazon Web Services General Reference*.
- Nếu bạn không sử dụng các tính năng retry và backoff của AWS SDK, hãy tạm dừng (pause) một khoảng (ví dụ, 200 ms) trước khi retry action [ReceiveMessage](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/APIReference/API_ReceiveMessage.html) sau khi không nhận được message nào, bị timeout, hoặc nhận được thông báo lỗi từ Amazon SQS. Đối với các lần sử dụng `ReceiveMessage` tiếp theo cho cùng kết quả, hãy tạm dừng lâu hơn (ví dụ, 400 ms).

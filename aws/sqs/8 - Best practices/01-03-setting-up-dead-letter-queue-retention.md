# Thiết lập thời gian lưu giữ của dead-letter queue trong Amazon SQS (Setting-up dead-letter queue retention in Amazon SQS)

> Nguồn: [Setting-up dead-letter queue retention in Amazon SQS](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/setting-up-dead-letter-queue-retention.html)

Đối với standard queue, việc hết hạn (expiration) của một message luôn dựa trên timestamp đưa vào queue ban đầu (original enqueue timestamp) của nó. Khi một message bị chuyển vào dead-letter queue, enqueue timestamp không thay đổi. Metric `ApproximateAgeOfOldestMessage` cho biết thời điểm message bị chuyển vào dead-letter queue, *không phải* thời điểm message được gửi ban đầu. Ví dụ, giả sử một message nằm 1 ngày trong queue gốc trước khi bị chuyển vào dead-letter queue. Nếu thời gian lưu giữ (retention period) của dead-letter queue là 4 ngày, message sẽ bị xóa khỏi dead-letter queue sau 3 ngày và `ApproximateAgeOfOldestMessage` là 3 ngày. Do đó, best practice là luôn đặt thời gian lưu giữ của dead-letter queue dài hơn thời gian lưu giữ của queue gốc.

Đối với FIFO queue, enqueue timestamp được đặt lại (reset) khi message bị chuyển vào dead-letter queue. Metric `ApproximateAgeOfOldestMessage` cho biết thời điểm message bị chuyển vào dead-letter queue. Trong cùng ví dụ trên, message sẽ bị xóa khỏi dead-letter queue sau 4 ngày và `ApproximateAgeOfOldestMessage` là 4 ngày.

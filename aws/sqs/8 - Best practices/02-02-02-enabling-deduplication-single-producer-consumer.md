# Bật khử trùng lặp cho hệ thống một producer/một consumer trong Amazon SQS (Enabling deduplication for a single-producer/consumer system in Amazon SQS)

> Nguồn: [Enabling deduplication for a single-producer/consumer system in Amazon SQS](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/single-producer-single-consumer.html)

Nếu bạn có một producer duy nhất và một consumer duy nhất, và các message là duy nhất (unique) vì chúng chứa một message ID dành riêng cho ứng dụng (application-specific message ID) trong body, hãy làm theo các best practice sau:

- Bật content-based deduplication cho queue (mỗi message của bạn có một body duy nhất). Producer có thể bỏ qua message deduplication ID.
- Khi content-based deduplication được bật cho một Amazon SQS FIFO queue, và một message được gửi kèm một deduplication ID, deduplication ID của [`SendMessage`](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/APIReference/API_SendMessage.html) sẽ ghi đè (override) deduplication ID được tạo ra dựa trên nội dung.
- Mặc dù consumer không bắt buộc phải cung cấp receive request attempt ID cho mỗi request, đây là một best practice vì nó cho phép các chuỗi thất bại–thử lại (fail-retry sequence) thực thi nhanh hơn.
- Bạn có thể retry các request gửi hoặc nhận vì chúng không ảnh hưởng đến thứ tự của message trong FIFO queue.

# Khi nào cần cung cấp message deduplication ID trong Amazon SQS (When to provide a message deduplication ID in Amazon SQS)

> Nguồn: [When to provide a message deduplication ID in Amazon SQS](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/providing-message-deduplication-id.html)

Một producer nên chỉ định một message deduplication ID trong các kịch bản sau:

- Khi gửi các message có nội dung (message body) giống hệt nhau nhưng phải được coi là duy nhất (unique).
- Khi gửi các message có cùng nội dung nhưng khác message attribute, nhằm đảm bảo mỗi message được xử lý riêng biệt.
- Khi gửi các message có nội dung khác nhau (ví dụ, một bộ đếm retry – retry counter – trong message body) nhưng yêu cầu Amazon SQS nhận diện chúng là trùng lặp.

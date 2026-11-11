# Phân phối ít nhất một lần trong Amazon SQS (Amazon SQS at-least-once delivery)

> Nguồn: [Amazon SQS at-least-once delivery](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/standard-queues-at-least-once-delivery.html)

Amazon SQS lưu trữ các bản sao message của bạn trên nhiều máy chủ để đảm bảo tính dự phòng (redundancy) và tính sẵn sàng cao (high availability). Trong những trường hợp hiếm hoi, một trong các máy chủ lưu trữ bản sao của một message có thể không khả dụng (unavailable) khi bạn nhận hoặc xóa message.

Nếu điều này xảy ra, bản sao của message không bị xóa trên máy chủ không khả dụng đó, và bạn có thể nhận lại bản sao message đó khi nhận message. Hãy thiết kế ứng dụng của bạn có tính *idempotent* (ứng dụng không nên bị ảnh hưởng tiêu cực khi xử lý cùng một message nhiều hơn một lần).

# Tự động hóa thông báo từ các AWS service tới Amazon SQS bằng Amazon EventBridge (Automating notifications from AWS services to Amazon SQS using Amazon EventBridge)

> Nguồn: [Automating notifications from AWS services to Amazon SQS using Amazon EventBridge](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-automating-using-eventbridge.html)

Amazon EventBridge cho phép bạn tự động hóa các AWS service và phản hồi các sự kiện (event), chẳng hạn như sự cố ứng dụng hoặc thay đổi tài nguyên, gần như theo thời gian thực (near real-time).

- Bạn có thể tạo các rule để lọc các event cụ thể và định nghĩa các hành động tự động khi một rule khớp với một event.
- EventBridge hỗ trợ nhiều target, bao gồm Amazon SQS standard queue và FIFO queue, nhận event ở định dạng JSON.

Để biết thêm thông tin, xem [Amazon EventBridge targets](https://docs.aws.amazon.com/eventbridge/latest/userguide/eb-targets.html) trong *[Amazon EventBridge User Guide](https://docs.aws.amazon.com/eventbridge/latest/userguide/)*.

# Các tag phân bổ chi phí của Amazon SQS (Amazon SQS cost allocation tags)

> Nguồn: [Amazon SQS cost allocation tags](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-queue-tags.html)

Để tổ chức và nhận diện các Amazon SQS queue của bạn cho mục đích phân bổ chi phí (cost allocation), bạn có thể thêm các *tag* metadata để nhận diện mục đích, chủ sở hữu (owner) hoặc môi trường (environment) của một queue. Điều này đặc biệt hữu ích khi bạn có nhiều queue. Để cấu hình tag bằng Amazon SQS console, xem [Configuring cost allocation tags for a queue using the Amazon SQS console](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-configure-tag-queue.html)

[![AWS Videos](https://img.youtube.com/vi/cQhMtYX6HRI/0.jpg)](https://www.youtube.com/watch?v=cQhMtYX6HRI)

Bạn có thể sử dụng cost allocation tag để tổ chức hóa đơn AWS (AWS bill) của mình sao cho phản ánh cấu trúc chi phí của riêng bạn. Để làm điều này, hãy đăng ký để hóa đơn AWS account của bạn bao gồm các tag key và value. Để biết thêm thông tin, xem [Setting Up a Monthly Cost Allocation Report](https://docs.aws.amazon.com/awsaccountbilling/latest/aboutv2/configurecostallocreport.html#allocation-report) trong *AWS Billing User Guide*.

Mỗi tag bao gồm một cặp key-value do bạn định nghĩa. Ví dụ, bạn có thể dễ dàng nhận diện các queue *production* và *testing* của mình nếu bạn gắn tag cho các queue như sau:

| Queue | Key | Value |
| --- | --- | --- |
| MyQueueA | QueueType | Production |
| MyQueueB | QueueType | Testing |

> **Lưu ý (Note)**
>
> Khi bạn sử dụng queue tag, hãy ghi nhớ các hướng dẫn sau:
>
> - Chúng tôi không khuyến nghị thêm quá 50 tag cho một queue. Việc gắn tag hỗ trợ các ký tự Unicode trong UTF-8.
> - Tag không mang bất kỳ ý nghĩa ngữ nghĩa (semantic meaning) nào. Amazon SQS diễn giải tag như các chuỗi ký tự (character string).
> - Tag phân biệt chữ hoa chữ thường (case-sensitive).
> - Một tag mới có key giống hệt key của một tag hiện có sẽ ghi đè (overwrite) tag hiện có đó.
> - Các action gắn tag bị giới hạn ở 30 TPS cho mỗi AWS account. Nếu ứng dụng của bạn yêu cầu thông lượng cao hơn, hãy [gửi yêu cầu](https://console.aws.amazon.com/servicequotas/home/services/sqs/quotas).
>
> Để xem danh sách đầy đủ các giới hạn về tag, xem [Amazon SQS standard queue quotas](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/quotas-queues.html).

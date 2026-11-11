# Các thuật ngữ chính của Amazon SQS FIFO queue (Amazon SQS FIFO queue key terms)

> Nguồn: [Amazon SQS FIFO queue key terms](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/FIFO-key-terms.html)

Các thuật ngữ chính sau đây có thể giúp bạn hiểu rõ hơn về chức năng của FIFO queue. Để biết thêm thông tin, xem *[Amazon Simple Queue Service API Reference](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/APIReference/)*.

**Client (Clients)**

Amazon SQS Buffered Asynchronous Client hiện chưa hỗ trợ FIFO queue.

**Message deduplication ID**

Một token được sử dụng trong Amazon SQS FIFO queue để định danh duy nhất các message và ngăn chặn trùng lặp. Nếu nhiều message có cùng deduplication ID được gửi trong một khoảng thời gian khử trùng lặp (deduplication interval) 5 phút, chúng được coi là trùng lặp, và chỉ một bản sao được phân phối. Nếu bạn không chỉ định deduplication ID và content-based deduplication được bật, Amazon SQS tạo một deduplication ID bằng cách băm (hash) nội dung message (message body). Cơ chế này đảm bảo phân phối đúng một lần (exactly-once delivery) bằng cách loại bỏ các message trùng lặp trong khung thời gian được chỉ định.

Amazon SQS tiếp tục theo dõi deduplication ID ngay cả sau khi message đã được nhận và xóa.

**Message group ID**

Trong FIFO (First-In-First-Out) queue, `MessageGroupId` là một attribute tổ chức các message thành các nhóm riêng biệt. Các message trong cùng một message group luôn được xử lý từng cái một, theo thứ tự nghiêm ngặt, đảm bảo rằng không có hai message nào từ cùng một group được xử lý đồng thời. Trong standard queue, việc sử dụng `MessageGroupId` cho phép sử dụng [fair queue](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-fair-queues.html). Nếu cần thứ tự nghiêm ngặt, hãy sử dụng FIFO queue.

**Receive request attempt ID**

Receive request attempt ID là một token duy nhất được sử dụng để khử trùng lặp (deduplicate) các lời gọi [`ReceiveMessage`](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/APIReference/API_ReceiveMessage.html) trong Amazon SQS.

**Sequence number**

Một số lớn, không liên tiếp (non-consecutive) mà Amazon SQS gán cho mỗi message.

**Dịch vụ (Services)**

Nếu ứng dụng của bạn sử dụng nhiều AWS service, hoặc kết hợp giữa AWS service và các service bên ngoài, điều quan trọng là phải hiểu chức năng nào của service không hỗ trợ FIFO queue.

Một số AWS service hoặc service bên ngoài gửi thông báo (notification) tới Amazon SQS có thể không tương thích với FIFO queue, mặc dù chúng cho phép bạn đặt một FIFO queue làm target.

Các tính năng sau của AWS service hiện chưa tương thích với FIFO queue:

- [Amazon S3 Event Notifications](https://docs.aws.amazon.com/AmazonS3/latest/userguide/NotificationHowTo.html)
- [Auto Scaling Lifecycle Hooks](https://docs.aws.amazon.com/autoscaling/ec2/userguide/lifecycle-hooks.html)
- [AWS IoT Rule Actions](https://docs.aws.amazon.com/iot/latest/developerguide/iot-rule-actions.html)
- [AWS Lambda Dead-Letter Queues](https://docs.aws.amazon.com/lambda/latest/dg/invocation-async.html#invocation-dlq)

Để biết thông tin về khả năng tương thích của các service khác với FIFO queue, xem tài liệu của service đó.

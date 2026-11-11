# Sử dụng dead-letter queue trong Amazon SQS (Using dead-letter queues in Amazon SQS)

> Nguồn: [Using dead-letter queues in Amazon SQS](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-dead-letter-queues.html)

Amazon SQS hỗ trợ dead-letter queue (DLQ), là nơi mà các source queue có thể nhắm tới (target) cho các message không được xử lý thành công. DLQ hữu ích cho việc debug ứng dụng của bạn vì bạn có thể cô lập các message chưa được tiêu thụ (unconsumed) để xác định lý do tại sao việc xử lý không thành công. Dead-letter queue phải nằm trong cùng AWS account và Region với source queue. Khi các message đã nằm trong dead-letter queue, bạn có thể:

- Kiểm tra log để tìm các exception có thể đã khiến message bị chuyển vào dead-letter queue.
- Phân tích nội dung của các message bị chuyển vào dead-letter queue để chẩn đoán các vấn đề của ứng dụng.
- Xác định xem bạn đã cho consumer đủ thời gian để xử lý message hay chưa.
- Chuyển message ra khỏi dead-letter queue bằng [dead-letter queue redrive](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-configure-dead-letter-queue.html).

Bạn phải tạo một queue mới trước khi cấu hình nó làm dead-letter queue. Để biết thông tin về việc cấu hình dead-letter queue bằng Amazon SQS console, xem [Configure a dead-letter queue using the Amazon SQS console](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-configure-dead-letter-queue.html). Để được trợ giúp về dead-letter queue, chẳng hạn như cách cấu hình một alarm cho bất kỳ message nào bị chuyển vào dead-letter queue, xem [Creating alarms for dead-letter queues using Amazon CloudWatch](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/dead-letter-queues-alarms-cloudwatch.html).

> **Lưu ý (Note)**
>
> Không sử dụng dead-letter queue với FIFO queue nếu bạn không muốn phá vỡ thứ tự chính xác của các message hoặc thao tác. Ví dụ, không sử dụng dead-letter queue với các chỉ dẫn trong một Edit Decision List (EDL) cho một bộ phần mềm chỉnh sửa video (video editing suite), nơi mà việc thay đổi thứ tự các chỉnh sửa sẽ làm thay đổi ngữ cảnh của các chỉnh sửa tiếp theo.

## Sử dụng policy cho dead-letter queue (Using policies for dead-letter queues)

Sử dụng một **redrive policy** để chỉ định `maxReceiveCount`. `maxReceiveCount` là số lần một consumer có thể nhận một message từ source queue trước khi nó bị chuyển vào dead-letter queue. Ví dụ, nếu `maxReceiveCount` được đặt ở giá trị thấp như 1, chỉ một lần nhận message thất bại cũng sẽ khiến message bị chuyển vào dead-letter queue. Để đảm bảo hệ thống của bạn có khả năng chống chịu lỗi (resilient), hãy đặt `maxReceiveCount` đủ cao để cho phép đủ số lần retry.

Đối với standard queue có redrive policy với `maxReceiveCount` lớn hơn 3, nếu một message được nhận 3 lần trở lên mà không bị xóa, SQS sẽ chuyển nó về cuối queue. Khi đó metric `ApproximateAgeOfOldestMessage` phản ánh tuổi (age) của message tiếp theo chưa vượt quá ngưỡng này.

**Redrive allow policy** chỉ định những source queue nào có thể truy cập dead-letter queue. Bạn có thể chọn cho phép tất cả source queue, cho phép các source queue cụ thể, hoặc từ chối tất cả source queue sử dụng dead-letter queue. Mặc định cho phép tất cả source queue sử dụng dead-letter queue. Nếu bạn chọn cho phép các queue cụ thể bằng tùy chọn `byQueue`, bạn có thể chỉ định tối đa 10 source queue bằng Amazon Resource Name (ARN) của source queue. Nếu bạn chỉ định `denyAll`, queue không thể được sử dụng làm dead-letter queue.

## Hiểu về thời gian lưu giữ message của dead-letter queue (Understanding message retention periods for dead-letter queues)

Đối với standard queue, việc hết hạn (expiration) của một message luôn dựa trên timestamp đưa vào queue ban đầu (original enqueue timestamp) của nó. Khi một message bị chuyển vào dead-letter queue, enqueue timestamp không thay đổi. Metric `ApproximateAgeOfOldestMessage` cho biết thời điểm message bị chuyển vào dead-letter queue, không phải thời điểm message được gửi ban đầu. Ví dụ, giả sử một message nằm 1 ngày trong queue gốc trước khi bị chuyển vào dead-letter queue. Nếu thời gian lưu giữ (retention period) của dead-letter queue là 4 ngày, message sẽ bị xóa khỏi dead-letter queue sau 3 ngày và `ApproximateAgeOfOldestMessage` là 3 ngày. Do đó, best practice là luôn đặt thời gian lưu giữ của dead-letter queue dài hơn thời gian lưu giữ của queue gốc.

Đối với FIFO queue, enqueue timestamp được đặt lại (reset) khi message bị chuyển vào dead-letter queue. Metric `ApproximateAgeOfOldestMessage` cho biết thời điểm message bị chuyển vào dead-letter queue. Trong cùng ví dụ trên, message sẽ bị xóa khỏi dead-letter queue sau bốn ngày và `ApproximateAgeOfOldestMessage` là bốn ngày.

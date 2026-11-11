# Tài nguyên cần thiết để xử lý Amazon SQS message (Resources required to process Amazon SQS messages)

> Nguồn: [Resources required to process Amazon SQS messages](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-resources-required-process-messages.html)

Amazon SQS cung cấp các ước tính về số lượng xấp xỉ các message bị trì hoãn (delayed), hiển thị (visible) và không hiển thị (not visible) trong một queue để giúp bạn đánh giá tài nguyên cần thiết cho việc xử lý. Để biết thêm thông tin về visibility, xem [Amazon SQS visibility timeout](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-visibility-timeout.html).

> **Lưu ý (Note)**
>
> Đối với một số metric, kết quả là xấp xỉ do kiến trúc phân tán của Amazon SQS. Trong hầu hết các trường hợp, số đếm sẽ gần với số lượng message thực tế trong queue.

Bảng sau liệt kê tên attribute cần sử dụng với action [`GetQueueAttributes`](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/APIReference/API_GetQueueAttributes.html):

| Tác vụ (Task) | Tên attribute (Attribute name) |
| --- | --- |
| Lấy số lượng xấp xỉ các message sẵn sàng để truy xuất từ queue. | ApproximateNumberOfMessagesVisible |
| Lấy số lượng xấp xỉ các message trong queue đang bị trì hoãn (delayed) và chưa sẵn sàng để đọc ngay lập tức. Điều này có thể xảy ra khi queue được cấu hình là delay queue hoặc khi một message được gửi kèm tham số delay. | ApproximateNumberOfMessagesDelayed |
| Lấy số lượng xấp xỉ các message đang in flight. Message được coi là in flight nếu chúng đã được gửi tới một client nhưng chưa bị xóa hoặc chưa đến cuối khoảng thời gian visibility (visibility window) của chúng. | ApproximateNumberOfMessagesNotVisible |

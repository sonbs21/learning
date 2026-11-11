# Delay queue trong Amazon SQS (Amazon SQS delay queues)

> Nguồn: [Amazon SQS delay queues](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-delay-queues.html)

Delay queue cho phép bạn trì hoãn (postpone) việc phân phối các message mới tới consumer trong một số giây, ví dụ, khi ứng dụng consumer của bạn cần thêm thời gian để xử lý message. Nếu bạn tạo một delay queue, bất kỳ message nào bạn gửi vào queue sẽ vẫn ở trạng thái ẩn (invisible) đối với consumer trong suốt khoảng thời gian trì hoãn (delay period). Độ trễ (delay) mặc định (tối thiểu) cho một queue là 0 giây. Tối đa là 15 phút. Để biết thông tin về việc cấu hình delay queue bằng console, xem [Configuring queue parameters using the Amazon SQS console](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-configure-queue-parameters.html).

> **Lưu ý (Note)**
>
> Đối với standard queue, thiết lập delay theo từng queue (per-queue delay) **không có hiệu lực hồi tố (not retroactive)** — việc thay đổi thiết lập không ảnh hưởng đến độ trễ của các message đã có trong queue.
>
> Đối với FIFO queue, thiết lập delay theo từng queue **có hiệu lực hồi tố (retroactive)** — việc thay đổi thiết lập ảnh hưởng đến độ trễ của các message đã có trong queue.

Delay queue tương tự như [visibility timeout](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-visibility-timeout.html) vì cả hai tính năng đều khiến message không khả dụng (unavailable) đối với consumer trong một khoảng thời gian cụ thể. Sự khác biệt giữa hai tính năng là, với delay queue, một message bị ẩn khi nó được thêm vào queue lần đầu tiên, trong khi với visibility timeout, một message chỉ bị ẩn sau khi nó được tiêu thụ (consume) từ queue. Sơ đồ sau minh họa mối quan hệ giữa delay queue và visibility timeout.

![Minh họa mối quan hệ giữa delay queue và visibility timeout.](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/images/sqs-delay-queues-diagram.png)

**Các tùy chọn lập lịch mở rộng (Extended scheduling options)**

Mặc dù Amazon SQS delay queue và message timer cho phép lập lịch phân phối message tối đa 15 phút trong tương lai, bạn có thể cần các khả năng lập lịch linh hoạt hơn. Trong những trường hợp như vậy, hãy cân nhắc sử dụng [EventBridge Scheduler](https://docs.aws.amazon.com/scheduler/latest/UserGuide/what-is-scheduler.html), cho phép bạn lập lịch hàng tỷ API action một lần (one-time) hoặc định kỳ (recurring) mà không có giới hạn về thời gian. EventBridge Scheduler là giải pháp được khuyến nghị cho các use case lập lịch message nâng cao.

Để đặt delay seconds trên từng message riêng lẻ, thay vì trên toàn bộ queue, hãy sử dụng [message timer](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-message-timers.html) để cho phép Amazon SQS sử dụng giá trị `DelaySeconds` của message timer thay vì giá trị `DelaySeconds` của delay queue. [EventBridge Scheduler](https://docs.aws.amazon.com/scheduler/latest/UserGuide/what-is-scheduler.html) cũng hỗ trợ lập lịch cho từng message riêng lẻ.

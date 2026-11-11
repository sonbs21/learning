# Message timer trong Amazon SQS (Amazon SQS message timers)

> Nguồn: [Amazon SQS message timers](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-message-timers.html)

Message timer cho phép bạn đặt một khoảng thời gian ẩn ban đầu (initial invisibility period) cho một message khi nó được thêm vào queue. Ví dụ, nếu bạn gửi một message với timer 45 giây, nó sẽ bị ẩn khỏi các consumer trong 45 giây đầu tiên. Độ trễ (delay) mặc định (tối thiểu) cho một message là 0 giây. Tối đa là 15 phút. Để biết thông tin về việc gửi message kèm timer bằng console, xem [Sending a message using a standard queue](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/creating-sqs-standard-queues.html#sqs-send-messages).

> **Lưu ý (Note)**
>
> FIFO queue không hỗ trợ timer trên từng message riêng lẻ.

Để đặt một khoảng thời gian trì hoãn (delay period) trên toàn bộ queue, thay vì trên từng message riêng lẻ, hãy sử dụng [delay queue](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-delay-queues.html). Thiết lập message timer cho một message riêng lẻ sẽ ghi đè (override) bất kỳ giá trị `DelaySeconds` nào trên một Amazon SQS delay queue.

**Các tùy chọn lập lịch mở rộng (Extended scheduling options)**

Mặc dù Amazon SQS delay queue và message timer cho phép lập lịch phân phối message tối đa 15 phút trong tương lai, bạn có thể cần các khả năng lập lịch linh hoạt hơn. Trong những trường hợp như vậy, hãy cân nhắc sử dụng [EventBridge Scheduler](https://docs.aws.amazon.com/scheduler/latest/UserGuide/what-is-scheduler.html), cho phép bạn lập lịch hàng tỷ API action một lần (one-time) hoặc định kỳ (recurring) mà không có giới hạn về thời gian. EventBridge Scheduler là giải pháp được khuyến nghị cho các use case lập lịch message nâng cao.

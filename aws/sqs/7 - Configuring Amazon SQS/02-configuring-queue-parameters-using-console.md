# Cấu hình các tham số queue bằng Amazon SQS console (Configuring queue parameters using the Amazon SQS console)

> Nguồn: [Configuring queue parameters using the Amazon SQS console](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-configure-queue-parameters.html)

Khi [tạo](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/creating-sqs-standard-queues.html#step-create-standard-queue) hoặc [chỉnh sửa](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-configure-edit-queue.html) một queue, bạn có thể cấu hình các tham số sau:

- **Visibility timeout** – Khoảng thời gian mà một message được nhận từ queue (bởi một consumer) sẽ không hiển thị (visible) với các message consumer khác. Để biết thêm thông tin, xem [Visibility timeout](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-visibility-timeout.html).

  > **Lưu ý (Note)**
  >
  > Việc sử dụng console để cấu hình visibility timeout sẽ cấu hình giá trị timeout cho tất cả các message trong queue. Để cấu hình timeout cho một hoặc nhiều message riêng lẻ, bạn phải sử dụng một trong các AWS SDK.

- **Message retention period** – Khoảng thời gian mà Amazon SQS lưu giữ các message vẫn còn nằm trong queue. Theo mặc định, queue lưu giữ message trong bốn ngày. Bạn có thể cấu hình một queue để lưu giữ message tối đa 14 ngày. Để biết thêm thông tin, xem [Message retention period](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/APIReference/API_SetQueueAttributes.html).
- **Delivery delay** – Khoảng thời gian mà Amazon SQS sẽ trì hoãn trước khi phân phối một message được thêm vào queue. Để biết thêm thông tin, xem [Delivery delay](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-delay-queues.html).
- **Maximum message size** – Kích thước message tối đa cho queue này. Để biết thêm thông tin, xem [Maximum message size](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-s3-messages.html).
- **Receive message wait time** – Khoảng thời gian tối đa mà Amazon SQS chờ message trở nên sẵn có sau khi queue nhận được một receive request. Để biết thêm thông tin, xem [Amazon SQS short and long polling](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-short-and-long-polling.html).
- **Enable content-based deduplication** – Amazon SQS có thể tự động tạo deduplication ID dựa trên nội dung (body) của message. Để biết thêm thông tin, xem [Amazon SQS FIFO queues](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-fifo-queues.html).
- **Enable high throughput FIFO** – Sử dụng để bật thông lượng cao cho các message trong queue. Việc chọn tùy chọn này sẽ thay đổi các tùy chọn liên quan ([Deduplication scope](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/enable-high-throughput-fifo.html) và [FIFO throughput limit](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/enable-high-throughput-fifo.html)) thành các thiết lập bắt buộc để bật thông lượng cao cho FIFO queue. Để biết thêm thông tin, xem [High throughput for FIFO queues in Amazon SQS](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/high-throughput-fifo.html) và [Amazon SQS message quotas](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/quotas-messages.html).
- **Redrive allow policy**: định nghĩa những source queue nào có thể sử dụng queue này làm dead-letter queue. Để biết thêm thông tin, xem [Using dead-letter queues in Amazon SQS](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-dead-letter-queues.html).

**Để cấu hình các tham số queue cho một queue hiện có (console)**

1. Mở Amazon SQS console tại [https://console.aws.amazon.com/sqs/](https://console.aws.amazon.com/sqs/).
2. Trong khung điều hướng (navigation pane), chọn **Queues**. Chọn một queue và chọn **Edit**.
3. Cuộn tới phần **Configuration**.
4. Với **Visibility timeout**, nhập khoảng thời gian và đơn vị. Phạm vi từ 0 giây đến 12 giờ. Giá trị mặc định là 30 giây.
5. Với **Message retention period**, nhập khoảng thời gian và đơn vị. Phạm vi từ 1 phút đến 14 ngày. Giá trị mặc định là 4 ngày.
6. Đối với standard queue, nhập một giá trị cho **Receive message wait time**. Phạm vi từ 0 đến 20 giây. Giá trị mặc định là 0 giây, tương ứng với [short polling](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-short-and-long-polling.html). Bất kỳ giá trị khác 0 nào sẽ đặt long polling.
7. Với **Delivery delay**, nhập khoảng thời gian và đơn vị. Phạm vi từ 0 giây đến 15 phút. Giá trị mặc định là 0 giây.
8. Với **Maximum message size**, nhập một giá trị. Phạm vi từ 1 KiB đến 1024 KiB. Giá trị mặc định là 1024 KiB.
9. Đối với FIFO queue, chọn **Enable content-based deduplication** để bật content-based deduplication. Thiết lập mặc định là tắt (disabled).
10. (Tùy chọn) Để một FIFO queue có thông lượng cao hơn cho việc gửi và nhận message trong queue, chọn **Enable high throughput FIFO**.

    Việc chọn tùy chọn này sẽ thay đổi các tùy chọn liên quan (**Deduplication scope** và **FIFO throughput limit**) thành các thiết lập bắt buộc để bật thông lượng cao cho FIFO queue. Nếu bạn thay đổi bất kỳ thiết lập nào bắt buộc để sử dụng high throughput FIFO, thông lượng thông thường (normal throughput) sẽ có hiệu lực cho queue, và việc khử trùng lặp diễn ra như đã chỉ định. Để biết thêm thông tin, xem [High throughput for FIFO queues in Amazon SQS](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/high-throughput-fifo.html) và [Amazon SQS message quotas](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/quotas-messages.html).
11. Với **Redrive allow policy**, chọn **Enabled**. Chọn một trong các tùy chọn sau: **Allow all** (mặc định), **By queue** hoặc **Deny all**. Khi chọn **By queue**, chỉ định một danh sách tối đa 10 source queue theo Amazon Resource Name (ARN).
12. Khi bạn hoàn tất cấu hình các tham số queue, chọn **Save**.

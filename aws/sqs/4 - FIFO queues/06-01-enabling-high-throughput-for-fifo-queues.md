# Bật thông lượng cao cho FIFO queue trong Amazon SQS (Enabling high throughput for FIFO queues in Amazon SQS)

> Nguồn: [Enabling high throughput for FIFO queues in Amazon SQS](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/enable-high-throughput-fifo.html)

Bạn có thể bật thông lượng cao (high throughput) cho bất kỳ FIFO queue mới hoặc hiện có nào. Tính năng này bao gồm ba tùy chọn mới khi bạn tạo và chỉnh sửa FIFO queue:

- **Enable high throughput FIFO** – Cung cấp thông lượng cao hơn cho các message trong FIFO queue hiện tại.
- **Deduplication scope** – Chỉ định việc khử trùng lặp (deduplication) diễn ra ở cấp queue hay cấp message group.
- **FIFO throughput limit** – Chỉ định quota thông lượng đối với các message trong FIFO queue được đặt ở cấp queue hay cấp message group.

**Để bật thông lượng cao cho một FIFO queue (console)**

1. Bắt đầu [tạo](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/creating-sqs-standard-queues.html#step-create-standard-queue) hoặc [chỉnh sửa](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-configure-edit-queue.html) một FIFO queue.
2. Khi chỉ định các tùy chọn cho queue, chọn **Enable high throughput FIFO**.

   Việc bật thông lượng cao cho FIFO queue sẽ đặt các tùy chọn liên quan như sau:
   - **Deduplication scope** được đặt thành **Message group**, đây là thiết lập bắt buộc để sử dụng thông lượng cao cho FIFO queue.
   - **FIFO throughput limit** được đặt thành **Per message group ID**, đây là thiết lập bắt buộc để sử dụng thông lượng cao cho FIFO queue.

   Nếu bạn thay đổi bất kỳ thiết lập nào bắt buộc để sử dụng thông lượng cao cho FIFO queue, thông lượng thông thường (normal throughput) sẽ có hiệu lực cho queue, và việc khử trùng lặp diễn ra như đã chỉ định.
3. Tiếp tục chỉ định tất cả các tùy chọn cho queue. Khi hoàn tất, chọn **Create queue** hoặc **Save**.

Sau khi tạo hoặc chỉnh sửa FIFO queue, bạn có thể [gửi message](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/creating-sqs-standard-queues.html#sqs-send-messages) tới nó và [nhận và xóa message](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/step-receive-delete-message.html), tất cả đều ở mức TPS cao hơn. Để biết các quota của chế độ high throughput, xem phần Message throughput trong [Amazon SQS message quotas](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/quotas-messages.html).

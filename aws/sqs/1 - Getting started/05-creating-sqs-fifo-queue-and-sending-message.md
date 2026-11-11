# Tạo một Amazon SQS FIFO queue và gửi message (Creating an Amazon SQS FIFO queue and sending a message)

> Nguồn: [Creating an Amazon SQS FIFO queue and sending a message](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/creating-sqs-fifo-queues.html)

Bạn có thể tạo một Amazon SQS FIFO queue và gửi message bằng console. Chủ đề này giải thích cách thiết lập các tham số của queue, bao gồm visibility timeout, message retention và deduplication (khử trùng lặp), đồng thời tuân theo các best practice bảo mật như tránh đưa thông tin nhạy cảm vào tên queue và bật mã hóa phía máy chủ (server-side encryption). Chủ đề này cũng đề cập đến việc định nghĩa access policy, cấu hình dead-letter queue và gửi message với các thuộc tính dành riêng cho FIFO như message group ID và deduplication ID.

## Tạo một FIFO queue bằng Amazon SQS console

Bạn có thể sử dụng Amazon SQS console để tạo [FIFO queue](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-fifo-queues.html). Console cung cấp giá trị mặc định cho tất cả các thiết lập, ngoại trừ tên queue.

> **Quan trọng (Important)**
>
> Vào ngày 17 tháng 8 năm 2022, mã hóa phía máy chủ (server-side encryption – SSE) mặc định đã được áp dụng cho tất cả các Amazon SQS queue.
>
> Không thêm thông tin nhận dạng cá nhân (personally identifiable information – PII) hoặc các thông tin bí mật hay nhạy cảm khác vào tên queue. Tên queue có thể được truy cập bởi nhiều Amazon Web Services, bao gồm billing và CloudWatch logs. Tên queue không được thiết kế để dùng cho dữ liệu riêng tư hoặc nhạy cảm.

**Để tạo một Amazon SQS FIFO queue**

1. Mở Amazon SQS console tại [https://console.aws.amazon.com/sqs/](https://console.aws.amazon.com/sqs/).
2. Chọn **Create queue**.
3. Với **Type**, loại queue **Standard** được đặt theo mặc định. Để tạo một FIFO queue, chọn **FIFO**.

   > **Lưu ý (Note)**
   >
   > Bạn không thể thay đổi loại queue sau khi đã tạo queue.

4. Nhập **Name** cho queue của bạn.

   Tên của một FIFO queue phải kết thúc bằng hậu tố (suffix) `.fifo`. Hậu tố này được tính vào quota 80 ký tự của tên queue. Để xác định một queue có phải là [FIFO](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-fifo-queues.html) hay không, bạn có thể kiểm tra xem tên queue có kết thúc bằng hậu tố này hay không.
5. (Tùy chọn) Console đặt các giá trị mặc định cho các [tham số cấu hình (configuration parameters)](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-configure-queue-parameters.html) của queue. Trong phần **Configuration**, bạn có thể đặt giá trị mới cho các tham số sau:
   1. Với **Visibility timeout**, nhập khoảng thời gian và đơn vị. Phạm vi từ 0 giây đến 12 giờ. Giá trị mặc định là 30 giây.
   2. Với **Message retention period**, nhập khoảng thời gian và đơn vị. Phạm vi từ 1 phút đến 14 ngày. Giá trị mặc định là 4 ngày.
   3. Với **Delivery delay**, nhập khoảng thời gian và đơn vị. Phạm vi từ 0 giây đến 15 phút. Giá trị mặc định là 0 giây.
   4. Với **Maximum message size**, nhập một giá trị. Phạm vi từ 1 KiB đến 1024 KiB. Giá trị mặc định là 1024 KiB.
   5. Với **Receive message wait time**, nhập một giá trị. Phạm vi từ 0 đến 20 giây. Giá trị mặc định là 0 giây, tương ứng với [short polling](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-short-and-long-polling.html). Bất kỳ giá trị khác 0 nào sẽ đặt long polling.
   6. Đối với FIFO queue, chọn **Content-based deduplication** để bật khử trùng lặp dựa trên nội dung (content-based deduplication). Thiết lập mặc định là tắt (disabled).
   7. (Tùy chọn) Để một FIFO queue có thông lượng cao hơn cho việc gửi và nhận message trong queue, chọn **Enable high throughput FIFO**.

      Việc chọn tùy chọn này sẽ thay đổi các tùy chọn liên quan (**Deduplication scope** và **FIFO throughput limit**) thành các thiết lập bắt buộc để bật thông lượng cao cho FIFO queue. Nếu bạn thay đổi bất kỳ thiết lập nào bắt buộc để sử dụng high throughput FIFO, thông lượng thông thường (normal throughput) sẽ có hiệu lực cho queue, và việc khử trùng lặp diễn ra như đã chỉ định. Để biết thêm thông tin, xem [High throughput for FIFO queues in Amazon SQS](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/high-throughput-fifo.html) và [Amazon SQS message quotas](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/quotas-messages.html).
6. (Tùy chọn) Định nghĩa một **Access policy**. [Access policy](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-creating-custom-policies-access-policy-examples.html) xác định các account, user và role có thể truy cập queue. Access policy cũng xác định các action (chẳng hạn như [`SendMessage`](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/APIReference/API_SendMessage.html), [`ReceiveMessage`](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/APIReference/API_ReceiveMessage.html), hoặc [`DeleteMessage`](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/APIReference/API_DeleteMessage.html)) mà các user có thể truy cập. Policy mặc định chỉ cho phép chủ sở hữu queue (queue owner) gửi và nhận message.

   Để định nghĩa access policy, thực hiện một trong các cách sau:
   - Chọn **Basic** để cấu hình ai có thể gửi message vào queue và ai có thể nhận message từ queue. Console tạo policy dựa trên các lựa chọn của bạn và hiển thị access policy kết quả trong panel JSON chỉ đọc (read-only).
   - Chọn **Advanced** để chỉnh sửa trực tiếp JSON access policy. Điều này cho phép bạn chỉ định một tập action tùy chỉnh mà mỗi principal (account, user hoặc role) có thể thực hiện.
7. Với **Redrive allow policy**, chọn **Enabled**. Chọn một trong các tùy chọn sau: **Allow all**, **By queue**, hoặc **Deny all**. Khi chọn **By queue**, chỉ định một danh sách tối đa 10 source queue theo Amazon Resource Name (ARN).
8. Amazon SQS cung cấp managed server-side encryption theo mặc định. Để chọn loại khóa mã hóa (encryption key type), hoặc để tắt Amazon SQS managed server-side encryption, mở rộng **Encryption**. Để biết thêm về các loại khóa mã hóa, xem [Configuring server-side encryption for a queue using SQS-managed encryption keys](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-configure-sqs-sse-queue.html) và [Configuring server-side encryption for a queue using the Amazon SQS console](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-configure-sse-existing-queue.html).

   > **Lưu ý (Note)**
   >
   > Khi SSE được bật, các request `SendMessage` và `ReceiveMessage` ẩn danh (anonymous) gửi tới queue được mã hóa sẽ bị từ chối. Các best practice bảo mật của Amazon SQS khuyến nghị không sử dụng request ẩn danh. Nếu bạn muốn gửi request ẩn danh tới một Amazon SQS queue, hãy đảm bảo tắt SSE.

9. (Tùy chọn) Để cấu hình một [dead-letter queue](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-configure-dead-letter-queue.html) nhận các message không thể phân phối (undeliverable), mở rộng **Dead-letter queue**.
10. (Tùy chọn) Để thêm [tag](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-configure-tag-queue.html) cho queue, mở rộng **Tags**.
11. Chọn **Create queue**. Amazon SQS tạo queue và hiển thị trang **Details** của queue.

Amazon SQS lan truyền (propagate) thông tin về queue mới trên toàn hệ thống. Vì Amazon SQS là một hệ thống phân tán, bạn có thể gặp một độ trễ nhỏ trước khi console hiển thị queue trên trang **Queues**.

Sau khi tạo queue, bạn có thể [gửi message](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/creating-sqs-standard-queues.html#sqs-send-messages) tới nó, và [nhận và xóa message](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/step-receive-delete-message.html). Bạn cũng có thể [chỉnh sửa](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-configure-edit-queue.html) bất kỳ thiết lập cấu hình nào của queue, ngoại trừ loại queue.

## Gửi message bằng FIFO queue

Sau khi bạn tạo queue, bạn có thể gửi message tới nó.

1. Từ khung điều hướng bên trái (navigation pane), chọn **Queues**. Từ danh sách queue, chọn queue mà bạn đã tạo.
2. Từ **Actions**, chọn **Send and receive messages**.

   Console hiển thị trang **Send and receive messages**.
3. Trong **Message body**, nhập nội dung message.
4. Đối với một First-In-First-Out (FIFO) queue, nhập **Message group ID**. Để biết thêm thông tin, xem [FIFO queue delivery logic in Amazon SQS](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/FIFO-queues-understanding-logic.html).
5. (Tùy chọn) Đối với FIFO queue, bạn có thể nhập **Message deduplication ID**. Nếu bạn đã bật content-based deduplication cho queue, message deduplication ID là không bắt buộc. Để biết thêm thông tin, xem [FIFO queue delivery logic in Amazon SQS](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/FIFO-queues-understanding-logic.html).
6. FIFO queue không hỗ trợ timer trên từng message riêng lẻ. Để biết thêm thông tin, xem [Amazon SQS message timers](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-message-timers.html).
7. Chọn **Send message**.

   Khi message của bạn được gửi, console hiển thị một thông báo thành công. Chọn **View details** để hiển thị thông tin về message đã gửi.

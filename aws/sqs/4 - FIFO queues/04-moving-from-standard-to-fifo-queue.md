# Chuyển từ standard queue sang FIFO queue trong Amazon SQS (Moving from a standard queue to a FIFO queue in Amazon SQS)

> Nguồn: [Moving from a standard queue to a FIFO queue in Amazon SQS](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/FIFO-queues-moving.html)

Nếu ứng dụng hiện tại của bạn sử dụng standard queue và bạn muốn tận dụng các tính năng sắp xếp thứ tự (ordering) hoặc xử lý đúng một lần (exactly-once processing) của FIFO queue, bạn cần cấu hình đúng cả queue lẫn ứng dụng của mình.

**Các lưu ý chính (Key considerations)**

- **Tạo FIFO queue (Creating a FIFO Queue):** Bạn không thể chuyển đổi (convert) một standard queue hiện có thành FIFO queue. Bạn phải hoặc tạo một FIFO queue mới cho ứng dụng của mình, hoặc xóa standard queue hiện có và tạo lại nó dưới dạng FIFO queue.
- **Tham số delay (Delay Parameter):** FIFO queue không hỗ trợ delay theo từng message (per-message delay), chỉ hỗ trợ delay theo từng queue (per-queue delay). Nếu ứng dụng của bạn đặt tham số `DelaySeconds` trên mỗi message, bạn phải sửa đổi để thay vào đó đặt `DelaySeconds` trên toàn bộ queue.
- **Message Group ID:** Cung cấp một [message group ID](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/high-throughput-fifo.html#partitions-and-data-distribution) cho mọi message được gửi. ID này cho phép xử lý song song các message trong khi vẫn duy trì thứ tự tương ứng của chúng. Hãy sử dụng một chiều nghiệp vụ (business dimension) chi tiết (granular) cho message group ID để mở rộng tốt hơn với FIFO queue. Bạn phân phối message vào càng nhiều message group ID, số lượng message sẵn sàng để tiêu thụ (consume) càng lớn.
- **Chế độ thông lượng cao (High Throughput Mode):** Sử dụng [chế độ high throughput](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/high-throughput-fifo.html#partitions-and-data-distribution) được khuyến nghị cho FIFO queue để đạt được thông lượng cao hơn. Để biết thêm thông tin về các quota nhắn tin, xem [Amazon SQS message quotas](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/quotas-messages.html).

**Checklist cho việc chuyển sang FIFO queue (Checklist for moving to FIFO queues)**

Trước khi gửi message tới một FIFO queue, hãy xác nhận những điều sau:

1. **Cấu hình các thiết lập delay (Configure delay settings)**
   - Sửa đổi ứng dụng của bạn để loại bỏ các delay theo từng message.
   - Đặt tham số `DelaySeconds` trên toàn bộ queue.
2. **Đặt message group ID (Set message group IDs)**
   - Tổ chức các message thành các message group bằng cách chỉ định một message group ID dựa trên một chiều nghiệp vụ (business dimension).
   - Sử dụng các chiều nghiệp vụ chi tiết hơn để cải thiện khả năng mở rộng (scalability).
3. **Xử lý việc khử trùng lặp message (Handle message deduplication)**
   - Nếu ứng dụng của bạn không thể gửi các message có nội dung (message body) giống hệt nhau, hãy cung cấp một message deduplication ID duy nhất cho mỗi message.
   - Nếu ứng dụng của bạn gửi các message có nội dung (message body) duy nhất, hãy bật content-based deduplication.
4. **Cấu hình consumer (Configure the consumer)**
   - Nhìn chung, không cần thay đổi code cho consumer.
   - Nếu việc xử lý message mất nhiều thời gian và visibility timeout được đặt cao, hãy cân nhắc thêm một receive request attempt ID vào mỗi action `ReceiveMessage`. Điều này giúp retry các lần nhận trong trường hợp lỗi mạng và ngăn queue bị tạm dừng (pause) do các lần nhận thất bại.

Bằng cách làm theo các bước này, bạn có thể đảm bảo ứng dụng của mình hoạt động đúng với FIFO queue, tận dụng tối đa các tính năng sắp xếp thứ tự và xử lý đúng một lần của chúng. Để biết thông tin chi tiết hơn, xem *[Amazon Simple Queue Service API Reference](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/APIReference/)*.

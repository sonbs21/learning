# Amazon SQS FIFO queue (Amazon SQS FIFO queues)

> Nguồn: [Amazon SQS FIFO queues](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-fifo-queues.html)

FIFO (First-In-First-Out – vào trước ra trước) queue có tất cả các khả năng của [standard queue](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/standard-queues.html), nhưng được thiết kế để tăng cường việc nhắn tin giữa các ứng dụng khi thứ tự của các thao tác và sự kiện là yếu tố then chốt, hoặc khi không thể chấp nhận việc trùng lặp.

Các tính năng quan trọng nhất của FIFO queue là [*phân phối FIFO (First-In-First-Out delivery)*](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/FIFO-queues-understanding-logic.html) và *[xử lý đúng một lần (exactly-once processing)](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/FIFO-queues-exactly-once-processing.html)*:

- Thứ tự mà các message được gửi và nhận được bảo toàn một cách nghiêm ngặt, và một message được phân phối một lần và vẫn không khả dụng (unavailable) cho đến khi một consumer xử lý và xóa nó.
- Các bản trùng lặp không được đưa vào queue.

Ngoài ra, FIFO queue hỗ trợ *message group*, cho phép có nhiều nhóm message có thứ tự bên trong một queue duy nhất. Không có quota giới hạn số lượng message group bên trong một FIFO queue.

Các ví dụ về những tình huống mà bạn có thể sử dụng FIFO queue bao gồm:

1. Hệ thống quản lý đơn hàng thương mại điện tử (e-commerce) nơi thứ tự là yếu tố then chốt
2. Tích hợp với các hệ thống bên thứ ba (third-party) nơi các sự kiện cần được xử lý theo thứ tự
3. Xử lý các input do người dùng nhập theo đúng thứ tự đã nhập
4. Truyền thông và mạng (Communications and networking) – Gửi và nhận dữ liệu và thông tin theo cùng một thứ tự
5. Hệ thống máy tính (Computer systems) – Đảm bảo các lệnh do người dùng nhập được thực thi theo đúng thứ tự
6. Các cơ sở giáo dục (Educational institutes) – Ngăn một sinh viên đăng ký khóa học trước khi đăng ký tài khoản
7. Hệ thống bán vé trực tuyến (Online ticketing system) – Nơi vé được phân phối theo nguyên tắc ai đến trước được phục vụ trước (first come first serve)

> **Lưu ý (Note)**
>
> FIFO queue cũng cung cấp xử lý đúng một lần (exactly-once processing), nhưng có số lượng giao dịch mỗi giây (transactions per second – TPS) bị giới hạn. Bạn có thể sử dụng chế độ **high throughput** của Amazon SQS với FIFO queue của mình để tăng giới hạn giao dịch. Để biết chi tiết về việc sử dụng chế độ high throughput, xem [High throughput for FIFO queues in Amazon SQS](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/high-throughput-fifo.html). Để biết thông tin về các quota thông lượng, xem [Amazon SQS message quotas](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/quotas-messages.html).

Amazon SQS FIFO queue khả dụng ở tất cả các Region nơi Amazon SQS khả dụng.

Để biết thêm về việc sử dụng FIFO queue với các yêu cầu sắp xếp thứ tự phức tạp, xem [Solving Complex Ordering Challenges with Amazon SQS FIFO Queues](https://aws.amazon.com/blogs/compute/solving-complex-ordering-challenges-with-amazon-sqs-fifo-queues/).

Để biết thông tin về cách tạo và cấu hình queue bằng Amazon SQS console, xem [Creating a standard queue using the Amazon SQS console](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/creating-sqs-standard-queues.html#step-create-standard-queue). Để xem các ví dụ Java, xem [Amazon SQS Java SDK examples](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-java-tutorials.html).

Để biết các best practice khi làm việc với FIFO queue, xem [Amazon SQS best practices](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-best-practices.html).

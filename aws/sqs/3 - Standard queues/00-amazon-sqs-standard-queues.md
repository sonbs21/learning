# Amazon SQS standard queue (Amazon SQS standard queues)

> Nguồn: [Amazon SQS standard queues](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/standard-queues.html)

Amazon SQS cung cấp standard queue là loại queue mặc định, hỗ trợ một số lượng API call mỗi giây gần như không giới hạn cho các action như [`SendMessage`](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/APIReference/API_SendMessage.html), [`ReceiveMessage`](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/APIReference/API_ReceiveMessage.html) và [`DeleteMessage`](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/APIReference/API_DeleteMessage.html). Standard queue đảm bảo phân phối message ít nhất một lần (at-least-once delivery), nhưng do kiến trúc phân tán cao, có thể có nhiều hơn một bản sao của một message được phân phối, và message đôi khi có thể đến không theo thứ tự. Mặc dù vậy, standard queue vẫn nỗ lực theo khả năng tốt nhất (best-effort) để duy trì thứ tự mà các message được gửi.

Khi bạn gửi một message bằng `SendMessage`, Amazon SQS lưu trữ message một cách dự phòng (redundantly) trên nhiều availability zone (AZ) trước khi xác nhận (acknowledge) nó. Sự dự phòng này đảm bảo rằng không có sự cố đơn lẻ nào của một máy tính, mạng hoặc AZ có thể khiến các message không thể truy cập được.

Bạn có thể tạo và cấu hình queue bằng Amazon SQS console. Để xem hướng dẫn chi tiết, xem [Creating a standard queue using the Amazon SQS console](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/creating-sqs-standard-queues.html#step-create-standard-queue). Để xem các ví dụ dành riêng cho Java, xem [Amazon SQS Java SDK examples](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-java-tutorials.html).

**Các use case cho standard queue**

Standard message queue phù hợp với nhiều kịch bản khác nhau, miễn là ứng dụng của bạn có thể xử lý các message có thể đến nhiều hơn một lần hoặc không theo thứ tự. Các ví dụ bao gồm:

- **Tách rời (decouple) các request trực tiếp của người dùng khỏi các công việc nền nặng** – Người dùng có thể upload media trong khi hệ thống thay đổi kích thước (resize) hoặc mã hóa (encode) nó ở chế độ nền.
- **Phân bổ tác vụ cho nhiều worker node** – Ví dụ, xử lý một khối lượng lớn các request xác thực thẻ tín dụng.
- **Gom nhóm (batch) message để xử lý sau** – Lên lịch để nhiều bản ghi được thêm vào database vào một thời điểm sau.

Để biết thông tin về các quota liên quan đến standard queue, xem [Amazon SQS standard queue quotas](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/quotas-queues.html).

Để biết các best practice khi làm việc với standard queue, xem [Amazon SQS best practices](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-best-practices.html).

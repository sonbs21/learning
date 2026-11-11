# Truy cập Amazon EventBridge Pipes thông qua Amazon SQS console (Accessing Amazon EventBridge Pipes through the Amazon SQS console)

> Nguồn: [Accessing Amazon EventBridge Pipes through the Amazon SQS console](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/eb-pipes-integration.html)

Amazon EventBridge Pipes kết nối các nguồn (source) tới các đích (target). Pipe được thiết kế cho các tích hợp điểm-tới-điểm (point-to-point) giữa các source và target được hỗ trợ, với hỗ trợ cho các phép biến đổi (transformation) và làm giàu dữ liệu (enrichment) nâng cao. EventBridge Pipes cung cấp một cách có khả năng mở rộng cao để kết nối Amazon SQS queue của bạn tới các AWS service như Step Functions, Amazon SQS và API Gateway, cũng như các ứng dụng phần mềm dạng dịch vụ (software as a service – SaaS) của bên thứ ba như Salesforce.

Để thiết lập một pipe, bạn chọn source, thêm bộ lọc (filtering) tùy chọn, định nghĩa enrichment tùy chọn, và chọn target cho dữ liệu sự kiện (event data).

Trên trang chi tiết (details page) của một Amazon SQS queue, bạn có thể xem các pipe sử dụng queue đó làm source của chúng. Từ đó, bạn cũng có thể:

- Mở EventBridge console để xem chi tiết pipe.
- Mở EventBridge console để tạo một pipe mới với queue làm source của nó.

Để biết thêm thông tin về việc cấu hình một Amazon SQS queue làm source của pipe, xem [Amazon SQS queue as a source](https://docs.aws.amazon.com/eventbridge/latest/userguide/eb-pipes-sqs.html) trong *Amazon EventBridge User Guide*. Để biết thêm thông tin chung về EventBridge Pipes, xem [EventBridge Pipes](https://docs.aws.amazon.com/eventbridge/latest/userguide/eb-pipes.html).

**Để truy cập EventBridge pipe cho một Amazon SQS queue cụ thể**

1. Mở [trang Queues](https://console.aws.amazon.com/sqs/#/queues) của Amazon SQS console.
2. Chọn một queue.
3. Trên trang chi tiết queue, chọn tab **EventBridge Pipes**.

   Tab **EventBridge Pipes** bao gồm danh sách bất kỳ pipe nào hiện đang được cấu hình để sử dụng queue đã chọn làm source, bao gồm:
   - tên pipe
   - trạng thái hiện tại
   - target của pipe
   - thời điểm pipe được chỉnh sửa lần cuối
4. Xem thêm chi tiết pipe hoặc tạo một pipe mới, nếu muốn:
   - **Để truy cập thêm chi tiết về một pipe:**

     Chọn tên pipe.

     Thao tác này mở trang **Pipe details** của EventBridge console.
   - **Để tạo một pipe mới:**

     Chọn **Connect Amazon SQS queue to pipe**.

     Thao tác này mở trang **Create pipe** của EventBridge console, với Amazon SQS queue được chỉ định làm source của pipe. Để biết thêm thông tin, xem [Creating an EventBridge pipe](https://docs.aws.amazon.com/eventbridge/latest/userguide/eb-pipes-create.html) trong *Amazon EventBridge User Guide*.

> **Quan trọng (Important)**
>
> Một message trên Amazon SQS queue được đọc bởi một pipe duy nhất và sau đó bị xóa khỏi queue sau khi được xử lý, *bất kể message đó có khớp với bộ lọc (filter) mà bạn đã cấu hình cho pipe đó hay không*. Hãy thận trọng khi cấu hình nhiều pipe sử dụng cùng một queue làm source.

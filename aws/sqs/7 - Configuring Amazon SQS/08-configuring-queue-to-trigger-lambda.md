# Cấu hình một Amazon SQS queue để kích hoạt AWS Lambda function (Configuring an Amazon SQS queue to trigger an AWS Lambda function)

> Nguồn: [Configuring an Amazon SQS queue to trigger an AWS Lambda function](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-configure-lambda-function-trigger.html)

Bạn có thể sử dụng một Lambda function để xử lý message từ một Amazon SQS queue. Lambda poll queue và gọi (invoke) function của bạn một cách đồng bộ (synchronously), truyền một batch message dưới dạng một event.

**Cấu hình visibility timeout (Configuring visibility timeout)**

Đặt visibility timeout của queue ít nhất gấp sáu lần [function timeout](https://docs.aws.amazon.com/lambda/latest/dg/configuration-function-common.html#configuration-common-summary). Điều này đảm bảo Lambda có đủ thời gian để retry nếu một function bị điều tiết (throttled) trong khi đang xử lý một batch trước đó.

**Sử dụng dead-letter queue (DLQ) (Using a dead-letter queue (DLQ))**

Chỉ định một dead-letter queue để thu nhận các message mà Lambda function xử lý thất bại.

**Xử lý nhiều queue và nhiều function (Handling multiple queues and functions)**

Một Lambda function có thể xử lý nhiều queue bằng cách tạo một event source riêng cho mỗi queue. Bạn cũng có thể liên kết (associate) nhiều Lambda function với cùng một queue.

**Permission cho các queue được mã hóa (Permissions for encrypted queues)**

Nếu bạn liên kết một queue được mã hóa với một Lambda function nhưng Lambda không poll message, hãy thêm permission `kms:Decrypt` vào Lambda execution role của bạn.

**Các giới hạn (Restrictions)**

Queue và Lambda function phải nằm trong cùng một AWS Region.

Một [queue được mã hóa](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-server-side-encryption.html) sử dụng key mặc định (AWS managed KMS key cho Amazon SQS) không thể gọi (invoke) một Lambda function ở một AWS account khác.

Để biết chi tiết triển khai, xem [Using AWS Lambda with Amazon SQS](https://docs.aws.amazon.com/lambda/latest/dg/with-sqs.html) trong *AWS Lambda Developer Guide*.

## Điều kiện tiên quyết (Prerequisites)

Để cấu hình Lambda function trigger, bạn phải đáp ứng các yêu cầu sau:

- Nếu bạn sử dụng một user, Amazon SQS role của bạn phải bao gồm các permission sau:
  - `lambda:CreateEventSourceMapping`
  - `lambda:ListEventSourceMappings`
  - `lambda:ListFunctions`
- Lambda execution role phải bao gồm các permission sau:
  - `sqs:DeleteMessage`
  - `sqs:GetQueueAttributes`
  - `sqs:ReceiveMessage`
- Nếu bạn liên kết một queue được mã hóa với một Lambda function, hãy thêm permission `kms:Decrypt` vào Lambda execution role.

Để biết thêm thông tin, xem [Overview of managing access in Amazon SQS](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-overview-of-managing-access.html).

**Để cấu hình một queue kích hoạt một Lambda function (console)**

1. Mở Amazon SQS console tại [https://console.aws.amazon.com/sqs/](https://console.aws.amazon.com/sqs/).
2. Trong khung điều hướng (navigation pane), chọn **Queues**.
3. Trên trang **Queues**, chọn queue cần cấu hình.
4. Trên trang của queue, chọn tab **Lambda triggers**.
5. Trên trang **Lambda triggers**, chọn một Lambda trigger.

   Nếu danh sách không có Lambda trigger mà bạn cần, chọn **Configure Lambda function trigger**. Nhập Amazon Resource Name (ARN) của Lambda function hoặc chọn một resource hiện có. Sau đó chọn **Save**.
6. Chọn **Save**. Console lưu cấu hình và hiển thị trang **Details** của queue.

   Trên trang **Details**, tab **Lambda triggers** hiển thị Lambda function và trạng thái của nó. Mất khoảng 1 phút để Lambda function được liên kết với queue của bạn.
7. Để xác minh kết quả cấu hình, hãy [gửi một message tới queue của bạn](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/creating-sqs-standard-queues.html#sqs-send-messages) rồi xem Lambda function được kích hoạt trong Lambda console.

# Đăng ký (subscribe) một queue vào Amazon SNS topic bằng Amazon SQS console (Subscribing a queue to an Amazon SNS topic using the Amazon SQS console)

> Nguồn: [Subscribing a queue to an Amazon SNS topic using the Amazon SQS console](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-configure-subscribe-queue-sns-topic.html)

Bạn có thể đăng ký (subscribe) một hoặc nhiều Amazon SQS queue vào một Amazon SNS topic. Khi bạn publish một message tới một topic, Amazon SNS sẽ gửi message đó tới mỗi queue đã đăng ký. Amazon SQS quản lý subscription và xử lý các permission cần thiết. Để biết thêm thông tin về Amazon SNS, xem [What is Amazon SNS?](https://docs.aws.amazon.com/sns/latest/dg/welcome.html) trong *Amazon Simple Notification Service Developer Guide*.

Khi bạn đăng ký một Amazon SQS queue vào một Amazon SNS topic, Amazon SNS sử dụng HTTPS để chuyển tiếp (forward) message tới Amazon SQS. Để biết thông tin về việc sử dụng Amazon SNS với các Amazon SQS queue được mã hóa, xem [Configure KMS permissions for AWS services](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-key-management.html#compatibility-with-aws-services).

> **Quan trọng (Important)**
>
> Amazon SQS hỗ trợ tối đa 20 statement cho mỗi access policy. Việc đăng ký vào một Amazon SNS topic sẽ thêm một statement như vậy. Vượt quá số lượng này sẽ dẫn đến việc phân phối của topic subscription bị thất bại.

**Để đăng ký một queue vào một Amazon SNS topic (console)**

1. Mở Amazon SQS console tại [https://console.aws.amazon.com/sqs/](https://console.aws.amazon.com/sqs/).
2. Trong khung điều hướng (navigation pane), chọn **Queues**.
3. Từ danh sách queue, chọn queue cần đăng ký vào Amazon SNS topic.
4. Từ **Actions**, chọn **Subscribe to Amazon SNS topic**.
5. Từ menu **Specify an Amazon SNS topic available for this queue**, chọn Amazon SNS topic cho queue của bạn.

   Nếu SNS topic không có trong danh sách, chọn **Enter Amazon SNS topic ARN** rồi nhập Amazon Resource Name (ARN) của topic.
6. Chọn **Save**.
7. Để xác minh subscription, hãy publish một message tới topic và xem message đó trong queue. Để biết thêm thông tin, xem [Amazon SNS message publishing](https://docs.aws.amazon.com/sns/latest/dg/sns-publishing.html) trong *Amazon Simple Notification Service Developer Guide*.

## Đăng ký giữa các account (Cross-account subscriptions)

Nếu Amazon SQS queue và Amazon SNS topic của bạn nằm ở các AWS account khác nhau, cần có thêm các permission bổ sung.

**Chủ sở hữu topic (Topic owner) (Account A)**

Sửa đổi access policy của Amazon SNS topic để cho phép AWS account của Amazon SQS queue được đăng ký. Ví dụ policy statement:

```json
{
  "Effect": "Allow",
  "Principal": { "AWS": "arn:aws:iam::111122223333:root" },
  "Action": "sns:Subscribe",
  "Resource": "arn:aws:sns:us-east-1:123456789012:MyTopic"
}
```

Policy này cho phép account `111122223333` đăng ký vào `MyTopic`.

**Chủ sở hữu queue (Queue owner) (Account B)**

Sửa đổi access policy của Amazon SQS queue để cho phép Amazon SNS topic gửi message. Ví dụ policy statement:

```json
{
  "Effect": "Allow",
  "Principal": { "Service": "sns.amazonaws.com" },
  "Action": "sqs:SendMessage",
  "Resource": "arn:aws:sqs:us-east-1:111122223333:MyQueue",
  "Condition": {
    "ArnEquals": { "aws:SourceArn": "arn:aws:sns:us-east-1:123456789012:MyTopic" }
  }
}
```

Policy này cho phép `MyTopic` gửi message tới `MyQueue`.

## Đăng ký giữa các region (Cross-region subscriptions)

Để đăng ký vào một Amazon SNS topic ở một AWS Region khác, hãy đảm bảo rằng:

- Access policy của Amazon SNS topic cho phép đăng ký giữa các region (cross-region subscription).
- Access policy của Amazon SQS queue cho phép Amazon SNS topic gửi message giữa các region.

Để biết thêm thông tin, [Sending Amazon SNS messages to an Amazon SQS queue or AWS Lambda function in a different Region](https://docs.aws.amazon.com/sns/latest/dg/sns-cross-region-delivery.html) trong *Amazon Simple Notification Service Developer Guide*.

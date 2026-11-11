# Cập nhật CloudTrail và yêu cầu về permission cho Amazon SQS dead-letter queue redrive (CloudTrail update and permission requirements for Amazon SQS dead-letter queue redrive)

> Nguồn: [CloudTrail update and permission requirements for Amazon SQS dead-letter queue redrive](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-dead-letter-queues-cloudtrail.html)

Vào ngày 8 tháng 6 năm 2023, Amazon SQS đã giới thiệu tính năng dead-letter queue (DLQ) redrive cho AWS SDK và AWS Command Line Interface (CLI). Khả năng này là phần bổ sung cho tính năng DLQ redrive vốn đã được hỗ trợ trên AWS console. Nếu trước đây bạn đã sử dụng AWS console để redrive các message của dead-letter queue, bạn có thể bị ảnh hưởng bởi các thay đổi sau:

## Đổi tên event của CloudTrail (CloudTrail event renaming)

Vào ngày 15 tháng 10 năm 2023, tên các CloudTrail event cho dead-letter queue redrive sẽ thay đổi trên Amazon SQS console. Nếu bạn đã đặt alarm cho các CloudTrail event này, bạn phải cập nhật chúng ngay bây giờ. Sau đây là các tên CloudTrail event mới cho DLQ redrive:

| Tên event trước đây (Previous event name) | Tên event mới (New event name) |
| --- | --- |
| `CreateMoveTask` | `StartMessageMoveTask` |
| `CancelMoveTask` | `CancelMessageMoveTask` |

## Các permission được cập nhật (Updated permissions)

Cùng với bản phát hành SDK và CLI, Amazon SQS cũng đã cập nhật các permission của queue cho DLQ redrive để tuân thủ các best practice bảo mật. Sử dụng các loại permission của queue sau để redrive message từ DLQ của bạn.

1. Permission dựa trên action (Action-based permissions) (cập nhật cho các DLQ API action)
2. Permission của Amazon SQS managed policy (Managed Amazon SQS policy permissions)
3. Permission policy sử dụng wildcard `sqs:*`

> **Quan trọng (Important)**
>
> Để sử dụng DLQ redrive cho SDK hoặc CLI, bạn bắt buộc phải có một permission policy cho DLQ redrive khớp với một trong các tùy chọn ở trên.

Nếu các permission của queue cho DLQ redrive của bạn không khớp với một trong các tùy chọn ở trên, bạn phải cập nhật permission của mình trước ngày 31 tháng 8 năm 2023. Từ bây giờ cho đến ngày 31 tháng 8 năm 2023, account của bạn sẽ có thể redrive message bằng các permission bạn đã cấu hình qua AWS console chỉ ở các region mà trước đây bạn đã sử dụng DLQ redrive. Ví dụ, giả sử bạn có "Account A" ở cả us-east-1 và eu-west-1. "Account A" đã được dùng để redrive message trên AWS console ở us-east-1 trước ngày 8 tháng 6 năm 2023, nhưng không phải ở eu-west-1. Trong khoảng thời gian từ ngày 8 tháng 6 năm 2023 đến ngày 31 tháng 8 năm 2023, nếu các policy permission của "Account A" không khớp với một trong các tùy chọn ở trên, nó chỉ có thể được dùng để redrive message trên AWS console ở us-east-1, và không thể ở eu-west-1.

> **Quan trọng (Important)**
>
> Nếu các permission DLQ redrive của bạn không khớp với một trong các tùy chọn này sau ngày 31 tháng 8 năm 2023, account của bạn sẽ không còn có thể redrive các message của DLQ bằng AWS console nữa.
>
> Tuy nhiên, nếu bạn đã sử dụng tính năng DLQ redrive trên AWS Console trong tháng 8 năm 2023, bạn được gia hạn đến ngày 15 tháng 10 năm 2023 để áp dụng các permission mới theo một trong các tùy chọn này.
>
> Để biết thêm thông tin, xem [Identifying impacted policies](#xác-định-các-policy-bị-ảnh-hưởng-identifying-impacted-policies).

Sau đây là các ví dụ về permission của queue cho từng tùy chọn DLQ redrive. Khi sử dụng [các queue được mã hóa phía máy chủ (SSE)](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-configure-sse-existing-queue.html), cần có permission tương ứng đối với AWS KMS key.

**Dựa trên action (Action-based)**

```json
{
    "Version":"2012-10-17",
    "Statement": [
        {
            "Effect": "Allow",
            "Action": [
                "sqs:ReceiveMessage",
                "sqs:DeleteMessage",
                "sqs:GetQueueAttributes",
                "sqs:StartMessageMoveTask",
                "sqs:ListMessageMoveTasks",
                "sqs:CancelMessageMoveTask"
            ],
            "Resource": "arn:aws:sqs:us-west-1:123456789012:<DLQ_name>"
        },
        {
            "Effect": "Allow",
            "Action": "sqs:SendMessage",
            "Resource": "arn:aws:sqs:us-west-1:123456789012:<DestQueue_name>"
        }
    ]
}
```

**Managed policy**

Các managed policy sau chứa các permission đã được cập nhật cần thiết:

- **AmazonSQSFullAccess** – Bao gồm các tác vụ dead-letter queue redrive sau: start, cancel và list.
- **AmazonSQSReadOnlyAccess** – Cung cấp quyền truy cập chỉ đọc (read-only), và bao gồm tác vụ list dead-letter queue redrive.

![Amazon SQS hiển thị permission policy AmazonSQSFullAccess để start, cancel và list các tác vụ dead-letter queue redrive, và AmazonSQSReadOnlyAccess cho quyền truy cập chỉ đọc.](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/images/sqs-dlq-cloudtrail.png)

**Permission Policy sử dụng wildcard sqs\***

```json
{
  "Version":"2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": "sqs:*",
      "Resource": "*"
    }
  ]
}
```

## Xác định các policy bị ảnh hưởng (Identifying impacted policies)

Nếu bạn đang sử dụng customer managed policy (CMP), bạn có thể sử dụng AWS CloudTrail và IAM để xác định các policy bị ảnh hưởng bởi việc cập nhật permission của queue.

> **Lưu ý (Note)**
>
> Nếu bạn đang sử dụng `AmazonSQSFullAccess` và `AmazonSQSReadOnlyAccess`, không cần thực hiện thêm hành động nào.

1. Đăng nhập vào AWS CloudTrail console.
2. Trên trang **Event history**, trong **Look up attributes**, sử dụng menu dropdown để chọn *Event name*. Sau đó, tìm kiếm `CreateMoveTask`.
3. Chọn một event để mở trang **Details**. Trong phần **Event records**, lấy `UserName` hoặc `RoleName` từ ARN của `userIdentity`.
4. Đăng nhập vào IAM console.
   - Đối với user, chọn Users. Chọn user có `UserName` đã xác định ở bước trước.
   - Đối với role, chọn Roles. Tìm kiếm user có `RoleName` đã xác định ở bước trước.
5. Trên trang **Details**, trong phần **Permissions**, xem xét bất kỳ policy nào có tiền tố `sqs:` trong `Action`, hoặc xem xét các policy có Amazon SQS queue được định nghĩa trong `Resource`.

# Tìm hiểu cách cấu hình dead-letter queue redrive trong Amazon SQS (Learn how to configure a dead-letter queue redrive in Amazon SQS)

> Nguồn: [Learn how to configure a dead-letter queue redrive in Amazon SQS](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-configure-dead-letter-queue-redrive.html)

Sử dụng dead-letter queue redrive để chuyển các message chưa được tiêu thụ (unconsumed) từ một dead-letter queue tới một đích (destination) khác để xử lý. Theo mặc định, dead-letter queue redrive chuyển message từ dead-letter queue về source queue. Tuy nhiên, bạn cũng có thể cấu hình bất kỳ queue nào khác làm đích redrive nếu cả hai queue cùng loại. Ví dụ, nếu dead-letter queue là FIFO queue, thì queue đích của redrive cũng phải là FIFO queue. Ngoài ra, bạn có thể cấu hình tốc độ redrive (redrive velocity) để đặt tốc độ mà Amazon SQS chuyển message.

> **Lưu ý (Note)**
>
> Khi một message được chuyển từ một FIFO queue sang một FIFO DLQ, deduplication ID của message gốc sẽ được thay thế bằng ID của message gốc. Điều này nhằm đảm bảo việc khử trùng lặp (deduplication) của DLQ sẽ không ngăn cản việc lưu trữ hai message độc lập tình cờ có cùng deduplication ID.

Dead-letter queue redrive các message theo thứ tự mà chúng được nhận, bắt đầu từ message cũ nhất. Tuy nhiên, queue đích tiếp nhận (ingest) các message được redrive, cũng như các message mới từ các producer khác, theo thứ tự mà nó nhận được chúng. Ví dụ, nếu một producer đang gửi message tới một source FIFO queue trong khi queue đó đồng thời nhận các message được redrive từ một dead-letter queue, các message được redrive sẽ xen kẽ với các message mới từ producer.

> **Lưu ý (Note)**
>
> Tác vụ redrive đặt lại (reset) thời gian lưu giữ (retention period). Tất cả các message được redrive đều được coi là message mới, với `messageID` và `enqueueTime` mới được gán cho các message được redrive.

## Cấu hình dead-letter queue redrive cho một standard queue hiện có bằng Amazon SQS API (Configuring a dead-letter queue redrive for an existing standard queue using the Amazon SQS API)

Bạn có thể cấu hình dead-letter queue redrive bằng các API action `StartMessageMoveTask`, `ListMessageMoveTasks` và `CancelMessageMoveTask`:

| API action | Mô tả |
| --- | --- |
| [`StartMessageMoveTask`](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/APIReference/API_StartMessageMoveTask.html) | Bắt đầu một tác vụ bất đồng bộ (asynchronous task) để chuyển message từ một source queue được chỉ định tới một destination queue được chỉ định. |
| [`ListMessageMoveTasks`](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/APIReference/API_ListMessageMoveTasks.html) | Lấy các tác vụ di chuyển message gần đây nhất (tối đa 10) của một source queue cụ thể. |
| [`CancelMessageMoveTask`](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/APIReference/API_CancelMessageMoveTask.html) | Hủy một tác vụ di chuyển message được chỉ định. Một tác vụ di chuyển message chỉ có thể bị hủy khi trạng thái hiện tại là RUNNING. |

## Cấu hình dead-letter queue redrive cho một standard queue hiện có bằng Amazon SQS console (Configuring a dead-letter queue redrive for an existing standard queue using the Amazon SQS console)

1. Mở Amazon SQS console tại [https://console.aws.amazon.com/sqs/](https://console.aws.amazon.com/sqs/).
2. Trong khung điều hướng (navigation pane), chọn **Queues**.
3. Chọn tên của queue mà bạn đã cấu hình làm [dead-letter queue](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-configure-dead-letter-queue.html).
4. Chọn **Start DLQ redrive**.
5. Trong **Redrive configuration**, với **Message destination**, thực hiện một trong các cách sau:
   - Để redrive message về source queue của chúng, chọn **Redrive to source queue(s)**.
   - Để redrive message tới một queue khác, chọn **Redrive to custom destination**. Sau đó, nhập Amazon Resource Name (ARN) của một destination queue hiện có.
6. Trong **Velocity control settings**, chọn một trong các tùy chọn sau:
   - **System optimized** - Redrive các message của dead-letter queue với số lượng message mỗi giây tối đa.
   - **Custom max velocity** - Redrive các message của dead-letter queue với tốc độ tối đa tùy chỉnh tính theo message mỗi giây. Tốc độ tối đa được phép là 500 message mỗi giây.
     - Khuyến nghị bắt đầu với một giá trị nhỏ cho Custom max velocity và kiểm tra để đảm bảo source queue không bị quá tải message. Từ đó, tăng dần giá trị Custom max velocity, đồng thời tiếp tục giám sát trạng thái của source queue.
7. Khi bạn hoàn tất cấu hình dead-letter queue redrive, chọn **Redrive messages**.

> **Quan trọng (Important)**
>
> Amazon SQS không hỗ trợ lọc (filter) và chỉnh sửa (modify) message trong khi redrive chúng từ dead-letter queue.
>
> Một tác vụ dead-letter queue redrive có thể chạy tối đa 36 giờ. Amazon SQS hỗ trợ tối đa 100 tác vụ redrive đang hoạt động (active) cho mỗi account.

8. Nếu bạn muốn hủy tác vụ redrive message, trên trang **Details** của queue, chọn **Cancel DLQ redrive**. Khi hủy một tác vụ redrive message đang diễn ra, bất kỳ message nào đã được chuyển thành công tới destination queue sẽ vẫn nằm trong destination queue.

## Cấu hình quyền của queue cho dead-letter queue redrive (Configuring queue permissions for dead-letter queue redrive)

Bạn có thể cấp cho user quyền truy cập vào các action cụ thể của dead-letter queue bằng cách thêm permission vào policy của bạn. Các permission tối thiểu cần thiết cho một dead-letter queue redrive như sau:

| Permission tối thiểu (Minimum Permissions) | Các API method bắt buộc (Required API methods) |
| --- | --- |
| Để bắt đầu một message redrive | - Thêm `sqs:StartMessageMoveTask`, `sqs:ReceiveMessage`, `sqs:DeleteMessage` và `sqs:GetQueueAttributes` của dead-letter queue. Nếu dead-letter queue hoặc source queue ban đầu được mã hóa (còn gọi là queue [SSE](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-server-side-encryption.html)), thì cũng cần `kms:Decrypt` cho bất kỳ KMS key nào đã được dùng để mã hóa message.<br>- Thêm `sqs:SendMessage` của destination queue. Nếu destination queue được mã hóa, thì cũng cần `kms:GenerateDataKey` và `kms:Decrypt`. |
| Để hủy một message redrive đang diễn ra | - Thêm `sqs:CancelMessageMoveTask`, `sqs:ReceiveMessage`, `sqs:DeleteMessage` và `sqs:GetQueueAttributes` của dead-letter queue. Nếu dead-letter queue được mã hóa (còn gọi là queue [SSE](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-server-side-encryption.html)), thì cũng cần `kms:Decrypt`. |
| Để hiển thị trạng thái di chuyển message | - Thêm `sqs:ListMessageMoveTasks` và `sqs:GetQueueAttributes` của dead-letter queue. |

**Để cấu hình permission cho một cặp queue được mã hóa (một source queue với một dead-letter queue)**

Sử dụng các bước sau để cấu hình permission tối thiểu cho một dead-letter queue (DLQ) redrive:

1. Mở IAM console tại [https://console.aws.amazon.com/iam/](https://console.aws.amazon.com/iam/).
2. Trong khung điều hướng, chọn **Policies**.
3. Tạo một [**policy**](https://docs.aws.amazon.com/IAM/latest/UserGuide/access_policies_create.html) mới và thêm các permission sau. Gắn (attach) policy vào IAM [user](https://docs.aws.amazon.com/IAM/latest/UserGuide/id_users.html) hoặc [role](https://docs.aws.amazon.com/IAM/latest/UserGuide/id_roles.html) sẽ thực hiện thao tác redrive.
   - Permission cho DLQ (source queue):
     - `sqs:StartMessageMoveTask`
     - `sqs:CancelMessageMoveTask`
     - `sqs:ListMessageMoveTasks`
     - `sqs:ReceiveMessage`
     - `sqs:DeleteMessage`
     - `sqs:GetQueueAttributes`
     - `sqs:ListDeadLetterSourceQueues`
     - Chỉ định **Resource ARN** của DLQ (source queue) (ví dụ: `"arn:aws:sqs:<DLQ_region>:<DLQ_accountId>:<DLQ_name>"`).
   - Permission cho destination queue:
     - `sqs:SendMessage`
     - Chỉ định `Resource ARN` của destination queue (ví dụ: `"arn:aws:sqs:<DestQueue_region>:<DestQueue_accountId>:<DestQueue_name>"`).
   - Permission cho KMS key:
     - `kms:Decrypt` (Cần thiết để giải mã message trong DLQ.)
     - `kms:GenerateDataKey` (Cần thiết để mã hóa message trong destination queue.)
       - Các `Resource` ARN:
         - ARN của KMS key được dùng để mã hóa message trong **DLQ** (source queue) (ví dụ: `"arn:aws:kms:<region>:<accountId>:key/<SourceQueueKeyId>"`).
         - ARN của KMS key được dùng để mã hóa message trong **destination queue** (ví dụ: `"arn:aws:kms:<region>:<accountId>:key/<DestinationQueueKeyId>"`).

Access policy của bạn sẽ trông giống như sau:

```json
{
    "Version":"2012-10-17",
    "Statement": [
        {
            "Effect": "Allow",
            "Action": [
                "sqs:StartMessageMoveTask",
                "sqs:CancelMessageMoveTask",
                "sqs:ListMessageMoveTasks",
                "sqs:ReceiveMessage",
                "sqs:DeleteMessage",
                "sqs:GetQueueAttributes",
                "sqs:ListDeadLetterSourceQueues"
            ],
            "Resource": "arn:aws:sqs:us-west-1:123456789012:<DLQ_name>",
            "Condition": {
                "StringEquals": {
                    "aws:ResourceTag/QueueRole": "source"
                }
            }
        },
        {
            "Effect": "Allow",
            "Action": "sqs:SendMessage",
            "Resource": "arn:aws:sqs:us-west-1:123456789012:<DestQueue_name>",
            "Condition": {
                "StringEquals": {
                    "aws:ResourceTag/QueueRole": "destination"
                }
            }
        },
        {
            "Effect": "Allow",
            "Action": [
                "kms:Decrypt",
                "kms:GenerateDataKey"
            ],
            "Resource": [
                "arn:aws:kms:us-west-1:123456789012:key/<SourceQueueKeyId>",
                "arn:aws:kms:us-west-1:123456789012:key/<DestQueueKeyId>"
            ]
        }
    ]
}
```

**Để cấu hình permission cho một cặp queue không được mã hóa (một source queue với một dead-letter queue)**

Làm theo các bước sau để cấu hình các permission tối thiểu cần thiết cho việc xử lý một dead-letter queue (DLQ) standard **không được mã hóa**. Các permission tối thiểu bắt buộc là *nhận* (receive), *xóa* (delete) và *lấy* (get) attribute từ dead-letter queue, và *gửi* (send) attribute tới source queue.

1. Mở IAM console tại [https://console.aws.amazon.com/iam/](https://console.aws.amazon.com/iam/).
2. Trong khung điều hướng, chọn **Policies**.
3. Tạo một [**policy**](https://docs.aws.amazon.com/IAM/latest/UserGuide/access_policies_create.html) mới và thêm các permission sau. Gắn (attach) policy vào IAM [user](https://docs.aws.amazon.com/IAM/latest/UserGuide/id_users.html) hoặc [role](https://docs.aws.amazon.com/IAM/latest/UserGuide/id_roles.html) sẽ thực hiện thao tác redrive.
   - Permission cho DLQ (source queue):
     - `sqs:StartMessageMoveTask`
     - `sqs:CancelMessageMoveTask`
     - `sqs:ListMessageMoveTasks`
     - `sqs:ReceiveMessage`
     - `sqs:DeleteMessage`
     - `sqs:ListDeadLetterSourceQueues`
     - Chỉ định **Resource ARN** của DLQ (source queue) (ví dụ: `"arn:aws:sqs:<DLQ_region>:<DLQ_accountId>:<DLQ_name>"`).
   - Permission cho destination queue:
     - `sqs:SendMessage`
     - Chỉ định `Resource ARN` của destination queue (ví dụ: `"arn:aws:sqs:<DestQueue_region>:<DestQueue_accountId>:<DestQueue_name>"`).

   Access policy của bạn sẽ trông giống như sau:

```json
{
    "Version":"2012-10-17",
    "Statement": [
        {
            "Effect": "Allow",
            "Action": [
                "sqs:StartMessageMoveTask",
                "sqs:CancelMessageMoveTask",
                "sqs:ListMessageMoveTasks",
                "sqs:ReceiveMessage",
                "sqs:DeleteMessage",
                "sqs:GetQueueAttributes",
                "sqs:ListDeadLetterSourceQueues"
            ],
            "Resource": "arn:aws:sqs:us-west-1:111122223333:<DLQ_name>",
            "Condition": {
                "StringEquals": {
                    "aws:ResourceTag/QueueRole": "source"
                }
            }
        },
        {
            "Effect": "Allow",
            "Action": "sqs:SendMessage",
            "Resource": "arn:aws:sqs:us-west-1:111122223333:<DestQueue_name>",
            "Condition": {
                "StringEquals": {
                    "aws:ResourceTag/QueueRole": "destination"
                }
            }
        }
    ]
}
```

## Sử dụng dead-letter queue redrive với kiểm soát truy cập qua VPC endpoint (Using dead-letter queue redrive with VPC endpoint access control)

Khi bạn giới hạn quyền truy cập queue chỉ cho các VPC cụ thể bằng condition `aws:sourceVpc`, bạn cần tạo một ngoại lệ cho các AWS service để cho phép chức năng dead-letter queue (DLQ) redrive. Lý do là dịch vụ Amazon SQS hoạt động bên ngoài VPC của bạn khi di chuyển message.

Để cho phép các thao tác DLQ redrive, hãy thêm condition `aws:CalledViaLast` vào queue policy của bạn. Điều này cho phép Amazon SQS thực hiện các API call thay mặt bạn trong khi vẫn duy trì các giới hạn VPC đối với truy cập trực tiếp.

Để cho phép cả truy cập giới hạn theo VPC lẫn DLQ redrive:

1. Sử dụng condition `aws:CalledViaLast` trong queue policy của bạn.
2. Áp dụng policy cho cả source queue và DLQ.
3. Duy trì các giới hạn VPC đối với truy cập trực tiếp từ các nguồn khác.

Đây là một policy ví dụ triển khai các yêu cầu này:

```json
{
  "Version":"2012-10-17",
  "Id": "SQSRedriveWithVpcRestriction",
  "Statement": [
    {
      "Sid": "DenyOutsideVPCUnlessAWSService_DestQueue",
      "Effect": "Deny",
      "Principal": "*",
      "Action": "sqs:*",
      "Resource": "arn:aws:sqs:*:111122223333:DestQueue",
      "Condition": {
        "StringNotEquals": {
          "aws:SourceVpc": "vpc-1234567890abcdef0"
        },
        "StringNotEqualsIfExists": {
          "aws:CalledViaLast": "sqs.amazonaws.com"
        }
      }
    },
    {
      "Sid": "DenyOutsideVPCUnlessAWSService_DLQ",
      "Effect": "Deny",
      "Principal": "*",
      "Action": "sqs:*",
      "Resource": "arn:aws:sqs:*:111122223333:Dlq",
      "Condition": {
        "StringNotEquals": {
          "aws:SourceVpc": "vpc-1234567890abcdef0"
        },
        "StringNotEqualsIfExists": {
          "aws:CalledViaLast": "sqs.amazonaws.com"
        }
      }
    }
  ]
}
```

- Thay thế các giá trị placeholder bằng các giá trị thực tế của bạn
- Policy này sử dụng một statement "deny" kèm condition, an toàn hơn so với việc sử dụng các statement "allow"
- Toán tử `StringNotEqualsIfExists` xử lý các trường hợp mà condition key có thể không có mặt trong request context.

Ngoài ra, bạn có thể sử dụng condition key `aws:ViaAWSService` để cho phép truy cập dựa trên service trong khi vẫn duy trì các giới hạn VPC. Condition key này cho biết request có đến từ một AWS service hay không. Đây là một policy ví dụ sử dụng `aws:ViaAWSService` thay cho `aws:CalledViaLast`:

```json
{
  "Version":"2012-10-17",
  "Id": "SQSRedriveWithVpcRestriction",
  "Statement": [
    {
      "Sid": "DenyOutsideVPCUnlessAWSService_DestQueue",
      "Effect": "Deny",
      "Principal": "*",
      "Action": "sqs:*",
      "Resource": "arn:aws:sqs:*:111122223333:DestQueue",
      "Condition": {
        "StringNotEquals": {
          "aws:SourceVpc": "vpc-1234567890abcdef0"
        },
        "BoolIfExists": {
          "aws:ViaAWSService": "false"
        }
      }
    },
    {
      "Sid": "DenyOutsideVPCUnlessAWSService_DLQ",
      "Effect": "Deny",
      "Principal": "*",
      "Action": "sqs:*",
      "Resource": "arn:aws:sqs:*:111122223333:Dlq",
      "Condition": {
        "StringNotEquals": {
          "aws:SourceVpc": "vpc-1234567890abcdef0"
        },
        "BoolIfExists": {
          "aws:ViaAWSService": "false"
        }
      }
    }
  ]
}
```

Toán tử BoolIfExists với condition `aws:ViaAWSService` đảm bảo rằng các request được cho phép khi chúng đến từ các service, trong khi vẫn duy trì các giới hạn VPC đối với truy cập trực tiếp. Cách này có thể đơn giản hơn để hiểu và duy trì, vì nó kiểm tra trực tiếp xem request có được thực hiện bởi một AWS service hay không, thay vì kiểm tra service nào đã thực hiện lời gọi cuối cùng.

Để biết thêm thông tin về các condition key được sử dụng trong IAM và resource policy, xem IAM JSON policy elements: Condition.

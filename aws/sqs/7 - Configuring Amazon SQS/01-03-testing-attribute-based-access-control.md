# Kiểm thử kiểm soát truy cập dựa trên thuộc tính trong Amazon SQS (Testing attribute-based access control in Amazon SQS)

> Nguồn: [Testing attribute-based access control in Amazon SQS](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-abac-testing-access-control.html)

Các ví dụ sau cho bạn thấy cách kiểm thử (test) kiểm soát truy cập dựa trên thuộc tính (attribute-based access control) trong Amazon SQS.

## Tạo một queue với tag key là environment và tag value là prod (Create a queue with the tag key set to environment and the tag value set to prod)

Chạy lệnh AWS CLI này để kiểm thử việc tạo queue với tag key là environment và tag value là prod. Nếu bạn chưa có AWS CLI, bạn có thể [tải về và cấu hình](https://docs.aws.amazon.com/cli/latest/userguide/getting-started-install.html) nó cho máy của mình.

```
aws sqs create-queue --queue-name prodQueue —region us-east-1 —tags "environment=prod"
```

Bạn nhận được lỗi `AccessDenied` từ Amazon SQS endpoint:

```
An error occurred (AccessDenied) when calling the CreateQueue operation: Access to the resource <queueUrl> is denied.
```

Điều này là do tag value trên IAM user không khớp với tag được truyền trong lời gọi API [`CreateQueue`](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/APIReference/API_CreateQueue.html). Hãy nhớ rằng chúng ta đã gắn một tag vào IAM user với key là `environment` và value là `beta`.

## Tạo một queue với tag key là environment và tag value là beta (Create a queue with the tag key set to environment and the tag value set to beta)

Chạy lệnh CLI này để kiểm thử việc tạo một queue với tag key là `environment` và tag value là `beta`.

```
aws sqs create-queue --queue-name betaQueue —region us-east-1 —tags "environment=beta"
```

Bạn nhận được một thông báo xác nhận việc tạo queue thành công, tương tự như thông báo dưới đây.

```
{
"QueueUrl": "<queueUrl>“
}
```

## Gửi một message tới queue (Sending a message to a queue)

Chạy lệnh CLI này để kiểm thử việc gửi một message tới queue.

```
aws sqs send-message --queue-url <queueUrl> --message-body testMessage
```

Response cho thấy việc phân phối message tới Amazon SQS queue thành công. Permission của IAM user cho phép bạn gửi message tới một queue có tag `beta`. Response bao gồm `MD5OfMessageBody` và `MessageId` chứa message.

```
{
"MD5OfMessageBody": "<MD5OfMessageBody>",
"MessageId": "<MessageId>"
}
```

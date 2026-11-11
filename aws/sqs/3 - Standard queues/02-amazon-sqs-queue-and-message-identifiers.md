# Các định danh của queue và message trong Amazon SQS (Amazon SQS queue and message identifiers)

> Nguồn: [Amazon SQS queue and message identifiers](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-queue-message-identifiers.html)

Chủ đề này mô tả các định danh (identifier) của standard queue và FIFO queue. Các định danh này có thể giúp bạn tìm và thao tác với các queue và message cụ thể.

## Các định danh cho Amazon SQS standard queue

Để biết thêm thông tin về các định danh sau, xem *[Amazon Simple Queue Service API Reference](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/APIReference/)*.

### Tên queue và URL (Queue name and URL)

Khi bạn tạo một queue mới, bạn phải chỉ định một tên queue là duy nhất (unique) đối với AWS account và region của bạn. Amazon SQS gán cho mỗi queue bạn tạo một định danh gọi là *queue URL*, bao gồm tên queue và các thành phần khác của Amazon SQS. Bất cứ khi nào bạn muốn thực hiện một action trên một queue, bạn cung cấp queue URL của nó.

Sau đây là queue URL của một queue có tên `MyQueue` thuộc sở hữu của một user có số AWS account là `123456789012`.

```
https://sqs.us-east-2.amazonaws.com/123456789012/MyQueue
```

Bạn có thể truy xuất URL của một queue bằng lập trình (programmatically) bằng cách liệt kê các queue của bạn và phân tích (parse) chuỗi theo sau số account. Để biết thêm thông tin, xem [`ListQueues`](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/APIReference/API_ListQueues.html).

### Message ID

Mỗi message nhận được một *message ID* do hệ thống gán, mà Amazon SQS trả về cho bạn trong response của [`SendMessage`](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/APIReference/API_SendMessage.html). Định danh này hữu ích cho việc nhận diện message. Độ dài tối đa của một message ID là 100 ký tự.

### Receipt handle

Mỗi lần bạn nhận một message từ queue, bạn nhận được một *receipt handle* cho message đó. Handle này gắn với hành động nhận message, không phải với bản thân message. Để xóa message hoặc để thay đổi visibility của message, bạn phải cung cấp receipt handle (không phải message ID). Do đó, bạn luôn phải nhận một message trước khi có thể xóa nó (bạn không thể đưa một message vào queue rồi sau đó thu hồi nó). Độ dài tối đa của một receipt handle là 1.024 ký tự.

> **Quan trọng (Important)**
>
> Nếu bạn nhận một message nhiều hơn một lần, mỗi lần nhận bạn sẽ có một receipt handle khác nhau. Bạn phải cung cấp receipt handle được nhận gần đây nhất khi bạn yêu cầu xóa message (nếu không, message có thể không bị xóa).

Sau đây là một ví dụ về receipt handle được ngắt thành ba dòng.

```
MbZj6wDWli+JvwwJaBV+3dcjk2YW2vA3+STFFljTM8tJJg6HRG6PYSasuWXPJB+Cw
Lj1FjgXUv1uSj1gUPAWV66FU/WeR4mq2OKpEGYWbnLmpRCJVAyeMjeU5ZBdtcQ+QE
auMZc8ZRv37sIW2iJKq3M9MFx1YvV11A2x/KSbkJ0=
```

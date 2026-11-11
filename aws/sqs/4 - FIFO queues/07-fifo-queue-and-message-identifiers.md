# Các định danh của FIFO queue và message trong Amazon SQS (FIFO queue and message identifiers in Amazon SQS)

> Nguồn: [FIFO queue and message identifiers in Amazon SQS](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-fifo-queue-message-identifiers.html)

Phần này mô tả các định danh (identifier) của FIFO queue. Các định danh này có thể giúp bạn tìm và thao tác với các queue và message cụ thể.

## Các định danh cho FIFO queue trong Amazon SQS (Identifiers for FIFO queues in Amazon SQS)

Để biết thêm thông tin về các định danh sau, xem *[Amazon Simple Queue Service API Reference](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/APIReference/)*.

### Tên queue và URL (Queue name and URL)

Khi bạn tạo một queue mới, bạn phải chỉ định một tên queue là duy nhất (unique) đối với AWS account và region của bạn. Amazon SQS gán cho mỗi queue bạn tạo một định danh gọi là *queue URL*, bao gồm tên queue và các thành phần khác của Amazon SQS. Bất cứ khi nào bạn muốn thực hiện một action trên một queue, bạn cung cấp queue URL của nó.

Tên của một FIFO queue phải kết thúc bằng hậu tố (suffix) `.fifo`. Hậu tố này được tính vào quota 80 ký tự của tên queue. Để xác định một queue có phải là [FIFO](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-fifo-queues.html) hay không, bạn có thể kiểm tra xem tên queue có kết thúc bằng hậu tố này hay không.

Sau đây là queue URL của một FIFO queue có tên `MyQueue` thuộc sở hữu của một user có số AWS account là `123456789012`.

```
https://sqs.us-east-2.amazonaws.com/123456789012/MyQueue.fifo
```

Bạn có thể truy xuất URL của một queue bằng lập trình (programmatically) bằng cách liệt kê các queue của bạn và phân tích (parse) chuỗi theo sau số account. Để biết thêm thông tin, xem [`ListQueues`](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/APIReference/API_ListQueues.html).

### Message ID

Mỗi message nhận được một *message ID* do hệ thống gán, mà Amazon SQS trả về cho bạn trong response của [`SendMessage`](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/APIReference/API_SendMessage.html). Định danh này hữu ích cho việc nhận diện message. Độ dài tối đa của một message ID là 100 ký tự.

### Receipt handle

Mỗi lần bạn nhận một message từ queue, bạn nhận được một *receipt handle* cho message đó. Handle này gắn với hành động nhận message, không phải với bản thân message. Để xóa message hoặc để thay đổi visibility của message, bạn phải cung cấp receipt handle (không phải message ID). Do đó, bạn luôn phải nhận một message trước khi có thể xóa nó (bạn không thể đưa một message vào queue rồi sau đó thu hồi nó). Độ dài tối đa của một receipt handle là 1.024 ký tự.

> **Quan trọng (Important)**
>
> Nếu bạn nhận một message nhiều hơn một lần, mỗi lần nhận bạn sẽ có một receipt handle khác nhau. Bạn phải cung cấp receipt handle được nhận gần đây nhất khi bạn yêu cầu xóa message (nếu không, message có thể không bị xóa).

Sau đây là một ví dụ về receipt handle (được ngắt thành ba dòng).

```
MbZj6wDWli+JvwwJaBV+3dcjk2YW2vA3+STFFljTM8tJJg6HRG6PYSasuWXPJB+Cw
Lj1FjgXUv1uSj1gUPAWV66FU/WeR4mq2OKpEGYWbnLmpRCJVAyeMjeU5ZBdtcQ+QE
auMZc8ZRv37sIW2iJKq3M9MFx1YvV11A2x/KSbkJ0=
```

## Các định danh bổ sung cho Amazon SQS FIFO queue (Additional identifiers for Amazon SQS FIFO queues)

Để biết thêm thông tin về các định danh sau, xem [Exactly-once processing in Amazon SQS](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/FIFO-queues-exactly-once-processing.html) và *[Amazon Simple Queue Service API Reference](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/APIReference/)*.

### Message deduplication ID

Một token được sử dụng trong Amazon SQS FIFO queue để định danh duy nhất các message và ngăn chặn trùng lặp. Nếu nhiều message có cùng deduplication ID được gửi trong một khoảng thời gian khử trùng lặp (deduplication interval) 5 phút, chúng được coi là trùng lặp, và chỉ một bản sao được phân phối. Nếu bạn không chỉ định deduplication ID và content-based deduplication được bật, Amazon SQS tạo một deduplication ID bằng cách băm (hash) nội dung message (message body). Cơ chế này đảm bảo phân phối đúng một lần (exactly-once delivery) bằng cách loại bỏ các message trùng lặp trong khung thời gian được chỉ định.

### Message group ID

`MessageGroupId` là một attribute chỉ được sử dụng trong Amazon SQS FIFO (First-In-First-Out) queue để tổ chức các message thành các nhóm riêng biệt. Các message trong cùng một message group luôn được xử lý từng cái một, theo thứ tự nghiêm ngặt, đảm bảo rằng không có hai message nào từ cùng một group được xử lý đồng thời. Standard queue không sử dụng `MessageGroupId` và không cung cấp đảm bảo về thứ tự. Nếu cần thứ tự nghiêm ngặt, hãy sử dụng FIFO queue thay thế.

### Sequence number

Một số lớn, không liên tiếp (non-consecutive) mà Amazon SQS gán cho mỗi message.

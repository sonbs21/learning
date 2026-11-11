# Short polling và long polling trong Amazon SQS (Amazon SQS short and long polling)

> Nguồn: [Amazon SQS short and long polling](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-short-and-long-polling.html)

Amazon SQS cung cấp các tùy chọn short polling và long polling để nhận message từ một queue. Hãy cân nhắc các yêu cầu của ứng dụng về khả năng phản hồi (responsiveness) và hiệu quả chi phí (cost efficiency) khi lựa chọn giữa hai tùy chọn polling này:

- **Short polling** (mặc định) – Request [`ReceiveMessage`](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/APIReference/API_ReceiveMessage.html) truy vấn một tập con (subset) các máy chủ (dựa trên phân phối ngẫu nhiên có trọng số – weighted random distribution) để tìm các message sẵn có và gửi response ngay lập tức, ngay cả khi không tìm thấy message nào.
- **Long polling** – [`ReceiveMessage`](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/APIReference/API_ReceiveMessage.html) truy vấn tất cả các máy chủ để tìm message, gửi response ngay khi có ít nhất một message sẵn có, tối đa đến số lượng tối đa được chỉ định. Một response rỗng chỉ được gửi khi thời gian chờ polling (polling wait time) hết hạn. Tùy chọn này có thể giảm số lượng response rỗng và có khả năng giảm chi phí.

Các phần sau giải thích chi tiết về short polling và long polling.

## Tiêu thụ message bằng short polling (Consuming messages using short polling)

Khi bạn tiêu thụ (consume) message từ một queue (FIFO hoặc standard) bằng short polling, Amazon SQS lấy mẫu (sample) một tập con các máy chủ của nó (dựa trên phân phối ngẫu nhiên có trọng số) và chỉ trả về message từ những máy chủ đó. Do đó, một request [`ReceiveMessage`](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/APIReference/API_ReceiveMessage.html) cụ thể có thể không trả về tất cả message của bạn. Tuy nhiên, nếu bạn có ít hơn 1.000 message trong queue, một request tiếp theo sẽ trả về các message của bạn. Nếu bạn tiếp tục tiêu thụ từ queue, Amazon SQS sẽ lấy mẫu tất cả các máy chủ của nó, và bạn sẽ nhận được tất cả message của mình.

Sơ đồ sau cho thấy hành vi short polling của các message được trả về từ một standard queue sau khi một trong các thành phần hệ thống của bạn gửi một receive request. Amazon SQS lấy mẫu một số máy chủ của nó (màu xám) và trả về các message A, C, D và B từ những máy chủ này. Message E không được trả về cho request này, nhưng được trả về cho một request tiếp theo.

![Lấy mẫu message bằng short polling (standard)](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/images/ArchOverview_Receive.png)

## Tiêu thụ message bằng long polling (Consuming messages using long polling)

Khi thời gian chờ (wait time) của API action [`ReceiveMessage`](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/APIReference/API_ReceiveMessage.html) lớn hơn 0, *long polling* có hiệu lực. Thời gian chờ long polling tối đa là 20 giây. Long polling giúp giảm chi phí sử dụng Amazon SQS bằng cách giảm số lượng response rỗng (empty response – khi không có message nào sẵn có cho một request `ReceiveMessage`) và response rỗng giả (false empty response – khi có message sẵn có nhưng không được bao gồm trong response). Để biết thông tin về việc bật long polling cho một queue mới hoặc hiện có bằng Amazon SQS console, xem [Configuring queue parameters using the Amazon SQS console](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-configure-queue-parameters.html). Để biết các best practice, xem [Setting-up long polling in Amazon SQS](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/best-practices-setting-up-long-polling.html).

Long polling mang lại các lợi ích sau:

- Giảm response rỗng bằng cách cho phép Amazon SQS chờ cho đến khi có message sẵn có trong queue trước khi gửi response. Trừ khi kết nối bị timeout, response cho request `ReceiveMessage` sẽ chứa ít nhất một trong các message sẵn có, tối đa đến số lượng message tối đa được chỉ định trong action `ReceiveMessage`. Trong một số ít trường hợp, bạn có thể nhận được response rỗng ngay cả khi queue vẫn còn chứa message, đặc biệt nếu bạn chỉ định một giá trị thấp cho tham số [`ReceiveMessageWaitTimeSeconds`](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/APIReference/API_ReceiveMessage.html#SQS-ReceiveMessage-request-WaitTimeSeconds).
- Giảm response rỗng giả bằng cách truy vấn tất cả — thay vì một tập con — các máy chủ Amazon SQS.
- Trả về message ngay khi chúng trở nên sẵn có.

Để biết thông tin về cách xác nhận một queue đã trống, xem [Confirming that an Amazon SQS queue is empty](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/confirm-queue-is-empty.html).

## Sự khác biệt giữa long polling và short polling (Differences between long and short polling)

Short polling xảy ra khi tham số [`WaitTimeSeconds`](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/APIReference/API_ReceiveMessage.html#SQS-ReceiveMessage-request-WaitTimeSeconds) của một request [`ReceiveMessage`](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/APIReference/API_ReceiveMessage.html) được đặt là `0` theo một trong hai cách:

- Lời gọi `ReceiveMessage` đặt `WaitTimeSeconds` là `0`.
- Lời gọi `ReceiveMessage` không đặt `WaitTimeSeconds`, nhưng queue attribute [`ReceiveMessageWaitTimeSeconds`](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/APIReference/API_SetQueueAttributes.html) được đặt là `0`.

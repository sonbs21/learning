# Visibility timeout trong Amazon SQS (Amazon SQS visibility timeout)

> Nguồn: [Amazon SQS visibility timeout](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-visibility-timeout.html)

Khi bạn nhận một message từ một Amazon SQS queue, message đó vẫn nằm trong queue nhưng tạm thời trở nên ẩn (invisible) đối với các consumer khác. Việc ẩn này được kiểm soát bởi visibility timeout, đảm bảo rằng các consumer khác không thể xử lý cùng message đó trong khi bạn đang làm việc với nó. Amazon SQS cung cấp hai tùy chọn để xóa message sau khi xử lý:

- **Xóa thủ công (Manual deletion)** – Bạn xóa message một cách tường minh (explicitly) bằng action [`DeleteMessage`](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/APIReference/API_DeleteMessage.html).
- **Xóa tự động (Automatic deletion)** – Được hỗ trợ trong một số AWS SDK, message được tự động xóa khi xử lý thành công, giúp đơn giản hóa workflow.

![Biểu đồ dòng thời gian (timeline) cho thấy cách các request được xử lý trong khoảng visibility timeout](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/images/sqs-visibility-timeout-diagram.png)

## Các use case của visibility timeout (Visibility timeout use cases)

**Quản lý các tác vụ chạy lâu (Manage long-running tasks)** – Sử dụng visibility timeout để xử lý các tác vụ đòi hỏi thời gian xử lý kéo dài. Đặt một visibility timeout phù hợp cho các message cần thời gian xử lý kéo dài. Điều này đảm bảo các consumer khác không lấy cùng message đó trong khi nó đang được xử lý, ngăn chặn công việc trùng lặp và duy trì hiệu quả của hệ thống.

**Triển khai cơ chế retry (Implement retry mechanisms)** – Kéo dài visibility timeout bằng lập trình (programmatically) cho các tác vụ không hoàn thành trong timeout ban đầu. Nếu một tác vụ không hoàn thành trong visibility timeout ban đầu, bạn có thể kéo dài timeout bằng lập trình. Điều này cho phép hệ thống của bạn retry việc xử lý message mà không để message trở nên visible với các consumer khác, cải thiện khả năng chịu lỗi (fault tolerance) và độ tin cậy. Kết hợp với **Dead-Letter Queue (DLQ)** để quản lý các lỗi kéo dài (persistent failure).

**Điều phối các hệ thống phân tán (Coordinate distributed systems)** – Sử dụng SQS visibility timeout để điều phối các tác vụ giữa các hệ thống phân tán. Đặt các visibility timeout phù hợp với thời gian xử lý dự kiến của các thành phần khác nhau. Điều này giúp duy trì tính nhất quán và ngăn chặn race condition trong các kiến trúc phân tán phức tạp.

**Tối ưu hóa việc sử dụng tài nguyên (Optimize resource utilization)** – Điều chỉnh SQS visibility timeout để tối ưu hóa việc sử dụng tài nguyên trong ứng dụng của bạn. Bằng cách đặt các timeout phù hợp, bạn có thể đảm bảo các message được xử lý hiệu quả mà không chiếm giữ tài nguyên một cách không cần thiết. Điều này dẫn đến hiệu năng tổng thể của hệ thống và hiệu quả chi phí tốt hơn.

## Đặt và điều chỉnh visibility timeout (Setting and adjusting the visibility timeout)

Visibility timeout bắt đầu ngay khi một message được phân phối tới bạn. Trong khoảng thời gian này, bạn được kỳ vọng sẽ xử lý và xóa message. Nếu bạn không xóa nó trước khi timeout hết hạn, message sẽ trở nên visible trở lại trong queue và có thể được truy xuất bởi một consumer khác. Visibility timeout mặc định của một queue là 30 giây, nhưng bạn có thể điều chỉnh giá trị này cho phù hợp với thời gian ứng dụng của bạn cần để xử lý và xóa một message. Bạn cũng có thể đặt một visibility timeout cụ thể cho từng message riêng lẻ mà không thay đổi thiết lập tổng thể của queue. Sử dụng action [`ChangeMessageVisibility`](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/APIReference/API_ChangeMessageVisibility.html) để kéo dài hoặc rút ngắn timeout bằng lập trình khi cần.

## Message in flight và quota (In flight messages and quotas)

Trong Amazon SQS, message in-flight là các message đã được một consumer nhận nhưng chưa bị xóa. Đối với standard queue, có giới hạn khoảng 120.000 message in-flight, tùy thuộc vào lưu lượng của queue và lượng message tồn đọng (backlog). Nếu bạn chạm tới giới hạn này, Amazon SQS trả về lỗi `OverLimit`, cho biết không thể nhận thêm message nào cho đến khi một số message in-flight bị xóa. Đối với FIFO queue, các giới hạn phụ thuộc vào số message group đang hoạt động (active).

- **Khi sử dụng short polling** – Nếu chạm tới giới hạn này khi đang sử dụng short polling, Amazon SQS sẽ trả về lỗi `OverLimit`, cho biết không thể nhận thêm message nào cho đến khi một số message in-flight bị xóa.
- **Khi sử dụng long polling** – Nếu bạn đang sử dụng long polling, Amazon SQS không trả về lỗi khi chạm tới giới hạn message in-flight. Thay vào đó, nó sẽ không trả về bất kỳ message mới nào cho đến khi số lượng message in-flight giảm xuống dưới giới hạn.

Để quản lý các message in-flight một cách hiệu quả:

1. **Xóa kịp thời (Prompt deletion)** – Xóa message (thủ công hoặc tự động) sau khi xử lý để giảm số lượng in-flight.
2. **Giám sát bằng CloudWatch (Monitor with CloudWatch)** – Đặt alarm cho số lượng in-flight cao để tránh chạm tới giới hạn.
3. **Phân tán tải (Distribute load)** – Nếu bạn đang xử lý một khối lượng message lớn, hãy sử dụng thêm queue hoặc consumer để cân bằng tải và tránh nút thắt cổ chai (bottleneck).
4. **Yêu cầu tăng quota (Request a quota increase)** – Gửi yêu cầu tới [AWS Support](https://docs.aws.amazon.com/awssupport/latest/user/create-service-quota-increase.html) nếu cần giới hạn cao hơn.

## Hiểu về visibility timeout trong standard queue và FIFO queue (Understanding visibility timeout in standard and FIFO queues)

Trong cả standard queue và FIFO (First-In-First-Out) queue, visibility timeout giúp ngăn nhiều consumer xử lý cùng một message đồng thời. Tuy nhiên, do mô hình phân phối ít nhất một lần (at-least-once delivery) của Amazon SQS, không có đảm bảo tuyệt đối rằng một message sẽ không được phân phối nhiều hơn một lần trong khoảng visibility timeout.

- **Standard queue** – Visibility timeout trong standard queue ngăn nhiều consumer xử lý cùng một message cùng lúc. Tuy nhiên, do mô hình at-least-once delivery, Amazon SQS không đảm bảo rằng một message sẽ không được phân phối nhiều hơn một lần trong khoảng visibility timeout.
- **FIFO queue** – Đối với FIFO queue, các message có cùng message group ID được xử lý theo một trình tự nghiêm ngặt. Khi một message có message group ID đang in-flight, các message tiếp theo trong group đó sẽ không được cung cấp cho đến khi message in-flight bị xóa hoặc visibility timeout hết hạn. Tuy nhiên, điều này không "khóa" (lock) group vô thời hạn – mỗi message được xử lý theo trình tự, và chỉ khi mỗi message bị xóa hoặc trở nên visible trở lại thì message tiếp theo trong group đó mới sẵn sàng cho các consumer. Cách tiếp cận này đảm bảo việc xử lý có thứ tự bên trong group mà không khóa group một cách không cần thiết khỏi việc phân phối message.

## Xử lý lỗi (Handling failures)

Nếu bạn không xử lý và xóa một message trước khi visibility timeout hết hạn — do lỗi ứng dụng, crash, hoặc sự cố kết nối — message sẽ trở nên visible trở lại trong queue. Sau đó nó có thể được truy xuất bởi cùng consumer đó hoặc một consumer khác cho một lần xử lý khác. Điều này đảm bảo rằng message không bị mất ngay cả khi lần xử lý ban đầu thất bại. Tuy nhiên, việc đặt visibility timeout quá cao có thể làm chậm việc xuất hiện trở lại của các message chưa được xử lý, có khả năng làm chậm các lần retry. Điều quan trọng là phải đặt một visibility timeout phù hợp dựa trên thời gian xử lý dự kiến để xử lý message kịp thời.

## Thay đổi và chấm dứt visibility timeout (Changing and terminating visibility timeout)

Bạn có thể thay đổi hoặc chấm dứt visibility timeout bằng action `ChangeMessageVisibility`:

- **Thay đổi timeout (Changing the timeout)** – Điều chỉnh visibility timeout một cách động (dynamically) bằng [`ChangeMessageVisibility`](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/APIReference/API_ChangeMessageVisibility.html). Điều này cho phép bạn kéo dài hoặc rút ngắn thời lượng timeout cho phù hợp với nhu cầu xử lý.
- **Chấm dứt timeout (Terminating the timeout)** – Nếu bạn quyết định không xử lý một message đã nhận, hãy chấm dứt visibility timeout của nó bằng cách đặt `VisibilityTimeout` thành 0 giây thông qua action `ChangeMessageVisibility`. Điều này ngay lập tức làm cho message sẵn sàng để các consumer khác xử lý.

## Các best practice (Best practices)

Sử dụng các best practice sau để quản lý visibility timeout trong Amazon SQS, bao gồm việc đặt, điều chỉnh và kéo dài timeout, cũng như xử lý các message chưa được xử lý bằng Dead-Letter Queue (DLQ).

- **Đặt và điều chỉnh timeout (Setting and adjusting the timeout).** Bắt đầu bằng việc đặt visibility timeout khớp với thời gian tối đa mà ứng dụng của bạn thường cần để xử lý và xóa một message. Nếu bạn không chắc chắn về thời gian xử lý chính xác, hãy bắt đầu với một timeout ngắn hơn (ví dụ, 2 phút) và kéo dài khi cần. Triển khai một cơ chế heartbeat để định kỳ kéo dài visibility timeout, đảm bảo message vẫn ở trạng thái ẩn cho đến khi việc xử lý hoàn tất. Điều này giảm thiểu độ trễ trong việc xử lý lại các message chưa được xử lý và ngăn chặn việc message trở nên visible quá sớm.
- **Kéo dài timeout và xử lý giới hạn 12 giờ (Extending the timeout and handling the 12-Hour limit).** Nếu thời gian xử lý của bạn thay đổi hoặc có thể vượt quá timeout được đặt ban đầu, hãy sử dụng action `ChangeMessageVisibility` để kéo dài visibility timeout trong khi đang xử lý message. Hãy lưu ý rằng visibility timeout có giới hạn tối đa là 12 giờ kể từ khi message được nhận lần đầu tiên. Việc kéo dài timeout không đặt lại (reset) giới hạn 12 giờ này. Nếu việc xử lý của bạn cần nhiều thời gian hơn giới hạn này, hãy cân nhắc sử dụng AWS Step Functions hoặc chia tác vụ thành các bước nhỏ hơn.
- **Xử lý các message chưa được xử lý (Handling unprocessed messages).** Để quản lý các message thất bại sau nhiều lần xử lý, hãy cấu hình một Dead-Letter Queue (DLQ). Điều này đảm bảo rằng các message không thể được xử lý sau nhiều lần retry sẽ được thu thập riêng để phân tích hoặc xử lý thêm, ngăn chúng liên tục luân chuyển trong queue chính.

# Hành vi của Amazon SQS FIFO queue và Lambda concurrency (Amazon SQS FIFO queue and Lambda concurrency behavior)

> Nguồn: [Amazon SQS FIFO queue and Lambda concurrency behavior](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/fifo-queue-lambda-behavior.html)

Bằng cách sử dụng FIFO (First-In-First-Out) queue với Lambda, bạn có thể đảm bảo việc xử lý có thứ tự các message bên trong mỗi message group. Lambda function sẽ không chạy nhiều instance cho cùng một message group cùng lúc, nhờ đó duy trì được thứ tự. Tuy nhiên, nó có thể mở rộng (scale up) để xử lý nhiều message group song song, đảm bảo xử lý hiệu quả workload của queue. Các điểm sau mô tả hành vi của Lambda function khi xử lý message từ một Amazon SQS FIFO queue liên quan đến message group ID:

- **Một instance cho mỗi message group (Single instance per message group):** Tại bất kỳ thời điểm nào, chỉ có một Lambda instance xử lý message từ một message group ID cụ thể. Điều này đảm bảo rằng các message trong cùng một group được xử lý theo thứ tự, duy trì tính toàn vẹn của trình tự FIFO.
- **Xử lý đồng thời các group khác nhau (Concurrent processing of different groups):** Lambda có thể xử lý đồng thời các message từ các message group ID khác nhau bằng nhiều instance. Điều này có nghĩa là trong khi một instance của Lambda function đang xử lý message từ một message group ID, các instance khác có thể đồng thời xử lý message từ các message group ID khác, tận dụng khả năng concurrency của Lambda để xử lý nhiều group song song.

## Nhóm message trong FIFO queue (FIFO queue message grouping)

FIFO queue đảm bảo rằng message được xử lý theo đúng thứ tự mà chúng được gửi. Chúng sử dụng một **message group ID** để nhóm các message cần được xử lý tuần tự.

Các message trong cùng một message group được xử lý theo thứ tự, và chỉ một message từ mỗi group được xử lý tại một thời điểm để duy trì thứ tự này.

## Lambda concurrency với FIFO queue (Lambda concurrency with FIFO queues)

Sau khi bạn tạo queue, bạn có thể gửi message tới nó.

Khi bạn thiết lập một Lambda function để xử lý message từ một Amazon SQS FIFO queue, Lambda tôn trọng các đảm bảo về thứ tự mà FIFO queue cung cấp. Các điểm sau mô tả hành vi của Lambda function về mặt concurrency và scaling khi xử lý message từ một Amazon SQS FIFO queue khi sử dụng message group ID:

- **Concurrency bên trong message group (Concurrency within message groups):** Chỉ có một Lambda instance xử lý message cho một message group ID cụ thể tại một thời điểm. Điều này đảm bảo rằng các message bên trong một group được xử lý tuần tự.
- **Scaling và nhiều message group (Scaling and multiple message groups):** Mặc dù Lambda có thể mở rộng để xử lý message đồng thời, việc mở rộng này diễn ra giữa các message group khác nhau. Nếu bạn có nhiều message group, Lambda có thể xử lý nhiều group song song, với mỗi group được xử lý bởi một Lambda instance riêng biệt.

Để biết thêm thông tin, xem [Scaling and concurrency in Lambda](https://docs.aws.amazon.com/lambda/latest/operatorguide/scaling-concurrency.html) trong *AWS Lambda Operator Guide*.

## Ví dụ use case (Use case example)

Giả sử FIFO queue của bạn nhận các message có cùng message group ID, và Lambda function của bạn có giới hạn concurrency cao (lên tới 1000).

Nếu một message từ group ID 'A' đang được xử lý và một message khác từ group ID 'A' đến, message thứ hai sẽ không kích hoạt (trigger) một Lambda instance mới cho đến khi message đầu tiên được xử lý xong hoàn toàn.

Tuy nhiên, nếu các message từ group ID 'A' và 'B' đến, cả hai message có thể được xử lý đồng thời bởi các Lambda instance riêng biệt.

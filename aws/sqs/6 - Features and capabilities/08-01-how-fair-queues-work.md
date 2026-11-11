# Cách hoạt động của Amazon SQS fair queue (How Amazon SQS fair queues work)

> Nguồn: [How Amazon SQS fair queues work](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-fair-queues-detailed.html)

Amazon SQS fair queue tự động giảm thiểu tác động noisy-neighbor trong các queue multi-tenant bằng cách ngăn một tenant đơn lẻ làm chậm việc xử lý message của các tenant khác. Khi fair queue phát hiện một tenant đang sử dụng một phần năng lực consumer (consumer capacity) của queue lớn bất cân xứng, chúng sẽ ưu tiên phân phối message từ các tenant yên tĩnh (quiet tenant).

## Nhận diện tenant (Tenant identification)

Fair queue nhận diện tenant bằng thuộc tính `MessageGroupId` trên mỗi message. Các message có cùng `MessageGroupId` thuộc về cùng một tenant. Một fair queue có thể chứa hỗn hợp các message có và không có `MessageGroupId`. Mỗi message không có `MessageGroupId` được coi là thuộc về một tenant riêng biệt của chính nó.

Chúng tôi khuyến nghị đặt `MessageGroupId` trên mọi message bạn gửi tới một fair queue, sử dụng một định danh ánh xạ (map) tới một thực thể thực tế trong hệ thống của bạn, chẳng hạn như customer ID, client application ID, hoặc loại request.

## Cách phát hiện hoạt động (How detection works)

Amazon SQS phát hiện noisy neighbor bằng hai thước đo:

- **Tỷ phần đồng thời (Concurrency share)** — số message in-flight của tenant tính theo tỷ lệ trên tổng số message in-flight trong queue. Thước đo này phát hiện các tenant có nhiều message đang được xử lý cùng lúc, dù là do khối lượng gửi cao, xử lý chậm, hoặc cả hai.
- **Tỷ phần thời gian xử lý (Processing time share)** — tỷ phần gần đây của tenant trên tổng thời gian xử lý của consumer. Thước đo này phát hiện các tenant có ít message nhưng xử lý chậm, khiến tenant đó chiếm dụng đáng kể thời gian của consumer mà không cần có nhiều message in-flight cùng lúc.

Một tenant được đánh dấu là noisy neighbor khi một trong các điều sau là đúng:

- **Concurrency share**: tenant chiếm hơn 10% số message in-flight trong queue và có ít nhất 30 message của chính nó đang in-flight.
- **Processing time share**: tỷ phần gần đây của tenant trên tổng thời gian xử lý của consumer vượt quá 10%.

Vì Amazon SQS là một hệ thống phân tán, các ngưỡng này là xấp xỉ và việc phát hiện có thể không được kích hoạt chính xác tại các giá trị được mô tả.

## Cách thứ tự phân phối message thay đổi (How message delivery order changes)

Khi một tenant được phát hiện là noisy neighbor, Amazon SQS ưu tiên phân phối message từ các quiet tenant. Các consumer instance khi poll message sẽ nhận message từ quiet tenant bất cứ khi nào có message của quiet tenant sẵn có. Việc ưu tiên này tiếp tục cho đến khi concurrency share và processing time share của noisy neighbor giảm xuống mức tương đương với các quiet tenant.

Message của noisy neighbor không bị loại bỏ (dropped) hay bị điều tiết (throttled). Dwell time của chúng tăng lên trong khi message của quiet tenant được ưu tiên, và trở lại bình thường khi backlog của chúng được giải quyết hết. Khi không có message nào của quiet tenant đang chờ, các consumer instance nhận message của noisy neighbor như bình thường.

Nếu nhiều tenant được phát hiện là noisy neighbor cùng lúc, các consumer instance sẽ nhận message từ noisy tenant có ít message in-flight nhất trước. Điều này giúp cân bằng thời gian xử lý giữa các noisy tenant.

## Khi một noisy neighbor trở nên yên tĩnh (When a noisy neighbor becomes quiet)

Một tenant không còn được coi là noisy neighbor khi một trong các điều sau là đúng:

- Backlog của tenant đã được tiêu thụ hết hoàn toàn.
- Không có message nào từ tenant đang in-flight trong một khoảng thời gian liên tục 5 phút.

Sau đó, message của tenant không còn bị giảm mức ưu tiên (deprioritized) và được đối xử giống như message từ bất kỳ quiet tenant nào khác.

## Các cân nhắc về năng lực consumer (Consumer capacity considerations)

Để thước đo concurrency share hoạt động hiệu quả, các consumer của bạn cần xử lý đủ nhiều message đồng thời để tỷ phần message in-flight của một tenant có thể nổi bật lên. Hãy tính đến điều này khi định cỡ (sizing) đội consumer của bạn.

Khi sử dụng Lambda làm consumer thông qua event source mapping, số lượng message in-flight phụ thuộc vào cả concurrency của Lambda function lẫn batch size. Hãy đánh giá các thiết lập này cùng nhau khi định cỡ consumer.

Khi số lượng message in-flight quá thấp để ngưỡng concurrency share được kích hoạt, thước đo processing time share vẫn có thể phát hiện noisy neighbor. Tuy nhiên, fair queue hoạt động tốt nhất khi các consumer xử lý đủ nhiều message đồng thời để cả hai thước đo đều được đánh giá.

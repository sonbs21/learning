# Logic phân phối của FIFO queue trong Amazon SQS (FIFO queue delivery logic in Amazon SQS)

> Nguồn: [FIFO queue delivery logic in Amazon SQS](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/FIFO-queues-understanding-logic.html)

Các khái niệm sau đây làm rõ cách Amazon SQS FIFO queue xử lý việc gửi và nhận message, đặc biệt là khi làm việc với thứ tự message và message group ID.

## Gửi message (Sending messages)

Amazon SQS FIFO queue bảo toàn thứ tự message bằng cách sử dụng các deduplication ID duy nhất và message group ID. Chủ đề này nêu bật tầm quan trọng của message group ID trong việc duy trì thứ tự nghiêm ngặt bên trong các group và nêu bật các best practice để đảm bảo việc phân phối message tin cậy, có thứ tự khi có nhiều producer.

1. **Bảo toàn thứ tự (Order preservation)**
   - Khi nhiều message được gửi liên tiếp tới một FIFO queue với các message deduplication ID duy nhất, Amazon SQS lưu trữ chúng và xác nhận (acknowledge) việc truyền tải của chúng. Các message này sau đó được nhận và xử lý theo đúng thứ tự mà chúng đã được truyền đi.
2. **Message group ID**
   - Trong FIFO queue, message được sắp xếp thứ tự dựa trên message group ID của chúng. Nếu nhiều producer hoặc thread gửi message với cùng một message group ID, Amazon SQS đảm bảo chúng được lưu trữ và xử lý theo thứ tự mà chúng đến.
   - Best practice: Để đảm bảo thứ tự message nghiêm ngặt khi có nhiều producer, hãy gán một message group ID duy nhất cho tất cả các message từ mỗi producer.
3. **Thứ tự theo từng group (Per-group ordering)**
   - **Logic của FIFO queue được áp dụng trên cơ sở từng message group ID:**
     - Mỗi message group ID đại diện cho một nhóm message riêng biệt, có thứ tự.
     - Bên trong một message group ID, tất cả các message được gửi và nhận theo thứ tự nghiêm ngặt.
     - Các message có message group ID khác nhau có thể đến hoặc được xử lý không theo thứ tự so với nhau.
   - **Yêu cầu (Requirement)** - Bạn phải gắn một message group ID với mỗi message. Nếu một message được gửi mà không có group ID, action sẽ thất bại.
   - **Kịch bản một group (Single group scenario)** - Nếu bạn yêu cầu tất cả các message được xử lý theo thứ tự nghiêm ngặt, hãy sử dụng cùng một message group ID cho mọi message.

## Nhận message (Receiving messages)

Amazon SQS FIFO queue xử lý việc truy xuất message, bao gồm xử lý theo batch, các đảm bảo về thứ tự FIFO, và các giới hạn về việc yêu cầu các message group ID cụ thể. Chủ đề này giải thích cách Amazon SQS truy xuất message bên trong và giữa các message group ID trong khi vẫn duy trì thứ tự nghiêm ngặt và các quy tắc về visibility.

1. **Truy xuất theo batch (Batch retrieval)**
   - Khi nhận message từ một FIFO queue có nhiều message group ID, Amazon SQS:
     - Cố gắng trả về càng nhiều message có cùng message group ID càng tốt trong một lời gọi duy nhất.
     - Cho phép các consumer khác xử lý đồng thời các message từ các message group ID khác.
   - **Làm rõ quan trọng (Important clarification)**
     - Bạn có thể nhận nhiều message từ cùng một message group ID trong một batch (tối đa 10 message trong một lời gọi duy nhất bằng cách sử dụng tham số `MaxNumberOfMessages`).
     - Tuy nhiên, bạn không thể nhận thêm message từ cùng message group ID đó trong các request tiếp theo cho đến khi:
       - Các message hiện đang được nhận bị xóa, hoặc
       - Chúng trở nên visible trở lại (ví dụ, sau khi visibility timeout hết hạn).
2. **Đảm bảo thứ tự FIFO (FIFO order guarantee)**
   - Các message được truy xuất trong một batch giữ nguyên thứ tự FIFO bên trong group.
   - Nếu có ít hơn 10 message khả dụng cho cùng một message group ID, Amazon SQS có thể bao gồm các message từ các message group ID khác trong cùng một batch, nhưng mỗi group vẫn giữ thứ tự FIFO.
3. **Các giới hạn đối với consumer (Consumer limitations)**
   - Bạn không thể yêu cầu một cách tường minh (explicitly) việc nhận message từ một message group ID cụ thể.

## Thử lại nhiều lần (Retrying multiple times)

Producer và consumer có thể thử lại (retry) một cách an toàn các action bị thất bại trong Amazon SQS FIFO queue mà không làm gián đoạn thứ tự message hoặc tạo ra bản trùng lặp. Chủ đề này nêu bật cách deduplication ID và visibility timeout đảm bảo tính toàn vẹn của message trong quá trình retry.

1. **Retry của producer (Producer retries)**
   - Nếu một action [`SendMessage`](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/APIReference/API_SendMessage.html) thất bại, producer có thể thử gửi lại message nhiều lần với cùng một message deduplication ID.
   - Miễn là producer nhận được ít nhất một xác nhận (acknowledgment) trước khi deduplication interval hết hạn, các lần retry:
     - Không tạo ra message trùng lặp.
     - Không làm gián đoạn thứ tự message.
2. **Retry của consumer (Consumer retries)**
   - Nếu một action [`ReceiveMessage`](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/APIReference/API_ReceiveMessage.html) thất bại, consumer có thể thử lại bao nhiêu lần tùy cần thiết bằng cách sử dụng cùng một receive request attempt ID.
   - Miễn là consumer nhận được ít nhất một xác nhận (acknowledgment) trước khi visibility timeout hết hạn, các lần retry:
     - Không làm gián đoạn thứ tự message.

## Ghi chú bổ sung về hành vi của FIFO (Additional notes on FIFO behavior)

Tìm hiểu về việc xử lý visibility timeout, cho phép xử lý song song với nhiều message group ID, và đảm bảo xử lý tuần tự nghiêm ngặt trong các kịch bản một group.

1. **Xử lý visibility timeout (Handling visibility timeout)**
   - Khi một message được truy xuất nhưng không bị xóa, nó vẫn ở trạng thái ẩn (invisible) cho đến khi visibility timeout hết hạn.
   - Không có message bổ sung nào từ cùng message group ID được trả về cho đến khi message đầu tiên bị xóa hoặc trở nên visible trở lại.
2. **Tính đồng thời và xử lý song song (Concurrency and parallel processing)**
   - FIFO queue cho phép xử lý song song các message giữa các message group ID khác nhau.
   - Để tối đa hóa tính đồng thời, hãy thiết kế hệ thống của bạn với nhiều message group ID cho các workflow độc lập.
3. **Kịch bản một group (Single group scenarios)**
   - Để xử lý tuần tự nghiêm ngặt tất cả các message trong một FIFO queue, hãy sử dụng một message group ID duy nhất cho tất cả các message trong queue.

## Các ví dụ để hiểu rõ hơn (Examples for better understanding)

Sau đây là các kịch bản thực tế minh họa hành vi của FIFO queue trong Amazon SQS.

1. **Kịch bản 1: Một group ID (Single group ID)**
   - Một producer gửi năm message với cùng message group ID là Group A.
   - Một consumer nhận các message này theo thứ tự FIFO. Cho đến khi consumer xóa các message này hoặc visibility timeout hết hạn, không có message bổ sung nào từ Group A được nhận.
2. **Kịch bản 2: Nhiều group ID (Multiple group IDs)**
   - Một producer gửi năm message tới Group A và 5 message tới Group B.
   - Consumer 1 xử lý các message từ Group A, trong khi Consumer 2 xử lý các message từ Group B. Điều này cho phép xử lý song song trong khi vẫn duy trì thứ tự nghiêm ngặt bên trong mỗi group.
3. **Kịch bản 3: Truy xuất theo batch (Batch retrieval)**
   - Một producer gửi bảy message tới Group A và ba message tới Group B.
   - Một consumer duy nhất truy xuất tối đa 10 message. Nếu queue cho phép, nó có thể trả về:
     - Bảy message từ Group A và ba message từ Group B (hoặc ít hơn nếu có ít message khả dụng hơn từ một group duy nhất).

# Thông lượng cao cho FIFO queue trong Amazon SQS (High throughput for FIFO queues in Amazon SQS)

> Nguồn: [High throughput for FIFO queues in Amazon SQS](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/high-throughput-fifo.html)

High throughput FIFO queue trong Amazon SQS quản lý hiệu quả thông lượng message cao trong khi vẫn duy trì thứ tự message nghiêm ngặt, đảm bảo độ tin cậy và khả năng mở rộng cho các ứng dụng xử lý số lượng lớn message. Giải pháp này lý tưởng cho các kịch bản đòi hỏi cả thông lượng cao lẫn việc phân phối message có thứ tự.

Amazon SQS high throughput FIFO queue là không cần thiết trong các kịch bản mà thứ tự message nghiêm ngặt không phải là yếu tố then chốt và khối lượng message đến tương đối thấp hoặc không đều (sporadic). Ví dụ, nếu bạn có một ứng dụng quy mô nhỏ xử lý các message không thường xuyên hoặc không tuần tự, sự phức tạp và chi phí tăng thêm liên quan đến high throughput FIFO queue có thể không đáng. Ngoài ra, nếu ứng dụng của bạn không yêu cầu khả năng thông lượng được tăng cường do high throughput FIFO queue cung cấp, việc chọn một Amazon SQS standard queue có thể tiết kiệm chi phí hơn và đơn giản hơn để quản lý.

Để tăng khả năng xử lý request (request capacity) trong high throughput FIFO queue, khuyến nghị tăng số lượng message group. Để biết thêm thông tin về các quota message của chế độ high throughput, xem [Amazon SQS service quotas](https://docs.aws.amazon.com/general/latest/gr/sqs-service.html#limits_sqs.html) trong *Amazon Web Services General Reference*.

Để biết thông tin về các quota theo từng queue và các chiến lược phân phối dữ liệu, xem [Amazon SQS message quotas](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/quotas-messages.html) và [Partitions and data distribution for high throughput for SQS FIFO queues](#partition-và-phân-phối-dữ-liệu-cho-chế-độ-high-throughput-của-sqs-fifo-queue-partitions-and-data-distribution-for-high-throughput-for-sqs-fifo-queues).

## Các use case cho high throughput của Amazon SQS FIFO queue (Use cases for high throughput for Amazon SQS FIFO queues)

Các use case sau đây nêu bật những ứng dụng đa dạng của high throughput FIFO queue, thể hiện hiệu quả của chúng trong nhiều ngành và kịch bản:

1. **Xử lý dữ liệu thời gian thực (Real-time data processing):** Các ứng dụng làm việc với các luồng dữ liệu thời gian thực, chẳng hạn như xử lý sự kiện (event processing) hoặc thu nhận dữ liệu telemetry (telemetry data ingestion), có thể hưởng lợi từ high throughput FIFO queue để xử lý dòng message liên tục trong khi vẫn bảo toàn thứ tự của chúng để phân tích chính xác.
2. **Xử lý đơn hàng thương mại điện tử (E-commerce order processing):** Trong các nền tảng thương mại điện tử nơi việc duy trì thứ tự các giao dịch của khách hàng là yếu tố then chốt, high throughput FIFO queue đảm bảo các đơn hàng được xử lý tuần tự và không bị chậm trễ, ngay cả trong các mùa mua sắm cao điểm.
3. **Dịch vụ tài chính (Financial services):** Các tổ chức tài chính xử lý giao dịch tần suất cao (high-frequency trading) hoặc dữ liệu giao dịch dựa vào high throughput FIFO queue để xử lý dữ liệu thị trường và các giao dịch với độ trễ tối thiểu, đồng thời tuân thủ các yêu cầu pháp lý nghiêm ngặt về thứ tự message.
4. **Truyền phát media (Media streaming):** Các nền tảng streaming và dịch vụ phân phối media sử dụng high throughput FIFO queue để quản lý việc phân phối các file media và nội dung streaming, đảm bảo trải nghiệm phát mượt mà cho người dùng trong khi vẫn duy trì đúng thứ tự phân phối nội dung.

## Partition và phân phối dữ liệu cho chế độ high throughput của SQS FIFO queue (Partitions and data distribution for high throughput for SQS FIFO queues)

Amazon SQS lưu trữ dữ liệu của FIFO queue trong các partition. Một *partition* là một phần lưu trữ được cấp phát cho một queue, được tự động sao chép (replicate) trên nhiều Availability Zone bên trong một AWS Region. Bạn không quản lý các partition. Thay vào đó, Amazon SQS xử lý việc quản lý partition.

Đối với FIFO queue, Amazon SQS thay đổi số lượng partition trong một queue trong các tình huống sau:

- Nếu tốc độ request hiện tại tiến gần hoặc vượt quá mức mà các partition hiện có có thể hỗ trợ, các partition bổ sung sẽ được cấp phát cho đến khi queue đạt tới quota của region. Để biết thông tin về các quota, xem [Amazon SQS message quotas](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/quotas-messages.html).
- Nếu các partition hiện tại có mức sử dụng (utilization) thấp, số lượng partition có thể bị giảm đi.

Việc quản lý partition diễn ra tự động ở chế độ nền và trong suốt (transparent) đối với ứng dụng của bạn. Queue và các message của bạn luôn khả dụng mọi lúc.

### Phân phối dữ liệu theo message group ID (Distributing data by message group IDs)

Để thêm một message vào FIFO queue, Amazon SQS sử dụng giá trị message group ID của mỗi message làm đầu vào cho một hàm băm (hash function) nội bộ. Giá trị đầu ra từ hàm băm xác định partition nào sẽ lưu trữ message.

Sơ đồ sau cho thấy một queue trải trên nhiều partition. Message group ID của queue dựa trên số hiệu item (item number). Amazon SQS sử dụng hàm băm của nó để xác định nơi lưu trữ một item mới; trong trường hợp này, nó dựa trên giá trị băm của chuỗi `item0`. Lưu ý rằng các item được lưu trữ theo cùng thứ tự mà chúng được thêm vào queue. Vị trí của mỗi item được xác định bởi giá trị băm của message group ID của nó.

![Một queue trải trên nhiều partition.](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/images/fifo-documentation-single.png)

> **Lưu ý (Note)**
>
> Amazon SQS được tối ưu hóa để phân phối đồng đều các item trên các partition của một FIFO queue, bất kể số lượng partition là bao nhiêu. AWS khuyến nghị bạn sử dụng các message group ID có thể có số lượng lớn các giá trị khác nhau (distinct values).

### Tối ưu hóa việc sử dụng partition (Optimizing partition utilization)

Mỗi partition hỗ trợ tối đa 3.000 message mỗi giây khi dùng batching, hoặc tối đa 300 message mỗi giây cho các thao tác send, receive và delete ở các region được hỗ trợ. Để biết thêm thông tin về các quota message của chế độ high throughput, xem [Amazon SQS service quotas](https://docs.aws.amazon.com/general/latest/gr/sqs-service.html#limits_sqs.html) trong *Amazon Web Services General Reference*.

Khi sử dụng các batch API, mỗi message được định tuyến (route) dựa trên quy trình được mô tả trong [Distributing data by message group IDs](#phân-phối-dữ-liệu-theo-message-group-id-distributing-data-by-message-group-ids). Các message được định tuyến tới cùng một partition sẽ được gom nhóm và xử lý trong một giao dịch (transaction) duy nhất.

Để tối ưu hóa việc sử dụng partition cho API `SendMessageBatch`, AWS khuyến nghị gom batch các message có cùng message group ID khi có thể.

Để tối ưu hóa việc sử dụng partition cho các API `DeleteMessageBatch` và `ChangeMessageVisibilityBatch`, AWS khuyến nghị sử dụng các request `ReceiveMessage` với tham số `MaxNumberOfMessages` được đặt là 10, và gom batch các receipt-handle được trả về bởi một request `ReceiveMessage` duy nhất.

Trong ví dụ sau, một batch gồm các message với nhiều message group ID khác nhau được gửi đi. Batch được chia thành ba nhóm, mỗi nhóm được tính vào quota của partition tương ứng.

![Một batch message với các message group ID khác nhau được lưu trữ trong các partition.](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/images/fifo-documentation-batch.png)

> **Lưu ý (Note)**
>
> Amazon SQS chỉ đảm bảo rằng các message có cùng kết quả hàm băm nội bộ của message group ID được gom nhóm bên trong một batch request. Tùy thuộc vào đầu ra của hàm băm nội bộ và số lượng partition, các message có message group ID khác nhau có thể được gom nhóm. Vì hàm băm hoặc số lượng partition có thể thay đổi bất kỳ lúc nào, các message được gom nhóm tại một thời điểm có thể không được gom nhóm về sau.

# Các quota về message của Amazon SQS (Amazon SQS message quotas)

> Nguồn: [Amazon SQS message quotas](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/quotas-messages.html)

Bảng sau liệt kê các quota liên quan đến message.

> Ghi chú của người dịch: tài liệu gốc trình bày dưới dạng bảng 2 cột (Quota | Description). Vì ô **Message throughput** có nhiều cấp nội dung lồng nhau, bản dịch trình bày mỗi quota thành một mục riêng, giữ nguyên thứ tự và nội dung.

### Batched message ID

Một batched message ID có thể có tối đa 80 ký tự. Các ký tự sau được chấp nhận: ký tự chữ và số (alphanumeric), dấu gạch ngang (`-`), và dấu gạch dưới (`_`).

### Message attributes

Một message có thể chứa tối đa 10 metadata attribute.

### Message batch

Một batch request message đơn lẻ có thể bao gồm tối đa 10 message. Để biết thêm thông tin, xem [Configuring AmazonSQSBufferedAsyncClient](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-client-side-buffering-request-batching.html#configuring-buffered-async-client) trong phần [Amazon SQS batch actions](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-batch-api-actions.html).

### Message content

Một message chỉ có thể bao gồm XML, JSON và văn bản không định dạng (unformatted text). Các ký tự Unicode sau được cho phép: `#x9` | `#xA` | `#xD` | `#x20` đến `#xD7FF` | `#xE000` đến `#xFFFD` | `#x10000` đến `#x10FFFF`

Bất kỳ ký tự nào không nằm trong danh sách này sẽ bị từ chối. Để biết thêm thông tin, xem [W3C specification for characters](https://www.w3.org/TR/REC-xml/#charsets).

### Message group ID

`MessageGroupId` là bắt buộc đối với FIFO queue. Nếu bạn không cung cấp `MessageGroupId` khi gửi message tới một FIFO queue, action sẽ thất bại. Trong standard queue, việc sử dụng `MessageGroupId` cho phép sử dụng [fair queue](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-fair-queues.html). Chúng tôi khuyến nghị bạn đưa `MessageGroupId` vào tất cả các message khi sử dụng fair queue.

Độ dài của `MessageGroupId` là 128 ký tự. Giá trị hợp lệ: ký tự chữ và số (alphanumeric) và dấu câu (punctuation) ``(!"#$%&'()*+,-./:;<=>?@[\]^_`{|}~)``.

### Message retention

Theo mặc định, một message được lưu giữ trong 4 ngày. Tối thiểu là 60 giây (1 phút). Tối đa là 1.209.600 giây (14 ngày).

### Message throughput

- **[Standard queue](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/standard-queues.html)**
  - Standard queue hỗ trợ một số lượng API call mỗi giây, cho mỗi action ([`SendMessage`](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/APIReference/API_SendMessage.html), [`ReceiveMessage`](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/APIReference/API_ReceiveMessage.html), hoặc [`DeleteMessage`](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/APIReference/API_DeleteMessage.html)), rất cao, gần như không giới hạn. Thông lượng cao này khiến chúng trở nên lý tưởng cho các use case đòi hỏi xử lý khối lượng lớn message một cách nhanh chóng, chẳng hạn như truyền dữ liệu thời gian thực (real-time data streaming) hoặc các ứng dụng quy mô lớn. Mặc dù standard queue tự động mở rộng theo nhu cầu, việc giám sát các mẫu sử dụng (usage pattern) là điều thiết yếu để đảm bảo hiệu năng tối ưu, đặc biệt là ở các region có workload cao hơn.
- **[FIFO queue](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-fifo-queues.html)**
  - Mỗi partition trong một FIFO queue bị giới hạn ở 300 giao dịch mỗi giây (transactions per second), cho mỗi API action (`SendMessage`, `ReceiveMessage` và `DeleteMessage`). Giới hạn này áp dụng riêng cho chế độ không phải high throughput (non-high throughput mode). Bằng cách chuyển sang chế độ high throughput, bạn có thể vượt qua giới hạn mặc định này. Để bật chế độ high-throughput, xem [Enabling high throughput for FIFO queues in Amazon SQS](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/enable-high-throughput-fifo.html).
  - Nếu bạn sử dụng batching, FIFO queue không ở chế độ high throughput hỗ trợ tối đa 3.000 message mỗi giây, cho mỗi API action (`SendMessage`, `ReceiveMessage` và `DeleteMessage`). 3.000 message mỗi giây tương ứng với 300 API call, mỗi call chứa một batch gồm 10 message.
- **[High throughput cho FIFO queue](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/high-throughput-fifo.html)**
  - Các giới hạn của Amazon SQS FIFO dựa trên số lượng API request, không phải giới hạn về message. Đối với chế độ high throughput, các giới hạn API request này như sau:
  - **Giới hạn thông lượng giao dịch (Transaction throughput limits) (các API call không dùng batching)**
    - Các giới hạn này xác định tần suất mà mỗi thao tác API (chẳng hạn như [SendMessage](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/APIReference/API_SendMessage.html), [ReceiveMessage](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/APIReference/API_ReceiveMessage.html), hoặc [DeleteMessage](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/APIReference/API_DeleteMessage.html)) có thể được thực hiện độc lập, đảm bảo hiệu năng hệ thống hiệu quả trong phạm vi số giao dịch mỗi giây (TPS) cho phép.
    - Các giới hạn sau dựa trên các API call không dùng batch:
      - US East (N. Virginia), US West (Oregon) và Europe (Ireland): Tối đa 70.000 giao dịch mỗi giây (TPS).
      - US East (Ohio) và Europe (Frankfurt): Tối đa 19.000 TPS.
      - Asia Pacific (Mumbai), Asia Pacific (Singapore), Asia Pacific (Sydney), Asia Pacific (Tokyo) và Europe (Spain): Tối đa 9.000 TPS.
      - Europe (London) và South America (São Paulo): Tối đa 4.500 TPS.
      - Tất cả các AWS Region khác: Thông lượng mặc định 2.400 TPS.
  - **Tối đa hóa thông lượng bằng batching (Maximizing throughput with batching)**
    - Xử lý nhiều message trong một API call duy nhất, giúp tăng hiệu quả đáng kể. Thay vì xử lý từng message riêng lẻ, batching cho phép bạn gửi, nhận hoặc xóa tối đa 10 message trong một API request duy nhất. Điều này làm giảm tổng số API call, cho phép bạn xử lý nhiều message hơn mỗi giây trong khi vẫn nằm trong giới hạn giao dịch (TPS) của region, tối đa hóa thông lượng và hiệu năng hệ thống. Để biết thêm thông tin, xem [Increasing throughput using horizontal scaling and action batching with Amazon SQS](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-throughput-horizontal-scaling-and-batching.html).
    - Các giới hạn sau dựa trên các API call dùng batch:
      - US East (N. Virginia), US West (Oregon) và Europe (Ireland): Tối đa 700.000 message mỗi giây (gấp 10 lần giới hạn không dùng batch là 70.000 TPS).
      - US East (Ohio) và Europe (Frankfurt): Tối đa 190.000 message mỗi giây.
      - Asia Pacific (Mumbai), Asia Pacific (Singapore), Asia Pacific (Sydney), Asia Pacific (Tokyo) và Europe (Spain): Tối đa 90.000 message mỗi giây.
      - Europe (London) và South America (São Paulo): Tối đa 45.000 message mỗi giây.
      - Tất cả các AWS Region khác: Tối đa 24.000 message mỗi giây.
  - **Tối ưu hóa thông lượng ngoài batching (Optimizing throughput beyond batching)**
    - Mặc dù batching có thể tăng thông lượng đáng kể, điều quan trọng là cần cân nhắc các chiến lược khác để tối ưu hóa hiệu năng FIFO:
      - **Phân phối message trên nhiều message group ID (Distribute messages across multiple message group IDs)** – Vì các message bên trong một group được xử lý tuần tự, việc phân phối workload của bạn trên nhiều message group cho phép tính song song tốt hơn và thông lượng tổng thể cao hơn. Để biết thêm thông tin, xem [Partitions and data distribution for high throughput for SQS FIFO queues](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/high-throughput-fifo.html#partitions-and-data-distribution).
      - **Sử dụng API call hiệu quả (Efficient use of API calls)** – Giảm thiểu các API call không cần thiết, chẳng hạn như thay đổi visibility thường xuyên hoặc xóa message lặp lại, để tối ưu hóa việc sử dụng TPS khả dụng và cải thiện hiệu quả.
      - **Sử dụng long poll khi nhận (Use long poll receives)** – Tận dụng long polling bằng cách đặt [`WaitTimeSeconds`](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/APIReference/API_ReceiveMessage.html#SQS-ReceiveMessage-request-WaitTimeSeconds) trong các receive request để giảm các response rỗng khi không có message nào khả dụng, giảm các API call không cần thiết và sử dụng tốt hơn quota TPS của bạn.
      - **Yêu cầu tăng thông lượng (Requesting throughput increases)** – Nếu ứng dụng của bạn yêu cầu thông lượng cao hơn các giới hạn mặc định, hãy yêu cầu tăng bằng console [Service Quotas](https://console.aws.amazon.com/servicequotas/home/services/sqs/quotas). Điều này có thể cần thiết cho các workload có nhu cầu cao hoặc ở các region có giới hạn mặc định thấp hơn. Để bật chế độ high-throughput, xem [Enabling high throughput for FIFO queues in Amazon SQS](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/enable-high-throughput-fifo.html).

### Message timer

Độ trễ (delay) mặc định (tối thiểu) cho một message là 0 giây. Tối đa là 15 phút.

### Message size

Kích thước message tối thiểu là 1 byte (1 ký tự). Tối đa là 1.048.576 byte (1 MiB).

Để gửi các message lớn hơn 1 MiB, bạn có thể sử dụng [Amazon SQS Extended Client Library for Java](https://github.com/awslabs/amazon-sqs-java-extended-client-lib) và [Amazon SQS Extended Client Library for Python](https://github.com/awslabs/amazon-sqs-python-extended-client-lib). Thư viện này cho phép bạn gửi một Amazon SQS message chứa tham chiếu (reference) tới một message payload trong Amazon S3. Kích thước payload tối đa là 2 GB. Thư viện mở rộng này chỉ hoạt động với các client đồng bộ (synchronous client).

### Message visibility timeout

Visibility timeout mặc định cho một message là 30 giây. Tối thiểu là 0 giây. Tối đa là 12 giờ.

### Policy information

Quota tối đa là 8.192 byte, 20 statement, 50 principal, hoặc 10 condition. Để biết thêm thông tin, xem [Amazon SQS policy quotas](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/quotas-policies.html).

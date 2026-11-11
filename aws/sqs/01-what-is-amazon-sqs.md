# Amazon Simple Queue Service là gì?

> Nguồn: [What is Amazon Simple Queue Service?](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/welcome.html)

Amazon Simple Queue Service (Amazon SQS) cung cấp một hàng đợi (queue) được lưu trữ (hosted) an toàn, bền vững (durable) và sẵn sàng (available), cho phép bạn tích hợp và tách rời (decouple) các hệ thống và thành phần phần mềm phân tán. Amazon SQS cung cấp các cấu trúc phổ biến như [dead-letter queue](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-dead-letter-queues.html) và [cost allocation tag](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-queue-tags.html). Nó cung cấp một web services API chung mà bạn có thể truy cập bằng bất kỳ ngôn ngữ lập trình nào được AWS SDK hỗ trợ.

## Lợi ích của việc sử dụng Amazon SQS

- **Bảo mật (Security)** – [Bạn kiểm soát](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/security-iam.html) ai có thể gửi message tới và nhận message từ một Amazon SQS queue. Bạn có thể chọn truyền dữ liệu nhạy cảm bằng cách bảo vệ nội dung của các message trong queue bằng mã hóa phía máy chủ (server-side encryption – SSE) mặc định do Amazon SQS quản lý, hoặc bằng các khóa [SSE](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-server-side-encryption.html) tùy chỉnh được quản lý trong AWS Key Management Service (AWS KMS).
- **Độ bền (Durability)** – Để đảm bảo an toàn cho message của bạn, Amazon SQS lưu trữ chúng trên nhiều máy chủ. Standard queue hỗ trợ [phân phối message ít nhất một lần (at-least-once message delivery)](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/standard-queues-at-least-once-delivery.html), còn FIFO queue hỗ trợ [xử lý message đúng một lần (exactly-once message processing)](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/FIFO-queues-exactly-once-processing.html) và chế độ [thông lượng cao (high-throughput)](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/high-throughput-fifo.html).
- **Tính sẵn sàng (Availability)** – Amazon SQS sử dụng [hạ tầng dự phòng (redundant infrastructure)](#kiến-trúc-cơ-bản-của-amazon-sqs) để cung cấp khả năng truy cập message với mức độ đồng thời cao (highly-concurrent) và tính sẵn sàng cao cho việc tạo (produce) và tiêu thụ (consume) message.
- **Khả năng mở rộng (Scalability)** – Amazon SQS có thể xử lý từng [request được đệm (buffered request)](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-client-side-buffering-request-batching.html) một cách độc lập, tự động mở rộng một cách trong suốt (transparently) để xử lý mọi mức tăng tải hoặc đột biến tải (spike) mà không cần bất kỳ chỉ dẫn cấp phát (provisioning) nào.
- **Độ tin cậy (Reliability)** – Amazon SQS khóa (lock) message của bạn trong quá trình xử lý, nhờ đó nhiều producer có thể gửi và nhiều consumer có thể nhận message cùng một lúc.
- **Tùy biến (Customization)** – Các queue của bạn không nhất thiết phải giống hệt nhau — ví dụ, bạn có thể [đặt độ trễ mặc định (default delay) cho một queue](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-delay-queues.html). Bạn có thể lưu trữ nội dung của các message lớn hơn 1 MiB [bằng Amazon Simple Storage Service (Amazon S3)](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-s3-messages.html) hoặc Amazon DynamoDB, với Amazon SQS giữ một con trỏ (pointer) tới object trong Amazon S3, hoặc bạn có thể chia một message lớn thành các message nhỏ hơn.

## Kiến trúc cơ bản của Amazon SQS

Tìm hiểu về các thành phần của một hệ thống nhắn tin phân tán (distributed messaging system) và vòng đời của một Amazon SQS message, từ lúc tạo đến lúc xóa.

Phần này mô tả các thành phần của một hệ thống nhắn tin phân tán và giải thích vòng đời của một Amazon SQS message.

### Queue phân tán (Distributed queues)

Có ba phần chính trong một hệ thống nhắn tin phân tán: **các thành phần của hệ thống phân tán của bạn**, **queue** của bạn (được phân tán trên các máy chủ Amazon SQS), và **các message trong queue**.

Trong kịch bản sau, hệ thống của bạn có nhiều *producer* (các thành phần gửi message vào queue) và *consumer* (các thành phần nhận message từ queue). Queue (chứa các message từ A đến E) lưu trữ các message một cách dự phòng (redundantly) trên nhiều máy chủ Amazon SQS.

![Ba phần chính trong một hệ thống nhắn tin phân tán: các thành phần của hệ thống phân tán của bạn, queue của bạn (được phân tán trên các máy chủ Amazon SQS), và các message trong queue.](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/images/ArchOverview.png)

### Vòng đời của message (Message lifecycle)

Kịch bản sau mô tả vòng đời của một Amazon SQS message trong queue, từ lúc tạo đến lúc xóa.

![Vòng đời của một Amazon SQS message trong queue, từ lúc tạo đến lúc xóa.](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/images/sqs-message-lifecycle-diagram.png)

**(1)** Một producer (Component 1) gửi message A vào queue, và message được phân tán một cách dự phòng trên các máy chủ Amazon SQS.

**(2)** Khi một consumer (Component 2) sẵn sàng xử lý message, nó tiêu thụ message từ queue, và message A được trả về. Trong khi message A đang được xử lý, nó vẫn nằm trong queue và không được trả về cho các receive request tiếp theo trong suốt khoảng thời gian [visibility timeout](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-visibility-timeout.html).

**(3)** Consumer (Component 2) xóa message A khỏi queue để ngăn message bị nhận và xử lý lại khi visibility timeout hết hạn.

> **Lưu ý**
>
> Amazon SQS tự động xóa các message đã nằm trong queue lâu hơn thời gian lưu giữ message tối đa (maximum message retention period). Thời gian lưu giữ message mặc định là 4 ngày. Tuy nhiên, bạn có thể đặt thời gian lưu giữ message thành một giá trị từ 60 giây đến 1.209.600 giây (14 ngày) bằng action [`SetQueueAttributes`](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/APIReference/API_SetQueueAttributes.html).

## Sự khác biệt giữa Amazon SQS, Amazon MQ và Amazon SNS

Amazon SQS, [Amazon SNS](https://aws.amazon.com/sns/) và [Amazon MQ](https://aws.amazon.com/amazon-mq/) cung cấp các dịch vụ nhắn tin được quản lý (managed messaging services) có khả năng mở rộng cao và dễ sử dụng, mỗi dịch vụ được thiết kế cho những vai trò cụ thể trong các hệ thống phân tán. Dưới đây là phần tổng quan nâng cao về sự khác biệt giữa các dịch vụ này:

**Amazon SQS** tách rời (decouple) và mở rộng các hệ thống và thành phần phần mềm phân tán với vai trò là một dịch vụ hàng đợi (queue service). Nó thường xử lý message thông qua một subscriber duy nhất, lý tưởng cho các workflow mà thứ tự và việc ngăn mất message là yếu tố then chốt. Để phân phối rộng hơn, việc tích hợp Amazon SQS với Amazon SNS cho phép [mô hình nhắn tin fanout (fanout messaging pattern)](https://aws.amazon.com/getting-started/hands-on/send-fanout-event-notifications/), đẩy message tới nhiều subscriber cùng lúc một cách hiệu quả.

**Amazon SNS** cho phép publisher gửi message tới nhiều subscriber thông qua các topic, đóng vai trò là các kênh giao tiếp. Subscriber nhận các message được publish bằng một loại endpoint được hỗ trợ, chẳng hạn như [Amazon Data Firehose](https://docs.aws.amazon.com/firehose/latest/dev/what-is-this-service.html), [Amazon SQS](#amazon-simple-queue-service-là-gì), [Lambda](https://docs.aws.amazon.com/lambda/latest/dg/welcome.html), HTTP, email, thông báo đẩy trên thiết bị di động (mobile push notifications) và tin nhắn văn bản trên di động (SMS). Dịch vụ này lý tưởng cho các kịch bản cần thông báo tức thời, chẳng hạn như tương tác người dùng theo thời gian thực hoặc hệ thống cảnh báo (alarm). Để ngăn mất message khi subscriber offline, việc tích hợp Amazon SNS với Amazon SQS queue đảm bảo việc phân phối nhất quán.

**Amazon MQ** phù hợp nhất với các doanh nghiệp muốn di chuyển (migrate) từ các message broker truyền thống, hỗ trợ các giao thức nhắn tin tiêu chuẩn như AMQP và MQTT, cùng với [Apache ActiveMQ](http://activemq.apache.org/) và [RabbitMQ](https://www.rabbitmq.com/). Nó mang lại khả năng tương thích với các hệ thống cũ (legacy) cần nhắn tin ổn định, tin cậy mà không phải cấu hình lại đáng kể.

Bảng sau cung cấp tổng quan về loại tài nguyên của từng dịch vụ:

| Loại tài nguyên (Resource type) | Amazon SNS | Amazon SQS | Amazon MQ |
| --- | --- | --- | --- |
| Đồng bộ (Synchronous) | Không | Không | Có |
| Bất đồng bộ (Asynchronous) | Có | Có | Có |
| Hàng đợi (Queues) | Không | Có | Có |
| Nhắn tin publisher-subscriber | Có | Không | Có |
| Message broker | Không | Không | Có |

Cả Amazon SQS và Amazon SNS đều được khuyến nghị cho các ứng dụng mới có thể tận dụng khả năng mở rộng gần như không giới hạn và các API đơn giản. Chúng thường mang lại giải pháp tiết kiệm chi phí hơn cho các ứng dụng có khối lượng lớn nhờ mô hình định giá trả theo mức sử dụng (pay-as-you-go). Chúng tôi khuyến nghị Amazon MQ cho việc di chuyển các ứng dụng từ các message broker hiện có vốn dựa vào khả năng tương thích với các API như JMS hoặc các giao thức như Advanced Message Queuing Protocol (AMQP), MQTT, OpenWire và Simple Text Oriented Message Protocol (STOMP).

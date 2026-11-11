# Quản lý các Amazon SQS message lớn bằng Extended Client Library và Amazon Simple Storage Service (Managing large Amazon SQS messages with Extended Client Library and Amazon Simple Storage Service)

> Nguồn: [Managing large Amazon SQS messages with Extended Client Library and Amazon Simple Storage Service](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-managing-large-messages.html)

Sử dụng [Amazon SQS Extended Client Library for Java](https://github.com/awslabs/amazon-sqs-java-extended-client-lib) và [Amazon SQS Extended Client Library for Python](https://github.com/awslabs/amazon-sqs-python-extended-client-lib/) để gửi các message lớn, đặc biệt là các payload có kích thước từ 256 KB đến 2 GB. Các thư viện này lưu trữ message payload trong một Amazon S3 bucket và gửi một tham chiếu (reference) tới object đã lưu trữ đó vào Amazon SQS queue.

> **Lưu ý (Note)**
>
> Các Amazon SQS Extended Client Library tương thích với cả standard queue và FIFO queue.

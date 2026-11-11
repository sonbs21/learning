# Cấu hình mã hóa phía máy chủ cho queue bằng Amazon SQS console (Configuring server-side encryption for a queue using the Amazon SQS console)

> Nguồn: [Configuring server-side encryption for a queue using the Amazon SQS console](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-configure-sse-existing-queue.html)

Để bảo vệ dữ liệu trong các message của queue, Amazon SQS bật mã hóa phía máy chủ (server-side encryption – SSE) theo mặc định cho tất cả các queue mới được tạo. Amazon SQS tích hợp với Amazon Web Services Key Management Service (Amazon Web Services KMS) để quản lý các [KMS key](https://docs.aws.amazon.com/kms/latest/developerguide/concepts.html#master_keys) cho mã hóa phía máy chủ (SSE). Để biết thông tin về việc sử dụng SSE, xem [Encryption at rest in Amazon SQS](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-server-side-encryption.html).

KMS key mà bạn gán cho queue của mình phải có một key policy bao gồm permission cho tất cả các principal được ủy quyền sử dụng queue. Để biết thông tin, xem [Key Management](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-key-management.html).

Nếu bạn không phải là chủ sở hữu (owner) của KMS key, hoặc nếu bạn đăng nhập bằng một account không có các permission `kms:ListAliases` và `kms:DescribeKey`, bạn sẽ không thể xem thông tin về KMS key trên Amazon SQS console. Hãy yêu cầu chủ sở hữu của KMS key cấp cho bạn các permission này. Để biết thêm thông tin, xem [Key Management](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-key-management.html).

Khi bạn [tạo](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/creating-sqs-standard-queues.html#step-create-standard-queue) hoặc [chỉnh sửa](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-configure-edit-queue.html) một queue, bạn có thể cấu hình SSE-KMS.

**Để cấu hình SSE-KMS cho một queue hiện có (console)**

1. Mở Amazon SQS console tại [https://console.aws.amazon.com/sqs/](https://console.aws.amazon.com/sqs/).
2. Trong khung điều hướng (navigation pane), chọn **Queues**.
3. Chọn một queue, sau đó chọn **Edit**.
4. Mở rộng **Encryption**.
5. Với **Server-side encryption**, chọn **Enabled** (mặc định).

   > **Lưu ý (Note)**
   >
   > Khi SSE được bật, các request `SendMessage` và `ReceiveMessage` ẩn danh (anonymous) gửi tới queue được mã hóa sẽ bị từ chối. Các best practice bảo mật của Amazon SQS khuyến nghị không sử dụng request ẩn danh. Nếu bạn muốn gửi request ẩn danh tới một Amazon SQS queue, hãy đảm bảo tắt SSE.

6. Chọn **AWS Key Management Service key (SSE-KMS)**.

   Console hiển thị **Description**, **Account** và **KMS key ARN** của KMS key.
7. Chỉ định KMS key ID cho queue. Để biết thêm thông tin, xem [Key terms](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-server-side-encryption.html#sqs-sse-key-terms).
   1. Chọn tùy chọn **Choose a KMS key alias**.
   2. Key mặc định là Amazon Web Services managed KMS key cho Amazon SQS. Để sử dụng key này, chọn nó từ danh sách **KMS key**.
   3. Để sử dụng một custom KMS key từ Amazon Web Services account của bạn, chọn nó từ danh sách **KMS key**. Để xem hướng dẫn tạo custom KMS key, xem [Creating Keys](https://docs.aws.amazon.com/kms/latest/developerguide/create-keys.html) trong *Amazon Web Services Key Management Service Developer Guide*.
   4. Để sử dụng một custom KMS key không có trong danh sách, hoặc một custom KMS key từ một Amazon Web Services account khác, chọn **Enter the KMS key alias** và nhập Amazon Resource Name (ARN) của KMS key.
8. (Tùy chọn) Với **Data key reuse period**, chỉ định một giá trị trong khoảng từ 1 phút đến 24 giờ. Mặc định là 5 phút. Để biết thêm thông tin, xem [Understanding the data key reuse period](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-key-management.html#sqs-how-does-the-data-key-reuse-period-work).
9. Khi bạn hoàn tất cấu hình SSE-KMS, chọn **Save**.

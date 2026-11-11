# Cấu hình mã hóa phía máy chủ cho queue bằng khóa mã hóa do SQS quản lý (Configuring server-side encryption for a queue using SQS-managed encryption keys)

> Nguồn: [Configuring server-side encryption for a queue using SQS-managed encryption keys](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-configure-sqs-sse-queue.html)

Ngoài tùy chọn mã hóa phía máy chủ (server-side encryption – SSE) do Amazon SQS quản lý [mặc định](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/creating-sqs-standard-queues.html#step-create-standard-queue), Amazon SQS managed SSE (SSE-SQS) cho phép bạn tạo mã hóa phía máy chủ được quản lý tùy chỉnh, sử dụng các khóa mã hóa do SQS quản lý (SQS-managed encryption key) để bảo vệ dữ liệu nhạy cảm được gửi qua các message queue. Với SSE-SQS, bạn không cần tạo và quản lý các khóa mã hóa, hoặc sửa đổi code để mã hóa dữ liệu. SSE-SQS cho phép bạn truyền dữ liệu một cách an toàn và giúp bạn đáp ứng các yêu cầu nghiêm ngặt về tuân thủ mã hóa (encryption compliance) và quy định pháp lý (regulatory requirement) mà không phát sinh thêm chi phí.

SSE-SQS bảo vệ dữ liệu lưu trữ (data at rest) bằng mã hóa Advanced Encryption Standard 256-bit (AES-256). SSE mã hóa message ngay khi Amazon SQS nhận được chúng. Amazon SQS lưu trữ message ở dạng đã mã hóa và chỉ giải mã chúng khi gửi tới một consumer được ủy quyền (authorized).

> **Lưu ý (Note)**
>
> Tùy chọn SSE mặc định chỉ có hiệu lực khi bạn tạo một queue mà không chỉ định các attribute mã hóa.
>
> Amazon SQS cho phép bạn tắt toàn bộ việc mã hóa queue. Do đó, việc tắt KMS-SSE sẽ không tự động bật SQS-SSE. Nếu bạn muốn bật SQS-SSE sau khi tắt KMS-SSE, bạn phải thêm một thay đổi attribute vào request.

**Để cấu hình mã hóa SSE-SQS cho một queue (console)**

> **Lưu ý (Note)**
>
> Bất kỳ queue mới nào được tạo bằng endpoint HTTP (không phải TLS) sẽ không bật mã hóa SSE-SQS theo mặc định. Một best practice về bảo mật là tạo các Amazon SQS queue bằng endpoint HTTPS hoặc [Signature Version 4](https://docs.aws.amazon.com/general/latest/gr/signature-version-4.html).

1. Mở Amazon SQS console tại [https://console.aws.amazon.com/sqs/](https://console.aws.amazon.com/sqs/).
2. Trong khung điều hướng (navigation pane), chọn **Queues**.
3. Chọn một queue, sau đó chọn **Edit**.
4. Mở rộng **Encryption**.
5. Với **Server-side encryption**, chọn **Enabled** (mặc định).

   > **Lưu ý (Note)**
   >
   > Khi SSE được bật, các request `SendMessage` và `ReceiveMessage` ẩn danh (anonymous) gửi tới queue được mã hóa sẽ bị từ chối. Các best practice bảo mật của Amazon SQS khuyến nghị không sử dụng request ẩn danh. Nếu bạn muốn gửi request ẩn danh tới một Amazon SQS queue, hãy đảm bảo tắt SSE.

6. Chọn **Amazon SQS key (SSE-SQS)**. Không có phí bổ sung khi sử dụng tùy chọn này.
7. Chọn **Save**.

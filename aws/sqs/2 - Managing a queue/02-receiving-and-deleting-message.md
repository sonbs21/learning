# Nhận và xóa một message trong Amazon SQS (Receiving and deleting a message in Amazon SQS)

> Nguồn: [Receiving and deleting a message in Amazon SQS](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/step-receive-delete-message.html)

Sau khi gửi message tới một Amazon SQS queue, bạn có thể truy xuất (retrieve) và xóa chúng để xử lý workflow của ứng dụng. Quy trình này đảm bảo việc xử lý message an toàn và tin cậy. Chủ đề này hướng dẫn bạn truy xuất và xóa message bằng Amazon SQS console và giải thích các thiết lập quan trọng để tối ưu thao tác này. Sau đây là các khái niệm chính về việc nhận và xóa message:

1. **Nhận message (Receiving messages)**
   - Khi bạn truy xuất message từ một Amazon SQS queue, bạn không thể nhắm tới các message cụ thể. Thay vào đó, hãy chỉ định số lượng message tối đa cần truy xuất trong một request duy nhất (tối đa 10).
   - Do bản chất phân tán của Amazon SQS, việc truy xuất từ một queue có ít message có thể trả về một response rỗng. Để giảm thiểu điều này:
     - Sử dụng long polling, cơ chế này chờ cho đến khi có message sẵn sàng hoặc poll hết thời gian chờ (time out). Cách tiếp cận này giảm chi phí polling không cần thiết và cải thiện hiệu quả.
     - Gửi lại request nếu cần.
2. **Khả năng hiển thị và việc xóa message (Message visibility and deletion)**
   - Message không bị xóa tự động sau khi được truy xuất. Tính năng này đảm bảo bạn có thể xử lý lại message trong trường hợp ứng dụng gặp lỗi hoặc gián đoạn mạng.
   - Sau khi xử lý, bạn phải gửi một delete request một cách tường minh (explicitly) để xóa message vĩnh viễn. Hành động này xác nhận việc xử lý thành công.
   - Các message được truy xuất bằng Amazon SQS console vẫn hiển thị (visible) để có thể truy xuất lại. Hãy điều chỉnh thiết lập visibility timeout cho các môi trường tự động để tạm thời ẩn message khỏi các consumer khác trong khi chúng đang được xử lý.
3. **Visibility timeout**
   - Thiết lập này xác định thời gian một message vẫn bị ẩn sau khi được truy xuất. Hãy đặt một timeout phù hợp để đảm bảo message chỉ được xử lý một lần và để ngăn trùng lặp trong quá trình xử lý phân tán.

**Để nhận và xóa một message bằng console**

1. Mở Amazon SQS console tại [https://console.aws.amazon.com/sqs/](https://console.aws.amazon.com/sqs/).
2. Trong khung điều hướng (navigation pane), chọn **Queues**.
3. Trên trang **Queues**, chọn **queue** mà bạn muốn nhận message, sau đó chọn **Send and receive messages**.
4. Trên trang **Send and receive messages**, chọn **Poll for messages**.

   Amazon SQS hiển thị một thanh tiến trình (progress bar) cho biết thời lượng polling. Các message được truy xuất sẽ xuất hiện trong phần **Messages**, hiển thị:
   - Message ID
   - Ngày gửi (Sent date)
   - Kích thước (Size)
   - Số lần nhận (Receive count)
5. Để xóa message, chọn những message bạn muốn xóa và chọn **Delete**.

   Xác nhận việc xóa trong hộp thoại **Delete Messages** bằng cách chọn **Delete**.

Để biết thêm chi tiết về các thao tác nâng cao, bao gồm truy xuất và xóa message dựa trên API, xem [Amazon SQS API Reference Guide](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/APIReference/API_Operations.html).

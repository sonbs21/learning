# Các quota của Amazon SQS FIFO queue (Amazon SQS FIFO queue quotas)

> Nguồn: [Amazon SQS FIFO queue quotas](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/quotas-fifo.html)

## Các quota của Amazon SQS (Amazon SQS quotas)

Bảng sau liệt kê các quota liên quan đến FIFO queue.

| Quota | Mô tả |
| --- | --- |
| **Delay queue** | Độ trễ (delay) mặc định (tối thiểu) cho một queue là 0 giây. Tối đa là 15 phút. |
| **Listed queues** | 1.000 queue cho mỗi request [`ListQueues`](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/APIReference/API_ListQueues.html). |
| **Long polling wait time** | Thời gian chờ long polling tối đa là 20 giây. |
| **Message groups** | Không có quota giới hạn số lượng message group bên trong một FIFO queue. |
| **Messages per queue (backlog)** | Số lượng message mà một Amazon SQS queue có thể lưu trữ là không giới hạn. |
| **Messages per queue (in flight)** | FIFO queue hỗ trợ tối đa 120.000 message in-flight (các message đã được consumer nhận nhưng chưa bị xóa). Nếu đạt tới giới hạn này, Amazon SQS không trả về lỗi, nhưng việc xử lý có thể bị ảnh hưởng. Bạn có thể yêu cầu tăng vượt quá giới hạn này bằng cách liên hệ [AWS Support](https://docs.aws.amazon.com/awssupport/latest/user/create-service-quota-increase.html). |
| **Queue name** | Tên của một FIFO queue phải kết thúc bằng hậu tố (suffix) `.fifo`. Hậu tố này được tính vào quota 80 ký tự của tên queue. Để xác định một queue có phải là [FIFO](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-fifo-queues.html) hay không, bạn có thể kiểm tra xem tên queue có kết thúc bằng hậu tố này hay không. |
| **Queue tag** | Chúng tôi không khuyến nghị thêm quá 50 tag cho một queue. Việc gắn tag hỗ trợ các ký tự Unicode trong UTF-8.<br><br>Tag Key là bắt buộc, nhưng tag Value là tùy chọn.<br><br>Tag Key và tag Value phân biệt chữ hoa chữ thường (case-sensitive).<br><br>Tag Key và tag Value có thể bao gồm các ký tự chữ và số (alphanumeric) Unicode trong UTF-8 và khoảng trắng. Các ký tự đặc biệt sau được cho phép: `_ . : / = + - @`<br><br>Tag Key hoặc Value không được chứa tiền tố dành riêng (reserved prefix) `aws:` (bạn không thể xóa các tag key hoặc value có tiền tố này).<br><br>Độ dài tối đa của tag Key là 128 ký tự Unicode trong UTF-8. Tag Key không được rỗng (empty) hoặc null.<br><br>Độ dài tối đa của tag Value là 256 ký tự Unicode trong UTF-8. Tag Value có thể rỗng hoặc null.<br><br>Các action gắn tag bị giới hạn ở 30 TPS cho mỗi AWS account. Nếu ứng dụng của bạn yêu cầu thông lượng cao hơn, hãy [gửi yêu cầu](https://console.aws.amazon.com/servicequotas/home/services/sqs/quotas). |

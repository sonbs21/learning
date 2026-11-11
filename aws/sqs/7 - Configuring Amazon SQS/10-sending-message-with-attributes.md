# Gửi message kèm attribute bằng Amazon SQS (Sending a message with attributes using Amazon SQS)

> Nguồn: [Sending a message with attributes using Amazon SQS](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-using-send-message-with-attributes.html)

Đối với standard queue và FIFO queue, bạn có thể đính kèm metadata có cấu trúc vào message, bao gồm timestamp, dữ liệu không gian địa lý (geospatial data), chữ ký (signature), và định danh (identifier). Để biết thêm thông tin, xem [Amazon SQS message attributes](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-message-metadata.html#sqs-message-attributes).

**Để gửi một message kèm attribute tới một queue bằng Amazon SQS console**

1. Mở Amazon SQS console tại [https://console.aws.amazon.com/sqs/](https://console.aws.amazon.com/sqs/).
2. Trong khung điều hướng (navigation pane), chọn **Queues**.
3. Trên trang **Queues**, chọn một queue.
4. Chọn **Send and receive messages**.
5. Nhập các tham số của message attribute.
   1. Trong ô văn bản name, nhập một tên duy nhất (unique) dài tối đa 256 ký tự.
   2. Với kiểu attribute (attribute type), chọn **String**, **Number**, hoặc **Binary**.
   3. (Tùy chọn) Nhập một kiểu dữ liệu tùy chỉnh (custom data type). Ví dụ, bạn có thể thêm **byte**, **int**, hoặc **float** làm kiểu dữ liệu tùy chỉnh cho **Number**.
   4. Trong ô văn bản value, nhập giá trị của message attribute.

   ![Amazon SQS console hiển thị phần Message attributes.](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/images/sqs-tutorials-sending-message-with-attributes.png)
6. Để thêm một message attribute khác, chọn **Add new attribute**.

   ![Amazon SQS console hiển thị nút Remove trong phần Message attributes.](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/images/sqs-tutorials-sending-message-with-attributes-custom-attribute.png)
7. Bạn có thể sửa đổi các giá trị attribute bất cứ lúc nào trước khi gửi message.
8. Để xóa một attribute, chọn **Remove**. Để xóa attribute đầu tiên, đóng **Message attributes**.
9. Khi bạn hoàn tất việc thêm attribute vào message, chọn **Send message**. Message của bạn được gửi và console hiển thị một thông báo thành công. Để xem thông tin về các message attribute của message đã gửi, chọn **View details**. Chọn **Done** để đóng hộp thoại **Message details**.

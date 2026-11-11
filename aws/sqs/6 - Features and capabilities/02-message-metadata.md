# Metadata của message trong Amazon SQS (Message metadata for Amazon SQS)

> Nguồn: [Message metadata for Amazon SQS](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-message-metadata.html)

Sử dụng message attribute để thêm metadata tùy chỉnh vào các Amazon SQS message cho ứng dụng của bạn. Sử dụng message system attribute để lưu trữ metadata phục vụ tích hợp với các AWS service khác, chẳng hạn như AWS X-Ray.

## Các message attribute của Amazon SQS (Amazon SQS message attributes)

Amazon SQS cho phép bạn đính kèm metadata có cấu trúc (chẳng hạn như timestamp, dữ liệu không gian địa lý – geospatial data, chữ ký – signature, và định danh – identifier) với message bằng cách sử dụng *message attribute*. Mỗi message có thể có tối đa 10 attribute. Message attribute là tùy chọn và tách biệt với message body (tuy nhiên, chúng được gửi kèm theo message body). Consumer của bạn có thể sử dụng message attribute để xử lý một message theo một cách cụ thể mà không cần phải xử lý message body trước. Để biết thông tin về việc gửi message kèm attribute bằng Amazon SQS console, xem [Sending a message with attributes using Amazon SQS](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-using-send-message-with-attributes.html).

> **Lưu ý (Note)**
>
> Đừng nhầm lẫn message attribute với *message system attribute*: Trong khi bạn có thể sử dụng message attribute để đính kèm metadata tùy chỉnh vào các Amazon SQS message cho ứng dụng của bạn, bạn có thể sử dụng [message system attribute](#các-message-system-attribute-của-amazon-sqs-amazon-sqs-message-system-attributes) để lưu trữ metadata cho các AWS service khác, chẳng hạn như AWS X-Ray.

**Các chủ đề (Topics)**

- [Message attribute components](#các-thành-phần-của-message-attribute-message-attribute-components)
- [Message attribute data types](#các-kiểu-dữ-liệu-của-message-attribute-message-attribute-data-types)
- [Calculating the MD5 message digest for message attributes](#tính-toán-md5-message-digest-cho-message-attribute-calculating-the-md5-message-digest-for-message-attributes)

### Các thành phần của message attribute (Message attribute components)

> **Quan trọng (Important)**
>
> Tất cả các thành phần của một message attribute đều được tính vào giới hạn kích thước message 1 MiB.
>
> `Name`, `Type`, `Value` và message body không được rỗng (empty) hoặc null.

Mỗi message attribute bao gồm các thành phần sau:

- **Name** – Tên message attribute có thể chứa các ký tự sau: `A`-`Z`, `a`-`z`, `0`-`9`, dấu gạch dưới (`_`), dấu gạch ngang (`-`), và dấu chấm (`.`). Các giới hạn sau được áp dụng:
  - Có thể dài tối đa 256 ký tự
  - Không được bắt đầu bằng `AWS.` hoặc `Amazon.` (hoặc bất kỳ biến thể chữ hoa/chữ thường nào)
  - Phân biệt chữ hoa chữ thường (case-sensitive)
  - Phải là duy nhất (unique) trong số tất cả các tên attribute của message
  - Không được bắt đầu hoặc kết thúc bằng dấu chấm
  - Không được có các dấu chấm liên tiếp nhau
- **Type** – Kiểu dữ liệu của message attribute. Các kiểu được hỗ trợ bao gồm `String`, `Number` và `Binary`. Bạn cũng có thể thêm thông tin tùy chỉnh cho bất kỳ kiểu dữ liệu nào. Kiểu dữ liệu có cùng các giới hạn như message body (để biết thêm thông tin, xem [`SendMessage`](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/APIReference/API_SendMessage.html) trong *Amazon Simple Queue Service API Reference*). Ngoài ra, các giới hạn sau được áp dụng:
  - Có thể dài tối đa 256 ký tự
  - Phân biệt chữ hoa chữ thường (case-sensitive)
- **Value** – Giá trị của message attribute. Đối với kiểu dữ liệu `String`, giá trị attribute có cùng các giới hạn như message body.

### Các kiểu dữ liệu của message attribute (Message attribute data types)

Các kiểu dữ liệu của message attribute chỉ dẫn Amazon SQS cách xử lý các giá trị message attribute tương ứng. Ví dụ, nếu kiểu là `Number`, Amazon SQS sẽ kiểm tra tính hợp lệ (validate) của các giá trị số.

Amazon SQS hỗ trợ các kiểu dữ liệu logic `String`, `Number` và `Binary` với các nhãn kiểu dữ liệu tùy chỉnh (custom data type label) tùy chọn theo định dạng `.custom-data-type`

- **String** – Các attribute kiểu `String` có thể lưu trữ văn bản Unicode sử dụng bất kỳ ký tự XML hợp lệ nào.
- **Number** – Các attribute kiểu `Number` có thể lưu trữ các giá trị số dương hoặc âm. Một số có thể có độ chính xác tối đa 38 chữ số, và có thể nằm trong khoảng từ 10^-128 đến 10^+126.

  > **Lưu ý (Note)**
  >
  > Amazon SQS loại bỏ các số 0 ở đầu và ở cuối (leading and trailing zeroes).

- **Binary** – Các attribute kiểu Binary có thể lưu trữ bất kỳ dữ liệu nhị phân nào, chẳng hạn như dữ liệu đã nén, dữ liệu đã mã hóa hoặc hình ảnh.
- **Custom** – Để tạo một kiểu dữ liệu tùy chỉnh, hãy nối thêm (append) một nhãn custom-type vào bất kỳ kiểu dữ liệu nào. Ví dụ:
  - `Number.byte`, `Number.short`, `Number.int` và `Number.float` có thể giúp phân biệt giữa các kiểu số.
  - `Binary.gif` và `Binary.png` có thể giúp phân biệt giữa các loại file.

  > **Lưu ý (Note)**
  >
  > Amazon SQS không diễn giải, kiểm tra tính hợp lệ hay sử dụng dữ liệu được nối thêm này.
  >
  > Nhãn custom-type có cùng các giới hạn như message body.

### Tính toán MD5 message digest cho message attribute (Calculating the MD5 message digest for message attributes)

Nếu bạn sử dụng AWS SDK for Java, bạn có thể bỏ qua phần này. Class `MessageMD5ChecksumHandler` của SDK for Java hỗ trợ MD5 message digest cho các Amazon SQS message attribute.

Nếu bạn sử dụng Query API hoặc một trong các AWS SDK không hỗ trợ MD5 message digest cho các Amazon SQS message attribute, bạn phải sử dụng các hướng dẫn sau để thực hiện việc tính toán MD5 message digest.

> **Lưu ý (Note)**
>
> Luôn bao gồm các hậu tố kiểu dữ liệu tùy chỉnh (custom data type suffix) trong việc tính toán MD5 message-digest.

#### Tổng quan (Overview)

Sau đây là tổng quan về thuật toán tính toán MD5 message digest:

1. Sắp xếp tất cả các message attribute theo tên theo thứ tự tăng dần (ascending).
2. Mã hóa (encode) từng phần riêng lẻ của mỗi attribute (`Name`, `Type` và `Value`) vào một buffer.
3. Tính message digest của toàn bộ buffer.

Sơ đồ sau cho thấy việc mã hóa MD5 message digest cho một message attribute đơn lẻ:

![Mã hóa MD5 message digest cho một message attribute đơn lẻ.](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/images/sqs-msg-attrib-md5.png)

#### Để mã hóa một Amazon SQS message attribute đơn lẻ (To encode a single Amazon SQS message attribute)

1. Mã hóa tên (name): độ dài (4 byte) và các byte UTF-8 của tên.
2. Mã hóa kiểu dữ liệu (data type): độ dài (4 byte) và các byte UTF-8 của kiểu dữ liệu.
3. Mã hóa kiểu truyền tải (transport type) (`String` hoặc `Binary`) của giá trị (1 byte).

   > **Lưu ý (Note)**
   >
   > Các kiểu dữ liệu logic `String` và `Number` sử dụng transport type `String`.
   >
   > Kiểu dữ liệu logic `Binary` sử dụng transport type `Binary`.

   1. Đối với transport type `String`, mã hóa 1.
   2. Đối với transport type `Binary`, mã hóa 2.
4. Mã hóa giá trị attribute.
   1. Đối với transport type `String`, mã hóa giá trị attribute: độ dài (4 byte) và các byte UTF-8 của giá trị.
   2. Đối với transport type `Binary`, mã hóa giá trị attribute: độ dài (4 byte) và các byte thô (raw bytes) của giá trị.

## Các message system attribute của Amazon SQS (Amazon SQS message system attributes)

Trong khi bạn có thể sử dụng [message attribute](#các-message-attribute-của-amazon-sqs-amazon-sqs-message-attributes) để đính kèm metadata tùy chỉnh vào các Amazon SQS message cho ứng dụng của bạn, bạn có thể sử dụng *message system attribute* để lưu trữ metadata cho các AWS service khác, chẳng hạn như AWS X-Ray. Để biết thêm thông tin, xem tham số request `MessageSystemAttribute` của các API action [`SendMessage`](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/APIReference/API_SendMessage.html) và [`SendMessageBatch`](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/APIReference/API_SendMessageBatch.html), attribute `AWSTraceHeader` của API action [`ReceiveMessage`](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/APIReference/API_ReceiveMessage.html), và kiểu dữ liệu [`MessageSystemAttributeValue`](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/APIReference/API_MessageSystemAttributeValue.html) trong *Amazon Simple Queue Service API Reference*.

Message system attribute có cấu trúc giống hệt message attribute, với các ngoại lệ sau:

- Hiện tại, message system attribute duy nhất được hỗ trợ là `AWSTraceHeader`. Kiểu của nó phải là `String` và giá trị của nó phải là một chuỗi AWS X-Ray trace header có định dạng đúng.
- Kích thước của một message system attribute không được tính vào tổng kích thước của message.

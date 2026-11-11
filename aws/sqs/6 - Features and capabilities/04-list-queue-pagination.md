# Phân trang khi liệt kê queue trong Amazon SQS (Amazon SQS list queue pagination)

> Nguồn: [Amazon SQS list queue pagination](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/list-all-queues-pagination.html)

Các API method [`listQueues`](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/APIReference/API_ListQueues.html) và [`listDeadLetterQueues`](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/APIReference/API_ListDeadLetterSourceQueues.html) hỗ trợ các điều khiển phân trang (pagination) tùy chọn. Theo mặc định, các API method này trả về tối đa 1000 queue trong response message. Bạn có thể đặt tham số `MaxResults` để trả về ít kết quả hơn trong mỗi response.

Đặt tham số `MaxResults` trong request `listQueues` hoặc `listDeadLetterQueues` để chỉ định số lượng kết quả tối đa được trả về trong response. Nếu bạn không đặt `MaxResults`, response bao gồm tối đa 1.000 kết quả và giá trị `NextToken` trong response là null.

Nếu bạn đặt `MaxResults`, response bao gồm một giá trị cho `NextToken` nếu còn kết quả bổ sung cần hiển thị. Sử dụng `NextToken` làm tham số trong request tiếp theo tới `listQueues` để nhận trang kết quả tiếp theo. Nếu không còn kết quả bổ sung nào cần hiển thị, giá trị `NextToken` trong response là null.

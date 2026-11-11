# Kiểm soát truy cập dựa trên thuộc tính cho Amazon SQS (Attribute-based access control for Amazon SQS)

> Nguồn: [Attribute-based access control for Amazon SQS](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-abac.html)

## ABAC là gì? (What is ABAC?)

Kiểm soát truy cập dựa trên thuộc tính (Attribute-based access control – ABAC) là một quy trình phân quyền (authorization) định nghĩa các permission dựa trên các tag được gắn vào user và AWS resource. ABAC cung cấp khả năng kiểm soát truy cập chi tiết (granular) và linh hoạt dựa trên các thuộc tính (attribute) và giá trị, giảm rủi ro bảo mật liên quan đến các policy dựa trên role (role-based policy) bị cấu hình lại, và tập trung hóa việc kiểm toán (auditing) cũng như quản lý access policy. Để biết thêm chi tiết về ABAC, xem [What is ABAC for AWS](https://docs.aws.amazon.com/IAM/latest/UserGuide/introduction_attribute-based-access-control.html) trong *IAM User Guide*.

Amazon SQS hỗ trợ ABAC bằng cách cho phép bạn kiểm soát quyền truy cập vào các Amazon SQS queue dựa trên các tag và alias được gắn với một Amazon SQS queue. Các condition key về tag và alias cho phép ABAC trong Amazon SQS sẽ cấp quyền cho các IAM principal sử dụng Amazon SQS queue mà không cần chỉnh sửa policy hay quản lý các grant. Để tìm hiểu thêm về các condition key của ABAC, xem [Condition keys for Amazon SQS](https://docs.aws.amazon.com/service-authorization/latest/reference/list_amazonsqs.html#amazonsqs-policy-keys) trong *Service Authorization Reference*.

Với ABAC, bạn có thể sử dụng tag để cấu hình các IAM access permission và policy cho các Amazon SQS queue của mình, giúp bạn mở rộng quy mô việc quản lý permission. Bạn có thể tạo một permissions policy duy nhất trong IAM sử dụng các tag mà bạn thêm vào mỗi vai trò nghiệp vụ (business role) — mà không cần phải cập nhật policy mỗi khi bạn thêm một resource mới. Bạn cũng có thể gắn tag vào các IAM principal để tạo một ABAC policy. Bạn có thể thiết kế các ABAC policy để cho phép các thao tác Amazon SQS khi tag trên IAM user role đang thực hiện lời gọi khớp với tag của Amazon SQS queue. Để tìm hiểu thêm về việc gắn tag trong AWS, xem [AWS Tagging Strategies](https://docs.aws.amazon.com/general/latest/gr/aws_tagging.html) và [Amazon SQS cost allocation tags](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-queue-tags.html).

> **Lưu ý (Note)**
>
> ABAC cho Amazon SQS hiện đã khả dụng ở tất cả các AWS Commercial Region nơi Amazon SQS khả dụng, với các ngoại lệ sau:
>
> - Asia Pacific (Hyderabad)
> - Asia Pacific (Melbourne)
> - Europe (Spain)
> - Europe (Zurich)

## Tại sao tôi nên sử dụng ABAC trong Amazon SQS? (Why should I use ABAC in Amazon SQS?)

Sau đây là một số lợi ích của việc sử dụng ABAC trong Amazon SQS:

- ABAC cho Amazon SQS yêu cầu ít permissions policy hơn. Bạn không cần tạo các policy khác nhau cho các chức năng công việc (job function) khác nhau. Bạn có thể sử dụng resource tag và request tag áp dụng cho nhiều hơn một queue, giúp giảm chi phí vận hành (operational overhead).
- Sử dụng ABAC để mở rộng quy mô team một cách nhanh chóng. Permission cho các resource mới được tự động cấp dựa trên tag khi các resource được gắn tag phù hợp trong quá trình tạo chúng.
- Sử dụng permission trên IAM principal để giới hạn quyền truy cập resource. Bạn có thể tạo tag cho IAM principal và sử dụng chúng để giới hạn quyền truy cập vào các action cụ thể khớp với tag trên IAM principal. Điều này giúp bạn tự động hóa quy trình cấp request permission.
- Theo dõi ai đang truy cập resource của bạn. Bạn có thể xác định danh tính của một session bằng cách xem các user attribute trong AWS CloudTrail.

**Các chủ đề (Topics)**

- [What is ABAC?](#abac-là-gì-what-is-abac)
- [Why should I use ABAC in Amazon SQS?](#tại-sao-tôi-nên-sử-dụng-abac-trong-amazon-sqs-why-should-i-use-abac-in-amazon-sqs)
- [Tagging for access control](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-abac-tagging-resource-control.html)
- [Creating IAM users and Amazon SQS queues](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-abac-creating-queues.html)
- [Testing attribute-based access control](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-abac-testing-access-control.html)

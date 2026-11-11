# AWS Certified Solutions Architect – Associate (SAA-C03)
## Mock Exam 01

**Thời gian:** 130 phút  
**Số câu:** 65  
**Hình thức:** Multiple choice hoặc multiple response. Câu nhiều đáp án ghi rõ số lượng.

Không tra tài liệu trong 130 phút. Câu multiple response chỉ đúng khi chọn đủ và không chọn thừa. Đáp án và giải thích ở file `aws-saa-c03-mock-exam-01-answer-key.md`.

## Domain 1 — Design Secure Architectures

1. Một ứng dụng chạy trên EC2 cần đọc object từ S3. Vì lý do bảo mật, công ty cấm lưu access key dài hạn trên instance và muốn credential tự động xoay vòng. Giải pháp nào đáp ứng tốt nhất?  
A. IAM user key trong user data  
B. IAM role qua instance profile  
C. Key trong AMI  
D. Public bucket

2. Công ty đã có hệ thống danh tính doanh nghiệp và muốn nhân viên dùng tài khoản hiện có để truy cập AWS, đồng thời quản lý MFA tập trung. Giải pháp nào phù hợp nhất?  
A. IAM Identity Center federation với IdP  
B. IAM user riêng  
C. Access key chung  
D. Root user theo phòng ban

3. Một bucket S3 chứa dữ liệu nội bộ. Chính sách bảo mật yêu cầu bucket chỉ nhận request đi qua các VPC endpoint được chỉ định và chỉ trong Region được phê duyệt. Cấu hình nào phù hợp nhất?  
A. Security group cho S3  
B. Public subnet NACL  
C. Bucket policy giới hạn `aws:SourceVpce` và Region  
D. ACL public-read

4. Một ứng dụng cần lưu thông tin đăng nhập database. Mật khẩu phải được mã hóa, phân quyền theo IAM và tự động xoay vòng mà không sửa code thủ công. Dịch vụ nào phù hợp nhất?  
A. Glacier  
B. CloudTrail  
C. Cognito Identity Pool  
D. Secrets Manager  

5. Một REST API chạy trên API Gateway được public cho khách hàng Internet. Đội bảo mật muốn chặn các mẫu request độc hại phổ biến như SQL injection và cross-site scripting trước khi tới backend. Giải pháp nào phù hợp nhất?  
A. AWS WAF  
B. Security group  
C. Inspector  
D. S3 Block Public Access

6. Một tổ chức có nhiều AWS account và yêu cầu ghi nhận API activity trên mọi account, mọi Region vào một tài khoản audit tập trung để điều tra sau này. Kiến trúc nào phù hợp nhất?  
A. Chỉ VPC Flow Logs  
B. Organization trail gửi log về bucket audit  
C. CloudWatch trên EC2  
D. ALB access logs

7. EC2 trong private subnet chỉ cần truy cập S3. Công ty muốn traffic đi trên mạng AWS, không qua Internet Gateway, public IP hoặc NAT Gateway để giảm bề mặt tấn công và chi phí. Giải pháp nào phù hợp nhất?  
A. Internet Gateway trong private subnet  
B. Public IP  
C. S3 gateway endpoint  
D. VPC peering tới VPC public

8. Security team muốn đặt guardrail áp dụng cho toàn bộ member account: không được tắt CloudTrail và không được tạo resource ngoài danh sách Region được phê duyệt. Công cụ nào phù hợp nhất?  
A. Security group  
B. Permissions boundary trên một user  
C. S3 lifecycle  
D. SCP trong Organizations  

9. Một workload xử lý dữ liệu nhạy cảm trên EBS, RDS và S3. Compliance yêu cầu doanh nghiệp tự kiểm soát chính sách khóa mã hóa và audit mọi lần sử dụng khóa. Lựa chọn nào phù hợp nhất?  
A. KMS customer managed key + CloudTrail  
B. Hard-code key  
C. Chỉ TLS  
D. Security group

10. (Chọn HAI) Một developer role có thể được nhiều team chỉnh sửa policy. Security team muốn giới hạn quyền tối đa của role ngay cả khi policy của role vô tình cấp quá rộng. Hai biện pháp nào phù hợp?  
A. Secret trong AMI  
B. Permissions boundary  
C. Root user + MFA  
D. SCP ở account/OU  
E. Public subnet

11. Một mobile app cần xác thực hàng triệu người dùng và cho phép họ truy cập một số tài nguyên AWS bằng credential tạm thời. Công ty không muốn tạo IAM user hoặc access key cho từng người. Kiến trúc nào phù hợp nhất?  
A. Root user  
B. Cognito User Pool + Identity Pool  
C. Simple AD cho từng thiết bị  
D. CloudFront cookie

12. Doanh nghiệp có mạng on-premises và hàng chục VPC cần kết nối private với nhau. Kiến trúc phải tránh full-mesh peering và đơn giản hóa việc quản lý route. Giải pháp nào phù hợp nhất?  
A. Full-mesh VPC peering  
B. NAT Gateway giữa VPC  
C. Transit Gateway + VPN/Direct Connect  
D. Internet Gateway

13. (Chọn HAI) Một ứng dụng cho phép khách hàng tải xuống file từ S3. Bucket phải giữ private, còn URL tải xuống phải tự hết hạn sau một khoảng thời gian. Hai lựa chọn nào đáp ứng yêu cầu?  
A. CloudFront signed URL  
B. Public bucket policy  
C. Tắt Block Public Access  
D. S3 presigned URL  
E. Key không hết hạn

14. Một ECS task cần đọc một số item từ DynamoDB. Quyền phải gắn theo workload, không dùng access key hard-code và không cấp thêm quyền cho các task khác. Cách nào phù hợp nhất?  
A. Security group  
B. IAM key chung  
C. Root trong container  
D. ECS task role  

15. Một RDS PostgreSQL nằm trong private subnet. Yêu cầu là chỉ các instance thuộc application tier được kết nối tới port 5432; không được mở database cho Internet. Cấu hình nào phù hợp nhất?  
A. DB security group cho phép từ app security group  
B. `0.0.0.0/0`  
C. Internet NACL  
D. Public DB + password mạnh

16. Security team muốn tự động phát hiện bucket S3 không mã hóa và security group cho phép SSH từ Internet, sau đó tổng hợp các finding trên một dashboard bảo mật. Dịch vụ nào phù hợp nhất?  
A. Route 53  
B. Security Hub + AWS Config rules  
C. ElastiCache  
D. DataSync

17. Một ứng dụng cần đọc API token lúc runtime. Token không được xuất hiện trong source code hoặc AMI, và quyền đọc phải cấp bằng IAM. Lựa chọn nào phù hợp nhất?  
A. Private Git  
B. EC2 tag  
C. Secrets Manager hoặc Parameter Store  
D. Bucket name

18. (Chọn HAI) EC2 trong private subnet cần tải package từ Internet nhưng không được có public IP. Thiết kế phải cho phép outbound Internet có kiểm soát. Hai thành phần nào cần có?  
A. IGW trực tiếp private subnet  
B. NAT Gateway trong public subnet  
C. Public IP cho EC2  
D. S3 endpoint bắt buộc cho mọi package  
E. Route private subnet tới NAT  

19. Compliance team muốn biết resource nào bị thay đổi cấu hình, đồng thời tự động đánh giá chúng có tuân thủ các rule nội bộ hay không. Dịch vụ nào phù hợp nhất?  
A. Inspector  
B. SNS  
C. Budgets  
D. AWS Config  

20. Một ứng dụng web nhận traffic từ client Internet qua Application Load Balancer. Yêu cầu là mã hóa traffic bằng chứng chỉ được quản lý tập trung trước khi request tới backend. Cấu hình nào phù hợp nhất?  
A. HTTPS listener + ACM certificate  
B. HTTP + EBS encryption  
C. UDP listener  
D. SSE-KMS

## Domain 2 — Design Resilient Architectures

21. Một web application chạy trên EC2 phải tiếp tục phục vụ khi toàn bộ một Availability Zone bị lỗi. Workload stateless và cần tự động tăng/giảm số instance theo tải. Kiến trúc nào phù hợp nhất?  
A. Một EC2 lớn  
B. ASG đa AZ + ALB  
C. Một EC2 + snapshot  
D. ALB và EC2 cùng AZ

22. Một hệ thống nhận đơn hàng theo burst. Nếu consumer tạm thời lỗi, message phải được giữ lại để xử lý lại và các message lỗi nhiều lần phải được tách riêng để điều tra. Thiết kế nào phù hợp nhất?  
A. SNS không subscriber  
B. CloudWatch metric  
C. SQS Standard + visibility timeout + DLQ  
D. S3 website

23. Producer nhận request rất nhanh trong khi consumer xử lý mỗi job mất vài giây. Lưu lượng có thể tăng đột biến và producer không được chờ consumer hoàn tất. Kiến trúc nào phù hợp nhất?  
A. Gọi trực tiếp một EC2  
B. Tăng EBS  
C. Weighted DNS không queue  
D. Producer → SQS → ASG consumer

24. Một database relational production cần failover tự động khi primary instance hoặc Availability Zone gặp sự cố. Doanh nghiệp muốn giải pháp managed trong cùng AWS Region. Lựa chọn nào phù hợp nhất?  
A. RDS Multi-AZ  
B. Read replica cùng AZ + script  
C. Snapshot hằng ngày  
D. DynamoDB global table

25. Một web app đọc cùng một nhóm dữ liệu rất thường xuyên. Cache chỉ là bản sao tạm thời, có thể dựng lại nếu node lỗi, và mục tiêu là giảm latency cũng như tải database. Dịch vụ nào phù hợp nhất?  
A. Glacier  
B. ElastiCache Redis/Memcached  
C. EBS shared cache  
D. CloudTrail

26. Team muốn triển khai phiên bản API mới mà không dừng phiên bản cũ, chuyển traffic dần để theo dõi metric và rollback nhanh nếu có lỗi. Chiến lược nào phù hợp nhất?  
A. Sửa binary trực tiếp  
B. Tắt ASG  
C. Blue/green hoặc canary với CodeDeploy + ALB  
D. DNS TTL 0

27. (Chọn HAI) Một ứng dụng stateless cần scale theo traffic và vẫn phục vụ khi một AZ mất. Hai thiết kế nào đáp ứng đồng thời khả năng scale và high availability?  
A. Một EC2 vertical scale  
B. Bắt buộc sticky session  
C. ALB → ASG đa AZ  
D. Session trên local disk  
E. ECS service đa AZ  

28. Một consumer có thể thất bại do dữ liệu không hợp lệ. Sau một số lần retry, message không nên tiếp tục làm chậm các message tốt mà phải được chuyển tới nơi xử lý riêng. Tính năng nào phù hợp nhất?  
A. S3 Object Lock  
B. Route 53 health check  
C. CloudFront failover  
D. DLQ + redrive policy  

29. Khi một đơn hàng được tạo, ba service độc lập cần nhận cùng một event. Mỗi service có tốc độ xử lý khác nhau và không được làm mất event nếu một service tạm thời chậm. Kiến trúc nào phù hợp nhất?  
A. SNS fanout tới SQS queue riêng  
B. Một SQS queue chung  
C. Lambda gọi tuần tự  
D. DynamoDB không stream

30. Một ứng dụng được triển khai ở hai Region. Khi endpoint ở Region primary unhealthy, DNS phải tự động trả lời bằng endpoint của Region secondary. Giải pháp nào phù hợp nhất?  
A. Simple routing  
B. Route 53 failover routing + health check  
C. ALB cross-Region target group  
D. NAT failover

31. Một ứng dụng toàn cầu cần đọc và ghi dữ liệu DynamoDB tại nhiều Region để giảm latency. Các bản sao phải được quản lý và đồng bộ bởi dịch vụ AWS. Lựa chọn nào phù hợp nhất?  
A. Local secondary index  
B. RDS Multi-AZ  
C. DynamoDB global tables  
D. Transfer Acceleration

32. Người dùng upload ảnh và ảnh phải được lưu bền vững, không phụ thuộc vào lifecycle của EC2. Ảnh được truy cập toàn cầu và cần scale tự động. Kiến trúc nào phù hợp nhất?  
A. EBS một EC2  
B. Instance store  
C. EFS một AZ  
D. S3 + CloudFront  

33. (Chọn HAI) Với database quan trọng, business đặt RPO tối đa 1 giờ và RTO tối đa 4 giờ. Công ty chấp nhận một phần downtime và muốn chi phí thấp hơn mô hình active-active. Hai chiến lược nào có thể phù hợp?  
A. Pilot light/warm standby Region khác  
B. Multi-AZ đủ bảo vệ Region  
C. Backup/cross-Region snapshot theo lịch  
D. Tăng instance  
E. Backup duy nhất cùng volume

34. Lambda xử lý message SQS thường mất tối đa 90 giây, nhưng cùng một message đôi khi xuất hiện lại trong queue khi Lambda vẫn đang chạy. Thay đổi nào phù hợp nhất?  
A. Visibility timeout lớn hơn thời gian xử lý tối đa  
B. SQS không retry  
C. Tắt encryption  
D. Đặt Lambda public subnet

35. Một nhóm EC2 phục vụ web cần tự loại instance không khỏe khỏi traffic và tự tạo instance thay thế mà không cần kỹ sư can thiệp. Cấu hình nào phù hợp nhất?  
A. Dashboard không alarm  
B. ASG + EC2/ELB health checks  
C. EBS encryption  
D. Access Analyzer

36. Website có người dùng trên nhiều châu lục và phần lớn nội dung là file tĩnh ít thay đổi. Công ty muốn nội dung được phục vụ gần người dùng và giảm số request về origin. Giải pháp nào phù hợp nhất?  
A. NAT mỗi AZ  
B. VPC peering tới user  
C. CloudFront caching  
D. SQS long polling

37. Một ứng dụng cần database key-value managed, chịu lỗi AZ và có thể phục hồi dữ liệu về thời điểm gần nhất trong vài phút nếu xảy ra lỗi logic. Dịch vụ/cấu hình nào phù hợp nhất?  
A. Table một AZ  
B. Instance store  
C. S3 IA không versioning  
D. DynamoDB + PITR  

## Domain 3 — Design High-Performing Architectures

38. Một ứng dụng Linux chạy trên nhiều EC2 ở nhiều AZ cần cùng truy cập filesystem POSIX. Throughput phải tăng theo nhu cầu mà team không muốn tự quản lý file server. Lựa chọn nào phù hợp nhất?  
A. EFS  
B. EBS gắn đồng thời mặc định  
C. Instance store  
D. Glacier

39. Một EC2 chạy workload database cần block storage bền vững với IOPS cao, ổn định và phù hợp cho dữ liệu production. Lựa chọn nào phù hợp nhất?  
A. S3  
B. EBS io2  
C. EFS IA  
D. Instance store cho dữ liệu bền vững

40. Một RDS database có lượng đọc tăng nhanh trong khi write vẫn nằm trong khả năng của primary. Team muốn mở rộng đọc mà không dùng standby Multi-AZ cho read traffic. Giải pháp nào phù hợp nhất?  
A. Multi-AZ standby đọc  
B. Tăng log level  
C. RDS read replicas  
D. NAT Gateway

41. Một ứng dụng cần cache key-value với latency rất thấp và có thể tăng khả năng phục vụ read khi traffic tăng. Cache không phải nguồn dữ liệu bền vững chính. Dịch vụ nào phù hợp nhất?  
A. Glacier  
B. Athena  
C. Backup  
D. ElastiCache Redis  

42. Một ứng dụng cần SQL, transaction và quan hệ giữa bảng, nhưng traffic thay đổi theo thời gian. Team muốn capacity database tự điều chỉnh và không muốn quản lý server database. Lựa chọn nào phù hợp nhất?  
A. Aurora Serverless v2  
B. EC2 MySQL  
C. DynamoDB cho mọi relational query  
D. S3 Select

43. (Chọn HAI) Doanh nghiệp lưu hàng terabyte dữ liệu ở S3 và chỉ query vài lần mỗi ngày. Họ không muốn duy trì cluster chạy 24/7 và cần metadata schema để nhiều analyst dùng chung. Hai lựa chọn nào phù hợp?  
A. RDS Multi-AZ làm data lake  
B. Athena  
C. EBS snapshot làm query engine  
D. Glue Data Catalog  
E. NAT mỗi file

44. Website phân phối ảnh và JavaScript từ S3 cho người dùng toàn cầu. Mục tiêu là giảm latency và giảm số lần request trực tiếp tới S3 origin. Kiến trúc nào phù hợp nhất?  
A. Tắt cache  
B. CloudFront + S3 origin  
C. S3 public subnet  
D. VPC peering user-S3

45. Một service cần xử lý hàng triệu request đọc/ghi nhỏ mỗi giây, schema có thể thay đổi và latency phải ổn định ở quy mô lớn. Dịch vụ database nào phù hợp nhất?  
A. RDS một instance  
B. EFS JSON  
C. DynamoDB  
D. Athena từng request

46. Doanh nghiệp cần truyền lượng dữ liệu lớn định kỳ từ data center lên S3. Kết nối Internet hiện tại không đủ ổn định và họ muốn một đường truyền private, nhất quán hơn. Lựa chọn nào phù hợp nhất?  
A. DNS TTL  
B. CloudFront upload  
C. SQS làm file transfer  
D. Direct Connect + dịch vụ transfer phù hợp

47. Aurora database có read traffic biến động mạnh theo giờ. Team muốn tự động tăng/giảm số read replica dựa trên metric mà không viết hệ thống provisioning riêng. Tính năng nào phù hợp nhất?  
A. Aurora Auto Scaling  
B. S3 Lifecycle  
C. Spot Fleet  
D. Latency routing

48. Một batch job có thể bị dừng và chạy lại từ checkpoint, không yêu cầu uptime liên tục. Mục tiêu chính là giảm chi phí compute. Lựa chọn nào phù hợp nhất?  
A. On-Demand duy nhất  
B. EC2 Spot  
C. Dedicated Host  
D. RDS Reserved

49. Mỗi khi người dùng upload file lên S3, hệ thống cần chạy một hàm xử lý thumbnail. Team muốn event-driven và không muốn duy trì server polling bucket. Giải pháp nào phù hợp nhất?  
A. EC2 polling mỗi giây  
B. EBS snapshot event  
C. S3 event → Lambda  
D. Route 53 health check

50. (Chọn HAI) Một service container chạy trên ECS cần scale theo số request, đồng thời team muốn giảm việc quản lý EC2 host. Hai lựa chọn nào phù hợp?  
A. Tắt health check  
B. Container root  
C. ECS Fargate  
D. Task cố định  
E. Service Auto Scaling theo ALB request/target  

51. Workload trong private subnet cần gọi các AWS service mà traffic không được đi qua Internet Gateway hoặc public IP. Team muốn chọn đúng loại endpoint theo từng service. Lựa chọn nào phù hợp nhất?  
A. IGW private subnet  
B. Public IP mọi workload  
C. NAT là lựa chọn duy nhất  
D. VPC endpoints, gateway/interface tùy service  

52. API có endpoint ở nhiều Region. Mục tiêu là tự động đưa mỗi user tới Region có network latency thấp nhất, không chỉ dựa trên quốc gia của user. Routing policy nào phù hợp nhất?  
A. Route 53 latency-based routing  
B. Geolocation bắt buộc  
C. Transfer Acceleration  
D. EBS Multi-Attach

## Domain 4 — Design Cost-Optimized Architectures

53. Một workload production chạy 24/7 với mức sử dụng khá ổn định trong ba năm tới. Doanh nghiệp muốn giảm chi phí so với On-Demand nhưng vẫn giữ compute capacity dự đoán được. Lựa chọn pricing nào phù hợp nhất?  
A. Spot cho DB bắt buộc uptime  
B. Savings Plans hoặc Reserved Instances  
C. Dedicated Host  
D. Tăng instance size

54. Một batch workload có thể bị interrupt mà không ảnh hưởng dữ liệu vì job sẽ tự retry. Yêu cầu chính là tối ưu chi phí compute. Lựa chọn nào phù hợp nhất?  
A. On-Demand duy nhất  
B. Dedicated Instances  
C. Spot Instances  
D. Reserved ba năm

55. Log compliance phải giữ nhiều năm, rất hiếm khi đọc, nhưng khi cần phải phục hồi trong vài giờ. Cấu hình storage nào thường tối ưu chi phí nhất?  
A. S3 Standard mãi mãi  
B. EBS archive  
C. DynamoDB  
D. S3 Lifecycle → Glacier Flexible Retrieval  

56. FinOps team muốn tìm idle resource, rightsizing opportunity và các khuyến nghị giảm chi phí từ nhiều account. Dịch vụ nào hỗ trợ tốt nhất?  
A. Cost Optimization Hub / Trusted Advisor  
B. Inspector  
C. WAF  
D. Cognito

57. Một API có traffic không đều, nhiều khoảng thời gian không có request. Team muốn tính phí chủ yếu theo số request/thời gian xử lý và không vận hành server. Kiến trúc nào phù hợp nhất?  
A. EC2 24/7  
B. API Gateway + Lambda  
C. Dedicated Host  
D. Aurora lớn quanh năm

58. (Chọn HAI) Object trong bucket thường được truy cập khi mới tạo, sau đó access pattern không thể dự đoán. Team muốn tự động chuyển object sang tier rẻ hơn mà không phải theo dõi thủ công. Hai lựa chọn nào phù hợp?  
A. S3 Intelligent-Tiering  
B. S3 Standard mãi mãi  
C. Tắt versioning dù compliance  
D. S3 Lifecycle transition  
E. Copy vào EBS

59. Một DynamoDB table có traffic khó dự đoán và burst theo chiến dịch marketing. Team không muốn dự báo capacity hoặc trả trước provisioned capacity. Lựa chọn nào phù hợp nhất?  
A. Provisioned thấp cố định  
B. EBS cache bắt buộc  
C. On-demand capacity mode  
D. Tắt retry SDK

60. Công ty muốn xem chi phí theo team và environment như dev, staging, production trên Cost Explorer. Các resource phải được phân loại nhất quán. Cách nào phù hợp nhất?  
A. Tên resource ngẫu nhiên  
B. CloudTrail thay billing  
C. Security group tags  
D. Cost allocation tags + Cost Explorer  

61. Một website tĩnh lưu trên S3 có lượng truy cập lớn từ nhiều quốc gia. Công ty muốn giảm tải origin và giảm chi phí truyền dữ liệu từ origin tới người dùng. Giải pháp nào phù hợp nhất?  
A. CloudFront caching  
B. Tắt cache  
C. NAT phục vụ object  
D. EC2 + EBS

62. Một job bảo trì chỉ chạy vài phút mỗi giờ và không cần compute ngoài các khung giờ đã định. Team muốn tránh trả tiền cho instance nhàn rỗi. Kiến trúc nào phù hợp nhất?  
A. EC2 24/7  
B. EventBridge Scheduler → Lambda/ECS task  
C. Dedicated Host  
D. RDS Multi-AZ

63. Một service luôn cần một mức compute nền ổn định, nhưng có thể tăng thêm capacity khi traffic burst. Phần capacity bổ sung có thể bị gián đoạn nếu có fallback. Kiến trúc pricing nào phù hợp nhất?  
A. Dedicated Host toàn bộ  
B. Spot toàn bộ không fallback  
C. Base capacity bằng On-Demand/Savings Plans + burst Spot  
D. Một instance lớn

64. Các EC2 private chỉ gọi S3 và DynamoDB. Công ty muốn giữ workload private và loại bỏ chi phí hourly/data processing không cần thiết của NAT Gateway. Giải pháp nào phù hợp nhất?  
A. NAT mỗi subnet  
B. Public IP  
C. Public subnet không route table  
D. Gateway VPC endpoints cho S3/DynamoDB  

65. Finance team đặt ngân sách theo tháng và muốn nhận cảnh báo trước khi chi phí thực tế hoặc forecast vượt ngưỡng. Họ không muốn tự xây pipeline billing. Dịch vụ nào phù hợp nhất?  
A. AWS Budgets  
B. CloudWatch Logs Insights  
C. Route 53 health check  
D. Macie

## Phiếu trả lời

```
01 __ 02 __ 03 __ 04 __ 05 __ 06 __ 07 __ 08 __ 09 __ 10 __
11 __ 12 __ 13 __ 14 __ 15 __ 16 __ 17 __ 18 __ 19 __ 20 __
21 __ 22 __ 23 __ 24 __ 25 __ 26 __ 27 __ 28 __ 29 __ 30 __
31 __ 32 __ 33 __ 34 __ 35 __ 36 __ 37 __ 38 __ 39 __ 40 __
41 __ 42 __ 43 __ 44 __ 45 __ 46 __ 47 __ 48 __ 49 __ 50 __
51 __ 52 __ 53 __ 54 __ 55 __ 56 __ 57 __ 58 __ 59 __ 60 __
61 __ 62 __ 63 __ 64 __ 65 __
```

Chấm 1 điểm/câu đúng hoàn toàn. Mock score không quy đổi chính xác sang scaled score AWS. Mốc luyện tập: <70% ôn nền tảng; 70–80% luyện theo domain; >80% làm đề mới và rà câu sai.

Nguồn: [AWS Certification](https://aws.amazon.com/certification/certified-solutions-architect-associate/) và [SAA-C03 Exam Guide](https://docs.aws.amazon.com/pdfs/aws-certification/latest/solutions-architect-associate-03/solutions-architect-associate-03.pdf).

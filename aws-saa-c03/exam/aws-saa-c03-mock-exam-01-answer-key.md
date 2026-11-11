# AWS SAA-C03 Mock Exam 01 — Answer key

|Câu|ĐA|Giải thích chi tiết|
|---:|:---:|---|
|1|B|EC2 instance profile cung cấp credential tạm thời từ IAM role và SDK tự lấy hoặc đổi credential khi cần. Vì vậy không phải phân phối access key dài hạn trong user data hoặc AMI, còn public bucket vi phạm nguyên tắc least privilege.|
|2|A|IAM Identity Center kết nối workforce identity với IdP doanh nghiệp và hỗ trợ cấp quyền theo account hoặc permission set. Cách này tập trung hóa vòng đời user và MFA; IAM user riêng hoặc access key chung tạo thêm credential cần quản lý.|
|3|C|S3 bucket policy có thể dùng điều kiện aws:SourceVpce để chỉ chấp nhận request đi qua endpoint cụ thể, đồng thời giới hạn Region bằng điều kiện phù hợp. S3 không gắn security group trực tiếp, còn ACL public-read đi ngược yêu cầu private.|
|4|D|Secrets Manager lưu secret được mã hóa, tích hợp IAM và hỗ trợ rotation, gồm cả mẫu rotation cho database. Glacier là storage archive, CloudTrail là audit log, còn Cognito Identity Pool không phải kho quản lý mật khẩu database.|
|5|A|AWS WAF kiểm tra request HTTP hoặc HTTPS theo web ACL và managed hoặc custom rules trước khi request tới backend API. Security group chỉ lọc network traffic, không phân tích nội dung HTTP nên không thay thế WAF cho SQL injection hoặc XSS.|
|6|B|Organization trail của CloudTrail có thể áp dụng cho toàn tổ chức và gửi log tập trung về bucket audit được bảo vệ. VPC Flow Logs và ALB logs chỉ ghi một phần lưu lượng, không ghi đầy đủ hoạt động gọi AWS API.|
|7|C|S3 gateway VPC endpoint thêm route tới S3 và giữ traffic trên mạng AWS, không cần NAT Gateway hay public IP. Internet Gateway, public IP và VPC peering tới một VPC public đều mở rộng đường đi không cần thiết.|
|8|D|SCP là guardrail ở cấp Organizations, account hoặc OU, nên có thể cấm hành động dù IAM policy trong member account cho phép. Permissions boundary trên một user không bao phủ toàn bộ account, còn security group không kiểm soát việc tạo resource hay tắt CloudTrail.|
|9|A|KMS customer managed key cho phép doanh nghiệp kiểm soát key policy, rotation và quyền sử dụng; CloudTrail ghi lại các API call liên quan đến key. TLS chỉ bảo vệ dữ liệu khi truyền, không đáp ứng mã hóa at rest và audit key usage.|
|10|BD|Permissions boundary giới hạn quyền tối đa mà developer role có thể sử dụng; SCP tiếp tục đặt giới hạn ở account hoặc OU. Hai lớp guardrail độc lập giúp giảm blast radius khi policy của role bị cấp nhầm.|
|11|B|Cognito User Pool xử lý đăng ký và xác thực người dùng, còn Identity Pool đổi identity đã xác thực thành credential AWS tạm thời qua IAM role. Do đó không cần tạo IAM user hoặc lưu access key cho từng người dùng mobile.|
|12|C|Transit Gateway hoạt động như hub route tập trung cho nhiều VPC và mạng on-premises, kết hợp VPN hoặc Direct Connect. Full-mesh peering tăng mạnh số kết nối và độ phức tạp; NAT Gateway và Internet Gateway không phải mô hình kết nối private phù hợp.|
|13|AD|CloudFront signed URL và S3 presigned URL đều cấp quyền tải có thời hạn mà không cần public bucket. Bucket policy public hoặc tắt Block Public Access làm tăng rủi ro, còn URL chứa key không hết hạn không đáp ứng yêu cầu bảo mật.|
|14|D|ECS task role gắn quyền IAM vào task definition, nên task chỉ có quyền cần thiết khi gọi DynamoDB. Security group chỉ kiểm soát network, còn access key chung hoặc root credential vi phạm least privilege và khó xoay vòng.|
|15|A|Security group của RDS có thể cho phép inbound từ security group của application tier trên đúng port 5432. Đây là kiểm soát theo identity của nhóm workload, tốt hơn mở 0.0.0.0/0 hoặc đưa database ra public subnet.|
|16|B|AWS Config rules đánh giá trạng thái cấu hình như encryption hoặc security group; Security Hub tập hợp và chuẩn hóa finding để theo dõi tập trung. Inspector chủ yếu đánh giá lỗ hổng workload, không phải công cụ chính cho các kiểm tra cấu hình này.|
|17|C|Secrets Manager hoặc Parameter Store giữ token ngoài source code và AMI, đồng thời có thể giới hạn quyền đọc bằng IAM. Private Git, EC2 tag hoặc bucket name không phải secret store an toàn và dễ bị lộ qua log, metadata hoặc repository.|
|18|BE|NAT Gateway phải nằm trong public subnet có route tới Internet Gateway; private subnet route outbound tới NAT. EC2 vẫn không có public IP. Chỉ đặt Internet Gateway trực tiếp cho private subnet hoặc cấp public IP sẽ phá vỡ mục tiêu private.|
|19|D|AWS Config ghi lại lịch sử cấu hình và đánh giá resource bằng managed hoặc custom rules. CloudTrail ghi API activity nhưng không tự trả lời đầy đủ câu hỏi resource hiện tại có compliant hay không; Budgets chỉ theo dõi chi phí.|
|20|A|HTTPS listener trên ALB kết hợp chứng chỉ ACM mã hóa kết nối client tới load balancer và quản lý certificate tập trung. EBS encryption bảo vệ storage, không bảo vệ HTTP traffic; HTTP listener không cung cấp mã hóa.|
|21|B|ALB phân phối request tới các instance trong ASG trải trên nhiều AZ, còn ASG thay đổi capacity theo tải và thay instance lỗi. Một instance hoặc một AZ duy nhất vẫn là single point of failure.|
|22|C|SQS giữ message bền vững khi consumer tạm thời lỗi; visibility timeout ẩn message đang xử lý và DLQ giữ message thất bại nhiều lần. SNS không subscriber và S3 website không cung cấp mô hình queue và retry phù hợp.|
|23|D|SQS tạo lớp đệm giữa producer và consumer, hấp thụ burst và cho phép consumer scale độc lập trong ASG. Gọi trực tiếp một EC2 buộc producer phụ thuộc vào capacity và trạng thái của consumer.|
|24|A|RDS Multi-AZ duy trì standby đồng bộ ở AZ khác và RDS tự thực hiện failover khi primary lỗi. Read replica phục vụ read scaling chứ không phải standby failover cùng mục tiêu, còn snapshot không cho failover nhanh.|
|25|B|ElastiCache phù hợp dữ liệu cache có thể dựng lại, cung cấp latency thấp và giảm số lần đọc vào database. Vì cache không phải nguồn dữ liệu chính, việc mất node không làm mất dữ liệu gốc như khi dùng EBS hoặc database sai mục đích.|
|26|C|Blue/green hoặc canary giữ phiên bản cũ, chuyển một phần hoặc toàn bộ traffic có kiểm soát và cho phép rollback. Sửa binary trực tiếp hoặc tắt ASG tạo downtime và không cung cấp đường rollback an toàn.|
|27|CE|ALB với ASG đa AZ và ECS service đa AZ đều phân phối stateless workload trên nhiều AZ và có cơ chế scale. Vertical scaling một instance và session trên local disk không đáp ứng đồng thời fault tolerance và horizontal scaling.|
|28|D|DLQ nhận message sau số lần receive hoặc retry được cấu hình, giúp cô lập poison message để điều tra mà không chặn message tốt. S3 Object Lock và Route 53 health check không phải cơ chế xử lý lỗi của SQS consumer.|
|29|A|SNS fanout phát một event tới nhiều SQS queue riêng, nên mỗi service có buffer, retry và tốc độ xử lý độc lập. Một queue chung khiến mỗi message thường chỉ được một consumer nhận và không đáp ứng fanout cho tất cả service.|
|30|B|Route 53 failover routing kết hợp health check có thể trả về primary khi healthy và chuyển sang secondary khi primary unhealthy. Simple routing không tự đánh giá health để failover.|
|31|C|DynamoDB global tables tạo bản sao multi-Region managed và hỗ trợ read hoặc write tại nhiều Region. Local secondary index chỉ là index trong cùng Region hoặc table, còn RDS Multi-AZ không phải multi-Region DynamoDB.|
|32|D|S3 cung cấp độ bền và khả năng mở rộng cho object upload, còn CloudFront cache và phân phối object tại edge toàn cầu. EBS, instance store hoặc một EFS trong một AZ gắn dữ liệu vào compute topology.|
|33|AC|Pilot light hoặc warm standby ở Region khác giúp rút ngắn thời gian khôi phục; backup hoặc cross-Region snapshot theo lịch có thể đáp ứng RPO 1 giờ nếu lịch và quy trình restore được kiểm thử. Multi-AZ chỉ bảo vệ trong một Region và không đủ cho Region disaster.|
|34|A|SQS có thể giao lại message khi visibility timeout hết hạn trước khi Lambda xử lý xong. Đặt timeout lớn hơn thời gian xử lý tối đa, thường có thêm buffer, giúp tránh duplicate do timeout; ứng dụng vẫn nên xử lý idempotent.|
|35|B|ASG dùng EC2 và ELB health checks để loại instance không khỏe khỏi service và tạo instance thay thế. Dashboard chỉ quan sát, còn encryption hoặc Access Analyzer không thực hiện remediation compute.|
|36|C|CloudFront cache nội dung tại edge gần người dùng, giảm latency và số request tới origin. NAT Gateway không phải CDN; tắt cache hoặc để request vòng về origin sẽ không đạt mục tiêu hiệu năng.|
|37|D|DynamoDB là managed multi-AZ service và Point-in-Time Recovery cho phép phục hồi table về thời điểm gần nhất trong retention window. Một table hoặc instance đơn lẻ hay storage class S3 không đáp ứng mô hình key-value transactional này.|
|38|A|EFS cung cấp filesystem POSIX managed có thể mount từ nhiều EC2 ở nhiều AZ và tự điều chỉnh throughput theo mode hoặc cấu hình. EBS thông thường gắn với một AZ hoặc instance, còn instance store không bền vững.|
|39|B|EBS io2 là block volume bền vững cho workload cần IOPS cao và ổn định, với khả năng cung cấp IOPS độc lập hơn so với các lựa chọn phổ thông. Instance store có hiệu năng nhưng dữ liệu mất khi instance bị dừng hoặc thay thế.|
|40|C|RDS read replica sao chép dữ liệu để phục vụ read traffic và có thể mở rộng số replica theo nhu cầu. Multi-AZ standby dành cho availability hoặc failover và không nên được dùng như read endpoint của ứng dụng.|
|41|D|ElastiCache Redis cung cấp cache key-value độ trễ thấp và các tính năng replication hoặc scaling phù hợp read-heavy workload. Glacier, Athena và Backup không phải online low-latency cache.|
|42|A|Aurora Serverless v2 tự điều chỉnh capacity database theo workload trong khi vẫn cung cấp engine relational, SQL và transaction. EC2 MySQL cần tự quản server, còn DynamoDB hoặc S3 Select không đáp ứng mô hình relational.|
|43|BD|Athena query trực tiếp dữ liệu trong S3 theo kiểu serverless, nên không cần cluster chạy liên tục. Glue Data Catalog lưu metadata và schema dùng chung; RDS hoặc EBS không phải query engine data lake phù hợp.|
|44|B|CloudFront đặt cache tại edge và dùng S3 làm origin, nên giảm round-trip tới S3 và giảm origin requests. S3 không nằm trong public subnet, và tắt cache đi ngược mục tiêu performance.|
|45|C|DynamoDB phù hợp workload key-value hoặc document với schema linh hoạt, throughput lớn và latency ổn định khi thiết kế partition key đúng. RDS một instance và EFS không scale theo mẫu request này; Athena không dành cho online request path.|
|46|D|Direct Connect cung cấp kết nối dedicated hoặc private ổn định hơn Internet; dịch vụ transfer phù hợp có thể tối ưu việc đưa dữ liệu lớn vào S3. CloudFront phục vụ nội dung tới người dùng chứ không phải kênh upload on-premises vào S3.|
|47|A|Aurora Auto Scaling tự động điều chỉnh số read replica theo policy và metric như CPU hoặc connections. Nhờ vậy read capacity bám theo workload mà không cần tự viết workflow tạo hoặc xóa replica.|
|48|B|EC2 Spot tận dụng capacity AWS chưa dùng với giá thấp hơn On-Demand, đổi lại instance có thể bị interrupt. Vì job có checkpoint và retry được nên trade-off này phù hợp.|
|49|C|S3 event notification kích hoạt Lambda ngay khi object được tạo, tạo pipeline event-driven và không cần server polling. Polling EC2 vừa tốn chi phí nhàn rỗi vừa tăng độ trễ và độ phức tạp.|
|50|CE|ECS Fargate loại bỏ phần lớn việc quản lý EC2 host; Service Auto Scaling có thể scale task theo ALB request count per target hoặc metric phù hợp. Kết hợp hai lựa chọn đáp ứng cả mục tiêu vận hành và elastic scaling.|
|51|D|VPC endpoint cung cấp private path tới AWS service; gateway endpoint thường dùng cho S3 hoặc DynamoDB, còn interface endpoint dùng cho nhiều service khác. Public IP, Internet Gateway hoặc giả định NAT là lựa chọn duy nhất đều không tối ưu yêu cầu private.|
|52|A|Route 53 latency-based routing đo latency từ user tới các Region và chọn endpoint có latency thấp nhất. Geolocation định tuyến theo vị trí địa lý, không đảm bảo Region có latency thấp nhất.|
|53|B|Savings Plans hoặc Reserved Instances giảm đơn giá khi doanh nghiệp cam kết mức sử dụng dài hạn. Vì workload ổn định 24/7, cam kết này phù hợp hơn On-Demand; Spot không đảm bảo capacity liên tục.|
|54|C|Spot Instances có giá thấp và phù hợp batch có thể bị interrupt rồi retry. Với workload không chịu được gián đoạn, Spot không nên là lựa chọn duy nhất, nhưng điều kiện câu hỏi cho phép dùng nó.|
|55|D|S3 Lifecycle tự động chuyển object cũ sang Glacier Flexible Retrieval, giảm storage cost nhưng vẫn có lựa chọn phục hồi trong phút hoặc giờ. Giữ toàn bộ ở S3 Standard sẽ đắt hơn cho dữ liệu hiếm truy cập.|
|56|A|Cost Optimization Hub và Trusted Advisor cung cấp các khuyến nghị như idle resource và rightsizing trên phạm vi AWS account. Inspector, WAF và Cognito phục vụ bảo mật hoặc identity, không phải cost optimization.|
|57|B|API Gateway và Lambda là serverless, tính phí theo request và thời gian chạy, nên phù hợp traffic không đều và không có server nhàn rỗi. EC2 hoặc Aurora provisioned sẽ tiếp tục phát sinh chi phí khi không có request.|
|58|AD|S3 Intelligent-Tiering tự chuyển object giữa các access tier khi mẫu truy cập thay đổi; Lifecycle transition dùng rule theo tuổi hoặc prefix để chuyển sang storage class rẻ hơn. Hai cơ chế giúp giảm cost mà không cần theo dõi thủ công từng object.|
|59|C|DynamoDB on-demand tự phục vụ request theo mức sử dụng và phù hợp traffic khó dự đoán hoặc burst. Provisioned capacity thấp có thể throttling, còn provisioned cao gây lãng phí khi traffic giảm.|
|60|D|Cost allocation tags gắn metadata team hoặc environment vào usage, sau đó Cost Explorer dùng tags để phân tích chi phí. Đặt tên resource hoặc security group tag không tự tạo cost allocation chuẩn nếu chưa bật và cấu hình billing tags.|
|61|A|CloudFront cache object tại edge, giảm số request và lượng dữ liệu phải lấy từ S3 origin. Điều đó vừa cải thiện latency vừa giảm origin data transfer so với việc để mọi user gọi S3 trực tiếp.|
|62|B|EventBridge Scheduler kích hoạt Lambda hoặc ECS task đúng lịch, nên compute chỉ chạy trong vài phút cần thiết. EC2 chạy 24/7 sẽ trả tiền cho thời gian nhàn rỗi.|
|63|C|Dùng On-Demand hoặc Savings Plans cho baseline đảm bảo capacity ổn định, rồi dùng Spot cho phần burst có thể interrupt. Đây là cách cân bằng chi phí thấp với khả năng fallback khi Spot bị thu hồi.|
|64|D|Gateway VPC endpoints cho S3 và DynamoDB cho phép private access và không có hourly charge theo kiểu NAT Gateway. Vì workload chỉ gọi hai service này, NAT Gateway là chi phí và đường đi thừa.|
|65|A|AWS Budgets theo dõi actual hoặc forecasted spend và gửi cảnh báo khi vượt ngưỡng. CloudWatch Logs, Route 53 và Macie không phải công cụ quản lý ngân sách billing.|

## Phân bổ

- Domain 1: 1–20 = 20 câu
- Domain 2: 21–37 = 17 câu
- Domain 3: 38–52 = 15 câu
- Domain 4: 53–65 = 13 câu

Tỷ lệ đề: 30.8% / 26.2% / 23.1% / 20.0%, xấp xỉ tỷ trọng chính thức 30% / 26% / 24% / 20%. Đây là đề tự biên soạn, không phải câu hỏi thật của AWS.

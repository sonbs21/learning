# AWS Certified Solutions Architect – Associate (SAA-C03)
## Mock Exam 02 — Advanced

**Thời gian:** 130 phút  
**Số câu:** 65  
**Hình thức:** Multiple choice và multiple response

Câu multiple response ghi rõ số lượng đáp án. Chỉ tính đúng khi chọn đủ và không chọn thừa. Đây là đề tự biên soạn, không phải đề thật của AWS.

## Domain 1 — Design Secure Architectures

### Câu 1
Một công ty ở Account A cần cho phép ứng dụng trong Account B đọc một prefix cụ thể của S3 bucket. Công ty không muốn access key dài hạn và muốn ngăn confused deputy. Giải pháp nào phù hợp nhất?

A. Cross-account IAM role, trust policy giới hạn Account B và yêu cầu ExternalId
B. IAM user ở Account A và access key cho Account B
C. Public bucket giới hạn bằng prefix
D. Security group giới hạn quyền S3

### Câu 2
Enterprise có 50 AWS account và nhân viên đăng nhập bằng corporate IdP. Security team muốn cấp quyền theo job function, hỗ trợ MFA của IdP và thu hồi quyền tập trung.

A. IAM user trong từng account
B. IAM Identity Center tích hợp IdP và permission sets
C. Một IAM role chung
D. Access key theo team

### Câu 3
Website phân phối private S3 content qua CloudFront. Người dùng không được truy cập S3 trực tiếp, kể cả khi biết object URL.

A. S3 ACL public-read
B. Presigned URL không hết hạn
C. CloudFront Origin Access Control và bucket policy chỉ cho distribution đọc
D. Security group gắn vào S3

### Câu 4
Workload chạy trên EKS cần đọc S3. Cluster dùng chung node group và pod của team này không được dùng credential của team khác.

A. Quyền S3 rộng trên node role
B. Access key trong ConfigMap
C. Bucket public-read
D. IAM roles for service accounts, role riêng cho Kubernetes service account

### Câu 5
ECS task cần lấy database password. Password phải tự động xoay vòng và task chỉ được đọc đúng secret của database.

A. Secrets Manager với task role và ARN giới hạn
B. Password trong task definition
C. Password trong private image
D. Access key admin trong biến môi trường

### Câu 6
Encrypted database snapshot ở Account A cần restore ở Account B. Cả hai account phải kiểm soát quyền dùng customer managed KMS key.

A. Copy rồi dùng AWS-owned key
B. Cho Account B quyền dùng KMS key qua key policy hoặc grant, chia sẻ encrypted snapshot và restore
C. Tắt encryption trước khi chia sẻ
D. Đặt KMS key trong security group

### Câu 7 — Chọn HAI
Public API bị abuse bởi client gửi hàng nghìn request mỗi phút. Muốn giảm abuse ở HTTP layer mà không sửa code.

A. VPC Flow Logs
B. AWS WAF rate-based rule
C. EBS encryption
D. API Gateway throttling hoặc usage plan
E. S3 lifecycle

### Câu 8
Bucket phải từ chối mọi PutObject nếu không yêu cầu SSE-KMS với đúng KMS key.

A. Chỉ bật default encryption
B. Security group port 443
C. Bucket policy explicit Deny dựa trên encryption headers
D. CloudTrail chặn sau khi ghi log

### Câu 9
EC2 private subnet cần được quản trị viên truy cập, không mở SSH inbound, không bastion và cần audit command execution.

A. Public IP và SSH từ Internet
B. VPC peering tới laptop
C. Private key trong S3 public
D. Systems Manager Session Manager với IAM và VPC endpoints cần thiết

### Câu 10
Ứng dụng private chỉ được đọc prefix reports/2026/ trong S3 qua VPC endpoint, không được truy cập bucket khác.

A. IAM policy giới hạn prefix kết hợp S3 gateway endpoint policy giới hạn bucket
B. Security group outbound 443
C. IAM policy rộng trên EC2
D. Endpoint policy s3:* và dựa vào subnet

### Câu 11 — Chọn HAI
Tổ chức muốn CloudTrail log tập trung, administrator workload account không xóa được log và log bất biến theo retention.

A. Lưu log cùng workload account
B. Chỉ CloudWatch dashboard
C. Organization trail gửi tới bucket ở log archive account riêng
D. Developer có quyền DeleteObject
E. S3 Object Lock với retention phù hợp

### Câu 12
Mobile app cần sign-up/sign-in; backend cần claims để authorize API nhưng mobile không cần AWS credentials trực tiếp.

A. Cognito Identity Pool bắt buộc
B. Cognito User Pool với JWT authorizer
C. IAM root user mỗi thiết bị
D. S3 presigned URL cho toàn API

### Câu 13
Muốn buộc member accounts chỉ tạo resource ở ba Region được phê duyệt, kể cả administrator member account.

A. Security group
B. NACL
C. SCP explicit Deny ngoài danh sách Region
D. IAM policy của developer

### Câu 14
Lambda chỉ được đọc DynamoDB item có partition key theo tenant ID trong request claim.

A. Scan toàn table
B. Table public subnet
C. Security group lọc partition key
D. IAM condition phù hợp leading keys kết hợp authorization ở application layer

### Câu 15
Muốn tự động phát hiện PII trong nhiều S3 bucket và cảnh báo dữ liệu nhạy cảm ngoài policy.

A. Amazon Macie
B. AWS Shield
C. Route 53
D. ElastiCache

### Câu 16 — Chọn HAI
Muốn ngăn member account public S3 bucket và phát hiện baseline security không tuân thủ.

A. S3 Block Public Access cấp account hoặc organization
B. NAT Gateway
C. Đổi tên bucket
D. AWS Config rules hoặc conformance packs
E. Tăng EBS

### Câu 17
Public ALB terminate TLS; certificate tự renew và private key không quản lý thủ công trên EC2.

A. Private key trong user data
B. ACM certificate trên HTTPS listener ALB
C. Self-signed certificate trên backend
D. EBS encryption

### Câu 18
Service ở VPC A gọi service ở VPC B. Compliance yêu cầu encryption end-to-end dù mạng private.

A. VPC peering là đủ vì private network mặc định mã hóa
B. Tắt TLS để giảm latency
C. TLS ở application layer và private connectivity như peering hoặc Transit Gateway
D. S3 SSE-KMS

### Câu 19
Database credentials phải tự xoay vòng và ứng dụng nhận password mới không deploy lại. Database hỗ trợ workflow rotation của Secrets Manager.

A. Parameter Store plaintext
B. Password trong container image
C. Password trong RDS tag
D. Secrets Manager rotation và đọc secret runtime

### Câu 20 — Chọn HAI
Root account chỉ dùng break-glass; muốn giảm rủi ro và cảnh báo khi root login.

A. Tạo root access key cho automation
B. Bật MFA và không dùng root hằng ngày
C. Chia sẻ password root
D. Tạo access key root dự phòng và lưu trong Secrets Manager
E. CloudTrail/EventBridge/Security Hub alert khi root sign-in

## Domain 2 — Design Resilient Architectures

### Câu 21
Hệ thống đặt hàng phải giữ thứ tự theo từng customer, nhưng customer khác xử lý song song. Duplicate cần tránh trong deduplication window.

A. SQS FIFO, MessageGroupId theo customer và deduplication phù hợp
B. SQS Standard một consumer
C. SNS email
D. S3 event không queue

### Câu 22
OrderCreated phải xử lý bởi billing, fulfillment và analytics. Mỗi service retry độc lập, lỗi lâu dài phải cô lập.

A. Một SQS queue chung
B. SNS fanout tới SQS queue riêng, mỗi queue có DLQ
C. Lambda gọi tuần tự
D. CloudWatch Logs

### Câu 23
ASG instance khởi động lâu vì tải model. Chỉ nhận traffic sau application health check pass và instance lỗi phải được thay.

A. Chỉ EC2 status check
B. Tắt health check
C. ELB health check trong ASG và grace period phù hợp
D. Route 53 simple routing

### Câu 24
DynamoDB global table nhận write cùng item ở hai Region do client offline reconnect. Cần hạn chế conflict nghiệp vụ.

A. Multi-Region write không có conflict strategy
B. S3 Standard
C. DAX giải quyết conflict
D. Single-writer theo partition hoặc Region, hoặc conditional writes và conflict logic

### Câu 25
RDS cần failover tự động trong AZ và read traffic tăng cao.

A. RDS Multi-AZ cho failover và read replicas cho read scaling
B. Một read replica xem như standby
C. Multi-AZ standby làm read endpoint
D. Snapshot thay read replica

### Câu 26
Aurora primary Region cần DR Region khác, RPO dưới một giây, RTO vài phút; secondary không phục vụ bình thường.

A. Snapshot hằng ngày
B. Aurora Global Database với secondary và quy trình failover
C. RDS Multi-AZ primary
D. EBS snapshot

### Câu 27
CloudFront có origin primary và secondary. Khi primary unhealthy, phải chuyển origin mà client giữ nguyên URL.

A. Route 53 simple routing
B. DNS TTL 0
C. CloudFront origin group với origin failover
D. NAT failover

### Câu 28
Workflow nhiều bước: throttling cần exponential backoff; validation error phải dừng và gửi event vận hành.

A. SQS visibility timeout
B. CloudFront cache
C. Route 53 health check
D. Step Functions Retry và Catch

### Câu 29
Lambda xử lý batch 10 SQS messages; chỉ 2 lỗi và muốn retry 2 message.

A. Partial batch response và idempotent processing
B. Tắt retry Lambda
C. SNS email
D. Tăng memory

### Câu 30
Backup bucket phải chống overwrite/delete trong retention; user được đọc nhưng không sửa bản đã khóa.

A. S3 Standard-IA
B. S3 Versioning và Object Lock retention/legal hold
C. CloudFront signed cookies
D. EBS encryption

### Câu 31
ECS API service phải duy trì desired count khi AZ/task lỗi và scale theo ALB request count.

A. Một task lớn
B. Task count 1 rồi tăng CPU
C. ECS service đa AZ, ALB health check và Service Auto Scaling
D. Route 53 weighted record

### Câu 32
EBS workload cần backup tự động, cross-Region copy và retention daily/weekly/monthly.

A. AMI thủ công
B. CloudFront cache volume
C. Presigned URL cho snapshot
D. AWS Backup plan với lifecycle, cross-Region copy và vault policy

### Câu 33
Account trung tâm phát event tới nhiều workload accounts. Consumer down tạm thời nhưng không muốn polling.

A. EventBridge cross-account event bus policy, target SQS hoặc Lambda
B. Gọi Lambda trực tiếp từng account
C. File local
D. Route 53 failover

### Câu 34
API backend overload khi burst. Business chấp nhận delay vài phút nhưng không mất request.

A. Tăng client timeout vô hạn
B. Ghi durable message vào SQS, worker scale theo backlog và DLQ
C. Một EC2 lớn đồng bộ
D. Bỏ request khi backend bận

### Câu 35
Cần deploy schema database và application version tương thích, tránh version mới phá version cũ trong rollout.

A. Destructive change trước rồi restart
B. Tắt Multi-AZ
C. Expand-and-contract migration, schema backward-compatible rồi deploy dần
D. Spot migration

### Câu 36
Workload có RPO 15 phút, RTO 1 giờ cho Region disaster; không chạy full capacity ở DR.

A. Chỉ RDS Multi-AZ
B. Không cần backup
C. Active-active full capacity bắt buộc
D. Warm standby hoặc pilot light kèm cross-Region backups

### Câu 37
S3 public content cần chống xóa nhầm trong deployment; object mới thay object cũ nhưng bản cũ phải khôi phục được.

A. S3 Versioning và lifecycle cho noncurrent versions
B. Tắt versioning
C. CloudFront invalidation בלבד
D. NAT Gateway

## Domain 3 — Design High-Performing Architectures

### Câu 38
HPC chạy nhiều EC2 cần latency network thấp nhất giữa instance; không ưu tiên fault isolation giữa host.

A. Spread placement group
B. Cluster placement group
C. Partition placement group
D. Không placement group

### Câu 39
Windows app cần shared file system, SMB, Active Directory integration và multi-AZ availability.

A. EFS
B. S3 Glacier
C. FSx for Windows File Server
D. Instance store

### Câu 40
DynamoDB partition key là status; phần lớn item ACTIVE, traffic hot partition và throttling dù tổng capacity còn dư.

A. Tăng capacity toàn table không đổi key
B. S3 Versioning
C. Đặt DynamoDB trong private subnet
D. Partition key cardinality cao hơn hoặc composite key

### Câu 41
Aurora có read traffic lớn nhiều AZ; muốn giảm latency và không quản endpoint từng replica.

A. Aurora reader endpoint và đủ read replicas
B. Cluster endpoint cho mọi read
C. Multi-AZ standby làm read endpoint
D. NAT Gateway trước DB

### Câu 42
Upload terabyte file lớn từ nhiều quốc gia lên S3; muốn retry từng phần khi lỗi mạng.

A. Một PUT duy nhất mỗi file
B. Multipart upload, có thể kết hợp Transfer Acceleration
C. CloudFront download
D. Route 53 weighted

### Câu 43
Website deploy asset mới thường xuyên, user nhận JavaScript cũ từ CloudFront; muốn tránh invalidate hàng nghìn object.

A. TTL bằng 0
B. Tắt edge cache
C. Filename/content-hash versioning và cache dài hạn
D. Tăng origin servers

### Câu 44
On-premises cần truy cập public AWS services qua Direct Connect, không dùng public Internet.

A. Private VIF tới VPC và NAT Gateway
B. Gateway endpoint trong VPC
C. VPC peering tới data center
D. Public VIF tới AWS public services

### Câu 45
Lambda cold start tăng giờ cao điểm; baseline ổn định, burst không dự đoán.

A. Provisioned Concurrency cho baseline, on-demand cho burst
B. Reserved Concurrency luôn loại cold start
C. Tăng timeout
D. Đặt Lambda public subnet

### Câu 46
Pipeline streaming throughput cao, replay tối đa 7 ngày, nhiều consumer độc lập đọc cùng dữ liệu.

A. SNS email
B. Kinesis Data Streams với retention và consumer applications riêng
C. SQS FIFO một queue
D. CloudTrail management events

### Câu 47
Athena query S3 chậm và scan quá nhiều; thường query theo ngày và region.

A. Một file cực lớn không partition
B. Copy hết vào EBS
C. Partition theo cột truy vấn và Parquet compressed
D. Tắt Glue Catalog

### Câu 48
Redis cache hơn 100 GB, read/write tăng, node đơn không đủ memory, cần latency thấp.

A. Memcached một node lớn
B. S3 Select
C. RDS read replica
D. ElastiCache Redis cluster mode enabled với sharding và replicas

### Câu 49
Batch đọc rất nhiều S3 objects và chạy song song hàng trăm EC2; muốn compute linh hoạt, không coordinator bottleneck.

A. S3 object storage, job queue và Auto Scaling workers
B. Instance store của một coordinator
C. RDS làm file system
D. Email từng file

### Câu 50
Private API qua API Gateway cần gọi internal NLB trong VPC mà backend không public.

A. Public IP cho NLB
B. VPC Link tới NLB
C. Internet Gateway giữa API Gateway và NLB
D. CloudFront tới private IP trực tiếp

### Câu 51
Multi-AZ app chỉ cần outbound Internet; muốn giảm blast radius khi một NAT lỗi.

A. Một NAT duy nhất tất cả AZ
B. Public IP cho database
C. NAT Gateway theo AZ với route table tương ứng
D. Internet Gateway trực tiếp private subnet

### Câu 52
Game backend cần anycast static IP, route tới healthy regional endpoint và tối ưu đường AWS global network.

A. Route 53 simple routing
B. NAT Gateway
C. S3 Transfer Acceleration cho API
D. AWS Global Accelerator

## Domain 4 — Design Cost-Optimized Architectures

### Câu 53
S3 object có access pattern không dự đoán được; muốn tự động tối ưu storage cost.

A. S3 Intelligent-Tiering
B. S3 Standard mãi mãi
C. EBS snapshots
D. DynamoDB TTL

### Câu 54
Compute usage ổn định nhưng instance family có thể đổi trong hai năm; muốn flexibility.

A. Standard Reserved Instance cố định type
B. Compute Savings Plan
C. Spot không fallback
D. Dedicated Host

### Câu 55
Web tier có baseline ổn định và burst campaign; burst có thể retry nhưng baseline không gián đoạn.

A. Tất cả On-Demand
B. Tất cả Spot
C. Baseline Savings Plan/On-Demand và burst Spot trong ASG
D. Dedicated Host cho burst

### Câu 56
DynamoDB workload mới chưa biết pattern, burst khó đoán; sau khi đo muốn tối ưu cost.

A. Provisioned rất thấp
B. Provisioned cao nhất
C. Dùng RDS vì DynamoDB burst
D. Bắt đầu on-demand, đo rồi cân nhắc provisioned với auto scaling

### Câu 57
Private EC2 chỉ gọi S3, DynamoDB và một số AWS APIs; NAT Gateway chiếm phần lớn network cost.

A. Gateway endpoints cho S3/DynamoDB và rà soát service thật sự cần NAT/interface endpoint
B. NAT Gateway mọi subnet
C. Public IP private EC2
D. Internet Gateway toàn bộ traffic

### Câu 58
Relational production traffic ổn định ban ngày, giảm mạnh ban đêm; capacity có thể thay đổi trong vài phút; engine tương thích Aurora.

A. Dedicated Host
B. Aurora Serverless v2 với min/max phù hợp
C. Instance lớn 24/7
D. EBS snapshot thay DB

### Câu 59
Website global có 95% static content và origin data transfer cost cao; muốn cache lâu.

A. Tắt cache
B. NAT Gateway cho static
C. CloudFront cache policy và compression phù hợp
D. Tăng EBS origin

### Câu 60
Database cần 20,000 IOPS nhưng throughput không cao; muốn điều chỉnh IOPS/throughput độc lập, không overprovision storage.

A. gp2 tăng dung lượng rất lớn
B. S3 Standard
C. EFS One Zone cho block DB
D. EBS gp3 với IOPS/throughput riêng nếu đạt yêu cầu

### Câu 61
Pipeline thường xuyên đọc dữ liệu giữa hai Region; muốn giảm data transfer nhưng vẫn multi-Region processing.

A. Đưa compute gần dữ liệu, selective transfer/replication thay vì kéo toàn dataset mỗi lần
B. NAT vì miễn phí cross-Region
C. CloudFront cho database replication
D. Tăng DNS TTL

### Câu 62
S3 log mới truy cập thường xuyên 30 ngày, sau đó hiếm đọc, sau 365 ngày xóa.

A. S3 Standard mãi mãi
B. Lifecycle transition theo tuổi và expiration sau 365 ngày
C. DynamoDB
D. EBS archive

### Câu 63
Development chỉ dùng giờ làm việc; muốn tự tắt compute nhưng không tác động production database.

A. Tắt mọi instance của account
B. S3 lifecycle stop EC2
C. Systems Manager Automation hoặc Instance Scheduler với tag scope rõ ràng
D. Đổi security group

### Câu 64
Finance phân bổ cost theo application, team, environment trên nhiều account, naming không nhất quán.

A. Chỉ tên resource
B. Chỉ security group name
C. Cộng CloudTrail thủ công
D. Chuẩn hóa cost allocation tags qua governance, kết hợp Cost Categories/Cost Explorer

### Câu 65
Nhiều account cần ngân sách theo application, cảnh báo forecast vượt ngưỡng và phát hiện anomaly chi phí.

A. AWS Budgets + Cost Explorer + Cost Anomaly Detection
B. CloudWatch CPU alarms
C. Route 53 health checks
D. AWS WAF

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

Nguồn format và tỷ trọng: [AWS SAA-C03 Exam Guide](https://docs.aws.amazon.com/aws-certification/latest/solutions-architect-associate-03.html).

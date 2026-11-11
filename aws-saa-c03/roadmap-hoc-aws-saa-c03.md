# Roadmap học AWS để thi AWS Certified Solutions Architect – Associate

> Lộ trình 16 tuần, khoảng 10–14 giờ mỗi tuần. Mục tiêu là AWS Certified Solutions Architect – Associate (SAA-C03). Trọng tâm không phải học thuộc tên dịch vụ, mà là phân tích yêu cầu, chọn kiến trúc đúng, hiểu trade-off về security, reliability, performance và cost.

## 1. Thông tin kỳ thi cần nắm

### Phiên bản và cấu trúc

- Mã bài thi: SAA-C03.
- Thời lượng: 130 phút.
- Số câu hiển thị: 65 câu.
- Dạng câu hỏi: multiple choice và multiple response.
- Theo exam guide, 50 câu được tính điểm và 15 câu không tính điểm; các câu không được trả lời bị tính sai và không có phạt khi đoán.
- Điểm đạt tối thiểu: scaled score 720/1000.
- Bốn domain:

| Domain | Tỷ trọng câu được chấm | Trọng tâm |
|---|---:|---|
| 1. Design Secure Architectures | 30% | IAM, data protection, network security, workload security |
| 2. Design Resilient Architectures | 26% | HA, backup, recovery, loose coupling, failure handling |
| 3. Design High-Performing Architectures | 24% | Compute, storage, database, network, ingestion |
| 4. Design Cost-Optimized Architectures | 20% | Pricing, right-sizing, lifecycle, serverless, caching |

AWS mô tả ứng viên mục tiêu là người có khoảng một năm kinh nghiệm thực hành thiết kế cloud solution; vì vậy roadmap luôn xen kẽ lý thuyết với lab.

### Cách đọc câu hỏi SAA

Trong mỗi câu, tìm theo thứ tự:

1. Workload là web app, data pipeline, batch, API, static content hay hybrid?
2. Ràng buộc bắt buộc là gì: RTO/RPO, latency, throughput, compliance, cost, operations?
3. Từ khóa chỉ mức độ ưu tiên: most secure, most cost-effective, least operational overhead, highly available, minimal changes.
4. Dịch vụ nào đáp ứng yêu cầu với ít thành phần và ít vận hành nhất?
5. Loại các đáp án vi phạm requirement trước khi so sánh trade-off.

## 2. Mục tiêu đầu ra

Sau 16 tuần, bạn có thể:

- Vẽ kiến trúc AWS có VPC, subnet, route, security group, IAM và flow dữ liệu rõ ràng.
- Chọn giữa EC2, ECS/Fargate, Lambda và EKS ở mức SAA.
- Chọn S3, EBS, EFS, FSx và storage class phù hợp.
- Chọn RDS/Aurora, DynamoDB, ElastiCache và OpenSearch theo access pattern.
- Thiết kế load balancing, Auto Scaling, Route 53, CloudFront và API Gateway.
- Thiết kế HA Multi-AZ, backup/restore, cross-Region và disaster recovery.
- Dùng SQS, SNS, EventBridge và Step Functions để loose coupling.
- Bảo vệ dữ liệu bằng IAM, KMS, Secrets Manager, TLS, VPC endpoint, WAF và CloudTrail.
- Tính được trade-off về cost, throughput, latency, durability và vận hành.
- Đọc metric CloudWatch, phân tích bottleneck và đề xuất cải thiện.
- Làm mock exam đạt ổn định từ 80–85% trước khi đăng ký thi.

## 3. Điều kiện đầu vào và môi trường học

### Nên biết trước

- Linux CLI, DNS, HTTP/HTTPS, TCP/IP, CIDR và TLS.
- Một ngôn ngữ lập trình hoặc scripting cơ bản.
- SQL, database transaction và cache.
- Docker và Git.
- Khái niệm load balancing, queue, replication, backup và monitoring.

### Tài khoản và công cụ

- AWS account riêng cho học tập.
- Bật MFA cho root user và không dùng root cho lab hằng ngày.
- Tạo IAM user/role học tập với quyền giới hạn.
- Bật Billing Alarm/Budget ngay ngày đầu.
- AWS CLI, AWS CloudShell hoặc terminal.
- Terraform hoặc CloudFormation tùy chọn; nên làm thủ công trước rồi IaC hóa.
- Draw.io, Excalidraw hoặc công cụ vẽ architecture.
- AWS Skill Builder, AWS Well-Architected Labs và AWS official practice questions.

### Guardrail chống phát sinh chi phí

1. Đặt ngân sách tháng rất thấp và alarm ở các mốc 50%, 80%, 100%.
2. Gắn tag Project, Environment, Owner và CostCenter cho resource.
3. Dùng một Region chính để học.
4. Xóa NAT Gateway, load balancer, EKS, RDS, Elastic IP và snapshot không dùng.
5. Kiểm tra Billing Cost Explorer sau mỗi lab lớn.
6. Không tạo resource production-size chỉ để thử.
7. Đọc pricing trước khi chạy dịch vụ tính phí theo giờ hoặc theo request.

## 4. Bộ dịch vụ cần nắm

| Nhóm | Dịch vụ và mức cần học |
|---|---|
| Identity | IAM, IAM policy, role, STS, Organizations, SCP, IAM Identity Center |
| Networking | VPC, subnet, route table, IGW, NAT Gateway, VPC endpoint, SG, NACL, ELB, Route 53, CloudFront, VPN, Direct Connect |
| Compute | EC2, AMI, EBS, Auto Scaling, ECS, Fargate, Lambda, ECR, EKS ở mức khái niệm |
| Storage | S3, storage class, lifecycle, versioning, replication, EBS, EFS, FSx, Storage Gateway |
| Database | RDS, Aurora, read replica, Multi-AZ, DynamoDB, DAX, ElastiCache, OpenSearch |
| Integration | SQS, SNS, EventBridge, Step Functions, Amazon MQ |
| Security | KMS, Secrets Manager, ACM, WAF, Shield, GuardDuty, Macie, Security Hub, CloudTrail |
| Observability | CloudWatch Metrics/Logs/Alarms, CloudTrail, AWS Config, X-Ray |
| Migration/data | DMS, DataSync, Snow Family, Transfer Family, Kinesis, Firehose, Glue, Athena |
| Governance/cost | AWS Budgets, Cost Explorer, Trusted Advisor, Control Tower ở mức nhận biết |

Không cần học mọi API của các dịch vụ trên. Cần hiểu use case, giới hạn, integration, pricing model và trade-off thường xuất hiện trong câu hỏi.

## 5. Roadmap 16 tuần

## Tuần 1 — Cloud fundamentals và tư duy Well-Architected

### Học

- Region, Availability Zone, edge location và global infrastructure.
- Shared Responsibility Model.
- Elasticity, scalability, availability, durability, fault tolerance và resilience.
- Horizontal scaling so với vertical scaling.
- Stateless application và externalized state.
- RTO, RPO, SLA, SLI, SLO.
- Well-Architected Framework:
  - Operational Excellence.
  - Security.
  - Reliability.
  - Performance Efficiency.
  - Cost Optimization.
  - Sustainability.
- CAP trade-off ở mức ứng dụng; eventual consistency.

### Thực hành

1. Chọn một web app và xác định user, workload, peak traffic, RTO/RPO.
2. Vẽ kiến trúc single AZ và Multi-AZ.
3. Review kiến trúc theo sáu pillar.
4. Tạo AWS Budget, MFA và tagging convention.

### Tiêu chí hoàn thành

- Giải thích được availability khác durability thế nào.
- Viết được requirement table trước khi chọn dịch vụ.
- Nhìn một architecture diagram và chỉ ra single point of failure.

## Tuần 2 — IAM, accounts và security fundamentals

### Học

- Root user, IAM user, group, role và policy.
- Identity-based policy và resource-based policy.
- Explicit deny, implicit deny và policy evaluation logic.
- ARN, action, resource, condition.
- IAM role cho EC2, Lambda, ECS task và cross-account access.
- STS AssumeRole và temporary credentials.
- MFA, password policy và access key rotation.
- IAM Access Analyzer.
- Organizations, account separation, SCP và permission boundary ở mức SAA.
- IAM Identity Center và federated identity ở mức nhận biết.

### Lab

1. Tạo role cho EC2 đọc S3.
2. Viết policy chỉ cho phép đọc một bucket/prefix.
3. Tạo explicit deny và xem kết quả evaluation.
4. Dùng AssumeRole giữa hai account nếu có môi trường phù hợp.
5. Kiểm tra IAM Access Analyzer.

### Bẫy đề thi

- Không cấp access key tĩnh cho EC2/Lambda nếu có IAM role.
- SCP không cấp quyền; SCP chỉ giới hạn quyền tối đa.
- Security group là stateful; NACL là stateless.
- IAM policy resource phải dùng ARN đúng loại dịch vụ.

### Tiêu chí hoàn thành

- Có thể giải thích vì sao một request bị AccessDenied.
- Viết least-privilege policy cho một workload nhỏ.
- Không dùng root user để tạo ứng dụng.

## Tuần 3 — VPC và networking căn bản

### Học

- VPC CIDR, subnet public/private/isolated.
- Route table, local route, default route.
- Internet Gateway và public IPv4.
- NAT Gateway cho private subnet đi Internet.
- NAT instance ở mức so sánh.
- Security group: inbound/outbound, stateful, reference SG.
- Network ACL: subnet level, stateless, rule number và ephemeral port.
- VPC DNS, DHCP option, resolver.
- VPC peering, Transit Gateway ở mức thiết kế.
- VPC endpoint:
  - Gateway endpoint cho S3/DynamoDB.
  - Interface endpoint/PrivateLink cho dịch vụ hỗ trợ.

### Lab

1. Tạo VPC với hai public và hai private subnet ở hai AZ.
2. Đặt ALB ở public subnet, EC2 ở private subnet.
3. Cho EC2 private gọi S3 qua endpoint.
4. Kiểm tra route table, SG flow và NACL.
5. Thử truy cập sai route để đọc flow logic.

### Tiêu chí hoàn thành

- Vẽ được packet path từ Internet tới ALB tới private EC2.
- Phân biệt Internet Gateway, NAT Gateway và VPC endpoint.
- Biết private subnet không có nghĩa là không có outbound Internet.

## Tuần 4 — Compute: EC2, AMI, EBS và Auto Scaling

### Học

- EC2 instance type: general purpose, compute, memory, storage, burstable.
- On-Demand, Reserved Instances, Savings Plans và Spot.
- AMI, user data, metadata và instance profile.
- EBS gp3, io2, throughput, IOPS, snapshot và encryption.
- Instance store và EBS khác nhau.
- Placement group: cluster, spread, partition.
- Auto Scaling Group, launch template, health check và lifecycle hook.
- Target tracking, step scaling và scheduled scaling.
- EC2 hibernation, stop/terminate/reboot.
- Elastic IP và khi nào không nên dùng.

### Lab

1. Tạo launch template từ AMI.
2. Dựng ASG trải trên hai AZ.
3. Gắn ALB và target group.
4. Tạo target tracking theo CPU hoặc request count.
5. Tạo EBS snapshot và restore volume.
6. Thử terminate instance để quan sát ASG thay thế.

### Bẫy đề thi

- ASG giúp scale nhưng không tự phân phối traffic nếu thiếu load balancer.
- EBS volume thường gắn trong một AZ; không attach trực tiếp sang AZ khác.
- Instance store mất dữ liệu khi instance stop/terminate.
- Spot có thể bị interrupt; không dùng cho workload không chịu interruption.

### Tiêu chí hoàn thành

- Chọn được EC2 family theo workload.
- Thiết kế ASG Multi-AZ có health check.
- Biết khi nào dùng EBS snapshot, AMI hoặc instance store.

## Tuần 5 — Load balancing, Route 53 và CloudFront

### Học

- ALB: Layer 7, path/host routing, HTTP/HTTPS, target group, health check.
- NLB: Layer 4, static IP, high throughput, TCP/UDP/TLS.
- Gateway Load Balancer ở mức nhận biết.
- Route 53 hosted zone, record, alias và TTL.
- Routing policy: simple, weighted, latency, failover, geolocation, geoproximity, multivalue.
- Health check và DNS failover.
- CloudFront distribution, origin, behavior, cache policy, origin request policy.
- Signed URL/cookie, invalidation và origin access control.
- Global Accelerator ở mức so sánh.

### Lab

1. Đặt ALB trước ASG.
2. Tạo path-based routing cho /api và /static.
3. Dùng Route 53 alias tới ALB.
4. Tạo CloudFront trước S3 hoặc ALB.
5. Quan sát cache hit/miss và TTL.

### Tiêu chí hoàn thành

- Chọn được ALB hay NLB theo protocol và routing requirement.
- Phân biệt Route 53 latency routing với geolocation routing.
- Biết CloudFront cache content gần user và giảm tải origin.

## Tuần 6 — S3 và hệ thống file

### Học

- S3 bucket/object/key, prefix và consistency hiện hành.
- Storage class: Standard, Intelligent-Tiering, Standard-IA, One Zone-IA, Glacier Instant/Flexible/Deep Archive.
- Versioning, delete marker, lifecycle rule và object lock.
- Encryption SSE-S3, SSE-KMS, SSE-C.
- Bucket policy, Block Public Access, ACL ở mức nhận biết.
- Presigned URL, multipart upload, transfer acceleration.
- S3 replication: Same-Region và Cross-Region.
- Event notification tới SNS, SQS, Lambda và EventBridge.
- EBS block storage.
- EFS shared elastic file system.
- FSx for Windows/Lustre/OpenZFS/ONTAP ở mức use case.
- Storage Gateway và DataSync ở mức migration.

### Lab

1. Tạo private S3 bucket bật Block Public Access.
2. Upload object bằng presigned URL.
3. Bật versioning và lifecycle sang Glacier.
4. Tạo bucket policy cho một role.
5. Gắn EFS cho hai EC2.
6. So sánh EBS, EFS và S3 trong một bảng.

### Tiêu chí hoàn thành

- Chọn được storage theo access pattern, latency, durability và cost.
- Biết khi nào dùng EFS thay vì EBS.
- Thiết kế được lifecycle để giảm chi phí storage.

## Tuần 7 — Database: RDS, Aurora và caching

### Học

- RDS engine, DB subnet group, parameter/option group.
- Multi-AZ standby so với read replica.
- RDS backup, automated backup, manual snapshot, PITR.
- Aurora cluster, writer/reader endpoint, Aurora Serverless và Global Database ở mức nhận biết.
- Read replica scaling và cross-Region read replica.
- ElastiCache Redis/Memcached.
- Cache-aside, session cache, TTL, invalidation.
- Database Proxy ở mức nhận biết.
- Encryption at rest, KMS và secret rotation.

### Lab

1. Tạo RDS trong private subnets.
2. Kết nối ứng dụng qua SG.
3. Bật Multi-AZ và tạo read replica nếu ngân sách cho phép.
4. Tạo snapshot, restore và PITR demo.
5. Dùng ElastiCache cho cache-aside.

### Bẫy đề thi

- Multi-AZ chủ yếu là HA/failover, không phải read scaling.
- Read replica dùng cho read scaling và eventual consistency.
- RDS không phải lựa chọn tốt cho key-value cực lớn hoặc schema linh hoạt như DynamoDB.

### Tiêu chí hoàn thành

- Phân biệt Multi-AZ, read replica, Aurora reader và ElastiCache.
- Chọn backup/restore strategy theo RPO/RTO.
- Vẽ database subnet và security flow đúng.

## Tuần 8 — DynamoDB và data access patterns

### Học

- Table, item, attribute, partition key, sort key.
- Simple primary key và composite primary key.
- Query so với Scan.
- GSI và LSI.
- Provisioned capacity, on-demand capacity, auto scaling.
- Read/write capacity, burst và throttling.
- Eventually consistent và strongly consistent read.
- DynamoDB Streams, TTL, conditional write và optimistic locking.
- Global Tables và multi-Region.
- DAX cho read-heavy low-latency use case.
- Single-table design ở mức SAA.

### Lab

1. Thiết kế table cho order theo customerId/orderId.
2. Viết Query theo partition key.
3. Tạo GSI cho access pattern thứ hai.
4. Test conditional write chống duplicate.
5. Bật TTL và Streams.

### Tiêu chí hoàn thành

- Thiết kế DynamoDB từ access pattern thay vì từ entity model.
- Giải thích GSI khác LSI.
- Nhận diện hot partition và throttling.

## Tuần 9 — Serverless: Lambda, API Gateway và Step Functions

### Học

- Lambda invocation: synchronous, asynchronous và event source mapping.
- Concurrency, reserved concurrency, provisioned concurrency.
- Cold start, timeout, memory, ephemeral storage và layers.
- Lambda execution role, environment variables và Secrets Manager.
- VPC Lambda và tác động đến networking/cold start.
- API Gateway REST, HTTP và WebSocket ở mức so sánh.
- Stages, throttling, usage plan, authorizer và custom domain.
- Lambda integration, mapping và error response.
- Step Functions Standard và Express.
- EventBridge Scheduler ở mức nhận biết.

### Lab

1. Tạo Lambda đọc S3 event.
2. Tạo API Gateway gọi Lambda.
3. Dùng reserved concurrency để bảo vệ downstream.
4. Tạo Step Functions gồm validate, process và catch.
5. Đo cold start và duration bằng CloudWatch.

### Tiêu chí hoàn thành

- Chọn được Lambda hay ECS/EC2 theo runtime, duration và control.
- Biết asynchronous Lambda có retry và DLQ/destination.
- Không nhét secret trực tiếp vào code hoặc image.

## Tuần 10 — Decoupling: SQS, SNS, EventBridge và messaging

### Học

- SQS Standard và FIFO.
- Visibility timeout, long polling, message retention, delay queue.
- Dead-letter queue, redrive policy và maxReceiveCount.
- Standard throughput và best-effort ordering.
- FIFO message group, deduplication và ordering theo group.
- SNS topic, subscription, filter policy và fan-out.
- SNS → SQS để tách publisher và consumer.
- EventBridge event bus, rule, target, event pattern và archive/replay.
- Step Functions và SQS/SNS/EventBridge chọn theo workflow.
- Amazon MQ ở mức use case migration.

### Lab

1. Tạo producer → SQS → Lambda/EC2 worker.
2. Cấu hình visibility timeout lớn hơn processing time.
3. Tạo DLQ và thử redrive.
4. Tạo SNS fan-out tới hai SQS queue.
5. Tạo EventBridge rule lọc OrderCreated.

### Bẫy đề thi

- Visibility timeout quá ngắn gây duplicate processing.
- SQS không tự đảm bảo exactly-once cho Standard.
- FIFO ordering nằm trong message group, không phải toàn queue.
- SNS là pub/sub; SQS là durable pull-based queue.

### Tiêu chí hoàn thành

- Chọn đúng SQS, SNS, EventBridge hay Step Functions.
- Thiết kế consumer idempotent.
- Có DLQ và metric age of oldest message.

## Tuần 11 — Security nâng cao và data protection

### Học

- KMS key, CMK/key policy, grants và envelope encryption.
- SSE-S3, SSE-KMS và client-side encryption.
- Secrets Manager so với Systems Manager Parameter Store.
- ACM certificate và TLS termination trên ALB/CloudFront/API Gateway.
- WAF web ACL, managed rules, rate-based rule.
- Shield Standard/Advanced ở mức phân biệt.
- CloudTrail management/data events.
- AWS Config rules và conformance pack ở mức nhận biết.
- GuardDuty, Macie, Inspector và Security Hub.
- VPC Flow Logs, CloudTrail Logs và CloudWatch Logs.
- Data exfiltration, least privilege và private endpoints.

### Lab

1. Tạo KMS key và mã hóa S3/RDS.
2. Đặt secret trong Secrets Manager và cấp role đọc secret.
3. Bật CloudTrail và truy vấn event.
4. Tạo WAF rate rule trước ALB hoặc API Gateway.
5. Bật VPC Flow Logs và kiểm tra log.

### Tiêu chí hoàn thành

- Chọn đúng KMS, Secrets Manager và Parameter Store.
- Biết log nào dùng CloudTrail, CloudWatch và VPC Flow Logs.
- Thiết kế defense in depth cho public API.

## Tuần 12 — Reliability, backup và disaster recovery

### Học

- Failure domain: instance, AZ, Region.
- Multi-AZ, multi-Region và active-active/active-passive.
- Backup/restore, pilot light, warm standby và multi-site.
- RTO/RPO trade-off và chi phí từng chiến lược.
- AWS Backup, EBS snapshot, RDS/Aurora backup, DynamoDB PITR.
- S3 versioning, replication và object lock.
- Route 53 failover và health check.
- Cross-Region read replica, Aurora Global Database, DynamoDB Global Tables.
- Chaos testing và game day.
- Graceful degradation, retry with backoff, circuit breaker và idempotency.

### Lab

1. Viết DR plan cho web app có RTO 1 giờ, RPO 15 phút.
2. Thực hiện backup/restore RDS hoặc DynamoDB.
3. Tạo route failover giữa hai endpoint.
4. Mô phỏng mất một AZ.
5. Vẽ dependency map và xác định điểm không được backup.

### Tiêu chí hoàn thành

- Chọn DR strategy theo RTO/RPO và ngân sách.
- Phân biệt backup restore với replication.
- Có runbook failover và failback.

## Tuần 13 — High performance và architecture patterns

### Học

- Caching ở CloudFront, ElastiCache, DAX và API Gateway.
- Read/write separation và asynchronous processing.
- CDN, compression, connection reuse và keep-alive.
- Auto Scaling theo CPU, request count, queue depth và custom metric.
- EC2 placement, enhanced networking và EBS optimization ở mức nhận biết.
- Kinesis Data Streams, Firehose và MSK ở mức use case.
- Glue, Athena và S3 data lake ở mức thiết kế.
- Batch processing với AWS Batch và Fargate ở mức nhận biết.
- Fan-out, event-driven, strangler migration và serverless pattern.
- Quotas, throttling, retry storm và backpressure.

### Lab

1. Đặt CloudFront trước S3/ALB.
2. Dùng queue depth để scale worker.
3. Tạo ingestion flow vào Kinesis/Firehose nếu có ngân sách.
4. Truy vấn dữ liệu S3 bằng Athena.
5. So sánh latency trước và sau cache.

### Tiêu chí hoàn thành

- Biết tối ưu compute, storage, database, network và ingestion.
- Chọn metric scaling đúng với bottleneck.
- Không dùng cache để che giấu thiết kế dữ liệu sai.

## Tuần 14 — Cost optimization và governance

### Học

- On-Demand, Reserved, Savings Plans và Spot.
- Right-sizing, scheduling non-production và Auto Scaling.
- S3 lifecycle, Intelligent-Tiering và archive.
- NAT Gateway cost và VPC endpoint cost trade-off.
- Data transfer cost: same AZ, cross AZ, cross Region, Internet.
- CloudFront và caching để giảm origin/data transfer.
- Serverless pay-per-request so với always-on compute.
- Cost allocation tags, Cost Explorer, Budgets và Cost Anomaly Detection.
- Trusted Advisor và Compute Optimizer.
- Organizations, consolidated billing, SCP và account strategy.

### Lab

1. Dự toán chi phí kiến trúc capstone.
2. So sánh NAT Gateway với S3 Gateway Endpoint.
3. Tạo lifecycle S3.
4. Tạo budget alarm.
5. Right-size một EC2/RDS giả lập dựa trên metric.

### Bẫy đề thi

- “Least operational overhead” không đồng nghĩa phương án rẻ nhất tuyệt đối.
- Spot rẻ nhưng không phù hợp workload cần ổn định.
- Cross-AZ traffic có thể phát sinh chi phí.
- NAT Gateway tính theo giờ và dữ liệu xử lý.

### Tiêu chí hoàn thành

- Biết đưa cost vào architecture decision.
- Giải thích được cost theo request, giờ, storage và data transfer.
- Có bảng cost estimate cho capstone.

## Tuần 15 — Tổng hợp theo domain và làm practice questions

### Việc cần làm theo domain

#### Domain 1 — Secure Architectures

- IAM role/policy, cross-account và SCP.
- VPC segmentation, SG/NACL, private subnet, endpoint.
- KMS, Secrets Manager, ACM, WAF, CloudTrail.
- S3 block public access và bucket policy.
- Encryption in transit/at rest.

#### Domain 2 — Resilient Architectures

- Multi-AZ, health check, Auto Scaling và load balancing.
- SQS/SNS/EventBridge, retry và DLQ.
- RDS Multi-AZ, Aurora, read replica, backup/PITR.
- S3 versioning/replication, DynamoDB Global Tables.
- DR strategy theo RTO/RPO.

#### Domain 3 — High-Performing Architectures

- EC2 family, EBS/EFS/S3.
- CloudFront, Route 53, ALB/NLB.
- RDS read scaling, DynamoDB key design, ElastiCache/DAX.
- Lambda concurrency, API Gateway, Kinesis/Firehose.
- Caching, asynchronous processing và partitioning.

#### Domain 4 — Cost-Optimized Architectures

- Pricing model compute/storage/database.
- Right-sizing, Savings Plans, Reserved, Spot.
- Lifecycle, tiering, serverless, caching.
- Data transfer và NAT/VPC endpoint.
- Budgets, tags và governance.

### Practice workflow

1. Làm từng câu trong thời gian giới hạn.
2. Viết lý do chọn đáp án trước khi xem giải thích.
3. Ghi lại requirement bị bỏ sót.
4. Phân loại lỗi: service gap, architecture gap, wording trap, calculation error.
5. Tạo flashcard từ trade-off, không chỉ từ định nghĩa.

### Tiêu chí hoàn thành

- Hoàn thành ít nhất 300–500 câu chất lượng.
- Mỗi câu sai có một ghi chú “vì sao đáp án khác sai”.
- Mock đầu tiên không cần điểm cao; mục tiêu là tìm gap.

## Tuần 16 — Mock exam và chốt kiến thức

### Lịch đề xuất

- Ngày 1: Mock full 65 câu trong 130 phút.
- Ngày 2: Review Domain 1 và 2.
- Ngày 3: Review Domain 3 và 4.
- Ngày 4: Mock full thứ hai.
- Ngày 5: Ôn weak areas, service limits và pricing traps.
- Ngày 6: Mock cuối, mô phỏng đúng điều kiện thi.
- Ngày 7: Nghỉ, xem mind map và chuẩn bị giấy tờ.

### Ngưỡng sẵn sàng

- Đạt ổn định 80–85% trên nhiều bộ câu hỏi khác nhau.
- Không còn domain nào dưới 70%.
- Có thể giải thích trade-off mà không cần học thuộc đáp án.
- Làm xong mock trong khoảng 100–110 phút để còn thời gian review.
- Không bị nhầm các cặp:
  - Multi-AZ và read replica.
  - Security group và NACL.
  - SQS và SNS.
  - Gateway endpoint và interface endpoint.
  - EBS và EFS.
  - CloudFront và Global Accelerator.
  - RDS và DynamoDB.
  - NAT Gateway và Internet Gateway.

## 6. Project thực hành xuyên suốt

### Project: Serverless e-commerce order platform

Xây một hệ thống bán hàng nhỏ với các yêu cầu:

- Public API cho tạo order.
- Authentication và authorization.
- Product catalog và order data.
- Static frontend.
- Xử lý payment/notification bất đồng bộ.
- Có retry, DLQ và idempotency.
- Có dashboard, log và alert.
- Có backup và DR plan.
- Có cost estimate.

### Kiến trúc gợi ý

~~~text
User
  │
CloudFront + WAF
  │
API Gateway
  │
Lambda / ECS
  ├── RDS or DynamoDB
  ├── ElastiCache
  └── EventBridge / SNS
          ├── SQS payment
          ├── SQS notification
          └── Step Functions

S3 static assets
CloudWatch + CloudTrail
KMS + Secrets Manager
~~~

### Definition of Done

- Có architecture diagram và data-flow diagram.
- Có IAM role riêng cho từng workload.
- Database không public Internet.
- Có Multi-AZ hoặc lý do rõ ràng nếu không dùng.
- Có retry/DLQ/idempotency.
- Có encryption at rest và in transit.
- Có CloudWatch dashboard và alarm.
- Có backup/restore test.
- Có monthly cost estimate.
- Có Well-Architected review theo sáu pillar.
- Có README giải thích vì sao chọn từng dịch vụ.

## 7. Cách học từng dịch vụ

Với mỗi dịch vụ, ghi một trang theo mẫu:

1. Dịch vụ giải quyết vấn đề gì?
2. Thành phần chính là gì?
3. Khi nào nên dùng?
4. Khi nào không nên dùng?
5. Availability/durability mặc định ra sao?
6. Scale theo chiều nào?
7. Security boundary ở đâu?
8. Pricing tính theo gì?
9. Integration quan trọng nào?
10. Failure mode và metric nào cần theo dõi?
11. Dịch vụ nào thường bị nhầm với nó?
12. Một architecture pattern và một anti-pattern.

## 8. Checklist trước khi đăng ký thi

### Kiến thức

- [ ] Nắm bốn exam domains và tỷ trọng.
- [ ] Đọc hết SAA-C03 exam guide.
- [ ] Hiểu Well-Architected Framework và sáu pillar.
- [ ] Làm được architecture trade-off không cần nhớ máy móc.
- [ ] Biết các service limits và default behavior thường gặp.

### Thực hành

- [ ] Đã làm VPC public/private Multi-AZ.
- [ ] Đã làm EC2 + ALB + ASG.
- [ ] Đã làm S3 lifecycle/versioning/presigned URL.
- [ ] Đã làm RDS backup/Multi-AZ/read replica.
- [ ] Đã làm DynamoDB key/GSI/conditional write.
- [ ] Đã làm Lambda + API Gateway.
- [ ] Đã làm SQS/SNS/EventBridge + DLQ.
- [ ] Đã làm CloudWatch, CloudTrail, KMS và Secrets Manager.
- [ ] Đã kiểm tra budget và xóa resource.

### Thi thử

- [ ] Có ít nhất 3 mock full.
- [ ] Điểm mock ổn định từ 80–85%.
- [ ] Review toàn bộ câu sai.
- [ ] Làm câu hỏi đúng thời gian.
- [ ] Không phụ thuộc vào một bộ đề bị học thuộc.

## 9. Kỹ thuật làm bài

### Khi đọc câu hỏi

1. Đọc câu cuối trước để biết đang hỏi requirement nào.
2. Gạch chân từ khóa: least operational overhead, cost-effective, highly available, private, real-time, durable, minimal changes.
3. Xác định workload và constraint.
4. Loại đáp án cần tự vận hành quá nhiều nếu đề yêu cầu managed/serverless.
5. Loại đáp án vi phạm security hoặc single-AZ.
6. So sánh hai đáp án cuối theo đúng requirement, không theo sở thích cá nhân.

### Phân bổ thời gian

- 130 phút cho 65 câu, trung bình khoảng 2 phút/câu.
- Câu dài: đánh dấu và đi tiếp.
- Câu multiple response: kiểm tra số đáp án cần chọn.
- Không để câu trống.
- Dành 10–15 phút cuối để review câu đánh dấu.

### Những từ khóa hay gợi ý đáp án

| Từ khóa | Hướng suy nghĩ |
|---|---|
| Least operational overhead | Managed/serverless |
| Highly available | Multi-AZ, health check, failover |
| Lowest cost | Right-size, lifecycle, serverless, Spot nếu phù hợp |
| Decouple | SQS/SNS/EventBridge |
| Fan-out | SNS hoặc EventBridge |
| Ordered exactly once | FIFO + dedup/idempotency, xem scope guarantee |
| Global low latency | CloudFront, Route 53 latency, Global Accelerator |
| Private access to AWS service | VPC endpoint |
| Read-heavy relational | Read replica, cache, Aurora reader |
| Unpredictable key-value scale | DynamoDB on-demand |
| Disaster recovery | RTO/RPO rồi chọn backup/replication strategy |

## 10. Tài liệu chính thức

- [AWS Certified Solutions Architect – Associate SAA-C03 Exam Guide](https://docs.aws.amazon.com/aws-certification/latest/solutions-architect-associate-03.html) — mục tiêu ứng viên, format, điểm, domain và service scope.
- [AWS Certified Solutions Architect – Associate certification page](https://aws.amazon.com/certification/certified-solutions-architect-associate/) — thông tin đăng ký, exam prep plan và practice resources.
- [AWS Well-Architected Framework](https://docs.aws.amazon.com/wellarchitected/latest/framework/) — nguyên tắc thiết kế theo sáu pillar.
- [AWS Well-Architected Tool](https://aws.amazon.com/well-architected-tool/) — review workload và theo dõi improvement plan.
- [AWS Skill Builder](https://skillbuilder.aws/) — digital courses, Builder Labs và exam preparation.
- [AWS Architecture Center](https://aws.amazon.com/architecture/) — reference architecture và pattern.
- [AWS Pricing Calculator](https://calculator.aws/) — lập cost estimate.
- [AWS Free Tier](https://aws.amazon.com/free/) — kiểm tra giới hạn miễn phí hiện hành trước khi chạy lab.
- [AWS Documentation](https://docs.aws.amazon.com/) — đọc service limits, pricing, quotas và best practices mới nhất.

## 11. Nếu chỉ có 8 tuần

### Tuần 1–2

- IAM, VPC, subnet, route, SG/NACL, EC2 và ALB.

### Tuần 3–4

- S3, EBS/EFS, RDS/Aurora, DynamoDB, ElastiCache.

### Tuần 5

- Lambda, API Gateway, SQS, SNS, EventBridge và Step Functions.

### Tuần 6

- CloudFront, Route 53, CloudWatch, CloudTrail, KMS, WAF và DR.

### Tuần 7

- Cost, Well-Architected, mock questions và review domain yếu.

### Tuần 8

- Ba mock full, sửa lỗi, ôn service comparison và thi.

Mục tiêu của roadmap là giúp bạn đọc đề như một Solutions Architect: bắt đầu từ yêu cầu và trade-off, sau đó mới chọn dịch vụ AWS phù hợp.

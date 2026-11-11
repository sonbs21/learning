# AWS SAA-C03 Mock Exam 01 — Answer key

|Câu|ĐA|Giải thích ngắn|
|---:|:---:|---|
|1|B|Role/instance profile cấp credential tạm thời cho EC2.|
|2|B|Identity Center hỗ trợ workforce identity và federation.|
|3|A|SourceVpce trong bucket policy giới hạn endpoint.|
|4|A|Secrets Manager hỗ trợ secret rotation.|
|5|A|WAF bảo vệ lớp HTTP/API.|
|6|A|Organization trail tập trung API activity.|
|7|A|S3 gateway endpoint giữ traffic trong AWS.|
|8|A|SCP là guardrail ở Organizations.|
|9|A|KMS CMK + CloudTrail cho key control/audit.|
|10|AB|Boundary giới hạn role; SCP giới hạn account/OU.|
|11|A|User Pool xác thực; Identity Pool cấp credential tạm thời.|
|12|A|Transit Gateway đơn giản hóa nhiều VPC/on-prem.|
|13|AB|CloudFront signed URL và S3 presigned URL đều tạm thời.|
|14|A|Task role là quyền theo ECS task.|
|15|A|SG có thể tham chiếu app SG.|
|16|A|Config kiểm tra compliance; Security Hub tổng hợp finding.|
|17|A|Secrets Manager/Parameter Store tránh hard-code.|
|18|AB|NAT public subnet + route private subnet.|
|19|A|Config ghi nhận và đánh giá cấu hình.|
|20|A|HTTPS listener + ACM mã hóa tới ALB.|
|21|A|ASG đa AZ + ALB loại single point of failure.|
|22|A|SQS bền vững; visibility timeout/DLQ xử lý lỗi.|
|23|A|Queue hấp thụ burst và tách producer/consumer.|
|24|A|RDS Multi-AZ có standby và failover tự động.|
|25|A|ElastiCache là lớp cache latency thấp.|
|26|A|Blue/green/canary hỗ trợ gradual shift/rollback.|
|27|AB|Cả hai thiết kế đều stateless, đa AZ và scale.|
|28|A|DLQ giữ message lỗi sau số retry.|
|29|A|SNS fanout tới queue riêng cho consumer độc lập.|
|30|A|Failover routing dùng health check.|
|31|A|Global tables là multi-Region multi-active.|
|32|A|S3 bền vững/scale; CloudFront phục vụ tĩnh.|
|33|AB|Pilot light/warm standby và backup theo lịch có thể đáp ứng RPO/RTO.|
|34|A|Visibility timeout ngắn gây message xuất hiện lại.|
|35|A|ASG thay instance unhealthy.|
|36|A|CloudFront cache tại edge.|
|37|A|DynamoDB managed multi-AZ; PITR phục hồi dữ liệu.|
|38|A|EFS là POSIX shared filesystem đa AZ.|
|39|A|io2 phù hợp IOPS cao ổn định.|
|40|A|Read replica scale read; standby không dành cho read scaling.|
|41|A|Redis phù hợp cache key-value latency thấp.|
|42|A|Aurora Serverless v2 tự điều chỉnh capacity.|
|43|AB|Athena query S3; Glue Catalog lưu schema.|
|44|A|CloudFront giảm latency và origin request.|
|45|A|DynamoDB phù hợp quy mô request lớn/latency ổn định.|
|46|A|Direct Connect cung cấp dedicated connectivity.|
|47|A|Aurora Auto Scaling scale read replicas.|
|48|A|Spot phù hợp job interruptible.|
|49|A|S3 event kích hoạt Lambda.|
|50|AB|Fargate giảm quản server; Service Auto Scaling scale theo metric.|
|51|A|VPC endpoint là private path tới AWS services.|
|52|A|Latency routing chọn Region latency thấp.|
|53|A|Savings Plans/RIs phù hợp usage ổn định dài hạn.|
|54|A|Spot rẻ và phù hợp workload retry được.|
|55|A|Glacier Flexible Retrieval hợp archive phục hồi trong giờ.|
|56|A|Cost Optimization Hub/Trusted Advisor có khuyến nghị cost.|
|57|A|Lambda/API Gateway tính theo usage.|
|58|AB|Intelligent-Tiering và lifecycle chuyển tier.|
|59|A|On-demand hợp traffic khó đoán/burst.|
|60|A|Tags phân bổ; Cost Explorer phân tích.|
|61|A|CloudFront giảm origin request và transfer.|
|62|A|Scheduler tránh compute chạy nhàn rỗi.|
|63|A|Baseline committed + Spot burst cân bằng cost/availability.|
|64|A|Gateway endpoint S3/DynamoDB tránh NAT hourly/data processing.|
|65|A|Budgets hỗ trợ budget/forecast alerts.|

## Phân bổ

- Domain 1: 1–20 = 20 câu
- Domain 2: 21–37 = 17 câu
- Domain 3: 38–52 = 15 câu
- Domain 4: 53–65 = 13 câu

Tỷ lệ đề: 30.8% / 26.2% / 23.1% / 20.0%, xấp xỉ tỷ trọng chính thức 30% / 26% / 24% / 20%. Đây là đề tự biên soạn, không phải câu hỏi thật của AWS.


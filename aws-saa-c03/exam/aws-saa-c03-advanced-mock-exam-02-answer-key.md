# AWS SAA-C03 Mock Exam 02 — Answer Key

|Câu|ĐA|Giải thích chi tiết|
|---:|:---:|---|
|1|A|Cross-account role cấp credential tạm thời và trust policy giới hạn đúng account gọi role. ExternalId giảm rủi ro confused deputy; access key, public bucket và security group không giải quyết IAM authorization.|
|2|B|IAM Identity Center kết nối IdP, hỗ trợ permission sets và MFA tập trung. IAM user hoặc access key theo account tạo nhiều credential khó thu hồi nhất quán.|
|3|C|Origin Access Control cho phép CloudFront ký request tới S3, còn bucket policy chỉ cho distribution được chỉ định đọc. Public-read hoặc URL không hết hạn vẫn cho phép bypass.|
|4|D|IRSA cấp IAM role theo Kubernetes service account, tách quyền giữa pod dù dùng chung node. Node role hoặc ConfigMap access key tạo blast radius lớn.|
|5|A|Secrets Manager hỗ trợ encryption, rotation và runtime retrieval; task role chỉ được phép đọc ARN secret cụ thể. Task definition, image hoặc access key không nên chứa secret dài hạn.|
|6|B|Account B cần quyền dùng KMS key qua key policy hoặc grant, đồng thời snapshot phải được chia sẻ đúng cách. Tắt encryption không đáp ứng yêu cầu kiểm soát dữ liệu.|
|7|BD|WAF rate rule giảm abuse ở HTTP layer; API Gateway throttling giới hạn tốc độ ở API layer. Flow Logs, EBS và lifecycle không điều tiết request ứng dụng.|
|8|C|Explicit Deny trong bucket policy từ chối PutObject khi encryption header thiếu hoặc sai KMS key. Default encryption hữu ích nhưng không thay thế enforcement nếu request sai phải bị reject.|
|9|D|Session Manager dùng IAM và SSM Agent, không cần inbound SSH hoặc bastion; session/command có thể audit. VPC endpoints giúp private subnet gọi SSM khi không dùng NAT.|
|10|A|IAM policy giới hạn object ARN/prefix, endpoint policy giới hạn bucket/service. Security group không hiểu object prefix và policy rộng không đạt least privilege.|
|11|CE|Organization trail gửi log về log archive account tách khỏi workload admin; Object Lock bảo vệ log khỏi xóa hoặc overwrite trong retention.|
|12|B|User Pool xác thực và phát JWT claims để API authorize. Identity Pool chỉ cần khi client cần AWS credentials trực tiếp.|
|13|C|SCP explicit Deny giới hạn quyền tối đa ở account/OU, kể cả member administrator. Security group/NACL và IAM policy riêng lẻ không bao phủ mọi principal.|
|14|D|Leading-key condition giúp giới hạn item theo tenant khi kết hợp authorization application layer. Scan toàn table và security group không tạo data boundary.|
|15|A|Macie phát hiện PII và dữ liệu nhạy cảm trong S3 bằng pattern/ML. Các dịch vụ còn lại không phải công cụ S3 data discovery.|
|16|AD|Block Public Access ngăn public S3 access; Config rules/conformance packs phát hiện baseline không tuân thủ. Hai lớp này bổ sung nhau.|
|17|B|ACM quản lý private key và tự renew certificate; ALB terminate HTTPS. Self-signed/user-data phải tự xoay vòng, EBS encryption không bảo vệ network traffic.|
|18|C|Peering hoặc Transit Gateway chỉ là đường private, không mặc định mã hóa end-to-end ở application layer. TLS giữa service mới đáp ứng yêu cầu compliance.|
|19|D|Secrets Manager rotation cập nhật credential và ứng dụng đọc runtime nên không cần deploy lại. Plaintext, image hoặc tag dễ lộ và không hỗ trợ rotation chuẩn.|
|20|BE|MFA và break-glass giảm rủi ro root; alert từ CloudTrail/EventBridge/Security Hub tạo phát hiện khi root sign-in. Root access key hoặc chia sẻ password là không an toàn.|
|21|A|FIFO dùng MessageGroupId để giữ thứ tự theo customer nhưng xử lý song song giữa các group; deduplication giảm duplicate trong window.|
|22|B|SNS fanout tạo bản sao event cho từng SQS queue, mỗi service có retry/DLQ độc lập. Queue chung không bảo đảm cả ba service đều nhận event.|
|23|C|ELB health check giúp ASG chỉ đưa instance readiness đạt yêu cầu vào traffic; grace period tránh thay instance trong lúc load model. EC2 status check chỉ phản ánh hạ tầng.|
|24|D|Multi-Region write cùng item có thể conflict. Single-writer, conditional writes và conflict logic làm rõ ownership thay vì giả định replica tự giải quyết nghiệp vụ.|
|25|A|Multi-AZ bảo vệ failover; read replicas tạo read capacity. Standby không nên dùng làm read endpoint và snapshot không scale read.|
|26|B|Aurora Global Database replicate nhanh giữa Region và hỗ trợ promotion/failover cho DR. Snapshot hằng ngày hoặc Multi-AZ không đạt cùng RPO/RTO.|
|27|C|CloudFront origin group chuyển secondary khi primary lỗi trong khi client giữ URL. DNS TTL và NAT không xử lý origin failover ở distribution.|
|28|D|Step Functions có Retry cho lỗi transient và Catch cho lỗi terminal để chuyển nhánh xử lý. Queue timeout không mô hình hóa workflow nhiều bước.|
|29|A|Partial batch response báo message nào fail để Lambda chỉ retry phần đó; idempotency vẫn cần vì distributed systems có thể giao lại.|
|30|B|Versioning giữ bản cũ và Object Lock ngăn xóa/overwrite trong retention. Storage class chỉ ảnh hưởng cost, không tạo immutability.|
|31|C|ECS service đa AZ, ALB health checks và desired count duy trì task; Service Auto Scaling scale theo request metric.|
|32|D|AWS Backup quản lý lịch, lifecycle, cross-Region copy và vault policy tập trung. AMI thủ công thiếu retention và governance nhất quán.|
|33|A|EventBridge resource policy nhận event cross-account; SQS hoặc Lambda xử lý theo nhu cầu và SQS có thể buffer khi consumer down.|
|34|B|SQS là durable buffer để nhận request trước, worker scale sau và DLQ giữ message lỗi. Đây là backpressure tốt hơn gọi đồng bộ một backend.|
|35|C|Expand-and-contract giữ schema backward-compatible, cho phép deploy app dần rồi bỏ phần cũ. Destructive migration trước có thể làm version cũ lỗi.|
|36|D|Warm standby/pilot light kết hợp cross-Region backup cân bằng RTO và chi phí. Multi-AZ không chống Region failure, active-active full capacity đắt hơn yêu cầu.|
|37|A|Versioning khôi phục object cũ; lifecycle quản lý noncurrent versions để tránh storage tăng vô hạn. Invalidation chỉ tác động cache.|
|38|B|Cluster placement group đặt instance gần nhau để tối ưu latency và throughput. Spread ưu tiên fault isolation, partition ưu tiên nhóm phần cứng.|
|39|C|FSx for Windows hỗ trợ SMB, Active Directory và shared file system cho Windows. EFS chủ yếu NFS/Linux-oriented và instance store không bền vững.|
|40|D|Partition key cardinality thấp tạo hot partition dù tổng capacity còn dư. Composite/high-cardinality key phân tán request; tăng capacity không sửa được skew.|
|41|A|Aurora reader endpoint phân phối connection tới replicas, không cần application biết từng endpoint. Cluster endpoint dành cho writer/failover.|
|42|B|Multipart upload upload song song và retry từng part; Transfer Acceleration có thể giúp đường truyền xa tới S3. Một PUT duy nhất dễ fail toàn file.|
|43|C|Content-hash filename tạo URL mới khi content đổi, cho phép cache dài hạn mà không invalidate hàng nghìn object.|
|44|D|Public VIF dành cho truy cập AWS public services qua Direct Connect, thay vì public Internet. Private VIF tới VPC không thay thế đường public-service này.|
|45|A|Provisioned Concurrency giữ environment warm cho baseline; on-demand xử lý burst. Reserved Concurrency chỉ giới hạn quota, không luôn loại cold start.|
|46|B|Kinesis Data Streams có retention và nhiều consumer đọc độc lập, phù hợp replay. SQS/SNS không cung cấp cùng mô hình stream replay.|
|47|C|Partition và Parquet compression giảm dữ liệu Athena phải scan, từ đó giảm latency và cost. Không partition khiến query đọc quá nhiều file/data.|
|48|D|Redis cluster mode sharding phân tán data/throughput, replicas tăng availability và read capacity. Một node lớn là giới hạn memory và failure domain.|
|49|A|S3 tách durable object storage khỏi compute; queue chia job và ASG scale worker theo backlog. Coordinator/instance store đơn lẻ tạo bottleneck.|
|50|B|VPC Link cho API Gateway private integration tới NLB trong VPC mà không publicize backend. Public IP hoặc Internet Gateway là đường đi không cần thiết.|
|51|C|NAT Gateway theo AZ tránh single point of failure và cross-AZ traffic, với route table tương ứng. Một NAT duy nhất rẻ hơn nhưng không đạt resilience.|
|52|D|Global Accelerator cung cấp anycast static IP, health-based regional routing và AWS global network. Route 53 chỉ cung cấp DNS steering.|
|53|A|Intelligent-Tiering tự chuyển access tier khi pattern thay đổi, phù hợp dữ liệu không dự đoán được. Standard vĩnh viễn thường đắt hơn.|
|54|B|Compute Savings Plan linh hoạt hơn Standard RI giữa instance family, size, Region và compute service. Spot không đảm bảo baseline.|
|55|C|Savings Plan/On-Demand bảo vệ baseline; Spot dùng cho burst có thể retry. Dùng Spot toàn bộ không đáp ứng baseline không gián đoạn.|
|56|D|On-demand tránh dự đoán sai lúc mới triển khai; sau khi đo có thể tối ưu bằng provisioned autoscaling. Capacity thấp throttling, capacity cao lãng phí.|
|57|A|Gateway endpoints cho S3/DynamoDB loại bỏ NAT khỏi hai đường đi phổ biến; service khác mới cân nhắc NAT/interface endpoint.|
|58|B|Aurora Serverless v2 co giãn capacity theo workload với min/max phù hợp, giảm capacity ban đêm mà vẫn giữ relational engine.|
|59|C|CloudFront cache và compression giảm origin requests và origin data transfer. Tắt cache hoặc thêm EBS không giải quyết global static cost.|
|60|D|gp3 cho phép cấu hình IOPS và throughput độc lập với dung lượng trong giới hạn, tránh tăng storage chỉ để lấy IOPS.|
|61|A|Đặt compute gần dữ liệu và selective transfer giảm GB cross-Region; kéo toàn dataset mỗi lần và NAT đều không tối ưu cost.|
|62|B|Lifecycle transition theo tuổi rồi expiration tự động hóa cả storage tier và xóa log sau retention. Giữ Standard hoặc copy DB tốn hơn.|
|63|C|Scheduler/SSM chạy theo tag và lịch, chỉ dừng development được chọn. Scope rõ ràng là điều kiện để không tác động production.|
|64|D|Cost allocation tags chuẩn hóa qua governance, kết hợp Cost Categories/Cost Explorer, đáng tin hơn naming không nhất quán.|
|65|A|Budgets cảnh báo actual/forecast, Cost Explorer phân tích và Cost Anomaly Detection phát hiện chi bất thường. CPU alarm và WAF không có billing context.|

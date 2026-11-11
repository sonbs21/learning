# Tuần 14 — Cost optimization và governance

> Mục tiêu: biết thiết kế một kiến trúc vừa đáp ứng yêu cầu kỹ thuật vừa kiểm soát được chi phí theo thời gian; hiểu chi phí phát sinh từ compute, storage, request, data transfer và vận hành; đồng thời xây dựng được cơ chế governance để chi phí không bị tăng ngoài kiểm soát.

## 1. Vì sao cost optimization là một kỹ năng kiến trúc?

Cost optimization không có nghĩa là chọn phương án có hóa đơn thấp nhất trong tháng đầu tiên. Một phương án rẻ nhưng làm tăng lỗi, tăng thời gian vận hành hoặc không đáp ứng được traffic có thể trở thành phương án đắt hơn về lâu dài.

Khi đánh giá một thiết kế, cần nhìn đồng thời vào:

- Chi phí trực tiếp của dịch vụ.
- Chi phí dữ liệu truyền giữa các thành phần.
- Chi phí do dư thừa capacity.
- Chi phí vận hành và nhân sự.
- Chi phí của downtime, mất dữ liệu hoặc xử lý sự cố.
- Khả năng tăng trưởng của workload.
- Mức độ linh hoạt khi traffic thay đổi.

Một quyết định cost tốt thường có dạng:

> Với workload và yêu cầu hiện tại, chọn phương án A vì tổng chi phí sở hữu phù hợp hơn, nhưng vẫn giữ điều kiện B để có thể chuyển sang phương án C khi traffic hoặc SLA thay đổi.

Không nên hỏi riêng “dịch vụ nào rẻ hơn?”. Hãy hỏi:

1. Workload này có đặc điểm gì?
2. Nó chạy bao nhiêu giờ?
3. Traffic ổn định hay biến động?
4. Dữ liệu được truy cập thường xuyên hay hiếm khi?
5. Có thể xử lý bất đồng bộ không?
6. Có thể chấp nhận bị gián đoạn không?
7. Chi phí nào là chi phí cố định, chi phí nào tăng theo usage?
8. Ai là người chịu trách nhiệm khi chi phí tăng?

## 2. Kết quả cần đạt

Sau tuần này, bạn cần có khả năng:

- Phân biệt On-Demand, Reserved Instances, Savings Plans và Spot.
- Chọn pricing model dựa trên tính ổn định, thời lượng và khả năng bị gián đoạn của workload.
- Right-size EC2, RDS, EBS, Lambda và các tài nguyên liên quan dựa trên metric thực tế.
- Lập lịch tắt môi trường non-production và kết hợp Auto Scaling.
- Dùng S3 Lifecycle, Intelligent-Tiering và archive storage đúng cách.
- Phân tích trade-off giữa NAT Gateway, S3 Gateway Endpoint và Interface Endpoint.
- Nhận diện chi phí same-AZ, cross-AZ, cross-Region, Internet egress và CDN.
- Giải thích khi nào serverless có thể rẻ hơn always-on compute và khi nào không.
- Dùng tags, Cost Explorer, Budgets, Cost Anomaly Detection, Trusted Advisor và Compute Optimizer.
- Hiểu consolidated billing, Organizations, SCP và chiến lược tách AWS account.
- Tạo bảng ước tính chi phí cho capstone và đưa cost vào architecture decision.
- Tránh các bẫy đề thi như “rẻ nhất” nhưng không đáp ứng availability, hoặc “serverless” nhưng lại phát sinh NAT và request cost lớn.

## 3. Lịch học gợi ý

| Buổi | Nội dung | Thời lượng |
|---|---|---:|
| 1 | Cost model, pricing model và right-sizing | 3 giờ |
| 2 | Storage lifecycle, data transfer, NAT và VPC Endpoint | 3 giờ |
| 3 | Cost visibility, tagging, budgets và anomaly detection | 2–3 giờ |
| 4 | Organizations, consolidated billing, SCP và account strategy | 2 giờ |
| 5 | Cost estimate cho capstone và các lab thực hành | 2–3 giờ |

---

## 4. Cost model: AWS tính tiền theo những đơn vị nào?

Muốn tối ưu chi phí, trước hết phải xác định đơn vị tính tiền của từng thành phần. Phần lớn câu hỏi AWS SAA không yêu cầu tính ra con số tuyệt đối; đề thường kiểm tra bạn có nhận ra loại chi phí nào sẽ tăng hay không.

### 4.1. Chi phí theo thời gian

Các tài nguyên chạy liên tục thường có chi phí theo giờ hoặc theo giây, tùy dịch vụ và loại tài nguyên.

Ví dụ:

- EC2 instance khi ở trạng thái running.
- NAT Gateway theo giờ.
- VPC Interface Endpoint theo Availability Zone và thời gian tồn tại.
- RDS database instance khi đang chạy.
- ElastiCache node.
- Load balancer theo thời gian cộng với đơn vị xử lý riêng.

Câu hỏi cần đặt ra:

- Tài nguyên có thực sự cần chạy 24/7 không?
- Môi trường dev/test có thể tắt ngoài giờ làm việc không?
- Có thể dùng Auto Scaling để giảm capacity khi không có traffic không?
- Một managed service có tính phí cho phần control plane hoặc node tối thiểu không?

### 4.2. Chi phí theo dung lượng lưu trữ

Storage thường được tính theo GB-tháng, nhưng có thể kèm phí request, retrieval hoặc minimum storage duration.

Các nguồn chi phí hay gặp:

- S3 object storage.
- EBS volume.
- EBS snapshot.
- EFS.
- RDS storage và backup.
- DynamoDB table và index.
- Logs trong CloudWatch Logs.
- AMI và snapshot cũ.

Đừng chỉ nhìn dung lượng hiện tại. Hãy kiểm tra:

- Dữ liệu tăng bao nhiêu mỗi tháng?
- Có versioning tạo ra nhiều bản cũ không?
- Có snapshot hoặc volume mồ côi không?
- Có log retention vô thời hạn không?
- Dữ liệu có thể chuyển sang tier rẻ hơn không?

### 4.3. Chi phí theo request hoặc capacity

Một số dịch vụ tính theo số lần gọi, số request, số message, số execution hoặc capacity đã cấp phát.

Ví dụ:

- Lambda request và duration.
- API Gateway request.
- S3 PUT, GET, LIST và các request khác.
- SQS request hoặc payload chunk.
- SNS publish và delivery.
- Step Functions state transition.
- DynamoDB read/write capacity hoặc on-demand request.
- Kinesis shard-hour và dữ liệu xử lý.

Một API có ít người dùng nhưng mỗi request gọi qua nhiều lớp vẫn có thể tạo chi phí đáng kể. Khi ước tính, hãy vẽ đường đi của một request:

    Client
      ↓
    CloudFront → API Gateway → Lambda → DynamoDB
      └────── cache hit có thể tránh gọi origin

Sau đó hỏi mỗi request đi qua bao nhiêu dịch vụ và mỗi dịch vụ tính theo đơn vị nào.

### 4.4. Chi phí data transfer

Data transfer thường là phần bị bỏ sót vì compute và storage dễ nhìn thấy hơn.

Các tình huống cần chú ý:

- Dữ liệu đi từ AWS ra Internet.
- Dữ liệu đi giữa các Region.
- Dữ liệu đi qua Availability Zone khác.
- Dữ liệu từ private subnet đi ra ngoài qua NAT Gateway.
- Origin phải trả nhiều dữ liệu vì cache hit ratio thấp.
- Một service gọi service khác qua đường vòng thay vì giao tiếp nội bộ phù hợp.

Nguyên tắc thực tế:

> Không chỉ tối ưu số lượng byte; hãy tối ưu cả đường đi của byte.

---

## 5. Pricing model cho compute

### 5.1. On-Demand

On-Demand cho phép trả tiền theo mức sử dụng mà không cần cam kết dài hạn.

Phù hợp với:

- Workload mới, chưa biết rõ mức sử dụng.
- Traffic không ổn định hoặc có tính thử nghiệm.
- Môi trường ngắn hạn.
- Workload cần chuyển loại instance hoặc Region thường xuyên.
- Hệ thống quan trọng nhưng chưa đủ dữ liệu để cam kết.

Ưu điểm:

- Linh hoạt.
- Không có commitment.
- Dễ thay đổi capacity.

Nhược điểm:

- Đơn giá thường cao hơn các mô hình cam kết.
- Nếu workload chạy ổn định 24/7, tổng chi phí có thể cao.

Đừng chọn Reserved hoặc Savings Plans chỉ vì thấy phần trăm giảm giá hấp dẫn. Nếu workload chưa ổn định, commitment sai có thể làm giảm tính linh hoạt.

### 5.2. Reserved Instances

Reserved Instances thường phù hợp với workload có nhu cầu tương đối ổn định trong một khoảng thời gian.

Cần phân biệt:

- Standard Reserved Instance: giảm giá tốt hơn nhưng ít linh hoạt hơn.
- Convertible Reserved Instance: có thể đổi sang cấu hình hoặc thuộc tính khác trong phạm vi cho phép, thường đổi lại mức giảm giá thấp hơn.
- Scheduled Reserved Instances là khái niệm lịch sử; khi làm bài mới, cần đọc kỹ wording và tài liệu hiện hành.

Phù hợp với:

- RDS hoặc EC2 chạy ổn định.
- Workload production có baseline capacity rõ ràng.
- Tổ chức đã có lịch sử sử dụng đủ dài.

Không phù hợp với:

- Workload thử nghiệm.
- Capacity thay đổi mạnh.
- Hệ thống chưa xác định được loại instance hoặc Region.

### 5.3. Savings Plans

Savings Plans là commitment về mức chi tiêu compute theo giờ trong thời hạn nhất định để nhận mức giá thấp hơn.

Hai ý cần nhớ:

- Compute Savings Plans linh hoạt hơn về instance family, Region và cách sử dụng compute.
- EC2 Instance Savings Plans thường cho mức giảm tốt hơn nhưng commitment cụ thể hơn.

Savings Plans có thể áp dụng cho nhiều dịch vụ compute đủ điều kiện như EC2, Lambda và Fargate tùy loại plan và điều kiện. Khi làm đề, tập trung vào bản chất:

> Workload có mức chi tiêu compute ổn định, nhưng muốn linh hoạt hơn so với việc khóa vào một cấu hình EC2 cụ thể.

### 5.4. Spot Instances

Spot dùng capacity EC2 nhàn rỗi của AWS với mức giá thấp hơn, nhưng instance có thể bị gián đoạn khi AWS cần lấy lại capacity.

Phù hợp với:

- Batch processing.
- Render hoặc xử lý dữ liệu có thể checkpoint.
- CI/CD worker có thể tạo lại.
- Big data worker.
- Stateless web tier khi có đủ On-Demand hoặc Reserved baseline.

Không phù hợp với:

- Database chính.
- Workload cần chạy liên tục.
- Xử lý không thể resume.
- Hệ thống không có cơ chế thay thế instance bị terminate.

Một architecture tốt với Spot thường có:

- Auto Scaling Group.
- Nhiều instance type và nhiều Availability Zone.
- Capacity rebalancing nếu cần.
- Checkpoint hoặc idempotent job.
- On-Demand base capacity cho mức tối thiểu.
- Queue để giữ công việc chưa xử lý.

Không nên hiểu “Spot rẻ” thành “Spot dùng được cho mọi workload”. Giá thấp là đánh đổi cho khả năng bị thu hồi.

### 5.5. Bảng chọn nhanh

| Đặc điểm workload | Lựa chọn thường phù hợp |
|---|---|
| Mới, chưa đo được usage | On-Demand |
| Baseline ổn định, ít thay đổi | Reserved hoặc Savings Plans |
| Compute usage ổn định nhưng muốn linh hoạt | Compute Savings Plans |
| Gắn chặt với instance family/Region | EC2 Instance Savings Plans hoặc RI |
| Có thể bị gián đoạn, xử lý lại được | Spot |
| Burst ngắn, không muốn quản lý server | Lambda hoặc serverless phù hợp |
| Chạy liên tục nhưng traffic thay đổi | Baseline cam kết + On-Demand/Auto Scaling |

---

## 6. Right-sizing: dùng đúng lượng tài nguyên

Right-sizing là điều chỉnh tài nguyên về mức đủ đáp ứng workload, thay vì luôn chọn cấu hình lớn để “cho an toàn”.

### 6.1. Quy trình right-sizing

1. Ghi nhận baseline trong một khoảng thời gian đủ đại diện.
2. Kiểm tra peak chứ không chỉ average.
3. Đối chiếu metric với SLO và error budget.
4. Kiểm tra bottleneck liên quan, không chỉ CPU.
5. Thử cấu hình nhỏ hơn trong môi trường kiểm soát.
6. Đo lại latency, throughput, error rate và cost.
7. Đặt rollback plan.
8. Lặp lại khi traffic hoặc code thay đổi.

Các metric cần xem:

- CPU utilization.
- Memory utilization.
- Network in/out.
- EBS IOPS và throughput.
- Disk queue length.
- Connection count.
- Request latency p95/p99.
- RDS freeable memory, CPU, storage, IOPS và database connections.
- Lambda duration, memory utilization và throttles.
- Queue depth hoặc age của message.

### 6.2. Bẫy “CPU thấp nên giảm instance”

CPU thấp không nhất thiết có nghĩa instance đang dư thừa. Workload có thể bị giới hạn bởi:

- Memory.
- Network bandwidth.
- EBS throughput.
- Database connection.
- Lock hoặc I/O wait.
- Một thread đơn không tận dụng được nhiều vCPU.
- Burst credit của burstable instance đã cạn.

Right-sizing phải nhìn vào bottleneck thực tế. Nếu giảm size khi memory đang sát ngưỡng, hệ thống có thể bắt đầu swap hoặc OOM dù CPU vẫn thấp.

### 6.3. EC2

Khi right-size EC2, xem:

- Instance family có phù hợp workload không.
- General purpose, compute optimized, memory optimized và storage optimized có khác nhau thế nào.
- Có thể dùng Graviton/ARM không nếu ứng dụng và dependency hỗ trợ.
- EBS có đang dư IOPS hoặc throughput không.
- Có instance chạy nhưng không phục vụ traffic không.
- Có thể dùng Auto Scaling thay cho một instance rất lớn không.

Ví dụ tư duy:

- Web tier stateless: nhiều instance vừa phải thường linh hoạt hơn một instance rất lớn.
- In-memory workload: memory optimized có thể phù hợp hơn việc tăng vCPU không cần thiết.
- Batch có thể bị gián đoạn: dùng Spot worker thay vì trả On-Demand cho toàn bộ capacity.

### 6.4. RDS

RDS cần được đánh giá theo:

- CPU.
- Memory và freeable memory.
- Database connections.
- Read/write IOPS.
- Storage throughput.
- Read latency và write latency.
- Read replica có đang giảm tải thật hay chỉ tạo thêm chi phí.
- Multi-AZ là yêu cầu availability hay đang bị hiểu nhầm là read scaling.

Nếu workload cần read scaling, có thể cân nhắc read replica hoặc caching. Nếu workload cần failover, Multi-AZ là quyết định reliability chứ không phải một cách tăng read capacity.

### 6.5. Lambda

Lambda có trade-off đặc biệt:

- Tăng memory thường đồng thời tăng CPU và có thể làm execution nhanh hơn.
- Memory cao hơn không tự động đắt hơn nếu thời gian chạy giảm đủ nhiều.
- Function quá nhỏ có thể chạy lâu, làm tăng duration và timeout.
- Provisioned Concurrency giúp giảm cold start nhưng tạo chi phí khi luôn giữ execution environment sẵn sàng.
- VPC access có thể kéo theo NAT và network cost nếu function cần đi ra Internet.

Khi tối ưu Lambda, hãy đo cost per invocation và cost per successful business operation, không chỉ memory setting.

---

## 7. Scheduling non-production và Auto Scaling

### 7.1. Tắt tài nguyên ngoài giờ

Môi trường dev, test, sandbox thường không cần chạy 24/7. Có thể giảm chi phí bằng cách:

- Stop EC2 ngoài giờ.
- Dùng Instance Scheduler hoặc automation theo tag.
- Scale Auto Scaling Group về min/desired bằng 0 khi an toàn.
- Tắt RDS non-production theo lịch nếu service và yêu cầu cho phép.
- Xóa ephemeral environment sau khi hoàn thành test.
- Đặt retention cho log và snapshot không cần thiết.

Cần kiểm tra trước khi tắt:

- Có job scheduled chạy ban đêm không?
- Có integration test hoặc backup phụ thuộc vào môi trường không?
- EBS, Elastic IP hoặc NAT có còn tạo phí sau khi stop không?
- Dịch vụ có hỗ trợ stop/start đúng theo yêu cầu không?
- Startup time có ảnh hưởng đến người dùng không?

Dừng EC2 thường không xóa EBS volume. Vì vậy hóa đơn có thể vẫn có phần storage.

### 7.2. Auto Scaling

Auto Scaling giúp capacity bám theo nhu cầu. Nhưng Auto Scaling không tự động làm mọi hệ thống rẻ hơn.

Cần chọn:

- Min capacity đủ cho availability.
- Desired capacity cho traffic bình thường.
- Max capacity để bảo vệ chi phí và quota.
- Metric phản ánh bottleneck, ví dụ CPU, request count per target hoặc queue age.
- Scale-out nhanh hơn scale-in để tránh thiếu capacity.
- Cooldown hoặc warm-up phù hợp.
- Instance lifecycle và graceful termination.

Một cấu hình max quá cao có thể tạo hóa đơn lớn khi có lỗi hoặc traffic bất thường. Vì vậy max capacity vừa là tham số performance vừa là guardrail chi phí.

### 7.3. Tách baseline và burst

Một pattern phổ biến:

- Dùng Reserved/Savings Plans cho baseline ổn định.
- Dùng On-Demand cho phần biến động.
- Dùng Spot cho phần có thể bị gián đoạn.
- Dùng Auto Scaling để điều chỉnh tổng capacity.

Đây là cách cân bằng giữa giá, độ ổn định và khả năng đáp ứng burst.

---

## 8. S3, lifecycle và archive storage

### 8.1. Các lớp lưu trữ

Một cách nhớ đơn giản:

| Tier | Dùng khi |
|---|---|
| S3 Standard | Truy cập thường xuyên, cần latency thấp |
| S3 Intelligent-Tiering | Chưa biết chắc access pattern hoặc access thay đổi |
| S3 Standard-IA | Ít truy cập nhưng cần truy xuất nhanh khi cần |
| S3 One Zone-IA | Dữ liệu có thể tái tạo và chấp nhận rủi ro của một AZ |
| S3 Glacier Instant Retrieval | Archive nhưng vẫn cần truy cập nhanh |
| S3 Glacier Flexible Retrieval | Archive, chấp nhận thời gian retrieval |
| S3 Glacier Deep Archive | Lưu trữ rất lâu, truy cập hiếm |

Tên tier không đủ để chọn. Cần xét:

- Tần suất truy cập.
- Thời gian cần lấy lại dữ liệu.
- Số lần retrieval.
- Minimum storage duration.
- Yêu cầu resilience.
- Phí request và retrieval.
- Dữ liệu có thể tái tạo hay không.

### 8.2. Lifecycle rule

Một lifecycle rule có thể:

- Chuyển object sang tier rẻ hơn sau một số ngày.
- Xóa object sau thời gian retention.
- Xóa noncurrent version.
- Xóa incomplete multipart upload.
- Áp dụng theo prefix hoặc tag.

Ví dụ policy logic:

    0–30 ngày: S3 Standard
    31–90 ngày: S3 Standard-IA
    91–365 ngày: Glacier Flexible Retrieval
    Sau 365 ngày: xóa nếu hết retention

Khoảng thời gian trên chỉ là ví dụ. Không nên sao chép máy móc vì lifecycle phải dựa trên access pattern và yêu cầu compliance.

### 8.3. Các nguồn phí S3 hay bị quên

- Versioning giữ nhiều bản cũ.
- Incomplete multipart uploads.
- Request lớn do ứng dụng gọi object quá nhỏ hoặc quá nhiều.
- Retrieval từ Glacier hoặc IA.
- Replication tạo thêm storage và request.
- Data transfer khi tải object ra ngoài AWS hoặc Internet.
- Log bucket lưu vô thời hạn.
- Backup không có expiration.

Best practice:

- Dùng tag hoặc prefix để phân loại retention.
- Xác định retention owner.
- Đặt lifecycle cho current và noncurrent version.
- Theo dõi storage tăng trưởng.
- Xem xét nén hoặc gộp file nhỏ khi phù hợp.
- Không đưa dữ liệu cần truy cập thường xuyên vào archive chỉ vì archive rẻ.

### 8.4. Intelligent-Tiering

Intelligent-Tiering phù hợp khi access pattern không chắc chắn hoặc thay đổi. Dịch vụ tự chuyển object giữa các access tier theo hành vi truy cập.

Trade-off:

- Giảm nhu cầu dự đoán thủ công.
- Có monitoring/automation charge nhỏ.
- Không phải lựa chọn tự động rẻ nhất cho mọi object.
- Cần kiểm tra kích thước object và access pattern.

Câu hỏi thi thường mô tả dữ liệu có access pattern không thể dự đoán và muốn giảm chi phí vận hành. Khi đó Intelligent-Tiering có thể phù hợp hơn việc tự viết job chuyển tier.

---

## 9. EBS, EFS và backup storage

### 9.1. EBS

Kiểm tra các điểm sau:

- Volume có còn attached hoặc đang bị bỏ quên không.
- gp2 có thể chuyển sang gp3 với cấu hình IOPS/throughput phù hợp không.
- Snapshot cũ có lifecycle hoặc retention rõ ràng không.
- Có volume quá lớn so với actual usage không.
- Có snapshot trùng lặp do automation sai không.
- Instance đã terminate nhưng volume vẫn giữ lại không.

Chuyển sang volume rẻ hơn chỉ an toàn nếu volume mới vẫn đáp ứng IOPS, throughput, latency và durability cần thiết.

### 9.2. EFS

EFS phù hợp khi nhiều compute node cần filesystem dùng chung và cần elasticity. Tuy nhiên, nếu workload chỉ cần block storage cho một EC2, EBS thường là lựa chọn tự nhiên hơn.

Các điểm cần xem:

- Standard và Infrequent Access.
- Lifecycle policy cho file ít truy cập.
- Throughput mode.
- Dữ liệu có cần shared filesystem thật sự không.
- Có thể dùng S3 cho object thay vì EFS không.

### 9.3. Backup

Backup rẻ không đồng nghĩa retention vô hạn. Nên định nghĩa:

- RPO/RTO.
- Retention theo ngày/tuần/tháng.
- Dữ liệu nào cần restore nhanh.
- Dữ liệu nào có thể archive.
- Ai phê duyệt xóa backup.
- Cách kiểm tra backup có restore được không.

Một snapshot không có lifecycle sẽ dần trở thành chi phí ẩn.

---

## 10. NAT Gateway và VPC Endpoint

Đây là chủ đề quan trọng vì NAT Gateway vừa có chi phí theo giờ vừa có chi phí theo dữ liệu xử lý.

### 10.1. NAT Gateway hoạt động thế nào?

NAT Gateway cho phép tài nguyên trong private subnet khởi tạo kết nối ra ngoài, thường là Internet, mà không cho Internet khởi tạo kết nối ngược trực tiếp vào private resource.

Một đường đi thường gặp:

    Private subnet
        ↓
    Route table
        ↓
    NAT Gateway ở public subnet
        ↓
    Internet Gateway
        ↓
    Internet hoặc AWS endpoint công khai

Chi phí có thể phát sinh từ:

- NAT Gateway tồn tại theo giờ.
- Dữ liệu đi qua NAT Gateway theo GB.
- Data transfer khác trên đường đi.
- Cross-AZ traffic nếu private subnet dùng NAT Gateway ở AZ khác.

### 10.2. S3 Gateway Endpoint

S3 Gateway Endpoint cho phép tài nguyên trong VPC truy cập S3 qua route table mà không cần đi qua NAT Gateway hoặc Internet Gateway.

Lợi ích:

- Không có phí theo giờ như NAT Gateway.
- Tránh phí NAT data processing cho traffic S3.
- Traffic S3 không cần đi qua public Internet.
- Có thể giới hạn bucket bằng endpoint policy.

Khi private workload chủ yếu truy cập S3, đây thường là lựa chọn quan trọng để giảm cost và đơn giản hóa đường đi.

DynamoDB cũng hỗ trợ Gateway Endpoint. Cần phân biệt Gateway Endpoint với Interface Endpoint.

### 10.3. Interface Endpoint

Interface Endpoint dùng ENI trong subnet và được hỗ trợ bởi AWS PrivateLink. Nó phù hợp khi cần truy cập nhiều AWS service hoặc endpoint của service/partner qua private IP.

Trade-off:

- Có phí theo giờ cho mỗi AZ/endpoint.
- Có phí xử lý dữ liệu.
- Tăng số lượng ENI và cấu hình security group.
- Có thể giảm phụ thuộc vào NAT.
- Tăng private connectivity và kiểm soát network path.

Không nên tạo Interface Endpoint cho mọi service chỉ vì “private tốt hơn”. Hãy so sánh:

- NAT hourly + data processing.
- Interface endpoint hourly theo AZ + data processing.
- Số AZ cần high availability.
- Lưu lượng thực tế.
- Yêu cầu security và network isolation.

### 10.4. Chọn kiến trúc NAT hay Endpoint

| Tình huống | Hướng cân nhắc |
|---|---|
| Private subnet truy cập nhiều bucket S3 | S3 Gateway Endpoint |
| Private subnet truy cập DynamoDB | DynamoDB Gateway Endpoint |
| Cần private access đến AWS service hỗ trợ PrivateLink | Interface Endpoint |
| Cần truy cập Internet thật sự | NAT Gateway |
| Lưu lượng outbound lớn, đi qua một AZ khác | Xem xét giảm cross-AZ hoặc bố trí NAT theo AZ |
| Workload nhỏ, ít traffic nhưng nhiều endpoint | So sánh tổng phí, không mặc định endpoint rẻ hơn |

### 10.5. Bẫy cross-AZ với NAT

Nếu private subnet ở AZ-a gửi traffic đến NAT Gateway ở AZ-b, có thể phát sinh cross-AZ data transfer. Với workload lớn, chi phí này có thể đáng kể.

Các hướng xử lý:

- Tạo NAT Gateway theo từng AZ cần dùng.
- Định tuyến private subnet đến NAT Gateway cùng AZ.
- Dùng Gateway Endpoint cho S3/DynamoDB.
- Giảm traffic outbound không cần thiết.
- Đánh đổi chi phí NAT theo giờ với chi phí cross-AZ dựa trên workload thực tế.

Nhiều NAT Gateway tăng chi phí cố định nhưng có thể giảm cross-AZ và tăng resilience. Đây là trade-off, không phải một đáp án đúng cho mọi hệ thống.

---

## 11. Data transfer và thiết kế đường đi của dữ liệu

### 11.1. Same-AZ, cross-AZ và cross-Region

Trong đề thi, hãy nhận diện vị trí của các thành phần:

- EC2 và RDS ở cùng AZ hay khác AZ?
- Application gọi database qua cross-AZ thường xuyên không?
- Load balancer phân phối traffic sang AZ khác không?
- Replication đi giữa AZ hoặc Region nào?
- Có central service ở một AZ khiến tất cả workload phải đi ngang AZ đó không?

Cross-AZ thường được chọn để tăng availability, nhưng traffic giữa các AZ có thể có chi phí. Không nên loại bỏ Multi-AZ chỉ để giảm phí nếu yêu cầu availability cần nó.

Cách tối ưu đúng là:

- Giữ redundancy cần thiết.
- Hạn chế chatty cross-AZ communication.
- Đặt thành phần có lưu lượng lớn gần nhau khi phù hợp.
- Batch hoặc aggregate dữ liệu.
- Dùng cache để giảm số lần gọi.
- Theo dõi chi phí trước và sau thay đổi.

### 11.2. Internet egress

Dữ liệu đi ra Internet thường là nguồn chi phí lớn, đặc biệt với:

- Video, ảnh hoặc file download.
- Backup tải ra ngoài.
- API response lớn.
- Log hoặc dữ liệu analytics xuất ra ngoài.
- Client ở nhiều khu vực.

CloudFront có thể giúp giảm tải origin và cải thiện latency, nhưng vẫn cần xem pricing của CloudFront, origin fetch và data transfer. CDN không làm mọi traffic miễn phí.

### 11.3. CloudFront và caching

CloudFront có thể giảm:

- Số request đến origin.
- Data transfer từ origin.
- Load trên EC2, ALB, API hoặc S3.
- Latency cho người dùng ở xa Region.

Để cache hiệu quả:

- Chọn cache key vừa đủ, tránh đưa query string/cookie không cần thiết vào cache key.
- Dùng TTL phù hợp.
- Dùng versioned filename cho static asset.
- Nén response.
- Không cache dữ liệu cá nhân hoặc response phụ thuộc authorization nếu chưa thiết kế đúng.
- Theo dõi cache hit ratio.
- Dùng invalidation có chủ đích vì invalidation hàng loạt có thể tạo chi phí và làm giảm hit ratio.

Tối ưu cache cần cân bằng:

- Freshness.
- Correctness.
- Privacy.
- Origin cost.
- User latency.

### 11.4. Nén, batching và pagination

Một cách giảm chi phí data transfer là giảm byte phải truyền:

- Bật compression cho response phù hợp.
- Dùng pagination thay vì trả toàn bộ dataset.
- Batch message hoặc record khi service hỗ trợ.
- Tránh polling quá thường xuyên.
- Dùng event-driven hoặc long polling phù hợp.
- Lưu object đã nén trong S3 nếu access pattern cho phép.

---

## 12. Serverless và pay-per-request

Serverless có thể giảm chi phí vận hành vì không phải duy trì server nhàn rỗi, nhưng không phải lúc nào cũng rẻ nhất.

### 12.1. Khi serverless thường có lợi

- Traffic thấp hoặc biến động mạnh.
- Request xử lý ngắn.
- Có thể scale về gần zero.
- Không muốn quản lý OS và capacity.
- Workload event-driven.
- Thời gian chạy không liên tục.

### 12.2. Khi always-on có thể phù hợp hơn

- Traffic cao và ổn định 24/7.
- Workload chạy liên tục.
- Function duration dài hoặc gọi rất nhiều lần.
- Cần kiểm soát runtime/network đặc thù.
- Chi phí per-request cộng dồn vượt baseline EC2/Fargate.
- Cần giảm cold start bằng Provisioned Concurrency ở quy mô lớn.

Đề thi thường kiểm tra “least operational overhead”, không phải “lowest price tuyệt đối”. Lambda có thể có operational overhead thấp hơn EC2 ngay cả khi một workload ổn định có thể chạy rẻ hơn trên container hoặc EC2.

### 12.3. Chi phí ẩn trong serverless

- API Gateway request.
- Lambda duration và request.
- Provisioned Concurrency.
- Step Functions state transitions.
- NAT Gateway nếu function trong private subnet cần Internet.
- VPC Interface Endpoint nếu thay NAT.
- CloudWatch Logs.
- Retries làm tăng số execution.
- Fan-out làm một event tạo nhiều invocation.

Một thiết kế serverless tốt cần có:

- Idempotency.
- Retry có giới hạn.
- DLQ hoặc destination khi phù hợp.
- Timeout và concurrency limit.
- Log retention.
- Monitoring cost per request.
- Kiểm soát recursive invocation.

---

## 13. Cost allocation tags

Tags là nền tảng để biết “ai đang tạo chi phí và tạo cho mục đích gì”.

### 13.1. Tag tối thiểu nên có

Một bộ tag thường hữu ích:

| Tag | Ý nghĩa |
|---|---|
| Application | Ứng dụng hoặc hệ thống |
| Environment | dev, test, staging, prod |
| Owner | Team hoặc người chịu trách nhiệm |
| CostCenter | Trung tâm chi phí |
| Project | Dự án hoặc initiative |
| DataClass | Mức phân loại dữ liệu |
| ManagedBy | Terraform, CloudFormation hoặc thủ công |
| ExpirationDate | Ngày hết hạn cho tài nguyên tạm thời |

Tên tag cần nhất quán. Environment, environment và Env không nên cùng tồn tại nếu muốn báo cáo chính xác.

### 13.2. Tag không thay thế governance

Tag có thể bị thiếu, sai hoặc không áp dụng được cho mọi loại resource. Vì vậy nên kết hợp:

- Tag policy.
- SCP giới hạn hành động nguy hiểm.
- AWS Config rule để phát hiện resource thiếu tag.
- Automation gắn tag mặc định khi tạo.
- Account/OUs để tách boundary.
- Cost category để nhóm chi phí theo business logic.

### 13.3. Chiến lược tag tốt

- Quy định tag bắt buộc.
- Xác định tag nào được dùng cho billing.
- Gắn tag ngay từ provisioning.
- Không dùng tag tự do cho dữ liệu nhạy cảm.
- Kiểm tra resource không tag theo định kỳ.
- Gắn owner có thể hành động, không chỉ ghi “team”.

---

## 14. Cost Explorer, Budgets và Cost Anomaly Detection

### 14.1. Cost Explorer

Cost Explorer giúp phân tích chi phí theo các chiều như:

- Service.
- Account.
- Region.
- Usage type.
- Tag hoặc cost category.
- Thời gian.
- Amortized hoặc unblended view tùy nhu cầu phân tích.

Cách dùng thực tế:

1. Xem tổng cost theo tháng.
2. Tách theo service.
3. Drill down vào usage type.
4. So sánh actual với forecast.
5. Xác định service hoặc account tăng bất thường.
6. Gắn phần tăng đó với deployment, traffic hoặc thay đổi architecture.

Đừng chỉ nhìn tổng bill. Một con số tổng không cho biết phần tăng đến từ NAT, data transfer, requests hay compute.

### 14.2. AWS Budgets

Budgets dùng để đặt ngưỡng và cảnh báo theo:

- Actual cost.
- Forecasted cost.
- Usage.
- RI utilization/coverage hoặc Savings Plans tùy loại budget.

Một budget tốt cần:

- Scope rõ: account, service, tag hoặc cost category.
- Ngưỡng actual và forecast.
- Người nhận cảnh báo.
- Mức độ nghiêm trọng khác nhau.
- Runbook khi cảnh báo xảy ra.
- Tránh tự động xóa production chỉ vì vượt budget.

Budget alarm là cơ chế phát hiện và phản ứng, không phải cơ chế làm chi phí tự giảm.

### 14.3. Cost Anomaly Detection

Cost Anomaly Detection dùng để phát hiện chi phí lệch khỏi pattern thông thường.

Nó hữu ích khi:

- Một service tăng usage đột ngột.
- Có vòng lặp gọi API.
- Có tài nguyên bị quên.
- Data transfer tăng bất thường.
- Có deployment sai cấu hình.

Cần hiểu giới hạn:

- Detection có thể có độ trễ.
- “Bất thường” không đồng nghĩa “sai”; traffic sale hợp lệ cũng có thể tạo anomaly.
- Cần gắn người nhận alert với quy trình xử lý.
- Không nên thay thế metric và monitoring service.

### 14.4. Cost Categories và CUR

Cost Categories giúp nhóm chi phí theo logic business như:

- Product.
- Team.
- Environment.
- Business unit.
- Shared platform.

AWS Cost and Usage Report cung cấp dữ liệu chi tiết hơn cho phân tích và chargeback/showback. Với cấp độ SAA, chỉ cần hiểu vai trò:

- Cost Explorer: phân tích nhanh.
- Budgets: ngưỡng và cảnh báo.
- Cost Anomaly Detection: phát hiện lệch pattern.
- CUR: dữ liệu chi tiết để xây dựng báo cáo hoặc hệ thống FinOps.

---

## 15. Trusted Advisor và Compute Optimizer

### 15.1. Trusted Advisor

Trusted Advisor đưa ra các kiểm tra và khuyến nghị trên nhiều nhóm như:

- Cost optimization.
- Performance.
- Security.
- Fault tolerance.
- Service limits.

Trong bài thi, Trusted Advisor thường xuất hiện khi đề muốn bạn:

- Tìm tài nguyên nhàn rỗi.
- Nhận diện cơ hội giảm chi phí.
- Kiểm tra security hoặc service limit.
- Có một dashboard recommendation tập trung.

Trusted Advisor không tự động sửa mọi vấn đề. Hãy xem recommendation, xác minh workload và đánh giá tác động trước khi áp dụng.

### 15.2. Compute Optimizer

Compute Optimizer phân tích metric và configuration để đưa ra recommendation cho những tài nguyên đủ điều kiện như:

- EC2.
- Auto Scaling Group.
- EBS.
- Lambda.
- ECS/Fargate hoặc tài nguyên khác tùy phạm vi hỗ trợ hiện tại.

Điểm quan trọng:

- Recommendation dựa trên dữ liệu quan sát.
- Dữ liệu chưa đủ đại diện có thể làm recommendation kém chính xác.
- Không nên áp dụng tự động mà không kiểm tra SLO, peak và dependency.
- Một recommendation rẻ hơn có thể giảm headroom hoặc thay đổi behavior.

---

## 16. AWS Organizations, consolidated billing và account strategy

### 16.1. Vì sao tách nhiều account?

Một account riêng cho production, security, logging, shared services và sandbox giúp:

- Cô lập blast radius.
- Tách quyền truy cập.
- Tách billing và ownership.
- Áp dụng policy khác nhau.
- Dễ điều tra và audit.
- Giảm nguy cơ test nhầm vào production.

Một account không nên được xem là chỉ để “chia bill”. Account là một security và governance boundary.

### 16.2. Consolidated billing

AWS Organizations cho phép gom nhiều account vào một tổ chức và quản lý billing tập trung.

Lợi ích:

- Có một payer/management account.
- Xem tổng usage.
- Có thể chia sẻ benefit đủ điều kiện của RI hoặc Savings Plans giữa các account.
- Quản lý account tập trung.
- Tạo policy và OU.

Cần cẩn thận:

- Sharing benefit có thể làm phân bổ chi phí khó đọc hơn.
- Management account cần được bảo vệ đặc biệt.
- Phải có quy ước chargeback/showback rõ ràng.
- Không nên dùng root user cho công việc thường ngày.

### 16.3. OU và account layout

Một layout minh họa:

    Organization
    ├── Security OU
    │   ├── Log Archive account
    │   └── Security Tooling account
    ├── Infrastructure OU
    │   └── Shared Services account
    ├── Workloads OU
    │   ├── Production account
    │   └── Staging account
    └── Sandbox OU
        └── Developer accounts

Đây chỉ là mẫu. Account strategy phải dựa trên:

- Mức độ cô lập cần thiết.
- Đội ngũ và ownership.
- Compliance.
- Shared services.
- Mô hình billing.
- Quy trình tạo và đóng account.

### 16.4. SCP

Service Control Policy đặt guardrail ở cấp Organizations. SCP:

- Có thể giới hạn những action/account/Region được phép dùng.
- Áp dụng cho member account hoặc OU.
- Không cấp permission trực tiếp.
- Không thay thế IAM policy.
- Không áp dụng theo cách “Allow” đơn giản nếu IAM không cấp quyền.

Cách hiểu quan trọng:

> Effective permission phải được phép bởi IAM và không bị chặn bởi SCP hoặc các boundary khác.

Ví dụ governance:

- Chỉ cho phép tạo resource ở các Region đã phê duyệt.
- Chặn tắt CloudTrail hoặc xóa log archive.
- Chặn rời khỏi organization.
- Chặn một số dịch vụ đắt hoặc không được phép.
- Bắt buộc dùng account/OU phù hợp.

Cẩn thận với SCP quá rộng. Một policy sai có thể làm gián đoạn cả organization. Nên test ở sandbox hoặc OU nhỏ trước.

### 16.5. Account factory và guardrail

Trong tổ chức lớn, nên chuẩn hóa:

- Cách tạo account.
- Baseline logging.
- IAM Identity Center.
- Guardrail.
- Budget mặc định.
- Tag policy.
- Security services.
- Quy trình đóng account và xử lý dữ liệu.

Mục tiêu là để một account mới không bắt đầu từ trạng thái “trống” và thiếu kiểm soát.

---

## 17. FinOps loop cho một kiến trúc AWS

Cost optimization hiệu quả là một vòng lặp:

    Plan → Estimate → Deploy → Measure → Attribute → Optimize → Repeat

### Plan

Xác định:

- Workload.
- SLO.
- Traffic.
- Data growth.
- Retention.
- RTO/RPO.
- Compliance.
- Budget owner.

### Estimate

Tính các thành phần chính:

- Compute hours.
- Storage GB-month.
- Request count.
- Database capacity.
- Data transfer.
- NAT/Endpoint.
- Monitoring và backup.
- Discount hoặc commitment.

### Deploy

Gắn:

- Tags.
- Budget.
- Alarms.
- Log retention.
- Auto Scaling bounds.
- Lifecycle.
- Ownership.

### Measure

Theo dõi:

- Cost per service.
- Cost per environment.
- Cost per request hoặc transaction.
- Cache hit ratio.
- Instance utilization.
- Storage growth.
- Data transfer.
- RI/Savings Plans coverage và utilization.

### Optimize

Ưu tiên thay đổi có tác động lớn:

1. Tài nguyên dư thừa hoặc chạy vô ích.
2. Data transfer và NAT.
3. Storage retention/tiering.
4. Dư thừa compute.
5. Pricing commitment sau khi có baseline.
6. Tối ưu request và architecture.

### Repeat

Cost thay đổi khi:

- Traffic thay đổi.
- Code thay đổi.
- Region thay đổi.
- Dataset tăng.
- Retention thay đổi.
- AWS ra pricing/service mới.

---

## 18. Cách lập cost estimate cho capstone

### 18.1. Chia theo thành phần

Tạo bảng như sau:

| Thành phần | Giả định usage | Đơn vị chi phí | Monthly estimate | Ghi chú |
|---|---|---|---:|---|
| EC2/compute | số instance × giờ chạy | instance-hour | … | baseline/burst |
| Load balancer | giờ + processed data | hour/LCU | … | |
| RDS | instance + storage + backup | hour/GB-month | … | Multi-AZ? |
| S3 | dung lượng + request | GB-month/request | … | lifecycle |
| NAT | số gateway + GB xử lý | hour/GB | … | endpoint trade-off |
| CloudFront | request + data transfer | request/GB | … | cache hit ratio |
| Monitoring | log ingestion + retention | GB-month | … | |
| Backup | snapshot/backup storage | GB-month | … | retention |

### 18.2. Công thức khái quát

Không cần nhớ đơn giá cụ thể để thi. Hãy hiểu công thức:

    Monthly compute
    = số resource × số giờ chạy × đơn giá theo giờ

    Monthly storage
    = dung lượng trung bình GB × đơn giá GB-tháng

    Monthly request
    = số request × đơn giá mỗi request hoặc mỗi đơn vị

    Monthly data transfer
    = dữ liệu truyền GB × đơn giá theo hướng truyền

    Monthly NAT
    = số NAT Gateway × số giờ
    + tổng GB đi qua NAT × đơn giá data processing
    + phí transfer liên quan

Sau đó cộng thêm overhead như backup, logs, monitoring và support nếu scope estimate yêu cầu.

### 18.3. Dùng ba kịch bản

Nên lập:

- Low: traffic thấp.
- Baseline: traffic trung bình dự kiến.
- Peak: traffic cao hoặc campaign.

Điều này giúp nhận ra architecture nào có:

- Fixed cost cao.
- Variable cost cao.
- Điểm hòa vốn giữa serverless và always-on.
- Rủi ro hóa đơn tăng đột biến.

---

## 19. Lab 1 — Dự toán chi phí kiến trúc capstone

### Mục tiêu

Tạo bảng cost estimate có thể giải thích được giả định và trade-off.

### Bối cảnh

Giả sử capstone có:

- Web/API tier chạy trong private subnet.
- ALB public.
- EC2 Auto Scaling.
- RDS Multi-AZ.
- S3 lưu file.
- CloudFront phân phối static content.
- NAT Gateway cho outbound traffic.
- CloudWatch logs.
- Backup định kỳ.

### Các bước

1. Vẽ request path.
2. Liệt kê mọi resource chạy theo giờ.
3. Ước tính traffic và data transfer.
4. Ghi số giờ chạy của dev/staging/prod.
5. Tính storage hiện tại và tốc độ tăng.
6. Ghi lifecycle và backup retention.
7. Tạo ba kịch bản low/baseline/peak.
8. Đánh dấu chi phí fixed và variable.
9. Đề xuất ít nhất ba cách giảm cost.
10. Ghi rõ tác động lên availability, performance và operations.

### Kết quả mong đợi

Bạn phải trả lời được:

- Nếu traffic tăng gấp 10, phần chi phí nào tăng trước?
- Nếu bỏ NAT và dùng S3 Gateway Endpoint, phần nào giảm?
- Nếu bật CloudFront, origin cost thay đổi thế nào?
- Nếu tắt non-production ngoài giờ, tiết kiệm loại chi phí nào?
- Vì sao chưa nên mua commitment ngay nếu baseline chưa rõ?

---

## 20. Lab 2 — So sánh NAT Gateway với S3 Gateway Endpoint

### Mục tiêu

Hiểu cả cost và network path.

### Kịch bản

Một ứng dụng trong private subnet tải file từ S3 thường xuyên. So sánh:

A. Private subnet → NAT Gateway → Internet path → S3  
B. Private subnet → S3 Gateway Endpoint → S3

### Phân tích

| Tiêu chí | NAT Gateway | S3 Gateway Endpoint |
|---|---|---|
| Phí theo giờ | Có | Không theo kiểu NAT Gateway |
| Phí data processing | Có | Tránh phần NAT processing |
| Route | Qua NAT | Qua route table |
| Dùng cho Internet | Có | Không |
| Policy | NAT/security routing | Endpoint policy + IAM/bucket policy |
| Use case | Outbound Internet | S3 private access |

### Câu hỏi cần trả lời

- Traffic có thực sự cần Internet không?
- Có bucket nào cần truy cập không?
- Có cần endpoint policy để giới hạn bucket không?
- NAT đang ở cùng AZ với workload chưa?
- Interface Endpoint có phù hợp hơn nếu service không có Gateway Endpoint không?
- Tổng phí thay đổi thế nào khi dữ liệu tăng?

---

## 21. Lab 3 — Tạo S3 lifecycle

### Mục tiêu

Thiết kế retention và tiering dựa trên access pattern.

### Ví dụ policy

    Prefix: uploads/
    Ngày 0–30: Standard
    Sau 30 ngày: Standard-IA
    Sau 90 ngày: Glacier Flexible Retrieval
    Sau 365 ngày: xóa nếu không còn yêu cầu retention

    Noncurrent versions:
    Sau 30 ngày: chuyển tier
    Sau 180 ngày: xóa

    Incomplete multipart uploads:
    Sau 7 ngày: xóa

### Những điều cần kiểm tra

- Dữ liệu có bị yêu cầu giữ lâu hơn bởi compliance không?
- Có thường xuyên restore sau 90 ngày không?
- Có minimum storage duration hoặc retrieval fee không?
- Xóa noncurrent version có ảnh hưởng khả năng rollback không?
- Lifecycle có áp dụng nhầm lên dữ liệu production không?

Không nên deploy lifecycle trực tiếp trên bucket quan trọng nếu chưa kiểm thử prefix, tag và dry-run logic.

---

## 22. Lab 4 — Tạo budget alarm

### Mục tiêu

Tạo cơ chế phát hiện sớm khi cost vượt dự kiến.

### Thiết kế

Tạo ít nhất:

- Monthly budget cho toàn account.
- Budget riêng cho non-production hoặc project.
- Alert khi actual đạt ngưỡng thấp.
- Alert khi forecast vượt ngân sách.
- Người nhận alert là owner có runbook.

### Runbook khi có cảnh báo

1. Kiểm tra account và service tăng.
2. So sánh với deployment hoặc campaign.
3. Kiểm tra NAT, data transfer, logs và resource ngoài dự kiến.
4. Kiểm tra resource không tag hoặc resource chạy ngoài lịch.
5. Xác minh có phải traffic hợp lệ không.
6. Nếu là sự cố, chặn nguồn tạo cost theo cách an toàn.
7. Ghi nguyên nhân và thêm guardrail.

Budget alert không nên kích hoạt hành động phá hủy tự động trên production nếu chưa có cơ chế phê duyệt và kiểm soát.

---

## 23. Lab 5 — Right-size EC2/RDS dựa trên metric

### Mục tiêu

Đưa ra recommendation có bằng chứng, không đoán theo cảm giác.

### Dữ liệu giả lập

| Metric | EC2 hiện tại | RDS hiện tại |
|---|---:|---:|
| CPU average | 18% | 22% |
| CPU p95 | 42% | 58% |
| Memory p95 | 78% | 71% |
| Network peak | cao | thấp |
| EBS I/O | gần giới hạn | trung bình |
| Connections | thấp | gần giới hạn |
| p99 latency | tăng vào peak | tăng khi write burst |

### Cách phân tích

- EC2 CPU thấp nhưng memory và EBS I/O cần xem kỹ trước khi giảm size.
- RDS CPU chưa cao nhưng connections gần giới hạn có thể cần đổi class hoặc pool connection.
- Tăng instance chưa chắc giải quyết latency nếu nguyên nhân là query hoặc lock.
- Có thể dùng cache hoặc read replica nếu bottleneck là read.
- Mọi thay đổi cần đo lại cả performance lẫn cost.

### Deliverable

Viết recommendation theo mẫu:

    Quan sát:
    Giả thuyết:
    Thay đổi đề xuất:
    Chi phí dự kiến:
    Rủi ro:
    Metric theo dõi:
    Điều kiện rollback:

---

## 24. Các bẫy đề thi thường gặp

### Bẫy 1: “Least operational overhead” = “cheapest”

Không đúng. Lambda, managed database hoặc serverless thường giảm vận hành, nhưng chi phí usage có thể cao hơn một hệ thống always-on ở quy mô lớn.

### Bẫy 2: Chọn Spot cho database hoặc workload không thể gián đoạn

Spot phù hợp với workload có thể retry, checkpoint hoặc thay thế. Giá rẻ không bù được việc mất tiến trình hoặc downtime.

### Bẫy 3: Quên NAT Gateway tính theo cả giờ và dữ liệu

NAT không chỉ có phí tồn tại. Dữ liệu đi qua NAT tạo thêm phí processing; cross-AZ còn có thể thêm phí transfer.

### Bẫy 4: Dùng NAT cho traffic S3 dù có Gateway Endpoint

Nếu private workload chỉ cần S3, Gateway Endpoint thường giảm đường đi và tránh NAT data processing.

### Bẫy 5: Chọn archive tier vì giá storage thấp

Nếu dữ liệu được truy cập thường xuyên, retrieval fee, latency và minimum duration có thể làm tổng chi phí cao hơn.

### Bẫy 6: Tắt Multi-AZ để giảm cost

Multi-AZ phục vụ availability và failover. Không được bỏ redundancy cần thiết chỉ vì muốn giảm hóa đơn.

### Bẫy 7: Mua Reserved/Savings Plans khi chưa có baseline

Commitment có lợi khi usage ổn định. Với workload chưa đo được, On-Demand giữ linh hoạt hơn.

### Bẫy 8: Tạo nhiều Interface Endpoint và nghĩ là luôn rẻ

Interface Endpoint có phí theo giờ mỗi AZ và phí dữ liệu. Cần so sánh với NAT theo lưu lượng và yêu cầu private access.

### Bẫy 9: Bỏ tag nhưng vẫn mong chia cost chính xác

Không có owner, environment và cost center thì rất khó biết ai chịu trách nhiệm và cost tăng từ đâu.

### Bẫy 10: SCP là permission grant

SCP chỉ đặt giới hạn tối đa cho account/OU. IAM policy vẫn phải cấp permission.

### Bẫy 11: Budget alarm tự động ngăn mọi chi phí

Budgets cảnh báo hoặc kích hoạt hành động được cấu hình; nó không tự sửa architecture hay ngăn mọi service chạy.

### Bẫy 12: Cross-AZ luôn xấu

Cross-AZ có thể tạo cost nhưng Multi-AZ vẫn cần cho resilience. Hãy giảm traffic không cần thiết, không loại bỏ redundancy bắt buộc.

---

## 25. Bộ câu hỏi tự kiểm tra

### Câu 1

Một công ty có workload batch có thể chạy lại nếu bị gián đoạn. Muốn giảm chi phí compute tối đa. Nên cân nhắc gì?

A. Chỉ dùng On-Demand  
B. Spot với checkpoint và capacity đa dạng  
C. Dedicated Host  
D. Provisioned Concurrency

Đáp án: B.

Lý do: workload có thể bị gián đoạn và chạy lại được nên phù hợp với Spot. Cần thêm checkpoint, retry và capacity strategy.

### Câu 2

Ứng dụng trong private subnet thường xuyên đọc S3. Cách giảm NAT cost phù hợp?

A. Tăng kích thước NAT Gateway  
B. Dùng S3 Gateway Endpoint  
C. Đưa toàn bộ instance ra public subnet  
D. Dùng Internet Gateway trực tiếp cho private subnet

Đáp án: B.

Lý do: S3 Gateway Endpoint cung cấp route riêng đến S3 và tránh NAT data processing.

### Câu 3

Một workload production chạy ổn định 24/7, nhưng loại instance có thể thay đổi trong tương lai. Lựa chọn nào cân bằng commitment và flexibility?

A. Spot duy nhất  
B. Compute Savings Plans  
C. Tắt Auto Scaling  
D. S3 Intelligent-Tiering

Đáp án: B.

Lý do: Compute Savings Plans phù hợp với baseline compute ổn định và linh hoạt hơn commitment gắn chặt vào một cấu hình.

### Câu 4

Một S3 bucket chứa dữ liệu audit phải giữ 7 năm nhưng hiếm khi truy cập. Cần làm gì?

A. Xóa sau 30 ngày  
B. Dùng lifecycle sang archive tier với retention phù hợp  
C. Giữ toàn bộ ở Standard  
D. Tắt versioning và không tạo backup

Đáp án: B.

Lý do: dữ liệu cần retention dài và truy cập hiếm phù hợp lifecycle/archive, nhưng phải xét retrieval và compliance.

### Câu 5

Một tổ chức muốn ngăn member account dùng Region không được phê duyệt. Công cụ phù hợp?

A. CloudFront cache policy  
B. SCP trong Organizations  
C. S3 lifecycle  
D. Compute Optimizer

Đáp án: B.

Lý do: SCP đặt guardrail ở cấp account hoặc OU.

### Câu 6

Một Lambda function trong private subnet gọi API Internet và tạo chi phí lớn. Cần kiểm tra gì trước?

A. Chỉ tăng memory Lambda  
B. NAT Gateway, volume outbound và khả năng bỏ private subnet nếu không cần  
C. Xóa IAM role  
D. Tăng S3 lifecycle

Đáp án: B.

Lý do: Lambda private subnet cần đường outbound; NAT có thể tạo phí theo giờ và dữ liệu. Cần đánh giá lại network path.

### Câu 7

Một account có chi phí tăng đột biến mà team chưa biết nguyên nhân. Công cụ nào phù hợp để phát hiện bất thường?

A. Cost Anomaly Detection  
B. Route 53 health check  
C. EBS snapshot  
D. ACM

Đáp án: A.

Lý do: công cụ này theo dõi pattern cost và cảnh báo khi có lệch bất thường.

---

## 26. Checklist hoàn thành tuần 14

### Pricing và compute

- [ ] Phân biệt On-Demand, RI, Savings Plans và Spot.
- [ ] Biết khi nào commitment là hợp lý.
- [ ] Biết workload nào chịu được Spot interruption.
- [ ] Biết đọc metric để right-size.
- [ ] Biết tắt non-production và đặt Auto Scaling bounds.

### Storage và network

- [ ] Biết chọn S3 tier theo access pattern.
- [ ] Biết thiết kế lifecycle cho current/noncurrent version.
- [ ] Biết kiểm tra EBS, EFS, snapshot và log retention.
- [ ] Biết NAT Gateway tính phí theo giờ và data processing.
- [ ] Biết khi nào dùng Gateway Endpoint và Interface Endpoint.
- [ ] Nhận diện cross-AZ, cross-Region và Internet egress.

### Visibility và governance

- [ ] Có bộ cost allocation tags.
- [ ] Biết dùng Cost Explorer để drill down.
- [ ] Biết tạo Budget cho actual và forecast.
- [ ] Biết vai trò của Cost Anomaly Detection.
- [ ] Biết Trusted Advisor và Compute Optimizer hỗ trợ gì.
- [ ] Hiểu Organizations, consolidated billing, OU và SCP.
- [ ] Có account strategy cho production, security, shared services và sandbox.

### Capstone

- [ ] Có bảng estimate cho low/baseline/peak.
- [ ] Ghi rõ giả định usage.
- [ ] Tách fixed cost và variable cost.
- [ ] Tính hoặc ít nhất nhận diện data transfer cost.
- [ ] Có kế hoạch lifecycle, budget và owner.
- [ ] Ghi trade-off giữa cost, reliability, performance và operations.

---

## 27. Tóm tắt cần nhớ

1. Cost optimization là tối ưu tổng thể, không chỉ chọn giá thấp nhất.
2. On-Demand giữ flexibility; RI/Savings Plans giảm giá khi có baseline ổn định.
3. Spot phù hợp với workload có thể bị gián đoạn và chạy lại.
4. Right-sizing phải dựa trên metric, peak và bottleneck thực tế.
5. Non-production thường là nơi dễ tiết kiệm bằng scheduling.
6. S3 lifecycle và tiering phải dựa trên access pattern, retention và retrieval.
7. NAT Gateway có phí theo giờ và theo dữ liệu xử lý.
8. S3/DynamoDB Gateway Endpoint thường là lựa chọn quan trọng để tránh NAT cho traffic tương ứng.
9. CloudFront và cache có thể giảm origin load và data transfer nhưng phải bảo vệ correctness và privacy.
10. Serverless giảm operational overhead, không đảm bảo luôn rẻ nhất.
11. Tags, Cost Explorer, Budgets và anomaly detection biến cost thành dữ liệu có thể hành động.
12. Organizations và SCP tạo boundary; SCP không tự cấp permission.
13. Giữ redundancy cần thiết; đừng hy sinh reliability chỉ để giảm bill.
14. Mọi architecture decision nên ghi rõ giả định, cost driver, trade-off và cách đo lại.

---

## 28. Tài liệu tham khảo chính thức

- AWS Cloud Financial Management: https://aws.amazon.com/aws-cost-management/
- Amazon EC2 pricing: https://aws.amazon.com/ec2/pricing/
- AWS Savings Plans: https://aws.amazon.com/savingsplans/
- Amazon S3 storage classes: https://aws.amazon.com/s3/storage-classes/
- S3 Lifecycle management: https://docs.aws.amazon.com/AmazonS3/latest/userguide/object-lifecycle-mgmt.html
- VPC endpoints: https://docs.aws.amazon.com/vpc/latest/privatelink/vpc-endpoints.html
- AWS NAT Gateway pricing: https://aws.amazon.com/vpc/pricing/
- Amazon CloudFront pricing: https://aws.amazon.com/cloudfront/pricing/
- AWS Cost Explorer: https://docs.aws.amazon.com/cost-management/latest/userguide/ce-what-is.html
- AWS Budgets: https://docs.aws.amazon.com/cost-management/latest/userguide/budgets-managing-costs.html
- Cost Anomaly Detection: https://docs.aws.amazon.com/cost-management/latest/userguide/getting-started-ad.html
- AWS Organizations: https://docs.aws.amazon.com/organizations/latest/userguide/orgs_introduction.html
- Service Control Policies: https://docs.aws.amazon.com/organizations/latest/userguide/orgs_manage_policies_scps.html
- AWS Compute Optimizer: https://docs.aws.amazon.com/compute-optimizer/latest/ug/what-is-compute-optimizer.html
- AWS Trusted Advisor: https://docs.aws.amazon.com/awssupport/latest/user/trusted-advisor.html


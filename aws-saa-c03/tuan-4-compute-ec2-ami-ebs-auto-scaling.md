# Tuần 4 — Compute: EC2, AMI, EBS và Auto Scaling

> Mục tiêu: chọn EC2 theo workload, đóng gói bằng AMI/Launch Template, chọn EBS theo IOPS/throughput và thiết kế Auto Scaling Group Multi-AZ có health check.

## 1. Kết quả cần đạt

- Chọn instance family theo CPU, memory, storage, network và workload pattern.
- So sánh On-Demand, Reserved Instances, Savings Plans và Spot.
- Phân biệt AMI, user data, instance metadata và instance profile.
- Chọn EBS gp3, io2, throughput và IOPS theo requirement.
- Phân biệt EBS, instance store, snapshot và AMI.
- Hiểu encryption, restore và giới hạn AZ của EBS.
- Phân biệt cluster, spread và partition placement group.
- Thiết kế Launch Template và Auto Scaling Group Multi-AZ.
- Chọn target tracking, step scaling hoặc scheduled scaling.
- Hiểu health check, warmup, lifecycle hook và instance replacement.
- Phân biệt reboot, stop, hibernate và terminate.
- Biết khi nào dùng Elastic IP và khi nào dùng DNS/load balancer.

## 2. Lịch học gợi ý

| Buổi | Nội dung | Thời lượng |
|---|---|---:|
| 1 | EC2 instance, pricing và lifecycle | 3 giờ |
| 2 | AMI, user data, metadata và instance profile | 2 giờ |
| 3 | EBS, snapshot, encryption và instance store | 3 giờ |
| 4 | Placement group và Auto Scaling | 3 giờ |
| 5 | Lab ALB/ASG, failure test và câu hỏi thi | 2–3 giờ |

Luôn bắt đầu bằng requirement: CPU, memory, I/O, network, latency, interruption tolerance, availability và cost.

---

## 3. EC2 fundamentals

EC2 cung cấp compute capacity có thể thay đổi theo nhu cầu. Một instance gồm instance type, AMI, subnet/AZ, storage, network interface, security group, IAM role và lifecycle state.

| Thành phần | Vai trò |
|---|---|
| Instance type | vCPU, memory, network, EBS bandwidth |
| AMI | template cho OS, software và root volume |
| Subnet/AZ | vị trí network và failure domain |
| Security group | firewall stateful cho ENI |
| EBS/instance store | block storage |
| Instance profile | cung cấp role credentials |
| User data | script khởi tạo |
| Metadata | thông tin runtime của instance |

EC2 là IaaS: AWS quản lý datacenter, host và hypervisor; khách hàng quản lý guest OS, patch, package, application, data, IAM và network configuration.

## 4. EC2 instance types

Tên instance thường gồm family, generation và size, ví dụ m7i.large. Chọn theo bottleneck và benchmark, không chỉ theo số vCPU.

| Family | Tối ưu cho | Ví dụ |
|---|---|---|
| General purpose: t, m | cân bằng CPU/memory/network | web, app, dev/test |
| Compute optimized: c | CPU cao | batch, encoding, API |
| Memory optimized: r, x, z | memory lớn | cache, analytics, database |
| Storage optimized: i, d, h | local NVMe/high I/O | search, data processing |
| Accelerated: p, g, inf, trn | GPU/ML accelerator | ML, graphics |

T family phù hợp workload có baseline CPU thấp nhưng thỉnh thoảng burst. Không dùng burstable mặc định cho CPU cao liên tục nếu cần hiệu năng ổn định; hãy kiểm tra CPU credit.

Tiêu chí chọn gồm CPU sustained/burst, working set memory, network bandwidth, EBS bandwidth, local storage, x86/Arm compatibility, availability của AZ và cost.

## 5. Pricing và purchasing options

### On-Demand

Không cần cam kết dài hạn, phù hợp workload ngắn hạn, không dự đoán được, thử nghiệm hoặc workload mới.

### Reserved Instances

Discount đổi lấy commitment về configuration/Region/term. Phù hợp workload steady-state và predictable. Reservation giảm giá, không tự triển khai instance. Standard ít linh hoạt hơn Convertible; payment option ảnh hưởng cash flow và discount.

### Savings Plans

Cam kết chi tiêu compute theo giờ trong một term để nhận discount. Compute Savings Plans linh hoạt hơn; EC2 Instance Savings Plans cụ thể hơn nhưng thường discount tốt hơn. Ở mức SAA, nhớ dùng cho compute usage ổn định.

### Spot Instances

Dùng capacity dư thừa với giá thấp hơn nhưng có thể bị interrupt. Phù hợp batch, distributed processing, stateless worker, CI runner có retry và workload checkpoint được.

Thiết kế Spot cần checkpoint, idempotency, queue retry, mixed On-Demand fallback, graceful drain và interruption monitoring. Không dùng cho workload không chịu interruption hoặc chỉ có state local.

Dedicated Hosts/Instances dành cho licensing, compliance hoặc isolation đặc biệt. Không chọn chúng chỉ vì cần HA; Multi-AZ và ASG mới giải quyết redundancy.

---

## 6. AMI, user data và metadata

### AMI

AMI là template để launch EC2, gồm root volume, block device mapping, OS/software, architecture và permission. AMI thường thuộc một Region; muốn dùng Region khác cần copy AMI.

Golden AMI đã patch, harden, cài agent và application baseline. Quy trình nên là build tự động, scan/test, version, rollout qua Launch Template/ASG và rollback khi lỗi.

Không lưu secret lâu dài trong AMI; dùng IAM role, Secrets Manager hoặc Parameter Store.

### User data

User data là script/config truyền khi launch instance, thường chạy lần đầu boot. Dùng để cài package, render config, đăng ký service discovery hoặc cài agent.

User data không chứa credential tĩnh. Script nên idempotent. Production nên ưu tiên immutable AMI và user data nhỏ hơn là cài toàn bộ application khi boot.

### Instance metadata và IMDSv2

Metadata cung cấp thông tin instance, network, user data và role credentials. Dùng IMDSv2 khi có thể vì yêu cầu session token và giảm một số rủi ro SSRF so với IMDSv1.

Không expose metadata cho ứng dụng không tin cậy. IAM role vẫn phải least privilege.

### Instance profile

Instance profile là container gắn IAM role vào EC2:

~~~text
EC2 → instance profile → IAM role → temporary credentials → AWS API
~~~

Không nhúng access key vào source, user data, AMI hoặc file cấu hình.

---

## 7. EBS

EBS là block storage persistent cho EC2. EBS volume thường thuộc một AZ, không attach trực tiếp sang instance ở AZ khác.

| Type | Đặc tính | Use case |
|---|---|---|
| gp3 | SSD general purpose, provision IOPS/throughput độc lập hơn | boot, web/app, DB phổ thông |
| gp2 | SSD, baseline IOPS theo kích thước | legacy/general workload |
| io2 | provisioned IOPS, hiệu năng cao | mission-critical DB |
| st1 | HDD throughput optimized | sequential log/big data |
| sc1 | HDD cold | dữ liệu ít truy cập |

- IOPS là số operation mỗi giây, quan trọng với random small I/O.
- Throughput là MB/s, quan trọng với sequential large I/O.
- Latency là thời gian mỗi operation.

Kiểm tra cả giới hạn volume và EBS bandwidth của instance. Database random I/O có thể cần IOPS cao; xử lý file lớn tuần tự có thể cần throughput cao.

Root volume chứa OS. Data volume nên tách riêng để giữ dữ liệu khi thay AMI/instance, backup độc lập và giảm rủi ro terminate nhầm. Kiểm tra DeleteOnTermination cho từng volume.

### EBS lifecycle

- EBS tồn tại độc lập với instance tùy cấu hình.
- EBS volume không tự là Multi-AZ.
- Muốn dùng ở AZ khác: snapshot rồi tạo volume mới ở AZ đích.
- EBS Multi-Attach có giới hạn type/OS/filesystem và cần application cluster-aware.

---

## 8. Snapshot và encryption

EBS snapshot là backup point-in-time. Snapshot sau lần đầu thường incremental theo block thay đổi.

Use case: backup/restore, tạo volume mới, copy Region khác, tạo AMI và disaster recovery. Snapshot không tự bảo đảm database-consistency; database cần flush/quiesce hoặc backup native/managed phù hợp.

EBS encryption bảo vệ data at rest, volume, snapshot và dữ liệu chuyển giữa instance với EBS trong phạm vi hỗ trợ. Dùng AWS managed key hoặc customer managed KMS key.

Khi copy encrypted snapshot sang Region khác, cần key phù hợp ở Region đích. User/role cần quyền KMS, không chỉ EC2/EBS permission.

Backup strategy phải xác định RPO/RTO, retention, cross-Region, encryption key, restore test, automation và cost.

---

## 9. Instance store và EBS

| Đặc điểm | EBS | Instance store |
|---|---|---|
| Persistence | persistent tùy cấu hình | ephemeral, gắn với host |
| AZ | volume thuộc một AZ | thuộc instance host |
| Snapshot | có | không như EBS |
| Resize | modify/replace được | phụ thuộc instance type |
| Use case | OS, DB, persistent data | cache, scratch, temporary data |

Instance store có thể nhanh nhưng mất dữ liệu khi stop/terminate hoặc host failure. Không dùng cho dữ liệu duy nhất cần giữ. Nếu dùng, hãy replication ở application layer, rebuild tự động và checkpoint ra EBS/S3/database.

---

## 10. Placement groups

### Cluster

Đặt instance gần nhau trong một AZ để latency thấp và network throughput cao. Phù hợp HPC/tightly coupled workload nhưng tập trung failure domain.

### Spread

Đặt instance trên phần cứng khác nhau để giảm correlated failure. Phù hợp số lượng nhỏ instance quan trọng và có giới hạn số instance theo AZ.

### Partition

Chia instance thành partition, mỗi partition có failure domain riêng; phù hợp Hadoop, Kafka hoặc Cassandra. Ứng dụng có thể biết partition topology để phân tán replica.

| Type | Ưu tiên | Đánh đổi |
|---|---|---|
| Cluster | latency/throughput | tập trung trong một AZ |
| Spread | isolation phần cứng | giới hạn số instance |
| Partition | distributed workload | cần app hiểu partition |

Placement group không thay thế Multi-AZ.

---

## 11. EC2 lifecycle

### Reboot

Khởi động lại OS trên cùng instance, thường giữ private/public identity và EBS.

### Stop/start

Stop giải phóng compute capacity; EBS vẫn tồn tại. Khi start, instance có thể chuyển host và public IPv4 auto-assigned có thể thay đổi. Elastic IP giữ địa chỉ nếu vẫn associate. Instance store data có thể mất.

### Hibernate

Lưu RAM state vào root EBS rồi stop để start lại nhanh hơn. Cần instance type, OS và root volume hỗ trợ.

### Terminate

Giải phóng instance. EBS root/data bị xóa hay giữ tùy DeleteOnTermination; instance store mất dữ liệu.

Trước terminate production, kiểm tra volume/snapshot, target registration, EIP/DNS, ASG replacement và retention.

---

## 12. Elastic IP và naming

Elastic IP là public IPv4 tĩnh có thể associate lại. Dùng khi workload thật sự cần IP cố định, như allowlist legacy hoặc appliance.

Không dùng EIP làm service discovery/failover chính cho web app. Ưu tiên Route 53, ALB/NLB, service discovery hoặc Global Accelerator. EIP có thể phát sinh cost khi allocate nhưng không dùng.

---

## 13. Launch Template

Launch Template là blueprint để launch EC2, gồm AMI, instance type, key pair, SG, IAM instance profile, block device mapping, user data, network settings, purchasing options, tags và metadata options.

Best practice:

- version template;
- immutable AMI và user data nhỏ;
- không hard-code secret;
- gắn tag cho instance/volume;
- test template trước khi dùng ASG;
- pin AMI version và có rollback.

---

## 14. Auto Scaling Group

ASG duy trì số lượng EC2 theo min, desired và max capacity.

~~~text
min capacity ≤ desired capacity ≤ max capacity
~~~

ASG có thể launch khi thiếu capacity, replace instance unhealthy, scale theo policy, trải instance qua AZ, đăng ký target vào load balancer và dùng lifecycle hook.

ASG không tự phân phối traffic; cần ALB/NLB hoặc cơ chế khác.

### Multi-AZ ASG

~~~text
Public ALB ở AZ-a/AZ-b
       ↓ target group
ASG: private subnet-a + private subnet-b
       ↓
EC2 cùng Launch Template
~~~

Kiểm tra subnet còn IP, AZ có capacity, desired capacity đủ chịu mất một AZ, health check, warmup, target registration và state externalized.

### Health check

ASG có EC2 health check mặc định. Bật ELB health check để thay instance khi instance còn running nhưng app unhealthy.

Health check nên phản ánh khả năng phục vụ nhưng không quá sâu đến mức dependency tạm thời làm terminate hàng loạt. Dùng grace period cho boot/configure.

### Scaling policy

| Policy | Khi dùng |
|---|---|
| Target tracking | giữ metric gần target, ví dụ CPU 50% |
| Step scaling | amount khác nhau theo mức alarm |
| Simple scaling | mô hình cũ, chờ cooldown |
| Scheduled scaling | traffic predictable theo giờ/ngày |
| Predictive scaling | pattern lịch sử dự đoán được |

Target tracking phù hợp metric ổn định. Scheduled scaling phù hợp traffic predictable; worker có thể scale theo queue backlog.

### Warmup, cooldown và lifecycle hook

Instance mới cần thời gian boot, cài app và warm cache. Dùng warmup, grace period và cooldown để tránh scale storm hoặc oscillation.

Lifecycle hook tạm dừng instance ở Pending:Wait hoặc Terminating:Wait để cài config, drain connection, upload log, checkpoint hoặc gửi event. Hook cần timeout/result rõ ràng.

Instance refresh thay dần instance khi đổi AMI/Launch Template. Dùng minimum healthy percentage, warmup, checkpoint và rollback.

---

## 15. Thiết kế workload với EC2

### Web/application tier

ALB phân phối traffic, ASG trải Multi-AZ, app stateless, session/data ở cache/database/S3, IAM role cho AWS API và log/metric tập trung.

### Worker/batch tier

SQS làm buffer, ASG scale theo queue depth, Spot nếu job checkpoint/retry được, xử lý idempotent và output ra S3/database.

### Stateful service

Chọn EBS theo IOPS/throughput, backup có restore test, replication ở application/database layer và xem xét RDS/managed service nếu cần giảm operational overhead.

---

## 16. Lab bắt buộc

### Lab 1 — AMI và Launch Template

1. Launch EC2 test từ AMI chính thức.
2. Cài web server/agent tối thiểu.
3. Tạo AMI versioned, không lưu secret.
4. Tạo Launch Template từ AMI.
5. Cấu hình instance profile, SG, EBS mapping, tags và IMDSv2.
6. Launch instance từ template để xác nhận boot.

### Lab 2 — ASG Multi-AZ với ALB

1. Chọn hai private app subnets ở hai AZ.
2. Tạo target group và health check.
3. Tạo ALB ở public subnets.
4. Tạo ASG với min/desired/max rõ ràng.
5. Gắn ASG vào target group.
6. Kiểm tra target register và request tới ALB.
7. Dừng/terminate một instance.
8. Quan sát ASG launch replacement và target health.

### Lab 3 — Target tracking

Tạo target tracking theo CPU hoặc ALB request count per target, tạo load vừa phải, quan sát CloudWatch metric, desired capacity và warmup, rồi xác nhận scale-in không xảy ra quá sớm.

### Lab 4 — EBS snapshot và restore

1. Tạo data volume EBS.
2. Ghi file test và tạo snapshot.
3. Thay/xóa volume mô phỏng failure.
4. Restore volume trong cùng AZ.
5. Attach/mount vào instance.
6. Xác nhận dữ liệu, encryption và permission.
7. Xóa resource test.

### Lab 5 — Failure test

| Tình huống | Cơ chế kỳ vọng |
|---|---|
| terminate instance | ASG thay instance |
| app health check fail | ALB loại target, ASG có thể thay |
| một AZ mất | ALB/ASG còn capacity ở AZ khác nếu đủ |
| volume hỏng | restore snapshot/backup |
| AMI mới lỗi | instance refresh rollback |

---

## 17. Bẫy đề thi SAA-C03

- ASG scale instance nhưng không tự phân phối traffic; cần ALB/NLB.
- ASG Multi-AZ không làm state local tự đồng bộ.
- EBS volume thường thuộc một AZ; không attach trực tiếp sang AZ khác.
- Instance store mất dữ liệu khi stop/terminate/host failure.
- Spot có thể bị interrupt; cần retry, checkpoint hoặc fallback.
- EBS snapshot không luôn là database-consistent backup.
- EBS encrypted cần kiểm tra KMS permission và Region.
- gp3 cho phép điều chỉnh IOPS/throughput độc lập hơn gp2.
- IOPS khác throughput; random và sequential I/O có nhu cầu khác nhau.
- Stop/start có thể làm mất public IPv4 auto-assigned.
- Reboot, stop, hibernate và terminate có hậu quả khác nhau.
- EC2 health check có thể không phát hiện app unhealthy; dùng ELB health check khi cần.
- Target tracking cần metric phù hợp và warmup.
- Cluster tối ưu latency nhưng tập trung failure domain.
- Spread dành số lượng nhỏ instance quan trọng.
- Partition phù hợp distributed system.
- EIP không phải service discovery; ưu tiên DNS/load balancer.
- User data không phải nơi lưu secret.
- Instance profile tốt hơn access key trên EC2.

---

## 18. Câu hỏi tự luyện

### Câu 1

Workload web cần CPU cao liên tục và ít memory. Chọn family nào?

**Đáp án:** compute optimized c, sau benchmark và kiểm tra network/EBS requirement.

### Câu 2

Workload CPU thấp hầu hết thời gian nhưng thỉnh thoảng burst. Chọn gì?

**Đáp án:** burstable T family nếu CPU credit và hiệu năng phù hợp.

### Câu 3

Job batch có thể retry và checkpoint, muốn giảm chi phí compute. Chọn gì?

**Đáp án:** Spot Instance, kết hợp queue, checkpoint, retry và fallback.

### Câu 4

EC2 cần đọc S3 nhưng code không được có access key. Dùng gì?

**Đáp án:** IAM role gắn qua instance profile, dùng temporary credentials.

### Câu 5

Database cần random I/O cao và IOPS ổn định. Chọn EBS nào?

**Đáp án:** io2/provisioned IOPS nếu requirement và cost phù hợp; gp3 có thể đủ cho workload nhỏ hơn.

### Câu 6

EBS volume ở AZ-a cần dùng cho instance ở AZ-b. Làm thế nào?

**Đáp án:** snapshot rồi restore volume mới ở AZ-b, hoặc dùng replication/managed database.

### Câu 7

ASG giữ instance running nhưng app trả HTTP 500. Cải thiện gì?

**Đáp án:** dùng ELB health check phù hợp để ASG biết target unhealthy; xử lý root cause và tránh health check quá sâu.

### Câu 8

Cần latency thấp giữa instance trong cùng AZ cho HPC. Placement group nào?

**Đáp án:** cluster placement group, chấp nhận tập trung trong một AZ.

### Câu 9

Cần giảm correlated hardware failure cho ít instance quan trọng. Placement group nào?

**Đáp án:** spread placement group.

### Câu 10

ASG thay instance liên tục dù CPU thấp. Nguyên nhân có thể là gì?

**Đáp án:** health check/path sai, SG/NACL, app boot chưa xong, grace period/warmup quá ngắn, user data/AMI lỗi hoặc thiếu capacity.

### Câu 11

Instance stop rồi start và public IP thay đổi. Vì sao?

**Đáp án:** public IPv4 tự động không phải địa chỉ tĩnh; dùng Elastic IP hoặc DNS/load balancer nếu cần endpoint ổn định.

---

## 19. Checklist hoàn thành tuần

### Kiến thức

- [ ] Chọn được general purpose, compute, memory, storage và burstable family.
- [ ] So sánh On-Demand, RI, Savings Plans và Spot.
- [ ] Giải thích AMI, golden AMI, user data, metadata và instance profile.
- [ ] Biết dùng IMDSv2 và không lưu secret trong AMI/user data.
- [ ] Phân biệt gp3, gp2, io2, st1 và sc1.
- [ ] Phân biệt IOPS, throughput và latency.
- [ ] Giải thích snapshot, encryption và EBS AZ limitation.
- [ ] Phân biệt EBS và instance store.
- [ ] Chọn cluster, spread hoặc partition placement group.
- [ ] Thiết kế Launch Template và ASG Multi-AZ.
- [ ] Chọn scaling policy và health check phù hợp.
- [ ] Phân biệt reboot, stop, hibernate và terminate.
- [ ] Biết khi nào dùng EIP hay DNS/load balancer.

### Thực hành

- [ ] Đã tạo AMI versioned và Launch Template.
- [ ] Đã dựng ASG qua hai AZ.
- [ ] Đã gắn ALB/target group và health check.
- [ ] Đã test target tracking.
- [ ] Đã terminate instance và quan sát ASG thay thế.
- [ ] Đã tạo snapshot và restore EBS volume.
- [ ] Đã kiểm tra DeleteOnTermination.
- [ ] Đã dọn EC2, ALB, ASG, EBS, snapshot và Elastic IP sau lab.

Bạn sẵn sàng sang Tuần 5 khi có thể chọn instance/storage theo requirement, thiết kế ASG Multi-AZ có health check và giải thích điều gì xảy ra khi instance bị terminate.

---

## 20. Tài liệu tham khảo

- [Amazon EC2 User Guide](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/concepts.html) — khái niệm EC2.
- [Amazon EC2 instance types](https://aws.amazon.com/ec2/instance-types/) — family và đặc tính instance.
- [Amazon Machine Images](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/AMIs.html) — AMI và image lifecycle.
- [Instance metadata options](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/configuring-instance-metadata-options.html) — IMDS và IMDSv2.
- [Amazon EBS volume types](https://docs.aws.amazon.com/ebs/latest/userguide/ebs-volume-types.html) — volume, IOPS và throughput.
- [Amazon EBS snapshots](https://docs.aws.amazon.com/ebs/latest/userguide/ebs-snapshots.html) — snapshot và restore.
- [Amazon EC2 Auto Scaling](https://docs.aws.amazon.com/autoscaling/ec2/userguide/what-is-amazon-ec2-auto-scaling.html) — ASG và scaling.
- [Auto Scaling health checks](https://docs.aws.amazon.com/autoscaling/ec2/userguide/ec2-auto-scaling-health-checks.html) — EC2/ELB health check.
- [Placement groups](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/placement-groups.html) — cluster, spread và partition.


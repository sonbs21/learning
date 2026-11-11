# Auto Scaling chuyên sâu — AWS SAA-C03

> Tài liệu tách riêng phần Auto Scaling từ Tuần 4. Mục tiêu là hiểu Amazon EC2 Auto Scaling Group như cơ chế duy trì capacity, tự phục hồi và điều chỉnh số lượng EC2 theo nhu cầu; đồng thời biết kết hợp ASG với Launch Template, ALB/NLB, CloudWatch, health check, lifecycle hook, instance refresh, Spot và Multi-AZ.

## 1. Auto Scaling là gì?

Auto Scaling không chỉ có nghĩa là tăng server khi CPU cao. Auto Scaling Group, hay ASG, có thể:

- Duy trì số lượng instance mong muốn.
- Tạo instance khi thiếu capacity.
- Thay thế instance unhealthy.
- Scale out khi workload tăng.
- Scale in khi workload giảm.
- Phân bố instance qua nhiều Availability Zone.
- Đăng ký/deregister target khỏi Load Balancer.
- Kết hợp On-Demand, Spot hoặc mixed instances.
- Hỗ trợ lifecycle hook và instance refresh.
- Phản ứng theo metric CloudWatch.

~~~text
Launch Template
      ↓
Auto Scaling Group
      ↓
EC2 instances
      ↓
Target Group
      ↓
ALB / NLB
~~~

ASG quản lý capacity; ALB/NLB mới phân phối request hoặc connection. ASG cũng không tự đồng bộ state local, không replicate database và không bảo vệ dữ liệu nằm duy nhất trên instance.

## 2. Vì sao cần ASG?

Nếu chạy một EC2 cố định:

- Instance có thể quá tải khi traffic tăng.
- Instance có thể nhàn rỗi khi traffic giảm.
- Instance lỗi cần thao tác thủ công.
- Dễ tạo single point of failure.
- Khó triển khai phiên bản mới.
- Khó đáp ứng traffic burst.

Với ASG:

~~~text
Traffic thấp
  → ít instance hơn

Traffic tăng
  → scale out

Instance lỗi
  → terminate/replacement

Traffic giảm
  → scale in
~~~

Mục tiêu là giữ capacity phù hợp với availability, performance, cost, startup time và downstream capacity.

## 3. ASG không thay thế thành phần khác

| Thành phần | Vai trò |
|---|---|
| Launch Template | Mô tả cách tạo instance |
| Auto Scaling Group | Duy trì và điều chỉnh số instance |
| ALB/NLB | Phân phối traffic |
| Target Group | Nhóm target và health check |
| CloudWatch | Metric, alarm và signal |
| IAM role | Quyền của instance |
| AMI | OS/software baseline |
| Route 53 | DNS tới load balancer |
| SQS | Buffer cho worker workload |

Architecture web thường là:

~~~text
Route 53
  ↓
ALB
  ↓
Target Group
  ↓
ASG
  ↓
EC2 ở nhiều AZ
~~~

---

# Phần I — Launch Template

## 4. Launch Template là gì?

Launch Template là blueprint để ASG biết phải tạo instance như thế nào.

Có thể chứa:

- AMI.
- Instance type.
- Key pair.
- Security Group.
- IAM instance profile.
- User data.
- EBS block device mapping.
- Network interface.
- Metadata options.
- Tags.
- Purchasing options.
- Placement settings.
- Monitoring options.

~~~text
Launch Template
├── AMI
├── Instance type
├── Security Group
├── IAM instance profile
├── User data
├── EBS volumes
├── IMDSv2 settings
├── Tags
└── Metadata
~~~

Mọi instance trong ASG nên được tạo từ template version có thể truy vết và rollback.

## 5. Launch Template version

Launch Template hỗ trợ nhiều version:

~~~text
Version 1 → AMI v1, app v1
Version 2 → AMI v2, app v2
Version 3 → AMI v3, app v3
~~~

Best practice:

- Pin version rõ ràng.
- Ghi changelog.
- Test version trước production.
- Có rollback version cũ.
- Không sửa thủ công từng instance để đồng bộ tạm thời.
- Không dùng AMI đã xóa hoặc sai Region.

Khi đổi Launch Template version, instance đang chạy thường không tự đổi. Cần dùng instance refresh, rolling replacement hoặc blue/green deployment.

## 6. AMI, user data và instance profile

AMI nên là golden image đã:

- Cài OS.
- Patch.
- Cài runtime.
- Cài monitoring/security agent.
- Cài application baseline.
- Harden theo standard.

User data nên nhỏ và idempotent:

~~~text
AMI:
  OS + runtime + agent + application baseline

User data:
  lấy config
  lấy secret qua IAM role
  render config
  start service
  signal ready
~~~

Không lưu secret trong AMI, user data, source code hoặc plain-text config. Dùng IAM role, Secrets Manager, Parameter Store và KMS.

Instance profile cung cấp IAM role cho EC2:

~~~text
EC2
  ↓
Instance Profile
  ↓
IAM Role
  ↓
Temporary Credentials
  ↓
AWS API
~~~

Nên yêu cầu IMDSv2 khi có thể.

## 7. Startup và user data

Instance mới có thể cần thời gian để:

- Boot OS.
- Cài package.
- Render config.
- Mount storage.
- Load model/cache.
- Start process.
- Register service.
- Pass health check.

Nếu startup dài:

- Dùng golden AMI.
- Dùng warm pool.
- Tăng health check grace period.
- Cấu hình instance warmup.
- Dùng lifecycle hook.
- Ghi log user data rõ ràng.

Rủi ro phổ biến:

- User data chạy lâu.
- Script không idempotent.
- NAT/DNS/endpoint thiếu.
- Application start trước config.
- Instance bị đánh unhealthy khi vẫn đang boot.

---

# Phần II — ASG capacity và Multi-AZ

## 8. Min, desired và max capacity

~~~text
min capacity ≤ desired capacity ≤ max capacity
~~~

### Minimum capacity

Số instance thấp nhất ASG giữ lại. Dùng để bảo đảm baseline availability.

### Desired capacity

Số instance ASG muốn chạy hiện tại. Desired có thể thay đổi bởi:

- Scaling policy.
- Scheduled action.
- Predictive scaling.
- Manual update.
- Capacity rebalance.
- Instance replacement.

### Maximum capacity

Giới hạn trên khi scale out. Max capacity bảo vệ:

- Cost.
- Database connections.
- Downstream service.
- AWS quota.
- Network capacity.
- Blast radius khi metric sai.

Ví dụ:

~~~text
Min:     2
Desired: 4
Max:     20
~~~

Traffic tăng có thể đưa ASG từ 4 lên tối đa 20; traffic giảm không đưa ASG dưới 2.

## 9. Capacity không đồng nghĩa performance

Tăng instance không luôn giải quyết:

- Database quá tải.
- Connection pool quá lớn.
- External API bị throttle.
- Lock contention.
- Shared storage bottleneck.
- Network bottleneck.
- Application single-threaded.
- Cache miss.
- Downstream timeout.

Trước khi tăng max capacity, kiểm tra downstream có chịu được không.

## 10. ASG Multi-AZ

~~~text
ASG
├── Private App Subnet AZ-a
├── Private App Subnet AZ-b
└── Private App Subnet AZ-c
~~~

Lợi ích:

- Giảm failure domain.
- Tăng availability.
- Hỗ trợ ALB Multi-AZ.
- Tiếp tục phục vụ khi một AZ lỗi.
- Dễ scale capacity.

Kiểm tra:

- Mỗi subnet đủ IP.
- AZ có capacity.
- Route/SG/NACL giống nhau.
- Target registration đúng.
- Database/cache dependency cũng resilient.
- NAT/endpoint path không là single-AZ dependency.
- Desired capacity đủ chịu mất một AZ.

Ví dụ:

~~~text
Desired = 4
AZ-a = 2
AZ-b = 2

Mất AZ-a:
  còn 2 instance ở AZ-b
~~~

Đây là mục tiêu thiết kế; phân bố thực tế còn phụ thuộc subnet, capacity và lỗi launch.

## 11. Subnet capacity và AZ capacity

ASG scale out có thể fail dù policy đúng vì:

- Subnet hết IP.
- Instance type không còn capacity ở AZ.
- EC2 quota hết.
- EBS quota/IOPS limit.
- Security Group/VPC sai.
- AMI không tồn tại.
- Spot capacity không có.

Cần dự trù:

- IP cho EC2 ENI.
- Interface endpoint ENI.
- Lambda/ECS ENI.
- Load Balancer ENI.
- Blue/green fleet.
- Growth peak.

---

# Phần III — Health check và replacement

## 12. EC2 health check và ELB health check

### EC2 health check

Phát hiện vấn đề hạ tầng/instance:

- Instance stopped.
- Instance terminated.
- System status failure.
- Instance không healthy ở host level.

EC2 có thể vẫn running khi application hỏng.

### ELB health check

Kiểm tra target qua ALB/NLB:

- Protocol.
- Port.
- Path.
- Status code.
- Target response.

Nếu muốn ASG thay instance khi application unhealthy, bật ELB health check trên ASG.

## 13. Health check flow

~~~text
EC2 process còn running
  ↓
Application trả HTTP 500
  ↓
ALB health check fail
  ↓
ALB ngừng gửi traffic
  ↓
ASG replace instance nếu ELB health check bật
~~~

Có hai câu hỏi khác nhau:

1. Load Balancer có gửi traffic tới target không?
2. ASG có terminate/replace target không?

Target có thể bị ALB loại khỏi traffic nhưng vẫn tồn tại trong ASG nếu ASG chỉ dùng EC2 health check.

## 14. Health check grace period

Grace period là thời gian ASG chờ instance mới khởi tạo trước khi đánh giá health nghiêm ngặt.

Nếu quá ngắn:

~~~text
Instance đang boot
  ↓
Health check fail
  ↓
ASG terminate
  ↓
Launch instance mới
  ↓
Health check fail tiếp
~~~

Nếu quá dài:

- Instance lỗi được giữ lâu.
- Capacity thực tế thấp.
- Recovery chậm.

Grace period nên dựa trên startup time thực tế, thường xem p95/p99.

## 15. Health endpoint

Health endpoint nên:

- Nhẹ.
- Nhanh.
- Ổn định.
- Có status code rõ.
- Không yêu cầu login nếu LB không gửi credential.
- Không phụ thuộc mọi downstream.
- Phản ánh khả năng nhận traffic.

Có thể tách:

~~~text
/health/live
  → process còn sống

/health/ready
  → instance sẵn sàng nhận traffic

/health/deep
  → kiểm tra dependency sâu hơn
~~~

Deep health check có thể hữu ích cho monitoring nhưng dễ gây cascade termination nếu database tạm lỗi.

## 16. Instance replacement

Luồng thông thường:

1. Instance bị đánh unhealthy.
2. ASG quyết định terminate.
3. Load Balancer deregister target.
4. Lifecycle hook có thể tạm dừng termination.
5. Instance bị terminate.
6. ASG launch instance mới.
7. Instance đăng ký target group.
8. Health check chạy.
9. Target healthy nhận traffic.

Dữ liệu không nên nằm duy nhất trên instance. Dùng EBS/snapshot, S3, RDS, DynamoDB, EFS hoặc datastore phù hợp.

---

# Phần IV — Scaling policies

## 17. Target tracking

Target tracking giữ metric gần một giá trị mục tiêu.

Ví dụ:

~~~text
Average CPU target = 50%
Request count per target = 1000
Queue age target = 60 seconds
~~~

Phù hợp:

- Web stateless.
- Metric có quan hệ với capacity.
- Muốn policy đơn giản.
- Muốn tự động scale out/in.

Rủi ro:

- Chọn sai metric.
- Warmup sai.
- Target không phản ánh bottleneck.
- Scale oscillation.
- Downstream không chịu được scale out.

## 18. Step scaling

Step scaling phản ứng theo các mức alarm.

~~~text
CPU < 60%:
  không đổi

CPU 60–75%:
  thêm 1 instance

CPU 75–90%:
  thêm 2 instance

CPU > 90%:
  thêm 4 instance
~~~

Phù hợp:

- Burst lớn.
- Cần scale amount khác nhau.
- Queue backlog tăng nhanh.
- Cần kiểm soát rõ scale action.

Cần cấu hình CloudWatch alarm, threshold, step adjustment, warmup và scale-in policy.

## 19. Simple scaling

Simple scaling là model cũ hơn, thường chờ cooldown sau scaling activity trước khi xử lý tiếp. Trong architecture mới, target tracking hoặc step scaling thường linh hoạt hơn.

## 20. Scheduled scaling

Scheduled scaling thay đổi capacity theo lịch.

~~~text
07:30:
  min 4, desired 6

19:00:
  min 1, desired 1
~~~

Phù hợp:

- Traffic giờ hành chính.
- Campaign biết trước.
- Batch job.
- Non-production environment.
- Workload theo ngày/tuần.

Có thể kết hợp:

- Scheduled scaling chuẩn bị baseline.
- Target tracking xử lý traffic lệch dự báo.

## 21. Predictive scaling

Predictive scaling dùng pattern lịch sử để dự báo capacity.

Phù hợp khi:

- Traffic có pattern lặp lại.
- Có đủ lịch sử.
- Startup time dài.
- Peak có thể dự đoán.

Không phù hợp khi traffic hoàn toàn ngẫu nhiên hoặc workload mới chưa có history.

## 22. Chọn metric scaling

| Workload | Metric thường phù hợp |
|---|---|
| Compute-bound web | CPU utilization |
| Web request ổn định | ALB request count per target |
| Memory-bound app | Memory custom metric |
| Worker SQS | Queue age/backlog |
| Long-running job | Jobs pending/processing latency |
| Connection service | Active connections |
| Database proxy/app tier | Connection count hoặc latency |

CPU thấp không chứng minh app không cần scale. Bottleneck có thể nằm ở memory, network, I/O, lock, connection hoặc downstream.

## 23. Scale theo ALB request count

Request count per target phù hợp hơn CPU khi request là tín hiệu chính:

~~~text
Request count per target vượt target
  ↓
ASG scale out
~~~

Cần chú ý:

- Request có độ nặng khác nhau.
- Một request dài có thể nặng hơn nhiều request nhẹ.
- Database vẫn có thể là bottleneck.
- Target registration/warmup ảnh hưởng metric.

## 24. Scale worker theo SQS

~~~text
Producer
  ↓
SQS queue
  ↓
Worker ASG
~~~

Metric thường dùng:

- Approximate age of oldest message.
- Visible message count.
- Backlog per instance.
- Processing latency.
- Error rate.
- DLQ count.

Queue age thường phản ánh user-visible delay tốt hơn CPU worker.

Worker phải idempotent vì message có thể được giao lại.

---

# Phần V — Warmup, cooldown và ổn định

## 25. Instance warmup

Instance mới cần thời gian boot và warm application. Nếu ASG scale tiếp trước khi instance mới có tác dụng:

~~~text
Metric cao
  → launch 2 instance

Instance chưa warm
  → metric vẫn cao

ASG launch thêm 4 instance
  → scale storm
~~~

Warmup nên phản ánh thời gian instance thực sự nhận traffic ổn định.

## 26. Cooldown

Cooldown/stabilization giúp ASG chờ effect của scaling trước khi action tiếp theo.

Cooldown quá dài:

- Recovery chậm.
- Không đáp ứng burst.

Cooldown quá ngắn:

- Scale storm.
- Cost tăng.
- Metric chưa phản ánh capacity mới.

## 27. Scale out và scale in

Scale out nên:

- Nhanh hơn.
- Có thể dùng step lớn khi overload.
- Chấp nhận capacity dư tạm thời.
- Bảo vệ latency.

Scale in nên:

- Thận trọng.
- Có drain connection/job.
- Không terminate instance đang xử lý.
- Có thể dùng scale-in protection.
- Có termination policy phù hợp.

## 28. Oscillation

Oscillation là ASG liên tục scale out rồi scale in.

Nguyên nhân:

- Metric quá nhạy.
- Target sai.
- Warmup/cooldown không phù hợp.
- Traffic burst ngắn.
- Health check chập chờn.
- App boot không ổn định.

Giải pháp:

- Điều chỉnh warmup/cooldown.
- Chọn metric ít nhiễu hơn.
- Tách ngưỡng scale out/in.
- Tăng startup reliability.
- Dùng scheduled baseline.
- Kiểm tra health check.

---

# Phần VI — Lifecycle hook và instance refresh

## 29. Lifecycle hook

Lifecycle hook tạm dừng instance ở:

- Pending:Wait.
- Terminating:Wait.

~~~text
Launch:
  Pending → Pending:Wait → InService

Terminate:
  InService → Terminating:Wait → Terminated
~~~

### Launch hook

Dùng để:

- Cài config.
- Đăng ký service.
- Mount EFS.
- Warm cache.
- Load artifact/model.
- Chạy security scan.
- Signal ready.

### Termination hook

Dùng để:

- Drain connection.
- Upload log.
- Checkpoint job.
- Deregister service.
- Flush queue.
- Cleanup registration.

Hook cần:

- Timeout.
- Default result.
- Automation không phụ thuộc duy nhất vào instance sắp chết.
- Monitoring lỗi.
- Cơ chế fallback.

## 30. Instance refresh

Khi đổi AMI, Launch Template, user data, Security Group, instance type hoặc EBS mapping, instance cũ không tự đổi.

Instance refresh thay dần fleet:

~~~text
Old fleet
  ↓
Launch new version
  ↓
Wait healthy/warmup
  ↓
Terminate old version
  ↓
Repeat
~~~

Cần xác định:

- Minimum healthy percentage.
- Maximum healthy percentage.
- Warmup.
- Checkpoint.
- Rollback.
- Health check.
- Capacity headroom.
- Behavior khi launch fail.

## 31. Blue/green và rolling deployment

### Blue/green

~~~text
ALB
├── Blue ASG
└── Green ASG
~~~

Chuyển traffic bằng ALB weighted target group, Route 53 weighted routing hoặc deployment controller.

Ưu điểm:

- Rollback nhanh.
- Fleet độc lập.
- Test version mới trước.

Nhược điểm:

- Tốn capacity tạm thời.
- Database schema phải backward compatible.
- Session/cache/job state cần xử lý.

### Rolling

Thay instance từng phần trong cùng ASG.

Ưu điểm:

- Ít capacity dư hơn.
- Dùng được với instance refresh.

Nhược điểm:

- Version cũ/mới chạy song song.
- Rollback phức tạp.
- Có thể giảm capacity nếu minimum healthy thấp.

---

# Phần VII — Mixed instances và Spot

## 32. Mixed instances policy

ASG có thể dùng nhiều instance type và purchasing option:

~~~text
On-Demand baseline
  + Spot burst
  + nhiều instance type
  + nhiều Availability Zone
~~~

Cần cấu hình:

- Instance type overrides.
- On-Demand base capacity.
- Spot percentage.
- Allocation strategy.
- Capacity-optimized behavior.
- Instance weight nếu size khác nhau.
- Capacity rebalance.
- Interruption handling.

## 33. Spot trong ASG

Spot phù hợp khi instance có thể:

- Bị interrupt.
- Được thay thế.
- Retry job.
- Checkpoint state.
- Không giữ dữ liệu duy nhất.
- Scale từ queue.

Không phù hợp khi:

- Một instance là database chính.
- Session chỉ ở local memory.
- Job không thể resume.
- Workload cần uptime liên tục.
- Không có fallback.
- Termination gây mất dữ liệu.

Khi dùng Spot, cần:

- Checkpoint.
- Idempotency.
- Queue retry.
- DLQ.
- On-Demand baseline.
- Nhiều instance type/AZ.
- Graceful drain.
- Interruption monitoring.

## 34. Capacity rebalance

Capacity rebalance giúp thay Spot instance có nguy cơ bị interrupt trước khi bị thu hồi, nếu feature/workload hỗ trợ.

Kết hợp:

- Interruption notice.
- Dừng nhận job mới.
- Checkpoint.
- Gửi message retry.
- Deregister target.
- Launch replacement.

## 35. Spot và load balancer

Khi Spot làm web target:

- Có đủ On-Demand baseline.
- Application stateless.
- Session externalized.
- ALB health check đúng.
- Deregistration delay đủ.
- Interruption không làm mất toàn bộ capacity.
- ASG có thể launch replacement.

---

# Phần VIII — Warm pool và startup time

## 36. Warm pool

Warm pool giữ instance đã chuẩn bị ở trạng thái phù hợp để scale out nhanh hơn.

Phù hợp:

- Startup rất lâu.
- Cài application nặng.
- Load model lớn.
- Container/agent initialization dài.
- Traffic burst cần phản ứng nhanh.

Trade-off:

- Tốn storage/compute cost tùy trạng thái.
- Có thể configuration drift.
- Cần lifecycle management.
- Không thay thế health check.

Warm pool hiệu quả hơn khi:

- AMI hoàn chỉnh.
- User data idempotent.
- Secret/config lấy runtime.
- Instance refresh cập nhật warm pool.
- Registration/health rõ.

---

# Phần IX — ASG với Load Balancer và worker

## 37. ASG với ALB/NLB

Luồng đăng ký:

~~~text
Instance launch
  ↓
Register target
  ↓
Health check
  ↓
Healthy
  ↓
Nhận traffic
~~~

Luồng terminate:

~~~text
Deregister target
  ↓
Drain connection
  ↓
Terminate instance
~~~

Kiểm tra:

- Target group đúng.
- Port đúng.
- Health check đúng.
- ALB-SG → App-SG đúng.
- Subnet route đúng.
- ALB ở nhiều AZ.
- ASG dùng đúng target group.

## 38. Session state

Load balancer phù hợp nhất với application stateless.

Nếu session ở local memory:

~~~text
Request 1 → Instance A tạo session
Request 2 → Instance B không thấy session
~~~

Sticky session có thể giúp tạm thời nhưng có trade-off:

- Failover kém linh hoạt.
- Traffic lệch.
- Instance lỗi làm session mất.
- Deployment phức tạp.
- Không giải quyết shared state.

Tốt hơn là externalize session vào:

- ElastiCache.
- DynamoDB.
- Database.
- Shared session service.

## 39. Worker ASG với SQS

~~~text
Producer
  ↓
SQS
  ↓
Worker ASG
  ↓
S3/Database
~~~

Worker ASG không nhất thiết cần ALB. Scale theo queue age/backlog.

Cần xử lý:

- Visibility timeout.
- Duplicate message.
- Idempotency.
- DLQ.
- Graceful shutdown.
- Checkpoint.
- Downstream throttling.
- Retry storm.

ASG tạo worker capacity; application vẫn chịu trách nhiệm xử lý concurrency và business correctness.

---

# Phần X — Cost và governance

## 40. Cost driver

Chi phí ASG gồm:

- EC2 instance hours.
- EBS volume.
- EBS snapshot.
- Data transfer.
- Load Balancer.
- NAT Gateway.
- CloudWatch metrics/logs.
- On-Demand/Spot mix.
- Warm pool.
- Idle minimum capacity.

Tối ưu:

- Right-size instance.
- Schedule non-production.
- Target tracking đúng.
- Dùng Spot cho workload chịu interruption.
- Dùng Savings Plans cho baseline.
- Dọn AMI/snapshot/EBS mồ côi.
- Giới hạn max capacity.
- Theo dõi cost per request/job.

## 41. Max capacity là cost guardrail

Max capacity bảo vệ khỏi:

- Retry storm.
- Queue poison message.
- DDoS/abuse.
- Metric sai.
- Downstream failure làm backlog.
- Auto Scaling runaway.

Max quá thấp:

- Không đáp ứng peak.
- Latency tăng.
- Queue age tăng.

Max quá cao:

- Bill tăng.
- Database quá tải.
- Downstream bị throttle.

Chọn max dựa trên performance test, quota và budget.

## 42. Tagging

Nên gắn:

- Application.
- Environment.
- Owner.
- CostCenter.
- ManagedBy.
- Version.
- DataClass.
- ExpirationDate.

Cấu hình propagate tag tới instance/volume và kiểm tra resource mới có tag đúng.

---

# Phần XI — Troubleshooting

## 43. ASG không launch instance

Kiểm tra:

1. Desired capacity lớn hơn số instance hiện tại chưa?
2. Max capacity đã đạt chưa?
3. Launch Template version còn tồn tại không?
4. AMI còn tồn tại và đúng Region không?
5. Instance type có capacity ở AZ không?
6. Subnet còn IP không?
7. EC2 quota còn không?
8. IAM service-linked role đúng không?
9. Security Group/VPC/subnet hợp lệ không?
10. EBS/KMS permission đúng không?
11. Spot capacity có sẵn không?
12. User data có làm boot fail không?

## 44. ASG launch rồi terminate liên tục

Nguyên nhân:

- Grace period quá ngắn.
- Health check path sai.
- App boot quá lâu.
- App không listen port.
- SG/NACL chặn ALB.
- User data lỗi.
- AMI lỗi.
- Target group port sai.
- Thiếu IAM permission.
- NAT/DNS/endpoint không hoạt động.
- ELB health check quá sâu.
- Scale-in xảy ra quá sớm.

Luồng điều tra:

~~~text
ASG activity history
  ↓
Target health reason
  ↓
EC2 system/application log
  ↓
ALB access/health log
  ↓
SG/NACL/route
  ↓
User data/AMI/IAM
~~~

## 45. ASG không scale out

Kiểm tra:

- Metric có vượt target/threshold không?
- Alarm có ở ALARM không?
- Policy gắn đúng ASG chưa?
- Max capacity đã đạt chưa?
- Warmup/cooldown có chặn không?
- Scheduled action có override không?
- Instance launch có fail không?
- Subnet hết IP không?
- Quota/capacity đủ không?
- Metric dimension đúng không?
- CloudWatch delay có đáng kể không?

## 46. ASG scale out quá nhiều

Nguyên nhân:

- Metric sai.
- Target quá thấp.
- Warmup chưa đúng.
- Application không nhận traffic mới.
- Queue backlog do worker lỗi chứ không phải thiếu worker.
- Retry storm.
- Database bottleneck.
- Health check chập chờn.
- Max capacity thiếu guardrail.

Đừng chỉ giảm max; phải tìm nguyên nhân demand signal sai.

## 47. ASG scale in quá sớm

Kiểm tra:

- Scale-in policy.
- Target tracking target.
- Warmup/cooldown.
- Deregistration delay.
- Termination policy.
- Scale-in protection.
- Scheduled scaling.
- Worker job đang xử lý.
- Connection còn active.

## 48. Target unhealthy nhưng instance healthy

~~~text
EC2 system healthy
Application unhealthy
~~~

Kiểm tra:

- Health check path/port.
- App process.
- App-SG allow từ ALB-SG.
- NACL.
- Route.
- Host header.
- TLS.
- App startup.
- Dependency.

Nếu muốn ASG replace app-unhealthy instance, ELB health check phải bật trên ASG.

## 49. Instance không nhận traffic

1. Instance đã registered target group chưa?
2. Target health healthy chưa?
3. Listener rule forward đúng target group chưa?
4. Weight có bằng zero không?
5. SG cho phép không?
6. AZ/subnet đúng không?
7. Target port đúng không?
8. Instance listen private IP không?
9. NACL có chặn không?
10. ALB behavior có ảnh hưởng không?

---

# Phần XII — Architecture pattern

## 50. Web tier stateless

~~~text
Route 53
  ↓
ALB Multi-AZ
  ↓
ASG Multi-AZ
  ↓
EC2 stateless
  ↓
RDS/ElastiCache/S3
~~~

Đặc điểm:

- Không giữ session local.
- Không lưu file duy nhất trên instance.
- Session externalized.
- AMI immutable.
- Health check readiness.
- Scale theo request/CPU.
- Database/cache có capacity phù hợp.

## 51. Worker ASG với SQS

~~~text
Application
  ↓
SQS
  ↓
Worker ASG
  ↓
S3/Database
~~~

Dùng queue age/backlog để scale. Dùng Spot nếu job retry/checkpoint được và có On-Demand baseline khi cần.

## 52. Scheduled web scaling

~~~text
07:00 → desired 4
12:00 → desired 10
19:00 → desired 3
~~~

Kết hợp target tracking để xử lý traffic lệch dự báo.

## 53. Blue/green với hai ASG

~~~text
ALB
├── Blue ASG
└── Green ASG
~~~

Cần xử lý:

- Database schema compatibility.
- Session.
- Cache.
- Background job duplicate.
- Observability.
- Cost tạm thời.
- Rollback.

---

# Phần XIII — Lab thực hành

## 54. Lab 1: Launch Template

1. Launch EC2 test từ AMI.
2. Cài application tối thiểu.
3. Tạo golden AMI versioned.
4. Tạo Launch Template.
5. Cấu hình instance profile.
6. Cấu hình Security Group.
7. Cấu hình EBS mapping.
8. Bật IMDSv2.
9. Thêm tags.
10. Launch instance từ template.
11. Kiểm tra application boot.
12. Tạo version mới và ghi thay đổi.

## 55. Lab 2: ASG Multi-AZ với ALB

1. Chọn hai private app subnet ở hai AZ.
2. Tạo target group.
3. Cấu hình health check.
4. Tạo ALB ở public subnet.
5. Tạo ASG với min/desired/max.
6. Gắn ASG vào target group.
7. Kiểm tra target registration.
8. Gọi ALB DNS.
9. Terminate một instance.
10. Quan sát ASG launch replacement.
11. Kiểm tra target health.
12. Kiểm tra activity history.

## 56. Lab 3: Target tracking

1. Tạo target tracking theo CPU hoặc ALB request count per target.
2. Tạo load vừa phải.
3. Quan sát CloudWatch metric.
4. Kiểm tra desired capacity.
5. Kiểm tra instance warmup.
6. Giảm traffic.
7. Quan sát scale-in.
8. Kiểm tra deregistration delay.
9. Đánh giá oscillation.

## 57. Lab 4: Scheduled scaling

1. Đặt min/desired thấp ngoài giờ.
2. Tạo scheduled action trước giờ peak.
3. Tăng desired capacity.
4. Kết hợp target tracking.
5. Theo dõi capacity trước peak.
6. Tạo action scale-in sau peak.
7. Kiểm tra drain và graceful shutdown.
8. Điều chỉnh warmup.

## 58. Lab 5: Health check failure

1. Tạo endpoint health.
2. Xác nhận target healthy.
3. Đổi health check path sai.
4. Quan sát target unhealthy.
5. Kiểm tra target reason.
6. Kiểm tra ASG activity history.
7. Restore health path.
8. Dừng một instance.
9. Quan sát replacement.
10. So sánh EC2 và ELB health check.

## 59. Lab 6: Instance refresh

1. Tạo Launch Template version mới.
2. Đổi AMI hoặc application version.
3. Chạy instance refresh.
4. Đặt minimum healthy percentage.
5. Đặt warmup.
6. Theo dõi checkpoint.
7. Kiểm tra target registration.
8. Thử version lỗi trên môi trường test.
9. Kiểm tra rollback.
10. Restore version ổn định.

## 60. Lab 7: Worker ASG với SQS

1. Tạo SQS queue và DLQ.
2. Tạo worker AMI.
3. Tạo worker ASG.
4. Gán IAM role đọc/delete message.
5. Publish message.
6. Scale theo queue age/backlog.
7. Test duplicate/retry.
8. Test terminate worker.
9. Xác nhận job được retry hoặc checkpoint.
10. Kiểm tra DLQ.

## 61. Dọn dẹp lab

- [ ] Xóa ASG.
- [ ] Xóa Launch Template version không dùng.
- [ ] Terminate EC2.
- [ ] Xóa target group.
- [ ] Xóa ALB/NLB.
- [ ] Xóa EBS volume/snapshot/AMI test.
- [ ] Release Elastic IP.
- [ ] Xóa CloudWatch alarm.
- [ ] Xóa SQS queue/DLQ.
- [ ] Kiểm tra NAT Gateway và billing.

---

# Phần XIV — Bẫy AWS SAA-C03

- ASG scale instance nhưng không tự phân phối traffic; cần ALB/NLB.
- Min, desired và max phải nhất quán.
- Max capacity là cost/downstream guardrail.
- Multi-AZ ASG không đồng bộ state local.
- EC2 health check có thể không phát hiện app unhealthy.
- ELB health check cần grace period phù hợp.
- Health check quá sâu có thể gây terminate hàng loạt.
- Target tracking cần metric và warmup phù hợp.
- Worker nên scale theo queue age/backlog, không mặc định theo CPU.
- Scheduled scaling phù hợp traffic predictable.
- Spot có thể bị interruption; cần checkpoint/retry/fallback.
- Mixed instances giảm dependency vào một instance type.
- Subnet hết IP có thể khiến scale out fail.
- AZ hết capacity có thể khiến launch fail.
- Instance refresh mới thay instance cũ sau khi đổi Launch Template.
- Lifecycle hook phải complete trước timeout.
- Sticky session không thay thế stateless design.
- User data không phải nơi lưu secret.
- ASG không bảo vệ dữ liệu local.
- Database, cache và downstream cũng phải chịu được scale out.
- Warm pool giảm startup latency nhưng có cost/complexity.
- Scale-out và scale-in cần độ khẩn cấp khác nhau.
- EBS volume thuộc một AZ; instance replacement cần xử lý data riêng.

---

# Phần XV — Câu hỏi tự luyện

## Câu 1

Web tier cần tự tăng instance khi request tăng và tiếp tục phục vụ khi một AZ lỗi. Thiết kế nào phù hợp?

A. Một EC2 lớn trong một AZ  
B. ALB Multi-AZ kết hợp ASG dùng private subnet ở nhiều AZ  
C. Route 53 tới một EC2  
D. EBS Multi-Attach duy nhất

Đáp án: B.

## Câu 2

ASG có min 2, desired 4 và max 10. Khi traffic tăng, ASG có thể scale tối đa bao nhiêu instance?

A. 2  
B. 4  
C. 10  
D. Không giới hạn

Đáp án: C.

## Câu 3

Instance vẫn running nhưng application trả HTTP 500. Cách để ASG thay instance?

A. Chỉ dùng EC2 health check  
B. Bật ELB health check trên ASG với endpoint phù hợp  
C. Tăng EBS size  
D. Tạo Elastic IP

Đáp án: B.

## Câu 4

ASG launch rồi terminate liên tục. Nguyên nhân có thể là gì?

A. Grace period quá ngắn hoặc health check path sai  
B. Route 53 TTL quá cao  
C. S3 lifecycle sai  
D. KMS rotation

Đáp án: A.

## Câu 5

Traffic tăng theo giờ làm việc predictable. Policy nào phù hợp?

A. Scheduled scaling kết hợp dynamic policy nếu cần  
B. Chỉ Spot  
C. Chỉ scale khi database fail  
D. Không dùng ASG

Đáp án: A.

## Câu 6

Worker xử lý SQS có backlog tăng. Metric nào phù hợp hơn CPU?

A. Queue age hoặc backlog per instance  
B. Route 53 TTL  
C. EBS snapshot count  
D. ALB listener count

Đáp án: A.

## Câu 7

Job batch có thể checkpoint và retry khi instance bị thu hồi. Cách giảm cost?

A. Spot trong ASG với retry/checkpoint và fallback  
B. Dedicated Host duy nhất  
C. Một On-Demand instance không backup  
D. Public subnet bắt buộc

Đáp án: A.

## Câu 8

Sau khi sửa Launch Template, instance đang chạy vẫn dùng AMI cũ. Vì sao?

A. Instance không tự update; cần instance refresh hoặc replacement  
B. Route 53 cache  
C. NACL cache  
D. EBS tự động đổi AMI

Đáp án: A.

## Câu 9

ASG không launch instance vì subnet hết IP. Giải pháp?

A. Mở rộng address planning/subnet hoặc dùng subnet đủ lớn  
B. Tăng health check interval  
C. Đổi Route 53 policy  
D. Tạo thêm Security Group

Đáp án: A.

## Câu 10

App giữ session trong memory local của instance. Giải pháp tốt hơn sticky session?

A. Externalize session vào ElastiCache/DynamoDB/datastore  
B. Gắn Elastic IP  
C. Tăng max capacity  
D. Dùng NACL

Đáp án: A.

## Câu 11

Cần chạy script trước khi instance mới nhận traffic. Dùng gì?

A. Launch lifecycle hook  
B. Route 53 CNAME  
C. S3 lifecycle  
D. EBS snapshot

Đáp án: A.

## Câu 12

Cần upload log và drain connection trước khi terminate instance. Dùng gì?

A. Termination lifecycle hook và deregistration delay  
B. Tăng DNS TTL  
C. Public IP  
D. Cluster placement group

Đáp án: A.

---

# Phần XVI — Checklist ghi nhớ

## Launch Template

- [ ] Có AMI versioned.
- [ ] Có instance type phù hợp.
- [ ] Có Security Group đúng.
- [ ] Có IAM instance profile.
- [ ] Có EBS mapping phù hợp.
- [ ] Có User Data idempotent.
- [ ] Bật IMDSv2.
- [ ] Có tags.
- [ ] Có version/rollback.

## ASG capacity

- [ ] Min, desired, max hợp lý.
- [ ] Max bảo vệ cost và downstream.
- [ ] Subnet đủ IP.
- [ ] AZ có capacity.
- [ ] ASG dùng nhiều AZ.
- [ ] Desired đủ chịu failure requirement.
- [ ] State externalized.
- [ ] Target registration đúng.

## Health và replacement

- [ ] EC2 hay ELB health check phù hợp.
- [ ] Health path/port đúng.
- [ ] Grace period đủ startup time.
- [ ] Warmup/cooldown đúng.
- [ ] Health check không quá sâu.
- [ ] Deregistration delay đủ drain.
- [ ] Activity history được theo dõi.
- [ ] Replacement không làm mất data.

## Scaling policy

- [ ] Target tracking cho metric ổn định.
- [ ] Step scaling cho mức overload khác nhau.
- [ ] Scheduled scaling cho pattern predictable.
- [ ] Predictive scaling cần history.
- [ ] Worker scale theo queue metric.
- [ ] Scale out nhanh hơn scale in.
- [ ] Tránh oscillation.
- [ ] Theo dõi downstream capacity.

## Deployment

- [ ] Instance refresh khi đổi AMI/template.
- [ ] Minimum healthy percentage rõ.
- [ ] Warmup rõ.
- [ ] Rollback có.
- [ ] Blue/green nếu cần rollback nhanh.
- [ ] Database schema backward compatible.
- [ ] Session/cache/job state không phụ thuộc instance cũ.

## Spot và cost

- [ ] Job Spot có checkpoint/retry.
- [ ] Có On-Demand baseline nếu cần.
- [ ] Có nhiều instance type/AZ.
- [ ] Có interruption handling.
- [ ] Có capacity rebalance nếu phù hợp.
- [ ] Có max capacity.
- [ ] Có tag owner/environment/cost center.
- [ ] Có schedule cho non-production.

---

## Tóm tắt cuối

1. Launch Template mô tả cách tạo EC2; ASG duy trì và điều chỉnh capacity.
2. Min là giới hạn thấp nhất, desired là capacity hiện tại, max là giới hạn trên.
3. ASG không tự phân phối traffic; cần ALB/NLB hoặc worker queue.
4. Multi-AZ ASG giảm failure domain nhưng state phải externalize.
5. EC2 health check kiểm tra hạ tầng; ELB health check kiểm tra target/application path.
6. Grace period phải đủ cho startup.
7. Target tracking giữ metric quanh target.
8. Step scaling phản ứng theo mức alarm.
9. Scheduled scaling phù hợp traffic có lịch.
10. Worker nên scale theo queue age/backlog hơn CPU đơn thuần.
11. Warmup/cooldown giúp tránh scale storm và oscillation.
12. Lifecycle hook dùng để setup, drain, checkpoint và cleanup.
13. Instance refresh thay fleet khi đổi AMI/Launch Template.
14. Mixed On-Demand/Spot cân bằng cost và availability.
15. Spot chỉ phù hợp workload có thể bị interrupt và xử lý lại.
16. Subnet hết IP hoặc AZ thiếu capacity có thể làm scale out thất bại.
17. Instance replacement không tự bảo vệ dữ liệu local.
18. Sticky session không tốt bằng externalized session cho scale/HA dài hạn.
19. Max capacity là performance guardrail và cost guardrail.
20. Troubleshooting ASG cần xem activity history, target health, CloudWatch, log, route và security.

## Tài liệu tham khảo chính thức

- [Amazon EC2 Auto Scaling](https://docs.aws.amazon.com/autoscaling/ec2/userguide/what-is-amazon-ec2-auto-scaling.html)
- [Auto Scaling groups](https://docs.aws.amazon.com/autoscaling/ec2/userguide/auto-scaling-groups.html)
- [Launch templates](https://docs.aws.amazon.com/autoscaling/ec2/userguide/launch-templates.html)
- [EC2 Auto Scaling health checks](https://docs.aws.amazon.com/autoscaling/ec2/userguide/ec2-auto-scaling-health-checks.html)
- [Target tracking scaling](https://docs.aws.amazon.com/autoscaling/ec2/userguide/as-scaling-target-tracking.html)
- [Step and simple scaling](https://docs.aws.amazon.com/autoscaling/ec2/userguide/as-scaling-simple-step.html)
- [Scheduled scaling](https://docs.aws.amazon.com/autoscaling/ec2/userguide/ec2-auto-scaling-scheduled-scaling.html)
- [Predictive scaling](https://docs.aws.amazon.com/autoscaling/ec2/userguide/ec2-auto-scaling-predictive-scaling.html)
- [Lifecycle hooks](https://docs.aws.amazon.com/autoscaling/ec2/userguide/lifecycle-hooks.html)
- [Instance refresh](https://docs.aws.amazon.com/autoscaling/ec2/userguide/asg-instance-refresh.html)
- [Mixed instances policy](https://docs.aws.amazon.com/autoscaling/ec2/userguide/ec2-auto-scaling-mixed-instances-groups.html)
- [Warm pools](https://docs.aws.amazon.com/autoscaling/ec2/userguide/ec2-auto-scaling-warm-pools.html)
- [Amazon EC2 Spot Instances](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/using-spot-instances.html)


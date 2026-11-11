# Tuần 1 — Cloud fundamentals và tư duy Well-Architected

> Mục tiêu: hiểu cách AWS tổ chức hạ tầng, biết mô tả requirement trước khi chọn dịch vụ và review được một workload theo sáu trụ cột Well-Architected.

## 1. Kết quả cần đạt

Sau tuần này, bạn có thể:

- Giải thích Region, Availability Zone và edge location.
- Phân biệt availability, durability, scalability, elasticity, resilience và fault tolerance.
- Phân biệt horizontal scaling với vertical scaling; giải thích vì sao stateless application dễ scale hơn.
- Phân biệt RTO, RPO, SLA, SLI và SLO.
- Mô tả Shared Responsibility Model cho EC2, S3, RDS, Lambda và VPC.
- Vẽ kiến trúc single-AZ/Multi-AZ và chỉ ra single point of failure.
- Viết requirement table trước khi chọn dịch vụ.
- Review workload theo Operational Excellence, Security, Reliability, Performance Efficiency, Cost Optimization và Sustainability.

## 2. Lịch học gợi ý

| Buổi | Nội dung | Thời lượng |
|---|---|---:|
| 1 | Cloud model, global infrastructure, shared responsibility | 2 giờ |
| 2 | Reliability vocabulary và scaling | 2 giờ |
| 3 | Sáu pillar của Well-Architected | 3 giờ |
| 4 | Vẽ single-AZ/Multi-AZ và review kiến trúc | 2–3 giờ |
| 5 | Budget, MFA, tagging và câu hỏi tự luyện | 2 giờ |

Với mỗi khái niệm, hãy trả lời: nó giải quyết vấn đề gì, đánh đổi điều gì và từ khóa nào trong đề thi gợi ý nó?

---

## 3. Cloud fundamentals

### 3.1. Cloud computing

Cloud computing là mô hình sử dụng tài nguyên CNTT theo nhu cầu qua mạng thay vì tự mua và vận hành toàn bộ phần cứng. Đặc điểm chính:

- **On-demand:** tạo hoặc giải phóng tài nguyên khi cần.
- **Pay-as-you-go:** chi phí gắn với mức sử dụng hoặc cam kết.
- **Elasticity:** tăng/giảm tài nguyên theo tải.
- **Global reach:** triển khai gần người dùng ở nhiều khu vực.
- **Managed services:** AWS quản lý một phần hoặc phần lớn hạ tầng.

Cloud không tự động làm ứng dụng highly available. Kiến trúc sư vẫn phải chọn topology, replication, health check, backup, security control và cách xử lý failure.

### 3.2. IaaS, managed platform và serverless

| Mô hình | Ví dụ | AWS thường quản lý | Bạn vẫn quản lý |
|---|---|---|---|
| IaaS | EC2 | datacenter, phần cứng, hypervisor | OS patch, app, data, IAM, network config |
| Managed platform | RDS, ECS/Fargate | nhiều phần hạ tầng và nền tảng | schema, query, dữ liệu, cấu hình, quyền |
| Serverless/managed | S3, DynamoDB, Lambda | server và scaling nền | code/data, IAM, input, quota, chi phí |

Từ khóa **least operational overhead** thường hướng tới managed hoặc serverless nếu vẫn đáp ứng các requirement khác.

---

## 4. AWS global infrastructure

### 4.1. Region

**Region** là khu vực địa lý độc lập gồm nhiều Availability Zone. Region có thể khác nhau về dịch vụ khả dụng, quota, pricing và yêu cầu compliance.

Khi chọn Region, cân nhắc:

- latency tới người dùng và hệ thống phụ thuộc;
- dịch vụ cần dùng có sẵn không;
- yêu cầu lưu trữ dữ liệu theo quốc gia/compliance;
- giá, quota và khả năng hỗ trợ;
- kế hoạch disaster recovery cross-Region.

Multi-Region có thể tăng resilience nhưng cũng tăng cost, độ phức tạp và vấn đề đồng bộ dữ liệu.

### 4.2. Availability Zone

**Availability Zone (AZ)** là một hoặc nhiều data center tách biệt về vật lý trong một Region, có failure domain riêng. Các AZ trong cùng Region kết nối bằng mạng độ trễ thấp.

- Multi-AZ giảm ảnh hưởng khi một AZ gặp sự cố.
- Multi-AZ không đồng nghĩa với Multi-Region.
- Workload chỉ ở một AZ có thể tạo single point of failure.
- Database Multi-AZ chủ yếu phục vụ availability/failover; không mặc nhiên là read scaling.

### 4.3. Edge location

**Edge location** là điểm hiện diện gần người dùng, thường được CloudFront và dịch vụ edge dùng để giảm latency. Edge location không phải Region và cũng không phải AZ.

Ví dụ: file tĩnh được cache ở edge thay vì mọi request đều đi tới origin trong Region.

### 4.4. Failure domain

```text
Resource/instance → AZ → Region → nhiều Region
```

Mở rộng failure domain thường tăng khả năng chịu lỗi nhưng cũng tăng chi phí và độ phức tạp. Chọn mức phù hợp với RTO, RPO, business impact và ngân sách.

---

## 5. Shared Responsibility Model

AWS chịu trách nhiệm **security of the cloud**: bảo vệ hạ tầng chạy dịch vụ AWS, gồm phần cứng, software nền, networking và facility.

Khách hàng chịu trách nhiệm **security in the cloud**: dữ liệu, IAM, OS trong EC2, network rule, mã hóa, secret, ứng dụng và cấu hình dịch vụ.

| Dịch vụ | AWS chịu trách nhiệm | Khách hàng chịu trách nhiệm |
|---|---|---|
| EC2 | host, hypervisor, physical security | OS patch, package, app, SG, IAM role, data |
| S3 | hạ tầng lưu trữ, durability của dịch vụ | bucket policy, Block Public Access, encryption, data |
| RDS | host, engine platform, backup capability theo cấu hình | credentials, network, schema, query, data |
| Lambda | server, runtime infrastructure, scaling nền | code, dependency, execution role, secret, input |
| VPC | hạ tầng mạng AWS | CIDR, route, SG, NACL, endpoint và flow thiết kế |

Dịch vụ càng managed thì khách hàng quản lý càng ít server, nhưng vẫn phải chịu trách nhiệm về dữ liệu, quyền truy cập và cấu hình.

### Bẫy thường gặp

- AWS không tự patch guest OS của EC2.
- S3 không thay thế việc cấu hình policy, encryption và public access.
- Managed database vẫn cần network access, credentials, encryption và authorization.
- Không mặc định rằng mọi dịch vụ đều tự backup; phải kiểm tra service và cấu hình cụ thể.

---

## 6. Reliability và performance vocabulary

### 6.1. Availability và durability

| Khái niệm | Câu hỏi | Ví dụ |
|---|---|---|
| **Availability** | Dịch vụ có phục vụ request khi cần không? | còn nhận request khi một instance hỏng |
| **Durability** | Dữ liệu có được giữ nguyên, không mất không? | object vẫn tồn tại sau lỗi phần cứng |

Một hệ thống có thể durability cao nhưng availability thấp: dữ liệu được giữ nhưng ứng dụng tạm thời không truy cập được. Ngược lại, dịch vụ có thể trả lời request nhưng mất dữ liệu nếu thiếu replication/backup.

### 6.2. Scalability và elasticity

- **Scalability:** khả năng đáp ứng tải lớn hơn bằng cách tăng năng lực.
- **Elasticity:** khả năng tự động tăng hoặc giảm năng lực theo tải.

Tăng instance thủ công trước sự kiện là scalable nhưng không tự động elastic.

### 6.3. Vertical và horizontal scaling

| Cách scale | Cách làm | Ưu điểm | Hạn chế |
|---|---|---|---|
| **Vertical/scale-up** | đổi sang máy lớn hơn | đơn giản, ít thay đổi app | giới hạn phần cứng, thường cần restart |
| **Horizontal/scale-out** | thêm nhiều instance/node | tăng capacity, phù hợp HA | cần load balancing và externalized state |

Web tier thường scale-out với load balancer và Auto Scaling. Database có thể cần read replica, partitioning, sharding hoặc dịch vụ phân tán phù hợp.

### 6.4. Resilience và fault tolerance

- **Resilience:** duy trì hoặc nhanh chóng khôi phục chức năng sau sự cố.
- **Fault tolerance:** tiếp tục hoạt động mà người dùng gần như không nhận thấy lỗi ở một thành phần.

Fault tolerance thường đắt và phức tạp hơn resilience. Có thể chấp nhận vài phút restore backup thay vì vận hành active-active nếu business cho phép.

### 6.5. Stateless và stateful

**Stateless application** không giữ session hoặc trạng thái quan trọng chỉ trên local instance; request kế tiếp có thể tới instance khác.

State có thể externalize vào session store/cache, database, object storage, queue hoặc event stream. Lợi ích là instance dễ thay thế, scale-out và rolling deployment đơn giản hơn.

Không phải state nào cũng đưa vào database. Chọn nơi lưu dựa trên durability, latency, consistency, access pattern và cost.

### 6.6. Loose coupling

Queue và event bus giúp producer không phải chờ consumer xử lý ngay. Lợi ích là retry, scale độc lập và cô lập lỗi tốt hơn. Trade-off là thêm latency, eventual consistency, idempotency, monitoring và dead-letter handling.

---

## 7. RTO, RPO, SLA, SLI và SLO

- **RTO (Recovery Time Objective):** downtime tối đa chấp nhận sau sự cố.
- **RPO (Recovery Point Objective):** lượng dữ liệu tối đa có thể mất, đo theo thời gian.
- **SLI (Service Level Indicator):** chỉ số đo được, như availability, p95 latency hoặc error rate.
- **SLO (Service Level Objective):** mục tiêu nội bộ, như 99,9% request thành công.
- **SLA (Service Level Agreement):** cam kết chính thức giữa nhà cung cấp và khách hàng, thường có điều khoản service credit.

| Requirement | Hướng suy nghĩ |
|---|---|
| RTO = 4 giờ | backup/restore với runbook rõ ràng có thể đủ |
| RTO = 5 phút | warm standby hoặc replication |
| RPO = 24 giờ | backup hằng ngày có thể đủ |
| RPO gần 0 | replication thường xuyên/synchronous tùy workload |

Không chọn DR strategy trước khi biết RTO/RPO. Backup/restore, pilot light, warm standby và active-active có cost và recovery time khác nhau.

Availability gần đúng:

```text
Availability = uptime / (uptime + downtime)
```

Downtime xấp xỉ trong 30 ngày: 99% là 7 giờ 12 phút, 99,9% là 43 phút 12 giây và 99,99% là 4 phút 19 giây. Đây là con số minh họa; SLA cụ thể phụ thuộc dịch vụ và điều khoản.

SLA của từng dịch vụ không tự động là SLA end-to-end của ứng dụng. Nếu mọi thành phần nằm trong một AZ, availability tổng thể có thể thấp hơn kỳ vọng.

---

## 8. Well-Architected Framework

Well-Architected là khung câu hỏi để phát hiện rủi ro, đánh đổi và cơ hội cải thiện; không phải danh sách dịch vụ bắt buộc phải dùng.

### 8.1. Operational Excellence

Tập trung vào vận hành, quan sát và cải tiến liên tục.

Câu hỏi:

- Ai sở hữu workload và chịu trách nhiệm khi có incident?
- Có runbook, playbook, rollback và quy trình thay đổi không?
- Có metric, log, alarm và dashboard phù hợp không?
- Sau incident có review nguyên nhân và action item không?

Thực hành: Infrastructure as Code, version control, deployment có kiểm soát, CloudWatch, tagging và runbook cho backup/failover.

### 8.2. Security

Tập trung bảo vệ dữ liệu, hệ thống và quyền truy cập.

Câu hỏi:

- Có least privilege và MFA không?
- Dữ liệu có mã hóa khi truyền và khi lưu không?
- Secret có nằm ngoài source code không?
- Có audit log, phát hiện bất thường và network boundary không?

Nguyên tắc: không dùng root cho công việc hằng ngày, ưu tiên IAM role/temporary credentials, deny public access mặc định và bảo vệ endpoint.

### 8.3. Reliability

Tập trung vào khả năng hoạt động đúng, chịu lỗi và phục hồi.

Câu hỏi:

- Có single point of failure không?
- Một AZ mất thì workload còn phục vụ không?
- Backup/replication có phù hợp RTO/RPO không?
- Đã test restore/failover thật chưa?
- Có health check, timeout, retry, idempotency và dead-letter handling chưa?

“Có backup” chưa đủ; cần biết restore mất bao lâu và ai đã kiểm tra restore.

### 8.4. Performance Efficiency

Tập trung đạt latency/throughput với tài nguyên phù hợp.

Câu hỏi:

- Bottleneck nằm ở compute, storage, database hay network?
- Có chọn đúng resource type và kích thước không?
- Có cache, CDN hoặc asynchronous processing để giảm tải không?
- Có đo p50/p95/p99 thay vì chỉ average không?

Tối ưu performance không đồng nghĩa dùng máy lớn nhất; hãy đo và xử lý bottleneck thực sự.

### 8.5. Cost Optimization

Tập trung đạt business value với chi phí hợp lý.

Câu hỏi:

- Có resource nhàn rỗi hoặc over-provisioned không?
- Có thể dùng serverless/managed service không?
- Workload có predictable để dùng commitment không?
- Có lifecycle/tier/delete policy cho dữ liệu không?
- Có tính data transfer, request và NAT cost không?

“Rẻ nhất” không luôn là “cost-optimized” nếu vi phạm security, reliability hoặc performance.

### 8.6. Sustainability

Tập trung giảm tài nguyên và tác động môi trường trong vòng đời workload.

Câu hỏi:

- Có dùng tài nguyên lớn hơn nhu cầu không?
- Có scale theo tải và tắt môi trường không dùng không?
- Có giảm data transfer, storage dư thừa và xử lý không cần thiết không?
- Có chọn dịch vụ managed hoặc cấu hình hiệu quả hơn không?

Sustainability thường giao thoa với cost và performance: right-sizing, elasticity và lifecycle policy có thể đem lại cả ba lợi ích.

### 8.7. Bảng nhớ nhanh

| Pillar | Câu hỏi ngắn |
|---|---|
| Operational Excellence | Có vận hành, quan sát và cải tiến được không? |
| Security | Ai được truy cập, dữ liệu được bảo vệ thế nào? |
| Reliability | Hỏng thì hệ thống chịu và phục hồi ra sao? |
| Performance Efficiency | Đạt latency/throughput bằng tài nguyên phù hợp chưa? |
| Cost Optimization | Có đạt value mà không lãng phí không? |
| Sustainability | Có giảm tài nguyên và tác động môi trường không? |

---

## 9. Requirement-first thinking

Đừng bắt đầu bằng “dùng dịch vụ nào?”. Hãy bắt đầu bằng requirement.

| Nhóm | Câu hỏi | Ví dụ |
|---|---|---|
| Người dùng | Ai dùng, ở đâu, lưu lượng bao nhiêu? | 50.000 user, Đông Nam Á |
| Workload | web, API, batch, file, streaming? | web app + REST API |
| Availability | downtime chấp nhận bao nhiêu? | vẫn phục vụ khi một AZ lỗi |
| Data | loại, kích thước, access pattern? | ảnh lớn, đọc nhiều |
| Consistency | strong hay eventual? | giỏ hàng cần nhất quán |
| Performance | latency, throughput, burst? | p95 dưới 200 ms |
| Security | public/private, compliance, encryption? | dữ liệu cá nhân phải mã hóa |
| Recovery | RTO/RPO? | RTO 1 giờ, RPO 15 phút |
| Operations | team muốn tự vận hành đến đâu? | team nhỏ, ưu tiên managed |
| Cost | ngân sách và pattern chi phí? | traffic biến động mạnh |

### Ví dụ suy luận

**Đề bài:** website traffic biến động mạnh, phải phục vụ khi một AZ lỗi, team nhỏ và muốn giảm vận hành.

1. Traffic biến động → cần elasticity.
2. Một AZ lỗi → triển khai qua ít nhất hai AZ.
3. Team nhỏ → ưu tiên managed/serverless nếu phù hợp.
4. Web traffic → cần phân phối traffic và health check.
5. State không nên nằm trên local instance → externalize session/data.
6. Sau đó mới so sánh compute, database, storage và cache.

Không kết luận “dùng EC2” chỉ vì quen thuộc; hãy kiểm tra operational overhead và elasticity.

---

## 10. CAP và eventual consistency

CAP giúp suy nghĩ về hệ thống phân tán: khi xảy ra network partition, hệ thống phải đánh đổi giữa consistency và availability.

- **Consistency:** đọc thấy dữ liệu mới nhất theo guarantee của hệ thống.
- **Availability:** request nhận được response hợp lệ, dù có thể chưa thấy dữ liệu mới nhất.
- **Partition tolerance:** hệ thống tiếp tục xử lý khi các node liên lạc không bình thường.

Eventual consistency phù hợp với lượt xem, feed/search index cập nhật trễ, analytics và notification không yêu cầu transaction đồng bộ. Consistency mạnh hơn thường cần cho số dư, tồn kho, thanh toán và chống ghi nhận giao dịch hai lần.

Không có lựa chọn luôn đúng. Hãy dựa vào business invariant và failure behavior.

---

## 11. Bài thực hành bắt buộc

### Lab 1 — Guardrail cho AWS account

- [ ] Bật MFA cho root user.
- [ ] Không dùng root cho lab hằng ngày.
- [ ] Tạo Budget và cảnh báo 50%, 80%, 100%.
- [ ] Chọn một Region chính.
- [ ] Dùng tag `Project`, `Environment`, `Owner`, `CostCenter`.
- [ ] Kiểm tra Billing/Cost Explorer sau lab.
- [ ] Ghi lại resource phải xóa.

Đặc biệt chú ý NAT Gateway, load balancer, RDS, EKS, Elastic IP, snapshot và log retention.

### Lab 2 — Vẽ single-AZ

Vẽ:

```text
User → Internet → public subnet → web/app instance → database
```

Đánh dấu AZ, vị trí dữ liệu, request path và các điểm lỗi khi instance, AZ hoặc database hỏng.

### Lab 3 — Chuyển sang Multi-AZ

Vẽ lại với hai AZ, load balancer, nhiều application instance, state externalized, database replication/Multi-AZ phù hợp, health check, monitoring và backup. Ghi rõ trade-off về cost, data transfer, complexity và consistency.

### Lab 4 — Review sáu pillar

| Pillar | Rủi ro | Metric/bằng chứng cần xem | Cải thiện ưu tiên | Trade-off |
|---|---|---|---|---|
| Operational Excellence |  |  |  |  |
| Security |  |  |  |  |
| Reliability |  |  |  |  |
| Performance Efficiency |  |  |  |  |
| Cost Optimization |  |  |  |  |
| Sustainability |  |  |  |  |

Với mỗi pillar, ghi ít nhất một rủi ro và một thay đổi nhỏ có tác động lớn.

---

## 12. Chiến lược làm câu hỏi SAA-C03

1. Xác định workload: web, API, batch, file, data pipeline hay hybrid.
2. Gạch chân requirement bắt buộc: RTO/RPO, latency, compliance, availability, throughput, cost.
3. Nhận diện từ khóa:
   - **least operational overhead** → managed/serverless;
   - **highly available** → redundancy, Multi-AZ, health check/failover;
   - **most cost-effective** → right-size, lifecycle, elasticity;
   - **minimal changes** → giải pháp phù hợp kiến trúc hiện tại;
   - **global low latency** → edge/CDN hoặc routing phù hợp;
   - **single point of failure** → thành phần chỉ có một bản hoặc một AZ.
4. Loại đáp án vi phạm requirement trước khi so sánh giá.
5. Nếu còn nhiều đáp án đúng, chọn đáp án ít thành phần và ít vận hành nhất nhưng vẫn đạt yêu cầu.

### Bẫy tuần 1

- Multi-AZ xử lý failure trong Region; không tự động xử lý Region outage.
- Availability khác durability.
- Horizontal scaling cần xử lý state; không tự động có chỉ vì thêm instance.
- SLA dịch vụ không đồng nghĩa SLA end-to-end của ứng dụng.
- Backup chưa test restore thì chưa chứng minh recovery.
- Managed service giảm quản lý server, không loại bỏ trách nhiệm IAM/data/configuration.
- Cost thấp nhất không được chọn nếu vi phạm RTO, security hoặc performance.

---

## 13. Câu hỏi tự luyện

### Câu 1

Một ứng dụng chạy trên một EC2 instance trong một AZ. Khi instance lỗi, người dùng không truy cập được. Điểm yếu chính là gì?

**Đáp án:** single point of failure và thiếu redundancy. Hướng cải thiện là nhiều instance qua nhiều AZ, health check và phân phối traffic.

### Câu 2

Một công ty muốn dữ liệu vẫn còn sau khi phần cứng lưu trữ hỏng, nhưng chấp nhận ứng dụng tạm thời không truy cập dữ liệu. Requirement nhấn mạnh khái niệm nào?

**Đáp án:** durability cao hơn availability.

### Câu 3

Một team nhỏ muốn hệ thống tự tăng giảm theo traffic và giảm patch server. Từ khóa nào quan trọng?

**Đáp án:** elasticity và least operational overhead; xem managed/serverless phù hợp workload.

### Câu 4

Ứng dụng backup hằng ngày nhưng business yêu cầu RPO 15 phút. Vấn đề là gì?

**Đáp án:** backup hiện tại không đạt RPO; cần tăng tần suất hoặc dùng replication/data protection phù hợp hơn.

### Câu 5

Ứng dụng có latency tốt nhưng chi phí cao vì instance luôn ở kích thước lớn nhất. Pillar nào cần review?

**Đáp án:** Cost Optimization và Performance Efficiency để right-size dựa trên metric.

### Câu 6

Đội vận hành triển khai thủ công, không có dashboard, runbook và owner khi incident. Pillar nào bị ảnh hưởng trực tiếp?

**Đáp án:** Operational Excellence; Reliability cũng bị ảnh hưởng vì khó phát hiện và phục hồi nhất quán.

---

## 14. Checklist hoàn thành tuần

### Kiến thức

- [ ] Giải thích Region, AZ và edge location.
- [ ] Phân biệt trách nhiệm AWS/khách hàng trong EC2, S3, RDS, Lambda, VPC.
- [ ] Phân biệt availability và durability bằng ví dụ.
- [ ] Phân biệt scalability và elasticity.
- [ ] Phân biệt vertical và horizontal scaling.
- [ ] Giải thích vì sao stateless dễ scale hơn.
- [ ] Giải thích RTO, RPO, SLA, SLI và SLO.
- [ ] Nêu được mục tiêu của từng pillar.
- [ ] Giải thích eventual consistency và workload phù hợp.

### Thực hành

- [ ] Đã cấu hình MFA, Budget và tagging convention.
- [ ] Đã vẽ single-AZ architecture.
- [ ] Đã chỉ ra ít nhất ba single point of failure.
- [ ] Đã vẽ phiên bản Multi-AZ.
- [ ] Đã review theo sáu pillar.
- [ ] Đã ghi trade-off về cost, complexity, availability và performance.

Bạn sẵn sàng sang Tuần 2 khi có thể nhìn một đề bài và viết requirement table trước khi nhắc đến IAM, VPC hay EC2.

---

## 15. Tài liệu tham khảo

- [AWS Well-Architected Framework](https://docs.aws.amazon.com/wellarchitected/latest/framework/) — nguyên tắc và câu hỏi review theo sáu pillar.
- [AWS Well-Architected Tool](https://aws.amazon.com/well-architected-tool/) — review workload và theo dõi improvement plan.
- [AWS Global Infrastructure](https://aws.amazon.com/about-aws/global-infrastructure/) — Region, Availability Zone và edge locations.
- [AWS Shared Responsibility Model](https://aws.amazon.com/compliance/shared-responsibility-model/) — ranh giới trách nhiệm AWS/khách hàng.
- [AWS Certified Solutions Architect – Associate Exam Guide](https://docs.aws.amazon.com/aws-certification/latest/solutions-architect-associate-03.html) — phạm vi SAA-C03.
- [AWS Pricing Calculator](https://calculator.aws/) — ước tính chi phí trước khi chạy lab.


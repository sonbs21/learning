
### Kiểm soát quyền truy cập

- **IAM**: quản lý user, role, policy và quyền truy cập.
- **IAM Identity Center**: quản lý đăng nhập tập trung cho nhiều tài khoản AWS.
- **Organizations / SCP**: giới hạn quyền ở cấp tổ chức hoặc account.

### Mã hóa và bí mật

- **KMS**: quản lý khóa mã hóa.
- **Secrets Manager**: lưu password, API key, token và tự động xoay vòng.
- **ACM**: quản lý chứng chỉ SSL/TLS cho HTTPS.
- **CloudHSM**: phần cứng chuyên dụng để quản lý khóa.

### Phát hiện và giám sát

- **CloudTrail**: ghi lại hành động/API call.
- **CloudWatch**: giám sát log, metric và cảnh báo.
- **GuardDuty**: phát hiện hành vi đáng ngờ, malware, credential compromise.
- **Security Hub**: tập trung các cảnh báo bảo mật.
- **AWS Config**: kiểm tra resource có tuân thủ rule hay không.

### Bảo vệ hạ tầng và network

- **Security Group**: firewall stateful ở cấp resource/ENI, chỉ có rule Allow, dùng để quy định traffic nào được phép đi vào hoặc đi ra khỏi EC2, ALB, RDS và các resource được hỗ trợ.
- **Network ACL (NACL)**: firewall stateless ở cấp subnet, có cả rule Allow và Deny, dùng làm lớp phòng thủ bổ sung cho toàn bộ resource trong subnet.
- **AWS WAF**: web application firewall ở Layer 7, kiểm tra HTTP/HTTPS request để chặn SQL injection, XSS, bot, IP xấu hoặc request vượt rate limit trước khi request tới ứng dụng.
- **AWS Shield**: chống DDoS, đặc biệt cho CloudFront và ALB.
- **AWS Network Firewall**: firewall nâng cao cho traffic trong VPC.
- **VPC Endpoint**: truy cập dịch vụ AWS private, không cần đi qua Internet.
- **Firewall Manager**: quản lý firewall policy cho nhiều account.
- **PrivateLink**: kết nối private đến service khác.

### Security Group, NACL và WAF khác nhau như thế nào?

Ba dịch vụ đều liên quan đến bảo vệ traffic nhưng hoạt động ở các lớp và phạm vi khác nhau:

```text
Internet
   │
   ▼
AWS WAF
Kiểm tra nội dung HTTP/HTTPS request
   │
   ▼
CloudFront / ALB / API Gateway
   │
   ▼
NACL của subnet
Kiểm tra IP, protocol và port ở biên subnet
   │
   ▼
Security Group của resource/ENI
Kiểm tra IP, port hoặc Security Group nguồn
   │
   ▼
EC2 / ECS task / database / application
```

| Đặc điểm | Security Group | NACL | AWS WAF |
|---|---|---|---|
| Phạm vi | Resource hoặc network interface | Subnet | Web application resource |
| Lớp bảo vệ | Network transport, chủ yếu Layer 3/4 | Network transport, Layer 3/4 | HTTP/HTTPS, Layer 7 |
| Stateful | Có | Không | Không áp dụng theo khái niệm kết nối SG/NACL |
| Rule | Chỉ Allow | Allow và Deny | Allow, Block, Count, CAPTCHA, Challenge |
| Cách đánh giá | Tổng hợp tất cả rule | Theo số thứ tự, khớp đầu tiên | Theo priority; terminating action dừng đánh giá |
| Có thể tham chiếu SG khác | Có | Không | Không |
| Hiểu path, header, query string | Không | Không | Có |
| Use case chính | Quyền kết nối tới từng tier/resource | Guardrail cho cả subnet, chặn CIDR cụ thể | Bảo vệ web/API khỏi tấn công ứng dụng và bot |

#### Security Group — firewall chính của resource

Security Group là virtual firewall gắn với network interface của resource. Có thể hiểu câu hỏi mà Security Group trả lời là:

> Resource này được phép nhận traffic từ đâu, qua protocol và port nào; đồng thời được phép gửi traffic đi đâu?

Ví dụ kiến trúc ba tầng:

```text
Internet
   │ HTTPS 443
   ▼
ALB Security Group
   │ HTTP 8080
   ▼
Application Security Group
   │ PostgreSQL 5432
   ▼
Database Security Group
```

Cấu hình nên dùng:

- ALB Security Group cho phép inbound TCP 443 từ Internet.
- Application Security Group chỉ cho inbound port 8080 từ **ALB Security Group**.
- Database Security Group chỉ cho inbound port 5432 từ **Application Security Group**.

Như vậy database không mở trực tiếp cho Internet hoặc toàn bộ VPC. Việc tham chiếu Security Group nguồn tốt hơn hard-code IP của EC2 vì instance có thể scale và thay IP.

Security Group có các đặc điểm quan trọng:

- **Stateful:** nếu inbound request được phép, response traffic của kết nối đó tự động được phép đi ngược lại, không cần tạo outbound rule đối xứng.
- **Chỉ có Allow:** không thể tạo Deny rule. Traffic không khớp Allow nào sẽ bị implicit deny.
- **Không xét thứ tự:** AWS đánh giá tổng hợp mọi rule, không có rule number hoặc “rule đầu tiên thắng”.
- Một resource có thể gắn nhiều Security Group; các Allow rule được cộng lại, không có Group nào dùng để phủ định Group khác.
- Có inbound và outbound rule riêng.
- Security Group không đọc URL path, HTTP header hoặc request body; đó là nhiệm vụ của WAF hoặc application.

Ví dụ inbound rule:

| Type | Protocol | Port | Source | Ý nghĩa |
|---|---|---:|---|---|
| HTTPS | TCP | 443 | `0.0.0.0/0` | mọi IPv4 address có thể kết nối HTTPS |
| HTTPS | TCP | 443 | `::/0` | mọi IPv6 address có thể kết nối HTTPS |
| Custom TCP | TCP | 8080 | `sg-alb` | chỉ resource dùng ALB SG có thể vào port 8080 |
| PostgreSQL | TCP | 5432 | `sg-app` | chỉ application tier có thể kết nối database |

`0.0.0.0/0` không đồng nghĩa resource chắc chắn truy cập được từ Internet. Resource còn cần public IP hoặc public endpoint, route tới Internet Gateway, NACL phù hợp và service đang lắng nghe đúng port.

Security Group phù hợp khi:

- chỉ cho ALB kết nối tới application server;
- chỉ cho application kết nối tới RDS;
- cho bastion/VPN CIDR kết nối SSH;
- giới hạn egress của workload tới một destination hoặc SG cụ thể;
- bảo vệ ENI của EC2, ECS task, Lambda trong VPC, RDS và nhiều dịch vụ hỗ trợ.

Các lỗi thường gặp:

- mở SSH/RDP từ `0.0.0.0/0`;
- nghĩ rằng Security Group có Deny rule;
- thêm một Security Group “chặt hơn” để bù cho Group đang mở rộng — các Allow được cộng lại nên cách này không hiệu quả;
- cho application dùng IP động của ALB thay vì tham chiếu ALB Security Group;
- quên outbound rule khi chủ động giới hạn egress;
- nhầm Security Group với user authentication hoặc IAM authorization.

#### Network ACL — lớp lọc ở biên subnet

Network Access Control List là firewall áp vào subnet. Câu hỏi NACL trả lời là:

> Traffic với source/destination CIDR, protocol và port này có được đi vào hoặc đi ra khỏi subnet không?

Đặc điểm quan trọng:

- **Stateless:** inbound và outbound được đánh giá độc lập. Cho phép request vào không tự cho phép response đi ra.
- Có cả **Allow** và **Deny** rule.
- Rule được đánh giá từ số nhỏ đến số lớn; rule khớp đầu tiên quyết định kết quả.
- Mỗi subnet chỉ liên kết với một NACL tại một thời điểm; một NACL có thể liên kết nhiều subnet.
- NACL áp dụng cho tất cả resource trong subnet liên kết.
- NACL dùng CIDR, protocol và port; không tham chiếu Security Group.
- Rule `*` cuối cùng deny traffic không khớp rule nào.

Ví dụ:

| Rule | Type | Port | Source | Action |
|---:|---|---:|---|---|
| 100 | HTTPS | 443 | `0.0.0.0/0` | ALLOW |
| 110 | Custom TCP | 1024–65535 | `0.0.0.0/0` | ALLOW |
| `*` | All traffic | All | `0.0.0.0/0` | DENY |

Rule 100 cho request HTTPS đi vào. Dải ephemeral port trong rule đối xứng có thể cần để response quay lại vì NACL stateless. Dải port chính xác phụ thuộc client, hệ điều hành, NAT hoặc loại kết nối; không nên học thuộc một dải rồi áp cho mọi tình huống.

Nên để khoảng cách giữa rule number, chẳng hạn 100, 200, 300, để có thể chèn rule mới. Nếu rule 100 Allow tất cả traffic thì rule 200 Deny một IP sẽ không bao giờ được xét cho traffic đã khớp rule 100.

Default NACL và custom NACL khác nhau:

- Default NACL ban đầu cho phép toàn bộ inbound và outbound traffic.
- Custom NACL mới ban đầu deny toàn bộ inbound và outbound traffic cho đến khi thêm Allow rule.

NACL phù hợp khi:

- cần deny một CIDR/IP range ở cấp subnet;
- áp baseline chung cho mọi resource trong một subnet;
- tạo lớp defense in depth nếu resource bị gắn sai Security Group;
- tách subnet có yêu cầu network control khác nhau.

NACL thường không nên là công cụ chính để micro-manage từng application instance. Rule stateless và việc quản lý ephemeral ports khiến nó khó vận hành hơn Security Group.

Các lỗi thường gặp:

- chỉ mở inbound mà quên outbound response;
- đặt Deny rule sau một Allow rule rộng hơn;
- nghĩ một subnet có thể gắn nhiều NACL cùng lúc;
- nghĩ NACL tham chiếu được Security Group;
- chặn nhầm DNS, health check hoặc ephemeral port;
- thay NACL nhưng quên rằng traffic còn phải vượt qua Security Group.

#### AWS WAF — firewall cho HTTP/HTTPS request

AWS WAF là web application firewall. Nó không quyết định một TCP connection chung có được tới subnet hay không; nó đọc thành phần của HTTP/HTTPS request để nhận diện request không mong muốn.

WAF có thể kiểm tra:

- source IP;
- URI path;
- query string;
- HTTP method;
- header và cookie;
- một phần request body theo inspection limit;
- chuỗi hoặc regular expression;
- dấu hiệu SQL injection và cross-site scripting;
- tốc độ request theo tiêu chí của rate-based rule;
- bot và tín hiệu từ managed rule group.

WAF bảo vệ các resource được hỗ trợ như CloudFront distribution, Application Load Balancer, API Gateway REST API, AppSync GraphQL API, Cognito user pool, App Runner và một số dịch vụ web khác.

Thành phần chính:

```text
Web ACL
├── Rule priority 10: AWS Managed Common Rules
├── Rule priority 20: SQL injection protection
├── Rule priority 30: Rate limit login endpoint
├── Rule priority 40: Block known bad IP set
└── Default action: Allow
```

Mỗi Web ACL có default action cho request không khớp rule. Rule được đánh giá theo priority, bắt đầu từ giá trị nhỏ hơn.

Các action phổ biến:

- **Allow:** cho request đi tiếp và dừng đánh giá Web ACL.
- **Block:** chặn request và dừng đánh giá.
- **Count:** chỉ ghi nhận match rồi tiếp tục đánh giá; hữu ích để thử rule trước khi block.
- **CAPTCHA:** yêu cầu người dùng giải CAPTCHA khi token chưa hợp lệ.
- **Challenge:** thực hiện browser challenge, thường ít gây gián đoạn hơn CAPTCHA.

Ví dụ use case:

- chặn request chứa pattern SQL injection;
- chặn quốc gia hoặc IP reputation list theo requirement;
- rate-limit `/login` để giảm brute force;
- chỉ cho một số HTTP method;
- dùng managed rule group bảo vệ trước common web exploits;
- đặt rule ở CloudFront để chặn sớm tại edge trước khi request tới origin.

Nên đưa rule mới về `Count` và quan sát sampled requests/log trước khi chuyển sang `Block`, đặc biệt với managed rule group. Rule quá rộng có thể chặn người dùng hợp lệ.

WAF không thay thế:

- Security Group hoặc NACL cho network access;
- authentication và authorization;
- input validation trong application;
- Shield cho DDoS network/transport layer;
- Network Firewall cho traffic VPC ngoài phạm vi HTTP web application;
- API Gateway throttling, usage plan hoặc business quota.

#### Chọn dịch vụ nào trong đề thi?

| Yêu cầu | Lựa chọn thường phù hợp |
|---|---|
| Chỉ ALB được gọi EC2 port 8080 | Security Group tham chiếu Security Group |
| Chỉ application tier được gọi RDS port 5432 | Security Group |
| Deny một CIDR cho toàn subnet | NACL |
| Cần cả Allow và Deny rule ở subnet | NACL |
| Chặn SQL injection hoặc XSS | AWS WAF |
| Rate-limit request tới `/login` | AWS WAF rate-based rule |
| Chống DDoS | AWS Shield, kết hợp CloudFront/WAF tùy lớp tấn công |
| Lọc domain, IPS hoặc traffic giữa các VPC/subnet | AWS Network Firewall |
| Điều tra traffic ACCEPT/REJECT | VPC Flow Logs |

Ba lớp có thể được dùng cùng nhau:

```text
WAF             → request HTTP này có độc hại không?
NACL            → subnet có cho IP/protocol/port này đi qua không?
Security Group  → resource có cho nguồn này kết nối không?
Application     → user có đăng nhập và được phép thao tác không?
```

Cho phép ở WAF không thể vượt qua NACL hoặc Security Group đang chặn. Ngược lại, mở Security Group port 443 không làm WAF bỏ qua rule Block. Request phải vượt qua tất cả lớp áp dụng trên đường đi.

### Bảo vệ dữ liệu và workload

- **Macie**: phát hiện dữ liệu nhạy cảm trong S3.
- **Inspector**: quét lỗ hổng EC2, container và Lambda.
- **Backup**: sao lưu và khôi phục dữ liệu.
- **S3 Block Public Access**: ngăn S3 bucket/object bị public ngoài ý muốn.
- **Amazon Detective**: điều tra nguyên nhân sau khi phát hiện sự cố.

Có thể nhớ theo các lớp:

```text
IAM              → Ai được làm gì?
Security Group   → Resource nào được kết nối?
NACL             → Subnet nào được kết nối?
WAF              → HTTP request nào được phép?
Network Firewall → Network traffic nào được phép?
KMS              → Dữ liệu được mã hóa bằng khóa nào?
Secrets Manager  → Password/token được lưu ở đâu?
CloudTrail       → Ai đã làm gì?
GuardDuty        → Có hành vi đáng ngờ không?
Shield           → Có bị DDoS không?
Inspector        → Có lỗ hổng không?
Macie            → Có dữ liệu nhạy cảm không?
```

Nếu mới học, nên ưu tiên theo thứ tự:

```text
IAM → Security Group → NACL → KMS
→ Secrets Manager → WAF → CloudTrail
→ GuardDuty → Shield
```

### Tài liệu AWS chính thức

- [Security groups và network ACL trong Amazon VPC](https://docs.aws.amazon.com/vpc/latest/userguide/vpc-security-groups.html)
- [Kiểm soát traffic subnet bằng network ACL](https://docs.aws.amazon.com/vpc/latest/userguide/vpc-network-acls.html)
- [AWS WAF Developer Guide](https://docs.aws.amazon.com/waf/latest/developerguide/waf-chapter.html)

# VPC chuyên sâu — Amazon Virtual Private Cloud

> Tài liệu mở rộng từ Tuần 3 của roadmap AWS SAA-C03. Mục tiêu là hiểu VPC theo đường đi thực tế của packet: từ CIDR, subnet, route table, gateway, endpoint đến Security Group, NACL và DNS; sau đó có thể thiết kế, debug và giải thích một kiến trúc network Multi-AZ.

## 1. VPC là gì?

Amazon VPC là một mạng logic cô lập trong AWS Region. VPC cung cấp không gian địa chỉ IP và các thành phần network để triển khai EC2, RDS, Load Balancer, Lambda, container, endpoint và nhiều dịch vụ khác.

~~~text
VPC
├── CIDR block
├── Availability Zone
│   ├── Public subnet
│   ├── Private application subnet
│   └── Private data subnet
├── Route table
├── Internet Gateway
├── NAT Gateway
├── VPC Endpoint
├── Security Group
├── Network ACL
└── DNS/Resolver
~~~

VPC không mặc định có Internet và cũng không mặc định an toàn. Bạn phải thiết kế riêng:

- Không gian địa chỉ.
- Subnet và Availability Zone.
- Route table.
- Gateway hoặc endpoint.
- Firewall.
- DNS.
- Kết nối đến on-premises hoặc VPC khác.
- Observability và quy trình troubleshooting.

Khi một workload không kết nối được, hãy đi theo thứ tự:

1. Source và destination là gì?
2. DNS có phân giải đúng địa chỉ không?
3. Source nằm ở subnet nào?
4. Subnet gắn route table nào?
5. Route table có route tới destination không?
6. Traffic cần đi qua IGW, NAT, endpoint, peering hay Transit Gateway?
7. Gateway hoặc endpoint đã attach và hoạt động chưa?
8. Security Group ở source và destination cho phép chưa?
9. NACL cho phép cả chiều đi và chiều về chưa?
10. Resource có listen đúng port không?
11. TLS, application protocol hoặc credential có lỗi không?

Network path cần đúng trước; firewall chỉ có thể cho phép hoặc chặn một path đã tồn tại.

---

# Phần I — CIDR, subnet và Availability Zone

## Subnet là gì?

Subnet là một phân vùng mạng bên trong VPC. Nó lấy một phần địa chỉ IP từ VPC CIDR và dùng phần địa chỉ đó cho các resource trong cùng một Availability Zone.

Có thể hình dung:

~~~text
VPC: 10.0.0.0/16
│
├── Public subnet: 10.0.0.0/20
├── Private app subnet: 10.0.32.0/20
└── Private data subnet: 10.0.64.0/20
~~~

VPC là không gian mạng lớn; subnet là các khu vực nhỏ hơn để phân tách workload và áp dụng routing/security khác nhau.

Một subnet có các đặc điểm quan trọng:

- Luôn nằm trong một VPC.
- Chỉ thuộc một Availability Zone.
- Có một CIDR block nằm trong CIDR của VPC.
- Được associate với một route table.
- Chịu ảnh hưởng của Network ACL gắn với subnet.
- Chứa các resource như EC2, ENI, RDS subnet group hoặc interface endpoint tùy dịch vụ.

Subnet không tự quyết định resource có public hay private chỉ dựa vào tên. Route table mới là yếu tố quan trọng:

- Public subnet thường có route mặc định tới Internet Gateway.
- Private subnet thường có route mặc định tới NAT Gateway hoặc không có Internet route.
- Isolated subnet thường chỉ có local route trong VPC.

Ví dụ, hai subnet sau đều nằm trong cùng VPC nhưng có vai trò khác nhau:

~~~text
Public subnet
  CIDR: 10.0.0.0/20
  Route: 0.0.0.0/0 → Internet Gateway
  Có thể chứa: Internet-facing ALB, NAT Gateway

Private app subnet
  CIDR: 10.0.32.0/20
  Route: 0.0.0.0/0 → NAT Gateway
  Có thể chứa: EC2, ECS task, application worker
~~~

Subnet cũng là boundary để tổ chức hệ thống theo tầng:

~~~text
Public subnet
  → thành phần nhận traffic từ Internet

Private application subnet
  → application và worker không cần inbound Internet trực tiếp

Private data subnet
  → database và data service chỉ nhận traffic từ application tier
~~~

Subnet không phải là firewall. Subnet cung cấp network placement và route association; Security Group bảo vệ resource, còn Network ACL bảo vệ ở subnet level.

## 2. CIDR là gì?

CIDR mô tả network address và prefix length.

~~~text
10.0.0.0/16
192.168.10.0/24
172.16.0.0/20
~~~

Prefix length cho biết có bao nhiêu bit dành cho phần network. Phần còn lại dành cho host address.

Công thức tổng quát cho IPv4:

~~~text
Tổng số địa chỉ = 2^(32 - prefix length)
~~~

Ví dụ:

| CIDR | Tổng số địa chỉ |
|---|---:|
| /16 | 65.536 |
| /20 | 4.096 |
| /24 | 256 |
| /26 | 64 |
| /28 | 16 |
| /30 | 4 |

Trong một subnet IPv4 của AWS, AWS giữ lại 5 địa chỉ:

- Địa chỉ đầu tiên: network address.
- Địa chỉ thứ hai: VPC router.
- Địa chỉ thứ ba: DNS server.
- Địa chỉ thứ tư: reserved cho future use.
- Địa chỉ cuối: broadcast address, dù AWS không hỗ trợ broadcast theo cách truyền thống.

Công thức gần đúng:

~~~text
Usable IPv4 = 2^(32 - prefix) - 5
~~~

Ví dụ subnet /24 thường có 251 địa chỉ usable trong AWS.

## 3. Chọn VPC CIDR

Một VPC mới thường dùng private CIDR như:

- 10.0.0.0/16.
- 172.16.0.0/16.
- 192.168.0.0/16.

Không nên chọn CIDR chỉ dựa trên số instance hiện tại. Hãy dự trù:

- Số Availability Zone.
- Số subnet theo tier.
- Interface endpoint ENI.
- Lambda ENI.
- Load Balancer ENI.
- Auto Scaling growth.
- Blue/green deployment.
- Shared services.
- Kết nối đến on-premises.
- Kết nối VPC khác.
- Mở rộng trong tương lai.

Nếu VPC sẽ kết nối VPN hoặc Direct Connect với on-premises, CIDR VPC không được overlap với mạng on-premises. Nếu nhiều VPC cần kết nối qua Transit Gateway, nên chuẩn hóa address plan ngay từ đầu.

## 4. Ví dụ chia subnet

VPC:

~~~text
10.0.0.0/16
~~~

Thiết kế theo tier:

| Subnet | CIDR | AZ | Mục đích |
|---|---|---|---|
| Public-A | 10.0.0.0/20 | AZ-a | ALB, NAT Gateway |
| Public-B | 10.0.16.0/20 | AZ-b | ALB, NAT Gateway |
| App-A | 10.0.32.0/20 | AZ-a | EC2, ECS, Lambda |
| App-B | 10.0.48.0/20 | AZ-b | EC2, ECS, Lambda |
| Data-A | 10.0.64.0/20 | AZ-a | RDS, internal data |
| Data-B | 10.0.80.0/20 | AZ-b | RDS, internal data |

Ví dụ chia theo môi trường:

~~~text
Production VPC: 10.0.0.0/16
Staging VPC:    10.1.0.0/16
Development:    10.2.0.0/16
Shared service: 10.3.0.0/16
~~~

Address plan nhất quán giúp:

- Dễ định tuyến.
- Dễ audit.
- Dễ kết nối hybrid.
- Tránh overlap.
- Dễ xác định resource thuộc môi trường nào.

## 5. Subnet chỉ thuộc một Availability Zone

Một subnet không thể trải dài nhiều AZ.

~~~text
VPC
├── Public-A  ở AZ-a
├── Public-B  ở AZ-b
├── App-A     ở AZ-a
├── App-B     ở AZ-b
├── Data-A    ở AZ-a
└── Data-B    ở AZ-b
~~~

Cách nói đúng là “các subnet tương ứng được tạo ở nhiều AZ”, không phải “một subnet Multi-AZ”.

Multi-AZ không chỉ là tạo subnet ở hai AZ. Các resource chạy trong đó cũng phải được phân bố:

- ALB có node ở nhiều AZ.
- Auto Scaling Group dùng subnet ở nhiều AZ.
- EC2 target ở nhiều AZ.
- Database có cơ chế failover.
- State không bị giữ ở một instance duy nhất.
- Route và dependency không tập trung vào một AZ.

## 6. Public, private và isolated subnet

Subnet không có thuộc tính public chỉ vì tên của nó. Một subnet thường được gọi là public khi route table có route tới Internet Gateway.

### Public subnet

~~~text
VPC CIDR    → local
0.0.0.0/0   → Internet Gateway
~~~

Thường chứa:

- Internet-facing ALB.
- NAT Gateway.
- Bastion host trong kiến trúc cũ.
- Public-facing appliance.
- Resource cần inbound Internet trực tiếp.

### Private subnet

~~~text
VPC CIDR    → local
0.0.0.0/0   → NAT Gateway
S3 prefix   → Gateway Endpoint
~~~

Thường chứa:

- Application server.
- Internal load balancer.
- Worker.
- ECS task.
- Lambda function cần VPC.
- Resource cần outbound nhưng không cần inbound Internet trực tiếp.

### Isolated subnet

~~~text
VPC CIDR    → local
~~~

Thường chứa:

- Database.
- Internal data store.
- Resource không cần Internet outbound.
- Workload cần giới hạn network path nghiêm ngặt.

Isolated subnet vẫn có thể truy cập resource trong VPC qua local route. Isolated chỉ có nghĩa không có default route ra ngoài.

## 7. CIDR và subnet phải hợp lệ

Các subnet trong cùng VPC:

- Phải nằm trong VPC CIDR.
- Không được overlap.
- Không được có cùng CIDR.
- Nên đủ lớn cho resource và endpoint.
- Nên nhất quán giữa các AZ.

Ví dụ không hợp lệ:

~~~text
VPC:       10.0.0.0/16
Subnet-A:  10.0.0.0/20
Subnet-B:  10.0.8.0/21
~~~

Subnet-B overlap với Subnet-A.

Ví dụ hợp lệ:

~~~text
VPC:       10.0.0.0/16
Subnet-A:  10.0.0.0/20
Subnet-B:  10.0.16.0/20
~~~

---

# Phần II — Route table và đường đi của packet

## 8. Route table làm gì?

Route table quyết định next hop của traffic rời subnet. Một route có:

- Destination CIDR.
- Target hoặc next hop.

Ví dụ:

~~~text
Destination       Target
10.0.0.0/16       local
0.0.0.0/0         igw-xxxxxxxx
10.20.0.0/16      pcx-xxxxxxxx
~~~

Route table không phải firewall. Nó trả lời:

> Traffic đến địa chỉ này sẽ đi qua đâu?

Security Group và NACL trả lời:

> Traffic đó có được phép đi qua không?

## 9. Route local

Mỗi VPC có local route cho VPC CIDR. Route này cho phép subnet trong cùng VPC giao tiếp nội bộ theo address range.

~~~text
VPC CIDR: 10.0.0.0/16
Local route: 10.0.0.0/16 → local
~~~

Một EC2 ở 10.0.32.10 có thể đi tới database ở 10.0.64.20 qua local route, miễn là Security Group và NACL cho phép.

Không cần tạo route riêng cho từng subnet trong cùng VPC nếu traffic đã nằm trong VPC CIDR.

## 10. Default route

Default route thường là:

~~~text
0.0.0.0/0
~~~

Nó match mọi IPv4 destination chưa có route cụ thể hơn.

Các default route thường gặp:

~~~text
Public subnet:
0.0.0.0/0 → Internet Gateway

Private subnet:
0.0.0.0/0 → NAT Gateway

Transit subnet:
0.0.0.0/0 → Transit Gateway
~~~

Không nên thêm default route vào database subnet nếu database không cần outbound. Đường đi dư thừa làm tăng blast radius và có thể tạo chi phí.

## 11. Longest-prefix match

Khi có nhiều route match destination, route có prefix dài hơn được ưu tiên.

~~~text
10.0.0.0/16  → local
10.0.5.0/24  → Endpoint
0.0.0.0/0    → NAT Gateway
~~~

Traffic tới 10.0.5.20 match cả ba route nhưng đi đến 10.0.5.0/24 vì đây là route cụ thể nhất.

Route đúng vẫn có thể bị SG/NACL chặn. Không có route phù hợp thì traffic bị drop.

## 12. Main route table và explicit association

Mỗi VPC có main route table. Subnet không gắn explicit route table sẽ dùng main route table.

Best practice:

- Không phụ thuộc quá nhiều vào main route table trong production.
- Associate explicit route table cho từng nhóm subnet.
- Đặt tên route table theo vai trò.
- Review route propagation và route change.
- Tránh vô tình thêm default route cho database subnet.

Ví dụ:

~~~text
rt-public
  ├── Public-A
  └── Public-B

rt-private-app-a
  └── App-A

rt-private-app-b
  └── App-B

rt-isolated-data
  ├── Data-A
  └── Data-B
~~~

Private app subnet ở mỗi AZ có thể dùng route table riêng để trỏ NAT Gateway cùng AZ.

## 13. Route table theo tầng

### Public route table

~~~text
10.0.0.0/16  → local
0.0.0.0/0    → Internet Gateway
~~~

### Private application route table

~~~text
10.0.0.0/16  → local
0.0.0.0/0    → NAT Gateway
S3 prefix     → S3 Gateway Endpoint
~~~

### Isolated data route table

~~~text
10.0.0.0/16  → local
~~~

Nếu data subnet cần truy cập dịch vụ AWS private, có thể thêm endpoint phù hợp. Không thêm Internet route chỉ để “cho dễ quản lý”.

---

# Phần III — Internet Gateway, public IPv4 và IPv6

## 14. Internet Gateway

Internet Gateway là gateway managed, redundant và horizontally scaled để VPC giao tiếp với Internet.

Để public resource IPv4 nhận traffic từ Internet, cần đồng thời:

1. VPC attach Internet Gateway.
2. Subnet route table có default route tới IGW.
3. Resource có public IPv4 hoặc Elastic IP phù hợp.
4. Security Group cho phép port cần thiết.
5. NACL cho phép inbound và outbound phù hợp.
6. Service/application listen đúng port.
7. DNS trỏ đúng địa chỉ nếu truy cập bằng hostname.

Chỉ có public IP không đủ nếu subnet không có route tới IGW.

Chỉ có route tới IGW không đủ nếu resource không có public address.

Chỉ có route và public IP không đủ nếu SG hoặc NACL chặn.

## 15. Public IPv4 và Elastic IP

Public IPv4 là địa chỉ Internet-routable được AWS cung cấp theo cơ chế của resource. Elastic IP là public IPv4 tĩnh mà bạn quản lý và có thể remap.

Dùng Elastic IP khi:

- Cần địa chỉ cố định.
- Đối tác allowlist IP.
- NAT Gateway cần public address.
- Network appliance cần địa chỉ tĩnh.

Không nên gắn Elastic IP cho mọi EC2. Đối với web application, nên dùng:

- ALB.
- Route 53.
- CloudFront.
- Auto Scaling.

Địa chỉ gắn trực tiếp vào instance làm giảm tính linh hoạt khi instance bị thay thế.

## 16. IPv6 và egress-only Internet Gateway

IPv6 trong VPC khác IPv4:

- Không dùng NAT Gateway cho IPv6.
- Instance có global unicast IPv6 address.
- Route mặc định thường là tuyến IPv6 tới Internet.
- Security Group và NACL cần rule IPv6 riêng.
- Egress-only Internet Gateway hỗ trợ outbound IPv6 và ngăn inbound connection khởi tạo từ Internet.

Nếu đề yêu cầu private workload outbound IPv6 nhưng không nhận inbound connection mới, hãy nghĩ đến egress-only Internet Gateway.

---

# Phần IV — NAT Gateway và outbound access

## 17. NAT Gateway là gì?

NAT Gateway cho phép resource trong private subnet khởi tạo connection ra ngoài, thường là Internet, nhưng không cho Internet khởi tạo connection mới vào private resource.

Đường đi:

~~~text
Private EC2
  ↓
Private route table
  ↓
NAT Gateway trong public subnet
  ↓
Public route table
  ↓
Internet Gateway
  ↓
Internet
~~~

NAT Gateway phải nằm trong public subnet. Public subnet của NAT cần route tới Internet Gateway. Private subnet cần route tới NAT.

Đường đi sai:

~~~text
Private EC2 → Internet Gateway
~~~

Private resource không thể dùng IGW như NAT để ẩn địa chỉ private và tạo outbound IPv4 Internet access.

## 18. NAT Gateway không phải firewall

NAT Gateway:

- Dịch địa chỉ private sang public.
- Hỗ trợ outbound connection.
- Không thay thế Security Group.
- Không thay thế NACL.
- Không lọc application-level request như WAF.
- Không tự giới hạn destination theo business policy.
- Không cho phép Internet khởi tạo inbound flow tới private instance.

Nếu cần egress filtering chi tiết, có thể cần:

- AWS Network Firewall.
- Proxy.
- Egress gateway pattern.
- Route control.
- Security Group/NACL.
- DNS filtering.

## 19. NAT Gateway theo AZ

### Mẫu một NAT dùng chung

~~~text
App-A ─┐
       ├── NAT-A
App-B ─┘
~~~

Ưu điểm:

- Ít NAT Gateway.
- Ít chi phí hourly.

Nhược điểm:

- App-B có thể đi cross-AZ tới NAT-A.
- Tạo dependency vào AZ-a.
- Có thể phát sinh cross-AZ data transfer.
- Khi AZ-a lỗi, outbound từ App-B có thể bị ảnh hưởng.

### Mẫu NAT mỗi AZ

~~~text
App-A → NAT-A
App-B → NAT-B
~~~

Ưu điểm:

- Giảm cross-AZ traffic.
- Tốt hơn cho HA.
- Failure domain rõ hơn.

Nhược điểm:

- Nhiều phí hourly.
- Nhiều route và resource cần quản lý.
- Phải monitor nhiều NAT hơn.

Quyết định phụ thuộc vào:

- Lưu lượng outbound.
- Chi phí data processing.
- Chi phí cross-AZ.
- Availability requirement.
- Số AZ.
- Mức độ đơn giản mong muốn.

## 20. NAT Gateway cost

NAT Gateway thường có hai nhóm chi phí:

- Chi phí tồn tại theo thời gian.
- Chi phí dữ liệu được xử lý.

Nếu application gửi nhiều dữ liệu qua NAT tới S3 hoặc dịch vụ AWS, chi phí có thể tăng đáng kể. Khi private workload truy cập S3/DynamoDB, hãy kiểm tra Gateway Endpoint trước khi mặc định dùng NAT.

Các biện pháp giảm cost:

- Dùng Gateway Endpoint cho S3/DynamoDB.
- Dùng Interface Endpoint khi PrivateLink phù hợp.
- Route NAT cùng AZ với private workload khi lưu lượng lớn.
- Giảm download/upload không cần thiết.
- Cache package hoặc artifact.
- Dùng CloudFront cho content phù hợp.
- Theo dõi NAT bytes processed.

## 21. NAT Gateway và NAT instance

| Tiêu chí | NAT Gateway | NAT instance |
|---|---|---|
| Managed | Có | Không |
| Patching | AWS quản lý | Người dùng quản lý |
| Scaling | Managed theo thiết kế service | Phải tự scale |
| HA | Thiết kế nhiều AZ | Phải tự thiết kế |
| Source/destination check | Không cần tắt | Thường cần tắt |
| Operational overhead | Thấp hơn | Cao hơn |
| Phù hợp least operational overhead | Thường có | Thường không |

NAT instance có thể được chọn trong trường hợp đặc biệt như muốn tự kiểm soát appliance hoặc traffic nhỏ, nhưng cần chấp nhận vận hành EC2, failover, route và capacity.

---

# Phần V — Security Group và NACL

## 22. Security Group

Security Group là virtual firewall stateful gắn với ENI hoặc resource hỗ trợ.

Đặc điểm:

- Chỉ có allow rule.
- Không có explicit deny.
- Stateful: response traffic được cho phép tự động.
- Có thể gắn nhiều SG vào một ENI.
- Có thể reference SG khác trong rule.
- Rule áp dụng tổng hợp; không có thứ tự theo số như NACL.

Ví dụ three-tier:

~~~text
ALB-SG
  Inbound:
  - TCP 443 từ Internet

APP-SG
  Inbound:
  - TCP 8080 từ ALB-SG

DB-SG
  Inbound:
  - TCP 5432 từ APP-SG
~~~

Khi app scale, SG reference vẫn hoạt động mà không phải cập nhật IP từng instance.

### Security Group không có deny

Nếu có rule allow TCP 443 từ một CIDR rộng, bạn không thể thêm deny TCP 443 từ một CIDR nhỏ hơn trong cùng SG để override. Muốn deny cụ thể:

- Thiết kế lại SG.
- Dùng NACL.
- Dùng WAF cho HTTP/HTTPS.
- Dùng firewall/proxy phù hợp.
- Giới hạn source ngay từ allow rule.

### Stateful nghĩa là gì?

Nếu client gửi outbound TCP tới server và SG outbound cho phép, response traffic thường được phép quay lại mà không cần tạo inbound rule đối ứng trên cùng SG.

Tuy nhiên, stateful không có nghĩa mọi network layer đều tự động cho phép. NACL vẫn stateless và route vẫn phải đúng.

## 23. Network ACL

NACL là firewall stateless áp dụng ở subnet level.

Đặc điểm:

- Có allow và deny rule.
- Rule có số thứ tự.
- Rule nhỏ hơn được đánh giá trước.
- Rule đầu tiên match quyết định.
- Cần cấu hình cả inbound và outbound.
- Một subnet có một NACL tại một thời điểm.
- Một NACL có thể gắn cho nhiều subnet.

### Vì sao NACL hay gây lỗi response?

Giả sử client gọi server TCP 443:

~~~text
Client ephemeral port 50000
  → Server port 443
Server port 443
  → Client ephemeral port 50000
~~~

Nếu NACL chỉ allow inbound TCP 443 nhưng không allow outbound traffic về ephemeral port của client, connection có thể fail hoặc response bị drop.

NACL cần được thiết kế theo cả flow:

- Inbound destination port.
- Outbound source/return port.
- IPv4 và IPv6 nếu dùng cả hai.
- DNS port 53 nếu DNS đi qua network ACL.
- Ephemeral port range phù hợp hệ điều hành/network design.

## 24. So sánh Security Group và NACL

| Đặc điểm | Security Group | NACL |
|---|---|---|
| Phạm vi | ENI/resource | Subnet |
| Stateful | Có | Không |
| Rule | Allow | Allow và deny |
| Rule order | Không theo số | Số nhỏ trước |
| SG reference | Có | Không |
| Response traffic | Tự động theo state | Phải cho phép |
| Use case | Workload access control | Subnet boundary/deny |

Quy tắc nhớ:

> SG kiểm soát resource-to-resource; NACL kiểm soát subnet-to-subnet hoặc subnet-level boundary.

## 25. Thiết kế SG theo tầng

Không nên dùng một SG rất rộng cho toàn bộ VPC. Tách theo vai trò:

~~~text
alb-public-sg
app-private-sg
worker-private-sg
db-private-sg
cache-private-sg
endpoint-sg
admin-sg
~~~

Mỗi SG nên:

- Có tên và mô tả rõ.
- Chỉ mở port cần thiết.
- Reference SG thay vì IP khi có thể.
- Có outbound rule được xem xét.
- Không dùng SG của database cho mọi workload.
- Không cho phép SSH từ Internet khi có SSM Session Manager hoặc VPN.

---

# Phần VI — DNS trong VPC

## 26. VPC DNS attributes

VPC có hai thuộc tính DNS quan trọng:

- enableDnsSupport.
- enableDnsHostnames.

enableDnsSupport cho phép VPC DNS resolution hoạt động. enableDnsHostnames cho phép resource nhận hostname DNS trong những điều kiện phù hợp, đặc biệt quan trọng với public DNS hostname và một số service private DNS.

Nếu DNS trong VPC lỗi:

- Kiểm tra hai thuộc tính.
- Kiểm tra DHCP option set.
- Kiểm tra resolver config trong OS.
- Kiểm tra route/NACL tới DNS nếu dùng resolver riêng.
- Kiểm tra private DNS cho Interface Endpoint.
- Kiểm tra hosted zone association.

## 27. AmazonProvidedDNS

AmazonProvidedDNS thường có địa chỉ:

~~~text
VPC network address + 2
~~~

Ví dụ:

~~~text
VPC: 10.0.0.0/16
Resolver: 10.0.0.2
~~~

Nếu dùng custom DNS:

- DHCP option set cần trỏ tới resolver.
- Resolver phải reachable.
- SG/NACL phải cho phép DNS.
- Cần xử lý forward cho domain AWS/private.
- Cần dự phòng khi DNS server lỗi.

## 28. DHCP option set

DHCP option set có thể cung cấp:

- Domain name.
- Domain-name-servers.
- NTP servers.
- NetBIOS name servers.
- NetBIOS node type.

Một VPC chỉ associate một DHCP option set tại một thời điểm. Thay đổi DHCP option set có thể cần restart hoặc renew DHCP lease trên instance để nhận giá trị mới.

## 29. Route 53 private hosted zone

Private hosted zone cho phép phân giải DNS nội bộ trong các VPC được associate.

~~~text
db.internal.example.com → private database endpoint
api.internal.example.com → internal ALB
~~~

Lợi ích:

- Không expose địa chỉ private ra Internet.
- Dùng tên ổn định thay vì IP.
- Hỗ trợ service discovery đơn giản.
- Có thể associate nhiều VPC nếu cấu hình đúng.

Khi debug private hosted zone:

- VPC đã associate chưa?
- DNS support/hostnames bật chưa?
- Record tồn tại đúng không?
- Có conflicting zone không?
- Client đang hỏi resolver nào?
- Security Group/NACL tới DNS có cho phép không?

## 30. Route 53 Resolver inbound/outbound

Inbound endpoint cho phép DNS resolver từ on-premises query domain trong AWS.

Outbound endpoint cho phép workload trong VPC query domain được quản lý bởi DNS server on-premises.

~~~text
On-prem DNS
   ↓
Inbound Resolver Endpoint
   ↓
Private Hosted Zone trong AWS

AWS workload
   ↓
Outbound Resolver Endpoint
   ↓
On-prem DNS
~~~

---

# Phần VII — VPC Endpoint và PrivateLink

## 31. Vì sao dùng VPC Endpoint?

VPC Endpoint cho phép private workload truy cập service mà không cần đi qua Internet hoặc NAT tùy loại endpoint.

Lợi ích:

- Giảm public exposure.
- Giảm NAT dependency.
- Có thể giảm chi phí data processing.
- Kiểm soát truy cập bằng IAM và endpoint policy.
- Giữ traffic trên mạng AWS/private path.
- Hỗ trợ kiến trúc private subnet.

Endpoint không tự cấp permission. Vẫn phải kiểm tra IAM policy, resource policy, endpoint policy, route, DNS, Security Group, NACL và service support.

## 32. Gateway Endpoint

Gateway Endpoint chủ yếu dùng cho:

- Amazon S3.
- DynamoDB.

Đặc điểm:

- Thêm route vào route table.
- Không tạo ENI trong subnet.
- Không gắn Security Group trực tiếp như Interface Endpoint.
- Có thể dùng endpoint policy.
- Private workload đi tới service qua route endpoint.
- Thường tránh được NAT path.

Đường đi:

~~~text
Private EC2
  ↓
Private route table
  ↓
S3 Gateway Endpoint
  ↓
S3
~~~

Khi đề nói “EC2 private truy cập S3 mà không qua NAT/Internet”, Gateway Endpoint thường là đáp án cần nghĩ đến đầu tiên.

## 33. Interface Endpoint

Interface Endpoint dùng AWS PrivateLink và tạo ENI private IP trong subnet.

Đặc điểm:

- Hỗ trợ nhiều AWS service.
- Hỗ trợ endpoint service do partner hoặc account khác cung cấp.
- Có Security Group gắn vào ENI.
- Có thể bật private DNS.
- Có phí theo giờ cho endpoint ở mỗi AZ và phí data processing.
- Nên triển khai nhiều AZ nếu yêu cầu HA.

Đường đi:

~~~text
Private workload
  ↓
Private DNS name
  ↓
Interface Endpoint ENI
  ↓
AWS service hoặc endpoint service
~~~

Các thành phần cần kiểm tra:

- Endpoint được tạo trong subnet đúng chưa?
- Endpoint SG cho phép traffic từ workload chưa?
- Private DNS có bật không?
- VPC DNS attributes có bật không?
- NACL có cho phép traffic đến ENI không?
- IAM/resource policy có cho phép không?
- Endpoint service có hỗ trợ Region hiện tại không?

## 34. So sánh Gateway và Interface Endpoint

| Tiêu chí | Gateway Endpoint | Interface Endpoint |
|---|---|---|
| Service điển hình | S3, DynamoDB | AWS service/PrivateLink |
| Cơ chế | Route table | ENI private IP |
| Security Group endpoint | Không theo kiểu ENI | Có |
| Private DNS | Ít trọng tâm | Rất quan trọng |
| Chi phí hourly | Thường không | Có theo AZ |
| Data processing | Theo pricing hiện hành | Có |
| HA | Route association | Nên có endpoint ở nhiều AZ |
| Endpoint policy | Có | Có tùy service |

## 35. Endpoint policy

Endpoint policy giới hạn request đi qua endpoint. Nó không thay thế IAM policy.

Một request cần vượt qua nhiều lớp:

- IAM role cho phép action.
- Endpoint policy cho phép request tương ứng.
- Resource policy không deny.
- Route tới endpoint tồn tại.
- Network security cho phép.
- Object/key policy phù hợp.

Nếu một lớp explicit deny, request có thể fail dù lớp khác allow.

---

# Phần VIII — Kết nối VPC với VPC khác và on-premises

## 36. VPC Peering

VPC Peering là kết nối private point-to-point giữa hai VPC.

Đặc điểm:

- Traffic không đi qua public Internet.
- Cần route table ở cả hai phía.
- CIDR không được overlap.
- Không có transitive routing.
- Phù hợp số lượng VPC nhỏ hoặc kết nối đơn giản.
- Khi số lượng VPC tăng, mesh trở nên khó quản lý.

~~~text
VPC-A ↔ VPC-B
VPC-A ↔ VPC-C
VPC-B ↔ VPC-C
~~~

Nếu VPC-A peering với VPC-B và VPC-B peering với VPC-C, VPC-A không tự động đi qua VPC-B để tới VPC-C.

Checklist peering:

1. Peering connection đã active chưa?
2. CIDR có overlap không?
3. Route table VPC-A có route tới CIDR VPC-B không?
4. Route table VPC-B có route về CIDR VPC-A không?
5. SG/NACL cho phép chưa?
6. DNS resolution qua peering có cần bật không?
7. Có route cụ thể hơn ghi đè route mong muốn không?

## 37. Transit Gateway

Transit Gateway là managed network transit hub dùng cho hub-and-spoke.

~~~text
VPC-A ─┐
VPC-B ─┼── Transit Gateway
VPC-C ─┤
VPN ───┘
~~~

Đặc điểm:

- Hỗ trợ transitive routing.
- Kết nối nhiều VPC và hybrid network.
- Có Transit Gateway route tables.
- Có attachment cho VPC, VPN, Direct Connect gateway hoặc loại hỗ trợ.
- Có chi phí và complexity riêng.
- CIDR overlap vẫn là vấn đề cần giải quyết.

Kết nối qua TGW có thể cần:

1. Route table trong subnet/VPC trỏ tới TGW.
2. Route table của Transit Gateway có route tới attachment đích.
3. Route propagation hoặc static route đúng.
4. Security Group/NACL cho phép.

Có route ở VPC nhưng thiếu route trong TGW vẫn không kết nối được.

## 38. VPN và Direct Connect ở mức SAA

### Site-to-Site VPN

Phù hợp:

- Kết nối nhanh với on-premises.
- Có mã hóa qua Internet.
- Setup linh hoạt.
- Backup cho Direct Connect.
- Chi phí thấp hơn trong một số use case.

Cần nhớ:

- Có customer gateway và virtual private gateway hoặc Transit Gateway.
- Cần route propagation hoặc static route tùy thiết kế.
- Internet path có thể biến động.
- Không phải lúc nào latency cũng ổn định như private circuit.

### Direct Connect

Phù hợp:

- Kết nối private, ổn định hơn tới AWS.
- Throughput lớn.
- Requirement hybrid dài hạn.
- Giảm phụ thuộc vào Internet.

Cần chú ý:

- Setup lâu hơn.
- Có chi phí.
- Cần redundancy.
- Direct Connect không tự mã hóa; có thể kết hợp VPN nếu requirement cần encryption.
- Có thể dùng Direct Connect Gateway và Transit Gateway cho nhiều VPC.

---

# Phần IX — Network load balancing và private service

## 39. Internet → ALB → private application

Mô hình phổ biến:

~~~text
User
  ↓
Route 53
  ↓
CloudFront/WAF tùy thiết kế
  ↓
Internet-facing ALB trong public subnets
  ↓
Target group
  ↓
EC2/ECS trong private app subnets
  ↓
RDS/ElastiCache trong private data subnets
~~~

Điều kiện network:

- ALB có subnet ở nhiều AZ.
- Public subnet có route tới IGW.
- ALB SG allow listener port.
- App SG allow application port từ ALB SG.
- Target health check path/port đúng.
- Target có route phù hợp.
- DB SG allow database port từ App SG.
- Database không cần public IP.

Backend không cần public IP để nhận traffic từ ALB. ALB giao tiếp với target qua private IP trong VPC.

## 40. Internal load balancer

Internal ALB/NLB có private IP và dùng cho:

- Service-to-service.
- Internal API.
- Private ingress.
- Shared services.
- Cross-account access qua PrivateLink tùy design.

Internal load balancer không nhận traffic Internet trực tiếp. Client phải có network path tới VPC/subnet chứa load balancer.

## 41. AWS PrivateLink và endpoint service

PrivateLink cho phép service provider expose service riêng qua Interface Endpoint mà không cần mở toàn bộ VPC hoặc thiết lập peering.

~~~text
Consumer VPC
  ↓
Interface Endpoint
  ↓
Endpoint Service
  ↓
Provider NLB
  ↓
Private service
~~~

Phù hợp:

- SaaS.
- Shared service giữa account.
- Expose service có kiểm soát.
- Tránh full network connectivity giữa hai VPC.

Không nhầm PrivateLink với VPC Peering:

- Peering kết nối network giữa VPC.
- PrivateLink expose một service cụ thể qua endpoint.

---

# Phần X — Network observability

## 42. VPC Flow Logs

VPC Flow Logs ghi metadata về traffic tới/từ VPC, subnet hoặc network interface.

Thông tin thường dùng:

- Source address.
- Destination address.
- Source port.
- Destination port.
- Protocol.
- Bytes/packets.
- Action ACCEPT hoặc REJECT.
- Interface.
- Timestamp.

Flow Logs hữu ích để biết traffic bị accept/reject ở network interface level, nhưng không phải packet capture đầy đủ.

Dùng Flow Logs để kiểm tra:

- SG/NACL có reject traffic không.
- Client có gửi traffic đến destination không.
- Port nào bị truy cập.
- Một workload có outbound bất thường không.
- NAT/interface endpoint path có traffic không.

## 43. Reachability Analyzer

Reachability Analyzer phân tích path giữa source và destination, giúp tìm nguyên nhân network path không reachable:

- Route table.
- SG.
- NACL.
- Gateway.
- Endpoint.
- TGW/peering.
- ENI.

Nó không gửi traffic thật như một connectivity test thông thường, nhưng hữu ích để xác định cấu hình chặn path.

## 44. VPC Traffic Mirroring

Traffic Mirroring sao chép network traffic từ ENI tới appliance hoặc monitoring target để phân tích sâu hơn.

Dùng khi:

- Cần packet-level inspection.
- Troubleshooting nâng cao.
- IDS/IPS.
- Forensics.
- Network monitoring.

Không phải lựa chọn mặc định cho mọi lỗi kết nối; Flow Logs và Reachability Analyzer thường đủ cho SAA-level troubleshooting.

## 45. Các loại log khác

| Công cụ/log | Trả lời câu hỏi |
|---|---|
| VPC Flow Logs | Network interface traffic được accept/reject thế nào? |
| ALB access logs | Request HTTP đi qua ALB ra sao? |
| CloudTrail | Ai thay đổi route/SG/NACL/gateway? |
| Route 53 query logs | DNS query nào được gửi? |
| CloudWatch metrics | Resource/network đang hoạt động thế nào? |
| Reachability Analyzer | Config path có reachable không? |

Đừng dùng CloudTrail để xem nội dung packet. CloudTrail ghi API activity, không phải network payload.

---

# Phần XI — Troubleshooting theo lớp

## 46. EC2 private không truy cập Internet

### Sơ đồ mong đợi

~~~text
Private EC2
  → Private Route Table
  → NAT Gateway
  → Public Route Table
  → Internet Gateway
  → Internet
~~~

### Checklist

1. EC2 nằm đúng private subnet chưa?
2. Private subnet association với route table nào?
3. Route table có default route tới NAT Gateway không?
4. NAT Gateway ở public subnet chưa?
5. NAT subnet có default route tới IGW không?
6. NAT Gateway available chưa?
7. NAT có Elastic IP theo yêu cầu chưa?
8. SG outbound cho phép chưa?
9. NACL outbound và inbound response cho phép chưa?
10. DNS hoạt động không?
11. OS route và proxy có đúng không?
12. Destination có chặn traffic không?

Các lỗi thường gặp:

- Private route table trỏ thẳng tới IGW.
- NAT Gateway đặt trong private subnet.
- NAT subnet không có route tới IGW.
- NACL chặn ephemeral response.
- DNS resolve fail nên tưởng Internet path fail.
- NAT Gateway bị xóa hoặc pending.
- Route table gắn nhầm subnet.

## 47. Internet không truy cập được ALB

1. ALB là internet-facing chưa?
2. ALB nằm trong public subnet ở ít nhất hai AZ chưa?
3. Public route table có route tới IGW không?
4. DNS trỏ đúng ALB chưa?
5. ALB SG allow listener port từ source chưa?
6. NACL public subnet allow cả chiều vào/về chưa?
7. Listener port/protocol đúng chưa?
8. Certificate/TLS có lỗi không?
9. WAF có block request không?
10. Target group có healthy target không?

Nếu ALB reachable nhưng trả 5xx:

- Kiểm tra target.
- App port.
- App SG.
- Health check.
- Dependency/database.
- Application log.

## 48. ALB target unhealthy

1. Target process có listen đúng port không?
2. Target có bind vào đúng interface không?
3. Health check protocol/path/port đúng không?
4. App SG có allow từ ALB SG không?
5. NACL có chặn không?
6. Route local trong VPC còn hoạt động không?
7. App trả status code được health check chấp nhận không?
8. Host header hoặc authentication có làm health check fail không?
9. Target có đủ capacity không?
10. Container task có đang restart không?

ALB health check không chỉ kiểm tra instance running; nó kiểm tra target có phản hồi đúng theo cấu hình health check.

## 49. EC2 không truy cập RDS

1. RDS endpoint resolve được không?
2. RDS đang ở VPC/subnet đúng không?
3. App SG outbound cho phép DB port không?
4. DB SG inbound cho phép source App SG không?
5. NACL hai chiều cho phép DB port và ephemeral port không?
6. Route table có local route hoặc route cần thiết không?
7. Database đang listen/available không?
8. Port engine đúng không?
9. TLS requirement có được đáp ứng không?
10. Connection limit hoặc authentication có gây lỗi không?

Không giải quyết lỗi RDS bằng cách mở DB ra Internet. Xác định đúng network path và security boundary.

## 50. Private EC2 không truy cập S3 qua endpoint

1. Endpoint là Gateway hay Interface?
2. Gateway endpoint đã associate với đúng private route table chưa?
3. Route cụ thể tới S3 có tồn tại không?
4. Endpoint policy có cho phép bucket/prefix không?
5. IAM role có S3 action cần thiết không?
6. Bucket policy có deny hoặc yêu cầu VPC endpoint cụ thể không?
7. Region và bucket ARN có đúng không?
8. DNS/endpoint name có bị override không?
9. NACL/SG có ảnh hưởng theo loại endpoint không?
10. Request thực sự có đi NAT thay vì endpoint không?

## 51. Interface Endpoint timeout

1. Interface endpoint có ENI trong subnet/AZ client không?
2. Endpoint SG inbound từ workload SG chưa?
3. VPC DNS support và hostnames bật chưa?
4. Private DNS của endpoint bật chưa?
5. Client resolve service hostname thành private IP chưa?
6. NACL cho phép traffic tới ENI chưa?
7. Route table có local route không?
8. Service có hỗ trợ interface endpoint ở Region không?
9. IAM và resource policy có allow không?
10. Endpoint ở trạng thái available chưa?

## 52. VPC Peering không hoạt động

1. Peering connection active chưa?
2. CIDR có overlap không?
3. VPC-A route tới VPC-B CIDR chưa?
4. VPC-B route về VPC-A CIDR chưa?
5. SG/NACL hai phía allow chưa?
6. DNS resolution qua peering có bật khi cần chưa?
7. Resource có listen port không?
8. Có đang cố đi qua một VPC trung gian không?
9. Route có bị route cụ thể hơn ghi đè không?

---

# Phần XII — Architecture pattern quan trọng

## 53. Three-tier VPC

~~~text
Internet
   ↓
Public ALB
   ↓
Private App subnets
   ↓
Private Data subnets
~~~

### Public tier

- Internet-facing ALB.
- Route tới IGW.
- SG chỉ mở listener.
- Không chứa database.

### Application tier

- EC2/ECS/Lambda/worker.
- Không public IP nếu không cần.
- Outbound qua NAT hoặc endpoint.
- SG nhận traffic từ ALB/worker cần thiết.

### Data tier

- RDS/Aurora/ElastiCache.
- Isolated hoặc private subnet.
- SG chỉ nhận từ app tier.
- Không default route Internet nếu không cần.

## 54. Private application có outbound nhưng không inbound

~~~text
Internet
   ↓
NAT Gateway ở public subnet
   ↑
Private application subnet
~~~

Use case:

- Install package.
- Gọi API bên thứ ba.
- Download dependency.
- Gọi service ngoài AWS.

Nếu workload chỉ gọi AWS service, endpoint có thể giảm public/NAT dependency.

## 55. Hub-and-spoke với Transit Gateway

~~~text
Production VPC ─┐
Staging VPC ────┼── Transit Gateway ── VPN/Direct Connect
Shared VPC ─────┘
~~~

Dùng route table TGW riêng nếu cần tách environment. Không cho tất cả VPC reach mọi VPC chỉ vì đã kết nối vào hub.

## 56. Centralized inspection

Một số tổ chức định tuyến traffic qua inspection VPC hoặc AWS Network Firewall:

~~~text
Spoke VPC
   ↓
Transit Gateway
   ↓
Inspection VPC/firewall
   ↓
Destination
~~~

Trade-off:

- Tăng visibility và control.
- Tăng latency, cost và complexity.
- Cần thiết kế route tránh asymmetric routing.
- Cần HA cho firewall/appliance.

---

# Phần XIII — Lab thực hành

## 57. Lab 1: Tạo VPC Multi-AZ

### Mục tiêu

Tạo address plan và network boundary cho hệ thống three-tier.

### Thiết kế

~~~text
VPC: 10.0.0.0/16

AZ-a:
  Public-A: 10.0.0.0/20
  App-A:    10.0.32.0/20
  Data-A:   10.0.64.0/20

AZ-b:
  Public-B: 10.0.16.0/20
  App-B:    10.0.48.0/20
  Data-B:   10.0.80.0/20
~~~

### Thực hiện

1. Tạo VPC.
2. Tạo sáu subnet.
3. Gắn tag Name, Tier và AZ.
4. Tạo Internet Gateway và attach vào VPC.
5. Tạo public route table.
6. Thêm default route tới IGW.
7. Associate Public-A và Public-B.
8. Tạo private app route tables.
9. Associate App-A/App-B.
10. Tạo isolated data route table.
11. Associate Data-A/Data-B.
12. Kiểm tra route association.
13. Vẽ lại sơ đồ bằng CIDR thật.

### Kết quả cần kiểm tra

- Public subnet có route Internet.
- App subnet chưa có Internet route nếu chưa tạo NAT.
- Data subnet chỉ có local route.
- Không subnet nào overlap.
- Mỗi subnet thuộc đúng một AZ.

## 58. Lab 2: ALB public, EC2 private

### Mục tiêu

Kiểm tra public ingress và private backend.

### Security Group

~~~text
ALB-SG:
  Inbound TCP 80/443 từ Internet tùy lab
  Outbound tới App-SG

App-SG:
  Inbound TCP 8080 từ ALB-SG
  Outbound theo requirement
~~~

### Thực hiện

1. Tạo ALB internet-facing ở Public-A/Public-B.
2. Tạo target group.
3. Tạo EC2 ở App-A/App-B.
4. Không gán public IP cho EC2.
5. Cài web server hoặc app listen TCP 8080.
6. Cho App-SG nhận traffic từ ALB-SG.
7. Cấu hình health check đúng port/path.
8. Truy cập ALB DNS.
9. Kiểm tra target health.
10. Thử truy cập trực tiếp private IP từ Internet và xác nhận không được.

### Bài học

- Backend không cần public IP.
- ALB public không làm toàn bộ backend public.
- SG reference giúp mô hình tự thích ứng khi target scale.

## 59. Lab 3: Private outbound qua NAT

### Mục tiêu

Cho private EC2 outbound Internet nhưng không expose inbound.

### Thực hiện

1. Tạo Elastic IP nếu flow yêu cầu.
2. Tạo NAT Gateway trong Public-A.
3. Đợi NAT available.
4. Sửa private route table của App-A để có default route tới NAT-A.
5. Đảm bảo public route table có default route tới IGW.
6. Kiểm tra SG outbound.
7. Kiểm tra NACL.
8. Test DNS và HTTPS outbound.
9. Lặp lại với App-B và cân nhắc NAT-B.
10. Ghi nhận cost.
11. Xóa NAT Gateway sau lab.

NAT Gateway có phí ngay cả khi traffic ít do hourly charge, ngoài phí data processing. Không để lab chạy qua đêm.

## 60. Lab 4: S3 Gateway Endpoint

### Mục tiêu

Cho private EC2 truy cập S3 không qua NAT.

### Thực hiện

1. Tạo S3 Gateway Endpoint.
2. Chọn đúng VPC.
3. Associate với private app route tables.
4. Thêm endpoint policy giới hạn bucket/prefix.
5. Gán IAM role chỉ đọc hoặc đọc/ghi đúng bucket.
6. Test S3 request.
7. Kiểm tra route table xuất hiện route endpoint.
8. So sánh khi bỏ endpoint và đi qua NAT.
9. Kiểm tra bucket policy không deny request.
10. Xóa resource tạm sau lab.

## 61. Lab 5: SG và NACL

### Mục tiêu

Thấy rõ stateful và stateless khác nhau.

| Test | Route | SG | NACL | Kết quả mong đợi |
|---|---|---|---|---|
| ALB → App | đúng | allow từ ALB-SG | allow | success |
| ALB → App | đúng | deny | allow | fail |
| ALB → App | đúng | allow | deny inbound | fail |
| App → DB | local | allow từ App-SG | allow hai chiều | success |
| App → DB | local | allow | thiếu response ephemeral | fail |
| Internet → private EC2 | thiếu public path | — | — | fail |

Sau mỗi thay đổi, ghi:

- Lớp nào chặn.
- Log/metric nào chứng minh.
- Cấu hình nào được restore.

## 62. Lab 6: Troubleshooting bằng Flow Logs

1. Bật Flow Logs cho subnet hoặc ENI test.
2. Gửi request vào port được phép.
3. Gửi request vào port bị chặn.
4. Tìm record ACCEPT/REJECT.
5. So sánh source/destination/port.
6. Thay đổi SG.
7. Thay đổi NACL.
8. Kiểm tra record mới.
9. Đặt retention log hợp lý.
10. Tắt hoặc xóa lab sau khi hoàn thành.

---

# Phần XIV — Bẫy AWS SAA-C03

## 63. Bẫy về subnet

- Subnet thuộc một AZ, không phải nhiều AZ.
- Public subnet cần route tới IGW.
- Public IP không tự tạo route.
- Private subnet có thể outbound Internet qua NAT.
- Isolated subnet vẫn giao tiếp nội bộ qua local route.
- Tên subnet không quyết định public/private; route table mới là yếu tố chính.

## 64. Bẫy về routing

- Route table không thay thế firewall.
- SG allow không sửa được route thiếu.
- Route cụ thể hơn thắng default route.
- Peering phải có route cả hai phía.
- TGW phải có route trong VPC và TGW route table.
- NAT subnet phải có route tới IGW.
- Private subnet không route trực tiếp tới IGW để outbound IPv4 qua NAT.

## 65. Bẫy về SG/NACL

- SG stateful, NACL stateless.
- SG chỉ có allow.
- NACL có allow/deny.
- NACL cần chiều đi và chiều về.
- SG reference tốt cho tầng ứng dụng.
- Mở database cho Internet thường là đáp án kém an toàn.
- NACL rule number có thứ tự.

## 66. Bẫy về endpoint

- Gateway Endpoint dành cho S3/DynamoDB.
- Interface Endpoint dùng ENI/PrivateLink.
- Interface Endpoint cần SG và private DNS.
- Endpoint không tự cấp IAM permission.
- Endpoint policy có thể giới hạn access.
- Endpoint không phải lúc nào cũng rẻ hơn NAT; Interface Endpoint có hourly/data cost.
- VPC Endpoint không tự làm mọi traffic private nếu ứng dụng vẫn dùng public hostname/path khác.

## 67. Bẫy về connectivity

- VPC peering không transitive.
- Overlapping CIDR cản peering/routing thông thường.
- TGW hỗ trợ transitive nhưng cần route đúng.
- PrivateLink expose service, không mở toàn bộ network.
- Direct Connect không tự mã hóa traffic.
- VPN có encryption qua Internet nhưng có latency/throughput trade-off.

---

# Phần XV — Câu hỏi tự luyện

## Câu 1 — Public subnet

Một subnet có tên Public-App nhưng route table chỉ có local route. Một EC2 trong subnet có public IPv4 nhưng không truy cập Internet. Nguyên nhân có khả năng nhất?

A. Thiếu route default tới Internet Gateway  
B. Thiếu RDS read replica  
C. Thiếu S3 lifecycle  
D. Thiếu Transit Gateway

Đáp án: A.

Tên subnet không quyết định public. Route tới IGW là điều kiện quan trọng.

## Câu 2 — Private outbound

EC2 trong private subnet cần download package từ Internet. Cấu hình nào phù hợp?

A. Route trực tiếp private subnet tới IGW  
B. NAT Gateway ở public subnet và private route tới NAT  
C. Gắn public IP nhưng không route  
D. VPC peering với Internet

Đáp án: B.

## Câu 3 — NAT placement

NAT Gateway nên đặt ở đâu?

A. Isolated database subnet  
B. Private application subnet  
C. Public subnet có route tới IGW  
D. On-premises subnet

Đáp án: C.

## Câu 4 — Stateful firewall

Một instance gửi TCP request tới server qua Security Group outbound rule. Không có inbound rule tương ứng cho response trên cùng SG. Response có được phép theo state không?

A. Thường có, vì SG stateful  
B. Không, vì SG luôn stateless  
C. Chỉ khi dùng NACL  
D. Chỉ khi có Transit Gateway

Đáp án: A.

## Câu 5 — NACL

Một NACL cho phép inbound TCP 443 nhưng connection vẫn timeout. Nguyên nhân thường gặp?

A. NACL stateless và outbound response/ephemeral port chưa được cho phép  
B. SG chỉ có deny  
C. IGW không hỗ trợ TCP  
D. Route table tự động deny response

Đáp án: A.

## Câu 6 — Endpoint S3

Private EC2 cần truy cập S3 mà không dùng NAT Gateway. Dùng gì?

A. Internet Gateway  
B. S3 Gateway Endpoint  
C. NLB public  
D. Egress-only IGW cho IPv4

Đáp án: B.

## Câu 7 — Interface Endpoint

Application timeout khi gọi private AWS service qua Interface Endpoint. Điều gì cần kiểm tra trước?

A. Endpoint ENI, private DNS, endpoint SG và route/local path  
B. S3 lifecycle  
C. RDS read replica  
D. CloudFront cache policy

Đáp án: A.

## Câu 8 — Peering

VPC-A peering với VPC-B; VPC-B peering với VPC-C. VPC-A muốn truy cập VPC-C qua VPC-B. Đáp án đúng?

A. Hoạt động tự động  
B. Không hoạt động vì VPC peering không transitive  
C. Chỉ cần thêm route ở VPC-A  
D. Chỉ cần bật DNS support

Đáp án: B.

## Câu 9 — Transit Gateway

Một workload đã có route tới Transit Gateway nhưng không truy cập được VPC đích. Cần kiểm tra thêm?

A. TGW route table và attachment association/propagation  
B. S3 object version  
C. CloudFront invalidation  
D. RDS backup

Đáp án: A.

## Câu 10 — SG reference

ALB target được thay thế liên tục bởi Auto Scaling. Cách giới hạn app port tốt nhất?

A. Mở app port cho toàn Internet  
B. Allow app port từ Security Group của ALB  
C. Cập nhật từng IP target thủ công  
D. Dùng public IP cố định cho từng target

Đáp án: B.

## Câu 11 — Isolated database

Một database không cần Internet outbound và chỉ được truy cập từ application tier. Thiết kế phù hợp?

A. Public subnet, DB port từ Internet  
B. Isolated/private data subnets và DB SG chỉ nhận từ App-SG  
C. Public IP và NACL deny  
D. NAT Gateway trong data subnet

Đáp án: B.

## Câu 12 — IPv6 outbound

Private workload có IPv6 và cần outbound Internet nhưng không nhận inbound connection mới. Dùng gì?

A. NAT Gateway IPv4  
B. Egress-only Internet Gateway  
C. S3 Gateway Endpoint  
D. VPC peering

Đáp án: B.

## Câu 13 — Route priority

Route table có:

~~~text
0.0.0.0/0    → NAT
10.0.5.0/24  → Endpoint
~~~

Traffic tới 10.0.5.20 đi đâu?

A. NAT  
B. Endpoint  
C. IGW  
D. Không đi được

Đáp án: B.

## Câu 14 — DNS

Interface Endpoint đã tạo nhưng client vẫn resolve service hostname thành public IP. Kiểm tra gì?

A. Private DNS, VPC DNS attributes và resolver configuration  
B. S3 versioning  
C. NACL deny tất cả  
D. RDS Multi-AZ

Đáp án: A.

## Câu 15 — ALB target

Internet truy cập được ALB nhưng target unhealthy. Cách điều tra đầu tiên?

A. Kiểm tra target port/path, App-SG từ ALB-SG và health check response  
B. Xóa IGW  
C. Tạo VPC mới  
D. Bật S3 Transfer Acceleration

Đáp án: A.

---

# Phần XVI — Checklist thiết kế VPC production

## 68. Address plan

- [ ] VPC CIDR đủ lớn cho growth.
- [ ] Không overlap với on-premises.
- [ ] Không overlap giữa các VPC cần kết nối.
- [ ] Có phân bổ CIDR theo AZ/tier.
- [ ] Có kế hoạch cho endpoint ENI và Lambda ENI.
- [ ] Có naming/tagging convention.

## 69. Subnet và AZ

- [ ] Public subnet ở nhiều AZ nếu có public load balancer.
- [ ] Private app subnet ở nhiều AZ.
- [ ] Data subnet ở nhiều AZ theo HA requirement.
- [ ] Database không nằm public nếu không cần.
- [ ] Isolated subnet không có default route dư thừa.
- [ ] Route table association được kiểm soát rõ.

## 70. Routing

- [ ] Public route tới IGW.
- [ ] Private route tới NAT hoặc endpoint đúng mục đích.
- [ ] NAT subnet route tới IGW.
- [ ] Peering route có cả hai phía.
- [ ] TGW route table có route/attachment đúng.
- [ ] Không có route cụ thể ngoài ý muốn.
- [ ] Có review route propagation.
- [ ] Có kiểm tra IPv4 và IPv6 riêng.

## 71. Security

- [ ] ALB SG chỉ mở listener cần thiết.
- [ ] App SG nhận traffic từ ALB SG.
- [ ] DB SG nhận traffic từ App SG.
- [ ] SG không mở database cho Internet.
- [ ] NACL có rule hai chiều.
- [ ] NACL ephemeral port được xem xét.
- [ ] IAM và endpoint policy đúng.
- [ ] VPC Flow Logs được bật theo requirement.
- [ ] DNS và private hosted zone được kiểm soát.
- [ ] Không dùng public SSH nếu SSM/VPN phù hợp.

## 72. Operations và cost

- [ ] Có monitoring cho NAT bytes processed.
- [ ] Có alert cho rejected flow hoặc endpoint lỗi.
- [ ] Có NAT mỗi AZ hoặc đã ghi rõ trade-off.
- [ ] Dùng Gateway Endpoint cho S3/DynamoDB khi phù hợp.
- [ ] Interface Endpoint chỉ tạo ở service/AZ cần thiết.
- [ ] Có runbook troubleshooting.
- [ ] Có tag owner/environment/cost center.
- [ ] Xóa resource lab và Elastic IP không dùng.
- [ ] Review network cost và cross-AZ traffic.

---

# Phần XVII — Tóm tắt phải nhớ

1. VPC là network logic cô lập; bạn phải tự thiết kế địa chỉ, subnet, route và security.
2. CIDR phải đủ lớn và không overlap với mạng cần kết nối.
3. Một subnet chỉ thuộc một AZ.
4. Public subnet thường có route tới Internet Gateway.
5. Private subnet có thể outbound Internet qua NAT Gateway.
6. Isolated subnet chỉ có local hoặc route private cần thiết.
7. Route table quyết định path; SG/NACL quyết định traffic có được phép không.
8. Longest-prefix match ưu tiên route cụ thể hơn.
9. NAT Gateway đặt trong public subnet; private route table trỏ tới NAT.
10. NAT Gateway có cost hourly và data processing.
11. SG stateful và chỉ có allow rule.
12. NACL stateless, có allow/deny, cần cả chiều đi và chiều về.
13. VPC DNS attributes và private DNS rất quan trọng với endpoint.
14. Gateway Endpoint dành cho S3/DynamoDB.
15. Interface Endpoint dùng ENI/PrivateLink và cần kiểm tra SG/private DNS.
16. Endpoint không tự cấp IAM permission.
17. VPC Peering không transitive.
18. Transit Gateway hỗ trợ hub-and-spoke/transitive routing nhưng cần route đúng.
19. Multi-AZ cần resource và dependency phân bố thật sự, không chỉ subnet.
20. Troubleshooting phải đi theo packet path: DNS → route → gateway/endpoint → SG → NACL → service.
21. Least operational overhead thường nghiêng về managed service, nhưng vẫn phải kiểm tra cost và requirement.
22. Một VPC tốt là VPC có address plan, boundary, route, observability và runbook rõ ràng.

---

## Tài liệu tham khảo chính thức

- [Amazon VPC User Guide](https://docs.aws.amazon.com/vpc/latest/userguide/what-is-amazon-vpc.html)
- [VPC CIDR blocks](https://docs.aws.amazon.com/vpc/latest/userguide/vpc-cidr-blocks.html)
- [Subnet concepts](https://docs.aws.amazon.com/vpc/latest/userguide/configure-subnets.html)
- [Route tables](https://docs.aws.amazon.com/vpc/latest/userguide/VPC_Route_Tables.html)
- [Internet gateways](https://docs.aws.amazon.com/vpc/latest/userguide/VPC_Internet_Gateway.html)
- [NAT gateways](https://docs.aws.amazon.com/vpc/latest/userguide/vpc-nat-gateway.html)
- [Security groups](https://docs.aws.amazon.com/vpc/latest/userguide/vpc-security-groups.html)
- [Network ACLs](https://docs.aws.amazon.com/vpc/latest/userguide/vpc-network-acls.html)
- [DNS support in your VPC](https://docs.aws.amazon.com/vpc/latest/userguide/vpc-dns.html)
- [Route 53 Resolver](https://docs.aws.amazon.com/Route53/latest/DeveloperGuide/resolver.html)
- [VPC peering](https://docs.aws.amazon.com/vpc/latest/peering/what-is-vpc-peering.html)
- [AWS Transit Gateway](https://docs.aws.amazon.com/vpc/latest/tgw/what-is-transit-gateway.html)
- [VPC endpoints](https://docs.aws.amazon.com/vpc/latest/privatelink/vpc-endpoints.html)
- [VPC Flow Logs](https://docs.aws.amazon.com/vpc/latest/userguide/flow-logs.html)
- [Reachability Analyzer](https://docs.aws.amazon.com/vpc/latest/reachability/what-is-reachability-analyzer.html)
- [AWS PrivateLink](https://docs.aws.amazon.com/vpc/latest/privatelink/what-is-privatelink.html)


# Tuần 3 — VPC và networking căn bản

> Mục tiêu: đọc được network diagram, tính CIDR/subnet, vẽ packet path và thiết kế VPC Multi-AZ với public/private subnet, route table, security group, NACL, NAT và VPC endpoint.

## 1. Kết quả cần đạt

- Giải thích VPC, CIDR, subnet public/private/isolated và Availability Zone.
- Tính số IP cơ bản và chia subnet cho một VPC.
- Phân biệt route table, local route, default route và longest-prefix match.
- Giải thích Internet Gateway, public IPv4 và đường đi Internet.
- Thiết kế private subnet outbound qua NAT Gateway.
- Phân biệt NAT Gateway với NAT instance.
- Phân biệt security group stateful với NACL stateless.
- Vẽ packet path Internet → ALB → private EC2.
- Giải thích VPC DNS, DHCP option và Route 53 Resolver ở mức SAA.
- Phân biệt VPC peering, Transit Gateway, Gateway Endpoint và Interface Endpoint.
- Chẩn đoán lỗi dựa trên route, SG, NACL, DNS và endpoint.

## 2. Lịch học gợi ý

| Buổi | Nội dung | Thời lượng |
|---|---|---:|
| 1 | VPC, CIDR, subnet và route table | 3 giờ |
| 2 | Internet Gateway, NAT và packet path | 2 giờ |
| 3 | Security group, NACL và DNS | 3 giờ |
| 4 | Peering, Transit Gateway và endpoints | 2 giờ |
| 5 | Lab VPC Multi-AZ và câu hỏi SAA-C03 | 2–3 giờ |

Quy tắc debug: xác định source/destination, kiểm tra DNS, route table, gateway/endpoint, security group rồi NACL ở cả chiều đi và chiều về.

---

## 3. VPC và CIDR

Amazon VPC là một mạng logic cô lập trong AWS account. Bạn kiểm soát address range, subnet, route table, gateway và network security.

```text
VPC CIDR
├── Public subnet(s)  → Internet Gateway
├── Private subnet(s) → NAT Gateway hoặc endpoint
└── Isolated subnet(s) → local route/internal service
```

VPC không tự làm subnet public. Subnet được xem là public khi route table có route tới Internet Gateway và resource có public address phù hợp.

### 3.1. CIDR notation

CIDR có dạng `network/prefix-length`.

```text
Tổng IPv4 = 2^(32 - prefix length)
```

Ví dụ: `/16` có 65.536 địa chỉ, `/24` có 256, `/28` có 16. Trong một subnet IPv4 của AWS, thường có 5 địa chỉ được giữ lại, nên usable gần đúng là tổng trừ 5.

Đừng chọn subnet quá nhỏ cho workload cần scale. Khi thiết kế, dự trù cho số AZ, subnet theo tầng, interface endpoint, Lambda ENI và growth.

### 3.2. Ví dụ chia subnet

VPC `10.0.0.0/16`:

| Subnet | CIDR | AZ | Vai trò |
|---|---|---|---|
| Public-A | `10.0.0.0/20` | AZ-a | ALB, NAT Gateway |
| Public-B | `10.0.16.0/20` | AZ-b | ALB, NAT Gateway |
| Private-App-A | `10.0.32.0/20` | AZ-a | application |
| Private-App-B | `10.0.48.0/20` | AZ-b | application |
| Private-Data-A | `10.0.64.0/20` | AZ-a | database |
| Private-Data-B | `10.0.80.0/20` | AZ-b | database |

CIDR không được overlap trong cùng VPC và nên không overlap với mạng on-premises nếu dự định VPN/Direct Connect.

### 3.3. Public, private và isolated subnet

| Loại | Route phổ biến | Use case |
|---|---|---|
| Public | `0.0.0.0/0 → IGW` | ALB public, NAT Gateway |
| Private | `0.0.0.0/0 → NAT Gateway` | application/worker cần outbound |
| Isolated | local hoặc route nội bộ | database, internal service |

Private không có nghĩa là không có outbound Internet. Private subnet có thể đi Internet qua NAT nhưng Internet không tự mở inbound connection vào private resource.

Subnet thuộc đúng một AZ. Multi-AZ cần tạo subnet tương ứng ở nhiều AZ; một subnet không trải dài nhiều AZ.

---

## 4. Route table và routing

Route table quyết định traffic từ subnet đi đâu. Mỗi subnet có một route table được associate; nếu không associate explicit thì dùng main route table.

```text
Destination       Target
10.0.0.0/16       local
0.0.0.0/0         igw-xxxxxxxx
10.20.0.0/16      pcx-xxxxxxxx
```

`local` route cho phép subnet trong cùng VPC giao tiếp theo CIDR phù hợp. `0.0.0.0/0` là default route. Khi nhiều route cùng match, route có prefix dài hơn được ưu tiên.

```text
10.0.0.0/16  → local
10.0.5.0/24  → endpoint
0.0.0.0/0    → NAT Gateway
```

Traffic tới `10.0.5.20` đi endpoint vì `/24` cụ thể hơn `/16` và `/0`.

Route table quyết định **có đường đi hay không**, không phải firewall. Có route đúng nhưng vẫn bị SG/NACL chặn; có SG allow nhưng không có route thì vẫn không kết nối được.

Nên dùng route table riêng cho các tier:

```text
PublicRouteTable:     local + 0.0.0.0/0 → IGW
PrivateAppRouteTable:  local + 0.0.0.0/0 → NAT + S3 → endpoint
IsolatedDataTable:     local only
```

Trong thiết kế HA, private subnet AZ-a thường route NAT Gateway ở AZ-a và AZ-b route NAT Gateway ở AZ-b. Một NAT dùng chung có thể giảm chi phí nhưng tăng cross-AZ traffic và dependency.

---

## 5. Internet Gateway và public access

Internet Gateway (IGW) là gateway managed, redundant cho VPC giao tiếp Internet.

Để resource IPv4 nhận inbound Internet trực tiếp, cần:

1. VPC có IGW attached.
2. Subnet route table có `0.0.0.0/0 → IGW`.
3. Resource có public IPv4 hoặc Elastic IP phù hợp.
4. Security group cho phép port cần thiết.
5. NACL cho phép cả chiều đi/về.
6. OS/application đang listen đúng port.

Public subnet không tự cấp public IP cho mọi resource. Elastic IP là địa chỉ tĩnh có thể remap nhưng có thể phát sinh cost; ưu tiên DNS/load balancer khi có thể.

Với IPv6, resource không dùng NAT Gateway. Có thể dùng egress-only Internet Gateway để cho phép outbound IPv6 nhưng chặn unsolicited inbound; cần kiểm tra cả route `::/0`, SG và NACL.

---

## 6. NAT Gateway và NAT instance

### 6.1. NAT Gateway

NAT Gateway cho phép resource private khởi tạo outbound connection tới Internet, trong khi Internet không khởi tạo inbound connection trực tiếp tới resource đó.

```text
Private EC2
  → private route table
  → NAT Gateway trong public subnet
  → public route table
  → Internet Gateway
  → Internet
```

NAT Gateway phải ở public subnet có route tới IGW và thường cần Elastic IP. Private subnet route tới NAT; không route trực tiếp tới IGW cho private instance.

NAT Gateway không phải firewall inbound. Nó không thay thế SG, NACL, proxy, egress filtering hoặc inspection firewall.

### 6.2. HA và cost

NAT Gateway managed nhưng tính phí theo giờ và data processed. Một NAT ở một AZ có thể là dependency khi AZ lỗi. Pattern HA là một NAT mỗi AZ và route table tương ứng.

| Lựa chọn | Ưu điểm | Hạn chế |
|---|---|---|
| Một NAT cho VPC | ít phí theo giờ | cross-AZ, dependency lớn hơn |
| NAT mỗi AZ | HA tốt, giảm cross-AZ | nhiều phí theo giờ |
| VPC endpoint | private access, giảm NAT cost | chỉ dùng cho service hỗ trợ |

### 6.3. NAT instance

NAT instance là EC2 do khách hàng quản lý. Nó cần patch, scale, monitor, failover và disable source/destination check. Có thể phù hợp case đặc biệt hoặc traffic nhỏ, nhưng thường không phải đáp án khi đề yêu cầu least operational overhead/HA.

---

## 7. Security Group

Security group là virtual firewall stateful gắn với ENI/resource.

- chỉ có allow rule, không có explicit deny;
- stateful: response traffic được cho phép tự động;
- có thể reference SG khác;
- áp dụng ở resource/ENI level.

Pattern tốt:

```text
ALB-SG: inbound 443 từ Internet
App-SG: inbound 8080 từ ALB-SG
DB-SG:  inbound 5432 từ App-SG
```

Dùng SG reference tốt hơn hard-code private IP khi tầng ứng dụng scale.

SG không có deny rule. Mở `0.0.0.0/0` vào database port thường là thiết kế kém; nên giới hạn theo source SG hoặc CIDR cần thiết.

---

## 8. Network ACL

Network ACL (NACL) là firewall stateless ở subnet level.

- có allow và deny rule;
- xử lý theo số thứ tự từ nhỏ tới lớn;
- rule đầu tiên match được áp dụng;
- phải cho phép chiều đi và chiều về;
- subnet có thể dùng một NACL, một NACL có thể gắn nhiều subnet.

NACL cần chú ý ephemeral ports. Nếu client khởi tạo TCP tới server 443, response quay về ephemeral port của client; NACL phải cho phép chiều response tương ứng.

| Đặc điểm | Security Group | NACL |
|---|---|---|
| Scope | resource/ENI | subnet |
| Stateful | có | không |
| Rule | allow | allow + deny |
| Order | không theo số | số nhỏ trước |
| SG reference | có | không |

SG phù hợp access control theo workload; NACL phù hợp coarse filtering hoặc deny subnet-level.

---

## 9. DNS, DHCP và Resolver

VPC có hai DNS attributes quan trọng:

- `enableDnsSupport`: bật phân giải DNS trong VPC;
- `enableDnsHostnames`: cho phép resource nhận hostname DNS trong điều kiện phù hợp.

VPC cung cấp AmazonProvidedDNS ở địa chỉ VPC CIDR base + 2, ví dụ `10.0.0.2` với VPC `10.0.0.0/16`.

DHCP option set truyền domain name, DNS server, NTP và NetBIOS settings cho instance; một VPC chỉ associate một DHCP option set tại một thời điểm.

Route 53 Resolver hỗ trợ private/hybrid DNS:

- inbound endpoint: on-premises query AWS/private hosted zone;
- outbound endpoint: VPC query DNS server on-premises;
- forwarding rules: chuyển domain cụ thể tới resolver phù hợp.

Nếu private endpoint không hoạt động, kiểm tra private DNS, VPC DNS attributes, route, SG endpoint và NACL.

---

## 10. VPC peering và Transit Gateway

### VPC peering

VPC peering là kết nối private point-to-point giữa hai VPC.

- traffic không đi Internet;
- cần route table ở cả hai phía;
- CIDR không được overlap;
- không có transitive routing;
- nhiều VPC sẽ tạo mesh phức tạp.

```text
VPC A ↔ VPC B
VPC A ↔ VPC C
```

VPC B không tự động đi qua A để tới C.

### Transit Gateway

Transit Gateway là hub managed kết nối nhiều VPC và on-premises, hỗ trợ hub-and-spoke và routing transitive qua TGW route tables.

| Tiêu chí | VPC peering | Transit Gateway |
|---|---|---|
| Topology | point-to-point | hub-and-spoke |
| Transitive | không | có qua TGW |
| Quy mô | nhỏ/vài VPC | nhiều VPC/hybrid |
| Chi phí | thấp hơn ở quy mô nhỏ | thêm charge và route management |

TGW không tự tạo route nếu attachment và route table chưa cấu hình đúng. Overlapping CIDR vẫn là vấn đề cần xử lý.

---

## 11. VPC endpoints

VPC endpoint cho phép truy cập AWS service hoặc PrivateLink service mà không cần đi qua Internet/NAT tùy loại.

### 11.1. Gateway endpoint

Gateway endpoint dùng cho S3 và DynamoDB.

- thêm route vào route table;
- không tạo ENI trong subnet;
- phù hợp private EC2 tới S3/DynamoDB;
- cần endpoint policy và resource policy đúng;
- thường giúp giảm NAT data processing.

```text
Private subnet → route table → Gateway Endpoint → S3/DynamoDB
```

### 11.2. Interface endpoint

Interface endpoint dùng AWS PrivateLink, tạo ENI private IP trong subnet và có security group.

- dùng cho nhiều AWS service, SaaS hoặc endpoint service hỗ trợ;
- cần route, SG endpoint và private DNS phù hợp;
- có phí theo giờ/endpoint AZ và data processing;
- nên triển khai trong nhiều AZ nếu cần HA.

```text
Private workload → Interface Endpoint ENI → AWS service
```

| Đặc điểm | Gateway endpoint | Interface endpoint |
|---|---|---|
| Service | S3, DynamoDB | nhiều service/PrivateLink |
| Cơ chế | route table target | ENI private IP |
| Security group | không gắn endpoint ENI | có |
| DNS | ít trọng tâm | private DNS quan trọng |
| Chi phí | thường không hourly | hourly + data processing |

Nếu đề nói private access S3 hoặc giảm NAT cost, nghĩ Gateway Endpoint. Nếu dịch vụ chỉ hỗ trợ PrivateLink, nghĩ Interface Endpoint.

Endpoint không tự cấp IAM permission. Vẫn kiểm tra IAM policy, endpoint policy, resource policy, SG, NACL và DNS.

---

## 12. Network observability

VPC Flow Logs ghi metadata traffic tới/từ VPC, subnet hoặc network interface. Dùng để xem source/destination, port, protocol và accepted/rejected traffic.

Flow Logs không phải packet capture đầy đủ. Kết hợp với CloudWatch/S3 logs, CloudTrail, ALB access logs, Reachability Analyzer và DNS query logging.

Khi debug, phân biệt:

- không có route;
- route đúng nhưng SG reject;
- SG allow nhưng NACL reject;
- TCP kết nối được nhưng DNS, TLS hoặc application lỗi.

---

## 13. Packet path mẫu

### Internet → ALB → private EC2

```text
Client Internet
    ↓
Internet Gateway
    ↓ public route
Public ALB
    ↓ target group/local route
Private EC2 ở AZ-a hoặc AZ-b
    ↓
Private database
```

Checklist: ALB subnet route tới IGW, ALB-SG inbound 443, App-SG chỉ nhận app port từ ALB-SG, DB-SG chỉ nhận DB port từ App-SG, target health check đúng và EC2 không cần public IP.

### Private EC2 → Internet

```text
Private EC2 → private route → NAT Gateway
→ public route → IGW → Internet
```

Kiểm tra route private tới NAT, route NAT subnet tới IGW, Elastic IP, outbound SG và NACL.

### Private EC2 → S3

```text
Private EC2 → route table → Gateway Endpoint → S3
```

Path private không bỏ qua IAM, endpoint policy hoặc bucket policy.

---

## 14. Lab bắt buộc

### Lab 1 — VPC hai AZ

1. Tạo VPC `10.0.0.0/16`.
2. Tạo public, private app và private data subnet ở hai AZ.
3. Tạo public route table với `0.0.0.0/0 → IGW`.
4. Associate public subnets.
5. Tạo private route table riêng cho app.
6. Ghi CIDR, AZ, route table và mục đích từng subnet.

Không tạo NAT Gateway nếu chỉ cần học route; NAT Gateway tính phí theo giờ và data processed.

### Lab 2 — ALB public, EC2 private

- ALB ở public subnets.
- EC2 ở private app subnets.
- ALB-SG cho phép 80/443 từ Internet tùy lab.
- App-SG chỉ cho app port từ ALB-SG.
- Không gắn public IP cho EC2 nếu không cần.
- Kiểm tra ALB truy cập được nhưng EC2 không truy cập trực tiếp từ Internet.

### Lab 3 — Private outbound qua NAT

1. Đặt NAT Gateway trong public subnet.
2. Gắn Elastic IP theo yêu cầu.
3. Private route table thêm `0.0.0.0/0 → NAT`.
4. Kiểm tra NAT subnet có `0.0.0.0/0 → IGW`.
5. Test outbound từ private EC2.
6. Xóa NAT Gateway và Elastic IP ngay sau lab.

### Lab 4 — Gateway Endpoint cho S3

- Tạo Gateway Endpoint cho S3.
- Associate với private route table.
- Dùng role chỉ đọc một prefix.
- Kiểm tra route endpoint, endpoint policy và bucket policy.
- So sánh path endpoint với path NAT.

### Lab 5 — SG và NACL

| Test | Route | SG | NACL | Kết quả |
|---|---|---|---|---|
| ALB → app port | đúng | allow | allow | thành công |
| Internet → private EC2 | không có public path | — | — | thất bại |
| App → DB port | đúng | allow SG reference | allow | thành công |
| App → DB port | đúng | deny | allow | thất bại |
| App → DB port | đúng | allow | deny một chiều | thất bại |

Ghi rõ lớp nào đã chặn sau mỗi test.

---

## 15. Bẫy đề thi SAA-C03

- Subnet chỉ thuộc một AZ; Multi-AZ cần subnet ở nhiều AZ.
- Public subnet cần route tới IGW; public IP một mình chưa đủ.
- Private subnet có thể outbound Internet qua NAT.
- NAT Gateway ở public subnet, private route table trỏ tới NAT.
- IGW không phải NAT; private instance không route trực tiếp tới IGW để ẩn địa chỉ.
- NAT Gateway không cho inbound connection mới tới private instance.
- Security group stateful, chỉ có allow rule.
- NACL stateless, có allow/deny, xử lý theo rule number và cần ephemeral ports.
- VPC peering không transitive và CIDR không được overlap.
- Transit Gateway hỗ trợ transitive routing nhưng vẫn cần attachment/route table.
- Gateway endpoint dành cho S3/DynamoDB; interface endpoint dùng ENI/PrivateLink.
- VPC endpoint không tự cấp IAM permission.
- Route table quyết định path, không phải firewall.
- Longest-prefix match được ưu tiên hơn default route.
- VPC DNS/private DNS cần hoạt động cho nhiều endpoint/service.

---

## 16. Câu hỏi tự luyện

### Câu 1

EC2 private cần tải package từ Internet nhưng không được nhận inbound trực tiếp. Thiết kế nào phù hợp?

**Đáp án:** private route tới NAT Gateway trong public subnet; NAT subnet route tới IGW; SG/NACL cho phép traffic.

### Câu 2

ALB public có target EC2 private nhưng target unhealthy. Kiểm tra gì?

**Đáp án:** target port/path, app listen, ALB-SG tới App-SG, NACL hai chiều, local route, subnet/AZ và health check response.

### Câu 3

Private EC2 cần truy cập S3 không qua NAT. Dùng gì?

**Đáp án:** Gateway VPC Endpoint cho S3, associate private route table; vẫn kiểm tra IAM, endpoint policy và bucket policy.

### Câu 4

Có 30 VPC cần kết nối private và routing giữa nhiều mạng. Chọn gì?

**Đáp án:** Transit Gateway vì hub-and-spoke và transitive routing; peering tạo mesh và không transitive.

### Câu 5

SG cho phép TCP 443 nhưng client vẫn không kết nối được. Vì sao?

**Đáp án:** thiếu route/IGW/public IP, NACL chặn, resource không listen, DNS/TLS/application lỗi hoặc network path sai. SG allow không tự tạo route.

### Câu 6

NACL cho phép inbound TCP 443 nhưng response không về client. Vì sao?

**Đáp án:** NACL stateless nên cần rule outbound cho ephemeral port/response traffic.

### Câu 7

Hai VPC có CIDR overlap cần peering. Vấn đề là gì?

**Đáp án:** peering không hỗ trợ overlapping CIDR; cần renumber, translation hoặc thiết kế khác.

### Câu 8

Interface endpoint timeout. Kiểm tra gì?

**Đáp án:** endpoint ENI trong subnet/AZ, route, endpoint SG, private DNS, NACL, service support, IAM và endpoint policy.

### Câu 9

Route table có `10.0.0.0/16 → local` và `10.0.5.0/24 → endpoint`. Traffic tới `10.0.5.20` đi đâu?

**Đáp án:** endpoint vì `/24` cụ thể hơn `/16`.

---

## 17. Checklist hoàn thành tuần

### Kiến thức

- [ ] Giải thích VPC, CIDR, subnet và AZ.
- [ ] Tính tổng/usable IPv4 cơ bản.
- [ ] Phân biệt public, private và isolated subnet.
- [ ] Giải thích local route, default route và longest-prefix match.
- [ ] Phân biệt IGW, NAT Gateway và NAT instance.
- [ ] Phân biệt SG stateful với NACL stateless.
- [ ] Nêu vai trò của ephemeral ports.
- [ ] Giải thích VPC DNS, DHCP option và Resolver.
- [ ] Phân biệt peering không transitive với Transit Gateway.
- [ ] Phân biệt Gateway Endpoint và Interface Endpoint.
- [ ] Giải thích vì sao endpoint không thay thế IAM.

### Thực hành

- [ ] Đã tạo VPC hai AZ với public/private/data subnet.
- [ ] Đã vẽ packet path Internet → ALB → private EC2.
- [ ] Đã cấu hình SG reference giữa ALB, app và database.
- [ ] Đã kiểm tra route table và lỗi route.
- [ ] Đã test private outbound qua NAT hoặc mô phỏng path.
- [ ] Đã tạo Gateway Endpoint cho S3.
- [ ] Đã test SG và NACL với chiều đi/về.
- [ ] Đã xóa NAT Gateway, Elastic IP và resource tính phí sau lab.

Bạn sẵn sàng sang Tuần 4 khi có thể chỉ ra request đi qua route table, gateway, SG và NACL nào, đồng thời giải thích vì sao private subnet vẫn có thể outbound Internet.

---

## 18. Tài liệu tham khảo

- [Amazon VPC User Guide](https://docs.aws.amazon.com/vpc/latest/userguide/what-is-amazon-vpc.html) — tổng quan VPC.
- [VPC CIDR blocks](https://docs.aws.amazon.com/vpc/latest/userguide/vpc-cidr-blocks.html) — CIDR và address range.
- [Route tables for your VPC](https://docs.aws.amazon.com/vpc/latest/userguide/VPC_Route_Tables.html) — route table và routing.
- [Internet gateways](https://docs.aws.amazon.com/vpc/latest/userguide/VPC_Internet_Gateway.html) — public access qua IGW.
- [NAT gateways](https://docs.aws.amazon.com/vpc/latest/userguide/vpc-nat-gateway.html) — private outbound và NAT design.
- [Security groups](https://docs.aws.amazon.com/vpc/latest/userguide/vpc-security-groups.html) — stateful resource firewall.
- [Network ACLs](https://docs.aws.amazon.com/vpc/latest/userguide/vpc-network-acls.html) — stateless subnet firewall.
- [VPC peering](https://docs.aws.amazon.com/vpc/latest/peering/what-is-vpc-peering.html) — kết nối VPC point-to-point.
- [AWS Transit Gateway](https://docs.aws.amazon.com/vpc/latest/tgw/what-is-transit-gateway.html) — hub-and-spoke connectivity.
- [VPC endpoints](https://docs.aws.amazon.com/vpc/latest/privatelink/vpc-endpoints.html) — Gateway Endpoint và Interface Endpoint.


# Mô hình quan hệ các thành phần trong AWS VPC

> Sơ đồ tổng quan giữa VPC, subnet, route table, Security Group, Network ACL, Internet Gateway, NAT Gateway, Route 53, ALB, VPC Endpoint và database.

## 1. Sơ đồ tổng quan

~~~mermaid
flowchart TB
    User[User / Internet]
    R53[Route 53 DNS]
    CF[CloudFront / WAF]
    IGW[Internet Gateway]

    subgraph VPC["VPC 10.0.0.0/16"]
        subgraph Public["Public Subnets"]
            PubRT[Public Route Table]
            ALB[Internet-facing ALB]
            NAT[NAT Gateway + Elastic IP]
        end

        subgraph App["Private App Subnets"]
            AppRT[Private Route Table]
            EC2[EC2 / ECS / Lambda]
            AppSG[Application Security Group]
        end

        subgraph Data["Private Data Subnets"]
            DataRT[Isolated Route Table]
            RDS[RDS / Aurora]
            DBSG[Database Security Group]
        end

        NACL1[Network ACL - Public]
        NACL2[Network ACL - App]
        NACL3[Network ACL - Data]
        S3EP[S3 Gateway Endpoint]
        VPCEP[Interface VPC Endpoint]
        PrivateDNS[Route 53 Private Hosted Zone]
    end

    User --> R53
    R53 --> CF
    R53 --> ALB
    CF --> IGW

    IGW --> PubRT
    PubRT --> ALB
    PubRT --> NAT

    ALB --> AppRT
    AppRT --> EC2
    EC2 --> DataRT
    DataRT --> RDS

    EC2 --> S3EP
    EC2 --> VPCEP
    PrivateDNS -. DNS nội bộ .-> EC2
    PrivateDNS -. DNS nội bộ .-> RDS

    NACL1 -. bảo vệ .-> Public
    NACL2 -. bảo vệ .-> App
    NACL3 -. bảo vệ .-> Data

    AppSG -. gắn vào ENI .-> EC2
    DBSG -. gắn vào ENI .-> RDS
    AppSG -. cho phép traffic .-> DBSG
~~~

## 2. Quan hệ phân cấp

~~~text
VPC
├── CIDR block
├── Availability Zones
│   ├── Public subnet
│   ├── Private app subnet
│   └── Private data subnet
├── Route tables
├── Internet Gateway
├── NAT Gateway
├── VPC Endpoints
├── Security Groups
├── Network ACLs
└── DNS/Resolver
~~~

## 3. Vai trò của từng thành phần

| Thành phần | Vai trò |
|---|---|
| VPC | Mạng logic cô lập trong AWS Region |
| Subnet | Phân vùng IP trong một Availability Zone |
| Route Table | Quyết định packet đi tới next hop nào |
| Internet Gateway | Kết nối VPC với Internet |
| NAT Gateway | Cho private subnet outbound Internet |
| Security Group | Stateful firewall ở resource/ENI level |
| Network ACL | Stateless firewall ở subnet level |
| Route 53 | DNS và routing theo hostname |
| ALB | Nhận request và chuyển vào application tier |
| Gateway Endpoint | Private access tới S3/DynamoDB |
| Interface Endpoint | PrivateLink access qua ENI private IP |
| RDS/Aurora | Database trong private data subnet |
| Transit Gateway | Hub kết nối nhiều VPC và hybrid network |

## 4. Route table và subnet

### Public subnet

~~~text
Public Route Table
├── VPC CIDR   → local
└── 0.0.0.0/0 → Internet Gateway
~~~

Thường chứa:

- Internet-facing ALB.
- NAT Gateway.
- Public-facing network appliance.

### Private app subnet

~~~text
Private App Route Table
├── VPC CIDR   → local
├── 0.0.0.0/0 → NAT Gateway
└── S3         → Gateway Endpoint
~~~

Thường chứa:

- EC2.
- ECS task.
- Lambda trong VPC.
- Application worker.

### Private data subnet

~~~text
Isolated Route Table
└── VPC CIDR → local
~~~

Thường chứa:

- RDS/Aurora.
- ElastiCache.
- Internal data service.

## 5. Security Group và NACL

~~~text
ALB-SG
  └── Allow HTTPS từ Internet

App-SG
  └── Allow application port từ ALB-SG

DB-SG
  └── Allow database port từ App-SG
~~~

Security Group:

- Gắn vào ENI/resource.
- Stateful.
- Chỉ có allow rule.
- Có thể reference Security Group khác.

Network ACL:

- Gắn với subnet.
- Stateless.
- Có allow và deny rule.
- Cần cho phép cả chiều đi và chiều về.
- Rule được xử lý theo số thứ tự.

## 6. Đường đi Internet vào application

~~~text
User
  ↓
Route 53 phân giải DNS
  ↓
CloudFront/WAF tùy kiến trúc
  ↓
Internet Gateway
  ↓
Public ALB
  ↓
Private EC2/ECS
  ↓
Private RDS/Aurora
~~~

Để đường đi hoạt động:

- Route 53 phải trỏ đúng resource.
- ALB phải nằm trong public subnet.
- Public route table phải có route tới IGW.
- ALB-SG phải allow listener port.
- App-SG phải allow application port từ ALB-SG.
- DB-SG phải allow database port từ App-SG.
- Target health check phải thành công.

## 7. Đường đi outbound từ private subnet

~~~text
Private EC2
  ↓
Private Route Table
  ↓
NAT Gateway trong Public Subnet
  ↓
Public Route Table
  ↓
Internet Gateway
  ↓
Internet
~~~

NAT Gateway:

- Phải nằm trong public subnet.
- Public subnet phải route tới IGW.
- Private subnet phải route tới NAT.
- Có phí theo giờ và dữ liệu xử lý.
- Không cho Internet khởi tạo inbound connection mới tới private EC2.

## 8. Private access tới S3

~~~text
Private EC2
  ↓
Private Route Table
  ↓
S3 Gateway Endpoint
  ↓
S3
~~~

Ngoài route, còn phải kiểm tra:

- IAM role.
- Endpoint policy.
- Bucket policy.
- KMS key policy nếu dùng SSE-KMS.

Nếu private workload chỉ truy cập S3 hoặc DynamoDB, Gateway Endpoint thường giúp tránh NAT path và giảm chi phí.

## 9. Interface VPC Endpoint

~~~text
Private workload
  ↓
Private DNS
  ↓
Interface Endpoint ENI
  ↓
AWS Service hoặc PrivateLink Service
~~~

Interface Endpoint cần kiểm tra:

- ENI được tạo ở subnet/AZ phù hợp.
- Endpoint Security Group cho phép workload.
- Private DNS được bật.
- VPC DNS support/hostnames được bật.
- NACL cho phép traffic.
- IAM và resource policy cho phép.
- Service hỗ trợ endpoint ở Region hiện tại.

## 10. VPC Peering và Transit Gateway

### VPC Peering

~~~text
VPC-A ↔ VPC-B
~~~

- Kết nối point-to-point.
- Cần route ở cả hai phía.
- CIDR không được overlap.
- Không có transitive routing.

~~~text
VPC-A ↔ VPC-B ↔ VPC-C

VPC-A không tự động đi qua VPC-B để tới VPC-C.
~~~

### Transit Gateway

~~~text
VPC-A ─┐
VPC-B ─┼── Transit Gateway ── VPN/Direct Connect
VPC-C ─┘
~~~

- Hub-and-spoke.
- Hỗ trợ transitive routing.
- Có Transit Gateway route table.
- Phù hợp nhiều VPC và hybrid network.
- Vẫn cần kiểm tra route table trong VPC và TGW.

## 11. Troubleshooting theo packet path

~~~text
DNS
  ↓
Source/Destination
  ↓
Subnet
  ↓
Route Table
  ↓
Gateway / Endpoint / Peering / TGW
  ↓
Security Group
  ↓
Network ACL
  ↓
Port/Application
  ↓
TLS/Auth
~~~

### EC2 private không ra Internet

Kiểm tra:

1. Private route table có route tới NAT không?
2. NAT có nằm trong public subnet không?
3. Public route table có route tới IGW không?
4. NAT có available và có Elastic IP phù hợp không?
5. SG outbound có cho phép không?
6. NACL có cho phép chiều về không?
7. DNS có hoạt động không?

### ALB target unhealthy

Kiểm tra:

1. Target listen đúng port chưa?
2. Health check path đúng chưa?
3. App-SG có allow từ ALB-SG không?
4. NACL có chặn không?
5. Application có trả status code hợp lệ không?
6. Database hoặc dependency có làm app fail không?

## 12. Bẫy AWS SAA-C03

- Public subnet cần route tới Internet Gateway.
- Public IP một mình chưa đủ để truy cập Internet.
- Private subnet có thể outbound Internet qua NAT.
- NAT Gateway phải ở public subnet.
- Route table không phải firewall.
- Security Group stateful, NACL stateless.
- NACL cần rule cả chiều đi và chiều về.
- VPC Peering không transitive.
- Transit Gateway cần route ở cả VPC và TGW.
- Gateway Endpoint dùng cho S3/DynamoDB.
- Interface Endpoint dùng ENI/PrivateLink.
- Endpoint không tự cấp IAM permission.
- Route 53 là DNS; route table mới quyết định network path.

## 13. Tóm tắt ngắn

~~~text
VPC       = mạng logic
Subnet    = phân vùng mạng trong một AZ
Route     = quyết định packet đi đâu
IGW       = kết nối public Internet
NAT       = cho private subnet outbound Internet
SG        = firewall cho resource/ENI
NACL      = firewall cho subnet
Route 53  = DNS
Endpoint  = private access tới AWS service
ALB       = nhận request và chuyển vào private app
RDS       = database trong private data subnet
TGW       = hub kết nối nhiều VPC
~~~

## Tài liệu tham khảo

- [Amazon VPC User Guide](https://docs.aws.amazon.com/vpc/latest/userguide/what-is-amazon-vpc.html)
- [Route tables](https://docs.aws.amazon.com/vpc/latest/userguide/VPC_Route_Tables.html)
- [Internet gateways](https://docs.aws.amazon.com/vpc/latest/userguide/VPC_Internet_Gateway.html)
- [NAT gateways](https://docs.aws.amazon.com/vpc/latest/userguide/vpc-nat-gateway.html)
- [Security groups](https://docs.aws.amazon.com/vpc/latest/userguide/vpc-security-groups.html)
- [Network ACLs](https://docs.aws.amazon.com/vpc/latest/userguide/vpc-network-acls.html)
- [VPC endpoints](https://docs.aws.amazon.com/vpc/latest/privatelink/vpc-endpoints.html)
- [AWS Transit Gateway](https://docs.aws.amazon.com/vpc/latest/tgw/what-is-transit-gateway.html)


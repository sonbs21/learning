# Architect your solution for Amazon ECS — Toàn tập

> Tổng hợp từ **toàn bộ folder `05 - Architect your solution for Amazon ECS`** (`05-00` → `05-11`, `05-99`), cùng cách làm với các file toàn tập *Task definition* (folder 13), *Load balancing* và *Service Connect* (folder 15).
>
> **Ký hiệu**
> - Không ký hiệu = lấy từ docs AWS đã dịch trong folder (có ghi file nguồn ở đầu mỗi mục).
> - 🔎 = kiến thức ngoài folder hoặc suy luận của mình — **nên tự kiểm tra lại** với docs AWS hiện tại.
> - ✍️ = ví dụ tự đặt (tên, giá trị, ID là placeholder).
> - ⚠️ = bẫy hay gặp.

---

## Mục lục

0. [Tóm tắt một trang](#0-tóm-tắt-một-trang)
1. [Bức tranh tổng thể: từ Dockerfile tới service đang chạy](#1-bức-tranh-tổng-thể-từ-dockerfile-tới-service-đang-chạy)
2. [Chọn compute: Fargate, Managed Instances, EC2](#2-chọn-compute-fargate-managed-instances-ec2)
3. [Launch type và capacity provider](#3-launch-type-và-capacity-provider)
4. [Đặt workload ở đâu: Region, AZ, Local Zones, Wavelength, Outposts, shared subnet](#4-đặt-workload-ở-đâu-region-az-local-zones-wavelength-outposts-shared-subnet)
5. [Mạng (1): task đi ra internet](#5-mạng-1-task-đi-ra-internet)
6. [Mạng (2): nhận traffic từ internet vào](#6-mạng-2-nhận-traffic-từ-internet-vào)
7. [Mạng (3): gọi dịch vụ AWS từ trong VPC](#7-mạng-3-gọi-dịch-vụ-aws-từ-trong-vpc)
8. [Mạng (4): các service gọi nhau](#8-mạng-4-các-service-gọi-nhau)
9. [Mạng (5): nhiều account, nhiều VPC, on-premises](#9-mạng-5-nhiều-account-nhiều-vpc-on-premises)
10. [Service endpoint và dual-stack (IPv6) cho ECS API](#10-service-endpoint-và-dual-stack-ipv6-cho-ecs-api)
11. [IAM roles](#11-iam-roles)
12. [Account settings (feature access) và logging](#12-account-settings-feature-access-và-logging)
13. [Container image best practices](#13-container-image-best-practices)
14. [Kích thước task](#14-kích-thước-task)
15. [Service auto scaling: chọn metric đúng](#15-service-auto-scaling-chọn-metric-đúng)
16. [Capacity, tính sẵn sàng và cú sốc nhu cầu](#16-capacity-tính-sẵn-sàng-và-cú-sốc-nhu-cầu)
17. [Cluster capacity và tăng tốc Cluster Auto Scaling (EC2)](#17-cluster-capacity-và-tăng-tốc-cluster-auto-scaling-ec2)
18. [Tối ưu thời gian khởi chạy task](#18-tối-ưu-thời-gian-khởi-chạy-task)
19. [Quota và throttling ở quy mô lớn](#19-quota-và-throttling-ở-quy-mô-lớn)
20. [Troubleshooting mạng](#20-troubleshooting-mạng)
21. [Triển khai: kiến trúc tham chiếu từng bước](#21-triển-khai-kiến-trúc-tham-chiếu-từng-bước)
22. [Checklist quyết định kiến trúc](#22-checklist-quyết-định-kiến-trúc)
23. [Chỗ docs có lỗi / không khớp](#23-chỗ-docs-có-lỗi--không-khớp)
24. [Câu hỏi tự kiểm tra (có đáp án)](#24-câu-hỏi-tự-kiểm-tra-có-đáp-án)
25. [Bản đồ nguồn](#25-bản-đồ-nguồn)

---

## 0. Tóm tắt một trang

| Chủ đề | Điều cần nhớ |
|---|---|
| Chuỗi khái niệm | Dockerfile → image → registry (ECR) → task definition → task / service trên cluster → giám sát |
| Compute | **Fargate** (không quản lý gì), **Managed Instances** (AWS quản lý EC2 trong account bạn), **EC2** (tự quản lý: GPU, custom AMI, privileged…) |
| Capacity | Launch type chỉ để khai báo **tương thích** (`requiresCompatibilities`); **capacity provider strategy** quyết định task **chạy ở đâu**. Dùng capacity provider ngay từ đầu |
| Chuyển đổi | Launch type → capacity provider: được. Capacity provider ↔ capacity provider: được. Capacity provider → launch type: **không** (trừ quay về launch type ban đầu). EC2 ↔ Fargate launch type: **không** |
| Edge | Local Zones, Wavelength, Outposts: **không có Fargate** |
| Mạng chuẩn | Task ở **private subnet**; vào qua **ALB** (hoặc NLB / API Gateway); ra internet qua **NAT** (1 NAT / AZ); gọi dịch vụ AWS qua **VPC endpoint** |
| Service gọi nhau | **Service Connect** (khuyến nghị) > Cloud Map service discovery (tự retry, chờ TTL) > internal LB (tốn tiền, nhưng mọi network mode) |
| IAM | **Task execution role** = quyền của ECS (ECR, `awslogs`, secret). **Task role** = quyền của code app |
| Image | Đầy đủ dependency · 1 tiến trình / container · bắt `SIGTERM` · log ra stdout/stderr · tag = git SHA + immutable tag ECR · image nhỏ, cùng Region |
| Sizing | Reservation (đảm bảo) → bursting → limit (CPU throttle, memory kill). Chọn bằng **load test** |
| Scale | Metric tốt: **tương quan với tải** và **giảm một nửa khi gấp đôi task**. Java/.NET/Ruby: không scale theo memory. Job queue: scale theo **queue depth** |
| HA | Target 60–80% utilization; **2 AZ → 2× task**, **3 AZ → 1,5× task** |
| Trần cứng | 5.000 EC2 / cluster; **1.000 task / service khi dùng Cloud Map** (không tăng được); ENI / instance (dùng trunking: c5.large 2 → 10 task) |
| Throttling | Đồng bộ (bạn gọi API → retry backoff+jitter) và bất đồng bộ (ECS gọi thay bạn → xem service event, CloudTrail, EventBridge) |

---

## 1. Bức tranh tổng thể: từ Dockerfile tới service đang chạy

> Nguồn: `05-00`, `05-99`

```
Dockerfile ──build──► Image ──push──► Registry (Amazon ECR)
                                          │
Task definition (JSON: image, CPU/memory, port, volume, role...)
                                          │ deploy
                                          ▼
Cluster (nhóm logic, chạy trên capacity đã đăng ký)
   ├── Standalone task  (chạy một lần, vd batch)
   └── Service          (giữ đúng N task; task hỏng → scheduler tạo task thay thế)
                                          │
        Container agent (trên mỗi container instance EC2): báo trạng thái, chạy/dừng task
                                          │
Giám sát: CloudWatch, Runtime Monitoring
```

| Khái niệm | Định nghĩa |
|---|---|
| Container | Đơn vị phần mềm chứa code, runtime, công cụ và thư viện hệ thống |
| Image | Template chỉ đọc để tạo container, thường build từ Dockerfile |
| Registry | Nơi lưu image (vd Amazon ECR) |
| Task definition | Blueprint dạng JSON mô tả 1..n container |
| Cluster | Nhóm logic task/service, chạy trên capacity đã đăng ký |
| Task | Một thể hiện của task definition |
| Service | Duy trì số task mong muốn |
| Container agent | Chạy trên container instance, nói chuyện với ECS control plane |

Folder 05 là phần **thiết kế nền**. Các chủ đề được đi sâu ở folder khác (theo `05-99`):

```
05 Architect ─┬─ chọn compute      → 08 Fargate · 06 Managed Instances · 09 EC2
              ├─ task definition    → 13 Task definitions
              ├─ cluster, capacity  → 14 Cluster
              ├─ scale, deploy, LB  → 15 Schedule
              └─ traffic HTTPS vào  → 12 Gateway
```

Kiến trúc tham chiếu (gom khuyến nghị của cả folder):

```
                         Internet
                            │
            ┌───────────── VPC ───────────────────────────────────────────┐
            │  Public subnet (mỗi AZ):  ALB (TLS + WAF)    NAT gateway     │
            │                              │                 ▲             │
            │  Private subnet (AZ a,b,c):  ▼                 │ API ngoài   │
            │      Task service A ◄── Service Connect ──► Task service B   │
            │            │                                   │             │
            │            └──► VPC endpoint (ECR, S3, ECS, DynamoDB, Logs)  │
            └──────────────────────────────────────────────────────────────┘
   Bên dưới task: capacity provider (Fargate / Fargate Spot / EC2 ASG / Managed Instances)
   Quyền: task role + task execution role       Log: stdout → CloudWatch Logs / FireLens
```

---

## 2. Chọn compute: Fargate, Managed Instances, EC2

> Nguồn: `05-00` mục 1, `05-03`, `05-04`, `05-09-04`

| | **Managed Instances** | **Fargate** (serverless) | **EC2 instances** |
|---|---|---|---|
| Ai quản lý instance | AWS (cấp phát, vá lỗi, scale) — instance nằm trong account bạn | Không có instance để quản lý | Bạn (chọn instance, cấu hình, bảo trì) |
| Tính tiền | 🔎 theo instance + phí quản lý | Theo tài nguyên task dùng | 🔎 theo instance |
| Dùng khi | Muốn đơn giản như Fargate nhưng kiểm soát hơn; hiệu năng dự đoán được; tối ưu chi phí | Không muốn quản lý hạ tầng; workload biến động; mới bắt đầu | GPU/HPC; capacity reservation; instance type cụ thể; privileged; custom AMI |
| Edge (Local Zones, Wavelength, Outposts) | 🔎 kiểm tra docs | **Không** | Có |

Thêm biến thể **Fargate Spot**: dùng capacity Spot, có thể bị thu hồi sau **cảnh báo 2 phút** khi nhu cầu cao. Hợp batch, dev/staging, mọi thứ không cần HA và độ trễ thấp. Chạy chung với Fargate on-demand để có capacity burst rẻ hơn.

Cây quyết định:

```
Cần gì từ hạ tầng?
├─ Không muốn quản lý server, workload biến động, mới bắt đầu    → Fargate
├─ Batch, dev/staging, chịu được gián đoạn                       → Fargate Spot
├─ Đơn giản như Fargate nhưng kiểm soát hơn, hiệu năng ổn định    → Managed Instances
├─ GPU/HPC, custom AMI, privileged, capacity reservation          → EC2 + Auto Scaling group
└─ Local Zone / Wavelength / Outposts                             → EC2 (Fargate không chạy)
```

Hạ tầng được khai báo ở 3 nơi: khi **tạo cluster**, trong **task definition** (gọi là launch type), hoặc qua **capacity provider**.

---

## 3. Launch type và capacity provider

> Nguồn: `05-01`, `05-09-04`

### 3.1 Hai cách cấu hình capacity

| Cách | Các lựa chọn |
|---|---|
| **Launch type** | EC2, Fargate, External (và Managed Instances dùng trong `requiresCompatibilities`) |
| **Capacity provider** (**khuyến nghị**) | `FARGATE`, `FARGATE_SPOT`, Auto Scaling group (EC2 tự quản), Amazon ECS Managed Instances |

Phân vai theo best practice:

| Dùng | Ở đâu | Để làm gì |
|---|---|---|
| Launch type | `requiresCompatibilities` trong task definition | Khai báo task **tương thích** với hạ tầng nào (chọn được nhiều) |
| Capacity provider strategy | Khi chạy task / tạo service | Quyết định task **thực sự chạy** trên capacity nào |

### 3.2 Quy tắc

- `FARGATE` và `FARGATE_SPOT` được ECS **tạo sẵn** trên mọi cluster.
- Auto Scaling group và Managed Instances capacity provider: **bạn tạo** (một hoặc nhiều mỗi cluster).
- Spot Fleet chỉ có dưới dạng capacity provider, không có launch type.
- Một **cluster** chứa được mọi loại capacity provider; một **strategy** chỉ chứa **một loại**.
- Muốn chuyển service sang loại compute khác: chỉ cần cập nhật capacity provider strategy.

🔎 Strategy có `base` (số task tối thiểu đặt trên provider đó) và `weight` (tỉ lệ chia phần còn lại). Ví dụ ✍️:

```json
"capacityProviderStrategy": [
  { "capacityProvider": "FARGATE",      "base": 2, "weight": 1 },
  { "capacityProvider": "FARGATE_SPOT",           "weight": 3 }
]
```

→ 2 task đầu chạy Fargate on-demand, phần còn lại chia 1:3 giữa on-demand và Spot. Hai provider này cùng loại (Fargate) nên trộn được.

### 3.3 Đổi compute cho service (service mutability)

Quy trình:

```
1. Task definition: requiresCompatibilities có đích (vd MANAGED_INSTANCES)
      └─ không đạt kiểm tra tương thích → UpdateService thất bại
2. Tạo capacity provider (nếu dùng Auto Scaling group tuỳ chỉnh)
3. UpdateService: dùng capacity provider strategy thay cho launch type
4. Kiểm tra deployment thành công
5. Giám sát, chỉnh cài đặt capacity provider
```

| Từ \ Sang | Capacity provider | Launch type |
|---|---|---|
| **Capacity provider** | ✅ Mọi hướng giữa ASG, Fargate, Managed Instances | ❌ — **trừ** quay về đúng launch type ban đầu bằng `capacityProviderStrategy: []` |
| **Launch type** | ✅ EC2 / Fargate / External → Managed Instances, Fargate, ASG | ❌ EC2 ↔ Fargate — dùng capacity provider tương đương |

```
Tạo service với Fargate launch type
 → UpdateService sang Managed Instances          ✅
 → UpdateService với capacityProviderStrategy=[]  ✅ về lại Fargate launch type
 → muốn sang EC2 launch type                      ❌ (không phải launch type ban đầu)
```

⚠️ Kết luận thực tế: **bắt đầu ngay bằng capacity provider**, vì đi một chiều.

---

## 4. Đặt workload ở đâu: Region, AZ, Local Zones, Wavelength, Outposts, shared subnet

> Nguồn: `05-00` mục 3, `05-03`, `05-04`

### 4.1 Subnet được chọn khi nào

| Hạ tầng | Chọn subnet khi |
|---|---|
| EC2 instances | Tạo cluster / launch instance (task chạy trong subnet của instance) |
| Fargate, máy on-premises | Chạy task hoặc tạo service |

Subnet có thể ở: Availability Zones, Local Zones, Wavelength Zones, Regions, Outposts.

### 4.2 Local Zones, Wavelength, Outposts

| | Local Zones | Wavelength Zones | AWS Outposts |
|---|---|---|---|
| Đặt ở đâu | Mở rộng của Region, gần người dùng | Rìa mạng 5G của nhà mạng | Data center / co-location / on-prem của bạn |
| Mục đích | Độ trễ thấp | Độ trễ **cực** thấp cho thiết bị 5G | Chạy gần dữ liệu và ứng dụng on-prem |
| Tên ví dụ | `us-west-2-lax-1a` | `us-east-1-wl1-bos-wlz-1` | (Outpost ARN) |
| Các bước | Opt in → tạo VPC/subnet trong zone | Opt in → tạo VPC/subnet → launch EC2 | Cài Outpost → subnet gắn `--outpost-arn` |
| Tài nguyên cho ECS | EC2, FSx, ALB | EC2 | EC2 |
| Fargate | ❌ | ❌ | ❌ |

### 4.3 ECS trên Outposts

- ECR, IAM, NLB chạy ở **Region**, không trên Outpost → tăng độ trễ.
- **Mất kết nối** Outpost ↔ Region: cluster đang chạy vẫn chạy; nhưng **không** tạo cluster mới, **không** hành động mới, **không** tự thay instance lỗi, CloudWatch Logs agent không gửi được log.
- Cần kết nối tới Region **tin cậy, HA, độ trễ thấp**.
- Giảm độ trễ pull image: `ECS_IMAGE_PULL_BEHAVIOR=prefer-cached`.

Các bước tạo cluster (đã sửa ký tự lỗi của docs, xem mục 23):

```bash
# 1–2. IAM role + instance profile
aws iam create-role --role-name ecsRole --assume-role-policy-document file://ecs-policy.json
aws iam put-role-policy --role-name ecsRole --policy-name ecsRolePolicy --policy-document file://role-policy.json
aws iam create-instance-profile --instance-profile-name outpost
aws iam add-role-to-instance-profile --instance-profile-name outpost --role-name ecsRole

# 3–4. VPC + subnet GẮN VỚI OUTPOST
aws ec2 create-vpc --cidr-block 10.0.0.0/16
aws ec2 create-subnet --cidr-block 10.0.3.0/24 --vpc-id vpc-xxxxxxxx \
  --outpost-arn arn:aws:outposts:us-west-2:123456789012:outpost/op-xxxxxxxxxxxxxxxx \
  --availability-zone-id usw2-az1

# 5. Security group (CIDR phù hợp Outpost)
aws ec2 create-security-group --group-name MyOutpostSG
aws ec2 authorize-security-group-ingress --group-name MyOutpostSG --protocol tcp --port 22 --cidr 10.0.3.0/24
aws ec2 authorize-security-group-ingress --group-name MyOutpostSG --protocol tcp --port 80 --cidr 10.0.3.0/24

# 6. Tạo cluster (docs không ghi lệnh; 🔎)
aws ecs create-cluster --cluster-name MyCluster
```

```bash
# 7. userdata.txt — cấu hình agent
#!/bin/bash
cat << 'EOF' >> /etc/ecs/ecs.config
ECS_CLUSTER=MyCluster
ECS_IMAGE_PULL_BEHAVIOR=prefer-cached
ECS_CONTAINER_INSTANCE_TAGS={"environment": "Outpost"}
EOF
```

```bash
# 8. Launch container instance trên Outpost (instance type phải có trên Outpost)
aws ec2 run-instances --count 1 --image-id ami-xxxxxxxx --instance-type c5.large \
  --key-name aws-outpost-key --subnet-id subnet-xxxxxxxxxxxxxxxxx \
  --iam-instance-profile Name=outpost --security-group-ids sg-xxxxxx \
  --associate-public-ip-address --user-data file://userdata.txt
```

⚠️ 🔎 Ví dụ của docs mở port 22. Ở production nên dùng SSM Session Manager thay vì SSH.

### 4.4 Shared subnet (VPC sharing trong AWS Organizations)

| | EC2 | Fargate |
|---|---|---|
| Owner phải share subnet trước | ✅ | ✅ |
| Không dùng SG mặc định của VPC (thuộc owner) | Cho container instance | Khi tạo service / chạy task |
| Không dùng SG của participant khác / owner | Khi launch instance | Khi tạo service / chạy task |
| Mỗi bên tự quản SG; owner chỉ **xem** SG của participant | ✅ | ✅ |
| Owner **không** xem / sửa / xoá cluster của participant | ✅ | ✅ |

---

## 5. Mạng (1): task đi ra internet

> Nguồn: `05-06-01`, `05-00` mục 3

### 5.1 Public subnet + internet gateway

- Host/task có **IP public**, định tuyến được từ internet → giao tiếp **hai chiều**.
- ⚠️ Phải siết SG/firewall; ví dụ **không mở port 22** (bot quét IP public và brute-force SSH).
- Hợp: ứng dụng public cần băng thông lớn, độ trễ tối thiểu (streaming video, game).

| Hạ tầng / mode | Ra internet qua IGW |
|---|---|
| EC2 `host`, `bridge` | ✅ dùng IP public của host |
| EC2 `awsvpc` | ❌ ENI của task **không** được cấp IP public → phải đi qua NAT |
| Fargate + **Assign public IP** | ✅ mỗi task một IP public |

### 5.2 Private subnet + NAT gateway

- Task có IP private; bên ngoài **không** kết nối vào được; ra ngoài qua NAT (bên ngoài thấy IP public của NAT).
- Hợp: bảo vệ container (thanh toán, dữ liệu/mật khẩu người dùng).
- Chi phí: theo giờ + theo GB xử lý; nên **1 NAT mỗi AZ** để AZ sập không ảnh hưởng đường ra.
- Fargate: private subnet, **không** bật Assign public IP.

### 5.3 So sánh

| | Public + IGW | Private + NAT |
|---|---|---|
| IP | Public | Private |
| Chiều | Hai chiều | Chỉ ra |
| Rủi ro | Bị quét, bị thử kết nối | Được bảo vệ khỏi truy cập trực tiếp |
| Chi phí NAT | — | Có, 1 NAT / AZ |

---

## 6. Mạng (2): nhận traffic từ internet vào

> Nguồn: `05-06-02`

Docs **không khuyến nghị** đặt container ở public subnet để nhận traffic trực tiếp khi quy mô lớn. Nên có **lớp đầu vào scale được**:

```
Internet → [ALB | NLB | API Gateway] (public) → IP private của task (private subnet)
```

### 6.1 Application Load Balancer (tầng 7)

- Hợp: website, HTTP REST API.
- Ưu điểm: **TLS termination**; **định tuyến theo host/path** (một ALB cho nhiều service); **gRPC, websocket** (HTTP/2); **bảo mật** (chống HTTP desync, tích hợp **AWS WAF** chặn SQL injection/XSS).

### 6.2 Network Load Balancer (tầng 4)

- Hợp: giao thức không phải HTTP, **UDP**, cần **mã hoá đầu-cuối** (NLB không đọc nội dung gói).
- Cũng terminate TLS được.
- ⚠️ **Đóng kết nối**: NLB không biết giao thức tầng trên nên không gửi được thông điệp đóng kết nối. Ba cách:
  1. Ứng dụng tự đóng.
  2. Bật connection termination của target group: NLB đóng kết nối tầng 4 khi task dừng (client có thể thấy lỗi).
  3. Đặt **deregistration delay > timeout của client**: client timeout trước, tự kết nối lại sang task mới; task cũ drain dần.

### 6.3 API Gateway HTTP API

- Hợp: HTTP có traffic **thấp** hoặc **đột biến**.
- Tính phí **theo request** (không có request = 0 đồng). ALB/NLB tính **theo giờ**. Tải cao thì ALB/NLB rẻ hơn mỗi request.
- Nối tới task qua **VPC link**, tìm IP qua **Cloud Map** (do ECS Service Discovery quản lý).
- Thêm: phân quyền client, usage tier, sửa request/response, cache response, endpoint edge/regional/private, **Lambda authorizer**.

### 6.4 So sánh

| | ALB | NLB | API Gateway |
|---|---|---|---|
| Tầng | 7 | 4 | (HTTP) |
| Phù hợp | Website, REST, gRPC, websocket | Non-HTTP, UDP, E2E encryption | HTTP traffic thấp / đột biến |
| Tới task | IP private | IP private | VPC link + Cloud Map |
| Path routing | ✅ (+ host) | — | ✅ |
| Bảo mật | WAF, desync mitigation | — | Authorizer, phân quyền client |
| Tính phí | Theo giờ | Theo giờ | Theo request |

Chi tiết ECS + ELB: xem file toàn tập *Load balancing* ở folder 15.

---

## 7. Mạng (3): gọi dịch vụ AWS từ trong VPC

> Nguồn: `05-06-03`

Những đích phải tới được:

| Ai gọi | Gọi tới | Để làm gì |
|---|---|---|
| Container agent | ECS control plane | ECS hoạt động |
| Host / task | ECR endpoint | Lấy image |
| Host / task | **Amazon S3** | Tải **layer** của image |
| App | DynamoDB, SQS… | Tuỳ ứng dụng |

### 7.1 NAT gateway — dễ nhất

Nhược điểm:
- **Không giới hạn được đích** (muốn chặn thì phải cắt mọi traffic ra).
- Tính phí **mỗi GB** — kể cả tải S3, query DynamoDB, **pull image ECR**.
- Băng thông **5 Gbps, tự scale tới 45 Gbps** mỗi NAT. Cần hơn → chia workload ra nhiều subnet, mỗi subnet một NAT.

### 7.2 AWS PrivateLink (VPC endpoint)

- Endpoint tạo **ENI trong subnet**; định tuyến VPC gửi traffic tới hostname dịch vụ qua ENI đó.
- Traffic **không rời mạng Amazon**; không cần IGW, NAT, VPN, Direct Connect, IP public.
- Endpoint hay dùng: **S3 gateway endpoint**, DynamoDB, **ECS**, **ECR**.

| Đích | Endpoint |
|---|---|
| ECS control plane | ECS VPC endpoint (🔎 `ecs`, `ecs-agent`, `ecs-telemetry`) |
| ECR | ECR VPC endpoint (🔎 `ecr.api`, `ecr.dkr`) |
| S3 (layer image) | **Gateway** endpoint S3 |
| DynamoDB | DynamoDB VPC endpoint |
| 🔎 Log, secret | `logs`, `secretsmanager`, `ssm` |

| | NAT | VPC endpoint |
|---|---|---|
| Độ dễ | Dễ nhất | Tạo từng endpoint |
| Giới hạn đích | Không | Chỉ dịch vụ có endpoint |
| Chi phí | Mỗi GB qua NAT | Không chịu phí NAT; 🔎 endpoint interface có phí giờ + GB riêng (gateway endpoint S3/DynamoDB miễn phí) |

💬 Thực tế: dùng **cả hai** — NAT cho API bên thứ ba, VPC endpoint cho ECR/S3/Logs để giảm phí NAT và giữ traffic private.

---

## 8. Mạng (4): các service gọi nhau

> Nguồn: `05-06-04`

### 8.1 Service Connect — khuyến nghị

- App dùng **tên ngắn + port chuẩn**; phạm vi: cùng cluster, khác cluster, **khác VPC cùng Region**.
- ECS lo hết: tạo tên, cập nhật mục khi task bật/tắt, chạy **agent trong mỗi task**. App dùng DNS chuẩn thì **không cần sửa code**.
- Thay đổi áp dụng **qua deployment** (thay task client), không phụ thuộc TTL DNS; dùng được circuit breaker.

### 8.2 Service discovery (Cloud Map, DNS)

- ECS đồng bộ danh sách task sang Cloud Map → DNS hostname phân giải ra IP task.
- Ưu: **độ trễ thấp**, không thành phần trung gian.
- `awsvpc`: bản ghi **A** đủ. `bridge` (chung IP, port động): cần **SRV** — app phải hỗ trợ.
- `awsvpc` cho **SG riêng mỗi service**, chỉ mở cho upstream cần thiết.
- Nhược: app **tự retry, tự bỏ qua backend hỏng**; TTL → có thể phân giải ra task đã chết.
- ⚠️ Giới hạn **1.000 task / service** (mục 19).

### 8.3 Internal load balancer

- Nằm hoàn toàn trong VPC; health check, tự gỡ target hỏng; **mọi network mode** (IP hoặc IP+port).
- Nhược duy nhất: **chi phí** (tài nguyên mỗi AZ + phí traffic). Giảm bằng **ALB dùng chung** với path rule: `/api/user/*` → user, `/api/order/*` → order.

### 8.4 So sánh

| | Service Connect | Cloud Map DNS | Internal LB |
|---|---|---|---|
| Docs | **Khuyến nghị** | Lựa chọn khác | Lựa chọn khác |
| Ở giữa | Agent (proxy) trong task | Không có | Load balancer |
| Đổi đích | Theo deployment | Chờ TTL | LB cập nhật pool |
| Retry / backend hỏng | 🔎 proxy retry + outlier detection | App tự làm | LB health check |
| Network mode | 🔎 `awsvpc`, `bridge` | `awsvpc` (A) / `bridge` (SRV) | Mọi mode |
| Chi phí | 🔎 không phí riêng (tốn CPU/memory cho proxy) | Phí Cloud Map | LB theo giờ + traffic |

Chi tiết: file toàn tập *Service Connect* ở folder 15.

---

## 9. Mạng (5): nhiều account, nhiều VPC, on-premises

> Nguồn: `05-06-05`

| Dịch vụ | Nối | Ghi chú |
|---|---|---|
| **AWS Transit Gateway** | VPC ↔ VPC, account ↔ account, on-prem | **Cân nhắc đầu tiên**; hub trung tâm |
| **Site-to-Site VPN** | On-prem ↔ VPC | |
| **VPC peering** | VPC ↔ VPC (cùng/khác account) | 🔎 không bắc cầu (non-transitive), CIDR không được trùng |
| **Shared VPC** | Nhiều account dùng chung VPC/subnet | Xem mục 4.4 |

```
          Transit Gateway (hub)
       ┌─────────┼──────────┐
   VPC team A  VPC team B  on-premises
```

🔎 Service Connect cũng hỗ trợ namespace chia sẻ qua AWS RAM giữa các account (xem file Service Connect).

---

## 10. Service endpoint và dual-stack (IPv6) cho ECS API

> Nguồn: `05-00` mục 2, `05-02`

- Endpoint mặc định của ECS API: **chỉ IPv4**.
- **Dual-stack endpoint**: IPv4 + IPv6, dạng **`ecs.<region>.api.aws`** (luôn theo Region).
- Đây là endpoint để **gọi ECS API** (CLI, SDK), không phải traffic ứng dụng.

| Cách | Phạm vi |
|---|---|
| `use_dualstack_endpoint = true` trong `~/.aws/config` | Mọi lệnh `ecs` |
| `--endpoint-url https://ecs.<region>.api.aws` | Từng lệnh |
| Hai profile (có / không dual-stack) | Theo profile |

```bash
aws ecs list-clusters --endpoint-url https://ecs.us-east-1.api.aws
```

```java
// Java SDK 2.x — bật cờ
EcsClient client = EcsClient.builder().region(Region.US_EAST_1).dualstackEnabled(true).build();
```

```go
// Go SDK (v1) — ghi đè endpoint
svc := ecs.New(sess, &aws.Config{
    Region:   aws.String(endpoints.UsEast1RegionID),
    Endpoint: aws.String("https://ecs.us-east-1.api.aws"),
})
```

REST: đặt `Host: ecs.us-east-1.api.aws` trong request.

---

## 11. IAM roles

> Nguồn: `05-11`, `05-00` mục 5

| Role | Ai dùng | Khi nào cần |
|---|---|---|
| **Task execution role** | ECS (thay bạn) | Fargate/external: pull **ECR private** (kể cả cross-account), log **`awslogs`**. Fargate/EC2: **private registry auth**, **Runtime Monitoring**, **secret** (Secrets Manager / SSM) |
| **Task role** | **Code app** | App gọi dịch vụ AWS (S3…) |
| **Container instance role** | EC2 / external instance | Đăng ký instance vào cluster |
| **ECS Anywhere role** | External instance | Gọi AWS API từ on-prem |
| **Infrastructure for load balancers role** | ECS | **Blue/green deployment** của ECS |
| **CodeDeploy role** | CodeDeploy | **CodeDeploy blue/green** |
| **EventBridge role** | EventBridge | Lên lịch task bằng rule/target |
| **Infrastructure role** | ECS | **Managed Instances**; gắn **EBS** cho task; **TLS Service Connect**; target group **VPC Lattice** |
| **Instance profile** | Managed Instances | Assume infrastructure role |

Theo hạ tầng:

| Hạ tầng | Role cần |
|---|---|
| Fargate | Execution role (ECR private, awslogs, secret…) + task role (nếu app gọi AWS) |
| EC2 | Container instance role + execution role + task role |
| External (Anywhere) | Container instance role + Anywhere role + execution role + task role |
| Managed Instances | Infrastructure role + instance profile + task role |

```
             Task execution role                     Task role
ECS agent ─────────────────────► ECR, awslogs,   App code ──────────► S3, DynamoDB, SQS...
                                 Secrets/SSM
```

🔎 Thêm: **service-linked role** `AWSServiceRoleForECS` (ECS tự tạo) — dùng để gọi EC2/ELB/Cloud Map thay bạn (chính là `userIdentity` trong ví dụ lỗi throttling ở mục 19).

💬 Mỗi service một task role, quyền tối thiểu.

---

## 12. Account settings (feature access) và logging

> Nguồn: `05-00` mục 4, 6

| Account setting | Dùng để |
|---|---|
| **Container Insights** | Metric + log (CPU, memory, đĩa, mạng) |
| **`awsvpc` trunking** | Thêm ENI trên một số instance type → nhiều task `awsvpc` hơn |
| **Tagging authorization** | Kiểm tra thêm `ecs:TagResource` khi tạo tài nguyên có tag |
| **Fargate FIPS-140** | Chuẩn mật mã cho hệ thống cần FISMA/FedRAMP |
| **Fargate task retirement time** | Chỉnh thời gian chờ trước khi task Fargate bị retire để vá |
| **Dual stack VPC** | Task dùng IPv4, IPv6 hoặc cả hai |
| **ARN format** | Định dạng ARN mới (cần cho tagging authorization…) |

🔎 Bật bằng `aws ecs put-account-setting` (cho principal hiện tại) hoặc `put-account-setting-default` (mặc định cho account), vd `--name containerInsights --value enabled`.

Logging:

| Lựa chọn | Log đi đâu |
|---|---|
| CloudWatch Logs (`awslogs`) | CloudWatch |
| FireLens | Dịch vụ AWS khác hoặc đối tác (AWS Partner Network) |

---

## 13. Container image best practices

> Nguồn: `05-05`, `05-00` mục 7, `05-09-03`

| Nguyên tắc | Vì sao |
|---|---|
| **Image đầy đủ**: mọi dependency là file tĩnh trong image; đổi gì → build image mới | Task thay thế khởi động được ở bất kỳ đâu |
| **Một tiến trình mỗi container** | Vòng đời container = vòng đời tiến trình; crash → ECS thay |
| **Bắt `SIGTERM`** | ECS gửi SIGTERM rồi SIGKILL. App phải: ngừng nhận việc mới → làm nốt việc dở → (nếu quá lâu) lưu việc dở ra ngoài task |
| **Log ra stdout/stderr** | Đổi hệ thống log ở tầng hạ tầng, không phải build lại |
| **Tag duy nhất mỗi build = git SHA**; `latest` chỉ để test | Biết chính xác code nào đang chạy |
| **Immutable image tags** trong ECR | Tag không bị ghi đè sang image khác |
| Không cần build image cho **mọi** commit | Chỉ build cho commit phát hành lên production |

Giảm kích thước image (để scale nhanh):
- Static binary / Go: `FROM scratch`, chỉ chứa binary.
- Base image tối giản (Amazon Linux, Ubuntu).
- **Multi-stage build**, không để artifact build trong image cuối.
- Gộp `RUN` bằng `&&` → ít layer.
- Chỉ đưa dữ liệu cần để khởi động; phần còn lại lấy từ S3.
- Image ở **ECR cùng Region** (hoặc ECR cross-region replication); tránh kéo qua Internet/VPN.

✍️ Dockerfile mẫu Spring Boot (multi-stage):

```dockerfile
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY . .
RUN ./mvnw -q -DskipTests package && cp target/*.jar /app.jar

FROM eclipse-temurin:21-jre
COPY --from=build /app.jar /app.jar
USER 1000
# exec form → java là PID 1, nhận SIGTERM trực tiếp
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app.jar"]
```

🔎 Spring Boot: bật `server.shutdown=graceful` để xử lý SIGTERM; ⚠️ dùng shell form (`ENTRYPOINT java -jar ...`) thì `sh` là PID 1 và có thể không chuyển SIGTERM.

**Một hay nhiều container trong một task definition (Fargate):** gộp khi container cần **ít nhất một** trong các điều: bật/tắt cùng nhau, gọi nhau qua `localhost`, chia sẻ tài nguyên, chia sẻ volume. Ngược lại → tách để scale riêng.

---

## 14. Kích thước task

> Nguồn: `05-09-01`, `05-09-05`, `05-07` mục 4

- CPU: 1024 unit = 1 vCPU. Memory: MB/MiB.
- **Reservation** = tối thiểu, ECS **đảm bảo** (không đặt task lên instance không đủ). **Bursting** = dùng vượt reservation nhưng trong limit. **Limit** = tối đa; CPU vượt → **throttle**, memory vượt → **dừng container**.
- Chọn số bằng **load test**.

| Loại app | Cách chọn |
|---|---|
| Stateless scale ngang | Đo memory khi phục vụ request (`ps`, `top`, Container Insights); CPU nhỏ (256) = scale mịn, rẻ, nhưng có thể không kịp spike; CPU lớn = nhanh, đắt |
| Không scale ngang (singleton, DB) | Theo load test để đạt SLO; ưu tiên capacity và chi phí |

**Fargate**: CPU/memory **mức task bắt buộc** (không cần cộng overhead); mức container tuỳ chọn. Limit ≥ mọi reservation → thường đặt = **tổng reservation các container**, rồi **làm tròn lên** kích thước Fargate gần nhất.

**EC2**: chọn instance type theo reservation để bin-pack khít. Ví dụ docs: 4 task × (0,5 vCPU, 2 GB) = 2 vCPU, 8 GB = đúng **m5.large**.

---

## 15. Service auto scaling: chọn metric đúng

> Nguồn: `05-09-02`

### 15.1 Cơ chế

```
CloudWatch metric ─► CloudWatch alarm ─► Application Auto Scaling ─► desired count của service
```

| Kiểu | Bạn cung cấp |
|---|---|
| **Target tracking** | Một giá trị mục tiêu (vd CPU 70%) |
| **Step scaling** | Các ngưỡng + số task thêm/bớt mỗi ngưỡng (phản ứng nhanh hơn) |
| 🔎 Scheduled scaling | Lịch cố định (dùng cho sự kiện biết trước, mục 16) |

### 15.2 Metric tốt có 2 tính chất

| Tính chất | Kiểm tra |
|---|---|
| Tương quan với nhu cầu | Giữ tài nguyên, đổi tải → metric đổi theo |
| Tỉ lệ với capacity | Giữ tải, **gấp đôi task** → metric **giảm một nửa** |

Quy trình load test:

```
1. Dashboard: CPU, memory, I/O, I/O queue depth, network + thời gian phản hồi
2. Tải nhỏ, giữ vài phút (warm up) → tăng chậm, giữ vài phút → lặp tới khi vỡ SLO
3. Metric nào tăng theo tải → ứng viên
4. Tài nguyên nào bão hoà / cạn trước → chọn metric của nó
5. Gấp đôi task, chạy lại ở tải đỉnh → metric giảm một nửa? → dùng được
```

Ví dụ docs: CPU 80% ở 100 req/s → gấp đôi task → CPU ~40% ✅ dùng CPU; vẫn 80% ❌ tìm metric khác.

### 15.3 Metric theo mô hình ứng dụng

| Mô hình | Metric khuyến nghị | Không nên | EC2 gợi ý |
|---|---|---|---|
| CPU-bound hiệu quả | CPU utilization | — | `c5`, `c6g` |
| Memory-bound hiệu quả (giải phóng memory sau request) | Memory utilization | Memory nếu app không giải phóng | `r5`, `r6g` |
| Worker-based (Apache, Gunicorn) | **Mức đồng thời trung bình** (custom metric ≥ 1 lần/phút; tổng / số replica) hoặc ALB `ActiveConnectionCount` / số replica | Số đếm thô | — |
| Waiting server (chờ downstream) | Memory trung bình hoặc đồng thời trung bình | — | — |
| **Java** | **CPU**; cạn heap ở đồng thời cao → số kết nối TB; cạn heap ở throughput cao → request rate TB | **Memory** (JVM 8u191+ tự chiếm heap lớn) | — |
| .NET, Ruby (GC) | CPU nếu CPU-bound; không thì throughput / đồng thời TB | Memory | — |
| Job processor | **Queue depth**: SQS `ApproximateNumberOfMessagesVisible`; Kinesis `MillisBehindLatest` (TB mọi consumer) | — | — |

Worker-based chạy tốt khi độ lệch chuẩn độ trễ nhỏ và trung bình gần **p95**; nếu không, backlog có thể tràn → thêm replica.

🔎 Với job processor, nhiều team dùng metric "backlog per task" = số message / số task đang chạy để target tracking.

✍️ 🔎 Ví dụ target tracking CPU bằng CLI:

```bash
aws application-autoscaling register-scalable-target \
  --service-namespace ecs --resource-id service/<CLUSTER>/<SERVICE> \
  --scalable-dimension ecs:service:DesiredCount --min-capacity 3 --max-capacity 30

aws application-autoscaling put-scaling-policy \
  --service-namespace ecs --resource-id service/<CLUSTER>/<SERVICE> \
  --scalable-dimension ecs:service:DesiredCount --policy-name cpu70 \
  --policy-type TargetTrackingScaling \
  --target-tracking-scaling-policy-configuration '{
    "TargetValue": 70,
    "PredefinedMetricSpecification": {"PredefinedMetricType": "ECSServiceAverageCPUUtilization"},
    "ScaleOutCooldown": 60, "ScaleInCooldown": 300 }'
```

---

## 16. Capacity, tính sẵn sàng và cú sốc nhu cầu

> Nguồn: `05-09-03`

### 16.1 Autoscaling luôn trễ

```
metric → CloudWatch tổng hợp (có thể vài phút) → alarm phải vượt vài phút → cấp task mới
      → (EC2) chờ instance warm-up → pull image → app khởi động
```

→ Phải **cấp dư (headroom)**:

| Lý do | Cách |
|---|---|
| Đỡ đợt tăng ngắn trong lúc scale còn trễ | Target **60–80%** utilization |
| Chịu sự cố một AZ | **2 AZ → 2×** task; **3 AZ → 1,5×** task (3 task cho mỗi 2 task cần) |

| Số AZ | Chạy | Mất 1 AZ còn | So với nhu cầu |
|---|---|---|---|
| 2 | 2× | 1× | Vừa đủ |
| 3 | 1,5× | 1× | Vừa đủ |

### 16.2 Tối đa tốc độ scale

| Cách | Ghi chú |
|---|---|
| Image nhỏ | Mục 13 |
| Image gần (ECR cùng Region) | Tránh Internet/VPN |
| Giảm ngưỡng health check LB | Mặc định có thể ≥ **90 giây**; giảm interval + threshold |
| Cold-start (JIT, Java) | Viết lại phần nhạy độ trễ bằng ngôn ngữ không bị phạt cold-start (🔎 hoặc dùng CDS / AOT để giảm thời gian warm-up của JVM) |
| **Step scaling** thay target tracking | Phản ứng nhanh hơn, giảm thời gian alarm ở trạng thái vi phạm |
| EC2: instance lớn hơn, EBS nhanh hơn | Throughput mạng/EBS tăng theo size; `gp3` chỉ định throughput/IOPS |
| EC2: network mode `bridge` | Nhanh hơn `awsvpc` (không gắn ENI), mất SG riêng mỗi task |

### 16.3 Cú sốc nhu cầu

| Loại | Khuyến nghị |
|---|---|
| **Biết trước** (sale, sự kiện) | Lịch chung giữa các team; **scheduled scaling** (out trước, in sau); Enterprise Support → TAM nâng quota, **prewarm LB** |
| **Không biết trước** | Cấp dư tới mức tối đa dự kiến; **leading indicator** để scale out sớm; **chế độ hiệu năng cao** (vd trả trang tĩnh); **tách monolith** để scale riêng phần quan trọng |

✍️ 🔎 Scheduled scaling trước đợt sale:

```bash
aws application-autoscaling put-scheduled-action \
  --service-namespace ecs --resource-id service/<CLUSTER>/<SERVICE> \
  --scalable-dimension ecs:service:DesiredCount --scheduled-action-name sale-up \
  --schedule "at(2026-11-11T00:00:00)" --scalable-target-action MinCapacity=50,MaxCapacity=200
```

---

## 17. Cluster capacity và tăng tốc Cluster Auto Scaling (EC2)

> Nguồn: `05-09-04`, `05-09-06`

### 17.1 Cung cấp capacity

- Tự launch EC2 và đăng ký qua agent → **tự lo scale** (khó).
- Khuyến nghị **capacity provider**:

| Provider | Đặc điểm |
|---|---|
| Fargate | On-demand; mỗi task **1 tài nguyên Fargate riêng** đúng CPU/memory mức task (1:1) |
| Fargate Spot | Spot; gián đoạn sau cảnh báo **2 phút**; chạy chung Fargate để burst rẻ |
| EC2 (ASG) | Gắn 1 Auto Scaling group; **Cluster Auto Scaling (CAS)** chỉnh ASG để mọi task đã lên lịch đều đặt được |

### 17.2 Vì sao CAS chậm và cách tăng tốc

Độ trễ: đẩy metric CloudWatch → chờ **`CapacityProviderReservation`** vượt alarm → chờ instance mới **warm-up**.

| Tham số | Mặc định | Hành động |
|---|---|---|
| `minimumScalingStepSize` | **1** instance | **Tăng** để mỗi lần thêm nhiều instance |
| `maximumScalingStepSize` | (không nêu) | Đừng để quá thấp |
| `instanceWarmupPeriod` | **300 s** | **Giảm** để instance được tính vào metric sớm |
| `targetCapacity` | 🔎 100 | Đặt < 100 (vd **80** → 20% dự phòng) để task chạy ngay, không bị throttle; đổi lại tốn tiền |

Chỉnh qua API `CreateCapacityProvider` / `UpdateCapacityProvider`.

Cách thay thế: dư ở **service** (tăng replica qua target tracking / step scaling) — chỉ giúp workload **đột biến**, **không** giúp khi deploy service mới từ 0 lên N.

🔎 `CapacityProviderReservation` = (số instance cần / số instance đang chạy) × 100; CAS cố giữ nó bằng `targetCapacity`.

✍️ 🔎 Tạo capacity provider EC2:

```bash
aws ecs create-capacity-provider --name cp-ondemand \
  --auto-scaling-group-provider '{
    "autoScalingGroupArn": "arn:aws:autoscaling:<REGION>:<ACCOUNT>:autoScalingGroup:...:autoScalingGroupName/ecs-asg",
    "managedScaling": {
      "status": "ENABLED", "targetCapacity": 80,
      "minimumScalingStepSize": 2, "maximumScalingStepSize": 20,
      "instanceWarmupPeriod": 120 },
    "managedTerminationProtection": "ENABLED" }'
```

---

## 18. Tối ưu thời gian khởi chạy task

> Nguồn: `05-07`

| Khuyến nghị | Áp dụng | Ý chính |
|---|---|---|
| `ECS_IMAGE_PULL_BEHAVIOR=prefer-cached` | EC2 | Có cache thì dùng, không thì pull; **tắt dọn image tự động** |
| Placement `binpack` | EC2 | Mật độ task cao → nhiều task hưởng cache; lợi nhất với image **Windows** (hàng chục GB) |
| **ENI trunking** | EC2 + `awsvpc` | c5.large: **2 → 10** task |
| Network mode | — | `awsvpc` thêm **vài giây** (gọi EC2 API cấp + gắn ENI) nhưng có SG mỗi task; ưu tiên tốc độ deploy → `bridge` |
| Đo vòng đời khởi chạy | — | Task metadata endpoint: so `StartedAt` của container với `StartedAt` của task → biết phần thời gian do app |
| Instance type khớp reservation | EC2 | 4 × (0,5 vCPU, 2 GB) = m5.large |

🔎 Fargate không có cache image giữa các task → dùng **SOCI lazy loading** (xem file toàn tập Task definition).

⚠️ `prefer-cached` + tag `latest` = có thể chạy image cũ. Đi kèm tag git SHA (mục 13).

---

## 19. Quota và throttling ở quy mô lớn

> Nguồn: `05-08-01`, `05-08-02`

### 19.1 Quota

| Quota | Giá trị | Điều chỉnh? | Ghi chú |
|---|---|---|---|
| Task Fargate đồng thời / Region | Theo account | Có | Tính cả On-Demand, Spot và **EKS pod trên Fargate** |
| EC2 instance / cluster | **5.000** | Có / thêm cluster | Có thể thành nút thắt khi deploy |
| `Tasks in the PROVISIONING state per cluster` | Theo cluster | — | Deploy hàng chục service × hàng trăm task, cluster thiếu capacity → throttle, back-off → chậm. Tránh: deploy lượng không cần thêm capacity, **hoặc giữ capacity dư** |
| ALB: `Targets per Application Load Balancer`, `Targets per Target Group per Region` | | Có | |
| NLB: `Targets per Availability Zone Per Network Load Balancer` (khi cross-zone) | Chặt hơn ALB | Có | |
| `Network interfaces per Region`, `Elastic IP addresses per Region` | | Có | |
| `Maximum network interfaces` / instance | Theo instance type | **Không** | Dùng ENI trunking |
| **Task / service khi dùng Cloud Map** | **1.000** | **Không** | Do quota `Instances per service` của Cloud Map |

Phép tính ENI trunking (c5.large):

| | Không trunking | Có trunking |
|---|---|---|
| ENI tối đa | 3 | 12 |
| − ENI chính | −1 | −1 |
| − ENI trunk | — | −1 |
| **Task `awsvpc`** | **2** | **10** |

### 19.2 API ECS gọi thay bạn (dễ bị throttle)

| Dịch vụ | API |
|---|---|
| ELB | `RegisterTarget(s)`, `DeregisterTarget(s)`, `DescribeTargetHealth` |
| EC2 (ENI) | `CreateNetworkInterface`, `AttachNetworkInterface`, `DetachNetworkInterface`, `DeleteNetworkInterface`, `DescribeNetworkInterfaces`, `DescribeVpcs`, `DescribeSubnets`, `DescribeSecurityGroups`, `DescribeInstances` |
| Cloud Map | `ListInstances`, `GetInstancesHealthStatus`, `RegisterInstance`, `DeregisterInstance` (giới hạn **không công bố**) |

Dấu hiệu: service event `Operations are being throttled. Will try again later.` Scheduler tự retry với back-off cấp số nhân → deploy chậm.

### 19.3 Hai loại throttling

| | Đồng bộ | Bất đồng bộ |
|---|---|---|
| Ai gọi | Bạn (CLI, SDK, pipeline) | ECS / CloudFormation thay bạn |
| Nhận biết | Lỗi ngay trong response | Service event, CloudTrail, EventBridge |
| Xử lý | Xử lý lỗi; **retry + exponential backoff + jitter** (SDK có sẵn, cấu hình được) | Giám sát; Step Functions retry `RunTask`; nhờ Support nâng giới hạn |

⚠️ **Standalone task**: `RunTask` bất đồng bộ và **không tự retry** → dùng Step Functions + EventBridge.

### 19.4 Giám sát throttling

| Công cụ | Dùng để |
|---|---|
| CloudTrail → CloudWatch Logs Insights | API nào bị throttle, ai gọi, bao nhiêu lần |
| Athena trên log CloudTrail | SQL đếm `ThrottlingException` |
| EventBridge | Sự kiện `ECS_OPERATION_THROTTLED`, `SERVICE_DISCOVERY_OPERATION_THROTTLED` → Lambda phản ứng |
| CloudWatch namespace `Usage` (By AWS Resource, type API, metric `CallCount`) | Alarm khi gần ngưỡng; kết hợp **anomaly detection** |

Truy vấn Athena (đã sửa thứ tự mốc thời gian của docs):

```sql
SELECT eventname, errorcode, eventsource, awsregion, useragent, COUNT(*) AS count
FROM cloudtrail_table_name
WHERE errorcode = 'ThrottlingException'
  AND eventtime BETWEEN '2024-09-23T00:00:00Z' AND '2024-09-24T00:00:00Z'
GROUP BY errorcode, awsregion, eventsource, useragent, eventname
ORDER BY count DESC;
```

🔎 Với SDK Java v2 có thể chọn retry mode `adaptive` / `standard`; CLI đặt `AWS_RETRY_MODE=adaptive` và `AWS_MAX_ATTEMPTS`.

---

## 20. Troubleshooting mạng

> Nguồn: `05-06-06`

| Công cụ / cài đặt | Giúp gì |
|---|---|
| **Container Insights** | Metric mạng (cạnh CPU, memory, đĩa) trên dashboard tự động |
| **AWS X-Ray** | Trace request giữa service và tới AWS endpoint: thời gian, mã phản hồi, service map, đi sâu từng transaction → tìm bottleneck |
| **VPC Flow Logs** | Log mọi kết nối ở mức ENI (gồm ELB, RDS, NAT) → kết nối có đi được không |
| **`nofile` ulimit** | Mỗi socket = 1 file descriptor; quota thấp → lỗi kết nối. Chỉnh trong task definition (EC2: có thể cả trên instance) |
| **`sysctl` net** | Buffer đọc/ghi; hữu ích trên instance lớn nhiều container |

🔎 Bảng tra nhanh triệu chứng mạng:

| Triệu chứng | Kiểm tra |
|---|---|
| Task không pull được image | Có đường ra không: public IP / NAT / VPC endpoint ECR **và S3**; execution role |
| Task EC2 `awsvpc` không ra internet ở public subnet | ENI task không có IP public → cần NAT |
| ALB báo target unhealthy | SG của task cho phép SG của ALB vào port app; health check path |
| Service A gọi B lúc được lúc không (Cloud Map) | TTL DNS trỏ task đã chết; app thiếu retry |
| Deploy chậm, event "throttled" | Mục 19 |
| Lỗi "too many open files" | `nofile` ulimit |
| Phí NAT tăng vọt | Pull image / S3 qua NAT → thêm VPC endpoint |

---

## 21. Triển khai: kiến trúc tham chiếu từng bước

> ✍️ / 🔎 Ghép các khuyến nghị của folder thành một quy trình; lệnh CLI là kiến thức ngoài folder — kiểm tra lại với `aws <service> <lệnh> help`. Production nên làm bằng IaC (CloudFormation/CDK/Terraform).

### 21.1 Mục tiêu

- Service `order-api` (HTTP) + `user-api` (nội bộ), Fargate, 3 AZ.
- Vào: ALB public. Ra: NAT 1/AZ. AWS: VPC endpoint. Service gọi nhau: Service Connect.

### 21.2 Mạng

```
VPC 10.0.0.0/16
├── Public  10.0.0.0/24 (a)  10.0.1.0/24 (b)  10.0.2.0/24 (c)   → IGW, ALB, NAT×3
└── Private 10.0.10.0/24 (a) 10.0.11.0/24 (b) 10.0.12.0/24 (c)  → task; route 0.0.0.0/0 → NAT cùng AZ
VPC endpoints: S3 (gateway), ecr.api, ecr.dkr, logs, secretsmanager (interface, private DNS on)
```

```bash
# Gateway endpoint S3 (miễn phí) — bắt buộc nếu muốn pull ECR private không qua NAT
aws ec2 create-vpc-endpoint --vpc-id <VPC> --service-name com.amazonaws.<REGION>.s3 \
  --vpc-endpoint-type Gateway --route-table-ids <RT_PRIVATE_A> <RT_PRIVATE_B> <RT_PRIVATE_C>

# Interface endpoint ECR / Logs
for svc in ecr.api ecr.dkr logs secretsmanager; do
  aws ec2 create-vpc-endpoint --vpc-id <VPC> --vpc-endpoint-type Interface \
    --service-name com.amazonaws.<REGION>.$svc --subnet-ids <PRIV_A> <PRIV_B> <PRIV_C> \
    --security-group-ids <SG_ENDPOINT> --private-dns-enabled
done
```

Security group:

| SG | Inbound |
|---|---|
| `sg-alb` | 443 từ 0.0.0.0/0 |
| `sg-order` | 8080 từ `sg-alb` |
| `sg-user` | 8080 từ `sg-order` |
| `sg-endpoint` | 443 từ `sg-order`, `sg-user` |

### 21.3 Cluster, capacity, IAM

```bash
aws ecs create-cluster --cluster-name prod \
  --capacity-providers FARGATE FARGATE_SPOT \
  --default-capacity-provider-strategy capacityProvider=FARGATE,base=1,weight=1 \
  --settings name=containerInsights,value=enabled \
  --service-connect-defaults namespace=prod.local
```

IAM:
- `order-execution-role`: `AmazonECSTaskExecutionRolePolicy` + đọc secret.
- `order-task-role`: chỉ quyền app cần.
- Mỗi service một bộ role.

### 21.4 Image và task definition

```
git push → build (multi-stage) → push ECR :<git-sha> (immutable tags) → register task def → update-service
```

Task definition: Fargate, `awsvpc`, `portMappings.name`, `awslogs`, secret, health check. Mẫu đầy đủ: file toàn tập *Task definition* (folder 13).

### 21.5 Service

```bash
aws ecs create-service --cluster prod --service-name order-api \
  --task-definition order-api:1 --desired-count 6 \
  --capacity-provider-strategy capacityProvider=FARGATE,base=3,weight=1 \
  --network-configuration "awsvpcConfiguration={subnets=[<PRIV_A>,<PRIV_B>,<PRIV_C>],securityGroups=[<SG_ORDER>],assignPublicIp=DISABLED}" \
  --load-balancers targetGroupArn=<TG_ARN>,containerName=app,containerPort=8080 \
  --health-check-grace-period-seconds 60 \
  --service-connect-configuration '{"enabled":true,"namespace":"prod.local"}' \
  --deployment-configuration "deploymentCircuitBreaker={enable=true,rollback=true}"
```

- `desired-count 6` cho 3 AZ: nếu nhu cầu thường là 4 task → 1,5× = 6.
- Auto scaling: target tracking CPU 60–80% (hoặc metric phù hợp mô hình, mục 15), min = 6.

### 21.6 Vận hành

- Container Insights + X-Ray + VPC Flow Logs.
- Alarm `Usage/CallCount`, EventBridge rule cho `ECS_OPERATION_THROTTLED`.
- Trước sự kiện lớn: scheduled scaling + xin tăng quota.
- Xem lại phí NAT hàng tháng; traffic AWS nào còn đi qua NAT thì thêm endpoint.

---

## 22. Checklist quyết định kiến trúc

> Nguồn: `05-99` mục 10 + tổng hợp

```
Compute
[ ] Compute mặc định: Fargate / Managed Instances / EC2? Spot cho dev, batch?
[ ] Dùng capacity provider ngay từ đầu (không dùng launch type để chạy)
[ ] Workload edge (Local Zone/Wavelength/Outposts) → EC2

Mạng
[ ] Task ở private subnet, nhiều AZ
[ ] Đầu vào: ALB dùng chung / NLB / API Gateway (traffic thấp)
[ ] Đầu ra: NAT 1/AZ + VPC endpoint (S3 gateway, ECR, Logs, Secrets)
[ ] Service-to-service: Service Connect (hoặc lý do rõ ràng để dùng Cloud Map / internal LB)
[ ] Multi-account: Transit Gateway trước tiên

Bảo mật
[ ] Execution role và task role tách riêng; mỗi service một task role
[ ] SG theo từng service, chỉ mở cho upstream cần
[ ] Không mở SSH ra internet

Image
[ ] Tag git SHA, immutable tag ECR, không latest ở prod
[ ] Bắt SIGTERM, log stdout, 1 tiến trình/container, image nhỏ, ECR cùng Region

Capacity
[ ] Kích thước từ load test
[ ] Metric scale đúng mô hình (Java không theo memory; queue → queue depth)
[ ] Target 60–80%; 2 AZ = 2×, 3 AZ = 1,5×
[ ] EC2: minimumScalingStepSize, instanceWarmupPeriod, targetCapacity

Quy mô
[ ] Biết trần: 5.000 instance/cluster, 1.000 task/service với Cloud Map, ENI/instance
[ ] Giám sát throttling (CloudTrail, EventBridge, Usage metric)
[ ] Pipeline retry với backoff + jitter; RunTask có Step Functions retry
```

---

## 23. Chỗ docs có lỗi / không khớp

| Chỗ | Vấn đề | Cách hiểu / sửa |
|---|---|---|
| `05-00` | `ecsCreateCluster` | Trong IAM là `ecs:CreateCluster` |
| `05-01` | Tiêu đề lặp "Amazon Amazon"; phần mở đầu chỉ kể 3 launch type, mục best practice thêm Managed Instances | Coi Managed Instances cũng dùng được trong `requiresCompatibilities` |
| `05-02` | Ví dụ Java thiếu `;` | Thêm `;` |
| `05-03` | Mở đầu nhắc Outposts nhưng nội dung không có mục Outposts | Xem `05-04` |
| `05-04` | `–-role-name`, `–-subnet-id` (gạch dài), nháy cong `‘EOF’`, `“environment”`, `--iam-instance-profile Name outpost` | Sửa thành ASCII, `Name=outpost` (đã sửa ở mục 4.3) |
| `05-04` | Mô tả `role-policy.json` nhưng `create-role` dùng `ecs-policy.json` | Hai file khác nhau: trust policy và permission policy |
| `05-06-01` | Câu "workload nhỏ thì private + NAT hiệu quả chi phí hơn" ngược mạch văn | Đối chiếu thêm; NAT là chi phí cố định đáng kể với workload nhỏ |
| `05-08-01` | Mục ELB lại nói về throttling của Cloud Map | Có vẻ chép nhầm |
| `05-08-02` | `eventName` có khoảng trắng; truy vấn Athena mốc đầu muộn hơn mốc cuối | Đổi thứ tự mốc (đã sửa ở mục 19.4) |
| `05-09-03` | "can show application performance" | Hiểu là "slow" |
| `05-11` | "allows allows" | Lỗi lặp chữ |

---

## 24. Câu hỏi tự kiểm tra (có đáp án)

**1. Kể chuỗi khái niệm từ code tới service đang chạy trên ECS.**
<details><summary>Đáp án</summary>

Dockerfile → build image → push lên registry (ECR) → viết task definition (JSON mô tả container, image, CPU/memory, port, volume) → deploy thành task hoặc service trên cluster (chạy trên capacity đã đăng ký) → giám sát bằng CloudWatch / Runtime Monitoring. Trên EC2, container agent trên mỗi instance báo trạng thái và chạy/dừng task theo lệnh ECS.
</details>

**2. Khi nào chọn Fargate, Managed Instances, EC2?**
<details><summary>Đáp án</summary>

- Fargate: không muốn quản lý hạ tầng, workload biến động, mới bắt đầu.
- Managed Instances: muốn đơn giản như Fargate nhưng kiểm soát hạ tầng hơn, hiệu năng dự đoán được, tối ưu chi phí; AWS lo vá lỗi và scale instance.
- EC2: cần GPU/HPC, capacity reservation, instance type cụ thể, privileged, custom AMI. Ở Local Zones, Wavelength, Outposts chỉ có EC2.
</details>

**3. Launch type và capacity provider khác vai trò thế nào theo best practice?**
<details><summary>Đáp án</summary>

Launch type chỉ dùng trong `requiresCompatibilities` của task definition, để khai báo task tương thích với hạ tầng nào (chọn được nhiều). Capacity provider strategy dùng khi chạy task hoặc tạo service, để quyết định task thực sự chạy trên capacity nào. AWS khuyến nghị dùng capacity provider để launch.
</details>

**4. Service đang dùng Fargate launch type. Muốn chuyển sang EC2. Làm thế nào?**
<details><summary>Đáp án</summary>

Không đổi thẳng sang EC2 launch type được. Cách làm:
1. Thêm `EC2` vào `requiresCompatibilities` của task definition.
2. Tạo capacity provider Auto Scaling group.
3. `UpdateService` với capacity provider strategy trỏ tới provider đó.

Sau khi đã sang capacity provider, chỉ quay về được đúng Fargate launch type ban đầu, bằng `capacityProviderStrategy: []`.
</details>

**5. Một capacity provider strategy có thể gồm FARGATE_SPOT và một ASG provider không?**
<details><summary>Đáp án</summary>

Không. Cluster chứa được mọi loại capacity provider, nhưng một strategy chỉ được chứa một loại. `FARGATE` + `FARGATE_SPOT` thì được, vì cùng loại Fargate.
</details>

**6. Vì sao Fargate Spot hợp batch và dev nhưng không hợp API thanh toán?**
<details><summary>Đáp án</summary>

Task Fargate Spot có thể bị thu hồi, với cảnh báo trước 2 phút, khi nhu cầu cao. Batch, dev và staging chịu được gián đoạn; API cần HA và độ trễ thấp thì không. Có thể trộn: `base` trên Fargate on-demand, phần burst trên Spot.
</details>

**7. Task EC2 dùng `awsvpc` ở public subnet có ra internet qua internet gateway được không? Fargate thì sao?**
<details><summary>Đáp án</summary>

- EC2 `awsvpc`: không. ENI của task không được cấp IP public, nên phải đi qua NAT gateway. (Mode `host`/`bridge` thì dùng IP public của host.)
- Fargate: được, nếu bật **Assign public IP** ở public subnet; mỗi task có IP public riêng.
</details>

**8. Vì sao docs không khuyến nghị cho container nhận traffic trực tiếp bằng IP public? Có những lựa chọn nào?**
<details><summary>Đáp án</summary>

Ở quy mô lớn nên có một lớp đầu vào scale được. Lớp này lo TLS, routing, bảo mật, và che task ở private subnet. Có ba lựa chọn:
- **ALB** (tầng 7): HTTP, REST, gRPC, websocket, WAF, path/host routing.
- **NLB** (tầng 4): non-HTTP, UDP, mã hoá đầu-cuối.
- **API Gateway HTTP API**: HTTP có traffic thấp hoặc đột biến; tính phí theo request; nối tới task qua VPC link + Cloud Map.
</details>

**9. Dùng NLB thì vấn đề gì xảy ra khi task bị thay, và cách xử lý?**
<details><summary>Đáp án</summary>

NLB không hiểu giao thức tầng trên nên không gửi thông điệp đóng kết nối cho client. Có ba cách xử lý:
1. App tự đóng kết nối.
2. Bật connection termination của target group: NLB đóng kết nối tầng 4. Client có thể thấy lỗi nếu không xử lý.
3. Đặt deregistration delay dài hơn timeout của client: client timeout trước và tự kết nối lại sang task mới.
</details>

**10. Khi nào API Gateway rẻ hơn ALB?**
<details><summary>Đáp án</summary>

Khi tổng số request thấp hoặc có những giai đoạn vắng. API Gateway tính phí theo request (không có request thì không mất tiền), còn ALB/NLB tính phí theo giờ dù không có traffic. Ở tải cao, ALB/NLB rẻ hơn tính trên mỗi request.
</details>

**11. Task ở private subnet chỉ có NAT gateway. Có những nhược điểm gì? Giải pháp?**
<details><summary>Đáp án</summary>

Nhược điểm:
- Không giới hạn được đích.
- Mọi GB đi qua NAT đều tính phí, kể cả pull image ECR, tải layer từ S3, query DynamoDB.
- Băng thông 5 Gbps, tự scale tới 45 Gbps mỗi NAT.

Giải pháp: thêm VPC endpoint (PrivateLink) cho S3 (gateway), ECR, ECS, DynamoDB, Logs… để traffic không rời mạng Amazon và không chịu phí NAT. Băng thông lớn thì chia workload ra nhiều subnet, mỗi subnet một NAT.
</details>

**12. Pull image từ ECR private qua VPC endpoint, chỉ tạo endpoint `ecr.api` và `ecr.dkr` là đủ chưa?**
<details><summary>Đáp án</summary>

Chưa. Layer của image nằm trên Amazon S3, nên cần thêm S3 gateway endpoint (hoặc vẫn có NAT). Docs nêu rõ host phải tới được cả ECR endpoint lẫn S3. Nếu dùng `awslogs` hay secret thì cũng cần endpoint `logs`, `secretsmanager`/`ssm` (🔎).
</details>

**13. So sánh Service Connect, Cloud Map service discovery và internal load balancer.**
<details><summary>Đáp án</summary>

- **Service Connect** (khuyến nghị): ECS chạy agent trong mỗi task. App dùng tên ngắn + port chuẩn, không cần sửa code. Thay đổi áp dụng theo deployment, không phụ thuộc TTL. Chạy được khác cluster, khác VPC cùng Region.
- **Cloud Map DNS**: độ trễ thấp, không có thành phần ở giữa. Nhưng app phải tự retry và bỏ qua backend hỏng; có thể bị TTL trỏ tới task đã chết; mode `bridge` cần bản ghi SRV; giới hạn 1.000 task/service.
- **Internal LB**: có health check, chạy với mọi network mode. Đổi lại tốn chi phí (giảm được bằng ALB dùng chung với path rule).
</details>

**14. Vì sao dùng Cloud Map với network mode `bridge` khó hơn `awsvpc`?**
<details><summary>Đáp án</summary>

Ở `bridge`, nhiều container dùng chung IP của host và có port ngẫu nhiên (dynamic port mapping). Bản ghi `A` chỉ chứa IP nên không đủ, phải dùng bản ghi `SRV` (IP + port), và app phải hỗ trợ SRV. Với `awsvpc` mỗi task có IP riêng nên `A` là đủ, lại có SG riêng cho từng service.
</details>

**15. Nhiều team, nhiều account, mỗi team một VPC, cần nối với nhau và với on-prem. Docs khuyên cân nhắc gì đầu tiên?**
<details><summary>Đáp án</summary>

AWS Transit Gateway, làm hub trung tâm nối VPC, account và on-prem. Các lựa chọn khác: Site-to-Site VPN (on-prem ↔ VPC), VPC peering (VPC ↔ VPC), Shared VPC (nhiều account dùng chung subnet).
</details>

**16. Task execution role và task role khác nhau thế nào? Kể 3 việc của mỗi role.**
<details><summary>Đáp án</summary>

- **Task execution role** do ECS dùng thay bạn: pull ECR private (kể cả cross-account), gửi log `awslogs`, lấy secret từ Secrets Manager/SSM. Ngoài ra còn xác thực private registry và Runtime Monitoring.
- **Task role** do code trong container dùng: gọi S3, DynamoDB, SQS…

Ví dụ khác: container instance role (EC2 đăng ký vào cluster), infrastructure role (EBS volume, TLS Service Connect, VPC Lattice, Managed Instances), infrastructure for load balancers role (blue/green của ECS).
</details>

**17. Muốn gắn EBS volume vào task và bật TLS cho Service Connect thì cần role gì?**
<details><summary>Đáp án</summary>

Amazon ECS infrastructure role. Role này cũng dùng cho Managed Instances và target group VPC Lattice.
</details>

**18. Kể 5 nguyên tắc container image và lý do của nguyên tắc bắt `SIGTERM`.**
<details><summary>Đáp án</summary>

Năm nguyên tắc: image đầy đủ dependency; một tiến trình mỗi container; bắt `SIGTERM`; log ra stdout/stderr; tag duy nhất (git SHA) kèm immutable tag ECR, không dùng `latest` ở prod.

Về `SIGTERM`: khi dừng task, ECS gửi `SIGTERM` trước rồi mới gửi `SIGKILL`. App bắt `SIGTERM` để ngừng nhận việc mới, làm nốt việc dở, hoặc lưu việc dở ra ngoài task. Nếu bỏ qua, ECS phải chờ rồi `SIGKILL`, mất việc đang làm.
</details>

**19. Khi nào gộp nhiều container vào một task definition trên Fargate?**
<details><summary>Đáp án</summary>

Khi chúng cần ít nhất một trong các điều sau: có chung vòng đời (bật/tắt cùng nhau); gọi nhau qua `localhost` (phải cùng host); chia sẻ tài nguyên; chia sẻ data volume. Ví dụ: app + sidecar log router/proxy. Nếu không cần thì tách ra để scale, cấp phát, thu hồi riêng.
</details>

**20. Một metric scale "tốt" phải có 2 tính chất gì? Kiểm tra ra sao?**
<details><summary>Đáp án</summary>

1. Tương quan với nhu cầu: tải tăng hoặc giảm thì metric tăng hoặc giảm theo.
2. Tỉ lệ với capacity: giữ nguyên tải, gấp đôi số task thì metric giảm một nửa.

Kiểm tra bằng load test: tăng tải dần tới khi vỡ SLO, tìm tài nguyên cạn trước, rồi gấp đôi số task và chạy lại ở tải đỉnh. Ví dụ: CPU từ 80% còn 40% là dùng được; CPU vẫn 80% thì không dùng được.
</details>

**21. App Spring Boot (Java) nên scale theo memory không? Worker SQS nên scale theo gì?**
<details><summary>Đáp án</summary>

- Java: không nên scale theo memory. JVM (8u191+) tự đặt heap lớn vừa container, nên memory gần như không đổi theo tải. Nên scale theo CPU. Nếu app cạn heap ở mức đồng thời cao thì dùng số kết nối trung bình; cạn heap ở throughput cao thì dùng request rate trung bình.
- Worker SQS: scale theo queue depth (`ApproximateNumberOfMessagesVisible`).
</details>

**22. Vì sao cần cấp dư, và chạy bao nhiêu task nếu nhu cầu bình thường là 4 task, trải trên 2 AZ? Trên 3 AZ?**
<details><summary>Đáp án</summary>

Lý do cấp dư:
- Autoscaling có độ trễ (tổng hợp metric, alarm phải vượt vài phút, khởi động task), nên cần headroom; đặt target 60–80%.
- Phải chịu được khi mất một AZ.

Số task:
- 2 AZ: 2× = 8 task (mỗi AZ 4; mất một AZ vẫn còn 4).
- 3 AZ: 1,5× = 6 task (mỗi AZ 2; mất một AZ còn 4).
</details>

**23. Kể các cách làm service scale out nhanh hơn.**
<details><summary>Đáp án</summary>

- Image nhỏ: `FROM scratch`/base tối giản, multi-stage, gộp `RUN`, không nhét dữ liệu thừa.
- Image ở ECR cùng Region.
- Giảm interval/threshold health check của LB (mặc định có thể ≥ 90s).
- Xử lý cold-start của Java/JIT.
- Dùng step scaling thay target tracking.
- Riêng EC2: instance lớn hơn, EBS nhanh hơn, mode `bridge`, `prefer-cached` + `binpack`, ENI trunking; tăng `minimumScalingStepSize`, giảm `instanceWarmupPeriod`, `targetCapacity` < 100.
</details>

**24. Sắp có đợt sale lúc 0h ngày 11/11. Chuẩn bị gì? Nếu là đợt viral không báo trước thì sao?**
<details><summary>Đáp án</summary>

Đợt biết trước:
- Các team dùng chung một lịch.
- Dùng scheduled scaling để scale out trước sự kiện và scale in sau.
- Xin tăng quota trước (Fargate task, target ELB…).
- Có Enterprise Support thì nhờ TAM kiểm tra quota và prewarm LB.

Đợt không báo trước:
- Cấp dư tới mức tối đa dự kiến.
- Scale theo leading indicator.
- Có chế độ hiệu năng cao (vd trả trang tĩnh).
- Tách monolith để scale riêng phần quan trọng.
</details>

**25. Cluster EC2 deploy chậm vì phải chờ instance mới. Chỉnh những tham số nào của capacity provider?**
<details><summary>Đáp án</summary>

- Tăng `minimumScalingStepSize` (mặc định 1) để mỗi lần thêm nhiều instance.
- Không để `maximumScalingStepSize` quá thấp.
- Giảm `instanceWarmupPeriod` (mặc định 300s).
- Đặt `targetCapacity` < 100 (vd 80 → giữ 20% capacity dự phòng) để task đặt được ngay.

Chỉnh qua `CreateCapacityProvider`/`UpdateCapacityProvider`. Lựa chọn khác là tăng replica ở service, nhưng cách này không giúp được khi deploy service mới từ 0 lên N.
</details>

**26. c5.large chạy được bao nhiêu task `awsvpc`? Bật ENI trunking thì sao? Vì sao?**
<details><summary>Đáp án</summary>

- Mặc định: 2 task. c5.large có tối đa 3 ENI, trừ 1 ENI chính của instance.
- Bật trunking: 10 task. Tối đa 12 ENI, trừ ENI chính và ENI trunk do ECS tạo.

Quota `Maximum network interfaces` mỗi instance không điều chỉnh được, nên trunking là cách tăng mật độ task.
</details>

**27. Service dùng Cloud Map service discovery muốn chạy 1.500 task. Có được không?**
<details><summary>Đáp án</summary>

Không. Quota `Instances per service` của Cloud Map giới hạn tối đa 1.000 task mỗi service và không tăng được. Có hai hướng: chia thành nhiều service, hoặc dùng cơ chế khác (Service Connect 🔎, internal LB).
</details>

**28. Phân biệt throttling đồng bộ và bất đồng bộ. Deploy hàng trăm service thấy event "Operations are being throttled" thì đó là loại nào, làm gì?**
<details><summary>Đáp án</summary>

- **Đồng bộ**: bạn gọi ECS API và nhận lỗi ngay. Xử lý bằng retry với exponential backoff + jitter (SDK có sẵn).
- **Bất đồng bộ**: ECS gọi EC2/ELB/Cloud Map thay bạn (`CreateNetworkInterface`, `RegisterTarget`, `DescribeTargetHealth`, `RegisterInstance`…).

Event "Operations are being throttled" là loại bất đồng bộ. Scheduler tự retry với back-off nên deploy chậm lại. Việc cần làm:
- Tìm API bị throttle và người gọi bằng CloudTrail + Logs Insights/Athena.
- Đặt alarm trên `Usage/CallCount` và EventBridge `ECS_OPERATION_THROTTLED`.
- Giãn deploy ra, giữ capacity dư.
- Nhờ Support nâng giới hạn.
</details>

**29. Pipeline dùng `RunTask` cho job đêm, thỉnh thoảng job không chạy. Vì sao và sửa thế nào?**
<details><summary>Đáp án</summary>

`RunTask` cho standalone task là thao tác bất đồng bộ và không tự retry khi bị throttle hoặc thất bại (khác với service, nơi scheduler có retry). Docs gợi ý dùng AWS Step Functions tích hợp EventBridge để retry, và theo dõi sự kiện task.
</details>

**30. App báo lỗi kết nối khi traffic cao, CPU/memory vẫn thấp. Nghi ngờ gì, và có những công cụ gì?**
<details><summary>Đáp án</summary>

Nghi ngờ `nofile` ulimit quá thấp: mỗi socket tốn 1 file descriptor. Tăng ulimit trong task definition (trên EC2 có thể phải tăng cả ở instance); với instance lớn có nhiều container, cân nhắc chỉnh buffer `sysctl` net.

Công cụ:
- Container Insights: metric mạng.
- X-Ray: tìm bottleneck giữa các service.
- VPC Flow Logs: xem kết nối có bị chặn không.
</details>

**31. Dual-stack endpoint của ECS dùng làm gì, dạng tên ra sao, bật trên CLI thế nào?**
<details><summary>Đáp án</summary>

Dùng để gọi ECS API qua IPv4 hoặc IPv6 (endpoint mặc định chỉ hỗ trợ IPv4). Dạng tên: `ecs.<region>.api.aws`. Bật trên CLI bằng một trong ba cách: `use_dualstack_endpoint = true` trong `~/.aws/config`; `--endpoint-url https://ecs.<region>.api.aws` cho từng lệnh; hoặc tách profile riêng. Đây không phải traffic của ứng dụng.
</details>

**32. Chạy ECS trên Outposts: cái gì chạy ở Region, mất kết nối thì sao, và giảm độ trễ pull image bằng cách nào?**
<details><summary>Đáp án</summary>

- Ở Region: ECR, IAM, NLB. Fargate không có trên Outposts.
- Khi mất kết nối: cluster đang chạy vẫn chạy, nhưng không tạo được cluster mới hay thực hiện hành động mới, instance lỗi không được tự thay, CloudWatch Logs agent không gửi log. Vì vậy kết nối về Region phải tin cậy, HA, độ trễ thấp.
- Giảm độ trễ pull image: đặt `ECS_IMAGE_PULL_BEHAVIOR=prefer-cached` trong `/etc/ecs/ecs.config`.
</details>

---

## 25. Bản đồ nguồn

| Mục | File nguồn |
|---|---|
| 1, 2, 10, 12 | `05-00-ecs-architect-your-solution.md` |
| 3 | `05-01-ecs-launch-types-and-capacity-providers.md` |
| 10 | `05-02-ecs-dual-stack-endpoints.md` |
| 4 | `05-03-ecs-shared-subnets-local-wavelength-zones.md`, `05-04-ecs-on-aws-outposts.md` |
| 13 | `05-05-ecs-container-image-best-practices.md` |
| 5 | `05-06-01-ecs-connect-applications-to-internet.md` |
| 6 | `05-06-02-ecs-receiving-inbound-connections.md` |
| 7 | `05-06-03-ecs-connecting-to-aws-services-from-vpc.md` |
| 8 | `05-06-04-ecs-connecting-services-in-vpc.md` |
| 9 | `05-06-05-ecs-networking-across-accounts-and-vpcs.md` |
| 20 | `05-06-06-ecs-networking-troubleshooting-services.md` |
| 18 | `05-07-ecs-optimize-task-launch-time.md` |
| 19 | `05-08-01-ecs-service-quotas-and-api-throttling.md`, `05-08-02-ecs-handle-throttling-issues.md` |
| 14 | `05-09-01-ecs-determining-task-size.md`, `05-09-05-ecs-choosing-fargate-task-sizes.md` |
| 15 | `05-09-02-ecs-optimizing-service-auto-scaling.md` |
| 16 | `05-09-03-ecs-capacity-and-availability.md` |
| 17 | `05-09-04-ecs-cluster-capacity.md`, `05-09-06-ecs-speeding-up-cluster-capacity-provisioning-ec2.md` |
| 11 | `05-11-ecs-iam-roles-overview.md` |
| 0, 1, 22 | `05-99-thuyet-trinh-cto.md` + tổng hợp |
| 21 | ✍️ / 🔎 |

Liên quan: file toàn tập *Task definition* (`13 - Task definitions/13-98-…`), *Load balancing* (`15 - Schedule your containers/15-04-05-99-…`), *Service Connect* (`15 - Schedule your containers/15-04-07-01-99-…`).

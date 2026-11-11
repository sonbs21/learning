# Amazon ECS task networking options for Fargate

> Nguồn: **Amazon ECS Developer Guide — Amazon ECS task networking options for Fargate**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/fargate-task-networking.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: diễn đạt lại bằng tiếng Việt, bám sát từng ý và đúng thứ tự của trang gốc, không thêm thông tin ngoài trang. Đây không phải bản dịch từng chữ; khi cần câu chữ chính xác, đọc link gốc ở trên.
> - Khối **💡 Giải thích thêm**: phần giải thích để dễ hiểu hơn, chỉ làm rõ những gì docs đã nói.

---

## Mục lục

1. [ENI của task Fargate](#1-eni-của-task-fargate)
2. [Đường ra internet để pull image](#2-đường-ra-internet-để-pull-image)
3. [VPC Flow Logs, PrivateLink, CloudFormation](#3-vpc-flow-logs-privatelink-cloudformation)
4. [ENI do Fargate quản lý và luồng traffic theo platform version](#4-eni-do-fargate-quản-lý-và-luồng-traffic-theo-platform-version)
5. [Những điều cần cân nhắc (Considerations)](#5-những-điều-cần-cân-nhắc-considerations)
6. [Dùng VPC ở chế độ dual-stack](#6-dùng-vpc-ở-chế-độ-dual-stack)
7. [Dùng VPC ở chế độ IPv6-only](#7-dùng-vpc-ở-chế-độ-ipv6-only)
8. [Region hỗ trợ IPv6-only](#8-region-hỗ-trợ-ipv6-only)
9. [So sánh với EC2 và Managed Instances](#9-so-sánh-với-ec2-và-managed-instances)
10. [Tóm tắt](#10-tóm-tắt)

---

## 1. ENI của task Fargate

**📘 Theo docs AWS**

- Mặc định, **mỗi task** ECS trên Fargate được cấp một **ENI** với một **địa chỉ IP private chính**.
- Dùng **public subnet** thì có thể (tuỳ chọn) gán thêm **IP public** cho ENI của task.
- Nếu VPC cấu hình **dual-stack** và subnet có **IPv6 CIDR block**, ENI của task cũng nhận **địa chỉ IPv6**.
- Mỗi task chỉ gắn **một ENI** tại một thời điểm.
- Các container trong **cùng task** giao tiếp được với nhau qua **`localhost`**.
- Tìm hiểu VPC và subnet: *How Amazon VPC works* — <https://docs.aws.amazon.com/vpc/latest/userguide/how-it-works.html>.

**💡 Giải thích thêm**

Đây chính là mode `awsvpc` (Fargate bắt buộc `awsvpc` — theo trang *Architect your application*), nên trang này không có bảng chọn network mode như trang EC2.

```
┌──────────── Task Fargate ────────────┐
│ container A ◄── localhost ──► container B │
└───────────────┬───────────────────────┘
                ENI (1 ENI / task)
                ├─ IPv4 private (luôn có)
                ├─ IPv4 public (tuỳ chọn, chỉ ở public subnet)
                └─ IPv6 (nếu VPC dual-stack + subnet có IPv6 CIDR)
```

---

## 2. Đường ra internet để pull image

**📘 Theo docs AWS**

Để pull container image, task Fargate **phải có route ra internet**. Các cách đảm bảo:

- **Public subnet**: gán **IP public** cho ENI của task.
- **Private subnet**: subnet có **NAT gateway**.
- **Image trên Amazon ECR**: cấu hình ECR dùng **interface VPC endpoint**, việc pull image đi qua **IPv4 private** của task. (Xem *Amazon ECR interface VPC endpoints (AWS PrivateLink)* — <https://docs.aws.amazon.com/AmazonECR/latest/userguide/vpc-endpoints.html>.)

**💡 Giải thích thêm**

| Subnet | Cách pull image | Ghi chú |
|---|---|---|
| Public | Gán IP public cho task | Thiếu IP public thì task không ra được internet dù ở public subnet |
| Private | NAT gateway | Mất phí NAT theo lưu lượng |
| Private, image ở ECR | Interface VPC endpoint của ECR | Đi hoàn toàn trong mạng private |

Lỗi hay gặp suy từ mục này: task ở public subnet nhưng không bật gán IP public → không pull được image.

---

## 3. VPC Flow Logs, PrivateLink, CloudFormation

**📘 Theo docs AWS**

- Vì **mỗi task có ENI riêng**, bạn dùng được các tính năng mạng như **VPC Flow Logs** để theo dõi traffic ra vào từng task. (Xem *VPC Flow Logs* — <https://docs.aws.amazon.com/vpc/latest/userguide/flow-logs.html>.)
- Có thể dùng **AWS PrivateLink**: cấu hình **VPC interface endpoint** để gọi **ECS API** qua IP private. PrivateLink giữ toàn bộ traffic giữa VPC và ECS **trong mạng Amazon**; không cần internet gateway, thiết bị NAT hay virtual private gateway. (Xem *Amazon ECS interface VPC endpoints (AWS PrivateLink)* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/vpc-endpoints.html>.)
- Ví dụ dùng resource `NetworkConfiguration` với CloudFormation: *CloudFormation example templates for Amazon ECS* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/working-with-templates.html>.

**💡 Giải thích thêm**

Có hai loại interface endpoint khác nhau ở mục 2 và 3: endpoint của **ECR** (để pull image) và endpoint của **ECS** (để gọi ECS API). Một kiến trúc private hoàn toàn thường cần cả hai, cộng thêm các endpoint cho dịch vụ task dùng (log, secrets…) — phần "cộng thêm" là suy luận từ bảng ở mục 4, không phải docs nói thẳng.

---

## 4. ENI do Fargate quản lý và luồng traffic theo platform version

**📘 Theo docs AWS**

- Các ENI được tạo do **AWS Fargate quản lý hoàn toàn**, và có IAM policy liên quan để cấp quyền cho Fargate.
- **Platform version `1.4.0` trở lên**: task nhận **một ENI duy nhất** (gọi là **task ENI**), mọi traffic mạng đi qua ENI đó trong VPC của bạn và được ghi vào **VPC flow logs**.
- **Platform version `1.3.0` trở xuống**: ngoài task ENI, task còn có **một ENI riêng thuộc sở hữu của Fargate**, dùng cho một số traffic **không hiện** trong VPC flow logs.

Bảng luồng traffic và IAM permission theo từng platform version:

| Hành động | Linux `1.3.0` trở xuống | Linux `1.4.0` | Windows `1.0.0` | IAM permission |
|---|---|---|---|---|
| Lấy thông tin đăng nhập ECR | Fargate owned ENI | Task ENI | Task ENI | Task execution IAM role |
| Pull image | Task ENI | Task ENI | Task ENI | Task execution IAM role |
| Gửi log qua log driver | Task ENI | Task ENI | Task ENI | Task execution IAM role |
| Gửi log qua FireLens | Task ENI | Task ENI | Task ENI | Task IAM role |
| Lấy secret từ Secrets Manager hoặc Systems Manager | Fargate owned ENI | Task ENI | Task ENI | Task execution IAM role |
| Traffic Amazon EFS | Không có | Task ENI | Task ENI | Task IAM role |
| Traffic ứng dụng | Task ENI | Task ENI | Task ENI | Task IAM role |

**💡 Giải thích thêm**

- Từ `1.4.0` (Linux) và `1.0.0` (Windows), **mọi traffic** đi qua task ENI. Hệ quả: security group, route table, NAT hoặc VPC endpoint của subnet task phải cho phép cả traffic **lấy secret, lấy credential ECR**, chứ không chỉ traffic ứng dụng.
- Cột IAM cho thấy hai role khác nhau:
  - **Task execution role**: những việc Fargate làm **để khởi động và vận hành** task — đăng nhập ECR, pull image, gửi log qua log driver, lấy secret.
  - **Task role**: những việc **chính task** làm — gửi log qua FireLens, truy cập EFS, traffic ứng dụng.

Liên hệ file *Architect your application* (mỗi family một IAM role): role đó là **task role** trong bảng này.

---

## 5. Những điều cần cân nhắc (Considerations)

**📘 Theo docs AWS**

1. **Service-linked role** — ECS cần service-linked role để có quyền gọi các dịch vụ AWS khác thay bạn. Role này được tạo tự động khi bạn tạo cluster, hoặc khi tạo/cập nhật service trên AWS Management Console. (Xem *Using service-linked roles for Amazon ECS* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/using-service-linked-roles.html>.) Có thể tự tạo bằng CLI:

   ```bash
   aws iam create-service-linked-role --aws-service-name ecs.amazonaws.com
   ```

2. **DNS hostname của task** — khi VPC bật cả **`enableDnsHostnames`** và **`enableDnsSupport`**, ECS đặt hostname của task là DNS hostname do Amazon cung cấp. Nếu không bật, hostname của task là một chuỗi **ngẫu nhiên**. (Xem *Using DNS with Your VPC* — <https://docs.aws.amazon.com/vpc/latest/userguide/vpc-dns.html>.)

3. **Giới hạn `awsVpcConfiguration`** — tối đa **16 subnet** và **5 security group**. (Xem *AwsVpcConfiguration* — <https://docs.aws.amazon.com/AmazonECS/latest/APIReference/API_AwsVpcConfiguration.html>.)

4. **Không tự tháo hay sửa ENI** do Fargate tạo và gắn — để tránh vô tình xoá ENI của task đang chạy. Muốn giải phóng ENI của task thì **dừng task**.

5. **Đổi DHCP options set của subnet** — thay đổi **không áp dụng** cho task đang chạy. Hãy khởi động task mới (sẽ nhận thiết lập mới) để chuyển dần trong khi kiểm thử, rồi dừng task cũ nếu không cần rollback.

6. **Địa chỉ IP theo loại subnet** (Linux `1.4.0`+ hoặc Windows `1.0.0`):
   - subnet **dual-stack** → task nhận **IPv4 và IPv6**;
   - subnet **IPv6-only** → task chỉ nhận **IPv6**.

7. **Jumbo frame** (Linux `1.4.0`+ hoặc Windows `1.0.0`) — task ENI hỗ trợ jumbo frame. Network interface được cấu hình **MTU** (kích thước payload lớn nhất vừa một frame). MTU càng lớn thì càng nhiều payload ứng dụng trong một frame, giảm overhead mỗi frame và tăng hiệu quả. Jumbo frame giảm overhead khi đường mạng giữa task và đích cũng hỗ trợ jumbo frame.

8. **Load balancer** — service dùng Fargate chỉ hỗ trợ **ALB** và **NLB**; **không hỗ trợ Classic Load Balancer**. Khi tạo target group, phải chọn target type **`ip`**, không phải `instance`. (Xem *Use load balancing to distribute Amazon ECS service traffic* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/service-load-balancing.html>.)

**💡 Giải thích thêm**

| # | Hệ quả thực tế |
|---|---|
| 1 | Trong account mới hoặc khi chỉ dùng CLI/IaC, có thể phải tự tạo service-linked role trước |
| 2 | Muốn hostname task dễ đọc thì bật DNS hostnames + DNS support cho VPC |
| 3 | Dàn task ra nhiều AZ thì nhớ giới hạn 16 subnet |
| 4 | Không xoá ENI "lạ" có vẻ thừa trong VPC khi task còn chạy |
| 5 | Đổi DNS server qua DHCP options thì phải thay task, không chờ task cũ tự nhận |
| 8 | Target type `ip` vì task Fargate không có "instance" để đăng ký, chỉ có IP của ENI |

---

## 6. Dùng VPC ở chế độ dual-stack

**📘 Theo docs AWS**

- Ở VPC **dual-stack**, task giao tiếp qua IPv4, IPv6 hoặc cả hai.
- IPv4 và IPv6 **độc lập nhau**; phải cấu hình **routing và security riêng** cho IPv4 và IPv6. (Xem *Migrating to IPv6* — <https://docs.aws.amazon.com/vpc/latest/userguide/vpc-migrate-ipv6.html>.)

Task Fargate được gán IPv6 khi thoả **tất cả** điều kiện:

1. Account setting **`dualStackIPv6`** của ECS được bật (`enabled`) cho IAM principal khởi chạy task, ở Region chạy task. Setting này **chỉ sửa được qua API hoặc AWS CLI**. Có thể bật cho một IAM principal cụ thể, hoặc cho cả account bằng account default setting. (Xem *Access Amazon ECS features with account settings* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/ecs-account-settings.html>.)
2. VPC và subnet đã bật IPv6. (Xem *Migrating to IPv6*.)
3. Subnet bật **tự động gán địa chỉ IPv6**. (Xem *Modify the IPv6 addressing attribute for your subnet* — <https://docs.aws.amazon.com/vpc/latest/userguide/modify-subnets.html>.)
4. Task hoặc service dùng Fargate platform version **`1.4.0` trở lên** cho Linux.

**Dịch vụ phụ thuộc lúc khởi chạy task** (ECR, SSM, Secrets Manager): với task Fargate trong VPC dual-stack,
- route table của **public subnet** cần route **IPv4 `0.0.0.0/0`** tới **internet gateway**;
- route table của **private subnet** cần route **IPv4 `0.0.0.0/0`** tới **NAT gateway**.

(Xem *Internet gateways* — <https://docs.aws.amazon.com/vpc/latest/userguide/VPC_Internet_Gateway.html> và *NAT gateways* — <https://docs.aws.amazon.com/vpc/latest/userguide/vpc-nat-gateway.html>.) Ví dụ cấu hình: *Example dual-stack VPC configuration* — <https://docs.aws.amazon.com/vpc/latest/userguide/vpc-migrate-ipv6-example.html>.

**💡 Giải thích thêm**

Checklist "vì sao task không có IPv6?":

```
[ ] Account setting dualStackIPv6 = enabled (đúng principal, đúng Region)  ← chỉ đổi qua API/CLI
[ ] VPC + subnet có IPv6
[ ] Subnet bật auto-assign IPv6
[ ] Platform version ≥ 1.4.0 (Linux)
```

Điểm dễ quên: ở dual-stack, dịch vụ phụ thuộc lúc launch (ECR, SSM, Secrets Manager) vẫn cần **đường IPv4** ra ngoài. Có IPv6 không có nghĩa là bỏ được NAT gateway hay route IPv4.

Account setting có thể bật bằng lệnh kiểu `aws ecs put-account-setting` hoặc `put-account-setting-default` với tên `dualStackIPv6` — tên lệnh là phần mình bổ sung, trang này chỉ nói "dùng API hoặc CLI".

---

## 7. Dùng VPC ở chế độ IPv6-only

**📘 Theo docs AWS**

Ở cấu hình **IPv6-only**, task chỉ giao tiếp qua IPv6. Thiết lập:
- thêm **IPv6 CIDR block** vào VPC và tạo subnet **chỉ có IPv6 CIDR block** (xem *Add IPv6 support for your VPC* — <https://docs.aws.amazon.com/vpc/latest/userguide/vpc-migrate-ipv6-add.html>, *Create a subnet* — <https://docs.aws.amazon.com/vpc/latest/userguide/create-subnets.html>);
- cập nhật **route table** với đích IPv6 và **security group** với rule IPv6 (xem *Configure route tables* — <https://docs.aws.amazon.com/vpc/latest/userguide/VPC_Route_Tables.html>, *Configure security group rules* — <https://docs.aws.amazon.com/vpc/latest/userguide/working-with-security-group-rules.html>).

Những điều cần cân nhắc:

- **Chuyển service sang IPv6-only**: cập nhật trực tiếp sang subnet IPv6-only, hoặc tạo service IPv6-only song song rồi dùng **ECS blue/green** để chuyển traffic. (Xem *Amazon ECS blue/green deployments* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/deployment-type-blue-green.html>.)
- **Load balancer**: service IPv6-only phải dùng load balancer **dualstack** với **target group IPv6**. Với ALB/NLB hiện có: tạo load balancer dualstack mới rồi chuyển traffic, hoặc cập nhật IP address type của load balancer cũ.
  - NLB: <https://docs.aws.amazon.com/elasticloadbalancing/latest/network/create-network-load-balancer.html>, <https://docs.aws.amazon.com/elasticloadbalancing/latest/network/load-balancer-ip-address-type.html>
  - ALB: <https://docs.aws.amazon.com/elasticloadbalancing/latest/application/create-application-load-balancer.html>, <https://docs.aws.amazon.com/elasticloadbalancing/latest/application/load-balancer-ip-address-type.html>
- **Không hỗ trợ Windows**.
- **Tới endpoint chỉ có IPv4**: dùng **DNS64 + NAT64**. (Xem *DNS64 and NAT64* — <https://docs.aws.amazon.com/vpc/latest/userguide/nat-gateway-nat64-dns64.html>.)
- **Platform version**: IPv6-only hỗ trợ từ Fargate platform version **`1.4.0` trở lên**.
- **ECR**: phải dùng **image URI endpoint dualstack**. (Xem <https://docs.aws.amazon.com/AmazonECR/latest/userguide/ecr-requests.html#ipv6-access-getting-started>.)

  > **Note (docs):** ECR **không hỗ trợ interface VPC endpoint dualstack** mà task IPv6-only có thể dùng.

- **ECS Exec**: không hỗ trợ ở IPv6-only.
- **CloudWatch + FIPS**: CloudWatch **không có dualstack FIPS endpoint** để giám sát task IPv6-only dùng tuân thủ **FIPS-140**. (Xem *AWS Fargate Federal Information Processing Standard (FIPS-140)* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/ecs-fips-compliance.html>.)

**💡 Giải thích thêm**

So với mục 2: ở IPv6-only, cách "dùng interface VPC endpoint của ECR qua IPv4 private" **không áp dụng được**, vì task không có IPv4 và ECR không có interface endpoint dualstack. Task phải đi tới endpoint dualstack của ECR. Đây là suy luận ghép từ mục 2 và Note ở trên.

Điểm riêng của Fargate so với hai trang EC2/Managed Instances là cảnh báo **CloudWatch FIPS** — chỉ ảnh hưởng workload cần tuân thủ FIPS-140.

---

## 8. Region hỗ trợ IPv6-only

**📘 Theo docs AWS**

| Khu vực | Region |
|---|---|
| Mỹ | US East (Ohio), US East (N. Virginia), US West (N. California), US West (Oregon) |
| Châu Phi | Africa (Cape Town) |
| Châu Á – Thái Bình Dương | Asia Pacific (Hong Kong), (Hyderabad), (Jakarta), (Melbourne), (Mumbai), (Osaka), (Seoul), (Singapore), (Sydney), (Tokyo) |
| Canada | Canada (Central), Canada West (Calgary) |
| Trung Quốc | China (Beijing), China (Ningxia) |
| Châu Âu | Europe (Frankfurt), (London), (Milan), (Paris), (Spain) |
| Trung Đông | Israel (Tel Aviv), Middle East (Bahrain), Middle East (UAE) |
| Nam Mỹ | South America (São Paulo) |
| GovCloud | AWS GovCloud (US-East), AWS GovCloud (US-West) |

**💡 Giải thích thêm**

Danh sách giống trang EC2. Có thể thay đổi theo thời gian; kiểm tra link gốc trước khi chọn Region.

---

## 9. So sánh với EC2 và Managed Instances

**💡 Giải thích thêm**

Ghép từ ba file networking, chỉ dựa trên nội dung các trang docs:

| | EC2 | Managed Instances | Fargate |
|---|---|---|---|
| Network mode | `awsvpc`, `bridge`, `host`, `none`, `default` | `awsvpc`, `host` (bắt buộc khai báo) | Chỉ `awsvpc` (1 ENI / task) |
| IP public cho task | — | — | Tuỳ chọn ở public subnet |
| Bảng luồng traffic theo platform version | — | — | Có (1.3.0 vs 1.4.0 vs Windows 1.0.0) |
| Mục dual-stack riêng | Không | Không | **Có** (account setting `dualStackIPv6`…) |
| IPv6-only: không Windows | Có | — | Có |
| IPv6-only: yêu cầu AMI / agent / `--enable-primary-ipv6` | Có | — | — (không quản lý instance) |
| IPv6-only: yêu cầu version | Agent ≥ 1.99.1 | — | Platform version ≥ 1.4.0 |
| IPv6-only: cảnh báo CloudWatch FIPS | — | — | Có |
| LB | (trang không nêu) | (trang không nêu) | Chỉ ALB/NLB, target type `ip` |

Ô "—" nghĩa là trang tương ứng không đề cập.

---

## 10. Tóm tắt

- Mỗi task Fargate có **1 ENI** với IPv4 private; tuỳ chọn IP public (public subnet); IPv6 nếu VPC dual-stack. Container trong task nói chuyện qua `localhost`.
- Pull image cần route ra internet: IP public, NAT gateway, hoặc interface VPC endpoint của ECR.
- Mỗi task có ENI riêng → dùng được **VPC Flow Logs**; có thể gọi ECS API qua **PrivateLink**.
- Platform **1.4.0+** (Linux) / **1.0.0** (Windows): mọi traffic qua task ENI. **1.3.0 trở xuống**: có thêm Fargate-owned ENI cho credential ECR và secret, không hiện trong flow logs.
- Execution role: ECR, pull image, log driver, secret. Task role: FireLens, EFS, traffic ứng dụng.
- Cân nhắc: service-linked role; DNS hostname cần `enableDnsHostnames` + `enableDnsSupport`; tối đa 16 subnet, 5 SG; không tự sửa ENI; DHCP options không áp cho task cũ; jumbo frame; chỉ ALB/NLB với target type `ip`.
- **Dual-stack**: cần `dualStackIPv6` (API/CLI), VPC/subnet IPv6, auto-assign IPv6, platform ≥ 1.4.0; dịch vụ lúc launch vẫn cần route IPv4 `0.0.0.0/0` (IGW hoặc NAT).
- **IPv6-only**: subnet chỉ IPv6; update trực tiếp hoặc blue/green; LB dualstack + TG IPv6; không Windows; DNS64/NAT64; platform ≥ 1.4.0; ECR dualstack; không ECS Exec; không có CloudWatch dualstack FIPS endpoint.

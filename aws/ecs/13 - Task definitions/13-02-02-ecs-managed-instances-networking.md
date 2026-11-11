# Amazon ECS task networking for Amazon ECS Managed Instances

> Nguồn: **Amazon ECS Developer Guide — Amazon ECS task networking for Amazon ECS Managed Instances**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/managed-instance-networking.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: diễn đạt lại bằng tiếng Việt, bám sát từng ý và đúng thứ tự của trang gốc, không thêm thông tin ngoài trang. Đây không phải bản dịch từng chữ; khi cần câu chữ chính xác, đọc link gốc ở trên.
> - Khối **💡 Giải thích thêm**: phần giải thích để dễ hiểu hơn, chỉ làm rõ những gì docs đã nói.

---

## Mục lục

1. [Network mode quyết định networking của task](#1-network-mode-quyết-định-networking-của-task)
2. [Hai network mode được hỗ trợ](#2-hai-network-mode-được-hỗ-trợ)
3. [Dùng VPC ở chế độ IPv6-only](#3-dùng-vpc-ở-chế-độ-ipv6-only)
4. [Những điều cần cân nhắc với IPv6-only](#4-những-điều-cần-cân-nhắc-với-ipv6-only)
5. [Tóm tắt](#5-tóm-tắt)

---

## 1. Network mode quyết định networking của task

**📘 Theo docs AWS**

- Cách task ECS chạy trên **Amazon ECS Managed Instances** hoạt động về mạng được quyết định bởi **network mode** khai báo trong task definition.
- **Bắt buộc** phải khai báo network mode trong task definition. Task definition **không** khai báo network mode thì **không chạy được** trên Managed Instances.
- Managed Instances hỗ trợ các network mode ở mục 2, giúp **tương thích ngược** khi chuyển workload từ **Fargate** hoặc từ **ECS trên EC2** sang.

**💡 Giải thích thêm**

Trên ECS chạy EC2 truyền thống, nếu bỏ trống network mode thì có giá trị mặc định. Trên Managed Instances thì không có chuyện đó: phải ghi rõ, nếu không task sẽ không chạy. Khi migrate task definition cũ sang, hãy kiểm tra trường `networkMode` trước tiên.

---

## 2. Hai network mode được hỗ trợ

**📘 Theo docs AWS**

| Network mode | Mô tả theo docs |
|---|---|
| **`awsvpc`** | Mỗi task nhận **ENI (elastic network interface) riêng** và **địa chỉ IPv4 private riêng**. Task có cùng đặc tính mạng như một EC2 instance, và **tương thích với task Fargate** truyền thống. Dùng **ENI trunking** để đạt **mật độ task cao**. |
| **`host`** | Task **dùng chung network namespace của host** trực tiếp. Networking của container gắn với instance bên dưới. |

**💡 Giải thích thêm**

```
awsvpc:                                   host:
┌──────── Instance ────────┐              ┌──────── Instance (IP của host) ────────┐
│ Task A ── ENI A (IP A)   │              │ Task A ─┐                              │
│ Task B ── ENI B (IP B)   │              │ Task B ─┴─ dùng chung mạng của host    │
└──────────────────────────┘              └────────────────────────────────────────┘
```

| | `awsvpc` | `host` |
|---|---|---|
| IP | Mỗi task một IP riêng | Dùng IP của host |
| Security group | Gắn được theo từng task (vì mỗi task có ENI) | Theo instance |
| Tương thích khi migrate | Từ Fargate (và EC2 dùng `awsvpc`) | Từ EC2 dùng `host` |
| Dùng được với ECS Gateway | Có (gateway yêu cầu `awsvpc`) | Không |

Hai dòng cuối là mình ghép từ các trang khác: Fargate bắt buộc `awsvpc` (trang *Architect your application*), và gateway không nhận `host` (trang *Amazon ECS Gateway*). Về "ENI trunking": thông thường số ENI gắn được vào một instance có giới hạn; trunking cho phép chạy nhiều task `awsvpc` hơn trên cùng một instance — docs chỉ nói nó giúp mật độ task cao, không đi sâu cơ chế.

Liên hệ trang *Architect your application*: AWS khuyến nghị `awsvpc` cho EC2 và Managed Instances.

---

## 3. Dùng VPC ở chế độ IPv6-only

**📘 Theo docs AWS**

Ở cấu hình **IPv6-only**, task ECS chỉ giao tiếp qua **IPv6**. Để thiết lập VPC và subnet cho cấu hình này, bạn cần:

1. **Thêm IPv6 CIDR block** vào VPC.
2. **Tạo subnet chỉ có IPv6 CIDR block**.
3. **Cập nhật route table** với các đích IPv6.
4. **Cấu hình security group** với rule IPv6.

Tài liệu Amazon VPC liên quan:
- *Add IPv6 support for your VPC* — <https://docs.aws.amazon.com/vpc/latest/userguide/vpc-migrate-ipv6-add.html>
- *Create a subnet* — <https://docs.aws.amazon.com/vpc/latest/userguide/create-subnets.html>
- *Configure route tables* — <https://docs.aws.amazon.com/vpc/latest/userguide/VPC_Route_Tables.html>
- *Configure security group rules* — <https://docs.aws.amazon.com/vpc/latest/userguide/working-with-security-group-rules.html>

**💡 Giải thích thêm**

```
VPC  ── + IPv6 CIDR
 └─ Subnet (chỉ IPv6 CIDR)
      ├─ Route table: thêm route IPv6
      └─ Security group: thêm rule IPv6
           └─ Task (awsvpc) → chỉ có địa chỉ IPv6
```

Bốn bước trên đều là cấu hình phía VPC, không phải trong task definition.

---

## 4. Những điều cần cân nhắc với IPv6-only

**📘 Theo docs AWS**

**a) Chuyển service sang IPv6-only** — service đang là IPv4-only hoặc dualstack có thể chuyển sang IPv6-only theo một trong hai cách:
- **cập nhật trực tiếp** service để dùng subnet IPv6-only; hoặc
- **tạo một service IPv6-only song song** rồi dùng **ECS blue/green deployment** để chuyển traffic sang service mới. (Xem *Amazon ECS blue/green deployments* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/deployment-type-blue-green.html>.)

**b) Load balancer** — service IPv6-only **phải** dùng load balancer **dualstack** với **target group IPv6**. Nếu đang migrate service đứng sau ALB hoặc NLB, bạn có thể:
- tạo load balancer dualstack mới và chuyển traffic từ load balancer cũ sang; hoặc
- **cập nhật IP address type** của load balancer hiện có.

Tài liệu liên quan:
- NLB: *Create a Network Load Balancer* — <https://docs.aws.amazon.com/elasticloadbalancing/latest/network/create-network-load-balancer.html>; *Update the IP address types for your Network Load Balancer* — <https://docs.aws.amazon.com/elasticloadbalancing/latest/network/load-balancer-ip-address-type.html>
- ALB: *Create an Application Load Balancer* — <https://docs.aws.amazon.com/elasticloadbalancing/latest/application/create-application-load-balancer.html>; *Update the IP address types for your Application Load Balancer* — <https://docs.aws.amazon.com/elasticloadbalancing/latest/application/load-balancer-ip-address-type.html>

**c) Gọi tới endpoint chỉ có IPv4** — task IPv6-only muốn nói chuyện với endpoint IPv4-only thì có thể thiết lập **DNS64** và **NAT64** để dịch địa chỉ IPv6 sang IPv4. (Xem *DNS64 and NAT64* — <https://docs.aws.amazon.com/vpc/latest/userguide/nat-gateway-nat64-dns64.html>.)

**d) Pull image từ ECR** — workload IPv6-only **phải** dùng **image URI endpoint dualstack** của Amazon ECR khi pull image. (Xem *Getting started with making requests over IPv6* — <https://docs.aws.amazon.com/AmazonECR/latest/userguide/ecr-requests.html#ipv6-access-getting-started>.)

> **Note (docs):** Amazon ECR **không hỗ trợ interface VPC endpoint dualstack** mà task IPv6-only có thể dùng.

**e) ECS Exec** — **không hỗ trợ** ở cấu hình IPv6-only.

**💡 Giải thích thêm**

Checklist khi chuyển một service sang IPv6-only, ghép từ các điểm trên:

| Hạng mục | Cần làm | Ghi chú |
|---|---|---|
| VPC / subnet | IPv6 CIDR, subnet chỉ IPv6, route, SG | Mục 3 |
| Cách chuyển | Update trực tiếp, hoặc service song song + blue/green | Blue/green ít rủi ro hơn vì có thể chuyển traffic dần và quay lại |
| Load balancer | Dualstack + target group IPv6 | Tạo LB mới hoặc đổi IP address type LB cũ |
| Dependency chỉ có IPv4 | DNS64 + NAT64 | API bên thứ ba, dịch vụ nội bộ cũ… |
| Image ECR | Dùng URI dualstack của ECR | Không dùng được interface VPC endpoint dualstack của ECR |
| Debug | Không có ECS Exec | Cần cách khác để vào container (log, metric…) |

Hệ quả thực tế của Note về ECR: trong subnet IPv6-only, bạn không thể dựa vào interface VPC endpoint của ECR để pull image hoàn toàn private; task cần đi tới endpoint dualstack của ECR. Đây là suy luận từ hai câu của docs (phải dùng endpoint dualstack + ECR không có interface endpoint dualstack); hãy kiểm tra tài liệu ECR ở link trên cho kiến trúc mạng cụ thể của bạn.

---

## 5. Tóm tắt

- Trên **ECS Managed Instances**, network mode trong task definition là **bắt buộc**; thiếu thì task không chạy.
- Hỗ trợ **`awsvpc`** (mỗi task một ENI + IPv4 private, tương thích Fargate, ENI trunking cho mật độ cao) và **`host`** (dùng chung mạng của host).
- **IPv6-only**: thêm IPv6 CIDR cho VPC, subnet chỉ IPv6, route table IPv6, SG rule IPv6.
- Chuyển sang IPv6-only: update trực tiếp hoặc service song song + **blue/green**.
- Load balancer phải **dualstack** với **target group IPv6**.
- Tới endpoint IPv4-only: dùng **DNS64 + NAT64**.
- Pull ECR: dùng **image URI dualstack**; ECR không có interface VPC endpoint dualstack.
- **ECS Exec** không hỗ trợ ở IPv6-only.

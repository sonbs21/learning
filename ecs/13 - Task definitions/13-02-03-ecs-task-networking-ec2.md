# Amazon ECS task networking options for EC2

> Nguồn: **Amazon ECS Developer Guide — Amazon ECS task networking options for EC2**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task-networking.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: diễn đạt lại bằng tiếng Việt, bám sát từng ý và đúng thứ tự của trang gốc, không thêm thông tin ngoài trang. Đây không phải bản dịch từng chữ; khi cần câu chữ chính xác, đọc link gốc ở trên.
> - Khối **💡 Giải thích thêm**: phần giải thích để dễ hiểu hơn, chỉ làm rõ những gì docs đã nói.

---

## Mục lục

1. [Network mode quyết định networking của task trên EC2](#1-network-mode-quyết-định-networking-của-task-trên-ec2)
2. [Các network mode có sẵn](#2-các-network-mode-có-sẵn)
3. [Dùng VPC ở chế độ IPv6-only](#3-dùng-vpc-ở-chế-độ-ipv6-only)
4. [Những điều cần cân nhắc với IPv6-only](#4-những-điều-cần-cân-nhắc-với-ipv6-only)
5. [Region hỗ trợ IPv6-only cho ECS](#5-region-hỗ-trợ-ipv6-only-cho-ecs)
6. [So sánh với trang Managed Instances](#6-so-sánh-với-trang-managed-instances)
7. [Tóm tắt](#7-tóm-tắt)

---

## 1. Network mode quyết định networking của task trên EC2

**📘 Theo docs AWS**

- Cách task ECS chạy trên **EC2 instance** hoạt động về mạng phụ thuộc vào **network mode** khai báo trong task definition.
- AWS **khuyến nghị dùng `awsvpc`**, trừ khi bạn có nhu cầu cụ thể cần mode khác.

**💡 Giải thích thêm**

Khác với Managed Instances (bắt buộc khai báo network mode), trên EC2 bạn có thể bỏ trống và ECS dùng mode mặc định theo hệ điều hành (xem bảng ở mục 2). Tuy vậy khuyến nghị vẫn là ghi rõ `awsvpc`.

---

## 2. Các network mode có sẵn

**📘 Theo docs AWS**

| Network mode | Linux trên EC2 | Windows trên EC2 | Mô tả theo docs |
|---|---|---|---|
| **`awsvpc`** | Có | Có | Task được cấp **ENI riêng** và một địa chỉ **IPv4 hoặc IPv6 private chính**. Task có cùng đặc tính mạng như một EC2 instance. |
| **`bridge`** | Có | Không | Task dùng mạng ảo tích hợp sẵn của Docker trên Linux, chạy bên trong từng EC2 instance chứa task. Mạng ảo này dùng Docker network driver `bridge`. Đây là **mode mặc định trên Linux** nếu task definition không khai báo network mode. |
| **`host`** | Có | Không | Task dùng mạng của host, **bỏ qua** mạng ảo của Docker bằng cách map port container **thẳng vào ENI** của EC2 instance. **Không dùng được dynamic port mapping**. Container trong task definition dùng mode này phải khai báo **`hostPort` cụ thể**. Một port trên host không dùng chung được cho nhiều task, nên **không chạy được nhiều task của cùng task definition trên một instance**. |
| **`none`** | Có | Không | Task **không có kết nối mạng ra bên ngoài**. |
| **`default`** | Không | Có | Task dùng mạng ảo tích hợp sẵn của Docker trên Windows, chạy bên trong từng EC2 instance. Mạng ảo này dùng Docker network driver `nat`. Đây là **mode mặc định trên Windows** nếu task definition không khai báo network mode. |

Tài liệu liên quan:
- Docker networking trên Linux: *Networking overview* (Docker Documentation) — <https://docs.docker.com/engine/network/>
- Docker networking trên Windows: *Windows container networking* (Microsoft) — <https://learn.microsoft.com/en-us/virtualization/windowscontainers/container-networking/architecture>

**💡 Giải thích thêm**

Hình dung từng mode trên một instance Linux:

```
awsvpc:   Task A ── ENI A (IP A)        Task B ── ENI B (IP B)        → mỗi task một IP

bridge:   Task A ─┐                                                    → các task sau mạng ảo Docker,
          Task B ─┴─ docker bridge ── ENI của instance (IP instance)      ra ngoài qua IP của instance

host:     Task A ── port 8080 trực tiếp trên ENI của instance          → port container = port host
          (Task A thứ hai không thể cũng lấy 8080 trên instance này)

none:     Task A  (không có mạng ra ngoài)
```

So sánh nhanh, dựa trên mô tả của docs:

| | `awsvpc` | `bridge` | `host` | `none` |
|---|---|---|---|---|
| IP riêng cho task | Có | Không (dùng IP instance) | Không (dùng IP instance) | — |
| Nhiều task cùng task definition trên một instance | Được | Được | **Không** (vì `hostPort` cố định) | — |
| Dynamic port mapping | — (mỗi task có IP riêng, không cần) | Được | **Không** | — |
| Mặc định khi bỏ trống | — | Linux | — | — |
| Windows | Có | Không | Không | Không |

Ghi chú bổ sung (không có trong trang này): dynamic port mapping là khi đặt `hostPort` bằng 0 hoặc bỏ trống ở mode `bridge`, Docker sẽ chọn một port ngẫu nhiên trên host — nhờ vậy chạy được nhiều task giống nhau trên cùng instance. Liên hệ với các trang trước: **ECS Gateway** và **Fargate** đều yêu cầu `awsvpc`.

---

## 3. Dùng VPC ở chế độ IPv6-only

**📘 Theo docs AWS**

Ở cấu hình **IPv6-only**, task chỉ giao tiếp qua **IPv6**. Thiết lập VPC và subnet:

1. **Thêm IPv6 CIDR block** vào VPC.
2. **Tạo subnet mới chỉ có IPv6 CIDR block**.
3. **Cập nhật route table** với các đích IPv6.
4. **Cấu hình security group** với rule IPv6.

Tài liệu Amazon VPC:
- *Add IPv6 support for your VPC* — <https://docs.aws.amazon.com/vpc/latest/userguide/vpc-migrate-ipv6-add.html>
- *Create a subnet* — <https://docs.aws.amazon.com/vpc/latest/userguide/create-subnets.html>
- *Configure route tables* — <https://docs.aws.amazon.com/vpc/latest/userguide/VPC_Route_Tables.html>
- *Configure security group rules* — <https://docs.aws.amazon.com/vpc/latest/userguide/working-with-security-group-rules.html>

**💡 Giải thích thêm**

Phần này giống hệt trang *Managed Instances*. Khác biệt nằm ở các điểm cân nhắc riêng cho EC2 ở mục 4.

---

## 4. Những điều cần cân nhắc với IPv6-only

**📘 Theo docs AWS**

**a) Chuyển service sang IPv6-only** — service IPv4-only hoặc dualstack có thể chuyển sang IPv6-only bằng cách:
- cập nhật trực tiếp service để dùng subnet IPv6-only; hoặc
- tạo service IPv6-only song song rồi dùng **ECS blue/green deployment** để chuyển traffic. (Xem *Amazon ECS blue/green deployments* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/deployment-type-blue-green.html>.)

**b) Load balancer** — service IPv6-only **phải** dùng load balancer **dualstack** với **target group IPv6**. Với service đang ở sau ALB hoặc NLB: tạo load balancer dualstack mới và chuyển traffic, hoặc **cập nhật IP address type** của load balancer hiện có.
- NLB: *Create a Network Load Balancer* — <https://docs.aws.amazon.com/elasticloadbalancing/latest/network/create-network-load-balancer.html>; *Update the IP address types for your Network Load Balancer* — <https://docs.aws.amazon.com/elasticloadbalancing/latest/network/load-balancer-ip-address-type.html>
- ALB: *Create an Application Load Balancer* — <https://docs.aws.amazon.com/elasticloadbalancing/latest/application/create-application-load-balancer.html>; *Update the IP address types for your Application Load Balancer* — <https://docs.aws.amazon.com/elasticloadbalancing/latest/application/load-balancer-ip-address-type.html>

**c) Không hỗ trợ Windows** — IPv6-only **không hỗ trợ Windows**. Phải dùng **ECS-optimized Linux AMI** để chạy task IPv6-only. (Xem *Amazon ECS-optimized Linux AMIs* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/ecs-optimized_AMI.html>.)

**d) Primary IPv6 cho container instance** — khi launch container instance để chạy task IPv6-only, **phải** đặt **primary IPv6 address** cho instance bằng tham số EC2 **`--enable-primary-ipv6`**.

> **Note (docs):** Không có primary IPv6 address thì task chạy trên instance đó ở mode **`host`** hoặc **`bridge`** sẽ **không đăng ký được** với load balancer hoặc với **AWS Cloud Map**.

- Tham số `--enable-primary-ipv6`: xem *run-instances* (AWS CLI Command Reference) — <https://docs.aws.amazon.com/cli/latest/reference/ec2/run-instances.html>.
- Launch container instance qua console: *Launching an Amazon ECS Linux container instance* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/launch_container_instance.html>.

**e) Agent tự phát hiện khả năng IPv6-only** — mặc định ECS container agent xác định instance có tương thích IPv6-only không bằng cách xem **default route IPv4 và IPv6** của instance. Muốn ghi đè, đặt tham số **`ECS_INSTANCE_IP_COMPATIBILITY`** thành `ipv4` hoặc `ipv6` trong file **`/etc/ecs/ecs.config`** của instance.

**f) Phiên bản agent** — task phải dùng container agent **phiên bản `1.99.1` trở lên**. Cách kiểm tra và cập nhật: *Updating the Amazon ECS container agent* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/ecs-agent-update.html>.

**g) Gọi tới endpoint chỉ có IPv4** — thiết lập **DNS64** và **NAT64** để dịch IPv6 sang IPv4. (Xem *DNS64 and NAT64* — <https://docs.aws.amazon.com/vpc/latest/userguide/nat-gateway-nat64-dns64.html>.)

**h) Pull image từ ECR** — workload IPv6-only **phải** dùng **image URI endpoint dualstack** của ECR. (Xem *Getting started with making requests over IPv6* — <https://docs.aws.amazon.com/AmazonECR/latest/userguide/ecr-requests.html#ipv6-access-getting-started>.)

> **Note (docs):** Amazon ECR **không hỗ trợ interface VPC endpoint dualstack** mà task IPv6-only có thể dùng.

**i) ECS Exec** — **không hỗ trợ** ở cấu hình IPv6-only.

**💡 Giải thích thêm**

Checklist cho container instance EC2 chạy task IPv6-only, ghép từ các điểm c–f:

```
[ ] AMI: ECS-optimized Linux (không phải Windows)
[ ] Launch instance với --enable-primary-ipv6
      (quan trọng nhất nếu task dùng host/bridge: thiếu thì không đăng ký được LB / Cloud Map)
[ ] ECS agent ≥ 1.99.1
[ ] (Tuỳ chọn) ECS_INSTANCE_IP_COMPATIBILITY=ipv6 trong /etc/ecs/ecs.config
      nếu không muốn agent tự đoán dựa vào default route
```

Nếu dùng launch template (ví dụ quản lý bằng Terraform), `--enable-primary-ipv6` là tham số của `run-instances`; trong launch template nó tương ứng với thiết lập primary IPv6 trên network interface — tên trường cụ thể bạn cần tra trong tài liệu EC2/Terraform, trang này không nêu.

Liên hệ file *Container image pull behavior*: `ECS_INSTANCE_IP_COMPATIBILITY` đặt cùng chỗ với `ECS_IMAGE_PULL_BEHAVIOR` — file `/etc/ecs/ecs.config`, thường ghi qua user data.

---

## 5. Region hỗ trợ IPv6-only cho ECS

**📘 Theo docs AWS**

Có thể chạy task ở cấu hình IPv6-only trong các Region sau (trong số các Region có ECS):

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

Danh sách Region có thể thay đổi theo thời gian; kiểm tra lại link gốc trước khi chọn Region cho kiến trúc IPv6-only.

---

## 6. So sánh với trang Managed Instances

**💡 Giải thích thêm**

Đặt cạnh file *Amazon ECS task networking for Amazon ECS Managed Instances*, chỉ dựa trên nội dung hai trang docs:

| | EC2 | Managed Instances |
|---|---|---|
| Bắt buộc khai báo network mode | Không (có mặc định theo OS) | **Có** |
| Mode hỗ trợ | `awsvpc`, `bridge`, `host`, `none` (Linux); `awsvpc`, `default` (Windows) | `awsvpc`, `host` |
| Khuyến nghị | `awsvpc` | (trang *Architect your application* khuyến nghị `awsvpc`) |
| IPv6-only: VPC/subnet/route/SG | Giống nhau | Giống nhau |
| IPv6-only: blue/green, LB dualstack, DNS64/NAT64, ECR dualstack, không ECS Exec | Giống nhau | Giống nhau |
| IPv6-only: Windows, AMI, `--enable-primary-ipv6`, `ECS_INSTANCE_IP_COMPATIBILITY`, agent ≥ 1.99.1 | **Có các yêu cầu này** | Trang không nêu (vì AWS quản lý instance) |
| Danh sách Region IPv6-only | Có | Trang không nêu |

Lý do Managed Instances không có các yêu cầu về AMI, agent, user data là mình suy luận: với Managed Instances, AWS quản lý instance nên những thứ đó không do bạn cấu hình.

---

## 7. Tóm tắt

- Network mode quyết định networking của task trên EC2; khuyến nghị **`awsvpc`**.
- **`awsvpc`** (Linux + Windows): ENI riêng, IP private riêng.
- **`bridge`** (Linux): mạng ảo Docker, **mặc định trên Linux**.
- **`host`** (Linux): dùng mạng host, không có dynamic port mapping, phải có `hostPort`, không chạy nhiều task cùng task definition trên một instance.
- **`none`** (Linux): không có mạng ra ngoài.
- **`default`** (Windows): mạng ảo Docker driver `nat`, **mặc định trên Windows**.
- **IPv6-only**: IPv6 CIDR, subnet chỉ IPv6, route, SG; update trực tiếp hoặc blue/green; LB dualstack + TG IPv6; **không hỗ trợ Windows**, phải dùng ECS-optimized Linux AMI; instance cần **`--enable-primary-ipv6`** (thiếu thì `host`/`bridge` không đăng ký được LB / Cloud Map); có thể ghi đè bằng `ECS_INSTANCE_IP_COMPATIBILITY`; agent **≥ 1.99.1**; DNS64/NAT64; ECR dualstack; không ECS Exec.
- Có danh sách Region hỗ trợ IPv6-only (mục 5).

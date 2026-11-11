# Use a Network Load Balancer for Amazon ECS

> Nguồn: **Amazon ECS Developer Guide — Use a Network Load Balancer for Amazon ECS**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/nlb.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: **bản dịch** sang tiếng Việt, đi đúng thứ tự và đúng nội dung của trang gốc, không thêm, không bớt ý.
> - Khối **💡 Giải thích thêm**: chỉ giải thích lại những gì **chính trang này** đã nói, cho dễ hiểu hơn. Không đưa thông tin từ trang khác hay ví dụ tự đặt.
> - Tên target type, network mode, deployment controller, instance type giữ nguyên tiếng Anh.
> - Tài liệu AWS được phát hành theo giấy phép **Creative Commons Attribution-ShareAlike 4.0**; bản dịch này ghi rõ nguồn ở trên.

---

## Mục lục

0. [Phần mở đầu: NLB và dynamic host port mapping](#0-phần-mở-đầu-nlb-và-dynamic-host-port-mapping)
1. [Best practice để deploy nhanh](#1-best-practice-để-deploy-nhanh)
2. [Những điều cần cân nhắc](#2-những-điều-cần-cân-nhắc)
3. [Important: target type và instance type](#3-important-target-type-và-instance-type)

---

## 0. Phần mở đầu: NLB và dynamic host port mapping

**📘 Theo docs AWS**

Network Load Balancer ra quyết định định tuyến ở **tầng vận chuyển (TCP/SSL)**. Nó có thể xử lý **hàng triệu request mỗi giây**. Sau khi load balancer nhận một kết nối, nó chọn một target từ target group của default rule bằng thuật toán định tuyến **flow hash**. Nó cố mở một **kết nối TCP** tới target đã chọn trên port chỉ định trong cấu hình listener. Nó chuyển tiếp request **không sửa header**. Network Load Balancer hỗ trợ **dynamic host port mapping**. Ví dụ, nếu container definition của task chỉ định port **80** cho container port của NGINX, và port **0** cho host port, thì host port được chọn **động** từ dải ephemeral port của container instance (như **32768 tới 61000** trên Amazon ECS-optimized AMI mới nhất). Khi task khởi chạy, container NGINX được đăng ký với Network Load Balancer dưới dạng tổ hợp **instance ID và port**, và traffic được phân phối tới instance ID và port tương ứng với container đó. Mapping động này cho phép bạn có **nhiều task của một service trên cùng container instance**. Thông tin thêm, xem *User Guide for Network Load Balancers* (<https://docs.aws.amazon.com/elasticloadbalancing/latest/network/>).

**💡 Giải thích thêm**

```
Kết nối đến ─► NLB chọn target bằng flow hash (từ target group của default rule)
             ─► mở kết nối TCP tới target trên port của listener
             ─► chuyển tiếp, KHÔNG sửa header
```

| Đặc điểm theo trang | |
|---|---|
| Tầng | Vận chuyển (TCP/SSL) |
| Năng lực | Hàng triệu request/giây |
| Chọn target | Thuật toán flow hash |
| Header | Không bị sửa |
| Dynamic host port mapping | Có — container port 80, host port 0 → port động 32768–61000 |

---

## 1. Best practice để deploy nhanh

**📘 Theo docs AWS**

Thông tin về best practice khi đặt tham số để tăng tốc deploy, xem:

- *Optimize load balancer health check parameters for Amazon ECS* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/load-balancer-healthcheck.html>)
- *Optimize load balancer connection draining parameters for Amazon ECS* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/load-balancer-connection-draining.html>)

---

## 2. Những điều cần cân nhắc

**📘 Theo docs AWS**

Cân nhắc các điểm sau khi dùng Network Load Balancer với Amazon ECS:

- Amazon ECS cần **service-linked IAM role**, cung cấp quyền cần để đăng ký và huỷ đăng ký target với load balancer khi task được tạo và dừng. Thông tin thêm, xem *Using service-linked roles for Amazon ECS* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/using-service-linked-roles.html>).

- Bạn **không thể** gắn **quá năm target group** vào một service.

- Với service ở cấu hình **IPv6-only**, bạn phải đặt loại địa chỉ IP của target group của Network Load Balancer là **`dualstack`**.

- Với service có task dùng network mode **`awsvpc`**, khi tạo target group cho service, bạn phải chọn target type **`ip`**, **không phải** `instance`. Đó là vì task dùng network mode `awsvpc` gắn với một elastic network interface, không phải một Amazon EC2 instance.

- Cấu hình subnet của load balancer phải gồm **mọi Availability Zone** mà container instance của bạn nằm trong.

- Sau khi tạo service, cấu hình load balancer **không thể thay đổi từ AWS Management Console**. Bạn có thể dùng AWS Copilot, AWS CloudFormation, AWS CLI hoặc SDK để sửa cấu hình load balancer **chỉ cho** rolling deployment controller **`ECS`**, không cho AWS CodeDeploy blue/green hay external. Khi bạn thêm, cập nhật hoặc gỡ một cấu hình load balancer, Amazon ECS bắt đầu một **deployment mới** với cấu hình Elastic Load Balancing đã cập nhật. Việc này khiến task đăng ký vào và huỷ đăng ký khỏi load balancer. Chúng tôi khuyến nghị bạn kiểm tra trên môi trường test trước khi cập nhật cấu hình Elastic Load Balancing. Thông tin về cách sửa cấu hình, xem *UpdateService* (<https://docs.aws.amazon.com/AmazonECS/latest/APIReference/API_UpdateService.html>) trong *Amazon Elastic Container Service API Reference*.

- Nếu một task của service không đạt tiêu chí health check của load balancer, task bị **dừng và khởi động lại**. Quá trình này tiếp tục cho tới khi service đạt số task đang chạy mong muốn.

- Khi bạn dùng một **Gateway Load Balancer** được cấu hình với **địa chỉ IP làm target** và **tắt Client IP Preservation**, request được nhìn thấy như đến từ **địa chỉ IP private của Gateway Load Balancer**. Nghĩa là service phía sau Gateway Load Balancer **thực tế mở ra toàn thế giới** ngay khi bạn cho phép request đến và health check trong security group của target.

- Với task **Fargate**, bạn phải dùng platform version **`1.4.0`** (Linux) hoặc **`1.0.0`** (Windows).

- Nếu gặp sự cố với service có bật load balancer, xem *Troubleshooting service load balancers in Amazon ECS* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/troubleshoot-service-load-balancers.html>).

- Khi dùng target type **`instance`**, task và load balancer phải ở **cùng VPC**. Khi dùng target type **`ip`**, hỗ trợ kết nối **chéo VPC**.

- Tính năng **giữ địa chỉ IP client** (client IP address preservation) của Network Load Balancer **tương thích** với target Fargate.

- Dùng **một target group riêng cho mỗi service**.

  Dùng cùng một target group cho nhiều service có thể gây sự cố khi deploy service.

- Bạn phải chỉ định target group **gắn với một Network Load Balancer**.

Thông tin về cách tạo Network Load Balancer, xem *Create a Network Load Balancer* (<https://docs.aws.amazon.com/elasticloadbalancing/latest/network/create-network-load-balancer.html>) trong *Network Load Balancers*.

**💡 Giải thích thêm**

| Chủ đề | Quy tắc theo trang |
|---|---|
| IAM | Cần service-linked role để đăng ký/huỷ đăng ký target |
| Số target group mỗi service | Tối đa **5** |
| IPv6-only | IP address type của target group: `dualstack` |
| Task `awsvpc` | Target type **`ip`** (không phải `instance`) |
| Subnet của load balancer | Bao gồm **mọi AZ** có container instance |
| Sửa cấu hình load balancer sau khi tạo service | Không qua console; qua Copilot/CloudFormation/CLI/SDK, **chỉ** với controller `ECS`; tạo deployment mới |
| Task không đạt health check | Dừng và khởi động lại tới khi đủ desired count |
| Fargate | Platform version `1.4.0` (Linux) / `1.0.0` (Windows) |
| `instance` target | Cùng VPC |
| `ip` target | Chéo VPC được |
| Client IP preservation | Tương thích với target Fargate |
| Target group | Mỗi service một target group riêng; phải gắn với một NLB |

Rủi ro bảo mật trang cảnh báo (Gateway Load Balancer, target là IP, tắt Client IP Preservation):

```
Request thật từ Internet ─► GWLB ─► target
target chỉ thấy IP private của GWLB
→ cho phép request + health check trong security group của target
→ thực tế mở cho toàn thế giới
```

Gạch đầu dòng này nói về **Gateway Load Balancer**, dù nằm trong trang về **Network Load Balancer**; bản dịch giữ nguyên như trang gốc.

---

## 3. Important: target type và instance type

**📘 Theo docs AWS**

> **Important:** Nếu task definition của service dùng network mode **`awsvpc`** (**bắt buộc cho Fargate**), bạn phải chọn target type **`ip`**, **không phải** `instance`. Đó là vì task dùng network mode `awsvpc` gắn với một elastic network interface, không phải một Amazon EC2 instance.
>
> Bạn **không thể** đăng ký instance **theo instance ID** nếu chúng thuộc các instance type sau: **C1, CC1, CC2, CG1, CG2, CR1, G1, G2, HI1, HS1, M1, M2, M3, và T1**. Bạn có thể đăng ký instance thuộc các type này **theo địa chỉ IP**.

**💡 Giải thích thêm**

| Trường hợp | Target type |
|---|---|
| Network mode `awsvpc` (bắt buộc với Fargate) | **`ip`** |
| Instance type C1, CC1, CC2, CG1, CG2, CR1, G1, G2, HI1, HS1, M1, M2, M3, T1 | Không đăng ký theo instance ID được → dùng **IP** |

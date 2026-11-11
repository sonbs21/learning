# Use an Application Load Balancer for Amazon ECS

> Nguồn: **Amazon ECS Developer Guide — Use an Application Load Balancer for Amazon ECS**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/alb.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: **bản dịch** sang tiếng Việt, đi đúng thứ tự và đúng nội dung của trang gốc, không thêm, không bớt ý.
> - Khối **💡 Giải thích thêm**: chỉ giải thích lại những gì **chính trang này** đã nói, cho dễ hiểu hơn. Không đưa thông tin từ trang khác hay ví dụ tự đặt.
> - Tên target type, network mode, deployment controller giữ nguyên tiếng Anh.
> - Tài liệu AWS được phát hành theo giấy phép **Creative Commons Attribution-ShareAlike 4.0**; bản dịch này ghi rõ nguồn ở trên.

---

## Mục lục

0. [Phần mở đầu: ALB và dynamic host port mapping](#0-phần-mở-đầu-alb-và-dynamic-host-port-mapping)
1. [Best practice để deploy nhanh](#1-best-practice-để-deploy-nhanh)
2. [Những điều cần cân nhắc](#2-những-điều-cần-cân-nhắc)

---

## 0. Phần mở đầu: ALB và dynamic host port mapping

**📘 Theo docs AWS**

Application Load Balancer ra quyết định định tuyến ở **tầng ứng dụng** (HTTP/HTTPS), hỗ trợ **định tuyến theo path**, và có thể định tuyến request tới **một hoặc nhiều port** trên mỗi container instance trong cluster. Application Load Balancer hỗ trợ **dynamic host port mapping**. Ví dụ, nếu container definition của task chỉ định port **80** cho container port của NGINX, và port **0** cho host port, thì host port được chọn **động** từ dải **ephemeral port** của container instance (như **32768 tới 61000** trên Amazon ECS-optimized AMI mới nhất). Khi task khởi chạy, container NGINX được đăng ký với Application Load Balancer dưới dạng tổ hợp **instance ID và port**, và traffic được phân phối tới instance ID và port tương ứng với container đó. Mapping động này cho phép bạn có **nhiều task của một service trên cùng container instance**. Thông tin thêm, xem *User Guide for Application Load Balancers* (<https://docs.aws.amazon.com/elasticloadbalancing/latest/application/>).

**💡 Giải thích thêm**

```
Container definition: containerPort = 80, hostPort = 0
        │
        ▼
Host port chọn động từ dải ephemeral (vd 32768–61000)
        │
        ▼
ALB đăng ký target = (instance ID, port động)
        → nhiều task cùng service chạy trên cùng một container instance, mỗi task một port khác
```

| | Ví dụ của trang |
|---|---|
| Container port | 80 (NGINX) |
| Host port | 0 → chọn động |
| Dải ephemeral | 32768 tới 61000 (ECS-optimized AMI mới nhất) |
| Target trong ALB | Instance ID + port |

---

## 1. Best practice để deploy nhanh

**📘 Theo docs AWS**

Thông tin về best practice khi đặt tham số để tăng tốc deploy, xem:

- *Optimize load balancer health check parameters for Amazon ECS* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/load-balancer-healthcheck.html>)
- *Optimize load balancer connection draining parameters for Amazon ECS* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/load-balancer-connection-draining.html>)

---

## 2. Những điều cần cân nhắc

**📘 Theo docs AWS**

Cân nhắc các điểm sau khi dùng Application Load Balancer với Amazon ECS:

- Amazon ECS cần **service-linked IAM role**, cung cấp quyền cần để **đăng ký và huỷ đăng ký target** với load balancer khi task được tạo và dừng. Thông tin thêm, xem *Using service-linked roles for Amazon ECS* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/using-service-linked-roles.html>).

- Với service ở cấu hình **IPv6-only**, bạn phải đặt loại địa chỉ IP của target group của Application Load Balancer là **`dualstack`** hoặc **`dualstack-without-public-ipv4`**.

- Với service có task dùng network mode **`awsvpc`**, khi tạo target group cho service, bạn phải chọn target type **`ip`**, **không phải** `instance`. Đó là vì task dùng network mode `awsvpc` gắn với một **elastic network interface**, không phải một Amazon EC2 instance.

- Nếu service cần truy cập **nhiều port được cân bằng tải**, như port **80** và **443** cho service HTTP/HTTPS, bạn có thể cấu hình **hai listener**. Một listener phụ trách **HTTPS**, chuyển request tới service, và một listener khác phụ trách **chuyển hướng request HTTP** tới port HTTPS phù hợp. Thông tin thêm, xem *Create a listener to your Application Load Balancer* (<https://docs.aws.amazon.com/elasticloadbalancing/latest/application/create-listener.html>) trong *User Guide for Application Load Balancers*.

- Cấu hình subnet của load balancer phải gồm **mọi Availability Zone** mà container instance của bạn nằm trong.

- Sau khi tạo service, cấu hình load balancer **không thể thay đổi từ AWS Management Console**. Bạn có thể dùng AWS Copilot, AWS CloudFormation, AWS CLI hoặc SDK để sửa cấu hình load balancer **chỉ cho** rolling deployment controller **`ECS`**, **không** cho AWS CodeDeploy blue/green hay external. Khi bạn thêm, cập nhật hoặc gỡ một cấu hình load balancer, Amazon ECS bắt đầu một **deployment mới** với cấu hình Elastic Load Balancing đã cập nhật. Việc này khiến task **đăng ký vào và huỷ đăng ký khỏi** load balancer. Chúng tôi khuyến nghị bạn **kiểm tra trên môi trường test** trước khi cập nhật cấu hình Elastic Load Balancing. Thông tin về cách sửa cấu hình, xem *UpdateService* (<https://docs.aws.amazon.com/AmazonECS/latest/APIReference/API_UpdateService.html>) trong *Amazon Elastic Container Service API Reference*.

- Nếu một task của service **không đạt** tiêu chí health check của load balancer, task bị **dừng và khởi động lại**. Quá trình này tiếp tục cho tới khi service đạt số task đang chạy mong muốn.

- Nếu gặp sự cố với service có bật load balancer, xem *Troubleshooting service load balancers in Amazon ECS* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/troubleshoot-service-load-balancers.html>).

- Khi dùng target type **`instance`**, task và load balancer phải ở **cùng VPC**. Khi dùng target type **`ip`**, hỗ trợ kết nối **chéo VPC**.

- Dùng **một target group riêng cho mỗi service**.

  Dùng cùng một target group cho nhiều service có thể gây sự cố khi deploy service.

- Bạn phải chỉ định target group **gắn với một Application Load Balancer**.

Thông tin về cách tạo Application Load Balancer, xem *Create an Application Load Balancer* (<https://docs.aws.amazon.com/elasticloadbalancing/latest/application/create-application-load-balancer.html>) trong *Application Load Balancers*.

**💡 Giải thích thêm**

Target type theo network mode và VPC:

| Target type | Dùng khi (theo trang) | VPC |
|---|---|---|
| **`ip`** | Task dùng `awsvpc` (**bắt buộc**) — task gắn với ENI | Hỗ trợ **chéo VPC** |
| **`instance`** | (không dùng cho `awsvpc`) | Task và load balancer **cùng VPC** |

Sửa cấu hình load balancer **sau khi** tạo service:

| Cách | Được không |
|---|---|
| AWS Management Console | ❌ |
| AWS Copilot, CloudFormation, CLI, SDK | ✅ — **chỉ** với deployment controller `ECS` (rolling) |
| CodeDeploy blue/green, external | ❌ |
| Khi sửa | ECS tạo **deployment mới**, task đăng ký/huỷ đăng ký lại → nên thử trên môi trường test trước |

Các quy tắc còn lại:

| Chủ đề | Quy tắc theo trang |
|---|---|
| IAM | Cần service-linked role để đăng ký/huỷ đăng ký target |
| IPv6-only | IP address type của target group: `dualstack` hoặc `dualstack-without-public-ipv4` |
| HTTP + HTTPS | Hai listener: 443 chuyển tới service, 80 chuyển hướng sang HTTPS |
| Subnet của load balancer | Bao gồm **mọi AZ** có container instance |
| Task không đạt health check | Bị dừng và khởi động lại, tới khi đủ desired count |
| Target group | **Mỗi service một target group riêng**; phải gắn với một ALB |

```
Listener :443 (HTTPS) ──► target group của service
Listener :80  (HTTP)  ──► redirect sang HTTPS
```

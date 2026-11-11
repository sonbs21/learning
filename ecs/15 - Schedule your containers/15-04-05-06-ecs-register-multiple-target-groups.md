# Registering multiple target groups with an Amazon ECS service

> Nguồn: **Amazon ECS Developer Guide — Registering multiple target groups with an Amazon ECS service**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/register-multiple-targetgroups.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: **bản dịch** sang tiếng Việt, đi đúng thứ tự và đúng nội dung của trang gốc, không thêm, không bớt ý. Ví dụ cấu hình giữ nguyên như trên docs.
> - Khối **💡 Giải thích thêm**: chỉ giải thích lại những gì **chính trang này** đã nói, cho dễ hiểu hơn. Không đưa thông tin từ trang khác hay ví dụ tự đặt.
> - Tên tham số, deployment controller giữ nguyên tiếng Anh.
> - Tài liệu AWS được phát hành theo giấy phép **Creative Commons Attribution-ShareAlike 4.0**; bản dịch này ghi rõ nguồn ở trên.

---

## Mục lục

0. [Phần mở đầu](#0-phần-mở-đầu)
1. [Những điều cần cân nhắc](#1-những-điều-cần-cân-nhắc)
2. [Ví dụ service definition](#2-ví-dụ-service-definition)

---

## 0. Phần mở đầu

**📘 Theo docs AWS**

Amazon ECS service có thể nhận traffic từ **nhiều load balancer** và mở **nhiều port được cân bằng tải** khi bạn chỉ định **nhiều target group** trong một service definition.

Để tạo service chỉ định nhiều target group, bạn **phải** tạo service bằng **Amazon ECS API, SDK, AWS CLI**, hoặc **CloudFormation template**. Sau khi service được tạo, bạn có thể **xem** service và các target group đăng ký với nó trên AWS Management Console. Bạn phải dùng **`UpdateService`** để sửa cấu hình load balancer của service có sẵn.

Nhiều target group có thể được chỉ định trong service definition theo định dạng sau. Cú pháp đầy đủ của service definition, xem *Service definition template* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/sd-template.html>).

```
"loadBalancers":[
   {  
      "targetGroupArn":"arn:aws:elasticloadbalancing:region:123456789012:targetgroup/target_group_name_1/1234567890123456",
      "containerName":"container_name",
      "containerPort":container_port
   },
   {  
      "targetGroupArn":"arn:aws:elasticloadbalancing:region:123456789012:targetgroup/target_group_name_2/6543210987654321",
      "containerName":"container_name",
      "containerPort":container_port
   }
]
```

**💡 Giải thích thêm**

| Việc | Làm bằng (theo trang) |
|---|---|
| Tạo service có nhiều target group | ECS API, SDK, AWS CLI, CloudFormation |
| Xem service và target group | AWS Management Console |
| Sửa cấu hình load balancer của service có sẵn | `UpdateService` |

Mỗi phần tử trong `loadBalancers` gồm 3 trường:

| Trường | Ý nghĩa |
|---|---|
| `targetGroupArn` | ARN của target group |
| `containerName` | Container nhận traffic |
| `containerPort` | Port của container đó |

---

## 1. Những điều cần cân nhắc

**📘 Theo docs AWS**

Cần cân nhắc các điểm sau khi chỉ định nhiều target group trong service definition.

- Với service dùng **Application Load Balancer** hoặc **Network Load Balancer**, bạn **không thể** gắn **quá năm target group** vào một service.
- Chỉ định nhiều target group trong service definition **chỉ được hỗ trợ** trong các điều kiện sau:
  - Service phải dùng **Application Load Balancer** hoặc **Network Load Balancer**.
  - Service phải dùng deployment controller loại **(`ECS`)**. Có thể là **Amazon ECS native/blue green deployment**, hoặc **rolling update deployment**.
- Chỉ định nhiều target group được hỗ trợ cho service chứa task dùng **cả** launch type **Fargate** và **EC2**.
- Khi tạo service chỉ định nhiều target group, **Amazon ECS service-linked role** phải được tạo. Role được tạo bằng cách **bỏ qua** tham số `role` trong API request, hoặc thuộc tính `Role` trong CloudFormation. Thông tin thêm, xem *Using service-linked roles for Amazon ECS* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/using-service-linked-roles.html>).

**💡 Giải thích thêm**

| Điều kiện | Theo trang |
|---|---|
| Loại load balancer | **ALB** hoặc **NLB** |
| Số target group tối đa | **5** mỗi service |
| Deployment controller | **`ECS`** (native/blue green hoặc rolling update) |
| Launch type | Fargate và EC2 |
| IAM | Cần **service-linked role** — để có role này, **không** truyền `role` (API) / `Role` (CloudFormation) |

---

## 2. Ví dụ service definition

**📘 Theo docs AWS**

Sau đây là một số ví dụ trường hợp sử dụng việc chỉ định nhiều target group trong service definition. Cú pháp đầy đủ của service definition, xem *Service definition template* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/sd-template.html>).

### 2.1 Load balancer riêng cho traffic nội bộ và traffic bên ngoài

**📘 Theo docs AWS**

Trong trường hợp sử dụng sau, một service dùng **hai load balancer riêng**, một cho **traffic nội bộ** và một cho **traffic từ internet**, cho **cùng container và port**.

```
"loadBalancers":[
   //Internal ELB
   {  
      "targetGroupArn":"arn:aws:elasticloadbalancing:region:123456789012:targetgroup/target_group_name_1/1234567890123456",
      "containerName":"nginx",
      "containerPort":8080
   },
   //Internet-facing ELB
   {  
      "targetGroupArn":"arn:aws:elasticloadbalancing:region:123456789012:targetgroup/target_group_name_2/6543210987654321",
      "containerName":"nginx",
      "containerPort":8080
   }
]
```

### 2.2 Mở nhiều port từ cùng một container

**📘 Theo docs AWS**

Trong trường hợp sử dụng sau, một service dùng **một load balancer** nhưng mở **nhiều port từ cùng một container**. Ví dụ, một container **Jenkins** có thể mở port **8080** cho giao diện web Jenkins và port **50000** cho API.

```
"loadBalancers":[
   {  
      "targetGroupArn":"arn:aws:elasticloadbalancing:region:123456789012:targetgroup/target_group_name_1/1234567890123456",
      "containerName":"jenkins",
      "containerPort":8080
   },
   {  
      "targetGroupArn":"arn:aws:elasticloadbalancing:region:123456789012:targetgroup/target_group_name_2/6543210987654321",
      "containerName":"jenkins",
      "containerPort":50000
   }
]
```

### 2.3 Mở port từ nhiều container

**📘 Theo docs AWS**

Trong trường hợp sử dụng sau, một service dùng **một load balancer** và **hai target group** để mở port từ **các container khác nhau**.

```
"loadBalancers":[
   {  
      "targetGroupArn":"arn:aws:elasticloadbalancing:region:123456789012:targetgroup/target_group_name_1/1234567890123456",
      "containerName":"webserver",
      "containerPort":80
   },
   {  
      "targetGroupArn":"arn:aws:elasticloadbalancing:region:123456789012:targetgroup/target_group_name_2/6543210987654321",
      "containerName":"database",
      "containerPort":3306
   }
]
```

**💡 Giải thích thêm**

So sánh ba ví dụ của trang:

| Ví dụ | Số load balancer | Container | Port | Ý tưởng |
|---|---|---|---|---|
| 2.1 Nội bộ + internet | **2** (internal, internet-facing) | `nginx` | 8080 cho cả hai | **Cùng** container + port, **hai** đường vào |
| 2.2 Nhiều port, một container | 1 | `jenkins` | 8080 (web UI), 50000 (API) | **Một** container, **nhiều** port |
| 2.3 Nhiều container | 1 | `webserver`, `database` | 80, 3306 | **Nhiều** container, mỗi cái một port |

```
2.1  internal ELB ─► TG1 ─┐
     internet ELB ─► TG2 ─┴─► nginx:8080

2.2  ELB ─► TG1 ─► jenkins:8080
         └► TG2 ─► jenkins:50000

2.3  ELB ─► TG1 ─► webserver:80
         └► TG2 ─► database:3306
```

Ví dụ 2.1 trên trang gốc có dòng chú thích `//Internal ELB` và `//Internet-facing ELB` bên trong khối cấu hình. JSON chuẩn không cho phép chú thích dạng `//`, nên khi dùng thật cần bỏ hai dòng này.

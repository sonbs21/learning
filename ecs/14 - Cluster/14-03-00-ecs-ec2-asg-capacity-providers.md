# Amazon ECS capacity providers for EC2 workloads

> Nguồn: **Amazon ECS Developer Guide — Amazon ECS capacity providers for EC2 workloads**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/asg-capacity-providers.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: diễn đạt lại bằng tiếng Việt, bám sát từng ý và đúng thứ tự của trang gốc, không thêm thông tin ngoài trang. Đây không phải bản dịch từng chữ; khi cần câu chữ chính xác, đọc link gốc ở trên.
> - Khối **💡 Giải thích thêm**: phần giải thích để dễ hiểu hơn, chỉ làm rõ những gì docs đã nói.

---

## Mục lục

0. [ASG capacity provider là gì](#0-asg-capacity-provider-là-gì)
1. [Managed scaling](#1-managed-scaling)
2. [Nên dùng Auto Scaling group mới, rỗng](#2-nên-dùng-auto-scaling-group-mới-rỗng)
3. [Khi tạo qua console](#3-khi-tạo-qua-console)
4. [Managed instance draining](#4-managed-instance-draining)
5. [Những điều cần cân nhắc](#5-những-điều-cần-cân-nhắc)
6. [Ví dụ Terraform](#6-ví-dụ-terraform)
7. [So sánh ASG – Managed Instances – Fargate](#7-so-sánh-asg--managed-instances--fargate)
8. [Tóm tắt](#8-tóm-tắt)

---

## 0. ASG capacity provider là gì

**📘 Theo docs AWS**

- Khi dùng **EC2 instance** làm capacity, bạn dùng **Auto Scaling group (ASG)** để quản lý các EC2 instance đăng ký vào cluster.
- Auto Scaling giúp luôn có **đúng số lượng** EC2 instance để gánh tải ứng dụng.

---

## 1. Managed scaling

**📘 Theo docs AWS**

- Dùng tính năng **managed scaling** để **ECS quản lý** việc scale-in và scale-out của ASG, hoặc **tự quản lý** các hành động scale.
- (Xem *Automatically manage Amazon ECS capacity with cluster auto scaling* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/cluster-auto-scaling.html>.)

**💡 Giải thích thêm**

| | Managed scaling bật | Tự quản lý |
|---|---|---|
| Ai quyết định số instance | ECS (dựa trên task cần chạy) | Bạn (scaling policy của ASG, lịch, …) |
| Task thiếu capacity | Chờ ở `PROVISIONING` trong lúc ECS scale ASG (file *Amazon ECS standalone tasks*) | Có thể không được đặt |
| Desired count ban đầu | Được để **0** (mục 5) | Bạn đặt |

---

## 2. Nên dùng Auto Scaling group mới, rỗng

**📘 Theo docs AWS**

- AWS khuyến nghị tạo **ASG mới, rỗng**.
- Dùng **ASG có sẵn**: các EC2 instance thuộc ASG đã **chạy và đăng ký vào cluster** **trước** khi ASG được dùng để tạo capacity provider có thể **không được đăng ký đúng** với capacity provider → gây vấn đề khi dùng capacity provider trong strategy.
- Dùng **`DescribeContainerInstances`** để kiểm tra một container instance có gắn với capacity provider hay không.

> **Note (docs):** Tạo ASG rỗng bằng cách đặt **desired count = 0**. Sau khi đã tạo capacity provider và gắn vào cluster, bạn mới scale out.

**💡 Giải thích thêm**

```bash
aws ecs describe-container-instances --cluster <CLUSTER> --container-instances <ARN> \
  --query 'containerInstances[].{id:ec2InstanceId,cp:capacityProviderName}'
```

Lệnh và trường `capacityProviderName` là bổ sung của mình. Instance nào không có capacity provider → nên thay (drain rồi terminate) để ASG tạo instance mới đăng ký đúng.

---

## 3. Khi tạo qua console

**📘 Theo docs AWS**

- Dùng console, ECS tạo **EC2 launch template** và **ASG** thay bạn, như một phần của **CloudFormation stack**. Tên có tiền tố **`EC2ContainerService-<ClusterName>`**.
- ASG đó dùng được làm capacity provider cho cluster.

**💡 Giải thích thêm**

Nếu bạn quản lý bằng Terraform (như file *Architect for EC2 capacity* — launch template + ASG + capacity provider), nên tự tạo các tài nguyên này bằng IaC thay vì để console sinh qua CloudFormation, tránh hai nguồn quản lý cùng một tài nguyên.

---

## 4. Managed instance draining

**📘 Theo docs AWS**

- AWS khuyến nghị dùng **managed instance draining** để terminate EC2 instance **êm**, không làm gián đoạn workload. Tính năng **bật mặc định**.
- (Xem *Safely stop Amazon ECS workloads running on EC2 instances* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/managed-instance-draining.html>.)

**💡 Giải thích thêm**

Chữ "managed instance draining" ở đây là tính năng của **ASG capacity provider** (drain instance EC2 trước khi ASG terminate), **không** liên quan tới sản phẩm **Amazon ECS Managed Instances**. Tên dễ nhầm.

---

## 5. Những điều cần cân nhắc

**📘 Theo docs AWS**

**Riêng cho ASG:**

| # | Quy tắc |
|---|---|
| 1 | ASG phải có **`MaxSize` > 0** mới scale out được |
| 2 | ASG **không được** có thiết lập **instance weighting** |
| 3 | ASG không scale out đủ cho số task cần chạy → task **kẹt, không qua được `PROVISIONING`** |
| 4 | **Không sửa** tài nguyên **scaling policy** gắn với ASG do capacity provider quản lý |
| 5 | Bật **managed scaling** khi tạo capacity provider thì **desired count** của ASG được đặt **0**; ECS quản lý scale-in và scale-out |
| 13 | ECS hỗ trợ **warm pool** của EC2 Auto Scaling — nhóm instance **khởi tạo sẵn**; khi scale out, ASG lấy từ warm pool thay vì launch instance "lạnh", cho phép chạy bước khởi tạo cuối trước khi đưa vào phục vụ. (Xem *Configuring pre-initialized instances for your Amazon ECS Auto Scaling group* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/using-warm-pool.html>.) |

**Chung cho capacity provider strategy** (giống file *Amazon ECS clusters for Fargate*):

| # | Quy tắc |
|---|---|
| 6 | Gắn capacity provider vào cluster **trước** khi đưa vào strategy |
| 7 | Tối đa **20** capacity provider trong một strategy |
| 8 | **Không** chuyển service từ ASG capacity provider sang Fargate capacity provider, và ngược lại |
| 9 | `weight` không khai báo: **console = 1**, **API/CLI = 0** |
| 10 | Ít nhất một provider có weight **> 0**; weight 0 không được dùng; tất cả weight 0 → `RunTask` / `CreateService` **thất bại** |
| 11 | Chỉ **một** provider có **base**; mặc định base **0** |
| 12 | Cluster chứa lẫn được ASG và Fargate; một strategy chỉ chứa **một** trong hai loại. Cluster chứa lẫn service/standalone task dùng capacity provider và launch type. Chuyển service từ launch type sang strategy phải **force new deployment** |

Tài liệu thêm: *Auto Scaling launch templates* — <https://docs.aws.amazon.com/autoscaling/ec2/userguide/launch-templates.html>; *Auto Scaling groups* — <https://docs.aws.amazon.com/autoscaling/ec2/userguide/auto-scaling-groups.html>.

**💡 Giải thích thêm**

Quy tắc 4 liên quan cái bẫy hay gặp với Terraform: capacity provider tạo **scaling policy** (target tracking) trên ASG và thay đổi `desired_capacity`. Nếu Terraform quản lý `desired_capacity` của ASG, mỗi lần `apply` sẽ "giành giật" với ECS. Cách thường dùng: `lifecycle { ignore_changes = [desired_capacity] }` trên ASG — kiến thức chung, không có trên trang.

Quy tắc 2 (không instance weighting) đáng lưu ý nếu ASG dùng **mixed instances policy** với `weighted_capacity`.

---

## 6. Ví dụ Terraform

**💡 Giải thích thêm**

Mở rộng ví dụ ở file *Architect for EC2 capacity*, áp các khuyến nghị trên (ASG rỗng, managed scaling, managed draining, gắn vào cluster với weight rõ ràng). Tên resource theo Terraform AWS provider; kiểm tra phiên bản provider.

```hcl
resource "aws_autoscaling_group" "ecs" {
  name_prefix         = "ecs-ec2-"
  min_size            = 0
  max_size            = 10            # MaxSize > 0
  desired_capacity    = 0             # ASG rỗng khi tạo
  vpc_zone_identifier = var.private_subnet_ids
  protect_from_scale_in = true        # thường dùng cùng managed termination protection

  launch_template {
    id      = aws_launch_template.ecs.id
    version = "$Latest"
  }

  tag {
    key                 = "AmazonECSManaged"
    value               = "true"
    propagate_at_launch = true
  }

  lifecycle {
    ignore_changes = [desired_capacity]   # để ECS điều khiển
  }
}

resource "aws_ecs_capacity_provider" "ec2" {
  name = "ec2-cp"

  auto_scaling_group_provider {
    auto_scaling_group_arn         = aws_autoscaling_group.ecs.arn
    managed_termination_protection = "ENABLED"
    managed_draining               = "ENABLED"   # managed instance draining

    managed_scaling {
      status          = "ENABLED"
      target_capacity = 90
    }
  }
}

resource "aws_ecs_cluster_capacity_providers" "this" {
  cluster_name       = aws_ecs_cluster.main.name
  capacity_providers = [aws_ecs_capacity_provider.ec2.name]

  default_capacity_provider_strategy {
    capacity_provider = aws_ecs_capacity_provider.ec2.name
    weight            = 1                         # API mặc định 0 → khai báo rõ
  }
}
```

`managed_termination_protection`, `protect_from_scale_in`, `target_capacity` là các thiết lập thường gặp của cluster auto scaling, chi tiết ở trang *cluster auto scaling* (link ở mục 1), không có trên trang này.

---

## 7. So sánh ASG – Managed Instances – Fargate

**💡 Giải thích thêm**

| | ASG capacity provider | Managed Instances | Fargate |
|---|---|---|---|
| Bạn quản lý | Launch template, AMI, ASG, agent config | Thuộc tính instance, subnet | Không gì về hạ tầng |
| Scale | Managed scaling (tuỳ chọn) | AWS tự động + consolidation | AWS |
| Drain an toàn | Managed instance draining (mặc định bật) | Drain theo vòng đời 14–21 ngày, ScaleInAfter | — |
| Warm pool | Có | — | — |
| Chuyển service sang loại khác | Không sang Fargate (ngược lại cũng không) | Có hướng dẫn migrate từ Fargate/EC2 | — |
| Weight mặc định qua API | 0 | (như quy tắc chung) | 0 |

---

## 8. Tóm tắt

- Dùng **ASG** để quản lý EC2 instance của cluster; bật **managed scaling** để ECS tự scale ASG, hoặc tự quản lý.
- Nên tạo **ASG mới, rỗng** (desired = 0); ASG có sẵn có thể khiến instance cũ **không gắn** đúng capacity provider — kiểm tra bằng `DescribeContainerInstances`.
- Console tạo launch template + ASG qua **CloudFormation**, tiền tố `EC2ContainerService-<ClusterName>`.
- Dùng **managed instance draining** (mặc định bật) để terminate instance êm.
- **ASG**: `MaxSize` > 0; không instance weighting; không đủ capacity → task kẹt ở `PROVISIONING`; không sửa scaling policy do capacity provider quản lý; managed scaling cho phép desired = 0; hỗ trợ **warm pool**.
- **Strategy**: gắn vào cluster trước; tối đa 20 provider; không chuyển service ASG ↔ Fargate; weight mặc định console 1 / API 0; cần ít nhất một weight > 0; chỉ một base (mặc định 0); một strategy chỉ ASG hoặc Fargate; chuyển launch type → strategy cần force new deployment.

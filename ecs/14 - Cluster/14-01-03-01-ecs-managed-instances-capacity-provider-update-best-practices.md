# Best practices for updating capacity providers for Amazon ECS Managed Instances

> Nguồn: **Amazon ECS Developer Guide — Best practices for updating capacity providers for Amazon ECS Managed Instances**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/capacity-provider-managed-instances-best-practices.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: diễn đạt lại bằng tiếng Việt, bám sát từng ý và đúng thứ tự của trang gốc, không thêm thông tin ngoài trang. Đây không phải bản dịch từng chữ; khi cần câu chữ chính xác, đọc link gốc ở trên.
> - Khối **💡 Giải thích thêm**: phần giải thích để dễ hiểu hơn, chỉ làm rõ những gì docs đã nói.

---

## Mục lục

1. [Nguyên tắc: coi capacity provider là bất biến](#1-nguyên-tắc-coi-capacity-provider-là-bất-biến)
2. [Quy trình 3 bước được khuyến nghị](#2-quy-trình-3-bước-được-khuyến-nghị)
3. [Lợi ích](#3-lợi-ích)
4. [Ghi chú cho CloudFormation](#4-ghi-chú-cho-cloudformation)
5. [Vì sao không nên cập nhật tại chỗ](#5-vì-sao-không-nên-cập-nhật-tại-chỗ)
6. [Áp dụng với CLI và Terraform](#6-áp-dụng-với-cli-và-terraform)
7. [Tóm tắt](#7-tóm-tắt)

---

## 1. Nguyên tắc: coi capacity provider là bất biến

**📘 Theo docs AWS**

Để **an toàn nhất** và **dễ rollback nhất**, AWS khuyến nghị coi capacity provider là **tài nguyên bất biến (immutable)**: cần đổi cấu hình thì **tạo cái mới** thay vì sửa cái cũ.

**💡 Giải thích thêm**

Cùng tinh thần với các file trước:
- Image: **tag bất biến** cho mỗi release (file *Architect your application*, *Container image pull behavior*).
- Gateway: thiết lập bất biến thì tạo gateway mới rồi chuyển service (file *Create and manage a gateway*).

---

## 2. Quy trình 3 bước được khuyến nghị

**📘 Theo docs AWS**

1. **Tạo capacity provider mới** với cấu hình đã cập nhật, **không sửa** cái hiện có.
2. **Cập nhật từng service** sang capacity provider mới và để deployment **hoàn tất**.
3. **Xoá capacity provider cũ** sau khi xác nhận cấu hình mới hoạt động đúng.

**💡 Giải thích thêm**

```
CP-v1 (đang dùng)
   │
   ├─ 1. tạo CP-v2 (cấu hình mới)
   ├─ 2. service A → CP-v2 → deploy xong → kiểm tra
   │     service B → CP-v2 → deploy xong → kiểm tra
   │     … (lỗi → đưa service về CP-v1)
   └─ 3. không còn service/task nào dùng CP-v1 → xoá CP-v1
```

Việc cập nhật service chính là bước 3 trong file *Migrate from Fargate / EC2 to Managed Instances* (`update-service --capacity-provider-strategy …`). Deployment sẽ **tạo instance mới** từ CP-v2 và dời task sang — nhờ vậy cấu hình mới được **kiểm chứng ngay**.

---

## 3. Lợi ích

**📘 Theo docs AWS**

| Lợi ích | Nội dung |
|---|---|
| **Rollout có kiểm soát** | Cập nhật **từng service một** và theo dõi ảnh hưởng |
| **Rollback dễ** | Có vấn đề thì nhanh chóng đưa service **về capacity provider cũ** |
| **Giảm phạm vi ảnh hưởng (blast radius)** | Lỗi ở cấu hình mới **không lập tức** ảnh hưởng mọi workload |

---

## 4. Ghi chú cho CloudFormation

**📘 Theo docs AWS**

> **Note (docs):** Dùng **CloudFormation** thì cân nhắc **giữ capacity provider cũ** tới một lần deploy **sau**, để vẫn **rollback được** thay đổi của stack.

**💡 Giải thích thêm**

Nếu cùng một lần cập nhật stack vừa chuyển service sang CP-v2 vừa xoá CP-v1, khi stack rollback sẽ không còn CP-v1 để quay về. Tách thành hai lần deploy: lần 1 thêm CP-v2 và chuyển service; lần 2 (sau khi ổn định) mới xoá CP-v1.

---

## 5. Vì sao không nên cập nhật tại chỗ

**📘 Theo docs AWS**

- **Có thể** cập nhật capacity provider tại chỗ, nhưng cách này tạo **phạm vi ảnh hưởng lớn và khó kiểm soát**.
- Cập nhật tại chỗ áp cấu hình mới cho **mọi capacity mới được cấp từ nay về sau**, nhưng **không kích hoạt** deployment của service.
- Vì vậy bạn có thể **không phát hiện** lỗi cấu hình cho tới **rất lâu sau**, khi service cần **scale**.

**💡 Giải thích thêm**

Ví dụ minh hoạ (của mình): đổi `allowedInstanceTypes` tại chỗ sang một family không có trong subnet đã chọn, hoặc đổi instance profile thiếu quyền.

```
Hôm nay:  update CP tại chỗ → không có gì xảy ra (instance cũ vẫn chạy)
2 tuần sau: instance cũ tới hạn 14–21 ngày bị drain (file Patching)
            hoặc service scale out lúc cao điểm
            → instance mới launch với cấu hình lỗi → task kẹt PENDING / PROVISIONING
            → sự cố xảy ra đúng lúc cần capacity nhất
```

Điều này khớp với file *Updating a cluster*: đổi capacity provider thì instance cũ vẫn chạy, chỉ instance mới dùng cấu hình mới.

| | Tạo mới (immutable) | Cập nhật tại chỗ |
|---|---|---|
| Khi nào phát hiện lỗi | Ngay khi deploy service | Khi instance mới được tạo (có thể rất lâu sau) |
| Phạm vi | Từng service | Mọi workload dùng capacity provider đó |
| Rollback | Đưa service về CP cũ | Phải sửa lại cấu hình, chờ instance mới |

---

## 6. Áp dụng với CLI và Terraform

**💡 Giải thích thêm**

Phần này là mình bổ sung, trang chỉ mô tả quy trình.

**CLI:**

```bash
# 1. Tạo CP mới
aws ecs create-capacity-provider --cluster <CLUSTER> --name <CP_V2> --managed-instances-provider '{ ... cấu hình mới ... }'

# 2. Chuyển từng service
aws ecs update-service --cluster <CLUSTER> --service <SERVICE_A> \
  --capacity-provider-strategy capacityProvider=<CP_V2>,weight=1 --force-new-deployment
# … theo dõi, lặp với các service khác

# 3. Xoá CP cũ khi không còn dùng
aws ecs delete-capacity-provider --capacity-provider <CP_V1>
```

Nhớ cập nhật cả **strategy mặc định của cluster** (nếu đang trỏ CP-v1) và các nơi chạy **standalone task / EventBridge Scheduler** trước khi xoá CP-v1.

**Terraform** — đặt tên có hậu tố phiên bản để Terraform tạo tài nguyên mới thay vì sửa tại chỗ:

```hcl
resource "aws_ecs_capacity_provider" "mi_v2" {
  name = "mi-${var.cluster_name}-v2"
  # ... cấu hình Managed Instances mới ...
}

resource "aws_ecs_service" "api" {
  # ...
  capacity_provider_strategy {
    capacity_provider = aws_ecs_capacity_provider.mi_v2.name
    weight            = 1
  }
}
```

Giữ resource `mi_v1` ở lần `apply` đầu; xoá ở lần `apply` sau khi đã xác nhận ổn (tương tự ghi chú CloudFormation ở mục 4). Kiểm tra provider Terraform đã hỗ trợ khối cấu hình Managed Instances trong `aws_ecs_capacity_provider` chưa.

---

## 7. Tóm tắt

- Coi capacity provider Managed Instances là **bất biến** để an toàn và dễ rollback.
- Quy trình: **tạo CP mới** → **chuyển từng service** và chờ deploy xong → **xoá CP cũ** khi đã ổn.
- Lợi ích: rollout có kiểm soát, rollback dễ, giảm blast radius.
- **CloudFormation**: giữ CP cũ tới lần deploy sau để còn rollback stack.
- **Cập nhật tại chỗ** áp cho capacity mới nhưng **không** kích hoạt deployment → lỗi cấu hình có thể chỉ lộ ra **rất lâu sau**, khi service cần scale.

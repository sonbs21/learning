# Amazon ECS Managed Instances container image pull behavior

> Nguồn: **Amazon ECS Developer Guide — Amazon ECS Managed Instances container image pull behavior**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/managed-instance-pull-behavior.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: diễn đạt lại bằng tiếng Việt, bám sát từng ý và đúng thứ tự của trang gốc, không thêm thông tin ngoài trang. Đây không phải bản dịch từng chữ; khi cần câu chữ chính xác, đọc link gốc ở trên.
> - Khối **💡 Giải thích thêm**: phần giải thích để dễ hiểu hơn, chỉ làm rõ những gì docs đã nói.

---

## Mục lục

1. [Kích thước image và thời gian khởi động](#1-kích-thước-image-và-thời-gian-khởi-động)
2. [Cache image trên instance](#2-cache-image-trên-instance)
3. [Tự dọn image (timer 15 phút)](#3-tự-dọn-image-timer-15-phút)
4. [Container image resolution (tag → digest)](#4-container-image-resolution-tag--digest)
5. [So sánh với EC2](#5-so-sánh-với-ec2)
6. [Gợi ý thực tế](#6-gợi-ý-thực-tế)
7. [Tóm tắt](#7-tóm-tắt)

---

## 1. Kích thước image và thời gian khởi động

**📘 Theo docs AWS**

- Thời gian khởi động container phụ thuộc **kích thước image**.
- Image lớn — bản đầy đủ của **Debian**, **Ubuntu**, **Amazon Linux 2** — khởi động lâu hơn vì chứa nhiều dịch vụ hơn bản **slim** (Debian-slim, Ubuntu-slim, Amazon-slim) hoặc base image tối giản như **Alpine**.

**💡 Giải thích thêm**

Ý này giống phần mở đầu của file *Container image pull behavior* (EC2). Khác biệt nằm ở các mục sau.

---

## 2. Cache image trên instance

**📘 Theo docs AWS**

- Khi ECS launch một container instance Managed Instances **mới**, **cache image cục bộ trống**.
- **Task đầu tiên** chạy trên instance đó sẽ **pull image** từ repository vào cache cục bộ.
- Các task **sau** dùng **cùng image** trên instance đó dùng **bản cache**, không pull lại.

**💡 Giải thích thêm**

```
Instance mới (cache trống)
  Task 1 (image A) → pull A từ registry → lưu cache        ← chậm
  Task 2 (image A) → dùng cache                             ← nhanh
  Task 3 (image B) → pull B → lưu cache                     ← chậm
```

Vì Managed Instances **mặc định đặt nhiều task trên một instance** (file *Architect for Amazon ECS Managed Instances*), task cùng service trên cùng instance hưởng lợi từ cache. Cache nằm trên **EBS data volume** (hoặc instance store) của instance — image chiếm cả bản nén và giải nén.

Lưu ý: instance có **vòng đời tối đa 14 ngày** — instance thay thế lại bắt đầu với cache trống.

---

## 3. Tự dọn image (timer 15 phút)

**📘 Theo docs AWS**

- ECS agent trên Managed Instances **tự quản lý dung lượng đĩa** của từng instance.
- Khi một task **dừng** và **không còn task đang chạy nào** dùng cùng image, ECS bắt đầu **timer dọn dẹp 15 phút** cho image đó.
- Nếu trong **15 phút** không có task mới dùng image đó khởi động trên instance, image bị **xoá khỏi cache**.

**💡 Giải thích thêm**

```
Task cuối dùng image A dừng
  │
  ├─ trong 15 phút có task mới dùng A → giữ A trong cache
  └─ quá 15 phút không có               → xoá A khỏi cache
```

Hệ quả: workload chạy **thưa thớt** (vd scheduled task mỗi giờ) gần như lần nào cũng phải pull lại image, vì cache bị dọn sau 15 phút — suy luận từ quy tắc trên.

---

## 4. Container image resolution (tag → digest)

**📘 Theo docs AWS**

- ECS **resolve tag image thành digest** để mọi task trong service chạy **cùng một phiên bản image**.
- Hành vi này **ảnh hưởng tới bin packing và việc chọn instance** của Managed Instances: ECS **lập lịch một task** của service **trước** để lấy digest, rồi mới lập lịch **các task còn lại**.
- (Xem *Container image resolution* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/deployment-type-ecs.html#deployment-container-image-stability>.)

**💡 Giải thích thêm**

```
Service desired count = 5
  Bước 1: lập lịch 1 task → resolve tag → digest (vd sha256:abc…)
  Bước 2: lập lịch 4 task còn lại với digest đó
```

- Đây là cơ chế `versionConsistency` (mặc định `enabled`) trong các file *task definition parameters*.
- Vì task đầu được đặt riêng, các task sau có thể không được "xếp chung" tối ưu với task đầu — đó là lý do docs nói nó **ảnh hưởng bin packing**. Liên hệ file *Managed Instances auto scaling and task placement* (bin packing dễ xảy ra lúc mới launch). Phần giải thích cơ chế ảnh hưởng là suy luận của mình.
- Nếu muốn bỏ bước này (vd khi đã dùng tag bất biến hoặc digest), có thể đặt `versionConsistency: disabled` cho container — trang này không đề cập, đây là liên hệ từ file *task definition parameters*.

---

## 5. So sánh với EC2

**💡 Giải thích thêm**

Đặt cạnh file *Container image pull behavior for EC2 and external instances*:

| | EC2 / external instance | Managed Instances |
|---|---|---|
| Cấu hình pull | `ECS_IMAGE_PULL_BEHAVIOR` trong `/etc/ecs/ecs.config` (`default`, `always`, `once`, `prefer-cached`) | **Không có** cấu hình tương tự trên trang này (không truy cập được AMI/agent — không custom AMI, không SSH) |
| Dùng cache | Tuỳ giá trị `ECS_IMAGE_PULL_BEHAVIOR` | Task sau dùng cache nếu image còn |
| Dọn image | Theo cấu hình cleanup của agent (bị tắt với `prefer-cached`) | **Tự động**: xoá sau **15 phút** không còn task dùng |
| Bake image vào AMI | Được | Không (không custom AMI) |
| Tag → digest | Có (`versionConsistency`) | Có, và ảnh hưởng bin packing |

---

## 6. Gợi ý thực tế

**💡 Giải thích thêm**

Gợi ý của mình, ghép từ trang này và các file trước:

| Mục tiêu | Cách |
|---|---|
| Khởi động nhanh hơn | Dùng image **slim/Alpine**, giảm số layer và dung lượng (theo mục 1) |
| Tránh pull từ internet | Đặt registry gần: **ECR cùng Region**, VPC endpoint cho ECR (file *task networking*) |
| Ổn định phiên bản | Tag **bất biến** (git SHA) hoặc digest (file *Architect your application*) |
| Đủ chỗ cho cache | Tăng `storageSizeGiB` nếu image lớn hoặc nhiều image khác nhau trên một instance (file *Architect for Amazon ECS Managed Instances*) |
| Workload chạy thưa | Chấp nhận pull lại sau 15 phút, hoặc gom lịch chạy gần nhau |

---

## 7. Tóm tắt

- Image lớn (Debian, Ubuntu, Amazon Linux 2 đầy đủ) khởi động chậm hơn bản slim hoặc Alpine.
- Instance Managed Instances mới có **cache trống**; task đầu pull image, task sau dùng cache.
- ECS agent tự quản lý đĩa: task cuối dùng image dừng → **timer 15 phút** → không có task mới dùng image thì **xoá** image khỏi cache.
- ECS **resolve tag → digest** để mọi task trong service chạy cùng phiên bản; để làm vậy, ECS lập lịch **một task trước** rồi mới các task còn lại → ảnh hưởng bin packing và chọn instance.

# Amazon ECS Managed Instances instance types

> Nguồn: **Amazon ECS Developer Guide — Amazon ECS Managed Instances instance types**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/managed-instances-instance-types.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: diễn đạt lại bằng tiếng Việt, bám sát từng ý và đúng thứ tự của trang gốc, không thêm thông tin ngoài trang. Đây không phải bản dịch từng chữ; khi cần câu chữ chính xác, đọc link gốc ở trên.
> - Khối **💡 Giải thích thêm**: phần giải thích để dễ hiểu hơn, chỉ làm rõ những gì docs đã nói.
> - Tên trường, giá trị và tên instance family giữ nguyên như docs.

---

## Mục lục

1. [Tổng quan](#1-tổng-quan)
2. [Hai cách chọn instance type](#2-hai-cách-chọn-instance-type)
3. [Chọn instance type cụ thể](#3-chọn-instance-type-cụ-thể)
4. [Chọn theo thuộc tính (attribute-based)](#4-chọn-theo-thuộc-tính-attribute-based)
5. [Tính tiền và hình thức mua](#5-tính-tiền-và-hình-thức-mua)
6. [Instance type được hỗ trợ](#6-instance-type-được-hỗ-trợ)
7. [Ví dụ cấu hình](#7-ví-dụ-cấu-hình)
8. [Tóm tắt](#8-tóm-tắt)

---

## 1. Tổng quan

**📘 Theo docs AWS**

Khi tạo capacity provider Managed Instances, bạn chọn cách ECS chọn EC2 instance type cho task:
- để ECS **tự chọn**;
- chỉ định **instance type hoặc family cụ thể**;
- hoặc định nghĩa **thuộc tính phần cứng** mà instance phải đáp ứng.

---

## 2. Hai cách chọn instance type

**📘 Theo docs AWS**

| Cách | Nội dung |
|---|---|
| **Instance type hoặc family cụ thể** | Chỉ định chính xác instance type (vd `m6i.xlarge`) hoặc family bằng wildcard (vd `c6i.*`, `m6*.*`). Dùng **`allowedInstanceTypes`** để đưa vào, **`excludedInstanceTypes`** để loại ra |
| **Chọn theo thuộc tính** | Định nghĩa yêu cầu phần cứng (số vCPU, memory, hãng CPU, loại accelerator…) → ECS chọn instance type **thoả mọi thuộc tính** |

- **Không** chỉ định instance type hay thuộc tính → ECS **tự chọn** instance type **tối ưu chi phí nhất** dựa trên yêu cầu của task definition.

**💡 Giải thích thêm**

Khớp file *Architect for Amazon ECS Managed Instances*: "default capacity provider" = tự chọn; "custom capacity provider" = một trong hai cách trên.

---

## 3. Chọn instance type cụ thể

**📘 Theo docs AWS**

- Kiểm soát chính xác ECS được dùng **instance type nào**: từng type, cả family, hoặc kết hợp.
- `allowedInstanceTypes` (đưa vào) và `excludedInstanceTypes` (loại ra) đều hỗ trợ **wildcard**:

| Mẫu | Ý nghĩa |
|---|---|
| `m6i.xlarge` | Đúng một instance type |
| `c6i.*` | Mọi kích thước của family c6i |
| `m6*.*` | Mọi family thế hệ m6 (m6i, m6a, m6g…) |

**💡 Giải thích thêm**

| Muốn | Cách |
|---|---|
| Chỉ chạy Graviton thế hệ 7 | `allowedInstanceTypes: ["c7g.*", "m7g.*", "r7g.*"]` |
| Mọi loại trừ instance burstable | `excludedInstanceTypes: ["t3.*", "t3a.*", "t4g.*"]` |

Ví dụ do mình đặt. Lưu ý: chọn family Graviton (`*g`) thì image phải build cho **ARM64** và task definition nên khai báo `cpuArchitecture: ARM64` (file *task definition parameters for Managed Instances*).

---

## 4. Chọn theo thuộc tính (attribute-based)

**📘 Theo docs AWS**

- Định nghĩa yêu cầu phần cứng → ECS chọn instance type đáp ứng. Linh hoạt và giúp task **đặt được** ngay cả khi một instance type cụ thể **không còn sẵn**.
- Nhiều **thuộc tính** → instance phải thoả **tất cả** (AND).
- Một thuộc tính có nhiều **giá trị** → instance chỉ cần thoả **một** giá trị (OR).

Các nhóm thuộc tính lọc được:

| Nhóm | Thuộc tính |
|---|---|
| **CPU và memory** | Số vCPU, memory (MiB), tỉ lệ memory/vCPU, hãng CPU (**Intel**, **AMD**, **AWS Graviton**), thế hệ instance (hiện tại hoặc cũ) |
| **Local storage** | Include / exclude / require local storage; loại (HDD hoặc SSD); tổng dung lượng |
| **Mạng và hiệu năng** | Băng thông mạng (Gbps), số network interface, băng thông EBS cơ bản (Mbps), hỗ trợ burstable, bare metal |
| **Accelerator** | Số lượng, hãng, tên chip, loại, tổng memory của accelerator |
| **Price protection** | Giá Spot và On-Demand tối đa, tính theo **% so với instance type rẻ nhất** khớp yêu cầu |

Danh sách thuộc tính và giá trị đầy đủ: *InstanceRequirementsRequest* — <https://docs.aws.amazon.com/AmazonECS/latest/APIReference/API_InstanceRequirementsRequest.html>.

**💡 Giải thích thêm**

```
vCPU 2–8   AND   memory 4–32 GiB   AND   hãng CPU ∈ {intel, amd}
                                         └── OR giữa các giá trị
```

- **Local storage** ở đây chính là `localStorage`, `localStorageTypes`, `totalLocalStorageGB` trong ví dụ instance store của file *Architect for Amazon ECS Managed Instances*.
- **Price protection** giúp tránh ECS chọn instance quá đắt chỉ vì nó khớp thuộc tính — gợi ý của mình về công dụng.
- Chọn theo thuộc tính thường **bền hơn** chọn type cụ thể khi một type hết capacity ở AZ — đúng ý docs "đặt được ngay cả khi type cụ thể không sẵn".

---

## 5. Tính tiền và hình thức mua

**📘 Theo docs AWS**

Chọn **mô hình cấp phát** khi tạo capacity provider:

| Mô hình | Nội dung | Cấu hình |
|---|---|---|
| **On-Demand** (mặc định) | Trả theo giờ, không cam kết dài hạn, không trả trước | Mặc định |
| **Spot** | Dùng capacity EC2 dư với **giá thấp hơn**; có thể **bị thu hồi** khi EC2 cần lại | `capacityOptionType` = **`Spot`** |
| **Capacity Reservations** | Đặt trước capacity EC2 ở một **AZ cụ thể** | `capacityOptionType` = **`Reserved`** + cung cấp **capacity reservation group** (xem <https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/create-cr-group.html>). Tuỳ chọn ưu tiên: **`reservations-only`**, **`reservations-first`**, **`reservations-excluded`** |

- Managed Instances **tự hưởng** Savings Plans và Reserved Instances bạn đã mua cho các instance type mà task dùng — **không cần cấu hình thêm**.

**💡 Giải thích thêm**

| Ưu tiên reservation | Ý nghĩa (suy ra từ tên giá trị) |
|---|---|
| `reservations-only` | Chỉ dùng capacity đã đặt trước |
| `reservations-first` | Dùng reservation trước, hết thì dùng On-Demand |
| `reservations-excluded` | Không dùng reservation |

Liên hệ file *Architect for Amazon ECS Managed Instances*: vì một capacity provider strategy chỉ chứa **một loại** provider, muốn trộn On-Demand và Spot có lẽ cần **hai capacity provider Managed Instances** (một Spot, một On-Demand) trong cùng strategy với base/weight — suy luận của mình, cần kiểm tra.

Với Spot, task có thể bị dừng khi instance bị thu hồi → phù hợp workload chịu gián đoạn (batch, xử lý hàng đợi), và ứng dụng cần tắt êm (file *Task lifecycle*).

---

## 6. Instance type được hỗ trợ

**📘 Theo docs AWS**

| Nhóm | Family được hỗ trợ |
|---|---|
| **Compute optimized** | C5, C5a, C5ad, C5d, C5n, C6a, C6g, C6gd, C6i, C6id, C6in, C7a, C7g, C7gd, C7gn, C7i, C7i-flex, C8a, C8g, C8gb, C8gd, C8gn, C8i, C8ib, C8id, C8i-flex, C8in, C8ine |
| **General purpose** | M5, M5a, M5ad, M5d, M5dn, M5n, M5zn, M6a, M6g, M6gd, M6i, M6id, M6idn, M6in, M7a, M7g, M7gd, M7i, M7i-flex, M8a, M8azn, M8g, M8gb, M8gd, M8gn, M8i, M8ib, M8id, M8idb, M8idn, M8i-flex, M8in, M8ine, M9g, M9gd, T3, T3a, T4g |
| **Memory optimized** | R5, R5a, R5ad, R5b, R5d, R5dn, R5n, R6a, R6g, R6gd, R6i, R6id, R6idn, R6in, R7a, R7g, R7gd, R7i, R7iz, R8a, R8g, R8gb, R8gd, R8gn, U-3tb1, U7i-6tb, U7i-8tb, U7i-12tb, U7in-24tb, U7in-32tb, X2gd, X2idn, X2iedn, X2iezn, X8g, Z1d |
| **Storage optimized** | D3, D3en, I3en, I4g, I4i, I7i, I7ie, I8g, I8ge, Im4gn, Is4gen |
| **Accelerated computing** | DL1, G4ad, G4dn, G5, G5g, G6, G6e, G6f, G7e, Gr6, Gr6f, Inf1, Inf2, P3dn, P4d, P4de, P5, P5en, P6-B200, P6-B300, Trn1 |
| **High-performance computing** | Hpc6a, Hpc6id, Hpc7a, Hpc7g |

Ngoài ra, Managed Instances **chỉ tạo** instance thoả:
- **nhiều hơn 1 vCPU**;
- kích thước **không phải** `nano` hoặc `micro`.

(Xem *Amazon EC2 instance type naming conventions* — <https://docs.aws.amazon.com/ec2/latest/instancetypes/instance-type-names.html>.)

**💡 Giải thích thêm**

Đọc tên family nhanh (quy ước chung của EC2, chi tiết ở link naming conventions):

| Ký tự | Ý nghĩa thường gặp | Ví dụ |
|---|---|---|
| `g` | **Graviton** (ARM) | C7**g**, M8**g** |
| `a` | **AMD** | M6**a** |
| `i` | **Intel** | C6**i** |
| `d` | Có **local NVMe** (instance store) | M6**id**, C7**gd** |
| `n` | Mạng hiệu năng cao | C6**in** |
| `-flex` | Biến thể "flex" giá tốt | M7i-**flex** |

Liên hệ các file trước:
- **G6f** xuất hiện trong danh sách accelerated → khớp ghi chú **GPU phân đoạn** trên G6f ở file *task definition parameters for Managed Instances*.
- Family có hậu tố **`d`** phù hợp khi bật **local storage** (instance store) — file *Architect for Amazon ECS Managed Instances*.
- Quy tắc "> 1 vCPU, không nano/micro" nghĩa là các size nhỏ nhất của **T3/T3a/T4g** sẽ không được dùng dù family có trong danh sách — suy luận từ hai quy tắc trên.

---

## 7. Ví dụ cấu hình

**💡 Giải thích thêm**

Trang không có ví dụ JSON. Phần dưới là mình minh hoạ dựa trên các tên trường xuất hiện trong trang này và file *Architect for Amazon ECS Managed Instances* (`instanceRequirements`, `allowedInstanceTypes`, `excludedInstanceTypes`, `capacityOptionType`); vị trí chính xác của từng trường nên đối chiếu *InstanceRequirementsRequest* và API tạo capacity provider.

**Chọn theo thuộc tính, chỉ Graviton, loại instance burstable:**

```json
"instanceRequirements": {
  "vCpuCount": { "min": 2, "max": 16 },
  "memoryMiB": { "min": 4096 },
  "cpuManufacturers": ["amazon-web-services"],
  "excludedInstanceTypes": ["t4g.*"]
}
```

**Spot cho workload batch:**

```json
"capacityOptionType": "Spot"
```

---

## 8. Tóm tắt

- Ba cách để ECS chọn instance: **tự động** (rẻ nhất theo task definition), **type/family cụ thể**, **theo thuộc tính**.
- **Type cụ thể**: `allowedInstanceTypes` / `excludedInstanceTypes`, hỗ trợ wildcard (`m6i.xlarge`, `c6i.*`, `m6*.*`).
- **Theo thuộc tính**: nhiều thuộc tính = AND, nhiều giá trị trong một thuộc tính = OR; nhóm CPU/memory (Intel, AMD, Graviton), local storage, mạng/hiệu năng, accelerator, price protection. Chi tiết ở *InstanceRequirementsRequest*.
- **Mua**: On-Demand (mặc định), **Spot** (`capacityOptionType: Spot`, có thể bị thu hồi), **Capacity Reservations** (`Reserved` + reservation group; `reservations-only` / `-first` / `-excluded`). Savings Plans và Reserved Instances tự áp dụng.
- **Hỗ trợ** các nhóm: compute (C), general purpose (M, T), memory (R, U, X, Z), storage (D, I), accelerated (G, P, Inf, Trn, DL), HPC.
- Chỉ tạo instance **> 1 vCPU** và **không phải nano/micro**.

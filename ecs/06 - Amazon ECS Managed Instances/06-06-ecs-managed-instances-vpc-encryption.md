# Enabling VPC Encryption control for Amazon ECS Managed Instances

> Nguồn: **Amazon ECS Developer Guide — Enabling VPC Encryption control for Amazon ECS Managed Instances**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/managed-instances-vpc-encryption.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: diễn đạt lại bằng tiếng Việt, bám sát từng ý và đúng thứ tự của trang gốc, không thêm thông tin ngoài trang. Đây không phải bản dịch từng chữ; khi cần câu chữ chính xác, đọc link gốc ở trên.
> - Khối **💡 Giải thích thêm**: phần giải thích để dễ hiểu hơn, chỉ làm rõ những gì docs đã nói.

---

## Mục lục

0. [VPC Encryption Controls là gì](#0-vpc-encryption-controls-là-gì)
1. [Điều kiện tiên quyết](#1-điều-kiện-tiên-quyết)
2. [Xác định instance type tương thích](#2-xác-định-instance-type-tương-thích)
3. [Tạo cluster hỗ trợ mã hoá VPC](#3-tạo-cluster-hỗ-trợ-mã-hoá-vpc)
4. [Những điều cần cân nhắc](#4-những-điều-cần-cân-nhắc)
5. [Xử lý sự cố](#5-xử-lý-sự-cố)
6. [Liên hệ với các file trước](#6-liên-hệ-với-các-file-trước)
7. [Tóm tắt](#7-tóm-tắt)

---

## 0. VPC Encryption Controls là gì

**📘 Theo docs AWS**

- Managed Instances hỗ trợ **VPC Encryption Controls** — tính năng bảo mật và tuân thủ cho phép **kiểm soát tập trung**, **giám sát** và **bắt buộc mã hoá khi truyền (encryption in transit)** cho **mọi luồng traffic** trong và giữa các VPC trong một Region.
- Khi bật VPC Encryption Controls trên subnet, bạn chỉ định trong **custom capacity provider** các instance type **hỗ trợ mã hoá khi truyền** → bảo đảm workload Managed Instances chạy **có mã hoá khi truyền**.

**💡 Giải thích thêm**

Mã hoá khi truyền ở đây là ở **tầng hạ tầng mạng** giữa các instance — không thay thế TLS ở tầng ứng dụng. Ý chính của trang: **không phải instance type nào cũng hỗ trợ**, nên phải giới hạn capacity provider vào các type hỗ trợ.

---

## 1. Điều kiện tiên quyết

**📘 Theo docs AWS**

- Một **VPC** đã **bật encryption in transit** trên các subnet. (Xem *VPC encryption controls documentation* — <https://docs.aws.amazon.com/vpc/latest/userguide/vpc-encryption-controls.html>.)
- Một **custom capacity provider** của Managed Instances. (Xem *Architect for Amazon ECS Managed Instances* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/ManagedInstances.html>.)

**💡 Giải thích thêm**

Phải là **custom** capacity provider (không phải default), vì cần chỉ định danh sách instance type cho phép (file *Managed Instances instance types*).

---

## 2. Xác định instance type tương thích

**📘 Theo docs AWS**

Instance type phải thoả **hai** điều kiện:

1. **Hỗ trợ VPC encryption in transit** — liệt kê bằng AWS CLI, lọc theo thuộc tính `network-info.encryption-in-transit-supported = true`:

```bash
aws ec2 describe-instance-types \
  --filters Name=network-info.encryption-in-transit-supported,Values=true \
  --query "InstanceTypes[*].[InstanceType]" \
  --output text | sort
```

2. **Được Managed Instances hỗ trợ** — xem danh sách ở *Amazon ECS Managed Instances instance types* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/managed-instances-instance-types.html>.

Nếu có yêu cầu khác (CPU, memory, kiến trúc…), lọc tiếp danh sách theo workload.

**💡 Giải thích thêm**

```
Instance type dùng được = (hỗ trợ encryption in transit)
                        ∩ (Managed Instances hỗ trợ)
                        ∩ (khớp yêu cầu workload: vCPU, memory, ARM/x86…)
```

Lệnh `describe-instance-types` trả kết quả **theo Region** đang cấu hình CLI — chạy đúng Region bạn triển khai (thêm `--region`). Gợi ý của mình.

---

## 3. Tạo cluster hỗ trợ mã hoá VPC

**📘 Theo docs AWS**

1. Tạo cluster mới, chọn hạ tầng **Fargate and Managed Instances**.
2. Chọn **Use custom – advanced** để mở thêm tham số cấu hình.
3. Ở **Allowed Instance types**, chỉ thêm các instance type **hỗ trợ** VPC encryption in transit.

Cấu hình như vậy, Managed Instances **chỉ launch** các EC2 instance type hỗ trợ VPC encryption in transit.

**💡 Giải thích thêm**

**Allowed Instance types** trên console tương ứng với trường `allowedInstanceTypes` (file *Managed Instances instance types*). Ví dụ dạng cấu hình (giá trị là placeholder, lấy từ kết quả lệnh ở mục 2):

```json
"instanceRequirements": {
  "allowedInstanceTypes": ["<TYPE_HỖ_TRỢ_1>", "<TYPE_HỖ_TRỢ_2>"]
}
```

Vị trí chính xác của trường nên đối chiếu API tạo capacity provider. Không nên dùng wildcard rộng (vd `m6*.*`) trừ khi đã chắc **mọi** type khớp đều hỗ trợ encryption in transit — suy luận từ mục 4.

---

## 4. Những điều cần cân nhắc

**📘 Theo docs AWS**

| Điểm | Nội dung |
|---|---|
| **Instance burstable** | **T3**, **T3a**, **T4g** **không hỗ trợ** VPC encryption in transit → **không dùng được** trong subnet bật encryption control ở chế độ **Enforced** |
| **Chuyển chế độ** | Chỉ chuyển subnet từ **Monitor** sang **Enforced** khi **mọi instance đang chạy** đều hỗ trợ encryption in transit |
| **Task không launch được** | Ở **Enforced**, chỉ định instance type không hỗ trợ thì task sẽ **launch thất bại** |

**💡 Giải thích thêm**

| Chế độ | Ý nghĩa (suy ra từ tên và mô tả) |
|---|---|
| **Monitor** | Chỉ **giám sát** traffic chưa mã hoá, không chặn |
| **Enforced** | **Bắt buộc** — tài nguyên không hỗ trợ không chạy được |

Lộ trình gợi ý (của mình):

```
1. Bật Monitor trên subnet
2. Đổi capacity provider sang allowedInstanceTypes chỉ gồm type hỗ trợ
3. Chờ instance cũ được thay (drain theo vòng đời 14–21 ngày hoặc deploy lại service)
4. Kiểm tra không còn tài nguyên chặn (mục 5)
5. Chuyển sang Enforced
```

Bước 3 dựa trên file *Patching in Amazon ECS Managed Instances*: instance cũ chỉ được thay khi drain; đổi cấu hình capacity provider không thay instance đang chạy ngay.

---

## 5. Xử lý sự cố

**📘 Theo docs AWS**

| Sự cố | Cách xử lý |
|---|---|
| **Task launch thất bại ở Enforced** | Kiểm tra **mọi** instance type đã chỉ định có hỗ trợ VPC encryption in transit bằng lệnh CLI ở mục 2 |
| **Không chuyển được sang Enforced** | Dùng console hoặc lệnh **`GetVpcResourcesBlockingEncryptionEnforcement`** để tìm tài nguyên **chưa** thực thi mã hoá khi truyền |

Thêm thông tin: *VPC encryption controls documentation* — <https://docs.aws.amazon.com/vpc/latest/userguide/vpc-encryption-controls.html>.

**💡 Giải thích thêm**

Tài nguyên chặn chuyển sang Enforced có thể không chỉ là Managed Instances — mọi tài nguyên trong VPC (instance EC2 khác, …) đều cần hỗ trợ. Đây là suy luận từ việc VPC Encryption Controls áp cho mọi luồng traffic trong VPC (mục 0).

---

## 6. Liên hệ với các file trước

**💡 Giải thích thêm**

| Nội dung | File liên quan |
|---|---|
| Custom capacity provider, `allowedInstanceTypes` | *Managed Instances instance types* |
| T3/T3a/T4g nằm trong danh sách Managed Instances hỗ trợ, nhưng bị loại khi Enforced | *Managed Instances instance types* |
| Thay instance theo vòng đời | *Patching in Amazon ECS Managed Instances* |
| Các tính năng tuân thủ khác (FIPS, CMK) | *Security considerations for Amazon ECS Managed Instances* |
| Chọn theo thuộc tính để đặt task dễ hơn | *Instance selection best practices* — với encryption control, danh sách type bị thu hẹp nên cần chọn đủ nhiều type để tránh thiếu capacity (suy luận) |

---

## 7. Tóm tắt

- **VPC Encryption Controls**: giám sát và bắt buộc mã hoá khi truyền cho mọi traffic trong/giữa VPC trong Region.
- **Tiên quyết**: VPC có subnet bật encryption in transit; **custom** capacity provider Managed Instances.
- **Instance type hợp lệ** = hỗ trợ encryption in transit (lọc bằng `aws ec2 describe-instance-types` với `network-info.encryption-in-transit-supported=true`) ∩ được Managed Instances hỗ trợ ∩ khớp yêu cầu workload.
- **Cấu hình**: tạo cluster **Fargate and Managed Instances** → **Use custom – advanced** → **Allowed Instance types** chỉ gồm type hỗ trợ.
- **Cân nhắc**: T3/T3a/T4g không hỗ trợ; chỉ chuyển Monitor → Enforced khi mọi instance đang chạy đều hỗ trợ; ở Enforced, type không hỗ trợ làm task launch thất bại.
- **Sự cố**: kiểm tra lại type bằng lệnh CLI; tìm tài nguyên chặn bằng `GetVpcResourcesBlockingEncryptionEnforcement`.

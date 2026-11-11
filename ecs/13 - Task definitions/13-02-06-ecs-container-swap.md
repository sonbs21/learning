# Managing container swap memory space on Amazon ECS

> Nguồn: **Amazon ECS Developer Guide — Managing container swap memory space on Amazon ECS**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/container-swap.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: diễn đạt lại bằng tiếng Việt, bám sát từng ý và đúng thứ tự của trang gốc, không thêm thông tin ngoài trang. Đây không phải bản dịch từng chữ; khi cần câu chữ chính xác, đọc link gốc ở trên.
> - Khối **💡 Giải thích thêm**: phần giải thích để dễ hiểu hơn, chỉ làm rõ những gì docs đã nói.
> - Tên tham số giữ nguyên như docs.

---

## Mục lục

1. [Tổng quan: swap theo từng container](#1-tổng-quan-swap-theo-từng-container)
2. [Tham số `maxSwap`](#2-tham-số-maxswap)
3. [Tham số `swappiness`](#3-tham-số-swappiness)
4. [Vị trí trong task definition](#4-vị-trí-trong-task-definition)
5. [Những điều cần cân nhắc (Considerations)](#5-những-điều-cần-cân-nhắc-considerations)
6. [Tóm tắt](#6-tóm-tắt)

---

## 1. Tổng quan: swap theo từng container

**📘 Theo docs AWS**

- ECS cho phép kiểm soát việc dùng **swap memory** trên EC2 instance **Linux** ở **mức từng container**.
- Với cấu hình swap theo container, mỗi container trong task definition có thể **bật hoặc tắt** swap. Container nào bật thì có thể **giới hạn** lượng swap tối đa được dùng.
- Ví dụ docs:
  - container **nhạy với độ trễ (latency-critical)** → **tắt** swap;
  - container có **nhu cầu memory tăng đột biến tạm thời** → **bật** swap để giảm khả năng lỗi **out-of-memory** khi container chịu tải.

**💡 Giải thích thêm**

Swap là vùng trên đĩa mà hệ điều hành dùng để chứa tạm các trang memory khi RAM không đủ. Đọc/ghi swap chậm hơn RAM rất nhiều, nên:
- bật swap → ít bị OOM hơn, nhưng có thể **chậm đi** khi đang swap;
- tắt swap → hiệu năng ổn định, nhưng vượt memory là bị dừng.

Liên hệ file *Best practices for task sizes*: vượt **memory limit** thì container bị dừng. Swap là cách cho container một "vùng đệm" trước khi chạm tới giới hạn đó.

---

## 2. Tham số `maxSwap`

**📘 Theo docs AWS**

- Tổng lượng swap (đơn vị **MiB**) mà container được dùng.
- Được chuyển thành tuỳ chọn **`--memory-swap`** của `docker run`, với giá trị = **memory của container + `maxSwap`**.
- `maxSwap` = **`0`** → container **không dùng swap**.
- Giá trị hợp lệ: `0` hoặc **số nguyên dương**.
- **Bỏ trống** `maxSwap` → container dùng cấu hình swap của **container instance** mà nó đang chạy.
- **Phải đặt `maxSwap`** thì mới dùng được tham số `swappiness`.

**💡 Giải thích thêm**

Ví dụ minh hoạ cách quy đổi sang Docker: container có memory 512 MiB và `maxSwap` = 256:

```
--memory-swap = 512 + 256 = 768 MiB
→ container dùng tối đa 512 MiB RAM + 256 MiB swap
```

| `maxSwap` | Kết quả |
|---|---|
| (bỏ trống) | Theo cấu hình swap của instance (xem mặc định ở mục 5) |
| `0` | Không dùng swap |
| `N` > 0 | Dùng tối đa N MiB swap |

Docs không nói rõ "memory của container" lấy từ trường nào; thông thường đó là memory hard limit (`memory`) của container — đây là phần mình suy luận, nên kiểm tra lại nếu bạn chỉ khai báo `memoryReservation`.

---

## 3. Tham số `swappiness`

**📘 Theo docs AWS**

- Dùng để tinh chỉnh **mức độ swap** của container.
- `swappiness` = **`0`** → không swap trừ khi **bắt buộc**.
- `swappiness` = **`100`** → swap **mạnh tay** (aggressively).
- Giá trị hợp lệ: số nguyên từ **`0` đến `100`**.
- Không khai báo → mặc định **`60`**.
- Không khai báo `maxSwap` → tham số này **bị bỏ qua**.
- Ánh xạ tới tuỳ chọn **`--memory-swappiness`** của `docker run`.

**💡 Giải thích thêm**

```
swappiness:  0 ────────────── 60 (mặc định) ────────────── 100
             chỉ swap khi bắt buộc                         swap mạnh tay
```

Quan hệ phụ thuộc:

```
maxSwap không khai báo  →  swappiness bị bỏ qua
maxSwap có khai báo     →  swappiness có hiệu lực (mặc định 60 nếu không khai báo)
```

Lưu ý: xem mục 5 — trên **Amazon Linux 2023**, `swappiness` không được hỗ trợ.

---

## 4. Vị trí trong task definition

**📘 Theo docs AWS**

Hai tham số nằm trong `linuxParameters` của từng container definition (docs đưa cú pháp JSON với hai trường `maxSwap` và `swappiness` kiểu số nguyên).

**💡 Giải thích thêm**

Ví dụ minh hoạ với giá trị cụ thể (giá trị do mình đặt, không có trong docs):

```json
"containerDefinitions": [
  {
    "name": "worker",
    "memory": 1024,
    "linuxParameters": {
      "maxSwap": 512,
      "swappiness": 10
    }
  },
  {
    "name": "api",
    "memory": 512,
    "linuxParameters": {
      "maxSwap": 0
    }
  }
]
```

- `worker`: nhu cầu memory tăng đột biến → cho phép 512 MiB swap, swappiness thấp để chỉ swap khi thật cần.
- `api`: nhạy độ trễ → tắt swap.

Đúng tinh thần ví dụ ở mục 1: cấu hình swap **khác nhau cho từng container** trong cùng task definition.

---

## 5. Những điều cần cân nhắc (Considerations)

**📘 Theo docs AWS**

1. **Instance phải có swap** — swap phải được **bật và cấp phát** trên EC2 instance chạy task thì container mới dùng được. Mặc định **ECS-optimized AMI không bật swap**; bạn phải tự bật trên instance. (Xem *Instance Store Swap Volumes* — <https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/instance-store-swap-volumes.html> hoặc *How do I allocate memory to work as swap space in an Amazon EC2 instance?* — <https://repost.aws/knowledge-center/ec2-memory-swap-file>.)
2. **Chỉ cho EC2** — các tham số swap chỉ hỗ trợ cho task definition chỉ định **EC2**; **không hỗ trợ** cho task definition chỉ dùng cho **Fargate**.
3. **Chỉ Linux** — chỉ hỗ trợ container Linux; container **Windows** hiện chưa hỗ trợ.
4. **Mặc định khi bỏ trống cả hai** — nếu task definition không khai báo `maxSwap` và `swappiness`, mỗi container có `swappiness` mặc định **60**, và tổng swap bị giới hạn ở **gấp đôi memory** của container.
5. **Amazon Linux 2023** — task chạy trên **Amazon Linux 2023** không hỗ trợ tham số `swappiness`.

**💡 Giải thích thêm**

Checklist trước khi dùng swap cho container:

```
[ ] Task chạy trên EC2 (không phải Fargate-only)
[ ] Container Linux (không phải Windows)
[ ] Instance đã bật và cấp phát swap
      (ECS-optimized AMI mặc định KHÔNG có → thường làm trong user data của launch template)
[ ] Nếu AMI là Amazon Linux 2023 → không dùng được swappiness, chỉ còn maxSwap
```

Điểm dễ nhầm: khai báo `maxSwap` trong task definition **không tự tạo swap** trên instance. Nếu instance không có swap, container cũng không có gì để dùng.

Về điểm 4: nếu bỏ trống cả hai tham số **và** instance có swap, container có thể dùng swap tới gấp đôi memory của nó. Muốn chắc chắn container không swap thì phải đặt `maxSwap` = 0 rõ ràng.

Liên hệ các file trước: Managed Instances không nằm trong phạm vi trang này (docs chỉ nói EC2 và Fargate), nên mình không suy ra gì cho Managed Instances.

---

## 6. Tóm tắt

- ECS cho phép cấu hình swap **theo từng container** trên EC2 Linux: tắt cho container nhạy độ trễ, bật cho container có nhu cầu memory đột biến.
- **`maxSwap`** (MiB): `0` = không swap; số dương = giới hạn swap; bỏ trống = theo cấu hình instance. Ánh xạ `--memory-swap` = memory + `maxSwap`. Phải có `maxSwap` thì `swappiness` mới có hiệu lực.
- **`swappiness`** (0–100, mặc định 60): 0 = chỉ swap khi bắt buộc, 100 = swap mạnh tay. Ánh xạ `--memory-swappiness`.
- Hai tham số nằm trong `linuxParameters` của container definition.
- Cân nhắc: instance phải bật swap (ECS-optimized AMI mặc định không có); chỉ EC2, không Fargate; chỉ Linux; bỏ trống cả hai → swappiness 60 và swap tối đa gấp đôi memory; Amazon Linux 2023 không hỗ trợ `swappiness`.

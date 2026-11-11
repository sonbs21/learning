# Updating Amazon ECS Managed Instances monitoring

> Nguồn: **Amazon ECS Developer Guide — Updating Amazon ECS Managed Instances monitoring**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/update-capacity-provider-managed-instances.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: diễn đạt lại bằng tiếng Việt, bám sát từng bước và đúng thứ tự của trang gốc, không thêm thông tin ngoài trang. Đây không phải bản dịch từng chữ; khi cần câu chữ hoặc ví dụ lệnh chính xác, đọc link gốc ở trên.
> - Khối **💡 Giải thích thêm**: phần giải thích để dễ hiểu hơn, chỉ làm rõ những gì docs đã nói.
> - Lệnh CLI dùng placeholder (`<...>`); tên nút trên console giữ tiếng Anh (in đậm).

---

## Mục lục

0. [Tổng quan](#0-tổng-quan)
1. [Đổi mức giám sát bằng console](#1-đổi-mức-giám-sát-bằng-console)
2. [Cập nhật tag bằng console](#2-cập-nhật-tag-bằng-console)
3. [Đổi mức giám sát bằng AWS CLI](#3-đổi-mức-giám-sát-bằng-aws-cli)
4. [Ghi chú và chỗ không khớp](#4-ghi-chú-và-chỗ-không-khớp)
5. [Tóm tắt](#5-tóm-tắt)

---

## 0. Tổng quan

**📘 Theo docs AWS**

- Có thể đổi mức giám sát của capacity provider Managed Instances giữa **basic** và **detailed**, để điều chỉnh lượng dữ liệu giám sát thu được **mà không cần tạo lại** capacity provider.
- Chi tiết các mức giám sát: *Monitoring Amazon ECS Managed Instances* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/monitoring-managed-instances.html>.

**💡 Giải thích thêm**

| Mức | Nội dung (theo file *Managed Instances capacity providers*) |
|---|---|
| **Basic** (mặc định) | Phần lớn metric chu kỳ 5 phút, status check 1 phút; miễn phí |
| **Detailed** | Mọi metric chu kỳ 1 phút |

Đây là một trong số ít thay đổi trên capacity provider mà docs chủ động hướng dẫn **sửa tại chỗ** — khác tinh thần "tạo mới thay vì sửa" ở file *Best practices for updating capacity providers*. Hợp lý vì đổi mức giám sát ít rủi ro hơn đổi instance type hay mạng (nhận xét của mình).

---

## 1. Đổi mức giám sát bằng console

**📘 Theo docs AWS**

1. Mở console tại <https://console.aws.amazon.com/ecs/v2>.
2. Chọn **Region**.
3. Thanh bên → **Clusters**.
4. Chọn tên cluster.
5. Tab **Infrastructure**.
6. Ở **Advanced configuration**, chọn:
   - **Basic** — CloudWatch gửi metric **status check**;
   - **Detailed** — CloudWatch gửi **mọi** metric.
7. **Update**.

---

## 2. Cập nhật tag bằng console

**📘 Theo docs AWS**

Cập nhật tag của capacity provider Managed Instances có sẵn:
1. Thanh bên → **Clusters**.
2. Trang clusters → **Infrastructure**.
3. Chọn capacity provider đã tạo.
4. Trên trang capacity provider → **Tags**.
5. Ở **Tags** → **Manage tags**.
6. Thêm tag: nhập key và value → **Save**. Thêm nhiều tag: chọn **Add tag** cho từng tag; tối đa **50** tag. Xoá tag: **Remove**.

> **Note (docs):** Nếu **tag propagation** đang bật, tag **thêm hoặc xoá sau khi** tạo capacity provider **không áp dụng** cho tài nguyên capacity provider **đã tạo trước đó**.

**💡 Giải thích thêm**

```
Tạo CP với tag Team=backend, propagateTags=CAPACITY_PROVIDER
  → instance i-1, i-2 được gắn Team=backend

Sau đó thêm tag CostCenter=42 vào CP
  → i-1, i-2: KHÔNG có CostCenter=42
  → instance mới (i-3…): có CostCenter=42
```

Hệ quả: tag mới chỉ "phủ" dần khi instance cũ được thay (vòng đời 14–21 ngày — file *Patching*; hoặc consolidation — file *Infrastructure optimization*). Nếu cần tag ngay cho báo cáo chi phí, nên lên kế hoạch tag từ lúc **tạo** capacity provider (file *Managed Instances capacity providers*) — gợi ý của mình.

---

## 3. Đổi mức giám sát bằng AWS CLI

**📘 Theo docs AWS**

- Dùng **phiên bản AWS CLI mới nhất** (<https://docs.aws.amazon.com/cli/latest/userguide/getting-started-install.html>).
- Dùng `update-capacity-provider`, truyền `--managed-instances-provider` với khối **`instanceLaunchTemplateUpdate`** chứa trường **`monitoring`**:

**Bật detailed monitoring:**

```bash
aws ecs update-capacity-provider \
  --name <CAPACITY_PROVIDER_NAME> \
  --managed-instances-provider '{ "instanceLaunchTemplateUpdate": { "monitoring": "DETAILED" } }'
```

**Về basic monitoring:**

```bash
aws ecs update-capacity-provider \
  --name <CAPACITY_PROVIDER_NAME> \
  --managed-instances-provider '{ "instanceLaunchTemplateUpdate": { "monitoring": "BASIC" } }'
```

**💡 Giải thích thêm**

Trường **`instanceLaunchTemplateUpdate`** (khác với `instanceLaunchTemplate` khi tạo) cho thấy API cập nhật có kiểu **cập nhật một phần** cho launch template: chỉ gửi trường cần đổi. Đây là suy luận từ việc ví dụ chỉ gửi đúng `monitoring` — trang không nói rõ các trường khác có được giữ nguyên hay không.

Kiểm tra sau khi đổi:

```bash
aws ecs describe-capacity-providers --capacity-providers <CAPACITY_PROVIDER_NAME>
```

(Lệnh kiểm tra lấy từ file *Creating a capacity provider*.)

---

## 4. Ghi chú và chỗ không khớp

**💡 Giải thích thêm**

| Chủ đề | Trang này | Trang khác |
|---|---|---|
| Giá trị `monitoring` | **Chữ hoa**: `DETAILED`, `BASIC` | *Creating a capacity provider*: **chữ thường** `basic` |
| Khối JSON khi update | `instanceLaunchTemplateUpdate` (chỉ trường cần đổi) | *GPU auto repair*: `update-capacity-provider` gửi lại **đầy đủ** `infrastructureRoleArn` + `instanceLaunchTemplate` + `autoRepairConfiguration` |
| Lệnh có `--cluster`? | Không | *Creating a capacity provider*: `cluster` có trong request tạo |

Gợi ý: dùng đúng dạng chữ như ví dụ của từng thao tác; nếu API báo lỗi giá trị, thử dạng còn lại và đối chiếu API reference (`UpdateCapacityProvider`, `CreateCapacityProvider`).

**Đổi mức giám sát có ảnh hưởng instance đang chạy không?** Trang không nói. Theo file *Updating a cluster* ("đổi capacity provider thì instance cũ vẫn chạy, instance mới dùng cấu hình mới") và vì `monitoring` nằm trong launch template, có khả năng chỉ instance **mới** áp dụng mức giám sát mới — cần kiểm tra trên môi trường thử (suy luận).

---

## 5. Tóm tắt

- Đổi giám sát capacity provider Managed Instances giữa **basic** và **detailed** mà **không cần tạo lại** capacity provider.
- **Console**: Clusters → cluster → **Infrastructure** → **Advanced configuration** → **Basic** (status check) / **Detailed** (mọi metric) → **Update**.
- **Tag**: Clusters → **Infrastructure** → capacity provider → **Tags** → **Manage tags**; tối đa **50** tag. Tag thêm/xoá sau khi tạo **không áp** cho tài nguyên đã tạo trước (dù bật propagation).
- **CLI**: `update-capacity-provider --managed-instances-provider '{"instanceLaunchTemplateUpdate":{"monitoring":"DETAILED"}}'` (hoặc `"BASIC"`).

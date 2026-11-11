# Deleting an Amazon ECS Managed Instances capacity provider

> Nguồn: **Amazon ECS Developer Guide — Deleting an Amazon ECS Managed Instances capacity provider**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/delete-capacity-provider-managed-instances-console-v2.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: diễn đạt lại bằng tiếng Việt, bám sát từng ý và đúng thứ tự của trang gốc, không thêm thông tin ngoài trang. Đây không phải bản dịch từng chữ; khi cần câu chữ chính xác, đọc link gốc ở trên.
> - Khối **💡 Giải thích thêm**: phần giải thích để dễ hiểu hơn, chỉ làm rõ những gì docs đã nói.
> - Tên nút trên console giữ tiếng Anh (in đậm); lệnh CLI dùng placeholder (`<...>`).

---

## Mục lục

1. [Trước khi xoá](#1-trước-khi-xoá)
2. [Xoá bằng console](#2-xoá-bằng-console)
3. [Trạng thái `INACTIVE`](#3-trạng-thái-inactive)
4. [Quy trình xoá an toàn (CLI)](#4-quy-trình-xoá-an-toàn-cli)
5. [Ghi chú](#5-ghi-chú)
6. [Tóm tắt](#6-tóm-tắt)

---

## 1. Trước khi xoá

**📘 Theo docs AWS**

- Khi không dùng nữa, bạn có thể xoá capacity provider Managed Instances.
- **Điều kiện**: trước khi xoá, capacity provider phải được **gỡ khỏi capacity provider strategy của mọi service**.
- Gỡ bằng API **`UpdateService`** hoặc luồng **cập nhật service** trên console.
- Dùng tuỳ chọn **Force new deployment** để mọi task đang dùng capacity của capacity provider này được **chuyển sang** capacity từ các capacity provider **còn lại**.

**💡 Giải thích thêm**

Vì sao cần **Force new deployment**: chỉ đổi strategy của service thì task **đang chạy** vẫn nằm trên instance cũ; phải deploy lại để task mới được đặt lên capacity provider khác (cùng logic "đổi cấu hình không tự kích hoạt deployment" ở file *Best practices for updating capacity providers*).

Ngoài service, cũng nên kiểm tra (gợi ý của mình — trang chỉ nhắc service):
- **Strategy mặc định** của cluster có trỏ tới capacity provider này không.
- **Standalone task** đang chạy, **EventBridge Scheduler**, pipeline gọi `RunTask` có dùng capacity provider này không.
- **Managed Daemons** đang gắn với capacity provider này (file *Creating and managing daemons*: chờ daemon task dừng rồi mới xoá capacity provider).

---

## 2. Xoá bằng console

**📘 Theo docs AWS**

1. Mở console tại <https://console.aws.amazon.com/ecs/v2>.
2. Thanh bên → **Clusters**.
3. Chọn cluster.
4. Trên trang **Cluster : `<tên>`** → **Infrastructure** → chọn capacity provider Managed Instances → **Delete**.
5. Trong hộp xác nhận, nhập **`delete <tên capacity provider>`**.
6. **Delete**.

---

## 3. Trạng thái `INACTIVE`

**📘 Theo docs AWS**

- Sau khi xoá, capacity provider chuyển sang trạng thái **`INACTIVE`**.
- Capacity provider `INACTIVE` có thể vẫn **thấy được** trong account một thời gian, nhưng hành vi này **có thể thay đổi** — **đừng dựa vào** việc capacity provider `INACTIVE` còn tồn tại.

**💡 Giải thích thêm**

Giống cluster `INACTIVE` ở file *Amazon ECS clusters*. Hệ quả cho IaC: không nên viết script hoặc Terraform giả định tìm lại được capacity provider đã xoá. Nếu cần tái sử dụng **tên** cũ, nên kiểm tra API có cho tạo lại cùng tên ngay hay không — trang không nói (gợi ý của mình).

---

## 4. Quy trình xoá an toàn (CLI)

**💡 Giải thích thêm**

Trang chỉ hướng dẫn console; quy trình CLI dưới đây là mình ghép từ trang này và các file trước:

```bash
# 1. Chuyển từng service sang capacity provider khác + force new deployment
aws ecs update-service \
  --cluster <CLUSTER> --service <SERVICE> \
  --capacity-provider-strategy capacityProvider=<CP_KHÁC>,weight=1 \
  --force-new-deployment

# 2. Chờ deployment xong; kiểm tra không còn task trên CP cũ
aws ecs describe-services --cluster <CLUSTER> --services <SERVICE>

# 3. (Nếu có) cập nhật strategy mặc định của cluster, liệt kê lại các CP còn dùng
aws ecs put-cluster-capacity-providers \
  --cluster <CLUSTER> \
  --capacity-providers <CP_KHÁC> \
  --default-capacity-provider-strategy capacityProvider=<CP_KHÁC>,weight=1

# 4. Xoá capacity provider
aws ecs delete-capacity-provider --capacity-provider <CP_CŨ>

# 5. Kiểm tra trạng thái (INACTIVE)
aws ecs describe-capacity-providers --capacity-providers <CP_CŨ>
```

Đây chính là bước 3 của quy trình "bất biến" trong file *Best practices for updating capacity providers* (tạo mới → chuyển service → xoá cũ). Với CloudFormation/Terraform, xoá CP cũ ở lần deploy **sau** để vẫn rollback được.

---

## 5. Ghi chú

**💡 Giải thích thêm**

- Câu mở đầu trang gốc có cụm "sau khi **group** bị xoá" — có vẻ là chữ sót lại từ trang xoá capacity provider **Auto Scaling group**; với Managed Instances hiểu là "sau khi capacity provider bị xoá".
- Trang không nói điều gì xảy ra với **managed instance** còn chạy khi xoá capacity provider. Theo các file trước (instance rảnh bị dọn — *Infrastructure optimization*), sau khi đã chuyển hết task thì instance sẽ rảnh và được dọn; nên xác nhận không còn task trước khi xoá (suy luận).

---

## 6. Tóm tắt

- Xoá capacity provider Managed Instances khi không dùng nữa.
- **Trước khi xoá**: gỡ capacity provider khỏi strategy của **mọi service** (`UpdateService` hoặc console), dùng **Force new deployment** để chuyển task sang capacity provider còn lại.
- **Console**: Clusters → cluster → **Infrastructure** → chọn capacity provider → **Delete** → nhập `delete <tên>` → **Delete**.
- Sau khi xoá: trạng thái **`INACTIVE`**; có thể còn thấy một thời gian nhưng **không được dựa vào**.

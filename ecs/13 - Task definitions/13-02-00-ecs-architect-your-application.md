# Architect your application for Amazon ECS

> Nguồn: **Amazon ECS Developer Guide — Architect your application for Amazon ECS**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/application_architecture.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: diễn đạt lại bằng tiếng Việt, bám sát từng ý và đúng thứ tự của trang gốc, không thêm thông tin ngoài trang. Đây không phải bản dịch từng chữ; khi cần câu chữ chính xác, đọc link gốc ở trên.
> - Khối **💡 Giải thích thêm**: phần giải thích để dễ hiểu hơn, chỉ làm rõ những gì docs đã nói.

---

## Mục lục

1. [Kiến trúc ứng dụng bắt đầu từ task definition](#1-kiến-trúc-ứng-dụng-bắt-đầu-từ-task-definition)
2. [Các thông tin chính trong task definition](#2-các-thông-tin-chính-trong-task-definition)
3. [Nguyên tắc 1 — Mỗi task definition family chỉ cho một mục đích nghiệp vụ](#3-nguyên-tắc-1--mỗi-task-definition-family-chỉ-cho-một-mục-đích-nghiệp-vụ)
4. [Nguyên tắc 2 — Mỗi phiên bản ứng dụng ứng với một revision](#4-nguyên-tắc-2--mỗi-phiên-bản-ứng-dụng-ứng-với-một-revision)
5. [Nguyên tắc 3 — Mỗi task definition family dùng IAM role riêng](#5-nguyên-tắc-3--mỗi-task-definition-family-dùng-iam-role-riêng)
6. [Tóm tắt](#6-tóm-tắt)

---

## 1. Kiến trúc ứng dụng bắt đầu từ task definition

**📘 Theo docs AWS**

Bạn thiết kế kiến trúc ứng dụng trên ECS bằng cách **tạo task definition** cho ứng dụng. Task definition chứa các tham số mô tả thông tin về ứng dụng (liệt kê ở mục 2).

**💡 Giải thích thêm**

Với ECS, "kiến trúc ứng dụng" được thể hiện cụ thể qua task definition: chạy trên hạ tầng nào, image nào, mạng ra sao, log đi đâu, volume nào. Các trang khác (service, capacity, gateway…) đều dựa trên task definition này.

---

## 2. Các thông tin chính trong task definition

**📘 Theo docs AWS**

**a) Capacity sử dụng** — quyết định hạ tầng mà task chạy trên đó.
- Dùng **EC2 capacity provider**: bạn chọn thêm **instance type**.
- Dùng **Amazon ECS Managed Instances capacity provider**: bạn có thể đưa ra **yêu cầu về instance** để ECS tự quản lý compute capacity.
- Một số loại instance, ví dụ **GPU**, cần đặt các tham số riêng. (Xem *Amazon ECS task definition use cases* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/use-cases.html>.)

**b) Container image** — chứa code ứng dụng và mọi dependency mà code cần để chạy.

**c) Network mode** cho các container trong task — quyết định task giao tiếp qua mạng như thế nào.
- Task chạy trên **EC2 instance** và **ECS Managed Instances**: có nhiều lựa chọn, nhưng AWS **khuyến nghị `awsvpc`**. `awsvpc` giúp đơn giản hoá networking cho container, cho bạn kiểm soát tốt hơn cách ứng dụng giao tiếp với nhau và với các dịch vụ khác trong VPC.
- Task chạy trên **Fargate**: **bắt buộc** dùng `awsvpc`.

**d) Cấu hình logging** cho task.

**e) Data volume** dùng với các container trong task.

Danh sách đầy đủ tham số: *Amazon ECS task definition parameters for Fargate* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task_definition_parameters.html>.

**💡 Giải thích thêm**

| Thành phần | Trả lời câu hỏi | Ghi chú theo docs |
|---|---|---|
| Capacity | Task chạy trên hạ tầng nào? | EC2 → chọn instance type; Managed Instances → đưa yêu cầu instance; GPU cần tham số riêng |
| Container image | Chạy code gì? | Code + toàn bộ dependency |
| Network mode | Task nói chuyện qua mạng thế nào? | EC2/Managed Instances: khuyến nghị `awsvpc`; Fargate: bắt buộc `awsvpc` |
| Logging | Log đi đâu? | — |
| Data volume | Container lưu/chia sẻ dữ liệu ở đâu? | — |

Liên hệ với các file trước: ECS Gateway yêu cầu service dùng `awsvpc`. Nếu theo khuyến nghị ở đây ngay từ đầu, bạn sẽ không phải đổi network mode khi muốn dùng gateway.

---

## 3. Nguyên tắc 1 — Mỗi task definition family chỉ cho một mục đích nghiệp vụ

**📘 Theo docs AWS**

- Mỗi **task definition family** chỉ nên phục vụ **một mục đích nghiệp vụ**.
- Nếu gom nhiều loại container ứng dụng vào cùng một task definition, bạn **không scale riêng** được từng loại.
- Ví dụ docs: một **website** và một **API** thường cần cách scale khác nhau. Khi traffic tăng, có thể bạn cần số container web khác với số container API. Nếu hai container này nằm chung một task definition, **mỗi task** đều chạy cùng số container web và API như nhau.

**💡 Giải thích thêm**

```
Gộp chung (không nên):                Tách riêng (nên):
task = [web + api]                     family "web" → service web  (scale 6)
scale 3 task → 3 web, 3 api            family "api" → service api  (scale 2)
muốn 6 web thì cũng phải 6 api         mỗi bên scale độc lập
```

Đây là cùng tinh thần với trang *Architect for EC2 capacity* (tách frontend, backend, data store thành các task definition riêng).

---

## 4. Nguyên tắc 2 — Mỗi phiên bản ứng dụng ứng với một revision

**📘 Theo docs AWS**

- Trong một task definition family, **mỗi revision** là một **ảnh chụp tại một thời điểm** của các thiết lập cho một container image cụ thể — tương tự cách container là ảnh chụp mọi thành phần cần để chạy một phiên bản code nhất định.
- Hãy tạo ánh xạ **một–một** giữa: **phiên bản code** ↔ **tag container image** ↔ **task definition revision**.
- Quy trình release điển hình theo docs:
  1. một **git commit**;
  2. được build thành **container image**, gắn tag là **git commit SHA**;
  3. tag image đó có **task definition revision** riêng;
  4. cuối cùng **cập nhật ECS service** để deploy revision mới.

**💡 Giải thích thêm**

```
git commit a1b2c3d
   └─► image  my-app:a1b2c3d
          └─► task definition  my-app:42   (revision mới, trỏ tới image :a1b2c3d)
                 └─► update-service → deploy revision 42
```

Lợi ích của ánh xạ một–một:
- Nhìn revision là biết đang chạy commit nào.
- Rollback = deploy lại revision cũ, vì revision cũ vẫn trỏ đúng image cũ.

Liên hệ với file *Container image pull behavior*: dùng tag theo git SHA chính là "mỗi release một tag mới", điều kiện để `ECS_IMAGE_PULL_BEHAVIOR` = `once`/`prefer-cached` an toàn. Liên hệ với file *Task definition states*: các revision cũ có thể deregister rồi xoá khi không cần, còn số revision vẫn tiếp tục tăng.

---

## 5. Nguyên tắc 3 — Mỗi task definition family dùng IAM role riêng

**📘 Theo docs AWS**

- Định nghĩa **mỗi task definition với IAM role riêng**.
- Áp dụng cùng với nguyên tắc 1 (mỗi thành phần nghiệp vụ một family). Làm cả hai giúp **giới hạn quyền truy cập** của từng service tới tài nguyên trong AWS account.
- Ví dụ docs: cho **authentication service** quyền kết nối tới **database mật khẩu**, đồng thời bảo đảm **chỉ order service** mới truy cập được **thông tin thanh toán thẻ tín dụng**.

**💡 Giải thích thêm**

```
family "auth"   ── IAM role auth   ──► được vào DB mật khẩu
family "order"  ── IAM role order  ──► được vào dữ liệu thanh toán thẻ
family "web"    ── IAM role web    ──► không được vào cả hai
```

Ba nguyên tắc bổ trợ nhau: tách family theo nghiệp vụ (nguyên tắc 1) thì mới gắn được role riêng theo nghiệp vụ (nguyên tắc 3). Nếu gộp nhiều thành phần vào một task, chúng buộc phải dùng chung quyền.

Docs nói chung "IAM role" cho task definition. Trong task definition có trường `taskRoleArn` (quyền cho code ứng dụng) và `executionRoleArn` (quyền cho ECS agent kéo image, ghi log…). Ví dụ của docs về quyền truy cập database và dữ liệu thanh toán tương ứng với **task role** — đây là phần mình giải thích thêm, trang này không nêu tên hai trường đó.

---

## 6. Tóm tắt

- Kiến trúc ứng dụng trên ECS được thể hiện qua **task definition**.
- Task definition gồm: **capacity** (EC2 → instance type, Managed Instances → yêu cầu instance, GPU cần tham số riêng), **container image**, **network mode** (khuyến nghị `awsvpc`; Fargate bắt buộc `awsvpc`), **logging**, **data volume**.
- **Nguyên tắc 1**: mỗi family một mục đích nghiệp vụ → scale độc lập (ví dụ web và API).
- **Nguyên tắc 2**: một–một giữa phiên bản code ↔ tag image (git SHA) ↔ revision → update service để deploy.
- **Nguyên tắc 3**: mỗi family một IAM role → giới hạn quyền từng service (ví dụ auth và order).

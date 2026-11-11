# Amazon ECS: Architect for EC2 capacity & Container image pull behavior

> Nguồn: **Amazon ECS Developer Guide**
> 1. *Architect for EC2 capacity for Amazon ECS* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/launch-type-ec2.html>
> 2. *Container image pull behavior for EC2 and external instances on Amazon ECS* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/pull-behavior.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: diễn đạt lại bằng tiếng Việt, bám sát từng ý và đúng thứ tự của trang gốc, không thêm thông tin ngoài trang. Đây không phải bản dịch từng chữ; khi cần câu chữ chính xác, đọc link gốc ở trên.
> - Khối **💡 Giải thích thêm**: phần mình giải thích để dễ hiểu hơn, chỉ làm rõ những gì docs đã nói.

---

## Phần 1 — Architect for EC2 capacity for Amazon ECS

### 1.1. Mục đích của EC2 capacity

**📘 Theo docs AWS**

EC2 capacity được khuyến nghị cho những workload có quy mô lớn và cần tối ưu về chi phí.

**💡 Giải thích thêm**

"Capacity" là hạ tầng mà task của bạn chạy trên đó. Với EC2, bạn tự cung cấp các EC2 instance (gọi là *container instance*) cho cluster, còn ECS đặt task lên các instance đó. Docs chỉ nêu một tiêu chí chọn: **workload lớn + cần tối ưu giá**.

---

### 1.2. Nguyên tắc khi mô hình hoá task definition và service

**📘 Theo docs AWS**

Khi lên kế hoạch tổ chức task definition và service trên EC2, AWS khuyên bạn cân nhắc hai điều:
- những process nào cần phải chạy cùng nhau;
- bạn sẽ scale từng thành phần theo cách nào.

**💡 Giải thích thêm**

- **Task definition** là bản mô tả một nhóm container. Một **task** là một lần chạy của task definition đó.
- **Service** là thứ giữ cho số lượng task mong muốn luôn chạy.
- Vì service scale theo **số task**, nên câu hỏi "chạy cùng nhau" và câu hỏi "scale thế nào" gắn liền với nhau: những container nằm chung một task definition sẽ luôn được scale cùng nhau.

---

### 1.3. Ví dụ ứng dụng ba thành phần

**📘 Theo docs AWS**

Docs đưa ra ví dụ một ứng dụng gồm:
- một **frontend service** hiển thị thông tin trên trang web;
- một **backend service** cung cấp API cho frontend;
- một **data store** (kho dữ liệu).

Với ví dụ này, AWS hướng dẫn:
- tạo các task definition gom những container phục vụ **cùng một mục đích** lại với nhau;
- tách **các thành phần khác nhau** ra thành **nhiều task definition** riêng.

Cluster minh hoạ có **3 container instance**, chạy tổng cộng **3 container frontend**, **2 container backend** và **1 container data store**.

Theo hình minh hoạ trong docs:
- **Instance 1**: một container frontend và một container database (data store).
- **Instance 2**: một container frontend và một container backend.
- **Instance 3**: một container frontend và một container backend.

```
┌──────────── Instance 1 ────────────┐ ┌──────────── Instance 2 ────────────┐ ┌──────────── Instance 3 ────────────┐
│ Front-end service container        │ │ Front-end service container        │ │ Front-end service container        │
│ Database service container         │ │ Backend service container          │ │ Backend service container          │
└────────────────────────────────────┘ └────────────────────────────────────┘ └────────────────────────────────────┘
```

**💡 Giải thích thêm**

- Vì frontend, backend và data store nằm ở **ba task definition khác nhau**, mỗi loại có số lượng riêng: 3 frontend, 2 backend, 1 data store. Đây chính là ý "scale từng thành phần" ở mục 1.2.
- Nếu gộp cả ba vào một task definition, số lượng của chúng sẽ luôn bằng nhau (ví dụ 3–3–3), không tạo ra được tỉ lệ 3–2–1 như trong ví dụ.
- Việc frontend và backend nằm chung một instance (Instance 2, 3) không có nghĩa là chúng cùng task. Chúng là các task khác nhau, chỉ được đặt lên cùng một máy.

---

### 1.4. Gom các container liên quan vào cùng một task definition

**📘 Theo docs AWS**

Bạn có thể đặt các container có liên quan vào chung một task definition, chẳng hạn những container liên kết với nhau và cần chạy cùng nhau. Ví dụ AWS đưa ra: thêm một container chuyên **stream log** vào frontend service và khai báo nó trong **cùng task definition** với frontend.

**💡 Giải thích thêm**

- Đây là trường hợp ngược lại với mục 1.3: container log streaming **chỉ tồn tại để phục vụ frontend**, nên nó phải chạy cùng và scale cùng frontend. Mỗi task frontend sẽ có một container log đi kèm.
- Tóm lại quy tắc của docs:
  - **Cùng mục đích, phải chạy cùng nhau** → chung task definition.
  - **Thành phần khác nhau** → tách task definition.

---

### 1.5. Từ task definition đến service

**📘 Theo docs AWS**

- Khi đã có task definition, bạn tạo **service** từ chúng để giữ cho các task mong muốn luôn sẵn sàng. (Xem: *Creating an Amazon ECS rolling update deployment*.)
- Trong service, bạn có thể gắn container với load balancer của **Elastic Load Balancing**. (Xem: *Use load balancing to distribute Amazon ECS service traffic*.)
- Khi yêu cầu của ứng dụng thay đổi, bạn có thể cập nhật service để **tăng hoặc giảm số task mong muốn**, hoặc để **triển khai phiên bản container mới hơn** trong task. (Xem: *Updating an Amazon ECS service*.)

**💡 Giải thích thêm**

Luồng mà docs mô tả:

```
Task definition ──► Service (duy trì số task) ──► gắn Load Balancer
                         │
                         └── Update service: đổi số task (scale) hoặc đổi phiên bản container (deploy)
```

Với ví dụ ở mục 1.3, ta sẽ có ba service tương ứng ba task definition, mỗi service được scale và deploy độc lập.

Link các trang liên quan:
- Creating an Amazon ECS rolling update deployment — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/create-service-console-v2.html>
- Use load balancing to distribute Amazon ECS service traffic — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/service-load-balancing.html>
- Updating an Amazon ECS service — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/update-service-console-v2.html>

---

## Phần 2 — Container image pull behavior for EC2 and external instances on Amazon ECS

### 2.1. Thời gian khởi động phụ thuộc vào image

**📘 Theo docs AWS**

Container khởi động nhanh hay chậm tuỳ vào container image bên dưới. Docs ví dụ: các image "nặng" — bản đầy đủ của Debian, Ubuntu, Amazon1/2 — có thể khởi động lâu hơn, vì bên trong có nhiều service chạy hơn so với các bản slim tương ứng (Debian-slim, Ubuntu-slim, Amazon-slim) hoặc các base image nhỏ hơn như Alpine.

**💡 Giải thích thêm**

Docs dùng điểm này làm bối cảnh: thời gian để một task sẵn sàng có phần phụ thuộc vào image. Phần tiếp theo nói về một yếu tố khác có thể tối ưu: thời gian **tải** image.

---

### 2.2. Hành vi pull mặc định của ECS agent

**📘 Theo docs AWS**

Khi ECS agent khởi chạy một task, nó pull Docker image từ registry ở xa, sau đó lưu một bản sao vào cache local. Nếu mỗi lần release ứng dụng bạn đều dùng một **image tag mới**, thì hành vi này là không cần thiết.

**💡 Giải thích thêm**

- **ECS agent** là tiến trình chạy trên mỗi container instance (EC2 hoặc external instance), nhận lệnh từ ECS và khởi chạy container.
- Vì sao docs nói "không cần thiết"? Nếu mỗi release có tag mới, thì một tag đã có trong cache sẽ không bao giờ thay đổi nội dung. Pull lại tag đó chỉ tốn thời gian, không mang lại image mới.

---

### 2.3. Tham số `ECS_IMAGE_PULL_BEHAVIOR`

**📘 Theo docs AWS**

Tham số agent `ECS_IMAGE_PULL_BEHAVIOR` quyết định cách pull image. Có các lựa chọn:

- **`default`** — image được pull từ xa. Nếu pull thất bại, dùng image đang có trong cache của instance.
- **`always`** — image được pull từ xa. Nếu pull thất bại, **task thất bại**.

Để **tăng tốc deployment**, AWS khuyên đặt tham số này thành một trong hai giá trị:

- **`once`** — chỉ pull từ xa khi image **chưa được pull bởi task nào trước đó trên cùng container instance**, hoặc khi bản cache **đã bị quá trình dọn image tự động (automated image cleanup) xoá**. Các trường hợp còn lại dùng bản cache trên instance. Nhờ vậy không có lần pull thừa nào.
- **`prefer-cached`** — pull từ xa nếu **không có image trong cache**; có thì dùng bản cache. Automated image cleanup được **tắt** cho container để bản cache không bị xoá.

Đặt `ECS_IMAGE_PULL_BEHAVIOR` thành một trong hai giá trị trên có thể tiết kiệm thời gian, vì agent dùng lại image đã tải sẵn. Với Docker image lớn, việc tải qua mạng có thể mất **10–20 giây**.

**💡 Giải thích thêm**

Bảng tóm tắt đúng theo nội dung docs:

| Giá trị | Khi nào pull từ xa | Pull lỗi thì sao | Image cleanup | Mục đích |
|---|---|---|---|---|
| `default` | Luôn pull | Dùng bản cache | (docs không đề cập thay đổi) | Mặc định |
| `always` | Luôn pull | Task thất bại | (docs không đề cập thay đổi) | Luôn lấy từ registry |
| `once` | Chỉ khi chưa từng pull trên instance này, hoặc cache đã bị cleanup xoá | (docs không nêu) | Vẫn chạy (cache có thể bị xoá) | Tăng tốc deployment |
| `prefer-cached` | Chỉ khi không có cache | (docs không nêu) | **Tắt** cho container | Tăng tốc deployment |

Một số điểm rút ra trực tiếp từ docs:

- **`default` và `always` giống nhau khi pull thành công**; chỉ khác nhau lúc pull lỗi: `default` lùi về bản cache, `always` cho task thất bại.
- **`once` và `prefer-cached` đều tránh pull khi đã có image**, khác nhau ở chỗ:
  - `once` để cơ chế cleanup chạy bình thường, nên image cũ có thể bị xoá rồi được pull lại khi cần.
  - `prefer-cached` tắt cleanup cho container để giữ bản cache.
- Hai giá trị tăng tốc này hợp lý chính vì điều kiện ở mục 2.2: **mỗi release dùng tag mới**. Ngược lại, nếu bạn ghi đè cùng một tag (ví dụ luôn push `latest`), thì khi đã có cache, `once`/`prefer-cached` sẽ không pull lại, nên instance có thể tiếp tục chạy nội dung cũ của tag đó. Đây là hệ quả logic của định nghĩa trong docs, nên cần lưu ý.
- Tiêu đề trang nói rõ phạm vi: **EC2 và external instances** (ECS Anywhere) — những nơi có ECS agent mà bạn cấu hình được.

---

## Tóm tắt

**Architect for EC2 capacity**
- Dùng EC2 cho workload lớn cần tối ưu giá.
- Cân nhắc process nào chạy cùng nhau và từng thành phần scale ra sao.
- Thành phần khác nhau → task definition riêng (ví dụ 3 frontend, 2 backend, 1 data store trên 3 instance).
- Container liên quan, phải chạy cùng (ví dụ log streaming cho frontend) → chung task definition.
- Tạo service từ task definition; gắn Elastic Load Balancing; update service để scale hoặc deploy phiên bản mới.

**Container image pull behavior**
- Thời gian khởi động phụ thuộc image (full nặng hơn slim / Alpine).
- Agent mặc định pull rồi cache; không cần thiết nếu mỗi release dùng tag mới.
- `ECS_IMAGE_PULL_BEHAVIOR`: `default`, `always`, và hai giá trị tăng tốc `once`, `prefer-cached`.
- Có thể tiết kiệm 10–20 giây tải image lớn.

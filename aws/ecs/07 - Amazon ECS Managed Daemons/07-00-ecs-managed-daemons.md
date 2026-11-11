# Amazon ECS Managed Daemons

> Nguồn: **Amazon ECS Developer Guide — Amazon ECS Managed Daemons**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/managed-daemons.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: diễn đạt lại bằng tiếng Việt, bám sát từng ý và đúng thứ tự của trang gốc, không thêm thông tin ngoài trang. Đây không phải bản dịch từng chữ; khi cần câu chữ chính xác, đọc link gốc ở trên.
> - Khối **💡 Giải thích thêm**: phần giải thích để dễ hiểu hơn, chỉ làm rõ những gì docs đã nói.

---

## Mục lục

1. [Managed Daemons là gì](#1-managed-daemons-là-gì)
2. [Mức độ quan trọng của daemon (criticality)](#2-mức-độ-quan-trọng-của-daemon-criticality)
3. [Cách Managed Daemons hoạt động](#3-cách-managed-daemons-hoạt-động)
   - [3.1 Đăng ký và gắn daemon](#31-đăng-ký-và-gắn-daemon)
   - [3.2 Thứ tự khởi động](#32-thứ-tự-khởi-động)
   - [3.3 Criticality quyết định hành vi khi lỗi](#33-criticality-quyết-định-hành-vi-khi-lỗi)
   - [3.4 Cập nhật daemon](#34-cập-nhật-daemon)
4. [Lợi ích chính](#4-lợi-ích-chính)
5. [Bắt đầu](#5-bắt-đầu)
6. [Liên hệ với các file trước](#6-liên-hệ-với-các-file-trước)
7. [Tóm tắt](#7-tóm-tắt)

---

## 1. Managed Daemons là gì

**📘 Theo docs AWS**

- **Managed Daemons** cho phép triển khai và quản lý các **software agent** — như agent **bảo mật**, **observability**, **networking** — trên hạ tầng container chạy **Amazon ECS Managed Instances**.
- Managed Daemons **tách vòng đời daemon** khỏi việc vận hành ứng dụng: bạn deploy, cập nhật, giám sát agent **độc lập**, không cần deploy lại workload hay phối hợp thay đổi giữa các service.

**💡 Giải thích thêm**

"Daemon" ở đây là tiến trình chạy **một bản trên mỗi máy** để phục vụ chung mọi task trên máy đó (vd agent thu log, agent giám sát, agent bảo mật). Trước đây hay làm bằng **sidecar** — mỗi task kèm một container agent — tốn tài nguyên và phải deploy lại task mỗi khi đổi agent.

```
Sidecar (mỗi task một agent):        Managed Daemon (mỗi instance một agent):
┌─ Instance ───────────────┐         ┌─ Instance ───────────────┐
│ [app A + agent]          │         │ [daemon agent]           │
│ [app B + agent]          │         │ [app A] [app B] [app C]  │
│ [app C + agent]          │         └──────────────────────────┘
└──────────────────────────┘
```

---

## 2. Mức độ quan trọng của daemon (criticality)

**📘 Theo docs AWS**

Mỗi managed daemon được cấu hình **critical** hoặc **non-critical**, tuỳ việc daemon lỗi có khiến ECS **drain** container instance hay không.

| | **Critical** (mặc định) | **Non-critical** (`critical: false`) |
|---|---|---|
| Trước khi đặt task ứng dụng | ECS khởi động daemon task **trước** khi đặt task ứng dụng lên instance | Daemon task hoạt động **độc lập** với sức khoẻ instance |
| Daemon task dừng hoặc unhealthy | ECS **drain và thay** container instance | ECS **giữ** instance hoạt động; task ứng dụng cũ vẫn chạy, task mới vẫn được đặt vào |
| Daemon không khởi động được | (xem mục 3.3) | **Không bao giờ chặn** việc đăng ký instance → task ứng dụng được đặt ngay dù daemon lỗi |

---

## 3. Cách Managed Daemons hoạt động

### 3.1 Đăng ký và gắn daemon

**📘 Theo docs AWS**

1. **Đăng ký daemon task definition** — một template mô tả các container tạo nên daemon.
2. **Tạo daemon** và gắn nó với **một cluster** và **một hoặc nhiều capacity provider Managed Instances**.
3. ECS bảo đảm **đúng một daemon task** chạy trên **mỗi EC2 instance** được cấp qua các capacity provider đó.

### 3.2 Thứ tự khởi động

**📘 Theo docs AWS**

- Daemon **không tự launch instance**.
- Khi bạn chạy task ứng dụng trên capacity provider Managed Instances, ECS:
  1. cấp phát EC2 instance;
  2. **khởi động daemon task**;
  3. **chỉ sau đó** mới chuyển task ứng dụng sang **`RUNNING`**.
- ECS áp dụng thứ tự này cho **mọi daemon**, bất kể critical hay không, để các chức năng xuyên suốt như **logging**, **tracing**, **thu thập metric** đã sẵn sàng trước khi ứng dụng bắt đầu xử lý request.

**💡 Giải thích thêm**

```
Task ứng dụng cần chạy
   → (nếu cần) ECS cấp instance Managed Instances
   → daemon task khởi động trên instance
   → task ứng dụng chuyển sang RUNNING
```

Liên hệ file *Task lifecycle*: daemon nằm "trước" giai đoạn task ứng dụng tới `RUNNING`. Daemon khởi động chậm thì task ứng dụng cũng đến `RUNNING` chậm hơn.

### 3.3 Criticality quyết định hành vi khi lỗi

**📘 Theo docs AWS**

**Critical daemon:**
- Daemon task **không khởi động được** → instance **không trở thành active**.
- Daemon task **dừng hoặc unhealthy** sau khi instance đã active → ECS **drain và thay** instance.
- Hành vi **tự sửa (auto-repair)** này giữ độ phủ daemon ổn định mà không cần can thiệp tay.

**Non-critical daemon:**
- **Không bao giờ chặn** instance và **không bao giờ** gây drain.
- Instance vẫn active dù daemon task không khởi động được, và vẫn active nếu daemon dừng hoặc unhealthy sau đó.
- Task ứng dụng vẫn chạy; ECS vẫn đặt task ứng dụng mới lên instance.

**💡 Giải thích thêm**

| Tình huống | Critical | Non-critical |
|---|---|---|
| Daemon không khởi động | Instance không active → task ứng dụng không được đặt lên | Instance vẫn active → task ứng dụng chạy bình thường |
| Daemon chết khi đang chạy | Drain + thay instance | Không làm gì với instance |
| Ưu tiên | **Độ phủ daemon** (không instance nào chạy app mà thiếu daemon) | **Liên tục của workload** |

Gợi ý chọn (của mình, docs không phân loại):
- **Critical**: agent bảo mật hoặc compliance — không được chạy ứng dụng nếu thiếu.
- **Non-critical**: agent metric/tracing "có thì tốt" — mất một lúc cũng không sao.

### 3.4 Cập nhật daemon

**📘 Theo docs AWS**

- Cập nhật daemon sang **task definition revision mới** → ECS thực hiện **rolling deployment** trên **mọi instance** trong các capacity provider được gắn.
- Trong quá trình deploy, ECS:
  - **drain đồng thời** một **tỉ lệ phần trăm instance** (cấu hình được);
  - cấp **instance thay thế** chạy daemon bản mới;
  - **tự thay** các task của ECS service.
- Có **circuit breaker** tích hợp sẵn.
- Có thể cấu hình **bake time** và **CloudWatch alarm** để ECS theo dõi deployment **sau khi cập nhật xong mọi instance** và **tự rollback** nếu có vấn đề.
- ECS phát **sự kiện EventBridge** và ghi **service action log** khi daemon task **không khởi động được** — cho cả critical lẫn non-critical.

**💡 Giải thích thêm**

```
Daemon revision N → N+1
   ├─ drain X% instance cùng lúc
   ├─ instance thay thế chạy daemon N+1
   ├─ task service được dời sang instance mới
   ├─ lặp lại tới khi hết instance
   └─ bake time + CloudWatch alarm → ổn thì giữ, lỗi thì rollback
```

Điểm cần lưu ý: cập nhật daemon **thay instance**, nên task của service cũng bị **dời chỗ**. Ứng dụng cần tắt êm (xử lý `SIGTERM`, `stopTimeout` hợp lý — file *Task lifecycle*), và service nên có đủ task để chịu được việc X% instance bị drain cùng lúc. Đây là suy luận từ mô tả của docs.

---

## 4. Lợi ích chính

**📘 Theo docs AWS**

| Lợi ích | Ý nghĩa theo docs |
|---|---|
| **Tách vòng đời quản lý** | Cập nhật daemon độc lập với deploy ứng dụng. |
| **Đảm bảo độ phủ** | ECS khởi động daemon trước task ứng dụng trên mọi instance, nên chức năng xuyên suốt sẵn sàng trước khi app xử lý request. Với daemon critical, daemon không khởi động được thì instance không đăng ký → instance **không bao giờ** chạy app mà thiếu daemon. |
| **Cập nhật phiên bản tin cậy** | Rollout trên mọi instance, có circuit breaker và tự rollback, bảo đảm mọi instance chạy đúng revision mục tiêu. |
| **Tận dụng tài nguyên tốt hơn** | Một daemon task mỗi instance thay cho mô hình **sidecar mỗi task**, giảm overhead tài nguyên. |
| **Tự sửa instance** | Với daemon critical, daemon dừng hoặc unhealthy → ECS tự drain và thay instance. |
| **Liên tục của workload** | Daemon non-critical giữ ứng dụng chạy liên tục dù daemon lỗi, dừng hoặc unhealthy. |

> **Note (docs):** ECS có **scheduling strategy `DAEMON`** cho service ECS trên **launch type EC2**. Managed Daemons là **tính năng mới** xây cho **Managed Instances**, giúp đơn giản việc deploy daemon và **đảm bảo độ phủ daemon mạnh hơn**.

**💡 Giải thích thêm**

| | Service với strategy `DAEMON` | Managed Daemons |
|---|---|---|
| Môi trường | Launch type **EC2** | **Managed Instances** |
| Thứ tự khởi động trước app | (trang này không nói) | Có, cho mọi daemon |
| Tự drain/thay instance khi daemon lỗi | (trang này không nói) | Có (critical) |
| Rolling update có bake time, alarm, rollback | (trang này không nói) | Có |

Cột trái để "(trang này không nói)" vì trang chỉ nhắc tên strategy `DAEMON`, không mô tả chi tiết.

---

## 5. Bắt đầu

**📘 Theo docs AWS**

- Đăng ký **daemon task definition** chỉ định container image, rồi **tạo daemon** gắn với capacity provider trong cluster.
- Dùng được **AWS Management Console**, **AWS CLI**, **CloudFormation** hoặc **AWS SDK**.
- Hướng dẫn từng bước: *Creating and managing daemons* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/managed-daemons-create-manage.html>.

**💡 Giải thích thêm**

Docs không nêu Terraform trong danh sách công cụ; nếu bạn quản lý hạ tầng bằng Terraform, cần kiểm tra AWS provider đã hỗ trợ tài nguyên daemon hay chưa (giống lưu ý ở file *Amazon ECS Gateway*).

---

## 6. Liên hệ với các file trước

**💡 Giải thích thêm**

| Nội dung | File liên quan |
|---|---|
| Sidecar (log router trong cùng task) | *Architect for EC2 capacity* — ví dụ container log streaming chung task với frontend; Managed Daemons là cách thay thế "một agent mỗi instance" |
| Daemon chạy trước khi task app `RUNNING` | *Task lifecycle*, *Standalone tasks* (luồng `PROVISIONING` → `RUNNING`) |
| Drain và thay instance | *Managed Instances auto scaling and task placement* |
| Agent thu log / metric | *Monitor a gateway* (CloudWatch), *Fargate task definition differences* (FireLens) |
| Chỉ cho Managed Instances | *Managed Instances task definition differences*, *task networking for Managed Instances* |

---

## 7. Tóm tắt

- **Managed Daemons**: chạy agent (bảo mật, observability, networking) trên **Managed Instances**, quản lý độc lập với ứng dụng.
- **Critical** (mặc định): daemon khởi động trước app; không khởi động được → instance không active; chết sau đó → drain và thay instance.
- **Non-critical** (`critical: false`): không bao giờ chặn instance hay gây drain; app vẫn chạy và vẫn được đặt.
- **Cách hoạt động**: đăng ký daemon task definition → tạo daemon gắn cluster + capacity provider Managed Instances → đúng một daemon task mỗi instance.
- Daemon không tự launch instance; ECS luôn khởi động daemon **trước** khi task app sang `RUNNING`.
- **Cập nhật**: rolling deployment, drain X% instance, instance thay thế chạy bản mới, task service được thay; có circuit breaker, bake time, CloudWatch alarm, tự rollback; daemon lỗi khởi động → sự kiện EventBridge + service action log.
- **Lợi ích**: tách vòng đời, đảm bảo độ phủ, cập nhật tin cậy, tiết kiệm tài nguyên so với sidecar, tự sửa instance, liên tục workload.
- Khác với strategy `DAEMON` của service trên EC2.
- Bắt đầu qua console, CLI, CloudFormation, SDK.

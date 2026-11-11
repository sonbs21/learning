# Amazon EC2 container instance security considerations for Amazon ECS

> Nguồn: **Amazon ECS Developer Guide — Amazon EC2 container instance security considerations for Amazon ECS**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/ec2-security-considerations.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: diễn đạt lại bằng tiếng Việt, bám sát từng ý và đúng thứ tự của trang gốc, không thêm thông tin ngoài trang. Đây không phải bản dịch từng chữ; khi cần câu chữ chính xác, đọc link gốc ở trên.
> - Khối **💡 Giải thích thêm**: phần giải thích để dễ hiểu hơn, chỉ làm rõ những gì docs đã nói.

---

## Mục lục

1. [Rủi ro: một instance, nhiều task](#1-rủi-ro-một-instance-nhiều-task)
2. [Bốn khuyến nghị của AWS](#2-bốn-khuyến-nghị-của-aws)
3. [Chặn truy cập IMDS (`ECS_AWSVPC_BLOCK_IMDS`)](#3-chặn-truy-cập-imds-ecs_awsvpc_block_imds)
4. [GuardDuty Runtime Monitoring](#4-guardduty-runtime-monitoring)
5. [Checklist bảo mật container instance EC2](#5-checklist-bảo-mật-container-instance-ec2)
6. [Tóm tắt](#6-tóm-tắt)

---

## 1. Rủi ro: một instance, nhiều task

**📘 Theo docs AWS**

- Nên đưa **một container instance** và **quyền truy cập của nó** vào **mô hình mối đe doạ (threat model)**.
- Ví dụ: một task **bị xâm nhập** có thể lợi dụng **quyền IAM** của một task **khác (không bị xâm nhập)** trên **cùng instance**.

**💡 Giải thích thêm**

```
┌──────────── EC2 container instance ────────────┐
│  Task A (bị xâm nhập)      Task B (task role X) │
│        │                                         │
│        └── cố lấy credential của B,              │
│            hoặc credential của instance profile  │
│            qua metadata (IMDS)                   │
└──────────────────────────────────────────────────┘
```

Đây là rủi ro của mô hình **nhiều task trên một instance** — cùng chủ đề với file *Security considerations for Amazon ECS Managed Instances* (Fargate thì mỗi task một môi trường cô lập).

---

## 2. Bốn khuyến nghị của AWS

**📘 Theo docs AWS**

| # | Khuyến nghị | Nội dung |
|---|---|---|
| 1 | **Không dùng quyền admin** khi chạy task | — |
| 2 | **Task role theo nguyên tắc quyền tối thiểu** | Container agent tự tạo **token** với **credential ID riêng** để truy cập tài nguyên ECS |
| 3 | **Chặn task `awsvpc` truy cập credential của instance profile** | Đặt biến cấu hình agent **`ECS_AWSVPC_BLOCK_IMDS=true`** trong file cấu hình agent rồi **restart agent**. Task vẫn dùng được quyền của **task role** (mục 3) |
| 4 | **GuardDuty Runtime Monitoring** | Phát hiện mối đe doạ cho cluster và container (mục 4) |

**💡 Giải thích thêm**

- Khuyến nghị 1 & 2 khớp file *Architect your application* ("mỗi task definition family một IAM role") — role riêng, quyền tối thiểu theo nghiệp vụ.
- "Không dùng quyền admin" có thể hiểu cả hai nghĩa: không gắn policy kiểu `AdministratorAccess` vào task role, và không chạy container `privileged` hay bằng root khi không cần (file *task definition parameters for Amazon EC2*: AWS khuyến nghị không dùng `privileged`). Phần mở rộng nghĩa thứ hai là của mình.

---

## 3. Chặn truy cập IMDS (`ECS_AWSVPC_BLOCK_IMDS`)

**📘 Theo docs AWS**

- Áp dụng cho task dùng network mode **`awsvpc`**.
- Mục tiêu: ngăn container lấy **credential** cấp cho **instance profile** của EC2, mà **vẫn giữ** quyền do **task role** cung cấp.
- Cách làm: đặt **`ECS_AWSVPC_BLOCK_IMDS`** = **`true`** trong file cấu hình agent, rồi **restart agent**.

**💡 Giải thích thêm**

Instance profile thường có quyền rộng hơn task role (cần cho agent: đăng ký instance, pull image, ghi log…). Nếu container gọi được **IMDS** (`169.254.169.254`), nó có thể lấy credential đó → leo thang quyền. Chặn IMDS buộc container chỉ dùng **task role**.

Cấu hình qua **user data** (cùng file `/etc/ecs/ecs.config` với `ECS_IMAGE_PULL_BEHAVIOR` ở file *Container image pull behavior*):

```bash
#!/bin/bash
cat <<'EOF' >> /etc/ecs/ecs.config
ECS_CLUSTER=<CLUSTER_NAME>
ECS_AWSVPC_BLOCK_IMDS=true
EOF
```

Với instance đang chạy, sau khi sửa file cần restart agent (vd `sudo systemctl restart ecs` trên AMI ECS-optimized Amazon Linux) — lệnh là kiến thức chung, trang chỉ nói "restart agent".

Lưu ý phạm vi: biến này chỉ áp cho task **`awsvpc`**. Task dùng mode **`bridge`** hoặc **`host`** cần biện pháp khác (vd chặn bằng iptables, hoặc đặt IMDS hop limit = 1 để container sau bridge không tới được IMDS) — các biện pháp này là kiến thức chung, trang không đề cập; tra thêm tài liệu *task IAM roles* của ECS. Đây cũng là một lý do nữa để ưu tiên `awsvpc` (file *task networking options for EC2*).

---

## 4. GuardDuty Runtime Monitoring

**📘 Theo docs AWS**

- Dùng **Amazon GuardDuty Runtime Monitoring** để **phát hiện mối đe doạ** cho cluster và container.
- Runtime Monitoring dùng **GuardDuty security agent**, bổ sung khả năng quan sát **lúc chạy** cho từng workload ECS, ví dụ:
  - **truy cập file**;
  - **thực thi tiến trình**;
  - **kết nối mạng**.
- (Xem *GuardDuty Runtime Monitoring* — <https://docs.aws.amazon.com/guardduty/latest/ug/runtime-monitoring.html>.)

**💡 Giải thích thêm**

Trên EC2, GuardDuty security agent chạy trên instance để quan sát mọi container. Trên Managed Instances, agent dạng này thường triển khai bằng **Managed Daemons** với capability đặc quyền (files *Amazon ECS Managed Daemons*, *Daemon task definitions*) — liên hệ của mình; trang này chỉ nói về EC2 container instance.

---

## 5. Checklist bảo mật container instance EC2

**💡 Giải thích thêm**

Ghép trang này với các file trước (mỗi dòng ghi nguồn):

```
IAM
[ ] Mỗi task definition family một task role, quyền tối thiểu          (trang này, Architect your application)
[ ] Không gắn quyền admin cho task role                                  (trang này)
[ ] Instance profile chỉ đủ quyền cho agent                              (gợi ý)

Cô lập
[ ] Dùng awsvpc + ECS_AWSVPC_BLOCK_IMDS=true                             (trang này)
[ ] Tránh privileged / root khi không cần                                (task definition parameters for EC2)
[ ] dockerSecurityOptions: no-new-privileges, SELinux/AppArmor nếu cần   (task definition parameters for EC2)
[ ] readonlyRootFilesystem khi được                                      (task definition parameters for EC2)
[ ] Workload cần cô lập mạnh → Fargate (hoặc single-task trên Managed Instances)
                                                                        (Define which container instances…, Security MI)

Giám sát
[ ] GuardDuty Runtime Monitoring                                         (trang này)
[ ] ECS Exec có logging (CloudWatch/S3)                                  (Creating a cluster for Fargate)

Vận hành
[ ] AMI ECS-optimized cập nhật thường xuyên, thay instance định kỳ       (gợi ý; Managed Instances làm tự động)
[ ] Managed instance draining bật                                        (capacity providers for EC2)
```

---

## 6. Tóm tắt

- Đưa **container instance** vào threat model: task bị xâm nhập có thể dùng quyền IAM của task khác trên **cùng instance**.
- **Khuyến nghị**:
  1. Không dùng **quyền admin** khi chạy task.
  2. **Task role quyền tối thiểu** (agent tự tạo token với credential ID riêng).
  3. Task **`awsvpc`**: đặt **`ECS_AWSVPC_BLOCK_IMDS=true`** trong cấu hình agent rồi restart → chặn credential của instance profile, vẫn giữ quyền task role.
  4. Bật **GuardDuty Runtime Monitoring** (quan sát truy cập file, tiến trình, kết nối mạng của từng workload).

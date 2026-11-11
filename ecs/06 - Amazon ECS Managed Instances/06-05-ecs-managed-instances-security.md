# Security considerations for Amazon ECS Managed Instances

> Nguồn: **Amazon ECS Developer Guide — Security considerations for Amazon ECS Managed Instances**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/managed-instances-security.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: diễn đạt lại bằng tiếng Việt, bám sát từng ý và đúng thứ tự của trang gốc, không thêm thông tin ngoài trang. Đây không phải bản dịch từng chữ; khi cần câu chữ hoặc ví dụ lệnh chính xác, đọc link gốc ở trên.
> - Khối **💡 Giải thích thêm**: phần giải thích để dễ hiểu hơn, chỉ làm rõ những gì docs đã nói.
> - Lệnh CLI dùng placeholder (`<...>`); tên lệnh, tham số và trường giữ đúng như docs.

---

## Mục lục

0. [Tổng quan](#0-tổng-quan)
1. [Mô hình bảo mật](#1-mô-hình-bảo-mật)
2. [Hiểu về managed instance](#2-hiểu-về-managed-instance)
3. [Tính năng bảo mật](#3-tính-năng-bảo-mật)
4. [Tuân thủ và quy định](#4-tuân-thủ-và-quy-định)
5. [FIPS-140](#5-fips-140)
6. [Tắt FIPS](#6-tắt-fips)
7. [Những điều cần cân nhắc về bảo mật](#7-những-điều-cần-cân-nhắc-về-bảo-mật)
8. [Best practices bảo mật](#8-best-practices-bảo-mật)
9. [Liên hệ và chỗ không khớp](#9-liên-hệ-và-chỗ-không-khớp)
10. [Tóm tắt](#10-tóm-tắt)

---

## 0. Tổng quan

**📘 Theo docs AWS**

- Managed Instances cho trải nghiệm compute container **được quản lý hoàn toàn**, chạy workload trên EC2 instance type cụ thể và **chuyển trách nhiệm bảo mật** sang AWS.
- Trang mô tả **mô hình bảo mật**, **tính năng** và **điều cần cân nhắc**.
- Phân chia trách nhiệm giữa AWS và khách hàng: *Shared responsibility model for Amazon ECS Managed Instances* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/security-shared-model-managed-instances.html>.

---

## 1. Mô hình bảo mật

**📘 Theo docs AWS**

| Đặc điểm | Nội dung |
|---|---|
| **Hạ tầng do AWS quản lý** | AWS kiểm soát vòng đời instance và vá bảo mật → loại bỏ khả năng **lỗi do con người** và **can thiệp trái phép** |
| **Không có quyền quản trị** | Mô hình bị khoá chặt, **cấm truy cập quản trị** vào managed instance |
| **Nhiều task trên một instance** | Mặc định đặt nhiều task trên một instance để tối ưu chi phí → **nới lỏng** ràng buộc cô lập workload so với Fargate |
| **Cô lập dữ liệu** | Dù AWS kiểm soát vòng đời instance và việc đặt task, AWS **không đăng nhập được** vào instance và **không truy cập** dữ liệu khách hàng |

---

## 2. Hiểu về managed instance

**📘 Theo docs AWS**

- Managed Instances cấp EC2 **managed instance** trong **account của bạn**. ECS là **operator** được chỉ định, quản lý toàn bộ vòng đời: cấp phát, scale, vá, terminate.
- Bạn **không có quyền** trực tiếp terminate hay sửa cài đặt các instance này. (Xem *Amazon EC2 managed instances* — <https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/amazon-ec2-managed-instances.html>.)

### 2.1 Nhận diện managed instance

**📘 Theo docs AWS**

| Dấu hiệu | Giá trị |
|---|---|
| Trường **`Operator`** trong kết quả `DescribeInstances` của EC2 | `ecs.amazonaws.com` |
| Tag **`aws:ec2:managed-launch`** trên instance | `ecs-managed-instances` |

### 2.2 Hiển thị tài nguyên được quản lý

**📘 Theo docs AWS**

- Từ **22/4/2026**, EC2 **mặc định ẩn** managed instance **mới** khỏi giao diện console EC2 và các thao tác **list** của API.
- Cài đặt hiển thị **không ảnh hưởng** tính tiền hay hoạt động — instance vẫn chạy và **vẫn tính tiền** dù bị ẩn.
- Điều chỉnh được bất cứ lúc nào. (Xem *Managed resource visibility settings* — <https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/amazon-ec2-managed-instances.html#managed-resource-visibility-settings>.)

**💡 Giải thích thêm**

Hệ quả thực tế: script hoặc công cụ kiểm kê (inventory, cost report) dựa vào `ec2 describe-instances` có thể **không thấy** managed instance mới sau mốc này, dù chúng vẫn tính tiền. Nếu cần thấy, chỉnh cài đặt hiển thị hoặc lọc theo `Operator` / tag ở 2.1. Đây là suy luận từ mô tả của docs.

Tag `aws:ec2:managed-launch` cũng hữu ích để lọc chi phí trong Cost Explorer (nếu tag được bật cho billing) — gợi ý của mình.

---

## 3. Tính năng bảo mật

### 3.1 Cấu hình theo best practice

**📘 Theo docs AWS**

| Biện pháp | Ý nghĩa |
|---|---|
| **Không SSH** | Tắt remote shell để tránh truy cập trái phép |
| **Root filesystem bất biến** | Không sửa được root filesystem → giữ toàn vẹn hệ thống |
| **Kiểm soát truy cập bắt buộc mức kernel** | **SELinux** tăng cường thực thi bảo mật ở kernel |

### 3.2 Tự động vá bảo mật

**📘 Theo docs AWS**

- **Cập nhật bảo mật định kỳ**: AWS cập nhật bản vá mới nhất, tôn trọng **maintenance window** bạn cấu hình.
- **Giới hạn vòng đời instance**: thời gian sống tối đa của instance đang chạy là **14 ngày**.
- **Kiểm soát cửa sổ bảo trì**: dùng **EC2 event windows** để chỉ định khi nào ECS thay instance bằng bản đã vá.

### 3.3 Linux capability đặc quyền

**📘 Theo docs AWS**

- Hỗ trợ phần mềm cần quyền Linux cao, cho giải pháp giám sát và bảo mật nâng cao.
- **Capability hỗ trợ**: opt-in được **mọi** capability đặc quyền, gồm `CAP_NET_ADMIN`, `CAP_SYS_ADMIN`, `CAP_BPF`, `CAP_PERFMON`.
- **Giải pháp phổ biến**: chạy được công cụ giám sát mạng và observability như **Wireshark**, **Datadog**.
- **Phải cấu hình tường minh**: phải bật capability đặc quyền **trên capacity provider** của Managed Instances, vì có thể gây thêm rủi ro bảo mật.

> **Important (docs):** Bật capability đặc quyền có thể đặt task trước **rủi ro bảo mật bổ sung**. Chỉ bật khi ứng dụng **thực sự cần** và bạn hiểu hệ quả.

**💡 Giải thích thêm**

Điểm mới so với file *Daemon task definitions*: muốn dùng `privileged` / capability đặc quyền (kể cả cho daemon), **capacity provider** cũng phải được cấu hình cho phép. Trang này không nêu tên trường cấu hình trên capacity provider — cần tra API tạo capacity provider.

---

## 4. Tuân thủ và quy định

**📘 Theo docs AWS**

| Nội dung | Chi tiết |
|---|---|
| **Chương trình tuân thủ** | Cùng phạm vi AWS Assurance Programs như ECS: **PCI-DSS**, **HIPAA**, **FedRAMP** |
| **FIPS endpoint** | Cấu hình ở **mức capacity provider**. Khác **Fargate** (cài đặt **mức account**) vì FIPS là cấu hình **theo từng instance**. Cấu hình khi tạo hoặc cập nhật capacity provider |
| **Customer Managed Keys** | Hỗ trợ tính năng bảo mật cần cho tuân thủ, như **CMK** để mã hoá |

---

## 5. FIPS-140

**📘 Theo docs AWS**

- AMI Managed Instances **tuân thủ FIPS-140** chỉ có ở **AWS GovCloud (US)**.
- Managed Instances hỗ trợ **FIPS-140-3**.
- Ở GovCloud (US), FIPS-140 **bật mặc định**. Muốn chạy không FIPS thì **tắt** trong cấu hình capacity provider.
- Task phải có `cpuArchitecture` là **`X86_64`** để tuân thủ FIPS-140.

**💡 Giải thích thêm**

Hệ quả: workload FIPS **không chạy được trên Graviton (ARM64)**. Ngoài GovCloud (US), không có AMI FIPS cho Managed Instances — liên hệ file *Fargate task networking* (CloudWatch không có dualstack FIPS endpoint cho IPv6-only) nếu bạn thiết kế hệ thống tuân thủ FIPS.

---

## 6. Tắt FIPS

**📘 Theo docs AWS**

Mặc định, capacity provider Managed Instances ở GovCloud (US) launch AMI tuân thủ FIPS. Có thể chọn **tắt** FIPS khi **tạo capacity provider mới**:

1. Tạo capacity provider với **`fipsEnabled: false`** trong `instanceLaunchTemplate`.

```bash
aws ecs create-capacity-provider \
  --cluster <CLUSTER> \
  --name <CAPACITY_PROVIDER_NAME> \
  --managed-instances-provider '{
    "infrastructureRoleArn": "<INFRA_ROLE_ARN>",
    "instanceLaunchTemplate": {
      "ec2InstanceProfileArn": "<INSTANCE_PROFILE_ARN>",
      "fipsEnabled": false,
      "networkConfiguration": { "subnets": ["<SUBNET_ID>"], "securityGroups": ["<SG_ID>"] }
    }
  }'
```

2. (Tuỳ chọn) Dùng **ECS Exec** để kiểm tra trạng thái FIPS: đọc file **`/proc/sys/crypto/fips_enabled`** trong container. Giá trị **`1`** nghĩa là đang dùng FIPS.

```bash
aws ecs execute-command \
  --cluster <CLUSTER> --task <TASK_ID> --container <CONTAINER> \
  --interactive --command "cat /proc/sys/crypto/fips_enabled"
```

**💡 Giải thích thêm**

Trang nói chọn tắt FIPS "khi tạo capacity provider **mới**", trong khi mục 4 nói cấu hình FIPS khi "tạo **hoặc cập nhật**". Muốn đổi trạng thái FIPS cho capacity provider có sẵn, nên kiểm tra API cập nhật có hỗ trợ không; an toàn nhất là tạo capacity provider mới như hướng dẫn.

---

## 7. Những điều cần cân nhắc về bảo mật

### 7.1 Mô hình nhiều task

**📘 Theo docs AWS**

| Điểm | Nội dung |
|---|---|
| **Chia sẻ tài nguyên instance** | Nhiều task có thể chạy chung instance → một task có thể bị ảnh hưởng bởi **lỗ hổng** từ task khác trên cùng instance hoặc cùng cluster |
| **Lựa chọn single-task** | Cấu hình được **chế độ single-task** cho khách hàng cần mô hình bảo mật như Fargate, với ranh giới cô lập **mức VM** |
| **Đánh đổi chi phí – bảo mật** | Multi-task: tối ưu chi phí, task khởi động nhanh hơn. Single-task: cô lập mạnh hơn |

### 7.2 Xử lý gián đoạn instance

**📘 Theo docs AWS**

- **Chịu được gián đoạn**: dùng Managed Instances cho ứng dụng chịu được việc service hoặc task bên dưới bị gián đoạn.
- **Workload dạng service**: dùng **ECS service** để tự thay task; hoặc chạy **standalone task** có thời lượng kiểm soát, **không quá 14 ngày**.
- **Tắt êm**: cấu hình **thời gian ân hạn khi tắt task** để kiểm soát ảnh hưởng của gián đoạn.

### 7.3 Truy cập dữ liệu và quyền riêng tư

**📘 Theo docs AWS**

- **Không truy cập dữ liệu khách hàng**: AWS kiểm soát vòng đời và việc đặt task, nhưng **không đăng nhập** được instance và **không truy cập** dữ liệu.
- **Chỉ metric và log**: AWS chỉ thu metric và log liên quan cần để cung cấp tính năng Managed Instances.
- **Mô hình khoá chặt**: cấm truy cập quản trị → loại bỏ khả năng lỗi do con người và can thiệp.

**💡 Giải thích thêm**

"Thời gian ân hạn khi tắt task" tương ứng với `stopTimeout` (file *task definition parameters for Managed Instances*, tối đa 86400 giây) — ứng dụng cần xử lý `SIGTERM` (file *Task lifecycle*).

---

## 8. Best practices bảo mật

**📘 Theo docs AWS**

| Best practice | Nội dung |
|---|---|
| **Đánh giá mô hình bảo mật** | Quyết định có chủ đích việc dùng Managed Instances theo yêu cầu bảo mật, nhất là mô hình **nhiều task trên một instance** |
| **Dùng single-task khi cần** | Workload cần cô lập mạnh → cấu hình **single-task** |
| **Giảm thiểu capability đặc quyền** | Chỉ bật khi **thật sự cần** và hiểu rủi ro |
| **Lên kế hoạch cho gián đoạn** | Ứng dụng phải chịu được việc thay instance, nhất là vòng đời tối đa **14 ngày** |
| **Cấu hình cửa sổ bảo trì** | Dùng **EC2 event windows** để kiểm soát lúc thay instance |
| **Giám sát và kiểm toán** | Định kỳ xem lại cấu hình Managed Instances và theo dõi sự kiện, thay đổi liên quan bảo mật |

---

## 9. Liên hệ và chỗ không khớp

**💡 Giải thích thêm**

**Liên hệ với các file trước:**

| Nội dung | File liên quan |
|---|---|
| Không SSH, root filesystem bất biến, SELinux, Bottlerocket | *Architect for Amazon ECS Managed Instances* |
| Vòng đời, event window, trình tự drain | *Patching in Amazon ECS Managed Instances* |
| Single-task / cô lập mức VM | *Architect for Amazon ECS Managed Instances* (tuỳ chọn mỗi task một instance) |
| Capability đặc quyền cho agent | *Daemon task definitions*, *Amazon ECS Managed Daemons* |
| FIPS trên Fargate (mức account) | *Fargate task networking* |
| Organization controls (SCP với AMI) | *Architect for Amazon ECS Managed Instances* |

**Chỗ không khớp:**

| Chủ đề | Trang này | Trang khác |
|---|---|---|
| Vòng đời instance | "Tối đa **14 ngày**" | *Patching*: drain từ ngày 14, terminate **chậm nhất ngày 21** |
| Đổi FIPS | Mục 4: tạo **hoặc cập nhật**; mục 6: tắt khi tạo capacity provider **mới** | — |

---

## 10. Tóm tắt

- **Mô hình bảo mật**: AWS quản lý hạ tầng và vá; không quyền quản trị; mặc định nhiều task/instance (cô lập kém Fargate); AWS không đăng nhập và không truy cập dữ liệu.
- **Managed instance** nằm trong account bạn, ECS là operator; bạn không terminate/sửa trực tiếp. Nhận diện qua `Operator = ecs.amazonaws.com` hoặc tag `aws:ec2:managed-launch = ecs-managed-instances`. Từ **22/4/2026**, EC2 mặc định **ẩn** managed instance mới (vẫn tính tiền).
- **Tính năng**: không SSH, root filesystem bất biến, SELinux; tự vá theo maintenance window; vòng đời 14 ngày; EC2 event windows; capability đặc quyền (`NET_ADMIN`, `SYS_ADMIN`, `BPF`, `PERFMON`) phải **bật tường minh trên capacity provider**.
- **Tuân thủ**: PCI-DSS, HIPAA, FedRAMP; FIPS ở **mức capacity provider** (Fargate: mức account); hỗ trợ CMK.
- **FIPS-140**: AMI FIPS chỉ ở **GovCloud (US)**; FIPS-140-3; bật mặc định ở GovCloud; cần `X86_64`; tắt bằng `fipsEnabled: false`; kiểm tra `/proc/sys/crypto/fips_enabled` qua ECS Exec.
- **Cân nhắc**: multi-task chia sẻ tài nguyên → dùng **single-task** nếu cần cô lập mức VM; ứng dụng phải chịu gián đoạn (service tự thay task, standalone ≤ 14 ngày, tắt êm); AWS chỉ thu metric và log.
- **Best practices**: đánh giá mô hình, single-task khi cần, hạn chế capability đặc quyền, chuẩn bị cho gián đoạn, cấu hình event window, giám sát và kiểm toán.

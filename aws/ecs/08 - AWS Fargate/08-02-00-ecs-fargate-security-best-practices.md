# Fargate security best practices in Amazon ECS

> Nguồn: **Amazon ECS Developer Guide — Fargate security best practices in Amazon ECS**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/security-fargate.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: **bản dịch** sang tiếng Việt, đi đúng thứ tự và đúng nội dung của trang gốc, không thêm, không bớt ý. Lệnh ví dụ giữ nguyên như trên docs.
> - Khối **💡 Giải thích thêm**: chỉ giải thích lại những gì **chính trang này** đã nói, cho dễ hiểu hơn. Không đưa thông tin từ trang khác hay ví dụ tự đặt.
> - Tên tham số, capability, dịch vụ giữ nguyên tiếng Anh.
> - Tài liệu AWS được phát hành theo giấy phép **Creative Commons Attribution-ShareAlike 4.0**; bản dịch này ghi rõ nguồn ở trên.

---

## Mục lục

0. [Phần mở đầu](#0-phần-mở-đầu)
1. [Dùng AWS KMS để mã hoá ephemeral storage cho Fargate](#1-dùng-aws-kms-để-mã-hoá-ephemeral-storage-cho-fargate)
2. [Capability SYS_PTRACE để trace syscall của kernel với Fargate](#2-capability-sys_ptrace-để-trace-syscall-của-kernel-với-fargate)
3. [Dùng Amazon GuardDuty với Fargate Runtime Monitoring](#3-dùng-amazon-guardduty-với-fargate-runtime-monitoring)

---

## 0. Phần mở đầu

**📘 Theo docs AWS**

Chúng tôi khuyến nghị bạn tính tới các best practice sau khi dùng AWS Fargate.

---

## 1. Dùng AWS KMS để mã hoá ephemeral storage cho Fargate

**📘 Theo docs AWS**

Bạn nên để **ephemeral storage** được mã hoá bằng **AWS KMS** hoặc **customer managed key** của riêng bạn. Với task chạy trên Fargate dùng platform version **`1.4.0` trở lên**, mỗi task nhận **20 GiB** ephemeral storage. Thông tin thêm, xem *customer managed key (CMK)* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/fargate-storage-encryption.html>). Bạn có thể tăng tổng lượng ephemeral storage, **tối đa 200 GiB**, bằng cách chỉ định tham số **`ephemeralStorage`** trong task definition. Với những task như vậy được khởi chạy **từ ngày 28 tháng 5 năm 2020** trở đi, ephemeral storage được mã hoá bằng thuật toán mã hoá **AES-256**, dùng khoá mã hoá **do Fargate quản lý**.

Thông tin thêm, xem *Storage options for Amazon ECS tasks* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/using_data_volumes.html>).

**Ví dụ: Khởi chạy một task trên Fargate platform version 1.4.0 với mã hoá ephemeral storage**

Lệnh sau sẽ khởi chạy một task trên Fargate platform version 1.4. Vì task này được khởi chạy như một phần của cluster, nó dùng 20 GiB ephemeral storage được **tự động mã hoá**.

```
aws ecs run-task --cluster clustername \
  --task-definition taskdefinition:version \
  --count 1
  --launch-type "FARGATE" \
  --platform-version 1.4.0 \
  --network-configuration "awsvpcConfiguration={subnets=[subnetid],securityGroups=[securitygroupid]}" \
  --region region
```

**💡 Giải thích thêm**

| Điểm | Theo trang |
|---|---|
| Ephemeral storage mặc định | **20 GiB** mỗi task (platform version ≥ `1.4.0`) |
| Tăng tối đa | **200 GiB** qua `ephemeralStorage` trong task definition |
| Mã hoá mặc định | **AES-256**, khoá do **Fargate** quản lý (task khởi chạy từ 28/5/2020) |
| Khuyến nghị | Mã hoá bằng **AWS KMS** hoặc **customer managed key** của bạn |

Trong lệnh ví dụ trên trang gốc, dòng `--count 1` **thiếu dấu `\`** ở cuối dòng. Chạy nguyên văn trong shell, lệnh sẽ dừng ở dòng đó và các tham số phía sau (`--launch-type`, `--platform-version`…) không được truyền vào. Khi dùng, thêm `\` sau `--count 1`.

---

## 2. Capability SYS_PTRACE để trace syscall của kernel với Fargate

**📘 Theo docs AWS**

Cấu hình mặc định của các **Linux capability** được thêm hoặc bỏ khỏi container do **Docker** cung cấp.

Task khởi chạy trên Fargate **chỉ hỗ trợ thêm** kernel capability **`SYS_PTRACE`**.

Video sau cho thấy cách dùng tính năng này thông qua dự án **Falco** của Sysdig (<https://github.com/falcosecurity/falco>).

Video: <https://www.youtube-nocookie.com/embed/OYGKjmFeLqI>

Code được bàn trong video trên có trên GitHub tại đây (<https://github.com/paavan98pm/ecs-fargate-pv1.4-falco>).

**💡 Giải thích thêm**

| Điểm | Theo trang |
|---|---|
| Linux capability mặc định | Theo cấu hình của Docker |
| Capability **thêm** được trên Fargate | **Chỉ** `SYS_PTRACE` |
| Mục đích | Trace syscall của kernel (ví dụ với Falco của Sysdig) |

---

## 3. Dùng Amazon GuardDuty với Fargate Runtime Monitoring

**📘 Theo docs AWS**

**Amazon GuardDuty** là dịch vụ **phát hiện mối đe doạ**, giúp bảo vệ account, container, workload và dữ liệu trong môi trường AWS của bạn. Dùng các mô hình **machine learning (ML)**, cùng khả năng phát hiện bất thường và mối đe doạ, GuardDuty liên tục giám sát các nguồn log và hoạt động runtime khác nhau để nhận diện và ưu tiên các rủi ro bảo mật tiềm ẩn và hoạt động độc hại trong môi trường của bạn.

**Runtime Monitoring** trong GuardDuty bảo vệ workload chạy trên Fargate bằng cách liên tục giám sát log AWS và hoạt động mạng để nhận diện hành vi độc hại hoặc trái phép. Runtime Monitoring dùng một **GuardDuty security agent** nhẹ, được **quản lý hoàn toàn**, phân tích hành vi trên host, như **truy cập file**, **thực thi tiến trình**, và **kết nối mạng**. Điều này bao quát các vấn đề gồm **leo thang đặc quyền**, dùng **credential bị lộ**, giao tiếp với **địa chỉ IP và domain độc hại**, và sự hiện diện của **malware** trên Amazon EC2 instance và workload container. Thông tin thêm, xem *GuardDuty Runtime Monitoring* (<https://docs.aws.amazon.com/guardduty/latest/ug/runtime-monitoring.html>) trong *GuardDuty User Guide*.

**💡 Giải thích thêm**

```
GuardDuty Runtime Monitoring (agent nhẹ, được quản lý hoàn toàn)
  quan sát: truy cập file · thực thi tiến trình · kết nối mạng
  phát hiện: leo thang đặc quyền · credential bị lộ · IP/domain độc hại · malware
```

| Agent quan sát | Vấn đề phát hiện được (theo trang) |
|---|---|
| Truy cập file | Leo thang đặc quyền |
| Thực thi tiến trình | Dùng credential bị lộ |
| Kết nối mạng | Giao tiếp với IP/domain độc hại |
| | Malware trên EC2 instance và workload container |

---

**💡 Tổng hợp ba best practice của trang**

| # | Best practice | Ý chính |
|---|---|---|
| 1 | Mã hoá ephemeral storage | KMS hoặc customer managed key; mặc định 20 GiB, tối đa 200 GiB; mặc định đã có AES-256 do Fargate quản lý |
| 2 | `SYS_PTRACE` | Capability duy nhất thêm được trên Fargate — để trace syscall (vd Falco) |
| 3 | GuardDuty Runtime Monitoring | Agent quản lý hoàn toàn, phát hiện hành vi độc hại lúc chạy |

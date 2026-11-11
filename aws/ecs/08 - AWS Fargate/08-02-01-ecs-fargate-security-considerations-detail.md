# Fargate security considerations for Amazon ECS

> Nguồn: **Amazon ECS Developer Guide — Fargate security considerations for Amazon ECS**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/fargate-security-considerations.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: **bản dịch** sang tiếng Việt, đi đúng thứ tự và đúng nội dung của trang gốc, không thêm, không bớt ý.
> - Khối **💡 Giải thích thêm**: chỉ giải thích lại những gì **chính trang này** đã nói, cho dễ hiểu hơn. Không đưa thông tin từ trang khác hay ví dụ tự đặt.
> - Tên Linux capability, tính năng giữ nguyên tiếng Anh.
> - Tài liệu AWS được phát hành theo giấy phép **Creative Commons Attribution-ShareAlike 4.0**; bản dịch này ghi rõ nguồn ở trên.

---

## Mục lục

0. [Cô lập giữa các task và sidecar](#0-cô-lập-giữa-các-task-và-sidecar)
1. [Chia sẻ tài nguyên giữa các container trong cùng task](#1-chia-sẻ-tài-nguyên-giữa-các-container-trong-cùng-task)
2. [Những giới hạn của môi trường Fargate](#2-những-giới-hạn-của-môi-trường-fargate)

---

## 0. Cô lập giữa các task và sidecar

**📘 Theo docs AWS**

Mỗi task có **capacity hạ tầng riêng**, vì Fargate chạy mỗi workload trên một **môi trường ảo cô lập**. Workload chạy trên Fargate **không dùng chung** network interface, ephemeral storage, CPU hay memory với các task khác. Bạn có thể chạy **nhiều container** trong một task, gồm container ứng dụng và **sidecar container**, hoặc chỉ sidecar. **Sidecar** là container chạy **bên cạnh** container ứng dụng trong một task Amazon ECS. Trong khi container ứng dụng chạy code lõi của ứng dụng, các tiến trình chạy trong sidecar có thể **bổ trợ** cho ứng dụng. Sidecar giúp bạn **tách** các chức năng của ứng dụng thành các container riêng, giúp bạn cập nhật từng phần của ứng dụng dễ hơn.

**💡 Giải thích thêm**

```
Fargate
 ├─ Task A (môi trường ảo cô lập: ENI, ephemeral storage, CPU, memory riêng)
 │    ├─ container ứng dụng  (code lõi)
 │    └─ sidecar            (bổ trợ ứng dụng)
 └─ Task B (cô lập với Task A)
```

| Khái niệm | Theo trang |
|---|---|
| Task Fargate | Capacity hạ tầng riêng, môi trường ảo cô lập |
| Sidecar | Container chạy cạnh container ứng dụng, bổ trợ ứng dụng |
| Lợi ích sidecar | Tách chức năng thành container riêng → dễ cập nhật từng phần |

---

## 1. Chia sẻ tài nguyên giữa các container trong cùng task

**📘 Theo docs AWS**

Các container thuộc **cùng một task** **dùng chung tài nguyên** với launch type Fargate, vì các container này **luôn chạy trên cùng host** và dùng chung tài nguyên compute. Các container này cũng **dùng chung ephemeral storage** do Fargate cung cấp. Các Linux container trong một task **dùng chung network namespace**, gồm **địa chỉ IP** và **network port**. Bên trong một task, các container thuộc task có thể giao tiếp với nhau qua **localhost**.

**💡 Giải thích thêm**

Ranh giới cô lập nằm ở **task**, không phải ở container:

| Tài nguyên | Giữa các **task** khác nhau | Giữa các **container** trong cùng task |
|---|---|---|
| Host | Riêng | **Chung** |
| CPU, memory (compute) | Riêng | **Chung** |
| Ephemeral storage | Riêng | **Chung** |
| Network namespace (IP, port) | Riêng | **Chung** (Linux container) |
| Giao tiếp | Qua mạng | Qua **localhost** |

Vì dùng chung IP và port, hai container trong cùng task không thể cùng lắng nghe một port (suy ra trực tiếp từ việc dùng chung network namespace mà trang nêu).

---

## 2. Những giới hạn của môi trường Fargate

**📘 Theo docs AWS**

Môi trường runtime trong Fargate **ngăn** bạn dùng một số tính năng điều khiển được hỗ trợ trên EC2 instance. Cân nhắc các điểm sau khi thiết kế workload chạy trên Fargate:

- **Không có container đặc quyền hay quyền truy cập đặc quyền** - Các tính năng như **privileged container** hay quyền truy cập đặc quyền hiện **không có** trên Fargate. Điều này ảnh hưởng các trường hợp sử dụng như chạy **Docker in Docker**.

- **Giới hạn quyền truy cập Linux capability** - Môi trường nơi container chạy trên Fargate bị **khoá chặt**. Các Linux capability bổ sung, như **CAP_SYS_ADMIN** và **CAP_NET_ADMIN**, bị **hạn chế** để ngăn **leo thang đặc quyền**. Fargate hỗ trợ thêm Linux capability **CAP_SYS_PTRACE** (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task_definition_parameters.html#other_task_definition_params>) vào task để cho phép các công cụ **quan sát và bảo mật** triển khai trong task giám sát ứng dụng container.

- **Không truy cập được host bên dưới** - **Cả khách hàng lẫn nhân viên vận hành AWS** đều **không thể** kết nối tới host chạy workload của khách hàng. Bạn có thể dùng **ECS Exec** để chạy lệnh trong, hoặc mở shell vào, container chạy trên Fargate. Bạn có thể dùng ECS Exec để thu thập thông tin chẩn đoán phục vụ debug. Fargate cũng **ngăn container truy cập tài nguyên của host bên dưới**, như hệ thống file, thiết bị, mạng, và container runtime.

- **Mạng** - Bạn có thể dùng **security group** và **network ACL** để kiểm soát traffic vào và ra. Task Fargate nhận địa chỉ IP từ **subnet đã cấu hình** trong VPC của bạn.

**💡 Giải thích thêm**

| Giới hạn | Hệ quả theo trang | Thay thế theo trang |
|---|---|---|
| Không privileged container | Không chạy được Docker in Docker | — |
| Hạn chế Linux capability (CAP_SYS_ADMIN, CAP_NET_ADMIN…) | Ngăn leo thang đặc quyền | Thêm được **CAP_SYS_PTRACE** cho công cụ quan sát/bảo mật |
| Không truy cập host | Không ai (cả AWS) kết nối vào host; container không chạm file system, thiết bị, mạng, runtime của host | **ECS Exec** để chạy lệnh / mở shell / thu thập chẩn đoán |
| Mạng | — | Kiểm soát bằng **security group** + **network ACL**; IP lấy từ subnet trong VPC |

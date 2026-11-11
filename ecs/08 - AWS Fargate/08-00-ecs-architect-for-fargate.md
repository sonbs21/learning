# Architect for AWS Fargate for Amazon ECS

> Nguồn: **Amazon ECS Developer Guide — Architect for AWS Fargate for Amazon ECS**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/AWS_Fargate.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: **bản dịch** sang tiếng Việt, đi đúng thứ tự và đúng nội dung của trang gốc, không thêm, không bớt ý.
> - Khối **💡 Giải thích thêm**: chỉ giải thích lại những gì **chính trang này** đã nói, cho dễ hiểu hơn. Không đưa thông tin từ trang khác hay ví dụ tự đặt.
> - Tên tham số, platform version, capacity provider giữ nguyên tiếng Anh.
> - Tài liệu AWS được phát hành theo giấy phép **Creative Commons Attribution-ShareAlike 4.0**; bản dịch này ghi rõ nguồn ở trên.

---

## Mục lục

0. [Phần mở đầu](#0-phần-mở-đầu)
1. [Hướng dẫn từng bước](#1-hướng-dẫn-từng-bước)
2. [Capacity provider](#2-capacity-provider)
3. [Task definition](#3-task-definition)
4. [Platform version](#4-platform-version)
5. [Cân bằng tải cho service](#5-cân-bằng-tải-cho-service)
6. [Usage metric](#6-usage-metric)

---

## 0. Phần mở đầu

**📘 Theo docs AWS**

**AWS Fargate** là công nghệ bạn có thể dùng với Amazon ECS để chạy container (<https://aws.amazon.com/containers/>) **mà không phải quản lý server** hay cluster các Amazon EC2 instance. Với AWS Fargate, bạn không còn phải cấp phát, cấu hình hay scale cluster máy ảo để chạy container. Điều này loại bỏ việc phải chọn loại server, quyết định khi nào scale cluster, hay tối ưu việc xếp task vào cluster.

Khi chạy task và service với Fargate, bạn **đóng gói** ứng dụng trong container, **chỉ định CPU và memory** cần, **định nghĩa mạng và IAM policy**, rồi khởi chạy ứng dụng. **Mỗi task Fargate có ranh giới cô lập riêng** và **không dùng chung** kernel, tài nguyên CPU, tài nguyên memory, hay elastic network interface bên dưới với task khác. Bạn cấu hình task definition cho Fargate bằng cách đặt tham số task definition **`requiresCompatibilities`** thành **`FARGATE`**. Thông tin thêm, xem *Capacity* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task_definition_parameters.html#requires_compatibilities>).

Fargate cung cấp platform version cho **Amazon Linux 2** (platform version **1.3.0**), hệ điều hành **Bottlerocket** (platform version **1.4.0**), và **Microsoft Windows 2019 Server** bản Full và Core. Trừ khi có nói khác, thông tin áp dụng cho mọi nền tảng Fargate.

Thông tin về các Region hỗ trợ Linux container trên Fargate, xem *Linux containers on AWS Fargate* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/AWS_Fargate-Regions.html#linux-regions>).

Thông tin về các Region hỗ trợ Windows container trên Fargate, xem *Windows containers on AWS Fargate* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/AWS_Fargate-Regions.html#windows-regions>).

**💡 Giải thích thêm**

| Không còn phải làm (theo trang) | Bạn vẫn làm |
|---|---|
| Cấp phát, cấu hình, scale cluster máy ảo | Đóng gói ứng dụng thành container |
| Chọn loại server | Chỉ định CPU và memory |
| Quyết định khi nào scale cluster | Định nghĩa mạng và IAM policy |
| Tối ưu việc xếp task | Khởi chạy ứng dụng |

Cô lập: mỗi task Fargate **không dùng chung** kernel, CPU, memory, ENI với task khác.

| Nền tảng Fargate | Platform version |
|---|---|
| Amazon Linux 2 | 1.3.0 |
| Bottlerocket | 1.4.0 |
| Windows Server 2019 (Full, Core) | (trang không nêu số) |

Bật Fargate trong task definition: `requiresCompatibilities` = `FARGATE`.

---

## 1. Hướng dẫn từng bước

**📘 Theo docs AWS**

Thông tin về cách bắt đầu bằng console, xem:

- *Learn how to create an Amazon ECS Linux task for Fargate* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/getting-started-fargate.html>)
- *Learn how to create an Amazon ECS Windows task for Fargate* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/Windows_fargate-getting_started.html>)

Thông tin về cách bắt đầu bằng AWS CLI, xem:

- *Creating an Amazon ECS Linux task for the Fargate with the AWS CLI* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/ECS_AWSCLI_Fargate.html>)
- *Creating an Amazon ECS Windows task for the Fargate with the AWS CLI* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/ECS_AWSCLI_Fargate_windows.html>)

---

## 2. Capacity provider

**📘 Theo docs AWS**

Các capacity provider sau có sẵn:

- **Fargate**
- **Fargate Spot** - Chạy task Amazon ECS **chịu được gián đoạn** với giá **giảm** so với giá AWS Fargate. Fargate Spot chạy task trên **capacity compute dư**. Khi AWS cần lấy lại capacity, task của bạn sẽ bị gián đoạn với **cảnh báo trước hai phút**. Thông tin thêm, xem *Amazon ECS clusters for Fargate* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/fargate-capacity-providers.html>).

**💡 Giải thích thêm**

| Capacity provider | Giá | Gián đoạn |
|---|---|---|
| Fargate | Giá Fargate | — |
| Fargate Spot | Giảm giá | Có — cảnh báo trước 2 phút khi AWS lấy lại capacity |

---

## 3. Task definition

**📘 Theo docs AWS**

Task Fargate **không hỗ trợ tất cả** tham số task definition Amazon ECS. Một số tham số **hoàn toàn không** được hỗ trợ, và một số khác **hoạt động khác** với task Fargate. Thông tin thêm, xem *Task CPU and memory* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/fargate-tasks-services.html#fargate-tasks-size>).

---

## 4. Platform version

**📘 Theo docs AWS**

**AWS Fargate platform version** dùng để chỉ một **môi trường runtime cụ thể** cho hạ tầng task Fargate. Đó là sự kết hợp giữa phiên bản **kernel** và **container runtime**. Bạn chọn platform version khi chạy task hoặc khi tạo service để duy trì một số task giống nhau.

**Revision mới** của platform version được phát hành khi môi trường runtime thay đổi, ví dụ khi có cập nhật kernel hoặc hệ điều hành, tính năng mới, sửa lỗi, hoặc cập nhật bảo mật. Một Fargate platform version được cập nhật bằng cách tạo một **revision mới** của platform version. **Mỗi task chạy trên một revision** của platform version **trong suốt vòng đời** của nó. Nếu muốn dùng revision mới nhất, bạn phải **khởi chạy task mới**. Một task mới chạy trên Fargate **luôn chạy trên revision mới nhất** của platform version, đảm bảo task luôn được khởi chạy trên hạ tầng an toàn và đã vá.

Nếu phát hiện một vấn đề bảo mật ảnh hưởng một platform version hiện có, AWS tạo một **revision mới đã vá** của platform version và **retire** (cho nghỉ) các task đang chạy trên revision có lỗ hổng. Trong một số trường hợp, bạn có thể được thông báo rằng task trên Fargate đã được **lên lịch retire**. Thông tin thêm, xem *Task retirement and maintenance for AWS Fargate on Amazon ECS* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task-maintenance.html>).

Thông tin thêm, xem *Fargate platform versions for Amazon ECS* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/platform-fargate.html>).

**💡 Giải thích thêm**

```
Platform version  = kernel + container runtime
   └─ revision 1 ─► revision 2 ─► revision 3 (mới nhất)   ← có cập nhật kernel/OS, tính năng, sửa lỗi, bảo mật

Task đang chạy:  giữ nguyên revision lúc nó khởi chạy (suốt vòng đời)
Task mới:        luôn chạy trên revision mới nhất
Lỗ hổng bảo mật: AWS tạo revision đã vá → retire task trên revision lỗi (có thể có thông báo lịch retire)
```

| Câu hỏi | Theo trang |
|---|---|
| Muốn task lên revision mới nhất? | Phải **khởi chạy task mới** |
| Task đang chạy có tự lên revision mới? | Không — giữ revision cũ suốt vòng đời |
| Revision có lỗ hổng? | AWS **retire** task đang chạy trên revision đó |

---

## 5. Cân bằng tải cho service

**📘 Theo docs AWS**

Amazon ECS service trên AWS Fargate có thể **tuỳ chọn** được cấu hình dùng Elastic Load Balancing để phân phối traffic đều cho các task trong service.

Amazon ECS service trên AWS Fargate hỗ trợ các loại load balancer **Application Load Balancer**, **Network Load Balancer** và **Gateway Load Balancer**. Application Load Balancer được dùng để định tuyến traffic HTTP/HTTPS (tầng 7). Network Load Balancer được dùng để định tuyến traffic TCP hoặc UDP (tầng 4). Thông tin thêm, xem *Use load balancing to distribute Amazon ECS service traffic* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/service-load-balancing.html>).

Khi tạo target group cho các service này, bạn phải chọn target type **`ip`**, **không phải** `instance`. Đó là vì task dùng network mode **`awsvpc`** gắn với một elastic network interface, không phải một Amazon EC2 instance. Thông tin thêm, xem *Use load balancing to distribute Amazon ECS service traffic* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/service-load-balancing.html>).

Dùng Network Load Balancer để định tuyến traffic **UDP** tới task Amazon ECS trên AWS Fargate **chỉ được hỗ trợ** khi dùng **platform version 1.4 trở lên**.

**💡 Giải thích thêm**

| Loại | Dùng cho (theo trang) |
|---|---|
| Application Load Balancer | HTTP/HTTPS (tầng 7) |
| Network Load Balancer | TCP/UDP (tầng 4); **UDP cần platform version ≥ 1.4** |
| Gateway Load Balancer | (trang chỉ nêu là được hỗ trợ) |

Target type cho service Fargate: **`ip`** (vì task dùng `awsvpc`, gắn với ENI).

---

## 6. Usage metric

**📘 Theo docs AWS**

Bạn có thể dùng **CloudWatch usage metric** để có cái nhìn về mức dùng tài nguyên của account. Dùng các metric này để hiển thị mức dùng service hiện tại trên biểu đồ và dashboard CloudWatch.

Usage metric của AWS Fargate **tương ứng với AWS service quota**. Bạn có thể cấu hình **alarm** cảnh báo khi mức dùng **gần chạm** một service quota. Thông tin thêm về service quota của AWS Fargate, xem *Amazon ECS endpoints and quotas* (<https://docs.aws.amazon.com/general/latest/gr/ecs-service.html>) trong *Amazon Web Services General Reference*.

Thông tin thêm về usage metric của AWS Fargate, xem *AWS Fargate usage metrics* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/monitoring-fargate-usage.html>).

**💡 Giải thích thêm**

```
Usage metric của Fargate ≙ service quota
   └─ vẽ lên graph/dashboard CloudWatch
   └─ đặt alarm khi mức dùng gần chạm quota
```

# Amazon ECS service quotas and API throttling limits

> Nguồn: **Amazon ECS Developer Guide — Amazon ECS service quotas and API throttling limits**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/operating-at-scale-service-quotas-best-practice.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: **bản dịch** sang tiếng Việt, đi đúng thứ tự và đúng nội dung của trang gốc, không thêm, không bớt ý.
> - Khối **💡 Giải thích thêm**: chỉ giải thích lại những gì **chính trang này** đã nói, cho dễ hiểu hơn. Không đưa thông tin từ trang khác hay ví dụ tự đặt.
> - Tên quota, tên API, thông điệp lỗi giữ nguyên tiếng Anh.
> - Tài liệu AWS được phát hành theo giấy phép **Creative Commons Attribution-ShareAlike 4.0**; bản dịch này ghi rõ nguồn ở trên.

---

## Mục lục

0. [Phần mở đầu](#0-phần-mở-đầu)
1. [Các service quota của Amazon ECS cần cân nhắc](#1-các-service-quota-của-amazon-ecs-cần-cân-nhắc)
2. [Elastic Load Balancing](#2-elastic-load-balancing)
3. [Elastic network interfaces](#3-elastic-network-interfaces)
4. [AWS Cloud Map](#4-aws-cloud-map)
5. [Tổng hợp](#5-tổng-hợp)

---

## 0. Phần mở đầu

**📘 Theo docs AWS**

Amazon ECS tích hợp với nhiều dịch vụ AWS, gồm Elastic Load Balancing, AWS Cloud Map và Amazon EC2. Nhờ tích hợp chặt chẽ này, Amazon ECS có nhiều tính năng như cân bằng tải cho service, service discovery, task networking và cluster auto scaling. Amazon ECS và các dịch vụ AWS mà nó tích hợp đều duy trì **service quota** và **giới hạn tốc độ API (API rate limit)** để đảm bảo hiệu năng và mức sử dụng nhất quán. Các service quota này cũng ngăn việc vô tình cấp nhiều tài nguyên hơn mức cần, và bảo vệ khỏi các hành động độc hại có thể làm tăng hoá đơn của bạn.

Khi nắm rõ service quota và giới hạn tốc độ API của AWS, bạn có thể lên kế hoạch scale workload mà không lo hiệu năng bị giảm bất ngờ. Thông tin thêm, xem *Amazon ECS service quotas* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/service-quotas.html>) và *Request throttling for the Amazon ECS API* (<https://docs.aws.amazon.com/AmazonECS/latest/APIReference/request-throttling.html>).

**💡 Giải thích thêm**

| Loại giới hạn | Mục đích theo trang |
|---|---|
| Service quota | Hiệu năng nhất quán; tránh vô tình cấp quá nhiều tài nguyên; chặn hành động độc hại làm tăng hoá đơn |
| API rate limit (throttling) | Hiệu năng và mức sử dụng nhất quán |

Trang đi qua quota của chính ECS (mục 1), rồi quota và throttling của ba dịch vụ tích hợp: Elastic Load Balancing, ENI (EC2), AWS Cloud Map.

---

## 1. Các service quota của Amazon ECS cần cân nhắc

**📘 Theo docs AWS**

Khi scale workload trên Amazon ECS, hãy cân nhắc các service quota sau. Hướng dẫn cách yêu cầu tăng service quota, xem *Managing your Amazon ECS and AWS Fargate service quotas in the AWS Management Console* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/service-quotas-manage.html>).

- **AWS Fargate** có quota giới hạn số task chạy đồng thời ở mỗi AWS Region. Có quota cho cả task On-Demand và Fargate Spot trên Amazon ECS. Mỗi service quota cũng **tính cả** các Amazon EKS pod mà bạn chạy trên Fargate. Thông tin thêm về quota Fargate, xem *AWS Fargate service quotas* (<https://docs.aws.amazon.com/AmazonECS/latest/userguide/service-quotas.html#service-quotas-fargate>) trong *Amazon Elastic Container Service User Guide for AWS Fargate*.

- Với task chạy trên Amazon EC2 instance, số Amazon EC2 instance tối đa bạn có thể đăng ký cho mỗi cluster là **5.000**. Nếu dùng Amazon ECS cluster auto scaling với capacity provider Auto Scaling group, hoặc tự quản lý Amazon EC2 instance cho cluster, quota này có thể trở thành **nút thắt khi deploy**. Nếu cần thêm capacity, bạn có thể tạo thêm cluster hoặc yêu cầu tăng service quota.

- Nếu dùng Amazon ECS cluster auto scaling với capacity provider Auto Scaling group, khi scale service hãy cân nhắc quota **`Tasks in the PROVISIONING state per cluster`**. Quota này là số task tối đa ở trạng thái `PROVISIONING` trong mỗi cluster mà capacity provider có thể tăng capacity cho. Khi bạn khởi chạy một lượng lớn task cùng lúc, bạn dễ chạm quota này. Một ví dụ là khi deploy đồng thời hàng chục service, mỗi service có hàng trăm task. Khi đó, capacity provider cần launch container instance mới để đặt task khi cluster không đủ capacity. Trong lúc capacity provider đang launch thêm Amazon EC2 instance, Amazon ECS service scheduler nhiều khả năng sẽ tiếp tục khởi chạy task song song. Tuy nhiên, hoạt động này có thể bị throttle do cluster không đủ capacity. Amazon ECS service scheduler áp dụng chiến lược **back-off và throttling theo cấp số nhân** để thử lại việc đặt task khi container instance mới được launch. Kết quả là bạn có thể thấy thời gian deploy hoặc scale-out chậm hơn. Để tránh tình huống này, bạn có thể lên kế hoạch deploy service theo một trong các cách sau: hoặc deploy một lượng lớn task **không cần tăng capacity** của cluster, hoặc **giữ sẵn capacity dư** trong cluster cho việc khởi chạy task mới.

Ngoài việc cân nhắc service quota của Amazon ECS khi scale workload, hãy cân nhắc cả service quota của các dịch vụ AWS khác tích hợp với Amazon ECS. Phần sau trình bày chi tiết các giới hạn tốc độ quan trọng của từng dịch vụ, và đưa ra khuyến nghị để xử lý các vấn đề throttling có thể xảy ra.

**💡 Giải thích thêm**

| Quota | Giá trị / phạm vi theo trang | Khi nào thành vấn đề | Cách xử lý theo trang |
|---|---|---|---|
| Fargate — task chạy đồng thời | Mỗi Region; có cho On-Demand và Fargate Spot; tính cả EKS pod trên Fargate | Chạy nhiều task Fargate | Yêu cầu tăng quota |
| EC2 instance mỗi cluster | **5.000** | Cluster auto scaling hoặc tự quản lý instance | Tạo thêm cluster hoặc yêu cầu tăng quota |
| `Tasks in the PROVISIONING state per cluster` | Mỗi cluster | Deploy cùng lúc rất nhiều task (vd hàng chục service × hàng trăm task) cần thêm instance | Deploy lượng task không cần thêm capacity, **hoặc** giữ capacity dư |

Chuỗi sự kiện khi chạm quota `PROVISIONING`, theo trang:

```
Deploy rất nhiều task cùng lúc, cluster thiếu capacity
  → capacity provider launch thêm EC2 instance
  → service scheduler vẫn khởi chạy task song song → bị throttle (thiếu capacity)
  → scheduler retry với back-off theo cấp số nhân
  → deploy / scale-out chậm hơn
```

---

## 2. Elastic Load Balancing

**📘 Theo docs AWS**

Bạn có thể cấu hình Amazon ECS service dùng Elastic Load Balancing để phân phối traffic đều cho các task. Thông tin thêm về cách chọn load balancer, xem *Use load balancing to distribute Amazon ECS service traffic* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/service-load-balancing.html>).

### 2.1 Service quota của Elastic Load Balancing

**📘 Theo docs AWS**

Khi scale workload, hãy cân nhắc các service quota sau của Elastic Load Balancing. Phần lớn service quota của Elastic Load Balancing **có thể điều chỉnh**, và bạn có thể yêu cầu tăng trong Service Quotas console.

**Application Load Balancer**

Khi dùng Application Load Balancer, tuỳ trường hợp sử dụng, bạn có thể cần yêu cầu tăng quota cho:

- Quota **`Targets per Application Load Balancer`** — số target phía sau Application Load Balancer.
- Quota **`Targets per Target Group per Region`** — số target phía sau các Target Group.

Thông tin thêm, xem *Quotas for your Application Load Balancers* (<https://docs.aws.amazon.com/elasticloadbalancing/latest/application/load-balancer-limits.html>).

**Network Load Balancer**

Có giới hạn **chặt hơn** về số target bạn có thể đăng ký với Network Load Balancer. Khi dùng Network Load Balancer, bạn thường muốn bật hỗ trợ **cross-zone**, điều này đi kèm giới hạn scale bổ sung về **`Targets per Availability Zone Per Network Load Balancer`** — số target tối đa trên mỗi Availability Zone cho mỗi Network Load Balancer. Thông tin thêm, xem *Quotas for your Network Load Balancers* (<https://docs.aws.amazon.com/elasticloadbalancing/latest/network/load-balancer-limits.html>).

### 2.2 API throttling của Elastic Load Balancing

**📘 Theo docs AWS**

Khi cấu hình Amazon ECS service dùng load balancer, **health check của target group phải đạt** trước khi service được coi là healthy. Để thực hiện các health check này, Amazon ECS gọi các thao tác API của Elastic Load Balancing thay cho bạn. Nếu account có nhiều service được cấu hình với load balancer, bạn có thể gặp deploy service chậm hơn do có thể bị throttle, cụ thể ở các thao tác API Elastic Load Balancing **`RegisterTarget`**, **`DeregisterTarget`** và **`DescribeTargetHealth`**. Khi bị throttle, lỗi throttling xuất hiện trong **service event message** của Amazon ECS.

Nếu bạn gặp throttling API của AWS Cloud Map, bạn có thể liên hệ Support để được hướng dẫn cách tăng giới hạn throttling API của AWS Cloud Map. Thông tin thêm về giám sát và xử lý các lỗi throttling như vậy, xem *Handling throttling issues* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/operating-at-scale-dealing-with-throttles.html>).

**💡 Giải thích thêm**

| Load balancer | Quota cần để ý (theo trang) |
|---|---|
| Application Load Balancer | `Targets per Application Load Balancer`; `Targets per Target Group per Region` |
| Network Load Balancer | Giới hạn target **chặt hơn**; bật cross-zone thì thêm `Targets per Availability Zone Per Network Load Balancer` |

API ELB dễ bị throttle khi có **nhiều service dùng load balancer**: `RegisterTarget`, `DeregisterTarget`, `DescribeTargetHealth` → deploy chậm, lỗi hiện trong service event.

Đoạn cuối của mục 2.2 trên trang gốc nói về throttling API của **AWS Cloud Map**, dù đang nằm trong mục **Elastic Load Balancing**. Bản dịch giữ nguyên như trang gốc; có vẻ đoạn này được chép từ mục Cloud Map. Đoạn tương ứng ở mục 3.2 và 4.2 nói tới dịch vụ đúng của mục đó.

---

## 3. Elastic network interfaces

**📘 Theo docs AWS**

Khi task dùng network mode `awsvpc`, Amazon ECS cấp một elastic network interface (ENI) **riêng** cho mỗi task. Khi Amazon ECS service dùng load balancer Elastic Load Balancing, các network interface này cũng được đăng ký làm target vào target group tương ứng được định nghĩa trong service.

### 3.1 Service quota của elastic network interface

**📘 Theo docs AWS**

Khi chạy task dùng network mode `awsvpc`, một ENI riêng được gắn vào mỗi task. Nếu các task đó phải truy cập được qua internet, hãy gán địa chỉ IP public cho ENI của các task đó. Khi scale workload Amazon ECS, hãy cân nhắc hai quota quan trọng sau:

- Quota **`Network interfaces per Region`** — số network interface tối đa trong một AWS Region cho account của bạn.
- Quota **`Elastic IP addresses per Region`** — số elastic IP address tối đa trong một AWS Region.

Cả hai service quota này **có thể điều chỉnh** và bạn có thể yêu cầu tăng từ Service Quotas console. Thông tin thêm, xem *Amazon VPC service quotas* (<https://docs.aws.amazon.com/vpc/latest/userguide/amazon-vpc-limits.html#vpc-limits-enis>).

Với workload Amazon ECS chạy trên Amazon EC2 instance, khi chạy task dùng network mode `awsvpc` hãy cân nhắc service quota **`Maximum network interfaces`** — số network interface tối đa cho mỗi Amazon EC2 instance. Quota này giới hạn số task bạn có thể đặt trên một instance. Bạn **không thể** điều chỉnh quota này và nó **không có** trong Service Quotas console. Thông tin thêm, xem *IP addresses per network interface per instance type* (<https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/using-eni.html#AvailableIpPerENI>) trong *Amazon EC2 User Guide*.

Dù không thể thay đổi số network interface gắn được vào một Amazon EC2 instance, bạn có thể dùng tính năng **elastic network interface trunking** để tăng số network interface khả dụng. Ví dụ, mặc định một instance `c5.large` có thể có tối đa **ba** network interface. Network interface chính của instance tính là một. Vì vậy, bạn có thể gắn thêm **hai** network interface vào instance. Vì mỗi task dùng network mode `awsvpc` cần một network interface, thông thường bạn chỉ chạy được **hai** task như vậy trên instance type này. Điều này có thể dẫn tới dùng không hết capacity của cluster. Nếu bật ENI trunking, bạn có thể tăng mật độ network interface để đặt nhiều task hơn trên mỗi instance. Khi bật trunking, một instance `c5.large` có thể có tối đa **12** network interface. Instance có network interface chính, và Amazon ECS tạo và gắn một network interface **"trunk"** vào instance. Kết quả là với cấu hình này, bạn chạy được **10** task trên instance thay vì hai task mặc định. Thông tin thêm, xem *Elastic network interface trunking* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/container-instance-eni.html>).

### 3.2 API throttling của elastic network interface

**📘 Theo docs AWS**

Khi chạy task dùng network mode `awsvpc`, Amazon ECS dựa vào các Amazon EC2 API sau. Mỗi API có giới hạn throttling khác nhau. Thông tin thêm, xem *Request throttling for the Amazon EC2 API* (<https://docs.aws.amazon.com/AWSEC2/latest/APIReference/throttling.html>).

- CreateNetworkInterface
- AttachNetworkInterface
- DetachNetworkInterface
- DeleteNetworkInterface
- DescribeNetworkInterfaces
- DescribeVpcs
- DescribeSubnets
- DescribeSecurityGroups
- DescribeInstances

Nếu các lời gọi Amazon EC2 API bị throttle trong quy trình cấp ENI, Amazon ECS service scheduler **tự động thử lại** với **back-off theo cấp số nhân**. Các lần thử lại tự động này đôi khi gây chậm khởi chạy task, dẫn tới tốc độ deploy chậm hơn. Khi bị throttle API, bạn sẽ thấy thông điệp `Operations are being throttled. Will try again later.` trong service event message. Nếu thường xuyên chạm giới hạn throttling của Amazon EC2 API, bạn có thể liên hệ Support để được hướng dẫn cách tăng giới hạn throttling API. Thông tin thêm về giám sát và xử lý lỗi throttling, xem *Handling throttling issues* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/operating-at-scale-dealing-with-throttles.html>).

**💡 Giải thích thêm**

Quota ENI theo trang:

| Quota | Phạm vi | Điều chỉnh được? |
|---|---|---|
| `Network interfaces per Region` | Mỗi Region, mỗi account | Có (Service Quotas console) |
| `Elastic IP addresses per Region` | Mỗi Region | Có (Service Quotas console) |
| `Maximum network interfaces` | Mỗi EC2 instance (theo instance type) | **Không**, không có trong Service Quotas console |

Phép tính ví dụ c5.large của trang:

| | Không trunking | Có trunking |
|---|---|---|
| Network interface tối đa | 3 | 12 |
| Trừ ENI chính của instance | − 1 | − 1 |
| Trừ ENI "trunk" do ECS tạo | — | − 1 |
| **Task `awsvpc` chạy được** | **2** | **10** |

Khi API EC2 bị throttle lúc cấp ENI: scheduler tự retry (back-off cấp số nhân) → task khởi chạy chậm → thấy thông điệp `Operations are being throttled. Will try again later.` trong service event.

---

## 4. AWS Cloud Map

**📘 Theo docs AWS**

Service discovery của Amazon ECS dùng API của AWS Cloud Map để quản lý namespace cho các Amazon ECS service. Nếu service có lượng lớn task, hãy cân nhắc các khuyến nghị sau. Thông tin thêm, xem *Amazon ECS service discovery considerations* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/service-discovery.html#service-discovery-considerations>).

### 4.1 Service quota của AWS Cloud Map

**📘 Theo docs AWS**

Khi Amazon ECS service được cấu hình dùng service discovery, quota **`Tasks per service`** — số task tối đa cho service — bị ảnh hưởng bởi service quota **`Instances per service`** của AWS Cloud Map — số instance tối đa cho service đó. Cụ thể, service quota của AWS Cloud Map giảm số task bạn có thể chạy xuống **tối đa 1.000 task mỗi service**. Bạn **không thể** thay đổi quota của AWS Cloud Map. Thông tin thêm, xem *AWS Cloud Map service quotas* (<https://docs.aws.amazon.com/general/latest/gr/cloud_map.html>).

### 4.2 API throttling của AWS Cloud Map

**📘 Theo docs AWS**

Amazon ECS gọi các API AWS Cloud Map **`ListInstances`**, **`GetInstancesHealthStatus`**, **`RegisterInstance`** và **`DeregisterInstance`** thay cho bạn. Chúng hỗ trợ service discovery và thực hiện health check khi bạn khởi chạy task. Khi nhiều service dùng service discovery với lượng lớn task được deploy cùng lúc, việc này có thể vượt giới hạn throttling API của AWS Cloud Map. Khi đó, nhiều khả năng bạn sẽ thấy thông điệp sau: `Operations are being throttled. Will try again later` trong service event message của Amazon ECS, cùng tốc độ deploy và khởi chạy task chậm hơn. AWS Cloud Map **không công bố** giới hạn throttling cho các API này. Nếu bị throttle từ các API này, bạn có thể liên hệ Support để được hướng dẫn tăng giới hạn throttling API. Thêm khuyến nghị về giám sát và xử lý các lỗi throttling như vậy, xem *Handling throttling issues* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/operating-at-scale-dealing-with-throttles.html>).

**💡 Giải thích thêm**

| Điểm | Theo trang |
|---|---|
| Giới hạn task khi dùng service discovery | **Tối đa 1.000 task mỗi service** (do quota `Instances per service` của Cloud Map) |
| Điều chỉnh được? | **Không** |
| API ECS gọi thay bạn | `ListInstances`, `GetInstancesHealthStatus`, `RegisterInstance`, `DeregisterInstance` |
| Khi nào bị throttle | Nhiều service dùng service discovery, nhiều task, deploy cùng lúc |
| Dấu hiệu | `Operations are being throttled. Will try again later` trong service event; deploy chậm |
| Giới hạn throttling | Cloud Map **không công bố**; liên hệ Support |

---

## 5. Tổng hợp

**💡 Giải thích thêm**

Tổng hợp từ các mục trên của chính trang:

| Dịch vụ | Quota cần để ý | API dễ bị throttle | Dấu hiệu / cách xử lý theo trang |
|---|---|---|---|
| **Amazon ECS / Fargate** | Task Fargate đồng thời mỗi Region (On-Demand, Spot, tính cả EKS pod); 5.000 EC2 instance mỗi cluster; `Tasks in the PROVISIONING state per cluster` | — | Tăng quota / thêm cluster; deploy không cần thêm capacity hoặc giữ capacity dư |
| **Elastic Load Balancing** | ALB: `Targets per Application Load Balancer`, `Targets per Target Group per Region`; NLB: `Targets per Availability Zone Per Network Load Balancer` | `RegisterTarget`, `DeregisterTarget`, `DescribeTargetHealth` | Lỗi throttling trong service event; deploy chậm |
| **ENI (EC2)** | `Network interfaces per Region`, `Elastic IP addresses per Region` (điều chỉnh được); `Maximum network interfaces` mỗi instance (không điều chỉnh được) | 9 API EC2 về network interface, VPC, subnet, security group, instance | Scheduler tự retry; thông điệp `Operations are being throttled…`; dùng ENI trunking; liên hệ Support |
| **AWS Cloud Map** | Tối đa 1.000 task mỗi service (không điều chỉnh được) | `ListInstances`, `GetInstancesHealthStatus`, `RegisterInstance`, `DeregisterInstance` | Thông điệp `Operations are being throttled…`; liên hệ Support |

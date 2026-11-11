# Architect your solution for Amazon ECS

> Nguồn: **Amazon ECS Developer Guide — Architect your solution for Amazon ECS**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/ecs-configuration.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: **bản dịch** sang tiếng Việt, đi đúng thứ tự và đúng nội dung của trang gốc, không thêm, không bớt ý.
> - Khối **💡 Giải thích thêm**: chỉ giải thích lại những gì **chính trang này** đã nói, cho dễ hiểu hơn. Không đưa thông tin từ trang khác hay ví dụ tự đặt.
> - Tên tính năng, tên dịch vụ, tham số giữ nguyên tiếng Anh.
> - Tài liệu AWS được phát hành theo giấy phép **Creative Commons Attribution-ShareAlike 4.0**; bản dịch này ghi rõ nguồn ở trên.

---

## Mục lục

0. [Phần mở đầu](#0-phần-mở-đầu)
1. [Capacity](#1-capacity)
2. [Service endpoints](#2-service-endpoints)
3. [Networking](#3-networking)
4. [Feature access](#4-feature-access)
5. [IAM roles](#5-iam-roles)
6. [Logging](#6-logging)
7. [Container image best practices](#7-container-image-best-practices)

---

## 0. Phần mở đầu

**📘 Theo docs AWS**

Bạn phải thiết kế ứng dụng sao cho chúng có thể chạy trên *container*. Container là một đơn vị phát triển phần mềm được chuẩn hoá, chứa mọi thứ mà ứng dụng phần mềm của bạn cần để chạy. Bao gồm code liên quan, runtime, công cụ hệ thống và thư viện hệ thống. Container được tạo từ một template chỉ đọc gọi là *image*. Container image là một artifact tĩnh chứa ứng dụng của bạn và các dependency của nó. Image thường được build từ một Dockerfile. Dockerfile là một file văn bản thuần chứa các chỉ dẫn để build container. Sau khi build xong, các image này được lưu trong một *registry* như Amazon ECR, nơi có thể tải chúng về.

Sau khi tạo và lưu image, bạn tạo một Amazon ECS task definition. *Task definition* là bản thiết kế (blueprint) cho ứng dụng của bạn. Đó là một file văn bản định dạng JSON, mô tả các tham số và một hoặc nhiều container tạo nên ứng dụng của bạn. Ví dụ, bạn có thể dùng nó để chỉ định image và các tham số cho hệ điều hành, container nào được dùng, port nào được mở cho ứng dụng, và data volume nào được dùng với các container trong task. Các tham số cụ thể có trong task definition phụ thuộc vào nhu cầu của ứng dụng cụ thể của bạn.

Sau khi định nghĩa task definition, bạn deploy nó dưới dạng service hoặc task trên cluster. *Cluster* là một nhóm logic các task hoặc service, chạy trên hạ tầng capacity đã được đăng ký vào cluster.

*Task* là một thể hiện (instantiation) của task definition bên trong cluster. Bạn có thể chạy một standalone task, hoặc chạy task như một phần của service. Bạn có thể dùng Amazon ECS *service* để chạy và duy trì đồng thời số lượng task mong muốn trong một Amazon ECS cluster. Cách nó hoạt động là: nếu bất kỳ task nào bị lỗi hoặc dừng vì bất kỳ lý do gì, Amazon ECS service scheduler sẽ launch một bản khác dựa trên task definition của bạn. Nó làm vậy để thay thế task đó và qua đó duy trì số lượng task mong muốn trong service.

*Container agent* chạy trên mỗi container instance trong một Amazon ECS cluster. Agent gửi thông tin về các task đang chạy và mức sử dụng tài nguyên của container về cho Amazon ECS. Nó khởi động và dừng task mỗi khi nhận được yêu cầu từ Amazon ECS.

Sau khi deploy task hoặc service, bạn có thể dùng bất kỳ công cụ nào sau đây để giám sát deployment và ứng dụng:

- CloudWatch
- Runtime Monitoring

**💡 Giải thích thêm**

Thứ tự các khái niệm mà đoạn mở đầu đi qua:

```
Dockerfile → image → registry (vd Amazon ECR)
          → task definition (file JSON, bản thiết kế)
          → deploy thành task hoặc service trên cluster
          → giám sát bằng CloudWatch / Runtime Monitoring
```

| Khái niệm | Tóm gọn theo đoạn trên |
|---|---|
| Container | Đơn vị phần mềm chứa code, runtime, công cụ và thư viện hệ thống |
| Image | Template chỉ đọc để tạo container, thường build từ Dockerfile |
| Registry | Nơi lưu image để tải về |
| Task definition | File JSON mô tả container, image, port, volume… |
| Cluster | Nhóm logic các task/service, chạy trên capacity đã đăng ký |
| Task | Một thể hiện của task definition |
| Service | Giữ đúng số task mong muốn; task hỏng thì scheduler tạo task thay thế |
| Container agent | Chạy trên container instance, báo trạng thái về ECS, chạy/dừng task theo yêu cầu |

---

## 1. Capacity

**📘 Theo docs AWS**

Capacity là hạ tầng nơi container của bạn chạy. Amazon ECS cung cấp ba loại hạ tầng cho cluster:

- **Amazon ECS Managed Instances** – AWS quản lý hoàn toàn các Amazon EC2 instance bên dưới chạy trong account của bạn, bao gồm cấp phát, vá lỗi và scale. Lựa chọn này mang lại sự cân bằng tối ưu giữa hiệu năng, hiệu quả chi phí và sự đơn giản trong vận hành.
- **Serverless (Fargate)** – Chỉ trả tiền cho tài nguyên mà task của bạn dùng, không phải quản lý bất kỳ hạ tầng nào. Lý tưởng cho workload biến động và để bắt đầu nhanh.
- **Amazon EC2 instances** – Bạn trực tiếp quản lý các Amazon EC2 instance bên dưới, bao gồm việc chọn instance, cấu hình và bảo trì.

Dùng Amazon ECS Managed Instances khi:

- Bạn muốn sự đơn giản của Fargate nhưng có nhiều quyền kiểm soát hơn đối với hạ tầng bên dưới.
- Bạn cần hiệu năng có thể dự đoán được và tối ưu chi phí.
- Bạn muốn AWS lo việc quản lý hạ tầng mà vẫn giữ được sự linh hoạt.

Dùng Fargate khi:

- Bạn muốn tập trung vào ứng dụng mà không phải quản lý hạ tầng.
- Bạn có workload biến động hoặc khó dự đoán.
- Bạn mới bắt đầu với container và muốn lựa chọn deploy đơn giản nhất.

Dùng Amazon EC2 instances khi:

- Bạn cần phần cứng chuyên dụng (tăng tốc GPU, tính toán hiệu năng cao).
- Bạn cần capacity reservation hoặc instance type cụ thể.
- Bạn cần privileged capability hoặc custom AMI.

Bạn chỉ định hạ tầng khi tạo cluster. Bạn cũng chỉ định loại hạ tầng khi đăng ký task definition. Task definition gọi hạ tầng là "launch type". Bạn cũng có thể dùng capacity provider.

**💡 Giải thích thêm**

| | Managed Instances | Fargate | EC2 instances |
|---|---|---|---|
| Ai quản lý instance | AWS | Không có instance để quản lý | Bạn |
| Tính tiền | (trang không nói) | Theo tài nguyên task dùng | (trang không nói) |
| Dùng khi | Muốn đơn giản như Fargate nhưng kiểm soát hơn; cần hiệu năng dự đoán được, tối ưu chi phí | Không muốn quản lý hạ tầng; workload biến động; mới bắt đầu | Cần GPU/HPC, capacity reservation, instance type cụ thể, privileged capability, custom AMI |

Có ba nơi khai báo hạ tầng theo đoạn cuối: khi **tạo cluster**, trong **task definition** (gọi là "launch type"), hoặc qua **capacity provider**.

---

## 2. Service endpoints

**📘 Theo docs AWS**

Service endpoint là URL của điểm vào Amazon ECS mà bạn dùng để kết nối tới dịch vụ bằng lập trình, qua Internet Protocol version 4 (IPv4) hoặc Internet Protocol version 6 (IPv6). Mặc định, các request bạn gửi để kết nối tới Amazon ECS bằng lập trình dùng service endpoint chỉ hỗ trợ request IPv4. Để kết nối bằng lập trình tới Amazon ECS với request IPv4 hoặc IPv6, bạn có thể dùng dual-stack endpoint. Thông tin về cách dùng dual-stack endpoint, xem *Using Amazon ECS dual-stack endpoints* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/dual-stack-endpoint.html>).

**💡 Giải thích thêm**

| Loại endpoint | Hỗ trợ |
|---|---|
| Mặc định | Chỉ IPv4 |
| Dual-stack | IPv4 hoặc IPv6 |

"Kết nối bằng lập trình" ở đây là gọi ECS qua API (vd từ CLI, SDK), không phải traffic của ứng dụng chạy trong container.

---

## 3. Networking

**📘 Theo docs AWS**

Tài nguyên AWS được tạo trong subnet. Khi bạn dùng EC2 instance, Amazon ECS launch các instance trong subnet mà bạn chỉ định khi tạo cluster. Task của bạn chạy trong subnet của instance. Với Fargate hoặc máy ảo on-premises, bạn chỉ định subnet khi chạy task hoặc tạo service.

Tuỳ vào ứng dụng, subnet có thể là private hoặc public, và subnet có thể nằm ở bất kỳ tài nguyên AWS nào sau đây:

- Availability Zones
- Local Zones
- Wavelength Zones
- AWS Regions
- AWS Outposts

Thông tin thêm, xem *Amazon ECS applications in shared subnets, Local Zones, and Wavelength Zones* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/cluster-regions-zones.html>) hoặc *Amazon Elastic Container Service on AWS Outposts* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/using-outposts.html>).

Bạn có thể cho ứng dụng kết nối internet bằng một trong các cách sau:

- **Public subnet với internet gateway**

  Dùng public subnet khi bạn có ứng dụng public cần lượng băng thông lớn hoặc độ trễ tối thiểu. Các trường hợp áp dụng gồm dịch vụ streaming video và game.

- **Private subnet với NAT gateway**

  Dùng private subnet khi bạn muốn bảo vệ container khỏi truy cập trực tiếp từ bên ngoài. Các trường hợp áp dụng gồm hệ thống xử lý thanh toán hoặc container lưu dữ liệu và mật khẩu người dùng.

- **AWS PrivateLink**

  Dùng AWS PrivateLink để có kết nối riêng tư giữa các VPC, dịch vụ AWS và mạng on-premises của bạn mà không để traffic đi ra internet công cộng.

**💡 Giải thích thêm**

Subnet được chọn ở đâu, theo đoạn đầu:

| Hạ tầng | Chọn subnet khi |
|---|---|
| EC2 instances | Tạo cluster (task chạy trong subnet của instance) |
| Fargate, máy ảo on-premises | Chạy task hoặc tạo service |

Ba cách ra internet:

| Cách | Ưu tiên | Ví dụ trong docs |
|---|---|---|
| Public subnet + internet gateway | Băng thông lớn, độ trễ thấp | Streaming video, game |
| Private subnet + NAT gateway | Không cho bên ngoài truy cập trực tiếp vào container | Xử lý thanh toán, lưu dữ liệu/mật khẩu người dùng |
| AWS PrivateLink | Kết nối riêng, không đi qua internet công cộng | — |

---

## 4. Feature access

**📘 Theo docs AWS**

Bạn có thể dùng account settings của Amazon ECS để truy cập các tính năng sau:

- **Container Insights**

  CloudWatch Container Insights thu thập, tổng hợp và tóm tắt metric và log từ các ứng dụng container và microservice của bạn. Các metric gồm mức sử dụng tài nguyên như CPU, memory, đĩa và mạng.

- **`awsvpc` trunking**

  Với một số EC2 instance type, bạn có thể có thêm network interface (ENI) trên các container instance mới launch.

- **Tagging authorization**

  Người dùng phải có quyền cho các hành động tạo tài nguyên, chẳng hạn `ecsCreateCluster`. Nếu có chỉ định tag trong hành động tạo tài nguyên, AWS thực hiện thêm bước kiểm tra quyền trên hành động `ecs:TagResource` để xác minh người dùng hoặc role có quyền tạo tag hay không.

- **Fargate FIPS-140 compliance**

  Fargate hỗ trợ Federal Information Processing Standard (FIPS-140), tiêu chuẩn quy định các yêu cầu bảo mật cho module mật mã bảo vệ thông tin nhạy cảm. Đây là tiêu chuẩn hiện hành của chính phủ Hoa Kỳ và Canada, áp dụng cho các hệ thống bắt buộc phải tuân thủ Federal Information Security Management Act (FISMA) hoặc Federal Risk and Authorization Management Program (FedRAMP).

- **Fargate task retirement time changes**

  Bạn có thể cấu hình khoảng thời gian chờ trước khi task Fargate bị retire để vá.

- **Dual stack VPC**

  Cho phép task giao tiếp qua IPv4, IPv6, hoặc cả hai.

- **Amazon Resource Name (ARN) format**

  Một số tính năng, chẳng hạn tagging authorization, yêu cầu định dạng Amazon Resource Name (ARN) mới.

Thông tin thêm, xem *Access Amazon ECS features with account settings* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/ecs-account-settings.html>).

**💡 Giải thích thêm**

| Account setting | Dùng để |
|---|---|
| Container Insights | Thu metric và log (CPU, memory, đĩa, mạng) |
| `awsvpc` trunking | Có thêm ENI trên một số EC2 instance type |
| Tagging authorization | Kiểm tra thêm quyền `ecs:TagResource` khi tạo tài nguyên có kèm tag |
| Fargate FIPS-140 compliance | Đáp ứng chuẩn mật mã FIPS-140 (cho hệ thống cần FISMA, FedRAMP) |
| Fargate task retirement time changes | Chỉnh thời gian chờ trước khi task Fargate bị retire để vá |
| Dual stack VPC | Task dùng IPv4, IPv6 hoặc cả hai |
| ARN format | Định dạng ARN mới, cần cho một số tính năng như tagging authorization |

Trên trang gốc, tên hành động tạo cluster được viết là `ecsCreateCluster`; trong IAM, tên hành động này thường viết là `ecs:CreateCluster`.

---

## 5. IAM roles

**📘 Theo docs AWS**

IAM role là một danh tính IAM mà bạn có thể tạo trong account, có các quyền cụ thể. Trong Amazon ECS, bạn có thể tạo role để cấp quyền cho tài nguyên Amazon ECS như container hoặc service.

Một số tính năng của Amazon ECS yêu cầu role. Thông tin thêm, xem *IAM roles for Amazon ECS* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/ecs-iam-role-overview.html>).

**💡 Giải thích thêm**

Ý chính: role là cách cấp quyền cho **container** hoặc **service** của ECS, và có những tính năng **không dùng được** nếu thiếu role. Danh sách role cụ thể nằm ở trang *IAM roles for Amazon ECS* được dẫn link.

---

## 6. Logging

**📘 Theo docs AWS**

Logging và giám sát là những khía cạnh quan trọng để duy trì độ tin cậy, tính sẵn sàng và hiệu năng của workload Amazon ECS. Có các lựa chọn sau:

- **Amazon CloudWatch logs** – định tuyến log về Amazon CloudWatch.
- **FireLens for Amazon ECS** – định tuyến log tới một dịch vụ AWS hoặc đích thuộc AWS Partner Network để lưu trữ và phân tích log. AWS Partner Network là cộng đồng đối tác toàn cầu, tận dụng các chương trình, chuyên môn và nguồn lực để xây dựng, tiếp thị và bán các sản phẩm cho khách hàng.

**💡 Giải thích thêm**

| Lựa chọn | Log đi đâu |
|---|---|
| Amazon CloudWatch logs | Amazon CloudWatch |
| FireLens for Amazon ECS | Một dịch vụ AWS hoặc đích của AWS Partner Network |

---

## 7. Container image best practices

**📘 Theo docs AWS**

Sau đây là các nguyên tắc chính cho container image của Amazon ECS:

- Đưa tất cả dependency vào image
- Chạy một tiến trình trên mỗi container
- Xử lý `SIGTERM` để tắt êm (graceful shutdown)
- Ghi log ra `stdout` và `stderr`
- Dùng tag duy nhất, tránh `latest` ở production

**💡 Giải thích thêm**

| Nguyên tắc | Nghĩa là |
|---|---|
| Đưa tất cả dependency vào image | Image tự đủ để chạy, không phụ thuộc thứ cài thêm bên ngoài |
| Một tiến trình mỗi container | Mỗi container chỉ làm một việc chính |
| Xử lý `SIGTERM` | Ứng dụng bắt tín hiệu `SIGTERM` để kết thúc gọn gàng trước khi bị dừng |
| Log ra `stdout`/`stderr` | Không ghi log vào file trong container |
| Tag duy nhất, tránh `latest` ở production | Mỗi bản build có tag riêng, biết chính xác phiên bản nào đang chạy |

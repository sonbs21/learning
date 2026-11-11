# Task retirement and maintenance for AWS Fargate on Amazon ECS

> Nguồn: **Amazon ECS Developer Guide — Task retirement and maintenance for AWS Fargate on Amazon ECS**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task-maintenance.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: **bản dịch** sang tiếng Việt, đi đúng thứ tự và đúng nội dung của trang gốc, không thêm, không bớt ý. Event mẫu giữ nguyên như trên docs.
> - Khối **💡 Giải thích thêm**: chỉ giải thích lại những gì **chính trang này** đã nói, cho dễ hiểu hơn. Không đưa thông tin từ trang khác hay ví dụ tự đặt.
> - Tên tham số, trạng thái, event type giữ nguyên tiếng Anh.
> - Tài liệu AWS được phát hành theo giấy phép **Creative Commons Attribution-ShareAlike 4.0**; bản dịch này ghi rõ nguồn ở trên.

---

## Mục lục

0. [Phần mở đầu](#0-phần-mở-đầu)
1. [Tổng quan về thông báo task retirement](#1-tổng-quan-về-thông-báo-task-retirement)
2. [Trạng thái tài nguyên bị ảnh hưởng trong thông báo](#2-trạng-thái-tài-nguyên-bị-ảnh-hưởng-trong-thông-báo)
3. [Có thể từ chối task retirement không?](#3-có-thể-từ-chối-task-retirement-không)
4. [Có nhận thông báo qua dịch vụ AWS khác được không?](#4-có-nhận-thông-báo-qua-dịch-vụ-aws-khác-được-không)
5. [Có đổi được task retirement sau khi đã lên lịch không?](#5-có-đổi-được-task-retirement-sau-khi-đã-lên-lịch-không)
6. [Amazon ECS xử lý task thuộc service thế nào?](#6-amazon-ecs-xử-lý-task-thuộc-service-thế-nào)
7. [Amazon ECS có tự xử lý standalone task không?](#7-amazon-ecs-có-tự-xử-lý-standalone-task-không)
8. [Xử lý sự cố tính sẵn sàng của service khi task retirement](#8-xử-lý-sự-cố-tính-sẵn-sàng-của-service-khi-task-retirement)
9. [Ghi chú về trang gốc](#9-ghi-chú-về-trang-gốc)

---

## 0. Phần mở đầu

**📘 Theo docs AWS**

AWS chịu trách nhiệm **bảo trì hạ tầng bên dưới** của AWS Fargate. AWS quyết định khi nào một **platform version revision** cần được thay bằng revision mới cho hạ tầng. Việc này gọi là **task retirement**. AWS gửi **thông báo task retirement** khi một platform version revision bị retire. Chúng tôi thường xuyên cập nhật các platform version được hỗ trợ để giới thiệu revision mới chứa các cập nhật cho phần mềm runtime của Fargate và các dependency bên dưới như hệ điều hành và container runtime. Sau khi có revision mới hơn, chúng tôi retire revision cũ để đảm bảo mọi workload của khách hàng chạy trên revision **mới nhất** của Fargate platform version. Khi một revision bị retire, **mọi task đang chạy trên revision đó bị dừng**.

Task Amazon ECS có thể chia thành **service task** hoặc **standalone task**. Service task được deploy như một phần của service và được Amazon ECS scheduler điều khiển. Thông tin thêm, xem *Amazon ECS services* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/ecs_services.html>). Standalone task là task được khởi chạy bởi API **`RunTask`** của Amazon ECS, trực tiếp hoặc bởi một scheduler bên ngoài như scheduled task (do Amazon EventBridge khởi chạy), AWS Batch, hoặc AWS Step Functions. Bạn **không cần làm gì** để phản hồi task retirement với **service task**, vì Amazon ECS scheduler **tự động thay** các task đó.

Với **standalone task**, bạn có thể cần xử lý thêm để phản hồi task retirement. Thông tin thêm, xem *Can Amazon ECS automatically handle standalone tasks?* (mục 7).

Với service task, bạn không cần làm gì với task retirement trừ khi muốn **thay các task này trước khi AWS làm**. Khi Amazon ECS scheduler dừng task, nó dùng **`maximumPercent`** và khởi chạy task mới nhằm duy trì desired count của service. Hãy làm theo best practice để giảm thiểu tác động của task retirement. Giá trị `maximumPercent` mặc định cho service dùng REPLICA service scheduler là **200%**. Vì vậy, khi AWS Fargate bắt đầu retire task, Amazon ECS **trước tiên lên lịch một task mới**, chờ nó chạy, **rồi mới** retire một task cũ. Khi bạn đặt `maximumPercent` là **100%**, Amazon ECS **dừng task trước**, rồi mới thay.

Với retirement của **standalone task**, AWS dừng task **vào hoặc sau ngày** task retirement. Amazon ECS **không khởi chạy task thay thế** khi một task bị dừng. Nếu cần các task này tiếp tục chạy, bạn cần dừng task đang chạy và khởi chạy task thay thế **trước thời điểm** nêu trong thông báo. Vì vậy, chúng tôi khuyến nghị khách hàng **giám sát trạng thái** standalone task và nếu cần, triển khai logic để **thay** các task bị dừng.

Khi một task bị dừng trong bất kỳ kịch bản nào, bạn có thể chạy `describe-tasks`. **`stoppedReason`** trong response là `ECS is performing maintenance on the underlying infrastructure hosting the task`.

Task maintenance áp dụng khi có một platform version revision cần được thay bằng revision mới. Nếu có vấn đề với **host Fargate bên dưới**, Amazon ECS **thay host mà không** gửi thông báo task retirement.

**💡 Giải thích thêm**

| | Service task | Standalone task |
|---|---|---|
| Khởi chạy bởi | Service (ECS scheduler) | `RunTask` (trực tiếp, EventBridge, AWS Batch, Step Functions) |
| Khi bị retire | ECS **tự thay** | ECS **không** thay |
| Bạn cần làm | Không, trừ khi muốn thay sớm | Thay **trước** ngày retirement; giám sát và tự viết logic thay thế |

`maximumPercent` quyết định thứ tự thay (theo đoạn mở đầu):

| `maximumPercent` | Thứ tự |
|---|---|
| **200%** (mặc định REPLICA) | Chạy task mới **trước** → chờ chạy → dừng task cũ |
| **100%** | Dừng task cũ **trước** → chạy task mới |

| Tình huống | Có thông báo task retirement? |
|---|---|
| Platform version revision cần thay | ✅ |
| Host Fargate bên dưới gặp vấn đề | ❌ — ECS thay host không báo trước |

Nhận biết task bị dừng do retirement: `describe-tasks` → `stoppedReason` = `ECS is performing maintenance on the underlying infrastructure hosting the task`.

---

## 1. Tổng quan về thông báo task retirement

**📘 Theo docs AWS**

Khi AWS đánh dấu một platform version revision cần retire, chúng tôi xác định **mọi task** đang chạy trên revision đó ở **mọi Region**.

Hình sau cho thấy vòng đời của một Fargate platform version revision, từ lúc ra mắt revision mới tới lúc retire revision.

*(Hình trên trang gốc: sơ đồ vòng đời task retirement của Fargate.)*

Thông tin chi tiết như sau.

- Sau khi một platform version revision mới ra mắt, **mọi task mới** được lên lịch trên revision này.
- Task **hiện có** đã được lên lịch và đang chạy **ở lại** revision ban đầu trong suốt thời gian của task và **không được di chuyển** sang revision mới.
- Task mới, ví dụ trong lần cập nhật service hoặc do Fargate task retirement, được đặt lên **platform version revision mới nhất** có tại thời điểm khởi chạy.

Thông báo task retirement được gửi qua **AWS Health Dashboard** cũng như qua **email** tới địa chỉ email đã đăng ký, và gồm thông tin sau:

- **Ngày task retirement** - Task bị dừng vào hoặc sau ngày này.
- Với **standalone task**: ID của các task.
- Với **service task**: ID của cluster nơi service chạy và ID của các service.
- Các bước tiếp theo bạn cần làm.

Thông thường, chúng tôi gửi **một thông báo cho mỗi loại** (service task và standalone task) ở mỗi AWS Region. Tuy nhiên, trong một số trường hợp, bạn có thể nhận **nhiều hơn một** event cho mỗi loại task, ví dụ khi có quá nhiều task cần retire vượt giới hạn của cơ chế thông báo.

Bạn có thể nhận diện task được lên lịch retire theo các cách sau:

- **Health Dashboard**

  Thông báo AWS Health có thể được gửi qua **Amazon EventBridge** tới nơi lưu trữ như Amazon Simple Storage Service, thực hiện hành động tự động như chạy AWS Lambda function, hoặc các hệ thống thông báo khác như Amazon Simple Notification Service. Thông tin thêm, xem *Monitoring AWS Health events with Amazon EventBridge* (<https://docs.aws.amazon.com/health/latest/ug/cloudwatch-events-health.html>). Cấu hình mẫu để gửi thông báo tới Amazon Chime, Slack hoặc Microsoft Teams, xem repository *AWS Health Aware* (<https://github.com/aws-samples/aws-health-aware>) trên GitHub.

  Sau đây là một event EventBridge mẫu.

  ```
  {
      "version": "0",
      "id": "3c268027-f43c-0171-7425-1d799EXAMPLE",
      "detail-type": "AWS Health Event",
      "source": "aws.health",
      "account": "123456789012",
      "time": "2023-08-16T23:18:51Z",
      "region": "us-east-1",
      "resources": [
          "cluster|service",
          "cluster|service"
      ],
      "detail": {
          "eventArn": "arn:aws:health:us-east-1::event/ECS/AWS_ECS_TASK_PATCHING_RETIREMENT/AWS_ECS_TASK_PATCHING_RETIREMENT_test1",
          "service": "ECS",
          "eventScopeCode": "ACCOUNT_SPECIFIC",
          "communicationId": "7988399e2e6fb0b905ddc88e0e2de1fd17e4c9fa60349577446d95a18EXAMPLE",
          "lastUpdatedTime": "Wed, 16 Aug 2023 23:18:52 GMT",
          "eventRegion": "us-east-1",
          "eventTypeCode": "AWS_ECS_TASK_PATCHING_RETIREMENT",
          "eventTypeCategory": "scheduledChange",
          "startTime": "Wed, 16 Aug 2023 23:18:51 GMT",
          "endTime": "Fri, 18 Aug 2023 23:18:51 GMT",
          "eventDescription": [
              {
                  "language": "en_US",
                  "latestDescription": "\\nA software update has been deployed to Fargate which includes CVE patches or other critical patches. No action is required on your part. All new tasks launched automatically uses the latest software version. For existing tasks, your tasks need to be restarted in order for these updates to apply. Your tasks running as part of the following ECS Services will be automatically updated beginning Wed, 16 Aug 2023 23:18:51 GMT.\\n\\nAfter Wed, 16 Aug 2023 23:18:51 GMT, the ECS scheduler will gradually replace these tasks, respecting the deployment settings for your service. Typically, services should see little to no interruption during the update and no action is required. When AWS stops tasks, AWS uses the minimum healthy percent (1) and launches a new task in an attempt to maintain the desired count for the service. By default, the minimum healthy percent of a service is 100 percent, so a new task is started first before a task is stopped. Service tasks are routinely replaced in the same way when you scale the service or deploy configuration changes or deploy task definition revisions. If you would like to control the timing of this restart you can update the service before Wed, 16 Aug 2023 23:18:51 GMT, by running the update-service command from the ECS command-line interface specifying force-new-deployment for services using Rolling update deployment type. For example:\\n\\n$ aws ecs update-service -service service_name \\\n--cluster cluster_name -force-new-deployment\\n\\nFor services using Blue/Green deployment type with AWS CodeDeploy:\\nPlease refer to create-deployment document (2) and create new deployment using same task definition revision.\\n\\nFor further details on ECS deployment types, please refer to ECS Deployment Developer Guide (1).\\nFor further details on Fargate's update process, please refer to the AWS Fargate User Guide (3).\\nIf you have any questions or concerns, please contact AWS Support (4).\\n\\n(1) https://docs.aws.amazon.com/AmazonECS/latest/developerguide/deployment-types.html\\n(2) https://docs.aws.amazon.com/cli/latest/reference/deploy/create-deployment.html\\n(3) https://docs.aws.amazon.com/AmazonECS/latest/userguide/task-maintenance.html\\n(4) https://aws.amazon.com/support\\n\\nA list of your affected resources(s) can be found in the 'Affected resources' tab in the 'Cluster/ Service' format in the AWS Health Dashboard. \\n\\n"
              }
          ],
        "affectedEntities": [
                  {
                      "entityValue": "arn:aws:ecs:eu-west-1:111222333444:task/examplecluster/00805ce1d81940b5a37398e5a2c23333"
                  },
                  {
                      "entityValue": "arn:aws:ecs:eu-west-1:111222333444:task/examplecluster/00805ce1d81940b5a37398e5a2c25555"
                  }
              }
          ]
      }
  }
  ```

- **Email**

  Một email được gửi tới email đã đăng ký cho AWS account ID.

Thông tin về cách chuẩn bị cho task retirement, xem *Prepare for AWS Fargate task retirement on Amazon ECS* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/prepare-task-retirement.html>).

**💡 Giải thích thêm**

Vòng đời revision theo trang:

```
Revision mới ra mắt ─► mọi task MỚI chạy trên revision mới
Task CŨ đang chạy   ─► ở lại revision cũ suốt vòng đời (không tự di chuyển)
Revision cũ bị retire ─► thông báo ─► tới ngày retirement: task cũ bị dừng
                                    ─► task thay thế lên revision mới nhất
```

Nội dung thông báo:

| Mục | Service task | Standalone task |
|---|---|---|
| Ngày retirement | ✅ | ✅ |
| Định danh | ID cluster + ID service | ID task |
| Bước tiếp theo | ✅ | ✅ |

Kênh nhận thông báo:

| Kênh | Dùng thêm được |
|---|---|
| AWS Health Dashboard | Qua EventBridge → S3, Lambda, SNS; mẫu cho Chime/Slack/Teams (AWS Health Aware) |
| Email | Email đã đăng ký cho account |

Các trường chính trong event mẫu:

| Trường | Giá trị trong ví dụ |
|---|---|
| `detail-type` | `AWS Health Event` |
| `source` | `aws.health` |
| `detail.service` | `ECS` |
| `detail.eventTypeCode` | `AWS_ECS_TASK_PATCHING_RETIREMENT` |
| `detail.eventTypeCategory` | `scheduledChange` |
| `detail.startTime` / `endTime` | Khoảng thời gian của event |
| `detail.affectedEntities[].entityValue` | ARN task bị ảnh hưởng |

---

## 2. Trạng thái tài nguyên bị ảnh hưởng trong thông báo

**📘 Theo docs AWS**

Mỗi tài nguyên bị ảnh hưởng liệt kê trong thông báo task retirement có một **trạng thái** phản ánh task đó còn đang chờ retire hay không. Trong khi retirement của một task đang chờ, trạng thái của nó là **Impaired**. Điều này cho biết task vẫn đang chạy trên platform version revision đã được lên lịch retire.

Sau khi task ngừng chạy, trạng thái **tự động** chuyển thành **Resolved**, thường **trong vòng 24 giờ**. Điều này áp dụng dù AWS retire task như một phần của task maintenance, hay **bạn tự dừng hoặc thay** task trước lịch retirement. Sau khi **mọi** tài nguyên bị ảnh hưởng trong một thông báo đều **Resolved**, AWS **đóng** thông báo retirement.

**💡 Giải thích thêm**

```
Impaired  (task còn chạy trên revision sắp retire)
   │  task dừng (do AWS retire HOẶC bạn tự dừng/thay)
   ▼
Resolved  (tự động, thường trong 24 giờ)
   │  mọi tài nguyên trong thông báo đều Resolved
   ▼
Thông báo được đóng
```

---

## 3. Có thể từ chối task retirement không?

**📘 Theo docs AWS**

**Không.** Theo **mô hình trách nhiệm chung** của AWS, AWS chịu trách nhiệm quản lý và bảo trì hạ tầng bên dưới của AWS Fargate. Bao gồm việc thực hiện cập nhật nền tảng định kỳ để đảm bảo bảo mật và ổn định. Các cập nhật này được AWS tự động áp dụng và khách hàng **không thể từ chối**. Đây là lợi ích chính của AWS Fargate so với chạy workload trên EC2 instance: trách nhiệm bảo trì nền tảng bên dưới do AWS lo. Mô hình này cho phép bạn tập trung vào ứng dụng thay vì bảo trì hạ tầng. Bằng cách tự động áp dụng các cập nhật nền tảng, AWS giữ môi trường Fargate luôn cập nhật và an toàn, không cần khách hàng làm gì. Điều này giúp cung cấp một môi trường container tin cậy và an toàn để chạy workload trên Fargate.

---

## 4. Có nhận thông báo qua dịch vụ AWS khác được không?

**📘 Theo docs AWS**

AWS gửi thông báo task retirement tới **Health Dashboard** và tới **email liên hệ chính** của AWS account. Health Dashboard cung cấp nhiều tích hợp với các dịch vụ AWS khác, gồm **EventBridge**. Bạn có thể dùng EventBridge để tự động hoá việc hiển thị thông báo (ví dụ chuyển tiếp thông điệp tới công cụ **ChatOps**). Thông tin thêm, xem *Solution overview: Capturing task retirement notifications* (<https://aws.amazon.com/blogs/containers/improving-operational-visibility-with-aws-fargate-task-retirement-notifications/>).

---

## 5. Có đổi được task retirement sau khi đã lên lịch không?

**📘 Theo docs AWS**

**Không.** Lịch dựa trên **task retirement wait time**, mặc định là **7 ngày**. Nếu cần thêm thời gian, bạn có thể chọn cấu hình thời gian chờ là **14 ngày**. Thông tin thêm, xem *Step 2: Capture task retirement notifications to alert teams and take actions* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/prepare-task-retirement.html#prepare-task-retirement-capture-task-events>).

Từ ngày **18/12/2025**, Amazon ECS cho phép bạn cấu hình **Amazon EC2 event windows** (<https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/event-windows.html>) cho task Fargate. Nếu cần kiểm soát **chính xác thời điểm** task retirement, ví dụ lên lịch vào **cuối tuần** để tránh gián đoạn trong giờ làm việc, bạn có thể cấu hình Amazon EC2 event windows cho task, service hoặc cluster, xem *Step 1: Set the task wait time or use Amazon EC2 event windows* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/prepare-task-retirement.html#prepare-task-retirement-set-time>). Lưu ý thay đổi cấu hình này áp dụng cho các retirement sẽ được lên lịch **trong tương lai**. Các retirement **đã lên lịch** hiện tại **không bị ảnh hưởng**. Hơn nữa, khi bạn cấu hình Amazon EC2 event window cho task Fargate, nó **được ưu tiên hơn** cấu hình task retirement wait time. Nếu có thêm thắc mắc, liên hệ Support.

**💡 Giải thích thêm**

| Cách kiểm soát thời điểm | Theo trang |
|---|---|
| Task retirement wait time | Mặc định **7 ngày**, chỉnh được thành **14 ngày** |
| Amazon EC2 event windows (từ 18/12/2025) | Chọn **chính xác** khung giờ (vd cuối tuần); đặt cho task, service hoặc cluster; **ưu tiên hơn** wait time |
| Retirement đã lên lịch | **Không đổi** được — cấu hình mới chỉ áp cho retirement tương lai |

---

## 6. Amazon ECS xử lý task thuộc service thế nào?

**📘 Theo docs AWS**

Với service task, bạn không cần làm gì để phản hồi task retirement trừ khi muốn thay các task này trước khi AWS làm. Khi Amazon ECS scheduler dừng task, nó dùng **minimum healthy percent** và khởi chạy task mới nhằm duy trì desired count của service. Để giảm thiểu tác động của Fargate task retirement, workload nên được deploy theo best practice của Amazon ECS. Ví dụ, khi deploy một ứng dụng stateless dưới dạng Amazon ECS service, như web hoặc API server, khách hàng nên deploy **nhiều task replica** và đặt **minimumHealthyPercent là 100%**. Mặc định, minimum healthy percent của một service là **100 phần trăm**. Vì vậy, khi Fargate bắt đầu retire task, Amazon ECS **trước tiên lên lịch một task mới** và chờ nó chạy, **rồi mới** retire một task cũ. Service task được thay định kỳ như một phần của task retirement **giống cách** khi bạn scale service, deploy thay đổi cấu hình, hoặc deploy task definition revision. Để chuẩn bị cho quá trình task retirement, xem *Prepare for AWS Fargate task retirement on Amazon ECS* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/prepare-task-retirement.html>).

**💡 Giải thích thêm**

Best practice theo trang cho service stateless (web, API server):

| Cấu hình | Lý do |
|---|---|
| Nhiều task replica | Không phụ thuộc một task |
| `minimumHealthyPercent` = 100% (mặc định) | Task mới chạy **trước**, task cũ dừng **sau** |

Task retirement thay task **giống như** khi scale service, deploy thay đổi cấu hình, hoặc deploy task definition revision mới.

---

## 7. Amazon ECS có tự xử lý standalone task không?

**📘 Theo docs AWS**

**Không.** AWS **không thể** tạo task thay thế cho standalone task được khởi chạy bởi `RunTask`, scheduled task (ví dụ qua EventBridge Scheduler), AWS Batch, hoặc AWS Step Functions. Amazon ECS **chỉ** quản lý task thuộc service.

---

## 8. Xử lý sự cố tính sẵn sàng của service khi task retirement

**📘 Theo docs AWS**

Nếu Amazon ECS **không khởi chạy được task thay thế** trong lúc task retirement, tính sẵn sàng của service có thể bị ảnh hưởng. Điều này có thể xảy ra do cấu hình sai từ phía khách hàng, như:

- IAM role thiếu hoặc cấu hình sai
- Không đủ capacity trong subnet đích
- Security group cấu hình sai
- Lỗi task definition

Khi Amazon ECS không khởi chạy được task thay thế, các task bị retire sẽ bị **dừng mà không có task thay thế**, làm **giảm capacity** khả dụng của service và có thể gây **gián đoạn service**. Hãy giám sát **số task** của service và **Amazon CloudWatch metric** để đảm bảo task thay thế được khởi chạy thành công trong các sự kiện retirement.

**💡 Giải thích thêm**

```
Retirement ─► ECS cố chạy task thay thế
               ├─ thành công → service giữ capacity
               └─ thất bại (IAM, subnet thiếu capacity, security group, task definition)
                    → task cũ vẫn bị dừng, KHÔNG có task thay → service giảm capacity
```

| Nguyên nhân task thay thế không chạy được (theo trang) |
|---|
| IAM role thiếu hoặc sai |
| Subnet đích không đủ capacity |
| Security group sai |
| Task definition lỗi |

Cách phòng theo trang: giám sát **số task** của service và **CloudWatch metric** trong lúc retirement.

---

## 9. Ghi chú về trang gốc

**💡 Giải thích thêm**

| Chỗ | Ghi chú |
|---|---|
| Phần mở đầu và mục 6 | Phần mở đầu nói scheduler dùng **`maximumPercent`** (mặc định 200% → chạy task mới trước); mục 6 nói dùng **minimum healthy percent** (mặc định 100% → chạy task mới trước). Cả hai cùng dẫn tới "chạy task mới trước, dừng task cũ sau", nhưng nêu tham số khác nhau |
| Event mẫu, phần `affectedEntities` | Cặp ngoặc đóng bị đảo (`}` rồi mới `]`), nên không phải JSON hợp lệ |
| Event mẫu, `resources` và `affectedEntities` | `resources` ghi dạng `cluster|service`, còn `affectedEntities` liệt kê **ARN task** |
| Event mẫu, lệnh trong `latestDescription` | Viết `-service`, `-force-new-deployment` với **một** dấu gạch, khác với dạng cờ hai dấu gạch (`--cluster`) ngay trong cùng lệnh |

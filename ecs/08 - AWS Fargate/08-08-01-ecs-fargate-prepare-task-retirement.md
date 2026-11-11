# Prepare for AWS Fargate task retirement on Amazon ECS

> Nguồn: **Amazon ECS Developer Guide — Prepare for AWS Fargate task retirement on Amazon ECS**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/prepare-task-retirement.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: **bản dịch** sang tiếng Việt, đi đúng thứ tự và đúng nội dung của trang gốc, không thêm, không bớt ý. Lệnh ví dụ giữ nguyên như trên docs.
> - Khối **💡 Giải thích thêm**: chỉ giải thích lại những gì **chính trang này** đã nói, cho dễ hiểu hơn. Không đưa thông tin từ trang khác hay ví dụ tự đặt.
> - Tên account setting, tag, API, lệnh giữ nguyên tiếng Anh.
> - Tài liệu AWS được phát hành theo giấy phép **Creative Commons Attribution-ShareAlike 4.0**; bản dịch này ghi rõ nguồn ở trên.

---

## Mục lục

0. [Phần mở đầu](#0-phần-mở-đầu)
1. [Bước 1: Đặt thời gian chờ hoặc dùng Amazon EC2 event windows](#1-bước-1-đặt-thời-gian-chờ-hoặc-dùng-amazon-ec2-event-windows)
2. [Bước 2: Bắt thông báo task retirement để báo team và hành động](#2-bước-2-bắt-thông-báo-task-retirement-để-báo-team-và-hành-động)
3. [Bước 3: Kiểm soát việc thay task](#3-bước-3-kiểm-soát-việc-thay-task)
4. [Ghi chú về trang gốc](#4-ghi-chú-về-trang-gốc)

---

## 0. Phần mở đầu

**📘 Theo docs AWS**

Để chuẩn bị cho task retirement, thực hiện các thao tác sau:

1. Đặt **thời gian chờ task retirement** hoặc dùng **Amazon EC2 event windows**.
2. **Bắt thông báo** task retirement để báo cho thành viên team.
3. Bạn có thể đảm bảo mọi task của service chạy trên **platform revision mới nhất** bằng cách cập nhật service với tuỳ chọn **force-deployment**. Bước này là **tuỳ chọn**.

---

## 1. Bước 1: Đặt thời gian chờ hoặc dùng Amazon EC2 event windows

**📘 Theo docs AWS**

Bạn có hai lựa chọn **account setting** để cấu hình thời điểm Fargate bắt đầu task retirement: **`fargateTaskRetirementWaitPeriod`** và **`fargateEventWindows`**.

### 1.1 Dùng account setting `fargateTaskRetirementWaitPeriod`

**📘 Theo docs AWS**

Bạn có thể cấu hình thời điểm Fargate bắt đầu task retirement. Thời gian chờ mặc định là **7 ngày**. Với workload cần áp dụng cập nhật **ngay**, chọn cài đặt tức thì (**`0`**). Nếu cần thêm thời gian, cấu hình lựa chọn **`7`** hoặc **`14`** ngày.

Chúng tôi khuyến nghị bạn chọn thời gian chờ **ngắn hơn** để nhận các platform version revision mới hơn sớm hơn.

Cấu hình thời gian chờ bằng cách chạy **`put-account-setting-default`** hoặc **`put-account-setting`** với tư cách **root user** hoặc **administrative user**. Dùng tuỳ chọn `fargateTaskRetirementWaitPeriod` cho `name` và tuỳ chọn `value` đặt một trong các giá trị sau:

- **`0`** - AWS gửi thông báo, và **ngay lập tức** bắt đầu retire các task bị ảnh hưởng.
- **`7`** - AWS gửi thông báo, và **chờ 7 ngày theo lịch** trước khi bắt đầu retire các task bị ảnh hưởng. Đây là **mặc định**.
- **`14`** - AWS gửi thông báo, và **chờ 14 ngày theo lịch** trước khi bắt đầu retire các task bị ảnh hưởng.

Thông tin thêm, xem *put-account-setting-default* (<https://docs.aws.amazon.com/cli/latest/reference/ecs/put-account-setting-default.html>) và *put-account-setting* (<https://docs.aws.amazon.com/cli/latest/reference/ecs/put-account-setting.html>) trong *Amazon Elastic Container Service API Reference*.

**💡 Giải thích thêm**

| `value` | Sau khi gửi thông báo |
|---|---|
| `0` | Retire **ngay** |
| `7` (mặc định) | Chờ **7 ngày** |
| `14` | Chờ **14 ngày** |

Khuyến nghị của trang: chọn thời gian chờ **ngắn hơn** để lên revision mới sớm hơn.

Lệnh ghép từ đúng tên lệnh, `name` và `value` trang nêu (ví dụ đặt 14 ngày):

```bash
aws ecs put-account-setting-default --name fargateTaskRetirementWaitPeriod --value 14
```

Cần chạy với **root user** hoặc **administrative user** (theo trang).

### 1.2 Dùng account setting `fargateEventWindows`

**📘 Theo docs AWS**

Từ ngày **18/12/2025**, Amazon ECS cho phép bạn cấu hình **Amazon EC2 event windows** (<https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/event-windows.html>) cho task Fargate. Nếu cần kiểm soát **chính xác thời điểm** task retirement, ví dụ lên lịch vào **cuối tuần** để tránh gián đoạn trong giờ làm việc, bạn có thể cấu hình Amazon EC2 event windows cho task, service hoặc cluster.

Khi dùng event window, Fargate đảm bảo task chạy **ít nhất 3 ngày** trước khi bị retire trong **window khả dụng tiếp theo**, trừ khi bị dừng bởi hành động do người dùng khởi tạo hoặc các sự kiện sức khoẻ nghiêm trọng như suy giảm phần cứng bên dưới.

Đặt account setting `fargateEventWindows` thành **`enabled`**. Bạn có thể dùng một trong các API sau: `put-account-setting-default` hoặc `put-account-setting` với tư cách root user hoặc administrative user.

Mỗi Amazon EC2 event window phải **mở ít nhất 4 giờ mỗi tuần**, và mỗi khoảng thời gian phải **dài ít nhất 2 giờ**. Với cluster và service lớn, chúng tôi khuyến nghị cấu hình event window với thời lượng **dài** (**8 giờ trở lên**) hoặc các khoảng thời gian **thường xuyên hơn**, xảy ra **ít nhất mỗi 3 ngày một lần**. Bạn có thể xem thêm các cân nhắc về Amazon EC2 event windows trong *user guide* (<https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/event-windows.html#event-windows-considerations>). AWS Fargate đảm bảo task chạy ít nhất 3 ngày trước khi bị retire, trừ khi bị dừng bởi hành động do người dùng khởi tạo hoặc các sự kiện sức khoẻ nghiêm trọng như suy giảm phần cứng bên dưới.

> **Important:** Việc thay task trong event window là **best effort** (cố gắng hết sức, không đảm bảo). Nếu bạn thấy task bị retire **ngoài** event window, hãy cân nhắc **kéo dài thời lượng** (8 giờ trở lên) hoặc **tăng tần suất** (ít nhất mỗi 3 ngày một lần).

Để áp dụng Amazon EC2 event windows cho Fargate task retirement:

- Đặt account setting `fargateEventWindows` thành **`enabled`**. Bạn có thể dùng một trong các API sau: `put-account-setting-default` hoặc `put-account-setting` với tư cách root user hoặc administrative user. Lưu ý đây là việc **bật một lần** để dùng tính năng Amazon EC2 event windows cho task Fargate.

- **Tạo** một Amazon EC2 event window qua AWS console hoặc AWS CLI. Để tạo event window bằng CLI, dùng API EC2 **`create-instance-event-window`** với **time range** hoặc **cron expression**. Ghi lại **`InstanceEventWindowId`** từ response.

  ```
  aws ec2 create-instance-event-window \
                      --time-range StartWeekDay=monday,StartHour=2,EndWeekDay=wednesday,EndHour=8 \
                      --tag-specifications "ResourceType=instance-event-window,Tags=[{Key=K1,Value=V1" \
                      --name myEventWindowName
  ```

  Ngoài ra, bạn có thể dùng cron expression khi tạo EC2 event window.

  ```
  aws ec2 create-instance-event-window \
                      --cron-expression "* 21-23 * * 2,3" \
                      --tag-specifications "ResourceType=instance-event-window,Tags=[{Key=K1,Value=V1" \
                      --name myEventWindowName
  ```

- Sau đó bạn có thể **gắn** event window với service cụ thể, cluster, hoặc mọi task trong account bằng API EC2 **`associate-instance-event-window`**.

  - Với **ECS service task**

    ```
    aws ec2 associate-instance-event-window \
    --instance-event-window-id iew-0abcdef1234567890 \
    --association-target "InstanceTags=[{Key=aws:ecs:serviceArn,Value=your-service-arn}]"
    ```

  - Với **ECS cluster**

    ```
    aws ec2 associate-instance-event-window \
    --instance-event-window-id iew-0abcdef1234567890 \
    --association-target "InstanceTags=[{Key=aws:ecs:clusterArn,Value=your-cluster-arn}]"
    ```

  - Để gắn event window với **mọi task** trong account

    ```
    aws ec2 associate-instance-event-window \
    --instance-event-window-id iew-0abcdef1234567890 \
    --association-target "InstanceTags=[{Key=aws:ecs:fargateTask,Value=true}]"
    ```

Bạn có thể dùng **nhiều cặp key-value** để gắn một event window với nhiều service hoặc cluster.

Fargate sẽ chọn event window cho mỗi task theo **thứ tự** sau:

- Nếu có event window gắn với **service** của task, nó được dùng. Điều này **không áp dụng** cho standalone task hoặc task không được quản lý.
- Nếu có event window gắn với **cluster** của task, nó được dùng.
- Nếu có event window đặt cho **mọi task Fargate**, nó được dùng.
- Cài đặt **`fargateTaskRetirementWaitPeriod`** sẽ được dùng nếu **không** event window nào khớp với task.

**Cấu hình event window cho Fargate task maintenance**

Xét trường hợp bạn chạy nhiều ECS service trên Fargate với các yêu cầu tính sẵn sàng khác nhau. Bạn muốn kiểm soát chính xác task retirement. Bạn có thể cấu hình nhiều event window như sau:

- **Bảo trì mặc định cho mọi task Fargate**: Tạo một event window cho bảo trì định kỳ vào **giờ thấp điểm** (**12 giờ đêm tới 4 giờ sáng mỗi ngày**) và gắn nó với mọi task Fargate bằng tag **`aws:ecs:fargateTask`**.
- **Bảo trì chỉ cuối tuần cho cluster development**: Với một cluster development có service chịu được gián đoạn vào cuối tuần, tạo một window **24 giờ cuối tuần** (thứ Bảy và Chủ nhật, cả ngày) và gắn nó với cluster bằng tag **`aws:ecs:clusterArn`** với ARN cluster của bạn.
- **Window giới hạn cho service trọng yếu**: Với một service **xử lý thanh toán** trọng yếu cần uptime cao trong các ngày trong tuần, giới hạn bảo trì vào **sáng sớm cuối tuần** (thứ Bảy và Chủ nhật, 12 giờ đêm tới 4 giờ sáng) và gắn nó với service cụ thể bằng tag **`aws:ecs:serviceArn`** với ARN service của bạn.

Với cấu hình này, service thanh toán dùng window riêng chỉ cuối tuần của nó, các service và task của cluster development dùng window 24 giờ cuối tuần, và mọi task Fargate khác dùng window bảo trì mặc định hằng ngày.

Thông tin thêm, xem *put-account-setting-default* (<https://docs.aws.amazon.com/cli/latest/reference/ecs/put-account-setting-default.html>) và *put-account-setting* (<https://docs.aws.amazon.com/cli/latest/reference/ecs/put-account-setting.html>) trong *Amazon Elastic Container Service API Reference*.

**💡 Giải thích thêm**

Ba bước bật event window:

```
1. put-account-setting(-default)  name=fargateEventWindows value=enabled   (bật một lần)
2. aws ec2 create-instance-event-window   (time range hoặc cron) → lấy InstanceEventWindowId
3. aws ec2 associate-instance-event-window  (tag service / cluster / mọi task Fargate)
```

Tag dùng để gắn:

| Phạm vi | Tag |
|---|---|
| Service | `aws:ecs:serviceArn` = ARN service |
| Cluster | `aws:ecs:clusterArn` = ARN cluster |
| Mọi task Fargate trong account | `aws:ecs:fargateTask` = `true` |

Thứ tự ưu tiên khi chọn window cho một task:

```
window của SERVICE  (không áp dụng cho standalone task)
   ↓ không có
window của CLUSTER
   ↓ không có
window cho MỌI task Fargate
   ↓ không có
fargateTaskRetirementWaitPeriod (0 / 7 / 14 ngày)
```

Ràng buộc và khuyến nghị:

| Điểm | Theo trang |
|---|---|
| Tối thiểu mỗi window | Mở ≥ **4 giờ/tuần**; mỗi khoảng ≥ **2 giờ** |
| Cluster/service lớn | Khoảng ≥ **8 giờ**, hoặc xuất hiện **ít nhất mỗi 3 ngày** |
| Task chạy tối thiểu trước khi retire | **3 ngày** (trừ khi người dùng dừng hoặc sự cố phần cứng nghiêm trọng) |
| Tính đảm bảo | **Best effort** — bị retire ngoài window thì kéo dài hoặc tăng tần suất window |

Ví dụ ba window của trang, gom thành bảng:

| Window | Thời gian | Gắn với | Áp dụng cho |
|---|---|---|---|
| Mặc định hằng ngày | 12h đêm – 4h sáng, mỗi ngày | `aws:ecs:fargateTask` | Mọi task Fargate khác |
| Cuối tuần 24 giờ | Thứ Bảy + Chủ nhật, cả ngày | `aws:ecs:clusterArn` | Cluster development |
| Cuối tuần sáng sớm | Thứ Bảy + Chủ nhật, 12h đêm – 4h sáng | `aws:ecs:serviceArn` | Service thanh toán |

Theo thứ tự ưu tiên ở trên: service thanh toán dùng window **của service**, cluster development dùng window **của cluster**, còn lại dùng window **mọi task**.

---

## 2. Bước 2: Bắt thông báo task retirement để báo team và hành động

**📘 Theo docs AWS**

Khi sắp có task retirement, AWS gửi thông báo task retirement tới **AWS Health Dashboard**, và tới **email liên hệ chính** của AWS account. AWS Health Dashboard cung cấp nhiều tích hợp với các dịch vụ AWS khác, gồm **Amazon EventBridge**. Bạn có thể dùng EventBridge để xây dựng tự động hoá từ thông báo task retirement, như tăng mức hiển thị của retirement sắp tới bằng cách chuyển tiếp thông điệp tới một công cụ **ChatOps**. **AWS Health Aware** là một tài nguyên cho thấy sức mạnh của AWS Health Dashboard và cách thông báo có thể được phân phối khắp tổ chức. Bạn có thể chuyển tiếp thông báo task retirement tới một ứng dụng chat, như **Slack**.

Hình sau cho thấy tổng quan giải pháp.

*(Hình trên trang gốc: sơ đồ giải pháp bắt thông báo task retirement của Fargate.)*

Thông tin chi tiết như sau.

- Fargate gửi thông báo task retirement tới AWS Health Dashboard.
- AWS Health Dashboard gửi mail tới email liên hệ chính của AWS account, và thông báo cho EventBridge.
- EventBridge có một **rule** bắt thông báo retirement.

  Rule tìm các event có Event Detail Type: `"AWS Health Event"` và Event Detail Type Code: `"AWS_ECS_TASK_PATCHING_RETIREMENT"`

- Rule kích hoạt một **Lambda function** chuyển thông tin tới Slack bằng **Slack Incoming Webhook**. Thông tin thêm, xem *Incoming Webhooks* (<https://slack.com/marketplace/A0F7XDUAZ-incoming-webhooks>).

Ví dụ code, xem *Capturing AWS Fargate Task Retirement Notifications* (<https://github.com/aws-samples/capturing-aws-fargate-task-retirement-notifications/tree/main>) trên GitHub.

**💡 Giải thích thêm**

```
Fargate ─► AWS Health Dashboard ─┬─► email liên hệ chính
                                  └─► EventBridge rule
                                       (detail-type "AWS Health Event",
                                        code "AWS_ECS_TASK_PATCHING_RETIREMENT")
                                          └─► Lambda ─► Slack Incoming Webhook
```

| Thành phần | Vai trò theo trang |
|---|---|
| AWS Health Dashboard | Nhận thông báo, gửi email, đẩy sang EventBridge |
| EventBridge rule | Lọc event retirement của ECS |
| Lambda | Chuyển thông tin sang Slack qua Incoming Webhook |

---

## 3. Bước 3: Kiểm soát việc thay task

**📘 Theo docs AWS**

Bạn **không thể** kiểm soát **chính xác** thời điểm task retirement, tuy nhiên bạn có thể định nghĩa **thời gian chờ**. Nếu muốn tự kiểm soát việc thay task theo lịch của mình, bạn có thể bắt thông báo task retirement để biết **ngày task retirement** trước. Sau đó bạn có thể **deploy lại** service để khởi chạy task thay thế, và tương tự thay mọi standalone task. Với service dùng **rolling deployment**, bạn cập nhật service bằng **`update-service`** với tuỳ chọn **`force-deployment`** **trước** thời điểm bắt đầu retirement.

Ví dụ `update-service` sau dùng tuỳ chọn `force-deployment`.

```
aws ecs update-service —-service service_name \
    --cluster cluster_name \
     --force-new-deployment
```

Với service dùng **blue/green deployment**, bạn cần tạo **deployment mới** trong **AWS CodeDeploy**. Thông tin về cách tạo deployment, xem *create-deployment* (<https://docs.aws.amazon.com/cli/latest/reference/deploy/create-deployment.html>) trong *AWS Command Line Interface Reference*.

**💡 Giải thích thêm**

| Loại | Cách thay task trước ngày retirement (theo trang) |
|---|---|
| Service — rolling deployment | `aws ecs update-service ... --force-new-deployment` |
| Service — blue/green (CodeDeploy) | Tạo deployment mới trong CodeDeploy (`create-deployment`) |
| Standalone task | Tự thay |

```
Bắt thông báo ─► biết ngày retirement ─► chủ động deploy lại trước ngày đó
                                          (task mới lên revision mới nhất)
```

---

## 4. Ghi chú về trang gốc

**💡 Giải thích thêm**

Các điểm trên chính trang mà bản dịch giữ nguyên như docs:

| Chỗ | Ghi chú |
|---|---|
| Lệnh `create-instance-event-window` (cả hai ví dụ) | `--tag-specifications "...Tags=[{Key=K1,Value=V1"` **thiếu** `}]` ở cuối |
| Lệnh `update-service` ở Bước 3 | Viết `—-service` (dấu gạch dài + gạch ngắn) thay vì `--service` |
| Tên tuỳ chọn | Đoạn văn nói `force-deployment`, còn lệnh dùng `--force-new-deployment` |
| Mục 1.2 | Câu "Fargate đảm bảo task chạy ít nhất 3 ngày…" xuất hiện **hai lần** |

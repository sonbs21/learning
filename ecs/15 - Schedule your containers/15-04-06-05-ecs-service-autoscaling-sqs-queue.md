# Scale Amazon ECS services based on an Amazon SQS queue

> Nguồn: **Amazon ECS Developer Guide — Scale Amazon ECS services based on an Amazon SQS queue**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/service-autoscaling-queue.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: **bản dịch** sang tiếng Việt, đi đúng thứ tự và đúng nội dung của trang gốc, không thêm, không bớt ý. Lệnh và file JSON ví dụ giữ nguyên như trên docs.
> - Khối **💡 Giải thích thêm**: chỉ giải thích lại những gì **chính trang này** đã nói, cho dễ hiểu hơn. Không đưa thông tin từ trang khác hay ví dụ tự đặt.
> - Tên metric, namespace, tham số, lệnh giữ nguyên tiếng Anh.
> - Tài liệu AWS được phát hành theo giấy phép **Creative Commons Attribution-ShareAlike 4.0**; bản dịch này ghi rõ nguồn ở trên.

---

## Mục lục

0. [Phần mở đầu](#0-phần-mở-đầu)
1. [Dùng metric backlog per task](#1-dùng-metric-backlog-per-task)
2. [Dùng metric math để tính metric](#2-dùng-metric-math-để-tính-metric)
3. [Dùng step scaling để kiểm soát nhiều hơn](#3-dùng-step-scaling-để-kiểm-soát-nhiều-hơn)
4. [Bảo vệ task đang xử lý message](#4-bảo-vệ-task-đang-xử-lý-message)
5. [Những điều cần cân nhắc](#5-những-điều-cần-cân-nhắc)
6. [Cấu hình scale theo hàng đợi](#6-cấu-hình-scale-theo-hàng-đợi)

---

## 0. Phần mở đầu

**📘 Theo docs AWS**

Bạn có thể scale Amazon ECS service theo **tải trong một hàng đợi Amazon Simple Queue Service (Amazon SQS)**. Điều này hữu ích cho ứng dụng **bất đồng bộ**, trong đó task của service đóng vai trò **worker** tiêu thụ và xử lý message từ hàng đợi. Khi tốc độ message đến thay đổi, Amazon ECS Service Auto Scaling **thêm task khi hàng đợi dồn lên** và **bớt task khi hàng đợi vơi đi**. Điều này giữ độ trễ của message chờ trong hàng đợi ở mức chấp nhận được mà **không cấp dư**.

---

## 1. Dùng metric backlog per task

**📘 Theo docs AWS**

Amazon SQS đẩy **độ sâu hàng đợi** lên CloudWatch dưới dạng metric **`ApproximateNumberOfMessagesVisible`**, cho biết số message sẵn sàng để lấy. Độ sâu hàng đợi cho thấy có việc cần làm, nhưng **không phải metric đáng tin cậy để scale trực tiếp**. Số message trong hàng đợi **không thay đổi tỉ lệ** với số task xử lý nó. Số task bạn cần còn phụ thuộc các yếu tố khác, như thời gian một task xử lý một message và lượng độ trễ (thời gian chờ trong hàng đợi) chấp nhận được. Một target tracking policy theo dõi độ sâu hàng đợi sẽ hiểu một hàng đợi lớn là nhu cầu cần **tỉ lệ thuận nhiều task hơn**. Điều này có thể khiến service **scale out vượt xa** mức workload cần và **tăng chi phí**.

Thay vào đó, hãy scale theo **backlog per task**: **độ sâu hàng đợi chia cho số task đang chạy**. Bạn tính giá trị này bằng một **CloudWatch metric math expression** chia độ sâu hàng đợi cho số task đang chạy. Backlog per task **chuẩn hoá** độ sâu hàng đợi theo số task đang chạy. Điều này làm nó **tỉ lệ với tải trên mỗi task**, nên phù hợp để scale.

Đặt giá trị mục tiêu là **acceptable backlog per task** (backlog chấp nhận được mỗi task). Để tính, chia **độ trễ ứng dụng chấp nhận được** cho **thời gian trung bình một task xử lý một message**.

Ví dụ, nếu độ trễ chấp nhận được là **10 giây** và một task mất **0,1 giây** để xử lý một message, acceptable backlog per task là 10 chia 0,1, bằng **100 message mỗi task**. Đây là giá trị mục tiêu cho policy. Nếu hàng đợi hiện có **1.500** message và **10** task đang chạy, backlog per task là **150** (1.500 chia 10), **vượt** mục tiêu. Service scale out **thêm năm task** để đưa backlog per task về tỉ lệ với giá trị mục tiêu.

**💡 Giải thích thêm**

| Metric | Vấn đề / ưu điểm theo trang |
|---|---|
| `ApproximateNumberOfMessagesVisible` (độ sâu hàng đợi) | **Không** tỉ lệ với số task → scale trực tiếp dễ scale out quá mức, tốn chi phí |
| **Backlog per task** = độ sâu ÷ số task đang chạy | Tỉ lệ với tải trên mỗi task → phù hợp để scale |

Công thức:

```
acceptable backlog per task (TargetValue) = độ trễ chấp nhận được ÷ thời gian xử lý 1 message
backlog per task (đang có)                = số message trong hàng đợi ÷ số task đang chạy
```

Ví dụ của trang, tính đầy đủ:

| Bước | Phép tính | Kết quả |
|---|---|---|
| Mục tiêu | 10 giây ÷ 0,1 giây | **100** message/task |
| Hiện tại | 1.500 message ÷ 10 task | **150** message/task (> 100) |
| Sau khi thêm 5 task | 1.500 ÷ 15 | **100** message/task (= mục tiêu) |

---

## 2. Dùng metric math để tính metric

**📘 Theo docs AWS**

Target tracking policy của Application Auto Scaling hỗ trợ CloudWatch **metric math**. Metric math cho phép bạn truy vấn nhiều CloudWatch metric và dùng biểu thức toán học để tạo một chuỗi thời gian mới dựa trên các metric đó. Bạn dùng một metric math expression để tính backlog per task **trực tiếp trong scaling policy**. Application Auto Scaling đánh giá biểu thức và quản lý các CloudWatch alarm khởi chạy hoạt động scale. Thông tin thêm, xem *Use metric math* (<https://docs.aws.amazon.com/AmazonCloudWatch/latest/monitoring/using-metric-math.html>) trong *CloudWatch User Guide*.

Để tính backlog per task, bạn dùng hai CloudWatch metric sau:

- **`ApproximateNumberOfMessagesVisible`** (thống kê **`Sum`**) từ namespace **`AWS/SQS`**, cho số message đang chờ trong hàng đợi.
- **`RunningTaskCount`** (thống kê **`Average`**) từ namespace **`ECS/ContainerInsights`**, cho số task đang chạy trong service. Để dùng metric này, bạn **phải bật CloudWatch Container Insights** cho cluster. Thông tin thêm, xem *Monitor Amazon ECS containers using Container Insights with enhanced observability* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/cloudwatch-container-insights.html>).

Trong ví dụ sau, `m1` là độ sâu hàng đợi và `m2` là số task đang chạy. Metric math expression **`m1 / m2`** tạo ra backlog per task. Application Auto Scaling điều chỉnh số task trong service để giữ giá trị này ở mức giá trị mục tiêu.

**💡 Giải thích thêm**

| Id | Metric | Namespace | Statistic | Vai trò |
|---|---|---|---|---|
| `m1` | `ApproximateNumberOfMessagesVisible` | `AWS/SQS` | `Sum` | Số message đang chờ |
| `m2` | `RunningTaskCount` | `ECS/ContainerInsights` | `Average` | Số task đang chạy (cần **Container Insights**) |
| — | Biểu thức `m1 / m2` | — | — | **Backlog per task** |

Câu "Trong ví dụ sau" ở mục này nói về ví dụ JSON nằm ở **bước 4 của mục 6**.

---

## 3. Dùng step scaling để kiểm soát nhiều hơn

**📘 Theo docs AWS**

Chúng tôi khuyến nghị **target tracking** cho hầu hết workload dựa trên hàng đợi vì đó là cách **đơn giản nhất**. Bạn đặt một giá trị mục tiêu duy nhất, và Amazon ECS Service Auto Scaling tạo và quản lý CloudWatch alarm và mức điều chỉnh scale cho bạn.

Dùng **step scaling** khi bạn muốn **toàn quyền kiểm soát** hành vi scale. Với step scaling, bạn tự tạo CloudWatch alarm và định nghĩa **policy scale-out và scale-in riêng**, mỗi policy có step adjustment riêng. Bạn đặt metric math expression backlog per task vào các alarm, và đặt action của mỗi alarm tới policy tương ứng. Điều này cho bạn kiểm soát các điểm sau:

- **Kích thước mỗi bước scale** – Bạn có thể định nghĩa step adjustment thêm nhiều task hơn khi backlog tăng, để vượt ngưỡng nhỏ thì thêm vài task và vượt ngưỡng lớn thì thêm nhiều task. Bạn có thể scale theo **phần trăm** số task đang chạy hoặc theo **số task cố định**.
- **Tốc độ phản hồi của scale** – Bạn kiểm soát độ nhạy qua tần suất đẩy metric và cài đặt đánh giá alarm. Ví dụ, bạn có thể đẩy một metric hàng đợi **độ phân giải cao mỗi 10 giây** và cấu hình alarm scale-out vượt ngưỡng chỉ với **một data point**, để service phản ứng với backlog tăng **nhanh hơn** độ phân giải một phút mặc định.
- **Hành vi scale-out và scale-in khác nhau** – Vì scale-out và scale-in là các policy và alarm riêng, bạn có thể phản ứng **nhanh** với backlog tăng trong khi scale in **thận trọng**. Ví dụ, bạn có thể yêu cầu backlog phải **giữ thấp trong vài phút** trước khi bớt task.

Thông tin thêm, xem *Use predefined increments based on CloudWatch alarms to scale Amazon ECS services* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/service-autoscaling-stepscaling.html>).

**💡 Giải thích thêm**

| | Target tracking (khuyến nghị cho đa số) | Step scaling (kiểm soát toàn bộ) |
|---|---|---|
| Alarm | Tự động tạo và quản lý | **Bạn** tạo, đặt biểu thức backlog per task vào alarm |
| Policy | Một giá trị mục tiêu | Hai policy riêng: scale-out và scale-in |
| Kích thước bước | Tự tính | Bạn đặt: theo % hoặc số task cố định, theo mức vượt ngưỡng |
| Tốc độ phản hồi | Theo mặc định | Bạn chỉnh: vd metric độ phân giải cao 10 giây, alarm vượt với 1 data point |
| Scale-in | Theo cơ chế chung | Có thể thận trọng riêng, vd backlog thấp vài phút mới bớt task |

---

## 4. Bảo vệ task đang xử lý message

**📘 Theo docs AWS**

Khi service scale in, Amazon ECS Service Auto Scaling có thể **dừng một task đang xử lý message**. Với công việc **chạy lâu** hoặc **không idempotent**, điều này có thể khiến message bị **xử lý lại** hoặc **mất tiến độ**. Để tránh việc này, dùng **task scale-in protection** để đánh dấu task được bảo vệ khi đang làm việc.

Với worker xử lý hàng đợi, chúng tôi khuyến nghị **task tự bảo vệ** qua **endpoint của Amazon ECS container agent**. Khi task bắt đầu xử lý một message, nó đặt thuộc tính **`ProtectionEnabled`** thành **`true`** để Amazon ECS không dừng nó trong sự kiện scale-in. Sau khi task xử lý xong message, nó đặt `ProtectionEnabled` thành **`false`** để task lại có thể bị dừng. Thông tin thêm, xem *Protect your Amazon ECS tasks from being terminated by scale-in events* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task-scale-in-protection.html>).

**💡 Giải thích thêm**

```
Worker lấy message ─► ProtectionEnabled = true   (ECS không dừng task khi scale in)
                  ─► xử lý message
                  ─► ProtectionEnabled = false  (task lại có thể bị dừng)
```

Rủi ro nếu không bảo vệ (theo trang): công việc chạy lâu hoặc không idempotent có thể bị **xử lý lại** hoặc **mất tiến độ**.

---

## 5. Những điều cần cân nhắc

**📘 Theo docs AWS**

Cân nhắc các điểm sau khi cấu hình scale theo hàng đợi cho Amazon ECS service:

- Vì Application Auto Scaling **tự đánh giá** metric math expression cho bạn, bạn **không cần** đẩy custom metric hay chạy thêm code nào để tính backlog per task.
- Metric **`RunningTaskCount`** chỉ có khi **CloudWatch Container Insights được bật** cho cluster. Nếu không, metric math expression **không có dữ liệu cho số chia** và **không scale**.
- Chọn giá trị mục tiêu dựa trên độ trễ mà **SLA** của ứng dụng cho phép. Giá trị mục tiêu **thấp hơn** giữ thời gian chờ trong hàng đợi **ngắn hơn** nhưng chạy **nhiều task hơn**. Giá trị mục tiêu **cao hơn** giảm chi phí nhưng **tăng thời gian** message chờ trong hàng đợi.
- Message chưa xử lý xong khi task bị dừng sẽ **quay lại** hàng đợi Amazon SQS. Một task đang chạy khác có thể xử lý chúng nếu bạn cấu hình **visibility timeout** phù hợp. Để giữ task không bị dừng khi đang xử lý message, dùng task scale-in protection, như mô tả ở *Bảo vệ task đang xử lý message* (mục 4).
- Các cân nhắc chung cho Amazon ECS Service Auto Scaling và target tracking cũng áp dụng cho scale theo hàng đợi.

**💡 Giải thích thêm**

| TargetValue | Thời gian chờ trong hàng đợi | Số task / chi phí |
|---|---|---|
| Thấp hơn | Ngắn hơn | Nhiều hơn |
| Cao hơn | Dài hơn | Ít hơn, rẻ hơn |

| Điểm | Theo trang |
|---|---|
| Custom metric / code | Không cần — Application Auto Scaling tự tính biểu thức |
| Container Insights tắt | `RunningTaskCount` không có → **không scale** |
| Task bị dừng giữa chừng | Message quay lại hàng đợi; task khác xử lý nếu visibility timeout phù hợp |

---

## 6. Cấu hình scale theo hàng đợi

**📘 Theo docs AWS**

Các bước sau cho thấy cách tạo một target tracking scaling policy dùng metric math expression để scale Amazon ECS service theo backlog per task. Bạn dùng AWS CLI cho các việc này.

1. Xác nhận bạn có các **quyền IAM** cần để tạo và cập nhật service, và để đăng ký scalable target và scaling policy. Thông tin thêm, xem *IAM permissions required for Amazon ECS service auto scaling* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/auto-scaling-IAM.html>).

2. **Bật CloudWatch Container Insights** cho cluster để metric `RunningTaskCount` có sẵn. Thông tin thêm, xem *Monitor Amazon ECS containers using Container Insights with enhanced observability* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/cloudwatch-container-insights.html>).

3. Đăng ký Amazon ECS service làm scalable target bằng lệnh **`register-scalable-target`** (<https://docs.aws.amazon.com/cli/latest/reference/application-autoscaling/register-scalable-target.html>). Scalable dimension là **`ecs:service:DesiredCount`**.

   ```
   aws application-autoscaling register-scalable-target \
       --service-namespace ecs \
       --scalable-dimension ecs:service:DesiredCount \
       --resource-id service/my-cluster/my-service \
       --min-capacity 1 \
       --max-capacity 10
   ```

4. Lưu metric math expression trong một **customized metric specification** trong file JSON tên **`config.json`**. Thay tên hàng đợi, tên cluster và tên service bằng giá trị của bạn, và đặt **`TargetValue`** bằng acceptable backlog per task bạn đã tính.

   ```
   {
       "CustomizedMetricSpecification": {
           "Metrics": [
               {
                   "Label": "Get the queue depth",
                   "Id": "m1",
                   "MetricStat": {
                       "Metric": {
                           "MetricName": "ApproximateNumberOfMessagesVisible",
                           "Namespace": "AWS/SQS",
                           "Dimensions": [
                               { "Name": "QueueName", "Value": "my-queue" }
                           ]
                       },
                       "Stat": "Sum"
                   },
                   "ReturnData": false
               },
               {
                   "Label": "Get the running task count",
                   "Id": "m2",
                   "MetricStat": {
                       "Metric": {
                           "MetricName": "RunningTaskCount",
                           "Namespace": "ECS/ContainerInsights",
                           "Dimensions": [
                               { "Name": "ClusterName", "Value": "my-cluster" },
                               { "Name": "ServiceName", "Value": "my-service" }
                           ]
                       },
                       "Stat": "Average"
                   },
                   "ReturnData": false
               },
               {
                   "Label": "Calculate the backlog per task",
                   "Id": "e1",
                   "Expression": "m1 / m2",
                   "ReturnData": true
               }
           ]
       },
       "TargetValue": 100
   }
   ```

5. Tạo target tracking scaling policy bằng lệnh **`put-scaling-policy`** (<https://docs.aws.amazon.com/cli/latest/reference/application-autoscaling/put-scaling-policy.html>) với file JSON làm đầu vào. Application Auto Scaling tạo và quản lý **hai CloudWatch alarm** (một cho scale-out và một cho scale-in) kích hoạt policy.

   ```
   aws application-autoscaling put-scaling-policy \
       --policy-name sqs-backlog-target-tracking-policy \
       --service-namespace ecs \
       --resource-id service/my-cluster/my-service \
       --scalable-dimension ecs:service:DesiredCount \
       --policy-type TargetTrackingScaling \
       --target-tracking-scaling-policy-configuration file://config.json
   ```

Thông tin thêm về target tracking scaling policy, xem *Target tracking scaling policies* (<https://docs.aws.amazon.com/autoscaling/application/userguide/application-auto-scaling-target-tracking.html>) trong *Application Auto Scaling User Guide*.

**💡 Giải thích thêm**

```
1. Quyền IAM
2. Bật Container Insights (để có RunningTaskCount)
3. register-scalable-target   ecs:service:DesiredCount, min 1, max 10
4. config.json                m1 (queue depth) / m2 (running tasks) = e1, TargetValue 100
5. put-scaling-policy         TargetTrackingScaling → tự tạo 2 alarm (scale-out, scale-in)
```

Đọc `config.json`:

| Id | `ReturnData` | Ý nghĩa |
|---|---|---|
| `m1` | `false` | Chỉ dùng làm đầu vào (không phải metric scale) |
| `m2` | `false` | Chỉ dùng làm đầu vào |
| `e1` | **`true`** | Kết quả `m1 / m2` — **metric dùng để scale** |
| `TargetValue` | — | `100` = acceptable backlog per task trong ví dụ của mục 1 |

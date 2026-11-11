# Handle Amazon ECS throttling issues

> Nguồn: **Amazon ECS Developer Guide — Handle Amazon ECS throttling issues**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/operating-at-scale-dealing-with-throttles.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: **bản dịch** sang tiếng Việt, đi đúng thứ tự và đúng nội dung của trang gốc, không thêm, không bớt ý. Code ví dụ giữ nguyên như trên docs.
> - Khối **💡 Giải thích thêm**: chỉ giải thích lại những gì **chính trang này** đã nói, cho dễ hiểu hơn. Không đưa thông tin từ trang khác hay ví dụ tự đặt.
> - Tên API, sự kiện, metric, dịch vụ giữ nguyên tiếng Anh.
> - Tài liệu AWS được phát hành theo giấy phép **Creative Commons Attribution-ShareAlike 4.0**; bản dịch này ghi rõ nguồn ở trên.

---

## Mục lục

0. [Phần mở đầu](#0-phần-mở-đầu)
1. [Throttling đồng bộ (synchronous)](#1-throttling-đồng-bộ-synchronous)
2. [Throttling bất đồng bộ (asynchronous)](#2-throttling-bất-đồng-bộ-asynchronous)
3. [Giám sát throttling](#3-giám-sát-throttling)
4. [Dùng CloudWatch để giám sát throttling](#4-dùng-cloudwatch-để-giám-sát-throttling)
5. [Tổng hợp](#5-tổng-hợp)

---

## 0. Phần mở đầu

**📘 Theo docs AWS**

Lỗi throttling chia thành hai nhóm chính: **throttling đồng bộ** và **throttling bất đồng bộ**.

---

## 1. Throttling đồng bộ (synchronous)

**📘 Theo docs AWS**

Khi xảy ra throttling đồng bộ, bạn nhận **ngay lập tức** một response lỗi từ Amazon ECS. Nhóm này thường xảy ra khi bạn gọi Amazon ECS API trong lúc chạy task hoặc tạo service. Thông tin thêm về throttling liên quan và các giới hạn throttle, xem *Request throttling for the Amazon ECS API* (<https://docs.aws.amazon.com/AmazonECS/latest/APIReference/request-throttling.html>).

Khi ứng dụng của bạn khởi tạo API request, ví dụ bằng AWS CLI hoặc AWS SDK, bạn có thể khắc phục API throttling. Bạn làm vậy bằng cách thiết kế ứng dụng để **xử lý lỗi**, hoặc triển khai chiến lược **exponential backoff và jitter** kèm logic **retry** cho các lời gọi API. Thông tin thêm, xem *Timeouts, retries, and backoff with jitter* (<https://aws.amazon.com/builders-library/timeouts-retries-and-backoff-with-jitter/>).

Nếu dùng AWS SDK, logic retry tự động đã **có sẵn** và **cấu hình được**.

**💡 Giải thích thêm**

| Điểm | Theo đoạn trên |
|---|---|
| Khi nào xảy ra | Bạn gọi ECS API (vd chạy task, tạo service) |
| Nhận biết | Nhận lỗi **ngay** trong response |
| Cách xử lý | Ứng dụng xử lý lỗi, hoặc retry với exponential backoff + jitter |
| Dùng AWS SDK | Retry tự động có sẵn, cấu hình được |

---

## 2. Throttling bất đồng bộ (asynchronous)

**📘 Theo docs AWS**

Throttling bất đồng bộ xảy ra do các quy trình bất đồng bộ, trong đó Amazon ECS hoặc CloudFormation có thể đang gọi API **thay cho bạn** để cấp tài nguyên. Điều quan trọng là phải biết Amazon ECS gọi những AWS API nào thay cho bạn. Ví dụ, API `CreateNetworkInterface` được gọi cho task dùng network mode `awsvpc`, và API `DescribeTargetHealth` được gọi khi thực hiện health check cho task đã đăng ký vào load balancer.

Khi workload đạt quy mô đáng kể, các thao tác API này có thể bị throttle — tức là bị throttle đủ để vượt các giới hạn mà Amazon ECS hoặc dịch vụ AWS được gọi áp đặt. Ví dụ, nếu bạn deploy hàng trăm service, mỗi service có hàng trăm task chạy đồng thời dùng network mode `awsvpc`, Amazon ECS gọi các thao tác Amazon EC2 API như `CreateNetworkInterface` và các thao tác Elastic Load Balancing API như `RegisterTarget` hoặc `DescribeTargetHealth` để đăng ký ENI và load balancer tương ứng. Các lời gọi API này có thể vượt giới hạn API, dẫn tới lỗi throttling. Sau đây là ví dụ một lỗi throttling của Elastic Load Balancing có trong service event message.

```json
{
   "userIdentity":{
      "arn":"arn:aws:sts::111122223333:assumed-role/AWSServiceRoleForECS/ecs-service-scheduler",
      "eventTime":"2022-03-21T08:11:24Z",
      "eventSource":"elasticloadbalancing.amazonaws.com",
      "eventName":" DescribeTargetHealth ",
      "awsRegion":"us-east-1",
      "sourceIPAddress":"ecs.amazonaws.com",
      "userAgent":"ecs.amazonaws.com",
      "errorCode":"ThrottlingException",
      "errorMessage":"Rate exceeded",
      "eventID":"0aeb38fc-229b-4912-8b0d-2e8315193e9c"
   }
}
```

Khi các lời gọi API này **dùng chung giới hạn** với các traffic API khác trong account, chúng có thể khó giám sát dù được phát ra dưới dạng service event.

**💡 Giải thích thêm**

| Điểm | Theo đoạn trên |
|---|---|
| Ai gọi API | Amazon ECS hoặc CloudFormation, **thay cho bạn** |
| Ví dụ API | `CreateNetworkInterface` (task `awsvpc`); `RegisterTarget`, `DescribeTargetHealth` (load balancer) |
| Khi nào xảy ra | Quy mô lớn, vd hàng trăm service × hàng trăm task `awsvpc` deploy cùng lúc |
| Nhận biết | Lỗi xuất hiện trong **service event message** |
| Khó khăn | Dùng chung giới hạn với API khác trong account → khó giám sát |

Đọc ví dụ lỗi trên trang:

| Trường | Giá trị | Ý nghĩa |
|---|---|---|
| `userIdentity.arn` | `…AWSServiceRoleForECS/ecs-service-scheduler` | Người gọi là **ECS service scheduler**, qua service-linked role |
| `eventSource` | `elasticloadbalancing.amazonaws.com` | API bị gọi thuộc Elastic Load Balancing |
| `eventName` | `DescribeTargetHealth` | API cụ thể |
| `errorCode` / `errorMessage` | `ThrottlingException` / `Rate exceeded` | Bị throttle |

Trong ví dụ trên trang gốc, giá trị `eventName` có khoảng trắng ở hai đầu (`" DescribeTargetHealth "`); bản ở trên giữ nguyên như docs.

---

## 3. Giám sát throttling

**📘 Theo docs AWS**

Điều quan trọng là xác định **API request nào bị throttle** và **ai gửi** các request này. Bạn có thể dùng **AWS CloudTrail**, dịch vụ giám sát throttling và tích hợp với CloudWatch, Amazon Athena và Amazon EventBridge. Bạn có thể cấu hình CloudTrail gửi các sự kiện cụ thể tới CloudWatch Logs. **CloudWatch Logs Insights** phân tích cú pháp và phân tích các sự kiện. Việc này xác định các chi tiết trong sự kiện throttling như user hoặc IAM role đã gọi và số lượng lời gọi API đã thực hiện. Thông tin thêm, xem *Monitoring CloudTrail log files with CloudWatch Logs* (<https://docs.aws.amazon.com/awscloudtrail/latest/userguide/monitor-cloudtrail-log-files-with-cloudwatch-logs.html>).

Thông tin thêm về CloudWatch Logs Insights và hướng dẫn cách truy vấn log file, xem *Analyzing log data with CloudWatch Logs Insights* (<https://docs.aws.amazon.com/AmazonCloudWatch/latest/logs/AnalyzingLogData.html>).

Với **Amazon Athena**, bạn có thể tạo truy vấn và phân tích dữ liệu bằng SQL chuẩn. Ví dụ, bạn có thể tạo một bảng Athena để phân tích các sự kiện CloudTrail. Thông tin thêm, xem *Using the CloudTrail console to create an Athena table for CloudTrail logs* (<https://docs.aws.amazon.com/athena/latest/ug/cloudtrail-logs.html#create-cloudtrail-table-ct>).

Sau khi tạo bảng Athena, bạn có thể dùng các truy vấn SQL như sau để điều tra lỗi `ThrottlingException`.

Thay `user-input` bằng giá trị của bạn.

```sql
select eventname, errorcode,eventsource,awsregion, useragent,COUNT(*) count
FROM cloudtrail_table-name
where errorcode = 'ThrottlingException'
AND eventtime between '2024-09-24T00:00:08Z' and '2024-09-23T23:15:08Z'
group by errorcode, awsregion, eventsource, useragent, eventname
order by count desc;
```

Amazon ECS cũng phát thông báo sự kiện tới **Amazon EventBridge**. Có các sự kiện thay đổi trạng thái tài nguyên (resource state change) và sự kiện hành động của service (service action). Chúng bao gồm các sự kiện throttling API như **`ECS_OPERATION_THROTTLED`** và **`SERVICE_DISCOVERY_OPERATION_THROTTLED`**. Thông tin thêm, xem *Amazon ECS service action events* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/ecs_service_events.html>).

Các sự kiện này có thể được một dịch vụ như **AWS Lambda** tiêu thụ để thực hiện hành động phản hồi. Thông tin thêm, xem *Handling Amazon ECS events* (<https://docs.aws.amazon.com/AmazonECS/latest/developerguide/ecs_cwet_handling.html>).

Nếu bạn chạy **standalone task**, một số thao tác API như **`RunTask`** là bất đồng bộ, và thao tác retry **không được thực hiện tự động**. Trong những trường hợp đó, bạn có thể dùng các dịch vụ như **AWS Step Functions** tích hợp với EventBridge để retry các thao tác bị throttle hoặc thất bại. Thông tin thêm, xem *Manage a container task (Amazon ECS, Amazon SNS)* (<https://docs.aws.amazon.com/step-functions/latest/dg/sample-project-container-task-notification.html>).

**💡 Giải thích thêm**

Công cụ giám sát theo trang:

| Công cụ | Dùng để |
|---|---|
| AWS CloudTrail → CloudWatch Logs + Logs Insights | Xem API nào bị throttle, ai gọi (user/IAM role), gọi bao nhiêu lần |
| Amazon Athena (bảng trên log CloudTrail) | Truy vấn SQL để đếm lỗi `ThrottlingException` theo API, nguồn, Region, user agent |
| Amazon EventBridge | Nhận sự kiện `ECS_OPERATION_THROTTLED`, `SERVICE_DISCOVERY_OPERATION_THROTTLED` |
| AWS Lambda | Tự động phản hồi các sự kiện trên |
| AWS Step Functions + EventBridge | Retry `RunTask` của standalone task (vì không tự retry) |

Truy vấn Athena trên trang: lọc `errorcode = 'ThrottlingException'` trong một khoảng thời gian, nhóm theo API (`eventname`), nguồn (`eventsource`), Region, `useragent`, rồi sắp xếp theo số lần giảm dần.

Trong truy vấn mẫu trên trang gốc, mốc đầu của `between` (`2024-09-24T00:00:08Z`) **muộn hơn** mốc cuối (`2024-09-23T23:15:08Z`), nên chạy nguyên văn có thể không trả về dòng nào. Khi dùng, đặt mốc sớm hơn ở trước.

---

## 4. Dùng CloudWatch để giám sát throttling

**📘 Theo docs AWS**

CloudWatch cung cấp giám sát mức dùng API trong namespace **`Usage`**, dưới mục **By AWS Resource**. Các metric này được ghi với type **API** và tên metric **CallCount**. Bạn có thể tạo alarm kích hoạt khi các metric này đạt một ngưỡng nhất định. Thông tin thêm, xem *Visualizing your service quotas and setting alarms* (<https://docs.aws.amazon.com/AmazonCloudWatch/latest/monitoring/CloudWatch-Quotas-Visualize-Alarms.html>).

CloudWatch cũng cung cấp **anomaly detection**. Tính năng này dùng machine learning để phân tích và thiết lập đường cơ sở (baseline) dựa trên hành vi riêng của metric mà bạn bật nó. Nếu có hoạt động API bất thường, bạn có thể dùng tính năng này cùng với CloudWatch alarm. Thông tin thêm, xem *Using CloudWatch anomaly detection* (<https://docs.aws.amazon.com/AmazonCloudWatch/latest/monitoring/CloudWatch_Anomaly_Detection.html>).

Nhờ **chủ động** giám sát lỗi throttling, bạn có thể liên hệ Support để tăng các giới hạn throttling liên quan và nhận hướng dẫn cho nhu cầu riêng của ứng dụng.

**💡 Giải thích thêm**

| Tính năng CloudWatch | Cách dùng theo trang |
|---|---|
| Namespace `Usage` → **By AWS Resource**, type **API**, metric **CallCount** | Theo dõi số lần gọi API; đặt alarm khi chạm ngưỡng |
| Anomaly detection | Học baseline của metric bằng ML; kết hợp alarm để phát hiện hoạt động API bất thường |

---

## 5. Tổng hợp

**💡 Giải thích thêm**

Tổng hợp từ các mục trên của chính trang:

| | Throttling đồng bộ | Throttling bất đồng bộ |
|---|---|---|
| Ai gọi API | Ứng dụng của bạn (CLI, SDK) | ECS hoặc CloudFormation thay bạn |
| Nhận biết | Lỗi ngay trong response | Service event message; CloudTrail; EventBridge |
| Ví dụ | Gọi ECS API khi chạy task, tạo service | `CreateNetworkInterface`, `RegisterTarget`, `DescribeTargetHealth` |
| Cách xử lý | Xử lý lỗi; retry với exponential backoff + jitter (SDK có sẵn) | Giám sát (CloudTrail, Athena, EventBridge, CloudWatch); Step Functions retry `RunTask`; liên hệ Support tăng giới hạn |

```
Giám sát:  CloudTrail ─► CloudWatch Logs Insights / Athena   (API nào, ai gọi, bao nhiêu)
           ECS ─► EventBridge (ECS_OPERATION_THROTTLED, SERVICE_DISCOVERY_OPERATION_THROTTLED) ─► Lambda
           CloudWatch Usage (CallCount) + alarm + anomaly detection
Xử lý:     retry (backoff + jitter) | Step Functions cho RunTask | Support tăng giới hạn
```

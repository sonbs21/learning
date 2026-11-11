# AWS Step Functions chuyên sâu — AWS SAA-C03

> Tài liệu này tách riêng và mở rộng phần Step Functions từ Tuần 9. Mục tiêu là hiểu cách mô hình hóa một quy trình nhiều bước bằng state machine, kiểm soát dữ liệu giữa các state, xử lý retry/catch đúng loại lỗi, chọn Standard hay Express và vận hành workflow có thể quan sát, bảo mật và khôi phục.

## 1. Một workflow thực tế

Giả sử quy trình đặt hàng gồm:

~~~text
Nhận order
   ↓
Validate
   ↓
Kiểm tra tồn kho
   ↓
Charge payment
   ↓
Reserve inventory
   ↓
Gửi xác nhận
~~~

Nếu viết tất cả trong một Lambda lớn:

- Code chứa cả business flow và integration.
- Retry từng bước khó kiểm soát.
- Không biết execution đang dừng ở đâu.
- Timeout của Lambda giới hạn toàn quy trình.
- Thêm human approval hoặc wait rất khó.
- Rollback/compensation rối.
- Log phân tán và khó audit.

Với Step Functions:

~~~text
Validate
   ↓
Choice: hợp lệ?
   ├── Không → Reject
   └── Có
        ↓
      Charge ──retry transient error──┐
        ↓                            │
      Reserve                        │
        ↓                            │
      Notify                         │
        ↓                            │
      Succeed                        │
                                     │
      Catch failure → Compensate ←───┘
~~~

Mỗi bước có input, output, timeout, retry và error path rõ ràng.

## 2. Step Functions là gì?

AWS Step Functions là dịch vụ workflow orchestration được quản lý. Workflow được mô tả thành state machine bằng Amazon States Language, viết tắt ASL.

Step Functions có thể:

- Gọi Lambda.
- Gọi API của nhiều AWS service trực tiếp.
- Chạy ECS/Fargate hoặc Batch job.
- Rẽ nhánh theo điều kiện.
- Chạy nhiều nhánh song song.
- Lặp trên dataset.
- Chờ một khoảng thời gian.
- Chờ callback/human approval.
- Retry với backoff và jitter.
- Catch lỗi và chuyển sang recovery path.
- Ghi execution history.
- Hiển thị workflow trực quan.
- Chạy child workflow.

Step Functions không phải compute engine. Nó điều phối; Lambda, ECS, Batch hoặc service khác thực hiện công việc.

## 3. Orchestration và choreography

### Orchestration

Một coordinator quyết định bước tiếp theo:

~~~text
Step Functions
├── gọi Payment
├── gọi Inventory
└── gọi Notification
~~~

Ưu điểm:

- Flow nhìn thấy rõ.
- Retry/catch tập trung.
- Audit dễ.
- Phù hợp quy trình nhiều bước.

### Choreography

Các service phản ứng theo event:

~~~text
OrderCreated
├── Payment consumer
├── Inventory consumer
└── Analytics consumer
~~~

Ưu điểm:

- Loose coupling.
- Fan-out tự nhiên.
- Service độc lập hơn.

Trade-off:

- Khó nhìn toàn bộ business flow.
- Debug chuỗi event khó hơn.
- Compensation phức tạp.

Hệ thống thực tế thường kết hợp: Step Functions orchestrate transaction flow, EventBridge/SNS phát domain event.

## 4. Khi nào nên dùng?

Phù hợp khi:

- Workflow nhiều bước.
- Cần retry/catch theo từng bước.
- Cần wait hoặc human approval.
- Cần audit execution.
- Cần chạy job và đợi hoàn tất.
- Cần parallel/map.
- Cần tách orchestration khỏi Lambda code.
- Cần compensation.
- Cần gọi AWS service trực tiếp.

Không cần Step Functions khi:

- Chỉ gọi một Lambda đơn giản.
- SQS consumer độc lập đã đủ.
- Fan-out event không cần coordinator.
- Flow cực ngắn và direct integration rõ hơn.
- Cost/state-transition overhead không hợp workload.

---

# Phần I — Khái niệm cốt lõi

## 5. State machine

State machine là định nghĩa workflow.

~~~json
{
  "StartAt": "Validate",
  "States": {
    "Validate": {
      "Type": "Task",
      "Resource": "arn:aws:states:::lambda:invoke",
      "Next": "Done"
    },
    "Done": {
      "Type": "Succeed"
    }
  }
}
~~~

Các phần chính:

- StartAt: state bắt đầu.
- States: tập state.
- Type: loại state.
- Next: state tiếp theo.
- End: kết thúc nhánh.
- Comment: mô tả.
- TimeoutSeconds: timeout toàn workflow khi dùng.
- QueryLanguage: JSONPath hoặc JSONata.

## 6. Execution

Execution là một lần chạy state machine.

~~~text
State machine: OrderWorkflow
├── execution order-1001
├── execution order-1002
└── execution order-1003
~~~

Mỗi execution có:

- ARN.
- Input.
- Status.
- Start/end time.
- Output hoặc error.
- Execution history theo workflow type.
- State transition events.

State machine definition là blueprint; execution là instance đang chạy.

## 7. State transition

Khi một state hoàn thành và workflow chuyển sang state khác, đó là state transition.

Standard Workflow tính phí chủ yếu theo state transition. Retry cũng tạo thêm transition, nên lỗi lặp có thể tăng chi phí.

---

# Phần II — Standard và Express

## 8. Standard Workflow

Standard phù hợp workflow:

- Dài, tối đa một năm.
- Durable và auditable.
- Cần execution history trong Step Functions.
- Cần .sync.
- Cần waitForTaskToken.
- Cần Distributed Map.
- Cần exactly-once workflow execution semantics.
- Có bước không idempotent cần orchestration chặt.

Exactly-once ở đây không có nghĩa external service không bao giờ nhận request trùng trong mọi failure scenario. Nếu bạn cấu hình Retry hoặc network timeout làm kết quả không rõ, task vẫn cần idempotency.

## 9. Express Workflow

Express phù hợp:

- High-volume.
- Short-running, tối đa năm phút.
- Event processing.
- IoT/stream transformation.
- Microservice orchestration ngắn.
- Các bước idempotent.

Express không lưu execution history theo cách Standard lưu. Muốn xem chi tiết cần bật CloudWatch Logs.

Express chỉ hỗ trợ Request Response integration pattern; không hỗ trợ .sync hoặc waitForTaskToken.

## 10. Async và Sync Express

### Asynchronous Express

StartExecution trả xác nhận đã bắt đầu, không chờ kết quả.

Semantics: at-least-once. Một execution có thể chạy lại; task phải idempotent.

### Synchronous Express

StartSyncExecution chờ workflow hoàn thành và trả result.

Semantics: at-most-once theo service model. Workflow không tự restart khi exception của execution xảy ra.

## 11. Bảng so sánh

| Tiêu chí | Standard | Async Express | Sync Express |
|---|---|---|---|
| Duration tối đa | 1 năm | 5 phút | 5 phút |
| Semantics | Exactly-once workflow | At-least-once | At-most-once |
| History | Step Functions giữ | CloudWatch Logs nếu bật | CloudWatch Logs nếu bật |
| Pricing | State transitions | Execution, duration, memory | Execution, duration, memory |
| .sync | Có theo integration | Không | Không |
| Callback token | Có theo integration | Không | Không |
| Distributed Map | Có | Không | Không |
| Human approval | Phù hợp | Không phù hợp | Không phù hợp |
| High-volume ngắn | Có thể nhưng đắt hơn | Phù hợp | Phù hợp |
| Caller chờ result | Thường start async/API khác | Không | Có |

Workflow type không thể đổi sau khi tạo. Muốn đổi phải tạo state machine khác.

---

# Phần III — Các loại state

## 12. Task

Task thực hiện một đơn vị công việc:

- Invoke Lambda.
- Gọi DynamoDB.
- Publish SNS.
- Send SQS.
- Start ECS task.
- Submit Batch job.
- Start child workflow.
- Gọi AWS SDK integration.
- Gọi HTTPS endpoint theo capability.

Task thường có:

- Resource.
- Arguments/Parameters.
- Timeout.
- Heartbeat.
- Retry.
- Catch.
- Result processing.
- Next hoặc End.

## 13. Choice

Choice rẽ nhánh theo dữ liệu:

~~~text
amount > 1000?
├── Có  → ManualReview
└── Không → AutoApprove
~~~

Luôn có Default nếu dữ liệu có thể không match. Nếu không có rule match và không có Default, execution lỗi.

Không đặt business rule quá phức tạp trong Choice nếu cần test/version như code domain.

## 14. Parallel

Parallel chạy các branch độc lập cùng lúc và đợi tất cả kết thúc.

~~~text
Parallel
├── CheckFraud
├── CheckCredit
└── CheckInventory
        ↓
chờ tất cả
~~~

Nếu một branch fail không được xử lý, Parallel state fail và các branch khác bị stop theo semantics dịch vụ. External task có thể vẫn tiếp tục nếu cancellation không tới hoặc service không hỗ trợ dừng.

Parallel phù hợp số nhánh cố định, khác Map lặp cùng logic trên danh sách động.

## 15. Map

Map chạy cùng một iterator/item processor cho từng item.

~~~text
Items: [A, B, C]
   ↓
Map
├── Process A
├── Process B
└── Process C
~~~

Có hai mode:

- Inline Map.
- Distributed Map.

## 16. Wait

Wait dừng workflow đến:

- Số giây.
- Timestamp.
- Giá trị lấy từ input.

Wait của Standard không giữ Lambda chạy, nên phù hợp delay dài. Không viết Lambda sleep hàng giờ.

## 17. Pass

Pass:

- Chuyển input.
- Thêm dữ liệu tĩnh.
- Biến đổi dữ liệu.
- Tạo output mock.
- Gán variable theo query language.

Pass không gọi compute.

## 18. Succeed và Fail

Succeed kết thúc nhánh/execution thành công.

Fail kết thúc lỗi với Error và Cause phù hợp.

Không đưa secret, token hoặc stack trace nhạy cảm vào Cause vì có thể xuất hiện trong history/log.

---

# Phần IV — Amazon States Language và dữ liệu

## 19. JSON đi qua workflow

Mỗi state nhận JSON input và tạo JSON output.

~~~text
Execution input
   ↓
State A input
   ↓ processing
State A output
   ↓
State B input
~~~

Nếu không kiểm soát, payload sẽ phình dần do mỗi state chèn thêm response metadata.

## 20. JSONPath pipeline

Với JSONPath, thứ tự xử lý:

~~~text
State input
   ↓ InputPath
   ↓ Parameters
Task/Service
   ↓ ResultSelector
   ↓ ResultPath
   ↓ OutputPath
State output
~~~

### InputPath

Chọn phần input đưa vào state.

### Parameters

Tạo payload gửi task từ static value, input path hoặc context.

### ResultSelector

Lọc/biến đổi raw result trước khi merge.

### ResultPath

Quyết định:

- Thay toàn bộ input bằng result.
- Chèn result vào một field.
- Giữ input và bỏ result bằng null.
- Kết hợp input/result.

### OutputPath

Lọc output chuyển sang state tiếp theo.

## 21. Ví dụ JSONPath

Input:

~~~json
{
  "order": {
    "id": "o-100",
    "amount": 500
  },
  "debug": "remove-me"
}
~~~

Tư duy cấu hình:

~~~text
InputPath      → $.order
Parameters     → { orderId, amount }
Task result    → { Payload, metadata }
ResultSelector → chỉ lấy Payload
ResultPath     → $.payment
OutputPath     → $
~~~

Output nên gọn:

~~~json
{
  "id": "o-100",
  "amount": 500,
  "payment": {
    "status": "AUTHORIZED"
  }
}
~~~

## 22. Context object

Context cung cấp metadata như:

- Execution ID/name.
- State name.
- Retry count.
- Entered time.
- Task token.
- Map item index/value.

JSONPath thường truy cập context bằng $$. JSONata dùng các biến/context tương ứng.

Không coi execution name do caller gửi là trusted business identity nếu chưa validate.

## 23. JSONata

JSONata là query/transformation language mới hơn được Step Functions khuyến nghị cho workflow mới theo tài liệu hiện hành.

JSONata thường dùng:

- Arguments.
- Output.
- Assign.
- Condition.
- Expression giữa ký hiệu {% ... %}.

JSONPath dùng năm trường input/output truyền thống; JSONata rút gọn còn Arguments và Output trong nhiều trường hợp.

Không trộn JSONPath và JSONata trong cùng một state. Có thể chọn QueryLanguage ở cấp state machine hoặc chuyển từng state theo quy tắc hỗ trợ.

Ở mức SAA-C03, cần hiểu data transformation và payload limit; không cần thuộc mọi cú pháp.

## 24. Payload lớn

Input/output mỗi task, state hoặc execution tối đa 256 KiB.

Không truyền:

- File.
- Ảnh.
- CSV lớn.
- Toàn bộ dataset.
- Lambda response metadata không cần.
- Base64 object.

Pattern:

~~~text
S3 lưu data lớn
   ↓
Step Functions truyền:
{
  bucket,
  key,
  versionId
}
~~~

Distributed Map có thể đọc dataset từ S3.

---

# Phần V — Service integrations

## 25. Optimized integration và AWS SDK integration

### Optimized integration

AWS tối ưu parameter, response hoặc integration pattern cho service phổ biến như Lambda, ECS, Batch, DynamoDB, SNS, SQS.

### AWS SDK integration

Gọi rất nhiều AWS API action gần giống dùng SDK.

Lợi ích direct integration:

- Ít Lambda glue code.
- Ít cold start.
- Ít deployment artifact.
- IAM rõ.
- Workflow trực quan.

Không dùng Lambda chỉ để gọi nguyên xi một AWS API nếu integration trực tiếp đáp ứng requirement.

## 26. Request Response

~~~text
Step Functions → gọi API
               ← nhận HTTP response
               → state tiếp theo
~~~

Nó chỉ đợi API call trả về, không nhất thiết đợi job mà API vừa tạo hoàn thành.

Ví dụ StartJob trả jobId không có nghĩa job đã xong.

Standard và Express đều hỗ trợ Request Response.

## 27. Run a Job .sync

~~~text
Step Functions → submit ECS/Batch/Glue job
               → theo dõi
               → đợi job hoàn tất
               → state tiếp theo
~~~

Chỉ Standard hỗ trợ và chỉ trên integration có hỗ trợ.

.sync cần thêm IAM permission để poll/subscribe/cancel job. Cross-account có thể phụ thuộc polling nhiều hơn.

Nếu workflow bị abort, Step Functions cố dừng job khi integration hỗ trợ và IAM cho phép; không giả định external side effect luôn được rollback.

## 28. Wait for Callback

~~~text
Step Functions
   ↓ gửi task token
SQS / SNS / API / external worker
   ↓ xử lý hoặc human approval
SendTaskSuccess / SendTaskFailure
   ↓
Workflow tiếp tục
~~~

Chỉ Standard hỗ trợ theo integration.

Task token:

- Phải giữ bí mật.
- Chỉ gửi cho principal/system cần dùng.
- Có thể hoàn thành task.
- Phải dùng từ principal cùng AWS account theo quy tắc dịch vụ.
- Hết hiệu lực khi task timeout.
- Token mới được tạo khi task retry.

Dùng TimeoutSeconds và HeartbeatSeconds để tránh execution chờ vô hạn.

## 29. Nested workflow

Một state machine có thể start state machine khác.

Lợi ích:

- Chia workflow lớn.
- Tái sử dụng.
- Tách bounded context.
- Tách Standard parent và Express child.
- Tránh execution history quá lớn.

Trade-off:

- IAM.
- Cost transitions/executions.
- Trace/correlation.
- Error propagation.
- Version/alias coordination.
- Payload limit vẫn áp dụng.

---

# Phần VI — Retry, Catch và timeout

## 30. Mặc định khi Task lỗi

Nếu không có Retry hoặc Catch phù hợp, lỗi Task làm execution fail.

Step Functions không tự đoán lỗi nào nên retry. Bạn phải thiết kế policy.

## 31. Retry

Các trường chính:

- ErrorEquals.
- IntervalSeconds.
- MaxAttempts.
- BackoffRate.
- MaxDelaySeconds.
- JitterStrategy.

Ví dụ tư duy:

~~~json
"Retry": [{
  "ErrorEquals": [
    "Lambda.TooManyRequestsException",
    "States.Timeout"
  ],
  "IntervalSeconds": 2,
  "MaxAttempts": 3,
  "BackoffRate": 2,
  "MaxDelaySeconds": 30,
  "JitterStrategy": "FULL"
}]
~~~

Retry tạo thêm state transition và side effect có thể chạy lại.

## 32. Chỉ retry lỗi transient

Nên retry có giới hạn:

- Throttling.
- Service unavailable.
- Network timeout.
- Temporary dependency failure.

Không retry vô hạn:

- Validation.
- Unauthorized.
- Forbidden.
- Business rejection.
- Payload quá lớn.
- Sai state path.
- Code bug deterministic.

## 33. Error names quan trọng

- States.ALL: wildcard, phải đứng một mình và cuối danh sách retrier/catcher phù hợp.
- States.TaskFailed: mọi task error trừ States.Timeout.
- States.Timeout: task/workflow timeout hoặc heartbeat timeout.
- States.DataLimitExceeded: payload vượt limit.
- States.Runtime: lỗi runtime/path.
- States.ExceedToleratedFailureThreshold: Distributed Map vượt ngưỡng lỗi.
- Lambda.TooManyRequestsException: Lambda throttled.

States.ALL không bắt được mọi terminal error theo cùng cách; DataLimitExceeded và Runtime cần hiểu riêng, tránh nghĩ wildcard cứu được mọi lỗi.

## 34. Catch

Catch route error sang recovery state.

~~~text
ChargePayment
├── ValidationError → Reject
├── PaymentDeclined → NotifyDeclined
├── Timeout         → ReconcilePayment
└── States.ALL      → ManualReview
~~~

Catch được xét theo thứ tự. Catch cụ thể trước, States.ALL cuối.

Dùng ResultPath để giữ input gốc và chèn error:

~~~json
{
  "order": { "id": "o-100" },
  "error": {
    "Error": "PaymentDeclined",
    "Cause": "..."
  }
}
~~~

## 35. Timeout và heartbeat

### TimeoutSeconds

Giới hạn tổng thời gian Task.

### HeartbeatSeconds

Giới hạn thời gian giữa các heartbeat của activity/callback task.

Heartbeat nên nhỏ hơn timeout. External worker gọi SendTaskHeartbeat để báo còn sống.

Không đặt timeout một năm chỉ vì Standard cho phép. Mỗi task cần bounded timeout và recovery path.

## 36. Idempotency

Retry có thể gọi lại payment, create order hoặc send message.

Pattern:

~~~text
Task nhận:
  operationId = executionId + state/business key

Downstream:
  conditional create
  hoặc idempotency table
  hoặc provider idempotency key
~~~

Exactly-once workflow không loại bỏ nhu cầu idempotency ở external side effect.

## 37. Compensation và Saga

Distributed transaction không thể rollback bằng database transaction toàn cục.

Saga:

~~~text
ReserveInventory
   ↓
ChargePayment
   ↓ lỗi Shipping
Compensate:
   ├── RefundPayment
   └── ReleaseInventory
~~~

Compensation cũng phải:

- Idempotent.
- Retry được.
- Có audit.
- Phân biệt undo hoàn toàn và business compensation.
- Có manual review nếu thất bại.

---

# Phần VII — Parallel và Map chuyên sâu

## 38. Parallel

Dùng khi số branch biết trước và logic khác nhau.

Kết quả thường là array kết quả theo branch. Dùng ResultSelector để làm phẳng/chọn field.

Mọi branch nhận input theo cấu hình. Không để nhiều branch cập nhật cùng resource mà không có concurrency control.

## 39. Inline Map

Inline Map:

- Input là JSON array.
- Iteration nằm trong parent history.
- Tối đa 40 iteration đồng thời.
- Chịu 256 KiB payload.
- Chịu 25.000 history events của Standard execution.

Phù hợp danh sách nhỏ/vừa.

MaxConcurrency giúp bảo vệ downstream. 0 thường mang nghĩa dùng mức concurrency tối đa cho phép theo mode.

## 40. Distributed Map

Distributed Map:

- Standard Workflow.
- Mỗi item/batch chạy child execution.
- Tối đa 10.000 child executions song song theo quota.
- Đọc JSON array hoặc S3 dataset như CSV/object list/inventory.
- Mỗi child có history riêng.
- Có Map Run resource/metrics.
- Có tolerated failure count/percentage.
- Có item batching/result writer theo capability.

Phù hợp:

- Xử lý hàng triệu object.
- Dataset vượt 256 KiB.
- Parent history có nguy cơ vượt 25.000 events.
- Cần hơn 40 concurrent iterations.

Không đặt concurrency 10.000 nếu downstream chỉ chịu 100.

## 41. Failure threshold

Distributed Map có thể cho phép một tỷ lệ/số item lỗi.

~~~text
1.000 items
tolerated failure = 1%
→ tối đa khoảng 10 lỗi theo cấu hình/semantics
~~~

Nếu vượt threshold, Map fail với error tương ứng. Item thất bại vẫn cần destination/result/reprocessing strategy.

---

# Phần VIII — IAM và security

## 42. State machine execution role

Execution role cho Step Functions gọi:

- Lambda.
- ECS/Batch.
- DynamoDB.
- SQS/SNS.
- EventBridge.
- Child workflow.
- API/service integrations.

Trust policy cho states.amazonaws.com. Permission least privilege theo action/resource.

## 43. Ai được start execution?

Caller cần identity-based permission như states:StartExecution hoặc StartSyncExecution trên state machine/alias.

Cross-account cần phối hợp role/resource pattern phù hợp. Không để public caller trực tiếp start workflow bằng credential tĩnh.

## 44. Task token security

Task token là capability secret: ai có token có thể complete task.

- Không log token.
- Không gửi trong URL công khai.
- Mã hóa message/channel.
- Giới hạn consumer.
- Không lưu lâu hơn cần thiết.
- Timeout/heartbeat.
- Rotate/retry tạo token mới.
- Audit SendTaskSuccess/Failure.

## 45. Sensitive data

Execution input/output/history và CloudWatch Logs có thể chứa dữ liệu nhạy cảm.

- Truyền reference thay secret.
- Redact log.
- Không đưa password/token vào state.
- Encrypt log group.
- IAM cho GetExecutionHistory.
- Data protection policy.
- Retention phù hợp.
- Dùng Secrets Manager trong task thực thi, không đẩy secret qua workflow.

## 46. IAM policy generation

Workflow Studio/console có thể sinh policy dựa trên definition, nhưng dynamic resource path có thể dẫn tới wildcard rộng.

Sau khi deploy:

- Review policy.
- Thu hẹp resource.
- Dùng condition.
- Tách role theo workflow.
- Kiểm tra permission cho .sync/callback/cancel.
- Không dùng AdministratorAccess.

---

# Phần IX — Versions, aliases và deployment

## 47. Version

Published state machine version là snapshot immutable của definition và role/config liên quan theo capability.

Dùng version để:

- Reproducible deployment.
- Audit.
- Rollback.
- Alias routing.

## 48. Alias

Alias trỏ tới một hoặc hai version và có thể chia traffic.

~~~text
PROD alias
├── 90% version 12
└── 10% version 13
~~~

Dùng cho canary. Theo dõi execution failure, latency và business metric.

Execution đã bắt đầu tiếp tục theo definition/version đã gắn với execution; update alias không biến state giữa chừng.

## 49. Infrastructure as Code

Quản lý state machine bằng:

- SAM.
- CloudFormation.
- CDK.
- Terraform.
- ASL file.
- Workflow Studio export.

Pipeline:

1. Validate definition.
2. Lint.
3. Test state.
4. Deploy version.
5. Integration test.
6. Shift alias.
7. Monitor.
8. Rollback.

Không sửa production trực tiếp trong console mà không đồng bộ IaC.

---

# Phần X — Observability và vận hành

## 50. Standard execution history

Standard lưu execution history, cho phép xem:

- State entered/exited.
- Input/output.
- Retry.
- Error/cause.
- Timing.
- Task scheduled/started/completed.

History được giữ theo quota retention hiện hành.

## 51. Express logging

Express không có persisted Step Functions execution history giống Standard. Bật CloudWatch Logs với level phù hợp.

Log level có thể ảnh hưởng:

- Khả năng debug.
- Dữ liệu nhạy cảm.
- Cost.
- Log volume.

## 52. CloudWatch metrics

- ExecutionsStarted.
- ExecutionsSucceeded.
- ExecutionsFailed.
- ExecutionsTimedOut.
- ExecutionsAborted.
- ExecutionTime.
- ExecutionThrottled.
- ActivitiesTimedOut.
- Lambda/service downstream metrics.
- Map Run metrics.

Alarm theo error rate và business SLO, không chỉ count.

## 53. X-Ray và correlation

Tracing giúp nối Step Functions với Lambda/service khi integration hỗ trợ.

Mang correlation ID/business ID xuyên workflow. Không dùng execution ARN làm business key duy nhất nếu workflow có redrive/restart.

## 54. Redrive

Standard execution thất bại có thể được redrive trong phạm vi hỗ trợ/quota.

Redrive tiếp tục từ state thất bại thay vì chạy lại toàn workflow trong một số trường hợp. Retry counters của state được redrive có behavior riêng.

Vẫn cần:

- Kiểm tra side effect đã xảy ra.
- Idempotency.
- Version compatibility.
- Input/data còn tồn tại.
- Runbook và audit.

## 55. Operational runbook

Mỗi workflow production cần trả lời:

- Ai sở hữu?
- Alarm nào kích hoạt?
- Có retry tự động không?
- Khi nào redrive?
- Khi nào start execution mới?
- Compensation thế nào?
- Token callback bị mất xử lý ra sao?
- Poison item trong Map xử lý thế nào?
- Data/reference còn retention bao lâu?

---

# Phần XI — Quotas và giới hạn

## 56. Giới hạn cốt lõi

| Quota | Standard | Express |
|---|---:|---:|
| Maximum duration | 1 năm | 5 phút |
| Input/output mỗi task/state/execution | 256 KiB | 256 KiB |
| Execution history | 25.000 events | Không cùng giới hạn persisted history |
| History retention | 90 ngày mặc định sau khi đóng | CloudWatch Logs nếu bật |
| Distributed Map | Có | Không |
| Open executions/account/Region | 1.000.000 mặc định | Không áp dụng cùng quota |
| State machine definition size | 1 MB | 1 MB |
| Step Functions API request size | 1 MB | 1 MB |

Các con số có thể thay đổi. Kiểm tra Service Quotas theo Region/account.

## 57. State transition throttling

Standard dùng token bucket cho state transitions.

Default theo tài liệu hiện hành:

- Một số Region lớn: bucket/refill 5.000 mỗi giây.
- Các Region khác: bucket/refill 800 mỗi giây.
- Express: state transition rate không có cùng throttle limit trong bảng.

New account có thể có quota thấp hơn. Khi bị throttle, metric ExecutionThrottled tăng; execution thường bị trì hoãn chứ không nhất thiết business fail ngay.

## 58. StartExecution throttling

StartExecution có quota token bucket khác nhau theo Region và workflow type.

Không học một con số duy nhất cho mọi Region. Capacity planning cần:

- Peak execution starts/giây.
- State transitions/execution.
- Duration.
- Open executions.
- Express hay Standard.
- Distributed Map child start rate.
- Downstream quota.

## 59. HTTP Task

HTTP Task có hard timeout 60 giây cho request-response và quota token bucket riêng.

Nếu external API chạy lâu:

- Dùng callback architecture.
- Queue + worker.
- Poll có bounded wait.
- Không cố giữ HTTP Task quá timeout.

## 60. Map quotas

- Inline Map: tối đa 40 concurrent iterations.
- Distributed Map: tối đa 10.000 parallel child executions.
- Open Map Runs mặc định 1.000.
- Parent Standard history tối đa 25.000 events.
- Distributed child dispatch rate còn phụ thuộc Standard/Express child và quota.

Max cao không có nghĩa nên dùng max. Limit theo downstream.

## 61. Khi gần giới hạn

| Rủi ro | Giải pháp |
|---|---|
| Payload 256 KiB | S3 + truyền reference |
| History 25.000 | Nested workflow/Distributed Map/start new execution |
| Duration > 5 phút | Standard thay Express |
| Duration > 1 năm | Tách workflow/restart/checkpoint |
| Transition throttle | Quota request, giảm state, Express nếu phù hợp |
| Map quá tải backend | MaxConcurrency |
| HTTP Task > 60 giây | Callback/queue |
| Quá nhiều open execution | Timeout/cleanup/quota/thiết kế lại |

---

# Phần XII — Cost optimization

## 62. Standard cost

Standard tính theo state transition.

Tăng cost bởi:

- Nhiều Pass state không cần.
- Poll loop quá dày.
- Retry nhiều.
- Map nhiều item.
- Nested workflow.
- Choice/Wait/Task transitions.

Không gộp state mù quáng làm workflow khó hiểu; tối ưu sau khi đo.

## 63. Express cost

Express tính theo:

- Số execution.
- Duration.
- Memory consumption theo pricing.

CloudWatch Logs là chi phí bổ sung và có thể lớn ở high volume.

## 64. Tối ưu

- Chọn đúng workflow type.
- Direct service integration thay Lambda glue.
- Batch item hợp lý.
- Giảm payload.
- Chọn log level/retention.
- Dùng Wait thay Lambda sleep.
- Tránh poll loop; dùng .sync/callback/event.
- Retry đúng lỗi với backoff/jitter.
- Nest Express cho high-volume idempotent subflow trong Standard khi hợp lý.
- Theo dõi cost per business workflow.

---

# Phần XIII — Kiến trúc mẫu

## 65. Order Saga

~~~text
Validate
  ↓
Reserve inventory
  ↓
Charge payment
  ↓
Create shipment
  ↓
Success

Failure:
  Refund payment
  Release inventory
  Manual review
~~~

Standard phù hợp vì audit, compensation và workflow dài.

## 66. Human approval

~~~text
Create request
  ↓
SQS/SNS message chứa task token
  ↓
Approver UI/service
  ├── SendTaskSuccess
  └── SendTaskFailure
  ↓
Workflow tiếp tục
~~~

Dùng Standard, timeout, heartbeat và bảo vệ token.

## 67. ECS/Batch job

~~~text
Step Functions
  ↓ RunTask.sync / SubmitJob.sync
ECS or Batch
  ↓ job complete
Step Functions
  ↓ next state
~~~

Không cần Lambda poll job status. Execution role cần permission start, describe, events và stop theo integration.

## 68. Large-scale S3 processing

~~~text
S3 CSV/Object list
   ↓
Distributed Map
   ↓ up to configured concurrency
Child workflows
   ├── Lambda
   ├── ECS
   └── AWS service
   ↓
Result writer / aggregation
~~~

Đặt MaxConcurrency theo downstream, tolerated failure và item batching.

## 69. Serverless API orchestration

~~~text
API Gateway
   ↓
Sync Express
   ↓
Validate → Parallel reads → Compose response
~~~

Phù hợp flow dưới năm phút, caller cần result, mọi step idempotent và integration pattern phù hợp.

API Gateway timeout có thể ngắn hơn Express max duration; budget end-to-end vẫn cần thiết.

## 70. ETL pipeline

~~~text
EventBridge Scheduler
   ↓
Standard Workflow
   ├── Start Glue job.sync
   ├── Validate output
   ├── Update catalog
   └── Notify
~~~

Scheduler và workflow mỗi bên có retry; task phải idempotent.

---

# Phần XIV — Troubleshooting

## 71. Execution fail ngay khi start

- Invalid input.
- State machine role missing.
- Alias/version sai.
- Execution name duplicate với Standard running execution.
- StartExecution throttle.
- Definition/state path lỗi.

## 72. Task AccessDenied

Xem:

- State machine execution role.
- Target resource policy.
- KMS key policy.
- Cross-account role.
- Dynamic ARN dẫn tới policy thiếu.
- .sync cần describe/events/stop permission.
- Callback principal cùng account.

## 73. States.DataLimitExceeded

- Task output quá lớn.
- Parameters làm input phình.
- State output vượt 256 KiB.
- Connector metadata không được lọc.

Giải pháp:

- ResultSelector/ResultPath/OutputPath.
- S3 reference.
- Bỏ metadata.
- Distributed Map đọc S3.

Không tin States.ALL sẽ xử lý mọi DataLimitExceeded.

## 74. States.Runtime

Thường do:

- JSONPath áp dụng lên null/missing field.
- Sai query language field.
- Intrinsic function sai.
- Choice không có dữ liệu.
- Transformation expression lỗi.

Validate definition và test state với input thật/edge cases.

## 75. Workflow kẹt RUNNING

- Callback token chưa trả.
- Heartbeat/timeout quá rộng.
- .sync job chưa hoàn tất.
- Wait timestamp sai.
- External worker chết.
- Child workflow chạy.
- Retry/backoff dài.

Xem state hiện tại, event history, job status và timeout.

## 76. ExecutionThrottled

- Quá nhiều transitions.
- Map concurrency.
- Poll loop.
- New account quota thấp.
- Burst execution.

Giảm transition, kiểm soát concurrency, xin quota, cân nhắc Express nếu semantics phù hợp.

## 77. Map làm downstream quá tải

- MaxConcurrency quá cao/0.
- Child workflow gọi Lambda/RDS không giới hạn.
- Retry storm.
- Item batch nhỏ.
- Distributed Map tới 10.000 child.

Đặt MaxConcurrency theo capacity thấp nhất, reserved concurrency và downstream rate limit.

## 78. Callback báo invalid token/timeout

- Token cũ sau retry.
- Task đã timeout.
- Execution đã stop.
- Sai account/principal.
- Token bị cắt/encode sai.
- Callback hai lần.

Lưu token an toàn theo task attempt và xử lý callback idempotent.

---

# Phần XV — Bẫy thi SAA-C03

## 79. Những nhầm lẫn thường gặp

1. Step Functions orchestrate; Lambda/ECS/service mới thực thi.
2. Standard tối đa một năm; Express tối đa năm phút.
3. Async Express at-least-once; Sync Express at-most-once.
4. Workflow type không đổi sau khi tạo.
5. Express không hỗ trợ .sync hoặc waitForTaskToken.
6. Distributed Map chỉ Standard.
7. Exactly-once workflow không loại bỏ idempotency external side effect.
8. Request Response không đợi background job hoàn thành.
9. .sync đợi job; callback đợi task token.
10. Wait không giữ Lambda chạy.
11. Parallel cho branch cố định; Map cho collection động.
12. Inline Map tối đa 40 concurrency.
13. Distributed Map tối đa 10.000 child executions nhưng phải bảo vệ downstream.
14. Payload chỉ 256 KiB; data lớn để S3.
15. Standard history tối đa 25.000 events.
16. Retry tính thêm transition và có thể lặp side effect.
17. States.ALL không bắt mọi terminal error.
18. Catch cụ thể đặt trước wildcard.
19. Task token là secret.
20. Express cần CloudWatch Logs để xem history.
21. Execution role phải có quyền gọi target.
22. Direct service integration có thể bỏ Lambda glue.
23. HTTP Task có hard timeout riêng.
24. Compensation không phải database rollback.
25. API Gateway timeout vẫn ảnh hưởng Sync Express API.

## 80. Cách chọn

~~~text
Cần > 5 phút, audit, callback, .sync hoặc Distributed Map?
├── Có → Standard
└── Không
    ├── High-volume, idempotent? → Express
    └── Cần durable history?     → Standard

Cần đợi job hoàn thành?
→ .sync

Cần human/external callback?
→ .waitForTaskToken

Danh sách nhỏ, <= 40 concurrency?
→ Inline Map

Dataset S3/lớn/>40 concurrency?
→ Distributed Map

Data > 256 KiB?
→ S3 + reference
~~~

---

# Phần XVI — Lab

## 81. Lab 1: Retry và Catch

- Lambda giả lập transient error.
- Task Retry với backoff/jitter.
- Validation error không retry.
- Catch sang Fail/ManualReview.
- Quan sát transition và history.
- Thêm idempotency key.

## 82. Lab 2: Direct integration

- Workflow PutItem DynamoDB trực tiếp.
- Publish SNS trực tiếp.
- So sánh với Lambda glue.
- Review generated IAM.
- Thu hẹp policy.

## 83. Lab 3: Callback

- Send SQS message với task token.
- Worker nhận message.
- Gửi heartbeat.
- SendTaskSuccess.
- Test timeout/token cũ/callback hai lần.
- Không log token.

## 84. Lab 4: Inline Map

- Process array 100 item.
- MaxConcurrency nhỏ.
- Gây một item lỗi.
- Retry/Catch.
- Quan sát history growth và downstream load.

## 85. Lab 5: Distributed Map

- CSV trong S3.
- Child workflow.
- Concurrency limit.
- Tolerated failure.
- Map Run metrics.
- Result writer.
- Test redrive.

## 86. Lab 6: Standard và Express

- Cùng flow ngắn.
- Chạy Standard, Async Express, Sync Express.
- So sánh start API, result, logs, history và cost model.
- Test duplicate/idempotency.

---

# Phần XVII — Câu hỏi tự kiểm tra

## 87. Câu hỏi

### Câu 1

Workflow phê duyệt có thể chờ vài ngày và cần audit. Chọn gì?

**Đáp án:** Standard Workflow với waitForTaskToken, timeout và bảo vệ token.

### Câu 2

Hàng triệu event ngắn, idempotent, dưới năm phút. Chọn gì?

**Đáp án:** Express Workflow; Async hoặc Sync tùy caller có cần result ngay.

### Câu 3

Gọi ECS task và phải đợi container hoàn tất?

**Đáp án:** Standard với ECS RunTask.sync nếu integration hỗ trợ.

### Câu 4

Request Response gọi Batch SubmitJob có đợi job xong không?

**Đáp án:** Không. Nó chỉ đợi SubmitJob API trả response; dùng .sync để đợi job.

### Câu 5

Payload giữa state là 5 MB. Làm gì?

**Đáp án:** Lưu S3 và truyền bucket/key/version; quota state payload là 256 KiB.

### Câu 6

Map cần xử lý 5.000 item đồng thời từ CSV S3?

**Đáp án:** Standard Distributed Map, nhưng đặt concurrency theo downstream thay vì mặc định tối đa.

### Câu 7

Payment Task timeout nhưng payment có thể đã charge. Retry thế nào?

**Đáp án:** Idempotency key và reconciliation; không retry mù.

### Câu 8

Cần đợi người dùng bấm approve từ hệ thống ngoài?

**Đáp án:** Standard callback with task token, SendTaskSuccess/Failure, timeout và heartbeat khi phù hợp.

### Câu 9

Tại sao States.ALL không cứu được mọi lỗi?

**Đáp án:** Một số terminal/runtime/data limit error có semantics riêng và không được wildcard xử lý như lỗi task thông thường.

### Câu 10

Inline Map khác Parallel?

**Đáp án:** Parallel chạy số branch cố định khác logic; Map lặp cùng processor trên collection động.

### Câu 11

Express execution history ở đâu?

**Đáp án:** Không persisted như Standard; bật CloudWatch Logs để quan sát chi tiết.

### Câu 12

Một workflow chỉ dùng Lambda để PutItem DynamoDB. Có thể bỏ Lambda không?

**Đáp án:** Có thể dùng optimized/AWS SDK service integration nếu mapping, IAM và error handling đáp ứng.

### Câu 13

State machine role có dynamodb:PutItem nhưng caller không start được workflow. Vì sao?

**Đáp án:** Execution role là quyền workflow gọi service; caller vẫn cần states:StartExecution trên state machine/alias.

### Câu 14

Standard exactly-once có cần idempotency không?

**Đáp án:** Có. Retry được cấu hình, timeout không rõ kết quả và external service semantics vẫn có thể tạo side effect trùng.

---

# Phần XVIII — Checklist

## 88. Checklist ôn thi

- [ ] Giải thích được orchestration và choreography.
- [ ] Phân biệt state machine và execution.
- [ ] Phân biệt Standard, Async Express và Sync Express.
- [ ] Nhớ duration và execution semantics.
- [ ] Biết Task, Choice, Parallel, Map, Wait, Pass, Succeed, Fail.
- [ ] Phân biệt Inline và Distributed Map.
- [ ] Hiểu JSONPath data flow.
- [ ] Nhận biết JSONata.
- [ ] Không truyền payload lớn hơn 256 KiB.
- [ ] Phân biệt optimized và AWS SDK integration.
- [ ] Phân biệt Request Response, .sync và waitForTaskToken.
- [ ] Thiết kế Retry với backoff/jitter.
- [ ] Thiết kế Catch theo error taxonomy.
- [ ] Hiểu Timeout và Heartbeat.
- [ ] Thiết kế idempotency và compensation.
- [ ] Bảo vệ task token.
- [ ] Dùng execution role least privilege.
- [ ] Hiểu version, alias và canary.
- [ ] Bật logging/metrics/tracing.
- [ ] Biết redrive và runbook.
- [ ] Nhớ quota duration, payload, history, Map và HTTP Task.
- [ ] Chọn workflow type theo requirement.
- [ ] Troubleshoot DataLimitExceeded, Runtime, throttle và callback.

---

# 89. Tóm tắt một trang

~~~text
STEP FUNCTIONS
managed workflow orchestration bằng ASL

STANDARD
duration 1 năm
exactly-once workflow semantics
history/audit
.sync + callback + Distributed Map
cost theo transition

EXPRESS
duration 5 phút
Async = at-least-once
Sync  = at-most-once
Request Response only
history qua CloudWatch Logs
cost theo execution/duration/memory

STATES
Task Choice Parallel Map Wait Pass Succeed Fail

DATA
JSONPath:
InputPath → Parameters → Task → ResultSelector
→ ResultPath → OutputPath

JSONata:
Arguments + Output + Assign/Condition

payload tối đa 256 KiB
data lớn → S3 reference

INTEGRATIONS
Request Response → đợi API response
.sync            → đợi job
waitForTaskToken → đợi external callback

ERROR
Retry transient + backoff + jitter
Catch specific trước, States.ALL cuối
timeout + heartbeat
mọi side effect quan trọng cần idempotency

MAP
Inline      → array, tối đa 40 concurrent, parent history
Distributed → S3/array, tối đa 10.000 child, Standard only

SECURITY
caller permission → start execution
execution role    → workflow gọi target
task token        → secret

LIMITS
Standard 1 năm, Express 5 phút
payload 256 KiB
Standard history 25.000 events
HTTP Task 60 giây
definition 1 MB
~~~

# 90. Tài liệu AWS chính thức

- [AWS Step Functions Developer Guide](https://docs.aws.amazon.com/step-functions/latest/dg/welcome.html)
- [Chọn Standard hay Express](https://docs.aws.amazon.com/step-functions/latest/dg/choosing-workflow-type.html)
- [Workflow states](https://docs.aws.amazon.com/step-functions/latest/dg/workflow-states.html)
- [Amazon States Language](https://states-language.net/spec.html)
- [Service integration patterns](https://docs.aws.amazon.com/step-functions/latest/dg/connect-to-resource.html)
- [AWS SDK integrations](https://docs.aws.amazon.com/step-functions/latest/dg/supported-services-awssdk.html)
- [Error handling, Retry và Catch](https://docs.aws.amazon.com/step-functions/latest/dg/concepts-error-handling.html)
- [Input và output processing](https://docs.aws.amazon.com/step-functions/latest/dg/concepts-input-output-filtering.html)
- [JSONata transformation](https://docs.aws.amazon.com/step-functions/latest/dg/transforming-data.html)
- [Map state](https://docs.aws.amazon.com/step-functions/latest/dg/state-map.html)
- [Distributed Map](https://docs.aws.amazon.com/step-functions/latest/dg/concepts-orchestrate-large-scale-parallel-workloads.html)
- [Step Functions service quotas](https://docs.aws.amazon.com/step-functions/latest/dg/service-quotas.html)
- [Step Functions best practices](https://docs.aws.amazon.com/step-functions/latest/dg/sfn-best-practices.html)
- [CloudWatch Logs](https://docs.aws.amazon.com/step-functions/latest/dg/cw-logs.html)
- [State machine versions](https://docs.aws.amazon.com/step-functions/latest/dg/concepts-state-machine-version.html)
- [State machine aliases](https://docs.aws.amazon.com/step-functions/latest/dg/concepts-state-machine-alias.html)


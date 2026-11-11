# Tuần 9 — Serverless: Lambda, API Gateway và Step Functions

> Mục tiêu: chọn đúng Lambda/API Gateway/Step Functions theo workload, hiểu invocation và concurrency, xử lý cold start/retry/error, bảo vệ secret và xây workflow serverless có thể quan sát.

## 1. Kết quả cần đạt

- Phân biệt Lambda synchronous, asynchronous và event source mapping.
- Hiểu concurrency, reserved concurrency và provisioned concurrency.
- Giải thích cold start, timeout, memory, ephemeral storage và layers.
- Dùng execution role, environment variables và Secrets Manager đúng cách.
- Hiểu tác động của VPC Lambda tới networking và cold start.
- Phân biệt API Gateway REST, HTTP và WebSocket.
- Hiểu stage, throttling, usage plan, authorizer và custom domain.
- Cấu hình Lambda integration, mapping, timeout và error response.
- Phân biệt Step Functions Standard và Express.
- Dùng Retry, Catch, Choice, Parallel, Map và workflow state.
- Nhận biết EventBridge Scheduler.
- Chọn Lambda hay ECS/EC2 theo runtime, duration, control và workload pattern.
- Thiết kế asynchronous retry, DLQ/destination và consumer idempotency.

## 2. Lịch học gợi ý

| Buổi | Nội dung | Thời lượng |
|---|---|---:|
| 1 | Lambda runtime, invocation và execution model | 3 giờ |
| 2 | Concurrency, cold start, memory, layers và VPC | 3 giờ |
| 3 | API Gateway, auth, throttling và custom domain | 3 giờ |
| 4 | Step Functions, retry/catch và Scheduler | 2–3 giờ |
| 5 | Lab tích hợp, CloudWatch và câu hỏi thi | 2 giờ |

Cách suy luận: kiểm tra runtime/duration trước khi chọn compute; xác định sync/async trước khi thiết kế retry; xác định API protocol/auth trước khi chọn API Gateway type; mô tả workflow trước khi chọn Step Functions.

---

## 3. Serverless fundamentals

Serverless không có nghĩa không có server. AWS quản lý server, capacity nền và phần lớn scaling; khách hàng trả cho execution/request/capacity và quản lý code, data, IAM, configuration, limits và observability.

### 3.1. Khi dùng serverless

Phù hợp khi:

- traffic burst hoặc không đều;
- event-driven processing;
- task ngắn, stateless;
- muốn giảm patch/server operations;
- scale-to-zero hoặc pay-per-use có lợi;
- workflow tích hợp nhiều managed service.

Cần cân nhắc khi:

- runtime chạy lâu hoặc cần process liên tục;
- cần OS/kernel/network control;
- dependency lớn hoặc startup rất nặng;
- workload cần local state lâu dài;
- execution vượt timeout hoặc concurrency limit;
- latency cực thấp và không chấp nhận cold start.

### 3.2. Lambda so với EC2/ECS

| Tiêu chí | Lambda | ECS/Fargate | EC2 |
|---|---|---|---|
| Vận hành server | rất ít | managed container nhưng vẫn quản task | nhiều hơn |
| Runtime | event/function, giới hạn execution | container linh hoạt | toàn quyền OS |
| Scaling | request/concurrency | task/service scaling | ASG/instance scaling |
| Startup | cold start | task startup | instance startup |
| Duration/control | giới hạn và managed | linh hoạt hơn | cao nhất |
| Cost | request/duration | task CPU/memory/time | instance/storage/time |
| Use case | event/API/job ngắn | service/container | long-running/custom OS |

Không chọn Lambda chỉ vì “serverless” nếu requirement cần process dài, custom runtime control hoặc long-running connection.

---

## 4. Lambda execution model

Lambda function gồm code, runtime, handler, memory, timeout, environment, execution role, layers và event configuration.

Một invocation có các giai đoạn:

1. service chọn hoặc tạo execution environment;
2. init code/package/runtime;
3. gọi handler với event;
4. xử lý response/error;
5. environment có thể được reuse hoặc bị giải phóng.

Code ngoài handler có thể chạy một lần mỗi execution environment, nên connection/client có thể đặt ngoài handler để reuse nhưng không được giả định state luôn tồn tại.

Lambda stateless ở cấp application. File trong ephemeral storage chỉ dùng tạm; dữ liệu lâu dài nên đặt ở S3, DynamoDB, RDS hoặc storage phù hợp.

### 4.1. Handler

Handler nên:

- parse/validate event;
- xử lý idempotent;
- timeout dependency rõ ràng;
- log correlation/request ID;
- không chứa secret hard-code;
- trả error đúng để invoker biết retry hay failure;
- không giữ mutable global state dựa trên execution environment.

### 4.2. Timeout và memory

Memory cấu hình ảnh hưởng cả CPU và một số network resource. Tăng memory có thể giảm duration nhưng tăng cost mỗi đơn vị thời gian; benchmark cost/performance thay vì luôn chọn thấp nhất.

Timeout phải lớn hơn p99 dependency nhưng không quá lớn khiến stuck invocation giữ capacity. Đặt timeout ở function, SDK/client, API Gateway và downstream nhất quán.

Lambda có giới hạn execution tối đa theo dịch vụ. Nếu task vượt giới hạn hoặc cần process liên tục, xem ECS/Fargate, Batch, Step Functions orchestration hoặc worker pattern.

### 4.3. Ephemeral storage

Lambda có ephemeral storage trong execution environment cho temporary files, unzip hoặc intermediate processing. Nó không phải persistent storage và có thể bị xóa khi environment recycle.

Nếu cần dung lượng tạm lớn, cấu hình phù hợp và dọn file sau mỗi invocation. Không dùng ephemeral storage làm source of truth.

### 4.4. Layers

Lambda layer chia sẻ dependencies/runtime code giữa nhiều function. Lợi ích:

- giảm duplication;
- quản lý dependency tập trung;
- có thể dùng native library chung.

Trade-off:

- version compatibility;
- package size;
- cold start;
- coupling giữa function và layer;
- rollback phức tạp nếu layer thay đổi.

---

## 5. Lambda invocation types

### 5.1. Synchronous invocation

Caller chờ response từ Lambda. Ví dụ API Gateway, ALB, SDK call.

Đặc điểm:

- lỗi trả trực tiếp cho caller;
- caller/trigger chịu trách nhiệm retry;
- timeout end-to-end phải phù hợp;
- phù hợp API và interactive request;
- không có DLQ asynchronous theo cùng pattern.

Flow:

~~~text
Client → API Gateway → Lambda → response
~~~

### 5.2. Asynchronous invocation

Service đặt event vào queue nội bộ rồi invoke Lambda. Caller nhận accepted/queued thay vì chờ function hoàn tất.

Ví dụ S3 event, EventBridge hoặc SNS theo integration hỗ trợ.

Đặc điểm:

- Lambda retry lỗi theo cơ chế async;
- có thể cấu hình DLQ hoặc destination;
- event có thể duplicate;
- consumer phải idempotent;
- payload và retry retention cần theo dõi;
- không phù hợp nếu caller cần kết quả ngay.

DLQ thường lưu event thất bại để điều tra; destination có thể route success/failure context tới Lambda, SNS, SQS hoặc EventBridge theo pattern hỗ trợ.

### 5.3. Event source mapping

Lambda poll/read event source như SQS, Kinesis hoặc DynamoDB Streams rồi invoke function.

Đặc điểm:

- Lambda service poll source;
- batch size và batching window ảnh hưởng throughput/latency;
- source cần retry/visibility/shard behavior;
- xử lý partial batch failure nếu integration hỗ trợ;
- DLQ thường nằm ở source như SQS;
- function phải idempotent.

Flow:

~~~text
SQS/Kinesis/DynamoDB Streams → event source mapping → Lambda
~~~

Khác biệt quan trọng: event source mapping là Lambda poll source; synchronous/asynchronous invocation là trigger delivery model khác.

### 5.4. Invocation comparison

| Kiểu | Caller chờ? | Retry | Use case |
|---|---|---|---|
| Synchronous | có | caller/integration | API, request-response |
| Asynchronous | không | Lambda async retry + DLQ/destination | event |
| Event source mapping | không trực tiếp | source/consumer retry | SQS, stream, queue |

---

## 6. Concurrency

Concurrency là số invocation đang chạy đồng thời.

Công thức suy nghĩ gần đúng:

~~~text
Concurrency ≈ request rate × average duration
~~~

Ví dụ 100 request/giây, duration trung bình 0,5 giây → concurrency gần 50, chưa tính burst và tail latency.

### 6.1. Unreserved concurrency

Phần capacity function có thể dùng trong account/Region nếu không bị giới hạn riêng. Một function burst quá mạnh có thể ảnh hưởng function khác.

### 6.2. Reserved concurrency

Reserved concurrency dành riêng và đồng thời đặt hard limit cho function.

Use case:

- bảo vệ downstream khỏi quá nhiều request;
- đảm bảo function quan trọng có capacity;
- giới hạn runaway recursion;
- cách ly noisy neighbor.

Nếu invocation vượt reserved concurrency, function bị throttled. Đặt quá thấp có thể làm mất throughput; đặt đúng theo downstream capacity.

### 6.3. Provisioned concurrency

Provisioned concurrency giữ execution environment đã initialized để giảm cold start cho function cần latency ổn định.

Use case:

- API latency-sensitive;
- traffic predictable;
- production peak;
- runtime initialization nặng.

Trade-off: phải trả cost cho capacity provisioned ngay cả khi ít request; vẫn cần scaling và giới hạn concurrency phù hợp.

### 6.4. Account limit và throttling

Lambda có account/Region concurrency limit và burst behavior. Khi throttled:

- synchronous caller nhận lỗi;
- async event được retry theo model;
- event source có thể backlog;
- downstream có thể được bảo vệ nhưng latency tăng.

Monitor invocations, errors, throttles, duration, concurrent executions và iterator age cho stream source.

---

## 7. Cold start và performance

Cold start xảy ra khi Lambda phải tạo execution environment mới. Các yếu tố ảnh hưởng:

- runtime;
- package size;
- layers;
- dependency/native library;
- VPC initialization/configuration;
- memory/CPU;
- init code;
- provisioned concurrency;
- traffic burst.

Giảm cold start bằng:

- package nhỏ;
- lazy import khi phù hợp;
- không init dependency nặng không cần;
- reuse client/connection ngoài handler;
- tăng memory để tăng CPU nếu benchmark cho thấy lợi ích;
- provisioned concurrency cho latency requirement;
- tránh VPC nếu không cần private resource;
- dùng runtime phù hợp.

Không cố “giữ warm” bằng ping tùy tiện để thay thế provisioned concurrency; có cost và không bảo đảm tuyệt đối.

### 7.1. Lambda trong VPC

VPC Lambda cần ENI/network path để truy cập private RDS, ElastiCache hoặc internal service. Khi vào private subnet:

- cần route, SG, NACL và DNS đúng;
- outbound Internet cần NAT Gateway hoặc endpoint;
- VPC attachment không tự cho Internet access;
- có thể tăng cold start/complexity tùy runtime/config;
- Lambda không nhận public IP để đi Internet trực tiếp.

Nếu Lambda chỉ gọi public AWS API và không cần private resource, không đưa vào VPC chỉ vì “bảo mật”. Nếu cần S3/DynamoDB private path, dùng VPC endpoint phù hợp.

### 7.2. Connection management

Lambda burst có thể tạo nhiều DB connection. Dùng RDS Proxy/pool/connection limit, reserved concurrency và caching phù hợp. Không mở connection mới vô hạn trong mỗi invocation nếu downstream có giới hạn.

---

## 8. Lambda permissions và configuration

### Execution role

Execution role cho phép Lambda gọi CloudWatch Logs, S3, DynamoDB, SQS, Secrets Manager, KMS hoặc service khác. Trust policy cho Lambda service; permissions phải least privilege.

### Resource-based policy

Một số trigger/service cần resource-based policy trên function để cho phép invoke. Identity role của function và invoke permission là hai chuyện khác nhau.

### Environment variables

Environment variables phù hợp config không nhạy cảm và reference tới secret. Không coi environment variable plaintext là secret manager. Nếu chứa secret, dùng encryption/KMS và access control nhưng ưu tiên Secrets Manager/Parameter Store cho secret lifecycle.

### Secrets Manager

Lambda lấy secret runtime bằng execution role. Secret rotation cần:

- function có permission đọc version hiện tại;
- cache secret với thời gian hợp lý;
- xử lý đổi credential;
- không log secret;
- retry/timeout Secrets Manager;
- kiểm tra VPC endpoint/NAT nếu Lambda trong private subnet.

### Layers và container image

Container image phù hợp package lớn/custom dependency; vẫn có cold start, image pull/cache và security scanning. Không nhét secret vào image layer vì image có thể được pull/scan/share.

---

## 9. API Gateway

API Gateway là managed entry point cho API, hỗ trợ authentication, throttling, routing, monitoring, custom domain và integration.

### 9.1. REST API

REST API có nhiều feature và customization:

- stages/deployments;
- usage plans/API keys;
- request/response mapping;
- authorizer;
- caching theo capability;
- resource/policy controls;
- can dùng Lambda, HTTP, AWS service integration.

Phù hợp API cần feature phong phú, mapping phức tạp hoặc compatibility với REST API configuration.

### 9.2. HTTP API

HTTP API thường đơn giản hơn, latency/cost thấp hơn cho use case API phổ thông. Phù hợp Lambda/HTTP backend với auth/routing cơ bản.

Đừng chọn REST chỉ vì tên “REST” nếu requirement chỉ cần API proxy đơn giản; so sánh feature và pricing.

### 9.3. WebSocket API

WebSocket giữ kết nối hai chiều lâu hơn cho chat, real-time notification, live update hoặc collaborative app. Cần thiết kế connection state, reconnect, authorization và scale.

### 9.4. Stage và deployment

Stage đại diện environment/version như dev, staging, prod. Deployment mới chưa chắc production dùng ngay; cần stage variable/mapping và promotion rõ.

Custom domain có thể map base path tới stage/API. Certificate thường quản lý bằng ACM theo requirement của service/Region.

---

## 10. API Gateway request flow

~~~text
Client
  ↓ HTTPS/custom domain
API Gateway
  ↓ auth/throttle/validation/mapping
Lambda hoặc HTTP/AWS integration
  ↓
Response mapping → Client
~~~

Các lớp cần kiểm tra khi request lỗi:

- DNS/custom domain/certificate;
- route/resource/method;
- authorizer/IAM;
- API key/usage plan nếu dùng;
- throttling/quota;
- integration permission;
- Lambda timeout/error;
- mapping response;
- backend SG/network nếu HTTP integration.

### 10.1. Lambda integration

API Gateway cần permission invoke Lambda. Lambda response phải đúng format integration; error handling cần mapping rõ status code và body an toàn.

Không leak stack trace, secret hoặc internal endpoint ra client. Log chi tiết server-side, trả error message phù hợp.

### 10.2. Throttling và usage plan

Throttling giới hạn request để bảo vệ API/backend. Có thể ở account/stage/route/usage plan tùy API type.

Usage plan và API key dùng kiểm soát quota/usage cho một số client; API key không phải authentication mạnh. Dùng IAM auth, JWT/Cognito hoặc Lambda authorizer cho authorization phù hợp.

### 10.3. Authorizer

- IAM authorization: request ký bằng AWS credentials, phù hợp AWS-to-AWS/internal.
- JWT/Cognito: validate token/claims, phù hợp user/API client.
- Lambda authorizer: logic auth tùy biến, có thể cache policy nhưng cần xem latency/security.

Authorizer không thay thế application authorization; kiểm tra tenant/resource permission ở backend khi cần.

### 10.4. Custom domain và TLS

Dùng ACM certificate, DNS alias và base path mapping. Enforce HTTPS và quản lý certificate expiry/rotation.

---

## 11. Step Functions

Step Functions là workflow orchestration managed cho các state/task, retry, timeout, branching và parallelism.

Dùng Step Functions khi cần:

- workflow nhiều bước;
- retry/catch rõ ràng;
- audit execution history;
- human approval/wait;
- gọi Lambda/AWS service;
- parallel/map processing;
- tách orchestration khỏi code Lambda.

Không dùng Step Functions chỉ để gọi một function đơn giản nếu trigger/integration trực tiếp đủ.

### 11.1. State phổ biến

| State | Vai trò |
|---|---|
| Task | gọi Lambda/AWS service/activity |
| Choice | branch theo input |
| Parallel | chạy nhánh song song |
| Map | lặp qua list, distributed map theo pattern |
| Wait | đợi thời gian/timestamp/event |
| Pass | transform/forward data |
| Succeed | kết thúc thành công |
| Fail | kết thúc lỗi |

### 11.2. Retry

Retry khai báo ErrorEquals, IntervalSeconds, MaxAttempts và BackoffRate. Dùng cho lỗi transient như throttling/network timeout; không retry vô hạn lỗi validation hoặc authorization.

Ví dụ tư duy:

~~~text
Task Process
  Retry transient error: exponential backoff, 3 lần
  Catch validation error → Reject
  Catch unexpected error → ManualReview
~~~

Retry phải idempotent vì cùng task có thể chạy nhiều lần. Nếu task đã tạo order/payment mà timeout trước response, retry không được tạo duplicate.

### 11.3. Catch

Catch route lỗi tới state khác. Có thể trả reason/error, nhưng không đưa secret/stack trace nhạy cảm ra output.

Thiết kế error taxonomy: validation, business rejection, transient infrastructure và unknown.

### 11.4. Standard và Express

| Tiêu chí | Standard | Express |
|---|---|---|
| Duration | workflow dài hơn | workflow ngắn hơn |
| Execution history | chi tiết, audit | tối ưu high-volume |
| Exactly-once workflow semantics | mạnh hơn theo service model | at-least-once/processing model cần chú ý |
| Cost | theo state transition | theo execution duration/request |
| Use case | order, approval, long workflow | high-volume event/data processing |

Ở mức SAA, chọn Standard cho workflow dài cần execution history/audit; Express cho high-volume, short-duration workflow khi chấp nhận semantics phù hợp.

### 11.5. Data flow và payload

Mỗi state nhận input và tạo output. Giữ payload nhỏ, lưu data lớn ở S3/DynamoDB và truyền reference. Tránh đẩy file lớn qua state input/output.

### 11.6. Callback và human approval

Workflow có thể pause chờ callback/token hoặc Wait state. Use case: manual approval, long-running job, external process. Cần timeout, retry, security token và cleanup.

---

## 12. EventBridge Scheduler

EventBridge Scheduler trigger Lambda, Step Functions, SQS hoặc service hỗ trợ theo cron/rate/time schedule.

Use case:

- chạy workflow định kỳ;
- delayed task;
- cleanup;
- reminder;
- batch job;
- one-time schedule.

Cần dùng execution role cho Scheduler, timezone đúng, retry/dead-letter behavior và idempotent target. Scheduler không tự đảm bảo task business chạy một lần nếu target retry; thiết kế idempotency.

---

## 13. Error handling và idempotency

Serverless thường có retry ở nhiều lớp: client, API Gateway/integration, Lambda async, event source, Step Functions và downstream. Vì vậy duplicate là khả năng bình thường.

### Idempotency pattern

- request có idempotency key;
- lưu key/status trong DynamoDB;
- conditional write chống duplicate;
- record result để retry trả cùng kết quả;
- transaction khi nhiều item phải cùng commit;
- không dùng timestamp/random ID mới mỗi lần retry.

### Timeout budget

Tổng latency phải thỏa:

~~~text
Client timeout > API Gateway timeout > Lambda timeout > downstream timeout
~~~

Các timeout không cần xếp tuyệt đối theo mọi integration, nhưng phải có budget rõ và tránh downstream chạy lâu hơn caller đã bỏ cuộc.

### DLQ và destination

Async Lambda failure nên route tới DLQ/destination theo use case để điều tra/replay. Event source như SQS thường dùng DLQ của queue và visibility timeout/maxReceiveCount.

DLQ không thay thế monitoring; cần alarm trên age, depth, failed execution và retry.

---

## 14. Observability

CloudWatch metrics:

- Invocations;
- Errors;
- Duration;
- Throttles;
- ConcurrentExecutions;
- IteratorAge cho stream;
- API Gateway 4xx/5xx, latency, count;
- Step Functions executions failed/timed out;
- downstream connection/error metrics.

Logs nên có:

- request/correlation ID;
- function version;
- execution ARN;
- business key không nhạy cảm;
- error category;
- duration/attempt.

X-Ray hoặc tracing giúp theo dõi request qua API Gateway, Lambda và downstream nếu cấu hình hỗ trợ. Không log secret, token, password hoặc full personal data.

---

## 15. Lab bắt buộc

### Lab 1 — Lambda đọc S3 event

1. Tạo private S3 bucket.
2. Tạo Lambda execution role tối thiểu.
3. Tạo event notification cho prefix test.
4. Parse bucket/key từ event.
5. Ghi log request ID và xử lý idempotent.
6. Test duplicate event và lỗi function.
7. Cấu hình DLQ/destination nếu dùng async pattern.

### Lab 2 — API Gateway gọi Lambda

1. Tạo route GET/POST.
2. Tạo Lambda integration.
3. Cấu hình permission invoke.
4. Trả response đúng status/body/header.
5. Thêm validation/auth phù hợp.
6. Test 4xx, 5xx, timeout và throttling.
7. Đặt custom domain nếu có môi trường phù hợp.

### Lab 3 — Reserved concurrency

- Đặt reserved concurrency thấp cho function test.
- Gửi burst request.
- Quan sát Throttles và downstream load.
- So sánh khi bỏ giới hạn.
- Ghi cách retry của client/integration.

### Lab 4 — Step Functions

Tạo workflow:

~~~text
Validate → Choice
             ├── invalid → Reject
             └── valid → Process → Notify
                              └── error → Retry/Catch
~~~

- Validate input;
- gọi Lambda process;
- retry transient error với backoff;
- catch validation/business error;
- ghi execution history;
- test duplicate/retry và idempotency.

### Lab 5 — Cold start và CloudWatch

- Gọi function sau thời gian idle.
- Gọi nhiều lần liên tục.
- Ghi duration/init duration nếu metric/log hỗ trợ.
- Thử thay đổi memory.
- So sánh package nhỏ/lớn hoặc provisioned concurrency nếu ngân sách cho phép.
- Không dùng warm-up ping như kết luận performance duy nhất.

---

## 16. Bẫy đề thi SAA-C03

- Lambda không phù hợp process dài vượt execution limit hoặc cần OS control.
- Synchronous caller chờ response; async caller không chờ function hoàn tất.
- Async Lambda có retry và có thể dùng DLQ/destination.
- Event source mapping poll SQS/stream; visibility/batch/retry phải cấu hình.
- Lambda invocation có thể duplicate; handler phải idempotent.
- Reserved concurrency giới hạn và dành capacity; quá thấp gây throttling.
- Provisioned concurrency giảm cold start nhưng phát sinh cost.
- Tăng memory có thể tăng CPU và giảm duration; phải benchmark.
- Lambda trong VPC không tự có Internet; cần NAT/endpoint/route/SG.
- Execution role là quyền function gọi service; invoke permission là quyền trigger gọi function.
- Environment variable không tự là secret manager.
- API key/usage plan không phải authentication đầy đủ.
- REST API nhiều feature hơn; HTTP API đơn giản/cost thấp hơn theo use case.
- WebSocket phù hợp kết nối hai chiều, không phải REST request thông thường.
- API Gateway timeout/limits ảnh hưởng thiết kế end-to-end.
- Step Functions orchestrate workflow; Lambda không nên chứa mọi state/retry logic.
- Retry phải giới hạn và chỉ retry transient error.
- Step Functions task phải idempotent vì retry/duplicate.
- Standard phù hợp workflow dài/audit; Express high-volume/short execution.
- Payload lớn nên lưu S3/DynamoDB rồi truyền reference.
- EventBridge Scheduler target vẫn cần IAM role và idempotency.
- Không nhét secret vào code, image, layer hoặc log.

---

## 17. Câu hỏi tự luyện

### Câu 1

API cần trả response cho client sau khi xử lý xong. Chọn invocation nào?

**Đáp án:** synchronous Lambda invocation qua API Gateway hoặc integration phù hợp.

### Câu 2

S3 upload kích hoạt xử lý không cần trả response, lỗi cần retry và lưu event thất bại. Chọn gì?

**Đáp án:** asynchronous Lambda invocation, cấu hình retry và DLQ/destination, handler idempotent.

### Câu 3

Lambda đọc SQS nhưng xử lý lâu hơn visibility timeout. Rủi ro?

**Đáp án:** message xuất hiện lại và bị xử lý duplicate. Tăng visibility timeout phù hợp, dùng heartbeat/partial failure/idempotency.

### Câu 4

Function burst làm RDS hết connection. Giải pháp nào?

**Đáp án:** reserved concurrency, RDS Proxy/connection pooling, timeout và retry hợp lý; không chỉ tăng Lambda concurrency.

### Câu 5

API có p99 latency nhạy với cold start. Chọn gì?

**Đáp án:** giảm package/init, tối ưu memory/runtime và xem provisioned concurrency; đo trước/sau.

### Câu 6

Lambda cần đọc RDS private và gọi S3 private. Cần gì?

**Đáp án:** VPC Lambda, private subnets, route/SG/NACL/DNS, RDS SG, S3 VPC endpoint hoặc NAT theo path và IAM role.

### Câu 7

Team cần API đơn giản, Lambda integration, latency/cost thấp và không cần mapping phức tạp. Chọn API nào?

**Đáp án:** HTTP API nếu các feature/auth/compatibility đáp ứng requirement.

### Câu 8

Cần API client authentication bằng JWT và quota usage riêng. Dùng gì?

**Đáp án:** JWT/Cognito hoặc authorizer cho authentication; usage plan/API key chỉ cho quota/usage nếu API type hỗ trợ, không coi API key là auth mạnh.

### Câu 9

Workflow order gồm validate, charge, reserve inventory và notify; charge lỗi transient cần retry, validation lỗi cần reject. Dùng gì?

**Đáp án:** Step Functions với Task, Retry, Catch, Choice/Parallel theo flow và idempotent task.

### Câu 10

Workflow có hàng triệu execution ngắn, không cần audit history dài như Standard. Chọn gì?

**Đáp án:** Express Workflows nếu semantics, duration và observability đáp ứng.

### Câu 11

Cần chạy workflow mỗi tối lúc 02:00 theo timezone. Dùng gì?

**Đáp án:** EventBridge Scheduler với schedule/timezone, execution role, retry/DLQ và idempotent target.

### Câu 12

Một Lambda gọi service ngoài rồi timeout sau khi service đã xử lý thành công. Retry có thể gây gì?

**Đáp án:** duplicate side effect. Dùng idempotency key/conditional write hoặc transaction/outbox pattern phù hợp.

---

## 18. Checklist hoàn thành tuần

### Kiến thức

- [ ] Phân biệt synchronous, asynchronous và event source mapping.
- [ ] Hiểu retry, DLQ/destination và duplicate event.
- [ ] Giải thích concurrency, reserved và provisioned concurrency.
- [ ] Hiểu cold start, memory, timeout, ephemeral storage và layers.
- [ ] Dùng execution role, environment config và Secrets Manager.
- [ ] Hiểu VPC Lambda, NAT/endpoint và cold start trade-off.
- [ ] Phân biệt REST API, HTTP API và WebSocket API.
- [ ] Hiểu stage, deployment, throttling, usage plan và authorizer.
- [ ] Cấu hình Lambda integration, mapping và error response.
- [ ] Phân biệt Step Functions Standard và Express.
- [ ] Dùng Retry, Catch, Choice, Parallel, Map và Wait.
- [ ] Hiểu EventBridge Scheduler ở mức use case.
- [ ] Thiết kế idempotency và timeout budget.
- [ ] Biết CloudWatch metrics/logs cần theo dõi.

### Thực hành

- [ ] Đã tạo Lambda đọc S3 event.
- [ ] Đã tạo API Gateway gọi Lambda.
- [ ] Đã test 4xx/5xx, timeout và throttling.
- [ ] Đã dùng reserved concurrency bảo vệ downstream.
- [ ] Đã tạo Step Functions validate/process/catch.
- [ ] Đã test retry và idempotency.
- [ ] Đã quan sát cold start/duration bằng CloudWatch.
- [ ] Đã thử Secrets Manager/role thay vì hard-code secret.
- [ ] Đã dọn function, API, log, state machine và test resource sau lab.

Bạn sẵn sàng sang Tuần 10 khi có thể chọn Lambda/API Gateway/Step Functions theo invocation và workflow, đồng thời giải thích retry, concurrency, DLQ và idempotency trong một kiến trúc serverless.

---

## 19. Tài liệu tham khảo

- [AWS Lambda Developer Guide](https://docs.aws.amazon.com/lambda/latest/dg/welcome.html) — runtime, invocation và configuration.
- [Lambda invocation models](https://docs.aws.amazon.com/lambda/latest/dg/lambda-invocation.html) — synchronous/asynchronous/event source.
- [Lambda concurrency](https://docs.aws.amazon.com/lambda/latest/dg/lambda-concurrency.html) — reserved/provisioned concurrency.
- [Lambda with VPC](https://docs.aws.amazon.com/lambda/latest/dg/configuration-vpc.html) — networking và private resources.
- [Lambda environment variables](https://docs.aws.amazon.com/lambda/latest/dg/configuration-envvars.html) — configuration.
- [Amazon API Gateway Developer Guide](https://docs.aws.amazon.com/apigateway/latest/developerguide/welcome.html) — API types, stages và integration.
- [API Gateway throttling](https://docs.aws.amazon.com/apigateway/latest/developerguide/api-gateway-request-throttling.html) — throttling và quota.
- [AWS Step Functions Developer Guide](https://docs.aws.amazon.com/step-functions/latest/dg/welcome.html) — workflow orchestration.
- [Step Functions error handling](https://docs.aws.amazon.com/step-functions/latest/dg/concepts-error-handling.html) — Retry/Catch.
- [Standard and Express Workflows](https://docs.aws.amazon.com/step-functions/latest/dg/choosing-workflow-type.html) — chọn workflow type.
- [EventBridge Scheduler](https://docs.aws.amazon.com/scheduler/latest/UserGuide/what-is-scheduler.html) — scheduled invocation.
- [AWS Secrets Manager](https://docs.aws.amazon.com/secretsmanager/latest/userguide/intro.html) — secret storage/rotation.
- [Amazon CloudWatch for Lambda](https://docs.aws.amazon.com/lambda/latest/dg/monitoring-cloudwatchlogs.html) — logs và monitoring.


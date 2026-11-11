# AWS Lambda chuyên sâu — AWS SAA-C03

> Tài liệu này tách riêng và mở rộng phần Lambda từ Tuần 9. Mục tiêu là hiểu Lambda như một nền tảng xử lý event, không chỉ là nơi chạy một đoạn code. Sau khi học xong, bạn phải giải thích được ai invoke function, ai retry, concurrency tăng thế nào, cold start xuất hiện ở đâu, quyền IAM nào dùng cho chiều nào và phải đổi kiến trúc ra sao khi chạm timeout hoặc quota.

## 1. Lambda là gì?

AWS Lambda là dịch vụ compute chạy code theo event mà không yêu cầu bạn trực tiếp provision hoặc quản lý server.

Bạn cung cấp:

- Code hoặc container image.
- Runtime.
- Handler.
- Memory.
- Timeout.
- IAM execution role.
- Environment configuration.
- Trigger hoặc cơ chế invoke.
- Networking nếu cần vào VPC.
- Retry, DLQ/destination và observability.

AWS quản lý:

- Server vật lý.
- Host operating system.
- Runtime infrastructure.
- Tạo và hủy execution environment.
- Scaling compute cơ bản.
- Fault tolerance của nền tảng.

~~~text
Event source / Client
        │
        ▼
     Lambda service
        │
        ├── chọn hoặc tạo execution environment
        ├── tải code/runtime/configuration
        ├── gọi handler
        └── thu hồi hoặc tái sử dụng environment
        │
        ▼
     Application logic
        │
        ├── S3
        ├── DynamoDB
        ├── SQS
        ├── RDS
        └── external API
~~~

Serverless không có nghĩa là không có server. Nó có nghĩa phần lớn server infrastructure được AWS quản lý; bạn vẫn chịu trách nhiệm cho code, dependency, IAM, dữ liệu, network, retry, quota, security và cost.

## 2. Khi nào Lambda phù hợp?

Lambda phù hợp với:

- API request-response ngắn.
- Xử lý event từ S3, SNS hoặc EventBridge.
- Worker đọc SQS.
- Xử lý stream từ Kinesis hoặc DynamoDB Streams.
- Scheduled job ngắn.
- Automation và glue logic.
- File/image processing theo event.
- Notification và data transformation.
- Workload traffic thay đổi mạnh hoặc có thời gian không có request.
- Kiến trúc event-driven, stateless.

Lambda không phải lựa chọn mặc định cho mọi workload.

Cân nhắc ECS/Fargate, EC2, Batch hoặc dịch vụ khác khi:

- Process chạy liên tục.
- Cần kiểm soát OS/kernel/daemon.
- Job vượt thời gian thực thi phù hợp.
- Cần GPU hoặc host customization.
- Dependency/runtime không phù hợp.
- Local state cần sống lâu.
- Traffic rất ổn định và compute liên tục khiến mô hình khác kinh tế hơn.
- Cần long-lived connection do application trực tiếp giữ.
- Workload cần mô hình multi-request concurrency trong cùng process theo cách server truyền thống.

## 3. Lambda, ECS/Fargate và EC2

| Tiêu chí | Lambda | ECS/Fargate | EC2 |
|---|---|---|---|
| Đơn vị chạy | Function invocation | Container task/service | Virtual machine |
| Quản lý server | Rất ít | Không quản host với Fargate | Tự quản guest OS |
| Scaling | Theo invocation/concurrency | Theo số task | Theo instance |
| Runtime | Managed/custom, có giới hạn | Container linh hoạt | Toàn quyền OS |
| Thời lượng | Function/event oriented | Có thể chạy lâu | Có thể chạy lâu |
| State local | Tạm thời | Tạm thời hoặc storage attach | Tùy storage |
| Startup | Cold/warm start | Task startup | Instance startup |
| Phù hợp | Event/API/job ngắn | Long-running container | Full control |

Câu hỏi đầu tiên không phải “Lambda có chạy được không?”, mà là “execution model của Lambda có phù hợp requirement không?”.

---

# Phần I — Cấu tạo một Lambda function

## 4. Các thành phần chính

~~~text
Lambda function
├── code package hoặc container image
├── runtime
├── architecture
├── handler
├── memory / CPU allocation
├── timeout
├── ephemeral storage
├── environment variables
├── execution role
├── resource-based policy
├── VPC configuration
├── layers/extensions
├── versions/aliases
└── event sources/triggers
~~~

Mỗi thành phần có thể ảnh hưởng startup time, cost, security và khả năng scale.

## 5. Runtime

Runtime là môi trường trung gian giữa Lambda service và code. Nó:

- Nhận event.
- Tạo context.
- Gọi handler.
- Trả response hoặc error.
- Quản lý vòng đời process theo Lambda Runtime API.

Có ba nhóm:

- Managed runtime: AWS cung cấp và vá runtime theo lifecycle.
- Custom runtime: dùng OS-only runtime và tự cung cấp runtime/bootstrap.
- Container image: đóng gói code/runtime theo Lambda image contract.

Runtime có vòng đời hỗ trợ và deprecation. Không pin mãi runtime cũ. Theo dõi deprecation, test upgrade và quản lý dependency độc lập với SDK có sẵn trong runtime.

## 6. Handler

Handler là entry point Lambda gọi cho mỗi event.

Pseudocode:

~~~text
function handler(event, context):
    validate event
    authorize business action
    load required state
    perform idempotent operation
    emit metrics/logs
    return result
~~~

Handler tốt nên:

- Nhỏ và rõ.
- Validate input ở boundary.
- Không hard-code secret.
- Có timeout cho downstream.
- Phân loại lỗi retryable và non-retryable.
- Idempotent khi event có thể được giao lại.
- Ghi correlation ID.
- Không dựa vào mutable global state để đảm bảo correctness.

## 7. Context object

Context cung cấp metadata runtime, ví dụ:

- Request ID.
- Function name/version.
- Remaining execution time.
- Log group/stream.
- Invoked ARN.
- Identity/client context trong một số invocation.

Remaining time hữu ích để dừng an toàn trước timeout:

~~~text
Nếu remaining time thấp:
  checkpoint hoặc trả lỗi có kiểm soát
  không bắt đầu thêm operation dài
~~~

Không dùng context làm source of truth cho business data.

---

# Phần II — Execution environment lifecycle

## 8. Init, Invoke và Shutdown

Lambda chạy code trong execution environment cô lập.

~~~text
INIT
├── tạo environment
├── tải code/runtime
├── chạy extension init
└── chạy code ngoài handler

INVOKE
├── nhận event
├── gọi handler
└── trả result/error

FREEZE / REUSE
├── environment có thể được giữ lại
└── invocation sau có thể dùng lại

SHUTDOWN
└── Lambda thu hồi environment
~~~

Cold start xảy ra khi cần tạo và initialize environment mới. Warm start xảy ra khi environment đã tồn tại được dùng lại.

Không có cam kết environment sẽ được giữ bao lâu.

## 9. Code ngoài handler

Code ngoài handler, còn gọi là static initialization, chạy khi environment khởi tạo.

Phù hợp để tạo:

- AWS SDK client.
- HTTP connection pool.
- Database client/proxy connection handle.
- Parsed configuration.
- Dependency container.
- Read-only model/config cache.

~~~text
INIT một lần cho environment:
  create S3 client
  create HTTP client
  load config

MỖI INVOCATION:
  parse event
  call dependency
  return response
~~~

Lợi ích là giảm công việc lặp lại. Nhưng object phải an toàn khi environment được reuse.

Không đặt ở global state:

- Dữ liệu riêng của request trước.
- Authentication result của user trước.
- Mutable business transaction.
- Secret mà bạn giả định không bao giờ rotate.
- File tạm chưa được phân tách theo request.

## 10. Freeze và reuse

Sau invocation, Lambda có thể freeze environment và resume khi có event mới.

Điều này dẫn tới:

- Connection có thể còn sống hoặc đã bị server đóng.
- File trong /tmp có thể còn, nhưng không được đảm bảo lâu dài.
- Cache có thể giúp performance, nhưng phải có TTL.
- Background thread có thể gây hành vi khó đoán nếu không quản lý.
- Invocation mới phải xác thực lại request và reset request-scoped state.

Correctness không được phụ thuộc vào warm reuse. Performance có thể tận dụng reuse.

## 11. Stateless nghĩa là gì?

Stateless không có nghĩa function không bao giờ giữ cache. Nó nghĩa dữ liệu cần cho correctness phải nằm ngoài execution environment.

Dữ liệu durable nên nằm ở:

- S3.
- DynamoDB.
- RDS/Aurora.
- EFS khi cần shared filesystem.
- ElastiCache cho cache.
- SQS/Kinesis cho event state.
- Step Functions cho workflow state.

~~~text
Không nên:
Lambda local variable → trạng thái order duy nhất

Nên:
Lambda → DynamoDB/RDS → trạng thái order durable
~~~

---

# Phần III — Packaging và deployment artifact

## 12. ZIP package

ZIP package phù hợp với phần lớn function:

- Code nhỏ.
- Runtime managed.
- Dependency vừa phải.
- Deployment nhanh.
- Dùng layer nếu thực sự cần chia sẻ.

Có thể upload trực tiếp hoặc qua S3 tùy kích thước. Unzipped package và layers chịu quota tổng.

## 13. Container image

Lambda hỗ trợ image theo contract Lambda, không phải mọi container web chạy nguyên xi đều phù hợp.

Container image hữu ích khi:

- Dependency lớn.
- Cần native library.
- Cần custom runtime.
- Team đã có image build pipeline.
- Muốn kiểm soát OS package rõ hơn.

Trade-off:

- Image lớn có thể ảnh hưởng distribution/startup.
- Phải scan CVE.
- Phải patch base image.
- Không được nhúng secret.
- Image phải tương thích architecture.
- Lambda container vẫn tuân theo Lambda execution model và quotas; nó không biến thành ECS.

Image thường lưu ở ECR. Dùng immutable tag/digest và lifecycle policy.

## 14. Layers

Layer chứa library, runtime component hoặc extension dùng chung.

Lợi ích:

- Chia sẻ dependency.
- Tách application code và common library.
- Dùng extension/agent.

Trade-off:

- Version coupling.
- Khó nhìn dependency cuối cùng.
- Có thể tăng package/init.
- Tổng unzipped size vẫn chịu quota.
- Layer update không tự thay function version đã publish.

Nếu common code thay đổi thường xuyên, package trực tiếp hoặc dependency build pipeline có thể dễ truy vết hơn.

## 15. Architecture

Lambda có thể hỗ trợ nhiều CPU architecture tùy runtime/Region, phổ biến là x86_64 và arm64.

Arm/Graviton có thể tối ưu cost/performance nhưng phải kiểm tra:

- Native dependency.
- Container image architecture.
- Layer compatibility.
- Benchmark.
- Runtime support.

Không chỉ đổi architecture rồi giả định binary cũ vẫn chạy.

---

# Phần IV — Invocation models

## 16. Ba mô hình phải phân biệt

~~~text
1. Synchronous invocation
   caller chờ result

2. Asynchronous invocation
   Lambda nhận và queue event nội bộ

3. Event source mapping
   Lambda poll queue/stream và gọi function theo batch
~~~

Điểm quan trọng nhất: ai sở hữu retry và event đang nằm ở đâu.

## 17. Synchronous invocation

Caller chờ Lambda xử lý xong.

Ví dụ:

- API Gateway.
- Application Load Balancer.
- SDK Invoke với RequestResponse.
- Một số AWS service integration.

~~~text
Caller → Lambda → response/error → Caller
~~~

Nếu function lỗi:

- Lỗi được trả về caller/integration.
- Lambda không tự retry function error chỉ vì đây là synchronous invocation.
- Caller hoặc upstream service quyết định retry.
- Function có thể đã thực hiện một phần side effect trước khi lỗi.

Do đó synchronous handler vẫn phải idempotent khi client có retry.

## 18. Asynchronous invocation

Caller gửi event và không chờ business result.

~~~text
Producer
   ↓ event accepted
Lambda internal asynchronous queue
   ↓
Function
   ├── success
   └── failure → retry → destination/DLQ
~~~

Ví dụ thường gặp:

- S3 event.
- SNS.
- EventBridge.
- SDK Invoke với Event.

Lambda quản lý internal queue, retry function error theo async policy và có thể route invocation record.

Retry mặc định cần phân biệt hai nhóm lỗi:

- Nếu handler/runtime trả lỗi hoặc timeout, Lambda mặc định chạy thêm hai lần: lần retry đầu sau khoảng một phút và lần tiếp theo sau khoảng hai phút.
- Nếu thiếu concurrency, bị throttle 429 hoặc gặp lỗi hệ thống 5xx, Lambda đưa event lại queue và retry với exponential backoff, từ khoảng một giây đến tối đa khoảng năm phút giữa các lần, trong thời gian giữ event mặc định có thể lên tới sáu giờ.

Các giá trị retry và maximum event age có thể cấu hình trong phạm vi dịch vụ hỗ trợ. Event vẫn có thể được giao trùng ngay cả khi function không báo lỗi, nên idempotency là bắt buộc đối với side effect quan trọng.

Có thể cấu hình:

- Maximum event age.
- Maximum retry attempts.
- On-success destination.
- On-failure destination.
- Dead-letter queue.

Nếu event hết tuổi hoặc hết retry, phải có destination/DLQ và alarm nếu dữ liệu quan trọng.

## 19. DLQ và destination khác nhau

### Dead-letter queue

DLQ cho asynchronous Lambda thường nhận event thất bại, giúp điều tra hoặc replay.

### Destination

Destination nhận invocation record phong phú hơn, có context, request và response/error metadata. Có thể cấu hình cho success và failure.

Destination có thể trỏ tới service được hỗ trợ như SQS, SNS, EventBridge hoặc Lambda; on-failure còn có lựa chọn lưu record theo capability hiện hành.

Không chỉ cấu hình destination rồi quên:

- Destination permission có thể sai.
- Payload có thể vượt giới hạn.
- Destination cũng có thể lỗi.
- Cần monitor delivery failure.
- Replay phải idempotent.

## 20. Event source mapping

Event source mapping là Lambda resource có poller đọc nguồn và invoke function theo batch.

Nguồn phổ biến:

- SQS.
- Kinesis Data Streams.
- DynamoDB Streams.
- MSK.
- Self-managed Kafka.
- Amazon MQ.
- DocumentDB change streams theo hỗ trợ.

~~~text
Queue/Stream
    ↓ Lambda pollers
Batch records
    ↓ synchronous invoke nội bộ
Function
~~~

Event source mapping xử lý at least once; duplicate có thể xảy ra.

Các tham số quan trọng:

- Batch size.
- Batching window.
- Starting position cho stream.
- Maximum concurrency hoặc parallelization.
- Filter criteria.
- Retry.
- Record age.
- Bisect batch.
- Partial batch response.
- On-failure destination theo source.

## 21. SQS event source mapping

~~~text
SQS
  ↓ poll
Lambda batch
  ↓
Handler
  ├── success → message bị xóa
  └── failure → message hiện lại sau visibility timeout
~~~

Cần thiết kế:

- Visibility timeout dài hơn thời gian xử lý có margin.
- DLQ và maxReceiveCount.
- Batch size phù hợp.
- Partial batch response để không retry record đã thành công.
- Reserved/maximum concurrency để bảo vệ downstream.
- Idempotency vì message có thể giao lại.
- Theo dõi ApproximateAgeOfOldestMessage.

Nếu toàn batch fail vì một message độc, các message tốt có thể bị xử lý lại nhiều lần nếu không dùng partial batch handling.

## 22. Kinesis và DynamoDB Streams

Stream được chia thành shard. Ordering được bảo toàn trong phạm vi shard theo semantics nguồn.

Nếu batch lỗi:

- Processing shard có thể bị chặn.
- IteratorAge tăng.
- Có thể retry đến khi record hết hạn nếu không giới hạn.
- Bisect batch giúp cô lập record lỗi.
- Maximum record age/retry attempts giúp tránh kẹt vô hạn.
- On-failure destination giữ thông tin record bị bỏ.

Tăng concurrency không vượt qua mọi giới hạn shard. Phải hiểu số shard, parallelization factor và ordering requirement.

## 23. Direct trigger và event source mapping

| Đặc điểm | Direct trigger | Event source mapping |
|---|---|---|
| Ai đẩy/đọc event? | Source push/invoke | Lambda poll |
| Ví dụ | S3, SNS, EventBridge | SQS, Kinesis, DynamoDB Streams |
| Batch | Tùy source, thường event push | Cấu hình batch |
| Retry ownership | Source/Lambda async model | Queue/stream mapping |
| Backlog ở đâu? | Source/internal async queue | Queue/stream |
| Metric cần xem | Async age/error/destination | Queue age/iterator age/batch error |

Đọc tài liệu của đúng event source; không áp dụng retry của S3 cho SQS hoặc stream.

---

# Phần V — Concurrency và scaling

## 24. Concurrency là gì?

Concurrency là số invocation đang thực thi đồng thời.

Ước lượng:

~~~text
Concurrency ≈ requests mỗi giây × average duration giây
~~~

Ví dụ:

~~~text
200 request/giây × 0,4 giây
≈ 80 concurrent executions
~~~

Dùng P95/P99 và burst khi capacity planning, không chỉ average.

## 25. Một execution environment xử lý thế nào?

Trong execution model Lambda chuẩn, một environment xử lý một invocation tại một thời điểm. Khi cần xử lý đồng thời nhiều invocation, Lambda tạo/reuse nhiều environment.

~~~text
Invocation A → Environment 1
Invocation B → Environment 2
Invocation C → Environment 3
~~~

Các capability mới như Lambda Managed Instances có multi-concurrency khác; không áp dụng mô hình đó ngược lại cho Lambda chuẩn.

## 26. Account concurrency

Account có concurrency quota theo Region. Nhiều function chia sẻ phần unreserved concurrency.

~~~text
Account concurrency pool
├── Function A
├── Function B
├── Function C
└── phần được giữ lại cho function không reserved
~~~

Một function burst có thể làm function khác bị throttled nếu không có isolation.

New account có thể có quota thấp hơn và AWS có thể điều chỉnh theo usage. Kiểm tra Service Quotas trước production.

Lambda giữ lại 100 đơn vị concurrency trong unreserved pool cho các function không có reserved concurrency. Vì vậy nếu account quota là 1.000, bạn không thể reserve toàn bộ 1.000 cho một function; mức có thể reserve/provision theo quy tắc này tối đa là phần unreserved account concurrency trừ 100, sau khi tính các allocation khác.

## 27. Reserved concurrency

Reserved concurrency có hai tác dụng đồng thời:

1. Dành capacity cho function.
2. Đặt maximum concurrency cho function.

Ví dụ reserved concurrency = 50:

- Function có thể dùng tối đa 50 concurrent executions.
- 50 capacity được tách khỏi shared pool.
- Nếu cần 51, invocation dư bị throttle/backlog theo invocation model.

Use case:

- Bảo vệ RDS chỉ chịu số connection nhất định.
- Cách ly function quan trọng.
- Chặn runaway recursion.
- Giới hạn consumer throughput.
- Dành capacity cho API critical.

Đặt reserved concurrency = 0 có thể tạm dừng invocation, nhưng behavior event async/queue cần hiểu rõ để không mất hoặc route event ngoài ý muốn.

## 28. Provisioned concurrency

Provisioned concurrency tạo sẵn initialized environments cho version hoặc alias.

~~~text
Provisioned concurrency = 20
→ 20 environments initialized, sẵn sàng
~~~

Phù hợp:

- API latency-sensitive.
- Runtime init nặng.
- Traffic có baseline/predictable peak.
- Cold start tail latency không chấp nhận được.

Trade-off:

- Trả phí cho capacity provisioned.
- Cần cấu hình trên version/alias, không phải $LATEST.
- Trigger phải invoke đúng alias/version.
- Traffic vượt mức provisioned có thể spill over sang on-demand và gặp cold start.
- Cần Application Auto Scaling khi demand thay đổi.

Reserved concurrency không làm environment warm. Provisioned concurrency không thay authorization hoặc downstream protection.

## 29. Scaling speed và throttling

Lambda không tăng vô hạn tức thời. Có quota scaling environment theo function và quota concurrency account.

Khi throttle:

- Synchronous caller nhận lỗi/throttle response.
- Async event chờ trong internal queue và có thể retry.
- SQS message vẫn ở queue/hiện lại.
- Stream backlog làm IteratorAge tăng.

Metric cần xem:

- ConcurrentExecutions.
- UnreservedConcurrentExecutions.
- Throttles.
- ClaimedAccountConcurrency.
- ProvisionedConcurrencyUtilization.
- ProvisionedConcurrencySpilloverInvocations.
- AsyncEventAge.
- IteratorAge.
- SQS queue age/depth.

## 30. Bảo vệ downstream

Lambda có thể scale nhanh hơn database hoặc API đối tác.

~~~text
API Gateway throttle
        ↓
Lambda reserved concurrency
        ↓
RDS Proxy / connection limit
        ↓
RDS
~~~

Không giải quyết database bottleneck bằng cách tăng concurrency mù quáng.

Với SQS:

~~~text
Producer burst → SQS buffer → Lambda consume có kiểm soát
~~~

Queue giúp tách arrival rate và processing rate.

---

# Phần VI — Cold start và performance

## 31. Cold start gồm những gì?

Cold start có thể gồm:

- Provision execution environment.
- Tải code/image.
- Khởi động runtime.
- Khởi động extension.
- Chạy static initialization.
- Attach network resources theo configuration.
- Restore snapshot nếu dùng SnapStart.

CloudWatch log có thể hiển thị Init Duration theo execution type/runtime.

Cold start thường ảnh hưởng mạnh hơn:

- Latency P99.
- Function ít được gọi.
- Traffic burst.
- Runtime init nặng.
- Package/dependency lớn.
- VPC/dependency setup phức tạp.
- Version mới vừa deploy.

## 32. Cách giảm cold start

- Giảm package và dependency.
- Chỉ import module cần thiết.
- Tối ưu code ngoài handler.
- Reuse SDK/HTTP/database client.
- Chọn memory đủ để init nhanh.
- Tránh extension không cần.
- Dùng architecture/runtime phù hợp.
- Dùng SnapStart khi hỗ trợ.
- Dùng provisioned concurrency cho latency nghiêm ngặt.
- Load test cold và warm path.
- Tránh VPC nếu không cần private resource.

Warm-up ping không đảm bảo mọi environment đều warm và có thể tạo chi phí/complexity. Nó không thay provisioned concurrency.

## 33. Memory và CPU

Lambda memory không chỉ quyết định RAM. CPU và một số tài nguyên tăng theo memory.

Một function CPU-bound có thể chạy nhanh hơn đáng kể khi tăng memory:

~~~text
Memory thấp:
  giá mỗi ms thấp
  duration dài

Memory cao:
  giá mỗi ms cao hơn
  duration ngắn hơn

Cần đo tổng cost + latency
~~~

Dùng benchmark hoặc Lambda Power Tuning. Không mặc định memory thấp nhất là rẻ nhất.

## 34. Timeout

Timeout là thời gian tối đa cho một invocation.

Đặt timeout:

- Lớn hơn thời gian xử lý bình thường có margin.
- Nhỏ hơn timeout caller khi cần trả lỗi có kiểm soát.
- Phù hợp downstream timeout.
- Không quá lớn khiến invocation treo giữ concurrency.
- Phù hợp visibility timeout nếu đọc SQS.

~~~text
Client timeout
  > API Gateway budget
      > Lambda expected duration
          > downstream SDK timeout
~~~

Đây là tư duy budget, không phải công thức cứng cho mọi integration.

## 35. Ephemeral storage /tmp

/tmp là storage tạm gắn với execution environment.

Dùng cho:

- Unzip.
- Image/video intermediate file.
- Model temporary cache.
- Download trước khi transform.
- Local scratch data.

Không dùng làm durable state. File có thể còn khi warm reuse nhưng mất khi environment bị thu hồi.

Cần:

- Unique path theo request.
- Dọn file.
- Không lưu secret không cần thiết.
- Giám sát disk usage.
- Cấu hình dung lượng phù hợp.
- Tính thêm cost cho phần storage bổ sung theo pricing hiện hành.

## 36. SnapStart

SnapStart khởi tạo function khi publish version, tạo snapshot của initialized execution environment rồi restore snapshot khi cần environment mới.

Phù hợp function có init nặng và runtime được hỗ trợ.

Tại thời điểm tài liệu này, SnapStart hỗ trợ các managed runtime chính được AWS nêu như:

- Java 11 trở lên.
- Python 3.12 trở lên.
- .NET 8 trở lên.

Các ràng buộc đáng nhớ:

- Dùng published version/alias, không dùng $LATEST.
- Không kết hợp với provisioned concurrency.
- Một số feature/storage không tương thích.
- Code sau restore phải xử lý uniqueness.
- Connection, credential và secret tạo trước snapshot có thể cần refresh.
- Random value/UUID phải được tạo đúng thời điểm để không bị reuse sai.

Provisioned concurrency cho latency nghiêm ngặt nhất; SnapStart giảm init latency với mô hình khác. Chọn sau benchmark.

---

# Phần VII — IAM và security identity

## 37. Execution role

Execution role là role function assume khi code chạy.

~~~text
Lambda service
   ↓ assume role
Function code
   ↓ temporary credentials
S3 / DynamoDB / SQS / Secrets Manager
~~~

Execution role gồm:

- Trust policy cho Lambda service.
- Permission policy cho đúng action/resource.
- KMS permission khi decrypt.
- VPC network interface permission khi cấu hình cần.
- Log permission.

Không dùng access key tĩnh trong code/environment.

## 38. Resource-based policy

Resource-based policy trên function quyết định principal/service nào được invoke function.

~~~text
S3 bucket
  ↓ lambda:InvokeFunction allowed by function policy
Lambda
~~~

Execution role và resource policy là hai chiều khác nhau:

| Câu hỏi | Cơ chế |
|---|---|
| Ai được gọi Lambda? | Resource-based policy hoặc caller identity policy |
| Lambda được gọi service nào? | Execution role |

Lỗi phổ biến là thêm S3 permission vào execution role rồi nghĩ S3 đã được phép invoke function.

Dùng SourceArn và SourceAccount để giảm confused deputy risk khi service invoke.

## 39. Service-linked hoặc event source permissions

Với event source mapping, Lambda poll nguồn bằng execution role. Ví dụ SQS consumer cần quyền receive/delete/get attributes trên queue.

Với direct trigger, source service thường cần invoke permission trên function.

Luôn xem integration flow:

~~~text
Push source:
source → invoke Lambda

Polling source:
Lambda poller → đọc source → invoke function
~~~

## 40. Environment variables và KMS

Environment variable phù hợp:

- Feature flag.
- Table/bucket name.
- Endpoint.
- Log level.
- ARN/reference tới secret.

Không phù hợp để hard-code:

- Password.
- Long-lived token.
- Private key.
- Access key.

Environment variable được mã hóa at rest, nhưng người có quyền đọc function configuration có thể thấy giá trị tùy permission. Dùng Secrets Manager/Parameter Store cho secret lifecycle.

KMS customer managed key hữu ích khi cần kiểm soát key policy/audit, nhưng làm IAM và deployment phức tạp hơn.

## 41. Secrets

Pattern:

~~~text
Lambda
  ↓ execution role
Secrets Manager
  ↓ current secret
in-memory cache có TTL
~~~

Cần xử lý:

- Rotation.
- Cache expiry.
- Authentication failure sau rotation.
- Network access nếu function ở VPC.
- Least privilege theo secret ARN.
- Không log secret.
- Không đưa secret vào deployment package/layer/image.
- Không giữ secret snapshot không an toàn với SnapStart.

## 42. Code signing và supply chain

Security không dừng ở IAM.

- Pin dependency.
- Scan CVE.
- Dùng artifact repository.
- Ký code package khi compliance yêu cầu.
- Kiểm tra source commit → artifact → deployed version.
- Không deploy từ laptop không truy vết.
- Scan container image trong ECR.
- Patch runtime/base image.
- Loại bỏ package không dùng.

---

# Phần VIII — Networking và VPC

## 43. Lambda mặc định có network thế nào?

Lambda không gắn vào VPC của bạn vẫn có thể gọi public AWS endpoints và Internet theo networking do Lambda service quản lý, miễn IAM và endpoint policy cho phép.

Nếu cần gọi RDS/ElastiCache/internal service có private IP, attach function vào VPC.

## 44. Lambda trong VPC

Bạn chọn:

- VPC.
- Subnets.
- Security Groups.

Lambda dùng Hyperplane ENI để kết nối vào VPC theo cấu hình.

~~~text
Lambda service
   ↓ managed networking
Hyperplane ENI
   ↓
Private subnet
   ├── RDS
   ├── ElastiCache
   └── internal service
~~~

Function không cần inbound rule để nhận invocation từ Lambda service. Invocation đi qua Lambda control/data plane; Security Group chủ yếu kiểm soát network traffic function khởi tạo tới resource.

## 45. Lambda trong public subnet không tự có Internet

Gắn Lambda vào public subnet không cấp public IP cho execution environment.

Muốn outbound Internet theo IPv4, thường cần:

~~~text
Lambda private subnet
   ↓ route 0.0.0.0/0
NAT Gateway
   ↓
Internet Gateway
   ↓
Internet
~~~

Hoặc dùng VPC endpoint cho AWS service:

~~~text
Lambda → S3 Gateway Endpoint
Lambda → Secrets Manager Interface Endpoint
Lambda → CloudWatch/API endpoint phù hợp
~~~

Chọn NAT hay endpoint theo service cần gọi, availability, data path và cost.

## 46. Security Group

Ví dụ Lambda gọi RDS:

~~~text
Lambda SG
  outbound TCP 5432
       ↓
RDS SG
  inbound TCP 5432 from Lambda SG
~~~

Dùng SG reference thay CIDR rộng khi phù hợp. NACL, route và DNS cũng phải đúng.

## 47. Database connection

Lambda burst có thể tạo hàng nghìn connection.

Giải pháp:

- RDS Proxy.
- Reserved concurrency.
- Reuse connection trong environment.
- Connection timeout ngắn.
- Pool size rất nhỏ/phù hợp execution model.
- Backoff.
- Database capacity planning.
- Aurora/DynamoDB theo access pattern nếu phù hợp.

RDS Proxy giúp pool và multiplex connection, nhưng không sửa query chậm hoặc transaction design kém.

## 48. EFS

Lambda có thể mount EFS khi cần shared POSIX filesystem theo capability hỗ trợ.

Use case:

- Shared model/library/data.
- File lớn không phù hợp package.
- Legacy app cần filesystem semantics.

Trade-off:

- VPC networking.
- EFS performance mode/throughput.
- Mount latency.
- IAM/POSIX permission.
- Availability/cost.
- Không thay S3 cho object storage.

---

# Phần IX — Versions, aliases và deployment

## 49. $LATEST và published version

$LATEST là mutable working version. Khi publish, Lambda tạo immutable numbered version.

~~~text
$LATEST → đang phát triển
Version 1 → immutable
Version 2 → immutable
Version 3 → immutable
~~~

Version snapshot gồm code và phần lớn configuration có liên quan tại thời điểm publish.

Production nên invoke alias/version thay vì phụ thuộc $LATEST.

## 50. Alias

Alias là tên ổn định trỏ tới version:

~~~text
dev  → version 7
test → version 9
prod → version 12
~~~

Lợi ích:

- Stable ARN.
- Promotion.
- Rollback.
- Provisioned concurrency.
- Weighted routing.
- Environment separation ở mức function release.

Alias không thay account/environment isolation.

## 51. Weighted alias và canary

Alias có thể chia traffic giữa hai version:

~~~text
prod alias
├── 90% → version 12
└── 10% → version 13
~~~

Theo dõi:

- Errors.
- Duration.
- Throttles.
- Business metrics.
- Downstream saturation.

Weighted routing mang tính xác suất; traffic thấp có thể phân bố không đều trong khoảng ngắn.

## 52. CodeDeploy và SAM deployment preference

CodeDeploy/SAM có thể tự động hóa:

- Canary.
- Linear rollout.
- Pre-traffic hook.
- Post-traffic hook.
- CloudWatch alarm rollback.

Deployment code thành công không có nghĩa database migration tương thích. Thay đổi schema phải backward-compatible với cả version cũ và mới trong rollout.

## 53. Environment strategy

Các lựa chọn:

- Function/alias riêng.
- Stack riêng.
- Account riêng.
- Region riêng.

Production quan trọng nên ưu tiên account/stack boundary rõ hơn chỉ alias. Không để dev role sửa production function.

---

# Phần X — HTTP entry points

## 54. API Gateway và Lambda

~~~text
Client → API Gateway → Lambda → response
~~~

API Gateway thêm:

- Routing.
- Authorizer.
- Throttling.
- Usage plan theo API type.
- Validation/mapping.
- Custom domain.
- WAF theo capability.
- Logging/metrics.

Lambda phải trả proxy response đúng contract. API Gateway timeout có thể ngắn hơn Lambda timeout; function có thể tiếp tục sau khi client đã nhận timeout.

## 55. Lambda Function URL

Function URL là HTTPS endpoint riêng cho function.

Auth type:

- AWS_IAM.
- NONE.

Phù hợp:

- Webhook đơn giản.
- Internal IAM-signed endpoint.
- Streaming response.
- Prototype hoặc endpoint không cần API management nâng cao.

Không có đầy đủ feature của API Gateway như usage plan, mapping, authorizer phong phú hoặc route management.

Auth NONE có thể tạo public endpoint. Phải bảo vệ bằng resource policy, application auth, CloudFront/WAF architecture hoặc lựa chọn API Gateway theo requirement.

## 56. Response streaming

Response streaming giúp:

- Giảm time to first byte.
- Trả response lớn hơn buffered response theo quota hỗ trợ.
- Stream progress/data.

Cần nhớ:

- Client phải hỗ trợ streaming.
- Function vẫn bị tính duration khi stream.
- Client disconnect không chắc dừng function.
- Bandwidth sau ngưỡng có giới hạn.
- Không dùng streaming thay asynchronous job cho business process dài.
- Kiểm tra Region và integration support.

---

# Phần XI — Error handling và idempotency

## 57. Phân loại lỗi

- Validation error: không retry.
- Authorization error: không retry cho đến khi credential/policy đổi.
- Not found/business conflict: xử lý theo contract.
- Throttling: retry có backoff/jitter.
- Network timeout: có thể retry nhưng side effect có thể đã xảy ra.
- Service unavailable: retry giới hạn.
- Code bug: không retry vô hạn.
- Poison message: cô lập vào DLQ/on-failure destination.

Retry decision phải dựa trên loại lỗi, không chỉ mọi exception.

## 58. Idempotency

At-least-once delivery nghĩa cùng event có thể được xử lý nhiều lần.

Pattern DynamoDB:

~~~text
Event có idempotencyKey
      ↓
Conditional Put nếu key chưa tồn tại
      ├── thành công → xử lý
      └── đã tồn tại → trả kết quả cũ/bỏ qua
~~~

Cần lưu:

- Key.
- Status: IN_PROGRESS/COMPLETED/FAILED.
- Result reference.
- Expiry/TTL.
- Lock timeout nếu invocation chết giữa chừng.

Idempotency key phải đại diện cùng business operation, không tạo random key mới ở mỗi retry.

## 59. Timeout không biết chắc kết quả

Ví dụ:

1. Lambda gọi payment service.
2. Payment service charge thành công.
3. Network timeout trước khi response về.
4. Lambda retry.
5. Khách hàng có thể bị charge hai lần.

Giải pháp:

- Gửi idempotency key xuống payment service.
- Query trạng thái trước retry khi protocol hỗ trợ.
- Transaction/outbox.
- Conditional write.
- Reconciliation.

## 60. Partial batch failure

Với SQS batch, handler có thể trả danh sách item thất bại theo integration support.

~~~text
Batch: A B C D
A success
B success
C fail
D success

Partial response → chỉ C được retry
~~~

Nếu không dùng, cả batch có thể retry và A/B/D bị xử lý lại.

## 61. DLQ không phải thùng rác

Mỗi DLQ cần:

- Alarm.
- Dashboard.
- Owner.
- Runbook.
- Retention.
- Replay tool/process.
- Idempotency.
- Cơ chế phân loại poison message.
- Quyền truy cập và encryption.

DLQ không được theo dõi chỉ trì hoãn mất dữ liệu.

---

# Phần XII — Observability

## 62. CloudWatch metrics

Metric cốt lõi:

- Invocations.
- Errors.
- Duration.
- Throttles.
- ConcurrentExecutions.
- AsyncEventAge.
- AsyncEventsDropped.
- IteratorAge.
- DeadLetterErrors.
- DestinationDeliveryFailures.
- ProvisionedConcurrencyInvocations.
- ProvisionedConcurrencySpilloverInvocations.
- ProvisionedConcurrencyUtilization.

Luôn ghép Lambda metric với source/downstream metric.

Ví dụ SQS:

- Queue depth.
- Oldest message age.
- Lambda concurrency.
- Errors/throttles.
- DLQ depth.

## 63. Logs

Lambda gửi log đến CloudWatch Logs khi role có quyền.

Log nên có:

- AWS request ID.
- Correlation ID.
- Event type.
- Business key không nhạy cảm.
- Function version/alias.
- Attempt/retry context.
- Error category.
- Downstream latency.
- Result status.

Không log toàn bộ event mặc định. Event có thể chứa token, PII, S3 object metadata hoặc secret.

Structured JSON giúp Logs Insights và metric extraction.

## 64. Embedded metrics và custom metrics

Custom metric nên dùng cho business/technical signal quan trọng:

- OrdersProcessed.
- PaymentDeclined.
- RecordsFailed.
- DependencyLatency.
- IdempotencyHit.
- CacheHit.
- BatchPartialFailure.

Không tạo dimension cardinality cao như requestId/userId vì tăng cost và khó quản lý.

## 65. X-Ray và tracing

Tracing giúp thấy:

~~~text
API Gateway
   ↓
Lambda init/invoke
   ↓
DynamoDB / HTTP dependency
~~~

Trace không thay log/metric. Sampling cần cân bằng observability và cost. Propagate correlation/trace context qua queue khi kiến trúc hỗ trợ.

## 66. Alarm

Alarm nên phát hiện:

- Error rate, không chỉ error count.
- Throttle.
- Duration gần timeout.
- AsyncEventAge tăng.
- IteratorAge tăng.
- SQS oldest message age.
- DLQ message > 0.
- Destination delivery failure.
- Provisioned concurrency spillover.
- Không có invocation khi đáng lẽ có.

---

# Phần XIII — Security

## 67. Security checklist

- Execution role least privilege.
- Resource-based invoke policy giới hạn source.
- Không hard-code secret.
- Encrypt data/config theo requirement.
- Validate mọi event.
- Sanitize output.
- Không log PII/token.
- Patch runtime và dependency.
- Scan package/container image.
- Dùng VPC khi cần private resource, không chỉ để “an toàn hơn”.
- Giới hạn concurrency để bảo vệ downstream.
- Dùng code signing khi compliance yêu cầu.
- Tách account/environment.
- Dùng version/alias và artifact bất biến.
- Theo dõi CloudTrail configuration change.
- Bảo vệ Function URL.
- Tránh recursive invocation.
- Kiểm soát extension/layer của bên thứ ba.

## 68. Recursive loop

Ví dụ lỗi:

~~~text
S3 ObjectCreated
   ↓
Lambda xử lý
   ↓
ghi file trở lại cùng prefix
   ↓
S3 ObjectCreated
   ↓ lặp vô hạn
~~~

Giải pháp:

- Input/output bucket khác.
- Prefix/suffix filter.
- Idempotency marker.
- EventBridge rule filter.
- Reserved concurrency/emergency stop.
- Alarm cost/invocation spike.
- Lambda recursive loop detection khi hỗ trợ, nhưng không phụ thuộc duy nhất vào nó.

## 69. Shared responsibility

AWS quản lý nền tảng; bạn vẫn quản lý:

- Code vulnerability.
- Dependency.
- IAM.
- Event validation.
- Data protection.
- Secret.
- Network route/SG.
- Logging.
- Retry.
- Compliance.
- Runtime upgrade trước deprecation.

---

# Phần XIV — Quotas và giới hạn

## 70. Tại sao quota quan trọng?

Một serverless architecture vẫn có giới hạn. Effective capacity là giới hạn nhỏ nhất giữa:

~~~text
Event source
→ API Gateway/source quota
→ Lambda concurrency/scaling
→ payload/timeout
→ downstream capacity
~~~

Quota có thể thay đổi theo Region/account. Luôn kiểm tra Service Quotas trước production.

## 71. Giới hạn Lambda chuẩn cần nhớ

| Resource | Giá trị phổ biến hiện hành | Có thể tăng? |
|---|---:|---|
| Standard function timeout | 900 giây, tức 15 phút | Không |
| Memory | 128 MB đến 10.240 MB | Không vượt range |
| Ephemeral storage /tmp | 512 MB đến 10.240 MB | Cấu hình trong range |
| Synchronous request payload | 6 MB | Không |
| Synchronous buffered response | 6 MB | Không |
| Synchronous streamed response | tối đa 200 MB | Không theo quota hiện hành |
| Asynchronous invocation payload | 1 MB | Không |
| Environment variables tổng | 4 KB | Không |
| Layers | tối đa 5 | Không |
| ZIP upload trực tiếp | 50 MB zipped | Không |
| ZIP content + layers | 250 MB unzipped | Không |
| Container image | 10 GB uncompressed | Không |
| Function resource policy | 20 KB | Không |
| Default account concurrency | thường 1.000 mỗi Region | Có |
| Scaling của một function | đến 1.000 environment mỗi 10 giây | Theo quota hiện hành |

Một số account mới có concurrency/memory quota thấp hơn. Không dựa vào default khi capacity planning.

## 72. Quota interaction với API Gateway

API Gateway có thể nhận nhiều request hơn Lambda concurrency mặc định.

Ví dụ:

~~~text
API Gateway peak: 10.000 RPS
Lambda duration: 1 giây
Required concurrency gần: 10.000
Account concurrency: 1.000

→ Lambda throttle trước
~~~

Giải pháp:

- Xin tăng concurrency.
- Giảm duration.
- API throttling.
- Cache.
- Async queue.
- Scale downstream.
- Multi-Region nếu requirement/data cho phép.

API Gateway payload có thể lớn hơn Lambda synchronous payload. Effective body còn phải trừ event envelope. File lớn nên upload trực tiếp S3 qua pre-signed URL.

## 73. Khi vượt giới hạn

| Tình huống | Biểu hiện | Giải pháp |
|---|---|---|
| Concurrency hết | Throttles | Quota/reserved/optimize/queue |
| Timeout | Task timed out | Tối ưu hoặc đổi async/ECS/Batch |
| Payload lớn | Invoke/API error | S3 reference/pre-signed URL |
| Package lớn | Deploy fail | Giảm dependency/image/EFS |
| /tmp đầy | I/O error | Dọn file/tăng storage/S3 |
| Env vars quá lớn | Update fail | AppConfig/S3/Parameter Store |
| Policy quá lớn | AddPermission fail | Gộp statement/điều chỉnh policy |
| Stream batch lỗi | IteratorAge tăng | Partial/bisect/retry destination |
| SQS consumer chậm | Queue age tăng | Concurrency/batch/optimize |

---

# Phần XV — Cost optimization

## 74. Lambda tính phí theo gì?

Tổng cost có thể gồm:

- Request.
- Duration theo memory/configuration.
- Provisioned concurrency.
- Ephemeral storage bổ sung.
- Data transfer.
- CloudWatch Logs.
- X-Ray.
- NAT Gateway.
- Secrets Manager.
- EFS.
- API Gateway.
- Downstream services.

Không đánh giá Lambda cost riêng lẻ mà bỏ log, NAT và API Gateway.

## 75. Tối ưu cost

- Right-size memory bằng benchmark.
- Giảm duration.
- Batch event hợp lý.
- Dùng Arm nếu tương thích và benchmark tốt.
- Tránh log quá nhiều.
- Đặt log retention.
- Cache SDK client/secret an toàn.
- Dùng direct service integration khi Lambda chỉ chuyển tiếp.
- SQS buffer để tránh overprovision/downstream failure.
- Provisioned concurrency chỉ cho latency cần thiết.
- Xóa old versions/layers nếu không cần.
- Chọn NAT hoặc VPC endpoints theo traffic/cost.
- Theo dõi cost per business transaction.

Lambda có thể rẻ cho traffic không đều; workload compute liên tục quy mô lớn cần so sánh ECS/EC2.

---

# Phần XVI — Kiến trúc mẫu

## 76. Serverless API

~~~text
Route 53
  ↓
API Gateway
  ↓ JWT/IAM authorizer
Lambda alias prod
  ├── DynamoDB
  ├── S3
  └── EventBridge
~~~

Cần:

- Timeout budget.
- Reserved concurrency.
- Idempotency.
- Correct HTTP errors.
- Access/application logs.
- Version/alias deployment.
- Downstream capacity.

## 77. S3 processing

~~~text
S3 ObjectCreated
   ↓ asynchronous
Lambda
   ├── validate bucket/key
   ├── idempotency
   ├── process
   └── write output sang prefix/bucket khác
        ↓
   failure destination/DLQ
~~~

Object key trong event có thể encoded; object có thể bị ghi đè/xóa trước khi function đọc. Dùng version ID/eTag khi cần consistency.

## 78. SQS worker

~~~text
Producer → SQS → Lambda batch → database/API
                │
                ├── partial batch failure
                ├── reserved/max concurrency
                └── DLQ
~~~

Tối ưu:

- Batch size.
- Visibility timeout.
- Idempotency.
- Backpressure.
- Queue age alarm.
- Downstream protection.

## 79. Stream processing

~~~text
DynamoDB Streams / Kinesis
       ↓ shard ordered records
Lambda batch
       ├── checkpoint
       ├── retry/bisect
       └── on-failure destination
~~~

Theo dõi IteratorAge. Một poison record có thể chặn shard nếu retry vô hạn.

## 80. Scheduled automation

~~~text
EventBridge Scheduler
       ↓
Lambda
       ├── cleanup
       ├── reconciliation
       └── report trigger
~~~

Scheduler cần execution role, retry/DLQ và timezone đúng. Scheduled delivery vẫn có thể duplicate; handler phải idempotent.

## 81. Fan-out

~~~text
Producer
   ↓
SNS / EventBridge
   ├── Lambda A
   ├── Lambda B
   └── SQS → Lambda C
~~~

Dùng SQS giữa event bus/topic và Lambda khi consumer cần durable buffer, độc lập retry hoặc kiểm soát tốc độ.

## 82. Orchestration

Nếu business flow gồm nhiều bước:

~~~text
Không nên:
Lambda lớn chứa retry/wait/state mọi bước

Nên:
Step Functions
├── Validate Lambda
├── Charge task
├── Reserve task
└── Notify task
~~~

Step Functions hiển thị state, retry/catch và audit rõ hơn. Mỗi task vẫn cần idempotent.

---

# Phần XVII — Troubleshooting

## 83. Function timeout

Kiểm tra:

- Duration P95/P99.
- Downstream latency.
- DNS/network.
- SDK timeout/retry.
- Cold init.
- Deadlock.
- File I/O.
- Memory/CPU.
- Database connection.
- Payload size.
- Remaining time log.

Không chỉ tăng timeout; tìm bottleneck và side effect sau client timeout.

## 84. Throttling

Kiểm tra:

- Reserved concurrency.
- Account concurrency.
- Provisioned concurrency spillover.
- Scaling rate.
- Event source maximum concurrency.
- Downstream backpressure.
- Recursive invocation.
- API/source retry.

Xem Throttles, ConcurrentExecutions và source backlog cùng nhau.

## 85. Không truy cập được Internet khi vào VPC

Kiểm tra:

1. Function chọn private subnet phù hợp.
2. Route 0.0.0.0/0 tới NAT Gateway.
3. NAT nằm public subnet có route tới Internet Gateway.
4. Security Group outbound.
5. NACL.
6. DNS resolution/hostnames.
7. IPv6 egress nếu dùng.
8. VPC endpoint cho AWS service nếu không dùng NAT.

Đưa Lambda vào public subnet không tự cấp public IP.

## 86. AccessDenied

Xác định chiều:

- Trigger không invoke được Lambda → resource-based policy/caller permission.
- Code Lambda không gọi được service → execution role.
- KMS decrypt fail → IAM và key policy.
- S3/SQS policy deny → resource policy/SCP/permission boundary.
- Cross-account → cả hai phía.
- VPC endpoint policy → network path policy.

CloudTrail và error ARN/action/resource giúp tìm statement thiếu.

## 87. SQS message xử lý lặp

Nguyên nhân:

- Visibility timeout quá ngắn.
- Handler timeout.
- Batch có một record fail.
- Delete không xảy ra vì invocation fail.
- Consumer duplicate theo at-least-once.
- Partial batch response chưa bật/đúng format.

Giải pháp không chỉ tăng visibility: thêm idempotency, DLQ và partial batch handling.

## 88. Stream IteratorAge tăng

- Function error retry batch.
- Poison record.
- Concurrency/shard không đủ.
- Downstream chậm.
- Throttle.
- Batch quá lớn.
- Parallelization/order constraint.

Dùng bisect, max retry/age và destination phù hợp.

## 89. Cold start cao

- Package/image lớn.
- Static init nặng.
- Extension.
- Runtime.
- Memory thấp.
- VPC/dependency.
- Version mới.
- Traffic burst.

Đo Init Duration; thử memory, package tối ưu, SnapStart hoặc provisioned concurrency.

## 90. No logs

- Execution role thiếu logs permission.
- Function chưa được invoke.
- Sai Region/account/log group.
- Log level/filter.
- Function fail trước log setup.
- CloudWatch ingestion delay/quota.
- Extension/log destination config.

## 91. Function chạy thành công nhưng business data sai

Status invocation thành công chỉ nghĩa handler không báo lỗi. Cần kiểm tra:

- Validation.
- Idempotency.
- Transaction.
- Partial failure.
- Swallowed exception.
- Wrong environment/table.
- Event schema version.
- Eventual consistency.
- Duplicate/out-of-order event.

---

# Phần XVIII — Các khả năng mới cần nhận biết

## 92. Lambda Managed Instances

Lambda Managed Instances là capability mới hơn với compute/networking và multi-concurrency semantics khác Lambda chuẩn.

Điểm nhận biết:

- Một environment có thể xử lý nhiều request đồng thời.
- Code phải thread/process/async safe theo runtime.
- VPC/networking và runtime support có yêu cầu riêng.
- Execution duration và quota có thể khác.

Đối với SAA-C03 truyền thống, trọng tâm vẫn là Lambda chuẩn: một invocation tại một thời điểm trên mỗi environment, standard timeout và concurrency model ở các phần trên. Không trộn hai mô hình khi trả lời câu hỏi.

## 93. Durable Functions

Durable Functions thêm checkpoint/wait/resume và workflow duration dài hơn cho runtime được hỗ trợ.

Nó không làm kiến thức Step Functions hoặc standard Lambda mất giá trị. Khi chọn:

- Xem requirement orchestration.
- Audit/history.
- Service integration.
- Runtime portability.
- Retry/compensation.
- Duration.
- Pricing.
- Mức độ hỗ trợ Region.

Trong đề SAA-C03 sử dụng mô hình truyền thống, Step Functions vẫn là đáp án tiêu chuẩn cho orchestration nhiều bước khi câu hỏi mô tả state machine, retry/catch, wait hoặc human approval.

---

# Phần XIX — Bẫy thi SAA-C03

## 94. Những nhầm lẫn thường gặp

1. Serverless không có nghĩa không cần quản IAM, quota và monitoring.
2. Synchronous invocation không tự retry function error; caller quyết định.
3. Async invocation dùng internal queue và Lambda retry.
4. Event source mapping là Lambda poll source.
5. SQS retry do visibility/redrive semantics, không giống Lambda async queue.
6. Stream batch lỗi có thể chặn shard.
7. Event delivery có thể duplicate; function phải idempotent.
8. Reserved concurrency vừa reserve vừa giới hạn maximum.
9. Provisioned concurrency giảm cold start nhưng có cost.
10. Reserved concurrency không làm function warm.
11. SnapStart và provisioned concurrency không dùng cùng nhau.
12. Memory tăng kéo theo CPU; memory thấp nhất chưa chắc rẻ nhất.
13. /tmp có thể được reuse nhưng không durable.
14. Lambda trong public subnet không tự có Internet.
15. Execution role là quyền function đi ra; resource policy là quyền principal đi vào.
16. Function URL NONE có thể public.
17. Version immutable; alias là pointer ổn định.
18. Lambda container image vẫn chịu Lambda runtime model/quota.
19. DLQ phải có alarm và replay process.
20. API Gateway có thể timeout trước Lambda.
21. Standard Lambda tối đa 15 phút; job dài xem Step Functions/ECS/Batch hoặc capability phù hợp.
22. Lambda concurrency có thể làm RDS quá tải.
23. Base64 làm payload lớn hơn.
24. S3 event có thể tạo recursive loop nếu ghi lại cùng filter.
25. CloudWatch success không đảm bảo business transaction đúng.

## 95. Cách suy luận câu hỏi Lambda

~~~text
Event đến từ đâu?
├── API/client → synchronous
├── S3/SNS/EventBridge → thường asynchronous
└── SQS/Kinesis/DynamoDB Streams → event source mapping

Caller cần kết quả ngay?
├── Có → timeout budget + sync error
└── Không → queue/event + retry/DLQ

Có duplicate không?
└── Luôn thiết kế idempotent nếu delivery có thể retry

Downstream chịu bao nhiêu tải?
└── reserved/max concurrency + queue/proxy

Latency có nhạy cold start?
└── optimize init / SnapStart / provisioned concurrency

Cần private resource?
└── VPC + SG + route + NAT/endpoint

Job quá lâu hoặc nhiều bước?
└── async + Step Functions/ECS/Batch
~~~

---

# Phần XX — Lab thực hành

## 96. Lab 1: Synchronous API

- Tạo Lambda.
- Tạo API Gateway HTTP API.
- Thêm execution role tối thiểu.
- Trả response proxy đúng.
- Thêm timeout downstream.
- Log request ID.
- Test 2xx, 4xx, 5xx và timeout.
- Đặt reserved concurrency.
- Quan sát metrics.

## 97. Lab 2: S3 asynchronous processing

- Tạo input/output prefix hoặc bucket riêng.
- Tạo S3 notification.
- Cấp invoke permission đúng source.
- Parse bucket/key.
- Thêm idempotency.
- Cấu hình failure destination.
- Cố tình throw error.
- Quan sát retry và record thất bại.
- Kiểm tra không tạo recursive loop.

## 98. Lab 3: SQS worker

- Tạo queue và DLQ.
- Tạo event source mapping.
- Cấu hình batch.
- Đặt visibility timeout.
- Tạo một poison message.
- Thêm partial batch response.
- Theo dõi queue age.
- Test reserved/maximum concurrency.
- Replay DLQ có kiểm soát.

## 99. Lab 4: VPC và RDS

- Lambda ở private subnets nhiều AZ.
- Lambda SG gọi RDS SG.
- Dùng RDS Proxy nếu môi trường cho phép.
- Thử gọi public API không có NAT để thấy timeout.
- Thêm NAT hoặc endpoint phù hợp.
- Theo dõi connection.
- Không mở RDS public.

## 100. Lab 5: Version, alias và canary

- Deploy version 1.
- Tạo alias prod.
- Deploy version 2.
- Chia traffic có weight.
- Tạo alarm Errors.
- Rollback alias.
- Xác nhận API/trigger gọi alias, không gọi $LATEST.

## 101. Lab 6: Performance

- Đo cold/warm duration.
- Giảm dependency.
- Thử nhiều memory size.
- So sánh cost/duration.
- Thử SnapStart hoặc provisioned concurrency nếu runtime/budget cho phép.
- Theo dõi spillover.
- Ghi kết luận bằng số đo, không cảm giác.

---

# Phần XXI — Câu hỏi tự kiểm tra

## 102. Câu hỏi

### Câu 1

API Gateway gọi Lambda và chờ response. Đây là invocation gì, ai retry khi function lỗi?

**Đáp án:** Synchronous invocation. API Gateway/client/upstream quyết định retry; Lambda không tự retry function error như async invocation.

### Câu 2

S3 upload gọi Lambda, function lỗi. Mô hình nào?

**Đáp án:** Asynchronous invocation. Lambda queue event, retry theo async settings và có thể gửi failure record tới destination/DLQ.

### Câu 3

Lambda đọc SQS bằng cách nào?

**Đáp án:** Event source mapping của Lambda poll SQS, gom batch và invoke function.

### Câu 4

Reserved concurrency = 20 có nghĩa gì?

**Đáp án:** Dành 20 concurrency cho function và đồng thời giới hạn function tối đa 20 concurrent executions.

### Câu 5

Provisioned concurrency khác reserved concurrency thế nào?

**Đáp án:** Provisioned concurrency pre-initialize environments để giảm cold start; reserved concurrency cách ly và giới hạn capacity nhưng không pre-warm.

### Câu 6

Lambda đã gắn public subnet nhưng không gọi được Internet. Vì sao?

**Đáp án:** Lambda VPC không nhận public IP như EC2. Cần private subnet route qua NAT Gateway hoặc egress/IPv6 phù hợp; AWS service có thể dùng VPC endpoint.

### Câu 7

S3 có quyền PutObject trong execution role nhưng vẫn không invoke được Lambda. Vì sao?

**Đáp án:** Execution role là quyền code Lambda gọi ra. S3 invoke Lambda cần resource-based permission trên function và notification đúng.

### Câu 8

SQS visibility timeout ngắn hơn Lambda duration gây gì?

**Đáp án:** Message có thể hiện lại và bị xử lý đồng thời/trùng trước invocation đầu hoàn tất.

### Câu 9

Một stream record lỗi làm IteratorAge tăng liên tục. Giải pháp?

**Đáp án:** Giới hạn retry/record age, bisect batch, partial handling khi hỗ trợ, on-failure destination và sửa poison record; vẫn giữ idempotency/order requirement.

### Câu 10

Function cần latency P99 ổn định và init nặng. Chọn gì?

**Đáp án:** Tối ưu init/package/memory trước; dùng provisioned concurrency khi cần guarantee thấp nhất, hoặc SnapStart nếu runtime/feature/trade-off phù hợp.

### Câu 11

Lambda gọi RDS và burst làm hết connection. Chọn gì?

**Đáp án:** Reserved concurrency, RDS Proxy/connection management, timeout/backoff và database capacity planning.

### Câu 12

File upload 50 MB qua API Gateway → Lambda bị lỗi payload. Thiết kế lại?

**Đáp án:** Lambda/API cấp S3 pre-signed URL, client upload trực tiếp S3 rồi phát event xử lý.

### Câu 13

Function timeout sau khi payment thành công nhưng chưa nhận response. Nguy cơ?

**Đáp án:** Retry có thể charge trùng. Dùng idempotency key xuyên suốt tới payment provider và reconciliation.

### Câu 14

Tại sao production nên dùng alias?

**Đáp án:** Stable ARN, version promotion, rollback, weighted routing và provisioned concurrency trên version/alias.

### Câu 15

Lambda container image có chạy như ECS service không?

**Đáp án:** Không. Image chỉ là deployment format; function vẫn chạy theo Lambda invocation, timeout, concurrency và filesystem model.

---

# Phần XXII — Checklist

## 103. Checklist ôn thi

- [ ] Giải thích được Lambda và shared responsibility.
- [ ] Biết lifecycle Init, Invoke, Freeze/Reuse và Shutdown.
- [ ] Phân biệt state cache tạm và durable state.
- [ ] Phân biệt ZIP, layer và container image.
- [ ] Phân biệt synchronous, asynchronous và event source mapping.
- [ ] Biết ai retry và event thất bại đi đâu.
- [ ] Hiểu SQS visibility, DLQ và partial batch failure.
- [ ] Hiểu stream shard, IteratorAge, bisect và retry.
- [ ] Tính concurrency từ rate × duration.
- [ ] Phân biệt account, reserved và provisioned concurrency.
- [ ] Biết cách bảo vệ downstream.
- [ ] Hiểu cold start và static initialization.
- [ ] Biết memory ảnh hưởng CPU và cost.
- [ ] Hiểu timeout và /tmp.
- [ ] Biết SnapStart use case/constraint.
- [ ] Phân biệt execution role và resource-based policy.
- [ ] Quản lý environment variable, KMS và secret đúng.
- [ ] Hiểu Lambda VPC, NAT, endpoint và Security Group.
- [ ] Biết RDS Proxy và EFS use case.
- [ ] Hiểu version, alias và canary.
- [ ] Phân biệt API Gateway và Function URL.
- [ ] Thiết kế idempotency, DLQ và replay.
- [ ] Theo dõi metric, log, trace và alarm.
- [ ] Nhớ các quota quan trọng.
- [ ] Chọn S3 pre-signed URL cho file lớn.
- [ ] Chọn async/Step Functions/ECS/Batch cho job dài.
- [ ] Troubleshoot timeout, throttle, VPC và AccessDenied.
- [ ] Nhận biết nhưng không trộn execution model mới với Lambda chuẩn.

---

# 104. Bản tóm tắt một trang

~~~text
LAMBDA
event-driven compute, AWS quản infrastructure
bạn quản code, IAM, data, network, retry, quota, observability

LIFECYCLE
Init → Invoke → Freeze/Reuse → Shutdown
correctness không phụ thuộc warm environment

INVOCATION
Synchronous:
  caller chờ, caller/upstream retry

Asynchronous:
  Lambda internal queue, Lambda retry, destination/DLQ

Event source mapping:
  Lambda poll SQS/stream, batch, at-least-once

CONCURRENCY
concurrency ≈ RPS × duration
reserved    → reserve + hard max
provisioned → pre-initialized environments
queue       → buffer và backpressure

IAM
resource policy → ai được invoke Lambda
execution role  → Lambda được gọi service nào

NETWORK
không VPC → public service endpoints/Internet theo Lambda networking
trong VPC → private resources
Internet  → NAT/egress phù hợp
AWS API   → NAT hoặc VPC endpoint

STATE
/tmp/global cache → tạm thời
S3/DynamoDB/RDS   → durable

RELIABILITY
idempotency + timeout + retry + DLQ + alarm
SQS partial batch
stream IteratorAge/bisect/destination

DEPLOY
$LATEST → publish version → alias → canary/rollback

LIMITS CỐT LÕI CỦA STANDARD LAMBDA
timeout 15 phút
memory 128 MB–10.240 MB
/tmp 512 MB–10.240 MB
sync buffered payload 6 MB mỗi chiều
async payload 1 MB
container image 10 GB
default concurrency thường 1.000/Region

PATTERNS
file lớn → S3 pre-signed URL
job dài → queue + Step Functions/ECS/Batch
database → RDS Proxy + concurrency control
low latency → optimize init + SnapStart/provisioned concurrency
~~~

# 105. Tài liệu AWS chính thức

- [AWS Lambda Developer Guide](https://docs.aws.amazon.com/lambda/latest/dg/welcome.html)
- [Lambda execution environment lifecycle](https://docs.aws.amazon.com/lambda/latest/dg/lambda-runtime-environment.html)
- [Lambda invocation methods](https://docs.aws.amazon.com/lambda/latest/dg/lambda-invocation.html)
- [Lambda retry behavior](https://docs.aws.amazon.com/lambda/latest/dg/invocation-retries.html)
- [Asynchronous invocation](https://docs.aws.amazon.com/lambda/latest/dg/invocation-async.html)
- [Asynchronous error handling và retry](https://docs.aws.amazon.com/lambda/latest/dg/invocation-async-error-handling.html)
- [Lambda destinations](https://docs.aws.amazon.com/lambda/latest/dg/invocation-async-retain-records.html)
- [Event source mappings](https://docs.aws.amazon.com/lambda/latest/dg/invocation-eventsourcemapping.html)
- [Lambda concurrency](https://docs.aws.amazon.com/lambda/latest/dg/lambda-concurrency.html)
- [Reserved concurrency](https://docs.aws.amazon.com/lambda/latest/dg/configuration-concurrency.html)
- [Provisioned concurrency](https://docs.aws.amazon.com/lambda/latest/dg/provisioned-concurrency.html)
- [Lambda quotas](https://docs.aws.amazon.com/lambda/latest/dg/gettingstarted-limits.html)
- [Lambda VPC networking](https://docs.aws.amazon.com/lambda/latest/dg/configuration-vpc.html)
- [Lambda SnapStart](https://docs.aws.amazon.com/lambda/latest/dg/snapstart.html)
- [Lambda versions](https://docs.aws.amazon.com/lambda/latest/dg/configuration-versions.html)
- [Lambda aliases](https://docs.aws.amazon.com/lambda/latest/dg/configuration-aliases.html)
- [Lambda Function URLs](https://docs.aws.amazon.com/lambda/latest/dg/urls-configuration.html)
- [Lambda response streaming](https://docs.aws.amazon.com/lambda/latest/dg/configuration-response-streaming.html)
- [Lambda security best practices](https://docs.aws.amazon.com/lambda/latest/dg/security.html)
- [Monitoring Lambda](https://docs.aws.amazon.com/lambda/latest/dg/lambda-monitoring.html)


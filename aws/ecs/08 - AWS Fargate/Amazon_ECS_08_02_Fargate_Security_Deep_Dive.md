# Amazon ECS – AWS Fargate Security

> Tài liệu này giải thích hai chủ đề trong AWS Docs: [Fargate security best practices in Amazon ECS](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/security-fargate.html) và [Fargate security considerations for Amazon ECS](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/fargate-security-considerations.html).

## 1. Phân biệt “best practices” và “considerations”

Hai khái niệm này liên quan nhưng không giống nhau:

| Nhóm | Ý nghĩa | Câu hỏi cần trả lời |
|---|---|---|
| Security best practices | Các biện pháp AWS khuyến nghị nên triển khai | Nên cấu hình gì để tăng khả năng bảo vệ và phát hiện mối đe dọa? |
| Security considerations | Đặc điểm và giới hạn bảo mật của môi trường Fargate | Kiến trúc ứng dụng có phù hợp với mô hình cô lập và các giới hạn của Fargate không? |

Tóm tắt:

```text
Security considerations
    → hiểu ranh giới cô lập và giới hạn của Fargate

Security best practices
    → chọn biện pháp bảo vệ phù hợp trong ranh giới đó
```

## 2. Mô hình bảo mật tổng quát của Fargate

Fargate chạy mỗi **task** trong một môi trường ảo hóa cô lập và cấp capacity hạ tầng riêng cho task đó. Hai task khác nhau không dùng chung:

- network interface;
- ephemeral storage;
- CPU;
- memory.

AWS quản lý host, lớp ảo hóa, container runtime và việc vá hạ tầng. Khách hàng vẫn chịu trách nhiệm về:

- container image và thư viện của ứng dụng;
- task definition;
- IAM task role và task execution role;
- VPC, subnet, security group, network ACL và route;
- secrets, dữ liệu và lựa chọn khóa mã hóa;
- logging, monitoring và phản ứng sự cố;
- cách nhóm nhiều container vào cùng một task.

Ranh giới quan trọng nhất cần nhớ là:

```text
Task A                              Task B
┌───────────────────────┐          ┌───────────────────────┐
│ App + trusted sidecar │          │ App + trusted sidecar │
│ Chia sẻ tài nguyên    │          │ Chia sẻ tài nguyên    │
│ bên trong cùng task   │          │ bên trong cùng task   │
└───────────────────────┘          └───────────────────────┘
          ▲                                  ▲
          └──── cô lập ở cấp task ───────────┘
```

Fargate cung cấp cô lập giữa các task, nhưng không coi từng container bên trong cùng một task là một security boundary độc lập.

## Fargate security best practices in Amazon ECS

### 3. Use AWS KMS to encrypt ephemeral storage for Fargate

#### 3.1. Ephemeral storage chứa gì?

Ephemeral storage là ổ đĩa tạm tồn tại theo vòng đời của task. Nó có thể chứa:

- container image đã tải xuống;
- image layer sau khi giải nén;
- bind mount dùng chung giữa các container;
- file tạm, cache hoặc dữ liệu ứng dụng ghi trong lúc chạy.

Khi task dừng, dữ liệu trên ephemeral storage không được giữ lại như persistent storage. Tuy nhiên, “dữ liệu tạm” vẫn có thể là dữ liệu nhạy cảm trong thời gian task hoạt động, vì vậy vẫn cần mã hóa.

#### 3.2. Mã hóa mặc định

Với Linux task chạy trên Fargate platform version `1.4.0` trở lên:

- task nhận mặc định 20 GiB ephemeral storage;
- có thể tăng tổng dung lượng lên tối đa 200 GiB bằng `ephemeralStorage` trong task definition;
- các task thuộc phạm vi được AWS nêu trong tài liệu sử dụng AES-256 với khóa do Fargate quản lý.

Ví dụ khai báo dung lượng:

```json
{
  "family": "example-task",
  "requiresCompatibilities": ["FARGATE"],
  "networkMode": "awsvpc",
  "cpu": "512",
  "memory": "1024",
  "ephemeralStorage": {
    "sizeInGiB": 50
  }
}
```

Việc tăng dung lượng không thay đổi bản chất tạm thời của storage. Dữ liệu cần tồn tại sau vòng đời task phải được đưa sang nơi lưu trữ bền vững phù hợp.

#### 3.3. Khi nào dùng customer managed key?

Khóa do Fargate quản lý đáp ứng nhu cầu mã hóa mặc định. Customer managed key trong AWS KMS phù hợp khi tổ chức cần:

- kiểm soát key policy;
- tự quản lý rotation và vòng đời khóa;
- audit hoạt động sử dụng khóa qua AWS CloudTrail;
- giới hạn khóa theo account hoặc ECS cluster;
- đáp ứng yêu cầu compliance bắt buộc dùng khóa do khách hàng quản lý.

Customer managed key không tự động làm ứng dụng “an toàn hơn” nếu IAM và key policy cấu hình quá rộng. Lợi ích chính là tăng quyền kiểm soát, khả năng audit và khả năng thực thi chính sách.

#### 3.4. Rủi ro vận hành với KMS key

Nếu disable, lên lịch xóa hoặc làm sai key policy, task mới có thể không sử dụng được cấu hình storage mong muốn. Vì vậy cần:

1. Áp dụng least privilege cho key policy.
2. Theo dõi thay đổi khóa bằng CloudTrail và cảnh báo.
3. Kiểm tra workload trước khi rotate hoặc thay khóa.
4. Không xóa khóa khi cluster hoặc deployment còn phụ thuộc vào khóa đó.
5. Dùng quy trình phê duyệt cho thao tác disable và delete.

Xem thêm: [Customer managed keys for AWS Fargate ephemeral storage](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/fargate-storage-encryption.html).

### 4. SYS_PTRACE capability for kernel syscall tracing with Fargate

#### 4.1. Linux capability là gì?

Linux chia đặc quyền truyền thống của tài khoản root thành các capability nhỏ hơn. Container chỉ nên nhận capability thực sự cần thiết thay vì có toàn bộ quyền đặc quyền.

Fargate khóa chặt môi trường runtime và chỉ hỗ trợ bổ sung capability `SYS_PTRACE` cho task. Capability này cho phép một process quan sát hoặc trace process khác, thường được dùng bởi:

- debugger;
- profiler;
- công cụ theo dõi system call;
- một số security/observability agent.

#### 4.2. Vì sao `SYS_PTRACE` nhạy cảm?

Process có `SYS_PTRACE` có thể quan sát sâu hoạt động của process khác trong phạm vi được phép. Tùy ứng dụng và cách bố trí container, điều này có thể làm lộ:

- dữ liệu trong memory;
- tham số command;
- hành vi system call;
- thông tin phục vụ khai thác nếu công cụ bị xâm nhập.

Vì vậy không nên thêm capability này theo mặc định.

#### 4.3. Cấu hình tối thiểu

Chỉ thêm `SYS_PTRACE` cho container thật sự cần nó:

```json
{
  "linuxParameters": {
    "capabilities": {
      "add": ["SYS_PTRACE"]
    }
  }
}
```

Checklist trước khi bật:

- Có yêu cầu tracing hoặc runtime security cụ thể không?
- Công cụ có thể hoạt động mà không cần `SYS_PTRACE` không?
- Chỉ container quan sát được cấp capability hay tất cả container đều được cấp?
- Image của công cụ có được pin digest và quét lỗ hổng không?
- Log và cảnh báo có phát hiện việc công cụ bị tắt hoặc hoạt động bất thường không?

Nguyên tắc: **không cấp capability để “phòng khi cần”; chỉ cấp khi đã xác định use case và phạm vi.**

### 5. Use Amazon GuardDuty with Fargate Runtime Monitoring

#### 5.1. GuardDuty Runtime Monitoring làm gì?

Amazon GuardDuty là dịch vụ phát hiện mối đe dọa. Runtime Monitoring cho Fargate sử dụng security agent nhẹ do AWS quản lý để phân tích hành vi khi workload đang chạy, bao gồm:

- truy cập file;
- thực thi process;
- kết nối mạng;
- hành vi leo thang đặc quyền;
- sử dụng credential bị lộ;
- giao tiếp với IP hoặc domain độc hại;
- dấu hiệu malware.

Khác với image scanning, Runtime Monitoring quan sát hành vi thực tế sau khi container đã chạy.

#### 5.2. Các lớp kiểm soát bổ sung cho nhau

```text
Trước khi chạy       Khi triển khai          Khi đang chạy
────────────────     ──────────────────       ──────────────────
Quét container       IAM + network policy     GuardDuty Runtime
image                + encrypted storage      Monitoring
```

Không lớp nào thay thế hoàn toàn lớp khác:

- image scanning không phát hiện mọi hành vi phát sinh tại runtime;
- GuardDuty không sửa IAM policy hoặc security group cấu hình sai;
- mã hóa storage không ngăn một process hợp lệ trong task đọc dữ liệu mà nó được cấp quyền.

#### 5.3. Vận hành finding

Bật Runtime Monitoring mới chỉ tạo khả năng phát hiện. Một quy trình hoàn chỉnh còn cần:

1. Chuyển finding vào hệ thống cảnh báo hoặc ticket.
2. Xác định severity nào cần phản ứng ngay.
3. Lưu task ARN, cluster, service, image digest và thời điểm phát hiện.
4. Cô lập hoặc thay task bị nghi ngờ bằng deployment sạch.
5. Thu hồi credential có khả năng bị lộ.
6. Điều tra image, application log, CloudTrail và network activity.
7. Sửa nguyên nhân gốc trước khi khôi phục bình thường.

Xem thêm: [GuardDuty Runtime Monitoring](https://docs.aws.amazon.com/guardduty/latest/ug/runtime-monitoring.html).

## Fargate security considerations for Amazon ECS

### 6. Isolation boundary: task khác với container

#### 6.1. Cô lập giữa các task

Mỗi Fargate task nhận hạ tầng riêng và không chia sẻ network interface, ephemeral storage, CPU hoặc memory với task khác. Đây là khác biệt quan trọng so với mô hình nhiều task cùng chạy trên một EC2 container instance do khách hàng quản lý.

#### 6.2. Chia sẻ bên trong cùng một task

Nhiều container trong cùng task luôn được đặt cùng nhau và chia sẻ một số tài nguyên:

- compute resource của task;
- Fargate ephemeral storage;
- với Linux, network namespace, IP address và port space;
- giao tiếp nội bộ qua `localhost`.

Điều này phù hợp với mô hình application container và trusted sidecar, ví dụ:

- ứng dụng và log router;
- ứng dụng và telemetry collector;
- ứng dụng và proxy hỗ trợ trực tiếp cho ứng dụng.

Không nên đặt hai workload không tin cậy lẫn nhau vào cùng task chỉ để tiết kiệm cấu hình. Nếu cần security boundary, hãy tách chúng thành task hoặc service riêng.

Ví dụ:

| Tình huống | Bố trí phù hợp |
|---|---|
| App và sidecar thu thập telemetry do cùng đội kiểm soát | Có thể cùng task |
| Hai ứng dụng của hai tenant không tin cậy nhau | Task riêng |
| App xử lý dữ liệu nhạy cảm và công cụ bên thứ ba có quyền rộng | Đánh giá kỹ; ưu tiên tách task |
| Các container cần scale độc lập | Service/task riêng |

### 7. No privileged containers or access

Fargate không hỗ trợ privileged container. Container không thể nhận mức truy cập tương đương quyền kiểm soát host như trong một số cấu hình Docker trên EC2.

Các use case thường bị ảnh hưởng:

- Docker-in-Docker cần privileged mode;
- phần mềm cần thao tác trực tiếp với thiết bị host;
- agent cần quyền quản trị kernel hoặc network stack;
- công cụ cần mount filesystem của host;
- workload phụ thuộc sâu vào container runtime của host.

Đây vừa là giới hạn tính năng vừa là biện pháp giảm nguy cơ container escape và privilege escalation. Nếu workload bắt buộc cần privileged access, Fargate có thể không phải compute option phù hợp; cần đánh giá ECS on EC2 hoặc một nền tảng khác cùng các biện pháp hardening tương ứng.

Không nên tìm cách mô phỏng privileged mode bằng cách đưa socket, credential hoặc API quản trị mạnh vào container. Cách đó có thể phá vỡ chính security boundary mà Fargate cung cấp.

### 8. Limited access to Linux capabilities

Fargate hạn chế các Linux capability có rủi ro cao như:

- `CAP_SYS_ADMIN`;
- `CAP_NET_ADMIN`.

Các capability này có phạm vi rất rộng và có thể tạo điều kiện cho privilege escalation hoặc thay đổi network behavior. Fargate chỉ hỗ trợ bổ sung `CAP_SYS_PTRACE` cho use case observability và security tooling phù hợp.

Hệ quả thiết kế:

- network appliance hoặc agent cần sửa routing/interface thường không chạy được như trên EC2;
- công cụ bảo mật yêu cầu kernel module hoặc eBPF đặc quyền cần được kiểm tra khả năng tương thích;
- task definition từ ECS on EC2 không thể luôn chuyển nguyên trạng sang Fargate;
- cần kiểm tra yêu cầu capability của image trước khi chọn launch type.

### 9. No access to the underlying host

Khách hàng không SSH hoặc kết nối quản trị vào host chạy Fargate task. Container cũng không truy cập được filesystem, device, networking hay container runtime của host.

Điều này mang lại hai tác động:

- **Bảo mật:** giảm bề mặt tấn công và ngăn workload phụ thuộc vào host.
- **Vận hành:** không thể đăng nhập host để sửa file hoặc debug theo cách truyền thống.

Để chẩn đoán, có thể dùng ECS Exec để chạy command hoặc mở shell trong container đang chạy. Tuy nhiên ECS Exec cần được quản trị như một đường truy cập đặc quyền:

- chỉ cấp `ecs:ExecuteCommand` cho người hoặc role cần thiết;
- kiểm soát quyền Systems Manager liên quan;
- bật logging cho session nếu chính sách tổ chức yêu cầu;
- không dùng ECS Exec như phương thức triển khai hoặc sửa production thủ công;
- thay task bằng deployment mới sau khi sửa image hoặc cấu hình.

Nguyên tắc vận hành của Fargate là **immutable workload**: sửa nguồn, build image mới, kiểm thử và redeploy thay vì chỉnh host hoặc container bằng tay.

### 10. Networking

Fargate task nhận IP address từ subnet đã cấu hình trong VPC và sử dụng network mode `awsvpc`. Mỗi task có elastic network interface riêng, nhờ đó security group có thể kiểm soát traffic ở cấp task/service.

Các lớp kiểm soát chính:

| Lớp | Vai trò |
|---|---|
| Security group | Stateful firewall cho ENI của task |
| Network ACL | Stateless control ở cấp subnet |
| Route table | Quyết định đường đi tới Internet, NAT, peering, Transit Gateway hoặc endpoint |
| VPC endpoint | Truy cập riêng tới một số dịch vụ AWS mà không cần đi qua Internet |
| Load balancer | Điểm nhận traffic, health check và phân phối request tới task |

#### 10.1. Inbound traffic

- Chỉ mở đúng application port.
- Nếu task đứng sau load balancer, security group của task nên nhận traffic từ security group của load balancer thay vì mở cho toàn Internet.
- Không cấp public IP nếu task không cần nhận hoặc tạo kết nối trực tiếp qua Internet.
- Tách security group theo chức năng thay vì dùng một nhóm chung quá rộng.

#### 10.2. Outbound traffic

Không nên mặc định coi outbound traffic là vô hại. Container bị xâm nhập có thể dùng outbound access để tải payload hoặc gửi dữ liệu ra ngoài.

Cần xác định task thực sự phải kết nối tới:

- ECR để pull image;
- CloudWatch Logs để gửi log;
- Secrets Manager hoặc Systems Manager Parameter Store để lấy secret;
- database, internal API hoặc dịch vụ bên thứ ba;
- Internet qua NAT/public IP nếu thật sự cần.

VPC endpoint có thể giảm nhu cầu đi qua Internet cho các dịch vụ AWS được hỗ trợ. Network policy phải được kiểm thử cùng luồng khởi động task; chặn quá mức có thể khiến task không pull được image, không lấy được secret hoặc không gửi được log.

## 11. Những hiểu lầm thường gặp

### “Fargate là serverless nên AWS chịu trách nhiệm toàn bộ bảo mật”

Sai. AWS bảo vệ hạ tầng Fargate; khách hàng vẫn quản lý image, application code, IAM, network, secrets, data và monitoring.

### “Các container trong cùng task được cô lập như hai task khác nhau”

Sai. Chúng chia sẻ compute, ephemeral storage và—đối với Linux—network namespace. Chỉ đặt các container có cùng mức tin cậy vào một task.

### “Ephemeral nghĩa là không cần mã hóa”

Sai. Dữ liệu vẫn tồn tại trong thời gian task chạy và có thể chứa secret hoặc dữ liệu nhạy cảm.

### “Có GuardDuty thì không cần hardening image và IAM”

Sai. GuardDuty là lớp phát hiện; nó không thay thế preventive control.

### “Không SSH được vào host nên không thể debug”

Sai. Có thể dùng log, metric, tracing và ECS Exec có kiểm soát. Thiết kế cần chuyển từ host-centric debugging sang container-centric observability.

## 12. Checklist triển khai an toàn

### Trước khi triển khai

- [ ] Chỉ nhóm các container tin cậy lẫn nhau vào cùng task.
- [ ] Xác nhận workload không cần privileged mode hoặc Linux capability không được hỗ trợ.
- [ ] Quét image và pin version/digest phù hợp.
- [ ] Tách task execution role khỏi task role.
- [ ] Áp dụng least privilege cho IAM và KMS key policy.
- [ ] Không nhúng secret vào image hoặc task definition dạng plain text.
- [ ] Thiết kế subnet, security group, route và VPC endpoint.
- [ ] Xác định ephemeral storage cần bao nhiêu và dùng loại khóa nào.

### Khi vận hành

- [ ] Bật log và metric cần thiết.
- [ ] Cân nhắc GuardDuty Runtime Monitoring cho Fargate.
- [ ] Định tuyến finding vào quy trình phản ứng sự cố.
- [ ] Giới hạn và audit quyền ECS Exec.
- [ ] Theo dõi thay đổi IAM, security group và KMS key.
- [ ] Redeploy task để nhận image và platform revision đã vá.
- [ ] Kiểm thử graceful shutdown và khả năng thay thế task.

## 13. Kết luận

Mô hình bảo mật cốt lõi của Fargate là **cô lập ở cấp task và không cho truy cập host**. Mô hình này giảm đáng kể phần hạ tầng khách hàng phải bảo vệ, nhưng không loại bỏ trách nhiệm bảo mật workload.

Ba best practice AWS nhấn mạnh là:

1. Mã hóa ephemeral storage bằng khóa phù hợp.
2. Chỉ dùng `SYS_PTRACE` khi có nhu cầu tracing rõ ràng.
3. Dùng GuardDuty Runtime Monitoring để phát hiện hành vi đáng ngờ khi container đang chạy.

Bốn consideration cần kiểm tra từ lúc thiết kế là:

1. Các container trong cùng task chia sẻ tài nguyên và phải tin cậy lẫn nhau.
2. Fargate không hỗ trợ privileged container.
3. Linux capability bị giới hạn mạnh.
4. Không có quyền truy cập host; networking phải được kiểm soát qua VPC, security group và network ACL.

Nếu workload phù hợp với các giới hạn này, Fargate cung cấp một nền tảng có ranh giới cô lập rõ ràng và giảm gánh nặng hardening hạ tầng. Nếu workload cần quyền kiểm soát kernel, device hoặc host sâu, cần đánh giá compute option khác thay vì làm suy yếu mô hình bảo mật.

## Tài liệu AWS tham khảo

- [Fargate security best practices in Amazon ECS](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/security-fargate.html)
- [Fargate security considerations for Amazon ECS](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/fargate-security-considerations.html)
- [AWS shared responsibility model for Amazon ECS](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/security-shared-model.html)
- [Customer managed keys for AWS Fargate ephemeral storage](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/fargate-storage-encryption.html)
- [GuardDuty Runtime Monitoring](https://docs.aws.amazon.com/guardduty/latest/ug/runtime-monitoring.html)
- [Using Amazon ECS Exec for debugging](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/ecs-exec.html)

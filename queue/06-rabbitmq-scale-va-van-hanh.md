# Tuần 6: RabbitMQ scale và vận hành

## 1. Mục tiêu bài học

Sau bài học này, bạn có thể:

- Giải thích competing consumers.
- Scale consumer theo queue và workload.
- Tuning prefetch dựa trên throughput, latency và memory.
- Hiểu fair dispatch và giới hạn thực tế của nó.
- Phân tích ordering khi có nhiều consumer.
- Đọc queue depth, ready messages và unacked messages.
- Phát hiện consumer chậm, consumer chết và backlog tăng.
- Quản lý connection/channel hợp lý.
- Nhận biết channel leak và connection churn.
- Hiểu quorum queue và trade-off với classic queue.
- Mô tả cluster, node failure và leader failover ở mức khái niệm.
- Thiết kế benchmark với 1, 3 và 10 consumers.
- Kill consumer giữa lúc xử lý và giải thích redelivery.
- Viết báo cáo gồm message size, producer rate, consumer count, prefetch, throughput, p95 latency, redelivery và queue depth.

Tuần 5 tập trung reliability. Tuần 6 trả lời câu hỏi:

    Khi message volume tăng, scale RabbitMQ và consumer thế nào
    mà không phá ordering, không làm broker cạn tài nguyên
    và vẫn quan sát được failure?

---

## 2. Bài toán scale

Giả sử producer phát 1.000 message mỗi giây.

Một consumer xử lý trung bình 100 message mỗi giây.

Nếu chỉ có một consumer:

    ingress rate = 1.000 msg/s
    processing rate = 100 msg/s
    backlog growth = 900 msg/s

Queue sẽ tăng liên tục.

Thêm consumer:

    3 consumers x 100 msg/s = 300 msg/s
    vẫn thấp hơn ingress 1.000 msg/s

Cần nhớ:

- Tăng consumer chỉ giúp tới giới hạn CPU, I/O và downstream.
- Broker cũng có giới hạn memory, disk, connection, channel và queue.
- Producer rate cần được kiểm soát bằng backpressure hoặc admission control.
- Queue là buffer, không phải năng lực xử lý vô hạn.

---

## 3. Competing consumers

Competing consumers là nhiều consumer cùng đọc một queue.

    order.notification queue
        ├── consumer 1
        ├── consumer 2
        └── consumer 3

Một message trong queue thường được deliver cho một consumer trong nhóm. Các consumer cạnh tranh để xử lý workload.

### 3.1. Khi nào dùng?

Phù hợp khi:

- Mỗi message chỉ cần xử lý một lần về mặt business.
- Muốn tăng throughput.
- Handler có thể chạy song song.
- Side effect idempotent.
- Không yêu cầu strict global ordering.

Ví dụ:

    payment.commands queue
        ├── payment worker 1
        ├── payment worker 2
        └── payment worker 3

### 3.2. Khi nào không dùng?

Không dùng cùng một queue cho các subscriber độc lập cần mỗi subscriber nhận bản copy.

Sai:

    order.events queue
        ├── notification service
        └── audit service

Notification có thể nhận message này, audit nhận message khác.

Đúng:

    order.events exchange
        ├── order.notification queue -> notification service
        └── order.audit queue        -> audit service

Mỗi flow có queue riêng; mỗi queue có thể tiếp tục scale bằng competing consumers.

---

## 4. Fair dispatch

RabbitMQ phân phối message tới consumer dựa trên trạng thái delivery, prefetch và acknowledgement.

Nếu prefetch quá cao:

    consumer 1 giữ nhiều unacked message
    consumer 2 rảnh
    consumer 3 rảnh

Workload có thể không đều.

Prefetch thấp hơn thường giúp phân phối công bằng hơn:

    consumer 1 xử lý một message
    consumer 2 xử lý một message
    consumer 3 xử lý một message

Nhưng fair dispatch không có nghĩa:

- Processing time của các message bằng nhau.
- Hoàn thành theo thứ tự publish.
- Consumer chậm tự động bị loại ngay.
- Throughput luôn tối đa.

Fairness là trade-off giữa utilization, latency, memory và ordering.

### 4.1. Ví dụ message không đồng đều

    message A: xử lý 10 ms
    message B: xử lý 5 giây
    message C: xử lý 10 ms

Nếu một consumer nhận B và prefetch lớn, nó có thể giữ thêm nhiều message trong khi consumer khác rảnh. Prefetch thấp giúp giảm mức độ lệch, nhưng không làm B xử lý nhanh hơn.

---

## 5. Prefetch tuning

Prefetch là số delivery chưa ack mà một consumer có thể giữ outstanding.

### 5.1. Prefetch thấp

    prefetch = 1

Ưu điểm:

- Ít message nằm trong client.
- Phân phối workload tương đối công bằng.
- Dễ kiểm soát memory.
- Hữu ích khi cần ordering tương đối.

Nhược điểm:

- Throughput có thể thấp.
- Consumer phải chờ round-trip nhiều hơn.
- Không tận dụng tốt workload nhỏ và nhanh.

### 5.2. Prefetch cao

    prefetch = 100

Ưu điểm:

- Broker có thể đẩy message trước.
- Giảm thời gian chờ trong một số workload.
- Có thể tăng throughput khi handler nhanh.

Nhược điểm:

- Unacked messages tăng.
- Memory client tăng.
- Một consumer giữ nhiều message.
- Fair dispatch kém hơn.
- Khi consumer chết, nhiều message có thể redeliver.
- Message age và queue metrics khó đọc hơn.

### 5.3. Cách tuning

Không chọn prefetch chỉ theo cảm tính. Test các giá trị:

    1, 5, 10, 25, 50, 100

Mỗi lần giữ cố định:

- Message size.
- Producer rate.
- Consumer code.
- Downstream latency.
- Consumer count.
- Broker version.
- Machine resources.

Đo:

- Throughput.
- p50/p95/p99 latency.
- Ready messages.
- Unacked messages.
- Consumer utilization.
- Redelivery.
- Memory.
- CPU.
- Network.
- Database pool.

### 5.4. Quy tắc khởi đầu

Workload xử lý lâu hoặc message lớn:

    prefetch thấp

Workload xử lý nhanh và nhỏ:

    prefetch vừa phải

Workload cần strict ordering trên một consumer:

    prefetch = 1

Không nên để unlimited prefetch cho business consumer.

---

## 6. Consumer concurrency trong Spring AMQP

### 6.1. Fixed concurrency

    spring:
      rabbitmq:
        listener:
          simple:
            concurrency: 3

Hoặc:

    factory.setConcurrentConsumers(3);

Có ba consumer cùng đọc queue.

### 6.2. Dynamic concurrency

    spring:
      rabbitmq:
        listener:
          simple:
            concurrency: 1
            max-concurrency: 10

Hoặc:

    factory.setConcurrentConsumers(1);
    factory.setMaxConcurrentConsumers(10);

Container có thể tăng/giảm consumer theo workload và trigger cấu hình.

### 6.3. Concurrency theo listener

    @RabbitListener(
        queues = "order.notification",
        concurrency = "3-10"
    )
    public void handle(OrderEventEnvelope<?> event) {
        notificationService.process(event);
    }

Notification có thể cần scale khác audit. Tách container factory nếu profile khác nhau.

### 6.4. Giới hạn concurrency

Concurrency thực tế bị giới hạn bởi:

- CPU.
- Database connection pool.
- HTTP client pool.
- Downstream rate limit.
- External API quota.
- Message size.
- Processing time.
- JVM memory.
- Broker capacity.

Tăng từ 10 lên 100 consumer không đảm bảo throughput tăng 10 lần.

### 6.5. Consumer count và queue count

Nhiều consumer cùng một queue giúp scale một flow:

    one queue + many consumers = work sharing

Nhiều queue giúp scale nhiều flow độc lập:

    one exchange + many queues = pub/sub isolation

Đây là hai chiều scale khác nhau.

---

## 7. Ordering limitation

### 7.1. Một queue không đảm bảo completed order

RabbitMQ có thể deliver theo queue order, nhưng với nhiều consumer:

    publish A, B, C
        -> consumer 1 nhận A
        -> consumer 2 nhận B
        -> consumer 3 nhận C

Nếu C xử lý xong trước A, side effect hoàn thành theo:

    C, B, A

Vì vậy delivery order và completion order khác nhau.

### 7.2. Prefetch ảnh hưởng quan sát ordering

Prefetch lớn làm nhiều message được gửi trước cho consumer. Điều này làm thứ tự xử lý và hoàn thành khó dự đoán hơn.

Nếu ordering nghiêm:

- Một consumer.
- Prefetch thấp.
- Một queue cho ordering domain.
- Sequence number.
- Idempotency và stale-event check.
- Thiết kế partition/shard theo aggregate.

### 7.3. Ordering theo aggregate

Thường không cần global ordering cho toàn hệ thống. Cần ordering trong phạm vi orderId:

    order-1: event 1 -> event 2 -> event 3
    order-2: event 1 -> event 2

Có thể shard theo aggregate ở tầng topology hoặc application. RabbitMQ queue cơ bản không tự cung cấp partition key như Kafka.

### 7.4. Trade-off

Strict ordering thường giảm parallelism.

    ordering mạnh
        -> ít consumer
        -> throughput thấp hơn

    throughput cao
        -> nhiều consumer
        -> completion order không đảm bảo

Phải ghi rõ invariant cần giữ trước khi scale.

---

## 8. Queue depth

Queue depth là lượng message đang chờ hoặc đang được xử lý tùy metric.

Cần phân biệt:

- Ready messages: chờ được deliver.
- Unacked messages: đã deliver nhưng chưa ack.
- Total messages: tùy dashboard có thể là tổng hợp.
- Message age: message lâu nhất đã chờ bao lâu.

### 8.1. Queue depth tăng

Có thể do:

- Producer rate lớn hơn consumer rate.
- Consumer crash.
- Consumer không active.
- Downstream chậm.
- Prefetch/concurrency quá thấp.
- Database bị lock.
- Retry quay lại main queue.
- Routing gửi nhầm flow.
- Broker flow control.
- Network hoặc node issue.

Queue depth chỉ là symptom. Cần thêm processing latency, consumer count và failure rate.

### 8.2. Queue depth giảm nhưng hệ thống vẫn lỗi

Queue có thể giảm vì:

- Auto ack làm message mất trước processing.
- Consumer reject không requeue và không có DLQ.
- Message hết TTL.
- Producer giảm traffic.
- Message bị route sai queue khác.
- Consumer đọc nhưng side effect fail.

Vì vậy không dùng queue depth đơn độc làm success metric.

### 8.3. Age of oldest message

Age của message lâu nhất thường phản ánh SLA tốt hơn depth.

Ví dụ:

    depth = 100
    processing time = 1 ms

có thể tốt hơn:

    depth = 10
    oldest age = 30 phút

Dashboard nên có depth và oldest message age.

---

## 9. Unacked messages

Unacked message là message đã giao cho consumer nhưng chưa được ack.

Unacked tăng có thể là bình thường nếu:

- Handler đang xử lý.
- Prefetch cao.
- Batch processing đang chạy.
- Downstream tạm thời chậm.

Unacked tăng liên tục là dấu hiệu:

- Consumer treo.
- Handler quá chậm.
- Quên ack.
- Exception bị nuốt.
- Connection/channel có vấn đề.
- Prefetch quá cao.
- Database pool cạn.
- Consumer nhận nhiều hơn khả năng xử lý.

### 9.1. Ready cao, unacked thấp

Có thể là:

- Thiếu consumer.
- Consumer concurrency thấp.
- Consumer không active.
- Producer quá nhanh.

### 9.2. Ready thấp, unacked cao

Có thể là:

- Consumer giữ quá nhiều message.
- Handler đang xử lý chậm.
- Downstream bị block.
- Prefetch quá cao.
- Ack bị trì hoãn.

### 9.3. Ready và unacked đều thấp

Có thể là:

- Hệ thống khỏe.
- Producer chưa phát traffic.
- Message bị mất do auto ack.
- Message bị reject/expire.
- Message đi nhầm queue.

Phải kết hợp metrics publish, consume, ack và reject.

---

## 10. Connection và channel

### 10.1. Connection

Connection là TCP/AMQP connection. Nó có chi phí network và process.

Không mở connection mỗi message.

Một service thường có connection được quản lý bởi CachingConnectionFactory và nhiều channel theo publisher/consumer.

### 10.2. Channel

Channel là logical session trong connection.

Channel dùng cho:

- Declare topology.
- Publish.
- Consume.
- Ack.
- Nack.
- Confirm.

Channel không nên bị tạo/xóa liên tục trong hot path.

### 10.3. Connection strategy

Có thể tách connection cho:

    connection 1: publisher
    connection 2: consumer
    connection 3: admin/topology

Lý do:

- Publisher flow control không ảnh hưởng consumer.
- Consumer disconnect không làm publisher mất connection.
- Dễ quan sát và giới hạn resource.

Không phải hệ thống nào cũng cần nhiều connection. Mỗi connection có memory, socket và heartbeat cost.

### 10.4. Channel leak

Channel leak xảy ra khi code tạo channel nhưng không đóng/reuse.

Dấu hiệu:

- Channel count tăng dần.
- Connection vẫn sống nhưng channel tăng.
- Channel open/close rate cao.
- Broker memory tăng.
- Application lỗi khi mở channel mới.

Spring container và RabbitTemplate nên quản lý lifecycle. Tránh tự tạo channel trong mỗi request nếu không cần.

### 10.5. Connection churn

Connection churn là mở/đóng connection liên tục.

Dấu hiệu:

- Connection open/close rate cao.
- TLS handshake tăng.
- CPU và network tăng.
- Publish latency dao động.
- Broker log nhiều connection event.

Dùng connection factory/cache và graceful reconnect.

---

## 11. Resource limits và flow control

RabbitMQ có thể flow-control publisher khi broker resource pressure cao.

Nguyên nhân:

- Memory watermark.
- Disk free limit.
- Queue backlog lớn.
- Consumer quá chậm.
- Quorum replication chậm.
- Network congestion.

Khi flow control:

- Publisher throughput giảm.
- Connection có thể ở trạng thái flow.
- Latency tăng.
- Queue depth có thể tiếp tục tăng nếu ingress chưa giảm.

Không giải quyết bằng cách tạo thêm producer thread vô hạn. Cần:

- Giảm producer rate.
- Scale consumer nếu bottleneck ở consumer.
- Giới hạn queue length.
- Xử lý downstream chậm.
- Kiểm tra disk và memory.
- Có alert.

---

## 12. Connection/channel limits

RabbitMQ và OS đều có giới hạn:

- Max connections.
- File descriptors.
- Channel count.
- Memory.
- TCP backlog.
- Erlang process.
- CPU.

Cần monitor:

- Tổng connection.
- Connection theo application.
- Tổng channel.
- Channel per connection.
- Connection open/close rate.
- Channel open/close rate.
- Authentication failure.
- Flow state.
- Consumer count.

RabbitMQ documentation lưu ý việc poll quá thường xuyên queue/channel có thể làm tăng CPU. Monitoring cũng cần budget.

---

## 13. Quorum queue

### 13.1. Khái niệm

Quorum queue là replicated queue dùng consensus để duy trì state giữa leader và follower.

    quorum queue
        ├── leader
        ├── follower
        └── follower

Nếu leader fail, follower có thể được bầu làm leader khi còn quorum.

### 13.2. Khi nào phù hợp?

Phù hợp cho:

- Order intake.
- Payment command.
- Queue dài hạn.
- Queue quan trọng nếu mất message ảnh hưởng correctness.
- Use case cần high availability và data safety.

RabbitMQ hiện khuyến nghị xem quorum queue là lựa chọn mặc định khi cần queue replicated/highly available, nhưng không có nghĩa mọi queue đều nên dùng quorum.

### 13.3. Khi nào không phù hợp?

Không phù hợp hoặc cần cân nhắc cho:

- Temporary queue.
- Exclusive queue.
- Queue tạo/xóa với churn cao.
- RPC reply queue.
- Lowest possible latency.
- Backlog cực lớn.
- Fanout rất lớn.
- Use case không coi data safety là ưu tiên.

### 13.4. Quorum trade-off

Ưu điểm:

- Replication.
- Leader election.
- Data safety tốt hơn cho queue quan trọng.
- Failover có kiểm soát.
- Poison message handling ở các phiên bản hỗ trợ.

Chi phí:

- Disk I/O.
- Network replication.
- Quorum availability.
- Latency cao hơn trong một số workload.
- Resource cost.
- Cần cluster topology đúng.

### 13.5. Declare quorum queue

Queue type phải chọn lúc declare:

    @Bean
    public Queue paymentCommandsQueue() {
        return QueueBuilder
            .durable("payment.commands")
            .withArgument("x-queue-type", "quorum")
            .build();
    }

Không đổi classic queue thành quorum bằng cách sửa một property trong place. Thường cần queue mới hoặc migration.

### 13.6. Confirm và quorum

Publisher confirm với quorum queue chỉ nên xem là thành công khi message đã được quorum chấp nhận theo semantics broker. Message chưa được confirm vẫn có thể mất trong failure window.

Consumer nên dùng manual ack để message chưa xử lý được redeliver.

---

## 14. Cluster và node failure

### 14.1. Cluster không phải replication cho mọi queue

RabbitMQ cluster replicate definitions như:

- Exchanges.
- Bindings.
- Users.
- Permissions.
- Policies.

Queue data replication phụ thuộc queue type. Không được suy luận “cluster có ba node nên mọi message có ba bản copy”.

### 14.2. Node failure

Khi node fail:

- Connection tới node đó mất.
- Consumer cần reconnect/recover.
- Queue leader có thể failover nếu queue type hỗ trợ.
- Message đang in-flight có thể redeliver.
- Publisher confirm đang chờ có thể timeout hoặc nack.
- Application phải chịu duplicate/unknown outcome.

### 14.3. Quorum leader failure

Quorum queue tạm pause delivery/state change trong lúc leader election. Sau khi leader mới được chọn và còn quorum, consumer có thể tiếp tục.

### 14.4. Network partition

Network partition có thể tạo:

- Mất liên lạc giữa nodes.
- Leader không còn majority.
- Publish bị reject hoặc timeout.
- Failover chậm.
- Cluster recovery phức tạp.

Không tự ý force cluster state hoặc xóa data directory trong production. Cần runbook và backup.

### 14.5. Node naming và network

Cluster phụ thuộc:

- Unique node name.
- DNS/hostname resolve.
- Port inter-node mở.
- Erlang distribution port.
- Cookie đồng nhất.
- Clock/network stability.

Container hóa không loại bỏ yêu cầu DNS và port.

---

## 15. Scaling strategy

### 15.1. Scale consumer trước

Nếu queue backlog tăng:

1. Kiểm tra producer rate.
2. Kiểm tra consumer active.
3. Kiểm tra handler latency.
4. Kiểm tra downstream capacity.
5. Tăng consumer có kiểm soát.
6. Đo lại throughput và p95.
7. Kiểm tra redelivery/unacked.

### 15.2. Scale queue topology

Nếu notification và payment có SLA khác nhau, tách queue:

    order.events
        ├── notification queue
        ├── payment queue
        └── audit queue

Không để flow chậm kéo flow quan trọng xuống.

### 15.3. Scale application instances

Có thể chạy nhiều instance Spring Boot:

    order-worker-1
    order-worker-2
    order-worker-3

Cùng đọc một queue. Broker phân phối message giữa các consumer.

Cần kiểm tra:

- Tổng concurrency của tất cả instance.
- Database pool tổng.
- Downstream rate limit tổng.
- Duplicate side effect.
- Consumer connection/channel count.

### 15.4. Scale producer

Producer scale có thể làm backlog tăng nhanh hơn. Trước khi scale producer:

- Biết consumer capacity.
- Có queue limit.
- Có backpressure.
- Có overload policy.
- Có rate limit.
- Có alert backlog.

---

## 16. Benchmark design

Mục tiêu benchmark không phải tìm một con số tuyệt đối. Mục tiêu là hiểu trade-off và tìm giới hạn workload cụ thể.

### 16.1. Biến độc lập

- Message size.
- Producer rate.
- Consumer count.
- Prefetch.
- Processing time.
- Concurrency.
- Queue type.
- Persistent/transient delivery.
- Confirm enabled/disabled.
- Local broker resource.

### 16.2. Metrics

- Throughput message/second.
- p50 latency.
- p95 latency.
- p99 latency.
- Ready queue depth.
- Unacked messages.
- Oldest message age.
- Redelivery count.
- Publish confirm latency.
- CPU.
- Memory.
- Disk I/O.
- Network.
- Error rate.

### 16.3. Giữ biến cố định

Mỗi run cần ghi:

    broker version
    JVM version
    machine type
    message size
    producer rate
    consumer count
    prefetch
    concurrency
    processing delay
    queue type
    delivery mode
    test duration

Nếu thay nhiều biến cùng lúc, không biết yếu tố nào tạo ra kết quả.

---

## 17. Lab setup

### 17.1. Message generator

Tạo producer phát message đều:

    eventId
    sequence
    createdAt
    payloadSize
    correlationId

Producer rate:

    100 msg/s
    500 msg/s
    1.000 msg/s

### 17.2. Consumer handler

Handler có thể mô phỏng processing:

    sleep 5 ms
    sleep 50 ms
    sleep 500 ms

Không dùng sleep dài trong production code. Đây chỉ là benchmark simulation.

### 17.3. Chạy 1, 3 và 10 consumers

Mỗi scenario:

    consumer count = 1
    prefetch = 1, 10, 50
    producer rate = cố định
    duration = 60 giây

Sau đó lặp với:

    consumer count = 3
    consumer count = 10

### 17.4. Bảng benchmark

| Run | Message size | Producer rate | Consumers | Prefetch | Throughput | p95 | Redelivery | Queue depth |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| A | 1 KB | 100/s | 1 | 1 | ghi nhận | ghi nhận | ghi nhận | ghi nhận |
| B | 1 KB | 100/s | 1 | 10 | ghi nhận | ghi nhận | ghi nhận | ghi nhận |
| C | 1 KB | 100/s | 3 | 10 | ghi nhận | ghi nhận | ghi nhận | ghi nhận |
| D | 1 KB | 100/s | 10 | 10 | ghi nhận | ghi nhận | ghi nhận | ghi nhận |

Không điền số giả. Chạy thực tế trên môi trường bạn mô tả.

---

## 18. Lab: Kill consumer giữa lúc xử lý

Flow:

1. Publish message.
2. Consumer nhận message.
3. Consumer delay trước ack.
4. Kill process.
5. Quan sát unacked message.
6. Khởi động consumer mới.
7. Quan sát redelivery.
8. Kiểm tra idempotency side effect.

Cần ghi:

- Message có redeliver không?
- Redelivery flag có được set không?
- Queue depth thay đổi thế nào?
- Unacked giảm khi connection đóng không?
- Side effect chạy một hay nhiều lần?
- Publisher có liên quan không?

Đây là lab chứng minh delivery và processing là hai trạng thái khác nhau.

---

## 19. Lab: Message xử lý lâu

Tạo message xử lý 30 giây:

    receive
        -> processing 30 seconds
        -> ack

Thử:

- prefetch 1.
- prefetch 10.
- một consumer.
- ba consumers.

Quan sát:

- Unacked messages.
- Memory consumer.
- Queue depth.
- Completion order.
- Shutdown time.
- Redelivery nếu kill consumer.

Nếu handler lâu hơn operational timeout, cần thiết kế:

- Timeout.
- Retry.
- Idempotency.
- Async job state.
- Visibility/lease tương đương ở tầng application.
- Không giữ consumer thread vô hạn.

---

## 20. Monitoring dashboard

Dashboard tối thiểu theo queue:

- Queue depth ready.
- Unacked count.
- Total consumers.
- Consumer utilization.
- Publish rate.
- Delivery rate.
- Ack rate.
- Reject/nack rate.
- Redelivery rate.
- Oldest message age.
- DLQ depth.
- Retry queue depth.

Dashboard theo broker:

- Node memory.
- Disk free.
- CPU.
- File descriptors.
- Connections.
- Channels.
- Connection churn.
- Channel churn.
- Flow control.
- Queue leaders.
- Quorum health.
- Network partitions.

### 20.1. Alert examples

Alert khi:

- Oldest message age vượt SLA.
- Ready messages tăng liên tục.
- Unacked tăng liên tục.
- Consumer count bằng zero.
- Redelivery tăng đột biến.
- DLQ depth tăng.
- Confirm latency tăng.
- Node memory gần watermark.
- Disk free thấp.
- Connection/channel churn cao.
- Quorum mất majority.

---

## 21. Operations runbook

### 21.1. Queue backlog tăng

1. Xác nhận queue và environment.
2. Kiểm tra producer rate.
3. Kiểm tra active consumers.
4. Kiểm tra ready và unacked.
5. Kiểm tra processing latency.
6. Kiểm tra downstream.
7. Tăng consumer có kiểm soát.
8. Theo dõi database/downstream.
9. Giảm producer nếu cần.
10. Ghi incident timeline.

Không purge queue chỉ để làm dashboard đẹp.

### 21.2. Unacked tăng

1. Kiểm tra handler duration.
2. Kiểm tra log exception.
3. Kiểm tra connection/channel.
4. Kiểm tra prefetch.
5. Kiểm tra downstream timeout.
6. Xem consumer thread dump.
7. Graceful restart nếu consumer treo.
8. Xác nhận redelivery và idempotency.

### 21.3. Không có consumer

1. Kiểm tra application health.
2. Kiểm tra RabbitMQ connection.
3. Kiểm tra permission.
4. Kiểm tra queue declaration.
5. Kiểm tra startup error.
6. Kiểm tra queue name/vhost.
7. Restart có kiểm soát.
8. Theo dõi backlog sau recovery.

### 21.4. Broker flow control

1. Kiểm tra memory watermark.
2. Kiểm tra disk free.
3. Kiểm tra queue backlog.
4. Kiểm tra consumer throughput.
5. Giảm producer rate.
6. Xử lý queue nóng.
7. Không mở thêm producer thread mù quáng.
8. Ghi lại resource graph.

---

## 22. Common mistakes

### Mistake 1: Tăng consumer mà không đo downstream

Database hoặc API bên ngoài có thể quá tải.

### Mistake 2: Prefetch quá cao

Unacked và memory tăng, fairness giảm.

### Mistake 3: Nghĩ nhiều consumer giữ ordering

Nhiều consumer làm completion order không đảm bảo.

### Mistake 4: Dùng queue depth làm health duy nhất

Depth thấp có thể do message bị mất, reject hoặc producer không phát.

### Mistake 5: Không monitor unacked

Unacked tăng có thể báo handler treo hoặc quên ack.

### Mistake 6: Mở connection mỗi request

Tạo connection churn, latency và broker load.

### Mistake 7: Tạo channel trong hot path

Dễ channel leak và channel churn.

### Mistake 8: Dùng cluster nhưng nghĩ mọi queue tự replicate

Queue type quyết định data replication.

### Mistake 9: Dùng quorum queue cho queue tạm

Tốn resource và không phù hợp lifecycle.

### Mistake 10: Scale producer khi consumer đã quá tải

Backlog tăng nhanh hơn và broker vào flow control.

### Mistake 11: Kill consumer rồi kết luận message mất

Cần kiểm tra unacked, reconnect, redelivery và ack policy.

### Mistake 12: Poll Management API quá dày

Monitoring có thể tự tạo load lên broker.

### Mistake 13: Không ghi benchmark context

Con số throughput không có ý nghĩa nếu thiếu message size, prefetch và processing time.

---

## 23. Security và capacity checklist

- [ ] Connection limit được tính theo instance và concurrency.
- [ ] Channel count được monitor.
- [ ] File descriptor limit đủ.
- [ ] Memory/disk watermark được theo dõi.
- [ ] Producer và consumer dùng permission tối thiểu.
- [ ] Management UI hạn chế truy cập.
- [ ] Metrics không chứa payload nhạy cảm.
- [ ] DLQ có quyền đọc hạn chế.
- [ ] Quorum queue có số member phù hợp.
- [ ] Các node dùng hostname/DNS ổn định.
- [ ] Inter-node ports được mở đúng.
- [ ] Backup và recovery runbook tồn tại.
- [ ] Benchmark không chạy chung broker production.

---

## 24. Definition of Done

- [ ] Chạy được competing consumers.
- [ ] Scale một queue từ 1 lên 3 và 10 consumers.
- [ ] Hiểu prefetch 1, 10 và 50.
- [ ] Đo throughput.
- [ ] Đo p95 latency.
- [ ] Quan sát ready messages.
- [ ] Quan sát unacked messages.
- [ ] Quan sát redelivery.
- [ ] Giải thích fair dispatch.
- [ ] Giải thích ordering limitation.
- [ ] Biết khi nào cần queue riêng.
- [ ] Biết khi nào tăng concurrency.
- [ ] Không mở connection mỗi message.
- [ ] Không tạo channel vô hạn.
- [ ] Hiểu connection/channel limits.
- [ ] Hiểu quorum queue.
- [ ] Biết cluster không tự replicate mọi queue.
- [ ] Kill consumer giữa lúc xử lý.
- [ ] Tạo message xử lý lâu.
- [ ] Ghi benchmark report.
- [ ] Có dashboard queue depth và oldest message age.
- [ ] Có runbook backlog.
- [ ] Có runbook unacked.
- [ ] Có runbook broker flow control.

---

## 25. Câu hỏi tự kiểm tra

1. Competing consumers là gì?
2. Khi nào dùng một queue với nhiều consumer?
3. Khi nào cần queue riêng cho mỗi service?
4. Fair dispatch có đảm bảo completion order không?
5. Prefetch thấp và cao khác nhau thế nào?
6. Vì sao prefetch cao làm unacked tăng?
7. Khi nào prefetch nên bằng 1?
8. Concurrency bị giới hạn bởi những resource nào?
9. Vì sao tăng concurrency có thể làm database quá tải?
10. Queue depth tăng có thể do nguyên nhân nào?
11. Ready và unacked khác nhau thế nào?
12. Ready thấp nhưng unacked cao nói lên điều gì?
13. Vì sao queue depth thấp chưa chắc hệ thống khỏe?
14. Connection và channel khác nhau thế nào?
15. Channel leak có dấu hiệu gì?
16. Connection churn gây vấn đề gì?
17. Broker flow control là gì?
18. Cluster có replicate nội dung mọi queue không?
19. Quorum queue phù hợp use case nào?
20. Quorum queue không phù hợp use case nào?
21. Vì sao quorum cần majority?
22. Node leader fail thì quorum queue xử lý thế nào?
23. Vì sao nhiều consumer làm ordering phức tạp?
24. Kill consumer sau side effect trước ack tạo gì?
25. Benchmark report tối thiểu cần field nào?

---

## 26. Chuẩn bị cho Tuần 7

Giai đoạn tiếp theo chuyển sang AWS SQS:

- Standard Queue.
- FIFO Queue.
- SendMessage, ReceiveMessage, DeleteMessage.
- Visibility timeout.
- Message retention.
- Delivery delay.
- Short polling và long polling.
- Batch send/receive/delete.
- MessageGroupId.
- Deduplication.

Các khái niệm được mang theo:

    queue depth
    consumer throughput
    duplicate delivery
    ordering trade-off
    retry
    dead-letter
    managed operation

SQS có delivery model và operational model khác RabbitMQ. Không bê nguyên cấu hình prefetch, ack và DLQ của RabbitMQ sang SQS mà không phân tích lại.

---

## 27. Tóm tắt

- Competing consumers scale một processing flow.
- Pub/sub cần queue riêng cho từng subscriber.
- Prefetch là trade-off giữa throughput, fairness, memory và ordering.
- Concurrency không được vượt capacity của downstream.
- Queue depth, ready và unacked phải được đọc cùng nhau.
- Oldest message age thường phản ánh SLA tốt hơn depth đơn độc.
- Connection và channel đều có resource cost.
- Connection/channel churn là vấn đề vận hành.
- Broker flow control là tín hiệu resource pressure.
- Nhiều consumer phá strict completion order.
- Quorum queue cung cấp replicated, consistency-oriented queue cho use case quan trọng.
- Cluster không đồng nghĩa mọi queue data tự replicate.
- Node failure có thể gây reconnect, failover, redelivery và unknown confirm.
- Benchmark phải giữ biến cố định và ghi đủ context.
- Vận hành tốt cần dashboard, alert và runbook, không chỉ scale thêm consumer.

---

## Tài liệu tham khảo

- RabbitMQ consumer acknowledgements và prefetch: https://www.rabbitmq.com/docs/confirms
- RabbitMQ channels: https://www.rabbitmq.com/docs/channels
- RabbitMQ connections: https://www.rabbitmq.com/docs/connections
- RabbitMQ monitoring: https://www.rabbitmq.com/docs/monitoring
- RabbitMQ quorum queues: https://www.rabbitmq.com/docs/quorum-queues
- RabbitMQ clustering: https://www.rabbitmq.com/docs/clustering
- RabbitMQ network partitions: https://www.rabbitmq.com/docs/partitions
- RabbitMQ reliability: https://www.rabbitmq.com/docs/reliability
- Spring AMQP listener concurrency: https://docs.spring.io/spring-amqp/reference/amqp/listener-concurrency.html
- Spring AMQP container configuration: https://docs.spring.io/spring-amqp/reference/amqp/containerAttributes.html


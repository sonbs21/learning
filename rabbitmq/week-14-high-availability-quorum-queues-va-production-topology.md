# Tuần 14 — High availability, quorum queues và production topology

> High availability không phải là “có ba node”. HA là khả năng hệ thống tiếp tục phục vụ trong một failure domain đã dự kiến, với mức mất dữ liệu và thời gian gián đoạn nằm trong RPO/RTO đã cam kết.

## 1. Kết quả cần đạt

Sau bài học, bạn có thể:

- Phân biệt RabbitMQ node, cluster, peer discovery, leader và follower.
- Giải thích quorum, majority, network partition và failure domain.
- Phân biệt classic queue với quorum queue.
- Chọn queue type theo durability, HA, workload và chi phí resource.
- Hiểu vì sao queue type phải quyết định ngay khi declare.
- Thiết kế topology durable cho production.
- Lập kế hoạch rolling restart, rolling upgrade và rollback.
- Dùng policy, operator policy, queue limits và resource alarms đúng mục đích.
- Phân biệt replica, backup definition và backup message store.
- Thiết kế multi-AZ, client reconnect và RTO/RPO giả định.
- Thực hiện failure drill: restart node, mất consumer, broker unavailable và disk pressure.

## 2. HA bắt đầu từ RPO và RTO

Trước khi chọn queue type, hãy viết hai con số:

```text
RPO — Recovery Point Objective
Sau sự cố, chấp nhận mất tối đa bao nhiêu dữ liệu?

RTO — Recovery Time Objective
Sau sự cố, chấp nhận mất bao lâu để hệ thống phục vụ lại?
```

Ví dụ:

| Workload | RPO giả định | RTO giả định | Hướng thiết kế |
|---|---:|---:|---|
| Email marketing có thể gửi lại | 5 phút | 30 phút | Durable queue, retry/replay, chi phí vừa phải |
| Payment command | Gần 0 sau publisher confirm | 5 phút | Quorum queue, confirm, idempotent consumer |
| Notification tạm thời | Có thể mất một số message | 15 phút | Có thể dùng topology đơn giản hơn |
| Audit event | Gần 0 | 15 phút | Replication, backup definition, replay strategy |

Không thể nói “queue này phải HA” nếu chưa nói rõ dữ liệu nào phải tồn tại và lúc nào hệ thống phải quay lại phục vụ.

## 3. Node, cluster và topology

### Node

Một RabbitMQ node là một runtime instance có:

- node name;
- data directory;
- listener AMQP/management;
- metadata và queue processes;
- kết nối đến các node khác nếu thuộc cluster.

### Cluster

Cluster là nhóm RabbitMQ node chia sẻ metadata và phối hợp để vận hành topology. Cluster không tự động biến mọi queue thành replicated queue.

Đây là hiểu lầm nguy hiểm:

```text
3 RabbitMQ nodes
!=
mọi queue có 3 bản sao
```

Queue type, replication policy/group size và placement mới quyết định dữ liệu queue nằm ở đâu.

### Client connection

Application connection đến một node cụ thể. Nếu node đó chết, client không “tự biết” node khác để chuyển sang trừ khi:

- connection URL/client config có nhiều endpoint;
- DNS/load balancer/service discovery trả endpoint còn khỏe;
- client có reconnect và recovery đúng cách;
- topology declare/binding được xử lý lại an toàn.

HA ở broker mà client chỉ biết một hostname/node vẫn có thể trở thành downtime ở application.

## 4. Peer discovery và cluster formation

Node mới cần biết peer để gia nhập cluster. Có thể dùng:

- static configuration;
- DNS-based discovery;
- cloud/Kubernetes peer discovery;
- orchestration/operator phù hợp môi trường.

Peer discovery giải quyết việc tìm node, không giải quyết toàn bộ HA. Vẫn phải thiết kế:

- Erlang cookie hoặc cơ chế xác thực node;
- network port giữa các node;
- node name ổn định;
- persistent data volume;
- hostname/DNS resolution;
- failure domain và placement;
- quy trình rejoin node sau khi mất kết nối.

Một node “thấy được” peer chưa chắc đã đủ điều kiện phục vụ queue replica. Phải kiểm tra cluster status và trạng thái replica thực tế.

## 5. Leader, follower và majority

Quorum queue dùng mô hình leader/follower dựa trên Raft:

```text
Producer -> leader
              |
              +--> follower A
              +--> follower B

Consumer <- leader
```

Leader xử lý operation của queue. Follower giữ bản sao log và tham gia đồng thuận. Khi leader không còn khả dụng, một follower trong majority có thể được bầu làm leader.

Với `N` member, quorum là:

```text
majority = floor(N / 2) + 1
```

| Số member | Majority cần để tiếp tục ghi |
|---:|---:|
| 1 | 1 |
| 3 | 2 |
| 5 | 3 |
| 7 | 4 |

Vì vậy số member nên là số lẻ trong đa số trường hợp. Thêm member không miễn phí: cần network, disk I/O, CPU và thời gian replication.

## 6. Network partition

Network partition xảy ra khi node vẫn có thể chạy process nhưng không thể giao tiếp ổn định với peer. Đây khác với node crash.

```text
Node A ----X---- Node B
   |              |
   +--- Node C ---+
```

Trong hệ thống consensus, mục tiêu là tránh hai phía cùng nghĩ mình có quyền ghi. Điều đó có thể khiến hệ thống từ chối một số operation để bảo vệ consistency.

Khi partition xảy ra, cần trả lời:

- phía nào còn majority?
- queue leader đang ở đâu?
- producer confirm có bị delay hoặc negative không?
- consumer có bị cancel/reconnect không?
- partition được chữa bằng network hay cần operator can thiệp?
- node cũ khi quay lại có được phép rejoin không?

Không tự ý “force join”, xóa data directory hoặc xóa node khỏi cluster khi chưa có runbook. Các thao tác đó có thể làm mất dữ liệu hoặc làm failure lan rộng.

Kiểm tra cluster:

```bash
rabbitmq-diagnostics cluster_status
rabbitmq-diagnostics status
rabbitmq-diagnostics check_if_node_is_quorum_critical
```

Kiểm tra quorum queue cụ thể:

```bash
rabbitmq-queues quorum_status orders.commands
```

Tên command và output cần được kiểm tra theo RabbitMQ version đang chạy.

## 7. Classic queue và quorum queue

### Classic queue

Classic queue là queue type truyền thống. Một classic queue không tự nhiên được replicate chỉ vì broker nằm trong cluster.

Phù hợp khi:

- workload không yêu cầu replicated queue;
- queue ngắn hạn hoặc có thể tạo lại;
- muốn latency/resource footprint thấp hơn;
- durability và recovery strategy đã được chấp nhận rõ.

Không được suy luận rằng durable classic queue đồng nghĩa với HA. `durable=true` nói về việc queue definition tồn tại qua restart; nó không đảm bảo queue có bản sao trên node khác.

### Quorum queue

Quorum queue là durable, replicated queue dựa trên Raft. Đây thường là lựa chọn phù hợp cho queue lâu dài, quan trọng và cần khả năng chịu failure node.

Đặc điểm chính:

- có leader và replica members;
- cần majority để tiến hành một số operation;
- có chi phí replication và consensus;
- cần nhiều disk/network hơn queue không replicated;
- phù hợp với message cần data safety;
- không phải đáp án cho mọi queue.

Quorum queue không biến message processing thành exactly-once. Consumer vẫn có thể xử lý message rồi crash trước ack, dẫn đến redelivery. Application vẫn cần idempotency.

## 8. Queue type phải quyết định từ đầu

Khi declare quorum queue, client đặt queue argument:

```java
Map<String, Object> arguments = Map.of(
    "x-queue-type", "quorum"
);

channel.queueDeclare(
    "orders.commands",
    true,
    false,
    false,
    arguments
);
```

Queue type không phải setting có thể đổi tùy ý bằng policy trên queue đã có dữ liệu. Nếu topology đã tạo queue classic với cùng tên, declare lại với type quorum có thể fail vì property mismatch.

Migration an toàn thường là:

```text
1. Tạo queue mới với tên/type mới.
2. Bind queue mới vào exchange.
3. Chuyển producer/consumer theo kế hoạch.
4. Drain hoặc replay message theo policy.
5. Xác nhận không còn consumer/producer dùng queue cũ.
6. Xóa queue cũ sau thời gian giữ an toàn.
```

Không xóa queue production để “declare lại cho đúng type” nếu chưa có kế hoạch message loss và rollback.

## 9. Quorum group size và placement

Quorum queue thường có group size nhỏ, phổ biến là ba member cho nhu cầu HA cơ bản. Group size lớn hơn làm tăng:

- replication traffic;
- disk write;
- resource usage;
- thời gian recovery;
- độ phức tạp khi vận hành.

Group size phải phù hợp failure domain. Ba replica trên cùng một availability zone không bảo vệ được failure của zone đó.

```text
Sai:
AZ-a: node-1, node-2, node-3

Tốt hơn:
AZ-a: node-1
AZ-b: node-2
AZ-c: node-3
```

Multi-AZ có trade-off về latency. Nếu các AZ quá xa hoặc network không ổn định, quorum write có thể chậm và dễ bị ảnh hưởng bởi partition. Chỉ đặt cluster xuyên vùng khi latency, network contract và failure model đã được kiểm chứng.

## 10. Durable topology

Production topology phải xác định rõ:

```text
vhost
exchange durable/type
queue durable/type
binding
dead-letter exchange
retry policy
queue limits
operator policy
owner
```

Ví dụ topology order:

```text
/orders-prod
  orders.events              topic exchange, durable
  orders.commands            direct exchange, durable
  order-created.payment      quorum queue, durable
  order-created.notification quorum queue, durable
  orders.dlx                 topic exchange, durable
  orders.dlq                 quorum queue, durable
```

Tên, arguments và policy phải được quản lý như artifact versioned. Không để mỗi service production tự tạo topology khác nhau theo thứ tự startup ngẫu nhiên.

## 11. Policy, operator policy và queue limits

### Policy

Policy áp dụng cấu hình động cho resource matching pattern, ví dụ:

- dead-letter exchange;
- message TTL;
- queue length limit;
- leader locator;
- delivery limit theo khả năng/version.

Ví dụ minh họa:

```bash
rabbitmqctl set_policy orders-dlx '^orders\\..*$' \
  '{"dead-letter-exchange":"orders.dlx"}' \
  --apply-to queues
```

### Operator policy

Operator policy là lớp policy do operator/platform áp đặt để bảo vệ cluster. Application team không nên có khả năng vượt qua các giới hạn bảo vệ như maximum queue length hoặc TTL tối đa.

Nguyên tắc:

- application policy mô tả nhu cầu service;
- operator policy đặt guardrail platform;
- giá trị cuối cùng phải được kiểm tra theo precedence và version RabbitMQ;
- không dùng policy thay cho thiết kế topology rõ ràng.

### Queue limits

Queue limit giúp ngăn một lỗi consumer làm đầy disk/memory:

- giới hạn số message;
- giới hạn tổng byte;
- TTL;
- delivery limit nếu phù hợp;
- DLX hoặc drop behavior có chủ đích.

Limit không phải cách che giấu lỗi. Khi message bị drop hoặc dead-letter, phải có metric, alert và runbook.

## 12. Resource alarms

RabbitMQ có thể phát alarm khi tài nguyên vượt ngưỡng:

- memory watermark;
- disk free limit;
- file descriptors hoặc process limits;
- resource khác tùy môi trường/version.

Alarm có thể làm broker block publisher hoặc gây cascade latency. Khi alarm xảy ra:

```text
1. Xác định alarm là memory, disk hay connection/resource.
2. Kiểm tra queue ready/unacked và message age.
3. Kiểm tra consumer/ack rate.
4. Kiểm tra disk growth, log và file descriptor.
5. Giảm ingress hoặc xử lý downstream nếu cần.
6. Không xóa dữ liệu tùy tiện để alarm biến mất.
```

Đặc biệt với quorum queue, queue dài vừa tốn message storage vừa tốn index/WAL-related resources. Đừng đợi disk đầy mới đặt queue limit.

## 13. Quorum queue và publisher confirms

Publisher confirm trả lời producer rằng broker đã xử lý publish tới mức nào. Với quorum queue, message được confirm chỉ khi đạt điều kiện an toàn theo cơ chế của queue/replication.

Nhưng:

```text
confirm != business transaction
confirm != consumer đã xử lý
confirm != exactly-once
```

Producer vẫn cần:

- bật publisher confirms;
- xử lý nack/returned message;
- retry có giới hạn;
- idempotency key/message ID;
- transactional outbox nếu publish gắn với database transaction.

Message chưa được confirm có thể mất khi producer, network hoặc node gặp sự cố. Đừng đánh dấu order “đã phát event” chỉ vì gọi `basic.publish` thành công trên client.

## 14. Client reconnect và recovery

Client production cần xử lý:

- TCP connection closed;
- channel closed;
- leader election;
- node unavailable;
- consumer cancelled;
- topology redeclare;
- publisher confirm pending;
- message redelivery.

Một chiến lược reconnect tốt:

```text
1. Phát hiện connection failure.
2. Backoff có jitter, không reconnect thundering herd.
3. Chọn endpoint còn khả dụng.
4. Mở connection/channel mới.
5. Redeclare topology idempotently nếu client chịu trách nhiệm.
6. Re-register consumers.
7. Đánh dấu publish pending chưa confirm để retry an toàn.
8. Theo dõi duplicate/redelivery bằng message ID.
```

Không reconnect trong vòng lặp bận:

```text
while (true) {
    connect();
}
```

Nó có thể làm broker quá tải đúng lúc broker đang recovery.

## 15. Load balancing và connection placement

Có thể dùng:

- nhiều hostname trong client config;
- DNS round-robin có health check phù hợp;
- load balancer TCP;
- Kubernetes Service/operator.

Nhưng load balancer không hiểu đầy đủ trạng thái queue leader. Nó chỉ phân phối connection. Sau khi kết nối, client vẫn có thể cần nói chuyện với node đang host leader và broker sẽ route operation nội bộ.

Tránh tạo một connection cho mỗi message. Thông thường:

- connection là tài nguyên tương đối đắt;
- channel nhẹ hơn connection;
- producer/consumer dùng connection/channel lifecycle rõ;
- prefetch và concurrency được giới hạn.

HA topology tốt nhưng connection strategy tệ vẫn có thể tạo outage.

## 16. Rolling restart

Rolling restart là restart từng node, chờ node quay lại healthy rồi mới chuyển sang node tiếp theo.

### Pre-check

```text
[ ] Cluster không có alarm.
[ ] Quorum queue có majority online.
[ ] Không có replica sync bất thường.
[ ] Disk còn đủ dung lượng.
[ ] Monitoring và alert đang hoạt động.
[ ] Client có reconnect.
[ ] Có maintenance window và owner.
[ ] Có rollback/abort condition.
```

### Quy trình

```text
1. Chọn node cần restart.
2. Kiểm tra node đó không làm mất majority của queue quan trọng.
3. Drain traffic nếu platform hỗ trợ.
4. Stop/restart node.
5. Xác nhận node join cluster.
6. Kiểm tra quorum status, alarms, ready/unacked và consumer.
7. Chờ replica catch up.
8. Lặp lại node tiếp theo.
```

Không restart hai node cùng lúc trong cluster ba node nếu điều đó làm quorum mất majority.

## 17. Rolling upgrade

Rolling upgrade không phải “thay binary lần lượt rồi cầu may”. Trước upgrade phải kiểm tra:

- version path có được hỗ trợ không;
- Erlang/runtime compatibility;
- release notes;
- plugin compatibility;
- stable feature flags;
- capacity khi mixed-version;
- client compatibility;
- backup definition và rollback plan.

### Quy trình upgrade tổng quát

```text
1. Xác định current/target version và supported path.
2. Upgrade node lên patch mới nhất trong series nếu cần.
3. Đảm bảo cluster healthy, không alarm.
4. Enable feature flags cần thiết theo tài liệu version.
5. Upgrade từng node một.
6. Sau mỗi node: kiểm tra cluster, queues, alarms, client reconnect.
7. Chỉ tiếp tục khi node đã rejoin và replica ổn định.
8. Enable stable feature flags sau khi đủ node ở version mới.
9. Chạy smoke test publish/consume/confirm.
```

Rollback không luôn là downgrade binary. RabbitMQ không nên được xem là hệ thống có downgrade tùy ý. Khi rủi ro cao, blue-green cluster thường an toàn hơn: dựng cluster mới, migrate topology/message theo kế hoạch, rồi chuyển traffic.

## 18. Backup không phải replication

Đây là phần phải nhớ:

```text
Quorum replication = chịu node failure trong thời gian vận hành
Backup              = khôi phục sau data corruption, operator error hoặc disaster
```

Replication không bảo vệ khỏi:

- application publish message sai trên mọi replica;
- operator xóa queue;
- message bị ack nhầm;
- credential bị compromise;
- lỗi logic làm dữ liệu sai;
- mất cả cluster/region.

### Backup definitions

Definitions gồm topology và quản trị như:

- users;
- vhosts;
- permissions;
- exchanges;
- queues;
- bindings;
- policies;
- parameters.

Export definitions:

```bash
rabbitmqctl export_definitions /secure/backup/definitions.json
```

Import vào broker/cluster khôi phục:

```bash
rabbitmqctl import_definitions /secure/backup/definitions.json
```

File definitions có thể chứa thông tin nhạy cảm tùy version/configuration. Bảo vệ file như artifact confidential, không commit public.

### Backup message store

Backup message store là bài toán khác và khó hơn. Snapshot khi node đang chạy có thể không nhất quán và message thường có lifecycle ngắn. Nếu RPO yêu cầu giữ business data, hãy cân nhắc:

- transactional outbox trong database;
- event log/object storage;
- upstream replay;
- backup strategy được RabbitMQ version hỗ trợ;
- quiesce/quorum-aware procedure.

Đừng nói “đã có quorum nên không cần backup”.

## 19. Production topology mẫu

### Mô hình ba node, ba AZ

```text
                 private network
       +-------------------------------+
       |                               |
    AZ-a                           AZ-b                           AZ-c
 rabbit-1                        rabbit-2                       rabbit-3
    |                                |                              |
    +-------- quorum replica --------+---------- quorum replica -----+

 Producers/consumers -> private endpoint / service discovery
 Operators            -> VPN/bastion -> HTTPS Management UI
 Monitoring           -> private metrics endpoint
```

### Failure assumptions

| Sự cố | Kỳ vọng |
|---|---|
| Một consumer chết | Message redeliver, consumer khác tiếp tục |
| Một node chết | Queue có majority tiếp tục sau leader election |
| Một AZ mất | Chỉ chịu được nếu replica và cluster placement phù hợp |
| Hai node trong cluster 3 node mất | Mất majority; có thể mất write availability |
| Toàn region mất | Cần DR/backup strategy ngoài cluster |
| Operator xóa queue | Replica không giúp; cần audit, protection và recovery plan |

Không gọi topology này “zero downtime” nếu chưa đo leader election, client reconnect và dependency recovery.

## 20. Failure domain

Failure domain là phạm vi có thể hỏng cùng lúc:

- process;
- VM;
- host;
- rack;
- availability zone;
- region;
- network segment;
- storage system.

Nếu ba node cùng nằm trên một host vật lý, ba replica không bảo vệ được host failure. Nếu volume của cả cluster phụ thuộc cùng một storage backend, storage failure có thể đánh sập toàn bộ replicas.

Artifact cần có:

```text
node -> host -> rack -> AZ -> region -> network/storage dependency
```

## 21. Lab 1 — Restart một node

### Mục tiêu

Chứng minh một quorum queue chịu được restart tuần tự của một node mà không mất message đã confirm.

### Chuẩn bị

```text
[ ] Lab cluster có ít nhất ba node.
[ ] Có quorum queue test.
[ ] Producer bật publisher confirms.
[ ] Consumer có message ID và idempotency log.
[ ] Có dashboard queue/connection/alarms.
```

### Thực hiện

```text
1. Publish một batch message có sequence number.
2. Ghi nhận message đã confirm.
3. Dừng một node.
4. Theo dõi leader election, confirm latency và consumer reconnect.
5. Khởi động node lại.
6. Kiểm tra node rejoin và replica catch up.
7. Đối chiếu message sequence trước/sau.
```

### Kết quả cần ghi

```text
node bị restart:
thời điểm stop/start:
thời gian mất kết nối:
thời gian leader election:
số message redelivered:
số message mất:
confirm latency p95:
alarms:
```

## 22. Lab 2 — Ngắt kết nối consumer

### Mục tiêu

Phân biệt broker vẫn khỏe với consumer đã mất.

### Thực hiện

```text
1. Chạy một producer liên tục ở tốc độ thấp.
2. Chạy hai consumer cùng queue.
3. Dừng một consumer bất ngờ.
4. Theo dõi unacked, ready, consumer count và redelivery.
5. Khởi động consumer lại.
6. Kiểm tra reconnect, redelivery và idempotency.
```

Đừng dùng `autoAck=true` cho lab này nếu muốn quan sát message pending khi consumer chết.

## 23. Lab 3 — Broker unavailable trong vài phút

Chỉ thực hiện ở lab/staging có approval. Không dùng production để thử ngắt broker.

```text
1. Dừng endpoint hoặc chặn traffic theo cách reversible.
2. Quan sát producer confirm timeout/error.
3. Quan sát client backoff và reconnect.
4. Khôi phục broker.
5. Kiểm tra publish retry có tạo duplicate không.
6. Kiểm tra consumer redelivery và DLQ.
```

Đặc biệt ghi lại message ở trạng thái “publish đã gửi nhưng chưa confirm”. Đây là vùng cần retry/idempotency, không được giả định thành công hay thất bại tuyệt đối.

## 24. Lab 4 — Tạo disk pressure

Không dùng lệnh phá hoại tùy tiện trên host. Dùng giới hạn storage của container/VM hoặc môi trường lab được tạo riêng.

### Mục tiêu

Quan sát disk alarm và tác động lên publisher.

### Thực hiện

```text
1. Đặt disk quota lab thấp có chủ đích.
2. Publish message với tốc độ kiểm soát.
3. Theo dõi disk free, alarm, publish confirm và queue depth.
4. Dừng producer hoặc giảm rate.
5. Xử lý message/cleanup đúng runbook.
6. Xác nhận alarm clear và broker quay lại trạng thái bình thường.
```

Không xóa trực tiếp file data/WAL của RabbitMQ để giải phóng disk. Hành động đó có thể làm hỏng dữ liệu.

## 25. Lab 5 — Khôi phục topology

### Mục tiêu

Khôi phục vhost, users, permissions, exchanges, queues, bindings và policies trên một broker/cluster lab mới.

### Quy trình

```bash
rabbitmqctl export_definitions definitions.json

# Trên môi trường lab đích
rabbitmqctl import_definitions definitions.json
```

Sau import, kiểm tra:

```text
[ ] Vhost tồn tại.
[ ] User tồn tại.
[ ] Permission đúng.
[ ] Exchange đúng type/durable.
[ ] Queue đúng type/durable/arguments.
[ ] Binding đúng routing key.
[ ] DLX/policy đúng.
[ ] TLS/network/client config trỏ đúng endpoint.
```

Sau đó chạy message flow end-to-end: publish -> confirm -> deliver -> ack.

## 26. Rolling upgrade procedure

Tài liệu upgrade production nên có dạng runbook:

```text
Mục tiêu version:
Current version:
Target version:
Supported upgrade path:
Erlang/runtime requirement:
Feature flags cần enable:
Backup definition location:
On-call owner:
Abort condition:
Rollback/blue-green plan:
```

### Abort condition mẫu

```text
- quorum critical trên queue quan trọng;
- alarm kéo dài;
- node không rejoin sau timeout;
- replica sync không tiến triển;
- confirm latency vượt ngưỡng;
- consumer reconnect loop;
- message loss hoặc unexpected nack.
```

Một node upgrade xong nhưng chưa rejoin đầy đủ thì chưa được coi là thành công.

## 27. Những lỗi thiết kế thường gặp

### Có cluster nhưng dùng classic queue và nghĩ đã replicated

Cluster chỉ cung cấp nền tảng node/metadata. Queue type và replica placement mới quyết định data HA.

### Chọn quorum cho mọi queue

Quorum queue có chi phí replication, disk và consensus. Queue ngắn hạn hoặc workload có thể tái tạo không nhất thiết cần cùng mức bảo vệ.

### Replicate tất cả replica trong một AZ

Không bảo vệ được AZ failure. Hãy map replica với failure domain thật.

### Restart nhiều node cùng lúc

Có thể làm mất majority và mất write availability. Rolling operation phải tính quorum trước.

### Không bật publisher confirms

Broker có thể nhận publish nhưng producer không có bằng chứng an toàn để retry. Confirm là một phần của data safety contract.

### Dùng reconnect không có backoff

Client storm có thể làm broker đang recovery càng quá tải.

### Nhầm replication với backup

Xóa nhầm queue hoặc publish dữ liệu sai sẽ được replicate rất nhanh. Backup/restore và replay strategy vẫn cần thiết.

### Đổi queue type bằng cách xóa queue

Có thể làm mất message. Migration cần queue mới, dual routing hoặc drain/replay có kiểm soát.

## 28. Checklist production

### Cluster và failure domain

```text
[ ] Có sơ đồ node -> host -> AZ -> region.
[ ] Node name, DNS và data volume ổn định.
[ ] Peer discovery/rejoin đã test.
[ ] Cluster node chạy version/runtime tương thích.
[ ] Network giữa node có latency và connectivity phù hợp.
[ ] Có runbook network partition.
```

### Queue và topology

```text
[ ] Queue type được quyết định ngay từ đầu.
[ ] Queue quan trọng dùng quorum khi requirement cần replicated HA.
[ ] Replica không cùng một failure domain.
[ ] Exchange/queue/binding durable theo requirement.
[ ] DLX/retry/limits có chủ đích.
[ ] Operator policy bảo vệ memory/disk/queue growth.
[ ] Topology được version hóa và có owner.
```

### Client

```text
[ ] Client có nhiều endpoint hoặc service discovery.
[ ] Reconnect có backoff và jitter.
[ ] Consumer re-register sau reconnect.
[ ] Publisher confirms và returned message được xử lý.
[ ] Pending publish có retry/idempotency.
[ ] Consumer xử lý redelivery an toàn.
```

### Backup và upgrade

```text
[ ] Có backup definitions định kỳ.
[ ] Definitions backup được restore thử trong lab.
[ ] Có chiến lược cho business message recovery.
[ ] Có RPO/RTO được chấp nhận.
[ ] Có rolling upgrade procedure.
[ ] Có abort condition và rollback/blue-green plan.
[ ] Không dùng downgrade tùy tiện.
```

## 29. Deliverable cuối tuần

Tạo thư mục:

```text
rabbitmq/week-14/
├── README.md
├── production-topology.md
├── failure-domains.md
├── rpo-rto.md
├── topology-definitions.json
├── rolling-restart-runbook.md
├── rolling-upgrade-runbook.md
├── backup-restore-runbook.md
└── failure-drill-results.md
```

`README.md` phải có:

- sơ đồ cluster và failure domain;
- queue nào là classic, queue nào là quorum;
- lý do chọn từng type;
- RPO/RTO giả định;
- cách client reconnect;
- cách kiểm tra quorum/alarms;
- điều kiện dừng rolling operation.

Không commit file definitions chứa password/secret production thật.

## 30. Tiêu chí hoàn thành

Bạn hoàn thành Tuần 14 khi:

- Vẽ được failure domain và nêu được RPO/RTO giả định.
- Biết dữ liệu nào còn tồn tại sau restart một node.
- Phân biệt rõ classic queue, quorum queue, durable và replicated.
- Hiểu majority, leader election và network partition.
- Có procedure rolling restart và rolling upgrade.
- Có backup/restore definitions và test restore.
- Thực hiện được bốn failure drill trong lab.
- Không nhầm queue replication với backup.

## 31. Câu hỏi tự kiểm tra

### Câu 1

Ba RabbitMQ node có tự động tạo ba bản sao cho mọi queue không?

**Đáp án:** Không. Queue type và replication configuration quyết định việc này.

### Câu 2

Quorum queue ba member cần bao nhiêu member để có majority?

**Đáp án:** Hai member.

### Câu 3

Durable classic queue có đồng nghĩa với HA không?

**Đáp án:** Không. Durable chủ yếu nói về việc queue definition tồn tại qua broker restart; không tự tạo replica trên node khác.

### Câu 4

Vì sao không đổi queue type tùy tiện trên queue đang có dữ liệu?

**Đáp án:** Queue type là thuộc tính của queue declaration và không thể biến đổi an toàn chỉ bằng policy. Cần migration sang queue mới và xử lý message/rollback.

### Câu 5

Quorum replication có thay thế backup không?

**Đáp án:** Không. Replication chống node failure khi vận hành; backup phục vụ data corruption, operator error và disaster recovery.

### Câu 6

Khi nào rolling upgrade phải dừng lại?

**Đáp án:** Khi node không rejoin, cluster mất quorum, alarm kéo dài, replica sync lỗi, confirm latency bất thường hoặc có dấu hiệu message loss.

## 32. Tài liệu tham khảo chính thức

- [RabbitMQ Quorum Queues](https://www.rabbitmq.com/docs/quorum-queues)
- [RabbitMQ Clustering](https://www.rabbitmq.com/docs/clustering)
- [Cluster Formation and Peer Discovery](https://www.rabbitmq.com/docs/cluster-formation)
- [Clustering and Network Partitions](https://www.rabbitmq.com/docs/partitions)
- [Upgrading RabbitMQ](https://www.rabbitmq.com/docs/upgrade)
- [RabbitMQ Backup and Restore](https://www.rabbitmq.com/docs/backup)

## 33. Chuẩn bị cho Tuần 15

Sau khi có topology HA và failure drill, bước tiếp theo là kiểm chứng bằng test: contract test, integration test, retry/DLQ test và chaos engineering. Một topology chỉ được coi là production-ready sau khi hành vi lúc node chết, network chập chờn, consumer crash và disk pressure đã được quan sát bằng bằng chứng thực tế.

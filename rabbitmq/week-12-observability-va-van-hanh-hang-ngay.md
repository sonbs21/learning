# Tuần 12 — Observability và vận hành hằng ngày

> Broker đang UP chưa có nghĩa hệ thống khỏe. Queue có thể tích tụ, consumer không ack, downstream timeout, DLQ tăng hoặc producer mất message trước confirm. Observability giúp phát hiện, định vị và xử lý các trạng thái đó.

## 1. Kết quả cần đạt

Sau bài học, bạn có thể:

- Theo dõi ready, unacked, total, publish/deliver/ack rate và redelivery rate.
- Theo dõi consumer, connection, channel và blocked connection.
- Hiểu memory watermark, disk free limit và broker alarms.
- Phân biệt Management UI với Prometheus/Grafana.
- Viết structured log có eventId và correlationId.
- Trace HTTP -> outbox -> RabbitMQ -> consumer.
- Định nghĩa SLI/SLO cho message processing.
- Thiết kế alert có owner, threshold, impact và runbook.
- Điều tra broker unreachable, queue buildup và DLQ spike.
- Phân biệt broker issue với downstream/database issue.

## 2. Observability gồm những gì?

Ba trụ cột:

~~~text
Metrics
  hệ thống đang có vấn đề ở đâu và mức độ nào

Logs
  chuyện gì xảy ra với một message cụ thể

Traces
  request đi qua service nào và mất thời gian ở đâu
~~~

Management UI tốt để xem nhanh một node/queue. Prometheus/Grafana tốt để lưu time series, trend, alert và so sánh nhiều environment.

## 3. Health model

~~~text
Producer
  -> publish rate
  -> confirm rate/latency
  -> returned message
  -> exchange route
  -> queue ready/unacked
  -> consumer delivery/ack
  -> business processing
  -> database/downstream
  -> DLQ/retry
~~~

Broker health, traffic health, queue health, consumer health và business health phải được xem cùng nhau.

Ví dụ cảnh báo:

~~~text
ready tăng
unacked tăng
ack rate giảm
oldest message age tăng
~~~

Hệ thống có thể đang incident dù process RabbitMQ vẫn chạy.

## 4. Metrics RabbitMQ

### Queue metrics

| Metric | Ý nghĩa |
|---|---|
| messages_ready | Message chờ consumer |
| messages_unacked | Message đã giao nhưng chưa ack |
| total messages | Ready + unacked |
| consumers | Số consumer trên queue |
| deliver rate | Tốc độ broker giao |
| ack rate | Tốc độ consumer ack |
| redeliver rate | Tốc độ giao lại |
| oldest message age | Message cũ nhất đang chờ |
| queue memory | Bộ nhớ queue |
| consumer utilisation | Mức sử dụng consumer |

### Broker metrics

- node uptime;
- memory used;
- memory watermark/alarm;
- disk free và disk alarm;
- file descriptors;
- sockets;
- connections;
- channels;
- blocked connections;
- publish/deliver/ack rates;
- confirm latency;
- cluster/node health nếu có.

### Application metrics

- business processing duration;
- database latency;
- downstream HTTP latency;
- downstream error/timeout/429;
- outbox pending;
- inbox duplicate;
- retry;
- DLQ;
- serialization error;
- unknown event/version;
- consumer restart.

## 5. Ready, unacked và total

Ready là message chưa được giao.

~~~text
ready tăng liên tục
  -> producer nhanh hơn consumer
  hoặc consumer không đọc
~~~

Unacked là message consumer đã nhận nhưng chưa ack. Unacked tăng có thể do processing chậm, prefetch cao, consumer treo, downstream timeout hoặc ack bị quên.

Total thường là ready + unacked.

~~~text
ready=0, unacked=10000
  -> consumer đang giữ nhiều message
  -> không có nghĩa đã xử lý xong
~~~

## 6. Rate metrics

~~~text
publish rate  = producer gửi
deliver rate  = broker giao
ack rate      = consumer xác nhận
redeliver rate = broker giao lại
~~~

| Quan sát | Khả năng |
|---|---|
| Publish > deliver | Queue backlog tăng |
| Deliver > ack | Unacked tăng |
| Ack giảm, redeliver tăng | Consumer lỗi/crash/requeue |
| Confirm latency tăng | Broker/network/disk bận |
| Deliver có nhưng business log không có | Converter/listener/app issue |

Rate phải xem cùng window thời gian, không nhìn một điểm đơn lẻ.

## 7. Consumer, connection và channel

### Consumer count

Consumer count bằng 0 trên queue quan trọng là cảnh báo. Consumer count cao nhưng ack rate thấp có thể do downstream, thread pool, database pool hoặc prefetch.

### Connection count

Tăng bất thường có thể là connection leak hoặc mỗi request mở connection mới.

### Channel count

Channel cao có thể do tạo channel liên tục, concurrency quá cao, recovery hoặc leak.

### Blocked connection

Khi thấy blocked connection:

1. kiểm tra memory/disk alarm;
2. kiểm tra queue depth;
3. kiểm tra producer rate;
4. kiểm tra message size;
5. kiểm tra consumer/ack rate;
6. không chỉ restart app.

## 8. Memory, disk và alarm

Memory watermark giúp broker bảo vệ node. Khi memory vượt ngưỡng, publisher connection có thể bị block.

Disk free limit bảo vệ dữ liệu persistent và metadata. Disk thấp có thể làm broker alarm và block publisher.

Kiểm tra:

- disk free và limit;
- queue backlog;
- message size;
- DLQ/retry volume;
- log/container volume;
- persistent message volume.

Không tăng threshold mù quáng. Tìm root cause trước khi đổi limit.

## 9. Management UI và Prometheus/Grafana

### Management UI

Tốt cho:

- state hiện tại;
- connection/channel/queue;
- binding;
- publish/get trong lab;
- debug nhanh.

Hạn chế:

- lịch sử/trend hạn chế;
- alert không mạnh;
- thao tác thủ công có thể đổi state;
- khó tổng hợp nhiều environment.

### Prometheus/Grafana

Prometheus lưu time series, PromQL và alert. Grafana cung cấp dashboard, drill-down, deployment annotation và view theo team.

## 10. Metrics endpoint

Kiểm tra plugin:

~~~bash
docker compose exec rabbitmq rabbitmq-plugins list -e
~~~

Expose metrics port nếu image/config cho phép:

~~~yaml
services:
  rabbitmq:
    image: rabbitmq:4-management
    ports:
      - "5672:5672"
      - "15672:15672"
      - "15692:15692"
~~~

Kiểm tra:

~~~bash
curl http://localhost:15692/metrics
~~~

Port và metric name có thể khác theo RabbitMQ version. Không expose metrics công khai không có network policy.

## 11. Prometheus config

~~~yaml
global:
  scrape_interval: 15s

scrape_configs:
  - job_name: rabbitmq
    static_configs:
      - targets:
          - rabbitmq:15692

  - job_name: order-service
    metrics_path: /actuator/prometheus
    static_configs:
      - targets:
          - order-service:8080
~~~

Tên label/metric cần lấy từ endpoint thực tế của version đang chạy.

## 12. Dashboard đề xuất

### Broker health

- node up;
- uptime;
- memory/watermark;
- disk/limit;
- alarms;
- connections;
- channels;
- blocked connections;
- file descriptors.

### Traffic

- publish/deliver/ack rate;
- redelivery rate;
- confirm latency;
- returned publish;
- reject/nack.

### Queues

- ready;
- unacked;
- total;
- consumers;
- deliver/ack rate;
- oldest message age;
- utilisation;
- queue memory.

### Errors

- retry;
- DLQ;
- serialization;
- unknown event/version;
- processing failure;
- downstream timeout/429.

### Application

- processing p50/p95/p99;
- database latency;
- HTTP latency;
- outbox pending;
- inbox duplicate;
- JVM heap/GC/thread pool;
- connection pool.

## 13. PromQL mẫu

Tên metric chính xác có thể khác theo exporter/version.

~~~promql
rabbitmq_queue_messages_ready
~~~

~~~promql
sum by (queue) (
  rabbitmq_queue_messages_ready
)
~~~

~~~promql
deriv(rabbitmq_queue_messages_ready[10m])
~~~

~~~promql
rabbitmq_queue_consumers
~~~

~~~promql
rate(rabbitmq_channel_messages_redelivered_total[5m])
~~~

Application processing p95:

~~~promql
histogram_quantile(
  0.95,
  sum by (le) (
    rate(order_message_processing_seconds_bucket[5m])
  )
)
~~~

## 14. Structured logging

Log dạng JSON:

~~~json
{
  "timestamp": "2026-10-04T10:00:00.123Z",
  "level": "INFO",
  "service": "email-service",
  "environment": "dev",
  "event": "message_processed",
  "eventId": "evt-100",
  "eventType": "order.created",
  "correlationId": "corr-100",
  "causationId": "cmd-100",
  "queue": "orders.email",
  "routingKey": "order.created",
  "processingMs": 85,
  "result": "success"
}
~~~

Không log password, token, payment data, PII không cần thiết hoặc payload rất lớn.

## 15. Correlation ID và tracing

Flow:

~~~text
HTTP request
  correlationId=corr-100
    -> outbox insert
    -> publisher log
    -> RabbitMQ event
    -> consumer log
    -> downstream call
~~~

Phân biệt:

~~~text
eventId       = một event
correlationId = một workflow
causationId   = message trực tiếp gây ra event
traceparent   = distributed trace context
~~~

Trace có thể gồm HTTP span, DB span, outbox span, publish/confirm span, consumer span và downstream span. Không truyền token trong tracing header.

## 16. SLI và SLO

### Processing latency

~~~text
consumer ack timestamp - message created/accepted timestamp
~~~

Tách queue wait, processing và end-to-end.

### Success rate

~~~text
successful processed messages / eligible messages
~~~

Quy định rõ permanent invalid/DLQ có tính failure hay không.

### DLQ rate

~~~text
messages sent to DLQ / messages processed
~~~

DLQ rate bằng zero không luôn tốt nếu listener ack quá sớm hoặc nuốt lỗi.

### Oldest message age

Oldest age thường phản ánh user impact tốt hơn queue depth.

## 17. Alert design

Alert cần:

~~~text
name
severity
owner
threshold
duration
impact
runbook link
dashboard link
silence policy
~~~

Ví dụ:

~~~text
Alert: OrdersQueueOldestMessageTooOld
Severity: warning
Owner: order-platform
Condition: oldest age > 60s for 10m
Impact: order processing delayed
Runbook: queue-buildup.md
~~~

Severity:

| Level | Ý nghĩa |
|---|---|
| Info | Cần quan sát |
| Warning | Có nguy cơ vi phạm SLO |
| Critical | Đang ảnh hưởng user |
| Page | Cần xử lý ngay |

Dùng duration/rate để tránh page vì spike ngắn.

## 18. Runbook: broker unreachable

### Triệu chứng

- connection refused/timeout;
- publish/consume rate bằng zero;
- Management UI không truy cập;
- node down alert.

### Kiểm tra

1. Container/process/node.
2. DNS/network/firewall.
3. AMQP và Management port.
4. Host disk/memory.
5. RabbitMQ logs.
6. Cluster/node health.
7. Client retry/fail-fast.
8. Outbox pending và publisher confirm.

### Không được làm

- xóa volume;
- reset broker;
- purge queue;
- replay toàn bộ không kiểm tra;
- tắt alarm để che triệu chứng.

### Sau khôi phục

Kiểm tra confirm latency, ready/unacked, consumer count, redelivery, DLQ và outbox FAILED. Ghi incident timeline.

## 19. Runbook: queue buildup

### Triệu chứng

~~~text
ready tăng
oldest age tăng
ack rate thấp
~~~

### Điều tra

1. Consumer count có đủ không?
2. Consumer log có exception không?
3. Unacked có tăng không?
4. Processing p95/p99 có tăng không?
5. Database/downstream có chậm không?
6. Prefetch có quá cao không?
7. Producer có burst không?
8. Broker có alarm không?

### Hành động

- sửa downstream;
- giảm producer rate;
- tăng consumer có kiểm soát;
- giảm prefetch nếu hoard;
- pause consumer để bảo vệ downstream;
- không tăng vô hạn;
- không purge queue khi chưa có quyết định mất dữ liệu.

Kết thúc khi ready/oldest giảm, ack ổn định và error/redelivery không tăng.

## 20. Runbook: DLQ spike

1. Lấy mẫu message, không purge.
2. Nhóm theo eventType/version/errorCode.
3. Kiểm tra deployment gần nhất.
4. Kiểm tra schema/contract.
5. Kiểm tra downstream.
6. Kiểm tra retry count/x-death.
7. Phân loại permanent hay transient.
8. Sửa nguyên nhân.
9. Replay batch nhỏ có rate limit.
10. Chỉ xóa DLQ sau publish confirm và audit.

## 21. Phân biệt broker và downstream issue

| Dấu hiệu | Broker issue | Downstream/app issue |
|---|---|---|
| Connection | Refused/blocked | Bình thường |
| Confirm | Chậm/nack | Bình thường |
| Queue ready | Có thể tăng | Thường tăng |
| Consumer count | Mất consumer | Vẫn có |
| Unacked | Có thể tăng | Tăng rõ |
| Processing latency | Có thể bình thường | Tăng |
| DB/HTTP errors | Không nhất thiết | Tăng |
| Memory/disk alarm | Có thể active | Thường không |

Queue buildup có thể do producer burst, consumer bug, database chậm hoặc broker alarm.

## 22. Lab 1 — Export metrics

1. Bật/kiểm tra Prometheus plugin.
2. Expose metrics endpoint.
3. Chạy Prometheus.
4. Scrape RabbitMQ.
5. Tìm queue/connection metrics.
6. Query ready, unacked và consumers.
7. Ghi metric name/version thực tế.

## 23. Lab 2 — Grafana dashboard

Tạo panel:

1. Broker health.
2. Publish/deliver/ack.
3. Ready/unacked.
4. Consumer count.
5. Oldest message age.
6. Redelivery.
7. DLQ count/rate.
8. Processing p95/p99.
9. Outbox pending.
10. Memory/disk alarm.

Thêm annotation deploy, broker restart, đổi prefetch, đổi concurrency và replay DLQ.

## 24. Lab 3 — Alert oldest message age

1. Làm consumer chậm hoặc dừng.
2. Đo oldest age tăng.
3. Tạo warning khi vượt 60 giây trong 10 phút.
4. Tạo critical khi vượt SLO nghiêm trọng.
5. Link dashboard/runbook.
6. Khôi phục consumer.
7. Xác nhận alert resolve.

## 25. Lab 4 — Trace một order

Trace:

~~~text
HTTP POST /orders
  -> order-service
  -> database order/outbox
  -> outbox publisher
  -> RabbitMQ publish/confirm
  -> email consumer
  -> email downstream
~~~

Dùng eventId, correlationId, causationId và traceparent. Mục tiêu là từ eventId tìm được producer log, routing key, queue và consumer log.

## 26. Lab 5 — Viết runbook

Tạo:

~~~text
runbooks/broker-unreachable.md
runbooks/queue-buildup.md
runbooks/dlq-spike.md
~~~

Mỗi file cần triệu chứng, impact, metric, kiểm tra 5 phút đầu, hành động cấm, mitigation, recovery verification, owner và post-incident actions.

## 27. Lỗi observability thường gặp

- Chỉ xem queue depth, không xem oldest age.
- Chỉ alert broker down, không alert queue buildup.
- Không gắn eventId/correlationId.
- Log full payload chứa PII.
- Metric label cardinality vô hạn vì dùng eventId.
- Không tách retry/DLQ rate.
- Không đo p95/p99.
- Không có owner/runbook.
- Dùng Get/Purge trong production khi điều tra.
- Không phân biệt ready/unacked.
- Không ghi deployment annotation.
- Không có outbox/inbox metrics.
- Page cho mọi spike ngắn.

## 28. Checklist

- [ ] Broker dashboard có memory/disk/alarm.
- [ ] Traffic dashboard có publish/deliver/ack/redelivery.
- [ ] Queue dashboard có ready/unacked/consumer/oldest age.
- [ ] DLQ/retry dashboard có rate/count.
- [ ] Application có processing p95/p99.
- [ ] Logs là structured JSON.
- [ ] Log có eventId/correlationId.
- [ ] Trace header được truyền có kiểm soát.
- [ ] Metric không có cardinality vô hạn.
- [ ] Alert có owner/threshold/impact/runbook.
- [ ] Có ba runbook chính.
- [ ] Có outbox/inbox metrics.
- [ ] Không log secret/PII.
- [ ] Có deployment annotation.
- [ ] Có post-incident review.

## 29. Deliverable cuối tuần

Tạo:

~~~text
rabbitmq/week-12/
├── README.md
├── prometheus.yml
├── dashboards/
│   └── rabbitmq-overview.json
├── alerts/
│   └── rabbitmq-alerts.yml
├── runbooks/
│   ├── broker-unreachable.md
│   ├── queue-buildup.md
│   └── dlq-spike.md
└── examples/
    └── structured-message-log.json
~~~

README cần có:

1. Dashboard architecture.
2. Metrics catalog.
3. PromQL/queries thực tế.
4. SLI/SLO definitions.
5. Alert matrix owner/threshold/severity.
6. Structured log schema.
7. Correlation/tracing strategy.
8. Ba runbook.
9. Kết quả năm lab.
10. Phân biệt broker issue và downstream issue.

## 30. Tiêu chí tự kiểm tra

1. Ready và unacked khác nhau thế nào?
2. Vì sao oldest message age quan trọng?
3. Publish, deliver và ack rate chẩn đoán gì?
4. Consumer count bằng zero có nghĩa gì?
5. Blocked connection liên quan alarm ra sao?
6. Management UI khác Prometheus/Grafana thế nào?
7. Structured log cần eventId/correlationId để làm gì?
8. correlationId khác eventId thế nào?
9. SLI processing latency đo từ đâu tới đâu?
10. Vì sao cần p95/p99?
11. Alert tốt cần những trường nào?
12. Broker unreachable, hành động nào nguy hiểm?
13. Queue buildup kiểm tra broker/consumer/downstream ra sao?
14. DLQ spike điều tra theo error/version thế nào?
15. Vì sao metric label không dùng eventId?
16. Từ eventId tìm producer và consumer log thế nào?
17. Làm sao phân biệt broker issue với downstream issue?
18. Vì sao dashboard không thay runbook?

### Đáp án cần nhớ

> Observability phải nối metrics, logs và traces. Theo dõi ready, unacked, rates, consumer count, oldest age, redelivery, DLQ, memory/disk alarm và application latency. Alert cần owner, threshold, impact và runbook. Khi điều tra, dùng eventId/correlationId đi từ producer tới consumer và kiểm tra cả broker lẫn downstream.

## 31. Chuẩn bị cho Tuần 13

Tuần sau tập trung vào security và governance:

- user, tag và permission configure/write/read;
- virtual host;
- least privilege;
- TLS AMQP/HTTPS Management;
- secret rotation;
- network policy;
- audit log;
- naming convention và ownership;
- không đưa secret/PII vào message.

Giữ dashboard, alert và runbook của tuần này để dùng khi thêm security, cluster và high availability.

# Tuần 16: Observability cho queue

## 1. Mục tiêu

Sau bài học này, bạn có thể:

- Phân biệt metrics, logs, traces và monitoring.
- Đo queue depth, consumer lag và oldest message age.
- Đo publish rate, delivery rate, process rate, ack rate và redelivery rate.
- Đo error rate, retry count, DLQ count và processing latency p50/p95/p99.
- Dùng correlation ID, causation ID, message ID và trace propagation.
- Thiết kế structured logging.
- Xây dashboard tối thiểu cho RabbitMQ, Kafka và SQS.
- Đặt alert theo SLO và business impact.
- Điều tra backlog, slow consumer, hot partition, poison message và retry storm.
- Chạy lab thay đổi số consumer, prefetch, consumer crash và message xử lý lâu.

Nguyên tắc:

> Observability là khả năng trả lời message đang ở đâu, chậm ở bước nào, vì sao chậm, ảnh hưởng tới ai và cần hành động gì.

---

## 2. Monitoring và observability

Monitoring theo dõi các lỗi đã biết:

- Queue depth vượt ngưỡng.
- Consumer lag tăng.
- Error rate tăng.
- DLQ có message.
- Broker disk gần đầy.

Observability dùng metrics, logs và traces để điều tra vấn đề chưa biết trước.

Ví dụ câu hỏi:

- Vì sao OrderCreated chậm?
- Message đã vào broker lúc nào?
- Đã được consumer nào nhận?
- Đã retry mấy lần?
- Đang chờ database hay payment provider?
- Side effect đã commit chưa?

Ba tín hiệu chính:

- Metrics: xu hướng, rate, count, latency và saturation.
- Logs: chi tiết một message hoặc một failure.
- Traces: chuỗi thời gian xuyên nhiều service.

OpenTelemetry khuyến khích liên kết logs với trace context để truy nguyên xuyên hệ thống. [OpenTelemetry logging](https://opentelemetry.io/docs/specs/otel/logs/)

---

## 3. Message lifecycle

Một message thường đi qua:

    created
      -> published
      -> accepted by broker
      -> available
      -> delivered
      -> processing
      -> side effect
      -> acknowledged or committed
      -> completed

Khi lỗi:

    processing
      -> retry
      -> delayed retry
      -> dead-letter
      -> quarantine
      -> replay

Nên có timestamp ở các boundary:

- created_at.
- published_at.
- delivered_at.
- processing_started_at.
- processing_finished_at.
- acknowledged_at.
- dead_lettered_at.

Từ đó tính:

- Queue wait time.
- Delivery wait time.
- Processing time.
- Downstream wait time.
- Retry delay.
- End-to-end latency.
- Time to DLQ.

Event envelope nên có:

- eventId.
- eventType.
- eventVersion.
- createdAt.
- correlationId.
- causationId.
- traceId.
- producer.
- aggregateId.

---

## 4. Bốn câu hỏi vận hành

### Có backlog không?

Xem:

- Queue depth.
- Ready messages.
- Consumer lag.
- Oldest message age.
- SQS visible messages.

### Consumer có đang xử lý không?

Xem:

- Consumer count.
- In-flight hoặc unacked.
- Delivery rate.
- Process rate.
- Ack/delete/commit rate.
- Processing latency.

### Consumer có xử lý thành công không?

Xem:

- Error rate.
- Retry count.
- Redelivery rate.
- DLQ/DLT count.
- Business success rate.

### Bottleneck ở đâu?

Phân biệt:

- Producer phát quá nhanh.
- Consumer thiếu capacity.
- Downstream chậm.
- Broker chậm.
- Hot partition hoặc hot key.
- Poison message.
- Deployment hoặc config regression.
- Database hoặc network lỗi.

Không kết luận consumer chậm chỉ vì queue depth tăng.

---

## 5. Queue depth

Queue depth là số message đang chờ hoặc đang xử lý, tùy metric.

Có thể phân rã:

    total backlog
      = ready messages
      + in-flight messages
      + delayed messages

Queue depth tăng do:

- Producer rate tăng.
- Consumer rate giảm.
- Consumer chết.
- Downstream chậm.
- Retry quay lại main queue.
- Poison message.
- Prefetch hoặc concurrency thấp.
- Broker throttling.

Queue depth giảm không luôn nghĩa hệ thống khỏe:

- Producer rate có thể đã về 0.
- Consumer có thể ack trước side effect.
- Error handler có thể discard message.
- DLQ có thể đang tăng.
- Dashboard có thể đang thiếu dữ liệu.

Backlog time gần đúng:

    backlog_time = backlog_messages / catch_up_rate

    catch_up_rate = consumer_rate - producer_rate

Nếu catch_up_rate nhỏ hơn hoặc bằng 0, backlog không tự giảm.

Luôn xem queue depth cùng:

- Publish rate.
- Process rate.
- Ack/delete/commit rate.
- Error rate.
- DLQ rate.
- Oldest age.
- Business outcome.

---

## 6. Consumer lag

Kafka lag thường là:

    lag = log_end_offset - committed_offset

Theo dõi:

- Lag tổng.
- Lag theo group.
- Lag theo topic.
- Lag theo partition.
- Max lag.
- Lag rate.
- Record age.
- Commit rate.
- Rebalance count.
- Consumer assignment.

Lag tăng đều có thể do:

- Producer rate lớn hơn processing rate.
- Consumer thiếu instance.
- Downstream chậm.
- Batch poll quá lớn.
- Database chậm.
- Retry làm giảm capacity.
- Partition nóng.

Một partition lag cao có thể do:

- Key skew.
- Một aggregate có volume bất thường.
- Consumer assignment lệch.
- Poison record.
- Partition leader hoặc broker chậm.
- Logic xử lý riêng aggregate đó bị chậm.

Lag thấp nhưng business chậm có thể do:

- Commit trước side effect.
- Message bị drop.
- Consumer đọc nhầm group.
- Producer không phát event.
- Metric chỉ đo committed offset.

Lag không thay thế business success metric.

---

## 7. Oldest message age

Oldest message age là thời gian message lâu nhất đã chờ hoặc chưa hoàn tất.

Ví dụ:

- Queue depth 10.000, age 2 giây: consumer có thể vẫn bắt kịp.
- Queue depth 100, age 20 phút: có thể có poison message hoặc partition block.

Cần thống nhất age tính từ:

- Message created.
- Message published.
- Message vào queue.
- Consumer nhận message.

Mỗi mốc trả lời một câu hỏi khác nhau.

SQS có ApproximateAgeOfOldestMessage và nhiều metrics queue-level. Các giá trị có tính approximate vì kiến trúc phân tán; poison message và DLQ có thể ảnh hưởng cách diễn giải. [SQS CloudWatch metrics](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-available-cloudwatch-metrics.html)

Alert age phải dựa trên SLA:

- Warning: age vượt 30 giây.
- Critical: age vượt 5 phút.

Không dùng cùng threshold cho notification, analytics và payment.

---

## 8. Throughput và rate

Các rate cơ bản:

- Publish rate.
- Receive hoặc delivery rate.
- Processing start rate.
- Processing success rate.
- Ack/delete/commit rate.
- Retry rate.
- DLQ rate.
- Error rate.
- Redelivery rate.

Công thức:

    net_backlog_rate = incoming_rate - completion_rate

    success_rate = successful_messages / attempted_messages

    error_rate = failed_attempts / attempted_attempts

    redelivery_rate = redelivered_deliveries / total_deliveries

    retry_ratio = retry_attempts / original_attempts

Rate phải có time window:

- 1 phút cho incident.
- 5 phút cho trend.
- 1 giờ cho capacity.
- 1 ngày cho seasonality.

Nếu publish rate lớn hơn completion rate, backlog tăng. Nếu completion rate lớn hơn publish rate, backlog giảm.

Nếu cả hai bằng 0, có thể là queue idle hoặc telemetry bị lỗi. Cần kiểm tra message activity và scrape health.

---

## 9. Ack, commit và delete rate

Tùy broker:

- RabbitMQ: consumer acknowledgement.
- Kafka: offset commit.
- SQS: DeleteMessage hoặc batch delete.

Ack, commit hoặc delete chỉ nói về delivery state, không luôn nói về business success.

Ack rate thấp có thể do:

- Handler chậm.
- Consumer crash.
- Downstream timeout.
- Manual ack bị quên.
- Prefetch quá lớn.
- Transaction chưa commit.

Ack rate cao nhưng business success thấp có thể do:

- Ack trước side effect.
- Error bị swallow.
- Message bị discard.
- Commit offset sớm.
- Metric ack bị hiểu nhầm là processed.

Dashboard nên phân biệt:

- delivered.
- processed_success.
- processed_error.
- acknowledged.
- redelivered.
- dead_lettered.

Không gọi tất cả là processed.

---

## 10. Redelivery rate

Redelivery tăng do:

- Consumer crash trước ack.
- Visibility timeout hết.
- Broker connection reset.
- Handler throw exception.
- Retry.
- Manual replay.
- SQS message không bị delete.

Redelivery không luôn là bug; at-least-once delivery có thể tạo duplicate bình thường.

Nên phân loại redelivery:

- Timeout.
- Application error.
- Broker reconnect.
- Replay.
- Visibility timeout.
- Unknown outcome.

Nếu redelivery cao kéo dài:

- Kiểm tra processing p99.
- Kiểm tra visibility hoặc ack deadline.
- Kiểm tra downstream.
- Kiểm tra error handler.
- Kiểm tra consumer crash.
- Kiểm tra idempotency hit rate.

---

## 11. Error rate

Error rate cần định nghĩa denominator:

- Error trên mỗi attempt.
- Error trên mỗi unique message.
- Error trên mỗi business operation.

Một message fail bốn lần rồi vào DLQ có thể được tính:

- Bốn attempt failures.
- Một failed message.
- Một failed business operation.

Nên có metric riêng:

- handler_attempt_failure_total.
- message_failed_total.
- business_operation_failed_total.
- retry_exhausted_total.
- dead_lettered_total.

Phân loại exception:

- Schema.
- Validation.
- Business.
- Database.
- Network.
- Timeout.
- Authentication.
- Dependency.
- Unknown.

Không dùng exception message raw làm metric label vì cardinality sẽ tăng không kiểm soát.

---

## 12. Retry count và DLQ count

Theo dõi:

- Retry attempts per second.
- Retry ratio.
- Average retry count.
- P95 retry count.
- Messages exhausting retry.
- Retry theo dependency.
- Retry theo event type.
- Retry theo consumer version.

DLQ dashboard:

- Current DLQ depth.
- DLQ arrival rate.
- Oldest DLQ age.
- DLQ by failure category.
- DLQ replay rate.
- Replay success/failure.
- Quarantine count.

DLQ count bằng 0 không luôn tốt:

- Không có traffic.
- Error handler discard.
- Metric chưa scrape.
- Consumer chưa chạy.
- Message bị mất trước khi vào DLQ.

DLQ count tăng là symptom cần triage, không phải con số để xóa.

---

## 13. Processing latency

Tách latency:

    delivery_to_handler_start
    handler_start_to_handler_end
    database_time
    downstream_time
    serialization_time
    handler_end_to_ack
    total_processing_time

Đo:

- p50: trải nghiệm phổ biến.
- p95: tail latency đáng chú ý.
- p99: outlier và capacity risk.
- max: dùng để điều tra, không dùng một mình đặt SLA.

Ví dụ:

    p50 = 20 ms
    p95 = 200 ms
    p99 = 4 seconds
    max = 2 minutes

Average có thể là 80 ms nhưng vẫn che giấu p99 tệ.

End-to-end latency:

    created_to_completed
      = broker_wait
      + delivery_wait
      + processing
      + retry_delay
      + downstream_wait

Nếu processing tốt nhưng oldest age xấu, bottleneck có thể là capacity hoặc scheduling. Nếu queue wait tốt nhưng processing p99 xấu, bottleneck nằm trong handler hoặc dependency.

---

## 14. Metrics cardinality

Label tốt:

    service
    environment
    queue
    topic
    consumer_group
    event_type
    result
    failure_category
    dependency

Label nguy hiểm:

    message_id
    order_id
    customer_id
    trace_id
    full_exception
    raw_payload

Identity cụ thể nên ở logs hoặc traces. Metrics dùng cho aggregate.

Nguyên tắc:

- Metrics trả lời how many và how often.
- Logs trả lời message cụ thể đã xảy ra gì.
- Traces trả lời thời gian nằm ở đâu.

High cardinality làm tăng cost, query chậm và có thể rò rỉ dữ liệu.

---

## 15. Structured logging

Log nên là JSON với field ổn định:

    timestamp
    level
    service
    environment
    event
    message_id
    event_id
    event_type
    consumer
    queue_or_topic
    partition
    offset
    correlation_id
    causation_id
    trace_id
    attempt
    processing_ms
    result
    failure_category

Event name gợi ý:

- message_received.
- processing_started.
- processing_succeeded.
- processing_failed.
- retry_scheduled.
- dead_lettered.
- replayed.

Không log:

- Password.
- Token.
- Authorization header.
- Card data.
- Raw PII không cần thiết.
- Full payload trong mọi attempt.
- Stack trace lặp lại hàng nghìn lần.

Dùng payload hash khi cần nhận diện payload mà không log raw body.

---

## 16. Correlation ID và causation ID

Correlation ID liên kết business flow:

    HTTP request
      correlationId = C1

    OrderCreated
      correlationId = C1

    PaymentRequested
      correlationId = C1

Causation ID liên kết message trực tiếp gây ra message hiện tại:

    OrderCreated.causationId = CreateOrder command id
    PaymentRequested.causationId = OrderCreated event id

Ngoài ra:

- Event ID dùng deduplication.
- Trace ID dùng timing và distributed trace.
- Aggregate ID dùng business lookup.

Không dùng một ID cho mọi mục đích.

---

## 17. Trace propagation

Trace context nên đi qua:

    HTTP
      -> application
      -> outbox
      -> broker headers
      -> consumer
      -> database
      -> downstream HTTP

Khi publish:

- Inject trace context vào headers.
- Giữ correlationId và causationId như business metadata.
- Gắn producer span.
- Gắn topic/queue và event type.
- Không nhét toàn trace context vào payload business.

Khi consume:

- Extract trace context.
- Tạo consumer span.
- Gắn message ID.
- Gắn topic/queue, partition, offset nếu có.
- Tạo child spans cho database và dependency.
- Gắn retry attempt.
- Kết thúc span ở boundary ack/commit nếu đó là phần cần đo.

Một message retry có thể tạo một span cho mỗi delivery attempt. Trace retention thường ngắn hơn event retention, nên eventId và correlationId vẫn phải tồn tại độc lập.

---

## 18. RabbitMQ observability

RabbitMQ cần theo dõi:

- Ready messages.
- Unacked messages.
- Total messages.
- Consumer count.
- Publish rate.
- Delivery rate.
- Ack rate.
- Redelivery.
- Consumer capacity.
- Connection count.
- Channel count.
- Memory.
- Disk alarm.
- Node health.
- Network links.

RabbitMQ monitoring docs phân biệt messages ready, unacknowledged, total và publish/delivery rates. [RabbitMQ monitoring](https://www.rabbitmq.com/docs/monitoring)

### Ready messages

Message chờ consumer nhận.

Ready tăng có thể do:

- Không có consumer.
- Consumer xử lý chậm.
- Prefetch hoặc concurrency thấp.
- Producer rate cao.
- Poison message.
- Broker flow control.

### Unacked messages

Message đã delivery nhưng chưa ack.

Unacked tăng có thể do:

- Handler chậm.
- Consumer treo.
- Ack bị quên.
- Prefetch quá lớn.
- Downstream timeout.
- Database transaction mở lâu.

### Consumer count

Consumer count bằng 0 là critical với queue cần xử lý liên tục.

Consumer count đúng nhưng throughput bằng 0 có thể do:

- Handler blocked.
- Connection còn sống nhưng thread pool chết.
- Queue không có message.
- Consumer đọc sai queue.
- Broker credit hoặc prefetch issue.

### Consumer capacity

Capacity thấp có thể chỉ ra:

- Consumer bận.
- Downstream chậm.
- Prefetch không phù hợp.
- Thiếu consumer.
- Queue idle nên cần xem cùng publish rate.

RabbitMQ Prometheus plugin cung cấp metric cho ready, unacked, total messages, consumers và consumer capacity. [RabbitMQ Prometheus](https://www.rabbitmq.com/docs/4.2/prometheus)

---

## 19. RabbitMQ dashboard

### Queue health

- Ready.
- Unacked.
- Total.
- Oldest message age do application đo.
- Consumer count.
- Queue state.

### Flow rate

- Publish.
- Deliver/get.
- Ack.
- Reject/nack.
- Redelivery.

### Consumer

- Consumer count.
- Consumer capacity.
- Processing p95/p99.
- Error.
- Retry.
- DLQ.

### Broker

- Connections.
- Channels.
- Memory.
- Disk.
- Disk alarm.
- Node status.
- Network partition.

Derived:

    net_backlog_rate = publish_rate - ack_rate
    unacked_ratio = unacked / max(total, 1)
    redelivery_ratio = redelivered / max(delivered, 1)
    consumer_efficiency = ack_rate / max(delivery_rate, 1)

Không dùng raw queue depth một mình.

---

## 20. Kafka observability

### Producer

- Record send rate.
- Byte rate.
- Request rate.
- Request latency.
- Error rate.
- Retry rate.
- Batch size.
- Compression rate.
- Buffer wait.
- Connection errors.

### Broker

- Bytes in/out.
- Messages in.
- Request queue time.
- Request latency.
- Network processor idle.
- Request handler idle.
- Under-replicated partitions.
- Offline partitions.
- ISR shrink/expand.
- Disk usage.
- Replication health.

### Consumer

- Records consumed rate.
- Bytes consumed rate.
- Fetch latency.
- Commit rate.
- Consumer lag.
- Lag max.
- Rebalance count.
- Poll interval violations.
- Processing latency.
- Retry topic lag.
- DLT count.

Consumer lag dashboard tối thiểu:

    group
    topic
    partition
    current_offset
    log_end_offset
    lag
    lag_rate
    record_age
    consumer_instance
    assigned_status

Luôn hiển thị top partitions by lag; tổng lag có thể che partition nóng.

---

## 21. Kafka dashboard

### Consumer group

- Lag total.
- Lag max.
- Lag by partition.
- Records consumed.
- Commit rate.
- Rebalance count.
- Assignment.
- Processing p95/p99.

### Producer

- Records/second.
- Bytes/second.
- Send latency p95.
- Retry rate.
- Error rate.
- Batch size.
- Compression ratio.

### Broker

- Bytes in/out.
- Request latency p95/p99.
- Under-replicated partitions.
- Offline partitions.
- ISR changes.
- Disk usage.
- Network and handler idle.

### Topic and DLT

- Records in.
- Records out.
- Retry topic lag.
- DLT arrival rate.
- DLT oldest age.
- DLT replay rate.

Derived:

    lag_seconds ~= lag_records / effective_processing_rate
    retry_ratio = retry_attempts / original_records
    partition_skew = max(partition_rate) / average(partition_rate)

Nếu UnderReplicatedPartitions hoặc disk latency tăng, scale consumer có thể không giải quyết được.

---

## 22. SQS observability

SQS CloudWatch metrics thường gồm:

- ApproximateNumberOfMessagesVisible.
- ApproximateNumberOfMessagesNotVisible.
- ApproximateAgeOfOldestMessage.
- NumberOfMessagesReceived.
- NumberOfMessagesDeleted.
- NumberOfEmptyReceives.
- NumberOfMessagesSent.
- ApproximateNumberOfMessagesDelayed.
- FIFO in-flight groups.
- DLQ visible messages.

SQS metrics có tính approximate và có điều kiện active queue. [SQS available metrics](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-available-cloudwatch-metrics.html)

### Visible messages

Gần với backlog đang sẵn sàng nhận.

### Not visible messages

Message đã receive nhưng chưa delete hoặc visibility timeout chưa hết.

Not visible cao kéo dài có thể chỉ ra:

- Consumer xử lý lâu.
- Visibility timeout quá dài.
- Consumer treo.
- Delete thất bại.
- Downstream chậm.

### Empty receives

Có thể do:

- Queue rỗng.
- Short polling.
- Poll frequency quá cao.
- Consumer thừa.
- Traffic burst không đều.

### DLQ

Với DLQ, nên xem visible messages và oldest age. AWS lưu ý automatic redrive có thể làm NumberOfMessagesSent và NumberOfMessagesReceived không khớp. [SQS monitoring](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/monitoring-using-cloudwatch.html)

---

## 23. SQS dashboard

### Source queue

- Visible.
- Not visible.
- Oldest age.
- Delayed messages.
- Receive rate.
- Delete rate.
- Empty receives.
- FIFO in-flight groups.

### DLQ

- Visible DLQ messages.
- Oldest DLQ age.
- Arrival trend.
- Redrive rate.
- Replay success/failure.
- Permission failures.

### Consumer application

- Processing p50/p95/p99.
- Visibility extensions.
- Delete failures.
- Retry count.
- Business success.
- Idempotency conflicts.
- Downstream latency.

Derived:

    backlog_rate = receive_rate - delete_rate
    delete_success_ratio = deleted / max(received, 1)
    inflight_pressure = not_visible / max(visible + not_visible, 1)

Không phản ứng với một điểm metric lẻ; dùng nhiều data points liên tiếp.

---

## 24. Business metrics

Technical metrics chưa đủ. Cần theo dõi:

- Orders created.
- Orders confirmed.
- Orders failed.
- Payments captured.
- Payments failed.
- Inventory reserved.
- Emails sent.
- Refunds completed.
- Saga stuck.
- Duplicate business operation.
- Manual intervention.

Ví dụ:

    queue appears healthy
    payment_captured_rate = 0

Có thể consumer ack sớm, đọc sai event hoặc side effect payment đang fail.

Business metrics nên có:

- Rate.
- Success ratio.
- Failure ratio.
- Age/SLA breach.
- Domain hoặc tenant nếu cardinality kiểm soát được.
- Version/deploy correlation.

---

## 25. Alerting principles

Alert tốt cần:

- Symptom rõ.
- Impact hoặc risk.
- Owner.
- Runbook.
- Threshold theo SLA.
- Duration.
- Severity.
- Dashboard link.
- Trace/log query link.

### Critical

- Consumer count bằng 0 khi incoming rate lớn hơn 0.
- Oldest age vượt critical SLA.
- DLQ tăng nhanh ở payment.
- Error rate cao và business success giảm.
- Kafka offline partition.
- RabbitMQ disk alarm.
- SQS oldest age vượt SLA.

### Warning

- Lag tăng.
- Retry ratio tăng.
- Unacked tăng.
- Consumer capacity giảm.
- DLQ có message không critical.

Không alert raw threshold mù:

    queue_depth > 1000

Tốt hơn:

    queue_depth tăng liên tục
    and oldest_age vượt SLA
    and completion_rate < incoming_rate
    for sustained interval

---

## 26. SLO và error budget

Ví dụ SLO:

- 99% OrderCreated xử lý trong 60 giây.
- 99.9% PaymentRequested không quá 5 phút tuổi.
- DLT rate dưới 0.01%.
- Duplicate business side effect bằng 0.
- Processing success trên 99.5%.
- Không partition lag quá 10 phút.

Error budget gồm:

- Late messages.
- Failed messages.
- DLT messages.
- Downtime.
- Manual recovery.

SLO nên đo theo business event, không chỉ broker metric.

Nếu queue depth thấp nhưng message age cao, SLO vẫn có thể breach.

---

## 27. Từ alert đến root cause

Quy trình:

    alert
      -> xác định queue/topic/group
      -> xem backlog và oldest age
      -> so sánh incoming và completion rate
      -> xem consumer count và assignment
      -> xem processing p95/p99
      -> xem error/retry/redelivery
      -> xem downstream latency
      -> mở trace của message mẫu
      -> kiểm tra deploy/config change
      -> chọn remediation

### Queue depth tăng, error thấp

Khả năng:

- Producer rate tăng.
- Consumer thiếu capacity.
- Downstream chậm nhưng chưa throw.
- Prefetch/concurrency thấp.
- Partition skew.

### Queue depth tăng, error và retry tăng

Khả năng:

- Dependency outage.
- Timeout sai.
- Retry storm.
- Circuit breaker chưa mở.
- Consumer version lỗi.

### Queue depth thấp, DLQ tăng

Khả năng:

- Schema invalid.
- Error handler discard.
- Retry policy quá ngắn.
- DLT routing sai.

### Một partition lag tăng

Khả năng:

- Hot key.
- Poison record.
- Consumer assignment.
- Downstream riêng aggregate chậm.
- Partition broker có vấn đề.

### Ack cao, business success thấp

Khả năng:

- Ack trước commit.
- Error bị nuốt.
- Side effect async chưa xong.
- Đọc sai topic/group.
- Metric ack bị hiểu nhầm.

---

## 28. Dashboard tối thiểu toàn hệ thống

### SLO row

- Oldest age theo queue quan trọng.
- End-to-end p95/p99.
- Business success.
- DLQ rate.
- SLO breach.

### Flow row

- Publish.
- Delivery.
- Process success.
- Ack/delete/commit.
- Retry.
- Redelivery.

### Backlog row

- RabbitMQ ready/unacked.
- Kafka lag total/max.
- SQS visible/not visible.
- Oldest age.
- Retry depth.
- DLQ depth.

### Consumer row

- Consumer count.
- Consumer capacity.
- Processing p50/p95/p99.
- Error.
- Rebalance/reconnect.
- In-flight work.

### Dependency row

- Database latency/error.
- HTTP latency/error.
- Rate-limit responses.
- Circuit state.
- Connection pool.

### Broker row

- RabbitMQ memory/disk/connections.
- Kafka ISR/offline/request latency.
- SQS throttling/API errors.
- Bytes in/out.

### Deployment row

- Producer version.
- Consumer version.
- Schema version.
- Deploy markers.
- Config change markers.

---

## 29. Lab 1: Chạy 1, 3 và 10 consumers

### Setup

- Queue hoặc topic đủ capacity.
- Payload cố định.
- Producer rate cố định.
- Processing delay cố định.
- Chạy 1, 3 và 10 consumer.

### Đo

- Throughput.
- p50/p95/p99.
- Queue depth hoặc lag.
- Oldest age.
- Ack/delete/commit.
- CPU và memory.
- Redelivery.
- Dependency latency.

### Câu hỏi

- Khi nào tăng consumer không còn giúp?
- Consumer có vượt số partition không?
- Queue giảm nhưng p99 tăng không?
- Bottleneck ở consumer hay downstream?
- Rebalance có tăng không?

### Deliverable

Bảng gồm:

    consumers
    producer_rate
    throughput
    p50
    p95
    p99
    backlog
    oldest_age
    redelivery_rate
    cpu
    memory

---

## 30. Lab 2: Thay đổi prefetch

Thử:

    prefetch = 1
    prefetch = 10
    prefetch = 100

Đo:

- Ready.
- Unacked.
- Throughput.
- Processing latency.
- Consumer fairness.
- Recovery sau kill.
- Memory.

Kỳ vọng:

- Prefetch thấp giảm unacked nhưng có thể giảm throughput.
- Prefetch cao có thể tăng throughput.
- Prefetch cao giữ nhiều message ở consumer chết.
- Message lâu có thể làm fairness xấu.

Không chọn prefetch chỉ theo throughput.

---

## 31. Lab 3: Kill consumer giữa xử lý

Các bước:

1. Gửi message có event ID.
2. Consumer bắt đầu xử lý.
3. Kill process trước ack hoặc commit.
4. Quan sát redelivery.
5. Khởi động consumer lại.
6. Kiểm tra Inbox/idempotency.
7. Đo end-to-end latency.

Log cần có:

    message_received
    processing_started
    process_exit
    message_redelivered
    processing_succeeded
    acknowledged_or_committed

Kỳ vọng:

- Message redeliver theo broker semantics.
- Side effect không duplicate.
- Redelivery tăng.
- Oldest age tăng trong outage.
- Alert không noise với crash rất ngắn.

---

## 32. Lab 4: Message xử lý lâu

Tạo nhóm:

    fast = 20 ms
    normal = 200 ms
    slow = 5 seconds
    stuck = 60 seconds

Đo:

- p50/p95/p99.
- Max processing time.
- Unacked/not visible.
- Visibility hoặc ack deadline.
- Consumer utilization.
- Queue depth.
- Oldest age.
- Timeout.
- Redelivery.

Thử điều chỉnh:

- Prefetch.
- Concurrency.
- Visibility timeout.
- Max poll interval.
- Processing timeout.
- Delayed retry.

Kết luận:

- Timeout phải lớn hơn processing time hợp lệ.
- Timeout phải nhỏ hơn failure detection SLA.
- Message stuck không được giữ worker vô hạn.
- Average không đủ để nhìn message slow.

---

## 33. Lab 5: Hot partition hoặc noisy tenant

Tạo distribution:

    key A = 70 percent
    key B = 10 percent
    key C = 10 percent
    key D = 10 percent

Quan sát:

- Rate theo partition.
- Lag theo partition.
- Consumer assignment.
- Latency theo key.
- Top tenant hoặc aggregate.
- Tổng backlog.

Mục tiêu:

- Phân biệt tổng lag và hot partition.
- Tìm key gây skew.
- Đánh giá đổi partition key.
- Đánh giá trade-off ordering và load balance.

Không dùng từng order ID làm metric label; dùng logs hoặc top-k analysis.

---

## 34. Lab 6: Dashboard và alert

Dashboard cần có:

RabbitMQ:

- Ready.
- Unacked.
- Consumers.
- Publish/delivery/ack.
- Redelivery.
- Capacity.
- Connections/channels.
- Memory/disk.

Kafka:

- Lag total/max/by partition.
- Records in/out.
- Bytes in/out.
- Commit.
- Rebalance.
- Under-replicated/offline.
- Request latency.

SQS:

- Visible.
- Not visible.
- Oldest age.
- Received/deleted.
- Empty receives.
- Delayed.
- DLQ visible.

Application:

- Processing p50/p95/p99.
- Error.
- Retry.
- DLT/DLQ.
- Business success.
- Dependency latency.
- Trace links.

Alert:

- Consumer outage.
- Oldest age vượt SLA.
- Lag tăng liên tục.
- DLQ arrival rate tăng.
- Redelivery ratio tăng.
- Error tăng và business success giảm.
- Broker disk/offline/under-replicated.

---

## 35. Lab 7: Trace một message

Flow:

    Order API
      -> order-service
      -> outbox publisher
      -> broker
      -> inventory-service
      -> inventory DB
      -> InventoryReserved event

Yêu cầu:

- Giữ correlation ID.
- Giữ causation ID.
- Propagate trace context qua headers.
- Tạo span publish.
- Tạo span consume.
- Tạo span database.
- Tạo span downstream.
- Gắn retry attempt.
- Gắn message ID.
- Gắn topic hoặc queue.

Deliverable:

- Một trace hoàn chỉnh.
- Log query theo correlation ID.
- Trace query theo message ID.
- Thời gian ở từng hop.
- Failure point khi kill consumer.

---

## 36. Lab 8: DLQ observability

Tạo DLQ gồm:

    schema failure
    dependency timeout
    code bug
    unknown payment
    poison message

Dashboard:

- DLQ depth.
- Arrival rate.
- Top failure category.
- Oldest DLQ age.
- Replay rate.
- Replay success/failure.
- Quarantine count.
- Message age at dead-letter.
- Consumer version.
- Producer version.

Thực hiện:

1. Triage sample.
2. Sửa một category.
3. Replay canary.
4. Quan sát side effect.
5. Mở rộng replay.
6. Ghi incident timeline.

---

## 37. Metric naming

Tên metric nên nêu rõ scope, unit và type:

    messaging_messages_published_total
    messaging_messages_processed_total
    messaging_message_processing_duration_seconds
    messaging_message_failures_total
    messaging_message_retries_total
    messaging_messages_dead_lettered_total
    messaging_consumer_lag_records
    messaging_oldest_message_age_seconds
    messaging_unacked_messages
    messaging_queue_depth

Dùng:

- Counter cho total events.
- Gauge cho current state.
- Histogram cho latency.

Không dùng một metric name lúc là counter, lúc là gauge tùy service.

---

## 38. Alert examples

### Consumer outage

    consumer_count = 0
    and incoming_rate > 0
    for 5 minutes

### Backlog SLA breach

    oldest_age > SLA
    and incoming_rate > completion_rate
    for sustained interval

### Retry storm

    retry_ratio > threshold
    and dependency_error_rate > threshold
    for 5 minutes

### DLT incident

    dlt_arrival_rate > 0
    or dlt_depth > 0
    for critical flow

### Kafka broker risk

    offline_partitions > 0
    or under_replicated_partitions > 0
    for sustained interval

Alert phải có severity, owner, dashboard, runbook và điều kiện recovery.

---

## 39. Anti-patterns

- Chỉ theo dõi queue depth.
- Chỉ theo dõi average latency.
- Log full payload mọi attempt.
- Dùng message ID làm metric label.
- Alert trên một data point.
- Dùng ack rate làm business success.
- Không có deploy marker.
- Không trace qua broker.
- Dashboard không có owner/runbook.
- Cùng threshold cho mọi queue.
- Không phân biệt zero traffic và zero telemetry.
- Không có DLQ metric.
- Không đo retry ratio.
- Không theo dõi partition-level lag.
- Không gắn version producer/consumer.

---

## 40. Dashboard review checklist

### Correctness

- [ ] Metric định nghĩa rõ.
- [ ] Counter/gauge/histogram dùng đúng.
- [ ] Denominator rõ.
- [ ] Unit rõ.
- [ ] Approximate metric được ghi chú.

### Coverage

- [ ] Producer.
- [ ] Broker.
- [ ] Consumer.
- [ ] Retry.
- [ ] DLQ.
- [ ] Dependency.
- [ ] Business outcome.

### Debuggability

- [ ] Correlation ID.
- [ ] Message ID.
- [ ] Event type/version.
- [ ] Consumer version.
- [ ] Trace link.
- [ ] Original destination.
- [ ] Attempt number.

### Operations

- [ ] Có SLO.
- [ ] Có severity.
- [ ] Có runbook.
- [ ] Có owner.
- [ ] Có deploy marker.
- [ ] Có retention policy.
- [ ] Không high-cardinality label nguy hiểm.
- [ ] Không log secret hoặc PII thừa.

---

## 41. Bài tập

### Bài 1

Queue depth tăng từ 0 lên 100.000 trong 10 phút. Nêu năm nguyên nhân và metric để phân biệt.

### Bài 2

Lag tổng ổn định nhưng một partition tăng liên tục. Viết investigation flow.

### Bài 3

Ack rate cao, error thấp nhưng PaymentCaptured bằng 0. Nêu các khả năng.

### Bài 4

Thiết kế metric names cho producer, consumer, retry và DLQ.

### Bài 5

Thiết kế structured log schema cho message failure.

### Bài 6

p50 là 20 ms, p95 là 200 ms, p99 là 10 giây. Đề xuất điều tra.

### Bài 7

SQS visible thấp nhưng oldest age cao. Phân tích poison message, FIFO group và approximate metric.

### Bài 8

RabbitMQ unacked tăng nhưng ready giảm. Consumer có thể đang làm gì?

### Bài 9

Thiết kế alert cho consumer outage, backlog breach, retry storm, DLT incident và Kafka broker risk.

### Bài 10

Vẽ dashboard cho Order Processing Platform.

---

## 42. Câu hỏi tự kiểm tra

1. Monitoring khác observability thế nào?
2. Queue depth không trả lời được câu hỏi nào?
3. Kafka lag được tính ra sao?
4. Oldest message age phản ánh điều gì?
5. Vì sao p95 và p99 quan trọng?
6. Ack rate khác business success thế nào?
7. Redelivery có luôn là lỗi không?
8. Vì sao error rate cần denominator?
9. Correlation ID và causation ID khác nhau thế nào?
10. Trace context đi qua broker bằng cách nào?
11. Vì sao message ID không nên là metric label?
12. RabbitMQ ready và unacked khác nhau thế nào?
13. Kafka tổng lag thấp nhưng partition lag cao nói lên điều gì?
14. SQS visible và not visible khác nhau thế nào?
15. Vì sao SQS metrics approximate cần thận trọng?
16. Khi nào queue depth giảm nhưng hệ thống vẫn lỗi?
17. Retry ratio tăng nói lên điều gì?
18. Deploy marker giúp điều tra gì?
19. Alert tốt cần những thành phần nào?
20. Từ alert đến root cause nên đi theo thứ tự nào?

---

## 43. Checklist hoàn thành tuần

- [ ] Phân biệt metrics, logs và traces.
- [ ] Đo queue depth.
- [ ] Đo consumer lag.
- [ ] Đo oldest message age.
- [ ] Đo publish, delivery, process và ack rate.
- [ ] Đo redelivery.
- [ ] Đo error, retry và DLQ.
- [ ] Đo processing p50/p95/p99.
- [ ] Dùng structured logging.
- [ ] Propagate correlation ID.
- [ ] Propagate trace context.
- [ ] Xây RabbitMQ dashboard.
- [ ] Xây Kafka dashboard.
- [ ] Xây SQS dashboard.
- [ ] Tạo alert theo SLO.
- [ ] Chạy lab 1, 3 và 10 consumer.
- [ ] Thay đổi prefetch.
- [ ] Kill consumer giữa xử lý.
- [ ] Tạo message xử lý lâu.
- [ ] Điều tra hot partition.
- [ ] Trace một message xuyên service.
- [ ] Triage và replay DLQ có kiểm soát.
- [ ] Viết investigation runbook.

---

## 44. Kết luận

Queue observability cần kết hợp trạng thái broker, hành vi consumer, latency, failure và business outcome.

Dashboard phải trả lời:

- Có bao nhiêu message đang chờ?
- Message cũ nhất bao lâu?
- Consumer có nhận và xử lý không?
- Ack/delete/commit có xảy ra sau side effect không?
- Có bao nhiêu redelivery và retry?
- DLQ tăng vì lý do gì?
- Bottleneck ở broker, consumer, database hay downstream?
- Một message cụ thể đã đi qua service nào?

Baseline:

- Queue depth và oldest age.
- Consumer lag theo group/partition.
- Publish, process, ack/delete/commit rate.
- Redelivery, retry và DLQ.
- Processing p50/p95/p99.
- Structured logs.
- Correlation ID, causation ID và trace propagation.
- Dashboard riêng cho RabbitMQ, Kafka và SQS.
- SLO và alert có runbook.
- Business metrics để tránh kết luận sai từ technical metrics.

Observability tốt không chỉ cho biết hệ thống đang đỏ. Nó giúp chứng minh message không mất, side effect không duplicate, retry không tạo storm và incident có thể khôi phục có kiểm soát.

---

## 45. Tài liệu tham khảo

- OpenTelemetry logs: https://opentelemetry.io/docs/specs/otel/logs/
- RabbitMQ monitoring: https://www.rabbitmq.com/docs/monitoring
- RabbitMQ Prometheus metrics: https://www.rabbitmq.com/docs/4.2/prometheus
- Amazon SQS metrics: https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-available-cloudwatch-metrics.html
- Amazon SQS CloudWatch monitoring: https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/monitoring-using-cloudwatch.html
- Recommended SQS alarms: https://docs.aws.amazon.com/AmazonCloudWatch/latest/monitoring/Best_Practice_Recommended_Alarms_AWS_Services.html


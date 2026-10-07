# Tuần 5: RabbitMQ reliability

## 1. Mục tiêu bài học

Sau bài học này, bạn có thể:

- Giải thích consumer acknowledgement và publisher confirms.
- Phân biệt basic.ack, basic.nack và basic.reject.
- Chọn requeue hay reject dựa trên loại lỗi.
- Cấu hình publisher confirms trong Spring AMQP.
- Hiểu mandatory publish và unroutable message.
- Phân biệt durable exchange, durable queue và persistent delivery.
- Thiết kế Dead Letter Exchange và Dead Letter Queue.
- Dùng message TTL và queue TTL đúng mục đích.
- Xây retry flow bằng delayed queue.
- Lưu retry count trong header hoặc metadata.
- Phân biệt lỗi tạm thời, lỗi vĩnh viễn và lỗi không xác định.
- Tránh requeue vô hạn và retry storm.
- Hiểu quorum queue ở mức kiến trúc.
- Viết integration test cho success, retry, DLQ và publish failure.

Nguyên tắc trung tâm:

    at-least-once delivery
        + idempotent processing
        + bounded retry
        + observable failure

Không nên hứa hẹn exactly-once effect chỉ vì dùng RabbitMQ acknowledgement hoặc publisher confirm.

---

## 2. Reliability có hai chiều

RabbitMQ có hai hướng cần bảo vệ:

    Publisher -> RabbitMQ
    RabbitMQ  -> Consumer

Publisher confirms bảo vệ chiều publisher tới broker.

Consumer acknowledgements bảo vệ chiều broker tới consumer.

Hai cơ chế độc lập:

    publisher confirm != consumer ack

Publisher confirm trả lời:

    Broker đã nhận và chịu trách nhiệm với publish chưa?

Consumer ack trả lời:

    Consumer đã xử lý delivery và broker có thể bỏ message chưa?

RabbitMQ documentation nhấn mạnh hai cơ chế này không biết về nhau. Publisher confirm không biết consumer đã xử lý. Consumer ack không biết publisher ban đầu là ai.

---

## 3. Failure model của RabbitMQ

### 3.1. Producer crash trước khi gửi

    application tạo event
        -> process crash
        -> chưa publish

Message chưa bao giờ vào broker. Nếu order database đã commit, đây là dual-write gap. Outbox là cách xử lý lâu dài.

### 3.2. Network timeout khi publish

    producer publish
        -> network timeout
        -> producer không biết broker đã nhận hay chưa

Nếu retry mù quáng, message có thể duplicate. Nếu không retry, message có thể mất. Publisher confirms giúp biết broker đã confirm chưa, nhưng timeout vẫn có thể tạo unknown outcome nếu confirm bị mất trên đường về.

Producer cần:

- eventId ổn định.
- retry có giới hạn.
- idempotent downstream.
- outbox hoặc trạng thái publish.
- metrics cho confirm timeout.

### 3.3. Broker nhận nhưng producer chưa nhận confirm

    broker nhận message
        -> broker gửi confirm
        -> network đứt
        -> producer không nhận được confirm

Producer có thể retry cùng eventId. Consumer phải chịu được duplicate.

### 3.4. Consumer crash trước side effect

    message delivered
        -> consumer crash
        -> chưa ack
        -> broker redeliver

Đây là trường hợp an toàn hơn nếu consumer dùng manual hoặc auto ack sau processing.

### 3.5. Consumer crash sau side effect trước ack

    message delivered
        -> side effect thành công
        -> process crash
        -> chưa ack
        -> redelivery
        -> side effect chạy lại

Đây là lý do cần idempotency.

### 3.6. Poison message

Message luôn fail vì:

- JSON sai schema.
- Field bắt buộc bị thiếu.
- Business data không hợp lệ.
- Version không được hỗ trợ.
- Consumer bug.
- Downstream permanent rejection.

Nếu cứ requeue ngay, message quay lại đầu queue liên tục và chiếm consumer.

### 3.7. Broker hoặc node failure

Durable queue và persistent message giúp giảm rủi ro restart, nhưng cần publisher confirm để biết broker đã chịu trách nhiệm. Khi yêu cầu cao hơn, quorum queue cung cấp replication semantics khác classic queue.

---

## 4. Consumer acknowledgement

### 4.1. basic.ack

basic.ack là positive acknowledgement.

Ý nghĩa:

    consumer xử lý thành công
        -> gửi ack
        -> broker có thể loại message khỏi queue

Ack phải được gửi trên cùng channel đã nhận delivery. Delivery tag có scope theo channel.

### 4.2. basic.reject

basic.reject là negative acknowledgement cho một delivery.

Nó có thể:

- reject với requeue false.
- reject với requeue true.

Reject chỉ xử lý một message mỗi lần.

### 4.3. basic.nack

basic.nack là RabbitMQ extension cho negative acknowledgement.

Nó hỗ trợ:

- Xử lý một hoặc nhiều delivery.
- requeue true hoặc false.
- Reject theo batch khi cần.

Không dùng multiple hoặc batch ack/nack nếu chưa hiểu rõ delivery order và side effect.

### 4.4. Requeue

Nếu requeue true:

    consumer không xử lý được
        -> message quay lại queue
        -> có thể được deliver lại

Requeue phù hợp khi lỗi thật sự tạm thời và có khả năng xử lý ngay sau đó. Requeue không phải retry policy hoàn chỉnh.

### 4.5. Reject không requeue

Nếu requeue false:

    message bị reject
        -> nếu có DLX, message có thể dead-letter
        -> nếu không có DLX, message bị loại theo policy broker

Dùng cho:

- Schema không hợp lệ.
- Business validation fail vĩnh viễn.
- Không hỗ trợ event version.
- Retry budget đã hết.

---

## 5. Ack timing

Quy tắc:

    process business side effect
        -> commit side effect
        -> ack

Không:

    receive message
        -> ack
        -> process side effect

Nếu ack trước side effect rồi process crash, broker không redeliver và message có thể mất.

### 5.1. Auto ack

Auto ack coi message đã được xử lý ngay khi broker deliver. Throughput cao nhưng safety thấp.

Dùng cho:

- Metrics không quan trọng.
- Log có thể mất.
- Event không có side effect quan trọng.
- Lab.

Không dùng mặc định cho payment, inventory hoặc order state.

### 5.2. Container AUTO

Spring container ack khi listener return thành công. Nếu listener throw exception, error handler quyết định reject, requeue hoặc recovery.

### 5.3. Manual ack

Manual ack cho application kiểm soát:

    try:
        process
        ack
    catch transient:
        nack requeue hoặc chuyển retry
    catch permanent:
        reject không requeue

Manual ack không tự tạo idempotency. Duplicate vẫn xảy ra nếu crash sau side effect trước ack.

---

## 6. Spring AMQP manual ack

Factory:

    @Bean
    public SimpleRabbitListenerContainerFactory
            manualRabbitListenerContainerFactory(
                ConnectionFactory connectionFactory,
                Jackson2JsonMessageConverter converter) {

        SimpleRabbitListenerContainerFactory factory =
            new SimpleRabbitListenerContainerFactory();

        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(converter);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setPrefetchCount(1);
        return factory;
    }

Listener:

    @RabbitListener(
        queues = RabbitTopology.NOTIFICATION_QUEUE,
        containerFactory = "manualRabbitListenerContainerFactory"
    )
    public void handle(
            OrderEventEnvelope<OrderCreatedPayload> event,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long tag)
            throws IOException {

        notificationService.send(event.payload());

        channel.basicAck(tag, false);
    }

Nếu notificationService throw exception trước basicAck, message chưa được ack. Error handling phải quyết định requeue hoặc reject.

Không bắt exception rồi nuốt:

    try {
        notificationService.send(event.payload());
    } catch (Exception ignored) {
        // sai: message có thể bị ack hoặc flow không rõ
    }

Nếu catch, phải phân loại và có hành động rõ ràng.

---

## 7. Requeue và retry

### 7.1. Requeue ngay lập tức

    main queue
        -> fail
        -> requeue
        -> nhận lại ngay
        -> fail
        -> requeue

Đây là hot loop. Nó có thể:

- Ăn CPU.
- Tăng log.
- Chặn message khác.
- Làm downstream quá tải.
- Không tạo delay thực sự.
- Không giới hạn số lần thử.

### 7.2. Khi nào requeue trực tiếp có thể chấp nhận?

Chỉ khi:

- Lỗi rất ngắn và hiếm.
- Có retry limit.
- Có backoff bên ngoài hoặc consumer pause.
- Không làm queue nóng.
- Có metric redelivery.

Trong phần lớn business flow, delayed retry queue dễ kiểm soát hơn.

### 7.3. Retryable error

Ví dụ:

- HTTP 503 từ downstream.
- Connection timeout.
- Database deadlock.
- Rate limit tạm thời.
- Broker/network lỗi tạm thời.

Retryable không có nghĩa retry vô hạn. Mỗi message cần retry budget.

### 7.4. Permanent error

Ví dụ:

- Schema invalid.
- Unknown event version.
- SKU không tồn tại nếu không thể bổ sung.
- Payment card bị từ chối vĩnh viễn.
- Business rule bị vi phạm.
- Authorization không hợp lệ.

Permanent error nên đi tới DLQ/DLT hoặc business result riêng, không requeue vô hạn.

### 7.5. Unknown error

Khi chưa biết lỗi tạm thời hay vĩnh viễn:

- Giới hạn số lần retry.
- Ghi stack trace và metadata.
- Đưa vào DLQ sau budget.
- Có runbook phân tích.
- Không để message quay vòng vô hạn.

---

## 8. Publisher confirms

### 8.1. Confirm giải quyết gì?

Nếu producer chỉ ghi frame vào socket, producer chưa biết broker đã nhận và xử lý message.

Publisher confirm cho phép broker phản hồi:

- Ack: broker đã chấp nhận/đảm bảo theo storage semantics.
- Nack: broker không thể xử lý message.

Confirm bảo vệ publisher-to-broker, không bảo vệ consumer processing.

### 8.2. Spring Boot configuration

Ví dụ:

    spring:
      rabbitmq:
        publisher-confirm-type: correlated
        publisher-returns: true
        template:
          mandatory: true

Tên property phụ thuộc Spring Boot version. Kiểm tra configuration metadata của project.

### 8.3. Correlated confirm callback

    @Configuration
    public class RabbitPublisherConfig {

        @Bean
        public RabbitTemplate rabbitTemplate(
                ConnectionFactory connectionFactory,
                Jackson2JsonMessageConverter converter) {

            RabbitTemplate template = new RabbitTemplate(
                connectionFactory
            );

            template.setMessageConverter(converter);
            template.setMandatory(true);

            template.setConfirmCallback(
                (correlation, ack, cause) -> {
                    if (!ack) {
                        log.error(
                            "Rabbit publish nack, id={}, cause={}",
                            correlation != null
                                ? correlation.getId()
                                : null,
                            cause
                        );
                    }
                }
            );

            template.setReturnsCallback(returned -> {
                log.error(
                    "Rabbit message returned, exchange={}, "
                    + "routingKey={}, replyCode={}, replyText={}",
                    returned.getExchange(),
                    returned.getRoutingKey(),
                    returned.getReplyCode(),
                    returned.getReplyText()
                );
            });

            return template;
        }
    }

### 8.4. CorrelationData

    CorrelationData correlation =
        new CorrelationData(event.eventId());

    rabbitTemplate.convertAndSend(
        RabbitTopology.EXCHANGE,
        RabbitTopology.ORDER_CREATED,
        event,
        message -> {
            message.getMessageProperties()
                .setMessageId(event.eventId());
            return message;
        },
        correlation
    );

Application có thể chờ confirm:

    CorrelationData.Confirm confirm =
        correlation.getFuture()
            .get(5, TimeUnit.SECONDS);

Không block request thread lâu trong production nếu traffic lớn. Có thể dùng asynchronous handling, outbox publisher và trạng thái publish riêng.

### 8.5. Confirm ack không phải consumer ack

Confirm ack chỉ nói:

    RabbitMQ đã nhận trách nhiệm với publish

Nó không nói:

- Notification đã gửi.
- Audit đã ghi.
- Payment đã xử lý.
- Consumer đã ack.
- Business transaction đã commit.

---

## 9. Mandatory publish và returned message

### 9.1. Unroutable message

Producer publish vào exchange với routing key không match binding:

    exchange: order.events
    routing key: order.unknown
    binding: order.created

Message không có queue đích.

Nếu không kiểm tra, producer có thể tưởng publish thành công chỉ vì không có exception synchronous.

### 9.2. Mandatory

mandatory publish yêu cầu broker trả message không route được về publisher.

Trong Spring:

    template.setMandatory(true);

Kết hợp returns callback để log hoặc xử lý.

### 9.3. Confirm và return khác nhau

- Confirm: broker đã xử lý publish ở mức broker.
- Return: message không route được tới queue khi mandatory.
- Có thể nhận return trước confirm ack.
- Cần xử lý cả hai.

### 9.4. Alternate exchange

Alternate exchange có thể nhận unroutable message. Đây là một lựa chọn topology để không bỏ im lặng message không match.

Tuy nhiên alternate exchange không thay thế:

- Schema validation.
- Business DLQ.
- Publisher confirm.
- Monitoring.
- Idempotency.

---

## 10. Durable exchange và queue

### 10.1. Durable exchange

Durable exchange tồn tại sau broker restart.

### 10.2. Durable queue

Durable queue tồn tại sau broker restart.

### 10.3. Durable binding

Binding giữa durable exchange và durable queue được khôi phục cùng topology.

### 10.4. Persistent delivery

Publisher đặt delivery mode persistent:

    template.convertAndSend(
        exchange,
        routingKey,
        event,
        message -> {
            message.getMessageProperties()
                .setDeliveryMode(
                    MessageDeliveryMode.PERSISTENT
                );
            return message;
        }
    );

Với JSON converter, cần kiểm tra message properties thực tế trong test hoặc Management UI.

### 10.5. Bốn lớp không nên nhầm

    durable exchange
        != durable queue
        != persistent message
        != publisher confirm

Một thiết kế muốn giảm message loss thường cần phối hợp:

    durable exchange
    + durable queue
    + persistent message
    + publisher confirm
    + consumer ack
    + idempotent consumer

Đây vẫn không phải exactly-once effect.

---

## 11. Dead Letter Exchange

Message có thể dead-letter khi:

- Consumer reject với requeue false.
- Consumer nack với requeue false.
- Message hết TTL.
- Queue vượt giới hạn tùy policy.
- Một số điều kiện broker khác.

Cấu hình queue với DLX:

    @Bean
    public DirectExchange deadLetterExchange() {
        return ExchangeBuilder
            .directExchange("order.dlx")
            .durable(true)
            .build();
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder
            .durable("order.dlq")
            .build();
    }

    @Bean
    public Binding deadLetterBinding(
            Queue deadLetterQueue,
            DirectExchange deadLetterExchange) {

        return BindingBuilder
            .bind(deadLetterQueue)
            .to(deadLetterExchange)
            .with("order.dead");
    }

Main queue:

    @Bean
    public Queue orderMainQueue() {
        return QueueBuilder
            .durable("order.main")
            .deadLetterExchange("order.dlx")
            .deadLetterRoutingKey("order.dead")
            .build();
    }

### 11.1. DLX không phải DLQ

- DLX là exchange nhận message dead-letter.
- DLQ là queue bind vào DLX để giữ message.
- Có thể một DLX route tới nhiều queue.
- DLQ là nơi điều tra, không phải nơi giấu lỗi.

### 11.2. Metadata dead-letter

RabbitMQ có thể thêm metadata như:

- x-death.
- queue nguồn.
- reason.
- thời điểm.
- exchange/routing key trước đó.

Không chỉ nhìn body. Khi điều tra DLQ, cần giữ headers và properties.

### 11.3. DLQ operation

DLQ cần:

- Owner.
- Alert.
- Retention.
- Access control.
- Replay/redrive runbook.
- Dashboard.
- Reason classification.
- Không tự động replay toàn bộ mà không kiểm tra.

---

## 12. Message TTL

TTL là thời gian message hoặc queue được phép tồn tại.

### 12.1. Message TTL trên queue

Spring:

    @Bean
    public Queue retry10sQueue() {
        return QueueBuilder
            .durable("order.retry.10s")
            .withArgument("x-message-ttl", 10_000)
            .deadLetterExchange("order.events")
            .deadLetterRoutingKey("order.created")
            .build();
    }

Message nằm trong retry queue khoảng 10 giây rồi dead-letter về main exchange/routing key.

### 12.2. Per-message expiration

Publisher có thể set expiration:

    message.getMessageProperties()
        .setExpiration("10000");

Per-message TTL hữu ích khi mỗi message có deadline khác nhau. Cần hiểu behavior expiration trong queue và test thực tế.

### 12.3. Queue TTL

Queue TTL hoặc expires là lifecycle của queue, không phải thời gian sống của message.

Đừng nhầm:

- Message TTL: message hết hạn.
- Queue TTL: queue không dùng có thể bị xóa.
- Consumer timeout: thời gian xử lý/delivery khác.
- Business deadline: thời hạn nghiệp vụ.

### 12.4. TTL không phải scheduler chính xác

TTL không đảm bảo message được chuyển đúng từng millisecond. Message thường được xử lý khi đến điều kiện expiration và vị trí phù hợp trong queue.

TTL phù hợp delayed retry đơn giản, không phải hệ thống scheduler chính xác.

---

## 13. Delayed retry topology

Flow roadmap:

    main queue
        ↓ lỗi tạm thời
    retry queue 10s
        ↓
    retry queue 60s
        ↓ lỗi vĩnh viễn
    dead-letter queue

### 13.1. Exchanges và queues

    order.events
        -> order.main

    order.retry.10s
        -> expires sau 10 giây
        -> dead-letter về order.events
        -> routing key order.created

    order.retry.60s
        -> expires sau 60 giây
        -> dead-letter về order.events
        -> routing key order.created

    order.dlx
        -> order.dlq

### 13.2. Retry count

Lưu retry count trong header:

    x-retry-count: 0

Hoặc event metadata:

    retryCount: 0

Khi chuyển retry:

    x-retry-count = x-retry-count + 1

Một message nên có:

- eventId ổn định.
- originalExchange.
- originalRoutingKey.
- retryCount.
- lastErrorType.
- lastFailedAt.
- correlationId.

### 13.3. Vì sao không sửa eventId?

Retry cùng một logical event nên giữ eventId. Nếu tạo eventId mới mỗi lần retry, idempotency và trace khó hơn.

Có thể tạo delivery attempt riêng trong metadata, nhưng event identity nên ổn định.

### 13.4. Routing retry

Pseudo flow:

    main consumer
        -> success: ack
        -> transient and count < 3:
             publish to retry queue
             ack original
        -> permanent or count >= 3:
             reject to DLQ
             hoặc publish DLQ rồi ack original

Nếu application publish sang retry queue rồi ack message gốc, publish retry phải được bảo vệ bằng confirm. Nếu không, có thể mất message giữa publish retry và ack original.

---

## 14. Spring Retry và broker retry

Có hai hướng:

### 14.1. In-process retry

Listener retry trong cùng consumer process:

    receive
        -> attempt 1
        -> wait backoff
        -> attempt 2
        -> recover

Ưu điểm:

- Dễ cấu hình.
- Không cần nhiều queue.
- Phù hợp lỗi ngắn.

Nhược điểm:

- Giữ consumer thread.
- Message vẫn unacked lâu.
- Process restart làm mất retry state trong memory.
- Có thể block queue.

### 14.2. Broker delayed retry

Message được chuyển sang retry queue có TTL rồi quay lại main queue.

Ưu điểm:

- Consumer thread được giải phóng.
- Backoff tồn tại ngoài process.
- Dễ quan sát từng tầng retry.

Nhược điểm:

- Nhiều topology.
- Cần quản lý retry count.
- Cần tránh loop.
- TTL không phải scheduler chính xác.
- Republish/ack phải atomic ở mức application policy.

### 14.3. Khi nào dùng hướng nào?

- Lỗi network rất ngắn: in-process retry có thể đủ.
- Downstream outage vài giây/phút: delayed queue tốt hơn.
- Lỗi schema: DLQ ngay.
- Retry cần lịch phức tạp: cân nhắc scheduler hoặc delayed-message plugin, nhưng phải đánh giá vận hành.

---

## 15. Spring Retry interceptor ở mức khái niệm

Một cấu hình retry interceptor có thể:

    @Bean
    public StatefulRetryOperationsInterceptor
            rabbitRetryInterceptor() {

        return RetryInterceptorBuilder.stateful()
            .maxRetries(3)
            .backOffOptions(
                1000,
                2.0,
                10000
            )
            .recoverer(
                new RejectAndDontRequeueRecoverer()
            )
            .build();
    }

Có thể gắn vào advice chain của listener container.

Cần chú ý:

- Stateful retry cần key duy nhất cho message.
- Phân loại exception phải xét cause.
- Recoverer quyết định message cuối cùng đi đâu.
- Retry interceptor không thay thế idempotency.
- Retry trong process không giống retry queue.

API và package có thể khác theo Spring AMQP version. Dùng tài liệu version tương ứng.

---

## 16. Retry policy cho OrderCreated

Ví dụ:

| Lỗi | Phân loại | Hành động |
|---|---|---|
| JSON malformed | Permanent | DLQ ngay |
| Unknown event version | Permanent | DLQ ngay |
| Notification HTTP 503 | Temporary | Retry 10s, 60s |
| Notification timeout | Temporary | Retry giới hạn |
| Email address invalid | Permanent/business | Order failure hoặc DLQ |
| Database deadlock | Temporary | Retry ngắn |
| Bug NullPointerException | Unknown | Retry ít lần rồi DLQ |
| Duplicate eventId | Expected | Ack sau khi bỏ qua |

Đừng đẩy mọi business rejection vào technical DLQ. Ví dụ payment bị từ chối có thể là business result PaymentFailed, không phải poison message.

---

## 17. Quorum queue ở mức khái niệm

Quorum queue là replicated queue dựa trên Raft-like consensus model của RabbitMQ.

Ý tưởng:

    leader
      <-> follower
      <-> follower

Message được replication theo quorum thay vì chỉ tồn tại trên một node.

Ưu điểm:

- Tăng khả năng chịu node failure.
- Semantics durability mạnh hơn classic queue trong nhiều trường hợp.
- Phù hợp queue cần data safety cao.

Trade-off:

- Tốn tài nguyên replication.
- Throughput và latency khác classic queue.
- Cần quorum node khỏe.
- Không phải queue nào cũng cần quorum.
- Vận hành cluster phức tạp hơn.

Publisher confirm với quorum queue phản ánh việc quorum replicas chấp nhận message theo semantics của broker.

Ở tuần này chỉ cần hiểu lựa chọn. Benchmark và cluster behavior sẽ mở rộng ở Tuần 6.

---

## 18. Publisher reliability flow

Một publisher production-like:

    create event with stable eventId
        -> serialize JSON
        -> publish persistent message
        -> use publisher confirm
        -> detect nack/timeout
        -> retry with bounded policy
        -> record publish status
        -> consumer handles duplicates idempotently

Nếu publish trong HTTP request:

- Không block quá lâu chờ confirm.
- Trả response phản ánh đúng trạng thái.
- Nếu chưa chắc publish, không nói event đã delivered.
- Cân nhắc trả accepted và xử lý outbox async.

Nếu publish từ outbox:

    database transaction
        -> order row
        -> outbox row

    publisher worker
        -> read outbox
        -> publish with confirms
        -> mark sent

---

## 19. Consumer reliability flow

Consumer production-like:

    receive message
        -> validate envelope
        -> classify error
        -> check idempotency
        -> process side effect
        -> ack on success

Temporary failure:

    receive
        -> classify transient
        -> bounded retry/backoff
        -> no infinite requeue

Permanent failure:

    receive
        -> classify permanent
        -> reject without requeue
        -> DLX/DLQ
        -> alert and runbook

Crash after side effect:

    redelivery
        -> idempotency key detects duplicate
        -> skip duplicate side effect
        -> ack

---

## 20. Integration test reliability

### Test 1: Ack sau processing

- Publish một message.
- Consumer throw trước side effect.
- Xác nhận message chưa bị mất theo policy.
- Consumer xử lý thành công.
- Xác nhận message được ack.

### Test 2: Duplicate delivery

- Gửi cùng eventId hai lần.
- Consumer chạy side effect một lần.
- Lần thứ hai chỉ ghi duplicate/skip.
- Cả hai delivery kết thúc an toàn.

### Test 3: Unroutable publish

- Publish routing key không có binding.
- Bật mandatory.
- Assert returned callback được gọi.
- Assert metric/log có exchange và routing key.

### Test 4: Confirm timeout/nack

- Mô phỏng connection/broker failure.
- Assert publisher không báo thành công giả.
- Assert retry/outbox state còn để xử lý.

### Test 5: Retry 10s và 60s

- Consumer throw transient.
- Assert message tới retry queue.
- Sau TTL, message quay lại main queue.
- Tăng retry count.
- Sau budget, message tới DLQ.

Test TTL nên dùng timeout hợp lý, không phụ thuộc sleep quá dài.

### Test 6: Permanent error

- Gửi payload invalid.
- Assert không requeue vô hạn.
- Assert DLQ có message và metadata reason.

### Test 7: Consumer crash

- Gửi message.
- Kill consumer trước ack.
- Khởi động consumer mới.
- Assert message redeliver.
- Assert side effect idempotent.

---

## 21. Observability cho reliability

Publisher metrics:

- publish attempts.
- confirm ack count.
- confirm nack count.
- confirm latency.
- confirm timeout.
- returned message count.
- publish retry count.
- outbox pending count.

Consumer metrics:

- delivery count.
- ack count.
- nack count.
- reject count.
- redelivery count.
- processing latency.
- transient failure count.
- permanent failure count.
- retry count.
- DLQ count.

Queue metrics:

- ready messages.
- unacked messages.
- consumer count.
- oldest message age.
- retry queue depth.
- DLQ depth.

Alert gợi ý:

- Confirm timeout tăng.
- Returned message khác zero.
- DLQ depth tăng.
- Redelivery tăng đột biến.
- Retry queue age vượt SLA.
- Unacked messages tăng liên tục.
- Queue không có active consumer.

---

## 22. Security và operations

- [ ] DLQ có quyền đọc hạn chế.
- [ ] Replay DLQ cần approval hoặc runbook.
- [ ] Không cho mọi service publish vào mọi exchange.
- [ ] Không cho mọi service consume mọi queue.
- [ ] Headers không chứa secret.
- [ ] Payload DLQ được bảo vệ như dữ liệu business.
- [ ] Có retention cho retry queue và DLQ.
- [ ] Có giới hạn message size.
- [ ] Có correlationId để audit.
- [ ] Credentials nằm ở secret manager.
- [ ] Management UI giới hạn network.
- [ ] Topology changes có review.
- [ ] Không purge queue production tùy tiện.

---

## 23. Bài thực hành

### Bài 1: Manual ack

Dùng notification consumer:

1. Cấu hình MANUAL.
2. Process thành công rồi ack.
3. Throw exception trước ack.
4. Quan sát unacked/redelivery.
5. Ghi lại delivery tag và channel.

### Bài 2: Nack với requeue

1. Gửi transient failure.
2. Nack requeue true.
3. Đo tốc độ redelivery.
4. Thêm giới hạn retry.
5. Giải thích vì sao không dùng vô hạn.

### Bài 3: Publisher confirm

1. Bật correlated confirm.
2. Publish persistent event.
3. Log eventId và confirm result.
4. Mô phỏng broker disconnect.
5. Quan sát confirm timeout hoặc nack.
6. Thiết kế retry bounded.

### Bài 4: Mandatory publish

1. Publish routing key không bind.
2. Bật mandatory.
3. Assert returned callback.
4. Sửa binding.
5. Publish lại.
6. So sánh confirm và return.

### Bài 5: Retry queues

Tạo:

    order.main
    order.retry.10s
    order.retry.60s
    order.dlq

Thiết kế TTL/DLX để flow:

    main -> retry 10s -> main
         -> retry 60s -> main
         -> dlq

Ghi retry count trong header.

### Bài 6: Poison message

1. Gửi payload malformed.
2. Đảm bảo không requeue vô hạn.
3. Kiểm tra DLQ.
4. Lưu reason và original routing key.
5. Viết runbook xử lý.

### Bài 7: Crash window

1. Consumer ghi side effect.
2. Dừng process trước ack.
3. Khởi động lại.
4. Quan sát duplicate.
5. Thêm processed_message.
6. Chứng minh side effect chỉ xảy ra một lần.

---

## 24. Common mistakes

### Mistake 1: Nhầm confirm với ack

Confirm là publisher-to-broker. Ack là broker-to-consumer.

### Mistake 2: Ack trước side effect

Tạo message loss khi consumer crash.

### Mistake 3: Requeue mọi lỗi

Tạo hot loop và retry storm.

### Mistake 4: Không có retry budget

Message có thể retry vô hạn, chiếm tài nguyên mãi.

### Mistake 5: Retry publish rồi ack original không confirm

Nếu retry publish fail sau đó ack original, message bị mất.

### Mistake 6: Dùng eventId mới cho mỗi retry

Idempotency và trace bị phá. Giữ eventId logical ổn định.

### Mistake 7: Chỉ tạo DLQ nhưng không có operation

DLQ không tự giải quyết lỗi. Cần owner, alert, retention và replay runbook.

### Mistake 8: Đặt TTL quá ngắn

Message có thể hết hạn trước khi downstream có cơ hội xử lý.

### Mistake 9: Nghĩ durable là guaranteed delivery

Cần persistent message, confirm và consumer ack.

### Mistake 10: Không phân biệt business failure

Payment declined có thể là PaymentFailed event, không nhất thiết là technical DLQ.

### Mistake 11: Bỏ qua quorum trade-off

Quorum queue tăng durability nhưng có chi phí tài nguyên và latency.

### Mistake 12: Retry exception bug quá nhiều lần

Bug code không tự biến thành transient chỉ vì retry nhiều.

---

## 25. Definition of Done

- [ ] Giải thích được hai chiều reliability.
- [ ] Phân biệt basic.ack, basic.nack và basic.reject.
- [ ] Biết requeue true/false.
- [ ] Ack sau side effect.
- [ ] Bật publisher confirms.
- [ ] Xử lý confirm ack/nack/timeout.
- [ ] Bật mandatory publish.
- [ ] Xử lý returned message.
- [ ] Dùng durable exchange.
- [ ] Dùng durable queue.
- [ ] Dùng persistent delivery.
- [ ] Thiết kế DLX.
- [ ] Tạo DLQ.
- [ ] Hiểu message TTL.
- [ ] Phân biệt message TTL và queue TTL.
- [ ] Tạo retry queue 10 giây.
- [ ] Tạo retry queue 60 giây.
- [ ] Lưu retry count.
- [ ] Chặn requeue vô hạn.
- [ ] Phân biệt transient/permanent/unknown error.
- [ ] Consumer idempotent theo eventId.
- [ ] Có test duplicate delivery.
- [ ] Có test unroutable publish.
- [ ] Có test permanent failure vào DLQ.
- [ ] Hiểu quorum queue ở mức khái niệm.
- [ ] Có metric redelivery, confirm và DLQ depth.
- [ ] Có runbook DLQ cơ bản.

---

## 26. Câu hỏi tự kiểm tra

1. Consumer ack bảo vệ chiều nào?
2. Publisher confirm bảo vệ chiều nào?
3. Vì sao hai cơ chế độc lập?
4. basic.ack khác basic.nack thế nào?
5. basic.reject có hạn chế gì so với basic.nack?
6. Khi nào requeue true hợp lý?
7. Vì sao requeue vô hạn nguy hiểm?
8. Confirm ack có nghĩa consumer đã xử lý chưa?
9. Mandatory publish dùng để phát hiện gì?
10. Confirm và returned message khác nhau thế nào?
11. Durable queue có đủ đảm bảo message không mất không?
12. Persistent delivery có ý nghĩa gì?
13. DLX khác DLQ thế nào?
14. Message bị dead-letter trong những trường hợp nào?
15. Message TTL khác queue TTL thế nào?
16. Vì sao TTL không phải scheduler chính xác?
17. Retry queue 10 giây hoạt động ra sao?
18. Vì sao cần retry count?
19. Vì sao giữ eventId khi retry?
20. Khi nào lỗi nên vào DLQ ngay?
21. Tại sao retry publish phải confirm trước khi ack message gốc?
22. Consumer crash sau side effect trước ack gây gì?
23. Idempotency key nên dùng eventId hay delivery tag?
24. Quorum queue giải quyết vấn đề gì?
25. DLQ cần những operational control nào?

---

## 27. Chuẩn bị cho Tuần 6

Tuần 6 tập trung scale và vận hành RabbitMQ:

- Competing consumers.
- Prefetch tuning.
- Consumer concurrency.
- Fair dispatch.
- Ordering limitation.
- Queue depth.
- Unacked messages.
- Connection/channel limit.
- Quorum queue.
- Cluster và node failure.
- Benchmark throughput, latency và redelivery.

Các metric cần mang sang Tuần 6:

- Message size.
- Producer rate.
- Consumer count.
- Prefetch.
- Throughput.
- p95 latency.
- Redelivery.
- Queue depth.
- Ready messages.
- Unacked messages.
- Confirm latency.
- DLQ rate.

---

## 28. Tóm tắt

- Consumer ack và publisher confirm bảo vệ hai chiều khác nhau.
- Ack sau khi business side effect hoàn tất.
- basic.ack là thành công; nack/reject là failure signal.
- Requeue là công cụ, không phải retry policy.
- Không requeue vô hạn.
- Publisher confirm giúp biết broker đã nhận trách nhiệm với publish.
- Mandatory publish phát hiện message không route được.
- Durable exchange, durable queue và persistent message là các thuộc tính khác nhau.
- DLX route dead-letter; DLQ giữ message để điều tra.
- TTL có thể tạo delayed retry đơn giản.
- Retry phải có backoff, budget và retry count.
- Giữ eventId ổn định qua các lần retry.
- Nếu retry publish rồi ack original, phải bảo vệ publish retry bằng confirm.
- Consumer vẫn cần idempotency vì crash window có thể tạo duplicate.
- Quorum queue tăng replication/durability nhưng có trade-off.
- Reliability cần metrics, alert, owner và runbook, không chỉ vài dòng config.

---

## Tài liệu tham khảo

- RabbitMQ acknowledgements và publisher confirms: https://www.rabbitmq.com/docs/confirms
- RabbitMQ reliability guide: https://www.rabbitmq.com/docs/reliability
- RabbitMQ dead letter exchanges: https://www.rabbitmq.com/docs/dlx
- RabbitMQ TTL và expiration: https://www.rabbitmq.com/docs/ttl
- RabbitMQ quorum queues: https://www.rabbitmq.com/docs/quorum-queues
- Spring AMQP resilience và broker failures: https://docs.spring.io/spring-amqp/reference/amqp/resilience-recovering-from-errors-and-broker-failures.html
- Spring AMQP MessageRecoverer: https://docs.spring.io/spring-amqp/docs/current/api/org/springframework/amqp/rabbit/retry/MessageRecoverer.html
- Spring AMQP reference: https://docs.spring.io/spring-amqp/reference/


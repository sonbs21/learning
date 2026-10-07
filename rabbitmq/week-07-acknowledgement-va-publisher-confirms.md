# Tuần 7 — Acknowledgement và publisher confirms

> Reliability của RabbitMQ có hai phía: producer phải biết broker đã nhận publish chưa; consumer chỉ xác nhận sau khi xử lý xong. Publisher confirm không thay consumer ack, và consumer ack không thay publisher confirm.

## 1. Kết quả cần đạt

Sau bài học, bạn có thể:

- Giải thích basic.ack, basic.nack và basic.reject.
- Chọn requeue hoặc không requeue theo loại lỗi.
- Nhận biết requeue loop và poison message.
- Hiểu redelivered flag không phải retry counter chính xác.
- Bật publisher confirms và phân biệt confirm với return.
- Dùng confirm từng message, batch và asynchronous.
- Dùng mandatory publish để phát hiện message không route được.
- Phân biệt durable entity, persistent message và publisher confirm.
- Giải thích giới hạn của connection recovery.
- Vẽ lifecycle từ publish đến consumer side effect và ack.
- Viết metric cho confirm latency, unconfirmed count, returns, redelivery và ack latency.

## 2. Reliability lifecycle end-to-end

~~~text
Producer
  1. tạo message
  2. publish vào exchange
  3. nhận publisher confirm
  4. exchange route vào queue
  5. consumer nhận delivery
  6. business side effect hoàn tất
  7. consumer gửi ack
  8. broker xóa message khỏi queue
~~~

Các mốc này độc lập:

~~~text
publisher confirm != consumer ack
consumer ack      != database commit ở mọi hệ thống
consumer ack      != email/payment đã rollback được
return callback   != publisher confirm
~~~

Producer confirm trả lời: broker đã nhận publish trên channel chưa?

Return callback trả lời: message có bị trả lại vì không route được không?

Consumer ack trả lời: consumer đã xử lý delivery và muốn broker bỏ nó chưa?

Không có một callback đơn lẻ chứng minh toàn bộ business operation thành công.

## 3. Consumer acknowledgement

### 3.1 basic.ack

~~~java
channel.basicAck(deliveryTag, false);
~~~

Với multiple=false, chỉ ack deliveryTag đó. Delivery tag phải thuộc channel hiện tại.

Thứ tự an toàn:

~~~text
nhận message
  -> validate
  -> ghi database/side effect
  -> ack
~~~

### 3.2 basic.reject

~~~java
channel.basicReject(deliveryTag, requeue);
~~~

requeue=true đưa message lại queue. requeue=false làm message rời queue hiện tại; nó có thể bị drop hoặc đi theo dead-letter policy.

Reject chỉ xử lý một delivery.

### 3.3 basic.nack

~~~java
channel.basicNack(
        deliveryTag,
        multiple,
        requeue
);
~~~

Ví dụ:

~~~java
channel.basicNack(deliveryTag, false, true);
~~~

multiple=true phủ định các delivery chưa ack trước đó trên cùng channel, nên chỉ dùng khi chắc chắn về thứ tự hoàn tất.

## 4. Ack trước hay sau side effect?

Ack trước:

~~~text
receive
  -> ack
  -> write database
  -> process crash
~~~

Message có thể mất khi database chưa được cập nhật.

Ack sau:

~~~text
receive
  -> write database
  -> ack
~~~

Nếu crash sau database write nhưng trước ack, message có thể redeliver và database operation chạy lại. Vì vậy cần idempotency.

Mặc định thực dụng:

~~~text
ack sau processing + idempotent business operation
~~~

Ack không biến broker và database thành một distributed transaction.

## 5. Requeue và lỗi vô hạn

### Lỗi tạm thời

Requeue có thể hợp lý khi database, HTTP downstream hoặc dependency tạm thời unavailable. Nhưng requeue ngay lập tức vẫn có thể tạo hot loop. Production cần retry hữu hạn, backoff và thường là retry queue/DLQ.

### Lỗi vĩnh viễn

Không requeue vô hạn:

- JSON sai schema;
- thiếu field bắt buộc;
- schema version không hỗ trợ;
- business rule không thể thỏa mãn;
- poison message luôn làm worker crash.

Loop:

~~~text
consume -> fail -> requeue
  -> consume -> fail -> requeue
  -> consumer bận với cùng message
~~~

Hậu quả là CPU/log tăng, queue không tiến, message khác bị đói và downstream bị gọi lặp.

### Retry counter

redelivered chỉ là tín hiệu broker delivery lại message. Nó không phải retry counter chính xác.

Không suy ra:

~~~text
redelivered=true => retry đúng N lần
~~~

Muốn giới hạn retry, cần application header hoặc retry topology có thể đếm và trace rõ. TTL/DLX/DLQ sẽ học ở tuần sau.

## 6. Redelivered flag

~~~java
boolean redelivered =
        delivery.getEnvelope().isRedeliver();
~~~

Flag có thể true sau consumer crash, channel reset, nack requeue hoặc connection failure.

Flag không cho biết:

- side effect trước đó có thành công chưa;
- đây là lần retry thứ mấy;
- message có phải poison không;
- duplicate có xảy ra ở hệ thống ngoài broker không.

Consumer vẫn phải idempotent dù flag true hay false.

## 7. Manual ack với Spring AMQP

~~~java
@Bean
public SimpleRabbitListenerContainerFactory manualAckFactory(
        ConnectionFactory connectionFactory,
        MessageConverter converter
) {
    SimpleRabbitListenerContainerFactory factory =
            new SimpleRabbitListenerContainerFactory();
    factory.setConnectionFactory(connectionFactory);
    factory.setMessageConverter(converter);
    factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
    factory.setPrefetchCount(1);
    return factory;
}
~~~

~~~java
@RabbitListener(
        queues = "orders.process",
        containerFactory = "manualAckFactory"
)
public void handle(
        OrderMessage payload,
        Message message,
        Channel channel
) throws IOException {
    long tag = message.getMessageProperties()
            .getDeliveryTag();

    try {
        orderService.process(payload);
        channel.basicAck(tag, false);
    } catch (TemporaryException exception) {
        channel.basicNack(tag, false, true);
    } catch (PermanentException exception) {
        channel.basicReject(tag, false);
    }
}
~~~

Ack đúng delivery tag và channel của listener. Không requeue vô hạn. Nếu để exception nổi lên, container error handler sẽ tham gia xử lý; cần hiểu rõ config Spring AMQP đang dùng.

## 8. Publisher confirms

Publisher confirm báo cho producer biết broker đã xử lý/accept publish trên channel.

Confirm không chứng minh message đã route:

~~~text
publish vào exchange
  -> broker confirm publish
  -> không có binding
  -> message có thể bị discard nếu không mandatory/return
~~~

Event quan trọng thường cần:

~~~text
publisher confirm + mandatory return
~~~

Confirm cũng không chứng minh consumer đã xử lý.

## 9. Confirm bằng Java client

### 9.1 Từng message

~~~java
channel.confirmSelect();

channel.basicPublish(
        "orders.events",
        "order.created",
        properties,
        body
);

channel.waitForConfirmsOrDie(5_000);
~~~

Một số client version dùng waitForConfirms. Kiểm tra API version của amqp-client.

Cách này dễ map lỗi nhưng chờ sau từng publish nên throughput có thể thấp.

### 9.2 Theo batch

~~~java
channel.confirmSelect();

for (Message message : batch) {
    channel.basicPublish(
            exchange,
            message.routingKey(),
            message.properties(),
            message.body()
    );
}

channel.waitForConfirmsOrDie(5_000);
~~~

Batch tăng throughput nhưng retry phức tạp. Nếu batch outcome không chắc chắn, retry có thể duplicate cả batch. Cần messageId và idempotency.

### 9.3 Asynchronous confirm

Một số Java client hỗ trợ confirm listener:

~~~java
channel.confirmSelect();

channel.addConfirmListener(
        (sequenceNumber, multiple) -> {
            confirmTracker.ack(
                    sequenceNumber,
                    multiple
            );
        },
        (sequenceNumber, multiple) -> {
            confirmTracker.nack(
                    sequenceNumber,
                    multiple
            );
        }
);
~~~

Ý tưởng:

~~~text
sequence number -> pending publish
ack             -> remove pending
nack            -> retry/mark failure
~~~

Tên callback có thể khác theo client version. Pending map phải có giới hạn; không để unconfirmed publish tăng vô hạn.

## 10. So sánh confirm modes

| Cách | Ưu điểm | Trade-off |
|---|---|---|
| Từng message | Dễ hiểu, dễ map lỗi | Latency/throughput kém |
| Batch | Throughput tốt hơn | Retry batch phức tạp |
| Async | Throughput và latency tốt | Cần tracker/cleanup |

Chọn dựa trên message rate, confirm latency, memory, retry policy, idempotency và yêu cầu mất message.

## 11. Mandatory publish và return callback

### 11.1 Unroutable message

Một message có thể được confirm nhưng không tới queue:

~~~text
exchange tồn tại
routing key không có binding
broker accept publish
không queue nhận message
~~~

Mandatory publish yêu cầu broker trả message không route được về producer.

### 11.2 Java client

~~~java
channel.addReturnListener(returned -> {
    System.err.printf(
            "unroutable code=%d exchange=%s key=%s%n",
            returned.getReplyCode(),
            returned.getExchange(),
            returned.getRoutingKey()
    );

    returnedMessageStore.save(returned);
});

channel.basicPublish(
        "orders.events",
        "order.unknown",
        true,
        properties,
        body
);
~~~

API return listener có thể khác theo client version. Cần bảo đảm mandatory=true, có handler, lưu context và có policy retry/alert.

### 11.3 Confirm và return cùng lúc

~~~text
publish:
  -> confirm có thể báo broker accept
  -> return báo không route được
~~~

Hai signal độc lập. Không dùng confirm thay return.

## 12. Mandatory publish với Spring RabbitTemplate

~~~java
@Bean
public RabbitTemplate rabbitTemplate(
        ConnectionFactory connectionFactory,
        MessageConverter converter
) {
    RabbitTemplate template =
            new RabbitTemplate(connectionFactory);
    template.setMessageConverter(converter);
    template.setMandatory(true);
    return template;
}
~~~

Returns callback theo Spring AMQP version:

~~~java
template.setReturnsCallback(returned -> {
    System.err.println(
            "Returned message: " + returned.getMessage()
    );
});
~~~

Theo dõi confirm bằng CorrelationData:

~~~java
CorrelationData correlationData =
        new CorrelationData(messageId);

rabbitTemplate.convertAndSend(
        exchange,
        routingKey,
        payload,
        correlationData
);
~~~

Confirm thành công vẫn không có nghĩa message đã route hoặc consumer đã ack.

## 13. Durable entity và persistent message

Durable exchange/queue:

~~~text
topology entity có mục tiêu tồn tại qua broker restart
~~~

Persistent delivery mode:

~~~java
AMQP.BasicProperties properties =
        new AMQP.BasicProperties.Builder()
                .deliveryMode(2)
                .build();
~~~

Một flow bền hơn thường phối hợp:

~~~text
durable exchange/queue
+ persistent message
+ publisher confirm
+ consumer manual ack
+ idempotent consumer
~~~

Nhưng persistence không có nghĩa:

- consumer chắc chắn xử lý;
- side effect bên ngoài được rollback;
- producer biết message đã route;
- broker không bao giờ mất dữ liệu trong mọi failure;
- duplicate không xảy ra.

Durability chỉ là một phần của reliability.

## 14. Connection recovery không phải business success

Timeline:

~~~text
t0 producer publish
t1 network đứt
t2 producer không biết broker đã nhận chưa
t3 connection recovery
t4 producer retry
t5 message có thể xuất hiện hai lần
~~~

Recovery có thể mở connection/channel lại, nhưng không biết database write, email hoặc payment đã thành công chưa.

Cần:

- messageId/requestId;
- publisher confirms;
- retry policy;
- idempotent consumer;
- không coi reconnect là business transaction.

## 15. Decision matrix

| Tình huống | Producer | Consumer |
|---|---|---|
| Publish không quan trọng | Có thể không confirm | Ack sau xử lý |
| Event quan trọng | Confirm + mandatory return | Manual ack |
| Lỗi tạm thời | Confirm publish | Nack/retry có giới hạn |
| Lỗi dữ liệu | Confirm publish | Không requeue vô hạn, DLQ |
| Không route được | Mandatory + return handler | Không có consumer để ack |
| Side effect quan trọng | Confirm + messageId | Ack sau commit/idempotent effect |
| RPC timeout | Confirm request | Reply correlationId + idempotency |
| Broker reconnect | Theo dõi confirm/retry | Xử lý redelivery |

## 16. Lifecycle thành công và failure

### Thành công

~~~text
producer publish
  -> broker confirm
  -> exchange route
  -> queue giữ message
  -> consumer deliver
  -> business side effect thành công
  -> consumer ack
~~~

### Không route

~~~text
producer publish mandatory
  -> broker confirm publish
  -> exchange không match binding
  -> return callback
  -> producer lưu/retry/alert
~~~

### Consumer crash trước ack

~~~text
producer confirm
  -> queue
  -> consumer nhận
  -> worker crash
  -> connection đóng
  -> message redeliver
~~~

### Producer mất connection

~~~text
producer publish
  -> network failure
  -> không biết confirm
  -> retry có thể duplicate
  -> messageId/idempotency xử lý hậu quả
~~~

## 17. Lab 1 — Tắt broker sau publish

Thực hiện:

1. Bật publisher confirms.
2. Publish 1.000 message.
3. Ghi messageId, thời điểm publish và confirm.
4. Tắt broker giữa chừng:

~~~bash
docker compose stop rabbitmq
~~~

5. Ghi số confirmed, failed, unknown outcome và pending confirm.
6. Khởi động lại:

~~~bash
docker compose start rabbitmq
~~~

7. Retry message chưa confirm theo messageId.
8. Kiểm tra duplicate ở queue/consumer.

| Metric | Value |
|---|---:|
| Published attempts | |
| Confirmed | |
| Nacked/failed | |
| Unknown outcome | |
| Retried | |
| Duplicate detected | |

Kết luận: message chưa confirm có outcome không chắc chắn; retry an toàn cần idempotency hoặc deduplication.

## 18. Lab 2 — Unroutable publish

1. Tạo exchange orders.events.
2. Chỉ bind routing key order.created.
3. Bật confirm và return listener.
4. Publish order.created.
5. Publish order.unknown với mandatory=true.
6. Ghi confirm và return của từng message.
7. Kiểm tra queue count.

Dự kiến:

~~~text
order.created:
  confirm = có
  return  = không

order.unknown:
  confirm có thể = có
  return = có
  queue delivery = không
~~~

Returned message quan trọng phải được lưu hoặc alert, không chỉ log.

## 19. Lab 3 — Exception trước ack

1. Publish message có messageId.
2. Consumer nhận manual ack.
3. Ném exception trước ack.
4. Đóng channel hoặc để container xử lý.
5. Chạy consumer lại.
6. Log redelivered flag.
7. Ghi số delivery và side effect.
8. Thử nack requeue=false và ghi message đi đâu.

Không dùng requeue vô hạn. Ghi policy DLQ dự kiến cho tuần 8.

## 20. Lab 4 — Confirm latency và unconfirmed count

Ghi các field:

~~~text
publish_started_at
publish_confirmed_at
confirm_latency_ms
publish_message_id
confirm_status
unconfirmed_count
returned_count
~~~

Chạy với:

- payload nhỏ và lớn;
- từng message;
- batch 10;
- batch 100;
- broker idle và broker có backlog.

Phân tích p50/p95 confirm latency, unconfirmed peak, memory và throughput.

## 21. Lab 5 — Durable/persistent restart

Tạo:

~~~text
durable.exchange
durable.queue
non-durable.exchange
non-durable.queue
~~~

Publish persistent và non-persistent message vào durable queue. Restart broker rồi kiểm tra.

Tách kết luận:

~~~text
entity tồn tại?
message còn?
publish có confirm?
consumer đã ack?
side effect có idempotent?
~~~

Không dùng một thử nghiệm local để tuyên bố exactly-once production.

## 22. Metric và alert

### Producer

- publish attempts;
- confirmed count;
- failed/nack count;
- confirm latency;
- pending/unconfirmed count;
- returned message count;
- retry count;
- unroutable routing key count.

### Consumer

- delivery count;
- ack count;
- nack/reject count;
- redelivery count;
- processing latency;
- failure count;
- requeue count;
- DLQ count;
- oldest ready age.

Alert khi unconfirmed count, return count, redelivery ratio, requeue loop hoặc confirm latency tăng bất thường.

## 23. Lỗi thiết kế thường gặp

- Chỉ bật confirm và nghĩ đã reliable.
- Chỉ dùng return và nghĩ message đã durable.
- Ack trước side effect.
- Nack requeue vô hạn.
- Dùng redelivered như retry counter.
- Retry cả batch không có idempotency.
- Hardcode message identity.
- Coi durable queue là exactly-once.
- Coi connection recovery là business success.
- Chỉ nhìn ready mà bỏ qua unacked và processing time.

## 24. Checklist reliability

- [ ] Event quan trọng dùng publisher confirms.
- [ ] Publish quan trọng có mandatory return handler.
- [ ] Producer lưu messageId/requestId.
- [ ] Pending confirm có giới hạn memory.
- [ ] Confirm latency được đo.
- [ ] Returned message được lưu/alert.
- [ ] Consumer ack sau side effect quan trọng.
- [ ] Requeue chỉ dùng cho lỗi tạm thời.
- [ ] Không requeue vô hạn.
- [ ] Redelivery không phải retry counter duy nhất.
- [ ] Durable entity và persistent message đúng mục tiêu.
- [ ] Consumer idempotent.
- [ ] Recovery không bị coi là business success.
- [ ] Có test broker crash, unroutable publish và consumer crash.

## 25. Deliverable cuối tuần

Tạo:

~~~text
rabbitmq/week-07/
├── README.md
├── diagrams/
│   └── reliability-lifecycle.md
└── src/
    ├── ConfirmingProducer.java
    ├── ReturningProducer.java
    ├── ReliableConsumer.java
    └── Metrics.java
~~~

README cần có:

1. Lifecycle từ publish tới consumer ack.
2. So sánh basic.ack, basic.nack và basic.reject.
3. Policy requeue cho transient/permanent/poison error.
4. Code confirm từng message, batch và async.
5. Return callback cho mandatory publish.
6. Decision matrix producer/consumer.
7. Kết quả tắt broker giữa publish.
8. Kết quả unroutable routing key.
9. Kết quả exception trước ack và redelivery.
10. Metric confirm latency và unconfirmed count.
11. Phân tích durable/persistent sau restart.
12. Giới hạn của connection recovery.

## 26. Tiêu chí tự kiểm tra

1. basic.ack xác nhận điều gì?
2. basic.nack khác basic.reject ở đâu?
3. Khi nào requeue hợp lý?
4. Vì sao requeue vô hạn nguy hiểm?
5. Vì sao redelivered không phải retry counter?
6. Publisher confirm xác nhận điều gì?
7. Confirm có chứng minh message vào queue không?
8. Return callback phát hiện tình huống nào?
9. Vì sao event quan trọng nên dùng confirm và mandatory?
10. Confirm từng message, batch và async khác nhau thế nào?
11. Vì sao batch retry có thể duplicate?
12. Durable queue khác persistent message thế nào?
13. Vì sao durable/persistent không tạo exactly-once?
14. Connection recovery không chứng minh điều gì?
15. Ack nên đặt trước hay sau side effect?
16. Broker restart giữa publish tạo outcome không chắc chắn nào?
17. Metric nào cảnh báo unroutable message?
18. Vì sao consumer vẫn phải idempotent dù producer có confirm?

### Đáp án cần nhớ

> Publisher confirm giúp producer biết broker đã accept publish; mandatory return giúp phát hiện message không route được; consumer ack giúp broker biết processing đã hoàn tất. Ba signal độc lập. Hãy confirm event quan trọng, xử lý return, ack sau side effect, giới hạn requeue, dùng idempotency cho duplicate và không nhầm durable/persistent với exactly-once.

## 27. Chuẩn bị cho Tuần 8

Tuần sau sẽ thiết kế retry, TTL, dead-letter exchange và DLQ:

- phân loại transient/permanent/poison error;
- retry queue 5 giây, 30 giây, 5 phút;
- TTL và DLX;
- exponential backoff, jitter và retry limit;
- error metadata và replay DLQ;
- tránh retry loop và duplicate nguy hiểm.

Giữ lại confirming producer, returning producer và reliable consumer của tuần này. Chúng sẽ là nền để kiểm tra retry message có được publish, route, delay và đưa vào DLQ đúng cách.

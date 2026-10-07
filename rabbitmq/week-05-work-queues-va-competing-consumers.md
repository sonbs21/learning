# Tuần 5 — Work queues và competing consumers

> Mục tiêu của bài này là biến một queue thành một nhóm worker có thể scale và chịu được crash. Bạn sẽ hiểu manual acknowledgement, prefetch, fair dispatch, graceful shutdown và đo được tác động của concurrency.

## 1. Kết quả cần đạt

Sau bài học, bạn có thể:

- Mô tả mô hình một queue với nhiều consumer.
- Phân biệt competing consumers với broadcast qua nhiều queue.
- Giải thích fair dispatch và prefetch.
- Dùng manual acknowledgement thay auto acknowledgement.
- Hiểu delivery tag, multiple ack, reject và nack.
- Viết worker xử lý message rồi mới ack.
- Quan sát ready, unacked, consumer count, processing time và redelivery.
- Mô phỏng worker crash trước và sau business processing.
- Thiết kế graceful shutdown khi deploy.
- Nhận biết khi ordering bị thay đổi bởi concurrency/retry.
- Chọn prefetch dựa trên processing time và giới hạn downstream.

## 2. Work queue và competing consumers

Một queue chứa 10.000 email có thể được xử lý bởi một worker:

~~~text
email.queue -> worker-1 -> SMTP provider
~~~

Hoặc nhiều worker cùng đọc queue:

~~~text
                         ┌── worker-1
email.queue ─────────────┼── worker-2
                         └── worker-3
~~~

Đây là competing consumers:

- các consumer đọc cùng một queue;
- mỗi message được giao cho một consumer tại một thời điểm;
- worker xử lý xong có thể nhận message tiếp theo;
- scale worker không tạo bản sao cho cùng message;
- nếu cần nhiều subscriber đều nhận event, phải dùng queue riêng.

### Work queue khác pub/sub

~~~text
một queue + nhiều consumer = chia việc
nhiều queue + mỗi queue có consumer riêng = broadcast
~~~

Ví dụ pub/sub:

~~~text
OrderCreated
  ├── email.queue      -> email workers
  ├── analytics.queue  -> analytics workers
  └── audit.queue      -> audit workers
~~~

Không dùng một queue chung cho email, analytics và audit nếu cả ba đều phải nhận cùng một event.

## 3. Vòng đời message với manual ack

~~~text
READY
  -> DELIVERED / UNACKED
       -> ACKED
       -> REJECTED
       -> NACKED + REQUEUED
       -> NACKED + DROPPED/DLQ
~~~

- READY: đang chờ consumer.
- UNACKED: đã giao nhưng chưa được xác nhận.
- ACKED: broker nhận xác nhận xử lý thành công.
- REJECTED/NACKED: consumer từ chối hoặc phủ định delivery.
- REQUEUED: message quay lại queue.
- DLQ/dropped: message rời queue theo policy lỗi.

Ack nên xảy ra sau business processing.

## 4. Auto ack và manual ack

### Auto ack

Auto ack đơn giản nhưng nguy hiểm với công việc quan trọng:

~~~text
broker giao message
  -> message được ack
  -> worker crash trước khi xử lý
  -> message không còn trong queue
~~~

Không nên mặc định dùng auto ack cho email quan trọng, thanh toán, hóa đơn hoặc cập nhật dữ liệu không thể tái tạo.

### Manual ack

~~~text
1. nhận message
2. validate payload
3. thực hiện công việc
4. ghi kết quả/side effect thành công
5. ack
~~~

Java client:

~~~java
channel.basicAck(deliveryTag, false);
~~~

Nếu worker crash trước ack, message chưa được xác nhận và có thể được requeue sau khi channel/connection đóng.

Ack chỉ xác nhận quan hệ consumer-broker. Nó không rollback được email, HTTP call, payment hay database transaction ở hệ thống khác. Vẫn cần idempotency.

## 5. Fair dispatch và prefetch

Khi nhiều consumer đọc queue, prefetch giới hạn số message chưa ack mà mỗi consumer/channel có thể giữ.

~~~text
prefetch = 1
  consumer giữ rất ít delivery chưa ack

prefetch = 10
  consumer có thể giữ nhiều delivery hơn

prefetch = 100
  consumer có thể hoard nhiều message
~~~

### Prefetch 1

Ưu điểm:

- fairness tốt hơn khi processing time khác nhau;
- ít message bị giữ khi worker crash;
- memory pressure thấp.

Nhược điểm:

- throughput có thể thấp với message xử lý cực nhanh;
- ack round-trip có thể thành bottleneck.

### Prefetch 10

Có thể tăng throughput mà vẫn giới hạn in-flight message. Đây là giá trị đáng thử trong lab, không phải mặc định cho mọi workload.

### Prefetch 100

Có thể tăng throughput trong workload phù hợp nhưng rủi ro:

- worker chậm giữ nhiều message;
- ready thấp nhưng unacked cao;
- crash tạo burst redelivery;
- memory pressure tăng;
- rolling deploy khó drain.

Fair dispatch không có nghĩa mọi consumer nhận chính xác cùng số message. Consumer nhanh có thể xử lý nhiều hơn; mục tiêu là không để consumer chậm giữ backlog quá lớn.

## 6. Đo lường work queue

Theo dõi ít nhất:

~~~text
queue depth
messages ready
messages unacked
consumer count
publish rate
delivery rate
ack rate
processing duration
failure count
redelivery count
oldest message age
~~~

Throughput:

~~~text
throughput = số message xử lý thành công / thời gian
~~~

Ví dụ 1.000 message trong 60 giây là khoảng 16,67 message/giây.

Bảng thử nghiệm:

| Biến | Giá trị |
|---|---|
| Consumer | 1, 3, 5 |
| Prefetch | 1, 10, 100 |
| Processing time | 10 ms, 100 ms, 500 ms |
| Payload | nhỏ, vừa, lớn |
| Downstream | nhanh, chậm, lỗi ngẫu nhiên |

Không chỉ nhìn ready. Ready thấp nhưng unacked cao có thể nghĩa là consumer đang ôm hết message.

## 7. Delivery tag và multiple ack

Delivery tag do channel cấp:

~~~java
long deliveryTag =
        delivery.getEnvelope().getDeliveryTag();
~~~

Ack/reject/nack:

~~~java
channel.basicAck(deliveryTag, false);
channel.basicReject(deliveryTag, true);
channel.basicNack(deliveryTag, false, true);
~~~

Delivery tag:

- chỉ có ý nghĩa trong channel đã tạo delivery;
- không phải event ID;
- có thể bắt đầu lại khi channel mới mở;
- không dùng làm idempotency key;
- không dùng tag của channel A để ack trên channel B.

Multiple ack:

~~~java
channel.basicAck(deliveryTag, true);
~~~

multiple=true ack delivery hiện tại và các delivery chưa ack trước đó trên cùng channel, tới tag chỉ định.

Ví dụ:

~~~text
tag 1 xong
tag 2 xong
tag 3 xong
ack tag 3, multiple=true
=> ack 1, 2, 3
~~~

Nếu xử lý song song và tag 1 chưa xong nhưng tag 3 đã xong, multiple ack có thể ack nhầm tag 1. Consumer cơ bản nên dùng multiple=false.

## 8. Reject và nack

Reject một delivery:

~~~java
channel.basicReject(deliveryTag, requeue);
~~~

Nack một hoặc nhiều delivery:

~~~java
channel.basicNack(
        deliveryTag,
        multiple,
        requeue
);
~~~

Requeue phù hợp với lỗi tạm thời:

- database tạm unavailable;
- HTTP downstream timeout;
- dependency chập chờn.

Không requeue vô hạn lỗi dữ liệu:

- JSON sai schema;
- thiếu field bắt buộc;
- business rule không thể thỏa mãn;
- poison message.

Loop nguy hiểm:

~~~text
consume -> fail -> requeue
  -> consume -> fail -> requeue
  -> lặp vô hạn
~~~

Retry hữu hạn và DLQ sẽ được học ở phần reliability. Tuần này cần biết phân biệt lỗi tạm thời và lỗi không thể tự sửa.

## 9. Consumer concurrency

Có thể scale bằng:

~~~text
một process + nhiều consumer/channel
nhiều process + mỗi process có consumer
~~~

Concurrency không tạo thứ tự:

~~~text
publish:  A B C
deliver:  A -> c1, B -> c2, C -> c1
complete: B A C
~~~

B có thể hoàn tất trước A. Nếu domain cần ordering, phải thiết kế riêng bằng partition/key, serial processing, sequence number hoặc state transition có điều kiện.

Tăng consumer có thể làm:

- CPU tăng;
- database pool cạn;
- API downstream rate limit;
- lock contention;
- ordering thay đổi;
- retry khó quan sát.

Tăng dần và đo thay vì đặt concurrency cao ngay từ đầu.

## 10. Work queue bằng Java client

Khai báo queue và prefetch:

~~~java
channel.queueDeclare(
        "thumbnail.work",
        true,
        false,
        false,
        null
);

channel.basicQos(1);
~~~

Consumer manual ack:

~~~java
DeliverCallback callback = (consumerTag, delivery) -> {
    long tag = delivery.getEnvelope().getDeliveryTag();

    try {
        processThumbnail(delivery.getBody());
        channel.basicAck(tag, false);
    } catch (TemporaryException exception) {
        channel.basicNack(tag, false, true);
    } catch (PermanentException exception) {
        channel.basicReject(tag, false);
    }
};

channel.basicConsume(
        "thumbnail.work",
        false,
        callback,
        consumerTag ->
                System.out.println("cancelled: " + consumerTag)
);
~~~

Producer lab:

~~~java
for (int index = 1; index <= 100; index++) {
    String body = "{\"imageId\":\"image-" + index + "\"}";

    channel.basicPublish(
            "",
            "thumbnail.work",
            null,
            body.getBytes(StandardCharsets.UTF_8)
    );
}
~~~

Trong production nên publish vào exchange domain rõ ràng thay vì phụ thuộc default exchange.

## 11. Work queue với Spring AMQP

Queue:

~~~java
@Bean
public Queue thumbnailWorkQueue() {
    return QueueBuilder
            .durable("thumbnail.work")
            .build();
}
~~~

Manual ack factory:

~~~java
@Bean
public SimpleRabbitListenerContainerFactory manualAckFactory(
        ConnectionFactory connectionFactory,
        MessageConverter messageConverter
) {
    SimpleRabbitListenerContainerFactory factory =
            new SimpleRabbitListenerContainerFactory();
    factory.setConnectionFactory(connectionFactory);
    factory.setMessageConverter(messageConverter);
    factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
    factory.setConcurrentConsumers(1);
    factory.setMaxConcurrentConsumers(3);
    factory.setPrefetchCount(1);
    return factory;
}
~~~

Listener:

~~~java
@RabbitListener(
        id = "thumbnailListener",
        queues = "thumbnail.work",
        containerFactory = "manualAckFactory"
)
public void handle(
        ThumbnailJob job,
        Message message,
        Channel channel
) throws IOException {
    long tag = message.getMessageProperties()
            .getDeliveryTag();

    try {
        thumbnailService.process(job);
        channel.basicAck(tag, false);
    } catch (TemporaryException exception) {
        channel.basicNack(tag, false, true);
    } catch (PermanentException exception) {
        channel.basicReject(tag, false);
    }
}
~~~

Lưu ý:

- ack đúng delivery tag;
- channel phải là channel của listener delivery;
- không ack trước process;
- không requeue vô hạn;
- khi có concurrency, mỗi delivery phải có lifecycle rõ;
- exception handling phải có policy.

Nếu dùng auto mode, container có thể ack khi method return bình thường. Nếu tự catch rồi nuốt exception, container có thể nghĩ message đã xử lý thành công.

## 12. Graceful shutdown

Khi deploy version mới:

~~~text
1. ngừng nhận delivery mới
2. chờ delivery đang xử lý hoàn tất
3. ack message đã xử lý thành công
4. để message chưa ack requeue khi channel đóng
5. đóng consumer/channel/connection
6. process exit
~~~

### Java client

Lưu consumer tag:

~~~java
String consumerTag = channel.basicConsume(
        "thumbnail.work",
        false,
        callback,
        cancelCallback
);
~~~

Khi shutdown:

~~~java
channel.basicCancel(consumerTag);
channel.close();
connection.close();
~~~

Cần chờ in-flight work hoàn tất trước khi đóng channel. Không chỉ gọi close ngay rồi giả định task đã xong.

### Spring listener container

Đặt id cho listener:

~~~java
@RabbitListener(
        id = "thumbnailListener",
        queues = "thumbnail.work",
        containerFactory = "manualAckFactory"
)
public void handle(...) {
    // ...
}
~~~

Dừng qua registry:

~~~java
@Autowired
private RabbitListenerEndpointRegistry registry;

registry.getListenerContainer("thumbnailListener")
        .stop();
~~~

Kiểm tra behavior của version Spring AMQP đang dùng và thời gian shutdown. Mục tiêu là không nhận delivery mới và cho in-flight message có cơ hội hoàn tất.

## 13. Ordering

Một consumer tuần tự thường nhận và hoàn tất theo thứ tự, nhưng requeue/retry có thể làm quan sát phức tạp.

Với nhiều consumer:

~~~text
publish:  A B C
deliver:  A -> c1, B -> c2, C -> c1
complete: B A C
~~~

Nếu trạng thái order phải chuyển:

~~~text
OrderCreated -> OrderPaid -> OrderShipped
~~~

Không chỉ dựa vào queue chung với nhiều consumer. Cân nhắc:

- serial processing cho cùng key;
- partition theo orderId;
- sequence number;
- optimistic state transition;
- kiểm tra version trước khi ghi;
- handler chịu được event đến trễ.

Concurrency là lựa chọn throughput; ordering là lựa chọn correctness.

## 14. Lab 1 — Worker xử lý thumbnail

Tạo queue thumbnail.work và worker giả lập:

~~~java
void process(ThumbnailJob job) throws InterruptedException {
    Thread.sleep(job.processingMillis());
}
~~~

Tạo 100 job có thời gian 10 ms, 50 ms và 500 ms.

Thực hiện:

1. Publish 100 job.
2. Chạy một consumer với prefetch 1.
3. Ghi processing time và throughput.
4. Chạy ba consumer.
5. So sánh queue depth, unacked và completion rate.
6. Dừng một worker đang xử lý.
7. Kiểm tra redelivery sau khi worker khác khởi động.

Bảng ghi nhận:

| Run | Consumers | Prefetch | Total time | Redelivery | Notes |
|---|---:|---:|---:|---:|---|
| A | 1 | 1 | | | |
| B | 3 | 1 | | | |
| C | 3 | 10 | | | |
| D | 3 | 100 | | | |

## 15. Lab 2 — So sánh prefetch

Giữ cùng 1.000 message và cùng số consumer. Chỉ thay:

~~~text
run A: prefetch = 1
run B: prefetch = 10
run C: prefetch = 100
~~~

Ghi:

- tổng thời gian;
- max ready;
- max unacked;
- processing time p50/p95 nếu có;
- số message worker chậm giữ;
- redelivery sau crash.

Dự đoán:

- prefetch 1 thường fair hơn;
- prefetch 10 có thể throughput tốt hơn;
- prefetch 100 có thể làm worker giữ nhiều message;
- kết quả phụ thuộc processing time, payload, network và downstream.

## 16. Lab 3 — Crash trước và sau processing

### Crash trước processing

~~~text
receive message
crash before business work
~~~

Message chưa ack nên có thể quay lại queue.

### Crash sau processing trước ack

~~~text
receive message
write thumbnail successfully
crash before ack
~~~

Worker mới có thể xử lý lại và tạo duplicate side effect. RabbitMQ chỉ biết chưa nhận ack; nó không biết thumbnail đã ghi thành công.

### Ack trước processing

~~~text
receive message
ack
crash before write
~~~

Message có thể mất khỏi queue trong khi thumbnail chưa được tạo.

### Thực hiện

Dùng cơ chế lab riêng để dừng process trước ack. Không dùng halt trong production.

Kiểm tra:

- ready/unacked trước crash;
- message sau crash;
- redelivered flag;
- số side effect đã ghi;
- duplicate khi chạy lại.

## 17. Lab 4 — Graceful shutdown

1. Chạy ba consumer.
2. Publish đủ message để cả ba bận.
3. Shutdown một consumer.
4. Đảm bảo consumer không nhận message mới.
5. Chờ in-flight message ack.
6. Kill cứng một consumer để so sánh.
7. Ghi khác nhau giữa graceful stop và crash.

| Cách dừng | In-flight hoàn tất | Requeued | Duplicate risk | Mất message risk |
|---|---|---|---|---|
| graceful stop | | | | |
| process crash | | | | |
| ack trước work | | | | |

## 18. Lab 5 — Reject, nack và requeue

Tạo ba loại lỗi:

~~~text
temporary failure
permanent invalid payload
poison message
~~~

Policy:

| Lỗi | Hành động tạm thời | Lý do |
|---|---|---|
| Temporary | nack requeue | Có khả năng thành công sau |
| Invalid payload | reject không requeue | Không tự sửa được |
| Poison message | giới hạn retry rồi DLQ | Tránh loop vô hạn |

Ghi lại số lần message được giao và cách nhận diện message retry nhiều lần.

## 19. Phân tích metric

### Queue depth

Nếu ready liên tục tăng:

~~~text
in_rate > out_rate
~~~

Kiểm tra producer rate, consumer throughput, downstream latency và error rate.

### Unacked

Unacked cao có thể nghĩa là:

- processing chậm;
- prefetch quá cao;
- consumer bị treo;
- ack bị quên;
- downstream timeout;
- concurrency giữ nhiều task.

Unacked thấp chưa chắc an toàn. Nếu auto ack quá sớm, message có thể mất khi worker crash.

### Redelivery

Redelivery tăng có thể do crash, channel đóng trước ack, nack requeue hoặc connection reset. Cần giới hạn và quan sát, không coi mọi redelivery là lỗi broker.

## 20. Lỗi thiết kế thường gặp

- Prefetch quá cao để che backlog.
- Ack trước side effect.
- Requeue vô hạn poison message.
- Tăng consumer không giới hạn.
- Dùng một queue cho nhiều business responsibility.
- Giả định ordering khi có concurrency.
- Kill cứng worker khi đang có in-flight work.
- Không có idempotency cho side effect.
- Chỉ nhìn ready mà bỏ qua unacked và processing time.

## 21. Checklist work queue

- [ ] Queue có tên ổn định và durable nếu công việc cần tồn tại.
- [ ] Consumer dùng manual ack cho công việc quan trọng.
- [ ] Ack chỉ sau processing thành công.
- [ ] Có eventId/idempotency key.
- [ ] Prefetch được chọn và đo.
- [ ] Concurrency phù hợp downstream.
- [ ] Reject/nack có policy rõ.
- [ ] Không requeue vô hạn poison message.
- [ ] Có metric ready, unacked, processing time và redelivery.
- [ ] Có graceful shutdown.
- [ ] Có test crash trước và sau ack.
- [ ] Ordering requirement được ghi rõ.
- [ ] Competing consumers không bị nhầm với pub/sub.
- [ ] Multiple ack chỉ dùng khi chắc chắn thứ tự hoàn tất.

## 22. Deliverable cuối tuần

Tạo:

~~~text
rabbitmq/week-05/
├── README.md
├── diagrams/
│   └── work-queue-lifecycle.md
└── src/
    ├── Worker.java
    ├── Producer.java
    └── GracefulShutdown.java
~~~

README cần có:

1. Sơ đồ một queue với ba competing consumers.
2. So sánh work queue và pub/sub.
3. Giải thích ready, unacked, acked và redelivered.
4. Bảng đo prefetch 1, 10 và 100.
5. Log delivery tag và redelivered flag.
6. Kết quả crash trước processing và sau processing trước ack.
7. Kết quả graceful shutdown so với kill cứng.
8. Policy reject/nack/requeue cho ba loại lỗi.
9. Giải thích vì sao concurrency làm ordering hoàn tất thay đổi.
10. Quyết định prefetch ban đầu và lý do.

## 23. Tiêu chí tự kiểm tra

1. Competing consumers khác pub/sub thế nào?
2. Vì sao nhiều consumer cùng queue không nhận cùng một message?
3. READY và UNACKED khác nhau thế nào?
4. Vì sao auto ack có thể làm mất message?
5. Ack nên đặt trước hay sau business processing?
6. Delivery tag có phải message ID không?
7. Multiple ack có rủi ro gì khi xử lý song song?
8. Reject và nack khác nhau ở điểm nào?
9. Khi nào requeue là hợp lý?
10. Vì sao requeue vô hạn nguy hiểm?
11. Prefetch 1, 10 và 100 ảnh hưởng fairness và memory thế nào?
12. Vì sao tăng consumer có thể làm database quá tải?
13. Vì sao concurrency làm thứ tự hoàn tất thay đổi?
14. Crash sau side effect nhưng trước ack có thể tạo gì?
15. Graceful shutdown cần làm những bước nào?
16. Vì sao unacked thấp chưa chắc hệ thống an toàn?
17. Khi queue ready tăng liên tục, cần đo metric nào?

### Đáp án cần nhớ

> Work queue chia công việc giữa các consumer; pub/sub dùng nhiều queue để mỗi subscriber nhận bản riêng. Manual ack bảo vệ message khỏi bị xóa trước khi xử lý xong, nhưng không loại bỏ duplicate. Prefetch thấp thường fair hơn, prefetch cao có thể throughput tốt hơn nhưng dễ hoard message và tăng memory pressure. Hãy ack sau processing, idempotent side effect, giới hạn requeue, đo ready/unacked/redelivery và shutdown graceful.

## 24. Chuẩn bị cho Tuần 6

Tuần sau sẽ kết hợp các primitive thành publish/subscribe, routing, topic và request/reply:

- fanout cho audit, notification và analytics;
- direct routing;
- topic pattern với wildcard;
- reply queue và correlation ID;
- timeout trong request/reply;
- phân biệt event notification với command;
- giới hạn của RPC qua broker.

Giữ lại kết quả benchmark và worker code của tuần này. Tuần 6 sẽ dùng chúng để so sánh một queue work distribution với nhiều queue independent subscribers.

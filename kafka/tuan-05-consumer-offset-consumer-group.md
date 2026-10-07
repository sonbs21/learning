# Tuần 5 — Consumer, offset và consumer group

> Mục tiêu: viết được Kafka consumer không làm mất record do commit sai, hiểu lag/assignment và thiết kế commit strategy rõ ràng.

## 1. Kết quả cần đạt

Sau bài học, bạn có thể:

- Mô tả poll loop, fetch và deserialize trong Java consumer.
- Cấu hình group.id, subscription và consumer client.
- Hiểu partition assignment trong consumer group.
- Phân biệt current position, committed offset, log end offset và lag.
- Hiểu auto commit và manual commit.
- Phân tích hậu quả của commit trước hoặc sau xử lý.
- Chọn earliest, latest hoặc chiến lược xử lý offset out of range.
- Cấu hình max.poll.interval.ms, max.poll.records, session.timeout.ms và heartbeat.
- Dùng pause/resume khi downstream chậm.
- Seek về offset cũ để replay có kiểm soát.
- Thiết kế graceful shutdown và commit strategy cho consumer.

## 2. Consumer hoạt động thế nào?

Luồng khái niệm:

    Kafka broker
        |
        | fetch records
        v
    Consumer poll()
        |
        v
    Deserialize key/value
        |
        v
    Application processing
        |
        v
    Commit offset

Consumer không nhận record bằng callback giống producer. Application chủ động gọi poll để lấy record. Trong một consumer instance, poll loop là trung tâm của:

- lấy record mới;
- gửi heartbeat hoặc duy trì membership theo client behavior;
- phát hiện assignment/rebalance;
- trả record cho application;
- xử lý commit;
- pause/resume partition;
- kiểm soát nhịp xử lý.

Một consumer nên có một poll loop rõ ràng, không bị block quá lâu bởi business logic.

## 3. Consumer properties cơ bản

### 3.1 bootstrap.servers

    bootstrap.servers=localhost:9092

Giống producer, consumer dùng bootstrap để lấy metadata. Consumer sau đó biết broker leader của partition được assign.

### 3.2 group.id

group.id xác định consumer group:

    group.id=order-payment

Các consumer có cùng group.id chia partition của topic. Các consumer khác group.id đọc độc lập.

Không dùng một group chung cho các ứng dụng đều phải nhận toàn bộ event:

    payment group    -> nhận event
    inventory group  -> nhận event
    analytics group  -> nhận event

Nếu Payment và Inventory dùng cùng group, chúng sẽ chia nhau record thay vì mỗi ứng dụng nhận tất cả.

### 3.3 key.deserializer và value.deserializer

    key.deserializer=org.apache.kafka.common.serialization.StringDeserializer
    value.deserializer=org.apache.kafka.common.serialization.StringDeserializer

Deserializer phải khớp format producer. Deserialization error cần được xử lý rõ; không nên để một poison record làm consumer crash lặp vô hạn mà không có alert hoặc error path.

### 3.4 enable.auto.commit

Auto commit:

    enable.auto.commit=true

Manual commit:

    enable.auto.commit=false

Auto commit đơn giản cho lab nhưng khó kiểm soát chính xác khi processing có side effect. Production thường cần commit strategy gắn với trạng thái xử lý.

### 3.5 auto.offset.reset

Các lựa chọn thường gặp:

- earliest;
- latest;
- none.

Property này chủ yếu có tác dụng khi group chưa có committed offset hoặc offset hiện tại không còn hợp lệ.

Đừng hiểu latest là “luôn chỉ đọc record mới”. Nếu group đã có offset, consumer thường tiếp tục từ committed offset của group.

## 4. Poll loop

### 4.1 Loop cơ bản

    Properties props = new Properties();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
    props.put(ConsumerConfig.GROUP_ID_CONFIG, "order-payment");
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
        StringDeserializer.class.getName());
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
        StringDeserializer.class.getName());
    props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

    try (KafkaConsumer<String, String> consumer =
             new KafkaConsumer<>(props)) {

        consumer.subscribe(List.of("dev.orders.order-created.v1"));

        while (running) {
            ConsumerRecords<String, String> records =
                consumer.poll(Duration.ofMillis(500));

            for (ConsumerRecord<String, String> record : records) {
                process(record);
            }

            consumer.commitSync();
        }
    }

Đây là skeleton học tập. Production cần shutdown flag, xử lý exception, timeout, metric, retry/error path và commit strategy phù hợp.

### 4.2 Poll không chỉ là lấy record

Consumer cần gọi poll đều đặn. Nếu application xử lý trong một lần poll quá lâu, broker/group coordinator có thể coi consumer không còn hoạt động hoặc consumer vượt max.poll.interval.ms.

Vì vậy cần kiểm soát:

- số record mỗi poll;
- thời gian xử lý mỗi batch;
- downstream latency;
- max.poll.interval.ms;
- graceful shutdown;
- rebalance behavior.

### 4.3 Empty poll

Poll có thể trả về rỗng. Đây không phải lỗi:

- chưa có record mới;
- consumer đang chờ broker;
- group chưa assignment xong;
- partition đang paused;
- consumer đang reconnect.

Không dùng empty poll làm lý do commit vô điều kiện nếu không có record và không có processing state cần commit.

## 5. Subscription và assignment

### 5.1 Subscribe

    consumer.subscribe(List.of("dev.orders.order-created.v1"));

Consumer tham gia group và coordinator phân assignment. Application không tự quyết định partition cụ thể trong mô hình subscribe.

### 5.2 Assignment

Ví dụ topic có 3 partition:

    Topic: orders
      partition 0
      partition 1
      partition 2

Group có 2 consumer:

    consumer-A -> partition 0, 1
    consumer-B -> partition 2

Nếu thêm consumer-C:

    consumer-A -> partition 0
    consumer-B -> partition 1
    consumer-C -> partition 2

Assignment thực tế phụ thuộc assignor và trạng thái group. Tuần 6 sẽ học sâu về rebalance và assignor.

### 5.3 Số consumer và partition

Nếu group có nhiều consumer hơn partition:

    Topic: 2 partitions
    Group:  4 consumers
    Active: tối đa 2 consumers có partition

Consumer không có assignment vẫn là member nhưng không xử lý record. Đây có thể là dự phòng scale, nhưng không tăng throughput ngay lập tức.

### 5.4 Một partition chỉ thuộc một consumer trong group

Tại một thời điểm ổn định, một partition không được hai consumer trong cùng group xử lý đồng thời theo assignment bình thường. Đây là nền tảng để chia tải và giữ thứ tự trong group.

Các duplicate vẫn có thể xảy ra do crash, commit timing và retry. Assignment không biến hệ thống thành exactly-once.

## 6. Bốn vị trí offset cần phân biệt

### 6.1 Current position

Current position là vị trí consumer đã fetch hoặc sẽ đọc tiếp trong session hiện tại. Nó có thể tiến lên trước khi business processing hoàn tất.

### 6.2 Committed offset

Committed offset là vị trí consumer group đã lưu vào Kafka internal offsets topic. Sau restart hoặc rebalance, consumer thường dùng vị trí này để tiếp tục.

### 6.3 Log end offset

Log end offset là vị trí cuối mà partition hiện có thể đọc đến, thường hiểu là vị trí sau record mới nhất.

### 6.4 Lag

Công thức khái niệm:

    lag = log end offset - committed/current processed offset

Lag cần được đọc theo từng partition và group. Tổng lag là tổng các partition nhưng có thể che giấu một partition hot.

Ví dụ:

    Partition 0: current=100, end=100, lag=0
    Partition 1: current=20,  end=100, lag=80
    Partition 2: current=98,  end=100, lag=2

Tổng lag là 82, nhưng vấn đề chính nằm ở partition 1.

## 7. Commit offset

### 7.1 Commit nghĩa là gì?

Commit offset ghi nhận vị trí mà group sẽ tiếp tục từ đó trong lần chạy sau. Commit không xác nhận business side effect đã transactionally gắn với Kafka trừ khi bạn thiết kế cơ chế phù hợp.

Đây là điểm cốt lõi:

> Commit offset là metadata về tiến độ đọc, không phải bằng chứng mọi tác dụng phụ đã thành công tuyệt đối.

### 7.2 Commit trước xử lý

    for (ConsumerRecord<String, String> record : records) {
        consumer.commitSync();
        process(record);
    }

Hoặc auto commit xảy ra trước khi process hoàn tất.

Nếu process chết sau commit:

    offset đã tiến
    side effect chưa hoàn tất
    record có thể bị bỏ qua

Đây là rủi ro mất record ở mức business processing.

### 7.3 Commit sau xử lý

    for (ConsumerRecord<String, String> record : records) {
        process(record);
        consumer.commitSync();
    }

Nếu process thành công nhưng process chết trước commit:

    side effect đã xảy ra
    offset chưa tiến
    record được đọc lại

Đây là at-least-once, có thể duplicate. Consumer cần idempotency.

### 7.4 Commit theo batch

    ConsumerRecords<String, String> records =
        consumer.poll(Duration.ofMillis(500));

    for (ConsumerRecord<String, String> record : records) {
        process(record);
    }

    consumer.commitSync();

Nếu bất kỳ record nào trong batch lỗi, không nên commit toàn bộ batch một cách mù quáng. Record sau có thể đã xử lý nhưng record trước chưa thành công, dẫn đến quyết định retry/skip phức tạp.

### 7.5 Commit offset cụ thể

Có thể commit offset cho từng partition:

    Map<TopicPartition, OffsetAndMetadata> offsets =
        new HashMap<>();

    offsets.put(
        new TopicPartition(record.topic(), record.partition()),
        new OffsetAndMetadata(record.offset() + 1)
    );

    consumer.commitSync(offsets);

Offset commit thường là offset tiếp theo cần đọc, nên dùng record.offset() + 1. Đây là lỗi off-by-one phổ biến.

### 7.6 commitSync và commitAsync

commitSync:

- chờ kết quả;
- dễ hiểu;
- phù hợp tại boundary an toàn;
- có thể block poll loop.

commitAsync:

- không block lâu như sync;
- có thể nhanh hơn;
- callback lỗi cần xử lý;
- callback cũ có thể hoàn thành sau callback mới;
- không nên dùng trong shutdown cuối cùng mà không commitSync bổ sung.

Một pattern thường gặp:

    consumer.commitAsync((offsets, exception) -> {
        if (exception != null) {
            log.error("Async commit failed", exception);
        }
    });

    // Khi shutdown hoặc trước khi rời group:
    consumer.commitSync();

Đây là pattern khái niệm; strategy thật phải xét rebalance và processing state.

## 8. Auto commit

Auto commit thuận tiện nhưng dễ hiểu sai. Consumer có thể tự commit vị trí dựa trên poll timing, trong khi business processing chưa hoàn thành.

Rủi ro:

- record đã fetch nhưng chưa xử lý;
- application block trong downstream;
- auto commit tiến offset;
- process crash;
- record không được xử lý lại.

Auto commit có thể chấp nhận cho:

- metric không quan trọng;
- demo;
- consumer không có side effect quan trọng;
- workload chấp nhận mất record.

Không nên dùng auto commit mặc định cho payment, inventory reservation hoặc workflow quan trọng nếu chưa phân tích kỹ.

## 9. at-most-once, at-least-once trong consumer

### 9.1 At-most-once

Luồng:

    poll -> commit -> process

Ưu điểm:

- giảm khả năng duplicate;
- logic đơn giản hơn ở downstream.

Rủi ro:

- crash sau commit có thể bỏ qua record;
- không phù hợp dữ liệu không được mất.

### 9.2 At-least-once

Luồng:

    poll -> process -> commit

Ưu điểm:

- record chưa commit có thể được xử lý lại;
- an toàn hơn trước crash về mặt không mất record.

Rủi ro:

- side effect duplicate;
- cần eventId/unique constraint/upsert/idempotency key;
- replay phải có kiểm soát.

### 9.3 Consumer idempotency

Các pattern:

- lưu eventId trong bảng processed_events với unique constraint;
- dùng business key và upsert;
- payment provider nhận idempotency key;
- ghi kết quả và marker xử lý trong cùng database transaction;
- kiểm tra version/sequence của aggregate;
- thiết kế side effect có thể lặp an toàn.

Không dùng một biến memory để dedup nếu cần survive restart.

## 10. auto.offset.reset và offset out of range

### 10.1 earliest

Đọc từ offset sớm nhất còn tồn tại khi group chưa có offset hợp lệ. Hữu ích cho consumer mới cần replay lịch sử còn retention.

### 10.2 latest

Bắt đầu từ record mới nhất, bỏ qua lịch sử chưa có offset. Phù hợp cho notification realtime trong một số trường hợp, nhưng có thể bỏ lỡ dữ liệu cần xử lý.

### 10.3 none

Ném lỗi nếu không có offset hợp lệ. Phù hợp khi application muốn tự xử lý tình huống thay vì âm thầm chọn earliest/latest.

### 10.4 Offset out of range

Có thể xảy ra khi:

- retention đã xóa offset cũ;
- topic được migrate;
- offset bị reset;
- consumer lưu offset không còn tồn tại;
- partition metadata thay đổi theo tình huống vận hành.

Cần quyết định:

- bắt đầu từ earliest còn lại;
- bắt đầu từ latest;
- dừng và alert;
- restore từ backup/replay source;
- tạo group mới có chủ đích.

Không chọn latest để che lỗi nếu dữ liệu bị mất là không chấp nhận được.

## 11. Timeout và heartbeat

### 11.1 max.poll.interval.ms

Đây là khoảng thời gian tối đa giữa hai lần poll mà consumer có thể xử lý record trước khi bị coi là không còn tiến triển.

Nếu processing một batch quá lâu:

    thời gian xử lý > max.poll.interval.ms
    => group có thể rebalance

Cách xử lý:

- giảm max.poll.records;
- tăng max.poll.interval.ms có kiểm soát;
- tối ưu processing;
- chia nhỏ batch;
- tách worker architecture;
- pause partition khi cần;
- không block poll loop vô hạn.

Tăng max.poll.interval.ms chỉ che được processing chậm, không làm downstream nhanh hơn.

### 11.2 max.poll.records

Giới hạn số record trả về mỗi poll. Giảm giá trị giúp giới hạn thời gian xử lý một batch, nhưng quá thấp có thể giảm throughput.

Chọn dựa trên:

    thời gian xử lý mỗi record
    x số record mỗi poll
    < max.poll.interval.ms

Đây là ước lượng, cần đo p95/p99 chứ không chỉ average.

### 11.3 session.timeout.ms

Nếu consumer không gửi heartbeat trong khoảng thời gian phù hợp, coordinator có thể loại consumer khỏi group. Giới hạn thực tế phụ thuộc broker và cấu hình heartbeat.

### 11.4 Heartbeat

Heartbeat cho coordinator biết consumer còn sống. Với client Java, heartbeat thường được quản lý trong consumer internals, nhưng application vẫn phải gọi poll đúng nhịp để duy trì session và assignment.

Heartbeat không cứu được một poll loop bị block vượt max.poll.interval.ms trong mọi trường hợp. Đây là hai cơ chế khác nhau:

- session timeout: liveness/heartbeat;
- max poll interval: tiến độ gọi poll và xử lý.

## 12. Pause và resume

### 12.1 Vì sao cần pause?

Dùng pause khi một partition hoặc downstream tạm thời không thể xử lý nhanh:

- database đang quá tải;
- API downstream rate-limit;
- partition có record nặng;
- muốn ưu tiên partition khác;
- backpressure cần được kiểm soát.

### 12.2 API

    Set<TopicPartition> assigned = consumer.assignment();

    consumer.pause(assigned);

    // Khi downstream phục hồi:
    consumer.resume(assigned);

Pause không xóa record và không commit offset. Nó chỉ yêu cầu consumer không trả record từ partition đó trong các poll tiếp theo.

Vẫn cần poll đều đặn để giữ consumer alive. Pause không phải sleep vô hạn.

### 12.3 Pause từng partition

    TopicPartition hot =
        new TopicPartition("dev.orders.order-created.v1", 1);

    consumer.pause(Set.of(hot));

Có thể pause partition lỗi và tiếp tục partition khác, nhưng cần phân tích ordering. Nếu các record cùng aggregate nằm cùng partition, xử lý bỏ qua một record có thể ảnh hưởng record sau.

### 12.4 Resume có điều kiện

Resume khi:

- queue nội bộ giảm;
- downstream health trở lại;
- rate limit reset;
- circuit breaker đóng;
- memory pressure an toàn.

Ghi metric thời gian paused và số record bị trì hoãn để không biến pause thành silent lag.

## 13. Seek và replay

### 13.1 Seek

Seek thay đổi vị trí đọc của consumer cho partition đã assignment:

    TopicPartition partition =
        new TopicPartition("dev.orders.order-created.v1", 0);

    consumer.seek(partition, 100L);

Sau seek, consumer sẽ đọc từ offset mới theo trạng thái session. Nếu muốn group restart cũng tiếp tục từ đó, cần commit offset tương ứng.

### 13.2 Replay có kiểm soát

Một quy trình replay an toàn:

1. tạo group replay riêng;
2. xác định topic, partition và offset;
3. kiểm tra eventId và side effect;
4. chạy với tốc độ giới hạn;
5. đo duplicate/error;
6. chỉ commit sau khi xử lý đúng;
7. ghi audit về khoảng offset đã replay.

Không seek group production tùy tiện khi chưa freeze side effect và có runbook.

### 13.3 Rewind bằng group mới

Cách an toàn hơn cho thử nghiệm:

    group.id=orders-replay-2026-10-04
    auto.offset.reset=earliest

Group mới có offset độc lập, không làm thay đổi group production.

## 14. Graceful shutdown

Một consumer shutdown đúng cần:

1. ngừng nhận work mới;
2. xử lý hoặc dừng rõ batch đang chạy;
3. commit offset của phần đã xử lý thành công;
4. đóng consumer;
5. rời group và giải phóng resources;
6. ghi log trạng thái shutdown.

Ví dụ khái niệm:

    volatile boolean running = true;

    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
        running = false;
    }));

    try {
        while (running) {
            ConsumerRecords<String, String> records =
                consumer.poll(Duration.ofMillis(500));

            processAndTrack(records);
            commitProcessedOffsets();
        }
    } finally {
        consumer.commitSync();
        consumer.close();
    }

Không commit toàn bộ poll batch nếu chỉ một phần đã xử lý thành công. Cần commit theo offset liên tục an toàn của từng partition.

## 15. Commit strategy theo loại workload

| Workload | Strategy gợi ý | Rủi ro |
|---|---|---|
| Metric có thể mất | Auto hoặc commit sớm | Mất record |
| Ghi database idempotent | Process rồi commit | Duplicate cần xử lý |
| Payment | Process + idempotency + commit | Timeout side effect |
| Gửi email | EventId/dedup + process rồi commit | Email trùng |
| Batch analytics | Track offset từng partition | Batch failure phức tạp |
| Replay | Group riêng, tốc độ giới hạn | Tác động downstream |

Không có commit strategy chung cho mọi topic. Strategy phải đi cùng:

- side effect;
- idempotency;
- retry;
- retention;
- SLA;
- failure recovery.

## 16. Consumer mẫu an toàn hơn

    public final class OrderConsumer implements AutoCloseable {
        private final KafkaConsumer<String, String> consumer;
        private volatile boolean running = true;

        public OrderConsumer(Properties props, String topic) {
            this.consumer = new KafkaConsumer<>(props);
            this.consumer.subscribe(List.of(topic));
        }

        public void run() {
            try {
                while (running) {
                    ConsumerRecords<String, String> records =
                        consumer.poll(Duration.ofMillis(500));

                    Map<TopicPartition, OffsetAndMetadata> processed =
                        new HashMap<>();

                    for (ConsumerRecord<String, String> record : records) {
                        processIdempotently(record);

                        processed.put(
                            new TopicPartition(
                                record.topic(),
                                record.partition()
                            ),
                            new OffsetAndMetadata(record.offset() + 1)
                        );
                    }

                    if (!processed.isEmpty()) {
                        consumer.commitSync(processed);
                    }
                }
            } finally {
                consumer.commitSync();
                consumer.close();
            }
        }

        public void stop() {
            running = false;
        }

        private void processIdempotently(
            ConsumerRecord<String, String> record
        ) {
            // Validate, deduplicate, apply side effect.
        }

        @Override
        public void close() {
            stop();
            consumer.close();
        }
    }

Đây vẫn là skeleton. Trong production cần xử lý exception theo từng record, xử lý partial batch, rebalance listener, metric và shutdown thread-safe.

## 17. Bài lab 1 — Consumer group với 3 partition

1. Tạo topic có 3 partition.
2. Gửi ít nhất 12 record.
3. Chạy consumer group với một instance.
4. Dùng kafka-consumer-groups.sh để xem assignment.
5. Chạy thêm instance thứ hai cùng group.
6. Chạy instance thứ ba.
7. Chạy instance thứ tư.
8. So sánh assignment và throughput.

Ghi lại:

- consumer nào nhận partition nào;
- group có bao nhiêu member;
- partition nào không có assignment nếu có;
- offset và lag theo partition.

## 18. Bài lab 2 — Commit trước và sau xử lý

Tạo hai consumer:

### Consumer A: commit trước

1. Poll record.
2. Commit offset.
3. Sleep hoặc dừng process trước process(record).

Kết quả cần quan sát:

- group offset đã tiến;
- side effect chưa xảy ra;
- restart consumer có thể bỏ qua record.

### Consumer B: commit sau

1. Poll record.
2. Ghi side effect.
3. Dừng process trước commit.

Kết quả:

- side effect có thể đã xảy ra;
- offset chưa tiến;
- restart đọc lại record;
- cần dedup/idempotency.

Dùng eventId hoặc unique constraint để chứng minh duplicate không tạo ra kết quả nghiệp vụ thứ hai.

## 19. Bài lab 3 — Kill consumer giữa lúc xử lý

1. Consumer dùng manual commit.
2. Sleep 5 giây trong process.
3. Gửi một record.
4. Kill process trong khoảng sleep.
5. Restart cùng group.
6. Xem record được đọc lại hay bị bỏ qua.
7. Lặp lại với commit trước processing.
8. So sánh hai kết quả.

Ghi lại timeline:

    poll
    process start
    side effect
    commit
    crash
    restart

Đây là cách tốt để hiểu offset không tự động đi cùng transaction business.

## 20. Bài lab 4 — Observe lag

1. Chạy producer nhanh hơn consumer.
2. Consumer sleep một khoảng sau mỗi record.
3. Định kỳ chạy:

    kafka-consumer-groups.sh \
      --bootstrap-server localhost:9092 \
      --describe \
      --group orders-lab

4. Ghi current offset, log end offset và lag.
5. Tăng tốc consumer.
6. Quan sát lag giảm.
7. Pause một partition rồi xem lag partition đó tăng riêng.

Kết luận cần có:

- lag không phải chỉ số “consumer đã chết”;
- lag có thể do producer tăng tốc, downstream chậm hoặc partition hot;
- tổng lag có thể che giấu partition cụ thể bị nghẽn.

## 21. Bài lab 5 — earliest, latest và offset out of range

### Phần A: group mới

1. Gửi record trước khi chạy group.
2. Chạy group mới với earliest.
3. Chạy group mới với latest.
4. So sánh record nhận được.
5. Gửi record mới.
6. Quan sát cả hai group.

### Phần B: retention

1. Tạo topic retention rất ngắn.
2. Gửi và ghi nhận offset.
3. Chờ record cũ bị cleanup.
4. Tạo consumer dùng offset cũ hoặc group cũ.
5. Quan sát offset out of range.
6. Thử earliest và latest có chủ đích.

Không dùng group production cho lab reset offset.

## 22. Bài lab 6 — Pause/resume

1. Chạy consumer có 3 partition.
2. Pause partition 1 khi lag thấp.
3. Tiếp tục produce record.
4. Quan sát partition 1 lag tăng.
5. Các partition khác vẫn được xử lý.
6. Resume partition 1.
7. Đo thời gian catch up.

Thêm downstream giả lập:

- queue nội bộ vượt giới hạn thì pause;
- queue giảm thì resume;
- log lý do pause/resume;
- metric paused duration.

## 23. Bài lab 7 — Seek và replay

1. Ghi lại topic, partition và offset của một số event.
2. Tạo group replay riêng.
3. Assign/seek về offset bắt đầu.
4. Xử lý trong khoảng giới hạn.
5. Commit offset replay.
6. Kiểm tra group production không bị thay đổi.
7. Đối chiếu eventId và side effect.

Nếu replay có side effect, chạy dry-run hoặc sink test trước.

## 24. Chẩn đoán lỗi consumer

### Consumer không nhận record

Kiểm tra:

1. bootstrap address;
2. topic name;
3. group.id;
4. assignment;
5. committed offset;
6. auto.offset.reset;
7. record có tồn tại trong retention không;
8. producer có gửi vào cluster khác không.

### Consumer đọc lại rất nhiều record

Khả năng:

- commit không chạy;
- commit lỗi;
- process crash trước commit;
- group.id thay đổi;
- offset bị reset;
- replay có chủ ý;
- consumer bị rebalance trước commit.

### Consumer bỏ qua record

Khả năng:

- commit trước xử lý;
- auto commit;
- reset latest;
- offset out of range và chọn latest;
- topic đã cleanup;
- application filter sai.

### Consumer bị rebalance liên tục

Khả năng:

- xử lý vượt max.poll.interval.ms;
- poll loop bị block;
- consumer crash;
- network/session timeout;
- quá nhiều instance join/leave;
- exception làm thread consumer chết.

Tuần 6 sẽ học rebalance sâu hơn, nhưng ở tuần này cần biết max.poll.interval.ms là nghi phạm đầu tiên khi processing quá lâu.

### Lag tăng mãi

Kiểm tra:

- producer rate;
- processing latency;
- downstream latency;
- partition skew;
- assignment;
- số consumer;
- commit rate;
- exception/retry loop;
- pause state.

## 25. Sai lầm phổ biến

### Dùng cùng group cho mọi service

Các service sẽ chia nhau record, làm một số service không nhận event.

### Commit ngay sau poll

Fetch được record không có nghĩa business processing thành công.

### Commit cả batch dù một record lỗi

Có thể bỏ qua record lỗi và làm sai dữ liệu.

### Gọi xử lý nặng trong poll loop không giới hạn

Consumer vượt max.poll.interval.ms và bị loại khỏi group.

### Tăng max.poll.interval.ms vô hạn

Chỉ che triệu chứng, làm detection crash/rebalance chậm hơn.

### Dùng auto commit cho side effect quan trọng

Offset có thể tiến trước business transaction.

### Dùng memory-only deduplication

Restart là mất lịch sử dedup, duplicate quay lại.

### Reset offset production bằng tay

Có thể replay payment/email hoặc bỏ qua dữ liệu.

### Không commit khi shutdown

Các record đã xử lý có thể bị đọc lại; đây không luôn sai nhưng cần hiểu duplicate impact.

### Tưởng pause là dừng consumer hoàn toàn

Consumer vẫn cần poll để duy trì membership và xử lý control flow.

## 26. Câu hỏi tự kiểm tra

1. Poll loop có vai trò gì?
2. group.id dùng để làm gì?
3. Vì sao hai group khác nhau đều đọc được cùng topic?
4. Một topic 3 partition và group 5 consumer sẽ tận dụng bao nhiêu consumer?
5. Current position khác committed offset thế nào?
6. Lag được tính khái niệm ra sao?
7. Commit trước processing gây rủi ro gì?
8. Commit sau processing gây rủi ro gì?
9. Vì sao commit offset không tự động commit database side effect?
10. earliest và latest có tác dụng khi nào?
11. Offset out of range có thể do đâu?
12. max.poll.interval.ms khác session.timeout.ms thế nào?
13. max.poll.records tác động gì?
14. Pause có commit offset không?
15. Vì sao consumer vẫn phải poll khi partition bị pause?
16. Seek production cần lưu ý gì?
17. Vì sao commitAsync vẫn cần xử lý lỗi?
18. Khi nào dùng commitSync?
19. Consumer idempotency có thể dùng pattern nào?
20. Graceful shutdown cần bảo đảm điều gì?

### Đáp án ngắn

1. Lấy record, duy trì group progress và xử lý assignment/commit.
2. Xác định consumer group và namespace offset.
3. Mỗi group có offset độc lập.
4. Tối đa 3 consumer có assignment.
5. Một là vị trí session; một là vị trí group đã lưu.
6. Log end offset trừ offset đã xử lý/commit.
7. Crash sau commit có thể bỏ qua record.
8. Crash trước commit có thể duplicate.
9. Chúng là hai hệ thống state khác nhau.
10. Khi group chưa có offset hợp lệ hoặc offset bị mất.
11. Retention cleanup, reset hoặc metadata migration.
12. Một bên về processing/poll interval, một bên về heartbeat/liveness.
13. Số record và thời gian xử lý mỗi poll.
14. Không, chỉ ngăn fetch record trong thời gian pause.
15. Để giữ consumer sống và xử lý protocol.
16. Dùng group replay, giới hạn range và idempotency.
17. Commit có thể lỗi bất đồng bộ hoặc callback cũ hoàn thành muộn.
18. Boundary an toàn, shutdown hoặc khi cần xác nhận.
19. eventId unique, upsert, dedup table, idempotency key.
20. Dừng work mới, xử lý/ghi nhận phần an toàn, commit đúng offset và close.

## 27. Checklist hoàn thành Tuần 5

- [ ] Tôi viết được poll loop Java cơ bản.
- [ ] Tôi hiểu group.id và subscription.
- [ ] Tôi quan sát được partition assignment.
- [ ] Tôi phân biệt current position, committed offset và log end offset.
- [ ] Tôi tính và đọc được lag theo partition.
- [ ] Tôi hiểu auto commit và manual commit.
- [ ] Tôi chứng minh được rủi ro commit trước xử lý.
- [ ] Tôi chứng minh được duplicate khi commit sau xử lý.
- [ ] Tôi chọn được earliest/latest phù hợp.
- [ ] Tôi biết xử lý offset out of range.
- [ ] Tôi hiểu max.poll.interval.ms và max.poll.records.
- [ ] Tôi phân biệt session timeout với poll interval.
- [ ] Tôi dùng được pause/resume.
- [ ] Tôi seek/replay bằng group riêng.
- [ ] Tôi có commit strategy theo từng workload.
- [ ] Tôi có graceful shutdown và không commit mù toàn batch.
- [ ] Tôi hoàn thành lab kill consumer và quan sát lag.

## 28. Câu chốt cần nhớ

Consumer không chỉ là vòng lặp đọc message. Consumer là một tiến trình quản lý ba thứ cùng lúc: dữ liệu đang xử lý, vị trí offset và membership trong consumer group.

Năm ý cần nhớ:

1. Commit trước xử lý có thể làm mất record; commit sau xử lý có thể tạo duplicate.
2. Lag phải được đọc theo partition, không chỉ nhìn tổng.
3. group.id quyết định consumer nào chia tải và service nào đọc độc lập.
4. max.poll.interval.ms là giới hạn quan trọng khi business processing chậm.
5. Replay và at-least-once an toàn chỉ khi side effect có idempotency.


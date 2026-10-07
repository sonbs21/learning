# Tuần 4 — Java Producer

> Mục tiêu: viết được Java producer có key, serializer, callback, timeout, retry và idempotence; biết chọn cấu hình theo reliability hoặc throughput thay vì copy config mù.

## 1. Kết quả cần đạt

Sau bài học, bạn có thể:

- Tạo KafkaProducer bằng Java và cấu hình đúng bootstrap server.
- Tạo ProducerRecord có topic, key, value, timestamp và headers.
- Phân biệt key serializer với value serializer.
- Gửi record đồng bộ và bất đồng bộ.
- Dùng Callback để ghi partition, offset, timestamp và latency.
- Hiểu send, flush và close không có cùng ý nghĩa.
- Giải thích acks, retries, request timeout và delivery timeout.
- Tối ưu batching bằng batch.size và linger.ms.
- Chọn compression phù hợp.
- Hiểu max.in.flight.requests.per.connection và ordering.
- Bật idempotent producer và hiểu producer sequence.
- Phân loại retriable, fatal, timeout và serialization error.
- Viết producer OrderCreated có metric cơ bản và graceful shutdown.

## 2. Producer hoạt động thế nào?

Luồng khái niệm:

    Application
        |
        v
    ProducerRecord
        |
        v
    Serializer key/value
        |
        v
    Partitioner chọn partition
        |
        v
    Record accumulator / batch
        |
        v
    Sender thread
        |
        v
    Kafka broker leader
        |
        v
    Produce response: partition, offset, timestamp

Producer không nhất thiết gửi từng record ngay khi gọi send. Client có thể:

- serialize record;
- chọn partition;
- xếp record vào batch;
- nén batch;
- gửi request theo network thread;
- retry khi lỗi retriable;
- hoàn thành Future và Callback khi có response hoặc lỗi cuối cùng.

Vì vậy lệnh send trả về nhanh không đồng nghĩa broker đã xác nhận record đã lưu thành công.

## 3. Chuẩn bị project Java

### 3.1 Maven dependency

Dùng version đã pin theo repository/team. Không dùng version latest trong lab.

    <dependency>
      <groupId>org.apache.kafka</groupId>
      <artifactId>kafka-clients</artifactId>
      <version>PINNED_VERSION</version>
    </dependency>

Ví dụ package:

    com.example.kafka.producer

Các class nên tách nhỏ:

    producer/
      OrderCreated.java
      OrderCreatedSerializer.java
      OrderProducer.java
      ProducerConfigFactory.java
      ProducerMetrics.java

Ở giai đoạn học, có thể dùng StringSerializer và JSON string. Khi vào schema registry, chuyển sang serializer/schema rõ ràng.

### 3.2 Topic lab

Dùng topic đã tạo ở tuần 3:

    dev.orders.order-created.v1

Key là orderId. Mục tiêu là các event của cùng order được định tuyến nhất quán.

## 4. Producer properties quan trọng

### 4.1 bootstrap.servers

Địa chỉ broker dùng để bootstrap metadata:

    bootstrap.servers=localhost:9092

Có thể cấu hình nhiều địa chỉ:

    bootstrap.servers=broker-1:9092,broker-2:9092,broker-3:9092

Nhiều bootstrap address giúp client có nhiều điểm vào ban đầu. Nó không biến producer thành một hệ thống gửi broadcast đến mọi broker.

### 4.2 client.id

client.id giúp nhận diện producer trong log và metric:

    client.id=order-service-producer

Nên đặt giá trị có ý nghĩa, ví dụ gồm service, environment hoặc instance. Tránh đặt mọi producer cùng một client.id nếu muốn debug latency và error theo instance.

### 4.3 key.serializer và value.serializer

Producer phải biết cách chuyển key/value thành bytes:

    key.serializer=org.apache.kafka.common.serialization.StringSerializer
    value.serializer=org.apache.kafka.common.serialization.StringSerializer

Key serializer phải phù hợp với kiểu key. Value serializer phải phù hợp với kiểu value. Nếu serializer ném lỗi, record không được gửi lên broker.

### 4.4 request.timeout.ms

request.timeout.ms là thời gian client chờ response cho một request trước khi coi request đó thất bại hoặc cần xử lý retry theo policy.

Timeout này không nhất thiết là tổng thời gian một record được phép tồn tại trong producer. Tổng thời gian còn liên quan đến delivery.timeout.ms.

### 4.5 delivery.timeout.ms

delivery.timeout.ms là giới hạn tổng thời gian producer cố gắng giao record, bao gồm thời gian chờ, retry và các bước liên quan.

Cần bảo đảm quan hệ hợp lý giữa:

    delivery.timeout.ms >= request.timeout.ms + thời gian retry thực tế

Client có thể điều chỉnh giới hạn theo version, nhưng đừng đặt delivery timeout thấp đến mức retry không có ý nghĩa.

### 4.6 retries

retries là số lần producer thử lại lỗi retriable. Trong các client hiện đại, retry và idempotence liên quan chặt chẽ tới delivery semantics.

Không nên coi retries là “bật lên là không duplicate”. Retry sau timeout có thể xảy ra trong tình huống broker đã ghi nhưng response chưa về. Idempotence giúp xử lý loại rủi ro này trong phạm vi Kafka protocol.

## 5. ProducerRecord

### 5.1 Record value đơn giản

    ProducerRecord<String, String> record =
        new ProducerRecord<>(
            "dev.orders.order-created.v1",
            "ORD-1001",
            "{\"orderId\":\"ORD-1001\",\"status\":\"CREATED\"}"
        );

Ở đây:

- String đầu tiên là topic;
- String thứ hai là key;
- String thứ ba là value.

### 5.2 Constructor đầy đủ hơn

    ProducerRecord<String, String> record =
        new ProducerRecord<>(
            "dev.orders.order-created.v1",
            null,
            System.currentTimeMillis(),
            "ORD-1001",
            payload,
            headers
        );

Timestamp có thể do application cung cấp. Nếu không cung cấp, producer/broker sẽ áp dụng hành vi mặc định theo cấu hình và version.

### 5.3 Headers

Headers phù hợp cho metadata phụ:

    record.headers().add(
        "correlation-id",
        correlationId.getBytes(StandardCharsets.UTF_8)
    );

Có thể dùng headers cho:

- correlation id;
- trace id;
- source service;
- event type;
- schema version;
- retry count.

Không nhét payload nghiệp vụ quan trọng vào headers nếu consumer cần schema rõ ràng và kiểm soát compatibility. Headers không thay thế value schema.

### 5.4 Key và ordering

Với OrderCreated, dùng orderId làm key:

    ProducerRecord<String, String> record =
        new ProducerRecord<>(topic, orderId, jsonPayload);

Key giúp partitioner đưa event liên quan tới cùng order vào cùng partition trong điều kiện số partition và partitioner phù hợp.

Không dùng customerId nếu yêu cầu ordering thực sự là theo orderId và một customer có nhiều order song song. Key phải phản ánh boundary cần serialize.

## 6. Serializer

### 6.1 StringSerializer

Phù hợp cho lab đơn giản:

    Properties props = new Properties();
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
        StringSerializer.class.getName());
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
        StringSerializer.class.getName());

Ưu điểm:

- nhanh để thử;
- dễ nhìn bằng console consumer;
- ít code.

Nhược điểm:

- không có schema enforcement;
- dễ tạo JSON không nhất quán;
- lỗi chỉ xuất hiện lúc runtime;
- khó kiểm soát evolution.

### 6.2 JSON serializer tự viết

Với object OrderCreated, serializer cần:

1. nhận object;
2. validate field bắt buộc;
3. chuyển thành bytes;
4. ném SerializationException nếu không thể serialize.

Không nên serialize object tùy tiện bằng Java native serialization cho event nghiệp vụ. Payload cần format ổn định, dễ quan sát và tương thích đa ngôn ngữ.

### 6.3 Validation trước serialize

Kiểm tra ở boundary:

- orderId không rỗng;
- totalAmount không âm;
- currency hợp lệ;
- occurredAt tồn tại;
- event version hợp lệ;
- items không rỗng nếu nghiệp vụ yêu cầu.

Nếu record không hợp lệ, fail fast trước khi gọi send. Đừng gửi payload lỗi rồi trông chờ consumer tự đoán.

### 6.4 Serialization error khác broker error

Serialization error xảy ra trước khi request hợp lệ được gửi tới broker. Retry broker không sửa được payload không serialize được.

Cách xử lý:

- log record identifier;
- đưa input lỗi vào error path phù hợp;
- không retry vô hạn;
- sửa dữ liệu hoặc code serializer;
- kiểm tra schema/version.

## 7. Gửi record: sync, async và flush

### 7.1 Async send

Cách dùng thông thường:

    producer.send(record, (metadata, exception) -> {
        if (exception != null) {
            log.error("Publish failed", exception);
            return;
        }

        log.info(
            "Published topic={}, partition={}, offset={}",
            metadata.topic(),
            metadata.partition(),
            metadata.offset()
        );
    });

send trả về ngay sau khi record được nhận vào buffer của producer, không phải sau khi broker xác nhận thành công.

### 7.2 Future

Có thể lấy Future để chờ kết quả:

    Future<RecordMetadata> future = producer.send(record);
    RecordMetadata metadata = future.get();

Cách này chuyển flow thành synchronous tại điểm get. Phù hợp khi cần kết quả trước khi tiếp tục, nhưng nếu chờ từng record sẽ giảm throughput.

### 7.3 Flush

flush yêu cầu client đẩy các record đang buffer và chờ request hoàn tất trong phạm vi cấu hình:

    producer.flush();

Dùng flush khi:

- trước shutdown;
- trong test;
- tại boundary batch rõ ràng;
- cần chắc chắn các record đã được gửi trước khi tiếp tục.

Không gọi flush sau mọi send trong production nếu muốn tận dụng batching, vì có thể làm mất hiệu quả batch.

### 7.4 Close

Luôn đóng producer:

    producer.close(Duration.ofSeconds(10));

close cho producer cơ hội flush và giải phóng network resources. Timeout giúp shutdown không treo vô hạn.

### 7.5 Graceful shutdown

Một pattern cơ bản:

    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
        producer.flush();
        producer.close(Duration.ofSeconds(10));
    }));

Trong application framework, cần bảo đảm producer được đóng một lần và không có thread khác tiếp tục gọi send sau khi close.

## 8. Callback và metadata

Callback thành công nên ghi tối thiểu:

- topic;
- partition;
- offset;
- timestamp;
- eventId/correlationId;
- latency;
- clientId nếu cần.

Ví dụ đo latency:

    long startNanos = System.nanoTime();

    producer.send(record, (metadata, exception) -> {
        long latencyMicros =
            (System.nanoTime() - startNanos) / 1_000;

        if (exception != null) {
            errorCounter.increment();
            log.error("send failed latencyMicros={}", latencyMicros, exception);
        } else {
            successCounter.increment();
            log.info(
                "send success partition={} offset={} latencyMicros={}",
                metadata.partition(),
                metadata.offset(),
                latencyMicros
            );
        }
    });

Không log toàn bộ payload nhạy cảm trong callback. Log eventId và metadata cần thiết để debug.

## 9. acks và durability

### 9.1 acks=0

Producer không chờ broker xác nhận.

Trade-off:

- latency thấp;
- throughput có thể cao;
- khó biết record có đến broker không;
- có thể mất record khi network hoặc broker lỗi.

Chỉ phù hợp dữ liệu có thể mất hoặc use case telemetry không quan trọng.

### 9.2 acks=1

Leader xác nhận sau khi append local. Follower có thể chưa replicate xong.

Trade-off:

- cân bằng giữa latency và durability;
- leader failure ngay sau response có thể làm record chưa replicate bị mất tùy tình huống.

### 9.3 acks=all

Leader chỉ xác nhận khi các replica trong ISR đáp ứng điều kiện phù hợp.

Thường kết hợp với:

    acks=all
    enable.idempotence=true
    replication.factor >= 3
    min.insync.replicas >= 2

Cấu hình cụ thể phụ thuộc workload và failure budget. acks=all không có nghĩa dữ liệu đã được ghi vào mọi replica trong assignment nếu replica đó không nằm trong ISR.

## 10. Retry và timeout

### 10.1 Retriable error

Có thể thử lại:

- transient network error;
- broker not available tạm thời;
- leader election;
- request timeout trong một số tình huống;
- not enough replicas trong tình trạng tạm thời.

Retry chỉ có ích nếu lỗi có khả năng biến mất. Cần giới hạn bằng delivery.timeout.ms.

### 10.2 Fatal error

Thường không nên retry mù:

- authentication/authorization bị từ chối;
- invalid topic hoặc request;
- config không hợp lệ;
- serialization logic lỗi;
- message quá lớn vượt giới hạn.

Hãy log nguyên nhân và đưa ra hành động vận hành rõ ràng.

### 10.3 Timeout không chắc là record chưa được ghi

Đây là điểm quan trọng:

1. Producer gửi request.
2. Broker append record.
3. Network response bị mất.
4. Producer thấy timeout.
5. Producer retry.

Broker có thể nhận record hai lần nếu không có cơ chế idempotence phù hợp. Vì vậy timeout là trạng thái không chắc chắn, không phải bằng chứng record chưa tồn tại.

### 10.4 Delivery timeout

Nếu retries quá nhiều hoặc broker quá chậm, delivery timeout kết thúc vòng đời record. Callback nhận exception cuối cùng.

Application cần:

- đếm failed sends;
- ghi eventId;
- quyết định retry ở tầng nghiệp vụ hay đưa vào outbox/error store;
- tránh retry vô hạn trong cả producer lẫn application.

## 11. Batching

### 11.1 batch.size

batch.size là kích thước mục tiêu của batch cho mỗi partition. Đây không phải giới hạn cứng tổng payload của producer.

Batch chỉ hiệu quả khi có đủ record cùng partition trước khi gửi.

### 11.2 linger.ms

linger.ms cho producer chờ thêm một khoảng ngắn để gom record vào batch.

- thấp: latency thấp, batch nhỏ;
- cao hơn: batch lớn hơn, throughput tốt hơn nhưng thêm chờ.

Không có giá trị tối ưu chung. Phải đo bằng workload thật.

### 11.3 Batching phụ thuộc partition

Producer tạo batch theo partition. Nếu key phân bố quá nhiều partition và traffic mỗi partition thấp, mỗi batch có thể nhỏ dù tổng traffic lớn.

Đây là lý do topic design và producer tuning liên quan với nhau.

### 11.4 Trade-off

| Mục tiêu | batch.size | linger.ms | Tác động |
|---|---:|---:|---|
| Latency thấp | nhỏ/vừa | thấp | phản hồi nhanh, ít batching |
| Throughput cao | lớn | vừa | nén và network hiệu quả hơn |
| Traffic thấp | vừa | thấp/vừa | tránh chờ lâu |
| Record lớn | phù hợp payload | tùy | cần xét max.request.size |

Không tăng batch.size vô hạn. Record lớn hoặc batch lớn có thể làm tăng memory pressure.

## 12. Compression

Các lựa chọn thường gặp:

- none;
- gzip;
- snappy;
- lz4;
- zstd.

### 12.1 None

- CPU thấp;
- network và disk cao hơn;
- phù hợp payload đã nén hoặc workload cần tối thiểu CPU.

### 12.2 gzip

- compression ratio tốt;
- CPU cao hơn;
- có thể tăng latency/CPU.

### 12.3 snappy

- nhanh;
- compression ratio vừa;
- phù hợp workload cần cân bằng.

### 12.4 lz4

- nhanh;
- thường phù hợp throughput và latency;
- cần benchmark với dữ liệu thật.

### 12.5 zstd

- compression ratio tốt;
- có thể tiết kiệm network/disk;
- CPU và version compatibility cần được kiểm tra.

### 12.6 Cách benchmark

Không benchmark bằng một payload lặp đơn giản rồi kết luận cho production. Dùng:

- JSON thật;
- kích thước record đại diện;
- tỷ lệ field lặp;
- throughput gần production;
- CPU và network có giới hạn;
- nhiều mức batch/linger.

Metric cần ghi:

- records/s;
- MB/s trước và sau nén;
- p50/p95/p99 latency;
- CPU;
- network bytes;
- error rate.

## 13. max.in.flight.requests.per.connection và ordering

max.in.flight.requests.per.connection giới hạn số request chưa nhận response trên một connection.

Nếu có nhiều request in-flight và retry, thứ tự có thể phức tạp hơn khi một request trước lỗi còn request sau đã thành công.

Với idempotent producer hiện đại, client có cơ chế sequence để bảo vệ ordering/duplicate trong phạm vi protocol. Tuy nhiên không nên coi mọi cấu hình concurrency đều vô hại.

Nếu ordering theo key là yêu cầu quan trọng:

- dùng cùng key;
- không tạo nhiều producer cạnh tranh cùng aggregate nếu không cần;
- bật idempotence;
- hiểu cấu hình max.in.flight theo version;
- xử lý callback và lỗi có thứ tự;
- đừng gửi retry nghiệp vụ thủ công mà không có eventId/sequence.

## 14. Idempotent producer

### 14.1 Vấn đề producer duplicate

Producer có thể gửi lại do:

- response bị mất;
- timeout;
- leader change;
- connection reset;
- retry transient error.

Nếu broker append được request trước khi producer biết kết quả, retry có thể tạo duplicate.

### 14.2 enable.idempotence

Idempotent producer dùng producer identity và sequence để broker nhận diện request hợp lệ, loại bỏ một số duplicate do retry trong cùng producer session.

Cấu hình thường dùng:

    enable.idempotence=true
    acks=all
    retries=some-positive-value

Client có thể tự điều chỉnh một số property liên quan để giữ semantics. Không trộn các config xung đột rồi đoán kết quả.

### 14.3 Giới hạn của idempotence

Idempotence producer không giải quyết:

- producer restart trong mọi tình huống nghiệp vụ;
- application gửi cùng event hai lần với logic mới;
- duplicate do replay thủ công;
- side effect ở consumer;
- duplicate qua nhiều producer;
- lỗi thiết kế eventId.

Vẫn nên có eventId ổn định và consumer idempotency.

### 14.4 Producer sequence

Mỗi producer/partition có sequence giúp broker phát hiện request trùng hoặc out-of-order trong protocol. Sequence là chi tiết vận hành của producer session, không phải business event version.

Không dùng producer sequence để thay thế eventId hoặc ordering nghiệp vụ.

## 15. OrderCreated model và producer mẫu

### 15.1 Model bất biến

Dùng record Java để tránh mutation:

    public record OrderCreated(
        String eventId,
        String orderId,
        String customerId,
        long totalAmount,
        String currency,
        Instant occurredAt
    ) {}

Validation có thể đặt trong factory hoặc constructor compact, tùy project. Không cho producer gửi object đang bị thread khác thay đổi.

### 15.2 Producer cấu hình reliability

    public final class OrderProducer implements AutoCloseable {
        private final KafkaProducer<String, String> producer;
        private final String topic;

        public OrderProducer(Properties props, String topic) {
            this.producer = new KafkaProducer<>(props);
            this.topic = topic;
        }

        public void send(String orderId, String payload, String eventId) {
            ProducerRecord<String, String> record =
                new ProducerRecord<>(topic, orderId, payload);

            record.headers().add(
                "event-id",
                eventId.getBytes(StandardCharsets.UTF_8)
            );

            long start = System.nanoTime();

            producer.send(record, (metadata, exception) -> {
                long latencyMicros =
                    (System.nanoTime() - start) / 1_000;

                if (exception != null) {
                    System.err.println(
                        "publish failed eventId=" + eventId
                        + " latencyMicros=" + latencyMicros
                        + " error=" + exception.getClass().getSimpleName()
                    );
                    return;
                }

                System.out.println(
                    "publish success eventId=" + eventId
                    + " partition=" + metadata.partition()
                    + " offset=" + metadata.offset()
                    + " latencyMicros=" + latencyMicros
                );
            });
        }

        @Override
        public void close() {
            producer.close(Duration.ofSeconds(10));
        }
    }

Đây là skeleton học tập. Production cần structured logging, metrics, validation, error routing và test.

### 15.3 Properties reliability

    Properties props = new Properties();
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
    props.put(ProducerConfig.CLIENT_ID_CONFIG, "order-service-producer");
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
        StringSerializer.class.getName());
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
        StringSerializer.class.getName());
    props.put(ProducerConfig.ACKS_CONFIG, "all");
    props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true");
    props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "zstd");
    props.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, "120000");

Cấu hình này cần được kiểm tra tương thích với broker và mục tiêu latency. Không copy trực tiếp vào production mà không benchmark.

### 15.4 Properties throughput

    props.put(ProducerConfig.ACKS_CONFIG, "all");
    props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true");
    props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "lz4");
    props.put(ProducerConfig.BATCH_SIZE_CONFIG, 64 * 1024);
    props.put(ProducerConfig.LINGER_MS_CONFIG, 5);
    props.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 64 * 1024 * 1024);

Throughput profile vẫn cần reliability. Không nên tắt idempotence chỉ để benchmark nhanh hơn nếu production requirement không cho phép duplicate.

## 16. Phân loại lỗi trong application

| Nhóm lỗi | Ví dụ | Retry mù? | Hướng xử lý |
|---|---|---|---|
| Retriable | leader change, network tạm thời | Có giới hạn | Chờ producer retry, metric |
| Fatal broker/config | auth denied, invalid config | Không | Sửa config/quyền |
| Timeout | không rõ broker đã ghi chưa | Có kiểm soát | Idempotence, eventId, quan sát |
| Serialization | object không encode được | Không | Sửa payload/schema |
| Record too large | vượt max request/message | Không | Giảm payload hoặc tăng limit có kiểm soát |
| Application validation | thiếu orderId | Không | Reject trước send |
| Shutdown | producer đang close | Không | Lifecycle đúng |

### 16.1 Error handler không được nuốt lỗi

Sai:

    producer.send(record, (metadata, exception) -> {
        // bỏ qua exception
    });

Đúng hơn:

- increment error metric;
- log eventId và nguyên nhân;
- phân loại exception;
- ghi error record/outbox nếu cần;
- thông báo health/alert;
- không tiếp tục coi event là published.

### 16.2 Không retry vô hạn ở nhiều tầng

Nếu producer retry 2 phút, application retry 10 lần và upstream HTTP retry 3 lần, một event có thể tạo nhiều request và kéo dài sự cố.

Cần xác định:

- tầng nào retry;
- tổng deadline;
- số lần retry;
- backoff;
- điều kiện dừng;
- nơi lưu event không gửi được.

## 17. Metrics và observability

Tối thiểu cần có:

- records sent;
- records acknowledged;
- send error count;
- serialization error count;
- retry count;
- request latency;
- record queue time;
- batch size;
- compression rate;
- buffer exhaustion;
- bytes sent;
- records per second.

Application metrics:

    producer_send_total
    producer_send_error_total
    producer_send_latency_seconds
    producer_records_total
    producer_retry_total

Kafka client JMX metrics nên được bật/thu thập theo tiêu chuẩn của team. Không cần log mọi record ở production; dùng sample hoặc log eventId theo error path.

## 18. Bài lab 1 — Producer gửi OrderCreated

Yêu cầu:

1. Tạo topic dev.orders.order-created.v1.
2. Viết Java producer dùng StringSerializer.
3. Mỗi record có key orderId.
4. Payload có eventId, orderId, customerId, amount và occurredAt.
5. Gửi ít nhất 20 record.
6. Callback ghi topic, partition, offset và latency.
7. Consumer CLI in key, partition và offset để kiểm tra.

Tiêu chí:

- cùng orderId đi cùng partition trong điều kiện topic chưa đổi partition count;
- callback báo lỗi rõ nếu broker tắt;
- shutdown đóng producer đúng cách;
- không coi hàm send return là publish success.

## 19. Bài lab 2 — Sync, async và flush

Tạo ba mode:

1. async callback;
2. sync với future.get;
3. async rồi flush cuối batch.

Đo:

- tổng thời gian;
- records/s;
- p50/p95 latency;
- số callback thành công;
- memory/buffer behavior.

Giải thích:

- vì sao future.get từng record thường chậm hơn async;
- vì sao flush sau mỗi record làm giảm batching;
- vì sao không flush trước shutdown có thể làm mất các record đang buffer khi process dừng.

## 20. Bài lab 3 — Serialization error và broker unavailable

### Serialization error

Tạo payload không hợp lệ hoặc serializer cố tình ném exception. Ghi nhận:

- exception xảy ra ở thread nào;
- callback có được gọi không;
- record có tới broker không;
- application có tiếp tục gửi record hợp lệ không.

### Broker unavailable

1. Chạy producer.
2. Dừng Kafka container.
3. Gửi record.
4. Quan sát log retry/timeout.
5. Khởi động broker lại.
6. Kiểm tra record nào được ack.
7. Kiểm tra error callback khi delivery timeout hết hạn.

Không kết luận record không tồn tại chỉ vì producer thấy timeout. Hãy dùng consumer/offset để xác minh.

## 21. Bài lab 4 — Benchmark batch và compression

Chạy ma trận tối thiểu:

| Profile | batch.size | linger.ms | compression |
|---|---:|---:|---|
| baseline | mặc định | mặc định | none |
| batch nhỏ | 16 KB | 0 | none |
| batch vừa | 64 KB | 5 | lz4 |
| compression | 64 KB | 5 | zstd |
| low latency | 16 KB | 0 | lz4 |

Với mỗi profile, ghi:

- tổng records;
- thời gian;
- records/s;
- MB/s;
- p50/p95/p99;
- CPU;
- network bytes;
- error count.

Kết luận phải dựa trên payload và môi trường cụ thể, không chỉ trên tên compression.

## 22. Bài lab 5 — Idempotence và retry

1. Bật enable.idempotence=true.
2. Dùng acks=all.
3. Tạo điều kiện network/broker chập chờn trong lab.
4. Gửi record có eventId.
5. Kiểm tra consumer và đếm eventId.
6. So sánh profile idempotence bật/tắt.
7. Ghi rõ duplicate nào Kafka xử lý được và duplicate nào vẫn cần consumer dedup.

Nhớ rằng test local không tái hiện mọi failure mode production. Kết quả lab chỉ chứng minh hành vi trong kịch bản đã thử.

## 23. Producer reliability profile và throughput profile

### Reliability profile

Dùng khi mất event nguy hiểm:

- acks=all;
- enable.idempotence=true;
- retries có giới hạn;
- delivery.timeout.ms đủ rộng;
- compression phù hợp;
- eventId ổn định;
- callback/metric bắt buộc;
- graceful shutdown;
- downstream có idempotency.

### Throughput profile

Dùng khi cần tối ưu tốc độ:

- batch.size tăng vừa phải;
- linger.ms nhỏ nhưng > 0 nếu latency cho phép;
- compression lz4/zstd sau benchmark;
- buffer.memory đủ;
- key phân bố đều;
- số partition đủ;
- theo dõi CPU và network.

Throughput profile không đồng nghĩa bỏ reliability. Mục tiêu đúng là đạt throughput cần thiết trong ranh giới mất dữ liệu và duplicate được chấp nhận.

## 24. Sai lầm phổ biến

### Coi send return là thành công

send chỉ cho biết record được nhận vào producer path. Hãy kiểm tra Callback hoặc Future.

### Gọi flush sau từng record

Điều này phá batching và làm latency/throughput xấu đi.

### Retry mọi exception

Serialization, auth và validation không tự khỏi bằng retry.

### Dùng timeout quá ngắn

Timeout ngắn tạo retry storm và false failure khi broker đang chậm tạm thời.

### Không có eventId

Khi timeout hoặc replay, không có ID khiến dedup và điều tra khó.

### Dùng orderId sai mục đích

Nếu cần ordering theo account mà dùng orderId, các event account có thể đi nhiều partition.

### Tắt idempotence để “cho nhanh”

Điều này có thể biến retry thành duplicate trong workload quan trọng.

### Log cả payload nhạy cảm

Producer log nên ưu tiên eventId, key, topic, partition, offset và error class.

### Đóng producer không graceful

Các record đang buffer có thể chưa được gửi hoặc callback chưa hoàn tất.

## 25. Câu hỏi tự kiểm tra

1. ProducerRecord chứa những thành phần nào?
2. Key serializer và value serializer có vai trò gì?
3. Vì sao send async chưa phải publish success?
4. Callback cần ghi metadata nào?
5. flush khác close thế nào?
6. acks=0, 1 và all khác nhau ra sao?
7. request.timeout.ms khác delivery.timeout.ms thế nào?
8. Vì sao timeout không chứng minh record chưa được ghi?
9. batch.size và linger.ms tác động gì?
10. Vì sao batching phụ thuộc partition?
11. Compression đánh đổi CPU, network và latency thế nào?
12. max.in.flight ảnh hưởng retry và ordering ra sao?
13. Idempotent producer giải quyết loại duplicate nào?
14. Idempotence không giải quyết được những duplicate nào?
15. Serialization error nên retry không?
16. Vì sao cần eventId ngay cả khi bật idempotence?
17. Tại sao không nên retry vô hạn ở nhiều tầng?
18. Reliability profile và throughput profile khác nhau thế nào?

### Đáp án ngắn

1. Topic, partition tùy chọn, timestamp, key, value và headers.
2. Chuyển key/value thành bytes để gửi broker.
3. send có thể mới xếp record vào buffer.
4. Topic, partition, offset, timestamp, eventId và latency.
5. flush đẩy/chờ buffer; close flush và giải phóng resource.
6. Từ không chờ xác nhận, chờ leader, đến xác nhận theo ISR.
7. Một request so với tổng vòng đời giao record.
8. Response có thể mất sau khi broker đã append.
9. Kích thước batch và thời gian gom record.
10. Batch được tạo theo từng partition.
11. Nén giảm network/disk nhưng dùng CPU và có thể đổi latency.
12. Nhiều request in-flight làm retry/order phức tạp hơn.
13. Một số duplicate do retry trong cùng producer session.
14. Replay, nhiều producer, application gửi lại và side effect consumer.
15. Không, cần sửa payload/schema.
16. Để dedup và truy vết ở nghiệp vụ.
17. Có thể tạo retry storm và kéo dài sự cố.
18. Một bên ưu tiên durability/quan sát; một bên tối ưu throughput trong giới hạn an toàn.

## 26. Checklist hoàn thành Tuần 4

- [ ] Tôi tạo được KafkaProducer bằng Java.
- [ ] Tôi dùng được ProducerRecord có key và headers.
- [ ] Tôi cấu hình đúng serializer.
- [ ] Tôi phân biệt send, Future, flush và close.
- [ ] Tôi ghi được topic, partition, offset và timestamp trong callback.
- [ ] Tôi hiểu acks=0, 1 và all.
- [ ] Tôi phân biệt request timeout với delivery timeout.
- [ ] Tôi hiểu retries và timeout không đồng nghĩa không duplicate.
- [ ] Tôi đo được tác động của batch.size và linger.ms.
- [ ] Tôi benchmark được ít nhất hai loại compression.
- [ ] Tôi hiểu max.in.flight và ordering.
- [ ] Tôi bật và kiểm tra idempotent producer.
- [ ] Tôi phân loại được retriable, fatal, timeout và serialization error.
- [ ] Tôi có metric send latency, error rate và records per second.
- [ ] Tôi có reliability profile và throughput profile riêng.
- [ ] Tôi có graceful shutdown cho producer.

## 27. Câu chốt cần nhớ

Java producer không chỉ là gọi producer.send. Một producer production-ready phải trả lời được bốn câu hỏi:

1. Record được serialize và định tuyến vào partition nào?
2. Khi broker chậm hoặc network lỗi, record được retry trong bao lâu?
3. Nếu response mất sau khi broker đã ghi, duplicate được kiểm soát thế nào?
4. Làm sao biết publish thành công, chậm, lỗi hoặc đang mất dữ liệu?

Nếu không có callback/metric, timeout budget, idempotence và shutdown rõ ràng, producer có thể trông như đang chạy nhưng vẫn âm thầm mất record hoặc tạo duplicate.


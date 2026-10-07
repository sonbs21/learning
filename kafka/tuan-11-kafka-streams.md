# Tuần 11 — Kafka Streams

> Mục tiêu: xây topology xử lý stream/table, biết khi nào stateful operation tạo state store, repartition/changelog topic và cách test topology trước khi chạy cluster thật.

## 1. Kết quả cần đạt

Sau bài học, bạn có thể:

- Phân biệt event stream, KStream, KTable và GlobalKTable.
- Chọn stateless hoặc stateful operation phù hợp.
- Dùng filter, map, branch, transform, groupBy, aggregate, reduce và count.
- Hiểu tumbling, hopping, sliding và session window.
- Thiết kế stream-stream, stream-table và table-table join.
- Cấu hình Serde đúng cho key/value/state.
- Hiểu state store, changelog topic và restore.
- Nhận diện khi topology cần repartition.
- Quan sát topology bằng describe và test bằng TopologyTestDriver.
- Hiểu exactly-once processing trong Kafka Streams.
- Nhận diện hot key và chi phí state/disk/recovery.

## 2. Kafka Streams là gì?

Kafka Streams là thư viện Java để xây ứng dụng xử lý dữ liệu trực tiếp từ Kafka topic.

Mô hình:

    input topic
         |
         v
    Kafka Streams topology
         |
         +--> output topic
         +--> state store
         +--> changelog topic
         +--> repartition topic
         |
         v
    downstream topic/application

Kafka Streams không phải một cluster riêng như Kafka broker. Một Streams application chạy bằng các process/instance bình thường, mỗi instance có một hoặc nhiều stream task.

### 2.1 Topology

Topology là đồ thị các processor:

    source -> filter -> map -> groupBy -> aggregate -> sink

Trong topology có thể có:

- source node đọc topic;
- processor node biến đổi;
- state store giữ state;
- repartition node đổi partitioning;
- sink node ghi output topic.

Topology thường được xây lúc startup. Record được xử lý khi application chạy.

### 2.2 Stream task

Streams chia workload theo partition. Mỗi task xử lý một hoặc nhiều input partition.

Nếu input topic có 6 partition và Streams application có 3 instance, các task được phân phối giữa instance. Tăng instance chỉ giúp song song trong giới hạn partition và topology.

## 3. Stream và table

### 3.1 Event stream

Event stream là chuỗi sự kiện:

    CustomerEmailChanged(CUS-1, a@example.com)
    CustomerEmailChanged(CUS-1, b@example.com)
    CustomerEmailChanged(CUS-1, c@example.com)

Mỗi record là một event độc lập trong lịch sử.

### 3.2 Table

Table biểu diễn trạng thái mới nhất theo key:

    CUS-1 -> c@example.com

Các event sau cập nhật cùng key sẽ thay thế giá trị trước trong table view.

### 3.3 KStream

KStream đại diện cho stream record. Mỗi record đi qua topology:

    KStream<String, OrderCreated> orders

Nếu có ba event cùng key, KStream thấy cả ba.

### 3.4 KTable

KTable đại diện cho changelog của trạng thái theo key:

    KTable<String, Customer> customers

Nếu key customer-1 xuất hiện nhiều lần, giá trị mới cập nhật trạng thái hiện tại. KTable thường được xây từ compacted topic hoặc aggregation.

### 3.5 GlobalKTable

GlobalKTable được materialize đầy đủ trên mỗi Streams instance:

    GlobalKTable<String, Customer> customerTable

Ưu điểm:

- join không cần repartition theo key của table;
- lookup local nhanh;
- phù hợp table nhỏ/vừa và cần đọc toàn bộ.

Nhược điểm:

- mỗi instance giữ toàn bộ table;
- memory/disk/network tăng theo số instance;
- không phù hợp table lớn.

### 3.6 So sánh

| Khái niệm | Ý nghĩa | Record cùng key |
|---|---|---|
| KStream | Event history | Giữ tất cả event |
| KTable | State mới nhất | Update value |
| GlobalKTable | State đầy đủ trên mỗi instance | Lookup local |
| Compacted topic | Changelog/state source | Giữ latest theo key sau compaction |

Đừng gọi mọi topic là table. Topic event history và topic state-by-key có semantics khác nhau.

## 4. Serde

Serde là serializer + deserializer dùng trong Kafka Streams.

Topology cần Serde cho:

- input key/value;
- output key/value;
- repartition topic;
- state store;
- changelog topic;
- join key/value;
- window store.

Ví dụ:

    Serdes.String()
    Serdes.Long()
    SpecificAvroSerde<OrderCreated>

### 4.1 Default Serde

Cấu hình default:

    Properties props = new Properties();
    props.put(
        StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG,
        Serdes.String().getClass()
    );
    props.put(
        StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG,
        Serdes.String().getClass()
    );

Default tiện cho topology đơn giản. Với nhiều loại value, chỉ định Serde tại source/operator/sink để tránh lỗi khó đọc.

### 4.2 Serde mismatch

Lỗi thường gặp:

- input JSON nhưng dùng StringSerde cho object;
- key đổi từ String sang Long;
- state store không serialize được class;
- join hai stream có value serde không tương thích;
- schema registry unavailable;
- nullable/tombstone không được xử lý.

Serde error có thể làm task fail. Hãy test payload representative.

## 5. Stateless operations

Stateless operation không cần nhớ record trước để xử lý record hiện tại.

### 5.1 filter

    KStream<String, OrderCreated> validOrders =
        orders.filter(
            (orderId, event) ->
                event.totalAmount() > 0
        );

Dùng để bỏ record không hợp lệ hoặc route theo điều kiện.

### 5.2 mapValues

Đổi value, giữ key:

    KStream<String, OrderSummary> summaries =
        orders.mapValues(event ->
            new OrderSummary(
                event.orderId(),
                event.totalAmount()
            )
        );

Phù hợp khi key vẫn đúng boundary.

### 5.3 map

Đổi cả key và value:

    KStream<String, CustomerOrder> byCustomer =
        orders.map((orderId, event) ->
            KeyValue.pair(
                event.customerId(),
                new CustomerOrder(event.orderId(), event.totalAmount())
            )
        );

Sau khi đổi key, nếu có aggregation/join phụ thuộc key mới, có thể cần repartition.

### 5.4 selectKey

    KStream<String, OrderCreated> byCustomer =
        orders.selectKey(
            (orderId, event) -> event.customerId()
        );

selectKey chỉ đổi key trong record hiện tại. Nó không tự đảm bảo topic đã được repartition trước mọi downstream stateful operation.

### 5.5 branch

    Map<String, KStream<String, OrderCreated>> branches =
        orders.split(Named.as("orders-"))
            .branch(
                (key, event) -> event.totalAmount() >= 1_000_000,
                Branched.as("high-value")
            )
            .defaultBranch(Branched.as("standard"));

Dùng branch để route high-value, standard, invalid hoặc domain-specific.

### 5.6 transform

transform cho phép logic tùy chỉnh, có thể truy cập ProcessorContext và state store nếu cấu hình phù hợp. Không dùng transform chỉ vì map/filter chưa quen; giữ topology đơn giản khi có thể.

## 6. Stateful operations

Stateful operation cần nhớ dữ liệu trước:

- count;
- reduce;
- aggregate;
- join;
- window;
- dedup;
- sessionization.

State thường được giữ trong local state store và backup bằng changelog topic.

### 6.1 groupByKey

Dùng khi key hiện tại đã đúng:

    KGroupedStream<String, OrderCreated> grouped =
        orders.groupByKey();

### 6.2 groupBy

Dùng khi cần đổi key và Serde:

    KGroupedStream<String, OrderCreated> byCustomer =
        orders.groupBy(
            (orderId, event) -> event.customerId(),
            Grouped.with(
                Serdes.String(),
                orderCreatedSerde
            )
        );

groupBy thường tạo repartition nếu key mới khác key hiện tại.

### 6.3 count

    KTable<String, Long> orderCountByCustomer =
        byCustomer.count(
            Materialized
                .<String, Long, KeyValueStore<Bytes, byte[]>>as(
                    "order-count-by-customer"
                )
                .withKeySerde(Serdes.String())
                .withValueSerde(Serdes.Long())
        );

Count phù hợp khi chỉ cần số lượng, không cần giữ aggregate phức tạp.

### 6.4 reduce

    KTable<String, OrderCreated> latestOrder =
        byCustomer.reduce(
            (oldValue, newValue) -> newValue
        );

Reduce cần hai value cùng type và hàm kết hợp trả về cùng type.

### 6.5 aggregate

    KTable<String, CustomerStats> stats =
        byCustomer.aggregate(
            CustomerStats::empty,
            (customerId, event, current) ->
                current.add(event),
            Materialized.with(
                Serdes.String(),
                customerStatsSerde
            )
        );

Aggregate phù hợp khi output type khác input type. Hàm aggregate nên được thiết kế deterministic và dễ test.

## 7. State store

State store là storage local cho topology:

    key -> state

Có thể dùng:

- KeyValueStore;
- WindowStore;
- SessionStore;
- persistent store;
- in-memory store cho use case nhỏ/test.

State store phục vụ:

- aggregation;
- join;
- dedup;
- window;
- materialized query;
- lookup trong topology.

### 7.1 Local state không phải source of truth duy nhất

Nếu local state mất:

    local state deleted
        |
        v
    restore từ changelog topic
        |
        v
    topology tiếp tục

Vì vậy cần:

- changelog enabled phù hợp;
- disk bền vững;
- state directory không bị xóa tùy tiện;
- restore time monitored;
- changelog retention đủ;
- schema/Serde tương thích.

### 7.2 Materialized state

    Materialized
        .as("customer-order-counts")
        .withKeySerde(Serdes.String())
        .withValueSerde(customerStatsSerde)

Tên store nên ổn định nếu cần query/read-only API. Không đổi store name tùy tiện vì có thể tạo state mới và restore lại toàn bộ.

### 7.3 Interactive query

Một materialized store có thể được query từ application, nhưng cần:

- biết instance nào sở hữu key;
- metadata/routing;
- consistency expectation;
- restore status;
- handling khi instance down.

Interactive query không tự biến local state thành một database phân tán mạnh như RDBMS.

## 8. Windowing

Window giới hạn state theo thời gian.

### 8.1 Tumbling window

Các cửa sổ không chồng lấn:

    10:00-10:05
    10:05-10:10
    10:10-10:15

Mỗi event thuộc một window. Phù hợp đếm order mỗi 5 phút.

    TimeWindows.ofSizeWithNoGrace(Duration.ofMinutes(5))

Cần cân nhắc late event và grace period theo version API.

### 8.2 Hopping window

Cửa sổ có overlap và tiến theo hop:

    window size = 10 phút
    advance     = 5 phút

    10:00-10:10
    10:05-10:15
    10:10-10:20

Một event có thể thuộc nhiều window. Hữu ích moving metrics, nhưng state và output lớn hơn.

### 8.3 Sliding window

Sliding window dựa trên khoảng cách giữa event trong stream. Phù hợp phát hiện event gần nhau theo thời gian, nhưng semantics và state phức tạp hơn.

### 8.4 Session window

Session gom event theo khoảng inactivity:

    customer activity
    event -- 2m -- event -- 20m gap -- new session

Phù hợp user session, browsing hoặc order interaction. Session window có thể merge khi event đến trong gap phù hợp.

### 8.5 Grace và late event

Event có timestamp cũ có thể đến muộn do network/retry. Grace period quyết định hệ thống còn chấp nhận update window hay đóng cửa sổ.

Cần ghi rõ:

- timestamp dùng là event time hay processing time;
- out-of-order tối đa;
- grace;
- output update/final;
- late event bị bỏ, route hay metric.

## 9. Join

### 9.1 Stream-stream join

Hai KStream đều là event stream:

    orders stream
       + join +
    payments stream

Cần window vì hai event không nhất thiết đến cùng lúc:

    JoinWindows.ofTimeDifferenceWithNoGrace(
        Duration.ofMinutes(5)
    )

Semantics:

- chỉ join event trong khoảng thời gian;
- duplicate/retry có thể tạo output lặp;
- late event có thể không join;
- cả hai side cần partition theo cùng join key.

### 9.2 Stream-table join

KStream event lookup vào KTable state:

    OrderCreated stream
       + lookup customer table
    Customer current state

Dùng khi mỗi order cần customer profile hiện tại hoặc trạng thái mới nhất tại thời điểm xử lý.

KTable update sau đó không tự replay mọi stream event cũ. Nếu cần re-evaluate lịch sử khi table đổi, phải thiết kế khác.

### 9.3 Table-table join

Hai KTable join theo state hiện tại:

    customer table + account table -> customer account view

Output thay đổi khi một bên update. Cần hiểu null/tombstone và cache.

### 9.4 GlobalKTable join

GlobalKTable có đầy đủ data trên mỗi instance. Join có thể dùng foreign key mapping mà không repartition stream theo table key, tùy API.

Trade-off:

- replicate toàn table đến mọi instance;
- startup restore lớn;
- memory/disk/network cao;
- phù hợp dimension table nhỏ/vừa.

### 9.5 Join key và repartition

Nếu stream A key bằng orderId nhưng muốn join stream B theo customerId:

    key hiện tại: orderId
    join key cần: customerId

Cần selectKey/map rồi repartition phù hợp. Không assume join tự hash lại mọi thứ đúng ý.

## 10. Repartition topic

Repartition topic là topic nội bộ dùng để đổi partitioning theo key mới.

Mô hình:

    input partitioned by orderId
         |
         +--> selectKey(customerId)
         |
         v
    repartition topic by customerId
         |
         v
    groupBy/aggregate/join

Repartition cần disk, network và latency. Nó có thể tạo topic nội bộ với tên topology/application.

### 10.1 Khi cần repartition?

Thường khi:

- groupBy đổi key;
- join cần cùng key;
- downstream stateful operator phụ thuộc key mới;
- source topic không partition theo business key.

### 10.2 Khi tránh được?

- key đã đúng;
- chỉ mapValues/filter;
- dùng GlobalKTable cho dimension table nhỏ;
- không cần stateful operation theo key mới.

### 10.3 Hot key

Nếu customerId có tenant lớn, repartition theo customerId vẫn tạo hot key. Repartition không sửa vấn đề key design; nó chỉ phân phối theo key mới.

## 11. Changelog topic

Changelog topic lưu thay đổi state store để restore.

    local state store
          |
          +--> changelog topic
          |
          +--> restore sau restart

Changelog giúp:

- khôi phục state;
- migrate task giữa instance;
- chịu được local disk mất;
- hỗ trợ fault tolerance.

Changelog không phải output business topic:

- output topic phục vụ downstream;
- changelog phục vụ internal state recovery.

### 11.1 Retention và compaction

Changelog thường cần compact hoặc policy phù hợp để giữ state theo key. Không xóa changelog tùy tiện vì restore sẽ chậm hoặc không đầy đủ.

### 11.2 Restore

Khi instance mới nhận task:

1. tạo local state store;
2. đọc changelog;
3. rebuild state;
4. bắt đầu xử lý live input.

Restore lớn có thể làm startup/rebalance lâu. Theo dõi restore latency và disk.

## 12. Exactly-once trong Kafka Streams

Kafka Streams có processing guarantee cấu hình được. Với pipeline Kafka-to-Kafka, exactly-once có thể bao gồm:

- input offset;
- state store/changelog;
- output topic;
- transaction.

Khái niệm:

    read input
       |
       +--> update state
       +--> write output
       +--> commit offset/state/output transaction

Nếu transaction abort, output/state/offset không được coi là committed cùng nhau trong phạm vi Kafka.

### 12.1 Giới hạn

Kafka Streams exactly-once không tự bao trùm:

- database;
- REST API;
- email;
- payment provider;
- external cache.

Nếu processor gọi external side effect, vẫn cần idempotency/outbox/inbox.

### 12.2 Transaction timeout

Topology chậm, state lớn hoặc downstream Kafka chậm có thể làm transaction timeout. Cần đo processing latency và cấu hình phù hợp, không tăng vô hạn.

## 13. Topology visualization

In topology:

    Topology topology = builder.build();
    System.out.println(topology.describe());

Cần đọc:

- source node;
- processor;
- state store;
- repartition topic;
- sink topic;
- parent/child relationship.

Topology describe giúp phát hiện:

- repartition ngoài dự kiến;
- state store không materialize như mong muốn;
- sink nhầm topic;
- join topology phức tạp;
- internal topic naming.

## 14. TopologyTestDriver

TopologyTestDriver cho phép test topology mà không cần broker thật:

    TopologyTestDriver testDriver =
        new TopologyTestDriver(topology, props);

    TestInputTopic<String, OrderCreated> input =
        testDriver.createInputTopic(
            "dev.orders.order-created.v1",
            stringSerializer,
            orderCreatedSerializer
        );

    TestOutputTopic<String, CustomerStats> output =
        testDriver.createOutputTopic(
            "dev.customers.stats.v1",
            stringDeserializer,
            customerStatsDeserializer
        );

    input.pipeInput("ORD-1", orderCreated);
    CustomerStats result = output.readValue();

    testDriver.close();

### 14.1 Test stateless

- input valid;
- input invalid;
- map key/value;
- branch route;
- output schema.

### 14.2 Test stateful

- nhiều record cùng key;
- aggregate count;
- state update;
- window boundary;
- late event;
- tombstone;
- restart/restore behavior ở integration test.

TopologyTestDriver nhanh và deterministic, nhưng không thay thế test broker thật cho network, partition assignment, replication và registry.

## 15. Bài lab 1 — Đếm order theo customer trong tumbling window

Topology:

    OrderCreated
        |
        +--> selectKey(customerId)
        |
        +--> groupByKey
        |
        +--> 5-minute tumbling window
        |
        +--> count
        |
        +--> output topic

Yêu cầu:

1. tạo event với timestamp khác nhau;
2. gửi nhiều order cùng customer;
3. gửi order khác customer;
4. kiểm tra count từng window;
5. thử event tới muộn;
6. thay đổi grace;
7. xem topology describe;
8. ghi output key gồm customer/window.

Quan sát:

- repartition có được tạo không;
- state store name;
- changelog topic;
- late event behavior;
- output có update/final semantics nào.

## 16. Bài lab 2 — Join OrderCreated với CustomerUpdated

Thiết kế:

    OrderCreated stream
        |
        +--> join CustomerTable
        |
        v
    EnrichedOrder

1. Tạo customer state topic compacted.
2. Load CustomerUpdated vào KTable.
3. Gửi OrderCreated cho customer đã có state.
4. Gửi OrderCreated cho customer chưa có state.
5. Update customer.
6. Gửi order tiếp theo.
7. So sánh profile được join.
8. Kiểm tra tombstone customer.
9. Nếu join bằng stream-stream, thêm window và test late event.

Ghi rõ: KTable join dùng state hiện tại tại thời điểm xử lý stream event; table update không tự replay mọi order cũ.

## 17. Bài lab 3 — KTable giữ trạng thái order mới nhất

1. Input OrderStatusChanged có key orderId.
2. Tạo KTable từ topic.
3. Materialize store order-status-store.
4. Gửi CREATED, PAID, SHIPPED.
5. Query output/table.
6. Gửi tombstone để xóa order state.
7. Restart application.
8. Quan sát restore từ changelog.

Kiểm tra:

- record nào là current state;
- output change log;
- local store;
- restore duration;
- behavior khi event cũ đến sau.

## 18. Bài lab 4 — Hot key và repartition

1. Input key orderId.
2. selectKey customerId.
3. groupBy customerId.
4. Gửi rất nhiều record cho một customer.
5. Quan sát repartition topic và task lag.
6. So sánh với phân bố customer đều.
7. Thử bucket key.
8. Ghi rõ ordering bị thay đổi thế nào.

Không kết luận bucket luôn đúng. Nếu customer ordering tuyệt đối cần giữ, bucket có thể không phù hợp.

## 19. Bài lab 5 — Xóa local state và restore changelog

1. Chạy topology aggregate.
2. Ghi state và output.
3. Dừng app sạch.
4. Xóa state directory local trong môi trường test.
5. Khởi động lại cùng application.id.
6. Đợi restore.
7. Query aggregate.
8. So sánh state trước/sau.

Chỉ xóa state local của lab. Không xóa thư mục state production mà chưa kiểm tra changelog, retention và restore plan.

## 20. Bài lab 6 — TopologyTestDriver

Viết test cho:

- filter invalid order;
- count theo customer;
- window boundary;
- stream-table join;
- tombstone;
- duplicate event;
- schema/Serde error.

Mỗi test nên có:

- input fixture;
- expected key/value/timestamp;
- assertion output;
- assertion state nếu queryable;
- cleanup test driver.

## 21. Chẩn đoán Kafka Streams

### Không có output

Kiểm tra:

- input topic đúng;
- application.id;
- Serde;
- key có null không;
- filter có loại hết record không;
- topology sink đúng topic;
- event time/window chưa đóng;
- state store restore chưa hoàn tất;
- exception task.

### Join không match

Kiểm tra:

- join key hai bên;
- partitioning/repartition;
- KTable đã có state trước event chưa;
- stream-stream window;
- timestamp;
- null/tombstone;
- schema/type mismatch.

### Topology tạo nhiều internal topic

Kiểm tra:

- selectKey/groupBy;
- join;
- repartition;
- topology builder;
- key đã đúng chưa.

Nhiều internal topic không tự là lỗi, nhưng tăng network/disk/latency.

### State restore chậm

Kiểm tra:

- changelog size;
- state store disk;
- network;
- number of tasks;
- compaction;
- Serde performance;
- instance assignment;
- restore traffic.

### Task crash lặp lại

Kiểm tra:

- poison record;
- Serde;
- state store corruption;
- schema evolution;
- external call;
- transaction timeout;
- exception không được recover.

## 22. Sai lầm phổ biến

### Dùng KStream khi cần state hiện tại

KStream giữ mọi event; lookup latest state thường phù hợp KTable.

### Dùng KTable như lịch sử event

KTable chỉ biểu diễn state/latest update, không thay thế event history.

### Join mà không kiểm tra key

Hai stream cùng business entity nhưng key khác nhau sẽ không match nếu không repartition.

### Nghĩ selectKey tự repartition ngay

Repartition thường xảy ra khi downstream stateful operator cần, không phải mọi selectKey đều ngay lập tức tạo topic.

### Materialize state không cần retention/restore plan

State store có disk, changelog và recovery cost.

### Xóa state directory production

Có thể gây restore lớn, downtime hoặc mất state nếu changelog không đủ.

### Dùng GlobalKTable cho bảng rất lớn

Mỗi instance giữ toàn bộ table, chi phí nhân theo instance.

### Window không có grace policy

Late event sẽ bị hiểu sai hoặc bỏ qua không có audit.

### Exactly-once Kafka Streams bao trùm API ngoài

External side effect vẫn cần idempotency.

### TopologyTestDriver thay cho mọi integration test

Không kiểm tra network, broker, registry, rebalance hay replication.

## 23. Câu hỏi tự kiểm tra

1. KStream khác KTable thế nào?
2. GlobalKTable phù hợp với loại data nào?
3. Stateless và stateful operation khác gì?
4. groupBy khác groupByKey thế nào?
5. Vì sao đổi key có thể tạo repartition?
6. Tumbling và hopping window khác nhau thế nào?
7. Session window dùng cho gì?
8. Stream-stream join cần window vì sao?
9. Stream-table join dùng state nào?
10. Changelog topic có vai trò gì?
11. Repartition topic có vai trò gì?
12. State store local có thể khôi phục bằng gì?
13. Exactly-once Streams bao trùm phạm vi nào?
14. Vì sao GlobalKTable tốn tài nguyên theo instance?
15. Hot key ảnh hưởng topology ra sao?
16. TopologyTestDriver kiểm tra được gì?
17. Vì sao event mới không luôn cập nhật lại mọi join cũ?
18. Tombstone ảnh hưởng KTable thế nào?

### Đáp án ngắn

1. KStream là event sequence; KTable là latest state theo key.
2. Dimension table nhỏ/vừa cần lookup local trên mọi instance.
3. Stateful cần nhớ state trước đó.
4. groupBy có thể đổi key/Serde; groupByKey giữ key hiện tại.
5. Stateful downstream cần cùng key trên partition.
6. Không overlap so với overlap theo bước thời gian.
7. Gom event theo khoảng inactivity.
8. Hai event cần xuất hiện trong thời gian tương quan.
9. KTable/current state.
10. Restore state store sau restart/failure.
11. Đổi partitioning theo key mới.
12. Changelog topic.
13. Kafka read-process-write/state/output.
14. Mỗi instance materialize toàn bộ table.
15. Một task/partition bị quá tải.
16. Logic topology/state/output deterministic.
17. KTable join phản ánh state lúc stream event xử lý.
18. Xóa state cho key trong changelog/table.

## 24. Checklist hoàn thành Tuần 11

- [ ] Tôi phân biệt KStream, KTable và GlobalKTable.
- [ ] Tôi hiểu stateless và stateful operation.
- [ ] Tôi dùng được filter, map, branch và transform.
- [ ] Tôi dùng được groupBy, aggregate, reduce và count.
- [ ] Tôi hiểu tumbling, hopping, sliding và session window.
- [ ] Tôi biết xử lý late event và grace.
- [ ] Tôi phân biệt stream-stream, stream-table và table-table join.
- [ ] Tôi hiểu Serde cho input/output/state.
- [ ] Tôi nhận diện được khi topology cần repartition.
- [ ] Tôi hiểu state store và materialization.
- [ ] Tôi phân biệt changelog topic với business output topic.
- [ ] Tôi biết state restore từ changelog hoạt động thế nào.
- [ ] Tôi hiểu hot key và chi phí repartition.
- [ ] Tôi biết giới hạn exactly-once của Kafka Streams.
- [ ] Tôi in/đọc được topology description.
- [ ] Tôi viết được test bằng TopologyTestDriver.
- [ ] Tôi hoàn thành lab window, join, KTable, hot key và restore.
- [ ] Tôi có kế hoạch disk/recovery cho state store.

## 25. Câu chốt cần nhớ

Kafka Streams là cách xây ứng dụng xử lý stream có state ngay trên Kafka, nhưng topology vẫn chịu các quy luật của partition, key, Serde, offset và transaction.

Năm ý cần nhớ:

1. KStream là lịch sử event; KTable là trạng thái mới nhất theo key.
2. Stateful operation thường kéo theo state store, changelog và recovery cost.
3. Đổi key trước aggregate/join thường cần repartition.
4. Window/join phải định nghĩa rõ timestamp, late event và ordering.
5. Exactly-once trong Kafka Streams không tự bao trùm database hay external API.


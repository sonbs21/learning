# Tuần 3 — Topic, partition, key và replication

> Mục tiêu: thiết kế được topic có partition key hợp lý, hiểu ordering và scale, đồng thời biết replication bảo vệ dữ liệu như thế nào.

## 1. Kết quả cần đạt

Sau bài học, bạn có thể:

- Phân biệt topic, partition, record, leader và follower.
- Giải thích partition là đơn vị ordering và parallelism.
- Chọn key dựa trên ordering nghiệp vụ và phân bố tải.
- Mô tả hash partitioning, sticky partitioning và custom partitioner.
- Hiểu tác động của partition count đến throughput, consumer group và ordering.
- Phân biệt replication factor, replicas, ISR và leader.
- Giải thích min.insync.replicas và unclean leader election.
- Nhận diện partition skew, hot key và cách xử lý.
- Cấu hình retention time, retention bytes và cleanup policy.
- Phân biệt delete policy, compact policy và tombstone.
- Viết topic design document cho hệ thống Order Platform.

## 2. Topic, record và partition

### 2.1 Topic

Topic là tên logic đại diện cho một dòng dữ liệu. Ví dụ:

    dev.orders.order-created.v1

Producer ghi record vào topic. Consumer đọc record từ topic. Topic không nhất thiết tương ứng với một bảng database; nó là một stream có thứ tự cục bộ và chính sách lưu trữ.

Một topic nên có:

- tên rõ domain và mục đích;
- owner;
- schema hoặc quy ước payload;
- partition key;
- số partition;
- replication factor;
- retention;
- cleanup policy;
- danh sách consumer group;
- yêu cầu ordering và replay.

### 2.2 Record

Record có thể gồm:

- key;
- value;
- headers;
- timestamp;
- partition;
- offset.

Ví dụ record:

    key   = customer-42
    value = CustomerEmailChanged
    topic = dev.customers.customer-events.v1

Partition và offset do Kafka gán sau khi record được append. Producer có thể chọn key, nhưng không tự quyết định offset cuối cùng.

### 2.3 Partition

Partition là một log tuần tự bên trong topic:

    Topic: orders
      Partition 0: [r0] [r1] [r2] [r3]
      Partition 1: [r0] [r1] [r2]
      Partition 2: [r0] [r1] [r2] [r3] [r4]

Mỗi partition có offset riêng bắt đầu từ 0 và tăng dần. Không có một offset duy nhất áp dụng cho toàn topic.

Partition là:

- đơn vị ordering;
- đơn vị parallelism trong consumer group;
- đơn vị được leader/follower replicate;
- đơn vị lưu trữ log và retention;
- giới hạn tự nhiên của số consumer active trong một group.

### 2.4 Ordering

Kafka đảm bảo thứ tự trong một partition, không đảm bảo thứ tự toàn topic khi topic có nhiều partition.

Nếu các event của cùng một order cần theo thứ tự:

    OrderCreated
    PaymentAuthorized
    OrderCompleted

thì chúng phải đi vào cùng partition, thường bằng key orderId. Nếu ba event rơi vào ba partition khác nhau, consumer không thể dựa vào offset để suy ra thứ tự toàn cục.

Ordering cũng có thể bị ảnh hưởng bởi:

- nhiều producer ghi cùng aggregate;
- xử lý bất đồng bộ ở consumer;
- retry ở downstream;
- các event tạo ra từ nhiều nguồn khác nhau.

## 3. Partition là đơn vị scale

### 3.1 Producer parallelism

Nhiều partition cho phép broker xử lý nhiều log song song và producer gửi dữ liệu đến nhiều leader khác nhau. Tuy nhiên throughput thực tế còn phụ thuộc:

- network;
- disk;
- batch;
- compression;
- số broker;
- kích thước record;
- số consumer;
- downstream.

Tăng partition không phải thuốc chữa mọi bottleneck.

### 3.2 Consumer parallelism

Một consumer group có thể xử lý song song theo partition:

    Topic có 4 partition
    Group có 2 consumer
    Mỗi consumer nhận khoảng 2 partition

Nếu group có 6 consumer nhưng topic chỉ có 4 partition:

    4 consumer có assignment
    2 consumer không có partition để xử lý

Vì vậy số consumer active tối đa trong group bị giới hạn bởi số partition.

### 3.3 Chi phí của quá nhiều partition

Mỗi partition có metadata, file segment, index và hoạt động replication. Quá nhiều partition có thể làm tăng:

- memory usage;
- thời gian leader election;
- metadata size;
- thời gian recovery;
- số file và thao tác disk;
- chi phí rebalance;
- độ phức tạp vận hành.

Đừng chọn số partition chỉ bằng công thức throughput đơn giản. Cần tính cả growth, số consumer group, retention, replication và khả năng mở rộng broker.

## 4. Record key và partitioner

### 4.1 Key dùng để làm gì?

Key thường phục vụ ba mục tiêu:

1. giữ event liên quan cùng partition;
2. tạo locality cho một entity;
3. phân bố tải giữa các partition.

Ví dụ:

| Nghiệp vụ | Key có thể dùng |
|---|---|
| Lịch sử order | orderId |
| Event của customer | customerId |
| Giao dịch tài khoản | accountId |
| Sensor readings | deviceId |
| Log request | requestId hoặc serviceId |

Không chọn key chỉ vì field đó có sẵn. Hãy hỏi: event nào cần ordering cùng nhau?

### 4.2 Cùng key thường vào cùng partition

Với partitioner mặc định, cùng một key thường được băm để chọn cùng partition khi số partition không đổi. Điều này giúp giữ thứ tự tương đối cho các record cùng key.

Ví dụ:

    key=customer-42 -> partition 1
    key=customer-42 -> partition 1
    key=customer-99 -> partition 2

Đây là quy tắc định tuyến, không phải đảm bảo tuyệt đối cho mọi tình huống. Khi thay đổi số partition hoặc custom partitioner, mapping có thể thay đổi.

### 4.3 Hash partitioning

Mô hình khái niệm:

    partition = hash(key) mod số_partition

Hash giúp key phân tán tương đối đều nếu cardinality và phân bố key tốt.

Nhưng hash không giải quyết được hot key. Nếu một customer có lượng event quá lớn, tất cả event của customer đó vẫn đi cùng partition để giữ ordering.

### 4.4 Null key và sticky partitioning

Khi record không có key, producer có thể phân bố record theo cơ chế sticky/batching của client. Mục tiêu thường là tận dụng batch tốt hơn thay vì chuyển partition sau mỗi record.

Record không có key phù hợp khi:

- không cần ordering theo entity;
- muốn phân bố tải tự do;
- event độc lập;
- có thể xử lý song song không phụ thuộc nhau.

Record không có key không phù hợp khi cần đảm bảo event của cùng order hoặc account đi cùng partition.

### 4.5 Custom partitioner

Custom partitioner cho phép chọn partition theo quy tắc riêng, nhưng làm tăng độ phức tạp:

- phải đảm bảo logic ổn định;
- producer và consumer cần hiểu assumption;
- dễ tạo skew;
- khó thay đổi sau khi dữ liệu đã tồn tại;
- phải test khi số partition thay đổi.

Chỉ dùng custom partitioner khi business requirement rõ ràng và partitioner mặc định không đáp ứng được.

## 5. Chọn partition key

### 5.1 Ba câu hỏi bắt buộc

Trước khi chọn key, trả lời:

1. Đơn vị nào cần giữ ordering?
2. Đơn vị nào có thể xử lý song song?
3. Đơn vị nào có nguy cơ tạo hot key?

Ví dụ order:

- Cần ordering theo orderId.
- Có thể xử lý song song giữa các order.
- Một order thường không có quá nhiều event.
- Key phù hợp: orderId.

Ví dụ customer notification:

- Có thể cần ordering theo customerId.
- Một customer lớn có thể tạo rất nhiều event.
- Cần cân nhắc hot key hoặc tách workload.

### 5.2 Key tốt

Một key tốt thường:

- phản ánh boundary nghiệp vụ;
- có cardinality đủ lớn;
- phân bố tương đối đều;
- ổn định theo thời gian;
- tồn tại trong mọi event liên quan;
- giúp consumer xử lý idempotent.

### 5.3 Key xấu

Tránh key:

- luôn có cùng một giá trị;
- chỉ có vài giá trị cho hàng triệu record;
- thay đổi không ổn định;
- chứa thông tin nhạy cảm không cần thiết;
- không liên quan đến ordering;
- dùng timestamp đơn lẻ khiến khó truy vết entity.

Ví dụ key status với các giá trị NEW, PAID, CANCELLED thường tạo phân bố rất lệch. Timestamp hiện tại có thể phân bố tốt nhưng không giúp gom event cùng order.

## 6. Partition skew và hot key

### 6.1 Partition skew

Partition skew là hiện tượng tải giữa các partition không đều:

    Partition 0:  10 GB
    Partition 1:   1 GB
    Partition 2:   1 GB

Hậu quả:

- một broker hoặc consumer quá tải;
- lag tập trung ở một partition;
- tăng consumer không giúp partition hot;
- disk usage không đều;
- throughput toàn pipeline bị giới hạn bởi partition nóng.

### 6.2 Hot key

Hot key là một key có lượng record vượt trội. Ví dụ:

    customerId = large-tenant
    event count = 80% tổng traffic

Nếu cần giữ ordering tuyệt đối cho key đó, không thể tùy tiện hash nó thành nhiều partition. Bạn phải chọn trade-off:

- giữ ordering và chấp nhận bottleneck;
- nới yêu cầu ordering;
- chia key thành các bucket;
- tách tenant lớn sang topic/workload riêng;
- xử lý hot entity bằng cơ chế đặc biệt.

### 6.3 Key bucketing

Có thể tạo key bucket:

    key = customerId + ":" + bucket(orderId)

Ví dụ một customer được chia thành 16 bucket. Cách này tăng parallelism nhưng mất ordering toàn bộ event của customer. Chỉ dùng khi business chấp nhận ordering trong từng bucket hoặc có sequence/reconciliation ở downstream.

### 6.4 Cách quan sát skew

Theo dõi:

- bytes in/out theo partition;
- records per partition;
- consumer lag theo partition;
- disk usage theo broker;
- key frequency;
- processing latency theo partition.

Không kết luận skew chỉ từ tổng throughput của topic. Cần xem phân bố chi tiết từng partition.

## 7. Tăng số partition và thay đổi ordering

Tăng partition thường là thao tác mở rộng, nhưng có tác động đến mapping key:

Trước:

    hash(key) mod 3

Sau:

    hash(key) mod 6

Cùng key có thể được định tuyến sang partition khác sau khi tăng partition. Các record mới và cũ của cùng key có thể nằm ở các partition khác nhau, làm ordering toàn bộ lịch sử khó bảo đảm.

Vì vậy:

- quyết định partition count sớm nhưng đừng đoán mù;
- tăng partition trước khi thiếu capacity;
- hiểu client partitioner đang dùng;
- không coi tăng partition là thao tác vô hại;
- nếu ordering lịch sử tuyệt đối quan trọng, cân nhắc topic mới và migration.

Giảm số partition thường không phải thao tác đơn giản. Có thể cần tạo topic mới, migrate dữ liệu và chuyển consumer.

## 8. Replication

### 8.1 Replica, leader và follower

Mỗi partition có thể có nhiều replica:

    Partition 0
      leader:  broker-1
      follower: broker-2
      follower: broker-3

Leader nhận request produce và thường phục vụ fetch. Follower replicate dữ liệu từ leader. Nếu leader lỗi, một replica đủ điều kiện có thể được chọn làm leader mới.

### 8.2 Replication factor

Replication factor là số bản sao của mỗi partition.

Ví dụ:

    replication factor = 3
    Partition 0 replicas = broker-1, broker-2, broker-3

Replication factor 1:

- không có redundancy;
- mất broker có thể mất dữ liệu hoặc availability;
- phù hợp lab một node;
- không phù hợp production quan trọng.

Replication factor 3 không có nghĩa mọi request đều được ghi đồng bộ vào cả ba trước khi trả về; điều đó còn phụ thuộc acks, ISR và cấu hình producer.

### 8.3 Preferred leader

Một partition có replica assignment và leader hiện tại. Kafka có thể cố gắng dùng preferred replica làm leader để cân bằng tải. Leader distribution không đều có thể làm một broker nhận nhiều produce/fetch hơn các broker khác.

Ở tuần này, cần biết xem leader trong describe output và nhận diện leader skew. Cân bằng leader chuyên sâu sẽ học khi vận hành cluster.

## 9. ISR, min.insync.replicas và durability

### 9.1 ISR

ISR là tập replica đang được coi là đồng bộ đủ với leader theo tiêu chí của Kafka.

Ví dụ:

    Replicas: 1,2,3
    ISR:      1,2,3

Nếu broker-3 chậm hoặc mất kết nối:

    Replicas: 1,2,3
    ISR:      1,2

ISR không nhất thiết luôn bằng danh sách replicas. Replica ngoài ISR có thể cần catch up trước khi trở lại đủ điều kiện.

### 9.2 min.insync.replicas

min.insync.replicas là số replica tối thiểu phải ở ISR để một số write yêu cầu đủ an toàn được chấp nhận.

Ví dụ production thường có ý tưởng:

    replication.factor = 3
    min.insync.replicas = 2
    producer acks = all

Mục tiêu là không xác nhận write khi chỉ còn một replica đồng bộ. Tuy nhiên cấu hình thực tế phải xét failure budget và khả năng chấp nhận mất availability.

### 9.3 Khi ISR giảm

Khi ISR nhỏ hơn min.insync.replicas và producer yêu cầu acks phù hợp, produce có thể thất bại với lỗi không đủ replica trong sync.

Đây không nhất thiết là bug. Đó có thể là tín hiệu an toàn: cluster đang không đủ điều kiện durability mà bạn đã yêu cầu.

### 9.4 Unclean leader election

Unclean leader election cho phép replica không nằm trong ISR trở thành leader khi không còn replica ISR phù hợp.

Trade-off:

- bật: có thể khôi phục availability nhanh hơn nhưng có nguy cơ mất record chưa replicate;
- tắt: bảo vệ dữ liệu tốt hơn nhưng partition có thể unavailable lâu hơn.

Không chọn theo câu “luôn bật” hoặc “luôn tắt”. Cần phân loại dữ liệu:

- event thanh toán/audit: ưu tiên không mất dữ liệu;
- metric tạm thời: có thể ưu tiên availability;
- dữ liệu có thể rebuild: trade-off khác dữ liệu nguồn sự thật.

## 10. Retention và segment

### 10.1 Retention time

Retention time giữ record trong một khoảng thời gian tối thiểu theo chính sách cleanup. Khi record đủ cũ và segment phù hợp, Kafka có thể xóa dữ liệu.

Retention không đảm bảo record bị xóa đúng từng giây tại thời điểm hết hạn. Cleanup thường làm việc theo segment và chu kỳ kiểm tra.

### 10.2 Retention bytes

Retention bytes giới hạn dung lượng log. Khi vượt ngưỡng, các segment cũ hơn có thể bị xóa theo cleanup policy.

Cần phân biệt:

- retention theo thời gian;
- retention theo dung lượng;
- giới hạn tổng topic;
- giới hạn từng partition;
- disk capacity của broker.

### 10.3 Segment

Partition log được chia thành các segment. Segment giúp Kafka:

- append dữ liệu theo file;
- tạo index;
- roll file theo thời gian hoặc kích thước;
- cleanup dữ liệu cũ;
- tránh một file log vô hạn.

Retention thường cleanup toàn segment cũ, không cắt tùy ý từng record giữa segment.

### 10.4 Chọn retention

Đặt retention dựa trên:

- thời gian consumer chậm nhất cần catch up;
- cửa sổ replay;
- yêu cầu audit;
- chi phí disk;
- backup;
- privacy và data deletion policy;
- tốc độ ghi trung bình và peak.

Công thức ước lượng sơ bộ:

    dung lượng cần giữ
    ≈ throughput trung bình x thời gian retention
    + overhead replica
    + headroom

Đây chỉ là ước lượng ban đầu. Cần benchmark record size, compression, peak traffic và segment overhead.

## 11. Cleanup policy: delete và compact

### 11.1 Delete policy

Delete policy xóa record/segment cũ dựa trên retention time hoặc retention bytes. Phù hợp với event stream cần cửa sổ lịch sử hữu hạn.

Ví dụ:

    cleanup.policy=delete

### 11.2 Compact policy

Compaction giữ lại giá trị mới nhất theo key trong thời gian cleanup chạy. Phù hợp với topic biểu diễn state mới nhất:

    customer-42 -> email mới nhất
    customer-99 -> trạng thái mới nhất

Compaction không có nghĩa log lập tức chỉ còn một record cho mỗi key. Đây là quá trình nền, có thể tồn tại duplicate hoặc record cũ trước khi cleanup hoàn tất.

### 11.3 Delete và compact kết hợp

Có thể dùng:

    cleanup.policy=compact,delete

Ý nghĩa là vừa compact theo key vừa xóa dữ liệu quá cũ theo retention. Cần hiểu policy phù hợp với use case và schema.

### 11.4 Tombstone record

Tombstone là record có key nhưng value null, dùng để biểu thị xóa key khi compact:

    key   = customer-42
    value = null

Tombstone không nhất thiết biến mất ngay. Nó cần tồn tại đủ lâu để consumer và compaction quan sát việc xóa. Nếu phát tombstone rồi xóa quá sớm, consumer mới có thể giữ state cũ.

Tombstone khác với một event JSON có trường deleted=true. Event deleted=true là payload; tombstone là quy ước key/value phục vụ compaction.

## 12. Thiết kế topic theo use case

### 12.1 Event lịch sử order

    Topic: prod.orders.order-events.v1
    Key: orderId
    Policy: delete
    Retention: theo cửa sổ replay/audit
    Ordering: trong cùng order
    Consumer: payment, inventory, analytics

### 12.2 Trạng thái customer mới nhất

    Topic: prod.customers.customer-state.v1
    Key: customerId
    Policy: compact hoặc compact,delete
    Tombstone: dùng khi xóa customer
    Consumer: cache builder, search index, analytics

### 12.3 Metric hoặc telemetry

    Topic: prod.telemetry.service-metrics.v1
    Key: có thể là serviceId hoặc null tùy mục tiêu
    Policy: delete
    Retention: ngắn
    Ordering: thường không cần toàn cục

### 12.4 Command

    Topic: prod.inventory.reserve-commands.v1
    Key: orderId
    Owner: inventory-team
    Semantics: command, không gọi là event
    Retry: cần quy ước rõ
    Consumer: inventory service

Command và event có thể cùng đi qua Kafka nhưng nên tách topic hoặc naming để tránh nhầm ownership và semantics.

## 13. Bài lab 1 — Tạo topic nhiều partition

Tạo topic 3 partition:

    kafka-topics.sh \
      --bootstrap-server localhost:9092 \
      --create \
      --topic dev.orders.order-created.v1 \
      --partitions 3 \
      --replication-factor 1

Describe:

    kafka-topics.sh \
      --bootstrap-server localhost:9092 \
      --describe \
      --topic dev.orders.order-created.v1

Ghi lại:

- partition count;
- leader mỗi partition;
- replicas;
- ISR;
- replication factor.

Câu hỏi:

1. Vì sao một topic có ba partition có thể có ba offset 0 khác nhau?
2. Consumer group có năm instance thì bao nhiêu instance có thể active?
3. Trong lab một broker, vì sao replica list chỉ có một broker?

## 14. Bài lab 2 — Chứng minh key định tuyến

Gửi record có key:

    kafka-console-producer.sh \
      --bootstrap-server localhost:9092 \
      --topic dev.orders.order-created.v1 \
      --property parse.key=true \
      --property key.separator=:

Nhập:

    customer-42:order-1
    customer-99:order-2
    customer-42:order-3
    customer-42:order-4
    customer-99:order-5

Consume và in metadata:

    kafka-console-consumer.sh \
      --bootstrap-server localhost:9092 \
      --topic dev.orders.order-created.v1 \
      --from-beginning \
      --property print.key=true \
      --property print.partition=true \
      --property print.offset=true \
      --max-messages 5

Kiểm tra:

- các record customer-42 đang ở partition nào;
- các record customer-99 đang ở partition nào;
- offset có tăng riêng theo từng partition không;
- có thể suy ra thứ tự toàn topic không.

Ghi chú: nếu number of partitions thay đổi, mapping key có thể thay đổi. Không chỉ nhìn một lần rồi coi đây là cam kết bất biến.

## 15. Bài lab 3 — Chứng minh giới hạn consumer parallelism

1. Tạo topic 3 partition.
2. Chạy một consumer group với một consumer.
3. Chạy thêm consumer thứ hai và thứ ba cùng group.
4. Chạy thêm consumer thứ tư.
5. Dùng consumer-groups describe để xem assignment.

Kỳ vọng:

- ba consumer đầu có thể nhận partition;
- consumer thứ tư có thể ở trạng thái không có assignment;
- các consumer cùng group chia dữ liệu thay vì mỗi consumer nhận toàn bộ topic.

Sau đó tạo một group khác. Group mới vẫn có thể đọc cùng các record vì offset độc lập.

## 16. Bài lab 4 — Quan sát partition skew

Tạo hai loại key:

- nhiều key phân bố đều;
- một hot key xuất hiện rất nhiều lần.

Gửi dữ liệu đủ lớn, sau đó quan sát:

- số record mỗi partition;
- lag mỗi partition;
- bytes in/out nếu có metric;
- partition nào có leader hoặc consumer chịu tải cao.

Viết báo cáo ngắn:

1. key nào tạo skew;
2. vì sao thêm consumer không chữa được một partition hot;
3. có thể chia bucket không;
4. chia bucket có làm mất ordering không.

## 17. Bài lab 5 — Retention ngắn

Tạo topic test với retention nhỏ:

    kafka-topics.sh \
      --bootstrap-server localhost:9092 \
      --create \
      --topic dev.retention.test.v1 \
      --partitions 1 \
      --replication-factor 1 \
      --config retention.ms=30000 \
      --config segment.ms=1000

Gửi vài record, đọc lại ngay, sau đó chờ quá thời gian retention rồi consume lại từ beginning.

Kiểm tra:

- record có còn không;
- cleanup có xảy ra đúng ngay tại mốc 30 giây không;
- segment.ms ảnh hưởng quan sát cleanup thế nào;
- retention là tối thiểu/ước lượng hay deadline chính xác từng record.

Đây là lab local. Không dùng retention cực ngắn trên topic thật nếu chưa hiểu tác động.

## 18. Bài lab 6 — Compacted topic và tombstone

Tạo compacted topic:

    kafka-topics.sh \
      --bootstrap-server localhost:9092 \
      --create \
      --topic dev.customers.state.v1 \
      --partitions 1 \
      --replication-factor 1 \
      --config cleanup.policy=compact

Gửi các phiên bản state:

    customer-42:email=a@example.com
    customer-42:email=b@example.com
    customer-99:email=c@example.com

Sau đó gửi tombstone cho customer-42. Với console producer, cần dùng cách gửi key có value rỗng/null phù hợp với CLI version; hãy kiểm tra help của version đang chạy trước khi thực hiện.

Quan sát:

- trước compaction có thể thấy nhiều version;
- compaction là background process;
- tombstone biểu thị xóa key;
- không nên kết luận compacted topic lập tức chỉ có state cuối.

## 19. Topic design document

Mỗi topic của capstone nên có một tài liệu ngắn:

### Identity

- Topic name:
- Environment:
- Domain owner:
- Data classification:
- Event hay command:
- Version:

### Data model

- Key:
- Value schema:
- Required headers:
- Aggregate/entity:
- Idempotency ID:

### Ordering và scale

- Ordering cần bảo đảm theo:
- Partition count ban đầu:
- Throughput trung bình:
- Peak throughput:
- Consumer groups:
- Hot key risk:

### Durability và storage

- Replication factor:
- min.insync.replicas:
- Producer ack expectation:
- Retention time:
- Retention bytes:
- Cleanup policy:
- Tombstone policy:
- Replay window:

### Operations

- Alert lag:
- Alert disk:
- Owner on-call:
- Runbook:
- Migration strategy nếu đổi schema hoặc partition count:

## 20. Ví dụ topic design cho OrderCreated

    Topic: prod.orders.order-created.v1
    Owner: order-team
    Type: event
    Key: orderId
    Ordering: tất cả event của một order
    Initial partitions: 6
    Replication factor: 3
    min.insync.replicas: 2
    Cleanup: delete
    Retention: 14 ngày
    Consumer groups: payment, inventory, analytics
    Replay: cho phép trong 14 ngày
    Hot key risk: một order thường có ít event
    Migration: tạo v2 nếu schema breaking

Lý do chọn orderId:

- các event của một order cần thứ tự;
- orderId có cardinality cao;
- tải thường phân bố tốt;
- mỗi order không tạo lượng event khổng lồ.

Không kết luận sáu partition là con số đúng cho mọi hệ thống. Đây chỉ là thiết kế ban đầu cần được xác nhận bằng throughput, số broker và growth forecast.

## 21. Sai lầm phổ biến

### Dùng một partition để “giữ thứ tự”

Một partition giữ thứ tự dễ nhưng giới hạn throughput và availability. Chỉ dùng khi workload nhỏ hoặc ordering toàn stream thực sự cần.

### Dùng key có cardinality thấp

Key như status, country hoặc eventType có thể tạo skew nghiêm trọng.

### Chọn key để chia tải nhưng quên ordering

Random key có thể phân bố tốt nhưng làm event cùng aggregate đi nhiều partition.

### Tăng partition mà không xét ordering

Mapping hash có thể thay đổi, làm lịch sử của cùng key nằm ở nhiều partition.

### Nghĩ replication factor 3 là exactly three synchronous writes

Durability còn phụ thuộc leader, ISR, acks, min.insync.replicas và failure timing.

### Nghĩ ISR luôn bằng replicas

Replica chậm hoặc mất kết nối có thể ra khỏi ISR.

### Dùng compact cho mọi topic

Compaction phù hợp state-by-key, không phải thay thế retention cho mọi event history.

### Xóa tombstone quá sớm

Consumer hoặc compaction có thể chưa kịp quan sát tombstone, khiến state cũ quay lại.

### Không ghi owner

Khi topic có lag hoặc schema cần đổi, không ai rõ ai chịu trách nhiệm.

## 22. Câu hỏi tự kiểm tra

1. Partition là đơn vị của những hoạt động nào?
2. Vì sao Kafka chỉ đảm bảo ordering trong partition?
3. Khi nào nên dùng orderId làm key?
4. Khi nào không nên dùng key?
5. Null key có thể phù hợp với loại workload nào?
6. Hot key là gì?
7. Vì sao thêm consumer không xử lý được một partition hot?
8. Tăng partition có thể ảnh hưởng mapping key ra sao?
9. Replication factor biểu thị điều gì?
10. Leader và follower khác nhau thế nào?
11. ISR là gì?
12. min.insync.replicas bảo vệ trade-off nào?
13. Unclean leader election đánh đổi durability với điều gì?
14. Retention time và retention bytes khác nhau thế nào?
15. Segment giúp cleanup hoạt động ra sao?
16. Delete policy và compact policy khác nhau thế nào?
17. Tombstone là gì?
18. Vì sao compacted topic có thể tạm thời chứa nhiều version?
19. Vì sao topic ownership quan trọng?
20. Tại sao event history và state-by-key thường dùng policy khác nhau?

### Đáp án ngắn

1. Ordering, parallelism, storage và replication.
2. Mỗi partition có offset/log tuần tự riêng; nhiều partition không có offset toàn cục.
3. Khi event cùng order cần xử lý theo thứ tự.
4. Khi không cần ordering theo entity hoặc key gây skew.
5. Event độc lập, cần phân bố tải tự do.
6. Key có lượng traffic vượt trội.
7. Một partition chỉ được một consumer trong group xử lý tại một thời điểm.
8. Công thức hash theo số partition có thể cho kết quả khác.
9. Số replica của mỗi partition.
10. Leader nhận request chính; follower replicate.
11. Tập replica đang đủ đồng bộ với leader.
12. Không xác nhận write khi số replica an toàn quá thấp.
13. Có thể khôi phục availability nhưng mất record chưa replicate.
14. Một giới hạn theo thời gian, một giới hạn theo dung lượng.
15. Cleanup thường xóa segment cũ thay vì cắt từng record.
16. Delete xóa dữ liệu cũ; compact giữ trạng thái mới nhất theo key.
17. Record có key và value null để biểu thị xóa trong compaction.
18. Compaction chạy nền, không xảy ra ngay lập tức.
19. Cần biết ai sở hữu schema, lag, retention và incident.
20. Event history cần lịch sử; state-by-key cần giá trị mới nhất.

## 23. Checklist hoàn thành Tuần 3

- [ ] Tôi phân biệt topic và partition.
- [ ] Tôi hiểu ordering chỉ được bảo đảm trong một partition.
- [ ] Tôi giải thích partition là đơn vị parallelism của consumer group.
- [ ] Tôi chọn được key theo boundary nghiệp vụ.
- [ ] Tôi biết rủi ro của null key, low-cardinality key và hot key.
- [ ] Tôi chứng minh được cùng key thường đi cùng partition.
- [ ] Tôi hiểu tăng partition có thể thay đổi mapping key.
- [ ] Tôi phân biệt leader, follower, replicas và ISR.
- [ ] Tôi hiểu replication factor và min.insync.replicas.
- [ ] Tôi biết trade-off của unclean leader election.
- [ ] Tôi cấu hình và quan sát retention trong lab.
- [ ] Tôi phân biệt delete, compact và tombstone.
- [ ] Tôi hoàn thành topic design document cho OrderCreated.
- [ ] Tôi giải thích được vì sao replication factor 1 chỉ phù hợp local.
- [ ] Tôi biết nhận diện partition skew qua lag và tải từng partition.

## 24. Câu chốt cần nhớ

Partition không chỉ là cách Kafka chia file. Partition đồng thời quyết định ordering, parallelism, consumer assignment, replication và một phần chi phí vận hành.

Năm ý cần nhớ:

1. Chọn key trước hết để giữ đúng ordering nghiệp vụ.
2. Partition count quyết định mức parallelism tối đa của consumer group.
3. Hot key có thể làm một partition nghẽn dù tổng cluster còn rảnh.
4. Replication bảo vệ availability và durability, nhưng cần phối hợp với ISR, acks và min.insync.replicas.
5. Delete phù hợp event history; compact phù hợp state mới nhất theo key.


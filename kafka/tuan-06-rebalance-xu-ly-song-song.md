# Tuần 6 — Rebalance và xử lý song song

> Mục tiêu: hiểu vì sao consumer group rebalance, chẩn đoán rebalance loop và xử lý record song song mà không vi phạm ownership partition hoặc commit sai thứ tự.

## 1. Kết quả cần đạt

Sau bài học, bạn có thể:

- Giải thích rebalance xảy ra khi nào và nó thay đổi assignment ra sao.
- Phân biệt eager rebalance và cooperative/incremental rebalance.
- Hiểu Range, RoundRobin, Sticky và CooperativeSticky assignor.
- Dùng ConsumerRebalanceListener để xử lý revoke/assign.
- Nhận diện rebalance loop do poll chậm, crash, network hoặc cấu hình.
- Hiểu KafkaConsumer không được dùng đồng thời từ nhiều thread.
- Thiết kế mô hình một consumer thread + worker pool.
- Nhận diện rủi ro commit ordering khi worker hoàn thành không theo thứ tự.
- Dùng pause/resume để điều tiết partition chậm.
- Hiểu static membership và group.instance.id ở mức khái niệm.
- Thiết kế graceful shutdown không gây duplicate không cần thiết.

## 2. Rebalance là gì?

Rebalance là quá trình consumer group phân phối lại các partition giữa các member.

Ví dụ ban đầu:

    Topic orders: partition 0, 1, 2, 3

    consumer-A -> partition 0, 1
    consumer-B -> partition 2, 3

Khi consumer-C tham gia:

    consumer-A -> partition 0
    consumer-B -> partition 1, 2
    consumer-C -> partition 3

Assignment mới phụ thuộc assignor, member hiện tại và metadata topic.

Rebalance không chỉ xảy ra khi thêm consumer. Nó có thể xảy ra khi:

- consumer mới join;
- consumer rời group;
- consumer crash;
- heartbeat timeout;
- xử lý vượt max.poll.interval.ms;
- topic tăng partition;
- subscription thay đổi;
- coordinator thay đổi;
- network chập chờn;
- member bị kick khỏi group.

Trong lúc rebalance, một số partition có thể tạm thời không được xử lý. Vì vậy rebalance có chi phí latency và có thể làm lag tăng.

## 3. Group coordinator và membership

Mỗi consumer group có coordinator broker quản lý membership và offset commit của group.

Consumer cần:

- join group;
- gửi heartbeat;
- nhận assignment;
- poll đều đặn;
- commit offset;
- rời group khi shutdown bình thường.

Khi coordinator phát hiện membership thay đổi, group cần thống nhất assignment mới. Consumer phải xử lý callback revoke/assign và tiếp tục poll theo state mới.

Không nên coi coordinator là một service nghiệp vụ. Nó là thành phần điều phối membership của group.

## 4. Eager và cooperative rebalance

### 4.1 Eager rebalance

Trong eager rebalance, các consumer thường revoke toàn bộ partition trước khi assignment mới được phân phối.

Trạng thái khái niệm:

    Trước:
      A -> 0,1
      B -> 2,3

    Revoke:
      A -> none
      B -> none

    Sau:
      A -> 0
      B -> 1,2
      C -> 3

Ưu điểm:

- mô hình dễ hiểu;
- toàn bộ group có một điểm đồng bộ rõ;
- phù hợp một số hệ thống cũ.

Nhược điểm:

- nhiều partition bị dừng dù chỉ một member thay đổi;
- pause lớn hơn;
- lag tăng trong rolling deployment.

### 4.2 Cooperative/incremental rebalance

Cooperative rebalance cố gắng chỉ di chuyển các partition cần thiết.

Ví dụ:

    Trước:
      A -> 0,1
      B -> 2,3

    C tham gia:
      A giữ 0
      B giữ 2
      partition cần chuyển được revoke dần
      C nhận partition mới

Ưu điểm:

- giảm stop-the-world;
- rolling deployment ít gián đoạn hơn;
- giữ assignment ổn định hơn.

Nhược điểm:

- state machine phức tạp hơn;
- listener phải xử lý revoke/assign đúng;
- trong một số lần rebalance có thể cần nhiều vòng để assignment ổn định;
- config assignor phải tương thích giữa các member.

Đừng trộn eager assignor và cooperative assignor một cách thiếu kế hoạch trong rollout. Đọc tài liệu version client đang dùng trước khi đổi.

## 5. Assignment strategy

### 5.1 RangeAssignor

Range phân phối partition theo range trong từng topic. Với nhiều topic hoặc số partition không chia đều, có thể tạo phân bố không cân bằng.

Khái niệm:

    partitions: [0,1,2,3]
    consumers:  [A,B]

    A -> [0,1]
    B -> [2,3]

Khi nhiều topic, range từng topic có thể làm một consumer nhận nhiều partition hơn.

### 5.2 RoundRobinAssignor

RoundRobin đi qua các partition và phân phối tuần tự cho các consumer.

Ưu điểm:

- thường cân bằng hơn khi nhiều topic và subscription giống nhau.

Điểm cần chú ý:

- nếu subscription khác nhau, kết quả có thể không cân bằng;
- assignment thay đổi có thể gây revoke nhiều hơn.

### 5.3 StickyAssignor

Sticky cố gắng:

- cân bằng partition;
- giữ assignment cũ nếu có thể;
- giảm di chuyển partition khi group thay đổi.

Điều này giúp giảm cache/state movement và giảm interruption.

### 5.4 CooperativeStickyAssignor

CooperativeSticky kết hợp mục tiêu sticky với incremental cooperative rebalance.

Phù hợp khi muốn:

- giữ assignment ổn định;
- giảm partition revoke toàn bộ;
- rolling deployment ít gián đoạn hơn.

### 5.5 Chọn assignor

Không có assignor luôn tốt nhất. Đánh giá theo:

- số topic;
- subscription có giống nhau không;
- số partition;
- state local của consumer;
- chi phí khởi tạo cache;
- thời gian rebalance;
- rolling deployment;
- yêu cầu tránh di chuyển partition.

Ghi rõ assignment strategy trong consumer config và runbook.

## 6. ConsumerRebalanceListener

### 6.1 Vì sao cần listener?

Listener cho phép application biết:

- partition sắp bị revoke;
- partition mới được assign;
- khi nào cần flush state;
- khi nào commit offset cuối cùng;
- khi nào khởi tạo state cho partition.

Ví dụ skeleton:

    consumer.subscribe(
        List.of("dev.orders.order-created.v1"),
        new ConsumerRebalanceListener() {
            @Override
            public void onPartitionsRevoked(
                Collection<TopicPartition> partitions
            ) {
                // Flush work an toàn và commit offset đã xử lý.
            }

            @Override
            public void onPartitionsAssigned(
                Collection<TopicPartition> partitions
            ) {
                // Load state, seek hoặc khởi tạo metrics.
            }
        }
    );

### 6.2 onPartitionsRevoked

Trước khi mất ownership:

- dừng nhận work mới từ partition đó;
- chờ hoặc hủy worker tương ứng theo policy;
- flush state local;
- commit offset của phần đã xử lý liên tục;
- giải phóng resource partition-specific.

Không commit offset mù cho mọi record đã poll nếu worker chưa xử lý xong.

### 6.3 onPartitionsAssigned

Khi nhận partition:

- đọc committed offset;
- load local state nếu cần;
- setup partition-specific resource;
- reset metric;
- seek có chủ đích nếu runbook yêu cầu;
- cho phép worker nhận record sau khi state sẵn sàng.

### 6.4 Listener không thay thế idempotency

Rebalance có thể xảy ra sau side effect nhưng trước commit. Listener giúp giảm rủi ro commit/ownership, nhưng consumer vẫn cần idempotency.

## 7. Rebalance loop

Rebalance loop là tình trạng group liên tục rebalance mà không ổn định.

Biểu hiện:

- log join group/leave group lặp lại;
- assignment thay đổi liên tục;
- lag tăng;
- throughput gần bằng 0;
- partition revoked/assigned liên tục;
- commit lỗi hoặc timeout;
- consumer group có member count dao động.

### 7.1 Nguyên nhân poll chậm

Nếu processing lâu hơn max.poll.interval.ms:

    poll
    process lâu
    không poll kịp
    coordinator/group coi member không tiến triển
    rebalance

Khắc phục:

- giảm max.poll.records;
- tối ưu process;
- tăng max.poll.interval.ms có kiểm soát;
- đưa processing nặng ra worker pool;
- pause partition khi queue nội bộ đầy;
- chia workload.

### 7.2 Nguyên nhân crash hoặc exception

Consumer thread chết do exception không được xử lý. Group loại member, assignment chuyển sang member khác, rồi process supervisor khởi động lại.

Cần:

- catch exception ở boundary;
- log stack trace và partition/offset;
- health check;
- restart policy có backoff;
- không retry poison record vô hạn trong poll loop.

### 7.3 Nguyên nhân network/session

- broker unreachable;
- DNS chập chờn;
- firewall;
- session timeout quá ngắn;
- coordinator failover;
- CPU starvation;
- container bị pause.

Đừng chỉ tăng timeout trước khi kiểm tra network và resource.

### 7.4 Nguyên nhân rolling deployment

Nếu nhiều instance restart đồng thời:

- cả group mất nhiều member;
- assignment revoke/assign liên tục;
- lag tăng;
- downstream bị burst khi group phục hồi.

Dùng rolling strategy, cooperative assignor và graceful shutdown để giảm tác động.

## 8. Thread safety của KafkaConsumer

KafkaConsumer không được dùng đồng thời từ nhiều thread.

Không làm:

    Thread A: consumer.poll()
    Thread B: consumer.commitSync()
    Thread C: consumer.seek()

Các lời gọi concurrent có thể gây IllegalStateException, race condition, assignment sai hoặc commit không như mong đợi.

### 8.1 Rule an toàn

Thông thường:

- một consumer instance;
- một consumer thread sở hữu poll, commit, pause, resume, seek;
- worker thread chỉ xử lý dữ liệu được giao;
- worker không gọi KafkaConsumer trực tiếp.

Nếu cần ra lệnh cho consumer thread, dùng queue/volatile signal và để consumer thread thực hiện thao tác Kafka.

### 8.2 wakeup

KafkaConsumer có wakeup để đánh thức poll từ thread khác trong lúc shutdown. Pattern:

    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
        consumer.wakeup();
    }));

Consumer thread xử lý WakeupException rồi đóng:

    try {
        while (running) {
            consumer.poll(Duration.ofMillis(500));
        }
    } catch (WakeupException e) {
        if (running) {
            throw e;
        }
    } finally {
        consumer.close();
    }

wakeup không phải lỗi business và không nên bị log như incident nếu do shutdown có chủ đích.

## 9. Mô hình consumer thread + worker pool

### 9.1 Kiến trúc

    Consumer thread
        |
        | poll
        v
    Per-partition queues
        |
        +--> Worker 1
        +--> Worker 2
        +--> Worker 3
        |
        v
    Process result
        |
        v
    Consumer thread commit

Consumer thread là owner của KafkaConsumer. Worker chỉ nhận task và trả kết quả.

### 9.2 Vì sao dùng worker pool?

- processing I/O-bound;
- downstream chậm;
- cần tận dụng CPU;
- một consumer thread không đủ throughput;
- vẫn muốn giữ một owner cho poll/commit.

### 9.3 Rủi ro

- worker hoàn thành out-of-order;
- record sau cùng partition hoàn thành trước record trước;
- commit offset vượt qua record chưa xử lý;
- queue nội bộ đầy làm memory tăng;
- partition revoke trong khi worker còn task;
- shutdown làm task chưa hoàn tất;
- side effect duplicate khi task retry.

## 10. Commit ordering với worker pool

Giả sử partition 0 nhận:

    offset 10 -> task A, xử lý 5 giây
    offset 11 -> task B, xử lý 1 giây

B hoàn thành trước A. Nếu commit offset 12 sau khi B xong:

    committed offset = 12
    offset 10 chưa xử lý xong

Nếu process chết, offset 10 có thể bị bỏ qua. Đây là lỗi commit vượt qua “contiguous processed offset”.

### 10.1 Contiguous commit

Chỉ commit offset tiếp theo sau chuỗi liên tục đã xử lý thành công:

    offset 10 done
    offset 11 done
    commit 12

Nếu offset 11 done nhưng offset 10 chưa done:

    không commit vượt qua 10

Cần một tracker per partition:

    partition 0:
      10 = done
      11 = done
      12 = not received

    safe commit = 12

Nếu 10 chưa done:

    10 = pending
    11 = done
    safe commit = 10

Commit offset 10 nghĩa là record 10 vẫn là record tiếp theo cần đọc.

### 10.2 Per-partition serial processing

Cách đơn giản nhất để giữ thứ tự là mỗi partition có một queue/worker tuần tự:

    partition 0 -> worker P0
    partition 1 -> worker P1
    partition 2 -> worker P2

Có thể xử lý song song giữa các partition nhưng tuần tự trong từng partition.

Ưu điểm:

- dễ reasoning;
- commit offset theo thứ tự;
- giữ ordering trong partition.

Nhược điểm:

- một record chậm chặn cả partition;
- số worker hữu ích bị giới hạn bởi partition;
- hot partition vẫn là bottleneck.

### 10.3 Out-of-order processing có tracker

Nếu cho phép xử lý song song trong cùng partition, cần:

- task state theo offset;
- contiguous completion tracker;
- giới hạn số task in-flight;
- xử lý failure của task trước;
- không commit vượt qua gap;
- quy định retry và poison record.

Độ phức tạp tăng mạnh. Chỉ dùng nếu business cho phép side effect out-of-order hoặc có sequence reconciliation.

## 11. Backpressure và worker queue

Worker pool phải có giới hạn:

    poll -> bounded queue -> workers

Không dùng queue vô hạn để che downstream chậm. Queue vô hạn có thể gây:

- memory exhaustion;
- GC pressure;
- shutdown kéo dài;
- lag không rõ vì record đã nằm trong memory nhưng chưa xử lý;
- duplicate lớn khi process chết.

Khi queue gần đầy:

1. pause partition tương ứng;
2. tiếp tục poll để duy trì membership;
3. theo dõi worker completion;
4. resume khi queue giảm;
5. alert nếu paused quá lâu.

Backpressure cần theo partition nếu muốn tránh một partition chậm làm ảnh hưởng toàn group.

## 12. Rebalance với worker pool

Khi onPartitionsRevoked:

1. đánh dấu partition không nhận task mới;
2. chờ task đang chạy hoàn tất trong deadline;
3. ghi nhận task thành công/thất bại;
4. tính contiguous processed offset;
5. commit offset an toàn;
6. hủy hoặc đưa task còn lại vào retry policy;
7. xóa queue và state partition.

Nếu không chờ task, partition có thể được giao cho consumer khác trong khi task cũ vẫn tạo side effect. Điều này có thể tạo duplicate hoặc out-of-order.

Trade-off khi task quá chậm:

- chờ lâu để giảm duplicate;
- revoke nhanh và chấp nhận reprocessing;
- ghi trạng thái task bền vững;
- dùng idempotency để cho phép retry.

Không có lựa chọn miễn phí; phải định nghĩa SLA shutdown/rebalance.

## 13. Static membership

Static membership dùng group.instance.id ổn định cho một instance để giảm rebalance không cần thiết khi process restart nhanh.

Khái niệm:

    instance-A -> group.instance.id=payment-1
    instance-B -> group.instance.id=payment-2

Khi payment-1 restart trong khoảng timeout phù hợp, group có thể giữ membership/assignment tốt hơn thay vì coi đây là member hoàn toàn mới.

Rủi ro:

- hai process dùng cùng group.instance.id;
- instance chết thật nhưng group giữ membership quá lâu;
- deployment thay đổi identity sai;
- scaling động cần quản lý ID cẩn thận.

Static membership không thay thế graceful shutdown, health check hoặc xử lý crash.

## 14. Cooperative rebalance và rollout

Khi triển khai version mới:

1. xác nhận mọi member dùng assignor tương thích;
2. rollout từng instance;
3. log revoked/assigned;
4. đo rebalance duration;
5. theo dõi lag và throughput;
6. rollback nếu group không ổn định.

Không đổi assignor và code xử lý revoke cùng lúc mà không có test group integration. Rebalance behavior có thể thay đổi theo version Kafka client.

## 15. Ví dụ consumer có listener

    ConsumerRebalanceListener listener =
        new ConsumerRebalanceListener() {
            @Override
            public void onPartitionsRevoked(
                Collection<TopicPartition> partitions
            ) {
                for (TopicPartition partition : partitions) {
                    stopNewTasks(partition);
                    waitForSafeCompletion(partition);
                }

                commitContiguousOffsets(partitions);
            }

            @Override
            public void onPartitionsAssigned(
                Collection<TopicPartition> partitions
            ) {
                for (TopicPartition partition : partitions) {
                    initializePartitionState(partition);
                }
            }
        };

    consumer.subscribe(
        List.of("dev.orders.order-created.v1"),
        listener
    );

Tên hàm minh họa, không phải API hoàn chỉnh. Ý chính là ownership chuyển giao phải gắn với state và offset.

## 16. Ví dụ worker pool có bounded queue

    ExecutorService workers =
        Executors.newFixedThreadPool(4);

    BlockingQueue<WorkItem> queue =
        new ArrayBlockingQueue<>(1000);

    while (running) {
        ConsumerRecords<String, String> records =
            consumer.poll(Duration.ofMillis(200));

        for (ConsumerRecord<String, String> record : records) {
            if (!queue.offer(new WorkItem(record),
                             100, TimeUnit.MILLISECONDS)) {
                consumer.pause(Set.of(
                    new TopicPartition(record.topic(), record.partition())
                ));
                break;
            }
        }

        drainCompletedTasks();
        commitOnlyContiguousOffsets();
    }

Điểm cần cải thiện khi đưa vào production:

- queue theo partition;
- không pause dựa trên record đang loop mà không tracking assignment;
- không commit trước completion;
- xử lý exception worker;
- shutdown deadline;
- metrics queue depth;
- task cancellation;
- rebalance listener;
- idempotency.

## 17. Bài lab 1 — Log revoked và assigned

1. Viết consumer có ConsumerRebalanceListener.
2. Log:
   - group id;
   - member/client id;
   - topic;
   - partition;
   - timestamp;
   - current committed offset.
3. Chạy một instance.
4. Thêm instance thứ hai.
5. Dừng một instance.
6. Tăng partition topic trong môi trường lab nếu cần.
7. Đối chiếu assignment trước/sau.

Báo cáo:

- event nào kích hoạt rebalance;
- partition nào bị revoke;
- partition nào được assign;
- thời gian từ revoke đến assignment;
- lag thay đổi ra sao.

## 18. Bài lab 2 — Rolling deployment và rebalance duration

Tạo ít nhất ba consumer instance cùng group.

Thực hiện:

1. ghi throughput/lag baseline;
2. restart lần lượt từng instance;
3. đo thời gian group ổn định;
4. ghi số lần revoke/assign;
5. thử eager assignor;
6. thử cooperative assignor nếu client/broker hỗ trợ;
7. so sánh lag và throughput.

Kết luận cần có:

- assignment có di chuyển bao nhiêu partition;
- thời gian gián đoạn;
- consumer có bị duplicate không;
- shutdown có commit an toàn không.

## 19. Bài lab 3 — Tạo processing chậm vượt max.poll.interval.ms

1. Cấu hình max.poll.interval.ms nhỏ trong lab.
2. Poll một batch.
3. Sleep lâu hơn interval.
4. Quan sát log group/rebalance.
5. Dùng group describe xem member/assignment.
6. Giảm max.poll.records.
7. Tăng interval hợp lý.
8. So sánh kết quả.

Không chỉ sửa bằng cách tăng timeout. Hãy ghi rõ processing p95/p99 và lý do chọn giá trị mới.

## 20. Bài lab 4 — So sánh assignor

Với cùng topic/group:

1. chạy RangeAssignor;
2. ghi assignment;
3. chạy RoundRobinAssignor;
4. ghi assignment;
5. chạy StickyAssignor;
6. thử CooperativeStickyAssignor nếu phù hợp;
7. thêm/bớt consumer;
8. đo số partition bị chuyển.

Bài lab cần dùng ít nhất hai topic hoặc số partition không chia đều để thấy khác biệt.

## 21. Bài lab 5 — Worker pool và commit ordering

Tạo topic 2 partition. Gửi record sao cho:

- offset nhỏ xử lý chậm;
- offset lớn xử lý nhanh.

Thực hiện hai phiên bản:

### Phiên bản sai

- mọi task gửi vào pool;
- task nào xong commit offset của task đó.

Quan sát khả năng commit vượt qua offset đang pending.

### Phiên bản đúng cơ bản

- mỗi partition một queue tuần tự;
- hoặc tracker contiguous offset;
- chỉ commit offset tiếp theo sau đoạn liên tục đã hoàn tất.

Kill process giữa các task và kiểm tra duplicate/replay.

## 22. Bài lab 6 — Pause partition khi downstream chậm

1. Tạo downstream giả lập sleep.
2. Đặt bounded queue nhỏ.
3. Khi queue đầy, pause partition.
4. Vẫn gọi poll.
5. Khi queue giảm, resume.
6. Theo dõi lag riêng partition paused.
7. Đặt timeout để alert nếu paused quá lâu.

Đừng dùng pause để che permanent failure. Nếu downstream chết lâu, cần circuit breaker, retry/DLT hoặc cảnh báo vận hành.

## 23. Chẩn đoán rebalance loop

### Dấu hiệu

- revoke/assign lặp lại;
- group member count thay đổi;
- consumer không kịp poll;
- lag tăng nhanh;
- callback commit lỗi;
- CPU cao hoặc GC pause;
- network timeout.

### Quy trình

1. xem consumer logs theo timestamp;
2. xem group describe;
3. kiểm tra max.poll.interval.ms;
4. đo processing time;
5. kiểm tra session timeout/heartbeat;
6. xem crash/exception;
7. kiểm tra broker coordinator;
8. kiểm tra network/DNS;
9. kiểm tra deployment restart;
10. kiểm tra partition/consumer ratio;
11. giảm batch hoặc pause downstream;
12. thử lại với một consumer tối giản không có business processing.

Dùng consumer tối giản để phân biệt lỗi Kafka/group với lỗi application processing.

## 24. Sai lầm phổ biến

### Dùng KafkaConsumer từ nhiều thread

KafkaConsumer không thread-safe. Chỉ consumer owner thread được gọi poll/commit/seek/pause/resume.

### Worker tự commit

Worker hoàn thành out-of-order có thể commit vượt qua task chưa xong.

### Queue worker vô hạn

Downstream chậm sẽ biến memory thành backlog không kiểm soát.

### Chờ worker vô thời hạn khi revoke

Rebalance bị kẹt, group lag tăng và deployment không hoàn tất.

### Revoke nhưng không flush state

Partition mới có thể bắt đầu từ offset/state không nhất quán.

### Commit toàn bộ batch trong listener

Một record lỗi có thể bị bỏ qua cùng các record còn lại.

### Tăng max.poll.interval.ms để che bug

Chỉ làm failure detection chậm hơn.

### Dùng assignor khác nhau không kế hoạch

Assignment và rebalance behavior không ổn định trong rollout.

### Nhầm pause với stop poll

Paused consumer vẫn phải poll để giữ membership.

### Không có idempotency ở worker

Rebalance và crash hợp lệ vẫn có thể gây duplicate side effect.

## 25. Câu hỏi tự kiểm tra

1. Rebalance xảy ra khi nào?
2. Vì sao thêm consumer làm assignment thay đổi?
3. Eager và cooperative rebalance khác nhau thế nào?
4. Range, RoundRobin, Sticky và CooperativeSticky khác nhau ra sao?
5. onPartitionsRevoked cần làm gì?
6. Vì sao onPartitionsAssigned có thể cần load state?
7. Rebalance loop có dấu hiệu nào?
8. Tại sao KafkaConsumer không dùng được đồng thời từ nhiều thread?
9. Worker pool tạo rủi ro commit gì?
10. Contiguous processed offset là gì?
11. Vì sao mỗi partition một worker dễ reasoning hơn?
12. Pause có giải quyết được downstream chết vĩnh viễn không?
13. Static membership giúp gì?
14. Vì sao duplicate vẫn có thể xảy ra dù rebalance listener đúng?
15. Khi nào tăng max.poll.interval.ms là hợp lý?
16. Graceful shutdown cần làm gì trước khi close?
17. Vì sao queue bounded tốt hơn queue vô hạn?
18. Làm sao phân biệt lỗi group với lỗi business processing?

### Đáp án ngắn

1. Join/leave/crash, timeout, topic hoặc subscription thay đổi.
2. Group cần phân chia partition lại cho member mới.
3. Eager revoke rộng; cooperative di chuyển tăng dần phần cần thiết.
4. Khác cách cân bằng, giữ assignment và mức di chuyển partition.
5. Dừng task mới, flush state, commit offset an toàn.
6. Khôi phục state/cache và chuẩn bị ownership mới.
7. Revoke/assign lặp, lag tăng, throughput giảm.
8. API không thread-safe và ownership state dễ race.
9. Worker hoàn thành out-of-order có thể commit vượt gap.
10. Offset tiếp theo sau chuỗi record liên tục đã xử lý xong.
11. Giữ ordering và commit sequence đơn giản.
12. Không; cần retry, DLT, circuit breaker hoặc alert.
13. Giảm rebalance khi instance restart nhanh và identity ổn định.
14. Crash/retry/side effect có thể xảy ra trước commit.
15. Khi processing hợp lệ thực sự dài và đã đo p95/p99.
16. Dừng work mới, hoàn tất phần an toàn, commit đúng rồi close.
17. Giới hạn memory và tạo backpressure rõ.
18. Chạy consumer tối giản, xem logs/metrics group và đo processing.

## 26. Checklist hoàn thành Tuần 6

- [ ] Tôi giải thích được rebalance và các trigger.
- [ ] Tôi phân biệt eager với cooperative rebalance.
- [ ] Tôi biết mục đích của các assignment strategy.
- [ ] Tôi log được revoked/assigned partition.
- [ ] Tôi chẩn đoán được rebalance loop.
- [ ] Tôi không gọi KafkaConsumer từ worker thread.
- [ ] Tôi hiểu mô hình consumer thread + worker pool.
- [ ] Tôi biết rủi ro commit out-of-order.
- [ ] Tôi có contiguous offset tracker hoặc per-partition serial processing.
- [ ] Tôi dùng bounded queue cho backpressure.
- [ ] Tôi biết pause/resume mà vẫn poll.
- [ ] Tôi hiểu static membership ở mức triển khai.
- [ ] Tôi có graceful shutdown khi partition bị revoke.
- [ ] Tôi đo được rebalance duration trong rolling deployment.
- [ ] Tôi thử xử lý vượt max.poll.interval.ms.
- [ ] Tôi có idempotency cho side effect worker.

## 27. Câu chốt cần nhớ

Rebalance là một phần bình thường của consumer group, không phải luôn là sự cố. Vấn đề là hệ thống phải chuyển ownership partition mà không làm mất offset, không commit vượt qua record chưa xử lý và không tạo duplicate ngoài khả năng kiểm soát.

Năm ý cần nhớ:

1. KafkaConsumer chỉ nên được sở hữu bởi một consumer thread.
2. Worker pool phải trả kết quả về consumer thread để commit có thứ tự.
3. Chỉ commit offset sau chuỗi record liên tục đã xử lý an toàn.
4. Rebalance listener là nơi flush state và bàn giao ownership.
5. Cooperative rebalance, bounded queue, pause/resume và idempotency cùng nhau tạo nền tảng xử lý song song an toàn.


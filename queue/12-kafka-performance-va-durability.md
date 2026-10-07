# Tuần 12: Kafka performance và durability

## 1. Mục tiêu của tuần

Sau bài học này, bạn có thể:

- Giải thích batch size, linger và compression ảnh hưởng thế nào đến throughput, latency và CPU.
- Phân biệt acks 0, 1 và all.
- Thiết kế producer có idempotence, retry và giới hạn in-flight requests hợp lý.
- Điều chỉnh fetch size, max poll records và max poll interval cho consumer xử lý chậm.
- Đọc consumer lag và phân biệt lag do thiếu capacity với lag do partition nóng.
- Thiết kế retention theo thời gian, retention theo kích thước và log compaction.
- Chọn replication factor và min.insync.replicas để cân bằng durability với availability.
- Đo bằng benchmark thay vì đoán cấu hình.
- Biết rằng Kafka không biến một nghiệp vụ non-idempotent thành idempotent chỉ bằng một vài producer config.

Phạm vi bài này tập trung vào Kafka producer, broker và consumer. Các ví dụ dùng Spring Kafka nhưng nguyên lý vẫn áp dụng cho Kafka client thuần.

---

## 2. Performance và durability là hai bài toán khác nhau

Kafka performance thường được nhìn qua các chỉ số:

- Throughput: records/second hoặc bytes/second.
- Producer latency: thời gian từ lúc ứng dụng gửi đến lúc nhận được kết quả.
- End-to-end latency: thời gian từ lúc phát sinh event đến lúc consumer xử lý xong.
- Consumer processing rate: số record consumer hoàn thành mỗi giây.
- CPU, memory, network và disk I/O.
- Consumer lag.

Kafka durability thường được nhìn qua:

- Record có được ghi bền vững qua nhiều broker hay chưa.
- Producer có nhận lỗi khi cluster không đủ replica đồng bộ hay không.
- Có thể mất record khi broker chết hay không.
- Khi retry, record có bị duplicate hoặc reorder hay không.
- Retention có giữ record đủ lâu để replay hay không.
- Compaction có giữ lại state mới nhất theo key hay không.

Một cấu hình nhanh nhất chưa chắc là cấu hình tốt nhất. Ví dụ:

- acks=0 giảm latency nhưng producer không biết broker có nhận record hay chưa.
- acks=all tăng độ an toàn nhưng phải chờ ISR.
- compression giảm network và disk nhưng tăng CPU.
- batch lớn tăng throughput nhưng có thể tăng latency chờ batch đầy.
- max.poll.records lớn tăng số record xử lý mỗi poll nhưng làm processing time dài hơn và dễ vượt max.poll.interval.ms.

Hãy luôn trả lời ba câu hỏi:

1. Hệ thống cần nhanh đến mức nào?
2. Hệ thống được phép mất hoặc duplicate record ở mức nào?
3. Khi consumer hoặc broker chậm, hệ thống phải backpressure, retry hay hy sinh dữ liệu?

---

## 3. Đo trước khi tuning

Không nên bắt đầu bằng việc tăng hàng loạt config. Trước tiên hãy ghi lại workload:

- Số record mỗi giây.
- Kích thước record trung bình và p95.
- Số partition.
- Số producer instance.
- Số consumer instance.
- Tỉ lệ key có bị dồn vào một số partition không.
- Thời gian xử lý trung bình và p95 của consumer.
- Mức latency chấp nhận được.
- Cửa sổ replay cần có.
- Mức mất dữ liệu chấp nhận được.

Các nhóm metric nên quan sát:

Producer:

- record-send-rate.
- byte-rate.
- record-error-rate.
- record-retry-rate.
- request-latency-avg và request-latency-max.
- batch-size-avg.
- batch-split-rate.
- bufferpool-wait-time.
- compression-rate.

Broker:

- BytesInPerSec và BytesOutPerSec.
- MessagesInPerSec.
- Request latency.
- Request queue time.
- Network processor idle.
- Request handler idle.
- UnderReplicatedPartitions.
- OfflinePartitionsCount.
- ISR shrink và ISR expand.
- Disk utilization và disk latency.

Consumer:

- records-consumed-rate.
- bytes-consumed-rate.
- fetch-latency.
- records-lag-max.
- current offset.
- log end offset.
- rebalance count.
- thời gian xử lý mỗi poll.

Một benchmark có ý nghĩa phải cố định các biến quan trọng:

- Cùng payload.
- Cùng số partition.
- Cùng số producer và consumer.
- Cùng replication factor.
- Cùng thời gian chạy.
- Có warm-up.
- Đo p50, p95, p99 thay vì chỉ đo trung bình.
- Chạy đủ lâu để thấy batch, retry và compaction behavior.

---

## 4. Producer batching: batch.size

Producer không gửi từng record độc lập ngay lập tức trong mọi trường hợp. Với mỗi partition, producer gom record vào batch trước khi gửi request.

batch.size là giới hạn kích thước batch mục tiêu cho từng partition. Nó không phải kích thước request tổng của toàn producer.

Batch nhỏ:

- Record thường được gửi sớm hơn.
- Có thể giảm latency trong tải thấp.
- Tăng số request.
- Tăng overhead network và CPU.
- Compression kém hiệu quả hơn vì mỗi batch có ít dữ liệu.

Batch lớn:

- Có nhiều dữ liệu để nén cùng nhau.
- Giảm số request.
- Có thể tăng throughput.
- Có thể làm một số record chờ lâu hơn nếu tải thấp.
- Tăng memory cần thiết khi có nhiều partition và nhiều producer in-flight.

Cần nhớ:

- batch.size là giới hạn, không phải lời hứa producer luôn chờ cho đủ batch.
- Nếu batch đầy, producer gửi ngay.
- Nếu batch chưa đầy, linger.ms và các điều kiện khác quyết định thời điểm gửi.
- Một producer có nhiều partition có thể giữ nhiều batch đồng thời.

Cách tuning thực tế:

1. Chạy baseline với cấu hình mặc định.
2. Tăng batch.size theo từng bước, chẳng hạn 16 KiB, 32 KiB, 64 KiB.
3. Ghi nhận batch-size-avg, throughput, p95 latency và memory.
4. Dừng tăng khi batch thực tế không lớn lên hoặc latency tăng không đáng giá.

Không nên đặt batch.size rất lớn chỉ vì muốn tối đa throughput. Nếu tải phân tán trên nhiều partition, tổng memory cho các batch đang chờ có thể tăng đáng kể.

---

## 5. linger.ms

linger.ms là thời gian producer có thể chờ thêm record để tạo batch tốt hơn trước khi gửi request. Đây là một delay có chủ đích để đổi latency lấy batching.

linger thấp:

- Giảm thời gian chờ.
- Phù hợp với command cần phản hồi nhanh.
- Có thể tạo nhiều batch nhỏ.
- Compression và network efficiency kém hơn.

linger cao:

- Tăng cơ hội gom batch.
- Có thể tăng throughput và compression ratio.
- Tăng producer latency trong tải thấp.
- Có thể làm p99 latency xấu hơn khi traffic không đều.

Trong Kafka 4.0, giá trị mặc định của linger.ms đã được thay đổi thành 5 ms. Không nên coi con số này là quy tắc cho mọi hệ thống; hãy kiểm tra version client và workload thật.

Một cách hiểu hữu ích:

- batch.size trả lời: batch được phép lớn đến đâu?
- linger.ms trả lời: producer được phép đợi thêm bao lâu?
- compression.type trả lời: batch được mã hóa nén như thế nào?

Ví dụ profile:

| Profile | batch.size | linger.ms | Nhận xét |
|---|---:|---:|---|
| Low latency | nhỏ hoặc vừa | thấp | Ưu tiên phản hồi nhanh |
| High throughput | vừa hoặc lớn | vài ms đến vài chục ms | Ưu tiên batch đầy |
| Bursty traffic | vừa | vài ms | Gom burst ngắn |
| Large payload | vừa, theo payload thực tế | thấp đến vừa | Theo dõi request và memory |

Đừng dùng linger để chữa một producer thiếu partition, broker quá tải hoặc network bị nghẽn. Linger chỉ giúp batching; nó không tạo thêm capacity.

---

## 6. Compression

Kafka hỗ trợ các codec phổ biến:

- none.
- gzip.
- snappy.
- lz4.
- zstd.

Compression được thực hiện trên batch. Vì vậy batch tốt thường giúp compression tốt hơn record-by-record.

Trade-off chính:

| Codec | CPU thường thấy | Compression thường thấy | Khi cân nhắc |
|---|---:|---:|---|
| none | thấp | không có | Payload nhỏ, network dư |
| lz4 | thấp đến vừa | tốt | Cân bằng latency và throughput |
| snappy | thấp đến vừa | vừa | Workload cần codec quen thuộc |
| gzip | cao hơn | tốt | Ưu tiên tiết kiệm bandwidth |
| zstd | vừa đến cao | rất tốt | Payload lớn, muốn giảm storage/network |

Đây là định hướng, không phải thứ hạng tuyệt đối. Payload JSON nhiều text có thể nén khác hoàn toàn payload đã nén, ảnh hoặc dữ liệu random.

Compression ảnh hưởng:

- Producer CPU.
- Broker network.
- Broker disk.
- Consumer CPU.
- End-to-end latency.
- Chi phí lưu trữ và truyền dữ liệu.

Nếu CPU producer đang bão hòa, codec mạnh hơn có thể làm throughput giảm dù network giảm. Nếu network là bottleneck, compression có thể tăng throughput tổng thể.

Lab nên so sánh cùng một payload và cùng cùng một workload:

- none.
- lz4.
- snappy.
- gzip.
- zstd.

Ghi lại:

- MB/s.
- records/s.
- producer p95 latency.
- CPU producer.
- CPU broker.
- network bytes.
- disk bytes.
- consumer processing rate.

---

## 7. acks: 0, 1 và all

acks quyết định producer chờ mức xác nhận nào từ broker.

### acks=0

Producer không chờ broker trả acknowledgement.

Ưu điểm:

- Latency thấp.
- Có thể đạt throughput cao nếu network nhanh.
- Producer ít bị ảnh hưởng bởi broker response.

Rủi ro:

- Producer không biết request đã tới broker hay chưa.
- Lỗi network có thể làm mất record mà ứng dụng không biết.
- Không phù hợp cho payment, order, inventory hoặc audit event nếu không có cơ chế bổ sung.

### acks=1

Leader ghi record rồi trả acknowledgement. Follower có thể chưa replicate xong.

Ưu điểm:

- Cân bằng tốt hơn acks=0.
- Latency thường thấp hơn acks=all.
- Leader failure ngay sau acknowledgement vẫn có thể làm mất record nếu follower chưa bắt kịp.

Rủi ro:

- Không bảo vệ tốt trước lỗi leader và mất dữ liệu chưa replicate.

### acks=all

Leader chờ tất cả replica trong ISR xác nhận record theo policy của cluster.

Ưu điểm:

- Mức durability cao nhất trong ba lựa chọn.
- Kết hợp với replication factor và min.insync.replicas để tránh ghi khi cluster không còn đủ replica an toàn.

Rủi ro:

- Latency phụ thuộc vào follower chậm nhất trong ISR.
- Nếu ISR giảm dưới min.insync.replicas, producer có thể nhận lỗi.
- Cần có retry và xử lý lỗi đúng.

acks=all không có nghĩa là record được lưu vĩnh viễn bất chấp mọi failure. Durability còn phụ thuộc vào:

- replication factor.
- min.insync.replicas.
- ISR thực tế.
- broker và disk failure.
- cấu hình unclean leader election.
- retention.
- khả năng producer retry và ứng dụng xử lý lỗi.

---

## 8. Replication factor, ISR và min.insync.replicas

Replication factor là số bản sao của mỗi partition trên các broker.

Ví dụ:

- replication factor = 3.
- Có một leader và hai follower.
- ISR là tập replica đang theo kịp leader và đủ điều kiện nhận record.

min.insync.replicas đặt số replica tối thiểu phải đang in-sync để broker chấp nhận ghi khi producer dùng acks=all.

Baseline thường dùng cho event quan trọng:

- replication factor = 3.
- min.insync.replicas = 2.
- acks = all.

Ý nghĩa:

- Khi cả ba replica khỏe, ghi bình thường.
- Khi một replica hỏng, còn hai ISR, hệ thống vẫn ghi.
- Khi chỉ còn một ISR, broker từ chối ghi mới thay vì giả vờ rằng durability vẫn được đảm bảo.

Đây là một trade-off rõ ràng:

- min.insync.replicas thấp hơn: availability ghi cao hơn, durability thấp hơn.
- min.insync.replicas cao hơn: durability cao hơn, dễ bị từ chối ghi khi broker lỗi.

Ví dụ failure model:

1. Partition có ba replica.
2. Một follower mất kết nối và rời ISR.
3. ISR còn leader và một follower.
4. Với min ISR bằng 2, acks=all vẫn có thể ghi.
5. Nếu thêm một broker nữa lỗi và ISR chỉ còn 1, ghi mới thất bại.
6. Producer phải retry hoặc phát lỗi để hệ thống upstream xử lý.

Đừng chỉ nhìn replication factor trên giấy. Hãy theo dõi:

- UnderReplicatedPartitions.
- ISR shrink.
- Offline partition.
- Disk đầy.
- Follower fetch latency.

---

## 9. Idempotent producer

Idempotent producer giúp Kafka loại bỏ duplicate do producer retry trong một số failure scenario. Producer dùng identity và sequence của session để broker nhận biết request lặp lại.

Một tình huống điển hình:

1. Producer gửi record.
2. Broker ghi record thành công.
3. Response bị mất trên network.
4. Producer thấy timeout và retry.
5. Nếu không có idempotence, broker có thể ghi duplicate.
6. Nếu idempotence hoạt động đúng, broker nhận biết retry tương ứng và tránh append duplicate.

Idempotence không đồng nghĩa với exactly-once cho toàn bộ business workflow.

Nó không tự giải quyết:

- Consumer xử lý xong nhưng crash trước khi commit offset.
- Gửi email, gọi HTTP hoặc trừ tiền bị retry.
- Hai event hợp lệ có cùng business key.
- Duplicate được tạo từ hai lần gọi nghiệp vụ khác nhau.
- Database transaction và Kafka transaction không đồng bộ.

Các điều kiện tương thích quan trọng:

- enable.idempotence=true.
- acks=all.
- retries lớn hơn 0.
- max.in.flight.requests.per.connection không vượt quá 5.

Khi cấu hình xung đột, client có thể báo lỗi hoặc không bật idempotence theo hành vi của version. Hãy kiểm tra effective configuration trong môi trường chạy thật.

Thiết kế vẫn cần business idempotency:

- Mỗi command/event có eventId duy nhất.
- Database có unique constraint theo eventId hoặc business operation id.
- Consumer ghi inbox hoặc processed-event table.
- Side effect có idempotency key.
- Retry không tạo thêm kết quả nghiệp vụ.

---

## 10. Producer retries và in-flight requests

Producer retry giúp gửi lại request tạm thời thất bại. Các lỗi có thể retry gồm:

- Network timeout.
- Leader election.
- Not enough replicas trong thời gian ngắn.
- Broker tạm bận.
- Metadata chưa cập nhật.

Retry không nên được hiểu là “cứ gửi lại vô hạn”. Cần giới hạn bằng:

- delivery.timeout.ms: tổng thời gian chờ thành công hoặc lỗi.
- request.timeout.ms: timeout của từng request.
- retries: số lần retry nếu version/client còn dùng tham số này trực tiếp.
- backoff giữa các lần retry.

max.in.flight.requests.per.connection là số request chưa nhận response mà một connection có thể có.

Nếu idempotence tắt, retries bật và max.in.flight lớn hơn 1:

1. Request A gửi trước nhưng lỗi tạm thời.
2. Request B gửi sau và thành công.
3. Request A retry và thành công sau.
4. Record B có thể được ghi trước record A.

Nếu ordering theo partition quan trọng:

- Bật idempotence.
- Giữ max.in.flight trong giới hạn hỗ trợ.
- Không tạo nhiều producer tùy tiện cho cùng ordering key.
- Hiểu rằng ordering chỉ có ý nghĩa trong một partition.
- Không retry một record ở application layer theo cách phá ordering.

Một retry policy tốt phải trả lời:

- Lỗi nào retry được?
- Retry trong bao lâu?
- Khi hết thời gian thì ghi log, DLQ hay dừng pipeline?
- Producer callback trả lỗi cho ai?
- Có metric để phân biệt retryable và non-retryable không?

---

## 11. Producer buffer và backpressure

Producer giữ record trong buffer trước khi gửi. Khi broker chậm hoặc consumer của broker không theo kịp, buffer có thể đầy.

Dấu hiệu thường gặp:

- bufferpool-wait-time tăng.
- send call bắt đầu block.
- delivery timeout tăng.
- application thread bị chặn.
- memory pressure nếu producer giữ quá nhiều dữ liệu ở tầng ứng dụng.

Các config cần hiểu:

- buffer.memory: dung lượng buffer phía producer.
- max.block.ms: thời gian các lệnh send có thể block khi chờ metadata hoặc buffer.
- delivery.timeout.ms: tổng thời gian chờ delivery thành công.

Tăng buffer không làm throughput vô hạn. Nó chỉ trì hoãn thời điểm backpressure xuất hiện và có thể làm latency queueing tăng.

Ứng dụng nên có:

- giới hạn tốc độ phát sinh event.
- timeout rõ ràng.
- metric về queueing time.
- circuit breaker hoặc load shedding với workload không quan trọng.
- chiến lược khi Kafka không khả dụng.
- outbox nếu không được phép mất event sau commit database.

---

## 12. Consumer fetch size

Consumer lấy dữ liệu theo fetch request, không phải mỗi lần poll đều gửi một request cho từng record.

Các config quan trọng:

### fetch.min.bytes

Lượng dữ liệu tối thiểu broker cố gắng trả về cho fetch request.

- Giá trị thấp: phản hồi sớm hơn, latency thấp hơn.
- Giá trị cao: có thể giảm request và tăng throughput khi traffic đủ lớn.
- Nếu traffic thấp, giá trị cao có thể làm consumer chờ lâu.

### fetch.max.wait.ms

Thời gian broker tối đa chờ để đạt fetch.min.bytes trước khi trả response.

fetch.min.bytes và fetch.max.wait.ms nên được xem cùng nhau. Tăng fetch.min.bytes mà không xem fetch.max.wait.ms có thể làm latency khó đoán.

### max.partition.fetch.bytes

Giới hạn dữ liệu broker trả cho mỗi partition trong một fetch.

Nếu một record hoặc batch lớn hơn giới hạn, Kafka vẫn có thể trả batch đầu tiên để consumer tiến bộ. Vì vậy giới hạn này phải tương thích với kích thước message lớn nhất và message.max.bytes phía broker/topic.

### fetch.max.bytes

Giới hạn tổng dữ liệu của một fetch response.

Đây là giới hạn tổng, còn max.partition.fetch.bytes là giới hạn theo partition. Nhiều partition trong một fetch có thể làm tổng response lớn.

Tuning theo mục tiêu:

- Low latency: fetch.min.bytes nhỏ, fetch.max.wait.ms thấp.
- High throughput: fetch.min.bytes lớn hơn, cho phép broker gom dữ liệu.
- Large message: kiểm tra giới hạn message ở producer, broker, topic và consumer.
- Memory constrained: tránh fetch quá lớn ở nhiều partition.

---

## 13. max.poll.records và max.poll.interval.ms

max.poll.records giới hạn số record được trả về trong một lần poll. Nó không phải giới hạn số record consumer fetch từ broker bên dưới; client vẫn có thể buffer dữ liệu đã fetch.

max.poll.interval.ms là thời gian tối đa giữa hai lần poll. Nếu application xử lý quá lâu và vượt khoảng này, consumer có thể bị coi là không responsive và group rebalance.

Quan hệ quan trọng:

- max.poll.records càng lớn, một lần xử lý càng lâu.
- thời gian xử lý một poll phải nhỏ hơn max.poll.interval.ms với margin an toàn.
- giảm max.poll.records giúp poll thường xuyên hơn.
- tăng max.poll.interval.ms có thể cho phép xử lý lâu hơn nhưng phát hiện consumer chết chậm hơn.

Cách ước lượng:

- processing time per record = p95 thời gian xử lý một record.
- batch processing time = processing time per record x max.poll.records.
- chọn max.poll.records sao cho batch processing time thấp hơn max.poll.interval.ms đáng kể.
- nếu processing time phân tán mạnh, dùng p99 và chừa margin cho GC, database và network.

Ví dụ:

- p95 xử lý một record là 50 ms.
- max.poll.records = 100.
- thời gian lý thuyết là 5 giây, nhưng thực tế có downstream call, retry và GC.
- không nên đặt max.poll.interval.ms đúng bằng 5 giây.
- hoặc giảm batch xuống, hoặc dùng worker model được kiểm soát, hoặc tăng interval có chủ đích.

Nếu xử lý song song trong listener:

- bảo đảm commit offset không xảy ra trước khi worker hoàn thành.
- quản lý thứ tự nếu partition ordering quan trọng.
- giới hạn số task để tránh làm nghẽn database.
- đo thời gian shutdown và rebalance.

---

## 14. Consumer lag

Consumer lag là khoảng cách giữa log end offset của partition và offset consumer đã xử lý hoặc commit.

Lag tăng khi:

- Producer ghi nhanh hơn consumer xử lý.
- Consumer bị chậm do database hoặc API downstream.
- Có partition nóng vì key phân bố lệch.
- Consumer instance bị chết.
- Rebalance diễn ra liên tục.
- max.poll.records quá lớn làm mỗi poll xử lý quá lâu.
- fetch hoặc network bị giới hạn.
- Broker disk hoặc network chậm.

Lag không phải lúc nào cũng là lỗi. Cần xem:

- lag tuyệt đối.
- tốc độ lag tăng.
- thời gian lag quy đổi thành phút hoặc giờ.
- lag theo từng partition.
- processing rate.
- incoming rate.
- oldest record age.
- rebalance count.

Cách đọc:

- Lag ổn định ở mức nhỏ: có thể bình thường.
- Lag tăng đều: capacity không đủ hoặc downstream đang chậm.
- Tổng lag vừa phải nhưng một partition rất cao: partition nóng hoặc consumer assignment lệch.
- Lag giảm đột ngột sau rebalance: cần kiểm tra commit và khả năng duplicate.
- Lag tăng cùng UnderReplicatedPartitions: broker-side issue có thể là nguyên nhân.

Công thức gần đúng:

- backlog records = log end offset - committed offset.
- catch-up time ≈ backlog / (consumer processing rate - incoming rate), nếu processing rate lớn hơn incoming rate.
- nếu processing rate nhỏ hơn incoming rate, lag sẽ tiếp tục tăng.

Đừng auto-scale chỉ theo tổng lag. Hãy kiểm tra số partition có thể phân phối và liệu bottleneck nằm ở CPU, DB, network hay partition key.

---

## 15. Retention theo time và size

Retention quyết định Kafka giữ record trong bao lâu hoặc đến tổng dung lượng nào.

Hai kiểu phổ biến:

- retention.ms: xóa record sau một khoảng thời gian.
- retention.bytes: giới hạn dung lượng log theo partition.

Nếu dùng cả hai, record có thể bị xóa khi một trong các điều kiện phù hợp với policy cleanup. Cần nhớ retention không phải backup:

- Record hết retention sẽ không replay được từ Kafka.
- Xóa nhầm topic hoặc mất nhiều broker không được khắc phục chỉ bằng retention.
- Backup và disaster recovery là bài toán riêng.

Retention có thể bị ảnh hưởng bởi segment:

- Kafka cleanup theo log segment, không phải từng record độc lập.
- Record có thể tồn tại lâu hơn thời gian lý thuyết một chút cho đến khi segment đủ điều kiện cleanup.
- Segment size và segment rolling ảnh hưởng thời điểm cleanup.

Chọn retention dựa trên:

- Cửa sổ consumer outage có thể chấp nhận.
- Thời gian phát hiện và xử lý incident.
- Nhu cầu replay.
- Chi phí disk.
- Quy định audit.
- Khả năng rebuild state từ source khác.

Ví dụ:

- Event operational: giữ 3 đến 7 ngày.
- Audit event: giữ lâu hơn hoặc đẩy sang storage dài hạn.
- Topic trung gian cho pipeline: retention ngắn hơn.
- Topic dùng replay điều tra: giữ đủ lâu để đội vận hành phản ứng.

---

## 16. Log compaction

Log compaction giữ lại giá trị mới nhất theo key thay vì giữ mọi phiên bản mãi mãi. Nó phù hợp với topic biểu diễn state hiện tại.

Ví dụ key là customerId:

- customer-42 -> GOLD.
- customer-42 -> PLATINUM.
- Sau compaction, bản PLATINUM mới nhất có thể được giữ lại.
- Consumer mới đọc topic có thể dựng state hiện tại mà không cần mọi thay đổi lịch sử.

Compaction phù hợp cho:

- Materialized view.
- Cache rebuild.
- Entity state.
- Configuration state.
- Changelog của state store.

Compaction không thay thế event history:

- Không đảm bảo consumer đọc ngay lập tức chỉ thấy bản mới nhất.
- Compaction chạy bất đồng bộ.
- Offset và tombstone vẫn có ý nghĩa.
- Các phiên bản cũ có thể tồn tại cho đến khi cleaner xử lý.
- Nếu cần audit toàn bộ thay đổi, hãy dùng event topic hoặc storage lịch sử riêng.

Tombstone là record có key nhưng value null, dùng để biểu diễn việc xóa key trong compacted topic. Tombstone cũng có retention riêng để consumer có cơ hội nhận biết việc xóa trước khi tombstone bị dọn.

So sánh:

| Event topic | Compacted topic |
|---|---|
| Giữ lịch sử sự kiện | Giữ state mới nhất theo key |
| Phù hợp audit và replay nghiệp vụ | Phù hợp rebuild state |
| Có thể phình nhanh | Có thể nhỏ hơn sau compaction |
| Key không nhất thiết duy nhất | Key là trung tâm của semantics |
| Không dùng compaction để thay thế lịch sử | Không dùng cho audit đầy đủ |

---

## 17. Thiết kế durability baseline

Với event quan trọng, một baseline có thể là:

~~~text
replication.factor = 3
min.insync.replicas = 2
acks = all
enable.idempotence = true
retries > 0
delivery.timeout.ms được đặt theo SLA
business side effect có idempotency
consumer chỉ commit sau khi xử lý thành công
~~~

Baseline này vẫn cần kiểm chứng:

- Có đủ ba broker ở các failure domain khác nhau không?
- Disk có đủ cho retention không?
- min.insync.replicas có làm hệ thống fail closed khi mất broker không?
- Producer có báo lỗi cho upstream không?
- Consumer có retry và DLQ không?
- Consumer commit có xảy ra sau side effect không?
- Database downstream có idempotent không?
- Có alert khi ISR shrink hoặc lag tăng không?

Durability là một chuỗi, không phải một config đơn lẻ:

Producer gửi thành công
-> leader ghi
-> đủ ISR xác nhận
-> broker giữ trong retention
-> consumer đọc
-> application xử lý
-> side effect commit
-> offset commit.

Mỗi mũi tên có failure mode riêng.

---

## 18. Các performance profile thường gặp

### Profile A: Command latency thấp

Mục tiêu:

- p99 latency thấp.
- Payload nhỏ.
- Không chấp nhận mất command.

Định hướng:

- batch.size vừa phải.
- linger.ms thấp.
- compression lz4 hoặc none sau khi benchmark.
- acks=all nếu command quan trọng.
- idempotence=true.
- producer và broker ở network gần nhau.
- consumer xử lý nhanh và commit rõ ràng.

Rủi ro:

- Tối ưu latency quá mạnh làm nhiều request nhỏ.
- CPU và network overhead tăng.
- Không nên dùng acks=0 chỉ để đạt một con số latency đẹp nếu mất command là không chấp nhận được.

### Profile B: Throughput analytics

Mục tiêu:

- Bytes/s và records/s cao.
- Latency vài ms hoặc vài chục ms chấp nhận được.
- Payload có tính nén tốt.

Định hướng:

- batch.size lớn hơn.
- linger.ms vài ms.
- compression lz4 hoặc zstd.
- fetch.min.bytes lớn hơn nếu traffic cao.
- đủ partition để chia tải.
- đo broker network và disk.

### Profile C: Consumer xử lý chậm

Mục tiêu:

- Không rebalance do xử lý dài.
- Không mất record.
- Lag có thể tăng nhưng phải quan sát được.

Định hướng:

- giảm max.poll.records.
- tính toán max.poll.interval.ms với margin.
- commit sau khi xử lý thành công.
- scale theo số partition và bottleneck downstream.
- retry có backoff.
- DLQ cho lỗi không thể xử lý.
- alert theo lag time và oldest record age.

### Profile D: State rebuild

Mục tiêu:

- Consumer mới có thể dựng state.
- Không cần giữ toàn bộ lịch sử thay đổi.

Định hướng:

- compacted topic.
- key ổn định.
- tombstone cho delete.
- retention tombstone đủ để consumer bắt kịp.
- kiểm thử bootstrap từ đầu topic.
- nếu cần audit, tạo event topic riêng.

---

## 19. Spring Kafka: cấu hình baseline cho lab

Các giá trị dưới đây chỉ là baseline để lab. Không copy thẳng vào production nếu chưa benchmark.

~~~yaml
spring:
  kafka:
    producer:
      properties:
        batch.size: 65536
        linger.ms: 5
        compression.type: lz4
        acks: all
        enable.idempotence: true
        delivery.timeout.ms: 120000
        request.timeout.ms: 30000
        max.in.flight.requests.per.connection: 5
    consumer:
      enable-auto-commit: false
      properties:
        fetch.min.bytes: 1
        fetch.max.wait.ms: 500
        max.partition.fetch.bytes: 1048576
        max.poll.records: 100
        max.poll.interval.ms: 300000
~~~

Producer callback nên ghi nhận:

- topic.
- partition.
- offset.
- latency.
- exception class.
- retryable hay non-retryable.
- correlation id hoặc event id.

Consumer listener nên có:

- acknowledgement mode phù hợp.
- error handler.
- backoff.
- dead-letter publishing recoverer nếu dùng DLQ.
- metric processing latency.
- metric commit latency.
- logging không chứa payload nhạy cảm.

Ví dụ nguyên tắc xử lý:

~~~text
receive record
-> validate schema
-> kiểm tra eventId đã xử lý chưa
-> thực hiện business operation idempotent
-> commit database
-> commit Kafka offset
~~~

Nếu muốn dùng Kafka transaction hoặc consume-transform-produce, cần thiết kế transaction boundary riêng. Đừng gọi một pipeline có database và HTTP side effect là exactly-once chỉ vì producer có idempotence.

---

## 20. Lab 1: Đo throughput theo compression

### Mục tiêu

So sánh codec trong cùng workload.

### Chuẩn bị

- Một topic có ít nhất 6 partition.
- Một producer instance cố định.
- Payload JSON có kích thước ổn định.
- Cùng batch.size và linger.ms.
- Chạy mỗi codec đủ lâu sau warm-up.

### Ma trận

~~~text
compression.type: none, lz4, snappy, gzip, zstd
batch.size: cố định
linger.ms: cố định
acks: all
enable.idempotence: true
~~~

### Thu thập

- records/s.
- MB/s.
- p50, p95, p99 producer latency.
- CPU producer.
- network bytes.
- broker disk bytes.
- consumer rate.
- error và retry rate.

### Câu hỏi

1. Codec nào giảm network nhiều nhất?
2. Codec nào làm CPU tăng mạnh nhất?
3. Codec nào có p99 tốt nhất?
4. Payload hiện tại có thực sự nén tốt không?
5. Với cùng throughput, codec nào có chi phí tổng tốt nhất?

---

## 21. Lab 2: Tắt và bật idempotent producer

### Mục tiêu

Quan sát khác biệt giữa retry có thể tạo duplicate và retry có idempotence.

### Cách làm an toàn trong lab

- Dùng topic riêng.
- Gắn eventId tăng dần vào từng record.
- Ghi nhận producer callback.
- Tạo gián đoạn mạng hoặc restart broker trong môi trường local test.
- Không dùng dữ liệu nghiệp vụ thật.
- Consumer đếm số eventId duy nhất và tổng số record nhận được.

### So sánh

~~~text
Case A: enable.idempotence=false, retries > 0
Case B: enable.idempotence=true, acks=all, retries > 0
~~~

### Kỳ vọng

- Case A có thể phát sinh duplicate trong một số failure timing.
- Case B giảm duplicate do producer retry, nhưng không thay thế deduplication ở consumer.
- Cả hai case đều cần quan sát lỗi hết delivery timeout.

Không cố tạo lỗi bằng cách kill dữ liệu hoặc xóa volume nếu chưa xác định rõ target. Lab nên có compose project và volume riêng.

---

## 22. Lab 3: Consumer xử lý chậm và theo dõi lag

### Mục tiêu

Hiểu quan hệ giữa processing time, max.poll.records, max.poll.interval.ms và lag.

### Thiết kế

1. Topic có 6 partition.
2. Producer gửi đều.
3. Consumer xử lý mỗi record với delay có thể cấu hình.
4. Chạy lần lượt 1, 3 và 6 consumer.
5. Theo dõi lag theo partition.
6. Tạo một key nóng để kiểm tra partition skew.

### Thử nghiệm

~~~text
Case A: max.poll.records=500, processing chậm
Case B: max.poll.records=100, processing chậm
Case C: max.poll.records=20, processing chậm
Case D: tăng max.poll.interval.ms có chủ đích
~~~

### Quan sát

- Có rebalance không?
- Lag tăng theo tổng hay chỉ một partition?
- Throughput thực tế thay đổi thế nào?
- Consumer có commit sau xử lý không?
- Scale thêm consumer có tác dụng khi số partition đã đủ không?
- Partition nóng có làm một consumer bị nghẽn không?

### Kết luận cần viết

Không chỉ ghi “giảm max.poll.records tốt hơn”. Hãy mô tả:

- processing time mỗi poll.
- lag time.
- rebalance count.
- database latency.
- trade-off giữa batch efficiency và failure recovery.

---

## 23. Lab 4: Retention ngắn và replay

### Mục tiêu

Thấy retention ảnh hưởng đến khả năng replay.

### Thiết kế

- Tạo topic lab với retention.ms ngắn.
- Gửi event có eventId.
- Để consumer dừng lâu hơn retention.
- Khởi động lại consumer từ offset cũ.
- Quan sát offset out of range hoặc hành vi reset offset theo policy.
- So sánh với topic có retention dài hơn.

### Câu hỏi

1. Consumer outage tối đa bao lâu?
2. Replay từ Kafka có đủ cho incident response không?
3. Có cần archive sang object storage không?
4. Khi offset đã hết retention, reset về earliest còn ý nghĩa không?
5. Alert nào cần phát trước khi dữ liệu replay bị xóa?

---

## 24. Lab 5: Event topic và compacted topic

### Mục tiêu

Phân biệt lịch sử event với state hiện tại.

### Topic event

Gửi:

~~~text
key=account-1, value=OPENED
key=account-1, value=KYC_APPROVED
key=account-1, value=ACTIVE
~~~

Topic event giữ các thay đổi để consumer có thể replay lịch sử.

### Topic compacted

Gửi:

~~~text
key=account-1, value=OPEN
key=account-1, value=KYC_APPROVED
key=account-1, value=ACTIVE
key=account-2, value=OPEN
key=account-2, value=null
~~~

Sau khi compaction chạy, state mới nhất của account-1 được giữ, còn account-2 có tombstone trong một khoảng thời gian.

### Kiểm tra

- Consumer mới có dựng được state không?
- Có nhìn thấy các phiên bản cũ trước khi cleaner chạy không?
- Tombstone có bị xử lý đúng không?
- Consumer có coi null value là delete không?
- Có cần event topic song song cho audit không?

---

## 25. Tuning checklist

### Producer

- [ ] Đã đo payload size và partition distribution.
- [ ] batch.size được chọn từ benchmark.
- [ ] linger.ms phù hợp với latency SLA.
- [ ] compression được chọn dựa trên CPU và network thật.
- [ ] acks được chọn theo durability requirement.
- [ ] idempotence không bị tắt do config xung đột.
- [ ] retry có delivery timeout.
- [ ] max.in.flight không phá ordering.
- [ ] producer buffer có metric và backpressure.

### Broker và topic

- [ ] replication factor phù hợp failure domain.
- [ ] min.insync.replicas được đặt rõ.
- [ ] Theo dõi UnderReplicatedPartitions.
- [ ] Disk có headroom cho retention và compaction.
- [ ] retention time và size đã được tính theo replay window.
- [ ] Compacted topic có key ổn định.
- [ ] Tombstone policy đã được hiểu.
- [ ] Message size limit đồng bộ giữa producer, broker, topic và consumer.

### Consumer

- [ ] fetch.min.bytes và fetch.max.wait.ms phù hợp traffic.
- [ ] fetch limits đủ cho message lớn nhất.
- [ ] max.poll.records dựa trên processing time.
- [ ] max.poll.interval.ms có margin.
- [ ] Commit xảy ra sau xử lý thành công.
- [ ] Lag được đo theo partition và theo thời gian.
- [ ] Có alert rebalance và lag.
- [ ] Retry và DLQ không làm partition bị kẹt vô hạn.
- [ ] Side effect idempotent.

---

## 26. Bài tập

### Bài 1

Một producer gửi 20.000 record/s, mỗi record 2 KiB, trên 12 partition. Đề xuất cách benchmark batch.size và linger.ms. Nêu metric cần đo.

### Bài 2

Cluster có replication factor 3 nhưng min.insync.replicas bằng 1. Producer dùng acks=all. Phân tích điều gì xảy ra khi hai broker follower cùng lỗi.

### Bài 3

Consumer xử lý p95 80 ms/record và đang dùng max.poll.records=500. Phân tích nguy cơ với max.poll.interval.ms=5 phút. Đề xuất cách kiểm chứng thay vì chỉ tính lý thuyết.

### Bài 4

Một topic có tổng lag thấp nhưng một partition có lag cao gấp 100 lần các partition khác. Liệt kê các nguyên nhân và cách phân biệt partition nóng với consumer bug.

### Bài 5

So sánh topic event retention 7 ngày với compacted topic state. Khi nào cần dùng cả hai?

### Bài 6

Vì sao idempotent producer không bảo đảm email chỉ được gửi một lần? Thiết kế cơ chế inbox hoặc idempotency key cho email service.

### Bài 7

Chọn profile cho hệ thống order:

- Không được mất order.
- Có thể duplicate message ở tầng transport.
- Payment service xử lý chậm.
- Cần replay 14 ngày.
- Cần audit đầy đủ.

Nêu cấu hình và cơ chế application-level cần có.

### Bài 8

Thiết kế dashboard tối thiểu cho tuần này. Chia metric thành producer, broker, consumer và business outcome.

---

## 27. Câu hỏi tự kiểm tra

1. batch.size khác linger.ms ở điểm nào?
2. Vì sao compression hiệu quả hơn khi batch lớn?
3. acks=all có bảo đảm dữ liệu không bao giờ mất không?
4. replication factor khác min.insync.replicas thế nào?
5. Idempotent producer xử lý failure scenario nào?
6. Khi nào retry có thể làm reorder record?
7. max.poll.records có làm fetch từ broker nhỏ đi không?
8. Nếu processing một poll quá lâu thì max.poll.interval.ms có vai trò gì?
9. Lag tăng nói lên điều gì và chưa nói lên điều gì?
10. Retention khác backup như thế nào?
11. Khi nào dùng compaction?
12. Tombstone trong compacted topic có ý nghĩa gì?
13. Vì sao tăng buffer.memory không giải quyết bottleneck gốc?
14. Vì sao consumer cần business idempotency dù producer đã bật idempotence?
15. Với RF=3 và min ISR=2, chuyện gì xảy ra khi chỉ còn một ISR?

Nếu chưa trả lời được câu 3, 4, 6, 8, 10 và 14, hãy đọc lại phần durability chain và làm lại lab failure.

---

## 28. Checklist hoàn thành tuần

- [ ] Giải thích được batch.size và linger.ms.
- [ ] Chạy benchmark ít nhất ba codec compression.
- [ ] So sánh acks=1 và acks=all trong lab.
- [ ] Hiểu quan hệ RF, ISR và min.insync.replicas.
- [ ] Bật idempotent producer và kiểm tra effective config.
- [ ] Tạo failure có retry và quan sát error/duplicate.
- [ ] Điều chỉnh fetch size cho workload thử nghiệm.
- [ ] Tạo consumer xử lý chậm và quan sát lag.
- [ ] Tạo retention ngắn và kiểm tra replay.
- [ ] Tạo event topic và compacted topic.
- [ ] Viết một trang benchmark report có số liệu trước và sau tuning.
- [ ] Trả lời được 15 câu tự kiểm tra.

---

## 29. Kết luận

Kafka performance là bài toán tối ưu một pipeline gồm batching, compression, network, broker disk, replication, fetch và consumer processing. Kafka durability là bài toán thiết kế failure model từ lúc producer gửi đến lúc consumer commit side effect.

Một cấu hình thực tế thường bắt đầu từ:

- batch và linger vừa đủ.
- compression có benchmark.
- acks=all cho event quan trọng.
- idempotent producer.
- retry có timeout.
- replication factor và min.insync.replicas rõ ràng.
- consumer poll phù hợp processing time.
- lag và ISR có alert.
- retention phục vụ replay.
- compaction chỉ dùng cho state, không thay thế event history.
- business operation idempotent.

Mục tiêu của tuần không phải tìm một bộ config “chuẩn cho mọi hệ thống”. Mục tiêu là biết đo, biết mô hình hóa failure, biết nêu trade-off và biết chứng minh cấu hình đáp ứng SLA.

---

## 30. Tài liệu tham khảo chính thức

- Apache Kafka Producer Configs: https://kafka.apache.org/40/configuration/producer-configs/
- Apache Kafka Consumer Configs: https://kafka.apache.org/40/configuration/consumer-configs/
- Apache Kafka Topic Configs: https://kafka.apache.org/40/configuration/topic-level-configs/
- Apache Kafka Design and Replication: https://kafka.apache.org/design/


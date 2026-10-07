# Tuần 13: Memory, TTL, eviction và persistence

## 1. Mục tiêu

Sau bài này, bạn có thể:

- Hiểu Redis memory model và memory overhead.
- Dùng maxmemory để giới hạn Redis.
- Thiết kế TTL cho cache key.
- Phân biệt expired key và evicted key.
- So sánh noeviction, allkeys-LRU, allkeys-LFU, volatile-LRU và volatile-TTL.
- Nhận diện big key, hot key và fragmentation.
- Hiểu RDB snapshot và AOF log.
- Chọn AOF fsync policy phù hợp.
- Phân biệt persistence cho cache với durability của database.
- Đo memory, eviction và behavior sau restart.

---

## 2. Vì sao memory và persistence quan trọng?

Redis chủ yếu phục vụ dữ liệu trong memory. Điều này tạo tốc độ cao nhưng cũng tạo giới hạn:

~~~text
Memory đầy
    ↓
Redis phải reject write hoặc evict key
    ↓
Cache miss tăng
    ↓
Database load tăng
~~~

Nếu Redis dùng persistence:

~~~text
Process/restart
    ↓
Redis đọc RDB hoặc AOF
    ↓
Khôi phục một phần hoặc toàn bộ state
~~~

Persistence không biến Redis cache thành database nghiệp vụ tự động. Cần phân biệt:

- Cache có thể rebuild từ source of truth.
- Session có thể chấp nhận mất hay không tùy yêu cầu.
- Payment/order không nên chỉ tồn tại trong Redis chưa được bảo vệ.
- RDB/AOF bảo vệ khả năng khôi phục Redis, không thay thế backup database.

---

## 3. Redis memory model

Memory Redis sử dụng không chỉ là tổng kích thước value.

~~~text
Memory tổng
  ├─ Key object
  ├─ Value object
  ├─ Data structure overhead
  ├─ Allocator overhead
  ├─ Client buffers
  ├─ Replication/AOF buffers
  ├─ Temporary command buffers
  └─ Fragmentation
~~~

Một key nhỏ nhưng hàng triệu key vẫn tốn nhiều memory vì overhead mỗi key. Một Hash/List/Stream lớn có overhead và encoding riêng.

Các yếu tố ảnh hưởng:

- Số lượng key.
- Độ dài key.
- Kích thước value.
- Số field/member.
- Cấu trúc dữ liệu.
- Số client.
- Pipeline payload.
- Replication buffer.
- AOF rewrite buffer.
- Allocator và fragmentation.

Không ước lượng memory chỉ bằng tổng byte của JSON payload.

---

## 4. Đo memory

Các command hữu ích:

~~~text
INFO memory
MEMORY USAGE shop:product:123
MEMORY STATS
INFO stats
DBSIZE
~~~

Các field thường quan tâm trong INFO memory:

- used_memory.
- used_memory_human.
- used_memory_rss.
- used_memory_peak.
- mem_fragmentation_ratio.
- maxmemory.
- maxmemory_policy.

Memory usage của một key không bao gồm toàn bộ tác động phụ của workload. Cần đo cả dataset và traffic thực tế.

---

## 5. maxmemory

maxmemory giới hạn memory Redis được phép sử dụng cho dataset hoặc theo behavior cấu hình.

Xem cấu hình:

~~~text
CONFIG GET maxmemory
CONFIG GET maxmemory-policy
~~~

Đặt trong lab:

~~~text
CONFIG SET maxmemory 100mb
CONFIG SET maxmemory-policy allkeys-lru
~~~

Trong production nên cấu hình bằng file/container config thay vì thay đổi ad hoc, đồng thời kiểm tra persistence và restart behavior.

### Khi đạt maxmemory

Redis sẽ:

- Evict key theo policy nếu policy cho phép.
- Reject write nếu noeviction hoặc không tìm được key phù hợp.
- Có thể làm application nhận lỗi OOM command.

Đừng chờ memory đạt 100% mới xử lý. Cần headroom cho client buffer, replication, AOF rewrite và spike tạm thời.

---

## 6. Dataset memory và operational memory

Có thể phân biệt:

~~~text
Dataset memory: key/value đang lưu
Operational memory: buffer, client, replication, persistence, fragmentation
~~~

Nếu đặt maxmemory quá sát RAM máy:

- Fork RDB/AOF rewrite có thể cần copy-on-write memory.
- Background rewrite tạo memory pressure.
- Replication backlog và client output buffer tăng.
- Hệ điều hành có thể swap hoặc OOM kill Redis.

Nên có capacity plan:

~~~text
RAM máy
  - headroom hệ điều hành
  - buffer vận hành
  - fork/copy-on-write margin
  = maxmemory an toàn
~~~

Con số cụ thể phụ thuộc workload, persistence, replication và môi trường.

---

## 7. TTL là gì?

TTL là thời gian key được phép tồn tại.

~~~text
SET cache:product:123 value EX 300
TTL cache:product:123
~~~

Giá trị TTL:

- Số dương: thời gian còn lại.
- -1: key tồn tại nhưng không có expiration.
- -2: key không tồn tại.

TTL dùng để:

- Giới hạn stale data.
- Tự dọn dữ liệu tạm.
- Giảm memory lâu dài.
- Giúp session/lock/token tự hết hạn.
- Làm safety net khi invalidation thất bại.

TTL không đảm bảo data mới ngay sau update. Key vẫn stale cho đến khi bị xóa, ghi đè hoặc hết hạn.

---

## 8. Expired key và eviction khác nhau

### Expired

Key hết TTL:

~~~text
TTL đến 0 → key trở thành expired
~~~

Redis có thể xóa key:

- Khi client truy cập key.
- Trong active expiration cycle background.
- Theo cơ chế quản lý expiration của Redis.

### Evicted

Key bị xóa vì memory đạt maxmemory và eviction policy chọn key đó.

~~~text
Memory đầy → policy chọn key → key bị evict
~~~

Eviction không có nghĩa TTL của key đã hết. Một key vừa mới tạo cũng có thể bị evict nếu policy chọn.

Metrics cần phân biệt:

- expired_keys.
- evicted_keys.
- keyspace_hits.
- keyspace_misses.

---

## 9. TTL strategy

TTL nên dựa trên freshness và lifecycle:

~~~text
Product description → vài phút
Search result       → vài giây/phút
Session             → idle/absolute timeout
Lock                → vài giây
Idempotency record  → theo retry window
Negative cache      → ngắn
~~~

Không dùng một TTL mặc định cho mọi key.

### TTL jitter

Tránh nhiều key hết hạn cùng lúc:

~~~text
TTL thực tế = base TTL + random(0..jitter)
~~~

Ví dụ:

~~~java
long ttlSeconds = 300 + ThreadLocalRandom.current()
    .nextLong(0, 61);
~~~

Jitter không được làm vượt stale budget. Với dữ liệu cần hết hạn chính xác, dùng policy khác thay vì cộng jitter tùy ý.

### TTL khi update key

Nếu gọi SET lại mà không truyền expiration, TTL cũ có thể bị mất tùy command/workflow. Khi cập nhật cache, luôn kiểm tra TTL contract:

~~~text
SET key value EX 300
~~~

Không tách SET và EXPIRE nếu có thể tránh khoảng key tồn tại không TTL.

---

## 10. Eviction policy

Eviction policy quyết định Redis xóa key nào khi đạt maxmemory.

Không có policy tốt nhất cho mọi workload. Chọn dựa trên việc Redis chứa:

- Chỉ cache.
- Cache lẫn dữ liệu quan trọng.
- Session/token.
- Stream/list cần retention.
- Dữ liệu không được phép mất.

---

## 11. noeviction

Với noeviction:

~~~text
Memory đầy → Redis reject write
~~~

Ưu điểm:

- Không tự xóa dữ liệu.
- Phù hợp khi mất bất kỳ key nào cũng không chấp nhận được.
- Buộc application xử lý capacity rõ ràng.

Rủi ro:

- Cache write fail.
- Rate limit/session/idempotency operation có thể lỗi.
- Nếu application không handle OOM, request fail hàng loạt.
- Không phù hợp cache thuần túy muốn tự giải phóng memory.

Dùng noeviction khi dữ liệu Redis quan trọng hoặc muốn fail rõ thay vì xóa ngẫu nhiên.

---

## 12. allkeys-LRU

LRU là Least Recently Used: ưu tiên evict key ít được truy cập gần đây.

allkeys-LRU xét mọi key, dù key có TTL hay không.

Phù hợp:

- Redis chủ yếu là cache.
- Mọi key đều có thể rebuild.
- Working set quan trọng hơn dữ liệu cũ ít dùng.

Rủi ro:

- Key quan trọng nhưng ít được truy cập có thể bị xóa.
- Dữ liệu không nên mất không được trộn vào cùng instance.
- LRU có thể là approximate, không phải thứ tự tuyệt đối.

---

## 13. allkeys-LFU

LFU là Least Frequently Used: ưu tiên evict key có tần suất truy cập thấp.

Phù hợp:

- Workload có hot key ổn định.
- Muốn giữ key được đọc thường xuyên.
- Working set có skew rõ.

Rủi ro:

- Key từng hot nhưng hiện không còn hot có thể giữ memory lâu.
- Key mới có thể chưa đủ frequency.
- Cần hiểu tuning decay/frequency nếu dùng nâng cao.

So sánh đơn giản:

~~~text
LRU → quan tâm lần truy cập gần nhất
LFU → quan tâm tần suất truy cập
~~~

Chọn bằng benchmark workload thật, không chỉ tên policy.

---

## 14. volatile-LRU

volatile-LRU chỉ xét key có TTL và evict key ít được dùng gần đây trong nhóm đó.

Phù hợp khi cùng Redis chứa:

~~~text
Key quan trọng không TTL → không bị policy này evict
Key cache có TTL → có thể evict
~~~

Rủi ro:

- Nếu cache key quên TTL, nó không thuộc tập volatile.
- Redis có thể không tìm thấy key phù hợp để evict.
- Key cache không TTL làm capacity behavior khó đoán.

Đây là lý do mọi cache key nên có TTL nếu policy dựa trên volatile.

---

## 15. volatile-TTL

volatile-TTL ưu tiên evict key có TTL còn lại ngắn hơn.

Ý tưởng:

~~~text
Key sắp hết hạn → evict trước
~~~

Rủi ro:

- TTL ngắn không đồng nghĩa giá trị ít quan trọng.
- Key không TTL không được xét.
- Có thể xóa dữ liệu vừa được tạo nhưng có TTL ngắn.
- Không phản ánh access frequency.

Chỉ dùng khi TTL thực sự đại diện cho mức ưu tiên và data model đã được kiểm chứng.

---

## 16. Các policy khác

Redis còn có các biến thể như:

- allkeys-random.
- volatile-random.
- volatile-LFU.
- volatile-ttl.
- allkeys-LRU/LFU.

Random thường ít phù hợp workload cache có locality nhưng có thể hữu ích trong một số mô hình đơn giản.

Điều quan trọng:

~~~text
Policy phải phù hợp với khả năng rebuild và giá trị business của key
~~~

Không dùng eviction policy để bảo vệ dữ liệu quan trọng nếu dữ liệu đó đáng lẽ nằm ở database bền vững.

---

## 17. Chọn policy cho cache

Cache thuần túy, mọi value rebuild được:

~~~text
allkeys-LRU hoặc allkeys-LFU
~~~

Cache có TTL và cùng Redis chứa key quan trọng:

~~~text
volatile-LRU hoặc tách instance
~~~

Dữ liệu không được phép tự mất:

~~~text
noeviction hoặc storage phù hợp hơn
~~~

Cảnh báo: session, idempotency record, token blacklist và rate limit có thể có policy khác nhau. Không trộn chúng vào cache instance với policy không phù hợp mà không đánh giá failure.

Tách Redis instance thường rõ ràng hơn cố gắng bảo vệ nhiều loại dữ liệu bằng một policy.

---

## 18. Big key

Big key là key có value/collection quá lớn hoặc command trên key tạo chi phí cao.

Ví dụ:

- Một String JSON vài MB.
- Một Hash có hàng triệu field.
- Một List/Set cực lớn.
- Một Stream không trim.
- Một key chứa toàn bộ search result lịch sử.

Rủi ro:

- GET/serialization latency cao.
- Memory spike.
- DEL block hoặc tốn CPU.
- Replication payload lớn.
- AOF rewrite nặng.
- Eviction một key giải phóng lớn nhưng gây miss spike.
- Backup/restore chậm.

Giảm big key:

- Chia nhỏ theo resource/page/time bucket.
- Giới hạn collection.
- Dùng LTRIM hoặc MAXLEN.
- Cache DTO nhỏ.
- Không dùng HGETALL/SMEMBERS/LRANGE toàn bộ tùy tiện.
- Dùng UNLINK khi phù hợp.
- Đo MEMORY USAGE và payload size.

---

## 19. Hot key

Hot key là key nhận lượng truy cập lớn hơn nhiều key khác.

Ví dụ:

~~~text
homepage:config → 500.000 GET/phút
~~~

Rủi ro:

- Một key tập trung traffic vào một node.
- Key hết hạn gây stampede.
- Serialization lặp lại.
- Network bandwidth tập trung.
- Cluster không phân tán một key duy nhất.

Giảm hot key:

- TTL jitter.
- Refresh-ahead.
- Single-flight.
- Local L1 cache.
- Replica/read scaling nếu phù hợp.
- Key replication có kiểm soát.
- Chia nhỏ dữ liệu nếu access pattern cho phép.
- Không tạo quá nhiều duplicate chỉ để scale mà không cân nhắc invalidation.

Big key là vấn đề kích thước; hot key là vấn đề traffic. Một key có thể vừa big vừa hot.

---

## 20. Fragmentation

Fragmentation là chênh lệch giữa memory Redis allocator giữ và memory dataset thực tế cần.

Các nguyên nhân:

- Key/value tạo/xóa với kích thước khác nhau.
- Eviction liên tục.
- Allocator giữ page để tái sử dụng.
- Fork copy-on-write.
- Workload biến động.

Theo dõi:

~~~text
INFO memory
MEMORY STATS
~~~

mem_fragmentation_ratio cao có thể nghĩa là RSS lớn hơn used memory, nhưng cần xem context. Không tự kết luận chỉ từ một field.

Cách giảm:

- Giữ key/value kích thước ổn định.
- Tránh tạo/xóa value cực lớn liên tục.
- Chọn allocator/config phù hợp.
- Restart có kế hoạch nếu cần reclaim memory.
- Không restart tùy tiện trong production.
- Chừa headroom cho rewrite/failover.

---

## 21. RDB snapshot

RDB là snapshot của dataset tại các thời điểm nhất định.

~~~text
Dataset hiện tại → snapshot file → restart đọc snapshot
~~~

Ưu điểm:

- File compact.
- Restore thường nhanh.
- Tốt cho backup.
- Ít overhead runtime hơn AOF trong một số workload.
- Dễ copy sang storage backup.

Rủi ro:

- Có thể mất các write sau snapshot gần nhất khi crash.
- Snapshot fork cần memory và I/O.
- BGSAVE tạo copy-on-write pressure.
- Snapshot interval càng dài, RPO càng lớn.

RDB phù hợp khi chấp nhận mất một khoảng dữ liệu giữa các snapshot hoặc cần backup/restore nhanh.

---

## 22. AOF

AOF ghi lại write command/log để có thể replay khi restart.

~~~text
SET key value
INCR counter
DEL old-key
...
~~~

Khi restart, Redis replay AOF để dựng lại state.

Ưu điểm:

- Durability tốt hơn tùy fsync policy.
- Có thể giảm mất write so với snapshot.
- Log thể hiện lịch sử mutation ở mức command.

Rủi ro:

- File lớn hơn RDB.
- Rewrite cần tài nguyên.
- Fsync policy ảnh hưởng latency/durability.
- Recovery có thể lâu hơn.
- Cần monitoring disk và AOF growth.

AOF không phải audit log business hoàn hảo; command log và domain event có ý nghĩa khác nhau.

---

## 23. AOF fsync policy

Các policy thường gặp:

### always

Mỗi write cố gắng fsync ngay.

Ưu điểm:

- Durability cao nhất trong các policy cơ bản.

Rủi ro:

- Latency và I/O cao.
- Throughput thấp hơn.
- Không đảm bảo chống mọi lỗi phần cứng/OS.

### everysec

Fsync khoảng mỗi giây.

Ưu điểm:

- Cân bằng latency và durability.
- Thường phù hợp nhiều workload.

Rủi ro:

- Có thể mất khoảng một giây write khi crash.

### no

Để OS quyết định flush.

Ưu điểm:

- Latency thấp hơn.

Rủi ro:

- RPO khó dự đoán.
- Mất nhiều dữ liệu hơn khi crash.

Chọn dựa trên RPO/RTO, không chỉ benchmark throughput.

---

## 24. RDB và AOF trade-off

| Tiêu chí | RDB | AOF |
|---|---|---|
| Dạng | Snapshot | Write log |
| Restore | Thường nhanh | Có thể lâu hơn |
| Mất dữ liệu crash | Từ snapshot gần nhất | Tùy fsync |
| File | Compact hơn | Có thể lớn |
| Runtime overhead | Snapshot/fork định kỳ | Ghi/f​​sync/rewrite |
| Backup | Thuận tiện | Cũng có thể backup |
| Audit business | Không phù hợp | Không thay domain event |

Có thể bật cả RDB và AOF để dùng mục đích khác nhau. Khi restart, behavior ưu tiên file persistence cần được hiểu theo version/configuration đang dùng.

---

## 25. Persistence có phù hợp cho cache không?

Cache thuần túy:

~~~text
Database là source
Redis mất → rebuild
~~~

Persistence có thể giúp restart nhanh hơn và giảm cache cold start, nhưng không phải bắt buộc nếu mất cache chấp nhận được.

Dữ liệu quan trọng:

- Không dùng Redis làm database duy nhất chỉ vì bật AOF.
- Cần backup, restore test, monitoring và recovery plan.
- Cần transaction/constraint ở database.
- Cần biết RPO/RTO.
- Cần kiểm tra corruption/disk full.

Persistence giúp giữ Redis state; nó không đảm bảo business correctness, schema migration hoặc idempotency.

---

## 26. Fork và copy-on-write

RDB/AOF rewrite thường có thể fork process. Parent tiếp tục xử lý request, child ghi snapshot/rewrite.

Nếu parent sửa page đang được child dùng, OS tạo copy page:

~~~text
Dataset lớn + write traffic cao
    ↓
Copy-on-write tăng
    ↓
RSS tăng đột biến
    ↓
Memory pressure/OOM risk
~~~

Cần chừa headroom và theo dõi:

- used_memory.
- used_memory_rss.
- fork duration.
- latest_fork_usec.
- AOF rewrite status.
- RDB last save status.

Đừng đặt maxmemory sát giới hạn RAM khi có persistence.

---

## 27. AOF rewrite

AOF có thể phình to do nhiều command lặp lại. Rewrite tạo log rút gọn phản ánh state hiện tại.

Ví dụ:

~~~text
SET counter 1
INCR counter
INCR counter
~~~

Có thể rewrite thành:

~~~text
SET counter 3
~~~

Rewrite cần:

- CPU.
- Disk I/O.
- Memory/copy-on-write.
- Buffer để ghi thêm command trong lúc rewrite.

Theo dõi disk space và rewrite failure. Disk đầy có thể ảnh hưởng AOF và behavior persistence.

---

## 28. Capacity planning

Ước lượng:

~~~text
Memory cần =
  key bytes
+ value bytes
+ structure overhead
+ client/buffer overhead
+ replication overhead
+ persistence/fork headroom
+ growth margin
~~~

Ví dụ dataset cache:

~~~text
1.000.000 key
average key + value = 1 KB
raw payload ≈ 1 GB
~~~

Không kết luận Redis chỉ cần 1 GB. Cần cộng overhead, fragmentation, buffer, eviction margin và peak growth.

Capacity plan phải test:

- Dataset hiện tại.
- Peak key count.
- Peak value size.
- Expiry wave.
- AOF rewrite.
- RDB snapshot.
- Restart restore.
- Replication/failover nếu có.

---

## 29. Lab: tạo dataset lớn

Tạo dữ liệu String trong lab bằng script hoặc redis-cli piped input có kiểm soát. Theo dõi:

~~~text
INFO memory
DBSIZE
MEMORY USAGE key
INFO stats
~~~

Không chạy test dataset lớn trên Redis production. Giới hạn tốc độ tạo key để không làm môi trường lab mất ổn định.

Ghi lại:

- used_memory trước/sau.
- RSS trước/sau.
- Số key.
- Average value size.
- Fragmentation ratio.
- Expired/evicted count.

---

## 30. Lab: key có và không có TTL

Tạo:

~~~text
SET lab:with-ttl value EX 60
SET lab:without-ttl value
TTL lab:with-ttl
TTL lab:without-ttl
~~~

Sau đó:

- Theo dõi TTL giảm.
- Truy cập key sau khi hết hạn.
- Kiểm tra DBSIZE.
- Quan sát expired_keys.
- Tìm key không TTL bằng tooling an toàn, không dùng KEYS trên production.

Mục tiêu: hiểu key không TTL có thể sống vô hạn dù được coi là cache.

---

## 31. Lab: kích hoạt maxmemory

Trong Redis lab:

~~~text
CONFIG SET maxmemory 20mb
CONFIG SET maxmemory-policy allkeys-lru
~~~

Tạo nhiều key rồi quan sát:

~~~text
INFO memory
INFO stats
~~~

Theo dõi:

- evicted_keys.
- keyspace_hits.
- keyspace_misses.
- Write error nếu policy không evict được.
- Hit ratio sau eviction.

Không dùng CONFIG SET tùy tiện trên production. Dùng cấu hình versioned và change process.

---

## 32. Lab: so sánh eviction policy

Lặp lại dataset với:

~~~text
noeviction
allkeys-lru
allkeys-lfu
volatile-lru
volatile-ttl
~~~

Thiết kế workload:

- Một nhóm key nóng đọc thường xuyên.
- Một nhóm key lạnh.
- Một nhóm key có TTL.
- Một nhóm key không TTL.
- Write liên tục đến khi đạt maxmemory.

So sánh:

- Key nào bị evict.
- Request nào lỗi.
- Hit ratio.
- Database fallback.
- Memory ổn định hay dao động.

---

## 33. Lab: restart với RDB và AOF

### RDB

Thiết lập snapshot trong lab, ghi dataset, restart Redis và kiểm tra key còn lại.

Ghi lại:

- Snapshot time.
- Restore time.
- Write có thể mất.
- File size.

### AOF

Bật AOF với fsync policy khác nhau ở môi trường lab. Ghi dataset, restart và so sánh:

- Restore time.
- File size.
- Data loss simulation.
- Runtime latency.
- Rewrite behavior.

Không mô phỏng crash bằng cách kill production process. Dùng container/lab riêng.

---

## 34. Runbook khi memory tăng

1. Kiểm tra used_memory và RSS.
2. Kiểm tra maxmemory/policy.
3. Kiểm tra evicted/expired keys.
4. Tìm big key bằng tooling an toàn.
5. Kiểm tra key không TTL.
6. Kiểm tra hot key/traffic spike.
7. Kiểm tra client buffer và blocked clients.
8. Kiểm tra AOF/RDB rewrite.
9. Kiểm tra fragmentation.
10. Quyết định giảm traffic, scale, cleanup hoặc thay policy.
11. Không flush toàn Redis nếu chưa xác định phạm vi.
12. Ghi nhận impact và recovery.

---

## 35. Câu hỏi tự kiểm tra

1. Redis memory gồm những thành phần nào ngoài value?
2. maxmemory làm gì khi đạt giới hạn?
3. expired key khác evicted key thế nào?
4. Vì sao cache key nên có TTL?
5. noeviction phù hợp trường hợp nào?
6. allkeys-LRU và allkeys-LFU khác nhau thế nào?
7. volatile policy có yêu cầu gì về TTL?
8. Big key và hot key khác nhau thế nào?
9. Fragmentation ratio cao có thể do đâu?
10. RDB khác AOF về durability và restore thế nào?
11. AOF fsync everysec đánh đổi điều gì?
12. Vì sao persistence fork có thể làm memory tăng?
13. Bật persistence có biến Redis thành database nghiệp vụ không?
14. Cần đo gì khi kiểm tra eviction?
15. Vì sao không flush Redis để chữa memory issue một cách mù quáng?

---

## 36. Tiêu chí hoàn thành

Bạn hoàn thành Tuần 13 khi có thể:

- Đo memory và memory usage theo key.
- Cấu hình maxmemory trong lab.
- Giải thích expired và evicted keys.
- Chọn noeviction, LRU, LFU hoặc volatile policy theo workload.
- Nhận diện big key, hot key và fragmentation.
- Đặt TTL cho cache key.
- Giải thích RDB snapshot và AOF log.
- Chọn fsync policy theo RPO/RTO.
- Hiểu fork/copy-on-write và persistence headroom.
- Chạy lab dataset lớn, eviction và restart.
- Viết capacity plan và memory incident runbook.
- Biết dữ liệu quan trọng cần source of truth, backup và recovery plan ngoài Redis.

Kết luận:

> Redis nhanh vì dùng memory, nhưng memory là tài nguyên hữu hạn. TTL giúp kiểm soát lifecycle, eviction quyết định điều gì xảy ra khi đầy, còn RDB/AOF quyết định khả năng phục hồi state. Một hệ thống production-ready phải tính cả overhead, fragmentation, big key, hot key, fork memory và business value của từng loại dữ liệu.


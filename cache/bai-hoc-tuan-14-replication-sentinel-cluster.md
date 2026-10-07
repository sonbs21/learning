# Tuần 14: Replication, Sentinel và Cluster

## 1. Mục tiêu

Sau bài này, bạn có thể:

- Giải thích primary/replica replication và replication lag.
- Phân biệt Sentinel với Redis Cluster.
- Hiểu discovery, monitoring, quorum và automatic failover.
- Giải thích hash slot và key distribution.
- Nhận diện giới hạn multi-key command trong Cluster.
- Dùng hash tags khi cần đưa key liên quan về cùng slot.
- Chọn read primary hay replica theo consistency.
- Thiết kế retry/failover policy an toàn.

---

## 2. Vì sao cần Replication, Sentinel và Cluster?

Một Redis instance đơn có rủi ro:

- Process chết.
- Máy chủ hoặc network lỗi.
- Bảo trì cần downtime.
- Dataset vượt memory một node.
- Một node không chịu được throughput.
- Một node trở thành điểm lỗi duy nhất.

Ba khái niệm giải quyết các vấn đề khác nhau:

| Thành phần | Giải quyết chính |
|---|---|
| Replication | Tạo bản sao dữ liệu |
| Sentinel | Monitor, discovery và failover |
| Cluster | Sharding và scale ngang |

Replication không tự failover. Sentinel không shard dataset. Cluster có topology phức tạp và giới hạn cross-slot.

---

## 3. Primary và replica

~~~text
Application → Primary
                 ├─ Replica 1
                 └─ Replica 2
~~~

Primary nhận write và truyền replication stream đến replica. Replica có thể phục vụ read, làm bản sao để failover và giảm tải read.

Replication thường bất đồng bộ:

~~~text
1. Client gửi write đến primary
2. Primary xử lý và trả response
3. Primary gửi replication stream
4. Replica nhận và áp dụng
~~~

Write thành công ở primary không đồng nghĩa mọi replica đã có value mới.

---

## 4. Replication lag

Replication lag là khoảng trễ giữa primary và replica:

~~~text
Primary: value version 42
Replica: value version 41
~~~

Nguyên nhân:

- Network chậm.
- Replica CPU bận.
- Write throughput cao.
- Command nặng/blocking.
- Full resynchronization.
- Persistence pressure.
- Reconnect/failover.

Đọc replica có thể trả stale data:

~~~text
Client write primary version 42
Client read replica ngay sau đó
Replica vẫn version 41
Client thấy dữ liệu cũ
~~~

Không đọc replica cho payment result, inventory checkout, permission vừa revoke, session logout hoặc request cần read-after-write mạnh.

Replica có thể phù hợp với analytics, dashboard, recommendation và product description có stale budget.

---

## 5. Read routing

Application cần policy:

~~~text
Write → primary
Strong read → primary
Eventual read → replica
~~~

Cần kiểm soát:

- Endpoint nào được đọc replica.
- Stale tối đa bao lâu.
- Sticky primary sau write.
- Fallback khi replica unavailable.
- Fallback có làm primary quá tải không.
- Client có refresh topology không.

Sau write có thể route về primary trong khoảng ngắn, nhưng đây chỉ là heuristic. Requirement quan trọng nên dùng version hoặc consistency policy rõ ràng.

---

## 6. WAIT và durability acknowledgement

Redis WAIT có thể chờ một số replica acknowledge replication, nhưng:

- Không biến Redis thành transaction database.
- Replica acknowledge không luôn là disk durability.
- Failover vẫn có thể mất write tùy timing.
- Chờ replica làm write latency tăng.

Dùng theo RPO/RTO, không bật cho mọi request nếu không cần.

---

## 7. Full và partial resynchronization

Khi replica reconnect:

- Partial resync tiếp tục từ replication backlog nếu đủ dữ liệu.
- Full resync gửi snapshot rồi replication stream mới.

Full resync tốn:

- Network bandwidth.
- CPU.
- Memory/copy-on-write.
- I/O.
- Thời gian bắt kịp.

Theo dõi replica link status, replication offset, backlog và sync progress.

---

## 8. Sentinel là gì?

Sentinel là hệ thống monitor primary/replica, hỗ trợ:

- Monitoring.
- Notification.
- Automatic failover.
- Service discovery.
- Client configuration.

~~~text
Sentinel 1 ─┐
Sentinel 2 ─┼─ monitor primary và replicas
Sentinel 3 ─┘
                 ↓
           promote replica
~~~

Sentinel không shard data và không phải proxy data request. Client hỏi Sentinel primary hiện tại là ai, sau đó kết nối tới Redis node phù hợp.

---

## 9. Sentinel discovery

Thay vì hardcode primary:

~~~text
redis-primary:6379
~~~

Client biết danh sách Sentinel:

~~~text
sentinel-1:26379
sentinel-2:26379
sentinel-3:26379
~~~

Khi failover, client cần:

- Refresh primary endpoint.
- Đóng connection cũ.
- Reconnect với backoff.
- Refresh pool.
- Xử lý command đang chạy.
- Retry chỉ những operation an toàn.

Không retry write mù quáng sau failover nếu operation không idempotent.

---

## 10. Quorum và failover

Cần phân biệt:

- Quorum: số Sentinel đồng ý primary subjective down.
- Majority: số Sentinel cần để authorize failover.

Ba Sentinel thường giúp tránh một Sentinel đơn lẻ tự promote sai. Không đặt mọi Sentinel trong cùng một failure domain.

~~~text
Network partition
  ├─ Nhóm A có majority → có thể failover
  └─ Nhóm B không có majority → không nên promote
~~~

Quorum không loại bỏ hoàn toàn split-brain; topology và network placement vẫn quan trọng.

---

## 11. Sentinel failover flow

~~~text
1. Sentinel phát hiện primary không reachable
2. Sentinel đánh giá subjectively down
3. Đạt quorum/majority
4. Chọn replica phù hợp
5. Promote replica thành primary
6. Reconfigure replica còn lại
7. Client refresh topology
8. Client reconnect và retry theo policy
~~~

Trong failover:

- Write có thể fail.
- Read có thể stale hoặc fail.
- Connection pool có thể giữ endpoint cũ.
- Retry có thể tạo duplicate side effect.
- Failover không đảm bảo zero downtime.

---

## 12. Sentinel và data loss

Replication asynchronous nên có cửa sổ mất write:

~~~text
Primary có write version 42
Replica mới có version 41
Primary fail
Replica promote với version 41
Version 42 mất
~~~

Giảm rủi ro bằng:

- Theo dõi replication health.
- WAIT khi latency chấp nhận được.
- RPO rõ ràng.
- Database/source of truth.
- Idempotency khi retry.
- Không dùng Redis là nơi duy nhất chứa payment/order quan trọng.

---

## 13. Redis Cluster

Redis Cluster phân phối key trên 16.384 hash slots.

~~~text
Key → CRC16(key) mod 16384 → hash slot → primary
~~~

Ví dụ:

~~~text
Primary A: slots 0-5000
Primary B: slots 5001-10000
Primary C: slots 10001-16383
~~~

Mỗi primary có thể có replica:

~~~text
Primary A → Replica A1
Primary B → Replica B1
Primary C → Replica C1
~~~

Cluster cung cấp sharding, horizontal scale, replica và failover theo topology.

---

## 14. Hash slot và data distribution

Mặc định Redis hash toàn bộ key:

~~~text
user:42 → slot X
product:123 → slot Y
~~~

Key được phân phối trên các primary. Điều này giúp scale nhưng làm multi-key command khó hơn.

Cluster không tự đặt mọi key liên quan cùng node. Application phải thiết kế key nếu cần locality.

---

## 15. CROSSSLOT và multi-key command

Nếu các key ở slot khác nhau, các command sau có thể lỗi:

- MGET nhiều key.
- MSET nhiều key.
- SUNION/SINTER.
- Transaction nhiều key.
- Lua script nhiều key.
- RENAME giữa key khác slot.
- Workflow atomic nhiều key.

Ví dụ nguy hiểm:

~~~text
MGET user:1 user:2
~~~

Hai key có thể ở hai slot khác nhau và trả CROSSSLOT.

Không dùng multi-key command trong Cluster nếu chưa kiểm tra slot mapping.

---

## 16. Hash tags

Phần trong dấu ngoặc nhọn được dùng để hash:

~~~text
user:{42}:profile
user:{42}:permissions
user:{42}:sessions
~~~

Các key trên cùng slot, phù hợp với multi-key command hoặc Lua liên quan một aggregate.

Ví dụ:

~~~text
order:{123}:status
order:{123}:events
order:{123}:lock
~~~

### Trade-off

Ưu điểm:

- Multi-key command không CROSSSLOT.
- Lua nhiều key có thể atomic.
- Data của một aggregate gần nhau.

Rủi ro:

- Hash tag chung quá rộng tạo hot slot.
- Một tenant/user lớn tập trung traffic.
- Không phân tán đều.
- Tăng locality nhưng giảm balance.

Chỉ dùng hash tag khi có yêu cầu multi-key thật.

---

## 17. Cluster-aware client

Client cluster-aware cần:

- Biết mapping slot/node.
- Route command đúng node.
- Xử lý MOVED.
- Xử lý ASK khi resharding.
- Refresh topology.
- Xử lý replica read nếu cho phép.
- Reconnect khi node failover.
- Kiểm tra transaction/Lua cùng slot.

Nếu dùng client không hỗ trợ Cluster, application có thể gửi command tới node sai và nhận MOVED/error.

Cần cấu hình cluster-aware Lettuce/Jedis/Spring Data Redis phù hợp, không chỉ đổi host từ standalone sang một node cluster.

---

## 18. MOVED và ASK

MOVED nghĩa là slot đã thuộc node khác; client nên cập nhật topology và gửi command tới node đúng.

ASK xuất hiện trong quá trình migration; client tạm thời gửi command theo hướng dẫn ASKING.

Cluster-aware client xử lý các response này. Client tự viết ở tầng thấp phải tránh retry loop, topology stale và gửi nhầm node.

---

## 19. Lua trong Cluster

Lua script truy cập nhiều key phải dùng key cùng hash slot.

~~~text
lock:{order:123}
order:{order:123}:status
~~~

Không tạo key phụ tùy ý bên trong script nếu key đó khác slot. Khai báo KEYS rõ ràng và dùng hash tag thống nhất.

Luôn test Lua trong Cluster thật; standalone không phát hiện CROSSSLOT.

---

## 20. Sentinel hay Cluster?

### Chọn Sentinel khi

- Dataset vừa một primary.
- Cần high availability/failover.
- Chưa cần shard.
- Muốn multi-key trên một node dễ hơn.
- Team muốn topology đơn giản hơn.

### Chọn Cluster khi

- Dataset vượt memory một node.
- Throughput cần scale ngang.
- Chấp nhận giới hạn cross-slot.
- Application có cluster-aware client.
- Team vận hành được resharding/failover.

Cluster không tự động tốt hơn Sentinel. Nó tăng operational complexity và yêu cầu thiết kế key nghiêm túc.

---

## 21. Replica read và consistency

| Dữ liệu | Route phù hợp |
|---|---|
| Payment vừa commit | Primary |
| Inventory checkout | Primary/source |
| Product description | Replica có thể được |
| Analytics | Replica |
| Permission vừa revoke | Primary hoặc version-aware |
| Search suggestion | Replica nếu stale chấp nhận |

Nếu lag vượt threshold, client nên chuyển primary hoặc fail theo policy. Không trả replica data cũ cho nghiệp vụ cần strong consistency.

---

## 22. Failover và retry

Sau failover:

- Connection cũ có thể lỗi.
- Command đang chạy có thể không rõ kết quả.
- Client có thể retry.
- Write retry có thể tạo duplicate.

Policy:

- GET thuần có thể retry có backoff.
- SET cache idempotent theo key/value/version có thể retry.
- Payment/order không retry mù quáng.
- Dùng idempotency key.
- Có timeout tổng và correlation ID.

Failover test phải kiểm tra behavior application, không chỉ việc replica được promote.

---

## 23. Replication không thay backup

Replication không bảo vệ khỏi:

~~~text
Cả failure domain bị mất
Corruption được replicate
Operator DEL được replicate
Sai dữ liệu được ghi và lan sang replica
~~~

Cần kết hợp:

- RDB/AOF.
- Backup ngoài cluster.
- Restore drill.
- Replica khác failure domain.
- Monitoring lag.
- ACL/TLS/network policy.
- Database source of truth.

Replication bảo vệ availability/data copy; backup bảo vệ recovery khỏi lỗi lớn hơn.

---

## 24. Bài thực hành replication

Tạo primary và replica trong lab:

~~~text
Primary: 6379
Replica: 6380
~~~

Thực hiện:

1. Ghi key ở primary.
2. Đọc ở replica.
3. Đo thời gian key xuất hiện.
4. Tạo write load.
5. Quan sát replication lag.
6. Restart replica.
7. Kiểm tra partial/full resync.
8. Mô phỏng primary failure.

Ghi nhận write có thể mất khi primary fail trước khi replica nhận.

---

## 25. Bài thực hành Sentinel

Tạo lab:

~~~text
1 primary
2 replicas
3 sentinels
~~~

Kiểm tra:

- Sentinel discover primary.
- Subjective/objective down.
- Quorum và majority.
- Replica promotion.
- Client refresh endpoint.
- Write/read trong failover.
- Retry behavior.
- Data loss window.

Không dùng một Sentinel duy nhất để kết luận HA đúng.

---

## 26. Bài thực hành Cluster

Test commands:

~~~text
SET product:1 value
CLUSTER KEYSLOT product:1
CLUSTER NODES
CLUSTER INFO
~~~

Thực hành:

- Key nằm ở slot nào.
- MGET key khác slot.
- MGET key cùng hash tag.
- Lua cùng/khác slot.
- Node failover.
- Topology refresh.
- Resharding.

Ví dụ cùng slot:

~~~text
user:{42}:profile
user:{42}:permissions
~~~

---

## 27. Metrics

Replication:

- Replication offset.
- Replica lag.
- Link status.
- Full/partial sync count.
- Backlog usage.
- Replica read latency.

Sentinel:

- Subjective/objective down.
- Failover count.
- Failover duration.
- Quorum status.
- Sentinel connectivity.
- Client reconnect errors.

Cluster:

- Slot coverage.
- Node health.
- MOVED/ASK count.
- CROSSSLOT errors.
- Resharding progress.
- Per-node memory.
- Per-node ops/sec.
- Hot slot/key.
- Replica lag.
- Failover events.

---

## 28. Câu hỏi tự kiểm tra

1. Replication khác Sentinel và Cluster thế nào?
2. Vì sao đọc replica có thể trả dữ liệu cũ?
3. Replication asynchronous tạo rủi ro gì khi primary fail?
4. Sentinel có shard dataset không?
5. Quorum khác majority thế nào?
6. Vì sao không đặt mọi Sentinel cùng failure domain?
7. Redis Cluster dùng bao nhiêu hash slot?
8. CROSSSLOT xảy ra khi nào?
9. Hash tags giải quyết vấn đề gì?
10. Vì sao hash tags có thể tạo hot slot?
11. MOVED và ASK khác nhau thế nào?
12. Khi nào Sentinel phù hợp hơn Cluster?
13. Vì sao failover retry có thể tạo duplicate payment?
14. Replication có thay thế backup không?
15. Cần test gì ngoài node promote?

---

## 29. Tiêu chí hoàn thành

Bạn hoàn thành Tuần 14 khi có thể:

- Vẽ primary/replica topology.
- Đo và giải thích replication lag.
- Chọn read primary hay replica theo consistency.
- Giải thích Sentinel monitoring, discovery, quorum và failover.
- Mô phỏng primary failure và client reconnect.
- Giải thích Cluster hash slot và data distribution.
- Phát hiện CROSSSLOT.
- Thiết kế hash tags cho multi-key operation.
- Cấu hình cluster-aware client.
- Phân biệt HA của Sentinel với sharding của Cluster.
- Viết retry/failover policy an toàn.
- Kết hợp replication với persistence, backup và source of truth.
- Chạy lab replication, Sentinel và Cluster.

Kết luận:

> Replication tạo bản sao, Sentinel giúp monitor và failover cho primary/replica, còn Cluster phân phối dữ liệu qua hash slot và scale ngang. Sentinel không shard dữ liệu; Cluster không loại bỏ consistency và retry problem. Lựa chọn đúng phụ thuộc vào memory, throughput, availability, multi-key operation và khả năng vận hành của team.


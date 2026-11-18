# Thao tác đa key (Multi-key operations)

> Nguồn: <https://redis.io/docs/latest/develop/using-commands/multi-key-operations/>
> Hành vi của các lệnh đa key (multi-key) trên các cấu hình Redis và thiết lập clustering khác nhau

Các thao tác đa key (multi-key operation) trong Redis cho phép bạn làm việc với nhiều key trong một lệnh duy nhất, nhưng hành vi của chúng thay đổi đáng kể tùy thuộc vào cấu hình Redis và thiết lập clustering của bạn. Trang này cung cấp một tài liệu tham khảo nhanh cho các developer làm việc với thao tác đa key trên các cấu hình Redis khác nhau.

## Các cấu hình (Configurations)

Redis hỗ trợ năm cấu hình riêng biệt, mỗi cấu hình có hành vi lệnh đa key khác nhau:

1. **ROS/RS tắt clustering** (ROS/RS clustering disabled) - Một Redis instance đơn lẻ
2. **ROS, bật clustering** (ROS, clustering enabled) - Redis Open Source cluster
3. **RS, bật clustering, bật OSS cluster API** (RS, clustering enabled, OSS cluster API enabled) - Redis Software có khả năng tương thích với ROS cluster
4. **RS, bật clustering, tắt OSS cluster API** (RS, clustering enabled, OSS cluster API disabled) - Clustering độc quyền (proprietary) của Redis Software
5. **RS, Active-Active** - Redis Software Active-Active (được coi là clustered ngay cả khi chỉ có một shard)

ROS là viết tắt của Redis Open Source và RS là viết tắt của Redis Software.

## Các hành vi của lệnh (Command behaviors)

Với mỗi cấu hình, các lệnh thể hiện một trong ba hành vi:

- **single-slot**: Lệnh phải thao tác trên các key nằm trong cùng một hash slot
- **cross-slot (all shards)**: Lệnh có thể thao tác xuyên qua tất cả các shard trong cluster
- **cross-slot (within a single shard)**: Lệnh có thể thao tác xuyên qua các slot nhưng chỉ trong phạm vi một shard duy nhất

## Các lệnh chỉ đọc (Read-only commands)

| Hành vi | Lệnh |
|----------|----------|
| **ROS/RS tắt clustering:**<br>&ndash; toàn bộ DB (một shard)<br><br>**ROS bật clustering, RS bật clustering (bật OSS cluster API):**<br>&ndash; shard hiện tại<br><br>**RS bật clustering (tắt OSS cluster API):**<br>&ndash; tất cả các shard | DBSIZE, KEYS, SCAN |
| **ROS/RS tắt clustering:**<br>&ndash; cross-slot<br><br>**ROS bật clustering, RS bật clustering (bật OSS cluster API):**<br>&ndash; single-slot<br><br>**RS bật clustering (tắt OSS cluster API):**<br>&ndash; cross-slot (all shards) | EXISTS, MGET |
| **ROS/RS tắt clustering:**<br>&ndash; cross-slot<br><br>**ROS bật clustering, RS bật clustering (bật OSS cluster API), RS bật clustering (tắt OSS cluster API):**<br>&ndash; single-slot | PFCOUNT, SDIFF, SINTER, SINTERCARD, SUNION, WATCH, XREAD, XREADGROUP, ZDIFF, ZINTER, ZINTERCARD, ZUNION |
| **ROS/RS tắt clustering:**<br>&ndash; cross-slot<br><br>**ROS bật clustering, RS bật clustering (bật OSS cluster API), RS bật clustering (tắt OSS cluster API):**<br>&ndash; single-shard | JSON.MGET<br><br>Người dùng sẽ không nhận lỗi CROSSSLOT. Tuy nhiên, khi bật clustering và không phải tất cả các key được chỉ định đều nằm trong cùng một slot, người dùng sẽ nhận kết quả một phần (partial result) cho tất cả các slot trên shard hiện tại. |
| **ROS/RS tắt clustering:**<br>&ndash; cross-slot (all shards)<br><br>**ROS bật clustering, RS bật clustering (bật OSS cluster API), RS bật clustering (tắt OSS cluster API):**<br>&ndash; cross-slot (all shards), không thể là một phần của transaction | TS.MGET, TS.MRANGE, TS.MREVRANGE, TS.QUERYINDEX |

## Các lệnh đọc-ghi (Read-write commands)

| Hành vi | Lệnh |
|----------|----------|
| **ROS/RS tắt clustering:**<br>&ndash; toàn bộ DB (một shard)<br><br>**ROS bật clustering, RS bật clustering (bật OSS cluster API):**<br>&ndash; shard hiện tại<br><br>**RS bật clustering (tắt OSS cluster API):**<br>&ndash; tất cả các shard | FLUSHALL, FLUSHDB |
| **ROS/RS tắt clustering:**<br>&ndash; cross-slot<br><br>**ROS bật clustering, RS bật clustering (bật OSS cluster API):**<br>&ndash; single-slot<br><br>**RS bật clustering (tắt OSS cluster API):**<br>&ndash; cross-slot (all shards) | DEL, MSET, TOUCH, UNLINK<br><br>Lưu ý: trên Active-Active, DEL, MSET và UNLINK là single-slot |
| **ROS/RS tắt clustering:**<br>&ndash; cross-slot<br><br>**ROS bật clustering, RS bật clustering (bật OSS cluster API), RS bật clustering (tắt OSS cluster API):**<br>&ndash; single-slot | BITOP, BLMOVE, BLMPOP, BLPOP, BRPOP, BRPOPLPUSH, BZMPOP, BZPOPMAX, BZPOPMIN, CMS.MERGE, COPY, GEORADIUS hoặc GEORADIUSBYMEMBER (với STORE hoặc STOREDIST), GEOSEARCHSTORE, JSON.MSET, LMOVE, LMPOP, MSETNX, PFMERGE, RENAME, RENAMENX, RPOPLPUSH, SDIFFSTORE, SINTERSTORE, SMOVE, SUNIONSTORE, TDIGEST.MERGE, TS.MADD, ZDIFFSTORE, ZINTERSTORE, ZMPOP, ZRANGESTORE, ZUNIONSTORE |
| **ROS/RS tắt clustering:**<br>&ndash; cross-slot<br><br>**ROS bật clustering, RS bật clustering (bật OSS cluster API), RS bật clustering (tắt OSS cluster API):**<br>&ndash; single-shard | TS.CREATERULE, TS.DELETERULE<br><br>Người dùng sẽ không nhận lỗi CROSSSLOT. Tuy nhiên, khi bật clustering và hai key được chỉ định không nằm trong cùng một slot, người dùng sẽ nhận `(error) ERR TSDB: the key does not exist`. |

## Pipeline, transaction và script (Pipelines, transactions, and scripts)

| Hành vi | Thao tác |
|----------|------------|
| **ROS/RS tắt clustering:**<br>&ndash; cross-slot<br>**ROS bật clustering, RS bật clustering (bật OSS cluster API):**<br>&ndash; single-slot<br>**RS bật clustering (tắt OSS cluster API):**<br>&ndash; cross-slot (all shards) | Pipeline |
| **ROS/RS tắt clustering:**<br>&ndash; cross-slot<br>**ROS bật clustering, RS bật clustering (bật OSS cluster API), RS bật clustering (tắt OSS cluster API):**<br>&ndash; single-slot | Các key trong một transaction `MULTI/EXEC`<br>Các key trong một Lua script được thực thi bằng EVAL hoặc EVALSHA |

## Ví dụ theo từng cấu hình (Examples by Configuration)

### Instance đơn lẻ (Không clustering) (Single Instance (No Clustering))

Trong một Redis instance đơn lẻ, mọi thao tác đa key đều hoạt động không bị hạn chế:

```redis
# Các thao tác pipeline hoạt động trên bất kỳ key nào
PIPELINE
SET user:1 "Alice"
SET product:100 "Widget"
GET user:1
GET product:100
EXEC

# Transaction hoạt động với bất kỳ key nào
MULTI
SET counter:a 1
SET counter:b 2
INCR counter:a
INCR counter:b
EXEC
```

### Môi trường clustered (Clustered Environments)

Trong các thiết lập clustered, bạn cần cân nhắc việc phân bổ slot:

```redis
# Lệnh này có thể thất bại nếu các key nằm ở các slot khác nhau
MSET user:1 "Alice" user:2 "Bob"

# Dùng hash tag để đảm bảo cùng một slot
MSET {users}:1 "Alice" {users}:2 "Bob"

# Kiểm tra một key thuộc slot nào
CLUSTER KEYSLOT user:1
CLUSTER KEYSLOT {users}:1
```

### Database Active-Active (Active-Active Databases)

Database Active-Active có thêm các hạn chế cho thao tác ghi:

```redis
# Thao tác đọc có thể hoạt động xuyên qua các slot
MGET user:1 user:2 product:100

# Thao tác ghi phải nằm trong cùng một slot
MSET {data}:user:1 "Alice" {data}:user:2 "Bob"
```

## Xử lý sự cố với thao tác đa key (Troubleshooting Multi-Key Operations)

### Các thông báo lỗi thường gặp (Common Error Messages)

- **CROSSSLOT**: Các key trong request không được băm (hash) vào cùng một slot
- **MOVED**: Key đã được chuyển sang một node khác (trong quá trình resharding)
- **TRYAGAIN**: Thao tác tạm thời không khả dụng (trong quá trình migration)

### Giải pháp (Solutions)

1. **Dùng hash tag** để gom nhóm các key liên quan
2. **Thiết kế lại mô hình dữ liệu** để giảm thiểu các thao tác cross-slot
3. **Kiểm tra trạng thái cluster** khi gặp lỗi
4. **Hiện thực logic retry** cho các lỗi tạm thời

## Cân nhắc về hiệu năng (Performance Considerations)

- **Thao tác single-slot** là nhanh nhất vì chúng không cần phối hợp (coordination)
- **Thao tác cross-slot** có thể có độ trễ cao hơn do định tuyến nội bộ (internal routing)
- **Các lệnh theo pattern** (KEYS, FLUSHALL) quét tất cả các shard và có thể tốn kém
- **Thao tác của module** có thể có cách hiện thực cross-slot được tối ưu hóa

Hãy chọn cấu hình Redis và thiết kế mô hình dữ liệu của bạn dựa trên các yêu cầu về thao tác đa key.

# Redis Resources

## Knowledge

- [Redis docs (redis.io/docs/latest)](https://redis.io/docs/latest/)
  Nguồn chính thức. Use for: mọi lệnh, cấu hình, ngữ nghĩa chính xác.
- [Diagnosing latency issues (redis.io)](https://redis.io/docs/latest/operate/oss_and_stack/management/optimization/latency/)
  Bài gốc về mô hình đơn luồng, lệnh chậm, fork, swap, THP. Use for: mọi bài về latency/sự cố prod.
- [Redis latency monitoring (redis.io)](https://redis.io/docs/latest/operate/oss_and_stack/management/optimization/latency-monitor/)
  LATENCY LATEST/HISTORY/DOCTOR. Use for: lab chẩn đoán.
- [Redis FAQ (redis.io)](https://redis.io/docs/latest/develop/get-started/faq/)
  Vì sao đơn luồng, giới hạn bộ nhớ, dùng nhiều core. Use for: bài nền tảng.
- [EXPIRE command (redis.io)](https://redis.io/docs/latest/commands/expire/)
  Ngữ nghĩa TTL, lệnh nào xoá timeout, expire trên replica. Use for: bài TTL/expiry.
- [Redis pipelining (redis.io)](https://redis.io/docs/latest/develop/using-commands/pipelining/)
  RTT, syscall, giới hạn kích thước mẻ. Use for: bài hiệu năng client.
- [Memory optimization (redis.io)](https://redis.io/docs/latest/operate/oss_and_stack/management/optimization/memory-optimization/)
  Encoding đặc biệt cho tập nhỏ, ngưỡng cấu hình. Use for: bài encoding/bộ nhớ, sizing.
- [OBJECT ENCODING (redis.io)](https://redis.io/docs/latest/commands/object-encoding/)
  Danh sách encoding theo phiên bản. Use for: tra tên encoding.
- [Spring Data Redis: Pipelining](https://docs.spring.io/spring-data/redis/reference/redis/pipelining.html)
  `executePipelined`, giới hạn với Cluster. Use for: ví dụ Java.
- [Transactions (redis.io)](https://redis.io/docs/latest/develop/using-commands/transactions/)
  MULTI/EXEC, không rollback, WATCH. Use for: bài atomicity.
- [Key eviction (redis.io)](https://redis.io/docs/latest/develop/reference/eviction/)
  Các policy, LRU/LFU xấp xỉ, maxmemory-samples. Use for: bài bộ nhớ.
- [Redis persistence (redis.io)](https://redis.io/docs/latest/operate/oss_and_stack/management/persistence/)
  RDB, AOF, fork, appendfsync, rewrite. Use for: bài persistence.
- [ElastiCache: Managing reserved memory (AWS)](https://docs.aws.amazon.com/AmazonElastiCache/latest/dg/redis-memory-management.html)
  reserved-memory-percent mặc định 25%, custom parameter group. Use for: sizing node.
- [AWS re:Post: OOM command not allowed](https://repost.aws/knowledge-center/oom-command-not-allowed-redis)
  Policy mặc định volatile-lru và lỗi OOM. Use for: sự cố bộ nhớ.
- [ElastiCache: Resilience / Multi-AZ (AWS)](https://docs.aws.amazon.com/AmazonElastiCache/latest/dg/disaster-recovery-resiliency.html)
  Multi-AZ, replication bất đồng bộ. Use for: bài HA/độ bền.
- [ElastiCache: Durability limitations (AWS)](https://docs.aws.amazon.com/AmazonElastiCache/latest/dg/Durability.Limitations.html)
  Durability cần Valkey 9.0+, Multi-AZ, cluster mode. Use for: quyết định dùng Redis làm store chính.
- [ElastiCache: Overall best practices (AWS)](https://docs.aws.amazon.com/AmazonElastiCache/latest/dg/WorkingWithRedis.html)
  Cluster mode, connection pooling, đọc từ replica, tránh KEYS/SMEMBERS, sharded pub/sub. Use for: mọi bài ElastiCache.
- [ElastiCache: Best practices for node-based clusters (AWS)](https://docs.aws.amazon.com/AmazonElastiCache/latest/dg/BestPractices.SelfDesigned.html)
  Multi-AZ, sizing bộ nhớ, resize, giảm downtime. Use for: bài triển khai/vận hành.
- [ElastiCache: Which metrics should I monitor? (AWS)](https://docs.aws.amazon.com/AmazonElastiCache/latest/dg/CacheMetrics.WhichShouldIMonitor.html)
  CPUUtilization vs EngineCPUUtilization, memory, evictions. Use for: bài monitoring/alarm.

## Wisdom (Communities)

- [Redis Discord (chính thức)](https://discord.gg/redis)
  Hỏi trực tiếp người dùng và dev Redis. Use for: câu hỏi thiết kế, sự cố khó.
- [r/redis](https://www.reddit.com/r/redis/)
  Use for: hỏi kinh nghiệm vận hành thực tế.
- [Stack Overflow tag redis](https://stackoverflow.com/questions/tagged/redis)
  Use for: lỗi cụ thể, đã có nhiều câu trả lời chất lượng.

## Gaps
- Chưa chọn sách chuyên sâu (cân nhắc: "Redis in Action", tài liệu internals về dict/rehash, ziplist/listpack). Cần kiểm chứng độ cập nhật trước khi thêm.
- Chưa có nguồn case study sự cố prod thực tế (postmortem) đáng tin.

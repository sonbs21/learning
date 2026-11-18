# Mission: Redis

## Why
Nâng level backend: hiểu Redis tới tận bên trong (vì sao nhanh, lưu dữ liệu thế nào, replication/cluster chạy ra sao) để tự thiết kế, triển khai trên AWS ElastiCache và xử lý được sự cố production thay vì chỉ dùng Redis như một hộp đen.

## Success looks like
- Giải thích được mô hình đơn luồng, event loop, và vì sao một lệnh chậm làm nghẽn mọi client
- Chọn đúng data type + TTL cho các bài toán cache, session, rate limit, leaderboard, lock
- Hiểu RDB/AOF, eviction policy, replication, failover, cluster mode và trade-off của chúng
- Dựng và cấu hình ElastiCache (Valkey/Redis OSS): cluster mode, Multi-AZ, parameter group, CloudWatch alarm
- Chẩn đoán được sự cố prod: latency spike, hot key, big key, cache stampede/avalanche/penetration, OOM, failover mất dữ liệu

## Constraints
- Xuất phát điểm: gần như chưa biết Redis
- Mỗi buổi 15–20 phút, một ý chính
- Lab chạy bằng Docker trên máy local; ElastiCache học qua docs + console khi cần

## Out of scope
- Redis Stack/module (Search, JSON, TimeSeries), Redis Enterprise
- Đọc source code C của Redis

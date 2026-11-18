# Notes

- Dạy bằng tiếng Việt, giữ thuật ngữ kỹ thuật bằng tiếng Anh.
- Mỗi bài 15–20 phút. Lab chạy bằng Docker (máy chưa cài redis-cli, dùng `docker exec`).
- Hướng triển khai chính: AWS ElastiCache. Người học cũng đang học AWS SAA-C03 và ECS (thư mục bên cạnh), nên nối với VPC/SG/Multi-AZ khi hợp lý.
- Workspace nằm trong repo học Java/Spring/Kafka, nên ví dụ client sẽ dùng Spring Data Redis / Lettuce (suy luận, chưa xác nhận).
- Lab DEBUG SLEEP cần chạy container với `--enable-debug-command yes`.
- Đã kiểm chứng trên redis:7.4 (2026-10-30): hash-max-listpack-entries = 512 (blog oneuptime ghi 128 là SAI); hash 512 field = 6192 B listpack, 513 = 28816 B hashtable; HDEL về 1 field vẫn là hashtable. redis-benchmark: ~200k ops/s, -P 16 ~1.7–2M.
- Bài 1–3 được soạn liền một lượt theo yêu cầu, chưa có phản hồi quiz. Trước khi soạn bài 4, hỏi điểm bài 1–3 và chỗ nào chưa rõ.
- Bài 4–8 soạn liền theo yêu cầu "tạo 5 bài" (2026-10-30), vẫn chưa có phản hồi quiz nào. Đã kiểm chứng trên redis:7.4: hz=10, active-expire-effort=1, maxmemory 0/noeviction/samples 5, busy-reply-threshold 5000, save "3600 1 300 100 60 10000", appendfsync everysec; 100k key EX 2 không ai đọc → DBSIZE 0 sau 3 s; volatile-lru không có key TTL → OOM; allkeys-lru evict 84544 key trong một lệnh SET; script Lua đã ghi → UNKILLABLE; AOF Redis 7 = base.rdb + incr.aof + manifest.
- Chưa kiểm chứng: maxmemory-policy mặc định của ElastiCache = volatile-lru (chỉ có nguồn re:Post).
- Lần tới: hỏi điểm quiz bài 1–8 trước khi soạn phần Pattern (bài 9+).

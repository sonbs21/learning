# Tuần 3: Redis command, key và TTL

## 1. Mục tiêu bài học

Sau bài này, bạn có thể:

- Phân biệt Redis server và Redis client.
- Sử dụng các lệnh cơ bản: `SET`, `GET`, `DEL`, `MGET`, `MSET`, `EXISTS`, `TYPE`.
- Đọc và quản lý thời gian sống bằng `EXPIRE`, `TTL`, `PERSIST`.
- Sử dụng `SET` với `NX`, `XX`, `EX`, `PX`.
- Hiểu `INCR`, `DECR` và tính atomic của một Redis command.
- Thiết kế key có namespace rõ ràng, predictable và không chứa dữ liệu nhạy cảm.
- Phân biệt database index trong Redis với database ứng dụng.
- Hiểu vì sao không dùng `KEYS` trong production và khi nào dùng `SCAN`.
- Xây được một bài lab Redis command có TTL, lock và rate limit đơn giản.

---

## 2. Redis trong kiến trúc ứng dụng

Redis là một server lưu trữ dữ liệu trong memory, giao tiếp với client qua network. Ứng dụng Java/Spring Boot không “gọi trực tiếp vào memory của Redis”; nó gửi command đến Redis server và nhận response.

```text
Spring Boot application
          │
          │ Redis protocol / client library
          ▼
      Redis server
          │
          └─ key/value, TTL, data structures
```

### 2.1. Redis server

Redis server chịu trách nhiệm:

- Nhận connection từ client.
- Parse và thực thi command.
- Lưu key và value.
- Quản lý TTL.
- Trả response.
- Thực hiện persistence, replication hoặc clustering nếu được cấu hình.

### 2.2. Redis client

Redis client có thể là:

- `redis-cli` trên command line.
- RedisInsight.
- Jedis hoặc Lettuce trong Java.
- Spring Data Redis.
- Client của Node.js, Python, Go hoặc ngôn ngữ khác.

Client không thay đổi ý nghĩa của command. Ví dụ `GET` trong `redis-cli` và `GET` qua Lettuce đều yêu cầu Redis trả value của một key.

---

## 3. Khởi động Redis lab

Nếu đã có Docker, chạy Redis local:

```bash
docker run --name redis-week-3 -p 6379:6379 -d redis:7
```

Kết nối bằng CLI:

```bash
redis-cli -h 127.0.0.1 -p 6379
```

Kiểm tra server:

```text
PING
```

Response mong đợi:

```text
PONG
```

Kết thúc session CLI:

```text
QUIT
```

Trong project thật, nên pin version Redis cụ thể thay vì dùng `latest`. Không đưa Redis production ra Internet công khai nếu chưa có authentication, network policy, TLS và monitoring phù hợp.

---

## 4. Redis command và RESP ở mức nhận biết

Redis nhận command theo protocol RESP. Ở mức học tuần 3, bạn chưa cần tự triển khai protocol, chỉ cần hiểu:

```text
Client gửi command + arguments
Redis xử lý command
Redis trả response
```

Ví dụ:

```text
SET app:product:123 "{...}" EX 300
```

Command này có:

- Tên command: `SET`.
- Key: `app:product:123`.
- Value: `"{...}"`.
- Option: `EX 300`.

Redis command thường có tính chất atomic ở cấp một command. Tuy nhiên, nhiều command nối tiếp nhau không tự động tạo thành một transaction logic.

---

## 5. String: cấu trúc cơ bản của cache

Redis String có thể lưu text, JSON, số nguyên hoặc dữ liệu nhị phân.

### 5.1. `SET`

```text
SET app:message "hello"
```

Response:

```text
OK
```

### 5.2. `GET`

```text
GET app:message
```

Response:

```text
"hello"
```

Nếu key không tồn tại:

```text
GET app:missing
```

Response là `nil`.

### 5.3. Ghi JSON

```text
SET app:product:123 '{"id":123,"name":"Keyboard","price":1200000}'
GET app:product:123
```

Redis không hiểu nội dung JSON trong String. Việc serialize và deserialize thuộc về application.

### 5.4. Ghi đè key

```text
SET app:message "first"
SET app:message "second"
GET app:message
```

Kết quả là `second`. `SET` mặc định ghi đè value hiện tại và thường xóa TTL cũ nếu không cung cấp TTL mới. Vì vậy cần chú ý khi cập nhật một key đã có expiration.

### 5.5. `DEL`

```text
DEL app:message
```

Response là số key đã bị xóa, thường là `1` hoặc `0`.

`DEL` là thao tác xóa đồng bộ theo command. Với value rất lớn, cần biết rằng xóa có thể tạo thêm chi phí; các trường hợp đặc biệt có thể cân nhắc `UNLINK` để xóa bất đồng bộ.

### 5.6. `EXISTS`

```text
EXISTS app:product:123
```

Response:

- `1`: key tồn tại.
- `0`: key không tồn tại.

Không nên luôn gọi `EXISTS` trước `GET`:

```text
EXISTS key
GET key
```

Hai command tạo thêm round trip và key có thể thay đổi giữa hai lần gọi. Nếu chỉ cần value, gọi `GET` một lần rồi kiểm tra `nil` thường tốt hơn.

---

## 6. Đọc và ghi nhiều key

### 6.1. `MGET`

```text
MGET app:product:123 app:product:456 app:product:789
```

Redis trả về danh sách value theo đúng thứ tự key truyền vào. Key không tồn tại tương ứng với `nil`.

`MGET` giúp giảm số round trip so với nhiều lệnh `GET` riêng lẻ.

### 6.2. `MSET`

```text
MSET app:color:red '#ff0000' app:color:blue '#0000ff'
```

`MSET` ghi nhiều key trong một command. Tuy nhiên, `MSET` không cung cấp TTL riêng cho từng key theo cách đơn giản như `SET ... EX`. Nếu dữ liệu cần TTL, phải thiết kế workflow phù hợp hoặc dùng pipeline/script khi cần.

### 6.3. Cẩn thận với kích thước batch

Batch quá lớn có thể:

- Tạo request payload lớn.
- Chiếm thời gian xử lý Redis.
- Làm tăng latency của các client khác.
- Gây áp lực memory ở client và server.

Batch nên có giới hạn hợp lý, không gom toàn bộ database vào một `MGET`.

---

## 7. Kiểm tra kiểu dữ liệu với `TYPE`

```text
TYPE app:product:123
```

Nếu key là String, response là:

```text
string
```

Các response khác có thể là `hash`, `list`, `set`, `zset`, `stream` hoặc `none` nếu key không tồn tại.

Mỗi key chỉ có một kiểu dữ liệu. Ví dụ:

```text
SET app:user:1 "Alice"
HSET app:user:1 name "Alice"
```

Command thứ hai sẽ lỗi vì key đang là String, không phải Hash. Khi đổi data model, cần xóa key cũ hoặc dùng namespace/version mới.

---

## 8. TTL: thời gian sống của key

TTL là thời gian một key còn được phép tồn tại trong Redis. Khi TTL hết, key được coi là expired và không còn được phục vụ như một value bình thường.

### 8.1. `EXPIRE`

```text
SET app:otp:user:42 "123456"
EXPIRE app:otp:user:42 300
```

Key sẽ có thời gian sống khoảng 300 giây.

### 8.2. `TTL`

```text
TTL app:otp:user:42
```

Các giá trị quan trọng:

- Số dương: số giây TTL còn lại.
- `-1`: key tồn tại nhưng không có expiration.
- `-2`: key không tồn tại.

Ví dụ:

```text
SET app:test "value"
TTL app:test
```

Kết quả thường là `-1` vì chưa đặt TTL. Đây là một tín hiệu cần chú ý nếu key đó lẽ ra là cache hoặc dữ liệu tạm.

### 8.3. `PTTL`

`PTTL` trả TTL theo milliseconds:

```text
PTTL app:otp:user:42
```

Giá trị đặc biệt cũng tương tự:

- `-1`: không có expiration.
- `-2`: key không tồn tại.

### 8.4. `PERSIST`

```text
PERSIST app:otp:user:42
```

`PERSIST` xóa expiration, biến key thành key không có TTL. Không dùng tùy tiện trên dữ liệu cache vì có thể biến một key tạm thành key tồn tại vô hạn.

### 8.5. Đặt TTL ngay khi `SET`

Tốt hơn là đặt value và TTL trong cùng một command:

```text
SET app:product:123 '{"id":123}' EX 300
```

Điều này tránh khoảng thời gian key tồn tại nhưng chưa có TTL:

```text
SET key value
EXPIRE key 300
```

Nếu client bị ngắt sau `SET` nhưng trước `EXPIRE`, key có thể tồn tại không giới hạn.

---

## 9. `EX`, `PX`, `NX` và `XX`

### 9.1. `EX`

`EX` đặt TTL theo giây:

```text
SET app:session:abc "user-42" EX 1800
```

### 9.2. `PX`

`PX` đặt TTL theo milliseconds:

```text
SET app:lock:order:123 token-abc PX 5000
```

### 9.3. `NX`

`NX` chỉ ghi nếu key chưa tồn tại:

```text
SET app:lock:order:123 token-abc NX PX 5000
```

Nếu key chưa có, Redis trả `OK`. Nếu key đã có, Redis không ghi và client nhận `nil`.

Đây là pattern cơ bản cho lock hoặc create-once. Tuy nhiên, distributed lock production cần thêm token ownership, release an toàn, timeout và phân tích failure sâu hơn.

### 9.4. `XX`

`XX` chỉ ghi nếu key đã tồn tại:

```text
SET app:config:version "42" XX
```

Nếu key chưa tồn tại, command không ghi.

### 9.5. Kết hợp options

Ví dụ thường gặp:

```text
SET app:token:abc user-42 NX EX 900
```

Ý nghĩa:

- Chỉ tạo nếu chưa tồn tại.
- TTL 900 giây.
- Value là `user-42`.

Luôn kiểm tra response. Không được giả định `SET` thành công chỉ vì command không ném exception.

---

## 10. `INCR` và `DECR`

Redis có thể dùng String để lưu số nguyên.

```text
SET app:counter:views 0
INCR app:counter:views
INCRBY app:counter:views 10
DECR app:counter:views
DECRBY app:counter:views 3
GET app:counter:views
```

Nếu key chưa tồn tại, `INCR` thường khởi tạo từ `0` rồi tăng lên `1`.

### 10.1. Atomic counter

Một command `INCR` là atomic trên một Redis instance: các client không cùng tăng cùng một giá trị theo kiểu mất update.

Đây là nền tảng cho:

- Page view counter.
- Số lần retry.
- Sequence number đơn giản.
- Rate limit counter.

### 10.2. TTL cho rate limit

Một flow đơn giản:

```text
INCR ratelimit:user:42:2026-10-01T10:30
EXPIRE ratelimit:user:42:2026-10-01T10:30 60
```

Nếu cần đảm bảo `INCR` và đặt TTL là một workflow atomic, phải dùng transaction hoặc Lua script phù hợp. Đặt TTL sau `INCR` nhiều lần có thể gia hạn cửa sổ không đúng ý định, nên cần xác định rõ fixed window hay sliding window.

---

## 11. Atomic command và workflow nhiều bước

Một command Redis thường được xử lý nguyên tử ở cấp command. Ví dụ:

```text
INCR app:counter
```

không bị một command khác chen vào giữa thao tác đọc và tăng bên trong Redis.

Nhưng workflow sau không tự động atomic:

```text
GET balance
SET balance new-value
```

Hai client có thể cùng đọc một balance cũ rồi ghi đè lẫn nhau.

Khi cần atomic workflow, các lựa chọn thường gặp là:

- Một command có sẵn như `INCRBY`.
- `MULTI`/`EXEC` transaction.
- `WATCH` optimistic locking.
- Lua script.
- Thiết kế lại data model để dùng command atomic.

Tuần này cần nhớ: “Redis nhanh” không đồng nghĩa “mọi chuỗi command đều atomic”.

---

## 12. Database index trong Redis

Redis mặc định có database index, thường bắt đầu với database `0`.

Chuyển database bằng:

```text
SELECT 1
```

Kiểm tra key ở database hiện tại:

```text
DBSIZE
```

### 12.1. Database index không phải namespace production

Không nên coi `SELECT 0`, `SELECT 1` là cách chính để tách:

- Development và production.
- Tenant khác nhau.
- Application khác nhau.

Các database index vẫn nằm trong cùng Redis process và có thể cùng chịu ảnh hưởng của memory pressure, restart hoặc misconfiguration. Nên tách môi trường bằng Redis instance/cluster hoặc deployment boundary, đồng thời dùng key namespace.

### 12.2. Tránh phụ thuộc vào `SELECT`

Redis Cluster không hỗ trợ database index giống standalone Redis. Vì vậy application production nên thiết kế key namespace thay vì phụ thuộc vào database index.

---

## 13. Thiết kế key

Key tốt giúp:

- Tránh collision.
- Dễ đọc khi debug.
- Dễ phân tích metrics.
- Dễ migrate hoặc invalidate theo nhóm.
- Giảm nguy cơ lẫn dữ liệu giữa môi trường và module.

### 13.1. Namespace rõ ràng

Một format gợi ý:

```text
application:environment:resource:identifier
```

Ví dụ:

```text
shop:prod:product:123
shop:prod:user:42:session
shop:prod:permission:user:42
shop:prod:search:v2:<hash>
```

Hoặc dùng format ngắn hơn nếu team đã thống nhất:

```text
app:product:123
app:session:42
```

Điều quan trọng là nhất quán, không phải dấu `:` hay format nào tuyệt đối đúng.

### 13.2. Key phải predictable

Với cùng một resource và identifier, application phải tạo ra cùng key:

```text
productId = 123 → app:product:123
```

Không đưa random value vào key nếu không có lý do. Nếu key không thể dự đoán từ input, việc đọc lại và invalidation sẽ khó.

### 13.3. Tránh collision

Không dùng key quá chung:

```text
123
user
config
```

Nên ghi rõ resource:

```text
app:product:123
app:user:123
app:config:checkout
```

### 13.4. Version trong key

Khi thay đổi schema hoặc serialization:

```text
app:product:v1:123
app:product:v2:123
```

Versioned key giúp application mới không đọc nhầm value của format cũ. Đặt TTL để key cũ tự thu hồi hoặc có kế hoạch cleanup rõ ràng.

### 13.5. Không đưa dữ liệu nhạy cảm vào key

Không nên đưa vào key:

- Password.
- Access token.
- Số thẻ đầy đủ.
- Email hoặc số điện thoại nếu không cần thiết.
- Nội dung query chứa dữ liệu cá nhân.

Key có thể xuất hiện trong log, metrics, dashboard hoặc error message. Dùng ID nội bộ hoặc hash có kiểm soát nếu cần biểu diễn nội dung query.

### 13.6. Độ dài key

Key cần đủ rõ nhưng không nên dài vô hạn. Key quá dài:

- Tốn memory.
- Tăng network payload.
- Làm log khó đọc.

Key ngắn kiểu `k1` không giúp maintainability; key dài chứa toàn bộ request cũng không tối ưu. Chọn độ dài cân bằng.

---

## 14. TTL và quy tắc key cache

Trong hệ thống cache, nên có quy tắc:

- Key cache phải có TTL trừ khi có lý do được ghi rõ.
- TTL phải phù hợp với stale budget.
- TTL được đặt ngay lúc tạo key.
- Không dùng `PERSIST` tùy tiện.
- Theo dõi số key không có TTL.
- Cân nhắc jitter cho nhóm key lớn.

Ví dụ:

```text
app:product:123            EX 300
app:search:v2:<hash>       EX 30
app:session:42             EX 1800
app:lock:order:123         PX 5000
```

Lock và cache có ý nghĩa TTL khác nhau:

- Cache TTL biểu diễn thời gian chấp nhận stale.
- Lock TTL là thời gian lease tối đa trước khi lock tự giải phóng.

Không dùng một TTL mặc định cho mọi loại key.

---

## 15. `KEYS` và `SCAN`

### 15.1. Vì sao không dùng `KEYS` production?

Lệnh:

```text
KEYS app:product:*
```

có thể quét toàn bộ keyspace. Với database lớn, `KEYS` có thể block Redis trong thời gian đáng kể, làm các request khác tăng latency.

`KEYS` có thể chấp nhận trong lab nhỏ hoặc debugging có kiểm soát, nhưng không nên dùng trong request path production.

### 15.2. Dùng `SCAN`

```text
SCAN 0 MATCH app:product:* COUNT 100
```

`SCAN` trả về cursor và một phần kết quả. Tiếp tục gọi bằng cursor mới cho đến khi cursor bằng `0`.

```text
SCAN 0 MATCH app:product:* COUNT 100
SCAN <cursor> MATCH app:product:* COUNT 100
...
SCAN 0
```

Lưu ý:

- `COUNT` chỉ là gợi ý, không phải số lượng chính xác.
- Có thể gặp duplicate trong một số điều kiện thay đổi keyspace.
- Không coi kết quả một vòng scan là snapshot tuyệt đối.
- Tốt nhất dùng để maintenance/offline tooling, không dùng trong hot request path.

### 15.3. Không dùng scan để thay thế thiết kế namespace

Nếu application thường xuyên cần tìm mọi key theo user hoặc product, có thể data model chưa phù hợp. Nên lưu index hoặc set liên quan thay vì dựa vào quét toàn keyspace.

---

## 16. Một số command quan sát hữu ích

### 16.1. `INFO`

```text
INFO server
INFO memory
INFO stats
```

Dùng để xem thông tin server, memory và một số thống kê.

### 16.2. `MEMORY USAGE`

```text
MEMORY USAGE app:product:123
```

Giúp ước lượng memory của một key. Kích thước value không phải toàn bộ chi phí; Redis còn có overhead cho key và cấu trúc dữ liệu.

### 16.3. `OBJECT ENCODING`

```text
OBJECT ENCODING app:product:123
```

Có thể giúp quan sát encoding nội bộ, nhưng không nên phụ thuộc vào implementation detail khi thiết kế business logic.

### 16.4. `COMMAND INFO`

```text
COMMAND INFO GET SET EXPIRE
```

Giúp xem metadata của command trên server.

### 16.5. `MONITOR`

`MONITOR` hiển thị command realtime:

```text
MONITOR
```

Chỉ dùng ngắn hạn trong môi trường phù hợp. Nó có thể tạo overhead lớn và làm lộ dữ liệu trong command/log, nên không bật tùy tiện trên production.

---

## 17. Lock đơn giản với `SET NX PX`

Một pattern cơ bản:

```text
SET lock:order:123 unique-token NX PX 5000
```

Nếu response là `OK`, client được xem là đã acquire lock trong tối đa 5 giây. Nếu response là `nil`, lock đang được giữ bởi client khác.

### 17.1. Vì sao cần unique token?

Client A có thể giữ lock, nhưng request xử lý lâu hơn TTL. Sau khi lock hết hạn, client B lấy được lock. Nếu A chạy `DEL lock:order:123` sau đó, A có thể xóa lock của B.

Vì vậy khi release cần chỉ xóa nếu value vẫn là token của mình, thường bằng Lua script atomic. Không dùng `DEL` mù quáng cho distributed lock.

### 17.2. Giới hạn của pattern đơn giản

`SET NX PX` là primitive hữu ích, nhưng lock production cần phân tích:

- Clock và timeout.
- Process pause hoặc GC pause.
- Network partition.
- Retry.
- Lease hết hạn trong lúc critical section.
- Fencing token nếu resource downstream yêu cầu bảo vệ mạnh.

Tuần 3 chỉ cần nắm pattern và các rủi ro nền tảng; lock nâng cao sẽ học sau.

---

## 18. Rate limit đơn giản với `INCR`

Ví dụ fixed window 60 giây:

```text
INCR ratelimit:user:42:window:12345
EXPIRE ratelimit:user:42:window:12345 60
```

Nếu counter vượt giới hạn, từ chối request.

Các điểm cần chú ý:

- Key phải có TTL để không tăng vô hạn.
- Cần quyết định thời điểm đặt TTL.
- Hai command `INCR` và `EXPIRE` có thể bị gián đoạn giữa chừng.
- Fixed window có boundary effect: client có thể gửi nhiều request ở hai đầu hai window.
- Production rate limit có thể cần Lua, sliding window hoặc token bucket.

---

## 19. Đếm lượt truy cập với counter

```text
INCR app:pageviews:product:123
```

Nếu cần theo ngày:

```text
INCR app:pageviews:product:123:2026-10-01
EXPIRE app:pageviews:product:123:2026-10-01 172800
```

TTL dài hơn một ngày giúp số liệu còn tồn tại qua thời điểm chuyển ngày để job tổng hợp có thời gian đọc. Đây là ví dụ cho thấy TTL nên dựa trên lifecycle của dữ liệu, không chỉ dựa trên cảm tính.

---

## 20. Các lỗi thiết kế phổ biến

### 20.1. Key cache không có TTL

```text
SET app:product:123 value
```

Nếu đây là dữ liệu cache, key có thể tồn tại mãi và làm memory tăng. Hãy đặt TTL ngay trong `SET` hoặc ghi rõ lý do không đặt TTL.

### 20.2. Đặt TTL bằng command thứ hai

```text
SET key value
EXPIRE key 300
```

Client crash giữa hai command có thể để lại key không expiration. Ưu tiên:

```text
SET key value EX 300
```

### 20.3. Dùng `KEYS` trong endpoint

Endpoint không nên gọi `KEYS app:*` để tìm dữ liệu. Thiết kế key hoặc index lại.

### 20.4. Dùng `EXISTS` rồi mới `GET`

Thường là thêm round trip không cần thiết và tạo race window. Gọi `GET` rồi xử lý `nil`.

### 20.5. Dùng key chung cho nhiều môi trường

```text
product:123
```

Nếu dev và production dùng chung Redis, có thể ghi đè dữ liệu. Dùng namespace hoặc tách instance.

### 20.6. Đưa token vào key hoặc log

Key có thể được log hoặc quan sát bởi operator. Không đưa secret vào key.

### 20.7. Tin rằng `INCR` làm mọi workflow atomic

`INCR` atomic, nhưng `GET → tính toán → SET` không tự động atomic. Cần command phù hợp, transaction hoặc script.

### 20.8. Đặt value quá lớn

Cache một object khổng lồ làm tăng memory, network payload, serialization latency và eviction pressure. Cân nhắc cache field cần thiết hoặc chia nhỏ dữ liệu.

---

## 21. Lab tổng hợp

### 21.1. Product cache

```text
SET shop:dev:product:123 '{"id":123,"name":"Keyboard","price":1200000}' EX 300
GET shop:dev:product:123
TTL shop:dev:product:123
TYPE shop:dev:product:123
```

### 21.2. Kiểm tra miss

```text
GET shop:dev:product:999
TTL shop:dev:product:999
```

Ghi nhận sự khác nhau giữa response `nil` của `GET` và `-2` của `TTL`.

### 21.3. Cập nhật và xóa

```text
SET shop:dev:product:123 '{"id":123,"price":1200000}' EX 300
DEL shop:dev:product:123
EXISTS shop:dev:product:123
```

### 21.4. Lock

```text
SET shop:dev:lock:order:123 token-a NX PX 5000
SET shop:dev:lock:order:123 token-b NX PX 5000
GET shop:dev:lock:order:123
PTTL shop:dev:lock:order:123
```

Lệnh đầu tiên nên thành công; lệnh thứ hai nên không ghi.

### 21.5. Counter

```text
INCR shop:dev:views:product:123
INCRBY shop:dev:views:product:123 10
GET shop:dev:views:product:123
```

### 21.6. Scan key

```text
SCAN 0 MATCH shop:dev:* COUNT 10
```

Thử tạo vài key, lặp cursor cho đến khi cursor quay về `0`. Không dùng `KEYS` trong bài endpoint.

---

## 22. Bài tập Java/Spring Boot

### Bài 1: Repository cache-aside

Viết service có method:

```java
Product getById(long id)
```

Yêu cầu:

- Tạo key theo format `shop:dev:product:{id}`.
- Đọc Redis trước.
- Cache miss thì đọc repository.
- Ghi Redis với TTL 5 phút.
- Không dùng `EXISTS` trước `GET`.

### Bài 2: Kiểm tra TTL

Viết một command hoặc integration test chứng minh:

- Key có TTL trả số dương.
- Key không có TTL trả `-1`.
- Key không tồn tại trả `-2`.

### Bài 3: Lock đơn giản

Viết method acquire lock với token ngẫu nhiên và `SET NX PX`. Test hai thread cùng acquire một lock và xác nhận chỉ một thread thành công.

### Bài 4: Counter có expiration

Tạo rate limiter đơn giản theo user. Ghi lại:

- Counter hiện tại.
- TTL còn lại.
- Hành vi khi vượt giới hạn.
- Trường hợp `INCR` thành công nhưng đặt TTL thất bại.

### Bài 5: Key contract

Viết tài liệu ngắn cho key của ứng dụng:

```text
Namespace:
Environment:
Resource:
Identifier:
Version:
TTL:
Sensitive data policy:
```

---

## 23. Câu hỏi tự kiểm tra

1. Redis server khác Redis client như thế nào?
2. `GET` trả gì khi key không tồn tại?
3. `TTL` trả gì cho key không có expiration và key không tồn tại?
4. Vì sao nên đặt TTL ngay trong `SET`?
5. `NX` và `XX` khác nhau thế nào?
6. Khi nào dùng `EX`, khi nào dùng `PX`?
7. Vì sao một command `INCR` atomic nhưng `GET → SET` không atomic?
8. Vì sao không dùng database index để tách production và development?
9. Vì sao `KEYS` nguy hiểm trên production?
10. `SCAN` có phải snapshot tuyệt đối của keyspace không?
11. Vì sao distributed lock cần unique token?
12. Tại sao key không nên chứa password hoặc access token?

---

## 24. Tiêu chí hoàn thành Tuần 3

Bạn hoàn thành bài học khi có thể:

- Kết nối Redis bằng `redis-cli` và kiểm tra `PING`.
- Tạo, đọc, cập nhật và xóa String key.
- Đọc được trạng thái TTL `-1`, `-2` và giá trị dương.
- Dùng `SET ... EX`, `SET ... PX`, `NX` và `XX` đúng mục đích.
- Tạo counter và hiểu giới hạn của atomic command.
- Thiết kế key có namespace, environment, resource và version.
- Giải thích vì sao cache key thường cần TTL.
- Dùng `SCAN` thay cho `KEYS` khi cần quét keyspace.
- Nhận diện rủi ro của key không có TTL, key chứa dữ liệu nhạy cảm và workflow nhiều command.
- Hoàn thành lab product cache, lock, counter và rate limit.

Kết luận cần nhớ:

> Redis command là các primitive nhỏ nhưng có ảnh hưởng trực tiếp đến tính đúng đắn của hệ thống. Key cần predictable và có namespace; dữ liệu tạm cần TTL; một command atomic không có nghĩa là cả workflow nhiều command cũng atomic.


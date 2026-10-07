# Tuần 2: Cache strategy và consistency

## 1. Mục tiêu

Sau bài này, bạn có thể:

- Phân biệt cache-aside, read-through, write-through, write-behind và refresh-ahead.
- Chọn strategy dựa trên read/write ratio, latency và mức stale có thể chấp nhận.
- Hiểu TTL, cache invalidation, event-based invalidation và versioned key.
- Phân biệt strong consistency, eventual consistency và causal consistency ở mức khái niệm.
- Viết quyết định caching cho product detail, session, inventory, search result và permission.
- Thiết kế hành vi khi cache miss, database update hoặc Redis bị lỗi.

---

## 2. Vì sao cần cache strategy?

Cache giúp giảm latency và tải cho database, nhưng ngay khi dữ liệu thay đổi, một câu hỏi quan trọng xuất hiện:

```text
Database đã có dữ liệu mới, cache phải làm gì?
```

Ví dụ:

```text
10:00:00  Database: product 123 có giá 100
10:00:01  Cache:    product 123 có giá 100
10:00:05  Admin cập nhật database thành giá 120
10:00:06  API đọc cache và vẫn trả giá 100
```

Nếu không có strategy rõ ràng, hệ thống có thể:

- Trả dữ liệu cũ.
- Tạo cache miss và tải đột biến lên database.
- Ghi database thành công nhưng cache không được cập nhật.
- Cập nhật cache thành công nhưng database thất bại.
- Khiến các service nhìn thấy các phiên bản dữ liệu khác nhau.

Cache strategy mô tả dữ liệu đi qua hệ thống như thế nào. Consistency mô tả các bản sao có thể khác nhau trong bao lâu và khi nào chúng phải hội tụ.

---

## 3. Bốn câu hỏi trước khi chọn strategy

### 3.1. Source of truth là gì?

Source of truth là nơi được xem là nguồn dữ liệu chính xác nhất. Trong thiết kế thông thường:

```text
Database = source of truth
Cache    = bản sao tối ưu cho đọc
```

Nếu xóa toàn bộ cache mà hệ thống vẫn phục hồi được bằng cách đọc database, thiết kế đó dễ vận hành an toàn hơn.

### 3.2. Dữ liệu được đọc và ghi với tần suất nào?

```text
Product detail: 10.000 reads / 20 writes  → read-heavy
Inventory:          500 reads / 2.000 writes → write-heavy
```

Read-heavy thường phù hợp với caching hơn. Write-heavy cần kiểm soát consistency và thứ tự cập nhật cẩn thận.

### 3.3. Dữ liệu được phép stale bao lâu?

Hãy ghi rõ stale budget:

```text
Product description: 5 phút
Search result:       30 giây
Permission:           rất ngắn hoặc invalidate ngay
Inventory checkout:  không được dùng cache làm quyết định cuối
```

Không có một TTL đúng cho mọi loại dữ liệu.

### 3.4. Nếu cache hỏng thì sao?

Cần quyết định:

- API có đọc thẳng database được không?
- Có được trả stale data tạm thời không?
- Ghi database thành công nhưng ghi cache thất bại thì request có thất bại không?
- Redis outage có thể làm database quá tải không?

---

## 4. Cache-aside / lazy loading

Đây là strategy phổ biến nhất khi bắt đầu tích hợp Redis.

### 4.1. Flow đọc

```text
1. Ứng dụng đọc cache
2. Nếu hit: trả dữ liệu
3. Nếu miss: đọc database
4. Ghi kết quả vào cache
5. Trả dữ liệu cho client
```

```text
Request
   │
   ▼
Cache ── hit ──> Response
   │
  miss
   ▼
Database ──> Ghi cache ──> Response
```

### 4.2. Flow ghi

Một cách phổ biến là ghi database trước, sau đó xóa cache:

```text
1. Ghi database
2. Nếu thành công, xóa cache tương ứng
3. Lần đọc sau sẽ cache miss và nạp dữ liệu mới
```

```java
public Product getById(long id) {
    String key = "app:product:" + id;

    Product cached = redis.get(key);
    if (cached != null) {
        return cached;
    }

    Product product = repository.findById(id)
        .orElseThrow(() -> new ProductNotFoundException(id));

    redis.set(key, product, Duration.ofMinutes(5));
    return product;
}

@Transactional
public Product update(long id, UpdateProductCommand command) {
    Product updated = repository.update(id, command);
    redis.delete("app:product:" + id);
    return updated;
}
```

### 4.3. Ưu điểm và rủi ro

Ưu điểm:

- Dễ hiểu, dễ áp dụng.
- Chỉ cache dữ liệu thực sự được đọc.
- Database vẫn là source of truth.
- Có thể đặt TTL khác nhau cho từng loại dữ liệu.

Rủi ro:

- Request đầu tiên sau miss sẽ chậm.
- Quên xóa cache khiến dữ liệu cũ tiếp tục được trả.
- Nhiều request cùng miss có thể cùng query database.
- Có khoảng thời gian database đã đổi nhưng cache chưa bị xóa.

Cache-aside phù hợp với product detail, profile, cấu hình và nội dung đọc nhiều nhưng không yêu cầu đồng bộ tuyệt đối từng mili giây.

---

## 5. Read-through cache

Với read-through, application đọc thông qua cache layer. Cache layer tự tải dữ liệu từ database khi xảy ra miss.

```text
Application → Cache layer
                  ├─ hit  → trả dữ liệu
                  └─ miss → gọi database → lưu cache → trả dữ liệu
```

Khác biệt chính:

- Cache-aside: application chứa logic `miss → query database → set cache`.
- Read-through: logic đó nằm trong cache provider hoặc abstraction.

Ưu điểm:

- Giảm logic cache lặp lại trong application.
- Chuẩn hóa TTL, serialization và metrics.

Rủi ro:

- Cấu hình và debugging phức tạp hơn.
- Provider có thể che giấu hành vi quan trọng.
- Không phải database/framework nào cũng hỗ trợ tự nhiên.

`@Cacheable` trong Spring có thể tạo cảm giác giống read-through, nhưng annotation không thay thế cache design. Vẫn phải hiểu proxy, key, TTL, provider và invalidation.

---

## 6. Write-through cache

Với write-through, application ghi thông qua cache layer. Cache layer cập nhật cache và ghi dữ liệu xuống database trong cùng workflow.

```text
Application → Cache layer → Database
                   │
                   └─ cập nhật cache
```

Ưu điểm:

- Cache được cập nhật ngay trong write flow.
- Giảm khả năng cache chứa bản sao cũ sau write thành công.
- Read sau write có thể lấy giá trị mới từ cache.

Rủi ro:

- Write latency tăng.
- Cache và database thường không nằm trong cùng transaction thật sự.
- Một service ghi thẳng database có thể làm cache stale.
- Cần xử lý partial failure, retry và thứ tự write.

Write-through phù hợp khi toàn bộ đường ghi được kiểm soát qua một cache layer thống nhất.

---

## 7. Write-behind / write-back cache

Với write-behind, application ghi vào cache trước; database được cập nhật bất đồng bộ sau đó.

```text
Application → Cache → Response nhanh
                  │
                  └─ queue/worker → Database
```

Ưu điểm:

- Write latency thấp.
- Có thể gom nhiều thay đổi trước khi ghi database.
- Có thể giảm số lần ghi database.

Rủi ro:

- Cache giữ dữ liệu chưa bền vững.
- Redis hoặc process hỏng trước khi flush có thể làm mất dữ liệu.
- Cần queue, retry, ordering và dead-letter handling.
- Đọc database ngay sau write có thể vẫn thấy dữ liệu cũ.

Không nên dùng write-behind chỉ vì muốn API trả nhanh. Chỉ dùng khi chấp nhận được độ trễ ghi và đã có kế hoạch persistence, recovery, retry và reconciliation.

---

## 8. Refresh-ahead

Refresh-ahead chủ động làm mới cache trước khi key hết hạn.

```text
TTL sắp hết
    │
    └─ background worker đọc database và cập nhật cache
```

Ví dụ: banner trang chủ được đọc hàng triệu lần. Thay vì chờ key hết hạn sau 300 giây, worker có thể refresh ở giây 290 để tránh hàng nghìn request cùng cache miss.

Ưu điểm:

- Giảm miss của hot key.
- Tránh đợt truy vấn database đột biến.

Rủi ro:

- Tốn tài nguyên background refresh.
- Có thể refresh dữ liệu không còn được đọc.
- Nhiều worker có thể cùng refresh một key nếu không phối hợp.

---

## 9. So sánh các strategy

| Strategy | Đọc | Ghi | Ưu điểm | Rủi ro |
|---|---|---|---|---|
| Cache-aside | App đọc cache, miss thì đọc DB | Ghi DB rồi xóa/cập nhật cache | Đơn giản, kiểm soát rõ | Quên invalidation, cache miss |
| Read-through | Cache tự tải DB khi miss | Tùy hệ thống | Ít logic lặp ở app | Abstraction/debug phức tạp |
| Write-through | Cache layer tham gia đọc | Cache và DB cùng write flow | Cache cập nhật nhanh | Write latency, partial failure |
| Write-behind | Cache phục vụ read | Cache trước, DB async | Write nhanh, batching | Mất dữ liệu, recovery khó |
| Refresh-ahead | Cache được làm mới trước expiry | Không thay đổi bản chất write | Giảm miss của key nóng | Tốn tài nguyên refresh |

Một hệ thống có thể dùng strategy khác nhau cho các loại dữ liệu khác nhau.

---

## 10. Cache invalidation

Cache invalidation là làm cho bản sao trong cache không còn được sử dụng sau khi dữ liệu gốc thay đổi.

Ba câu hỏi phải tách riêng:

1. Khi nào cache được tạo?
2. Khi nào cache được cập nhật?
3. Khi nào cache bị xóa hoặc hết hạn?

### 10.1. Xóa cache sau database update

```text
Write database thành công → DEL cache key
```

Ưu điểm:

- Lần đọc tiếp theo nạp dữ liệu mới.
- Không phải duplicate logic dựng object cache.
- Giảm nguy cơ cache chứa object không đầy đủ.

Rủi ro:

- `DEL` thất bại thì cache vẫn cũ.
- Tất cả đường ghi phải thực hiện invalidation.
- Có thể xảy ra race condition giữa read và delete.

### 10.2. Cập nhật trực tiếp cache

```text
Write database thành công → SET cache bằng object mới
```

Cách này tránh cache miss sau update, nhưng object cache có thể không giống dữ liệu sau khi database áp dụng default, trigger, version hoặc logic khác. Chỉ dùng khi chắc chắn dữ liệu được dựng chính xác.

### 10.3. TTL-based invalidation

```text
SET app:product:123 value EX 300
```

TTL là safety net đơn giản, nhưng không làm cache mới ngay sau update. TTL quá ngắn làm hit ratio thấp; TTL quá dài làm stale data tồn tại lâu.

Nên thêm jitter cho những nhóm key lớn để tránh hết hạn cùng lúc:

```text
TTL thực tế = 300 giây + random(0..60 giây)
```

Jitter không được phá vỡ stale budget của business.

### 10.4. Event-based invalidation

Khi database thay đổi, phát event:

```text
ProductUpdated(productId=123)
        │
        ├─ Service A xóa cache product:123
        ├─ Service B cập nhật search index
        └─ Service C cập nhật projection
```

Ưu điểm:

- Nhiều service phản ứng với cùng một thay đổi.
- Giảm thời gian stale nếu event được xử lý nhanh.

Rủi ro:

- Event có thể trễ, trùng hoặc sai thứ tự.
- Consumer có thể tạm thời down.
- Cần retry, idempotency và monitoring.

### 10.5. Versioned key

Thay vì xóa key cũ, ứng dụng đổi version:

```text
app:product:v1:123
app:product:v2:123
```

Khi version tăng, request chỉ đọc key mới; key cũ được thu hồi bằng TTL. Cách này hữu ích khi thay đổi format serialization, schema hoặc có nhiều biến thể dữ liệu.

Đổi lại, key cũ có thể tồn tại đến khi TTL hết hạn và làm tăng memory usage.

---

## 11. Consistency là gì?

Consistency mô tả mức độ các reader nhìn thấy cùng một phiên bản dữ liệu và thời điểm thay đổi được phản ánh.

Câu hỏi thực tế:

```text
Sau khi write thành công, request đọc tiếp theo có bắt buộc thấy dữ liệu mới không?
```

Câu trả lời phụ thuộc vào business requirement, không phụ thuộc vào việc ta thích Redis hay database nào.

### 11.1. Strong consistency

Sau khi write được xác nhận thành công, các read tiếp theo nhìn thấy giá trị mới theo mô hình đã cam kết.

Phù hợp với:

- Quyết định cho phép/từ chối giao dịch.
- Số dư tài khoản trong bước thanh toán.
- Kiểm tra tồn kho cuối cùng.
- Trạng thái khóa tài khoản.

Cache không nên là nguồn quyết định cuối cùng cho các dữ liệu này. Có thể dùng cache để tối ưu read phụ, nhưng bước commit phải xác nhận với source of truth.

### 11.2. Eventual consistency

Các bản sao được phép khác nhau tạm thời, nhưng nếu không có thay đổi mới thì cuối cùng sẽ hội tụ.

```text
10:00:00  Database cập nhật product name
10:00:01  Service catalog nhận event
10:00:02  Cache bị xóa
10:00:03  Request nạp tên mới
```

Phù hợp với:

- Product description.
- Search index.
- Recommendation.
- Dashboard.
- Feed hoặc notification có thể trễ vài giây.

Eventual consistency chỉ an toàn khi biết stale budget và có monitoring để phát hiện bản sao không hội tụ.

### 11.3. Causal consistency

Causal consistency bảo đảm những thay đổi có quan hệ nguyên nhân-kết quả được nhìn thấy theo đúng thứ tự logic.

Ví dụ:

```text
1. User tạo bài viết.
2. User sửa tiêu đề.
```

Reader không nên quan sát trạng thái “đã sửa tiêu đề” trước khi thấy bài viết tồn tại theo cách tạo ra trạng thái vô nghĩa.

Các công cụ thường liên quan đến causal consistency:

- Event ordering.
- Version hoặc sequence number.
- Logical timestamp.
- Consumer idempotent.

---

## 12. TTL không đồng nghĩa với consistency

TTL giới hạn thời gian một key tồn tại, chứ không bảo đảm cache mới ngay sau database update.

```text
Product description: TTL 300 giây
Search result:       TTL 30 giây
Permission:           TTL ngắn + event invalidation
```

Nếu database update ở giây thứ 1 và TTL còn 299 giây, cache vẫn có thể trả dữ liệu cũ trong khoảng đó. Vì vậy thường kết hợp:

```text
Event/delete để cập nhật nhanh
TTL để làm safety net nếu event hoặc invalidation thất bại
```

TTL quá ngắn làm tăng miss và tải database. TTL quá dài làm tăng stale risk.

---

## 13. Race condition giữa đọc, ghi và invalidation

Flow nguy hiểm:

```text
Request A: đọc database, lấy giá cũ 100
Request B: cập nhật database thành 120
Request B: xóa cache
Request A: ghi giá cũ 100 vào cache
```

Kết quả: dù B đã xóa cache, A lại ghi dữ liệu cũ vào cache.

Một số cách giảm rủi ro:

- Ghi database rồi xóa cache.
- Dùng version trong value hoặc key.
- Chỉ ghi cache nếu version mới hơn version hiện tại.
- Dùng lock cho workflow nạp dữ liệu quan trọng.
- Dùng TTL làm safety net.
- Dùng event có sequence/version và bỏ qua event cũ.

Không nên nghĩ rằng hai lệnh `SET` và `DEL` ở các request khác nhau tự động tạo thành một transaction logic.

---

## 14. Thiết kế khi cache failure

### 14.1. Fail open

Nếu đọc cache lỗi, bỏ qua cache và đọc database:

```text
Redis error → Database → Response
```

Phù hợp với product detail nếu database có thể chịu tải thêm. Nên có timeout ngắn, circuit breaker và giới hạn fallback để Redis outage không làm sập database.

### 14.2. Fail closed

Nếu cache lỗi, từ chối request hoặc trả lỗi. Chỉ phù hợp khi cache là thành phần bắt buộc và việc tiếp tục sẽ không an toàn.

### 14.3. Serve stale

Nếu có bản sao cũ, hệ thống có thể trả stale data tạm thời khi source không sẵn sàng. Chỉ dùng cho dữ liệu cho phép stale và cần ghi rõ trong requirement.

Không dùng cùng một fallback cho product description, permission và inventory. Mức consistency của chúng khác nhau.

---

## 15. Quy trình chọn strategy

```text
1. Xác định source of truth
2. Đo read/write ratio
3. Đo chi phí đọc từ source
4. Xác định stale budget
5. Chọn read strategy
6. Chọn write/invalidation strategy
7. Chọn TTL và versioning
8. Thiết kế cache failure behavior
9. Định nghĩa metrics và test concurrency
```

Mẫu quyết định:

```text
Data: Product detail
Source of truth: PostgreSQL
Read strategy: cache-aside
Write strategy: update DB rồi delete cache
TTL: 5 phút
Consistency: eventual, stale tối đa khoảng 5 phút
Failure: Redis lỗi thì đọc DB
Metrics: hit ratio, miss ratio, p95 latency, DB query rate
```

---

## 16. Bài tập quyết định caching

### 16.1. Product detail

```text
Strategy: cache-aside
Write: update database rồi delete cache
TTL: 5–15 phút tùy freshness requirement
Consistency: eventual cho thông tin mô tả
Lưu ý: giá và tồn kho cần quy tắc riêng
```

### 16.2. User session

```text
Strategy: session store/read-write
TTL: gắn với thời gian session còn hiệu lực
Consistency: revoke/logout cần phản ánh nhanh
Lưu ý: không lưu secret hoặc dữ liệu nhạy cảm quá mức
```

### 16.3. Inventory

```text
Không dùng cache làm nguồn quyết định cuối cùng.
Database hoặc inventory service xác nhận số lượng khi commit.
Cache chỉ dùng cho hiển thị gần đúng hoặc read optimization.
```

### 16.4. Search result

```text
Strategy: cache-aside
Key: hash của query + filter + page + sort + version
TTL: 15–60 giây
Consistency: eventual
Lưu ý: giới hạn kích thước và số lượng query được cache
```

### 16.5. Permission

```text
Có thể cache với TTL ngắn.
Ưu tiên event-based invalidation khi quyền thay đổi.
Không dựa vào cache stale dài để quyết định thao tác nhạy cảm.
```

### 16.6. Bảng tổng hợp

| Dữ liệu | Strategy | Invalidation | Consistency |
|---|---|---|---|
| Product detail | Cache-aside | TTL + xóa sau update | Eventual |
| User session | Session store | TTL + logout/revoke | Gần strong cho revoke |
| Inventory | Không tin cache làm nguồn cuối | Transaction/domain event | Strong ở commit |
| Search result | Cache-aside | TTL ngắn + index version | Eventual |
| Permission | Cache-aside/read-through | TTL ngắn + event | Strong/gần strong cho deny |

---

## 17. Bài thực hành trong tuần

### Bài 1: Viết quyết định caching

Với product detail, user session, inventory, search result và permission, điền:

```text
Data:
Source of truth:
Read/write ratio:
Strategy:
TTL:
Invalidation:
Allowed stale time:
Cache failure behavior:
Reason:
```

### Bài 2: Phân tích race condition

Cho flow:

```text
Request A đọc giá cũ từ database
Request B update giá mới vào database
Request B delete cache
Request A set giá cũ vào cache
```

Đề xuất ít nhất hai cách xử lý và nêu trade-off của từng cách.

### Bài 3: TTL hay event?

Chọn một dữ liệu có thể stale 30 giây và một dữ liệu cần phản ánh thay đổi gần như ngay lập tức. Giải thích vì sao chọn TTL, event invalidation hoặc kết hợp cả hai.

### Bài 4: Thiết kế fallback

Viết behavior khi Redis timeout trong ba trường hợp:

1. Đọc product detail.
2. Kiểm tra permission.
3. Kiểm tra inventory khi checkout.

### Bài 5: Đo consistency

Tạo một test cập nhật product rồi lập tức đọc lại nhiều lần. Ghi nhận:

- Lần đọc đầu tiên thấy phiên bản nào.
- Mất bao lâu để cache hội tụ.
- Có request nào thấy phiên bản cũ sau khi invalidation không.
- Redis hoặc database lỗi thì hệ thống phản ứng thế nào.

---

## 18. Câu hỏi tự kiểm tra

1. Khi nào cache-aside là lựa chọn tốt?
2. Read-through khác cache-aside ở nơi đặt logic cache miss như thế nào?
3. Write-through giải quyết vấn đề gì và không giải quyết được vấn đề gì?
4. Vì sao write-behind có rủi ro mất dữ liệu?
5. Refresh-ahead giúp hot key như thế nào?
6. TTL có đảm bảo cache cập nhật ngay sau database update không?
7. Vì sao event invalidation cần idempotency và retry?
8. Strong consistency có phải luôn là lựa chọn tốt nhất không?
9. Vì sao inventory không nên chỉ đọc từ cache khi checkout?
10. Cache failure nên fail open hay fail closed trong product detail và permission?

---

## 19. Tiêu chí hoàn thành Tuần 2

Bạn hoàn thành bài học khi có thể:

- Chọn strategy cho một workload cụ thể và giải thích trade-off.
- Vẽ read path và write path của cache-aside.
- Mô tả TTL, event invalidation và versioned key.
- Phát hiện race condition giữa read, write và invalidation.
- Nêu stale budget cho từng loại dữ liệu.
- Phân biệt eventual consistency với dữ liệu cần strong consistency.
- Thiết kế fallback khi Redis timeout hoặc mất kết nối.
- Ghi rõ source of truth và lý do không dùng cache làm database duy nhất.

Kết luận cần nhớ:

> Cache strategy quyết định dữ liệu được đọc, ghi và làm mới như thế nào; consistency quyết định hệ thống chấp nhận các bản sao khác nhau trong bao lâu. Tốc độ chỉ là một phần của bài toán — strategy tốt phải kiểm soát cả stale data, failure và khả năng phục hồi.


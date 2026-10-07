# Tuần 1: Vì sao cần cache?

## 1. Mục tiêu của bài học

Sau bài này, bạn có thể:

- Giải thích vì sao một request đọc dữ liệu có thể chậm dù code rất đơn giản.
- Phân biệt latency của ứng dụng, network, database và serialization.
- Mô tả chính xác cache hit và cache miss.
- Tính hit ratio và miss ratio từ số liệu thực tế.
- Nhận diện workload read-heavy, write-heavy và hot key.
- Đánh giá một loại dữ liệu có cache được hay không.
- Phân biệt freshness với staleness và quyết định mức stale có thể chấp nhận.
- Hiểu cache-aside và vai trò của source of truth.
- Biết cache giúp giảm tải và latency, nhưng không tự động làm dữ liệu nhất quán hơn.

---

## 2. Cache là gì?

Cache là một vùng lưu trữ dữ liệu tạm thời, được đặt gần nơi sử dụng dữ liệu để những lần đọc sau nhanh hơn. Dữ liệu trong cache thường là bản sao của dữ liệu ở một hệ thống khác, chẳng hạn database hoặc một service bên ngoài.

Ví dụ, endpoint lấy thông tin sản phẩm thường có flow cơ bản:

```text
Client → API → Database → API → Client
```

Nếu sản phẩm `123` được đọc nhiều lần, ta có thể lưu bản sao của nó vào Redis:

```text
Client → API → Redis → API → Client
                   └─ cache miss → Database → Redis
```

Điểm quan trọng: cache không phải source of truth trong thiết kế thông thường. Database vẫn là nơi dữ liệu gốc được lưu và quyết định dữ liệu nào là đúng. Cache chỉ là bản sao tối ưu cho việc đọc.

## 3. Vì sao database có thể trở thành nút thắt?

Một câu lệnh như sau trông rất đơn giản:

```sql
SELECT id, name, price
FROM products
WHERE id = 123;
```

Nhưng request thực tế có thể phải trả các loại chi phí sau:

### 3.1. Chi phí xử lý trong ứng dụng

- Nhận request và parse HTTP.
- Xác thực người dùng.
- Kiểm tra quyền.
- Tạo connection hoặc lấy connection từ pool.
- Chuyển đổi dữ liệu sang object Java.

### 3.2. Chi phí network

API và database thường không nằm trong cùng process. Request phải đi qua network, dù hai máy ở cùng datacenter. Chi phí này bao gồm:

- Thời gian gửi query.
- Thời gian chờ database xử lý.
- Thời gian trả result về ứng dụng.
- Các lần round trip nếu query hoặc workflow có nhiều bước.

### 3.3. Chi phí database

Database có thể phải:

- Tìm index.
- Đọc page từ memory hoặc disk.
- Join nhiều bảng.
- Sort, filter, aggregate.
- Chờ lock hoặc chờ tài nguyên CPU, I/O.

Một query nhanh khi hệ thống ít tải chưa chắc vẫn nhanh khi có hàng nghìn request đồng thời.

### 3.4. Chi phí serialization

Sau khi lấy dữ liệu, ứng dụng còn phải chuyển đổi:

```text
Database row → Java object → JSON response
```

Nếu object lớn, có nhiều quan hệ lồng nhau hoặc cần mapping phức tạp, serialization và deserialization cũng tạo thêm latency và CPU usage.

Cache không xóa toàn bộ chi phí của request, nhưng có thể bỏ qua phần database query và một phần mapping tốn kém.

---

## 4. Latency: nhìn vào thời gian chậm nhất, không chỉ thời gian trung bình

Latency là thời gian từ lúc hệ thống nhận request đến lúc trả response. Khi đo hiệu năng, không nên chỉ nhìn average latency.

Ví dụ:

| Chỉ số | Giá trị |
|---|---:|
| p50 | 20 ms |
| p95 | 80 ms |
| p99 | 900 ms |

`p99 = 900 ms` nghĩa là khoảng 1% request chậm từ 900 ms trở lên. Với hệ thống có nhiều request, một request chậm còn có thể giữ thread, connection và làm các request khác chậm theo.

Cache thường có ích nhất khi:

- Dữ liệu được đọc lặp lại.
- Query database tốn thời gian.
- Dữ liệu không thay đổi liên tục.
- Latency thấp quan trọng đối với trải nghiệm người dùng.

Không nên giả định rằng thêm cache chắc chắn làm hệ thống nhanh hơn. Cần đo trước và sau khi thay đổi.

---

## 5. Cache hit và cache miss

### 5.1. Cache hit

Cache hit xảy ra khi ứng dụng tìm thấy dữ liệu cần đọc trong cache:

```text
Request
  → GET app:product:123
  → Có dữ liệu
  → Trả response
```

Trong trường hợp này, ứng dụng không cần query database. Đây là đường đi nhanh.

### 5.2. Cache miss

Cache miss xảy ra khi key không tồn tại, đã hết TTL hoặc dữ liệu trong cache không thể sử dụng:

```text
Request
  → GET app:product:123
  → Không có dữ liệu
  → Query database
  → Ghi kết quả vào cache
  → Trả response
```

Cache miss vẫn phải trả chi phí database query. Vì vậy cache không biến mọi request thành request nhanh; cache chỉ giúp những request gặp hit đi theo đường ngắn hơn.

### 5.3. Công thức hit ratio

```text
hit ratio  = số cache hit / tổng số lần đọc cache
miss ratio = số cache miss / tổng số lần đọc cache

miss ratio = 1 - hit ratio
```

Ví dụ, trong 10.000 lần đọc:

- 9.000 lần hit.
- 1.000 lần miss.

Khi đó:

```text
hit ratio  = 9.000 / 10.000 = 90%
miss ratio = 1.000 / 10.000 = 10%
```

Hit ratio cao thường là tín hiệu tốt, nhưng không phải tiêu chí duy nhất. Một cache hit 99% vẫn có thể gây vấn đề nếu dữ liệu bị stale, cache hết bộ nhớ hoặc một hot key bị lỗi.

---

## 6. Read-heavy và write-heavy workload

### 6.1. Read-heavy

Read-heavy là workload có số lần đọc lớn hơn nhiều so với số lần ghi.

Ví dụ:

- Trang chi tiết sản phẩm.
- Danh sách cấu hình ứng dụng.
- Nội dung bài viết.
- Tỷ giá hoặc dữ liệu tham khảo.
- Thông tin profile được đọc nhiều lần.

Đây là nhóm thường phù hợp với cache vì một bản sao có thể phục vụ nhiều lần đọc.

### 6.2. Write-heavy

Write-heavy là workload có nhiều thao tác ghi hoặc dữ liệu thay đổi liên tục.

Ví dụ:

- Số lượng tồn kho thay đổi liên tục.
- Trạng thái giao dịch tài chính.
- Counter ghi với tần suất rất cao.
- Dữ liệu cảm biến thời gian thực.

Cache vẫn có thể được dùng, nhưng phải thiết kế consistency cẩn thận. Nếu cache được dữ liệu thay đổi liên tục mà không invalidation đúng, người dùng có thể thấy dữ liệu sai.

### 6.3. Câu hỏi cần đặt ra

Trước khi cache một endpoint, hãy hỏi:

1. Dữ liệu này được đọc bao nhiêu lần?
2. Dữ liệu được ghi hoặc thay đổi bao nhiêu lần?
3. Chi phí đọc từ source hiện tại là bao nhiêu?
4. Dữ liệu cũ tối đa bao lâu vẫn chấp nhận được?
5. Nếu cache mất toàn bộ dữ liệu, hệ thống có hoạt động được không?

---

## 7. Hot key và working set

### 7.1. Hot key

Hot key là một key được truy cập nhiều hơn đáng kể so với các key khác.

Ví dụ:

```text
app:product:123       500.000 lượt đọc/ngày
app:product:456            20 lượt đọc/ngày
app:product:789             5 lượt đọc/ngày
```

Sản phẩm `123` là hot key. Cache hot key thường đem lại lợi ích lớn vì một dữ liệu có thể phục vụ rất nhiều request.

Tuy nhiên, hot key cũng có rủi ro:

- Nếu key hết hạn, nhiều request cùng lúc có thể query database.
- Nếu node chứa key gặp sự cố, lượng request dồn xuống backend.
- Một key quá nóng có thể tạo mất cân bằng tải.

Tuần 1 chỉ cần nhận diện rủi ro này; các kỹ thuật xử lý stampede sẽ học ở phần sau.

### 7.2. Working set

Working set là tập dữ liệu được truy cập thường xuyên trong một khoảng thời gian.

Ví dụ, hệ thống có 10 triệu sản phẩm nhưng trong một ngày chỉ có 200.000 sản phẩm được xem. 200.000 sản phẩm đó gần với working set của ngày hôm đó.

Cache không nhất thiết phải chứa toàn bộ database. Mục tiêu thường là giữ working set quan trọng trong dung lượng cache phù hợp.

---

## 8. Khi nào dữ liệu có thể cache được?

Một dữ liệu có tính cacheability cao thường có các đặc điểm:

- Được đọc nhiều lần.
- Kết quả giống nhau cho nhiều request hoặc nhiều người dùng.
- Chi phí tạo hoặc lấy dữ liệu cao.
- Không thay đổi quá thường xuyên.
- Có thể xác định thời gian sống hợp lý.
- Không chứa dữ liệu riêng tư cần cô lập nghiêm ngặt.

Ví dụ phù hợp:

- Product detail công khai.
- Danh sách quốc gia, tỉnh thành.
- Cấu hình tính năng đã publish.
- Nội dung bài viết.
- Kết quả tổng hợp có thể chấp nhận trễ vài phút.

Ví dụ cần thận trọng:

- Số dư tài khoản.
- Quyền truy cập vừa thay đổi.
- Tồn kho trong lúc checkout.
- Trạng thái thanh toán.
- Dữ liệu cá nhân theo từng user.

Không cache một dữ liệu chỉ vì “query này chậm”. Cần biết dữ liệu đó có thể stale đến mức nào và stale data có gây thiệt hại hay không.

---

## 9. Freshness và staleness

### 9.1. Freshness

Freshness mô tả dữ liệu trong cache gần với dữ liệu gốc đến mức nào. Dữ liệu vừa được cập nhật sau khi database thay đổi có freshness cao.

### 9.2. Staleness

Staleness là độ cũ của dữ liệu cache so với source of truth.

Ví dụ:

```text
10:00:00  Database: giá sản phẩm = 100
10:00:05  Database: giá sản phẩm = 120
10:00:20  Cache vẫn trả giá = 100
```

Trong khoảng từ `10:00:05` đến `10:00:20`, cache bị stale 15 giây.

Stale data không phải lúc nào cũng là bug. Một trang tin có thể chấp nhận dữ liệu chậm vài chục giây. Nhưng tồn kho hoặc trạng thái thanh toán có thể không chấp nhận dữ liệu cũ.

Khi cache dữ liệu, cần ghi rõ:

```text
Dữ liệu này được phép stale tối đa bao lâu?
```

TTL là một cách giới hạn thời gian stale, nhưng TTL không đảm bảo dữ liệu luôn mới ngay sau khi database thay đổi.

---

## 10. Cache capacity

Cache có dung lượng hữu hạn. Nếu dữ liệu mới được thêm vào khi cache đầy, một số dữ liệu cũ phải bị loại bỏ theo eviction policy.

Các yếu tố ảnh hưởng dung lượng:

- Số lượng key.
- Kích thước value.
- Overhead của Redis và cấu trúc dữ liệu.
- TTL và tốc độ ghi mới.
- Chính sách eviction.

Không nên nghĩ cache là “database rẻ hơn”. Cache phải được coi là tài nguyên có giới hạn và cần theo dõi memory usage.

Một cache có thể hoạt động tốt ở môi trường test nhưng thất bại ở production vì:

- Value thực tế lớn hơn dự kiến.
- Số lượng key tăng nhanh.
- Không đặt TTL.
- Một job tạo quá nhiều key tạm thời.

---

## 11. Cache-aside: pattern đầu tiên cần nắm

Cache-aside còn gọi là lazy loading. Ứng dụng chủ động đọc cache trước, rồi đọc database khi cache miss.

```text
1. Đọc cache
2. Nếu hit: trả dữ liệu
3. Nếu miss: đọc database
4. Ghi dữ liệu vào cache
5. Trả dữ liệu
```

Pseudo-code:

```java
public Product getProduct(long id) {
    String key = "app:product:" + id;

    Product cached = redis.get(key);
    if (cached != null) {
        return cached;
    }

    Product product = productRepository.findById(id)
        .orElseThrow(() -> new ProductNotFoundException(id));

    redis.set(key, product, Duration.ofMinutes(5));
    return product;
}
```

Ở pattern này:

- Database là source of truth.
- Cache được nạp khi có request đọc.
- Key chưa từng được đọc sẽ không chiếm cache.
- Cache miss làm request đầu tiên chậm hơn các request sau.

Khi cập nhật sản phẩm, ứng dụng thường phải xóa hoặc cập nhật key cache tương ứng. Nếu chỉ cập nhật database mà quên xử lý cache, API có thể trả dữ liệu cũ.

---

## 12. Flow bài toán sản phẩm

### Không có cache

```text
Request 1 → Database → Response
Request 2 → Database → Response
Request 3 → Database → Response
```

Nếu ba request cùng đọc một sản phẩm, database phải xử lý cả ba lần.

### Có cache-aside

```text
Request 1 → Cache miss → Database → Cache → Response
Request 2 → Cache hit ----------------→ Response
Request 3 → Cache hit ----------------→ Response
```

Lợi ích:

- Request 2 và 3 có latency thấp hơn.
- Database nhận ít query hơn.
- Database có thêm capacity cho các thao tác quan trọng khác.

Chi phí và rủi ro:

- Cần vận hành thêm Redis.
- Cần xử lý cache miss.
- Có thể trả stale data.
- Cần có chiến lược khi Redis không khả dụng.

---

## 13. Cache không giải quyết mọi vấn đề

Cache không tự động:

- Làm database nhanh hơn.
- Làm dữ liệu nhất quán hơn.
- Thay thế backup database.
- Thay thế transaction.
- Đảm bảo không có stale data.
- Sửa một query sai hoặc một schema kém.

Nếu cache miss ratio cao, hệ thống vẫn phụ thuộc nhiều vào database. Nếu dữ liệu bị invalidation sai, cache có thể làm người dùng thấy thông tin cũ lâu hơn. Nếu Redis hỏng mà ứng dụng không có fallback, việc thêm cache còn tạo thêm một điểm lỗi mới.

Vì vậy câu hỏi đúng không phải là “Có nên dùng Redis không?”, mà là:

```text
Dữ liệu nào nên cache, trong bao lâu, với rủi ro stale nào,
và hệ thống sẽ làm gì khi cache không hoạt động?
```

---

## 14. Bài thực hành trong tuần

### Bài 1: Vẽ hai flow

Chọn endpoint đọc sản phẩm và vẽ:

```text
Request → Database → Response

Request → Cache hit → Response
             ↓ miss
          Database → Cache → Response
```

Ghi chú latency dự kiến ở mỗi bước.

### Bài 2: Đo trước khi cache

Gửi cùng một workload đến endpoint, ví dụ 1.000 request đọc sản phẩm.

Ghi lại tối thiểu:

- Tổng thời gian.
- Requests per second.
- p50, p95, p99 latency.
- Số query database.
- CPU và memory của ứng dụng/database nếu có thể.

Không thay đổi workload giữa hai lần đo.

### Bài 3: Tính hit ratio

Giả sử có thống kê:

```text
Tổng số lần đọc cache: 20.000
Cache hit:              17.000
Cache miss:              3.000
```

Hãy tính:

```text
hit ratio  = 17.000 / 20.000 = 85%
miss ratio =  3.000 / 20.000 = 15%
```

Sau đó trả lời: miss ratio 15% có chấp nhận được không? Câu trả lời phụ thuộc vào chi phí một query database và yêu cầu latency của endpoint.

### Bài 4: Đánh giá khả năng cache

Với mỗi loại dữ liệu dưới đây, ghi `nên cache`, `cần thận trọng` hoặc `không nên cache tùy tiện`, kèm lý do:

- Product detail.
- User session.
- Inventory.
- Search result.
- Permission.
- Số dư tài khoản.

Mẫu trả lời:

```text
Product detail: nên cache.
Lý do: đọc nhiều, ít thay đổi, có thể chấp nhận stale ngắn.
TTL dự kiến: 5 phút.
Source of truth: database.
```

---

## 15. Câu hỏi tự kiểm tra

1. Cache hit khác cache miss ở điểm nào?
2. Vì sao cache hit ratio cao vẫn chưa đủ để kết luận hệ thống an toàn?
3. Database có còn bị tải khi dùng cache không?
4. Hot key là gì và nó có rủi ro gì?
5. Working set có nghĩa là gì?
6. Dữ liệu stale có luôn là lỗi không?
7. Source of truth thường là thành phần nào trong cache-aside?
8. Điều gì xảy ra với request đầu tiên khi key chưa có trong cache?
9. Vì sao không nên cache số dư tài khoản một cách tùy tiện?
10. Nếu Redis ngừng hoạt động, endpoint của bạn sẽ làm gì?

## 16. Tiêu chí hoàn thành Tuần 1

Bạn hoàn thành bài học khi có thể tự giải thích bằng ví dụ của mình:

- Cache giúp giảm latency và database load như thế nào.
- Một request cache miss đi qua những bước nào.
- Hit ratio và miss ratio được tính ra sao.
- Endpoint nào là read-heavy và endpoint nào là write-heavy.
- Key nào là hot key và dữ liệu nào thuộc working set.
- Dữ liệu nào được phép stale trong bao lâu.
- Vì sao database vẫn là source of truth trong cache-aside.
- Vì sao cache là một bản sao có giới hạn, không phải bản thay thế database.

Kết luận ngắn gọn cần nhớ:

> Cache đánh đổi tính tươi của dữ liệu và độ phức tạp vận hành để lấy latency thấp hơn, giảm tải cho hệ thống phía sau và tăng khả năng phục vụ các request đọc lặp lại.


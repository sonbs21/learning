# So sánh các kiểu dữ liệu (Compare data types)

> Nguồn: <https://redis.io/docs/latest/develop/data-types/compare-data-types/>
> Chọn kiểu dữ liệu Redis tốt nhất cho tác vụ của bạn.

Redis cung cấp nhiều kiểu dữ liệu để lưu trữ dữ liệu của bạn.
Các kiểu sau được chuyên biệt hóa cao cho những mục đích cụ thể:

-   [Geospatial](https://redis.io/docs/latest/develop/data-types/geospatial):
    lưu các string kèm tọa độ tương ứng để phục vụ truy vấn không gian địa lý (geospatial query).
-   [Probabilistic data types](https://redis.io/docs/latest/develop/data-types/probabilistic):
    lưu các phép đếm gần đúng và các thống kê khác cho những tập dữ liệu lớn.
-   [Time series](https://redis.io/docs/latest/develop/data-types/timeseries):
    lưu các điểm dữ liệu giá trị thực (real-valued) kèm thời điểm chúng được thu thập.
-   [Vector sets](https://redis.io/docs/latest/develop/data-types/vector-sets):
    lưu các string kèm dữ liệu vector tương ứng (và metadata tùy chọn)
    để phục vụ truy vấn độ tương đồng vector (vector similarity query).

Các kiểu dữ liệu còn lại mang tính đa dụng (general-purpose) hơn:

-   [Strings](https://redis.io/docs/latest/develop/data-types/strings):
    lưu dữ liệu văn bản hoặc nhị phân.
-   [Arrays](https://redis.io/docs/latest/develop/data-types/arrays):
    lưu các string được truy cập theo chỉ số nguyên (integer index), có hỗ trợ chỉ số
    thưa (sparse) và tổng hợp (aggregation) phía server.
-   [Hashes](https://redis.io/docs/latest/develop/data-types/hashes):
    lưu các cặp key-value bên trong một key duy nhất.
-   [JSON](https://redis.io/docs/latest/develop/data-types/json):
    lưu các mảng (array) và object key-value có cấu trúc, phân cấp, khớp với
    định dạng file văn bản [JSON](https://www.json.org/json-en.html) phổ biến.
-   [Lists](https://redis.io/docs/latest/develop/data-types/lists):
    lưu một dãy string đơn giản.
-   [Sets](https://redis.io/docs/latest/develop/data-types/sets):
    lưu một tập hợp các string duy nhất (unique).
-   [Sorted sets](https://redis.io/docs/latest/develop/data-types/sorted-sets):
    lưu một tập hợp các string duy nhất kèm score tương ứng.
-   [Streams](https://redis.io/docs/latest/develop/data-types/streams):
    lưu một dãy các entry, mỗi entry có một tập các cặp field-value.

Các kiểu dữ liệu đa dụng có một số tính năng trùng lặp nhau,
và thực tế, có lẽ bạn có thể mô phỏng bất kỳ kiểu nào trong số đó chỉ bằng string
cùng một chút sáng tạo. Tuy nhiên, mỗi kiểu dữ liệu mang lại những
sự đánh đổi (tradeoff) khác nhau về hiệu năng, mức sử dụng bộ nhớ, và chức năng.
Hướng dẫn này giúp bạn chọn kiểu dữ liệu tốt nhất cho tác vụ của mình.

## Tính năng của các kiểu dữ liệu (Data type features)

Các mục bên dưới tóm tắt tính năng của từng kiểu dữ liệu.

### Strings

-   **Cấu trúc (Structure)**: dữ liệu văn bản/nhị phân không có cấu trúc, hoặc các bộ đếm (counter) đơn giản,
    tập bit (bit set), hoặc tập hợp số nguyên.
-   **Thao tác (Operations)**: get, set, append, increment, decrement, các phép toán bitwise.
-   **Phù hợp cho (Suitable for)**: Document không có cấu trúc, counter, flag, bitmap.

String chủ yếu hữu ích để lưu văn bản hoặc các khối dữ liệu nhị phân
mà cấu trúc bên trong sẽ do chính ứng dụng của bạn quản lý.
Tuy nhiên, chúng cũng hỗ trợ các thao tác truy cập các dải bit (range of bits)
trong string để dùng như tập bit, số nguyên, hoặc số thực dấu phẩy động (floating-point).

### Arrays

-   **Cấu trúc**: dãy string thưa (sparse), có thể truy cập theo chỉ số (index-addressable).
-   **Thao tác**: get, set, delete, đọc theo dải (range read), quét theo dải (range scan), chèn tuần tự (sequential insert), tổng hợp (aggregate).
-   **Phù hợp cho**: Log sự kiện (event log), ring buffer, số đo cảm biến (sensor reading), và các dãy khác chủ yếu ghi nối thêm (append-heavy) hoặc thưa.

Array lưu các value được truy cập theo chỉ số nguyên, giúp truy cập ngẫu nhiên (random access) có độ phức tạp O(1) bất kể kích thước logic của array. Vì array là thưa, việc đặt một phần tử tại chỉ số 1,000,000 không cấp phát bộ nhớ cho một triệu slot trống ở giữa, nên các khoảng trống chỉ số lớn không tốn kém. Array phân biệt giữa *độ dài logic (logical length)* (chỉ số được đặt cao nhất cộng một, được trả về bởi [`ARLEN`](https://redis.io/docs/latest/commands/arlen)) và *số lượng phần tử (element count)* (số slot không rỗng, được trả về bởi [`ARCOUNT`](https://redis.io/docs/latest/commands/arcount)).

Ngoài việc truy cập trực tiếp theo chỉ số, array hỗ trợ chèn tuần tự thông qua [`ARINSERT`](https://redis.io/docs/latest/commands/arinsert), lệnh này tự động dịch chuyển một con trỏ (cursor) nội bộ. Con trỏ có thể được đặt lại vị trí bằng [`ARSEEK`](https://redis.io/docs/latest/commands/arseek), cho phép các kiểu ghi nối thêm linh hoạt. [`ARRING`](https://redis.io/docs/latest/commands/arring) làm cho pattern ring buffer trở nên tường minh: nó chèn các value theo modulo một kích thước cố định, quay vòng lại và ghi đè các entry cũ nhất khi buffer đầy. [`AROP`](https://redis.io/docs/latest/commands/arop) thực hiện các phép tổng hợp trong một lượt duyệt (single-pass) (sum, min, max, các phép toán bitwise, so khớp value) trên một dải mà không cần lấy từng phần tử riêng lẻ.

### Hashes

-   **Cấu trúc**: tập hợp các cặp key-value.
-   **Thao tác**: get, set, delete, increment, decrement, query.
-   **Phù hợp cho**: Các object đơn giản với số lượng field nhỏ.

Hash chủ yếu hữu ích để lưu các object có số lượng field nhỏ,
không lồng nhau (nested) hay có cấu trúc phức tạp. Tuy nhiên,
không có giới hạn thực sự nào về số lượng field bạn có thể lưu trong một hash, nên bạn
có thể dùng hash theo nhiều cách khác nhau trong ứng dụng của mình.
Các value của field là string, nhưng hash cung cấp các lệnh để xử lý
chúng như số nguyên hoặc số thực dấu phẩy động và thực hiện các phép toán số học
đơn giản trên chúng. Bạn có thể đặt thời gian hết hạn (expiration) cho từng field riêng lẻ của hash,
và bạn cũng có thể đánh index và truy vấn các hash document bằng
[Redis Search](https://redis.io/docs/latest/develop/ai/search-and-query).

### JSON

-   **Cấu trúc**: các mảng và object key-value phân cấp, khớp với
    định dạng file văn bản [JSON](https://www.json.org/json-en.html) phổ biến.
-   **Thao tác**: get, set, update, delete, query.
-   **Phù hợp cho**: Các object phức tạp, lồng nhau, với nhiều field.

JSON cung cấp khả năng mô hình hóa dữ liệu phong phú với các field và mảng lồng nhau.
Bạn có thể dùng một cú pháp path đơn giản để truy cập bất kỳ tập con dữ liệu nào trong
một JSON document. JSON cũng có các tính năng
[Redis Search](https://redis.io/docs/latest/develop/ai/search-and-query)
mạnh mẽ và linh hoạt hơn so với hash.

### Lists

-   **Cấu trúc**: dãy string đơn giản.
-   **Thao tác**: push, pop, get, set, trim.
-   **Phù hợp cho**: Queue, stack, log, và các cấu trúc dữ liệu tuyến tính khác.

List lưu các dãy value kiểu string. Chúng được tối ưu cho việc
thêm và xóa một số ít phần tử ở đầu (head) hoặc cuối (tail),
nên chúng rất hiệu quả để hiện thực queue, stack,
và deque.

### Sets

-   **Cấu trúc**: tập hợp các string duy nhất.
-   **Thao tác**: add, remove, kiểm tra thành viên (test membership), giao (intersect), hợp (union), hiệu (difference).
-   **Phù hợp cho**: Các phần tử duy nhất không có dữ liệu đi kèm.

Set lưu các tập hợp string duy nhất. Chúng cung cấp các thao tác hiệu quả
để kiểm tra thành viên, thêm và xóa phần tử.
Chúng cũng hỗ trợ các phép toán tập hợp như giao, hợp, và hiệu.

### Sorted sets

-   **Cấu trúc**: tập hợp các string duy nhất kèm score tương ứng.
-   **Thao tác**: add, remove, kiểm tra thành viên, lấy theo dải (range) dựa trên score hoặc thứ hạng (rank).
-   **Phù hợp cho**: Các phần tử duy nhất có score, hoặc các tập hợp có thứ tự.

Sorted set lưu các tập hợp string duy nhất kèm score tương ứng.
Chúng được tối ưu cho các truy vấn theo dải (range query) hiệu quả dựa trên score,
nên chúng hữu ích để hiện thực priority queue và các tập hợp có thứ tự
khác.

### Streams

-   **Cấu trúc**: dãy các entry, mỗi entry có một tập các cặp field-value.
-   **Thao tác**: add, read, trim.
-   **Phù hợp cho**: Dữ liệu log, time series, và các cấu trúc chỉ ghi nối thêm (append-only) khác.

Stream lưu các dãy entry, mỗi entry có một tập các cặp field-value.
Chúng được tối ưu cho việc ghi nối thêm entry mới và đọc chúng theo thứ tự,
nên chúng hữu ích để hiện thực dữ liệu log, time series, và các
cấu trúc dữ liệu append-only khác. Chúng cũng có hỗ trợ sẵn consumer group
để quản lý nhiều reader và đảm bảo phân phối ít nhất một lần (at-least-once delivery).

## Chọn một kiểu dữ liệu (Choose a data type)

Các mục bên dưới phân tích ưu và nhược điểm của từng kiểu dữ liệu cho
các tác vụ cụ thể. Lưu ý rằng bạn nên coi
các gợi ý này là "kinh nghiệm chung" (rules-of-thumb) hơn là quy định chặt chẽ, vì
có thể có nhiều lý do tinh tế để ưu tiên kiểu dữ liệu này hơn kiểu khác.
Mức sử dụng bộ nhớ là một trong những lý do đó. Để so sánh một bản ghi của bạn cần bao nhiêu bộ nhớ
khi lưu dưới dạng string, hash, hoặc JSON document, hãy dùng công cụ tính trong
[Compare memory use](#compare-memory-use).

### Document (Documents)

Thông thường bạn sẽ lưu dữ liệu dạng document bằng kiểu string, hash, hoặc JSON.
JSON nói chung có yêu cầu cao nhất về bộ nhớ và xử lý,
tiếp theo là hash, rồi đến string. Hãy dùng cây quyết định (decision tree) bên dưới làm hướng dẫn để
chọn kiểu dữ liệu tốt nhất cho tác vụ của bạn.

> Trên trang gốc, cây quyết định là một widget tương tác. Dưới đây là nội dung của nó.

1. **Bạn có cần các cấu trúc dữ liệu lồng nhau (field và mảng) hoặc đánh index/truy vấn geospatial với Redis Search không?**
   *Tại sao hỏi:* JSON là kiểu document duy nhất hỗ trợ các cấu trúc lồng nhau sâu và tích hợp với Redis Search cho các cấu trúc đó.
   - **Có** → **Dùng JSON**
   - **Không** → sang câu 2
2. **Bạn có cần đánh index/truy vấn bằng Redis Search nhưng có thể không cần các cấu trúc dữ liệu lồng nhau và đánh index geospatial không?**
   *Tại sao hỏi:* Hash hỗ trợ đánh index và truy vấn với chi phí bộ nhớ thấp hơn và truy cập field nhanh hơn so với JSON.
   - **Có** → **Dùng hash**
   - **Không** → sang câu 3
3. **Bạn có cần đặt thời gian hết hạn cho từng phần dữ liệu riêng lẻ bên trong document không?**
   *Tại sao hỏi:* Chỉ hash hỗ trợ truy cập và hết hạn ở mức field một cách hiệu quả.
   - **Có** → **Dùng hash**
   - **Không** → sang câu 4
4. **Bạn có cần truy cập thường xuyên từng field dữ liệu riêng lẻ trong document, nhưng các field là số nguyên hoặc bit đơn giản mà bạn có thể dễ dàng tham chiếu bằng một chỉ số nguyên không?**
   *Tại sao hỏi:* String và hash đều hỗ trợ truy cập field hiệu quả, nhưng string gọn và hiệu quả hơn nếu bạn chỉ cần các bit field với chỉ số nguyên.
   - **Có** → **Dùng string**
   - **Không** → sang câu 5
5. **Bạn có cần truy cập thường xuyên từng field dữ liệu riêng lẻ trong document mà có value là string hoặc dữ liệu nhị phân không?**
   *Tại sao hỏi:* Hash hỗ trợ truy cập field nói chung, nhưng string gọn và hiệu quả hơn nếu bạn không cần điều đó.
   - **Có** → **Dùng hash**
   - **Không** → **Dùng string**

<a name="compare-memory-use"></a>

#### So sánh mức sử dụng bộ nhớ (Compare memory use)

Mỗi kiểu cần bao nhiêu bộ nhớ phụ thuộc vào dữ liệu của bạn. Để so sánh các kiểu cho một bản ghi của riêng bạn, hãy dùng công cụ tính (calculator).

> Công cụ tính này chỉ có trên trang gốc: <https://redis.io/docs/latest/develop/data-types/compare-data-types/#compare-memory-use>

### Collection (Collections)

Thông thường bạn sẽ lưu dữ liệu dạng collection bằng kiểu set hoặc sorted set,
và với các collection rất đơn giản, bạn thậm chí có thể dùng string. Tất cả đều cho phép
kiểm tra thành viên cơ bản, nhưng có các tính năng bổ sung và sự đánh đổi khác nhau.
Sorted set có chi phí bộ nhớ và yêu cầu xử lý cao nhất, tiếp theo
là set, rồi đến string.
Hãy dùng cây quyết định bên dưới làm hướng dẫn để chọn kiểu dữ liệu tốt nhất cho tác vụ của bạn.
Lưu ý rằng nếu bạn cần lưu thông tin bổ sung cho các key trong một set
hoặc sorted set, bạn có thể làm điều đó bằng một hash hoặc JSON object phụ trợ có tên field
khớp với các key trong collection.

> Trên trang gốc, cây quyết định là một widget tương tác. Dưới đây là nội dung của nó.

1. **Bạn có cần lưu và truy xuất các key theo một thứ tự tùy ý hoặc theo thứ tự từ điển (lexicographical order) không?**
   *Tại sao hỏi:* Sorted set là kiểu collection duy nhất hỗ trợ duyệt có thứ tự, điều thiết yếu nếu bạn cần truy cập các phần tử theo một thứ tự cụ thể.
   - **Có** → **Dùng sorted set**
   - **Không** → sang câu 2
2. **Bạn có cần lưu thông tin bổ sung cho mỗi key VÀ bạn không cần các phép toán tập hợp (hợp, giao, hiệu) không?**
   *Tại sao hỏi:* Hash cho phép bạn gắn dữ liệu với mỗi key, nhưng chúng không hỗ trợ các phép toán tập hợp. Nếu bạn cần cả dữ liệu bổ sung lẫn phép toán tập hợp, set không phù hợp.
   - **Có** → **Dùng hash**
   - **Không** → sang câu 3
3. **Các key có luôn là các chỉ số nguyên đơn giản trong một khoảng đã biết không?**
   *Tại sao hỏi:* Bitmap trên string cung cấp chi phí bộ nhớ tối thiểu và truy cập ngẫu nhiên hiệu quả cho chỉ số nguyên, với các phép toán bitwise tương đương với phép toán tập hợp.
   - **Có** → **Dùng string (bitmap)**
   - **Không** → **Dùng set**

### Sequence (Sequences)

Thông thường bạn sẽ lưu các dãy (sequence) dữ liệu string hoặc nhị phân bằng sorted set,
list, stream, hoặc array. Mỗi kiểu đều có ưu điểm và nhược điểm cho những mục đích cụ thể.
Hãy dùng cây quyết định bên dưới làm hướng dẫn để chọn kiểu dữ liệu tốt nhất cho tác vụ của bạn.

> Trên trang gốc, cây quyết định là một widget tương tác. Dưới đây là nội dung của nó.

1. **Bạn có cần duy trì một thứ tự ưu tiên tùy ý, thứ tự từ điển, hoặc thực hiện các phép toán tập hợp không?**
   *Tại sao hỏi:* Sorted set là kiểu sequence duy nhất hỗ trợ cả sắp thứ tự lẫn phép toán tập hợp.
   - **Có** → **Dùng sorted set**
   - **Không** → sang câu 2
2. **Bạn có cần truy cập phần tử theo một chỉ số nguyên do phía gọi chọn (bao gồm cả chỉ số thưa hoặc không liên tục), thực hiện tổng hợp phía server trên các dải chỉ số (sum, min, max, bitwise), hoặc dùng một ring buffer kích thước cố định ghi đè các entry cũ nhất không?**
   *Tại sao hỏi:* Array ánh xạ trực tiếp chỉ số nguyên tới value kiểu string với truy cập O(1), hỗ trợ chỉ số thưa mà không cấp phát các khoảng trống, và có các thao tác tổng hợp phía server và ring buffer mà list và stream không cung cấp.
   - **Có** → **Dùng array**
   - **Không** → sang câu 3
3. **Bạn có cần lưu và truy xuất phần tử chủ yếu theo thứ tự timestamp, hoặc quản lý nhiều consumer cùng đọc từ sequence không?**
   *Tại sao hỏi:* Stream là kiểu sequence duy nhất hỗ trợ sắp thứ tự dựa trên timestamp và consumer group để quản lý nhiều reader với phân phối ít nhất một lần (at-least-once delivery).
   - **Có** → **Dùng stream**
   - **Không** → **Dùng list**

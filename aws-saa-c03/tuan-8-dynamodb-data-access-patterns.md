# Tuần 8 — DynamoDB và data access patterns

> Mục tiêu: thiết kế DynamoDB từ access pattern, chọn partition/sort key và index đúng, tránh hot partition/throttling, đồng thời hiểu consistency, capacity, Streams, TTL, Global Tables và DAX.

## 1. Kết quả cần đạt

- Giải thích table, item, attribute, partition key và sort key.
- Phân biệt simple primary key với composite primary key.
- Thiết kế key phân phối traffic đều và hỗ trợ Query.
- Phân biệt Query với Scan, filter với key condition.
- Chọn và thiết kế GSI/LSI.
- Hiểu provisioned capacity, on-demand capacity, auto scaling, RCU/WCU và burst.
- Nhận diện hot partition, throttling và cách xử lý.
- Phân biệt eventually consistent với strongly consistent read.
- Dùng conditional write, transaction và optimistic locking.
- Hiểu DynamoDB Streams và TTL.
- Nhận biết Global Tables và multi-Region active-active.
- Chọn DAX cho read-heavy low-latency use case.
- Áp dụng single-table design ở mức SAA.
- Thiết kế bảng order theo customerId/orderId và Query theo access pattern.

## 2. Lịch học gợi ý

| Buổi | Nội dung | Thời lượng |
|---|---|---:|
| 1 | Data model và primary key | 3 giờ |
| 2 | Query/Scan, GSI/LSI và pagination | 3 giờ |
| 3 | Capacity, throttling và consistency | 3 giờ |
| 4 | Streams, TTL, conditional write, Global Tables, DAX | 3 giờ |
| 5 | Single-table design, lab và câu hỏi thi | 2 giờ |

Nguyên tắc chính: DynamoDB được thiết kế từ access pattern, không phải bắt đầu từ entity model giống relational database.

---

## 3. DynamoDB data model

DynamoDB là managed NoSQL key-value/document database. Nó phù hợp workload cần scale lớn, latency thấp, schema linh hoạt và access pattern biết trước.

### 3.1. Table, item và attribute

- **Table:** nơi lưu item.
- **Item:** record dạng JSON-like document.
- **Attribute:** field trong item.
- **Primary key:** định danh và phân phối item.
- **Partition:** vùng storage/throughput logic được xác định từ partition key.

Các item trong cùng table có thể có attribute khác nhau, nhưng key schema của table/index phải tuân thủ.

DynamoDB hỗ trợ scalar, document và set data types. Không nên biến mọi relational entity thành một table riêng nếu access pattern có thể phục vụ bằng một table/index.

### 3.2. Primary key

Có hai dạng:

| Dạng | Thành phần | Use case |
|---|---|---|
| Simple primary key | partition key | lookup theo một key |
| Composite primary key | partition key + sort key | nhóm item và range/query trong nhóm |

Partition key xác định item được phân phối vào partition. Sort key sắp xếp item trong cùng partition key và hỗ trợ range condition.

Ví dụ:

~~~text
PK = CUSTOMER#123
SK = ORDER#2026-10-10T09:30:00Z#ORDER-001
~~~

Một customer có nhiều order cùng partition key nhưng khác sort key.

### 3.3. Partition key tốt

Partition key tốt có:

- nhiều giá trị;
- phân phối request và item tương đối đều;
- cardinality cao;
- không tập trung toàn bộ traffic vào một key;
- phù hợp access pattern chính.

Partition key xấu:

- status duy nhất như OPEN/CLOSED;
- ngày hiện tại nếu mọi request ghi vào một key;
- tenant cực lớn nếu một tenant chiếm toàn bộ traffic;
- giá trị tuần tự nếu tạo điểm nóng theo pattern.

Composite key không tự giải quyết hot partition. Nếu một partition key vẫn nhận quá nhiều request, cần sharding/suffix hoặc thiết kế access pattern khác.

### 3.4. Sort key

Sort key hỗ trợ:

- equals;
- begins_with;
- between;
- lớn hơn/nhỏ hơn;
- ascending/descending order;
- range theo thời gian;
- nhóm item liên quan.

Sort key không phải index toàn table; nó chỉ sắp xếp trong cùng partition key.

Ví dụ access pattern:

~~~text
PK = CUSTOMER#123
SK begins_with ORDER#
SK between ORDER#2026-01 and ORDER#2026-03
~~~

---

## 4. Thiết kế từ access pattern

Trước khi tạo table, liệt kê request thật sự:

| Access pattern | Key/index cần |
|---|---|
| Lấy customer theo customerId | PK = CUSTOMER#id |
| Lấy order của customer | PK = CUSTOMER#id, SK begins_with ORDER# |
| Lấy một order | PK = CUSTOMER#id, SK = ORDER#id |
| Lấy order theo orderId toàn hệ thống | GSI với orderId |
| Lấy order theo status + thời gian | GSI với status/sharded status và time sort |
| Lấy item gần đây | sort key là timestamp |
| Lấy entity liên quan | composite key trong single table |

Thiết kế nên trả lời:

- request có thể Query bằng key condition không;
- có cần nhiều entity trong một partition không;
- dữ liệu trả về có nhỏ và bounded không;
- có cần GSI/LSI không;
- traffic phân phối có đều không;
- write/read hot key xử lý thế nào;
- dữ liệu có hết hạn tự động không.

Nếu chưa biết access pattern, DynamoDB dễ trở thành table phải Scan nhiều và khó tối ưu.

---

## 5. Query và Scan

### 5.1. Query

Query đọc item theo partition key và có thể lọc theo sort key. Query tận dụng key/index, hiệu quả và predictable hơn Scan.

Ví dụ logic:

~~~text
Query PK = CUSTOMER#123
  AND SK begins_with ORDER#
  ORDER BY SK descending
  LIMIT 20
~~~

Query vẫn có thể trả nhiều page. 1 MB là giới hạn dữ liệu mỗi response trước khi pagination; dùng LastEvaluatedKey/ExclusiveStartKey để lấy trang tiếp theo.

### 5.2. Scan

Scan đọc toàn bộ hoặc một phần table/index rồi filter. Scan:

- tốn read capacity;
- latency tăng theo kích thước table;
- có thể gây throttling;
- không phù hợp request online thường xuyên.

Use case hợp lý: admin job nhỏ, migration, audit batch, backfill hoặc table nhỏ. Với table lớn, dùng export, stream, index hoặc thiết kế key phù hợp thay vì Scan.

### 5.3. Key condition và FilterExpression

Key condition quyết định item DynamoDB đọc từ index. FilterExpression chỉ lọc sau khi đọc, không giảm read capacity của dữ liệu đã đọc.

Vì vậy:

~~~text
Query bằng PK/SK → tốt
Query broad rồi Filter status → vẫn tốn capacity
Scan rồi Filter → thường rất đắt/chậm
~~~

Dùng ProjectionExpression để giảm dữ liệu trả về qua network, nhưng không phải cách chính để giảm mọi read capacity.

### 5.4. Pagination và sort

DynamoDB không trả toàn bộ kết quả lớn trong một response. Application cần:

- lưu LastEvaluatedKey;
- truyền ExclusiveStartKey cho page kế;
- xử lý item bị thêm/xóa giữa các page;
- giới hạn page size;
- không giả định page luôn đầy;
- dùng ScanIndexForward để đổi thứ tự sort.

---

## 6. GSI và LSI

### 6.1. Global Secondary Index

GSI có partition key và tùy chọn sort key riêng, khác primary key của table. Nó hỗ trợ access pattern thứ hai và có thể tạo sau khi table tồn tại.

Ví dụ table chính:

~~~text
PK = CUSTOMER#123
SK = ORDER#001
~~~

GSI cho lookup theo order:

~~~text
GSI1PK = ORDER#001
GSI1SK = CUSTOMER#123
~~~

Đặc điểm:

- có thể khác partition key;
- có thể có sort key;
- có thể tạo/xóa sau;
- có capacity/configuration riêng;
- thường đọc eventual consistency;
- project attribute theo KEYS_ONLY, INCLUDE hoặc ALL;
- ghi thêm vào GSI có cost và có thể bị throttling.

GSI hữu ích nhưng mỗi index là thêm storage, write cost và operational complexity.

### 6.2. Local Secondary Index

LSI có cùng partition key với table nhưng sort key khác. Nó phải được tạo khi tạo table và có giới hạn kích thước item collection theo partition key.

Đặc điểm:

- cùng partition key với table;
- sort key khác;
- tạo lúc create table;
- có thể hỗ trợ strongly consistent read;
- dùng capacity của table theo model dịch vụ;
- giới hạn item collection khiến cần tính trước.

### 6.3. So sánh GSI và LSI

| Đặc điểm | GSI | LSI |
|---|---|---|
| Partition key | khác được | phải giống table |
| Sort key | tùy chọn | khác table |
| Tạo sau | có | không, tạo lúc table |
| Capacity | riêng | dùng capacity table theo model |
| Strongly consistent read | không theo pattern GSI | có thể |
| Giới hạn item collection | không giống LSI limit | có giới hạn |
| Use case | access pattern khác | nhiều sort order trong cùng PK |

Bẫy phổ biến: GSI không cần cùng partition key; LSI bắt buộc cùng partition key.

### 6.4. Projection

- **KEYS_ONLY:** chỉ key.
- **INCLUDE:** key + attribute chọn.
- **ALL:** toàn bộ attribute.

Projection nhiều field giúp đọc đủ dữ liệu nhưng tăng storage/write cost. Chọn theo query response và cost.

---

## 7. Capacity và throughput

DynamoDB cung cấp capacity theo read/write workload.

### 7.1. Read capacity unit

Một RCU thường đại diện cho một strongly consistent read tối đa 4 KB mỗi giây, hoặc hai eventually consistent reads mỗi giây cùng kích thước. Transactional read có hệ số cao hơn.

Suy nghĩ đơn giản:

- item càng lớn → cần nhiều RCU;
- strongly consistent → tốn hơn eventually;
- query/scan đọc nhiều item → tốn theo dữ liệu đọc, không chỉ dữ liệu trả sau filter;
- projection/limit không luôn giảm cost phần đã đọc.

### 7.2. Write capacity unit

Một WCU thường đại diện cho một write tối đa 1 KB mỗi giây. Item lớn hơn 1 KB cần nhiều WCU; transactional write có hệ số cao hơn.

Ghi vào GSI cũng tạo write cost/index maintenance. GSI bị thiếu capacity có thể làm write table bị ảnh hưởng.

### 7.3. Provisioned capacity

Provisioned mode đặt read/write capacity trước. Có thể dùng auto scaling để điều chỉnh theo metric.

Phù hợp:

- workload predictable;
- muốn kiểm soát capacity/cost;
- traffic ổn định;
- có thể cấu hình target utilization.

Auto scaling không phản ứng tức thời với mọi burst; cần headroom và kiểm tra scale-out delay.

### 7.4. On-demand capacity

On-demand tự xử lý capacity theo request, phù hợp workload không dự đoán được, traffic burst hoặc chưa có history.

Trade-off:

- đơn giản hơn;
- cost per request có thể cao hơn workload steady;
- vẫn cần tránh hot key và giới hạn service;
- không biến thiết kế key xấu thành tốt.

### 7.5. Burst và adaptive capacity

DynamoDB có khả năng burst/adaptive capacity theo behavior dịch vụ, nhưng không nên dựa vào burst vô hạn. Partition nóng vẫn có thể throttling dù tổng table capacity còn dư.

Thiết kế phải phân phối traffic; adaptive capacity là hỗ trợ, không phải thay thế key design.

---

## 8. Hot partition và throttling

### 8.1. Hot partition

Hot partition xảy ra khi một partition key nhận tỷ lệ traffic quá lớn. Ví dụ tất cả write dùng PK ORDER-TODAY hoặc tất cả read dùng PK HOT-PRODUCT.

Triệu chứng:

- throttled requests;
- latency tăng;
- consumed capacity không đều;
- một key có traffic vượt trội.

### 8.2. Cách xử lý

- chọn partition key cardinality cao hơn;
- sharding key bằng suffix như PRODUCT#123#0..9;
- randomize write key;
- distribute time bucket;
- đọc nhiều shard rồi merge;
- cache hot item bằng DAX/ElastiCache;
- dùng write buffer/queue;
- xem lại GSI partition key;
- tăng capacity chỉ khi vấn đề là tổng throughput, không phải hot key.

Sharding làm query phức tạp hơn và cần biết số shard/merge strategy. Không sharding mù quáng.

### 8.3. GSI hot key

Một GSI có partition key ít cardinality như STATUS=OPEN có thể hot dù table chính phân phối tốt. Thiết kế GSI cũng phải review distribution, không chỉ primary key.

---

## 9. Consistency và transactions

### Eventually consistent read

Read có thể chưa thấy write mới nhất nhưng thường ít cost/latency hơn. Phù hợp feed, catalog, analytics hoặc dữ liệu chấp nhận trễ.

### Strongly consistent read

Đọc dữ liệu mới nhất sau write trong phạm vi hỗ trợ. Phù hợp business cần read-after-write mạnh, nhưng tốn capacity hơn và không áp dụng cho mọi pattern/replica/global design.

GSI chỉ hỗ trợ eventual consistency theo pattern DynamoDB; LSI có thể hỗ trợ strong read.

### Conditional write

Conditional expression chỉ ghi nếu điều kiện đúng. Use case:

- chống duplicate order;
- optimistic locking;
- chỉ update nếu version hiện tại đúng;
- ghi nếu item chưa tồn tại;
- đảm bảo stock/status chưa bị thay đổi.

Ví dụ logic:

~~~text
Put order nếu attribute_not_exists(orderId)
Update nếu version = 7
~~~

Conditional failure là trạng thái nghiệp vụ cần xử lý, không phải luôn là lỗi hệ thống.

### Transactions

DynamoDB Transactions hỗ trợ nhóm read/write atomic theo giới hạn dịch vụ. Dùng khi nhiều item phải cùng commit, nhưng tốn capacity hơn và không thay thế thiết kế key tốt.

### Optimistic locking

Lưu version number trong item:

1. đọc item version 7;
2. client update với condition version = 7;
3. ghi version 8;
4. nếu condition fail, đọc lại và resolve conflict.

Pattern này tránh last-write-wins vô tình ghi đè khi nhiều client cập nhật.

---

## 10. DynamoDB Streams

DynamoDB Streams ghi lại thay đổi item theo thứ tự trong phạm vi partition/shard theo retention dịch vụ, thường dùng cho event-driven processing.

Use case:

- audit/change data capture;
- đồng bộ search index;
- materialized view;
- notification;
- analytics pipeline;
- trigger Lambda.

Stream view type:

- KEYS_ONLY;
- NEW_IMAGE;
- OLD_IMAGE;
- NEW_AND_OLD_IMAGES.

Consumer phải idempotent vì event processing có thể retry/duplicate. Xử lý error bằng retry/DLQ theo integration phù hợp.

DynamoDB Streams không phải full backup lâu dài. Nếu cần lưu lịch sử dài, stream sang S3/Kinesis/warehouse.

---

## 11. TTL

DynamoDB TTL tự động đánh dấu item hết hạn dựa trên Unix epoch seconds trong một attribute. Xóa diễn ra asynchronous, không phải đúng tại thời điểm timestamp.

Use case:

- session;
- temporary token;
- cart chưa checkout;
- idempotency record;
- cache-like item;
- dữ liệu tạm.

Không dùng TTL để đảm bảo xóa chính xác theo deadline compliance hoặc privacy nếu requirement cần deletion tức thời. Item có thể còn tồn tại một thời gian; thiết kế authorization không được dựa vào việc TTL đã xóa ngay.

TTL deletion có thể xuất hiện qua Streams theo behavior dịch vụ, hữu ích cho cleanup/event.

---

## 12. Global Tables và multi-Region

Global Tables cung cấp multi-Region, multi-active replication cho DynamoDB. Application ở nhiều Region có thể đọc/ghi local, giảm latency và tăng regional resilience.

Cần cân nhắc:

- replication bất đồng bộ;
- conflict resolution/last writer wins theo behavior;
- write conflict khi cùng item ở nhiều Region;
- cost replication và write;
- schema/index phải tương thích;
- failover và routing;
- data residency;
- RPO/RTO;
- idempotency và conflict handling.

Global Tables không làm dữ liệu transaction cross-Region trở thành strongly consistent tức thời. Business cần tránh concurrent writes xung đột hoặc thiết kế conflict resolution.

---

## 13. DAX

DAX là in-memory cache dành cho DynamoDB, phù hợp read-heavy và latency rất thấp.

Use case:

- nhiều read lặp lại;
- microsecond-level cache requirement;
- application muốn giảm read load;
- response có thể eventual consistent.

DAX có thể cache read và hỗ trợ write-through theo pattern. Nhưng:

- không thay DynamoDB source of truth;
- cache miss vẫn đi DynamoDB;
- dữ liệu có thể stale;
- không phù hợp nếu request cần strongly consistent read;
- cluster/network/SG/cost cần vận hành;
- invalidation/TTL vẫn phải hiểu.

So sánh DAX và ElastiCache:

| Tiêu chí | DAX | ElastiCache |
|---|---|---|
| Tích hợp | native DynamoDB API | cache/data structure tổng quát |
| Use case | DynamoDB read cache | session, custom cache, Redis features |
| Consistency | phù hợp eventual read | app kiểm soát |
| Data model | DynamoDB item/request | Redis/Memcached |
| Chọn khi | read-heavy DynamoDB | cần cache linh hoạt |

---

## 14. Single-table design

Single-table design lưu nhiều entity type trong một table, dùng PK/SK và index để phục vụ access patterns.

Ví dụ:

~~~text
PK = CUSTOMER#123, SK = CUSTOMER#123
PK = CUSTOMER#123, SK = ORDER#2026-001
PK = CUSTOMER#123, SK = ORDER#2026-002
PK = ORDER#2026-001, SK = CUSTOMER#123
~~~

Một Query với PK CUSTOMER#123 có thể trả customer và orders liên quan. GSI có thể tạo alternate lookup theo orderId/status.

### 14.1. Ưu điểm

- ít round trip;
- lấy entity liên quan trong một Query;
- scale theo access pattern;
- phù hợp serverless/high-scale;
- giảm join ở application.

### 14.2. Đánh đổi

- khó học hơn relational normalization;
- duplicate data/denormalization;
- update nhiều item khi dữ liệu thay đổi;
- GSI write cost;
- query pattern phải biết trước;
- report ad hoc không phù hợp.

### 14.3. Quy trình thiết kế

1. Liệt kê access patterns.
2. Xác định item nào cần lấy cùng nhau.
3. Chọn partition key để group và distribute.
4. Dùng sort key để range/order/entity relation.
5. Tạo GSI cho lookup khác.
6. Tính size, RCU/WCU và hot key.
7. Kiểm tra write/update consistency.
8. Test production-like traffic.

Single-table design không có nghĩa mọi thứ phải nhét vào một table trong mọi dự án. Nó là pattern tối ưu theo access pattern khi trade-off phù hợp.

---

## 15. Ví dụ order theo customerId/orderId

### Access patterns

- tạo order;
- lấy một order của customer;
- list order của customer theo thời gian;
- tìm order theo orderId;
- tìm order theo status gần đây;
- chống tạo duplicate order.

### Table chính

~~~text
PK = CUSTOMER#C001
SK = ORDER#2026-10-10T09:30:00Z#O1001
customerId = C001
orderId = O1001
status = PAID
createdAt = 2026-10-10T09:30:00Z
total = 125000
~~~

Query order của customer:

~~~text
PK = CUSTOMER#C001
SK begins_with ORDER#
ScanIndexForward = false
Limit = 20
~~~

### GSI theo orderId

~~~text
GSI1PK = ORDER#O1001
GSI1SK = CUSTOMER#C001
~~~

### GSI theo status

Status có cardinality thấp và dễ hot. Nếu cần query status, có thể thêm time bucket/shard:

~~~text
GSI2PK = STATUS#PAID#2026-10#SHARD#03
GSI2SK = 2026-10-10T09:30:00Z#O1001
~~~

Chỉ dùng sharding khi traffic/scale cần; đổi lại application phải query nhiều shard và merge.

### Conditional create

Tạo order với idempotency key hoặc item riêng:

~~~text
Put nếu attribute_not_exists(PK)
~~~

Khi client retry request do timeout, conditional write giúp tránh duplicate order.

---

## 16. Lab bắt buộc

### Lab 1 — Table order

1. Tạo table với composite key phù hợp.
2. Ghi customer/order item.
3. Query tất cả order của một customer.
4. Query theo time range và descending order.
5. Kiểm tra pagination.
6. Đo item size và consumed capacity.

### Lab 2 — GSI access pattern thứ hai

- Tạo GSI theo orderId hoặc status/time.
- Viết Query trên GSI.
- Chọn projection KEYS_ONLY, INCLUDE hoặc ALL.
- So sánh read/write/storage cost.
- Kiểm tra GSI eventual consistency.

### Lab 3 — Conditional write

1. Tạo order với idempotency key.
2. Gửi cùng request hai lần.
3. Dùng attribute_not_exists hoặc version condition.
4. Xử lý ConditionalCheckFailedException.
5. Test optimistic locking với version number.

### Lab 4 — TTL và Streams

- Thêm expiresAt dạng epoch seconds.
- Bật TTL.
- Bật Streams với NEW_AND_OLD_IMAGES.
- Dùng Lambda hoặc consumer test để đọc change.
- Kiểm tra TTL deletion là asynchronous.
- Đảm bảo consumer idempotent.

### Lab 5 — Capacity/throttling

- Tạo workload write/read có hot partition.
- Quan sát throttled requests và consumed capacity.
- Thay key bằng distributed/sharded key.
- So sánh latency và phân phối traffic.
- Xóa table/test data sau lab.

### Lab 6 — DAX hoặc mô phỏng cache

Nếu ngân sách cho phép, tạo DAX cluster; nếu không, mô phỏng cache-aside bằng Redis/local cache:

1. read cache;
2. miss → DynamoDB;
3. set TTL;
4. write table;
5. invalidate key;
6. test stale/failure behavior.

---

## 17. Bẫy đề thi SAA-C03

- Thiết kế DynamoDB từ access pattern, không từ entity model.
- Query dùng partition key; Scan đọc toàn table/index và thường đắt.
- FilterExpression lọc sau khi đọc, không thay thế key condition.
- GSI có partition key khác và tạo sau; LSI phải cùng partition key và tạo lúc create table.
- GSI thường chỉ eventual consistent; LSI có thể strong consistent.
- LSI có item collection size limit theo partition key.
- GSI thêm storage/write cost và có thể bị hot partition.
- Partition key cardinality thấp gây hot partition/throttling.
- Tăng provisioned capacity không luôn sửa hot key.
- On-demand không chữa thiết kế partition key xấu.
- Strongly consistent read tốn hơn eventually consistent và không áp dụng mọi index.
- TTL xóa asynchronous, không phải deadline deletion chính xác.
- DynamoDB Streams có thể duplicate/retry; consumer phải idempotent.
- Global Tables là multi-Region replication, không phải synchronous global transaction.
- DAX không phù hợp strongly consistent read và không thay source of truth.
- Conditional write/optimistic locking dùng chống overwrite/duplicate.
- Single-table cần access pattern rõ và chấp nhận denormalization.
- Query có pagination; không giả định mọi item trả trong một response.
- ProjectionExpression giảm payload nhưng không thay thế key design/capacity planning.
- GSI status thấp cardinality có thể hot dù primary table tốt.
- DynamoDB không phải relational join/reporting engine.
- Không lưu secret hoặc PII không được bảo vệ trong item/log.

---

## 18. Câu hỏi tự luyện

### Câu 1

Ứng dụng cần lấy tất cả order của một customer theo thời gian. Thiết kế key thế nào?

**Đáp án:** partition key là customerId, sort key chứa order prefix và timestamp; dùng Query với begins_with/range, không Scan.

### Câu 2

Cần tìm order theo orderId nhưng primary key là customerId + order timestamp. Dùng gì?

**Đáp án:** GSI với orderId làm partition key, hoặc item lookup pattern riêng tùy access pattern.

### Câu 3

Một table bị throttling dù tổng capacity còn dư. Nguyên nhân có thể là gì?

**Đáp án:** hot partition/hot key hoặc GSI hot partition; kiểm tra phân phối key và traffic.

### Câu 4

Muốn thêm index sau khi table đã chạy production. Chọn GSI hay LSI?

**Đáp án:** GSI; LSI phải tạo cùng table ban đầu.

### Câu 5

Ứng dụng cần đọc dữ liệu mới nhất ngay sau write. Dùng read nào?

**Đáp án:** strongly consistent read nếu table/pattern hỗ trợ; không đọc từ GSI/DAX/Global Table nếu cần guarantee đó.

### Câu 6

Client retry request tạo order vì timeout, không được tạo duplicate. Dùng gì?

**Đáp án:** conditional write với idempotency key hoặc transaction/condition phù hợp.

### Câu 7

Session hết hạn tự động sau một giờ nhưng không cần xóa đúng giây. Dùng gì?

**Đáp án:** DynamoDB TTL với expiresAt epoch seconds, đồng thời authorization không phụ thuộc TTL xóa tức thời.

### Câu 8

Cần trigger cập nhật search index khi item thay đổi. Dùng gì?

**Đáp án:** DynamoDB Streams với consumer/Lambda idempotent.

### Câu 9

Nhiều Region cần local read/write và failover. Dùng gì?

**Đáp án:** Global Tables, đồng thời thiết kế conflict resolution, eventual replication và routing/failover.

### Câu 10

Read-heavy DynamoDB cần latency rất thấp và request lặp lại nhiều. Dùng gì?

**Đáp án:** DAX nếu eventual consistency chấp nhận; không dùng cho strongly consistent read requirement.

### Câu 11

Cần query item theo partition key nhưng muốn lọc status. Vì sao Query + Filter không tối ưu?

**Đáp án:** DynamoDB vẫn đọc item theo key rồi mới filter; capacity/latency của dữ liệu đọc vẫn phát sinh. Nếu status là access pattern chính, dùng key/GSI phù hợp.

### Câu 12

Một key STATUS=OPEN nhận phần lớn read/write. Cách xử lý?

**Đáp án:** thiết kế GSI partition key có shard/time bucket hoặc access pattern khác; cache hot data; tăng capacity đơn thuần có thể không đủ.

---

## 19. Checklist hoàn thành tuần

### Kiến thức

- [ ] Giải thích table, item, attribute, partition key và sort key.
- [ ] Phân biệt simple và composite primary key.
- [ ] Thiết kế partition key phân phối traffic.
- [ ] Dùng sort key cho range/order/time.
- [ ] Phân biệt Query với Scan.
- [ ] Hiểu key condition, filter và pagination.
- [ ] Phân biệt GSI với LSI.
- [ ] Hiểu projection và index cost.
- [ ] Tính capacity cơ bản với RCU/WCU.
- [ ] Phân biệt provisioned, on-demand và auto scaling.
- [ ] Nhận diện hot partition/throttling.
- [ ] Phân biệt eventual/strong consistency.
- [ ] Dùng conditional write và optimistic locking.
- [ ] Hiểu Streams, TTL, Global Tables và DAX.
- [ ] Giải thích single-table design và trade-off.

### Thực hành

- [ ] Đã thiết kế table order theo customerId/orderId.
- [ ] Đã Query theo partition key và sort key range.
- [ ] Đã test pagination.
- [ ] Đã tạo GSI cho access pattern thứ hai.
- [ ] Đã test conditional write chống duplicate.
- [ ] Đã bật TTL và Streams.
- [ ] Đã mô phỏng hot partition/throttling.
- [ ] Đã thử cache-aside/DAX phù hợp budget.
- [ ] Đã dọn table/index/cluster/test data sau lab.

Bạn sẵn sàng sang Tuần 9 khi có thể thiết kế DynamoDB từ access pattern, giải thích vì sao một key gây hot partition, chọn GSI/LSI đúng và phân biệt cache/stream/TTL với source of truth.

---

## 20. Tài liệu tham khảo

- [Amazon DynamoDB Developer Guide](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/Introduction.html) — tổng quan DynamoDB.
- [DynamoDB core components](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/HowItWorks.CoreComponents.html) — table, item và key.
- [Query and Scan](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/QueryAndScan.html) — access và pagination.
- [Secondary indexes](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/SecondaryIndexes.html) — GSI và LSI.
- [Read/write capacity mode](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/HowItWorks.ReadWriteCapacityMode.html) — provisioned/on-demand.
- [Read consistency](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/HowItWorks.ReadConsistency.html) — eventual/strong consistency.
- [DynamoDB Streams](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/Streams.html) — change data capture.
- [Time to Live](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/TTL.html) — automatic expiration.
- [Global Tables](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/GlobalTables.html) — multi-Region replication.
- [DynamoDB transactions](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/transaction-apis.html) — transactional write/read.
- [Amazon DynamoDB Accelerator](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/DAX.html) — DAX cache.


# Các kiểu dữ liệu Redis (Redis data types)

> Nguồn: <https://redis.io/docs/latest/develop/data-types/>
> Tổng quan về các kiểu dữ liệu được Redis hỗ trợ

Redis là một data structure server (server cấu trúc dữ liệu).
Về cốt lõi, Redis cung cấp một tập hợp các kiểu dữ liệu gốc (native) giúp bạn giải quyết nhiều loại vấn đề khác nhau, từ [caching](https://redis.io/docs/latest/develop/data-types/strings) đến
[queuing (hàng đợi)](https://redis.io/docs/latest/develop/data-types/lists) đến
[xử lý sự kiện (event processing)](https://redis.io/docs/latest/develop/data-types/streams).
Dưới đây là mô tả ngắn về từng kiểu dữ liệu, kèm link tới các bài tổng quan chi tiết hơn và tài liệu tham khảo lệnh.
Mỗi bài tổng quan đều bao gồm một tutorial đầy đủ kèm các đoạn code mẫu.

## Các kiểu dữ liệu (Data types)

[Redis Open Source](https://redis.io/docs/latest/operate/oss_and_stack)
hiện thực các kiểu dữ liệu sau:

- [Strings](#strings)
    - [Bitmaps](#bitmaps)
    - [Bitfields](#bitfields)
- [Arrays](#arrays)
- [Geospatial indexes](#geospatial-indexes)
- [Hashes](#hashes)
- [JSON](#json)
- [Lists](#lists)
- [Probabilistic data types](#probabilistic-data-types)
- [Sets](#sets)
- [Sorted sets](#sorted-sets)
- [Streams](#streams)
- [Time series](#time-series)
- [Vector sets](#vector-sets)

Xem [Compare data types](https://redis.io/docs/latest/develop/data-types/compare-data-types)
để có lời khuyên về việc kiểu dữ liệu đa dụng (general-purpose) nào là tốt nhất cho các tác vụ thông dụng.

### Strings

[Redis strings](https://redis.io/docs/latest/develop/data-types/strings) là kiểu dữ liệu cơ bản nhất của Redis, biểu diễn một chuỗi các byte.
Để biết thêm thông tin, xem:

* [Overview of Redis strings](https://redis.io/docs/latest/develop/data-types/strings)
* [Redis string command reference](https://redis.io/commands/?group=string)

### Bitfields

[Redis bitfields](https://redis.io/docs/latest/develop/data-types/strings/bitfields) mã hóa hiệu quả nhiều bộ đếm (counter) trong một value kiểu string.
Bitfield cung cấp các thao tác get, set và increment nguyên tử (atomic) và hỗ trợ các chính sách xử lý tràn số (overflow policy) khác nhau.
Để biết thêm thông tin, xem:

* [Overview of Redis bitfields](https://redis.io/docs/latest/develop/data-types/strings/bitfields)
* Lệnh [`BITFIELD`](https://redis.io/docs/latest/commands/bitfield).

### Bitmaps

[Redis bitmaps](https://redis.io/docs/latest/develop/data-types/strings/bitmaps) cho phép bạn thực hiện các phép toán bitwise trên string.
Để biết thêm thông tin, xem:

* [Overview of Redis bitmaps](https://redis.io/docs/latest/develop/data-types/strings/bitmaps)
* [Redis bitmap command reference](https://redis.io/commands/?group=bitmap)

### Arrays

[Redis arrays](https://redis.io/docs/latest/develop/data-types/arrays) là các dãy string thưa (sparse), có thể truy cập theo chỉ số (index-addressable).
Để biết thêm thông tin, xem:

* [Overview of Redis arrays](https://redis.io/docs/latest/develop/data-types/arrays)
* [Redis array command reference](https://redis.io/commands/?group=array)

### Geospatial indexes

[Redis geospatial indexes](https://redis.io/docs/latest/develop/data-types/geospatial) hữu ích cho việc tìm các vị trí nằm trong một bán kính địa lý hoặc một khung giới hạn (bounding box) cho trước.
Để biết thêm thông tin, xem:

* [Overview of Redis geospatial indexes](https://redis.io/docs/latest/develop/data-types/geospatial)
* [Redis geospatial indexes command reference](https://redis.io/commands/?group=geo)

### Hashes

[Redis hashes](https://redis.io/docs/latest/develop/data-types/hashes) là các kiểu bản ghi (record type) được mô hình hóa như các tập hợp cặp field-value.
Do đó, Redis hash tương tự như [Python dictionaries](https://docs.python.org/3/tutorial/datastructures.html#dictionaries), [Java HashMaps](https://docs.oracle.com/javase/8/docs/api/java/util/HashMap.html), và [Ruby hashes](https://ruby-doc.org/core-3.1.2/Hash.html).
Để biết thêm thông tin, xem:

* [Overview of Redis hashes](https://redis.io/docs/latest/develop/data-types/hashes)
* [Redis hashes command reference](https://redis.io/commands/?group=hash)

### JSON

[Redis JSON](https://redis.io/docs/latest/develop/data-types/json) cung cấp
các mảng (array) và object key-value có cấu trúc, phân cấp, khớp với
định dạng file văn bản [JSON](https://www.json.org/json-en.html) phổ biến.
Bạn có thể import văn bản JSON vào các object Redis và truy cập,
sửa đổi, và truy vấn từng phần tử dữ liệu riêng lẻ.
Để biết thêm thông tin, xem:

- [Overview of Redis JSON](https://redis.io/docs/latest/develop/data-types/json)
- [JSON command reference](https://redis.io/commands?group=json)

### Lists

[Redis lists](https://redis.io/docs/latest/develop/data-types/lists) là các danh sách string được sắp xếp theo thứ tự chèn vào.
Để biết thêm thông tin, xem:

* [Overview of Redis lists](https://redis.io/docs/latest/develop/data-types/lists)
* [Redis list command reference](https://redis.io/commands/?group=list)

### Probabilistic data types

Các kiểu dữ liệu này cho phép bạn thu thập và tính toán thống kê theo cách
gần đúng (approximate) nhưng rất hiệu quả. Các kiểu sau
hiện có sẵn:

- [Bloom filter](#bloom-filter)
- [Count-min sketch](#count-min-sketch)
- [Cuckoo filter](#cuckoo-filter)
- [HyperLogLog](#hyperloglog)
- [t-digest](#t-digest)
- [Top-K](#top-k)

### Bloom filter

[Redis Bloom filters](https://redis.io/docs/latest/develop/data-types/probabilistic/bloom-filter)
cho phép bạn kiểm tra sự có mặt hoặc vắng mặt của một phần tử trong một tập hợp. Để biết thêm
thông tin, xem:

- [Overview of Redis Bloom filters](https://redis.io/docs/latest/develop/data-types/probabilistic/bloom-filter)
- [Bloom filter command reference](https://redis.io/commands?group=bf)

### Count-min sketch

[Redis Count-min sketch](https://redis.io/docs/latest/develop/data-types/probabilistic/count-min-sketch)
ước lượng tần suất của một điểm dữ liệu trong một luồng (stream) các giá trị.
Để biết thêm thông tin, xem:

- [Redis Count-min sketch overview](https://redis.io/docs/latest/develop/data-types/probabilistic/count-min-sketch)
- [Count-min sketch command reference](https://redis.io/commands?group=cms)

### Cuckoo filter

[Redis Cuckoo filters](https://redis.io/docs/latest/develop/data-types/probabilistic/cuckoo-filter)
cho phép bạn kiểm tra sự có mặt hoặc vắng mặt của một phần tử trong một tập hợp. Chúng tương tự như
[Bloom filter](#bloom-filter) nhưng có sự đánh đổi (trade-off) hơi khác giữa tính năng
và hiệu năng. Để biết thêm thông tin, xem:

- [Overview of Redis Cuckoo filters](https://redis.io/docs/latest/develop/data-types/probabilistic/cuckoo-filter)
- [Cuckoo filter command reference](https://redis.io/commands?group=cf)

### HyperLogLog

Các cấu trúc dữ liệu [Redis HyperLogLog](https://redis.io/docs/latest/develop/data-types/probabilistic/hyperloglogs) cung cấp các ước lượng xác suất về cardinality (tức là số lượng phần tử) của các tập hợp lớn. Để biết thêm thông tin, xem:

* [Overview of Redis HyperLogLog](https://redis.io/docs/latest/develop/data-types/probabilistic/hyperloglogs)
* [Redis HyperLogLog command reference](https://redis.io/commands/?group=hyperloglog)

### t-digest

Các cấu trúc [Redis t-digest](https://redis.io/docs/latest/develop/data-types/probabilistic/t-digest)
ước lượng các phân vị (percentile) từ một luồng các giá trị dữ liệu. Để biết thêm
thông tin, xem:

- [Redis t-digest overview](https://redis.io/docs/latest/develop/data-types/probabilistic/t-digest)
- [t-digest command reference](https://redis.io/commands?group=tdigest)

### Top-K

Các cấu trúc [Redis Top-K](https://redis.io/docs/latest/develop/data-types/probabilistic/top-k)
ước lượng thứ hạng của một điểm dữ liệu trong một luồng các giá trị.
Để biết thêm thông tin, xem:

- [Redis Top-K overview](https://redis.io/docs/latest/develop/data-types/probabilistic/top-k)
- [Top-K command reference](https://redis.io/commands?group=topk)

### Sets

[Redis sets](https://redis.io/docs/latest/develop/data-types/sets) là các tập hợp không có thứ tự gồm các string duy nhất (unique), hoạt động giống như set trong ngôn ngữ lập trình yêu thích của bạn (ví dụ, [Java HashSets](https://docs.oracle.com/javase/7/docs/api/java/util/HashSet.html), [Python sets](https://docs.python.org/3.10/library/stdtypes.html#set-types-set-frozenset), v.v.).
Với một Redis set, bạn có thể thêm, xóa, và kiểm tra sự tồn tại trong thời gian O(1) (nói cách khác, bất kể số lượng phần tử của set là bao nhiêu).
Để biết thêm thông tin, xem:

* [Overview of Redis sets](https://redis.io/docs/latest/develop/data-types/sets)
* [Redis set command reference](https://redis.io/commands/?group=set)

### Sorted sets

[Redis sorted sets](https://redis.io/docs/latest/develop/data-types/sorted-sets) là các tập hợp string duy nhất, duy trì thứ tự theo score gắn với mỗi string.
Để biết thêm thông tin, xem:

* [Overview of Redis sorted sets](https://redis.io/docs/latest/develop/data-types/sorted-sets)
* [Redis sorted set command reference](https://redis.io/commands/?group=sorted-set)

### Streams

Một [Redis stream](https://redis.io/docs/latest/develop/data-types/streams) là một cấu trúc dữ liệu hoạt động giống như một log chỉ ghi nối thêm (append-only log).
Stream giúp ghi lại các sự kiện theo thứ tự chúng xảy ra và sau đó phân phối (syndicate) chúng để xử lý.
Để biết thêm thông tin, xem:

* [Overview of Redis Streams](https://redis.io/docs/latest/develop/data-types/streams)
* [Redis Streams command reference](https://redis.io/commands/?group=stream)

### Time series

Các cấu trúc [Redis time series](https://redis.io/docs/latest/develop/data-types/timeseries)
cho phép bạn lưu trữ và truy vấn các điểm dữ liệu có gắn timestamp.
Để biết thêm thông tin, xem:

- [Redis time series overview](https://redis.io/docs/latest/develop/data-types/timeseries)
- [Time series command reference](https://redis.io/commands?group=timeseries)

### Vector sets

[Redis vector sets](https://redis.io/docs/latest/develop/data-types/vector-sets) là một kiểu dữ liệu chuyên biệt được thiết kế để quản lý dữ liệu vector nhiều chiều (high-dimensional), cho phép tìm kiếm độ tương đồng vector (vector similarity search) nhanh và hiệu quả ngay trong Redis. Vector set được tối ưu cho các trường hợp sử dụng liên quan đến machine learning, hệ thống gợi ý (recommendation system), và tìm kiếm ngữ nghĩa (semantic search), trong đó mỗi vector biểu diễn một điểm dữ liệu trong không gian nhiều chiều. Vector set hỗ trợ thuật toán [HNSW](https://en.wikipedia.org/wiki/Hierarchical_navigable_small_world) (hierarchical navigable small world), cho phép bạn lưu trữ, đánh index, và truy vấn vector dựa trên độ đo cosine similarity. Với vector set, Redis cung cấp hỗ trợ gốc cho hybrid search, kết hợp độ tương đồng vector với các [filter](https://redis.io/docs/latest/develop/data-types/vector-sets/filtered-search) có cấu trúc.
Để biết thêm thông tin, xem:

* [Overview of Redis vector sets](https://redis.io/docs/latest/develop/data-types/vector-sets)
* [Redis vector set command reference](https://redis.io/commands/?group=vector_set)

## Thêm phần mở rộng (Adding extensions)

Để mở rộng các tính năng do các kiểu dữ liệu có sẵn cung cấp, hãy dùng một trong các lựa chọn sau:

1. Viết [các hàm phía server bằng Lua (server-side functions in Lua)](https://redis.io/docs/latest/develop/programmability) tùy chỉnh của riêng bạn.
1. Viết Redis module của riêng bạn bằng [modules API](https://redis.io/docs/latest/develop/reference/modules) hoặc tham khảo các [module được cộng đồng hỗ trợ (community-supported modules)](https://redis.io/docs/latest/operate/oss_and_stack/stack-with-enterprise).

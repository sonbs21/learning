# Key và value (Keys and values)

> Nguồn: <https://redis.io/docs/latest/develop/using-commands/keyspace/>
> Quản lý key trong Redis: hết hạn key (key expiration), quét (scanning), thay đổi và truy vấn không gian key (key space)

Mỗi đối tượng dữ liệu mà bạn lưu trong một database Redis đều có một *key* duy nhất
của riêng nó. Key là một chuỗi (string) mà bạn truyền vào các lệnh Redis để
truy xuất đối tượng tương ứng hoặc sửa đổi dữ liệu của nó. Đối tượng dữ liệu gắn với một
key cụ thể được gọi là *value*, và cả hai cùng nhau được gọi là
một *cặp key-value* (key-value pair).

## Nội dung của key (Content of keys)

Một key thường là một tên dạng văn bản mang ý nghĩa nào đó trong
mô hình dữ liệu của bạn. Không giống như tên biến trong một ngôn ngữ lập trình, key của Redis có rất ít
hạn chế về định dạng, nên các key chứa khoảng trắng hoặc ký tự dấu câu
hầu như đều ổn (ví dụ: "1st Attempt", hoặc "% of price in $"). Redis không
hỗ trợ namespace hay các kiểu phân loại khác cho key, nên bạn phải cẩn thận để tránh
trùng tên (name collision). Tuy nhiên, có một quy ước là dùng ký tự dấu hai chấm ":"
để chia key thành các phần (ví dụ: "person:1", "person:2",
"office:London", "office:NewYork:1"). Bạn có thể dùng cách này như một cách đơn giản để gom
các key lại thành các nhóm.

Mặc dù key thường là văn bản, Redis thực ra hiện thực key theo kiểu *binary-safe*,
nên bạn có thể dùng bất kỳ chuỗi byte nào làm một key hợp lệ, chẳng hạn một file JPEG hoặc
một giá trị struct từ ứng dụng của bạn. Chuỗi rỗng cũng là một key hợp lệ trong Redis.

Cũng có một vài điều khác cần ghi nhớ về key:

* Key rất dài không phải là ý hay. Ví dụ, một key dài 1024 byte là một ý tồi
  không chỉ về mặt bộ nhớ, mà còn vì việc tra cứu key trong
  tập dữ liệu có thể cần nhiều phép so sánh key tốn kém. Ngay cả khi công việc cần làm
  là kiểm tra sự tồn tại của một giá trị lớn, thì việc băm nó (ví dụ
  bằng SHA1) là ý hay hơn, đặc biệt là xét về bộ nhớ
  và băng thông.
* Key rất ngắn thường cũng không phải là ý hay. Chẳng có mấy lý do để viết
  "u1000flw" làm key nếu bạn có thể viết "user:1000:followers". Cách sau
  dễ đọc hơn và phần dung lượng tăng thêm là nhỏ so với dung lượng mà
  chính đối tượng key và đối tượng value sử dụng. Dù key ngắn rõ ràng sẽ
  tốn ít bộ nhớ hơn một chút, việc của bạn là tìm ra điểm cân bằng phù hợp.
* Hãy cố gắng tuân theo một schema. Ví dụ "object-type:id" là một ý
  hay, như trong "user:1000". Dấu chấm hoặc dấu gạch ngang thường được dùng cho các trường
  nhiều từ, như trong "comment:4321:reply.to" hoặc "comment:4321:reply-to".
* Kích thước key tối đa cho phép là 512 MB.

### Hashtag (Hashtags)

Redis dùng [hashing](https://en.wikipedia.org/wiki/Hash_table) để truy xuất
value gắn với một key một cách rất hiệu quả. Hashing là việc kết hợp các
giá trị byte thô của key để tạo ra một số nguyên gọi là *index*. Index này sau đó
được dùng để định vị *hash slot* nơi value của key được lưu.

Thông thường, toàn bộ key được dùng để tính hash index, nhưng có một số
tình huống bạn chỉ cần băm một phần của key. Bạn có thể chọn
phần của key muốn băm bằng một cặp dấu ngoặc nhọn `{...}` để tạo
một *hashtag*. Ví dụ, các key `person:1` và `person:2` tạo ra các
hash index khác nhau, nhưng `{person}:1` và `{person}:2` tạo ra cùng một index vì
chỉ phần hashtag `person` nằm trong dấu ngoặc mới được dùng để tính hash.

Một cách dùng phổ biến của hashtag là cho phép
[các thao tác đa key (multi-key operations)](https://redis.io/docs/latest/operate/rs/databases/durability-ha/clustering)
với một database *clustered* (xem
[Database clustering](https://redis.io/docs/latest/operate/rs/databases/durability-ha/clustering#multikey-operations)
để biết thêm thông tin). Redis không cho phép hầu hết các thao tác đa key trong một database clustered
trừ khi tất cả các key đều tạo ra cùng một hash index. Ví dụ, lệnh
[SINTER](https://redis.io/docs/latest/commands/sinter)
tìm [phần giao (intersection)](https://en.wikipedia.org/wiki/Intersection_(set_theory))
của hai value kiểu [set](https://redis.io/docs/latest/develop/data-types/sets) khác nhau.
Điều này có nghĩa là lệnh

```bash
SINTER group:1 group:2
```

sẽ không hoạt động với một database clustered, nhưng

```bash
SINTER {group}:1 {group}:2
```

sẽ hoạt động vì hashtag đảm bảo hai key tạo ra cùng một hash index.

Lưu ý rằng mặc dù hashtag hữu ích trong một số trường hợp nhất định, bạn không nên
tạo thói quen dùng chúng một cách tràn lan. Nếu bạn có quá nhiều key được ánh xạ vào cùng một
hash slot thì cuối cùng điều này sẽ làm giảm hiệu năng database của bạn.
Xem [Database clustering](https://redis.io/docs/latest/operate/rs/databases/durability-ha/clustering)
để biết thêm thông tin về cách dùng hashtag.

## Thay đổi và truy vấn không gian key (Altering and querying the key space)

Có những lệnh không được định nghĩa cho một kiểu cụ thể nào, nhưng hữu ích
để tương tác với không gian key, và do đó có thể dùng với
key thuộc bất kỳ kiểu nào.

Ví dụ, lệnh [`EXISTS`](https://redis.io/docs/latest/commands/exists) trả về 1 hoặc 0 để báo hiệu một key cho trước
có tồn tại trong database hay không, trong khi lệnh [`DEL`](https://redis.io/docs/latest/commands/del) xóa một key
và value đi kèm, bất kể value đó là gì.

```
> set mykey hello
OK
> exists mykey
(integer) 1
> del mykey
(integer) 1
> exists mykey
(integer) 0
```

Từ các ví dụ, bạn cũng có thể thấy bản thân [`DEL`](https://redis.io/docs/latest/commands/del) trả về 1 hoặc 0 tùy thuộc vào việc
key đã bị xóa (nó có tồn tại) hay không (không có key nào với tên
đó).

Có rất nhiều lệnh liên quan đến không gian key, nhưng hai lệnh trên là
những lệnh thiết yếu, cùng với lệnh [`TYPE`](https://redis.io/docs/latest/commands/type), lệnh trả về loại
value được lưu tại key chỉ định:

```
> set mykey x
OK
> type mykey
string
> del mykey
(integer) 1
> type mykey
none
```

## Hết hạn key (Key expiration)

Trước khi đi tiếp, chúng ta nên xem xét một tính năng quan trọng của Redis hoạt động bất kể kiểu value bạn đang lưu là gì: key expiration (hết hạn key). Key expiration cho phép bạn đặt một thời gian chờ (timeout) cho một key, còn được gọi là "time to live", hay "TTL". Khi hết thời gian sống, key sẽ tự động bị hủy.

Một vài lưu ý quan trọng về key expiration:

* Chúng có thể được đặt với độ chính xác theo giây hoặc theo mili giây.
* Tuy nhiên, độ phân giải của thời gian hết hạn luôn là 1 mili giây.
* Thông tin về thời gian hết hạn được replicate và lưu bền (persist) trên đĩa, thời gian vẫn "trôi qua" một cách ảo khi Redis server của bạn đang dừng (điều này có nghĩa là Redis lưu thời điểm mà một key sẽ hết hạn).

Dùng lệnh [`EXPIRE`](https://redis.io/docs/latest/commands/expire) để đặt thời gian hết hạn cho một key:

```
> set key some-value
OK
> expire key 5
(integer) 1
> get key (immediately)
"some-value"
> get key (after some time)
(nil)
```

Key đã biến mất giữa hai lần gọi [`GET`](https://redis.io/docs/latest/commands/get), vì lần gọi thứ hai bị
trễ hơn 5 giây. Trong ví dụ trên, chúng ta đã dùng [`EXPIRE`](https://redis.io/docs/latest/commands/expire) để
đặt thời gian hết hạn (nó cũng có thể được dùng để đặt một thời gian hết hạn khác
cho một key đã có sẵn thời gian hết hạn, giống như [`PERSIST`](https://redis.io/docs/latest/commands/persist) có thể được dùng để
xóa thời gian hết hạn và làm cho key tồn tại vĩnh viễn). Tuy nhiên, chúng ta
cũng có thể tạo key có thời gian hết hạn bằng các lệnh Redis khác. Ví dụ
dùng các tùy chọn của [`SET`](https://redis.io/docs/latest/commands/set):

```
> set key 100 ex 10
OK
> ttl key
(integer) 9
```

Ví dụ trên đặt một key với value kiểu string là `100`, có thời gian hết hạn
là mười giây. Sau đó lệnh [`TTL`](https://redis.io/docs/latest/commands/ttl) được gọi để kiểm tra
thời gian sống còn lại của key.

Để đặt và kiểm tra thời gian hết hạn theo mili giây, hãy xem các lệnh [`PEXPIRE`](https://redis.io/docs/latest/commands/pexpire) và
[`PTTL`](https://redis.io/docs/latest/commands/pttl), cũng như danh sách đầy đủ các tùy chọn của [`SET`](https://redis.io/docs/latest/commands/set).

## Duyệt không gian key (Navigating the keyspace)

### Scan

Để duyệt dần dần (incrementally) qua các key trong một database Redis một cách hiệu quả, bạn có thể dùng lệnh [`SCAN`](https://redis.io/docs/latest/commands/scan).

Vì [`SCAN`](https://redis.io/docs/latest/commands/scan) cho phép duyệt dần dần, mỗi lần gọi chỉ trả về một số ít phần tử, nên nó có thể được dùng trong môi trường production mà không gặp nhược điểm của các lệnh như [`KEYS`](https://redis.io/docs/latest/commands/keys) hay [`SMEMBERS`](https://redis.io/docs/latest/commands/smembers) — những lệnh có thể chặn (block) server trong thời gian dài (thậm chí vài giây) khi được gọi trên các tập key hoặc tập phần tử lớn.

Tuy nhiên, trong khi các lệnh blocking như [`SMEMBERS`](https://redis.io/docs/latest/commands/smembers) có thể cung cấp tất cả các phần tử thuộc một Set tại một thời điểm nhất định,
thì họ lệnh [`SCAN`](https://redis.io/docs/latest/commands/scan) chỉ đưa ra các đảm bảo hạn chế về các phần tử được trả về, vì tập hợp mà chúng ta duyệt dần dần có thể thay đổi trong quá trình duyệt.

### Keys

Một cách khác để duyệt không gian key là dùng lệnh [`KEYS`](https://redis.io/docs/latest/commands/keys), nhưng cách này nên được dùng một cách cẩn thận, vì [`KEYS`](https://redis.io/docs/latest/commands/keys) sẽ chặn Redis server cho đến khi tất cả các key được trả về.

**Cảnh báo**: hãy coi [`KEYS`](https://redis.io/docs/latest/commands/keys) là một lệnh chỉ nên được dùng trong môi trường
production với sự cẩn trọng tối đa.

[`KEYS`](https://redis.io/docs/latest/commands/keys) có thể làm hỏng hiệu năng khi được thực thi trên các database lớn.
Lệnh này dành cho việc debug và các thao tác đặc biệt, chẳng hạn như thay đổi
bố cục không gian key (keyspace layout) của bạn.
Đừng dùng [`KEYS`](https://redis.io/docs/latest/commands/keys) trong code ứng dụng thông thường của bạn.
Nếu bạn đang tìm cách tìm các key trong một tập con của không gian key, hãy cân nhắc
dùng [`SCAN`](https://redis.io/docs/latest/commands/scan) hoặc [set](https://redis.io/docs/latest/develop/data-types/sets).

Các pattern kiểu glob được hỗ trợ:

* `h?llo` khớp với `hello`, `hallo` và `hxllo`
* `h*llo` khớp với `hllo` và `heeeello`
* `h[ae]llo` khớp với `hello` và `hallo,` nhưng không khớp với `hillo`
* `h[^e]llo` khớp với `hallo`, `hbllo`, ... nhưng không khớp với `hello`
* `h[a-b]llo` khớp với `hallo` và `hbllo`

Dùng `\` để escape các ký tự đặc biệt nếu bạn muốn khớp chúng nguyên văn.

# Mức sử dụng RAM của Redis JSON (Redis JSON RAM Usage)

> Nguồn: <https://redis.io/docs/latest/develop/data-types/json/ram/>

> [!NOTE]
> Do các tính năng liên tục được bổ sung, cải tiến và tối ưu hóa, mức tiêu thụ bộ nhớ của JSON có thể thay đổi tùy theo phiên bản Redis.
> Redis 8 trong Redis Open Source đã được dùng cho các ví dụ trong trang này.

Mỗi key trong Redis đều chiếm bộ nhớ và cần ít nhất lượng RAM để lưu tên key, cũng
như một chút chi phí phụ trội (overhead) cho mỗi key mà Redis sử dụng. Ngoài ra, value trong key cũng cần
RAM.

Redis JSON lưu các giá trị JSON dưới dạng dữ liệu nhị phân sau khi deserialize. Cách biểu diễn này thường
tốn kém hơn, xét về kích thước, so với dạng đã serialize. Mọi giá trị JSON đều chiếm ít nhất 8 byte (trên kiến trúc 64-bit) vì mỗi giá trị được biểu diễn như một lớp bọc mỏng (thin wrapper) quanh một con trỏ. Thông tin kiểu được lưu trong các bit thấp của con trỏ, vốn được đảm bảo bằng 0 do các ràng buộc về căn chỉnh (alignment). Điều này cho phép các bit đó được tận dụng lại để lưu một số dữ liệu phụ trợ.

Với một số loại giá trị JSON, 8 byte là tất cả những gì cần. Null và boolean không cần thêm bộ nhớ nào. Các số nguyên nhỏ được lưu trong bộ nhớ tĩnh (static memory) vì chúng được dùng thường xuyên, nên chúng cũng chỉ dùng 8 byte ban đầu. Tương tự, string, mảng và object rỗng không cần bất kỳ việc ghi chép quản lý (bookkeeping) nào. Thay vào đó, chúng trỏ tới các instance tĩnh của một string, mảng, hoặc object _null_. Dưới đây là một số ví dụ dùng lệnh [JSON.DEBUG MEMORY](https://redis.io/docs/latest/commands/json.debug-memory) để báo cáo mức tiêu thụ bộ nhớ:

```
127.0.0.1:6379> JSON.SET boolean . 'true'
OK
127.0.0.1:6379> JSON.DEBUG MEMORY boolean
(integer) 8

127.0.0.1:6379> JSON.SET null . null
OK
127.0.0.1:6379> JSON.DEBUG MEMORY null
(integer) 8

127.0.0.1:6379> JSON.SET emptystring . '""'
OK
127.0.0.1:6379> JSON.DEBUG MEMORY emptystring
(integer) 8

127.0.0.1:6379> JSON.SET emptyarr . '[]'
OK
127.0.0.1:6379> JSON.DEBUG MEMORY emptyarr
(integer) 8

127.0.0.1:6379> JSON.SET emptyobj . '{}'
OK
127.0.0.1:6379> JSON.DEBUG MEMORY emptyobj
(integer) 8
```

Yêu cầu RAM này là như nhau đối với mọi giá trị scalar, nhưng string cần thêm không gian
tùy theo độ dài của chúng. Ví dụ, một string 3 ký tự sẽ dùng thêm 3 byte:

```
127.0.0.1:6379> JSON.SET foo . '"bar"'
OK
127.0.0.1:6379> JSON.DEBUG MEMORY foo
(integer) 11
```

Trong bốn ví dụ sau, mỗi mảng cần 56 byte. Con số này được chia ra như sau:
- 8 byte cho con trỏ giá trị mảng ban đầu
- 16 byte metadata: 8 byte cho dung lượng đã cấp phát (allocated capacity) và 8 byte cho kích thước tại thời điểm hiện tại của mảng
- 32 byte cho mảng. Dung lượng ban đầu của một mảng là 4. Vì vậy, phép tính là `4 * 8` byte

```
127.0.0.1:6379> JSON.SET arr . '[""]'
OK
127.0.0.1:6379> JSON.DEBUG MEMORY arr
(integer) 56
```

```
127.0.0.1:6379> JSON.SET arr . '["", ""]'
OK
127.0.0.1:6379> JSON.DEBUG MEMORY arr
(integer) 56
```

```
127.0.0.1:6379> JSON.SET arr . '["", "", ""]'
OK
127.0.0.1:6379> JSON.DEBUG MEMORY arr
(integer) 56
```

```
127.0.0.1:6379> JSON.SET arr . '["", "", "", ""]'
OK
127.0.0.1:6379> JSON.DEBUG MEMORY arr
(integer) 56
```

Khi dung lượng hiện tại không đủ để chứa một giá trị mới, mảng sẽ cấp phát lại (reallocate) để tăng gấp đôi dung lượng. Một mảng có 5 phần tử sẽ có dung lượng là 8, do đó tiêu tốn `8 + 16 + 8 * 8 = 88` byte.

```
127.0.0.1:6379> JSON.SET arr . '["", "", "", "", ""]'
OK
127.0.0.1:6379> JSON.DEBUG MEMORY arr
(integer) 88
```

Vì các thao tác cấp phát lại có thể tốn kém, Redis tăng kích thước mảng JSON theo cấp số nhân (geometrically) thay vì tuyến tính. Cách tiếp cận này phân bổ chi phí ra nhiều lần chèn.

Bảng này cho biết kích thước (tính bằng byte) của một vài file kiểm thử từ [module repo](https://github.com/RedisJSON/RedisJSON/tree/master/tests/files), được lưu bằng
JSON. Cột _MessagePack_ chỉ để tham khảo và phản ánh độ dài của giá trị khi được lưu bằng [MessagePack](https://msgpack.org/index.html).

| File                                    | Kích thước file | Redis JSON | MessagePack |
| --------------------------------------- | --------------- | ---------- | ----------- |
| /tests/files/pass-100.json              | 381             | 1069       | 140         |
| /tests/files/pass-jsonsl-1.json         | 1387            | 2190       | 757         |
| /tests/files/pass-json-parser-0000.json | 3718            | 5469       | 2393        |
| /tests/files/pass-jsonsl-yahoo2.json    | 22466           | 26901      | 16869       |
| /tests/files/pass-jsonsl-yelp.json      | 46333           | 57513      | 35529       |

> Lưu ý: Trong phiên bản hiện tại, việc xóa giá trị khỏi các container **không** giải phóng bộ nhớ đã
cấp phát cho container đó.

## Cơ chế tái sử dụng string của JSON (JSON string reuse mechanism)

Redis dùng một cơ chế tái sử dụng string toàn cục (global string reuse) để giảm mức sử dụng bộ nhớ. Khi một giá trị string xuất hiện nhiều lần, dù là trong cùng một JSON document
hay trên các document khác nhau trên cùng một node, Redis chỉ lưu một bản sao duy nhất của string đó và dùng các tham chiếu tới nó.
Cách tiếp cận này đặc biệt hiệu quả khi nhiều document có cấu trúc tương tự nhau.

Tuy nhiên, lệnh `JSON.DEBUG MEMORY` báo cáo mức sử dụng bộ nhớ như thể mỗi instance string được lưu độc lập, ngay cả khi nó thực ra đang được tái sử dụng.
Ví dụ, document `{"foo": ["foo", "foo"]}` tái sử dụng string `"foo"` ở bên trong, nhưng mức sử dụng bộ nhớ được báo cáo lại tính string đó ba lần: một lần cho key và một lần cho mỗi phần tử của mảng.

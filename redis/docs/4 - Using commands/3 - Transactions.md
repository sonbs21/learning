# Transaction (Transactions)

> Nguồn: <https://redis.io/docs/latest/develop/using-commands/transactions/>
> Cách transaction hoạt động trong Redis

Redis Transaction cho phép thực thi một nhóm lệnh
trong một bước duy nhất, chúng xoay quanh các lệnh
[`MULTI`](https://redis.io/docs/latest/commands/multi), [`EXEC`](https://redis.io/docs/latest/commands/exec), [`DISCARD`](https://redis.io/docs/latest/commands/discard) và [`WATCH`](https://redis.io/docs/latest/commands/watch).
Redis Transaction đưa ra hai đảm bảo quan trọng:

* Tất cả các lệnh trong một transaction được tuần tự hóa (serialized) và thực thi
lần lượt. Một request được gửi bởi client khác sẽ không bao giờ được
phục vụ **ở giữa** quá trình thực thi một Redis Transaction.
Điều này đảm bảo rằng các lệnh được thực thi như một thao tác
cô lập (isolated) duy nhất.

* Lệnh [`EXEC`](https://redis.io/docs/latest/commands/exec)
kích hoạt việc thực thi tất cả các lệnh trong transaction, vì vậy
nếu một client mất kết nối tới server trong ngữ cảnh của một
transaction trước khi gọi lệnh [`EXEC`](https://redis.io/docs/latest/commands/exec) thì không thao tác nào
được thực hiện, ngược lại nếu lệnh [`EXEC`](https://redis.io/docs/latest/commands/exec) được gọi, tất cả các
thao tác đều được thực hiện. Khi dùng
[append-only file](https://redis.io/docs/latest/operate/oss_and_stack/management/persistence#append-only-file), Redis đảm bảo
dùng một syscall write(2) duy nhất để ghi transaction xuống đĩa.
Tuy nhiên, nếu Redis server bị crash hoặc bị quản trị viên hệ thống kill
theo một cách cứng nào đó, có thể chỉ một phần các thao tác
được ghi nhận. Redis sẽ phát hiện tình trạng này khi khởi động lại, và sẽ thoát kèm một lỗi.
Dùng công cụ `redis-check-aof` có thể sửa
append only file, công cụ này sẽ xóa transaction bị ghi dở để
server có thể khởi động lại.

Bắt đầu từ phiên bản 2.2, Redis cho phép thêm một đảm bảo nữa ngoài
hai đảm bảo trên, dưới dạng optimistic locking (khóa lạc quan) theo cách rất giống với một
thao tác check-and-set (CAS).
Điều này được trình bày [ở phần sau](#cas) của trang này.

## Cách sử dụng (Usage)

Một Redis Transaction được bắt đầu bằng lệnh [`MULTI`](https://redis.io/docs/latest/commands/multi). Lệnh này
luôn phản hồi `OK`. Tại thời điểm này người dùng có thể gửi nhiều
lệnh. Thay vì thực thi các lệnh này, Redis sẽ xếp chúng vào hàng đợi (queue).
Tất cả các lệnh được thực thi khi [`EXEC`](https://redis.io/docs/latest/commands/exec) được gọi.

Thay vào đó, gọi [`DISCARD`](https://redis.io/docs/latest/commands/discard) sẽ xóa sạch hàng đợi của transaction và thoát
khỏi transaction.

Ví dụ sau tăng các key `foo` và `bar` một cách nguyên tử (atomically).

```
> MULTI
OK
> INCR foo
QUEUED
> INCR bar
QUEUED
> EXEC
1) (integer) 1
2) (integer) 1
```

Như đã thấy rõ từ phiên làm việc ở trên, [`EXEC`](https://redis.io/docs/latest/commands/exec) trả về một
mảng các phản hồi, trong đó mỗi phần tử là phản hồi của một lệnh
trong transaction, theo đúng thứ tự các lệnh được gửi.

Khi một kết nối Redis đang ở trong ngữ cảnh của một request [`MULTI`](https://redis.io/docs/latest/commands/multi),
tất cả các lệnh sẽ phản hồi bằng chuỗi `QUEUED` (được gửi dưới dạng Status Reply
xét theo góc độ giao thức Redis). Một lệnh đã được xếp hàng
đơn giản là được lên lịch để thực thi khi [`EXEC`](https://redis.io/docs/latest/commands/exec) được gọi.

## Lỗi bên trong một transaction (Errors inside a transaction)

Trong một transaction, có thể gặp hai loại lỗi lệnh:

* Một lệnh có thể không xếp hàng được, nên có thể có lỗi trước khi [`EXEC`](https://redis.io/docs/latest/commands/exec) được gọi.
Ví dụ lệnh có thể sai cú pháp (sai số lượng đối số,
sai tên lệnh, ...), hoặc có thể có một tình trạng nghiêm trọng nào đó như hết
bộ nhớ (nếu server được cấu hình giới hạn bộ nhớ bằng chỉ thị `maxmemory`).
* Một lệnh có thể thất bại *sau khi* [`EXEC`](https://redis.io/docs/latest/commands/exec) được gọi, ví dụ vì chúng ta thực hiện
một thao tác trên một key có value sai kiểu (như gọi một thao tác list trên một value kiểu string).

Bắt đầu từ Redis 2.6.5, server sẽ phát hiện lỗi trong quá trình tích lũy các lệnh.
Sau đó nó sẽ từ chối thực thi transaction, trả về một lỗi khi [`EXEC`](https://redis.io/docs/latest/commands/exec), và hủy bỏ transaction.

> **Lưu ý cho Redis < 2.6.5:** Trước Redis 2.6.5, client cần phát hiện các lỗi xảy ra trước [`EXEC`](https://redis.io/docs/latest/commands/exec) bằng cách kiểm tra
giá trị trả về của lệnh được xếp hàng: nếu lệnh phản hồi QUEUED thì nó đã được
xếp hàng đúng, ngược lại Redis trả về một lỗi.
Nếu có lỗi trong khi xếp hàng một lệnh, hầu hết các client
sẽ hủy bỏ (abort) và loại bỏ (discard) transaction. Ngược lại, nếu client chọn tiếp tục với transaction
thì lệnh [`EXEC`](https://redis.io/docs/latest/commands/exec) sẽ thực thi tất cả các lệnh đã được xếp hàng thành công bất kể các lỗi trước đó.

Ngược lại, các lỗi xảy ra *sau* [`EXEC`](https://redis.io/docs/latest/commands/exec) không được xử lý theo cách đặc biệt nào:
tất cả các lệnh khác vẫn sẽ được thực thi ngay cả khi có lệnh nào đó thất bại trong transaction.

Điều này rõ ràng hơn ở mức giao thức. Trong ví dụ sau, một
lệnh sẽ thất bại khi được thực thi dù cú pháp đúng:

```
Trying 127.0.0.1...
Connected to localhost.
Escape character is '^]'.
MULTI
+OK
SET a abc
+QUEUED
LPOP a
+QUEUED
EXEC
*2
+OK
-WRONGTYPE Operation against a key holding the wrong kind of value
```

[`EXEC`](https://redis.io/docs/latest/commands/exec) trả về [bulk string reply](https://redis.io/docs/latest/develop/reference/protocol-spec#bulk-string-reply) gồm hai phần tử, trong đó một phần tử là mã `OK` và
phần tử còn lại là một phản hồi lỗi. Việc tìm một cách hợp lý để đưa lỗi tới người dùng
là tùy thuộc vào thư viện client.

Điều quan trọng cần lưu ý là
**ngay cả khi một lệnh thất bại, tất cả các lệnh khác trong hàng đợi vẫn được xử lý** – Redis sẽ _không_ dừng
việc xử lý các lệnh.

Một ví dụ khác, cũng dùng wire protocol với `telnet`, cho thấy
lỗi cú pháp lại được báo ngay lập tức (ASAP):

```
MULTI
+OK
INCR a b c
-ERR wrong number of arguments for 'incr' command
```

Lần này, do lỗi cú pháp, lệnh [`INCR`](https://redis.io/docs/latest/commands/incr) sai hoàn toàn không được xếp
vào hàng đợi.

## Còn rollback thì sao? (What about rollbacks?)

Redis không hỗ trợ rollback transaction vì việc hỗ trợ rollback
sẽ ảnh hưởng đáng kể tới sự đơn giản và hiệu năng của Redis.

## Hủy bỏ hàng đợi lệnh (Discarding the command queue)

[`DISCARD`](https://redis.io/docs/latest/commands/discard) có thể được dùng để hủy bỏ một transaction. Trong trường hợp này, không có
lệnh nào được thực thi và trạng thái của kết nối được khôi phục về
bình thường.

```
> SET foo 1
OK
> MULTI
OK
> INCR foo
QUEUED
> DISCARD
OK
> GET foo
"1"
```
<a name="cas"></a>

## Optimistic locking dùng check-and-set (Optimistic locking using check-and-set)

[`WATCH`](https://redis.io/docs/latest/commands/watch) được dùng để cung cấp hành vi check-and-set (CAS) cho Redis
transaction.

Các key được [`WATCH`](https://redis.io/docs/latest/commands/watch) sẽ được giám sát để phát hiện các thay đổi trên chúng. Nếu
ít nhất một key đang được watch bị sửa đổi trước lệnh [`EXEC`](https://redis.io/docs/latest/commands/exec), thì
toàn bộ transaction bị hủy, và [`EXEC`](https://redis.io/docs/latest/commands/exec) trả về một [Null reply](https://redis.io/docs/latest/develop/reference/protocol-spec#nil-reply) để thông báo rằng
transaction đã thất bại.

Ví dụ, hãy tưởng tượng chúng ta cần tăng value
của một key thêm 1 một cách nguyên tử (giả sử Redis không có [`INCR`](https://redis.io/docs/latest/commands/incr)).

Cách thử đầu tiên có thể như sau:

```
val = GET mykey
val = val + 1
SET mykey $val
```

Cách này chỉ hoạt động đáng tin cậy nếu chỉ có một client duy nhất thực hiện
thao tác tại một thời điểm. Nếu nhiều client cố gắng tăng key
vào khoảng cùng một thời điểm thì sẽ xảy ra race condition. Ví dụ,
client A và B sẽ cùng đọc value cũ, chẳng hạn là 10. Value sẽ
được cả hai client tăng lên 11, và cuối cùng được [`SET`](https://redis.io/docs/latest/commands/set) làm value
của key. Vì vậy value cuối cùng sẽ là 11 thay vì 12.

Nhờ có [`WATCH`](https://redis.io/docs/latest/commands/watch), chúng ta có thể mô hình hóa vấn đề này rất tốt:

```
WATCH mykey
val = GET mykey
val = val + 1
MULTI
SET mykey $val
EXEC
```

Dùng đoạn code trên, nếu có race condition và một client khác
sửa đổi kết quả của `val` trong khoảng thời gian giữa lần chúng ta gọi [`WATCH`](https://redis.io/docs/latest/commands/watch) và
lần chúng ta gọi [`EXEC`](https://redis.io/docs/latest/commands/exec), transaction sẽ thất bại.

Chúng ta chỉ cần lặp lại thao tác với hy vọng lần này sẽ không gặp
race mới. Hình thức khóa này được gọi là _optimistic locking_ (khóa lạc quan).
Trong nhiều trường hợp sử dụng, nhiều client sẽ truy cập các key khác nhau,
nên xung đột khó xảy ra – thường thì không cần lặp lại thao tác.

Bắt đầu từ phiên bản 8.4, Redis cung cấp các lệnh compare-and-set và compare-and-delete nguyên tử mới cho các key kiểu string.
Một client có thể dùng một lệnh [`SET`](https://redis.io/docs/latest/commands/set) duy nhất với các tùy chọn `IFEQ`/`IFNE`/`IFDEQ`/`IFDNE` để cập nhật nguyên tử một key kiểu string chỉ khi value của nó chưa thay đổi kể từ lúc được lấy về.
Cách này đơn giản và nhanh hơn nhiều.
Tương tự, một client có thể dùng một lệnh [`DELEX`](https://redis.io/docs/latest/commands/delex) duy nhất cho compare-and-delete: xóa nguyên tử một key kiểu string nếu value của nó chưa thay đổi kể từ lúc được lấy về.

## Giải thích WATCH (WATCH explained)

Vậy thực chất [`WATCH`](https://redis.io/docs/latest/commands/watch) là gì? Nó là một lệnh khiến
[`EXEC`](https://redis.io/docs/latest/commands/exec) trở nên có điều kiện: chúng ta yêu cầu Redis thực hiện
transaction chỉ khi không có key nào trong số các key được [`WATCH`](https://redis.io/docs/latest/commands/watch) bị sửa đổi. Điều này bao gồm
cả các sửa đổi do client thực hiện, như các lệnh ghi, và do chính Redis thực hiện,
như hết hạn (expiration) hoặc bị loại bỏ (eviction). Nếu các key bị sửa đổi trong khoảng thời gian từ khi chúng được
[`WATCH`](https://redis.io/docs/latest/commands/watch) đến khi nhận được [`EXEC`](https://redis.io/docs/latest/commands/exec), thì thay vào đó toàn bộ transaction sẽ bị hủy
bỏ.

**LƯU Ý**
* Trong các phiên bản Redis trước 6.0.9, một key hết hạn sẽ không khiến transaction
bị hủy bỏ. [Xem thêm về điều này](https://github.com/redis/redis/pull/7920)
* Các lệnh bên trong một transaction sẽ không kích hoạt điều kiện [`WATCH`](https://redis.io/docs/latest/commands/watch) vì chúng
chỉ được xếp hàng cho tới khi [`EXEC`](https://redis.io/docs/latest/commands/exec) được gửi.

[`WATCH`](https://redis.io/docs/latest/commands/watch) có thể được gọi nhiều lần. Đơn giản là tất cả các lần gọi [`WATCH`](https://redis.io/docs/latest/commands/watch) sẽ
có tác dụng theo dõi các thay đổi kể từ lúc gọi, cho tới
thời điểm [`EXEC`](https://redis.io/docs/latest/commands/exec) được gọi. Bạn cũng có thể truyền bất kỳ số lượng key nào vào một
lần gọi [`WATCH`](https://redis.io/docs/latest/commands/watch) duy nhất.

Khi [`EXEC`](https://redis.io/docs/latest/commands/exec) được gọi, tất cả các key đều được [`UNWATCH`](https://redis.io/docs/latest/commands/unwatch), bất kể
transaction có bị hủy bỏ hay không. Ngoài ra, khi một kết nối client
bị đóng, mọi thứ đều được [`UNWATCH`](https://redis.io/docs/latest/commands/unwatch).

Cũng có thể dùng lệnh [`UNWATCH`](https://redis.io/docs/latest/commands/unwatch) (không có đối số)
để xóa sạch tất cả các key đang được watch. Đôi khi điều này hữu ích khi chúng ta
khóa lạc quan một vài key, vì có thể chúng ta cần thực hiện một
transaction để thay đổi các key đó, nhưng sau khi đọc nội dung hiện tại
của các key, chúng ta lại không muốn tiếp tục. Khi điều này xảy ra, chúng ta chỉ cần gọi
[`UNWATCH`](https://redis.io/docs/latest/commands/unwatch) để kết nối có thể được dùng tự do ngay cho các
transaction mới.

### Dùng WATCH để hiện thực ZPOP (Using WATCH to implement ZPOP)

Một ví dụ hay để minh họa cách [`WATCH`](https://redis.io/docs/latest/commands/watch) có thể được dùng để tạo ra các
thao tác nguyên tử mới mà Redis không hỗ trợ sẵn là hiện thực ZPOP
([`ZPOPMIN`](https://redis.io/docs/latest/commands/zpopmin), [`ZPOPMAX`](https://redis.io/docs/latest/commands/zpopmax) và các biến thể blocking của chúng chỉ mới được thêm vào
từ phiên bản 5.0), đó là một lệnh lấy ra (pop) phần tử có score thấp hơn
từ một sorted set theo cách nguyên tử. Đây là cách
hiện thực đơn giản nhất:

```
WATCH zset
element = ZRANGE zset 0 0
MULTI
ZREM zset element
EXEC
```

Nếu [`EXEC`](https://redis.io/docs/latest/commands/exec) thất bại (tức là trả về một [Null reply](https://redis.io/docs/latest/develop/reference/protocol-spec#nil-reply)) thì chúng ta chỉ cần lặp lại thao tác.

## Redis scripting và transaction (Redis scripting and transactions)

Một thứ khác cần cân nhắc cho các thao tác dạng transaction trong Redis là
[redis script](https://redis.io/docs/latest/commands/eval), vốn có tính transaction. Mọi thứ
bạn có thể làm với một Redis Transaction, bạn cũng có thể làm với một script, và
thường thì script sẽ vừa đơn giản hơn vừa nhanh hơn.

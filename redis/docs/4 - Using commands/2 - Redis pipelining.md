# Redis pipelining

> Nguồn: <https://redis.io/docs/latest/develop/using-commands/pipelining/>
> Cách tối ưu thời gian khứ hồi (round-trip time) bằng cách gom nhóm các lệnh Redis

Redis pipelining là một kỹ thuật cải thiện hiệu năng bằng cách gửi nhiều lệnh cùng lúc mà không cần chờ phản hồi của từng lệnh riêng lẻ. Pipelining được hỗ trợ bởi hầu hết các Redis client. Tài liệu này mô tả vấn đề mà pipelining được thiết kế để giải quyết và cách pipelining hoạt động trong Redis.

## Giao thức Request/Response và thời gian khứ hồi (Request/Response protocols and round-trip time (RTT))

Redis là một TCP server sử dụng mô hình client-server và cái được gọi là giao thức *Request/Response*.

Điều này có nghĩa là thông thường một request được hoàn thành qua các bước sau:

* Client gửi một truy vấn (query) tới server, và đọc từ socket, thường theo cách blocking, để lấy phản hồi của server.
* Server xử lý lệnh và gửi phản hồi trở lại cho client.

Vì vậy, ví dụ một chuỗi bốn lệnh sẽ trông như thế này:

 * *Client:* INCR X
 * *Server:* 1
 * *Client:* INCR X
 * *Server:* 2
 * *Client:* INCR X
 * *Server:* 3
 * *Client:* INCR X
 * *Server:* 4

Client và Server được kết nối với nhau qua một đường liên kết mạng (network link).
Đường liên kết đó có thể rất nhanh (một loopback interface) hoặc rất chậm (một kết nối được thiết lập qua Internet với nhiều hop giữa hai host).
Dù độ trễ mạng là bao nhiêu, các gói tin vẫn cần thời gian để đi từ client tới server, rồi đi ngược từ server về client để mang theo phản hồi.

Khoảng thời gian này được gọi là RTT (Round Trip Time – thời gian khứ hồi).
Dễ thấy điều này có thể ảnh hưởng tới hiệu năng thế nào khi một client cần thực hiện nhiều request liên tiếp (ví dụ thêm nhiều phần tử vào cùng một list, hoặc nạp dữ liệu vào một database với nhiều key).
Ví dụ, nếu RTT là 250 mili giây (trong trường hợp một đường liên kết rất chậm qua Internet), thì ngay cả khi server có khả năng xử lý 100k request mỗi giây, chúng ta cũng chỉ có thể xử lý tối đa bốn request mỗi giây.

Nếu interface được dùng là loopback interface, RTT ngắn hơn nhiều, thường dưới một mili giây, nhưng ngay cả như vậy nó vẫn cộng dồn lại thành rất nhiều nếu bạn cần thực hiện nhiều thao tác ghi liên tiếp.

May mắn là có một cách để cải thiện trường hợp sử dụng này.

## Redis Pipelining

Một server Request/Response có thể được hiện thực sao cho nó có khả năng xử lý các request mới ngay cả khi client chưa đọc các phản hồi cũ.
Bằng cách này, có thể gửi *nhiều lệnh* tới server mà hoàn toàn không cần chờ phản hồi, và cuối cùng đọc các phản hồi trong một bước duy nhất.

Điều này được gọi là pipelining, và là một kỹ thuật đã được sử dụng rộng rãi trong nhiều thập kỷ.
Ví dụ, nhiều bản hiện thực giao thức POP3 đã hỗ trợ tính năng này, giúp tăng tốc đáng kể quá trình tải email mới từ server.

Redis đã hỗ trợ pipelining từ những ngày đầu, nên dù bạn đang chạy phiên bản nào, bạn đều có thể dùng pipelining với Redis.
Đây là một ví dụ sử dụng tiện ích netcat thô:

```bash
$ (printf "PING\r\nPING\r\nPING\r\n"; sleep 1) | nc localhost 6379
+PONG
+PONG
+PONG
```

Lần này chúng ta không phải trả chi phí RTT cho mỗi lần gọi, mà chỉ một lần cho cả ba lệnh.

Nói một cách rõ ràng, với pipelining, thứ tự các thao tác trong ví dụ đầu tiên của chúng ta sẽ như sau:

 * *Client:* INCR X
 * *Client:* INCR X
 * *Client:* INCR X
 * *Client:* INCR X
 * *Server:* 1
 * *Server:* 2
 * *Server:* 3
 * *Server:* 4

> **LƯU Ý QUAN TRỌNG**: Trong khi client gửi lệnh bằng pipelining, server sẽ buộc phải xếp hàng (queue) các phản hồi, sử dụng bộ nhớ. Vì vậy nếu bạn cần gửi rất nhiều lệnh bằng pipelining, tốt hơn là gửi chúng thành các lô (batch), mỗi lô chứa một số lượng hợp lý, ví dụ 10k lệnh, đọc các phản hồi, rồi lại gửi 10k lệnh khác, và cứ thế tiếp tục. Tốc độ sẽ gần như tương đương, nhưng lượng bộ nhớ phát sinh thêm sẽ tối đa chỉ bằng lượng cần để xếp hàng các phản hồi cho 10k lệnh này.

## Không chỉ là vấn đề RTT (It's not just a matter of RTT)

Pipelining không chỉ là một cách để giảm chi phí độ trễ gắn với
thời gian khứ hồi, mà thực tế nó còn cải thiện đáng kể số lượng thao tác
bạn có thể thực hiện mỗi giây trên một Redis server nhất định.
Điều này là vì khi không dùng pipelining, việc phục vụ mỗi lệnh là rất rẻ xét từ
góc độ truy cập các cấu trúc dữ liệu và tạo ra phản hồi,
nhưng lại rất tốn kém xét từ góc độ thực hiện socket I/O. Việc này
bao gồm gọi syscall `read()` và `write()`, nghĩa là chuyển từ user
land sang kernel land.
Việc chuyển ngữ cảnh (context switch) là một sự sụt giảm tốc độ rất lớn.

Khi dùng pipelining, nhiều lệnh thường được đọc chỉ với một system call `read()`
duy nhất, và nhiều phản hồi được gửi đi chỉ với một system call `write()`
duy nhất. Do đó, tổng số truy vấn được thực hiện mỗi giây
ban đầu tăng gần như tuyến tính khi pipeline dài hơn, và cuối cùng
đạt tới 10 lần mức cơ sở (baseline) thu được khi không dùng pipelining, như minh họa trong hình này.

![Pipeline size and IOPs](https://redis.io/docs/latest/images/dev/reference/pipeline_iops.webp)

## Một ví dụ code thực tế (A real world code example)

Trong benchmark sau, chúng ta sẽ dùng Redis Ruby client, có hỗ trợ pipelining, để kiểm tra mức cải thiện tốc độ nhờ pipelining:

```ruby
require 'rubygems'
require 'redis'

def bench(descr)
  start = Time.now
  yield
  puts "#{descr} #{Time.now - start} seconds"
end

def without_pipelining
  r = Redis.new
  10_000.times do
    r.ping
  end
end

def with_pipelining
  r = Redis.new
  r.pipelined do |rp|
    10_000.times do
      rp.ping
    end
  end
end

bench('without pipelining') do
  without_pipelining
end
bench('with pipelining') do
  with_pipelining
end
```

Chạy đoạn script đơn giản ở trên cho ra các số liệu sau trên hệ thống Mac OS X của tôi, chạy qua loopback interface, nơi pipelining sẽ mang lại mức cải thiện nhỏ nhất vì RTT vốn đã khá thấp:

```
without pipelining 1.185238 seconds
with pipelining 0.250783 seconds
```

Như bạn thấy, khi dùng pipelining, chúng ta đã cải thiện việc truyền tải lên gấp năm lần.

## Pipelining so với Scripting (Pipelining vs Scripting)

Khi dùng [Redis scripting](https://redis.io/docs/latest/commands/eval), có sẵn từ Redis 2.6, một số trường hợp sử dụng pipelining có thể được giải quyết hiệu quả hơn bằng các script thực hiện phần lớn công việc cần thiết ở phía server.
Một lợi thế lớn của scripting là nó có thể vừa đọc vừa ghi dữ liệu với độ trễ tối thiểu, khiến các thao tác như *đọc, tính toán, ghi* (read, compute, write) trở nên rất nhanh (pipelining không thể giúp trong tình huống này vì client cần phản hồi của lệnh đọc trước khi có thể gọi lệnh ghi).

Đôi khi ứng dụng cũng có thể muốn gửi các lệnh [`EVAL`](https://redis.io/docs/latest/commands/eval) hoặc [`EVALSHA`](https://redis.io/docs/latest/commands/evalsha) trong một pipeline.
Điều này hoàn toàn khả thi và Redis hỗ trợ rõ ràng việc đó bằng lệnh [SCRIPT LOAD](https://redis.io/docs/latest/commands/script-load) (nó đảm bảo rằng [`EVALSHA`](https://redis.io/docs/latest/commands/evalsha) có thể được gọi mà không có rủi ro bị lỗi).

## Phụ lục: Tại sao busy loop lại chậm ngay cả trên loopback interface? (Appendix: Why are busy loops slow even on the loopback interface?)

Ngay cả với tất cả kiến thức nền đã được trình bày trong trang này, bạn có thể vẫn thắc mắc tại sao
một benchmark Redis như sau (viết bằng mã giả), lại chậm ngay cả khi
được thực thi trên loopback interface, khi server và client đang chạy
trên cùng một máy vật lý:

```sh
FOR-ONE-SECOND:
    Redis.SET("foo","bar")
END
```

Suy cho cùng, nếu cả tiến trình Redis và benchmark đều chạy trên cùng một
máy, chẳng phải nó chỉ là sao chép các thông điệp trong bộ nhớ từ chỗ này sang chỗ khác mà không có
độ trễ thực sự hay mạng nào liên quan sao?

Lý do là các tiến trình trong một hệ thống không phải lúc nào cũng đang chạy, thực tế chính
bộ lập lịch của kernel (kernel scheduler) mới là thứ cho phép tiến trình chạy.
Vì vậy, ví dụ, khi benchmark được phép chạy, nó đọc phản hồi từ Redis server (liên quan tới lệnh được thực thi gần nhất), và ghi một lệnh mới.
Lệnh lúc này nằm trong buffer của loopback interface, nhưng để server đọc được nó, kernel phải lập lịch cho tiến trình server (hiện đang bị block trong một system call)
chạy, và cứ thế tiếp tục.
Vì vậy, xét về mặt thực tế, loopback interface vẫn có độ trễ giống như mạng, do cách bộ lập lịch của kernel hoạt động.

Về cơ bản, một benchmark dạng busy loop là điều ngớ ngẩn nhất có thể làm khi
đo hiệu năng trên một server mạng. Điều khôn ngoan là đơn giản tránh
benchmark theo cách này.

# Redis bitmaps

> Nguồn: <https://redis.io/docs/latest/develop/data-types/strings/bitmaps/>
> Giới thiệu về Redis bitmaps

## Chú giải các ví dụ code (Code Examples Legend)

Các ví dụ code bên dưới cho thấy cách thực hiện cùng một thao tác trong các ngôn ngữ lập trình và thư viện client khác nhau:

- **Redis CLI**: Giao diện dòng lệnh (command-line interface) cho Redis
- **C# (Synchronous)**: client đồng bộ StackExchange.Redis
- **C# (Asynchronous)**: client bất đồng bộ StackExchange.Redis
- **Go**: client go-redis
- **Java (Synchronous - Jedis)**: client đồng bộ Jedis
- **Java (Asynchronous - Lettuce)**: client bất đồng bộ Lettuce
- **Java (Reactive - Lettuce)**: client reactive/streaming Lettuce
- **JavaScript (Node.js)**: client node-redis
- **PHP**: client Predis
- **Python**: client redis-py
- **Rust (Synchronous)**: client đồng bộ redis-rs
- **Rust (Asynchronous)**: client bất đồng bộ redis-rs

Mỗi ví dụ code minh họa cùng một thao tác cơ bản trên các ngôn ngữ khác nhau. Cú pháp và pattern cụ thể thay đổi tùy theo ngôn ngữ và thư viện client, nhưng các lệnh Redis bên dưới và hành vi của chúng vẫn nhất quán.

---


### Tóm tắt các lệnh bitmap/bitfield (Bitmap/bitfield command summary)

**7 lệnh trong nhóm này:**

[Xem tất cả các lệnh bitmap](https://redis.io/commands/?group=bitmap)

| Lệnh | Tóm tắt | Độ phức tạp | Từ phiên bản |
|---------|---------|------------|-------|
| [BITCOUNT](https://redis.io/commands/bitcount/) | Đếm số bit được bật (set bit) (population counting) trong một string. | O(N) | 2.6.0 |
| [BITFIELD](https://redis.io/commands/bitfield/) | Thực hiện các thao tác số nguyên bitfield tùy ý trên string. | O(1) cho mỗi subcommand được chỉ định | 3.2.0 |
| [BITFIELD_RO](https://redis.io/commands/bitfield_ro/) | Thực hiện các thao tác số nguyên bitfield chỉ đọc (read-only) tùy ý trên string. | O(1) cho mỗi subcommand được chỉ định | 6.0.0 |
| [BITOP](https://redis.io/commands/bitop/) | Thực hiện các phép toán bitwise trên nhiều string, và lưu kết quả. | O(N) | 2.6.0 |
| [BITPOS](https://redis.io/commands/bitpos/) | Tìm bit được bật (1) hoặc bị xóa (0) đầu tiên trong một string. | O(N) | 2.8.7 |
| [GETBIT](https://redis.io/commands/getbit/) | Trả về giá trị của một bit theo offset. | O(1) | 2.2.0 |
| [SETBIT](https://redis.io/commands/setbit/) | Bật hoặc xóa bit tại offset của value string. Tạo key nếu nó chưa tồn tại. | O(1) | 2.2.0 |



Bitmap không phải là một kiểu dữ liệu thực sự, mà là một tập các thao tác hướng bit (bit-oriented)
được định nghĩa trên kiểu String, vốn được xử lý như một vector bit (bit vector).
Vì string là các blob binary-safe và độ dài tối đa của chúng là 512 MB,
nên chúng phù hợp để đặt tới 2^32 bit khác nhau.

Bạn có thể thực hiện các phép toán bitwise trên một hoặc nhiều string.
Một số ví dụ về trường hợp sử dụng bitmap bao gồm:

* Biểu diễn tập hợp hiệu quả cho các trường hợp mà các thành viên của tập hợp tương ứng với các số nguyên 0-N.
* Quyền (permission) của object, trong đó mỗi bit biểu diễn một quyền cụ thể, tương tự như cách hệ thống file lưu quyền.

## Ví dụ (Example)

Giả sử bạn có 1000 tay đua xe đạp đang đua qua vùng nông thôn, với các cảm biến (sensor) trên xe được đánh nhãn 0-999.
Bạn muốn nhanh chóng xác định xem một cảm biến cho trước đã ping tới server theo dõi (tracking server) trong vòng một giờ hay chưa, để kiểm tra tình trạng của một tay đua.

Bạn có thể biểu diễn tình huống này bằng một bitmap có key tham chiếu tới giờ hiện tại.

* Tay đua 123 ping tới server vào ngày 1 tháng 1 năm 2024 trong khung giờ 00:00. Sau đó bạn có thể xác nhận rằng tay đua 123 đã ping tới server. Bạn cũng có thể kiểm tra xem tay đua 456 đã ping tới server trong cùng giờ đó hay chưa.

*Nền tảng: Đặt và lấy từng bit riêng lẻ bằng SETBIT và GETBIT để theo dõi các trạng thái nhị phân*

**Lệnh (Commands):** SETBIT, GETBIT

**Độ phức tạp (Complexity):**
- SETBIT: O(1)
- GETBIT: O(1)

**Có sẵn trong (Available in):** Redis CLI, C#, Go, Java (Asynchronous - Lettuce), Java (Reactive - Lettuce), Java (Synchronous - Jedis), JavaScript (Node.js), PHP, Python

##### Redis CLI

```
> SETBIT pings:2024-01-01-00:00 123 1
(integer) 0
> GETBIT pings:2024-01-01-00:00 123
1
> GETBIT pings:2024-01-01-00:00 456
0
```

##### C#

```csharp
        bool res1 = db.StringSetBit("pings:2024-01-01-00:00", 123, true);
        Console.WriteLine(res1);    // >>> 0

        bool res2 = db.StringGetBit("pings:2024-01-01-00:00", 123);
        Console.WriteLine(res2);    // >>> True

        bool res3 = db.StringGetBit("pings:2024-01-01-00:00", 456);
        Console.WriteLine(res3);    // >>> False
```

##### Go

```go
	res1, err := rdb.SetBit(ctx, "pings:2024-01-01-00:00", 123, 1).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res1) // >>> 0

	res2, err := rdb.GetBit(ctx, "pings:2024-01-01-00:00", 123).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res2) // >>> 1

	res3, err := rdb.GetBit(ctx, "pings:2024-01-01-00:00", 456).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res3) // >>> 0
```

##### Java (Asynchronous - Lettuce)

```java
            CompletableFuture<Void> ping = async.setbit("pings:2024-01-01-00:00", 123, 1).thenCompose(res1 -> {
                System.out.println(res1); // >>> 0
                return async.getbit("pings:2024-01-01-00:00", 123);
            }).thenCompose(res2 -> {
                System.out.println(res2); // >>> 1
                return async.getbit("pings:2024-01-01-00:00", 456);
            }).thenAccept(res3 -> {
                System.out.println(res3); // >>> 0
            }).toCompletableFuture();
```

##### Java (Reactive - Lettuce)

```java
            Mono<Void> ping = reactive.setbit("pings:2024-01-01-00:00", 123, 1).doOnNext(res1 -> {
                System.out.println(res1); // >>> 0
            }).flatMap(v -> reactive.getbit("pings:2024-01-01-00:00", 123)).doOnNext(res2 -> {
                System.out.println(res2); // >>> 1
            }).flatMap(v -> reactive.getbit("pings:2024-01-01-00:00", 456)).doOnNext(res3 -> {
                System.out.println(res3); // >>> 0
            }).then();
```

##### Java (Synchronous - Jedis)

```java
        boolean res1 = jedis.setbit("pings:2024-01-01-00:00", 123, true);
        System.out.println(res1); // >>> false

        boolean res2 = jedis.getbit("pings:2024-01-01-00:00", 123);
        System.out.println(res2); // >>> true

        boolean res3 = jedis.getbit("pings:2024-01-01-00:00", 456);
        System.out.println(res3); // >>> false
```

##### JavaScript (Node.js)

```javascript
const res1 = await client.setBit("pings:2024-01-01-00:00", 123, 1)
console.log(res1)  // >>> 0

const res2 = await client.getBit("pings:2024-01-01-00:00", 123)
console.log(res2)  // >>> 1

const res3 = await client.getBit("pings:2024-01-01-00:00", 456)
console.log(res3)  // >>> 0
```

##### PHP

```php
        $res1 = $r->setbit('pings:2024-01-01-00:00', 123, 1);
        echo $res1 . PHP_EOL;
        // >>> 0

        $res2 = $r->getbit('pings:2024-01-01-00:00', 123);
        echo $res2 . PHP_EOL;
        // >>> 1

        $res3 = $r->getbit('pings:2024-01-01-00:00', 456);
        echo $res3 . PHP_EOL;
        // >>> 0
```

##### Python

```python
res1 = r.setbit("pings:2024-01-01-00:00", 123, 1)
print(res1)  # >>> 0

res2 = r.getbit("pings:2024-01-01-00:00", 123)
print(res2)  # >>> 1

res3 = r.getbit("pings:2024-01-01-00:00", 456)
print(res3)  # >>> 0
```




## Các thao tác bit (Bit Operations)

Các thao tác bit được chia thành hai nhóm: các thao tác trên một bit đơn lẻ có thời gian hằng số
(constant-time), như đặt một bit thành 1 hoặc 0, hoặc lấy giá trị của nó, và
các thao tác trên nhóm bit, ví dụ đếm số bit được bật
trong một dải bit cho trước (ví dụ: population counting).

Một trong những ưu điểm lớn nhất của bitmap là chúng thường mang lại
mức tiết kiệm không gian cực lớn khi lưu trữ thông tin. Ví dụ, trong một hệ thống
mà các user khác nhau được biểu diễn bằng các user ID tăng dần, có thể
ghi nhớ một thông tin dạng một bit (ví dụ, biết liệu
một user có muốn nhận newsletter hay không) của 4 tỷ user chỉ với 512 MB bộ nhớ.

Lệnh [`SETBIT`](https://redis.io/docs/latest/commands/setbit) nhận đối số thứ nhất là số thứ tự bit, và đối số thứ hai
là giá trị để đặt cho bit, là 1 hoặc 0. Lệnh này
tự động mở rộng string nếu bit được trỏ tới nằm ngoài
độ dài hiện tại của string.

[`GETBIT`](https://redis.io/docs/latest/commands/getbit) chỉ đơn giản trả về giá trị của bit tại chỉ số được chỉ định.
Các bit nằm ngoài phạm vi (trỏ tới một bit nằm ngoài độ dài của string
được lưu trong key đích) luôn được coi là bằng 0.

Có ba lệnh thao tác trên nhóm bit:

1. [`BITOP`](https://redis.io/docs/latest/commands/bitop) thực hiện các phép toán bitwise giữa các string khác nhau. Các toán tử được cung cấp là `AND`, `OR`, `XOR`, `NOT`, `DIFF`, `DIFF1`, `ANDOR`, và `ONE`.
2. [`BITCOUNT`](https://redis.io/docs/latest/commands/bitcount) thực hiện population counting, báo cáo số lượng bit được đặt thành 1.
3. [`BITPOS`](https://redis.io/docs/latest/commands/bitpos) tìm bit đầu tiên có giá trị được chỉ định là 0 hoặc 1.

Cả [`BITPOS`](https://redis.io/docs/latest/commands/bitpos) và [`BITCOUNT`](https://redis.io/docs/latest/commands/bitcount) đều có thể thao tác trên các dải byte (byte range) của
string, thay vì chạy trên toàn bộ độ dài của string. Chúng ta có thể dễ dàng xem số lượng bit đã được bật trong một bitmap.

*Đếm bit: Dùng BITCOUNT để đếm số bit được bật trong một bitmap khi bạn cần lấy population count*

**Dựa trên (Builds upon):** ping

**Lệnh (Commands):** BITCOUNT

**Độ phức tạp (Complexity):**
- BITCOUNT: O(N)

**Có sẵn trong (Available in):** Redis CLI, C#, Go, Java (Asynchronous - Lettuce), Java (Reactive - Lettuce), Java (Synchronous - Jedis), JavaScript (Node.js), PHP, Python

##### Redis CLI

```
> BITCOUNT pings:2024-01-01-00:00
(integer) 1
```

##### C#

```csharp
        bool res4 = db.StringSetBit("pings:2024-01-01-00:00", 123, true);
        long res5 = db.StringBitCount("pings:2024-01-01-00:00");
        Console.WriteLine(res5);    // >>> 1
```

##### Go

```go
	res4, err := rdb.BitCount(ctx, "pings:2024-01-01-00:00",
		&redis.BitCount{
			Start: 0,
			End:   456,
		}).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res4) // >>> 1
```

##### Java (Asynchronous - Lettuce)

```java
            CompletableFuture<Void> bitcount = async.bitcount("pings:2024-01-01-00:00").thenAccept(res4 -> {
                System.out.println(res4); // >>> 1
            }).toCompletableFuture();
```

##### Java (Reactive - Lettuce)

```java
            Mono<Void> bitcount = reactive.bitcount("pings:2024-01-01-00:00").doOnNext(res4 -> {
                System.out.println(res4); // >>> 1
            }).then();
```

##### Java (Synchronous - Jedis)

```java
        long res4 = jedis.bitcount("pings:2024-01-01-00:00");
        System.out.println(res4); // >>> 1
```

##### JavaScript (Node.js)

```javascript
await client.setBit("pings:2024-01-01-00:00", 123, 1)
const res4 = await client.bitCount("pings:2024-01-01-00:00")
console.log(res4)  // >>> 1
```

##### PHP

```php
        // Ensure the bit is set
        $r->setbit('pings:2024-01-01-00:00', 123, 1);
        $res4 = $r->bitcount('pings:2024-01-01-00:00');
        echo $res4 . PHP_EOL;
        // >>> 1
```

##### Python

```python
r.setbit("pings:2024-01-01-00:00", 123, 1)
res4 = r.bitcount("pings:2024-01-01-00:00")
print(res4)  # >>> 1
```



Ví dụ, hãy tưởng tượng bạn muốn biết chuỗi ngày truy cập liên tiếp dài nhất (longest streak of daily visits) của
các user trên website của bạn. Bạn bắt đầu đếm ngày từ số không, tức là
ngày bạn công khai website, và đặt một bit bằng [`SETBIT`](https://redis.io/docs/latest/commands/setbit) mỗi khi
user truy cập website. Để làm chỉ số bit, bạn chỉ cần lấy unix time
hiện tại, trừ đi offset ban đầu, và chia cho số giây trong một ngày
(thông thường là 3600\*24).

Bằng cách này, với mỗi user bạn có một string nhỏ chứa thông tin
truy cập của từng ngày. Với [`BITCOUNT`](https://redis.io/docs/latest/commands/bitcount), có thể dễ dàng lấy
số ngày mà một user cho trước đã truy cập website, trong khi với
một vài lần gọi [`BITPOS`](https://redis.io/docs/latest/commands/bitpos), hoặc đơn giản là lấy về và phân tích bitmap ở phía client,
có thể dễ dàng tính được chuỗi ngày liên tiếp dài nhất.

### Các phép toán bitwise (Bitwise operations)

Lệnh [`BITOP`](https://redis.io/docs/latest/commands/bitop) thực hiện các phép toán
bitwise trên hai hoặc nhiều key nguồn (source key), và lưu kết quả vào một key đích (destination key).

Các ví dụ bên dưới cho thấy các phép toán có sẵn, sử dụng ba key: `A` (với mẫu bit
`11011000`), `B` (`00011001`), và `C` (`01101100`).

![Bitop setup](https://redis.io/docs/latest/images/dev/bitmap/BitopSetup.svg)

Đánh số các bit từ trái sang phải, bắt đầu từ số không, các lệnh `SETBIT` sau
sẽ tạo ra các bitmap này:

*Chuẩn bị cho các phép toán bitwise: Tạo nhiều bitmap bằng SETBIT để chuẩn bị minh họa các phép toán bitwise*

**Độ khó (Difficulty):** Trung bình (Intermediate)

**Dựa trên (Builds upon):** ping

**Lệnh (Commands):** SETBIT, GET

**Độ phức tạp (Complexity):**
- SETBIT: O(1)
- GET: O(1)

**Có sẵn trong (Available in):** Redis CLI, C#, Go, Java (Asynchronous - Lettuce), Java (Reactive - Lettuce), Java (Synchronous - Jedis), JavaScript (Node.js), PHP, Python

##### Redis CLI

```
> SETBIT A 0 1
(integer) 0
> SETBIT A 1 1
(integer) 0
> SETBIT A 3 1
(integer) 0
> SETBIT A 4 1
(integer) 0
> GET A
"\xd8"
# Hex value: 0xd8 = 0b11011000

> SETBIT B 3 1
(integer) 0
> SETBIT B 4 1
(integer) 0
> SETBIT B 7 1
(integer) 0
> GET B
"\x19"
# Hex value: 0x19 = 0b00011001

> SETBIT C 1 1
(integer) 0
> SETBIT C 2 1
(integer) 0
> SETBIT C 4 1
(integer) 0
> SETBIT C 5 1
(integer) 0
> GET C
"l"
# ASCII "l" = hex 0x6c = 0b01101100
```

##### C#

```csharp

        // Bitwise operations are not supported in NRedisStack.

```

##### Go

```go
	rdb.SetBit(ctx, "A", 0, 1)
	rdb.SetBit(ctx, "A", 1, 1)
	rdb.SetBit(ctx, "A", 3, 1)
	rdb.SetBit(ctx, "A", 4, 1)
	ba, _ := rdb.Get(ctx, "A").Bytes()
	fmt.Printf("%08b\n", ba[0])
	// >>> 11011000

	rdb.SetBit(ctx, "B", 3, 1)
	rdb.SetBit(ctx, "B", 4, 1)
	rdb.SetBit(ctx, "B", 7, 1)
	bb, _ := rdb.Get(ctx, "B").Bytes()
	fmt.Printf("%08b\n", bb[0])
	// >>> 00011001

	rdb.SetBit(ctx, "C", 1, 1)
	rdb.SetBit(ctx, "C", 2, 1)
	rdb.SetBit(ctx, "C", 4, 1)
	rdb.SetBit(ctx, "C", 5, 1)
	bc, _ := rdb.Get(ctx, "C").Bytes()
	fmt.Printf("%08b\n", bc[0])
	// >>> 01101100
```

##### Java (Asynchronous - Lettuce)

```java
            CompletableFuture<Void> setup = async.setbit("A", 0, 1).thenCompose(v -> async.setbit("A", 1, 1))
                    .thenCompose(v -> async.setbit("A", 3, 1)).thenCompose(v -> async.setbit("A", 4, 1))
                    .thenCompose(v -> async.setbit("B", 3, 1)).thenCompose(v -> async.setbit("B", 4, 1))
                    .thenCompose(v -> async.setbit("B", 7, 1)).thenCompose(v -> async.setbit("C", 1, 1))
                    .thenCompose(v -> async.setbit("C", 2, 1)).thenCompose(v -> async.setbit("C", 4, 1))
                    .thenCompose(v -> async.setbit("C", 5, 1)).thenCompose(v -> asyncBytes.get("A".getBytes()))
                    .thenApply(res -> {
                        byte b = (res != null && res.length > 0) ? res[0] : 0;
                        String bitsA = String.format("%8s", Integer.toBinaryString(b & 0xFF)).replace(' ', '0');
                        System.out.println(bitsA); // >>> 11011000
                        return bitsA;
                    }).thenCompose(v -> asyncBytes.get("B".getBytes())).thenApply(res -> {
                        byte b = (res != null && res.length > 0) ? res[0] : 0;
                        String bitsB = String.format("%8s", Integer.toBinaryString(b & 0xFF)).replace(' ', '0');
                        System.out.println(bitsB); // >>> 00011001
                        return bitsB;
                    })
                    // Print C
                    .thenCompose(v -> asyncBytes.get("C".getBytes())).thenAccept(res -> {
                        byte b = (res != null && res.length > 0) ? res[0] : 0;
                        String bitsC = String.format("%8s", Integer.toBinaryString(b & 0xFF)).replace(' ', '0');
                        System.out.println(bitsC); // >>> 01101100
                    }).toCompletableFuture();
```

##### Java (Reactive - Lettuce)

```java
            Mono<Void> setup = reactive.setbit("A", 0, 1).then(reactive.setbit("A", 1, 1)).then(reactive.setbit("A", 3, 1))
                    .then(reactive.setbit("A", 4, 1)).then(reactive.setbit("B", 3, 1)).then(reactive.setbit("B", 4, 1))
                    .then(reactive.setbit("B", 7, 1)).then(reactive.setbit("C", 1, 1)).then(reactive.setbit("C", 2, 1))
                    .then(reactive.setbit("C", 4, 1)).then(reactive.setbit("C", 5, 1)).then(reactiveBytes.get("A".getBytes()))
                    .doOnNext(res -> {
                        byte b = (res != null && res.length > 0) ? res[0] : 0;
                        String bitsA = String.format("%8s", Integer.toBinaryString(b & 0xFF)).replace(' ', '0');
                        System.out.println(bitsA); // >>> 11011000
                    }).then(reactiveBytes.get("B".getBytes())).doOnNext(res -> {
                        byte b = (res != null && res.length > 0) ? res[0] : 0;
                        String bitsB = String.format("%8s", Integer.toBinaryString(b & 0xFF)).replace(' ', '0');
                        System.out.println(bitsB); // >>> 00011001
                    }).then(reactiveBytes.get("C".getBytes())).doOnNext(res -> {
                        byte b = (res != null && res.length > 0) ? res[0] : 0;
                        String bitsC = String.format("%8s", Integer.toBinaryString(b & 0xFF)).replace(' ', '0');
                        System.out.println(bitsC); // >>> 01101100
                    }).then();
```

##### Java (Synchronous - Jedis)

```java
        jedis.setbit("A", 0, true);
        jedis.setbit("A", 1, true);
        jedis.setbit("A", 3, true);
        jedis.setbit("A", 4, true);

        byte[] res5 = jedis.get("A".getBytes());
        System.out.println(String.format("%8s", Integer.toBinaryString(res5[0] & 0xFF)).replace(' ', '0'));
        // >>> 11011000

        jedis.setbit("B", 3, true);
        jedis.setbit("B", 4, true);
        jedis.setbit("B", 7, true);

        byte[] res6 = jedis.get("B".getBytes());
        System.out.println(String.format("%8s", Integer.toBinaryString(res6[0] & 0xFF)).replace(' ', '0'));
        // >>> 00011001

        jedis.setbit("C", 1, true);
        jedis.setbit("C", 2, true);
        jedis.setbit("C", 4, true);
        jedis.setbit("C", 5, true);

        byte[] res7 = jedis.get("C".getBytes());
        System.out.println(String.format("%8s", Integer.toBinaryString(res7[0] & 0xFF)).replace(' ', '0'));
        // >>> 01101100
```

##### JavaScript (Node.js)

```javascript
await client.setBit("A", 0, 1)
await client.setBit("A", 1, 1)
await client.setBit("A", 3, 1)
await client.setBit("A", 4, 1)

const res5 = await client.get("A")
console.log(res5.readUInt8(0).toString(2).padStart(8, '0'))
// >>> 11011000

await client.setBit("B", 3, 1)
await client.setBit("B", 4, 1)
await client.setBit("B", 7, 1)

const res6 = await client.get("B")
console.log(res6.readUInt8(0).toString(2).padStart(8, '0'))
// >>> 00011001

await client.setBit("C", 1, 1)
await client.setBit("C", 2, 1)
await client.setBit("C", 4, 1)
await client.setBit("C", 5, 1)

const res7 = await client.get("C")
console.log(res7.readUInt8(0).toString(2).padStart(8, '0'))
// >>> 01101100
```

##### PHP

```php
        $r->setbit('A', 0, 1);
        $r->setbit('A', 1, 1);
        $r->setbit('A', 3, 1);
        $r->setbit('A', 4, 1);

        $res5 = $r->get('A');
        echo str_pad(decbin(ord($res5)), 8, '0', STR_PAD_LEFT) . PHP_EOL;
        // >>> 11011000

        $r->setbit('B', 3, 1);
        $r->setbit('B', 4, 1);
        $r->setbit('B', 7, 1);

        $res6 = $r->get('B');
        echo str_pad(decbin(ord($res6)), 8, '0', STR_PAD_LEFT) . PHP_EOL;
        // >>> 00011001

        $r->setbit('C', 1, 1);
        $r->setbit('C', 2, 1);
        $r->setbit('C', 4, 1);
        $r->setbit('C', 5, 1);

        $res7 = $r->get('C');
        echo str_pad(decbin(ord($res7)), 8, '0', STR_PAD_LEFT) . PHP_EOL;
        // >>> 01101100
```

##### Python

```python
r.setbit("A", 0, 1)
r.setbit("A", 1, 1)
r.setbit("A", 3, 1)
r.setbit("A", 4, 1)

res5 = r.get("A")
print("{:08b}".format(int.from_bytes(res5, "big")))
# >>> 11011000

r.setbit("B", 3, 1)
r.setbit("B", 4, 1)
r.setbit("B", 7, 1)

res6 = r.get("B")
print("{:08b}".format(int.from_bytes(res6, "big")))
# >>> 00011001

r.setbit("C", 1, 1)
r.setbit("C", 2, 1)
r.setbit("C", 4, 1)
r.setbit("C", 5, 1)

res7 = r.get("C")
print("{:08b}".format(int.from_bytes(res7, "big")))
# >>> 01101100
```



#### `AND`

Đặt một bit trong key đích thành 1 chỉ khi nó được bật trong tất cả các key nguồn.

![Bitop AND](https://redis.io/docs/latest/images/dev/bitmap/BitopAnd.svg)

*Phép AND: Dùng BITOP AND để tìm các bit được bật trong tất cả các bitmap nguồn khi bạn cần tìm các bit chung giữa nhiều tập hợp*

**Độ khó (Difficulty):** Trung bình (Intermediate)

**Dựa trên (Builds upon):** bitop_setup

**Lệnh (Commands):** BITOP, GET

**Độ phức tạp (Complexity):**
- BITOP: O(N)
- GET: O(1)

**Có sẵn trong (Available in):** Redis CLI, C#, Go, Java (Asynchronous - Lettuce), Java (Reactive - Lettuce), Java (Synchronous - Jedis), JavaScript (Node.js), PHP, Python

##### Redis CLI

```
> BITOP AND R A B C
(integer) 1
> GET R
"\b"
# ASCII "\b" (backspace) = hex 0x08 = 0b00001000
```

##### C#

```csharp

        // Bitwise operations are not supported in NRedisStack.

```

##### Go

```go
	rdb.BitOpAnd(ctx, "R", "A", "B", "C")
	br, _ := rdb.Get(ctx, "R").Bytes()
	fmt.Printf("%08b\n", br[0])
	// >>> 00001000
```

##### Java (Asynchronous - Lettuce)

```java
            CompletableFuture<Void> andOp = async.bitopAnd("R", "A", "B", "C")
                    .thenCompose(len -> asyncBytes.get("R".getBytes())).thenAccept(res -> {
                        byte b = (res != null && res.length > 0) ? res[0] : 0;
                        String bits = String.format("%8s", Integer.toBinaryString(b & 0xFF)).replace(' ', '0');
                        System.out.println(bits); // >>> 00001000
                    }).toCompletableFuture();
```

##### Java (Reactive - Lettuce)

```java
            Mono<Void> andOp = reactive.bitopAnd("R", "A", "B", "C").then(reactiveBytes.get("R".getBytes())).doOnNext(res -> {
                byte b = (res != null && res.length > 0) ? res[0] : 0;
                String bits = String.format("%8s", Integer.toBinaryString(b & 0xFF)).replace(' ', '0');
                System.out.println(bits); // >>> 00001000
            }).then();
```

##### Java (Synchronous - Jedis)

```java
        jedis.bitop(BitOP.AND, "R", "A", "B", "C");
        byte[] res8 = jedis.get("R".getBytes());
        System.out.println(String.format("%8s", Integer.toBinaryString(res8[0] & 0xFF)).replace(' ', '0'));
        // >>> 00001000
```

##### JavaScript (Node.js)

```javascript
await client.bitOp("AND", "R", ["A", "B", "C"])
const res8 = await client.get("R")
console.log(res8.readUInt8(0).toString(2).padStart(8, '0'))
// >>> 00001000
```

##### PHP

```php
        $r->bitop('AND', 'R', 'A', 'B', 'C');
        $res8 = $r->get('R');
        echo str_pad(decbin(ord($res8)), 8, '0', STR_PAD_LEFT) . PHP_EOL;
        // >>> 00001000
```

##### Python

```python
r.bitop("AND", "R", "A", "B", "C")
res8 = r.get("R")
print("{:08b}".format(int.from_bytes(res8, "big")))
# >>> 00001000
```



#### `OR`
Đặt một bit trong key đích thành 1 nếu nó được bật trong ít nhất một key nguồn.

![Bitop OR](https://redis.io/docs/latest/images/dev/bitmap/BitopOr.svg)

*Phép OR: Dùng BITOP OR để tìm các bit được bật trong ít nhất một bitmap nguồn khi bạn cần kết hợp nhiều tập hợp*

**Độ khó (Difficulty):** Trung bình (Intermediate)

**Dựa trên (Builds upon):** bitop_setup

**Lệnh (Commands):** BITOP, GET

**Độ phức tạp (Complexity):**
- BITOP: O(N)
- GET: O(1)

**Có sẵn trong (Available in):** Redis CLI, C#, Go, Java (Asynchronous - Lettuce), Java (Reactive - Lettuce), Java (Synchronous - Jedis), JavaScript (Node.js), PHP, Python

##### Redis CLI

```
> BITOP OR R A B C
(integer) 1
> GET R
"\xfd"
# Hex value: 0xfd = 0b11111101
```

##### C#

```csharp

        // Bitwise operations are not supported in NRedisStack.

```

##### Go

```go
	rdb.BitOpOr(ctx, "R", "A", "B", "C")
	br, _ = rdb.Get(ctx, "R").Bytes()
	fmt.Printf("%08b\n", br[0])
	// >>> 11111101
```

##### Java (Asynchronous - Lettuce)

```java
            CompletableFuture<Void> orOp = async.bitopOr("R", "A", "B", "C").thenCompose(len -> asyncBytes.get("R".getBytes()))
                    .thenAccept(res -> {
                        byte b = (res != null && res.length > 0) ? res[0] : 0;
                        String bits = String.format("%8s", Integer.toBinaryString(b & 0xFF)).replace(' ', '0');
                        System.out.println(bits); // >>> 11111101
                    }).toCompletableFuture();
```

##### Java (Reactive - Lettuce)

```java
            Mono<Void> orOp = reactive.bitopOr("R", "A", "B", "C").then(reactiveBytes.get("R".getBytes())).doOnNext(res -> {
                byte b = (res != null && res.length > 0) ? res[0] : 0;
                String bits = String.format("%8s", Integer.toBinaryString(b & 0xFF)).replace(' ', '0');
                System.out.println(bits); // >>> 11111101
            }).then();
```

##### Java (Synchronous - Jedis)

```java
        jedis.bitop(BitOP.OR, "R", "A", "B", "C");
        byte[] res9 = jedis.get("R".getBytes());
        System.out.println(String.format("%8s", Integer.toBinaryString(res9[0] & 0xFF)).replace(' ', '0'));
        // >>> 11111101
```

##### JavaScript (Node.js)

```javascript
await client.bitOp("OR", "R", ["A", "B", "C"])
const res9 = await client.get("R")
console.log(res9.readUInt8(0).toString(2).padStart(8, '0'))
// >>> 11111101
```

##### PHP

```php
        $r->bitop('OR', 'R', 'A', 'B', 'C');
        $res9 = $r->get('R');
        echo str_pad(decbin(ord($res9)), 8, '0', STR_PAD_LEFT) . PHP_EOL;
        // >>> 11111101
```

##### Python

```python
r.bitop("OR", "R", "A", "B", "C")
res9 = r.get("R")
print("{:08b}".format(int.from_bytes(res9, "big")))
# >>> 11111101
```



#### `XOR`

Với hai key nguồn, đặt một bit trong key đích thành 1 nếu giá trị của bit đó
khác nhau ở hai key. Với ba key nguồn trở lên, kết quả XOR của hai key đầu tiên
sẽ được XOR tiếp với key kế tiếp, và cứ thế tiếp tục.

![Bitop XOR](https://redis.io/docs/latest/images/dev/bitmap/BitopXor.svg)

*Phép XOR: Dùng BITOP XOR để tìm các bit khác nhau giữa các bitmap khi bạn cần xác định sự khác biệt*

**Độ khó (Difficulty):** Trung bình (Intermediate)

**Dựa trên (Builds upon):** bitop_setup

**Lệnh (Commands):** BITOP, GET

**Độ phức tạp (Complexity):**
- BITOP: O(N)
- GET: O(1)

**Có sẵn trong (Available in):** Redis CLI, C#, Go, Java (Asynchronous - Lettuce), Java (Reactive - Lettuce), Java (Synchronous - Jedis), JavaScript (Node.js), PHP, Python

##### Redis CLI

```
> BITOP XOR R A B
(integer) 1
> GET R
"\xc1"
# Hex value: 0xc1 = 0b11000001
```

##### C#

```csharp

        // Bitwise operations are not supported in NRedisStack.

```

##### Go

```go
	rdb.BitOpXor(ctx, "R", "A", "B")
	br, _ = rdb.Get(ctx, "R").Bytes()
	fmt.Printf("%08b\n", br[0])
	// >>> 11000001
```

##### Java (Asynchronous - Lettuce)

```java
            CompletableFuture<Void> xorOp = async.bitopXor("R", "A", "B").thenCompose(len -> asyncBytes.get("R".getBytes()))
                    .thenAccept(res -> {
                        byte b = (res != null && res.length > 0) ? res[0] : 0;
                        String bits = String.format("%8s", Integer.toBinaryString(b & 0xFF)).replace(' ', '0');
                        System.out.println(bits); // >>> 11000001
                    }).toCompletableFuture();
```

##### Java (Reactive - Lettuce)

```java
            Mono<Void> xorOp = reactive.bitopXor("R", "A", "B").then(reactiveBytes.get("R".getBytes())).doOnNext(res -> {
                byte b = (res != null && res.length > 0) ? res[0] : 0;
                String bits = String.format("%8s", Integer.toBinaryString(b & 0xFF)).replace(' ', '0');
                System.out.println(bits); // >>> 11000001
            }).then();
```

##### Java (Synchronous - Jedis)

```java
        jedis.bitop(BitOP.XOR, "R", "A", "B");
        byte[] res10 = jedis.get("R".getBytes());
        System.out.println(String.format("%8s", Integer.toBinaryString(res10[0] & 0xFF)).replace(' ', '0'));
        // >>> 11000001
```

##### JavaScript (Node.js)

```javascript
await client.bitOp("XOR", "R", ["A", "B"]) // XOR uses two keys here
const res10 = await client.get("R")
console.log(res10.readUInt8(0).toString(2).padStart(8, '0'))
// >>> 11000001
```

##### PHP

```php
        $r->bitop('XOR', 'R', 'A', 'B');
        $res10 = $r->get('R');
        echo str_pad(decbin(ord($res10)), 8, '0', STR_PAD_LEFT) . PHP_EOL;
        // >>> 11000001
```

##### Python

```python
r.bitop("XOR", "R", "A", "B")
res10 = r.get("R")
print("{:08b}".format(int.from_bytes(res10, "big")))
# >>> 11000001
```



#### `NOT`

Đặt một bit trong key đích thành 1 nếu nó không được bật trong key nguồn (đây
là toán tử một ngôi (unary operator) duy nhất).

![Bitop NOT](https://redis.io/docs/latest/images/dev/bitmap/BitopNot.svg)

*Phép NOT: Dùng BITOP NOT để đảo ngược tất cả các bit trong một bitmap khi bạn cần phủ định một tập hợp*

**Độ khó (Difficulty):** Trung bình (Intermediate)

**Dựa trên (Builds upon):** bitop_setup

**Lệnh (Commands):** BITOP, GET

**Độ phức tạp (Complexity):**
- BITOP: O(N)
- GET: O(1)

**Có sẵn trong (Available in):** Redis CLI, C#, Go, Java (Asynchronous - Lettuce), Java (Reactive - Lettuce), Java (Synchronous - Jedis), JavaScript (Node.js), PHP, Python

##### Redis CLI

```
> BITOP NOT R A
(integer) 1
> GET R
"'"
# ASCII "'" (single quote) = hex 0x27 = 0b00100111
```

##### C#

```csharp

        // Bitwise operations are not supported in NRedisStack.

```

##### Go

```go
	rdb.BitOpNot(ctx, "R", "A")
	br, _ = rdb.Get(ctx, "R").Bytes()
	fmt.Printf("%08b\n", br[0])
	// >>> 00100111
```

##### Java (Asynchronous - Lettuce)

```java
            CompletableFuture<Void> notOp = async.bitopNot("R", "A").thenCompose(len -> asyncBytes.get("R".getBytes()))
                    .thenAccept(res -> {
                        byte b = (res != null && res.length > 0) ? res[0] : 0;
                        String bits = String.format("%8s", Integer.toBinaryString(b & 0xFF)).replace(' ', '0');
                        System.out.println(bits); // >>> 00100111
                    }).toCompletableFuture();
```

##### Java (Reactive - Lettuce)

```java
            Mono<Void> notOp = reactive.bitopNot("R", "A").then(reactiveBytes.get("R".getBytes())).doOnNext(res -> {
                byte b = (res != null && res.length > 0) ? res[0] : 0;
                String bits = String.format("%8s", Integer.toBinaryString(b & 0xFF)).replace(' ', '0');
                System.out.println(bits); // >>> 00100111
            }).then();
```

##### Java (Synchronous - Jedis)

```java
        jedis.bitop(BitOP.NOT, "R", "A");
        byte[] res11 = jedis.get("R".getBytes());
        System.out.println(String.format("%8s", Integer.toBinaryString(res11[0] & 0xFF)).replace(' ', '0'));
        // >>> 00100111
```

##### JavaScript (Node.js)

```javascript
await client.bitOp("NOT", "R", "A")
const res11 = await client.get("R")
console.log(res11.readUInt8(0).toString(2).padStart(8, '0'))
// >>> 00100111
```

##### PHP

```php
        $r->bitop('NOT', 'R', 'A');
        $res11 = $r->get('R');
        echo str_pad(decbin(ord($res11)), 8, '0', STR_PAD_LEFT) . PHP_EOL;
        // >>> 00100111
```

##### Python

```python
r.bitop("NOT", "R", "A")
res11 = r.get("R")
print("{:08b}".format(int.from_bytes(res11, "big")))
# >>> 00100111
```



#### `DIFF`

Đặt một bit trong key đích thành 1 nếu nó được bật trong key nguồn đầu tiên, nhưng không được bật trong bất kỳ
key nguồn nào khác.

![Bitop DIFF](https://redis.io/docs/latest/images/dev/bitmap/BitopDiff.svg)

*Phép DIFF: Dùng BITOP DIFF để tìm các bit được bật trong bitmap đầu tiên nhưng không có trong các bitmap khác khi bạn cần phép hiệu tập hợp (set difference)*

**Độ khó (Difficulty):** Nâng cao (Advanced)

**Dựa trên (Builds upon):** bitop_setup

**Lệnh (Commands):** BITOP, GET

**Độ phức tạp (Complexity):**
- BITOP: O(N)
- GET: O(1)

**Có sẵn trong (Available in):** Redis CLI, C#, Go, Java (Asynchronous - Lettuce), Java (Reactive - Lettuce), Java (Synchronous - Jedis), JavaScript (Node.js), PHP, Python

##### Redis CLI

```
> BITOP DIFF R A B C
(integer) 1
> GET R
"\x80"
# Hex value: 0x80 = 0b10000000
```

##### C#

```csharp

        // Bitwise operations are not supported in NRedisStack.

```

##### Go

```go
	rdb.BitOpDiff(ctx, "R", "A", "B", "C")
	br, _ = rdb.Get(ctx, "R").Bytes()
	fmt.Printf("%08b\n", br[0])
	// >>> 10000000
```

##### Java (Asynchronous - Lettuce)

```java
            CompletableFuture<Void> diffOp = async.bitopDiff("R", "A", "B", "C")
                    .thenCompose(len -> asyncBytes.get("R".getBytes())).thenAccept(res -> {
                        byte b = (res != null && res.length > 0) ? res[0] : 0;
                        String bits = String.format("%8s", Integer.toBinaryString(b & 0xFF)).replace(' ', '0');
                        System.out.println(bits); // >>> 10000000
                    }).toCompletableFuture();
```

##### Java (Reactive - Lettuce)

```java
            Mono<Void> diffOp = reactive.bitopDiff("R", "A", "B", "C").then(reactiveBytes.get("R".getBytes())).doOnNext(res -> {
                byte b = (res != null && res.length > 0) ? res[0] : 0;
                String bits = String.format("%8s", Integer.toBinaryString(b & 0xFF)).replace(' ', '0');
                System.out.println(bits); // >>> 10000000
            }).then();
```

##### Java (Synchronous - Jedis)

```java
        jedis.bitop(BitOP.DIFF, "R", "A", "B", "C");
        byte[] res12 = jedis.get("R".getBytes());
        System.out.println(String.format("%8s", Integer.toBinaryString(res12[0] & 0xFF)).replace(' ', '0'));
        // >>> 10000000
```

##### JavaScript (Node.js)

```javascript
await client.bitOp("DIFF", "R", ["A", "B", "C"])
const res12 = await client.get("R")
console.log(res12.readUInt8(0).toString(2).padStart(8, '0'))
// >>> 10000000
```

##### PHP

```php
        $r->bitop('DIFF', 'R', 'A', 'B', 'C');
        $res12 = $r->get('R');
        echo str_pad(decbin(ord($res12)), 8, '0', STR_PAD_LEFT) . PHP_EOL;
        // >>> 10000000
```

##### Python

```python
r.bitop("DIFF", "R", "A", "B", "C")
res12 = r.get("R")
print("{:08b}".format(int.from_bytes(res12, "big")))
# >>> 10000000
```



#### `DIFF1`

Đặt một bit trong key đích thành 1 nếu nó không được bật trong key nguồn đầu tiên,
nhưng được bật trong ít nhất một key nguồn khác.

![Bitop DIFF1](https://redis.io/docs/latest/images/dev/bitmap/BitopDiff1.svg)

*Phép DIFF1: Dùng BITOP DIFF1 để tìm các bit không có trong bitmap đầu tiên nhưng có trong ít nhất một bitmap khác khi bạn cần phép hiệu ngược (inverse difference)*

**Độ khó (Difficulty):** Nâng cao (Advanced)

**Dựa trên (Builds upon):** bitop_setup

**Lệnh (Commands):** BITOP, GET

**Độ phức tạp (Complexity):**
- BITOP: O(N)
- GET: O(1)

**Có sẵn trong (Available in):** Redis CLI, C#, Go, Java (Asynchronous - Lettuce), Java (Reactive - Lettuce), Java (Synchronous - Jedis), JavaScript (Node.js), PHP, Python

##### Redis CLI

```
> BITOP DIFF1 R A B C
(integer) 1
> GET R
"%"
# ASCII "%" (percent) = hex 0x25 = 0b00100101
```

##### C#

```csharp

        // Bitwise operations are not supported in NRedisStack.

```

##### Go

```go
	rdb.BitOpDiff1(ctx, "R", "A", "B", "C")
	br, _ = rdb.Get(ctx, "R").Bytes()
	fmt.Printf("%08b\n", br[0])
	// >>> 00100101
```

##### Java (Asynchronous - Lettuce)

```java
            CompletableFuture<Void> diff1Op = async.bitopDiff1("R", "A", "B", "C")
                    .thenCompose(len -> asyncBytes.get("R".getBytes())).thenAccept(res -> {
                        byte b = (res != null && res.length > 0) ? res[0] : 0;
                        String bits = String.format("%8s", Integer.toBinaryString(b & 0xFF)).replace(' ', '0');
                        System.out.println(bits); // >>> 00100101
                    }).toCompletableFuture();
```

##### Java (Reactive - Lettuce)

```java
            Mono<Void> diff1Op = reactive.bitopDiff1("R", "A", "B", "C").then(reactiveBytes.get("R".getBytes()))
                    .doOnNext(res -> {
                        byte b = (res != null && res.length > 0) ? res[0] : 0;
                        String bits = String.format("%8s", Integer.toBinaryString(b & 0xFF)).replace(' ', '0');
                        System.out.println(bits); // >>> 00100101
                    }).then();
```

##### Java (Synchronous - Jedis)

```java
        jedis.bitop(BitOP.DIFF1, "R", "A", "B", "C");
        byte[] res13 = jedis.get("R".getBytes());
        System.out.println(String.format("%8s", Integer.toBinaryString(res13[0] & 0xFF)).replace(' ', '0'));
        // >>> 00100101
```

##### JavaScript (Node.js)

```javascript
await client.bitOp("DIFF1", "R", ["A", "B", "C"])
const res13 = await client.get("R")
console.log(res13.readUInt8(0).toString(2).padStart(8, '0'))
// >>> 00100101
```

##### PHP

```php
        $r->bitop('DIFF1', 'R', 'A', 'B', 'C');
        $res13 = $r->get('R');
        echo str_pad(decbin(ord($res13)), 8, '0', STR_PAD_LEFT) . PHP_EOL;
        // >>> 00100101
```

##### Python

```python
r.bitop("DIFF1", "R", "A", "B", "C")
res13 = r.get("R")
print("{:08b}".format(int.from_bytes(res13, "big")))
# >>> 00100101
```



#### `ANDOR`

Đặt một bit trong key đích thành 1 nếu nó được bật trong key nguồn đầu tiên và cũng được bật trong ít nhất một key nguồn khác.

![Bitop ANDOR](https://redis.io/docs/latest/images/dev/bitmap/BitopAndOr.svg)

*Phép ANDOR: Dùng BITOP ANDOR để tìm các bit có trong bitmap đầu tiên và ít nhất một bitmap khác khi bạn cần phép giao với hợp (intersection with union)*

**Độ khó (Difficulty):** Nâng cao (Advanced)

**Dựa trên (Builds upon):** bitop_setup

**Lệnh (Commands):** BITOP, GET

**Độ phức tạp (Complexity):**
- BITOP: O(N)
- GET: O(1)

**Có sẵn trong (Available in):** Redis CLI, C#, Go, Java (Asynchronous - Lettuce), Java (Reactive - Lettuce), Java (Synchronous - Jedis), JavaScript (Node.js), PHP, Python

##### Redis CLI

```
> BITOP ANDOR R A B C
(integer) 1
> GET R
"X"
# ASCII "X" = hex 0x58 = 0b01011000
```

##### C#

```csharp

        // Bitwise operations are not supported in NRedisStack.

```

##### Go

```go
	rdb.BitOpAndOr(ctx, "R", "A", "B", "C")
	br, _ = rdb.Get(ctx, "R").Bytes()
	fmt.Printf("%08b\n", br[0])
	// >>> 01011000
```

##### Java (Asynchronous - Lettuce)

```java
            CompletableFuture<Void> andorOp = async.bitopAndor("R", "A", "B", "C")
                    .thenCompose(len -> asyncBytes.get("R".getBytes())).thenAccept(res -> {
                        byte b = (res != null && res.length > 0) ? res[0] : 0;
                        String bits = String.format("%8s", Integer.toBinaryString(b & 0xFF)).replace(' ', '0');
                        System.out.println(bits); // >>> 01011000
                    }).toCompletableFuture();
```

##### Java (Reactive - Lettuce)

```java
            Mono<Void> andorOp = reactive.bitopAndor("R", "A", "B", "C").then(reactiveBytes.get("R".getBytes()))
                    .doOnNext(res -> {
                        byte b = (res != null && res.length > 0) ? res[0] : 0;
                        String bits = String.format("%8s", Integer.toBinaryString(b & 0xFF)).replace(' ', '0');
                        System.out.println(bits); // >>> 01011000
                    }).then();
```

##### Java (Synchronous - Jedis)

```java
        jedis.bitop(BitOP.ANDOR, "R", "A", "B", "C");
        byte[] res14 = jedis.get("R".getBytes());
        System.out.println(String.format("%8s", Integer.toBinaryString(res14[0] & 0xFF)).replace(' ', '0'));
        // >>> 01011000
```

##### JavaScript (Node.js)

```javascript
await client.bitOp("ANDOR", "R", ["A", "B", "C"])
const res14 = await client.get("R")
console.log(res14.readUInt8(0).toString(2).padStart(8, '0'))
// >>> 01011000
```

##### PHP

```php
        $r->bitop('ANDOR', 'R', 'A', 'B', 'C');
        $res14 = $r->get('R');
        echo str_pad(decbin(ord($res14)), 8, '0', STR_PAD_LEFT) . PHP_EOL;
        // >>> 01011000
```

##### Python

```python
r.bitop("ANDOR", "R", "A", "B", "C")
res14 = r.get("R")
print("{:08b}".format(int.from_bytes(res14, "big")))
# >>> 01011000
```



#### `ONE`

Đặt một bit trong key đích thành 1 nếu nó được bật trong đúng một key nguồn.

![Bitop ONE](https://redis.io/docs/latest/images/dev/bitmap/BitopOne.svg)

*Phép ONE: Dùng BITOP ONE để tìm các bit được bật trong đúng một bitmap khi bạn cần tư cách thành viên độc quyền (exclusive membership)*

**Độ khó (Difficulty):** Nâng cao (Advanced)

**Dựa trên (Builds upon):** bitop_setup

**Lệnh (Commands):** BITOP, GET

**Độ phức tạp (Complexity):**
- BITOP: O(N)
- GET: O(1)

**Có sẵn trong (Available in):** Redis CLI, C#, Go, Java (Asynchronous - Lettuce), Java (Reactive - Lettuce), Java (Synchronous - Jedis), JavaScript (Node.js), PHP, Python

##### Redis CLI

```
> BITOP ONE R A B C
(integer) 1
> GET R
"\xa5"
# Hex value: 0xa5 = 0b10100101
```

##### C#

```csharp

        // Bitwise operations are not supported in NRedisStack.

```

##### Go

```go
	rdb.BitOpOne(ctx, "R", "A", "B", "C")
	br, _ = rdb.Get(ctx, "R").Bytes()
	fmt.Printf("%08b\n", br[0])
	// >>> 10100101
```

##### Java (Asynchronous - Lettuce)

```java
            CompletableFuture<Void> oneOp = async.bitopOne("R", "A", "B", "C")
                    .thenCompose(len -> asyncBytes.get("R".getBytes())).thenAccept(res -> {
                        byte b = (res != null && res.length > 0) ? res[0] : 0;
                        String bits = String.format("%8s", Integer.toBinaryString(b & 0xFF)).replace(' ', '0');
                        System.out.println(bits); // >>> 10100101
                    }).toCompletableFuture();
```

##### Java (Reactive - Lettuce)

```java
            Mono<Void> oneOp = reactive.bitopOne("R", "A", "B", "C").then(reactiveBytes.get("R".getBytes())).doOnNext(res -> {
                byte b = (res != null && res.length > 0) ? res[0] : 0;
                String bits = String.format("%8s", Integer.toBinaryString(b & 0xFF)).replace(' ', '0');
                System.out.println(bits); // >>> 10100101
            }).then();
```

##### Java (Synchronous - Jedis)

```java
        jedis.bitop(BitOP.ONE, "R", "A", "B", "C");
        byte[] res15 = jedis.get("R".getBytes());
        System.out.println(String.format("%8s", Integer.toBinaryString(res15[0] & 0xFF)).replace(' ', '0'));
        // >>> 10100101
```

##### JavaScript (Node.js)

```javascript
await client.bitOp("ONE", "R", ["A", "B", "C"])
const res15 = await client.get("R")
console.log(res15.readUInt8(0).toString(2).padStart(8, '0'))
// >>> 10100101
```

##### PHP

```php
        $r->bitop('ONE', 'R', 'A', 'B', 'C');
        $res15 = $r->get('R');
        echo str_pad(decbin(ord($res15)), 8, '0', STR_PAD_LEFT) . PHP_EOL;
        // >>> 10100101
```

##### Python

```python
r.bitop("ONE", "R", "A", "B", "C")
res15 = r.get("R")
print("{:08b}".format(int.from_bytes(res15, "big")))
# >>> 10100101
```



## Chia bitmap thành nhiều key (Split bitmaps into multiple keys)

Bitmap rất dễ chia thành nhiều key, ví dụ để
sharding tập dữ liệu và vì nói chung tốt hơn là nên
tránh làm việc với các key khổng lồ. Để chia một bitmap ra nhiều key khác nhau
thay vì đặt tất cả các bit vào một key, một chiến lược đơn giản là
lưu M bit cho mỗi key, lấy tên key bằng `bit-number/M` và
bit thứ N cần trỏ tới bên trong key bằng `bit-number MOD M`.

## Hiệu năng (Performance)

[`SETBIT`](https://redis.io/docs/latest/commands/setbit) và [`GETBIT`](https://redis.io/docs/latest/commands/getbit) là O(1).
[`BITOP`](https://redis.io/docs/latest/commands/bitop) là O(n), với _n_ là độ dài của string dài nhất trong phép so sánh.

## Tìm hiểu thêm (Learn more)

* [Redis Bitmaps Explained](https://www.youtube.com/watch?v=oj8LdJQjhJo) hướng dẫn bạn cách dùng bitmap để khám phá bản đồ (map exploration) trong một game online.
* [Redis University's RU101](https://university.redis.com/courses/ru101/) trình bày chi tiết về Redis bitmap.

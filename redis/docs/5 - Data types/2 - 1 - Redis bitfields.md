# Redis bitfields

> Nguồn: <https://redis.io/docs/latest/develop/data-types/strings/bitfields/>
> Giới thiệu về Redis bitfields

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



Redis bitfield cho phép bạn đặt (set), tăng (increment), và lấy (get) các giá trị số nguyên có độ dài bit tùy ý.
Ví dụ, bạn có thể thao tác trên mọi thứ, từ số nguyên không dấu (unsigned) 1-bit đến số nguyên có dấu (signed) 63-bit.

Các giá trị này được lưu bằng Redis string được mã hóa nhị phân (binary-encoded).
Bitfield hỗ trợ các thao tác đọc, ghi và tăng nguyên tử (atomic), khiến chúng trở thành một lựa chọn tốt để quản lý các bộ đếm (counter) và các giá trị số tương tự.

## Ví dụ (Example)

Giả sử bạn muốn duy trì hai chỉ số (metric) cho nhiều chiếc xe đạp khác nhau: giá hiện tại và số lượng chủ sở hữu theo thời gian. Bạn có thể biểu diễn các bộ đếm này bằng một bitfield rộng 32-bit cho mỗi chiếc xe.

* Xe 1 ban đầu có giá 1,000 (bộ đếm ở offset 0) và chưa từng có chủ sở hữu nào. Sau khi được bán, giờ nó được coi là xe đã qua sử dụng và giá lập tức giảm xuống để phản ánh tình trạng mới, và giờ nó có một chủ sở hữu (offset 1). Sau một thời gian khá lâu, chiếc xe trở thành xe cổ điển (classic). Chủ sở hữu ban đầu bán nó có lãi, nên giá tăng lên và số lượng chủ sở hữu cũng tăng theo. Cuối cùng, bạn có thể xem giá hiện tại và số lượng chủ sở hữu của chiếc xe.

*Các thao tác bitfield: Dùng BITFIELD để đặt, tăng, và lấy một cách nguyên tử các giá trị số nguyên có độ dài bit tùy ý khi bạn cần quản lý nhiều bộ đếm một cách hiệu quả*

**Độ khó (Difficulty):** Trung bình (Intermediate)

**Lệnh (Commands):** BITFIELD

**Độ phức tạp (Complexity):**
- BITFIELD: O(1)

**Có sẵn trong (Available in):** Redis CLI, Go, Java (Synchronous - Jedis), JavaScript (Node.js), Python

##### Redis CLI

```
> BITFIELD bike:1:stats SET u32 #0 1000
1) (integer) 0
> BITFIELD bike:1:stats INCRBY u32 #0 -50 INCRBY u32 #1 1
1) (integer) 950
2) (integer) 1
> BITFIELD bike:1:stats INCRBY u32 #0 500 INCRBY u32 #1 1
1) (integer) 1450
2) (integer) 2
> BITFIELD bike:1:stats GET u32 #0 GET u32 #1
1) (integer) 1450
2) (integer) 2
```

##### Go

```go
	res1, err := rdb.BitField(ctx, "bike:1:stats",
		"set", "u32", "#0", "1000",
	).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res1) // >>> [0]

	res2, err := rdb.BitField(ctx,
		"bike:1:stats",
		"incrby", "u32", "#0", "-50",
		"incrby", "u32", "#1", "1",
	).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res2) // >>> [950 1]

	res3, err := rdb.BitField(ctx,
		"bike:1:stats",
		"incrby", "u32", "#0", "500",
		"incrby", "u32", "#1", "1",
	).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res3) // >>> [1450 2]

	res4, err := rdb.BitField(ctx, "bike:1:stats",
		"get", "u32", "#0",
		"get", "u32", "#1",
	).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res4) // >>> [1450 2]
```

##### Java (Synchronous - Jedis)

```java
        List<Long> res1 = jedis.bitfield("bike:1:stats", "SET", "u32", "#0", "1000");
        System.out.println(res1);   // >>> [0]

        List<Long> res2 = jedis.bitfield("bike:1:stats", "INCRBY", "u32", "#0", "-50", "INCRBY", "u32", "#1", "1");
        System.out.println(res2);   // >>> [950, 1]

        List<Long> res3 = jedis.bitfield("bike:1:stats", "INCRBY", "u32", "#0", "500", "INCRBY", "u32", "#1", "1");
        System.out.println(res3);   // >>> [1450, 2]

        List<Long> res4 = jedis.bitfield("bike:1:stats", "GET", "u32", "#0", "GET", "u32", "#1");
        System.out.println(res4);   // >>> [1450, 2]
```

##### JavaScript (Node.js)

```javascript
let res1 = await client.bitField("bike:1:stats", [{
  operation: 'SET',
  encoding: 'u32',
  offset: '#0',
  value: 1000
}]);
console.log(res1);  // >>> [0]

let res2 = await client.bitField('bike:1:stats', [
  {
    operation: 'INCRBY',
    encoding: 'u32',
    offset: '#0',
    increment: -50
  },
  {
    operation: 'INCRBY',
    encoding: 'u32',
    offset: '#1',
    increment: 1
  }
]);
console.log(res2);  // >>> [950, 1]

let res3 = await client.bitField('bike:1:stats', [
  {
    operation: 'INCRBY',
    encoding: 'u32',
    offset: '#0',
    increment: 500
  },
  {
    operation: 'INCRBY',
    encoding: 'u32',
    offset: '#1',
    increment: 1
  }
]);
console.log(res3);  // >>> [1450, 2]

let res4 = await client.bitField('bike:1:stats', [
  {
    operation: 'GET',
    encoding: 'u32',
    offset: '#0'
  },
  {
    operation: 'GET',
    encoding: 'u32',
    offset: '#1'
  }
]);
console.log(res4);  // >>> [1450, 2]
```

##### Python

```python
bf = r.bitfield("bike:1:stats")
res1 = bf.set("u32", "#0", 1000).execute()
print(res1)  # >>> [0]

res2 = bf.incrby("u32", "#0", -50).incrby("u32", "#1", 1).execute()
print(res2)  # >>> [950, 1]

res3 = bf.incrby("u32", "#0", 500).incrby("u32", "#1", 1).execute()
print(res3)  # >>> [1450, 2]

res4 = bf.get("u32", "#0").get("u32", "#1").execute()
print(res4)  # >>> [1450, 2]
```




## Hiệu năng (Performance)

[`BITFIELD`](https://redis.io/docs/latest/commands/bitfield) là O(n), với _n_ là số lượng bộ đếm được truy cập.

# Redis Strings

> Nguồn: <https://redis.io/docs/latest/develop/data-types/strings/>
> Giới thiệu về Redis string

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


### Tóm tắt các lệnh string (String command summary)

**26 lệnh trong nhóm này:**

[Xem tất cả các lệnh string](https://redis.io/commands/?group=string)

| Lệnh | Tóm tắt | Độ phức tạp | Từ phiên bản |
|---------|---------|------------|-------|
| [APPEND](https://redis.io/commands/append/) | Nối thêm (append) một string vào value của một key. Tạo key nếu nó chưa tồn tại. | O(1). The amortized time complexity is O(1) ass... | 2.0.0 |
| [DECR](https://redis.io/commands/decr/) | Giảm value số nguyên của một key đi một. Dùng 0 làm giá trị khởi tạo nếu key chưa tồn tại. | O(1) | 1.0.0 |
| [DECRBY](https://redis.io/commands/decrby/) | Trừ một số khỏi value số nguyên của một key. Dùng 0 làm giá trị khởi tạo nếu key chưa tồn tại. | O(1) | 1.0.0 |
| [DELEX](https://redis.io/commands/delex/) | Xóa có điều kiện key được chỉ định dựa trên việc so sánh value hoặc digest. | O(1) for IFEQ/IFNE, O(N) for IFDEQ/IFDNE where ... | 8.4.0 |
| [DIGEST](https://redis.io/commands/digest/) | Trả về hash XXH3 của một value kiểu string. | O(N) với N là độ dài của value kiểu string. | 8.4.0 |
| [GET](https://redis.io/commands/get/) | Trả về value kiểu string của một key. | O(1) | 1.0.0 |
| [GETDEL](https://redis.io/commands/getdel/) | Trả về value kiểu string của một key sau khi xóa key đó. | O(1) | 6.2.0 |
| [GETEX](https://redis.io/commands/getex/) | Trả về value kiểu string của một key sau khi đặt thời gian hết hạn cho nó. | O(1) | 6.2.0 |
| [GETRANGE](https://redis.io/commands/getrange/) | Trả về một chuỗi con (substring) của string được lưu tại một key. | O(N) where N is the length of the returned stri... | 2.4.0 |
| [GETSET](https://redis.io/commands/getset/) | Trả về value string trước đó của một key sau khi đặt nó thành value mới. | O(1) | 1.0.0 |

#### Tóm tắt các lệnh string (String command summary) (part 2)

| Lệnh | Tóm tắt | Độ phức tạp | Từ phiên bản |
|---------|---------|------------|-------|
| [INCR](https://redis.io/commands/incr/) | Tăng value số nguyên của một key thêm một. Dùng 0 làm giá trị khởi tạo nếu key chưa tồn tại. | O(1) | 1.0.0 |
| [INCRBY](https://redis.io/commands/incrby/) | Tăng value số nguyên của một key thêm một số. Dùng 0 làm giá trị khởi tạo nếu key chưa tồn tại. | O(1) | 1.0.0 |
| [INCRBYFLOAT](https://redis.io/commands/incrbyfloat/) | Tăng value số thực dấu phẩy động của một key thêm một số. Dùng 0 làm giá trị khởi tạo nếu key chưa tồn tại. | O(1) | 2.6.0 |
| [INCREX](https://redis.io/commands/increx/) | Tăng value dạng số của một key thêm một số và đặt thời gian hết hạn cho nó. Dùng 0 làm giá trị khởi tạo nếu key chưa tồn tại. | O(1) | 8.8.0 |
| [LCS](https://redis.io/commands/lcs/) | Tìm chuỗi con chung dài nhất (longest common substring). | O(N*M) where N and M are the lengths of s1 and ... | 7.0.0 |
| [MGET](https://redis.io/commands/mget/) | Trả về một cách nguyên tử (atomically) các value string của một hoặc nhiều key. | O(N) với N là số key cần truy xuất. | 1.0.0 |
| [MSET](https://redis.io/commands/mset/) | Tạo hoặc sửa đổi một cách nguyên tử các value string của một hoặc nhiều key. | O(N) với N là số key cần đặt. | 1.0.1 |
| [MSETEX](https://redis.io/commands/msetex/) | Đặt một cách nguyên tử nhiều key string với cùng một thời gian hết hạn trong một thao tác duy nhất. Hỗ trợ phân tích đối số linh hoạt, trong đó các flag điều kiện và hết hạn có thể xuất hiện theo bất kỳ thứ tự nào. | O(N) với N là số key cần đặt. | 8.4.0 |
| [MSETNX](https://redis.io/commands/msetnx/) | Sửa đổi một cách nguyên tử các value string của một hoặc nhiều key chỉ khi tất cả các key đều chưa tồn tại. | O(N) với N là số key cần đặt. | 1.0.1 |
| [PSETEX](https://redis.io/commands/psetex/) | Đặt cả value string và thời gian hết hạn tính bằng mili giây của một key. Key được tạo nếu nó chưa tồn tại. | O(1) | 2.6.0 |

#### Tóm tắt các lệnh string (String command summary) (part 3)

| Lệnh | Tóm tắt | Độ phức tạp | Từ phiên bản |
|---------|---------|------------|-------|
| [SET](https://redis.io/commands/set/) | Đặt value string của một key, bỏ qua kiểu của nó. Key được tạo nếu nó chưa tồn tại. | O(1) | 1.0.0 |
| [SETEX](https://redis.io/commands/setex/) | Đặt value string và thời gian hết hạn của một key. Tạo key nếu nó chưa tồn tại. | O(1) | 2.0.0 |
| [SETNX](https://redis.io/commands/setnx/) | Đặt value string của một key chỉ khi key chưa tồn tại. | O(1) | 1.0.0 |
| [SETRANGE](https://redis.io/commands/setrange/) | Ghi đè một phần của value string bằng một string khác tại một offset. Tạo key nếu nó chưa tồn tại. | O(1), not counting the time taken to copy the n... | 2.2.0 |
| [STRLEN](https://redis.io/commands/strlen/) | Trả về độ dài của một value string. | O(1) | 2.2.0 |
| [SUBSTR](https://redis.io/commands/substr/) | Trả về một chuỗi con từ một value string. | O(N) where N is the length of the returned stri... | 1.0.0 |



Redis string lưu các chuỗi byte, bao gồm văn bản, object đã được serialize, và mảng nhị phân.
Do đó, string là kiểu value đơn giản nhất mà bạn có thể gắn với một Redis key.
Chúng thường được dùng cho caching, nhưng chúng cũng hỗ trợ thêm các chức năng cho phép bạn hiện thực bộ đếm (counter) và thực hiện các phép toán bitwise.

Vì Redis key là string, nên khi chúng ta cũng dùng kiểu string làm value,
chúng ta đang ánh xạ một string tới một string khác. Kiểu dữ liệu string hữu ích
cho nhiều trường hợp sử dụng, như cache các đoạn HTML hoặc các trang.

*Nền tảng: Đặt và truy xuất value string bằng SET và GET (ghi đè value đã có)*

**Lệnh (Commands):** SET, GET

**Độ phức tạp (Complexity):**
- SET: O(1)
- GET: O(1)

**Có sẵn trong (Available in):** Redis CLI, C#, Go, Java (Asynchronous - Lettuce), Java (Reactive - Lettuce), Java (Synchronous - Jedis), JavaScript (Node.js), PHP, Python, Ruby, Rust (Asynchronous), Rust (Synchronous)

##### Redis CLI

```
> SET bike:1 Deimos
OK
> GET bike:1
"Deimos"
```

##### C#

```csharp
        var res1 = db.StringSet("bike:1", "Deimos");
        Console.WriteLine(res1); // true
        var res2 = db.StringGet("bike:1");
        Console.WriteLine(res2); // Deimos
```

##### Go

```go
	res1, err := rdb.Set(ctx, "bike:1", "Deimos", 0).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res1) // >>> OK

	res2, err := rdb.Get(ctx, "bike:1").Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res2) // >>> Deimos
```

##### Java (Asynchronous - Lettuce)

```java
            CompletableFuture<Void> setAndGet = asyncCommands.set("bike:1", "Deimos").thenCompose(v -> {
                System.out.println(v); // >>> OK
                return asyncCommands.get("bike:1");
            })
                    .thenAccept(System.out::println) // >>> Deimos
                    .toCompletableFuture();
```

##### Java (Reactive - Lettuce)

```java
            Mono<Void> setAndGet = reactiveCommands.set("bike:1", "Deimos").doOnNext(v -> {
                System.out.println(v); // OK
            }).flatMap(v -> reactiveCommands.get("bike:1")).doOnNext(res -> {
                System.out.println(res); // Deimos
            }).then();
```

##### Java (Synchronous - Jedis)

```java
      String res1 = jedis.set("bike:1", "Deimos");
      System.out.println(res1); // OK
      String res2 = jedis.get("bike:1");
      System.out.println(res2); // Deimos
```

##### JavaScript (Node.js)

```javascript
const res1 = await client.set("bike:1", "Deimos");
console.log(res1);  // OK
const res2 = await client.get("bike:1");
console.log(res2);  // Deimos
```

##### PHP

```php
        $res1 = $r->set('bike:1', 'Deimos');
        echo "$res1" . PHP_EOL;
        // >>> OK

        $res2 = $r->get('bike:1');
        echo "$res2" . PHP_EOL;
        // >>> Deimos
```

##### Python

```python
res1 = r.set("bike:1", "Deimos")
print(res1)  # True
res2 = r.get("bike:1")
print(res2)  # Deimos
```

##### Ruby

```ruby
res1 = r.set('bike:1', 'Deimos')
puts res1 # >>> OK

res2 = r.get('bike:1')
puts res2 # >>> Deimos
```

##### Rust (Asynchronous)

```rust
        if let Ok(res) = r.set("bike:1", "Deimos").await {
            let res: String = res;
            println!("{res}");    // >>> OK
        }

        match r.get("bike:1").await {
            Ok(res) => {
                let res: String = res;
                println!("{res}");   // >>> Deimos
            },
            Err(e) => {
                println!("Error getting foo: {e}");
                return;
            }
        };
```

##### Rust (Synchronous)

```rust
        if let Ok(res) = r.set("bike:1", "Deimos") {
            let res: String = res;
            println!("{res}");    // >>> OK
        }

        match r.get("bike:1") {
            Ok(res) => {
                let res: String = res;
                println!("{res}");   // >>> Deimos
            },
            Err(e) => {
                println!("Error getting bike:1: {e}");
                return;
            }
        };
```



Như bạn thấy, dùng các lệnh [`SET`](https://redis.io/docs/latest/commands/set) và [`GET`](https://redis.io/docs/latest/commands/get) là cách chúng ta đặt
và truy xuất một value string. Lưu ý rằng [`SET`](https://redis.io/docs/latest/commands/set) sẽ thay thế bất kỳ value nào
đã được lưu trong key, trong trường hợp key đã tồn tại, ngay cả khi
key đó gắn với một value không phải string. Vì vậy [`SET`](https://redis.io/docs/latest/commands/set) thực hiện một phép gán.

Value có thể là string (bao gồm cả dữ liệu nhị phân) thuộc mọi loại, ví dụ bạn
có thể lưu một ảnh jpeg bên trong một value. Một value không thể lớn hơn 512 MB.

Lệnh [`SET`](https://redis.io/docs/latest/commands/set) có những tùy chọn thú vị được cung cấp dưới dạng các đối số
bổ sung. Ví dụ, tôi có thể yêu cầu [`SET`](https://redis.io/docs/latest/commands/set) thất bại nếu key đã tồn tại,
hoặc ngược lại, chỉ thành công nếu key đã tồn tại:

*Các thao tác SET có điều kiện: Dùng tùy chọn NX và XX để đặt một key chỉ khi nó chưa tồn tại, hoặc chỉ khi nó đã tồn tại*

**Độ khó (Difficulty):** Trung bình (Intermediate)

**Dựa trên (Builds upon):** set_get

**Lệnh (Commands):** SET

**Độ phức tạp (Complexity):**
- SET: O(1)

**Có sẵn trong (Available in):** Redis CLI, C#, Go, Java (Asynchronous - Lettuce), Java (Reactive - Lettuce), Java (Synchronous - Jedis), JavaScript (Node.js), PHP, Python, Ruby, Rust (Asynchronous), Rust (Synchronous)

##### Redis CLI

```
# Recreate the bike:1 key so this example runs on its own.
> SET bike:1 Deimos
OK
> SET bike:1 bike NX
(nil)
> SET bike:1 bike XX
OK
```

##### C#

```csharp
        // Recreate the bike:1 key so this example runs on its own.
        db.StringSet("bike:1", "Deimos");

        var res3 = db.StringSet("bike:1", "bike", when: When.NotExists);
        Console.WriteLine(res3); // false
        Console.WriteLine(db.StringGet("bike:1"));
        var res4 = db.StringSet("bike:1", "bike", when: When.Exists);
        Console.WriteLine(res4); // true
```

##### Go

```go
	// Recreate the bike:1 key so this example runs on its own.
	rdb.Set(ctx, "bike:1", "Deimos", 0)

	res3, err := rdb.SetNX(ctx, "bike:1", "bike", 0).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res3) // >>> false

	res4, err := rdb.Get(ctx, "bike:1").Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res4) // >>> Deimos

	res5, err := rdb.SetXX(ctx, "bike:1", "bike", 0).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res5) // >>> OK
```

##### Java (Asynchronous - Lettuce)

```java
            // Recreate the bike:1 key so this example runs on its own.
            CompletableFuture<Void> setnx = asyncCommands.set("bike:1", "Deimos")
                    .thenCompose(setup -> asyncCommands.set("bike:1", "bike", SetArgs.Builder.nx())).thenCompose(v -> {
                        System.out.println(v); // >>> null (because key already exists)
                        return asyncCommands.get("bike:1");
                    })
                    .thenAccept(System.out::println) // >>> Deimos (value is unchanged)
                    .toCompletableFuture();
            setnx.join();

            // set the value to "bike" if it already exists
            CompletableFuture<Void> setxx = asyncCommands.set("bike:1", "bike", SetArgs.Builder.xx())
                    .thenAccept(System.out::println) // >>> OK
                    .toCompletableFuture();
            setxx.join();
```

##### Java (Reactive - Lettuce)

```java
            // Recreate the bike:1 key so this example runs on its own.
            Mono<Void> setnx = reactiveCommands.set("bike:1", "Deimos")
                    // A failed NX returns an empty Mono, so check for an element.
                    .flatMap(setup -> reactiveCommands.set("bike:1", "bike", SetArgs.Builder.nx()).hasElement()).doOnNext(v -> {
                        System.out.println(v); // false (because key already exists)
                    }).flatMap(v -> reactiveCommands.get("bike:1")).doOnNext(res -> {
                        System.out.println(res); // Deimos (value is unchanged)
                    }).then();

            Mono<Void> setxx = reactiveCommands.set("bike:1", "bike", SetArgs.Builder.xx()).doOnNext(res -> {
                System.out.println(res); // OK
            }).then();
```

##### Java (Synchronous - Jedis)

```java
      // Recreate the bike:1 key so this example runs on its own.
      jedis.set("bike:1", "Deimos");

      String res3 = jedis.set("bike:1", "bike", SetParams.setParams().nx());
      System.out.println(res3); // null (because key already exists)
      System.out.println(jedis.get("bike:1")); // Deimos (value is unchanged)
      String res4 = jedis.set("bike:1", "bike", SetParams.setParams().xx()); // set the value to "bike" if it
      // already
      // exists
      System.out.println(res4); // OK
```

##### JavaScript (Node.js)

```javascript
// Recreate the bike:1 key so this example runs on its own.
await client.set("bike:1", "Deimos");

const res3 = await client.set("bike:1", "bike", {'NX': true});
console.log(res3);  // null
console.log(await client.get("bike:1"));  // Deimos
const res4 = await client.set("bike:1", "bike", {'XX': true});
console.log(res4);  // OK
```

##### PHP

```php
        // Recreate the bike:1 key so this example runs on its own.
        $r->set('bike:1', 'Deimos');

        $res3 = $r->set('bike:1', 'bike', 'nx');
        echo var_export($res3, true) . PHP_EOL;
        // >>> NULL
        
        echo $r->get('bike:1') . PHP_EOL;
        // >>> Deimos

        $res4 = $r->set('bike:1', 'bike', 'xx');
        echo "$res4" . PHP_EOL;
        // >>> OK
```

##### Python

```python
# Recreate the bike:1 key so this example runs on its own.
r.set("bike:1", "Deimos")

res3 = r.set("bike:1", "bike", nx=True)
print(res3)  # None
print(r.get("bike:1"))  # Deimos
res4 = r.set("bike:1", "bike", xx=True)
print(res4)  # True
```

##### Ruby

```ruby
# Recreate the bike:1 key so this example runs on its own.
r.set('bike:1', 'Deimos')

res3 = r.set('bike:1', 'bike', nx: true)
puts res3 # >>> false

puts r.get('bike:1') # >>> Deimos

res4 = r.set('bike:1', 'bike', xx: true)
puts res4 # >>> true
```

##### Rust (Asynchronous)

```rust
        // Recreate the bike:1 key so this example runs on its own.
        let _: () = r.set("bike:1", "Deimos").await.expect("Failed to set");

        if let Ok(res) = r.set_options("bike:1", "bike", redis::SetOptions::default().conditional_set(ExistenceCheck::NX)).await {
            let res: bool = res;
            println!("{res}");    // >>> false
        }

        match r.get("bike:1").await {
            Ok(res) => {
                let res: String = res;
                println!("{res}");   // >>> Deimos
            },
            Err(e) => {
                println!("Error getting foo: {e}");
                return;
            }
        };

        if let Ok(res) = r.set_options("bike:1", "bike", redis::SetOptions::default().conditional_set(ExistenceCheck::XX)).await {
            let res: String = res;
            println!("{res}");    // >>> OK
        }

        match r.get("bike:1").await {
            Ok(res) => {
                let res: String = res;
                println!("{res}");   // >>> bike
            },
            Err(e) => {
                println!("Error getting foo: {e}");
                return;
            }
        };
```

##### Rust (Synchronous)

```rust
        // Recreate the bike:1 key so this example runs on its own.
        let _: () = r.set("bike:1", "Deimos").expect("Failed to set");

        if let Ok(res) = r.set_options("bike:1", "bike", redis::SetOptions::default().conditional_set(ExistenceCheck::NX)) {
            let res: bool = res;
            println!("{res}");    // >>> false
        }

        match r.get("bike:1") {
            Ok(res) => {
                let res: String = res;
                println!("{res}");   // >>> Deimos
            },
            Err(e) => {
                println!("Error getting bike:1: {e}");
                return;
            }
        };

        if let Ok(res) = r.set_options("bike:1", "bike", redis::SetOptions::default().conditional_set(ExistenceCheck::XX)) {
            let res: String = res;
            println!("{res}");    // >>> OK
        }
        
        match r.get("bike:1") {
            Ok(res) => {
                let res: String = res;
                println!("{res}");   // >>> bike
            },
            Err(e) => {
                println!("Error getting bike:1: {e}");
                return;
            }
        };
```



Bắt đầu từ Redis 8.4, [`SET`](https://redis.io/docs/latest/commands/set) cũng có thể kiểm tra value hiện tại của key trước khi ghi.
Với tùy chọn `IFEQ`, `SET` chỉ thành công nếu value hiện tại bằng với value bạn truyền vào.
Điều này cho phép một client cập nhật một value mà nó đã đọc trước đó mà không ghi đè lên thay đổi do một client khác thực hiện trong lúc đó, một pattern được gọi là compare-and-swap (CAS).
Lệnh [`DELEX`](https://redis.io/docs/latest/commands/delex) áp dụng cùng phép kiểm tra đó cho việc xóa (compare-and-delete):

*Compare-and-swap và compare-and-delete: Dùng tùy chọn IFEQ với SET và DELEX để cập nhật hoặc xóa một key chỉ khi value của nó chưa thay đổi kể từ lúc bạn đọc*

**Độ khó (Difficulty):** Trung bình (Intermediate)

**Dựa trên (Builds upon):** setnx_xx

**Lệnh (Commands):** SET, GET, DELEX

**Độ phức tạp (Complexity):**
- SET: O(1)
- GET: O(1)
- DELEX: O(1)

**Có sẵn trong (Available in):** Redis CLI, C#, Go, Java (Asynchronous - Lettuce), Java (Reactive - Lettuce), Java (Synchronous - Jedis), JavaScript (Node.js), PHP, Python, Rust (Asynchronous), Rust (Synchronous)

##### Redis CLI

```
> SET bike:1 Deimos
OK
> SET bike:1 Ares IFEQ Deimos
OK
> SET bike:1 Zeus IFEQ Deimos
(nil)
> GET bike:1
"Ares"
> DELEX bike:1 IFEQ Deimos
(integer) 0
> DELEX bike:1 IFEQ Ares
(integer) 1
```

##### C#

```csharp
        db.StringSet("bike:1", "Deimos");

        var res9 = db.StringSet("bike:1", "Ares", when: ValueCondition.Equal("Deimos"));
        Console.WriteLine(res9); // True
        var res10 = db.StringSet("bike:1", "Zeus", when: ValueCondition.Equal("Deimos"));
        Console.WriteLine(res10); // False
        Console.WriteLine(db.StringGet("bike:1")); // Ares

        var res11 = db.StringDelete("bike:1", ValueCondition.Equal("Deimos"));
        Console.WriteLine(res11); // False
        var res12 = db.StringDelete("bike:1", ValueCondition.Equal("Ares"));
        Console.WriteLine(res12); // True
```

##### Go

```go
	rdb.Set(ctx, "bike:1", "Deimos", 0)

	res11, err := rdb.SetIFEQ(ctx, "bike:1", "Ares", "Deimos", 0).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res11) // >>> OK

	// The value no longer matches, so SetIFEQ returns redis.Nil.
	_, err = rdb.SetIFEQ(ctx, "bike:1", "Zeus", "Deimos", 0).Result()

	if err != nil && err != redis.Nil {
		panic(err)
	}

	fmt.Println(err == redis.Nil) // >>> true

	res12, err := rdb.Get(ctx, "bike:1").Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res12) // >>> Ares

	res13, err := rdb.DelExArgs(ctx, "bike:1", redis.DelExArgs{
		Mode: "IFEQ", MatchValue: "Deimos",
	}).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res13) // >>> 0

	res14, err := rdb.DelExArgs(ctx, "bike:1", redis.DelExArgs{
		Mode: "IFEQ", MatchValue: "Ares",
	}).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res14) // >>> 1
```

##### Java (Asynchronous - Lettuce)

```java
            CompletableFuture<Void> casCad = asyncCommands.set("bike:1", "Deimos")
                    .thenCompose(setup -> asyncCommands.set("bike:1", "Ares",
                            SetArgs.Builder.compareCondition(CompareCondition.valueEq("Deimos"))))
                    .thenCompose(v -> {
                        System.out.println(v); // >>> OK
                        return asyncCommands.set("bike:1", "Zeus",
                                SetArgs.Builder.compareCondition(CompareCondition.valueEq("Deimos")));
                    }).thenCompose(v -> {
                        System.out.println(v); // >>> null
                        return asyncCommands.get("bike:1");
                    }).thenCompose(v -> {
                        System.out.println(v); // >>> Ares
                        return asyncCommands.delex("bike:1", CompareCondition.valueEq("Deimos"));
                    }).thenCompose(v -> {
                        System.out.println(v); // >>> 0
                        return asyncCommands.delex("bike:1", CompareCondition.valueEq("Ares"));
                    })
                    .thenAccept(System.out::println) // >>> 1
                    .toCompletableFuture();
```

##### Java (Reactive - Lettuce)

```java
            Mono<Void> casCad = reactiveCommands.set("bike:1", "Deimos")
                    .flatMap(setup -> reactiveCommands.set("bike:1", "Ares",
                            SetArgs.Builder.compareCondition(CompareCondition.valueEq("Deimos"))))
                    .doOnNext(res -> {
                        System.out.println(res); // OK
                    })
                    // A failed condition returns an empty Mono, so check for an element.
                    .flatMap(v -> reactiveCommands.set("bike:1", "Zeus",
                            SetArgs.Builder.compareCondition(CompareCondition.valueEq("Deimos"))).hasElement())
                    .doOnNext(res -> {
                        System.out.println(res); // false
                    }).flatMap(v -> reactiveCommands.get("bike:1")).doOnNext(res -> {
                        System.out.println(res); // Ares
                    }).flatMap(v -> reactiveCommands.delex("bike:1", CompareCondition.valueEq("Deimos")))
                    .doOnNext(res -> {
                        System.out.println(res); // 0
                    }).flatMap(v -> reactiveCommands.delex("bike:1", CompareCondition.valueEq("Ares")))
                    .doOnNext(res -> {
                        System.out.println(res); // 1
                    }).then();
```

##### Java (Synchronous - Jedis)

```java
      jedis.set("bike:1", "Deimos");

      String res9 = jedis.set("bike:1", "Ares",
          SetParams.setParams().condition(CompareCondition.valueEq("Deimos")));
      System.out.println(res9); // OK
      String res10 = jedis.set("bike:1", "Zeus",
          SetParams.setParams().condition(CompareCondition.valueEq("Deimos")));
      System.out.println(res10); // null
      System.out.println(jedis.get("bike:1")); // Ares

      long res11 = jedis.delex("bike:1", CompareCondition.valueEq("Deimos"));
      System.out.println(res11); // 0
      long res12 = jedis.delex("bike:1", CompareCondition.valueEq("Ares"));
      System.out.println(res12); // 1
```

##### JavaScript (Node.js)

```javascript
await client.set("bike:1", "Deimos");

const res9 = await client.set("bike:1", "Ares", {
  condition: 'IFEQ', matchValue: "Deimos"
});
console.log(res9);  // OK
const res10 = await client.set("bike:1", "Zeus", {
  condition: 'IFEQ', matchValue: "Deimos"
});
console.log(res10);  // null
console.log(await client.get("bike:1"));  // Ares

const res11 = await client.delEx("bike:1", {
  condition: 'IFEQ', matchValue: "Deimos"
});
console.log(res11);  // 0
const res12 = await client.delEx("bike:1", {
  condition: 'IFEQ', matchValue: "Ares"
});
console.log(res12);  // 1
```

##### PHP

```php
        $r->set('bike:1', 'Deimos');

        $res9 = $r->set('bike:1', 'Ares', null, null, 'IFEQ', 'Deimos');
        echo "$res9" . PHP_EOL;
        // >>> OK

        $res10 = $r->set('bike:1', 'Zeus', null, null, 'IFEQ', 'Deimos');
        echo var_export($res10, true) . PHP_EOL;
        // >>> NULL

        echo $r->get('bike:1') . PHP_EOL;
        // >>> Ares

        $res11 = $r->delex('bike:1', 'IFEQ', 'Deimos');
        echo "$res11" . PHP_EOL;
        // >>> 0

        $res12 = $r->delex('bike:1', 'IFEQ', 'Ares');
        echo "$res12" . PHP_EOL;
        // >>> 1
```

##### Python

```python
r.set("bike:1", "Deimos")

res9 = r.set("bike:1", "Ares", ifeq="Deimos")
print(res9)  # True
res10 = r.set("bike:1", "Zeus", ifeq="Deimos")
print(res10)  # None
print(r.get("bike:1"))  # Ares

res11 = r.delex("bike:1", ifeq="Deimos")
print(res11)  # 0
res12 = r.delex("bike:1", ifeq="Ares")
print(res12)  # 1
```

##### Rust (Asynchronous)

```rust
        let _: () = r.set("bike:1", "Deimos").await.expect("Failed to set");

        let res: Option<String> = r
            .set_options("bike:1", "Ares", redis::SetOptions::default().value_comparison(ValueComparison::ifeq("Deimos")))
            .await.expect("Failed to set");
        println!("{res:?}");    // >>> Some("OK")

        let res: Option<String> = r
            .set_options("bike:1", "Zeus", redis::SetOptions::default().value_comparison(ValueComparison::ifeq("Deimos")))
            .await.expect("Failed to set");
        println!("{res:?}");    // >>> None

        let res: String = r.get("bike:1").await.expect("Failed to get");
        println!("{res}");   // >>> Ares

        let res: usize = r.del_ex("bike:1", ValueComparison::ifeq("Deimos")).await.expect("Failed to delete");
        println!("{res}");    // >>> 0

        let res: usize = r.del_ex("bike:1", ValueComparison::ifeq("Ares")).await.expect("Failed to delete");
        println!("{res}");    // >>> 1
```

##### Rust (Synchronous)

```rust
        let _: () = r.set("bike:1", "Deimos").expect("Failed to set");

        let res: Option<String> = r
            .set_options("bike:1", "Ares", redis::SetOptions::default().value_comparison(ValueComparison::ifeq("Deimos")))
            .expect("Failed to set");
        println!("{res:?}");    // >>> Some("OK")

        let res: Option<String> = r
            .set_options("bike:1", "Zeus", redis::SetOptions::default().value_comparison(ValueComparison::ifeq("Deimos")))
            .expect("Failed to set");
        println!("{res:?}");    // >>> None

        let res: String = r.get("bike:1").expect("Failed to get");
        println!("{res}");   // >>> Ares

        let res: usize = r.del_ex("bike:1", ValueComparison::ifeq("Deimos")).expect("Failed to delete");
        println!("{res}");    // >>> 0

        let res: usize = r.del_ex("bike:1", ValueComparison::ifeq("Ares")).expect("Failed to delete");
        println!("{res}");    // >>> 1
```



Tùy chọn `IFNE` đảo ngược phép kiểm tra, còn các tùy chọn `IFDEQ` và `IFDNE` so sánh hash digest của value thay vì chính value đó.
Dùng [`DIGEST`](https://redis.io/docs/latest/commands/digest) để lấy digest của value của một key.

Có một số lệnh khác để thao tác trên string. Ví dụ,
lệnh [`GETSET`](https://redis.io/docs/latest/commands/getset) đặt một key thành value mới, và trả về value cũ làm
kết quả. Bạn có thể dùng lệnh này, ví dụ, nếu bạn có một
hệ thống tăng một Redis key bằng [`INCR`](https://redis.io/docs/latest/commands/incr)
mỗi khi website của bạn có một lượt truy cập mới. Bạn có thể muốn thu thập
thông tin này mỗi giờ một lần, mà không làm mất một lần tăng nào.
Bạn có thể [`GETSET`](https://redis.io/docs/latest/commands/getset) key đó, gán cho nó value mới là "0" và đọc lại
value cũ.

Khả năng đặt hoặc truy xuất value của nhiều key trong một lệnh duy nhất
cũng hữu ích để giảm độ trễ. Vì lý do này mà có
các lệnh [`MSET`](https://redis.io/docs/latest/commands/mset) và [`MGET`](https://redis.io/docs/latest/commands/mget):

*Đặt và truy xuất nhiều value bằng MSET và MGET khi bạn cần giảm số lần round trip tới server*

**Dựa trên (Builds upon):** set_get

**Lệnh (Commands):** MSET, MGET

**Độ phức tạp (Complexity):**
- MSET: O(N)
- MGET: O(N)

**Có sẵn trong (Available in):** Redis CLI, C#, Go, Java (Asynchronous - Lettuce), Java (Reactive - Lettuce), Java (Synchronous - Jedis), JavaScript (Node.js), PHP, Python, Ruby, Rust (Asynchronous), Rust (Synchronous)

##### Redis CLI

```
> MSET bike:1 "Deimos" bike:2 "Ares" bike:3 "Vanth"
OK
> MGET bike:1 bike:2 bike:3
1) "Deimos"
2) "Ares"
3) "Vanth"
```

##### C#

```csharp
        var res5 = db.StringSet([
            new ("bike:1", "Deimos"), new("bike:2", "Ares"), new("bike:3", "Vanth")
        ]);
        Console.WriteLine(res5);
        var res6 = db.StringGet(["bike:1", "bike:2", "bike:3"]);
        Console.WriteLine(string.Join(", ", res6));
```

##### Go

```go
	res6, err := rdb.MSet(ctx, "bike:1", "Deimos", "bike:2", "Ares", "bike:3", "Vanth").Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res6) // >>> OK

	res7, err := rdb.MGet(ctx, "bike:1", "bike:2", "bike:3").Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res7) // >>> [Deimos Ares Vanth]
```

##### Java (Asynchronous - Lettuce)

```java
            Map<String, String> bikeMap = new HashMap<>();
            bikeMap.put("bike:1", "Deimos");
            bikeMap.put("bike:2", "Ares");
            bikeMap.put("bike:3", "Vanth");

            CompletableFuture<Void> mset = asyncCommands.mset(bikeMap).thenCompose(v -> {
                System.out.println(v); // >>> OK
                return asyncCommands.mget("bike:1", "bike:2", "bike:3");
            })
                    .thenAccept(System.out::println)
                    // >>> [KeyValue[bike:1, Deimos], KeyValue[bike:2, Ares], KeyValue[bike:3,
                    // Vanth]]
                    .toCompletableFuture();
```

##### Java (Reactive - Lettuce)

```java
            Map<String, String> bikeMap = new HashMap<>();
            bikeMap.put("bike:1", "Deimos");
            bikeMap.put("bike:2", "Ares");
            bikeMap.put("bike:3", "Vanth");

            Mono<Void> mset = reactiveCommands.mset(bikeMap).doOnNext(System.out::println) // OK
                    .flatMap(v -> reactiveCommands.mget("bike:1", "bike:2", "bike:3").collectList()).doOnNext(res -> {
                        System.out.println(res); // [KeyValue[bike:1, Deimos], KeyValue[bike:2, Ares], KeyValue[bike:3, Vanth]]
                    }).then();
```

##### Java (Synchronous - Jedis)

```java
      String res5 = jedis.mset("bike:1", "Deimos", "bike:2", "Ares", "bike:3", "Vanth");
      System.out.println(res5); // OK
      List<String> res6 = jedis.mget("bike:1", "bike:2", "bike:3");
      System.out.println(res6); // [Deimos, Ares, Vanth]
```

##### JavaScript (Node.js)

```javascript
const res5 = await client.mSet([
  ["bike:1", "Deimos"],
  ["bike:2", "Ares"],
  ["bike:3", "Vanth"]
]);

console.log(res5);  // OK
const res6 = await client.mGet(["bike:1", "bike:2", "bike:3"]);
console.log(res6);  // ['Deimos', 'Ares', 'Vanth']
```

##### PHP

```php
        $res5 = $r->mset([
            'bike:1' => 'Deimos', 'bike:2' => 'Ares', 'bike:3' => 'Vanth'
        ]);
        echo "$res5" . PHP_EOL;
        // >>> OK

        $res6 = $r->mget(['bike:1', 'bike:2', 'bike:3']);
        echo json_encode($res6) . PHP_EOL;
        // >>> ["Deimos","Ares","Vanth"]
```

##### Python

```python
res5 = r.mset({"bike:1": "Deimos", "bike:2": "Ares", "bike:3": "Vanth"})
print(res5)  # True
res6 = r.mget(["bike:1", "bike:2", "bike:3"])
print(res6)  # ['Deimos', 'Ares', 'Vanth']
```

##### Ruby

```ruby
res5 = r.mset('bike:1', 'Deimos', 'bike:2', 'Ares', 'bike:3', 'Vanth')
puts res5 # >>> OK

res6 = r.mget('bike:1', 'bike:2', 'bike:3')
puts res6.inspect # >>> ["Deimos", "Ares", "Vanth"]
```

##### Rust (Asynchronous)

```rust
        if let Ok(res) = r.mset(&[("bike:1", "Deimos"), ("bike:2", "Ares"), ("bike:3", "Vanth")]).await {
            let res: String = res;
            println!("{res}");    // >>> OK
        }

        match r.mget(&["bike:1", "bike:2", "bike:3"]).await {
            Ok(res) => {
                let res: Vec<String> = res;
                println!("{res:?}");   // >>> ["Deimos", "Ares", "Vanth"]
            },
            Err(e) => {
                println!("Error getting foo: {e}");
                return;
            }
        };
```

##### Rust (Synchronous)

```rust
        if let Ok(res) = r.mset(&[("bike:1", "Deimos"), ("bike:2", "Ares"), ("bike:3", "Vanth")]) {
            let res: String = res;
            println!("{res}");    // >>> OK
        }

        match r.mget(&["bike:1", "bike:2", "bike:3"]) {
            Ok(res) => {
                let res: Vec<String> = res;
                println!("{res:?}");   // >>> ["Deimos", "Ares", "Vanth"]
            },
            Err(e) => {
                println!("Error getting values: {e}");
                return;
            }
        };
```



Khi dùng [`MGET`](https://redis.io/docs/latest/commands/mget), Redis trả về một mảng các value.

## String dùng làm bộ đếm (Strings as counters)

Mặc dù string là các value cơ bản của Redis, vẫn có những thao tác thú vị
mà bạn có thể thực hiện với chúng. Ví dụ, một trong số đó là phép tăng nguyên tử (atomic increment):

*Bộ đếm nguyên tử (atomic counter): Tăng value string bằng INCR và INCRBY khi bạn cần các thao tác thread-safe (khởi tạo bằng 0 nếu key chưa tồn tại)*

**Dựa trên (Builds upon):** set_get

**Lệnh (Commands):** SET, INCR, INCRBY

**Độ phức tạp (Complexity):**
- SET: O(1)
- INCR: O(1)
- INCRBY: O(1)

**Có sẵn trong (Available in):** Redis CLI, C#, Go, Java (Asynchronous - Lettuce), Java (Reactive - Lettuce), Java (Synchronous - Jedis), JavaScript (Node.js), PHP, Python, Ruby, Rust (Asynchronous), Rust (Synchronous)

##### Redis CLI

```
> SET total_crashes 0
OK
> INCR total_crashes
(integer) 1
> INCRBY total_crashes 10
(integer) 11
```

##### C#

```csharp
        db.StringSet("total_crashes", 0);
        var res7 = db.StringIncrement("total_crashes");
        Console.WriteLine(res7); // 1
        var res8 = db.StringIncrement("total_crashes", 10);
        Console.WriteLine(res8);
```

##### Go

```go
	res8, err := rdb.Set(ctx, "total_crashes", "0", 0).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res8) // >>> OK

	res9, err := rdb.Incr(ctx, "total_crashes").Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res9) // >>> 1

	res10, err := rdb.IncrBy(ctx, "total_crashes", 10).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res10) // >>> 11
```

##### Java (Asynchronous - Lettuce)

```java
            CompletableFuture<Void> incrby = asyncCommands.set("total_crashes", "0")
                    .thenCompose(v -> asyncCommands.incr("total_crashes")).thenCompose(v -> {
                        System.out.println(v); // >>> 1
                        return asyncCommands.incrby("total_crashes", 10);
                    })
                    .thenAccept(System.out::println) // >>> 11
                    .toCompletableFuture();
```

##### Java (Reactive - Lettuce)

```java
            Mono<Void> incrby = reactiveCommands.set("total_crashes", "0").flatMap(v -> reactiveCommands.incr("total_crashes"))
                    .doOnNext(v -> {
                        System.out.println(v); // 1
                    }).flatMap(v -> reactiveCommands.incrby("total_crashes", 10)).doOnNext(res -> {
                        System.out.println(res); // 11
                    }).then();
```

##### Java (Synchronous - Jedis)

```java
      jedis.set("total_crashes", "0");
      Long res7 = jedis.incr("total_crashes");
      System.out.println(res7); // 1
      Long res8 = jedis.incrBy("total_crashes", 10);
      System.out.println(res8); // 11
```

##### JavaScript (Node.js)

```javascript
await client.set("total_crashes", 0);
const res7 = await client.incr("total_crashes");
console.log(res7); // 1
const res8 = await client.incrBy("total_crashes", 10);
console.log(res8); // 11
```

##### PHP

```php
        $r->set('total_crashes', 0);
        $res7 = $r->incr('total_crashes');
        echo "$res7" . PHP_EOL;
        // >>> 1

        $res8 = $r->incrby('total_crashes', 10);
        echo "$res8" . PHP_EOL;
        // >>> 11
```

##### Python

```python
r.set("total_crashes", 0)
res7 = r.incr("total_crashes")
print(res7)  # 1
res8 = r.incrby("total_crashes", 10)
print(res8)  # 11
```

##### Ruby

```ruby
r.set('total_crashes', 0)

res7 = r.incr('total_crashes')
puts res7 # >>> 1

res8 = r.incrby('total_crashes', 10)
puts res8 # >>> 11
```

##### Rust (Asynchronous)

```rust
        if let Ok(res) = r.set("total_crashes", 0).await {
            let res: String = res;
            println!("{res}");    // >>> OK
        }

        if let Ok(res) = r.incr("total_crashes", 1).await {
            let res: i32 = res;
            println!("{res}");    // >>> 1
        }

        if let Ok(res) = r.incr("total_crashes", 10).await {
            let res: i32 = res;
            println!("{res}");    // >>> 11
        }
```

##### Rust (Synchronous)

```rust
        if let Ok(res) = r.set("total_crashes", 0) {
            let res: String = res;
            println!("{res}");    // >>> OK
        }

        if let Ok(res) = r.incr("total_crashes", 1) {
            let res: i32 = res;
            println!("{res}");    // >>> 1
        }

        if let Ok(res) = r.incr("total_crashes", 10) {
            let res: i32 = res;
            println!("{res}");    // >>> 11
        }
```



Lệnh [`INCR`](https://redis.io/docs/latest/commands/incr) phân tích (parse) value string thành một số nguyên,
tăng nó thêm một, và cuối cùng đặt giá trị thu được làm value mới.
Có các lệnh tương tự khác như [`INCRBY`](https://redis.io/docs/latest/commands/incrby),
[`DECR`](https://redis.io/docs/latest/commands/decr) và [`DECRBY`](https://redis.io/docs/latest/commands/decrby). Về mặt nội bộ, nó
luôn là cùng một lệnh, chỉ hoạt động theo cách hơi khác nhau.

INCR là nguyên tử (atomic) nghĩa là gì?
Nghĩa là ngay cả khi nhiều client cùng gửi INCR lên
cùng một key thì cũng sẽ không bao giờ rơi vào race condition. Ví dụ, sẽ không bao giờ
xảy ra chuyện client 1 đọc "10", client 2 cũng đọc "10" cùng lúc, cả hai
tăng lên 11, và đặt value mới là 11. Value cuối cùng sẽ luôn là
12 và thao tác đọc-tăng-đặt (read-increment-set) được thực hiện trong khi tất cả các
client khác không thực thi lệnh nào cùng lúc.


## Giới hạn (Limits)

Theo mặc định, một Redis string đơn lẻ có thể có kích thước tối đa là 512 MB.

## Phép toán bitwise và bitfield (Bitwise and bitfield operations)

Để thực hiện các phép toán bitwise trên một string, xem tài liệu [kiểu dữ liệu bitmaps](https://redis.io/docs/latest/develop/data-types/strings/bitmaps). Để lưu và thao tác các giá trị số nguyên bên trong một string, xem tài liệu [kiểu dữ liệu bitfields](https://redis.io/docs/latest/develop/data-types/strings/bitfields).

## Hiệu năng (Performance)

Hầu hết các thao tác trên string là O(1), nghĩa là chúng rất hiệu quả.
Tuy nhiên, hãy cẩn thận với các lệnh [`SUBSTR`](https://redis.io/docs/latest/commands/substr), [`GETRANGE`](https://redis.io/docs/latest/commands/getrange), và [`SETRANGE`](https://redis.io/docs/latest/commands/setrange), vốn có thể là O(n).
Các lệnh string truy cập ngẫu nhiên (random-access) này có thể gây ra vấn đề hiệu năng khi làm việc với các string lớn.

## Các lựa chọn thay thế (Alternatives)

Nếu bạn đang lưu dữ liệu có cấu trúc dưới dạng một string đã serialize, bạn cũng có thể cân nhắc Redis [hashes](https://redis.io/docs/latest/develop/data-types/hashes) hoặc [JSON](https://redis.io/docs/latest/develop/data-types/json).

## Tìm hiểu thêm (Learn more)

* [Redis Strings Explained](https://www.youtube.com/watch?v=7CUt4yWeRQE) là một video ngắn, giải thích toàn diện về Redis string.
* [Redis University's RU101](https://university.redis.com/courses/ru101/) trình bày chi tiết về Redis string.

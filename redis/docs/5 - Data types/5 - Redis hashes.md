# Redis hashes

> Nguồn: <https://redis.io/docs/latest/develop/data-types/hashes/>
> Giới thiệu về Redis hashes

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


### Tóm tắt các lệnh hash (Hash command summary)

**33 lệnh trong nhóm này:**

[Xem tất cả các lệnh hash](https://redis.io/commands/?group=hash)

| Lệnh | Tóm tắt | Độ phức tạp | Từ phiên bản |
|---------|---------|------------|-------|
| [HDEL](https://redis.io/commands/hdel/) | Xóa một hoặc nhiều field và value của chúng khỏi một hash. Xóa luôn hash nếu không còn field nào. | O(N) với N là số field cần xóa... | 2.0.0 |
| [HEXISTS](https://redis.io/commands/hexists/) | Xác định một field có tồn tại trong một hash hay không. | O(1) | 2.0.0 |
| [HEXPIRE](https://redis.io/commands/hexpire/) | Đặt thời hạn cho field của hash bằng thời gian hết hạn tương đối (giây) | O(N) với N là số field được chỉ định | 7.4.0 |
| [HEXPIREAT](https://redis.io/commands/hexpireat/) | Đặt thời hạn cho field của hash bằng một Unix timestamp tuyệt đối (giây) | O(N) với N là số field được chỉ định | 7.4.0 |
| [HEXPIRETIME](https://redis.io/commands/hexpiretime/) | Trả về thời điểm hết hạn của một field trong hash dưới dạng Unix timestamp, tính bằng giây. | O(N) với N là số field được chỉ định | 7.4.0 |
| [HGET](https://redis.io/commands/hget/) | Trả về value của một field trong một hash. | O(1) | 2.0.0 |
| [HGETALL](https://redis.io/commands/hgetall/) | Trả về tất cả các field và value trong một hash. | O(N) với N là kích thước của hash. | 2.0.0 |
| [HGETDEL](https://redis.io/commands/hgetdel/) | Trả về value của một field và xóa field đó khỏi hash. | O(N) với N là số field được chỉ định | 8.0.0 |
| [HGETEX](https://redis.io/commands/hgetex/) | Lấy value của một hoặc nhiều field của một hash key cho trước, và có thể đặt thời hạn cho chúng. | O(N) với N là số field được chỉ định | 8.0.0 |
| [HIMPORT](https://redis.io/commands/himport/) | Lệnh chứa (container) cho các lệnh import hash theo session sử dụng fieldset. | Tùy thuộc vào subcommand. | 8.10.0 |

#### Tóm tắt các lệnh hash (Hash command summary) (part 2)

| Lệnh | Tóm tắt | Độ phức tạp | Từ phiên bản |
|---------|---------|------------|-------|
| [HIMPORT DISCARD](https://redis.io/commands/himport-discard/) | Xóa một fieldset cục bộ của session (session-local) theo tên. | O(N) với N là số fieldset cục bộ của session... | 8.10.0 |
| [HIMPORT DISCARDALL](https://redis.io/commands/himport-discardall/) | Xóa tất cả các fieldset cục bộ của session cho kết nối. | O(N) với N là số fieldset cục bộ của session... | 8.10.0 |
| [HIMPORT PREPARE](https://redis.io/commands/himport-prepare/) | Định nghĩa một fieldset cục bộ của session, ánh xạ một tên tới một tập tên field đã sắp xếp. | O(N*log(N)) với N là số field. | 8.10.0 |
| [HIMPORT SET](https://redis.io/commands/himport-set/) | Tạo một hash dựa trên fieldset từ các value được cung cấp theo thứ tự khớp với một fieldset đã được chuẩn bị trước đó. | O(N) với N là số field trong fieldset... | 8.10.0 |
| [HINCRBY](https://redis.io/commands/hincrby/) | Tăng value số nguyên của một field trong hash thêm một số. Dùng 0 làm giá trị khởi tạo nếu field chưa tồn tại. | O(1) | 2.0.0 |
| [HINCRBYFLOAT](https://redis.io/commands/hincrbyfloat/) | Tăng value số thực dấu phẩy động của một field thêm một số. Dùng 0 làm giá trị khởi tạo nếu field chưa tồn tại. | O(1) | 2.6.0 |
| [HKEYS](https://redis.io/commands/hkeys/) | Trả về tất cả các field trong một hash. | O(N) với N là kích thước của hash. | 2.0.0 |
| [HLEN](https://redis.io/commands/hlen/) | Trả về số field trong một hash. | O(1) | 2.0.0 |
| [HMGET](https://redis.io/commands/hmget/) | Trả về value của tất cả các field trong một hash. | O(N) với N là số field được yêu cầu... | 2.0.0 |
| [HMSET](https://redis.io/commands/hmset/) | Đặt value cho nhiều field. | O(N) với N là số field được đặt. | 2.0.0 |

#### Tóm tắt các lệnh hash (Hash command summary) (part 3)

| Lệnh | Tóm tắt | Độ phức tạp | Từ phiên bản |
|---------|---------|------------|-------|
| [HPERSIST](https://redis.io/commands/hpersist/) | Xóa thời hạn của mỗi field được chỉ định | O(N) với N là số field được chỉ định | 7.4.0 |
| [HPEXPIRE](https://redis.io/commands/hpexpire/) | Đặt thời hạn cho field của hash bằng thời gian hết hạn tương đối (mili giây) | O(N) với N là số field được chỉ định | 7.4.0 |
| [HPEXPIREAT](https://redis.io/commands/hpexpireat/) | Đặt thời hạn cho field của hash bằng một Unix timestamp tuyệt đối (mili giây) | O(N) với N là số field được chỉ định | 7.4.0 |
| [HPEXPIRETIME](https://redis.io/commands/hpexpiretime/) | Trả về thời điểm hết hạn của một field trong hash dưới dạng Unix timestamp, tính bằng mili giây. | O(N) với N là số field được chỉ định | 7.4.0 |
| [HPTTL](https://redis.io/commands/hpttl/) | Trả về TTL tính bằng mili giây của một field trong hash. | O(N) với N là số field được chỉ định | 7.4.0 |
| [HRANDFIELD](https://redis.io/commands/hrandfield/) | Trả về một hoặc nhiều field ngẫu nhiên từ một hash. | O(N) với N là số field được trả về | 6.2.0 |
| [HSCAN](https://redis.io/commands/hscan/) | Duyệt qua các field và value của một hash. | O(1) cho mỗi lần gọi. O(N) cho một lần duyệt hoàn chỉnh... | 2.8.0 |
| [HSET](https://redis.io/commands/hset/) | Tạo hoặc sửa đổi value của một field trong một hash. | O(1) cho mỗi cặp field/value được thêm, nên O(N)... | 2.0.0 |
| [HSETEX](https://redis.io/commands/hsetex/) | Đặt value của một hoặc nhiều field của một hash key cho trước, và có thể đặt thời hạn cho chúng. | O(N) với N là số field được đặt. | 8.0.0 |
| [HSETNX](https://redis.io/commands/hsetnx/) | Đặt value của một field trong hash chỉ khi field đó chưa tồn tại. | O(1) | 2.0.0 |

#### Tóm tắt các lệnh hash (Hash command summary) (part 4)

| Lệnh | Tóm tắt | Độ phức tạp | Từ phiên bản |
|---------|---------|------------|-------|
| [HSTRLEN](https://redis.io/commands/hstrlen/) | Trả về độ dài value của một field. | O(1) | 3.2.0 |
| [HTTL](https://redis.io/commands/httl/) | Trả về TTL tính bằng giây của một field trong hash. | O(N) với N là số field được chỉ định | 7.4.0 |
| [HVALS](https://redis.io/commands/hvals/) | Trả về tất cả các value trong một hash. | O(N) với N là kích thước của hash. | 2.0.0 |



Redis hash là các kiểu bản ghi (record type) có cấu trúc là tập hợp các cặp field-value.
Bạn có thể dùng hash để biểu diễn các object cơ bản và để lưu các nhóm bộ đếm (counter), cùng nhiều mục đích khác.

*Nền tảng: Đặt và truy xuất các field của hash bằng HSET và HGET (ghi đè value của field đã có)*

**Lệnh (Commands):** HSET, HGET, HGETALL

**Độ phức tạp (Complexity):**
- HSET: O(1)
- HGET: O(1)
- HGETALL: O(N)

**Có sẵn trong (Available in):** Redis CLI, C#, Go, Java (Asynchronous - Lettuce), Java (Reactive - Lettuce), Java (Synchronous - Jedis), JavaScript (Node.js), PHP, Python, Ruby, Rust (Asynchronous), Rust (Synchronous)

##### Redis CLI

```
> HSET bike:1 model Deimos brand Ergonom type 'Enduro bikes' price 4972
(integer) 4
> HGET bike:1 model
"Deimos"
> HGET bike:1 price
"4972"
> HGETALL bike:1
1) "model"
2) "Deimos"
3) "brand"
4) "Ergonom"
5) "type"
6) "Enduro bikes"
7) "price"
8) "4972"
```

##### C#

```csharp
        db.HashSet("bike:1", [
            new("model", "Deimos"),
            new("brand", "Ergonom"),
            new("type", "Enduro bikes"),
            new("price", 4972)
        ]);

        Console.WriteLine("Hash Created");
        // Hash Created

        var model = db.HashGet("bike:1", "model");
        Console.WriteLine($"Model: {model}");
        // Model: Deimos

        var price = db.HashGet("bike:1", "price");
        Console.WriteLine($"Price: {price}");
        // Price: 4972

        var bike = db.HashGetAll("bike:1");
        Console.WriteLine("bike:1");
        Console.WriteLine(string.Join("\n", bike.Select(b => $"{b.Name}: {b.Value}")));
        // Bike:1:
        // model: Deimos
        // brand: Ergonom
        // type: Enduro bikes
        // price: 4972
```

##### Go

```go
	hashFields := []string{
		"model", "Deimos",
		"brand", "Ergonom",
		"type", "Enduro bikes",
		"price", "4972",
	}

	res1, err := rdb.HSet(ctx, "bike:1", hashFields).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res1) // >>> 4

	res2, err := rdb.HGet(ctx, "bike:1", "model").Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res2) // >>> Deimos

	res3, err := rdb.HGet(ctx, "bike:1", "price").Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res3) // >>> 4972

	cmdReturn := rdb.HGetAll(ctx, "bike:1")
	res4, err := cmdReturn.Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res4)
	// >>> map[brand:Ergonom model:Deimos price:4972 type:Enduro bikes]

	type BikeInfo struct {
		Model string `redis:"model"`
		Brand string `redis:"brand"`
		Type  string `redis:"type"`
		Price int    `redis:"price"`
	}

	var res4a BikeInfo

	if err := cmdReturn.Scan(&res4a); err != nil {
		panic(err)
	}

	fmt.Printf("Model: %v, Brand: %v, Type: %v, Price: $%v\n",
		res4a.Model, res4a.Brand, res4a.Type, res4a.Price)
	// >>> Model: Deimos, Brand: Ergonom, Type: Enduro bikes, Price: $4972
```

##### Java (Asynchronous - Lettuce)

```java
            Map<String, String> bike1 = new HashMap<>();
            bike1.put("model", "Deimos");
            bike1.put("brand", "Ergonom");
            bike1.put("type", "Enduro bikes");
            bike1.put("price", "4972");

            CompletableFuture<Void> setGetAll = asyncCommands.hset("bike:1", bike1).thenCompose(res1 -> {
                System.out.println(res1); // >>> 4
                return asyncCommands.hget("bike:1", "model");
            }).thenCompose(res2 -> {
                System.out.println(res2); // >>> Deimos
                return asyncCommands.hget("bike:1", "price");
            }).thenCompose(res3 -> {
                System.out.println(res3); // >>> 4972
                return asyncCommands.hgetall("bike:1");
            })
                    .thenAccept(System.out::println)
                    // >>> {type=Enduro bikes, brand=Ergonom, price=4972, model=Deimos}
                    .toCompletableFuture();
```

##### Java (Reactive - Lettuce)

```java
            Map<String, String> bike1 = new HashMap<>();
            bike1.put("model", "Deimos");
            bike1.put("brand", "Ergonom");
            bike1.put("type", "Enduro bikes");
            bike1.put("price", "4972");

            Mono<Long> setGetAll = reactiveCommands.hset("bike:1", bike1).doOnNext(result -> {
                System.out.println(result); // >>> 4
            });

            setGetAll.block();

            Mono<String> getModel = reactiveCommands.hget("bike:1", "model").doOnNext(result -> {
                System.out.println(result); // >>> Deimos
            });

            Mono<String> getPrice = reactiveCommands.hget("bike:1", "price").doOnNext(result -> {
                System.out.println(result); // >>> 4972
            });

            Mono<List<KeyValue<String, String>>> getAll = reactiveCommands.hgetall("bike:1").collectList().doOnNext(result -> {
                System.out.println(result);
                // >>> [KeyValue[type, Enduro bikes], KeyValue[brand, Ergonom],
                // KeyValue[price, 4972], KeyValue[model, Deimos]]
            });
```

##### Java (Synchronous - Jedis)

```java
      Map<String, String> bike1 = new HashMap<>();
      bike1.put("model", "Deimos");
      bike1.put("brand", "Ergonom");
      bike1.put("type", "Enduro bikes");
      bike1.put("price", "4972");

      Long res1 = jedis.hset("bike:1", bike1);
      System.out.println(res1); // 4

      String res2 = jedis.hget("bike:1", "model");
      System.out.println(res2); // Deimos

      String res3 = jedis.hget("bike:1", "price");
      System.out.println(res3); // 4972

      Map<String, String> res4 = jedis.hgetAll("bike:1");
      System.out.println(res4); // {type=Enduro bikes, brand=Ergonom, price=4972, model=Deimos}
```

##### JavaScript (Node.js)

```javascript
const res1 = await client.hSet(
  'bike:1',
  {
    'model': 'Deimos',
    'brand': 'Ergonom',
    'type': 'Enduro bikes',
    'price': 4972,
  }
)
console.log(res1) // 4

const res2 = await client.hGet('bike:1', 'model')
console.log(res2)  // 'Deimos'

const res3 = await client.hGet('bike:1', 'price')
console.log(res3)  // '4972'

const res4 = await client.hGetAll('bike:1')
console.log(res4)
/*
{
  brand: 'Ergonom',
  model: 'Deimos',
  price: '4972',
  type: 'Enduro bikes'
}
*/
```

##### PHP

```php
        $res1 = $r->hmset('bike:1', [
            'model' => 'Deimos',
            'brand' => 'Ergonom',
            'type' => 'Enduro bikes',
            'price' => 4972,
        ]);

        echo $res1 . PHP_EOL;
        // >>> 4

        $res2 = $r->hget('bike:1', 'model');
        echo $res2 . PHP_EOL;
        // >>> Deimos

        $res3 = $r->hget('bike:1', 'price');
        echo $res3 . PHP_EOL;
        // >>> 4972

        $res4 = $r->hgetall('bike:1');
        echo json_encode($res3) . PHP_EOL;
        // >>> {"name":"Deimos","brand":"Ergonom","type":"Enduro bikes","price":"4972"}
```

##### Python

```python
res1 = r.hset(
    "bike:1",
    mapping={
        "model": "Deimos",
        "brand": "Ergonom",
        "type": "Enduro bikes",
        "price": 4972,
    },
)
print(res1)
# >>> 4

res2 = r.hget("bike:1", "model")
print(res2)
# >>> 'Deimos'

res3 = r.hget("bike:1", "price")
print(res3)
# >>> '4972'

res4 = r.hgetall("bike:1")
print(res4)
# >>> {'model': 'Deimos', 'brand': 'Ergonom', 'type': 'Enduro bikes', 'price': '4972'}

```

##### Ruby

```ruby
res1 = r.hset('bike:1', {
  'model' => 'Deimos',
  'brand' => 'Ergonom',
  'type' => 'Enduro bikes',
  'price' => 4972
})
puts res1 # >>> 4

res2 = r.hget('bike:1', 'model')
puts res2 # >>> Deimos

res3 = r.hget('bike:1', 'price')
puts res3 # >>> 4972

res4 = r.hgetall('bike:1')
puts res4.inspect
# >>> {"model" => "Deimos", "brand" => "Ergonom", "type" => "Enduro bikes", "price" => "4972"}
```

##### Rust (Asynchronous)

```rust
        let hash_fields = [
            ("model", "Deimos"),
            ("brand", "Ergonom"),
            ("type", "Enduro bikes"),
            ("price", "4972"),
        ];

        if let Ok(res) = r.hset_multiple("bike:1", &hash_fields).await {
            let res: String = res;
            println!("{res}");    // >>> OK
        }

        match r.hget("bike:1", "model").await {
            Ok(res) => {
                let res: String = res;
                println!("{res}");   // >>> Deimos
            },
            Err(e) => {
                println!("Error getting bike:1 model: {e}");
                return;
            }
        };

        match r.hget("bike:1", "price").await {
            Ok(res) => {
                let res: String = res;
                println!("{res}");   // >>> 4972
            },
            Err(e) => {
                println!("Error getting bike:1 price: {e}");
                return;
            }
        };

        match r.hgetall("bike:1").await {
            Ok(res) => {
                let res: Vec<(String, String)> = res;
                println!("{res:?}");
                // >>> [("model", "Deimos"), ("brand", "Ergonom"), ("type", "Enduro bikes"), ("price", "4972")]
            },
            Err(e) => {
                println!("Error getting bike:1: {e}");
                return;
            }
        };
```

##### Rust (Synchronous)

```rust
        let hash_fields = [
            ("model", "Deimos"),
            ("brand", "Ergonom"),
            ("type", "Enduro bikes"),
            ("price", "4972"),
        ];

        if let Ok(res) = r.hset_multiple("bike:1", &hash_fields) {
            let res: String = res;
            println!("{res}");    // >>> OK
        }

        match r.hget("bike:1", "model") {
            Ok(res) => {
                let res: String = res;
                println!("{res}");   // >>> Deimos
            },
            Err(e) => {
                println!("Error getting bike:1 model: {e}");
                return;
            }
        };

        match r.hget("bike:1", "price") {
            Ok(res) => {
                let res: String = res;
                println!("{res}");   // >>> 4972
            },
            Err(e) => {
                println!("Error getting bike:1 price: {e}");
                return;
            }
        };

        match r.hgetall("bike:1") {
            Ok(res) => {
                let res: Vec<(String, String)> = res;
                println!("{res:?}");
                // >>> [("model", "Deimos"), ("brand", "Ergonom"), ("type", "Enduro bikes"), ("price", "4972")]
            },
            Err(e) => {
                println!("Error getting bike:1: {e}");
                return;
            }
        };
```



Mặc dù hash rất tiện để biểu diễn các *object*, thực ra số lượng field bạn có thể
đặt vào một hash không có giới hạn thực tế nào (ngoài bộ nhớ khả dụng), nên bạn có thể dùng
hash theo nhiều cách khác nhau trong ứng dụng của mình.

Lệnh [`HSET`](https://redis.io/docs/latest/commands/hset) đặt nhiều field của hash, trong khi [`HGET`](https://redis.io/docs/latest/commands/hget) truy xuất
một field duy nhất. [`HMGET`](https://redis.io/docs/latest/commands/hmget) tương tự [`HGET`](https://redis.io/docs/latest/commands/hget) nhưng trả về một mảng các value:

*Truy xuất value của nhiều field từ một hash bằng HMGET khi bạn cần giảm số lần round trip tới server*

**Dựa trên (Builds upon):** set_get_all

**Lệnh (Commands):** DEL, HSET, HMGET

**Độ phức tạp (Complexity):**
- DEL: O(N)
- HSET: O(1)
- HMGET: O(N)

**Có sẵn trong (Available in):** Redis CLI, C#, Go, Java (Asynchronous - Lettuce), Java (Reactive - Lettuce), Java (Synchronous - Jedis), JavaScript (Node.js), PHP, Python, Ruby, Rust (Asynchronous), Rust (Synchronous)

##### Redis CLI

```
# Recreate the bike:1 hash so this example runs on its own.
> DEL bike:1
(integer) 1
> HSET bike:1 model Deimos brand Ergonom type 'Enduro bikes' price 4972
(integer) 4
> HMGET bike:1 model price no-such-field
1) "Deimos"
2) "4972"
3) (nil)
```

##### C#

```csharp
        // Recreate the bike:1 hash so this example runs on its own.
        db.KeyDelete("bike:1");
        db.HashSet("bike:1", [
            new("model", "Deimos"),
            new("brand", "Ergonom"),
            new("type", "Enduro bikes"),
            new("price", 4972)
        ]);

        var values = db.HashGet("bike:1", ["model", "price"]);
        Console.WriteLine(string.Join(" ", values));
        // Deimos 4972
```

##### Go

```go
	// Recreate the bike:1 hash so this example runs on its own.
	rdb.Del(ctx, "bike:1")
	hashFields := []string{
		"model", "Deimos",
		"brand", "Ergonom",
		"type", "Enduro bikes",
		"price", "4972",
	}
	rdb.HSet(ctx, "bike:1", hashFields)

	cmdReturn := rdb.HMGet(ctx, "bike:1", "model", "price")
	res5, err := cmdReturn.Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res5) // >>> [Deimos 4972]

	type BikeInfo struct {
		Model string `redis:"model"`
		Brand string `redis:"-"`
		Type  string `redis:"-"`
		Price int    `redis:"price"`
	}

	var res5a BikeInfo

	if err := cmdReturn.Scan(&res5a); err != nil {
		panic(err)
	}

	fmt.Printf("Model: %v, Price: $%v\n", res5a.Model, res5a.Price)
	// >>> Model: Deimos, Price: $4972
```

##### Java (Asynchronous - Lettuce)

```java
            // Recreate the bike:1 hash so this example runs on its own.
            CompletableFuture<Void> hmGet = setGetAll
                    .thenCompose(res4 -> asyncCommands.del("bike:1"))
                    .thenCompose(delRes -> asyncCommands.hset("bike:1", bike1))
                    .thenCompose(hsetRes -> asyncCommands.hmget("bike:1", "model", "price"))
                    .thenAccept(System.out::println)
                    // [KeyValue[model, Deimos], KeyValue[price, 4972]]
                    .toCompletableFuture();
```

##### Java (Reactive - Lettuce)

```java
            // Recreate the bike:1 hash so this example runs on its own.
            Mono<List<KeyValue<String, String>>> hmGet = reactiveCommands.del("bike:1")
                    .then(reactiveCommands.hset("bike:1", bike1))
                    .then(reactiveCommands.hmget("bike:1", "model", "price").collectList())
                    .doOnNext(result -> {
                        System.out.println(result);
                        // >>> [KeyValue[model, Deimos], KeyValue[price, 4972]]
                    });
```

##### Java (Synchronous - Jedis)

```java
      // Recreate the bike:1 hash so this example runs on its own.
      jedis.del("bike:1");
      jedis.hset("bike:1", bike1);

      List<String> res5 = jedis.hmget("bike:1", "model", "price");
      System.out.println(res5); // [Deimos, 4972]
```

##### JavaScript (Node.js)

```javascript
// Recreate the bike:1 hash so this example runs on its own.
await client.del('bike:1')
await client.hSet(
  'bike:1',
  {
    'model': 'Deimos',
    'brand': 'Ergonom',
    'type': 'Enduro bikes',
    'price': 4972,
  }
)

const res5 = await client.hmGet('bike:1', ['model', 'price'])
console.log(res5)  // ['Deimos', '4972']
```

##### PHP

```php
        // Recreate the bike:1 hash so this example runs on its own.
        $r->del('bike:1');
        $r->hmset('bike:1', [
            'model' => 'Deimos',
            'brand' => 'Ergonom',
            'type' => 'Enduro bikes',
            'price' => 4972,
        ]);

        $res5 = $r->hmget('bike:1', ['model', 'price']);
        echo json_encode($res5) . PHP_EOL;
        // >>> ["Deimos","4972"]
```

##### Python

```python
# Recreate the bike:1 hash so this example runs on its own.
r.delete("bike:1")
r.hset(
    "bike:1",
    mapping={
        "model": "Deimos",
        "brand": "Ergonom",
        "type": "Enduro bikes",
        "price": 4972,
    },
)

res5 = r.hmget("bike:1", ["model", "price"])
print(res5)
# >>> ['Deimos', '4972']
```

##### Ruby

```ruby
# Recreate the bike:1 hash so this example runs on its own.
r.del('bike:1')
r.hset('bike:1', {
  'model' => 'Deimos',
  'brand' => 'Ergonom',
  'type' => 'Enduro bikes',
  'price' => 4972
})

res5 = r.hmget('bike:1', 'model', 'price', 'no-such-field')
puts res5.inspect # >>> ["Deimos", "4972", nil]
```

##### Rust (Asynchronous)

```rust
        // Recreate the bike:1 hash so this example runs on its own.
        let _: () = r.del("bike:1").await.expect("Failed to del");
        let _: () = r.hset_multiple(
            "bike:1",
            &[("model", "Deimos"), ("brand", "Ergonom"), ("type", "Enduro bikes"), ("price", "4972")],
        ).await.expect("Failed to hset");

        match r.hmget("bike:1", &["model", "price"]).await {
            Ok(res) => {
                let res: Vec<String> = res;
                println!("{res:?}");   // >>> ["Deimos", "4972"]
            },
            Err(e) => {
                println!("Error getting bike:1: {e}");
                return;
            }
        };
```

##### Rust (Synchronous)

```rust
        // Recreate the bike:1 hash so this example runs on its own.
        let _: () = r.del("bike:1").expect("Failed to del");
        let _: () = r.hset_multiple(
            "bike:1",
            &[("model", "Deimos"), ("brand", "Ergonom"), ("type", "Enduro bikes"), ("price", "4972")],
        ).expect("Failed to hset");

        match r.hmget("bike:1", &["model", "price"]) {
            Ok(res) => {
                let res: Vec<String> = res;
                println!("{res:?}");   // >>> ["Deimos", "4972"]
            },
            Err(e) => {
                println!("Error getting bike:1: {e}");
                return;
            }
        };
```



Cũng có các lệnh có thể thực hiện thao tác trên từng field riêng lẻ,
như [`HINCRBY`](https://redis.io/docs/latest/commands/hincrby):

*Tăng value của field trong hash để làm bộ đếm bằng HINCRBY (tạo field nếu chưa có, khởi tạo bằng 0)*

**Dựa trên (Builds upon):** set_get_all

**Lệnh (Commands):** DEL, HSET, HINCRBY

**Độ phức tạp (Complexity):**
- DEL: O(N)
- HSET: O(1)
- HINCRBY: O(1)

**Có sẵn trong (Available in):** Redis CLI, C#, Go, Java (Asynchronous - Lettuce), Java (Reactive - Lettuce), Java (Synchronous - Jedis), JavaScript (Node.js), PHP, Python, Ruby, Rust (Asynchronous), Rust (Synchronous)

##### Redis CLI

```
# Recreate the bike:1 hash so this example runs on its own.
> DEL bike:1
(integer) 1
> HSET bike:1 model Deimos brand Ergonom type 'Enduro bikes' price 4972
(integer) 4
> HINCRBY bike:1 price 100
(integer) 5072
> HINCRBY bike:1 price -100
(integer) 4972
```

##### C#

```csharp
        // Recreate the bike:1 hash so this example runs on its own.
        db.KeyDelete("bike:1");
        db.HashSet("bike:1", [
            new("model", "Deimos"),
            new("brand", "Ergonom"),
            new("type", "Enduro bikes"),
            new("price", 4972)
        ]);

        var newPrice = db.HashIncrement("bike:1", "price", 100);
        Console.WriteLine($"New price: {newPrice}");
        // New price: 5072

        newPrice = db.HashIncrement("bike:1", "price", -100);
        Console.WriteLine($"New price: {newPrice}");
        // New price: 4972
```

##### Go

```go
	// Recreate the bike:1 hash so this example runs on its own.
	rdb.Del(ctx, "bike:1")
	hashFields := []string{
		"model", "Deimos",
		"brand", "Ergonom",
		"type", "Enduro bikes",
		"price", "4972",
	}
	rdb.HSet(ctx, "bike:1", hashFields)

	res6, err := rdb.HIncrBy(ctx, "bike:1", "price", 100).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res6) // >>> 5072

	res7, err := rdb.HIncrBy(ctx, "bike:1", "price", -100).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res7) // >>> 4972
```

##### Java (Asynchronous - Lettuce)

```java
            // Recreate the bike:1 hash so this example runs on its own.
            CompletableFuture<Void> hIncrBy = hmGet
                    .thenCompose(r -> asyncCommands.del("bike:1"))
                    .thenCompose(delRes -> asyncCommands.hset("bike:1", bike1))
                    .thenCompose(hsetRes -> asyncCommands.hincrby("bike:1", "price", 100))
                    .thenCompose(res6 -> {
                        System.out.println(res6); // >>> 5072
                        return asyncCommands.hincrby("bike:1", "price", -100);
                    })
                    .thenAccept(System.out::println)
                    // >>> 4972
                    .toCompletableFuture();
```

##### Java (Reactive - Lettuce)

```java
            // Recreate the bike:1 hash so this example runs on its own.
            Mono<Void> hIncrBy = reactiveCommands.del("bike:1")
                    .then(reactiveCommands.hset("bike:1", bike1))
                    .then(reactiveCommands.hincrby("bike:1", "price", 100)).doOnNext(result -> {
                System.out.println(result); // >>> 5072
            }).flatMap(v -> reactiveCommands.hincrby("bike:1", "price", -100)).doOnNext(result -> {
                System.out.println(result); // >>> 4972
            }).then();
```

##### Java (Synchronous - Jedis)

```java
      // Recreate the bike:1 hash so this example runs on its own.
      jedis.del("bike:1");
      jedis.hset("bike:1", bike1);

      Long res6 = jedis.hincrBy("bike:1", "price", 100);
      System.out.println(res6); // 5072
      Long res7 = jedis.hincrBy("bike:1", "price", -100);
      System.out.println(res7); // 4972
```

##### JavaScript (Node.js)

```javascript
// Recreate the bike:1 hash so this example runs on its own.
await client.del('bike:1')
await client.hSet(
  'bike:1',
  {
    'model': 'Deimos',
    'brand': 'Ergonom',
    'type': 'Enduro bikes',
    'price': 4972,
  }
)

const res6 = await client.hIncrBy('bike:1', 'price', 100)
console.log(res6)  // 5072
const res7 = await client.hIncrBy('bike:1', 'price', -100)
console.log(res7)  // 4972
```

##### PHP

```php
        // Recreate the bike:1 hash so this example runs on its own.
        $r->del('bike:1');
        $r->hmset('bike:1', [
            'model' => 'Deimos',
            'brand' => 'Ergonom',
            'type' => 'Enduro bikes',
            'price' => 4972,
        ]);

        $res6 = $r->hincrby('bike:1', 'price', 100);
        echo $res6 . PHP_EOL;
        // >>> 5072

        $res7 = $r->hincrby('bike:1', 'price', -100);
        echo $res7 . PHP_EOL;
        // >>> 4972
```

##### Python

```python
# Recreate the bike:1 hash so this example runs on its own.
r.delete("bike:1")
r.hset(
    "bike:1",
    mapping={
        "model": "Deimos",
        "brand": "Ergonom",
        "type": "Enduro bikes",
        "price": 4972,
    },
)

res6 = r.hincrby("bike:1", "price", 100)
print(res6)
# >>> 5072
res7 = r.hincrby("bike:1", "price", -100)
print(res7)
# >>> 4972
```

##### Ruby

```ruby
# Recreate the bike:1 hash so this example runs on its own.
r.del('bike:1')
r.hset('bike:1', {
  'model' => 'Deimos',
  'brand' => 'Ergonom',
  'type' => 'Enduro bikes',
  'price' => 4972
})

res6 = r.hincrby('bike:1', 'price', 100)
puts res6 # >>> 5072

res7 = r.hincrby('bike:1', 'price', -100)
puts res7 # >>> 4972
```

##### Rust (Asynchronous)

```rust
        // Recreate the bike:1 hash so this example runs on its own.
        let _: () = r.del("bike:1").await.expect("Failed to del");
        let _: () = r.hset_multiple(
            "bike:1",
            &[("model", "Deimos"), ("brand", "Ergonom"), ("type", "Enduro bikes"), ("price", "4972")],
        ).await.expect("Failed to hset");

        if let Ok(res) = r.hincr("bike:1", "price", 100).await {
            let res: i32 = res;
            println!("{res}");    // >>> 5072
        }

        if let Ok(res) = r.hincr("bike:1", "price", -100).await {
            let res: i32 = res;
            println!("{res}");    // >>> 4972
        }
```

##### Rust (Synchronous)

```rust
        // Recreate the bike:1 hash so this example runs on its own.
        let _: () = r.del("bike:1").expect("Failed to del");
        let _: () = r.hset_multiple(
            "bike:1",
            &[("model", "Deimos"), ("brand", "Ergonom"), ("type", "Enduro bikes"), ("price", "4972")],
        ).expect("Failed to hset");

        if let Ok(res) = r.hincr("bike:1", "price", 100) {
            let res: i32 = res;
            println!("{res}");    // >>> 5072
        }

        if let Ok(res) = r.hincr("bike:1", "price", -100) {
            let res: i32 = res;
            println!("{res}");    // >>> 4972
        }
```



Bạn có thể tìm [danh sách đầy đủ các lệnh hash trong tài liệu](https://redis.io/commands?group=hash).

Đáng chú ý là các hash nhỏ (tức là có ít phần tử với value nhỏ) được
mã hóa theo một cách đặc biệt trong bộ nhớ, khiến chúng rất tiết kiệm bộ nhớ.

## Ví dụ (Examples)

* Lưu các bộ đếm cho số lần bike:1 đã được đi, bị tai nạn, hoặc đổi chủ:

*Pattern thực tế: Kết hợp HINCRBY và HMGET để theo dõi nhiều bộ đếm khi bạn cần cập nhật nguyên tử (atomic) trên nhiều field*

**Độ khó (Difficulty):** Trung bình (Intermediate)

**Dựa trên (Builds upon):** hincrby, hmget

**Lệnh (Commands):** HINCRBY, HGET, HMGET

**Độ phức tạp (Complexity):**
- HINCRBY: O(1)
- HGET: O(1)
- HMGET: O(N)

**Có sẵn trong (Available in):** Redis CLI, C#, Go, Java (Asynchronous - Lettuce), Java (Reactive - Lettuce), Java (Synchronous - Jedis), JavaScript (Node.js), PHP, Python, Ruby, Rust (Asynchronous), Rust (Synchronous)

##### Redis CLI

```
> HINCRBY bike:1:stats rides 1
(integer) 1
> HINCRBY bike:1:stats rides 1
(integer) 2
> HINCRBY bike:1:stats rides 1
(integer) 3
> HINCRBY bike:1:stats crashes 1
(integer) 1
> HINCRBY bike:1:stats owners 1
(integer) 1
> HGET bike:1:stats rides
"3"
> HMGET bike:1:stats owners crashes
1) "1"
2) "1"
```

##### C#

```csharp
        var rides = db.HashIncrement("bike:1", "rides");
        Console.WriteLine($"Rides: {rides}");
        // Rides: 1

        rides = db.HashIncrement("bike:1", "rides");
        Console.WriteLine($"Rides: {rides}");
        // Rides: 2

        rides = db.HashIncrement("bike:1", "rides");
        Console.WriteLine($"Rides: {rides}");
        // Rides: 3

        var crashes = db.HashIncrement("bike:1", "crashes");
        Console.WriteLine($"Crashes: {crashes}");
        // Crashes: 1

        var owners = db.HashIncrement("bike:1", "owners");
        Console.WriteLine($"Owners: {owners}");
        // Owners: 1

        var stats = db.HashGet("bike:1", ["crashes", "owners"]);
        Console.WriteLine($"Bike stats: crashes={stats[0]}, owners={stats[1]}");
        // Bike stats: crashes=1, owners=1
```

##### Go

```go
	res8, err := rdb.HIncrBy(ctx, "bike:1:stats", "rides", 1).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res8) // >>> 1

	res9, err := rdb.HIncrBy(ctx, "bike:1:stats", "rides", 1).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res9) // >>> 2

	res10, err := rdb.HIncrBy(ctx, "bike:1:stats", "rides", 1).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res10) // >>> 3

	res11, err := rdb.HIncrBy(ctx, "bike:1:stats", "crashes", 1).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res11) // >>> 1

	res12, err := rdb.HIncrBy(ctx, "bike:1:stats", "owners", 1).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res12) // >>> 1

	res13, err := rdb.HGet(ctx, "bike:1:stats", "rides").Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res13) // >>> 3

	res14, err := rdb.HMGet(ctx, "bike:1:stats", "crashes", "owners").Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res14) // >>> [1 1]
```

##### Java (Asynchronous - Lettuce)

```java
            CompletableFuture<Void> incrByGetMget = asyncCommands.hincrby("bike:1:stats", "rides", 1).thenCompose(res7 -> {
                System.out.println(res7); // >>> 1
                return asyncCommands.hincrby("bike:1:stats", "rides", 1);
            }).thenCompose(res8 -> {
                System.out.println(res8); // >>> 2
                return asyncCommands.hincrby("bike:1:stats", "rides", 1);
            }).thenCompose(res9 -> {
                System.out.println(res9); // >>> 3
                return asyncCommands.hincrby("bike:1:stats", "crashes", 1);
            }).thenCompose(res10 -> {
                System.out.println(res10); // >>> 1
                return asyncCommands.hincrby("bike:1:stats", "owners", 1);
            }).thenCompose(res11 -> {
                System.out.println(res11); // >>> 1
                return asyncCommands.hget("bike:1:stats", "rides");
            }).thenCompose(res12 -> {
                System.out.println(res12); // >>> 3
                return asyncCommands.hmget("bike:1:stats", "crashes", "owners");
            })
                    .thenAccept(System.out::println)
                    // >>> [KeyValue[crashes, 1], KeyValue[owners, 1]]
                    .toCompletableFuture();
```

##### Java (Reactive - Lettuce)

```java
            Mono<Void> incrByGetMget = reactiveCommands.hincrby("bike:1:stats", "rides", 1).doOnNext(result -> {
                System.out.println(result); // >>> 1
            }).flatMap(v -> reactiveCommands.hincrby("bike:1:stats", "rides", 1)).doOnNext(result -> {
                System.out.println(result); // >>> 2
            }).flatMap(v -> reactiveCommands.hincrby("bike:1:stats", "rides", 1)).doOnNext(result -> {
                System.out.println(result); // >>> 3
            }).flatMap(v -> reactiveCommands.hincrby("bike:1:stats", "crashes", 1)).doOnNext(result -> {
                System.out.println(result); // >>> 1
            }).flatMap(v -> reactiveCommands.hincrby("bike:1:stats", "owners", 1)).doOnNext(result -> {
                System.out.println(result); // >>> 1
            }).then();

            incrByGetMget.block();

            Mono<String> getRides = reactiveCommands.hget("bike:1:stats", "rides").doOnNext(result -> {
                System.out.println(result); // >>> 3
            });

            Mono<List<KeyValue<String, String>>> getCrashesOwners = reactiveCommands.hmget("bike:1:stats", "crashes", "owners")
                    .collectList().doOnNext(result -> {
                        System.out.println(result);
                        // >>> [KeyValue[crashes, 1], KeyValue[owners, 1]]
                    });
```

##### Java (Synchronous - Jedis)

```java
      Long res8 = jedis.hincrBy("bike:1:stats", "rides", 1);
      System.out.println(res8); // 1
      Long res9 = jedis.hincrBy("bike:1:stats", "rides", 1);
      System.out.println(res9); // 2
      Long res10 = jedis.hincrBy("bike:1:stats", "rides", 1);
      System.out.println(res10); // 3
      Long res11 = jedis.hincrBy("bike:1:stats", "crashes", 1);
      System.out.println(res11); // 1
      Long res12 = jedis.hincrBy("bike:1:stats", "owners", 1);
      System.out.println(res12); // 1
      String res13 = jedis.hget("bike:1:stats", "rides");
      System.out.println(res13); // 3
      List<String> res14 = jedis.hmget("bike:1:stats", "crashes", "owners");
      System.out.println(res14); // [1, 1]
```

##### JavaScript (Node.js)

```javascript
const res11 = await client.hIncrBy('bike:1:stats', 'rides', 1)
console.log(res11)  // 1
const res12 = await client.hIncrBy('bike:1:stats', 'rides', 1)
console.log(res12)  // 2
const res13 = await client.hIncrBy('bike:1:stats', 'rides', 1)
console.log(res13)  // 3
const res14 = await client.hIncrBy('bike:1:stats', 'crashes', 1)
console.log(res14)  // 1
const res15 = await client.hIncrBy('bike:1:stats', 'owners', 1)
console.log(res15)  // 1
const res16 = await client.hGet('bike:1:stats', 'rides')
console.log(res16)  // 3
const res17 = await client.hmGet('bike:1:stats', ['crashes', 'owners'])
console.log(res17)  // ['1', '1']
```

##### PHP

```php
        $res8 = $r->hincrby('bike:1:stats', 'rides', 1);
        echo $res8 . PHP_EOL;
        // >>> 1

        $res9 = $r->hincrby('bike:1:stats', 'rides', 1);
        echo $res9 . PHP_EOL;
        // >>> 2

        $res10 = $r->hincrby('bike:1:stats', 'rides', 1);
        echo $res10 . PHP_EOL;
        // >>> 3

        $res11 = $r->hincrby('bike:1:stats', 'crashes', 1);
        echo $res11 . PHP_EOL;
        // >>> 1

        $res12 = $r->hincrby('bike:1:stats', 'owners', 1);
        echo $res12 . PHP_EOL;
        // >>> 1

        $res13 = $r->hget('bike:1:stats', 'rides');
        echo $res13 . PHP_EOL;
        // >>> 3

        $res14 = $r->hmget('bike:1:stats', ['crashes', 'owners']);
        echo json_encode($res14) . PHP_EOL;
        // >>> ["1","1"]
```

##### Python

```python
res11 = r.hincrby("bike:1:stats", "rides", 1)
print(res11)
# >>> 1
res12 = r.hincrby("bike:1:stats", "rides", 1)
print(res12)
# >>> 2
res13 = r.hincrby("bike:1:stats", "rides", 1)
print(res13)
# >>> 3
res14 = r.hincrby("bike:1:stats", "crashes", 1)
print(res14)
# >>> 1
res15 = r.hincrby("bike:1:stats", "owners", 1)
print(res15)
# >>> 1
res16 = r.hget("bike:1:stats", "rides")
print(res16)
# >>> 3
res17 = r.hmget("bike:1:stats", ["crashes", "owners"])
print(res17)
# >>> ['1', '1']
```

##### Ruby

```ruby
res8 = r.hincrby('bike:1:stats', 'rides', 1)
puts res8 # >>> 1

res9 = r.hincrby('bike:1:stats', 'rides', 1)
puts res9 # >>> 2

res10 = r.hincrby('bike:1:stats', 'rides', 1)
puts res10 # >>> 3

res11 = r.hincrby('bike:1:stats', 'crashes', 1)
puts res11 # >>> 1

res12 = r.hincrby('bike:1:stats', 'owners', 1)
puts res12 # >>> 1

res13 = r.hget('bike:1:stats', 'rides')
puts res13 # >>> 3

res14 = r.hmget('bike:1:stats', 'owners', 'crashes')
puts res14.inspect # >>> ["1", "1"]
```

##### Rust (Asynchronous)

```rust
        if let Ok(res) = r.hincr("bike:1:stats", "rides", 1).await {
            let res: i32 = res;
            println!("{res}");    // >>> 1
        }

        if let Ok(res) = r.hincr("bike:1:stats", "rides", 1).await {
            let res: i32 = res;
            println!("{res}");    // >>> 2
        }

        if let Ok(res) = r.hincr("bike:1:stats", "rides", 1).await {
            let res: i32 = res;
            println!("{res}");    // >>> 3
        }

        if let Ok(res) = r.hincr("bike:1:stats", "crashes", 1).await {
            let res: i32 = res;
            println!("{res}");    // >>> 1
        }

        if let Ok(res) = r.hincr("bike:1:stats", "owners", 1).await {
            let res: i32 = res;
            println!("{res}");    // >>> 1
        }

        match r.hget("bike:1:stats", "rides").await {
            Ok(res) => {
                let res: i32 = res;
                println!("{res}");   // >>> 3
            },
            Err(e) => {
                println!("Error getting bike:1:stats rides: {e}");
                return;
            }
        };

        match r.hmget("bike:1:stats", &["crashes", "owners"]).await {
            Ok(res) => {
                let res: Vec<i32> = res;
                println!("{res:?}");   // >>> [1, 1]
            },
            Err(e) => {
                println!("Error getting bike:1:stats crashes and owners: {e}");
                return;
            }
        };
```

##### Rust (Synchronous)

```rust
        if let Ok(res) = r.hincr("bike:1:stats", "rides", 1) {
            let res: i32 = res;
            println!("{res}");    // >>> 1
        }

        if let Ok(res) = r.hincr("bike:1:stats", "rides", 1) {
            let res: i32 = res;
            println!("{res}");    // >>> 2
        }

        if let Ok(res) = r.hincr("bike:1:stats", "rides", 1) {
            let res: i32 = res;
            println!("{res}");    // >>> 3
        }

        if let Ok(res) = r.hincr("bike:1:stats", "crashes", 1) {
            let res: i32 = res;
            println!("{res}");    // >>> 1
        }

        if let Ok(res) = r.hincr("bike:1:stats", "owners", 1) {
            let res: i32 = res;
            println!("{res}");    // >>> 1
        }

        match r.hget("bike:1:stats", "rides") {
            Ok(res) => {
                let res: i32 = res;
                println!("{res}");   // >>> 3
            },
            Err(e) => {
                println!("Error getting bike:1:stats rides: {e}");
                return;
            }
        };

        match r.hmget("bike:1:stats", &["crashes", "owners"]) {
            Ok(res) => {
                let res: Vec<i32> = res;
                println!("{res:?}");   // >>> [1, 1]
            },
            Err(e) => {
                println!("Error getting bike:1:stats crashes and owners: {e}");
                return;
            }
        };
```



<a name="field-expiration"></a>

## Hết hạn field (Field expiration)

Redis 7.4 giới thiệu khả năng chỉ định thời điểm hết hạn (expiration time) hoặc giá trị thời gian sống (time-to-live, TTL) cho từng field riêng lẻ của hash.
Khả năng này tương đương với [key expiration](https://redis.io/docs/latest/develop/using-commands/keyspace#key-expiration) và bao gồm một số lệnh tương tự.

Dùng các lệnh sau để đặt thời điểm hết hạn chính xác hoặc giá trị TTL cho các field cụ thể:

* [`HEXPIRE`](https://redis.io/docs/latest/commands/hexpire): đặt TTL còn lại tính bằng giây.
* [`HPEXPIRE`](https://redis.io/docs/latest/commands/hpexpire): đặt TTL còn lại tính bằng mili giây.
* [`HEXPIREAT`](https://redis.io/docs/latest/commands/hexpireat): đặt thời điểm hết hạn là một timestamp[^1] tính bằng giây.
* [`HPEXPIREAT`](https://redis.io/docs/latest/commands/hpexpireat): đặt thời điểm hết hạn là một timestamp tính bằng mili giây.

[^1]: tất cả các timestamp được tính bằng giây hoặc mili giây kể từ [Unix epoch](https://en.wikipedia.org/wiki/Unix_time).

Dùng các lệnh sau để lấy thời điểm chính xác hoặc TTL còn lại cho tới khi các field cụ thể hết hạn:

* [`HEXPIRETIME`](https://redis.io/docs/latest/commands/hexpiretime): lấy thời điểm hết hạn dưới dạng timestamp tính bằng giây.
* [`HPEXPIRETIME`](https://redis.io/docs/latest/commands/hpexpiretime): lấy thời điểm hết hạn dưới dạng timestamp tính bằng mili giây.
* [`HTTL`](https://redis.io/docs/latest/commands/httl): lấy TTL còn lại tính bằng giây.
* [`HPTTL`](https://redis.io/docs/latest/commands/hpttl): lấy TTL còn lại tính bằng mili giây.

Dùng lệnh sau để xóa thời hạn của các field cụ thể:

* [`HPERSIST`](https://redis.io/docs/latest/commands/hpersist): xóa thời hạn.

Redis 8.0 giới thiệu các lệnh sau:

* [`HGETEX`](https://redis.io/docs/latest/commands/hgetex): Lấy value của một hoặc nhiều field của một hash key cho trước và có thể đặt thời điểm hết hạn hoặc thời gian sống (TTL) cho chúng.
* [`HSETEX`](https://redis.io/docs/latest/commands/hsetex): Đặt value của một hoặc nhiều field của một hash key cho trước và có thể đặt thời điểm hết hạn hoặc thời gian sống (TTL) cho chúng.

### Các trường hợp sử dụng phổ biến của hết hạn field (Common field expiration use cases)

1. **Theo dõi sự kiện (Event Tracking)**: Dùng một hash key để lưu các sự kiện trong một giờ vừa qua. Đặt TTL của mỗi sự kiện là một giờ. Dùng `HLEN` để đếm các sự kiện trong một giờ qua.

1. **Phát hiện gian lận (Fraud Detection)**: Tạo một hash với các bộ đếm sự kiện theo giờ. Đặt TTL của mỗi field là 48 giờ. Truy vấn hash để lấy số sự kiện mỗi giờ trong 48 giờ qua.

1. **Quản lý session khách hàng (Customer Session Management)**: Lưu dữ liệu khách hàng trong các hash key. Tạo một hash key mới cho mỗi session và thêm một field session vào hash key của khách hàng. Cho cả session key lẫn field session trong hash key của khách hàng tự động hết hạn khi session hết hạn.

1. **Theo dõi session đang hoạt động (Active Session Tracking)**: Lưu tất cả các session đang hoạt động trong một hash key. Đặt TTL của mỗi session để tự động hết hạn sau một thời gian không hoạt động. Dùng `HLEN` để đếm số session đang hoạt động.

#<a name="field-expiration"></a>

## Hết hạn field (Field expiration) examples

Hết hạn field của hash được hỗ trợ bởi các thư viện client chính thức. Các ví dụ bên dưới
minh họa các lệnh hết hạn field bằng một hash lưu dữ liệu cảm biến (sensor) với
cấu trúc sau:

| Field           | Value |
| :-------------- | :---- |
| `air_quality`   | 256   |
| `battery_level` | 89    |

Vì các field sẽ hết hạn, mỗi ví dụ sẽ tạo lại hash `sensor:sensor1` trước để
nó có thể chạy độc lập.

Đặt TTL 60 giây cho hai field của một hash rồi lấy TTL còn lại của
các field đó:

*Hết hạn field: Đặt TTL tính bằng giây cho từng field của hash bằng HEXPIRE, rồi đọc TTL còn lại bằng HTTL*

**Độ khó (Difficulty):** Trung bình (Intermediate)

**Dựa trên (Builds upon):** set_get_all

**Lệnh (Commands):** DEL, HSET, HEXPIRE, HTTL

**Độ phức tạp (Complexity):**
- DEL: O(N)
- HSET: O(1)
- HEXPIRE: O(N)
- HTTL: O(N)

**Có sẵn trong (Available in):** Redis CLI, C#, Go, Java (Asynchronous - Lettuce), Java (Reactive - Lettuce), Java (Synchronous - Jedis), JavaScript (Node.js), PHP, Python, Ruby, Rust (Asynchronous), Rust (Synchronous)

##### Redis CLI

```
> DEL sensor:sensor1
(integer) 0
> HSET sensor:sensor1 air_quality 256 battery_level 89
(integer) 2
> HEXPIRE sensor:sensor1 60 FIELDS 2 air_quality battery_level
1) (integer) 1
2) (integer) 1
> HTTL sensor:sensor1 FIELDS 2 air_quality battery_level
1) (integer) 60
2) (integer) 60
```

##### C#

```csharp
        db.KeyDelete("sensor:sensor1");
        db.HashSet("sensor:sensor1", [
            new("air_quality", 256),
            new("battery_level", 89)
        ]);

        // Set a TTL of 60 seconds on two fields of the hash.
        ExpireResult[] hexpireResult = db.HashFieldExpire(
            "sensor:sensor1",
            new RedisValue[] { "air_quality", "battery_level" },
            TimeSpan.FromSeconds(60)
        );
        Console.WriteLine(string.Join(", ", hexpireResult));
        // Success, Success

        // Retrieve the remaining TTL for those fields.
        long[] httlResult = db.HashFieldGetTimeToLive(
            "sensor:sensor1",
            new RedisValue[] { "air_quality", "battery_level" }
        );
        Console.WriteLine(httlResult.Length);
        // 2
```

##### Go

```go
	rdb.Del(ctx, "sensor:sensor1")
	rdb.HSet(ctx, "sensor:sensor1", "air_quality", 256, "battery_level", 89)

	// Set a TTL of 60 seconds on two fields of the hash.
	res18, err := rdb.HExpire(ctx, "sensor:sensor1", 60*time.Second,
		"air_quality", "battery_level").Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res18) // >>> [1 1]

	// Retrieve the remaining TTL for those fields (returns one value per field).
	res19, err := rdb.HTTL(ctx, "sensor:sensor1", "air_quality", "battery_level").Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(len(res19)) // >>> 2
```

##### Java (Asynchronous - Lettuce)

```java
            Map<String, String> sensor1 = new HashMap<>();
            sensor1.put("air_quality", "256");
            sensor1.put("battery_level", "89");

            CompletableFuture<Void> hExpire = asyncCommands.del("sensor:sensor1")
                    .thenCompose(delRes -> asyncCommands.hset("sensor:sensor1", sensor1))
                    // Set a TTL of 60 seconds on two fields of the hash.
                    .thenCompose(hsetRes -> asyncCommands.hexpire("sensor:sensor1", 60, "air_quality", "battery_level"))
                    .thenCompose(res15 -> {
                        System.out.println(res15); // >>> [1, 1]
                        // Retrieve the remaining TTL for those fields.
                        return asyncCommands.httl("sensor:sensor1", "air_quality", "battery_level");
                    })
                    .thenAccept(res16 -> System.out.println(res16.size()))
                    // >>> 2
                    .toCompletableFuture();
```

##### Java (Reactive - Lettuce)

```java
            Map<String, String> sensor1 = new HashMap<>();
            sensor1.put("air_quality", "256");
            sensor1.put("battery_level", "89");

            // Set a TTL of 60 seconds on two fields of the hash.
            Mono<List<Long>> hExpire = reactiveCommands.del("sensor:sensor1")
                    .then(reactiveCommands.hset("sensor:sensor1", sensor1))
                    .then(reactiveCommands.hexpire("sensor:sensor1", 60, "air_quality", "battery_level").collectList())
                    .doOnNext(result -> {
                        System.out.println(result);
                        // >>> [1, 1]
                    });

            hExpire.block();

            // Retrieve the remaining TTL for those fields.
            Mono<List<Long>> hTtl = reactiveCommands.httl("sensor:sensor1", "air_quality", "battery_level")
                    .collectList().doOnNext(result -> {
                        System.out.println(result.size());
                        // >>> 2
                    });

            hTtl.block();
```

##### Java (Synchronous - Jedis)

```java
      jedis.del("sensor:sensor1");
      Map<String, String> sensor1 = new HashMap<>();
      sensor1.put("air_quality", "256");
      sensor1.put("battery_level", "89");
      jedis.hset("sensor:sensor1", sensor1);

      // Set a TTL of 60 seconds on two fields of the hash.
      List<Long> res15 = jedis.hexpire("sensor:sensor1", 60, "air_quality", "battery_level");
      System.out.println(res15); // [1, 1]

      // Retrieve the remaining TTL for those fields.
      List<Long> res16 = jedis.httl("sensor:sensor1", "air_quality", "battery_level");
      System.out.println(res16.size()); // 2
```

##### JavaScript (Node.js)

```javascript
await client.del('sensor:sensor1')
await client.hSet('sensor:sensor1', { 'air_quality': 256, 'battery_level': 89 })

// Set a TTL of 60 seconds on two fields of the hash.
const res18 = await client.hExpire('sensor:sensor1', ['air_quality', 'battery_level'], 60)
console.log(res18)  // [1, 1]

// Retrieve the remaining TTL for those fields.
const res19 = await client.hTTL('sensor:sensor1', ['air_quality', 'battery_level'])
console.log(res19)  // [60, 60] (or close to 60)
```

##### PHP

```php
        $r->del('sensor:sensor1');
        $r->hmset('sensor:sensor1', [
            'air_quality' => 256,
            'battery_level' => 89,
        ]);

        // Set a TTL of 60 seconds on two fields of the hash.
        $res15 = $r->hexpire('sensor:sensor1', 60, ['air_quality', 'battery_level']);
        echo json_encode($res15) . PHP_EOL;
        // >>> [1,1]

        // Retrieve the remaining TTL for those fields.
        $res16 = $r->httl('sensor:sensor1', ['air_quality', 'battery_level']);
        echo count($res16) . PHP_EOL;
        // >>> 2
```

##### Python

```python
r.delete("sensor:sensor1")
r.hset("sensor:sensor1", mapping={"air_quality": 256, "battery_level": 89})

# Set a TTL of 60 seconds on two fields of the hash.
res18 = r.hexpire("sensor:sensor1", 60, "air_quality", "battery_level")
print(res18)
# >>> [1, 1]

# Retrieve the remaining TTL for those fields.
res19 = r.httl("sensor:sensor1", "air_quality", "battery_level")
print(res19)
# >>> [60, 60]
# (your actual values may be slightly lower)
```

##### Ruby

```ruby
r.del('sensor:sensor1')
r.hset('sensor:sensor1', { 'air_quality' => 256, 'battery_level' => 89 })

# Set a TTL of 60 seconds on two fields of the hash.
res15 = r.hexpire('sensor:sensor1', 60, 'air_quality', 'battery_level')
puts res15.inspect # >>> [1, 1]

# Retrieve the remaining TTL for those fields.
res16 = r.httl('sensor:sensor1', 'air_quality', 'battery_level')
puts res16.inspect # >>> [60, 60]
# (your actual values may be slightly lower)
```

##### Rust (Asynchronous)

```rust
        let _: () = r.del("sensor:sensor1").await.expect("Failed to del");
        let _: () = r.hset_multiple(
            "sensor:sensor1",
            &[("air_quality", "256"), ("battery_level", "89")],
        ).await.expect("Failed to hset");

        // Set a TTL of 60 seconds on two fields of the hash.
        match r.hexpire("sensor:sensor1", 60, redis::ExpireOption::NONE, &["air_quality", "battery_level"]).await {
            Ok(res18) => {
                let res18: Vec<i64> = res18;
                println!("{:?}", res18);    // >>> [1, 1]
            },
            Err(e) => {
                println!("Error setting expiration: {e}");
                return;
            }
        }

        // Retrieve the remaining TTL for those fields.
        match r.httl("sensor:sensor1", &["air_quality", "battery_level"]).await {
            Ok(res19) => {
                let res19: Vec<i64> = res19;
                println!("{}", res19.len());    // >>> 2
            },
            Err(e) => {
                println!("Error getting TTL: {e}");
                return;
            }
        }
```

##### Rust (Synchronous)

```rust
        let _: () = r.del("sensor:sensor1").expect("Failed to del");
        let _: () = r.hset_multiple(
            "sensor:sensor1",
            &[("air_quality", "256"), ("battery_level", "89")],
        ).expect("Failed to hset");

        // Set a TTL of 60 seconds on two fields of the hash.
        match r.hexpire("sensor:sensor1", 60, redis::ExpireOption::NONE, &["air_quality", "battery_level"]) {
            Ok(res18) => {
                let res18: Vec<i64> = res18;
                println!("{:?}", res18);    // >>> [1, 1]
            },
            Err(e) => {
                println!("Error setting expiration: {e}");
                return;
            }
        }

        // Retrieve the remaining TTL for those fields.
        match r.httl("sensor:sensor1", &["air_quality", "battery_level"]) {
            Ok(res19) => {
                let res19: Vec<i64> = res19;
                println!("{}", res19.len());    // >>> 2
            },
            Err(e) => {
                println!("Error getting TTL: {e}");
                return;
            }
        }
```



Đặt TTL của một field trong hash tính bằng mili giây rồi lấy TTL còn lại tính bằng mili giây:

*Hết hạn field: Đặt TTL tính bằng mili giây cho một field của hash bằng HPEXPIRE, rồi đọc TTL còn lại bằng HPTTL*

**Độ khó (Difficulty):** Trung bình (Intermediate)

**Dựa trên (Builds upon):** set_get_all

**Lệnh (Commands):** DEL, HSET, HPEXPIRE, HPTTL

**Độ phức tạp (Complexity):**
- DEL: O(N)
- HSET: O(1)
- HPEXPIRE: O(N)
- HPTTL: O(N)

**Có sẵn trong (Available in):** Redis CLI, C#, Go, Java (Asynchronous - Lettuce), Java (Reactive - Lettuce), Java (Synchronous - Jedis), JavaScript (Node.js), PHP, Python, Ruby, Rust (Asynchronous), Rust (Synchronous)

##### Redis CLI

```
> DEL sensor:sensor1
(integer) 1
> HSET sensor:sensor1 air_quality 256 battery_level 89
(integer) 2
> HPEXPIRE sensor:sensor1 60000 FIELDS 1 air_quality
1) (integer) 1
> HPTTL sensor:sensor1 FIELDS 1 air_quality
1) (integer) 59994
```

##### C#

```csharp
        db.KeyDelete("sensor:sensor1");
        db.HashSet("sensor:sensor1", [
            new("air_quality", 256),
            new("battery_level", 89)
        ]);

        // Set the TTL of the 'air_quality' field, expressed in milliseconds.
        ExpireResult[] hpexpireResult = db.HashFieldExpire(
            "sensor:sensor1",
            new RedisValue[] { "air_quality" },
            TimeSpan.FromMilliseconds(60000)
        );
        Console.WriteLine(string.Join(", ", hpexpireResult));
        // Success

        // Retrieve the remaining TTL.
        long[] hpttlResult = db.HashFieldGetTimeToLive(
            "sensor:sensor1",
            new RedisValue[] { "air_quality" }
        );
        Console.WriteLine(hpttlResult.Length);
        // 1
```

##### Go

```go
	rdb.Del(ctx, "sensor:sensor1")
	rdb.HSet(ctx, "sensor:sensor1", "air_quality", 256, "battery_level", 89)

	// Set the TTL of the 'air_quality' field in milliseconds.
	res20, err := rdb.HPExpire(ctx, "sensor:sensor1", 60000*time.Millisecond,
		"air_quality").Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res20) // >>> [1]

	// Retrieve the remaining TTL in milliseconds (returns one value per field).
	res21, err := rdb.HPTTL(ctx, "sensor:sensor1", "air_quality").Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(len(res21)) // >>> 1
```

##### Java (Asynchronous - Lettuce)

```java
            CompletableFuture<Void> hpExpire = hExpire
                    .thenCompose(prev -> asyncCommands.del("sensor:sensor1"))
                    .thenCompose(delRes -> asyncCommands.hset("sensor:sensor1", sensor1))
                    // Set the TTL of the 'air_quality' field in milliseconds.
                    .thenCompose(hsetRes -> asyncCommands.hpexpire("sensor:sensor1", 60000, "air_quality"))
                    .thenCompose(res17 -> {
                        System.out.println(res17); // >>> [1]
                        // Retrieve the remaining TTL in milliseconds.
                        return asyncCommands.hpttl("sensor:sensor1", "air_quality");
                    })
                    .thenAccept(res18 -> System.out.println(res18.size()))
                    // >>> 1
                    .toCompletableFuture();
```

##### Java (Reactive - Lettuce)

```java
            // Set the TTL of the 'air_quality' field in milliseconds.
            Mono<List<Long>> hpExpire = reactiveCommands.del("sensor:sensor1")
                    .then(reactiveCommands.hset("sensor:sensor1", sensor1))
                    .then(reactiveCommands.hpexpire("sensor:sensor1", 60000, "air_quality").collectList())
                    .doOnNext(result -> {
                        System.out.println(result);
                        // >>> [1]
                    });

            hpExpire.block();

            // Retrieve the remaining TTL in milliseconds.
            Mono<List<Long>> hpTtl = reactiveCommands.hpttl("sensor:sensor1", "air_quality")
                    .collectList().doOnNext(result -> {
                        System.out.println(result.size());
                        // >>> 1
                    });

            hpTtl.block();
```

##### Java (Synchronous - Jedis)

```java
      jedis.del("sensor:sensor1");
      jedis.hset("sensor:sensor1", sensor1);

      // Set the TTL of the 'air_quality' field in milliseconds.
      List<Long> res17 = jedis.hpexpire("sensor:sensor1", 60000, "air_quality");
      System.out.println(res17); // [1]

      // Retrieve the remaining TTL in milliseconds.
      List<Long> res18 = jedis.hpttl("sensor:sensor1", "air_quality");
      System.out.println(res18.size()); // 1
```

##### JavaScript (Node.js)

```javascript
await client.del('sensor:sensor1')
await client.hSet('sensor:sensor1', { 'air_quality': 256, 'battery_level': 89 })

// Set the TTL of the 'air_quality' field in milliseconds.
const res20 = await client.hpExpire('sensor:sensor1', ['air_quality'], 60000)
console.log(res20)  // [1]

// Retrieve the remaining TTL in milliseconds.
const res21 = await client.hpTTL('sensor:sensor1', ['air_quality'])
console.log(res21)  // [59994] (your actual value may vary)
```

##### PHP

```php
        $r->del('sensor:sensor1');
        $r->hmset('sensor:sensor1', [
            'air_quality' => 256,
            'battery_level' => 89,
        ]);

        // Set the TTL of the 'air_quality' field in milliseconds.
        $res17 = $r->hpexpire('sensor:sensor1', 60000, ['air_quality']);
        echo json_encode($res17) . PHP_EOL;
        // >>> [1]

        // Retrieve the remaining TTL in milliseconds.
        $res18 = $r->hpttl('sensor:sensor1', ['air_quality']);
        echo count($res18) . PHP_EOL;
        // >>> 1
```

##### Python

```python
r.delete("sensor:sensor1")
r.hset("sensor:sensor1", mapping={"air_quality": 256, "battery_level": 89})

# Set the TTL of the 'air_quality' field in milliseconds.
res20 = r.hpexpire("sensor:sensor1", 60000, "air_quality")
print(res20)
# >>> [1]

# Retrieve the remaining TTL in milliseconds.
res21 = r.hpttl("sensor:sensor1", "air_quality")
print(res21)
# >>> [59994]
# (your actual value may vary)
```

##### Ruby

```ruby
r.del('sensor:sensor1')
r.hset('sensor:sensor1', { 'air_quality' => 256, 'battery_level' => 89 })

# Set the TTL of the 'air_quality' field in milliseconds.
res17 = r.hpexpire('sensor:sensor1', 60_000, 'air_quality')
puts res17.inspect # >>> [1]

# Retrieve the remaining TTL in milliseconds.
res18 = r.hpttl('sensor:sensor1', 'air_quality')
puts res18.inspect # >>> [59999]
# (your actual value may vary)
```

##### Rust (Asynchronous)

```rust
        let _: () = r.del("sensor:sensor1").await.expect("Failed to del");
        let _: () = r.hset_multiple(
            "sensor:sensor1",
            &[("air_quality", "256"), ("battery_level", "89")],
        ).await.expect("Failed to hset");

        // Set the TTL of the 'air_quality' field in milliseconds.
        match r.hpexpire("sensor:sensor1", 60000, redis::ExpireOption::NONE, &["air_quality"]).await {
            Ok(res20) => {
                let res20: Vec<i64> = res20;
                println!("{:?}", res20);    // >>> [1]
            },
            Err(e) => {
                println!("Error setting expiration: {e}");
                return;
            }
        }

        // Retrieve the remaining TTL in milliseconds.
        match r.hpttl("sensor:sensor1", &["air_quality"]).await {
            Ok(res21) => {
                let res21: Vec<i64> = res21;
                println!("{}", res21.len());    // >>> 1
            },
            Err(e) => {
                println!("Error getting PTTL: {e}");
                return;
            }
        }
```

##### Rust (Synchronous)

```rust
        let _: () = r.del("sensor:sensor1").expect("Failed to del");
        let _: () = r.hset_multiple(
            "sensor:sensor1",
            &[("air_quality", "256"), ("battery_level", "89")],
        ).expect("Failed to hset");

        // Set the TTL of the 'air_quality' field in milliseconds.
        match r.hpexpire("sensor:sensor1", 60000, redis::ExpireOption::NONE, &["air_quality"]) {
            Ok(res20) => {
                let res20: Vec<i64> = res20;
                println!("{:?}", res20);    // >>> [1]
            },
            Err(e) => {
                println!("Error setting expiration: {e}");
                return;
            }
        }

        // Retrieve the remaining TTL in milliseconds.
        match r.hpttl("sensor:sensor1", &["air_quality"]) {
            Ok(res21) => {
                let res21: Vec<i64> = res21;
                println!("{}", res21.len());    // >>> 1
            },
            Err(e) => {
                println!("Error getting PTTL: {e}");
                return;
            }
        }
```



Đặt thời điểm hết hạn của một field trong hash là một timestamp cụ thể rồi lấy lại thời điểm
hết hạn đó (cả hai đều dưới dạng Unix time tính bằng giây):

*Hết hạn field: Đặt một timestamp hết hạn tuyệt đối cho một field của hash bằng HEXPIREAT, rồi đọc lại bằng HEXPIRETIME*

**Độ khó (Difficulty):** Trung bình (Intermediate)

**Dựa trên (Builds upon):** set_get_all

**Lệnh (Commands):** DEL, HSET, HEXPIREAT, HEXPIRETIME

**Độ phức tạp (Complexity):**
- DEL: O(N)
- HSET: O(1)
- HEXPIREAT: O(N)
- HEXPIRETIME: O(N)

**Có sẵn trong (Available in):** Redis CLI, C#, Go, Java (Asynchronous - Lettuce), Java (Reactive - Lettuce), Java (Synchronous - Jedis), JavaScript (Node.js), PHP, Python, Rust (Asynchronous), Rust (Synchronous)

##### Redis CLI

```
> DEL sensor:sensor1
(integer) 1
> HSET sensor:sensor1 air_quality 256 battery_level 89
(integer) 2
# Set the expiration to a Unix time in the future
# (4102444800 is just an example; use a timestamp appropriate for your use case).
> HEXPIREAT sensor:sensor1 4102444800 FIELDS 1 air_quality
1) (integer) 1
> HEXPIRETIME sensor:sensor1 FIELDS 1 air_quality
1) (integer) 4102444800
```

##### C#

```csharp
        db.KeyDelete("sensor:sensor1");
        db.HashSet("sensor:sensor1", [
            new("air_quality", 256),
            new("battery_level", 89)
        ]);

        // Set the expiration of 'air_quality' to a time 24 hours from now.
        ExpireResult[] hexpireAtResult = db.HashFieldExpire(
            "sensor:sensor1",
            new RedisValue[] { "air_quality" },
            DateTime.UtcNow.AddHours(24)
        );
        Console.WriteLine(string.Join(", ", hexpireAtResult));
        // Success

        // Retrieve the expiration time as a Unix timestamp.
        long[] hexpireTimeResult = db.HashFieldGetExpireDateTime(
            "sensor:sensor1",
            new RedisValue[] { "air_quality" }
        );
        Console.WriteLine(hexpireTimeResult.Length);
        // 1
```

##### Go

```go
	rdb.Del(ctx, "sensor:sensor1")
	rdb.HSet(ctx, "sensor:sensor1", "air_quality", 256, "battery_level", 89)

	// Set the expiration of 'air_quality' to a time 24 hours from now.
	res22, err := rdb.HExpireAt(ctx, "sensor:sensor1", time.Now().Add(24*time.Hour),
		"air_quality").Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res22) // >>> [1]

	// Retrieve the expiration time as a Unix timestamp (returns one value per field).
	res23, err := rdb.HExpireTime(ctx, "sensor:sensor1", "air_quality").Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(len(res23)) // >>> 1
```

##### Java (Asynchronous - Lettuce)

```java
            long expireAtSeconds = System.currentTimeMillis() / 1000L + 24 * 60 * 60;
            CompletableFuture<Void> hExpireAt = hpExpire
                    .thenCompose(prev -> asyncCommands.del("sensor:sensor1"))
                    .thenCompose(delRes -> asyncCommands.hset("sensor:sensor1", sensor1))
                    // Set the expiration of 'air_quality' to a Unix time 24 hours from now.
                    .thenCompose(hsetRes -> asyncCommands.hexpireat("sensor:sensor1", expireAtSeconds, "air_quality"))
                    .thenCompose(res19 -> {
                        System.out.println(res19); // >>> [1]
                        // Retrieve the expiration time as a Unix timestamp in seconds.
                        return asyncCommands.hexpiretime("sensor:sensor1", "air_quality");
                    })
                    .thenAccept(res20 -> System.out.println(res20.size()))
                    // >>> 1
                    .toCompletableFuture();
```

##### Java (Reactive - Lettuce)

```java
            long expireAtSeconds = System.currentTimeMillis() / 1000L + 24 * 60 * 60;

            // Set the expiration of 'air_quality' to a Unix time 24 hours from now.
            Mono<List<Long>> hExpireAt = reactiveCommands.del("sensor:sensor1")
                    .then(reactiveCommands.hset("sensor:sensor1", sensor1))
                    .then(reactiveCommands.hexpireat("sensor:sensor1", expireAtSeconds, "air_quality").collectList())
                    .doOnNext(result -> {
                        System.out.println(result);
                        // >>> [1]
                    });

            hExpireAt.block();

            // Retrieve the expiration time as a Unix timestamp in seconds.
            Mono<List<Long>> hExpireTime = reactiveCommands.hexpiretime("sensor:sensor1", "air_quality")
                    .collectList().doOnNext(result -> {
                        System.out.println(result.size());
                        // >>> 1
                    });

            hExpireTime.block();
```

##### Java (Synchronous - Jedis)

```java
      jedis.del("sensor:sensor1");
      jedis.hset("sensor:sensor1", sensor1);

      // Set the expiration of 'air_quality' to a Unix time 24 hours from now.
      long expireAt = System.currentTimeMillis() / 1000L + 24 * 60 * 60;
      List<Long> res19 = jedis.hexpireAt("sensor:sensor1", expireAt, "air_quality");
      System.out.println(res19); // [1]

      // Retrieve the expiration time as a Unix timestamp in seconds.
      List<Long> res20 = jedis.hexpireTime("sensor:sensor1", "air_quality");
      System.out.println(res20.size()); // 1
```

##### JavaScript (Node.js)

```javascript
await client.del('sensor:sensor1')
await client.hSet('sensor:sensor1', { 'air_quality': 256, 'battery_level': 89 })

// Set the expiration of 'air_quality' to a Unix time 24 hours from now.
const expireAt = Math.floor(Date.now() / 1000) + 24 * 60 * 60
const res22 = await client.hExpireAt('sensor:sensor1', ['air_quality'], expireAt)
console.log(res22)  // [1]

// Retrieve the expiration time as a Unix timestamp in seconds.
const res23 = await client.hExpireTime('sensor:sensor1', ['air_quality'])
console.log(res23)  // [1717668041] (your actual value will vary)
```

##### PHP

```php
        $r->del('sensor:sensor1');
        $r->hmset('sensor:sensor1', [
            'air_quality' => 256,
            'battery_level' => 89,
        ]);

        // Set the expiration of 'air_quality' to a Unix time 24 hours from now.
        $res19 = $r->hexpireat('sensor:sensor1', time() + 24 * 60 * 60, ['air_quality']);
        echo json_encode($res19) . PHP_EOL;
        // >>> [1]

        // Retrieve the expiration time as a Unix timestamp in seconds.
        $res20 = $r->hexpiretime('sensor:sensor1', ['air_quality']);
        echo count($res20) . PHP_EOL;
        // >>> 1
```

##### Python

```python
r.delete("sensor:sensor1")
r.hset("sensor:sensor1", mapping={"air_quality": 256, "battery_level": 89})

# Set the expiration of 'air_quality' to a Unix time 24 hours from now.
res22 = r.hexpireat(
    "sensor:sensor1",
    int(time.time()) + 24 * 60 * 60,
    "air_quality",
)
print(res22)
# >>> [1]

# Retrieve the expiration time as a Unix timestamp in seconds.
res23 = r.hexpiretime("sensor:sensor1", "air_quality")
print(res23)
# >>> [1717668041]
# (your actual value will vary)
```

##### Rust (Asynchronous)

```rust
        let _: () = r.del("sensor:sensor1").await.expect("Failed to del");
        let _: () = r.hset_multiple(
            "sensor:sensor1",
            &[("air_quality", "256"), ("battery_level", "89")],
        ).await.expect("Failed to hset");

        // Set the expiration of 'air_quality' to a Unix time 24 hours from now.
        let now = std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .expect("Time went backwards")
            .as_secs() as i64;

        match r.hexpire_at("sensor:sensor1", now + 24 * 60 * 60, redis::ExpireOption::NONE, &["air_quality"]).await {
            Ok(res22) => {
                let res22: Vec<i64> = res22;
                println!("{:?}", res22);    // >>> [1]
            },
            Err(e) => {
                println!("Error setting expiration: {e}");
                return;
            }
        }

        // Retrieve the expiration time as a Unix timestamp in seconds.
        match r.hexpire_time("sensor:sensor1", &["air_quality"]).await {
            Ok(res23) => {
                let res23: Vec<i64> = res23;
                println!("{}", res23.len());    // >>> 1
            },
            Err(e) => {
                println!("Error getting expiration time: {e}");
                return;
            }
        }
```

##### Rust (Synchronous)

```rust
        let _: () = r.del("sensor:sensor1").expect("Failed to del");
        let _: () = r.hset_multiple(
            "sensor:sensor1",
            &[("air_quality", "256"), ("battery_level", "89")],
        ).expect("Failed to hset");

        // Set the expiration of 'air_quality' to a Unix time 24 hours from now.
        let now = std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .expect("Time went backwards")
            .as_secs() as i64;

        match r.hexpire_at("sensor:sensor1", now + 24 * 60 * 60, redis::ExpireOption::NONE, &["air_quality"]) {
            Ok(res22) => {
                let res22: Vec<i64> = res22;
                println!("{:?}", res22);    // >>> [1]
            },
            Err(e) => {
                println!("Error setting expiration: {e}");
                return;
            }
        }

        // Retrieve the expiration time as a Unix timestamp in seconds.
        match r.hexpire_time("sensor:sensor1", &["air_quality"]) {
            Ok(res23) => {
                let res23: Vec<i64> = res23;
                println!("{}", res23.len());    // >>> 1
            },
            Err(e) => {
                println!("Error getting expiration time: {e}");
                return;
            }
        }
```



## Compact hash (Compact hashes)

Redis 8.10 giới thiệu *compact hash*, một cách để giảm bộ nhớ dùng cho các hash có chung tên field. Khi nhiều hash có cùng bố cục (ví dụ, mỗi user một hash, mỗi hash có `name`, `email`, và `age`), Redis có thể lưu tập tên field dùng chung một lần duy nhất và chỉ giữ các value, cùng một tham chiếu nhỏ tới tập đó, trong mỗi key. Đây là một cách mã hóa nội bộ (internal encoding): các lệnh hash hiện có vẫn hoạt động y như trước, với cùng ngữ nghĩa và phản hồi. Hash chỉ đơn giản là dùng ít bộ nhớ hơn.

**Compact hash hoạt động tốt khi:**

- Nhiều key có chung một tập tên field gần như ổn định — kiểu ánh xạ object kinh điển, chẳng hạn mỗi user, đơn hàng, hoặc session một hash. Càng nhiều key dùng chung một tập field, mức tiết kiệm bộ nhớ càng lớn.
- Việc đọc và cập nhật value vẫn nhanh như trên một hash thông thường. Việc đọc bất kỳ field nào (ví dụ, [`HGET`](https://redis.io/docs/latest/commands/hget), [`HGETALL`](https://redis.io/docs/latest/commands/hgetall), hoặc [`HRANDFIELD`](https://redis.io/docs/latest/commands/hrandfield)) và ghi đè value của một field đã có (`HSET key field value` khi `field` đã tồn tại) không bị ảnh hưởng.

**Compact hash không phù hợp khi:**

- **Tên field thay đổi thường xuyên.** Thêm một field mới bằng [`HSET`](https://redis.io/docs/latest/commands/hset) hoặc xóa một field bằng [`HDEL`](https://redis.io/docs/latest/commands/hdel) sẽ tách key khỏi tập tên field dùng chung của nó và xác định lại nó theo tập mới, tạo ra một tập mới nếu bố cục đó chưa từng xuất hiện. Một workload liên tục thêm hoặc xóa tên field sẽ làm mất dần lợi ích này. Cũng lưu ý rằng một khi hash đã trở thành compact hash, nó sẽ giữ nguyên như vậy suốt vòng đời của key: nó di chuyển giữa các tập tên field khi các field thay đổi, nhưng không bao giờ quay lại thành hash thường (plain hash).
- **Tên field là duy nhất hoặc thay đổi rất nhiều theo từng key.** Khi ít được dùng chung, một key sẽ có tập tên field riêng, kèm theo một chút chi phí metadata. Vì vậy, một tập chỉ được vài key dùng có thể tốn nhiều bộ nhớ hơn một hash thường. Compact hash chỉ có lợi khi hàng trăm hoặc hàng nghìn key dùng chung một tập.
- **Hash rất lớn.** Vì một compact hash được truyền đi dưới dạng một blob duy nhất trong quá trình replication và slot migration, các hash key rất lớn có thể làm giảm hoặc triệt tiêu lợi ích.

Có hai cách để bật (opt in) tính năng này.

### Import hàng loạt với HIMPORT (Bulk import with HIMPORT)

Họ lệnh [`HIMPORT`](https://redis.io/docs/latest/commands/himport) cho phép một client import hiệu quả nhiều hash có chung tên field. Bạn khai báo các tên field dùng chung một lần bằng [`HIMPORT PREPARE`](https://redis.io/docs/latest/commands/himport-prepare), sau đó tạo từng key bằng [`HIMPORT SET`](https://redis.io/docs/latest/commands/himport-set) bằng cách chỉ gửi các value của nó. Cách này giảm lưu lượng mạng và khối lượng xử lý cho mỗi lệnh so với việc chạy [`HSET`](https://redis.io/docs/latest/commands/hset) một lần cho mỗi key, và gợi ý Redis lưu các key mới dưới dạng compact hash. Xem [`HIMPORT`](https://redis.io/docs/latest/commands/himport) để biết toàn bộ quy trình và các subcommand của nó.

*Nền tảng: Khai báo các tên field dùng chung một lần bằng HIMPORT PREPARE, rồi tạo từng hash bằng cách chỉ gửi các value của nó với HIMPORT SET*

**Độ khó (Difficulty):** Trung bình (Intermediate)

**Lệnh (Commands):** HIMPORT, HGETALL

**Độ phức tạp (Complexity):**
- HIMPORT: Tùy thuộc vào subcommand
- HGETALL: O(N)

**Có sẵn trong (Available in):** Redis CLI, C#, C#, Go, Java, Java (Asynchronous - Lettuce), Java (Reactive - Lettuce), Java (Synchronous - Jedis), JavaScript (Node.js), JavaScript (Node.js), PHP, Python, Ruby

##### Redis CLI

```
> HIMPORT PREPARE user name email age
OK
> HIMPORT SET user:1 user Alice alice@example.com 34
OK
> HIMPORT SET user:2 user Bob bob@example.com 41
OK
> HIMPORT SET user:3 user Carol carol@example.com 29
OK
> HGETALL user:2
1) "age"
2) "41"
3) "name"
4) "Bob"
5) "email"
6) "bob@example.com"
```

##### C#

```csharp
        // Declare the field names once.
        await using var fields = HashImport.Create("name", "email", "age");

        // Create each hash by sending only its values.
        await db.HashImportAsync("user:1", fields, new RedisValue[] { "Alice", "alice@example.com", "34" });
        await db.HashImportAsync("user:2", fields, new RedisValue[] { "Bob", "bob@example.com", "41" });
        await db.HashImportAsync("user:3", fields, new RedisValue[] { "Carol", "carol@example.com", "29" });

        // The result is an ordinary hash.
        HashEntry[] res1 = await db.HashGetAllAsync("user:2");
        Console.WriteLine(string.Join(", ", res1.Select(h => $"{h.Name}: {h.Value}")));
        // >>> age: 41, name: Bob, email: bob@example.com
```

##### C#

```csharp
        // Declare the field names once.
        using var fields = HashImport.Create("name", "email", "age");

        // Create each hash by sending only its values.
        db.HashImport("user:1", fields, new RedisValue[] { "Alice", "alice@example.com", "34" });
        db.HashImport("user:2", fields, new RedisValue[] { "Bob", "bob@example.com", "41" });
        db.HashImport("user:3", fields, new RedisValue[] { "Carol", "carol@example.com", "29" });

        // The result is an ordinary hash.
        HashEntry[] res1 = db.HashGetAll("user:2");
        Console.WriteLine(string.Join(", ", res1.Select(h => $"{h.Name}: {h.Value}")));
        // >>> age: 41, name: Bob, email: bob@example.com
```

##### C#

```csharp
        // Declare the field names once.
        await using var fields = HashImport.Create("name", "email", "age");

        // Create each hash by sending only its values.
        await db.HashImportAsync("user:1", fields, new RedisValue[] { "Alice", "alice@example.com", "34" });
        await db.HashImportAsync("user:2", fields, new RedisValue[] { "Bob", "bob@example.com", "41" });
        await db.HashImportAsync("user:3", fields, new RedisValue[] { "Carol", "carol@example.com", "29" });

        // The result is an ordinary hash.
        HashEntry[] res1 = await db.HashGetAllAsync("user:2");
        Console.WriteLine(string.Join(", ", res1.Select(h => $"{h.Name}: {h.Value}")));
        // >>> age: 41, name: Bob, email: bob@example.com
```

##### C#

```csharp
        // Declare the field names once.
        using var fields = HashImport.Create("name", "email", "age");

        // Create each hash by sending only its values.
        db.HashImport("user:1", fields, new RedisValue[] { "Alice", "alice@example.com", "34" });
        db.HashImport("user:2", fields, new RedisValue[] { "Bob", "bob@example.com", "41" });
        db.HashImport("user:3", fields, new RedisValue[] { "Carol", "carol@example.com", "29" });

        // The result is an ordinary hash.
        HashEntry[] res1 = db.HashGetAll("user:2");
        Console.WriteLine(string.Join(", ", res1.Select(h => $"{h.Name}: {h.Value}")));
        // >>> age: 41, name: Bob, email: bob@example.com
```

##### Go

```go
	// Declare the field names once.
	if err := rdb.HImportPrepare(ctx, "user", "name", "email", "age").Err(); err != nil {
		panic(err)
	}

	// Create each hash by sending only its values.
	res1, err := rdb.HImportSet(ctx, "user:1", "user", "Alice", "alice@example.com", "34").Result()
	if err != nil {
		panic(err)
	}
	fmt.Println(res1) // >>> OK

	if err := rdb.HImportSet(ctx, "user:2", "user", "Bob", "bob@example.com", "41").Err(); err != nil {
		panic(err)
	}
	if err := rdb.HImportSet(ctx, "user:3", "user", "Carol", "carol@example.com", "29").Err(); err != nil {
		panic(err)
	}

	// The result is an ordinary hash.
	res2, err := rdb.HGetAll(ctx, "user:2").Result()
	if err != nil {
		panic(err)
	}
	fmt.Println(res2) // >>> map[age:41 email:bob@example.com name:Bob]
```

##### Java

```java
            // Declare the field names once.
            try (HashImport<String> fieldset = HashImport.of("name", "email", "age")) {
                // Create each hash by sending only its values.
                String res1 = syncCommands.himportSet("user:1", fieldset, "Alice", "alice@example.com", "34");
                System.out.println(res1); // >>> OK

                syncCommands.himportSet("user:2", fieldset, "Bob", "bob@example.com", "41");
                syncCommands.himportSet("user:3", fieldset, "Carol", "carol@example.com", "29");
            }

            // The result is an ordinary hash.
            Map<String, String> res2 = syncCommands.hgetall("user:2");
            System.out.println(res2);
            // >>> {age=41, name=Bob, email=bob@example.com}
```

##### Java (Asynchronous - Lettuce)

```java
            CompletableFuture<Void> basicOps;

            // Declare the field names once.
            try (HashImport<String> fieldset = HashImport.of("name", "email", "age")) {
                // Create each hash by sending only its values.
                RedisFuture<String> import1 = asyncCommands.himportSet(
                        "user:1", fieldset, "Alice", "alice@example.com", "34");
                asyncCommands.himportSet("user:2", fieldset, "Bob", "bob@example.com", "41");
                asyncCommands.himportSet("user:3", fieldset, "Carol", "carol@example.com", "29");

                basicOps = import1.thenCompose(res1 -> {
                    System.out.println(res1); // >>> OK
                    // The result is an ordinary hash.
                    return asyncCommands.hgetall("user:2");
                }).thenAccept(res2 -> {
                    System.out.println(res2);
                    // >>> {age=41, name=Bob, email=bob@example.com}
                }).toCompletableFuture();
            }

            basicOps.join();
```

##### Java (Reactive - Lettuce)

```java
            // Declare the field names once.
            try (HashImport<String> fieldset = HashImport.of("name", "email", "age")) {
                // Create each hash by sending only its values.
                Mono<String> import1 = reactiveCommands
                    .himportSet("user:1", fieldset, "Alice", "alice@example.com", "34")
                    .doOnNext(res1 -> {
                        System.out.println(res1); // >>> OK
                    });

                Mono<String> import2 = reactiveCommands
                    .himportSet("user:2", fieldset, "Bob", "bob@example.com", "41");
                Mono<String> import3 = reactiveCommands
                    .himportSet("user:3", fieldset, "Carol", "carol@example.com", "29");

                // Subscribe before the fieldset closes.
                Mono.when(import1, import2, import3).block();
            }

            // The result is an ordinary hash.
            Mono<List<KeyValue<String, String>>> getAll = reactiveCommands.hgetall("user:2")
                .collectList()
                .doOnNext(res2 -> {
                    System.out.println(res2);
                    // >>> [KeyValue[age, 41], KeyValue[name, Bob], KeyValue[email, bob@example.com]]
                });

            getAll.block();
```

##### Java (Synchronous - Jedis)

```java
        // Declare the field names once.
        try (HashImport fields = HashImport.of("name", "email", "age")) {
            // Create each hash by sending only its values.
            String res1 = jedis.himportSet("user:1", fields, "Alice", "alice@example.com", "34");
            System.out.println(res1); // >>> OK

            jedis.himportSet("user:2", fields, "Bob", "bob@example.com", "41");
            jedis.himportSet("user:3", fields, "Carol", "carol@example.com", "29");
        }

        // The result is an ordinary hash.
        Map<String, String> res2 = jedis.hgetAll("user:2");
        System.out.println(res2);
        // >>> {name=Bob, age=41, email=bob@example.com}
```

##### JavaScript (Node.js)

```javascript
// Declare the field names once.
await client.hImportPrepare('user', ['name', 'email', 'age']);

// Create each hash by sending only its values.
const res1 = await client.hImportSet('user:1', 'user', ['Alice', 'alice@example.com', '34']);
console.log(res1); // >>> OK

await client.hImportSet('user:2', 'user', ['Bob', 'bob@example.com', '41']);
await client.hImportSet('user:3', 'user', ['Carol', 'carol@example.com', '29']);

// The result is an ordinary hash.
const res2 = await client.hGetAll('user:2');
console.log(res2);
// >>> { age: '41', name: 'Bob', email: 'bob@example.com' }
```

##### JavaScript (Node.js)

```javascript
// Declare the field names once, when you create the client.
const redis = new Redis({
  himportFieldsets: [{ name: 'user', fields: ['name', 'email', 'age'] }],
});

// Create each hash by sending only its values.
const res1 = await redis.himport('SET', 'user:1', 'user', 'Alice', 'alice@example.com', '34');
console.log(res1); // >>> OK

await redis.himport('SET', 'user:2', 'user', 'Bob', 'bob@example.com', '41');
await redis.himport('SET', 'user:3', 'user', 'Carol', 'carol@example.com', '29');

// The result is an ordinary hash.
const res2 = await redis.hgetall('user:2');
console.log(res2);
// >>> { age: '41', name: 'Bob', email: 'bob@example.com' }
```

##### JavaScript (Node.js)

```javascript
// Declare the field names once.
await client.hImportPrepare('user', ['name', 'email', 'age']);

// Create each hash by sending only its values.
const res1 = await client.hImportSet('user:1', 'user', ['Alice', 'alice@example.com', '34']);
console.log(res1); // >>> OK

await client.hImportSet('user:2', 'user', ['Bob', 'bob@example.com', '41']);
await client.hImportSet('user:3', 'user', ['Carol', 'carol@example.com', '29']);

// The result is an ordinary hash.
const res2 = await client.hGetAll('user:2');
console.log(res2);
// >>> { age: '41', name: 'Bob', email: 'bob@example.com' }
```

##### JavaScript (Node.js)

```javascript
// Declare the field names once, when you create the client.
const redis = new Redis({
  himportFieldsets: [{ name: 'user', fields: ['name', 'email', 'age'] }],
});

// Create each hash by sending only its values.
const res1 = await redis.himport('SET', 'user:1', 'user', 'Alice', 'alice@example.com', '34');
console.log(res1); // >>> OK

await redis.himport('SET', 'user:2', 'user', 'Bob', 'bob@example.com', '41');
await redis.himport('SET', 'user:3', 'user', 'Carol', 'carol@example.com', '29');

// The result is an ordinary hash.
const res2 = await redis.hgetall('user:2');
console.log(res2);
// >>> { age: '41', name: 'Bob', email: 'bob@example.com' }
```

##### PHP

```php
        // Declare the field names once.
        $r->himport->prepare('user', ['name', 'email', 'age']);

        // Create each hash by sending only its values.
        $res1 = $r->himport->set('user:1', 'user', ['Alice', 'alice@example.com', '34']);
        echo $res1 . PHP_EOL; // >>> OK

        $r->himport->set('user:2', 'user', ['Bob', 'bob@example.com', '41']);
        $r->himport->set('user:3', 'user', ['Carol', 'carol@example.com', '29']);

        // The result is an ordinary hash.
        $res2 = $r->hgetall('user:2');
        echo json_encode($res2) . PHP_EOL;
        // >>> {"age":"41","name":"Bob","email":"bob@example.com"}
```

##### Python

```python
# Declare the field names once.
r.himport_prepare("user", ["name", "email", "age"])

# Create each hash by sending only its values.
res1 = r.himport_set("user:1", "user", ["Alice", "alice@example.com", "34"])
print(res1)
# >>> True

r.himport_set("user:2", "user", ["Bob", "bob@example.com", "41"])
r.himport_set("user:3", "user", ["Carol", "carol@example.com", "29"])

# The result is an ordinary hash.
res2 = r.hgetall("user:2")
print(res2)
# >>> {'age': '41', 'name': 'Bob', 'email': 'bob@example.com'}
```

##### Ruby

```ruby
# Declare the field names once.
r.himport_prepare('user', %w[name email age])

# Create each hash by sending only its values.
res1 = r.himport_set('user:1', 'user', ['Alice', 'alice@example.com', '34'])
puts res1 # >>> OK

r.himport_set('user:2', 'user', ['Bob', 'bob@example.com', '41'])
r.himport_set('user:3', 'user', ['Carol', 'carol@example.com', '29'])

# The result is an ordinary hash.
res2 = r.hgetall('user:2')
puts res2.inspect
# >>> {"age" => "41", "name" => "Bob", "email" => "bob@example.com"}
```



Mỗi key bạn tạo theo cách này là một hash bình thường. Lưu ý rằng:

- [`HIMPORT SET`](https://redis.io/docs/latest/commands/himport-set) thay thế một key đã có thay vì gộp (merge) vào nó.
- [`HGETALL`](https://redis.io/docs/latest/commands/hgetall) có thể trả về các field theo thứ tự khác với thứ tự bạn đã khai báo.

Một fieldset thuộc về kết nối đã khai báo nó. Các thư viện client hỗ trợ `HIMPORT` sẽ theo dõi các fieldset của bạn và khai báo lại chúng trên mỗi kết nối mà chúng dùng, bao gồm các kết nối trong pool, các lần kết nối lại, và các node của cluster, nên bạn không phải tự quản lý việc này. Một số client, như Jedis, Lettuce, và StackExchange.Redis, không có phương thức prepare: bạn tạo một object fieldset và client sẽ khai báo nó khi được dùng lần đầu. Hầu hết các thư viện client đánh dấu việc hỗ trợ `HIMPORT` là thử nghiệm (experimental), nên API của nó có thể thay đổi, và một số client không hỗ trợ nó bên trong transaction.

Để import nhiều hash, hãy gửi các lệnh import trong một [pipeline](https://redis.io/docs/latest/develop/using-commands/pipelining) để chúng dùng chung các round trip mạng thay vì lần lượt chờ từng phản hồi:

*Nạp dữ liệu hàng loạt (bulk ingestion): Pipeline nhiều lệnh HIMPORT SET dùng chung một fieldset để nạp một lượng lớn hash với ít round trip mạng*

**Độ khó (Difficulty):** Trung bình (Intermediate)

**Dựa trên (Builds upon):** himport_basic

**Lệnh (Commands):** HIMPORT, HGET

**Độ phức tạp (Complexity):**
- HIMPORT: Tùy thuộc vào subcommand
- HGET: O(1)

**Có sẵn trong (Available in):** Redis CLI, C#, C#, Go, Java, Java (Asynchronous - Lettuce), Java (Reactive - Lettuce), Java (Synchronous - Jedis), JavaScript (Node.js), JavaScript (Node.js), PHP, Python, Ruby

##### Redis CLI

```
> HIMPORT PREPARE user name email age
OK
> HIMPORT SET user:1000 user user1000 user1000@example.com 20
OK
> HGET user:1000 email
"user1000@example.com"
```

##### C#

```csharp
        await using var bulkFields = HashImport.Create("name", "email", "age");

        // Start many imports without awaiting each one, so they share round trips.
        var imports = new List<Task>();

        for (int i = 1; i <= 1000; i++)
        {
            imports.Add(db.HashImportAsync(
                $"user:{i}",
                bulkFields,
                new RedisValue[] { $"user{i}", $"user{i}@example.com", (20 + i % 50).ToString() }
            ));
        }

        await Task.WhenAll(imports);

        int res2 = imports.Count(t => t.Status == TaskStatus.RanToCompletion);
        Console.WriteLine(res2);    // >>> 1000

        RedisValue res3 = await db.HashGetAsync("user:1000", "email");
        Console.WriteLine(res3);    // >>> user1000@example.com
```

##### C#

```csharp
        using var bulkFields = HashImport.Create("name", "email", "age");

        // Queue many imports and send them in one round trip.
        var batch = db.CreateBatch();
        var imports = new List<Task>();

        for (int i = 1; i <= 1000; i++)
        {
            imports.Add(batch.HashImportAsync(
                $"user:{i}",
                bulkFields,
                new RedisValue[] { $"user{i}", $"user{i}@example.com", (20 + i % 50).ToString() }
            ));
        }

        batch.Execute();
        Task.WaitAll(imports.ToArray());

        int res2 = imports.Count(t => t.Status == TaskStatus.RanToCompletion);
        Console.WriteLine(res2);    // >>> 1000

        RedisValue res3 = db.HashGet("user:1000", "email");
        Console.WriteLine(res3);    // >>> user1000@example.com
```

##### C#

```csharp
        await using var bulkFields = HashImport.Create("name", "email", "age");

        // Start many imports without awaiting each one, so they share round trips.
        var imports = new List<Task>();

        for (int i = 1; i <= 1000; i++)
        {
            imports.Add(db.HashImportAsync(
                $"user:{i}",
                bulkFields,
                new RedisValue[] { $"user{i}", $"user{i}@example.com", (20 + i % 50).ToString() }
            ));
        }

        await Task.WhenAll(imports);

        int res2 = imports.Count(t => t.Status == TaskStatus.RanToCompletion);
        Console.WriteLine(res2);    // >>> 1000

        RedisValue res3 = await db.HashGetAsync("user:1000", "email");
        Console.WriteLine(res3);    // >>> user1000@example.com
```

##### C#

```csharp
        using var bulkFields = HashImport.Create("name", "email", "age");

        // Queue many imports and send them in one round trip.
        var batch = db.CreateBatch();
        var imports = new List<Task>();

        for (int i = 1; i <= 1000; i++)
        {
            imports.Add(batch.HashImportAsync(
                $"user:{i}",
                bulkFields,
                new RedisValue[] { $"user{i}", $"user{i}@example.com", (20 + i % 50).ToString() }
            ));
        }

        batch.Execute();
        Task.WaitAll(imports.ToArray());

        int res2 = imports.Count(t => t.Status == TaskStatus.RanToCompletion);
        Console.WriteLine(res2);    // >>> 1000

        RedisValue res3 = db.HashGet("user:1000", "email");
        Console.WriteLine(res3);    // >>> user1000@example.com
```

##### Go

```go
	if err := rdb.HImportPrepare(ctx, "user", "name", "email", "age").Err(); err != nil {
		panic(err)
	}

	// Queue many imports and send them in one round trip.
	pipe := rdb.Pipeline()
	for i := 1; i <= 1000; i++ {
		pipe.HImportSet(ctx, fmt.Sprintf("user:%d", i), "user",
			fmt.Sprintf("user%d", i), fmt.Sprintf("user%d@example.com", i), fmt.Sprint(20+i%50))
	}
	res3, err := pipe.Exec(ctx)
	if err != nil {
		panic(err)
	}

	allOK := true
	for _, cmd := range res3 {
		if cmd.Err() != nil {
			allOK = false
		}
	}
	fmt.Println(len(res3), allOK) // >>> 1000 true

	res4, err := rdb.HGet(ctx, "user:1000", "email").Result()
	if err != nil {
		panic(err)
	}
	fmt.Println(res4) // >>> user1000@example.com
```

##### Java

```java
            List<String> res3 = new ArrayList<>();

            // The sync API waits for each reply, so these imports aren't pipelined.
            // To pipeline them, use the async or reactive API.
            try (HashImport<String> fieldset = HashImport.of("name", "email", "age")) {
                for (int i = 1; i <= 1000; i++) {
                    res3.add(syncCommands.himportSet("user:" + i, fieldset,
                            "user" + i, "user" + i + "@example.com", String.valueOf(20 + i % 50)));
                }
            }

            boolean allOk = res3.stream().allMatch("OK"::equals);
            System.out.println(res3.size() + " " + allOk); // >>> 1000 true

            String res4 = syncCommands.hget("user:1000", "email");
            System.out.println(res4); // >>> user1000@example.com
```

##### Java (Asynchronous - Lettuce)

```java
            List<RedisFuture<String>> res3 = new ArrayList<>();

            // Issue many imports without waiting for each reply.
            try (HashImport<String> fieldset = HashImport.of("name", "email", "age")) {
                for (int i = 1; i <= 1000; i++) {
                    res3.add(asyncCommands.himportSet("user:" + i, fieldset,
                            "user" + i, "user" + i + "@example.com", String.valueOf(20 + i % 50)));
                }
            }

            LettuceFutures.awaitAll(5, TimeUnit.SECONDS, res3.toArray(new RedisFuture[0]));

            boolean allOk = res3.stream().allMatch(f -> "OK".equals(f.toCompletableFuture().join()));
            System.out.println(res3.size() + " " + allOk); // >>> 1000 true

            CompletableFuture<Void> getEmail = asyncCommands.hget("user:1000", "email")
                .thenAccept(res4 -> {
                    System.out.println(res4); // >>> user1000@example.com
                })
                .toCompletableFuture();

            getEmail.join();
```

##### Java (Reactive - Lettuce)

```java
            List<String> res3;

            // Issue many imports without waiting for each reply.
            try (HashImport<String> fieldset = HashImport.of("name", "email", "age")) {
                res3 = Flux.range(1, 1000)
                    .flatMap(i -> reactiveCommands.himportSet("user:" + i, fieldset,
                            "user" + i, "user" + i + "@example.com", String.valueOf(20 + i % 50)))
                    .collectList()
                    .block();
            }

            boolean allOk = res3.stream().allMatch("OK"::equals);
            System.out.println(res3.size() + " " + allOk); // >>> 1000 true

            Mono<String> getEmail = reactiveCommands.hget("user:1000", "email").doOnNext(res4 -> {
                System.out.println(res4); // >>> user1000@example.com
            });

            getEmail.block();
```

##### Java (Synchronous - Jedis)

```java
        List<Response<String>> res3 = new ArrayList<>();

        // Queue many imports and send them in one round trip.
        try (HashImport fields = HashImport.of("name", "email", "age");
                Pipeline pipeline = jedis.pipelined()) {
            for (int i = 1; i <= 1000; i++) {
                res3.add(pipeline.himportSet("user:" + i, fields,
                        "user" + i, "user" + i + "@example.com", String.valueOf(20 + i % 50)));
            }
            pipeline.sync();
        }

        boolean allOk = res3.stream().allMatch(r -> "OK".equals(r.get()));
        System.out.println(res3.size() + " " + allOk); // >>> 1000 true

        String res4 = jedis.hget("user:1000", "email");
        System.out.println(res4); // >>> user1000@example.com
```

##### JavaScript (Node.js)

```javascript
await client.hImportPrepare('user', ['name', 'email', 'age']);

// Commands issued together are automatically pipelined.
const imports = [];
for (let i = 1; i <= 1000; i++) {
  imports.push(
    client.hImportSet(`user:${i}`, 'user', [`user${i}`, `user${i}@example.com`, String(20 + i % 50)])
  );
}
const res3 = await Promise.all(imports);

console.log(res3.length, res3.every((r) => r === 'OK'));
// >>> 1000 true

const res4 = await client.hGet('user:1000', 'email');
console.log(res4); // >>> user1000@example.com
```

##### JavaScript (Node.js)

```javascript
const redis = new Redis({
  himportFieldsets: [{ name: 'user', fields: ['name', 'email', 'age'] }],
});

// Queue many imports and send them in one round trip.
const pipeline = redis.pipeline();
for (let i = 1; i <= 1000; i++) {
  pipeline.himport('SET', `user:${i}`, 'user', `user${i}`, `user${i}@example.com`, String(20 + i % 50));
}
const res3 = await pipeline.exec();

console.log(res3.length, res3.every(([err]) => err === null));
// >>> 1000 true

const res4 = await redis.hget('user:1000', 'email');
console.log(res4); // >>> user1000@example.com
```

##### JavaScript (Node.js)

```javascript
await client.hImportPrepare('user', ['name', 'email', 'age']);

// Commands issued together are automatically pipelined.
const imports = [];
for (let i = 1; i <= 1000; i++) {
  imports.push(
    client.hImportSet(`user:${i}`, 'user', [`user${i}`, `user${i}@example.com`, String(20 + i % 50)])
  );
}
const res3 = await Promise.all(imports);

console.log(res3.length, res3.every((r) => r === 'OK'));
// >>> 1000 true

const res4 = await client.hGet('user:1000', 'email');
console.log(res4); // >>> user1000@example.com
```

##### JavaScript (Node.js)

```javascript
const redis = new Redis({
  himportFieldsets: [{ name: 'user', fields: ['name', 'email', 'age'] }],
});

// Queue many imports and send them in one round trip.
const pipeline = redis.pipeline();
for (let i = 1; i <= 1000; i++) {
  pipeline.himport('SET', `user:${i}`, 'user', `user${i}`, `user${i}@example.com`, String(20 + i % 50));
}
const res3 = await pipeline.exec();

console.log(res3.length, res3.every(([err]) => err === null));
// >>> 1000 true

const res4 = await redis.hget('user:1000', 'email');
console.log(res4); // >>> user1000@example.com
```

##### PHP

```php
        // Queue many imports and send them in one round trip. The fieldset is
        // declared inside the pipeline so it runs on the same connection.
        $replies = $r->pipeline(function ($pipe) {
            $pipe->himport('PREPARE', 'user', ['name', 'email', 'age']);

            for ($i = 1; $i <= 1000; $i++) {
                $pipe->himport('SET', "user:$i", 'user', ["user$i", "user$i@example.com", (string) (20 + $i % 50)]);
            }
        });

        // The first reply is for PREPARE.
        $res3 = array_slice($replies, 1);
        $allOk = true;
        foreach ($res3 as $reply) {
            $allOk = $allOk && $reply == 'OK';
        }
        echo count($res3) . ' ' . var_export($allOk, true) . PHP_EOL; // >>> 1000 true

        $res4 = $r->hget('user:1000', 'email');
        echo $res4 . PHP_EOL; // >>> user1000@example.com
```

##### Python

```python
r.himport_prepare("user", ["name", "email", "age"])

# Queue many imports and send them in one round trip.
with r.pipeline(transaction=False) as pipe:
    for i in range(1, 1001):
        pipe.himport_set(
            f"user:{i}", "user", [f"user{i}", f"user{i}@example.com", str(20 + i % 50)]
        )
    res3 = pipe.execute()

print(len(res3), all(res3))
# >>> 1000 True

res4 = r.hget("user:1000", "email")
print(res4)
# >>> user1000@example.com
```

##### Ruby

```ruby
# Queue many imports and send them in one round trip. The fieldset is
# declared inside the pipeline so it runs on the same connection.
replies = r.pipelined do |pipe|
  pipe.himport_prepare('user', %w[name email age])
  (1..1000).each do |i|
    pipe.himport_set("user:#{i}", 'user', ["user#{i}", "user#{i}@example.com", (20 + i % 50).to_s])
  end
end

# The first reply is for PREPARE.
res3 = replies.drop(1)
puts "#{res3.length} #{res3.all?('OK')}" # >>> 1000 true

res4 = r.hget('user:1000', 'email')
puts res4 # >>> user1000@example.com
```



### Chuyển đổi tự động (Automatic conversion)

Với các workload không thể áp dụng lệnh mới, Redis có thể tự chuyển đổi các hash đủ điều kiện thành compact hash mà không cần thay đổi code. Tất cả các thiết lập sau mặc định là `0` (tắt) và có thể thay đổi lúc runtime bằng [`CONFIG SET`](https://redis.io/docs/latest/commands/config-set).

#### Trên đường ghi (On the write path)

Các thiết lập này chuyển đổi các hash được tạo hoặc sửa đổi bởi các lệnh thông thường (chẳng hạn [`HSET`](https://redis.io/docs/latest/commands/hset)), nên một ứng dụng hiện có sẽ được tiết kiệm bộ nhớ mà không cần thay đổi code. Chúng có hiệu lực một cách lười biếng (lazily), giống như `hash-max-listpack-entries`: một thay đổi chỉ áp dụng cho một hash cụ thể vào lần ghi tiếp theo của nó, chứ không phải ngay lúc bạn chạy [`CONFIG SET`](https://redis.io/docs/latest/commands/config-set).

| Cấu hình | Ý nghĩa |
|---|---|
| `hash-min-template-entries` | Số field tối thiểu để một hash được tự động chuyển thành compact hash vào lần ghi tiếp theo. `0` tắt chuyển đổi tự động. |
| `hash-max-template-entries` | Số field tối đa cho chuyển đổi tự động: một hash rộng hơn mức này sẽ được giữ là hash thường (giữ các hash rất rộng ngoài registry dùng chung). `0` nghĩa là không có giới hạn trên. |

Một hash sẽ không được chuyển đổi nếu nó dùng [hết hạn field](#field-expiration), ngay cả khi số field của nó đạt mức tối thiểu. Sau khi một hash đã được chuyển đổi, nó vẫn là compact hash ngay cả khi số field của nó sau này tăng vượt quá `hash-max-template-entries`.

#### Khi nạp RDB (On RDB load)

Một RDB được lưu trước khi có tính năng này chỉ chứa các hash thường. Các cấu hình này cho phép Redis chuyển chúng thành compact hash *trong lúc RDB được nạp*, nhờ đó việc nâng cấp sẽ thu hồi được bộ nhớ mà không phải ghi lại dữ liệu. Chúng chỉ áp dụng cho các RDB không chứa compact hash; một RDB đã chứa compact hash sẽ được nạp nguyên trạng, không có chuyển đổi lúc nạp. Về bản chất, đây là các cấu hình nâng cấp dùng một lần: chúng không còn tác dụng một khi RDB chứa ít nhất một compact hash.

| Cấu hình | Ý nghĩa |
|---|---|
| `hash-rdb-load-min-template-entries` | Số field tối thiểu để chuyển một hash thường thành compact hash trong lúc nạp. `0` tắt chuyển đổi lúc nạp. |
| `hash-rdb-load-max-template-entries` | Số field tối đa cho chuyển đổi lúc nạp. `0` nghĩa là không có giới hạn trên. |
| `hash-rdb-load-template-disassembly-threshold` | Số key tối thiểu phải dùng chung một tập tên field đã chuyển đổi để tập đó được giữ lại. `0` giữ lại mọi tập đã chuyển đổi. |

Ngưỡng tháo dỡ (disassembly threshold) giúp tránh lãng phí bộ nhớ cho các tập tên field chỉ được vài key dùng chung: khi kết thúc quá trình nạp, nếu một tập được dùng bởi ít key hơn ngưỡng, các key đó sẽ được chuyển lại thành hash thường và tập dùng chung sẽ được giải phóng. Nó cũng đóng vai trò như một van an toàn trong quá trình nạp. Redis theo dõi có bao nhiêu tập đã chuyển đổi vẫn còn dưới ngưỡng; một khi đã tạo ít nhất 1,000 tập, nếu hơn một nửa trong số đó vẫn dưới ngưỡng, Redis sẽ cho rằng tập dữ liệu không phù hợp với compact hash và ngừng tạo tập mới giữa chừng quá trình nạp. Từ thời điểm đó, chỉ những hash có tập field khớp với một tập đã được tạo trong lần nạp này mới được chuyển đổi; số còn lại giữ nguyên là hash thường.

## Hiệu năng (Performance)

Hầu hết các lệnh hash của Redis là O(1).

Một số lệnh, như [`HKEYS`](https://redis.io/docs/latest/commands/hkeys), [`HVALS`](https://redis.io/docs/latest/commands/hvals), [`HGETALL`](https://redis.io/docs/latest/commands/hgetall), và hầu hết các lệnh liên quan đến hết hạn, là O(n), với _n_ là số cặp field-value.

## Giới hạn (Limits)

Mỗi hash có thể lưu tới 4,294,967,295 (2^32 - 1) cặp field-value.
Trong thực tế, hash của bạn chỉ bị giới hạn bởi tổng bộ nhớ trên các VM chạy hệ thống Redis của bạn.

## Tìm hiểu thêm (Learn more)

* [Redis Hashes Explained](https://www.youtube.com/watch?v=-KdITaRkQ-U) là một video ngắn, giải thích toàn diện về Redis hash.
* [Redis University's RU101](https://university.redis.com/courses/ru101/) trình bày chi tiết về Redis hash.

# Redis lists

> Nguồn: <https://redis.io/docs/latest/develop/data-types/lists/>
> Giới thiệu về Redis lists

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


### Tóm tắt các lệnh list (List command summary)

**24 lệnh trong nhóm này:**

[Xem tất cả các lệnh list](https://redis.io/commands/?group=list)

| Lệnh | Tóm tắt | Độ phức tạp | Từ phiên bản |
|---------|---------|------------|-------|
| [BLMOVE](https://redis.io/commands/blmove/) | Lấy ra (pop) một phần tử từ một list, đẩy (push) nó vào một list khác và trả về nó. Nếu không có phần tử thì block cho tới khi có. Xóa list nếu phần tử cuối cùng đã bị chuyển đi. | O(1) | 6.2.0 |
| [BLMOVEM](https://redis.io/commands/blmovem/) | Chuyển tối đa (hoặc đúng) một số lượng phần tử từ một list sang list khác và trả về chúng. Nếu không có phần tử thì block cho tới khi có. Xóa list nguồn nếu nó trở nên rỗng. | O(N) với N là số phần tử được chuyển. | 8.10.0 |
| [BLMPOP](https://redis.io/commands/blmpop/) | Lấy ra phần tử đầu tiên từ một trong nhiều list. Nếu không có phần tử thì block cho tới khi có. Xóa list nếu phần tử cuối cùng đã bị lấy ra. | O(N+M) với N là số key được cung cấp... | 7.0.0 |
| [BLPOP](https://redis.io/commands/blpop/) | Xóa và trả về phần tử đầu tiên trong một list. Nếu không có phần tử thì block cho tới khi có. Xóa list nếu phần tử cuối cùng đã bị lấy ra. | O(N) với N là số key được cung cấp. | 2.0.0 |
| [BRPOP](https://redis.io/commands/brpop/) | Xóa và trả về phần tử cuối cùng trong một list. Nếu không có phần tử thì block cho tới khi có. Xóa list nếu phần tử cuối cùng đã bị lấy ra. | O(N) với N là số key được cung cấp. | 2.0.0 |
| [BRPOPLPUSH](https://redis.io/commands/brpoplpush/) | Lấy ra một phần tử từ một list, đẩy nó vào một list khác và trả về nó. Nếu không có phần tử thì block cho tới khi có. Xóa list nếu phần tử cuối cùng đã bị lấy ra. | O(1) | 2.2.0 |
| [LINDEX](https://redis.io/commands/lindex/) | Trả về một phần tử từ một list theo chỉ số của nó. | O(N) với N là số phần tử phải duyệt qua... | 1.0.0 |
| [LINSERT](https://redis.io/commands/linsert/) | Chèn một phần tử vào trước hoặc sau một phần tử khác trong một list. | O(N) với N là số phần tử phải duyệt qua... | 2.2.0 |
| [LLEN](https://redis.io/commands/llen/) | Trả về độ dài của một list. | O(1) | 1.0.0 |
| [LMOVE](https://redis.io/commands/lmove/) | Trả về một phần tử sau khi lấy nó ra khỏi một list và đẩy nó vào một list khác. Xóa list nếu phần tử cuối cùng đã bị chuyển đi. | O(1) | 6.2.0 |

#### Tóm tắt các lệnh list (List command summary) (part 2)

| Lệnh | Tóm tắt | Độ phức tạp | Từ phiên bản |
|---------|---------|------------|-------|
| [LMOVEM](https://redis.io/commands/lmovem/) | Chuyển tối đa (hoặc đúng) một số lượng phần tử từ một list sang list khác và trả về chúng. Xóa list nguồn nếu nó trở nên rỗng. | O(N) với N là số phần tử được chuyển. | 8.10.0 |
| [LMPOP](https://redis.io/commands/lmpop/) | Trả về nhiều phần tử từ một list sau khi xóa chúng. Xóa list nếu phần tử cuối cùng đã bị lấy ra. | O(N+M) với N là số key được cung cấp... | 7.0.0 |
| [LPOP](https://redis.io/commands/lpop/) | Trả về các phần tử đầu tiên trong một list sau khi xóa chúng. Xóa list nếu phần tử cuối cùng đã bị lấy ra. | O(N) với N là số phần tử được trả về | 1.0.0 |
| [LPOS](https://redis.io/commands/lpos/) | Trả về chỉ số của các phần tử khớp trong một list. | O(N) với N là số phần tử trong list... | 6.0.6 |
| [LPUSH](https://redis.io/commands/lpush/) | Thêm một hoặc nhiều phần tử vào đầu một list. Tạo key nếu nó chưa tồn tại. | O(1) cho mỗi phần tử được thêm, nên O(N) để thêm N phần tử... | 1.0.0 |
| [LPUSHX](https://redis.io/commands/lpushx/) | Thêm một hoặc nhiều phần tử vào đầu một list chỉ khi list đó đã tồn tại. | O(1) cho mỗi phần tử được thêm, nên O(N) để thêm N phần tử... | 2.2.0 |
| [LRANGE](https://redis.io/commands/lrange/) | Trả về một dải phần tử từ một list. | O(S+N) với S là khoảng cách của offset bắt đầu... | 1.0.0 |
| [LREM](https://redis.io/commands/lrem/) | Xóa các phần tử khỏi một list. Xóa list nếu phần tử cuối cùng đã bị xóa. | O(N+M) với N là độ dài của list và M... | 1.0.0 |
| [LSET](https://redis.io/commands/lset/) | Đặt giá trị của một phần tử trong list theo chỉ số của nó. | O(N) với N là độ dài của list. Việc đặt... | 1.0.0 |
| [LTRIM](https://redis.io/commands/ltrim/) | Xóa các phần tử ở cả hai đầu của một list. Xóa list nếu tất cả các phần tử đã bị cắt bỏ. | O(N) với N là số phần tử bị xóa... | 1.0.0 |

#### Tóm tắt các lệnh list (List command summary) (part 3)

| Lệnh | Tóm tắt | Độ phức tạp | Từ phiên bản |
|---------|---------|------------|-------|
| [RPOP](https://redis.io/commands/rpop/) | Trả về và xóa các phần tử cuối cùng của một list. Xóa list nếu phần tử cuối cùng đã bị lấy ra. | O(N) với N là số phần tử được trả về | 1.0.0 |
| [RPOPLPUSH](https://redis.io/commands/rpoplpush/) | Trả về phần tử cuối cùng của một list sau khi xóa nó và đẩy nó vào một list khác. Xóa list nếu phần tử cuối cùng đã bị lấy ra. | O(1) | 1.2.0 |
| [RPUSH](https://redis.io/commands/rpush/) | Nối thêm một hoặc nhiều phần tử vào cuối một list. Tạo key nếu nó chưa tồn tại. | O(1) cho mỗi phần tử được thêm, nên O(N) để thêm N phần tử... | 1.0.0 |
| [RPUSHX](https://redis.io/commands/rpushx/) | Nối thêm một phần tử vào cuối một list chỉ khi list đó đã tồn tại. | O(1) cho mỗi phần tử được thêm, nên O(N) để thêm N phần tử... | 2.2.0 |



Redis list là các danh sách liên kết (linked list) chứa các giá trị string.
Redis list thường được dùng để:

* Hiện thực stack và queue.
* Xây dựng cơ chế quản lý hàng đợi cho các hệ thống worker chạy nền (background worker).

## Ví dụ (Examples)

* Dùng list như một queue (vào trước, ra trước - FIFO):

*Pattern queue: Dùng LPUSH để thêm phần tử và RPOP để lấy chúng ra theo thứ tự FIFO khi bạn cần xử lý các phần tử theo đúng thứ tự chúng được thêm vào*

**Dựa trên (Builds upon):** lpush_rpush, lpop_rpop

**Lệnh (Commands):** LPUSH, RPOP

**Độ phức tạp (Complexity):**
- LPUSH: O(1)
- RPOP: O(N)

**Có sẵn trong (Available in):** Redis CLI, C#, C#, Go, Java (Asynchronous - Lettuce), Java (Synchronous - Jedis), JavaScript (Node.js), PHP, Python, Ruby, Rust (Asynchronous), Rust (Synchronous)

##### Redis CLI

```
> LPUSH bikes:repairs bike:1
(integer) 1
> LPUSH bikes:repairs bike:2
(integer) 2
> RPOP bikes:repairs
"bike:1"
> RPOP bikes:repairs
"bike:2"
```

##### C#

```csharp
        long res1 = db.ListLeftPush("bikes:repairs", "bike:1");
        Console.WriteLine(res1);    // >>> 1

        long res2 = db.ListLeftPush("bikes:repairs", "bike:2");
        Console.WriteLine(res2);    // >>> 2

        RedisValue res3 = db.ListRightPop("bikes:repairs");
        Console.WriteLine(res3);    // >>> "bike:1"

        RedisValue res4 = db.ListRightPop("bikes:repairs");
        Console.WriteLine(res4); // >>> "bike:2"
```

##### C#

```csharp
        long res1 = db.ListLeftPush("bikes:repairs", "bike:1");
        Console.WriteLine(res1);    // >>> 1

        long res2 = db.ListLeftPush("bikes:repairs", "bike:2");
        Console.WriteLine(res2);    // >>> 2

        RedisValue res3 = db.ListRightPop("bikes:repairs");
        Console.WriteLine(res3);    // >>> "bike:1"

        RedisValue res4 = db.ListRightPop("bikes:repairs");
        Console.WriteLine(res4); // >>> "bike:2"
```

##### C#

```csharp
        long res1 = db.ListLeftPush("bikes:repairs", "bike:1");
        Console.WriteLine(res1);    // >>> 1

        long res2 = db.ListLeftPush("bikes:repairs", "bike:2");
        Console.WriteLine(res2);    // >>> 2

        RedisValue res3 = db.ListRightPop("bikes:repairs");
        Console.WriteLine(res3);    // >>> "bike:1"

        RedisValue res4 = db.ListRightPop("bikes:repairs");
        Console.WriteLine(res4); // >>> "bike:2"
```

##### C#

```csharp
        long res1 = db.ListLeftPush("bikes:repairs", "bike:1");
        Console.WriteLine(res1);    // >>> 1

        long res2 = db.ListLeftPush("bikes:repairs", "bike:2");
        Console.WriteLine(res2);    // >>> 2

        RedisValue res3 = db.ListRightPop("bikes:repairs");
        Console.WriteLine(res3);    // >>> "bike:1"

        RedisValue res4 = db.ListRightPop("bikes:repairs");
        Console.WriteLine(res4); // >>> "bike:2"
```

##### Go

```go
	res1, err := rdb.LPush(ctx, "bikes:repairs", "bike:1").Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res1) // >>> 1

	res2, err := rdb.LPush(ctx, "bikes:repairs", "bike:2").Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res2) // >>> 2

	res3, err := rdb.RPop(ctx, "bikes:repairs").Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res3) // >>> bike:1

	res4, err := rdb.RPop(ctx, "bikes:repairs").Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res4) // >>> bike:2
```

##### Java (Asynchronous - Lettuce)

```java
            CompletableFuture<Void> queue = asyncCommands.lpush("bikes:repairs", "bike:1").thenCompose(res1 -> {
                System.out.println(res1); // >>> 1

                return asyncCommands.lpush("bikes:repairs", "bike:2");
            }).thenCompose(res2 -> {
                System.out.println(res2); // >>> 2

                return asyncCommands.rpop("bikes:repairs");
            }).thenCompose(res3 -> {
                System.out.println(res3); // >>> bike:1

                return asyncCommands.rpop("bikes:repairs");
            })
                    .thenAccept(System.out::println) // >>> bike:2
                    .toCompletableFuture();
```

##### Java (Synchronous - Jedis)

```java
        long res1 = jedis.lpush("bikes:repairs", "bike:1");
        System.out.println(res1);  // >>> 1

        long res2 = jedis.lpush("bikes:repairs", "bike:2");
        System.out.println(res2);  // >>> 2

        String res3 = jedis.rpop("bikes:repairs");
        System.out.println(res3);  // >>> bike:1

        String res4 = jedis.rpop("bikes:repairs");
        System.out.println(res4); // >>> bike:2
```

##### JavaScript (Node.js)

```javascript
const res1 = await client.lPush('bikes:repairs', 'bike:1');
console.log(res1);  // 1

const res2 = await client.lPush('bikes:repairs', 'bike:2');
console.log(res2);  // 2

const res3 = await client.rPop('bikes:repairs');
console.log(res3);  // bike:1

const res4 = await client.rPop('bikes:repairs');
console.log(res4);  // bike:2
```

##### PHP

```php
        $res1 = $r->lpush('bikes:repairs', 'bike:1');
        echo $res1 . PHP_EOL;
        // >>> 1

        $res2 = $r->lpush('bikes:repairs', 'bike:2');
        echo $res2 . PHP_EOL;
        // >>> 2

        $res3 = $r->rpop('bikes:repairs');
        echo $res3 . PHP_EOL;
        // >>> bike:1

        $res4 = $r->rpop('bikes:repairs');
        echo $res4 . PHP_EOL;
        // >>> bike:2
```

##### Python

```python
res1 = r.lpush("bikes:repairs", "bike:1")
print(res1)  # >>> 1

res2 = r.lpush("bikes:repairs", "bike:2")
print(res2)  # >>> 2

res3 = r.rpop("bikes:repairs")
print(res3)  # >>> bike:1

res4 = r.rpop("bikes:repairs")
print(res4)  # >>> bike:2
```

##### Ruby

```ruby
res1 = r.lpush('bikes:repairs', 'bike:1')
puts res1 # >>> 1

res2 = r.lpush('bikes:repairs', 'bike:2')
puts res2 # >>> 2

res3 = r.rpop('bikes:repairs')
puts res3 # >>> bike:1

res4 = r.rpop('bikes:repairs')
puts res4 # >>> bike:2
```

##### Rust (Asynchronous)

```rust
        if let Ok(res) = r.lpush("bikes:repairs", "bike:1").await {
            let res: usize = res;
            println!("{res}"); // >>> 1
        }

        if let Ok(res) = r.lpush("bikes:repairs", "bike:2").await {
            let res: usize = res;
            println!("{res}"); // >>> 2
        }

        if let Ok(res) = r.rpop("bikes:repairs", None).await {
            let res: Option<String> = res;
            print_optional_string(res.clone()); // >>> bike:1
        }

        if let Ok(res) = r.rpop("bikes:repairs", None).await {
            let res: Option<String> = res;
            print_optional_string(res.clone()); // >>> bike:2
        }
```

##### Rust (Synchronous)

```rust
        if let Ok(res) = r.lpush("bikes:repairs", "bike:1") {
            let res: usize = res;
            println!("{res}"); // >>> 1
        }

        if let Ok(res) = r.lpush("bikes:repairs", "bike:2") {
            let res: usize = res;
            println!("{res}"); // >>> 2
        }

        if let Ok(res) = r.rpop("bikes:repairs", None) {
            let res: Option<String> = res;
            print_optional_string(res.clone()); // >>> bike:1
        }

        if let Ok(res) = r.rpop("bikes:repairs", None) {
            let res: Option<String> = res;
            print_optional_string(res.clone()); // >>> bike:2
        }
```



* Dùng list như một stack (vào trước, ra sau - LIFO):

*Pattern stack: Dùng LPUSH để thêm phần tử và LPOP để lấy chúng ra theo thứ tự LIFO khi bạn cần xử lý các phần tử theo thứ tự ngược lại*

**Dựa trên (Builds upon):** lpush_rpush

**Lệnh (Commands):** LPUSH, LPOP

**Độ phức tạp (Complexity):**
- LPUSH: O(1)
- LPOP: O(N)

**Có sẵn trong (Available in):** Redis CLI, C#, C#, Go, Java (Asynchronous - Lettuce), Java (Synchronous - Jedis), JavaScript (Node.js), PHP, Python, Ruby, Rust (Asynchronous), Rust (Synchronous)

##### Redis CLI

```
> LPUSH bikes:repairs bike:1
(integer) 1
> LPUSH bikes:repairs bike:2
(integer) 2
> LPOP bikes:repairs
"bike:2"
> LPOP bikes:repairs
"bike:1"
```

##### C#

```csharp
        long res5 = db.ListLeftPush("bikes:repairs", "bike:1");
        Console.WriteLine(res5);    // >>> 1

        long res6 = db.ListLeftPush("bikes:repairs", "bike:2");
        Console.WriteLine(res6);    // >>> 2

        RedisValue res7 = db.ListLeftPop("bikes:repairs");
        Console.WriteLine(res7);    // >>> "bike:2"

        RedisValue res8 = db.ListLeftPop("bikes:repairs");
        Console.WriteLine(res8);    // >>> "bike:1"
```

##### C#

```csharp
        long res5 = db.ListLeftPush("bikes:repairs", "bike:1");
        Console.WriteLine(res5);    // >>> 1

        long res6 = db.ListLeftPush("bikes:repairs", "bike:2");
        Console.WriteLine(res6);    // >>> 2

        RedisValue res7 = db.ListLeftPop("bikes:repairs");
        Console.WriteLine(res7);    // >>> "bike:2"

        RedisValue res8 = db.ListLeftPop("bikes:repairs");
        Console.WriteLine(res8);    // >>> "bike:1"
```

##### C#

```csharp
        long res5 = db.ListLeftPush("bikes:repairs", "bike:1");
        Console.WriteLine(res5);    // >>> 1

        long res6 = db.ListLeftPush("bikes:repairs", "bike:2");
        Console.WriteLine(res6);    // >>> 2

        RedisValue res7 = db.ListLeftPop("bikes:repairs");
        Console.WriteLine(res7);    // >>> "bike:2"

        RedisValue res8 = db.ListLeftPop("bikes:repairs");
        Console.WriteLine(res8);    // >>> "bike:1"
```

##### C#

```csharp
        long res5 = db.ListLeftPush("bikes:repairs", "bike:1");
        Console.WriteLine(res5);    // >>> 1

        long res6 = db.ListLeftPush("bikes:repairs", "bike:2");
        Console.WriteLine(res6);    // >>> 2

        RedisValue res7 = db.ListLeftPop("bikes:repairs");
        Console.WriteLine(res7);    // >>> "bike:2"

        RedisValue res8 = db.ListLeftPop("bikes:repairs");
        Console.WriteLine(res8);    // >>> "bike:1"
```

##### Go

```go
	res5, err := rdb.LPush(ctx, "bikes:repairs", "bike:1").Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res5) // >>> 1

	res6, err := rdb.LPush(ctx, "bikes:repairs", "bike:2").Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res6) // >>> 2

	res7, err := rdb.LPop(ctx, "bikes:repairs").Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res7) // >>> bike:2

	res8, err := rdb.LPop(ctx, "bikes:repairs").Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res8) // >>> bike:1
```

##### Java (Asynchronous - Lettuce)

```java
            CompletableFuture<Void> stack = asyncCommands.lpush("bikes:repairs", "bike:1").thenCompose(res4 -> {
                System.out.println(res4); // >>> 1

                return asyncCommands.lpush("bikes:repairs", "bike:2");
            }).thenCompose(res5 -> {
                System.out.println(res5); // >>> 2

                return asyncCommands.lpop("bikes:repairs");
            }).thenCompose(res6 -> {
                System.out.println(res6); // >>> bike:2

                return asyncCommands.lpop("bikes:repairs");
            })
                    .thenAccept(System.out::println) // >>> bike:1
                    .toCompletableFuture();
```

##### Java (Synchronous - Jedis)

```java
        long res5 = jedis.lpush("bikes:repairs", "bike:1");
        System.out.println(res5);  // >>> 1

        long res6 = jedis.lpush("bikes:repairs", "bike:2");
        System.out.println(res6);  // >>> 2

        String res7 = jedis.lpop("bikes:repairs");
        System.out.println(res7);  // >>> bike:2

        String res8 = jedis.lpop("bikes:repairs");
        System.out.println(res8);  // >>> bike:1
```

##### JavaScript (Node.js)

```javascript
const res5 = await client.lPush('bikes:repairs', 'bike:1');
console.log(res5);  // 1

const res6 = await client.lPush('bikes:repairs', 'bike:2');
console.log(res6); // 2

const res7 = await client.lPop('bikes:repairs');
console.log(res7);  // bike:2

const res8 = await client.lPop('bikes:repairs');
console.log(res8);  // bike:1
```

##### PHP

```php
        $res5 = $r->lpush('bikes:repairs', 'bike:1');
        echo $res5 . PHP_EOL;
        // >>> 1

        $res6 = $r->lpush('bikes:repairs', 'bike:2');
        echo $res6 . PHP_EOL;
        // >>> 2

        $res7 = $r->lpop('bikes:repairs');
        echo $res7 . PHP_EOL;
        // >>> bike:2

        $res8 = $r->lpop('bikes:repairs');
        echo $res8 . PHP_EOL;
        // >>> bike:1
```

##### Python

```python
res5 = r.lpush("bikes:repairs", "bike:1")
print(res5)  # >>> 1

res6 = r.lpush("bikes:repairs", "bike:2")
print(res6)  # >>> 2

res7 = r.lpop("bikes:repairs")
print(res7)  # >>> bike:2

res8 = r.lpop("bikes:repairs")
print(res8)  # >>> bike:1
```

##### Ruby

```ruby
res5 = r.lpush('bikes:repairs', 'bike:1')
puts res5 # >>> 1

res6 = r.lpush('bikes:repairs', 'bike:2')
puts res6 # >>> 2

res7 = r.lpop('bikes:repairs')
puts res7 # >>> bike:2

res8 = r.lpop('bikes:repairs')
puts res8 # >>> bike:1
```

##### Rust (Asynchronous)

```rust
        if let Ok(res) = r.lpush("bikes:repairs", "bike:1").await {
            let res: usize = res;
            println!("{res}"); // >>> 1
        }

        if let Ok(res) = r.lpush("bikes:repairs", "bike:2").await {
            let res: usize = res;
            println!("{res}"); // >>> 2
        }

        if let Ok(res) = r.lpop("bikes:repairs", None).await {
            let res: Option<String> = res;
            print_optional_string(res.clone()); // >>> bike:2
        }

        if let Ok(res) = r.lpop("bikes:repairs", None).await {
            let res: Option<String> = res;
            print_optional_string(res.clone()); // >>> bike:1
        }
```

##### Rust (Synchronous)

```rust
        if let Ok(res) = r.lpush("bikes:repairs", "bike:1") {
            let res: usize = res;
            println!("{res}"); // >>> 1
        }

        if let Ok(res) = r.lpush("bikes:repairs", "bike:2") {
            let res: usize = res;
            println!("{res}"); // >>> 2
        }

        if let Ok(res) = r.lpop("bikes:repairs", None) {
            let res: Option<String> = res;
            print_optional_string(res.clone()); // >>> bike:2
        }

        if let Ok(res) = r.lpop("bikes:repairs", None) {
            let res: Option<String> = res;
            print_optional_string(res.clone()); // >>> bike:1
        }
```



* Kiểm tra độ dài của một list:

*Nền tảng: Lấy số phần tử trong một list bằng LLEN để kiểm tra kích thước list*

**Lệnh (Commands):** LLEN

**Độ phức tạp (Complexity):**
- LLEN: O(1)

**Có sẵn trong (Available in):** Redis CLI, C#, C#, Go, Java (Asynchronous - Lettuce), Java (Synchronous - Jedis), JavaScript (Node.js), PHP, Python, Ruby, Rust (Asynchronous), Rust (Synchronous)

##### Redis CLI

```
> LLEN bikes:repairs
(integer) 0
```

##### C#

```csharp
        long res9 = db.ListLength("bikes:repairs");
        Console.WriteLine(res9);    // >>> 0
```

##### C#

```csharp
        long res9 = db.ListLength("bikes:repairs");
        Console.WriteLine(res9);    // >>> 0
```

##### C#

```csharp
        long res9 = db.ListLength("bikes:repairs");
        Console.WriteLine(res9);    // >>> 0
```

##### C#

```csharp
        long res9 = db.ListLength("bikes:repairs");
        Console.WriteLine(res9);    // >>> 0
```

##### Go

```go
	res9, err := rdb.LLen(ctx, "bikes:repairs").Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res9) // >>> 0
```

##### Java (Asynchronous - Lettuce)

```java
            CompletableFuture<Void> llen = asyncCommands.llen("bikes:repairs")
                    .thenAccept(System.out::println) // >>> 0
                    .toCompletableFuture();
```

##### Java (Synchronous - Jedis)

```java
        long res9 = jedis.llen("bikes:repairs");
        System.out.println(res9);  // >>> 0
```

##### JavaScript (Node.js)

```javascript
const res9 = await client.lLen('bikes:repairs');
console.log(res9); // 0
```

##### PHP

```php
        $res9 = $r->llen('bikes:repairs');
        echo $res9 . PHP_EOL;
        // >>> 0
```

##### Python

```python
res9 = r.llen("bikes:repairs")
print(res9)  # >>> 0
```

##### Ruby

```ruby
res9 = r.llen('bikes:repairs')
puts res9 # >>> 0
```

##### Rust (Asynchronous)

```rust
        if let Ok(res) = r.llen("bikes:repairs").await {
            let res: usize = res;
            println!("{res}"); // >>> 0
        }
```

##### Rust (Synchronous)

```rust
        if let Ok(res) = r.llen("bikes:repairs") {
            let res: usize = res;
            println!("{res}"); // >>> 0
        }
```



* Lấy một phần tử ra khỏi một list và đẩy vào list khác một cách nguyên tử (atomically):

*Chuyển nguyên tử: Dùng LMOVE để chuyển phần tử giữa các list trong một thao tác duy nhất khi bạn cần chuyển phần tử mà không có race condition*

**Độ khó (Difficulty):** Trung bình (Intermediate)

**Dựa trên (Builds upon):** lpush_rpush, lpop_rpop

**Lệnh (Commands):** LPUSH, LMOVE, LRANGE

**Độ phức tạp (Complexity):**
- LPUSH: O(1)
- LMOVE: O(1)
- LRANGE: O(S+N)

**Có sẵn trong (Available in):** Redis CLI, C#, C#, Go, Java (Asynchronous - Lettuce), Java (Synchronous - Jedis), JavaScript (Node.js), PHP, Python, Ruby, Rust (Asynchronous), Rust (Synchronous)

##### Redis CLI

```
> LPUSH bikes:repairs bike:1
(integer) 1
> LPUSH bikes:repairs bike:2
(integer) 2
> LMOVE bikes:repairs bikes:finished LEFT LEFT
"bike:2"
> LRANGE bikes:repairs 0 -1
1) "bike:1"
> LRANGE bikes:finished 0 -1
1) "bike:2"
```

##### C#

```csharp
        long res10 = db.ListLeftPush("{bikes}:repairs", "bike:1");
        Console.WriteLine(res10);   // >>> 1

        long res11 = db.ListLeftPush("{bikes}:repairs", "bike:2");
        Console.WriteLine(res11);   // >>> 2

        RedisValue res12 = db.ListMove("{bikes}:repairs", "{bikes}:finished", ListSide.Left, ListSide.Left);
        Console.Write(res12);   // >>> "bike:2"

        RedisValue[] res13 = db.ListRange("{bikes}:repairs", 0, -1);
        Console.WriteLine(string.Join(", ", res13));    // >>> "bike:1"

        RedisValue[] res14 = db.ListRange("{bikes}:finished", 0, -1);
        Console.WriteLine(string.Join(", ", res14));    // >>> "bike:2"
```

##### C#

```csharp
        long res10 = db.ListLeftPush("{bikes}:repairs", "bike:1");
        Console.WriteLine(res10);   // >>> 1

        long res11 = db.ListLeftPush("{bikes}:repairs", "bike:2");
        Console.WriteLine(res11);   // >>> 2

        RedisValue res12 = db.ListMove("{bikes}:repairs", "{bikes}:finished", ListSide.Left, ListSide.Left);
        Console.Write(res12);   // >>> "bike:2"

        RedisValue[] res13 = db.ListRange("{bikes}:repairs", 0, -1);
        Console.WriteLine(string.Join(", ", res13));    // >>> "bike:1"

        RedisValue[] res14 = db.ListRange("{bikes}:finished", 0, -1);
        Console.WriteLine(string.Join(", ", res14));    // >>> "bike:2"
```

##### C#

```csharp
        long res10 = db.ListLeftPush("{bikes}:repairs", "bike:1");
        Console.WriteLine(res10);   // >>> 1

        long res11 = db.ListLeftPush("{bikes}:repairs", "bike:2");
        Console.WriteLine(res11);   // >>> 2

        RedisValue res12 = db.ListMove("{bikes}:repairs", "{bikes}:finished", ListSide.Left, ListSide.Left);
        Console.Write(res12);   // >>> "bike:2"

        RedisValue[] res13 = db.ListRange("{bikes}:repairs", 0, -1);
        Console.WriteLine(string.Join(", ", res13));    // >>> "bike:1"

        RedisValue[] res14 = db.ListRange("{bikes}:finished", 0, -1);
        Console.WriteLine(string.Join(", ", res14));    // >>> "bike:2"
```

##### C#

```csharp
        long res10 = db.ListLeftPush("{bikes}:repairs", "bike:1");
        Console.WriteLine(res10);   // >>> 1

        long res11 = db.ListLeftPush("{bikes}:repairs", "bike:2");
        Console.WriteLine(res11);   // >>> 2

        RedisValue res12 = db.ListMove("{bikes}:repairs", "{bikes}:finished", ListSide.Left, ListSide.Left);
        Console.Write(res12);   // >>> "bike:2"

        RedisValue[] res13 = db.ListRange("{bikes}:repairs", 0, -1);
        Console.WriteLine(string.Join(", ", res13));    // >>> "bike:1"

        RedisValue[] res14 = db.ListRange("{bikes}:finished", 0, -1);
        Console.WriteLine(string.Join(", ", res14));    // >>> "bike:2"
```

##### Go

```go
	res10, err := rdb.LPush(ctx, "bikes:repairs", "bike:1").Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res10) // >>> 1

	res11, err := rdb.LPush(ctx, "bikes:repairs", "bike:2").Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res11) // >>> 2

	res12, err := rdb.LMove(ctx, "bikes:repairs", "bikes:finished", "LEFT", "LEFT").Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res12) // >>> bike:2

	res13, err := rdb.LRange(ctx, "bikes:repairs", 0, -1).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res13) // >>> [bike:1]

	res14, err := rdb.LRange(ctx, "bikes:finished", 0, -1).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res14) // >>> [bike:2]
```

##### Java (Asynchronous - Lettuce)

```java
            CompletableFuture<Void> lmovelrange = asyncCommands.lpush("bikes:repairs", "bike:1").thenCompose(res7 -> {
                System.out.println(res7); // >>> 1

                return asyncCommands.lpush("bikes:repairs", "bike:2");
            }).thenCompose(res8 -> {
                System.out.println(res8); // >>> 2

                return asyncCommands.lmove("bikes:repairs", "bikes:finished", LMoveArgs.Builder.leftLeft());
            }).thenCompose(res9 -> {
                System.out.println(res9); // >>> bike:2

                return asyncCommands.lrange("bikes:repairs", 0, -1);
            }).thenCompose(res10 -> {
                System.out.println(res10); // >>> [bike:1]

                return asyncCommands.lrange("bikes:finished", 0, -1);
            })
                    .thenAccept(System.out::println) // >>> [bike:2]
                    .toCompletableFuture();
```

##### Java (Synchronous - Jedis)

```java
        long res10 = jedis.lpush("bikes:repairs", "bike:1");
        System.out.println(res10);  // >>> 1

        long res11 = jedis.lpush("bikes:repairs", "bike:2");
        System.out.println(res11);  // >>> 2

        String res12 = jedis.lmove("bikes:repairs", "bikes:finished", ListDirection.LEFT, ListDirection.LEFT);
        System.out.println(res12);  // >>> bike:2

        List<String> res13 = jedis.lrange("bikes:repairs", 0, -1);
        System.out.println(res13);  // >>> [bike:1]

        List<String> res14 = jedis.lrange("bikes:finished", 0, -1);
        System.out.println(res14);  // >>> [bike:2]
```

##### JavaScript (Node.js)

```javascript
const res10 = await client.lPush('bikes:repairs', 'bike:1');
console.log(res10);  // 1

const res11 = await client.lPush('bikes:repairs', 'bike:2');
console.log(res11);  // 2

const res12 = await client.lMove('bikes:repairs', 'bikes:finished', 'LEFT', 'LEFT');
console.log(res12);  // 'bike:2'

const res13 = await client.lRange('bikes:repairs', 0, -1);
console.log(res13);  // ['bike:1']

const res14 = await client.lRange('bikes:finished', 0, -1);
console.log(res14);  // ['bike:2']
```

##### PHP

```php
        $res10 = $r->lpush('bikes:repairs', 'bike:1');
        echo $res10 . PHP_EOL;
        // >>> 1

        $res11 = $r->lpush('bikes:repairs', 'bike:2');
        echo $res11 . PHP_EOL;
        // >>> 2

        $res12 = $r->lmove('bikes:repairs', 'bikes:finished', 'LEFT', 'LEFT');
        echo $res12 . PHP_EOL;
        // >>> 'bike:2'

        $res13 = $r->lrange('bikes:repairs', 0, -1);
        echo json_encode($res13) . PHP_EOL;
        // >>> ['bike:1']

        $res14 = $r->lrange('bikes:finished', 0, -1);
        echo json_encode($res14) . PHP_EOL;
        // >>> ['bike:2']
```

##### Python

```python
res10 = r.lpush("bikes:repairs", "bike:1")
print(res10)  # >>> 1

res11 = r.lpush("bikes:repairs", "bike:2")
print(res11)  # >>> 2

res12 = r.lmove("bikes:repairs", "bikes:finished", "LEFT", "LEFT")
print(res12)  # >>> 'bike:2'

res13 = r.lrange("bikes:repairs", 0, -1)
print(res13)  # >>> ['bike:1']

res14 = r.lrange("bikes:finished", 0, -1)
print(res14)  # >>> ['bike:2']
```

##### Ruby

```ruby
res10 = r.lpush('bikes:repairs', 'bike:1')
puts res10 # >>> 1

res11 = r.lpush('bikes:repairs', 'bike:2')
puts res11 # >>> 2

res12 = r.lmove('bikes:repairs', 'bikes:finished', 'LEFT', 'LEFT')
puts res12 # >>> bike:2

res13 = r.lrange('bikes:repairs', 0, -1)
puts res13.inspect # >>> ["bike:1"]

res14 = r.lrange('bikes:finished', 0, -1)
puts res14.inspect # >>> ["bike:2"]
```

##### Rust (Asynchronous)

```rust
        if let Ok(res) = r.lpush("bikes:repairs", "bike:1").await {
            let res: usize = res;
            println!("{res}"); // >>> 1
        }

        if let Ok(res) = r.lpush("bikes:repairs", "bike:2").await {
            let res: usize = res;
            println!("{res}"); // >>> 2
        }

        if let Ok(res) = r
            .lmove(
                "bikes:repairs",
                "bikes:finished",
                Direction::Left,
                Direction::Left,
            )
            .await
        {
            let res: String = res;
            println!("{res}"); // >>> bike:2
        }

        if let Ok(res) = r.lrange("bikes:repairs", 0, -1).await {
            let res: Vec<String> = res;
            println!("{res:?}"); // >>> ["bike:1"]
        }

        if let Ok(res) = r.lrange("bikes:finished", 0, -1).await {
            let res: Vec<String> = res;
            println!("{res:?}"); // >>> ["bike:2"]
        }
```

##### Rust (Synchronous)

```rust
        if let Ok(res) = r.lpush("bikes:repairs", "bike:1") {
            let res: usize = res;
            println!("{res}"); // >>> 1
        }

        if let Ok(res) = r.lpush("bikes:repairs", "bike:2") {
            let res: usize = res;
            println!("{res}"); // >>> 2
        }

        if let Ok(res) = r.lmove(
            "bikes:repairs",
            "bikes:finished",
            Direction::Left,
            Direction::Left,
        ) {
            let res: String = res;
            println!("{res}"); // >>> bike:2
        }

        if let Ok(res) = r.lrange("bikes:repairs", 0, -1) {
            let res: Vec<String> = res;
            println!("{res:?}"); // >>> ["bike:1"]
        }

        if let Ok(res) = r.lrange("bikes:finished", 0, -1) {
            let res: Vec<String> = res;
            println!("{res:?}"); // >>> ["bike:2"]
        }
```



* Để giới hạn độ dài của một list, bạn có thể gọi [`LTRIM`](https://redis.io/docs/latest/commands/ltrim):

*List có giới hạn (capped list): Dùng LTRIM để chỉ giữ lại một dải phần tử cụ thể khi bạn cần duy trì một list có kích thước cố định*

**Độ khó (Difficulty):** Trung bình (Intermediate)

**Dựa trên (Builds upon):** lpush_rpush

**Lệnh (Commands):** DEL, RPUSH, LTRIM, LRANGE

**Độ phức tạp (Complexity):**
- DEL: O(N)
- RPUSH: O(1)
- LTRIM: O(N)
- LRANGE: O(S+N)

**Có sẵn trong (Available in):** Redis CLI, C#, C#, Go, Java (Asynchronous - Lettuce), Java (Synchronous - Jedis), JavaScript (Node.js), PHP, Python, Ruby, Rust (Asynchronous), Rust (Synchronous)

##### Redis CLI

```
> DEL bikes:repairs
(integer) 1
> RPUSH bikes:repairs bike:1 bike:2 bike:3 bike:4 bike:5
(integer) 5
> LTRIM bikes:repairs 0 2
OK
> LRANGE bikes:repairs 0 -1
1) "bike:1"
2) "bike:2"
3) "bike:3"
```

##### C#

```csharp
        long res49 = db.ListLeftPush("bikes:repairs", ["bike:1", "bike:2", "bike:3", "bike:4", "bike:5"]);
        Console.WriteLine(res49);   // >>> 5

        db.ListTrim("bikes:repairs", 0, 2);
        RedisValue[] res50 = db.ListRange("bikes:repairs", 0, -1);
        Console.WriteLine(string.Join(", ", res50));    // >>> "bike:5, bike:4, bike:3"
```

##### C#

```csharp
        db.KeyDelete("bikes:repairs");

        long res49 = db.ListLeftPush("bikes:repairs", ["bike:1", "bike:2", "bike:3", "bike:4", "bike:5"]);
        Console.WriteLine(res49);   // >>> 5

        db.ListTrim("bikes:repairs", 0, 2);
        RedisValue[] res50 = db.ListRange("bikes:repairs", 0, -1);
        Console.WriteLine(string.Join(", ", res50));    // >>> "bike:5, bike:4, bike:3"
```

##### C#

```csharp
        long res49 = db.ListLeftPush("bikes:repairs", ["bike:1", "bike:2", "bike:3", "bike:4", "bike:5"]);
        Console.WriteLine(res49);   // >>> 5

        db.ListTrim("bikes:repairs", 0, 2);
        RedisValue[] res50 = db.ListRange("bikes:repairs", 0, -1);
        Console.WriteLine(string.Join(", ", res50));    // >>> "bike:5, bike:4, bike:3"
```

##### C#

```csharp
        db.KeyDelete("bikes:repairs");

        long res49 = db.ListLeftPush("bikes:repairs", ["bike:1", "bike:2", "bike:3", "bike:4", "bike:5"]);
        Console.WriteLine(res49);   // >>> 5

        db.ListTrim("bikes:repairs", 0, 2);
        RedisValue[] res50 = db.ListRange("bikes:repairs", 0, -1);
        Console.WriteLine(string.Join(", ", res50));    // >>> "bike:5, bike:4, bike:3"
```

##### Go

```go
	rdb.Del(ctx, "bikes:repairs")

	res51, err := rdb.LPush(ctx, "bikes:repairs", "bike:1", "bike:2", "bike:3", "bike:4", "bike:5").Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res51) // >>> 5

	res52, err := rdb.LTrim(ctx, "bikes:repairs", 0, 2).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res52) // >>> OK

	res53, err := rdb.LRange(ctx, "bikes:repairs", 0, -1).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res53) // >>> [bike:5 bike:4 bike:3]
```

##### Java (Asynchronous - Lettuce)

```java
            CompletableFuture<Void> ltrim1 = asyncCommands
                    .lpush("bikes:repairs", "bike:1", "bike:2", "bike:3", "bike:4", "bike:5").thenCompose(res37 -> {
                        System.out.println(res37); // >>> 5

                        return asyncCommands.ltrim("bikes:repairs", 0, 2);
                    }).thenCompose(res38 -> {
                        System.out.println(res38); // >>> OK

                        return asyncCommands.lrange("bikes:repairs", 0, -1);
                    })
                    .thenAccept(System.out::println) // >>> [bike:5, bike:4, bike:3]
                    .toCompletableFuture();
```

##### Java (Synchronous - Jedis)

```java
        jedis.del("bikes:repairs");

        long res48 = jedis.lpush("bikes:repairs", "bike:1", "bike:2", "bike:3", "bike:4", "bike:5");
        System.out.println(res48);  // >>> 5

        String res49 = jedis.ltrim("bikes:repairs", 0, 2);
        System.out.println(res49);  // >>> OK

        List<String> res50 = jedis.lrange("bikes:repairs", 0, -1);
        System.out.println(res50);  // >>> [bike:5, bike:4, bike:3]
```

##### JavaScript (Node.js)

```javascript
await client.del('bikes:repairs');

const res48 = await client.lPush(
  'bikes:repairs', ['bike:1', 'bike:2', 'bike:3', 'bike:4', 'bike:5']
);
console.log(res48);  // 5

const res49 = await client.lTrim('bikes:repairs', 0, 2);
console.log(res49);  // 'OK'

const res50 = await client.lRange('bikes:repairs', 0, -1);
console.log(res50);  // ['bike:5', 'bike:4', 'bike:3']
```

##### PHP

```php
        $res48 = $r->lpush('bikes:repairs', 'bike:1', 'bike:2', 'bike:3', 'bike:4', 'bike:5');
        echo $res48 . PHP_EOL;
        // >>> 5

        $res49 = $r->ltrim('bikes:repairs', 0, 2);
        echo $res49 . PHP_EOL;
        // >>> True

        $res50 = $r->lrange('bikes:repairs', 0, -1);
        echo json_encode($res50) . PHP_EOL;
        // >>> ['bike:5', 'bike:4', 'bike:3']
```

##### Python

```python
r.delete("bikes:repairs")

res48 = r.lpush("bikes:repairs", "bike:1", "bike:2", "bike:3", "bike:4", "bike:5")
print(res48)  # >>> 5

res49 = r.ltrim("bikes:repairs", 0, 2)
print(res49)  # >>> True

res50 = r.lrange("bikes:repairs", 0, -1)
print(res50)  # >>> ['bike:5', 'bike:4', 'bike:3']
```

##### Ruby

```ruby
r.del('bikes:repairs')

res15 = r.rpush('bikes:repairs', ['bike:1', 'bike:2', 'bike:3', 'bike:4', 'bike:5'])
puts res15 # >>> 5

res16 = r.ltrim('bikes:repairs', 0, 2)
puts res16 # >>> OK

res17 = r.lrange('bikes:repairs', 0, -1)
puts res17.inspect # >>> ["bike:1", "bike:2", "bike:3"]
```

##### Rust (Asynchronous)

```rust
        if let Ok(res) = r.del("bikes:repairs").await {
            let res: usize = res;
            println!("{res}"); // >>> 1
        }

        if let Ok(res) = r
            .rpush(
                "bikes:repairs",
                &["bike:1", "bike:2", "bike:3", "bike:4", "bike:5"],
            )
            .await
        {
            let res: usize = res;
            println!("{res}"); // >>> 5
        }

        if let Ok(res) = r.ltrim("bikes:repairs", 0, 2).await {
            let res: () = res;
            let _ = res;
            println!("OK"); // >>> OK
        }

        if let Ok(res) = r.lrange("bikes:repairs", 0, -1).await {
            let res: Vec<String> = res;
            println!("{res:?}"); // >>> ["bike:1", "bike:2", "bike:3"]
        }
```

##### Rust (Synchronous)

```rust
        if let Ok(res) = r.del("bikes:repairs") {
            let res: usize = res;
            println!("{res}"); // >>> 1
        }

        if let Ok(res) = r.rpush("bikes:repairs", &["bike:1", "bike:2", "bike:3", "bike:4", "bike:5"]) {
            let res: usize = res;
            println!("{res}"); // >>> 5
        }

        if let Ok(res) = r.ltrim("bikes:repairs", 0, 2) {
            let res: () = res;
            let _ = res;
            println!("OK"); // >>> OK
        }

        if let Ok(res) = r.lrange("bikes:repairs", 0, -1) {
            let res: Vec<String> = res;
            println!("{res:?}"); // >>> ["bike:1", "bike:2", "bike:3"]
        }
```



### List là gì? (What are Lists?)

Để giải thích kiểu dữ liệu List, tốt hơn là bắt đầu với một chút lý thuyết,
vì thuật ngữ *List* thường được những người làm công nghệ thông tin dùng
không đúng cách. Ví dụ, "Python List" không phải là thứ mà cái tên có thể gợi ý (Linked
List), mà thực ra là Array (cùng kiểu dữ liệu đó trong
Ruby thực tế được gọi là Array).

Nhìn từ góc độ rất tổng quát, một List chỉ là một dãy các phần tử có thứ tự:
10,20,1,2,3 là một list. Nhưng các thuộc tính của một List được hiện thực bằng
Array rất khác với các thuộc tính của một List được hiện thực bằng
*Linked List*.

Redis list được hiện thực bằng Linked List. Điều này có nghĩa là ngay cả khi bạn có
hàng triệu phần tử trong một list, thao tác thêm một phần tử mới vào
đầu (head) hoặc cuối (tail) của list vẫn được thực hiện *trong thời gian hằng số (constant time)*. Tốc độ thêm một
phần tử mới bằng lệnh [`LPUSH`](https://redis.io/docs/latest/commands/lpush) vào đầu một list có mười
phần tử cũng giống như thêm một phần tử vào đầu một list có 10
triệu phần tử.

Nhược điểm là gì? Truy cập một phần tử *theo chỉ số* rất nhanh với các list
được hiện thực bằng Array (truy cập theo chỉ số trong thời gian hằng số) và không nhanh lắm với
các list được hiện thực bằng linked list (nơi thao tác này cần một lượng
công việc tỷ lệ với chỉ số của phần tử được truy cập).

Redis List được hiện thực bằng linked list vì với một hệ thống database,
việc có thể thêm phần tử vào một list rất dài một cách rất nhanh là cực kỳ quan trọng.
Một ưu điểm mạnh khác, như bạn sẽ thấy ngay sau đây, là Redis List có thể
được giữ ở độ dài cố định trong thời gian hằng số.

Khi việc truy cập nhanh vào phần giữa của một tập hợp phần tử lớn là quan trọng,
có một cấu trúc dữ liệu khác có thể được dùng, gọi là sorted set.
Sorted set được trình bày trong trang hướng dẫn [Sorted sets](https://redis.io/docs/latest/develop/data-types/sorted-sets).

### Những bước đầu tiên với Redis List (First steps with Redis Lists)

Lệnh [`LPUSH`](https://redis.io/docs/latest/commands/lpush) thêm một phần tử mới vào một list, ở
bên trái (ở đầu), trong khi lệnh [`RPUSH`](https://redis.io/docs/latest/commands/rpush) thêm một
phần tử mới vào một list, ở bên phải (ở cuối). Cuối cùng, lệnh
[`LRANGE`](https://redis.io/docs/latest/commands/lrange) trích xuất các dải phần tử từ list:

*Nền tảng: Thêm phần tử vào cả hai đầu của một list bằng LPUSH (trái/đầu) và RPUSH (phải/cuối) để xây dựng các dãy có thứ tự*

**Lệnh (Commands):** DEL, RPUSH, LPUSH, LRANGE

**Độ phức tạp (Complexity):**
- DEL: O(N)
- RPUSH: O(1)
- LPUSH: O(1)
- LRANGE: O(S+N)

**Có sẵn trong (Available in):** Redis CLI, C#, C#, Go, Java (Asynchronous - Lettuce), Java (Synchronous - Jedis), JavaScript (Node.js), PHP, Python, Ruby, Rust (Asynchronous), Rust (Synchronous)

##### Redis CLI

```
> DEL bikes:repairs
(integer) 1
> RPUSH bikes:repairs bike:1
(integer) 1
> RPUSH bikes:repairs bike:2
(integer) 2
> LPUSH bikes:repairs bike:important_bike
(integer) 3
> LRANGE bikes:repairs 0 -1
1) "bike:important_bike"
2) "bike:1"
3) "bike:2"
```

##### C#

```csharp
        long res15 = db.ListRightPush("bikes:repairs", "bike:1");
        Console.WriteLine(res15);   // >>> 1

        long res16 = db.ListRightPush("bikes:repairs", "bike:2");
        Console.WriteLine(res16);   // >>> 2

        long res17 = db.ListLeftPush("bikes:repairs", "bike:important_bike");
        Console.WriteLine(res17);   // >>> 3

        RedisValue[] res18 = db.ListRange("bikes:repairs", 0, -1);
        Console.WriteLine(string.Join(", ", res18));    // >>> "bike:important_bike, bike:1, bike:2"
```

##### C#

```csharp
        db.KeyDelete("bikes:repairs");

        long res15 = db.ListRightPush("bikes:repairs", "bike:1");
        Console.WriteLine(res15);   // >>> 1

        long res16 = db.ListRightPush("bikes:repairs", "bike:2");
        Console.WriteLine(res16);   // >>> 2

        long res17 = db.ListLeftPush("bikes:repairs", "bike:important_bike");
        Console.WriteLine(res17);   // >>> 3

        RedisValue[] res18 = db.ListRange("bikes:repairs", 0, -1);
        Console.WriteLine(string.Join(", ", res18));    // >>> "bike:important_bike, bike:1, bike:2"
```

##### C#

```csharp
        long res15 = db.ListRightPush("bikes:repairs", "bike:1");
        Console.WriteLine(res15);   // >>> 1

        long res16 = db.ListRightPush("bikes:repairs", "bike:2");
        Console.WriteLine(res16);   // >>> 2

        long res17 = db.ListLeftPush("bikes:repairs", "bike:important_bike");
        Console.WriteLine(res17);   // >>> 3

        RedisValue[] res18 = db.ListRange("bikes:repairs", 0, -1);
        Console.WriteLine(string.Join(", ", res18));    // >>> "bike:important_bike, bike:1, bike:2"
```

##### C#

```csharp
        db.KeyDelete("bikes:repairs");

        long res15 = db.ListRightPush("bikes:repairs", "bike:1");
        Console.WriteLine(res15);   // >>> 1

        long res16 = db.ListRightPush("bikes:repairs", "bike:2");
        Console.WriteLine(res16);   // >>> 2

        long res17 = db.ListLeftPush("bikes:repairs", "bike:important_bike");
        Console.WriteLine(res17);   // >>> 3

        RedisValue[] res18 = db.ListRange("bikes:repairs", 0, -1);
        Console.WriteLine(string.Join(", ", res18));    // >>> "bike:important_bike, bike:1, bike:2"
```

##### Go

```go
	rdb.Del(ctx, "bikes:repairs")

	res15, err := rdb.RPush(ctx, "bikes:repairs", "bike:1").Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res15) // >>> 1

	res16, err := rdb.RPush(ctx, "bikes:repairs", "bike:2").Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res16) // >>> 2

	res17, err := rdb.LPush(ctx, "bikes:repairs", "bike:important_bike").Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res17) // >>> 3

	res18, err := rdb.LRange(ctx, "bikes:repairs", 0, -1).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res18) // >>> [bike:important_bike bike:1 bike:2]
```

##### Java (Asynchronous - Lettuce)

```java
            CompletableFuture<Void> lpushrpush = asyncCommands.rpush("bikes:repairs", "bike:1").thenCompose(res11 -> {
                System.out.println(res11); // >>> 1

                return asyncCommands.rpush("bikes:repairs", "bike:2");
            }).thenCompose(res12 -> {
                System.out.println(res12); // >>> 2

                return asyncCommands.lpush("bikes:repairs", "bike:important_bike");
            }).thenCompose(res13 -> {
                System.out.println(res13); // >>> 3

                return asyncCommands.lrange("bikes:repairs", 0, -1);
            })
                    .thenAccept(System.out::println)
                    // >>> [bike:important_bike, bike:1, bike:2]
                    .toCompletableFuture();
```

##### Java (Synchronous - Jedis)

```java
        jedis.del("bikes:repairs");

        long res15 = jedis.rpush("bikes:repairs", "bike:1");
        System.out.println(res15);  // >>> 1

        long res16 = jedis.rpush("bikes:repairs", "bike:2");
        System.out.println(res16);  // >>> 2

        long res17 = jedis.lpush("bikes:repairs", "bike:important_bike");
        System.out.println(res17);  // >>> 3

        List<String> res18 = jedis.lrange("bikes:repairs", 0, -1);
        System.out.println(res18);  // >>> [bike:important_bike, bike:1, bike:2]
```

##### JavaScript (Node.js)

```javascript
await client.del('bikes:repairs');

const res15 = await client.rPush('bikes:repairs', 'bike:1');
console.log(res15);  // 1

const res16 = await client.rPush('bikes:repairs', 'bike:2');
console.log(res16);  // 2

const res17 = await client.lPush('bikes:repairs', 'bike:important_bike');
console.log(res17);  // 3

const res18 = await client.lRange('bikes:repairs', 0, -1);
console.log(res18);  // ['bike:important_bike', 'bike:1', 'bike:2']
```

##### PHP

```php
        $r->del('bikes:repairs');

        $res15 = $r->rpush('bikes:repairs', 'bike:1');
        echo $res15 . PHP_EOL;
        // >>> 1

        $res16 = $r->rpush('bikes:repairs', 'bike:2');
        echo $res16 . PHP_EOL;
        // >>> 2

        $res17 = $r->lpush('bikes:repairs', 'bike:important_bike');
        echo $res17 . PHP_EOL;
        // >>> 3

        $res18 = $r->lrange('bikes:repairs', 0, -1);
        echo json_encode($res18) . PHP_EOL;
        // >>> ['bike:important_bike', 'bike:1', 'bike:2']
```

##### Python

```python
r.delete("bikes:repairs")

res15 = r.rpush("bikes:repairs", "bike:1")
print(res15)  # >>> 1

res16 = r.rpush("bikes:repairs", "bike:2")
print(res16)  # >>> 2

res17 = r.lpush("bikes:repairs", "bike:important_bike")
print(res17)  # >>> 3

res18 = r.lrange("bikes:repairs", 0, -1)
print(res18)  # >>> ['bike:important_bike', 'bike:1', 'bike:2']
```

##### Ruby

```ruby
r.del('bikes:repairs')

res18 = r.rpush('bikes:repairs', 'bike:1')
puts res18 # >>> 1

res19 = r.rpush('bikes:repairs', 'bike:2')
puts res19 # >>> 2

res20 = r.lpush('bikes:repairs', 'bike:important_bike')
puts res20 # >>> 3

res21 = r.lrange('bikes:repairs', 0, -1)
puts res21.inspect # >>> ["bike:important_bike", "bike:1", "bike:2"]
```

##### Rust (Asynchronous)

```rust
        let _: usize = r.del("bikes:repairs").await.unwrap_or(0);

        if let Ok(res) = r.rpush("bikes:repairs", "bike:1").await {
            let res: usize = res;
            println!("{res}"); // >>> 1
        }

        if let Ok(res) = r.rpush("bikes:repairs", "bike:2").await {
            let res: usize = res;
            println!("{res}"); // >>> 2
        }

        if let Ok(res) = r.lpush("bikes:repairs", "bike:important_bike").await {
            let res: usize = res;
            println!("{res}"); // >>> 3
        }

        if let Ok(res) = r.lrange("bikes:repairs", 0, -1).await {
            let res: Vec<String> = res;
            println!("{res:?}"); // >>> ["bike:important_bike", "bike:1", "bike:2"]
        }
```

##### Rust (Synchronous)

```rust
        let _: usize = r.del("bikes:repairs").unwrap_or(0);

        if let Ok(res) = r.rpush("bikes:repairs", "bike:1") {
            let res: usize = res;
            println!("{res}"); // >>> 1
        }

        if let Ok(res) = r.rpush("bikes:repairs", "bike:2") {
            let res: usize = res;
            println!("{res}"); // >>> 2
        }

        if let Ok(res) = r.lpush("bikes:repairs", "bike:important_bike") {
            let res: usize = res;
            println!("{res}"); // >>> 3
        }

        if let Ok(res) = r.lrange("bikes:repairs", 0, -1) {
            let res: Vec<String> = res;
            println!("{res:?}"); // >>> ["bike:important_bike", "bike:1", "bike:2"]
        }
```



Lưu ý rằng [`LRANGE`](https://redis.io/docs/latest/commands/lrange) nhận hai chỉ số, phần tử đầu tiên và phần tử cuối cùng
của dải cần trả về. Cả hai chỉ số đều có thể là số âm, báo cho Redis
bắt đầu đếm từ cuối: vậy -1 là phần tử cuối cùng, -2 là
phần tử áp chót của list, và cứ thế.

Như bạn thấy, [`RPUSH`](https://redis.io/docs/latest/commands/rpush) đã nối các phần tử vào bên phải của list, trong khi
lệnh [`LPUSH`](https://redis.io/docs/latest/commands/lpush) cuối cùng đã nối phần tử vào bên trái.

Cả hai lệnh đều là *lệnh variadic (variadic commands)*, nghĩa là bạn có thể tự do đẩy
nhiều phần tử vào một list trong một lần gọi duy nhất:

*Thao tác variadic: Thêm nhiều phần tử trong một lệnh duy nhất bằng LPUSH hoặc RPUSH khi bạn cần giảm số lần round trip tới server*

**Dựa trên (Builds upon):** lpush_rpush

**Lệnh (Commands):** DEL, RPUSH, LPUSH, LRANGE

**Độ phức tạp (Complexity):**
- DEL: O(N)
- RPUSH: O(1)
- LPUSH: O(1)
- LRANGE: O(S+N)

**Có sẵn trong (Available in):** Redis CLI, C#, C#, Go, Java (Asynchronous - Lettuce), Java (Synchronous - Jedis), JavaScript (Node.js), PHP, Python, Ruby, Rust (Asynchronous), Rust (Synchronous)

##### Redis CLI

```
> DEL bikes:repairs
(integer) 1
> RPUSH bikes:repairs bike:1 bike:2 bike:3
(integer) 3
> LPUSH bikes:repairs bike:important_bike bike:very_important_bike
> LRANGE bikes:repairs 0 -1
1) "bike:very_important_bike"
2) "bike:important_bike"
3) "bike:1"
4) "bike:2"
5) "bike:3"
```

##### C#

```csharp
        long res19 = db.ListRightPush("bikes:repairs", ["bike:1", "bike:2", "bike:3"]);
        Console.WriteLine(res19);   // >>> 3

        long res20 = db.ListLeftPush("bikes:repairs", ["bike:important_bike", "bike:very_important_bike"]);
        Console.WriteLine(res20);   // >>> 5

        RedisValue[] res21 = db.ListRange("bikes:repairs", 0, -1);
        Console.WriteLine(string.Join(", ", res21));
        // >>> "bike:very_important_bike, bike:important_bike, bike:1, bike:2, bike:3"
```

##### C#

```csharp
        db.KeyDelete("bikes:repairs");

        long res19 = db.ListRightPush("bikes:repairs", ["bike:1", "bike:2", "bike:3"]);
        Console.WriteLine(res19);   // >>> 3

        long res20 = db.ListLeftPush("bikes:repairs", ["bike:important_bike", "bike:very_important_bike"]);
        Console.WriteLine(res20);   // >>> 5

        RedisValue[] res21 = db.ListRange("bikes:repairs", 0, -1);
        Console.WriteLine(string.Join(", ", res21));
        // >>> "bike:very_important_bike, bike:important_bike, bike:1, bike:2, bike:3"
```

##### C#

```csharp
        long res19 = db.ListRightPush("bikes:repairs", ["bike:1", "bike:2", "bike:3"]);
        Console.WriteLine(res19);   // >>> 3

        long res20 = db.ListLeftPush("bikes:repairs", ["bike:important_bike", "bike:very_important_bike"]);
        Console.WriteLine(res20);   // >>> 5

        RedisValue[] res21 = db.ListRange("bikes:repairs", 0, -1);
        Console.WriteLine(string.Join(", ", res21));
        // >>> "bike:very_important_bike, bike:important_bike, bike:1, bike:2, bike:3"
```

##### C#

```csharp
        db.KeyDelete("bikes:repairs");

        long res19 = db.ListRightPush("bikes:repairs", ["bike:1", "bike:2", "bike:3"]);
        Console.WriteLine(res19);   // >>> 3

        long res20 = db.ListLeftPush("bikes:repairs", ["bike:important_bike", "bike:very_important_bike"]);
        Console.WriteLine(res20);   // >>> 5

        RedisValue[] res21 = db.ListRange("bikes:repairs", 0, -1);
        Console.WriteLine(string.Join(", ", res21));
        // >>> "bike:very_important_bike, bike:important_bike, bike:1, bike:2, bike:3"
```

##### Go

```go
	rdb.Del(ctx, "bikes:repairs")

	res19, err := rdb.RPush(ctx, "bikes:repairs", "bike:1", "bike:2", "bike:3").Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res19) // >>> 3

	res20, err := rdb.LPush(ctx, "bikes:repairs", "bike:important_bike", "bike:very_important_bike").Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res20) // >>> 5

	res21, err := rdb.LRange(ctx, "bikes:repairs", 0, -1).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res21) // >>> [bike:very_important_bike bike:important_bike bike:1 bike:2 bike:3]
```

##### Java (Asynchronous - Lettuce)

```java
            CompletableFuture<Void> variadic = asyncCommands.rpush("bikes:repairs", "bike:1", "bike:2", "bike:3")
                    .thenCompose(res14 -> {
                        System.out.println(res14); // >>> 3

                        return asyncCommands.lpush("bikes:repairs", "bike:important_bike", "bike:very_important_bike");
                    }).thenCompose(res15 -> {
                        System.out.println(res15); // >>> 5

                        return asyncCommands.lrange("bikes:repairs", 0, -1);
                    })
                    .thenAccept(System.out::println)
                    // >>> [bike:very_important_bike, bike:important_bike, bike:1, bike:2, bike:3]
                    .toCompletableFuture();
```

##### Java (Synchronous - Jedis)

```java
        jedis.del("bikes:repairs");

        long res19 = jedis.rpush("bikes:repairs", "bike:1", "bike:2", "bike:3");
        System.out.println(res19);  // >>> 3

        long res20 = jedis.lpush("bikes:repairs", "bike:important_bike", "bike:very_important_bike");
        System.out.println(res20);  // >>> 5

        List<String> res21 = jedis.lrange("bikes:repairs", 0, -1);
        System.out.println(res21);  // >>> [bike:very_important_bike, bike:important_bike, bike:1, bike:2, bike:3]
```

##### JavaScript (Node.js)

```javascript
await client.del('bikes:repairs');

const res19 = await client.rPush('bikes:repairs', ['bike:1', 'bike:2', 'bike:3']);
console.log(res19);  // 3

const res20 = await client.lPush(
  'bikes:repairs', ['bike:important_bike', 'bike:very_important_bike']
);
console.log(res20);  // 5

const res21 = await client.lRange('bikes:repairs', 0, -1);
console.log(res21);  // ['bike:very_important_bike', 'bike:important_bike', 'bike:1', 'bike:2', 'bike:3']
```

##### PHP

```php
        $r->del('bikes:repairs');

        $res19 = $r->rpush('bikes:repairs', 'bike:1', 'bike:2', 'bike:3');
        echo $res19 . PHP_EOL;
        // >>> 3

        $res20 = $r->lpush('bikes:repairs', 'bike:important_bike', 'bike:very_important_bike');
        echo $res20 . PHP_EOL;
        // >>> 5

        $res21 = $r->lrange('bikes:repairs', 0, -1);
        echo json_encode($res21) . PHP_EOL;
        // >>> ['bike:very_important_bike', 'bike:important_bike', 'bike:1', ...
```

##### Python

```python
r.delete("bikes:repairs")

res19 = r.rpush("bikes:repairs", "bike:1", "bike:2", "bike:3")
print(res19)  # >>> 3

res20 = r.lpush("bikes:repairs", "bike:important_bike", "bike:very_important_bike")
print(res20)  # >>> 5

res21 = r.lrange("bikes:repairs", 0, -1)
print(
    res21
)  # >>> ['bike:very_important_bike', 'bike:important_bike', 'bike:1', ...
```

##### Ruby

```ruby
r.del('bikes:repairs')

res22 = r.rpush('bikes:repairs', ['bike:1', 'bike:2', 'bike:3'])
puts res22 # >>> 3

res23 = r.lpush('bikes:repairs', ['bike:important_bike', 'bike:very_important_bike'])
puts res23 # >>> 5

res24 = r.lrange('bikes:repairs', 0, -1)
puts res24.inspect
# >>> ["bike:very_important_bike", "bike:important_bike", "bike:1", "bike:2", "bike:3"]
```

##### Rust (Asynchronous)

```rust
        let _: usize = r.del("bikes:repairs").await.unwrap_or(0);

        if let Ok(res) = r.rpush("bikes:repairs", &["bike:1", "bike:2", "bike:3"]).await {
            let res: usize = res;
            println!("{res}"); // >>> 3
        }

        if let Ok(res) = r
            .lpush("bikes:repairs", &["bike:important_bike", "bike:very_important_bike"])
            .await
        {
            let res: usize = res;
            println!("{res}"); // >>> 5
        }

        if let Ok(res) = r.lrange("bikes:repairs", 0, -1).await {
            let res: Vec<String> = res;
            println!("{res:?}");
            // >>> ["bike:very_important_bike", "bike:important_bike", "bike:1", "bike:2", "bike:3"]
        }
```

##### Rust (Synchronous)

```rust
        let _: usize = r.del("bikes:repairs").unwrap_or(0);

        if let Ok(res) = r.rpush("bikes:repairs", &["bike:1", "bike:2", "bike:3"]) {
            let res: usize = res;
            println!("{res}"); // >>> 3
        }

        if let Ok(res) = r.lpush("bikes:repairs", &["bike:important_bike", "bike:very_important_bike"]) {
            let res: usize = res;
            println!("{res}"); // >>> 5
        }

        if let Ok(res) = r.lrange("bikes:repairs", 0, -1) {
            let res: Vec<String> = res;
            println!("{res:?}");
            // >>> ["bike:very_important_bike", "bike:important_bike", "bike:1", "bike:2", "bike:3"]
        }
```



Một thao tác quan trọng được định nghĩa trên Redis list là khả năng *lấy phần tử ra (pop elements)*.
Pop phần tử là thao tác vừa lấy phần tử từ list,
vừa loại bỏ nó khỏi list, cùng một lúc. Bạn có thể pop phần tử
từ bên trái và bên phải, tương tự như cách bạn có thể push phần tử vào cả hai phía
của list. Chúng ta sẽ thêm ba phần tử và pop ba phần tử, nên ở cuối
chuỗi lệnh này list sẽ rỗng và không còn phần tử nào để
pop:

*Nền tảng: Xóa và lấy phần tử từ cả hai đầu của một list bằng LPOP (trái/đầu) và RPOP (phải/cuối) để tiêu thụ các phần tử*

**Lệnh (Commands):** DEL, RPUSH, RPOP, LPOP

**Độ phức tạp (Complexity):**
- DEL: O(N)
- RPUSH: O(1)
- RPOP: O(N)
- LPOP: O(N)

**Có sẵn trong (Available in):** Redis CLI, C#, C#, Go, Java (Asynchronous - Lettuce), Java (Synchronous - Jedis), JavaScript (Node.js), PHP, Python, Ruby, Rust (Asynchronous), Rust (Synchronous)

##### Redis CLI

```
> DEL bikes:repairs
(integer) 1
> RPUSH bikes:repairs bike:1 bike:2 bike:3
(integer) 3
> RPOP bikes:repairs
"bike:3"
> LPOP bikes:repairs
"bike:1"
> RPOP bikes:repairs
"bike:2"
> RPOP bikes:repairs
(nil)
```

##### C#

```csharp
        long res22 = db.ListRightPush("bikes:repairs", ["bike:1", "bike:2", "bike:3"]);
        Console.WriteLine(res22);   // >>> 3

        RedisValue res23 = db.ListRightPop("bikes:repairs");
        Console.WriteLine(res23);   // >>> "bike:3"

        RedisValue res24 = db.ListLeftPop("bikes:repairs");
        Console.WriteLine(res24);   // >>> "bike:1"

        RedisValue res25 = db.ListRightPop("bikes:repairs");
        Console.WriteLine(res25);   // >>> "bike:2"

        RedisValue res26 = db.ListRightPop("bikes:repairs");
        Console.WriteLine(res26);   // >>> <Empty string>
```

##### C#

```csharp
        db.KeyDelete("bikes:repairs");

        long res22 = db.ListRightPush("bikes:repairs", ["bike:1", "bike:2", "bike:3"]);
        Console.WriteLine(res22);   // >>> 3

        RedisValue res23 = db.ListRightPop("bikes:repairs");
        Console.WriteLine(res23);   // >>> "bike:3"

        RedisValue res24 = db.ListLeftPop("bikes:repairs");
        Console.WriteLine(res24);   // >>> "bike:1"

        RedisValue res25 = db.ListRightPop("bikes:repairs");
        Console.WriteLine(res25);   // >>> "bike:2"

        RedisValue res26 = db.ListRightPop("bikes:repairs");
        Console.WriteLine(res26);   // >>> <Empty string>
```

##### C#

```csharp
        long res22 = db.ListRightPush("bikes:repairs", ["bike:1", "bike:2", "bike:3"]);
        Console.WriteLine(res22);   // >>> 3

        RedisValue res23 = db.ListRightPop("bikes:repairs");
        Console.WriteLine(res23);   // >>> "bike:3"

        RedisValue res24 = db.ListLeftPop("bikes:repairs");
        Console.WriteLine(res24);   // >>> "bike:1"

        RedisValue res25 = db.ListRightPop("bikes:repairs");
        Console.WriteLine(res25);   // >>> "bike:2"

        RedisValue res26 = db.ListRightPop("bikes:repairs");
        Console.WriteLine(res26);   // >>> <Empty string>
```

##### C#

```csharp
        db.KeyDelete("bikes:repairs");

        long res22 = db.ListRightPush("bikes:repairs", ["bike:1", "bike:2", "bike:3"]);
        Console.WriteLine(res22);   // >>> 3

        RedisValue res23 = db.ListRightPop("bikes:repairs");
        Console.WriteLine(res23);   // >>> "bike:3"

        RedisValue res24 = db.ListLeftPop("bikes:repairs");
        Console.WriteLine(res24);   // >>> "bike:1"

        RedisValue res25 = db.ListRightPop("bikes:repairs");
        Console.WriteLine(res25);   // >>> "bike:2"

        RedisValue res26 = db.ListRightPop("bikes:repairs");
        Console.WriteLine(res26);   // >>> <Empty string>
```

##### Go

```go
	rdb.Del(ctx, "bikes:repairs")

	res22, err := rdb.RPush(ctx, "bikes:repairs", "bike:1", "bike:2", "bike:3").Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res22) // >>> 3

	res23, err := rdb.RPop(ctx, "bikes:repairs").Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res23) // >>> bike:3

	res24, err := rdb.LPop(ctx, "bikes:repairs").Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res24) // >>> bike:1

	res25, err := rdb.RPop(ctx, "bikes:repairs").Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res25) // >>> bike:2

	res26, err := rdb.RPop(ctx, "bikes:repairs").Result()

	if err != nil {
		fmt.Println(err) // >>> redis: nil
	}

	fmt.Println(res26) // >>> <empty string>

```

##### Java (Asynchronous - Lettuce)

```java
            CompletableFuture<Void> lpoprpop = asyncCommands.rpush("bikes:repairs", "bike:1", "bike:2", "bike:3")
                    .thenCompose(res16 -> {
                        System.out.println(res16); // >>> 3

                        return asyncCommands.rpop("bikes:repairs");
                    }).thenCompose(res17 -> {
                        System.out.println(res17); // >>> bike:3

                        return asyncCommands.lpop("bikes:repairs");
                    }).thenCompose(res18 -> {
                        System.out.println(res18); // >>> bike:1

                        return asyncCommands.rpop("bikes:repairs");
                    }).thenCompose(res19 -> {
                        System.out.println(res19); // >>> bike:2

                        return asyncCommands.rpop("bikes:repairs");
                    })
                    .thenAccept(System.out::println) // >>> null
                    .toCompletableFuture();
```

##### Java (Synchronous - Jedis)

```java
        jedis.del("bikes:repairs");

        long res22 = jedis.rpush("bikes:repairs", "bike:1", "bike:2", "bike:3");
        System.out.println(res22);  // >>> 3

        String res23 = jedis.rpop("bikes:repairs");
        System.out.println(res23);  // >>> bike:3

        String res24 = jedis.lpop("bikes:repairs");
        System.out.println(res24);  // >>> bike:1

        String res25 = jedis.rpop("bikes:repairs");
        System.out.println(res25);  // >>> bike:2

        String res26 = jedis.rpop("bikes:repairs");
        System.out.println(res26);  // >>> null
```

##### JavaScript (Node.js)

```javascript
await client.del('bikes:repairs');

const res22 = await client.rPush('bikes:repairs', ['bike:1', 'bike:2', 'bike:3']);
console.log(res22);  // 3

const res23 = await client.rPop('bikes:repairs');
console.log(res23);  // 'bike:3'

const res24 = await client.lPop('bikes:repairs');
console.log(res24);  // 'bike:1'

const res25 = await client.rPop('bikes:repairs');
console.log(res25);  // 'bike:2'

const res26 = await client.rPop('bikes:repairs');
console.log(res26);  // null
```

##### PHP

```php
        $r->del('bikes:repairs');

        $res22 = $r->rpush('bikes:repairs', 'bike:1', 'bike:2', 'bike:3');
        echo $res22 . PHP_EOL;
        // >>> 3

        $res23 = $r->rpop('bikes:repairs');
        echo $res23 . PHP_EOL;
        // >>> 'bike:3'

        $res24 = $r->lpop('bikes:repairs');
        echo $res24 . PHP_EOL;
        // >>> 'bike:1'

        $res25 = $r->rpop('bikes:repairs');
        echo $res25 . PHP_EOL;
        // >>> 'bike:2'

        $res26 = $r->rpop('bikes:repairs');
        echo $res26 . PHP_EOL;
        // >>> None
```

##### Python

```python
r.delete("bikes:repairs")

res22 = r.rpush("bikes:repairs", "bike:1", "bike:2", "bike:3")
print(res22)  # >>> 3

res23 = r.rpop("bikes:repairs")
print(res23)  # >>> 'bike:3'

res24 = r.lpop("bikes:repairs")
print(res24)  # >>> 'bike:1'

res25 = r.rpop("bikes:repairs")
print(res25)  # >>> 'bike:2'

res26 = r.rpop("bikes:repairs")
print(res26)  # >>> None
```

##### Ruby

```ruby
r.del('bikes:repairs')

res25 = r.rpush('bikes:repairs', ['bike:1', 'bike:2', 'bike:3'])
puts res25 # >>> 3

res26 = r.rpop('bikes:repairs')
puts res26 # >>> bike:3

res27 = r.lpop('bikes:repairs')
puts res27 # >>> bike:1

res28 = r.rpop('bikes:repairs')
puts res28 # >>> bike:2

res29 = r.rpop('bikes:repairs')
puts res29.inspect # >>> nil
```

##### Rust (Asynchronous)

```rust
        let _: usize = r.del("bikes:repairs").await.unwrap_or(0);

        if let Ok(res) = r.rpush("bikes:repairs", &["bike:1", "bike:2", "bike:3"]).await {
            let res: usize = res;
            println!("{res}"); // >>> 3
        }

        if let Ok(res) = r.rpop("bikes:repairs", None).await {
            let res: Option<String> = res;
            print_optional_string(res.clone()); // >>> bike:3
        }

        if let Ok(res) = r.lpop("bikes:repairs", None).await {
            let res: Option<String> = res;
            print_optional_string(res.clone()); // >>> bike:1
        }

        if let Ok(res) = r.rpop("bikes:repairs", None).await {
            let res: Option<String> = res;
            print_optional_string(res.clone()); // >>> bike:2
        }

        if let Ok(res) = r.rpop("bikes:repairs", None).await {
            let res: Option<String> = res;
            print_optional_string(res.clone()); // >>> (nil)
        }
```

##### Rust (Synchronous)

```rust
        let _: usize = r.del("bikes:repairs").unwrap_or(0);

        if let Ok(res) = r.rpush("bikes:repairs", &["bike:1", "bike:2", "bike:3"]) {
            let res: usize = res;
            println!("{res}"); // >>> 3
        }

        if let Ok(res) = r.rpop("bikes:repairs", None) {
            let res: Option<String> = res;
            print_optional_string(res.clone()); // >>> bike:3
        }

        if let Ok(res) = r.lpop("bikes:repairs", None) {
            let res: Option<String> = res;
            print_optional_string(res.clone()); // >>> bike:1
        }

        if let Ok(res) = r.rpop("bikes:repairs", None) {
            let res: Option<String> = res;
            print_optional_string(res.clone()); // >>> bike:2
        }

        if let Ok(res) = r.rpop("bikes:repairs", None) {
            let res: Option<String> = res;
            print_optional_string(res.clone()); // >>> (nil)
        }
```



Redis trả về một giá trị NULL để báo hiệu rằng không còn phần tử nào trong
list.

### Các trường hợp sử dụng phổ biến của list (Common use cases for lists)

List hữu ích cho nhiều tác vụ, hai trường hợp sử dụng rất tiêu biểu
là:

* Ghi nhớ các cập nhật mới nhất mà người dùng đăng lên một mạng xã hội.
* Giao tiếp giữa các tiến trình, dùng pattern consumer-producer, trong đó producer đẩy các phần tử vào một list, và một consumer (thường là một *worker*) tiêu thụ các phần tử đó và thực thi các hành động. Redis có các lệnh list đặc biệt để làm cho trường hợp sử dụng này vừa đáng tin cậy hơn vừa hiệu quả hơn.

Ví dụ, cả hai thư viện Ruby phổ biến [resque](https://github.com/resque/resque) và
[sidekiq](https://github.com/mperham/sidekiq) đều dùng Redis list ở bên dưới để
hiện thực các job chạy nền (background job).

Mạng xã hội Twitter nổi tiếng [lưu các tweet mới nhất](http://www.infoq.com/presentations/Real-Time-Delivery-Twitter)
do người dùng đăng vào Redis list.

Để mô tả một trường hợp sử dụng phổ biến từng bước một, hãy tưởng tượng trang chủ của bạn hiển thị các
ảnh mới nhất được đăng trên một mạng xã hội chia sẻ ảnh và bạn muốn tăng tốc truy cập.

* Mỗi khi một người dùng đăng một ảnh mới, chúng ta thêm ID của nó vào một list bằng [`LPUSH`](https://redis.io/docs/latest/commands/lpush).
* Khi người dùng truy cập trang chủ, chúng ta dùng `LRANGE 0 9` để lấy 10 mục được đăng gần nhất.

### List có giới hạn (Capped lists)

Trong nhiều trường hợp sử dụng, chúng ta chỉ muốn dùng list để lưu *các mục mới nhất*,
bất kể chúng là gì: cập nhật mạng xã hội, log, hay bất cứ thứ gì khác.

Redis cho phép chúng ta dùng list như một collection có giới hạn (capped collection), chỉ ghi nhớ N mục
mới nhất và loại bỏ tất cả các mục cũ nhất bằng lệnh [`LTRIM`](https://redis.io/docs/latest/commands/ltrim).

Lệnh [`LTRIM`](https://redis.io/docs/latest/commands/ltrim) tương tự như [`LRANGE`](https://redis.io/docs/latest/commands/lrange), nhưng **thay vì hiển thị
dải phần tử được chỉ định**, nó đặt dải này làm giá trị mới của list. Tất cả
các phần tử nằm ngoài dải cho trước sẽ bị xóa.

Ví dụ, nếu bạn đang thêm xe đạp vào cuối một danh sách sửa chữa, nhưng chỉ
muốn quan tâm tới 3 chiếc đã nằm trong danh sách lâu nhất:

*List có giới hạn: Dùng LTRIM với chỉ số dương để giữ lại một dải phần tử từ đầu list khi bạn cần duy trì một list có kích thước cố định*

**Độ khó (Difficulty):** Trung bình (Intermediate)

**Dựa trên (Builds upon):** lpush_rpush

**Lệnh (Commands):** DEL, RPUSH, LTRIM, LRANGE

**Độ phức tạp (Complexity):**
- DEL: O(N)
- RPUSH: O(1)
- LTRIM: O(N)
- LRANGE: O(S+N)

**Có sẵn trong (Available in):** Redis CLI, C#, C#, Go, Java (Asynchronous - Lettuce), Java (Synchronous - Jedis), JavaScript (Node.js), PHP, Python, Ruby, Rust (Asynchronous), Rust (Synchronous)

##### Redis CLI

```
> DEL bikes:repairs
(integer) 0
> RPUSH bikes:repairs bike:1 bike:2 bike:3 bike:4 bike:5
(integer) 5
> LTRIM bikes:repairs 0 2
OK
> LRANGE bikes:repairs 0 -1
1) "bike:1"
2) "bike:2"
3) "bike:3"
```

##### C#

```csharp
        long res27 = db.ListLeftPush("bikes:repairs", ["bike:1", "bike:2", "bike:3", "bike:4", "bike:5"]);
        Console.WriteLine(res27);   // >>> 5

        db.ListTrim("bikes:repairs", 0, 2);
        RedisValue[] res28 = db.ListRange("bikes:repairs", 0, -1);
        Console.WriteLine(string.Join(", ", res28));    // "bike:5, bike:4, bike:3"
```

##### C#

```csharp
        long res27 = db.ListLeftPush("bikes:repairs", ["bike:1", "bike:2", "bike:3", "bike:4", "bike:5"]);
        Console.WriteLine(res27);   // >>> 5

        db.ListTrim("bikes:repairs", 0, 2);
        RedisValue[] res28 = db.ListRange("bikes:repairs", 0, -1);
        Console.WriteLine(string.Join(", ", res28));    // "bike:5, bike:4, bike:3"
```

##### C#

```csharp
        long res27 = db.ListLeftPush("bikes:repairs", ["bike:1", "bike:2", "bike:3", "bike:4", "bike:5"]);
        Console.WriteLine(res27);   // >>> 5

        db.ListTrim("bikes:repairs", 0, 2);
        RedisValue[] res28 = db.ListRange("bikes:repairs", 0, -1);
        Console.WriteLine(string.Join(", ", res28));    // "bike:5, bike:4, bike:3"
```

##### C#

```csharp
        long res27 = db.ListLeftPush("bikes:repairs", ["bike:1", "bike:2", "bike:3", "bike:4", "bike:5"]);
        Console.WriteLine(res27);   // >>> 5

        db.ListTrim("bikes:repairs", 0, 2);
        RedisValue[] res28 = db.ListRange("bikes:repairs", 0, -1);
        Console.WriteLine(string.Join(", ", res28));    // "bike:5, bike:4, bike:3"
```

##### Go

```go
	rdb.Del(ctx, "bikes:repairs")

	res27, err := rdb.RPush(ctx, "bikes:repairs", "bike:1", "bike:2", "bike:3", "bike:4", "bike:5").Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res27) // >>> 5

	res28, err := rdb.LTrim(ctx, "bikes:repairs", 0, 2).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res28) // >>> OK

	res29, err := rdb.LRange(ctx, "bikes:repairs", 0, -1).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res29) // >>> [bike:1 bike:2 bike:3]
```

##### Java (Asynchronous - Lettuce)

```java
            CompletableFuture<Void> ltrim = asyncCommands
                    .lpush("bikes:repairs", "bike:1", "bike:2", "bike:3", "bike:4", "bike:5").thenCompose(res20 -> {
                        System.out.println(res20); // >>> 5

                        return asyncCommands.ltrim("bikes:repairs", 0, 2);
                    }).thenCompose(res21 -> {
                        System.out.println(res21); // >>> OK

                        return asyncCommands.lrange("bikes:repairs", 0, -1);
                    })
                    .thenAccept(System.out::println)
                    // >>> [bike:5, bike:4, bike:3]
                    .toCompletableFuture();
```

##### Java (Synchronous - Jedis)

```java
        long res27 = jedis.rpush("bikes:repairs", "bike:1", "bike:2", "bike:3", "bike:4", "bike:5");
        System.out.println(res27);  // >>> 5

        String res28 = jedis.ltrim("bikes:repairs", 0, 2);
        System.out.println(res28);  // >>> OK

        List<String> res29 = jedis.lrange("bikes:repairs", 0, -1);
        System.out.println(res29);  // >>> [bike:1, bike:2, bike:3]
```

##### JavaScript (Node.js)

```javascript
const res27 = await client.lPush(
  'bikes:repairs', ['bike:1', 'bike:2', 'bike:3', 'bike:4', 'bike:5']
);
console.log(res27);  // 5

const res28 = await client.lTrim('bikes:repairs', 0, 2);
console.log(res28);  // OK

const res29 = await client.lRange('bikes:repairs', 0, -1);
console.log(res29);  // ['bike:5', 'bike:4', 'bike:3']
```

##### PHP

```php
        $res27 = $r->rpush('bikes:repairs', 'bike:1', 'bike:2', 'bike:3', 'bike:4', 'bike:5');
        echo $res27 . PHP_EOL;
        // >>> 5

        $res28 = $r->ltrim('bikes:repairs', 0, 2);
        echo $res28 . PHP_EOL;
        // >>> True

        $res29 = $r->lrange('bikes:repairs', 0, -1);
        echo json_encode($res29) . PHP_EOL;
        // >>> ['bike:1', 'bike:2', 'bike:3']
```

##### Python

```python
res27 = r.rpush("bikes:repairs", "bike:1", "bike:2", "bike:3", "bike:4", "bike:5")
print(res27)  # >>> 5

res28 = r.ltrim("bikes:repairs", 0, 2)
print(res28)  # >>> True

res29 = r.lrange("bikes:repairs", 0, -1)
print(res29)  # >>> ['bike:1', 'bike:2', 'bike:3']
```

##### Ruby

```ruby
r.del('bikes:repairs')

res30 = r.rpush('bikes:repairs', ['bike:1', 'bike:2', 'bike:3', 'bike:4', 'bike:5'])
puts res30 # >>> 5

res31 = r.ltrim('bikes:repairs', 0, 2)
puts res31 # >>> OK

res32 = r.lrange('bikes:repairs', 0, -1)
puts res32.inspect # >>> ["bike:1", "bike:2", "bike:3"]
```

##### Rust (Asynchronous)

```rust
        if let Ok(res) = r
            .rpush(
                "bikes:repairs",
                &["bike:1", "bike:2", "bike:3", "bike:4", "bike:5"],
            )
            .await
        {
            let res: usize = res;
            println!("{res}"); // >>> 5
        }

        if let Ok(res) = r.ltrim("bikes:repairs", 0, 2).await {
            let res: () = res;
            let _ = res;
            println!("OK"); // >>> OK
        }

        if let Ok(res) = r.lrange("bikes:repairs", 0, -1).await {
            let res: Vec<String> = res;
            println!("{res:?}"); // >>> ["bike:1", "bike:2", "bike:3"]
        }
```

##### Rust (Synchronous)

```rust
        if let Ok(res) = r.rpush("bikes:repairs", &["bike:1", "bike:2", "bike:3", "bike:4", "bike:5"]) {
            let res: usize = res;
            println!("{res}"); // >>> 5
        }

        if let Ok(res) = r.ltrim("bikes:repairs", 0, 2) {
            let res: () = res;
            let _ = res;
            println!("OK"); // >>> OK
        }

        if let Ok(res) = r.lrange("bikes:repairs", 0, -1) {
            let res: Vec<String> = res;
            println!("{res:?}"); // >>> ["bike:1", "bike:2", "bike:3"]
        }
```



Lệnh [`LTRIM`](https://redis.io/docs/latest/commands/ltrim) ở trên báo cho Redis chỉ giữ lại các phần tử của list từ chỉ số
0 đến 2, mọi thứ khác sẽ bị loại bỏ. Điều này cho phép một pattern rất đơn giản nhưng
hữu ích: thực hiện một thao tác push vào List + một thao tác trim List cùng nhau
để thêm một phần tử mới và loại bỏ các phần tử vượt quá giới hạn. Sau đó, dùng
[`LTRIM`](https://redis.io/docs/latest/commands/ltrim) với chỉ số âm có thể giúp chỉ giữ lại 3 phần tử được thêm gần đây nhất:

*List có giới hạn với chỉ số âm: Dùng LTRIM với chỉ số âm để giữ các phần tử gần đây nhất khi bạn cần duy trì một list kích thước cố định chứa các mục mới nhất*

**Độ khó (Difficulty):** Trung bình (Intermediate)

**Dựa trên (Builds upon):** lpush_rpush

**Lệnh (Commands):** DEL, RPUSH, LTRIM, LRANGE

**Độ phức tạp (Complexity):**
- DEL: O(N)
- RPUSH: O(1)
- LTRIM: O(N)
- LRANGE: O(S+N)

**Có sẵn trong (Available in):** Redis CLI, C#, C#, Go, Java (Asynchronous - Lettuce), Java (Synchronous - Jedis), JavaScript (Node.js), PHP, Python, Ruby, Rust (Asynchronous), Rust (Synchronous)

##### Redis CLI

```
> DEL bikes:repairs
(integer) 1
> RPUSH bikes:repairs bike:1 bike:2 bike:3 bike:4 bike:5
(integer) 5
> LTRIM bikes:repairs -3 -1
OK
> LRANGE bikes:repairs 0 -1
1) "bike:3"
2) "bike:4"
3) "bike:5"
```

##### C#

```csharp
        long res29 = db.ListRightPush("bikes:repairs", ["bike:1", "bike:2", "bike:3", "bike:4", "bike:5"]);
        Console.WriteLine(res29);   // >>> 5

        db.ListTrim("bikes:repairs", -3, -1);
        RedisValue[] res30 = db.ListRange("bikes:repairs", 0, -1);
        Console.WriteLine(string.Join(", ", res30));    // >>> "bike:3, bike:4, bike:5"
```

##### C#

```csharp
        db.KeyDelete("bikes:repairs");

        long res29 = db.ListRightPush("bikes:repairs", ["bike:1", "bike:2", "bike:3", "bike:4", "bike:5"]);
        Console.WriteLine(res29);   // >>> 5

        db.ListTrim("bikes:repairs", -3, -1);
        RedisValue[] res30 = db.ListRange("bikes:repairs", 0, -1);
        Console.WriteLine(string.Join(", ", res30));    // >>> "bike:3, bike:4, bike:5"
```

##### C#

```csharp
        long res29 = db.ListRightPush("bikes:repairs", ["bike:1", "bike:2", "bike:3", "bike:4", "bike:5"]);
        Console.WriteLine(res29);   // >>> 5

        db.ListTrim("bikes:repairs", -3, -1);
        RedisValue[] res30 = db.ListRange("bikes:repairs", 0, -1);
        Console.WriteLine(string.Join(", ", res30));    // >>> "bike:3, bike:4, bike:5"
```

##### C#

```csharp
        db.KeyDelete("bikes:repairs");

        long res29 = db.ListRightPush("bikes:repairs", ["bike:1", "bike:2", "bike:3", "bike:4", "bike:5"]);
        Console.WriteLine(res29);   // >>> 5

        db.ListTrim("bikes:repairs", -3, -1);
        RedisValue[] res30 = db.ListRange("bikes:repairs", 0, -1);
        Console.WriteLine(string.Join(", ", res30));    // >>> "bike:3, bike:4, bike:5"
```

##### Go

```go
	rdb.Del(ctx, "bikes:repairs")

	res30, err := rdb.RPush(ctx, "bikes:repairs", "bike:1", "bike:2", "bike:3", "bike:4", "bike:5").Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res30) // >>> 5

	res31, err := rdb.LTrim(ctx, "bikes:repairs", -3, -1).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res31) // >>> OK

	res32, err := rdb.LRange(ctx, "bikes:repairs", 0, -1).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res32) // >>> [bike:3 bike:4 bike:5]
```

##### Java (Asynchronous - Lettuce)

```java
            CompletableFuture<Void> ltrimendoflist = asyncCommands
                    .rpush("bikes:repairs", "bike:1", "bike:2", "bike:3", "bike:4", "bike:5").thenCompose(res22 -> {
                        System.out.println(res22); // >>> 5

                        return asyncCommands.ltrim("bikes:repairs", -3, -1);
                    }).thenCompose(res23 -> {
                        System.out.println(res23); // >>> OK

                        return asyncCommands.lrange("bikes:repairs", 0, -1);
                    })
                    .thenAccept(System.out::println)
                    // >>> [bike:3, bike:4, bike:5]
                    .toCompletableFuture();
```

##### Java (Synchronous - Jedis)

```java
        jedis.del("bikes:repairs");

        res27 = jedis.rpush("bikes:repairs", "bike:1", "bike:2", "bike:3", "bike:4", "bike:5");
        System.out.println(res27);  // >>> 5

        res28 = jedis.ltrim("bikes:repairs", -3, -1);
        System.out.println(res2);  // >>> OK

        res29 = jedis.lrange("bikes:repairs", 0, -1);
        System.out.println(res29);  // >>> [bike:3, bike:4, bike:5]
```

##### JavaScript (Node.js)

```javascript
await client.del('bikes:repairs');

const res27eol = await client.rPush(
  'bikes:repairs', ['bike:1', 'bike:2', 'bike:3', 'bike:4', 'bike:5']
);
console.log(res27eol);  // 5

const res28eol = await client.lTrim('bikes:repairs', -3, -1);
console.log(res28eol);  // 'OK'

const res29eol = await client.lRange('bikes:repairs', 0, -1);
console.log(res29eol);  // ['bike:3', 'bike:4', 'bike:5']
```

##### PHP

```php
        $r->del('bikes:repairs');

        $res27 = $r->rpush('bikes:repairs', 'bike:1', 'bike:2', 'bike:3', 'bike:4', 'bike:5');
        echo $res27 . PHP_EOL;
        // >>> 5

        $res28 = $r->ltrim('bikes:repairs', -3, -1);
        echo $res28 . PHP_EOL;
        // >>> True

        $res29 = $r->lrange('bikes:repairs', 0, -1);
        echo json_encode($res29) . PHP_EOL;
        // >>> ['bike:3', 'bike:4', 'bike:5']
```

##### Python

```python
r.delete("bikes:repairs")

res27 = r.rpush("bikes:repairs", "bike:1", "bike:2", "bike:3", "bike:4", "bike:5")
print(res27)  # >>> 5

res28 = r.ltrim("bikes:repairs", -3, -1)
print(res28)  # >>> True

res29 = r.lrange("bikes:repairs", 0, -1)
print(res29)  # >>> ['bike:3', 'bike:4', 'bike:5']
```

##### Ruby

```ruby
r.del('bikes:repairs')

res33 = r.rpush('bikes:repairs', ['bike:1', 'bike:2', 'bike:3', 'bike:4', 'bike:5'])
puts res33 # >>> 5

res34 = r.ltrim('bikes:repairs', -3, -1)
puts res34 # >>> OK

res35 = r.lrange('bikes:repairs', 0, -1)
puts res35.inspect # >>> ["bike:3", "bike:4", "bike:5"]
```

##### Rust (Asynchronous)

```rust
        let _: usize = r.del("bikes:repairs").await.unwrap_or(0);

        if let Ok(res) = r
            .rpush(
                "bikes:repairs",
                &["bike:1", "bike:2", "bike:3", "bike:4", "bike:5"],
            )
            .await
        {
            let res: usize = res;
            println!("{res}"); // >>> 5
        }

        if let Ok(res) = r.ltrim("bikes:repairs", -3, -1).await {
            let res: () = res;
            let _ = res;
            println!("OK"); // >>> OK
        }

        if let Ok(res) = r.lrange("bikes:repairs", 0, -1).await {
            let res: Vec<String> = res;
            println!("{res:?}"); // >>> ["bike:3", "bike:4", "bike:5"]
        }
```

##### Rust (Synchronous)

```rust
        let _: usize = r.del("bikes:repairs").unwrap_or(0);

        if let Ok(res) = r.rpush("bikes:repairs", &["bike:1", "bike:2", "bike:3", "bike:4", "bike:5"]) {
            let res: usize = res;
            println!("{res}"); // >>> 5
        }

        if let Ok(res) = r.ltrim("bikes:repairs", -3, -1) {
            let res: () = res;
            let _ = res;
            println!("OK"); // >>> OK
        }

        if let Ok(res) = r.lrange("bikes:repairs", 0, -1) {
            let res: Vec<String> = res;
            println!("{res:?}"); // >>> ["bike:3", "bike:4", "bike:5"]
        }
```



Sự kết hợp ở trên thêm các phần tử mới và chỉ giữ lại 3
phần tử mới nhất trong list. Với [`LRANGE`](https://redis.io/docs/latest/commands/lrange), bạn có thể truy cập các mục trên cùng
mà không cần ghi nhớ dữ liệu rất cũ.

Lưu ý: mặc dù về mặt kỹ thuật [`LRANGE`](https://redis.io/docs/latest/commands/lrange) là một lệnh O(N), việc truy cập các dải nhỏ
ở gần đầu hoặc cuối list là một thao tác có thời gian hằng số.

## Các thao tác blocking trên list (Blocking operations on lists)

List có một tính năng đặc biệt khiến chúng phù hợp để hiện thực queue,
và nói chung là làm khối xây dựng cho các hệ thống giao tiếp giữa các tiến trình (inter process communication):
các thao tác blocking.

Hãy tưởng tượng bạn muốn đẩy các phần tử vào một list bằng một tiến trình, và dùng
một tiến trình khác để thực sự làm một công việc nào đó với các
phần tử đó. Đây là thiết lập producer / consumer thông thường, và có thể được hiện thực
theo cách đơn giản sau:

* Để đẩy phần tử vào list, producer gọi [`LPUSH`](https://redis.io/docs/latest/commands/lpush).
* Để lấy ra / xử lý phần tử từ list, consumer gọi [`RPOP`](https://redis.io/docs/latest/commands/rpop).

Tuy nhiên, đôi khi list có thể rỗng và không có gì
để xử lý, nên [`RPOP`](https://redis.io/docs/latest/commands/rpop) chỉ trả về NULL. Trong trường hợp này, consumer buộc phải chờ
một khoảng thời gian rồi thử lại bằng [`RPOP`](https://redis.io/docs/latest/commands/rpop). Việc này được gọi là *polling*, và không phải
là ý hay trong bối cảnh này vì nó có một số nhược điểm:

1. Buộc Redis và client phải xử lý các lệnh vô ích (tất cả các request khi list rỗng sẽ không làm được việc gì thực sự, chúng chỉ trả về NULL).
2. Thêm độ trễ vào việc xử lý phần tử, vì sau khi worker nhận được NULL, nó sẽ chờ một khoảng thời gian. Để giảm độ trễ, chúng ta có thể chờ ít hơn giữa các lần gọi [`RPOP`](https://redis.io/docs/latest/commands/rpop), nhưng điều đó lại làm trầm trọng thêm vấn đề số 1, tức là nhiều lần gọi vô ích tới Redis hơn.

Vì vậy Redis hiện thực các lệnh gọi là [`BRPOP`](https://redis.io/docs/latest/commands/brpop) và [`BLPOP`](https://redis.io/docs/latest/commands/blpop), là các phiên bản
của [`RPOP`](https://redis.io/docs/latest/commands/rpop) và [`LPOP`](https://redis.io/docs/latest/commands/lpop) có khả năng block nếu list rỗng: chúng chỉ trả kết quả về cho
phía gọi khi có một phần tử mới được thêm vào list, hoặc khi đạt tới timeout do người dùng
chỉ định.

Đây là một ví dụ về lời gọi [`BRPOP`](https://redis.io/docs/latest/commands/brpop) mà chúng ta có thể dùng trong worker:

*Thao tác blocking: Dùng BRPOP để chờ phần tử với một timeout khi bạn cần hiện thực pattern producer-consumer mà không cần polling*

**Độ khó (Difficulty):** Trung bình (Intermediate)

**Dựa trên (Builds upon):** lpush_rpush, lpop_rpop

**Lệnh (Commands):** DEL, RPUSH, BRPOP

**Độ phức tạp (Complexity):**
- DEL: O(N)
- RPUSH: O(1)
- BRPOP: O(N)

**Có sẵn trong (Available in):** Redis CLI, C#, C#, Go, Java (Asynchronous - Lettuce), Java (Synchronous - Jedis), JavaScript (Node.js), PHP, Python, Ruby, Rust (Asynchronous), Rust (Synchronous)

##### Redis CLI

```
> DEL bikes:repairs
(integer) 1
> RPUSH bikes:repairs bike:1 bike:2
(integer) 2
> BRPOP bikes:repairs 1
1) "bikes:repairs"
2) "bike:2"
> BRPOP bikes:repairs 1
1) "bikes:repairs"
2) "bike:1"
> BRPOP bikes:repairs 1
(nil)
(2.01s)
```

##### C#

```csharp
        long res31 = db.ListRightPush("bikes:repairs", ["bike:1", "bike:2"]);
        Console.WriteLine(res31);   // >>> 2

        Tuple<RedisKey, RedisValue>? res32 = db.BRPop(["bikes:repairs"], 1);

        if (res32 != null)
            Console.WriteLine($"{res32.Item1} -> {res32.Item2}"); // >>> "bikes:repairs -> bike:2"

        Tuple<RedisKey, RedisValue>? res33 = db.BRPop(["bikes:repairs"], 1);

        if (res33 != null)
            Console.WriteLine($"{res33.Item1} -> {res33.Item2}"); // >>> "bikes:repairs -> bike:1"

        Tuple<RedisKey, RedisValue>? res34 = db.BRPop(["bikes:repairs"], 1);
        Console.WriteLine(res34);   // >>> "Null"
```

##### C#

```csharp
        db.KeyDelete("bikes:repairs");

        long res31 = db.ListRightPush("bikes:repairs", ["bike:1", "bike:2"]);
        Console.WriteLine(res31);   // >>> 2

        Tuple<RedisKey, RedisValue>? res32 = db.BRPop(["bikes:repairs"], 1);

        if (res32 != null)
            Console.WriteLine($"{res32.Item1} -> {res32.Item2}"); // >>> "bikes:repairs -> bike:2"

        Tuple<RedisKey, RedisValue>? res33 = db.BRPop(["bikes:repairs"], 1);

        if (res33 != null)
            Console.WriteLine($"{res33.Item1} -> {res33.Item2}"); // >>> "bikes:repairs -> bike:1"

        Tuple<RedisKey, RedisValue>? res34 = db.BRPop(["bikes:repairs"], 1);
        Console.WriteLine(res34);   // >>> "Null"
```

##### C#

```csharp
        long res31 = db.ListRightPush("bikes:repairs", ["bike:1", "bike:2"]);
        Console.WriteLine(res31);   // >>> 2

        Tuple<RedisKey, RedisValue>? res32 = db.BRPop(["bikes:repairs"], 1);

        if (res32 != null)
            Console.WriteLine($"{res32.Item1} -> {res32.Item2}"); // >>> "bikes:repairs -> bike:2"

        Tuple<RedisKey, RedisValue>? res33 = db.BRPop(["bikes:repairs"], 1);

        if (res33 != null)
            Console.WriteLine($"{res33.Item1} -> {res33.Item2}"); // >>> "bikes:repairs -> bike:1"

        Tuple<RedisKey, RedisValue>? res34 = db.BRPop(["bikes:repairs"], 1);
        Console.WriteLine(res34);   // >>> "Null"
```

##### C#

```csharp
        db.KeyDelete("bikes:repairs");

        long res31 = db.ListRightPush("bikes:repairs", ["bike:1", "bike:2"]);
        Console.WriteLine(res31);   // >>> 2

        Tuple<RedisKey, RedisValue>? res32 = db.BRPop(["bikes:repairs"], 1);

        if (res32 != null)
            Console.WriteLine($"{res32.Item1} -> {res32.Item2}"); // >>> "bikes:repairs -> bike:2"

        Tuple<RedisKey, RedisValue>? res33 = db.BRPop(["bikes:repairs"], 1);

        if (res33 != null)
            Console.WriteLine($"{res33.Item1} -> {res33.Item2}"); // >>> "bikes:repairs -> bike:1"

        Tuple<RedisKey, RedisValue>? res34 = db.BRPop(["bikes:repairs"], 1);
        Console.WriteLine(res34);   // >>> "Null"
```

##### Go

```go
	rdb.Del(ctx, "bikes:repairs")

	res33, err := rdb.RPush(ctx, "bikes:repairs", "bike:1", "bike:2").Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res33) // >>> 2

	res34, err := rdb.BRPop(ctx, 1, "bikes:repairs").Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res34) // >>> [bikes:repairs bike:2]

	res35, err := rdb.BRPop(ctx, 1, "bikes:repairs").Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res35) // >>> [bikes:repairs bike:1]

	res36, err := rdb.BRPop(ctx, 1, "bikes:repairs").Result()

	if err != nil {
		fmt.Println(err) // >>> redis: nil
	}

	fmt.Println(res36) // >>> []
```

##### Java (Asynchronous - Lettuce)

```java
            CompletableFuture<Void> brpop = asyncCommands.rpush("bikes:repairs", "bike:1", "bike:2").thenCompose(res24 -> {
                System.out.println(res24); // >>> 2

                return asyncCommands.brpop(1, "bikes:repairs");
            }).thenCompose(res25 -> {
                System.out.println(res25);
                // >>> KeyValue[bikes:repairs, bike:2]

                return asyncCommands.brpop(1, "bikes:repairs");
            }).thenCompose(res26 -> {
                System.out.println(res26);
                // >>> KeyValue[bikes:repairs, bike:1]

                return asyncCommands.brpop(1, "bikes:repairs");
            })
                    .thenAccept(System.out::println) // >>> null
                    .toCompletableFuture();
```

##### Java (Synchronous - Jedis)

```java
        jedis.del("bikes:repairs");

        long res31 = jedis.rpush("bikes:repairs", "bike:1", "bike:2");
        System.out.println(res31);  // >>> 2

        List<String> res32 = jedis.brpop(1, "bikes:repairs");
        System.out.println(res32);  // >>> (bikes:repairs, bike:2)

        List<String>  res33 = jedis.brpop(1,"bikes:repairs");
        System.out.println(res33);  // >>> (bikes:repairs, bike:1)

        List<String>  res34 = jedis.brpop(1,"bikes:repairs");
        System.out.println(res34);  // >>> null
```

##### JavaScript (Node.js)

```javascript
await client.del('bikes:repairs');

const res31 = await client.rPush('bikes:repairs', ['bike:1', 'bike:2']);
console.log(res31);  // 2

const res32 = await client.brPop('bikes:repairs', 1);
console.log(res32);  // { key: 'bikes:repairs', element: 'bike:2' }

const res33 = await client.brPop('bikes:repairs', 1);
console.log(res33);  // { key: 'bikes:repairs', element: 'bike:1' }

const res34 = await client.brPop('bikes:repairs', 1);
console.log(res34);  // null
```

##### PHP

```php
        $r->del('bikes:repairs');

        $res31 = $r->rpush('bikes:repairs', 'bike:1', 'bike:2');
        echo $res31 . PHP_EOL;
        // >>> 2

        $res32 = $r->brpop('bikes:repairs', 1);
        echo json_encode($res32) . PHP_EOL;
        // >>> ['bikes:repairs', 'bike:2']

        $res33 = $r->brpop('bikes:repairs', 1);
        echo json_encode($res33) . PHP_EOL;
        // >>> ['bikes:repairs', 'bike:1']

        $res34 = $r->brpop('bikes:repairs', 1);
        echo json_encode($res34) . PHP_EOL;
        // >>> None
```

##### Python

```python
r.delete("bikes:repairs")

res31 = r.rpush("bikes:repairs", "bike:1", "bike:2")
print(res31)  # >>> 2

res32 = r.brpop("bikes:repairs", timeout=1)
print(res32)  # >>> ('bikes:repairs', 'bike:2')

res33 = r.brpop("bikes:repairs", timeout=1)
print(res33)  # >>> ('bikes:repairs', 'bike:1')

res34 = r.brpop("bikes:repairs", timeout=1)
print(res34)  # >>> None
```

##### Ruby

```ruby
r.del('bikes:repairs')

res36 = r.rpush('bikes:repairs', ['bike:1', 'bike:2'])
puts res36 # >>> 2

res37 = r.brpop('bikes:repairs', timeout: 1)
puts res37.inspect # >>> ["bikes:repairs", "bike:2"]

res38 = r.brpop('bikes:repairs', timeout: 1)
puts res38.inspect # >>> ["bikes:repairs", "bike:1"]

res39 = r.brpop('bikes:repairs', timeout: 1)
puts res39.inspect # >>> nil
```

##### Rust (Asynchronous)

```rust
        let _: usize = r.del("bikes:repairs").await.unwrap_or(0);

        if let Ok(res) = r.rpush("bikes:repairs", &["bike:1", "bike:2"]).await {
            let res: usize = res;
            println!("{res}"); // >>> 2
        }

        if let Ok(res) = r.brpop("bikes:repairs", 1.0).await {
            let res: Option<[String; 2]> = res;
            println!("{res:?}"); // >>> Some(["bikes:repairs", "bike:2"])
        }

        if let Ok(res) = r.brpop("bikes:repairs", 1.0).await {
            let res: Option<[String; 2]> = res;
            println!("{res:?}"); // >>> Some(["bikes:repairs", "bike:1"])
        }

        if let Ok(res) = r.brpop("bikes:repairs", 1.0).await {
            let res: Option<[String; 2]> = res;
            println!("{res:?}"); // >>> None
        }
```

##### Rust (Synchronous)

```rust
        let _: usize = r.del("bikes:repairs").unwrap_or(0);

        if let Ok(res) = r.rpush("bikes:repairs", &["bike:1", "bike:2"]) {
            let res: usize = res;
            println!("{res}"); // >>> 2
        }

        if let Ok(res) = r.brpop("bikes:repairs", 1.0) {
            let res: Option<[String; 2]> = res;
            println!("{res:?}"); // >>> Some(["bikes:repairs", "bike:2"])
        }

        if let Ok(res) = r.brpop("bikes:repairs", 1.0) {
            let res: Option<[String; 2]> = res;
            println!("{res:?}"); // >>> Some(["bikes:repairs", "bike:1"])
        }

        if let Ok(res) = r.brpop("bikes:repairs", 1.0) {
            let res: Option<[String; 2]> = res;
            println!("{res:?}"); // >>> None
        }
```



Nó có nghĩa là: "chờ phần tử trong list `bikes:repairs`, nhưng trả về nếu sau 1 giây
vẫn không có phần tử nào".

Lưu ý rằng bạn có thể dùng 0 làm timeout để chờ phần tử mãi mãi, và bạn cũng
có thể chỉ định nhiều list chứ không chỉ một, để chờ trên nhiều
list cùng lúc, và được thông báo khi list đầu tiên nhận được một
phần tử.

Một vài điều cần lưu ý về [`BRPOP`](https://redis.io/docs/latest/commands/brpop):

1. Các client được phục vụ theo thứ tự: client đầu tiên bị block khi chờ một list sẽ được phục vụ trước khi một phần tử được đẩy vào bởi một client khác, và cứ thế.
2. Giá trị trả về khác so với [`RPOP`](https://redis.io/docs/latest/commands/rpop): nó là một mảng hai phần tử vì nó còn bao gồm tên của key, bởi [`BRPOP`](https://redis.io/docs/latest/commands/brpop) và [`BLPOP`](https://redis.io/docs/latest/commands/blpop) có thể block để chờ phần tử từ nhiều list.
3. Nếu đạt tới timeout, NULL sẽ được trả về.

Còn nhiều điều nữa bạn nên biết về list và các thao tác blocking. Chúng tôi
khuyên bạn nên đọc thêm về những điều sau:

* Có thể xây dựng các queue an toàn hơn hoặc queue xoay vòng (rotating queue) bằng [`LMOVE`](https://redis.io/docs/latest/commands/lmove).
* Cũng có một biến thể blocking của lệnh này, gọi là [`BLMOVE`](https://redis.io/docs/latest/commands/blmove).

## Tự động tạo và xóa key (Automatic creation and removal of keys)

Cho tới giờ, trong các ví dụ của chúng ta, chúng ta chưa bao giờ phải tạo list rỗng trước khi đẩy
phần tử vào, hay xóa các list rỗng khi chúng không còn phần tử nào bên trong.
Redis chịu trách nhiệm xóa key khi list trở nên rỗng, hoặc tạo
một list rỗng nếu key chưa tồn tại và chúng ta đang cố thêm phần tử
vào nó, ví dụ, bằng [`LPUSH`](https://redis.io/docs/latest/commands/lpush).

Điều này không chỉ riêng với list, nó áp dụng cho tất cả các kiểu dữ liệu Redis
gồm nhiều phần tử -- Stream, Set, Sorted Set và Hash.

Về cơ bản, chúng ta có thể tóm tắt hành vi này bằng ba quy tắc:

1. Khi chúng ta thêm một phần tử vào một kiểu dữ liệu tập hợp (aggregate data type), nếu key đích chưa tồn tại, một kiểu dữ liệu tập hợp rỗng sẽ được tạo ra trước khi thêm phần tử.
2. Khi chúng ta xóa phần tử khỏi một kiểu dữ liệu tập hợp, nếu giá trị trở nên rỗng, key sẽ tự động bị hủy. Kiểu dữ liệu Stream là ngoại lệ duy nhất của quy tắc này.
3. Gọi một lệnh chỉ đọc như [`LLEN`](https://redis.io/docs/latest/commands/llen) (trả về độ dài của list), hoặc một lệnh ghi xóa phần tử, với một key rỗng, luôn cho ra cùng kết quả như thể key đó đang chứa một kiểu tập hợp rỗng thuộc kiểu mà lệnh mong đợi.

Ví dụ về quy tắc 1:

*Tự động tạo: Redis tự động tạo một list khi bạn push vào một key chưa tồn tại, giúp đơn giản hóa việc khởi tạo*

**Lệnh (Commands):** DEL, LPUSH

**Độ phức tạp (Complexity):**
- DEL: O(N)
- LPUSH: O(1)

**Có sẵn trong (Available in):** Redis CLI, C#, C#, Go, Java (Asynchronous - Lettuce), Java (Synchronous - Jedis), JavaScript (Node.js), PHP, Python, Ruby, Rust (Asynchronous), Rust (Synchronous)

##### Redis CLI

```
> DEL new_bikes
(integer) 0
> LPUSH new_bikes bike:1 bike:2 bike:3
(integer) 3
```

##### C#

```csharp
        bool res35 = db.KeyDelete("new_bikes");
        Console.WriteLine(res35);   // >>> False

        long res36 = db.ListRightPush("new_bikes", ["bike:1", "bike:2", "bike:3"]);
        Console.WriteLine(res36);   // >>> 3
```

##### C#

```csharp
        db.KeyDelete("new_bikes");

        long res36 = db.ListRightPush("new_bikes", ["bike:1", "bike:2", "bike:3"]);
        Console.WriteLine(res36);   // >>> 3
```

##### C#

```csharp
        bool res35 = db.KeyDelete("new_bikes");
        Console.WriteLine(res35);   // >>> False

        long res36 = db.ListRightPush("new_bikes", ["bike:1", "bike:2", "bike:3"]);
        Console.WriteLine(res36);   // >>> 3
```

##### C#

```csharp
        db.KeyDelete("new_bikes");

        long res36 = db.ListRightPush("new_bikes", ["bike:1", "bike:2", "bike:3"]);
        Console.WriteLine(res36);   // >>> 3
```

##### Go

```go
	res37, err := rdb.Del(ctx, "new_bikes").Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res37) // >>> 0

	res38, err := rdb.LPush(ctx, "new_bikes", "bike:1", "bike:2", "bike:3").Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res38) // >>> 3
```

##### Java (Asynchronous - Lettuce)

```java
            CompletableFuture<Void> rule1 = asyncCommands.del("new_bikes").thenCompose(res27 -> {
                System.out.println(res27); // >>> 0

                return asyncCommands.lpush("new_bikes", "bike:1", "bike:2", "bike:3");
            })
                    .thenAccept(System.out::println) // >>> 3
                    .toCompletableFuture();
```

##### Java (Synchronous - Jedis)

```java
        jedis.del("new_bikes");

        long res36 = jedis.lpush("new_bikes", "bike:1", "bike:2", "bike:3");
        System.out.println(res36);  // >>> 3
```

##### JavaScript (Node.js)

```javascript
const res35 = await client.del('new_bikes');
console.log(res35); // 0

const res36 = await client.lPush('new_bikes', ['bike:1', 'bike:2', 'bike:3']);
console.log(res36); // 3
```

##### PHP

```php
        $res35 = $r->del('new_bikes');
        echo $res35 . PHP_EOL;
        // >>> 0

        $res36 = $r->lpush('new_bikes', 'bike:1', 'bike:2', 'bike:3');
        echo $res36 . PHP_EOL;
        // >>> 3
```

##### Python

```python
res35 = r.delete("new_bikes")
print(res35)  # >>> 0

res36 = r.lpush("new_bikes", "bike:1", "bike:2", "bike:3")
print(res36)  # >>> 3
```

##### Ruby

```ruby
res40 = r.del('new_bikes')
puts res40 # >>> 0

res41 = r.lpush('new_bikes', ['bike:1', 'bike:2', 'bike:3'])
puts res41 # >>> 3
```

##### Rust (Asynchronous)

```rust
        if let Ok(res) = r.del("new_bikes").await {
            let res: usize = res;
            println!("{res}"); // >>> 0
        }

        if let Ok(res) = r.lpush("new_bikes", &["bike:1", "bike:2", "bike:3"]).await {
            let res: usize = res;
            println!("{res}"); // >>> 3
        }
```

##### Rust (Synchronous)

```rust
        if let Ok(res) = r.del("new_bikes") {
            let res: usize = res;
            println!("{res}"); // >>> 0
        }

        if let Ok(res) = r.lpush("new_bikes", &["bike:1", "bike:2", "bike:3"]) {
            let res: usize = res;
            println!("{res}"); // >>> 3
        }
```



Tuy nhiên, chúng ta không thể thực hiện thao tác với sai kiểu nếu key đã tồn tại:

*An toàn kiểu: Redis ngăn các thao tác trên sai kiểu dữ liệu, trả về lỗi WRONGTYPE khi bạn cố dùng lệnh list trên các key không phải list*

**Dựa trên (Builds upon):** rule_1

**Lệnh (Commands):** DEL, SET, TYPE, LPUSH

**Độ phức tạp (Complexity):**
- DEL: O(N)
- SET: O(1)
- TYPE: O(1)
- LPUSH: O(1)

**Có sẵn trong (Available in):** Redis CLI, C#, C#, Go, Java (Asynchronous - Lettuce), Java (Synchronous - Jedis), JavaScript (Node.js), PHP, Python, Ruby, Rust (Asynchronous), Rust (Synchronous)

##### Redis CLI

```
> DEL new_bikes
(integer) 1
> SET new_bikes bike:1
OK
> TYPE new_bikes
string
> LPUSH new_bikes bike:2 bike:3
(error) WRONGTYPE Operation against a key holding the wrong kind of value
```

##### C#

```csharp
        bool res37 = db.StringSet("new_bikes", "bike:1");
        Console.WriteLine(res37);   // >>> True

        RedisType res38 = db.KeyType("new_bikes");
        Console.WriteLine(res38);   // >>> RedisType.String

        try
        {
            long res39 = db.ListRightPush("new_bikes", ["bike:2", "bike:3"]);
        }
        catch (Exception e)
        {
            Console.WriteLine(e);
        }
```

##### C#

```csharp
        db.KeyDelete("new_bikes");

        bool res37 = db.StringSet("new_bikes", "bike:1");
        Console.WriteLine(res37);   // >>> True

        RedisType res38 = db.KeyType("new_bikes");
        Console.WriteLine(res38);   // >>> RedisType.String

        try
        {
            long res39 = db.ListRightPush("new_bikes", ["bike:2", "bike:3"]);
        }
        catch (Exception e)
        {
            Console.WriteLine(e);
        }
```

##### C#

```csharp
        bool res37 = db.StringSet("new_bikes", "bike:1");
        Console.WriteLine(res37);   // >>> True

        RedisType res38 = db.KeyType("new_bikes");
        Console.WriteLine(res38);   // >>> RedisType.String

        try
        {
            long res39 = db.ListRightPush("new_bikes", ["bike:2", "bike:3"]);
        }
        catch (Exception e)
        {
            Console.WriteLine(e);
        }
```

##### C#

```csharp
        db.KeyDelete("new_bikes");

        bool res37 = db.StringSet("new_bikes", "bike:1");
        Console.WriteLine(res37);   // >>> True

        RedisType res38 = db.KeyType("new_bikes");
        Console.WriteLine(res38);   // >>> RedisType.String

        try
        {
            long res39 = db.ListRightPush("new_bikes", ["bike:2", "bike:3"]);
        }
        catch (Exception e)
        {
            Console.WriteLine(e);
        }
```

##### Go

```go
	rdb.Del(ctx, "new_bikes")

	res39, err := rdb.Set(ctx, "new_bikes", "bike:1", 0).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res39) // >>> OK

	res40, err := rdb.Type(ctx, "new_bikes").Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res40) // >>> string

	res41, err := rdb.LPush(ctx, "new_bikes", "bike:2", "bike:3").Result()

	if err != nil {
		fmt.Println(err)
		// >>> WRONGTYPE Operation against a key holding the wrong kind of value
	}

	fmt.Println(res41)
```

##### Java (Asynchronous - Lettuce)

```java
            CompletableFuture<Void> rule11 = asyncCommands.set("new_bikes_string", "bike:1").thenCompose(res28 -> {
                System.out.println(res28); // >>> OK

                return asyncCommands.type("new_bikes_string");
            }).thenCompose(res29 -> {
                System.out.println(res29); // >>> string

                return asyncCommands.lpush("new_bikes_string", "bike:2", "bike:3");
            }).handle((res, ex) -> {
                if (ex == null) {
                    return res;
                } else {
                    System.out.println(ex);
                    // >>> java.util.concurrent.CompletionException:
                    // >>> io.lettuce.core.RedisCommandExecutionException:
                    // >>> WRONGTYPE Operation against a key holding the wrong
                    // >>> kind of value

                    return -1L;
                }
            })
                    .thenAccept(System.out::println) // >>> -1
                    .toCompletableFuture();
```

##### Java (Synchronous - Jedis)

```java
        jedis.del("new_bikes");

        String res37 = jedis.set("new_bikes", "bike:1");
        System.out.println(res37);  // >>> OK

        String res38 = jedis.type("new_bikes");
        System.out.println(res38);  // >>> string

        try {
            long res39  = jedis.lpush("new_bikes", "bike:2", "bike:3");
        } catch (Exception e) {
            e.printStackTrace();
            // >>> redis.clients.jedis.exceptions.JedisDataException:
            // >>> WRONGTYPE Operation against a key holding the wrong kind of value
        }
```

##### JavaScript (Node.js)

```javascript
await client.del('new_bikes');

const res37 = await client.set('new_bikes', 'bike:1');
console.log(res37);  // 'OK'

const res38 = await client.type('new_bikes');
console.log(res38);  // 'string'

try {
  const res39 = await client.lPush('new_bikes', 'bike:2', 'bike:3');
  // redis.exceptions.ResponseError:
  // [SimpleError: WRONGTYPE Operation against a key holding the wrong kind of value]
}
catch(e){
  console.log(e);
}
```

##### PHP

```php
        $r->del('new_bikes');

        $res37 = $r->set('new_bikes', 'bike:1');
        echo $res37 . PHP_EOL;
        // >>> True

        $res38 = $r->type('new_bikes');
        echo $res38 . PHP_EOL;
        // >>> 'string'

        try {
            $res39 = $r->lpush('new_bikes', 'bike:2', 'bike:3');
            // >>> redis.exceptions.ResponseError:
            // >>> WRONGTYPE Operation against a key holding the wrong kind of value
        } catch (\Predis\Response\ServerException $e) {
            echo $e->getMessage() . PHP_EOL;
        }
```

##### Python

```python
r.delete("new_bikes")

res37 = r.set("new_bikes", "bike:1")
print(res37)  # >>> True

res38 = r.type("new_bikes")
print(res38)  # >>> 'string'

try:
    res39 = r.lpush("new_bikes", "bike:2", "bike:3")
    # >>> redis.exceptions.ResponseError:
    # >>> WRONGTYPE Operation against a key holding the wrong kind of value
except redis.exceptions.ResponseError as e:
    print(e)
```

##### Ruby

```ruby
r.del('new_bikes')

res42 = r.set('new_bikes', 'bike:1')
puts res42 # >>> OK

res43 = r.type('new_bikes')
puts res43 # >>> string

wrong_type_error = nil
begin
  r.lpush('new_bikes', ['bike:2', 'bike:3'])
rescue Redis::CommandError => e
  wrong_type_error = e
  puts e.message # >>> WRONGTYPE Operation against a key holding the wrong kind of value
end
```

##### Rust (Asynchronous)

```rust
        if let Ok(res) = r.del("new_bikes").await {
            let res: usize = res;
            println!("{res}"); // >>> 1
        }

        if let Ok(res) = r.set("new_bikes", "bike:1").await {
            let res: String = res;
            println!("{res}"); // >>> OK
        }

        if let Ok(res) = r.key_type("new_bikes").await {
            let res: ValueType = res;
            let res: String = res.into();
            println!("{res}"); // >>> string
        }

        let res: redis::RedisResult<usize> = r.lpush("new_bikes", &["bike:2", "bike:3"]).await;
        match res {
            Ok(_) => {}
            Err(e) => {
                let msg = e.to_string();
                if let Some((_, tail)) = msg.split_once("WRONGTYPE ") {
                    println!("WRONGTYPE {tail}");
                } else {
                    println!("{msg}");
                }
            }
        }
```

##### Rust (Synchronous)

```rust
        if let Ok(res) = r.del("new_bikes") {
            let res: usize = res;
            println!("{res}"); // >>> 1
        }

        if let Ok(res) = r.set("new_bikes", "bike:1") {
            let res: String = res;
            println!("{res}"); // >>> OK
        }

        if let Ok(res) = r.key_type("new_bikes") {
            let res: ValueType = res;
            let res: String = res.into();
            println!("{res}"); // >>> string
        }

        let res: redis::RedisResult<usize> = r.lpush("new_bikes", &["bike:2", "bike:3"]);
        match res {
            Ok(_) => {}
            Err(e) => {
                let msg = e.to_string();
                if let Some((_, tail)) = msg.split_once("WRONGTYPE ") {
                    println!("WRONGTYPE {tail}");
                } else {
                    println!("{msg}");
                }
            }
        }
```



Ví dụ về quy tắc 2:

*Tự động xóa: Redis tự động xóa các list rỗng, giải phóng bộ nhớ khi phần tử cuối cùng bị xóa*

**Dựa trên (Builds upon):** lpush_rpush, lpop_rpop

**Lệnh (Commands):** DEL, LPUSH, EXISTS, LPOP

**Độ phức tạp (Complexity):**
- DEL: O(N)
- LPUSH: O(1)
- EXISTS: O(N)
- LPOP: O(N)

**Có sẵn trong (Available in):** Redis CLI, C#, C#, Go, Java (Asynchronous - Lettuce), Java (Synchronous - Jedis), JavaScript (Node.js), PHP, Python, Ruby, Rust (Asynchronous), Rust (Synchronous)

##### Redis CLI

```
> DEL bikes:repairs
(integer) 1
> LPUSH bikes:repairs bike:1 bike:2 bike:3
(integer) 3
> EXISTS bikes:repairs
(integer) 1
> LPOP bikes:repairs
"bike:3"
> LPOP bikes:repairs
"bike:2"
> LPOP bikes:repairs
"bike:1"
> EXISTS bikes:repairs
(integer) 0
```

##### C#

```csharp
        long res40 = db.ListLeftPush("bikes:repairs", ["bike:1", "bike:2", "bike:3"]);
        Console.WriteLine(res40);   // >>> 3

        bool res41 = db.KeyExists("bikes:repairs");
        Console.WriteLine(res41);   // >>> True

        RedisValue res42 = db.ListLeftPop("bikes:repairs");
        Console.WriteLine(res42);   // >>> "bike:3"

        RedisValue res43 = db.ListLeftPop("bikes:repairs");
        Console.WriteLine(res43);   // >>> "bike:2"

        RedisValue res44 = db.ListLeftPop("bikes:repairs");
        Console.WriteLine(res44);   //  >>> "bike:1"

        bool res45 = db.KeyExists("bikes:repairs");
        Console.WriteLine(res45);   // >>> False
```

##### C#

```csharp
        db.KeyDelete("bikes:repairs");

        long res40 = db.ListLeftPush("bikes:repairs", ["bike:1", "bike:2", "bike:3"]);
        Console.WriteLine(res40);   // >>> 3

        bool res41 = db.KeyExists("bikes:repairs");
        Console.WriteLine(res41);   // >>> True

        RedisValue res42 = db.ListLeftPop("bikes:repairs");
        Console.WriteLine(res42);   // >>> "bike:3"

        RedisValue res43 = db.ListLeftPop("bikes:repairs");
        Console.WriteLine(res43);   // >>> "bike:2"

        RedisValue res44 = db.ListLeftPop("bikes:repairs");
        Console.WriteLine(res44);   //  >>> "bike:1"

        bool res45 = db.KeyExists("bikes:repairs");
        Console.WriteLine(res45);   // >>> False
```

##### C#

```csharp
        long res40 = db.ListLeftPush("bikes:repairs", ["bike:1", "bike:2", "bike:3"]);
        Console.WriteLine(res40);   // >>> 3

        bool res41 = db.KeyExists("bikes:repairs");
        Console.WriteLine(res41);   // >>> True

        RedisValue res42 = db.ListLeftPop("bikes:repairs");
        Console.WriteLine(res42);   // >>> "bike:3"

        RedisValue res43 = db.ListLeftPop("bikes:repairs");
        Console.WriteLine(res43);   // >>> "bike:2"

        RedisValue res44 = db.ListLeftPop("bikes:repairs");
        Console.WriteLine(res44);   //  >>> "bike:1"

        bool res45 = db.KeyExists("bikes:repairs");
        Console.WriteLine(res45);   // >>> False
```

##### C#

```csharp
        db.KeyDelete("bikes:repairs");

        long res40 = db.ListLeftPush("bikes:repairs", ["bike:1", "bike:2", "bike:3"]);
        Console.WriteLine(res40);   // >>> 3

        bool res41 = db.KeyExists("bikes:repairs");
        Console.WriteLine(res41);   // >>> True

        RedisValue res42 = db.ListLeftPop("bikes:repairs");
        Console.WriteLine(res42);   // >>> "bike:3"

        RedisValue res43 = db.ListLeftPop("bikes:repairs");
        Console.WriteLine(res43);   // >>> "bike:2"

        RedisValue res44 = db.ListLeftPop("bikes:repairs");
        Console.WriteLine(res44);   //  >>> "bike:1"

        bool res45 = db.KeyExists("bikes:repairs");
        Console.WriteLine(res45);   // >>> False
```

##### Go

```go
	rdb.Del(ctx, "bikes:repairs")
	res42, err := rdb.LPush(ctx, "bikes:repairs", "bike:1", "bike:2", "bike:3").Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res42) // >>> 3

	res43, err := rdb.Exists(ctx, "bikes:repairs").Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res43) // >>> 1

	res44, err := rdb.LPop(ctx, "bikes:repairs").Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res44) // >>> bike:3

	res45, err := rdb.LPop(ctx, "bikes:repairs").Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res45) // >>> bike:2

	res46, err := rdb.LPop(ctx, "bikes:repairs").Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res46) // >>> bike:1

	res47, err := rdb.Exists(ctx, "bikes:repairs").Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res47) // >>> 0
```

##### Java (Asynchronous - Lettuce)

```java
            CompletableFuture<Void> rule2 = asyncCommands.lpush("bikes:repairs", "bike:1", "bike:2", "bike:3")
                    .thenCompose(res30 -> {
                        System.out.println(res30); // >>> 3

                        return asyncCommands.exists("bikes:repairs");
                    }).thenCompose(res31 -> {
                        System.out.println(res31); // >>> 1

                        return asyncCommands.lpop("bikes:repairs");
                    }).thenCompose(res32 -> {
                        System.out.println(res32); // >>> bike:3

                        return asyncCommands.lpop("bikes:repairs");
                    }).thenCompose(res33 -> {
                        System.out.println(res33); // >>> bike:2

                        return asyncCommands.lpop("bikes:repairs");
                    }).thenCompose(res34 -> {
                        System.out.println(res34); // >>> bike:1

                        return asyncCommands.exists("bikes:repairs");
                    })
                    .thenAccept(System.out::println) // >>> 0
                    .toCompletableFuture();
```

##### Java (Synchronous - Jedis)

```java
        jedis.del("bikes:repairs");

        jedis.lpush("bikes:repairs", "bike:1", "bike:2", "bike:3");
        System.out.println(res36);  // >>> 3

        boolean res40 = jedis.exists("bikes:repairs");
        System.out.println(res40);  // >>> true

        String res41 = jedis.lpop("bikes:repairs");
        System.out.println(res41);  // >>> bike:3

        String res42 = jedis.lpop("bikes:repairs");
        System.out.println(res42);  // >>> bike:2

        String res43 = jedis.lpop("bikes:repairs");
        System.out.println(res43);  // >>> bike:1

        boolean res44 = jedis.exists("bikes:repairs");
        System.out.println(res44);  // >>> false
```

##### JavaScript (Node.js)

```javascript
await client.del('bikes:repairs');

await client.lPush('bikes:repairs', ['bike:1', 'bike:2', 'bike:3']);
console.log(res36);  // 3

const res40 = await client.exists('bikes:repairs')
console.log(res40);  // 1

const res41 = await client.lPop('bikes:repairs');
console.log(res41);  // 'bike:3'

const res42 = await client.lPop('bikes:repairs');
console.log(res42);  // 'bike:2'

const res43 = await client.lPop('bikes:repairs');
console.log(res43);  // 'bike:1'

const res44 = await client.exists('bikes:repairs');
console.log(res44);  // 0
```

##### PHP

```php
        $r->del('bikes:repairs');
        $res36 = $r->lpush('bikes:repairs', 'bike:1', 'bike:2', 'bike:3');
        echo $res36 . PHP_EOL;
        // >>> 3

        $res40 = $r->exists('bikes:repairs');
        echo $res40 . PHP_EOL;
        // >>> 1

        $res41 = $r->lpop('bikes:repairs');
        echo $res41 . PHP_EOL;
        // >>> 'bike:3'

        $res42 = $r->lpop('bikes:repairs');
        echo $res42 . PHP_EOL;
        // >>> 'bike:2'

        $res43 = $r->lpop('bikes:repairs');
        echo $res43 . PHP_EOL;
        // >>> 'bike:1'

        $res44 = $r->exists('bikes:repairs');
        echo $res44 . PHP_EOL;
        // >>> False
```

##### Python

```python
r.delete("bikes:repairs")

r.lpush("bikes:repairs", "bike:1", "bike:2", "bike:3")
print(res36)  # >>> 3

res40 = r.exists("bikes:repairs")
print(res40)  # >>> 1

res41 = r.lpop("bikes:repairs")
print(res41)  # >>> 'bike:3'

res42 = r.lpop("bikes:repairs")
print(res42)  # >>> 'bike:2'

res43 = r.lpop("bikes:repairs")
print(res43)  # >>> 'bike:1'

res44 = r.exists("bikes:repairs")
print(res44)  # >>> False
```

##### Ruby

```ruby
r.del('bikes:repairs')

res44 = r.lpush('bikes:repairs', ['bike:1', 'bike:2', 'bike:3'])
puts res44 # >>> 3

res45 = r.exists('bikes:repairs')
puts res45 # >>> 1

res46 = r.lpop('bikes:repairs')
puts res46 # >>> bike:3

res47 = r.lpop('bikes:repairs')
puts res47 # >>> bike:2

res48 = r.lpop('bikes:repairs')
puts res48 # >>> bike:1

res49 = r.exists('bikes:repairs')
puts res49 # >>> 0
```

##### Rust (Asynchronous)

```rust
        let _: usize = r.rpush("bikes:repairs", "bike:placeholder").await.unwrap_or(0);
        if let Ok(res) = r.del("bikes:repairs").await {
            let res: usize = res;
            println!("{res}"); // >>> 1
        }

        if let Ok(res) = r.lpush("bikes:repairs", &["bike:1", "bike:2", "bike:3"]).await {
            let res: usize = res;
            println!("{res}"); // >>> 3
        }

        if let Ok(res) = r.exists("bikes:repairs").await {
            let res: bool = res;
            println!("{}", i32::from(res)); // >>> 1
        }

        if let Ok(res) = r.lpop("bikes:repairs", None).await {
            let res: Option<String> = res;
            print_optional_string(res.clone()); // >>> bike:3
        }

        if let Ok(res) = r.lpop("bikes:repairs", None).await {
            let res: Option<String> = res;
            print_optional_string(res.clone()); // >>> bike:2
        }

        if let Ok(res) = r.lpop("bikes:repairs", None).await {
            let res: Option<String> = res;
            print_optional_string(res.clone()); // >>> bike:1
        }

        if let Ok(res) = r.exists("bikes:repairs").await {
            let res: bool = res;
            println!("{}", i32::from(res)); // >>> 0
        }
```

##### Rust (Synchronous)

```rust
        let _: usize = r.rpush("bikes:repairs", "bike:placeholder").unwrap_or(0);
        if let Ok(res) = r.del("bikes:repairs") {
            let res: usize = res;
            println!("{res}"); // >>> 1
        }

        if let Ok(res) = r.lpush("bikes:repairs", &["bike:1", "bike:2", "bike:3"]) {
            let res: usize = res;
            println!("{res}"); // >>> 3
        }

        if let Ok(res) = r.exists("bikes:repairs") {
            let res: bool = res;
            println!("{}", i32::from(res)); // >>> 1
        }

        if let Ok(res) = r.lpop("bikes:repairs", None) {
            let res: Option<String> = res;
            print_optional_string(res.clone()); // >>> bike:3
        }

        if let Ok(res) = r.lpop("bikes:repairs", None) {
            let res: Option<String> = res;
            print_optional_string(res.clone()); // >>> bike:2
        }

        if let Ok(res) = r.lpop("bikes:repairs", None) {
            let res: Option<String> = res;
            print_optional_string(res.clone()); // >>> bike:1
        }

        if let Ok(res) = r.exists("bikes:repairs") {
            let res: bool = res;
            println!("{}", i32::from(res)); // >>> 0
        }
```



Key không còn tồn tại sau khi tất cả các phần tử đã bị pop ra.

Ví dụ về quy tắc 3:

*Xử lý nil: Các lệnh chỉ đọc trên key không tồn tại trả về kết quả rỗng (0 hoặc nil) thay vì lỗi, coi chúng như các list rỗng*

**Lệnh (Commands):** DEL, LLEN, LPOP

**Độ phức tạp (Complexity):**
- DEL: O(N)
- LLEN: O(1)
- LPOP: O(N)

**Có sẵn trong (Available in):** Redis CLI, C#, C#, Go, Java (Asynchronous - Lettuce), Java (Synchronous - Jedis), JavaScript (Node.js), PHP, Python, Ruby, Rust (Asynchronous), Rust (Synchronous)

##### Redis CLI

```
> DEL bikes:repairs
(integer) 1
> LLEN bikes:repairs
(integer) 0
> LPOP bikes:repairs
(nil)
```

##### C#

```csharp
        bool res46 = db.KeyDelete("bikes:repairs");
        Console.WriteLine(res46);   // >>> False

        long res47 = db.ListLength("bikes:repairs");
        Console.WriteLine(res47);   // >>> 0

        RedisValue res48 = db.ListLeftPop("bikes:repairs");
        Console.WriteLine(res48);   // >>> Null
```

##### C#

```csharp
        db.KeyDelete("bikes:repairs");

        long res47 = db.ListLength("bikes:repairs");
        Console.WriteLine(res47);   // >>> 0

        RedisValue res48 = db.ListLeftPop("bikes:repairs");
        Console.WriteLine(res48);   // >>> Null
```

##### C#

```csharp
        bool res46 = db.KeyDelete("bikes:repairs");
        Console.WriteLine(res46);   // >>> False

        long res47 = db.ListLength("bikes:repairs");
        Console.WriteLine(res47);   // >>> 0

        RedisValue res48 = db.ListLeftPop("bikes:repairs");
        Console.WriteLine(res48);   // >>> Null
```

##### C#

```csharp
        db.KeyDelete("bikes:repairs");

        long res47 = db.ListLength("bikes:repairs");
        Console.WriteLine(res47);   // >>> 0

        RedisValue res48 = db.ListLeftPop("bikes:repairs");
        Console.WriteLine(res48);   // >>> Null
```

##### Go

```go
	rdb.Del(ctx, "bikes:repairs")

	res48, err := rdb.Del(ctx, "bikes:repairs").Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res48) // >>> 0

	res49, err := rdb.LLen(ctx, "bikes:repairs").Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res49) // >>> 0

	res50, err := rdb.LPop(ctx, "bikes:repairs").Result()

	if err != nil {
		fmt.Println(err) // >>> redis: nil
	}

	fmt.Println(res50) // >>> <empty string>
```

##### Java (Asynchronous - Lettuce)

```java
            CompletableFuture<Void> rule3 = asyncCommands.del("bikes:repairs").thenCompose(res35 -> {
                System.out.println(res35); // >>> 0

                return asyncCommands.llen("bikes:repairs");
            }).thenCompose(res36 -> {
                System.out.println(res36); // >>> 0

                return asyncCommands.lpop("bikes:repairs");
            })
                    .thenAccept(System.out::println) // >>> null
                    .toCompletableFuture();
```

##### Java (Synchronous - Jedis)

```java
        jedis.del("bikes:repairs");

        long res46 = jedis.llen("bikes:repairs");
        System.out.println(res46);  // >>> 0

        String res47 = jedis.lpop("bikes:repairs");
        System.out.println(res47);  // >>> null
```

##### JavaScript (Node.js)

```javascript
await client.del('bikes:repairs');

const res45 = await client.del('bikes:repairs');
console.log(res45);  // 0

const res46 = await client.lLen('bikes:repairs');
console.log(res46);  // 0

const res47 = await client.lPop('bikes:repairs');
console.log(res47);  // null
```

##### PHP

```php
        $res45 = $r->del('bikes:repairs');
        echo $res45 . PHP_EOL;
        // >>> 0

        $res46 = $r->llen('bikes:repairs');
        echo $res46 . PHP_EOL;
        // >>> 0

        $res47 = $r->lpop('bikes:repairs');
        echo $res47 . PHP_EOL;
        // >>> None
```

##### Python

```python
r.delete("bikes:repairs")

res45 = r.delete("bikes:repairs")
print(res45)  # >>> 0

res46 = r.llen("bikes:repairs")
print(res46)  # >>> 0

res47 = r.lpop("bikes:repairs")
print(res47)  # >>> None
```

##### Ruby

```ruby
r.del('bikes:repairs')

res50 = r.del('bikes:repairs')
puts res50 # >>> 0

res51 = r.llen('bikes:repairs')
puts res51 # >>> 0

res52 = r.lpop('bikes:repairs')
puts res52.inspect # >>> nil
```

##### Rust (Asynchronous)

```rust
        if let Ok(res) = r.del("bikes:repairs").await {
            let res: usize = res;
            println!("{res}"); // >>> 0
        }

        if let Ok(res) = r.llen("bikes:repairs").await {
            let res: usize = res;
            println!("{res}"); // >>> 0
        }

        if let Ok(res) = r.lpop("bikes:repairs", None).await {
            let res: Option<String> = res;
            print_optional_string(res.clone()); // >>> (nil)
        }
```

##### Rust (Synchronous)

```rust
        if let Ok(res) = r.del("bikes:repairs") {
            let res: usize = res;
            println!("{res}"); // >>> 0
        }

        if let Ok(res) = r.llen("bikes:repairs") {
            let res: usize = res;
            println!("{res}"); // >>> 0
        }

        if let Ok(res) = r.lpop("bikes:repairs", None) {
            let res: Option<String> = res;
            print_optional_string(res.clone()); // >>> (nil)
        }
```




## Giới hạn (Limits)

Độ dài tối đa của một Redis list là 2^32 - 1 (4,294,967,295) phần tử.

## Hiệu năng (Performance)

Các thao tác trên list truy cập phần đầu hoặc cuối của nó là O(1), nghĩa là chúng rất hiệu quả.
Tuy nhiên, các lệnh thao tác trên các phần tử bên trong list thường là O(n).
Ví dụ về các lệnh này gồm [`LINDEX`](https://redis.io/docs/latest/commands/lindex), [`LINSERT`](https://redis.io/docs/latest/commands/linsert), và [`LSET`](https://redis.io/docs/latest/commands/lset).
Hãy thận trọng khi chạy các lệnh này, chủ yếu là khi thao tác trên các list lớn.

## Các lựa chọn thay thế (Alternatives)

Hãy cân nhắc [Redis streams](https://redis.io/docs/latest/develop/data-types/streams) như một lựa chọn thay thế cho list khi bạn cần lưu và xử lý một chuỗi sự kiện không xác định trước.

## Tìm hiểu thêm (Learn more)

* [Redis Lists Explained](https://www.youtube.com/watch?v=PB5SeOkkxQc) là một video ngắn, giải thích toàn diện về Redis list.
* [Redis University's RU101](https://university.redis.com/courses/ru101/) trình bày chi tiết về Redis list.

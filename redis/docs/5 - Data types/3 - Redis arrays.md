# Redis arrays

> Nguồn: <https://redis.io/docs/latest/develop/data-types/arrays/>
> Giới thiệu về Redis arrays

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


### Tóm tắt các lệnh array (Array command summary)

**18 lệnh trong nhóm này:**

[Xem tất cả các lệnh array](https://redis.io/commands/?group=array)

| Lệnh | Tóm tắt | Độ phức tạp | Từ phiên bản |
|---------|---------|------------|-------|
| [ARCOUNT](https://redis.io/commands/arcount/) | Trả về số phần tử không rỗng trong một array. | O(1) | 8.8.0 |
| [ARDEL](https://redis.io/commands/ardel/) | Xóa các phần tử tại các chỉ số được chỉ định trong một array. | O(N) với N là số chỉ số cần xóa | 8.8.0 |
| [ARDELRANGE](https://redis.io/commands/ardelrange/) | Xóa các phần tử trong một hoặc nhiều dải (range). | Tỷ lệ với số phần tử hiện có... | 8.8.0 |
| [ARGET](https://redis.io/commands/arget/) | Lấy value tại một chỉ số trong một array. | O(1) | 8.8.0 |
| [ARGETRANGE](https://redis.io/commands/argetrange/) | Lấy các value trong một dải chỉ số. | O(N) với N là độ dài của dải | 8.8.0 |
| [ARGREP](https://redis.io/commands/argrep/) | Tìm kiếm các phần tử array trong một dải bằng các điều kiện văn bản (textual predicate). | O(P * C) với P là số vị trí được duyệt qua... | 8.8.0 |
| [ARINFO](https://redis.io/commands/arinfo/) | Trả về metadata về một array. | O(1), hoặc O(N) với tùy chọn FULL, trong đó N là... | 8.8.0 |
| [ARINSERT](https://redis.io/commands/arinsert/) | Chèn một hoặc nhiều value tại các chỉ số liên tiếp. | O(N) với N là số value | 8.8.0 |
| [ARLASTITEMS](https://redis.io/commands/arlastitems/) | Trả về các phần tử được chèn gần đây nhất. | O(N) với N là count | 8.8.0 |
| [ARLEN](https://redis.io/commands/arlen/) | Trả về độ dài của một array (chỉ số lớn nhất + 1). | O(1) | 8.8.0 |

#### Tóm tắt các lệnh array (Array command summary) (part 2)

| Lệnh | Tóm tắt | Độ phức tạp | Từ phiên bản |
|---------|---------|------------|-------|
| [ARMGET](https://redis.io/commands/armget/) | Lấy các value tại nhiều chỉ số trong một array. | O(N) với N là số chỉ số | 8.8.0 |
| [ARMSET](https://redis.io/commands/armset/) | Đặt nhiều cặp chỉ số-value trong một array. | O(N) với N là số cặp | 8.8.0 |
| [ARNEXT](https://redis.io/commands/arnext/) | Trả về chỉ số tiếp theo mà ARINSERT sẽ dùng. | O(1) | 8.8.0 |
| [AROP](https://redis.io/commands/arop/) | Thực hiện các phép tổng hợp (aggregate) trên các phần tử array trong một dải. | O(P) với P là số vị trí được duyệt trong các slice được chạm tới... | 8.8.0 |
| [ARRING](https://redis.io/commands/arring/) | Chèn các value vào một ring buffer có kích thước chỉ định, quay vòng (wrap) và cắt bớt (truncate) khi cần. | O(M) thông thường, O(N+M) khi thay đổi kích thước ring, với N là... | 8.8.0 |
| [ARSCAN](https://redis.io/commands/arscan/) | Duyệt các phần tử đang tồn tại trong một dải, trả về các cặp chỉ số-value. | O(P) với P là số vị trí được duyệt trong các slice được chạm tới... | 8.8.0 |
| [ARSEEK](https://redis.io/commands/arseek/) | Đặt con trỏ (cursor) của ARINSERT / ARRING tới một chỉ số cụ thể. | O(1) | 8.8.0 |
| [ARSET](https://redis.io/commands/arset/) | Đặt một hoặc nhiều value liên tiếp bắt đầu từ một chỉ số trong một array. | O(N) với N là số value | 8.8.0 |



Redis array là các cấu trúc dữ liệu thưa (sparse), có thể truy cập theo chỉ số (index-addressable), ánh xạ các chỉ số nguyên (trong khoảng 0 đến 2⁶⁴−1) tới các value kiểu string. Không giống list, các phần tử được truy cập trực tiếp theo chỉ số thay vì theo vị trí trong một dãy, và bạn có thể đặt bất kỳ chỉ số nào mà không phải cấp phát các khoảng trống giữa các slot đã được dùng. Điều này khiến array rất phù hợp cho log sự kiện có gắn timestamp, ring buffer cho các phép đo dạng streaming, phân tích theo cửa sổ trượt (sliding-window analytics), và các workload khác có kiểu truy cập thưa hoặc chỉ số lớn.

## Cách dùng cơ bản (Basic usage)

Dùng [`ARSET`](https://redis.io/docs/latest/commands/arset) để ghi một hoặc nhiều value liên tiếp bắt đầu từ một chỉ số cho trước, và [`ARGET`](https://redis.io/docs/latest/commands/arget) để đọc value tại một chỉ số. Truy cập một chỉ số chưa được đặt sẽ trả về nil reply.

*Ghi các value liên tiếp bằng ARSET và đọc một chỉ số bằng ARGET; chỉ số chưa được đặt trả về nil*

**Lệnh (Commands):** ARSET, ARGET

**Độ phức tạp (Complexity):**
- ARSET: O(N)
- ARGET: O(1)

**Có sẵn trong (Available in):** Redis CLI, Go, Java (Asynchronous - Lettuce), Java (Reactive - Lettuce), JavaScript (Node.js), PHP, Python

##### Redis CLI

```
> ARSET events:1 0 "login" "click" "purchase"
(integer) 3
> ARGET events:1 0
"login"
> ARGET events:1 999
(nil)
```

##### Go

```go
	setRes, err := rdb.ARSet(ctx, "events:1", 0, "login", "click", "purchase").Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(setRes) // >>> 3

	getRes, err := rdb.ARGet(ctx, "events:1", 0).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(getRes) // >>> login

	missing, err := rdb.ARGet(ctx, "events:1", 999).Result()

	if err == redis.Nil {
		fmt.Println("<nil>") // >>> <nil>
	} else if err != nil {
		panic(err)
	} else {
		fmt.Println(missing)
	}
```

##### Java (Asynchronous - Lettuce)

```java
            CompletableFuture<Void> arsetArgetExample = asyncCommands
                    .arset("events:1", 0, "login", "click", "purchase")
                    .thenCompose(res1 -> {
                        System.out.println(res1);
                        // >>> 3
                        return asyncCommands.arget("events:1", 0);
                    })
                    .thenCompose(res2 -> {
                        System.out.println(res2);
                        // >>> login
                        return asyncCommands.arget("events:1", 999);
                    })
                    .thenAccept(res3 -> {
                        System.out.println(res3);
                        // >>> null
                    })
                    .toCompletableFuture();

            arsetArgetExample.join();
```

##### Java (Reactive - Lettuce)

```java
            Mono<Long> arsetArget1 = reactiveCommands.arset("events:1", 0, "login", "click", "purchase")
                    .doOnNext(result -> {
                        System.out.println(result); // >>> 3
                    });

            arsetArget1.block();

            Mono<String> arsetArget2 = reactiveCommands.arget("events:1", 0).doOnNext(result -> {
                System.out.println(result); // >>> login
            });

            arsetArget2.block();

            Mono<Optional<String>> arsetArget3 = reactiveCommands.arget("events:1", 999)
                    .map(Optional::of)
                    .defaultIfEmpty(Optional.empty())
                    .doOnNext(result -> {
                        System.out.println(result.orElse(null)); // >>> null
                    });

            arsetArget3.block();
```

##### JavaScript (Node.js)

```javascript
const setResult = await client.arSet('events:1', 0, ['login', 'click', 'purchase']);
console.log(setResult); // >>> 3

const getResult = await client.arGet('events:1', 0);
console.log(getResult); // >>> login

const missingResult = await client.arGet('events:1', 999);
console.log(missingResult); // >>> null
```

##### PHP

```php
        $res1 = $redis->arset('events:1', 0, ['login', 'click', 'purchase']);
        echo $res1 . PHP_EOL; // >>> 3

        $res2 = $redis->arget('events:1', 0);
        echo $res2 . PHP_EOL; // >>> login

        $res3 = $redis->arget('events:1', 999);
        echo var_export($res3, true) . PHP_EOL; // >>> NULL
```

##### Python

```python
res1 = r.arset("events:1", 0, "login", "click", "purchase")
print(res1)
# >>> 3

res2 = r.arget("events:1", 0)
print(res2)
# >>> login

res3 = r.arget("events:1", 999)
print(res3)
# >>> None
```



Để ghi value tại các chỉ số tùy ý, không liên tiếp, dùng [`ARMSET`](https://redis.io/docs/latest/commands/armset). Để đọc nhiều chỉ số trong một lần round trip, dùng [`ARMGET`](https://redis.io/docs/latest/commands/armget):

*Ghi vào các chỉ số tùy ý, không liên tiếp bằng ARMSET và đọc nhiều chỉ số trong một lần round trip bằng ARMGET*

**Lệnh (Commands):** ARMSET, ARMGET

**Độ phức tạp (Complexity):**
- ARMSET: O(N)
- ARMGET: O(N)

**Có sẵn trong (Available in):** Redis CLI, Go, Java (Asynchronous - Lettuce), Java (Reactive - Lettuce), JavaScript (Node.js), PHP, Python

##### Redis CLI

```
> ARMSET metrics 0 "10" 5 "20" 100 "30"
(integer) 3
> ARMGET metrics 0 5 100 999
1) "10"
2) "20"
3) "30"
4) (nil)
```

##### Go

```go
	msetRes, err := rdb.ARMSet(ctx, "metrics",
		redis.AREntry{Index: 0, Value: "10"},
		redis.AREntry{Index: 5, Value: "20"},
		redis.AREntry{Index: 100, Value: "30"},
	).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(msetRes) // >>> 3

	mgetRes, err := rdb.ARMGet(ctx, "metrics", 0, 5, 100, 999).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(mgetRes) // >>> [10 20 30 <nil>]
```

##### Java (Asynchronous - Lettuce)

```java
            Map<Long, String> metricsValues = new HashMap<>();
            metricsValues.put(0L, "10");
            metricsValues.put(5L, "20");
            metricsValues.put(100L, "30");

            CompletableFuture<Void> armsetArmgetExample = asyncCommands
                    .armset("metrics", metricsValues)
                    .thenCompose(res1 -> {
                        System.out.println(res1);
                        // >>> 3
                        return asyncCommands.armget("metrics", 0, 5, 100, 999);
                    })
                    .thenAccept(res2 -> {
                        System.out.println(res2);
                        // >>> [10, 20, 30, null]
                    })
                    .toCompletableFuture();

            armsetArmgetExample.join();
```

##### Java (Reactive - Lettuce)

```java
            Map<Long, String> armsetParams = new HashMap<>();
            armsetParams.put(0L, "10");
            armsetParams.put(5L, "20");
            armsetParams.put(100L, "30");

            Mono<Long> armsetArmget1 = reactiveCommands.armset("metrics", armsetParams).doOnNext(result -> {
                System.out.println(result); // >>> 3
            });

            armsetArmget1.block();

            Mono<List<String>> armsetArmget2 = reactiveCommands.armget("metrics", 0, 5, 100, 999)
                    .collectList()
                    .map(values -> values.stream()
                            .map(value -> value.getValueOrElse(null))
                            .collect(Collectors.toList()))
                    .doOnNext(result -> {
                        System.out.println(result); // >>> [10, 20, 30, null]
                    });

            armsetArmget2.block();
```

##### JavaScript (Node.js)

```javascript
const mSetResult = await client.arMSet('metrics', { 0: '10', 5: '20', 100: '30' });
console.log(mSetResult); // >>> 3

const mGetResult = await client.arMGet('metrics', [0, 5, 100, 999]);
console.log(mGetResult); // >>> [ '10', '20', '30', null ]
```

##### PHP

```php
        $res1 = $redis->armset('metrics', [0 => '10', 5 => '20', 100 => '30']);
        echo $res1 . PHP_EOL; // >>> 3

        $res2 = $redis->armget('metrics', [0, 5, 100, 999]);
        echo json_encode($res2) . PHP_EOL; // >>> ["10","20","30",null]
```

##### Python

```python
res4 = r.armset("metrics", {0: "10", 5: "20", 100: "30"})
print(res4)
# >>> 3

res5 = r.armget("metrics", 0, 5, 100, 999)
print(res5)
# >>> ['10', '20', '30', None]
```



## Độ dài array so với số lượng phần tử (Array length vs. element count)

Redis array cung cấp hai cách đo kích thước khác nhau:

- [`ARLEN`](https://redis.io/docs/latest/commands/arlen) trả về *độ dài logic (logical length)*: chỉ số được đặt cao nhất cộng một.
- [`ARCOUNT`](https://redis.io/docs/latest/commands/arcount) trả về số phần tử *không rỗng (non-empty)*.

Với một array thưa, hai giá trị này có thể khác nhau đáng kể:

*So sánh độ dài logic (ARLEN) với số phần tử không rỗng (ARCOUNT) của một array thưa*

**Lệnh (Commands):** ARSET, ARLEN, ARCOUNT

**Độ phức tạp (Complexity):**
- ARSET: O(N)
- ARLEN: O(1)
- ARCOUNT: O(1)

**Có sẵn trong (Available in):** Redis CLI, Go, Java (Asynchronous - Lettuce), Java (Reactive - Lettuce), JavaScript (Node.js), PHP, Python

##### Redis CLI

```
> ARSET sparse 0 "a"
(integer) 1
> ARSET sparse 1000000 "b"
(integer) 1
> ARLEN sparse
(integer) 1000001
> ARCOUNT sparse
(integer) 2
```

##### Go

```go
	set1, err := rdb.ARSet(ctx, "sparse", 0, "a").Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(set1) // >>> 1

	set2, err := rdb.ARSet(ctx, "sparse", 1000000, "b").Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(set2) // >>> 1

	lenRes, err := rdb.ARLen(ctx, "sparse").Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(lenRes) // >>> 1000001

	countRes, err := rdb.ARCount(ctx, "sparse").Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(countRes) // >>> 2
```

##### Java (Asynchronous - Lettuce)

```java
            CompletableFuture<Void> lenCountExample = asyncCommands
                    .arset("sparse", 0, "a")
                    .thenCompose(res1 -> {
                        System.out.println(res1);
                        // >>> 1
                        return asyncCommands.arset("sparse", 1000000, "b");
                    })
                    .thenCompose(res2 -> {
                        System.out.println(res2);
                        // >>> 1
                        return asyncCommands.arlen("sparse");
                    })
                    .thenCompose(res3 -> {
                        System.out.println(res3);
                        // >>> 1000001
                        return asyncCommands.arcount("sparse");
                    })
                    .thenAccept(res4 -> {
                        System.out.println(res4);
                        // >>> 2
                    })
                    .toCompletableFuture();

            lenCountExample.join();
```

##### Java (Reactive - Lettuce)

```java
            Mono<Long> lenCount1 = reactiveCommands.arset("sparse", 0, "a").doOnNext(result -> {
                System.out.println(result); // >>> 1
            });

            lenCount1.block();

            Mono<Long> lenCount2 = reactiveCommands.arset("sparse", 1000000, "b").doOnNext(result -> {
                System.out.println(result); // >>> 1
            });

            lenCount2.block();

            Mono<Long> lenCount3 = reactiveCommands.arlen("sparse").doOnNext(result -> {
                System.out.println(result); // >>> 1000001
            });

            lenCount3.block();

            Mono<Long> lenCount4 = reactiveCommands.arcount("sparse").doOnNext(result -> {
                System.out.println(result); // >>> 2
            });

            lenCount4.block();
```

##### JavaScript (Node.js)

```javascript
const setA = await client.arSet('sparse', 0, 'a');
console.log(setA); // >>> 1

const setB = await client.arSet('sparse', 1000000, 'b');
console.log(setB); // >>> 1

const lenResult = await client.arLen('sparse');
console.log(lenResult); // >>> 1000001

const countResult = await client.arCount('sparse');
console.log(countResult); // >>> 2
```

##### PHP

```php
        $res1 = $redis->arset('sparse', 0, 'a');
        echo $res1 . PHP_EOL; // >>> 1

        $res2 = $redis->arset('sparse', 1000000, 'b');
        echo $res2 . PHP_EOL; // >>> 1

        $res3 = $redis->arlen('sparse');
        echo $res3 . PHP_EOL; // >>> 1000001

        $res4 = $redis->arcount('sparse');
        echo $res4 . PHP_EOL; // >>> 2
```

##### Python

```python
res6 = r.arset("sparse", 0, "a")
print(res6)
# >>> 1

res7 = r.arset("sparse", 1000000, "b")
print(res7)
# >>> 1

res8 = r.arlen("sparse")
print(res8)
# >>> 1000001

res9 = r.arcount("sparse")
print(res9)
# >>> 2
```



## Đọc theo dải (Reading ranges)

[`ARGETRANGE`](https://redis.io/docs/latest/commands/argetrange) trả về mọi vị trí trong một dải—bao gồm cả các slot rỗng dưới dạng nil—theo thứ tự chỉ số. Đảo ngược `start` và `end` sẽ đảo ngược chiều duyệt:

*Đọc mọi vị trí trong một dải bằng ARGETRANGE, bao gồm các slot rỗng được trả về dưới dạng nil*

**Lệnh (Commands):** ARMSET, ARGETRANGE

**Độ phức tạp (Complexity):**
- ARMSET: O(N)
- ARGETRANGE: O(N)

**Có sẵn trong (Available in):** Redis CLI, Go, Java (Asynchronous - Lettuce), Java (Reactive - Lettuce), JavaScript (Node.js), PHP, Python

##### Redis CLI

```
> ARMSET seq 0 "a" 1 "b" 3 "d"
(integer) 3
> ARGETRANGE seq 0 3
1) "a"
2) "b"
3) (nil)
4) "d"
```

##### Go

```go
	msetRes, err := rdb.ARMSet(ctx, "seq",
		redis.AREntry{Index: 0, Value: "a"},
		redis.AREntry{Index: 1, Value: "b"},
		redis.AREntry{Index: 3, Value: "d"},
	).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(msetRes) // >>> 3

	rangeRes, err := rdb.ARGetRange(ctx, "seq", 0, 3).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(rangeRes) // >>> [a b <nil> d]
```

##### Java (Asynchronous - Lettuce)

```java
            Map<Long, String> seqRangeValues = new HashMap<>();
            seqRangeValues.put(0L, "a");
            seqRangeValues.put(1L, "b");
            seqRangeValues.put(3L, "d");

            CompletableFuture<Void> argetrangeExample = asyncCommands
                    .armset("seq", seqRangeValues)
                    .thenCompose(res1 -> {
                        System.out.println(res1);
                        // >>> 3
                        return asyncCommands.argetrange("seq", 0, 3);
                    })
                    .thenAccept(res2 -> {
                        System.out.println(res2);
                        // >>> [a, b, null, d]
                    })
                    .toCompletableFuture();

            argetrangeExample.join();
```

##### Java (Reactive - Lettuce)

```java
            Map<Long, String> argetrangeParams = new HashMap<>();
            argetrangeParams.put(0L, "a");
            argetrangeParams.put(1L, "b");
            argetrangeParams.put(3L, "d");

            Mono<Long> argetrange1 = reactiveCommands.armset("seq", argetrangeParams).doOnNext(result -> {
                System.out.println(result); // >>> 3
            });

            argetrange1.block();

            Mono<List<String>> argetrange2 = reactiveCommands.argetrange("seq", 0, 3)
                    .collectList()
                    .map(values -> values.stream()
                            .map(value -> value.getValueOrElse(null))
                            .collect(Collectors.toList()))
                    .doOnNext(result -> {
                        System.out.println(result); // >>> [a, b, null, d]
                    });

            argetrange2.block();
```

##### JavaScript (Node.js)

```javascript
const rangeSetResult = await client.arMSet('seq', { 0: 'a', 1: 'b', 3: 'd' });
console.log(rangeSetResult); // >>> 3

const rangeResult = await client.arGetRange('seq', 0, 3);
console.log(rangeResult); // >>> [ 'a', 'b', null, 'd' ]
```

##### PHP

```php
        $res1 = $redis->armset('seq', [0 => 'a', 1 => 'b', 3 => 'd']);
        echo $res1 . PHP_EOL; // >>> 3

        $res2 = $redis->argetrange('seq', 0, 3);
        echo json_encode($res2) . PHP_EOL; // >>> ["a","b",null,"d"]
```

##### Python

```python
res10 = r.armset("seq", {0: "a", 1: "b", 3: "d"})
print(res10)
# >>> 3

res11 = r.argetrange("seq", 0, 3)
print(res11)
# >>> ['a', 'b', None, 'd']
```



Để chỉ duyệt các phần tử đang tồn tại và lấy chỉ số của chúng cùng với value, dùng [`ARSCAN`](https://redis.io/docs/latest/commands/arscan). Lệnh này bỏ qua các slot rỗng và trả về một mảng các cặp chỉ số-value, với tùy chọn `LIMIT` để giới hạn kích thước kết quả:

*Chỉ duyệt các phần tử đang tồn tại bằng ARSCAN, lấy từng chỉ số cùng với value của nó*

**Dựa trên (Builds upon):** argetrange

**Lệnh (Commands):** ARSCAN

**Độ phức tạp (Complexity):**
- ARSCAN: O(P)

**Có sẵn trong (Available in):** Redis CLI, Go, Java (Asynchronous - Lettuce), Java (Reactive - Lettuce), JavaScript (Node.js), PHP, Python

##### Redis CLI

```
> ARSCAN seq 0 3
1) 1) (integer) 0
   2) "a"
2) 1) (integer) 1
   2) "b"
3) 1) (integer) 3
   2) "d"
```

##### Go

```go
	msetRes, err := rdb.ARMSet(ctx, "seq",
		redis.AREntry{Index: 0, Value: "a"},
		redis.AREntry{Index: 1, Value: "b"},
		redis.AREntry{Index: 3, Value: "d"},
	).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(msetRes) // >>> 3

	scanRes, err := rdb.ARScan(ctx, "seq", 0, 3, nil).Result()

	if err != nil {
		panic(err)
	}

	for _, entry := range scanRes {
		fmt.Printf("%d -> %s\n", entry.Index, entry.Value)
	}
	// >>> 0 -> a
	// >>> 1 -> b
	// >>> 3 -> d
```

##### Java (Asynchronous - Lettuce)

```java
            Map<Long, String> seqScanValues = new HashMap<>();
            seqScanValues.put(0L, "a");
            seqScanValues.put(1L, "b");
            seqScanValues.put(3L, "d");

            CompletableFuture<Void> arscanExample = asyncCommands
                    .armset("seq", seqScanValues)
                    .thenCompose(res1 -> {
                        System.out.println(res1);
                        // >>> 3
                        return asyncCommands.arscan("seq", 0, 3);
                    })
                    .thenAccept(res2 -> {
                        for (IndexedValue<String> pair : res2) {
                            System.out.println(pair.getIndex() + " -> " + pair.getValue());
                        }
                        // >>> 0 -> a
                        // >>> 1 -> b
                        // >>> 3 -> d
                    })
                    .toCompletableFuture();

            arscanExample.join();
```

##### Java (Reactive - Lettuce)

```java
            Map<Long, String> arscanParams = new HashMap<>();
            arscanParams.put(0L, "a");
            arscanParams.put(1L, "b");
            arscanParams.put(3L, "d");

            Mono<Long> arscan1 = reactiveCommands.armset("seq", arscanParams).doOnNext(result -> {
                System.out.println(result); // >>> 3
            });

            arscan1.block();

            Mono<List<IndexedValue<String>>> arscan2 = reactiveCommands.arscan("seq", 0, 3)
                    .doOnNext(entry -> {
                        System.out.println(entry.getIndex() + " -> " + entry.getValue());
                        // >>> 0 -> a
                        // >>> 1 -> b
                        // >>> 3 -> d
                    })
                    .collectList()
            ;

            arscan2.block();
```

##### JavaScript (Node.js)

```javascript
const scanSetResult = await client.arMSet('seq', { 0: 'a', 1: 'b', 3: 'd' });
console.log(scanSetResult); // >>> 3

const scanResult = await client.arScan('seq', 0, 3);
for (const { index, value } of scanResult) {
  console.log(`${index} -> ${value}`);
}
// >>> 0 -> a
// >>> 1 -> b
// >>> 3 -> d
```

##### PHP

```php
        $res1 = $redis->armset('seq', [0 => 'a', 1 => 'b', 3 => 'd']);
        echo $res1 . PHP_EOL; // >>> 3

        $res2 = $redis->arscan('seq', 0, 3);
        foreach ($res2 as $pair) {
            echo $pair[0] . ' -> ' . $pair[1] . PHP_EOL;
        }
        // >>> 0 -> a
        // >>> 1 -> b
        // >>> 3 -> d
```

##### Python

```python
res12 = r.armset("seq", {0: "a", 1: "b", 3: "d"})
print(res12)
# >>> 3

res13 = r.arscan("seq", 0, 3)
for index, value in res13:
    print(f"{index} -> {value}")
# >>> 0 -> a
# >>> 1 -> b
# >>> 3 -> d
```



## Chèn tuần tự (Sequential insertion)

[`ARINSERT`](https://redis.io/docs/latest/commands/arinsert) ghi nối thêm (append) các value bằng một con trỏ nội bộ tự động tiến lên sau mỗi lần gọi. Dùng [`ARNEXT`](https://redis.io/docs/latest/commands/arnext) để xem lần chèn tiếp theo sẽ rơi vào đâu, và [`ARSEEK`](https://redis.io/docs/latest/commands/arseek) để đặt lại vị trí con trỏ:

*Ghi nối thêm value bằng ARINSERT với một con trỏ tự tiến, xem con trỏ bằng ARNEXT, và đặt lại vị trí bằng ARSEEK*

**Lệnh (Commands):** ARINSERT, ARNEXT, ARSEEK

**Độ phức tạp (Complexity):**
- ARINSERT: O(N)
- ARNEXT: O(1)
- ARSEEK: O(1)

**Có sẵn trong (Available in):** Redis CLI, Go, Java (Asynchronous - Lettuce), Java (Reactive - Lettuce), JavaScript (Node.js), PHP, Python

##### Redis CLI

```
> ARINSERT log "event1"
(integer) 0
> ARINSERT log "event2"
(integer) 1
> ARNEXT log
(integer) 2
> ARSEEK log 10
(integer) 1
> ARINSERT log "event3"
(integer) 10
```

##### Go

```go
	ins1, err := rdb.ARInsert(ctx, "log", "event1").Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(ins1) // >>> 0

	ins2, err := rdb.ARInsert(ctx, "log", "event2").Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(ins2) // >>> 1

	nextRes, err := rdb.ARNext(ctx, "log").Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(nextRes) // >>> 2

	seekRes, err := rdb.ARSeek(ctx, "log", 10).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(seekRes) // >>> 1

	ins3, err := rdb.ARInsert(ctx, "log", "event3").Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(ins3) // >>> 10
```

##### Java (Asynchronous - Lettuce)

```java
            CompletableFuture<Void> arinsertExample = asyncCommands
                    .arinsert("log", "event1")
                    .thenCompose(res1 -> {
                        System.out.println(res1);
                        // >>> 0
                        return asyncCommands.arinsert("log", "event2");
                    })
                    .thenCompose(res2 -> {
                        System.out.println(res2);
                        // >>> 1
                        return asyncCommands.arnext("log");
                    })
                    .thenCompose(res3 -> {
                        System.out.println(res3);
                        // >>> 2
                        return asyncCommands.arseek("log", 10);
                    })
                    .thenCompose(res4 -> {
                        System.out.println(res4);
                        // >>> 1
                        return asyncCommands.arinsert("log", "event3");
                    })
                    .thenAccept(res5 -> {
                        System.out.println(res5);
                        // >>> 10
                    })
                    .toCompletableFuture();

            arinsertExample.join();
```

##### Java (Reactive - Lettuce)

```java
            Mono<Long> arinsert1 = reactiveCommands.arinsert("log", "event1").doOnNext(result -> {
                System.out.println(result); // >>> 0
            });

            arinsert1.block();

            Mono<Long> arinsert2 = reactiveCommands.arinsert("log", "event2").doOnNext(result -> {
                System.out.println(result); // >>> 1
            });

            arinsert2.block();

            Mono<Long> arinsert3 = reactiveCommands.arnext("log").doOnNext(result -> {
                System.out.println(result); // >>> 2
            });

            arinsert3.block();

            Mono<Long> arinsert4 = reactiveCommands.arseek("log", 10).doOnNext(result -> {
                System.out.println(result); // >>> 1
            });

            arinsert4.block();

            Mono<Long> arinsert5 = reactiveCommands.arinsert("log", "event3").doOnNext(result -> {
                System.out.println(result); // >>> 10
            });

            arinsert5.block();
```

##### JavaScript (Node.js)

```javascript
const insert1 = await client.arInsert('log', 'event1');
console.log(insert1); // >>> 0

const insert2 = await client.arInsert('log', 'event2');
console.log(insert2); // >>> 1

const nextResult = await client.arNext('log');
console.log(nextResult); // >>> 2

const seekResult = await client.arSeek('log', 10);
console.log(seekResult); // >>> 1

const insert3 = await client.arInsert('log', 'event3');
console.log(insert3); // >>> 10
```

##### PHP

```php
        $res1 = $redis->arinsert('log', 'event1');
        echo $res1 . PHP_EOL; // >>> 0

        $res2 = $redis->arinsert('log', 'event2');
        echo $res2 . PHP_EOL; // >>> 1

        $res3 = $redis->arnext('log');
        echo $res3 . PHP_EOL; // >>> 2

        $res4 = $redis->arseek('log', 10);
        echo $res4 . PHP_EOL; // >>> 1

        $res5 = $redis->arinsert('log', 'event3');
        echo $res5 . PHP_EOL; // >>> 10
```

##### Python

```python
res14 = r.arinsert("log", "event1")
print(res14)
# >>> 0

res15 = r.arinsert("log", "event2")
print(res15)
# >>> 1

res16 = r.arnext("log")
print(res16)
# >>> 2

res17 = r.arseek("log", 10)
print(res17)
# >>> 1

res18 = r.arinsert("log", "event3")
print(res18)
# >>> 10
```



<a name="ring-buffer-mode"></a>

## Chế độ ring buffer (Ring buffer mode)

[`ARRING`](https://redis.io/docs/latest/commands/arring) biến một array thành một buffer vòng (circular buffer) có kích thước cố định. Mỗi lần gọi sẽ chèn một value tại `insert_idx % size`, quay vòng về chỉ số `0` khi cửa sổ đã đầy và ghi đè entry cũ nhất:

*Dùng ARRING để duy trì một buffer vòng kích thước cố định, quay vòng và ghi đè entry cũ nhất khi đầy*

**Lệnh (Commands):** ARRING, ARGET

**Độ phức tạp (Complexity):**
- ARRING: O(M)
- ARGET: O(1)

**Có sẵn trong (Available in):** Redis CLI, Go, Java (Asynchronous - Lettuce), Java (Reactive - Lettuce), JavaScript (Node.js), PHP, Python

##### Redis CLI

```
> ARRING readings 3 "v0"
(integer) 0
> ARRING readings 3 "v1"
(integer) 1
> ARRING readings 3 "v2"
(integer) 2
> ARRING readings 3 "v3"
(integer) 0
> ARGET readings 0
"v3"
```

##### Go

```go
	ring0, err := rdb.ARRing(ctx, "readings", 3, "v0").Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(ring0) // >>> 0

	ring1, err := rdb.ARRing(ctx, "readings", 3, "v1").Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(ring1) // >>> 1

	ring2, err := rdb.ARRing(ctx, "readings", 3, "v2").Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(ring2) // >>> 2

	ring3, err := rdb.ARRing(ctx, "readings", 3, "v3").Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(ring3) // >>> 0

	getRes, err := rdb.ARGet(ctx, "readings", 0).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(getRes) // >>> v3
```

##### Java (Asynchronous - Lettuce)

```java
            CompletableFuture<Void> arringExample = asyncCommands
                    .arring("readings", 3, "v0")
                    .thenCompose(res1 -> {
                        System.out.println(res1);
                        // >>> 0
                        return asyncCommands.arring("readings", 3, "v1");
                    })
                    .thenCompose(res2 -> {
                        System.out.println(res2);
                        // >>> 1
                        return asyncCommands.arring("readings", 3, "v2");
                    })
                    .thenCompose(res3 -> {
                        System.out.println(res3);
                        // >>> 2
                        return asyncCommands.arring("readings", 3, "v3");
                    })
                    .thenCompose(res4 -> {
                        System.out.println(res4);
                        // >>> 0
                        return asyncCommands.arget("readings", 0);
                    })
                    .thenAccept(res5 -> {
                        System.out.println(res5);
                        // >>> v3
                    })
                    .toCompletableFuture();

            arringExample.join();
```

##### Java (Reactive - Lettuce)

```java
            Mono<Long> arring1 = reactiveCommands.arring("readings", 3, "v0").doOnNext(result -> {
                System.out.println(result); // >>> 0
            });

            arring1.block();

            Mono<Long> arring2 = reactiveCommands.arring("readings", 3, "v1").doOnNext(result -> {
                System.out.println(result); // >>> 1
            });

            arring2.block();

            Mono<Long> arring3 = reactiveCommands.arring("readings", 3, "v2").doOnNext(result -> {
                System.out.println(result); // >>> 2
            });

            arring3.block();

            Mono<Long> arring4 = reactiveCommands.arring("readings", 3, "v3").doOnNext(result -> {
                System.out.println(result); // >>> 0
            });

            arring4.block();

            Mono<String> arring5 = reactiveCommands.arget("readings", 0).doOnNext(result -> {
                System.out.println(result); // >>> v3
            });

            arring5.block();
```

##### JavaScript (Node.js)

```javascript
const ring0 = await client.arRing('readings', 3, 'v0');
console.log(ring0); // >>> 0

const ring1 = await client.arRing('readings', 3, 'v1');
console.log(ring1); // >>> 1

const ring2 = await client.arRing('readings', 3, 'v2');
console.log(ring2); // >>> 2

const ring3 = await client.arRing('readings', 3, 'v3');
console.log(ring3); // >>> 0

const ringGet = await client.arGet('readings', 0);
console.log(ringGet); // >>> v3
```

##### PHP

```php
        $res1 = $redis->arring('readings', 3, 'v0');
        echo $res1 . PHP_EOL; // >>> 0

        $res2 = $redis->arring('readings', 3, 'v1');
        echo $res2 . PHP_EOL; // >>> 1

        $res3 = $redis->arring('readings', 3, 'v2');
        echo $res3 . PHP_EOL; // >>> 2

        $res4 = $redis->arring('readings', 3, 'v3');
        echo $res4 . PHP_EOL; // >>> 0

        $res5 = $redis->arget('readings', 0);
        echo $res5 . PHP_EOL; // >>> v3
```

##### Python

```python
res19 = r.arring("readings", 3, "v0")
print(res19)
# >>> 0

res20 = r.arring("readings", 3, "v1")
print(res20)
# >>> 1

res21 = r.arring("readings", 3, "v2")
print(res21)
# >>> 2

res22 = r.arring("readings", 3, "v3")
print(res22)
# >>> 0

res23 = r.arget("readings", 0)
print(res23)
# >>> v3
```



[`ARLASTITEMS`](https://redis.io/docs/latest/commands/arlastitems) lấy *N* phần tử được chèn gần đây nhất theo thứ tự thời gian. Truyền flag `REV` để đảo ngược thứ tự:

*Lấy N phần tử được chèn gần đây nhất bằng ARLASTITEMS, có thể đảo ngược thứ tự bằng REV*

**Dựa trên (Builds upon):** arring

**Lệnh (Commands):** ARLASTITEMS

**Độ phức tạp (Complexity):**
- ARLASTITEMS: O(N)

**Có sẵn trong (Available in):** Redis CLI, Go, Java (Asynchronous - Lettuce), Java (Reactive - Lettuce), JavaScript (Node.js), PHP, Python

##### Redis CLI

```
> ARLASTITEMS readings 3
1) "v1"
2) "v2"
3) "v3"
> ARLASTITEMS readings 3 REV
1) "v3"
2) "v2"
3) "v1"
```

##### Go

```go
	// Set up the ring: insert v0, v1, v2, v3 into a size-3 ring.
	for _, v := range []string{"v0", "v1", "v2", "v3"} {
		if err := rdb.ARRing(ctx, "readings", 3, v).Err(); err != nil {
			panic(err)
		}
	}

	lastRes, err := rdb.ARLastItems(ctx, "readings", 3, false).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(lastRes) // >>> [v1 v2 v3]

	lastRevRes, err := rdb.ARLastItems(ctx, "readings", 3, true).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(lastRevRes) // >>> [v3 v2 v1]
```

##### Java (Asynchronous - Lettuce)

```java
            CompletableFuture<Void> arlastitemsExample = asyncCommands
                    .arring("readings", 3, "v0")
                    .thenCompose(res1 -> asyncCommands.arring("readings", 3, "v1"))
                    .thenCompose(res2 -> asyncCommands.arring("readings", 3, "v2"))
                    .thenCompose(res3 -> asyncCommands.arring("readings", 3, "v3"))
                    .thenCompose(res4 -> asyncCommands.arlastitems("readings", 3))
                    .thenCompose(res5 -> {
                        System.out.println(res5);
                        // >>> [v1, v2, v3]
                        return asyncCommands.arlastitems("readings", 3, true);
                    })
                    .thenAccept(res6 -> {
                        System.out.println(res6);
                        // >>> [v3, v2, v1]
                    })
                    .toCompletableFuture();

            arlastitemsExample.join();
```

##### Java (Reactive - Lettuce)

```java
            // Set up the ring: insert v0, v1, v2, v3 into a size-3 ring.
            reactiveCommands.arring("readings", 3, "v0").block();
            reactiveCommands.arring("readings", 3, "v1").block();
            reactiveCommands.arring("readings", 3, "v2").block();
            reactiveCommands.arring("readings", 3, "v3").block();

            Mono<List<String>> arlastitems1 = reactiveCommands.arlastitems("readings", 3).collectList()
                    .doOnNext(result -> {
                        System.out.println(result); // >>> [v1, v2, v3]
                    });

            arlastitems1.block();

            Mono<List<String>> arlastitems2 = reactiveCommands.arlastitems("readings", 3, true).collectList()
                    .doOnNext(result -> {
                        System.out.println(result); // >>> [v3, v2, v1]
                    });

            arlastitems2.block();
```

##### JavaScript (Node.js)

```javascript
await client.arRing('readings', 3, 'v0');
await client.arRing('readings', 3, 'v1');
await client.arRing('readings', 3, 'v2');
await client.arRing('readings', 3, 'v3');

const lastItems = await client.arLastItems('readings', 3);
console.log(lastItems); // >>> [ 'v1', 'v2', 'v3' ]

const lastItemsRev = await client.arLastItems('readings', 3, { REV: true });
console.log(lastItemsRev); // >>> [ 'v3', 'v2', 'v1' ]
```

##### PHP

```php
        $redis->arring('readings', 3, 'v0');
        $redis->arring('readings', 3, 'v1');
        $redis->arring('readings', 3, 'v2');
        $redis->arring('readings', 3, 'v3');

        $res1 = $redis->arlastitems('readings', 3);
        echo json_encode($res1) . PHP_EOL; // >>> ["v1","v2","v3"]

        $res2 = $redis->arlastitems('readings', 3, true);
        echo json_encode($res2) . PHP_EOL; // >>> ["v3","v2","v1"]
```

##### Python

```python
r.arring("readings", 3, "v0")
r.arring("readings", 3, "v1")
r.arring("readings", 3, "v2")
r.arring("readings", 3, "v3")

res24 = r.arlastitems("readings", 3)
print(res24)
# >>> ['v1', 'v2', 'v3']

res25 = r.arlastitems("readings", 3, rev=True)
print(res25)
# >>> ['v3', 'v2', 'v1']
```



## Các phép tổng hợp (Aggregate operations)

[`AROP`](https://redis.io/docs/latest/commands/arop) thực hiện một phép tổng hợp trong một lượt duyệt (single-pass) trên một dải phần tử liên tiếp:

| Phép toán | Mô tả |
|-----------|-------------|
| `SUM` | Tổng các value dạng số |
| `MIN` | Value dạng số nhỏ nhất |
| `MAX` | Value dạng số lớn nhất |
| `AND` / `OR` / `XOR` | Phép toán bitwise trên các value số nguyên |
| `MATCH value` | Số phần tử bằng `value` |
| `USED` | Số phần tử không rỗng trong dải |

*Chạy một phép tổng hợp single-pass trên một dải liên tiếp bằng AROP, chẳng hạn SUM, MAX, hoặc đếm MATCH*

**Lệnh (Commands):** ARMSET, AROP

**Độ phức tạp (Complexity):**
- ARMSET: O(N)
- AROP: O(P)

**Có sẵn trong (Available in):** Redis CLI, Go, Java (Asynchronous - Lettuce), Java (Reactive - Lettuce), JavaScript (Node.js), PHP, Python

##### Redis CLI

```
> ARMSET scores 0 "10" 1 "20" 2 "30"
(integer) 3
> AROP scores 0 2 SUM
"60"
> AROP scores 0 2 MAX
"30"
> AROP scores 0 2 MATCH "10"
(integer) 1
```

##### Go

```go
	msetRes, err := rdb.ARMSet(ctx, "scores",
		redis.AREntry{Index: 0, Value: "10"},
		redis.AREntry{Index: 1, Value: "20"},
		redis.AREntry{Index: 2, Value: "30"},
	).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(msetRes) // >>> 3

	sumRes, err := rdb.AROpSum(ctx, "scores", 0, 2).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(sumRes) // >>> 60

	maxRes, err := rdb.AROpMax(ctx, "scores", 0, 2).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(maxRes) // >>> 30

	matchRes, err := rdb.AROpMatch(ctx, "scores", 0, 2, "10").Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(matchRes) // >>> 1
```

##### Java (Asynchronous - Lettuce)

```java
            Map<Long, String> aropScores = new HashMap<>();
            aropScores.put(0L, "10");
            aropScores.put(1L, "20");
            aropScores.put(2L, "30");

            CompletableFuture<Void> aropExample = asyncCommands
                    .armset("scores", aropScores)
                    .thenCompose(res1 -> {
                        System.out.println(res1);
                        // >>> 3
                        return asyncCommands.aropAggregate("scores", 0, 2, ArAggregateType.SUM);
                    })
                    .thenCompose(res2 -> {
                        System.out.println(res2);
                        // >>> 60
                        return asyncCommands.aropAggregate("scores", 0, 2, ArAggregateType.MAX);
                    })
                    .thenCompose(res3 -> {
                        System.out.println(res3);
                        // >>> 30
                        return asyncCommands.aropCount("scores", 0, 2, "10");
                    })
                    .thenAccept(res4 -> {
                        System.out.println(res4);
                        // >>> 1
                    })
                    .toCompletableFuture();

            aropExample.join();
```

##### Java (Reactive - Lettuce)

```java
            Map<Long, String> aropParams = new HashMap<>();
            aropParams.put(0L, "10");
            aropParams.put(1L, "20");
            aropParams.put(2L, "30");

            Mono<Long> arop1 = reactiveCommands.armset("scores", aropParams).doOnNext(result -> {
                System.out.println(result); // >>> 3
            });

            arop1.block();

            Mono<String> arop2 = reactiveCommands.aropAggregate("scores", 0, 2, ArAggregateType.SUM)
                    .doOnNext(result -> {
                        System.out.println(result); // >>> 60
                    });

            arop2.block();

            Mono<String> arop3 = reactiveCommands.aropAggregate("scores", 0, 2, ArAggregateType.MAX)
                    .doOnNext(result -> {
                        System.out.println(result); // >>> 30
                    });

            arop3.block();

            Mono<Long> arop4 = reactiveCommands.aropCount("scores", 0, 2, "10").doOnNext(result -> {
                System.out.println(result); // >>> 1
            });

            arop4.block();
```

##### JavaScript (Node.js)

```javascript
const opSetResult = await client.arMSet('scores', { 0: '10', 1: '20', 2: '30' });
console.log(opSetResult); // >>> 3

const sumResult = await client.arOp('scores', 0, 2, 'SUM');
console.log(sumResult); // >>> 60

const maxResult = await client.arOp('scores', 0, 2, 'MAX');
console.log(maxResult); // >>> 30

const matchResult = await client.arOp('scores', 0, 2, 'MATCH', '10');
console.log(matchResult); // >>> 1
```

##### PHP

```php
        $res1 = $redis->armset('scores', [0 => '10', 1 => '20', 2 => '30']);
        echo $res1 . PHP_EOL; // >>> 3

        $res2 = $redis->arop('scores', 0, 2, 'SUM');
        echo $res2 . PHP_EOL; // >>> 60

        $res3 = $redis->arop('scores', 0, 2, 'MAX');
        echo $res3 . PHP_EOL; // >>> 30

        $res4 = $redis->arop('scores', 0, 2, 'MATCH', '10');
        echo $res4 . PHP_EOL; // >>> 1
```

##### Python

```python
res26 = r.armset("scores", {0: "10", 1: "20", 2: "30"})
print(res26)
# >>> 3

res27 = r.arop("scores", 0, 2, ArrayAggregateOperations.SUM)
print(res27)
# >>> 60

res28 = r.arop("scores", 0, 2, ArrayAggregateOperations.MAX)
print(res28)
# >>> 30

res29 = r.arop("scores", 0, 2, ArrayAggregateOperations.MATCH, value="10")
print(res29)
# >>> 1
```



## Tìm kiếm phần tử (Searching elements)

[`ARGREP`](https://redis.io/docs/latest/commands/argrep) tìm các phần tử trong một dải có value khớp với một hoặc nhiều điều kiện văn bản (textual predicate) và trả về chỉ số của chúng. Các slot rỗng được bỏ qua. Có bốn dạng điều kiện được hỗ trợ: `EXACT` (bằng hoàn toàn), `MATCH` (chuỗi con), `GLOB` (cùng cú pháp wildcard như `MATCH` của [`SCAN`](https://redis.io/docs/latest/commands/scan)), và `RE` (biểu thức chính quy - regular expression). Nhiều điều kiện được kết hợp bằng `OR` theo mặc định, hoặc bằng `AND` khi tùy chọn đó được truyền vào. Truyền `NOCASE` để so sánh không phân biệt hoa thường, `WITHVALUES` để trả về các value khớp cùng với chỉ số của chúng, và `LIMIT` để giới hạn số kết quả khớp.

Điều này đặc biệt hữu ích khi một array lưu văn bản được đánh chỉ số theo dòng, chẳng hạn một file log, trong đó mỗi phần tử chứa một dòng:

*Tìm các phần tử khớp với điều kiện văn bản (EXACT, MATCH, GLOB, RE) bằng ARGREP, kết hợp bằng AND hoặc OR*

**Lệnh (Commands):** DEL, ARMSET, ARGREP

**Độ phức tạp (Complexity):**
- DEL: O(N)
- ARMSET: O(N)
- ARGREP: O(P * C)

**Có sẵn trong (Available in):** Redis CLI, Go, Java (Asynchronous - Lettuce), Java (Reactive - Lettuce), JavaScript (Node.js), PHP, Python

##### Redis CLI

```
> DEL log
(integer) 1
> ARMSET log 0 "boot: ok" 1 "warn: disk" 2 "ERROR: cpu" 3 "info: ready" 4 "error: net"
(integer) 5
> ARGREP log - + MATCH "error" NOCASE
1) (integer) 2
2) (integer) 4
> ARGREP log 0 4 GLOB "warn:*" OR GLOB "error:*" WITHVALUES
1) 1) (integer) 1
   2) "warn: disk"
2) 1) (integer) 4
   2) "error: net"
```

##### Go

```go
	msetRes, err := rdb.ARMSet(ctx, "log",
		redis.AREntry{Index: 0, Value: "boot: ok"},
		redis.AREntry{Index: 1, Value: "warn: disk"},
		redis.AREntry{Index: 2, Value: "ERROR: cpu"},
		redis.AREntry{Index: 3, Value: "info: ready"},
		redis.AREntry{Index: 4, Value: "error: net"},
	).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(msetRes) // >>> 5

	// Case-insensitive match for "error".
	grepRes, err := rdb.ARGrep(ctx, "log", "0", "4", &redis.ARGrepArgs{
		Predicates: []redis.ARGrepPredicate{
			{Type: redis.ARGrepMatch, Value: "error"},
		},
		NoCase: true,
	}).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(grepRes) // >>> [2 4]

	// Two GLOB predicates combined with the default OR, returning values too.
	grepValsRes, err := rdb.ARGrepWithValues(ctx, "log", "0", "4", &redis.ARGrepArgs{
		Predicates: []redis.ARGrepPredicate{
			{Type: redis.ARGrepGlob, Value: "warn:*"},
			{Type: redis.ARGrepGlob, Value: "error:*"},
		},
	}).Result()

	if err != nil {
		panic(err)
	}

	for _, entry := range grepValsRes {
		fmt.Printf("%d -> %s\n", entry.Index, entry.Value)
	}
	// >>> 1 -> warn: disk
	// >>> 4 -> error: net
```

##### Java (Asynchronous - Lettuce)

```java
            Map<Long, String> argrepLog = new HashMap<>();
            argrepLog.put(0L, "boot: ok");
            argrepLog.put(1L, "warn: disk");
            argrepLog.put(2L, "ERROR: cpu");
            argrepLog.put(3L, "info: ready");
            argrepLog.put(4L, "error: net");

            CompletableFuture<Void> argrepExample = asyncCommands
                    .armset("log", argrepLog)
                    .thenCompose(res1 -> {
                        System.out.println(res1);
                        // >>> 5
                        return asyncCommands.argrep("log",
                                ArGrepArgs.range(0, 4).match("error").nocase());
                    })
                    .thenCompose(res2 -> {
                        System.out.println(res2);
                        // >>> [2, 4]
                        return asyncCommands.argrepWithValues("log",
                                ArGrepArgs.range(0, 4).glob("warn:*").glob("error:*"));
                    })
                    .thenAccept(res3 -> {
                        for (IndexedValue<String> pair : res3) {
                            System.out.println(pair.getIndex() + " -> " + pair.getValue());
                        }
                        // >>> 1 -> warn: disk
                        // >>> 4 -> error: net
                    })
                    .toCompletableFuture();

            argrepExample.join();
```

##### Java (Reactive - Lettuce)

```java
            Map<Long, String> argrepParams = new HashMap<>();
            argrepParams.put(0L, "boot: ok");
            argrepParams.put(1L, "warn: disk");
            argrepParams.put(2L, "ERROR: cpu");
            argrepParams.put(3L, "info: ready");
            argrepParams.put(4L, "error: net");

            Mono<Long> argrep1 = reactiveCommands.armset("log", argrepParams).doOnNext(result -> {
                System.out.println(result); // >>> 5
            });

            argrep1.block();

            Mono<List<Long>> argrep2 = reactiveCommands.argrep("log", ArGrepArgs.range(0, 4).match("error").nocase())
                    .collectList()
                    .doOnNext(result -> {
                        System.out.println(result); // >>> [2, 4]
                    });

            argrep2.block();

            Mono<List<IndexedValue<String>>> argrep3 = reactiveCommands
                    .argrepWithValues("log", ArGrepArgs.range(0, 4).glob("warn:*").glob("error:*"))
                    .doOnNext(entry -> {
                        System.out.println(entry.getIndex() + " -> " + entry.getValue());
                        // >>> 1 -> warn: disk
                        // >>> 4 -> error: net
                    })
                    .collectList()
            ;

            argrep3.block();
```

##### JavaScript (Node.js)

```javascript
const grepSetResult = await client.arMSet('log', {
  0: 'boot: ok',
  1: 'warn: disk',
  2: 'ERROR: cpu',
  3: 'info: ready',
  4: 'error: net'
});
console.log(grepSetResult); // >>> 5

const grepResult = await client.arGrep(
  'log',
  0,
  4,
  [['MATCH', 'error']],
  { NOCASE: true }
);
console.log(grepResult); // >>> [ 2, 4 ]

const grepWithValues = await client.arGrepWithValues(
  'log',
  0,
  4,
  [['GLOB', 'warn:*'], ['GLOB', 'error:*']],
  { COMBINATOR: 'OR' }
);
for (const { index, value } of grepWithValues) {
  console.log(`${index} -> ${value}`);
}
// >>> 1 -> warn: disk
// >>> 4 -> error: net
```

##### PHP

```php
        $res1 = $redis->armset('log', [
            0 => 'boot: ok',
            1 => 'warn: disk',
            2 => 'ERROR: cpu',
            3 => 'info: ready',
            4 => 'error: net',
        ]);
        echo $res1 . PHP_EOL; // >>> 5

        // Predicates are [type, value] pairs. Positional argument order is:
        // (key, start, end, predicates, combinator, limit, withValues, noCase)
        $res2 = $redis->argrep('log', 0, 4, [['MATCH', 'error']], null, null, false, true);
        echo json_encode($res2) . PHP_EOL; // >>> [2,4]

        $res3 = $redis->argrep(
            'log',
            0,
            4,
            [['GLOB', 'warn:*'], ['GLOB', 'error:*']],
            'OR',
            null,
            true
        );
        foreach ($res3 as $pair) {
            echo $pair[0] . ' -> ' . $pair[1] . PHP_EOL;
        }
        // >>> 1 -> warn: disk
        // >>> 4 -> error: net
```

##### Python

```python
res30 = r.armset(
    "log",
    {
        0: "boot: ok",
        1: "warn: disk",
        2: "ERROR: cpu",
        3: "info: ready",
        4: "error: net",
    },
)
print(res30)
# >>> 5

res31 = r.argrep(
    "log",
    0,
    4,
    [(ArrayPredicateType.MATCH, "error")],
    nocase=True,
)
print(res31)
# >>> [2, 4]

res32 = r.argrep(
    "log",
    0,
    4,
    [
        (ArrayPredicateType.GLOB, "warn:*"),
        (ArrayPredicateType.GLOB, "error:*"),
    ],
    combinator=ArrayPredicateCombinator.OR,
    withvalues=True,
)
print(res32)
# >>> [[1, 'warn: disk'], [4, 'error: net']]
```



Các giá trị đặc biệt `-` và `+` biểu thị chỉ số đầu tiên và cuối cùng của array. Kết hợp với [chế độ ring buffer](#ring-buffer-mode), điều này cho phép một array kích thước cố định chứa *N* dòng log gần đây nhất và được tìm kiếm tại chỗ.

## Xóa phần tử (Deleting elements)

[`ARDEL`](https://redis.io/docs/latest/commands/ardel) xóa một hoặc nhiều phần tử theo chỉ số và trả về số phần tử thực sự bị xóa. [`ARDELRANGE`](https://redis.io/docs/latest/commands/ardelrange) xóa tất cả các phần tử trong một dải chỉ số; có hỗ trợ đảo ngược `start` và `end`:

*Xóa phần tử theo chỉ số bằng ARDEL hoặc xóa cả một dải chỉ số bằng ARDELRANGE*

**Dựa trên (Builds upon):** arop

**Lệnh (Commands):** ARDEL, ARDELRANGE

**Độ phức tạp (Complexity):**
- ARDEL: O(N)

**Có sẵn trong (Available in):** Redis CLI, Go, Java (Asynchronous - Lettuce), Java (Reactive - Lettuce), JavaScript (Node.js), PHP, Python

##### Redis CLI

```
> ARDEL scores 1
(integer) 1
> ARDELRANGE scores 0 2
(integer) 2
```

##### Go

```go
	msetRes, err := rdb.ARMSet(ctx, "scores",
		redis.AREntry{Index: 0, Value: "10"},
		redis.AREntry{Index: 1, Value: "20"},
		redis.AREntry{Index: 2, Value: "30"},
	).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(msetRes) // >>> 3

	delRes, err := rdb.ARDel(ctx, "scores", 1).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(delRes) // >>> 1

	delRangeRes, err := rdb.ARDelRange(ctx, "scores", redis.ARRange{Start: 0, End: 2}).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(delRangeRes) // >>> 2
```

##### Java (Asynchronous - Lettuce)

```java
            Map<Long, String> ardelScores = new HashMap<>();
            ardelScores.put(0L, "10");
            ardelScores.put(1L, "20");
            ardelScores.put(2L, "30");

            CompletableFuture<Void> ardelExample = asyncCommands
                    .armset("scores", ardelScores)
                    .thenCompose(res1 -> {
                        System.out.println(res1);
                        // >>> 3
                        return asyncCommands.ardel("scores", 1);
                    })
                    .thenCompose(res2 -> {
                        System.out.println(res2);
                        // >>> 1
                        return asyncCommands.ardelrange("scores", 0, 2);
                    })
                    .thenAccept(res3 -> {
                        System.out.println(res3);
                        // >>> 2
                    })
                    .toCompletableFuture();

            ardelExample.join();
```

##### Java (Reactive - Lettuce)

```java
            Map<Long, String> ardelParams = new HashMap<>();
            ardelParams.put(0L, "10");
            ardelParams.put(1L, "20");
            ardelParams.put(2L, "30");

            Mono<Long> ardel1 = reactiveCommands.armset("scores", ardelParams).doOnNext(result -> {
                System.out.println(result); // >>> 3
            });

            ardel1.block();

            Mono<Long> ardel2 = reactiveCommands.ardel("scores", 1).doOnNext(result -> {
                System.out.println(result); // >>> 1
            });

            ardel2.block();

            Mono<Long> ardel3 = reactiveCommands.ardelrange("scores", 0, 2).doOnNext(result -> {
                System.out.println(result); // >>> 2
            });

            ardel3.block();
```

##### JavaScript (Node.js)

```javascript
const delSetResult = await client.arMSet('scores', { 0: '10', 1: '20', 2: '30' });
console.log(delSetResult); // >>> 3

const delResult = await client.arDel('scores', 1);
console.log(delResult); // >>> 1

const delRangeResult = await client.arDelRange('scores', [[0, 2]]);
console.log(delRangeResult); // >>> 2
```

##### PHP

```php
        $res1 = $redis->armset('scores', [0 => '10', 1 => '20', 2 => '30']);
        echo $res1 . PHP_EOL; // >>> 3

        $res2 = $redis->ardel('scores', 1);
        echo $res2 . PHP_EOL; // >>> 1

        $res3 = $redis->ardelrange('scores', 0, 2);
        echo $res3 . PHP_EOL; // >>> 2
```

##### Python

```python
res33 = r.armset("scores", {0: "10", 1: "20", 2: "30"})
print(res33)
# >>> 3

res34 = r.ardel("scores", 1)
print(res34)
# >>> 1

res35 = r.ardelrange("scores", (0, 2))
print(res35)
# >>> 2
```



Xóa phần tử cuối cùng còn lại sẽ xóa luôn toàn bộ key.

## Xem thông tin nội bộ (Introspection)

[`ARINFO`](https://redis.io/docs/latest/commands/arinfo) trả về metadata về cấu trúc nội bộ của một array, bao gồm độ dài logic, số lượng phần tử, và chỉ số chèn tiếp theo. Truyền tùy chọn `FULL` để bao gồm cả thống kê theo từng slice, chẳng hạn tỷ lệ lấp đầy (fill rate) và số lượng slice dày (dense) so với slice thưa (sparse):

```
> ARINFO readings
 1) "len"
 2) (integer) 3
 3) "count"
 4) (integer) 3
 5) "next-insert-index"
 6) (integer) 0
...
```

## Cấu hình (Configuration)

Các tham số cấu hình sau ảnh hưởng tới hành vi của array:
- `array-slice-size`
- `array-sparse-kmax`
- `array-sparse-kmin`

Xem [trang cấu hình Redis](https://redis.io/docs/latest/operate/oss_and_stack/management/config) để biết chi tiết.

## Hiệu năng (Performance)

Hầu hết các lệnh array là O(1), bao gồm [`ARSET`](https://redis.io/docs/latest/commands/arset), [`ARGET`](https://redis.io/docs/latest/commands/arget), [`ARDEL`](https://redis.io/docs/latest/commands/ardel), [`ARINSERT`](https://redis.io/docs/latest/commands/arinsert), [`ARNEXT`](https://redis.io/docs/latest/commands/arnext), [`ARSEEK`](https://redis.io/docs/latest/commands/arseek), [`ARCOUNT`](https://redis.io/docs/latest/commands/arcount), và [`ARLEN`](https://redis.io/docs/latest/commands/arlen). Các thao tác chạm tới N phần tử—chẳng hạn [`ARGETRANGE`](https://redis.io/docs/latest/commands/argetrange), [`ARSCAN`](https://redis.io/docs/latest/commands/arscan), [`ARDELRANGE`](https://redis.io/docs/latest/commands/ardelrange), [`AROP`](https://redis.io/docs/latest/commands/arop), và [`ARLASTITEMS`](https://redis.io/docs/latest/commands/arlastitems)—là O(N). Cách mã hóa sliced-array bên dưới xử lý hiệu quả cả kiểu truy cập dày lẫn thưa, nên các khoảng trống chỉ số lớn tiêu tốn rất ít bộ nhớ.

## Các lựa chọn thay thế (Alternatives)

Array bổ sung cho, chứ không thay thế, các kiểu collection khác của Redis:

- Dùng [Redis lists](https://redis.io/docs/latest/develop/data-types/lists) khi bạn cần các thao tác push/pop ở cả hai đầu, hoặc khi bạn cần chèn phần tử vào giữa các phần tử đã có.
- Dùng [Redis hashes](https://redis.io/docs/latest/develop/data-types/hashes) khi các value được truy cập theo tên field thay vì theo chỉ số dạng số.
- Dùng [Redis streams](https://redis.io/docs/latest/develop/data-types/streams) khi bạn cần một log sự kiện append-only với consumer group và cơ chế xác nhận (acknowledgement).

## Giới hạn (Limits)

[`ARGETRANGE`](https://redis.io/docs/latest/commands/argetrange) áp đặt giới hạn cứng 1,000,000 phần tử cho mỗi lần gọi để phòng tránh việc vô tình đọc một dải quá lớn.

Array không được hỗ trợ trong [Active-Active databases](https://redis.io/docs/latest/operate/rs/databases/active-active).

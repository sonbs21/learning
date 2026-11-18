# Path

> Nguồn: <https://redis.io/docs/latest/develop/data-types/json/path/>
> Truy cập các phần tử cụ thể bên trong một JSON document

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


Path cho phép bạn truy cập các phần tử cụ thể bên trong một JSON document. Vì không có tiêu chuẩn nào cho cú pháp JSON path, Redis JSON tự hiện thực cú pháp riêng. Cú pháp của JSON dựa trên các best practice phổ biến và cố ý giống với [JSONPath](http://goessner.net/articles/JsonPath/).

JSON hỗ trợ hai cú pháp truy vấn: [cú pháp JSONPath](#jsonpath-syntax) và [cú pháp legacy path](#legacy-path-syntax) từ phiên bản đầu tiên của JSON.

JSON biết nên dùng cú pháp nào dựa vào ký tự đầu tiên của truy vấn path. Nếu truy vấn bắt đầu bằng ký tự `$`, nó dùng cú pháp JSONPath. Ngược lại, nó mặc định dùng cú pháp legacy path.

Giá trị trả về là một chuỗi JSON với một mảng cấp cao nhất (top-level array) chứa các chuỗi JSON đã serialize.
Và nếu dùng nhiều path (multi-path), giá trị trả về là một chuỗi JSON với một object cấp cao nhất có các value là mảng các giá trị JSON đã serialize.

## Hỗ trợ JSONPath (JSONPath support)

RedisJSON v2.0 giới thiệu hỗ trợ [JSONPath](http://goessner.net/articles/JsonPath/). Nó tuân theo cú pháp được Goessner mô tả trong [bài viết](http://goessner.net/articles/JsonPath/) của ông.

Một truy vấn JSONPath có thể trỏ tới nhiều vị trí trong một JSON document. Trong trường hợp này, các lệnh JSON áp dụng thao tác lên mọi vị trí có thể. Đây là một cải tiến lớn so với các truy vấn [legacy path](#legacy-path-syntax), vốn chỉ thao tác trên path đầu tiên.

Lưu ý rằng cấu trúc phản hồi của lệnh thường khác đi khi dùng JSONPath. Xem trang [Commands](https://redis.io/commands/?group=json) để biết thêm chi tiết.

Cú pháp mới hỗ trợ ký pháp ngoặc vuông (bracket notation), cho phép dùng các ký tự đặc biệt như dấu hai chấm ":" hoặc khoảng trắng trong tên key.

Nếu bạn muốn đưa dấu ngoặc kép vào một truy vấn từ CLI, hãy bao JSONPath trong dấu nháy đơn. Ví dụ:

```bash
JSON.GET store '$.inventory["mountain_bikes"]'
```


<a name="jsonpath-syntax"></a>

## Cú pháp JSONPath (JSONPath syntax)

Bảng cú pháp JSONPath sau được điều chỉnh từ [bảng so sánh cú pháp path](https://goessner.net/articles/JsonPath/index.html#e2) của Goessner.

| Phần tử cú pháp | Mô tả |
|----------------|-------------|
| $ | Gốc (root) (phần tử JSON ngoài cùng), bắt đầu path. |
| . or [] | Chọn một phần tử con. |
| .. | Đi xuống đệ quy (recursive descent) qua JSON document. |
| * | Wildcard, trả về tất cả các phần tử. |
| [] | Toán tử chỉ số (subscript operator), truy cập một phần tử của mảng. |
| [,] | Hợp (union), chọn nhiều phần tử. |
| [start\:end\:step] | Cắt mảng (array slice), trong đó *start*, *end*, và *step* là các giá trị chỉ số. Bạn có thể bỏ qua các giá trị trong slice (ví dụ, `[3:]`, `[:8:2]`) để dùng giá trị mặc định: *start* mặc định là chỉ số đầu tiên, *end* mặc định là chỉ số cuối cùng, và *step* mặc định là `1`. Dùng `[*]` hoặc `[:]` để chọn tất cả các phần tử. |
| ?() | Lọc (filter) một JSON object hoặc mảng. Hỗ trợ các toán tử so sánh <nobr>(`==`, `!=`, `<`, `<=`, `>`, `>=`, `=~`)</nobr>, toán tử logic <nobr>(`&&`, `\|\|`, `!`)</nobr>, toán tử số học <nobr>(`+`, `-`, `*`, `/`, `%`)</nobr>, toán tử thành viên (membership) <nobr>(`in`, `nin`)</nobr>, toán tử quan hệ tập hợp (set-relation) <nobr>(`subsetof`, `anyof`, `noneof`)</nobr>, các toán tử <nobr>(`size`/`sizeof`, `empty`)</nobr>, và dấu ngoặc đơn <nobr>(`(`, `)`)</nobr>. |
| () | Biểu thức script (script expression). |
| @ | Phần tử hiện tại, dùng trong biểu thức filter hoặc script. |
| ~ | Trả về tên các member của một object dưới dạng danh sách string. |

Bắt đầu từ Redis 8.10, kiểu dữ liệu JSON hỗ trợ cú pháp JSONPath phong phú hơn, với các toán tử và hàm bổ sung:

- [Biểu thức projection (projection expressions)](#projection-expressions) ở cấp cao nhất của một truy vấn JSONPath
- [`==` và `!=`](#comparing-array-and-object-literals) giờ đây có thể so sánh bất kỳ literal nào, kể cả literal mảng và object
- Toán tử phủ định filter: [`!`](#negation-)
- [`size`/`sizeof` và `empty`](#size-sizeof-and-empty) toán tử trên string, mảng, object, và nodelist
- [`in` và `nin`](#membership-in-and-nin) toán tử: kiểm tra thành viên trên mảng và nodelist
- [Toán tử trên số](#arithmetic-operators): toán tử hai ngôi `-`, `+`, `*`, `/`, `%`, và toán tử một ngôi `-` và `+`
- Toán tử trên object: [`~`](#get-keys-operator-)
- [`length()`](#length) hàm trên mảng, object, và string
- Hàm trên số: [`abs()`, `ceiling()`, `floor()`](#abs-ceiling-and-floor)
- Hàm trên string: [`match()`, `search()`](#match-and-search)
- Nối string với [`concat()`](#concat)
- Hàm trên mảng: [`first()`, `last()`, `index()`](#first-last-and-index), [`append()`](#append)
- Hàm tổng hợp (aggregation) trên mảng: [`min()`, `max()`, `avg()`, `sum()`, `stddev()`](#min-max-avg-sum-and-stddev)
- Hàm trên object: [`keys()`](#keys)
- Hàm trên nodelist: [`count()`](#count)
- Hàm trên nodelist có đúng một node: [`value()`](#value)
- Hàm quan hệ trên mảng và nodelist: [`subsetof()`, `anyof()`, `noneof()`](#set-relations-subsetof-anyof-and-noneof)

Các toán tử này có thể được dùng bên trong một biểu thức filter (`?()`). Hàm có thể xuất hiện bên trong biểu thức filter và, khi chúng trả về một giá trị, như các [biểu thức projection](#projection-expressions) ở cấp cao nhất. Một hàm có thể được viết ở dạng tiền tố (prefix), `length($.arr)`, hoặc dạng hậu tố (postfix, kiểu method), `$.arr.length()`. Một đoạn path theo ngay sau bởi `(` là một lời gọi method, nên `$.arr.length()` là một lời gọi hàm trong khi `$.arr.length` là tham chiếu tới một field tên là `length`.

> [!WARNING]
> Bắt đầu từ Redis 8.10, hai thay đổi trong việc phân tích path có thể ảnh hưởng tới các truy vấn hiện có:
>
> - Để truy cập một field có tên chứa dấu ngã (`~`), hãy dùng ký pháp ngoặc vuông, ví dụ `$["a~b"]`. Dấu ngã không còn hợp lệ trong tên field khi dùng ký pháp dấu chấm (dot notation).
> - Các từ `in`, `nin`, `subsetof`, `anyof`, `noneof`, `size`, `sizeof`, và `empty` giờ được dành riêng làm toán tử. Để truy cập một field có một trong các tên này, hãy dùng `$.size` hoặc `$["size"]`.

<a name="evaluation-semantics"></a>

## Ngữ nghĩa đánh giá (Evaluation semantics)

Các quy tắc sau áp dụng cho các toán tử và hàm được mô tả bên dưới:

- **Kết quả "Nothing".** Bất kỳ toán hạng nào không thể đánh giá được — một toán hạng số học không phải số, phép chia hoặc chia lấy dư cho 0, một chỉ số nằm ngoài phạm vi, sai số lượng đối số, không khớp kiểu, hoặc tràn số học thành một giá trị không hữu hạn — đều được đánh giá thành *Nothing*. Bên trong một filter, kết quả *Nothing* làm điều kiện bao quanh trở thành false, nên phần tử đó bị bỏ qua. Khi là projection, nó tạo ra một phản hồi rỗng (`[]`). Việc đánh giá không bao giờ trả về lỗi và không bao giờ làm truy vấn thất bại trong các trường hợp này.
- **Ép kiểu số (number coercion).** Các toán tử `==`, `!=`, `in`, `nin`, và các toán tử quan hệ tập hợp coi số nguyên và số thực dấu phẩy động có cùng giá trị là bằng nhau (ví dụ, `1` bằng `1.0`). Các toán tử thứ tự (`<`, `<=`, `>`, `>=`) không thể so sánh với một literal mảng hoặc object, nên phép so sánh như vậy không bao giờ khớp.
- **Node list khớp theo kiểu "any".** Khi một toán hạng chọn nhiều hơn một node (ví dụ, `@.*`), một phép so sánh hoặc toán tử sẽ đúng nếu *bất kỳ* node nào được chọn thỏa mãn nó.
- **Toán tử số học phải được bao quanh bởi khoảng trắng.** `@.a + 1` là phép cộng, nhưng `@.a+1` là tham chiếu tới một field có tên đúng là `a+1`, vì tập ký tự của tên field bao gồm các ký tự như `+`, `-`, `/`, `%`, `$`, `^`, `:`, và `_`.
- **Hàm được kiểm tra số đối số (arity).** Gọi một hàm với sai số lượng đối số sẽ tạo ra *Nothing* thay vì âm thầm chỉ dùng một phần các đối số.

## Ví dụ JSONPath (JSONPath examples)

Các ví dụ sau dùng JSON document này, lưu thông tin chi tiết về các mặt hàng trong kho của một cửa hàng:

```json
{
    "inventory": {
        "mountain_bikes": [
            {
                "id": "bike:1",
                "model": "Phoebe",
                "description": "This is a mid-travel trail slayer that is a fantastic daily driver or one bike quiver. The Shimano Claris 8-speed groupset gives plenty of gear range to tackle hills and there\u2019s room for mudguards and a rack too.  This is the bike for the rider who wants trail manners with low fuss ownership.",
                "price": 1920,
                "specs": {"material": "carbon", "weight": 13.1},
                "colors": ["black", "silver"],
            },
... output truncated for AI-facing Markdown ...
                "description": "A real joy to ride, this bike got very high scores in last years Bike of the year report. The carefully crafted 50-34 tooth chainset and 11-32 tooth cassette give an easy-on-the-legs bottom gear for climbing, and the high-quality Vittoria Zaffiro tires give balance and grip.It includes a low-step frame , our memory foam seat, bump-resistant shocks and conveniently placed thumb throttle. Put it all together and you get a bike that helps redefine what can be done for this price.",
                "price": 3941,
                "specs": {"material": "alloy", "weight": 11.6},
            },
        ],
    }
}
```

Trước tiên, tạo JSON document trong database của bạn:

*Chuẩn bị: Tạo một JSON document phức tạp với các object và mảng lồng nhau để dùng trong các ví dụ JSONPath*

**Lệnh (Commands):** JSON.SET

**Độ phức tạp (Complexity):**
- JSON.SET: O(M+N)

**Có sẵn trong (Available in):** Redis CLI, C#, Go, Java (Asynchronous - Lettuce), Java (Reactive - Lettuce), Java (Synchronous - Jedis), JavaScript (Node.js), PHP, Python, Ruby, Rust (Asynchronous), Rust (Synchronous)

##### Redis CLI

```
> JSON.SET bikes:inventory $ '{ "inventory": { "mountain_bikes": [ { "id": "bike:1", "model": "Phoebe", "description": "This is a mid-travel trail slayer that is a fantastic daily driver or one bike quiver. The Shimano Claris 8-speed groupset gives plenty of gear range to tackle hills and there\'s room for mudguards and a rack too. This is the bike for the rider who wants trail manners with low fuss ownership.", "price": 1920, "specs": {"material": "carbon", "weight": 13.1}, "colors": ["black", "silver"] }, { "id": "bike:2", "model": "Quaoar", "description": "Redesigned for the 2020 model year, this bike impressed our testers and is the best all-around trail bike we\'ve ever tested. The Shimano gear system effectively does away with an external cassette, so is super low maintenance in terms of wear and tear. All in all it\'s an impressive package for the price, making it very competitive.", "price": 2072, "specs": {"material": "aluminium", "weight": 7.9}, "colors": ["black", "white"] }, { "id": "bike:3", "model": "Weywot", "description": "This bike gives kids aged six years and older a durable and uberlight mountain bike for their first experience on tracks and easy cruising through forests and fields. A set of powerful Shimano hydraulic disc brakes provide ample stopping ability. If you\'re after a budget option, this is one of the best bikes you could get.", "price": 3264, "specs": {"material": "alloy", "weight": 13.8} } ], "commuter_bikes": [ { "id": "bike:4", "model": "Salacia", "description": "This bike is a great option for anyone who just wants a bike to get about on With a slick-shifting Claris gears from Shimano\'s, this is a bike which doesn\'t break the bank and delivers craved performance. It\'s for the rider who wants both efficiency and capability.", "price": 1475, "specs": {"material": "aluminium", "weight": 16.6}, "colors": ["black", "silver"] }, { "id": "bike:5", "model": "Mimas", "description": "A real joy to ride, this bike got very high scores in last years Bike of the year report. The carefully crafted 50-34 tooth chainset and 11-32 tooth cassette give an easy-on-the-legs bottom gear for climbing, and the high-quality Vittoria Zaffiro tires give balance and grip.It includes a low-step frame , our memory foam seat, bump-resistant shocks and conveniently placed thumb throttle. Put it all together and you get a bike that helps redefine what can be done for this price.", "price": 3941, "specs": {"material": "alloy", "weight": 11.6} } ] }}'
... output truncated for AI-facing Markdown ...
> JSON.SET bikes:inventory $ '{ "inventory": { "mountain_bikes": [ { "id": "bike:1", "model": "Phoebe", "description": "This is a mid-travel trail slayer that is a fantastic daily driver or one bike quiver. The Shimano Claris 8-speed groupset gives plenty of gear range to tackle hills and there\'s room for mudguards and a rack too. This is the bike for the rider who wants trail manners with low fuss ownership.", "price": 1920, "specs": {"material": "carbon", "weight": 13.1}, "colors": ["black", "silver"] }, { "id": "bike:2", "model": "Quaoar", "description": "Redesigned for the 2020 model year, this bike impressed our testers and is the best all-around trail bike we\'ve ever tested. The Shimano gear system effectively does away with an external cassette, so is super low maintenance in terms of wear and tear. All in all it\'s an impressive package for the price, making it very competitive.", "price": 2072, "specs": {"material": "aluminium", "weight": 7.9}, "colors": ["black", "white"] }, { "id": "bike:3", "model": "Weywot", "description": "This bike gives kids aged six years and older a durable and uberlight mountain bike for their first experience on tracks and easy cruising through forests and fields. A set of powerful Shimano hydraulic disc brakes provide ample stopping ability. If you\'re after a budget option, this is one of the best bikes you could get.", "price": 3264, "specs": {"material": "alloy", "weight": 13.8} } ], "commuter_bikes": [ { "id": "bike:4", "model": "Salacia", "description": "This bike is a great option for anyone who just wants a bike to get about on With a slick-shifting Claris gears from Shimano\'s, this is a bike which doesn\'t break the bank and delivers craved performance. It\'s for the rider who wants both efficiency and capability.", "price": 1475, "specs": {"material": "aluminium", "weight": 16.6}, "colors": ["black", "silver"] }, { "id": "bike:5", "model": "Mimas", "description": "A real joy to ride, this bike got very high scores in last years Bike of the year report. The carefully crafted 50-34 tooth chainset and 11-32 tooth cassette give an easy-on-the-legs bottom gear for climbing, and the high-quality Vittoria Zaffiro tires give balance and grip.It includes a low-step frame , our memory foam seat, bump-resistant shocks and conveniently placed thumb throttle. Put it all together and you get a bike that helps redefine what can be done for this price.", "price": 3941, "specs": {"material": "alloy", "weight": 11.6} } ] }}'
```

##### C#

```csharp
        string inventoryJson = @"
{
    ""inventory"": {
        ""mountain_bikes"": [
            {
                ""id"": ""bike:1"",
                ""model"": ""Phoebe"",
                ""description"": ""This is a mid-travel trail slayer that is a fantastic daily driver or one bike quiver. The Shimano Claris 8-speed groupset gives plenty of gear range to tackle hills and there\u2019s room for mudguards and a rack too.  This is the bike for the rider who wants trail manners with low fuss ownership."",
                ""price"": 1920,
                ""specs"": {""material"": ""carbon"", ""weight"": 13.1},
                ""colors"": [""black"", ""silver""]
            },
            {
                ""id"": ""bike:2"",
                ""model"": ""Quaoar"",
                ""description"": ""Redesigned for the 2020 model year, this bike impressed our testers and is the best all-around trail bike we've ever tested. The Shimano gear system effectively does away with an external cassette, so is super low maintenance in terms of wear and tear. All in all it's an impressive package for the price, making it very competitive."",
                ""price"": 2072,
                ""specs"": {""material"": ""aluminium"", ""weight"": 7.9},
                ""colors"": [""black"", ""white""]
            },
            {
                ""id"": ""bike:3"",
                ""model"": ""Weywot"",
                ""description"": ""This bike gives kids aged six years and older a durable and uberlight mountain bike for their first experience on tracks and easy cruising through forests and fields. A set of powerful Shimano hydraulic disc brakes provide ample stopping ability. If you're after a budget option, this is one of the best bikes you could get."",
                ""price"": 3264,
                ""specs"": {""material"": ""alloy"", ""weight"": 13.8}
            }
        ],
        ""commuter_bikes"": [
            {
                ""id"": ""bike:4"",
                ""model"": ""Salacia"",
                ""description"": ""This bike is a great option for anyone who just wants a bike to get about on With a slick-shifting Claris gears from Shimano\u2019s, this is a bike which doesn\u2019t break the bank and delivers craved performance.  It\u2019s for the rider who wants both efficiency and capability."",
                ""price"": 1475,
                ""specs"": {""material"": ""aluminium"", ""weight"": 16.6},
                ""colors"": [""black"", ""silver""]
            },
            {
                ""id"": ""bike:5"",
                ""model"": ""Mimas"",
                ""description"": ""A real joy to ride, this bike got very high scores in last years Bike of the year report. The carefully crafted 50-34 tooth chainset and 11-32 tooth cassette give an easy-on-the-legs bottom gear for climbing, and the high-quality Vittoria Zaffiro tires give balance and grip.It includes a low-step frame , our memory foam seat, bump-resistant shocks and conveniently placed thumb throttle. Put it all together and you get a bike that helps redefine what can be done for this price."",
                ""price"": 3941,
                ""specs"": {""material"": ""alloy"", ""weight"": 11.6}
            }
        ]
    }
}";

        bool res28 = db.JSON().Set("bikes:inventory", "$", inventoryJson);
        Console.WriteLine(res28);   // >>> True
```

##### Go

```go
	var inventory_json = map[string]interface{}{
		"inventory": map[string]interface{}{
			"mountain_bikes": []interface{}{
				map[string]interface{}{
					"id":    "bike:1",
					"model": "Phoebe",
					"description": "This is a mid-travel trail slayer that is a fantastic " +
						"daily driver or one bike quiver. The Shimano Claris 8-speed groupset " +
						"gives plenty of gear range to tackle hills and there\u2019s room for " +
						"mudguards and a rack too.  This is the bike for the rider who wants " +
						"trail manners with low fuss ownership.",
					"price":  1920,
					"specs":  map[string]interface{}{"material": "carbon", "weight": 13.1},
					"colors": []interface{}{"black", "silver"},
				},
				map[string]interface{}{
					"id":    "bike:2",
					"model": "Quaoar",
					"description": "Redesigned for the 2020 model year, this bike " +
						"impressed our testers and is the best all-around trail bike we've " +
						"ever tested. The Shimano gear system effectively does away with an " +
						"external cassette, so is super low maintenance in terms of wear " +
						"and tear. All in all it's an impressive package for the price, " +
						"making it very competitive.",
					"price":  2072,
					"specs":  map[string]interface{}{"material": "aluminium", "weight": 7.9},
					"colors": []interface{}{"black", "white"},
				},
				map[string]interface{}{
					"id":    "bike:3",
					"model": "Weywot",
					"description": "This bike gives kids aged six years and older " +
						"a durable and uberlight mountain bike for their first experience " +
						"on tracks and easy cruising through forests and fields. A set of " +
						"powerful Shimano hydraulic disc brakes provide ample stopping " +
						"ability. If you're after a budget option, this is one of the best " +
						"bikes you could get.",
					"price": 3264,
					"specs": map[string]interface{}{"material": "alloy", "weight": 13.8},
				},
			},
			"commuter_bikes": []interface{}{
				map[string]interface{}{
					"id":    "bike:4",
					"model": "Salacia",
					"description": "This bike is a great option for anyone who just " +
						"wants a bike to get about on With a slick-shifting Claris gears " +
						"from Shimano\u2019s, this is a bike which doesn\u2019t break the " +
						"bank and delivers craved performance.  It\u2019s for the rider " +
						"who wants both efficiency and capability.",
					"price":  1475,
					"specs":  map[string]interface{}{"material": "aluminium", "weight": 16.6},
					"colors": []interface{}{"black", "silver"},
				},
				map[string]interface{}{
					"id":    "bike:5",
					"model": "Mimas",
					"description": "A real joy to ride, this bike got very high " +
						"scores in last years Bike of the year report. The carefully " +
						"crafted 50-34 tooth chainset and 11-32 tooth cassette give an " +
						"easy-on-the-legs bottom gear for climbing, and the high-quality " +
						"Vittoria Zaffiro tires give balance and grip.It includes " +
						"a low-step frame , our memory foam seat, bump-resistant shocks and " +
						"conveniently placed thumb throttle. Put it all together and you " +
						"get a bike that helps redefine what can be done for this price.",
					"price": 3941,
					"specs": map[string]interface{}{"material": "alloy", "weight": 11.6},
				},
			},
		},
	}

	res1, err := rdb.JSONSet(ctx, "bikes:inventory", "$", inventory_json).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res1) // >>> OK
```

##### Java (Asynchronous - Lettuce)

```java
            String inventory_json_str = "{" + "    \"inventory\": {" + "        \"mountain_bikes\": [" + "            {"
                    + "                \"id\": \"bike:1\"," + "                \"model\": \"Phoebe\","
                    + "                \"description\": \"This is a mid-travel trail slayer that is a "
                    + "fantastic daily driver or one bike quiver. The Shimano Claris 8-speed groupset "
                    + "gives plenty of gear range to tackle hills and there\u2019s room for mudguards "
                    + "and a rack too.  This is the bike for the rider who wants trail manners with " + "low fuss ownership.\","
                    + "                \"price\": 1920,"
                    + "                \"specs\": {\"material\": \"carbon\", \"weight\": 13.1},"
                    + "                \"colors\": [\"black\", \"silver\"]" + "            }," + "            {"
                    + "                \"id\": \"bike:2\"," + "                \"model\": \"Quaoar\","
                    + "                \"description\": \"Redesigned for the 2020 model year, this "
                    + "bike impressed our testers and is the best all-around trail bike we've ever "
                    + "tested. The Shimano gear system effectively does away with an external cassette, "
                    + "so is super low maintenance in terms of wear and tear. All in all it's an "
                    + "impressive package for the price, making it very competitive.\"," + "                \"price\": 2072,"
                    + "                \"specs\": {\"material\": \"aluminium\", \"weight\": 7.9},"
                    + "                \"colors\": [\"black\", \"white\"]" + "            }," + "            {"
                    + "                \"id\": \"bike:3\"," + "                \"model\": \"Weywot\","
                    + "                \"description\": \"This bike gives kids aged six years and older "
                    + "a durable and uberlight mountain bike for their first experience on tracks and easy "
                    + "cruising through forests and fields. A set of powerful Shimano hydraulic disc brakes "
                    + "provide ample stopping ability. If you're after a budget option, this is one of the "
                    + "best bikes you could get.\"," + "                \"price\": 3264,"
                    + "                \"specs\": {\"material\": \"alloy\", \"weight\": 13.8}" + "            }" + "        ],"
                    + "        \"commuter_bikes\": [" + "            {" + "                \"id\": \"bike:4\","
                    + "                \"model\": \"Salacia\","
                    + "                \"description\": \"This bike is a great option for anyone who just "
                    + "wants a bike to get about on With a slick-shifting Claris gears from Shimano\u2019s, "
                    + "this is a bike which doesn\u2019t break the bank and delivers craved performance.  "
                    + "It\u2019s for the rider who wants both efficiency and capability.\","
                    + "                \"price\": 1475,"
                    + "                \"specs\": {\"material\": \"aluminium\", \"weight\": 16.6},"
                    + "                \"colors\": [\"black\", \"silver\"]" + "            }," + "            {"
                    + "                \"id\": \"bike:5\"," + "                \"model\": \"Mimas\","
                    + "                \"description\": \"A real joy to ride, this bike got very high scores "
                    + "in last years Bike of the year report. The carefully crafted 50-34 tooth chainset "
                    + "and 11-32 tooth cassette give an easy-on-the-legs bottom gear for climbing, and the "
                    + "high-quality Vittoria Zaffiro tires give balance and grip.It includes a low-step "
                    + "frame , our memory foam seat, bump-resistant shocks and conveniently placed thumb "
                    + "throttle. Put it all together and you get a bike that helps redefine what can be "
                    + "done for this price.\"," + "                \"price\": 3941,"
                    + "                \"specs\": {\"material\": \"alloy\", \"weight\": 11.6}" + "            }" + "        ]"
                    + "    }" + "}";

            Charset charset = Charset.forName("UTF-8");
            ByteBuffer inventory_json = charset.encode(inventory_json_str);

            CompletableFuture<Void> setBikes = asyncCommands
                    .jsonSet("bikes:inventory", JsonPath.ROOT_PATH, parser.loadJsonValue(inventory_json))
                    .thenAccept(System.out::println) // >>> OK
                    .toCompletableFuture();
```

##### Java (Reactive - Lettuce)

```java
            String inventory_json_str = "{" + "    \"inventory\": {" + "        \"mountain_bikes\": [" + "            {"
                    + "                \"id\": \"bike:1\"," + "                \"model\": \"Phoebe\","
                    + "                \"description\": \"This is a mid-travel trail slayer that is a "
                    + "fantastic daily driver or one bike quiver. The Shimano Claris 8-speed groupset "
                    + "gives plenty of gear range to tackle hills and there\u2019s room for mudguards "
                    + "and a rack too.  This is the bike for the rider who wants trail manners with " + "low fuss ownership.\","
                    + "                \"price\": 1920,"
                    + "                \"specs\": {\"material\": \"carbon\", \"weight\": 13.1},"
                    + "                \"colors\": [\"black\", \"silver\"]" + "            }," + "            {"
                    + "                \"id\": \"bike:2\"," + "                \"model\": \"Quaoar\","
                    + "                \"description\": \"Redesigned for the 2020 model year, this "
                    + "bike impressed our testers and is the best all-around trail bike we've ever "
                    + "tested. The Shimano gear system effectively does away with an external cassette, "
                    + "so is super low maintenance in terms of wear and tear. All in all it's an "
                    + "impressive package for the price, making it very competitive.\"," + "                \"price\": 2072,"
                    + "                \"specs\": {\"material\": \"aluminium\", \"weight\": 7.9},"
                    + "                \"colors\": [\"black\", \"white\"]" + "            }," + "            {"
                    + "                \"id\": \"bike:3\"," + "                \"model\": \"Weywot\","
                    + "                \"description\": \"This bike gives kids aged six years and older "
                    + "a durable and uberlight mountain bike for their first experience on tracks and easy "
                    + "cruising through forests and fields. A set of powerful Shimano hydraulic disc brakes "
                    + "provide ample stopping ability. If you're after a budget option, this is one of the "
                    + "best bikes you could get.\"," + "                \"price\": 3264,"
                    + "                \"specs\": {\"material\": \"alloy\", \"weight\": 13.8}" + "            }" + "        ],"
                    + "        \"commuter_bikes\": [" + "            {" + "                \"id\": \"bike:4\","
                    + "                \"model\": \"Salacia\","
                    + "                \"description\": \"This bike is a great option for anyone who just "
                    + "wants a bike to get about on With a slick-shifting Claris gears from Shimano\u2019s, "
                    + "this is a bike which doesn\u2019t break the bank and delivers craved performance.  "
                    + "It\u2019s for the rider who wants both efficiency and capability.\","
                    + "                \"price\": 1475,"
                    + "                \"specs\": {\"material\": \"aluminium\", \"weight\": 16.6},"
                    + "                \"colors\": [\"black\", \"silver\"]" + "            }," + "            {"
                    + "                \"id\": \"bike:5\"," + "                \"model\": \"Mimas\","
                    + "                \"description\": \"A real joy to ride, this bike got very high scores "
                    + "in last years Bike of the year report. The carefully crafted 50-34 tooth chainset "
                    + "and 11-32 tooth cassette give an easy-on-the-legs bottom gear for climbing, and the "
                    + "high-quality Vittoria Zaffiro tires give balance and grip.It includes a low-step "
                    + "frame , our memory foam seat, bump-resistant shocks and conveniently placed thumb "
                    + "throttle. Put it all together and you get a bike that helps redefine what can be "
                    + "done for this price.\"," + "                \"price\": 3941,"
                    + "                \"specs\": {\"material\": \"alloy\", \"weight\": 11.6}" + "            }" + "        ]"
                    + "    }" + "}";

            Charset charset = Charset.forName("UTF-8");
            ByteBuffer inventory_json = charset.encode(inventory_json_str);

            Mono<Void> setBikes = reactiveCommands
                    .jsonSet("bikes:inventory", JsonPath.ROOT_PATH, parser.loadJsonValue(inventory_json))
                    .doOnNext(System.out::println) // >>> OK
                    .then();
```

##### Java (Synchronous - Jedis)

```java
        String inventory_json = "{"
        + "    \"inventory\": {"
        + "        \"mountain_bikes\": ["
        + "            {"
        + "                \"id\": \"bike:1\","
        + "                \"model\": \"Phoebe\","
        + "                \"description\": \"This is a mid-travel trail slayer that is a "
        + "fantastic daily driver or one bike quiver. The Shimano Claris 8-speed groupset "
        + "gives plenty of gear range to tackle hills and there\u2019s room for mudguards "
        + "and a rack too.  This is the bike for the rider who wants trail manners with "
        + "low fuss ownership.\","
        + "                \"price\": 1920,"
        + "                \"specs\": {\"material\": \"carbon\", \"weight\": 13.1},"
        + "                \"colors\": [\"black\", \"silver\"]"
        + "            },"
        + "            {"
        + "                \"id\": \"bike:2\","
        + "                \"model\": \"Quaoar\","
        + "                \"description\": \"Redesigned for the 2020 model year, this "
        + "bike impressed our testers and is the best all-around trail bike we've ever "
        + "tested. The Shimano gear system effectively does away with an external cassette, "
        + "so is super low maintenance in terms of wear and tear. All in all it's an "
        + "impressive package for the price, making it very competitive.\","
        + "                \"price\": 2072,"
        + "                \"specs\": {\"material\": \"aluminium\", \"weight\": 7.9},"
        + "                \"colors\": [\"black\", \"white\"]"
        + "            },"
        + "            {"
        + "                \"id\": \"bike:3\","
        + "                \"model\": \"Weywot\","
        + "                \"description\": \"This bike gives kids aged six years and older "
        + "a durable and uberlight mountain bike for their first experience on tracks and easy "
        + "cruising through forests and fields. A set of powerful Shimano hydraulic disc brakes "
        + "provide ample stopping ability. If you're after a budget option, this is one of the "
        + "best bikes you could get.\","
        + "                \"price\": 3264,"
        + "                \"specs\": {\"material\": \"alloy\", \"weight\": 13.8}"
        + "            }"
        + "        ],"
        + "        \"commuter_bikes\": ["
        + "            {"
        + "                \"id\": \"bike:4\","
        + "                \"model\": \"Salacia\","
        + "                \"description\": \"This bike is a great option for anyone who just "
        + "wants a bike to get about on With a slick-shifting Claris gears from Shimano\u2019s, "
        + "this is a bike which doesn\u2019t break the bank and delivers craved performance.  "
        + "It\u2019s for the rider who wants both efficiency and capability.\","
        + "                \"price\": 1475,"
        + "                \"specs\": {\"material\": \"aluminium\", \"weight\": 16.6},"
        + "                \"colors\": [\"black\", \"silver\"]"
        + "            },"
        + "            {"
        + "                \"id\": \"bike:5\","
        + "                \"model\": \"Mimas\","
        + "                \"description\": \"A real joy to ride, this bike got very high scores "
        + "in last years Bike of the year report. The carefully crafted 50-34 tooth chainset "
        + "and 11-32 tooth cassette give an easy-on-the-legs bottom gear for climbing, and the "
        + "high-quality Vittoria Zaffiro tires give balance and grip.It includes a low-step "
        + "frame , our memory foam seat, bump-resistant shocks and conveniently placed thumb "
        + "throttle. Put it all together and you get a bike that helps redefine what can be "
        + "done for this price.\","
        + "                \"price\": 3941,"
        + "                \"specs\": {\"material\": \"alloy\", \"weight\": 11.6}"
        + "            }"
        + "        ]"
        + "    }"
        + "}";

        String res28 = jedis.jsonSet("bikes:inventory", new Path2("$"), inventory_json);
        System.out.println(res28);  // >>> OK
```

##### JavaScript (Node.js)

```javascript
const inventoryJSON = {
  "inventory": {
    "mountain_bikes": [{
        "id": "bike:1",
        "model": "Phoebe",
        "description": "This is a mid-travel trail slayer that is a fantastic daily driver or one bike quiver. The Shimano Claris 8-speed groupset gives plenty of gear range to tackle hills and there\u2019s room for mudguards and a rack too.  This is the bike for the rider who wants trail manners with low fuss ownership.",
        "price": 1920,
        "specs": {
          "material": "carbon",
          "weight": 13.1
        },
        "colors": ["black", "silver"],
      },
      {
        "id": "bike:2",
        "model": "Quaoar",
        "description": "Redesigned for the 2020 model year, this bike impressed our testers and is the best all-around trail bike we've ever tested. The Shimano gear system effectively does away with an external cassette, so is super low maintenance in terms of wear and teaawait client. All in all it's an impressive package for the price, making it very competitive.",
        "price": 2072,
        "specs": {
          "material": "aluminium",
          "weight": 7.9
        },
        "colors": ["black", "white"],
      },
      {
        "id": "bike:3",
        "model": "Weywot",
        "description": "This bike gives kids aged six years and older a durable and uberlight mountain bike for their first experience on tracks and easy cruising through forests and fields. A set of powerful Shimano hydraulic disc brakes provide ample stopping ability. If you're after a budget option, this is one of the best bikes you could get.",
        "price": 3264,
        "specs": {
          "material": "alloy",
          "weight": 13.8
        },
      },
    ],
    "commuter_bikes": [{
        "id": "bike:4",
        "model": "Salacia",
        "description": "This bike is a great option for anyone who just wants a bike to get about on With a slick-shifting Claris gears from Shimano\u2019s, this is a bike which doesn\u2019t break the bank and delivers craved performance.  It\u2019s for the rider who wants both efficiency and capability.",
        "price": 1475,
        "specs": {
          "material": "aluminium",
          "weight": 16.6
        },
        "colors": ["black", "silver"],
      },
      {
        "id": "bike:5",
        "model": "Mimas",
        "description": "A real joy to ride, this bike got very high scores in last years Bike of the year report. The carefully crafted 50-34 tooth chainset and 11-32 tooth cassette give an easy-on-the-legs bottom gear for climbing, and the high-quality Vittoria Zaffiro tires give balance and grip.It includes a low-step frame , our memory foam seat, bump-resistant shocks and conveniently placed thumb throttle. Put it all together and you get a bike that helps redefine what can be done for this price.",
        "price": 3941,
        "specs": {
          "material": "alloy",
          "weight": 11.6
        },
      },
    ],
  }
};

const res28 = await client.json.set("bikes:inventory", "$", inventoryJSON);
console.log(res28); //  OK
```

##### PHP

```php
        $inventory = [
            'inventory' => [
                'mountain_bikes' => [
                    [
                        'id' => 'bike:1',
                        'model' => 'Phoebe',
                        'description' => 'This is a mid-travel trail slayer that is a fantastic daily driver or one bike quiver. The Shimano Claris 8-speed groupset gives plenty of gear range to tackle hills and there’s room for mudguards and a rack too.  This is the bike for the rider who wants trail manners with low fuss ownership.',
                        'price' => 1920,
                        'specs' => ['material' => 'carbon', 'weight' => 13.1],
                        'colors' => ['black', 'silver'],
                    ],
                    [
                        'id' => 'bike:2',
                        'model' => 'Quaoar',
                        'description' => "Redesigned for the 2020 model year, this bike impressed our testers and is the best all-around trail bike we've ever tested. The Shimano gear system effectively does away with an external cassette, so is super low maintenance in terms of wear and tear. All in all it's an impressive package for the price, making it very competitive.",
                        'price' => 2072,
                        'specs' => ['material' => 'aluminium', 'weight' => 7.9],
                        'colors' => ['black', 'white'],
                    ],
                    [
                        'id' => 'bike:3',
                        'model' => 'Weywot',
                        'description' => 'This bike gives kids aged six years and older a durable and uberlight mountain bike for their first experience on tracks and easy cruising through forests and fields. A set of powerful Shimano hydraulic disc brakes provide ample stopping ability. If you\'re after a budget option, this is one of the best bikes you could get.',
                        'price' => 3264,
                        'specs' => ['material' => 'alloy', 'weight' => 13.8],
                    ],
                ],
                'commuter_bikes' => [
                    [
                        'id' => 'bike:4',
                        'model' => 'Salacia',
                        'description' => 'This bike is a great option for anyone who just wants a bike to get about on With a slick-shifting Claris gears from Shimano’s, this is a bike which doesn’t break the bank and delivers craved performance.  It’s for the rider who wants both efficiency and capability.',
                        'price' => 1475,
                        'specs' => ['material' => 'aluminium', 'weight' => 16.6],
                        'colors' => ['black', 'silver'],
                    ],
                    [
                        'id' => 'bike:5',
                        'model' => 'Mimas',
                        'description' => 'A real joy to ride, this bike got very high scores in last years Bike of the year report. The carefully crafted 50-34 tooth chainset and 11-32 tooth cassette give an easy-on-the-legs bottom gear for climbing, and the high-quality Vittoria Zaffiro tires give balance and grip.It includes a low-step frame , our memory foam seat, bump-resistant shocks and conveniently placed thumb throttle. Put it all together and you get a bike that helps redefine what can be done for this price.',
                        'price' => 3941,
                        'specs' => ['material' => 'alloy', 'weight' => 11.6],
                    ],
                ],
            ],
        ];
        $res1b = $r->jsonset('bikes:inventory', '$', json_encode($inventory, JSON_THROW_ON_ERROR));
        echo $res1b . PHP_EOL;
        // >>> OK
```

##### Python

```python
inventory_json = {
    "inventory": {
        "mountain_bikes": [
            {
                "id": "bike:1",
                "model": "Phoebe",
                "description": "This is a mid-travel trail slayer that is a fantastic "
                "daily driver or one bike quiver. The Shimano Claris 8-speed groupset "
                "gives plenty of gear range to tackle hills and there\u2019s room for "
                "mudguards and a rack too.  This is the bike for the rider who wants "
                "trail manners with low fuss ownership.",
                "price": 1920,
                "specs": {"material": "carbon", "weight": 13.1},
                "colors": ["black", "silver"],
            },
            {
                "id": "bike:2",
                "model": "Quaoar",
                "description": "Redesigned for the 2020 model year, this bike "
                "impressed our testers and is the best all-around trail bike we've "
                "ever tested. The Shimano gear system effectively does away with an "
                "external cassette, so is super low maintenance in terms of wear "
                "and tear. All in all it's an impressive package for the price, "
                "making it very competitive.",
                "price": 2072,
                "specs": {"material": "aluminium", "weight": 7.9},
                "colors": ["black", "white"],
            },
            {
                "id": "bike:3",
                "model": "Weywot",
                "description": "This bike gives kids aged six years and older "
                "a durable and uberlight mountain bike for their first experience "
                "on tracks and easy cruising through forests and fields. A set of "
                "powerful Shimano hydraulic disc brakes provide ample stopping "
                "ability. If you're after a budget option, this is one of the best "
                "bikes you could get.",
                "price": 3264,
                "specs": {"material": "alloy", "weight": 13.8},
            },
        ],
        "commuter_bikes": [
            {
                "id": "bike:4",
                "model": "Salacia",
                "description": "This bike is a great option for anyone who just "
                "wants a bike to get about on With a slick-shifting Claris gears "
                "from Shimano\u2019s, this is a bike which doesn\u2019t break the "
                "bank and delivers craved performance.  It\u2019s for the rider "
                "who wants both efficiency and capability.",
                "price": 1475,
                "specs": {"material": "aluminium", "weight": 16.6},
                "colors": ["black", "silver"],
            },
            {
                "id": "bike:5",
                "model": "Mimas",
                "description": "A real joy to ride, this bike got very high "
                "scores in last years Bike of the year report. The carefully "
                "crafted 50-34 tooth chainset and 11-32 tooth cassette give an "
                "easy-on-the-legs bottom gear for climbing, and the high-quality "
                "Vittoria Zaffiro tires give balance and grip.It includes "
                "a low-step frame , our memory foam seat, bump-resistant shocks and "
                "conveniently placed thumb throttle. Put it all together and you "
                "get a bike that helps redefine what can be done for this price.",
                "price": 3941,
                "specs": {"material": "alloy", "weight": 11.6},
            },
        ],
    }
}

res1 = r.json().set("bikes:inventory", "$", inventory_json)
print(res1)  # >>> True
```

##### Ruby

```ruby
inventory_json = {
  'inventory' => {
    'mountain_bikes' => [
      {
        'id' => 'bike:1',
        'model' => 'Phoebe',
        'description' => 'This is a mid-travel trail slayer that is a fantastic ' \
          'daily driver or one bike quiver. The Shimano Claris 8-speed groupset ' \
          'gives plenty of gear range to tackle hills and there’s room for ' \
          'mudguards and a rack too.  This is the bike for the rider who wants ' \
          'trail manners with low fuss ownership.',
        'price' => 1920,
        'specs' => { 'material' => 'carbon', 'weight' => 13.1 },
        'colors' => %w[black silver]
      },
      {
        'id' => 'bike:2',
        'model' => 'Quaoar',
        'description' => 'Redesigned for the 2020 model year, this bike ' \
          "impressed our testers and is the best all-around trail bike we've " \
          'ever tested. The Shimano gear system effectively does away with an ' \
          'external cassette, so is super low maintenance in terms of wear ' \
          "and tear. All in all it's an impressive package for the price, " \
          'making it very competitive.',
        'price' => 2072,
        'specs' => { 'material' => 'aluminium', 'weight' => 7.9 },
        'colors' => %w[black white]
      },
      {
        'id' => 'bike:3',
        'model' => 'Weywot',
        'description' => 'This bike gives kids aged six years and older ' \
          'a durable and uberlight mountain bike for their first experience ' \
          'on tracks and easy cruising through forests and fields. A set of ' \
          'powerful Shimano hydraulic disc brakes provide ample stopping ' \
          "ability. If you're after a budget option, this is one of the best " \
          'bikes you could get.',
        'price' => 3264,
        'specs' => { 'material' => 'alloy', 'weight' => 13.8 }
      }
    ],
    'commuter_bikes' => [
      {
        'id' => 'bike:4',
        'model' => 'Salacia',
        'description' => 'This bike is a great option for anyone who just ' \
          'wants a bike to get about on With a slick-shifting Claris gears ' \
          'from Shimano’s, this is a bike which doesn’t break the ' \
          "bank and delivers craved performance.  It's for the rider " \
          'who wants both efficiency and capability.',
        'price' => 1475,
        'specs' => { 'material' => 'aluminium', 'weight' => 16.6 },
        'colors' => %w[black silver]
      },
      {
        'id' => 'bike:5',
        'model' => 'Mimas',
        'description' => 'A real joy to ride, this bike got very high ' \
          'scores in last years Bike of the year report. The carefully ' \
          'crafted 50-34 tooth chainset and 11-32 tooth cassette give an ' \
          'easy-on-the-legs bottom gear for climbing, and the high-quality ' \
          'Vittoria Zaffiro tires give balance and grip.It includes ' \
          'a low-step frame , our memory foam seat, bump-resistant shocks and ' \
          'conveniently placed thumb throttle. Put it all together and you ' \
          'get a bike that helps redefine what can be done for this price.',
        'price' => 3941,
        'specs' => { 'material' => 'alloy', 'weight' => 11.6 }
      }
    ]
  }
}

res1 = r.json_set('bikes:inventory', '$', inventory_json)
puts res1 # >>> OK
```

##### Rust (Asynchronous)

```rust
        let res29: bool = r
            .json_set("bikes:inventory", "$", &inventory_json())
            .await
            .expect("Failed to run JSON.SET");
        print_set_result(res29); // >>> OK
```

##### Rust (Synchronous)

```rust
        let res29: bool = r
            .json_set("bikes:inventory", "$", &inventory_json())
            .expect("Failed to run JSON.SET");
        print_set_result(res29); // >>> OK
```



### Ví dụ truy cập (Access examples)

Các ví dụ sau dùng lệnh [`JSON.GET`](https://redis.io/docs/latest/commands/json.get) để truy xuất dữ liệu từ nhiều path khác nhau trong JSON document.

Bạn có thể dùng toán tử wildcard `*` để trả về danh sách tất cả các mặt hàng trong kho:

*Truy vấn wildcard: Dùng toán tử * để lấy tất cả các phần tử trong một collection khi bạn cần truy cập mọi phần tử ở một cấp cụ thể*

**Dựa trên (Builds upon):** set_bikes

**Lệnh (Commands):** JSON.GET

**Độ phức tạp (Complexity):**
- JSON.GET: O(N)

**Có sẵn trong (Available in):** Redis CLI, C#, Go, Java (Asynchronous - Lettuce), Java (Reactive - Lettuce), Java (Synchronous - Jedis), JavaScript (Node.js), PHP, Python, Ruby, Rust (Asynchronous), Rust (Synchronous)

##### Redis CLI

```
> JSON.GET bikes:inventory $.inventory.*
"[[{\"id\":\"bike:1\",\"model\":\"Phoebe\",\"description\":\"This is a mid-travel trail slayer that is a fantastic daily driver or one bike quiver. The Shimano Claris 8-speed groupset gives plenty of gear range to tackle hills and there's room for mudguards and a rack too. This is the bike for the rider who wants trail manners with low fuss ownership.\",\"price\":1920,\"specs\":{\"material\":\"carbon\",\"weight\":13.1},\"colors\":[\"black\",\"silver\"]},{\"id\":\"bike:2\",\"model\":\"Quaoar\",\"description\":\"Redesigned for the 2020 model year, this bike impressed our testers and is the best all-around trail bike we've ever tested. The Shimano gear system effectively does away with an external cassette, so is super low maintenance in terms of wear and tear. All in all it's an impressive package for the price, making it very competitive.\",\"price\":2072,\"specs\":{\"material\":\"aluminium\",\"weight\":7.9},\"colors\":[\"black\",\"white\"]},{\"id\":\"bike:3\",\"model\":\"Weywot\",\"description\":\"This bike gives kids aged six years and older a durable and uberlight mountain bike for their first experience on tracks and easy cruising through forests and fields. A set of powerful Shimano hydraulic disc brakes provide ample stopping ability. If you're after a budget option, this is one of the best bikes you could get.\",\"price\":3264,\"specs\":{\"material\":\"alloy\",\"weight\":13.8}}],[{\"id\":\"bike:4\",\"model\":\"Salacia\",\"description\":\"This bike is a great option for anyone who just wants a bike to get about on With a slick-shifting Claris gears from Shimano's, this is a bike which doesn't break the bank and delivers craved performance. It's for the rider who wants both efficiency and capability.\",\"price\":1475,\"specs\":{\"material\":\"aluminium\",\"weight\":16.6},\"colors\":[\"black\",\"silver\"]},{\"id\":\"bike:5\",\"model\":\"Mimas\",\"description\":\"A real joy to ride, this bike got very high scores in last years Bike of the year report. The carefully crafted 50-34 tooth chainset and 11-32 tooth cassette give an easy-on-the-legs bottom gear for climbing, and the high-quality Vittoria Zaffiro tires give balance and grip.It includes a low-step frame , our memory foam seat, bump-resistant shocks and conveniently placed thumb throttle. Put it all together and you get a bike that helps redefine what can be done for this price.\",\"price\":3941,\"specs\":{\"material\":\"alloy\",\"weight\":11.6}}]]"
```

##### C#

```csharp
        RedisResult res29 = db.JSON().Get("bikes:inventory", path: "$.inventory.*");
        Console.WriteLine(res29);   // >>> {[[{"id":"bike:1","model":"Phoebe", ...
```

##### Go

```go
	res2, err := rdb.JSONGetWithArgs(ctx, "bikes:inventory",
		&redis.JSONGetArgs{Indent: "  ", Newline: "\n", Space: " "},
		"$.inventory.*",
	).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res2)
	// >>>
	// [
	//   [
	//     {
	//       "colors": [
	//         "black",
	//         "silver"
	// ...
```

##### Java (Asynchronous - Lettuce)

```java
            CompletableFuture<Void> getBikes = asyncCommands.jsonGet("bikes:inventory", JsonPath.of("$.inventory.*"))
                    .thenAccept(System.out::println)
                    // >>> [[[{"id":"bike:1","model":"Phoebe",...
                    .toCompletableFuture();
```

##### Java (Reactive - Lettuce)

```java
            Mono<Void> getBikes = reactiveCommands.jsonGet("bikes:inventory", JsonPath.of("$.inventory.*")).collectList()
                    .doOnNext(System.out::println) // >>> [[[{"id":"bike:1","model":"Phoebe",...
                    .then();
```

##### Java (Synchronous - Jedis)

```java
        Object res29 = jedis.jsonGet("bikes:inventory", new Path2("$.inventory.*"));
        System.out.println(res29);
        // >>> [[{"specs":{"material":"carbon","weight":13.1},"price":1920, ...
```

##### JavaScript (Node.js)

```javascript
const res29 = await client.json.get("bikes:inventory", {
  path: "$.inventory.*"
});
console.log(res29);
/*
[
  [
    {
      id: 'bike:1',
      model: 'Phoebe',
      description: 'This is a mid-travel trail slayer that is a fantastic daily driver or one bike quiver. The Shimano Claris 8-speed groupset gives plenty of gear range to tackle hills and there’s room for mudguards and a rack too.  This is the bike for the rider who wants trail manners with low fuss ownership.',
      price: 1920,
      specs: [Object],
      colors: [Array]
    },
    {
      id: 'bike:2',
      model: 'Quaoar',
      description: "Redesigned for the 2020 model year, this bike impressed our testers and is the best all-around trail bike we've ever tested. The Shimano gear system effectively does away with an external cassette, so is super low maintenance in terms of wear and teaawait client. All in all it's an impressive package for the price, making it very competitive.",
      price: 2072,
      specs: [Object],
      colors: [Array]
    },
    {
      id: 'bike:3',
      model: 'Weywot',
      description: "This bike gives kids aged six years and older a durable and uberlight mountain bike for their first experience on tracks and easy cruising through forests and fields. A set of powerful Shimano hydraulic disc brakes provide ample stopping ability. If you're after a budget option, this is one of the best bikes you could get.",
      price: 3264,
      specs: [Object]
    }
  ],
  [
    {
      id: 'bike:4',
      model: 'Salacia',
      description: 'This bike is a great option for anyone who just wants a bike to get about on With a slick-shifting Claris gears from Shimano’s, this is a bike which doesn’t break the bank and delivers craved performance.  It’s for the rider who wants both efficiency and capability.',
      price: 1475,
      specs: [Object],
      colors: [Array]
    },
    {
      id: 'bike:5',
      model: 'Mimas',
      description: 'A real joy to ride, this bike got very high scores in last years Bike of the year report. The carefully crafted 50-34 tooth chainset and 11-32 tooth cassette give an easy-on-the-legs bottom gear for climbing, and the high-quality Vittoria Zaffiro tires give balance and grip.It includes a low-step frame , our memory foam seat, bump-resistant shocks and conveniently placed thumb throttle. Put it all together and you get a bike that helps redefine what can be done for this price.',
      price: 3941,
      specs: [Object]
    }
  ]
]
*/
```

##### PHP

```php
        $res2b = $r->jsonget('bikes:inventory', '', '', '', '$.inventory.*');
        echo $res2b . PHP_EOL;
        // >>> [{'id': 'bike:1', 'model': 'Phoebe',
```

##### Python

```python
res2 = r.json().get("bikes:inventory", "$.inventory.*")
print(res2)
# >>>    [[{'id': 'bike:1', 'model': 'Phoebe',
# >>>       'description': 'This is a mid-travel trail slayer...
```

##### Ruby

```ruby
res2 = r.json_get('bikes:inventory', '$.inventory.*')
p res2
# >>> [[{"id"=>"bike:1", "model"=>"Phoebe",
# >>>    "description"=>"This is a mid-travel trail slayer...
```

##### Rust (Asynchronous)

```rust
        let res30: String = r
            .json_get("bikes:inventory", "$.inventory.*")
            .await
            .expect("Failed to run JSON.GET");
        println!("{res30}");
        // >>> [[{"id":"bike:1","model":"Phoebe","description":"This is a mid-travel trail slayer...
```

##### Rust (Synchronous)

```rust
        let res30: String = r
            .json_get("bikes:inventory", "$.inventory.*")
            .expect("Failed to run JSON.GET");
        println!("{res30}");
        // >>> [[{"id":"bike:1","model":"Phoebe","description":"This is a mid-travel trail slayer...
```



Với một số truy vấn, nhiều path có thể cho ra cùng kết quả. Ví dụ, các path sau đều trả về tên của tất cả các xe đạp leo núi (mountain bike):

*Truy cập phần tử mảng: Dùng ký pháp ngoặc vuông và chỉ số mảng để truy cập các phần tử cụ thể, hoặc tất cả phần tử bằng [*], khi bạn cần lấy giá trị từ mảng*

**Độ khó (Difficulty):** Trung bình (Intermediate)

**Dựa trên (Builds upon):** get_bikes

**Lệnh (Commands):** JSON.GET

**Độ phức tạp (Complexity):**
- JSON.GET: O(N)

**Có sẵn trong (Available in):** Redis CLI, C#, Go, Java (Asynchronous - Lettuce), Java (Reactive - Lettuce), Java (Synchronous - Jedis), JavaScript (Node.js), PHP, Python, Ruby, Rust (Asynchronous), Rust (Synchronous)

##### Redis CLI

```
> JSON.GET bikes:inventory $.inventory.mountain_bikes[*].model
"[\"Phoebe\",\"Quaoar\",\"Weywot\"]"
> JSON.GET bikes:inventory '$.inventory["mountain_bikes"][*].model'
"[\"Phoebe\",\"Quaoar\",\"Weywot\"]"
> JSON.GET bikes:inventory '$..mountain_bikes[*].model'
"[\"Phoebe\",\"Quaoar\",\"Weywot\"]"
```

##### C#

```csharp
        RedisResult res30 = db.JSON().Get("bikes:inventory", path: "$.inventory.mountain_bikes[*].model");
        Console.WriteLine(res30);   // >>> ["Phoebe","Quaoar","Weywot"]

        RedisResult res31 = db.JSON().Get("bikes:inventory", path: "$.inventory[\"mountain_bikes\"][*].model");
        Console.WriteLine(res31);   // >>> ["Phoebe","Quaoar","Weywot"]

        RedisResult res32 = db.JSON().Get("bikes:inventory", path: "$..mountain_bikes[*].model");
        Console.WriteLine(res32);   // >>> ["Phoebe","Quaoar","Weywot"]
```

##### Go

```go
	res3, err := rdb.JSONGet(ctx, "bikes:inventory",
		"$.inventory.mountain_bikes[*].model",
	).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res3)
	// >>> ["Phoebe","Quaoar","Weywot"]

	res4, err := rdb.JSONGet(ctx,
		"bikes:inventory", "$.inventory[\"mountain_bikes\"][*].model",
	).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res4)
	// >>> ["Phoebe","Quaoar","Weywot"]

	res5, err := rdb.JSONGet(ctx,
		"bikes:inventory", "$..mountain_bikes[*].model",
	).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res5)
	// >>> ["Phoebe","Quaoar","Weywot"]
```

##### Java (Asynchronous - Lettuce)

```java
            CompletableFuture<Void> getMtnBikes = asyncCommands
                    .jsonGet("bikes:inventory", JsonPath.of("$.inventory.mountain_bikes[*].model")).thenCompose(res19 -> {
                        System.out.println(res19); // >>> [["Phoebe","Quaoar","Weywot"]]
                        return asyncCommands.jsonGet("bikes:inventory",
                                JsonPath.of("$.inventory[\"mountain_bikes\"][*].model"));
                    }).thenCompose(res20 -> {
                        System.out.println(res20); // >>> [["Phoebe","Quaoar","Weywot"]]
                        return asyncCommands.jsonGet("bikes:inventory", JsonPath.of("$..mountain_bikes[*].model"));
                    })
                    .thenAccept(System.out::println)
                    // >>> [["Phoebe","Quaoar","Weywot"]]
                    .toCompletableFuture();
```

##### Java (Reactive - Lettuce)

```java
            Mono<Void> getMtnBikes = reactiveCommands
                    .jsonGet("bikes:inventory", JsonPath.of("$.inventory.mountain_bikes[*].model")).collectList()
                    .doOnNext(res19 -> {
                        System.out.println(res19); // >>> [["Phoebe","Quaoar","Weywot"]]
                    })
                    .flatMap(res19 -> reactiveCommands
                            .jsonGet("bikes:inventory", JsonPath.of("$.inventory[\"mountain_bikes\"][*].model")).collectList())
                    .doOnNext(res20 -> {
                        System.out.println(res20); // >>> [["Phoebe","Quaoar","Weywot"]]
                    })
                    .flatMap(res20 -> reactiveCommands.jsonGet("bikes:inventory", JsonPath.of("$..mountain_bikes[*].model"))
                            .collectList())
                    .doOnNext(System.out::println) // >>> [["Phoebe","Quaoar","Weywot"]]
                    .then();
```

##### Java (Synchronous - Jedis)

```java
        Object res30 = jedis.jsonGet(
            "bikes:inventory", new Path2("$.inventory.mountain_bikes[*].model")
        );
        System.out.println(res30);  // >>> ["Phoebe","Quaoar","Weywot"]

        Object res31 = jedis.jsonGet(
            "bikes:inventory", new Path2("$.inventory[\"mountain_bikes\"][*].model")
        );
        System.out.println(res31);  // >>> ["Phoebe","Quaoar","Weywot"]

        Object res32 = jedis.jsonGet(
            "bikes:inventory", new Path2("$..mountain_bikes[*].model")
        );
        System.out.println(res32);  // >>> ["Phoebe","Quaoar","Weywot"]
```

##### JavaScript (Node.js)

```javascript
const res30 = await client.json.get("bikes:inventory", {
  path: "$.inventory.mountain_bikes[*].model"
});
console.log(res30); //  ['Phoebe', 'Quaoar', 'Weywot']

const res31 = await client.json.get("bikes:inventory", {
  path: '$.inventory["mountain_bikes"][*].model'
});
console.log(res31); //  ['Phoebe', 'Quaoar', 'Weywot']

const res32 = await client.json.get("bikes:inventory", {
  path: "$..mountain_bikes[*].model"
});
console.log(res32); //  ['Phoebe', 'Quaoar', 'Weywot']
```

##### PHP

```php
        $res3b = $r->jsonget('bikes:inventory', '', '', '', '$.inventory.mountain_bikes[*].model');
        echo $res3b . PHP_EOL;
        // >>> ["Phoebe","Quaoar","Weywot"]

        $res4b = $r->jsonget('bikes:inventory', '', '', '', '$.inventory["mountain_bikes"][*].model');
        echo $res4b . PHP_EOL;
        // >>> ["Phoebe","Quaoar","Weywot"]

        $res5b = $r->jsonget('bikes:inventory', '', '', '', '$..mountain_bikes[*].model');
        echo $res5b . PHP_EOL;
        // >>> ["Phoebe","Quaoar","Weywot"]
```

##### Python

```python
res3 = r.json().get("bikes:inventory", "$.inventory.mountain_bikes[*].model")
print(res3)  # >>> [['Phoebe', 'Quaoar', 'Weywot']]

res4 = r.json().get("bikes:inventory", '$.inventory["mountain_bikes"][*].model')
print(res4)  # >>> [['Phoebe', 'Quaoar', 'Weywot']]

res5 = r.json().get("bikes:inventory", "$..mountain_bikes[*].model")
print(res5)  # >>> [['Phoebe', 'Quaoar', 'Weywot']]
```

##### Ruby

```ruby
res3 = r.json_get('bikes:inventory', '$.inventory.mountain_bikes[*].model')
p res3 # >>> ["Phoebe", "Quaoar", "Weywot"]

res4 = r.json_get('bikes:inventory', '$.inventory["mountain_bikes"][*].model')
p res4 # >>> ["Phoebe", "Quaoar", "Weywot"]

res5 = r.json_get('bikes:inventory', '$..mountain_bikes[*].model')
p res5 # >>> ["Phoebe", "Quaoar", "Weywot"]
```

##### Rust (Asynchronous)

```rust
        let res31: String = r
            .json_get("bikes:inventory", "$.inventory.mountain_bikes[*].model")
            .await
            .expect("Failed to run JSON.GET");
        println!("{res31}"); // >>> [["Phoebe","Quaoar","Weywot"]]

        let res32: String = r
            .json_get(
                "bikes:inventory",
                r#"$.inventory["mountain_bikes"][*].model"#,
            )
            .await
            .expect("Failed to run JSON.GET");
        println!("{res32}"); // >>> [["Phoebe","Quaoar","Weywot"]]

        let res33: String = r
            .json_get("bikes:inventory", "$..mountain_bikes[*].model")
            .await
            .expect("Failed to run JSON.GET");
        println!("{res33}"); // >>> [["Phoebe","Quaoar","Weywot"]]
```

##### Rust (Synchronous)

```rust
        let res31: String = r
            .json_get("bikes:inventory", "$.inventory.mountain_bikes[*].model")
            .expect("Failed to run JSON.GET");
        println!("{res31}"); // >>> [["Phoebe","Quaoar","Weywot"]]

        let res32: String = r
            .json_get(
                "bikes:inventory",
                r#"$.inventory["mountain_bikes"][*].model"#,
            )
            .expect("Failed to run JSON.GET");
        println!("{res32}"); // >>> [["Phoebe","Quaoar","Weywot"]]

        let res33: String = r
            .json_get("bikes:inventory", "$..mountain_bikes[*].model")
            .expect("Failed to run JSON.GET");
        println!("{res33}"); // >>> [["Phoebe","Quaoar","Weywot"]]
```



Toán tử đi xuống đệ quy (recursive descent) `..` có thể lấy một field từ nhiều phần của một JSON document. Ví dụ sau trả về tên của tất cả các mặt hàng trong kho:

*Đi xuống đệ quy: Dùng toán tử .. để tìm một field ở bất kỳ độ sâu nào trong JSON document khi bạn cần tìm giá trị qua nhiều cấp lồng nhau*

**Độ khó (Difficulty):** Trung bình (Intermediate)

**Dựa trên (Builds upon):** get_mtnbikes

**Lệnh (Commands):** JSON.GET

**Độ phức tạp (Complexity):**
- JSON.GET: O(N)

**Có sẵn trong (Available in):** Redis CLI, C#, Go, Java (Asynchronous - Lettuce), Java (Reactive - Lettuce), Java (Synchronous - Jedis), JavaScript (Node.js), PHP, Python, Ruby, Rust (Asynchronous), Rust (Synchronous)

##### Redis CLI

```
> JSON.GET bikes:inventory $..model
"[\"Phoebe\",\"Quaoar\",\"Weywot\",\"Salacia\",\"Mimas\"]"
```

##### C#

```csharp
        RedisResult res33 = db.JSON().Get("bikes:inventory", path: "$..model");
        Console.WriteLine(res33);   // >>> ["Phoebe","Quaoar","Weywot","Salacia","Mimas"]
```

##### Go

```go
	res6, err := rdb.JSONGet(ctx, "bikes:inventory", "$..model").Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res6) // >>> ["Salacia","Mimas","Phoebe","Quaoar","Weywot"]
```

##### Java (Asynchronous - Lettuce)

```java
            CompletableFuture<Void> getModels = asyncCommands.jsonGet("bikes:inventory", JsonPath.of("$..model"))
                    .thenAccept(System.out::println)
                    // >>> [["Phoebe","Quaoar","Weywot","Salacia","Mimas"]]
                    .toCompletableFuture();
```

##### Java (Reactive - Lettuce)

```java
            Mono<Void> getModels = reactiveCommands.jsonGet("bikes:inventory", JsonPath.of("$..model")).collectList()
                    .doOnNext(System.out::println) // >>> [["Phoebe","Quaoar","Weywot","Salacia","Mimas"]]
                    .then();
```

##### Java (Synchronous - Jedis)

```java
        Object res33 = jedis.jsonGet("bikes:inventory", new Path2("$..model"));
        System.out.println(res33);
        // >>> ["Phoebe","Quaoar","Weywot","Salacia","Mimas"]
```

##### JavaScript (Node.js)

```javascript
const res33 = await client.json.get("bikes:inventory", {
  path: "$..model"
});
console.log(res33); //  ['Phoebe', 'Quaoar', 'Weywot', 'Salacia', 'Mimas']
```

##### PHP

```php
        $res6b = $r->jsonget('bikes:inventory', '', '', '', '$..model');
        echo $res6b . PHP_EOL;
        // >>> ["Phoebe","Quaoar","Weywot","Salacia","Mimas"]
```

##### Python

```python
res6 = r.json().get("bikes:inventory", "$..model")
print(res6)  # >>> [['Phoebe', 'Quaoar', 'Weywot', 'Salacia', 'Mimas']]
```

##### Ruby

```ruby
res6 = r.json_get('bikes:inventory', '$..model')
p res6 # >>> ["Phoebe", "Quaoar", "Weywot", "Salacia", "Mimas"]
```

##### Rust (Asynchronous)

```rust
        let res34: String = r
            .json_get("bikes:inventory", "$..model")
            .await
            .expect("Failed to run JSON.GET");
        println!("{res34}"); // >>> [["Phoebe","Quaoar","Weywot","Salacia","Mimas"]]
```

##### Rust (Synchronous)

```rust
        let res34: String = r
            .json_get("bikes:inventory", "$..model")
            .expect("Failed to run JSON.GET");
        println!("{res34}"); // >>> [["Phoebe","Quaoar","Weywot","Salacia","Mimas"]]
```



Bạn có thể dùng array slice để chọn một dải phần tử từ một mảng. Ví dụ này trả về tên của 2 xe đạp leo núi đầu tiên:

*Cắt mảng: Dùng cú pháp [start:end] để chọn một dải phần tử mảng khi bạn cần lấy một tập con các phần tử từ một mảng*

**Độ khó (Difficulty):** Trung bình (Intermediate)

**Dựa trên (Builds upon):** get_mtnbikes

**Lệnh (Commands):** JSON.GET

**Độ phức tạp (Complexity):**
- JSON.GET: O(N)

**Có sẵn trong (Available in):** Redis CLI, C#, Go, Java (Asynchronous - Lettuce), Java (Reactive - Lettuce), Java (Synchronous - Jedis), JavaScript (Node.js), PHP, Python, Ruby, Rust (Asynchronous), Rust (Synchronous)

##### Redis CLI

```
> JSON.GET bikes:inventory $..mountain_bikes[0:2].model
"[\"Phoebe\",\"Quaoar\"]"
```

##### C#

```csharp
        RedisResult res34 = db.JSON().Get("bikes:inventory", path: "$..mountain_bikes[0:2].model");
        Console.WriteLine(res34);   // >>> ["Phoebe","Quaoar"]
```

##### Go

```go
	res7, err := rdb.JSONGet(ctx, "bikes:inventory", "$..mountain_bikes[0:2].model").Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res7) // >>> ["Phoebe","Quaoar"]
```

##### Java (Asynchronous - Lettuce)

```java
            CompletableFuture<Void> get2MtnBikes = asyncCommands
                    .jsonGet("bikes:inventory", JsonPath.of("$..mountain_bikes[0:2].model"))
                    .thenAccept(System.out::println)
                    // >>> [["Phoebe","Quaoar"]]
                    .toCompletableFuture();
```

##### Java (Reactive - Lettuce)

```java
            Mono<Void> get2MtnBikes = reactiveCommands.jsonGet("bikes:inventory", JsonPath.of("$..mountain_bikes[0:2].model"))
                    .collectList()
                    .doOnNext(System.out::println) // >>> [["Phoebe","Quaoar"]]
                    .then();
```

##### Java (Synchronous - Jedis)

```java
        Object res34 = jedis.jsonGet(
            "bikes:inventory", new Path2("$..mountain_bikes[0:2].model")
        );
        System.out.println(res34);  // >>> ["Phoebe","Quaoar"]
```

##### JavaScript (Node.js)

```javascript
const res34 = await client.json.get("bikes:inventory", {
  path: "$..mountain_bikes[0:2].model"
});
console.log(res34); //  ['Phoebe', 'Quaoar']
```

##### PHP

```php
        $res7b = $r->jsonget('bikes:inventory', '', '', '', '$..mountain_bikes[0:2].model');
        echo $res7b . PHP_EOL;
        // >>> ["Phoebe","Quaoar"]
```

##### Python

```python
res7 = r.json().get("bikes:inventory", "$..mountain_bikes[0:2].model")
print(res7)  # >>> [['Phoebe', 'Quaoar']]
```

##### Ruby

```ruby
res7 = r.json_get('bikes:inventory', '$..mountain_bikes[0:2].model')
p res7 # >>> ["Phoebe", "Quaoar"]
```

##### Rust (Asynchronous)

```rust
        let res35: String = r
            .json_get("bikes:inventory", "$..mountain_bikes[0:2].model")
            .await
            .expect("Failed to run JSON.GET");
        println!("{res35}"); // >>> [["Phoebe","Quaoar"]]
```

##### Rust (Synchronous)

```rust
        let res35: String = r
            .json_get("bikes:inventory", "$..mountain_bikes[0:2].model")
            .expect("Failed to run JSON.GET");
        println!("{res35}"); // >>> [["Phoebe","Quaoar"]]
```



Biểu thức filter `?()` cho phép bạn chọn các phần tử JSON dựa trên những điều kiện nhất định. Bạn có thể dùng các toán tử so sánh (`==`, `!=`, `<`, `<=`, `>`, `>=`, và bắt đầu từ phiên bản v2.4.2, cả `=~`), toán tử logic (`&&`, `||`), và dấu ngoặc đơn (`(`, `)`) bên trong các biểu thức này. Một biểu thức filter có thể được áp dụng trên một mảng hoặc một object, duyệt qua tất cả các **phần tử** trong mảng hoặc tất cả các **value** trong object, và chỉ lấy những cái khớp với điều kiện filter.

Các path bên trong điều kiện filter dùng ký pháp dấu chấm với `@` để biểu thị phần tử mảng hiện tại hoặc value hiện tại của object, hoặc `$` để biểu thị phần tử cấp cao nhất. Ví dụ, dùng `@.key_name` để tham chiếu tới một giá trị lồng bên trong và `$.top_level_key_name` để tham chiếu tới một giá trị cấp cao nhất.

Từ phiên bản v2.4.2 trở đi, bạn có thể dùng toán tử so sánh `=~` để so khớp một path có giá trị string ở vế trái với một pattern biểu thức chính quy (regular expression) ở vế phải. Để biết thêm thông tin, xem [tài liệu cú pháp regular expression được hỗ trợ](https://docs.rs/regex/latest/regex/#syntax).

Các giá trị không phải string sẽ không khớp. Chỉ có thể khớp khi vế trái là một path có giá trị string và vế phải là một string viết cứng (hard-coded) hoặc một path có giá trị string. Xem [ví dụ](#json-filter-examples) bên dưới.

Phép so khớp regex là khớp một phần (partial), nghĩa là một pattern regex như `"foo"` sẽ khớp với một string như `"barefoots"`.
Để khớp chính xác, hãy dùng pattern regex `"^foo$"`.

Các engine JSONPath khác có thể dùng pattern regex nằm giữa các dấu gạch chéo (ví dụ, `/foo/`),
và phép so khớp của chúng là chính xác. Chúng có thể thực hiện khớp một phần bằng một pattern regex như
`/.*foo.*/`.

<a name="json-filter-examples"></a>

### Ví dụ filter (Filter examples)

Trong ví dụ sau, filter chỉ trả về các xe đạp leo núi có giá nhỏ hơn 3000 và
trọng lượng nhỏ hơn 10:

*Biểu thức filter: Dùng ?() với các toán tử so sánh và logic để chọn các phần tử khớp với điều kiện cụ thể khi bạn cần truy vấn dựa trên nhiều tiêu chí*

**Độ khó (Difficulty):** Nâng cao (Advanced)

**Dựa trên (Builds upon):** get_models

**Lệnh (Commands):** JSON.GET

**Độ phức tạp (Complexity):**
- JSON.GET: O(N)

**Có sẵn trong (Available in):** Redis CLI, C#, Go, Java (Asynchronous - Lettuce), Java (Reactive - Lettuce), Java (Synchronous - Jedis), JavaScript (Node.js), PHP, Python, Ruby, Rust (Asynchronous), Rust (Synchronous)

##### Redis CLI

```
> JSON.GET bikes:inventory '$..mountain_bikes[?(@.price < 3000 && @.specs.weight < 10)]'
"[{\"id\":\"bike:2\",\"model\":\"Quaoar\",\"description\":\"Redesigned for the 2020 model year, this bike impressed our testers and is the best all-around trail bike we've ever tested. The Shimano gear system effectively does away with an external cassette, so is super low maintenance in terms of wear and tear. All in all it's an impressive package for the price, making it very competitive.\",\"price\":2072,\"specs\":{\"material\":\"aluminium\",\"weight\":7.9},\"colors\":[\"black\",\"white\"]}]"
```

##### C#

```csharp
        RedisResult res35 = db.JSON().Get(
            "bikes:inventory",
            path: "$..mountain_bikes[?(@.price < 3000 && @.specs.weight < 10)]"
        );
        Console.WriteLine(res35);
        // >>> [{"id":"bike:2","model":"Quaoar","description":"Redesigned for the 2020 model year...
```

##### Go

```go
	res8, err := rdb.JSONGetWithArgs(ctx, "bikes:inventory",
		&redis.JSONGetArgs{Indent: "  ", Newline: "\n", Space: " "},
		"$..mountain_bikes[?(@.price < 3000 && @.specs.weight < 10)]",
	).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res8)
	// >>>
	// [
	//   {
	//     "colors": [
	//       "black",
	//       "white"
	//     ],
	//     "description": "Redesigned for the 2020 model year
	// ...
```

##### Java (Asynchronous - Lettuce)

```java
            CompletableFuture<Void> filter1 = asyncCommands
                    .jsonGet("bikes:inventory", JsonPath.of("$..mountain_bikes[?(@.price < 3000 && @.specs.weight < 10)]"))
                    .thenAccept(System.out::println)
                    // >>> [[{"id":"bike:2","model":"Quaoar","description":...
                    .toCompletableFuture();
```

##### Java (Reactive - Lettuce)

```java
            Mono<Void> filter1 = reactiveCommands
                    .jsonGet("bikes:inventory", JsonPath.of("$..mountain_bikes[?(@.price < 3000 && @.specs.weight < 10)]"))
                    .collectList()
                    .doOnNext(System.out::println) // >>> [[{"id":"bike:2","model":"Quaoar","description":...
                    .then();
```

##### Java (Synchronous - Jedis)

```java
        Object res35 = jedis.jsonGet(
            "bikes:inventory",
            new Path2("$..mountain_bikes[?(@.price < 3000 && @.specs.weight < 10)]")
        );
        System.out.println(res35);
        // >>> [{"specs":{"material":"aluminium","weight":7.9},"price":2072,...
```

##### JavaScript (Node.js)

```javascript
const res35 = await client.json.get("bikes:inventory", {
  path: "$..mountain_bikes[?(@.price < 3000 && @.specs.weight < 10)]"
});
console.log(res35);
/*
[
  {
    id: 'bike:2',
    model: 'Quaoar',
    description: "Redesigned for the 2020 model year, this bike impressed our testers and is the best all-around trail bike we've ever tested. The Shimano gear system effectively does away with an external cassette, so is super low maintenance in terms of wear and teaawait client. All in all it's an impressive package for the price, making it very competitive.",
    price: 2072,
    specs: { material: 'aluminium', weight: 7.9 },
    colors: [ 'black', 'white' ]
  }
]
*/
```

##### PHP

```php
        $res8b = $r->jsonget(
            'bikes:inventory',
            '',
            '',
            '',
            '$..mountain_bikes[?(@.price < 3000 && @.specs.weight < 10)]'
        );
        echo $res8b . PHP_EOL;
        // >>> [{"id":"bike:2","model":"Quaoar",...}]
```

##### Python

```python
res8 = r.json().get(
    "bikes:inventory",
    "$..mountain_bikes[?(@.price < 3000 && @.specs.weight < 10)]",
)
print(res8)
# >>> [{'id': 'bike:2', 'model': 'Quaoar',
#           'description': "Redesigned for the 2020 model year...
```

##### Ruby

```ruby
res8 = r.json_get(
  'bikes:inventory',
  '$..mountain_bikes[?(@.price < 3000 && @.specs.weight < 10)]'
)
p res8
# >>> [{"id"=>"bike:2", "model"=>"Quaoar",
# >>>   "description"=>"Redesigned for the 2020 model year...
```

##### Rust (Asynchronous)

```rust
        let res36: String = r
            .json_get(
                "bikes:inventory",
                "$..mountain_bikes[?(@.price < 3000 && @.specs.weight < 10)]",
            )
            .await
            .expect("Failed to run JSON.GET");
        println!("{res36}");
        // >>> [[{"id":"bike:2","model":"Quaoar","description":"Redesigned for the 2020 model year...
```

##### Rust (Synchronous)

```rust
        let res36: String = r
            .json_get(
                "bikes:inventory",
                "$..mountain_bikes[?(@.price < 3000 && @.specs.weight < 10)]",
            )
            .expect("Failed to run JSON.GET");
        println!("{res36}");
        // >>> [[{"id":"bike:2","model":"Quaoar","description":"Redesigned for the 2020 model year...
```



Ví dụ này lọc kho hàng để lấy tên model của các xe đạp làm từ hợp kim (alloy):

*Filter so sánh bằng: Dùng toán tử == trong biểu thức filter để chọn các phần tử có giá trị field cụ thể khi bạn cần tìm các mục khớp chính xác tiêu chí*

**Độ khó (Difficulty):** Nâng cao (Advanced)

**Dựa trên (Builds upon):** filter1

**Lệnh (Commands):** JSON.GET

**Độ phức tạp (Complexity):**
- JSON.GET: O(N)

**Có sẵn trong (Available in):** Redis CLI, C#, Go, Java (Asynchronous - Lettuce), Java (Reactive - Lettuce), Java (Synchronous - Jedis), JavaScript (Node.js), PHP, Python, Ruby, Rust (Asynchronous), Rust (Synchronous)

##### Redis CLI

```
> JSON.GET bikes:inventory '$..[?(@.specs.material == "alloy")].model'
"[\"Weywot\",\"Mimas\"]"
```

##### C#

```csharp
        RedisResult res36 = db.JSON().Get(
            "bikes:inventory",
            path: "$..[?(@.specs.material == 'alloy')].model"
        );
        Console.WriteLine(res36);   // >>> ["Weywot","Mimas"]
```

##### Go

```go
	res9, err := rdb.JSONGet(ctx,
		"bikes:inventory",
		"$..[?(@.specs.material == 'alloy')].model",
	).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res9) // >>> ["Mimas","Weywot"]
```

##### Java (Asynchronous - Lettuce)

```java
            CompletableFuture<Void> filter2 = asyncCommands
                    .jsonGet("bikes:inventory", JsonPath.of("$..[?(@.specs.material == 'alloy')].model"))
                    .thenAccept(System.out::println)
                    // >>> [["Weywot","Mimas"]]
                    .toCompletableFuture();
```

##### Java (Reactive - Lettuce)

```java
            Mono<Void> filter2 = reactiveCommands
                    .jsonGet("bikes:inventory", JsonPath.of("$..[?(@.specs.material == 'alloy')].model")).collectList()
                    .doOnNext(System.out::println) // >>> [["Weywot","Mimas"]]
                    .then();
```

##### Java (Synchronous - Jedis)

```java
        Object res36 = jedis.jsonGet(
            "bikes:inventory", new Path2("$..[?(@.specs.material == 'alloy')].model")
        );
        System.out.println(res36);  // >>> ["Weywot","Mimas"]
```

##### JavaScript (Node.js)

```javascript
//  names of bikes made from an alloy
const res36 = await client.json.get("bikes:inventory", {
  path: "$..[?(@.specs.material == 'alloy')].model"
});
console.log(res36); //  ['Weywot', 'Mimas']
```

##### PHP

```php
        $res9b = $r->jsonget('bikes:inventory', '', '', '', "$..[?(@.specs.material == 'alloy')].model");
        echo $res9b . PHP_EOL;
        // >>> ["Weywot","Mimas"]
```

##### Python

```python
res9 = r.json().get("bikes:inventory", "$..[?(@.specs.material == 'alloy')].model")
print(res9)  # >>> ['Weywot', 'Mimas']
```

##### Ruby

```ruby
res9 = r.json_get('bikes:inventory', "$..[?(@.specs.material == 'alloy')].model")
p res9 # >>> ["Weywot", "Mimas"]
```

##### Rust (Asynchronous)

```rust
        let res37: String = r
            .json_get(
                "bikes:inventory",
                "$..[?(@.specs.material == 'alloy')].model",
            )
            .await
            .expect("Failed to run JSON.GET");
        println!("{res37}"); // >>> [["Weywot","Mimas"]]
```

##### Rust (Synchronous)

```rust
        let res37: String = r
            .json_get(
                "bikes:inventory",
                "$..[?(@.specs.material == 'alloy')].model",
            )
            .expect("Failed to run JSON.GET");
        println!("{res37}"); // >>> [["Weywot","Mimas"]]
```



Ví dụ này, hợp lệ từ phiên bản v2.4.2 trở đi, chỉ lọc các xe đạp có chất liệu bắt đầu bằng
"al-" bằng phép so khớp regex. Lưu ý rằng phép khớp này không phân biệt hoa thường nhờ tiền tố `(?i)` trong
pattern regular expression `"(?i)al"`:

*Filter regex: Dùng toán tử =~ với regular expression trong biểu thức filter để khớp pattern trong các giá trị string khi bạn cần lọc linh hoạt dựa trên pattern*

**Độ khó (Difficulty):** Nâng cao (Advanced)

**Dựa trên (Builds upon):** filter2

**Lệnh (Commands):** JSON.GET

**Độ phức tạp (Complexity):**
- JSON.GET: O(N)

**Có sẵn trong (Available in):** Redis CLI, C#, Go, Java (Asynchronous - Lettuce), Java (Reactive - Lettuce), Java (Synchronous - Jedis), JavaScript (Node.js), PHP, Python, Ruby, Rust (Asynchronous), Rust (Synchronous)

##### Redis CLI

```
> JSON.GET bikes:inventory '$..[?(@.specs.material =~ "(?i)al")].model'
"[\"Quaoar\",\"Weywot\",\"Salacia\",\"Mimas\"]"
```

##### C#

```csharp
        RedisResult res37 = db.JSON().Get(
            "bikes:inventory",
            path: "$..[?(@.specs.material =~ '(?i)al')].model"
        );
        Console.WriteLine(res37);   // >>> ["Quaoar","Weywot","Salacia","Mimas"]
```

##### Go

```go
	res10, err := rdb.JSONGet(ctx,
		"bikes:inventory",
		"$..[?(@.specs.material =~ '(?i)al')].model",
	).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res10) // >>> ["Salacia","Mimas","Quaoar","Weywot"]
```

##### Java (Asynchronous - Lettuce)

```java
            CompletableFuture<Void> filter3 = asyncCommands
                    .jsonGet("bikes:inventory", JsonPath.of("$..[?(@.specs.material =~ '(?i)al')].model"))
                    .thenAccept(System.out::println)
                    // >>> [["Quaoar","Weywot","Salacia","Mimas"]]
                    .toCompletableFuture();
```

##### Java (Reactive - Lettuce)

```java
            Mono<Void> filter3 = reactiveCommands
                    .jsonGet("bikes:inventory", JsonPath.of("$..[?(@.specs.material =~ '(?i)al')].model")).collectList()
                    .doOnNext(System.out::println) // >>> [["Quaoar","Weywot","Salacia","Mimas"]]
                    .then();
```

##### Java (Synchronous - Jedis)

```java
        Object res37 = jedis.jsonGet(
            "bikes:inventory", new Path2("$..[?(@.specs.material =~ '(?i)al')].model")
        );
        System.out.println(res37);
        // >>> ["Quaoar","Weywot","Salacia","Mimas"]
```

##### JavaScript (Node.js)

```javascript
const res37 = await client.json.get("bikes:inventory", {
  path: "$..[?(@.specs.material =~ '(?i)al')].model"
});
console.log(res37); //  ['Quaoar', 'Weywot', 'Salacia', 'Mimas']
```

##### PHP

```php
        $res10b = $r->jsonget('bikes:inventory', '', '', '', "$..[?(@.specs.material =~ '(?i)al')].model");
        echo $res10b . PHP_EOL;
        // >>> ["Quaoar","Weywot","Salacia","Mimas"]
```

##### Python

```python
res10 = r.json().get("bikes:inventory", "$..[?(@.specs.material =~ '(?i)al')].model")
print(res10)  # >>> ['Quaoar', 'Weywot', 'Salacia', 'Mimas']
```

##### Ruby

```ruby
res10 = r.json_get('bikes:inventory', "$..[?(@.specs.material =~ '(?i)al')].model")
p res10 # >>> ["Quaoar", "Weywot", "Salacia", "Mimas"]
```

##### Rust (Asynchronous)

```rust
        let res38: String = r
            .json_get(
                "bikes:inventory",
                "$..[?(@.specs.material =~ '(?i)al')].model",
            )
            .await
            .expect("Failed to run JSON.GET");
        println!("{res38}"); // >>> [["Quaoar","Weywot","Salacia","Mimas"]]
```

##### Rust (Synchronous)

```rust
        let res38: String = r
            .json_get(
                "bikes:inventory",
                "$..[?(@.specs.material =~ '(?i)al')].model",
            )
            .expect("Failed to run JSON.GET");
        println!("{res38}"); // >>> [["Quaoar","Weywot","Salacia","Mimas"]]
```



Bạn cũng có thể chỉ định một pattern regex bằng một thuộc tính (property) từ chính JSON object.
Ví dụ, chúng ta có thể thêm một thuộc tính string tên là `regex_pat` vào mỗi xe đạp leo núi,
với giá trị `"(?i)al"` để khớp chất liệu, như trong ví dụ trước. Sau đó chúng ta
có thể so khớp `regex_pat` với chất liệu của xe đạp:

*Filter regex động: Dùng các pattern regex được lưu trong thuộc tính JSON để lọc phần tử khi bạn cần áp dụng các pattern được định nghĩa ngay trong document*

**Độ khó (Difficulty):** Nâng cao (Advanced)

**Dựa trên (Builds upon):** filter3

**Lệnh (Commands):** JSON.SET, JSON.GET

**Độ phức tạp (Complexity):**
- JSON.SET: O(M+N)
- JSON.GET: O(N)

**Có sẵn trong (Available in):** Redis CLI, C#, Go, Java (Asynchronous - Lettuce), Java (Reactive - Lettuce), Java (Synchronous - Jedis), JavaScript (Node.js), PHP, Python, Ruby, Rust (Asynchronous), Rust (Synchronous)

##### Redis CLI

```
> JSON.SET bikes:inventory $.inventory.mountain_bikes[0].regex_pat '"(?i)al"'
OK
> JSON.SET bikes:inventory $.inventory.mountain_bikes[1].regex_pat '"(?i)al"'
OK
> JSON.SET bikes:inventory $.inventory.mountain_bikes[2].regex_pat '"(?i)al"'
OK
> JSON.GET bikes:inventory '$.inventory.mountain_bikes[?(@.specs.material =~ @.regex_pat)].model'
"[\"Quaoar\",\"Weywot\"]"
```

##### C#

```csharp
        bool res38 = db.JSON().Set(
            "bikes:inventory",
            "$.inventory.mountain_bikes[0].regex_pat",
            "\"(?i)al\""
        );
        Console.WriteLine(res38);   // >>> True

        bool res39 = db.JSON().Set(
            "bikes:inventory",
            "$.inventory.mountain_bikes[1].regex_pat",
            "\"(?i)al\""
        );
        Console.WriteLine(res39);   // >>> True

        bool res40 = db.JSON().Set(
            "bikes:inventory",
            "$.inventory.mountain_bikes[2].regex_pat",
            "\"(?i)al\""
        );
        Console.WriteLine(res40);   // >>> True

        RedisResult res41 = db.JSON().Get(
            "bikes:inventory",
            path: "$.inventory.mountain_bikes[?(@.specs.material =~ @.regex_pat)].model"
        );
        Console.WriteLine(res41);   // >>> ["Quaoar","Weywot"]
```

##### Go

```go
	res11, err := rdb.JSONSet(ctx,
		"bikes:inventory",
		"$.inventory.mountain_bikes[0].regex_pat",
		"\"(?i)al\"",
	).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res11) // >>> OK

	res12, err := rdb.JSONSet(ctx,
		"bikes:inventory",
		"$.inventory.mountain_bikes[1].regex_pat",
		"\"(?i)al\"",
	).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res12) // >>> OK

	res13, err := rdb.JSONSet(ctx,
		"bikes:inventory",
		"$.inventory.mountain_bikes[2].regex_pat",
		"\"(?i)al\"",
	).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res13) // >>> OK

	res14, err := rdb.JSONGet(ctx,
		"bikes:inventory",
		"$.inventory.mountain_bikes[?(@.specs.material =~ @.regex_pat)].model",
	).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res14) // >>> ["Quaoar","Weywot"]
```

##### Java (Asynchronous - Lettuce)

```java
            CompletableFuture<Void> filter4 = asyncCommands.jsonSet("bikes:inventory",
                    JsonPath.of("$.inventory.mountain_bikes[0].regex_pat"), parser.createJsonValue("\"(?i)al\""))
                    .thenCompose(r -> {
                        System.out.println(r); // >>> OK

                        return asyncCommands.jsonSet("bikes:inventory", JsonPath.of("$.inventory.mountain_bikes[1].regex_pat"),
                                parser.createJsonValue("\"(?i)al\""));
                    }).thenCompose(r -> {
                        System.out.println(r); // >>> OK

                        return asyncCommands.jsonSet("bikes:inventory", JsonPath.of("$.inventory.mountain_bikes[2].regex_pat"),
                                parser.createJsonValue("\"(?i)al\""));
                    }).thenCompose(res22 -> {
                        System.out.println(res22); // >>> OK

                        return asyncCommands.jsonGet("bikes:inventory",
                                JsonPath.of("$.inventory.mountain_bikes[?(@.specs.material =~ @.regex_pat)].model"));
                    })
                    .thenAccept(System.out::println)
                    // >>> [["Quaoar","Weywot"]]
                    .toCompletableFuture();
```

##### Java (Reactive - Lettuce)

```java
            Mono<Void> filter4 = reactiveCommands.jsonSet("bikes:inventory",
                    JsonPath.of("$.inventory.mountain_bikes[0].regex_pat"), parser.createJsonValue("\"(?i)al\""))
                    .doOnNext(r -> {
                        System.out.println(r); // >>> OK
                    })
                    .flatMap(r -> reactiveCommands.jsonSet("bikes:inventory",
                            JsonPath.of("$.inventory.mountain_bikes[1].regex_pat"), parser.createJsonValue("\"(?i)al\"")))
                    .doOnNext(r -> {
                        System.out.println(r); // >>> OK
                    })
                    .flatMap(r -> reactiveCommands.jsonSet("bikes:inventory",
                            JsonPath.of("$.inventory.mountain_bikes[2].regex_pat"), parser.createJsonValue("\"(?i)al\"")))
                    .doOnNext(res22 -> {
                        System.out.println(res22); // >>> OK
                    })
                    .flatMap(res22 -> reactiveCommands
                            .jsonGet("bikes:inventory",
                                    JsonPath.of("$.inventory.mountain_bikes[?(@.specs.material =~ @.regex_pat)].model"))
                            .collectList())
                    .doOnNext(System.out::println) // >>> [["Quaoar","Weywot"]]
                    .then();
```

##### Java (Synchronous - Jedis)

```java
        jedis.jsonSet(
            "bikes:inventory", new Path2("$.inventory.mountain_bikes[0].regex_pat"),
            "\"(?i)al\""
        );
        jedis.jsonSet(
            "bikes:inventory", new Path2("$.inventory.mountain_bikes[1].regex_pat"),
            "\"(?i)al\""
        );
        jedis.jsonSet(
            "bikes:inventory", new Path2("$.inventory.mountain_bikes[2].regex_pat"),
            "\"(?i)al\""
        );

        Object res38 = jedis.jsonGet(
            "bikes:inventory",
            new Path2("$.inventory.mountain_bikes[?(@.specs.material =~ @.regex_pat)].model")
        );
        System.out.println(res38);  // >>> ["Quaoar","Weywot"]
```

##### JavaScript (Node.js)

```javascript
const res37a = await client.json.set(
  'bikes:inventory',
  '$.inventory.mountain_bikes[0].regex_pat',
  '(?i)al'
);

const res37b = await client.json.set(
  'bikes:inventory',
  '$.inventory.mountain_bikes[1].regex_pat',
  '(?i)al'
);

const res37c = await client.json.set(
  'bikes:inventory',
  '$.inventory.mountain_bikes[2].regex_pat',
  '(?i)al'
);

const res37d = await client.json.get(
  'bikes:inventory',
  { path: '$.inventory.mountain_bikes[?(@.specs.material =~ @.regex_pat)].model' }
);
console.log(res37d); // ['Quaoar', 'Weywot']
```

##### PHP

```php
        $r->jsonset('bikes:inventory', '$.inventory.mountain_bikes[0].regex_pat', '"(?i)al"');
        $r->jsonset('bikes:inventory', '$.inventory.mountain_bikes[1].regex_pat', '"(?i)al"');
        $r->jsonset('bikes:inventory', '$.inventory.mountain_bikes[2].regex_pat', '"(?i)al"');

        $res14b = $r->jsonget(
            'bikes:inventory',
            '',
            '',
            '',
            '$.inventory.mountain_bikes[?(@.specs.material =~ @.regex_pat)].model'
        );
        echo $res14b . PHP_EOL;
        // >>> ["Quaoar","Weywot"]
```

##### Python

```python
res11 = r.json().set(
    "bikes:inventory", "$.inventory.mountain_bikes[0].regex_pat", "(?i)al"
)
res12 = r.json().set(
    "bikes:inventory", "$.inventory.mountain_bikes[1].regex_pat", "(?i)al"
)
res13 = r.json().set(
    "bikes:inventory", "$.inventory.mountain_bikes[2].regex_pat", "(?i)al"
)

res14 = r.json().get(
    "bikes:inventory",
    "$.inventory.mountain_bikes[?(@.specs.material =~ @.regex_pat)].model",
)
print(res14)  # >>> ['Quaoar', 'Weywot']
```

##### Ruby

```ruby
res11 = r.json_set(
  'bikes:inventory', '$.inventory.mountain_bikes[0].regex_pat', '(?i)al'
)
res12 = r.json_set(
  'bikes:inventory', '$.inventory.mountain_bikes[1].regex_pat', '(?i)al'
)
res13 = r.json_set(
  'bikes:inventory', '$.inventory.mountain_bikes[2].regex_pat', '(?i)al'
)

res14 = r.json_get(
  'bikes:inventory',
  '$.inventory.mountain_bikes[?(@.specs.material =~ @.regex_pat)].model'
)
p res14 # >>> ["Quaoar", "Weywot"]
```

##### Rust (Asynchronous)

```rust
        let _: bool = r
            .json_set(
                "bikes:inventory",
                "$.inventory.mountain_bikes[0].regex_pat",
                &json!("(?i)al"),
            )
            .await
            .expect("Failed to run JSON.SET");
        let _: bool = r
            .json_set(
                "bikes:inventory",
                "$.inventory.mountain_bikes[1].regex_pat",
                &json!("(?i)al"),
            )
            .await
            .expect("Failed to run JSON.SET");
        let _: bool = r
            .json_set(
                "bikes:inventory",
                "$.inventory.mountain_bikes[2].regex_pat",
                &json!("(?i)al"),
            )
            .await
            .expect("Failed to run JSON.SET");

        let res39: String = r
            .json_get(
                "bikes:inventory",
                "$.inventory.mountain_bikes[?(@.specs.material =~ @.regex_pat)].model",
            )
            .await
            .expect("Failed to run JSON.GET");
        println!("{res39}"); // >>> [["Quaoar","Weywot"]]
```

##### Rust (Synchronous)

```rust
        let _: bool = r
            .json_set(
                "bikes:inventory",
                "$.inventory.mountain_bikes[0].regex_pat",
                &json!("(?i)al"),
            )
            .expect("Failed to run JSON.SET");
        let _: bool = r
            .json_set(
                "bikes:inventory",
                "$.inventory.mountain_bikes[1].regex_pat",
                &json!("(?i)al"),
            )
            .expect("Failed to run JSON.SET");
        let _: bool = r
            .json_set(
                "bikes:inventory",
                "$.inventory.mountain_bikes[2].regex_pat",
                &json!("(?i)al"),
            )
            .expect("Failed to run JSON.SET");

        let res39: String = r
            .json_get(
                "bikes:inventory",
                "$.inventory.mountain_bikes[?(@.specs.material =~ @.regex_pat)].model",
            )
            .expect("Failed to run JSON.GET");
        println!("{res39}"); // >>> [["Quaoar","Weywot"]]
```



Bắt đầu từ Redis 8.10, cú pháp filter JSONPath hỗ trợ các toán tử sau.

<a name="negation-"></a>

#### Phủ định `!` (Negation `!`)

Toán tử `!` phủ định một điều kiện filter. Nó có độ ưu tiên cao hơn các toán tử logic `&&` và `||`, nên hãy dùng dấu ngoặc đơn để phủ định một điều kiện phức hợp. Phủ định kép (`!!`) tương đương với một phép kiểm tra sự tồn tại.

*Phủ định filter: Dùng toán tử ! để đảo ngược một điều kiện filter khi bạn cần chọn các phần tử không khớp*

**Độ khó (Difficulty):** Nâng cao (Advanced)

**Lệnh (Commands):** JSON.SET, JSON.GET

**Độ phức tạp (Complexity):**
- JSON.SET: O(M+N)
- JSON.GET: O(N)

**Có sẵn trong (Available in):** Redis CLI, C#, Go, Java (Asynchronous - Lettuce), Java (Reactive - Lettuce), Java (Synchronous - Jedis), JavaScript (Node.js), PHP, Python, Ruby, Rust (Asynchronous), Rust (Synchronous)

##### Redis CLI

```
> JSON.SET doc $ '[{"a":1,"b":1},{"b":2},{"a":1},{"c":3}]'
OK
> JSON.GET doc '$[?!@.a]'
"[{\"b\":2},{\"c\":3}]"
> JSON.GET doc '$[?!(@.a==1)]'
"[{\"b\":2},{\"c\":3}]"
> JSON.GET doc '$[?!@.a && @.b]'
"[{\"b\":2}]"
```

##### C#

```csharp
        bool res1 = db.JSON().Set("doc", "$", "[{\"a\":1,\"b\":1},{\"b\":2},{\"a\":1},{\"c\":3}]");
        Console.WriteLine(res1);    // >>> True

        RedisResult res2 = db.JSON().Get("doc", path: "$[?!@.a]");
        Console.WriteLine(res2);    // >>> [{"b":2},{"c":3}]

        RedisResult res3 = db.JSON().Get("doc", path: "$[?!(@.a==1)]");
        Console.WriteLine(res3);    // >>> [{"b":2},{"c":3}]

        RedisResult res4 = db.JSON().Get("doc", path: "$[?!@.a && @.b]");
        Console.WriteLine(res4);    // >>> [{"b":2}]
```

##### Go

```go
	res1, err := rdb.JSONSet(ctx, "doc", "$",
		`[{"a":1,"b":1},{"b":2},{"a":1},{"c":3}]`,
	).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res1) // >>> OK

	res2, err := rdb.JSONGet(ctx, "doc", `$[?!@.a]`).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res2) // >>> [{"b":2},{"c":3}]

	res3, err := rdb.JSONGet(ctx, "doc", `$[?!(@.a==1)]`).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res3) // >>> [{"b":2},{"c":3}]

	res4, err := rdb.JSONGet(ctx, "doc", `$[?!@.a && @.b]`).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res4) // >>> [{"b":2}]
```

##### Java (Asynchronous - Lettuce)

```java
            CompletableFuture<Void> filterNegationExample = asyncCommands
                    .jsonSet("doc", JsonPath.ROOT_PATH,
                            parser.createJsonValue("[{\"a\":1,\"b\":1},{\"b\":2},{\"a\":1},{\"c\":3}]"))
                    .thenCompose(res1 -> {
                        System.out.println(res1); // >>> OK
                        return asyncCommands.jsonGet("doc", JsonPath.of("$[?!@.a]"));
                    }).thenCompose(res2 -> {
                        System.out.println(res2); // >>> [[{"b":2},{"c":3}]]
                        return asyncCommands.jsonGet("doc", JsonPath.of("$[?!(@.a==1)]"));
                    }).thenCompose(res3 -> {
                        System.out.println(res3); // >>> [[{"b":2},{"c":3}]]
                        return asyncCommands.jsonGet("doc", JsonPath.of("$[?!@.a && @.b]"));
                    }).thenAccept(res4 -> {
                        System.out.println(res4); // >>> [[{"b":2}]]
                    }).toCompletableFuture();
```

##### Java (Reactive - Lettuce)

```java
            Mono<Void> filterNegationExample = reactiveCommands
                    .jsonSet("doc", JsonPath.ROOT_PATH,
                            parser.createJsonValue("[{\"a\":1,\"b\":1},{\"b\":2},{\"a\":1},{\"c\":3}]"))
                    .doOnNext(res1 -> {
                        System.out.println(res1); // >>> OK
                    })
                    .flatMap(res1 -> reactiveCommands.jsonGet("doc", JsonPath.of("$[?!@.a]")).collectList())
                    .doOnNext(res2 -> {
                        System.out.println(res2); // >>> [[{"b":2},{"c":3}]]
                    })
                    .flatMap(res2 -> reactiveCommands.jsonGet("doc", JsonPath.of("$[?!(@.a==1)]")).collectList())
                    .doOnNext(res3 -> {
                        System.out.println(res3); // >>> [[{"b":2},{"c":3}]]
                    })
                    .flatMap(res3 -> reactiveCommands.jsonGet("doc", JsonPath.of("$[?!@.a && @.b]")).collectList())
                    .doOnNext(res4 -> {
                        System.out.println(res4); // >>> [[{"b":2}]]
                    })
                    .then();
```

##### Java (Synchronous - Jedis)

```java
        String res1 = jedis.jsonSet("doc", new Path2("$"), "[{\"a\":1,\"b\":1},{\"b\":2},{\"a\":1},{\"c\":3}]");
        System.out.println(res1);    // >>> OK

        Object res2 = jedis.jsonGet("doc", new Path2("$[?!@.a]"));
        System.out.println(res2);    // >>> [{"b":2},{"c":3}]

        Object res3 = jedis.jsonGet("doc", new Path2("$[?!(@.a==1)]"));
        System.out.println(res3);    // >>> [{"b":2},{"c":3}]

        Object res4 = jedis.jsonGet("doc", new Path2("$[?!@.a && @.b]"));
        System.out.println(res4);    // >>> [{"b":2}]
```

##### JavaScript (Node.js)

```javascript
const res1 = await client.json.set('doc', '$', [{ a: 1, b: 1 }, { b: 2 }, { a: 1 }, { c: 3 }]);
console.log(res1); // >>> OK

const res2 = await client.json.get('doc', { path: '$[?!@.a]' });
console.log(res2); // >>> [ { b: 2 }, { c: 3 } ]

const res3 = await client.json.get('doc', { path: '$[?!(@.a==1)]' });
console.log(res3); // >>> [ { b: 2 }, { c: 3 } ]

const res4 = await client.json.get('doc', { path: '$[?!@.a && @.b]' });
console.log(res4); // >>> [ { b: 2 } ]
```

##### PHP

```php
        $res1 = $this->redis->jsonset('doc', '$', json_encode(
            [["a" => 1, "b" => 1], ["b" => 2], ["a" => 1], ["c" => 3]]
        ));
        echo $res1 . PHP_EOL; // >>> OK

        $res2 = $this->redis->jsonget('doc', '', '', '', '$[?!@.a]');
        echo $res2 . PHP_EOL; // >>> [{"b":2},{"c":3}]

        $res3 = $this->redis->jsonget('doc', '', '', '', '$[?!(@.a==1)]');
        echo $res3 . PHP_EOL; // >>> [{"b":2},{"c":3}]

        $res4 = $this->redis->jsonget('doc', '', '', '', '$[?!@.a && @.b]');
        echo $res4 . PHP_EOL; // >>> [{"b":2}]
```

##### Python

```python
res1 = r.json().set("doc", "$", [{"a": 1, "b": 1}, {"b": 2}, {"a": 1}, {"c": 3}])
print(res1)
# >>> True

res2 = r.json().get("doc", "$[?!@.a]")
print(res2)
# >>> [{'b': 2}, {'c': 3}]

res3 = r.json().get("doc", "$[?!(@.a==1)]")
print(res3)
# >>> [{'b': 2}, {'c': 3}]

res4 = r.json().get("doc", "$[?!@.a && @.b]")
print(res4)
# >>> [{'b': 2}]
```

##### Ruby

```ruby
res1 = r.json_set('doc', '$', [{ 'a' => 1, 'b' => 1 }, { 'b' => 2 }, { 'a' => 1 }, { 'c' => 3 }])
puts res1 # >>> OK

res2 = r.json_get('doc', '$[?!@.a]')
p res2 # >>> [{"b"=>2}, {"c"=>3}]

res3 = r.json_get('doc', '$[?!(@.a==1)]')
p res3 # >>> [{"b"=>2}, {"c"=>3}]

res4 = r.json_get('doc', '$[?!@.a && @.b]')
p res4 # >>> [{"b"=>2}]
```

##### Rust (Asynchronous)

```rust
        let _: bool = match r.json_set("doc", "$", &json!([{"a":1,"b":1},{"b":2},{"a":1},{"c":3}])).await {
            Ok(v) => v,
            Err(e) => {
                println!("Error setting doc: {e}");
                return;
            }
        };

        match r.json_get("doc", "$[?!@.a]").await {
            Ok(res1) => {
                let res1: String = res1;
                println!("{res1}");    // >>> [{"b":2},{"c":3}]
            },
            Err(e) => {
                println!("Error getting doc: {e}");
                return;
            }
        }

        match r.json_get("doc", "$[?!(@.a==1)]").await {
            Ok(res2) => {
                let res2: String = res2;
                println!("{res2}");    // >>> [{"b":2},{"c":3}]
            },
            Err(e) => {
                println!("Error getting doc: {e}");
                return;
            }
        }

        match r.json_get("doc", "$[?!@.a && @.b]").await {
            Ok(res3) => {
                let res3: String = res3;
                println!("{res3}");    // >>> [{"b":2}]
            },
            Err(e) => {
                println!("Error getting doc: {e}");
                return;
            }
        }

```

##### Rust (Synchronous)

```rust
        match r.json_set("doc", "$", &json!([{"a":1,"b":1},{"b":2},{"a":1},{"c":3}])) {
            Ok(res1) => {
                let res1: bool = res1;
                println!("{}", if res1 { "OK" } else { "(nil)" });    // >>> OK
            },
            Err(e) => {
                println!("Error setting doc: {e}");
                return;
            }
        }

        match r.json_get("doc", "$[?!@.a]") {
            Ok(res2) => {
                let res2: String = res2;
                println!("{res2}");    // >>> [{"b":2},{"c":3}]
            },
            Err(e) => {
                println!("Error getting doc: {e}");
                return;
            }
        }

        match r.json_get("doc", "$[?!(@.a==1)]") {
            Ok(res3) => {
                let res3: String = res3;
                println!("{res3}");    // >>> [{"b":2},{"c":3}]
            },
            Err(e) => {
                println!("Error getting doc: {e}");
                return;
            }
        }

        match r.json_get("doc", "$[?!@.a && @.b]") {
            Ok(res4) => {
                let res4: String = res4;
                println!("{res4}");    // >>> [{"b":2}]
            },
            Err(e) => {
                println!("Error getting doc: {e}");
                return;
            }
        }

```



<a name="comparing-array-and-object-literals"></a>

#### So sánh literal mảng và object (Comparing array and object literals)

Các toán tử `==` và `!=` có thể so sánh bất kỳ JSON literal nào, kể cả literal mảng và object, ở cả hai vế của toán tử. Phép so sánh dựa trên bằng nhau về cấu trúc (so sánh sâu - deep equality). Phép so sánh giữa các giá trị khác kiểu luôn là false.

*So sánh literal: Dùng == và != để so sánh literal mảng và object theo bằng nhau về cấu trúc khi bạn cần khớp toàn bộ một giá trị lồng nhau*

**Độ khó (Difficulty):** Nâng cao (Advanced)

**Lệnh (Commands):** JSON.SET, JSON.GET

**Độ phức tạp (Complexity):**
- JSON.SET: O(M+N)
- JSON.GET: O(N)

**Có sẵn trong (Available in):** Redis CLI, C#, Go, Java (Asynchronous - Lettuce), Java (Reactive - Lettuce), Java (Synchronous - Jedis), JavaScript (Node.js), PHP, Python, Ruby, Rust (Asynchronous), Rust (Synchronous)

##### Redis CLI

```
> JSON.SET doc $ '{"arrs":[[1],[2],[1,2],[1,[2]]],"objs":[{"x":1},{"x":2},{"y":1}]}'
OK
> JSON.GET doc '$.arrs[?(@ == [1])]'
"[[1]]"
> JSON.GET doc '$.arrs[?(@ == [1,[2]])]'
"[[1,[2]]]"
> JSON.GET doc '$.objs[?(@ == {"x":1})]'
"[{\"x\":1}]"
```

##### C#

```csharp
        bool res5 = db.JSON().Set(
            "doc",
            "$",
            "{\"arrs\":[[1],[2],[1,2],[1,[2]]],\"objs\":[{\"x\":1},{\"x\":2},{\"y\":1}]}"
        );
        Console.WriteLine(res5);    // >>> True

        RedisResult res6 = db.JSON().Get("doc", path: "$.arrs[?(@ == [1])]");
        Console.WriteLine(res6);    // >>> [[1]]

        RedisResult res7 = db.JSON().Get("doc", path: "$.arrs[?(@ == [1,[2]])]");
        Console.WriteLine(res7);    // >>> [[1,[2]]]

        RedisResult res8 = db.JSON().Get("doc", path: "$.objs[?(@ == {\"x\":1})]");
        Console.WriteLine(res8);    // >>> [{"x":1}]
```

##### Go

```go
	res1, err := rdb.JSONSet(ctx, "doc", "$",
		`{"arrs":[[1],[2],[1,2],[1,[2]]],"objs":[{"x":1},{"x":2},{"y":1}]}`,
	).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res1) // >>> OK

	res2, err := rdb.JSONGet(ctx, "doc", `$.arrs[?(@ == [1])]`).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res2) // >>> [[1]]

	res3, err := rdb.JSONGet(ctx, "doc", `$.arrs[?(@ == [1,[2]])]`).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res3) // >>> [[1,[2]]]

	res4, err := rdb.JSONGet(ctx, "doc", `$.objs[?(@ == {"x":1})]`).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res4) // >>> [{"x":1}]
```

##### Java (Asynchronous - Lettuce)

```java
            CompletableFuture<Void> filterLiteralEqExample = asyncCommands
                    .jsonSet("doc", JsonPath.ROOT_PATH,
                            parser.createJsonValue(
                                    "{\"arrs\":[[1],[2],[1,2],[1,[2]]],\"objs\":[{\"x\":1},{\"x\":2},{\"y\":1}]}"))
                    .thenCompose(res1 -> {
                        System.out.println(res1); // >>> OK
                        return asyncCommands.jsonGet("doc", JsonPath.of("$.arrs[?(@ == [1])]"));
                    }).thenCompose(res2 -> {
                        System.out.println(res2); // >>> [[[1]]]
                        return asyncCommands.jsonGet("doc", JsonPath.of("$.arrs[?(@ == [1,[2]])]"));
                    }).thenCompose(res3 -> {
                        System.out.println(res3); // >>> [[[1,[2]]]]
                        return asyncCommands.jsonGet("doc", JsonPath.of("$.objs[?(@ == {\"x\":1})]"));
                    }).thenAccept(res4 -> {
                        System.out.println(res4); // >>> [[{"x":1}]]
                    }).toCompletableFuture();
```

##### Java (Reactive - Lettuce)

```java
            Mono<Void> filterLiteralEqExample = reactiveCommands
                    .jsonSet("doc", JsonPath.ROOT_PATH,
                            parser.createJsonValue(
                                    "{\"arrs\":[[1],[2],[1,2],[1,[2]]],\"objs\":[{\"x\":1},{\"x\":2},{\"y\":1}]}"))
                    .doOnNext(res1 -> {
                        System.out.println(res1); // >>> OK
                    })
                    .flatMap(res1 -> reactiveCommands.jsonGet("doc", JsonPath.of("$.arrs[?(@ == [1])]")).collectList())
                    .doOnNext(res2 -> {
                        System.out.println(res2); // >>> [[[1]]]
                    })
                    .flatMap(res2 -> reactiveCommands.jsonGet("doc", JsonPath.of("$.arrs[?(@ == [1,[2]])]")).collectList())
                    .doOnNext(res3 -> {
                        System.out.println(res3); // >>> [[[1,[2]]]]
                    })
                    .flatMap(res3 -> reactiveCommands.jsonGet("doc", JsonPath.of("$.objs[?(@ == {\"x\":1})]")).collectList())
                    .doOnNext(res4 -> {
                        System.out.println(res4); // >>> [[{"x":1}]]
                    })
                    .then();
```

##### Java (Synchronous - Jedis)

```java
        String res5 = jedis.jsonSet(
            "doc",
            new Path2("$"),
            "{\"arrs\":[[1],[2],[1,2],[1,[2]]],\"objs\":[{\"x\":1},{\"x\":2},{\"y\":1}]}"
        );
        System.out.println(res5);    // >>> OK

        Object res6 = jedis.jsonGet("doc", new Path2("$.arrs[?(@ == [1])]"));
        System.out.println(res6);    // >>> [[1]]

        Object res7 = jedis.jsonGet("doc", new Path2("$.arrs[?(@ == [1,[2]])]"));
        System.out.println(res7);    // >>> [[1,[2]]]

        Object res8 = jedis.jsonGet("doc", new Path2("$.objs[?(@ == {\"x\":1})]"));
        System.out.println(res8);    // >>> [{"x":1}]
```

##### JavaScript (Node.js)

```javascript
const res5 = await client.json.set('doc', '$', {
  arrs: [[1], [2], [1, 2], [1, [2]]],
  objs: [{ x: 1 }, { x: 2 }, { y: 1 }]
});
console.log(res5); // >>> OK

const res6 = await client.json.get('doc', { path: '$.arrs[?(@ == [1])]' });
console.log(res6); // >>> [ [ 1 ] ]

const res7 = await client.json.get('doc', { path: '$.arrs[?(@ == [1,[2]])]' });
console.log(res7); // >>> [ [ 1, [ 2 ] ] ]

const res8 = await client.json.get('doc', { path: '$.objs[?(@ == {"x":1})]' });
console.log(res8); // >>> [ { x: 1 } ]
```

##### PHP

```php
        $res5 = $this->redis->jsonset('doc', '$', json_encode([
            "arrs" => [[1], [2], [1, 2], [1, [2]]],
            "objs" => [["x" => 1], ["x" => 2], ["y" => 1]],
        ]));
        echo $res5 . PHP_EOL; // >>> OK

        $res6 = $this->redis->jsonget('doc', '', '', '', '$.arrs[?(@ == [1])]');
        echo $res6 . PHP_EOL; // >>> [[1]]

        $res7 = $this->redis->jsonget('doc', '', '', '', '$.arrs[?(@ == [1,[2]])]');
        echo $res7 . PHP_EOL; // >>> [[1,[2]]]

        $res8 = $this->redis->jsonget('doc', '', '', '', '$.objs[?(@ == {"x":1})]');
        echo $res8 . PHP_EOL; // >>> [{"x":1}]
```

##### Python

```python
res1 = r.json().set(
    "doc",
    "$",
    {"arrs": [[1], [2], [1, 2], [1, [2]]], "objs": [{"x": 1}, {"x": 2}, {"y": 1}]},
)
print(res1)
# >>> True

res2 = r.json().get("doc", "$.arrs[?(@ == [1])]")
print(res2)
# >>> [[1]]

res3 = r.json().get("doc", "$.arrs[?(@ == [1,[2]])]")
print(res3)
# >>> [[1, [2]]]

res4 = r.json().get("doc", '$.objs[?(@ == {"x":1})]')
print(res4)
# >>> [{'x': 1}]
```

##### Ruby

```ruby
res1 = r.json_set(
  'doc', '$',
  { 'arrs' => [[1], [2], [1, 2], [1, [2]]], 'objs' => [{ 'x' => 1 }, { 'x' => 2 }, { 'y' => 1 }] }
)
puts res1 # >>> OK

res2 = r.json_get('doc', '$.arrs[?(@ == [1])]')
p res2 # >>> [[1]]

res3 = r.json_get('doc', '$.arrs[?(@ == [1,[2]])]')
p res3 # >>> [[1, [2]]]

res4 = r.json_get('doc', '$.objs[?(@ == {"x":1})]')
p res4 # >>> [{"x"=>1}]
```

##### Rust (Asynchronous)

```rust
        let _: bool = match r.json_set(
            "doc",
            "$",
            &json!({"arrs":[[1],[2],[1,2],[1,[2]]],"objs":[{"x":1},{"x":2},{"y":1}]}),
        ).await {
            Ok(v) => v,
            Err(e) => {
                println!("Error setting doc: {e}");
                return;
            }
        };

        match r.json_get("doc", "$.arrs[?(@ == [1])]").await {
            Ok(res4) => {
                let res4: String = res4;
                println!("{res4}");    // >>> [[1]]
            },
            Err(e) => {
                println!("Error getting doc: {e}");
                return;
            }
        }

        match r.json_get("doc", "$.arrs[?(@ == [1,[2]])]").await {
            Ok(res5) => {
                let res5: String = res5;
                println!("{res5}");    // >>> [[1,[2]]]
            },
            Err(e) => {
                println!("Error getting doc: {e}");
                return;
            }
        }

        match r.json_get("doc", r#"$.objs[?(@ == {"x":1})]"#).await {
            Ok(res6) => {
                let res6: String = res6;
                println!("{res6}");    // >>> [{"x":1}]
            },
            Err(e) => {
                println!("Error getting doc: {e}");
                return;
            }
        }

```

##### Rust (Synchronous)

```rust
        match r.json_set(
            "doc",
            "$",
            &json!({"arrs":[[1],[2],[1,2],[1,[2]]],"objs":[{"x":1},{"x":2},{"y":1}]}),
        ) {
            Ok(res5) => {
                let res5: bool = res5;
                println!("{}", if res5 { "OK" } else { "(nil)" });    // >>> OK
            },
            Err(e) => {
                println!("Error setting doc: {e}");
                return;
            }
        }

        match r.json_get("doc", "$.arrs[?(@ == [1])]") {
            Ok(res6) => {
                let res6: String = res6;
                println!("{res6}");    // >>> [[1]]
            },
            Err(e) => {
                println!("Error getting doc: {e}");
                return;
            }
        }

        match r.json_get("doc", "$.arrs[?(@ == [1,[2]])]") {
            Ok(res7) => {
                let res7: String = res7;
                println!("{res7}");    // >>> [[1,[2]]]
            },
            Err(e) => {
                println!("Error getting doc: {e}");
                return;
            }
        }

        match r.json_get("doc", r#"$.objs[?(@ == {"x":1})]"#) {
            Ok(res8) => {
                let res8: String = res8;
                println!("{res8}");    // >>> [{"x":1}]
            },
            Err(e) => {
                println!("Error getting doc: {e}");
                return;
            }
        }

```



<a name="arithmetic-operators"></a>

#### Toán tử số học (Arithmetic operators)

Các toán tử hai ngôi `+`, `-`, `*`, `/`, và `%` cùng các toán tử một ngôi `-` và `+` thao tác trên số. Độ ưu tiên, từ cao nhất đến thấp nhất, là: một ngôi `-`/`+`, rồi `*`/`/`/`%`, rồi `+`/`-`. Dùng dấu ngoặc đơn để thay đổi độ ưu tiên. Phép chia luôn cho ra kết quả số thực dấu phẩy động. Phép chia hoặc chia lấy dư cho 0 cho ra *Nothing*, nên không có phần tử nào khớp.

Hãy nhớ rằng các toán tử số học phải được bao quanh bởi khoảng trắng (xem [Ngữ nghĩa đánh giá](#evaluation-semantics)).

*Số học trong filter: Dùng +, -, *, / và % bên trong một điều kiện filter khi bạn cần tính một giá trị trước khi so sánh nó*

**Độ khó (Difficulty):** Nâng cao (Advanced)

**Lệnh (Commands):** JSON.SET, JSON.GET

**Độ phức tạp (Complexity):**
- JSON.SET: O(M+N)
- JSON.GET: O(N)

**Có sẵn trong (Available in):** Redis CLI, C#, Go, Java (Asynchronous - Lettuce), Java (Reactive - Lettuce), Java (Synchronous - Jedis), JavaScript (Node.js), PHP, Python, Ruby, Rust (Asynchronous), Rust (Synchronous)

##### Redis CLI

```
> JSON.SET doc $ '[{"a":2,"b":3},{"a":5,"b":2}]'
OK
> JSON.GET doc '$[?@.a + 1 == 3]'
"[{\"a\":2,\"b\":3}]"
> JSON.GET doc '$[?@.a + @.b * 2 == 8]'
"[{\"a\":2,\"b\":3}]"
> JSON.GET doc '$[?(@.a + @.b) * 2 == 10]'
"[{\"a\":2,\"b\":3}]"
```

##### C#

```csharp
        bool res9 = db.JSON().Set("doc", "$", "[{\"a\":2,\"b\":3},{\"a\":5,\"b\":2}]");
        Console.WriteLine(res9);    // >>> True

        RedisResult res10 = db.JSON().Get("doc", path: "$[?@.a + 1 == 3]");
        Console.WriteLine(res10);   // >>> [{"a":2,"b":3}]

        RedisResult res11 = db.JSON().Get("doc", path: "$[?@.a + @.b * 2 == 8]");
        Console.WriteLine(res11);   // >>> [{"a":2,"b":3}]

        RedisResult res12 = db.JSON().Get("doc", path: "$[?(@.a + @.b) * 2 == 10]");
        Console.WriteLine(res12);   // >>> [{"a":2,"b":3}]
```

##### Go

```go
	res1, err := rdb.JSONSet(ctx, "doc", "$",
		`[{"a":2,"b":3},{"a":5,"b":2}]`,
	).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res1) // >>> OK

	res2, err := rdb.JSONGet(ctx, "doc", `$[?@.a + 1 == 3]`).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res2) // >>> [{"a":2,"b":3}]

	res3, err := rdb.JSONGet(ctx, "doc", `$[?@.a + @.b * 2 == 8]`).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res3) // >>> [{"a":2,"b":3}]

	res4, err := rdb.JSONGet(ctx, "doc", `$[?(@.a + @.b) * 2 == 10]`).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res4) // >>> [{"a":2,"b":3}]
```

##### Java (Asynchronous - Lettuce)

```java
            CompletableFuture<Void> filterArithmeticExample = asyncCommands
                    .jsonSet("doc", JsonPath.ROOT_PATH,
                            parser.createJsonValue("[{\"a\":2,\"b\":3},{\"a\":5,\"b\":2}]"))
                    .thenCompose(res1 -> {
                        System.out.println(res1); // >>> OK
                        return asyncCommands.jsonGet("doc", JsonPath.of("$[?@.a + 1 == 3]"));
                    }).thenCompose(res2 -> {
                        System.out.println(res2); // >>> [[{"a":2,"b":3}]]
                        return asyncCommands.jsonGet("doc", JsonPath.of("$[?@.a + @.b * 2 == 8]"));
                    }).thenCompose(res3 -> {
                        System.out.println(res3); // >>> [[{"a":2,"b":3}]]
                        return asyncCommands.jsonGet("doc", JsonPath.of("$[?(@.a + @.b) * 2 == 10]"));
                    }).thenAccept(res4 -> {
                        System.out.println(res4); // >>> [[{"a":2,"b":3}]]
                    }).toCompletableFuture();
```

##### Java (Reactive - Lettuce)

```java
            Mono<Void> filterArithmeticExample = reactiveCommands
                    .jsonSet("doc", JsonPath.ROOT_PATH, parser.createJsonValue("[{\"a\":2,\"b\":3},{\"a\":5,\"b\":2}]"))
                    .doOnNext(res1 -> {
                        System.out.println(res1); // >>> OK
                    })
                    .flatMap(res1 -> reactiveCommands.jsonGet("doc", JsonPath.of("$[?@.a + 1 == 3]")).collectList())
                    .doOnNext(res2 -> {
                        System.out.println(res2); // >>> [[{"a":2,"b":3}]]
                    })
                    .flatMap(res2 -> reactiveCommands.jsonGet("doc", JsonPath.of("$[?@.a + @.b * 2 == 8]")).collectList())
                    .doOnNext(res3 -> {
                        System.out.println(res3); // >>> [[{"a":2,"b":3}]]
                    })
                    .flatMap(res3 -> reactiveCommands.jsonGet("doc", JsonPath.of("$[?(@.a + @.b) * 2 == 10]")).collectList())
                    .doOnNext(res4 -> {
                        System.out.println(res4); // >>> [[{"a":2,"b":3}]]
                    })
                    .then();
```

##### Java (Synchronous - Jedis)

```java
        String res9 = jedis.jsonSet("doc", new Path2("$"), "[{\"a\":2,\"b\":3},{\"a\":5,\"b\":2}]");
        System.out.println(res9);    // >>> OK

        Object res10 = jedis.jsonGet("doc", new Path2("$[?@.a + 1 == 3]"));
        System.out.println(res10);    // >>> [{"a":2,"b":3}]

        Object res11 = jedis.jsonGet("doc", new Path2("$[?@.a + @.b * 2 == 8]"));
        System.out.println(res11);    // >>> [{"a":2,"b":3}]

        Object res12 = jedis.jsonGet("doc", new Path2("$[?(@.a + @.b) * 2 == 10]"));
        System.out.println(res12);    // >>> [{"a":2,"b":3}]
```

##### JavaScript (Node.js)

```javascript
const res9 = await client.json.set('doc', '$', [{ a: 2, b: 3 }, { a: 5, b: 2 }]);
console.log(res9); // >>> OK

const res10 = await client.json.get('doc', { path: '$[?@.a + 1 == 3]' });
console.log(res10); // >>> [ { a: 2, b: 3 } ]

const res11 = await client.json.get('doc', { path: '$[?@.a + @.b * 2 == 8]' });
console.log(res11); // >>> [ { a: 2, b: 3 } ]

const res12 = await client.json.get('doc', { path: '$[?(@.a + @.b) * 2 == 10]' });
console.log(res12); // >>> [ { a: 2, b: 3 } ]
```

##### PHP

```php
        $res9 = $this->redis->jsonset('doc', '$', json_encode(
            [["a" => 2, "b" => 3], ["a" => 5, "b" => 2]]
        ));
        echo $res9 . PHP_EOL; // >>> OK

        $res10 = $this->redis->jsonget('doc', '', '', '', '$[?@.a + 1 == 3]');
        echo $res10 . PHP_EOL; // >>> [{"a":2,"b":3}]

        $res11 = $this->redis->jsonget('doc', '', '', '', '$[?@.a + @.b * 2 == 8]');
        echo $res11 . PHP_EOL; // >>> [{"a":2,"b":3}]

        $res12 = $this->redis->jsonget('doc', '', '', '', '$[?(@.a + @.b) * 2 == 10]');
        echo $res12 . PHP_EOL; // >>> [{"a":2,"b":3}]
```

##### Python

```python
res1 = r.json().set("doc", "$", [{"a": 2, "b": 3}, {"a": 5, "b": 2}])
print(res1)
# >>> True

res2 = r.json().get("doc", "$[?@.a + 1 == 3]")
print(res2)
# >>> [{'a': 2, 'b': 3}]

res3 = r.json().get("doc", "$[?@.a + @.b * 2 == 8]")
print(res3)
# >>> [{'a': 2, 'b': 3}]

res4 = r.json().get("doc", "$[?(@.a + @.b) * 2 == 10]")
print(res4)
# >>> [{'a': 2, 'b': 3}]
```

##### Ruby

```ruby
res1 = r.json_set('doc', '$', [{ 'a' => 2, 'b' => 3 }, { 'a' => 5, 'b' => 2 }])
puts res1 # >>> OK

res2 = r.json_get('doc', '$[?@.a + 1 == 3]')
p res2 # >>> [{"a"=>2, "b"=>3}]

res3 = r.json_get('doc', '$[?@.a + @.b * 2 == 8]')
p res3 # >>> [{"a"=>2, "b"=>3}]

res4 = r.json_get('doc', '$[?(@.a + @.b) * 2 == 10]')
p res4 # >>> [{"a"=>2, "b"=>3}]
```

##### Rust (Asynchronous)

```rust
        let _: bool = match r.json_set("doc", "$", &json!([{"a":2,"b":3},{"a":5,"b":2}])).await {
            Ok(v) => v,
            Err(e) => {
                println!("Error setting doc: {e}");
                return;
            }
        };

        match r.json_get("doc", "$[?@.a + 1 == 3]").await {
            Ok(res7) => {
                let res7: String = res7;
                println!("{res7}");    // >>> [{"a":2,"b":3}]
            },
            Err(e) => {
                println!("Error getting doc: {e}");
                return;
            }
        }

        match r.json_get("doc", "$[?@.a + @.b * 2 == 8]").await {
            Ok(res8) => {
                let res8: String = res8;
                println!("{res8}");    // >>> [{"a":2,"b":3}]
            },
            Err(e) => {
                println!("Error getting doc: {e}");
                return;
            }
        }

        match r.json_get("doc", "$[?(@.a + @.b) * 2 == 10]").await {
            Ok(res9) => {
                let res9: String = res9;
                println!("{res9}");    // >>> [{"a":2,"b":3}]
            },
            Err(e) => {
                println!("Error getting doc: {e}");
                return;
            }
        }

```

##### Rust (Synchronous)

```rust
        match r.json_set("doc", "$", &json!([{"a":2,"b":3},{"a":5,"b":2}])) {
            Ok(res9) => {
                let res9: bool = res9;
                println!("{}", if res9 { "OK" } else { "(nil)" });    // >>> OK
            },
            Err(e) => {
                println!("Error setting doc: {e}");
                return;
            }
        }

        match r.json_get("doc", "$[?@.a + 1 == 3]") {
            Ok(res10) => {
                let res10: String = res10;
                println!("{res10}");    // >>> [{"a":2,"b":3}]
            },
            Err(e) => {
                println!("Error getting doc: {e}");
                return;
            }
        }

        match r.json_get("doc", "$[?@.a + @.b * 2 == 8]") {
            Ok(res11) => {
                let res11: String = res11;
                println!("{res11}");    // >>> [{"a":2,"b":3}]
            },
            Err(e) => {
                println!("Error getting doc: {e}");
                return;
            }
        }

        match r.json_get("doc", "$[?(@.a + @.b) * 2 == 10]") {
            Ok(res12) => {
                let res12: String = res12;
                println!("{res12}");    // >>> [{"a":2,"b":3}]
            },
            Err(e) => {
                println!("Error getting doc: {e}");
                return;
            }
        }

```



<a name="membership-in-and-nin"></a>

#### Thành viên: `in` và `nin` (Membership: `in` and `nin`)

`value in array` khớp khi `value` là một thành viên của `array`; `value nin array` là phủ định chặt (strict negation) của nó. Vế phải có thể là một literal mảng hoặc một path tới một mảng. Phép kiểm tra thành viên dùng cùng cách so sánh cấu trúc và ép kiểu số như `==`. Nếu vế phải không phải là mảng, `in` là false (nên `nin` là true).

*Filter thành viên: Dùng in và nin để kiểm tra một giá trị có là thành viên của một mảng hay không khi bạn cần so khớp kiểu tập hợp*

**Độ khó (Difficulty):** Nâng cao (Advanced)

**Lệnh (Commands):** JSON.SET, JSON.GET

**Độ phức tạp (Complexity):**
- JSON.SET: O(M+N)
- JSON.GET: O(N)

**Có sẵn trong (Available in):** Redis CLI, C#, Go, Java (Asynchronous - Lettuce), Java (Reactive - Lettuce), Java (Synchronous - Jedis), JavaScript (Node.js), PHP, Python, Ruby, Rust (Asynchronous), Rust (Synchronous)

##### Redis CLI

```
> JSON.SET doc $ '{"a":[1,2,3,4],"allow":[2,3]}'
OK
> JSON.GET doc '$.a[?@ in [2,4]]'
"[2,4]"
> JSON.GET doc '$.a[?@ nin [2,4]]'
"[1,3]"
> JSON.GET doc '$.a[?@ in $.allow]'
"[2,3]"
```

##### C#

```csharp
        bool res13 = db.JSON().Set("doc", "$", "{\"a\":[1,2,3,4],\"allow\":[2,3]}");
        Console.WriteLine(res13);   // >>> True

        RedisResult res14 = db.JSON().Get("doc", path: "$.a[?@ in [2,4]]");
        Console.WriteLine(res14);   // >>> [2,4]

        RedisResult res15 = db.JSON().Get("doc", path: "$.a[?@ nin [2,4]]");
        Console.WriteLine(res15);   // >>> [1,3]

        RedisResult res16 = db.JSON().Get("doc", path: "$.a[?@ in $.allow]");
        Console.WriteLine(res16);   // >>> [2,3]
```

##### Go

```go
	res1, err := rdb.JSONSet(ctx, "doc", "$",
		`{"a":[1,2,3,4],"allow":[2,3]}`,
	).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res1) // >>> OK

	res2, err := rdb.JSONGet(ctx, "doc", `$.a[?@ in [2,4]]`).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res2) // >>> [2,4]

	res3, err := rdb.JSONGet(ctx, "doc", `$.a[?@ nin [2,4]]`).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res3) // >>> [1,3]

	res4, err := rdb.JSONGet(ctx, "doc", `$.a[?@ in $.allow]`).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res4) // >>> [2,3]
```

##### Java (Asynchronous - Lettuce)

```java
            CompletableFuture<Void> filterMembershipExample = asyncCommands
                    .jsonSet("doc", JsonPath.ROOT_PATH,
                            parser.createJsonValue("{\"a\":[1,2,3,4],\"allow\":[2,3]}"))
                    .thenCompose(res1 -> {
                        System.out.println(res1); // >>> OK
                        return asyncCommands.jsonGet("doc", JsonPath.of("$.a[?@ in [2,4]]"));
                    }).thenCompose(res2 -> {
                        System.out.println(res2); // >>> [[2,4]]
                        return asyncCommands.jsonGet("doc", JsonPath.of("$.a[?@ nin [2,4]]"));
                    }).thenCompose(res3 -> {
                        System.out.println(res3); // >>> [[1,3]]
                        return asyncCommands.jsonGet("doc", JsonPath.of("$.a[?@ in $.allow]"));
                    }).thenAccept(res4 -> {
                        System.out.println(res4); // >>> [[2,3]]
                    }).toCompletableFuture();
```

##### Java (Reactive - Lettuce)

```java
            Mono<Void> filterMembershipExample = reactiveCommands
                    .jsonSet("doc", JsonPath.ROOT_PATH, parser.createJsonValue("{\"a\":[1,2,3,4],\"allow\":[2,3]}"))
                    .doOnNext(res1 -> {
                        System.out.println(res1); // >>> OK
                    })
                    .flatMap(res1 -> reactiveCommands.jsonGet("doc", JsonPath.of("$.a[?@ in [2,4]]")).collectList())
                    .doOnNext(res2 -> {
                        System.out.println(res2); // >>> [[2,4]]
                    })
                    .flatMap(res2 -> reactiveCommands.jsonGet("doc", JsonPath.of("$.a[?@ nin [2,4]]")).collectList())
                    .doOnNext(res3 -> {
                        System.out.println(res3); // >>> [[1,3]]
                    })
                    .flatMap(res3 -> reactiveCommands.jsonGet("doc", JsonPath.of("$.a[?@ in $.allow]")).collectList())
                    .doOnNext(res4 -> {
                        System.out.println(res4); // >>> [[2,3]]
                    })
                    .then();
```

##### Java (Synchronous - Jedis)

```java
        String res13 = jedis.jsonSet("doc", new Path2("$"), "{\"a\":[1,2,3,4],\"allow\":[2,3]}");
        System.out.println(res13);    // >>> OK

        Object res14 = jedis.jsonGet("doc", new Path2("$.a[?@ in [2,4]]"));
        System.out.println(res14);    // >>> [2,4]

        Object res15 = jedis.jsonGet("doc", new Path2("$.a[?@ nin [2,4]]"));
        System.out.println(res15);    // >>> [1,3]

        Object res16 = jedis.jsonGet("doc", new Path2("$.a[?@ in $.allow]"));
        System.out.println(res16);    // >>> [2,3]
```

##### JavaScript (Node.js)

```javascript
const res13 = await client.json.set('doc', '$', { a: [1, 2, 3, 4], allow: [2, 3] });
console.log(res13); // >>> OK

const res14 = await client.json.get('doc', { path: '$.a[?@ in [2,4]]' });
console.log(res14); // >>> [ 2, 4 ]

const res15 = await client.json.get('doc', { path: '$.a[?@ nin [2,4]]' });
console.log(res15); // >>> [ 1, 3 ]

const res16 = await client.json.get('doc', { path: '$.a[?@ in $.allow]' });
console.log(res16); // >>> [ 2, 3 ]
```

##### PHP

```php
        $res13 = $this->redis->jsonset('doc', '$', json_encode(
            ["a" => [1, 2, 3, 4], "allow" => [2, 3]]
        ));
        echo $res13 . PHP_EOL; // >>> OK

        $res14 = $this->redis->jsonget('doc', '', '', '', '$.a[?@ in [2,4]]');
        echo $res14 . PHP_EOL; // >>> [2,4]

        $res15 = $this->redis->jsonget('doc', '', '', '', '$.a[?@ nin [2,4]]');
        echo $res15 . PHP_EOL; // >>> [1,3]

        $res16 = $this->redis->jsonget('doc', '', '', '', '$.a[?@ in $.allow]');
        echo $res16 . PHP_EOL; // >>> [2,3]
```

##### Python

```python
res1 = r.json().set("doc", "$", {"a": [1, 2, 3, 4], "allow": [2, 3]})
print(res1)
# >>> True

res2 = r.json().get("doc", "$.a[?@ in [2,4]]")
print(res2)
# >>> [2, 4]

res3 = r.json().get("doc", "$.a[?@ nin [2,4]]")
print(res3)
# >>> [1, 3]

res4 = r.json().get("doc", "$.a[?@ in $.allow]")
print(res4)
# >>> [2, 3]
```

##### Ruby

```ruby
res1 = r.json_set('doc', '$', { 'a' => [1, 2, 3, 4], 'allow' => [2, 3] })
puts res1 # >>> OK

res2 = r.json_get('doc', '$.a[?@ in [2,4]]')
p res2 # >>> [2, 4]

res3 = r.json_get('doc', '$.a[?@ nin [2,4]]')
p res3 # >>> [1, 3]

res4 = r.json_get('doc', '$.a[?@ in $.allow]')
p res4 # >>> [2, 3]
```

##### Rust (Asynchronous)

```rust
        let _: bool = match r.json_set("doc", "$", &json!({"a":[1,2,3,4],"allow":[2,3]})).await {
            Ok(v) => v,
            Err(e) => {
                println!("Error setting doc: {e}");
                return;
            }
        };

        match r.json_get("doc", "$.a[?@ in [2,4]]").await {
            Ok(res10) => {
                let res10: String = res10;
                println!("{res10}");    // >>> [2,4]
            },
            Err(e) => {
                println!("Error getting doc: {e}");
                return;
            }
        }

        match r.json_get("doc", "$.a[?@ nin [2,4]]").await {
            Ok(res11) => {
                let res11: String = res11;
                println!("{res11}");    // >>> [1,3]
            },
            Err(e) => {
                println!("Error getting doc: {e}");
                return;
            }
        }

        match r.json_get("doc", "$.a[?@ in $.allow]").await {
            Ok(res12) => {
                let res12: String = res12;
                println!("{res12}");    // >>> [2,3]
            },
            Err(e) => {
                println!("Error getting doc: {e}");
                return;
            }
        }

```

##### Rust (Synchronous)

```rust
        match r.json_set("doc", "$", &json!({"a":[1,2,3,4],"allow":[2,3]})) {
            Ok(res13) => {
                let res13: bool = res13;
                println!("{}", if res13 { "OK" } else { "(nil)" });    // >>> OK
            },
            Err(e) => {
                println!("Error setting doc: {e}");
                return;
            }
        }

        match r.json_get("doc", "$.a[?@ in [2,4]]") {
            Ok(res14) => {
                let res14: String = res14;
                println!("{res14}");    // >>> [2,4]
            },
            Err(e) => {
                println!("Error getting doc: {e}");
                return;
            }
        }

        match r.json_get("doc", "$.a[?@ nin [2,4]]") {
            Ok(res15) => {
                let res15: String = res15;
                println!("{res15}");    // >>> [1,3]
            },
            Err(e) => {
                println!("Error getting doc: {e}");
                return;
            }
        }

        match r.json_get("doc", "$.a[?@ in $.allow]") {
            Ok(res16) => {
                let res16: String = res16;
                println!("{res16}");    // >>> [2,3]
            },
            Err(e) => {
                println!("Error getting doc: {e}");
                return;
            }
        }

```



<a name="set-relations-subsetof-anyof-and-noneof"></a>

#### Quan hệ tập hợp: `subsetof`, `anyof`, và `noneof` (Set relations: `subsetof`, `anyof`, and `noneof`)

Các toán tử này so sánh hai mảng (mỗi vế có thể là một literal mảng hoặc một path tới một mảng):

- `subsetof` khớp khi mọi phần tử của mảng bên trái đều là thành viên của mảng bên phải. Một mảng rỗng là tập con của bất kỳ mảng nào.
- `anyof` khớp khi hai mảng có phần giao khác rỗng. Một mảng rỗng không bao giờ khớp.
- `noneof` khớp khi hai mảng có phần giao rỗng. Một mảng rỗng luôn khớp.

*Filter quan hệ tập hợp: Dùng subsetof, anyof, và noneof để so sánh hai mảng khi bạn cần so khớp dựa trên phần chồng lấn của chúng*

**Độ khó (Difficulty):** Nâng cao (Advanced)

**Lệnh (Commands):** JSON.SET, JSON.GET

**Độ phức tạp (Complexity):**
- JSON.SET: O(M+N)
- JSON.GET: O(N)

**Có sẵn trong (Available in):** Redis CLI, C#, Go, Java (Asynchronous - Lettuce), Java (Reactive - Lettuce), Java (Synchronous - Jedis), JavaScript (Node.js), PHP, Python, Ruby, Rust (Asynchronous), Rust (Synchronous)

##### Redis CLI

```
> JSON.SET doc $ '{"a":[[1,2],[1,5],[]]}'
OK
> JSON.GET doc '$.a[?@ subsetof [1,2,3]]'
"[[1,2],[]]"
> JSON.SET doc $ '{"a":[[1,9],[8,9],[]]}'
OK
> JSON.GET doc '$.a[?@ anyof [1,2,3]]'
"[[1,9]]"
> JSON.SET doc $ '{"a":[[4,5],[1,9],[]]}'
OK
> JSON.GET doc '$.a[?@ noneof [1,2,3]]'
"[[4,5],[]]"
```

##### C#

```csharp
        bool res17 = db.JSON().Set("doc", "$", "{\"a\":[[1,2],[1,5],[]]}");
        Console.WriteLine(res17);   // >>> True

        RedisResult res18 = db.JSON().Get("doc", path: "$.a[?@ subsetof [1,2,3]]");
        Console.WriteLine(res18);   // >>> [[1,2],[]]

        bool res19 = db.JSON().Set("doc", "$", "{\"a\":[[1,9],[8,9],[]]}");
        Console.WriteLine(res19);   // >>> True

        RedisResult res20 = db.JSON().Get("doc", path: "$.a[?@ anyof [1,2,3]]");
        Console.WriteLine(res20);   // >>> [[1,9]]

        bool res21 = db.JSON().Set("doc", "$", "{\"a\":[[4,5],[1,9],[]]}");
        Console.WriteLine(res21);   // >>> True

        RedisResult res22 = db.JSON().Get("doc", path: "$.a[?@ noneof [1,2,3]]");
        Console.WriteLine(res22);   // >>> [[4,5],[]]
```

##### Go

```go
	res1, err := rdb.JSONSet(ctx, "doc", "$",
		`{"a":[[1,2],[1,5],[]]}`,
	).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res1) // >>> OK

	res2, err := rdb.JSONGet(ctx, "doc", `$.a[?@ subsetof [1,2,3]]`).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res2) // >>> [[1,2],[]]

	res3, err := rdb.JSONSet(ctx, "doc", "$",
		`{"a":[[1,9],[8,9],[]]}`,
	).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res3) // >>> OK

	res4, err := rdb.JSONGet(ctx, "doc", `$.a[?@ anyof [1,2,3]]`).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res4) // >>> [[1,9]]

	res5, err := rdb.JSONSet(ctx, "doc", "$",
		`{"a":[[4,5],[1,9],[]]}`,
	).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res5) // >>> OK

	res6, err := rdb.JSONGet(ctx, "doc", `$.a[?@ noneof [1,2,3]]`).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res6) // >>> [[4,5],[]]
```

##### Java (Asynchronous - Lettuce)

```java
            CompletableFuture<Void> filterSetRelationsExample = asyncCommands
                    .jsonSet("doc", JsonPath.ROOT_PATH, parser.createJsonValue("{\"a\":[[1,2],[1,5],[]]}"))
                    .thenCompose(res1 -> {
                        System.out.println(res1); // >>> OK
                        return asyncCommands.jsonGet("doc", JsonPath.of("$.a[?@ subsetof [1,2,3]]"));
                    }).thenCompose(res2 -> {
                        System.out.println(res2); // >>> [[[1,2],[]]]
                        return asyncCommands.jsonSet("doc", JsonPath.ROOT_PATH,
                                parser.createJsonValue("{\"a\":[[1,9],[8,9],[]]}"));
                    }).thenCompose(res3 -> {
                        System.out.println(res3); // >>> OK
                        return asyncCommands.jsonGet("doc", JsonPath.of("$.a[?@ anyof [1,2,3]]"));
                    }).thenCompose(res4 -> {
                        System.out.println(res4); // >>> [[[1,9]]]
                        return asyncCommands.jsonSet("doc", JsonPath.ROOT_PATH,
                                parser.createJsonValue("{\"a\":[[4,5],[1,9],[]]}"));
                    }).thenCompose(res5 -> {
                        System.out.println(res5); // >>> OK
                        return asyncCommands.jsonGet("doc", JsonPath.of("$.a[?@ noneof [1,2,3]]"));
                    }).thenAccept(res6 -> {
                        System.out.println(res6); // >>> [[[4,5],[]]]
                    }).toCompletableFuture();
```

##### Java (Reactive - Lettuce)

```java
            Mono<Void> filterSetRelationsExample = reactiveCommands
                    .jsonSet("doc", JsonPath.ROOT_PATH, parser.createJsonValue("{\"a\":[[1,2],[1,5],[]]}"))
                    .doOnNext(res1 -> {
                        System.out.println(res1); // >>> OK
                    })
                    .flatMap(res1 -> reactiveCommands.jsonGet("doc", JsonPath.of("$.a[?@ subsetof [1,2,3]]")).collectList())
                    .doOnNext(res2 -> {
                        System.out.println(res2); // >>> [[[1,2],[]]]
                    })
                    .flatMap(res2 -> reactiveCommands.jsonSet("doc", JsonPath.ROOT_PATH,
                            parser.createJsonValue("{\"a\":[[1,9],[8,9],[]]}")))
                    .doOnNext(res3 -> {
                        System.out.println(res3); // >>> OK
                    })
                    .flatMap(res3 -> reactiveCommands.jsonGet("doc", JsonPath.of("$.a[?@ anyof [1,2,3]]")).collectList())
                    .doOnNext(res4 -> {
                        System.out.println(res4); // >>> [[[1,9]]]
                    })
                    .flatMap(res4 -> reactiveCommands.jsonSet("doc", JsonPath.ROOT_PATH,
                            parser.createJsonValue("{\"a\":[[4,5],[1,9],[]]}")))
                    .doOnNext(res5 -> {
                        System.out.println(res5); // >>> OK
                    })
                    .flatMap(res5 -> reactiveCommands.jsonGet("doc", JsonPath.of("$.a[?@ noneof [1,2,3]]")).collectList())
                    .doOnNext(res6 -> {
                        System.out.println(res6); // >>> [[[4,5],[]]]
                    })
                    .then();
```

##### Java (Synchronous - Jedis)

```java
        String res17 = jedis.jsonSet("doc", new Path2("$"), "{\"a\":[[1,2],[1,5],[]]}");
        System.out.println(res17);    // >>> OK

        Object res18 = jedis.jsonGet("doc", new Path2("$.a[?@ subsetof [1,2,3]]"));
        System.out.println(res18);    // >>> [[1,2],[]]

        String res19 = jedis.jsonSet("doc", new Path2("$"), "{\"a\":[[1,9],[8,9],[]]}");
        System.out.println(res19);    // >>> OK

        Object res20 = jedis.jsonGet("doc", new Path2("$.a[?@ anyof [1,2,3]]"));
        System.out.println(res20);    // >>> [[1,9]]

        String res21 = jedis.jsonSet("doc", new Path2("$"), "{\"a\":[[4,5],[1,9],[]]}");
        System.out.println(res21);    // >>> OK

        Object res22 = jedis.jsonGet("doc", new Path2("$.a[?@ noneof [1,2,3]]"));
        System.out.println(res22);    // >>> [[4,5],[]]
```

##### JavaScript (Node.js)

```javascript
const res17 = await client.json.set('doc', '$', { a: [[1, 2], [1, 5], []] });
console.log(res17); // >>> OK

const res18 = await client.json.get('doc', { path: '$.a[?@ subsetof [1,2,3]]' });
console.log(res18); // >>> [ [ 1, 2 ], [] ]

const res19 = await client.json.set('doc', '$', { a: [[1, 9], [8, 9], []] });
console.log(res19); // >>> OK

const res20 = await client.json.get('doc', { path: '$.a[?@ anyof [1,2,3]]' });
console.log(res20); // >>> [ [ 1, 9 ] ]

const res21 = await client.json.set('doc', '$', { a: [[4, 5], [1, 9], []] });
console.log(res21); // >>> OK

const res22 = await client.json.get('doc', { path: '$.a[?@ noneof [1,2,3]]' });
console.log(res22); // >>> [ [ 4, 5 ], [] ]
```

##### PHP

```php
        $res17 = $this->redis->jsonset('doc', '$', json_encode(
            ["a" => [[1, 2], [1, 5], []]]
        ));
        echo $res17 . PHP_EOL; // >>> OK

        $res18 = $this->redis->jsonget('doc', '', '', '', '$.a[?@ subsetof [1,2,3]]');
        echo $res18 . PHP_EOL; // >>> [[1,2],[]]

        $res19 = $this->redis->jsonset('doc', '$', json_encode(
            ["a" => [[1, 9], [8, 9], []]]
        ));
        echo $res19 . PHP_EOL; // >>> OK

        $res20 = $this->redis->jsonget('doc', '', '', '', '$.a[?@ anyof [1,2,3]]');
        echo $res20 . PHP_EOL; // >>> [[1,9]]

        $res21 = $this->redis->jsonset('doc', '$', json_encode(
            ["a" => [[4, 5], [1, 9], []]]
        ));
        echo $res21 . PHP_EOL; // >>> OK

        $res22 = $this->redis->jsonget('doc', '', '', '', '$.a[?@ noneof [1,2,3]]');
        echo $res22 . PHP_EOL; // >>> [[4,5],[]]
```

##### Python

```python
res1 = r.json().set("doc", "$", {"a": [[1, 2], [1, 5], []]})
print(res1)
# >>> True

res2 = r.json().get("doc", "$.a[?@ subsetof [1,2,3]]")
print(res2)
# >>> [[1, 2], []]

res3 = r.json().set("doc", "$", {"a": [[1, 9], [8, 9], []]})
print(res3)
# >>> True

res4 = r.json().get("doc", "$.a[?@ anyof [1,2,3]]")
print(res4)
# >>> [[1, 9]]

res5 = r.json().set("doc", "$", {"a": [[4, 5], [1, 9], []]})
print(res5)
# >>> True

res6 = r.json().get("doc", "$.a[?@ noneof [1,2,3]]")
print(res6)
# >>> [[4, 5], []]
```

##### Ruby

```ruby
res1 = r.json_set('doc', '$', { 'a' => [[1, 2], [1, 5], []] })
puts res1 # >>> OK

res2 = r.json_get('doc', '$.a[?@ subsetof [1,2,3]]')
p res2 # >>> [[1, 2], []]

res3 = r.json_set('doc', '$', { 'a' => [[1, 9], [8, 9], []] })
puts res3 # >>> OK

res4 = r.json_get('doc', '$.a[?@ anyof [1,2,3]]')
p res4 # >>> [[1, 9]]

res5 = r.json_set('doc', '$', { 'a' => [[4, 5], [1, 9], []] })
puts res5 # >>> OK

res6 = r.json_get('doc', '$.a[?@ noneof [1,2,3]]')
p res6 # >>> [[4, 5], []]
```

##### Rust (Asynchronous)

```rust
        let _: bool = match r.json_set("doc", "$", &json!({"a":[[1,2],[1,5],[]]})).await {
            Ok(v) => v,
            Err(e) => {
                println!("Error setting doc: {e}");
                return;
            }
        };

        match r.json_get("doc", "$.a[?@ subsetof [1,2,3]]").await {
            Ok(res13) => {
                let res13: String = res13;
                println!("{res13}");    // >>> [[1,2],[]]
            },
            Err(e) => {
                println!("Error getting doc: {e}");
                return;
            }
        }

        let _: bool = match r.json_set("doc", "$", &json!({"a":[[1,9],[8,9],[]]})).await {
            Ok(v) => v,
            Err(e) => {
                println!("Error setting doc: {e}");
                return;
            }
        };

        match r.json_get("doc", "$.a[?@ anyof [1,2,3]]").await {
            Ok(res14) => {
                let res14: String = res14;
                println!("{res14}");    // >>> [[1,9]]
            },
            Err(e) => {
                println!("Error getting doc: {e}");
                return;
            }
        }

        let _: bool = match r.json_set("doc", "$", &json!({"a":[[4,5],[1,9],[]]})).await {
            Ok(v) => v,
            Err(e) => {
                println!("Error setting doc: {e}");
                return;
            }
        };

        match r.json_get("doc", "$.a[?@ noneof [1,2,3]]").await {
            Ok(res15) => {
                let res15: String = res15;
                println!("{res15}");    // >>> [[4,5],[]]
            },
            Err(e) => {
                println!("Error getting doc: {e}");
                return;
            }
        }

```

##### Rust (Synchronous)

```rust
        match r.json_set("doc", "$", &json!({"a":[[1,2],[1,5],[]]})) {
            Ok(res17) => {
                let res17: bool = res17;
                println!("{}", if res17 { "OK" } else { "(nil)" });    // >>> OK
            },
            Err(e) => {
                println!("Error setting doc: {e}");
                return;
            }
        }

        match r.json_get("doc", "$.a[?@ subsetof [1,2,3]]") {
            Ok(res18) => {
                let res18: String = res18;
                println!("{res18}");    // >>> [[1,2],[]]
            },
            Err(e) => {
                println!("Error getting doc: {e}");
                return;
            }
        }

        match r.json_set("doc", "$", &json!({"a":[[1,9],[8,9],[]]})) {
            Ok(res19) => {
                let res19: bool = res19;
                println!("{}", if res19 { "OK" } else { "(nil)" });    // >>> OK
            },
            Err(e) => {
                println!("Error setting doc: {e}");
                return;
            }
        }

        match r.json_get("doc", "$.a[?@ anyof [1,2,3]]") {
            Ok(res20) => {
                let res20: String = res20;
                println!("{res20}");    // >>> [[1,9]]
            },
            Err(e) => {
                println!("Error getting doc: {e}");
                return;
            }
        }

        match r.json_set("doc", "$", &json!({"a":[[4,5],[1,9],[]]})) {
            Ok(res21) => {
                let res21: bool = res21;
                println!("{}", if res21 { "OK" } else { "(nil)" });    // >>> OK
            },
            Err(e) => {
                println!("Error setting doc: {e}");
                return;
            }
        }

        match r.json_get("doc", "$.a[?@ noneof [1,2,3]]") {
            Ok(res22) => {
                let res22: String = res22;
                println!("{res22}");    // >>> [[4,5],[]]
            },
            Err(e) => {
                println!("Error getting doc: {e}");
                return;
            }
        }

```



#### `size` (`sizeof`) and `empty`

`left sizeof n` khớp khi kích thước của `left` bằng số nguyên `n`, trong đó kích thước là số ký tự trong một string, số phần tử trong một mảng, hoặc số member trong một object. Một `n` không phải số nguyên sẽ bị cắt về phía 0; một `n` không phải số không bao giờ khớp. `size` là bí danh (alias) của `sizeof`.

`left empty true` khớp với một string, mảng, hoặc object rỗng; `left empty false` khớp với một cái không rỗng.

*Filter kích thước và rỗng: Dùng sizeof và empty để lọc theo kích thước hoặc tính rỗng của một string, mảng, hoặc object*

**Độ khó (Difficulty):** Nâng cao (Advanced)

**Lệnh (Commands):** JSON.SET, JSON.GET

**Độ phức tạp (Complexity):**
- JSON.SET: O(M+N)
- JSON.GET: O(N)

**Có sẵn trong (Available in):** Redis CLI, C#, Go, Java (Asynchronous - Lettuce), Java (Reactive - Lettuce), Java (Synchronous - Jedis), JavaScript (Node.js), PHP, Python, Ruby, Rust (Asynchronous), Rust (Synchronous)

##### Redis CLI

```
> JSON.SET doc $ '{"a":[[4,5],[1],[7,8,9]]}'
OK
> JSON.GET doc '$.a[?@ sizeof 2]'
"[[4,5]]"
> JSON.SET doc $ '{"a":[[],[1],"",[2,3],{},{"k":1}]}'
OK
> JSON.GET doc '$.a[?@ empty true]'
"[[],\"\",{}]"
> JSON.GET doc '$.a[?@ empty false]'
"[[1],[2,3],{\"k\":1}]"
```

##### C#

```csharp
        bool res23 = db.JSON().Set("doc", "$", "{\"a\":[[4,5],[1],[7,8,9]]}");
        Console.WriteLine(res23);   // >>> True

        RedisResult res24 = db.JSON().Get("doc", path: "$.a[?@ sizeof 2]");
        Console.WriteLine(res24);   // >>> [[4,5]]

        bool res25 = db.JSON().Set("doc", "$", "{\"a\":[[],[1],\"\",[2,3],{},{\"k\":1}]}");
        Console.WriteLine(res25);   // >>> True

        RedisResult res26 = db.JSON().Get("doc", path: "$.a[?@ empty true]");
        Console.WriteLine(res26);   // >>> [[],"",{}]

        RedisResult res27 = db.JSON().Get("doc", path: "$.a[?@ empty false]");
        Console.WriteLine(res27);   // >>> [[1],[2,3],{"k":1}]
```

##### Go

```go
	res1, err := rdb.JSONSet(ctx, "doc", "$",
		`{"a":[[4,5],[1],[7,8,9]]}`,
	).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res1) // >>> OK

	res2, err := rdb.JSONGet(ctx, "doc", `$.a[?@ sizeof 2]`).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res2) // >>> [[4,5]]

	res3, err := rdb.JSONSet(ctx, "doc", "$",
		`{"a":[[],[1],"",[2,3],{},{"k":1}]}`,
	).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res3) // >>> OK

	res4, err := rdb.JSONGet(ctx, "doc", `$.a[?@ empty true]`).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res4) // >>> [[],"",{}]

	res5, err := rdb.JSONGet(ctx, "doc", `$.a[?@ empty false]`).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res5) // >>> [[1],[2,3],{"k":1}]
```

##### Java (Asynchronous - Lettuce)

```java
            CompletableFuture<Void> filterSizeEmptyExample = asyncCommands
                    .jsonSet("doc", JsonPath.ROOT_PATH, parser.createJsonValue("{\"a\":[[4,5],[1],[7,8,9]]}"))
                    .thenCompose(res1 -> {
                        System.out.println(res1); // >>> OK
                        return asyncCommands.jsonGet("doc", JsonPath.of("$.a[?@ sizeof 2]"));
                    }).thenCompose(res2 -> {
                        System.out.println(res2); // >>> [[[4,5]]]
                        return asyncCommands.jsonSet("doc", JsonPath.ROOT_PATH,
                                parser.createJsonValue("{\"a\":[[],[1],\"\",[2,3],{},{\"k\":1}]}"));
                    }).thenCompose(res3 -> {
                        System.out.println(res3); // >>> OK
                        return asyncCommands.jsonGet("doc", JsonPath.of("$.a[?@ empty true]"));
                    }).thenCompose(res4 -> {
                        System.out.println(res4); // >>> [[[],"",{}]]
                        return asyncCommands.jsonGet("doc", JsonPath.of("$.a[?@ empty false]"));
                    }).thenAccept(res5 -> {
                        System.out.println(res5); // >>> [[[1],[2,3],{"k":1}]]
                    }).toCompletableFuture();
```

##### Java (Reactive - Lettuce)

```java
            Mono<Void> filterSizeEmptyExample = reactiveCommands
                    .jsonSet("doc", JsonPath.ROOT_PATH, parser.createJsonValue("{\"a\":[[4,5],[1],[7,8,9]]}"))
                    .doOnNext(res1 -> {
                        System.out.println(res1); // >>> OK
                    })
                    .flatMap(res1 -> reactiveCommands.jsonGet("doc", JsonPath.of("$.a[?@ sizeof 2]")).collectList())
                    .doOnNext(res2 -> {
                        System.out.println(res2); // >>> [[[4,5]]]
                    })
                    .flatMap(res2 -> reactiveCommands.jsonSet("doc", JsonPath.ROOT_PATH,
                            parser.createJsonValue("{\"a\":[[],[1],\"\",[2,3],{},{\"k\":1}]}")))
                    .doOnNext(res3 -> {
                        System.out.println(res3); // >>> OK
                    })
                    .flatMap(res3 -> reactiveCommands.jsonGet("doc", JsonPath.of("$.a[?@ empty true]")).collectList())
                    .doOnNext(res4 -> {
                        System.out.println(res4); // >>> [[[],"",{}]]
                    })
                    .flatMap(res4 -> reactiveCommands.jsonGet("doc", JsonPath.of("$.a[?@ empty false]")).collectList())
                    .doOnNext(res5 -> {
                        System.out.println(res5); // >>> [[[1],[2,3],{"k":1}]]
                    })
                    .then();
```

##### Java (Synchronous - Jedis)

```java
        String res23 = jedis.jsonSet("doc", new Path2("$"), "{\"a\":[[4,5],[1],[7,8,9]]}");
        System.out.println(res23);    // >>> OK

        Object res24 = jedis.jsonGet("doc", new Path2("$.a[?@ sizeof 2]"));
        System.out.println(res24);    // >>> [[4,5]]

        String res25 = jedis.jsonSet("doc", new Path2("$"), "{\"a\":[[],[1],\"\",[2,3],{},{\"k\":1}]}");
        System.out.println(res25);    // >>> OK

        Object res26 = jedis.jsonGet("doc", new Path2("$.a[?@ empty true]"));
        System.out.println(res26);    // >>> [[],"",{}]

        Object res27 = jedis.jsonGet("doc", new Path2("$.a[?@ empty false]"));
        System.out.println(res27);    // >>> [[1],[2,3],{"k":1}]
```

##### JavaScript (Node.js)

```javascript
const res23 = await client.json.set('doc', '$', { a: [[4, 5], [1], [7, 8, 9]] });
console.log(res23); // >>> OK

const res24 = await client.json.get('doc', { path: '$.a[?@ sizeof 2]' });
console.log(res24); // >>> [ [ 4, 5 ] ]

const res25 = await client.json.set('doc', '$', { a: [[], [1], '', [2, 3], {}, { k: 1 }] });
console.log(res25); // >>> OK

const res26 = await client.json.get('doc', { path: '$.a[?@ empty true]' });
console.log(res26); // >>> [ [], '', {} ]

const res27 = await client.json.get('doc', { path: '$.a[?@ empty false]' });
console.log(res27); // >>> [ [ 1 ], [ 2, 3 ], { k: 1 } ]
```

##### PHP

```php
        $res23 = $this->redis->jsonset('doc', '$', json_encode(
            ["a" => [[4, 5], [1], [7, 8, 9]]]
        ));
        echo $res23 . PHP_EOL; // >>> OK

        $res24 = $this->redis->jsonget('doc', '', '', '', '$.a[?@ sizeof 2]');
        echo $res24 . PHP_EOL; // >>> [[4,5]]

        $res25 = $this->redis->jsonset('doc', '$', json_encode(
            ["a" => [[], [1], "", [2, 3], new \stdClass(), ["k" => 1]]]
        ));
        echo $res25 . PHP_EOL; // >>> OK

        $res26 = $this->redis->jsonget('doc', '', '', '', '$.a[?@ empty true]');
        echo $res26 . PHP_EOL; // >>> [[],"",{}]

        $res27 = $this->redis->jsonget('doc', '', '', '', '$.a[?@ empty false]');
        echo $res27 . PHP_EOL; // >>> [[1],[2,3],{"k":1}]
```

##### Python

```python
res1 = r.json().set("doc", "$", {"a": [[4, 5], [1], [7, 8, 9]]})
print(res1)
# >>> True

res2 = r.json().get("doc", "$.a[?@ sizeof 2]")
print(res2)
# >>> [[4, 5]]

res3 = r.json().set("doc", "$", {"a": [[], [1], "", [2, 3], {}, {"k": 1}]})
print(res3)
# >>> True

res4 = r.json().get("doc", "$.a[?@ empty true]")
print(res4)
# >>> [[], '', {}]

res5 = r.json().get("doc", "$.a[?@ empty false]")
print(res5)
# >>> [[1], [2, 3], {'k': 1}]
```

##### Ruby

```ruby
res1 = r.json_set('doc', '$', { 'a' => [[4, 5], [1], [7, 8, 9]] })
puts res1 # >>> OK

res2 = r.json_get('doc', '$.a[?@ sizeof 2]')
p res2 # >>> [[4, 5]]

res3 = r.json_set('doc', '$', { 'a' => [[], [1], '', [2, 3], {}, { 'k' => 1 }] })
puts res3 # >>> OK

res4 = r.json_get('doc', '$.a[?@ empty true]')
p res4 # >>> [[], "", {}]

res5 = r.json_get('doc', '$.a[?@ empty false]')
p res5 # >>> [[1], [2, 3], {"k"=>1}]
```

##### Rust (Asynchronous)

```rust
        let _: bool = match r.json_set("doc", "$", &json!({"a":[[4,5],[1],[7,8,9]]})).await {
            Ok(v) => v,
            Err(e) => {
                println!("Error setting doc: {e}");
                return;
            }
        };

        match r.json_get("doc", "$.a[?@ sizeof 2]").await {
            Ok(res16) => {
                let res16: String = res16;
                println!("{res16}");    // >>> [[4,5]]
            },
            Err(e) => {
                println!("Error getting doc: {e}");
                return;
            }
        }

        let _: bool = match r.json_set("doc", "$", &json!({"a":[[],[1],"",[2,3],{},{"k":1}]})).await {
            Ok(v) => v,
            Err(e) => {
                println!("Error setting doc: {e}");
                return;
            }
        };

        match r.json_get("doc", "$.a[?@ empty true]").await {
            Ok(res17) => {
                let res17: String = res17;
                println!("{res17}");    // >>> [[],"",{}]
            },
            Err(e) => {
                println!("Error getting doc: {e}");
                return;
            }
        }

        match r.json_get("doc", "$.a[?@ empty false]").await {
            Ok(res18) => {
                let res18: String = res18;
                println!("{res18}");    // >>> [[1],[2,3],{"k":1}]
            },
            Err(e) => {
                println!("Error getting doc: {e}");
                return;
            }
        }

```

##### Rust (Synchronous)

```rust
        match r.json_set("doc", "$", &json!({"a":[[4,5],[1],[7,8,9]]})) {
            Ok(res23) => {
                let res23: bool = res23;
                println!("{}", if res23 { "OK" } else { "(nil)" });    // >>> OK
            },
            Err(e) => {
                println!("Error setting doc: {e}");
                return;
            }
        }

        match r.json_get("doc", "$.a[?@ sizeof 2]") {
            Ok(res24) => {
                let res24: String = res24;
                println!("{res24}");    // >>> [[4,5]]
            },
            Err(e) => {
                println!("Error getting doc: {e}");
                return;
            }
        }

        match r.json_set("doc", "$", &json!({"a":[[],[1],"",[2,3],{},{"k":1}]})) {
            Ok(res25) => {
                let res25: bool = res25;
                println!("{}", if res25 { "OK" } else { "(nil)" });    // >>> OK
            },
            Err(e) => {
                println!("Error setting doc: {e}");
                return;
            }
        }

        match r.json_get("doc", "$.a[?@ empty true]") {
            Ok(res26) => {
                let res26: String = res26;
                println!("{res26}");    // >>> [[],"",{}]
            },
            Err(e) => {
                println!("Error getting doc: {e}");
                return;
            }
        }

        match r.json_get("doc", "$.a[?@ empty false]") {
            Ok(res27) => {
                let res27: String = res27;
                println!("{res27}");    // >>> [[1],[2,3],{"k":1}]
            },
            Err(e) => {
                println!("Error getting doc: {e}");
                return;
            }
        }

```



<a name="get-keys-operator-"></a>

#### Toán tử lấy key `~` (Get-keys operator `~`)

Toán tử kết thúc (terminal) `~` trả về tên các member của một object dưới dạng một danh sách string phẳng. Khi áp dụng lên gốc (`$~`), nó trả về các key cấp cao nhất. Khi áp dụng lên một đối tượng nhận khớp nhiều kết quả, nó làm phẳng các key của mọi object được khớp. Một đối tượng nhận không phải object sẽ không đóng góp key nào.

Vì `~` là toán tử kết thúc, các biểu thức như `$.obj~.x`, `$.obj~~`, và `$.obj.keys()~` là lỗi phân tích cú pháp (parse error). Để có một lựa chọn thay thế có thể kết hợp và nối chuỗi với các hàm khác, hãy dùng hàm [`keys()`](#keys).

*Filter lấy key: Dùng toán tử ~ để lấy tên các member của một object dưới dạng danh sách string*

**Độ khó (Difficulty):** Nâng cao (Advanced)

**Lệnh (Commands):** JSON.SET, JSON.GET

**Độ phức tạp (Complexity):**
- JSON.SET: O(M+N)
- JSON.GET: O(N)

**Có sẵn trong (Available in):** Redis CLI, C#, Go, Java (Asynchronous - Lettuce), Java (Reactive - Lettuce), Java (Synchronous - Jedis), JavaScript (Node.js), PHP, Python, Ruby, Rust (Asynchronous), Rust (Synchronous)

##### Redis CLI

```
> JSON.SET doc $ '{"obj":{"x":1,"y":2},"books":[{"t":"a"},{"t":"b"}]}'
OK
> JSON.GET doc '$.obj~'
"[\"x\",\"y\"]"
> JSON.GET doc '$~'
"[\"obj\",\"books\"]"
> JSON.GET doc '$.books~'
"[]"
```

##### C#

```csharp
        bool res28 = db.JSON().Set(
            "doc",
            "$",
            "{\"obj\":{\"x\":1,\"y\":2},\"books\":[{\"t\":\"a\"},{\"t\":\"b\"}]}"
        );
        Console.WriteLine(res28);   // >>> True

        RedisResult res29 = db.JSON().Get("doc", path: "$.obj~");
        Console.WriteLine(res29);   // >>> ["x","y"]

        RedisResult res30 = db.JSON().Get("doc", path: "$~");
        Console.WriteLine(res30);   // >>> ["obj","books"]

        RedisResult res31 = db.JSON().Get("doc", path: "$.books~");
        Console.WriteLine(res31);   // >>> []
```

##### Go

```go
	res1, err := rdb.JSONSet(ctx, "doc", "$",
		`{"obj":{"x":1,"y":2},"books":[{"t":"a"},{"t":"b"}]}`,
	).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res1) // >>> OK

	res2, err := rdb.JSONGet(ctx, "doc", `$.obj~`).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res2) // >>> ["x","y"]

	res3, err := rdb.JSONGet(ctx, "doc", `$~`).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res3) // >>> ["obj","books"]

	res4, err := rdb.JSONGet(ctx, "doc", `$.books~`).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res4) // >>> []
```

##### Java (Asynchronous - Lettuce)

```java
            CompletableFuture<Void> filterGetkeysExample = asyncCommands
                    .jsonSet("doc", JsonPath.ROOT_PATH,
                            parser.createJsonValue(
                                    "{\"obj\":{\"x\":1,\"y\":2},\"books\":[{\"t\":\"a\"},{\"t\":\"b\"}]}"))
                    .thenCompose(res1 -> {
                        System.out.println(res1); // >>> OK
                        return asyncCommands.jsonGet("doc", JsonPath.of("$.obj~"));
                    }).thenCompose(res2 -> {
                        System.out.println(res2); // >>> [["x","y"]]
                        return asyncCommands.jsonGet("doc", JsonPath.of("$~"));
                    }).thenCompose(res3 -> {
                        System.out.println(res3); // >>> [["obj","books"]]
                        return asyncCommands.jsonGet("doc", JsonPath.of("$.books~"));
                    }).thenAccept(res4 -> {
                        System.out.println(res4); // >>> [[]]
                    }).toCompletableFuture();
```

##### Java (Reactive - Lettuce)

```java
            Mono<Void> filterGetkeysExample = reactiveCommands
                    .jsonSet("doc", JsonPath.ROOT_PATH,
                            parser.createJsonValue("{\"obj\":{\"x\":1,\"y\":2},\"books\":[{\"t\":\"a\"},{\"t\":\"b\"}]}"))
                    .doOnNext(res1 -> {
                        System.out.println(res1); // >>> OK
                    })
                    .flatMap(res1 -> reactiveCommands.jsonGet("doc", JsonPath.of("$.obj~")).collectList())
                    .doOnNext(res2 -> {
                        System.out.println(res2); // >>> [["x","y"]]
                    })
                    .flatMap(res2 -> reactiveCommands.jsonGet("doc", JsonPath.of("$~")).collectList())
                    .doOnNext(res3 -> {
                        System.out.println(res3); // >>> [["obj","books"]]
                    })
                    .flatMap(res3 -> reactiveCommands.jsonGet("doc", JsonPath.of("$.books~")).collectList())
                    .doOnNext(res4 -> {
                        System.out.println(res4); // >>> [[]]
                    })
                    .then();
```

##### Java (Synchronous - Jedis)

```java
        String res28 = jedis.jsonSet(
            "doc",
            new Path2("$"),
            "{\"obj\":{\"x\":1,\"y\":2},\"books\":[{\"t\":\"a\"},{\"t\":\"b\"}]}"
        );
        System.out.println(res28);    // >>> OK

        Object res29 = jedis.jsonGet("doc", new Path2("$.obj~"));
        System.out.println(res29);    // >>> ["x","y"]

        Object res30 = jedis.jsonGet("doc", new Path2("$~"));
        System.out.println(res30);    // >>> ["obj","books"]

        Object res31 = jedis.jsonGet("doc", new Path2("$.books~"));
        System.out.println(res31);    // >>> []
```

##### JavaScript (Node.js)

```javascript
const res28 = await client.json.set('doc', '$', {
  obj: { x: 1, y: 2 },
  books: [{ t: 'a' }, { t: 'b' }]
});
console.log(res28); // >>> OK

const res29 = await client.json.get('doc', { path: '$.obj~' });
console.log(res29); // >>> [ 'x', 'y' ]

const res30 = await client.json.get('doc', { path: '$~' });
console.log(res30); // >>> [ 'obj', 'books' ]

const res31 = await client.json.get('doc', { path: '$.books~' });
console.log(res31); // >>> []
```

##### PHP

```php
        $res28 = $this->redis->jsonset('doc', '$', json_encode([
            "obj" => ["x" => 1, "y" => 2],
            "books" => [["t" => "a"], ["t" => "b"]],
        ]));
        echo $res28 . PHP_EOL; // >>> OK

        $res29 = $this->redis->jsonget('doc', '', '', '', '$.obj~');
        echo $res29 . PHP_EOL; // >>> ["x","y"]

        $res30 = $this->redis->jsonget('doc', '', '', '', '$~');
        echo $res30 . PHP_EOL; // >>> ["obj","books"]

        $res31 = $this->redis->jsonget('doc', '', '', '', '$.books~');
        echo $res31 . PHP_EOL; // >>> []
```

##### Python

```python
res1 = r.json().set("doc", "$", {"obj": {"x": 1, "y": 2}, "books": [{"t": "a"}, {"t": "b"}]})
print(res1)
# >>> True

res2 = r.json().get("doc", "$.obj~")
print(res2)
# >>> ['x', 'y']

res3 = r.json().get("doc", "$~")
print(res3)
# >>> ['obj', 'books']

res4 = r.json().get("doc", "$.books~")
print(res4)
# >>> []
```

##### Ruby

```ruby
res1 = r.json_set('doc', '$', { 'obj' => { 'x' => 1, 'y' => 2 }, 'books' => [{ 't' => 'a' }, { 't' => 'b' }] })
puts res1 # >>> OK

res2 = r.json_get('doc', '$.obj~')
p res2 # >>> ["x", "y"]

res3 = r.json_get('doc', '$~')
p res3 # >>> ["obj", "books"]

res4 = r.json_get('doc', '$.books~')
p res4 # >>> []
```

##### Rust (Asynchronous)

```rust
        let _: bool = match r.json_set(
            "doc",
            "$",
            &json!({"obj":{"x":1,"y":2},"books":[{"t":"a"},{"t":"b"}]}),
        ).await {
            Ok(v) => v,
            Err(e) => {
                println!("Error setting doc: {e}");
                return;
            }
        };

        match r.json_get("doc", "$.obj~").await {
            Ok(res19) => {
                let res19: String = res19;
                println!("{res19}");    // >>> ["x","y"]
            },
            Err(e) => {
                println!("Error getting doc: {e}");
                return;
            }
        }

        match r.json_get("doc", "$~").await {
            Ok(res20) => {
                let res20: String = res20;
                println!("{res20}");    // >>> ["obj","books"]
            },
            Err(e) => {
                println!("Error getting doc: {e}");
                return;
            }
        }

        match r.json_get("doc", "$.books~").await {
            Ok(res21) => {
                let res21: String = res21;
                println!("{res21}");    // >>> []
            },
            Err(e) => {
                println!("Error getting doc: {e}");
                return;
            }
        }

```

##### Rust (Synchronous)

```rust
        match r.json_set(
            "doc",
            "$",
            &json!({"obj":{"x":1,"y":2},"books":[{"t":"a"},{"t":"b"}]}),
        ) {
            Ok(res28) => {
                let res28: bool = res28;
                println!("{}", if res28 { "OK" } else { "(nil)" });    // >>> OK
            },
            Err(e) => {
                println!("Error setting doc: {e}");
                return;
            }
        }

        match r.json_get("doc", "$.obj~") {
            Ok(res29) => {
                let res29: String = res29;
                println!("{res29}");    // >>> ["x","y"]
            },
            Err(e) => {
                println!("Error getting doc: {e}");
                return;
            }
        }

        match r.json_get("doc", "$~") {
            Ok(res30) => {
                let res30: String = res30;
                println!("{res30}");    // >>> ["obj","books"]
            },
            Err(e) => {
                println!("Error getting doc: {e}");
                return;
            }
        }

        match r.json_get("doc", "$.books~") {
            Ok(res31) => {
                let res31: String = res31;
                println!("{res31}");    // >>> []
            },
            Err(e) => {
                println!("Error getting doc: {e}");
                return;
            }
        }

```



Hàm có thể xuất hiện bên trong biểu thức filter và, khi chúng trả về một giá trị, như các [biểu thức projection](#projection-expressions) ở cấp cao nhất. Bắt đầu từ Redis 8.10, JSONPath hỗ trợ các hàm sau.

#### `length()`

Trả về số ký tự trong một string, số phần tử trong một mảng, hoặc số member trong một object. Bất kỳ kiểu nào khác đều cho ra *Nothing*.

*Hàm length: Dùng length() để lấy số ký tự trong một string, số phần tử trong một mảng, hoặc số member trong một object*

**Độ khó (Difficulty):** Nâng cao (Advanced)

**Lệnh (Commands):** JSON.SET, JSON.GET

**Độ phức tạp (Complexity):**
- JSON.SET: O(M+N)
- JSON.GET: O(N)

**Có sẵn trong (Available in):** Redis CLI, C#, Go, Java (Asynchronous - Lettuce), Java (Reactive - Lettuce), Java (Synchronous - Jedis), JavaScript (Node.js), PHP, Python, Ruby, Rust (Asynchronous), Rust (Synchronous)

##### Redis CLI

```
> JSON.SET doc $ '{"a":[[1,2,3],[1],"abcd","x"]}'
OK
> JSON.GET doc '$.a[?length(@) > 2]'
"[[1,2,3],\"abcd\"]"
```

##### C#

```csharp
        bool res32 = db.JSON().Set("doc", "$", "{\"a\":[[1,2,3],[1],\"abcd\",\"x\"]}");
        Console.WriteLine(res32);   // >>> True

        RedisResult res33 = db.JSON().Get("doc", path: "$.a[?length(@) > 2]");
        Console.WriteLine(res33);   // >>> [[1,2,3],"abcd"]
```

##### Go

```go
	res1, err := rdb.JSONSet(ctx, "doc", "$",
		`{"a":[[1,2,3],[1],"abcd","x"]}`,
	).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res1) // >>> OK

	res2, err := rdb.JSONGet(ctx, "doc", `$.a[?length(@) > 2]`).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res2) // >>> [[1,2,3],"abcd"]
```

##### Java (Asynchronous - Lettuce)

```java
            CompletableFuture<Void> funcLengthExample = asyncCommands
                    .jsonSet("doc", JsonPath.ROOT_PATH,
                            parser.createJsonValue("{\"a\":[[1,2,3],[1],\"abcd\",\"x\"]}"))
                    .thenCompose(res1 -> {
                        System.out.println(res1); // >>> OK
                        return asyncCommands.jsonGet("doc", JsonPath.of("$.a[?length(@) > 2]"));
                    }).thenAccept(res2 -> {
                        System.out.println(res2); // >>> [[[1,2,3],"abcd"]]
                    }).toCompletableFuture();
```

##### Java (Reactive - Lettuce)

```java
            Mono<Void> funcLengthExample = reactiveCommands
                    .jsonSet("doc", JsonPath.ROOT_PATH,
                            parser.createJsonValue("{\"a\":[[1,2,3],[1],\"abcd\",\"x\"]}"))
                    .doOnNext(res1 -> {
                        System.out.println(res1); // >>> OK
                    })
                    .flatMap(res1 -> reactiveCommands.jsonGet("doc", JsonPath.of("$.a[?length(@) > 2]")).collectList())
                    .doOnNext(res2 -> {
                        System.out.println(res2); // >>> [[[1,2,3],"abcd"]]
                    })
                    .then();
```

##### Java (Synchronous - Jedis)

```java
        String res32 = jedis.jsonSet("doc", new Path2("$"), "{\"a\":[[1,2,3],[1],\"abcd\",\"x\"]}");
        System.out.println(res32);    // >>> OK

        Object res33 = jedis.jsonGet("doc", new Path2("$.a[?length(@) > 2]"));
        System.out.println(res33);    // >>> [[1,2,3],"abcd"]
```

##### JavaScript (Node.js)

```javascript
const res32 = await client.json.set('doc', '$', { a: [[1, 2, 3], [1], 'abcd', 'x'] });
console.log(res32); // >>> OK

const res33 = await client.json.get('doc', { path: '$.a[?length(@) > 2]' });
console.log(res33); // >>> [ [ 1, 2, 3 ], 'abcd' ]
```

##### PHP

```php
        $res32 = $this->redis->jsonset('doc', '$', json_encode(
            ["a" => [[1, 2, 3], [1], "abcd", "x"]]
        ));
        echo $res32 . PHP_EOL; // >>> OK

        $res33 = $this->redis->jsonget('doc', '', '', '', '$.a[?length(@) > 2]');
        echo $res33 . PHP_EOL; // >>> [[1,2,3],"abcd"]
```

##### Python

```python
res1 = r.json().set("doc", "$", {"a": [[1, 2, 3], [1], "abcd", "x"]})
print(res1)
# >>> True

res2 = r.json().get("doc", "$.a[?length(@) > 2]")
print(res2)
# >>> [[1, 2, 3], 'abcd']
```

##### Ruby

```ruby
res1 = r.json_set('doc', '$', { 'a' => [[1, 2, 3], [1], 'abcd', 'x'] })
puts res1 # >>> OK

res2 = r.json_get('doc', '$.a[?length(@) > 2]')
p res2 # >>> [[1, 2, 3], "abcd"]
```

##### Rust (Asynchronous)

```rust
        let _: bool = match r.json_set("doc", "$", &json!({"a":[[1,2,3],[1],"abcd","x"]})).await {
            Ok(v) => v,
            Err(e) => {
                println!("Error setting doc: {e}");
                return;
            }
        };

        match r.json_get("doc", "$.a[?length(@) > 2]").await {
            Ok(res1) => {
                let res1: String = res1;
                println!("{res1}");    // >>> [[1,2,3],"abcd"]
            },
            Err(e) => {
                println!("Error getting doc: {e}");
                return;
            }
        }

```

##### Rust (Synchronous)

```rust
        match r.json_set("doc", "$", &json!({"a":[[1,2,3],[1],"abcd","x"]})) {
            Ok(res32) => {
                let res32: bool = res32;
                println!("{}", if res32 { "OK" } else { "(nil)" });    // >>> OK
            },
            Err(e) => {
                println!("Error setting doc: {e}");
                return;
            }
        }

        match r.json_get("doc", "$.a[?length(@) > 2]") {
            Ok(res33) => {
                let res33: String = res33;
                println!("{res33}");    // >>> [[1,2,3],"abcd"]
            },
            Err(e) => {
                println!("Error getting doc: {e}");
                return;
            }
        }

```



#### `count()`

Trả về số node được chọn bởi một truy vấn. Một path không tồn tại được tính là `0`; một node đơn được tính là `1`.

*Hàm count: Dùng count() để lấy số node được chọn bởi một truy vấn*

**Độ khó (Difficulty):** Nâng cao (Advanced)

**Lệnh (Commands):** JSON.SET, JSON.GET

**Độ phức tạp (Complexity):**
- JSON.SET: O(M+N)
- JSON.GET: O(N)

**Có sẵn trong (Available in):** Redis CLI, C#, Go, Java (Asynchronous - Lettuce), Java (Reactive - Lettuce), Java (Synchronous - Jedis), JavaScript (Node.js), PHP, Python, Ruby, Rust (Asynchronous), Rust (Synchronous)

##### Redis CLI

```
> JSON.SET doc $ '[{"a":1,"b":2,"c":3},{"a":1}]'
OK
> JSON.GET doc '$[?count(@.*) == 3]'
"[{\"a\":1,\"b\":2,\"c\":3}]"
```

##### C#

```csharp
        bool res34 = db.JSON().Set("doc", "$", "[{\"a\":1,\"b\":2,\"c\":3},{\"a\":1}]");
        Console.WriteLine(res34);   // >>> True

        RedisResult res35 = db.JSON().Get("doc", path: "$[?count(@.*) == 3]");
        Console.WriteLine(res35);   // >>> [{"a":1,"b":2,"c":3}]
```

##### Go

```go
	res1, err := rdb.JSONSet(ctx, "doc", "$",
		`[{"a":1,"b":2,"c":3},{"a":1}]`,
	).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res1) // >>> OK

	res2, err := rdb.JSONGet(ctx, "doc", `$[?count(@.*) == 3]`).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res2) // >>> [{"a":1,"b":2,"c":3}]
```

##### Java (Asynchronous - Lettuce)

```java
            CompletableFuture<Void> funcCountExample = asyncCommands
                    .jsonSet("doc", JsonPath.ROOT_PATH,
                            parser.createJsonValue("[{\"a\":1,\"b\":2,\"c\":3},{\"a\":1}]"))
                    .thenCompose(res1 -> {
                        System.out.println(res1); // >>> OK
                        return asyncCommands.jsonGet("doc", JsonPath.of("$[?count(@.*) == 3]"));
                    }).thenAccept(res2 -> {
                        System.out.println(res2); // >>> [[{"a":1,"b":2,"c":3}]]
                    }).toCompletableFuture();
```

##### Java (Reactive - Lettuce)

```java
            Mono<Void> funcCountExample = reactiveCommands
                    .jsonSet("doc", JsonPath.ROOT_PATH, parser.createJsonValue("[{\"a\":1,\"b\":2,\"c\":3},{\"a\":1}]"))
                    .doOnNext(res1 -> {
                        System.out.println(res1); // >>> OK
                    })
                    .flatMap(res1 -> reactiveCommands.jsonGet("doc", JsonPath.of("$[?count(@.*) == 3]")).collectList())
                    .doOnNext(res2 -> {
                        System.out.println(res2); // >>> [[{"a":1,"b":2,"c":3}]]
                    })
                    .then();
```

##### Java (Synchronous - Jedis)

```java
        String res34 = jedis.jsonSet("doc", new Path2("$"), "[{\"a\":1,\"b\":2,\"c\":3},{\"a\":1}]");
        System.out.println(res34);    // >>> OK

        Object res35 = jedis.jsonGet("doc", new Path2("$[?count(@.*) == 3]"));
        System.out.println(res35);    // >>> [{"a":1,"b":2,"c":3}]
```

##### JavaScript (Node.js)

```javascript
const res34 = await client.json.set('doc', '$', [{ a: 1, b: 2, c: 3 }, { a: 1 }]);
console.log(res34); // >>> OK

const res35 = await client.json.get('doc', { path: '$[?count(@.*) == 3]' });
console.log(res35); // >>> [ { a: 1, b: 2, c: 3 } ]
```

##### PHP

```php
        $res34 = $this->redis->jsonset('doc', '$', json_encode(
            [["a" => 1, "b" => 2, "c" => 3], ["a" => 1]]
        ));
        echo $res34 . PHP_EOL; // >>> OK

        $res35 = $this->redis->jsonget('doc', '', '', '', '$[?count(@.*) == 3]');
        echo $res35 . PHP_EOL; // >>> [{"a":1,"b":2,"c":3}]
```

##### Python

```python
res1 = r.json().set("doc", "$", [{"a": 1, "b": 2, "c": 3}, {"a": 1}])
print(res1)
# >>> True

res2 = r.json().get("doc", "$[?count(@.*) == 3]")
print(res2)
# >>> [{'a': 1, 'b': 2, 'c': 3}]
```

##### Ruby

```ruby
res1 = r.json_set('doc', '$', [{ 'a' => 1, 'b' => 2, 'c' => 3 }, { 'a' => 1 }])
puts res1 # >>> OK

res2 = r.json_get('doc', '$[?count(@.*) == 3]')
p res2 # >>> [{"a"=>1, "b"=>2, "c"=>3}]
```

##### Rust (Asynchronous)

```rust
        let _: bool = match r.json_set("doc", "$", &json!([{"a":1,"b":2,"c":3},{"a":1}])).await {
            Ok(v) => v,
            Err(e) => {
                println!("Error setting doc: {e}");
                return;
            }
        };

        match r.json_get("doc", "$[?count(@.*) == 3]").await {
            Ok(res1) => {
                let res1: String = res1;
                println!("{res1}");    // >>> [{"a":1,"b":2,"c":3}]
            },
            Err(e) => {
                println!("Error getting doc: {e}");
                return;
            }
        }

```

##### Rust (Synchronous)

```rust
        match r.json_set("doc", "$", &json!([{"a":1,"b":2,"c":3},{"a":1}])) {
            Ok(res34) => {
                let res34: bool = res34;
                println!("{}", if res34 { "OK" } else { "(nil)" });    // >>> OK
            },
            Err(e) => {
                println!("Error setting doc: {e}");
                return;
            }
        }

        match r.json_get("doc", "$[?count(@.*) == 3]") {
            Ok(res35) => {
                let res35: String = res35;
                println!("{res35}");    // >>> [{"a":1,"b":2,"c":3}]
            },
            Err(e) => {
                println!("Error getting doc: {e}");
                return;
            }
        }

```



#### `value()`

Trả về giá trị của một truy vấn chọn đúng một node. Một truy vấn chọn không node nào hoặc nhiều hơn một node sẽ cho ra *Nothing*.

*Hàm value: Dùng value() để lấy giá trị của một truy vấn chọn đúng một node*

**Độ khó (Difficulty):** Nâng cao (Advanced)

**Lệnh (Commands):** JSON.SET, JSON.GET

**Độ phức tạp (Complexity):**
- JSON.SET: O(M+N)
- JSON.GET: O(N)

**Có sẵn trong (Available in):** Redis CLI, C#, Go, Java (Asynchronous - Lettuce), Java (Reactive - Lettuce), Java (Synchronous - Jedis), JavaScript (Node.js), PHP, Python, Ruby, Rust (Asynchronous), Rust (Synchronous)

##### Redis CLI

```
> JSON.SET doc $ '[{"a":1},{"a":2}]'
OK
> JSON.GET doc '$[?value(@.a) == 1]'
"[{\"a\":1}]"
```

##### C#

```csharp
        bool res36 = db.JSON().Set("doc", "$", "[{\"a\":1},{\"a\":2}]");
        Console.WriteLine(res36);   // >>> True

        RedisResult res37 = db.JSON().Get("doc", path: "$[?value(@.a) == 1]");
        Console.WriteLine(res37);   // >>> [{"a":1}]
```

##### Go

```go
	res1, err := rdb.JSONSet(ctx, "doc", "$",
		`[{"a":1},{"a":2}]`,
	).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res1) // >>> OK

	res2, err := rdb.JSONGet(ctx, "doc", `$[?value(@.a) == 1]`).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res2) // >>> [{"a":1}]
```

##### Java (Asynchronous - Lettuce)

```java
            CompletableFuture<Void> funcValueExample = asyncCommands
                    .jsonSet("doc", JsonPath.ROOT_PATH, parser.createJsonValue("[{\"a\":1},{\"a\":2}]"))
                    .thenCompose(res1 -> {
                        System.out.println(res1); // >>> OK
                        return asyncCommands.jsonGet("doc", JsonPath.of("$[?value(@.a) == 1]"));
                    }).thenAccept(res2 -> {
                        System.out.println(res2); // >>> [[{"a":1}]]
                    }).toCompletableFuture();
```

##### Java (Reactive - Lettuce)

```java
            Mono<Void> funcValueExample = reactiveCommands
                    .jsonSet("doc", JsonPath.ROOT_PATH, parser.createJsonValue("[{\"a\":1},{\"a\":2}]"))
                    .doOnNext(res1 -> {
                        System.out.println(res1); // >>> OK
                    })
                    .flatMap(res1 -> reactiveCommands.jsonGet("doc", JsonPath.of("$[?value(@.a) == 1]")).collectList())
                    .doOnNext(res2 -> {
                        System.out.println(res2); // >>> [[{"a":1}]]
                    })
                    .then();
```

##### Java (Synchronous - Jedis)

```java
        String res36 = jedis.jsonSet("doc", new Path2("$"), "[{\"a\":1},{\"a\":2}]");
        System.out.println(res36);    // >>> OK

        Object res37 = jedis.jsonGet("doc", new Path2("$[?value(@.a) == 1]"));
        System.out.println(res37);    // >>> [{"a":1}]
```

##### JavaScript (Node.js)

```javascript
const res36 = await client.json.set('doc', '$', [{ a: 1 }, { a: 2 }]);
console.log(res36); // >>> OK

const res37 = await client.json.get('doc', { path: '$[?value(@.a) == 1]' });
console.log(res37); // >>> [ { a: 1 } ]
```

##### PHP

```php
        $res36 = $this->redis->jsonset('doc', '$', json_encode(
            [["a" => 1], ["a" => 2]]
        ));
        echo $res36 . PHP_EOL; // >>> OK

        $res37 = $this->redis->jsonget('doc', '', '', '', '$[?value(@.a) == 1]');
        echo $res37 . PHP_EOL; // >>> [{"a":1}]
```

##### Python

```python
res1 = r.json().set("doc", "$", [{"a": 1}, {"a": 2}])
print(res1)
# >>> True

res2 = r.json().get("doc", "$[?value(@.a) == 1]")
print(res2)
# >>> [{'a': 1}]
```

##### Ruby

```ruby
res1 = r.json_set('doc', '$', [{ 'a' => 1 }, { 'a' => 2 }])
puts res1 # >>> OK

res2 = r.json_get('doc', '$[?value(@.a) == 1]')
p res2 # >>> [{"a"=>1}]
```

##### Rust (Asynchronous)

```rust
        let _: bool = match r.json_set("doc", "$", &json!([{"a":1},{"a":2}])).await {
            Ok(v) => v,
            Err(e) => {
                println!("Error setting doc: {e}");
                return;
            }
        };

        match r.json_get("doc", "$[?value(@.a) == 1]").await {
            Ok(res1) => {
                let res1: String = res1;
                println!("{res1}");    // >>> [{"a":1}]
            },
            Err(e) => {
                println!("Error getting doc: {e}");
                return;
            }
        }

```

##### Rust (Synchronous)

```rust
        match r.json_set("doc", "$", &json!([{"a":1},{"a":2}])) {
            Ok(res36) => {
                let res36: bool = res36;
                println!("{}", if res36 { "OK" } else { "(nil)" });    // >>> OK
            },
            Err(e) => {
                println!("Error setting doc: {e}");
                return;
            }
        }

        match r.json_get("doc", "$[?value(@.a) == 1]") {
            Ok(res37) => {
                let res37: String = res37;
                println!("{res37}");    // >>> [{"a":1}]
            },
            Err(e) => {
                println!("Error getting doc: {e}");
                return;
            }
        }

```



#### `keys()`

Trả về tên các member của một object dưới dạng danh sách string, giống như toán tử `~`. Khác với `~`, `keys()` có thể kết hợp và nối chuỗi với các hàm khác.

*Hàm keys: Dùng keys() để lấy tên các member của một object dưới dạng danh sách string có thể kết hợp và nối chuỗi*

**Độ khó (Difficulty):** Nâng cao (Advanced)

**Lệnh (Commands):** JSON.SET, JSON.GET

**Độ phức tạp (Complexity):**
- JSON.SET: O(M+N)
- JSON.GET: O(N)

**Có sẵn trong (Available in):** Redis CLI, C#, Go, Java (Asynchronous - Lettuce), Java (Reactive - Lettuce), Java (Synchronous - Jedis), JavaScript (Node.js), PHP, Python, Ruby, Rust (Asynchronous), Rust (Synchronous)

##### Redis CLI

```
> JSON.SET doc $ '{"obj":{"x":1,"y":2}}'
OK
> JSON.GET doc '$.obj.keys()'
"[\"x\",\"y\"]"
> JSON.GET doc '$.obj.keys().count()'
"[2]"
```

##### C#

```csharp
        bool res38 = db.JSON().Set("doc", "$", "{\"obj\":{\"x\":1,\"y\":2}}");
        Console.WriteLine(res38);   // >>> True

        RedisResult res39 = db.JSON().Get("doc", path: "$.obj.keys()");
        Console.WriteLine(res39);   // >>> ["x","y"]

        RedisResult res40 = db.JSON().Get("doc", path: "$.obj.keys().count()");
        Console.WriteLine(res40);   // >>> [2]
```

##### Go

```go
	res1, err := rdb.JSONSet(ctx, "doc", "$",
		`{"obj":{"x":1,"y":2}}`,
	).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res1) // >>> OK

	res2, err := rdb.JSONGet(ctx, "doc", `$.obj.keys()`).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res2) // >>> ["x","y"]

	res3, err := rdb.JSONGet(ctx, "doc", `$.obj.keys().count()`).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res3) // >>> [2]
```

##### Java (Asynchronous - Lettuce)

```java
            CompletableFuture<Void> funcKeysExample = asyncCommands
                    .jsonSet("doc", JsonPath.ROOT_PATH, parser.createJsonValue("{\"obj\":{\"x\":1,\"y\":2}}"))
                    .thenCompose(res1 -> {
                        System.out.println(res1); // >>> OK
                        return asyncCommands.jsonGet("doc", JsonPath.of("$.obj.keys()"));
                    }).thenCompose(res2 -> {
                        System.out.println(res2); // >>> [["x","y"]]
                        return asyncCommands.jsonGet("doc", JsonPath.of("$.obj.keys().count()"));
                    }).thenAccept(res3 -> {
                        System.out.println(res3); // >>> [[2]]
                    }).toCompletableFuture();
```

##### Java (Reactive - Lettuce)

```java
            Mono<Void> funcKeysExample = reactiveCommands
                    .jsonSet("doc", JsonPath.ROOT_PATH, parser.createJsonValue("{\"obj\":{\"x\":1,\"y\":2}}"))
                    .doOnNext(res1 -> {
                        System.out.println(res1); // >>> OK
                    })
                    .flatMap(res1 -> reactiveCommands.jsonGet("doc", JsonPath.of("$.obj.keys()")).collectList())
                    .doOnNext(res2 -> {
                        System.out.println(res2); // >>> [["x","y"]]
                    })
                    .flatMap(res2 -> reactiveCommands.jsonGet("doc", JsonPath.of("$.obj.keys().count()")).collectList())
                    .doOnNext(res3 -> {
                        System.out.println(res3); // >>> [[2]]
                    })
                    .then();
```

##### Java (Synchronous - Jedis)

```java
        String res38 = jedis.jsonSet("doc", new Path2("$"), "{\"obj\":{\"x\":1,\"y\":2}}");
        System.out.println(res38);    // >>> OK

        Object res39 = jedis.jsonGet("doc", new Path2("$.obj.keys()"));
        System.out.println(res39);    // >>> ["x","y"]

        Object res40 = jedis.jsonGet("doc", new Path2("$.obj.keys().count()"));
        System.out.println(res40);    // >>> [2]
```

##### JavaScript (Node.js)

```javascript
const res38 = await client.json.set('doc', '$', { obj: { x: 1, y: 2 } });
console.log(res38); // >>> OK

const res39 = await client.json.get('doc', { path: '$.obj.keys()' });
console.log(res39); // >>> [ 'x', 'y' ]

const res40 = await client.json.get('doc', { path: '$.obj.keys().count()' });
console.log(res40); // >>> [ 2 ]
```

##### PHP

```php
        $res38 = $this->redis->jsonset('doc', '$', json_encode(
            ["obj" => ["x" => 1, "y" => 2]]
        ));
        echo $res38 . PHP_EOL; // >>> OK

        $res39 = $this->redis->jsonget('doc', '', '', '', '$.obj.keys()');
        echo $res39 . PHP_EOL; // >>> ["x","y"]

        $res40 = $this->redis->jsonget('doc', '', '', '', '$.obj.keys().count()');
        echo $res40 . PHP_EOL; // >>> [2]
```

##### Python

```python
res1 = r.json().set("doc", "$", {"obj": {"x": 1, "y": 2}})
print(res1)
# >>> True

res2 = r.json().get("doc", "$.obj.keys()")
print(res2)
# >>> ['x', 'y']

res3 = r.json().get("doc", "$.obj.keys().count()")
print(res3)
# >>> [2]
```

##### Ruby

```ruby
res1 = r.json_set('doc', '$', { 'obj' => { 'x' => 1, 'y' => 2 } })
puts res1 # >>> OK

res2 = r.json_get('doc', '$.obj.keys()')
p res2 # >>> ["x", "y"]

res3 = r.json_get('doc', '$.obj.keys().count()')
p res3 # >>> [2]
```

##### Rust (Asynchronous)

```rust
        let _: bool = match r.json_set("doc", "$", &json!({"obj":{"x":1,"y":2}})).await {
            Ok(v) => v,
            Err(e) => {
                println!("Error setting doc: {e}");
                return;
            }
        };

        match r.json_get("doc", "$.obj.keys()").await {
            Ok(res1) => {
                let res1: String = res1;
                println!("{res1}");    // >>> ["x","y"]
            },
            Err(e) => {
                println!("Error getting doc: {e}");
                return;
            }
        }

        match r.json_get("doc", "$.obj.keys().count()").await {
            Ok(res2) => {
                let res2: String = res2;
                println!("{res2}");    // >>> [2]
            },
            Err(e) => {
                println!("Error getting doc: {e}");
                return;
            }
        }

```

##### Rust (Synchronous)

```rust
        match r.json_set("doc", "$", &json!({"obj":{"x":1,"y":2}})) {
            Ok(res38) => {
                let res38: bool = res38;
                println!("{}", if res38 { "OK" } else { "(nil)" });    // >>> OK
            },
            Err(e) => {
                println!("Error setting doc: {e}");
                return;
            }
        }

        match r.json_get("doc", "$.obj.keys()") {
            Ok(res39) => {
                let res39: String = res39;
                println!("{res39}");    // >>> ["x","y"]
            },
            Err(e) => {
                println!("Error getting doc: {e}");
                return;
            }
        }

        match r.json_get("doc", "$.obj.keys().count()") {
            Ok(res40) => {
                let res40: String = res40;
                println!("{res40}");    // >>> [2]
            },
            Err(e) => {
                println!("Error getting doc: {e}");
                return;
            }
        }

```



#### `match()` and `search()`

Cả hai đều kiểm tra một string với một pattern regular expression ([RFC 9485](https://datatracker.ietf.org/doc/rfc9485/) I-Regexp). `match()` yêu cầu toàn bộ string phải khớp (anchored), trong khi `search()` khớp với bất kỳ chuỗi con nào (cùng hành vi với toán tử `=~`). Một pattern không hợp lệ sẽ không khớp.

*Hàm regex: Dùng match() cho phép khớp regular expression có neo (toàn bộ string) và search() cho phép khớp một phần*

**Độ khó (Difficulty):** Nâng cao (Advanced)

**Lệnh (Commands):** JSON.SET, JSON.GET

**Độ phức tạp (Complexity):**
- JSON.SET: O(M+N)
- JSON.GET: O(N)

**Có sẵn trong (Available in):** Redis CLI, C#, Go, Java (Asynchronous - Lettuce), Java (Reactive - Lettuce), Java (Synchronous - Jedis), JavaScript (Node.js), PHP, Python, Ruby, Rust (Asynchronous), Rust (Synchronous)

##### Redis CLI

```
> JSON.SET doc $ '{"a":["abc","xabc","a","b"]}'
OK
> JSON.GET doc '$.a[?match(@, "a.*")]'
"[\"abc\",\"a\"]"
> JSON.SET doc $ '{"a":["abc","xyz","b"]}'
OK
> JSON.GET doc '$.a[?search(@, "b")]'
"[\"abc\",\"b\"]"
```

##### C#

```csharp
        bool res41 = db.JSON().Set("doc", "$", "{\"a\":[\"abc\",\"xabc\",\"a\",\"b\"]}");
        Console.WriteLine(res41);   // >>> True

        RedisResult res42 = db.JSON().Get("doc", path: "$.a[?match(@, \"a.*\")]");
        Console.WriteLine(res42);   // >>> ["abc","a"]

        bool res43 = db.JSON().Set("doc", "$", "{\"a\":[\"abc\",\"xyz\",\"b\"]}");
        Console.WriteLine(res43);   // >>> True

        RedisResult res44 = db.JSON().Get("doc", path: "$.a[?search(@, \"b\")]");
        Console.WriteLine(res44);   // >>> ["abc","b"]
```

##### Go

```go
	res1, err := rdb.JSONSet(ctx, "doc", "$",
		`{"a":["abc","xabc","a","b"]}`,
	).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res1) // >>> OK

	res2, err := rdb.JSONGet(ctx, "doc", `$.a[?match(@, "a.*")]`).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res2) // >>> ["abc","a"]

	res3, err := rdb.JSONSet(ctx, "doc", "$",
		`{"a":["abc","xyz","b"]}`,
	).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res3) // >>> OK

	res4, err := rdb.JSONGet(ctx, "doc", `$.a[?search(@, "b")]`).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res4) // >>> ["abc","b"]
```

##### Java (Asynchronous - Lettuce)

```java
            CompletableFuture<Void> funcMatchSearchExample = asyncCommands
                    .jsonSet("doc", JsonPath.ROOT_PATH,
                            parser.createJsonValue("{\"a\":[\"abc\",\"xabc\",\"a\",\"b\"]}"))
                    .thenCompose(res1 -> {
                        System.out.println(res1); // >>> OK
                        return asyncCommands.jsonGet("doc", JsonPath.of("$.a[?match(@, \"a.*\")]"));
                    }).thenCompose(res2 -> {
                        System.out.println(res2); // >>> [["abc","a"]]
                        return asyncCommands.jsonSet("doc", JsonPath.ROOT_PATH,
                                parser.createJsonValue("{\"a\":[\"abc\",\"xyz\",\"b\"]}"));
                    }).thenCompose(res3 -> {
                        System.out.println(res3); // >>> OK
                        return asyncCommands.jsonGet("doc", JsonPath.of("$.a[?search(@, \"b\")]"));
                    }).thenAccept(res4 -> {
                        System.out.println(res4); // >>> [["abc","b"]]
                    }).toCompletableFuture();
```

##### Java (Reactive - Lettuce)

```java
            Mono<Void> funcMatchSearchExample = reactiveCommands
                    .jsonSet("doc", JsonPath.ROOT_PATH, parser.createJsonValue("{\"a\":[\"abc\",\"xabc\",\"a\",\"b\"]}"))
                    .doOnNext(res1 -> {
                        System.out.println(res1); // >>> OK
                    })
                    .flatMap(res1 -> reactiveCommands.jsonGet("doc", JsonPath.of("$.a[?match(@, \"a.*\")]")).collectList())
                    .doOnNext(res2 -> {
                        System.out.println(res2); // >>> [["abc","a"]]
                    })
                    .flatMap(res2 -> reactiveCommands.jsonSet("doc", JsonPath.ROOT_PATH,
                            parser.createJsonValue("{\"a\":[\"abc\",\"xyz\",\"b\"]}")))
                    .doOnNext(res3 -> {
                        System.out.println(res3); // >>> OK
                    })
                    .flatMap(res3 -> reactiveCommands.jsonGet("doc", JsonPath.of("$.a[?search(@, \"b\")]")).collectList())
                    .doOnNext(res4 -> {
                        System.out.println(res4); // >>> [["abc","b"]]
                    })
                    .then();
```

##### Java (Synchronous - Jedis)

```java
        String res41 = jedis.jsonSet("doc", new Path2("$"), "{\"a\":[\"abc\",\"xabc\",\"a\",\"b\"]}");
        System.out.println(res41);    // >>> OK

        Object res42 = jedis.jsonGet("doc", new Path2("$.a[?match(@, \"a.*\")]"));
        System.out.println(res42);    // >>> ["abc","a"]

        String res43 = jedis.jsonSet("doc", new Path2("$"), "{\"a\":[\"abc\",\"xyz\",\"b\"]}");
        System.out.println(res43);    // >>> OK

        Object res44 = jedis.jsonGet("doc", new Path2("$.a[?search(@, \"b\")]"));
        System.out.println(res44);    // >>> ["abc","b"]
```

##### JavaScript (Node.js)

```javascript
const res41 = await client.json.set('doc', '$', { a: ['abc', 'xabc', 'a', 'b'] });
console.log(res41); // >>> OK

const res42 = await client.json.get('doc', { path: '$.a[?match(@, "a.*")]' });
console.log(res42); // >>> [ 'abc', 'a' ]

const res43 = await client.json.set('doc', '$', { a: ['abc', 'xyz', 'b'] });
console.log(res43); // >>> OK

const res44 = await client.json.get('doc', { path: '$.a[?search(@, "b")]' });
console.log(res44); // >>> [ 'abc', 'b' ]
```

##### PHP

```php
        $res41 = $this->redis->jsonset('doc', '$', json_encode(
            ["a" => ["abc", "xabc", "a", "b"]]
        ));
        echo $res41 . PHP_EOL; // >>> OK

        $res42 = $this->redis->jsonget('doc', '', '', '', '$.a[?match(@, "a.*")]');
        echo $res42 . PHP_EOL; // >>> ["abc","a"]

        $res43 = $this->redis->jsonset('doc', '$', json_encode(
            ["a" => ["abc", "xyz", "b"]]
        ));
        echo $res43 . PHP_EOL; // >>> OK

        $res44 = $this->redis->jsonget('doc', '', '', '', '$.a[?search(@, "b")]');
        echo $res44 . PHP_EOL; // >>> ["abc","b"]
```

##### Python

```python
res1 = r.json().set("doc", "$", {"a": ["abc", "xabc", "a", "b"]})
print(res1)
# >>> True

res2 = r.json().get("doc", '$.a[?match(@, "a.*")]')
print(res2)
# >>> ['abc', 'a']

res3 = r.json().set("doc", "$", {"a": ["abc", "xyz", "b"]})
print(res3)
# >>> True

res4 = r.json().get("doc", '$.a[?search(@, "b")]')
print(res4)
# >>> ['abc', 'b']
```

##### Ruby

```ruby
res1 = r.json_set('doc', '$', { 'a' => ['abc', 'xabc', 'a', 'b'] })
puts res1 # >>> OK

res2 = r.json_get('doc', '$.a[?match(@, "a.*")]')
p res2 # >>> ["abc", "a"]

res3 = r.json_set('doc', '$', { 'a' => ['abc', 'xyz', 'b'] })
puts res3 # >>> OK

res4 = r.json_get('doc', '$.a[?search(@, "b")]')
p res4 # >>> ["abc", "b"]
```

##### Rust (Asynchronous)

```rust
        let _: bool = match r.json_set("doc", "$", &json!({"a":["abc","xabc","a","b"]})).await {
            Ok(v) => v,
            Err(e) => {
                println!("Error setting doc: {e}");
                return;
            }
        };

        match r.json_get("doc", r#"$.a[?match(@, "a.*")]"#).await {
            Ok(res1) => {
                let res1: String = res1;
                println!("{res1}");    // >>> ["abc","a"]
            },
            Err(e) => {
                println!("Error getting doc: {e}");
                return;
            }
        }

        let _: bool = match r.json_set("doc", "$", &json!({"a":["abc","xyz","b"]})).await {
            Ok(v) => v,
            Err(e) => {
                println!("Error setting doc: {e}");
                return;
            }
        };

        match r.json_get("doc", r#"$.a[?search(@, "b")]"#).await {
            Ok(res2) => {
                let res2: String = res2;
                println!("{res2}");    // >>> ["abc","b"]
            },
            Err(e) => {
                println!("Error getting doc: {e}");
                return;
            }
        }

```

##### Rust (Synchronous)

```rust
        match r.json_set("doc", "$", &json!({"a":["abc","xabc","a","b"]})) {
            Ok(res41) => {
                let res41: bool = res41;
                println!("{}", if res41 { "OK" } else { "(nil)" });    // >>> OK
            },
            Err(e) => {
                println!("Error setting doc: {e}");
                return;
            }
        }

        match r.json_get("doc", r#"$.a[?match(@, "a.*")]"#) {
            Ok(res42) => {
                let res42: String = res42;
                println!("{res42}");    // >>> ["abc","a"]
            },
            Err(e) => {
                println!("Error getting doc: {e}");
                return;
            }
        }

        match r.json_set("doc", "$", &json!({"a":["abc","xyz","b"]})) {
            Ok(res43) => {
                let res43: bool = res43;
                println!("{}", if res43 { "OK" } else { "(nil)" });    // >>> OK
            },
            Err(e) => {
                println!("Error setting doc: {e}");
                return;
            }
        }

        match r.json_get("doc", r#"$.a[?search(@, "b")]"#) {
            Ok(res44) => {
                let res44: String = res44;
                println!("{res44}");    // >>> ["abc","b"]
            },
            Err(e) => {
                println!("Error getting doc: {e}");
                return;
            }
        }

```



#### `concat()`

Nối các đối số string của nó thành một string duy nhất. Nó yêu cầu ít nhất một đối số, và bất kỳ đối số nào không phải string đều cho ra *Nothing*.

*Hàm concat: Dùng concat() để nối các đối số string thành một string duy nhất để so sánh trong một filter*

**Độ khó (Difficulty):** Nâng cao (Advanced)

**Lệnh (Commands):** JSON.SET, JSON.GET

**Độ phức tạp (Complexity):**
- JSON.SET: O(M+N)
- JSON.GET: O(N)

**Có sẵn trong (Available in):** Redis CLI, C#, Go, Java (Asynchronous - Lettuce), Java (Reactive - Lettuce), Java (Synchronous - Jedis), JavaScript (Node.js), PHP, Python, Ruby, Rust (Asynchronous), Rust (Synchronous)

##### Redis CLI

```
> JSON.SET doc $ '{"a":[{"x":"a","y":"b"},{"x":"a","y":"c"}]}'
OK
> JSON.GET doc '$.a[?concat(@.x, @.y) == "ab"]'
"[{\"x\":\"a\",\"y\":\"b\"}]"
```

##### C#

```csharp
        bool res45 = db.JSON().Set("doc", "$", "{\"a\":[{\"x\":\"a\",\"y\":\"b\"},{\"x\":\"a\",\"y\":\"c\"}]}");
        Console.WriteLine(res45);   // >>> True

        RedisResult res46 = db.JSON().Get("doc", path: "$.a[?concat(@.x, @.y) == \"ab\"]");
        Console.WriteLine(res46);   // >>> [{"x":"a","y":"b"}]
```

##### Go

```go
	res1, err := rdb.JSONSet(ctx, "doc", "$",
		`{"a":[{"x":"a","y":"b"},{"x":"a","y":"c"}]}`,
	).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res1) // >>> OK

	res2, err := rdb.JSONGet(ctx, "doc", `$.a[?concat(@.x, @.y) == "ab"]`).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res2) // >>> [{"x":"a","y":"b"}]
```

##### Java (Asynchronous - Lettuce)

```java
            CompletableFuture<Void> funcConcatExample = asyncCommands
                    .jsonSet("doc", JsonPath.ROOT_PATH,
                            parser.createJsonValue("{\"a\":[{\"x\":\"a\",\"y\":\"b\"},{\"x\":\"a\",\"y\":\"c\"}]}"))
                    .thenCompose(res1 -> {
                        System.out.println(res1); // >>> OK
                        return asyncCommands.jsonGet("doc", JsonPath.of("$.a[?concat(@.x, @.y) == \"ab\"]"));
                    }).thenAccept(res2 -> {
                        System.out.println(res2); // >>> [[{"x":"a","y":"b"}]]
                    }).toCompletableFuture();
```

##### Java (Reactive - Lettuce)

```java
            Mono<Void> funcConcatExample = reactiveCommands
                    .jsonSet("doc", JsonPath.ROOT_PATH,
                            parser.createJsonValue("{\"a\":[{\"x\":\"a\",\"y\":\"b\"},{\"x\":\"a\",\"y\":\"c\"}]}"))
                    .doOnNext(res1 -> {
                        System.out.println(res1); // >>> OK
                    })
                    .flatMap(res1 -> reactiveCommands.jsonGet("doc", JsonPath.of("$.a[?concat(@.x, @.y) == \"ab\"]"))
                            .collectList())
                    .doOnNext(res2 -> {
                        System.out.println(res2); // >>> [[{"x":"a","y":"b"}]]
                    })
                    .then();
```

##### Java (Synchronous - Jedis)

```java
        String res45 = jedis.jsonSet("doc", new Path2("$"), "{\"a\":[{\"x\":\"a\",\"y\":\"b\"},{\"x\":\"a\",\"y\":\"c\"}]}");
        System.out.println(res45);    // >>> OK

        Object res46 = jedis.jsonGet("doc", new Path2("$.a[?concat(@.x, @.y) == \"ab\"]"));
        System.out.println(res46);    // >>> [{"x":"a","y":"b"}]
```

##### JavaScript (Node.js)

```javascript
const res45 = await client.json.set('doc', '$', { a: [{ x: 'a', y: 'b' }, { x: 'a', y: 'c' }] });
console.log(res45); // >>> OK

const res46 = await client.json.get('doc', { path: '$.a[?concat(@.x, @.y) == "ab"]' });
console.log(res46); // >>> [ { x: 'a', y: 'b' } ]
```

##### PHP

```php
        $res45 = $this->redis->jsonset('doc', '$', json_encode(
            ["a" => [["x" => "a", "y" => "b"], ["x" => "a", "y" => "c"]]]
        ));
        echo $res45 . PHP_EOL; // >>> OK

        $res46 = $this->redis->jsonget('doc', '', '', '', '$.a[?concat(@.x, @.y) == "ab"]');
        echo $res46 . PHP_EOL; // >>> [{"x":"a","y":"b"}]
```

##### Python

```python
res1 = r.json().set("doc", "$", {"a": [{"x": "a", "y": "b"}, {"x": "a", "y": "c"}]})
print(res1)
# >>> True

res2 = r.json().get("doc", '$.a[?concat(@.x, @.y) == "ab"]')
print(res2)
# >>> [{'x': 'a', 'y': 'b'}]
```

##### Ruby

```ruby
res1 = r.json_set('doc', '$', { 'a' => [{ 'x' => 'a', 'y' => 'b' }, { 'x' => 'a', 'y' => 'c' }] })
puts res1 # >>> OK

res2 = r.json_get('doc', '$.a[?concat(@.x, @.y) == "ab"]')
p res2 # >>> [{"x"=>"a", "y"=>"b"}]
```

##### Rust (Asynchronous)

```rust
        let _: bool = match r.json_set(
            "doc",
            "$",
            &json!({"a":[{"x":"a","y":"b"},{"x":"a","y":"c"}]}),
        ).await {
            Ok(v) => v,
            Err(e) => {
                println!("Error setting doc: {e}");
                return;
            }
        };

        match r.json_get("doc", r#"$.a[?concat(@.x, @.y) == "ab"]"#).await {
            Ok(res1) => {
                let res1: String = res1;
                println!("{res1}");    // >>> [{"x":"a","y":"b"}]
            },
            Err(e) => {
                println!("Error getting doc: {e}");
                return;
            }
        }

```

##### Rust (Synchronous)

```rust
        match r.json_set("doc", "$", &json!({"a":[{"x":"a","y":"b"},{"x":"a","y":"c"}]})) {
            Ok(res45) => {
                let res45: bool = res45;
                println!("{}", if res45 { "OK" } else { "(nil)" });    // >>> OK
            },
            Err(e) => {
                println!("Error setting doc: {e}");
                return;
            }
        }

        match r.json_get("doc", r#"$.a[?concat(@.x, @.y) == "ab"]"#) {
            Ok(res46) => {
                let res46: String = res46;
                println!("{res46}");    // >>> [{"x":"a","y":"b"}]
            },
            Err(e) => {
                println!("Error getting doc: {e}");
                return;
            }
        }

```



#### `abs()`, `ceiling()`, and `floor()`

Thao tác trên một số: `abs()` trả về giá trị tuyệt đối, `ceiling()` làm tròn lên số nguyên gần nhất, và `floor()` làm tròn xuống. Một đối số số nguyên vẫn là số nguyên và một đối số số thực dấu phẩy động vẫn là số thực. Một kết quả tràn khỏi phạm vi số nguyên có dấu 64-bit sẽ cho ra *Nothing*.

*Hàm số: Dùng abs(), ceiling(), và floor() để biến đổi một số trước khi so sánh nó trong một filter*

**Độ khó (Difficulty):** Nâng cao (Advanced)

**Lệnh (Commands):** JSON.SET, JSON.GET

**Độ phức tạp (Complexity):**
- JSON.SET: O(M+N)
- JSON.GET: O(N)

**Có sẵn trong (Available in):** Redis CLI, C#, Go, Java (Asynchronous - Lettuce), Java (Reactive - Lettuce), Java (Synchronous - Jedis), JavaScript (Node.js), PHP, Python, Ruby, Rust (Asynchronous), Rust (Synchronous)

##### Redis CLI

```
> JSON.SET doc $ '{"a":[2.1,3.9,1.0]}'
OK
> JSON.GET doc '$.a[?ceiling(@) == 3]'
"[2.1]"
> JSON.SET doc $ '{"a":[2.1,2.9,3.5]}'
OK
> JSON.GET doc '$.a[?floor(@) == 2]'
"[2.1,2.9]"
> JSON.SET doc $ '{"a":[{"n":-5},{"n":5},{"n":-3}]}'
OK
> JSON.GET doc '$.a[?abs(@.n) == 5]'
"[{\"n\":-5},{\"n\":5}]"
```

##### C#

```csharp
        bool res47 = db.JSON().Set("doc", "$", "{\"a\":[2.1,3.9,1.0]}");
        Console.WriteLine(res47);   // >>> True

        RedisResult res48 = db.JSON().Get("doc", path: "$.a[?ceiling(@) == 3]");
        Console.WriteLine(res48);   // >>> [2.1]

        bool res49 = db.JSON().Set("doc", "$", "{\"a\":[2.1,2.9,3.5]}");
        Console.WriteLine(res49);   // >>> True

        RedisResult res50 = db.JSON().Get("doc", path: "$.a[?floor(@) == 2]");
        Console.WriteLine(res50);   // >>> [2.1,2.9]

        bool res51 = db.JSON().Set("doc", "$", "{\"a\":[{\"n\":-5},{\"n\":5},{\"n\":-3}]}");
        Console.WriteLine(res51);   // >>> True

        RedisResult res52 = db.JSON().Get("doc", path: "$.a[?abs(@.n) == 5]");
        Console.WriteLine(res52);   // >>> [{"n":-5},{"n":5}]
```

##### Go

```go
	res1, err := rdb.JSONSet(ctx, "doc", "$",
		`{"a":[2.1,3.9,1.0]}`,
	).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res1) // >>> OK

	res2, err := rdb.JSONGet(ctx, "doc", `$.a[?ceiling(@) == 3]`).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res2) // >>> [2.1]

	res3, err := rdb.JSONSet(ctx, "doc", "$",
		`{"a":[2.1,2.9,3.5]}`,
	).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res3) // >>> OK

	res4, err := rdb.JSONGet(ctx, "doc", `$.a[?floor(@) == 2]`).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res4) // >>> [2.1,2.9]

	res5, err := rdb.JSONSet(ctx, "doc", "$",
		`{"a":[{"n":-5},{"n":5},{"n":-3}]}`,
	).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res5) // >>> OK

	res6, err := rdb.JSONGet(ctx, "doc", `$.a[?abs(@.n) == 5]`).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res6) // >>> [{"n":-5},{"n":5}]
```

##### Java (Asynchronous - Lettuce)

```java
            CompletableFuture<Void> funcMathExample = asyncCommands
                    .jsonSet("doc", JsonPath.ROOT_PATH, parser.createJsonValue("{\"a\":[2.1,3.9,1.0]}"))
                    .thenCompose(res1 -> {
                        System.out.println(res1); // >>> OK
                        return asyncCommands.jsonGet("doc", JsonPath.of("$.a[?ceiling(@) == 3]"));
                    }).thenCompose(res2 -> {
                        System.out.println(res2); // >>> [[2.1]]
                        return asyncCommands.jsonSet("doc", JsonPath.ROOT_PATH,
                                parser.createJsonValue("{\"a\":[2.1,2.9,3.5]}"));
                    }).thenCompose(res3 -> {
                        System.out.println(res3); // >>> OK
                        return asyncCommands.jsonGet("doc", JsonPath.of("$.a[?floor(@) == 2]"));
                    }).thenCompose(res4 -> {
                        System.out.println(res4); // >>> [[2.1,2.9]]
                        return asyncCommands.jsonSet("doc", JsonPath.ROOT_PATH,
                                parser.createJsonValue("{\"a\":[{\"n\":-5},{\"n\":5},{\"n\":-3}]}"));
                    }).thenCompose(res5 -> {
                        System.out.println(res5); // >>> OK
                        return asyncCommands.jsonGet("doc", JsonPath.of("$.a[?abs(@.n) == 5]"));
                    }).thenAccept(res6 -> {
                        System.out.println(res6); // >>> [[{"n":-5},{"n":5}]]
                    }).toCompletableFuture();
```

##### Java (Reactive - Lettuce)

```java
            Mono<Void> funcMathExample = reactiveCommands
                    .jsonSet("doc", JsonPath.ROOT_PATH, parser.createJsonValue("{\"a\":[2.1,3.9,1.0]}"))
                    .doOnNext(res1 -> {
                        System.out.println(res1); // >>> OK
                    })
                    .flatMap(res1 -> reactiveCommands.jsonGet("doc", JsonPath.of("$.a[?ceiling(@) == 3]")).collectList())
                    .doOnNext(res2 -> {
                        System.out.println(res2); // >>> [[2.1]]
                    })
                    .flatMap(res2 -> reactiveCommands.jsonSet("doc", JsonPath.ROOT_PATH,
                            parser.createJsonValue("{\"a\":[2.1,2.9,3.5]}")))
                    .doOnNext(res3 -> {
                        System.out.println(res3); // >>> OK
                    })
                    .flatMap(res3 -> reactiveCommands.jsonGet("doc", JsonPath.of("$.a[?floor(@) == 2]")).collectList())
                    .doOnNext(res4 -> {
                        System.out.println(res4); // >>> [[2.1,2.9]]
                    })
                    .flatMap(res4 -> reactiveCommands.jsonSet("doc", JsonPath.ROOT_PATH,
                            parser.createJsonValue("{\"a\":[{\"n\":-5},{\"n\":5},{\"n\":-3}]}")))
                    .doOnNext(res5 -> {
                        System.out.println(res5); // >>> OK
                    })
                    .flatMap(res5 -> reactiveCommands.jsonGet("doc", JsonPath.of("$.a[?abs(@.n) == 5]")).collectList())
                    .doOnNext(res6 -> {
                        System.out.println(res6); // >>> [[{"n":-5},{"n":5}]]
                    })
                    .then();
```

##### Java (Synchronous - Jedis)

```java
        String res47 = jedis.jsonSet("doc", new Path2("$"), "{\"a\":[2.1,3.9,1.0]}");
        System.out.println(res47);    // >>> OK

        Object res48 = jedis.jsonGet("doc", new Path2("$.a[?ceiling(@) == 3]"));
        System.out.println(res48);    // >>> [2.1]

        String res49 = jedis.jsonSet("doc", new Path2("$"), "{\"a\":[2.1,2.9,3.5]}");
        System.out.println(res49);    // >>> OK

        Object res50 = jedis.jsonGet("doc", new Path2("$.a[?floor(@) == 2]"));
        System.out.println(res50);    // >>> [2.1,2.9]

        String res51 = jedis.jsonSet("doc", new Path2("$"), "{\"a\":[{\"n\":-5},{\"n\":5},{\"n\":-3}]}");
        System.out.println(res51);    // >>> OK

        Object res52 = jedis.jsonGet("doc", new Path2("$.a[?abs(@.n) == 5]"));
        System.out.println(res52);    // >>> [{"n":-5},{"n":5}]
```

##### JavaScript (Node.js)

```javascript
const res47 = await client.json.set('doc', '$', { a: [2.1, 3.9, 1.0] });
console.log(res47); // >>> OK

const res48 = await client.json.get('doc', { path: '$.a[?ceiling(@) == 3]' });
console.log(res48); // >>> [ 2.1 ]

const res49 = await client.json.set('doc', '$', { a: [2.1, 2.9, 3.5] });
console.log(res49); // >>> OK

const res50 = await client.json.get('doc', { path: '$.a[?floor(@) == 2]' });
console.log(res50); // >>> [ 2.1, 2.9 ]

const res51 = await client.json.set('doc', '$', { a: [{ n: -5 }, { n: 5 }, { n: -3 }] });
console.log(res51); // >>> OK

const res52 = await client.json.get('doc', { path: '$.a[?abs(@.n) == 5]' });
console.log(res52); // >>> [ { n: -5 }, { n: 5 } ]
```

##### PHP

```php
        $res47 = $this->redis->jsonset('doc', '$', json_encode(
            ["a" => [2.1, 3.9, 1.0]]
        ));
        echo $res47 . PHP_EOL; // >>> OK

        $res48 = $this->redis->jsonget('doc', '', '', '', '$.a[?ceiling(@) == 3]');
        echo $res48 . PHP_EOL; // >>> [2.1]

        $res49 = $this->redis->jsonset('doc', '$', json_encode(
            ["a" => [2.1, 2.9, 3.5]]
        ));
        echo $res49 . PHP_EOL; // >>> OK

        $res50 = $this->redis->jsonget('doc', '', '', '', '$.a[?floor(@) == 2]');
        echo $res50 . PHP_EOL; // >>> [2.1,2.9]

        $res51 = $this->redis->jsonset('doc', '$', json_encode(
            ["a" => [["n" => -5], ["n" => 5], ["n" => -3]]]
        ));
        echo $res51 . PHP_EOL; // >>> OK

        $res52 = $this->redis->jsonget('doc', '', '', '', '$.a[?abs(@.n) == 5]');
        echo $res52 . PHP_EOL; // >>> [{"n":-5},{"n":5}]
```

##### Python

```python
res1 = r.json().set("doc", "$", {"a": [2.1, 3.9, 1.0]})
print(res1)
# >>> True

res2 = r.json().get("doc", "$.a[?ceiling(@) == 3]")
print(res2)
# >>> [2.1]

res3 = r.json().set("doc", "$", {"a": [2.1, 2.9, 3.5]})
print(res3)
# >>> True

res4 = r.json().get("doc", "$.a[?floor(@) == 2]")
print(res4)
# >>> [2.1, 2.9]

res5 = r.json().set("doc", "$", {"a": [{"n": -5}, {"n": 5}, {"n": -3}]})
print(res5)
# >>> True

res6 = r.json().get("doc", "$.a[?abs(@.n) == 5]")
print(res6)
# >>> [{'n': -5}, {'n': 5}]
```

##### Ruby

```ruby
res1 = r.json_set('doc', '$', { 'a' => [2.1, 3.9, 1.0] })
puts res1 # >>> OK

res2 = r.json_get('doc', '$.a[?ceiling(@) == 3]')
p res2 # >>> [2.1]

res3 = r.json_set('doc', '$', { 'a' => [2.1, 2.9, 3.5] })
puts res3 # >>> OK

res4 = r.json_get('doc', '$.a[?floor(@) == 2]')
p res4 # >>> [2.1, 2.9]

res5 = r.json_set('doc', '$', { 'a' => [{ 'n' => -5 }, { 'n' => 5 }, { 'n' => -3 }] })
puts res5 # >>> OK

res6 = r.json_get('doc', '$.a[?abs(@.n) == 5]')
p res6 # >>> [{"n"=>-5}, {"n"=>5}]
```

##### Rust (Asynchronous)

```rust
        let _: bool = match r.json_set("doc", "$", &json!({"a":[2.1,3.9,1.0]})).await {
            Ok(v) => v,
            Err(e) => {
                println!("Error setting doc: {e}");
                return;
            }
        };

        match r.json_get("doc", "$.a[?ceiling(@) == 3]").await {
            Ok(res1) => {
                let res1: String = res1;
                println!("{res1}");    // >>> [2.1]
            },
            Err(e) => {
                println!("Error getting doc: {e}");
                return;
            }
        }

        let _: bool = match r.json_set("doc", "$", &json!({"a":[2.1,2.9,3.5]})).await {
            Ok(v) => v,
            Err(e) => {
                println!("Error setting doc: {e}");
                return;
            }
        };

        match r.json_get("doc", "$.a[?floor(@) == 2]").await {
            Ok(res2) => {
                let res2: String = res2;
                println!("{res2}");    // >>> [2.1,2.9]
            },
            Err(e) => {
                println!("Error getting doc: {e}");
                return;
            }
        }

        let _: bool = match r.json_set(
            "doc",
            "$",
            &json!({"a":[{"n":-5},{"n":5},{"n":-3}]}),
        ).await {
            Ok(v) => v,
            Err(e) => {
                println!("Error setting doc: {e}");
                return;
            }
        };

        match r.json_get("doc", "$.a[?abs(@.n) == 5]").await {
            Ok(res3) => {
                let res3: String = res3;
                println!("{res3}");    // >>> [{"n":-5},{"n":5}]
            },
            Err(e) => {
                println!("Error getting doc: {e}");
                return;
            }
        }

```

##### Rust (Synchronous)

```rust
        match r.json_set("doc", "$", &json!({"a":[2.1,3.9,1.0]})) {
            Ok(res47) => {
                let res47: bool = res47;
                println!("{}", if res47 { "OK" } else { "(nil)" });    // >>> OK
            },
            Err(e) => {
                println!("Error setting doc: {e}");
                return;
            }
        }

        match r.json_get("doc", "$.a[?ceiling(@) == 3]") {
            Ok(res48) => {
                let res48: String = res48;
                println!("{res48}");    // >>> [2.1]
            },
            Err(e) => {
                println!("Error getting doc: {e}");
                return;
            }
        }

        match r.json_set("doc", "$", &json!({"a":[2.1,2.9,3.5]})) {
            Ok(res49) => {
                let res49: bool = res49;
                println!("{}", if res49 { "OK" } else { "(nil)" });    // >>> OK
            },
            Err(e) => {
                println!("Error setting doc: {e}");
                return;
            }
        }

        match r.json_get("doc", "$.a[?floor(@) == 2]") {
            Ok(res50) => {
                let res50: String = res50;
                println!("{res50}");    // >>> [2.1,2.9]
            },
            Err(e) => {
                println!("Error getting doc: {e}");
                return;
            }
        }

        match r.json_set("doc", "$", &json!({"a":[{"n":-5},{"n":5},{"n":-3}]})) {
            Ok(res51) => {
                let res51: bool = res51;
                println!("{}", if res51 { "OK" } else { "(nil)" });    // >>> OK
            },
            Err(e) => {
                println!("Error setting doc: {e}");
                return;
            }
        }

        match r.json_get("doc", "$.a[?abs(@.n) == 5]") {
            Ok(res52) => {
                let res52: String = res52;
                println!("{res52}");    // >>> [{"n":-5},{"n":5}]
            },
            Err(e) => {
                println!("Error getting doc: {e}");
                return;
            }
        }

```



#### `first()`, `last()`, and `index()`

`first(array)` và `last(array)` trả về phần tử đầu tiên và cuối cùng của một mảng. `index(array, n)` trả về phần tử tại chỉ số `n`; một `n` âm được đếm từ cuối, một `n` có phần thập phân bị cắt về phía 0, và một chỉ số ngoài phạm vi cho ra *Nothing*.

*Hàm truy cập mảng: Dùng first(), last(), và index() để chọn ra một phần tử từ một mảng trước khi so sánh nó*

**Độ khó (Difficulty):** Nâng cao (Advanced)

**Lệnh (Commands):** JSON.SET, JSON.GET

**Độ phức tạp (Complexity):**
- JSON.SET: O(M+N)
- JSON.GET: O(N)

**Có sẵn trong (Available in):** Redis CLI, C#, Go, Java (Asynchronous - Lettuce), Java (Reactive - Lettuce), Java (Synchronous - Jedis), JavaScript (Node.js), PHP, Python, Ruby, Rust (Asynchronous), Rust (Synchronous)

##### Redis CLI

```
> JSON.SET doc $ '{"a":[{"n":[1,2]},{"n":[9,8]}]}'
OK
> JSON.GET doc '$.a[?first(@.n) == 1]'
"[{\"n\":[1,2]}]"
> JSON.GET doc '$.a[?last(@.n) == 8]'
"[{\"n\":[9,8]}]"
> JSON.GET doc '$.a[?index(@.n, -1) == 2]'
"[{\"n\":[1,2]}]"
```

##### C#

```csharp
        bool res53 = db.JSON().Set("doc", "$", "{\"a\":[{\"n\":[1,2]},{\"n\":[9,8]}]}");
        Console.WriteLine(res53);   // >>> True

        RedisResult res54 = db.JSON().Get("doc", path: "$.a[?first(@.n) == 1]");
        Console.WriteLine(res54);   // >>> [{"n":[1,2]}]

        RedisResult res55 = db.JSON().Get("doc", path: "$.a[?last(@.n) == 8]");
        Console.WriteLine(res55);   // >>> [{"n":[9,8]}]

        RedisResult res56 = db.JSON().Get("doc", path: "$.a[?index(@.n, -1) == 2]");
        Console.WriteLine(res56);   // >>> [{"n":[1,2]}]
```

##### Go

```go
	res1, err := rdb.JSONSet(ctx, "doc", "$",
		`{"a":[{"n":[1,2]},{"n":[9,8]}]}`,
	).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res1) // >>> OK

	res2, err := rdb.JSONGet(ctx, "doc", `$.a[?first(@.n) == 1]`).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res2) // >>> [{"n":[1,2]}]

	res3, err := rdb.JSONGet(ctx, "doc", `$.a[?last(@.n) == 8]`).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res3) // >>> [{"n":[9,8]}]

	res4, err := rdb.JSONGet(ctx, "doc", `$.a[?index(@.n, -1) == 2]`).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res4) // >>> [{"n":[1,2]}]
```

##### Java (Asynchronous - Lettuce)

```java
            CompletableFuture<Void> funcArrayAccessExample = asyncCommands
                    .jsonSet("doc", JsonPath.ROOT_PATH,
                            parser.createJsonValue("{\"a\":[{\"n\":[1,2]},{\"n\":[9,8]}]}"))
                    .thenCompose(res1 -> {
                        System.out.println(res1); // >>> OK
                        return asyncCommands.jsonGet("doc", JsonPath.of("$.a[?first(@.n) == 1]"));
                    }).thenCompose(res2 -> {
                        System.out.println(res2); // >>> [[{"n":[1,2]}]]
                        return asyncCommands.jsonGet("doc", JsonPath.of("$.a[?last(@.n) == 8]"));
                    }).thenCompose(res3 -> {
                        System.out.println(res3); // >>> [[{"n":[9,8]}]]
                        return asyncCommands.jsonGet("doc", JsonPath.of("$.a[?index(@.n, -1) == 2]"));
                    }).thenAccept(res4 -> {
                        System.out.println(res4); // >>> [[{"n":[1,2]}]]
                    }).toCompletableFuture();
```

##### Java (Reactive - Lettuce)

```java
            Mono<Void> funcArrayAccessExample = reactiveCommands
                    .jsonSet("doc", JsonPath.ROOT_PATH, parser.createJsonValue("{\"a\":[{\"n\":[1,2]},{\"n\":[9,8]}]}"))
                    .doOnNext(res1 -> {
                        System.out.println(res1); // >>> OK
                    })
                    .flatMap(res1 -> reactiveCommands.jsonGet("doc", JsonPath.of("$.a[?first(@.n) == 1]")).collectList())
                    .doOnNext(res2 -> {
                        System.out.println(res2); // >>> [[{"n":[1,2]}]]
                    })
                    .flatMap(res2 -> reactiveCommands.jsonGet("doc", JsonPath.of("$.a[?last(@.n) == 8]")).collectList())
                    .doOnNext(res3 -> {
                        System.out.println(res3); // >>> [[{"n":[9,8]}]]
                    })
                    .flatMap(res3 -> reactiveCommands.jsonGet("doc", JsonPath.of("$.a[?index(@.n, -1) == 2]")).collectList())
                    .doOnNext(res4 -> {
                        System.out.println(res4); // >>> [[{"n":[1,2]}]]
                    })
                    .then();
```

##### Java (Synchronous - Jedis)

```java
        String res53 = jedis.jsonSet("doc", new Path2("$"), "{\"a\":[{\"n\":[1,2]},{\"n\":[9,8]}]}");
        System.out.println(res53);    // >>> OK

        Object res54 = jedis.jsonGet("doc", new Path2("$.a[?first(@.n) == 1]"));
        System.out.println(res54);    // >>> [{"n":[1,2]}]

        Object res55 = jedis.jsonGet("doc", new Path2("$.a[?last(@.n) == 8]"));
        System.out.println(res55);    // >>> [{"n":[9,8]}]

        Object res56 = jedis.jsonGet("doc", new Path2("$.a[?index(@.n, -1) == 2]"));
        System.out.println(res56);    // >>> [{"n":[1,2]}]
```

##### JavaScript (Node.js)

```javascript
const res53 = await client.json.set('doc', '$', { a: [{ n: [1, 2] }, { n: [9, 8] }] });
console.log(res53); // >>> OK

const res54 = await client.json.get('doc', { path: '$.a[?first(@.n) == 1]' });
console.log(res54); // >>> [ { n: [ 1, 2 ] } ]

const res55 = await client.json.get('doc', { path: '$.a[?last(@.n) == 8]' });
console.log(res55); // >>> [ { n: [ 9, 8 ] } ]

const res56 = await client.json.get('doc', { path: '$.a[?index(@.n, -1) == 2]' });
console.log(res56); // >>> [ { n: [ 1, 2 ] } ]
```

##### PHP

```php
        $res53 = $this->redis->jsonset('doc', '$', json_encode(
            ["a" => [["n" => [1, 2]], ["n" => [9, 8]]]]
        ));
        echo $res53 . PHP_EOL; // >>> OK

        $res54 = $this->redis->jsonget('doc', '', '', '', '$.a[?first(@.n) == 1]');
        echo $res54 . PHP_EOL; // >>> [{"n":[1,2]}]

        $res55 = $this->redis->jsonget('doc', '', '', '', '$.a[?last(@.n) == 8]');
        echo $res55 . PHP_EOL; // >>> [{"n":[9,8]}]

        $res56 = $this->redis->jsonget('doc', '', '', '', '$.a[?index(@.n, -1) == 2]');
        echo $res56 . PHP_EOL; // >>> [{"n":[1,2]}]
```

##### Python

```python
res1 = r.json().set("doc", "$", {"a": [{"n": [1, 2]}, {"n": [9, 8]}]})
print(res1)
# >>> True

res2 = r.json().get("doc", "$.a[?first(@.n) == 1]")
print(res2)
# >>> [{'n': [1, 2]}]

res3 = r.json().get("doc", "$.a[?last(@.n) == 8]")
print(res3)
# >>> [{'n': [9, 8]}]

res4 = r.json().get("doc", "$.a[?index(@.n, -1) == 2]")
print(res4)
# >>> [{'n': [1, 2]}]
```

##### Ruby

```ruby
res1 = r.json_set('doc', '$', { 'a' => [{ 'n' => [1, 2] }, { 'n' => [9, 8] }] })
puts res1 # >>> OK

res2 = r.json_get('doc', '$.a[?first(@.n) == 1]')
p res2 # >>> [{"n"=>[1, 2]}]

res3 = r.json_get('doc', '$.a[?last(@.n) == 8]')
p res3 # >>> [{"n"=>[9, 8]}]

res4 = r.json_get('doc', '$.a[?index(@.n, -1) == 2]')
p res4 # >>> [{"n"=>[1, 2]}]
```

##### Rust (Asynchronous)

```rust
        let _: bool = match r.json_set(
            "doc",
            "$",
            &json!({"a":[{"n":[1,2]},{"n":[9,8]}]}),
        ).await {
            Ok(v) => v,
            Err(e) => {
                println!("Error setting doc: {e}");
                return;
            }
        };

        match r.json_get("doc", "$.a[?first(@.n) == 1]").await {
            Ok(res1) => {
                let res1: String = res1;
                println!("{res1}");    // >>> [{"n":[1,2]}]
            },
            Err(e) => {
                println!("Error getting doc: {e}");
                return;
            }
        }

        match r.json_get("doc", "$.a[?last(@.n) == 8]").await {
            Ok(res2) => {
                let res2: String = res2;
                println!("{res2}");    // >>> [{"n":[9,8]}]
            },
            Err(e) => {
                println!("Error getting doc: {e}");
                return;
            }
        }

        match r.json_get("doc", "$.a[?index(@.n, -1) == 2]").await {
            Ok(res3) => {
                let res3: String = res3;
                println!("{res3}");    // >>> [{"n":[1,2]}]
            },
            Err(e) => {
                println!("Error getting doc: {e}");
                return;
            }
        }

```

##### Rust (Synchronous)

```rust
        match r.json_set("doc", "$", &json!({"a":[{"n":[1,2]},{"n":[9,8]}]})) {
            Ok(res53) => {
                let res53: bool = res53;
                println!("{}", if res53 { "OK" } else { "(nil)" });    // >>> OK
            },
            Err(e) => {
                println!("Error setting doc: {e}");
                return;
            }
        }

        match r.json_get("doc", "$.a[?first(@.n) == 1]") {
            Ok(res54) => {
                let res54: String = res54;
                println!("{res54}");    // >>> [{"n":[1,2]}]
            },
            Err(e) => {
                println!("Error getting doc: {e}");
                return;
            }
        }

        match r.json_get("doc", "$.a[?last(@.n) == 8]") {
            Ok(res55) => {
                let res55: String = res55;
                println!("{res55}");    // >>> [{"n":[9,8]}]
            },
            Err(e) => {
                println!("Error getting doc: {e}");
                return;
            }
        }

        match r.json_get("doc", "$.a[?index(@.n, -1) == 2]") {
            Ok(res56) => {
                let res56: String = res56;
                println!("{res56}");    // >>> [{"n":[1,2]}]
            },
            Err(e) => {
                println!("Error getting doc: {e}");
                return;
            }
        }

```



#### `min()`, `max()`, `avg()`, `sum()`, and `stddev()`

Các hàm tổng hợp này thao tác trên một mảng số. `stddev()` trả về độ lệch chuẩn *tổng thể (population)* (chia cho N). Các hàm này là chặt chẽ (strict): một mảng chứa bất kỳ phần tử nào không phải số, hoặc một mảng rỗng, sẽ cho ra *Nothing* — các phần tử không bao giờ bị âm thầm bỏ qua.

*Hàm tổng hợp: Dùng sum(), avg(), min(), max(), hoặc stddev() để thu gọn một mảng số thành một giá trị duy nhất trong một filter*

**Độ khó (Difficulty):** Nâng cao (Advanced)

**Lệnh (Commands):** JSON.SET, JSON.GET

**Độ phức tạp (Complexity):**
- JSON.SET: O(M+N)
- JSON.GET: O(N)

**Có sẵn trong (Available in):** Redis CLI, C#, Go, Java (Asynchronous - Lettuce), Java (Reactive - Lettuce), Java (Synchronous - Jedis), JavaScript (Node.js), PHP, Python, Ruby, Rust (Asynchronous), Rust (Synchronous)

##### Redis CLI

```
> JSON.SET doc $ '{"a":[{"n":[3,1,2]},{"n":[5,6]}]}'
OK
> JSON.GET doc '$.a[?sum(@.n) == 6]'
"[{\"n\":[3,1,2]}]"
> JSON.GET doc '$.a[?avg(@.n) == 2]'
"[{\"n\":[3,1,2]}]"
```

##### C#

```csharp
        bool res57 = db.JSON().Set("doc", "$", "{\"a\":[{\"n\":[3,1,2]},{\"n\":[5,6]}]}");
        Console.WriteLine(res57);   // >>> True

        RedisResult res58 = db.JSON().Get("doc", path: "$.a[?sum(@.n) == 6]");
        Console.WriteLine(res58);   // >>> [{"n":[3,1,2]}]

        RedisResult res59 = db.JSON().Get("doc", path: "$.a[?avg(@.n) == 2]");
        Console.WriteLine(res59);   // >>> [{"n":[3,1,2]}]
```

##### Go

```go
	res1, err := rdb.JSONSet(ctx, "doc", "$",
		`{"a":[{"n":[3,1,2]},{"n":[5,6]}]}`,
	).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res1) // >>> OK

	res2, err := rdb.JSONGet(ctx, "doc", `$.a[?sum(@.n) == 6]`).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res2) // >>> [{"n":[3,1,2]}]

	res3, err := rdb.JSONGet(ctx, "doc", `$.a[?avg(@.n) == 2]`).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res3) // >>> [{"n":[3,1,2]}]
```

##### Java (Asynchronous - Lettuce)

```java
            CompletableFuture<Void> funcAggregateExample = asyncCommands
                    .jsonSet("doc", JsonPath.ROOT_PATH,
                            parser.createJsonValue("{\"a\":[{\"n\":[3,1,2]},{\"n\":[5,6]}]}"))
                    .thenCompose(res1 -> {
                        System.out.println(res1); // >>> OK
                        return asyncCommands.jsonGet("doc", JsonPath.of("$.a[?sum(@.n) == 6]"));
                    }).thenCompose(res2 -> {
                        System.out.println(res2); // >>> [[{"n":[3,1,2]}]]
                        return asyncCommands.jsonGet("doc", JsonPath.of("$.a[?avg(@.n) == 2]"));
                    }).thenAccept(res3 -> {
                        System.out.println(res3); // >>> [[{"n":[3,1,2]}]]
                    }).toCompletableFuture();
```

##### Java (Reactive - Lettuce)

```java
            Mono<Void> funcAggregateExample = reactiveCommands
                    .jsonSet("doc", JsonPath.ROOT_PATH,
                            parser.createJsonValue("{\"a\":[{\"n\":[3,1,2]},{\"n\":[5,6]}]}"))
                    .doOnNext(res1 -> {
                        System.out.println(res1); // >>> OK
                    })
                    .flatMap(res1 -> reactiveCommands.jsonGet("doc", JsonPath.of("$.a[?sum(@.n) == 6]")).collectList())
                    .doOnNext(res2 -> {
                        System.out.println(res2); // >>> [[{"n":[3,1,2]}]]
                    })
                    .flatMap(res2 -> reactiveCommands.jsonGet("doc", JsonPath.of("$.a[?avg(@.n) == 2]")).collectList())
                    .doOnNext(res3 -> {
                        System.out.println(res3); // >>> [[{"n":[3,1,2]}]]
                    })
                    .then();
```

##### Java (Synchronous - Jedis)

```java
        String res57 = jedis.jsonSet("doc", new Path2("$"), "{\"a\":[{\"n\":[3,1,2]},{\"n\":[5,6]}]}");
        System.out.println(res57);    // >>> OK

        Object res58 = jedis.jsonGet("doc", new Path2("$.a[?sum(@.n) == 6]"));
        System.out.println(res58);    // >>> [{"n":[3,1,2]}]

        Object res59 = jedis.jsonGet("doc", new Path2("$.a[?avg(@.n) == 2]"));
        System.out.println(res59);    // >>> [{"n":[3,1,2]}]
```

##### JavaScript (Node.js)

```javascript
const res57 = await client.json.set('doc', '$', { a: [{ n: [3, 1, 2] }, { n: [5, 6] }] });
console.log(res57); // >>> OK

const res58 = await client.json.get('doc', { path: '$.a[?sum(@.n) == 6]' });
console.log(res58); // >>> [ { n: [ 3, 1, 2 ] } ]

const res59 = await client.json.get('doc', { path: '$.a[?avg(@.n) == 2]' });
console.log(res59); // >>> [ { n: [ 3, 1, 2 ] } ]
```

##### PHP

```php
        $res57 = $this->redis->jsonset('doc', '$', json_encode(
            ["a" => [["n" => [3, 1, 2]], ["n" => [5, 6]]]]
        ));
        echo $res57 . PHP_EOL; // >>> OK

        $res58 = $this->redis->jsonget('doc', '', '', '', '$.a[?sum(@.n) == 6]');
        echo $res58 . PHP_EOL; // >>> [{"n":[3,1,2]}]

        $res59 = $this->redis->jsonget('doc', '', '', '', '$.a[?avg(@.n) == 2]');
        echo $res59 . PHP_EOL; // >>> [{"n":[3,1,2]}]
```

##### Python

```python
res1 = r.json().set("doc", "$", {"a": [{"n": [3, 1, 2]}, {"n": [5, 6]}]})
print(res1)
# >>> True

res2 = r.json().get("doc", "$.a[?sum(@.n) == 6]")
print(res2)
# >>> [{'n': [3, 1, 2]}]

res3 = r.json().get("doc", "$.a[?avg(@.n) == 2]")
print(res3)
# >>> [{'n': [3, 1, 2]}]
```

##### Ruby

```ruby
res1 = r.json_set('doc', '$', { 'a' => [{ 'n' => [3, 1, 2] }, { 'n' => [5, 6] }] })
puts res1 # >>> OK

res2 = r.json_get('doc', '$.a[?sum(@.n) == 6]')
p res2 # >>> [{"n"=>[3, 1, 2]}]

res3 = r.json_get('doc', '$.a[?avg(@.n) == 2]')
p res3 # >>> [{"n"=>[3, 1, 2]}]
```

##### Rust (Asynchronous)

```rust
        let _: bool = match r.json_set(
            "doc",
            "$",
            &json!({"a":[{"n":[3,1,2]},{"n":[5,6]}]}),
        ).await {
            Ok(v) => v,
            Err(e) => {
                println!("Error setting doc: {e}");
                return;
            }
        };

        match r.json_get("doc", "$.a[?sum(@.n) == 6]").await {
            Ok(res1) => {
                let res1: String = res1;
                println!("{res1}");    // >>> [{"n":[3,1,2]}]
            },
            Err(e) => {
                println!("Error getting doc: {e}");
                return;
            }
        }

        match r.json_get("doc", "$.a[?avg(@.n) == 2]").await {
            Ok(res2) => {
                let res2: String = res2;
                println!("{res2}");    // >>> [{"n":[3,1,2]}]
            },
            Err(e) => {
                println!("Error getting doc: {e}");
                return;
            }
        }

```

##### Rust (Synchronous)

```rust
        match r.json_set("doc", "$", &json!({"a":[{"n":[3,1,2]},{"n":[5,6]}]})) {
            Ok(res57) => {
                let res57: bool = res57;
                println!("{}", if res57 { "OK" } else { "(nil)" });    // >>> OK
            },
            Err(e) => {
                println!("Error setting doc: {e}");
                return;
            }
        }

        match r.json_get("doc", "$.a[?sum(@.n) == 6]") {
            Ok(res58) => {
                let res58: String = res58;
                println!("{res58}");    // >>> [{"n":[3,1,2]}]
            },
            Err(e) => {
                println!("Error getting doc: {e}");
                return;
            }
        }

        match r.json_get("doc", "$.a[?avg(@.n) == 2]") {
            Ok(res59) => {
                let res59: String = res59;
                println!("{res59}");    // >>> [{"n":[3,1,2]}]
            },
            Err(e) => {
                println!("Error getting doc: {e}");
                return;
            }
        }

```



#### `append()`

`append(value, ...)` trả về mảng được khớp với giá trị hoặc các giá trị cho trước được thêm vào sau các phần tử của nó. Đây là một projection chỉ đọc tại thời điểm truy vấn (read-only query-time projection) và không sửa đổi document đã lưu — để thay đổi một mảng tại chỗ, hãy dùng lệnh [`JSON.ARRAPPEND`](https://redis.io/docs/latest/commands/json.arrappend). Một đối số nhiều giá trị được thêm vào như một phần tử duy nhất (nó không được trải ra), và một đối số *Nothing* làm cho toàn bộ kết quả là *Nothing*.

*Hàm append: Dùng append() như một projection chỉ đọc tại thời điểm truy vấn, thêm một giá trị vào sau các phần tử của một mảng mà không sửa đổi document đã lưu*

**Độ khó (Difficulty):** Nâng cao (Advanced)

**Lệnh (Commands):** JSON.SET, JSON.GET

**Độ phức tạp (Complexity):**
- JSON.SET: O(M+N)
- JSON.GET: O(N)

**Có sẵn trong (Available in):** Redis CLI, C#, Go, Java (Asynchronous - Lettuce), Java (Reactive - Lettuce), Java (Synchronous - Jedis), JavaScript (Node.js), PHP, Python, Ruby, Rust (Asynchronous), Rust (Synchronous)

##### Redis CLI

```
> JSON.SET doc $ '{"arr":[1,2,3]}'
OK
> JSON.GET doc '$.arr.append(9)'
"[1,2,3,9]"
> JSON.SET doc $ '{"books":[{"t":"a","price":30},{"t":"b","price":5}]}'
OK
> JSON.GET doc '$.books[?(@.price >= 10)].append({"t":"X"})'
"[{\"t\":\"a\",\"price\":30},{\"t\":\"X\"}]"
```

##### C#

```csharp
        bool res60 = db.JSON().Set("doc", "$", "{\"arr\":[1,2,3]}");
        Console.WriteLine(res60);   // >>> True

        RedisResult res61 = db.JSON().Get("doc", path: "$.arr.append(9)");
        Console.WriteLine(res61);   // >>> [1,2,3,9]

        bool res62 = db.JSON().Set("doc", "$", "{\"books\":[{\"t\":\"a\",\"price\":30},{\"t\":\"b\",\"price\":5}]}");
        Console.WriteLine(res62);   // >>> True

        RedisResult res63 = db.JSON().Get("doc", path: "$.books[?(@.price >= 10)].append({\"t\":\"X\"})");
        Console.WriteLine(res63);   // >>> [{"t":"a","price":30},{"t":"X"}]
```

##### Go

```go
	res1, err := rdb.JSONSet(ctx, "doc", "$",
		`{"arr":[1,2,3]}`,
	).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res1) // >>> OK

	res2, err := rdb.JSONGet(ctx, "doc", `$.arr.append(9)`).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res2) // >>> [1,2,3,9]

	res3, err := rdb.JSONSet(ctx, "doc", "$",
		`{"books":[{"t":"a","price":30},{"t":"b","price":5}]}`,
	).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res3) // >>> OK

	res4, err := rdb.JSONGet(ctx, "doc", `$.books[?(@.price >= 10)].append({"t":"X"})`).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res4) // >>> [{"t":"a","price":30},{"t":"X"}]
```

##### Java (Asynchronous - Lettuce)

```java
            CompletableFuture<Void> funcAppendExample = asyncCommands
                    .jsonSet("doc", JsonPath.ROOT_PATH, parser.createJsonValue("{\"arr\":[1,2,3]}"))
                    .thenCompose(res1 -> {
                        System.out.println(res1); // >>> OK
                        return asyncCommands.jsonGet("doc", JsonPath.of("$.arr.append(9)"));
                    }).thenCompose(res2 -> {
                        System.out.println(res2); // >>> [[1,2,3,9]]
                        return asyncCommands.jsonSet("doc", JsonPath.ROOT_PATH,
                                parser.createJsonValue(
                                        "{\"books\":[{\"t\":\"a\",\"price\":30},{\"t\":\"b\",\"price\":5}]}"));
                    }).thenCompose(res3 -> {
                        System.out.println(res3); // >>> OK
                        return asyncCommands.jsonGet("doc",
                                JsonPath.of("$.books[?(@.price >= 10)].append({\"t\":\"X\"})"));
                    }).thenAccept(res4 -> {
                        System.out.println(res4); // >>> [[{"t":"a","price":30},{"t":"X"}]]
                    }).toCompletableFuture();
```

##### Java (Reactive - Lettuce)

```java
            Mono<Void> funcAppendExample = reactiveCommands
                    .jsonSet("doc", JsonPath.ROOT_PATH, parser.createJsonValue("{\"arr\":[1,2,3]}"))
                    .doOnNext(res1 -> {
                        System.out.println(res1); // >>> OK
                    })
                    .flatMap(res1 -> reactiveCommands.jsonGet("doc", JsonPath.of("$.arr.append(9)")).collectList())
                    .doOnNext(res2 -> {
                        System.out.println(res2); // >>> [[1,2,3,9]]
                    })
                    .flatMap(res2 -> reactiveCommands.jsonSet("doc", JsonPath.ROOT_PATH,
                            parser.createJsonValue(
                                    "{\"books\":[{\"t\":\"a\",\"price\":30},{\"t\":\"b\",\"price\":5}]}")))
                    .doOnNext(res3 -> {
                        System.out.println(res3); // >>> OK
                    })
                    .flatMap(res3 -> reactiveCommands
                            .jsonGet("doc", JsonPath.of("$.books[?(@.price >= 10)].append({\"t\":\"X\"})"))
                            .collectList())
                    .doOnNext(res4 -> {
                        System.out.println(res4); // >>> [[{"t":"a","price":30},{"t":"X"}]]
                    })
                    .then();
```

##### Java (Synchronous - Jedis)

```java
        String res60 = jedis.jsonSet("doc", new Path2("$"), "{\"arr\":[1,2,3]}");
        System.out.println(res60);    // >>> OK

        Object res61 = jedis.jsonGet("doc", new Path2("$.arr.append(9)"));
        System.out.println(res61);    // >>> [1,2,3,9]

        String res62 = jedis.jsonSet("doc", new Path2("$"), "{\"books\":[{\"t\":\"a\",\"price\":30},{\"t\":\"b\",\"price\":5}]}");
        System.out.println(res62);    // >>> OK

        Object res63 = jedis.jsonGet("doc", new Path2("$.books[?(@.price >= 10)].append({\"t\":\"X\"})"));
        System.out.println(res63);    // >>> [{"t":"a","price":30},{"t":"X"}]
```

##### JavaScript (Node.js)

```javascript
const res60 = await client.json.set('doc', '$', { arr: [1, 2, 3] });
console.log(res60); // >>> OK

const res61 = await client.json.get('doc', { path: '$.arr.append(9)' });
console.log(res61); // >>> [ 1, 2, 3, 9 ]

const res62 = await client.json.set('doc', '$', {
  books: [{ t: 'a', price: 30 }, { t: 'b', price: 5 }]
});
console.log(res62); // >>> OK

const res63 = await client.json.get('doc', { path: '$.books[?(@.price >= 10)].append({"t":"X"})' });
console.log(res63); // >>> [ { t: 'a', price: 30 }, { t: 'X' } ]
```

##### PHP

```php
        $res60 = $this->redis->jsonset('doc', '$', json_encode(
            ["arr" => [1, 2, 3]]
        ));
        echo $res60 . PHP_EOL; // >>> OK

        $res61 = $this->redis->jsonget('doc', '', '', '', '$.arr.append(9)');
        echo $res61 . PHP_EOL; // >>> [1,2,3,9]

        $res62 = $this->redis->jsonset('doc', '$', json_encode(
            ["books" => [["t" => "a", "price" => 30], ["t" => "b", "price" => 5]]]
        ));
        echo $res62 . PHP_EOL; // >>> OK

        $res63 = $this->redis->jsonget('doc', '', '', '', '$.books[?(@.price >= 10)].append({"t":"X"})');
        echo $res63 . PHP_EOL; // >>> [{"t":"a","price":30},{"t":"X"}]
```

##### Python

```python
res1 = r.json().set("doc", "$", {"arr": [1, 2, 3]})
print(res1)
# >>> True

res2 = r.json().get("doc", "$.arr.append(9)")
print(res2)
# >>> [1, 2, 3, 9]

res3 = r.json().set("doc", "$", {"books": [{"t": "a", "price": 30}, {"t": "b", "price": 5}]})
print(res3)
# >>> True

res4 = r.json().get("doc", '$.books[?(@.price >= 10)].append({"t":"X"})')
print(res4)
# >>> [{'t': 'a', 'price': 30}, {'t': 'X'}]
```

##### Ruby

```ruby
res1 = r.json_set('doc', '$', { 'arr' => [1, 2, 3] })
puts res1 # >>> OK

res2 = r.json_get('doc', '$.arr.append(9)')
p res2 # >>> [1, 2, 3, 9]

res3 = r.json_set('doc', '$', { 'books' => [{ 't' => 'a', 'price' => 30 }, { 't' => 'b', 'price' => 5 }] })
puts res3 # >>> OK

res4 = r.json_get('doc', '$.books[?(@.price >= 10)].append({"t":"X"})')
p res4 # >>> [{"t"=>"a", "price"=>30}, {"t"=>"X"}]
```

##### Rust (Asynchronous)

```rust
        let _: bool = match r.json_set("doc", "$", &json!({"arr":[1,2,3]})).await {
            Ok(v) => v,
            Err(e) => {
                println!("Error setting doc: {e}");
                return;
            }
        };

        match r.json_get("doc", "$.arr.append(9)").await {
            Ok(res1) => {
                let res1: String = res1;
                println!("{res1}");    // >>> [1,2,3,9]
            },
            Err(e) => {
                println!("Error getting doc: {e}");
                return;
            }
        }

        let _: bool = match r.json_set(
            "doc",
            "$",
            &json!({"books":[{"t":"a","price":30},{"t":"b","price":5}]}),
        ).await {
            Ok(v) => v,
            Err(e) => {
                println!("Error setting doc: {e}");
                return;
            }
        };

        match r.json_get("doc", r#"$.books[?(@.price >= 10)].append({"t":"X"})"#).await {
            Ok(res2) => {
                let res2: String = res2;
                println!("{res2}");    // >>> [{"t":"a","price":30},{"t":"X"}]
            },
            Err(e) => {
                println!("Error getting doc: {e}");
                return;
            }
        }

```

##### Rust (Synchronous)

```rust
        match r.json_set("doc", "$", &json!({"arr":[1,2,3]})) {
            Ok(res60) => {
                let res60: bool = res60;
                println!("{}", if res60 { "OK" } else { "(nil)" });    // >>> OK
            },
            Err(e) => {
                println!("Error setting doc: {e}");
                return;
            }
        }

        match r.json_get("doc", "$.arr.append(9)") {
            Ok(res61) => {
                let res61: String = res61;
                println!("{res61}");    // >>> [1,2,3,9]
            },
            Err(e) => {
                println!("Error getting doc: {e}");
                return;
            }
        }

        match r.json_set("doc", "$", &json!({"books":[{"t":"a","price":30},{"t":"b","price":5}]})) {
            Ok(res62) => {
                let res62: bool = res62;
                println!("{}", if res62 { "OK" } else { "(nil)" });    // >>> OK
            },
            Err(e) => {
                println!("Error setting doc: {e}");
                return;
            }
        }

        match r.json_get("doc", r#"$.books[?(@.price >= 10)].append({"t":"X"})"#) {
            Ok(res63) => {
                let res63: String = res63;
                println!("{res63}");    // >>> [{"t":"a","price":30},{"t":"X"}]
            },
            Err(e) => {
                println!("Error getting doc: {e}");
                return;
            }
        }

```



### Ví dụ cập nhật (Update examples)

Bạn cũng có thể dùng truy vấn JSONPath khi muốn cập nhật các phần cụ thể của một JSON document.

Ví dụ, bạn có thể truyền một JSONPath vào lệnh [`JSON.SET`](https://redis.io/docs/latest/commands/json.set) để cập nhật một field cụ thể. Ví dụ này thay đổi giá của mặt hàng đầu tiên trong danh sách tai nghe (headphones):

*Cập nhật hàng loạt: Dùng JSONPath với JSON.NUMINCRBY để cập nhật nhiều giá trị số trong document khi bạn cần áp dụng phép toán số học lên nhiều field*

**Độ khó (Difficulty):** Trung bình (Intermediate)

**Dựa trên (Builds upon):** get_bikes

**Lệnh (Commands):** JSON.GET, JSON.NUMINCRBY

**Độ phức tạp (Complexity):**
- JSON.GET: O(N)
- JSON.NUMINCRBY: O(1)

**Có sẵn trong (Available in):** Redis CLI, C#, Go, Java (Asynchronous - Lettuce), Java (Reactive - Lettuce), Java (Synchronous - Jedis), JavaScript (Node.js), PHP, Python, Ruby, Rust (Asynchronous), Rust (Synchronous)

##### Redis CLI

```
> JSON.GET bikes:inventory $..price
"[1920,2072,3264,1475,3941]"
> JSON.NUMINCRBY bikes:inventory $..price -100
"[1820,1972,3164,1375,3841]"
> JSON.NUMINCRBY bikes:inventory $..price 100
"[1920,2072,3264,1475,3941]"
```

##### C#

```csharp
        RedisResult res42 = db.JSON().Get("bikes:inventory", path: "$..price");
        Console.WriteLine(res42);   // >>> [1920,2072,3264,1475,3941]

        double?[] res43 = db.JSON().NumIncrby("bikes:inventory", "$..price", -100);
        Console.WriteLine(string.Join(", ", res43));    // >>> 1820, 1972, 3164, 1375, 3841

        double?[] res44 = db.JSON().NumIncrby("bikes:inventory", "$..price", 100);
        Console.WriteLine(string.Join(", ", res44));    // >>> 1920, 2072, 3264, 1475, 3941
```

##### Go

```go
	res15, err := rdb.JSONGet(ctx, "bikes:inventory", "$..price").Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res15) // >>> [1475,3941,1920,2072,3264]

	res16, err := rdb.JSONNumIncrBy(ctx, "bikes:inventory", "$..price", -100).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res16) // >>> [1375,3841,1820,1972,3164]

	res17, err := rdb.JSONNumIncrBy(ctx, "bikes:inventory", "$..price", 100).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res17) // >>> [1475,3941,1920,2072,3264]
```

##### Java (Asynchronous - Lettuce)

```java
            CompletableFuture<Void> updateBikes = asyncCommands.jsonGet("bikes:inventory", JsonPath.of("$..price"))
                    .thenCompose(r -> {
                        System.out.println(r); // >>> [[1920,2072,3264,1475,3941]]

                        return asyncCommands.jsonNumincrby("bikes:inventory", JsonPath.of("$..price"), -100);
                    }).thenCompose(res23 -> {
                        System.out.println(res23); // >>> [1820, 1972, 3164, 1375, 3841]

                        return asyncCommands.jsonNumincrby("bikes:inventory", JsonPath.of("$..price"), 100);
                    })
                    .thenAccept(System.out::println)
                    // >>> [1920, 2072, 3264, 1475, 3941]
                    .toCompletableFuture();
```

##### Java (Reactive - Lettuce)

```java
            Mono<Void> updateBikes = reactiveCommands.jsonGet("bikes:inventory", JsonPath.of("$..price")).collectList()
                    .doOnNext(r -> {
                        System.out.println(r); // >>> [[1920,2072,3264,1475,3941]]
                    })
                    .flatMap(
                            r -> reactiveCommands.jsonNumincrby("bikes:inventory", JsonPath.of("$..price"), -100).collectList())
                    .doOnNext(res23 -> {
                        System.out.println(res23); // >>> [1820, 1972, 3164, 1375, 3841]
                    })
                    .flatMap(res23 -> reactiveCommands.jsonNumincrby("bikes:inventory", JsonPath.of("$..price"), 100)
                            .collectList())
                    .doOnNext(System.out::println) // >>> [1920, 2072, 3264, 1475, 3941]
                    .then();
```

##### Java (Synchronous - Jedis)

```java
        Object res39 = jedis.jsonGet("bikes:inventory", new Path2("$..price"));
        System.out.println(res39);
        // >>> [1920,2072,3264,1475,3941]

        Object res40 = jedis.jsonNumIncrBy("bikes:inventory", new Path2("$..price"), -100);
        System.out.println(res40);  // >>> [1820, 1972, 3164, 1375, 3841]

        Object res41 = jedis.jsonNumIncrBy("bikes:inventory", new Path2("$..price"), 100);
        System.out.println(res41);  // >>> [1920, 2072, 3264, 1475, 3941]
```

##### JavaScript (Node.js)

```javascript
const res38 = await client.json.get("bikes:inventory", {
  path: "$..price"
});
console.log(res38);  //  [1920, 2072, 3264, 1475, 3941]

const res39 = await client.json.numIncrBy("bikes:inventory", "$..price", -100);
console.log(res39);  //  [1820, 1972, 3164, 1375, 3841]

const res40 = await client.json.numIncrBy("bikes:inventory", "$..price", 100);
console.log(res40);  //  [1920, 2072, 3264, 1475, 3941]
```

##### PHP

```php
        $res15b = $r->jsonget('bikes:inventory', '', '', '', '$..price');
        echo $res15b . PHP_EOL;
        // >>> [1920,2072,3264,1475,3941]

        $res16b = $r->jsonnumincrby('bikes:inventory', '$..price', -100);
        echo json_encode($res16b) . PHP_EOL;
        // >>> [1820,1972,3164,1375,3841]

        $res17b = $r->jsonnumincrby('bikes:inventory', '$..price', 100);
        echo json_encode($res17b) . PHP_EOL;
        // >>> [1920,2072,3264,1475,3941]
```

##### Python

```python
res15 = r.json().get("bikes:inventory", "$..price")
print(res15)  # >>> [1920, 2072, 3264, 1475, 3941]

res16 = r.json().numincrby("bikes:inventory", "$..price", -100)
print(res16)  # >>> [1820, 1972, 3164, 1375, 3841]

res17 = r.json().numincrby("bikes:inventory", "$..price", 100)
print(res17)  # >>> [1920, 2072, 3264, 1475, 3941]
```

##### Ruby

```ruby
res15 = r.json_get('bikes:inventory', '$..price')
p res15 # >>> [1920, 2072, 3264, 1475, 3941]

res16 = r.json_numincrby('bikes:inventory', '$..price', -100)
p res16 # >>> [1820, 1972, 3164, 1375, 3841]

res17 = r.json_numincrby('bikes:inventory', '$..price', 100)
p res17 # >>> [1920, 2072, 3264, 1475, 3941]
```

##### Rust (Asynchronous)

```rust
        let res40: String = r
            .json_get("bikes:inventory", "$..price")
            .await
            .expect("Failed to run JSON.GET");
        println!("{res40}"); // >>> [1920,2072,3264,1475,3941]

        let res41: String = cmd("JSON.NUMINCRBY")
            .arg("bikes:inventory")
            .arg("$..price")
            .arg(-100)
            .query_async(&mut r)
            .await
            .expect("Failed to run JSON.NUMINCRBY");
        println!("{res41}"); // >>> [1820,1972,3164,1375,3841]

        let res42: String = cmd("JSON.NUMINCRBY")
            .arg("bikes:inventory")
            .arg("$..price")
            .arg(100)
            .query_async(&mut r)
            .await
            .expect("Failed to run JSON.NUMINCRBY");
        println!("{res42}"); // >>> [1920,2072,3264,1475,3941]
```

##### Rust (Synchronous)

```rust
        let res40: String = r
            .json_get("bikes:inventory", "$..price")
            .expect("Failed to run JSON.GET");
        println!("{res40}"); // >>> [1920,2072,3264,1475,3941]

        let res41: String = cmd("JSON.NUMINCRBY")
            .arg("bikes:inventory")
            .arg("$..price")
            .arg(-100)
            .query(&mut r)
            .expect("Failed to run JSON.NUMINCRBY");
        println!("{res41}"); // >>> [1820,1972,3164,1375,3841]

        let res42: String = cmd("JSON.NUMINCRBY")
            .arg("bikes:inventory")
            .arg("$..price")
            .arg(100)
            .query(&mut r)
            .expect("Failed to run JSON.NUMINCRBY");
        println!("{res42}"); // >>> [1920,2072,3264,1475,3941]
```



Bạn có thể dùng biểu thức filter để chỉ cập nhật những phần tử JSON khớp với một số điều kiện nhất định. Ví dụ sau đặt giá của mọi xe đạp thành 1500 nếu giá của nó hiện đang nhỏ hơn 2000:

*Cập nhật có điều kiện: Dùng biểu thức filter với JSON.SET để chỉ cập nhật các phần tử khớp điều kiện cụ thể khi bạn cần sửa đổi có chọn lọc*

**Độ khó (Difficulty):** Nâng cao (Advanced)

**Dựa trên (Builds upon):** filter1

**Lệnh (Commands):** JSON.SET, JSON.GET

**Độ phức tạp (Complexity):**
- JSON.SET: O(M+N)
- JSON.GET: O(N)

**Có sẵn trong (Available in):** Redis CLI, C#, Go, Java (Asynchronous - Lettuce), Java (Reactive - Lettuce), Java (Synchronous - Jedis), JavaScript (Node.js), PHP, Python, Ruby, Rust (Asynchronous), Rust (Synchronous)

##### Redis CLI

```
> JSON.SET bikes:inventory '$.inventory.*[?(@.price<2000)].price' 1500
OK
> JSON.GET bikes:inventory $..price
"[1500,2072,3264,1500,3941]"
```

##### C#

```csharp
        bool res45 = db.JSON().Set(
            "bikes:inventory",
            "$.inventory.*[?(@.price<2000)].price",
            1500
        );
        Console.WriteLine(res45);   // >>> True

        RedisResult res46 = db.JSON().Get("bikes:inventory", path: "$..price");
        Console.WriteLine(res46);   // >>> [1500,2072,3264,1500,3941]
```

##### Go

```go
	res18, err := rdb.JSONSet(ctx,
		"bikes:inventory",
		"$.inventory.*[?(@.price<2000)].price",
		1500,
	).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res18) // >>> OK

	res19, err := rdb.JSONGet(ctx, "bikes:inventory", "$..price").Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res19) // >>> [1500,3941,1500,2072,3264]
```

##### Java (Asynchronous - Lettuce)

```java
            CompletableFuture<Void> updateFilters1 = asyncCommands.jsonSet("bikes:inventory",
                    JsonPath.of("$.inventory.*[?(@.price<2000)].price"), parser.createJsonValue("1500")).thenCompose(r -> {
                        System.out.println(r); // >>> OK

                        return asyncCommands.jsonGet("bikes:inventory", JsonPath.of("$..price"));
                    })
                    .thenAccept(System.out::println)
                    // >>> [[1500,2072,3264,1500,3941]]
                    .toCompletableFuture();
```

##### Java (Reactive - Lettuce)

```java
            Mono<Void> updateFilters1 = reactiveCommands.jsonSet("bikes:inventory",
                    JsonPath.of("$.inventory.*[?(@.price<2000)].price"), parser.createJsonValue("1500")).doOnNext(r -> {
                        System.out.println(r); // >>> OK
                    }).flatMap(r -> reactiveCommands.jsonGet("bikes:inventory", JsonPath.of("$..price")).collectList())
                    .doOnNext(System.out::println) // >>> [[1500,2072,3264,1500,3941]]
                    .then();
```

##### Java (Synchronous - Jedis)

```java
        jedis.jsonSet("bikes:inventory", new Path2("$.inventory.*[?(@.price<2000)].price"), 1500);
        Object res42 = jedis.jsonGet("bikes:inventory", new Path2("$..price"));
        System.out.println(res42);  // >>> [1500,2072,3264,1500,3941]
```

##### JavaScript (Node.js)

```javascript
const res40a = await client.json.set(
  'bikes:inventory',
  '$.inventory.*[?(@.price<2000)].price',
  1500
);

// Get all prices from the inventory
const res40b = await client.json.get(
  'bikes:inventory',
  { path: "$..price" }
);
console.log(res40b); // [1500, 2072, 3264, 1500, 3941]
```

##### PHP

```php
        $res18b = $r->jsonset('bikes:inventory', '$.inventory.*[?(@.price<2000)].price', '1500');
        $res19b = $r->jsonget('bikes:inventory', '', '', '', '$..price');
        echo $res19b . PHP_EOL;
        // >>> [1500,2072,3264,1500,3941]
```

##### Python

```python
res18 = r.json().set("bikes:inventory", "$.inventory.*[?(@.price<2000)].price", 1500)
res19 = r.json().get("bikes:inventory", "$..price")
print(res19)  # >>> [1500, 2072, 3264, 1500, 3941]
```

##### Ruby

```ruby
res18 = r.json_set('bikes:inventory', '$.inventory.*[?(@.price<2000)].price', 1500)
res19 = r.json_get('bikes:inventory', '$..price')
p res19 # >>> [1500, 2072, 3264, 1500, 3941]
```

##### Rust (Asynchronous)

```rust
        let _: bool = r
            .json_set(
                "bikes:inventory",
                "$.inventory.*[?(@.price<2000)].price",
                &json!(1500),
            )
            .await
            .expect("Failed to run JSON.SET");

        let res43: String = r
            .json_get("bikes:inventory", "$..price")
            .await
            .expect("Failed to run JSON.GET");
        println!("{res43}"); // >>> [1500,2072,3264,1500,3941]
```

##### Rust (Synchronous)

```rust
        let _: bool = r
            .json_set(
                "bikes:inventory",
                "$.inventory.*[?(@.price<2000)].price",
                &json!(1500),
            )
            .expect("Failed to run JSON.SET");

        let res43: String = r
            .json_get("bikes:inventory", "$..price")
            .expect("Failed to run JSON.GET");
        println!("{res43}"); // >>> [1500,2072,3264,1500,3941]
```



Truy vấn JSONPath cũng hoạt động với các lệnh JSON khác nhận một path làm đối số. Ví dụ, bạn có thể thêm một lựa chọn màu mới cho một bộ tai nghe bằng [`JSON.ARRAPPEND`](https://redis.io/docs/latest/commands/json.arrappend):

*Cập nhật mảng với filter: Dùng JSON.ARRAPPEND với biểu thức filter để thêm phần tử vào các mảng khớp điều kiện khi bạn cần sửa đổi collection có chọn lọc*

**Độ khó (Difficulty):** Nâng cao (Advanced)

**Dựa trên (Builds upon):** update_filters1

**Lệnh (Commands):** JSON.ARRAPPEND, JSON.GET

**Độ phức tạp (Complexity):**
- JSON.ARRAPPEND: O(1)
- JSON.GET: O(N)

**Có sẵn trong (Available in):** Redis CLI, C#, Go, Java (Asynchronous - Lettuce), Java (Reactive - Lettuce), Java (Synchronous - Jedis), JavaScript (Node.js), PHP, Python, Ruby, Rust (Asynchronous), Rust (Synchronous)

##### Redis CLI

```
> JSON.ARRAPPEND bikes:inventory '$.inventory.*[?(@.price<2000)].colors' '"pink"'
1) (integer) 3
2) (integer) 3
> JSON.GET bikes:inventory $..[*].colors
"[[\"black\",\"silver\",\"pink\"],[\"black\",\"white\"],[\"black\",\"silver\",\"pink\"]]"
```

##### C#

```csharp
        long?[] res47 = db.JSON().ArrAppend(
            "bikes:inventory", "$.inventory.*[?(@.price<2000)].colors", "pink"
        );
        Console.WriteLine(string.Join(", ", res47));    // >>> 3, 3

        RedisResult res48 = db.JSON().Get("bikes:inventory", path: "$..[*].colors");
        Console.WriteLine(res48);   // >>> [["black","silver","pink"],["black","white"],["black","silver","pink"]]
```

##### Go

```go
	res20, err := rdb.JSONArrAppend(ctx,
		"bikes:inventory",
		"$.inventory.*[?(@.price<2000)].colors",
		"\"pink\"",
	).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res20) // >>> [3 3]

	res21, err := rdb.JSONGet(ctx, "bikes:inventory", "$..[*].colors").Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res21)
	// >>> [["black","silver","pink"],["black","silver","pink"],["black","white"]]
```

##### Java (Asynchronous - Lettuce)

```java
            CompletableFuture<Void> updateFilters2 = asyncCommands.jsonArrappend("bikes:inventory",
                    JsonPath.of("$.inventory.*[?(@.price<2000)].colors"), parser.createJsonValue("\"pink\"")).thenCompose(r -> {
                        System.out.println(r); // >>> [3, 3]

                        return asyncCommands.jsonGet("bikes:inventory", JsonPath.of("$..[*].colors"));
                    })
                    .thenAccept(System.out::println)
                    // >>> [[["black","silver","pink"],["black","white"],["black","silver","pink"]]]
                    .toCompletableFuture();
```

##### Java (Reactive - Lettuce)

```java
            Mono<Void> updateFilters2 = reactiveCommands.jsonArrappend("bikes:inventory",
                    JsonPath.of("$.inventory.*[?(@.price<2000)].colors"), parser.createJsonValue("\"pink\"")).collectList()
                    .doOnNext(r -> {
                        System.out.println(r); // >>> [3, 3]
                    }).flatMap(r -> reactiveCommands.jsonGet("bikes:inventory", JsonPath.of("$..[*].colors")).collectList())
                    .doOnNext(System.out::println) // >>>
                                                   // [[["black","silver","pink"],["black","white"],["black","silver","pink"]]]
                    .then();
```

##### Java (Synchronous - Jedis)

```java
        List<Long> res43 = jedis.jsonArrAppendWithEscape(
            "bikes:inventory", new Path2("$.inventory.*[?(@.price<2000)].colors"),
            "\"pink\""
        );
        System.out.println(res43);  // >>> [3, 3]

        Object res44 = jedis.jsonGet("bikes:inventory", new Path2("$..[*].colors"));
        System.out.println(res44);
        // >>> [["black","silver","\"pink\""],["black","white"],["black","silver","\"pink\""]]
```

##### JavaScript (Node.js)

```javascript
const res41 = await client.json.arrAppend(
    "bikes:inventory", "$.inventory.*[?(@.price<2000)].colors", "pink"
);
console.log(res41);  //  [3, 3]

const res42 = await client.json.get("bikes:inventory", {
  path: "$..[*].colors"
});
console.log(res42);  //  [['black', 'silver', 'pink'], ['black', 'white'], ['black', 'silver', 'pink']]
```

##### PHP

```php
        $res20b = $r->jsonarrappend('bikes:inventory', '$.inventory.*[?(@.price<2000)].colors', '"pink"');
        echo json_encode($res20b) . PHP_EOL;
        // >>> [3,3]

        $res21b = $r->jsonget('bikes:inventory', '', '', '', '$..[*].colors');
        echo $res21b . PHP_EOL;
        // >>> [["black","silver","pink"],["black","white"],["black","silver","pink"]]
```

##### Python

```python
res20 = r.json().arrappend(
    "bikes:inventory", "$.inventory.*[?(@.price<2000)].colors", "pink"
)
print(res20)  # >>> [3, 3]

res21 = r.json().get("bikes:inventory", "$..[*].colors")
print(
    res21
)  # >>> [['black', 'silver', 'pink'], ['black', 'white'], ['black', 'silver', 'pink']]
```

##### Ruby

```ruby
res20 = r.json_arrappend(
  'bikes:inventory', '$.inventory.*[?(@.price<2000)].colors', 'pink'
)
p res20 # >>> [3, 3]

res21 = r.json_get('bikes:inventory', '$..[*].colors')
p res21
# >>> [["black", "silver", "pink"], ["black", "white"], ["black", "silver", "pink"]]
```

##### Rust (Asynchronous)

```rust
        let res44: Value = cmd("JSON.ARRAPPEND")
            .arg("bikes:inventory")
            .arg("$.inventory.*[?(@.price<2000)].colors")
            .arg("\"pink\"")
            .query_async(&mut r)
            .await
            .expect("Failed to run JSON.ARRAPPEND");
        print_redis_value(&res44); // >>> [3,3]

        let res45: String = r
            .json_get("bikes:inventory", "$..[*].colors")
            .await
            .expect("Failed to run JSON.GET");
        println!("{res45}");
        // >>> [["black","silver","pink"],["black","white"],["black","silver","pink"]]
```

##### Rust (Synchronous)

```rust
        let res44: Value = cmd("JSON.ARRAPPEND")
            .arg("bikes:inventory")
            .arg("$.inventory.*[?(@.price<2000)].colors")
            .arg("\"pink\"")
            .query(&mut r)
            .expect("Failed to run JSON.ARRAPPEND");
        print_redis_value(&res44); // >>> [3,3]

        let res45: String = r
            .json_get("bikes:inventory", "$..[*].colors")
            .expect("Failed to run JSON.GET");
        println!("{res45}");
        // >>> [["black","silver","pink"],["black","white"],["black","silver","pink"]]
```



<a name="projection-expressions"></a>

## Biểu thức projection (Projection expressions)

Cấp cao nhất của một truy vấn JSONPath có thể là một biểu thức *tính toán* ra một giá trị — phép số học, một lời gọi hàm, một toán tử lấy key, v.v. — thay vì chỉ chọn các node từ document. Một truy vấn là path thuần (ví dụ, `$.a.b`, `$..x`, hoặc thậm chí một path được bao hoàn toàn trong ngoặc đơn như `($.a)`) vẫn chọn node; mọi thứ khác (ví dụ, `$.a + 1`, `-$.a`, `$.arr.length()`, `length($.arr)`, `$.obj~`, hoặc `$.obj.keys()`) là một projection.

Một projection được đánh giá thành *Nothing* sẽ tạo ra một phản hồi rỗng (`[]`).

*Biểu thức projection: Dùng phép số học, một lời gọi hàm, hoặc độ dài của một path làm truy vấn cấp cao nhất để tính ra một giá trị thay vì chỉ chọn node*

**Độ khó (Difficulty):** Nâng cao (Advanced)

**Lệnh (Commands):** JSON.SET, JSON.GET

**Độ phức tạp (Complexity):**
- JSON.SET: O(M+N)
- JSON.GET: O(N)

**Có sẵn trong (Available in):** Redis CLI, C#, Go, Java (Asynchronous - Lettuce), Java (Reactive - Lettuce), Java (Synchronous - Jedis), JavaScript (Node.js), PHP, Python, Ruby, Rust (Asynchronous), Rust (Synchronous)

##### Redis CLI

```
> JSON.SET doc $ '{"a":2,"b":4,"arr":[1,2,3]}'
OK
> JSON.GET doc '$.a + 1'
"[3]"
> JSON.GET doc '$.a * $.b'
"[8]"
> JSON.GET doc '($.a + $.b) / 2'
"[3.0]"
> JSON.GET doc '$.arr.length()'
"[3]"
> JSON.GET doc '$.a / 0'
"[]"
```

##### C#

```csharp
        bool res64 = db.JSON().Set("doc", "$", "{\"a\":2,\"b\":4,\"arr\":[1,2,3]}");
        Console.WriteLine(res64);   // >>> True

        RedisResult res65 = db.JSON().Get("doc", path: "$.a + 1");
        Console.WriteLine(res65);   // >>> [3]

        RedisResult res66 = db.JSON().Get("doc", path: "$.a * $.b");
        Console.WriteLine(res66);   // >>> [8]

        RedisResult res67 = db.JSON().Get("doc", path: "($.a + $.b) / 2");
        Console.WriteLine(res67);   // >>> [3.0]

        RedisResult res68 = db.JSON().Get("doc", path: "$.arr.length()");
        Console.WriteLine(res68);   // >>> [3]

        RedisResult res69 = db.JSON().Get("doc", path: "$.a / 0");
        Console.WriteLine(res69);   // >>> []
```

##### Go

```go
	res1, err := rdb.JSONSet(ctx, "doc", "$",
		`{"a":2,"b":4,"arr":[1,2,3]}`,
	).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res1) // >>> OK

	res2, err := rdb.JSONGet(ctx, "doc", `$.a + 1`).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res2) // >>> [3]

	res3, err := rdb.JSONGet(ctx, "doc", `$.a * $.b`).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res3) // >>> [8]

	res4, err := rdb.JSONGet(ctx, "doc", `($.a + $.b) / 2`).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res4) // >>> [3.0]

	res5, err := rdb.JSONGet(ctx, "doc", `$.arr.length()`).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res5) // >>> [3]

	res6, err := rdb.JSONGet(ctx, "doc", `$.a / 0`).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res6) // >>> []
```

##### Java (Asynchronous - Lettuce)

```java
            CompletableFuture<Void> projBasicExample = asyncCommands
                    .jsonSet("doc", JsonPath.ROOT_PATH,
                            parser.createJsonValue("{\"a\":2,\"b\":4,\"arr\":[1,2,3]}"))
                    .thenCompose(res1 -> {
                        System.out.println(res1); // >>> OK
                        return asyncCommands.jsonGet("doc", JsonPath.of("$.a + 1"));
                    }).thenCompose(res2 -> {
                        System.out.println(res2); // >>> [[3]]
                        return asyncCommands.jsonGet("doc", JsonPath.of("$.a * $.b"));
                    }).thenCompose(res3 -> {
                        System.out.println(res3); // >>> [[8]]
                        return asyncCommands.jsonGet("doc", JsonPath.of("($.a + $.b) / 2"));
                    }).thenCompose(res4 -> {
                        System.out.println(res4); // >>> [[3.0]]
                        return asyncCommands.jsonGet("doc", JsonPath.of("$.arr.length()"));
                    }).thenCompose(res5 -> {
                        System.out.println(res5); // >>> [[3]]
                        return asyncCommands.jsonGet("doc", JsonPath.of("$.a / 0"));
                    }).thenAccept(res6 -> {
                        System.out.println(res6); // >>> [[]]
                    }).toCompletableFuture();
```

##### Java (Reactive - Lettuce)

```java
            Mono<Void> projBasicExample = reactiveCommands
                    .jsonSet("doc", JsonPath.ROOT_PATH, parser.createJsonValue("{\"a\":2,\"b\":4,\"arr\":[1,2,3]}"))
                    .doOnNext(res1 -> {
                        System.out.println(res1); // >>> OK
                    })
                    .flatMap(res1 -> reactiveCommands.jsonGet("doc", JsonPath.of("$.a + 1")).collectList())
                    .doOnNext(res2 -> {
                        System.out.println(res2); // >>> [[3]]
                    })
                    .flatMap(res2 -> reactiveCommands.jsonGet("doc", JsonPath.of("$.a * $.b")).collectList())
                    .doOnNext(res3 -> {
                        System.out.println(res3); // >>> [[8]]
                    })
                    .flatMap(res3 -> reactiveCommands.jsonGet("doc", JsonPath.of("($.a + $.b) / 2")).collectList())
                    .doOnNext(res4 -> {
                        System.out.println(res4); // >>> [[3.0]]
                    })
                    .flatMap(res4 -> reactiveCommands.jsonGet("doc", JsonPath.of("$.arr.length()")).collectList())
                    .doOnNext(res5 -> {
                        System.out.println(res5); // >>> [[3]]
                    })
                    .flatMap(res5 -> reactiveCommands.jsonGet("doc", JsonPath.of("$.a / 0")).collectList())
                    .doOnNext(res6 -> {
                        System.out.println(res6); // >>> [[]]
                    })
                    .then();
```

##### Java (Synchronous - Jedis)

```java
        String res64 = jedis.jsonSet("doc", new Path2("$"), "{\"a\":2,\"b\":4,\"arr\":[1,2,3]}");
        System.out.println(res64);    // >>> OK

        Object res65 = jedis.jsonGet("doc", new Path2("$.a + 1"));
        System.out.println(res65);    // >>> [3]

        Object res66 = jedis.jsonGet("doc", new Path2("$.a * $.b"));
        System.out.println(res66);    // >>> [8]

        Object res67 = jedis.jsonGet("doc", new Path("($.a + $.b) / 2"));
        System.out.println(res67);    // >>> [3.0]

        Object res68 = jedis.jsonGet("doc", new Path2("$.arr.length()"));
        System.out.println(res68);    // >>> [3]

        Object res69 = jedis.jsonGet("doc", new Path2("$.a / 0"));
        System.out.println(res69);    // >>> []
```

##### JavaScript (Node.js)

```javascript
const res64 = await client.json.set('doc', '$', { a: 2, b: 4, arr: [1, 2, 3] });
console.log(res64); // >>> OK

const res65 = await client.json.get('doc', { path: '$.a + 1' });
console.log(res65); // >>> [ 3 ]

const res66 = await client.json.get('doc', { path: '$.a * $.b' });
console.log(res66); // >>> [ 8 ]

const res67 = await client.json.get('doc', { path: '($.a + $.b) / 2' });
console.log(res67); // >>> [ 3 ]

const res68 = await client.json.get('doc', { path: '$.arr.length()' });
console.log(res68); // >>> [ 3 ]

const res69 = await client.json.get('doc', { path: '$.a / 0' });
console.log(res69); // >>> []
```

##### PHP

```php
        $res64 = $this->redis->jsonset('doc', '$', json_encode(
            ["a" => 2, "b" => 4, "arr" => [1, 2, 3]]
        ));
        echo $res64 . PHP_EOL; // >>> OK

        $res65 = $this->redis->jsonget('doc', '', '', '', '$.a + 1');
        echo $res65 . PHP_EOL; // >>> [3]

        $res66 = $this->redis->jsonget('doc', '', '', '', '$.a * $.b');
        echo $res66 . PHP_EOL; // >>> [8]

        $res67 = $this->redis->jsonget('doc', '', '', '', '($.a + $.b) / 2');
        echo $res67 . PHP_EOL; // >>> [3.0]

        $res68 = $this->redis->jsonget('doc', '', '', '', '$.arr.length()');
        echo $res68 . PHP_EOL; // >>> [3]

        $res69 = $this->redis->jsonget('doc', '', '', '', '$.a / 0');
        echo $res69 . PHP_EOL; // >>> []
```

##### Python

```python
res1 = r.json().set("doc", "$", {"a": 2, "b": 4, "arr": [1, 2, 3]})
print(res1)
# >>> True

res2 = r.json().get("doc", "$.a + 1")
print(res2)
# >>> [3]

res3 = r.json().get("doc", "$.a * $.b")
print(res3)
# >>> [8]

res4 = r.json().get("doc", "($.a + $.b) / 2")
print(res4)
# >>> [3.0]

res5 = r.json().get("doc", "$.arr.length()")
print(res5)
# >>> [3]

res6 = r.json().get("doc", "$.a / 0")
print(res6)
# >>> []
```

##### Ruby

```ruby
res1 = r.json_set('doc', '$', { 'a' => 2, 'b' => 4, 'arr' => [1, 2, 3] })
puts res1 # >>> OK

res2 = r.json_get('doc', '$.a + 1')
p res2 # >>> [3]

res3 = r.json_get('doc', '$.a * $.b')
p res3 # >>> [8]

res4 = r.json_get('doc', '($.a + $.b) / 2')
p res4 # >>> [3.0]

res5 = r.json_get('doc', '$.arr.length()')
p res5 # >>> [3]

res6 = r.json_get('doc', '$.a / 0')
p res6 # >>> []
```

##### Rust (Asynchronous)

```rust
        let _: bool = match r.json_set("doc", "$", &json!({"a":2,"b":4,"arr":[1,2,3]})).await {
            Ok(v) => v,
            Err(e) => {
                println!("Error setting doc: {e}");
                return;
            }
        };

        match r.json_get("doc", "$.a + 1").await {
            Ok(res1) => {
                let res1: String = res1;
                println!("{res1}");    // >>> [3]
            },
            Err(e) => {
                println!("Error getting doc: {e}");
                return;
            }
        }

        match r.json_get("doc", "$.a * $.b").await {
            Ok(res2) => {
                let res2: String = res2;
                println!("{res2}");    // >>> [8]
            },
            Err(e) => {
                println!("Error getting doc: {e}");
                return;
            }
        }

        match r.json_get("doc", "($.a + $.b) / 2").await {
            Ok(res3) => {
                let res3: String = res3;
                println!("{res3}");    // >>> [3.0]
            },
            Err(e) => {
                println!("Error getting doc: {e}");
                return;
            }
        }

        match r.json_get("doc", "$.arr.length()").await {
            Ok(res4) => {
                let res4: String = res4;
                println!("{res4}");    // >>> [3]
            },
            Err(e) => {
                println!("Error getting doc: {e}");
                return;
            }
        }

        match r.json_get("doc", "$.a / 0").await {
            Ok(res5) => {
                let res5: String = res5;
                println!("{res5}");    // >>> []
            },
            Err(e) => {
                println!("Error getting doc: {e}");
                return;
            }
        }

```

##### Rust (Synchronous)

```rust
        match r.json_set("doc", "$", &json!({"a":2,"b":4,"arr":[1,2,3]})) {
            Ok(res64) => {
                let res64: bool = res64;
                println!("{}", if res64 { "OK" } else { "(nil)" });    // >>> OK
            },
            Err(e) => {
                println!("Error setting doc: {e}");
                return;
            }
        }

        match r.json_get("doc", "$.a + 1") {
            Ok(res65) => {
                let res65: String = res65;
                println!("{res65}");    // >>> [3]
            },
            Err(e) => {
                println!("Error getting doc: {e}");
                return;
            }
        }

        match r.json_get("doc", "$.a * $.b") {
            Ok(res66) => {
                let res66: String = res66;
                println!("{res66}");    // >>> [8]
            },
            Err(e) => {
                println!("Error getting doc: {e}");
                return;
            }
        }

        match r.json_get("doc", "($.a + $.b) / 2") {
            Ok(res67) => {
                let res67: String = res67;
                println!("{res67}");    // >>> [3.0]
            },
            Err(e) => {
                println!("Error getting doc: {e}");
                return;
            }
        }

        match r.json_get("doc", "$.arr.length()") {
            Ok(res68) => {
                let res68: String = res68;
                println!("{res68}");    // >>> [3]
            },
            Err(e) => {
                println!("Error getting doc: {e}");
                return;
            }
        }

        match r.json_get("doc", "$.a / 0") {
            Ok(res69) => {
                let res69: String = res69;
                println!("{res69}");    // >>> []
            },
            Err(e) => {
                println!("Error getting doc: {e}");
                return;
            }
        }
```



Khi [`JSON.GET`](https://redis.io/docs/latest/commands/json.get) được truyền nhiều hơn một path, projection và path thuần có thể được trộn lẫn, và mỗi path trở thành một key trong object được trả về. Thứ tự các key trong object đó không được đảm bảo.

*Truy vấn nhiều path: Truyền nhiều hơn một path vào JSON.GET để kết quả của mỗi path được trả về dưới key riêng của nó trong một phản hồi duy nhất*

**Độ khó (Difficulty):** Nâng cao (Advanced)

**Lệnh (Commands):** JSON.GET

**Độ phức tạp (Complexity):**
- JSON.GET: O(N)

**Có sẵn trong (Available in):** Redis CLI, C#, Go, Java (Asynchronous - Lettuce), Java (Reactive - Lettuce), Java (Synchronous - Jedis), JavaScript (Node.js), PHP, Python, Ruby, Rust (Asynchronous), Rust (Synchronous)

##### Redis CLI

```
> JSON.GET doc '$.a + 1' '$.b'
"{\"$.a + 1\":[3],\"$.b\":[4]}"
```

##### C#

```csharp
        bool res70 = db.JSON().Set("doc", "$", "{\"a\":2,\"b\":4,\"arr\":[1,2,3]}");
        Console.WriteLine(res70);   // >>> True

        RedisResult res71 = db.JSON().Get("doc", paths: new[] { "$.a + 1", "$.b" });
        Console.WriteLine(res71);   // >>> {"$.a + 1":[3],"$.b":[4]}
```

##### Go

```go
	res1, err := rdb.JSONSet(ctx, "doc", "$",
		`{"a":2,"b":4,"arr":[1,2,3]}`,
	).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res1) // >>> OK

	raw, err := rdb.JSONGet(ctx, "doc", `$.a + 1`, `$.b`).Result()

	if err != nil {
		panic(err)
	}

	// A multi-path JSON.GET reply is a JSON object whose key order is not
	// guaranteed, so decode it into a map rather than printing the raw
	// string. Go's fmt package prints map keys in sorted order, which keeps
	// this example's output deterministic regardless of the order Redis
	// returns the paths in.
	res2 := map[string]interface{}{}

	if err := json.Unmarshal([]byte(raw), &res2); err != nil {
		panic(err)
	}

	fmt.Println(res2) // >>> map[$.a + 1:[3] $.b:[4]]
```

##### Java (Asynchronous - Lettuce)

```java
            CompletableFuture<Void> projMultipathExample = asyncCommands
                    .jsonSet("doc", JsonPath.ROOT_PATH,
                            parser.createJsonValue("{\"a\":2,\"b\":4,\"arr\":[1,2,3]}"))
                    .thenCompose(res7 -> {
                        System.out.println(res7); // >>> OK
                        return asyncCommands.jsonGet("doc", JsonPath.of("$.a + 1"), JsonPath.of("$.b"));
                    }).thenAccept(res8 -> {
                        // The multi-path JSON.GET reply is a single JSON object keyed by path,
                        // and Redis does not guarantee key order, so look up each path rather
                        // than comparing the object's rendered string.
                        JsonObject paths = res8.get(0).asJsonObject();
                        System.out.println(paths); // >>> {"$.a + 1":[3],"$.b":[4]}
                    }).toCompletableFuture();
```

##### Java (Reactive - Lettuce)

```java
            Mono<Void> projMultipathExample = reactiveCommands
                    .jsonSet("doc", JsonPath.ROOT_PATH, parser.createJsonValue("{\"a\":2,\"b\":4,\"arr\":[1,2,3]}"))
                    .doOnNext(res7 -> {
                        System.out.println(res7); // >>> OK
                    })
                    .flatMap(res7 -> reactiveCommands.jsonGet("doc", JsonPath.of("$.a + 1"), JsonPath.of("$.b"))
                            .collectList())
                    .doOnNext(res8 -> {
                        System.out.println(res8); // >>> [{"$.a + 1":[3],"$.b":[4]}]
                    })
                    .then();
```

##### Java (Synchronous - Jedis)

```java
        String res70 = jedis.jsonSet("doc", new Path2("$"), "{\"a\":2,\"b\":4,\"arr\":[1,2,3]}");
        System.out.println(res70);    // >>> OK

        Object res71 = jedis.jsonGet("doc", new Path2("$.a + 1"), new Path2("$.b"));
        System.out.println(res71);    // >>> {"$.a + 1":[3],"$.b":[4]} (a JSON object; key order is not guaranteed)
```

##### JavaScript (Node.js)

```javascript
const res70 = await client.json.set('doc', '$', { a: 2, b: 4, arr: [1, 2, 3] });
console.log(res70); // >>> OK

const res71 = await client.json.get('doc', { path: ['$.a + 1', '$.b'] });
console.log(res71); // >>> { '$.a + 1': [ 3 ], '$.b': [ 4 ] }
```

##### PHP

```php
        $res70 = $this->redis->jsonset('doc', '$', json_encode(
            ["a" => 2, "b" => 4, "arr" => [1, 2, 3]]
        ));
        echo $res70 . PHP_EOL; // >>> OK

        $res71 = $this->redis->jsonget('doc', '', '', '', '$.a + 1', '$.b');
        // The reply is a JSON object keyed by path; key order is not guaranteed.
        echo $res71 . PHP_EOL; // >>> {"$.a + 1":[3],"$.b":[4]} (key order not guaranteed)
```

##### Python

```python
res7 = r.json().set("doc", "$", {"a": 2, "b": 4, "arr": [1, 2, 3]})
print(res7)
# >>> True

res8 = r.json().get("doc", "$.a + 1", "$.b")
print(res8)
# >>> {'$.a + 1': [3], '$.b': [4]}
```

##### Ruby

```ruby
res7 = r.json_set('doc', '$', { 'a' => 2, 'b' => 4, 'arr' => [1, 2, 3] })
puts res7 # >>> OK

# The reply's key order is not guaranteed, so don't rely on it.
res8 = r.json_get('doc', '$.a + 1', '$.b')
p res8
```

##### Rust (Asynchronous)

```rust
        let _: bool = match r.json_set("doc", "$", &json!({"a":2,"b":4,"arr":[1,2,3]})).await {
            Ok(v) => v,
            Err(e) => {
                println!("Error setting doc: {e}");
                return;
            }
        };

        match r.json_get("doc", &["$.a + 1", "$.b"]).await {
            Ok(res6) => {
                let res6: String = res6;
                // The reply's key order is not guaranteed, so parse it into a
                // Value and compare that structurally rather than relying on
                // the raw string's key order.
                println!("{res6}");
            },
            Err(e) => {
                println!("Error getting doc: {e}");
                return;
            }
        }

```

##### Rust (Synchronous)

```rust
        match r.json_set("doc", "$", &json!({"a":2,"b":4,"arr":[1,2,3]})) {
            Ok(res70) => {
                let res70: bool = res70;
                println!("{}", if res70 { "OK" } else { "(nil)" });    // >>> OK
            },
            Err(e) => {
                println!("Error setting doc: {e}");
                return;
            }
        }

        // A multi-path JSON.GET replies with an object keyed by path, but Redis does not
        // guarantee the key order in that reply, so this parses the JSON and compares it
        // structurally (map equality, order-independent) instead of comparing raw strings.
        match r.json_get::<_, _, String>("doc", vec!["$.a + 1", "$.b"]) {
            Ok(res71) => {
                let parsed: serde_json::Value = serde_json::from_str(&res71).unwrap();
                // Render with sorted keys purely for a deterministic, printable example —
                // the actual reply's key order can differ from run to run.
                let sorted: std::collections::BTreeMap<String, serde_json::Value> =
                    serde_json::from_value(parsed.clone()).unwrap();
                println!("{}", serde_json::to_string(&sorted).unwrap());    // >>> {"$.a + 1":[3],"$.b":[4]}
            },
            Err(e) => {
                println!("Error getting doc: {e}");
                return;
            }
        }

```



[`JSON.MGET`](https://redis.io/docs/latest/commands/json.mget) đánh giá projection một cách độc lập cho từng key. Một key không tồn tại, hoặc một lỗi đánh giá của riêng một key, sẽ cho ra null reply cho key đó thay vì làm toàn bộ request thất bại.

<a name="legacy-path-syntax"></a>

## Cú pháp legacy path (Legacy path syntax)

RedisJSON v1 có cách hiện thực path như sau. JSON v2 vẫn hỗ trợ legacy path này bên cạnh JSONPath.

Path luôn bắt đầu tại gốc của một giá trị Redis JSON. Gốc được ký hiệu bằng ký tự dấu chấm (`.`). Với các path tham chiếu tới con của gốc, việc thêm tiền tố gốc vào path là tùy chọn.

Redis JSON hỗ trợ cả ký pháp dấu chấm lẫn ký pháp ngoặc vuông để truy cập key của object. Các path sau đều tham chiếu tới _headphones_, là con của _inventory_ nằm dưới gốc:

*   `.inventory.headphones`
*   `inventory["headphones"]`
*   `['inventory']["headphones"]`

Để truy cập một phần tử mảng, hãy đặt chỉ số của nó trong một cặp ngoặc vuông. Chỉ số bắt đầu từ 0, với 0 là phần tử đầu tiên của mảng, 1 là phần tử tiếp theo, và cứ thế. Bạn có thể dùng offset âm để truy cập phần tử bắt đầu từ cuối mảng. Ví dụ, -1 là phần tử cuối cùng trong mảng, -2 là phần tử áp chót, và cứ thế.

### Tên key JSON và khả năng tương thích của path (JSON key names and path compatibility)

Theo định nghĩa, một JSON key có thể là bất kỳ chuỗi JSON hợp lệ nào. Mặt khác, path theo truyền thống dựa trên quy ước đặt tên biến của JavaScript (và Java).

Mặc dù JSON có thể lưu các object chứa tên key tùy ý, bạn chỉ có thể dùng legacy path để truy cập các key này nếu chúng tuân theo các quy tắc cú pháp đặt tên sau:

1.  Tên phải bắt đầu bằng một chữ cái, ký tự đô la (`$`), hoặc ký tự gạch dưới (`_`)
2.  Tên có thể chứa chữ cái, chữ số, ký tự đô la, và gạch dưới
3.  Tên phân biệt hoa thường

## Độ phức tạp thời gian của việc đánh giá path (Time complexity of path evaluation)

Độ phức tạp thời gian của việc tìm kiếm (điều hướng tới) một phần tử trong path được tính từ:

1. Cấp con - mỗi cấp dọc theo path thêm một lần tìm kiếm
2. Tìm key - O(N)<sup>&#8224;</sup>, với N là số key trong object cha
3. Tìm trong mảng - O(1)

Điều này có nghĩa là độ phức tạp thời gian tổng thể của việc tìm kiếm một path là _O(N*M)_, với N là độ sâu và M là số key của object cha.

<sup>&#8224;</sup> Mặc dù điều này chấp nhận được với các object có N nhỏ, việc truy cập có thể được tối ưu cho các object lớn hơn.

# Redis geospatial

> Nguồn: <https://redis.io/docs/latest/develop/data-types/geospatial/>
> Giới thiệu về Redis Geospatial

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


### Tóm tắt các lệnh geospatial (Geospatial command summary)

**10 lệnh trong nhóm này:**

[Xem tất cả các lệnh geo](https://redis.io/commands/?group=geo)

| Lệnh | Tóm tắt | Độ phức tạp | Từ phiên bản |
|---------|---------|------------|-------|
| [GEOADD](https://redis.io/commands/geoadd/) | Thêm một hoặc nhiều member vào một geospatial index. Key được tạo nếu nó chưa tồn tại. | O(log(N)) cho mỗi phần tử được thêm, với N là... | 3.2.0 |
| [GEODIST](https://redis.io/commands/geodist/) | Trả về khoảng cách giữa hai member của một geospatial index. | O(1) | 3.2.0 |
| [GEOHASH](https://redis.io/commands/geohash/) | Trả về các member của một geospatial index dưới dạng chuỗi geohash. | O(1) cho mỗi member được yêu cầu. | 3.2.0 |
| [GEOPOS](https://redis.io/commands/geopos/) | Trả về kinh độ (longitude) và vĩ độ (latitude) của các member trong một geospatial index. | O(1) cho mỗi member được yêu cầu. | 3.2.0 |
| [GEORADIUS](https://redis.io/commands/georadius/) | Truy vấn một geospatial index để tìm các member nằm trong một khoảng cách tính từ một tọa độ, có thể lưu kết quả. | O(N+log(M)) với N là số phần tử trong... | 3.2.0 |
| [GEORADIUS_RO](https://redis.io/commands/georadius_ro/) | Trả về các member của một geospatial index nằm trong một khoảng cách tính từ một tọa độ. | O(N+log(M)) với N là số phần tử trong... | 3.2.10 |
| [GEORADIUSBYMEMBER](https://redis.io/commands/georadiusbymember/) | Truy vấn một geospatial index để tìm các member nằm trong một khoảng cách tính từ một member, có thể lưu kết quả. | O(N+log(M)) với N là số phần tử trong... | 3.2.0 |
| [GEORADIUSBYMEMBER_RO](https://redis.io/commands/georadiusbymember_ro/) | Trả về các member của một geospatial index nằm trong một khoảng cách tính từ một member. | O(N+log(M)) với N là số phần tử trong... | 3.2.10 |
| [GEOSEARCH](https://redis.io/commands/geosearch/) | Truy vấn một geospatial index để tìm các member nằm bên trong một vùng hình hộp (box) hoặc hình tròn. | O(N+log(M)) với N là số phần tử trong... | 6.2.0 |
| [GEOSEARCHSTORE](https://redis.io/commands/geosearchstore/) | Truy vấn một geospatial index để tìm các member nằm bên trong một vùng hình hộp hoặc hình tròn, có thể lưu kết quả. | O(N+log(M)) với N là số phần tử trong... | 6.2.0 |



Redis geospatial index cho phép bạn lưu trữ tọa độ và tìm kiếm chúng.
Cấu trúc dữ liệu này hữu ích để tìm các điểm lân cận trong một bán kính hoặc một khung giới hạn (bounding box) cho trước.

> [!NOTE]
> Cẩn thận đừng nhầm lẫn kiểu dữ liệu Geospatial với các tính năng
> [Geospatial](https://redis.io/docs/latest/develop/ai/search-and-query/advanced-concepts/geo)
> trong [Redis Search](https://redis.io/docs/latest/develop/ai/search-and-query).
> Mặc dù có một số điểm tương đồng giữa hai tính năng này, kiểu dữ liệu này dành cho
> các trường hợp sử dụng đơn giản hơn và không có nhiều tùy chọn định dạng và truy vấn
> như trong Redis Search.

## Ví dụ (Examples)

Giả sử bạn đang xây dựng một ứng dụng di động cho phép bạn tìm tất cả các trạm cho thuê xe đạp gần vị trí hiện tại của bạn nhất.

Thêm một vài vị trí vào một geospatial index:

*Nền tảng: Thêm tọa độ địa lý vào một geospatial index bằng GEOADD để lưu dữ liệu vị trí*

**Lệnh (Commands):** GEOADD

**Độ phức tạp (Complexity):**
- GEOADD: O(log(N)

**Có sẵn trong (Available in):** Redis CLI, C#, Go, Java (Synchronous - Jedis), JavaScript (Node.js), Python

##### Redis CLI

```
> GEOADD bikes:rentable -122.27652 37.805186 station:1
(integer) 1
> GEOADD bikes:rentable -122.2674626 37.8062344 station:2
(integer) 1
> GEOADD bikes:rentable -122.2469854 37.8104049 station:3
(integer) 1
```

##### C#

```csharp
        bool res1 = db.GeoAdd("bikes:rentable", -122.27652, 37.805186, "station:1");
        Console.WriteLine(res1);    // >>> True

        bool res2 = db.GeoAdd("bikes:rentable", -122.2674626, 37.8062344, "station:2");
        Console.WriteLine(res2);    // >>> True

        bool res3 = db.GeoAdd("bikes:rentable", -122.2469854, 37.8104049, "station:3");
        Console.WriteLine(res3);    // >>> True
```

##### Go

```go
	res1, err := rdb.GeoAdd(ctx, "bikes:rentable",
		&redis.GeoLocation{
			Longitude: -122.27652,
			Latitude:  37.805186,
			Name:      "station:1",
		}).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res1) // >>> 1

	res2, err := rdb.GeoAdd(ctx, "bikes:rentable",
		&redis.GeoLocation{
			Longitude: -122.2674626,
			Latitude:  37.8062344,
			Name:      "station:2",
		}).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res2) // >>> 1

	res3, err := rdb.GeoAdd(ctx, "bikes:rentable",
		&redis.GeoLocation{
			Longitude: -122.2469854,
			Latitude:  37.8104049,
			Name:      "station:3",
		}).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res3) // >>> 1
```

##### Java (Synchronous - Jedis)

```java
      long res1 = jedis.geoadd("bikes:rentable", -122.27652, 37.805186, "station:1");
      System.out.println(res1); // 1

      long res2 = jedis.geoadd("bikes:rentable", -122.2674626, 37.8062344, "station:2");
      System.out.println(res2); // 1

      long res3 = jedis.geoadd("bikes:rentable", -122.2469854, 37.8104049, "station:3");
      System.out.println(res2); // 1
```

##### JavaScript (Node.js)

```javascript
const res1 = await client.geoAdd('bikes:rentable', {
  longitude: -122.27652,
  latitude: 37.805186,
  member: 'station:1'
});
console.log(res1)  // 1

const res2 = await client.geoAdd('bikes:rentable', {
  longitude: -122.2674626,
  latitude: 37.8062344,
  member: 'station:2'
});
console.log(res2)  // 1

const res3 = await client.geoAdd('bikes:rentable', {
  longitude: -122.2469854,
  latitude: 37.8104049,
  member: 'station:3'
})
console.log(res3)  // 1
```

##### Python

```python
res1 = r.geoadd("bikes:rentable", [-122.27652, 37.805186, "station:1"])
print(res1)  # >>> 1

res2 = r.geoadd("bikes:rentable", [-122.2674626, 37.8062344, "station:2"])
print(res2)  # >>> 1

res3 = r.geoadd("bikes:rentable", [-122.2469854, 37.8104049, "station:3"])
print(res3)  # >>> 1
```



Tìm tất cả các vị trí nằm trong bán kính 5 km tính từ một vị trí cho trước, và trả về khoảng cách tới từng vị trí:

*Tìm kiếm lân cận (proximity search): Dùng GEOSEARCH để tìm các vị trí trong một bán kính hoặc khung giới hạn khi bạn cần khám phá các điểm ở gần*

**Độ khó (Difficulty):** Trung bình (Intermediate)

**Dựa trên (Builds upon):** geoadd

**Lệnh (Commands):** GEOSEARCH

**Độ phức tạp (Complexity):**
- GEOSEARCH: O(N+log(M)

**Có sẵn trong (Available in):** Redis CLI, C#, Go, Java (Synchronous - Jedis), JavaScript (Node.js), Python

##### Redis CLI

```
> GEOSEARCH bikes:rentable FROMLONLAT -122.2612767 37.7936847 BYRADIUS 5 km WITHDIST
1) 1) "station:1"
   2) "1.8523"
2) 1) "station:2"
   2) "1.4979"
3) 1) "station:3"
   2) "2.2441"
```

##### C#

```csharp
        GeoRadiusResult[] res4 = db.GeoSearch("bikes:rentable",
            -122.27652,
            37.805186,
            new GeoSearchCircle(5, GeoUnit.Kilometers)
        );

        foreach (GeoRadiusResult member in res4)
        {
            Console.WriteLine($"Member: '{member.Member}', distance: {member.Distance}, position: {member.Position}");
        }
        // >>> Member: 'station:1', distance: 0.0001, position: -122.27652043104172 37.80518485897756
        // >>> Member: 'station:2', distance: 0.8047, position: -122.26745992898941 37.80623423353753
        // >>> Member: 'station:3', distance: 2.6596, position: -122.24698394536972 37.81040384984464
```

##### Go

```go
	res4, err := rdb.GeoSearch(ctx, "bikes:rentable",
		&redis.GeoSearchQuery{
			Longitude:  -122.27652,
			Latitude:   37.805186,
			Radius:     5,
			RadiusUnit: "km",
		},
	).Result()

	if err != nil {
		panic(err)
	}

	fmt.Println(res4) // >>> [station:1 station:2 station:3]
```

##### Java (Synchronous - Jedis)

```java
      List<GeoRadiusResponse> res4 = jedis.geosearch(
          "bikes:rentable",
          new GeoCoordinate(-122.27652, 37.805186),
          5,
          GeoUnit.KM
      );
      List<String> members = res4.stream() //
          .map(GeoRadiusResponse::getMemberByString) //
          .collect(Collectors.toList());
      System.out.println(members); // [station:1, station:2, station:3]
```

##### JavaScript (Node.js)

```javascript
const res4 = await client.geoSearch(
  'bikes:rentable', {
    longitude: -122.27652,
    latitude: 37.805186,
  },
  { radius: 5,
    unit: 'km'
  }
);
console.log(res4)  // ['station:1', 'station:2', 'station:3']
```

##### Python

```python
res4 = r.geosearch(
    "bikes:rentable",
    longitude=-122.27652,
    latitude=37.805186,
    radius=5,
    unit="km",
)
print(res4)  # >>> ['station:1', 'station:2', 'station:3']
```



## Tìm hiểu thêm (Learn more)

* [Redis Geospatial Explained](https://www.youtube.com/watch?v=qftiVQraxmI) giới thiệu geospatial index bằng cách hướng dẫn bạn xây dựng một bản đồ các điểm tham quan trong công viên địa phương.
* [Redis University's RU101](https://university.redis.com/courses/ru101/) trình bày chi tiết về Redis geospatial index.

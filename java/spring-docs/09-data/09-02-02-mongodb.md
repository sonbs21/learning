# 9.2.2. MongoDB

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#data.nosql.mongodb>

[MongoDB](https://www.mongodb.com/) là một database tài liệu (document database) NoSQL mã nguồn mở, dùng schema giống JSON thay vì dữ liệu quan hệ dựa trên bảng truyền thống. Spring Boot cung cấp một số tiện ích để làm việc với MongoDB, bao gồm các "Starter" `spring-boot-starter-data-mongodb` và `spring-boot-starter-data-mongodb-reactive`.

## Kết nối tới database MongoDB (Connecting to a MongoDB Database)

Để truy cập các database MongoDB, bạn có thể inject một `org.springframework.data.mongodb.MongoDatabaseFactory` đã được tự động cấu hình. Theo mặc định, instance cố gắng kết nối tới một MongoDB server tại `mongodb://localhost/test`. Ví dụ sau cho thấy cách kết nối tới một database MongoDB:

**Java**

```java
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;

import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.stereotype.Component;

@Component
public class MyBean {

    private final MongoDatabaseFactory mongo;

    public MyBean(MongoDatabaseFactory mongo) {
        this.mongo = mongo;
    }

    // ...

    public MongoCollection<Document> someMethod() {
        MongoDatabase db = this.mongo.getMongoDatabase();
        return db.getCollection("users");
    }

}
```

**Kotlin**

```kotlin
import com.mongodb.client.MongoCollection
import org.bson.Document
import org.springframework.data.mongodb.MongoDatabaseFactory
import org.springframework.stereotype.Component

@Component
class MyBean(private val mongo: MongoDatabaseFactory) {

    // ...

    fun someMethod(): MongoCollection<Document> {
        val db = mongo.mongoDatabase
        return db.getCollection("users")
    }

}
```

Nếu bạn đã định nghĩa `MongoClient` của riêng mình, nó sẽ được dùng để tự động cấu hình một `MongoDatabaseFactory` phù hợp.

`MongoClient` được tự động cấu hình được tạo bằng một bean `MongoClientSettings`. Nếu bạn đã định nghĩa `MongoClientSettings` của riêng mình, nó sẽ được dùng mà không sửa đổi và các property `spring.data.mongodb` sẽ bị bỏ qua. Nếu không, một `MongoClientSettings` sẽ được tự động cấu hình và các property `spring.data.mongodb` sẽ được áp dụng lên nó. Trong cả hai trường hợp, bạn có thể khai báo một hoặc nhiều bean `MongoClientSettingsBuilderCustomizer` để tinh chỉnh cấu hình `MongoClientSettings`. Mỗi bean sẽ được gọi theo thứ tự với `MongoClientSettings.Builder` được dùng để xây dựng `MongoClientSettings`.

Bạn có thể đặt property `spring.data.mongodb.uri` để thay đổi URL và cấu hình các thiết lập bổ sung như *replica set*, như trong ví dụ sau:

**Properties**

```properties
spring.data.mongodb.uri=mongodb://user:[email protected]:27017,mongoserver2.example.com:23456/test
```

**Yaml**

```yaml
spring:
  data:
    mongodb:
      uri: "mongodb://user:[email protected]:27017,mongoserver2.example.com:23456/test"
```

Ngoài ra, bạn có thể chỉ định các chi tiết kết nối bằng các property riêng lẻ. Ví dụ, bạn có thể khai báo các thiết lập sau trong `application.properties`:

**Properties**

```properties
spring.data.mongodb.host=mongoserver1.example.com
spring.data.mongodb.port=27017
spring.data.mongodb.additional-hosts[0]=mongoserver2.example.com:23456
spring.data.mongodb.database=test
spring.data.mongodb.username=user
spring.data.mongodb.password=secret
```

**Yaml**

```yaml
spring:
  data:
    mongodb:
      host: "mongoserver1.example.com"
      port: 27017
      additional-hosts:
      - "mongoserver2.example.com:23456"
      database: "test"
      username: "user"
      password: "secret"
```

`MongoClient` được tự động cấu hình có thể được cấu hình để dùng SSL khi giao tiếp với server bằng cách đặt các property như trong ví dụ này:

**Properties**

```properties
spring.data.mongodb.uri=mongodb://user:[email protected]:27017,mongoserver2.example.com:23456/test
spring.data.mongodb.ssl.enabled=true
```

**Yaml**

```yaml
spring:
  data:
    mongodb:
      uri: "mongodb://user:[email protected]:27017,mongoserver2.example.com:23456/test"
      ssl:
        enabled: true
```

Tài liệu tin cậy SSL tùy chỉnh có thể được cấu hình trong một [SSL bundle](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.ssl) và được áp dụng cho `MongoClient` như trong ví dụ này:

**Properties**

```properties
spring.data.mongodb.uri=mongodb://user:[email protected]:27017,mongoserver2.example.com:23456/test
spring.data.mongodb.ssl.bundle=example
```

**Yaml**

```yaml
spring:
  data:
    mongodb:
      uri: "mongodb://user:[email protected]:27017,mongoserver2.example.com:23456/test"
      ssl:
        bundle: "example"
```

> **MẸO (TIP):** Nếu `spring.data.mongodb.port` không được chỉ định, giá trị mặc định `27017` được dùng. Bạn có thể xóa dòng này khỏi ví dụ đã trình bày ở trên.
>
> Bạn cũng có thể chỉ định cổng như một phần của địa chỉ host bằng cú pháp `host:port`. Định dạng này nên được dùng nếu bạn cần thay đổi cổng của một mục `additional-hosts`.

> **MẸO (TIP):** Nếu bạn không dùng Spring Data MongoDB, bạn có thể inject một bean `MongoClient` thay vì dùng `MongoDatabaseFactory`. Nếu bạn muốn kiểm soát hoàn toàn việc thiết lập kết nối MongoDB, bạn cũng có thể khai báo bean `MongoDatabaseFactory` hoặc `MongoClient` của riêng mình.

> **LƯU Ý (NOTE):** Nếu bạn đang dùng driver reactive, Netty là bắt buộc cho SSL. Auto-configuration cấu hình factory này tự động nếu có Netty và factory cần dùng chưa được tùy chỉnh.

## MongoTemplate

[Spring Data MongoDB](https://spring.io/projects/spring-data-mongodb) cung cấp class [`MongoTemplate`](https://docs.spring.io/spring-data/mongodb/docs/4.2.5/api/org/springframework/data/mongodb/core/MongoTemplate.html) có thiết kế rất giống với `JdbcTemplate` của Spring. Cũng như với `JdbcTemplate`, Spring Boot tự động cấu hình một bean để bạn inject template, như sau:

**Java**

```java
import com.mongodb.client.MongoCollection;
import org.bson.Document;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

@Component
public class MyBean {

    private final MongoTemplate mongoTemplate;

    public MyBean(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    // ...

    public MongoCollection<Document> someMethod() {
        return this.mongoTemplate.getCollection("users");
    }

}
```

**Kotlin**

```kotlin
import com.mongodb.client.MongoCollection
import org.bson.Document
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.stereotype.Component

@Component
class MyBean(private val mongoTemplate: MongoTemplate) {

    // ...

    fun someMethod(): MongoCollection<Document> {
        return mongoTemplate.getCollection("users")
    }

}
```

Xem [Javadoc của `MongoOperations`](https://docs.spring.io/spring-data/mongodb/docs/4.2.5/api/org/springframework/data/mongodb/core/MongoOperations.html) để biết đầy đủ chi tiết.

## Repository Spring Data MongoDB (Spring Data MongoDB Repositories)

Spring Data bao gồm hỗ trợ repository cho MongoDB. Cũng như với các repository JPA đã thảo luận trước đó, nguyên tắc cơ bản là các query được xây dựng tự động dựa trên tên phương thức.

Trên thực tế, cả Spring Data JPA và Spring Data MongoDB đều dùng chung cùng một hạ tầng. Bạn có thể lấy ví dụ JPA ở trên và, giả sử `City` giờ là một class dữ liệu MongoDB thay vì một `@Entity` JPA, nó hoạt động theo cùng cách, như trong ví dụ sau:

**Java**

```java
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

public interface CityRepository extends Repository<City, Long> {

    Page<City> findAll(Pageable pageable);

    City findByNameAndStateAllIgnoringCase(String name, String state);

}
```

**Kotlin**

```kotlin
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.Repository

interface CityRepository :
    Repository<City?, Long?> {
    fun findAll(pageable: Pageable?): Page<City?>?
    fun findByNameAndStateAllIgnoringCase(name: String?, state: String?): City?
}
```

Repository và document được tìm thấy thông qua việc quét. Theo mặc định, các [package auto-configuration](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#using.auto-configuration.packages) được quét. Bạn có thể tùy chỉnh các vị trí tìm kiếm repository và document bằng cách dùng lần lượt `@EnableMongoRepositories` và `@EntityScan`.

> **MẸO (TIP):** Để biết đầy đủ chi tiết về Spring Data MongoDB, bao gồm các công nghệ ánh xạ object phong phú của nó, xem [tài liệu tham khảo](https://spring.io/projects/spring-data-mongodb) của nó.

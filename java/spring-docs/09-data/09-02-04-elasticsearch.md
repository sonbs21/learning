# 9.2.4. Elasticsearch

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#data.nosql.elasticsearch>

[Elasticsearch](https://www.elastic.co/products/elasticsearch) là một công cụ tìm kiếm và phân tích mã nguồn mở, phân tán, theo kiểu RESTful. Spring Boot cung cấp auto-configuration cơ bản cho các client Elasticsearch.

Spring Boot hỗ trợ một số client:

- REST client cấp thấp chính thức
- Java API client chính thức
- `ReactiveElasticsearchClient` do Spring Data Elasticsearch cung cấp

Spring Boot cung cấp một "Starter" chuyên biệt, `spring-boot-starter-data-elasticsearch`.

## Kết nối tới Elasticsearch bằng REST client (Connecting to Elasticsearch Using REST clients)

Elasticsearch đi kèm hai REST client khác nhau mà bạn có thể dùng để truy vấn một cluster: [client cấp thấp](https://www.elastic.co/guide/en/elasticsearch/client/java-api-client/current/java-rest-low.html) từ module `org.elasticsearch.client:elasticsearch-rest-client` và [Java API client](https://www.elastic.co/guide/en/elasticsearch/client/java-api-client/current/index.html) từ module `co.elastic.clients:elasticsearch-java`. Ngoài ra, Spring Boot cung cấp hỗ trợ cho một client reactive từ module `org.springframework.data:spring-data-elasticsearch`. Theo mặc định, các client sẽ nhắm tới [localhost:9200](http://localhost:9200). Bạn có thể dùng các property `spring.elasticsearch.*` để tinh chỉnh thêm cách các client được cấu hình, như trong ví dụ sau:

**Properties**

```properties
spring.elasticsearch.uris=https://search.example.com:9200
spring.elasticsearch.socket-timeout=10s
spring.elasticsearch.username=user
spring.elasticsearch.password=secret
```

**Yaml**

```yaml
spring:
  elasticsearch:
    uris: "https://search.example.com:9200"
    socket-timeout: "10s"
    username: "user"
    password: "secret"
```

### Kết nối tới Elasticsearch bằng RestClient (Connecting to Elasticsearch Using RestClient)

Nếu bạn có `elasticsearch-rest-client` trên classpath, Spring Boot sẽ tự động cấu hình và đăng ký một bean `RestClient`. Ngoài các property đã mô tả ở trên, để tinh chỉnh `RestClient`, bạn có thể đăng ký một số lượng tùy ý các bean hiện thực `RestClientBuilderCustomizer` để tùy chỉnh nâng cao hơn. Để kiểm soát hoàn toàn cấu hình của client, hãy định nghĩa một bean `RestClientBuilder`.

Ngoài ra, nếu `elasticsearch-rest-client-sniffer` nằm trên classpath, một `Sniffer` được tự động cấu hình để tự động phát hiện các node từ một cluster Elasticsearch đang chạy và đặt chúng vào bean `RestClient`. Bạn có thể tinh chỉnh thêm cách `Sniffer` được cấu hình, như trong ví dụ sau:

**Properties**

```properties
spring.elasticsearch.restclient.sniffer.interval=10m
spring.elasticsearch.restclient.sniffer.delay-after-failure=30s
```

**Yaml**

```yaml
spring:
  elasticsearch:
    restclient:
      sniffer:
        interval: "10m"
        delay-after-failure: "30s"
```

### Kết nối tới Elasticsearch bằng ElasticsearchClient (Connecting to Elasticsearch Using ElasticsearchClient)

Nếu bạn có `co.elastic.clients:elasticsearch-java` trên classpath, Spring Boot sẽ tự động cấu hình và đăng ký một bean `ElasticsearchClient`.

`ElasticsearchClient` dùng một transport phụ thuộc vào `RestClient` đã mô tả ở trên. Vì vậy, các property đã mô tả ở trên có thể được dùng để cấu hình `ElasticsearchClient`. Hơn nữa, bạn có thể định nghĩa một bean `RestClientOptions` để kiểm soát thêm hành vi của transport.

### Kết nối tới Elasticsearch bằng ReactiveElasticsearchClient (Connecting to Elasticsearch using ReactiveElasticsearchClient)

[Spring Data Elasticsearch](https://spring.io/projects/spring-data-elasticsearch) đi kèm `ReactiveElasticsearchClient` để truy vấn các instance Elasticsearch theo cách reactive. Nếu bạn có Spring Data Elasticsearch và Reactor trên classpath, Spring Boot sẽ tự động cấu hình và đăng ký một `ReactiveElasticsearchClient`.

`ReactiveElasticsearchclient` dùng một transport phụ thuộc vào `RestClient` đã mô tả ở trên. Vì vậy, các property đã mô tả ở trên có thể được dùng để cấu hình `ReactiveElasticsearchClient`. Hơn nữa, bạn có thể định nghĩa một bean `RestClientOptions` để kiểm soát thêm hành vi của transport.

## Kết nối tới Elasticsearch bằng Spring Data (Connecting to Elasticsearch by Using Spring Data)

Để kết nối tới Elasticsearch, một bean `ElasticsearchClient` phải được định nghĩa, được Spring Boot tự động cấu hình hoặc được ứng dụng cung cấp thủ công (xem các phần trước). Với cấu hình này, một `ElasticsearchTemplate` có thể được inject giống như bất kỳ Spring bean nào khác, như trong ví dụ sau:

**Java**

```java
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.stereotype.Component;

@Component
public class MyBean {

    private final ElasticsearchTemplate template;

    public MyBean(ElasticsearchTemplate template) {
        this.template = template;
    }

    // ...

    public boolean someMethod(String id) {
        return this.template.exists(id, User.class);
    }

}
```

**Kotlin**

```kotlin
import org.springframework.stereotype.Component

@Component
class MyBean(private val template: org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate ) {

    // ...

    fun someMethod(id: String): Boolean {
        return template.exists(id, User::class.java)
    }

}
```

Khi có `spring-data-elasticsearch` và Reactor, Spring Boot cũng có thể tự động cấu hình một [ReactiveElasticsearchClient](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#data.nosql.elasticsearch.connecting-using-rest.reactiveclient) và một `ReactiveElasticsearchTemplate` dưới dạng bean. Chúng là tương đương reactive của các REST client khác.

## Repository Spring Data Elasticsearch (Spring Data Elasticsearch Repositories)

Spring Data bao gồm hỗ trợ repository cho Elasticsearch. Cũng như với các repository JPA đã thảo luận trước đó, nguyên tắc cơ bản là các query được tự động xây dựng cho bạn dựa trên tên phương thức.

Trên thực tế, cả Spring Data JPA và Spring Data Elasticsearch đều dùng chung cùng một hạ tầng. Bạn có thể lấy ví dụ JPA ở trên và, giả sử `City` giờ là một class `@Document` của Elasticsearch thay vì một `@Entity` JPA, nó hoạt động theo cùng cách.

Repository và document được tìm thấy thông qua việc quét. Theo mặc định, các [package auto-configuration](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#using.auto-configuration.packages) được quét. Bạn có thể tùy chỉnh các vị trí tìm kiếm repository và document bằng cách dùng lần lượt `@EnableElasticsearchRepositories` và `@EntityScan`.

> **MẸO (TIP):** Để biết đầy đủ chi tiết về Spring Data Elasticsearch, xem [tài liệu tham khảo](https://docs.spring.io/spring-data/elasticsearch/docs/current/reference/html/).

Spring Boot hỗ trợ cả repository Elasticsearch cổ điển và reactive, dùng các bean `ElasticsearchRestTemplate` hoặc `ReactiveElasticsearchTemplate`. Rất có thể những bean đó được Spring Boot tự động cấu hình khi có các dependency cần thiết.

Nếu bạn muốn dùng template của riêng mình để làm nền cho các repository Elasticsearch, bạn có thể thêm `@Bean` `ElasticsearchRestTemplate` hoặc `ElasticsearchOperations` của riêng mình, miễn là nó có tên `"elasticsearchTemplate"`. Điều tương tự áp dụng cho `ReactiveElasticsearchTemplate` và `ReactiveElasticsearchOperations`, với tên bean `"reactiveElasticsearchTemplate"`.

Bạn có thể chọn tắt hỗ trợ repository bằng property sau:

**Properties**

```properties
spring.data.elasticsearch.repositories.enabled=false
```

**Yaml**

```yaml
spring:
  data:
    elasticsearch:
      repositories:
        enabled: false
```

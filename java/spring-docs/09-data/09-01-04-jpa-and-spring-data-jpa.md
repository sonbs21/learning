# 9.1.4. JPA và Spring Data JPA (JPA and Spring Data JPA)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#data.sql.jpa-and-spring-data>

Java Persistence API là một công nghệ tiêu chuẩn cho phép bạn "ánh xạ" (map) object sang cơ sở dữ liệu quan hệ. POM `spring-boot-starter-data-jpa` cung cấp một cách nhanh chóng để bắt đầu. Nó cung cấp các dependency chính sau:

- Hibernate: Một trong những implementation JPA phổ biến nhất.
- Spring Data JPA: Giúp bạn hiện thực các repository dựa trên JPA.
- Spring ORM: Hỗ trợ ORM cốt lõi từ Spring Framework.

> **MẸO (TIP):** Chúng tôi không đi vào quá nhiều chi tiết về JPA hay [Spring Data](https://spring.io/projects/spring-data) ở đây. Bạn có thể làm theo hướng dẫn ["Accessing Data with JPA"](https://spring.io/guides/gs/accessing-data-jpa/) từ [spring.io](https://spring.io) và đọc tài liệu tham khảo của [Spring Data JPA](https://spring.io/projects/spring-data-jpa) và [Hibernate](https://hibernate.org/orm/documentation/).

## Các class Entity (Entity Classes)

Theo truyền thống, các class "Entity" của JPA được chỉ định trong file `persistence.xml`. Với Spring Boot, file này không cần thiết và "Entity Scanning" (quét entity) được dùng thay thế. Theo mặc định, các [package auto-configuration](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#using.auto-configuration.packages) được quét.

Mọi class được đánh annotation `@Entity`, `@Embeddable`, hoặc `@MappedSuperclass` đều được xem xét. Một class entity điển hình trông giống như ví dụ sau:

**Java**

```java
import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;

@Entity
public class City implements Serializable {

    @Id
    @GeneratedValue
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String state;

    // ... additional members, often include @OneToMany mappings

    protected City() {
        // no-args constructor required by JPA spec
        // this one is protected since it should not be used directly
    }

    public City(String name, String state) {
        this.name = name;
        this.state = state;
    }

    public String getName() {
        return this.name;
    }

    public String getState() {
        return this.state;
    }

    // ... etc

}
```

**Kotlin**

```kotlin
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import java.io.Serializable

@Entity
class City : Serializable {

    @Id
    @GeneratedValue
    private val id: Long? = null

    @Column(nullable = false)
    var name: String? = null
        private set

    // ... etc
    @Column(nullable = false)
    var state: String? = null
        private set

    // ... additional members, often include @OneToMany mappings

    protected constructor() {
        // no-args constructor required by JPA spec
        // this one is protected since it should not be used directly
    }

    constructor(name: String?, state: String?) {
        this.name = name
        this.state = state
    }

}
```

> **MẸO (TIP):** Bạn có thể tùy chỉnh các vị trí quét entity bằng annotation `@EntityScan`. Xem hướng dẫn how-to "[Tách định nghĩa @Entity khỏi cấu hình Spring (Separate @Entity Definitions from Spring Configuration)](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.data-access.separate-entity-definitions-from-spring-configuration)".

## Repository Spring Data JPA (Spring Data JPA Repositories)

Các repository [Spring Data JPA](https://spring.io/projects/spring-data-jpa) là các interface mà bạn có thể định nghĩa để truy cập dữ liệu. Các query JPA được tạo tự động từ tên phương thức của bạn. Ví dụ, một interface `CityRepository` có thể khai báo phương thức `findAllByState(String state)` để tìm tất cả các thành phố trong một bang nhất định.

Với các query phức tạp hơn, bạn có thể đánh annotation [`Query`](https://docs.spring.io/spring-data/jpa/docs/3.2.5/api/org/springframework/data/jpa/repository/Query.html) của Spring Data lên phương thức.

Các repository Spring Data thường kế thừa từ interface [`Repository`](https://docs.spring.io/spring-data/commons/docs/3.2.5/api/org/springframework/data/repository/Repository.html) hoặc [`CrudRepository`](https://docs.spring.io/spring-data/commons/docs/3.2.5/api/org/springframework/data/repository/CrudRepository.html). Nếu bạn dùng auto-configuration, các [package auto-configuration](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#using.auto-configuration.packages) sẽ được tìm kiếm để tìm repository.

> **MẸO (TIP):** Bạn có thể tùy chỉnh các vị trí tìm kiếm repository bằng `@EnableJpaRepositories`.

Ví dụ sau cho thấy một định nghĩa interface repository Spring Data điển hình:

**Java**

```java
import org.springframework.boot.docs.data.sql.jpaandspringdata.entityclasses.City;
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
import org.springframework.boot.docs.data.sql.jpaandspringdata.entityclasses.City
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.Repository

interface CityRepository : Repository<City?, Long?> {

    fun findAll(pageable: Pageable?): Page<City?>?

    fun findByNameAndStateAllIgnoringCase(name: String?, state: String?): City?

}
```

Các repository Spring Data JPA hỗ trợ ba chế độ khởi động (bootstrapping) khác nhau: default, deferred, và lazy. Để bật bootstrapping deferred hoặc lazy, hãy đặt property `spring.data.jpa.repositories.bootstrap-mode` lần lượt thành `deferred` hoặc `lazy`. Khi dùng bootstrapping deferred hoặc lazy, `EntityManagerFactoryBuilder` được tự động cấu hình sẽ dùng `AsyncTaskExecutor` của context, nếu có, làm bootstrap executor. Nếu có nhiều hơn một, cái có tên `applicationTaskExecutor` sẽ được dùng.

> **LƯU Ý (NOTE):** Khi dùng bootstrapping deferred hoặc lazy, hãy đảm bảo trì hoãn mọi truy cập tới hạ tầng JPA cho đến sau giai đoạn khởi động application context. Bạn có thể dùng `SmartInitializingSingleton` để gọi mọi khởi tạo cần đến hạ tầng JPA. Với các component JPA (như converter) được tạo dưới dạng Spring bean, hãy dùng `ObjectProvider` để trì hoãn việc phân giải dependency, nếu có.

> **MẸO (TIP):** Chúng tôi mới chỉ chạm vào bề mặt của Spring Data JPA. Để biết đầy đủ chi tiết, xem [tài liệu tham khảo Spring Data JPA](https://docs.spring.io/spring-data/jpa/reference/3.2.5/).

## Repository Spring Data Envers (Spring Data Envers Repositories)

Nếu có [Spring Data Envers](https://spring.io/projects/spring-data-envers), các repository JPA được tự động cấu hình để hỗ trợ các query Envers điển hình.

Để dùng Spring Data Envers, hãy đảm bảo repository của bạn kế thừa từ `RevisionRepository` như trong ví dụ sau:

**Java**

```java
import org.springframework.boot.docs.data.sql.jpaandspringdata.entityclasses.Country;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.history.RevisionRepository;

public interface CountryRepository extends RevisionRepository<Country, Long, Integer>, Repository<Country, Long> {

    Page<Country> findAll(Pageable pageable);

}
```

**Kotlin**

```kotlin
import org.springframework.boot.docs.data.sql.jpaandspringdata.entityclasses.Country
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.Repository
import org.springframework.data.repository.history.RevisionRepository

interface CountryRepository :
        RevisionRepository<Country?, Long?, Int>,
        Repository<Country?, Long?> {

    fun findAll(pageable: Pageable?): Page<Country?>?

}
```

> **LƯU Ý (NOTE):** Để biết thêm chi tiết, xem [tài liệu tham khảo Spring Data Envers](https://docs.spring.io/spring-data/jpa/reference/3.2.5//#envers).

## Tạo và xóa database JPA (Creating and Dropping JPA Databases)

Theo mặc định, database JPA được tự động tạo **chỉ** khi bạn dùng database nhúng (H2, HSQL, hoặc Derby). Bạn có thể cấu hình tường minh các thiết lập JPA bằng các property `spring.jpa.*`. Ví dụ, để tạo và xóa bảng, bạn có thể thêm dòng sau vào `application.properties`:

**Properties**

```properties
spring.jpa.hibernate.ddl-auto=create-drop
```

**Yaml**

```yaml
spring:
  jpa:
    hibernate.ddl-auto: "create-drop"
```

> **LƯU Ý (NOTE):** Tên property nội bộ của chính Hibernate cho thiết lập này (nếu bạn nhớ nó rõ hơn) là `hibernate.hbm2ddl.auto`. Bạn có thể đặt nó, cùng với các property gốc khác của Hibernate, bằng `spring.jpa.properties.*` (tiền tố được loại bỏ trước khi thêm chúng vào entity manager). Dòng sau cho thấy một ví dụ về việc đặt các property JPA cho Hibernate:

**Properties**

```properties
spring.jpa.properties.hibernate[globally_quoted_identifiers]=true
```

**Yaml**

```yaml
spring:
  jpa:
    properties:
      hibernate:
        "globally_quoted_identifiers": "true"
```

> Dòng trong ví dụ trên truyền giá trị `true` cho property `hibernate.globally_quoted_identifiers` tới entity manager của Hibernate.

Theo mặc định, việc thực thi (hoặc kiểm tra) DDL được trì hoãn cho đến khi `ApplicationContext` đã khởi động. Cũng có cờ `spring.jpa.generate-ddl`, nhưng nó không được dùng nếu auto-configuration của Hibernate đang hoạt động, vì các thiết lập `ddl-auto` chi tiết hơn.

## Open EntityManager in View

Nếu bạn đang chạy một ứng dụng web, Spring Boot theo mặc định đăng ký [`OpenEntityManagerInViewInterceptor`](https://docs.spring.io/spring-framework/docs/6.1.6/javadoc-api/org/springframework/orm/jpa/support/OpenEntityManagerInViewInterceptor.html) để áp dụng mẫu "Open EntityManager in View", cho phép lazy loading trong các web view. Nếu bạn không muốn hành vi này, bạn nên đặt `spring.jpa.open-in-view` thành `false` trong `application.properties`.

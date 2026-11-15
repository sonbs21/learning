# 9.1.1. Cấu hình DataSource (Configure a DataSource)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#data.sql.datasource>

Interface `javax.sql.DataSource` của Java cung cấp một phương thức tiêu chuẩn để làm việc với các kết nối database. Theo truyền thống, một `DataSource` dùng một `URL` cùng với một số thông tin xác thực để thiết lập kết nối database.

> **MẸO (TIP):** Xem [phần "How-to"](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.data-access.configure-custom-datasource) để có các ví dụ nâng cao hơn, thường là để kiểm soát hoàn toàn cấu hình của DataSource.

## Hỗ trợ database nhúng (Embedded Database Support)

Việc phát triển ứng dụng bằng một database nhúng in-memory thường rất tiện lợi. Hiển nhiên, database in-memory không cung cấp lưu trữ bền vững. Bạn cần nạp dữ liệu vào database khi ứng dụng khởi động và chuẩn bị bỏ đi dữ liệu khi ứng dụng kết thúc.

> **MẸO (TIP):** Phần "How-to" có [một phần về cách khởi tạo database](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.data-initialization).

Spring Boot có thể tự động cấu hình các database nhúng [H2](https://www.h2database.com), [HSQL](https://hsqldb.org/), và [Derby](https://db.apache.org/derby/). Bạn không cần cung cấp URL kết nối nào. Bạn chỉ cần thêm dependency build tới database nhúng mà bạn muốn dùng. Nếu có nhiều database nhúng trên classpath, hãy đặt configuration property `spring.datasource.embedded-database-connection` để kiểm soát cái nào được dùng. Đặt property thành `none` sẽ vô hiệu hóa auto-configuration của database nhúng.

> **LƯU Ý (NOTE):** Nếu bạn đang dùng tính năng này trong test, bạn có thể nhận thấy cùng một database được tái sử dụng bởi toàn bộ test suite bất kể số lượng application context mà bạn dùng. Nếu bạn muốn đảm bảo mỗi context có một database nhúng riêng, bạn nên đặt `spring.datasource.generate-unique-name` thành `true`.

Ví dụ, các dependency POM điển hình sẽ như sau:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
<dependency>
    <groupId>org.hsqldb</groupId>
    <artifactId>hsqldb</artifactId>
    <scope>runtime</scope>
</dependency>
```

> **LƯU Ý (NOTE):** Bạn cần dependency tới `spring-jdbc` để một database nhúng được tự động cấu hình. Trong ví dụ này, nó được kéo vào một cách bắc cầu thông qua `spring-boot-starter-data-jpa`.

> **MẸO (TIP):** Nếu vì lý do nào đó, bạn cấu hình URL kết nối cho một database nhúng, hãy cẩn thận đảm bảo việc tự động tắt (automatic shutdown) của database bị vô hiệu hóa. Nếu bạn dùng H2, bạn nên dùng `DB_CLOSE_ON_EXIT=FALSE` để làm điều đó. Nếu bạn dùng HSQLDB, bạn nên đảm bảo không dùng `shutdown=true`. Vô hiệu hóa việc tự động tắt của database cho phép Spring Boot kiểm soát khi nào database được đóng, nhờ đó đảm bảo việc đóng diễn ra khi không còn cần truy cập database nữa.

## Kết nối tới database production (Connection to a Production Database)

Các kết nối database production cũng có thể được tự động cấu hình bằng một `DataSource` có pooling.

## Cấu hình DataSource (DataSource Configuration)

Cấu hình DataSource được kiểm soát bởi các configuration property bên ngoài trong `spring.datasource.*`. Ví dụ, bạn có thể khai báo phần sau trong `application.properties`:

**Properties**

```properties
spring.datasource.url=jdbc:mysql://localhost/test
spring.datasource.username=dbuser
spring.datasource.password=dbpass
```

**Yaml**

```yaml
spring:
  datasource:
    url: "jdbc:mysql://localhost/test"
    username: "dbuser"
    password: "dbpass"
```

> **LƯU Ý (NOTE):** Tối thiểu bạn nên chỉ định URL bằng cách đặt property `spring.datasource.url`. Nếu không, Spring Boot sẽ cố gắng tự động cấu hình một database nhúng.

> **MẸO (TIP):** Spring Boot có thể suy ra class JDBC driver cho hầu hết các database từ URL. Nếu bạn cần chỉ định một class cụ thể, bạn có thể dùng property `spring.datasource.driver-class-name`.

> **LƯU Ý (NOTE):** Để một `DataSource` có pooling được tạo ra, chúng tôi cần có khả năng xác minh rằng một class `Driver` hợp lệ có sẵn, vì vậy chúng tôi kiểm tra điều đó trước khi làm bất cứ điều gì. Nói cách khác, nếu bạn đặt `spring.datasource.driver-class-name=com.mysql.jdbc.Driver`, thì class đó phải có thể nạp được.

Xem [`DataSourceProperties`](https://github.com/spring-projects/spring-boot/tree/v3.2.5/spring-boot-project/spring-boot-autoconfigure/src/main/java/org/springframework/boot/autoconfigure/jdbc/DataSourceProperties.java) để biết thêm các tùy chọn được hỗ trợ. Đây là các tùy chọn tiêu chuẩn hoạt động bất kể [implementation thực tế](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#data.sql.datasource.connection-pool). Cũng có thể tinh chỉnh các thiết lập đặc thù cho từng implementation bằng tiền tố tương ứng của chúng (`spring.datasource.hikari.*`, `spring.datasource.tomcat.*`, `spring.datasource.dbcp2.*`, và `spring.datasource.oracleucp.*`). Xem tài liệu của implementation connection pool mà bạn đang dùng để biết thêm chi tiết.

Ví dụ, nếu bạn dùng [connection pool của Tomcat](https://tomcat.apache.org/tomcat-10.1-doc/jdbc-pool.html#Common_Attributes), bạn có thể tùy chỉnh nhiều thiết lập bổ sung, như trong ví dụ sau:

**Properties**

```properties
spring.datasource.tomcat.max-wait=10000
spring.datasource.tomcat.max-active=50
spring.datasource.tomcat.test-on-borrow=true
```

**Yaml**

```yaml
spring:
  datasource:
    tomcat:
      max-wait: 10000
      max-active: 50
      test-on-borrow: true
```

Thiết lập này sẽ khiến pool chờ 10000ms trước khi ném exception nếu không có kết nối nào khả dụng, giới hạn số lượng kết nối tối đa là 50 và kiểm tra (validate) kết nối trước khi mượn nó từ pool.

## Các connection pool được hỗ trợ (Supported Connection Pools)

Spring Boot dùng thuật toán sau để chọn một implementation cụ thể:

1. Chúng tôi ưu tiên [HikariCP](https://github.com/brettwooldridge/HikariCP) vì hiệu năng và khả năng xử lý đồng thời của nó. Nếu có HikariCP, chúng tôi luôn chọn nó.
2. Nếu không, nếu có `DataSource` pooling của Tomcat, chúng tôi dùng nó.
3. Nếu không, nếu có [Commons DBCP2](https://commons.apache.org/proper/commons-dbcp/), chúng tôi dùng nó.
4. Nếu không có HikariCP, Tomcat, và DBCP2 nhưng có Oracle UCP, chúng tôi dùng nó.

> **LƯU Ý (NOTE):** Nếu bạn dùng các "starter" `spring-boot-starter-jdbc` hoặc `spring-boot-starter-data-jpa`, bạn sẽ tự động có dependency tới `HikariCP`.

Bạn có thể bỏ qua hoàn toàn thuật toán đó và chỉ định connection pool cần dùng bằng cách đặt property `spring.datasource.type`. Điều này đặc biệt quan trọng nếu bạn chạy ứng dụng trong một Tomcat container, vì `tomcat-jdbc` được cung cấp mặc định.

Các connection pool bổ sung luôn có thể được cấu hình thủ công bằng `DataSourceBuilder`. Nếu bạn định nghĩa bean `DataSource` của riêng mình, auto-configuration sẽ không diễn ra. Các connection pool sau được `DataSourceBuilder` hỗ trợ:

- HikariCP
- `Datasource` pooling của Tomcat
- Commons DBCP2
- Oracle UCP & `OracleDataSource`
- `SimpleDriverDataSource` của Spring Framework
- `JdbcDataSource` của H2
- `PGSimpleDataSource` của PostgreSQL
- C3P0

## Kết nối tới một JNDI DataSource (Connection to a JNDI DataSource)

Nếu bạn triển khai ứng dụng Spring Boot lên một Application Server, bạn có thể muốn cấu hình và quản lý DataSource bằng các tính năng có sẵn của Application Server và truy cập nó bằng JNDI.

Property `spring.datasource.jndi-name` có thể được dùng thay thế cho các property `spring.datasource.url`, `spring.datasource.username`, và `spring.datasource.password` để truy cập `DataSource` từ một vị trí JNDI cụ thể. Ví dụ, phần sau trong `application.properties` cho thấy cách bạn có thể truy cập một `DataSource` được định nghĩa trong JBoss AS:

**Properties**

```properties
spring.datasource.jndi-name=java:jboss/datasources/customers
```

**Yaml**

```yaml
spring:
  datasource:
    jndi-name: "java:jboss/datasources/customers"
```

# 18.9.5. Dùng công cụ migration cơ sở dữ liệu cấp cao hơn (Use a Higher-level Database Migration Tool)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.data-initialization.migration-tool>

Spring Boot hỗ trợ hai công cụ migration cấp cao hơn: [Flyway](https://flywaydb.org/) và [Liquibase](https://www.liquibase.org/).

## Thực thi migration cơ sở dữ liệu Flyway lúc khởi động (Execute Flyway Database Migrations on Startup)

Để tự động chạy các migration cơ sở dữ liệu Flyway lúc khởi động, hãy thêm `org.flywaydb:flyway-core` vào classpath.

Thông thường, migration là các script có dạng `V<VERSION>__<NAME>.sql` (với `<VERSION>` là một phiên bản phân tách bằng dấu gạch dưới, chẳng hạn như '1' hoặc '2_1'). Theo mặc định, chúng nằm trong một thư mục tên là `classpath:db/migration`, nhưng bạn có thể sửa vị trí đó bằng cách đặt `spring.flyway.locations`. Đây là một danh sách phân tách bằng dấu phẩy gồm một hoặc nhiều vị trí `classpath:` hoặc `filesystem:`. Ví dụ, cấu hình sau sẽ tìm script ở cả vị trí classpath mặc định và thư mục `/opt/migration`:

**Properties**

```properties
spring.flyway.locations=classpath:db/migration,filesystem:/opt/migration
```

**Yaml**

```yaml
spring:
  flyway:
    locations: "classpath:db/migration,filesystem:/opt/migration"
```

Bạn cũng có thể thêm một placeholder đặc biệt `{vendor}` để dùng các script đặc thù cho nhà cung cấp. Giả sử như sau:

**Properties**

```properties
spring.flyway.locations=classpath:db/migration/{vendor}
```

**Yaml**

```yaml
spring:
  flyway:
    locations: "classpath:db/migration/{vendor}"
```

Thay vì dùng `db/migration`, cấu hình trên đặt thư mục cần dùng theo kiểu cơ sở dữ liệu (chẳng hạn như `db/migration/mysql` cho MySQL). Danh sách các cơ sở dữ liệu được hỗ trợ có trong [`DatabaseDriver`](https://github.com/spring-projects/spring-boot/tree/v3.2.5/spring-boot-project/spring-boot/src/main/java/org/springframework/boot/jdbc/DatabaseDriver.java).

Migration cũng có thể được viết bằng Java. Flyway sẽ được auto-configure với mọi bean implement `JavaMigration`.

[`FlywayProperties`](https://github.com/spring-projects/spring-boot/tree/v3.2.5/spring-boot-project/spring-boot-autoconfigure/src/main/java/org/springframework/boot/autoconfigure/flyway/FlywayProperties.java) cung cấp hầu hết các thiết lập của Flyway và một tập nhỏ các property bổ sung có thể được dùng để vô hiệu hóa migration hoặc tắt việc kiểm tra vị trí. Nếu bạn cần kiểm soát nhiều hơn đối với cấu hình, hãy cân nhắc đăng ký một bean `FlywayConfigurationCustomizer`.

Spring Boot gọi `Flyway.migrate()` để thực hiện migration cơ sở dữ liệu. Nếu bạn muốn kiểm soát nhiều hơn, hãy cung cấp một `@Bean` implement [`FlywayMigrationStrategy`](https://github.com/spring-projects/spring-boot/tree/v3.2.5/spring-boot-project/spring-boot-autoconfigure/src/main/java/org/springframework/boot/autoconfigure/flyway/FlywayMigrationStrategy.java).

Flyway hỗ trợ các [callback](https://flywaydb.org/documentation/concepts/callbacks) SQL và Java. Để dùng các callback dựa trên SQL, hãy đặt các script callback trong thư mục `classpath:db/migration`. Để dùng các callback dựa trên Java, hãy tạo một hoặc nhiều bean implement `Callback`. Mọi bean như vậy đều được tự động đăng ký với `Flyway`. Chúng có thể được sắp thứ tự bằng cách dùng `@Order` hoặc bằng cách implement `Ordered`. Các bean implement interface `FlywayCallback` đã bị deprecated cũng có thể được phát hiện, tuy nhiên chúng không thể được dùng cùng với các bean `Callback`.

Theo mặc định, Flyway autowire `DataSource` (`@Primary`) trong context của bạn và dùng nó cho migration. Nếu bạn muốn dùng một `DataSource` khác, bạn có thể tạo một cái và đánh dấu `@Bean` của nó là `@FlywayDataSource`. Nếu bạn làm vậy và muốn có hai data source, hãy nhớ tạo một cái khác và đánh dấu nó là `@Primary`. Ngoài ra, bạn có thể dùng `DataSource` native của Flyway bằng cách đặt `spring.flyway.[url,user,password]` trong các property bên ngoài. Đặt `spring.flyway.url` hoặc `spring.flyway.user` là đủ để khiến Flyway dùng `DataSource` của riêng nó. Nếu bất kỳ property nào trong ba property chưa được đặt, giá trị của property `spring.datasource` tương đương sẽ được dùng.

Bạn cũng có thể dùng Flyway để cung cấp dữ liệu cho các kịch bản cụ thể. Ví dụ, bạn có thể đặt các migration dành riêng cho test trong `src/test/resources` và chúng chỉ được chạy khi ứng dụng của bạn khởi động để kiểm thử. Ngoài ra, bạn có thể dùng cấu hình theo profile để tùy chỉnh `spring.flyway.locations` sao cho một số migration nhất định chỉ chạy khi một profile cụ thể đang hoạt động. Ví dụ, trong `application-dev.properties`, bạn có thể chỉ định thiết lập sau:

**Properties**

```properties
spring.flyway.locations=classpath:/db/migration,classpath:/dev/db/migration
```

**Yaml**

```yaml
spring:
  flyway:
    locations: "classpath:/db/migration,classpath:/dev/db/migration"
```

Với thiết lập đó, các migration trong `dev/db/migration` chỉ chạy khi profile `dev` đang hoạt động.

## Thực thi migration cơ sở dữ liệu Liquibase lúc khởi động (Execute Liquibase Database Migrations on Startup)

Để tự động chạy các migration cơ sở dữ liệu Liquibase lúc khởi động, hãy thêm `org.liquibase:liquibase-core` vào classpath.

> **LƯU Ý (NOTE):** Khi bạn thêm `org.liquibase:liquibase-core` vào classpath, các migration cơ sở dữ liệu chạy theo mặc định cả trong lúc ứng dụng khởi động lẫn trước khi các test của bạn chạy. Hành vi này có thể được tùy chỉnh bằng cách dùng property `spring.liquibase.enabled`, đặt các giá trị khác nhau trong cấu hình `main` và `test`. Không thể dùng hai cách khác nhau để khởi tạo cơ sở dữ liệu (ví dụ Liquibase cho lúc ứng dụng khởi động, JPA cho lúc chạy test).

Theo mặc định, master change log được đọc từ `db/changelog/db.changelog-master.yaml`, nhưng bạn có thể thay đổi vị trí bằng cách đặt `spring.liquibase.change-log`. Ngoài YAML, Liquibase cũng hỗ trợ các định dạng change log JSON, XML, và SQL.

Theo mặc định, Liquibase autowire `DataSource` (`@Primary`) trong context của bạn và dùng nó cho migration. Nếu bạn cần dùng một `DataSource` khác, bạn có thể tạo một cái và đánh dấu `@Bean` của nó là `@LiquibaseDataSource`. Nếu bạn làm vậy và muốn có hai data source, hãy nhớ tạo một cái khác và đánh dấu nó là `@Primary`. Ngoài ra, bạn có thể dùng `DataSource` native của Liquibase bằng cách đặt `spring.liquibase.[driver-class-name,url,user,password]` trong các property bên ngoài. Đặt `spring.liquibase.url` hoặc `spring.liquibase.user` là đủ để khiến Liquibase dùng `DataSource` của riêng nó. Nếu bất kỳ property nào trong ba property chưa được đặt, giá trị của property `spring.datasource` tương đương sẽ được dùng.

Xem [`LiquibaseProperties`](https://github.com/spring-projects/spring-boot/tree/v3.2.5/spring-boot-project/spring-boot-autoconfigure/src/main/java/org/springframework/boot/autoconfigure/liquibase/LiquibaseProperties.java) để biết chi tiết về các thiết lập có sẵn như context, schema mặc định, và các thiết lập khác.

## Dùng Flyway cho migration chỉ dành cho test (Use Flyway for test-only migrations)

Nếu bạn muốn tạo các migration Flyway để nạp dữ liệu vào cơ sở dữ liệu test, hãy đặt chúng trong `src/test/resources/db/migration`. Một file có tên, ví dụ, `src/test/resources/db/migration/V9999__test-data.sql` sẽ được thực thi sau các migration production và chỉ khi bạn đang chạy test. Bạn có thể dùng file này để tạo dữ liệu test cần thiết. File này sẽ không được đóng gói vào uber jar hoặc container của bạn.

## Dùng Liquibase cho migration chỉ dành cho test (Use Liquibase for test-only migrations)

Nếu bạn muốn tạo các migration Liquibase để nạp dữ liệu vào cơ sở dữ liệu test, bạn phải tạo một test changelog có bao gồm cả production changelog.

Trước tiên, bạn cần cấu hình Liquibase dùng một changelog khác khi chạy test. Một cách để làm điều này là tạo một profile `test` của Spring Boot và đặt các property Liquibase vào đó. Để làm vậy, hãy tạo một file tên là `src/test/resources/application-test.properties` và đặt property sau vào đó:

**Properties**

```properties
spring.liquibase.change-log=classpath:/db/changelog/db.changelog-test.yaml
```

**Yaml**

```yaml
spring:
  liquibase:
    change-log: "classpath:/db/changelog/db.changelog-test.yaml"
```

Điều này cấu hình Liquibase dùng một changelog khác khi chạy trong profile `test`.

Bây giờ hãy tạo file changelog tại `src/test/resources/db/changelog/db.changelog-test.yaml`:

```yaml
databaseChangeLog:
  - include:
      file: classpath:/db/changelog/db.changelog-master.yaml
  - changeSet:
      runOrder: "last"
      id: "test"
      changes:
        # Insert your changes here
```

Changelog này sẽ được dùng khi các test được chạy và nó sẽ không được đóng gói vào uber jar hoặc container của bạn. Nó bao gồm production changelog và sau đó khai báo một changeset mới, có thiết lập `runOrder: last` chỉ định rằng nó chạy sau khi tất cả các changeset production đã được chạy. Bây giờ bạn có thể dùng ví dụ [insert changeset](https://docs.liquibase.com/change-types/insert.html) để chèn dữ liệu hoặc [sql changeset](https://docs.liquibase.com/change-types/sql.html) để thực thi SQL trực tiếp.

Việc cuối cùng cần làm là cấu hình Spring Boot kích hoạt profile `test` khi chạy test. Để làm điều này, bạn có thể thêm annotation `@ActiveProfiles("test")` vào các class test được annotate `@SpringBootTest`.

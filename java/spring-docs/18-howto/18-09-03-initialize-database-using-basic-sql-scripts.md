# 18.9.3. Khởi tạo cơ sở dữ liệu bằng các script SQL cơ bản (Initialize a Database Using Basic SQL Scripts)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.data-initialization.using-basic-sql-scripts>

Spring Boot có thể tự động tạo schema (các script DDL) của JDBC `DataSource` hoặc R2DBC `ConnectionFactory` và khởi tạo dữ liệu của nó (các script DML).

Theo mặc định, nó nạp các script schema từ `optional:classpath*:schema.sql` và các script dữ liệu từ `optional:classpath*:data.sql`. Vị trí của các script schema và dữ liệu này có thể được tùy chỉnh lần lượt bằng `spring.sql.init.schema-locations` và `spring.sql.init.data-locations`. Tiền tố `optional:` nghĩa là ứng dụng sẽ khởi động ngay cả khi các file không tồn tại. Để ứng dụng khởi động thất bại khi thiếu file, hãy bỏ tiền tố `optional:`.

Ngoài ra, Spring Boot xử lý các file `optional:classpath*:schema-${platform}.sql` và `optional:classpath*:data-${platform}.sql` (nếu có), trong đó `${platform}` là giá trị của `spring.sql.init.platform`. Điều này cho phép bạn chuyển sang các script đặc thù cho cơ sở dữ liệu nếu cần. Ví dụ, bạn có thể chọn đặt nó thành tên nhà cung cấp của cơ sở dữ liệu (`hsqldb`, `h2`, `oracle`, `mysql`, `postgresql`, v.v.).

Theo mặc định, việc khởi tạo cơ sở dữ liệu SQL chỉ được thực hiện khi dùng một embedded in-memory database. Để luôn khởi tạo một cơ sở dữ liệu SQL, bất kể kiểu của nó, hãy đặt `spring.sql.init.mode` thành `always`. Tương tự, để vô hiệu hóa việc khởi tạo, hãy đặt `spring.sql.init.mode` thành `never`. Theo mặc định, Spring Boot bật tính năng fail-fast của database initializer dựa trên script. Điều này có nghĩa là, nếu các script gây ra exception, ứng dụng sẽ khởi động thất bại. Bạn có thể tinh chỉnh hành vi đó bằng cách đặt `spring.sql.init.continue-on-error`.

Theo mặc định, việc khởi tạo `DataSource` dựa trên script được thực hiện trước khi bất kỳ bean JPA `EntityManagerFactory` nào được tạo. `schema.sql` có thể được dùng để tạo schema cho các entity do JPA quản lý và `data.sql` có thể được dùng để nạp dữ liệu vào nó. Mặc dù chúng tôi không khuyến nghị dùng nhiều công nghệ khởi tạo data source, nếu bạn muốn việc khởi tạo `DataSource` dựa trên script có thể xây dựng tiếp trên việc tạo schema do Hibernate thực hiện, hãy đặt `spring.jpa.defer-datasource-initialization` thành `true`. Điều này sẽ trì hoãn việc khởi tạo data source cho đến sau khi mọi bean `EntityManagerFactory` đã được tạo và khởi tạo. Khi đó, `schema.sql` có thể được dùng để bổ sung cho bất kỳ việc tạo schema nào do Hibernate thực hiện và `data.sql` có thể được dùng để nạp dữ liệu vào nó.

> **LƯU Ý (NOTE):** Các script khởi tạo hỗ trợ `--` cho comment một dòng và `/* */` cho comment khối. Các định dạng comment khác không được hỗ trợ.

Nếu bạn đang dùng một [công cụ migration cơ sở dữ liệu cấp cao hơn (Higher-level Database Migration Tool)](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.data-initialization.migration-tool), như Flyway hoặc Liquibase, bạn nên chỉ dùng riêng chúng để tạo và khởi tạo schema. Việc dùng các script cơ bản `schema.sql` và `data.sql` cùng với Flyway hoặc Liquibase không được khuyến nghị và hỗ trợ này sẽ bị loại bỏ trong một bản phát hành tương lai.

Nếu bạn cần khởi tạo dữ liệu test bằng một công cụ migration cơ sở dữ liệu cấp cao hơn, vui lòng xem các phần về [Flyway](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.data-initialization.migration-tool.flyway-tests) và [Liquibase](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.data-initialization.migration-tool.liquibase-tests).

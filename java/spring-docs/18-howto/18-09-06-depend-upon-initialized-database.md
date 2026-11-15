# 18.9.6. Phụ thuộc vào một cơ sở dữ liệu đã được khởi tạo (Depend Upon an Initialized Database)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.data-initialization.dependencies>

Việc khởi tạo cơ sở dữ liệu được thực hiện trong khi ứng dụng đang khởi động như một phần của quá trình refresh application context. Để cho phép truy cập một cơ sở dữ liệu đã được khởi tạo trong lúc khởi động, các bean đóng vai trò database initializer và các bean yêu cầu cơ sở dữ liệu đã được khởi tạo sẽ được phát hiện tự động. Các bean mà việc khởi tạo của chúng phụ thuộc vào việc cơ sở dữ liệu đã được khởi tạo được cấu hình để phụ thuộc vào những bean khởi tạo nó. Nếu, trong lúc khởi động, ứng dụng của bạn cố truy cập cơ sở dữ liệu mà nó chưa được khởi tạo, bạn có thể cấu hình thêm việc phát hiện các bean khởi tạo cơ sở dữ liệu và yêu cầu cơ sở dữ liệu đã được khởi tạo.

## Phát hiện database initializer (Detect a Database Initializer)

Spring Boot sẽ tự động phát hiện các bean thuộc các kiểu sau có khởi tạo một cơ sở dữ liệu SQL:

- `DataSourceScriptDatabaseInitializer`
- `EntityManagerFactory`
- `Flyway`
- `FlywayMigrationInitializer`
- `R2dbcScriptDatabaseInitializer`
- `SpringLiquibase`

Nếu bạn đang dùng một starter bên thứ ba cho một thư viện khởi tạo cơ sở dữ liệu, nó có thể cung cấp một detector để các bean thuộc kiểu khác cũng được phát hiện tự động. Để các bean khác được phát hiện, hãy đăng ký một implementation của `DatabaseInitializerDetector` trong `META-INF/spring.factories`.

## Phát hiện bean phụ thuộc vào việc khởi tạo cơ sở dữ liệu (Detect a Bean That Depends On Database Initialization)

Spring Boot sẽ tự động phát hiện các bean thuộc các kiểu sau phụ thuộc vào việc khởi tạo cơ sở dữ liệu:

- `AbstractEntityManagerFactoryBean` (trừ khi `spring.jpa.defer-datasource-initialization` được đặt thành `true`)
- `DSLContext` (jOOQ)
- `EntityManagerFactory` (trừ khi `spring.jpa.defer-datasource-initialization` được đặt thành `true`)
- `JdbcClient`
- `JdbcOperations`
- `NamedParameterJdbcOperations`

Nếu bạn đang dùng một thư viện truy cập dữ liệu starter bên thứ ba, nó có thể cung cấp một detector để các bean thuộc kiểu khác cũng được phát hiện tự động. Để các bean khác được phát hiện, hãy đăng ký một implementation của `DependsOnDatabaseInitializationDetector` trong `META-INF/spring.factories`. Ngoài ra, hãy annotate class của bean hoặc phương thức `@Bean` của nó với `@DependsOnDatabaseInitialization`.

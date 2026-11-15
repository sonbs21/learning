# 9.1.7. Sử dụng jOOQ (Using jOOQ)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#data.sql.jooq>

jOOQ Object Oriented Querying ([jOOQ](https://www.jooq.org/)) là một sản phẩm phổ biến của [Data Geekery](https://www.datageekery.com/), sinh code Java từ database của bạn và cho phép bạn xây dựng các câu truy vấn SQL an toàn kiểu (type-safe) thông qua API fluent của nó. Cả phiên bản thương mại và mã nguồn mở đều có thể dùng với Spring Boot.

## Sinh code (Code Generation)

Để dùng các query an toàn kiểu của jOOQ, bạn cần sinh các class Java từ schema database. Bạn có thể làm theo hướng dẫn trong [tài liệu hướng dẫn sử dụng jOOQ](https://www.jooq.org/doc/3.18.14/manual-single-page/#jooq-in-7-steps-step3). Nếu bạn dùng plugin `jooq-codegen-maven` và cũng dùng "parent POM" `spring-boot-starter-parent`, bạn có thể yên tâm bỏ qua thẻ `<version>` của plugin. Bạn cũng có thể dùng các biến phiên bản do Spring Boot định nghĩa (chẳng hạn như `h2.version`) để khai báo dependency database của plugin. Đoạn sau cho thấy một ví dụ:

```xml
<plugin>
    <groupId>org.jooq</groupId>
    <artifactId>jooq-codegen-maven</artifactId>
    <executions>
        ...
    </executions>
    <dependencies>
        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <version>${h2.version}</version>
        </dependency>
    </dependencies>
    <configuration>
        <jdbc>
            <driver>org.h2.Driver</driver>
            <url>jdbc:h2:~/yourdatabase</url>
        </jdbc>
        <generator>
            ...
        </generator>
    </configuration>
</plugin>
```

## Sử dụng DSLContext (Using DSLContext)

API fluent do jOOQ cung cấp được khởi tạo thông qua interface `org.jooq.DSLContext`. Spring Boot tự động cấu hình một `DSLContext` dưới dạng Spring Bean và kết nối nó với `DataSource` của ứng dụng. Để dùng `DSLContext`, bạn có thể inject nó, như trong ví dụ sau:

**Java**

```java
import java.util.GregorianCalendar;
import java.util.List;

import org.jooq.DSLContext;

import org.springframework.stereotype.Component;

import static org.springframework.boot.docs.data.sql.jooq.dslcontext.Tables.AUTHOR;

@Component
public class MyBean {

    private final DSLContext create;

    public MyBean(DSLContext dslContext) {
        this.create = dslContext;
    }


}
```

**Kotlin**

```kotlin
import org.jooq.DSLContext
import org.springframework.stereotype.Component
import java.util.GregorianCalendar

@Component
class MyBean(private val create: DSLContext) {


}
```

> **MẸO (TIP):** Tài liệu hướng dẫn jOOQ thường dùng một biến tên là `create` để giữ `DSLContext`.

Sau đó, bạn có thể dùng `DSLContext` để xây dựng các query, như trong ví dụ sau:

**Java**

```java
public List<GregorianCalendar> authorsBornAfter1980() {
    return this.create.selectFrom(AUTHOR)
        .where(AUTHOR.DATE_OF_BIRTH.greaterThan(new GregorianCalendar(1980, 0, 1)))
        .fetch(AUTHOR.DATE_OF_BIRTH);
```

**Kotlin**

```kotlin
fun authorsBornAfter1980(): List<GregorianCalendar> {
    return create.selectFrom<Tables.TAuthorRecord>(Tables.AUTHOR)
        .where(Tables.AUTHOR?.DATE_OF_BIRTH?.greaterThan(GregorianCalendar(1980, 0, 1)))
        .fetch(Tables.AUTHOR?.DATE_OF_BIRTH)
}
```

## SQL Dialect của jOOQ (jOOQ SQL Dialect)

Trừ khi property `spring.jooq.sql-dialect` đã được cấu hình, Spring Boot sẽ xác định SQL dialect cần dùng cho datasource của bạn. Nếu Spring Boot không thể phát hiện dialect, nó dùng `DEFAULT`.

> **LƯU Ý (NOTE):** Spring Boot chỉ có thể tự động cấu hình các dialect được phiên bản mã nguồn mở của jOOQ hỗ trợ.

## Tùy chỉnh jOOQ (Customizing jOOQ)

Các tùy chỉnh nâng cao hơn có thể đạt được bằng cách định nghĩa bean `DefaultConfigurationCustomizer` của riêng bạn, bean này sẽ được gọi trước khi tạo `@Bean` `org.jooq.Configuration`. Điều này được ưu tiên hơn mọi thứ được áp dụng bởi auto-configuration.

Bạn cũng có thể tạo `@Bean` `org.jooq.Configuration` của riêng mình nếu muốn kiểm soát hoàn toàn cấu hình jOOQ.

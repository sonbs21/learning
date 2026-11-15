# 6.1. Hệ thống build (Build Systems)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#using.build-systems>

Chúng tôi đặc biệt khuyên bạn nên chọn một hệ thống build có hỗ trợ [*quản lý dependency (dependency management)*](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#using.build-systems.dependency-management) và có thể sử dụng các artifact được publish lên repository "Maven Central". Chúng tôi khuyên bạn nên chọn Maven hoặc Gradle. Có thể làm cho Spring Boot hoạt động với các hệ thống build khác (ví dụ Ant), nhưng chúng không được hỗ trợ tốt lắm.

## 6.1.1. Quản lý dependency (Dependency Management)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#using.build-systems.dependency-management>

Mỗi bản phát hành của Spring Boot cung cấp một danh sách dependency được tuyển chọn (curated) mà nó hỗ trợ. Trên thực tế, bạn không cần cung cấp phiên bản cho bất kỳ dependency nào trong số này trong cấu hình build, vì Spring Boot quản lý việc đó cho bạn. Khi bạn nâng cấp chính Spring Boot, các dependency này cũng được nâng cấp theo một cách nhất quán.

> **LƯU Ý (NOTE):** Bạn vẫn có thể chỉ định phiên bản và ghi đè khuyến nghị của Spring Boot nếu cần.

Danh sách được tuyển chọn này chứa tất cả các module Spring mà bạn có thể dùng với Spring Boot, cũng như một danh sách chọn lọc các thư viện bên thứ ba. Danh sách có sẵn dưới dạng một Bills of Materials (BOM) tiêu chuẩn (`spring-boot-dependencies`) có thể dùng với cả [Maven](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#using.build-systems.maven) và [Gradle](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#using.build-systems.gradle).

> **CẢNH BÁO (WARNING):** Mỗi bản phát hành của Spring Boot gắn với một phiên bản cơ sở của Spring Framework. Chúng tôi **đặc biệt** khuyên bạn không nên chỉ định phiên bản của nó.

## 6.1.2. Maven

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#using.build-systems.maven>

Để tìm hiểu về việc sử dụng Spring Boot với Maven, hãy xem tài liệu của Maven plugin của Spring Boot:

- Tài liệu tham khảo ([HTML](https://docs.spring.io/spring-boot/docs/3.2.5/maven-plugin/reference/htmlsingle/) và [PDF](https://docs.spring.io/spring-boot/docs/3.2.5/maven-plugin/reference/pdf/spring-boot-maven-plugin-reference.pdf))
- [API](https://docs.spring.io/spring-boot/docs/3.2.5/maven-plugin/api/)

## 6.1.3. Gradle

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#using.build-systems.gradle>

Để tìm hiểu về việc sử dụng Spring Boot với Gradle, hãy xem tài liệu của Gradle plugin của Spring Boot:

- Tài liệu tham khảo ([HTML](https://docs.spring.io/spring-boot/docs/3.2.5/gradle-plugin/reference/htmlsingle/) và [PDF](https://docs.spring.io/spring-boot/docs/3.2.5/gradle-plugin/reference/pdf/spring-boot-gradle-plugin-reference.pdf))
- [API](https://docs.spring.io/spring-boot/docs/3.2.5/gradle-plugin/api/)

## 6.1.4. Ant

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#using.build-systems.ant>

Có thể build một project Spring Boot bằng Apache Ant+Ivy. Module "AntLib" `spring-boot-antlib` cũng có sẵn để giúp Ant tạo các jar thực thi.

Để khai báo dependency, một file `ivy.xml` điển hình trông giống như ví dụ sau:

```xml
<ivy-module version="2.0">
    <info organisation="org.springframework.boot" module="spring-boot-sample-ant" />
    <configurations>
        <conf name="compile" description="everything needed to compile this module" />
        <conf name="runtime" extends="compile" description="everything needed to run this module" />
    </configurations>
    <dependencies>
        <dependency org="org.springframework.boot" name="spring-boot-starter"
            rev="${spring-boot.version}" conf="compile" />
    </dependencies>
</ivy-module>
```

Một file `build.xml` điển hình trông giống như ví dụ sau:

```xml
<project
    xmlns:ivy="antlib:org.apache.ivy.ant"
    xmlns:spring-boot="antlib:org.springframework.boot.ant"
    name="myapp" default="build">

    <property name="spring-boot.version" value="3.2.5" />

    <target name="resolve" description="--> retrieve dependencies with ivy">
        <ivy:retrieve pattern="lib/[conf]/[artifact]-[type]-[revision].[ext]" />
    </target>

    <target name="classpaths" depends="resolve">
        <path id="compile.classpath">
            <fileset dir="lib/compile" includes="*.jar" />
        </path>
    </target>

    <target name="init" depends="classpaths">
        <mkdir dir="build/classes" />
    </target>

    <target name="compile" depends="init" description="compile">
        <javac srcdir="src/main/java" destdir="build/classes" classpathref="compile.classpath" />
    </target>

    <target name="build" depends="compile">
        <spring-boot:exejar destfile="build/myapp.jar" classes="build/classes">
            <spring-boot:lib>
                <fileset dir="lib/runtime" />
            </spring-boot:lib>
        </spring-boot:exejar>
    </target>
</project>
```

> **MẸO (TIP):** Nếu bạn không muốn dùng module `spring-boot-antlib`, hãy xem hướng dẫn "How-to" *[Build an Executable Archive From Ant without Using spring-boot-antlib](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.build.build-an-executable-archive-with-ant-without-using-spring-boot-antlib)*.

## 6.1.5. Starter

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#using.build-systems.starters>

Starter là một tập hợp các mô tả dependency (dependency descriptor) tiện lợi mà bạn có thể đưa vào ứng dụng. Bạn có được "cửa hàng một điểm dừng" (one-stop shop) cho tất cả các công nghệ Spring và công nghệ liên quan mà bạn cần, mà không phải lục lọi code mẫu và copy-paste hàng loạt mô tả dependency. Ví dụ, nếu bạn muốn bắt đầu dùng Spring và JPA để truy cập database, hãy thêm dependency `spring-boot-starter-data-jpa` vào project của bạn.

Các starter chứa rất nhiều dependency mà bạn cần để có một project chạy được nhanh chóng, với một tập hợp các dependency bắc cầu (transitive dependency) được quản lý, nhất quán và được hỗ trợ.

> **Cái tên nói lên điều gì (What is in a name)**
>
> Tất cả các starter **chính thức** đều tuân theo một mẫu đặt tên tương tự: `spring-boot-starter-*`, trong đó `*` là một loại ứng dụng cụ thể. Cấu trúc đặt tên này nhằm giúp bạn khi cần tìm một starter. Tích hợp Maven trong nhiều IDE cho phép bạn tìm dependency theo tên. Ví dụ, khi đã cài plugin Eclipse hoặc Spring Tools phù hợp, bạn có thể nhấn `ctrl-space` trong trình soạn thảo POM và gõ "spring-boot-starter" để có danh sách đầy đủ.
>
> Như đã giải thích trong phần "[Tạo Starter của riêng bạn (Creating Your Own Starter)](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.developing-auto-configuration.custom-starter)", các starter của bên thứ ba không nên bắt đầu bằng `spring-boot`, vì tiền tố này được dành riêng cho các artifact chính thức của Spring Boot. Thay vào đó, một starter của bên thứ ba thường bắt đầu bằng tên của project. Ví dụ, một project starter bên thứ ba tên là `thirdpartyproject` thường sẽ được đặt tên là `thirdpartyproject-spring-boot-starter`.

Các starter ứng dụng sau được Spring Boot cung cấp dưới group `org.springframework.boot`:

| Tên | Mô tả |
|---|---|
| `spring-boot-starter` | Starter cốt lõi, bao gồm hỗ trợ auto-configuration, logging và YAML |
| `spring-boot-starter-activemq` | Starter cho messaging JMS dùng Apache ActiveMQ |
| `spring-boot-starter-amqp` | Starter để dùng Spring AMQP và Rabbit MQ |
| `spring-boot-starter-aop` | Starter cho lập trình hướng khía cạnh (aspect-oriented programming) với Spring AOP và AspectJ |
| `spring-boot-starter-artemis` | Starter cho messaging JMS dùng Apache Artemis |
| `spring-boot-starter-batch` | Starter để dùng Spring Batch |
| `spring-boot-starter-cache` | Starter để dùng hỗ trợ caching của Spring Framework |
| `spring-boot-starter-data-cassandra` | Starter để dùng database phân tán Cassandra và Spring Data Cassandra |
| `spring-boot-starter-data-cassandra-reactive` | Starter để dùng database phân tán Cassandra và Spring Data Cassandra Reactive |
| `spring-boot-starter-data-couchbase` | Starter để dùng database hướng tài liệu (document-oriented) Couchbase và Spring Data Couchbase |
| `spring-boot-starter-data-couchbase-reactive` | Starter để dùng database hướng tài liệu Couchbase và Spring Data Couchbase Reactive |
| `spring-boot-starter-data-elasticsearch` | Starter để dùng công cụ tìm kiếm và phân tích Elasticsearch và Spring Data Elasticsearch |
| `spring-boot-starter-data-jdbc` | Starter để dùng Spring Data JDBC |
| `spring-boot-starter-data-jpa` | Starter để dùng Spring Data JPA với Hibernate |
| `spring-boot-starter-data-ldap` | Starter để dùng Spring Data LDAP |
| `spring-boot-starter-data-mongodb` | Starter để dùng database hướng tài liệu MongoDB và Spring Data MongoDB |
| `spring-boot-starter-data-mongodb-reactive` | Starter để dùng database hướng tài liệu MongoDB và Spring Data MongoDB Reactive |
| `spring-boot-starter-data-neo4j` | Starter để dùng database đồ thị (graph database) Neo4j và Spring Data Neo4j |
| `spring-boot-starter-data-r2dbc` | Starter để dùng Spring Data R2DBC |
| `spring-boot-starter-data-redis` | Starter để dùng kho dữ liệu key-value Redis với Spring Data Redis và client Lettuce |
| `spring-boot-starter-data-redis-reactive` | Starter để dùng kho dữ liệu key-value Redis với Spring Data Redis reactive và client Lettuce |
| `spring-boot-starter-data-rest` | Starter để expose các repository Spring Data qua REST bằng Spring Data REST và Spring MVC |
| `spring-boot-starter-freemarker` | Starter để xây dựng ứng dụng web MVC dùng view FreeMarker |
| `spring-boot-starter-graphql` | Starter để xây dựng ứng dụng GraphQL với Spring GraphQL |
| `spring-boot-starter-groovy-templates` | Starter để xây dựng ứng dụng web MVC dùng view Groovy Templates |
| `spring-boot-starter-hateoas` | Starter để xây dựng ứng dụng web RESTful dựa trên hypermedia với Spring MVC và Spring HATEOAS |
| `spring-boot-starter-integration` | Starter để dùng Spring Integration |
| `spring-boot-starter-jdbc` | Starter để dùng JDBC với connection pool HikariCP |
| `spring-boot-starter-jersey` | Starter để xây dựng ứng dụng web RESTful dùng JAX-RS và Jersey. Một lựa chọn thay thế cho [`spring-boot-starter-web`](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#spring-boot-starter-web) |
| `spring-boot-starter-jooq` | Starter để dùng jOOQ truy cập database SQL bằng JDBC. Một lựa chọn thay thế cho [`spring-boot-starter-data-jpa`](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#spring-boot-starter-data-jpa) hoặc [`spring-boot-starter-jdbc`](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#spring-boot-starter-jdbc) |
| `spring-boot-starter-json` | Starter để đọc và ghi json |
| `spring-boot-starter-mail` | Starter để dùng Java Mail và hỗ trợ gửi email của Spring Framework |
| `spring-boot-starter-mustache` | Starter để xây dựng ứng dụng web dùng view Mustache |
| `spring-boot-starter-oauth2-authorization-server` | Starter để dùng các tính năng của Spring Authorization Server |
| `spring-boot-starter-oauth2-client` | Starter để dùng các tính năng client OAuth2/OpenID Connect của Spring Security |
| `spring-boot-starter-oauth2-resource-server` | Starter để dùng các tính năng resource server OAuth2 của Spring Security |
| `spring-boot-starter-pulsar` | Starter để dùng Spring for Apache Pulsar |
| `spring-boot-starter-pulsar-reactive` | Starter để dùng Spring for Apache Pulsar Reactive |
| `spring-boot-starter-quartz` | Starter để dùng bộ lập lịch (scheduler) Quartz |
| `spring-boot-starter-rsocket` | Starter để xây dựng client và server RSocket |
| `spring-boot-starter-security` | Starter để dùng Spring Security |
| `spring-boot-starter-test` | Starter để kiểm thử ứng dụng Spring Boot với các thư viện bao gồm JUnit Jupiter, Hamcrest và Mockito |
| `spring-boot-starter-thymeleaf` | Starter để xây dựng ứng dụng web MVC dùng view Thymeleaf |
| `spring-boot-starter-validation` | Starter để dùng Java Bean Validation với Hibernate Validator |
| `spring-boot-starter-web` | Starter để xây dựng ứng dụng web, bao gồm cả RESTful, dùng Spring MVC. Dùng Tomcat làm container nhúng mặc định |
| `spring-boot-starter-web-services` | Starter để dùng Spring Web Services |
| `spring-boot-starter-webflux` | Starter để xây dựng ứng dụng WebFlux dùng hỗ trợ Reactive Web của Spring Framework |
| `spring-boot-starter-websocket` | Starter để xây dựng ứng dụng WebSocket dùng hỗ trợ MVC WebSocket của Spring Framework |

Ngoài các starter ứng dụng, các starter sau có thể được dùng để thêm các tính năng *[sẵn sàng cho production (production ready)](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#actuator)*:

| Tên | Mô tả |
|---|---|
| `spring-boot-starter-actuator` | Starter để dùng Actuator của Spring Boot, cung cấp các tính năng sẵn sàng cho production giúp bạn giám sát và quản lý ứng dụng |

Cuối cùng, Spring Boot cũng bao gồm các starter sau, có thể dùng nếu bạn muốn loại trừ hoặc thay thế các khía cạnh kỹ thuật (technical facet) cụ thể:

| Tên | Mô tả |
|---|---|
| `spring-boot-starter-jetty` | Starter để dùng Jetty làm servlet container nhúng. Một lựa chọn thay thế cho [`spring-boot-starter-tomcat`](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#spring-boot-starter-tomcat) |
| `spring-boot-starter-log4j2` | Starter để dùng Log4j2 cho logging. Một lựa chọn thay thế cho [`spring-boot-starter-logging`](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#spring-boot-starter-logging) |
| `spring-boot-starter-logging` | Starter cho logging dùng Logback. Starter logging mặc định |
| `spring-boot-starter-reactor-netty` | Starter để dùng Reactor Netty làm HTTP server reactive nhúng. |
| `spring-boot-starter-tomcat` | Starter để dùng Tomcat làm servlet container nhúng. Starter servlet container mặc định được dùng bởi [`spring-boot-starter-web`](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#spring-boot-starter-web) |
| `spring-boot-starter-undertow` | Starter để dùng Undertow làm servlet container nhúng. Một lựa chọn thay thế cho [`spring-boot-starter-tomcat`](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#spring-boot-starter-tomcat) |

Để tìm hiểu cách thay thế các khía cạnh kỹ thuật, hãy xem tài liệu how-to về [thay thế web server](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.webserver.use-another) và [hệ thống logging](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.logging.log4j).

> **MẸO (TIP):** Để có danh sách các starter bổ sung do cộng đồng đóng góp, hãy xem [file README](https://github.com/spring-projects/spring-boot/tree/main/spring-boot-project/spring-boot-starters/README.adoc) trong module `spring-boot-starters` trên GitHub.

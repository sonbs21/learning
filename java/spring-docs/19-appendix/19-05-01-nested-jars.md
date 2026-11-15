# E.1. JAR lồng nhau (Nested JARs)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#appendix.executable-jar.nested-jars>

Java không cung cấp bất kỳ cách chuẩn nào để nạp các file jar lồng nhau (tức là các file jar mà bản thân chúng nằm bên trong một jar). Điều này có thể gây khó khăn nếu bạn cần phân phối một ứng dụng khép kín có thể chạy từ dòng lệnh mà không cần giải nén.

Để giải quyết vấn đề này, nhiều developer dùng các jar "shaded". Một jar shaded đóng gói tất cả các class, từ tất cả các jar, vào một "uber jar" duy nhất. Vấn đề với các jar shaded là khó thấy được những thư viện nào thực sự có trong ứng dụng của bạn. Nó cũng có thể gây ra vấn đề nếu cùng một tên file được dùng (nhưng với nội dung khác nhau) trong nhiều jar. Spring Boot có một cách tiếp cận khác và cho phép bạn thực sự lồng các jar trực tiếp.

## Cấu trúc file jar thực thi (The Executable Jar File Structure)

Các file jar tương thích với Spring Boot Loader nên được cấu trúc theo cách sau:

```
example.jar
 |
 +-META-INF
 |  +-MANIFEST.MF
 +-org
 |  +-springframework
 |     +-boot
 |        +-loader
 |           +-<spring boot loader classes>
 +-BOOT-INF
    +-classes
    |  +-mycompany
    |     +-project
    |        +-YourClasses.class
    +-lib
       +-dependency1.jar
       +-dependency2.jar
```

Các class của ứng dụng nên được đặt trong một thư mục lồng nhau `BOOT-INF/classes`. Các dependency nên được đặt trong một thư mục lồng nhau `BOOT-INF/lib`.

## Cấu trúc file war thực thi (The Executable War File Structure)

Các file war tương thích với Spring Boot Loader nên được cấu trúc theo cách sau:

```
example.war
 |
 +-META-INF
 |  +-MANIFEST.MF
 +-org
 |  +-springframework
 |     +-boot
 |        +-loader
 |           +-<spring boot loader classes>
 +-WEB-INF
    +-classes
    |  +-com
    |     +-mycompany
    |        +-project
    |           +-YourClasses.class
    +-lib
    |  +-dependency1.jar
    |  +-dependency2.jar
    +-lib-provided
       +-servlet-api.jar
       +-dependency3.jar
```

Các dependency nên được đặt trong một thư mục lồng nhau `WEB-INF/lib`. Mọi dependency cần thiết khi chạy ở chế độ embedded nhưng không cần thiết khi triển khai lên một web container truyền thống nên được đặt trong `WEB-INF/lib-provided`.

## File index (Index Files)

Các file nén jar và war tương thích với Spring Boot Loader có thể bao gồm các file index bổ sung trong thư mục `BOOT-INF/`. Một file `classpath.idx` có thể được cung cấp cho cả jar và war, và nó cung cấp thứ tự mà các jar nên được thêm vào classpath. File `layers.idx` chỉ có thể được dùng cho jar, và nó cho phép một jar được chia thành các layer logic để tạo image Docker/OCI.

Các file index tuân theo cú pháp tương thích YAML để chúng có thể được các công cụ bên thứ ba dễ dàng parse. Tuy nhiên, các file này *không* được parse nội bộ dưới dạng YAML và chúng phải được viết chính xác theo các định dạng được mô tả dưới đây để có thể được dùng.

## Classpath index (Classpath Index)

File classpath index có thể được cung cấp tại `BOOT-INF/classpath.idx`. Thông thường, nó được sinh tự động bởi các build plugin Maven và Gradle của Spring Boot. Nó cung cấp một danh sách tên jar (bao gồm cả thư mục) theo thứ tự mà chúng nên được thêm vào classpath. Khi được sinh bởi các build plugin, thứ tự classpath này khớp với thứ tự được hệ thống build dùng để chạy và kiểm thử ứng dụng. Mỗi dòng phải bắt đầu bằng gạch ngang và dấu cách (`"-·"`) và tên phải nằm trong dấu ngoặc kép.

Ví dụ, với jar sau:

```
example.jar
 |
 +-META-INF
 |  +-...
 +-BOOT-INF
    +-classes
    |  +...
    +-lib
       +-dependency1.jar
       +-dependency2.jar
```

File index sẽ trông như thế này:

```
- "BOOT-INF/lib/dependency2.jar"
- "BOOT-INF/lib/dependency1.jar"
```

## Layer index (Layer Index)

File layers index có thể được cung cấp tại `BOOT-INF/layers.idx`. Nó cung cấp một danh sách các layer và các phần của jar nên được chứa trong chúng. Các layer được viết theo thứ tự mà chúng nên được thêm vào image Docker/OCI. Tên layer được viết dưới dạng chuỗi trong dấu ngoặc kép với tiền tố gạch ngang và dấu cách (`"-·"`) và hậu tố dấu hai chấm (`":"`). Nội dung layer là tên file hoặc tên thư mục được viết dưới dạng chuỗi trong dấu ngoặc kép với tiền tố dấu cách dấu cách gạch ngang dấu cách (`"··-·"`). Tên thư mục kết thúc bằng `/`, tên file thì không. Khi một tên thư mục được dùng, nó có nghĩa là tất cả các file bên trong thư mục đó nằm trong cùng một layer.

Một ví dụ điển hình về layers index sẽ là:

```
- "dependencies":
  - "BOOT-INF/lib/dependency1.jar"
  - "BOOT-INF/lib/dependency2.jar"
- "application":
  - "BOOT-INF/classes/"
  - "META-INF/"
```

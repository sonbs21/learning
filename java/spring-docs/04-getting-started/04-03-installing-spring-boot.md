# 4.3. Cài đặt Spring Boot (Installing Spring Boot)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#getting-started.installing>

Spring Boot có thể được sử dụng với các công cụ phát triển Java "cổ điển" hoặc được cài đặt như một công cụ dòng lệnh. Dù theo cách nào, bạn cũng cần [Java SDK v17](https://www.java.com) trở lên. Trước khi bắt đầu, bạn nên kiểm tra bản cài đặt Java hiện tại bằng lệnh sau:

```shell
$ java -version
```

Nếu bạn mới làm quen với phát triển Java hoặc muốn thử nghiệm Spring Boot, bạn có thể muốn thử [Spring Boot CLI](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#getting-started.installing.cli) (Command Line Interface - giao diện dòng lệnh) trước. Nếu không, hãy đọc tiếp phần hướng dẫn cài đặt "cổ điển".

## 4.3.1. Hướng dẫn cài đặt dành cho lập trình viên Java (Installation Instructions for the Java Developer)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#getting-started.installing.java>

Bạn có thể sử dụng Spring Boot giống như bất kỳ thư viện Java tiêu chuẩn nào. Để làm vậy, hãy đưa các file `spring-boot-*.jar` phù hợp vào classpath của bạn. Spring Boot không yêu cầu tích hợp công cụ đặc biệt nào, nên bạn có thể dùng bất kỳ IDE hay trình soạn thảo văn bản nào. Ngoài ra, một ứng dụng Spring Boot cũng chẳng có gì đặc biệt, nên bạn có thể chạy và debug ứng dụng Spring Boot như với bất kỳ chương trình Java nào khác.

Mặc dù bạn *có thể* sao chép các jar của Spring Boot, chúng tôi thường khuyên bạn nên dùng một công cụ build có hỗ trợ quản lý dependency (chẳng hạn như Maven hoặc Gradle).

### Cài đặt với Maven (Maven Installation)

Spring Boot tương thích với Apache Maven 3.6.3 trở lên. Nếu bạn chưa cài Maven, bạn có thể làm theo hướng dẫn tại [maven.apache.org](https://maven.apache.org).

> **MẸO (TIP):** Trên nhiều hệ điều hành, Maven có thể được cài đặt bằng trình quản lý gói (package manager). Nếu bạn dùng OSX Homebrew, hãy thử `brew install maven`. Người dùng Ubuntu có thể chạy `sudo apt-get install maven`. Người dùng Windows có [Chocolatey](https://chocolatey.org/) có thể chạy `choco install maven` từ một cửa sổ lệnh có quyền nâng cao (administrator).

Các dependency của Spring Boot sử dụng group id `org.springframework.boot`. Thông thường, file Maven POM của bạn kế thừa từ project `spring-boot-starter-parent` và khai báo dependency tới một hoặc nhiều ["Starter"](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#using.build-systems.starters). Spring Boot cũng cung cấp một [Maven plugin](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#build-tool-plugins.maven) tùy chọn để tạo các jar thực thi (executable jar).

Thông tin chi tiết hơn về việc bắt đầu với Spring Boot và Maven có thể được tìm thấy trong [phần Getting Started](https://docs.spring.io/spring-boot/docs/3.2.5/maven-plugin/reference/htmlsingle/#getting-started) của tài liệu tham khảo Maven plugin.

### Cài đặt với Gradle (Gradle Installation)

Spring Boot tương thích với Gradle 7.x (7.5 trở lên) và 8.x. Nếu bạn chưa cài Gradle, bạn có thể làm theo hướng dẫn tại [gradle.org](https://gradle.org).

Các dependency của Spring Boot có thể được khai báo bằng `group` `org.springframework.boot`. Thông thường, project của bạn khai báo dependency tới một hoặc nhiều ["Starter"](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#using.build-systems.starters). Spring Boot cung cấp một [Gradle plugin](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#build-tool-plugins.gradle) hữu ích, có thể dùng để đơn giản hóa việc khai báo dependency và để tạo các jar thực thi.

> **Gradle Wrapper**
>
> Gradle Wrapper cung cấp một cách tiện lợi để "lấy" Gradle khi bạn cần build một project. Nó là một script và thư viện nhỏ mà bạn commit cùng với code để khởi động (bootstrap) quá trình build. Xem [docs.gradle.org/current/userguide/gradle_wrapper.html](https://docs.gradle.org/current/userguide/gradle_wrapper.html) để biết chi tiết.

Thông tin chi tiết hơn về việc bắt đầu với Spring Boot và Gradle có thể được tìm thấy trong [phần Getting Started](https://docs.spring.io/spring-boot/docs/3.2.5/gradle-plugin/reference/htmlsingle/#getting-started) của tài liệu tham khảo Gradle plugin.

## 4.3.2. Cài đặt Spring Boot CLI (Installing the Spring Boot CLI)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#getting-started.installing.cli>

Spring Boot CLI (Command Line Interface) là một công cụ dòng lệnh mà bạn có thể dùng để nhanh chóng tạo prototype với Spring.

Bạn không bắt buộc phải dùng CLI để làm việc với Spring Boot, nhưng đó là một cách nhanh để khởi động một ứng dụng Spring mà không cần IDE.

### Cài đặt thủ công (Manual Installation)

Bạn có thể tải bản phân phối Spring CLI từ một trong các địa chỉ sau:

- [spring-boot-cli-3.2.5-bin.zip](https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-cli/3.2.5/spring-boot-cli-3.2.5-bin.zip)
- [spring-boot-cli-3.2.5-bin.tar.gz](https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-cli/3.2.5/spring-boot-cli-3.2.5-bin.tar.gz)

Sau khi tải về, hãy làm theo hướng dẫn trong [INSTALL.txt](https://raw.githubusercontent.com/spring-projects/spring-boot/v3.2.5/spring-boot-project/spring-boot-tools/spring-boot-cli/src/main/content/INSTALL.txt) từ file nén đã giải nén. Tóm lại, có một script `spring` (`spring.bat` cho Windows) trong thư mục `bin/` của file `.zip`. Ngoài ra, bạn có thể dùng `java -jar` với file `.jar` (script giúp bạn chắc chắn rằng classpath được thiết lập đúng).

### Cài đặt với SDKMAN! (Installation with SDKMAN!)

SDKMAN! (The Software Development Kit Manager) có thể được dùng để quản lý nhiều phiên bản của các SDK nhị phân khác nhau, bao gồm Groovy và Spring Boot CLI. Hãy lấy SDKMAN! từ [sdkman.io](https://sdkman.io) và cài đặt Spring Boot bằng các lệnh sau:

```shell
$ sdk install springboot
$ spring --version
Spring CLI v3.2.5
```

Nếu bạn phát triển tính năng cho CLI và muốn truy cập phiên bản mà bạn đã build, hãy dùng các lệnh sau:

```shell
$ sdk install springboot dev /path/to/spring-boot/spring-boot-cli/target/spring-boot-cli-3.2.5-bin/spring-3.2.5/
$ sdk default springboot dev
$ spring --version
Spring CLI v3.2.5
```

Các hướng dẫn trên cài đặt một instance cục bộ của `spring` gọi là instance `dev`. Nó trỏ tới vị trí build đích của bạn, nên mỗi khi bạn build lại Spring Boot, `spring` sẽ luôn được cập nhật.

Bạn có thể thấy nó bằng cách chạy lệnh sau:

```shell
$ sdk ls springboot

================================================================================
Available Springboot Versions
================================================================================
> + dev
* 3.2.5

================================================================================
+ - local version
* - installed
> - currently in use
================================================================================
```

### Cài đặt với OSX Homebrew (OSX Homebrew Installation)

Nếu bạn dùng Mac và sử dụng [Homebrew](https://brew.sh/), bạn có thể cài đặt Spring Boot CLI bằng các lệnh sau:

```shell
$ brew tap spring-io/tap
$ brew install spring-boot
```

Homebrew cài đặt `spring` vào `/usr/local/bin`.

> **LƯU Ý (NOTE):** Nếu bạn không thấy formula, có thể bản cài đặt brew của bạn đã cũ. Trong trường hợp đó, hãy chạy `brew update` rồi thử lại.

### Cài đặt với MacPorts (MacPorts Installation)

Nếu bạn dùng Mac và sử dụng [MacPorts](https://www.macports.org/), bạn có thể cài đặt Spring Boot CLI bằng lệnh sau:

```shell
$ sudo port install spring-boot-cli
```

### Tự động hoàn thành lệnh (Command-line Completion)

Spring Boot CLI bao gồm các script cung cấp tính năng tự động hoàn thành lệnh (command completion) cho shell [BASH](https://en.wikipedia.org/wiki/Bash_%28Unix_shell%29) và [zsh](https://en.wikipedia.org/wiki/Z_shell). Bạn có thể `source` script này (cũng có tên là `spring`) trong bất kỳ shell nào, hoặc đặt nó vào phần khởi tạo bash completion của cá nhân hoặc toàn hệ thống. Trên hệ thống Debian, các script toàn hệ thống nằm trong `<installation location>/shell-completion/bash` và tất cả script trong thư mục đó sẽ được thực thi khi một shell mới khởi động. Ví dụ, để chạy script thủ công nếu bạn đã cài đặt bằng SDKMAN!, hãy dùng các lệnh sau:

```shell
$ . ~/.sdkman/candidates/springboot/current/shell-completion/bash/spring
$ spring <HIT TAB HERE>
  grab  help  jar  run  test  version
```

> **LƯU Ý (NOTE):** Nếu bạn cài đặt Spring Boot CLI bằng Homebrew hoặc MacPorts, các script tự động hoàn thành lệnh sẽ được tự động đăng ký với shell của bạn.

### Cài đặt với Windows Scoop (Windows Scoop Installation)

Nếu bạn dùng Windows và sử dụng [Scoop](https://scoop.sh/), bạn có thể cài đặt Spring Boot CLI bằng các lệnh sau:

```
> scoop bucket add extras
> scoop install springboot
```

Scoop cài đặt `spring` vào `~/scoop/apps/springboot/current/bin`.

> **LƯU Ý (NOTE):** Nếu bạn không thấy app manifest, có thể bản cài đặt scoop của bạn đã cũ. Trong trường hợp đó, hãy chạy `scoop update` rồi thử lại.

# 13.2.11. Thông tin ứng dụng (Application Information)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#actuator.endpoints.info>

Thông tin ứng dụng expose nhiều thông tin khác nhau được thu thập từ tất cả các bean [`InfoContributor`](https://github.com/spring-projects/spring-boot/tree/v3.2.5/spring-boot-project/spring-boot-actuator/src/main/java/org/springframework/boot/actuate/info/InfoContributor.java) được định nghĩa trong `ApplicationContext`. Spring Boot bao gồm một số bean `InfoContributor` được tự động cấu hình, và bạn có thể viết của riêng mình.

## Các InfoContributor được tự động cấu hình (Auto-configured InfoContributors)

Khi phù hợp, Spring tự động cấu hình các bean `InfoContributor` sau:

| ID | Tên | Mô tả | Điều kiện tiên quyết |
|---|---|---|---|
| `build` | [`BuildInfoContributor`](https://github.com/spring-projects/spring-boot/tree/v3.2.5/spring-boot-project/spring-boot-actuator/src/main/java/org/springframework/boot/actuate/info/BuildInfoContributor.java) | Expose thông tin build. | Một resource `META-INF/build-info.properties`. |
| `env` | [`EnvironmentInfoContributor`](https://github.com/spring-projects/spring-boot/tree/v3.2.5/spring-boot-project/spring-boot-actuator/src/main/java/org/springframework/boot/actuate/info/EnvironmentInfoContributor.java) | Expose bất kỳ property nào từ `Environment` có tên bắt đầu bằng `info.`. | Không có. |
| `git` | [`GitInfoContributor`](https://github.com/spring-projects/spring-boot/tree/v3.2.5/spring-boot-project/spring-boot-actuator/src/main/java/org/springframework/boot/actuate/info/GitInfoContributor.java) | Expose thông tin git. | Một resource `git.properties`. |
| `java` | [`JavaInfoContributor`](https://github.com/spring-projects/spring-boot/tree/v3.2.5/spring-boot-project/spring-boot-actuator/src/main/java/org/springframework/boot/actuate/info/JavaInfoContributor.java) | Expose thông tin Java runtime. | Không có. |
| `os` | [`OsInfoContributor`](https://github.com/spring-projects/spring-boot/tree/v3.2.5/spring-boot-project/spring-boot-actuator/src/main/java/org/springframework/boot/actuate/info/OsInfoContributor.java) | Expose thông tin Hệ điều hành. | Không có. |

Việc một contributor riêng lẻ có được bật hay không được kiểm soát bởi property `management.info.<id>.enabled` của nó. Các contributor khác nhau có các giá trị mặc định khác nhau cho property này, tùy thuộc vào điều kiện tiên quyết của chúng và bản chất của thông tin mà chúng expose.

Không có điều kiện tiên quyết nào cho biết chúng nên được bật, các contributor `env`, `java`, và `os` bị tắt theo mặc định. Mỗi contributor có thể được bật bằng cách đặt property `management.info.<id>.enabled` của nó thành `true`.

Các info contributor `build` và `git` được bật theo mặc định. Mỗi contributor có thể bị tắt bằng cách đặt property `management.info.<id>.enabled` của nó thành `false`. Ngoài ra, để tắt mọi contributor thường được bật theo mặc định, hãy đặt property `management.info.defaults.enabled` thành `false`.

## Thông tin ứng dụng tùy chỉnh (Custom Application Information)

Khi contributor `env` được bật, bạn có thể tùy chỉnh dữ liệu được endpoint `info` expose bằng cách đặt các Spring property `info.*`. Tất cả các property `Environment` bên dưới key `info` đều được tự động expose. Ví dụ, bạn có thể thêm các thiết lập sau vào file `application.properties`:

**Properties**

```properties
info.app.encoding=UTF-8
info.app.java.source=17
info.app.java.target=17
```

**Yaml**

```yaml
info:
  app:
    encoding: "UTF-8"
    java:
      source: "17"
      target: "17"
```

> **MẸO (TIP):** Thay vì hard-code các giá trị đó, bạn cũng có thể [mở rộng các info property tại thời điểm build](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.properties-and-configuration.expand-properties).
>
> Giả sử bạn dùng Maven, bạn có thể viết lại ví dụ trên như sau:

**Properties**

```properties
[email protected]@
[email protected]@
[email protected]@
```

**Yaml**

```yaml
info:
  app:
    encoding: "@project.build.sourceEncoding@"
    java:
      source: "@java.version@"
      target: "@java.version@"
```

## Thông tin commit Git (Git Commit Information)

Một tính năng hữu ích khác của endpoint `info` là khả năng publish thông tin về trạng thái của repository mã nguồn `git` khi project được build. Nếu có bean `GitProperties`, bạn có thể dùng endpoint `info` để expose các property này.

> **MẸO (TIP):** Một bean `GitProperties` được tự động cấu hình nếu có file `git.properties` ở thư mục gốc của classpath. Xem "[cách sinh thông tin git](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.build.generate-git-info)" để biết thêm chi tiết.

Theo mặc định, endpoint expose các property `git.branch`, `git.commit.id`, và `git.commit.time`, nếu có. Nếu bạn không muốn bất kỳ property nào trong số này xuất hiện trong response của endpoint, chúng cần được loại bỏ khỏi file `git.properties`. Nếu bạn muốn hiển thị toàn bộ thông tin git (tức là toàn bộ nội dung của `git.properties`), hãy dùng property `management.info.git.mode`, như sau:

**Properties**

```properties
management.info.git.mode=full
```

**Yaml**

```yaml
management:
  info:
    git:
      mode: "full"
```

Để tắt hoàn toàn thông tin commit git khỏi endpoint `info`, hãy đặt property `management.info.git.enabled` thành `false`, như sau:

**Properties**

```properties
management.info.git.enabled=false
```

**Yaml**

```yaml
management:
  info:
    git:
      enabled: false
```

## Thông tin build (Build Information)

Nếu có bean `BuildProperties`, endpoint `info` cũng có thể publish thông tin về bản build của bạn. Điều này xảy ra nếu có file `META-INF/build-info.properties` trên classpath.

> **MẸO (TIP):** Cả plugin Maven và Gradle đều có thể sinh file đó. Xem "[cách sinh thông tin build](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.build.generate-info)" để biết thêm chi tiết.

## Thông tin Java (Java Information)

Endpoint `info` publish thông tin về môi trường Java runtime của bạn, xem [`JavaInfo`](https://docs.spring.io/spring-boot/docs/3.2.5/api/org/springframework/boot/info/JavaInfo.html) để biết thêm chi tiết.

## Thông tin hệ điều hành (OS Information)

Endpoint `info` publish thông tin về Hệ điều hành của bạn, xem [`OsInfo`](https://docs.spring.io/spring-boot/docs/3.2.5/api/org/springframework/boot/info/OsInfo.html) để biết thêm chi tiết.

## Viết InfoContributor tùy chỉnh (Writing Custom InfoContributors)

Để cung cấp thông tin ứng dụng tùy chỉnh, bạn có thể đăng ký các Spring bean hiện thực interface [`InfoContributor`](https://github.com/spring-projects/spring-boot/tree/v3.2.5/spring-boot-project/spring-boot-actuator/src/main/java/org/springframework/boot/actuate/info/InfoContributor.java).

Ví dụ sau đóng góp một entry `example` với một giá trị duy nhất:

**Java**

```java
import java.util.Collections;

import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.stereotype.Component;

@Component
public class MyInfoContributor implements InfoContributor {

    @Override
    public void contribute(Info.Builder builder) {
        builder.withDetail("example", Collections.singletonMap("key", "value"));
    }

}
```

**Kotlin**

```kotlin
import org.springframework.boot.actuate.info.Info
import org.springframework.boot.actuate.info.InfoContributor
import org.springframework.stereotype.Component
import java.util.Collections

@Component
class MyInfoContributor : InfoContributor {

    override fun contribute(builder: Info.Builder) {
        builder.withDetail("example", Collections.singletonMap("key", "value"))
    }

}
```

Nếu bạn truy cập endpoint `info`, bạn sẽ thấy một response chứa entry bổ sung sau:

```json
{
    "example": {
        "key" : "value"
    }
}
```

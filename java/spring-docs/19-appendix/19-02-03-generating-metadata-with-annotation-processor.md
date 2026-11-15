# B.3. Tự sinh metadata bằng annotation processor (Generating Your Own Metadata by Using the Annotation Processor)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#appendix.configuration-metadata.annotation-processor>

Bạn có thể dễ dàng sinh file configuration metadata của riêng mình từ các mục được annotate với `@ConfigurationProperties` bằng cách dùng jar `spring-boot-configuration-processor`. Jar này bao gồm một Java annotation processor được gọi khi dự án của bạn được biên dịch.

## Cấu hình annotation processor (Configuring the Annotation Processor)

Để dùng processor, hãy thêm một dependency vào `spring-boot-configuration-processor`.

Với Maven, dependency nên được khai báo là optional, như trong ví dụ sau:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-configuration-processor</artifactId>
    <optional>true</optional>
</dependency>
```

Với Gradle, dependency nên được khai báo trong cấu hình `annotationProcessor`, như trong ví dụ sau:

```gradle
dependencies {
    annotationProcessor "org.springframework.boot:spring-boot-configuration-processor"
}
```

Nếu bạn đang dùng file `additional-spring-configuration-metadata.json`, task `compileJava` nên được cấu hình để phụ thuộc vào task `processResources`, như trong ví dụ sau:

```gradle
tasks.named('compileJava') {
    inputs.files(tasks.named('processResources'))
}
```

Dependency này đảm bảo rằng metadata bổ sung có sẵn khi annotation processor chạy trong quá trình biên dịch.

> **LƯU Ý (NOTE):** Nếu bạn đang dùng AspectJ trong dự án, bạn cần đảm bảo rằng annotation processor chỉ chạy một lần. Có một vài cách để làm điều này. Với Maven, bạn có thể cấu hình tường minh `maven-apt-plugin` và chỉ thêm dependency vào annotation processor ở đó. Bạn cũng có thể để AspectJ plugin chạy toàn bộ quá trình xử lý và vô hiệu hóa việc xử lý annotation trong cấu hình `maven-compiler-plugin`, như sau:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <proc>none</proc>
    </configuration>
</plugin>
```

> **LƯU Ý (NOTE):** Nếu bạn đang dùng Lombok trong dự án, bạn cần đảm bảo rằng annotation processor của nó chạy trước `spring-boot-configuration-processor`. Để làm vậy với Maven, bạn có thể liệt kê các annotation processor theo đúng thứ tự bằng thuộc tính `annotationProcessors` của Maven compiler plugin. Nếu bạn không dùng thuộc tính này, và các annotation processor được nhận từ các dependency có sẵn trên classpath, hãy đảm bảo rằng dependency `lombok` được định nghĩa trước dependency `spring-boot-configuration-processor`.

## Tự động sinh metadata (Automatic Metadata Generation)

Processor nhận cả các class và phương thức được annotate với `@ConfigurationProperties`.

Nếu class có một constructor có tham số duy nhất, một property được tạo cho mỗi tham số của constructor, trừ khi constructor được annotate với `@Autowired`. Nếu class có một constructor được annotate tường minh với `@ConstructorBinding`, một property được tạo cho mỗi tham số của constructor đó. Ngược lại, các property được phát hiện thông qua sự có mặt của các getter và setter chuẩn với cách xử lý đặc biệt cho các kiểu collection và map (được phát hiện ngay cả khi chỉ có getter). Annotation processor cũng hỗ trợ việc dùng các annotation lombok `@Data`, `@Value`, `@Getter`, và `@Setter`.

Hãy xem ví dụ sau:

```java
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "my.server")
public class MyServerProperties {

    /**
     * Name of the server.
     */
    private String name;

    /**
     * IP address to listen to.
     */
    private String ip = "127.0.0.1";

    /**
     * Port to listener to.
     */
    private int port = 9797;

    // getters/setters ...

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getIp() {
        return this.ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public int getPort() {
        return this.port;
    }

    public void setPort(int port) {
        this.port = port;
    }
    // fold:off
```

Ví dụ này expose ba property, trong đó `my.server.name` không có giá trị mặc định còn `my.server.ip` và `my.server.port` mặc định lần lượt là `"127.0.0.1"` và `9797`. Javadoc trên các field được dùng để điền thuộc tính `description`. Chẳng hạn, mô tả của `my.server.ip` là "IP address to listen to.".

> **LƯU Ý (NOTE):** Bạn chỉ nên dùng văn bản thuần với Javadoc của field `@ConfigurationProperties`, vì chúng không được xử lý trước khi được thêm vào JSON.

Annotation processor áp dụng một số heuristic để trích xuất giá trị mặc định từ source model. Giá trị mặc định phải được cung cấp một cách tĩnh. Đặc biệt, đừng tham chiếu tới một hằng số được định nghĩa trong một class khác. Ngoài ra, annotation processor không thể tự động phát hiện giá trị mặc định cho các `Enum` và `Collections`.

Đối với các trường hợp không thể phát hiện giá trị mặc định, nên cung cấp [metadata thủ công](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#appendix.configuration-metadata.annotation-processor.adding-additional-metadata). Hãy xem ví dụ sau:

```java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "my.messaging")
public class MyMessagingProperties {

    private List<String> addresses = new ArrayList<>(Arrays.asList("a", "b"));

    private ContainerType containerType = ContainerType.SIMPLE;

    // getters/setters ...

    public List<String> getAddresses() {
        return this.addresses;
    }

    public void setAddresses(List<String> addresses) {
        this.addresses = addresses;
    }

    public ContainerType getContainerType() {
        return this.containerType;
    }

    public void setContainerType(ContainerType containerType) {
        this.containerType = containerType;
    }

    public enum ContainerType {

        SIMPLE, DIRECT

    }

}
```

Để tài liệu hóa giá trị mặc định cho các property trong class trên, bạn có thể thêm nội dung sau vào [metadata thủ công của module](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#appendix.configuration-metadata.annotation-processor.adding-additional-metadata):

```json
{"properties": [
    {
        "name": "my.messaging.addresses",
        "defaultValue": ["a", "b"]
    },
    {
        "name": "my.messaging.container-type",
        "defaultValue": "simple"
    }
]}
```

> **LƯU Ý (NOTE):** Chỉ cần `name` của property để tài liệu hóa metadata bổ sung cho các property hiện có.

### Property lồng nhau (Nested Properties)

Annotation processor tự động coi các inner class là property lồng nhau. Thay vì tài liệu hóa `ip` và `port` ở gốc của namespace, chúng ta có thể tạo một sub-namespace cho chúng. Hãy xem ví dụ đã cập nhật:

```java
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "my.server")
public class MyServerProperties {

    private String name;

    private Host host;

    // getters/setters ...

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Host getHost() {
        return this.host;
    }

    public void setHost(Host host) {
        this.host = host;
    }

    public static class Host {

        private String ip;

        private int port;

        // getters/setters ...

        public String getIp() {
            return this.ip;
        }

        public void setIp(String ip) {
            this.ip = ip;
        }

        public int getPort() {
            return this.port;
        }

        public void setPort(int port) {
            this.port = port;
        }

    }

}
```

Ví dụ trên tạo ra thông tin metadata cho các property `my.server.name`, `my.server.host.ip`, và `my.server.host.port`. Bạn có thể dùng annotation `@NestedConfigurationProperty` trên một field để chỉ ra rằng một class thông thường (không phải inner class) nên được xử lý như thể nó là lồng nhau.

> **MẸO (TIP):** Điều này không có tác dụng đối với collection và map, vì các kiểu đó được nhận diện tự động, và một metadata property duy nhất được sinh ra cho mỗi kiểu.

## Thêm metadata bổ sung (Adding Additional Metadata)

Việc xử lý file cấu hình của Spring Boot khá linh hoạt, và thường có trường hợp tồn tại các property không được bind vào một bean `@ConfigurationProperties`. Bạn cũng có thể cần tinh chỉnh một số thuộc tính của một key hiện có. Để hỗ trợ những trường hợp như vậy và cho phép bạn cung cấp các "hint" tùy chỉnh, annotation processor tự động hợp nhất các mục từ `META-INF/additional-spring-configuration-metadata.json` vào file metadata chính.

Nếu bạn tham chiếu tới một property đã được phát hiện tự động, mô tả, giá trị mặc định, và thông tin deprecation sẽ bị ghi đè, nếu được chỉ định. Nếu khai báo property thủ công không được xác định trong module hiện tại, nó được thêm vào như một property mới.

Định dạng của file `additional-spring-configuration-metadata.json` giống hệt với `spring-configuration-metadata.json` thông thường. File property bổ sung là tùy chọn. Nếu bạn không có property bổ sung nào, đừng thêm file này.

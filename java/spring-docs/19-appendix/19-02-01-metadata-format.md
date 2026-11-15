# B.1. Định dạng metadata (Metadata Format)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#appendix.configuration-metadata.format>

Các file configuration metadata nằm bên trong các jar tại `META-INF/spring-configuration-metadata.json`. Chúng dùng định dạng JSON với các mục được phân loại thành "groups" hoặc "properties" và các gợi ý giá trị bổ sung được phân loại trong "hints", như trong ví dụ sau:

```json
{"groups": [
    {
        "name": "server",
        "type": "org.springframework.boot.autoconfigure.web.ServerProperties",
        "sourceType": "org.springframework.boot.autoconfigure.web.ServerProperties"
    },
    {
        "name": "spring.jpa.hibernate",
        "type": "org.springframework.boot.autoconfigure.orm.jpa.JpaProperties$Hibernate",
        "sourceType": "org.springframework.boot.autoconfigure.orm.jpa.JpaProperties",
        "sourceMethod": "getHibernate()"
    }
    ...
],"properties": [
    {
        "name": "server.port",
        "type": "java.lang.Integer",
        "sourceType": "org.springframework.boot.autoconfigure.web.ServerProperties"
    },
    {
        "name": "server.address",
        "type": "java.net.InetAddress",
        "sourceType": "org.springframework.boot.autoconfigure.web.ServerProperties"
    },
    {
          "name": "spring.jpa.hibernate.ddl-auto",
          "type": "java.lang.String",
          "description": "DDL mode. This is actually a shortcut for the \"hibernate.hbm2ddl.auto\" property.",
          "sourceType": "org.springframework.boot.autoconfigure.orm.jpa.JpaProperties$Hibernate"
    }
    ...
],"hints": [
    {
        "name": "spring.jpa.hibernate.ddl-auto",
        "values": [
            {
                "value": "none",
                "description": "Disable DDL handling."
            },
            {
                "value": "validate",
                "description": "Validate the schema, make no changes to the database."
            },
            {
                "value": "update",
                "description": "Update the schema if necessary."
            },
            {
                "value": "create",
                "description": "Create the schema and destroy previous data."
            },
            {
                "value": "create-drop",
                "description": "Create and then destroy the schema at the end of the session."
            }
        ]
    }
]}
```

Mỗi "property" là một mục cấu hình mà người dùng chỉ định với một giá trị nhất định. Ví dụ, `server.port` và `server.address` có thể được chỉ định trong `application.properties`/`application.yaml` của bạn, như sau:

**Properties**

```properties
server.port=9090
server.address=127.0.0.1
```

**Yaml**

```yaml
server:
  port: 9090
  address: 127.0.0.1
```

Các "group" là các mục cấp cao hơn, bản thân chúng không chỉ định giá trị mà thay vào đó cung cấp một nhóm theo ngữ cảnh cho các property. Ví dụ, các property `server.port` và `server.address` là một phần của group `server`.

> **LƯU Ý (NOTE):** Không bắt buộc mọi "property" phải có một "group". Một số property có thể tồn tại độc lập.

Cuối cùng, "hint" là thông tin bổ sung được dùng để hỗ trợ người dùng cấu hình một property nhất định. Ví dụ, khi một developer đang cấu hình property `spring.jpa.hibernate.ddl-auto`, một công cụ có thể dùng các hint để đưa ra trợ giúp tự động hoàn thành cho các giá trị `none`, `validate`, `update`, `create`, và `create-drop`.

## Các thuộc tính của group (Group Attributes)

JSON object chứa trong mảng `groups` có thể chứa các thuộc tính được trình bày trong bảng sau:

| Tên | Kiểu | Mục đích |
|---|---|---|
| `name` | String | Tên đầy đủ của group. Thuộc tính này là bắt buộc. |
| `type` | String | Tên class của kiểu dữ liệu của group. Ví dụ, nếu group dựa trên một class được annotate với `@ConfigurationProperties`, thuộc tính sẽ chứa tên đầy đủ của class đó. Nếu nó dựa trên một phương thức `@Bean`, nó sẽ là kiểu trả về của phương thức đó. Nếu không biết kiểu, thuộc tính có thể được bỏ qua. |
| `description` | String | Một mô tả ngắn về group có thể được hiển thị cho người dùng. Nếu không có mô tả, nó có thể được bỏ qua. Khuyến nghị rằng các mô tả nên là những đoạn văn ngắn, với dòng đầu tiên cung cấp một bản tóm tắt súc tích. Dòng cuối cùng trong mô tả nên kết thúc bằng một dấu chấm (`.`). |
| `sourceType` | String | Tên class của nguồn đã đóng góp group này. Ví dụ, nếu group dựa trên một phương thức `@Bean` được annotate với `@ConfigurationProperties`, thuộc tính này sẽ chứa tên đầy đủ của class `@Configuration` chứa phương thức đó. Nếu không biết kiểu nguồn, thuộc tính có thể được bỏ qua. |
| `sourceMethod` | String | Tên đầy đủ của phương thức (bao gồm dấu ngoặc và kiểu tham số) đã đóng góp group này (ví dụ, tên của một phương thức `@Bean` được annotate `@ConfigurationProperties`). Nếu không biết phương thức nguồn, nó có thể được bỏ qua. |

## Các thuộc tính của property (Property Attributes)

JSON object chứa trong mảng `properties` có thể chứa các thuộc tính được mô tả trong bảng sau:

| Tên | Kiểu | Mục đích |
|---|---|---|
| `name` | String | Tên đầy đủ của property. Tên ở dạng chữ thường phân tách bằng dấu chấm (ví dụ, `server.address`). Thuộc tính này là bắt buộc. |
| `type` | String | Signature đầy đủ của kiểu dữ liệu của property (ví dụ, `java.lang.String`) nhưng cũng có thể là một kiểu generic đầy đủ (chẳng hạn như `java.util.Map<java.lang.String,com.example.MyEnum>`). Bạn có thể dùng thuộc tính này để hướng dẫn người dùng về kiểu giá trị mà họ có thể nhập. Để nhất quán, kiểu của một kiểu nguyên thủy được chỉ định bằng cách dùng kiểu wrapper tương ứng của nó (ví dụ, `boolean` trở thành `java.lang.Boolean`). Lưu ý rằng class này có thể là một kiểu phức tạp được chuyển đổi từ một `String` khi các giá trị được bind. Nếu không biết kiểu, nó có thể được bỏ qua. |
| `description` | String | Một mô tả ngắn về property có thể được hiển thị cho người dùng. Nếu không có mô tả, nó có thể được bỏ qua. Khuyến nghị rằng các mô tả nên là những đoạn văn ngắn, với dòng đầu tiên cung cấp một bản tóm tắt súc tích. Dòng cuối cùng trong mô tả nên kết thúc bằng một dấu chấm (`.`). |
| `sourceType` | String | Tên class của nguồn đã đóng góp property này. Ví dụ, nếu property đến từ một class được annotate với `@ConfigurationProperties`, thuộc tính này sẽ chứa tên đầy đủ của class đó. Nếu không biết kiểu nguồn, nó có thể được bỏ qua. |
| `defaultValue` | Object | Giá trị mặc định, được dùng nếu property không được chỉ định. Nếu kiểu của property là một mảng, nó có thể là một mảng (các) giá trị. Nếu không biết giá trị mặc định, nó có thể được bỏ qua. |
| `deprecation` | Deprecation | Chỉ định property có bị deprecated hay không. Nếu field không bị deprecated hoặc nếu thông tin đó không được biết, nó có thể được bỏ qua. Bảng tiếp theo cung cấp thêm chi tiết về thuộc tính `deprecation`. |

JSON object chứa trong thuộc tính `deprecation` của mỗi phần tử `properties` có thể chứa các thuộc tính sau:

| Tên | Kiểu | Mục đích |
|---|---|---|
| `level` | String | Mức độ deprecation, có thể là `warning` (mặc định) hoặc `error`. Khi một property có mức deprecation `warning`, nó vẫn nên được bind trong environment. Tuy nhiên, khi nó có mức deprecation `error`, property không còn được quản lý và không được bind. |
| `reason` | String | Một mô tả ngắn về lý do property bị deprecated. Nếu không có lý do, nó có thể được bỏ qua. Khuyến nghị rằng các mô tả nên là những đoạn văn ngắn, với dòng đầu tiên cung cấp một bản tóm tắt súc tích. Dòng cuối cùng trong mô tả nên kết thúc bằng một dấu chấm (`.`). |
| `replacement` | String | Tên đầy đủ của property *thay thế* property đã bị deprecated này. Nếu không có property thay thế cho property này, nó có thể được bỏ qua. |
| `since` | String | Phiên bản mà property bắt đầu bị deprecated. Có thể được bỏ qua. |

> **LƯU Ý (NOTE):** Trước Spring Boot 1.3, một thuộc tính boolean `deprecated` duy nhất có thể được dùng thay cho phần tử `deprecation`. Điều này vẫn được hỗ trợ theo cách deprecated và không nên được dùng nữa. Nếu không có lý do và property thay thế, nên đặt một object `deprecation` rỗng.

Deprecation cũng có thể được chỉ định theo cách khai báo trong code bằng cách thêm annotation `@DeprecatedConfigurationProperty` vào getter expose property đã bị deprecated. Chẳng hạn, giả sử property `my.app.target` gây nhầm lẫn và đã được đổi tên thành `my.app.name`. Ví dụ sau cho thấy cách xử lý tình huống đó:

```java
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.DeprecatedConfigurationProperty;

@ConfigurationProperties("my.app")
public class MyProperties {

    private String name;

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Deprecated
    @DeprecatedConfigurationProperty(replacement = "my.app.name")
    public String getTarget() {
        return this.name;
    }

    @Deprecated
    public void setTarget(String target) {
        this.name = target;
    }

}
```

> **LƯU Ý (NOTE):** Không có cách nào để đặt `level`. `warning` luôn được giả định, vì code vẫn đang xử lý property đó.

Code trên đảm bảo rằng property đã bị deprecated vẫn hoạt động (ủy quyền cho property `name` ở phía sau). Một khi các phương thức `getTarget` và `setTarget` có thể được loại bỏ khỏi public API của bạn, hint deprecation tự động trong metadata cũng biến mất. Nếu bạn muốn giữ lại một hint, việc thêm metadata thủ công với mức deprecation `error` sẽ đảm bảo người dùng vẫn được thông báo về property đó. Làm vậy đặc biệt hữu ích khi có cung cấp một `replacement`.

## Các thuộc tính của hint (Hint Attributes)

JSON object chứa trong mảng `hints` có thể chứa các thuộc tính được trình bày trong bảng sau:

| Tên | Kiểu | Mục đích |
|---|---|---|
| `name` | String | Tên đầy đủ của property mà hint này tham chiếu tới. Tên ở dạng chữ thường phân tách bằng dấu chấm (chẳng hạn như `spring.mvc.servlet.path`). Nếu property tham chiếu tới một map (chẳng hạn như `system.contexts`), hint sẽ áp dụng cho các *key* của map (`system.contexts.keys`) hoặc các *value* (`system.contexts.values`) của map. Thuộc tính này là bắt buộc. |
| `values` | ValueHint[] | Một danh sách các giá trị hợp lệ như được định nghĩa bởi object `ValueHint` (được mô tả trong bảng tiếp theo). Mỗi mục định nghĩa giá trị và có thể có một mô tả. |
| `providers` | ValueProvider[] | Một danh sách các provider như được định nghĩa bởi object `ValueProvider` (được mô tả ở phần sau của tài liệu này). Mỗi mục định nghĩa tên của provider và các tham số của nó, nếu có. |

JSON object chứa trong thuộc tính `values` của mỗi phần tử `hint` có thể chứa các thuộc tính được mô tả trong bảng sau:

| Tên | Kiểu | Mục đích |
|---|---|---|
| `value` | Object | Một giá trị hợp lệ cho phần tử mà hint tham chiếu tới. Nếu kiểu của property là một mảng, nó cũng có thể là một mảng (các) giá trị. Thuộc tính này là bắt buộc. |
| `description` | String | Một mô tả ngắn về giá trị có thể được hiển thị cho người dùng. Nếu không có mô tả, nó có thể được bỏ qua. Khuyến nghị rằng các mô tả nên là những đoạn văn ngắn, với dòng đầu tiên cung cấp một bản tóm tắt súc tích. Dòng cuối cùng trong mô tả nên kết thúc bằng một dấu chấm (`.`). |

JSON object chứa trong thuộc tính `providers` của mỗi phần tử `hint` có thể chứa các thuộc tính được mô tả trong bảng sau:

| Tên | Kiểu | Mục đích |
|---|---|---|
| `name` | String | Tên của provider dùng để cung cấp thêm trợ giúp nội dung cho phần tử mà hint tham chiếu tới. |
| `parameters` | JSON object | Bất kỳ tham số bổ sung nào mà provider hỗ trợ (xem tài liệu của provider để biết thêm chi tiết). |

## Các mục metadata lặp lại (Repeated Metadata Items)

Các object có cùng tên "property" và "group" có thể xuất hiện nhiều lần trong một file metadata. Ví dụ, bạn có thể bind hai class riêng biệt vào cùng một tiền tố, mỗi class có thể có tên property trùng lặp. Mặc dù việc cùng một tên xuất hiện nhiều lần trong metadata không nên phổ biến, những bên tiêu thụ metadata nên cẩn thận để đảm bảo rằng họ hỗ trợ điều đó.

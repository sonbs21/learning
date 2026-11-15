# B.2. Cung cấp hint thủ công (Providing Manual Hints)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#appendix.configuration-metadata.manual-hints>

Để cải thiện trải nghiệm người dùng và hỗ trợ thêm cho người dùng trong việc cấu hình một property nhất định, bạn có thể cung cấp metadata bổ sung để:

- Mô tả danh sách các giá trị tiềm năng cho một property.
- Liên kết một provider, để gắn một ngữ nghĩa được định nghĩa rõ ràng cho một property, sao cho một công cụ có thể phát hiện danh sách các giá trị tiềm năng dựa trên ngữ cảnh của dự án.

## Value hint (Value Hint)

Thuộc tính `name` của mỗi hint tham chiếu tới `name` của một property. Trong [ví dụ ban đầu được trình bày ở trên](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#appendix.configuration-metadata.format), chúng ta cung cấp năm giá trị cho property `spring.jpa.hibernate.ddl-auto`: `none`, `validate`, `update`, `create`, và `create-drop`. Mỗi giá trị cũng có thể có một mô tả.

Nếu property của bạn có kiểu `Map`, bạn có thể cung cấp hint cho cả key lẫn value (nhưng không cho chính map đó). Các hậu tố đặc biệt `.keys` và `.values` phải tham chiếu lần lượt tới các key và các value.

Giả sử một `my.contexts` ánh xạ các giá trị `String` đặc biệt (magic) tới một số nguyên, như trong ví dụ sau:

```java
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("my")
public class MyProperties {

    private Map<String, Integer> contexts;

    // getters/setters ...

    public Map<String, Integer> getContexts() {
        return this.contexts;
    }

    public void setContexts(Map<String, Integer> contexts) {
        this.contexts = contexts;
    }

}
```

Các giá trị đặc biệt (trong ví dụ này) là `sample1` và `sample2`. Để cung cấp thêm trợ giúp nội dung cho các key, bạn có thể thêm JSON sau vào [metadata thủ công của module](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#appendix.configuration-metadata.annotation-processor.adding-additional-metadata):

```json
{"hints": [
    {
        "name": "my.contexts.keys",
        "values": [
            {
                "value": "sample1"
            },
            {
                "value": "sample2"
            }
        ]
    }
]}
```

> **MẸO (TIP):** Chúng tôi khuyến nghị bạn dùng một `Enum` cho hai giá trị đó thay thế. Nếu IDE của bạn hỗ trợ, đây là cách tiếp cận hiệu quả nhất cho việc tự động hoàn thành.

## Value provider (Value Providers)

Provider là một cách mạnh mẽ để gắn ngữ nghĩa cho một property. Trong phần này, chúng tôi định nghĩa các provider chính thức mà bạn có thể dùng cho các hint của riêng mình. Tuy nhiên, IDE yêu thích của bạn có thể hiện thực một số hoặc không hiện thực provider nào trong số này. Ngoài ra, nó cuối cùng cũng có thể cung cấp provider của riêng nó.

> **LƯU Ý (NOTE):** Vì đây là một tính năng mới, các nhà cung cấp IDE phải bắt kịp cách nó hoạt động. Thời gian áp dụng đương nhiên sẽ khác nhau.

Bảng sau tóm tắt danh sách các provider được hỗ trợ:

| Tên | Mô tả |
|---|---|
| `any` | Cho phép cung cấp bất kỳ giá trị bổ sung nào. |
| `class-reference` | Tự động hoàn thành các class có sẵn trong dự án. Thường bị giới hạn bởi một class cơ sở được chỉ định bởi tham số `target`. |
| `handle-as` | Xử lý property như thể nó được định nghĩa bởi kiểu được định nghĩa bởi tham số bắt buộc `target`. |
| `logger-name` | Tự động hoàn thành các tên logger hợp lệ và [logger group](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.logging.log-groups). Thông thường, tên package và tên class có sẵn trong dự án hiện tại có thể được tự động hoàn thành cũng như các group đã định nghĩa. |
| `spring-bean-reference` | Tự động hoàn thành các tên bean có sẵn trong dự án hiện tại. Thường bị giới hạn bởi một class cơ sở được chỉ định bởi tham số `target`. |
| `spring-profile-name` | Tự động hoàn thành các tên Spring profile có sẵn trong dự án. |

> **MẸO (TIP):** Chỉ một provider có thể hoạt động cho một property nhất định, nhưng bạn có thể chỉ định nhiều provider nếu tất cả chúng đều có thể quản lý property *theo một cách nào đó*. Hãy đảm bảo đặt provider mạnh nhất lên đầu, vì IDE phải dùng provider đầu tiên trong phần JSON mà nó có thể xử lý. Nếu không có provider nào cho một property nhất định được hỗ trợ, cũng không có trợ giúp nội dung đặc biệt nào được cung cấp.

### Any

Giá trị provider đặc biệt **any** cho phép cung cấp bất kỳ giá trị bổ sung nào. Việc validate giá trị thông thường dựa trên kiểu property nên được áp dụng nếu được hỗ trợ.

Provider này thường được dùng nếu bạn có một danh sách giá trị và mọi giá trị thêm vẫn nên được coi là hợp lệ.

Ví dụ sau cung cấp `on` và `off` làm các giá trị tự động hoàn thành cho `system.state`:

```json
{"hints": [
    {
        "name": "system.state",
        "values": [
            {
                "value": "on"
            },
            {
                "value": "off"
            }
        ],
        "providers": [
            {
                "name": "any"
            }
        ]
    }
]}
```

Lưu ý rằng, trong ví dụ trên, mọi giá trị khác cũng được cho phép.

### Class Reference

Provider **class-reference** tự động hoàn thành các class có sẵn trong dự án. Provider này hỗ trợ các tham số sau:

| Tham số | Kiểu | Giá trị mặc định | Mô tả |
|---|---|---|---|
| `target` | `String` (`Class`) | *none* | Tên đầy đủ của class mà giá trị được chọn phải có thể gán được cho nó. Thường được dùng để lọc bỏ các class không phải ứng viên. Lưu ý rằng thông tin này có thể được chính kiểu cung cấp bằng cách expose một class với upper bound phù hợp. |
| `concrete` | `boolean` | true | Chỉ định có chỉ coi các class cụ thể (concrete) là ứng viên hợp lệ hay không. |

Đoạn metadata sau tương ứng với property chuẩn `server.servlet.jsp.class-name` định nghĩa tên class `JspServlet` cần dùng:

```json
{"hints": [
    {
        "name": "server.servlet.jsp.class-name",
        "providers": [
            {
                "name": "class-reference",
                "parameters": {
                    "target": "jakarta.servlet.http.HttpServlet"
                }
            }
        ]
    }
]}
```

### Handle As

Provider **handle-as** cho phép bạn thay thế kiểu của property bằng một kiểu cấp cao hơn. Điều này thường xảy ra khi property có kiểu `java.lang.String`, vì bạn không muốn các class cấu hình của mình phụ thuộc vào các class có thể không có trên classpath. Provider này hỗ trợ các tham số sau:

| Tham số | Kiểu | Giá trị mặc định | Mô tả |
|---|---|---|---|
| **`target`** | `String` (`Class`) | *none* | Tên đầy đủ của kiểu cần xem xét cho property. Tham số này là bắt buộc. |

Các kiểu sau có thể được dùng:

- Bất kỳ `java.lang.Enum` nào: Liệt kê các giá trị có thể có cho property. (Chúng tôi khuyến nghị định nghĩa property với kiểu `Enum`, vì không cần thêm hint nào để IDE tự động hoàn thành các giá trị)
- `java.nio.charset.Charset`: Hỗ trợ tự động hoàn thành các giá trị charset/encoding (chẳng hạn như `UTF-8`)
- `java.util.Locale`: tự động hoàn thành các locale (chẳng hạn như `en_US`)
- `org.springframework.util.MimeType`: Hỗ trợ tự động hoàn thành các giá trị content type (chẳng hạn như `text/plain`)
- `org.springframework.core.io.Resource`: Hỗ trợ tự động hoàn thành abstraction Resource của Spring để tham chiếu tới một file trên filesystem hoặc trên classpath (chẳng hạn như `classpath:/sample.properties`)

> **MẸO (TIP):** Nếu có thể cung cấp nhiều giá trị, hãy dùng kiểu `Collection` hoặc *Array* để cho IDE biết về điều đó.

Đoạn metadata sau tương ứng với property chuẩn `spring.liquibase.change-log` định nghĩa đường dẫn tới changelog cần dùng. Thực ra nó được dùng nội bộ dưới dạng `org.springframework.core.io.Resource` nhưng không thể được expose như vậy, vì chúng ta cần giữ giá trị String gốc để truyền nó cho Liquibase API.

```json
{"hints": [
    {
        "name": "spring.liquibase.change-log",
        "providers": [
            {
                "name": "handle-as",
                "parameters": {
                    "target": "org.springframework.core.io.Resource"
                }
            }
        ]
    }
]}
```

### Logger Name

Provider **logger-name** tự động hoàn thành các tên logger hợp lệ và [logger group](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.logging.log-groups). Thông thường, tên package và tên class có sẵn trong dự án hiện tại có thể được tự động hoàn thành. Nếu group được bật (mặc định) và nếu một logger group tùy chỉnh được xác định trong cấu hình, việc tự động hoàn thành cho nó nên được cung cấp. Các framework cụ thể có thể có thêm các tên logger đặc biệt cũng có thể được hỗ trợ.

Provider này hỗ trợ các tham số sau:

| Tham số | Kiểu | Giá trị mặc định | Mô tả |
|---|---|---|---|
| `group` | `boolean` | `true` | Chỉ định có xem xét các group đã biết hay không. |

Vì tên logger có thể là bất kỳ tên tùy ý nào, provider này nên cho phép mọi giá trị nhưng có thể làm nổi bật các tên package và tên class hợp lệ không có trên classpath của dự án.

Đoạn metadata sau tương ứng với property chuẩn `logging.level`. Key là *tên logger*, và value tương ứng với các log level chuẩn hoặc bất kỳ level tùy chỉnh nào. Vì Spring Boot định nghĩa sẵn một vài logger group, các value hint chuyên dụng đã được thêm cho chúng.

```json
{"hints": [
    {
        "name": "logging.level.keys",
        "values": [
            {
                "value": "root",
                "description": "Root logger used to assign the default logging level."
            },
            {
                "value": "sql",
                "description": "SQL logging group including Hibernate SQL logger."
            },
            {
                "value": "web",
                "description": "Web logging group including codecs."
            }
        ],
        "providers": [
            {
                "name": "logger-name"
            }
        ]
    },
    {
        "name": "logging.level.values",
        "values": [
            {
                "value": "trace"
            },
            {
                "value": "debug"
            },
            {
                "value": "info"
            },
            {
                "value": "warn"
            },
            {
                "value": "error"
            },
            {
                "value": "fatal"
            },
            {
                "value": "off"
            }

        ],
        "providers": [
            {
                "name": "any"
            }
        ]
    }
]}
```

### Spring Bean Reference

Provider **spring-bean-reference** tự động hoàn thành các bean được định nghĩa trong cấu hình của dự án hiện tại. Provider này hỗ trợ các tham số sau:

| Tham số | Kiểu | Giá trị mặc định | Mô tả |
|---|---|---|---|
| `target` | `String` (`Class`) | *none* | Tên đầy đủ của class bean mà ứng viên phải có thể gán được cho nó. Thường được dùng để lọc bỏ các bean không phải ứng viên. |

Đoạn metadata sau tương ứng với property chuẩn `spring.jmx.server` định nghĩa tên của bean `MBeanServer` cần dùng:

```json
{"hints": [
    {
        "name": "spring.jmx.server",
        "providers": [
            {
                "name": "spring-bean-reference",
                "parameters": {
                    "target": "javax.management.MBeanServer"
                }
            }
        ]
    }
]}
```

> **LƯU Ý (NOTE):** Binder không biết về metadata. Nếu bạn cung cấp hint đó, bạn vẫn cần chuyển đổi tên bean thành một tham chiếu Bean thực tế bằng cách dùng `ApplicationContext`.

### Spring Profile Name

Provider **spring-profile-name** tự động hoàn thành các Spring profile được định nghĩa trong cấu hình của dự án hiện tại.

Đoạn metadata sau tương ứng với property chuẩn `spring.profiles.active` định nghĩa tên của (các) Spring profile cần bật:

```json
{"hints": [
    {
        "name": "spring.profiles.active",
        "providers": [
            {
                "name": "spring-profile-name"
            }
        ]
    }
]}
```

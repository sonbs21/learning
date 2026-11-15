# E.4. Các tính năng của PropertiesLauncher (PropertiesLauncher Features)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#appendix.executable-jar.property-launcher>

`PropertiesLauncher` có một vài tính năng đặc biệt có thể được bật bằng các property bên ngoài (System property, biến môi trường, mục manifest, hoặc `loader.properties`). Bảng sau mô tả các property này:

| Key | Mục đích |
|---|---|
| `loader.path` | Classpath phân tách bằng dấu phẩy, chẳng hạn như `lib,${HOME}/app/lib`. Các mục đứng trước được ưu tiên, giống như một `-classpath` thông thường trên dòng lệnh `javac`. |
| `loader.home` | Được dùng để phân giải các đường dẫn tương đối trong `loader.path`. Ví dụ, với `loader.path=lib`, thì `${loader.home}/lib` là một vị trí classpath (cùng với tất cả các file jar trong thư mục đó). Property này cũng được dùng để định vị một file `loader.properties`, như trong ví dụ sau `file:///opt/app`. Nó mặc định là `${user.dir}`. |
| `loader.args` | Các tham số mặc định cho phương thức main (phân tách bằng dấu cách). |
| `loader.main` | Tên của main class cần khởi chạy (ví dụ, `com.app.Application`). |
| `loader.config.name` | Tên của file properties (ví dụ, `launcher`). Nó mặc định là `loader`. |
| `loader.config.location` | Đường dẫn tới file properties (ví dụ, `classpath:loader.properties`). Nó mặc định là `loader.properties`. |
| `loader.system` | Cờ boolean để chỉ ra rằng tất cả các property nên được thêm vào System property. Nó mặc định là `false`. |

Khi được chỉ định dưới dạng biến môi trường hoặc mục manifest, các tên sau nên được dùng:

| Key | Mục manifest | Biến môi trường |
|---|---|---|
| `loader.path` | `Loader-Path` | `LOADER_PATH` |
| `loader.home` | `Loader-Home` | `LOADER_HOME` |
| `loader.args` | `Loader-Args` | `LOADER_ARGS` |
| `loader.main` | `Start-Class` | `LOADER_MAIN` |
| `loader.config.location` | `Loader-Config-Location` | `LOADER_CONFIG_LOCATION` |
| `loader.system` | `Loader-System` | `LOADER_SYSTEM` |

> **MẸO (TIP):** Các build plugin tự động chuyển thuộc tính `Main-Class` sang `Start-Class` khi uber jar được build. Nếu bạn dùng chúng, hãy chỉ định tên của class cần khởi chạy bằng thuộc tính `Main-Class` và bỏ qua `Start-Class`.

Các quy tắc sau áp dụng khi làm việc với `PropertiesLauncher`:

- `loader.properties` được tìm trong `loader.home`, sau đó ở gốc classpath, và sau đó trong `classpath:/BOOT-INF/classes`. Vị trí đầu tiên có tồn tại file với tên đó sẽ được dùng.
- `loader.home` là vị trí thư mục của một file properties bổ sung (ghi đè mặc định) chỉ khi `loader.config.location` không được chỉ định.
- `loader.path` có thể chứa các thư mục (được quét đệ quy để tìm file jar và zip), đường dẫn file nén, một thư mục bên trong file nén được quét để tìm file jar (ví dụ, `dependencies.jar!/lib`), hoặc các pattern wildcard (cho hành vi JVM mặc định). Đường dẫn file nén có thể tương đối với `loader.home` hoặc ở bất kỳ đâu trong hệ thống file với tiền tố `jar:file:`.
- `loader.path` (nếu rỗng) mặc định là `BOOT-INF/lib` (nghĩa là một thư mục cục bộ hoặc một thư mục lồng nhau nếu chạy từ một file nén). Vì điều này, `PropertiesLauncher` hoạt động giống như `JarLauncher` khi không có cấu hình bổ sung nào được cung cấp.
- `loader.path` không thể được dùng để cấu hình vị trí của `loader.properties` (classpath được dùng để tìm file sau là classpath của JVM khi `PropertiesLauncher` được khởi chạy).
- Việc thay thế placeholder được thực hiện từ System và biến môi trường cùng với chính file properties trên tất cả các giá trị trước khi sử dụng.
- Thứ tự tìm kiếm property (ở những chỗ hợp lý để tìm ở nhiều hơn một nơi) là biến môi trường, system property, `loader.properties`, manifest của file nén đã giải nén, và manifest của file nén.

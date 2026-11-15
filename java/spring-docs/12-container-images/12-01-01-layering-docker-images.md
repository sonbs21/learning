# 12.1.1. Phân lớp Docker image (Layering Docker Images)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#container-images.efficient-images.layering>

Để giúp việc tạo các Docker image được tối ưu dễ dàng hơn, Spring Boot hỗ trợ thêm một file chỉ mục layer (layer index) vào jar. Nó cung cấp danh sách các layer và các phần của jar nên được chứa trong chúng. Danh sách các layer trong chỉ mục được sắp xếp dựa trên thứ tự mà các layer nên được thêm vào image Docker/OCI. Mặc định, các layer sau được hỗ trợ:

- `dependencies` (cho các dependency đã phát hành thông thường)
- `spring-boot-loader` (cho mọi thứ bên dưới `org/springframework/boot/loader`)
- `snapshot-dependencies` (cho các dependency snapshot)
- `application` (cho các class và resource của ứng dụng)

Sau đây là ví dụ về một file `layers.idx`:

```yaml
- "dependencies":
  - BOOT-INF/lib/library1.jar
  - BOOT-INF/lib/library2.jar
- "spring-boot-loader":
  - org/springframework/boot/loader/launch/JarLauncher.class
  - ... <other classes>
- "snapshot-dependencies":
  - BOOT-INF/lib/library3-SNAPSHOT.jar
- "application":
  - META-INF/MANIFEST.MF
  - BOOT-INF/classes/a/b/C.class
```

Việc phân lớp này được thiết kế để tách code dựa trên khả năng nó thay đổi giữa các lần build ứng dụng. Code thư viện ít có khả năng thay đổi giữa các lần build, vì vậy nó được đặt trong các layer riêng để cho phép công cụ tái sử dụng các layer từ cache. Code ứng dụng có nhiều khả năng thay đổi giữa các lần build hơn, vì vậy nó được cô lập trong một layer riêng.

Spring Boot cũng hỗ trợ phân lớp cho các file war với sự trợ giúp của `layers.idx`.

Với Maven, xem [phần đóng gói jar hoặc war có phân lớp](https://docs.spring.io/spring-boot/docs/3.2.5/maven-plugin/reference/htmlsingle/#repackage-layers) để biết thêm chi tiết về việc thêm chỉ mục layer vào file nén. Với Gradle, xem [phần đóng gói jar hoặc war có phân lớp](https://docs.spring.io/spring-boot/docs/3.2.5/gradle-plugin/reference/htmlsingle/#packaging-layered-archives) trong tài liệu Gradle plugin.

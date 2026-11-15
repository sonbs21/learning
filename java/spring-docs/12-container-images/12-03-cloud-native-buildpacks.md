# 12.3. Cloud Native Buildpacks

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#container-images.buildpacks>

Dockerfile chỉ là một cách để build docker image. Một cách khác để build docker image là trực tiếp từ plugin Maven hoặc Gradle, bằng cách dùng buildpack. Nếu bạn từng dùng một nền tảng ứng dụng như Cloud Foundry hoặc Heroku thì có lẽ bạn đã dùng buildpack. Buildpack là phần của nền tảng nhận ứng dụng của bạn và chuyển đổi nó thành thứ mà nền tảng thực sự có thể chạy. Ví dụ, buildpack Java của Cloud Foundry sẽ nhận thấy bạn đang đẩy lên một file `.jar` và tự động thêm một JRE phù hợp.

Với Cloud Native Buildpacks, bạn có thể tạo các image tương thích Docker có thể chạy ở bất kỳ đâu. Spring Boot có hỗ trợ buildpack trực tiếp cho cả Maven và Gradle. Điều này có nghĩa là bạn chỉ cần gõ một lệnh duy nhất và nhanh chóng có được một image hợp lý trong Docker daemon đang chạy ở máy cục bộ.

Xem tài liệu của từng plugin về cách dùng buildpack với [Maven](https://docs.spring.io/spring-boot/docs/3.2.5/maven-plugin/reference/htmlsingle/#build-image) và [Gradle](https://docs.spring.io/spring-boot/docs/3.2.5/gradle-plugin/reference/htmlsingle/#build-image).

> **LƯU Ý (NOTE):** [Paketo Spring Boot buildpack](https://github.com/paketo-buildpacks/spring-boot) hỗ trợ file `layers.idx`, vì vậy mọi tùy chỉnh được áp dụng lên nó sẽ được phản ánh trong image do buildpack tạo ra.

> **LƯU Ý (NOTE):** Để đạt được các bản build có thể tái tạo (reproducible build) và cache container image, Buildpack có thể thao tác metadata của tài nguyên ứng dụng (chẳng hạn như thông tin "last modified" của file). Bạn nên đảm bảo ứng dụng không dựa vào metadata đó tại runtime. Spring Boot có thể dùng thông tin đó khi phục vụ tài nguyên tĩnh, nhưng điều này có thể được tắt bằng `spring.web.resources.cache.use-last-modified`.

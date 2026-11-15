# 14.1.1. Cloud Foundry

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#deployment.cloud.cloud-foundry>

Cloud Foundry cung cấp các buildpack mặc định được dùng nếu không có buildpack nào khác được chỉ định. [Java buildpack](https://github.com/cloudfoundry/java-buildpack) của Cloud Foundry có hỗ trợ tuyệt vời cho các ứng dụng Spring, bao gồm Spring Boot. Bạn có thể triển khai các ứng dụng jar thực thi độc lập cũng như các ứng dụng được đóng gói `.war` truyền thống.

Sau khi bạn đã build ứng dụng (ví dụ, bằng `mvn clean package`) và đã [cài đặt công cụ dòng lệnh `cf`](https://docs.cloudfoundry.org/cf-cli/install-go-cli.html), hãy triển khai ứng dụng bằng lệnh `cf push`, thay thế bằng đường dẫn tới file `.jar` đã biên dịch. Hãy đảm bảo đã [đăng nhập bằng client dòng lệnh `cf`](https://docs.cloudfoundry.org/cf-cli/getting-started.html#login) trước khi đẩy một ứng dụng lên. Dòng sau cho thấy việc dùng lệnh `cf push` để triển khai một ứng dụng:

```shell
$ cf push acloudyspringtime -p target/demo-0.0.1-SNAPSHOT.jar
```

> **LƯU Ý (NOTE):** Trong ví dụ trên, chúng tôi dùng `acloudyspringtime` thay cho bất kỳ giá trị nào bạn đặt cho `cf` làm tên ứng dụng.

Xem [tài liệu `cf push`](https://docs.cloudfoundry.org/cf-cli/getting-started.html#push) để biết thêm các tùy chọn. Nếu có file [`manifest.yml`](https://docs.cloudfoundry.org/devguide/deploy-apps/manifest.html) của Cloud Foundry trong cùng thư mục, nó sẽ được xem xét.

Lúc này, `cf` bắt đầu tải ứng dụng của bạn lên, tạo ra output tương tự như ví dụ sau:

```
Uploading acloudyspringtime... OK
Preparing to start acloudyspringtime... OK
-----> Downloaded app package (8.9M)
-----> Java Buildpack Version: v3.12 (offline) | https://github.com/cloudfoundry/java-buildpack.git#6f25b7e
-----> Downloading Open Jdk JRE
       Expanding Open Jdk JRE to .java-buildpack/open_jdk_jre (1.6s)
-----> Downloading Open JDK Like Memory Calculator 2.0.2_RELEASE from https://java-buildpack.cloudfoundry.org/memory-calculator/trusty/x86_64/memory-calculator-2.0.2_RELEASE.tar.gz (found in cache)
       Memory Settings: -Xss349K -Xmx681574K -XX:MaxMetaspaceSize=104857K -Xms681574K -XX:MetaspaceSize=104857K
-----> Downloading Container Certificate Trust Store 1.0.0_RELEASE from https://java-buildpack.cloudfoundry.org/container-certificate-trust-store/container-certificate-trust-store-1.0.0_RELEASE.jar (found in cache)
       Adding certificates to .java-buildpack/container_certificate_trust_store/truststore.jks (0.6s)
-----> Downloading Spring Auto Reconfiguration 1.10.0_RELEASE from https://java-buildpack.cloudfoundry.org/auto-reconfiguration/auto-reconfiguration-1.10.0_RELEASE.jar (found in cache)
Checking status of app 'acloudyspringtime'...
  0 of 1 instances running (1 starting)
  ...
  0 of 1 instances running (1 starting)
  ...
  0 of 1 instances running (1 starting)
  ...
  1 of 1 instances running (1 running)

App started
```

Xin chúc mừng! Ứng dụng giờ đã chạy!

Sau khi ứng dụng đã chạy, bạn có thể kiểm tra trạng thái của ứng dụng đã triển khai bằng lệnh `cf apps`, như trong ví dụ sau:

```shell
$ cf apps
Getting applications in ...
OK

name                 requested state   instances   memory   disk   urls
...
acloudyspringtime    started           1/1         512M     1G     acloudyspringtime.cfapps.io
...
```

Sau khi Cloud Foundry xác nhận rằng ứng dụng đã được triển khai, bạn sẽ có thể tìm thấy ứng dụng tại URI đã cho. Trong ví dụ trên, bạn có thể tìm thấy nó tại `https://acloudyspringtime.cfapps.io/`.

## Gắn với các service (Binding to Services)

Theo mặc định, metadata về ứng dụng đang chạy cũng như thông tin kết nối service được expose cho ứng dụng dưới dạng biến môi trường (ví dụ: `$VCAP_SERVICES`). Quyết định kiến trúc này là do bản chất đa ngôn ngữ (polyglot) của Cloud Foundry (bất kỳ ngôn ngữ và nền tảng nào cũng có thể được hỗ trợ dưới dạng buildpack). Biến môi trường có phạm vi tiến trình không phụ thuộc ngôn ngữ.

Biến môi trường không phải lúc nào cũng tạo thành API dễ dùng nhất, vì vậy Spring Boot tự động trích xuất chúng và làm phẳng dữ liệu thành các property có thể truy cập thông qua abstraction `Environment` của Spring, như trong ví dụ sau:

**Java**

```java
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class MyBean implements EnvironmentAware {

    private String instanceId;

    @Override
    public void setEnvironment(Environment environment) {
        this.instanceId = environment.getProperty("vcap.application.instance_id");
    }

    // ...

}
```

**Kotlin**

```kotlin
import org.springframework.context.EnvironmentAware
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component

@Component
class MyBean : EnvironmentAware {

    private var instanceId: String? = null

    override fun setEnvironment(environment: Environment) {
        instanceId = environment.getProperty("vcap.application.instance_id")
    }

    // ...

}
```

Tất cả các property của Cloud Foundry đều có tiền tố `vcap`. Bạn có thể dùng các property `vcap` để truy cập thông tin ứng dụng (chẳng hạn như URL công khai của ứng dụng) và thông tin service (chẳng hạn như thông tin xác thực database). Xem Javadoc của [`CloudFoundryVcapEnvironmentPostProcessor`](https://docs.spring.io/spring-boot/docs/3.2.5/api/org/springframework/boot/cloud/CloudFoundryVcapEnvironmentPostProcessor.html) để biết đầy đủ chi tiết.

> **MẸO (TIP):** Project [Java CFEnv](https://github.com/pivotal-cf/java-cfenv/) phù hợp hơn cho các tác vụ như cấu hình DataSource.

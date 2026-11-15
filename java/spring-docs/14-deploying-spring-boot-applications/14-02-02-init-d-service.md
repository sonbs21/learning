# 14.2.2. Cài đặt như một service init.d (System V) (Installation as an init.d Service (System V))

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#deployment.installing.init-d>

Để dùng ứng dụng như một service `init.d`, hãy cấu hình bản build của nó để tạo ra một [jar thực thi đầy đủ (fully executable jar)](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#deployment.installing).

> **THẬN TRỌNG (CAUTION):** Jar thực thi đầy đủ hoạt động bằng cách nhúng thêm một script ở đầu file. Hiện tại, một số công cụ không chấp nhận định dạng này, vì vậy không phải lúc nào bạn cũng có thể dùng kỹ thuật này. Ví dụ, `jar -xf` có thể âm thầm thất bại khi giải nén một jar hoặc war đã được làm thành thực thi đầy đủ. Khuyến nghị chỉ làm cho jar hoặc war của bạn thực thi đầy đủ nếu bạn định thực thi nó trực tiếp, thay vì chạy nó bằng `java -jar` hoặc triển khai nó lên một servlet container.

> **THẬN TRỌNG (CAUTION):** Một file jar định dạng zip64 không thể được làm thành thực thi đầy đủ. Cố gắng làm vậy sẽ cho ra một file jar bị báo là hỏng khi thực thi trực tiếp hoặc bằng `java -jar`. Một file jar định dạng tiêu chuẩn chứa một hoặc nhiều jar lồng nhau định dạng zip64 vẫn có thể thực thi đầy đủ.

Để tạo một jar "thực thi đầy đủ" bằng Maven, hãy dùng cấu hình plugin sau:

```xml
<plugin>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-maven-plugin</artifactId>
    <configuration>
        <executable>true</executable>
    </configuration>
</plugin>
```

Ví dụ sau cho thấy cấu hình Gradle tương đương:

```gradle
tasks.named('bootJar') {
    launchScript()
}
```

Sau đó, nó có thể được tạo symlink vào `init.d` để hỗ trợ các lệnh tiêu chuẩn `start`, `stop`, `restart`, và `status`.

Launch script mặc định được thêm vào một jar thực thi đầy đủ hỗ trợ hầu hết các bản phân phối Linux và được kiểm thử trên CentOS và Ubuntu. Các nền tảng khác, như OS X và FreeBSD, yêu cầu dùng một script tùy chỉnh. Script mặc định hỗ trợ các tính năng sau:

- Khởi động service dưới user sở hữu file jar
- Theo dõi PID của ứng dụng bằng `/var/run/<appname>/<appname>.pid`
- Ghi log console vào `/var/log/<appname>.log`

Giả sử bạn có một ứng dụng Spring Boot được cài đặt trong `/var/myapp`, để cài đặt một ứng dụng Spring Boot như một service `init.d`, hãy tạo một symlink như sau:

```shell
$ sudo ln -s /var/myapp/myapp.jar /etc/init.d/myapp
```

Sau khi cài đặt, bạn có thể khởi động và dừng service theo cách thông thường. Ví dụ, trên một hệ thống dựa trên Debian, bạn có thể khởi động nó bằng lệnh sau:

```shell
$ service myapp start
```

> **MẸO (TIP):** Nếu ứng dụng khởi động thất bại, hãy kiểm tra file log được ghi vào `/var/log/<appname>.log` để tìm lỗi.

Bạn cũng có thể đánh dấu ứng dụng tự động khởi động bằng các công cụ tiêu chuẩn của hệ điều hành. Ví dụ, trên Debian, bạn có thể dùng lệnh sau:

```shell
$ update-rc.d myapp defaults <priority>
```

## Bảo mật một service init.d (Securing an init.d Service)

> **LƯU Ý (NOTE):** Sau đây là một tập hợp các hướng dẫn về cách bảo mật một ứng dụng Spring Boot chạy dưới dạng service init.d. Nó không nhằm là một danh sách đầy đủ mọi thứ nên làm để gia cố (harden) một ứng dụng và môi trường mà nó chạy.

Khi được thực thi dưới quyền root, như trường hợp khi root được dùng để khởi động một service init.d, script thực thi mặc định chạy ứng dụng dưới user được chỉ định trong biến môi trường `RUN_AS_USER`. Khi biến môi trường này không được đặt, user sở hữu file jar sẽ được dùng thay thế. Bạn không bao giờ nên chạy một ứng dụng Spring Boot dưới quyền `root`, vì vậy `RUN_AS_USER` không bao giờ nên là root và file jar của ứng dụng không bao giờ nên thuộc sở hữu của root. Thay vào đó, hãy tạo một user cụ thể để chạy ứng dụng và đặt biến môi trường `RUN_AS_USER` hoặc dùng `chown` để làm cho user đó trở thành chủ sở hữu của file jar, như trong ví dụ sau:

```shell
$ chown bootapp:bootapp your-app.jar
```

Trong trường hợp này, script thực thi mặc định chạy ứng dụng dưới user `bootapp`.

> **MẸO (TIP):** Để giảm khả năng tài khoản user của ứng dụng bị xâm phạm, bạn nên cân nhắc ngăn nó dùng login shell. Ví dụ, bạn có thể đặt shell của tài khoản thành `/usr/sbin/nologin`.

Bạn cũng nên thực hiện các bước để ngăn việc sửa đổi file jar của ứng dụng. Thứ nhất, hãy cấu hình quyền của nó để nó không thể bị ghi và chỉ có thể được đọc hoặc thực thi bởi chủ sở hữu, như trong ví dụ sau:

```shell
$ chmod 500 your-app.jar
```

Thứ hai, bạn cũng nên thực hiện các bước để hạn chế thiệt hại nếu ứng dụng hoặc tài khoản đang chạy nó bị xâm phạm. Nếu kẻ tấn công giành được quyền truy cập, họ có thể làm cho file jar có thể ghi và thay đổi nội dung của nó. Một cách để bảo vệ chống lại điều này là làm cho nó bất biến (immutable) bằng `chattr`, như trong ví dụ sau:

```shell
$ sudo chattr +i your-app.jar
```

Điều này sẽ ngăn bất kỳ user nào, kể cả root, sửa đổi jar.

Nếu root được dùng để điều khiển service của ứng dụng và bạn [dùng một file `.conf`](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#deployment.installing.init-d.script-customization.when-running.conf-file) để tùy chỉnh việc khởi động của nó, file `.conf` sẽ được user root đọc và đánh giá. Nó nên được bảo mật tương ứng. Hãy dùng `chmod` để file chỉ có thể được đọc bởi chủ sở hữu và dùng `chown` để làm cho root trở thành chủ sở hữu, như trong ví dụ sau:

```shell
$ chmod 400 your-app.conf
$ sudo chown root:root your-app.conf
```

## Tùy chỉnh script khởi động (Customizing the Startup Script)

Script khởi động nhúng mặc định được ghi bởi plugin Maven hoặc Gradle có thể được tùy chỉnh theo nhiều cách. Với hầu hết mọi người, dùng script mặc định cùng với một vài tùy chỉnh thường là đủ. Nếu bạn thấy mình không thể tùy chỉnh thứ gì đó cần thiết, hãy dùng tùy chọn `embeddedLaunchScript` để tự viết toàn bộ file của riêng bạn.

### Tùy chỉnh script khởi động khi nó được ghi (Customizing the Start Script When It Is Written)

Thường hợp lý khi tùy chỉnh các thành phần của script khởi động khi nó được ghi vào file jar. Ví dụ, các script init.d có thể cung cấp một "description". Vì bạn biết trước description (và nó không cần thay đổi), bạn cũng có thể cung cấp nó khi jar được tạo ra.

Để tùy chỉnh các thành phần được ghi, hãy dùng tùy chọn `embeddedLaunchScriptProperties` của Spring Boot Maven plugin hoặc [property `properties` của `launchScript` trong Spring Boot Gradle plugin](https://docs.spring.io/spring-boot/docs/3.2.5/gradle-plugin/reference/htmlsingle/#packaging-executable-configuring-launch-script).

Các thay thế property sau được hỗ trợ với script mặc định:

| Tên | Mô tả | Mặc định Gradle | Mặc định Maven |
|---|---|---|---|
| `mode` | Chế độ của script. | `auto` | `auto` |
| `initInfoProvides` | Phần `Provides` của "INIT INFO" | `${task.baseName}` | `${project.artifactId}` |
| `initInfoRequiredStart` | Phần `Required-Start` của "INIT INFO". | `$remote_fs $syslog $network` | `$remote_fs $syslog $network` |
| `initInfoRequiredStop` | Phần `Required-Stop` của "INIT INFO". | `$remote_fs $syslog $network` | `$remote_fs $syslog $network` |
| `initInfoDefaultStart` | Phần `Default-Start` của "INIT INFO". | `2 3 4 5` | `2 3 4 5` |
| `initInfoDefaultStop` | Phần `Default-Stop` của "INIT INFO". | `0 1 6` | `0 1 6` |
| `initInfoShortDescription` | Phần `Short-Description` của "INIT INFO". | Phiên bản một dòng của `${project.description}` (dự phòng là `${task.baseName}`) | `${project.name}` |
| `initInfoDescription` | Phần `Description` của "INIT INFO". | `${project.description}` (dự phòng là `${task.baseName}`) | `${project.description}` (dự phòng là `${project.name}`) |
| `initInfoChkconfig` | Phần `chkconfig` của "INIT INFO" | `2345 99 01` | `2345 99 01` |
| `confFolder` | Giá trị mặc định cho `CONF_FOLDER` | Thư mục chứa jar | Thư mục chứa jar |
| `inlinedConfScript` | Tham chiếu tới một file script nên được nhúng inline vào launch script mặc định. Điều này có thể được dùng để đặt các biến môi trường như `JAVA_OPTS` trước khi bất kỳ file cấu hình bên ngoài nào được nạp |  |  |
| `logFolder` | Giá trị mặc định cho `LOG_FOLDER`. Chỉ hợp lệ cho một service `init.d` |  |  |
| `logFilename` | Giá trị mặc định cho `LOG_FILENAME`. Chỉ hợp lệ cho một service `init.d` |  |  |
| `pidFolder` | Giá trị mặc định cho `PID_FOLDER`. Chỉ hợp lệ cho một service `init.d` |  |  |
| `pidFilename` | Giá trị mặc định cho tên của file PID trong `PID_FOLDER`. Chỉ hợp lệ cho một service `init.d` |  |  |
| `useStartStopDaemon` | Có nên dùng lệnh `start-stop-daemon`, khi có sẵn, để điều khiển tiến trình hay không | `true` | `true` |
| `stopWaitTime` | Giá trị mặc định cho `STOP_WAIT_TIME` tính bằng giây. Chỉ hợp lệ cho một service `init.d` | 60 | 60 |

### Tùy chỉnh script khi nó chạy (Customizing a Script When It Runs)

Với các mục của script cần được tùy chỉnh *sau khi* jar đã được ghi, bạn có thể dùng biến môi trường hoặc một [file cấu hình](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#deployment.installing.init-d.script-customization.when-running.conf-file).

Các environment property sau được hỗ trợ với script mặc định:

| Biến | Mô tả |
|---|---|
| `MODE` | "Chế độ" hoạt động. Giá trị mặc định phụ thuộc vào cách jar được build nhưng thường là `auto` (nghĩa là nó cố gắng đoán xem đó có phải là một init script hay không bằng cách kiểm tra xem nó có phải là một symlink trong thư mục tên `init.d` hay không). Bạn có thể đặt tường minh thành `service` để các lệnh `stop\|start\|status\|restart` hoạt động, hoặc thành `run` nếu bạn muốn chạy script ở foreground. |
| `RUN_AS_USER` | User sẽ được dùng để chạy ứng dụng. Khi không được đặt, user sở hữu file jar sẽ được dùng. |
| `USE_START_STOP_DAEMON` | Có nên dùng lệnh `start-stop-daemon`, khi có sẵn, để điều khiển tiến trình hay không. Mặc định là `true`. |
| `PID_FOLDER` | Tên gốc của thư mục pid (mặc định là `/var/run`). |
| `LOG_FOLDER` | Tên của thư mục để đặt các file log (mặc định là `/var/log`). |
| `CONF_FOLDER` | Tên của thư mục để đọc các file .conf (mặc định là cùng thư mục với file jar). |
| `LOG_FILENAME` | Tên của file log trong `LOG_FOLDER` (mặc định là `<appname>.log`). |
| `APP_NAME` | Tên của ứng dụng. Nếu jar được chạy từ một symlink, script sẽ đoán tên ứng dụng. Nếu nó không phải là symlink hoặc bạn muốn đặt tường minh tên ứng dụng, điều này có thể hữu ích. |
| `RUN_ARGS` | Các tham số truyền cho chương trình (ứng dụng Spring Boot). |
| `JAVA_HOME` | Vị trí của file thực thi `java` được phát hiện bằng `PATH` theo mặc định, nhưng bạn có thể đặt nó tường minh nếu có một file thực thi tại `$JAVA_HOME/bin/java`. |
| `JAVA_OPTS` | Các tùy chọn được truyền cho JVM khi nó được khởi chạy. |
| `JARFILE` | Vị trí tường minh của file jar, trong trường hợp script đang được dùng để khởi chạy một jar mà nó thực ra không được nhúng vào. |
| `DEBUG` | Nếu không rỗng, đặt cờ `-x` trên tiến trình shell, cho phép bạn thấy logic trong script. |
| `STOP_WAIT_TIME` | Thời gian tính bằng giây để chờ khi dừng ứng dụng trước khi buộc tắt (mặc định là `60`). |

> **LƯU Ý (NOTE):** Các biến `PID_FOLDER`, `LOG_FOLDER`, và `LOG_FILENAME` chỉ hợp lệ cho một service `init.d`. Với `systemd`, các tùy chỉnh tương đương được thực hiện bằng script 'service'. Xem [trang man về cấu hình service unit](https://www.freedesktop.org/software/systemd/man/systemd.service.html) để biết thêm chi tiết.

#### Sử dụng một file Conf (Using a Conf Gile)

Ngoại trừ `JARFILE` và `APP_NAME`, các thiết lập được liệt kê trong phần trước có thể được cấu hình bằng một file `.conf`. File này được mong đợi nằm cạnh file jar và có cùng tên nhưng có hậu tố `.conf` thay vì `.jar`. Ví dụ, một jar tên là `/var/myapp/myapp.jar` dùng file cấu hình tên là `/var/myapp/myapp.conf`, như trong ví dụ sau:

**myapp.conf**

```
JAVA_OPTS=-Xmx1024M
LOG_FOLDER=/custom/log/folder
```

> **MẸO (TIP):** Nếu bạn không thích đặt file cấu hình cạnh file jar, bạn có thể đặt biến môi trường `CONF_FOLDER` để tùy chỉnh vị trí của file cấu hình.

Để tìm hiểu về việc bảo mật file này một cách phù hợp, xem [hướng dẫn bảo mật một service init.d](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#deployment.installing.init-d.securing).

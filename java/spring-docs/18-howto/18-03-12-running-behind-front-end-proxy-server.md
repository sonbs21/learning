# 18.3.12. Chạy sau một front-end proxy server (Running Behind a Front-end Proxy Server)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.webserver.use-behind-a-proxy-server>

Nếu ứng dụng của bạn đang chạy sau một proxy, một load-balancer hoặc trên cloud, thông tin request (như host, port, scheme…​) có thể thay đổi trên đường đi. Ứng dụng của bạn có thể đang chạy trên `10.10.10.10:8080`, nhưng các HTTP client chỉ nên thấy `example.org`.

[RFC7239 "Forwarded Headers"](https://tools.ietf.org/html/rfc7239) định nghĩa HTTP header `Forwarded`; các proxy có thể dùng header này để cung cấp thông tin về request gốc. Bạn có thể cấu hình ứng dụng đọc các header đó và tự động dùng thông tin đó khi tạo link và gửi chúng tới client trong các response HTTP 302, tài liệu JSON hoặc trang HTML. Cũng có các header không chuẩn, như `X-Forwarded-Host`, `X-Forwarded-Port`, `X-Forwarded-Proto`, `X-Forwarded-Ssl`, và `X-Forwarded-Prefix`.

Nếu proxy thêm các header thường dùng `X-Forwarded-For` và `X-Forwarded-Proto`, việc đặt `server.forward-headers-strategy` thành `NATIVE` là đủ để hỗ trợ chúng. Với tùy chọn này, chính các web server hỗ trợ tính năng này một cách native; bạn có thể xem tài liệu riêng của chúng để tìm hiểu về hành vi cụ thể.

Nếu điều này chưa đủ, Spring Framework cung cấp một [ForwardedHeaderFilter](https://docs.spring.io/spring-framework/reference/6.1/web/webmvc/filters.html#filters-forwarded-headers) cho servlet stack và một [ForwardedHeaderTransformer](https://docs.spring.io/spring-framework/reference/6.1/web/webflux/reactive-spring.html#webflux-forwarded-headers) cho reactive stack. Bạn có thể dùng chúng trong ứng dụng bằng cách đặt `server.forward-headers-strategy` thành `FRAMEWORK`.

> **MẸO (TIP):** Nếu bạn đang dùng Tomcat và kết thúc SSL (terminating SSL) tại proxy, `server.tomcat.redirect-context-root` nên được đặt thành `false`. Điều này cho phép header `X-Forwarded-Proto` được tôn trọng trước khi bất kỳ redirect nào được thực hiện.

> **LƯU Ý (NOTE):** Nếu ứng dụng của bạn chạy trên Cloud Foundry, Heroku hoặc Kubernetes, property `server.forward-headers-strategy` mặc định là `NATIVE`. Trong tất cả các trường hợp khác, nó mặc định là `NONE`.

## Tùy chỉnh cấu hình proxy của Tomcat (Customize Tomcat's Proxy Configuration)

Nếu bạn dùng Tomcat, bạn có thể cấu hình thêm tên của các header được dùng để mang thông tin "forwarded", như trong ví dụ sau:

**Properties**

```properties
server.tomcat.remoteip.remote-ip-header=x-your-remote-ip-header
server.tomcat.remoteip.protocol-header=x-your-protocol-header
```

**Yaml**

```yaml
server:
  tomcat:
    remoteip:
      remote-ip-header: "x-your-remote-ip-header"
      protocol-header: "x-your-protocol-header"
```

Tomcat cũng được cấu hình với một biểu thức chính quy khớp với các proxy nội bộ cần được tin cậy. Xem [mục `server.tomcat.remoteip.internal-proxies` trong phụ lục](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#application-properties.server.server.tomcat.remoteip.internal-proxies) để biết giá trị mặc định của nó. Bạn có thể tùy chỉnh cấu hình của valve bằng cách thêm một mục vào `application.properties`, như trong ví dụ sau:

**Properties**

```properties
server.tomcat.remoteip.internal-proxies=192\\.168\\.\\d{1,3}\\.\\d{1,3}
```

**Yaml**

```yaml
server:
  tomcat:
    remoteip:
      internal-proxies: "192\\.168\\.\\d{1,3}\\.\\d{1,3}"
```

> **LƯU Ý (NOTE):** Bạn có thể tin cậy tất cả các proxy bằng cách đặt `internal-proxies` thành rỗng (nhưng đừng làm vậy trong production).

Bạn có thể kiểm soát hoàn toàn cấu hình `RemoteIpValve` của Tomcat bằng cách tắt valve tự động (để làm vậy, đặt `server.forward-headers-strategy=NONE`) và thêm một instance valve mới bằng một bean `WebServerFactoryCustomizer`.

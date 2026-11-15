# 11.6.4. Phát hiện HTTP client cho RestClient và RestTemplate (HTTP Client Detection for RestClient and RestTemplate)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#io.rest-client.clienthttprequestfactory>

Spring Boot sẽ tự động phát hiện HTTP client nào cần dùng với `RestClient` và `RestTemplate`, tùy thuộc vào các thư viện có sẵn trên classpath của ứng dụng. Theo thứ tự ưu tiên, các client sau được hỗ trợ:

1. Apache HttpClient
2. Jetty HttpClient
3. OkHttp (deprecated)
4. Client JDK đơn giản (`HttpURLConnection`)

Nếu có nhiều client trên classpath, client được ưu tiên nhất sẽ được dùng.

# Bài 4: Consuming a SOAP web service

> Category **1. Web** trong [spring-boot-roadmap-complete.md](../spring-boot-roadmap-complete.md) — bài cuối của category Web, đóng vai **client** gọi chính service đã dựng ở [03-producing-soap-web-service.md](03-producing-soap-web-service.md). Nếu Bài 3 là "viết XSD → sinh code server → expose WSDL", Bài 4 đi **ngược chiều**: "lấy WSDL có sẵn → sinh code client → gọi service". Nội dung đối chiếu với guide chính thức `spring.io/guides/gs-consuming-web-service` qua Context7.

---

## 0. Mục tiêu bài học

Sau bài này, bạn cần trả lời được:
- Vì sao code Java ở client (Bài 4) và ở server (Bài 3) đều "sinh ra từ hợp đồng", nhưng công cụ sinh code lại khác nhau (`xjc` vs `wsimport`)?
- `WebServiceGatewaySupport` đóng vai trò gì, tương đương thành phần nào ở REST (`RestTemplate`/`WebClient`)?
- `SoapActionCallback` giải quyết vấn đề gì mà nếu thiếu, request có thể bị server từ chối?
- Vì sao `Jaxb2Marshaller` là bean bắt buộc phải khai báo tay ở client, khác với Bài 3 nơi `SimpleXsdSchema` được Boot tự tạo?

---

## 1. Bức tranh tổng thể: Bài 3 và Bài 4 là 2 nửa của cùng 1 câu chuyện

| | Bài 3 — Producing (server) | Bài 4 — Consuming (client, bài này) |
|---|---|---|
| Nguồn hợp đồng | Tự viết file `.xsd` | Tải file `.wsdl` **do chính server Bài 3 sinh ra** |
| Công cụ sinh code | `jaxb2-maven-plugin` (goal `xjc`) | `jaxws-maven-plugin` (goal `wsimport`) |
| Class sinh ra | `GetCountryRequest`, `GetCountryResponse`, `Country`, `Currency` | Giống hệt tên class, nhưng nằm ở package khác (`...consumingwebservice.wsdl`) |
| Thành phần trung tâm | `@Endpoint` + `MessageDispatcherServlet` | `WebServiceGatewaySupport` + `WebServiceTemplate` |
| Vai trò | Nhận SOAP request, trả SOAP response | Tạo SOAP request, gửi đi, nhận response |

Vì cả 2 phía đều sinh code từ **cùng 1 hợp đồng gốc** (namespace `https://spring.io/guides/gs-producing-web-service`), class `GetCountryRequest`/`GetCountryResponse` ở client và server có cấu trúc field **giống nhau tuyệt đối** — đây chính là lợi ích của contract-first đã nói ở Bài 3: client và server không cần biết code Java của nhau, chỉ cần cùng đọc 1 file XML.

---

## 2. Thêm dependency

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-webservices</artifactId>
    </dependency>
    <dependency>
        <groupId>wsdl4j</groupId>
        <artifactId>wsdl4j</artifactId>
        <optional>true</optional>
    </dependency>
</dependencies>
```

Giống Bài 3 — vẫn cần `spring-boot-starter-webservices` vì client cũng dùng `WebServiceTemplate`/`Jaxb2Marshaller` nằm trong module Spring WS, không phải module riêng cho client.

---

## 3. Lấy WSDL từ service đang chạy

Chạy service ở Bài 3 (`mvn spring-boot:run` trong project `producing-web-service`), sau đó lưu WSDL về máy:

```bash
curl http://localhost:8080/services/countries.wsdl -o src/main/wsdl/countries.wsdl
```

Đặt file vào `src/main/wsdl/countries.wsdl` — đây là **input** để công cụ sinh code ở bước sau đọc vào, tương tự vai trò của file `.xsd` ở Bài 3, chỉ khác là ở đây bạn nhận file **đã được sinh sẵn**, không tự viết tay.

---

## 4. Sinh Java class ở client bằng `wsimport`

Khác Bài 3 (dùng `xjc` để sinh từ XSD), phía client dùng **`wsimport`** — công cụ chuẩn JAX-WS để sinh code từ WSDL (WSDL chứa cả thông tin operation, không chỉ cấu trúc dữ liệu như XSD đơn thuần):

```xml
<plugin>
    <groupId>com.sun.xml.ws</groupId>
    <artifactId>jaxws-maven-plugin</artifactId>
    <version>4.0.5</version>
    <executions>
        <execution>
            <goals>
                <goal>wsimport</goal>
            </goals>
        </execution>
    </executions>
    <configuration>
        <wsdlDirectory>${basedir}/src/main/wsdl</wsdlDirectory>
        <packageName>com.example.consumingwebservice.wsdl</packageName>
    </configuration>
</plugin>
```

Chạy `mvn compile`, plugin đọc `countries.wsdl` và sinh ra các class `GetCountryRequest`, `GetCountryResponse`, `Country`, `Currency` vào package `com.example.consumingwebservice.wsdl` (package này **tự chọn tên**, không cần trùng package ở server — 2 project là 2 codebase độc lập, chỉ cần cùng cấu trúc XML).

> Ghi nhớ: `xjc` sinh từ **XSD** (chỉ có cấu trúc dữ liệu), `wsimport` sinh từ **WSDL** (có cả cấu trúc dữ liệu lẫn định nghĩa operation/binding) — đây là lý do 2 phía dùng 2 plugin khác nhau dù cùng nền JAXB.

---

## 5. Cấu hình `Jaxb2Marshaller` + `CountryClient` bean

```java
package com.example.consumingwebservice;

import org.springframework.boot.webservices.client.WebServiceTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;

@Configuration
public class CountryConfiguration {

    @Bean
    public Jaxb2Marshaller marshaller() {
        Jaxb2Marshaller marshaller = new Jaxb2Marshaller();
        // package này PHẢI khớp packageName đã khai báo ở jaxws-maven-plugin (mục 4)
        marshaller.setContextPath("com.example.consumingwebservice.wsdl");
        return marshaller;
    }

    @Bean
    public CountryClient countryClient(WebServiceTemplateBuilder builder, Jaxb2Marshaller marshaller) {
        builder = builder.setMarshaller(marshaller).setUnmarshaller(marshaller);

        CountryClient client = new CountryClient();
        client.setWebServiceTemplate(builder.build());
        client.setDefaultUri("http://localhost:8080/services");
        return client;
    }
}
```

| Bean | Vai trò | So sánh với Bài 3 |
|---|---|---|
| `Jaxb2Marshaller` | Chuyển đổi object Java ↔ XML (marshal/unmarshal) — dùng chung 1 bean cho **cả** việc gửi request (marshal) **và** đọc response (unmarshal) | Ở Bài 3, việc này do `MessageDispatcherServlet` tự làm ngầm; ở client phải khai báo tay vì không có auto-configuration nào biết trước package chứa class sinh ra |
| `setContextPath(...)` | Khai báo package chứa các class JAXB đã sinh — Marshaller dùng reflection quét annotation `@XmlRootElement` trong package này | Tương đương việc `SimpleXsdSchema` ở Bài 3 biết đọc file `.xsd` — chỉ khác nguồn: ở đây là package Java, không phải file XML |
| `CountryClient` | Bean nghiệp vụ thực hiện gọi SOAP — xem chi tiết mục 6 | Tương đương `CountryEndpoint` ở Bài 3, nhưng chiều ngược lại (gửi thay vì nhận) |

---

## 6. Viết `CountryClient` — `WebServiceGatewaySupport`

```java
package com.example.consumingwebservice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ws.client.core.support.WebServiceGatewaySupport;
import org.springframework.ws.soap.client.core.SoapActionCallback;

import com.example.consumingwebservice.wsdl.GetCountryRequest;
import com.example.consumingwebservice.wsdl.GetCountryResponse;

public class CountryClient extends WebServiceGatewaySupport {

    private static final Logger log = LoggerFactory.getLogger(CountryClient.class);

    public GetCountryResponse getCountry(String country) {
        GetCountryRequest request = new GetCountryRequest();
        request.setName(country);

        log.info("Requesting location for " + country);

        GetCountryResponse response = (GetCountryResponse) getWebServiceTemplate()
                .marshalSendAndReceive("http://localhost:8080/services/countries", request,
                        new SoapActionCallback(
                                "https://spring.io/guides/gs-producing-web-service/GetCountryRequest"));

        return response;
    }
}
```

| Thành phần | Vai trò | Tương đương ở REST |
|---|---|---|
| `WebServiceGatewaySupport` | Class cha cung cấp sẵn field `webServiceTemplate` + getter `getWebServiceTemplate()` — bạn chỉ cần kế thừa và gọi | Tương đương việc tự inject `RestTemplate`/`WebClient` vào 1 service, nhưng đóng gói sẵn hơn |
| `marshalSendAndReceive(uri, request, callback)` | 1 lệnh làm 3 việc: **marshal** object → XML, **gửi** HTTP POST, **unmarshal** response XML → object | Tương đương `restTemplate.postForObject(uri, request, ResponseType.class)`, nhưng phải tự marshal/unmarshal 2 chiều thay vì Jackson tự lo |
| `SoapActionCallback` | Set HTTP header `SOAPAction` — 1 số server SOAP (đặc biệt implementation cũ/enterprise) **dựa vào header này để định tuyến**, không chỉ dựa vào nội dung XML | Không có tương đương ở REST — REST định tuyến hoàn toàn qua URL + method, không cần header phụ |

> Giá trị truyền vào `SoapActionCallback` chính là **giá trị SOAPAction** mà WSDL của Bài 3 khai báo cho operation `getCountryRequest` — nếu bạn đổi `targetNamespace` ở Bài 3 mà quên cập nhật giá trị này ở client, request vẫn có thể bị `MessageDispatcherServlet` từ chối tùy cấu hình routing của server.

---

## 7. Gọi thử từ `main()` — `ApplicationRunner`

```java
package com.example.consumingwebservice;

import java.util.List;

import com.example.consumingwebservice.wsdl.GetCountryResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class ConsumingWebServiceApplication {

    Logger logger = LoggerFactory.getLogger(ConsumingWebServiceApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(ConsumingWebServiceApplication.class, args);
    }

    @Bean
    ApplicationRunner lookup(CountryClient countryClient) {
        return args -> {
            List<String> countryOption = args.getOptionValues("country");
            String country = (countryOption == null || countryOption.isEmpty()) ? "Spain" : countryOption.get(0);
            GetCountryResponse response = countryClient.getCountry(country);
            logger.info("Country [%s] has currency [%s].".formatted(country, response.getCountry().getCurrency()));
        };
    }
}
```

`ApplicationRunner` (tương đương `CommandLineRunner` đã học ở Bài 1, nhưng nhận `ApplicationArguments` đã được parse sẵn thành option/non-option thay vì `String[]` thô) chạy ngay sau khi context khởi tạo xong — đây là cách guide chính thức demo việc gọi SOAP client mà không cần dựng thêm 1 Controller/endpoint riêng.

Chạy thử (**phải có service Bài 3 đang chạy ở port 8080 trước**):
```bash
mvn spring-boot:run -Dspring-boot.run.arguments=--country=Spain
```

---

## 8. Test client mà không cần chạy service thật — `MockWebServiceServer`

Tương tự cách Bài 3 gợi ý dùng `MockWebServiceClient` để test server mà không cần gửi request qua network thật, phía client có `MockWebServiceServer` (nằm trong package `org.springframework.ws.test.client`) để giả lập server:

```java
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.ws.test.client.MockWebServiceServer;
import org.springframework.xml.transform.StringSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.ws.test.client.RequestMatchers.payload;
import static org.springframework.ws.test.client.ResponseCreators.withPayload;

@SpringBootTest
class CountryClientTests {

    @Autowired
    private CountryClient countryClient;

    private MockWebServiceServer mockServer;

    @BeforeEach
    void createServer() {
        mockServer = MockWebServiceServer.createServer(countryClient);
    }

    @Test
    void getCountryShouldReturnSpainCapital() throws Exception {
        StringSource requestPayload = new StringSource(
            "<getCountryRequest xmlns='https://spring.io/guides/gs-producing-web-service'>" +
            "<name>Spain</name></getCountryRequest>");
        StringSource responsePayload = new StringSource(
            "<getCountryResponse xmlns='https://spring.io/guides/gs-producing-web-service'>" +
            "<country><name>Spain</name><population>46704314</population>" +
            "<capital>Madrid</capital><currency>EUR</currency></country></getCountryResponse>");

        mockServer.expect(payload(requestPayload)).andRespond(withPayload(responsePayload));

        var response = countryClient.getCountry("Spain");

        assertThat(response.getCountry().getCapital()).isEqualTo("Madrid");
        mockServer.verify();
    }
}
```

`MockWebServiceServer.createServer(countryClient)` "chèn" 1 lớp giả lập ngay dưới `WebServiceTemplate` của `countryClient` — request thật không hề đi ra network, nhờ vậy test chạy nhanh và không phụ thuộc service Bài 3 phải đang chạy. `mockServer.verify()` xác nhận đúng số lượng request mong đợi đã được gửi — nếu code gọi thiếu hoặc gọi thừa 1 lần, test sẽ fail.

---

## 9. Tổng kết luồng end-to-end (nối liền Bài 3 + Bài 4)

```
[Bài 3] mvn spring-boot:run → service chạy tại :8080, expose /services/countries.wsdl
        │
        ▼
[Bài 4] curl .../countries.wsdl -o src/main/wsdl/countries.wsdl   (lấy hợp đồng)
        │
        ├─ jaxws-maven-plugin (wsimport) sinh GetCountryRequest/GetCountryResponse/Country
        │
        ├─ Jaxb2Marshaller (contextPath = package vừa sinh) + WebServiceTemplateBuilder
        │       → build thành CountryClient bean
        │
        ├─ countryClient.getCountry("Spain")
        │       → marshal GetCountryRequest thành XML
        │       → POST tới http://localhost:8080/services/countries + header SOAPAction
        │       → [Bài 3] MessageDispatcherServlet route tới CountryEndpoint.getCountry()
        │       → nhận XML response, unmarshal thành GetCountryResponse
        │
        └─ In ra: "Country [Spain] has currency [EUR]."
```

---

## Câu hỏi tự kiểm tra
1. Vì sao phía client dùng `wsimport` (đọc WSDL) chứ không dùng `xjc` (đọc XSD) như phía server ở Bài 3?
2. `Jaxb2Marshaller.setContextPath(...)` phải khớp với cấu hình nào ở `jaxws-maven-plugin`? Điều gì xảy ra nếu 2 giá trị này lệch nhau?
3. `marshalSendAndReceive()` thực hiện gộp bao nhiêu bước? Kể tên.
4. `SoapActionCallback` dùng để làm gì? Nếu bỏ nó đi, request có chắc bị server từ chối trong mọi trường hợp không?
5. `WebServiceGatewaySupport` cung cấp sẵn thành phần nào cho class kế thừa nó?
6. `MockWebServiceServer.createServer(countryClient)` giúp test nhanh hơn nhờ tránh được điều gì so với chạy test tích hợp thật với service Bài 3?

## Bài tập luyện tập
- Chạy đủ cả 2 project (Bài 3 làm server, Bài 4 làm client), gọi `countryClient.getCountry("Spain")` và xác nhận log in đúng `EUR`.
- Thêm quốc gia mới ở `CountryRepository` (Bài 3, ví dụ "Poland"/`PLN`), sinh lại WSDL (không cần vì cấu trúc operation không đổi, chỉ dữ liệu đổi), gọi `getCountry("Poland")` từ client và xác nhận nhận đúng `PLN`.
- Cố tình sửa `setDefaultUri` ở `CountryConfiguration` sang port sai (VD `:9999`), chạy `ApplicationRunner` và quan sát lỗi kết nối — giải thích thông báo lỗi nhận được.
- Viết thêm 1 test bằng `MockWebServiceServer` cho trường hợp gọi quốc gia **không tồn tại** trong `CountryRepository` — server Bài 3 hiện trả về `country` rỗng (`null`), thử expect payload response tương ứng và xác nhận `response.getCountry()` là `null` ở phía client.
- Đối chiếu toàn bộ 4 bài trong category Web: viết lại (bằng lời, không cần code) sơ đồ 4 bài liên kết với nhau ra sao — từ "chạy 1 app tối thiểu" (Bài 1) đến "gọi được 1 SOAP service end-to-end" (Bài 4).

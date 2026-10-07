# Bài 3: Producing a SOAP web service

> Category **1. Web** trong [spring-boot-roadmap-complete.md](../spring-boot-roadmap-complete.md) — sau khi Bài 2 đã render HTML bằng Spring MVC (kiến trúc **code-first**: viết Controller trước, response tự sinh ra), bài này học kiến trúc ngược lại: **contract-first** — viết hợp đồng XML (XSD) trước, code Java được sinh ra từ hợp đồng đó. Đây là nền tảng bắt buộc cho Bài 4 (Consuming a SOAP web service), vì client ở bài 4 sẽ dùng chính WSDL do service này sinh ra. Nội dung đối chiếu với Spring Boot Reference 3.5 và guide chính thức `gs-producing-web-service` qua Context7 (`io/webservices` — Web Services auto-configuration; `spring.io/guides/gs-producing-web-service`).

---

## 0. Mục tiêu bài học

Sau bài này, bạn cần trả lời được:
- SOAP khác REST ở điểm cốt lõi nào, và vì sao SOAP thường đi kèm phong cách **contract-first** (XSD trước, code sau)?
- `@Endpoint` + `@PayloadRoot` đóng vai trò tương đương `@RestController` + `@GetMapping` ở REST, nhưng định tuyến (routing) dựa trên cái gì?
- WSDL được Spring Boot sinh ra tự động từ đâu, cần khai báo những gì?
- `MessageDispatcherServlet` khác `DispatcherServlet` (đã học ở Bài 2) ở điểm nào, và ai đăng ký nó?

---

## 1. SOAP là gì, và vì sao đi theo hướng Contract-First

| | REST (`@RestController`, Bài REST APIs) | SOAP (`@Endpoint`, bài này) |
|---|---|---|
| Định dạng message | Thường là JSON, linh hoạt | Bắt buộc **XML theo chuẩn SOAP envelope** |
| Hợp đồng (contract) | Thường ngầm định qua code — code-first | Tường minh bằng **WSDL** (mô tả operation) + **XSD** (mô tả cấu trúc dữ liệu) |
| Cách phổ biến để xây | Viết Controller trước, doc (OpenAPI) sinh sau | Viết XSD trước → sinh Java class từ XSD → viết Endpoint dùng class đó |
| Định tuyến request | Theo URL + HTTP method (`@GetMapping("/greeting")`) | Theo **namespace + tên phần tử XML** trong SOAP body (`@PayloadRoot`) |

Lý do SOAP thường dùng **contract-first**: hợp đồng XSD là nguồn chân lý duy nhất (single source of truth), độc lập ngôn ngữ — server viết bằng Java, client có thể sinh code bằng .NET/C#/Python đều dựa trên cùng 1 file XSD, không phụ thuộc cách server implement. Đây khác hẳn REST ở Bài 2/REST APIs, nơi bạn viết Java trước rồi mới mô tả API.

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

    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-webservices-test</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

> Lưu ý tên artifact: `spring-boot-starter-webservices` (viết liền), **không phải** `web-services` — lỗi chính tả này là nguyên nhân phổ biến khiến `WebServicesAutoConfiguration` không kích hoạt. `wsdl4j` là thư viện runtime bắt buộc để Spring WS sinh WSDL — thiếu nó, app sẽ lỗi khi khởi động phần expose WSDL.

Chỉ cần thêm starter, `WebServicesAutoConfiguration` sẽ tự đăng ký `MessageDispatcherServlet` — cùng cơ chế `@ConditionalOnClass` đã học ở Bài 1 (spring-boot-fundamentals), tương tự cách `ThymeleafAutoConfiguration` tự kích hoạt ở Bài 2 khi có starter Thymeleaf.

---

## 3. Định nghĩa domain bằng XML Schema (XSD)

Đây là bước **khác biệt lớn nhất** so với Bài 2: thay vì viết class Java `Greeting` rồi để Spring tự serialize, bạn viết hợp đồng XML trước.

`src/main/resources/META-INF/schemas/countriesWs.xsd`:
```xml
<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:tns="https://spring.io/guides/gs-producing-web-service"
           targetNamespace="https://spring.io/guides/gs-producing-web-service" elementFormDefault="qualified">

    <xs:element name="getCountryRequest">
        <xs:complexType>
            <xs:sequence>
                <xs:element name="name" type="xs:string"/>
            </xs:sequence>
        </xs:complexType>
    </xs:element>

    <xs:element name="getCountryResponse">
        <xs:complexType>
            <xs:sequence>
                <xs:element name="country" type="tns:country"/>
            </xs:sequence>
        </xs:complexType>
    </xs:element>

    <xs:complexType name="country">
        <xs:sequence>
            <xs:element name="name" type="xs:string"/>
            <xs:element name="population" type="xs:int"/>
            <xs:element name="capital" type="xs:string"/>
            <xs:element name="currency" type="tns:currency"/>
        </xs:sequence>
    </xs:complexType>

    <xs:simpleType name="currency">
        <xs:restriction base="xs:string">
            <xs:enumeration value="GBP"/>
            <xs:enumeration value="EUR"/>
            <xs:enumeration value="PLN"/>
        </xs:restriction>
    </xs:simpleType>
</xs:schema>
```

| Khái niệm XSD | Ý nghĩa |
|---|---|
| `targetNamespace` | Định danh duy nhất cho hợp đồng này — chính là giá trị sẽ dùng trong `@PayloadRoot(namespace = ...)` ở Endpoint bên dưới |
| `<xs:element name="getCountryRequest">` | Định nghĩa **request message** — SOAP body gửi lên phải khớp cấu trúc này |
| `<xs:element name="getCountryResponse">` | Định nghĩa **response message** |
| `<xs:complexType name="country">` | Kiểu dữ liệu tái sử dụng (tương đương 1 class Java thông thường) |

---

## 4. Sinh Java class từ XSD bằng JAXB

Không viết tay class `GetCountryRequest`/`GetCountryResponse`/`Country` — để Maven **tự sinh** từ XSD ở bước build, đảm bảo code Java luôn khớp 100% với hợp đồng:

```xml
<plugin>
    <groupId>org.codehaus.mojo</groupId>
    <artifactId>jaxb2-maven-plugin</artifactId>
    <version>4.0.0</version>
    <executions>
        <execution>
            <id>xjc</id>
            <goals>
                <goal>xjc</goal>
            </goals>
        </execution>
    </executions>
    <configuration>
        <sources>
            <source>${project.basedir}/src/main/resources/META-INF/schemas/countriesWs.xsd</source>
        </sources>
    </configuration>
</plugin>
```

Chạy `mvn compile` (hoặc `mvn generate-sources`), plugin gọi **xjc** (XML-to-Java Compiler, công cụ chuẩn của JAXB) và sinh ra các class `GetCountryRequest`, `GetCountryResponse`, `Country`, `Currency` (enum) vào `target/generated-sources/jaxb` — mỗi `<xs:element>`/`<xs:complexType>` trong XSD tương ứng 1 class Java có annotation JAXB (`@XmlRootElement`, `@XmlType`...) để (de)serialize XML tự động.

> So với Bài 2 — nơi bạn tự viết class `Greeting` bằng tay rồi Spring bind form vào nó — ở đây thứ tự đảo ngược: XSD là nguồn, Java class là **sản phẩm sinh ra**, không tự sửa tay các file trong `generated-sources`.

---

## 5. Viết Endpoint xử lý request

`@Endpoint` là annotation tương đương `@Controller`/`@RestController` ở Bài 2, nhưng định tuyến theo **namespace + tên phần tử XML** thay vì URL:

```java
package com.example.producingwebservice;

import io.spring.guides.gs_producing_web_service.GetCountryRequest;
import io.spring.guides.gs_producing_web_service.GetCountryResponse;

import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;

@Endpoint
public class CountryEndpoint {

    private static final String NAMESPACE_URI = "https://spring.io/guides/gs-producing-web-service";

    private final CountryRepository countryRepository;

    public CountryEndpoint(CountryRepository countryRepository) {
        this.countryRepository = countryRepository;
    }

    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "getCountryRequest")
    @ResponsePayload
    public GetCountryResponse getCountry(@RequestPayload GetCountryRequest request) {
        GetCountryResponse response = new GetCountryResponse();
        response.setCountry(countryRepository.findCountry(request.getName()));

        return response;
    }
}
```

| Annotation | Vai trò | So sánh với Bài 2/REST |
|---|---|---|
| `@Endpoint` | Đánh dấu bean này xử lý SOAP message — `MessageDispatcherServlet` quét các bean này khi khởi động | Tương đương `@Controller`/`@RestController` |
| `@PayloadRoot(namespace, localPart)` | Định tuyến: message có root element tên `getCountryRequest` trong namespace trên sẽ được method này xử lý | Tương đương `@GetMapping("/greeting")`, nhưng khóa định tuyến là **cặp namespace+tên phần tử**, không phải URL |
| `@RequestPayload` | Bind SOAP body (đã deserialize thành object JAXB) vào tham số | Tương đương `@RequestBody` ở REST |
| `@ResponsePayload` | Giá trị trả về sẽ được serialize thành SOAP body của response | Tương đương `@ResponseBody` ở REST — khác hẳn Bài 2 nơi `return` là **tên view**, không phải dữ liệu |

---

## 6. Sinh WSDL tự động — `WebServiceConfig`

WSDL (Web Services Description Language) là "OpenAPI của SOAP" — mô tả operation nào tồn tại, request/response ra sao, để client (Bài 4) sinh code gọi service mà không cần đọc code server.

```java
package com.example.producingwebservice;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.ws.wsdl.wsdl11.DefaultWsdl11Definition;
import org.springframework.xml.xsd.SimpleXsdSchema;

@Configuration(proxyBeanMethods = false)
public class WebServiceConfig {

    @Bean
    public DefaultWsdl11Definition countries(SimpleXsdSchema countriesWs) {
        DefaultWsdl11Definition wsdl11Definition = new DefaultWsdl11Definition();
        wsdl11Definition.setPortTypeName("CountriesPort");
        wsdl11Definition.setLocationUri("/services");
        wsdl11Definition.setTargetNamespace("https://spring.io/guides/gs-producing-web-service");
        wsdl11Definition.setSchema(countriesWs);
        return wsdl11Definition;
    }
}
```

Tham số `countriesWs` (kiểu `SimpleXsdSchema`) **không cần tự khai báo bean** — Spring Boot tự tạo nó nếu bạn khai báo vị trí file XSD trong `application.properties`:

```properties
spring.webservices.wsdl-locations=classpath:META-INF/schemas/
```

| Cấu hình | Ý nghĩa |
|---|---|
| `spring.webservices.wsdl-locations` | Nơi Boot quét file `.xsd`/`.wsdl` để tự tạo bean `SimpleXsdSchema`/`SimpleWsdl11Definition` — tên bean lấy theo tên file (không đuôi), ở đây là `countriesWs` |
| `DefaultWsdl11Definition` bean tên `countries` | Bean này sẽ được expose tại URL `/services/countries.wsdl` (tên bean + hậu tố `.wsdl`, ghép sau prefix servlet) |
| `setLocationUri("/services")` | Địa chỉ SOAP endpoint thực sự nhận request — client gửi POST XML tới đây |

---

## 7. `MessageDispatcherServlet` — Front Controller cho SOAP

Cùng pattern Front Controller đã học ở Bài 2 (`DispatcherServlet`), nhưng dành riêng cho SOAP:

```
Client POST XML (SOAP envelope) → /services
        │
        ▼
  MessageDispatcherServlet (tự đăng ký bởi WebServicesAutoConfiguration, servlet mapping mặc định /services/*)
        │
        ├─ 1. Đọc SOAP body, xác định namespace + tên phần tử root
        │
        ├─ 2. Quét các bean @Endpoint → tìm method khớp @PayloadRoot(namespace, localPart)
        │
        ├─ 3. Deserialize body thành object JAXB (@RequestPayload) → gọi method
        │
        └─ 4. Serialize object trả về (@ResponsePayload) → ghi SOAP envelope response
```

| | `DispatcherServlet` (Bài 2) | `MessageDispatcherServlet` (bài này) |
|---|---|---|
| Ai đăng ký tự động | `WebMvcAutoConfiguration` | `WebServicesAutoConfiguration` |
| Định tuyến dựa trên | URL path + HTTP method | Namespace + tên phần tử XML trong body |
| Đơn vị xử lý | `@Controller`/`@RestController` | `@Endpoint` |

---

## 8. `CountryRepository` — nguồn dữ liệu

```java
package com.example.producingwebservice;

import java.util.HashMap;
import java.util.Map;

import io.spring.guides.gs_producing_web_service.Country;
import io.spring.guides.gs_producing_web_service.Currency;

import org.springframework.stereotype.Component;

@Component
public class CountryRepository {

    private static final Map<String, Country> countries = new HashMap<>();

    @PostConstruct
    public void initData() {
        Country spain = new Country();
        spain.setName("Spain");
        spain.setCapital("Madrid");
        spain.setCurrency(Currency.EUR);
        spain.setPopulation(46704314);

        countries.put(spain.getName(), spain);
    }

    public Country findCountry(String name) {
        return countries.get(name);
    }
}
```

Class `Country`/`Currency` ở đây chính là các class **sinh ra từ XSD ở mục 4** — không viết tay, chỉ import từ package do `jaxb2-maven-plugin` tạo ra.

---

## 9. Kiểm tra WSDL và gọi thử service

Chạy app rồi mở `http://localhost:8080/services/countries.wsdl` — Spring tự sinh WSDL hoàn chỉnh từ `DefaultWsdl11Definition` + `SimpleXsdSchema` đã khai báo, không cần viết tay.

Gọi thử bằng `curl` (vì SOAP dùng HTTP POST + XML body, không có cú pháp GET tiện lợi như REST):

```bash
curl --header "content-type: text/xml" -d @request.xml http://localhost:8080/services
```

`request.xml`:
```xml
<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
                   xmlns:gs="https://spring.io/guides/gs-producing-web-service">
   <soapenv:Header/>
   <soapenv:Body>
      <gs:getCountryRequest>
         <gs:name>Spain</gs:name>
      </gs:getCountryRequest>
   </soapenv:Body>
</soapenv:Envelope>
```

So với Bài 2 (`curl http://localhost:8080/greeting?name=Son` — 1 dòng, GET), việc gọi SOAP luôn nặng hơn: phải bọc đúng SOAP envelope, đúng namespace, dùng POST — đây là lý do các bài sau (Bài 4, REST APIs) thường được ưa chuộng hơn cho service mới, còn SOAP chủ yếu gặp khi tích hợp hệ thống doanh nghiệp cũ (legacy enterprise) đã có sẵn.

---

## Câu hỏi tự kiểm tra
1. Vì sao SOAP thường đi theo hướng contract-first (XSD trước, code sau) trong khi REST ở Bài 2/REST APIs thường code-first?
2. `@PayloadRoot` định tuyến request dựa trên cái gì? So sánh với cách `@GetMapping` định tuyến ở Bài 2.
3. Nếu đổi `targetNamespace` trong file XSD nhưng quên cập nhật `NAMESPACE_URI` trong `CountryEndpoint`, điều gì sẽ xảy ra khi client gọi service?
4. `SimpleXsdSchema` bean tên `countriesWs` trong `WebServiceConfig` đến từ đâu — bạn có viết `@Bean` cho nó không? Giải thích cơ chế.
5. `MessageDispatcherServlet` và `DispatcherServlet` giống nhau ở pattern nào, khác nhau ở điểm mấu chốt nào?
6. WSDL được expose ở URL nào, dựa trên tên bean nào để suy ra tên file `.wsdl`?

## Bài tập luyện tập
- Thêm 1 quốc gia mới (VD "Poland", currency `PLN`) vào `CountryRepository`, gọi lại `request.xml` với `<gs:name>Poland</gs:name>` và xác nhận response đúng.
- Thêm 1 operation mới `getAllCountries` (không cần tham số) vào XSD, sinh lại code bằng `mvn compile`, viết thêm 1 method trong `CountryEndpoint` với `@PayloadRoot` tương ứng trả về danh sách toàn bộ quốc gia.
- Cố tình gửi `request.xml` với namespace sai (`xmlns:gs="wrong-namespace"`), quan sát lỗi SOAP Fault trả về và giải thích tại sao `MessageDispatcherServlet` từ chối route request này.
- Viết test dùng `spring-boot-starter-webservices-test` (`MockWebServiceClient`) gửi request XML và assert response XML khớp payload mong đợi — so sánh cách viết test này với `MockMvc` đã dùng ở Bài 2.
- Đọc trước Bài 4 (Consuming a SOAP web service): xác định WSDL sinh ra ở bài này sẽ được dùng thế nào để sinh code client tự động.

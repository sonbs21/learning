# Bài 2: Serving Web Content with Spring MVC

> Category **1. Web** trong [spring-boot-roadmap-complete.md](../spring-boot-roadmap-complete.md) — sau khi Bài 1 đã dựng được 1 app chạy được, bài này học cách **render trang HTML động** (thay vì trả JSON như REST API) bằng Spring MVC + Thymeleaf. Nội dung đối chiếu với Spring Boot Reference 3.5 qua Context7 (`web/servlet` — Static Content, Welcome Page; `how-to/spring-mvc` — Template Engine Resolvers; `ThymeleafAutoConfiguration`).

---

## 0. Mục tiêu bài học

Sau bài này, bạn cần trả lời được:
- `@Controller` khác `@RestController` ở điểm mấu chốt nào, và khi nào dùng cái nào?
- `DispatcherServlet` đóng vai trò gì trong việc xử lý 1 request HTML?
- Spring Boot tìm file template (`.html`) và file tĩnh (CSS/JS) ở đâu, theo quy tắc nào?
- Cách truyền dữ liệu từ Controller sang view bằng `Model`, và cách hiển thị nó bằng cú pháp Thymeleaf.

---

## 1. `@Controller` vs `@RestController`

Đây là điểm khác biệt cốt lõi giữa bài 2 (render HTML) và REST API (category 2 trong roadmap):

| | `@Controller` | `@RestController` |
|---|---|---|
| Giá trị method trả về | **Tên logic của view** (String) — Spring sẽ tìm file template tương ứng để render | **Dữ liệu trực tiếp** (object → serialize JSON/XML) ghi thẳng vào response body |
| Tương đương annotation | `@Controller` (không có `@ResponseBody`) | `@Controller` + `@ResponseBody` gộp lại |
| Dùng cho | Trang web hiển thị cho người dùng (server-side rendering) | API cho client (SPA, mobile app, service khác) gọi |

```java
@Controller
public class GreetingController {

    @GetMapping("/greeting")
    public String greeting(@RequestParam(defaultValue = "World") String name, Model model) {
        model.addAttribute("name", name);
        return "greeting"; // đây là TÊN VIEW, không phải nội dung trả về
    }
}
```
Nếu vô tình gắn `@RestController` (hoặc thêm `@ResponseBody`) vào method trên, Spring sẽ trả **chuỗi `"greeting"` y nguyên** làm response body thay vì render file `greeting.html` — lỗi rất hay gặp khi mới học.

---

## 2. `DispatcherServlet` — Front Controller Pattern

Spring MVC hoạt động theo mô hình **Front Controller**: mọi request HTTP đều đi qua **1 servlet trung tâm duy nhất** trước khi được định tuyến tới đúng nơi xử lý.

```
Request "GET /greeting?name=Son"
        │
        ▼
  DispatcherServlet (front controller, tự cấu hình bởi WebMvcAutoConfiguration)
        │
        ├─ 1. Tra HandlerMapping → tìm ra GreetingController.greeting() khớp URL "/greeting"
        │
        ├─ 2. Gọi method, method trả về String "greeting" + Model chứa {name: "Son"}
        │
        ├─ 3. Tra ViewResolver → map tên "greeting" thành file thật
        │       (VD Thymeleaf: classpath:/templates/greeting.html)
        │
        └─ 4. Render template với dữ liệu trong Model → trả HTML hoàn chỉnh về client
```

Với Spring Boot, bạn **không cần tự khai báo** `DispatcherServlet` trong `web.xml` như Spring thuần — `WebMvcAutoConfiguration` tự đăng ký nó ngay khi có `spring-boot-starter-web` trên classpath (đã học cơ chế auto-configuration ở [02-spring-boot-fundamentals.md](../02-spring-boot-fundamentals.md)).

---

## 3. Thêm dependency Thymeleaf

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-thymeleaf</artifactId>
</dependency>
```

Chỉ cần thêm starter này, `ThymeleafAutoConfiguration` sẽ tự kích hoạt (điều kiện: class Thymeleaf có mặt trên classpath — đúng cơ chế `@ConditionalOnClass` đã học ở bài Fundamentals) và tự đăng ký 1 `ViewResolver` chuyên cho Thymeleaf, với **prefix/suffix mặc định**:

| Cấu hình | Giá trị mặc định |
|---|---|
| Prefix (thư mục chứa template) | `classpath:/templates/` |
| Suffix (đuôi file) | `.html` |

Nghĩa là khi Controller `return "greeting"`, ViewResolver sẽ tự ghép thành đường dẫn `classpath:/templates/greeting.html`. Muốn đổi mặc định này, khai báo `spring.thymeleaf.prefix` / `spring.thymeleaf.suffix` trong `application.properties`, hoặc tự cung cấp 1 bean `ViewResolver` khác — khi đó bean tự khai báo sẽ override bean auto-config (đúng cơ chế `@ConditionalOnMissingBean`).

---

## 4. Viết Controller + truyền dữ liệu qua `Model`

```java
package com.example.myproject.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class GreetingController {

    @GetMapping("/greeting")
    public String greeting(@RequestParam(name = "name", required = false, defaultValue = "World") String name,
                            Model model) {
        model.addAttribute("name", name);
        return "greeting";
    }
}
```

`Model` ở đây là 1 dạng `Map<String, Object>` mà Spring truyền tự động sang tầng view — mọi `addAttribute(key, value)` sẽ có thể truy cập trong template bằng `${key}`.

---

## 5. Viết template Thymeleaf

`src/main/resources/templates/greeting.html`:
```html
<!DOCTYPE HTML>
<html xmlns:th="https://www.thymeleaf.org">
<head>
    <title>Getting Started: Serving Web Content</title>
    <link rel="stylesheet" th:href="@{/css/style.css}" />
</head>
<body>
    <p th:text="'Hello, ' + ${name} + '!'" />
</body>
</html>
```

| Thuộc tính Thymeleaf | Ý nghĩa |
|---|---|
| `xmlns:th="https://www.thymeleaf.org"` | Khai báo namespace để IDE/parser hiểu các thuộc tính `th:*` — không ảnh hưởng khi trình duyệt hiển thị |
| `th:text="'Hello, ' + ${name} + '!'"` | Ghi đè nội dung text bên trong thẻ bằng giá trị tính toán — `${name}` lấy từ `Model` mà Controller đã `addAttribute` |
| `th:href="@{/css/style.css}"` | Cú pháp `@{...}` sinh URL tương đối theo context path của app — nên dùng thay vì hardcode `href="/css/style.css"` để tránh lỗi khi app deploy dưới 1 context path khác `/` |

Điểm hay của Thymeleaf so với JSP: file `.html` này **mở trực tiếp bằng trình duyệt vẫn xem được** (hiển thị "Hello, !" tĩnh) vì cú pháp `th:*` chỉ là thuộc tính HTML hợp lệ, không phải tag lạ — designer có thể chỉnh giao diện mà không cần chạy server.

---

## 6. Static resources (CSS/JS/ảnh)

Spring Boot tự phục vụ file tĩnh từ 4 vị trí mặc định trên classpath (không cần cấu hình gì thêm):

```
src/main/resources/static/
src/main/resources/public/
src/main/resources/resources/
src/main/resources/META-INF/resources/
```

Ví dụ file CSS ở `src/main/resources/static/css/style.css` sẽ tự truy cập được qua URL `http://localhost:8080/css/style.css` — đây chính là URL mà `th:href="@{/css/style.css}"` ở template phía trên trỏ tới.

> Lưu ý quan trọng: nếu bạn tự cấu hình `spring.web.resources.static-locations`, giá trị này sẽ **thay thế hoàn toàn** (không cộng dồn) 4 vị trí mặc định ở trên — khai báo thiếu vị trí nào thì vị trí đó ngừng được phục vụ.

### Welcome page
Nếu tồn tại file `index.html` trong 1 trong các vị trí tĩnh trên (hoặc 1 template tên `index`), Spring Boot tự dùng nó làm trang mặc định khi truy cập `/`. `WelcomePageHandlerMapping` có độ ưu tiên **thấp nhất** — nếu bạn tự định nghĩa `@GetMapping("/")` trong 1 Controller, route tự viết sẽ luôn thắng welcome page.

---

## 7. Xử lý Form — GET hiển thị, POST submit

Mở rộng ví dụ trên thành form nhập tên rồi hiển thị lời chào (nền tảng cho bài "Handling Form Submission" ở category IO):

```java
@Controller
public class GreetingController {

    @GetMapping("/greeting")
    public String greetingForm(Model model) {
        model.addAttribute("greeting", new Greeting());
        return "greeting";
    }

    @PostMapping("/greeting")
    public String greetingSubmit(@ModelAttribute Greeting greeting) {
        return "result";
    }
}
```
```java
public class Greeting {
    private String name;
    // getter/setter
}
```
```html
<!-- greeting.html -->
<form th:action="@{/greeting}" th:object="${greeting}" method="post">
    <p>Name: <input type="text" th:field="*{name}" /></p>
    <p><input type="submit" value="Submit" /></p>
</form>
```
```html
<!-- result.html -->
<p th:text="'Hello, ' + ${greeting.name} + '!'" />
```

| Annotation | Vai trò |
|---|---|
| `@ModelAttribute` | Bind toàn bộ field trong form (theo tên `name` khớp property) vào object `Greeting`, tự thêm vào `Model` với key mặc định là tên class viết thường (`greeting`) |
| `th:object` + `th:field` | Cú pháp Thymeleaf để bind 2 chiều giữa field HTML và property Java — tương đương `<input name="name" value="...">` viết tay nhưng gọn hơn |

> `GET /greeting` và `POST /greeting` **cùng URL** nhưng khác HTTP method — đây là pattern chuẩn: GET để hiển thị form rỗng, POST để nhận dữ liệu submit và trả về trang kết quả khác.

---

## 8. Test Controller trả về HTML

Khác với REST API (kiểm tra JSON body), test ở đây kiểm tra **view name** và **nội dung render**:

```java
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class GreetingControllerTests {

    @Test
    void greetingWithoutNameShouldReturnDefaultView(@Autowired MockMvc mvc) throws Exception {
        mvc.perform(get("/greeting"))
           .andExpect(status().isOk())
           .andExpect(view().name("greeting"))
           .andExpect(model().attribute("name", "World"))
           .andExpect(content().string(org.hamcrest.Matchers.containsString("Hello, World!")));
    }

    @Test
    void greetingWithNameParamShouldUseIt(@Autowired MockMvc mvc) throws Exception {
        mvc.perform(get("/greeting").param("name", "Son"))
           .andExpect(model().attribute("name", "Son"));
    }
}
```
`view()` và `model()` là 2 matcher đặc thù cho Spring MVC (khác REST test chỉ cần `content()`/`jsonPath()`) — cho phép assert đúng cả **tên view được chọn** lẫn **dữ liệu đưa vào Model**, độc lập với việc HTML cuối cùng render ra sao.

---

## 9. Tổng kết luồng end-to-end

```
Trình duyệt: GET /greeting?name=Son
        │
        ▼
DispatcherServlet nhận request
        │
        ├─ HandlerMapping → GreetingController.greeting(name="Son", model)
        │
        ├─ Controller: model.addAttribute("name", "Son"); return "greeting";
        │
        ├─ ViewResolver (Thymeleaf) → classpath:/templates/greeting.html
        │
        └─ Render template với ${name}="Son" → trả HTML "Hello, Son!" về trình duyệt

Song song: trình duyệt GET /css/style.css
        │
        └─ Spring Boot phục vụ trực tiếp từ src/main/resources/static/css/style.css
           (KHÔNG đi qua Controller/ViewResolver — đây là static resource handler riêng)
```

---

## Câu hỏi tự kiểm tra
1. Nếu 1 method trong `@Controller` trả về `"greeting"` nhưng lại bị đánh dấu `@ResponseBody`, response trả về client sẽ là gì? Vì sao?
2. `DispatcherServlet` cần bao nhiêu bước chính để đi từ 1 request HTTP tới 1 trang HTML hoàn chỉnh? Liệt kê theo thứ tự.
3. Prefix/suffix mặc định của `ThymeleafAutoConfiguration` là gì? Nếu Controller `return "user/profile"`, Spring sẽ tìm file nào?
4. File CSS đặt ở `src/main/resources/static/css/app.css` có cần khai báo route riêng trong Controller không? Vì sao?
5. Nếu vừa có `index.html` trong `static/`, vừa có `@GetMapping("/")` trong 1 Controller, request `GET /` sẽ render cái nào? Vì sao?
6. Trong ví dụ form ở mục 7, tại sao `GET /greeting` và `POST /greeting` có thể dùng chung 1 URL mà không xung đột?

## Bài tập luyện tập
- Tạo Controller `PageController` với `@GetMapping("/about")` trả về view `about`, template hiển thị tên bạn và 1 đoạn giới thiệu ngắn — style bằng 1 file CSS đặt trong `static/css/`.
- Cố tình đổi `@Controller` thành `@RestController` trên `GreetingController` ở mục 4, chạy lại và quan sát response trả về khác gì so với ban đầu — giải thích bằng lời tại sao.
- Cấu hình lại `spring.thymeleaf.prefix=classpath:/views/` trong `application.properties`, di chuyển file `greeting.html` sang `src/main/resources/views/`, xác nhận app vẫn chạy đúng.
- Hoàn thiện ví dụ form ở mục 7 thành ứng dụng chạy được đầy đủ (`Greeting`, `GreetingController` với cả GET/POST, `greeting.html`, `result.html`) — thử submit với tên rỗng và quan sát hành vi.
- Viết test bằng `MockMvc` cho route `POST /greeting` ở trên, assert `view().name("result")` và nội dung HTML chứa đúng tên đã submit.

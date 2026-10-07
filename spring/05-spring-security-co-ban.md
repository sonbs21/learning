# Tuần 5: Spring Security cơ bản

> Tài liệu tổng hợp kiến thức mục 5 trong lộ trình [spring-boot-roadmap.md](spring-boot-roadmap.md). Nội dung đã được đối chiếu với Spring Security Reference (6.5) qua Context7 để đảm bảo chính xác về kiến trúc filter chain, cơ chế `hasRole`/`hasAuthority`, và cấu hình CORS/CSRF.

---

## 1. Kiến trúc Filter Chain

### 1.1 `DelegatingFilterProxy` và `FilterChainProxy`
Spring Security không "chèn trực tiếp" vào Servlet container — nó hoạt động qua 2 lớp trung gian:
- **`DelegatingFilterProxy`**: 1 Servlet `Filter` tiêu chuẩn (do container quản lý), nhiệm vụ duy nhất là **ủy quyền** (delegate) request tới 1 Spring bean cùng tên đăng ký trong `ApplicationContext`.
```java
// Pseudocode cơ chế delegate
public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) {
    Filter delegate = getFilterBean("springSecurityFilterChain"); // lấy bean từ Spring container
    delegate.doFilter(request, response);
}
```
- **`FilterChainProxy`**: chính là bean `"springSecurityFilterChain"` ở trên — bản thân nó là 1 `Filter` đặc biệt, chứa danh sách nhiều `SecurityFilterChain`. Với mỗi request, nó chọn ra đúng 1 `SecurityFilterChain` phù hợp (dựa vào `securityMatcher`) rồi chạy toàn bộ chuỗi filter bảo mật bên trong `SecurityFilterChain` đó.

```java
@Bean
public SecurityFilterChain apiFilterChain(HttpSecurity http) throws Exception {
    http
        .securityMatcher("/api/**") // chỉ áp dụng chain này cho request khớp pattern
        .authorizeHttpRequests(auth -> auth.anyRequest().authenticated());
    return http.build();
}
```
Có thể khai báo **nhiều `SecurityFilterChain`** cho các nhóm URL khác nhau (VD 1 chain cho `/api/**` dùng JWT stateless, 1 chain khác cho phần web truyền thống dùng session) — mỗi chain có `@Order` riêng để xác định thứ tự kiểm tra.

### 1.2 Thứ tự filter quan trọng
Bên trong 1 `SecurityFilterChain`, các filter chạy tuần tự theo thứ tự cố định (không phải thứ tự khai báo tùy ý) — ví dụ `UsernamePasswordAuthenticationFilter` (xử lý form login) chạy **trước** `AuthorizationFilter`/`FilterSecurityInterceptor` (kiểm tra quyền truy cập) — logic hợp lý vì phải **xác thực xong** (biết user là ai) thì mới có thể **phân quyền** (user đó được làm gì) được.

Muốn thêm filter tùy chỉnh (VD filter đọc JWT từ header), dùng `addFilterBefore`/`addFilterAfter` để chèn đúng vị trí tương đối so với 1 filter có sẵn:
```java
@Bean
SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
        .addFilterAfter(new TenantFilter(), AnonymousAuthenticationFilter.class);
    return http.build();
}
```

---

## 2. Authentication

### 2.1 `AuthenticationManager` và `AuthenticationProvider`
- **`AuthenticationManager`**: interface trung tâm nhận vào 1 `Authentication` (chưa xác thực) và trả về 1 `Authentication` đã xác thực (hoặc ném exception nếu thất bại).
- **`AuthenticationProvider`**: hiện thực cụ thể logic xác thực cho 1 cơ chế nhất định (username/password, LDAP, OAuth2...). `AuthenticationManager` (thường là `ProviderManager`) ủy quyền cho danh sách các `AuthenticationProvider` đã đăng ký, thử từng cái cho tới khi có 1 provider xác thực thành công.

### 2.2 `DaoAuthenticationProvider` — cơ chế username/password phổ biến nhất
```
DaoAuthenticationProvider
    ├── dùng UserDetailsService.loadUserByUsername(username) → lấy về UserDetails
    └── dùng PasswordEncoder.matches(rawPassword, userDetails.getPassword()) → so khớp mật khẩu
```
Nếu khớp, trả về `UsernamePasswordAuthenticationToken` chứa `UserDetails` làm principal, sau đó được lưu vào `SecurityContextHolder` cho các bước xử lý tiếp theo trong request.

```java
@Service
public class CustomUserDetailsService implements UserDetailsService {
    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) {
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new UsernameNotFoundException("User không tồn tại: " + username));
        return org.springframework.security.core.userdetails.User
            .withUsername(user.getUsername())
            .password(user.getPasswordHash())
            .authorities(user.getRoles().toArray(new String[0]))
            .build();
    }
}
```

### 2.3 `PasswordEncoder`
```java
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(); // khuyến nghị mặc định của Spring Security
}
```
- **`encode(rawPassword)`**: mã hóa mật khẩu gốc thành chuỗi hash để lưu database (không bao giờ lưu plain-text).
- **`matches(rawPassword, encodedPassword)`**: so khớp mật khẩu người dùng nhập với hash đã lưu — **không** giải mã ngược hash để so sánh (băm là 1 chiều).
- **BCrypt** tự sinh salt ngẫu nhiên và nhúng vào trong chuỗi hash kết quả, chống được rainbow table attack; **Argon2** (winner của Password Hashing Competition) mạnh hơn về khả năng chống tấn công phần cứng chuyên dụng (GPU/ASIC) nhờ tốn nhiều bộ nhớ khi tính toán, thường được khuyến nghị cho hệ thống yêu cầu bảo mật cao hơn mức mặc định.

---

## 3. Authorization

### 3.1 Method-level Security
```java
@Configuration
@EnableMethodSecurity // bật @PreAuthorize/@PostAuthorize/@Secured
public class MethodSecurityConfig {}

@Service
public class BankService {

    @PreAuthorize("hasRole('ADMIN')") // kiểm tra TRƯỚC khi method chạy
    public Account readAccount(Long id) { ... }

    @PreAuthorize("hasAuthority('permission:read')")
    @PostAuthorize("returnObject.owner == authentication.name") // kiểm tra SAU khi có kết quả trả về
    public Customer readCustomer(String id) { ... }
}
```
- `@PreAuthorize`: đánh giá điều kiện **trước** khi method thực thi — chặn sớm nếu không đủ quyền, tiết kiệm tài nguyên.
- `@PostAuthorize`: đánh giá **sau** khi có kết quả trả về (`returnObject`) — dùng khi quyền truy cập phụ thuộc vào chính dữ liệu trả về (VD chỉ chủ sở hữu mới xem được record của mình), method vẫn chạy xong rồi mới kiểm tra.
- `@Secured("ROLE_ADMIN")`: cách cũ hơn, chỉ hỗ trợ kiểm tra role đơn giản, không hỗ trợ biểu thức SpEL linh hoạt như `@PreAuthorize`.

### 3.2 Role vs Authority
| | Authority | Role |
|---|---|---|
| Bản chất | Đơn vị quyền hạn nguyên thủy (`GrantedAuthority`), tên tùy ý (VD `"permission:read"`, `"ROLE_ADMIN"`) | Một dạng "authority đặc biệt" theo quy ước, luôn có prefix `ROLE_` |
| Cách kiểm tra | `hasAuthority("ROLE_ADMIN")` — so khớp **chính xác** chuỗi, không tự thêm prefix | `hasRole("ADMIN")` — Spring **tự động thêm prefix `ROLE_`** (hoặc prefix tùy chỉnh qua `GrantedAuthorityDefaults`) trước khi so khớp |
| Khi nào dùng | Khi cần phân quyền chi tiết theo hành động cụ thể (permission-based) | Khi phân nhóm người dùng theo vai trò tổng quát (role-based) |

> Lỗi thường gặp: gọi `hasRole("ROLE_ADMIN")` (tự thêm `ROLE_` lần nữa) trong khi authority lưu trong DB đã là `"ROLE_ADMIN"` → thành `ROLE_ROLE_ADMIN`, không bao giờ khớp. Quy tắc: `hasRole("ADMIN")` (không prefix) tương đương `hasAuthority("ROLE_ADMIN")` (có prefix).

### 3.3 `HttpSecurity` config — Authorization ở tầng request
```java
@Bean
SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/api/public/**").permitAll()       // không cần xác thực
            .requestMatchers("/api/admin/**").hasRole("ADMIN")   // cần role ADMIN
            .requestMatchers(HttpMethod.GET, "/api/orders/**").hasAuthority("order:read")
            .anyRequest().authenticated()                        // mọi request khác phải đăng nhập
        );
    return http.build();
}
```
> Thứ tự khai báo `requestMatchers` quan trọng — quy tắc khớp **đầu tiên** (từ trên xuống) sẽ được áp dụng, nên luôn đặt rule cụ thể trước, rule tổng quát (`anyRequest()`) đặt sau cùng.

---

## 4. Session-based vs Stateless

| | Session-based | Stateless (JWT) |
|---|---|---|
| Nơi lưu trạng thái đăng nhập | **Server** lưu session (trong bộ nhớ hoặc store phân tán như Redis), client chỉ giữ session ID (cookie `JSESSIONID`) | **Không lưu gì ở server** — mọi thông tin xác thực nằm trong token, client tự gửi kèm token mỗi request |
| Scale ngang | Cần **sticky session** hoặc session store dùng chung (Redis) giữa các instance | Dễ scale ngang — bất kỳ instance nào cũng tự verify được token mà không cần tra cứu state chung |
| Phù hợp | Ứng dụng web truyền thống (server-rendered) | REST API, microservices |

```java
@Bean
SecurityFilterChain apiFilterChain(HttpSecurity http) throws Exception {
    http
        .sessionManagement(session -> session
            .sessionCreationPolicy(SessionCreationPolicy.STATELESS) // không tạo/dùng HttpSession
        );
    return http.build();
}
```

---

## 5. CORS & CSRF

### 5.1 CORS — cấu hình đúng cho REST API
```java
@Bean
SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http.cors(cors -> cors.configurationSource(corsConfigurationSource()));
    return http.build();
}

@Bean
UrlBasedCorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(List.of("https://myapp.com")); // liệt kê rõ domain được phép, tránh "*" khi có credentials
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE"));
    configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
    configuration.setAllowCredentials(true);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
}
```
Có thể đăng ký **nhiều `CorsConfigurationSource`** khác nhau cho từng `SecurityFilterChain` (theo `securityMatcher`) nếu các nhóm API có yêu cầu CORS khác nhau.

### 5.2 CSRF — khi nào bật/tắt
CSRF (Cross-Site Request Forgery) **mặc định được bật** trong Spring Security. Nguyên lý tấn công: trang web độc hại lợi dụng session cookie đã đăng nhập sẵn của user (trình duyệt tự động gửi kèm cookie) để giả mạo request tới ứng dụng của bạn.

```java
// Tắt hoàn toàn CSRF — dùng khi API stateless (JWT), không có session/cookie để lợi dụng
http.csrf(csrf -> csrf.disable());

// Hoặc chỉ bỏ qua CSRF cho 1 nhóm endpoint cụ thể, giữ nguyên cho phần còn lại
http.csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"));
```
| Kiểu ứng dụng | CSRF |
|---|---|
| Session-based (server lưu session, dùng cookie) | **Giữ bật** — đây chính là kịch bản CSRF nhắm tới |
| Stateless REST API dùng JWT trong header `Authorization` | **Tắt** — trình duyệt không tự động gửi kèm JWT trong header như cách nó tự gửi cookie, nên không có bề mặt tấn công CSRF theo cách truyền thống |

---

## Câu hỏi tự kiểm tra
1. `DelegatingFilterProxy` và `FilterChainProxy` khác nhau ở vai trò gì? Vì sao Spring Security cần tới 2 lớp trung gian này thay vì đăng ký filter thẳng vào Servlet container?
2. Vì sao `UsernamePasswordAuthenticationFilter` phải chạy **trước** filter kiểm tra authorization? Điều gì xảy ra nếu thứ tự bị đảo ngược?
3. `DaoAuthenticationProvider` dùng `UserDetailsService` và `PasswordEncoder` như thế nào để xác thực 1 user? Vẽ lại luồng bằng lời.
4. Giải thích chính xác sự khác biệt giữa `hasRole("ADMIN")` và `hasAuthority("ROLE_ADMIN")` — tại sao gọi `hasRole("ROLE_ADMIN")` gần như luôn là 1 bug?
5. Khi nào nên dùng `@PreAuthorize`, khi nào nên dùng `@PostAuthorize`? Cho 1 ví dụ cụ thể chỉ `@PostAuthorize` mới giải quyết được.
6. Vì sao ứng dụng stateless dùng JWT thường tắt CSRF nhưng vẫn giữ bật với ứng dụng session-based? Giải thích cơ chế tấn công CSRF mà cấu hình này phòng chống.

## Bài tập luyện tập
- Cấu hình `SecurityFilterChain` cơ bản: `/api/public/**` cho phép truy cập tự do, các endpoint còn lại yêu cầu đăng nhập, dùng `UserDetailsService` tự viết (lấy user từ 1 danh sách in-memory hoặc database) kết hợp `BCryptPasswordEncoder`.
- Viết 2 method trong 1 `@Service`, 1 method dùng `@PreAuthorize("hasRole('ADMIN')")`, 1 method dùng `@PostAuthorize("returnObject.owner == authentication.name")` — viết test kiểm chứng cả 2 chặn đúng khi user không đủ quyền.
- Cố tình viết sai `hasRole("ROLE_ADMIN")` (prefix trùng lặp), chạy thử để tái hiện lỗi luôn bị từ chối truy cập dù user có đúng quyền, sau đó sửa lại đúng thành `hasRole("ADMIN")`.
- Cấu hình 2 `SecurityFilterChain` riêng biệt với `@Order` khác nhau: 1 chain cho `/api/**` dùng `SessionCreationPolicy.STATELESS` (tắt CSRF), 1 chain cho phần còn lại dùng session-based (giữ CSRF bật) — kiểm chứng cả 2 hoạt động độc lập đúng theo URL pattern.
- Cấu hình CORS cho phép 1 domain frontend cụ thể gọi API, thử gọi từ 1 domain khác không nằm trong danh sách `allowedOrigins` để xác nhận request bị trình duyệt chặn.

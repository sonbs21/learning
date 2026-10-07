# Tuần 10 — Authentication và Authorization trong Spring Security

> Stack: Java 21, Spring Boot 4.x, Spring Security 7.x, Spring Data JPA và PostgreSQL. Bài này tiếp nối Task API, User/Project/Task ở các tuần trước. JWT, refresh token và OAuth2 sẽ học sâu hơn ở tuần 11.

## 1. Mục tiêu bài học

Sau tuần này, bạn có thể:

- Phân biệt authentication và authorization.
- Giải thích request đi qua SecurityFilterChain và SecurityContext thế nào.
- Tạo SecurityFilterChain theo nguyên tắc deny by default.
- Lưu password bằng BCrypt hoặc Argon2, không lưu plaintext.
- Dùng UserDetailsService, DaoAuthenticationProvider và AuthenticationManager.
- Thiết kế role USER, MANAGER, ADMIN và authority theo permission.
- Bật method security với PreAuthorize.
- Bảo vệ Task theo owner/project/admin.
- Hiểu session authentication, session fixation và stateless bearer authentication.
- Phân biệt 401 Unauthorized và 403 Forbidden.
- Chọn CSRF posture cho session cookie và Bearer token.
- Test security bằng MockMvc và integration test.

---

## 2. Authentication và authorization là gì?

Authentication trả lời:

> Request này đến từ user nào?

Ví dụ user đăng nhập bằng email/password. Nếu credential đúng, Spring tạo Authentication chứa principal và authorities.

Authorization trả lời:

> User đó được làm hành động này không?

Ví dụ:

- USER chỉ cập nhật Task do mình sở hữu.
- MANAGER quản lý Task trong Project của mình.
- ADMIN quản lý user và mọi Task.

Luồng tổng quát:

~~~text
HTTP request
    ↓
Security filters
    ↓
Authentication (nếu có credential)
    ↓
SecurityContext
    ↓
Authorization rules
    ↓
Controller / service hoặc 401 / 403
~~~

Authentication không đồng nghĩa authorization. User đã đăng nhập vẫn có thể bị từ chối khi truy cập tài nguyên không thuộc quyền.

---

## 3. Mô hình user và authority

### 3.1. Role và authority

Role là nhóm quyền cấp cao:

~~~text
ROLE_USER
ROLE_MANAGER
ROLE_ADMIN
~~~

Authority là quyền cụ thể:

~~~text
task:read
task:create
task:update:own
task:update:project
task:delete
user:manage
~~~

Trong Spring:

- hasRole("ADMIN") tự thêm prefix ROLE_ và tìm ROLE_ADMIN.
- hasAuthority("task:read") tìm đúng chuỗi authority.
- Không trộn role và authority tùy tiện trong cùng rule.

Role phù hợp coarse-grained access. Permission phù hợp domain rule chi tiết. Production thường dùng cả hai:

~~~text
ADMIN → user:manage, task:delete, task:update:any
MANAGER → task:read, task:update:project
USER → task:read, task:create, task:update:own
~~~

### 3.2. Entity database

Ví dụ tối thiểu:

~~~sql
CREATE TABLE app_users (
    id              UUID PRIMARY KEY,
    email           VARCHAR(254) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE user_authorities (
    user_id     UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    authority   VARCHAR(100) NOT NULL,
    PRIMARY KEY (user_id, authority)
);
~~~

Không lưu password client gửi. Column password_hash chứa hash được tạo bởi PasswordEncoder.

Nếu muốn role riêng:

~~~sql
CREATE TABLE roles (
    id VARCHAR(50) PRIMARY KEY
);

CREATE TABLE user_roles (
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    role_id VARCHAR(50) NOT NULL REFERENCES roles(id),
    PRIMARY KEY (user_id, role_id)
);
~~~

Schema authorization phải có unique constraint và foreign key. Không chỉ kiểm tra role bằng code UI.

---

## 4. Dependency và secret

Thêm starter:

~~~xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
~~~

Password encoder không cần secret riêng. Session signing key, JWT key, database password và OAuth client secret phải đọc từ secret manager/environment, không commit vào source.

Một password hợp lệ vẫn phải áp dụng policy phù hợp ở DTO: độ dài tối thiểu, không log request password, không trả password hash trong response.

---

## 5. SecurityFilterChain

Cấu hình API session-based với custom JSON login:

~~~java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfiguration {

    @Bean
    SecurityFilterChain apiSecurity(
            HttpSecurity http,
            SecurityContextRepository securityContextRepository
    ) throws Exception {
        http
            .securityContext(context -> context
                .securityContextRepository(securityContextRepository)
            )
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers(
                    "/api/auth/register",
                    "/api/auth/login",
                    "/actuator/health"
                ).permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/manager/**").hasAnyRole("MANAGER", "ADMIN")
                .anyRequest().authenticated()
            )
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable())
            .logout(logout -> logout.disable());

        return http.build();
    }
}
~~~

Đoạn trên là authenticate by default: mọi endpoint chưa khai báo riêng đều phải đăng nhập. Deny by default chặt hơn là dùng anyRequest().denyAll() rồi mở từng endpoint/HTTP method rõ ràng. Với app học nhỏ, anyRequest().authenticated() dễ thực hành hơn; với API production hoặc nhiều module, ưu tiên denyAll ở fallback chain để tránh endpoint mới vô tình được mở.

Thứ tự matcher rất quan trọng: rule cụ thể đặt trước rule tổng quát. Không mở rộng /api/** để rồi hy vọng controller tự kiểm tra quyền.

Nếu dùng nhiều SecurityFilterChain với securityMatcher, luôn có một fallback chain bảo vệ phần còn lại của app. Một chain chỉ match /api/** sẽ không bảo vệ actuator, static path hoặc endpoint khác nằm ngoài matcher đó.

SecurityFilterChain không phải controller. Nó quyết định request có được đi tiếp tới MVC hay không.

Spring Security bật CSRF mặc định cho servlet app. Với session-cookie và custom JSON login, POST /api/auth/login cũng nên có CSRF token, trừ khi bạn đã có threat model rõ ràng để ignore riêng endpoint đó. Đừng disable CSRF ở session app chỉ vì response là JSON.

---

## 6. 401 và 403

| Tình huống | Status | Ý nghĩa |
|---|---:|---|
| Chưa đăng nhập hoặc credential không hợp lệ | 401 | Authentication chưa hoàn tất |
| Đã đăng nhập nhưng thiếu quyền | 403 | Authentication thành công, authorization thất bại |
| Resource không tồn tại | 404 | Có thể áp dụng resource hiding theo policy |

AuthenticationEntryPoint xử lý 401. AccessDeniedHandler xử lý 403.

~~~java
@Bean
SecurityFilterChain apiSecurity(
        HttpSecurity http,
        AuthenticationEntryPoint entryPoint,
        AccessDeniedHandler deniedHandler
) throws Exception {
    http
        .exceptionHandling(exceptions -> exceptions
            .authenticationEntryPoint(entryPoint)
            .accessDeniedHandler(deniedHandler)
        )
        .authorizeHttpRequests(authorize -> authorize
            .requestMatchers("/api/auth/**").permitAll()
            .anyRequest().authenticated()
        );

    return http.build();
}
~~~

Response API nên dùng cùng Problem Details contract của tuần 6. Không trả stack trace, tên class, hay thông tin rằng email nào đã tồn tại khi muốn chống account enumeration.

---

## 7. Password hashing

### 7.1. PasswordEncoder bean

BCrypt là lựa chọn phổ biến:

~~~java
@Bean
PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
}
~~~

Argon2 là lựa chọn tốt nếu môi trường hỗ trợ memory-hard hashing:

~~~java
@Bean
PasswordEncoder passwordEncoder() {
    return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
}
~~~

Không tự viết SHA-256(password), MD5, hoặc mã hóa reversible. Password hashing cần salt và cost để chống brute force.

Cost factor phải đo trên hạ tầng thật. Hash quá nhanh làm brute force rẻ; hash quá chậm làm login dễ bị DoS. Không log password hoặc hash.

### 7.2. Đăng ký user

~~~java
@Service
public class RegistrationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public RegistrationService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserAccount register(RegisterCommand command) {
        String email = command.email().strip().toLowerCase(Locale.ROOT);

        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyRegisteredException();
        }

        UserAccount user = UserAccount.create(
                email,
                passwordEncoder.encode(command.password()),
                Set.of("ROLE_USER")
        );

        return userRepository.save(user);
    }
}
~~~

Unique constraint database vẫn bắt buộc vì hai request đồng thời có thể cùng vượt qua existsByEmail. Bắt DataIntegrityViolationException ở application/error layer và trả lỗi conflict theo contract.

### 7.3. Password upgrade

DelegatingPasswordEncoder có thể lưu prefix algorithm:

~~~text
{bcrypt}$2a$...
{argon2id}$argon2id$...
~~~

Khi đổi algorithm, verify hash cũ rồi re-encode bằng encoder mới trong lần đăng nhập thành công. Không tự sửa hash bằng SQL.

---

## 8. UserDetailsService và DaoAuthenticationProvider

UserDetailsService tải user theo username/email:

~~~java
@Service
public class DatabaseUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public DatabaseUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username)
            throws UsernameNotFoundException {

        UserAccount user = userRepository.findByEmail(username)
                .orElseThrow(() ->
                        new UsernameNotFoundException("Invalid credentials"));

        return User.withUsername(user.getEmail())
                .password(user.getPasswordHash())
                .disabled(!user.isEnabled())
                .authorities(user.getAuthorities().toArray(String[]::new))
                .build();
    }
}
~~~

Không trả thông báo khác nhau cho “email không tồn tại” và “password sai” ở login response. Log nội bộ có thể dùng correlation ID, nhưng không log credential.

DaoAuthenticationProvider kết hợp UserDetailsService và PasswordEncoder để:

1. Nhận username/password.
2. Load UserDetails.
3. Gọi PasswordEncoder.matches.
4. Tạo Authentication nếu hợp lệ.
5. Đặt Authentication vào SecurityContext theo cơ chế đang dùng.

Cấu hình provider thường được Boot tự tạo khi có UserDetailsService và PasswordEncoder. Khi cần explicit:

~~~java
@Bean
AuthenticationProvider daoAuthenticationProvider(
        UserDetailsService users,
        PasswordEncoder encoder
) {
    DaoAuthenticationProvider provider =
            new DaoAuthenticationProvider(users);
    provider.setPasswordEncoder(encoder);
    return provider;
}
~~~

API constructor của provider thay đổi theo Spring Security version; ưu tiên tài liệu version BOM đang dùng.

Nếu custom controller cần inject AuthenticationManager:

~~~java
@Bean
AuthenticationManager authenticationManager(
        AuthenticationConfiguration configuration
) throws Exception {
    return configuration.getAuthenticationManager();
}
~~~

---

## 9. Login bằng AuthenticationManager

Nếu ứng dụng tự nhận POST /login và muốn phát hành session/cookie:

~~~java
public record LoginRequest(
        @NotBlank @Email String email,
        @NotBlank String password
) {
}
~~~

~~~java
@RestController
@RequestMapping("/api/auth")
public class AuthenticationController {

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;

    public AuthenticationController(
            AuthenticationManager authenticationManager,
            SecurityContextRepository securityContextRepository
    ) {
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
    }

    @PostMapping("/login")
    public LoginResponse login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        Authentication authentication =
                authenticationManager.authenticate(
                        UsernamePasswordAuthenticationToken.unauthenticated(
                                request.email(),
                                request.password()
                        )
                );

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, httpRequest, httpResponse);

        return LoginResponse.from(authentication);
    }
}
~~~

Khai báo SecurityContextRepository bean phù hợp, thường là DelegatingSecurityContextRepository gồm request và HttpSession repository:

~~~java
@Bean
SecurityContextRepository securityContextRepository() {
    return new DelegatingSecurityContextRepository(
            new RequestAttributeSecurityContextRepository(),
            new HttpSessionSecurityContextRepository()
    );
}
~~~

Nếu tự khai báo SecurityContextRepository, nối cùng repository đó vào HttpSecurity bằng securityContext(...) như ở phần SecurityFilterChain. Với form login do Spring quản lý, framework xử lý flow này. Với custom JSON login, phải gọi saveContext sau khi authenticate; chỉ set thread-local không làm request sau đăng nhập.

Không trả password, internal user ID không cần thiết, hay toàn bộ authorities nếu API không cần. Response có thể là user summary và trạng thái authenticated; JWT access/refresh token thuộc tuần 11.

Với CSRF enabled, frontend cần lấy CSRF token trước rồi gửi lại trong header khi POST login/register. Với SPA, CookieCsrfTokenRepository thường được dùng để frontend đọc token CSRF; cookie session vẫn phải HttpOnly.

---

## 10. SecurityContext

SecurityContext chứa Authentication hiện tại. Lấy user ở service:

~~~java
Authentication authentication =
        SecurityContextHolder.getContext().getAuthentication();

String email = authentication.getName();
Collection<? extends GrantedAuthority> authorities =
        authentication.getAuthorities();
~~~

Tốt hơn là truyền actor/current user rõ ràng xuống use case hoặc dùng method security expression. Không đọc SecurityContext trong entity/domain model.

SecurityContext gắn với execution context của request. Async thread, scheduled job và message consumer cần cơ chế truyền identity riêng; không giả định SecurityContext tự xuất hiện ở mọi thread.

Authentication phải được xem là dữ liệu untrusted từ phía client sau khi framework xác minh. Object principal không nên chứa quyền tùy ý do request gửi lên.

---

## 11. Role và permission

### 11.1. URL authorization

~~~java
http.authorizeHttpRequests(authorize -> authorize
    .requestMatchers("/api/auth/**").permitAll()
    .requestMatchers(HttpMethod.GET, "/api/tasks/**")
        .hasAuthority("task:read")
    .requestMatchers(HttpMethod.DELETE, "/api/tasks/**")
        .hasAuthority("task:delete")
    .requestMatchers("/api/admin/**")
        .hasRole("ADMIN")
    .anyRequest().authenticated()
);
~~~

Rule URL phù hợp coarse-grained. Nó không biết Task id thuộc user nào.

### 11.2. Method security

Bật:

~~~java
@Configuration
@EnableMethodSecurity
class MethodSecurityConfiguration {
}
~~~

Bảo vệ admin:

~~~java
@PreAuthorize("hasRole('ADMIN')")
public void disableUser(UUID userId) {
    userRepository.disable(userId);
}
~~~

Bảo vệ permission:

~~~java
@PreAuthorize("hasAuthority('task:delete')")
public void deleteTask(UUID taskId) {
    taskRepository.deleteById(taskId);
}
~~~

Method security là lớp bổ sung, không thay validation và ownership check trong service. Đừng đặt expression phức tạp tới mức business rule bị giấu trong annotation.

---

## 12. Ownership và project authorization

USER chỉ được sửa Task của mình. MANAGER được sửa Task trong Project mình quản lý. Có thể dùng authorization bean:

~~~java
@Component("taskAuthorization")
public class TaskAuthorization {

    private final TaskRepository taskRepository;
    private final ProjectMembershipRepository membershipRepository;

    public TaskAuthorization(
            TaskRepository taskRepository,
            ProjectMembershipRepository membershipRepository
    ) {
        this.taskRepository = taskRepository;
        this.membershipRepository = membershipRepository;
    }

    public boolean canEdit(UUID taskId, Authentication authentication) {
        String email = authentication.getName();

        return taskRepository.findOwnerEmailById(taskId)
                .map(owner -> owner.equalsIgnoreCase(email))
                .orElse(false)
                || hasManagerAccess(taskId, email)
                || has(authentication, "ROLE_ADMIN");
    }

    private boolean hasManagerAccess(UUID taskId, String email) {
        return membershipRepository.existsManagerForTask(taskId, email);
    }

    private boolean has(Authentication authentication, String authority) {
        return authentication.getAuthorities().stream()
                .anyMatch(item -> item.getAuthority().equals(authority));
    }
}
~~~

~~~java
@PreAuthorize("@taskAuthorization.canEdit(#taskId, authentication)")
@Transactional
public void updateTask(UUID taskId, UpdateTaskCommand command) {
    // service vẫn load entity và kiểm tra state transition
}
~~~

Authorization query không nên trả entity graph lớn. Dùng exists/projection để kiểm tra quyền. Vẫn bảo vệ bằng database constraint và transaction vì authorization check và update có thể gặp race.

Nếu resource không tồn tại, chọn policy nhất quán giữa 404 và 403. Không để timing/error message vô tình tiết lộ resource private.

---

## 13. Session authentication

Session phù hợp cho browser app hoặc API dùng secure cookie:

~~~java
http
    .sessionManagement(session -> session
        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
        .sessionFixation(fixation -> fixation.changeSessionId())
        .maximumSessions(5)
    )
    .formLogin(Customizer.withDefaults());
~~~

Cookie production cần:

~~~yaml
server:
  servlet:
    session:
      cookie:
        http-only: true
        secure: true
        same-site: strict
~~~

Secure cookie chỉ hoạt động đúng khi request đi qua HTTPS. SameSite Strict có thể ảnh hưởng flow cross-site hợp lệ; chọn chính sách theo frontend/deployment.

Sau login, session fixation protection đổi session ID. Không tự copy session ID từ request vào response. Logout phải invalidate session và xóa cookie.

Session state cần shared store khi chạy nhiều instance, hoặc sticky session có tradeoff. Nếu không có shared store, user có thể authenticated ở instance này nhưng không ở instance khác.

---

## 14. Stateless Bearer authentication

API dùng Authorization header thường không lưu authentication trong server session:

~~~java
@Bean
SecurityFilterChain bearerApi(HttpSecurity http) throws Exception {
    http
        .sessionManagement(session -> session
            .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
        )
        .oauth2ResourceServer(oauth2 -> oauth2
            .jwt(Customizer.withDefaults())
        )
        .authorizeHttpRequests(authorize -> authorize
            .requestMatchers("/api/auth/**").permitAll()
            .anyRequest().authenticated()
        );

    return http.build();
}
~~~

Thêm starter resource server khi học phần này:

~~~xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
</dependency>
~~~

Cấu hình tối thiểu thường trỏ tới issuer của identity provider:

~~~yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${JWT_ISSUER_URI}
~~~

Resource server validate chữ ký, expiry/not-before và issuer khi cấu hình issuer-uri hoặc decoder tương ứng. Audience không phải lúc nào cũng được check mặc định; nếu API yêu cầu aud cụ thể, thêm OAuth2TokenValidator<Jwt> riêng. Không tự parse JWT bằng split chuỗi trong controller.

Bearer token bị đánh cắp có thể dùng cho tới khi hết hạn/revocation policy. Thiết kế access token ngắn hạn, refresh token rotation và key rotation sẽ học ở tuần 11.

CSRF posture khác session:

- Cookie tự động gửi theo browser nên cần CSRF protection.
- Bearer token trong Authorization header không tự động được browser gửi như cookie; API stateless thường disable CSRF sau khi xác minh architecture.
- Không disable CSRF chỉ vì endpoint trả JSON.

Không dùng cả session và bearer cho cùng path nếu chưa document precedence và threat model.

---

## 15. CSRF

CSRF lợi dụng browser tự động gửi cookie của site đích. Session-cookie browser app nên giữ CSRF enabled:

~~~java
http.csrf(csrf -> csrf
    .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
);
~~~

Frontend gửi token trong header hoặc hidden form field theo contract.

API thuần Bearer token có thể:

~~~java
http
    .csrf(csrf -> csrf.disable())
    .sessionManagement(session -> session
        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
    );
~~~

Điều kiện: không dùng cookie để authenticate path đó, CORS được giới hạn và token validation đúng. CSRF không thay authentication, CORS hay authorization.

Đừng dùng GET cho mutation để né CSRF. POST/PATCH/DELETE vẫn phải authorize và validate.

---

## 16. Register, login và me API

Contract bài tập:

~~~text
POST /api/auth/register
POST /api/auth/login
POST /api/auth/logout
GET  /api/users/me
~~~

Register:

~~~java
@PostMapping("/register")
@ResponseStatus(HttpStatus.CREATED)
public UserResponse register(
        @Valid @RequestBody RegisterRequest request
) {
    UserAccount user = registrationService.register(
            new RegisterCommand(request.email(), request.password())
    );
    return userMapper.toResponse(user);
}
~~~

Me:

~~~java
@GetMapping("/me")
public UserResponse me(Authentication authentication) {
    return userService.findResponseByEmail(authentication.getName());
}
~~~

Controller nhận Authentication từ Spring, không nhận email/role trong request body để xác định actor. Client chỉ cung cấp target resource; server lấy current principal từ security context.

---

## 17. CORS và security headers

CORS không phải authorization. Chỉ cho phép origin frontend thật:

~~~java
@Bean
CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(List.of("https://app.example.com"));
    config.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE"));
    config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-CSRF-TOKEN"));
    config.setAllowCredentials(true);
    config.setMaxAge(Duration.ofHours(1));

    UrlBasedCorsConfigurationSource source =
            new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/**", config);
    return source;
}
~~~

Không dùng allowed origin * khi allowCredentials=true. Không tin origin/header do client tự khai ngoài cơ chế CORS của browser.

Giữ security headers mặc định của Spring Security. CSP, frame policy, referrer policy và HSTS cần kiểm tra theo frontend/deployment; không copy header lạ mà chưa hiểu ảnh hưởng.

---

## 18. Test authentication và authorization

### 18.1. Test request đã authenticated

~~~java
@WebMvcTest(TaskController.class)
class TaskSecurityTest {

    @Autowired
    MockMvc mvc;

    @Test
    void anonymousUserGets401() throws Exception {
        mvc.perform(get("/api/tasks"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(
            username = "user@example.com",
            authorities = "task:read"
    )
    void userWithReadAuthorityCanListTasks() throws Exception {
        mvc.perform(get("/api/tasks"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "task:read")
    void userWithoutDeleteAuthorityGets403() throws Exception {
        mvc.perform(delete("/api/tasks/{id}", UUID.randomUUID())
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }
}
~~~

Package annotation có thể là org.springframework.security.test.context.support.WithMockUser. Thêm spring-security-test ở scope test.

Test HTTP rule và method rule riêng. @WithMockUser bỏ qua password hashing, nên vẫn cần integration test login thật. Nếu dùng @WebMvcTest, import SecurityConfiguration và mock các bean phụ thuộc thay vì tắt security filters.

Nếu endpoint stateless dùng resource server JWT, test bằng jwt() để mô phỏng bearer token đã được decoder xác thực:

~~~java
mvc.perform(get("/api/tasks")
        .with(jwt().authorities(
                new SimpleGrantedAuthority("task:read")
        )))
    .andExpect(status().isOk());
~~~

### 18.2. Test password

- Register không lưu plaintext.
- PasswordEncoder.matches(password, hash) là true.
- Password sai bị từ chối.
- Hai password giống nhau tạo hash khác nhau do salt.
- Response không chứa password_hash.

### 18.3. Test CSRF

Session-cookie mutation không có CSRF token phải bị từ chối:

~~~java
mvc.perform(post("/api/tasks")
        .with(user("user@example.com"))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{}"))
    .andExpect(status().isForbidden());
~~~

Test thêm request có csrf() để chứng minh contract đúng. Bearer stateless endpoint cần test token validation và không vô tình tạo session.

### 18.4. Integration test

Dùng database thật/Testcontainers để kiểm tra:

- UserDetailsService load đúng authorities.
- Unique email và disabled user.
- Login success/failure.
- SecurityContext/session sau login.
- User chỉ sửa Task own.
- Manager chỉ sửa Task thuộc Project mình quản lý.
- Admin quản lý user.
- 401/403 Problem Details nhất quán.

---

## 19. Lỗi thường gặp

- Lưu password plaintext hoặc hash bằng MD5/SHA-256 đơn giản.
- Trả “email không tồn tại” khác “password sai”, làm lộ account.
- Mở permitAll cho /api/** rồi quên method authorization.
- Dùng hasRole("ROLE_ADMIN"); hasRole tự thêm ROLE_ nên phải dùng hasRole("ADMIN").
- Chỉ kiểm tra quyền ở UI.
- Nhận actor email/role từ request body.
- Disable CSRF cho session-cookie API vì thấy response là JSON.
- Dùng session nhưng khai báo STATELESS hoặc tự set SecurityContext mà không persist.
- Parse JWT bằng code tự viết thay vì resource server.
- Catch mọi exception trong auth và trả 200.
- Log Authorization header, password, refresh token hoặc session ID.
- Dùng CORS origin * cùng credentials.
- Không rate limit login/register.
- Chỉ test @WithMockUser, không test login/password/database.
- Dùng role quá rộng thay vì permission tối thiểu.

---

## 20. Bài thực hành bắt buộc

1. Tạo migration app_users và user_authorities.
2. Tạo register API với password policy và PasswordEncoder.
3. Tạo login API hoặc form/session flow.
4. Tạo /api/users/me.
5. Cấu hình SecurityFilterChain deny by default.
6. Seed USER, MANAGER và ADMIN với authority phù hợp.
7. Bảo vệ admin endpoint bằng role.
8. Bảo vệ Task update bằng owner/project authorization.
9. Bật method security và viết PreAuthorize.
10. Viết custom 401/403 Problem Details.
11. Chọn session-cookie hoặc stateless bearer cho từng path và document CSRF posture.
12. Viết MockMvc test cho anonymous, authenticated, insufficient authority và CSRF.
13. Chạy integration test login trên PostgreSQL Testcontainers.
14. Kiểm tra log không có password/token/hash.

---

## 21. Câu hỏi tự kiểm tra

1. Authentication khác authorization thế nào?
2. SecurityFilterChain chạy ở đâu so với controller?
3. SecurityContext chứa gì?
4. hasRole khác hasAuthority thế nào?
5. Vì sao role phải dùng ROLE_ prefix?
6. DaoAuthenticationProvider làm những bước gì?
7. PasswordEncoder cần salt và cost để làm gì?
8. Vì sao unique constraint email vẫn cần dù service đã existsByEmail?
9. Khi nào trả 401 và khi nào trả 403?
10. Ownership check khác role check thế nào?
11. Vì sao session-cookie cần CSRF?
12. Khi nào API Bearer token có thể disable CSRF?
13. Session fixation là gì?
14. Vì sao stateless token không có nghĩa token không cần expiry/revocation?
15. Vì sao không lấy current user từ request body?
16. @WithMockUser chưa đủ để test login vì sao?
17. SecurityContext có tự truyền sang mọi async thread không?
18. CORS có thay authorization không?

---

## 22. Tiêu chí hoàn thành tuần 10

- [ ] Có register, login, logout và me API.
- [ ] Password không bao giờ lưu plaintext hoặc log.
- [ ] Có PasswordEncoder BCrypt/Argon2.
- [ ] SecurityFilterChain deny by default.
- [ ] Có USER, MANAGER, ADMIN và permission phù hợp.
- [ ] Có URL authorization và method security.
- [ ] User chỉ sửa được Task theo ownership policy.
- [ ] 401 và 403 có response contract riêng, không lộ nội bộ.
- [ ] Đã chọn session hoặc stateless bearer theo từng architecture.
- [ ] CSRF posture được document và test.
- [ ] Session cookie có HttpOnly, Secure, SameSite phù hợp.
- [ ] Không có secret/token/password trong log hoặc Git.
- [ ] Có MockMvc và integration security tests.
- [ ] Login/register có rate limit plan cho tuần 11.

---

## 23. Gợi ý lịch học 7 ngày

| Ngày | Nội dung | Kết quả |
|---|---|---|
| 1 | Authentication, filter chain, SecurityContext | Hiểu request security flow |
| 2 | User entity, PasswordEncoder, register | Hash password đúng |
| 3 | UserDetailsService, provider, login | Login tạo Authentication |
| 4 | Role/authority và URL rules | Endpoint được bảo vệ |
| 5 | Method security và ownership | User/Manager/Admin đúng quyền |
| 6 | Session, stateless, CSRF, 401/403 | Chọn architecture có lý do |
| 7 | MockMvc/integration test và audit log | Hoàn thiện security checklist |

Tuần 11 sẽ mở rộng JWT access/refresh token, expiration, key rotation, token revocation, OAuth2/OIDC, CORS và rate limiting.

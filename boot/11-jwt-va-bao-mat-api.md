# Tuần 11 — JWT và bảo mật API

> Stack: Java 21, Spring Boot 4.x, Spring Security 7.x, Spring Data JPA và PostgreSQL. Bài này tiếp nối authentication/authorization ở tuần 10. JWT, refresh token và OAuth2/OIDC được học trong bối cảnh API stateless.

## 1. Mục tiêu bài học

Sau tuần này, bạn có thể:

- Giải thích cấu trúc và giới hạn của JWT.
- Phân biệt access token với refresh token.
- Thiết kế access token sống ngắn và refresh token rotation.
- Validate chữ ký, issuer, audience, expiry, not-before và algorithm.
- Cấu hình Spring Security Resource Server với JWT.
- Hiểu JWKS, kid và key rotation.
- Vô hiệu hóa refresh token và phát hiện reuse.
- Phân biệt OAuth 2.0 với OpenID Connect.
- Cấu hình CORS và rate limiting cho API.
- Áp dụng các rủi ro chính trong OWASP API Security.
- Quản lý signing key, client secret và token an toàn.
- Viết test cho token invalid, expired, wrong audience, revoked refresh token và rate limit.

---

## 2. Access token và refresh token

Luồng thông thường:

~~~text
Client ── login ──> Authorization Server
Client <─ access token + refresh token ─
Client ── access token ──> API Resource Server
Client <─ 401 khi access token hết hạn
Client ── refresh token ──> Authorization Server
Client <─ access token mới + refresh token mới ─
~~~

Access token dùng gọi API. Refresh token chỉ dùng lấy access token mới.

| Token | Dùng ở đâu | Thời gian | Cách lưu |
|---|---|---:|---|
| Access token JWT | Authorization header tới API | 5–15 phút | Memory hoặc secure storage của client |
| Refresh token opaque | Chỉ gửi authorization server | ngày/tuần theo policy | Hash ở server, revoke được |
| ID token OIDC | Client biết danh tính user | Ngắn | Không gửi tới API để authorize |

Không dùng ID token thay access token. ID token dành cho client biết user đã authenticate; access token có audience/resource server và scope dùng để gọi API.

JWT không tự động làm hệ thống an toàn. JWT thường khó revoke trước expiry, payload có thể bị đọc, và việc ký token không thay thế authorization object-level.

---

## 3. Cấu trúc JWT

JWT compact serialization có ba phần Base64URL:

~~~text
base64url(header).base64url(payload).base64url(signature)
~~~

Header:

~~~json
{
  "alg": "RS256",
  "typ": "JWT",
  "kid": "key-2026-01"
}
~~~

Payload:

~~~json
{
  "iss": "https://id.example.com",
  "sub": "user-uuid",
  "aud": ["task-api"],
  "exp": 1790000000,
  "iat": 1789999100,
  "nbf": 1789999100,
  "scope": "task:read task:update",
  "roles": ["USER"]
}
~~~

Signature xác nhận token được phát hành bởi private key tương ứng với public key. Header và payload không bí mật; Base64URL không phải encryption. Không đưa password, refresh token, secret, dữ liệu y tế hoặc thông tin nhạy cảm vào claim.

Các claim quan trọng:

- iss: issuer phải đúng identity provider đã cấu hình.
- sub: subject, định danh user; không dùng email thay cho ID bất biến nếu email có thể đổi.
- aud: resource server mà token dành cho.
- exp: thời điểm hết hạn; phải kiểm tra.
- iat: thời điểm phát hành.
- nbf: token chỉ hợp lệ sau thời điểm này.
- scope/roles: dữ liệu authorization, nhưng API vẫn phải kiểm tra ownership trong database.

Reject algorithm không được phép. Không chấp nhận token chỉ vì decode được payload.

---

## 4. Cấu hình Resource Server

### 4.1. Dependency

~~~xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
</dependency>
~~~

Resource server nhận Bearer token và validate JWT. Ứng dụng không cần tự viết OncePerRequestFilter để decode JWT khi dùng starter chính thức.

### 4.2. Cấu hình issuer

~~~yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: <OAUTH2_ISSUER_URI>
~~~

issuer-uri cho phép Spring lấy metadata và JWKS từ provider. Production phải pin đúng issuer HTTPS, không nhận issuer từ request.

Nếu provider không hỗ trợ discovery hoặc muốn cấu hình JWKS trực tiếp, vẫn giữ issuer-uri để Spring validate claim iss:

~~~yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: <OAUTH2_ISSUER_URI>
          jwk-set-uri: <OAUTH2_JWK_SET_URI>
~~~

jwk-set-uri giúp resource server không phải gọi discovery endpoint lúc startup. issuer-uri vẫn dùng để validate issuer của token. Không tắt issuer/audience validation chỉ để làm test pass.

### 4.3. SecurityFilterChain stateless

~~~java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class ResourceServerSecurityConfiguration {

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http) throws Exception {
        http
            .cors(Customizer.withDefaults())
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(Customizer.withDefaults())
            )
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/api/auth/refresh").permitAll()
                .requestMatchers("/api/admin/**").hasAuthority("SCOPE_user:manage")
                .anyRequest().authenticated()
            );

        return http.build();
    }
}
~~~

CSRF chỉ disable ở API path thực sự dùng Bearer header và không authenticate bằng cookie. Nếu cùng ứng dụng có browser session path, tách SecurityFilterChain bằng securityMatcher và giữ CSRF cho session chain.

permitAll cho /api/auth/refresh chỉ có nghĩa endpoint này không cần access token JWT. Controller/service refresh vẫn phải validate raw refresh token, kiểm tra expiry/revoke/reuse và áp dụng rate limit.

STATELESS không có nghĩa token không cần expiry/revocation. Nó chỉ nói server không lưu authentication session cho request đó.

---

## 5. Validate token đúng cách

Resource server cần kiểm tra tối thiểu:

~~~text
1. Có chữ ký hợp lệ với public key hiện tại
2. Algorithm nằm trong allowlist
3. issuer đúng
4. audience chứa resource server hiện tại
5. exp chưa hết hạn
6. nbf và clock skew hợp lệ
7. token type/scope phù hợp endpoint
~~~

Audience validator:

~~~java
@Bean
JwtDecoder jwtDecoder(
        @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
        String issuer
) {
    JwtDecoder decoder = JwtDecoders.fromIssuerLocation(issuer);

    OAuth2TokenValidator<Jwt> issuerValidator =
            JwtValidators.createDefaultWithIssuer(issuer);

    OAuth2TokenValidator<Jwt> audienceValidator = token ->
            token.getAudience().contains("task-api")
                    ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(
                        new OAuth2Error(
                            "invalid_token",
                            "Wrong audience",
                            null
                        )
                    );

    if (!(decoder instanceof NimbusJwtDecoder nimbusDecoder)) {
        throw new IllegalStateException("Expected Nimbus JWT decoder");
    }

    nimbusDecoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
            issuerValidator,
            audienceValidator
    ));
    return nimbusDecoder;
}
~~~

Đoạn decoder phụ thuộc provider/algorithm; cách đơn giản hơn là dùng JwtDecoders.fromIssuerLocation và cấu hình validator bằng bean phù hợp version Spring Security. Điều cần nhớ là audience không nên chỉ kiểm tra bằng controller.

Clock skew phải nhỏ và có chủ đích. Không thêm nhiều phút để che server clock sai; đồng bộ NTP và theo dõi clock.

---

## 6. Map scope và role thành authority

Resource server thường map scope thành authority có prefix SCOPE_:

~~~text
scope: task:read task:update
→ SCOPE_task:read
→ SCOPE_task:update
~~~

Bảo vệ endpoint:

~~~java
@PreAuthorize("hasAuthority('SCOPE_task:read')")
@GetMapping("/api/tasks")
public TaskPageResponse listTasks(...) {
    // ...
}
~~~

Nếu identity provider đưa roles claim:

~~~java
@Bean
JwtAuthenticationConverter jwtAuthenticationConverter() {
    JwtGrantedAuthoritiesConverter scopes =
            new JwtGrantedAuthoritiesConverter();

    JwtAuthenticationConverter converter =
            new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(jwt -> {
        Set<GrantedAuthority> authorities = new HashSet<>(
                scopes.convert(jwt)
        );

        List<String> roles = jwt.getClaimAsStringList("roles");
        if (roles != null) {
            roles.stream()
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                    .forEach(authorities::add);
        }

        return authorities;
    });
    return converter;
}
~~~

Cấu hình converter vào jwt:

~~~java
.oauth2ResourceServer(oauth2 -> oauth2
    .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
)
~~~

Không tin roles do frontend gửi. Roles phải đến từ token đã ký bởi issuer tin cậy, và những quyết định ownership vẫn phải query database.

---

## 7. Access token sống ngắn

Access token nên sống ngắn vì resource server thường không gọi authorization server cho mỗi request để kiểm tra revoke.

Ví dụ policy:

~~~text
access token: 5–15 phút
refresh token: theo session/device policy
clock skew: vài chục giây, không tùy tiện
~~~

Khi access token hết hạn:

- API trả 401 với WWW-Authenticate phù hợp.
- Client dùng refresh token với authorization server.
- Authorization server rotate refresh token và trả token mới.
- Client retry request một lần có kiểm soát.
- Không retry vô hạn khi nhận 401.

Không lưu access token vào localStorage cho browser nếu threat model lo XSS. Với browser app, cân nhắc BFF hoặc memory storage, secure HttpOnly SameSite cookie cho session/refresh flow và CSRF protection tương ứng. Chọn storage theo architecture.

---

## 8. Refresh token an toàn

### 8.1. Token ngẫu nhiên, không phải JWT

Refresh token có thể là chuỗi random 256-bit:

~~~java
byte[] bytes = new byte[32];
SecureRandom secureRandom = new SecureRandom();
secureRandom.nextBytes(bytes);

String rawToken = Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(bytes);
~~~

Client nhận raw token. Database chỉ lưu hash:

~~~java
String tokenHash = sha256Hex(rawToken);

static String sha256Hex(String value) {
    try {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256")
                        .digest(value.getBytes(StandardCharsets.UTF_8))
        );
    } catch (NoSuchAlgorithmException impossible) {
        throw new IllegalStateException("SHA-256 is unavailable", impossible);
    }
}
~~~

SHA-256 phù hợp cho token random đủ entropy vì attacker không thể brute-force như password. Password vẫn phải dùng BCrypt/Argon2.

### 8.2. Bảng refresh session

~~~sql
CREATE TABLE refresh_sessions (
    id                  UUID PRIMARY KEY,
    user_id             UUID NOT NULL REFERENCES app_users(id),
    token_hash          CHAR(64) NOT NULL UNIQUE,
    family_id           UUID NOT NULL,
    issued_at           TIMESTAMPTZ NOT NULL,
    expires_at          TIMESTAMPTZ NOT NULL,
    revoked_at          TIMESTAMPTZ,
    replaced_by_id      UUID REFERENCES refresh_sessions(id),
    device_label        VARCHAR(200),
    last_used_at        TIMESTAMPTZ,
    created_ip_hash     VARCHAR(128)
);

CREATE INDEX idx_refresh_sessions_user
    ON refresh_sessions(user_id);

CREATE INDEX idx_refresh_sessions_family
    ON refresh_sessions(family_id);
~~~

Không lưu raw refresh token hoặc IP đầy đủ nếu không cần. Nếu cần audit, hash/truncate và có retention policy.

### 8.3. Rotation và reuse detection

Refresh request hợp lệ:

~~~text
1. Hash raw token
2. Tìm row theo token_hash
3. Kiểm tra chưa revoke và chưa hết hạn
4. Revoke token hiện tại
5. Tạo refresh token mới cùng family_id
6. Ghi replaced_by_id/last_used_at
7. Phát access token mới
8. Trả refresh token raw mới đúng một lần
~~~

Nếu token đã revoked nhưng lại được dùng, có thể là token reuse. Revoke toàn bộ family_id và yêu cầu login lại.

Rotation phải atomic trong transaction. Hai refresh request đồng thời cần lock hoặc conditional update để không cùng sử dụng một token hợp lệ.

Với public client theo OAuth 2.0 Security BCP, refresh token cần rotation như trên hoặc sender-constrained token. Nếu không sender-constrain, rotation + reuse detection là lớp phòng thủ chính khi token bị đánh cắp.

---

## 9. Revoke token

Các sự kiện cần revoke:

- User logout trên một device.
- Logout toàn bộ phiên.
- Đổi password.
- Admin disable user.
- Phát hiện refresh token reuse.
- Security incident/key compromise.

Revoke refresh token bằng database phù hợp hơn JWT access token. Access token đã phát hành vẫn có thể sống tới exp; nếu cần revoke tức thời, dùng opaque access token/introspection hoặc denylist có TTL, nhưng tăng latency và state.

Không gọi JWT “logout được ngay” nếu API chỉ verify chữ ký và exp mà không kiểm tra revocation.

---

## 10. Key rotation và JWKS

### 10.1. Private/public key

Authorization server ký bằng private key. Resource server chỉ cần public key/JWKS để verify:

~~~text
private key → ký token
public key  → API verify token
JWKS        → endpoint phân phối public keys
kid         → chọn đúng public key
~~~

Private key không commit Git, không in log, không gửi tới resource server. Dùng KMS, Vault, cloud secret manager hoặc secret volume.

### 10.2. Rotation không downtime

1. Tạo key pair mới.
2. Publish public key mới trong JWKS với kid mới.
3. Authorization server bắt đầu ký token bằng kid mới.
4. Resource server refresh JWKS và chấp nhận key cũ trong thời gian token cũ còn sống.
5. Sau max access-token lifetime + clock skew, ngừng publish key cũ.
6. Revoke/rotate refresh sessions nếu có dấu hiệu compromise.

Resource server phải cache JWKS nhưng có cách refresh khi gặp kid mới. Không fetch JWKS từ request URL do client gửi.

### 10.3. Algorithm policy

Allowlist algorithm đã chọn, ví dụ RS256 hoặc ES256. Không lấy alg từ token rồi chấp nhận bất kỳ giá trị nào. Khi đổi algorithm, rollout và test tương thích trước.

---

## 11. OAuth 2.0 và OpenID Connect

OAuth 2.0 là framework delegation/authorization. OIDC thêm authentication identity layer trên OAuth 2.0.

| Thành phần | Vai trò |
|---|---|
| Resource Owner | User |
| Client | Web/mobile app yêu cầu token |
| Authorization Server | Login, consent, phát token |
| Resource Server | API validate access token |
| Identity Provider | Cung cấp identity |

Authorization Code + PKCE phù hợp user-facing app. Client credentials phù hợp machine-to-machine, không đại diện user.

Resource server nên validate issuer, audience, signature và scopes. Client không nên tự nhận claims từ frontend rồi gọi API với role tự khai.

OIDC ID token có claims identity như sub, iss, aud, nonce. Access token có audience resource server và scope. Không gửi ID token vào business API.

Nếu tự dựng authorization server, cần credential recovery, consent, key management, MFA, session, revocation và compliance. Với project học, dùng provider chuẩn an toàn hơn tự viết toàn bộ.

---

## 12. CORS cho API

CORS là policy của browser, không phải authorization:

~~~java
@Bean
CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(List.of("https://app.example.com"));
    config.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE"));
    config.setAllowedHeaders(List.of(
            "Authorization",
            "Content-Type",
            "X-Request-Id"
    ));
    config.setExposedHeaders(List.of("Location", "Retry-After"));
    config.setAllowCredentials(false);
    config.setMaxAge(Duration.ofMinutes(30));

    UrlBasedCorsConfigurationSource source =
            new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/**", config);
    return source;
}
~~~

Không dùng origin * cùng credentials. Không phản chiếu Origin bất kỳ vào Access-Control-Allow-Origin. CORS không ngăn curl/server-to-server; authentication và authorization vẫn chạy.

Khi có CorsConfigurationSource bean, nối CORS vào security chain bằng http.cors(Customizer.withDefaults()) như phần 4.3 để preflight request được xử lý ở security filter.

---

## 13. Rate limiting login và refresh

Login và refresh là endpoint đắt, dễ bị brute force. Rate limit theo nhiều key:

~~~text
IP + username/email hash
user account
device/session
global endpoint
~~~

Không chỉ dùng IP vì người dùng có thể ở NAT chung; cũng không chỉ dùng username vì attacker có thể đổi username.

Khi vượt limit:

~~~http
HTTP/1.1 429 Too Many Requests
Retry-After: 60
Content-Type: application/problem+json
~~~

Rate limiter in-memory phù hợp single instance/dev. Nhiều instance cần gateway/Redis/distributed limiter. Key client IP chỉ đáng tin khi reverse proxy được cấu hình trust đúng; X-Forwarded-For từ client có thể spoof.

Rate limit không thay password hashing, MFA, account lock policy và monitoring. Tránh account lock vĩnh viễn vì attacker có thể dùng nó để DoS user.

---

## 14. Secret management

Secret cần externalize:

~~~yaml
security:
  jwt:
    issuer: <JWT_ISSUER>
    audience: <JWT_AUDIENCE>
    # Không đặt private key thật trong Git
~~~

Quy tắc:

- Không hardcode signing key, DB password, client secret hoặc refresh token.
- Không log Authorization header/cookie/token.
- Không đưa secret vào exception detail.
- Rotate key và credential định kỳ.
- Giới hạn quyền đọc secret theo service.
- Dùng secret version và rollback được.
- Redact secret trong tracing/HTTP access log.
- Scan Git history nếu từng lộ secret; rotate ngay cả khi đã xóa file.

Public JWKS có thể công khai; private signing key không bao giờ được đưa vào resource server.

---

## 15. OWASP API Security cần kiểm tra

### Broken Object Level Authorization

User có token hợp lệ nhưng đổi taskId của user khác. Luôn check ownership/project authorization trong service/database.

### Broken Authentication

Token parser tự viết, expiry không kiểm tra, refresh token không rotate, password policy yếu hoặc login không rate limit.

### Broken Object Property Level Authorization

Client gửi role=ADMIN, ownerId hoặc status nội bộ trong update DTO. Dùng DTO field allowlist và server-side assignment.

### Unrestricted Resource Consumption

Page size không giới hạn, query keyword không kiểm soát, upload lớn, refresh/login không rate limit.

### Security Misconfiguration

CORS *, debug stack trace, actuator public, secret trong config, algorithm bất kỳ hoặc session cookie thiếu Secure.

### Improper Inventory Management

API version cũ vẫn public, JWKS/issuer endpoint nhầm môi trường, endpoint test còn mở.

### Unsafe Consumption of APIs

Không tin dữ liệu từ identity provider/downstream tuyệt đối; validate issuer/audience/schema/timeout và có allowlist.

Security review không chỉ kiểm tra JWT chữ ký. Authorization và resource ownership vẫn là trọng tâm.

---

## 16. Error handling cho JWT API

Token invalid/expired thường trả 401. Cách đơn giản nhất là dùng handler có sẵn của Spring Security:

~~~java
@Bean
AuthenticationEntryPoint bearerEntryPoint() {
    return new BearerTokenAuthenticationEntryPoint();
}

@Bean
AccessDeniedHandler bearerDeniedHandler() {
    return new BearerTokenAccessDeniedHandler();
}
~~~

Nối handler vào SecurityFilterChain nếu bạn override exception handling:

~~~java
.exceptionHandling(exceptions -> exceptions
    .authenticationEntryPoint(bearerEntryPoint())
    .accessDeniedHandler(bearerDeniedHandler())
)
~~~

Hai handler built-in trên tạo WWW-Authenticate header theo Bearer token contract. Nếu cần bọc response body theo Problem Details của tuần 6, chọn custom entry point riêng và vẫn phải giữ header đúng chuẩn:

~~~java
@Bean
AuthenticationEntryPoint problemBearerEntryPoint(ObjectMapper mapper) {
    return (request, response, exception) -> {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType("application/problem+json");
        response.setHeader(
                HttpHeaders.WWW_AUTHENTICATE,
                "Bearer error=\"invalid_token\""
        );
        mapper.writeValue(response.getOutputStream(), Map.of(
                "type", "/problems/invalid-token",
                "title", "Authentication required",
                "status", 401,
                "detail", "The access token is invalid or expired"
        ));
    };
}
~~~

Thiếu scope nhưng token hợp lệ trả 403 qua AccessDeniedHandler. Không trả lý do chi tiết như key id, signature bytes, token claims hay issuer internals.

WWW-Authenticate header nên dùng theo Bearer token contract, nhưng không đưa token hoặc secret vào header/body. Error handler không được log raw token.

---

## 17. Test JWT và API security

### 17.1. SecurityMockMvcRequestPostProcessors

~~~java
@WebMvcTest(TaskController.class)
class JwtAuthorizationTest {

    @Autowired
    MockMvc mvc;

    @Test
    void anonymousRequestGets401() throws Exception {
        mvc.perform(get("/api/tasks"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void readScopeCanListTasks() throws Exception {
        mvc.perform(get("/api/tasks")
                .with(jwt().authorities(
                        new SimpleGrantedAuthority("SCOPE_task:read")
                )))
                .andExpect(status().isOk());
    }

    @Test
    void missingScopeGets403() throws Exception {
        mvc.perform(delete("/api/tasks/{id}", UUID.randomUUID())
                .with(jwt().authorities(
                        new SimpleGrantedAuthority("SCOPE_task:read")
                )))
                .andExpect(status().isForbidden());
    }
}
~~~

jwt() tạo Authentication giả để kiểm tra authorization, không chứng minh decoder verify chữ ký thật.

### 17.2. Integration test với signed JWT

Test riêng:

- Token đúng signature được chấp nhận.
- Signature sai bị từ chối.
- exp trong quá khứ bị từ chối.
- nbf trong tương lai bị từ chối.
- issuer sai bị từ chối.
- audience sai bị từ chối.
- algorithm không allowlist bị từ chối.
- kid cũ trong thời gian overlap vẫn hợp lệ.
- kid bị thu hồi/không tồn tại bị từ chối.
- Scope map thành authority đúng.

### 17.3. Refresh token test

- Raw refresh token không xuất hiện trong DB/log.
- Refresh token dùng lần đầu được rotation.
- Token cũ dùng lần hai revoke cả family.
- Token hết hạn bị từ chối.
- Logout revoke đúng device hoặc toàn bộ session theo policy.
- Hai refresh request đồng thời không cùng thành công.
- Đổi password revoke session theo policy.

Không commit token thật, signing private key hay production JWKS vào test. Tạo key ephemeral/test fixture và kiểm tra log output không chứa credential.

---

## 18. Bài thực hành bắt buộc

1. Cấu hình Resource Server validate issuer và audience.
2. Tạo access token policy sống ngắn.
3. Tạo refresh_sessions migration với token_hash, family_id, expiry và revoked_at.
4. Implement refresh token rotation.
5. Implement reuse detection và revoke family.
6. Map scope/roles thành Spring authorities.
7. Bảo vệ Task bằng scope + ownership.
8. Cấu hình JWKS và mô phỏng key rotation với kid cũ/mới.
9. Cấu hình CORS allowlist cho local/test/prod.
10. Rate limit login và refresh; trả 429 có Retry-After.
11. Rà soát OWASP API risks cho endpoint register/login/refresh/tasks.
12. Viết test token expired, wrong issuer/audience/signature/scope.
13. Kiểm tra log không có token/password/private key.
14. Document logout, revoke, expiry và incident response.

---

## 19. Câu hỏi tự kiểm tra

1. JWT payload có được mã hóa không?
2. Access token khác ID token thế nào?
3. Vì sao access token nên sống ngắn?
4. Vì sao refresh token nên opaque và lưu hash?
5. Refresh token rotation phát hiện reuse ra sao?
6. Khi nào revoke một token và khi nào revoke cả family?
7. JWKS và kid hỗ trợ key rotation thế nào?
8. Issuer và audience khác nhau gì?
9. Vì sao resource server không nên chỉ decode JWT?
10. OAuth2 khác OIDC ở lớp nào?
11. CORS có thay authorization không?
12. Vì sao rate limit theo mỗi IP là chưa đủ?
13. Stateless API có logout JWT tức thời không?
14. Khi nào disable CSRF cho API?
15. Test jwt() chứng minh điều gì và không chứng minh điều gì?
16. Vì sao BOLA vẫn xảy ra dù token signature hợp lệ?

---

## 20. Tiêu chí hoàn thành tuần 11

- [ ] Access token sống ngắn và có exp.
- [ ] Resource server validate signature, issuer, audience, nbf và expiry.
- [ ] Không hardcode signing key/client secret.
- [ ] Refresh token được lưu hash, không lưu raw token.
- [ ] Có refresh token rotation và reuse detection.
- [ ] Có revoke theo device/family/user policy.
- [ ] Có key rotation với JWKS/kid.
- [ ] Scope/role map thành authority rõ ràng.
- [ ] Ownership check vẫn chạy ở service/database.
- [ ] CORS dùng allowlist.
- [ ] Login/refresh có rate limit và 429 contract.
- [ ] Không log token, password, cookie hoặc private key.
- [ ] Có test invalid/expired/wrong issuer/audience/signature.
- [ ] Có rà soát OWASP API Security.
- [ ] Có tài liệu logout, expiry, revoke và incident response.

---

## 21. Gợi ý lịch học 7 ngày

| Ngày | Nội dung | Kết quả |
|---|---|---|
| 1 | JWT structure, claims, access/ID/refresh token | Biết token nào dùng ở đâu |
| 2 | Resource Server, issuer, audience, scopes | API validate token đúng |
| 3 | Refresh token hash, rotation, revoke | Có token lifecycle |
| 4 | JWKS, kid và key rotation | Đổi key không downtime |
| 5 | OAuth2/OIDC và CORS | Chọn flow đúng client |
| 6 | Rate limiting, secret management, OWASP API | Giảm abuse và misconfiguration |
| 7 | Security tests và incident checklist | Hoàn thiện bảo mật API |

Tuần 12 theo roadmap chuyển sang unit test và test slice: service với Mockito, WebMvcTest, MockMvc, DataJpaTest, validation và authorization tests.

## 22. Tài liệu tham khảo

- Spring Security 7 Resource Server JWT: https://docs.spring.io/spring-security/reference/7.0/servlet/oauth2/resource-server/jwt.html
- Spring Security 7 OAuth2 Resource Server: https://docs.spring.io/spring-security/reference/7.0/servlet/oauth2/resource-server/index.html
- OAuth 2.0 Authorization Framework: https://www.rfc-editor.org/rfc/rfc6749
- OAuth 2.0 Bearer Token Usage: https://www.rfc-editor.org/rfc/rfc6750
- OAuth 2.0 Security Best Current Practice: https://www.rfc-editor.org/rfc/rfc9700
- JSON Web Token: https://www.rfc-editor.org/rfc/rfc7519
- OWASP API Security Top 10: https://owasp.org/API-Security/

# Tuần 6: Security nâng cao

> Tài liệu tổng hợp kiến thức mục 6 trong lộ trình [spring-boot-roadmap.md](spring-boot-roadmap.md). Nội dung đã được đối chiếu với Spring Security Reference (6.5) qua Context7 để đảm bảo chính xác về cấu hình OAuth2 Resource Server. Phần JWT/OAuth2/OIDC dựa trên chuẩn IETF (RFC 7519, RFC 6749) và OpenID Connect Core — các đặc tả ổn định, không thay đổi theo version thư viện.

---

## 1. JWT (JSON Web Token)

### 1.1 Cấu trúc: `header.payload.signature`
JWT là 1 chuỗi gồm 3 phần, mỗi phần encode bằng Base64URL, nối nhau bằng dấu `.`:

```
eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4ifQ.dQw4w9WgXcQ...
└──── header ────┘ └──────── payload ────────┘ └── signature ──┘
```
- **Header**: chứa metadata — thuật toán ký (`alg`, VD `HS256`/`RS256`) và loại token (`typ: JWT`).
- **Payload**: chứa **claims** — thông tin về subject (`sub`), thời gian hết hạn (`exp`), issuer (`iss`), và các claim tùy chỉnh khác. **Payload chỉ encode, không mã hóa** — ai cũng đọc được nội dung nếu có token, nên **không bao giờ** nhét thông tin nhạy cảm (mật khẩu, số thẻ...) vào payload.
- **Signature**: chữ ký để xác minh token **không bị giả mạo/chỉnh sửa** — tính bằng cách ký (header + payload) với secret key hoặc private key.

### 1.2 Access Token vs Refresh Token
| | Access Token | Refresh Token |
|---|---|---|
| Mục đích | Gửi kèm mỗi request để xác thực | Dùng để **xin cấp lại** access token mới khi hết hạn |
| Thời gian sống | **Ngắn** (VD 15 phút) — giảm thiệt hại nếu bị lộ | **Dài** (VD 7-30 ngày) |
| Gửi tới đâu | Gửi kèm mọi API request (`Authorization: Bearer <token>`) | Chỉ gửi tới endpoint refresh token, không gửi kèm API request thông thường |
| Vòng đời renew | Không renew — hết hạn thì dùng refresh token xin cái mới | Có thể tự renew (rotate) mỗi lần dùng để giảm rủi ro bị đánh cắp và tái sử dụng |

### 1.3 Ký và verify: HMAC (HS256) vs RSA (RS256)
| | HMAC (HS256) | RSA (RS256) |
|---|---|---|
| Loại khóa | **Đối xứng** — cùng 1 secret key dùng để **ký và verify** | **Bất đối xứng** — private key để ký, public key để verify |
| Ai verify được | Chỉ bên có secret key (thường chỉ 1 server duy nhất) | **Bất kỳ ai có public key** — phù hợp khi nhiều service (microservices) cần tự verify token mà không cần giữ secret nhạy cảm |
| Rủi ro | Nếu secret bị lộ, kẻ tấn công vừa verify được vừa **tự ký được token giả** | Chỉ nơi giữ private key mới ký được token giả — public key bị lộ (vốn dĩ công khai) không gây rủi ro tương tự |
| Dùng khi | Hệ thống đơn giản, monolith, 1 bên phát hành + 1 bên verify | Kiến trúc microservices/OAuth2 — Authorization Server ký bằng private key, nhiều Resource Server verify bằng public key lấy từ JWKS endpoint |

### 1.4 Lưu trữ token phía client
| Nơi lưu | Ưu điểm | Nhược điểm |
|---|---|---|
| **`localStorage`** | Dễ dùng, JavaScript truy cập trực tiếp được | Dễ bị đánh cắp qua **XSS** (JavaScript độc hại chèn vào trang có thể đọc thẳng `localStorage`) |
| **Cookie `HttpOnly`** | JavaScript **không đọc được** (chống XSS đọc trộm token) | Trình duyệt **tự động gửi kèm** cookie mỗi request tới domain đó → cần bật CSRF protection để chống giả mạo request lợi dụng cookie này (xem lại [05-spring-security-co-ban.md](05-spring-security-co-ban.md) mục 5.2) |

Không có lựa chọn nào "an toàn tuyệt đối" — đây là sự đánh đổi giữa 2 loại tấn công (XSS vs CSRF), cần kết hợp thêm các biện pháp khác (Content Security Policy chống XSS, CSRF token/SameSite cookie chống CSRF) dù chọn cách nào.

---

## 2. OAuth2 & OIDC

### 2.1 Các flow (grant type) chính
| Flow | Dùng khi | Cách hoạt động (tóm tắt) |
|---|---|---|
| **Authorization Code** | Ứng dụng có backend (web app, mobile app) cần user đăng nhập | User được redirect tới Authorization Server đăng nhập → nhận về 1 `code` tạm thời → backend dùng `code` đó đổi lấy access token (đổi ở backend, token không lộ ra trình duyệt) |
| **Client Credentials** | Giao tiếp **server-to-server**, không có user cụ thể (machine-to-machine) | Client tự gửi `client_id` + `client_secret` trực tiếp để đổi lấy access token, không qua bước đăng nhập user |
| **Implicit** *(không khuyến nghị)* | Cũ dùng cho SPA (Single Page App) | Token trả thẳng về trình duyệt qua URL fragment — dễ lộ token, hiện khuyến nghị dùng Authorization Code + PKCE thay thế |
| **Resource Owner Password** *(không khuyến nghị)* | Cũ dùng khi ứng dụng tự thu thập username/password | Ứng dụng client trực tiếp cầm password của user gửi cho Authorization Server — vi phạm nguyên tắc "chỉ Authorization Server mới nên thấy password", chỉ nên dùng cho hệ thống nội bộ tin cậy tuyệt đối |

### 2.2 OIDC (OpenID Connect) bổ sung gì trên nền OAuth2
OAuth2 vốn dĩ chỉ giải quyết bài toán **Authorization** ("app này được phép làm gì thay mặt user") — bản thân nó **không định nghĩa** cách xác định "user là ai". OIDC là 1 lớp mở rộng trên OAuth2, bổ sung:
- **ID Token**: 1 JWT riêng biệt chứa thông tin **định danh** user (`sub`, `name`, `email`...) — dùng để ứng dụng biết chính xác ai vừa đăng nhập, khác với access token (dùng để gọi API thay mặt user).
- Endpoint chuẩn hóa `/userinfo` để lấy thêm thông tin profile user.
- `.well-known/openid-configuration` — endpoint discovery chuẩn để client tự động biết các endpoint khác của Authorization Server.

### 2.3 Resource Server vs Authorization Server vs Client
| Vai trò | Trách nhiệm |
|---|---|
| **Authorization Server** | Nơi user đăng nhập, cấp phát access token/ID token/refresh token (VD Keycloak) |
| **Resource Server** | API thực sự chứa dữ liệu cần bảo vệ, **verify** token gửi kèm request (không tự cấp token) — đây chính là vai trò Spring Boot backend thường đóng |
| **Client** | Ứng dụng (web/mobile/service khác) yêu cầu truy cập tài nguyên **thay mặt** user hoặc chính nó, cầm token đi gọi Resource Server |

---

## 3. Keycloak Integration

### 3.1 Cấu hình Realm, Client, Role mapping
- **Realm**: 1 không gian cô lập quản lý user/client/role riêng (tương tự "tenant") — mỗi công ty/dự án thường dùng 1 realm riêng.
- **Client**: đại diện cho 1 ứng dụng được phép xin token từ realm đó (VD `order-service`, `frontend-app`), mỗi client có `client_id` + `client_secret` riêng (với confidential client).
- **Role mapping**: gán role cho user, có thể là **realm role** (dùng chung toàn realm) hoặc **client role** (chỉ áp dụng cho 1 client cụ thể).

### 3.2 Đặc điểm claim `realm_access.roles` của Keycloak
Access token do Keycloak phát hành chứa role trong 1 claim **không theo chuẩn OAuth2/OIDC thông thường**:
```json
{
  "sub": "f1a2b3c4",
  "realm_access": {
    "roles": ["user", "order-admin"]
  },
  "resource_access": {
    "order-service": { "roles": ["order:read"] }
  }
}
```
Vì Spring Security **mặc định** chỉ hiểu claim `scope`/`scp` (chuẩn OAuth2) thành authority, nên cần viết 1 `Converter` tùy chỉnh để "dịch" `realm_access.roles` của Keycloak thành `GrantedAuthority` mà `@PreAuthorize("hasRole(...)")` hiểu được:

```java
@Bean
public JwtAuthenticationConverter jwtAuthenticationConverter() {
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(jwt -> {
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess == null) return List.of();
        List<String> roles = (List<String>) realmAccess.get("roles");
        return roles.stream()
            .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase())) // thêm prefix ROLE_ để dùng được hasRole()
            .collect(Collectors.toList());
    });
    return converter;
}
```

### 3.3 Spring Boot làm Resource Server xác thực token từ Keycloak
```yaml
# application.yml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: http://localhost:8180/realms/myrealm
          # Spring Boot tự động gọi .well-known/openid-configuration để lấy jwk-set-uri
          # Chỉ cần khai báo jwk-set-uri trực tiếp nếu Authorization Server không hỗ trợ discovery endpoint:
          # jwk-set-uri: http://localhost:8180/realms/myrealm/protocol/openid-connect/certs
```
```java
@Bean
SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationConverter jwtAuthenticationConverter) throws Exception {
    http
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/api/public/**").permitAll()
            .anyRequest().authenticated()
        )
        .oauth2ResourceServer(oauth2 -> oauth2
            .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
        );
    return http.build();
}
```
Với cấu hình này, Spring Boot tự động: lấy public key từ JWKS endpoint của Keycloak → verify chữ ký RS256 của token gửi lên → kiểm tra `exp`/`iss` → convert claim thành `Authentication` object có sẵn để dùng trong `@PreAuthorize`.

### 3.4 Dependency cần thiết
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
</dependency>
```

---

## 4. Cryptography liên quan

### 4.1 AES-GCM — mã hóa đối xứng có authentication tag
- **Đối xứng**: cùng 1 khóa dùng để mã hóa và giải mã — nhanh, phù hợp mã hóa dữ liệu khối lượng lớn (VD mã hóa field nhạy cảm trong database).
- **GCM (Galois/Counter Mode)**: ngoài mã hóa còn tự sinh ra **authentication tag** — cho phép phát hiện nếu ciphertext bị **chỉnh sửa** (tamper detection), không chỉ đơn thuần mã hóa như các mode cũ hơn (VD ECB, CBC không có tính năng này).

### 4.2 RSA — mã hóa bất đối xứng, dùng cho chữ ký JWT RS256
- **Bất đối xứng**: cặp khóa **public/private** — private key ký hoặc giải mã, public key verify hoặc mã hóa.
- Trong ngữ cảnh JWT RS256: Authorization Server giữ **private key** để ký token, mọi Resource Server chỉ cần **public key** (thường phân phối qua JWKS endpoint) để verify — không cần chia sẻ bí mật nào giữa các service, phù hợp kiến trúc phân tán/microservices.

### 4.3 BCrypt, Argon2 — băm mật khẩu
Khác biệt cốt lõi so với AES/RSA: **băm (hashing) là 1 chiều**, không có thao tác "giải mã ngược" — chỉ dùng để **so khớp**, không dùng để khôi phục lại giá trị gốc (đã trình bày kỹ ở [05-spring-security-co-ban.md](05-spring-security-co-ban.md) mục 2.3):
- **Salt tự động**: cả BCrypt và Argon2 đều tự sinh salt ngẫu nhiên nhúng vào chuỗi hash kết quả — 2 user cùng dùng chung 1 password vẫn cho ra 2 chuỗi hash khác nhau, chống rainbow table attack.
- **Chống brute-force**: cả 2 đều có "work factor"/"cost" cấu hình được — cố tình làm chậm quá trình tính hash (mili-giây thay vì micro-giây) để kẻ tấn công không thể thử hàng triệu mật khẩu/giây, mà không ảnh hưởng đáng kể tới trải nghiệm user (chỉ hash 1 lần khi login).

---

## Câu hỏi tự kiểm tra
1. Vì sao payload của JWT không nên chứa thông tin nhạy cảm dù token đã có chữ ký? Chữ ký bảo vệ điều gì, và không bảo vệ điều gì?
2. Access token và refresh token khác nhau ở thời gian sống và nơi được gửi tới như thế nào? Vì sao access token nên có thời gian sống ngắn?
3. Vì sao kiến trúc microservices thường chọn RS256 thay vì HS256 cho JWT? Nếu dùng HS256 trong microservices, rủi ro gì phát sinh khi cần nhiều service cùng verify token?
4. OIDC bổ sung gì so với OAuth2 thuần túy? ID Token và Access Token khác nhau ở mục đích sử dụng như thế nào?
5. Vì sao claim `realm_access.roles` của Keycloak cần 1 `JwtAuthenticationConverter` tùy chỉnh thay vì Spring Security tự hiểu được?
6. AES-GCM và RSA khác nhau cơ bản ở điểm nào (đối xứng vs bất đối xứng)? Vì sao JWT RS256 dùng RSA mà không dùng AES?
7. Tại sao BCrypt/Argon2 lại "cố tình chậm" thay vì tối ưu cho nhanh như các hàm băm thông thường (VD MD5, SHA-256 trần)?

## Bài tập luyện tập
- Dùng thư viện `jjwt` (hoặc tương đương) tự tay tạo 1 JWT ký bằng HS256, decode lại bằng tay (Base64URL decode từng phần header/payload) để nhìn thấy rõ nội dung claim — không dùng thư viện decode, tự viết code decode thủ công để hiểu cơ chế.
- Cài đặt Keycloak (Docker), tạo 1 realm, 1 client, 2 user với role khác nhau (`user`, `order-admin`), lấy access token qua `curl` với `grant_type=password`, decode token để quan sát claim `realm_access.roles`.
- Cấu hình Spring Boot làm Resource Server trỏ tới Keycloak vừa dựng, viết `JwtAuthenticationConverter` tùy chỉnh để map `realm_access.roles` thành `GrantedAuthority`, viết 1 endpoint `@PreAuthorize("hasRole('ORDER-ADMIN')")` và kiểm chứng đúng user có role mới truy cập được.
- Viết 1 đoạn code Java tự mã hóa/giải mã 1 chuỗi bằng AES-GCM (dùng `javax.crypto.Cipher`), cố tình chỉnh sửa 1 byte của ciphertext trước khi giải mã để chứng minh GCM phát hiện được tamper (ném exception thay vì trả về dữ liệu sai lệch âm thầm).
- So sánh thời gian hash của BCrypt ở các cost factor khác nhau (VD 10 vs 14) bằng `System.nanoTime()`, giải thích bằng số liệu thực tế vì sao cost factor cao hơn giúp chống brute-force hiệu quả hơn nhưng cũng tốn tài nguyên server hơn khi có nhiều user login đồng thời.

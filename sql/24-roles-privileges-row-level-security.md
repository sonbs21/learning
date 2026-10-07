# Tuần 12 nâng cao: Roles, privileges và Row-Level Security trong PostgreSQL

> Mục tiêu: thiết kế role theo nguyên tắc quyền tối thiểu, phân biệt owner với runtime user, cấp quyền đúng cho schema/table/sequence/function, dùng default privileges, kiểm soát search_path, hiểu SSL/TLS ở mức triển khai và xây dựng Row-Level Security cho mô hình multi-tenant.

Bảo mật PostgreSQL là một chuỗi kiểm soát:

~~~text
identity
  → login role
  → role membership và SET ROLE
  → privilege trên schema/object
  → policy giới hạn row
  → TLS, secret và network
~~~

Application không nên kết nối bằng superuser hoặc owner. Migration, runtime, readonly và reporting nên là các role khác nhau, mỗi role chỉ nhận quyền cần thiết.

---

## 1. Môi trường lab

Các lệnh CREATE ROLE cần superuser hoặc quyền tương ứng:

~~~bash
psql -h localhost -U postgres -d lab
~~~

Tạo schema multi-tenant:

~~~sql
CREATE SCHEMA IF NOT EXISTS shop;

CREATE TABLE IF NOT EXISTS shop.tenants (
    tenant_id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_name text NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS shop.tenant_memberships (
    role_name name PRIMARY KEY,
    tenant_id bigint NOT NULL REFERENCES shop.tenants(tenant_id)
);

CREATE TABLE IF NOT EXISTS shop.documents (
    document_id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id bigint NOT NULL REFERENCES shop.tenants(tenant_id),
    title text NOT NULL,
    body text NOT NULL DEFAULT '',
    created_by name NOT NULL DEFAULT current_user,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

INSERT INTO shop.tenants (tenant_name)
VALUES ('tenant-a'), ('tenant-b')
ON CONFLICT (tenant_name) DO NOTHING;
~~~

Dữ liệu tenant phải được seed bằng migration/owner. Không cấp quyền sửa bảng mapping tenant cho application runtime.

---

## 2. Role login và role group

PostgreSQL dùng role cho cả user và group. LOGIN quyết định role có mở connection được không:

~~~sql
CREATE ROLE app_owner NOLOGIN;
CREATE ROLE app_runtime NOLOGIN;
CREATE ROLE app_readonly NOLOGIN;
CREATE ROLE app_reporting NOLOGIN;

CREATE ROLE api_login LOGIN
    PASSWORD 'thay-bang-secret-tu-secret-manager';

GRANT app_runtime TO api_login;
~~~

Ý nghĩa:

| Role | Mục đích |
|---|---|
| app_owner | schema/object owner và migration |
| app_runtime | quyền tối thiểu cho API |
| app_readonly | đọc dữ liệu được phép |
| app_reporting | đọc view hoặc báo cáo |
| api_login | login role của service |

Password trong ví dụ chỉ dành cho lab. Secret thật phải ở secret manager, không nằm trong Git hoặc file migration.

### LOGIN, NOLOGIN và membership

Role group thường là NOLOGIN:

~~~sql
ALTER ROLE app_runtime NOLOGIN;
ALTER ROLE api_login LOGIN;
GRANT app_runtime TO api_login;
~~~

Kiểm tra:

~~~sql
\du+

SELECT
    rolname,
    rolsuper,
    rolinherit,
    rolcreaterole,
    rolcreatedb,
    rolcanlogin,
    rolbypassrls,
    rolconnlimit
FROM pg_roles
ORDER BY rolname;
~~~

INHERIT cho phép role login dùng quyền của role thành viên theo cơ chế kế thừa. Với NOINHERIT, session phải SET ROLE:

~~~sql
CREATE ROLE migration_login LOGIN PASSWORD 'lab-only-secret';
GRANT app_owner TO migration_login;

SET ROLE app_owner;
-- chạy migration
RESET ROLE;
~~~

Kiểm tra identity:

~~~sql
SELECT
    session_user,
    current_user,
    current_role,
    current_setting('role');
~~~

session_user là người mở connection. current_user là identity dùng để kiểm tra quyền hiện tại và có thể thay đổi sau SET ROLE hoặc trong SECURITY DEFINER function.

Không cấp cho application:

~~~sql
ALTER ROLE api_login SUPERUSER;
ALTER ROLE api_login BYPASSRLS;
ALTER ROLE api_login CREATEDB;
ALTER ROLE api_login CREATEROLE;
~~~

SUPERUSER và BYPASSRLS làm các bài test bảo mật mất ý nghĩa. CREATEDB/CREATEROLE mở rộng phạm vi thay đổi không cần thiết.

---

## 3. Role membership và ADMIN OPTION

Xem membership:

~~~sql
SELECT
    member.rolname AS member_role,
    parent.rolname AS granted_role,
    m.admin_option
FROM pg_auth_members AS m
JOIN pg_roles AS member ON member.oid = m.member
JOIN pg_roles AS parent ON parent.oid = m.roleid
ORDER BY member.rolname, parent.rolname;
~~~

Grant và revoke:

~~~sql
GRANT app_runtime TO api_login;
REVOKE app_runtime FROM api_login;
~~~

ADMIN OPTION cho phép member grant role tiếp cho role khác:

~~~sql
GRANT app_runtime TO api_login WITH ADMIN OPTION;
~~~

Không cấp ADMIN OPTION nếu không có yêu cầu. Khi debug quyền, kiểm tra role trực tiếp, membership gián tiếp, INHERIT, SET ROLE và thuộc tính BYPASSRLS.

---

## 4. Privilege trên schema

Có quyền trên table chưa đủ; role phải có USAGE trên schema:

~~~sql
REVOKE ALL ON SCHEMA shop FROM PUBLIC;

GRANT USAGE ON SCHEMA shop
TO app_runtime, app_readonly, app_reporting;

GRANT CREATE ON SCHEMA shop TO app_owner;
REVOKE CREATE ON SCHEMA shop
FROM app_runtime, app_readonly, app_reporting;
~~~

USAGE cho phép resolve object trong schema. Nó không tự cấp SELECT hoặc INSERT trên table.

Không để schema writable của user không tin cậy nằm trước schema tin cậy trong search_path:

~~~sql
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
~~~

Điều này đặc biệt quan trọng với SECURITY DEFINER function, vì object giả mạo có thể được resolve trước object hợp lệ nếu search_path sai.

---

## 5. Table, column, sequence và function privilege

### Table privilege

~~~sql
GRANT SELECT ON shop.documents TO app_readonly;

GRANT SELECT, INSERT, UPDATE
ON shop.documents TO app_runtime;

REVOKE DELETE, TRUNCATE, REFERENCES, TRIGGER
ON shop.documents FROM app_runtime;
~~~

Các quyền chính:

- SELECT: đọc;
- INSERT: thêm;
- UPDATE: sửa;
- DELETE: xóa;
- TRUNCATE: truncate cả table;
- REFERENCES: tạo foreign key tham chiếu;
- TRIGGER: tạo trigger.

Không cấp ALL PRIVILEGES nếu application chỉ cần một phần.

### Column privilege

~~~sql
GRANT SELECT (document_id, tenant_id, title, created_at)
ON shop.documents TO app_reporting;

GRANT UPDATE (title, body)
ON shop.documents TO app_runtime;
~~~

Column privilege hữu ích khi table có cột nhạy cảm. RLS lọc row; column privilege kiểm soát cột. Hai lớp không thay thế nhau.

### Sequence và identity

INSERT vào table dùng identity/serial có thể cần quyền sequence. Kiểm tra:

~~~sql
\ds shop.*

SELECT sequence_schema, sequence_name
FROM information_schema.sequences
WHERE sequence_schema = 'shop';
~~~

Nếu runtime lỗi permission trên sequence:

~~~sql
GRANT USAGE, SELECT
ON ALL SEQUENCES IN SCHEMA shop
TO app_runtime;
~~~

Hãy test INSERT bằng role runtime thật, không đoán quyền từ DDL.

### Function privilege

Function mới thường có EXECUTE cho PUBLIC. Function nhạy cảm cần thu hẹp:

~~~sql
REVOKE ALL ON FUNCTION shop.current_tenant_id() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION shop.current_tenant_id()
TO app_runtime;
~~~

Function được xác định bằng tên và kiểu tham số:

~~~sql
SELECT
    n.nspname,
    p.proname,
    pg_get_function_identity_arguments(p.oid) AS arguments,
    has_function_privilege('app_runtime', p.oid, 'EXECUTE')
FROM pg_proc AS p
JOIN pg_namespace AS n ON n.oid = p.pronamespace
WHERE n.nspname = 'shop';
~~~

---

## 6. Ownership không giống GRANT

Owner có quyền quản trị object:

- thay đổi hoặc drop object;
- grant quyền;
- thường bypass RLS;
- quyết định default privileges khi tạo object.

Đặt owner:

~~~sql
ALTER SCHEMA shop OWNER TO app_owner;
ALTER TABLE shop.documents OWNER TO app_owner;
ALTER TABLE shop.tenants OWNER TO app_owner;
ALTER TABLE shop.tenant_memberships OWNER TO app_owner;
~~~

Application không nên là owner. Nếu application tạo table trong production, object có thể thuộc login runtime và migration owner không quản lý được.

Kiểm tra owner:

~~~sql
SELECT
    n.nspname AS schema_name,
    c.relname AS object_name,
    pg_get_userbyid(c.relowner) AS owner_name,
    c.relkind
FROM pg_class AS c
JOIN pg_namespace AS n ON n.oid = c.relnamespace
WHERE n.nspname = 'shop'
ORDER BY c.relname;
~~~

REVOKE quyền khỏi owner không biến owner thành user hạn chế. Đó là lý do phải tách app_owner và app_runtime.

---

## 7. Default privileges

GRANT hiện tại không áp dụng cho object tạo sau. Cấu hình default privileges theo role tạo object:

~~~sql
ALTER DEFAULT PRIVILEGES
FOR ROLE app_owner
IN SCHEMA shop
GRANT SELECT, INSERT, UPDATE
ON TABLES TO app_runtime;

ALTER DEFAULT PRIVILEGES
FOR ROLE app_owner
IN SCHEMA shop
GRANT SELECT
ON TABLES TO app_readonly;

ALTER DEFAULT PRIVILEGES
FOR ROLE app_owner
IN SCHEMA shop
GRANT USAGE, SELECT
ON SEQUENCES TO app_runtime;

ALTER DEFAULT PRIVILEGES
FOR ROLE app_owner
IN SCHEMA shop
REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC;

ALTER DEFAULT PRIVILEGES
FOR ROLE app_owner
IN SCHEMA shop
GRANT EXECUTE ON FUNCTIONS TO app_runtime;
~~~

Default privileges không sửa object cũ. Cần REVOKE/GRANT riêng cho object đã tồn tại.

Nếu migration login SET ROLE app_owner để tạo object, cần xác nhận object được tạo với owner nào và default privilege của role nào được áp dụng. Đây là lỗi cấu hình thường gặp.

Xem ACL:

~~~sql
SELECT
    n.nspname,
    c.relname,
    c.relacl
FROM pg_class AS c
JOIN pg_namespace AS n ON n.oid = c.relnamespace
WHERE n.nspname = 'shop'
ORDER BY c.relname;
~~~

---

## 8. Kiểm tra quyền

Trong psql:

~~~sql
\dn+ shop
\dp shop.*
\df+ shop.*
\du+
~~~

Trong SQL:

~~~sql
SELECT
    has_schema_privilege('app_runtime', 'shop', 'USAGE'),
    has_table_privilege('app_runtime', 'shop.documents', 'SELECT'),
    has_table_privilege('app_runtime', 'shop.documents', 'INSERT'),
    has_table_privilege('app_runtime', 'shop.documents', 'DELETE');
~~~

Kiểm tra role hiện tại:

~~~sql
SELECT
    session_user,
    current_user,
    current_schema(),
    current_schemas(false);
~~~

Đừng kết luận chỉ từ GRANT. Mở session bằng role thật và thử SELECT, INSERT, UPDATE, DELETE, function, sequence và view.

---

## 9. search_path

search_path quyết định PostgreSQL tìm object không schema-qualified ở đâu:

~~~sql
SHOW search_path;
SELECT current_schemas(true);
~~~

Trong SQL ứng dụng, ưu tiên:

~~~sql
SELECT title
FROM shop.documents;
~~~

Có thể đặt search_path cho role:

~~~sql
ALTER ROLE api_login
SET search_path = shop, pg_catalog;
~~~

Không đặt schema mà user không tin cậy có CREATE trước schema tin cậy. Với SECURITY DEFINER function:

~~~sql
CREATE FUNCTION shop.safe_lookup(p_id bigint)
RETURNS text
LANGUAGE sql
SECURITY DEFINER
SET search_path = shop, pg_temp
AS $$
    SELECT title
    FROM shop.documents
    WHERE document_id = p_id;
$$;
~~~

Schema-qualified name vẫn là cách dễ audit nhất. search_path đặc biệt quan trọng với function, operator, type và table không ghi rõ schema.

---

## 10. SSL/TLS ở mức triển khai

SSL/TLS bảo vệ đường truyền giữa client và PostgreSQL. Nó không thay thế GRANT, RLS, password policy hoặc secret management.

Kiểm tra:

~~~sql
SHOW ssl;
SHOW ssl_min_protocol_version;

SELECT
    ssl,
    version,
    cipher,
    bits,
    client_dn
FROM pg_stat_ssl
WHERE pid = pg_backend_pid();
~~~

Client production nên xác thực server:

~~~bash
psql "host=db.example.internal dbname=app user=api_login sslmode=verify-full sslrootcert=/path/ca.crt"
~~~

Các sslmode cần phân biệt:

- disable: không dùng TLS;
- prefer: thử TLS nhưng có thể fallback;
- require: yêu cầu TLS nhưng chưa nhất thiết verify hostname;
- verify-ca: verify CA;
- verify-full: verify CA và hostname.

Ngoài connection string, cần kiểm tra pg_hba.conf để ép SSL, giới hạn network và phương thức xác thực phù hợp. Có TLS nhưng client không verify server vẫn có rủi ro kết nối nhầm endpoint.

---

## 11. Row-Level Security

Table privilege trả lời: role có được dùng object không?

RLS trả lời: trong object đó role được thấy hoặc sửa row nào?

Bật RLS:

~~~sql
ALTER TABLE shop.documents ENABLE ROW LEVEL SECURITY;
~~~

Khi RLS bật mà không có policy phù hợp, truy cập bình thường sẽ bị default-deny. RLS không thay thế authentication, schema USAGE, table privilege, column privilege hoặc network security.

Role cần cả GRANT và policy. Có policy nhưng thiếu SELECT/INSERT vẫn bị permission denied.

---

## 12. USING và WITH CHECK

- USING: row hiện tại được nhìn thấy hoặc target bởi SELECT/UPDATE/DELETE;
- WITH CHECK: row mới sau INSERT/UPDATE có được phép không.

Policy multi-tenant:

~~~sql
CREATE POLICY documents_select_policy
ON shop.documents
FOR SELECT TO app_runtime
USING (tenant_id = shop.current_tenant_id());

CREATE POLICY documents_insert_policy
ON shop.documents
FOR INSERT TO app_runtime
WITH CHECK (tenant_id = shop.current_tenant_id());

CREATE POLICY documents_update_policy
ON shop.documents
FOR UPDATE TO app_runtime
USING (tenant_id = shop.current_tenant_id())
WITH CHECK (tenant_id = shop.current_tenant_id());

CREATE POLICY documents_delete_policy
ON shop.documents
FOR DELETE TO app_runtime
USING (tenant_id = shop.current_tenant_id());
~~~

UPDATE cần cả USING và WITH CHECK. Nếu chỉ có USING, user có thể target row tenant A rồi cố đổi tenant_id sang B.

Có thể dùng FOR ALL:

~~~sql
CREATE POLICY documents_runtime_policy
ON shop.documents
FOR ALL TO app_runtime
USING (tenant_id = shop.current_tenant_id())
WITH CHECK (tenant_id = shop.current_tenant_id());
~~~

Tách policy theo command thường dễ review hơn FOR ALL. Khi có nhiều policy, permissive policy thường kết hợp theo OR; restrictive policy kết hợp thêm theo AND. Phải đọc toàn bộ policy, không chỉ một policy.

---

## 13. Mapping role với tenant

Lab dùng role name để mô phỏng identity:

~~~sql
CREATE ROLE tenant_a_user LOGIN PASSWORD 'lab-only-secret-a';
CREATE ROLE tenant_b_user LOGIN PASSWORD 'lab-only-secret-b';

GRANT app_runtime TO tenant_a_user, tenant_b_user;

INSERT INTO shop.tenant_memberships (role_name, tenant_id)
SELECT 'tenant_a_user', tenant_id
FROM shop.tenants
WHERE tenant_name = 'tenant-a'
ON CONFLICT (role_name) DO UPDATE
SET tenant_id = EXCLUDED.tenant_id;

INSERT INTO shop.tenant_memberships (role_name, tenant_id)
SELECT 'tenant_b_user', tenant_id
FROM shop.tenants
WHERE tenant_name = 'tenant-b'
ON CONFLICT (role_name) DO UPDATE
SET tenant_id = EXCLUDED.tenant_id;
~~~

Function lấy tenant:

~~~sql
CREATE OR REPLACE FUNCTION shop.current_tenant_id()
RETURNS bigint
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = shop, pg_temp
AS $$
    SELECT tenant_id
    FROM shop.tenant_memberships
    WHERE role_name = session_user;
$$;

ALTER FUNCTION shop.current_tenant_id() OWNER TO app_owner;
REVOKE ALL ON FUNCTION shop.current_tenant_id() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION shop.current_tenant_id()
TO app_runtime, tenant_a_user, tenant_b_user;

REVOKE ALL ON shop.tenant_memberships
FROM app_runtime, tenant_a_user, tenant_b_user;
~~~

Function SECURITY DEFINER phải có owner tin cậy, search_path khóa và tên bảng rõ ràng. Bên trong SECURITY DEFINER, current_user là owner của function; session_user mới là login mở connection. Nếu một login chung phục vụ nhiều tenant, session_user cũng không đủ để phân biệt tenant. Khi đó cần identity context được application xác thực, thiết lập trong transaction và reset đúng connection pool.

---

## 14. Tạo policy multi-tenant hoàn chỉnh

Cấp quyền table trước:

~~~sql
GRANT USAGE ON SCHEMA shop TO app_runtime;
GRANT SELECT, INSERT, UPDATE, DELETE
ON shop.documents TO app_runtime;
~~~

Bật RLS và policy:

~~~sql
ALTER TABLE shop.documents ENABLE ROW LEVEL SECURITY;

CREATE POLICY documents_select_policy
ON shop.documents
FOR SELECT TO app_runtime
USING (tenant_id = shop.current_tenant_id());

CREATE POLICY documents_insert_policy
ON shop.documents
FOR INSERT TO app_runtime
WITH CHECK (tenant_id = shop.current_tenant_id());

CREATE POLICY documents_update_policy
ON shop.documents
FOR UPDATE TO app_runtime
USING (tenant_id = shop.current_tenant_id())
WITH CHECK (tenant_id = shop.current_tenant_id());

CREATE POLICY documents_delete_policy
ON shop.documents
FOR DELETE TO app_runtime
USING (tenant_id = shop.current_tenant_id());
~~~

Seed dữ liệu bằng owner:

~~~sql
INSERT INTO shop.documents (tenant_id, title, body)
SELECT tenant_id, 'Tài liệu A', 'Nội dung A'
FROM shop.tenants
WHERE tenant_name = 'tenant-a';

INSERT INTO shop.documents (tenant_id, title, body)
SELECT tenant_id, 'Tài liệu B', 'Nội dung B'
FROM shop.tenants
WHERE tenant_name = 'tenant-b';
~~~

---

## 15. Test SELECT, INSERT, UPDATE và DELETE

Mở session bằng tenant_a_user:

~~~bash
psql -h localhost -U tenant_a_user -d lab
~~~

SELECT chỉ thấy tenant A:

~~~sql
SELECT document_id, tenant_id, title
FROM shop.documents
ORDER BY document_id;
~~~

INSERT row tenant A:

~~~sql
INSERT INTO shop.documents (tenant_id, title, body)
SELECT shop.current_tenant_id(), 'A mới', 'Nội dung A mới';
~~~

INSERT tenant B phải bị từ chối:

~~~sql
INSERT INTO shop.documents (tenant_id, title, body)
SELECT tenant_id, 'B giả mạo', 'Không được phép'
FROM shop.tenants
WHERE tenant_name = 'tenant-b';
~~~

UPDATE row A:

~~~sql
UPDATE shop.documents
SET title = 'A đã sửa'
WHERE tenant_id = shop.current_tenant_id();
~~~

UPDATE đổi tenant_id sang B phải bị WITH CHECK chặn:

~~~sql
UPDATE shop.documents
SET tenant_id = (
    SELECT tenant_id
    FROM shop.tenants
    WHERE tenant_name = 'tenant-b'
)
WHERE title = 'A đã sửa';
~~~

DELETE chỉ tác động row tenant A:

~~~sql
DELETE FROM shop.documents
WHERE title = 'A mới';
~~~

Chạy lại bằng tenant_b_user. Kết quả phải đối xứng. RLS có thể làm SELECT trả zero row thay vì lỗi; phân biệt “không thấy row” với “không có table privilege”.

---

## 16. Owner bypass, BYPASSRLS và FORCE RLS

Owner thường bypass RLS. Superuser và role có BYPASSRLS cũng bypass:

~~~sql
SELECT rolname, rolsuper, rolbypassrls
FROM pg_roles
WHERE rolname IN ('app_owner', 'app_runtime', 'api_login');
~~~

Nếu muốn owner cũng chịu policy:

~~~sql
ALTER TABLE shop.documents FORCE ROW LEVEL SECURITY;
~~~

FORCE RLS cần cân nhắc vì migration hoặc maintenance owner có thể bắt đầu thấy zero row hoặc bị chặn. Không dùng app_owner làm role runtime chỉ để “test cho giống production”.

Kiểm tra:

~~~sql
SELECT
    n.nspname AS schema_name,
    c.relname AS table_name,
    c.relrowsecurity AS rls_enabled,
    c.relforcerowsecurity AS rls_forced
FROM pg_class AS c
JOIN pg_namespace AS n ON n.oid = c.relnamespace
WHERE n.nspname = 'shop'
  AND c.relname = 'documents';
~~~

---

## 17. View, materialized view và đường vòng

Không giả định RLS trên table tự động giải quyết mọi truy cập qua view/function. Test bằng role thật:

~~~sql
CREATE VIEW shop.document_report AS
SELECT document_id, tenant_id, title, created_at
FROM shop.documents;

GRANT SELECT ON shop.document_report TO app_reporting;
~~~

Trong PostgreSQL hiện đại có tùy chọn security_invoker cho view:

~~~sql
CREATE VIEW shop.document_report_invoker
WITH (security_invoker = true)
AS
SELECT document_id, tenant_id, title
FROM shop.documents;
~~~

Kiểm tra phiên bản triển khai và semantics cụ thể trước khi dùng. Materialized view lưu kết quả vật lý; quyền đọc và quyền refresh phải được thiết kế riêng. Một view hoặc SECURITY DEFINER function có thể trở thành đường vòng nếu owner có quyền rộng hơn caller.

---

## 18. SECURITY DEFINER và API nâng quyền

SECURITY DEFINER chạy với quyền owner function. Dùng nó như API hẹp, không như cách cấp quyền tổng quát:

~~~sql
CREATE OR REPLACE FUNCTION shop.create_document(
    p_title text,
    p_body text
)
RETURNS bigint
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = shop, pg_temp
AS $$
DECLARE
    v_document_id bigint;
BEGIN
    INSERT INTO shop.documents (tenant_id, title, body, created_by)
    VALUES (shop.current_tenant_id(), p_title, p_body, session_user)
    RETURNING document_id INTO v_document_id;

    RETURN v_document_id;
END;
$$;

ALTER FUNCTION shop.create_document(text, text) OWNER TO app_owner;
REVOKE ALL ON FUNCTION shop.create_document(text, text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION shop.create_document(text, text)
TO app_runtime;
~~~

Checklist:

1. Khóa search_path.
2. Schema-qualify object.
3. Không nối input thành SQL.
4. Validate actor và tenant.
5. Cấp EXECUTE tối thiểu.
6. Owner không là superuser nếu không cần.
7. Biết function có bypass RLS hay không.

Nếu function phải tôn trọng RLS của caller, cân nhắc SECURITY INVOKER hoặc kiểm tra quyền/tenant trong function một cách explicit.

---

## 19. Identity context và connection pool

Hai mô hình phổ biến:

### Một login cho nhiều tenant

Application kết nối bằng api_login, thiết lập context trong transaction:

~~~sql
BEGIN;
SET LOCAL app.tenant_id = '42';
-- query của request
COMMIT;
~~~

Policy có thể đọc:

~~~sql
tenant_id = current_setting('app.tenant_id', true)::bigint
~~~

Không coi giá trị do client gửi là bằng chứng identity. Application phải xác thực user, chọn tenant được phép, dùng SET LOCAL và reset state khi pool trả connection.

### Role riêng theo tenant hoặc service

Policy dùng current_user và bảng mapping như lab. Identity dễ chứng minh hơn ở database nhưng số role/connection có thể lớn.

Quyết định dựa trên authentication, pool, số tenant, chi phí vận hành và yêu cầu compliance. RLS không tự xác thực caller.

---

## 20. Audit quyền và dữ liệu nhạy cảm

Nên audit:

- GRANT/REVOKE và thay đổi membership;
- tạo role, đổi password, BYPASSRLS;
- thay policy;
- thay owner;
- đọc/sửa dữ liệu nhạy cảm;
- tạo SECURITY DEFINER function.

Có thể dùng PostgreSQL logging, pgaudit hoặc hệ thống audit ngoài tùy yêu cầu. Audit không nên chỉ nằm trong bảng mà cùng user có thể xóa.

~~~sql
SELECT
    grantee,
    table_schema,
    table_name,
    privilege_type
FROM information_schema.role_table_grants
WHERE table_schema = 'shop'
ORDER BY grantee, table_name, privilege_type;
~~~

---

## 21. Lab end-to-end

### Bước 1: Bootstrap role

~~~sql
CREATE ROLE app_owner NOLOGIN;
CREATE ROLE app_runtime NOLOGIN;
CREATE ROLE app_readonly NOLOGIN;
CREATE ROLE app_reporting NOLOGIN;

CREATE ROLE tenant_a_user LOGIN PASSWORD 'lab-only-secret-a';
CREATE ROLE tenant_b_user LOGIN PASSWORD 'lab-only-secret-b';

GRANT app_runtime TO tenant_a_user, tenant_b_user;
~~~

### Bước 2: Ownership và schema

~~~sql
ALTER SCHEMA shop OWNER TO app_owner;
ALTER TABLE shop.tenants OWNER TO app_owner;
ALTER TABLE shop.tenant_memberships OWNER TO app_owner;
ALTER TABLE shop.documents OWNER TO app_owner;

REVOKE ALL ON SCHEMA shop FROM PUBLIC;
GRANT USAGE ON SCHEMA shop TO app_runtime, app_readonly, app_reporting;
GRANT CREATE ON SCHEMA shop TO app_owner;
~~~

### Bước 3: Runtime privilege

~~~sql
GRANT SELECT, INSERT, UPDATE, DELETE
ON shop.documents TO app_runtime;

GRANT USAGE, SELECT
ON ALL SEQUENCES IN SCHEMA shop TO app_runtime;

REVOKE TRUNCATE, REFERENCES, TRIGGER
ON shop.documents FROM app_runtime;
~~~

### Bước 4: RLS và policy

Chạy function current_tenant_id, sau đó:

~~~sql
ALTER TABLE shop.documents ENABLE ROW LEVEL SECURITY;

CREATE POLICY documents_select_policy
ON shop.documents
FOR SELECT TO app_runtime
USING (tenant_id = shop.current_tenant_id());

CREATE POLICY documents_insert_policy
ON shop.documents
FOR INSERT TO app_runtime
WITH CHECK (tenant_id = shop.current_tenant_id());

CREATE POLICY documents_update_policy
ON shop.documents
FOR UPDATE TO app_runtime
USING (tenant_id = shop.current_tenant_id())
WITH CHECK (tenant_id = shop.current_tenant_id());

CREATE POLICY documents_delete_policy
ON shop.documents
FOR DELETE TO app_runtime
USING (tenant_id = shop.current_tenant_id());
~~~

### Bước 5: Test hai tenant

~~~bash
psql -h localhost -U tenant_a_user -d lab
~~~

~~~sql
SELECT current_user, shop.current_tenant_id();
SELECT document_id, tenant_id, title FROM shop.documents;
INSERT INTO shop.documents (tenant_id, title, body)
SELECT shop.current_tenant_id(), 'A from runtime', 'body';
~~~

Mở session tenant_b_user và xác nhận không thấy row A, không sửa được row A, nhưng thao tác được row B. Test INSERT tenant A từ B và UPDATE đổi tenant_id.

### Bước 6: Test view và owner

So sánh query bằng tenant user, app_reporting, app_owner và superuser. Ghi lại row count, lỗi permission và policy behavior.

---

## 22. Kiểm thử bảo mật

| Actor | SELECT row mình | SELECT row khác | INSERT row mình | INSERT row khác | UPDATE cross-tenant | DELETE row khác |
|---|---:|---:|---:|---:|---:|---:|
| tenant_a_user | cho phép | zero row/từ chối | cho phép | từ chối | từ chối | từ chối |
| tenant_b_user | cho phép | zero row/từ chối | cho phép | từ chối | từ chối | từ chối |
| app_readonly | theo policy | theo policy | từ chối | từ chối | từ chối | từ chối |
| app_owner | tùy FORCE RLS | tùy FORCE RLS | tùy | tùy | tùy | tùy |
| superuser | bypass | bypass | bypass | bypass | bypass | bypass |

Test tối thiểu:

1. thiếu USAGE schema;
2. có schema nhưng thiếu table SELECT;
3. có table privilege nhưng không có policy;
4. có policy nhưng không có table privilege;
5. owner query;
6. BYPASSRLS query;
7. view query;
8. SECURITY DEFINER function;
9. UPDATE đổi tenant_id;
10. connection pool context cũ.

Thử role thật bằng connection mới. SET ROLE chỉ có ý nghĩa nếu caller được phép SET sang role đó.

---

## 23. Các lỗi thường gặp

### Dùng superuser cho application

Superuser bypass gần như toàn bộ kiểm soát và làm test quyền sai lệch.

### Quên USAGE schema

Có SELECT table nhưng thiếu USAGE schema vẫn không truy cập được qua tên schema.

### Chỉ bật RLS nhưng quên GRANT

RLS không cấp table privilege.

### Quên sequence

INSERT table identity/serial có thể lỗi trên sequence. Test bằng runtime role.

### Nghĩ REVOKE khóa owner

Owner vẫn quản trị object và thường bypass RLS. Tách owner khỏi runtime.

### Quên default privileges

Object mới có ACL khác object cũ. Default privileges phải thuộc đúng role tạo object.

### Tin tenant_id từ client

Request input không phải identity. Dùng role mapping hoặc context được xác thực.

### Không reset connection pool

Context request trước có thể rò sang request sau. Dùng SET LOCAL và reset session state.

### View hoặc SECURITY DEFINER làm lộ dữ liệu

Test table chưa đủ; phải test mọi entry point.

### Thiếu WITH CHECK

User có thể chuyển row hợp lệ sang tenant khác nếu UPDATE policy chỉ có USING.

### Cấp CREATE schema cho runtime

Runtime có thể tạo object hoặc lợi dụng search_path. Chỉ migration owner nên có CREATE.

---

## 24. Migration quyền và rollback

Migration quyền được review như code:

~~~sql
BEGIN;

REVOKE ALL ON SCHEMA shop FROM PUBLIC;
GRANT USAGE ON SCHEMA shop TO app_runtime;

GRANT SELECT, INSERT, UPDATE, DELETE
ON shop.documents TO app_runtime;

REVOKE TRUNCATE, REFERENCES, TRIGGER
ON shop.documents FROM app_runtime;

ALTER TABLE shop.documents ENABLE ROW LEVEL SECURITY;

COMMIT;
~~~

Rollback cần biết policy nào tạo, GRANT nào cần REVOKE, owner cũ là ai và default privilege cũ ra sao. Không chạy REVOKE rộng trên production khi chưa snapshot ACL.

---

## 25. Checklist review

### Role

- Application có dùng superuser hoặc owner không?
- Login có BYPASSRLS, CREATEDB, CREATEROLE không?
- Group role có NOLOGIN không?
- Membership và ADMIN OPTION có tối thiểu không?
- Có tách migration, runtime, readonly, reporting không?

### Object privilege

- Schema có USAGE đúng role không?
- Runtime có CREATE schema không?
- Table có thừa DELETE, TRUNCATE, TRIGGER không?
- Sequence đã test chưa?
- Function EXECUTE trên PUBLIC đã kiểm tra chưa?
- Default privileges thuộc đúng role tạo object chưa?

### RLS

- RLS đã ENABLE chưa?
- Policy đúng role và command chưa?
- SELECT/UPDATE/DELETE có USING chưa?
- INSERT/UPDATE có WITH CHECK chưa?
- Owner, superuser, BYPASSRLS đã test chưa?
- View/function/materialized view có đường vòng không?
- Tenant context có đáng tin cậy và reset đúng không?

### Vận hành

- TLS có verify server không?
- Secret có ở ngoài Git không?
- Có audit role/policy không?
- Có rollback migration không?
- CI/staging có test bằng role thật không?

---

## 26. Bài tập thực hành

### Bài 1 — Thiết kế role

Tạo app_owner, app_runtime, app_readonly, app_reporting và login role. Viết bảng quyền, sau đó chứng minh bằng has_table_privilege và session thật.

### Bài 2 — Default privileges

Tạo table/function mới bằng app_owner. Kiểm tra quyền tự động của app_runtime và EXECUTE của PUBLIC.

### Bài 3 — Multi-tenant RLS

Tạo tenant A/B, role mapping và policy đủ SELECT/INSERT/UPDATE/DELETE. Test bốn thao tác bằng hai login.

### Bài 4 — WITH CHECK

Test UPDATE đổi tenant_id. Tạm bỏ WITH CHECK trong lab để quan sát rủi ro, sau đó khôi phục policy.

### Bài 5 — Owner bypass

Đếm row bằng app_owner, tenant user và superuser. Bật FORCE RLS rồi chạy lại.

### Bài 6 — View leakage

Tạo view thường và view security_invoker. Test bằng reporting, tenant user và owner.

### Bài 7 — Connection pool

Mô phỏng request A set tenant A, request B dùng lại session mà không reset. Sửa bằng SET LOCAL trong transaction.

### Bài 8 — Security report

Báo cáo role graph, ACL, policy, identity flow, owner/BYPASSRLS behavior, TLS mode và vấn đề còn lại.

---

## 27. Tiêu chí hoàn thành Tuần 12

Bạn hoàn thành bài khi có thể:

- phân biệt login role, group role, owner và current_user;
- giải thích INHERIT, NOINHERIT, SET ROLE và ADMIN OPTION;
- thiết kế role least privilege;
- cấp quyền schema, table, column, sequence và function;
- hiểu owner không giống GRANT thông thường;
- cấu hình và kiểm tra default privileges;
- kiểm soát search_path và schema public;
- giải thích TLS, sslmode và verify-full;
- bật RLS và viết policy USING/WITH CHECK;
- phân biệt policy SELECT/INSERT/UPDATE/DELETE;
- kiểm tra owner bypass, FORCE RLS, superuser và BYPASSRLS;
- xây policy multi-tenant từ identity context đáng tin cậy;
- test table, view và function bằng role thật;
- phát hiện đường vòng qua SECURITY DEFINER và view;
- tạo migration quyền có rollback và audit.

---

## Tóm tắt cần nhớ

1. Application không dùng superuser hoặc owner.
2. Group role nên NOLOGIN; login role nhận membership tối thiểu.
3. Schema, table, sequence và function là các lớp privilege riêng.
4. Default privileges chỉ áp dụng cho object tạo sau và gắn với role tạo object.
5. Owner có quyền đặc biệt; REVOKE không biến owner thành runtime user.
6. search_path phải được kiểm soát, nhất là trong SECURITY DEFINER.
7. RLS lọc row nhưng không thay authentication hoặc table privilege.
8. USING kiểm soát row hiện tại; WITH CHECK kiểm soát row sau INSERT/UPDATE.
9. Owner, superuser và BYPASSRLS có thể làm test RLS sai lệch.
10. tenant_id do client gửi không phải bằng chứng identity.
11. View và SECURITY DEFINER function phải được test như entry point riêng.
12. Quyền database cần được kiểm thử bằng role thật, audit và rollback.

# Tuần 6 nâng cao: Full-text search và generated data

> Mục tiêu: xây full-text search bằng tsvector và tsquery, tạo search vector từ nhiều field, dùng GIN index, tìm kiếm bằng websearch_to_tsquery, xếp hạng kết quả với ts_rank, chọn text search configuration/dictionary và dùng generated column hoặc expression index.

Full-text search không chỉ là tìm chuỗi con. Nó cần xử lý:

~~~text
Tách văn bản thành token.
Chuẩn hóa token.
Loại stop word.
Đưa từ về lexeme phù hợp.
Tìm nhiều từ theo logic.
Xếp hạng kết quả.
Index để query lớn không phải đọc toàn bộ table.
~~~

Trong PostgreSQL, pipeline cơ bản:

~~~text
text
  → to_tsvector(configuration, text)
  → tsvector
  → tsquery
  → tsvector @@ tsquery
  → ts_rank
~~~

---

## 1. Full-text search khác ILIKE thế nào?

ILIKE tìm pattern text:

~~~sql
SELECT id, title
FROM books
WHERE title ILIKE '%postgres%';
~~~

ILIKE phù hợp với:

- tìm substring đơn giản;
- autocomplete nhỏ;
- dữ liệu ít;
- tìm đúng chuỗi hiển thị.

Nhược điểm:

- không hiểu từ;
- không ranking theo mức liên quan;
- wildcard đầu thường khó dùng B-tree;
- khó xử lý stemming, stop word và nhiều từ;
- query lớn có thể phải scan nhiều row.

Full-text search chuyển text thành tsvector và query thành tsquery:

~~~sql
SELECT id, title
FROM books
WHERE search_vector @@ plainto_tsquery('simple', 'postgres database');
~~~

Full-text search phù hợp với:

- tìm nhiều từ;
- AND/OR/phrase;
- stemming;
- ranking;
- index GIN;
- văn bản dài.

Hai cách không loại trừ nhau. ILIKE có thể phù hợp cho prefix/autocomplete; full-text search phù hợp cho search nội dung.

---

## 2. tsvector

tsvector là dạng chuẩn hóa của document để tìm kiếm.

~~~sql
SELECT to_tsvector(
    'english',
    'PostgreSQL is a powerful relational database'
);
~~~

Kết quả có thể chứa lexeme và vị trí:

~~~text
'database':6 'power':4 'postgresql':1 'relat':5
~~~

Tùy configuration, database có thể stem relational thành relat và bỏ stop word is, a.

Xem tsvector từ nhiều field:

~~~sql
SELECT to_tsvector(
    'english',
    coalesce(title, '') || ' ' || coalesce(description, '')
)
FROM books;
~~~

coalesce quan trọng vì nối chuỗi với NULL có thể làm cả expression thành NULL.

tsvector không phải text hiển thị cho người dùng. Nó là cấu trúc phục vụ search và ranking.

---

## 3. tsquery

tsquery biểu diễn điều kiện tìm kiếm.

Tạo query trực tiếp:

~~~sql
SELECT to_tsquery('english', 'postgres & database');
~~~

Toán tử thường dùng:

| Operator | Ý nghĩa |
|---|---|
| & | AND |
| | | OR |
| ! | NOT |
| <-> | đứng cạnh nhau |
| <N> | cách nhau N vị trí |

Dấu ngoặc điều khiển precedence:

~~~sql
SELECT to_tsquery(
    'english',
    '(postgres | mysql) & database'
);
~~~

Kết nối tsvector với tsquery:

~~~sql
SELECT to_tsvector(
    'english',
    'PostgreSQL database'
) @@ to_tsquery(
    'english',
    'postgres & database'
);
~~~

Kết quả là true hoặc false.

Không đưa raw input của người dùng trực tiếp vào to_tsquery nếu input có ký tự query operator mà bạn không muốn người dùng điều khiển. Với input tìm kiếm tự nhiên, dùng plainto_tsquery hoặc websearch_to_tsquery.

---

## 4. plainto_tsquery

plainto_tsquery chuyển text tự nhiên thành các term kết hợp bằng AND:

~~~sql
SELECT plainto_tsquery(
    'english',
    'postgres database'
);
~~~

Phù hợp khi người dùng nhập từ khóa đơn giản và không cần cú pháp search nâng cao.

Query:

~~~sql
SELECT id, title
FROM books
WHERE search_vector @@ plainto_tsquery(
    'english',
    'postgres database'
);
~~~

plainto_tsquery xử lý input an toàn hơn to_tsquery vì không coi raw syntax của user là một query language đầy đủ.

---

## 5. websearch_to_tsquery

websearch_to_tsquery hỗ trợ cú pháp gần với search engine:

~~~sql
SELECT websearch_to_tsquery(
    'english',
    'postgres "query planner" -mysql'
);
~~~

Ý nghĩa thường gặp:

- từ cách nhau: AND;
- dấu ngoặc kép: phrase;
- dấu trừ: NOT;
- OR: alternative.

Query ứng dụng:

~~~sql
SELECT
    id,
    title,
    description
FROM books
WHERE search_vector @@ websearch_to_tsquery(
    'english',
    $1
);
~~~

websearch_to_tsquery phù hợp cho input trực tiếp từ ô tìm kiếm. Vẫn cần giới hạn độ dài input, xử lý input rỗng và rate limit endpoint.

---

## 6. Text search configuration

Configuration quyết định:

- parser;
- dictionary;
- stop word;
- stemming;
- ngôn ngữ.

Xem cấu hình:

~~~sql
SHOW default_text_search_config;
~~~

Liệt kê configuration:

~~~sql
SELECT cfgname
FROM pg_ts_config;
~~~

Xem dictionary:

~~~sql
SELECT dictname, dicttemplate
FROM pg_ts_dict;
~~~

Một số cấu hình phổ biến:

~~~sql
SELECT to_tsvector('simple', 'PostgreSQL databases');
SELECT to_tsvector('english', 'PostgreSQL databases');
~~~

simple thường không stemming nhiều; english dùng dictionary và stop word tiếng Anh.

Không chọn english cho dữ liệu tiếng Việt rồi kỳ vọng stemming tiếng Việt đúng. Cần kiểm tra configuration, extension hoặc search service phù hợp với ngôn ngữ dữ liệu.

Nếu database chứa nhiều ngôn ngữ, có thể:

- lưu language code;
- tạo search vector theo configuration tương ứng;
- tách index/search field theo ngôn ngữ;
- dùng search engine chuyên dụng nếu yêu cầu phức tạp.

---

## 7. Dictionary và stemming

Dictionary nhận token và biến đổi thành lexeme, đồng thời có thể loại stop word.

Ví dụ với english:

~~~sql
SELECT to_tsvector(
    'english',
    'connect connected connecting connection'
);
~~~

Các từ có thể được đưa về dạng gần nhau tùy stemming dictionary.

Stemming giúp tìm các biến thể từ nhưng có thể tạo false positive hoặc false negative. Search quality cần test bằng bộ query thật, không chỉ nhìn một output tsvector.

Stop word như the, is, a có thể bị bỏ. Nếu domain cần tìm chính xác một từ thường bị coi là stop word, cần cấu hình dictionary phù hợp.

---

## 8. Search vector từ title và description

Thiết kế table:

~~~sql
CREATE TABLE books (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    title text NOT NULL,
    description text,
    category text,
    created_at timestamptz NOT NULL DEFAULT now()
);
~~~

Tạo search vector trong query:

~~~sql
SELECT
    id,
    title
FROM books
WHERE to_tsvector(
    'english',
    coalesce(title, '') || ' ' || coalesce(description, '')
) @@ websearch_to_tsquery(
    'english',
    'postgres database'
);
~~~

Cách này đúng nhưng mỗi query phải tính tsvector. Với table lớn hoặc search thường xuyên, dùng generated column hoặc expression index.

---

## 9. Generated column cho tsvector

Generated column lưu search vector được database tính từ title và description:

~~~sql
ALTER TABLE books
ADD COLUMN search_vector tsvector
GENERATED ALWAYS AS (
    to_tsvector(
        'english',
        coalesce(title, '') || ' ' || coalesce(description, '')
    )
) STORED;
~~~

Tạo GIN index:

~~~sql
CREATE INDEX books_search_vector_gin_idx
ON books USING GIN (search_vector);
~~~

Query:

~~~sql
SELECT id, title
FROM books
WHERE search_vector @@ websearch_to_tsquery(
    'english',
    'postgres database'
);
~~~

Lợi ích:

- expression chỉ được duy trì khi input thay đổi;
- query ngắn và dễ đọc;
- index dùng cùng một representation;
- giảm rủi ro query và index dùng expression khác nhau.

Trade-off:

- table và index lớn hơn;
- UPDATE title hoặc description phải cập nhật search_vector;
- đổi configuration cần migration/rebuild;
- search vector không phải text hiển thị.

---

## 10. Trọng số title và description

Title thường quan trọng hơn description. setweight gán trọng số:

~~~sql
ALTER TABLE books
ADD COLUMN search_vector tsvector
GENERATED ALWAYS AS (
    setweight(
        to_tsvector('english', coalesce(title, '')),
        'A'
    )
    ||
    setweight(
        to_tsvector('english', coalesce(description, '')),
        'B'
    )
) STORED;
~~~

Các weight thường là A, B, C, D. A có priority cao hơn.

Có thể thêm category:

~~~sql
setweight(
    to_tsvector('simple', coalesce(category, '')),
    'A'
)
~~~

Không dùng configuration khác nhau tùy tiện trong cùng vector. Nếu category là code ổn định, configuration simple có thể phù hợp hơn.

---

## 11. GIN full-text index

Tạo index:

~~~sql
CREATE INDEX books_search_vector_gin_idx
ON books USING GIN (search_vector);
~~~

Query search:

~~~sql
SELECT
    id,
    title
FROM books
WHERE search_vector @@ websearch_to_tsquery(
    'english',
    'postgres database'
);
~~~

Đo plan:

~~~sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT
    id,
    title
FROM books
WHERE search_vector @@ websearch_to_tsquery(
    'english',
    'postgres database'
);
~~~

Có thể thấy Bitmap Index Scan và Bitmap Heap Scan.

GIN phù hợp để kiểm tra match tsvector, nhưng:

- index có thể lớn;
- insert/update text tốn hơn;
- pending list và vacuum ảnh hưởng write;
- search vector thay đổi thì index phải maintain.

Không tạo GIN nếu table nhỏ hoặc search không đủ thường xuyên.

---

## 12. Expression index

Nếu không muốn thêm generated column, tạo expression index:

~~~sql
CREATE INDEX books_search_expression_gin_idx
ON books USING GIN (
    to_tsvector(
        'english',
        coalesce(title, '') || ' ' || coalesce(description, '')
    )
);
~~~

Query phải dùng expression tương thích:

~~~sql
SELECT id, title
FROM books
WHERE to_tsvector(
    'english',
    coalesce(title, '') || ' ' || coalesce(description, '')
) @@ websearch_to_tsquery(
    'english',
    'postgres database'
);
~~~

Expression index phù hợp khi:

- expression đơn giản và ổn định;
- không cần select search_vector;
- không muốn thêm column;
- migration có thể giữ expression nhất quán.

Generated column thường dễ đọc hơn khi nhiều query, ranking, debugging hoặc nhiều field/weight.

---

## 13. ts_rank

ts_rank xếp hạng document theo mức phù hợp:

~~~sql
SELECT
    id,
    title,
    ts_rank(
        search_vector,
        websearch_to_tsquery('english', 'postgres database')
    ) AS rank
FROM books
WHERE search_vector @@ websearch_to_tsquery(
    'english',
    'postgres database'
)
ORDER BY rank DESC, id;
~~~

Không nên gọi cùng một tsquery nhiều lần nếu query phức tạp. Dùng CTE hoặc LATERAL:

~~~sql
WITH query AS (
    SELECT websearch_to_tsquery(
        'english',
        'postgres database'
    ) AS tsq
)
SELECT
    b.id,
    b.title,
    ts_rank(b.search_vector, query.tsq) AS rank
FROM books AS b
CROSS JOIN query
WHERE b.search_vector @@ query.tsq
ORDER BY rank DESC, b.id;
~~~

Ranking chỉ có ý nghĩa trong tập result match. Nó không tự bảo đảm search quality theo business.

---

## 14. ts_rank_cd

ts_rank_cd tính cover density, xem xét mức gần nhau của lexeme:

~~~sql
SELECT
    id,
    title,
    ts_rank_cd(
        search_vector,
        websearch_to_tsquery('english', 'postgres database')
    ) AS rank
FROM books
WHERE search_vector @@ websearch_to_tsquery(
    'english',
    'postgres database'
)
ORDER BY rank DESC;
~~~

ts_rank phù hợp với nhiều trường hợp đơn giản. ts_rank_cd có thể hữu ích khi độ gần của term quan trọng. Đo và đánh giá kết quả bằng query thực tế.

---

## 15. Highlight kết quả

ts_headline tạo đoạn text có highlight:

~~~sql
SELECT
    id,
    title,
    ts_headline(
        'english',
        coalesce(description, ''),
        websearch_to_tsquery('english', 'postgres database'),
        'StartSel=<mark>, StopSel=</mark>, MaxWords=35'
    ) AS snippet
FROM books
WHERE search_vector @@ websearch_to_tsquery(
    'english',
    'postgres database'
)
ORDER BY id;
~~~

Đầu ra có thể chứa HTML. Không render trực tiếp dữ liệu chưa được kiểm soát nếu frontend có nguy cơ XSS. Sanitize hoặc escape theo context hiển thị.

ts_headline có thể tốn CPU; chỉ chạy trên result page nhỏ, không chạy toàn bộ table.

---

## 16. Phrase search và prefix

Phrase search qua websearch:

~~~sql
SELECT id, title
FROM books
WHERE search_vector @@ websearch_to_tsquery(
    'english',
    '"query planner"'
);
~~~

Prefix query bằng to_tsquery:

~~~sql
SELECT id, title
FROM books
WHERE search_vector @@ to_tsquery(
    'english',
    'post:*'
);
~~~

Không lấy raw prefix từ user mà không giới hạn độ dài. Prefix quá ngắn hoặc quá phổ biến có thể trả rất nhiều row và gây tải lớn.

---

## 17. Fuzzy search và full-text search

Full-text search không thay thế mọi fuzzy search.

| Nhu cầu | Công cụ |
|---|---|
| Từ và biến thể từ | Full-text search |
| Phrase | tsquery/websearch |
| Tìm prefix token | prefix tsquery |
| Typo, similarity | pg_trgm |
| Exact substring | ILIKE hoặc trigram |
| Search nhiều ngôn ngữ phức tạp | search engine chuyên dụng |

Có thể kết hợp full-text search và trigram, nhưng mỗi index tăng write/storage. Đo query và đặt fallback hợp lý.

---

## 18. Generated data là gì trong bài này?

Generated data là dữ liệu database tự tính từ source column, không phải dữ liệu application tự nhân bản.

Các dạng dùng trong search:

- generated tsvector;
- generated normalized text;
- expression index;
- materialized view cho search/reporting ở mức lớn hơn.

Ví dụ generated normalized text:

~~~sql
ALTER TABLE books
ADD COLUMN normalized_title text
GENERATED ALWAYS AS (
    lower(trim(title))
) STORED;
~~~

Index:

~~~sql
CREATE INDEX books_normalized_title_idx
ON books (normalized_title);
~~~

Generated data phù hợp khi công thức deterministic và cần nhất quán. Không dùng generated column cho dữ liệu phụ thuộc current time hoặc external service.

---

## 19. Generated column và configuration migration

Search vector phụ thuộc text search configuration. Nếu đổi từ simple sang english, hoặc đổi dictionary:

1. tạo migration;
2. cập nhật generated expression;
3. rebuild generated data;
4. rebuild hoặc validate GIN index;
5. benchmark query;
6. kiểm tra kết quả search cũ và mới.

Không đổi configuration âm thầm vì cùng một text có thể tạo lexeme khác, khiến kết quả search thay đổi.

Nếu mỗi row có language khác nhau, một generated column duy nhất có thể không đủ. Có thể:

- dùng configuration theo language ở application;
- tạo vector riêng theo language;
- dùng trigger/expression phù hợp;
- tách index theo workload.

---

## 20. Accent và collation ở mức khái niệm

Full-text search, lower, collation và accent là các vấn đề khác nhau:

- lower xử lý hoa thường;
- collation ảnh hưởng sort/comparison;
- unaccent loại hoặc chuyển đổi dấu nếu extension/configuration hỗ trợ;
- text search dictionary xử lý token và stemming.

Nếu cần search không phân biệt dấu:

~~~sql
CREATE EXTENSION IF NOT EXISTS unaccent;
~~~

Có thể dùng unaccent trong expression, nhưng function dùng trong index phải phù hợp yêu cầu immutability và configuration. Hãy kiểm tra trên phiên bản PostgreSQL thật trước khi tạo index production.

Thiết kế rõ:

~~~text
Search behavior mong muốn
→ normalization
→ text search configuration
→ generated vector hoặc expression index
→ test query tiếng Việt/ngôn ngữ thực tế
~~~

Đừng coi collation là giải pháp cho stemming hoặc full-text ranking.

---

## 21. Search nhiều field và trọng số

Schema:

~~~sql
CREATE TABLE articles (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    title text NOT NULL,
    summary text,
    body text,
    tags text[],
    search_vector tsvector
        GENERATED ALWAYS AS (
            setweight(
                to_tsvector('english', coalesce(title, '')),
                'A'
            )
            ||
            setweight(
                to_tsvector('english', coalesce(summary, '')),
                'B'
            )
            ||
            setweight(
                to_tsvector('english', coalesce(body, '')),
                'C'
            )
        ) STORED
);

CREATE INDEX articles_search_vector_gin_idx
ON articles USING GIN (search_vector);
~~~

Search và rank:

~~~sql
WITH query AS (
    SELECT websearch_to_tsquery(
        'english',
        'database planner'
    ) AS tsq
)
SELECT
    a.id,
    a.title,
    ts_rank(a.search_vector, query.tsq) AS rank
FROM articles AS a
CROSS JOIN query
WHERE a.search_vector @@ query.tsq
ORDER BY rank DESC, a.id;
~~~

Tags có thể được đưa vào vector bằng cách nối text hoặc xử lý riêng. Nếu tags cần filter exact và foreign key, dùng bảng tag quan hệ.

---

## 22. Query plan và benchmark

Baseline không có index:

~~~sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT id, title
FROM books
WHERE to_tsvector(
    'english',
    coalesce(title, '') || ' ' || coalesce(description, '')
) @@ websearch_to_tsquery(
    'english',
    'postgres database'
);
~~~

Sau generated column và GIN:

~~~sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT id, title
FROM books
WHERE search_vector @@ websearch_to_tsquery(
    'english',
    'postgres database'
);
~~~

Ghi:

- Seq Scan trước index;
- Bitmap Index Scan sau GIN;
- estimated/actual rows;
- execution time;
- shared hit/read;
- index size;
- thời gian insert/update text.

Nếu table nhỏ, planner có thể vẫn chọn Seq Scan. Đây không chứng minh GIN sai.

---

## 23. Search input và an toàn

Endpoint search cần:

- giới hạn độ dài query;
- xử lý input rỗng;
- giới hạn page size;
- parameterized query;
- tránh raw to_tsquery nếu không muốn user điều khiển operator;
- timeout;
- rate limit;
- sanitize snippet khi render HTML.

Dùng:

~~~sql
websearch_to_tsquery('english', $1)
~~~

thay vì nối chuỗi user input vào SQL.

Nếu sản phẩm muốn hỗ trợ cú pháp nâng cao như AND/OR/NOT, phải document grammar, parse/validate và giới hạn độ phức tạp.

---

## 24. Migration từ ILIKE sang full-text search

### Bước 1: đo query hiện tại

~~~sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT id, title
FROM books
WHERE title ILIKE '%postgres%';
~~~

### Bước 2: tạo search vector

~~~sql
ALTER TABLE books
ADD COLUMN search_vector tsvector
GENERATED ALWAYS AS (
    to_tsvector(
        'english',
        coalesce(title, '') || ' ' || coalesce(description, '')
    )
) STORED;
~~~

### Bước 3: tạo GIN index

~~~sql
CREATE INDEX CONCURRENTLY books_search_vector_gin_idx
ON books USING GIN (search_vector);
~~~

### Bước 4: test result quality

So sánh:

- query cũ;
- query full-text;
- query phrase;
- query stemming;
- query không dấu nếu cần;
- ranking;
- kết quả kỳ vọng của product.

### Bước 5: chuyển application

Dùng feature flag hoặc rollout từng phần. Giữ fallback trong thời gian cần thiết.

### Bước 6: review index cũ

Không xóa index ILIKE/trigram nếu còn endpoint khác dùng. Xác định workload trước khi cleanup.

---

## 25. Lỗi thường gặp

### Dùng to_tsquery cho raw user input

Ký tự đặc biệt có thể làm lỗi hoặc thay đổi ý nghĩa query. Dùng websearch_to_tsquery hoặc parse input rõ ràng.

### Dùng sai configuration

English configuration không tự giải quyết search tiếng Việt. Test ngôn ngữ thật.

### Không dùng coalesce

NULL trong title/description có thể làm expression tsvector thành NULL.

### Không gán trọng số

Title, body và tag có giá trị relevance khác nhau. setweight giúp ranking thể hiện ý định.

### Tạo GIN nhưng không đo

GIN làm write và storage tốn hơn. Dùng EXPLAIN và benchmark.

### Gọi ts_headline cho toàn table

Highlight tốn CPU. Chỉ chạy trên page result nhỏ.

### Đưa field quan hệ vào JSONB

customer_id, order status, amount và book_id cần column/foreign key khi là dữ liệu nghiệp vụ.

### Generated expression không nhất quán

Index expression và query expression phải tương thích. Generated column giúp tránh lặp expression.

### Chỉ benchmark với table nhỏ

Planner có thể chọn Seq Scan đúng trên table nhỏ. Test kích thước gần production.

---

## 26. Lab tổng hợp

Tạo bảng books:

~~~sql
CREATE TABLE books (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    title text NOT NULL,
    description text,
    category text,
    search_vector tsvector
        GENERATED ALWAYS AS (
            setweight(
                to_tsvector('english', coalesce(title, '')),
                'A'
            )
            ||
            setweight(
                to_tsvector('english', coalesce(description, '')),
                'B'
            )
        ) STORED
);

CREATE INDEX books_search_vector_gin_idx
ON books USING GIN (search_vector);
~~~

Thực hành:

1. Insert book có title và description.
2. Search bằng plainto_tsquery.
3. Search bằng websearch_to_tsquery.
4. Test phrase search.
5. Test prefix search.
6. Tính rank bằng ts_rank.
7. Tạo snippet bằng ts_headline.
8. So sánh English và Simple configuration.
9. Thêm 100.000 book test.
10. Đo Seq Scan trước index và GIN sau index.
11. Cập nhật title và đo write cost.
12. Thử expression index thay generated column.
13. Ghi result quality và execution metrics.

---

## 27. Bài tập thực hành

### Bài 1 — tsvector

Chuyển title và description thành tsvector bằng configuration simple và english, so sánh output.

### Bài 2 — tsquery

Viết query AND, OR, NOT và phrase.

### Bài 3 — Web search

Nhận input tự nhiên, dùng websearch_to_tsquery và xử lý input rỗng.

### Bài 4 — Search nhiều field

Tạo vector từ title, summary và body với trọng số A, B, C.

### Bài 5 — GIN

Tạo GIN index, chạy EXPLAIN ANALYZE trước/sau và ghi scan node.

### Bài 6 — Ranking

Dùng ts_rank và ts_rank_cd; so sánh thứ tự kết quả.

### Bài 7 — Highlight

Dùng ts_headline tạo snippet và xử lý an toàn khi render HTML.

### Bài 8 — Generated column

Tạo search_vector generated column, sửa title và kiểm tra vector tự cập nhật.

### Bài 9 — Expression index

Viết expression index tương đương và phân tích trade-off với generated column.

### Bài 10 — Accent/language

Kiểm tra dữ liệu tiếng Việt hoặc dữ liệu có dấu; ghi rõ behavior mong muốn và configuration được chọn.

### Bài 11 — Migration

Lập kế hoạch chuyển một endpoint ILIKE sang full-text search, gồm rollout, fallback và cleanup index.

---

## 28. Tiêu chí hoàn thành Tuần 6

Bạn hoàn thành bài học khi có thể:

- Phân biệt JSON text, tsvector và tsquery.
- Tạo tsvector từ một hoặc nhiều field.
- Dùng to_tsvector, to_tsquery, plainto_tsquery và websearch_to_tsquery.
- Dùng AND, OR, NOT và phrase search.
- Chọn text search configuration phù hợp với dữ liệu.
- Hiểu vai trò của dictionary, stop word và stemming.
- Tạo GIN full-text index.
- Xếp hạng bằng ts_rank hoặc ts_rank_cd.
- Tạo snippet bằng ts_headline an toàn.
- Dùng generated column hoặc expression index.
- Gán trọng số title, summary và body.
- So sánh full-text search với ILIKE và pg_trgm.
- Hiểu giới hạn của collation và accent normalization.
- Thiết kế search input an toàn và có giới hạn.
- Đo query và write cost trước/sau index.

## Tóm tắt cần nhớ

~~~text
tsvector là representation của document; tsquery là điều kiện tìm kiếm.
@@ kiểm tra tsvector có match tsquery.
plainto_tsquery phù hợp input từ khóa đơn giản.
websearch_to_tsquery phù hợp ô search tự nhiên.
to_tsquery chỉ dùng khi đã parse và kiểm soát syntax.
GIN là index chính cho full-text search.
ts_rank xếp hạng; ts_headline tạo snippet.
Generated column giữ search vector nhất quán với source text.
Title và body có thể cần trọng số khác nhau.
Configuration và dictionary quyết định quality của search.
Full-text search không thay thế mọi fuzzy search hoặc substring search.
Field nghiệp vụ ổn định nên là column quan hệ, không phải search payload.
~~~

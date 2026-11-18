# Các trường hợp sử dụng (Use cases)

> Nguồn: <https://redis.io/docs/latest/develop/data-types/json/use_cases/>

Tất nhiên bạn có thể dùng các cấu trúc dữ liệu gốc (native) của Redis để lưu các JSON object, và đó là một cách làm phổ biến. Ví dụ, bạn có thể serialize JSON và lưu nó trong một Redis String.

Tuy nhiên, Redis JSON mang lại một số lợi ích so với cách tiếp cận này.

**Truy cập và truy xuất các giá trị con (Access and retrieval of subvalues)**

Với JSON, bạn có thể lấy các giá trị lồng nhau (nested) mà không phải truyền toàn bộ object qua mạng. Khả năng truy cập các object con có thể mang lại hiệu quả cao hơn khi bạn lưu các JSON object lớn trong Redis.

**Cập nhật một phần một cách nguyên tử (Atomic partial updates)**

JSON cho phép bạn chạy một cách nguyên tử (atomically) các thao tác như tăng một giá trị, thêm hoặc xóa phần tử khỏi một mảng, nối thêm (append) string, v.v. Để làm điều tương tự với một object đã serialize, bạn phải lấy về rồi serialize lại toàn bộ object, việc này có thể tốn kém và cũng thiếu tính nguyên tử.

**Đánh index và truy vấn (Indexing and querying)**

Khi bạn lưu các JSON object dưới dạng Redis string, không có cách nào tốt để truy vấn các object đó. Ngược lại, lưu các object này dưới dạng JSON cho phép bạn đánh index và truy vấn chúng. Khả năng này được cung cấp bởi Redis Search.

Với tùy chọn `FPHA` của lệnh [`JSON.SET`](https://redis.io/docs/latest/commands/json.set), Redis cung cấp cho người dùng một cách linh hoạt để lưu các vector số thực dấu phẩy động (floating point vector) phục vụ cho vector search.

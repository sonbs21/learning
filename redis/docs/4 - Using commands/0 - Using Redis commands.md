# Sử dụng các lệnh Redis (Using Redis commands)

> Nguồn: <https://redis.io/docs/latest/develop/using-commands/>
> Tìm hiểu cách tương tác với Redis bằng các lệnh.

Các ứng dụng client và các công cụ tương tác với Redis bằng các lệnh (command). Phần lớn
các lệnh hiện thực các [kiểu dữ liệu](https://redis.io/docs/latest/develop/data-types) để lưu trữ và truy xuất dữ liệu,
nhưng cũng có những lệnh xử lý cấu hình server, bảo mật, và nhiều thứ khác.

Các mục bên dưới đưa ra cái nhìn tổng quan về cách các lệnh Redis hoạt động. Xem
[Redis commands reference](https://redis.io/commands) để có danh sách đầy đủ các lệnh.

## Cấu trúc lệnh (Command structure)

Mỗi lệnh được xác định bởi một tên duy nhất. Các nhóm lệnh có liên quan với nhau
thường tuân theo một quy ước đặt tên nhất quán. Ví dụ, tất cả các lệnh
làm việc với [hash](https://redis.io/docs/latest/develop/data-types/hashes) đều bắt đầu bằng tiền tố `H`.
Hầu hết các lệnh nhận một hoặc nhiều đối số (argument) chỉ định dữ liệu cần thao tác.
Với các lệnh của kiểu dữ liệu, đối số đầu tiên thường là [key](https://redis.io/docs/latest/develop/using-commands/keyspace) xác định đối tượng dữ liệu đích.

Sau khi bạn gửi một lệnh, server sẽ cố gắng xử lý nó rồi trả về
một phản hồi (response). Các lệnh cập nhật dữ liệu thường trả về một thông báo trạng thái (chẳng hạn `OK`)
hoặc một con số cho biết số lượng phần tử đã được thay đổi hoặc cập nhật. Các lệnh
truy xuất dữ liệu trả về dữ liệu được yêu cầu. Một lệnh không thành công sẽ trả về
một thông báo lỗi mô tả vấn đề.

Việc tương tác với một Redis server bao gồm một chuỗi các lệnh và phản hồi.
Tác dụng của một lệnh là như nhau bất kể bạn gửi nó
từ một [thư viện client](https://redis.io/docs/latest/develop/clients), hay từ một công cụ client
như [redis-cli](https://redis.io/docs/latest/develop/tools/cli) hoặc
[Redis Insight](https://redis.io/docs/latest/develop/tools/insight). Điều này rất hữu ích
trong quá trình phát triển. Bạn có thể dùng một công cụ cấp cao để thử nghiệm một
lệnh, thiết lập dữ liệu kiểm thử, hoặc làm mẫu thử (prototype) một mô hình dữ liệu, rồi sau đó truy cập
dữ liệu đã chuẩn bị từ code ứng dụng của bạn. Hầu hết các ví dụ code Redis
được trình bày kèm một đoạn trích từ phiên làm việc CLI và code ứng dụng tương đương
cho từng thư viện client.

## Gom nhóm lệnh (Batching commands)

Mặc dù bạn có thể gửi các lệnh Redis lần lượt từng lệnh một, nhưng thường sẽ hiệu quả hơn
nếu gom một chuỗi các lệnh liên quan lại với nhau thành một *pipeline*. Một pipeline
gửi nhiều lệnh tới server trong một lần giao tiếp duy nhất và nhận
các phản hồi theo cùng cách đó. Xem
[Pipelining](https://redis.io/docs/latest/develop/using-commands/pipelining) để có mô tả đầy đủ
về kỹ thuật này và xem thêm các ví dụ pipelining cho các
[thư viện client](https://redis.io/docs/latest/develop/clients).

Một lý do khác để gom nhóm lệnh là để xử lý chúng như một đơn vị không bị gián đoạn.
Bạn nên làm điều này nếu cần chắc chắn rằng tất cả các lệnh đều được
hoàn thành mà không có cùng dữ liệu đó bị một client khác sửa đổi (điều này
có thể khiến dữ liệu rơi vào trạng thái không nhất quán). Redis sử dụng *transaction*
để hiện thực hành vi này. Xem
[Transactions](https://redis.io/docs/latest/develop/using-commands/transactions) để biết
thêm thông tin và xem thêm các ví dụ transaction cho các
[thư viện client](https://redis.io/docs/latest/develop/clients).

## Thông tin thêm (More information)

Các trang khác trong mục này mô tả chi tiết hơn các khái niệm về lệnh Redis:

- [Keys and values](https://redis.io/docs/latest/develop/using-commands/keyspace/) — Quản lý key trong Redis: hết hạn key (key expiration), quét (scanning), thay đổi và truy vấn không gian key (key space)
- [Redis pipelining](https://redis.io/docs/latest/develop/using-commands/pipelining/) — Cách tối ưu thời gian khứ hồi (round-trip time) bằng cách gom nhóm các lệnh Redis
- [Transactions](https://redis.io/docs/latest/develop/using-commands/transactions/) — Cách transaction hoạt động trong Redis
- [Multi-key operations](https://redis.io/docs/latest/develop/using-commands/multi-key-operations/) — Hành vi của các lệnh đa key (multi-key) trên các cấu hình Redis và thiết lập clustering khác nhau

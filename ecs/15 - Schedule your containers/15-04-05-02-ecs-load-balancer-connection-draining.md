# Optimize load balancer connection draining parameters for Amazon ECS

> Nguồn: **Amazon ECS Developer Guide — Optimize load balancer connection draining parameters for Amazon ECS**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/load-balancer-connection-draining.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: **bản dịch** sang tiếng Việt, đi đúng thứ tự và đúng nội dung của trang gốc, không thêm, không bớt ý. Code ví dụ giữ nguyên như trên docs.
> - Khối **💡 Giải thích thêm**: chỉ giải thích lại những gì **chính trang này** đã nói, cho dễ hiểu hơn. Không đưa thông tin từ trang khác hay ví dụ tự đặt.
> - Tên tham số, trạng thái, tín hiệu giữ nguyên tiếng Anh.
> - Tài liệu AWS được phát hành theo giấy phép **Creative Commons Attribution-ShareAlike 4.0**; bản dịch này ghi rõ nguồn ở trên.

---

## Mục lục

0. [Connection draining và deregistration delay](#0-connection-draining-và-deregistration-delay)
1. [Khả năng phản hồi SIGTERM](#1-khả-năng-phản-hồi-sigterm)
2. [Tổng hợp: hai khoảng chờ khi dừng task](#2-tổng-hợp-hai-khoảng-chờ-khi-dừng-task)

---

## 0. Connection draining và deregistration delay

**📘 Theo docs AWS**

Để tối ưu, client duy trì một **kết nối keep alive** tới container service. Điều này cho phép các request tiếp theo từ client đó **dùng lại** kết nối hiện có. Khi bạn muốn dừng traffic tới một container, bạn thông báo cho load balancer. Load balancer định kỳ kiểm tra xem client đã đóng kết nối keep alive chưa. Amazon ECS giám sát load balancer, và **chờ** load balancer báo rằng kết nối keep alive đã đóng (target ở trạng thái **`UNUSED`**).

Khoảng thời gian load balancer chờ để chuyển target sang trạng thái `UNUSED` là **deregistration delay**. Bạn có thể cấu hình tham số load balancer sau để tăng tốc deploy.

- `deregistration_delay.timeout_seconds`: **300** (mặc định)

Khi bạn có service với thời gian phản hồi **dưới 1 giây**, hãy đặt tham số thành giá trị sau để load balancer chỉ chờ **5 giây** trước khi ngắt kết nối giữa client và service backend:

- `deregistration_delay.timeout_seconds`: **5**

> **Note:** **Không** đặt giá trị 5 giây khi service có **request sống lâu**, như **upload file chậm** hoặc **kết nối streaming**.

**💡 Giải thích thêm**

```
Muốn dừng traffic tới container → báo load balancer
   └─ load balancer chờ client đóng keep alive (tối đa deregistration delay)
         └─ target chuyển UNUSED → ECS tiếp tục
```

| `deregistration_delay.timeout_seconds` | Khi nào dùng (theo trang) |
|---|---|
| **300** (mặc định) | — |
| **5** | Service phản hồi **< 1 giây** |
| Không dùng 5 | Request sống lâu: upload file chậm, streaming |

---

## 1. Khả năng phản hồi SIGTERM

**📘 Theo docs AWS**

Amazon ECS trước tiên gửi một **stop signal** tới task để báo ứng dụng cần kết thúc và tắt. Tín hiệu này có thể được định nghĩa trong container image bằng chỉ thị **STOPSIGNAL** và mặc định là **SIGTERM**. Sau đó, Amazon ECS gửi thông điệp **SIGKILL**. Khi ứng dụng **bỏ qua** SIGTERM, Amazon ECS service phải **chờ** để gửi tín hiệu SIGKILL nhằm kết thúc tiến trình.

Khoảng thời gian Amazon ECS chờ để gửi SIGKILL được xác định bởi tuỳ chọn Amazon ECS agent sau:

- `ECS_CONTAINER_STOP_TIMEOUT`: **30** (mặc định)

  Thông tin thêm về tham số container agent, xem *Amazon ECS Container Agent* (<https://github.com/aws/amazon-ecs-agent/blob/master/README.md>) trên GitHub.

Để rút ngắn khoảng chờ, đặt tham số Amazon ECS agent thành giá trị sau:

- `ECS_CONTAINER_STOP_TIMEOUT`: **2**

  Nếu ứng dụng mất **hơn 1 giây**, hãy **nhân giá trị đó với 2** và dùng số đó làm giá trị.

Trong trường hợp này, Amazon ECS chờ **2 giây** để container tắt, rồi Amazon ECS gửi thông điệp SIGKILL nếu ứng dụng chưa dừng.

Bạn cũng có thể sửa code ứng dụng để **bắt tín hiệu SIGTERM** và phản ứng với nó. Sau đây là ví dụ bằng JavaScript:

```javascript
process.on('SIGTERM', function() {
  server.close();
})
```

Code này khiến HTTP server **ngừng lắng nghe** request mới, **trả lời xong** các request đang xử lý, rồi tiến trình Node.js kết thúc vì event loop không còn việc gì. Do đó, nếu tiến trình chỉ mất **500 ms** để xử lý xong các request đang dở, nó **kết thúc sớm** mà không phải chờ hết stop timeout và bị gửi SIGKILL.

**💡 Giải thích thêm**

```
ECS dừng task
  ├─ gửi stop signal (STOPSIGNAL trong image; mặc định SIGTERM)
  ├─ chờ tối đa ECS_CONTAINER_STOP_TIMEOUT
  └─ ứng dụng chưa dừng → gửi SIGKILL
```

| `ECS_CONTAINER_STOP_TIMEOUT` | Theo trang |
|---|---|
| **30** (mặc định) | — |
| **2** | Để rút ngắn khoảng chờ |
| Ứng dụng mất > 1 giây để tắt | Lấy thời gian đó **× 2** |

Ví dụ áp dụng quy tắc "× 2" của trang: ứng dụng mất 3 giây để tắt → đặt `ECS_CONTAINER_STOP_TIMEOUT` = 6.

Cách tốt hơn là **bắt SIGTERM** trong code (ví dụ Node.js của trang):

| Bước khi nhận SIGTERM | Kết quả |
|---|---|
| `server.close()` | Ngừng nhận request mới |
| Xử lý xong request đang dở | Không cắt ngang người dùng |
| Event loop rỗng → tiến trình thoát | Thoát **sớm** (vd sau 500 ms), không chờ hết timeout, không bị SIGKILL |

Câu "multiply the value by 2" trên trang gốc không nói rõ "value" là gì; theo ngữ cảnh ("nếu ứng dụng mất hơn 1 giây"), đó là **thời gian ứng dụng cần để tắt**.

---

## 2. Tổng hợp: hai khoảng chờ khi dừng task

**💡 Giải thích thêm**

Tổng hợp từ hai mục trên của chính trang:

| Khoảng chờ | Tham số | Mặc định | Giá trị tăng tốc | Điều kiện |
|---|---|---|---|---|
| Load balancer chờ đóng keep alive (deregistration delay) | `deregistration_delay.timeout_seconds` | 300 giây | 5 giây | Phản hồi < 1 giây; **không** dùng cho upload chậm / streaming |
| ECS chờ trước khi gửi SIGKILL | `ECS_CONTAINER_STOP_TIMEOUT` | 30 giây | 2 giây | Tắt > 1 giây → thời gian tắt × 2 |

Cả hai đều là thời gian **chờ khi gỡ task cũ** trong lúc deploy; giảm chúng giúp deploy nhanh hơn, còn **bắt SIGTERM** trong code giúp task thoát sớm hơn cả timeout.

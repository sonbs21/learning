# Cấu trúc dữ liệu xác suất (Probabilistic)

> Nguồn: <https://redis.io/docs/latest/develop/data-types/probabilistic/>

*Cấu trúc dữ liệu xác suất (probabilistic data structures)* cho ra các giá trị xấp xỉ của các số liệu thống kê như
số lượng (count), tần suất (frequency), và thứ hạng (ranking) thay vì các giá trị chính xác.
Ưu điểm của việc dùng giá trị xấp xỉ là chúng đủ dùng cho
nhiều mục đích phổ biến nhưng lại hiệu quả hơn nhiều khi tính toán. Đôi khi
chúng còn có những ưu điểm khác, chẳng hạn như che giấu (obfuscate) thời gian, vị trí,
và các dữ liệu nhạy cảm khác.

Các cấu trúc dữ liệu xác suất có sẵn như một phần của Redis Open Source và cũng có trong Redis Software và Redis Cloud.
Xem
[Install Redis Open Source](https://redis.io/docs/latest/operate/oss_and_stack/install/install-stack) hoặc
[Install Redis Software](https://redis.io/docs/latest/operate/rs/installing-upgrading/install)
để có hướng dẫn cài đặt đầy đủ.

- [Bloom filter](https://redis.io/docs/latest/develop/data-types/probabilistic/bloom-filter/) — Bloom filter là một cấu trúc dữ liệu xác suất kiểm tra sự có mặt của một phần tử trong một tập hợp
- [Count-min sketch](https://redis.io/docs/latest/develop/data-types/probabilistic/count-min-sketch/) — Count-min sketch là một cấu trúc dữ liệu xác suất ước lượng tần suất của một phần tử trong một luồng dữ liệu (data stream).
- [Cuckoo filter](https://redis.io/docs/latest/develop/data-types/probabilistic/cuckoo-filter/) — Cuckoo filter là một cấu trúc dữ liệu xác suất kiểm tra sự có mặt của một phần tử trong một tập hợp
- [HyperLogLog](https://redis.io/docs/latest/develop/data-types/probabilistic/hyperloglogs/) — HyperLogLog là một cấu trúc dữ liệu xác suất ước lượng cardinality (số phần tử) của một tập hợp.
- [t-digest](https://redis.io/docs/latest/develop/data-types/probabilistic/t-digest/) — t-digest là một cấu trúc dữ liệu xác suất cho phép bạn ước lượng phân vị (percentile) của một luồng dữ liệu.
- [Top-K](https://redis.io/docs/latest/develop/data-types/probabilistic/top-k/) — Top-K là một cấu trúc dữ liệu xác suất cho phép bạn tìm các phần tử xuất hiện nhiều nhất trong một luồng dữ liệu.
- [Configuration Parameters](https://redis.io/docs/latest/develop/data-types/probabilistic/configuration/) — Các cấu trúc dữ liệu xác suất của Redis hỗ trợ nhiều tham số cấu hình.

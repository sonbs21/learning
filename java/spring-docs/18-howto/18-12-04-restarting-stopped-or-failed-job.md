# 18.12.4. Khởi động lại một Job đã dừng hoặc thất bại (Restarting a stopped or failed Job)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.batch.restarting-a-failed-job>

Để khởi động lại một `Job` thất bại, tất cả các tham số (định danh và không định danh) phải được chỉ định lại trên dòng lệnh. Các tham số không định danh **không** được sao chép từ lần thực thi trước. Điều này cho phép chúng được sửa đổi hoặc loại bỏ.

> **LƯU Ý (NOTE):** Khi bạn đang dùng một `JobParametersIncrementer` tùy chỉnh, bạn phải tập hợp tất cả các tham số do incrementer quản lý để khởi động lại một lần thực thi thất bại.

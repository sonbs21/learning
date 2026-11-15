# 17.4.3. Tìm class main (Finding a Main Class)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#build-tool-plugins.other-build-systems.finding-main-class>

Nếu bạn không dùng `Repackager.setMainClass()` để chỉ định một class main, repackager sẽ dùng [ASM](https://asm.ow2.io/) để đọc các file class và cố gắng tìm một class phù hợp có phương thức `public static void main(String[] args)`. Một exception sẽ được ném ra nếu tìm thấy nhiều hơn một ứng viên.

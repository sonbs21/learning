# 18.3.4. Dùng HTTP port ngẫu nhiên chưa được gán (Use a Random Unassigned HTTP Port)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.webserver.use-random-port>

Để quét tìm một port trống (dùng cơ chế native của hệ điều hành để tránh xung đột), hãy dùng `server.port=0`.

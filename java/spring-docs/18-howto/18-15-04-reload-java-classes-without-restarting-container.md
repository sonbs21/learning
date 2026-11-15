# 18.15.4. Nạp lại class Java mà không khởi động lại container (Reload Java Classes without Restarting the Container)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.hotswapping.reload-java-classes-without-restarting>

Nhiều IDE hiện đại (Eclipse, IDEA, và các IDE khác) hỗ trợ hot swapping bytecode. Do đó, nếu bạn thực hiện một thay đổi không ảnh hưởng đến signature của class hoặc phương thức, nó sẽ được nạp lại một cách sạch sẽ mà không có tác dụng phụ.

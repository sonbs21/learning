public class OrderServiceImpl implements OrderService {
    @Tx
    public void save(String item) {
        System.out.println("    [code thật] lưu đơn hàng: " + item);
    }

    public void placeOrder(String item) {          // KHÔNG có @Tx
        System.out.println("    [code thật] kiểm tra kho...");
        this.save(item);                            // tự gọi method của chính mình
    }
}

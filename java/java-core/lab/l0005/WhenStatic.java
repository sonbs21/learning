public class WhenStatic {
    public static void main(String[] args) {
        System.out.println("1. main bắt đầu");
        System.out.println("2. sắp gọi Shop.open() lần đầu");
        Shop.open();
        System.out.println("3. sắp gọi Shop.open() lần hai");
        Shop.open();
    }
}

class Shop {
    static {
        System.out.println("   >>> [static] Dọn dẹp, mở cửa hàng (chỉ 1 lần)");
    }
    static void open() {
        System.out.println("   Phục vụ một khách");
    }
}

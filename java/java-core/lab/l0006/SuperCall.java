public class SuperCall {
    static class TaiKhoan {
        String moTa() { return "Tài khoản thường"; }
    }
    static class TaiKhoanVip extends TaiKhoan {
        @Override
        String moTa() { return super.moTa() + " + ưu đãi VIP"; }   // gọi bản của CHA rồi thêm
    }

    public static void main(String[] args) {
        System.out.println(new TaiKhoanVip().moTa());
    }
}
